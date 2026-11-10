package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Baker shift: a rhythm game. Five in the morning, the oven is lit, and the dough wants to be
 * worked to a steady beat. Dough balls travel along a belt towards the hit line; tap anywhere when
 * a ball meets the line. Four phrases, each a bar-based pattern of quarters, eighths and rests,
 * escalating from plain quarter notes to syncopation at a faster tempo.
 *
 * Timing is judged on accumulated time (never frame counts). A tap is seen at a frame boundary, so
 * its real moment is estimated half a frame earlier and the windows are widened by the same half
 * frame. PERFECT is within 60 ms, OK within 130 ms of the beat.
 *
 * The decision: some phrases carry a golden FLOURISH, a fold held across two beats (press on the
 * first, release on the last). It adds up to 4 points on top of the phrase, but a fold that is
 * pressed and then released wrongly tears the dough and costs a point. Skipping it is free.
 * Stray taps (rests, between notes) cost too, so mashing the screen scores nothing.
 */
final class BakerMini extends Mini {
    // ---- the music --------------------------------------------------------------------------
    /** Phrases in eighth-note slots, 8 per bar. x = tap, F = flourish press, f = its release, . = rest. */
    private static final String[] PHRASE = {
            "x.x.x.x.|x.x...x.",
            "x.x.xxx.|x.xx.xx.|x.F...f.",
            "x..x..x.|x.x..x.x|F...f.xx",
            "x.xx.xx.|.x.xx.x.|x..x.xx.|F...f.xx",
    };
    private static final float[] BPM = {88, 100, 112, 124};
    /** One bar of count-in before every phrase. */
    private static final int COUNT_IN = 4;
    private static final float PERFECT_S = 0.060f, OK_S = 0.130f;
    /** Pixels per quarter beat on the belt (the same at every tempo, so the picture reads alike). */
    private static final float PXB = 78, HIT_X = 78;
    /** Space the host reserves under the play area; used only to lay out before the first draw. */
    private static final float FOOT = 96;
    private static final float STRAY_COST = 0.6f, PERFECT_AT = 0.80f, OK_AT = 0.40f;

    // ---- chart of the current round -----------------------------------------------------------
    private int round, n;
    private float spb, diff;
    private final float[] nb = new float[24], nt = new float[24], endT = new float[24];
    private final boolean[] fl = new boolean[24], strong = new boolean[24];
    /** Tap notes: 0 pending, 1 hit, 2 missed. Flourish: 0 pending, 1 held, 2 finished, 3 skipped. */
    private final int[] st = new int[24];
    private final float[] pts = new float[24];
    private int taps;

    // ---- play state -------------------------------------------------------------------------------
    private float time, lastDt = 1f / 30f, pulse, tail;
    private int beatIdx;
    private float sum, strays, strayCd;
    private int holding;
    private float holdFrom;
    private int goodHits, perfects;
    private boolean decided;

    // ---- look ---------------------------------------------------------------------------------------
    private float sq, sqv, fold, bake, glowKick, loafPop, shotKick;
    private final float[] press = new float[2];
    private final float[] dent = new float[2];
    private int side;
    private float wallTop, wallH, laneTop, laneH = 56, tableTop, baseY, scale = 1, meterY, lastShown;
    private final float[] px = new float[44], py = new float[44], pvx = new float[44], pvy = new float[44],
            pl = new float[44], ps = new float[44];
    private int pc, shownLoaves;
    private final float[] dome = new float[46];

    // ---- bot ------------------------------------------------------------------------------------------
    private boolean botReady;
    private final float[] bt = new float[24], br = new float[24];
    private final boolean[] bSkip = new boolean[24], bFlour = new boolean[24], bDone = new boolean[24];
    private int botHold = -1;

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return "אופה";
    }

    @Override
    String howTo() {
        return "לוחצים על הבצק כשהכדור מגיע לקו, בדיוק בקצב. במנוחה לא לוחצים! "
                + "קישוט זהב: מחזיקים שתי פעימות ומשחררים בזמן. שווה יותר, ואפשר לקרוע.";
    }

    @Override
    void begin(int r, float difficulty) {
        round = Math.max(0, Math.min(3, r));
        diff = Ui.clamp(difficulty, 0, 1);
        float bpm = BPM[round] * (0.95f + 0.2f * diff);
        spb = 60f / bpm;
        parse(PHRASE[round]);
        time = 0;
        beatIdx = -1;
        pulse = 0;
        tail = 0;
        sum = strays = strayCd = 0;
        holding = -1;
        goodHits = perfects = 0;
        decided = false;
        sq = sqv = fold = bake = glowKick = loafPop = shotKick = 0;
        press[0] = press[1] = dent[0] = dent[1] = 0;
        side = 0;
        shownLoaves = 0;
        for (int i = 0; i < px.length; i++) pl[i] = 0;
        botReady = false;
        botHold = -1;
        layout(Game.HUD_H + 74, h.screenH() - FOOT);
    }

    private void parse(String p) {
        n = 0;
        taps = 0;
        int slot = 0;
        int open = -1;
        for (int i = 0; i < p.length() && n < nb.length; i++) {
            char ch = p.charAt(i);
            if (ch == '|') continue;
            float beat = COUNT_IN + slot * 0.5f;
            if (ch == 'x' || ch == 'F') {
                nb[n] = beat;
                nt[n] = beat * spb;
                fl[n] = ch == 'F';
                strong[n] = slot % 8 == 0;
                st[n] = 0;
                pts[n] = 0;
                endT[n] = nt[n];
                if (ch == 'F') open = n;
                else taps++;
                n++;
            } else if (ch == 'f' && open >= 0) {
                endT[open] = beat * spb;
                open = -1;
            }
            slot++;
        }
        // A flourish is worth more than a plain note, but it is never part of the baseline.
    }

    // ---- logic ------------------------------------------------------------------------------------------

    @Override
    int update(float dt) {
        dt = Ui.clamp(dt, 0.001f, 0.1f);
        lastDt = Ui.clamp(dt, 0.008f, 0.06f);
        time += dt;
        int b = (int) Math.floor(time / spb);
        while (beatIdx < b) {
            beatIdx++;
            pulse = 1;
            h.sfx(Sfx.TICK);
        }
        pulse = Math.max(0, pulse - dt * 4.2f);
        glowKick = Math.max(0, glowKick - dt * 2.2f);
        loafPop = Math.max(0, loafPop - dt * 3f);
        strayCd = Math.max(0, strayCd - dt);
        press[0] = Math.max(0, press[0] - dt * 6.5f);
        press[1] = Math.max(0, press[1] - dt * 6.5f);
        dent[0] = Math.max(0, dent[0] - dt * 0.9f);
        dent[1] = Math.max(0, dent[1] - dt * 0.9f);
        // Dough as a damped spring: a hit squashes it, it springs back and wobbles.
        sqv += (-150 * sq - 9 * sqv) * dt;
        sq += sqv * dt;
        float foldGoal = holding >= 0 ? 1 : 0;
        fold += (foldGoal - fold) * Math.min(1, dt * 12);
        for (int i = 0; i < px.length; i++) {
            if (pl[i] <= 0) continue;
            pl[i] -= dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            pvx[i] *= 1 - 1.5f * dt;
            pvy[i] -= 30 * dt;
        }

        float okW = OK_S + lastDt * 0.5f;
        boolean open = false;
        for (int i = 0; i < n; i++) {
            if (fl[i]) {
                if (st[i] == 0 && time > nt[i] + okW) st[i] = 3;
                else if (st[i] == 1 && time > endT[i] + okW) tear(i);
            } else if (st[i] == 0 && time > nt[i] + okW) {
                st[i] = 2;
                h.text(tx(), fy(), "החמצה", 0xFFFF9A8A);
            }
            if (unresolved(i)) open = true;
        }
        if (open) return RUNNING;
        tail += dt;
        bake = Math.min(1, tail / 0.7f);
        if (tail < 0.75f) return RUNNING;
        float ratio = ratio();
        return ratio >= PERFECT_AT ? PERFECT : ratio >= OK_AT ? OK : MISS;
    }

    /** A tap note is settled once hit or missed; a flourish also while it is being held. */
    private boolean unresolved(int i) {
        return st[i] == 0 || (fl[i] && st[i] == 1);
    }

    private float ratio() {
        float m = 2f * Math.max(1, taps);
        return Ui.clamp((sum - STRAY_COST * strays) / m, 0, 1);
    }

    /** Estimated real moment of the tap that is being delivered now. */
    private float tapTime() {
        return time - lastDt * 0.5f;
    }

    @Override
    void down(float x, float y) {
        if (decided || holding >= 0) return;
        float t = tapTime();
        float okW = OK_S + lastDt * 0.5f, perfW = PERFECT_S + lastDt * 0.5f;
        int best = -1;
        float bd = 1e9f;
        for (int i = 0; i < n; i++) {
            float d = Math.abs(t - nt[i]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        boolean allDone = true;
        for (int i = 0; i < n; i++) if (unresolved(i)) allDone = false;
        if (best < 0 || allDone || t < nt[0] - 0.4f) {
            // Count-in and the tail are free: players feel the groove before the first note.
            patDough(0.12f, false);
            return;
        }
        if (bd > okW || st[best] != 0) {
            stray();
            return;
        }
        float d = t - nt[best];
        int grade = bd <= perfW ? 2 : 1;
        if (fl[best]) {
            st[best] = 1;
            holding = best;
            holdFrom = t;
            pts[best] = grade;
            h.sfx(Sfx.WHOOSH);
            patDough(0.3f, false);
            h.text(tx(), fy(), grade == 2 ? "תופסים!" : "מחזיקים…", Ui.ACCENT);
            return;
        }
        st[best] = 1;
        pts[best] = grade;
        sum += grade;
        goodHits++;
        if (grade == 2) perfects++;
        loafPop = 1;
        patDough(strong[best] ? 0.7f : grade == 2 ? 0.5f : 0.36f, strong[best]);
        glowKick = Math.min(1, glowKick + (grade == 2 ? 0.7f : 0.3f));
        h.sfx(grade == 2 ? Sfx.GOOD : Sfx.TAP);
        if (grade == 2) {
            h.text(tx(), fy(), "מושלם!", Ui.ACCENT);
            h.burst(dx(), baseY - 30 * scale, 0xFFFFE9A8, 8);
        } else {
            h.text(tx(), fy(), d < 0 ? "מוקדם" : "מאוחר", Ui.ORANGE);
            h.burst(dx(), baseY - 30 * scale, 0xFFF4EDE0, 4);
        }
    }

    @Override
    void up(float x, float y) {
        if (holding < 0) return;
        int i = holding;
        holding = -1;
        float t = tapTime();
        float okW = OK_S + lastDt * 0.5f, perfW = PERFECT_S + lastDt * 0.5f;
        float ad = Math.abs(t - endT[i]);
        if (ad <= okW) {
            int g = ad <= perfW ? 2 : 1;
            float total = pts[i] + g;
            sum += total;
            st[i] = 2;
            goodHits++;
            if (g == 2 && pts[i] == 2) perfects++;
            loafPop = 1;
            sqv = 0;
            sq = 0.8f;
            press[0] = press[1] = 1;
            dent[0] = dent[1] = 1;
            glowKick = 1;
            spawn(dx(), baseY - 40 * scale, 14, 1.6f);
            h.sfx(g == 2 ? Sfx.PERFECT : Sfx.GOOD);
            h.text(tx(), fy(), g == 2 && pts[i] == 2 ? "קיפול מושלם!" : "קיפול!", Ui.ACCENT);
            h.burst(dx(), baseY - 40 * scale, Ui.ACCENT, 14);
        } else {
            tear(i);
        }
    }

    private void tear(int i) {
        st[i] = 2;
        holding = -1;
        sum -= 1;
        sq = -0.5f;
        sqv = 0;
        h.shake(5);
        h.sfx(Sfx.BAD);
        h.text(tx(), fy(), "הבצק נקרע", Ui.BAD);
    }

    private void stray() {
        strays++;
        patDough(0.18f, false);
        if (strayCd <= 0) {
            strayCd = 0.5f;
            h.text(tx(), fy(), "לא בקצב", Ui.BAD);
            h.shake(2);
        }
    }

    /** The dough reacts to every touch, good or bad: squash, a hand lands, flour flies. */
    private void patDough(float amount, boolean big) {
        sq = Math.max(sq, amount);
        sqv = 0;
        press[side] = 1;
        dent[side] = 1;
        side ^= 1;
        spawn(dx(), baseY - 28 * scale, big ? 10 : 4, big ? 1.5f : 1f);
        if (big) h.shake(3);
    }

    /** Judgement words float in the gap between the hands, just under the belt numbers. */
    private float fy() {
        return tableTop + 24;
    }

    /** Consecutive words alternate left and right so they do not pile up. */
    private float tx() {
        return W / 2 + (((goodHits + (int) strays) & 1) == 0 ? -46 : 46);
    }

    private float dx() {
        return W / 2;
    }

    @Override
    void pause() {
        holding = -1;
        botHold = -1;
    }

    // ---- bot ------------------------------------------------------------------------------------------

    @Override
    void bot(Random r, float skill) {
        if (!botReady) {
            botReady = true;
            botHold = -1;
            float sd = 0.026f + 0.115f * (float) Math.pow(1 - skill, 0.9);
            float missP = (1 - skill) * (1 - skill) * 0.45f + (1 - skill) * 0.08f;
            for (int i = 0; i < n; i++) {
                boolean off = (nb[i] * 2) % 2 != 0;
                float e = (float) r.nextGaussian() * sd * (off ? 1.35f : 1f) - 0.004f;
                bt[i] = nt[i] + e;
                bSkip[i] = r.nextFloat() < missP;
                bDone[i] = false;
                bFlour[i] = fl[i] && r.nextFloat() < skill * skill * 0.95f;
                br[i] = endT[i] + (float) r.nextGaussian() * sd * 1.3f;
                if (fl[i] && !bFlour[i]) bSkip[i] = true;
            }
        }
        // Players without the groove also tap at random, which the rests punish.
        if (r.nextFloat() < (1 - skill) * (1 - skill) * 0.5f * lastDt && botHold < 0) down(180, 300);
        if (botHold >= 0) {
            if (time >= br[botHold]) {
                up(180, 300);
                botHold = -1;
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            if (bDone[i] || bSkip[i]) continue;
            if (time >= bt[i]) {
                bDone[i] = true;
                down(180, 300);
                if (fl[i] && holding == i) botHold = i;
                return;
            }
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------

    private void layout(float top, float bottom) {
        float H = bottom - top;
        wallTop = top;
        wallH = Ui.clamp(H * 0.27f, 86, 140);
        laneTop = top + wallH;
        tableTop = laneTop + laneH + 22;
        meterY = bottom - 20;
        float avail = meterY - 20 - tableTop;
        scale = Ui.clamp(avail / 185f, 0.5f, 1.12f);
        baseY = tableTop + avail * 0.74f + 10;
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        float pos = time / spb;
        drawWall(c, pos);
        drawLane(c, pos);
        drawTable(c);
        drawDough(c, pos);
        drawParticles(c);
        drawMeter(c, bottom);
    }

    private static int mix(int a, int b, float t) {
        t = Ui.clamp(t, 0, 1);
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br2 = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br2 - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    private static int alpha(int col, float a) {
        return ((int) (Ui.clamp(a, 0, 1) * 255) << 24) | (col & 0xFFFFFF);
    }

    private float dawn() {
        float inRound = Ui.clamp(time / (spb * (COUNT_IN + 12)), 0, 1);
        return Ui.clamp((round + inRound) / 4f, 0, 1);
    }

    private void drawWall(Gfx c, float pos) {
        float y0 = wallTop, y1 = laneTop;
        float glow = 0.62f + 0.22f * pulse + 0.3f * glowKick + 0.08f * (float) Math.sin(time * 9);
        c.fillRect(0, y0, W, wallH, 0xFF2A1A18);
        // Bricks.
        for (float y = y0 + 12; y < y1; y += 14) {
            c.fillRect(0, y, W, 1, 0x22000000);
            int row = (int) ((y - y0) / 14);
            for (float x = (row % 2) * 20; x < W; x += 40) c.fillRect(x, y - 13, 1, 13, 0x22000000);
        }
        // Window onto a street that is still dark; dawn creeps in over the four rounds.
        float wx = 16, wy = y0 + 8, ww = 112, wh = wallH - 20;
        c.fillRoundRect(wx - 5, wy - 5, ww + 10, wh + 10, 5, 0xFF4A3426);
        float d = dawn();
        int[] night = {0xFF0A1330, 0xFF14214A, 0xFF22305F, 0xFF2E3A6B};
        int[] morn = {0xFF3F5E9A, 0xFF8C86B0, 0xFFE59A6E, 0xFFFFC77A};
        float sh = wh / 4f;
        for (int i = 0; i < 4; i++) c.fillRect(wx, wy + i * sh, ww, sh + 1, mix(night[i], morn[i], d));
        if (d < 0.6f) {
            for (int i = 0; i < 7; i++) {
                float sx = wx + 8 + noise(i * 3) * (ww - 16), sy = wy + 6 + noise(i * 3 + 1) * wh * 0.45f;
                float tw = 0.5f + 0.5f * (float) Math.sin(time * 3 + i * 2.1f);
                c.fillCircle(sx, sy, 1.1f, alpha(0xFFFFFF, (0.8f - d) * (0.4f + 0.5f * tw)));
            }
        }
        if (d > 0.45f) c.fillCircle(wx + ww * 0.78f, wy + wh - 6 - (d - 0.45f) * 14, 10, alpha(0xFFE8A0, (d - 0.45f) * 1.6f));
        float rb = wy + wh;
        c.fillPoly(new float[]{wx, rb, wx, rb - 12, wx + 18, rb - 12, wx + 18, rb - 22, wx + 40, rb - 22, wx + 40,
                rb - 10, wx + 62, rb - 10, wx + 62, rb - 18, wx + 86, rb - 18, wx + 86, rb - 8, wx + ww, rb - 8,
                wx + ww, rb}, 0xFF0B0F1E);
        c.fillRect(wx + ww / 2 - 1, wy, 2, wh, 0xFF4A3426);
        c.fillRect(wx, wy + wh * 0.45f, ww, 2, 0xFF4A3426);
        // The metronome ball bounces in front of the window and lands on the belt exactly on the beat.
        float frac = pos - (float) Math.floor(pos);
        float bounce = (float) Math.sin(Math.PI * frac);
        float bh = Math.min(wallH - 34, 52);
        float by = y1 - 11 - bounce * bh;
        c.fillCircle(HIT_X, y1 - 3, 9 + 2 * (1 - bounce), 0x44000000);
        c.fillCircle(HIT_X, by, 10 + 2.5f * pulse, 0xFF10141A);
        c.fillCircle(HIT_X, by, 8.5f + 2.5f * pulse, mix(0xFFF4EDE0, 0xFFFFD36B, pulse));

        // Oven: brick dome with a mouth that glows and pulses with the beat.
        float ox = 146, ow = 108, oy = y0 + 6, oh = wallH - 8;
        c.fillRoundRect(ox + ow / 2 - 9, oy - 4, 18, 14, 3, 0xFF3A2A28);
        c.fillRoundRect(ox, oy + 10, ow, oh - 10, 22, 0xFF6A4038);
        c.fillRoundRect(ox + 5, oy + 15, ow - 10, oh - 15, 18, 0xFF7A4B40);
        for (int i = 0; i < 4; i++) c.fillRect(ox + 10, oy + 26 + i * (oh - 40) / 4f, ow - 20, 1, 0x33000000);
        float mx = ox + ow / 2, mw = ow * 0.36f, mh = oh * 0.5f, mb = y1 - 8;
        c.fillCircle(mx, mb - mh * 0.4f, 70 + 30 * glow, alpha(0xFF8A3C, 0.07f * glow));
        c.fillCircle(mx, mb - mh * 0.4f, 46 + 20 * glow, alpha(0xFF8A3C, 0.09f * glow));
        arch(c, mx, mb, mw + 4, mh + 4, 0xFF2B1A16);
        arch(c, mx, mb, mw, mh, mix(0xFF2A0E08, 0xFF7A1C0A, glow));
        arch(c, mx, mb, mw * 0.82f, mh * 0.8f, mix(0xFF4A1408, 0xFFC4400C, glow));
        arch(c, mx, mb, mw * 0.58f, mh * 0.6f, mix(0xFF80280A, 0xFFFF8A1E, glow));
        arch(c, mx, mb, mw * 0.32f, mh * 0.36f, mix(0xFFB0480C, 0xFFFFD25A, glow));
        for (int i = 0; i < 3; i++) {
            float fx = mx + (i - 1) * mw * 0.5f;
            float fh = (0.28f + 0.12f * glow) * mh * (0.8f + 0.25f * (float) Math.sin(time * 11 + i * 2.3f));
            c.fillPoly(new float[]{fx - 7, mb, fx + 7, mb, fx + 2 * (float) Math.sin(time * 8 + i), mb - fh},
                    mix(0xFFFF8A1E, 0xFFFFE27A, glow));
        }
        c.fillRoundRect(mx - mw - 6, mb, 2 * mw + 12, 6, 2, 0xFF2B1A16);
        // Trays of rolls slide in the mouth on hits.
        c.fillRoundRect(mx - 10, y0 + 22, 20, 12, 6, 0xFF2B1A16);
        c.fillCircle(mx, y0 + 28, 4.5f, mix(0xFF554433, 0xFFFFC857, glow));

        // Cooling rack on the right, filling with loaves as hits land.
        float rx = 266, rw = 88;
        int rows = wallH > 110 ? 3 : 2;
        int slots = rows * 3;
        shownLoaves = taps > 0 ? Math.min(slots, (int) Math.floor((float) goodHits / taps * slots + 0.001f)) : 0;
        c.fillRect(rx, y0 + 6, 5, wallH - 8, 0xFF5A3C28);
        c.fillRect(rx + rw - 5, y0 + 6, 5, wallH - 8, 0xFF5A3C28);
        float rowH = (wallH - 14) / rows;
        for (int rr = 0; rr < rows; rr++) {
            float sy = y0 + 8 + (rr + 1) * rowH - 2;
            c.fillRect(rx, sy, rw, 5, 0xFF7A5436);
            for (int k = 0; k < 3; k++) {
                int idx = (rows - 1 - rr) * 3 + k;
                if (idx >= shownLoaves) continue;
                float lx = rx + 17 + k * 27;
                float pop = idx == shownLoaves - 1 ? 1 + 0.35f * loafPop : 1;
                float lw = 11 * pop, lh = 8 * pop;
                c.fillPoly(new float[]{lx - lw, sy, lx - lw * 0.8f, sy - lh * 1.2f, lx, sy - lh * 1.6f, lx + lw * 0.8f,
                        sy - lh * 1.2f, lx + lw, sy}, 0xFFC68A3E);
                c.line(lx - 4, sy - lh * 1.1f, lx - 1, sy - 2, 1.4f, 0xFF8A5A22);
                c.line(lx + 2, sy - lh * 1.2f, lx + 5, sy - 2, 1.4f, 0xFF8A5A22);
            }
        }
        c.fillRect(0, y1 - 1, W, 1, 0xFF120C0A);
    }

    private static void arch(Gfx c, float cx, float base, float rx, float ry, int col) {
        float[] p = new float[2 * 12];
        for (int i = 0; i < 11; i++) {
            double a = Math.PI * i / 10;
            p[2 * i] = cx + (float) Math.cos(a) * rx;
            p[2 * i + 1] = base - (float) Math.pow(Math.sin(a), 0.8) * ry;
        }
        p[22] = cx - rx;
        p[23] = base;
        c.fillPoly(p, col);
        c.fillRect(cx - rx, base - 1, 2 * rx, 2, col);
    }

    private void drawLane(Gfx c, float pos) {
        float y = laneTop, cy = y + laneH / 2;
        c.fillRect(0, y, W, laneH, 0xFF20150F);
        c.fillRect(0, y, W, 3, 0xFF4A3426);
        c.fillRect(0, y + laneH - 3, W, 3, 0xFF4A3426);
        float speed = PXB / spb;
        float okPx = (OK_S + lastDt * 0.5f) * speed, perfPx = (PERFECT_S + lastDt * 0.5f) * speed;
        c.fillRect(HIT_X - okPx, y + 3, 2 * okPx, laneH - 6, 0x26FFC857);
        c.fillRect(HIT_X - perfPx, y + 3, 2 * perfPx, laneH - 6, 0x40FFC857);
        // Beat grid scrolls past: tall lines on beats, small ones on the off-beat eighths.
        int firstBeat = (int) Math.floor(pos - HIT_X / PXB) - 1;
        int lastBeat = (int) Math.ceil(pos + (W - HIT_X) / PXB) + 1;
        for (int b = Math.max(0, firstBeat); b <= Math.min(lastBeat, COUNT_IN + 17); b++) {
            float x = HIT_X + (b - pos) * PXB;
            boolean bar = b % 4 == 0;
            if (x > 6 && x < W - 6) {
                c.fillRect(x - (bar ? 1.5f : 0.75f), y + (bar ? 4 : 10), bar ? 3 : 1.5f, laneH - (bar ? 8 : 20),
                        bar ? 0x66FFFFFF : 0x30FFFFFF);
                c.text(String.valueOf(b % 4 + 1), x, y + laneH + 15, 12, b < COUNT_IN ? 0xFFFFC857 : Ui.DIM, Gfx.CENTER);
            }
            float xe = x + PXB * 0.5f;
            if (xe > 6 && xe < W - 6) c.fillRect(xe - 0.5f, cy - 4, 1, 8, 0x30FFFFFF);
        }
        // Flourish bars first, so plain notes ride on top.
        for (int i = 0; i < n; i++) {
            if (!fl[i]) continue;
            float x0 = HIT_X + (nb[i] - pos) * PXB, x1 = HIT_X + (endT[i] / spb - pos) * PXB;
            if (x1 < -20 || x0 > W + 20) continue;
            boolean live = st[i] == 1;
            boolean dead = st[i] == 3 || (st[i] == 2 && sum < 0);
            int bar = dead ? 0x44887755 : live ? 0xFFFFC857 : 0xAAC9962F;
            float a = Math.max(x0, 10), b = Math.min(x1, W - 10);
            if (b > a) c.fillRoundRect(a, cy - 9, b - a, 18, 9, bar);
            if (live) {
                float lit = Math.min(x1, HIT_X);
                if (lit > Math.max(x0, 10)) c.fillRoundRect(Math.max(x0, 10), cy - 5, lit - Math.max(x0, 10), 10, 5, 0xFFFFF2B8);
            }
            if (x1 > 10 && x1 < W - 10) {
                c.fillCircle(x1, cy, 13, bar);
                c.fillCircle(x1, cy, 6, 0xFFFFFFFF);
            }
            if (st[i] == 0 && x0 < W - 40 && x1 > HIT_X) {
                c.text("החזק", (Math.max(a, 20) + b) / 2, y - 5, 12, 0xFFFFC857, Gfx.CENTER);
            }
        }
        // Approach rings: a ring closes in on each of the next notes, so the beat is anticipated.
        for (int i = 0; i < n; i++) {
            if (st[i] != 0) continue;
            float until = nt[i] - time;
            if (until < 0 || until > spb * 1.1f) continue;
            float x = HIT_X + (nb[i] - pos) * PXB;
            if (x < 12 || x > W - 12) continue;
            float k = until / (spb * 1.1f);
            float rr = 13 + 20 * k;
            c.fillCircle(x, cy, rr, alpha(0xFFE9A8, 0.55f * (1 - k) + 0.1f));
            c.fillCircle(x, cy, rr - 2.2f, 0xFF20150F);
        }
        for (int i = 0; i < n; i++) {
            float x = HIT_X + (nb[i] - pos) * PXB;
            if (x < 8 || x > W - 8 || st[i] == 1 && !fl[i]) continue;
            if (fl[i]) {
                if (st[i] >= 2) continue;
                c.fillCircle(x, cy, 16, 0xFF6A4A12);
                c.fillCircle(x, cy, 13.5f, st[i] == 1 ? 0xFFFFF2B8 : 0xFFFFC857);
                // A tiny fold glyph: two chevrons.
                c.line(x - 6, cy - 3, x, cy + 3, 2.2f, 0xFF5A3A08);
                c.line(x + 6, cy - 3, x, cy + 3, 2.2f, 0xFF5A3A08);
                continue;
            }
            float r = strong[i] ? 14 : 12;
            if (st[i] == 2) {
                c.fillCircle(x, cy, r, 0x33998877);
                c.line(x - 5, cy - 5, x + 5, cy + 5, 2, 0x66FF6B6B);
                c.line(x + 5, cy - 5, x - 5, cy + 5, 2, 0x66FF6B6B);
                continue;
            }
            c.fillCircle(x, cy, r + 2, strong[i] ? 0xFFFFC857 : 0xFF8A6A48);
            c.fillCircle(x, cy, r, 0xFFF3E2BC);
            c.fillCircle(x - 3, cy - 3, r * 0.45f, 0x66FFFFFF);
        }
        // The hit line and its pulse.
        float ring = 21 + 12 * pulse;
        c.fillCircle(HIT_X, cy, ring, alpha(0xFFFFFF, 0.1f + 0.18f * pulse));
        c.fillCircle(HIT_X, cy, 20, 0xFFFFC857);
        c.fillCircle(HIT_X, cy, 17.5f, 0xFF20150F);
        c.fillRect(HIT_X - 1, y + 2, 2, laneH - 4, alpha(0xFFE9A8, 0.4f + 0.5f * pulse));
        // Count-in numerals large on the table.
        if (time < COUNT_IN * spb) {
            int num = Math.min(COUNT_IN, (int) Math.floor(time / spb) + 1);
            c.text(String.valueOf(num), W / 2, tableTop + 46 * scale, 44 + 10 * pulse, alpha(0xFFFFFF, 0.2f + 0.2f * pulse),
                    Gfx.CENTER);
        }
    }

    private void drawTable(Gfx c) {
        c.fillRect(0, tableTop - 6, W, meterY + 20 - (tableTop - 6) + 120, 0xFF5E4029);
        c.fillRect(0, tableTop - 6, W, 4, 0xFF8A6240);
        for (int i = 1; i < 6; i++) c.fillRect(0, tableTop + i * (meterY - tableTop) / 5f, W, 1, 0x22000000);
        // Floured board under the dough.
        float bw = 250 * Math.min(1, scale + 0.15f);
        c.fillRoundRect(W / 2 - bw / 2, baseY - 6, bw, 30 * scale + 6, 8, 0xFFB9A07A);
        c.fillRoundRect(W / 2 - bw / 2 + 4, baseY - 4, bw - 8, 22 * scale + 6, 6, 0xFFD8C8A4);
        for (int i = 0; i < 26; i++) {
            c.fillCircle(W / 2 - bw / 2 + 8 + noise(i * 5) * (bw - 16), baseY - 2 + noise(i * 5 + 2) * 22 * scale, 1.2f,
                    0x66FFFFFF);
        }
        // A rolling pin and a bowl of flour keep the corners from being empty.
        float py = baseY + 22 * scale + 8;
        if (py < meterY - 34) {
            c.fillRoundRect(214, py, 100, 11, 5, 0xFFC9A26A);
            c.fillRoundRect(206, py + 2, 14, 7, 3, 0xFF8A6240);
            c.fillRoundRect(308, py + 2, 14, 7, 3, 0xFF8A6240);
        }
        float bxx = 52, byy = baseY + 4;
        c.fillCircle(bxx, byy + 2, 20 * Math.min(1, scale + 0.2f), 0xFF2C2A30);
        c.fillCircle(bxx, byy - 1, 16 * Math.min(1, scale + 0.2f), 0xFFF2EDE4);
        c.fillCircle(bxx - 4, byy - 3, 5, 0x66FFFFFF);
    }

    private void drawDough(Gfx c, float pos) {
        float s = scale;
        float cx = W / 2;
        float squash = sq, f = fold;
        float rx = 82 * s * (1 + 0.32f * squash - 0.22f * f);
        float ry = 44 * s * (1 - 0.5f * squash + 0.85f * f) + 3 * s * (float) Math.sin(time * 2.2f);
        ry = Math.max(8 * s, ry);
        int col = mix(0xFFF1DDB0, 0xFFD9A25B, bake);
        domePoly(c, cx + 2, baseY + 4, rx + 3, ry, 0x55000000);
        domePoly(c, cx, baseY + 2, rx, ry, mix(col, 0xFF8A6A40, 0.45f));
        domePoly(c, cx, baseY, rx, ry, col);
        domePoly(c, cx - rx * 0.12f, baseY - ry * 0.12f, rx * 0.62f, ry * 0.62f, 0x30FFFFFF);
        for (int i = 0; i < 2; i++) {
            float dxp = (i == 0 ? -1 : 1) * rx * 0.38f;
            if (dent[i] > 0) c.fillCircle(cx + dxp, baseY - ry * 0.55f, 15 * s * (0.6f + 0.4f * dent[i]), alpha(0x8A6A40, 0.4f * dent[i]));
        }
        if (f > 0.3f) c.line(cx - rx * 0.5f, baseY - ry * 0.45f, cx + rx * 0.5f, baseY - ry * 0.45f, 2, alpha(0x8A6A40, f * 0.6f));
        // Hands. Each lands on its own side; during a fold both grab the ends and pull.
        float topY = baseY - ry;
        for (int i = 0; i < 2; i++) {
            float sgn = i == 0 ? -1 : 1;
            float lift = 34 * s * (1 - press[i]) + 3 * s * (float) Math.sin(time * 4 + i * 2);
            float hx = cx + sgn * rx * 0.46f, hy = topY - 8 * s - lift + 6 * s * press[i];
            float gx = cx + sgn * (rx + 6 * s), gy = baseY - ry * 0.7f - 12 * s * f;
            hx += (gx - hx) * f;
            hy += (gy - hy) * f;
            float ax = cx + sgn * 190, ay = tableTop - 10;
            float ang = (float) Math.atan2(hy - ay, hx - ax);
            c.line(ax, ay, hx, hy, 30 * s, 0xFFE0D9CC);
            c.line(ax, ay, hx, hy, 30 * s - 6, 0xFFF4F0E8);
            // Cuff, then a palm with a thumb: the hand reads as a hand, not a ball.
            float cxf = hx - (float) Math.cos(ang) * 20 * s, cyf = hy - (float) Math.sin(ang) * 20 * s;
            c.fillCircle(cxf, cyf, 15 * s, 0xFFD6CEC0);
            c.fillCircle(hx, hy, 19 * s, 0xFFE0AB84);
            c.fillCircle(hx - sgn * 7 * s, hy - 9 * s, 8 * s, 0xFFE9BA95);
            c.fillCircle(hx + sgn * 4 * s, hy + 8 * s, 9 * s, 0xFFD69A74);
        }
    }

    private void domePoly(Gfx c, float cx, float base, float rx, float ry, int col) {
        int k = 20;
        for (int i = 0; i <= k; i++) {
            double a = -0.2 + (Math.PI + 0.4) * i / k;
            double sn = Math.sin(a);
            dome[2 * i] = cx + (float) Math.cos(a) * rx;
            dome[2 * i + 1] = base - (float) (Math.signum(sn) * Math.pow(Math.abs(sn), 0.72)) * ry;
        }
        c.fillPoly(java.util.Arrays.copyOf(dome, 2 * (k + 1)), col);
    }

    private void drawParticles(Gfx c) {
        for (int i = 0; i < px.length; i++) {
            if (pl[i] <= 0) continue;
            float a = Math.min(1, pl[i] / 0.5f);
            c.fillCircle(px[i], py[i], ps[i] * (1.3f - 0.5f * a), alpha(0xF7F1E6, 0.7f * a));
        }
    }

    private void drawMeter(Gfx c, float bottom) {
        float x0 = 40, x1 = 320, y = meterY, hh = 9;
        c.fillRoundRect(x0 - 2, y - 2, x1 - x0 + 4, hh + 4, 6, 0xFF120C0A);
        float r = Ui.clamp((sum - STRAY_COST * strays) / (2f * Math.max(1, taps)), 0, 1);
        int col = r >= PERFECT_AT ? Ui.ACCENT : r >= OK_AT ? Ui.GOOD : Ui.ORANGE;
        if (r > 0.01f) c.fillRoundRect(x0, y, (x1 - x0) * r, hh, 4.5f, col);
        c.fillRect(x0 + (x1 - x0) * OK_AT - 1, y - 3, 2, hh + 6, 0xAAFFFFFF);
        c.fillRect(x0 + (x1 - x0) * PERFECT_AT - 1, y - 3, 2, hh + 6, 0xAAFFC857);
        c.text("טוב", x0 + (x1 - x0) * OK_AT, y - 6, 12, Ui.DIM, Gfx.CENTER);
        c.text("מושלם", x0 + (x1 - x0) * PERFECT_AT, y - 6, 12, Ui.ACCENT, Gfx.CENTER);
    }

    private void spawn(float x, float y, int count, float power) {
        for (int k = 0; k < count; k++) {
            int i = pc++ % px.length;
            float a = (float) (-Math.PI * (0.1 + 0.8 * noise(pc * 7)));
            float sp = (50 + 90 * noise(pc * 11)) * power;
            px[i] = x + (noise(pc * 13) - 0.5f) * 90 * scale;
            py[i] = y;
            pvx[i] = (float) Math.cos(a) * sp;
            pvy[i] = (float) Math.sin(a) * sp;
            pl[i] = 0.5f + 0.5f * noise(pc * 17);
            ps[i] = 3 + 4 * noise(pc * 19);
        }
    }

    /** Deterministic hash noise in 0..1, so visuals never need their own Random. */
    private static float noise(int v) {
        int x = v * 374761393 + 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0xFFFF) / 65535f;
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        float sq0 = 0.5f * Math.max(0, (float) Math.sin(t * 5));
        c.fillRoundRect(cx - 110, cy + 10, 220, 38, 8, 0xFF5E4029);
        c.fillRoundRect(cx - 80, cy + 8, 160, 22, 8, 0xFFD8C8A4);
        float rx = 56 * (1 + 0.3f * sq0), ry = 34 * (1 - 0.5f * sq0);
        float[] p = new float[2 * 21];
        for (int i = 0; i <= 18; i++) {
            double a = Math.PI * i / 18;
            p[2 * i] = cx + (float) Math.cos(a) * rx;
            p[2 * i + 1] = cy + 18 - (float) Math.pow(Math.sin(a), 0.62) * ry;
        }
        p[38] = cx - rx;
        p[39] = cy + 22;
        p[40] = cx + rx;
        p[41] = cy + 22;
        c.fillPoly(p, 0xFFF1DDB0);
        float lift = 30 * (1 - sq0 * 2);
        c.line(cx + 90, cy - 60, cx + 14, cy - 18 - Math.max(0, lift) + 20 * sq0, 20, 0xFFE8E1D6);
        c.fillCircle(cx + 14, cy - 18 - Math.max(0, lift) + 20 * sq0, 13, 0xFFE0AB84);
        c.fillCircle(cx - 100, cy - 40, 22, alpha(0xFF8A1E, 0.35f));
        c.fillCircle(cx - 100, cy - 40, 12, 0xFFFFC857);
        for (int i = 0; i < 3; i++) c.fillCircle(cx - 70 + i * 28 + 0, cy - 62, 6, i == 1 ? 0xFFFFC857 : 0xFFF3E2BC);
    }

}
