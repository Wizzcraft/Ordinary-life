package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Casino venue: a single-zero roulette wheel (37 pockets) on a green felt table.
 *
 * The player picks a chip, taps bets on the table (colour, parity, halves, dozens, or any single
 * number through the number sheet) and spins. The winning number is drawn from {@code h.rnd()} the
 * moment the spin starts; the animation is then planned backwards so the wheel and the ball land
 * on exactly that pocket. Stakes leave the wallet when a chip is placed and winnings come back
 * through {@code h.pay} when the ball settles.
 *
 * Every bet pays 36/37 of the stake on average (97.3%): a straight number pays 35:1, a dozen 2:1,
 * the even-money bets 1:1, and zero takes them all.
 */
final class RouletteMini extends Mini {
    // ---- the maths (also used by CasinoCheck) ----

    /** Pockets in wheel order, clockwise. */
    static final int[] ORDER = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16,
            33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    static final boolean[] RED = new boolean[37];

    static {
        for (int n : new int[]{1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}) RED[n] = true;
    }

    /** Bet ids: 0..36 are straight numbers, then the outside bets. */
    static final int B_RED = 37, B_BLACK = 38, B_ODD = 39, B_EVEN = 40, B_LOW = 41, B_HIGH = 42, B_D1 = 43, B_D2 = 44,
            B_D3 = 45, BETS = 46;
    static final int[] CHIPS = {10, 25, 50, 100};

    /** Multiple of the stake handed back (stake included) when the ball lands on {@code n}. */
    static int returnMult(int bet, int n) {
        if (bet < 37) return bet == n ? 36 : 0;
        if (n == 0) return 0;
        boolean win;
        switch (bet) {
            case B_RED: win = RED[n]; break;
            case B_BLACK: win = !RED[n]; break;
            case B_ODD: win = n % 2 == 1; break;
            case B_EVEN: win = n % 2 == 0; break;
            case B_LOW: win = n <= 18; break;
            case B_HIGH: win = n >= 19; break;
            case B_D1: return n <= 12 ? 3 : 0;
            case B_D2: return n >= 13 && n <= 24 ? 3 : 0;
            case B_D3: return n >= 25 ? 3 : 0;
            default: return 0;
        }
        return win ? 2 : 0;
    }

    /** The one place the winning number is drawn. */
    static int draw(Random r) {
        return r.nextInt(37);
    }

    // ---- spin plan ----
    private static final float TAU = (float) (Math.PI * 2), STEP = TAU / 37f;
    /** Whole spin, and the moment the ball leaves the track and falls into the pockets. */
    private static final float T = 6.0f, T_DROP = 3.9f, DROP = T - T_DROP;
    private static final float BALL_TURNS = 5.5f, MIX = 0.25f, DAMP = 2.4f, OMEGA = 5.5f;

    private static final int BET = 0, SPIN = 1;
    private static final int FELT = 0xFF0E4A32, FELT2 = 0xFF136040, BRASS = 0xFFC9A04A, BRASS_D = 0xFF7A5524;

    private int state, chip;
    private final int[] bets = new int[BETS], last = new int[BETS];
    private int total, lastTotal;
    /** What the latest spin staked and returned; read by CasinoCheck. */
    int lastStake, lastWin;
    private boolean sheet, leave;

    // wheel and ball
    private float th, t, th0, phi0, wtot, x0, target, trackR, pocketR, rb;
    private int result = -1, resIdx, tickIdx = -1;
    private boolean ballOn, lit;
    private float ballPsi, litT, tickGap, flashT;
    private final boolean[] won = new boolean[BETS];
    private final int[] hist = new int[5];
    private int histN;
    private String msg = "";
    private int msgCol = Ui.DIM;
    private float time;

    // layout (recomputed every draw, and once at begin so touches before the first draw are safe)
    private float lTop, lBottom, rowH, wheelY, wheelH, cx, cy, R, panX0, panX1, dozY, outY, chipY, actY;
    private final float[] bx = new float[BETS], by = new float[BETS], bw = new float[BETS], bh = new float[BETS];
    private float sheetTop, sheetBottom;
    private Btn exitB, clearB, againB, spinB, numB;
    private final Btn[] chipB = new Btn[4];

    // flying coins
    private static final class Fly {
        float x0, y0, x1, y1, t, delay;
        boolean lose;
        int val;
    }

    private final ArrayList<Fly> flies = new ArrayList<>();

    // bot
    private int botWait, botStep, botSpins;

    @Override
    int rounds() {
        return 0;
    }

    @Override
    String title() {
        return "רולטה";
    }

    @Override
    String howTo() {
        return "בוחרים אסימון, מניחים הימורים על השולחן ולוחצים סובב. אפשר כמה הימורים בסיבוב אחד, ואפס לוקח את כולם.";
    }

    @Override
    void begin(int round, float difficulty) {
        if (total > 0) h.pay(total);
        java.util.Arrays.fill(bets, 0);
        total = 0;
        state = BET;
        sheet = false;
        leave = false;
        t = 0;
        flies.clear();
        flashT = 0;
        msg = "";
        chip = (int) Ui.clamp(Math.round(h.var("casino.roulette.chip")), 0, CHIPS.length - 1);
        fixChip();
        layout(Game.HUD_H + 74, h.screenH() - 96);
    }

    /** Moves the selection down to a chip the player can afford, if there is one. */
    private void fixChip() {
        while (chip > 0 && h.money() < CHIPS[chip]) chip--;
    }

    @Override
    void pause() {
        // A spin that has not been resolved gives the stakes back, and so do bets not yet spun.
        if (total > 0) h.pay(total);
        java.util.Arrays.fill(bets, 0);
        total = 0;
        state = BET;
        sheet = false;
        t = 0;
        lit = false;
        flies.clear();
    }

    @Override
    boolean wantsExit() {
        return leave;
    }

    // ---- update ----

    @Override
    int update(float dt) {
        time += dt;
        if (flashT > 0) flashT -= dt;
        if (lit) litT += dt;
        for (int i = flies.size() - 1; i >= 0; i--) {
            Fly f = flies.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
            } else {
                f.t += dt / 0.9f;
                if (f.t >= 1) flies.remove(i);
            }
        }
        if (state == BET) {
            th += 0.16f * dt;
            return RUNNING;
        }
        t += dt;
        tickGap -= dt;
        if (t >= T) {
            t = T;
            settle();
            return RUNNING;
        }
        th = wheelAngle(t);
        if (t > T_DROP) {
            float s = t - T_DROP;
            float psi = target + dropOffset(s);
            int idx = Math.floorMod(Math.round(psi / STEP), 37);
            if (idx != tickIdx) {
                if (tickIdx >= 0 && tickGap <= 0) {
                    h.sfx(Sfx.TICK);
                    tickGap = 0.05f;
                }
                tickIdx = idx;
            }
        }
        return RUNNING;
    }

    private static float easeE(float t) {
        float u = 1 - Math.min(t, T) / T;
        return 1 - (float) Math.pow(u, 2.4);
    }

    private static float easeD(float t) {
        float u = Math.max(0, 1 - t / T);
        return DAMP * (float) Math.pow(u, 1.4) / T;
    }

    private float wheelAngle(float tt) {
        return th0 + wtot * easeE(tt);
    }

    /** Offset of the ball from the middle of its pocket, in the wheel's own frame, once it has dropped. */
    private float dropOffset(float s) {
        float u = Ui.clamp(s / DROP, 0, 1);
        float taper = 1 - u * u * u * u * u;
        return x0 * (float) (Math.exp(-DAMP * s) * Math.cos(OMEGA * s)) * taper;
    }

    /** Plans the whole spin around the already drawn result so that wheel and ball arrive on it together. */
    private void plan() {
        target = resIdx * STEP;
        th0 = th;
        phi0 = ballOn ? th + ballPsi : th + h.rnd().nextFloat() * TAU;
        float b = BALL_TURNS * TAU;
        float phiD = phi0 - b;
        float phiDv = -b / T_DROP * MIX;
        float ed = easeE(T_DROP), e1 = easeD(T_DROP);
        float den = ed + e1 / DAMP;
        float base = phiD - th0 - target + phiDv / DAMP;
        float min = 3.2f * TAU;
        int k = (int) Math.floor((base - min * den) / TAU);
        wtot = (base - TAU * k) / den;
        x0 = (wtot * e1 - phiDv) / DAMP;
        tickIdx = -1;
    }

    /** Ball position (angle, radius) at time tt. */
    private void ballAt(float tt, float[] out) {
        if (tt < T_DROP) {
            float u = tt / T_DROP, b = BALL_TURNS * TAU;
            out[0] = phi0 - b * (MIX * u + (1 - MIX) * (1 - (1 - u) * (1 - u)));
            float up = Ui.clamp(tt / 0.45f, 0, 1);
            float r = ballOn ? pocketR + (trackR - pocketR) * up * up * (3 - 2 * up) : trackR;
            float in = Ui.clamp((tt - (T_DROP - 0.9f)) / 0.9f, 0, 1);
            out[1] = r - 3 * in * in;
        } else {
            float s = tt - T_DROP;
            out[0] = wheelAngle(tt) + target + dropOffset(s);
            float f = Ui.clamp(s / 0.5f, 0, 1);
            float r = trackR - 3 + (pocketR - trackR + 3) * f * f;
            float hop = (float) Math.abs(Math.sin(s * 13)) * R * 0.05f * (float) Math.exp(-2.3 * s);
            out[1] = r - hop;
        }
    }

    private void startSpin() {
        if (state != BET || total <= 0) return;
        sheet = false;
        lit = false;
        state = SPIN;
        t = 0;
        // The result is decided here and nowhere else.
        result = draw(h.rnd());
        resIdx = indexOf(result);
        plan();
        msg = "הכדור מסתובב";
        msgCol = Ui.DIM;
        h.sfx(Sfx.WHOOSH);
    }

    static int indexOf(int n) {
        for (int i = 0; i < 37; i++) if (ORDER[i] == n) return i;
        return 0;
    }

    /** Pocket (number) the ball sits in at the end of the planned spin; CasinoCheck compares it with the result. */
    int landedNumber() {
        float[] o = new float[2];
        ballAt(T, o);
        float psi = o[0] - wheelAngle(T);
        return ORDER[Math.floorMod(Math.round(psi / STEP), 37)];
    }

    int[] lastBets() {
        return last.clone();
    }

    int lastResult() {
        return result;
    }

    private void settle() {
        state = BET;
        int win = 0;
        for (int b = 0; b < BETS; b++) {
            won[b] = false;
            if (bets[b] <= 0) continue;
            int m = returnMult(b, result);
            if (m > 0) {
                won[b] = true;
                win += bets[b] * m;
            }
        }
        int net = win - total;
        lastStake = total;
        lastWin = win;
        if (win > 0) h.pay(win);
        ballOn = true;
        ballPsi = target;
        th = wheelAngle(T);
        lit = true;
        litT = 0;
        flashT = 1.6f;
        for (int i = hist.length - 1; i > 0; i--) hist[i] = hist[i - 1];
        hist[0] = result;
        histN = Math.min(hist.length, histN + 1);
        // chips: winners fly to the wallet, losers are swept away
        int coins = 0;
        for (int b = 0; b < BETS; b++) {
            if (bets[b] <= 0) continue;
            float sx = sheetCellX(b), sy = sheetCellY(b);
            int n = won[b] ? Math.min(5, 2 + bets[b] * returnMult(b, result) / 200) : 1;
            for (int j = 0; j < n && flies.size() < 40; j++) {
                Fly f = new Fly();
                f.x0 = sx + (j - n / 2f) * 4;
                f.y0 = sy;
                f.lose = !won[b];
                f.x1 = f.lose ? (panX0 + panX1) / 2 : W / 2 + 20;
                f.y1 = f.lose ? wheelY : lTop - 80;
                f.delay = 0.15f + 0.09f * (coins++);
                f.val = bets[b];
                flies.add(f);
            }
        }
        System.arraycopy(bets, 0, last, 0, BETS);
        lastTotal = total;
        java.util.Arrays.fill(bets, 0);
        total = 0;
        float mx = (panX0 + panX1) / 2;
        if (net > 0) {
            msg = "זכית " + Ui.nis(net);
            msgCol = Ui.GOOD;
            h.text(mx, wheelY + wheelH * 0.5f, "+" + Ui.nis(win), Ui.ACCENT);
            h.burst(mx, wheelY + wheelH * 0.5f, Ui.ACCENT, net >= lastTotal * 5 ? 26 : 12);
            h.sfx(net >= lastTotal * 5 ? Sfx.PERFECT : Sfx.COIN);
        } else if (net == 0 && win > 0) {
            msg = "יצאת בלי הפסד";
            msgCol = Ui.INK;
            h.sfx(Sfx.GOOD);
        } else {
            msg = "הפסדת " + Ui.nis(-net);
            msgCol = Ui.BAD;
            h.sfx(Sfx.BAD);
            if (result == 0) h.shake(5);
        }
        h.sfx(Sfx.DING);
        fixChip();
    }

    // ---- touches ----

    @Override
    void down(float x, float y) {
        buildButtons();
        if (exitB.hit(x, y)) {
            if (total > 0) h.pay(total);
            java.util.Arrays.fill(bets, 0);
            total = 0;
            leave = true;
            return;
        }
        if (state != BET) return;
        if (spinB.hit(x, y)) {
            startSpin();
            return;
        }
        if (clearB.hit(x, y)) {
            if (total > 0) {
                h.pay(total);
                java.util.Arrays.fill(bets, 0);
                total = 0;
                h.sfx(Sfx.TICK);
            }
            return;
        }
        if (againB.hit(x, y)) {
            if (total > 0) h.pay(total);
            java.util.Arrays.fill(bets, 0);
            total = 0;
            if (lastTotal > 0 && h.pay(-lastTotal)) {
                System.arraycopy(last, 0, bets, 0, BETS);
                total = lastTotal;
                h.sfx(Sfx.TAP);
            }
            return;
        }
        if (numB.hit(x, y)) {
            sheet = !sheet;
            h.sfx(Sfx.TAP);
            return;
        }
        for (int i = 0; i < chipB.length; i++) {
            if (chipB[i].hit(x, y)) {
                chip = i;
                h.setVar("casino.roulette.chip", i);
                h.sfx(Sfx.TAP);
                return;
            }
        }
        int from = sheet ? 0 : B_RED, to = sheet ? 37 : BETS;
        for (int b = from; b < to; b++) {
            if (Ui.in(x, y, bx[b], by[b], bw[b], bh[b])) {
                addBet(b);
                return;
            }
        }
    }

    private void addBet(int b) {
        int v = CHIPS[chip];
        if (h.pay(-v)) {
            bets[b] += v;
            total += v;
            h.sfx(Sfx.TAP);
        } else {
            h.text(bx[b] + bw[b] / 2, by[b], "אין מספיק כסף", Ui.DIM);
            h.sfx(Sfx.TICK);
        }
    }

    private float sheetCellX(int b) {
        return bx[b] + bw[b] / 2;
    }

    private float sheetCellY(int b) {
        return by[b] + bh[b] / 2;
    }

    // ---- layout ----

    private void layout(float top, float bottom) {
        lTop = top;
        lBottom = bottom;
        float H = bottom - top, gap = 4;
        rowH = H >= 420 ? 50 : 38;
        float used = 4 * rowH + 5 * gap;
        wheelH = Math.min(H - used, 236);
        float y = top + Math.max(0, (H - used - wheelH) / 2);
        wheelY = y;
        y += wheelH + gap;
        dozY = y;
        y += rowH + gap;
        outY = y;
        y += rowH + gap;
        chipY = y;
        y += rowH + gap;
        actY = y;
        R = Math.min(wheelH / 2 - 2, 104);
        cx = 12 + R;
        cy = wheelY + wheelH / 2;
        panX0 = cx + R + 8;
        panX1 = W - 8;
        trackR = R - 11;
        pocketR = R - 17 - (R - 17 - R * 0.5f) * 0.3f;
        rb = Math.max(3.2f, R * 0.052f);

        float tw = (W - 16 - 2 * gap) / 3;
        int[] dz = {B_D1, B_D2, B_D3};
        for (int i = 0; i < 3; i++) {
            set(dz[i], 8 + i * (tw + gap), dozY, tw, rowH);
        }
        float ow = (W - 16 - 5 * gap) / 6;
        int[] out = {B_LOW, B_EVEN, B_RED, B_BLACK, B_ODD, B_HIGH};
        for (int i = 0; i < 6; i++) {
            set(out[i], 8 + i * (ow + gap), outY, ow, rowH);
        }
        // the number sheet covers the wheel, dozens and colours
        sheetTop = wheelY;
        sheetBottom = outY + rowH;
        float sh = sheetBottom - sheetTop, zh = Math.min(36, sh * 0.15f), g = 3;
        set(0, 8, sheetTop, W - 16, zh);
        float cw = (W - 16 - 8 * g) / 9, ch = (sh - zh - 5 * g) / 4;
        for (int n = 1; n <= 36; n++) {
            int r = (n - 1) / 9, c = (n - 1) % 9;
            set(n, 8 + c * (cw + g), sheetTop + zh + g + r * (ch + g), cw, ch);
        }
    }

    private void set(int b, float x, float y, float w, float hh) {
        bx[b] = x;
        by[b] = y;
        bw[b] = w;
        bh[b] = hh;
    }

    private void buildButtons() {
        boolean idle = state == BET;
        int money = h.money();
        exitB = new Btn(0, 8, actY, 62, rowH, "יציאה", idle, false);
        clearB = new Btn(1, 74, actY, 62, rowH, "נקה", idle && total > 0, false);
        againB = new Btn(2, 140, actY, 62, rowH, "שוב", idle && lastTotal > 0 && money + total >= lastTotal, false);
        spinB = new Btn(3, 206, actY, 146, rowH, state == SPIN ? "מסתובב..." : total > 0 ? "סובב!  " + Ui.nis(total) : "סובב!", idle && total > 0, true);
        float cwid = 56;
        for (int i = 0; i < 4; i++) {
            chipB[i] = new Btn(10 + i, 8 + i * (cwid + 4), chipY, cwid, rowH, "", idle && money >= CHIPS[i], false);
        }
        int nums = 0;
        for (int n = 0; n < 37; n++) if (bets[n] > 0) nums++;
        numB = new Btn(20, 8 + 4 * (cwid + 4), chipY, W - 16 - 4 * (cwid + 4), rowH,
                sheet ? "סגור" : nums > 0 ? "מספר (" + nums + ")" : "מספר", idle, sheet);
    }

    // ---- drawing ----

    @Override
    void draw(Gfx c, float top, float bottom) {
        if (top != lTop || bottom != lBottom) layout(top, bottom);
        buildButtons();
        c.fillRoundRect(2, top - 3, W - 4, bottom - top + 6, 14, FELT);
        c.strokeRoundRect(3, top - 2, W - 6, bottom - top + 4, 13, 2, BRASS_D);

        if (!sheet) {
            drawWheel(c, cx, cy, R, th, lit ? resIdx : -1, litT);
            drawBall(c);
            drawPanel(c);
            drawTable(c);
        } else {
            drawSheet(c);
        }
        drawChipRow(c);
        exitB.draw(c);
        clearB.draw(c);
        againB.draw(c);
        spinB.draw(c);
        // lamp strip around the spin button while it is live
        if (spinB.on) {
            float p = (float) (0.5 + 0.5 * Math.sin(time * 5));
            c.strokeRoundRect(spinB.x - 2, spinB.y - 2, spinB.w + 4, spinB.h + 4, 12, 2, ((int) (60 + 120 * p) << 24) | 0xFFE08A);
        }
        drawFlies(c);
    }

    private static final float[] POLY = new float[12];

    /** Draws the wheel; lit >= 0 lights that pocket. Also used for the intro art. */
    static void drawWheel(Gfx c, float cx, float cy, float R, float th, int lit, float litT) {
        c.fillCircle(cx + 2, cy + 3, R + 2, 0x66000000);
        c.fillCircle(cx, cy, R, BRASS);
        c.fillCircle(cx, cy, R - 3, BRASS_D);
        c.fillCircle(cx, cy, R - 5, 0xFF3E2A17);
        c.fillCircle(cx, cy, R - 15, 0xFF2A1C10);
        float rO = R - 17, rI = R * 0.5f;
        c.fillCircle(cx, cy, rO, 0xFFB08A3E);
        for (int i = 0; i < 37; i++) {
            int n = ORDER[i];
            int col = n == 0 ? 0xFF1F8F4E : RED[n] ? 0xFFC8302F : 0xFF1B1D22;
            wedge(c, cx, cy, rI, rO, th + i * STEP, STEP * 0.84f, col);
        }
        if (lit >= 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(litT * 7));
            int a = (int) (90 + 120 * pulse);
            wedge(c, cx, cy, rI, rO, th + lit * STEP, STEP * 0.84f, (a << 24) | 0xFFF3B0);
            float ang = th + lit * STEP;
            c.line(cx + (float) Math.cos(ang) * (rO + 1), cy + (float) Math.sin(ang) * (rO + 1),
                    cx + (float) Math.cos(ang) * (rI - 2), cy + (float) Math.sin(ang) * (rI - 2), 2.2f, 0xFFFFE08A);
        }
        c.fillCircle(cx, cy, rI - 1, 0xFF5C3A1C);
        c.fillCircle(cx, cy, rI * 0.86f, 0xFF7A4E26);
        for (int k = 0; k < 4; k++) {
            float a = th * 1.0f + k * (float) Math.PI / 2;
            c.line(cx, cy, cx + (float) Math.cos(a) * rI * 0.82f, cy + (float) Math.sin(a) * rI * 0.82f, Math.max(2, R * 0.04f), BRASS);
            c.fillCircle(cx + (float) Math.cos(a) * rI * 0.82f, cy + (float) Math.sin(a) * rI * 0.82f, Math.max(2, R * 0.04f), 0xFFE9C873);
        }
        c.fillCircle(cx, cy, R * 0.12f, BRASS);
        c.fillCircle(cx - R * 0.02f, cy - R * 0.02f, R * 0.06f, 0xFFF3DC9A);
    }

    private static void wedge(Gfx c, float cx, float cy, float rI, float rO, float mid, float span, int col) {
        float a0 = mid - span / 2, a1 = mid + span / 2, am = mid;
        POLY[0] = cx + (float) Math.cos(a0) * rO;
        POLY[1] = cy + (float) Math.sin(a0) * rO;
        POLY[2] = cx + (float) Math.cos(am) * rO;
        POLY[3] = cy + (float) Math.sin(am) * rO;
        POLY[4] = cx + (float) Math.cos(a1) * rO;
        POLY[5] = cy + (float) Math.sin(a1) * rO;
        POLY[6] = cx + (float) Math.cos(a1) * rI;
        POLY[7] = cy + (float) Math.sin(a1) * rI;
        POLY[8] = cx + (float) Math.cos(am) * rI;
        POLY[9] = cy + (float) Math.sin(am) * rI;
        POLY[10] = cx + (float) Math.cos(a0) * rI;
        POLY[11] = cy + (float) Math.sin(a0) * rI;
        c.fillPoly(POLY, col);
    }

    private final float[] bo = new float[2];

    private void drawBall(Gfx c) {
        if (state == SPIN) {
            // a short smear behind the ball while it is fast
            for (int k = 3; k >= 1; k--) {
                ballAt(Math.max(0, t - k * 0.022f), bo);
                if (t >= T_DROP && k > 1) continue;
                c.fillCircle(cx + (float) Math.cos(bo[0]) * bo[1], cy + (float) Math.sin(bo[0]) * bo[1], rb, (0x2A - k * 6) << 24 | 0xFFFFFF);
            }
            ballAt(t, bo);
        } else if (ballOn) {
            bo[0] = th + ballPsi;
            bo[1] = pocketR;
        } else {
            return;
        }
        float bxp = cx + (float) Math.cos(bo[0]) * bo[1], byp = cy + (float) Math.sin(bo[0]) * bo[1];
        c.fillCircle(bxp + 1, byp + 1.5f, rb, 0x66000000);
        c.fillCircle(bxp, byp, rb, 0xFFF4F1E8);
        c.fillCircle(bxp - rb * 0.3f, byp - rb * 0.3f, rb * 0.4f, 0xFFFFFFFF);
    }

    private void drawPanel(Gfx c) {
        float pw = panX1 - panX0, mx = (panX0 + panX1) / 2;
        float y = wheelY + Math.max(0, (wheelH - 118) / 2);
        // last results
        float sp = Math.min(26, pw / 5);
        for (int i = 0; i < histN; i++) {
            int n = hist[i];
            float hx = mx + ((histN - 1) / 2f - i) * sp;
            c.fillCircle(hx, y + 12, 12, pocketColor(n));
            c.text(String.valueOf(n), hx, y + 17, 12, 0xFFFFFFFF, Gfx.CENTER);
        }
        if (histN == 0) c.text("אין תוצאות עדיין", mx, y + 17, 12, Ui.DIM, Gfx.CENTER);
        // big number
        float by0 = y + 56;
        if (state == SPIN) {
            c.fillCircle(mx, by0, 25, 0xFF0A2A1D);
            c.text("?", mx, by0 + 9, 28, Ui.DIM, Gfx.CENTER);
        } else if (histN > 0) {
            float k = 1 + (flashT > 0 ? 0.08f * (float) Math.sin(time * 14) : 0);
            c.fillCircle(mx, by0, 27 * k, BRASS);
            c.fillCircle(mx, by0, 24 * k, pocketColor(hist[0]));
            c.text(String.valueOf(hist[0]), mx, by0 + 9, 26, 0xFFFFFFFF, Gfx.CENTER);
        } else {
            c.fillCircle(mx, by0, 25, 0xFF0A2A1D);
        }
        String m = msg.isEmpty() ? (total > 0 ? "מוכנים לסיבוב" : "הניחו הימור") : msg;
        int mc = msg.isEmpty() ? Ui.INK : msgCol;
        c.text(m, mx, y + 101, 15, mc, Gfx.CENTER);
        c.text("על השולחן: " + Ui.nis(total), mx, y + 118, 13, total > 0 ? Ui.ACCENT : Ui.DIM, Gfx.CENTER);
    }

    private static int pocketColor(int n) {
        return n == 0 ? 0xFF1F8F4E : RED[n] ? 0xFFC8302F : 0xFF1B1D22;
    }

    private void drawTable(Gfx c) {
        boolean can = state == BET && h.money() >= CHIPS[chip];
        String[] lab = {"1-12", "13-24", "25-36"};
        int[] dz = {B_D1, B_D2, B_D3};
        for (int i = 0; i < 3; i++) cell(c, dz[i], lab[i], FELT2, 0xFFF3E6C2, can);
        cell(c, B_LOW, "1-18", FELT2, 0xFFF3E6C2, can);
        cell(c, B_EVEN, "זוגי", FELT2, 0xFFF3E6C2, can);
        cell(c, B_RED, "אדום", 0xFFB02828, 0xFFFFFFFF, can);
        cell(c, B_BLACK, "שחור", 0xFF1B1D22, 0xFFFFFFFF, can);
        cell(c, B_ODD, "אי-זוגי", FELT2, 0xFFF3E6C2, can);
        cell(c, B_HIGH, "19-36", FELT2, 0xFFF3E6C2, can);
    }

    /** One bet cell: label, current stake and a gold flash when it won. */
    private void cell(Gfx c, int b, String label, int fill, int ink, boolean en) {
        float x = bx[b], y = by[b], w = bw[b], hh = bh[b];
        boolean win = won[b] && flashT > 0 && ((int) (time * 8) % 2 == 0);
        c.fillRoundRect(x, y, w, hh, 7, en || bets[b] > 0 ? fill : Ui.darken(fill, 0.6f));
        if (win) c.fillRoundRect(x, y, w, hh, 7, 0x88FFE08A);
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, hh - 1, 7, 1.5f, bets[b] > 0 || win ? 0xFFFFE08A : 0x99C9A04A);
        int tc = en || bets[b] > 0 ? ink : (ink & 0xFFFFFF) | 0x77000000;
        if (w > 90) {
            c.text(label, x + w * 0.28f, y + hh / 2 + 5.5f, 16, tc, Gfx.CENTER);
            c.text("×3", x + w * 0.52f, y + hh / 2 + 4.5f, 12, Ui.darken(tc, 0.7f), Gfx.LEFT);
            if (bets[b] > 0) goldChip(c, x + w - 20, y + hh / 2, bets[b]);
        } else {
            float ly = bets[b] > 0 ? y + hh - 6 : y + hh / 2 + 5;
            c.text(label, x + w / 2, ly, 14, tc, Gfx.CENTER);
            if (bets[b] > 0) goldChip(c, x + w - 13, y + 12, bets[b]);
        }
    }

    private void goldChip(Gfx c, float x, float y, int amt) {
        String s = String.valueOf(amt);
        float r = Math.max(11, c.textWidth(s, 12) / 2 + 4);
        c.fillCircle(x, y, r, 0xFFFFC857);
        c.fillCircle(x, y, r - 2, 0xFFE8A92E);
        c.text(s, x, y + 4.3f, 12, 0xFF1A1300, Gfx.CENTER);
    }

    private void drawSheet(Gfx c) {
        boolean can = state == BET && h.money() >= CHIPS[chip];
        c.fillRoundRect(6, sheetTop - 2, W - 12, sheetBottom - sheetTop + 4, 10, FELT2);
        for (int n = 0; n < 37; n++) {
            float x = bx[n], y = by[n], w = bw[n], hh = bh[n];
            int col = pocketColor(n);
            boolean win = won[n] && flashT > 0 && ((int) (time * 8) % 2 == 0);
            c.fillRoundRect(x, y, w, hh, 5, can || bets[n] > 0 ? col : Ui.darken(col, 0.55f));
            if (win) c.fillRoundRect(x, y, w, hh, 5, 0x88FFE08A);
            c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, hh - 1, 5, bets[n] > 0 ? 2.2f : 1, bets[n] > 0 ? 0xFFFFE08A : 0x88C9A04A);
            int ink = can || bets[n] > 0 ? 0xFFFFFFFF : 0x88FFFFFF;
            if (bets[n] > 0) {
                c.text(String.valueOf(n), x + w / 2, y + hh * 0.42f, 16, ink, Gfx.CENTER);
                c.fillRoundRect(x + 2, y + hh - 17, w - 4, 15, 7, 0xFFFFC857);
                c.text(String.valueOf(bets[n]), x + w / 2, y + hh - 5, 12, 0xFF1A1300, Gfx.CENTER);
            } else {
                c.text(n == 0 ? "0 - הימור על אפס (פי 36)" : String.valueOf(n), x + w / 2, y + hh / 2 + 6, n == 0 ? 14 : 17, ink, Gfx.CENTER);
            }
        }
    }

    private void drawChipRow(Gfx c) {
        int[] cols = {0xFF3B7DD8, 0xFF2F9E5B, 0xFF8E52C8, 0xFF22262E};
        for (int i = 0; i < 4; i++) {
            Btn b = chipB[i];
            float mx = b.x + b.w / 2, my = b.y + b.h / 2;
            float r = Math.min(b.h / 2 - 1, 19);
            boolean sel = i == chip;
            if (sel) {
                c.fillCircle(mx, my, r + 4, 0x44FFE08A);
                c.strokeRoundRect(b.x, b.y, b.w, b.h, 9, 2, 0xFFFFE08A);
            }
            int col = b.on ? cols[i] : 0xFF2A2F37;
            c.fillCircle(mx + 1, my + 2, r, 0x66000000);
            c.fillCircle(mx, my, r, col);
            for (int k = 0; k < 8; k++) {
                float a = k * (float) Math.PI / 4;
                c.fillCircle(mx + (float) Math.cos(a) * (r - 2.5f), my + (float) Math.sin(a) * (r - 2.5f), 2.1f, b.on ? 0xFFF5F2E6 : 0xFF444B55);
            }
            c.fillCircle(mx, my, r - 6, Ui.darken(col, 0.8f));
            c.text(String.valueOf(CHIPS[i]), mx, my + 4.6f, 13, b.on ? 0xFFFFFFFF : 0xFF666F7C, Gfx.CENTER);
        }
        numB.draw(c);
    }

    private void drawFlies(Gfx c) {
        for (Fly f : flies) {
            if (f.delay > 0) continue;
            float e = f.t * f.t * (3 - 2 * f.t);
            float x = f.x0 + (f.x1 - f.x0) * e, y = f.y0 + (f.y1 - f.y0) * e - (float) Math.sin(Math.PI * e) * 26;
            if (f.lose) {
                int a = (int) (200 * (1 - f.t));
                c.fillCircle(x, y, 8, (a << 24) | 0x777777);
            } else {
                c.fillCircle(x, y, 7, 0xFFFFC857);
                c.fillCircle(x, y, 4.5f, 0xFFE8A92E);
                c.fillCircle(x - 2, y - 2, 1.6f, 0xFFFFF2B8);
            }
        }
    }

    @Override
    void art(Gfx c, float cx0, float cy0, float time0) {
        drawWheel(c, cx0, cy0, 56, time0 * 1.4f, -1, 0);
        float a = -time0 * 3.2f;
        c.fillCircle(cx0 + (float) Math.cos(a) * 42, cy0 + (float) Math.sin(a) * 42, 3.4f, 0xFFF4F1E8);
    }

    // ---- bot ----

    @Override
    void bot(Random r, float skill) {
        if (state == SPIN) {
            botWait = 12;
            botStep = 0;
            return;
        }
        if (botWait-- > 0) return;
        botWait = 4 + r.nextInt(6);
        buildButtons();
        if (sheet) {
            down(numB.x + 2, numB.y + 2);
            return;
        }
        if (botStep == 0) {
            // Leaves after a few spins, or when it can no longer afford the smallest chip.
            if (h.money() < CHIPS[0] || (botSpins >= 2 && r.nextInt(4) == 0) || botSpins >= 40) {
                botSpins = 0;
                down(exitB.x + 4, exitB.y + 4);
                return;
            }
            down(chipB[0].x + 4, chipB[0].y + 4);
            botStep = 1;
        } else if (botStep == 1 || botStep == 2) {
            int[] even = {B_RED, B_BLACK, B_ODD, B_EVEN, B_LOW, B_HIGH};
            int b = even[r.nextInt(even.length)];
            down(bx[b] + 4, by[b] + 4);
            botStep = botStep == 1 && r.nextBoolean() ? 2 : 3;
        } else {
            down(spinB.x + 4, spinB.y + 4);
            botSpins++;
            botStep = 0;
            botWait = 3;
        }
    }
}
