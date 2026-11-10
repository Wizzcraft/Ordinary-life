package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Gas attendant shift: eight cars, one per hour. A car pulls in and the driver asks for an amount
 * (or a full tank, shown only on the gauge). Hold to pump: the counter rolls faster and faster;
 * release exactly on the amount. Short annoys the driver, over comes out of the attendant's pocket.
 *
 * Decisions: a turbo handle (twice as fast, but the exact window in time is tighter), and an optional
 * quick wipe of a dirty windscreen for a tip that burns the driver's patience, so the car may drive off.
 *
 * Why the counter follows x = T * (t / tT)^1.6: the same curve every car means the player learns one
 * rhythm; only the amount and the speed change. The "perfect" band is defined in time (about 60 ms
 * each side of the ideal release), then converted to shekels, so faster counters stay fair.
 */
final class GasMini extends Mini {
    private static final int ARRIVE = 0, WAIT = 1, PUMP = 2, LEAVE = 3;
    private static final float EXP = 1.6f;
    private static final float ARRIVE_T = 0.75f, DRIVE_T = 0.55f, WIPE_NEED = 320f;
    private static final float PUMP_X = 8, PUMP_W = 124, STOP_X = 262;
    private static final int TIP_PERFECT = 4, TIP_TURBO = 3, TIP_WIPE = 6;

    // ---- car shapes: x,y pairs, car faces left, y negative is up, wheels touch y = 0 ----
    private static final float[][] SLAB = {
            {-56, -8, -56, -23, -48, -27, 56, -27, 56, -8},
            {-67, -8, -67, -22, -58, -27, 67, -27, 67, -8},
            {-63, -8, -63, -24, -58, -28, 63, -28, 63, -8},
            {-70, -8, -70, -23, -60, -27, 70, -27, 70, -8},
            {-48, -8, -48, -20, -40, -25, 48, -25, 48, -8}};
    private static final float[][] CAB = {
            {-32, -27, -20, -46, 36, -46, 52, -27},
            {-34, -27, -20, -46, 24, -46, 42, -27},
            {-58, -28, -40, -56, 63, -56, 63, -28},
            {-42, -27, -30, -46, 4, -46, 12, -27},
            {-30, -25, -22, -46, 30, -46, 40, -25}};
    private static final float[][] WIN1 = {
            {-29, -29, -19, -43, -3, -43, -3, -29},
            {-31, -29, -19, -43, -3, -43, -3, -29},
            {-54, -30, -39, -52, -22, -52, -22, -30},
            {-39, -29, -29, -43, -8, -43, -8, -29},
            {-27, -27, -21, -43, -3, -43, -3, -27}};
    private static final float[][] WIN2 = {
            {3, -29, 3, -43, 32, -43, 46, -29},
            {2, -29, 2, -43, 21, -43, 36, -29},
            {-16, -32, -16, -52, 28, -52, 28, -32},
            {-4, -29, -4, -43, 2, -43, 8, -29},
            {3, -27, 3, -43, 26, -43, 35, -27}};
    private static final float[] BED = {16, -27, 16, -37, 70, -37, 70, -27};
    private static final float[] LEN = {112, 134, 126, 140, 96};
    private static final float[] WHEEL_X = {34, 42, 40, 44, 30};
    private static final float[][] FLAP = {{-38, -20}, {-52, -19}, {-44, -22}, {-54, -19}, {-36, -17}};
    private static final float[] ROOF = {46, 46, 56, 46, 46};
    private static final int[] PAINT = {0xFFD9453B, 0xFF3C7DD9, 0xFFF2C14E, 0xFF4FB286, 0xFFEDEDED, 0xFF8E5BC7,
            0xFFE8834A, 0xFF7C8794};
    private static final int[] SKY_DAY = {0xFF6FA8D6, 0xFFBFDCF0}, SKY_DUSK = {0xFF4B3F72, 0xFFE8895A},
            SKY_NIGHT = {0xFF0B1030, 0xFF2A3560};

    private final float[] scratch = new float[24];

    // ---- layout, recomputed in draw() ----
    private float top = 150, bottom = 664, btnY, btnH, gaugeY, carBase, carScale = 1, panelY;

    // ---- round state ----
    private int phase = ARRIVE, round, lastT, lastType = -1, lastPaint = -1;
    private int grade = RUNNING;
    private float diff, time, pt, th, x, pat, patMax, tT0, hoseT;
    private int target = 100;
    private boolean full, dirty, turbo, holding, pumped, angry, over, waivePay, paid, flicker;
    private float wipeProg, lastWx, lastWy, carX = 520, wheelRot;
    private boolean wiping, wipeDone;
    private int carType, carPaint, queueType, queuePaint, mood;
    private float leaveHold, tickAt, flash;
    private int flashCol = Ui.INK;
    // bot plan
    private boolean planned, wantWipe, wantTurbo, botSkip;
    private float planAt, jitter, wipeT0;

    @Override
    int rounds() {
        return 8;
    }

    @Override
    String title() {
        return h.t("תדלקן", "תדלקנית");
    }

    @Override
    String howTo() {
        return "מחזיקים כדי לתדלק והמונה מאיץ, ומשחררים בדיוק על הסכום. "
                + "טורבו מהיר פי שניים, וניגוב השמשה מרוויח טיפ.";
    }

    // ------------------------------------------------------------------ rules

    private float tp() {
        return Math.max(2.2f, (0.07f - 0.025f * diff) * EXP * target / tT0) * (turbo ? 1.35f : 1f);
    }

    private float baseTp() {
        return Math.max(2.2f, (0.07f - 0.025f * diff) * EXP * target / tT0);
    }

    @Override
    void begin(int r, float difficulty) {
        round = r;
        diff = difficulty;
        Random rnd = h.rnd();
        full = r == 2 || r == 5 || r == 7;
        int t;
        if (full) {
            t = 38 + rnd.nextInt(60);
        } else if (difficulty < 0.2f) {
            t = rnd.nextBoolean() ? 50 : 100;
        } else if (difficulty < 0.45f) {
            int[] a = {40, 60, 80, 100, 120};
            t = a[rnd.nextInt(a.length)];
        } else {
            int[] a = {35, 45, 65, 75, 85, 95, 115, 135, 145};
            t = a[rnd.nextInt(a.length)];
        }
        if (t == lastT) t += 5;
        lastT = target = t;
        tT0 = (2.15f - 0.85f * difficulty) * (0.92f + 0.16f * rnd.nextFloat());
        patMax = pat = Math.max(1.7f, 4.6f - 3.0f * difficulty);
        dirty = r == 1 || (r > 1 && rnd.nextFloat() < 0.6f);
        carType = pickType(rnd, lastType);
        carPaint = pickPaint(rnd, lastPaint);
        lastType = carType;
        lastPaint = carPaint;
        queueType = rnd.nextInt(5);
        queuePaint = rnd.nextInt(PAINT.length);
        phase = ARRIVE;
        pt = th = x = 0;
        turbo = holding = pumped = angry = over = paid = wiping = wipeDone = false;
        grade = RUNNING;
        wipeProg = 0;
        carX = W + 120;
        hoseT = 0;
        tickAt = 0;
        flash = 0;
        mood = 0;
        planned = false;
        h.sfx(Sfx.WHOOSH);
        // A round replayed after an interruption must not pay (or charge) a second time.
        if (waivePay) paid = true;
        waivePay = false;
    }

    private static int pickType(Random r, int not) {
        int t = r.nextInt(5);
        return t == not ? (t + 1 + r.nextInt(4)) % 5 : t;
    }

    private static int pickPaint(Random r, int not) {
        int t = r.nextInt(PAINT.length);
        return t == not ? (t + 1) % PAINT.length : t;
    }

    @Override
    int update(float dt) {
        time += dt;
        if (flash > 0) flash -= dt;
        float carV = 0;
        float stop = STOP_X;
        switch (phase) {
            case ARRIVE: {
                pt += dt;
                float u = Math.min(1, pt / ARRIVE_T);
                float e = 1 - (1 - u) * (1 - u) * (1 - u);
                float nx = (W + 120) + (stop - (W + 120)) * e;
                carV = (carX - nx) / Math.max(dt, 1e-4f);
                carX = nx;
                if (u >= 1) {
                    phase = WAIT;
                    pt = 0;
                    carX = stop;
                    h.sfx(Sfx.DOOR);
                }
                break;
            }
            case WAIT:
                pt += dt;
                pat -= dt;
                if (pat <= 0) walkOut(false);
                break;
            case PUMP:
                pt += dt;
                pat -= dt * 0.5f;
                th += dt;
                float prog = th * (turbo ? 2 : 1) / tT0;
                x = target * (float) Math.pow(prog, EXP);
                float step = Math.max(4, target / 14f);
                while (x >= tickAt + step) {
                    tickAt += step;
                    h.sfx(Sfx.TICK);
                }
                flicker = diff >= 0.3f && ((time * 3.1f) % 1f) > 0.66f;
                if (x - target > 5 * tp()) {
                    over = true;
                    finish();
                } else if (pat <= 0) {
                    walkOut(true);
                }
                break;
            default:
                pt += dt;
                if (pt > leaveHold) {
                    float u = Math.min(1, (pt - leaveHold) / (angry ? 0.4f : DRIVE_T));
                    float nx = stop - u * u * (stop + 170);
                    carV = (carX - nx) / Math.max(dt, 1e-4f);
                    carX = nx;
                    if (u >= 1) return grade;
                }
                break;
        }
        wheelRot += carV * dt / 10f;
        float hoseGoal = (phase == WAIT || phase == PUMP || (phase == LEAVE && pt < leaveHold)) ? 1 : 0;
        hoseT += (hoseGoal - hoseT) * Math.min(1, dt * 8);
        return RUNNING;
    }

    /** The driver gives up waiting. */
    private void walkOut(boolean midPump) {
        holding = false;
        wiping = false;
        angry = true;
        grade = MISS;
        phase = LEAVE;
        pt = 0;
        leaveHold = 0;
        mood = 2;
        h.text(STOP_X, carBase - 6, midPump ? "נסע באמצע" : "נסע בעצבים", Ui.BAD);
        h.sfx(Sfx.BEEP);
        h.shake(4);
    }

    private void finish() {
        holding = false;
        float err = x - target, p = tp();
        boolean overshoot = err > p;
        if (Math.abs(err) <= p) grade = PERFECT;
        else if (err < 0 && err >= -3.2f * p) grade = OK;
        else if (err > 0 && err <= 2.0f * p) grade = OK;
        else grade = MISS;
        phase = LEAVE;
        pt = 0;
        leaveHold = grade == MISS ? 0.3f : 0.45f;
        float fx = STOP_X, fy = carBase - 8;
        int cost = 0, tip = 0;
        String word;
        int col;
        if (grade == PERFECT) {
            word = turbo ? "בול בטורבו!" : "בול!";
            col = Ui.ACCENT;
            tip = TIP_PERFECT + (turbo ? TIP_TURBO : 0);
            mood = 1;
            h.sfx(Sfx.PERFECT);
            h.burst(PUMP_X + 70, panelY + 40, Ui.ACCENT, 14);
            h.burst(fx, fy, Ui.ACCENT, 10);
        } else if (grade == OK) {
            word = err < 0 ? "חסר טיפה" : "קצת יותר";
            col = err < 0 ? Ui.ORANGE : Ui.ACCENT;
            mood = err < 0 ? 2 : 0;
            h.sfx(Sfx.GOOD);
            h.burst(fx, fy, Ui.GOOD, 6);
        } else {
            word = err < 0 ? "חסר הרבה" : "גלש!";
            col = Ui.BAD;
            mood = 2;
            h.sfx(Sfx.BAD);
            h.shake(err < 0 ? 4 : 7);
        }
        if (err > p) cost = Math.max(1, Math.min(30, Math.round(err * (grade == OK ? 0.5f : 1f))));
        h.text(fx, fy, word, col);
        flashCol = col;
        flash = 0.5f;
        if (!paid) {
            paid = true;
            if (cost > 0 && h.pay(-cost)) h.text(PUMP_X + 70, panelY + 150, "-" + Ui.nis(cost), Ui.BAD);
            if (tip > 0 && h.pay(tip)) {
                h.text(fx + 40, fy + 30, "+" + Ui.nis(tip), Ui.GOOD);
                h.sfx(Sfx.COIN);
            }
        }
    }

    // ------------------------------------------------------------------ touch

    private float carCx() {
        return carX;
    }

    private float carTop() {
        return carBase - ROOF[carType] * carScale;
    }

    private boolean onCar(float px, float py) {
        float half = LEN[carType] * carScale / 2 + 8;
        return Math.abs(px - carCx()) <= half && py >= carTop() - 12 && py <= carBase + 6;
    }

    private boolean onTurbo(float px, float py) {
        return Ui.in(px, py, 12, btnY, 84, btnH);
    }

    @Override
    void down(float px, float py) {
        if (phase != WAIT || holding) return;
        if (!pumped && onTurbo(px, py)) {
            turbo = !turbo;
            h.sfx(turbo ? Sfx.BEEP : Sfx.TAP);
            return;
        }
        if (dirty && !wipeDone && onCar(px, py)) {
            wiping = true;
            lastWx = px;
            lastWy = py;
            return;
        }
        phase = PUMP;
        pumped = true;
        holding = true;
        th = 0;
        pt = 0;
        tickAt = 0;
        h.sfx(Sfx.POUR);
    }

    @Override
    void move(float px, float py) {
        if (!wiping || phase != WAIT) return;
        // Count sideways travel only while the sponge is over the car.
        if (onCar(px, py)) wipeProg += Math.abs(px - lastWx);
        lastWx = px;
        lastWy = py;
        if (wipeProg >= WIPE_NEED && !wipeDone) {
            wipeDone = true;
            wiping = false;
            h.text(carCx(), carBase - 8, "נוצץ!", Ui.BLUE);
            h.burst(carCx(), carBase - 28 * carScale, 0xFFBFE6FF, 12);
            if (!paid && h.pay(TIP_WIPE)) {
                h.text(carCx() + 40, carBase + 14, "+" + Ui.nis(TIP_WIPE), Ui.GOOD);
                h.sfx(Sfx.COIN);
            } else {
                h.sfx(Sfx.GOOD);
            }
        }
    }

    @Override
    void up(float px, float py) {
        wiping = false;
        if (holding && phase == PUMP) finish();
    }

    @Override
    void pause() {
        holding = false;
        wiping = false;
        // Anything already paid stays paid; the replayed round is free of tips and charges.
        if (paid || wipeDone) waivePay = true;
    }

    // ------------------------------------------------------------------ bot

    @Override
    void bot(Random r, float skill) {
        if (phase == ARRIVE || phase == LEAVE) return;
        if (phase == WAIT && !holding) {
            if (!planned) {
                planned = true;
                float sd = 0.035f + 0.30f * (float) Math.pow(1 - skill, 1.6);
                jitter = (float) r.nextGaussian() * sd;
                planAt = time + 0.22f + 0.5f * (1 - skill) + r.nextFloat() * 0.2f;
                botSkip = r.nextFloat() < 0.22f * (1 - skill) * (1 - skill);
                float pumpNeed = tT0 * 0.5f + 0.2f;
                wantWipe = dirty && skill > 0.45f && r.nextFloat() < skill + 0.2f;
                wantTurbo = false;
                if (wantWipe) {
                    // Is there time for a wipe plus a normal pump? If not, turbo only if the player is good.
                    float need = 0.3f + 1.3f + pumpNeed;
                    if (pat < need) {
                        if (skill > 0.7f && pat >= 0.3f + 1.3f + tT0 * 0.25f + 0.1f) wantTurbo = true;
                        else wantWipe = false;
                    }
                }
                if (!wantWipe && skill > 0.9f && diff > 0.4f && r.nextFloat() < 0.3f) wantTurbo = true;
            }
            if (time < planAt || botSkip) return;
            if (wantTurbo && !turbo) {
                down(54, btnY + btnH / 2);
                up(54, btnY + btnH / 2);
                return;
            }
            if (wantWipe && !wipeDone) {
                float cy = carBase - 22 * carScale;
                if (!wiping) {
                    wipeT0 = time;
                    down(carCx(), cy);
                } else {
                    move(carCx() + 48 * (float) Math.sin((time - wipeT0) * 14), cy);
                }
                return;
            }
            if (wiping) up(carCx(), carBase);
            down(220, btnY + btnH / 2);
        } else if (phase == PUMP && holding) {
            float rate = (turbo ? 2 : 1) * EXP * target / tT0 * (float) Math.pow(Math.max(th * (turbo ? 2 : 1) / tT0, 0.05f), EXP - 1);
            if (x + rate / 60f >= target + rate * jitter) up(220, btnY + btnH / 2);
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    private void layout(float t, float b) {
        top = t;
        bottom = b;
        float hh = b - t;
        btnH = Ui.clamp(hh * 0.2f, 54, 88);
        btnY = b - btnH - 4;
        gaugeY = btnY - 30;
        carBase = gaugeY - 26;
        carScale = Ui.clamp(hh / 330f, 0.9f, 1.2f);
        panelY = t + 34;
    }

    private static int lerpC(int a, int b, float t) {
        t = Ui.clamp(t, 0, 1);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255,
                bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    private static int alpha(int argb, float a) {
        return ((int) (255 * Ui.clamp(a, 0, 1)) << 24) | (argb & 0xFFFFFF);
    }

    private void poly(Gfx c, float[] src, float ox, float oy, float s, int col) {
        int n = src.length;
        for (int i = 0; i < n; i += 2) {
            scratch[i] = ox + src[i] * s;
            scratch[i + 1] = oy + src[i + 1] * s;
        }
        float[] p = n == scratch.length ? scratch : java.util.Arrays.copyOf(scratch, n);
        c.fillPoly(p, col);
    }

    /** 0 = day .. 1 = night, by round. */
    private float dusk() {
        return Ui.clamp(round / 7f, 0, 1);
    }

    private void drawCar(Gfx c, int type, int paint, float cx, float base, float s, boolean night, float rot,
                         boolean lights) {
        int body = PAINT[paint], dark = Ui.darken(body, 0.72f), glass = night ? 0xFF111A2A : 0xFF263447;
        // shadow
        c.fillRoundRect(cx - LEN[type] * s / 2 - 2, base - 3, LEN[type] * s + 4, 6, 3, 0x55000000);
        if (type == 3) poly(c, BED, cx, base, s, dark);
        poly(c, CAB[type], cx, base, s, body);
        poly(c, SLAB[type], cx, base, s, body);
        // darker lower trim and bumpers
        float l = LEN[type] / 2;
        c.fillRect(cx - l * s, base - 13 * s, l * 2 * s, 5 * s, dark);
        c.fillRoundRect(cx - (l + 2) * s, base - 14 * s, 6 * s, 8 * s, 2, 0xFF2A3038);
        c.fillRoundRect(cx + (l - 4) * s, base - 14 * s, 6 * s, 8 * s, 2, 0xFF2A3038);
        poly(c, WIN1[type], cx, base, s, glass);
        poly(c, WIN2[type], cx, base, s, glass);
        // a little head behind the windscreen
        float[] w = WIN1[type];
        float hx = (w[0] + w[2] + w[4] + w[6]) / 4 + 3, hy = w[5] + 6;
        c.fillCircle(cx + hx * s, base + hy * s, 4.4f * s, 0xFFD9A77F);
        c.fillRect(cx + (hx - 4.5f) * s, base + (hy + 4) * s, 9 * s, 5 * s, Ui.darken(body, 0.5f));
        // lights
        float lx = cx - l * s;
        c.fillRoundRect(lx, base - 22 * s, 5 * s, 6 * s, 2, lights ? 0xFFFFF2B0 : 0xFFCFC9A8);
        c.fillRoundRect(cx + (l - 4) * s, base - 22 * s, 4 * s, 6 * s, 2, 0xFFB02A2A);
        if (lights && night) {
            c.fillPoly(new float[]{lx, base - 22 * s, lx - 52, base - 6, lx - 52, base + 4, lx, base - 16 * s}, 0x33FFF2B0);
        }
        // wheels
        for (int k = -1; k <= 1; k += 2) {
            float wx = cx + k * WHEEL_X[type] * s, wy = base - 10 * s, rr = 10 * s;
            c.fillCircle(wx, wy, rr, 0xFF15181D);
            c.fillCircle(wx, wy, rr * 0.55f, 0xFFB8C0CC);
            float a = rot;
            c.line(wx, wy, wx + (float) Math.cos(a) * rr * 0.5f, wy + (float) Math.sin(a) * rr * 0.5f, 1.4f, 0xFF5A6572);
            c.line(wx, wy, wx - (float) Math.cos(a) * rr * 0.5f, wy - (float) Math.sin(a) * rr * 0.5f, 1.4f, 0xFF5A6572);
        }
        // the fuel flap
        c.fillRoundRect(cx + FLAP[type][0] * s - 4 * s, base + FLAP[type][1] * s - 4 * s, 8 * s, 8 * s, 2, Ui.darken(body, 0.55f));
    }

    private void face(Gfx c, float cx, float cy, float r, int m) {
        c.fillCircle(cx, cy, r, m == 2 ? 0xFFF0A890 : 0xFFF2C9A1);
        c.fillCircle(cx - r * 0.38f, cy - r * 0.15f, r * 0.12f, 0xFF2A2A2A);
        c.fillCircle(cx + r * 0.38f, cy - r * 0.15f, r * 0.12f, 0xFF2A2A2A);
        if (m == 1) {
            c.line(cx - r * 0.4f, cy + r * 0.3f, cx, cy + r * 0.55f, 1.6f, 0xFF2A2A2A);
            c.line(cx, cy + r * 0.55f, cx + r * 0.4f, cy + r * 0.3f, 1.6f, 0xFF2A2A2A);
        } else if (m == 2) {
            c.line(cx - r * 0.4f, cy + r * 0.55f, cx, cy + r * 0.3f, 1.6f, 0xFF2A2A2A);
            c.line(cx, cy + r * 0.3f, cx + r * 0.4f, cy + r * 0.55f, 1.6f, 0xFF2A2A2A);
            c.line(cx - r * 0.65f, cy - r * 0.5f, cx - r * 0.2f, cy - r * 0.3f, 1.6f, 0xFF7A2A20);
            c.line(cx + r * 0.65f, cy - r * 0.5f, cx + r * 0.2f, cy - r * 0.3f, 1.6f, 0xFF7A2A20);
        } else {
            c.line(cx - r * 0.35f, cy + r * 0.45f, cx + r * 0.35f, cy + r * 0.45f, 1.6f, 0xFF2A2A2A);
        }
    }

    private String phrase() {
        if (full) return "מיכל מלא, בבקשה";
        float u = pat / patMax;
        if (diff > 0.5f) return "אני ממהר, בבקשה";
        if (diff > 0.25f || u < 0.5f) return "יש לי פגישה";
        return "תדלוק, בבקשה";
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void draw(Gfx c, float t, float b) {
        layout(t, b);
        float hh = b - t, night = Ui.clamp((dusk() - 0.55f) / 0.35f, 0, 1);
        float d = dusk();
        int skyTop, skyBot;
        if (d < 0.5f) {
            skyTop = lerpC(SKY_DAY[0], SKY_DUSK[0], d / 0.5f);
            skyBot = lerpC(SKY_DAY[1], SKY_DUSK[1], d / 0.5f);
        } else {
            skyTop = lerpC(SKY_DUSK[0], SKY_NIGHT[0], (d - 0.5f) / 0.5f);
            skyBot = lerpC(SKY_DUSK[1], SKY_NIGHT[1], (d - 0.5f) / 0.5f);
        }
        float groundTop = carBase - 20;
        int bands = 6;
        for (int i = 0; i < bands; i++) {
            float y0 = t + (groundTop - t) * i / bands;
            c.fillRect(0, y0, W, (groundTop - t) / bands + 1, lerpC(skyTop, skyBot, (i + 0.5f) / bands));
        }
        if (night > 0.2f) {
            for (int i = 0; i < 16; i++) {
                float sx = (i * 97 % 340) + 10, sy = t + 26 + (i * 53 % 70);
                float tw = 0.5f + 0.5f * (float) Math.sin(time * 2 + i * 1.7f);
                c.fillCircle(sx, sy, 1.1f, alpha(0xFFFFFFFF, night * (0.4f + 0.5f * tw)));
            }
        }
        // sun by day, moon by night, a couple of clouds while it is light
        float cxs = 318, cys = t + 50 + 26 * d;
        c.fillCircle(cxs, cys, 15, lerpC(0xFFFFE9A0, 0xFFE8ECF5, night));
        if (night > 0.2f) c.fillCircle(cxs + 6, cys - 3, 13, lerpC(skyTop, skyBot, 0.1f));
        if (night < 0.6f) {
            int cl = alpha(0xFFFFFFFF, 0.55f * (1 - night));
            float cd = (time * 2) % 420 - 60;
            c.fillRoundRect(cd, t + 62, 50, 12, 6, cl);
            c.fillRoundRect(cd + 16, t + 55, 28, 12, 6, cl);
        }
        // distant town on the horizon
        int skyline = Ui.darken(lerpC(skyBot, 0xFF2A3345, 0.55f), 0.9f);
        for (int i = 0; i < 9; i++) {
            float bx = i * 44 - 10, bh = 14 + (i * 37 % 26);
            c.fillRect(bx, groundTop - bh, 38, bh + 2, skyline);
            if (night > 0.3f && i % 2 == 0) {
                c.fillRect(bx + 8, groundTop - bh + 6, 4, 4, alpha(0xFFFFE08A, night));
                c.fillRect(bx + 22, groundTop - bh + 12, 4, 4, alpha(0xFFFFE08A, night * 0.8f));
            }
        }
        // forecourt
        int asphalt = lerpC(0xFF5A6270, 0xFF1C212B, d * 0.8f);
        c.fillRect(0, groundTop, W, b - groundTop, asphalt);
        c.fillRect(0, groundTop, W, 3, lerpC(0xFF8C93A0, 0xFF3A4252, d * 0.8f));
        for (int i = 0; i < 6; i++) c.fillRect(150 + i * 36, carBase + 6, 20, 3, alpha(0xFFFFFFFF, 0.2f));

        // canopy and its pillar
        c.fillRect(165, t + 14, 9, carBase - t - 14, lerpC(0xFFB5BCC8, 0xFF3C4452, d * 0.7f));
        c.fillRect(0, t + 2, W, 12, lerpC(0xFFD3D8E0, 0xFF48515F, d * 0.7f));
        c.fillRect(0, t + 12, W, 4, 0xFFD8453A);
        for (int i = 0; i < 6; i++) {
            float lx = 30 + i * 60;
            if (night > 0.1f) c.fillPoly(new float[]{lx - 5, t + 16, lx + 5, t + 16, lx + 26, t + 46, lx - 26, t + 46}, alpha(0xFFFFF2B0, 0.07f + 0.1f * night));
            c.fillRoundRect(lx - 7, t + 16, 14, 3, 1.5f, 0xFFFFF2C8);
        }

        drawPump(c, t, d);

        // the car waiting behind (only a slice of it shows)
        drawCar(c, queueType, queuePaint, W + 36, carBase - 6, 0.7f, night > 0.3f, 0, true);

        // exhaust while moving
        if (phase == ARRIVE || (phase == LEAVE && pt > leaveHold)) {
            float lenHalf = LEN[carType] * carScale / 2;
            for (int i = 0; i < 4; i++) {
                float k = (time * 3 + i * 0.25f) % 1f;
                c.fillCircle(carX + lenHalf + 4 + k * 22, carBase - 12 - k * 8, 2 + k * 4, alpha(0xFFCCD2DA, 0.35f * (1 - k)));
            }
        }
        float bob = (phase == WAIT || phase == PUMP) ? (float) Math.sin(time * 38) * 0.35f : 0;
        boolean lit = night > 0.3f || dusk() > 0.45f;
        drawCar(c, carType, carPaint, carX, carBase + bob, carScale, night > 0.3f, wheelRot, lit);
        drawWindscreen(c);
        drawHose(c, t);
        drawBubble(c);
        drawGauge(c);
        drawButtons(c);
    }

    private void drawPump(Gfx c, float t, float d) {
        float py = t + 22, ph = carBase + 2 - py;
        c.fillRoundRect(PUMP_X - 2, carBase - 4, PUMP_W + 8, 12, 3, lerpC(0xFF9AA2AF, 0xFF3A4252, d * 0.7f));
        c.fillRoundRect(PUMP_X, py, PUMP_W, ph, 8, lerpC(0xFFEEF0F3, 0xFFA8AFBA, d * 0.6f));
        c.fillRoundRect(PUMP_X, py, PUMP_W, 22, 8, 0xFFD8453A);
        c.fillRect(PUMP_X, py + 12, PUMP_W, 10, 0xFFD8453A);
        // the counter: big, in a dark housing
        float dx = PUMP_X + 6, dy = panelY, dw = PUMP_W - 12, dh = 58;
        c.fillRoundRect(dx - 2, dy - 2, dw + 4, dh + 4, 7, 0xFF3A4350);
        c.fillRoundRect(dx, dy, dw, dh, 6, 0xFF07110C);
        String shown = Integer.toString((int) Math.min(x, 999));
        boolean live = phase == PUMP || phase == LEAVE;
        boolean hide = flicker && phase == PUMP;
        int col = 0xFF7CFFB0;
        float size = 42;
        if (phase == LEAVE && flash > 0) {
            col = flashCol;
            size = 42 * (1 + 0.14f * flash / 0.5f);
        }
        String ghost = shown.length() >= 3 ? "888" : shown.length() == 2 ? "88" : "8";
        if (!live) shown = "0";
        c.text(ghost.length() > shown.length() ? ghost : ghost, dx + dw - 8, dy + 46, 42, 0xFF0F2A1A, Gfx.RIGHT);
        if (!hide) c.text(shown, dx + dw - 8, dy + 46, size, col, Gfx.RIGHT);
        c.text("₪", dx + 12, dy + 18, 16, 0xFF3E8F63, Gfx.LEFT);
        // body details
        float by = dy + dh + 10;
        c.fillRoundRect(PUMP_X + 12, by, 36, 14, 4, 0xFF2C3644);
        c.fillRoundRect(PUMP_X + 56, by, 36, 14, 4, 0xFF2C3644);
        c.fillCircle(PUMP_X + 30, by + 7, 3, turbo ? Ui.ORANGE : 0xFF5A6572);
        c.fillCircle(PUMP_X + 74, by + 7, 3, phase == PUMP ? Ui.GOOD : 0xFF5A6572);
        // holster on the right side
        c.fillRoundRect(PUMP_X + PUMP_W - 4, by + 18, 12, 24, 4, 0xFF2A3038);
    }

    private float holsterX() {
        return PUMP_X + PUMP_W + 6;
    }

    private float holsterY() {
        return panelY + 58 + 10 + 18 + 8;
    }

    private void drawHose(Gfx c, float t) {
        float sx = holsterX(), sy = holsterY();
        float fx = carX + FLAP[carType][0] * carScale, fy = carBase + FLAP[carType][1] * carScale;
        float ex = sx + (fx - sx) * hoseT, ey = (sy + 8) + (fy - (sy + 8)) * hoseT;
        float pump = phase == PUMP ? 1 : 0;
        float sag = 34 + (float) Math.sin(time * 2) * 2 + (1 - hoseT) * 6;
        float mx = (sx + ex) / 2, my = (sy + ey) / 2 + sag;
        float jx = pump * (float) Math.sin(time * 55) * 0.8f;
        float px = sx, py = sy;
        int segs = 12;
        for (int i = 1; i <= segs; i++) {
            float u = i / (float) segs, iu = 1 - u;
            float qx = iu * iu * sx + 2 * iu * u * (mx + jx) + u * u * ex;
            float qy = iu * iu * sy + 2 * iu * u * my + u * u * ey;
            c.line(px, py, qx, qy, 5.5f, 0xFF161A20);
            c.line(px, py, qx, qy, 2.2f, 0xFF39424F);
            px = qx;
            py = qy;
        }
        // nozzle
        c.fillCircle(ex, ey, 4.2f, 0xFF20252C);
        c.fillRect(ex - 3, ey - 9, 6, 9, 0xFF20252C);
        c.fillRect(ex - 6, ey - 10, 7, 3, 0xFF8A94A3);
        if (phase == PUMP && hoseT > 0.9f) {
            // fuel visibly moving along the hose, faster as the counter speeds up
            float speed = 1.2f + 3 * Ui.clamp(x / Math.max(target, 1), 0, 1.2f);
            for (int i = 0; i < 6; i++) {
                float u = (th * speed + i / 6f) % 1f, iu = 1 - u;
                float qx = iu * iu * sx + 2 * iu * u * (mx + jx) + u * u * ex;
                float qy = iu * iu * sy + 2 * iu * u * my + u * u * ey;
                c.fillCircle(qx, qy, 2f, 0xFFFFE08A);
            }
        }
    }

    private void drawWindscreen(Gfx c) {
        if (phase == ARRIVE) return;
        float s = carScale, cy = carBase - 22 * s;
        if (dirty) {
            float[] w = WIN1[carType];
            float gx = carX + (w[0] + w[2] + w[4] + w[6]) / 4 * s, gy = carBase + (w[1] + w[3] + w[5] + w[7]) / 4 * s;
            float a = 0.7f * (1 - Ui.clamp(wipeProg / WIPE_NEED, 0, 1));
            if (a > 0.02f) {
                c.fillCircle(gx - 6 * s, gy + 1, 7 * s, alpha(0xFF7A6A48, a));
                c.fillCircle(gx + 5 * s, gy - 3 * s, 6 * s, alpha(0xFF6B5C3E, a));
                c.fillCircle(gx, gy + 5 * s, 5 * s, alpha(0xFF857550, a));
            }
        }
        if (dirty && !wipeDone && phase == WAIT && !wiping && wipeProg < 1) {
            // Hint: a sponge sweeping to and fro, and a pill that says what it is worth.
            float sx = carX + 38 * (float) Math.sin(time * 4);
            drawSponge(c, sx, carTop() - 4, 0.8f);
            float pw = 168;
            c.fillRoundRect(carX - pw / 2, cy - 2, pw, 20, 10, 0xE60B0E12);
            c.text("החליקו כאן לניגוב · טיפ " + Ui.nis(TIP_WIPE), carX, cy + 12, 12, Ui.ACCENT, Gfx.CENTER);
        }
        if (wiping) {
            drawSponge(c, lastWx, Ui.clamp(lastWy, carTop(), carBase), 1f);
            for (int i = 0; i < 5; i++) {
                float k = (time * 2.5f + i * 0.2f) % 1f;
                c.fillCircle(lastWx - 14 + i * 7, lastWy - k * 14, 2 + (i % 2), alpha(0xFFFFFFFF, 0.8f * (1 - k)));
            }
            Ui.bar(c, carX - 40, carTop() - 22, 80, 6, wipeProg / WIPE_NEED, Ui.BLUE);
        }
    }

    private void drawSponge(Gfx c, float sx, float sy, float k) {
        c.fillRoundRect(sx - 14 * k, sy - 8 * k, 28 * k, 16 * k, 4, 0xFFF2D04E);
        c.fillRoundRect(sx - 14 * k, sy + 3 * k, 28 * k, 5 * k, 3, 0xFF3FA66B);
        c.fillCircle(sx - 5 * k, sy - 2 * k, 1.8f * k, 0xFFC9A22E);
        c.fillCircle(sx + 4 * k, sy - 3 * k, 1.5f * k, 0xFFC9A22E);
    }

    private void drawBubble(Gfx c) {
        if (phase == ARRIVE && carX > W) return;
        float bx = 140, bw = 212, bh = 74;
        float by = Math.max(top + 30, carTop() - 24 - bh);
        float cxTail = Ui.clamp(carX, bx + 30, bx + bw - 30);
        boolean hurry = pat / patMax < 0.3f && phase != LEAVE;
        float shake = hurry ? (float) Math.sin(time * 40) * 1.2f : 0;
        bx += shake;
        c.fillPoly(new float[]{cxTail - 8, by + bh - 1, cxTail + 8, by + bh - 1, cxTail + shake, by + bh + 14}, 0xFFF4F1E8);
        c.fillRoundRect(bx, by, bw, bh, 12, 0xFFF4F1E8);
        int m = phase == LEAVE ? mood : (hurry ? 2 : 0);
        face(c, bx + 26, by + 30, 15, m);
        c.text(phrase(), bx + bw - 12, by + 24, 14, 0xFF3A4350, Gfx.RIGHT);
        if (full) {
            c.text("מלא", bx + bw - 12, by + 54, 30, 0xFF1D232C, Gfx.RIGHT);
            // a tiny gauge so the word has a picture
            c.fillRoundRect(bx + 52, by + 36, 56, 12, 6, 0xFF1D232C);
            c.fillRoundRect(bx + 54, by + 38, 52, 8, 4, 0xFF4FB286);
        } else {
            c.text(Ui.nis(target), bx + bw - 12, by + 54, 32, 0xFF1D232C, Gfx.RIGHT);
        }
        float u = Ui.clamp(pat / patMax, 0, 1);
        int pc = u > 0.5f ? Ui.GOOD : u > 0.25f ? Ui.ORANGE : Ui.BAD;
        if (u < 0.25f && ((int) (time * 6) & 1) == 0) pc = 0xFFFFFFFF;
        c.fillRoundRect(bx + 10, by + bh - 10, bw - 20, 6, 3, 0xFFD6D2C4);
        c.fillRoundRect(bx + 10, by + bh - 10, Math.max(6, (bw - 20) * u), 6, 3, pc);
    }

    private void drawGauge(Gfx c) {
        float gx = 24, gw = 312, gy = gaugeY, gh = 16;
        float p = tp(), scaleMax = target + 6 * baseTp();
        c.fillRoundRect(gx - 2, gy - 2, gw + 4, gh + 4, 10, 0xFF0B0E12);
        float zs = (target - 3.2f * p) / scaleMax, ze = (target + 2.0f * p) / scaleMax;
        c.fillRect(gx + gw * zs, gy, gw * (ze - zs), gh, alpha(Ui.ACCENT, 0.25f));
        float ps = (target - p) / scaleMax, pe = (target + p) / scaleMax;
        c.fillRect(gx + gw * ps, gy, gw * (pe - ps), gh, alpha(Ui.GOOD, 0.55f));
        float f = Ui.clamp(x / scaleMax, 0, 1);
        if (f > 0.01f) {
            float err = x - target;
            int col = err < -3.2f * p ? Ui.BLUE : err < -p ? Ui.ACCENT : err <= p ? Ui.GOOD : err <= 2 * p ? Ui.ORANGE : Ui.BAD;
            c.fillRoundRect(gx, gy, Math.max(gh, gw * f), gh, gh / 2, col);
        }
        float nx = gx + gw * target / scaleMax;
        c.line(nx, gy - 5, nx, gy + gh + 5, 3, 0xFFFFFFFF);
        c.fillPoly(new float[]{nx - 6, gy - 13, nx + 6, gy - 13, nx, gy - 4}, 0xFFFFFFFF);
        c.text(full ? "מלא" : Ui.nis(target), nx, gy - 16, 12, Ui.INK, Gfx.CENTER);
    }

    private void drawButtons(Gfx c) {
        boolean ready = phase == WAIT && !holding;
        // turbo handle
        boolean locked = pumped || phase != WAIT;
        int tb = turbo ? Ui.ORANGE : Ui.PANEL2;
        c.fillRoundRect(12, btnY, 84, btnH, 16, locked && !turbo ? Ui.PANEL : tb);
        c.strokeRoundRect(12, btnY, 84, btnH, 16, 2, turbo ? 0xFFFFD2A8 : Ui.DIM);
        float lx = 12 + 42, ly = btnY + btnH / 2 - 8;
        int ic = turbo ? Ui.DARK_INK : (locked ? Ui.DIM : Ui.ORANGE);
        c.fillPoly(new float[]{lx + 4, ly - 14, lx - 8, ly + 3, lx - 1, ly + 3, lx - 4, ly + 16, lx + 8, ly - 3, lx + 1, ly - 3}, ic);
        c.text("טורבו", lx, btnY + btnH - 10, 14, turbo ? Ui.DARK_INK : (locked ? Ui.DIM : Ui.INK), Gfx.CENTER);
        // pump trigger
        float px = 106, pw = 242;
        int col = holding ? Ui.GOOD : ready ? 0xFF2E7D55 : Ui.PANEL;
        float pulse = ready ? 0.5f + 0.5f * (float) Math.sin(time * 5) : 0;
        c.fillRoundRect(px, btnY, pw, btnH, 18, col);
        c.strokeRoundRect(px, btnY, pw, btnH, 18, 2 + pulse * 2, holding ? 0xFFD9FFE8 : ready ? Ui.GOOD : Ui.PANEL2);
        String label = holding ? "שחררו על המספר!" : ready ? "החזיקו לתדלק" : phase == ARRIVE ? "הרכב מגיע..." : "";
        c.text(label, px + pw / 2 + 14, btnY + btnH / 2 + 7, 20, ready || holding ? Ui.INK : Ui.DIM, Gfx.CENTER);
        float ix = px + 28, iy = btnY + btnH / 2;
        int ic2 = ready || holding ? 0xFFEAFBF1 : Ui.DIM;
        c.fillCircle(ix, iy + 4, 9, ic2);
        c.fillPoly(new float[]{ix - 8, iy + 1, ix + 8, iy + 1, ix, iy - 15}, ic2);
    }

    // ------------------------------------------------------------------ intro art

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        float base = cy + 40;
        c.fillRoundRect(cx - 120, base - 2, 240, 8, 4, 0xFF3A4252);
        c.fillRoundRect(cx - 108, cy - 56, 46, base - cy + 56, 7, 0xFFEEF0F3);
        c.fillRoundRect(cx - 108, cy - 56, 46, 14, 7, 0xFFD8453A);
        c.fillRoundRect(cx - 104, cy - 38, 38, 22, 4, 0xFF07110C);
        c.text(Integer.toString((int) (t * 37) % 100), cx - 70, cy - 20, 20, 0xFF7CFFB0, Gfx.RIGHT);
        carScale = 1;
        drawCar(c, 0, 1, cx + 36, base, 1f, false, t * 4, true);
        float ex = cx + 36 - 38, ey = base - 20;
        float px = cx - 62, py = cy + 4;
        for (int i = 0; i < 10; i++) {
            float u = i / 10f, v = (i + 1) / 10f;
            float x1 = px + (ex - px) * u, y1 = py + (ey - py) * u + (float) Math.sin(u * Math.PI) * 22;
            float x2 = px + (ex - px) * v, y2 = py + (ey - py) * v + (float) Math.sin(v * Math.PI) * 22;
            c.line(x1, y1, x2, y2, 4, 0xFF39424F);
        }
        c.fillCircle(ex, ey, 4, 0xFF20252C);
    }
}
