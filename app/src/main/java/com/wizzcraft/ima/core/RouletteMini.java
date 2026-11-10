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
 * pause() pays a spin in flight exactly as drawn (never a refund); only chips placed for a spin
 * that has not started go back to the wallet.
 *
 * Every bet pays 36/37 of the stake on average (97.3%): a straight number pays 35:1, a dozen 2:1,
 * the even-money bets 1:1, and zero takes them all.
 *
 * Persistent variable: casino.roulette.chip (index into CHIPS).
 */
final class RouletteMini extends Mini {
    // ================================================================ stakes
    // One block for the economy designer. Every amount is in shekels.
    /** Most that can be on the table for one spin, all bets together. */
    static final int TABLE_MAX = 100;
    /** The chips in the tray; one is the current chip and each tap on a bet adds one of it. */
    static final int[] CHIPS = {5, 10, 25, 50};
    // The return multiples below set the return to player (97.3%); they are the standard single-zero
    // table and are not meant to be tuned.
    // ================================================================

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
    /** On a short screen the big wheel stays up this long after the ball settles, then the table returns. */
    private static final float SHOW = 1.6f;

    private static final int BET = 0, SPIN = 1;
    private static final int FELT = 0xFF0E4A32, FELT2 = 0xFF136040, BRASS = 0xFFC9A04A, BRASS_D = 0xFF7A5524;

    // touch ids after the 46 bet cells
    private static final int I_CHIP = BETS, I_NUM = BETS + 4, I_EXIT = BETS + 5, I_CLEAR = BETS + 6, I_AGAIN = BETS + 7,
            I_SPIN = BETS + 8, I_PAGE = BETS + 9, NI = BETS + 10;

    private int state, chip, page;
    private final int[] bets = new int[BETS], last = new int[BETS];
    private int total, lastTotal;
    /** What the latest spin staked and returned; read by CasinoCheck. */
    int lastStake, lastWin;
    private boolean sheet, leave, lostLast, spunOnce;

    // wheel and ball
    private float th, t, th0, phi0, wtot, x0, target, trackR, pocketR, rb;
    private int result = -1, resIdx, tickIdx = -1;
    private boolean ballOn, lit;
    private float ballPsi, litT = 99, tickGap, flashT, resT = 99;
    private final boolean[] won = new boolean[BETS];
    private final int[] hist = new int[5];
    private int histN;
    private int awayBet, awayBack;
    private float time;

    // layout (recomputed every draw, and once at begin so touches before the first draw are safe)
    private float lTop, lBottom, rTop, rBot, zTop, zBot, wheelH, cx, cy, R, panX0, panX1, dozY, outY, trayY, barY;
    private float cellW, cellH;
    private boolean compact;
    private final float[] bx = new float[NI], by = new float[NI], bw = new float[NI], bh = new float[NI];
    private int pressId = -1;
    private float pressT;

    // flying coins
    private static final class Fly {
        float x0, y0, x1, y1, t, delay;
        boolean lose;
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
        return "בוחרים אסימון, מניחים הימורים על השולחן ומסובבים. אפשר כמה הימורים בסיבוב אחד, ואפס לוקח את כולם.";
    }

    @Override
    void begin(int round, float difficulty) {
        // Normally pause() has already settled everything; this only guards a begin() without one.
        settleNow();
        state = BET;
        sheet = false;
        leave = false;
        page = 0;
        t = 0;
        resT = 99;
        flies.clear();
        flashT = 0;
        chip = (int) Ui.clamp(Math.round(h.var("casino.roulette.chip")), 0, CHIPS.length - 1);
        fixChip();
        layout(Game.PLAY_TOP, Math.max(h.screenH(), 560) - Game.PLAY_FOOT);
    }

    /** Moves the selection down to a chip the player can afford, if there is one. */
    private void fixChip() {
        while (chip > 0 && h.money() < CHIPS[chip]) chip--;
    }

    /** Pays a spin in flight as drawn; chips placed for a spin that never started go back. */
    private void settleNow() {
        if (state == SPIN) {
            t = T;
            settle();
            awayBet = lastStake;
            awayBack = lastWin;
        }
        clearTable();
    }

    private void clearTable() {
        if (total > 0) h.pay(total);
        java.util.Arrays.fill(bets, 0);
        total = 0;
    }

    @Override
    void pause() {
        settleNow();
        state = BET;
        sheet = false;
        t = 0;
        flies.clear();
        pressId = -1;
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
        if (pressT > 0) pressT -= dt;
        litT += dt;
        resT += dt;
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
        if (state != BET || total <= 0 || resT < Cards.Room.RESULT_HOLD) return;
        sheet = false;
        lit = false;
        state = SPIN;
        t = 0;
        awayBet = awayBack = 0;
        // The result is decided here and nowhere else.
        result = draw(h.rnd());
        resIdx = indexOf(result);
        plan();
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

    boolean spinning() {
        return state == SPIN;
    }

    /** Chips on the table right now (placed, not yet spun). */
    int onTable() {
        return total;
    }

    int betOn(int b) {
        return bets[b];
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
        lastStake = total;
        lastWin = win;
        if (win > 0) h.pay(win);
        spunOnce = true;
        ballOn = true;
        ballPsi = target;
        th = wheelAngle(T);
        lit = true;
        litT = 0;
        resT = 0;
        flashT = 1.6f;
        for (int i = hist.length - 1; i > 0; i--) hist[i] = hist[i - 1];
        hist[0] = result;
        histN = Math.min(hist.length, histN + 1);
        // chips: winners fly to the wallet in the strip, losers are swept to the wheel
        int coins = 0;
        for (int b = 0; b < BETS; b++) {
            if (bets[b] <= 0) continue;
            float sx = bx[b] + bw[b] / 2, sy = by[b] + bh[b] / 2;
            int n = won[b] ? Math.min(5, 2 + bets[b] * returnMult(b, result) / 200) : 1;
            for (int j = 0; j < n && flies.size() < 40; j++) {
                Fly f = new Fly();
                f.x0 = sx + (j - n / 2f) * 4;
                f.y0 = sy;
                f.lose = !won[b];
                f.x1 = f.lose ? cx : 343;
                f.y1 = f.lose ? cy : lTop + 15;
                f.delay = 0.15f + 0.09f * (coins++);
                flies.add(f);
            }
        }
        System.arraycopy(bets, 0, last, 0, BETS);
        lastTotal = total;
        java.util.Arrays.fill(bets, 0);
        total = 0;
        float mx = (panX0 + panX1) / 2;
        lostLast = Cards.Room.resultFx(h, mx, cy, lastStake, lastWin) == Cards.Room.LOST;
        if (result == 0 && lostLast) h.shake(5);
        h.sfx(Sfx.DING);
        fixChip();
    }

    // ---- touches ----

    private boolean idle() {
        return state == BET && resT >= Cards.Room.RESULT_HOLD;
    }

    /** The big wheel covers the table on a short screen while it spins and for a moment after. */
    private boolean bigView() {
        return compact && (state == SPIN || resT < SHOW);
    }

    private boolean enabled(int id) {
        if (id < BETS) {
            if (!idle() || h.money() < CHIPS[chip] || bigView()) return false;
            if (sheet) return id <= 36 && (!compact || id == 0 || page == (id - 1) / 18);
            return id >= 37;
        }
        int money = h.money();
        switch (id) {
            case I_NUM:
                return idle() && !bigView();
            case I_PAGE:
                return idle() && sheet && compact;
            case I_EXIT:
                return idle();
            case I_CLEAR:
                return idle() && total > 0;
            case I_AGAIN:
                return idle() && lastTotal > 0 && lastTotal <= TABLE_MAX && money + total >= lastTotal && !sameAsLast();
            case I_SPIN:
                return idle() && total > 0;
            default:
                return id >= I_CHIP && id < I_CHIP + 4 && idle() && money >= CHIPS[id - I_CHIP];
        }
    }

    private boolean sameAsLast() {
        for (int b = 0; b < BETS; b++) if (bets[b] != last[b]) return false;
        return true;
    }

    private int hitId(float x, float y) {
        for (int i = 0; i < NI; i++) {
            if (enabled(i) && Ui.in(x, y, bx[i], by[i], bw[i], bh[i])) return i;
        }
        return -1;
    }

    @Override
    void down(float x, float y) {
        pressId = hitId(x, y);
        pressT = 0.14f;
    }

    @Override
    void up(float x, float y) {
        int id = hitId(x, y);
        if (id >= 0 && id == pressId) act(id);
        pressId = -1;
    }

    private void act(int id) {
        if (!enabled(id)) return;
        if (id < BETS) {
            addBet(id);
            return;
        }
        switch (id) {
            case I_NUM:
                sheet = !sheet;
                h.sfx(Sfx.TAP);
                break;
            case I_PAGE:
                page = 1 - page;
                h.sfx(Sfx.TAP);
                break;
            case I_EXIT:
                clearTable();
                leave = true;
                break;
            case I_CLEAR:
                clearTable();
                h.sfx(Sfx.TICK);
                break;
            case I_AGAIN:
                clearTable();
                if (h.pay(-lastTotal)) {
                    System.arraycopy(last, 0, bets, 0, BETS);
                    total = lastTotal;
                    h.sfx(Sfx.TAP);
                }
                break;
            case I_SPIN:
                startSpin();
                break;
            default:
                chip = id - I_CHIP;
                h.setVar("casino.roulette.chip", chip);
                h.sfx(Sfx.TAP);
        }
    }

    private void addBet(int b) {
        int v = CHIPS[chip];
        if (total + v > TABLE_MAX) {
            h.text(bx[b] + bw[b] / 2, by[b], "עד " + Ui.nis(TABLE_MAX) + " לסיבוב", Ui.INK);
            h.sfx(Sfx.TICK);
        } else if (h.pay(-v)) {
            bets[b] += v;
            total += v;
            h.sfx(Sfx.TAP);
        } else {
            h.text(bx[b] + bw[b] / 2, by[b], "אין מספיק כסף", Ui.INK);
            h.sfx(Sfx.TICK);
        }
    }

    // ---- layout ----

    private void layout(float top, float bottom) {
        lTop = top;
        lBottom = bottom;
        float gap = 4, rowH = 44;
        barY = Cards.Room.barY(bottom);
        trayY = Cards.Room.trayY(bottom);
        rTop = Cards.Room.below(top) + 4;
        rBot = Cards.Room.trayTop(bottom) - 4;
        dozY = rBot - 2 * rowH - gap;
        outY = dozY + rowH + gap;
        compact = dozY - gap - rTop < 120;
        zTop = rTop;
        zBot = bigView() ? rBot : dozY - gap;
        wheelH = zBot - zTop;
        R = Math.min(wheelH / 2 - 2, 104);
        cx = 12 + R;
        cy = zTop + wheelH / 2;
        panX0 = cx + R + 8;
        panX1 = W - 8;
        trackR = R - Math.max(5, R * 0.106f);
        // the ball rests 30% of the way in from the pockets' outer rim (rO) toward the hub (rI)
        pocketR = (R - Math.max(8, R * 0.163f)) * 0.7f + R * 0.5f * 0.3f;
        rb = Math.max(2.6f, R * 0.052f);

        float tw = (W - 16 - 2 * gap) / 3;
        int[] dz = {B_D1, B_D2, B_D3};
        // reading right to left: the first dozen sits at the right
        for (int i = 0; i < 3; i++) set(dz[i], W - 8 - (i + 1) * tw - i * gap, dozY, tw, rowH);
        float ow = (W - 16 - 5 * gap) / 6;
        int[] out = {B_LOW, B_EVEN, B_RED, B_BLACK, B_ODD, B_HIGH};
        for (int i = 0; i < 6; i++) set(out[i], W - 8 - (i + 1) * ow - i * gap, outY, ow, rowH);

        // The number sheet: the classic table turned on its side, columns of three (3k+1 at the bottom),
        // the zero column at the right where a Hebrew reader starts. Tall screens show 1-36 at once;
        // short ones show 1-18 or 19-36 with a page switch under the zero.
        float g = 3, sh = rBot - rTop;
        int rowsShown = compact ? 3 : 6;
        cellW = (W - 16 - 6 * g) / 7;
        cellH = Math.min(52, (sh - (rowsShown - 1) * g - (compact ? 0 : 6)) / rowsShown);
        float blockH = 3 * cellH + 2 * g;
        float sTop = rTop + Math.max(0, (sh - (compact ? blockH : 2 * blockH + 6 + g)) / 2);
        for (int n = 1; n <= 36; n++) {
            int blk = (n - 1) / 18, k = (n - 1) % 18, col = k / 3, row = 2 - k % 3;
            float y = sTop + (compact ? 0 : blk * (blockH + 6 + g)) + row * (cellH + g);
            float x = W - 8 - cellW - (col + 1) * (cellW + g);
            set(n, x, y, cellW, cellH);
        }
        float zx = W - 8 - cellW;
        if (compact) {
            float zh = (blockH - g) * 0.55f;
            set(0, zx, sTop, cellW, zh);
            set(I_PAGE, zx, sTop + zh + g, cellW, blockH - zh - g);
        } else {
            set(0, zx, sTop, cellW, 2 * blockH + 6 + g);
            set(I_PAGE, -100, -100, 0, 0);
        }

        float r = Cards.Room.CHIP_R;
        for (int i = 0; i < 4; i++) {
            float ccx = 8 + r + 3 + i * (2 * r + 10);
            set(I_CHIP + i, ccx - r - 3, trayY - r - 3, 2 * r + 6, 2 * r + 6);
        }
        float nx = bx[I_CHIP + 3] + bw[I_CHIP + 3] + 8;
        set(I_NUM, nx, trayY - 24, 352 - nx, 48);
        float p = Cards.Room.PAD, ew = Cards.Room.EXIT_W, bh2 = Cards.Room.BAR_H;
        set(I_EXIT, p, barY, ew, bh2);
        float x1 = p + ew + gap;
        set(I_CLEAR, x1, barY, 70, bh2);
        set(I_AGAIN, x1 + 70 + gap, barY, 70, bh2);
        float sx = x1 + 2 * (70 + gap);
        set(I_SPIN, sx, barY, 360 - p - sx, bh2);
    }

    private void set(int b, float x, float y, float w, float hh) {
        bx[b] = x;
        by[b] = y;
        bw[b] = w;
        bh[b] = hh;
    }

    // ---- drawing ----

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        c.fillRoundRect(2, top - 3, W - 4, bottom - top + 6, 14, FELT);
        c.strokeRoundRect(3, top - 2, W - 6, bottom - top + 4, 13, 2, BRASS_D);

        if (!sheet || bigView()) {
            drawWheel(c, cx, cy, R, th, lit ? resIdx : -1, litT);
            drawBall(c);
            drawPanel(c);
            if (!bigView()) drawTable(c);
        } else {
            drawSheet(c);
        }
        drawTray(c);
        Cards.Room.exit(c, bx[I_EXIT], by[I_EXIT], bw[I_EXIT], bh[I_EXIT], enabled(I_EXIT), press(I_EXIT));
        btn(c, I_CLEAR, h.t("נקה", "נקי"), false);
        btn(c, I_AGAIN, "שוב", false);
        String spin = state == SPIN ? "מסתובב..." : total > 0 ? h.t("סובב ", "סובבי ") + Ui.nis(total) : h.t("סובב", "סובבי");
        btn(c, I_SPIN, spin, !lostLast);
        drawFlies(c);
        String note = awayBet > 0 ? Cards.Room.awayNote(awayBet, awayBack) : histN > 0 && state == BET ? "יצא " + hist[0] : "";
        int noteCol = awayBet > 0 ? Cards.Room.awayColor(awayBet, awayBack) : histN > 0 ? pocketInk(hist[0]) : Ui.DIM;
        Cards.Room.strip(c, top, h.money(), total, TABLE_MAX, note, noteCol);
    }

    private float press(int id) {
        return pressId == id && pressT > 0 ? 1 : 0;
    }

    private void btn(Gfx c, int id, String label, boolean gold) {
        Cards.Room.action(c, bx[id], by[id], bw[id], bh[id], label, enabled(id), gold, press(id));
    }

    private static final float[] POLY = new float[12];

    /** Draws the wheel; lit >= 0 lights that pocket. Also used for the intro art. */
    static void drawWheel(Gfx c, float cx, float cy, float R, float th, int lit, float litT) {
        c.fillCircle(cx + 2, cy + 3, R + 2, 0x66000000);
        c.fillCircle(cx, cy, R, BRASS);
        c.fillCircle(cx, cy, R - 3, BRASS_D);
        c.fillCircle(cx, cy, R - Math.max(3.5f, R * 0.048f), 0xFF3E2A17);
        float rO = R - Math.max(8, R * 0.163f), rI = R * 0.5f;
        c.fillCircle(cx, cy, rO + 2, 0xFF2A1C10);
        c.fillCircle(cx, cy, rO, 0xFFB08A3E);
        for (int i = 0; i < 37; i++) {
            int n = ORDER[i];
            wedge(c, cx, cy, rI, rO, th + i * STEP, STEP * 0.84f, pocketColor(n));
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
            float a = th + k * (float) Math.PI / 2;
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

    /** History, the big result number and the result words, to the right of the wheel. */
    private void drawPanel(Gfx c) {
        float pw = panX1 - panX0, mx = (panX0 + panX1) / 2;
        float sp = Math.min(26, pw / 5);
        if (wheelH < 100) {
            // short screen, betting: one row of history and one line of words
            float y = zTop + 4;
            for (int i = 0; i < histN; i++) ball(c, mx + ((histN - 1) / 2f - i) * sp, y + 12, 11, hist[i]);
            if (histN == 0) c.text("אין תוצאות עדיין", mx, y + 17, 12, Ui.DIM, Gfx.CENTER);
            line(c, mx, zBot - 8, pw, 15);
            return;
        }
        float y = zTop + Math.max(0, (wheelH - 124) / 2);
        for (int i = 0; i < histN; i++) ball(c, mx + ((histN - 1) / 2f - i) * sp, y + 12, 12, hist[i]);
        if (histN == 0) c.text("אין תוצאות עדיין", mx, y + 17, 12, Ui.DIM, Gfx.CENTER);
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
        line(c, mx, y + 104, pw, 17);
    }

    /** The panel's words: the result of the last spin, or what to do now. */
    private void line(Gfx c, float mx, float y, float pw, float size) {
        if (state == SPIN) {
            c.text("הכדור מסתובב", mx, y, Ui.fit(c, "הכדור מסתובב", pw, size - 2), Ui.DIM, Gfx.CENTER);
        } else if (spunOnce && total == 0) {
            String hl = Cards.Room.headline(lastStake, lastWin);
            c.text(hl, mx, y, Ui.fit(c, hl, pw, size), Cards.Room.headlineColor(lastStake, lastWin), Gfx.CENTER);
            if (wheelH >= 100) {
                String d = Cards.Room.detail(lastStake, lastWin);
                c.text(d, mx, y + 18, Ui.fit(c, d, pw, 12), 0xFFC9D2DC, Gfx.CENTER);
            }
        } else {
            boolean broke = h.money() < CHIPS[0] && total == 0;
            String m = broke ? "אין מספיק כסף לשחק" : total > 0 ? h.t("מוכן? סובב", "מוכנה? סובבי") : h.t("הנח אסימונים", "הניחי אסימונים");
            c.text(m, mx, y, Ui.fit(c, m, pw, size - 2), broke ? Ui.BAD : Ui.INK, Gfx.CENTER);
        }
    }

    private void ball(Gfx c, float x, float y, float r, int n) {
        c.fillCircle(x, y, r, pocketColor(n));
        c.text(String.valueOf(n), x, y + r * 0.4f, 12, 0xFFFFFFFF, Gfx.CENTER);
    }

    private static int pocketColor(int n) {
        return n == 0 ? 0xFF1F8F4E : RED[n] ? 0xFFC8302F : 0xFF1B1D22;
    }

    /** The pocket colour as legible text on the dark strip. */
    private static int pocketInk(int n) {
        return n == 0 ? Ui.GOOD : RED[n] ? 0xFFFF7A7A : Ui.INK;
    }

    private void drawTable(Gfx c) {
        String[] lab = {"1-12", "13-24", "25-36"};
        int[] dz = {B_D1, B_D2, B_D3};
        for (int i = 0; i < 3; i++) cell(c, dz[i], lab[i], FELT2, 0xFFF3E6C2);
        cell(c, B_LOW, "1-18", FELT2, 0xFFF3E6C2);
        cell(c, B_EVEN, "זוגי", FELT2, 0xFFF3E6C2);
        cell(c, B_RED, "אדום", 0xFFB02828, 0xFFFFFFFF);
        cell(c, B_BLACK, "שחור", 0xFF1B1D22, 0xFFFFFFFF);
        cell(c, B_ODD, "אי-זוגי", FELT2, 0xFFF3E6C2);
        cell(c, B_HIGH, "19-36", FELT2, 0xFFF3E6C2);
    }

    /** One bet cell: label, current stake and a gold flash when it won. */
    private void cell(Gfx c, int b, String label, int fill, int ink) {
        boolean en = enabled(b);
        float x = bx[b], y = by[b], w = bw[b], hh = bh[b];
        boolean win = won[b] && flashT > 0 && ((int) (time * 8) % 2 == 0);
        c.fillRoundRect(x, y, w, hh, 7, en || bets[b] > 0 ? fill : Ui.darken(fill, 0.6f));
        if (win) c.fillRoundRect(x, y, w, hh, 7, 0x88FFE08A);
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, hh - 1, 7, 1.5f, bets[b] > 0 || win ? 0xFFFFE08A : 0x99C9A04A);
        int tc = en || bets[b] > 0 ? ink : (ink & 0xFFFFFF) | 0x77000000;
        if (w > 90) {
            c.text(label, x + w * 0.4f, y + hh / 2 + 5.5f, 16, tc, Gfx.CENTER);
            c.text("×3", x + w * 0.4f + c.textWidth(label, 16) / 2 + 4, y + hh / 2 + 4.5f, 12, Ui.darken(tc, 0.75f), Gfx.LEFT);
            if (bets[b] > 0) goldChip(c, x + 16, y + hh / 2, bets[b]);
        } else {
            float ly = bets[b] > 0 ? y + hh - 6 : y + hh / 2 + 5;
            c.text(label, x + w / 2, ly, Ui.fit(c, label, w - 6, 14), tc, Gfx.CENTER);
            if (bets[b] > 0) goldChip(c, x + w / 2, y + 13, bets[b]);
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
        c.fillRoundRect(6, rTop - 2, W - 12, rBot - rTop + 4, 10, 0xFF0B3A27);
        for (int n = 0; n < 37; n++) {
            if (compact && n > 0 && (n - 1) / 18 != page) continue;
            boolean can = enabled(n);
            float x = bx[n], y = by[n], w = bw[n], hh = bh[n];
            int col = pocketColor(n);
            boolean win = won[n] && flashT > 0 && ((int) (time * 8) % 2 == 0);
            c.fillRoundRect(x, y, w, hh, 6, can || bets[n] > 0 ? col : Ui.darken(col, 0.55f));
            if (win) c.fillRoundRect(x, y, w, hh, 6, 0x88FFE08A);
            c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, hh - 1, 6, bets[n] > 0 ? 2.2f : 1, bets[n] > 0 ? 0xFFFFE08A : 0x88C9A04A);
            int ink = can || bets[n] > 0 ? 0xFFFFFFFF : 0x88FFFFFF;
            if (bets[n] > 0) {
                c.text(String.valueOf(n), x + w / 2, y + hh * 0.45f, 16, ink, Gfx.CENTER);
                c.fillRoundRect(x + 3, y + hh - 17, w - 6, 15, 7, 0xFFFFC857);
                c.text(String.valueOf(bets[n]), x + w / 2, y + hh - 5, 12, 0xFF1A1300, Gfx.CENTER);
            } else {
                c.text(String.valueOf(n), x + w / 2, y + hh / 2 + 6.5f, 18, ink, Gfx.CENTER);
            }
        }
        // under the zero: what a number pays, or (short screens) the page switch
        if (compact) {
            boolean on = enabled(I_PAGE);
            float x = bx[I_PAGE], y = by[I_PAGE], w = bw[I_PAGE], hh = bh[I_PAGE];
            c.fillRoundRect(x, y, w, hh, 6, on ? Cards.Room.NEUTRAL : Cards.Room.OFF);
            float ay = y + hh / 2 - 6;
            if (page == 0) c.fillPoly(new float[]{x + w / 2 + 6, ay - 6, x + w / 2 - 6, ay, x + w / 2 + 6, ay + 6}, Ui.INK);
            else c.fillPoly(new float[]{x + w / 2 - 6, ay - 6, x + w / 2 + 6, ay, x + w / 2 - 6, ay + 6}, Ui.INK);
            c.text(page == 0 ? "19-36" : "1-18", x + w / 2, y + hh - 8, 12, Ui.INK, Gfx.CENTER);
        } else {
            c.text("פי 36", bx[0] + bw[0] / 2, by[0] + bh[0] - 12, 12, 0xCCFFFFFF, Gfx.CENTER);
        }
    }

    private void drawTray(Gfx c) {
        for (int i = 0; i < 4; i++) {
            int id = I_CHIP + i;
            Cards.Room.chip(c, bx[id] + bw[id] / 2, by[id] + bh[id] / 2, CHIPS[i], h.money() >= CHIPS[i] && state == BET, i == chip, press(id));
        }
        int nums = 0;
        for (int n = 0; n < 37; n++) if (bets[n] > 0) nums++;
        String lab = sheet ? h.t("חזור לשולחן", "חזרי לשולחן") : nums > 0 ? "מספרים (" + nums + ")" : "מספרים";
        Cards.Room.action(c, bx[I_NUM], by[I_NUM], bw[I_NUM], bh[I_NUM], lab, enabled(I_NUM), false, press(I_NUM));
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
        if (state == SPIN || !idle()) {
            botWait = 12;
            botStep = 0;
            return;
        }
        if (botWait-- > 0) return;
        botWait = 4 + r.nextInt(6);
        if (sheet) {
            tap(I_NUM);
            return;
        }
        if (botStep == 0) {
            // Leaves after a few spins, or when it can no longer afford the smallest chip.
            if (h.money() < CHIPS[0] || (botSpins >= 2 && r.nextInt(4) == 0) || botSpins >= 40) {
                botSpins = 0;
                tap(I_EXIT);
                return;
            }
            tap(I_CHIP + (h.money() >= CHIPS[1] ? 1 : 0));
            botStep = 1;
        } else if (botStep == 1 || botStep == 2) {
            int[] even = {B_RED, B_BLACK, B_ODD, B_EVEN, B_LOW, B_HIGH};
            tap(even[r.nextInt(even.length)]);
            botStep = botStep == 1 && r.nextBoolean() ? 2 : 3;
        } else {
            tap(I_SPIN);
            botSpins++;
            botStep = 0;
            botWait = 3;
        }
    }

    private void tap(int id) {
        float x = bx[id] + bw[id] / 2, y = by[id] + bh[id] / 2;
        down(x, y);
        up(x, y);
    }
}
