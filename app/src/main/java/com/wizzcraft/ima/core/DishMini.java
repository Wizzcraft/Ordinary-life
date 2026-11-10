package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Dishwasher shift, four rushes at the back of the kitchen. A dirty dish slides onto the sink; drag a
 * finger over it and the grime wipes away exactly where the finger passed (coverage lives on a coarse
 * 14x14 grid, strokes are interpolated so a fast swipe still cleans a continuous line). When a dish is
 * clean enough it is rinsed and flies to the drying rack.
 *
 * The decisions: a spotless dish sparkles (worth most) but the last specks cost seconds; the rinse
 * button sends a dish out early, and a dish sent out with a visible spot is bounced back to the bottom
 * of the stack; baked-on crust takes circles or a soak (the bucket loosens a dish while you wash the
 * next one); a wine glass cracks when scrubbed too fast; dishes arrive faster every rush.
 */
final class DishMini extends Mini {
    private static final int G = 14, CELLS = G * G;
    private static final int PLATE = 0, PAN = 1, GLASS = 2;
    private static final int SAUCE = 1, CRUST = 2, GREASE = 3;
    private static final int ST_NONE = 0, ST_IN = 1, ST_SCRUB = 2, ST_RINSE = 3, ST_STAMP = 4, ST_CRACK = 5;

    /** Brush radius in logical units; energy deposited is path length, so speed never matters for cleaning. */
    private static final float BR = 22;
    private static final float SLIDE = 0.22f, RINSE_T = 0.42f, FLY_T = 0.34f, STAMP_T = 0.85f, CRACK_T = 0.8f;
    private static final float SOAK_TIME = 2.6f;
    /** Share of grime weight left: spotless sparkles, near is sent after a short idle, easy is the most the inspector tolerates. */
    private static final float SPOTLESS = 0.012f, NEAR = 0.05f, EASY = 0.12f, BUTTON_FROM = 0.45f;
    private static final float GLASS_SAFE_SPEED = 520;
    private static final int[] COUNT = {3, 3, 4, 4};
    private static final float[] LIMIT = {10.5f, 11.5f, 13f, 14.5f};
    private static final int[] SAUCE_COL = {0xFFD0472E, 0xFFE3AA1C, 0xFF8C5733, 0xFF6E9A3A};
    private static final float BDT = 1f / 30f;

    private static final int FN = 44, TN = 26, RN = 6, CN = 9;

    private static final class Dish {
        final int kind;
        final byte[] ty = new byte[CELLS];
        final float[] base = new float[CELLS], need = new float[CELLS], e = new float[CELLS];
        final int[] col = new int[CELLS];
        float totalW, remW, shiftU, arrived = 1;
        int retries, seed;
        boolean hard;

        Dish(int kind) {
            this.kind = kind;
        }
    }

    // ---- round state ----
    private int round, total, arrIdx, done, sparkles, returned, broken;
    private float diff, time, limit, gap;
    private int result = RUNNING;
    private final ArrayList<Dish> tower = new ArrayList<>();
    private final ArrayList<Dish> queue = new ArrayList<>();
    private final float[] arrT = new float[8];
    private Dish cur, soak, flyDish;
    private int curState;
    private float curT, blockT, fromX, fromY, flyT, flyToX, flyToY, soakT, lastCleanT, stress;
    private boolean soakReady, fetchSoak, rinseSpark, flySpark, learned, warned;
    private float scrubPath, shine;
    private final int[] rackKind = new int[8];
    private final boolean[] rackSpark = new boolean[8];

    // ---- touch ----
    private boolean scrubbing, fingerDown;
    private float lx, ly, pathAcc, speed, foamAcc, tickCd, rippleCd, suds;

    // ---- layout (set by draw, defaults fit the 760 screen) ----
    private float lt = 166, lb = 664, bandH = 78, cx = 180, cy = 400, R = 86, cs = 2 * 86f / G;
    private float soakX = 12, soakY = 614, rinseX = 244, btnW = 104, btnH = 46;
    private boolean clumpsReady;

    // ---- effects ----
    private final float[] fx = new float[FN], fy = new float[FN], fvx = new float[FN], fvy = new float[FN],
            fl = new float[FN], fm = new float[FN], fr = new float[FN];
    private int fHead;
    private final float[] tx = new float[TN], ty = new float[TN], tl = new float[TN], ts = new float[TN];
    private int tHead;
    private final float[] rx = new float[RN], ry = new float[RN], ra = new float[RN];
    private int rHead;
    private final float[] hx = new float[CN], hy = new float[CN], kx = new float[CN], ky = new float[CN],
            kvx = new float[CN], kvy = new float[CN];
    private int rs = 777;
    private final float[] poly = new float[64];

    // ---- bot ----
    private boolean bDown, bCircle, bSoakChecked;
    private float bx, by, bDelay, bAng, bRad, bCx, bCy, bT, bV, bEx, bEy, bSx, bSy;
    private int bLegs;
    private Dish bDish;

    // =====================================================================================
    // Mini contract
    // =====================================================================================

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("שוטף כלים", "שוטפת כלים");
    }

    @Override
    String howTo() {
        return "גוררים את האצבע על הצלחת ומשפשפים עד שהיא נקייה, ואז שוטפים. "
                + "כתם עקשן? עיגולים או השרייה. כוסות לאט!";
    }

    @Override
    void begin(int r, float difficulty) {
        round = Math.max(0, Math.min(3, r));
        diff = Ui.clamp(difficulty, 0, 1.2f);
        total = COUNT[round];
        time = 0;
        limit = LIMIT[round] * (1 - 0.14f * Math.min(1, diff));
        gap = Math.max(1.3f, 2.7f - 0.4f * round - 0.6f * diff);
        result = RUNNING;
        arrIdx = done = sparkles = returned = broken = 0;
        tower.clear();
        queue.clear();
        cur = soak = flyDish = null;
        curState = ST_NONE;
        curT = blockT = flyT = soakT = lastCleanT = stress = 0;
        soakReady = fetchSoak = warned = false;
        scrubbing = fingerDown = false;
        pathAcc = speed = foamAcc = tickCd = rippleCd = scrubPath = shine = 0;
        suds = 0.15f;
        bDown = false;
        bDish = null;
        for (int i = 0; i < FN; i++) fl[i] = 0;
        for (int i = 0; i < TN; i++) tl[i] = 0;
        for (int i = 0; i < RN; i++) ra[i] = 9;
        for (int i = 0; i < 8; i++) rackKind[i] = -1;
        if (round == 0) learned = false;

        Random rg = new Random(h.rnd().nextLong());
        // (kind, crust blobs) per rush; the first dish is always an easy plate.
        int[][][] plan = {
                {{PLATE, 0}, {PLATE, 0}, {PLATE, 0}},
                {{PLATE, 0}, {PLATE, 1}, {PLATE, 1}},
                {{PLATE, 0}, {GLASS, 0}, {PAN, 1}, {PLATE, 1}},
                {{PLATE, 1}, {GLASS, 0}, {PAN, 1}, {PLATE, 2}},
        };
        int[][] p = plan[round];
        ArrayList<int[]> order = new ArrayList<>();
        for (int[] d : p) order.add(d);
        for (int i = order.size() - 1; i > 1; i--) {
            int j = 1 + rg.nextInt(i);
            int[] t = order.get(i);
            order.set(i, order.get(j));
            order.set(j, t);
        }
        for (int i = 0; i < total; i++) {
            int[] d = order.get(i);
            queue.add(make(d[0], d[1], rg, round == 0));
            arrT[i] = i < 2 ? 0 : (i - 1) * gap;
        }
        clumpsReady = false;
    }

    @Override
    void pause() {
        scrubbing = false;
        fingerDown = false;
        bDown = false;
    }

    @Override
    int update(float dt) {
        if (result != RUNNING) return result;
        dt = Math.min(dt, 0.1f);
        time += dt;
        curT += dt;
        if (blockT > 0) blockT -= dt;
        for (Dish d : tower) d.arrived += dt;

        // new dishes land on the stack
        while (arrIdx < total && time >= arrT[arrIdx]) {
            Dish d = queue.get(arrIdx);
            d.arrived = 0;
            if (arrIdx < 2) tower.add(0, d);
            else {
                tower.add(d);
                h.sfx(Sfx.TAP);
            }
            arrIdx++;
        }

        // finger speed (path length per second, smoothed)
        float inst = pathAcc / Math.max(dt, 0.001f);
        pathAcc = 0;
        speed += (inst - speed) * Math.min(1, dt * 14);
        tickCd -= dt;
        rippleCd -= dt;
        suds = Math.max(0.1f, suds - dt * 0.03f);

        if (cur == null && curState == ST_NONE && blockT <= 0) pull();

        if (cur != null) {
            switch (curState) {
                case ST_IN:
                    if (curT >= SLIDE) {
                        curState = ST_SCRUB;
                        lastCleanT = time;
                    }
                    break;
                case ST_SCRUB:
                    scrubTick(dt);
                    break;
                case ST_RINSE:
                    shine += dt;
                    if (curT >= RINSE_T) startFly();
                    break;
                case ST_STAMP:
                    if (curT >= STAMP_T) {
                        cur.retries++;
                        tower.add(0, cur);
                        cur = null;
                        curState = ST_NONE;
                    }
                    break;
                case ST_CRACK:
                    if (curT >= CRACK_T) {
                        cur = null;
                        curState = ST_NONE;
                        stress = 0;
                    }
                    break;
                default:
                    break;
            }
        }
        if (flyDish != null) {
            flyT += dt;
            if (flyT >= FLY_T) finishFly();
        }

        // soak bucket
        if (soak != null && !soakReady) {
            soakT += dt;
            if (soakT >= SOAK_TIME) {
                soakReady = true;
                h.text(soakX + btnW / 2, soakY - 6, "מוכן", Ui.GOOD);
                h.sfx(Sfx.GOOD);
            }
        }

        updateFx(dt);

        boolean allIn = arrIdx >= total && tower.isEmpty() && cur == null && soak == null && flyDish == null;
        if (allIn || time >= limit) return decide();
        return RUNNING;
    }

    private int decide() {
        int left = total - done - broken;
        float pts = sparkles + 0.6f * (done - sparkles) - 0.15f * returned - 0.5f * broken;
        float frac = Math.max(0, pts) / total;
        if (left == 0 && broken == 0 && frac >= 0.82f) result = PERFECT;
        else if (frac >= 0.35f && left * 2 <= total) result = OK;
        else result = MISS;
        return result;
    }

    // =====================================================================================
    // Dish generation
    // =====================================================================================

    private static float hs(int a, int b) {
        int n = a * 374761393 + b * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFF) / 65535f;
    }

    private static float cu(int ix) {
        return -1 + (ix + 0.5f) * 2f / G;
    }

    private static boolean inMask(int kind, float u, float v) {
        if (kind == GLASS) {
            if (v < -0.93f) return false;
            float a = u / 0.58f, b = (v + 0.93f) / 1.07f;
            return a * a + b * b <= 1;
        }
        float lim = kind == PAN ? 0.84f : 0.9f;
        return u * u + v * v <= lim * lim;
    }

    private static float weight(int t) {
        return t == CRUST ? 2f : t == GREASE ? 0.7f : 1f;
    }

    private Dish make(int kind, int crusts, Random rg, boolean soft) {
        Dish d = new Dish(kind);
        d.seed = rg.nextInt(10000);
        float harden = 1 + 0.3f * diff;
        if (kind == PAN) d.shiftU = -0.2f;
        if (kind == PLATE) {
            int nb = soft ? 2 : 2 + rg.nextInt(2);
            for (int b = 0; b < nb; b++) {
                float a = rg.nextFloat() * 6.283f, rr = 0.15f + rg.nextFloat() * 0.45f;
                float u0 = (float) Math.cos(a) * rr, v0 = (float) Math.sin(a) * rr;
                float rb = soft ? 0.3f + rg.nextFloat() * 0.08f : 0.2f + rg.nextFloat() * 0.12f;
                int col = SAUCE_COL[rg.nextInt(3)];
                blob(d, u0, v0, rb, SAUCE, soft ? 0.5f : 0.66f, col, b * 31 + d.seed);
            }
        } else if (kind == PAN) {
            for (int i = 0; i < CELLS; i++) {
                float u = cu(i % G), v = cu(i / G);
                if (u * u + v * v <= 0.78f * 0.78f && hs(i, d.seed) > 0.3f) put(d, i, GREASE, 1.5f * harden, 0xFFE9D77B);
            }
        } else {
            // wine film in the lower bowl plus two lip smudges
            for (int i = 0; i < CELLS; i++) {
                float u = cu(i % G), v = cu(i / G);
                if (inMask(GLASS, u, v) && v > -0.45f && hs(i, d.seed) > 0.4f) put(d, i, SAUCE, 0.5f, 0xFFB0405F);
            }
            for (int b = 0; b < 2; b++) {
                float u0 = (rg.nextFloat() - 0.5f) * 0.5f, v0 = -0.75f + rg.nextFloat() * 0.45f;
                blob(d, u0, v0, 0.2f + rg.nextFloat() * 0.08f, SAUCE, 0.6f, 0xFF8E2347, b * 17 + d.seed);
            }
        }
        for (int b = 0; b < crusts; b++) {
            float a = rg.nextFloat() * 6.283f, rr = 0.1f + rg.nextFloat() * 0.5f;
            float u0 = (float) Math.cos(a) * rr, v0 = (float) Math.sin(a) * rr;
            blob(d, u0, v0, 0.17f + rg.nextFloat() * 0.06f, CRUST, 4.5f * harden, 0xFF3B2416, b * 13 + d.seed);
        }
        for (int i = 0; i < CELLS; i++) {
            if (d.ty[i] == 0) continue;
            d.totalW += weight(d.ty[i]);
            if (d.ty[i] != SAUCE) d.hard = true;
        }
        d.remW = d.totalW;
        return d;
    }

    private void put(Dish d, int i, int type, float need, int col) {
        d.ty[i] = (byte) type;
        d.base[i] = d.need[i] = need;
        d.col[i] = col;
    }

    private void blob(Dish d, float u0, float v0, float rb, int type, float need, int col, int salt) {
        for (int i = 0; i < CELLS; i++) {
            float u = cu(i % G), v = cu(i / G);
            if (!inMask(d.kind, u, v)) continue;
            float du = u - u0, dv = v - v0;
            float lim = rb * (0.82f + 0.36f * hs(i, salt));
            if (du * du + dv * dv <= lim * lim) put(d, i, type, need, col);
        }
    }

    // =====================================================================================
    // Flow of dishes
    // =====================================================================================

    private void pull() {
        if (soak != null && (soakReady || fetchSoak)) {
            float p = Math.min(1, soakT / SOAK_TIME);
            for (int i = 0; i < CELLS; i++) {
                if (soak.ty[i] != 0) soak.need[i] = soak.e[i] + (soak.need[i] - soak.e[i]) * (1 - 0.78f * p);
            }
            cur = soak;
            fromX = soakX + 30;
            fromY = soakY + 10;
            soak = null;
            soakReady = fetchSoak = false;
        } else if (!tower.isEmpty()) {
            cur = tower.remove(tower.size() - 1);
            fromX = 52;
            fromY = lt + bandH - 26;
        } else {
            return;
        }
        curState = ST_IN;
        curT = 0;
        stress = 0;
        warned = false;
        scrubPath = 0;
        scrubbing = false;
        h.sfx(Sfx.TAP);
    }

    private float left() {
        return cur == null ? 1 : cur.remW / cur.totalW;
    }

    private void scrubTick(float dt) {
        float l = left();
        if (cur.kind == GLASS) {
            if (scrubbing && speed > GLASS_SAFE_SPEED) {
                stress += (speed - GLASS_SAFE_SPEED) / 420f * dt;
                if (stress > 0.4f && !warned) {
                    warned = true;
                    h.text(cx, cy - R - 6, "לאט!", Ui.ORANGE);
                    h.sfx(Sfx.TICK);
                }
            } else {
                stress = Math.max(0, stress - 0.55f * dt);
                if (stress < 0.15f) warned = false;
            }
            if (stress >= 1) {
                crack();
                return;
            }
        }
        float idle = time - lastCleanT;
        if (l <= SPOTLESS || (l <= NEAR && idle > 0.6f) || (l <= EASY && idle > 1.7f)) accept();
    }

    private void accept() {
        boolean spark = left() <= SPOTLESS;
        curState = ST_RINSE;
        curT = 0;
        shine = 0;
        rinseSpark = spark;
        scrubbing = false;
        done++;
        if (spark) sparkles++;
        h.sfx(Sfx.POUR);
        if (spark) {
            h.sfx(Sfx.PERFECT);
            h.text(cx, cy - R * 0.6f, "נוצץ!", Ui.ACCENT);
            h.burst(cx, cy, 0xFFFFE9A0, 14);
            for (int i = 0; i < 7; i++) twinkle(cx + (hs(i, 3) - 0.5f) * R * 1.7f, cy + (hs(i, 9) - 0.5f) * R * 1.7f, 14 + hs(i, 5) * 8);
        } else {
            h.sfx(Sfx.GOOD);
            h.text(cx, cy - R * 0.6f, "נקי", Ui.GOOD);
            for (int i = 0; i < 3; i++) twinkle(cx + (hs(i, 4) - 0.5f) * R * 1.4f, cy + (hs(i, 8) - 0.5f) * R * 1.4f, 9);
        }
    }

    private void reject() {
        curState = ST_STAMP;
        curT = 0;
        scrubbing = false;
        returned++;
        h.sfx(Sfx.BAD);
        h.shake(5);
        h.text(cx, cy - R * 0.5f, "חזר!", Ui.BAD);
    }

    private void crack() {
        curState = ST_CRACK;
        curT = 0;
        scrubbing = false;
        broken++;
        h.sfx(Sfx.BAD);
        h.shake(8);
        h.text(cx, cy - R * 0.6f, "נסדקה!", Ui.BAD);
        h.burst(cx, cy - R * 0.4f, 0xFFDDF2FF, 14);
    }

    private void startFly() {
        if (flyDish != null) finishFly();
        flyDish = cur;
        flySpark = rinseSpark;
        flyT = 0;
        int slot = Math.min(7, done - 1);
        rackKind[slot] = cur.kind;
        rackSpark[slot] = rinseSpark;
        flyToX = rackX(slot);
        flyToY = lt + bandH - 24;
        flyFromX = cx + cur.shiftU * R;
        flyFromY = cy;
        cur = null;
        curState = ST_NONE;
    }

    private float flyFromX, flyFromY;

    private void finishFly() {
        if (flyDish == null) return;
        if (flySpark) {
            twinkle(flyToX, flyToY - 12, 16);
            twinkle(flyToX + 8, flyToY + 4, 10);
            h.burst(flyToX, flyToY, 0xFFFFE9A0, 6);
        }
        flyDish = null;
    }

    private float rackX(int slot) {
        float w = Math.min(5, total) * 12 + 8;
        return 308 - w / 2 + 10 + slot * 12;
    }

    // =====================================================================================
    // Touch
    // =====================================================================================

    private boolean hitSoak(float x, float y) {
        return x >= soakX - 4 && x <= soakX + btnW + 4 && y >= soakY - 4 && y <= soakY + btnH + 6;
    }

    private boolean hitRinse(float x, float y) {
        return x >= rinseX - 4 && x <= rinseX + btnW + 4 && y >= soakY - 4 && y <= soakY + btnH + 6;
    }

    @Override
    void down(float x, float y) {
        fingerDown = true;
        if (result != RUNNING) return;
        if (hitSoak(x, y)) {
            onSoak();
            return;
        }
        if (hitRinse(x, y)) {
            onRinse();
            return;
        }
        if (cur != null && curState == ST_SCRUB) beginScrub(x, y);
    }

    @Override
    void move(float x, float y) {
        if (result != RUNNING) return;
        if (!scrubbing) {
            // A swipe that began while the dish was still sliding in joins as soon as it settles.
            if (fingerDown && cur != null && curState == ST_SCRUB && y < soakY - 6) beginScrub(x, y);
            return;
        }
        if (cur == null || curState != ST_SCRUB) {
            scrubbing = false;
            return;
        }
        stroke(lx, ly, x, y);
        lx = x;
        ly = y;
    }

    @Override
    void up(float x, float y) {
        scrubbing = false;
        fingerDown = false;
    }

    private void beginScrub(float x, float y) {
        scrubbing = true;
        lx = x;
        ly = y;
        stroke(x, y, x + 0.5f, y);
    }

    private void onSoak() {
        if (soak != null) {
            // Take the bucket's dish back to the sink (the dish there goes on top of the stack).
            if (cur != null && curState == ST_SCRUB) {
                tower.add(cur);
                cur = null;
                curState = ST_NONE;
                scrubbing = false;
            }
            if (cur == null) fetchSoak = true;
            h.sfx(Sfx.TAP);
        } else if (cur != null && curState == ST_SCRUB) {
            soak = cur;
            soakT = 0;
            soakReady = false;
            cur = null;
            curState = ST_NONE;
            scrubbing = false;
            h.sfx(Sfx.POUR);
            h.text(soakX + btnW / 2, soakY - 6, "מושרה", Ui.BLUE);
            for (int i = 0; i < 5; i++) spawnFoam(soakX + 20 + i * 14, soakY + 4);
        }
    }

    private void onRinse() {
        if (cur == null || curState != ST_SCRUB) return;
        float l = left();
        if (l > BUTTON_FROM) {
            h.sfx(Sfx.TICK);
            h.text(cx, cy, "עוד שפשוף", Ui.DIM);
            return;
        }
        if (l <= EASY) accept();
        else reject();
    }

    // =====================================================================================
    // Scrubbing
    // =====================================================================================

    private float originX(Dish d) {
        return cx + d.shiftU * R;
    }

    private void stroke(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.01f) return;
        if (d > 700) d = 700;
        pathAcc += d;
        scrubPath += d;
        if (!learned && scrubPath > 120) learned = true;
        int steps = Math.max(1, (int) Math.ceil(d / 5f));
        float sl = d / steps;
        boolean cleaned = false;
        for (int i = 1; i <= steps; i++) {
            float f = (float) i / steps;
            float px = x0 + dx * f, py = y0 + dy * f;
            if (brush(px, py, sl)) cleaned = true;
            foamAcc += sl;
            if (foamAcc > 16) {
                foamAcc = 0;
                spawnFoam(px, py);
            }
            pushSuds(px, py);
        }
        suds = Math.min(1, suds + d / 2500f);
        if (rippleCd <= 0) {
            rippleCd = 0.14f;
            ra[rHead % RN] = 0;
            rx[rHead % RN] = x1;
            ry[rHead % RN] = y1;
            rHead++;
        }
        if (cleaned && tickCd <= 0) {
            tickCd = 0.17f;
            h.sfx(Sfx.TICK);
        }
    }

    /** Applies the brush at one point; returns true if any cell was cleaned. */
    private boolean brush(float px, float py, float sl) {
        Dish d = cur;
        if (d == null) return false;
        float ox = originX(d) - R, oy = cy - R;
        int i0 = Math.max(0, (int) Math.floor((px - BR - ox) / cs)), i1 = Math.min(G - 1, (int) Math.floor((px + BR - ox) / cs));
        int j0 = Math.max(0, (int) Math.floor((py - BR - oy) / cs)), j1 = Math.min(G - 1, (int) Math.floor((py + BR - oy) / cs));
        boolean any = false;
        float k = sl / (2 * BR);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int idx = j * G + i;
                if (d.ty[idx] == 0) continue;
                float ddx = ox + (i + 0.5f) * cs - px, ddy = oy + (j + 0.5f) * cs - py;
                float q = (ddx * ddx + ddy * ddy) / (BR * BR);
                if (q >= 1) continue;
                d.e[idx] += k * (1 - 0.45f * q);
                if (d.e[idx] >= d.need[idx]) {
                    d.remW -= weight(d.ty[idx]);
                    d.ty[idx] = 0;
                    any = true;
                    lastCleanT = time;
                    if (hs(idx, (int) (time * 31)) < 0.22f) twinkle(ox + (i + 0.5f) * cs, oy + (j + 0.5f) * cs, 6 + 5 * hs(idx, 2));
                }
            }
        }
        if (any && d.remW < 0.0001f) d.remW = 0;
        return any;
    }

    // =====================================================================================
    // Effects
    // =====================================================================================

    private float rand() {
        rs = rs * 1103515245 + 12345;
        return ((rs >>> 8) & 0xFFFF) / 65535f;
    }

    private void spawnFoam(float x, float y) {
        int i = fHead++ % FN;
        fx[i] = x + (rand() - 0.5f) * 14;
        fy[i] = y + (rand() - 0.5f) * 14;
        fvx[i] = (rand() - 0.5f) * 16;
        fvy[i] = -10 - rand() * 22;
        fm[i] = fl[i] = 0.7f + rand() * 0.8f;
        fr[i] = 2.5f + rand() * 4.5f;
    }

    private void twinkle(float x, float y, float size) {
        int i = tHead++ % TN;
        tx[i] = x;
        ty[i] = y;
        ts[i] = size;
        tl[i] = 0.55f;
    }

    private void pushSuds(float px, float py) {
        if (!clumpsReady) return;
        for (int i = 0; i < CN; i++) {
            float dx = kx[i] - px, dy = ky[i] - py;
            float q = dx * dx + dy * dy;
            if (q < 70 * 70 && q > 0.01f) {
                float dd = (float) Math.sqrt(q), s = (70 - dd) * 0.06f;
                kvx[i] += dx / dd * s;
                kvy[i] += dy / dd * s;
            }
        }
    }

    private void updateFx(float dt) {
        for (int i = 0; i < FN; i++) {
            if (fl[i] <= 0) continue;
            fl[i] -= dt;
            fx[i] += fvx[i] * dt;
            fy[i] += fvy[i] * dt;
        }
        for (int i = 0; i < TN; i++) if (tl[i] > 0) tl[i] -= dt;
        for (int i = 0; i < RN; i++) if (ra[i] < 5) ra[i] += dt;
        if (clumpsReady) {
            for (int i = 0; i < CN; i++) {
                kvx[i] += (hx[i] - kx[i]) * 3.5f * dt;
                kvy[i] += (hy[i] - ky[i]) * 3.5f * dt;
                kvx[i] *= Math.max(0, 1 - 4.5f * dt);
                kvy[i] *= Math.max(0, 1 - 4.5f * dt);
                kx[i] += kvx[i] * dt * 6;
                ky[i] += kvy[i] * dt * 6;
            }
        }
    }

    // =====================================================================================
    // Drawing
    // =====================================================================================

    private static int al(int rgb, float a) {
        int v = (int) (Ui.clamp(a, 0, 1) * 255);
        return (v << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void layout(float top, float bottom) {
        boolean changed = top != lt || bottom != lb;
        lt = top;
        lb = bottom;
        bandH = 78;
        soakY = bottom - 52;
        float boardTop = top + bandH, boardBot = soakY - 6;
        R = Math.min(88, (boardBot - boardTop - 14) / 2);
        cs = 2 * R / G;
        cx = 180;
        cy = (boardTop + boardBot) / 2;
        soakX = 12;
        rinseX = 360 - 12 - btnW;
        if (changed || !clumpsReady) {
            float[] xs = {26, 334, 54, 308, 20, 342, 96, 266, 180};
            float[] ys = {0.06f, 0.1f, 0.5f, 0.55f, 0.88f, 0.9f, 0.02f, 0.04f, 0.96f};
            for (int i = 0; i < CN; i++) {
                hx[i] = kx[i] = xs[i];
                hy[i] = ky[i] = boardTop + 6 + ys[i] * (boardBot - boardTop - 4);
                kvx[i] = kvy[i] = 0;
            }
            clumpsReady = true;
        }
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawKitchen(c);
        drawSink(c);
        drawTower(c);
        drawRack(c);
        drawBoard(c);
        drawFoam(c);
        drawButtons(c);
        drawHud(c);
    }

    private void drawKitchen(Gfx c) {
        float by0 = lt, by1 = lt + bandH;
        c.fillRect(0, by0, 360, bandH, 0xFF1B2430);
        for (int i = 1; i < 12; i++) c.fillRect(i * 30 - 0.5f, by0, 1, bandH, 0x14FFFFFF);
        for (int j = 1; j < 4; j++) c.fillRect(0, by0 + j * 20 - 0.5f, 360, 1, 0x14FFFFFF);
        // hanging ladle and spatula
        c.line(112, by0 + 8, 112, by0 + 36, 2.5f, 0xFF8E9AA8);
        c.fillCircle(112, by0 + 40, 6.5f, 0xFF8E9AA8);
        c.fillCircle(112, by0 + 40, 4, 0xFF1B2430);
        c.line(250, by0 + 8, 250, by0 + 32, 2.5f, 0xFF8E9AA8);
        c.fillRoundRect(244, by0 + 31, 12, 12, 2.5f, 0xFF8E9AA8);
        c.fillRect(248, by0 + 34, 1.5f, 6, 0xFF1B2430);
        c.fillRect(251, by0 + 34, 1.5f, 6, 0xFF1B2430);
        // faucet
        c.fillRoundRect(173, by0 + 8, 14, 18, 3, 0xFF9AA6B4);
        c.fillRoundRect(168, by0 + 24, 24, 9, 4, 0xFFB8C3CF);
        c.fillRect(176, by0 + 10, 3, 14, 0x55FFFFFF);
        float drip = (time * 0.8f) % 1.6f;
        if (drip < 0.5f && curState != ST_RINSE) c.fillCircle(180, by0 + 36 + drip * 70, 2.3f, 0xCC9FD4EE);
        // counter
        c.fillRect(0, by1 - 9, 360, 9, 0xFF6B7785);
        c.fillRect(0, by1 - 9, 360, 2, 0xFF93A0AF);
        // steam from the hot water
        for (int i = 0; i < 7; i++) {
            float ph = (time * 0.22f + i * 0.143f) % 1f;
            float sx = 96 + i * 28 + (float) Math.sin(time * 1.3f + i * 2) * 9;
            float sy = by1 - 4 - ph * 62;
            c.fillCircle(sx, sy, 7 + ph * 14, al(0xFFFFFF, 0.09f * (1 - ph)));
        }
    }

    private void drawSink(Gfx c) {
        float y0 = lt + bandH, y1 = lb;
        c.fillRoundRect(2, y0, 356, y1 - y0 + 2, 14, 0xFF7B8794);
        float murk = Math.min(1, done / (float) total) * 0.45f;
        int w1 = mix(0xFF2D7391, 0xFF55705F, murk), w2 = mix(0xFF235E78, 0xFF405C4E, murk);
        c.fillRoundRect(9, y0 + 7, 342, y1 - y0 - 8, 10, w1);
        c.fillRoundRect(26, y0 + 20, 308, y1 - y0 - 30, 18, w2);
        c.fillRoundRect(9, y0 + 7, 342, 6, 3, 0x33000000);
        // water shimmer
        for (int k = 0; k < 2; k++) {
            float yy = y0 + 40 + k * (y1 - y0) * 0.38f;
            float px = 20, py = yy;
            for (int i = 1; i <= 9; i++) {
                float nx = 20 + i * 36, ny = yy + (float) Math.sin(time * 1.6f + i * 0.9f + k * 2) * 3;
                c.line(px, py, nx, ny, 1.6f, 0x1CFFFFFF);
                px = nx;
                py = ny;
            }
        }
        // ripples from the brush
        for (int i = 0; i < RN; i++) {
            if (ra[i] > 0.9f) continue;
            float rr = 10 + ra[i] * 70, a = 0.22f * (1 - ra[i] / 0.9f);
            for (int s = 0; s < 14; s++) {
                double a0 = s * Math.PI * 2 / 14, a1 = (s + 1) * Math.PI * 2 / 14;
                c.line(rx[i] + (float) Math.cos(a0) * rr, ry[i] + (float) Math.sin(a0) * rr * 0.8f,
                        rx[i] + (float) Math.cos(a1) * rr, ry[i] + (float) Math.sin(a1) * rr * 0.8f, 1.5f, al(0xFFFFFF, a));
            }
        }
        // suds islands: they swell with scrubbing and get shoved around by the finger
        float sc = 1 + suds * 0.55f;
        for (int i = 0; i < CN; i++) {
            float bob = (float) Math.sin(time * 1.1f + i * 1.7f) * 1.5f;
            float x = kx[i], y = ky[i] + bob;
            c.fillCircle(x, y, 9 * sc, 0xD8EAF6FF);
            c.fillCircle(x - 8 * sc, y + 2, 6 * sc, 0xD0F4FAFF);
            c.fillCircle(x + 8 * sc, y + 2, 7 * sc, 0xD0F0F8FF);
            c.fillCircle(x - 2, y - 6 * sc, 6 * sc, 0xE0FFFFFF);
            c.fillCircle(x + 3, y + 5 * sc, 5 * sc, 0xC8E4F2FF);
            c.fillCircle(x - 4, y - 4 * sc, 2.5f, 0x99FFFFFF);
        }
        // ambient bubbles rising and popping
        float boardH = lb - 52 - 6 - (lt + bandH);
        for (int i = 0; i < 12; i++) {
            float per = 3.4f + hs(i, 1) * 3.2f;
            float ph = (time / per + hs(i, 2)) % 1f;
            float bx0 = 30 + hs(i, 3) * 300 + (float) Math.sin(time * 1.4f + i) * 6;
            float by = lt + bandH + 14 + boardH - ph * (boardH + 6);
            float r = 3 + hs(i, 4) * 4;
            if (ph < 0.93f) {
                c.fillCircle(bx0, by, r, 0x40FFFFFF);
                c.fillCircle(bx0 - r * 0.3f, by - r * 0.3f, r * 0.3f, 0x99FFFFFF);
            } else {
                float pr = r * (1 + (ph - 0.93f) * 12);
                c.fillCircle(bx0, by, pr, al(0xFFFFFF, 0.3f * (1 - (ph - 0.93f) / 0.07f)));
                c.fillCircle(bx0, by, Math.max(0.5f, pr - 1.5f), w1 & 0x00FFFFFF | 0x40000000);
            }
        }
    }

    private void drawTower(Gfx c) {
        float base = lt + bandH - 9, x = 52;
        int n = tower.size();
        
        for (int i = 0; i < n && i < 8; i++) {
            Dish d = tower.get(i);
            float drop = d.arrived < 0.25f ? (0.25f - d.arrived) / 0.25f : 0;
            float y = base - (i + 1) * 9 - drop * drop * 26;
            int dirt = firstDirt(d);
            if (d.kind == PAN) {
                c.fillRoundRect(x - 26, y - 1, 52, 9, 3.5f, 0xFF3A424C);
                c.fillRoundRect(x + 24, y + 2, 17, 4, 2, 0xFF2A3038);
                c.fillRoundRect(x - 18, y + 1, 36, 4, 2, al(dirt, 0.9f));
            } else if (d.kind == GLASS) {
                c.fillRoundRect(x - 20, y, 40, 8, 4, 0xCCCDE8F5);
                c.fillRoundRect(x - 22, y + 2, 5, 4, 2, 0xFFEAF6FC);
                c.fillRoundRect(x - 8, y + 1.5f, 14, 4, 2, al(dirt, 0.9f));
            } else {
                c.fillRoundRect(x - 28, y, 56, 8, 4, 0xFFD3D9E2);
                c.fillRoundRect(x - 22, y - 1, 44, 4, 2, 0xFFF1F3F7);
                c.fillCircle(x - 8 + (i % 3) * 6, y + 1.5f, 3.2f, al(dirt, 0.95f));
                c.fillCircle(x + 9 - (i % 2) * 5, y + 1, 2.4f, al(dirt, 0.95f));
            }
        }
        // soon-to-arrive marker: pips under the counter would clutter, so a small count label instead
        int incoming = total - arrIdx;
        if (incoming > 0) c.text("עוד " + incoming, x, lt + 26, 12, Ui.ACCENT, Gfx.CENTER);
    }

    private int firstDirt(Dish d) {
        for (int i = 0; i < CELLS; i++) if (d.ty[i] != 0) return d.col[i];
        return 0xFFB0B8C2;
    }

    private void drawRack(Gfx c) {
        float base = lt + bandH - 9;
        int n = Math.min(5, total);
        float w = n * 12 + 8, x0 = 308 - w / 2;
        c.fillRoundRect(x0, base - 6, w, 6, 3, 0xFF9AA6B4);
        c.fillRoundRect(x0, base - 6, w, 2, 1, 0xFFC4CEDA);
        for (int s = 0; s < n; s++) {
            float sx = x0 + 6 + s * 12;
            c.fillRect(sx - 1, base - 33, 9, 27, 0x14FFFFFF);
        }
        for (int s = 0; s < Math.min(8, done + (flyDish != null ? 0 : 0)); s++) {
            int k = rackKind[s];
            if (k < 0) continue;
            if (flyDish != null && s == done - 1) continue; // still in the air
            float sx = rackX(s);
            if (k == PAN) {
                c.fillRoundRect(sx - 4, base - 32, 9, 26, 4, 0xFF4B5461);
                c.fillRect(sx - 1, base - 40, 3, 9, 0xFF3A424C);
            } else if (k == GLASS) {
                c.fillCircle(sx, base - 25, 5, 0xCCE2F3FA);
                c.fillRect(sx - 1, base - 20, 2, 12, 0xCCE2F3FA);
                c.fillRoundRect(sx - 4, base - 9, 8, 3, 1.5f, 0xCCE2F3FA);
            } else {
                c.fillRoundRect(sx - 3.5f, base - 32, 8, 26, 4, 0xFFF4F6FA);
                c.fillRoundRect(sx - 1, base - 30, 3, 22, 1.5f, 0xFFC9D2DE);
            }
            if (rackSpark[s]) {
                float tw = 0.75f + 0.25f * (float) Math.sin(time * 6 + s * 2);
                star(c, sx, base - 38, 6 * tw, 0xFFFFE9A0);
            }
        }
    }

    /** Four-point sparkle star. */
    private void star(Gfx c, float x, float y, float r, int col) {
        float q = r * 0.28f;
        poly[0] = x;
        poly[1] = y - r;
        poly[2] = x + q;
        poly[3] = y - q;
        poly[4] = x + r;
        poly[5] = y;
        poly[6] = x + q;
        poly[7] = y + q;
        poly[8] = x;
        poly[9] = y + r;
        poly[10] = x - q;
        poly[11] = y + q;
        poly[12] = x - r;
        poly[13] = y;
        poly[14] = x - q;
        poly[15] = y - q;
        float[] p = java.util.Arrays.copyOf(poly, 16);
        c.fillPoly(p, col);
    }

    private void drawBoard(Gfx c) {
        if (cur != null) {
            float ox = cx + cur.shiftU * R, oy = cy, sc = 1;
            if (curState == ST_IN) {
                float p = Math.min(1, curT / SLIDE);
                p = 1 - (1 - p) * (1 - p) * (1 - p);
                ox = fromX + (ox - fromX) * p;
                oy = fromY + (oy - fromY) * p;
                sc = 0.3f + 0.7f * p;
            }
            if (curState == ST_STAMP) ox += (float) Math.sin(curT * 50) * (1 - curT / STAMP_T) * 5;
            float fade = curState == ST_RINSE ? 1 - curT / RINSE_T : 1;
            float crack = curState == ST_CRACK ? curT / CRACK_T : -1;
            drawDish(c, cur, ox, oy, R * sc, fade, crack);
            if (curState == ST_SCRUB || curState == ST_IN) drawRing(c, ox - cur.shiftU * R * sc, oy, R * sc);
            if (curState == ST_RINSE) drawRinse(c);
            if (curState == ST_STAMP) {
                c.fillRoundRect(cx - 74, cy - 24, 148, 48, 8, 0xCC12161C);
                c.strokeRoundRect(cx - 74, cy - 24, 148, 48, 8, 4, Ui.BAD);
                c.text("לא נקי!", cx, cy + 9, 28, Ui.BAD, Gfx.CENTER);
            }
            if (cur.kind == GLASS && curState == ST_SCRUB) drawGauge(c);
            if (!learned && round == 0 && curState == ST_SCRUB) drawHandHint(c);
        }
        if (flyDish != null) {
            float p = Math.min(1, flyT / FLY_T);
            float ex = p * p;
            float x = flyFromX + (flyToX - flyFromX) * ex;
            float y = flyFromY + (flyToY - flyFromY) * ex - (float) Math.sin(p * Math.PI) * 40;
            drawDish(c, flyDish, x, y, R * (1 - 0.78f * ex), 0, -1);
        }
        // shine sweep + twinkles
        for (int i = 0; i < TN; i++) {
            if (tl[i] <= 0) continue;
            float k = tl[i] / 0.55f;
            star(c, tx[i], ty[i], ts[i] * (0.4f + 0.6f * (float) Math.sin(k * Math.PI)), 0xFFFFF4C8);
        }
    }

    private void drawRinse(Gfx c) {
        float p = curT / RINSE_T;
        float x = cx, y1 = cy - R * 0.1f;
        c.line(180, lt + 32, x, y1, 7, 0xCC9FD8F2);
        c.line(180, lt + 32, x, y1, 3, 0xDDEAF8FF);
        for (int i = 0; i < 8; i++) {
            float a = i * 0.785f + curT * 9;
            float d = 22 + 40 * ((curT * 3 + i * 0.13f) % 1);
            c.fillCircle(x + (float) Math.cos(a) * d, y1 + (float) Math.sin(a) * d * 0.7f, 2.6f, 0xAAEAF8FF);
        }
        if (rinseSpark) {
            // diagonal glint across the dish
            float gx = cx - R * 1.2f + p * R * 2.6f;
            poly[0] = gx - 10;
            poly[1] = cy - R;
            poly[2] = gx + 4;
            poly[3] = cy - R;
            poly[4] = gx + 16;
            poly[5] = cy + R;
            poly[6] = gx + 2;
            poly[7] = cy + R;
            c.fillPoly(java.util.Arrays.copyOf(poly, 8), 0x55FFFFFF);
        }
    }

    private void drawRing(Gfx c, float ox, float oy, float rr) {
        float p = 1 - left();
        int lit = (int) Math.floor(p * 36 + 0.0001f);
        boolean ok = left() <= EASY;
        for (int i = 0; i < 36; i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / 36;
            float x = ox + (float) Math.cos(a) * (rr + 12), y = oy + (float) Math.sin(a) * (rr + 12);
            if (i < lit) c.fillCircle(x, y, 3.3f, ok ? Ui.GOOD : Ui.ACCENT);
            else c.fillCircle(x, y, 1.8f, 0x66FFFFFF);
        }
    }

    private void drawGauge(Gfx c) {
        float gx = 20, gy = cy - R * 0.9f, gh = R * 1.5f;
        c.fillRoundRect(gx, gy, 14, gh, 7, 0xAA12161C);
        float s = Math.min(1, stress);
        int col = s < 0.4f ? Ui.GOOD : s < 0.75f ? Ui.ORANGE : Ui.BAD;
        if (s > 0.01f) c.fillRoundRect(gx + 2, gy + gh - 2 - (gh - 4) * s, 10, (gh - 4) * s, 5, col);
        c.text("עדין", gx + 7, gy - 5, 12, s > 0.4f ? col : Ui.DIM, Gfx.CENTER);
        // a notch where the glass starts to suffer
        c.fillRect(gx - 3, gy + gh - 2 - (gh - 4) * 0.4f, 20, 1.5f, 0xAAFFFFFF);
    }

    private void drawHandHint(Gfx c) {
        // Ghost finger scribbling over the biggest remaining dirt: shows the verb without words.
        float sx = 0, sy = 0;
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            if (cur.ty[i] == 0) continue;
            sx += cu(i % G);
            sy += cu(i / G);
            n++;
        }
        if (n == 0) return;
        float mx = cx + sx / n * R, my = cy + sy / n * R;
        for (int k = 5; k >= 0; k--) {
            float t = time * 3.2f - k * 0.16f;
            float x = mx + (float) Math.sin(t * 1.5f) * 34, y = my + (float) Math.sin(t * 3f) * 10;
            c.fillCircle(x, y, 4 - k * 0.5f, al(0xFFFFFF, 0.5f - k * 0.07f));
        }
        float t = time * 3.2f;
        float x = mx + (float) Math.sin(t * 1.5f) * 34, y = my + (float) Math.sin(t * 3f) * 10;
        c.fillRoundRect(x - 7, y + 4, 15, 26, 7, 0xCCFFE0C2);
        c.fillCircle(x, y + 4, 8.5f, 0xDDFFE0C2);
        c.strokeRoundRect(x - 7, y + 4, 15, 26, 7, 1.5f, 0x88000000);
    }

    // ---- one dish, in normalised coordinates around (ox, oy) with radius rr ----

    private void drawDish(Gfx c, Dish d, float ox, float oy, float rr, float dirtAlpha, float crack) {
        if (d.kind == GLASS) {
            drawGlass(c, d, ox, oy, rr, crack);
        } else if (d.kind == PAN) {
            c.fillCircle(ox, oy + rr * 0.04f, rr * 1.04f, 0x55000000);
            poly[0] = ox + rr * 0.85f;
            poly[1] = oy - rr * 0.13f;
            poly[2] = ox + rr * 1.75f;
            poly[3] = oy - rr * 0.1f;
            poly[4] = ox + rr * 1.75f;
            poly[5] = oy + rr * 0.1f;
            poly[6] = ox + rr * 0.85f;
            poly[7] = oy + rr * 0.13f;
            c.fillPoly(java.util.Arrays.copyOf(poly, 8), 0xFF2A3038);
            c.fillCircle(ox + rr * 1.62f, oy, rr * 0.05f, 0xFF12161C);
            c.fillCircle(ox, oy, rr, 0xFF1F252C);
            c.fillCircle(ox, oy, rr * 0.93f, 0xFF39424D);
            c.fillCircle(ox, oy, rr * 0.86f, 0xFF66717F);
            c.fillCircle(ox - rr * 0.3f, oy - rr * 0.3f, rr * 0.28f, 0x14FFFFFF);
        } else {
            c.fillCircle(ox, oy + rr * 0.04f, rr * 1.04f, 0x55000000);
            c.fillCircle(ox, oy, rr, 0xFFCDD5E0);
            c.fillCircle(ox, oy, rr * 0.95f, 0xFFF5F7FB);
            c.fillCircle(ox, oy, rr * 0.77f, 0xFFB9CDE6);
            c.fillCircle(ox, oy, rr * 0.74f, 0xFFF5F7FB);
            c.fillCircle(ox, oy, rr * 0.66f, 0xFFE3E9F1);
            c.fillCircle(ox, oy, rr * 0.63f, 0xFFF2F5F9);
        }
        float cell = 2 * rr / G;
        float oy0 = oy - rr;
        float base = ox - rr; // ox already includes the pan's shift, exactly like the brush mapping
        for (int i = 0; i < CELLS; i++) {
            int t = d.ty[i];
            if (t == 0) continue;
            int ix = i % G, iy = i / G;
            float x = base + (ix + 0.5f) * cell + (hs(i, 7) - 0.5f) * cell * 0.25f;
            float y = oy0 + (iy + 0.5f) * cell + (hs(i, 8) - 0.5f) * cell * 0.25f;
            float frac = Ui.clamp((d.need[i] - d.e[i]) / d.base[i], 0.12f, 1f);
            float a = (0.38f + 0.62f * frac) * dirtAlpha;
            if (a <= 0.01f) continue;
            if (t == SAUCE) {
                c.fillCircle(x, y, cell * 0.78f, al(d.col[i], a));
                if (frac > 0.6f) c.fillCircle(x - cell * 0.22f, y - cell * 0.22f, cell * 0.17f, al(0xFFFFFF, 0.35f * dirtAlpha));
            } else if (t == CRUST) {
                float r = cell * 0.9f;
                poly[0] = x;
                poly[1] = y - r;
                poly[2] = x + r;
                poly[3] = y;
                poly[4] = x;
                poly[5] = y + r;
                poly[6] = x - r;
                poly[7] = y;
                c.fillPoly(java.util.Arrays.copyOf(poly, 8), al(d.col[i], a));
                if (frac > 0.3f) {
                    c.line(x - r * 0.5f, y - r * 0.5f, x + r * 0.5f, y + r * 0.5f, 1.3f, al(0xFFB59468, 0.8f * dirtAlpha));
                    c.line(x - r * 0.5f, y + r * 0.5f, x + r * 0.5f, y - r * 0.5f, 1.3f, al(0xFFB59468, 0.8f * dirtAlpha));
                }
            } else {
                c.fillCircle(x, y, cell * 0.8f, al(0xFFE9D77B, a * 0.7f));
                if (hs(i, 11) > 0.65f) c.line(x - cell * 0.3f, y - cell * 0.2f, x + cell * 0.1f, y - cell * 0.45f, 1.4f, al(0xFFFFFF, 0.6f * dirtAlpha));
            }
        }
        // wet gloss on the clean porcelain
        if (d.kind == PLATE) {
            c.line(ox - rr * 0.62f, oy - rr * 0.3f, ox - rr * 0.38f, oy - rr * 0.6f, 2.2f, 0x99FFFFFF);
        }
    }

    private void drawGlass(Gfx c, Dish d, float ox, float oy, float rr, float crack) {
        float split = crack >= 0 ? crack * rr * 0.25f : 0;
        float alpha = crack >= 0 ? 1 - crack : 1;
        int body = al(0xE4F2FA, 0.62f * alpha), edge = al(0xFFFFFF, 0.95f * alpha);
        // bowl as a half-ellipse polygon
        for (int half = 0; half < (crack >= 0 ? 2 : 1); half++) {
            int n = 20;
            int pc = 0;
            float sgn = half == 0 ? 1 : -1;
            float dxs = crack >= 0 ? sgn * split : 0;
            float dys = crack >= 0 ? crack * crack * rr * 0.4f : 0;
            float rot = crack >= 0 ? sgn * crack * 0.3f : 0;
            for (int k = 0; k <= n; k++) {
                double th = Math.PI * k / n;
                float u = (float) Math.cos(th) * 0.58f, v = -0.93f + (float) Math.sin(th) * 1.07f;
                if (crack >= 0) {
                    // split vertically: right half uses theta 0..pi/2, left half pi/2..pi
                    double t2 = half == 0 ? Math.PI * 0.5 * k / n : Math.PI * 0.5 + Math.PI * 0.5 * k / n;
                    u = (float) Math.cos(t2) * 0.58f;
                    v = -0.93f + (float) Math.sin(t2) * 1.07f;
                }
                poly[pc++] = ox + u * rr + dxs + v * rr * rot * 0.3f;
                poly[pc++] = oy + v * rr + dys;
            }
            if (crack >= 0) {
                poly[pc++] = ox + dxs;
                poly[pc++] = oy - 0.93f * rr + dys;
            }
            c.fillPoly(java.util.Arrays.copyOf(poly, pc), body);
            for (int k = 0; k + 3 < pc; k += 2) c.line(poly[k], poly[k + 1], poly[k + 2], poly[k + 3], 1.8f, edge);
        }
        if (crack < 0) {
            c.line(ox - rr * 0.58f, oy - 0.93f * rr, ox + rr * 0.58f, oy - 0.93f * rr, 2.4f, edge);
            // stem and foot
            c.fillRect(ox - rr * 0.05f, oy + rr * 0.13f, rr * 0.1f, rr * 0.55f, body);
            c.line(ox - rr * 0.05f, oy + rr * 0.13f, ox - rr * 0.05f, oy + rr * 0.68f, 1.5f, edge);
            c.line(ox + rr * 0.05f, oy + rr * 0.13f, ox + rr * 0.05f, oy + rr * 0.68f, 1.5f, edge);
            c.fillRoundRect(ox - rr * 0.42f, oy + rr * 0.66f, rr * 0.84f, rr * 0.12f, rr * 0.06f, body);
            c.strokeRoundRect(ox - rr * 0.42f, oy + rr * 0.66f, rr * 0.84f, rr * 0.12f, rr * 0.06f, 1.5f, edge);
            // glint
            c.line(ox - rr * 0.42f, oy - rr * 0.6f, ox - rr * 0.36f, oy - rr * 0.1f, 2.6f, 0x99FFFFFF);
            // hairline cracks grow with stress
            int lines = stress > 0.3f ? (stress > 0.65f ? 3 : 2) : 0;
            if (cur == d) {
                for (int k = 0; k < lines; k++) {
                    float a = -0.4f + k * 0.55f;
                    float x0 = ox + (k - 1) * rr * 0.14f, y0 = oy - rr * 0.45f;
                    float x1 = x0 + (float) Math.sin(a) * rr * 0.25f, y1 = y0 + rr * 0.22f;
                    float x2 = x1 - (float) Math.sin(a) * rr * 0.12f, y2 = y1 + rr * 0.24f;
                    c.line(x0, y0, x1, y1, 1.5f, 0xFF3A4350);
                    c.line(x1, y1, x2, y2, 1.5f, 0xFF3A4350);
                }
            }
        }
        // dirt cells drawn by the shared loop use the plate/pan frame, so glass draws its own
        float cell = 2 * rr / G;
        for (int i = 0; i < CELLS; i++) {
            int t = d.ty[i];
            if (t == 0) continue;
            float x = ox - rr + (i % G + 0.5f) * cell + (hs(i, 7) - 0.5f) * cell * 0.25f;
            float y = oy - rr + (i / G + 0.5f) * cell + (hs(i, 8) - 0.5f) * cell * 0.25f;
            float frac = Ui.clamp((d.need[i] - d.e[i]) / d.base[i], 0.12f, 1f);
            c.fillCircle(x, y, cell * 0.78f, al(d.col[i], (0.4f + 0.6f * frac) * alpha));
            if (frac > 0.6f) c.fillCircle(x - cell * 0.22f, y - cell * 0.22f, cell * 0.17f, al(0xFFFFFF, 0.4f * alpha));
        }
    }

    private void drawFoam(Gfx c) {
        for (int i = 0; i < FN; i++) {
            if (fl[i] <= 0) continue;
            float k = fl[i] / fm[i];
            float r = fr[i] * (k < 0.15f ? 1 + (0.15f - k) * 5 : 1);
            c.fillCircle(fx[i], fy[i], r, al(0xFFFFFF, k < 0.15f ? k * 3 : 0.6f));
            if (k >= 0.15f) c.fillCircle(fx[i] - r * 0.3f, fy[i] - r * 0.3f, r * 0.3f, 0xCCFFFFFF);
        }
        // the brush: a ring of suds under the finger shows exactly how wide the wipe is
        if (scrubbing && cur != null && curState == ST_SCRUB) {
            for (int s = 0; s < 8; s++) {
                double a = s * Math.PI / 4 + time * 2;
                c.fillCircle(lx + (float) Math.cos(a) * BR, ly + (float) Math.sin(a) * BR, 3.2f, 0x99FFFFFF);
            }
            c.fillCircle(lx, ly, BR - 3, 0x1CFFFFFF);
        }
    }

    private void drawButtons(Gfx c) {
        // soak bucket
        boolean can = cur != null && curState == ST_SCRUB && soak == null;
        boolean ready = soak != null && soakReady;
        int fill = ready ? 0xFF1F5A3A : soak != null ? 0xFF1F3F66 : can ? 0xFF2C3644 : 0xFF1E2530;
        c.fillRoundRect(soakX, soakY, btnW, btnH, 10, fill);
        int edge = ready ? Ui.GOOD : soak != null ? Ui.BLUE : can ? 0xFF8A94A3 : 0xFF3A4350;
        float pulse = ready ? 0.6f + 0.4f * (float) Math.sin(time * 8) : 1;
        c.strokeRoundRect(soakX, soakY, btnW, btnH, 10, ready ? 3 : 2, al(edge, pulse));
        if (soak != null && !soakReady) {
            float p = soakT / SOAK_TIME;
            c.fillRoundRect(soakX + 4, soakY + btnH - 10, (btnW - 8) * p, 6, 3, Ui.BLUE);
        }
        // tub icon
        float tx0 = soakX + 22, ty0 = soakY + 14;
        int ic = can || soak != null ? 0xFFE9EDF2 : 0xFF566170;
        poly[0] = tx0 - 12;
        poly[1] = ty0;
        poly[2] = tx0 + 12;
        poly[3] = ty0;
        poly[4] = tx0 + 8;
        poly[5] = ty0 + 14;
        poly[6] = tx0 - 8;
        poly[7] = ty0 + 14;
        c.fillPoly(java.util.Arrays.copyOf(poly, 8), ic);
        c.fillCircle(tx0 - 3, ty0 - 4 - (soak != null ? (time * 8) % 4 : 0), 3, al(ic, 0.8f));
        c.fillCircle(tx0 + 5, ty0 - 7, 2.2f, al(ic, 0.8f));
        String sl = ready ? "מוכן!" : soak != null ? "משרה..." : "להשרות";
        c.text(sl, soakX + btnW - 12, soakY + 25, 15, can || soak != null ? Ui.INK : Ui.DIM, Gfx.RIGHT);
        if (soak != null && !soakReady) c.text("לחצו להחזיר", soakX + btnW / 2, soakY - 4, 11, Ui.DIM, Gfx.CENTER);

        // rinse
        float l = left();
        boolean active = cur != null && curState == ST_SCRUB;
        boolean enabled = active && l <= BUTTON_FROM;
        boolean good = active && l <= EASY;
        int rf = good ? 0xFF1F5A3A : enabled ? 0xFF5A4318 : active ? 0xFF2C3644 : 0xFF1E2530;
        c.fillRoundRect(rinseX, soakY, btnW, btnH, 10, rf);
        float rp = good ? 0.65f + 0.35f * (float) Math.sin(time * 7) : 1;
        c.strokeRoundRect(rinseX, soakY, btnW, btnH, 10, good ? 3 : 2, al(good ? Ui.GOOD : enabled ? Ui.ORANGE : active ? 0xFF8A94A3 : 0xFF3A4350, rp));
        float dx0 = rinseX + btnW - 24, dy0 = soakY + 24;
        int dc = active ? 0xFFE9EDF2 : 0xFF566170;
        poly[0] = dx0;
        poly[1] = dy0 - 13;
        poly[2] = dx0 + 9;
        poly[3] = dy0 + 2;
        poly[4] = dx0 + 6;
        poly[5] = dy0 + 10;
        poly[6] = dx0 - 6;
        poly[7] = dy0 + 10;
        poly[8] = dx0 - 9;
        poly[9] = dy0 + 2;
        c.fillPoly(java.util.Arrays.copyOf(poly, 10), dc);
        c.text("שטוף", rinseX + btnW - 40, soakY + 25, 16, active ? Ui.INK : Ui.DIM, Gfx.RIGHT);
        if (good) {
            // check mark
            c.line(rinseX + 9, soakY + 25, rinseX + 14, soakY + 31, 3.5f, Ui.GOOD);
            c.line(rinseX + 14, soakY + 31, rinseX + 23, soakY + 17, 3.5f, Ui.GOOD);
        } else if (enabled) {
            // caution: a visible spot will bounce the dish back
            poly[0] = rinseX + 22;
            poly[1] = soakY + 12;
            poly[2] = rinseX + 33;
            poly[3] = soakY + 33;
            poly[4] = rinseX + 11;
            poly[5] = soakY + 33;
            c.fillPoly(java.util.Arrays.copyOf(poly, 6), Ui.ORANGE);
            c.fillRect(rinseX + 21, soakY + 19, 2.2f, 7, 0xFF12161C);
            c.fillRect(rinseX + 21, soakY + 28, 2.2f, 2.5f, 0xFF12161C);
        }
        // percent badge between the buttons
        if (cur != null && (curState == ST_SCRUB || curState == ST_IN)) {
            int pct = l <= SPOTLESS ? 100 : (int) Math.floor((1 - l) * 100);
            if (pct >= 100 && l > 0) pct = 99;
            c.fillRoundRect(cx - 44, soakY, 88, btnH, 10, 0xCC12161C);
            c.text(pct + "%", cx, soakY + 25, 24, l <= EASY ? Ui.GOOD : Ui.INK, Gfx.CENTER);
            String nm = cur.kind == PAN ? "מחבת" : cur.kind == GLASS ? "כוס יין" : "צלחת";
            c.text(nm, cx, soakY + 40, 12, Ui.DIM, Gfx.CENTER);
        }
    }

    private void drawHud(Gfx c) {
        // time bar on the wall
        float f = Ui.clamp(time / limit, 0, 1);
        float tl0 = limit - time;
        int col = f < 0.6f ? Ui.GOOD : f < 0.85f ? Ui.ACCENT : Ui.BAD;
        c.fillRoundRect(24, lt + 3, 276, 7, 3.5f, 0x55FFFFFF);
        c.fillRoundRect(24, lt + 3, Math.max(7, 276 * (1 - f)), 7, 3.5f, col);
        String ts0 = String.valueOf(Math.max(0, (int) Math.ceil(tl0)));
        boolean hurry = f >= 0.8f;
        c.text(hurry && ((int) (time * 4) % 2 == 0) ? "מהר! " + ts0 : ts0, 336, lt + 11, 13, hurry ? Ui.BAD : Ui.DIM, Gfx.RIGHT);
        // hint line in the middle of the wall
        String hint = null;
        int hc = Ui.INK;
        if (cur != null && curState == ST_SCRUB) {
            boolean fresh = time - lastCleanT < 99 && curT < 7;
            if (!learned && round == 0) hint = "גררו את האצבע על הלכלוך";
            else if (cur.kind == GLASS && (round <= 2 || curT < 4)) hint = "כוס שבירה: לאט ובעדינות";
            else if (cur.kind == PAN && (round <= 2 || curT < 4)) hint = "מחבת שומנית: כמה מעברים";
            else if (cur.hard && (round <= 1 || fresh && curT < 4)) hint = "כתם עקשן: עיגולים או השרייה";
        }
        if (hint != null) {
            float size = 12.5f, w = c.textWidth(hint, size);
            if (w > 172) size = size * 172 / w;
            c.fillRoundRect(180 - 88, lt + bandH - 31, 176, 20, 10, 0x99000000);
            c.text(hint, 180, lt + bandH - 17, size, hc, Gfx.CENTER);
        } else if (cur != null && curState == ST_STAMP) {
            c.text("חוזר לתחתית הערימה", 180, lt + bandH - 17, 12.5f, Ui.BAD, Gfx.CENTER);
        }
    }

    @Override
    void art(Gfx c, float cx0, float cy0, float t) {
        c.fillRoundRect(cx0 - 100, cy0 - 56, 200, 112, 16, 0xFF7B8794);
        c.fillRoundRect(cx0 - 94, cy0 - 50, 188, 100, 12, 0xFF2D7391);
        c.fillCircle(cx0 + 4, cy0 + 4, 46, 0x55000000);
        c.fillCircle(cx0, cy0, 46, 0xFFCDD5E0);
        c.fillCircle(cx0, cy0, 43, 0xFFF5F7FB);
        c.fillCircle(cx0, cy0, 34, 0xFFE3E9F1);
        // left half still dirty, right half shining
        c.fillCircle(cx0 - 22, cy0 - 8, 13, 0xFFD0472E);
        c.fillCircle(cx0 - 12, cy0 + 14, 10, 0xFFE3AA1C);
        c.fillCircle(cx0 - 28, cy0 + 10, 8, 0xFF8C5733);
        poly[0] = cx0 - 14;
        poly[1] = cy0 - 40;
        poly[2] = cx0 + 4;
        poly[3] = cy0 - 40;
        poly[4] = cx0 - 4;
        poly[5] = cy0 + 40;
        poly[6] = cx0 - 22;
        poly[7] = cy0 + 40;
        c.fillPoly(java.util.Arrays.copyOf(poly, 8), 0x22FFFFFF);
        star(c, cx0 + 22, cy0 - 12, 10 + 3 * (float) Math.sin(t * 5), 0xFFFFF4C8);
        star(c, cx0 + 34, cy0 + 14, 6 + 2 * (float) Math.sin(t * 5 + 2), 0xFFFFF4C8);
        for (int i = 0; i < 6; i++) {
            float ph = (t * 0.4f + i * 0.17f) % 1f;
            c.fillCircle(cx0 - 80 + i * 32 + (float) Math.sin(t * 2 + i) * 4, cy0 + 44 - ph * 90, 3 + (i % 3) * 1.5f, al(0xFFFFFF, 0.5f * (1 - ph)));
        }
        // sponge
        float sx = cx0 - 56 + (float) Math.sin(t * 4) * 8;
        c.fillRoundRect(sx - 14, cy0 - 40, 28, 18, 5, 0xFFE9C23B);
        c.fillRoundRect(sx - 14, cy0 - 28, 28, 6, 3, 0xFF4BA36B);
    }

    // =====================================================================================
    // Test bot: a finger that scrubs in strokes (down, many moves, up)
    // =====================================================================================

    @Override
    void bot(Random r, float skill) {
        if (result != RUNNING) return;
        if (cur == null || curState != ST_SCRUB) {
            if (bDown) {
                up(bx, by);
                bDown = false;
            }
            bDish = null;
            return;
        }
        if (bDish != cur) {
            bDish = cur;
            bDelay = 0.15f + (1 - skill) * 0.5f * r.nextFloat();
            bSoakChecked = false;
            if (bDown) {
                up(bx, by);
                bDown = false;
            }
        }
        if (bDelay > 0) {
            bDelay -= BDT;
            return;
        }
        float l = left();

        if (!bSoakChecked) {
            bSoakChecked = true;
            float hard = 0;
            for (int i = 0; i < CELLS; i++) if (cur.ty[i] == CRUST || cur.ty[i] == GREASE) hard += cur.need[i] - cur.e[i];
            if (soak == null && cur.kind != GLASS && hard > 14 && r.nextFloat() < skill * 1.15f - 0.15f) {
                if (bDown) {
                    up(bx, by);
                    bDown = false;
                }
                down(soakX + 30, soakY + 20);
                up(soakX + 30, soakY + 20);
                return;
            }
        }
        if (soak != null && soakReady && skill > 0.5f && l > 0.5f && r.nextFloat() < 0.1f) {
            if (bDown) {
                up(bx, by);
                bDown = false;
            }
            down(soakX + 30, soakY + 20);
            up(soakX + 30, soakY + 20);
            return;
        }
        // impatience: sending a dish out early
        float press = 0;
        if (l <= BUTTON_FROM) {
            if (l <= EASY) press = (1 - skill) * 0.05f;
            else if (skill < 0.5f) press = (0.5f - skill) * 0.1f;
        }
        if (press > 0 && r.nextFloat() < press) {
            if (bDown) {
                up(bx, by);
                bDown = false;
            }
            down(rinseX + 40, soakY + 20);
            up(rinseX + 40, soakY + 20);
            return;
        }
        if (l <= NEAR && !bDown) {
            // satisfied; the auto-send will take it after a short idle
            if (r.nextFloat() < (1 - skill) * 0.2f + 0.02f) {
                bDelay = 0.9f;
                return;
            }
        }

        if (!bDown) {
            startStroke(r, skill);
            return;
        }
        advanceStroke(r, skill);
    }

    private void startStroke(Random r, float skill) {
        Dish d = cur;
        float ox = originX(d);
        int pick = -1;
        if (r.nextFloat() < 0.15f + 0.85f * skill) {
            int tries = 1 + (int) (skill * 8);
            float best = -1;
            for (int k = 0; k < tries; k++) {
                int idx = r.nextInt(CELLS);
                for (int s = 0; s < CELLS; s++) {
                    int j = (idx + s) % CELLS;
                    if (d.ty[j] != 0) {
                        float rem = d.need[j] - d.e[j];
                        if (rem > best) {
                            best = rem;
                            pick = j;
                        }
                        break;
                    }
                }
            }
        }
        float tx0, ty0;
        if (pick >= 0) {
            tx0 = ox + cu(pick % G) * R;
            ty0 = cy + cu(pick / G) * R;
        } else {
            double a = r.nextDouble() * 6.283, rr = Math.sqrt(r.nextDouble()) * R * 0.85;
            tx0 = ox + (float) Math.cos(a) * (float) rr;
            ty0 = cy + (float) Math.sin(a) * (float) rr;
        }
        bV = (240 + 260 * skill) * (0.8f + 0.4f * r.nextFloat());
        if (d.kind == GLASS) bV = skill > 0.45f ? Math.min(bV, 300 + 40 * skill) : 640 + 380 * r.nextFloat();
        bCircle = pick >= 0 && d.ty[pick] == CRUST && skill > 0.3f;
        if (bCircle) {
            bCx = tx0;
            bCy = ty0;
            bRad = 8 + 4 * r.nextFloat();
            bAng = r.nextFloat() * 6.28f;
            bT = 0;
            bSx = bCx + (float) Math.cos(bAng) * bRad;
            bSy = bCy + (float) Math.sin(bAng) * bRad;
        } else {
            double ang = skill > 0.5f ? (r.nextBoolean() ? 0 : 3.14159) + (r.nextFloat() - 0.5f) * 0.4f : r.nextDouble() * 6.283;
            if (pick >= 0 && skill > 0.5f && r.nextFloat() < 0.4f) ang = 1.5708;
            float len = 34 + 34 * skill;
            bSx = tx0 - (float) Math.cos(ang) * len / 2;
            bSy = ty0 - (float) Math.sin(ang) * len / 2;
            bEx = tx0 + (float) Math.cos(ang) * len / 2;
            bEy = ty0 + (float) Math.sin(ang) * len / 2;
            bLegs = skill > 0.2f ? 2 + (r.nextFloat() < skill ? 1 : 0) : 1;
        }
        bx = clampX(bSx, ox);
        by = clampY(bSy);
        bSx = bx;
        bSy = by;
        bEx = clampX(bEx, ox);
        bEy = clampY(bEy);
        down(bx, by);
        bDown = true;
    }

    private float clampX(float x, float ox) {
        float dx = x - ox;
        float lim = R * 0.92f;
        return ox + Ui.clamp(dx, -lim, lim);
    }

    private float clampY(float y) {
        return Ui.clamp(y, cy - R * 0.92f, cy + R * 0.92f);
    }

    private void advanceStroke(Random r, float skill) {
        float step = bV * BDT;
        if (bCircle) {
            bT += BDT;
            bAng += step / bRad;
            bx = bCx + (float) Math.cos(bAng) * bRad;
            by = bCy + (float) Math.sin(bAng) * bRad;
            move(bx, by);
            boolean gone = true;
            Dish d = cur;
            for (int i = 0; i < CELLS && gone; i++) {
                if (d.ty[i] != CRUST) continue;
                float ddx = ox(d) + cu(i % G) * R - bCx, ddy = cy + cu(i / G) * R - bCy;
                if (ddx * ddx + ddy * ddy < 30 * 30) gone = false;
            }
            if (gone || bT > 1.8f) {
                up(bx, by);
                bDown = false;
            }
            return;
        }
        float dx = bEx - bx, dy = bEy - by;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d <= step) {
            bx = bEx;
            by = bEy;
            move(bx, by);
            bLegs--;
            if (bLegs <= 0) {
                up(bx, by);
                bDown = false;
            } else {
                float tx0 = bSx, ty0 = bSy;
                bSx = bEx;
                bSy = bEy;
                bEx = tx0;
                bEy = ty0;
            }
        } else {
            bx += dx / d * step;
            by += dy / d * step;
            move(bx, by);
        }
    }

    private float ox(Dish d) {
        return originX(d);
    }
}
