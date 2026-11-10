package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Software-developer lesson (school): one session of five "what comes next" puzzles drawn on a
 * monitor. A sequence of blocks follows a hidden rule; the player picks the block that continues it.
 * Rules get subtler with difficulty: a cycling shape, shape and colour cycling at different
 * speeds, then numbers (steps, doubling, growing steps, sums of the last two). The rubber duck is a
 * one-time hint that removes two wrong answers for half a point.
 *
 * Every puzzle is generated and then proven: {@link #predict} is a small solver that tries every rule
 * in the universe against the visible blocks, and the puzzle is kept only if all rules that fit
 * agree on one next value. The test bot uses the same solver on the visible blocks only.
 *
 * This file also holds the desk drawing helpers (monitor, mug, duck, build light, blocks) that
 * {@link DevMini} reuses, so the two professions share one office.
 */
final class DevStudyMini extends Mini {
    static final int QUESTIONS = 5;
    static final int SHAPE = 0, COL = 1, NUM = 2;
    static final int[] PAL = {0xFFE5484D, 0xFF3E8EDE, 0xFF3FB68B, 0xFFF0B429, 0xFF9B6BDF};
    static final int WOOD = 0xFF4B3B2F, WOOD_HI = 0xFF65503F, WOOD_LO = 0xFF31261E;
    static final int TILE = 0xFF232C38, TILE_EDGE = 0xFF3C4858;

    private static final int ASK = 0, REVEAL = 1;

    // ------------------------------------------------------------------ puzzle

    static final class Puzzle {
        int n, tier;
        /** Visible sequence per attribute; seq[NUM] is null when numbers are not in play. */
        final int[][] seq = new int[3][];
        /** Four options as {shape, colour, number or -1}. */
        final int[][] opt = new int[4][];
        int correct;
    }

    /**
     * What this sequence can continue with under every simple rule it obeys: repeating cycles of
     * length 1..4 (seen repeat at least twice), and for numbers also constant steps, doubling or
     * tripling, steps that grow evenly, "sum of the previous two" and alternating steps. Returns the
     * single next value, or -1 when no rule fits or the rules disagree.
     */
    static int predict(int[] x, int n, boolean numeric, int hi) {
        int[] pred = {-2};
        boolean[] clash = {false};
        for (int p = 1; p <= 4; p++) {
            if (n - p < 2) continue;
            boolean fits = true;
            for (int i = p; i < n && fits; i++) fits = x[i] == x[i - p];
            if (fits) merge(pred, clash, x[n - p], hi);
        }
        if (numeric) {
            if (n >= 3) {
                int d = x[1] - x[0];
                boolean fits = true;
                for (int i = 2; i < n && fits; i++) fits = x[i] - x[i - 1] == d;
                if (fits) merge(pred, clash, x[n - 1] + d, hi);
                if (x[0] > 0 && x[1] % x[0] == 0 && x[1] / x[0] >= 2) {
                    int q = x[1] / x[0];
                    fits = true;
                    for (int i = 2; i < n && fits; i++) fits = x[i] == x[i - 1] * q;
                    if (fits) merge(pred, clash, x[n - 1] * q, hi);
                }
            }
            if (n >= 4) {
                int g = (x[2] - x[1]) - (x[1] - x[0]);
                boolean fits = g != 0;
                for (int i = 3; i < n && fits; i++) fits = (x[i] - x[i - 1]) - (x[i - 1] - x[i - 2]) == g;
                if (fits) merge(pred, clash, x[n - 1] + (x[n - 1] - x[n - 2]) + g, hi);
                fits = true;
                for (int i = 2; i < n && fits; i++) fits = x[i] == x[i - 1] + x[i - 2];
                if (fits) merge(pred, clash, x[n - 1] + x[n - 2], hi);
            }
            if (n >= 5) {
                boolean fits = true;
                for (int i = 3; i < n && fits; i++) {
                    fits = x[i] - x[i - 1] == x[i - 2] - x[i - 3];
                }
                if (fits) merge(pred, clash, x[n - 1] + (x[n - 2] - x[n - 3]), hi);
            }
        }
        return clash[0] || pred[0] < 0 ? -1 : pred[0];
    }

    private static void merge(int[] pred, boolean[] clash, int v, int hi) {
        if (v < 0 || v > hi) return; // a rule that leaves the board is not in the universe
        if (pred[0] == -2) pred[0] = v;
        else if (pred[0] != v) clash[0] = true;
    }

    private static int[] pick(Random r, int k, int dom, int base) {
        int[] pool = new int[dom];
        for (int i = 0; i < dom; i++) pool[i] = base + i;
        for (int i = dom - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = pool[i];
            pool[i] = pool[j];
            pool[j] = t;
        }
        int[] out = new int[k];
        System.arraycopy(pool, 0, out, 0, k);
        return out;
    }

    private static int[] cycle(Random r, int n, int p, int dom, int base) {
        int[] vals = pick(r, p, dom, base);
        int[] s = new int[n + 1];
        for (int i = 0; i <= n; i++) s[i] = vals[i % p];
        return s;
    }

    private static int[] constant(Random r, int n, int dom) {
        int[] s = new int[n + 1];
        java.util.Arrays.fill(s, r.nextInt(dom));
        return s;
    }

    /** Kinds: 0 steps, 1 two-number cycle, 2 doubling/tripling, 3 growing steps, 4 sum of two, 5 alternating steps. */
    private static int[] numRule(Random r, int kind, int n, boolean allowDown) {
        int[] s = new int[n + 1];
        switch (kind) {
            case 0: {
                int[] ds = allowDown ? new int[]{2, 3, 4, 5, 6, 7, 10, -2, -3, -4, -5} : new int[]{2, 3, 4, 5, 6, 7, 10};
                int d = ds[r.nextInt(ds.length)];
                s[0] = d > 0 ? 1 + r.nextInt(12) : -d * (n + 1) + r.nextInt(8);
                for (int i = 1; i <= n; i++) s[i] = s[i - 1] + d;
                break;
            }
            case 1: {
                int[] v = pick(r, 2, 40, 2);
                for (int i = 0; i <= n; i++) s[i] = v[i % 2];
                break;
            }
            case 2: {
                int q = 2 + r.nextInt(2);
                s[0] = 1 + r.nextInt(3);
                for (int i = 1; i <= n; i++) s[i] = s[i - 1] * q;
                break;
            }
            case 3: {
                int e = 1 + r.nextInt(3), g = 1 + r.nextInt(3);
                s[0] = 1 + r.nextInt(6);
                for (int i = 1; i <= n; i++) s[i] = s[i - 1] + e + g * (i - 1);
                break;
            }
            case 4:
                s[0] = 1 + r.nextInt(4);
                s[1] = 1 + r.nextInt(4);
                for (int i = 2; i <= n; i++) s[i] = s[i - 1] + s[i - 2];
                break;
            default: {
                int a = 3 + r.nextInt(7), b = 1 + r.nextInt(a - 1);
                if (r.nextBoolean()) { int t = a; a = -b; b = -t; }
                s[0] = 12 + r.nextInt(25);
                for (int i = 1; i <= n; i++) s[i] = s[i - 1] + (i % 2 == 1 ? a : b);
                break;
            }
        }
        for (int v : s) if (v < 1 || v > 99) return null;
        return s;
    }

    /** A puzzle for the given tier 0..4, proven to have exactly one right option. */
    static Puzzle make(Random r, int tier) {
        for (int tries = 0; tries < 400; tries++) {
            Puzzle p = tryMake(r, tier);
            if (p != null) return p;
        }
        return fallback(r);
    }

    private static Puzzle fallback(Random r) {
        int[][] f = new int[3][];
        f[SHAPE] = cycle(r, 5, 2, 5, 0);
        f[COL] = constant(r, 5, 5);
        Puzzle p = assemble(r, f, 5, 0);
        if (p == null) throw new IllegalStateException("fallback puzzle failed");
        return p;
    }

    private static Puzzle tryMake(Random r, int tier) {
        int[][] f = new int[3][];
        int n;
        switch (tier) {
            case 0: {
                n = 5;
                int a = r.nextInt(2);
                f[a] = cycle(r, n, 2 + r.nextInt(2), 5, 0);
                f[1 - a] = constant(r, n, 5);
                break;
            }
            case 1: {
                n = 6;
                int p1 = 2 + r.nextInt(2), p2 = r.nextInt(5) == 0 ? p1 : 5 - p1;
                f[SHAPE] = cycle(r, n, p1, 5, 0);
                f[COL] = cycle(r, n, p2, 5, 0);
                break;
            }
            case 2: {
                n = 5;
                f[NUM] = numRule(r, r.nextInt(4) == 0 ? 1 : 0, n, false);
                int a = r.nextInt(2);
                f[a] = cycle(r, n, 2 + r.nextInt(2), 5, 0);
                f[1 - a] = constant(r, n, 5);
                break;
            }
            case 3: {
                n = 5;
                int[] kinds = {0, 2, 3, 4, 5};
                f[NUM] = numRule(r, kinds[r.nextInt(kinds.length)], n, true);
                int a = r.nextInt(2);
                f[a] = cycle(r, n, 2 + r.nextInt(2), 5, 0);
                f[1 - a] = r.nextBoolean() ? constant(r, n, 5) : cycle(r, n, 2, 5, 0);
                break;
            }
            default: {
                n = 6;
                int[] kinds = {2, 3, 4, 5};
                f[NUM] = numRule(r, kinds[r.nextInt(kinds.length)], n, true);
                f[SHAPE] = cycle(r, n, 3, 5, 0);
                f[COL] = cycle(r, n, r.nextBoolean() ? 2 : 4, 5, 0);
                break;
            }
        }
        if (tier >= 2 && f[NUM] == null) return null;
        return assemble(r, f, n, tier);
    }

    /** Proves the puzzle with the solver, then builds four distinct options around the answer. */
    private static Puzzle assemble(Random r, int[][] f, int n, int tier) {
        Puzzle p = new Puzzle();
        p.n = n;
        p.tier = tier;
        int[] ans = {-1, -1, -1};
        for (int a = 0; a < 3; a++) {
            if (f[a] == null) continue;
            p.seq[a] = java.util.Arrays.copyOf(f[a], n);
            ans[a] = f[a][n];
            if (predict(p.seq[a], n, a == NUM, a == NUM ? 99 : 4) != ans[a]) return null;
        }
        // Distractors change one attribute of the answer, preferring attributes that actually vary.
        int[] vary = new int[3];
        int nv = 0;
        for (int a = 0; a < 3; a++) {
            if (p.seq[a] == null) continue;
            for (int i = 1; i < n; i++) if (p.seq[a][i] != p.seq[a][0]) { vary[nv++] = a; break; }
        }
        if (nv == 0) return null;
        int[][] o = new int[4][];
        o[0] = ans;
        int made = 1, start = r.nextInt(nv);
        for (int k = 0; k < 3; k++) {
            int[] cand = null;
            for (int tries = 0; tries < 60 && cand == null; tries++) {
                int a = vary[(start + k + (tries > 20 ? r.nextInt(nv) : 0)) % nv];
                int[] c = ans.clone();
                c[a] = alternative(r, p.seq[a], a, ans[a], n);
                if (c[a] != ans[a] && !contains(o, made, c)) cand = c;
            }
            for (int tries = 0; tries < 60 && cand == null; tries++) {
                int[] c = ans.clone();
                int a = r.nextInt(3);
                if (p.seq[a] == null) continue;
                c[a] = alternative(r, p.seq[a], a, ans[a], n);
                if (c[a] != ans[a] && !contains(o, made, c)) cand = c;
            }
            if (cand == null) return null;
            o[made++] = cand;
        }
        for (int i = 3; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int[] t = o[i];
            o[i] = o[j];
            o[j] = t;
        }
        for (int i = 0; i < 4; i++) {
            p.opt[i] = o[i];
            if (o[i] == ans) p.correct = i;
        }
        return p;
    }

    private static boolean contains(int[][] o, int count, int[] c) {
        for (int i = 0; i < count; i++) {
            if (o[i][0] == c[0] && o[i][1] == c[1] && o[i][2] == c[2]) return true;
        }
        return false;
    }

    /** A plausible wrong value for one attribute: something seen in the sequence, or a near miss. */
    private static int alternative(Random r, int[] seq, int attr, int right, int n) {
        if (attr != NUM) {
            if (r.nextInt(3) > 0) {
                int v = seq[r.nextInt(n)];
                if (v != right) return v;
            }
            int v = r.nextInt(5);
            return v == right ? (v + 1) % 5 : v;
        }
        int d = Math.abs(seq[n - 1] - seq[n - 2]);
        int[] near = {right + 1, right - 1, right + d, right - d, right * 2, right / 2, seq[n - 1], right + 10, right - 10,
                right + 2, right - 2};
        for (int tries = 0; tries < 12; tries++) {
            int v = near[r.nextInt(near.length)];
            if (v >= 1 && v <= 99 && v != right) return v;
        }
        return right == 1 ? 2 : right - 1;
    }

    // ------------------------------------------------------------------ session

    private Puzzle pz;
    private int q, phase, picked;
    private float diff, time, qt, limit, rev, appear, points, duckT, duckMood;
    private boolean hintUsed, hinted;
    private final boolean[] hid = new boolean[4];
    private final int[] qres = new int[QUESTIONS];
    private int streak;

    // layout, refreshed every draw and lazily before the first touch
    private boolean laid;
    private float mx, my, mw, mh, deskTop, deskBot, optH, deskH;
    private final float[] ox = new float[4], oy = new float[4];

    @Override
    int rounds() {
        return 1;
    }

    @Override
    String title() {
        return "שיעור פיתוח";
    }

    @Override
    String howTo() {
        return "על המסך יש רצף בלוקים שמסתתר בו כלל. מוצאים אותו ובוחרים את הבלוק שממשיך את הרצף. "
                + "הברווז מוריד שתי תשובות, אבל על חצי נקודה.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = difficulty;
        q = 0;
        points = 0;
        streak = 0;
        hintUsed = false;
        duckMood = 0;
        java.util.Arrays.fill(qres, -1);
        newQuestion();
    }

    private void newQuestion() {
        float qd = Ui.clamp(diff * 0.5f + q * 0.14f, 0, 1);
        int tier = Math.min(4, (int) (qd * 5f));
        pz = make(h.rnd(), tier);
        float[] lim = {5.6f, 6.1f, 6.9f, 7.2f, 7.6f};
        limit = lim[tier] - diff * 0.8f;
        qt = 0;
        appear = 0;
        phase = ASK;
        picked = -1;
        hinted = false;
        java.util.Arrays.fill(hid, false);
    }

    @Override
    int update(float dt) {
        time += dt;
        appear += dt;
        if (duckT > 0) duckT -= dt;
        if (phase == ASK) {
            qt += dt;
            if (qt >= limit) resolve(-1);
        } else {
            rev += dt;
            if (rev >= (picked < 0 ? 1.0f : 0.85f)) {
                q++;
                if (q >= QUESTIONS) {
                    return points >= 4.5f ? PERFECT : points >= 2.5f ? OK : MISS;
                }
                newQuestion();
            }
        }
        return RUNNING;
    }

    private void resolve(int pick) {
        phase = REVEAL;
        picked = pick;
        rev = 0;
        boolean right = pick == pz.correct;
        float cx = pick >= 0 ? ox[pick] + 75 : 180, cy = pick >= 0 ? oy[pick] + optH * 0.3f : my + mh * 0.5f;
        if (right) {
            streak++;
            points += hinted ? 0.5f : 1f;
            qres[q] = hinted ? 1 : 2;
            h.text(cx, cy, streak >= 3 ? "רצף " + streak + "!" : hinted ? "נכון, עם עזרה" : "נכון!", hinted ? Ui.ORANGE : Ui.GOOD);
            h.burst(cx, cy, hinted ? Ui.ORANGE : Ui.ACCENT, hinted ? 6 : 12);
            h.sfx(streak >= 3 ? Sfx.PERFECT : Sfx.DING);
            duckT = 0.7f;
            duckMood = 1;
        } else {
            streak = 0;
            qres[q] = 0;
            h.text(cx, cy, pick < 0 ? "נגמר הזמן" : "לא זה", Ui.BAD);
            h.shake(5);
            h.sfx(Sfx.BAD);
            duckT = 0.9f;
            duckMood = 3;
        }
    }

    @Override
    void down(float x, float y) {
        if (!laid) lay(MiniLabDefaults.top(), MiniLabDefaults.bottom(h.screenH()));
        if (phase != ASK) return;
        if (!hintUsed && Ui.in(x, y, 284, deskTop - 4, 72, deskBot - deskTop + 8)) {
            hintUsed = true;
            hinted = true;
            int removed = 0;
            for (int guard = 0; guard < 40 && removed < 2; guard++) {
                int k = h.rnd().nextInt(4);
                if (k != pz.correct && !hid[k]) {
                    hid[k] = true;
                    removed++;
                }
            }
            h.text(314, deskTop - 6, "קוואק!", Ui.ACCENT);
            h.burst(314, deskTop + 10, Ui.ACCENT, 6);
            h.sfx(Sfx.BEEP);
            duckT = 0.6f;
            duckMood = 1;
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (!hid[i] && Ui.in(x, y, ox[i], oy[i], 150, optH)) {
                resolve(i);
                return;
            }
        }
    }

    /** What the lab's host layout looks like, for touches that arrive before the first draw. */
    private static final class MiniLabDefaults {
        static float top() {
            return Game.HUD_H + 74;
        }

        static float bottom(float screenH) {
            return screenH - 96;
        }
    }

    private void lay(float top, float bottom) {
        laid = true;
        float hh = bottom - top;
        optH = Ui.clamp(hh * 0.19f, 54, 90);
        float r2 = bottom - 4 - optH, r1 = r2 - 8 - optH;
        for (int i = 0; i < 4; i++) {
            ox[i] = i % 2 == 0 ? 188 : 22;
            oy[i] = i < 2 ? r1 : r2;
        }
        float avail = r1 - 8 - (top + 2), stand = 8;
        deskH = Ui.clamp(avail * 0.26f, 38, 70);
        mh = avail - deskH - stand;
        if (mh > 200) {
            deskH += mh - 200;
            mh = 200;
        }
        mx = 14;
        mw = 332;
        my = top + 2;
        deskTop = my + mh + stand - 2;
        deskBot = r1 - 6;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void draw(Gfx c, float top, float bottom) {
        lay(top, bottom);
        monitor(c, mx, my, mw, mh, time);
        stand(c, mx + mw / 2, my + mh, 8);
        float sx = mx + 8, sy = my + 8, sw = mw - 16, sh = mh - 16;

        String cap = "מה בא אחרי?";
        int capCol = Ui.DIM;
        if (phase == REVEAL) {
            boolean right = picked == pz.correct;
            cap = right ? "נכון!" : picked < 0 ? "נגמר הזמן" : "זה הבלוק הנכון";
            capCol = right ? Ui.GOOD : Ui.BAD;
        }
        codeBackdrop(c, sx, sy + 22, sw, sh - 36, time);
        c.text(cap, sx + sw / 2, sy + 15, 13, capCol, Gfx.CENTER);

        int n = pz.n;
        float t = Math.min(Math.min(46f, (sw - 12 - 4 * n) / (n + 1)), sh - 40);
        float gap = (sw - 12 - (n + 1) * t) / n;
        float cy = sy + sh * 0.5f + 5;
        for (int k = 0; k <= n; k++) {
            float cx = sx + sw - 6 - t / 2 - k * (t + gap);
            float pop = ease(Ui.clamp(appear * 5f - k * 0.35f, 0, 1));
            if (k < n) {
                drawBlock(c, cx, cy, t * pop, blockAt(k), TILE, TILE_EDGE, 1.5f);
            } else if (phase == REVEAL) {
                float f = ease(Ui.clamp(rev * 6f, 0, 1));
                drawBlock(c, cx, cy, t * f, pz.opt[pz.correct], TILE, picked == pz.correct ? Ui.GOOD : Ui.BAD, 2.5f);
            } else {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 5);
                c.fillRoundRect(cx - t / 2, cy - t / 2, t, t, t * 0.2f, 0xFF161C25);
                c.strokeRoundRect(cx - t / 2, cy - t / 2, t, t, t * 0.2f, 2f, argb(60 + (int) (140 * pulse), Ui.ACCENT));
                c.text("?", cx, cy + t * 0.18f, t * 0.55f, Ui.ACCENT, Gfx.CENTER);
            }
        }
        // The compile bar: the time left on this question.
        float frac = phase == ASK ? 1 - qt / limit : 0;
        int barCol = frac > 0.5f ? Ui.GOOD : frac > 0.25f ? Ui.ACCENT : Ui.BAD;
        if (phase == ASK) Ui.bar(c, sx + 6, sy + sh - 9, sw - 12, 5, frac, barCol);

        // desk
        desk(c, deskTop, deskBot);
        float base = deskBot - 6;
        mug(c, 40, base, time);
        for (int i = 0; i < QUESTIONS; i++) {
            float lx = 180 + (i - 2) * 26, ly = deskTop + (deskBot - deskTop) * 0.5f;
            int col = qres[i] == 2 ? Ui.ACCENT : qres[i] == 1 ? Ui.ORANGE : qres[i] == 0 ? Ui.BAD : 0xFF39424F;
            boolean now = i == q && phase == ASK;
            if (now) col = argb(110 + (int) (120 * (0.5f + 0.5f * Math.sin(time * 6))), Ui.GOOD);
            c.fillCircle(lx, ly, 8, 0xFF1A1F27);
            c.fillCircle(lx, ly, 6, col);
            if (qres[i] >= 0) c.fillCircle(lx - 1.8f, ly - 1.8f, 1.8f, 0x66FFFFFF);
        }
        float bob = duckT > 0 ? (float) Math.abs(Math.sin(duckT * 14)) * 4 : 0;
        duck(c, 314, base - bob, 1f, time, hintUsed ? 2 : (int) duckMood * (duckT > 0 ? 1 : 0), -1);
        if (!hintUsed) {
            c.text("רמז", 270, base - 10, 12, Ui.ACCENT, Gfx.CENTER);
            c.text("ברווז", 270, base + 3, 12, Ui.DIM, Gfx.CENTER);
        }

        // options
        for (int i = 0; i < 4; i++) {
            float x = ox[i], y = oy[i];
            float f = ease(Ui.clamp(appear * 4f - 0.5f - i * 0.2f, 0, 1));
            int edge = TILE_EDGE;
            if (phase == REVEAL) {
                if (i == pz.correct) edge = Ui.GOOD;
                else if (i == picked) edge = Ui.BAD;
            }
            c.fillRoundRect(x, y, 150, optH, 12, hid[i] ? 0xFF171C24 : Ui.PANEL);
            c.strokeRoundRect(x, y, 150, optH, 12, edge == TILE_EDGE ? 1.5f : 3f, hid[i] ? 0xFF222A35 : edge == TILE_EDGE ? Ui.PANEL2 : edge);
            if (hid[i]) {
                c.line(x + 62, y + optH / 2, x + 88, y + optH / 2, 3, 0xFF2C3644);
                continue;
            }
            float s = Math.min(optH - 12, 56) * f;
            drawBlock(c, x + 75, y + optH / 2, s, pz.opt[i], TILE, TILE_EDGE, 1.5f);
        }
    }

    private int[] bufBlock = new int[3];

    private int[] blockAt(int k) {
        bufBlock[0] = pz.seq[SHAPE][k];
        bufBlock[1] = pz.seq[COL][k];
        bufBlock[2] = pz.seq[NUM] == null ? -1 : pz.seq[NUM][k];
        return bufBlock;
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        monitor(c, cx - 106, cy - 52, 212, 104, t);
        stand(c, cx, cy + 52, 8);
        codeBackdrop(c, cx - 98, cy - 40, 196, 76, t);
        int[][] b = {{0, 0, -1}, {1, 1, -1}, {0, 0, -1}, {1, 1, -1}};
        for (int i = 0; i < 4; i++) {
            drawBlock(c, cx + 76 - i * 38, cy - 4, 32, b[i], TILE, TILE_EDGE, 1.5f);
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 5);
        c.fillRoundRect(cx - 92, cy - 20, 32, 32, 6, 0xFF161C25);
        c.strokeRoundRect(cx - 92, cy - 20, 32, 32, 6, 2f, argb(80 + (int) (150 * pulse), Ui.ACCENT));
        c.text("?", cx - 76, cy + 4, 18, Ui.ACCENT, Gfx.CENTER);
        duck(c, cx + 134, cy + 60, 1.1f, t, 0, -1);
        mug(c, cx - 134, cy + 60, t);
    }

    // ------------------------------------------------------------------ shared drawing helpers

    static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    static float ease(float t) {
        if (t >= 1) return 1;
        float c1 = 1.70158f, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    static float[] shapePoly(int shape, float cx, float cy, float r) {
        switch (shape) {
            case 1:
                return new float[]{cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f,
                        cx - r * 0.9f, cy + r * 0.9f};
            case 2:
                return new float[]{cx, cy - r * 1.15f, cx + r * 1.1f, cy + r * 0.58f, cx - r * 1.1f, cy + r * 0.58f};
            case 3:
                return new float[]{cx, cy - r * 1.18f, cx + r * 1.02f, cy, cx, cy + r * 1.18f, cx - r * 1.02f, cy};
            case 4: {
                float[] p = new float[12];
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 6 + i * Math.PI / 3;
                    p[i * 2] = cx + (float) Math.cos(a) * r * 1.08f;
                    p[i * 2 + 1] = cy + (float) Math.sin(a) * r * 1.08f;
                }
                return p;
            }
            default: {
                float[] p = new float[24];
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    p[i * 2] = cx + (float) Math.cos(a) * r;
                    p[i * 2 + 1] = cy + (float) Math.sin(a) * r;
                }
                return p;
            }
        }
    }

    static void fillShape(Gfx c, int shape, float cx, float cy, float r, int argb) {
        if (shape == 0) c.fillCircle(cx, cy, r, argb);
        else c.fillPoly(shapePoly(shape, cx, cy, r), argb);
    }

    /** A hollow shape: white-ish rim around the tile colour. */
    static void outlineShape(Gfx c, int shape, float cx, float cy, float r, int rim, int hole) {
        fillShape(c, shape, cx, cy, r, rim);
        fillShape(c, shape, cx, cy, r * 0.62f, hole);
    }

    /** One block: a tile with a coloured shape and, when num >= 0, a number under it. b = {shape, colour, num}. */
    static void drawBlock(Gfx c, float cx, float cy, float s, int[] b, int bg, int edge, float edgeW) {
        if (s < 2) return;
        c.fillRoundRect(cx - s / 2, cy - s / 2, s, s, s * 0.2f, bg);
        c.strokeRoundRect(cx - s / 2, cy - s / 2, s, s, s * 0.2f, edgeW, edge);
        int col = PAL[Math.max(0, Math.min(4, b[1]))];
        if (b[2] >= 0) {
            fillShape(c, b[0], cx, cy - s * 0.13f, s * 0.24f, col);
            c.text(String.valueOf(b[2]), cx, cy + s * 0.37f, Math.max(12, s * 0.3f), Ui.INK, Gfx.CENTER);
        } else {
            fillShape(c, b[0], cx, cy, s * 0.3f, col);
            fillShape(c, b[0], cx - s * 0.07f, cy - s * 0.08f, s * 0.1f, 0x30FFFFFF);
        }
    }

    static void monitor(Gfx c, float x, float y, float w, float h, float t) {
        c.fillRoundRect(x + 2, y + 4, w, h, 12, 0x55000000);
        c.fillRoundRect(x, y, w, h, 12, 0xFF2B3440);
        c.strokeRoundRect(x, y, w, h, 12, 1.5f, 0xFF3F4B5B);
        c.fillRoundRect(x + 8, y + 8, w - 16, h - 16, 7, 0xFF0C1117);
        c.fillRect(x + 12, y + 9, w - 24, 1.5f, 0x14FFFFFF);
        float breathe = 0.5f + 0.5f * (float) Math.sin(t * 2);
        c.fillCircle(x + w - 14, y + h - 4, 1.9f, argb(120 + (int) (120 * breathe), Ui.GOOD));
    }

    /** Monitor neck and foot, hanging from the monitor's bottom edge at (cx, y). */
    static void stand(Gfx c, float cx, float y, float len) {
        c.fillPoly(new float[]{cx - 9, y - 2, cx + 9, y - 2, cx + 14, y + len, cx - 14, y + len}, 0xFF222A34);
        c.fillRoundRect(cx - 38, y + len - 3, 76, 6, 3, 0xFF2B3440);
    }

    /** Faint made-up "code": rows of bars of different lengths, since there is no text to show. */
    static void codeBackdrop(Gfx c, float x, float y, float w, float hgt, float t) {
        int rows = (int) (hgt / 9);
        int[] cols = {0xFF5AA9FF, 0xFF5BD68A, 0xFFFFC857, 0xFF9B6BDF};
        for (int i = 0; i < rows; i++) {
            float indent = ((i * 7 + 3) % 4) * 14;
            float len = 30 + ((i * 53 + 17) % 100) * 1.6f;
            c.fillRoundRect(x + w - 8 - indent - len, y + i * 9 + 2, len, 4, 2, argb(20, cols[(i * 5) % 4]));
        }
        if (((int) (t * 1.6f)) % 2 == 0) c.fillRect(x + w - 10, y + hgt - 6, 5, 7, 0x55FFFFFF);
    }

    static void desk(Gfx c, float y0, float y1) {
        c.fillRect(0, y0, 360, y1 - y0, WOOD);
        for (int i = 0; i < 6; i++) c.fillRect(i * 64 - 10, y0 + 6 + (i % 3) * 9, 40, 1.2f, 0x14000000);
        c.fillRect(0, y0, 360, 2.5f, WOOD_HI);
        c.fillRect(0, y1 - 3, 360, 3, WOOD_LO);
    }

    /** Coffee mug standing at (x, base), with steam. */
    static void mug(Gfx c, float x, float base, float t) {
        c.fillCircle(x + 11, base - 9, 6, 0xFFD9D2C3);
        c.fillCircle(x + 11, base - 9, 3.2f, WOOD);
        c.fillRoundRect(x - 10, base - 20, 21, 20, 4, 0xFFEDE7DA);
        c.fillRect(x - 10, base - 13, 21, 5, 0xFFE5484D);
        c.fillRect(x - 10, base - 20, 21, 3, 0xFF3B2A1E);
        for (int k = 0; k < 2; k++) {
            float ph = t * 2.2f + k * 1.7f;
            float x0 = x - 4 + k * 8;
            for (int s = 0; s < 3; s++) {
                float a = (float) Math.sin(ph + s * 0.9) * 2.2f, b = (float) Math.sin(ph + (s + 1) * 0.9) * 2.2f;
                c.line(x0 + a, base - 24 - s * 5, x0 + b, base - 29 - s * 5, 1.6f, argb(90 - s * 22, 0xFFFFFF));
            }
        }
    }

    /**
     * The rubber duck, standing at (x, base) and looking in direction dir (1 right, -1 left). mood: 0 idle,
     * 1 pleased (wide eye), 2 asleep, 3 looking away.
     */
    static void duck(Gfx c, float x, float base, float s, float t, int mood, int dir) {
        int yellow = 0xFFFFD23F, shade = 0xFFE8B21C, orange = 0xFFFF8A2B;
        c.fillCircle(x, base - 9 * s, 11.5f * s, yellow);
        c.fillPoly(new float[]{x - dir * 8 * s, base - 14 * s, x - dir * 17 * s, base - 21 * s, x - dir * 12 * s, base - 8 * s}, yellow);
        c.fillCircle(x - dir * 2 * s, base - 8 * s, 6 * s, shade);
        float hx = x + dir * 6 * s, hy = base - 22 * s;
        c.fillCircle(hx, hy, 8 * s, yellow);
        float lookX = mood == 3 ? -dir * 1.8f * s : dir * 0.8f * s;
        c.fillPoly(new float[]{hx + dir * 6 * s, hy - 1.5f * s, hx + dir * 14 * s, hy + 1.2f * s, hx + dir * 6 * s, hy + 3.2f * s}, orange);
        boolean blink = ((int) (t * 1.3f)) % 5 == 0 && (t * 1.3f) % 1 < 0.12f;
        if (mood == 2 || blink) {
            c.line(hx + dir * 1.5f * s - 2 * s, hy - 2 * s, hx + dir * 1.5f * s + 2 * s, hy - 2 * s, 1.4f * s, 0xFF3B2A1E);
        } else {
            c.fillCircle(hx + dir * 2.5f * s + lookX, hy - 2.2f * s, (mood == 1 ? 2.2f : 1.6f) * s, 0xFF1B1410);
            if (mood == 1) c.fillCircle(hx + dir * 2.5f * s + lookX + 0.6f, hy - 2.8f * s, 0.7f * s, 0xFFFFFFFF);
        }
        if (mood == 2) {
            for (int k = 0; k < 3; k++) {
                float u = (t * 0.8f + k * 0.33f) % 1f;
                c.fillCircle(hx - dir * (6 + u * 6) * s, hy - (10 + u * 14) * s, (1f + u) * s, argb((int) (200 * (1 - u)), Ui.DIM));
            }
        }
    }

    /** The build light: a lamp on a post, glowing in {@code col} when on. */
    static void buildLight(Gfx c, float x, float base, int col, boolean on, float t) {
        c.fillRoundRect(x - 10, base - 5, 20, 5, 2, 0xFF1A1F27);
        c.fillRect(x - 2, base - 22, 4, 18, 0xFF2B3440);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 5);
        if (on) {
            c.fillCircle(x, base - 31, 22 + 3 * pulse, argb(28, col));
            c.fillCircle(x, base - 31, 15, argb(46, col));
        }
        c.fillRoundRect(x - 9, base - 42, 18, 21, 7, on ? col : Ui.darken(col, 0.35f));
        c.fillRoundRect(x - 6, base - 39, 5, 12, 2.5f, on ? 0x66FFFFFF : 0x22FFFFFF);
        c.fillRect(x - 10, base - 44, 20, 3, 0xFF1A1F27);
    }

    // ------------------------------------------------------------------ test bot

    private int botQ = -1;
    private float botAt, botHintAt;
    private boolean botActed, botHintDone, botWantsHint;

    /**
     * Honest bot: it reads the visible blocks only. Each attribute is examined with a chance that
     * grows with skill; an examined attribute is solved with {@link #predict}, which filters the
     * options. Attributes the bot skipped leave its choice to luck.
     */
    @Override
    void bot(Random r, float skill) {
        if (phase != ASK || pz == null) return;
        if (!laid) lay(MiniLabDefaults.top(), MiniLabDefaults.bottom(h.screenH()));
        if (botQ != q) {
            botQ = q;
            botActed = false;
            botHintDone = false;
            botAt = 2.9f + (1 - skill) * 2.4f + pz.tier * 0.5f + r.nextFloat() * 0.8f;
            botWantsHint = !hintUsed && pz.tier >= 2 && r.nextFloat() < 0.04f + 1.4f * skill * (1 - skill);
            botHintAt = botAt * 0.45f;
        }
        if (botWantsHint && !botHintDone && qt >= botHintAt) {
            botHintDone = true;
            down(314, deskTop + 10);
        }
        if (botActed || qt < botAt) return;
        botActed = true;
        int[] want = {-1, -1, -1};
        for (int a = 0; a < 3; a++) {
            if (pz.seq[a] == null) continue;
            float look = 0.3f + 0.85f * skill;
            if (a == NUM) look *= 0.5f + 0.5f * skill;
            if (r.nextFloat() < Math.min(0.98f, look)) {
                want[a] = predict(pz.seq[a], pz.n, a == NUM, a == NUM ? 99 : 4);
            }
        }
        int[] cand = new int[4];
        int cn = 0;
        for (int i = 0; i < 4; i++) {
            if (hid[i]) continue;
            boolean fits = true;
            for (int a = 0; a < 3; a++) if (want[a] >= 0 && pz.opt[i][a] != want[a]) fits = false;
            if (fits) cand[cn++] = i;
        }
        if (cn == 0) {
            for (int i = 0; i < 4; i++) if (!hid[i]) cand[cn++] = i;
        }
        int pick = cand[r.nextInt(cn)];
        down(ox[pick] + 75, oy[pick] + optH / 2);
    }
}
