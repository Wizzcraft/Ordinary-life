package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Barman shift, four orders on a busy night. Each order is a drink built in layers: hold a bottle
 * to pour, let go at the line. The bottle tilts up gradually (a long hold pours fast, a tap is a
 * trickle) and it drips for a moment after you let go, so you have to release a touch early.
 * Overshoot a layer and it stays: you cannot take liquid out, and anything past the rim is wasted.
 *
 * The risk is in the rhythm. A fast full pour is quick but lands past the line if you are late;
 * tapping a short layer up is safe but spends the customer's patience, and a PERFECT drink must also
 * be served while he still smiles. Rounds: 1) two layers, 2) three layers, 3) two glasses at once,
 * 4) the regular, whose usual is shown for a moment and then the lines vanish: pour from memory.
 */
final class BarMini extends Mini {
    private static final int[] COL = {0xFFFFA92B, 0xFFFF4D6D, 0xFF34E0A1, 0xFF8C7BFF};
    /** Volume per second at full tilt: slim, fat (fast, glugs), flask, tapered (slow). */
    private static final float[] FLOW = {0.30f, 0.50f, 0.38f, 0.27f};
    private static final float[] BX = {45, 135, 225, 315};
    private static final float BL = 60;
    private static final float TILT_UP = 0.45f, TILT_DOWN = 0.12f;
    private static final float PEEK = 2.6f;
    private static final int MAXS = 6;
    private static final float BELL_X = 318;
    private static final int NEON_PINK = 0xFFFF3DB8, NEON_CYAN = 0xFF3DF2FF;

    /** Bottle silhouettes with the mouth at the origin and the body running down the +y axis. */
    private static final float[][] SHAPE = {
            {-3.5f, 0, 3.5f, 0, 3.5f, 16, 8, 24, 8, 60, -8, 60, -8, 24, -3.5f, 16},
            {-3.5f, 0, 3.5f, 0, 3.5f, 12, 9, 17, 15, 25, 18, 36, 16, 47, 10, 56, -10, 56, -16, 47, -18, 36, -15, 25,
                    -9, 17, -3.5f, 12},
            {-4, 0, 4, 0, 4, 10, 14, 22, 14, 56, -14, 56, -14, 22, -4, 10},
            {-3, 0, 3, 0, 3, 22, 12, 60, -12, 60, -3, 22}};
    private static final float[][] LABEL = {{34, 50, 8}, {30, 46, 16}, {30, 48, 14}, {38, 54, 6}};
    private static final String[] NAMES = {"זריחה על הים", "ירח סגול", "סערה בכוס", "שמש חצות", "גל חם", "ערפל ירוק",
            "לילה בנמל", "ברק אדום", "קרח ופלפל", "עננים מתוקים"};
    private static final int[] SHIRTS = {0xFF5B8DEF, 0xFFE86A5C, 0xFF7BC47F, 0xFFC38AE8};

    // ---- layout, set by draw() ----
    private float lTop = 166, lBottom = 664, counterY = 572, glassTop = 382, glassBottom = 570, rowY = 654;

    // ---- the order ----
    private int nG, round, sel;
    private final int[][] rB = new int[2][3];
    private final float[][] rT = new float[2][3];
    private final int[] rN = new int[2];
    private final String[] name = new String[2];
    private boolean hidden;
    private float diff, tolP, tolO, patMax, pat, hiddenT;

    // ---- the glasses ----
    private final int[][] segB = new int[2][MAXS];
    private final float[][] segTop = new float[2][MAXS];
    private final int[] nSeg = new int[2];
    private final float[] spill = new float[2];

    // ---- the pour ----
    private boolean finger;
    private int bottle = -1, lastBottle = -1, lastGlass;
    private float tilt, lift, flowNow, poured, pourSfxT, time, entry;
    private boolean spillTold, wrongTold;

    // ---- the end ----
    private int phase, result;
    private float outro, mood;
    private final int[] grade = new int[2];

    // ---- compact view of a glass: layers by bottle, strays ignored ----
    private final int[] cb = new int[MAXS];
    private final float[] ct = new float[MAXS];
    private int cn;

    // ---- bot ----
    private boolean botInit;
    private float botWait, botAim, botBellT;
    private final float[] memErr = new float[3];
    private final int[] memB = new int[3];

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("ברמן", "ברמנית");
    }

    @Override
    String howTo() {
        return "מחזיקים בקבוק כדי לשפוך, ומשחררים בקו שבכוס. כל שכבה בקבוק אחר, מה שנשפך לא חוזר. "
                + "סיימתם? לחצו על הפעמון.";
    }

    @Override
    void begin(int rnd, float difficulty) {
        round = Math.max(0, Math.min(3, rnd));
        diff = Ui.clamp(difficulty, 0, 1);
        Random r = h.rnd();
        nG = round == 2 ? 2 : 1;
        hidden = round == 3;
        float[] pBase = {0.07f, 0.055f, 0.05f, 0.045f};
        float[] patBase = {10, 11, 12, 11};
        tolP = pBase[round] - 0.02f * diff;
        tolO = tolP * 2 + 0.01f;
        patMax = pat = patBase[round] * (1 - 0.15f * diff);
        for (int g = 0; g < 2; g++) {
            nSeg[g] = 0;
            spill[g] = 0;
            grade[g] = MISS;
        }
        int a = r.nextInt(NAMES.length);
        for (int g = 0; g < nG; g++) {
            int layers = round == 0 || round == 2 ? 2 : 3;
            rN[g] = layers;
            int start = r.nextInt(4);
            int step = r.nextBoolean() ? 1 : 3;
            for (int k = 0; k < layers; k++) rB[g][k] = (start + step * k) % 4;
            if (hidden) {
                // The usual: three even thirds, so it is easy to hold in the head.
                rT[g][0] = 0.30f;
                rT[g][1] = 0.60f;
                rT[g][2] = 0.90f;
                rB[g][0] = 1;
                rB[g][1] = 0;
                rB[g][2] = 3;
                name[g] = "הרגיל";
            } else if (layers == 2) {
                rT[g][0] = 0.38f + r.nextFloat() * 0.14f;
                rT[g][1] = 0.80f + r.nextFloat() * 0.12f;
                name[g] = NAMES[(a + g * 3) % NAMES.length];
            } else {
                rT[g][0] = 0.24f + r.nextFloat() * 0.08f;
                rT[g][1] = 0.50f + r.nextFloat() * 0.10f;
                rT[g][2] = 0.80f + r.nextFloat() * 0.12f;
                name[g] = NAMES[(a + g * 3) % NAMES.length];
            }
        }
        sel = 0;
        finger = false;
        bottle = lastBottle = -1;
        tilt = lift = flowNow = poured = pourSfxT = 0;
        spillTold = wrongTold = false;
        phase = 0;
        result = RUNNING;
        outro = 0;
        mood = 1;
        entry = 0.8f;
        hiddenT = 0;
        botInit = false;
    }

    // ------------------------------------------------------------------ rules

    private float level(int g) {
        return nSeg[g] == 0 ? 0 : segTop[g][nSeg[g] - 1];
    }

    /** Builds the compact layer list of glass g: slivers under 0.025 are ignored, equal neighbours merge. */
    private void compact(int g) {
        cn = 0;
        float prev = 0;
        for (int s = 0; s < nSeg[g]; s++) {
            float top = segTop[g][s];
            float th = top - prev;
            prev = top;
            if (th < 0.025f && s < nSeg[g] - 1) continue;
            if (th < 0.012f) continue;
            if (cn > 0 && cb[cn - 1] == segB[g][s]) {
                ct[cn - 1] = top;
            } else if (cn < MAXS) {
                cb[cn] = segB[g][s];
                ct[cn] = top;
                cn++;
            }
        }
    }

    /** Which layer the player is working on now: the unfinished top layer, else the next one. */
    private int curLayer(int g) {
        compact(g);
        int k = cn;
        if (cn > 0 && cn <= rN[g] && ct[cn - 1] < rT[g][cn - 1] - tolP * 0.5f) k = cn - 1;
        return Math.min(k, rN[g] - 1);
    }

    private void pourStep(float dt) {
        boolean holding = finger && bottle >= 0;
        tilt = Ui.clamp(tilt + (holding ? dt / TILT_UP : -dt / TILT_DOWN), 0, 1);
        lift = Ui.clamp(lift + (holding ? dt * 7 : -dt * 7), 0, 1);
        flowNow = 0;
        if (tilt <= 0 || lastBottle < 0) return;
        int b = lastBottle, g = lastGlass;
        float glug = 1;
        if (b == 1) glug = 1 + 0.45f * (float) (Math.sin(time * 13) * Math.sin(time * 5.3 + 1));
        else if (b == 2) glug = 1 + 0.15f * (float) Math.sin(time * 9);
        flowNow = FLOW[b] * tilt * glug;
        float amt = flowNow * dt;
        float lv = level(g);
        if (lv + amt > 1) {
            float over = lv + amt - 1;
            amt = 1 - lv;
            spill[g] += over;
            if (!spillTold) {
                spillTold = true;
                h.text(gx(g), glassTop + 26, "נשפך!", Ui.BAD);
            }
            if ((int) (time * 12) % 3 == 0) h.burst(gx(g) + gw() / 2, glassTop, COL[b], 2);
            h.shake(2);
        }
        if (amt > 0) {
            int n = nSeg[g];
            if (n == 0 || segB[g][n - 1] != b) {
                if (n < MAXS) {
                    segB[g][n] = b;
                    segTop[g][n] = lv;
                    nSeg[g] = ++n;
                }
            }
            segB[g][n - 1] = n == MAXS ? segB[g][n - 1] : b;
            segTop[g][n - 1] += amt;
            poured += amt;
        }
        pourSfxT -= dt;
        if (pourSfxT <= 0 && tilt > 0.25f) {
            h.sfx(Sfx.POUR);
            pourSfxT = 0.3f;
        }
    }

    private void onRelease(int g, int b) {
        if (poured < 0.03f || hidden || phase != 0) return;
        compact(g);
        if (cn == 0) return;
        int k = cn - 1;
        float x = gx(g), y = glassTop + 26;
        if (k >= rN[g] || cb[k] != rB[g][k]) return;
        float err = ct[k] - rT[g][k];
        float ae = Math.abs(err);
        if (ae <= tolP) {
            h.text(x, y, "מדויק!", Ui.ACCENT);
            h.burst(x, yOf(ct[k]), COL[b], 6);
            h.sfx(Sfx.DING);
        } else if (err > tolO) {
            h.text(x, y, "יותר מדי", Ui.BAD);
            h.sfx(Sfx.BAD);
            h.shake(3);
        } else if (err < -tolO) {
            h.text(x, y, "חסר, אפשר להוסיף", Ui.DIM);
            h.sfx(Sfx.TICK);
        } else {
            h.text(x, y, "בסדר", Ui.GOOD);
            h.sfx(Sfx.TICK);
        }
    }

    private int judge(int g) {
        compact(g);
        if (cn != rN[g]) return MISS;
        float err = 0;
        for (int k = 0; k < cn; k++) {
            if (cb[k] != rB[g][k]) return MISS;
            err = Math.max(err, Math.abs(ct[k] - rT[g][k]));
        }
        if (spill[g] > 0.25f || err > tolO) return MISS;
        return err <= tolP && spill[g] < 0.04f ? PERFECT : OK;
    }

    private void finish(boolean timedOut) {
        int worst = PERFECT;
        for (int g = 0; g < nG; g++) {
            grade[g] = judge(g);
            worst = Math.min(worst, grade[g]);
        }
        boolean slow = pat < patMax * 0.15f;
        if (worst == PERFECT && slow) worst = OK;
        if (timedOut) worst = Math.min(worst, OK);
        result = worst;
        phase = 1;
        outro = 1.3f;
        finger = false;
        bottle = -1;
        for (int g = 0; g < nG; g++) {
            String s = grade[g] == PERFECT ? "מושלם!" : grade[g] == OK ? "טוב" : "לא זה";
            int col = grade[g] == PERFECT ? Ui.ACCENT : grade[g] == OK ? Ui.GOOD : Ui.BAD;
            if (nG == 2) h.text(gx(g), glassTop + 26, s, col);
        }
        float cx = BX[0] - 2;
        if (timedOut) {
            h.text(cx + 10, counterY - 60, "נמאס לו לחכות", Ui.BAD);
            h.sfx(Sfx.BAD);
            h.shake(4);
        } else if (result == PERFECT) {
            h.text(180, glassTop + 8, "מושלם!", Ui.ACCENT);
            h.burst(180, glassTop + 30, Ui.ACCENT, 14);
            h.sfx(Sfx.PERFECT);
        } else if (result == OK) {
            h.text(180, glassTop + 8, slow && grade[0] == PERFECT ? "איטי, אבל טוב" : "שתה ולא התלונן", Ui.GOOD);
            h.sfx(Sfx.GOOD);
        } else {
            h.text(180, glassTop + 8, "לא מה שהזמנתי", Ui.BAD);
            h.sfx(Sfx.BAD);
            h.shake(5);
        }
    }

    @Override
    int update(float dt) {
        time += dt;
        if (phase == 1) {
            outro -= dt;
            tilt = Math.max(0, tilt - dt / TILT_DOWN);
            lift = Math.max(0, lift - dt * 7);
            return outro <= 0 ? result : RUNNING;
        }
        if (entry > 0) {
            entry -= dt;
            return RUNNING;
        }
        hiddenT += dt;
        pat -= dt;
        mood = Ui.clamp(pat / patMax, 0, 1);
        pourStep(dt);
        if (pat <= 0) finish(true);
        return RUNNING;
    }

    // ------------------------------------------------------------------ input

    private float gx(int g) {
        return nG == 1 ? 180 : g == 0 ? 120 : 240;
    }

    private float gw() {
        return nG == 1 ? 68 : 54;
    }

    private float yOf(float level) {
        float bot = glassBottom - 3;
        return bot - level * (bot - glassTop);
    }

    private float bellY() {
        return (glassTop + glassBottom) / 2;
    }

    private boolean ready() {
        for (int g = 0; g < nG; g++) if (level(g) < 0.05f) return false;
        return true;
    }

    @Override
    void down(float x, float y) {
        if (phase != 0 || entry > 0 || finger) return;
        if (Math.abs(x - BELL_X) <= 32 && Math.abs(y - bellY()) <= 34) {
            if (ready()) {
                finish(false);
            } else {
                h.text(BELL_X, bellY() - 40, "עוד לא", Ui.DIM);
                h.sfx(Sfx.TICK);
            }
            return;
        }
        if (y >= rowY - 78) {
            for (int b = 0; b < 4; b++) {
                if (Math.abs(x - BX[b]) > 44) continue;
                bottle = lastBottle = b;
                lastGlass = sel;
                finger = true;
                poured = 0;
                spillTold = false;
                if (!hidden && level(sel) < 0.995f) {
                    int k = curLayer(sel);
                    boolean topUp = cn > 0 && k == cn - 1 && cb[cn - 1] == b;
                    if (!topUp && b != rB[sel][k] && !wrongTold) {
                        h.text(gx(sel), glassTop + 26, "לא הבקבוק הזה", Ui.DIM);
                        h.sfx(Sfx.TICK);
                    }
                }
                return;
            }
            return;
        }
        if (nG == 2) {
            for (int g = 0; g < nG; g++) {
                if (Math.abs(x - gx(g)) <= 52 && y >= glassTop - 14 && y <= glassBottom + 8 && g != sel) {
                    sel = g;
                    h.sfx(Sfx.TICK);
                }
            }
        }
    }

    @Override
    void up(float x, float y) {
        if (!finger) return;
        finger = false;
        if (bottle >= 0) onRelease(lastGlass, bottle);
        bottle = -1;
    }

    @Override
    void pause() {
        finger = false;
        bottle = -1;
        tilt = 0;
        lift = 0;
    }

    // ------------------------------------------------------------------ drawing

    private static int al(int col, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (col & 0xFFFFFF);
    }

    private static void glowLine(Gfx c, float x1, float y1, float x2, float y2, float w, int col) {
        c.line(x1, y1, x2, y2, w * 5, al(col, 22));
        c.line(x1, y1, x2, y2, w * 2.6f, al(col, 55));
        c.line(x1, y1, x2, y2, w, al(col, 255));
    }

    private void layout(float top, float bottom) {
        lTop = top;
        lBottom = bottom;
        counterY = bottom - 92;
        glassBottom = counterY - 2;
        glassTop = Math.max(top + 108, glassBottom - 190);
        rowY = bottom - 10;
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawWall(c, top);
        drawCustomer(c);
        for (int g = 0; g < nG; g++) drawGlass(c, g);
        drawCounter(c);
        drawTickets(c, top);
        drawBell(c);
        drawBottles(c);
        drawPour(c);
    }

    private void drawWall(Gfx c, float top) {
        float wallTop = top, wallBot = counterY;
        c.fillRect(0, wallTop, 360, wallBot - wallTop, 0xFF151A25);
        for (int i = 0; i < 9; i++) c.fillRect(i * 45, wallTop, 1, wallBot - wallTop, 0x14FFFFFF);
        float decorTop = top + 54, decorBot = glassTop - 58;
        if (decorBot - decorTop >= 56) {
            // Back shelf with silhouettes and a neon sign, only when the screen is tall enough.
            float sy = decorBot;
            c.fillRect(0, sy, 360, 4, 0xFF3A2E28);
            for (int i = 0; i < 9; i++) {
                int k = (i * 5 + 2) % 4;
                float bx = 22 + i * 40;
                float s = 0.62f + 0.12f * ((i * 7) % 3);
                c.fillCircle(bx, sy - 22, 24, al(COL[k], 16));
                drawBottleShape(c, k, bx, sy - 60 * s, 0, s, al(COL[k], 120), false);
            }
            float flick = (time * 3 % 7 > 6.7f) ? 0.5f : 1;
            int ns = al(NEON_PINK, (int) (255 * flick));
            float sx = 180, sy2 = decorTop + (decorBot - decorTop - 70) * 0.5f + 14;
            for (int d = -2; d <= 2; d += 2) c.text("בר", sx + d, sy2 + 22 + d / 2, 32, al(NEON_PINK, (int) (40 * flick)), Gfx.CENTER);
            c.text("בר", sx, sy2 + 22, 32, ns, Gfx.CENTER);
        }
        glowLine(c, 14, top + 52, 346, top + 52, 1.2f, al(NEON_CYAN, 160));
    }

    private void drawCustomer(Gfx c) {
        float walk = entry > 0 ? entry / 0.8f : 0;
        float x = BX[0] - 1 - walk * 70;
        float feet = counterY + 10;
        float s = 4.2f;
        int shirt = SHIRTS[round % SHIRTS.length];
        Ui.person(c, x, feet, s, round % 2 == 1, time, walk > 0, shirt);
        float headY = feet - 15.5f * s - 3.4f * s, r = 3.7f * s;
        // Face: eyes, and a mouth that follows his patience (or the verdict once served).
        float m = phase == 1 ? (result == MISS ? 0 : result == OK ? 0.6f : 1) : mood;
        c.fillCircle(x - 5, headY - 2, 1.7f, 0xFF20242C);
        c.fillCircle(x + 5, headY - 2, 1.7f, 0xFF20242C);
        float my = headY + 7;
        if (m > 0.55f) {
            c.line(x - 5, my - 1, x, my + 2.5f, 1.6f, 0xFF20242C);
            c.line(x, my + 2.5f, x + 5, my - 1, 1.6f, 0xFF20242C);
        } else if (m > 0.28f) {
            c.line(x - 4, my + 1, x + 4, my + 1, 1.6f, 0xFF20242C);
        } else {
            c.line(x - 5, my + 3, x, my - 0.5f, 1.6f, 0xFF20242C);
            c.line(x, my - 0.5f, x + 5, my + 3, 1.6f, 0xFF20242C);
            c.line(x - 8, headY - 7, x - 2, headY - 5, 1.6f, 0xFF20242C);
            c.line(x + 8, headY - 7, x + 2, headY - 5, 1.6f, 0xFF20242C);
        }
        if (phase == 0 && entry <= 0) {
            float bw = 54, by = headY - r - 14;
            int col = mood > 0.55f ? Ui.GOOD : mood > 0.28f ? Ui.ORANGE : Ui.BAD;
            if (mood < 0.28f && (int) (time * 6) % 2 == 0) col = al(col, 120);
            Ui.bar(c, x - bw / 2, by, bw, 7, mood, col);
        }
    }

    private void drawCounter(Gfx c) {
        c.fillRect(0, counterY, 360, lBottom - counterY + 60, 0xFF241A18);
        c.fillRect(0, counterY, 360, 7, 0xFF5A4034);
        c.fillRect(0, counterY + 7, 360, 2, 0x44000000);
        glowLine(c, 0, counterY + 8, 360, counterY + 8, 1.2f, NEON_PINK);
    }

    private void drawTickets(Gfx c, float top) {
        for (int g = 0; g < nG; g++) {
            float w = nG == 1 ? 250 : 164;
            float cx = nG == 1 ? 180 : g == 0 ? 98 : 262;
            float x = cx - w / 2, y = top + 4, hh = 42;
            boolean on = nG == 2 && g == sel;
            c.fillRoundRect(x, y, w, hh, 8, 0xFF232B38);
            c.strokeRoundRect(x, y, w, hh, 8, on || hidden ? 2 : 1, hidden ? Ui.ACCENT : on ? NEON_CYAN : 0xFF3B4658);
            boolean show = !hidden || hiddenT < PEEK + 0.8f || phase == 1;
            // mini glass
            float ix = x + w - 26, iy = y + 5, iw = 16, ih = 32;
            c.fillRoundRect(ix, iy, iw, ih, 2, 0xFF11151C);
            if (show) {
                float prev = 0;
                for (int k = 0; k < rN[g]; k++) {
                    float a = prev, b = rT[g][k];
                    c.fillRect(ix + 1, iy + ih - 1 - b * (ih - 2), iw - 2, (b - a) * (ih - 2), COL[rB[g][k]]);
                    prev = b;
                }
            } else {
                c.text("?", ix + iw / 2, iy + 23, 20, Ui.ACCENT, Gfx.CENTER);
            }
            c.strokeRoundRect(ix, iy, iw, ih, 2, 1.2f, 0x99FFFFFF);
            String label = hidden && phase == 0 && hiddenT < PEEK ? "הרגיל, תזכרו!" : name[g];
            c.text(label, x + (w - 34) / 2, y + 19, 14, hidden ? Ui.ACCENT : Ui.INK, Gfx.CENTER);
            // layer dots, in pouring order
            if (show) {
                float dx = x + (w - 34) / 2 - (rN[g] - 1) * 9;
                for (int k = 0; k < rN[g]; k++) c.fillCircle(dx + k * 18, y + 31, 4.6f, COL[rB[g][k]]);
            } else {
                c.text("מהזיכרון", x + (w - 34) / 2, y + 35, 11, Ui.DIM, Gfx.CENTER);
            }
            if (hidden && phase == 0 && hiddenT < PEEK) {
                Ui.bar(c, x + 8, y + hh - 4, w - 16, 3, 1 - hiddenT / PEEK, Ui.ACCENT);
            }
        }
    }

    private void drawGlass(Gfx c, int g) {
        float x = gx(g), w = gw(), x0 = x - w / 2;
        boolean on = g == sel;
        // neon halo of the glass in use
        if (nG == 2 && on) c.fillRoundRect(x0 - 8, glassTop - 6, w + 16, glassBottom - glassTop + 10, 10, al(NEON_CYAN, 22));
        c.fillRect(x0, glassTop, w, glassBottom - glassTop, 0x30A8C8FF);
        float prev = 0;
        float bot = glassBottom - 3;
        for (int s = 0; s < nSeg[g]; s++) {
            float top = segTop[g][s];
            float y1 = yOf(top), y0 = yOf(prev);
            if (y0 - y1 >= 0.5f) {
                c.fillRect(x0 + 2, y1, w - 4, y0 - y1, al(COL[segB[g][s]], 235));
                c.fillRect(x0 + 2, y1, w - 4, 2, 0x66FFFFFF);
                int nb = Math.max(1, (int) ((y0 - y1) / 18));
                for (int i = 0; i < nb; i++) {
                    float ph = (time * 0.35f + i * 0.37f + s * 0.21f + g * 0.1f) % 1;
                    c.fillCircle(x0 + 8 + ((i * 29 + s * 13) % (int) (w - 16)), y0 - ph * (y0 - y1), 1.6f, 0x55FFFFFF);
                }
            }
            prev = top;
        }
        // fill lines
        boolean lines = !hidden || phase == 1 || hiddenT < PEEK + 0.8f;
        float fade = hidden && phase == 0 ? Ui.clamp(1 - (hiddenT - PEEK) / 0.8f, 0, 1) : 1;
        if (lines && fade > 0 && entry <= 0.4f) {
            int cur = phase == 0 ? curLayer(g) : -1;
            for (int k = 0; k < rN[g]; k++) {
                float ly = yOf(rT[g][k]);
                int a = (int) (255 * fade);
                if (k == cur && on) {
                    float band = tolP * (bot - glassTop);
                    boolean inside = Math.abs(level(g) - rT[g][k]) <= tolP;
                    c.fillRect(x0 - 4, ly - band, w + 8, band * 2, al(inside ? Ui.GOOD : Ui.ACCENT, (int) (70 * fade)));
                    c.fillRect(x0 - 6, ly - 1, w + 12, 2.4f, al(inside ? Ui.GOOD : Ui.ACCENT, a));
                } else {
                    for (float dx = x0 - 4; dx < x0 + w + 4; dx += 9) c.fillRect(dx, ly - 0.7f, 5, 1.6f, al(0xFFFFFFFF, (int) (150 * fade)));
                }
                c.fillCircle(x0 + w + 12, ly, 5.2f, al(COL[rB[g][k]], a));
            }
        }
        // glass walls
        c.fillRect(x0 - 2, glassTop, 3, glassBottom - glassTop, 0x99FFFFFF);
        c.fillRect(x0 + w - 1, glassTop, 3, glassBottom - glassTop, 0x99FFFFFF);
        c.fillRect(x0 - 2, glassBottom - 4, w + 4, 5, 0xBBFFFFFF);
        c.fillRect(x0 + 5, glassTop + 6, 3, (glassBottom - glassTop) * 0.55f, 0x33FFFFFF);
        if (spill[g] > 0.005f) {
            float pw = Math.min(90, 12 + spill[g] * 130);
            int sc = nSeg[g] > 0 ? COL[segB[g][nSeg[g] - 1]] : 0xFFFFFFFF;
            c.fillRoundRect(x - pw / 2, glassBottom - 2, pw, 4, 2, al(sc, 190));
            c.line(x0 + w + 1, glassTop, x0 + w + 1, glassTop + Math.min(spill[g] * 400, glassBottom - glassTop), 2, al(sc, 200));
        }
    }

    private void drawBell(Gfx c) {
        float x = BELL_X, y = bellY();
        boolean ok = ready() && phase == 0;
        float pulse = ok ? 1 + 0.06f * (float) Math.sin(time * 5) : 1;
        int body = ok ? 0xFFFFC94D : 0xFF6A5F45;
        if (ok) c.fillCircle(x, y, 38 * pulse, al(Ui.ACCENT, 30));
        c.fillRoundRect(x - 26, y + 14, 52, 7, 3, Ui.darken(body, 0.7f));
        c.fillPoly(new float[]{x - 22, y + 14, x - 20, y - 2, x - 12, y - 12, x, y - 17, x + 12, y - 12, x + 20, y - 2, x + 22, y + 14},
                body);
        c.fillCircle(x, y - 19, 3.5f, Ui.darken(body, 0.8f));
        c.fillRect(x - 13, y - 6, 3, 14, 0x55FFFFFF);
        c.text("הגש", x, y + 40, 13, ok ? Ui.ACCENT : Ui.DIM, Gfx.CENTER);
    }

    private void drawBottles(Gfx c) {
        for (int b = 0; b < 4; b++) {
            float x = BX[b];
            boolean held = bottle == b && lift > 0.05f;
            c.fillRoundRect(x - 36, rowY - 3, 72, 6, 3, al(COL[b], 150));
            c.fillCircle(x, rowY - 28, 38, al(COL[b], 20));
            c.fillRoundRect(x - 35, rowY - 3, 70, 3, 1.5f, al(COL[b], 255));
            if (held) {
                c.strokeRoundRect(x - 12, rowY - 62, 24, 58, 6, 1.5f, al(COL[b], 120));
                continue;
            }
            boolean hint = phase == 0 && entry <= 0 && !hidden && !finger && b == rB[sel][curLayer(sel)];
            if (hint) {
                float p = 0.5f + 0.5f * (float) Math.sin(time * 6);
                c.strokeRoundRect(x - 30, rowY - 68, 60, 72, 14, 1.5f + p, al(0xFFFFFFFF, (int) (70 + 90 * p)));
            }
            drawBottleShape(c, b, x, rowY - 4 - BL, 0, 1, COL[b], true);
        }
    }

    /** Draws bottle kind k with its mouth at (mx, my), rotated by ang radians about the mouth. */
    private void drawBottleShape(Gfx c, int k, float mx, float my, float ang, float s, int col, boolean detail) {
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float[] sh = SHAPE[k];
        float[] p = new float[sh.length];
        for (int i = 0; i < sh.length; i += 2) {
            float lx = sh[i] * s, ly = sh[i + 1] * s;
            p[i] = mx + lx * ca - ly * sa;
            p[i + 1] = my + lx * sa + ly * ca;
        }
        c.fillPoly(p, detail ? Ui.darken(col, 0.55f) : col);
        if (!detail) return;
        // inner glow of the liquid, label, cap and a glint
        float[] lb = LABEL[k];
        float hw = lb[2] * s, y0 = lb[0] * s, y1 = lb[1] * s;
        float[] q = {-hw, y0, hw, y0, hw, y1, -hw, y1};
        float[] lq = new float[8];
        for (int i = 0; i < 8; i += 2) {
            lq[i] = mx + q[i] * ca - q[i + 1] * sa;
            lq[i + 1] = my + q[i] * sa + q[i + 1] * ca;
        }
        c.fillPoly(lq, 0xFFEDE6D6);
        c.fillCircle(mx + 0 * ca - ((y0 + y1) / 2) * sa, my + ((y0 + y1) / 2) * ca, 2.4f * s, col);
        float cx1 = mx + (-4.5f * s) * ca, cy1 = my + (-4.5f * s) * sa;
        float cx2 = mx + (4.5f * s) * ca, cy2 = my + (4.5f * s) * sa;
        c.line(cx1 - (-2 * s) * sa, cy1 + (-2 * s) * ca, cx2 - (-2 * s) * sa, cy2 + (-2 * s) * ca, 4 * s, 0xFFD7DDE5);
    }

    private void drawPour(Gfx c) {
        if (lastBottle < 0 || lift <= 0.01f) return;
        int b = lastBottle, g = lastGlass;
        float e = lift * lift * (3 - 2 * lift);
        float dir = BX[b] >= gx(g) ? 1 : -1;
        float restX = BX[b], restY = rowY - 4 - BL;
        float pourX = gx(g) - dir * 4, pourY = glassTop - 8;
        float mx = restX + (pourX - restX) * e, my = restY + (pourY - restY) * e;
        float ang = -dir * (float) Math.toRadians(112) * e;
        // faint stand where the bottle was
        drawBottleShape(c, b, mx, my, ang, 1, COL[b], true);
        if (tilt > 0.05f && flowNow > 0.001f) {
            float surf = yOf(level(g));
            float wob = (float) Math.sin(time * 40) * 0.6f;
            float sw = 1.5f + 5f * flowNow / 0.5f;
            c.line(pourX + wob, pourY + 3, pourX, surf, sw, al(COL[b], 235));
            c.line(pourX + wob - 0.5f, pourY + 3, pourX, surf, sw * 0.35f, 0x99FFFFFF);
            c.fillCircle(pourX - 4, surf, 2.2f, al(COL[b], 200));
            c.fillCircle(pourX + 5, surf - 1, 1.8f, al(COL[b], 200));
        }
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        c.fillCircle(cx, cy, 58, al(NEON_PINK, 28));
        c.fillCircle(cx, cy, 44, al(NEON_CYAN, 24));
        float x0 = cx - 24, w = 48, top = cy - 34, bot = cy + 52;
        c.fillRect(x0, top, w, bot - top, 0x30A8C8FF);
        float lvl = bot;
        float[] up = {0.38f, 0.70f, 0.96f};
        for (int k = 0; k < 3; k++) {
            float ny = bot - up[k] * (bot - top);
            if (k == 2) ny = top + 6 + 3 * (float) Math.sin(t * 3);
            c.fillRect(x0 + 2, ny, w - 4, lvl - ny, al(COL[(k * 2 + 1) % 4], 235));
            c.fillRect(x0 + 2, ny, w - 4, 2, 0x66FFFFFF);
            lvl = ny;
        }
        c.fillRect(x0 - 2, top, 3, bot - top, 0x99FFFFFF);
        c.fillRect(x0 + w - 1, top, 3, bot - top, 0x99FFFFFF);
        c.fillRect(x0 - 2, bot - 4, w + 4, 5, 0xBBFFFFFF);
        // the bottle leans in from the right and pours
        float e = 0.5f + 0.5f * (float) Math.sin(t * 2);
        drawBottleShape(c, 1, cx + 6, top - 18 + 3 * e, (float) Math.toRadians(-125), 0.9f, COL[1], true);
        c.line(cx + 2, top - 14, cx + 2, top + 4, 3, al(COL[1], 235));
        drawBottleShape(c, 0, cx + 78, cy + 16, 0, 0.8f, COL[0], true);
        drawBottleShape(c, 3, cx - 78, cy + 16, 0, 0.8f, COL[3], true);
    }

    // ------------------------------------------------------------------ bot

    private float botLead(Random r, float skill) {
        return 0.06f + (float) r.nextGaussian() * (1 - skill) * 0.03f;
    }

    @Override
    void bot(Random r, float skill) {
        if (phase != 0 || entry > 0) return;
        float dt = 1f / 30;
        if (!botInit) {
            botInit = true;
            botWait = 0.2f + (1 - skill) * 0.5f;
            botBellT = 0.2f + (1 - skill) * 1.2f;
            for (int k = 0; k < 3; k++) {
                // Memory of the usual: a weak player forgets a bottle or misjudges a line.
                memErr[k] = hidden ? (float) r.nextGaussian() * (1 - skill) * 0.09f : 0;
                memB[k] = hidden && r.nextFloat() < (1 - skill) * 0.5f ? r.nextInt(4) : rB[0][k];
            }
        }
        int g = sel;
        if (finger) {
            float lv = level(g);
            float lead = flowNow * tilt * 0.06f;
            if (lv + lead >= botAim || lv >= 1f) {
                up(0, 0);
                botWait = 0.1f + (1 - skill) * 0.4f * r.nextFloat();
            } else if (skill < 0.25f && r.nextFloat() < 0.025f) {
                up(0, 0);
                botWait = 0.2f;
            }
            return;
        }
        botWait -= dt;
        if (botWait > 0) return;
        float sigma = 0.13f * (1 - skill) + 0.003f;
        float tolBot = 0.01f + (1 - skill) * 0.06f;
        if (skill < 0.15f) {
            // Clueless: any bottle, any amount, and the bell whenever.
            if (r.nextFloat() < 0.12f && ready()) {
                down(BELL_X, bellY());
                return;
            }
            botAim = level(g) + 0.1f + r.nextFloat() * 0.5f;
            down(BX[r.nextInt(4)], rowY - 30);
            return;
        }
        compact(g);
        int bt = -1;
        float aim = 0;
        if (cn > 0 && cn <= rN[g] && cb[cn - 1] == rB[g][cn - 1] && ct[cn - 1] < rT[g][cn - 1] - tolBot) {
            bt = cb[cn - 1];
            aim = rT[g][cn - 1];
        } else if (cn < rN[g]) {
            bt = hidden ? memB[cn] : rB[g][cn];
            aim = rT[g][cn] + (hidden ? memErr[cn] : 0);
        }
        if (bt >= 0) {
            botAim = aim + (float) r.nextGaussian() * sigma - 0.0f;
            // a hand cannot be that exact
            float lead = botLead(r, skill);
            botAim -= (lead - 0.06f) * 0.5f;
            down(BX[bt], rowY - 30);
            if (!finger) return;
            // A quick tap is enough when the gap is tiny: release on the next frame.
            return;
        }
        if (nG == 2 && g == 0 && level(1) < 0.05f) {
            down(gx(1), (glassTop + glassBottom) / 2);
            botWait = 0.15f + (1 - skill) * 0.4f;
            return;
        }
        botBellT -= dt;
        if (botBellT <= 0 && ready()) down(BELL_X, bellY());
    }
}
