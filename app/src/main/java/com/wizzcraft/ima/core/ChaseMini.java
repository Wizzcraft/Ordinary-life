package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Police chase event: a one-thumb night runner through the back streets. The runner runs by
 * itself. A tap jumps, holding the finger jumps higher (the jump is cut short on release), which
 * clears bins and puddles with a hop and barriers and dumpsters with a full leap. A tired cop
 * follows and a gap gauge shows how far behind he is: every stumble lets him gain, every clean
 * obstacle pulls the runner ahead. Reach the safe corner before he does.
 *
 * The decision: halfway through there is a scaffold above the street. A full jump near the sign
 * climbs onto it (no ground obstacles up there), but it has a gap in the deck that needs another
 * high jump, and finishing it earns a big lead. Staying on the street is safe but busy.
 *
 * MISS = caught, OK = escaped, PERFECT = escaped without a single stumble.
 * Difficulty is the player's heat: faster run, denser obstacles, a second cop standing in the street.
 */
final class ChaseMini extends Mini {
    // ---- jump physics (exact constant-acceleration integration, so frame rate does not matter) ----
    private static final float G = 1750, JUMP_V = 540, CUT = 0.5f, MIN_HOLD = 0.04f;
    private static final float COYOTE = 0.09f, BUFFER = 0.14f;
    /** Runner's screen x, half width and the forgiveness (units) for clipping a top edge. */
    private static final float RX = 118, HALF = 5, FOOT = 5;

    // ---- obstacle kinds ----
    private static final int PUDDLE = 0, CAT = 1, BIN = 2, BARRIER = 3, DUMP = 4, COP = 5, DECK = 6;
    private static final float DECK_TOP = 62, DECK_BASE = 59;

    // ---- states ----
    private static final int INTRO = 0, RUN = 1, WIN = 2, CAUGHT = 3;
    private static final float INTRO_T = 1.1f, DURATION = 18.5f;
    private static final float CATCH = 30, GAP_CAP = 250;

    private static final int SKIN = 0xFFF2C9A1;

    private static final class Ob {
        int kind;
        float x, w, top, base, hitBelow;
        boolean solid;
        boolean broken, counted, hintShown;
        float near = 999, t;
    }

    private final ArrayList<Ob> obs = new ArrayList<>();
    private float diff = 0.3f, v = 200, len = 4000, wx, py, vy, holdT, gapPx = 170, runMult, runT;
    private float time, introT, outT, slowT, invulT, coyote, buf, plankTimer, stumbleAnim, phase, gy = 400, topY = 100;
    private float hintT, copMult;
    private boolean pressed, grounded = true, jumping, cut, shortEntered, shortDone, hidden, sirenDone;
    private int state, stumbles, combo, result = RUNNING;
    private float shortX = -1, gapX0, gapX1, bEnd;
    private int seq;

    // dust
    private static final int DN = 28;
    private final float[] dx = new float[DN], dy = new float[DN], dage = new float[DN], dsz = new float[DN],
            dvx = new float[DN];
    private int dnext;

    // bot
    private float botT, botHeld, botHold, botPlanD, botNoise;
    private boolean botDown, botWantShort, botDecided, botMiss;
    private Ob botTarget;

    // ------------------------------------------------------------------ Mini API

    @Override
    int rounds() {
        return 1;
    }

    @Override
    String title() {
        return "בריחה";
    }

    @Override
    String howTo() {
        return "לחיצה קופצת, החזקה קופצת גבוה. מדלגים על פחים ומחסומים ומגיעים לפינה לפני השוטר. "
                + "על הפיגום יש קיצור מסוכן.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = Ui.clamp(difficulty, 0, 1);
        v = 176 + 74 * diff;
        len = v * DURATION;
        wx = 0;
        py = 0;
        vy = 0;
        grounded = true;
        jumping = false;
        cut = false;
        holdT = 0;
        gapPx = 188 - 34 * diff;
        runMult = 0;
        copMult = 0;
        runT = 0;
        slowT = invulT = coyote = buf = plankTimer = stumbleAnim = 0;
        outT = introT = 0;
        hintT = 0;
        state = INTRO;
        stumbles = 0;
        combo = 0;
        result = RUNNING;
        pressed = false;
        shortEntered = shortDone = hidden = sirenDone = false;
        for (int i = 0; i < DN; i++) dage[i] = 9;
        botDown = false;
        botTarget = null;
        botDecided = false;
        botT = 0;
        generate();
    }

    @Override
    int update(float dt) {
        if (dt <= 0) return result;
        dt = Math.min(dt, 0.25f);
        time += dt;
        if (!sirenDone && h != null) {
            sirenDone = true;
            h.sfx(Sfx.SIREN);
        }
        int n = (int) Math.ceil(dt / (1f / 90f));
        float sd = dt / n;
        for (int i = 0; i < n; i++) step(sd);
        for (int i = 0; i < DN; i++) {
            if (dage[i] < 2) {
                dage[i] += dt;
                dx[i] += dvx[i] * dt;
            }
        }
        return result;
    }

    @Override
    void down(float x, float y) {
        if (state == INTRO || state == RUN) {
            pressed = true;
            if (state == RUN) buf = BUFFER;
        }
    }

    @Override
    void up(float x, float y) {
        pressed = false;
    }

    @Override
    void pause() {
        pressed = false;
        buf = 0;
    }

    int statStumbles() {
        return stumbles;
    }

    boolean statShort() {
        return shortDone;
    }

    // ------------------------------------------------------------------ level

    private Ob add(int kind, float x) {
        Ob o = new Ob();
        o.kind = kind;
        o.x = x;
        o.solid = true;
        switch (kind) {
            case PUDDLE:
                o.w = 34;
                o.top = 3;
                o.hitBelow = 7;
                o.solid = false;
                break;
            case CAT:
                o.w = 18;
                o.top = 16;
                o.hitBelow = 12;
                o.solid = false;
                break;
            case BIN:
                o.w = 22;
                o.top = 28;
                o.hitBelow = 23;
                break;
            case BARRIER:
                o.w = 26;
                o.top = 46;
                o.hitBelow = 41;
                break;
            case DUMP:
                o.w = 40;
                o.top = 42;
                o.hitBelow = 37;
                break;
            case COP:
                o.w = 14;
                o.top = 56;
                o.hitBelow = 51;
                o.solid = false;
                break;
            default:
                o.top = DECK_TOP;
                o.base = DECK_BASE;
                break;
        }
        obs.add(o);
        return o;
    }

    private static boolean isHigh(int kind) {
        return kind == BARRIER || kind == DUMP || kind == COP;
    }

    /** Lays the whole street out in advance: every obstacle is placed so that a clean line exists. */
    private void generate() {
        obs.clear();
        gapX0 = gapX1 = bEnd = 1e9f;
        Random r = h.rnd();
        shortX = len * (0.40f + 0.06f * r.nextFloat());
        float x = v * 1.75f;
        int copsLeft = diff >= 0.8f ? 2 : diff >= 0.5f ? 1 : 0;
        int sinceHigh = 0, last = -1, same = 0;
        float stop = len - v * 1.7f;
        boolean first = true;
        while (x < stop) {
            if (x + v * 1.3f > shortX && !hasShortPlaced()) {
                placeScaffold(Math.max(shortX, x));
                x = bEnd + v * 1.0f + 190;
                last = -1;
                continue;
            }
            int kind;
            if (first) {
                kind = BIN;
            } else if (obs.size() == 1) {
                kind = BARRIER;
            } else {
                float p = r.nextFloat();
                boolean wantHigh = sinceHigh >= 2 || p < 0.40f + 0.1f * diff;
                if (wantHigh) {
                    kind = BARRIER;
                    float q = r.nextFloat();
                    if (copsLeft > 0 && q < 0.45f && x > len * 0.5f) {
                        kind = COP;
                        copsLeft--;
                    } else if (q < 0.65f) {
                        kind = DUMP;
                    }
                } else {
                    float q = r.nextFloat();
                    kind = q < 0.34f ? PUDDLE : q < 0.62f ? CAT : BIN;
                }
                if (kind == last && ++same >= 2) kind = isHigh(kind) ? BIN : (kind == PUDDLE ? CAT : PUDDLE);
                if (kind != last) same = 0;
            }
            first = false;
            Ob o = add(kind, x);
            sinceHigh = isHigh(kind) ? 0 : sinceHigh + 1;
            last = kind;
            // Time between this obstacle's end and the next one's start. After a high leap the
            // runner is in the air for ~0.62 s, so never less than ~1 s; the heat squeezes the rest.
            float minT = isHigh(kind) ? 1.0f : 0.80f;
            float t = (1.22f - 0.50f * diff) * (0.92f + 0.5f * r.nextFloat());
            if (r.nextInt(6) == 0) t += 0.5f;
            x += o.w + v * Math.max(minT, t);
        }
        // a hostile cop always shows up at high heat even if the dice were unkind
        if (copsLeft > 0 && diff >= 0.5f) {
            for (int i = obs.size() - 1; i > 3 && copsLeft > 0; i--) {
                Ob o = obs.get(i);
                if (o.kind == BARRIER && o.x > len * 0.55f && o.x < len * 0.9f) {
                    o.kind = COP;
                    o.w = 14;
                    o.top = 56;
                    o.hitBelow = 51;
                    o.solid = false;
                    copsLeft--;
                }
            }
        }
    }

    private boolean hasShortPlaced() {
        for (Ob o : obs) if (o.kind == DECK) return true;
        return false;
    }

    /** Scaffold: deck A, a gap, deck B; two low obstacles on the street underneath. */
    private void placeScaffold(float s) {
        Ob a = add(DECK, s);
        a.w = 112;
        gapX0 = s + 112;
        gapX1 = gapX0 + 52;
        Ob b = add(DECK, gapX1);
        b.w = 132;
        bEnd = gapX1 + 132;
        add(PUDDLE, s + 26);
        add(CAT, s + 214);
        shortX = s;
    }

    // ------------------------------------------------------------------ simulation

    /** Height of the highest surface under the runner that is at most FOOT above the given foot height. */
    private float support(float xc, float foot) {
        float best = 0;
        for (int i = 0; i < obs.size(); i++) {
            Ob o = obs.get(i);
            if (o.broken || !(o.solid || o.kind == DECK)) continue;
            if (o.x >= xc + HALF || o.x + o.w <= xc - HALF) continue;
            if (o.top <= foot + FOOT && o.top > best) best = o.top;
        }
        return best;
    }

    private void step(float sd) {
        phase += sd;
        if (stumbleAnim > 0) stumbleAnim -= sd;
        if (state == INTRO) {
            introT += sd;
            if (introT >= INTRO_T) {
                state = RUN;
                if (pressed) buf = BUFFER;
            }
            return;
        }
        if (state == CAUGHT) {
            outT += sd;
            runMult += (0 - runMult) * (1 - (float) Math.exp(-sd * 9));
            wx += v * runMult * sd;
            fall(sd);
            if (outT >= 1.25f) result = MISS;
            return;
        }
        if (state == WIN) {
            outT += sd;
            if (!hidden) {
                wx += v * 0.85f * sd;
                fall(sd);
                if (wx >= len + 52) {
                    hidden = true;
                    h.sfx(Sfx.DOOR);
                    h.text(RX + 40, gy - 70, stumbles == 0 ? "ברחת בלי שריטה!" : "ברחת!",
                            stumbles == 0 ? Ui.ACCENT : Ui.GOOD);
                }
            }
            if (outT >= 1.2f) result = stumbles == 0 ? PERFECT : OK;
            return;
        }

        // ---- RUN ----
        runT += sd;
        buf -= sd;
        invulT -= sd;
        slowT -= sd;
        hintT += sd;
        if (!grounded) coyote -= sd;
        float ramp = Ui.clamp(runT / 0.7f, 0, 1);
        ramp = ramp * ramp * (3 - 2 * ramp);
        float target = (slowT > 0 ? 0.38f : 1f) * ramp;
        runMult += (target - runMult) * (1 - (float) Math.exp(-sd * (target < runMult ? 20 : 3.4f)));
        float speed = v * runMult;
        // the cop accelerates exactly like the runner does, so the start costs nothing
        copMult += (ramp - copMult) * (1 - (float) Math.exp(-sd * 3.4f));
        float copV = v * 1.012f * copMult;
        gapPx += (speed - copV) * sd;
        gapPx = Math.min(gapPx, GAP_CAP);

        // jump trigger
        if (buf > 0 && (grounded || coyote > 0)) {
            vy = JUMP_V;
            grounded = false;
            coyote = 0;
            buf = 0;
            holdT = 0;
            cut = false;
            jumping = true;
            h.sfx(Sfx.WHOOSH);
            puff(wx, 3, 0.55f);
        }
        float oldPy = py, oldWx = wx;
        wx += speed * sd;
        boolean wasOnDeck = grounded && py >= DECK_TOP - 1;
        if (!grounded) {
            if (jumping) holdT += sd;
            if (jumping && vy > 0 && !pressed && holdT >= MIN_HOLD && !cut) {
                vy *= CUT;
                cut = true;
            }
            float ny = py + vy * sd - 0.5f * G * sd * sd;
            float nvy = vy - G * sd;
            float sup = Math.max(support(wx, oldPy), support(oldWx, oldPy));
            if (nvy <= 0 && ny <= sup) {
                land(sup, vy - G * sd);
            } else {
                py = ny;
                vy = nvy;
            }
        } else {
            float sup = support(wx, py);
            if (sup < py - 0.05f) {
                grounded = false;
                coyote = COYOTE;
                jumping = false;
                vy = 0;
                if (wasOnDeck && wx >= bEnd - 14) deckDone();
            } else {
                py = sup;
            }
        }
        // standing on the scaffold
        if (grounded && py >= DECK_TOP - 1) {
            plankTimer = 0.9f;
            if (!shortEntered) {
                shortEntered = true;
                h.text(RX, gy - py - 70, "קיצור!", Ui.ACCENT);
                h.burst(RX, gy - py, 0xFFFFC857, 8);
                h.sfx(Sfx.DING);
            }
            if (!shortDone && wx >= bEnd - 14) deckDone();
        } else {
            plankTimer -= sd;
        }
        if (grounded && vy == 0) {
            footDust(sd, speed);
        }
        collide();
        if (state != RUN) return;
        if (gapPx <= CATCH) {
            state = CAUGHT;
            outT = 0;
            h.sfx(Sfx.BAD);
            h.shake(8);
            h.text(180, gy - 100, "נתפסת!", Ui.BAD);
            return;
        }
        if (wx >= len) {
            state = WIN;
            outT = 0;
            h.sfx(stumbles == 0 ? Sfx.PERFECT : Sfx.GOOD);
            h.burst(RX + 30, gy - 40, stumbles == 0 ? 0xFFFFC857 : 0xFF5BD68A, 14);
        }
    }

    /** Free fall for the outros (the runner may be mid-air). */
    private void fall(float sd) {
        if (py > 0) {
            float ny = py + vy * sd - 0.5f * G * sd * sd;
            vy -= G * sd;
            if (ny <= 0) {
                py = 0;
                vy = 0;
                grounded = true;
            } else {
                py = ny;
            }
        }
    }

    private float landV;

    private void land(float sup, float impactVy) {
        py = sup;
        vy = 0;
        grounded = true;
        jumping = false;
        landV = -impactVy;
        puff(wx, 6, Ui.clamp(landV / 600f, 0.4f, 1.1f));
        // dropping through the gap of the scaffold onto the street is a stumble
        if (sup < 1 && plankTimer > 0 && wx > gapX0 - 6 && wx < gapX1 + 12 && !shortDone && invulT <= 0) {
            fellInGap();
        }
    }

    private void fellInGap() {
        stumble(null, "נפלתי!", 0xFFC8A06A);
    }

    private void deckDone() {
        if (shortDone) return;
        shortDone = true;
        gapPx = Math.min(GAP_CAP, gapPx + 52);
        h.text(RX + 20, gy - py - 84, "הרווחת מרחק!", Ui.GOOD);
        h.burst(RX, gy - py - 10, 0xFF5BD68A, 12);
        h.sfx(Sfx.PERFECT);
    }

    private void collide() {
        for (int i = 0; i < obs.size(); i++) {
            Ob o = obs.get(i);
            if (o.broken) continue;
            boolean over = o.x < wx + HALF && o.x + o.w > wx - HALF;
            if (over && o.kind != DECK) {
                float clear = py - o.top;
                if (clear < o.near) o.near = clear;
                if (py < o.hitBelow) {
                    if (invulT > 0) {
                        o.broken = true;
                    } else {
                        stumble(o, null, 0);
                    }
                }
            }
            if (!o.counted && wx - HALF > o.x + o.w) {
                o.counted = true;
                if (o.kind == DECK || o.broken) continue;
                if (shortEntered && py >= DECK_TOP - 2 && o.top < DECK_TOP) continue; // skipped by climbing
                float bonus = isHigh(o.kind) ? 11 : 8;
                gapPx = Math.min(GAP_CAP, gapPx + bonus);
                combo++;
                float sy = gy - py - 66;
                if (o.near < 7 && o.near > -3) {
                    h.text(RX + 10, sy, "כמעט!", Ui.ACCENT);
                    h.sfx(Sfx.DING);
                } else if (combo >= 3 && combo % 3 == 0) {
                    h.text(RX + 10, sy, "נקי x" + combo, Ui.GOOD);
                    h.sfx(Sfx.TICK);
                }
            }
        }
    }

    private void stumble(Ob o, String word, int debris) {
        stumbles++;
        combo = 0;
        slowT = 0.5f;
        invulT = 0.95f;
        stumbleAnim = 0.8f;
        gapPx -= 14;
        String say = word;
        int col = debris;
        if (o != null) {
            o.broken = true;
            o.t = 0;
            switch (o.kind) {
                case PUDDLE:
                    say = "הופס!";
                    col = 0xFF6FA8D8;
                    break;
                case CAT:
                    say = "מיאו!";
                    col = 0xFF3A3A48;
                    break;
                case BIN:
                    say = "בום!";
                    col = 0xFF4C8A62;
                    break;
                case COP:
                    say = "סליחה!";
                    col = 0xFF2C4C94;
                    break;
                case DUMP:
                    say = "בום!";
                    col = 0xFF3F7A9A;
                    break;
                default:
                    say = "אוי!";
                    col = 0xFFFF9F5A;
                    break;
            }
        }
        h.text(RX, gy - py - 70, say, Ui.BAD);
        h.shake(6);
        h.sfx(Sfx.BAD);
        h.burst(RX + 8, gy - 14, col, 12);
        puff(wx, 5, 0.9f);
    }

    // ------------------------------------------------------------------ dust

    private void puff(float worldX, int count, float size) {
        for (int i = 0; i < count; i++) {
            int k = dnext++ % DN;
            dx[k] = worldX - 4 + hs(seq++, 3) * 8;
            dy[k] = hs(seq, 4) * 3;
            dage[k] = 0;
            dsz[k] = (2.4f + hs(seq, 5) * 3.2f) * size;
            dvx[k] = -30 - hs(seq, 6) * 60;
        }
    }

    private float stepAcc;

    private void footDust(float sd, float speed) {
        stepAcc += sd * speed;
        if (stepAcc > 64) {
            stepAcc = 0;
            puff(wx - 4, 1, 0.45f);
        }
    }

    // ------------------------------------------------------------------ drawing

    static float hs(int i, int s) {
        int x = i * 374761393 + s * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        x ^= x >>> 16;
        return (x & 0xFFFFFF) / 16777216f;
    }

    private float sx(float worldX) {
        return worldX - wx + RX;
    }

    private static int alpha(int argb, float a) {
        int al = (int) (((argb >>> 24) & 0xFF) * Ui.clamp(a, 0, 1));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static void ell(Gfx c, float cx, float cy, float rx, float ry, int col) {
        float[] p = new float[28];
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            p[i * 2] = cx + (float) Math.cos(a) * rx;
            p[i * 2 + 1] = cy + (float) Math.sin(a) * ry;
        }
        c.fillPoly(p, col);
    }

    private static void quad(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
            int col) {
        c.fillPoly(new float[]{x0, y0, x1, y1, x2, y2, x3, y3}, col);
    }

    /** A rectangle rotated about its centre. */
    private static void rrect(Gfx c, float cx, float cy, float w, float hh, float ang, int col) {
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float[] ox = {-w / 2, w / 2, w / 2, -w / 2}, oy = {-hh / 2, -hh / 2, hh / 2, hh / 2};
        float[] p = new float[8];
        for (int i = 0; i < 4; i++) {
            p[i * 2] = cx + ox[i] * ca - oy[i] * sa;
            p[i * 2 + 1] = cy + ox[i] * sa + oy[i] * ca;
        }
        c.fillPoly(p, col);
    }

    private static final int[] SKY = {0xFF060919, 0xFF0A0E26, 0xFF10143A, 0xFF171346, 0xFF221752, 0xFF301B58, 0xFF402060};

    @Override
    void draw(Gfx c, float top, float bottom) {
        topY = top;
        float hh = bottom - top;
        gy = top + hh * 0.80f;
        float yF = gy - Math.min(hh * 0.36f, 140); // top of the near facade
        boolean flash = ((int) (time * 5)) % 2 == 0;
        float prox = Ui.clamp(1.1f - (gapPx - CATCH) / 230f, 0.28f, 1f);
        int flashCol = flash ? 0xFFFF2A3A : 0xFF2A6BFF;
        int otherCol = flash ? 0xFF2A6BFF : 0xFFFF2A3A;

        // sky
        c.fillRect(0, top, Mini.W, hh, 0xFF0C0F20);
        float bandH = (gy - top) / SKY.length;
        for (int i = 0; i < SKY.length; i++) c.fillRect(0, top + i * bandH, Mini.W, bandH + 1, SKY[i]);
        // stars
        for (int i = 0; i < 18; i++) {
            float x = hs(i, 11) * 360, y = top + 46 + hs(i, 12) * (yF - top - 70);
            float tw = 0.5f + 0.5f * (float) Math.sin(time * (1.5f + hs(i, 13) * 2) + i);
            c.fillCircle(x, y, 0.7f + hs(i, 14) * 0.8f, alpha(0xFFFFFFFF, 0.25f + 0.5f * tw));
        }
        // moon
        float mx = 270 - (wx * 0.012f) % 60, my = top + 62 + hh * 0.05f;
        c.fillCircle(mx, my, 30, 0x0CFFF3D0);
        c.fillCircle(mx, my, 21, 0x14FFF3D0);
        c.fillCircle(mx, my, 14, 0xFFF2EBCB);
        c.fillCircle(mx - 4, my - 3, 3.2f, 0xFFD9D2B0);
        c.fillCircle(mx + 4, my + 4, 2.2f, 0xFFD9D2B0);
        // low clouds, lit from below by the city
        for (int i = 0; i < 3; i++) {
            float cx0 = ((i * 190 - wx * 0.03f) % 570 + 570) % 570 - 120;
            float cy0 = top + 58 + hs(i, 20) * hh * 0.12f;
            ell(c, cx0, cy0, 70, 7, 0x20B07AA8);
            ell(c, cx0 + 24, cy0 - 4, 40, 5, 0x1CB07AA8);
        }
        c.fillRect(0, yF - 46, Mini.W, 46, 0x16FF7A5A);

        float lim = top + 50;
        skyline(c, yF - 6, 0.07f, 74, 40, 120, 0xFF171A3C, 0x55FFD27A, 31, true, lim, hh);
        skyline(c, yF + 4, 0.20f, 108, 30, 82, 0xFF1F2250, 0xBBFFD27A, 47, true, lim, hh);

        // police light spill from behind, over the roofs
        for (int i = 0; i < 3; i++) {
            c.fillCircle(-26, gy - 30, 210 - i * 62, alpha(flashCol, (0.05f + i * 0.04f) * prox));
        }
        c.fillCircle(-10, gy - 30, 120, alpha(otherCol, 0.05f * prox));

        facade(c, yF, gy);
        pavement(c, gy, bottom);
        lamps(c, gy, hh);

        // corner shop and the scaffold sign sit in the world
        shop(c, gy);
        for (int i = 0; i < obs.size(); i++) drawDeckPosts(c, obs.get(i));
        sign(c);
        for (int i = 0; i < obs.size(); i++) drawOb(c, obs.get(i), flash);
        drawDecks(c);

        // police glow on the street behind the runner
        ell(c, 0, gy + 4, 190, 22, alpha(flashCol, 0.20f * prox));
        ell(c, 0, gy + 8, 110, 12, alpha(flashCol, 0.20f * prox));
        // patrol car at the start
        patrolCar(c, gy, flash);

        // characters
        float copSX = RX - (30 + (gapPx - CATCH) * 0.42f);
        if (state == CAUGHT) copSX = RX - 25;
        else if (state == WIN) copSX += outT * 12;
        runnerAndCop(c, copSX, flash);

        // dust
        for (int i = 0; i < DN; i++) {
            if (dage[i] >= 0.7f) continue;
            float a = 1 - dage[i] / 0.7f;
            c.fillCircle(sx(dx[i]), gy - dy[i] - dage[i] * 10, dsz[i] * (0.7f + dage[i] * 1.4f), alpha(0xFFB9A58A, 0.5f * a));
        }
        // speed streaks
        if (state == RUN || state == WIN) {
            for (int i = 0; i < 5; i++) {
                float yy = top + 80 + hs(i, 70) * (gy - top - 110);
                float xx = ((hs(i, 71) * 520 - wx * 2.2f) % 520 + 520) % 520 - 80;
                c.line(xx, yy, xx + 26 + 16 * runMult, yy, 1, 0x12FFFFFF);
            }
        }
        // strobe on the whole picture, strongest right after each switch
        float since = (time * 5) % 1f;
        float flashA = (1 - since) * (1 - since) * 0.10f * prox;
        c.fillRect(0, top, Mini.W, hh, alpha(flashCol, flashA));
        // vignette
        c.fillRect(0, top, 22, hh, 0x30000000);
        c.fillRect(Mini.W - 22, top, 22, hh, 0x30000000);

        hud(c, top, bottom);
        hints(c, bottom);
    }

    private void skyline(Gfx c, float yBase, float k, float pitch, float minH, float maxH, int col, int lit, int salt,
            boolean roof, float limit, float hh) {
        float sc = wx * k;
        int i0 = (int) Math.floor(sc / pitch) - 1;
        int n = (int) (Mini.W / pitch) + 3;
        int roofCol = Ui.darken(col, 0.8f);
        float scaleH = Ui.clamp(hh / 500f, 0.55f, 1f);
        for (int i = i0; i < i0 + n; i++) {
            float bx = i * pitch - sc;
            float r1 = hs(i, salt), r2 = hs(i, salt + 1), r3 = hs(i, salt + 2);
            float bw = pitch * (0.74f + 0.26f * r1);
            float bh = (minH + (maxH - minH) * r2) * scaleH;
            float by = Math.max(limit, yBase - bh);
            c.fillRect(bx, by, bw, yBase - by + 3, col);
            c.fillRect(bx, by, bw, 2, Ui.darken(col, 1.35f));
            if (roof) {
                switch ((int) (r3 * 5)) {
                    case 0: // water tank on legs
                        c.fillRoundRect(bx + bw * 0.25f, by - 11, 17, 8, 4, roofCol);
                        c.fillRect(bx + bw * 0.25f + 3, by - 4, 2, 4, roofCol);
                        c.fillRect(bx + bw * 0.25f + 12, by - 4, 2, 4, roofCol);
                        break;
                    case 1: // antenna with a red light
                        c.line(bx + bw * 0.6f, by, bx + bw * 0.6f, by - 22, 1.2f, roofCol);
                        c.line(bx + bw * 0.6f - 5, by - 16, bx + bw * 0.6f + 5, by - 16, 1, roofCol);
                        if (((int) (time * 1.3f + i)) % 2 == 0) c.fillCircle(bx + bw * 0.6f, by - 22, 1.6f, 0xFFFF4A4A);
                        break;
                    case 2: { // solar heater: slanted panel and a barrel
                        float px = bx + bw * 0.2f;
                        quad(c, px, by, px + 4, by - 9, px + 24, by - 9, px + 22, by, roofCol);
                        c.fillRoundRect(px + 5, by - 15, 16, 5, 2.5f, roofCol);
                        break;
                    }
                    case 3: // stair hut
                        c.fillRect(bx + bw * 0.55f, by - 10, 14, 10, roofCol);
                        break;
                    default:
                        break;
                }
            }
            int cols = (int) ((bw - 6) / 11), rows = (int) ((yBase - by - 10) / 15);
            for (int rr = 0; rr < rows; rr++) {
                for (int cc = 0; cc < cols; cc++) {
                    float q = hs(i * 131 + rr * 17 + cc, salt + 9);
                    if (q > 0.80f) {
                        int lc = q > 0.97f ? alpha(0xFF7AD0FF, ((lit >>> 24) / 255f) * (0.8f + 0.2f * (float) Math.sin(time * 9 + i))) : lit;
                        c.fillRect(bx + 4 + cc * 11, by + 7 + rr * 15, 5, 7, lc);
                    }
                }
            }
        }
    }

    private void facade(Gfx c, float yF, float gyy) {
        float yb = gyy - 12;
        c.fillRect(0, yF, Mini.W, yb - yF, 0xFF2A2C4A);
        c.fillRect(0, yF, Mini.W, 4, 0xFF3A3D60);
        c.fillRect(0, yF + 4, Mini.W, 3, 0xFF202238);
        // soft shading toward the street
        c.fillRect(0, yb - 18, Mini.W, 18, 0x22000000);
        float pitch = 176;
        float sc = wx * 0.6f;
        int i0 = (int) Math.floor(sc / pitch) - 1;
        float fh = yb - yF;
        for (int i = i0; i < i0 + 4; i++) {
            float bx = i * pitch - sc;
            int type = (int) (hs(i, 90) * 6);
            switch (type) {
                case 0: { // lit doorway with a step and a number plate
                    c.fillRect(bx + 50, yb - 56, 36, 56, 0xFF151827);
                    c.fillRect(bx + 54, yb - 52, 28, 52, 0xFFFFC46A);
                    c.fillRect(bx + 54, yb - 52, 28, 52, 0x30FF8A3A);
                    c.fillRect(bx + 66, yb - 52, 2, 52, 0xFFB8803A);
                    c.fillRect(bx + 46, yb - 3, 44, 3, 0xFF3A3D60);
                    c.fillRoundRect(bx + 92, yb - 46, 12, 8, 2, 0xFF8FA0C0);
                    c.fillCircle(bx + 70, yb - 66, 14, 0x12FFC46A);
                    break;
                }
                case 1: { // roller shutter with graffiti
                    c.fillRect(bx + 24, yb - fh * 0.7f, 92, fh * 0.7f, 0xFF383B58);
                    for (int s = 0; s < 8; s++) c.fillRect(bx + 24, yb - fh * 0.7f + s * (fh * 0.7f / 8), 92, 1.2f, 0xFF2A2D46);
                    float[] g = {bx + 36, yb - 30, bx + 46, yb - 40, bx + 54, yb - 28, bx + 64, yb - 42, bx + 74, yb - 30};
                    for (int s = 0; s < 4; s++) c.line(g[s * 2], g[s * 2 + 1], g[s * 2 + 2], g[s * 2 + 3], 2.4f, 0xFFE05AB0);
                    c.line(bx + 40, yb - 18, bx + 92, yb - 22, 2, 0xFF5AE0D8);
                    break;
                }
                case 2: { // window with shutters, AC unit and a pipe
                    float wy = yF + fh * 0.2f;
                    c.fillRect(bx + 40, wy, 26, 32, 0xFFFFD27A);
                    c.fillRect(bx + 40, wy, 26, 32, 0x30FF8A3A);
                    c.fillRect(bx + 52, wy, 2, 32, 0xFF8A5A2A);
                    c.fillRect(bx + 28, wy - 1, 11, 34, 0xFF3F5A4C);
                    c.fillRect(bx + 67, wy - 1, 11, 34, 0xFF3F5A4C);
                    c.fillRect(bx + 44, wy + 38, 20, 12, 0xFF9AA0B8);
                    c.fillRect(bx + 46, wy + 41, 16, 2, 0xFF6C7290);
                    c.fillRect(bx + 100, yF + 6, 3, fh - 6, 0xFF4A4E6E);
                    break;
                }
                case 3: { // alley mouth, fire escape and a drum
                    c.fillRect(bx + 30, yF + 14, 50, fh - 14, 0xFF10121F);
                    for (int s = 0; s < 3; s++) {
                        float ly = yF + 24 + s * 22;
                        c.line(bx + 34, ly, bx + 76, ly + 10, 1.4f, 0xFF3C405E);
                        c.line(bx + 34, ly + 10, bx + 34, ly + 22, 1, 0xFF3C405E);
                    }
                    c.fillRoundRect(bx + 56, yb - 18, 14, 18, 2, 0xFF3A5A7A);
                    break;
                }
                case 4: { // posters
                    rrect(c, bx + 50, yb - 40, 18, 26, -0.05f, 0xFFD8D2BE);
                    rrect(c, bx + 72, yb - 42, 18, 26, 0.04f, 0xFFE0735A);
                    rrect(c, bx + 94, yb - 38, 18, 24, -0.03f, 0xFF6EA8E0);
                    c.fillRect(bx + 44, yb - 46, 10, 2, 0xFF9A3A3A);
                    break;
                }
                default: { // balcony with laundry
                    float by0 = yF + fh * 0.36f;
                    c.fillRect(bx + 20, by0, 100, 3, 0xFF4A4E6E);
                    for (int s = 0; s < 7; s++) c.fillRect(bx + 22 + s * 15, by0 + 3, 2, 14, 0xFF3A3D60);
                    c.line(bx + 20, by0 - 26, bx + 120, by0 - 22, 1, 0xFF8A94A3);
                    for (int s = 0; s < 4; s++) {
                        float lx = bx + 32 + s * 22;
                        int[] cl = {0xFFE05A5A, 0xFFE0D45A, 0xFF5AA9E0, 0xFFE0E0E0};
                        float sway = (float) Math.sin(time * 2 + s + i) * 1.5f;
                        quad(c, lx, by0 - 25 + s * 0.8f, lx + 12, by0 - 24 + s * 0.8f, lx + 11 + sway, by0 - 9, lx + 1 + sway, by0 - 9, cl[s]);
                    }
                    break;
                }
            }
        }
        // police colour washing along the wall
        boolean flash = ((int) (time * 5)) % 2 == 0;
        float prox = Ui.clamp(1.1f - (gapPx - CATCH) / 230f, 0.28f, 1f);
        int fc = flash ? 0xFFFF2A3A : 0xFF2A6BFF;
        quad(c, 0, yF, 200 + 80 * prox, yF + 12, 190 + 60 * prox, yb, 0, yb, alpha(fc, 0.12f * prox));
        quad(c, 0, yF + 10, 90 + 60 * prox, yF + 20, 80 + 40 * prox, yb, 0, yb, alpha(fc, 0.12f * prox));
    }

    private void pavement(Gfx c, float gyy, float bottom) {
        float yb = gyy - 12;
        c.fillRect(0, yb, Mini.W, bottom - yb, 0xFF23253A);
        c.fillRect(0, yb, Mini.W, 4, 0xFF40446A);
        c.fillRect(0, yb + 4, Mini.W, 2, 0xFF191B2C);
        // wet asphalt below the kerb
        c.fillRect(0, gyy + 6, Mini.W, bottom - gyy - 6, 0xFF1C1E31);
        float dashY = gyy + (bottom - gyy) * 0.55f;
        float sc = wx % 64;
        for (int i = -1; i < 7; i++) c.fillRect(i * 64 - sc, dashY, 30, 2.4f, 0x44AAB0D0);
        // manholes and cracks
        int i0 = (int) Math.floor(wx / 230);
        for (int i = i0 - 1; i < i0 + 3; i++) {
            float x = i * 230 + 90 - wx + RX;
            float yy = gyy + (bottom - gyy) * 0.28f;
            ell(c, x, yy, 17, 4, 0xFF2C2F48);
            ell(c, x, yy - 0.8f, 14, 3, 0xFF3A3E5E);
            c.line(x + 60, yy + 8, x + 84, yy + 6, 1, 0xFF14162A);
            c.line(x + 84, yy + 6, x + 92, yy + 12, 1, 0xFF14162A);
        }
        // kerb studs
        for (int i = -1; i < 7; i++) c.fillRect(i * 48 - wx % 48, yb + 1, 22, 1.2f, 0x22FFFFFF);
    }

    private void lamps(Gfx c, float gyy, float hh) {
        float pitch = 300;
        int i0 = (int) Math.floor((wx - RX) / pitch) - 1;
        float ph = Math.min(hh * 0.50f, 128);
        for (int i = i0; i < i0 + 4; i++) {
            float x = i * pitch + 150 - wx + RX;
            if (x < -80 || x > Mini.W + 80) continue;
            boolean broken = hs(i, 120) > 0.8f;
            float fl = broken ? (((int) (time * 11 + i * 3)) % 7 < 2 ? 0.25f : 1f) : 1f;
            float poleY = gyy - 8;
            float headY = poleY - ph;
            c.fillRect(x - 1.6f, headY, 3.2f, ph, 0xFF0E1020);
            c.fillRect(x - 5, poleY - 6, 10, 6, 0xFF0E1020);
            c.line(x, headY + 2, x + 15, headY - 3, 2.4f, 0xFF0E1020);
            float lx = x + 15, ly = headY - 1;
            quad(c, lx - 7, ly - 3, lx + 7, ly - 3, lx + 5, ly + 2, lx - 5, ly + 2, 0xFF0E1020);
            // cone, halo and the pool of light on the street
            quad(c, lx - 5, ly + 2, lx + 5, ly + 2, lx + 52, gyy + 10, lx - 44, gyy + 10, alpha(0x16FFE6A0, fl));
            c.fillCircle(lx, ly + 2, 24, alpha(0x16FFE6A0, fl));
            c.fillCircle(lx, ly + 2, 14, alpha(0x30FFE6A0, fl));
            c.fillCircle(lx, ly + 2, 5, alpha(0xFFFFF2C8, fl));
            ell(c, lx + 4, gyy + 5, 58, 9, alpha(0x30FFE6A0, fl));
            ell(c, lx + 4, gyy + 5, 30, 5, alpha(0x30FFE6A0, fl));
        }
    }

    private void patrolCar(Gfx c, float gyy, boolean flash) {
        float x = sx(-118);
        if (x < -150 || x > Mini.W) return;
        // body: a generic boxy sedan, white with a blue stripe, nose pointing toward the runner
        quad(c, x, gyy - 8, x + 100, gyy - 8, x + 100, gyy - 24, x, gyy - 24, 0xFFD6DCE6);
        quad(c, x + 14, gyy - 24, x + 74, gyy - 24, x + 62, gyy - 40, x + 24, gyy - 40, 0xFFD6DCE6);
        quad(c, x + 28, gyy - 26, x + 70, gyy - 26, x + 60, gyy - 38, x + 32, gyy - 38, 0xFF22304A);
        c.fillRect(x, gyy - 17, 100, 5, 0xFF2A6BFF);
        c.fillCircle(x + 22, gyy - 8, 8, 0xFF0A0C14);
        c.fillCircle(x + 80, gyy - 8, 8, 0xFF0A0C14);
        c.fillCircle(x + 22, gyy - 8, 3.4f, 0xFF8A94A3);
        c.fillCircle(x + 80, gyy - 8, 3.4f, 0xFF8A94A3);
        c.fillRoundRect(x + 38, gyy - 46, 12, 6, 2, flash ? 0xFFFF2A3A : 0xFF7A1018);
        c.fillRoundRect(x + 50, gyy - 46, 12, 6, 2, flash ? 0xFF10285A : 0xFF2A6BFF);
        c.fillCircle(x + 44, gyy - 43, 22, alpha(flash ? 0xFFFF2A3A : 0xFF2A6BFF, 0.22f));
        c.fillCircle(x + 56, gyy - 43, 22, alpha(flash ? 0xFF2A6BFF : 0xFFFF2A3A, 0.16f));
        c.fillRect(x + 94, gyy - 14, 6, 4, 0xFFFFF2B0);
        quad(c, x + 100, gyy - 14, x + 170, gyy - 8, x + 170, gyy - 2, x + 100, gyy - 10, 0x22FFF2B0);
    }

    private void shop(Gfx c, float gyy) {
        float x = sx(len);
        // the glow gives the corner away long before it is in sight
        float far = Ui.clamp(1 - (x - Mini.W) / 520f, 0, 1);
        if (x > Mini.W + 520) return;
        if (x > Mini.W) {
            c.fillRect(Mini.W - 60, topY, 60, gyy - topY + 12, alpha(0xFFFFD27A, 0.07f * far));
            ell(c, Mini.W + 20, gyy + 6, 120 * far, 14, alpha(0xFFFFE2A0, 0.35f * far));
            return;
        }
        float wallTop = gyy - 112;
        c.fillRect(x - 6, wallTop, 260, gyy - wallTop + 2, 0xFF3A2E52);
        c.fillRect(x - 6, wallTop, 260, 5, 0xFF52416E);
        // lit window and open door
        c.fillRect(x + 78, gyy - 70, 70, 52, 0xFFFFE2A0);
        c.fillRect(x + 78, gyy - 70, 70, 52, 0x30FF8A3A);
        for (int i = 0; i < 4; i++) c.fillRect(x + 84 + i * 16, gyy - 40, 10, 22, 0xFFE0A050);
        c.fillRect(x + 30, gyy - 64, 36, 64, 0xFFFFE2A0);
        c.fillRect(x + 30, gyy - 64, 36, 64, 0x30FF8A3A);
        // awning stripes
        for (int i = 0; i < 9; i++) {
            float ax = x + 14 + i * 16;
            quad(c, ax, gyy - 86, ax + 16, gyy - 86, ax + 18, gyy - 72, ax - 2, gyy - 72, i % 2 == 0 ? 0xFFE0453A : 0xFFF2EBD8);
        }
        // neon sign
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 6);
        c.fillRoundRect(x + 52, gyy - 108, 86, 20, 6, 0xFF1A1030);
        c.fillCircle(x + 95, gyy - 98, 40, alpha(0xFF7AF0FF, 0.10f * pulse));
        c.text("פתוח", x + 95, gyy - 92, 16, alpha(0xFF7AF0FF, pulse), Gfx.CENTER);
        // light on the street and the lit approach
        ell(c, x + 50, gyy + 6, 150, 14, 0x40FFE2A0);
        ell(c, x + 50, gyy + 6, 80, 8, 0x40FFE2A0);
        quad(c, x + 30, gyy - 64, x + 66, gyy - 64, x - 60, gyy + 12, x - 130, gyy + 12, 0x18FFE2A0);
    }

    private void sign(Gfx c) {
        if (shortX < 0 || !hasShortPlaced()) return;
        float x = sx(shortX) - 76;
        if (x < -90 || x > Mini.W + 40) return;
        float hh = Math.min(gy - topY, 400) * 0.5f;
        float top = gy - Math.min(hh + 20, 150);
        float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6);
        c.fillRect(x - 1.5f, top + 30, 3, gy - top - 30 - 8, 0xFF0E1020);
        c.fillRoundRect(x - 38, top, 76, 34, 5, 0xFF0E1020);
        c.strokeRoundRect(x - 38, top, 76, 34, 5, 2, alpha(0xFFFFC857, 0.5f + 0.5f * pulse));
        c.text("קיצור", x, top + 17, 15, Ui.ACCENT, Gfx.CENTER);
        // arrow pointing up and right: hold the jump
        c.text("החזיקו", x, top + 30, 12, alpha(Ui.ACCENT, 0.6f + 0.4f * pulse), Gfx.CENTER);
    }

    private void drawDeckPosts(Gfx c, Ob o) {
        if (o.kind != DECK) return;
        float x0 = sx(o.x);
        if (x0 > Mini.W + 40 || x0 + o.w < -40) return;
        int col = 0xFF4A4F6E;
        for (float p = 4; p <= o.w + 0.1f; p += 42) {
            float px = x0 + Math.min(p, o.w - 4);
            c.fillRect(px - 1.6f, gy - DECK_TOP, 3.2f, DECK_TOP - 2, col);
        }
        // cross braces
        for (float p = 4; p + 42 <= o.w + 4; p += 42) {
            c.line(x0 + p, gy - 4, x0 + p + 42, gy - DECK_TOP + 6, 1.4f, 0xFF3A3E5A);
        }
    }

    private void drawDecks(Gfx c) {
        for (int i = 0; i < obs.size(); i++) {
            Ob o = obs.get(i);
            if (o.kind != DECK) continue;
            float x0 = sx(o.x);
            if (x0 > Mini.W + 40 || x0 + o.w < -40) continue;
            float y0 = gy - DECK_TOP;
            c.fillRect(x0, y0, o.w, 5, 0xFF9A7448);
            c.fillRect(x0, y0 + 4, o.w, 2, 0xFF5A4228);
            for (float p = 14; p < o.w; p += 14) c.fillRect(x0 + p, y0, 1, 5, 0xFF6A4E2E);
            // rail with string lights, so the scaffold reads from afar
            c.line(x0, y0 - 18, x0 + o.w, y0 - 18, 1.6f, 0xFF6C7290);
            for (float p = 3; p <= o.w; p += 44) c.line(x0 + p, y0, x0 + p, y0 - 18, 1.4f, 0xFF6C7290);
            for (float p = 9; p < o.w; p += 18) {
                float tw = 0.7f + 0.3f * (float) Math.sin(time * 5 + p);
                c.fillCircle(x0 + p, y0 - 14 + (float) Math.sin(p * 0.9f) * 1.6f, 4.5f, alpha(0x30FFD27A, tw));
                c.fillCircle(x0 + p, y0 - 14 + (float) Math.sin(p * 0.9f) * 1.6f, 1.7f, alpha(0xFFFFE2A0, tw));
            }
        }
        // hanging cable in the gap
        if (hasShortPlaced()) {
            float gx = sx(gapX0);
            if (gx > -80 && gx < Mini.W + 40) {
                float y0 = gy - DECK_TOP;
                float sw = (float) Math.sin(time * 3) * 2;
                c.line(gx + 6, y0 + 5, gx + 9 + sw, y0 + 22, 1.6f, 0xFF14162A);
                c.fillRect(gx - 3, y0, 3, 7, 0xFF7A5A34);
                c.fillRect(gx + 52, y0, 3, 7, 0xFF7A5A34);
                // hazard stripes mark the edge
                for (int s = 0; s < 3; s++) c.fillRect(gx - 12 + s * 5, y0 - 1, 3, 2, 0xFFFFC857);
            }
        }
    }

    private void drawOb(Gfx c, Ob o, boolean flash) {
        float x0 = sx(o.x);
        if (x0 > Mini.W + 60 || x0 + o.w < -60) return;
        if (o.kind == DECK) return;
        if (o.broken) o.t += 0.033f;
        float t = o.t;
        float base = gy;
        switch (o.kind) {
            case PUDDLE: {
                float cx = x0 + o.w / 2;
                ell(c, cx, base + 3, o.w * 0.62f, 4.6f, 0xFF0C1020);
                ell(c, cx, base + 2.6f, o.w * 0.56f, 3.6f, 0xFF27406A);
                ell(c, cx - 6, base + 2.2f, o.w * 0.22f, 1.5f, alpha(0xFFFFE2A0, 0.7f));
                float rp = (time * 0.9f + o.x * 0.01f) % 1f;
                ell(c, cx + 5, base + 2.8f, 3 + rp * 9, 1 + rp * 2, alpha(0xFF9ECBFF, 0.35f * (1 - rp)));
                if (o.broken && t < 0.6f) {
                    for (int i = 0; i < 6; i++) {
                        float a = -0.4f - i * 0.45f, d = t * 90;
                        c.fillCircle(cx + (float) Math.cos(a) * d * 0.6f, base - (float) Math.sin(-a) * d * 0.8f + t * t * 200,
                                2.2f, alpha(0xFF9ECBFF, 1 - t / 0.6f));
                    }
                }
                break;
            }
            case CAT: {
                float cx = x0 + o.w / 2;
                if (o.broken) {
                    cx += t * 170; // the cat bolts
                    float arch = Math.max(0, 1 - (t * 2.2f - 1) * (t * 2.2f - 1)) * 14;
                    base -= arch;
                }
                int fur = 0xFF2C2C3C;
                ell(c, cx, base - 7, 9, 6, fur);
                c.fillCircle(cx + 7, base - 11, 5, fur);
                c.fillPoly(new float[]{cx + 3.5f, base - 14, cx + 5, base - 20, cx + 7, base - 15}, fur);
                c.fillPoly(new float[]{cx + 8, base - 15, cx + 11, base - 20, cx + 11.5f, base - 13}, fur);
                float tail = (float) Math.sin(time * 4 + o.x) * 3;
                c.line(cx - 8, base - 8, cx - 14, base - 14 - tail * 0.3f, 2, fur);
                c.line(cx - 14, base - 14 - tail * 0.3f, cx - 13 + tail, base - 20, 2, fur);
                c.fillCircle(cx + 8.5f, base - 11.5f, 1.2f, 0xFFFFE04A);
                c.fillCircle(cx + 5.5f, base - 11.5f, 1.2f, 0xFFFFE04A);
                c.fillRect(cx - 5, base - 2, 2, 2, fur);
                c.fillRect(cx + 4, base - 2, 2, 2, fur);
                break;
            }
            case BIN: {
                float tilt = o.broken ? Math.min(t * 6, 1.45f) : 0;
                float cx = x0 + o.w / 2 + (o.broken ? t * 40 : 0);
                float lift = o.broken ? Math.max(0, 14 - t * 90 + t * t * 160) : 0;
                if (o.broken) {
                    rrect(c, cx, base - 14 + (tilt > 1.3f ? 4 : 0) - lift * 0.2f, 20, 28, tilt, 0xFF3E7A56);
                    c.fillCircle(cx + 18 + t * 70, base - 6 - Math.max(0, 22 - t * 70 + t * t * 120), 2.4f, 0xFFE0D8A8);
                    c.fillCircle(cx + 26 + t * 50, base - 3, 2, 0xFFB86A4A);
                    break;
                }
                quad(c, cx - 10, base - 25, cx + 10, base - 25, cx + 8, base, cx - 8, base, 0xFF3E7A56);
                c.fillRect(cx - 7, base - 22, 2, 18, 0xFF2E5C40);
                c.fillRect(cx + 4, base - 22, 2, 18, 0xFF2E5C40);
                c.fillRoundRect(cx - 12, base - 29, 24, 6, 2, 0xFF4E9468);
                c.fillRect(cx - 3, base - 32, 6, 3, 0xFF2E5C40);
                c.fillCircle(cx - 7, base, 2, 0xFF10121C);
                c.fillCircle(cx + 7, base, 2, 0xFF10121C);
                c.fillRect(cx - 3, base - 18, 6, 6, 0xFFD8D2BE);
                break;
            }
            case BARRIER: {
                float cx = x0 + o.w / 2;
                float tilt = o.broken ? Math.min(t * 7, 1.5f) : 0;
                float lift = o.broken ? Math.max(0, 20 * t * 3 - 60 * t * t * 3) : 0;
                if (o.broken) {
                    float dxp = t * 60;
                    rrect(c, cx + dxp, base - 20 - lift, 30, 12, -tilt * 0.8f, 0xFFF2EBD8);
                    rrect(c, cx + dxp, base - 20 - lift, 12, 12, -tilt * 0.8f, 0xFFE0453A);
                    break;
                }
                c.line(cx - 10, base, cx - 8, base - 22, 3, 0xFF2A2D46);
                c.line(cx + 10, base, cx + 8, base - 22, 3, 0xFF2A2D46);
                c.fillRect(cx - 13, base - 46, 26, 11, 0xFFF2EBD8);
                for (int i = 0; i < 4; i++) {
                    float sx0 = cx - 13 + i * 7;
                    quad(c, sx0, base - 46, sx0 + 3.5f, base - 46, sx0 + 0.5f, base - 35, sx0 - 3, base - 35, 0xFFE0453A);
                }
                c.fillRect(cx - 13, base - 33, 26, 8, 0xFFF2EBD8);
                for (int i = 0; i < 4; i++) {
                    float sx0 = cx - 13 + i * 7 + 3.5f;
                    quad(c, sx0, base - 33, sx0 + 3.5f, base - 33, sx0 + 0.5f, base - 25, sx0 - 3, base - 25, 0xFFE0453A);
                }
                boolean on = ((int) (time * 3 + o.x * 0.01f)) % 2 == 0;
                c.fillCircle(cx, base - 50, 3.6f, on ? 0xFFFFB02A : 0xFF6A4A18);
                if (on) c.fillCircle(cx, base - 50, 11, 0x30FFB02A);
                break;
            }
            case DUMP: {
                float cx = x0 + o.w / 2;
                if (o.broken) {
                    float tw = Math.min(t * 5, 1f);
                    quad(c, x0 + 2, base - 4 + tw * 2, x0 + o.w - 2, base - 4 + tw * 2, x0 + o.w + 2 + tw * 4, base - 30 + tw * 2,
                            x0 - 2 + tw * 4, base - 32 + tw * 2, 0xFF3F7A9A);
                    rrect(c, x0 + o.w * 0.5f + t * 60, base - 40 - Math.max(0, 24 - t * 120 + t * t * 260), o.w + 4, 8, -0.5f - t * 3, 0xFF4F8EB0);
                    break;
                }
                quad(c, x0, base - 4, x0 + o.w, base - 4, x0 + o.w + 3, base - 36, x0 - 3, base - 36, 0xFF3F7A9A);
                c.fillRect(x0 - 3, base - 42, o.w + 6, 7, 0xFF4F8EB0);
                for (int i = 1; i < 4; i++) c.fillRect(x0 + i * o.w / 4 - 1, base - 34, 2, 28, 0xFF2F6280);
                c.fillRect(x0 + 5, base - 24, 14, 5, 0xFFE0D45A);
                c.line(x0 + 24, base - 14, x0 + 34, base - 22, 2, 0xFFE05AB0);
                c.fillCircle(x0 + 5, base - 2, 3, 0xFF10121C);
                c.fillCircle(x0 + o.w - 5, base - 2, 3, 0xFF10121C);
                c.fillRect(x0 + o.w - 12, base - 8, 8, 3, 0xFFD8D2BE);
                break;
            }
            case COP: {
                float cx = x0 + o.w / 2;
                float off = o.broken ? -t * 24 : 0;
                cop(c, cx + off, base, 2.1f, time * 0.6f, o.broken ? 5 : 1, -1);
                if (!o.broken) {
                    // torch beam sweeping toward the runner
                    float a = (float) Math.sin(time * 2.4f) * 6;
                    quad(c, cx - 14, base - 38, cx - 14, base - 36, cx - 120, base - 12 + a, cx - 120, base - 58 + a, 0x1CFFF3B0);
                }
                break;
            }
            default:
                break;
        }
    }

    private void runnerAndCop(Gfx c, float copSX, boolean flash) {
        float shadowA = 0.35f;
        float fy = gy;
        // cop first (behind the runner), with his torch beam
        float cp = phase * 0.78f;
        int mode = 0;
        if (state == INTRO) mode = 4;
        else if (state == CAUGHT) mode = 3;
        else if (state == WIN && hidden) mode = 2;
        float run = (state == INTRO || (state == WIN && hidden)) ? 0 : cp;
        ell(c, copSX, fy + 2, 13, 3.2f, alpha(0xFF000000, shadowA));
        ell(c, copSX - 2, fy + 2, 10, 2.6f, alpha(flash ? 0xFFFF2A3A : 0xFF2A6BFF, 0.35f));
        if (state != CAUGHT && !(state == WIN && hidden) && state != INTRO) {
            float ty = fy - 52;
            quad(c, copSX + 14, fy - 30, copSX + 14, fy - 27, RX + 2, ty + 36, RX + 2, ty + 6, 0x20FFF3B0);
        }
        cop(c, copSX, fy, 2.2f, run, mode, 1);
        if (state == INTRO) {
            // "stop!" bubble
            float b = Math.min(1, introT / 0.12f);
            float bx = copSX + 40, by = fy - 88;
            c.fillRoundRect(bx - 40 * b, by - 14 * b, 80 * b, 26 * b, 9, 0xFFF2EBD8);
            c.fillPoly(new float[]{bx - 22, by + 10, bx - 10, by + 10, bx - 24, by + 22}, 0xFFF2EBD8);
            if (b > 0.9f) c.text("עצור!", bx, by + 6, 18, 0xFFC2252B, Gfx.CENTER);
        }
        if (hidden) return;
        // runner
        boolean blink = invulT > 0 && ((int) (time * 18)) % 2 == 0;
        float ry = fy - py;
        float sh = Ui.clamp(1 - py / 140f, 0.35f, 1);
        ell(c, RX, fy + 2, 11 * sh, 3 * sh, alpha(0xFF000000, shadowA * sh));
        ell(c, RX - 3, fy + 2, 8 * sh, 2.4f * sh, alpha(flash ? 0xFFFF2A3A : 0xFF2A6BFF, 0.35f * sh));
        if (!blink) {
            float sc = 2.1f;
            int col = h.female() ? 0xFFFF7AA8 : 0xFFFFB347;
            boolean running = grounded && (state == RUN || state == WIN) && runMult > 0.15f;
            float ph = runT * 1.5f;
            if (state == CAUGHT) running = false;
            // squash a little when landing, stretch when rising
            Ui.person(c, RX, ry + (stumbleAnim > 0 ? (float) Math.abs(Math.sin(time * 22)) * 1.5f : 0), sc, h.female(), ph, running, col);
            if (stumbleAnim > 0) {
                for (int i = 0; i < 3; i++) {
                    float a = time * 9 + i * 2.09f;
                    c.fillCircle(RX + (float) Math.cos(a) * 11, ry - 52 + (float) Math.sin(a) * 3.2f, 2, 0xFFFFE04A);
                }
            }
            if (state == RUN && !grounded && vy > 0) {
                c.line(RX - 4, ry + 4, RX - 6, ry + 14, 1.2f, 0x30FFFFFF);
                c.line(RX + 1, ry + 4, RX + 1, ry + 16, 1.2f, 0x30FFFFFF);
            }
        }
    }

    /**
     * mode 0 running, 1 blocking with arms out, 2 doubled over panting, 3 reaching for the shoulder,
     * 4 shouting, 5 startled. d = +1 faces right, -1 faces left.
     */
    private void cop(Gfx c, float x, float y, float s, float ph, int mode, float d) {
        int navy = 0xFF2C4C94, dark = 0xFF16203D, gold = 0xFFFFC857;
        boolean run = mode == 0;
        float sw = run ? (float) Math.sin(ph * 9) * 2.8f * s : 0;
        float bob = run ? Math.abs((float) Math.sin(ph * 9)) * 1.1f * s : (mode == 2 ? (float) Math.sin(time * 8) * 0.5f * s : 0);
        float lean = mode == 2 ? 3.4f * s * d : 0;
        c.line(x - 1.9f * s, y - 7 * s, x - 1.9f * s - sw, y, 2.4f * s, dark);
        c.line(x + 1.9f * s, y - 7 * s, x + 1.9f * s + sw, y, 2.4f * s, dark);
        c.fillRect(x - 1.9f * s - sw - 1.5f * s * (d < 0 ? 1 : 0), y - 1.4f * s, 3 * s, 1.4f * s, 0xFF0A0C14);
        float t = y - 18 * s - bob + (mode == 2 ? 2.4f * s : 0);
        float cx = x + lean * 0.5f;
        c.fillPoly(new float[]{cx - 5.2f * s, t, cx + 5.2f * s, t, x + 6.6f * s, y - 10 * s, x + 4.4f * s, y - 6.8f * s,
                x - 4.4f * s, y - 6.8f * s, x - 6.6f * s, y - 10 * s}, navy);
        c.line(x - 6.4f * s, y - 9 * s, x + 6.4f * s, y - 9 * s, 1.4f * s, 0xFF0D1220);
        c.fillRect(x - 0.9f * s, y - 9.8f * s, 1.8f * s, 1.6f * s, gold);
        c.fillCircle(cx + 2.8f * s * d, t + 3.8f * s, 1.2f * s, gold);
        // arms
        float shy = t + 2.4f * s;
        float sh1 = cx + 1.0f * s * d;
        float hx, hy;
        switch (mode) {
            case 1: // arms wide
                hx = x + 9 * s * d;
                hy = shy - 1 * s;
                c.line(sh1, shy, hx, hy, 2.2f * s, navy);
                c.fillCircle(hx, hy, 1.3f * s, SKIN);
                c.line(cx - 1 * s * d, shy, x - 8.5f * s * d, shy - 2 * s, 2.2f * s, navy);
                c.fillCircle(x - 8.5f * s * d, shy - 2 * s, 1.3f * s, SKIN);
                break;
            case 2: // hands on knees
                c.line(sh1, shy, x + 4 * s * d, y - 6 * s, 2.2f * s, navy);
                c.line(cx - 1 * s * d, shy, x - 3 * s * d, y - 6.5f * s, 2.2f * s, navy);
                break;
            case 3: // reaching
                hx = x + 10.5f * s * d;
                hy = shy - 0.5f * s;
                c.line(sh1, shy, hx, hy, 2.3f * s, navy);
                c.fillCircle(hx, hy, 1.4f * s, SKIN);
                c.line(cx - 1 * s * d, shy, x - 5 * s * d, shy + 6 * s, 2.2f * s, navy);
                break;
            case 4: // pointing and shouting
                hx = x + 8 * s * d;
                hy = shy - 7 * s + (float) Math.sin(time * 14) * 0.8f * s;
                c.line(sh1, shy, hx, hy, 2.3f * s, navy);
                c.fillCircle(hx, hy, 1.4f * s, SKIN);
                c.line(cx - 1 * s * d, shy, x - 6 * s * d, shy + 6 * s, 2.2f * s, navy);
                break;
            case 5: // startled hands up
                c.line(sh1, shy, x + 6 * s * d, shy - 8 * s, 2.2f * s, navy);
                c.line(cx - 1 * s * d, shy, x - 6 * s * d, shy - 8 * s, 2.2f * s, navy);
                break;
            default: // running: one arm forward with the torch, one swinging
                hx = x + (6.5f * s + sw * 0.3f) * d;
                hy = shy + 3.5f * s;
                c.line(sh1, shy, hx, hy, 2.3f * s, navy);
                c.fillCircle(hx, hy, 1.4f * s, SKIN);
                c.fillRect(hx + (d > 0 ? 0 : -3.4f * s), hy - 1.2f * s, 3.4f * s, 2.2f * s, 0xFF8A94A3);
                c.line(cx - 1 * s * d, shy, x - (4.5f * s + sw * 0.5f) * d, shy + 5 * s, 2.2f * s, navy);
                break;
        }
        // head and cap
        float hxx = cx + lean * 0.5f;
        c.fillCircle(hxx, t - 3.8f * s, 3.7f * s, SKIN);
        c.fillPoly(new float[]{hxx - 4.3f * s, t - 5.0f * s, hxx + 4.3f * s, t - 5.0f * s, hxx + 3.8f * s, t - 8.6f * s,
                hxx - 3.8f * s, t - 8.6f * s}, 0xFF1B2A55);
        c.line(hxx, t - 5.2f * s, hxx + 6.4f * s * d, t - 4.8f * s, 1.5f * s, 0xFF0D1220);
        c.fillCircle(hxx, t - 6.9f * s, 0.9f * s, gold);
        c.line(hxx + 0.6f * s * d, t - 2.0f * s, hxx + 3.5f * s * d, t - 2.0f * s, 1.1f * s, 0xFF3A2A1E);
        c.fillCircle(hxx + 1.9f * s * d, t - 4.1f * s, 0.55f * s, dark);
        if (mode == 4 || mode == 5) c.fillCircle(hxx + 2.2f * s * d, t - 0.9f * s, 0.95f * s, 0xFF3A0A0A);
        if (run || mode == 2) {
            // sweat
            for (int i = 0; i < 2; i++) {
                float f = (time * 1.4f + i * 0.5f) % 1f;
                c.fillCircle(hxx - (4.8f + i * 1.6f) * s * d, t - 7 * s - f * 5 * s, 0.8f * s, alpha(0xFF9ADBFF, 1 - f));
            }
        }
    }

    private void hud(Gfx c, float top, float bottom) {
        float gx0 = 24, gx1 = 336, yy = top + 17;
        float f = Ui.clamp((gapPx - CATCH) / 190f, 0, 1);
        float runnerX = gx1 - 4, copX = gx1 - 22 - f * (gx1 - gx0 - 26);
        boolean danger = f < 0.28f;
        float pulse = danger ? 0.5f + 0.5f * (float) Math.sin(time * 14) : 0;
        c.fillRoundRect(gx0 - 6, yy - 12, gx1 - gx0 + 12, 24, 12, 0xCC0B0E12);
        int col = f < 0.28f ? Ui.BAD : f < 0.55f ? Ui.ORANGE : Ui.GOOD;
        c.fillRoundRect(copX, yy - 4, runnerX - copX, 8, 4, alpha(col, 0.55f + 0.45f * pulse));
        // cop head icon
        c.fillCircle(copX, yy, 8.5f + pulse * 1.5f, 0xFF1B2A55);
        c.fillCircle(copX, yy + 1.5f, 5.2f, SKIN);
        c.fillRect(copX - 6, yy - 5.5f, 12, 3.5f, 0xFF1B2A55);
        c.fillCircle(copX, yy - 4.5f, 1.4f, Ui.ACCENT);
        // runner icon
        int rc = h.female() ? 0xFFFF7AA8 : 0xFFFFB347;
        c.fillCircle(runnerX, yy, 8.5f, rc);
        c.fillCircle(runnerX, yy + 0.5f, 5, SKIN);
        // progress to the corner
        float pf = Ui.clamp(wx / len, 0, 1);
        float py0 = top + 38;
        c.fillRoundRect(gx0, py0, 262, 6, 3, 0xCC0B0E12);
        c.fillRoundRect(gx0, py0, Math.max(6, 262 * pf), 6, 3, Ui.ACCENT);
        if (shortX >= 0 && hasShortPlaced()) {
            float mx = gx0 + 262 * (shortX / len);
            c.fillRect(mx - 1, py0 - 2, 2, 10, 0xFF8A94A3);
        }
        c.text("הפינה", gx1, py0 + 7, 12, Ui.INK, Gfx.RIGHT);
    }

    private void hints(Gfx c, float bottom) {
        if (state != RUN && state != INTRO) return;
        String msg = null;
        for (int i = 0; i < obs.size(); i++) {
            Ob o = obs.get(i);
            if (o.broken || o.counted || o.kind == DECK) continue;
            float d = o.x - wx;
            if (d < -20) continue;
            if (d < 250 && (i == 0 || i == 1)) msg = i == 0 ? "לחיצה = קפיצה" : "החזקה = קפיצה גבוהה";
            break;
        }
        if (msg == null && state == INTRO) msg = "מוכנים?";
        if (msg == null) return;
        float a = 0.6f + 0.4f * (float) Math.sin(time * 6);
        float w = c.textWidth(msg, 14) + 26;
        c.fillRoundRect(Mini.W / 2 - w / 2, bottom - 30, w, 22, 11, 0xB00B0E12);
        c.text(msg, Mini.W / 2, bottom - 14.5f, 14, alpha(Ui.INK, a), Gfx.CENTER);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        float w = 232, hgt = 112;
        float x0 = cx - w / 2, y0 = cy - hgt / 2;
        boolean flash = ((int) (t * 5)) % 2 == 0;
        int fc = flash ? 0xFFFF2A3A : 0xFF2A6BFF;
        c.fillRoundRect(x0, y0, w, hgt, 10, 0xFF1A1740);
        c.fillRect(x0, y0 + 40, w, 28, 0xFF2A2C4A);
        c.fillRect(x0, y0 + 80, w, hgt - 80, 0xFF23253A);
        for (int i = 0; i < 5; i++) c.fillRect(x0 + 10 + i * 46, y0 + 14 + (i % 2) * 8, 28, 30 - (i % 2) * 8, 0xFF171A3C);
        for (int i = 0; i < 9; i++) c.fillRect(x0 + 16 + i * 25, y0 + 22 + (i * 7) % 18, 4, 5, 0xAAFFD27A);
        // the glow is kept inside the card by building it from strips rather than from big circles
        for (int i = 0; i < 4; i++) {
            float gw = 40 + i * 22;
            c.fillRect(x0, y0 + 46 - i * 2, gw, hgt - 46 - 26 + i * 4, alpha(fc, 0.07f));
        }
        c.fillCircle(x0 + w - 36, y0 + 20, 12, 0xFFF2EBCB);
        // lamp
        c.fillRect(x0 + 150, y0 + 24, 2.4f, 58, 0xFF0E1020);
        quad(c, x0 + 150, y0 + 26, x0 + 156, y0 + 26, x0 + 186, y0 + 84, x0 + 122, y0 + 84, 0x18FFE6A0);
        c.fillCircle(x0 + 154, y0 + 26, 4, 0xFFFFF2C8);
        float bob = (float) Math.abs(Math.sin(t * 9)) * 2;
        float shift = (float) Math.sin(t * 1.3f) * 4;
        cop(c, x0 + 62 + shift, y0 + 88 - bob * 0.3f, 1.55f, t * 0.8f, 0, 1);
        quad(c, x0 + 74 + shift, y0 + 62, x0 + 74 + shift, y0 + 65, x0 + 128, y0 + 70, x0 + 128, y0 + 48, 0x22FFF3B0);
        Ui.person(c, x0 + 134, y0 + 88 - bob, 1.6f, false, t * 1.5f, true, 0xFFFFB347);
        c.text("עצור!", x0 + 70, y0 + 30, 16, 0xFFFF6B6B, Gfx.CENTER);
    }

    // ------------------------------------------------------------------ bot

    /** Jump hold times (seconds) used by the bot: a quick tap, and a full leap. */
    private static final float TAP = 0.07f, LEAP = 0.42f;

    /**
     * Distance window (from the runner's toe to the obstacle's front, at take-off) in which a jump with
     * the given hold clears {@code o} at speed {@code speed}. Returns {lo, hi} or null if none exists.
     */
    static float[] window(float w, float hitBelow, boolean solid, float top, float speed, float hold) {
        float lo = 1e9f, hi = -1e9f;
        for (int di = -4; di <= 200; di++) {
            if (jumpClears(di, w, hitBelow, solid, top, speed, hold)) {
                lo = Math.min(lo, di);
                hi = Math.max(hi, di);
            }
        }
        return hi < lo ? null : new float[]{lo, hi};
    }

    static float arc(float t, float hold) {
        float tc = Math.max(hold, MIN_HOLD), apex = JUMP_V / G;
        if (tc >= apex || t <= tc) return JUMP_V * t - 0.5f * G * t * t;
        float y1 = JUMP_V * tc - 0.5f * G * tc * tc, v1 = (JUMP_V - G * tc) * CUT, u = t - tc;
        return y1 + v1 * u - 0.5f * G * u * u;
    }

    private static boolean jumpClears(float d, float w, float hitBelow, boolean solid, float top, float speed,
            float hold) {
        float ox = HALF + d; // obstacle front relative to the runner's centre at take-off
        float prev = 0;
        for (float t = 0; t < 1.6f; t += 1f / 360f) {
            float y = arc(t, hold);
            float xc = speed * t;
            boolean over = ox < xc + HALF && ox + w > xc - HALF;
            if (over && y < hitBelow) {
                // came down onto its top edge instead of crashing into it?
                if (solid && prev >= top - FOOT && y <= top) return true;
                return false;
            }
            if (t > 0.1f && y <= 0) return xc - HALF >= ox + w;
            prev = y;
        }
        return true;
    }

    @Override
    void bot(Random r, float skill) {
        float bdt = Math.max(0, Math.min(0.2f, time - botT));
        botT = time;
        if (botDown) {
            botHeld += bdt;
            if (botHeld >= botHold) {
                botDown = false;
                up(0, 0);
            }
            return;
        }
        if (state != RUN || bdt <= 0) return;
        if (skill < 0.12f) {
            // clueless: jabs at the screen now and then
            if (r.nextFloat() < 0.9f * bdt) press(r.nextBoolean() ? TAP : LEAP);
            return;
        }
        if (!botDecided) {
            botDecided = true;
            botWantShort = r.nextFloat() < Math.max(0, skill - 0.25f) * 1.15f;
        }
        float stand = grounded ? py : 99;
        float toe = wx + HALF;
        float frame = v * bdt;
        boolean onDeck = grounded && py >= DECK_TOP - 1;
        boolean climbing = onDeck || (hasShortPlaced() && botWantShort && !shortDone && !(grounded && py < DECK_TOP - 1 && wx > shortX + 60));
        // 1. the next ordinary threat (what lies under the scaffold is skipped when climbing)
        Ob tgt = null;
        for (int i = 0; i < obs.size(); i++) {
            Ob o = obs.get(i);
            if (o.broken || o.kind == DECK || o.x + o.w < wx - HALF) continue;
            if (o.top <= stand + 2 && grounded) continue; // already above it (on the scaffold)
            if (climbing && o.x >= shortX && o.x < bEnd) continue;
            if (o.x - toe > 300) break;
            if (tgt == null || o.x < tgt.x) tgt = o;
        }
        // 2. the scaffold
        if (climbing && grounded) {
            if (py >= DECK_TOP - 1) {
                if (wx < gapX0) {
                    if (botTarget != GAPT) {
                        botTarget = GAPT;
                        botNoise = noise(r, skill) * 0.5f;
                        botMiss = r.nextFloat() < 0.5f * (1 - skill) * (1 - skill);
                    }
                    float dd = gapX0 - toe + HALF;
                    float want = botMiss ? -40 : 14 + botNoise;
                    if (dd <= want + frame * 0.5f) press(skill > 0.5f ? 0.3f : TAP + 0.1f);
                    return;
                }
            } else if (shortX - toe < 130 && (tgt == null || tgt.x > shortX - 30)) {
                if (botTarget != SHORT) {
                    botTarget = SHORT;
                    botNoise = noise(r, skill);
                    botMiss = r.nextFloat() < 0.5f * (1 - skill) * (1 - skill);
                }
                float dd = shortX - toe;
                float want = botMiss ? -90 : 38 + botNoise;
                if (dd <= want + frame * 0.5f) press(LEAP);
                return;
            }
        }
        if (tgt == null || !grounded) return;
        if (botTarget != tgt) {
            botTarget = tgt;
            botNoise = noise(r, skill);
            botMiss = r.nextFloat() < 0.5f * (1 - skill) * (1 - skill);
            botHold = 0;
            boolean high = isHigh(tgt.kind);
            boolean wrong = r.nextFloat() < (1 - skill) * (high ? 0.4f : 0.15f);
            float hold = high ? LEAP : TAP;
            if (wrong) hold = high ? TAP : LEAP;
            botPlanHold = hold;
            String key = tgt.kind + ":" + (int) v + ":" + (int) (hold * 100);
            Float cached = WINDOWS.get(key);
            if (cached == null) {
                float[] wdw = window(tgt.w, tgt.hitBelow, tgt.solid, tgt.top, v, hold);
                cached = wdw == null ? 30f : (wdw[0] + wdw[1]) / 2;
                WINDOWS.put(key, cached);
            }
            botPlanD = cached;
        }
        float dd = tgt.x - toe;
        float want = botMiss ? -60 : botPlanD + botNoise;
        if (dd <= want + frame * 0.5f && dd > -40) press(botPlanHold);
    }

    private static final java.util.HashMap<String, Float> WINDOWS = new java.util.HashMap<>();
    private static final Ob SHORT = new Ob(), GAPT = new Ob();
    private float botPlanHold;

    private float noise(Random r, float skill) {
        // timing error in units of distance: an expert is within a couple of units
        float sigma = (0.012f + (1 - skill) * 0.12f) * v;
        return (float) r.nextGaussian() * sigma;
    }

    private void press(float hold) {
        botDown = true;
        botHeld = 0;
        botHold = hold;
        down(RX, gy);
    }
}
