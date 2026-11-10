package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Doctor school: one cramming session. A chalkboard shows pairs of (silly symptom -> silly remedy)
 * for a few seconds, then the board is wiped and twelve patients come in one by one; tap the remedy
 * that fits. The set grows with difficulty (3..6 pairs) and the look time shrinks. A wrong answer
 * shows the right pair at once, so a lost memory is re-learned on the spot.
 *
 * The risk: you can peek at the board (twice), but a peeked answer is worth only half a point.
 *
 * Everything medical is cartoon fiction. {@link Med} also holds the shared icon set that
 * {@link DoctorMini} draws with, so the lesson teaches exactly what the shift uses.
 */
final class DoctorStudyMini extends Mini {
    private static final int QUESTIONS = 12;
    private static final int LOOK = 0, ASK = 1, FEED = 2;

    // pairs of this session: symptom pairSym[i] is cured by remedy pairRem[i]
    private int k;
    private final int[] pairSym = new int[6], pairRem = new int[6];
    private final int[] btnPair = new int[6];
    private final int[] order = new int[QUESTIONS];
    private final boolean[] seen = new boolean[6];

    private int phase, q, peeksLeft, correct, streak, pickedBtn, curPair;
    private float diff, t, lookT, qTime, qLimit, peekT, feedT, points, time;
    private boolean peeked, feedOk, timedOut;

    // layout, recomputed on every draw (touch uses the last one)
    private float top = 166, bottom = 664;
    private final float[] bx = new float[6], by = new float[6];
    private float bw, bh;
    private float readyX, readyY, readyW = 220, readyH = 48;
    private float peekX, peekY, peekW, peekH;

    // bot
    private int botQ = -1;
    private boolean botKnows, botPeek;
    private float botDelay;

    // ------------------------------------------------------------------ shared icon set

    /** Cartoon ailments and cures: icons, names and the fixed symptom -> remedy table. */
    static final class Med {
        static final int SYMPTOMS = 12, REMEDIES = 6;
        /** Remedy that cures each symptom. Look-alike symptoms (3/8, 0/4, 5/1) belong to different remedies. */
        static final int[] REMEDY_OF = {0, 3, 4, 0, 2, 1, 4, 3, 1, 5, 2, 5};
        static final String[] SYM_NAME = {"שיהוק כרוני", "אף אדום מדי", "רגל שנרדמה", "ראש מסתובב", "גרד מסתורי",
                "ראש מעלה אדים", "עיטוש בלי סוף", "אצבעות קפואות", "בטן מקרקרת", "עין מקפצת", "אוזן מצלצלת",
                "לשון מסובכת"};
        static final String[] REM_NAME = {"חיבוק גדול", "כוס מים", "מדבקת כוכב", "קוביית קרח", "כפית דבש", "תנומה קלה"};
        static final int[] SYM_COL = {0xFFFFD84D, 0xFFFF6B6B, 0xFFB69CFF, 0xFF6FD6FF, 0xFFFFA25A, 0xFFFF8A3D,
                0xFF9BE37A, 0xFFBFE9FF, 0xFFF59BC8, 0xFFFFFFFF, 0xFFFFC857, 0xFF5BD6B8};
        static final int[] REM_COL = {0xFFFF6FA0, 0xFF5AA9FF, 0xFFFFC857, 0xFF8FE3E8, 0xFFFFA25A, 0xFFC3A6FF};
        static final int BADGE = 0xFF1B222C;

        private Med() {
        }

        /** The two symptoms a remedy cures. */
        static int symptomOf(int remedy, int which) {
            int seenCount = 0;
            for (int s = 0; s < SYMPTOMS; s++) {
                if (REMEDY_OF[s] != remedy) continue;
                if (seenCount == which) return s;
                seenCount++;
            }
            return 0;
        }

        private static void poly(Gfx c, float cx, float cy, float u, int col, float... p) {
            float[] q = new float[p.length];
            for (int i = 0; i < p.length; i += 2) {
                q[i] = cx + p[i] * u;
                q[i + 1] = cy + p[i + 1] * u;
            }
            c.fillPoly(q, col);
        }

        private static void ln(Gfx c, float cx, float cy, float u, float x1, float y1, float x2, float y2, float w,
                int col) {
            c.line(cx + x1 * u, cy + y1 * u, cx + x2 * u, cy + y2 * u, w * u, col);
        }

        /** A symptom: a ROUND dark badge with a glyph. Radius r. */
        static void sym(Gfx c, int id, float cx, float cy, float r) {
            int col = SYM_COL[id];
            c.fillCircle(cx, cy, r, BADGE);
            c.fillCircle(cx, cy, r - r * 0.07f, (col & 0x00FFFFFF) | 0x33000000);
            c.fillCircle(cx, cy, r - r * 0.12f, BADGE);
            float u = r * 0.068f;
            switch (id) {
                case 0: // hiccup: lightning zigzag
                    poly(c, cx, cy, u, col, 3, -10, -6, 2, -1, 2, -3, 10, 6, -3, 1, -3, 5, -10);
                    break;
                case 1: // red nose: bridge, round tip, nostrils
                    poly(c, cx, cy, u, col, -2.6f, -10, 2.6f, -10, 6, -1, -6, -1);
                    c.fillCircle(cx, cy + 3 * u, 6.6f * u, col);
                    c.fillCircle(cx - 2.4f * u, cy + 5 * u, 1.3f * u, BADGE);
                    c.fillCircle(cx + 2.4f * u, cy + 5 * u, 1.3f * u, BADGE);
                    c.fillCircle(cx - 2.2f * u, cy + 0.6f * u, 1.5f * u, 0x88FFFFFF);
                    break;
                case 2: // sleeping foot: boot with a Z
                    poly(c, cx, cy, u, col, -8, -2, -2, -2, -2, 3, 8, 5, 8, 9.5f, -8, 9.5f);
                    ln(c, cx, cy, u, 2, -9, 8, -9, 2, col);
                    ln(c, cx, cy, u, 8, -9, 2, -4, 2, col);
                    ln(c, cx, cy, u, 2, -4, 8, -4, 2, col);
                    break;
                case 3: { // dizzy spiral
                    float px = cx, py = cy;
                    for (int s = 1; s <= 30; s++) {
                        double a = s * 0.37, rr = a * 0.78;
                        float nx = cx + (float) Math.cos(a) * (float) rr * u, ny = cy + (float) Math.sin(a) * (float) rr * u;
                        c.line(px, py, nx, ny, 1.9f * u, col);
                        px = nx;
                        py = ny;
                    }
                    break;
                }
                case 4: // mysterious itch: three claw scratches
                    for (int i = -1; i <= 1; i++) ln(c, cx, cy, u, i * 5.5f - 3.5f, -9, i * 5.5f + 3.5f, 9, 2.4f, col);
                    break;
                case 5: // steaming head: flame
                    poly(c, cx, cy, u, col, 0, -10, 4.5f, -3.5f, 7, 3, 4, 9.5f, -4, 9.5f, -7, 3, -4.5f, -2, -1.5f, -4);
                    poly(c, cx, cy, u, BADGE, 0, 1, 3, 5, 1.5f, 8.5f, -1.5f, 8.5f, -3, 5);
                    break;
                case 6: // endless sneeze: big drop then a trail of smaller ones
                    c.fillCircle(cx - 5.5f * u, cy, 4.8f * u, col);
                    c.fillCircle(cx + 1.2f * u, cy - 2 * u, 3.2f * u, col);
                    c.fillCircle(cx + 6.5f * u, cy - 4 * u, 2 * u, col);
                    c.fillCircle(cx + 3 * u, cy + 4 * u, 2.2f * u, col);
                    c.fillCircle(cx + 8 * u, cy + 1 * u, 1.3f * u, col);
                    break;
                case 7: // frozen fingers: snowflake
                    for (int i = 0; i < 3; i++) {
                        double a = i * Math.PI / 3 + 0.2;
                        float dx = (float) Math.cos(a) * 9.5f, dy = (float) Math.sin(a) * 9.5f;
                        ln(c, cx, cy, u, -dx, -dy, dx, dy, 1.8f, col);
                        c.fillCircle(cx + dx * u, cy + dy * u, 1.6f * u, col);
                        c.fillCircle(cx - dx * u, cy - dy * u, 1.6f * u, col);
                    }
                    break;
                case 8: // rumbling belly: a ring with a wave
                    c.fillCircle(cx, cy, 8.6f * u, col);
                    c.fillCircle(cx, cy, 6.2f * u, BADGE);
                    ln(c, cx, cy, u, -4.5f, 0, -2.2f, -2.6f, 1.6f, col);
                    ln(c, cx, cy, u, -2.2f, -2.6f, 0, 2.6f, 1.6f, col);
                    ln(c, cx, cy, u, 0, 2.6f, 2.2f, -2.6f, 1.6f, col);
                    ln(c, cx, cy, u, 2.2f, -2.6f, 4.5f, 0, 1.6f, col);
                    break;
                case 9: // bouncing eye
                    poly(c, cx, cy, u, col, -10, 1, -5, -4, 0, -5.5f, 5, -4, 10, 1, 5, 6, 0, 7.5f, -5, 6);
                    c.fillCircle(cx, cy + 1 * u, 3.4f * u, BADGE);
                    c.fillCircle(cx + 0.8f * u, cy + 0.2f * u, 1.1f * u, col);
                    ln(c, cx, cy, u, -6, -9, -3.5f, -7.5f, 1.4f, col);
                    ln(c, cx, cy, u, 6, -9, 3.5f, -7.5f, 1.4f, col);
                    break;
                case 10: // ringing ear: a bell with ring marks
                    poly(c, cx, cy, u, col, -6, 5, -5, -3, -2.5f, -7.5f, 2.5f, -7.5f, 5, -3, 6, 5);
                    c.fillRect(cx - 8 * u, cy + 4.5f * u, 16 * u, 2.4f * u, col);
                    c.fillCircle(cx, cy + 9 * u, 2 * u, col);
                    ln(c, cx, cy, u, -9.5f, -4, -8.5f, 1, 1.4f, col);
                    ln(c, cx, cy, u, 9.5f, -4, 8.5f, 1, 1.4f, col);
                    break;
                default: // tangled tongue: a bow-tie knot
                    poly(c, cx, cy, u, col, -10, -6.5f, 0, 0, -10, 6.5f);
                    poly(c, cx, cy, u, col, 10, -6.5f, 0, 0, 10, 6.5f);
                    c.fillCircle(cx, cy, 2.6f * u, col);
                    break;
            }
        }

        /** A remedy: a ROUNDED-SQUARE tile with a glyph. Half-size r. */
        static void rem(Gfx c, int id, float cx, float cy, float r) {
            int col = REM_COL[id];
            c.fillRoundRect(cx - r, cy - r, r * 2, r * 2, r * 0.3f, BADGE);
            c.strokeRoundRect(cx - r, cy - r, r * 2, r * 2, r * 0.3f, Math.max(1.4f, r * 0.09f), col);
            float u = r * 0.068f;
            switch (id) {
                case 0: // a big hug: heart
                    c.fillCircle(cx - 4.6f * u, cy - 3.2f * u, 5.2f * u, col);
                    c.fillCircle(cx + 4.6f * u, cy - 3.2f * u, 5.2f * u, col);
                    poly(c, cx, cy, u, col, -9.5f, -1, 9.5f, -1, 0, 10);
                    break;
                case 1: // a glass of water: drop
                    poly(c, cx, cy, u, col, 0, -10, 6.6f, 1.5f, -6.6f, 1.5f);
                    c.fillCircle(cx, cy + 3.4f * u, 7 * u, col);
                    c.fillCircle(cx - 2.4f * u, cy + 2 * u, 1.6f * u, 0x99FFFFFF);
                    break;
                case 2: { // a star sticker
                    float[] p = new float[20];
                    for (int i = 0; i < 10; i++) {
                        double a = -Math.PI / 2 + i * Math.PI / 5;
                        float rr = (i % 2 == 0 ? 10f : 4.4f) * u;
                        p[i * 2] = cx + (float) Math.cos(a) * rr;
                        p[i * 2 + 1] = cy + (float) Math.sin(a) * rr;
                    }
                    c.fillPoly(p, col);
                    break;
                }
                case 3: // an ice cube: hexagon with a Y
                    poly(c, cx, cy, u, col, 0, -10, 8.7f, -5, 8.7f, 5, 0, 10, -8.7f, 5, -8.7f, -5);
                    ln(c, cx, cy, u, 0, 0, 0, 9.5f, 1.4f, BADGE);
                    ln(c, cx, cy, u, 0, 0, -8.2f, -4.7f, 1.4f, BADGE);
                    ln(c, cx, cy, u, 0, 0, 8.2f, -4.7f, 1.4f, BADGE);
                    break;
                case 4: // a spoon of honey
                    c.fillCircle(cx, cy - 4.8f * u, 5.6f * u, col);
                    ln(c, cx, cy, u, 0, -1, 0, 9.5f, 3.2f, col);
                    c.fillCircle(cx - 1.8f * u, cy - 6.4f * u, 1.5f * u, 0x77FFFFFF);
                    break;
                default: // a short nap: crescent moon
                    c.fillCircle(cx - 0.5f * u, cy, 8.6f * u, col);
                    c.fillCircle(cx + 3.6f * u, cy - 3 * u, 7.4f * u, BADGE);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ Mini

    @Override
    int rounds() {
        return 1;
    }

    @Override
    String title() {
        return "לימודי רפואה";
    }

    @Override
    String howTo() {
        return "מסתכלים בלוח וזוכרים איזה טיפול מתאים לאיזה תסמין, ואז מטפלים בחולים מהזיכרון. "
                + "אפשר להציץ, אבל תשובה עם הצצה שווה חצי.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = Ui.clamp(difficulty, 0, 1);
        k = 3 + Math.min(3, (int) (diff * 4.2f));
        Random r = h.rnd();
        // k distinct remedies, one of their two symptoms each
        int[] rems = {0, 1, 2, 3, 4, 5};
        shuffle(rems, r);
        for (int i = 0; i < k; i++) {
            pairRem[i] = rems[i];
            pairSym[i] = Med.symptomOf(rems[i], r.nextInt(2));
            btnPair[i] = i;
            seen[i] = false;
        }
        shuffle(btnPair, k, r);
        // twelve questions, every pair at least once, never the same pair twice in a row
        int[] pool = new int[QUESTIONS];
        for (int i = 0; i < QUESTIONS; i++) pool[i] = i < k ? i : r.nextInt(k);
        for (int tries = 0; tries < 20; tries++) {
            shuffle(pool, r);
            boolean ok = true;
            for (int i = 1; i < QUESTIONS; i++) if (pool[i] == pool[i - 1]) ok = false;
            if (ok) break;
        }
        System.arraycopy(pool, 0, order, 0, QUESTIONS);
        lookT = 2.4f + k * 0.85f - diff * 0.8f;
        qLimit = 3.3f - diff * 0.7f;
        phase = LOOK;
        t = 0;
        q = 0;
        peeksLeft = 2;
        correct = 0;
        streak = 0;
        points = 0;
        peekT = 0;
        feedT = 0;
        botQ = -1;
        lay(166, Math.max(300, h.screenH()) - 96);
    }

    private static void shuffle(int[] a, Random r) {
        shuffle(a, a.length, r);
    }

    private static void shuffle(int[] a, int n, Random r) {
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    @Override
    int update(float dt) {
        time += dt;
        t += dt;
        if (phase == LOOK) {
            if (t >= lookT) startAsk();
            return RUNNING;
        }
        if (phase == ASK) {
            if (peekT > 0) {
                peekT -= dt;
                return RUNNING;
            }
            qTime += dt;
            if (qLimit - qTime < 1.0f && (int) ((qLimit - qTime) * 6) != (int) ((qLimit - qTime + dt) * 6)) {
                h.sfx(Sfx.TICK);
            }
            if (qTime >= qLimit) resolve(-1);
            return RUNNING;
        }
        feedT -= dt;
        if (feedT > 0) return RUNNING;
        q++;
        if (q >= QUESTIONS) {
            return points >= 0.9f * QUESTIONS ? PERFECT : points >= 0.65f * QUESTIONS ? OK : MISS;
        }
        nextQuestion();
        return RUNNING;
    }

    private void startAsk() {
        phase = ASK;
        q = 0;
        h.sfx(Sfx.WHOOSH);
        nextQuestion();
    }

    private void nextQuestion() {
        phase = ASK;
        curPair = order[q];
        qTime = 0;
        peekT = 0;
        peeked = false;
        timedOut = false;
    }

    /** Answers with button b, or -1 when time ran out. */
    private void resolve(int b) {
        pickedBtn = b;
        boolean right = b >= 0 && btnPair[b] == curPair;
        timedOut = b < 0;
        feedOk = right;
        seen[curPair] = true;
        float cx = W / 2, cy = top + 70;
        if (right) {
            correct++;
            streak++;
            points += peeked ? 0.5f : 1f;
            h.text(cx, cy, peeked ? "נכון, בהצצה" : streak >= 3 ? "רצף " + streak + "!" : "נכון!",
                    peeked ? Ui.ACCENT : Ui.GOOD);
            h.burst(bx[b] + bw / 2, by[b] + bh / 2, Med.REM_COL[pairRem[curPair]], peeked ? 6 : 12);
            h.sfx(streak >= 3 ? Sfx.DING : Sfx.GOOD);
            feedT = 0.55f;
        } else {
            streak = 0;
            h.text(cx, cy, timedOut ? "איטי מדי" : "לא זה", Ui.BAD);
            h.shake(5);
            h.sfx(Sfx.BAD);
            feedT = 0.9f;
        }
        phase = FEED;
    }

    @Override
    void down(float x, float y) {
        if (phase == LOOK) {
            if (Ui.in(x, y, readyX, readyY, readyW, readyH)) {
                h.sfx(Sfx.TAP);
                startAsk();
            }
            return;
        }
        if (phase != ASK || peekT > 0) return;
        if (peeksLeft > 0 && !peeked && Ui.in(x, y, peekX, peekY, peekW, peekH)) {
            peeksLeft--;
            peeked = true;
            peekT = 1.0f;
            h.sfx(Sfx.TAP);
            return;
        }
        for (int i = 0; i < k; i++) {
            if (Ui.in(x, y, bx[i], by[i], bw, bh)) {
                resolve(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private void lay(float t0, float b0) {
        top = t0;
        bottom = b0;
        float H = bottom - top;
        readyW = 220;
        readyH = 48;
        readyX = (W - readyW) / 2;
        readyY = bottom - readyH - 6;
        float cardH = Ui.clamp(H * 0.32f, 84, 160);
        peekW = 78;
        peekH = cardH;
        peekX = 262;
        peekY = top + 34;
        int cols = k <= 3 ? k : k == 4 ? 2 : 3;
        int rows = (k + cols - 1) / cols;
        float areaTop = top + 34 + cardH + 10, areaBot = bottom - 4;
        float gap = 10;
        bw = (340 - (cols - 1) * gap) / cols;
        bh = Math.min(92, (areaBot - areaTop - (rows - 1) * gap) / rows);
        float totalH = rows * bh + (rows - 1) * gap;
        float y0 = areaBot - totalH;
        for (int i = 0; i < k; i++) {
            int rr = i / cols, cc = i % cols;
            int inRow = Math.min(cols, k - rr * cols);
            float rowW = inRow * bw + (inRow - 1) * gap;
            bx[i] = (W - rowW) / 2 + cc * (bw + gap);
            by[i] = y0 + rr * (bh + gap);
        }
    }

    @Override
    void draw(Gfx c, float t0, float b0) {
        lay(t0, b0);
        if (phase == LOOK) drawLook(c, true);
        else drawAsk(c);
    }

    private void chalkboard(Gfx c, float x, float y, float w, float hh) {
        c.fillRoundRect(x - 6, y - 6, w + 12, hh + 12, 12, 0xFF6B4A2B);
        c.fillRoundRect(x, y, w, hh, 8, 0xFF1F3A2E);
        for (int i = 0; i < 4; i++) {
            c.fillRoundRect(x + w * (0.08f + i * 0.22f), y + hh - 5, w * 0.1f, 4, 2, 0x22FFFFFF);
        }
    }

    private void drawLook(Gfx c, boolean withButton) {
        float by0 = top + 22, boardH = bottom - by0 - (withButton ? 62 : 6);
        chalkboard(c, 14, by0, 332, boardH);
        c.text("לזכור! הלוח יימחק", W / 2, by0 + 22, 15, 0xFFEFF5E6, Gfx.CENTER);
        float frac = 1 - t / lookT;
        if (withButton) Ui.bar(c, 40, by0 + 30, 280, 5, frac, frac < 0.25f ? Ui.BAD : 0xFFEFF5E6);
        drawPairs(c, by0 + 42, by0 + boardH - 6);
        if (!withButton) return;
        c.fillRoundRect(readyX, readyY, readyW, readyH, 14, Ui.GOOD);
        c.text("יאללה, מבחן!", W / 2, readyY + 31, 18, Ui.DARK_INK, Gfx.CENTER);
    }

    private void drawPairs(Gfx c, float y0, float y1) {
        int rows = (k + 1) / 2;
        float cellH = Math.min(104, (y1 - y0) / rows);
        float r = Math.min(30, cellH * 0.38f);
        y0 += ((y1 - y0) - rows * cellH) / 2;
        for (int i = 0; i < k; i++) {
            int rr = i / 2, cc = i % 2;
            boolean lonely = k % 2 == 1 && i == k - 1;
            float cx = lonely ? W / 2 : 14 + 332 * (cc == 0 ? 0.27f : 0.73f);
            float cy = y0 + rr * cellH + cellH / 2;
            float bob = (float) Math.sin(time * 3 + i) * 1.2f;
            Med.sym(c, pairSym[i], cx - 42, cy + bob, r);
            // arrow
            c.line(cx - 12, cy, cx + 8, cy, 2.4f, 0xFFEFF5E6);
            c.fillPoly(new float[]{cx + 4, cy - 6, cx + 14, cy, cx + 4, cy + 6}, 0xFFEFF5E6);
            Med.rem(c, pairRem[i], cx + 44, cy - bob, r * 0.92f);
        }
    }

    private void drawAsk(Gfx c) {
        float H = bottom - top;
        // progress pips and the question timer
        for (int i = 0; i < QUESTIONS; i++) {
            int col = i < q ? Ui.GOOD : i == q ? Ui.ACCENT : Ui.PANEL2;
            c.fillRoundRect(W / 2 - QUESTIONS * 13 + i * 26 + 2, top + 4, 22, 6, 3, col);
        }
        float left = phase == ASK ? 1 - qTime / qLimit : 0;
        boolean hurry = left < 0.3f;
        Ui.bar(c, 30, top + 16, 300, 7, left, hurry ? Ui.BAD : Ui.BLUE);

        // the patient's card
        float cardH = peekH, cy = peekY + cardH / 2;
        float bob = (float) Math.sin(time * 3) * 1.5f;
        c.fillRoundRect(20, peekY, 230, cardH, 14, 0xFFEFEAE0);
        c.fillRoundRect(20, peekY, 230, 16, 8, 0xFFB9C6CC);
        c.fillCircle(135, peekY + 8, 3, 0xFF6B7A82);
        float gTop = peekY + 20, gBot = peekY + cardH - 26;
        float gr = Math.min(46, (gBot - gTop) / 2);
        Med.sym(c, pairSym[curPair], 135, (gTop + gBot) / 2 + bob, gr);
        c.text(Med.SYM_NAME[pairSym[curPair]], 135, peekY + cardH - 10, 14, 0xFF2B2F33, Gfx.CENTER);
        if (phase == FEED) {
            int col = feedOk ? Ui.GOOD : Ui.BAD;
            c.strokeRoundRect(20, peekY, 230, cardH, 14, 3, col);
        }

        // peek button
        boolean canPeek = peeksLeft > 0 && !peeked && phase == ASK;
        c.fillRoundRect(peekX, peekY, peekW, peekH, 14, canPeek ? Ui.PANEL2 : Ui.PANEL);
        c.strokeRoundRect(peekX, peekY, peekW, peekH, 14, 2, canPeek ? Ui.ACCENT : Ui.PANEL2);
        float ex = peekX + peekW / 2, ey = peekY + peekH / 2 - 8;
        int ecol = canPeek ? Ui.ACCENT : Ui.DIM;
        c.fillPoly(new float[]{ex - 17, ey, ex - 8, ey - 8, ex, ey - 10, ex + 8, ey - 8, ex + 17, ey, ex + 8, ey + 8, ex,
                ey + 10, ex - 8, ey + 8}, ecol);
        c.fillCircle(ex, ey, 5, Ui.PANEL);
        c.text("הצצה", ex, peekY + peekH - 10, 13, ecol, Gfx.CENTER);
        for (int i = 0; i < 2; i++) c.fillCircle(ex - 6 + i * 12, peekY + 7, 3, i < peeksLeft ? Ui.ACCENT : Ui.PANEL2);

        // remedy buttons: a shelf
        for (int i = 0; i < k; i++) {
            int p = btnPair[i];
            boolean isRight = p == curPair, picked = i == pickedBtn;
            int edge = Med.REM_COL[pairRem[p]];
            int fill = Ui.PANEL;
            if (phase == FEED) {
                if (isRight) fill = 0xFF1F4A33;
                else if (picked) fill = 0xFF58262B;
            }
            c.fillRoundRect(bx[i], by[i], bw, bh, 14, fill);
            c.strokeRoundRect(bx[i], by[i], bw, bh, 14, phase == FEED && isRight ? 3 : 1.6f,
                    phase == FEED && isRight ? Ui.GOOD : edge & 0x99FFFFFF);
            float rr = Math.min(bh * 0.30f, 28);
            Med.rem(c, pairRem[p], bx[i] + bw / 2, by[i] + bh * 0.42f, rr);
            c.text(Med.REM_NAME[pairRem[p]], bx[i] + bw / 2, by[i] + bh - 7, 12, Ui.INK, Gfx.CENTER);
        }
        if (phase == FEED && !feedOk) {
            // the lesson sticks when the answer is shown beside the question
            float ax = 135, ay = peekY + cardH * 0.5f;
            c.fillRoundRect(ax - 50, ay - 20, 100, 40, 12, 0xEE12161C);
            Med.rem(c, pairRem[curPair], ax + 22, ay, 16);
            c.fillPoly(new float[]{ax - 22, ay - 6, ax - 10, ay, ax - 22, ay + 6}, Ui.ACCENT);
            Med.sym(c, pairSym[curPair], ax - 34, ay, 14);
        }
        if (peekT > 0) {
            c.fillRect(0, top, W, H, 0xCC12161C);
            drawLook(c, false);
        }
    }

    @Override
    void art(Gfx c, float cx, float cy, float tm) {
        chalkboard(c, cx - 78, cy - 52, 156, 104);
        for (int i = 0; i < 2; i++) {
            float y = cy - 24 + i * 46, b = (float) Math.sin(tm * 3 + i) * 1.5f;
            Med.sym(c, i == 0 ? 0 : 7, cx - 36, y + b, 16);
            c.line(cx - 14, y, cx + 4, y, 2.2f, 0xFFEFF5E6);
            c.fillPoly(new float[]{cx + 1, y - 5, cx + 10, y, cx + 1, y + 5}, 0xFFEFF5E6);
            Med.rem(c, i == 0 ? 1 : 3, cx + 38, y - b, 15);
        }
    }

    @Override
    void pause() {
        peekT = 0;
    }

    // ------------------------------------------------------------------ bot

    @Override
    void bot(Random r, float skill) {
        if (phase == LOOK) {
            if (skill > 0.2f && t >= lookT * (0.6f + 0.35f * skill)) down(readyX + 5, readyY + 5);
            return;
        }
        if (phase != ASK) return;
        if (botQ != q) {
            botQ = q;
            // Memory is honest: it weakens with more pairs, with a cut-short look and as the minutes pass.
            float used = Math.min(1f, Math.max(t, lookT * 0.5f) / lookT);
            float base = Ui.clamp(skill * 1.04f - 0.045f * (k - 3), 0, 0.97f) * (0.55f + 0.45f * used);
            float p = seen[curPair] ? 0.45f + 0.4f * skill : base * (1 - 0.012f * q);
            botKnows = r.nextFloat() < p;
            botPeek = !botKnows && skill > 0.45f && peeksLeft > 0 && r.nextFloat() < 0.5f + skill * 0.4f;
            botDelay = 0.55f + (1 - skill) * 1.7f + r.nextFloat() * 0.5f;
        }
        if (botPeek && !peeked && qTime >= botDelay * 0.4f) {
            down(peekX + 5, peekY + 5);
            botKnows = true;
            return;
        }
        if (peekT > 0 || qTime < botDelay) return;
        int pick = 0;
        if (botKnows) {
            for (int i = 0; i < k; i++) if (btnPair[i] == curPair) pick = i;
        } else {
            pick = r.nextInt(k);
        }
        down(bx[pick] + 4, by[pick] + 4);
    }
}
