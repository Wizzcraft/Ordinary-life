package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Software-developer shift: four releases, each a bug hunt on a little production line. A block
 * (shape + colour) travels through a chain of stations. A paint station should turn it the colour
 * of its drop; a stamp station should give it the shape of its outline. Under every station a chip
 * shows what the block actually looked like when it came out. A station whose chip does not follow
 * from its symbol is the bug: tap it before the release timer runs out, then press ship.
 *
 * Bug kinds: WRONG (applies a different value), DEAD (does nothing) and SIDE (does its job but also
 * spoils the other attribute). Later releases are longer, hold two bugs and a red herring (a station
 * that flashes and sparks but behaves). The decision: a station whose symbol already matches the
 * incoming block cannot be proven by this test, because a dead station looks the same. Ship now for a
 * bonus (PERFECT needs a clean, early release) and risk a crash, or pay time for one more test run
 * with a different block that exposes everything.
 *
 * Every flow is generated and proven by simulation: the bug set is exactly the stations whose chip
 * breaks the local rule once all visible bugs are fixed, and a test input exists that exposes the rest.
 * The test bot only reads what the player sees (symbols, chips, the input block).
 */
final class DevMini extends Mini {
    private static final int K = 5;
    private static final int OKAY = 0, WRONG = 1, DEAD = 2, SIDE = 3;
    private static final int BOOT = 0, RUN = 1, IDLE = 2, SHIP = 3;
    private static final int MAXN = 8;
    private static final float DWELL = 0.12f, BOOT_T = 0.6f, PERF_FRAC = 0.33f, TEST_COST = 1.5f, FALSE_COST = 0.9f;
    private static final float LATENT_P = 0.34f, ELBOW = 26;
    private static final int PANEL_T = 0xFF28313E, CHIP_BG = 0xFF1A212B, WIRE = 0xFF303B4A, WIRE_LIT = 0xFF5F86B5;

    private static final class St {
        boolean paint, noisy, fixed;
        int val, bug, off;
    }

    private St[] st = new St[0];
    private int n, round;
    private float diff;
    private int inS, inC, testS, testC;
    private int curInS, curInC;
    private boolean testUsed;
    private final int[] chS = new int[MAXN], chC = new int[MAXN];
    private final boolean[] shown = new boolean[MAXN];
    private final float[] pop = new float[MAXN], flashBad = new float[MAXN], flashGood = new float[MAXN],
            pulse = new float[MAXN], lamp = new float[MAXN], squash = new float[MAXN];
    /** True once the station's pilot lamp has stayed dark while the block went through. */
    private final boolean[] dark = new boolean[MAXN];
    private int revealed, phase, falseAlarms, shipResult, lastSec;
    private float tok, dwell, step, bootT, shipT, time, timeLeft, limit, runTime, duckT;
    private int duckMood, curS, curC;
    private boolean shipFx, forced;

    // layout, refreshed every draw and lazily before the first touch
    private boolean laid;
    private float wallTop;
    private float monY, monH, tile, chip, rowPitch, boardY, deskTop, deskBot, btnY, btnH;
    private final float[] seg = new float[8];
    private int segN;
    private final int[] blk = new int[3];

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("מפתח", "מפתחת");
    }

    @Override
    String howTo() {
        return "הבלוק עובר תחנה אחרי תחנה, וכל תחנה צריכה לעשות מה שהסמל שלה מראה. "
                + "תחנה שמשבשת אותו היא התקלה: לוחצים עליה. אחר כך לשחרר, או לבדוק עוד פעם.";
    }

    // ------------------------------------------------------------------ the rules of a station

    /** What station k really does to a block (s, c), encoded s * K + c. */
    private static int outOf(St k, boolean fixed, int s, int c) {
        int bug = fixed ? OKAY : k.bug;
        int os = s, oc = c;
        switch (bug) {
            case OKAY:
                if (k.paint) oc = k.val;
                else os = k.val;
                break;
            case WRONG:
                if (k.paint) oc = (k.val + k.off) % K;
                else os = (k.val + k.off) % K;
                break;
            case SIDE:
                if (k.paint) {
                    oc = k.val;
                    os = (s + k.off) % K;
                } else {
                    os = k.val;
                    oc = (c + k.off) % K;
                }
                break;
            default:
                break; // DEAD: the block passes through untouched
        }
        return os * K + oc;
    }

    private static int expectOf(St k, int s, int c) {
        return outOf(k, true, s, c);
    }

    /**
     * Simulates the whole line for an input block with no fixes, then repeatedly fixes whatever is
     * visibly wrong (a chip that breaks its station's rule). Returns {mismatches seen at first, bugs that
     * stayed invisible}.
     */
    private static int[] fixLoop(St[] s, int a, int b) {
        int len = s.length, initial = 0;
        boolean[] fx = new boolean[len];
        for (int pass = 0; pass < 12; pass++) {
            int ps = a, pc = b, found = 0;
            for (int i = 0; i < len; i++) {
                int o = outOf(s[i], fx[i], ps, pc);
                if (o != expectOf(s[i], ps, pc)) {
                    if (pass == 0) initial++;
                    if (s[i].bug != OKAY && !fx[i]) {
                        fx[i] = true;
                        found++;
                    }
                }
                ps = o / K;
                pc = o % K;
            }
            if (found == 0) break;
        }
        int left = 0;
        for (int i = 0; i < len; i++) if (s[i].bug != OKAY && !fx[i]) left++;
        return new int[]{initial, left};
    }

    private static int otherThan(Random r, int v) {
        return (v + 1 + r.nextInt(K - 1)) % K;
    }

    // ------------------------------------------------------------------ generation

    private void generate(Random r) {
        for (int tries = 0; tries < 800; tries++) if (tryGenerate(r)) return;
        fallback();
    }

    private boolean tryGenerate(Random r) {
        int vb = round >= 2 || (round == 1 && diff > 0.7f) ? 2 : 1;
        int uWant = round == 0 ? 0 : round == 1 ? r.nextInt(2) : 1 + r.nextInt(2);
        boolean herring = round >= 2 || (round == 1 && diff > 0.5f);
        int len = 4 + round;
        St[] s = new St[len];
        int paints = 0;
        for (int i = 0; i < len; i++) {
            s[i] = new St();
            s[i].paint = r.nextBoolean();
            if (s[i].paint) paints++;
        }
        if (paints == 0 || paints == len) return false;
        for (int i = 2; i < len; i++) if (s[i].paint == s[i - 1].paint && s[i].paint == s[i - 2].paint) return false;
        int iS = r.nextInt(K), iC = r.nextInt(K);
        boolean unP = false, unS = false;
        if (uWant == 2) {
            unP = true;
            unS = true;
        } else if (uWant == 1) {
            if (r.nextBoolean()) unP = true;
            else unS = true;
        }
        int lastS = iS, lastC = iC, firstP = -1, firstS = -1;
        for (int i = 0; i < len; i++) {
            St k = s[i];
            if (k.paint) {
                boolean first = firstP < 0;
                if (first) firstP = i;
                k.val = first && unP ? lastC : otherThan(r, lastC);
                lastC = k.val;
            } else {
                boolean first = firstS < 0;
                if (first) firstS = i;
                k.val = first && unS ? lastS : otherThan(r, lastS);
                lastS = k.val;
            }
        }
        boolean[] special = new boolean[len];
        if (unP) special[firstP] = true;
        if (unS) special[firstS] = true;
        boolean[] isBug = new boolean[len];
        int latentIdx = -1;
        int[] cand = r.nextBoolean() ? new int[]{firstP, firstS} : new int[]{firstS, firstP};
        for (int idx : cand) {
            if (idx >= 0 && special[idx] && latentIdx < 0 && r.nextFloat() < LATENT_P) latentIdx = idx;
        }
        if (latentIdx >= 0) {
            s[latentIdx].bug = DEAD;
            isBug[latentIdx] = true;
        }
        int placed = 0;
        for (int tries = 0; tries < 40 && placed < vb; tries++) {
            int idx = r.nextInt(len);
            if (isBug[idx] || special[idx]) continue;
            float roll = r.nextFloat();
            int kind = round == 0 ? (roll < 0.55f ? WRONG : DEAD) : roll < 0.4f ? WRONG : roll < 0.65f ? DEAD : SIDE;
            s[idx].bug = kind;
            s[idx].off = 1 + r.nextInt(K - 1);
            isBug[idx] = true;
            placed++;
        }
        if (placed < vb) return false;
        if (herring) {
            for (int tries = 0; tries < 30; tries++) {
                int idx = r.nextInt(len);
                if (!isBug[idx]) {
                    s[idx].noisy = true;
                    break;
                }
            }
        }
        int[] res = fixLoop(s, iS, iC);
        if (res[0] < 1 || res[1] != (latentIdx >= 0 ? 1 : 0)) return false;
        int ts = -1, tc = -1;
        for (int tries = 0; tries < 80 && ts < 0; tries++) {
            int a = r.nextInt(K), b = r.nextInt(K);
            if (firstS >= 0 && a == s[firstS].val) continue;
            if (firstP >= 0 && b == s[firstP].val) continue;
            if (a == iS && b == iC) continue;
            if (fixLoop(s, a, b)[1] == 0) {
                ts = a;
                tc = b;
            }
        }
        if (ts < 0) return false;
        commit(s, iS, iC, ts, tc);
        return true;
    }

    /** A flow that is always valid: alternating stations, one wrong-value bug in the middle. */
    private void fallback() {
        int len = 4 + round;
        St[] s = new St[len];
        int pv = 0, sv = 0;
        for (int i = 0; i < len; i++) {
            s[i] = new St();
            s[i].paint = i % 2 == 0;
            if (s[i].paint) s[i].val = pv = (pv + 1) % K;
            else s[i].val = sv = (sv + 1) % K;
        }
        s[1].bug = WRONG;
        s[1].off = 2;
        commit(s, 0, 0, 3, 3);
    }

    private void commit(St[] s, int iS, int iC, int ts, int tc) {
        st = s;
        n = s.length;
        inS = iS;
        inC = iC;
        testS = ts;
        testC = tc;
    }

    // ------------------------------------------------------------------ round

    @Override
    void begin(int round, float difficulty) {
        this.round = Math.max(0, Math.min(3, round));
        diff = difficulty;
        generate(h.rnd());
        curInS = inS;
        curInC = inC;
        testUsed = false;
        recompute(false);
        for (int i = 0; i < MAXN; i++) {
            shown[i] = false;
            pop[i] = flashBad[i] = flashGood[i] = pulse[i] = lamp[i] = squash[i] = 0;
            dark[i] = false;
            bSig[i] = -1;
            bVerd[i] = 0;
        }
        revealed = 0;
        tok = 0;
        dwell = 0;
        bootT = 0;
        shipT = 0;
        shipFx = false;
        forced = false;
        falseAlarms = 0;
        phase = BOOT;
        curS = inS;
        curC = inC;
        duckT = 0;
        duckMood = 0;
        botDecideAt = -1;
        step = Ui.clamp(0.62f - 0.05f * this.round - 0.1f * diff, 0.36f, 0.7f);
        runTime = (n + 1) * step + n * DWELL;
        int bugs = 0;
        for (St k : st) if (k.bug != OKAY) bugs++;
        limit = BOOT_T + runTime + 6.5f + 1.0f * Math.min(bugs, 3);
        timeLeft = limit;
        lastSec = (int) Math.ceil(limit);
    }

    /** Recomputes every chip from the current input and fixes; chips that change pulse. */
    private void recompute(boolean announce) {
        int ps = curInS, pc = curInC;
        for (int i = 0; i < n; i++) {
            int o = outOf(st[i], st[i].fixed, ps, pc);
            int os = o / K, oc = o % K;
            if (announce && (os != chS[i] || oc != chC[i])) pulse[i] = 0.6f;
            chS[i] = os;
            chC[i] = oc;
            ps = os;
            pc = oc;
        }
    }

    private int bugsLeft() {
        int left = 0;
        for (St k : st) if (k.bug != OKAY && !k.fixed) left++;
        return left;
    }

    private boolean canTest() {
        return phase == IDLE && !testUsed && timeLeft > runTime + TEST_COST + 1.2f;
    }

    @Override
    int update(float dt) {
        time += dt;
        if (duckT > 0) duckT -= dt;
        for (int i = 0; i < MAXN; i++) {
            if (pop[i] > 0) pop[i] -= dt;
            if (flashBad[i] > 0) flashBad[i] -= dt;
            if (flashGood[i] > 0) flashGood[i] -= dt;
            if (pulse[i] > 0) pulse[i] -= dt;
            if (lamp[i] > 0) lamp[i] -= dt;
            if (squash[i] > 0) squash[i] -= dt;
        }
        if (phase != SHIP) {
            timeLeft -= dt;
            int sec = (int) Math.ceil(timeLeft);
            if (sec < lastSec) {
                lastSec = sec;
                if (sec <= 3 && sec >= 1) h.sfx(Sfx.TICK);
            }
            if (timeLeft <= 0) {
                timeLeft = 0;
                h.text(180, deskTop - 4, "נגמר הזמן!", Ui.BAD);
                startShip(true);
            }
        }
        switch (phase) {
            case BOOT:
                bootT += dt;
                if (bootT >= BOOT_T) {
                    phase = RUN;
                    h.sfx(Sfx.BEEP);
                }
                break;
            case RUN:
                if (dwell > 0) {
                    dwell -= dt;
                } else {
                    tok += dt / step;
                }
                if (revealed < n && tok >= revealed + 1) {
                    tok = revealed + 1;
                    reveal(revealed);
                    revealed++;
                    dwell = DWELL;
                }
                if (revealed >= n && dwell <= 0 && tok >= n + 1) {
                    tok = n + 1;
                    phase = IDLE;
                }
                break;
            case SHIP:
                shipT += dt;
                if (!shipFx && shipT >= 0.45f) {
                    shipFx = true;
                    shipFx();
                }
                if (shipT >= 1.25f) return shipResult;
                break;
            default:
                break;
        }
        return RUNNING;
    }

    private void reveal(int i) {
        shown[i] = true;
        pop[i] = 0.35f;
        // A dead station never lights its pilot lamp: the one tell that survives a lucky test block.
        dark[i] = st[i].bug == DEAD && !st[i].fixed;
        lamp[i] = dark[i] ? 0 : 0.4f;
        curS = chS[i];
        curC = chC[i];
        h.sfx(Sfx.TICK);
    }

    private void startShip(boolean forcedShip) {
        if (phase == SHIP) return;
        phase = SHIP;
        shipT = 0;
        forced = forcedShip;
        float frac = Math.max(0, timeLeft) / limit;
        if (bugsLeft() > 0) shipResult = MISS;
        else shipResult = !forced && falseAlarms == 0 && frac >= PERF_FRAC ? PERFECT : OK;
        h.sfx(Sfx.WHOOSH);
    }

    private void shipFx() {
        float x = cellX(n + 1), y = cellY(n + 1);
        if (shipResult == PERFECT) {
            h.text(180, y - 34, "שחרור מושלם!", Ui.ACCENT);
            h.burst(x, y, Ui.ACCENT, 16);
            h.sfx(Sfx.PERFECT);
            duckMood = 1;
            duckT = 1.2f;
        } else if (shipResult == OK) {
            h.text(180, y - 34, "שוחרר", Ui.GOOD);
            h.burst(x, y, Ui.GOOD, 10);
            h.sfx(Sfx.GOOD);
            duckMood = 1;
            duckT = 0.9f;
        } else {
            h.text(180, y - 34, "נשאר באג!", Ui.BAD);
            h.burst(x, y, Ui.BAD, 14);
            h.shake(9);
            h.sfx(Sfx.SIREN);
            duckMood = 3;
            duckT = 1.2f;
        }
    }

    // ------------------------------------------------------------------ touch

    @Override
    void down(float x, float y) {
        if (!laid) lay(Game.HUD_H + 74, h.screenH() - 96);
        if (phase == SHIP) return;
        if (Ui.in(x, y, 166, btnY, 178, btnH)) {
            if (phase == IDLE) startShip(false);
            else h.text(260, btnY - 4, "הבנייה עוד רצה", Ui.DIM);
            return;
        }
        if (Ui.in(x, y, 16, btnY, 138, btnH)) {
            if (canTest()) startTest();
            else if (testUsed) h.text(85, btnY - 4, "כבר נבדק", Ui.DIM);
            else if (phase == IDLE) h.text(85, btnY - 4, "אין זמן לעוד בדיקה", Ui.DIM);
            return;
        }
        for (int i = 0; i < n; i++) {
            float cx = cellX(i + 1);
            if (Math.abs(x - cx) <= 31 && y >= cellY(i + 1) - tile / 2 - 4 && y <= cellY(i + 1) + tile / 2 + 5 + chip + 4) {
                tapStation(i);
                return;
            }
        }
    }

    private void tapStation(int i) {
        float x = cellX(i + 1), y = cellY(i + 1) - tile / 2 - 8;
        St k = st[i];
        if (!shown[i]) {
            h.text(x, y, "עוד לא רץ", Ui.DIM);
            h.sfx(Sfx.TICK);
            return;
        }
        if (k.fixed) {
            h.sfx(Sfx.TICK);
            return;
        }
        if (k.bug != OKAY) {
            k.fixed = true;
            recompute(true);
            flashGood[i] = 0.6f;
            squash[i] = 0.9f;
            h.text(x, y, "תוקן!", Ui.GOOD);
            h.burst(x, cellY(i + 1), Ui.GOOD, 10);
            h.sfx(Sfx.DING);
            duckT = 0.6f;
            duckMood = 1;
        } else {
            falseAlarms++;
            timeLeft = Math.max(0.2f, timeLeft - FALSE_COST);
            flashBad[i] = 0.5f;
            h.text(x, y, "זה תקין", Ui.BAD);
            h.text(180, deskTop - 6, "-" + FALSE_COST, Ui.BAD);
            h.shake(3);
            h.sfx(Sfx.BAD);
            duckT = 0.8f;
            duckMood = 3;
        }
    }

    private void startTest() {
        testUsed = true;
        timeLeft = Math.max(0.2f, timeLeft - TEST_COST);
        h.text(180, deskTop - 6, "-" + TEST_COST, Ui.ORANGE);
        h.text(cellX(0), cellY(0) - tile / 2 - 6, "קלט חדש", Ui.BLUE);
        h.sfx(Sfx.WHOOSH);
        curInS = testS;
        curInC = testC;
        recompute(false);
        for (int i = 0; i < MAXN; i++) {
            shown[i] = false;
            dark[i] = false;
            bSig[i] = -1;
            bVerd[i] = 0;
        }
        revealed = 0;
        tok = 0;
        dwell = 0;
        curS = curInS;
        curC = curInC;
        phase = RUN;
        botDecideAt = -1;
    }

    // ------------------------------------------------------------------ layout

    private float cellX(int p) {
        int col = p < 5 ? p : 9 - p;
        return 304 - 62 * col;
    }

    private float cellY(int p) {
        return boardY + (p < 5 ? 0 : rowPitch) + tile / 2;
    }

    private void lay(float top, float bottom) {
        laid = true;
        wallTop = top;
        float hh = bottom - top;
        float sc = Ui.clamp(hh / 294f, 1f, 1.5f);
        tile = Ui.clamp(40 * sc, 40, 50);
        chip = Ui.clamp(26 * sc, 26, 36);
        float rowGap = 9 * sc, rp0 = tile + 5 * sc + chip + rowGap;
        float monH0 = 6 + 16 + 6 + 2 * rp0 - rowGap + 12;
        float stand = 6;
        btnH = Ui.clamp(48 * sc, 48, 56);
        float fixed = stand + btnH + 15;
        float spare = hh - monH0 - fixed - 44;
        float add = Ui.clamp(spare * 0.35f, 0, 30);
        rowPitch = rp0 + add;
        monH = monH0 + add;
        float deskH = Ui.clamp(hh - monH - fixed, 40, 110);
        btnY = bottom - 4 - btnH;
        deskBot = btnY - 5;
        deskTop = deskBot - deskH;
        monY = deskTop - stand + 2 - monH;
        float boardH = rowPitch + tile + 5 * sc + chip;
        float usableTop = monY + 6 + 16, usableH = monH - 12 - 16;
        boardY = usableTop + Math.max(0, (usableH - boardH) / 2);
    }

    /** The polyline of wire p (cell p to cell p + 1) into seg / segN. */
    private void segPts(int p) {
        float x0 = cellX(p), y0 = cellY(p), x1 = cellX(p + 1), y1 = cellY(p + 1);
        seg[0] = x0;
        seg[1] = y0;
        if (p == 4) {
            seg[2] = ELBOW;
            seg[3] = y0;
            seg[4] = ELBOW;
            seg[5] = y1;
            seg[6] = x1;
            seg[7] = y1;
            segN = 4;
        } else {
            seg[2] = x1;
            seg[3] = y1;
            segN = 2;
        }
    }

    private float segLen() {
        float len = 0;
        for (int i = 0; i + 1 < segN; i++) {
            len += Math.abs(seg[i * 2 + 2] - seg[i * 2]) + Math.abs(seg[i * 2 + 3] - seg[i * 2 + 1]);
        }
        return len;
    }

    /** Draws the first fraction f of the current seg polyline. */
    private void drawSeg(Gfx c, float f, float w, int col) {
        float left = f * segLen();
        for (int i = 0; i + 1 < segN && left > 0; i++) {
            float ax = seg[i * 2], ay = seg[i * 2 + 1], bx = seg[i * 2 + 2], by = seg[i * 2 + 3];
            float l = Math.abs(bx - ax) + Math.abs(by - ay);
            float t = l <= 0 ? 1 : Math.min(1, left / l);
            c.line(ax, ay, ax + (bx - ax) * t, ay + (by - ay) * t, w, col);
            left -= l;
        }
    }

    private float tokX, tokY;

    private void tokenXY(float s) {
        int p = Math.max(0, Math.min(n, (int) Math.floor(s)));
        float f = Math.max(0, Math.min(1, s - p));
        segPts(p);
        float len = segLen(), left = f * len;
        tokX = seg[0];
        tokY = seg[1];
        for (int i = 0; i + 1 < segN; i++) {
            float ax = seg[i * 2], ay = seg[i * 2 + 1], bx = seg[i * 2 + 2], by = seg[i * 2 + 3];
            float l = Math.abs(bx - ax) + Math.abs(by - ay);
            if (left <= l || i + 2 >= segN) {
                float t = l <= 0 ? 1 : Math.min(1, left / l);
                tokX = ax + (bx - ax) * t;
                tokY = ay + (by - ay) * t;
                return;
            }
            left -= l;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void draw(Gfx c, float top, float bottom) {
        lay(top, bottom);
        drawWall(c);
        DevStudyMini.monitor(c, 14, monY, 332, monH, time);
        DevStudyMini.stand(c, 180, monY + monH, 6);
        float sx = 22, sy = monY + 8, sw = 316, sh = monH - 16;
        DevStudyMini.codeBackdrop(c, sx, sy + 18, sw, sh - 22, time);
        // title bar of the "program"
        c.fillRect(sx, sy, sw, 16, 0xFF151C26);
        c.fillCircle(sx + 10, sy + 8, 3, 0xFFE5484D);
        c.fillCircle(sx + 21, sy + 8, 3, 0xFFF0B429);
        c.fillCircle(sx + 32, sy + 8, 3, 0xFF3FB68B);
        c.text("קו ייצור", sx + sw - 8, sy + 12, 12, Ui.DIM, Gfx.RIGHT);

        // wires first, stations on top
        for (int p = 0; p <= n; p++) {
            segPts(p);
            drawSeg(c, 1, 4, WIRE);
            float lit = Ui.clamp(tok - p, 0, 1);
            if (phase == IDLE || phase == SHIP) lit = 1;
            if (lit > 0) drawSeg(c, lit, 4, WIRE_LIT);
            if (segN == 2) {
                float mx = (seg[0] + seg[2]) / 2, my = seg[1], dir = seg[2] < seg[0] ? -1 : 1;
                c.fillPoly(new float[]{mx + dir * 4, my, mx - dir * 3, my - 4, mx - dir * 3, my + 4}, lit >= 1 ? 0xFF9CBCE4 : 0xFF46556A);
            }
        }
        drawInput(c);
        for (int i = 0; i < n; i++) drawStation(c, i);
        drawOutput(c);
        if (phase != SHIP) drawToken(c);
        for (int i = 0; i < n; i++) {
            if (squash[i] <= 0) continue;
            float u = 1 - squash[i] / 0.9f;
            bugGlyph(c, cellX(i + 1) + (float) Math.sin(u * 9) * 5, cellY(i + 1) - tile / 2 - 4 - u * 26, 1f,
                    DevStudyMini.argb((int) (230 * (1 - u)), Ui.BAD), time);
        }
        if (phase == SHIP && shipResult == MISS && shipT > 0.45f) {
            for (int i = 0; i < n; i++) {
                if (st[i].bug == OKAY || st[i].fixed) continue;
                float x = cellX(i + 1), y = cellY(i + 1);
                float p = 0.5f + 0.5f * (float) Math.sin(time * 12);
                c.strokeRoundRect(x - tile / 2 - 3, y - tile / 2 - 3, tile + 6, tile + 6, 11, 3, DevStudyMini.argb(120 + (int) (130 * p), Ui.BAD));
                bugGlyph(c, x, y - tile / 2 - 10, 1.1f, Ui.BAD, time);
            }
        }

        drawDesk(c);
        drawButtons(c);
    }

    /** When the screen is tall there is wall above the monitor: a poster and a clock that runs down with the timer. */
    private void drawWall(Gfx c) {
        float gap = monY - wallTop;
        if (gap < 34) return;
        float sz = Math.min(gap - 14, 64), y0 = monY - 8 - sz;
        c.fillRoundRect(30, y0, sz * 1.15f, sz, 5, 0xFF3A2F26);
        c.fillRect(34, y0 + 4, sz * 1.15f - 8, sz - 8, 0xFF2C4A66);
        c.fillCircle(30 + sz * 0.8f, y0 + sz * 0.32f, sz * 0.11f, 0xFFFFC857);
        c.fillPoly(new float[]{34, y0 + sz - 4, 34 + sz * 0.45f, y0 + sz * 0.38f, 34 + sz * 0.8f, y0 + sz - 4}, 0xFF23384D);
        c.fillPoly(new float[]{34 + sz * 0.4f, y0 + sz - 4, 34 + sz * 0.78f, y0 + sz * 0.5f, 30 + sz * 1.15f - 4, y0 + sz - 4}, 0xFF1B2D3E);
        float r = sz * 0.42f, cx = 318 - r * 0.3f, cy = y0 + sz / 2;
        c.fillCircle(cx, cy, r + 3, 0xFF3A2F26);
        c.fillCircle(cx, cy, r, 0xFFE9EDF2);
        float frac = Ui.clamp(timeLeft / limit, 0, 1);
        int dots = 24;
        for (int i = 0; i < dots; i++) {
            if (i / (float) dots > frac) break;
            double ang = -Math.PI / 2 + i * 2 * Math.PI / dots;
            c.fillCircle(cx + (float) Math.cos(ang) * r * 0.72f, cy + (float) Math.sin(ang) * r * 0.72f, 2.2f, frac > 0.18f ? 0xFF3E8EDE : 0xFFE5484D);
        }
        double hand = -Math.PI / 2 + time * 2;
        c.line(cx, cy, cx + (float) Math.cos(hand) * r * 0.55f, cy + (float) Math.sin(hand) * r * 0.55f, 1.6f, 0xFF28313E);
        c.fillCircle(cx, cy, 2.2f, 0xFF28313E);
    }

    private void drawInput(Gfx c) {
        float x = cellX(0), y = cellY(0);
        c.fillRoundRect(x - tile / 2, y - tile / 2, tile, tile, 9, 0xFF121820);
        c.strokeRoundRect(x - tile / 2, y - tile / 2, tile, tile, 9, 1.5f, 0xFF3A6EA5);
        blk[0] = curInS;
        blk[1] = curInC;
        blk[2] = -1;
        float s = tile * 0.8f;
        DevStudyMini.drawBlock(c, x, y, s, blk, CHIP_BG, 0xFF3A6EA5, 1.5f);
        c.text("קלט", x, y + tile / 2 + 5 + chip * 0.6f, 12, Ui.BLUE, Gfx.CENTER);
    }

    private void drawOutput(Gfx c) {
        float x = cellX(n + 1), y = cellY(n + 1);
        int box = shipResult == MISS && phase == SHIP && shipT > 0.45f ? 0xFF8E3B3B : 0xFF8A6A3F;
        float lid = phase == SHIP && shipT > 0.45f && shipResult != MISS ? -6 : 0;
        c.fillRoundRect(x - tile / 2, y - tile / 2 + 8, tile, tile - 8, 6, box);
        c.fillRect(x - 4, y - tile / 2 + 8, 8, tile - 8, 0x33FFFFFF);
        c.fillRoundRect(x - tile / 2 - 3, y - tile / 2 + lid, tile + 6, 11, 4, Ui.darken(box, 1.2f));
        c.fillRect(x - 4, y - tile / 2 + lid, 8, 11, 0x33FFFFFF);
        if (phase == SHIP && shipT > 0.45f && shipResult != MISS) {
            c.fillCircle(x, y - tile / 2 - 6, 5 + 3 * (float) Math.sin(time * 10), DevStudyMini.argb(150, shipResult == PERFECT ? Ui.ACCENT : Ui.GOOD));
        }
        c.text("פלט", x, y + tile / 2 + 5 + chip * 0.6f, 12, Ui.DIM, Gfx.CENTER);
    }

    private void drawStation(Gfx c, int i) {
        St k = st[i];
        float cx = cellX(i + 1), cy = cellY(i + 1);
        float wob = k.noisy ? (float) Math.sin(time * 31 + i * 2) * 0.9f : 0;
        float chipCy = cy + tile / 2 + 5 + chip / 2;
        c.line(cx, cy + tile / 2, cx, chipCy - chip / 2, 1.5f, 0xFF3A4656);
        int edge = k.fixed ? Ui.GOOD : DevStudyMini.TILE_EDGE;
        float ew = k.fixed ? 2f : 1.5f;
        if (flashBad[i] > 0) {
            edge = Ui.BAD;
            ew = 3.5f;
        }
        if (flashGood[i] > 0) ew = 4f;
        c.fillRoundRect(cx - tile / 2 + wob, cy - tile / 2, tile, tile, 9, PANEL_T);
        c.strokeRoundRect(cx - tile / 2 + wob, cy - tile / 2, tile, tile, 9, ew, edge);
        float ix = cx + wob;
        if (k.paint) {
            int col = DevStudyMini.PAL[k.val];
            c.fillCircle(ix, cy + tile * 0.1f, tile * 0.21f, col);
            c.fillPoly(new float[]{ix, cy - tile * 0.34f, ix + tile * 0.17f, cy + tile * 0.0f, ix - tile * 0.17f, cy + tile * 0.0f}, col);
            c.fillCircle(ix - tile * 0.07f, cy + tile * 0.06f, tile * 0.05f, 0x55FFFFFF);
        } else {
            DevStudyMini.outlineShape(c, k.val, ix, cy, tile * 0.27f, 0xFFE9EDF2, PANEL_T);
        }
        if (k.fixed) {
            float bx = cx + tile / 2 - 7, by = cy + tile / 2 - 7;
            c.fillCircle(bx, by, 6, Ui.GOOD);
            c.line(bx - 3, by, bx - 0.8f, by + 2.5f, 1.8f, Ui.DARK_INK);
            c.line(bx - 0.8f, by + 2.5f, bx + 3.2f, by - 2.6f, 1.8f, Ui.DARK_INK);
        }
        float pl = cx - tile / 2 + 6, pt = cy - tile / 2 + 6;
        if (!shown[i]) {
            c.fillCircle(pl, pt, 3, 0xFF2A3340);
        } else if (lamp[i] > 0) {
            c.fillCircle(pl, pt, 7, DevStudyMini.argb(70, Ui.GOOD));
            c.fillCircle(pl, pt, 3.4f, Ui.GOOD);
        } else {
            c.fillCircle(pl, pt, 3, dark[i] ? 0xFF3A2226 : 0xFF2D5A3E);
        }
        if (k.noisy) {
            boolean on = ((int) (time * 7 + i)) % 2 == 0;
            float lx = cx + tile / 2 - 5, ly = cy - tile / 2 + 5;
            c.fillCircle(lx, ly, 4.2f, on ? 0xFFFFD23F : 0xFF6B5A1E);
            if (on) {
                c.line(lx + 4, ly - 3, lx + 8, ly - 6, 1.5f, 0xFFFFD23F);
                c.line(lx + 5, ly + 1, lx + 9, ly + 1.5f, 1.5f, 0xFFFFD23F);
                c.line(lx - 1, ly - 5, lx - 2, ly - 9, 1.5f, 0xFFFFD23F);
            }
        }
        // the result chip
        if (!shown[i]) {
            c.fillCircle(cx, chipCy, 2.5f, 0xFF2E3846);
            return;
        }
        float f = pop[i] > 0 ? 1 + 0.28f * (float) Math.sin(pop[i] / 0.35f * Math.PI) : 1;
        blk[0] = chS[i];
        blk[1] = chC[i];
        blk[2] = -1;
        int ce = pulse[i] > 0 ? Ui.ACCENT : DevStudyMini.TILE_EDGE;
        DevStudyMini.drawBlock(c, cx, chipCy, chip * f, blk, CHIP_BG, ce, pulse[i] > 0 ? 2.5f : 1.5f);
    }

    private void drawToken(Gfx c) {
        if (phase == IDLE || phase == SHIP) return;
        float s = phase == BOOT ? 0 : tok;
        tokenXY(s);
        float x = tokX, y = tokY;
        float bob = (float) Math.sin(time * 14) * 1.2f;
        float scale = phase == BOOT ? 1 : Ui.clamp(n + 1 - tok + 0.3f, 0.3f, 1f);
        c.fillCircle(x, y + bob, tile * 0.5f * scale, 0x22FFFFFF);
        blk[0] = curS;
        blk[1] = curC;
        blk[2] = -1;
        DevStudyMini.drawBlock(c, x, y + bob, tile * 0.62f * scale, blk, CHIP_BG, 0xFFE9EDF2, 2f);
    }

    private void drawDesk(Gfx c) {
        DevStudyMini.desk(c, deskTop, deskBot);
        float base = deskBot - 6;
        DevStudyMini.mug(c, 36, base, time);
        int lamp = 0xFFF0B429;
        boolean on = true;
        if (phase == SHIP && shipT > 0.45f) lamp = shipResult == MISS ? Ui.BAD : Ui.GOOD;
        else if (timeLeft < limit * 0.18f && ((int) (time * 6)) % 2 == 0) on = false;
        DevStudyMini.buildLight(c, 84, base, lamp, on, time);
        // release timer
        float frac = Math.max(0, timeLeft) / limit;
        float lx = 118, lw = 128, ly = deskTop + (deskBot - deskTop) * 0.5f - 15;
        c.fillRoundRect(lx, ly, lw, 30, 6, 0xFF0B0E12);
        c.strokeRoundRect(lx, ly, lw, 30, 6, 1.5f, 0xFF2B3440);
        int tcol = frac > 0.4f ? Ui.GOOD : frac > 0.18f ? Ui.ACCENT : Ui.BAD;
        String t = String.valueOf(Math.round(Math.max(0, timeLeft) * 10) / 10f);
        c.text(t, lx + lw - 10, ly + 21, 19, tcol, Gfx.RIGHT);
        c.text("שחרור", lx + 8, ly + 13, 12, Ui.DIM, Gfx.LEFT);
        Ui.bar(c, lx + 8, ly + 21, 48, 4, frac, tcol);
        float bob = duckT > 0 ? (float) Math.abs(Math.sin(duckT * 14)) * 4 : 0;
        DevStudyMini.duck(c, 314, base - bob, 1f, time, duckT > 0 ? duckMood : 0, -1);
    }

    private void drawButtons(Gfx c) {
        boolean ready = phase == IDLE;
        c.fillRoundRect(166, btnY, 178, btnH, 14, ready ? Ui.GOOD : Ui.PANEL2);
        if (ready) c.fillRoundRect(166, btnY, 178, btnH * 0.45f, 14, 0x22FFFFFF);
        c.text(ready ? "שחרור!" : phase == SHIP ? "משחררים..." : "בונה...", 255, btnY + btnH / 2 + 7, 22, ready ? Ui.DARK_INK : Ui.DIM, Gfx.CENTER);
        boolean can = canTest();
        c.fillRoundRect(16, btnY, 138, btnH, 14, can ? 0xFF27415F : Ui.PANEL);
        c.strokeRoundRect(16, btnY, 138, btnH, 14, 1.5f, can ? Ui.BLUE : Ui.PANEL2);
        c.text(testUsed ? "נבדק" : "בדיקה נוספת", 85, btnY + btnH / 2 - 1, 15, can ? Ui.INK : Ui.DIM, Gfx.CENTER);
        c.text(testUsed ? "" : "עולה זמן", 85, btnY + btnH / 2 + 15, 12, can ? Ui.BLUE : 0xFF566170, Gfx.CENTER);
    }

    /** A little bug: oval body, head, legs and antennae. */
    static void bugGlyph(Gfx c, float x, float y, float s, int col, float t) {
        float w = (float) Math.sin(t * 16) * 1.2f * s;
        for (int k = -1; k <= 1; k++) {
            c.line(x - 5 * s, y + k * 3.5f * s, x - 10 * s, y + k * 5 * s + w * k, 1.5f * s, col);
            c.line(x + 5 * s, y + k * 3.5f * s, x + 10 * s, y + k * 5 * s - w * k, 1.5f * s, col);
        }
        c.line(x - 1.5f * s, y - 7 * s, x - 4 * s, y - 11 * s, 1.3f * s, col);
        c.line(x + 1.5f * s, y - 7 * s, x + 4 * s, y - 11 * s, 1.3f * s, col);
        c.fillCircle(x, y + 1 * s, 6.2f * s, col);
        c.fillCircle(x, y - 6 * s, 3.4f * s, col);
        c.line(x, y - 3 * s, x, y + 7 * s, 1.1f * s, 0x66000000);
        c.fillCircle(x - 1.4f * s, y - 6.5f * s, 0.9f * s, 0xFFFFFFFF);
        c.fillCircle(x + 1.4f * s, y - 6.5f * s, 0.9f * s, 0xFFFFFFFF);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        DevStudyMini.monitor(c, cx - 106, cy - 52, 212, 104, t);
        DevStudyMini.stand(c, cx, cy + 52, 8);
        DevStudyMini.codeBackdrop(c, cx - 98, cy - 40, 196, 76, t);
        float[] xs = {cx + 60, cx, cx - 60};
        c.line(cx + 90, cy - 18, cx - 90, cy - 18, 4, WIRE);
        c.line(cx + 90, cy - 18, cx + 30 + 30 * (float) (0.5 + 0.5 * Math.sin(t * 2)) * 2, cy - 18, 4, WIRE_LIT);
        for (int i = 0; i < 3; i++) {
            c.fillRoundRect(xs[i] - 20, cy - 38, 40, 40, 9, PANEL_T);
            c.strokeRoundRect(xs[i] - 20, cy - 38, 40, 40, 9, 1.5f, DevStudyMini.TILE_EDGE);
        }
        c.fillCircle(xs[0], cy - 14, 8.5f, DevStudyMini.PAL[0]);
        c.fillPoly(new float[]{xs[0], cy - 32, xs[0] + 7, cy - 19, xs[0] - 7, cy - 19}, DevStudyMini.PAL[0]);
        DevStudyMini.outlineShape(c, 2, xs[1], cy - 18, 11, 0xFFE9EDF2, PANEL_T);
        c.fillCircle(xs[2], cy - 14, 8.5f, DevStudyMini.PAL[1]);
        c.fillPoly(new float[]{xs[2], cy - 32, xs[2] + 7, cy - 19, xs[2] - 7, cy - 19}, DevStudyMini.PAL[1]);
        int[][] chips = {{0, 0, -1}, {1, 0, -1}, {1, 1, -1}};
        for (int i = 0; i < 3; i++) {
            DevStudyMini.drawBlock(c, xs[i], cy + 26, 26, chips[i], CHIP_BG, i == 1 ? Ui.BAD : DevStudyMini.TILE_EDGE, i == 1 ? 2.5f : 1.5f);
        }
        bugGlyph(c, xs[1] + 22, cy + 44 + (float) Math.sin(t * 3) * 1.5f, 1.2f, Ui.BAD, t);
        DevStudyMini.duck(c, cx + 134, cy + 60, 1.1f, t, 0, -1);
        DevStudyMini.mug(c, cx - 134, cy + 60, t);
    }

    // ------------------------------------------------------------------ test bot

    private final float[] bSeen = new float[MAXN], bReact = new float[MAXN];
    private final int[] bSig = new int[MAXN];
    private final byte[] bVerd = new byte[MAXN];
    private float botDecideAt = -1;

    private int sigOf(int i) {
        int ps = i == 0 ? curInS : chS[i - 1], pc = i == 0 ? curInC : chC[i - 1];
        return (((ps * K + pc) * K + chS[i]) * K + chC[i]) * 2 + (st[i].fixed ? 1 : 0);
    }

    /** True when the station's chip does not follow from its symbol and the block that came in. */
    private boolean looksBroken(Random r, int i, float skill) {
        St k = st[i];
        int ps = i == 0 ? curInS : chS[i - 1], pc = i == 0 ? curInC : chC[i - 1];
        boolean iconBad = k.paint ? chC[i] != k.val : chS[i] != k.val;
        boolean sideBad = k.paint ? chS[i] != ps : chC[i] != pc;
        float detectIcon = 0.97f - 0.75f * (1 - skill);
        float detectSide = Ui.clamp(skill * 1.05f - 0.1f, 0, 0.97f);
        float fp = (1 - skill) * 0.16f + (k.noisy ? 0.65f * (float) Math.pow(1 - skill, 1.3) + 0.03f : 0);
        float roll = r.nextFloat();
        if (dark[i] && r.nextFloat() < Ui.clamp(skill * 1.1f - 0.15f, 0, 0.95f)) return true;
        if (iconBad || sideBad) return (iconBad && roll < detectIcon) || (sideBad && r.nextFloat() < detectSide);
        return roll < fp;
    }

    /** Stations where nothing was expected to change, so nothing proves they work. */
    private int unprovenSeen() {
        int u = 0;
        for (int i = 0; i < n; i++) {
            if (!shown[i]) continue;
            int ps = i == 0 ? curInS : chS[i - 1], pc = i == 0 ? curInC : chC[i - 1];
            boolean noChangeNeeded = st[i].paint ? pc == st[i].val : ps == st[i].val;
            if (noChangeNeeded && chS[i] == ps && chC[i] == pc) u++;
        }
        return u;
    }

    @Override
    void bot(Random r, float rawSkill) {
        // Attention rises fast at first: a half-decent player already spots the obvious bugs.
        float skill = (float) Math.pow(rawSkill, 0.5);
        if (!laid) lay(Game.HUD_H + 74, h.screenH() - 96);
        if (phase == SHIP || phase == BOOT) return;
        boolean allJudged = true;
        for (int i = 0; i < n; i++) {
            if (!shown[i]) {
                allJudged = false;
                continue;
            }
            int sig = sigOf(i);
            if (bSig[i] != sig) {
                bSig[i] = sig;
                bVerd[i] = 0;
                bSeen[i] = time;
                bReact[i] = 0.15f + (1 - skill) * 0.9f + r.nextFloat() * 0.25f;
            }
            if (bVerd[i] == 0) {
                allJudged = false;
                if (time >= bSeen[i] + bReact[i]) {
                    if (looksBroken(r, i, skill)) {
                        bVerd[i] = 2;
                        down(cellX(i + 1), cellY(i + 1));
                    } else {
                        bVerd[i] = 1;
                    }
                }
            }
        }
        if (phase != IDLE || !allJudged) {
            botDecideAt = -1;
            return;
        }
        if (botDecideAt < 0) {
            botDecideAt = time + 0.3f + (1 - skill) * 0.9f + r.nextFloat() * 0.3f;
            return;
        }
        if (time < botDecideAt) return;
        botDecideAt = -1;
        int u = unprovenSeen();
        // Players who did not catch a dark lamp cannot rule out a dead station: the less sure, the likelier a re-test.
        float p = u == 0 ? 0.04f : (0.08f + 0.5f * (1 - skill)) * (u >= 2 ? 1.4f : 1f);
        if (canTest() && r.nextFloat() < p) down(85, btnY + btnH / 2);
        else down(255, btnY + btnH / 2);
    }
}
