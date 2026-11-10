package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Casino venue: a chunky three-reel machine. Each reel is a weighted virtual strip of 64 stops;
 * a spin picks one stop per reel uniformly and the animation then lands on exactly those stops, so
 * the symbols above and below the line are simply the strip's real neighbours (nothing is
 * arranged to tease). Only the middle line pays: three of a kind, or two coins anywhere on it.
 * Return to player is about 94%.
 *
 * The symbols are original little polygon pictures from this game's world: a coin, a half pita
 * with filling, a plastic food box, a house key, a phone with mom calling, and a star.
 */
final class SlotsMini extends Mini {
    // ---- the maths (also used by CasinoCheck) ----
    static final int COIN = 0, PITA = 1, BOX = 2, KEY = 3, PHONE = 4, STAR = 5, SYMS = 6;
    static final int N = 64;
    /** Stops per symbol on every reel. */
    static final int[] WEIGHT = {20, 14, 11, 9, 6, 4};
    /** Pays (times the bet) for three of a kind, by symbol. */
    static final int[] TRIPLE = {5, 10, 15, 30, 75, 250};
    /** Pays for exactly two coins on the line, wherever they are. */
    static final int COIN_PAIR = 2;
    static final int[] BETS = {5, 10, 25};
    static final int[] STRIP = new int[N];

    static {
        // Spread the symbols evenly round the strip: rarest first so they get clean spacing.
        boolean[] used = new boolean[N];
        for (int s = SYMS - 1; s >= 1; s--) {
            for (int j = 0; j < WEIGHT[s]; j++) {
                int pos = (int) ((j + 0.5f) * N / WEIGHT[s] + s * 5) % N;
                while (used[pos]) pos = (pos + 1) % N;
                used[pos] = true;
                STRIP[pos] = s;
            }
        }
        for (int i = 0; i < N; i++) if (!used[i]) STRIP[i] = COIN;
    }

    /** Multiple of the bet paid for a line showing a, b, c. */
    static int payMult(int a, int b, int c) {
        if (a == b && b == c) return TRIPLE[a];
        int coins = (a == COIN ? 1 : 0) + (b == COIN ? 1 : 0) + (c == COIN ? 1 : 0);
        return coins == 2 ? COIN_PAIR : 0;
    }

    /** The one place the stops are drawn: one uniform stop per reel. */
    static void drawStops(Random r, int[] out) {
        for (int i = 0; i < 3; i++) out[i] = r.nextInt(N);
    }

    // ---- animation plan ----
    /** Fast phase length per reel, then the landing run of LAND stops over LAND_T seconds. */
    private static final float LAND = 7, LAND_T = 0.65f, MIN_FAST = 22;

    private static final int IDLE = 0, SPIN = 1;
    private static final int BODY = 0xFFD9432B, BODY_D = 0xFF9C2A1A, BODY_L = 0xFFF06A48, CREAM = 0xFFFBF3DD;

    private int state, bet, spinsShown;
    private boolean leave;
    private final int[] cur = {3, 22, 45}, nxt = new int[3];
    private final float[] dist = new float[3];
    private final boolean[] landed = new boolean[3];
    private float t, time, winT, lever, lcdShown;
    private int winAmt, winMult;
    private boolean winReel0, winReel1, winReel2;
    private String lcd = "";
    private int lcdCol = 0xFF9BFF6A;

    // layout
    private float lTop, lBottom, sh, wx, wy, ww, rw, gapR, bodyY, bodyH, lcdY, ptY, ptRow, betY, rowH, spinY;
    private Btn exitB;
    private final Btn[] betB = new Btn[3];
    private float spinX, spinW;

    private static final class Fly {
        float x0, y0, x1, y1, t, delay;
    }

    private final ArrayList<Fly> flies = new ArrayList<>();
    private int botWait, botSpins, botStep;

    @Override
    int rounds() {
        return 0;
    }

    @Override
    String title() {
        return "מכונת מזל";
    }

    @Override
    String howTo() {
        return "בוחרים הימור ולוחצים על הכפתור או על הידית. שלושה סמלים זהים על הקו האמצעי מנצחים, וגם שני מטבעות.";
    }

    @Override
    void begin(int round, float difficulty) {
        state = IDLE;
        leave = false;
        t = 0;
        winT = 0;
        lever = 0;
        flies.clear();
        for (int i = 0; i < 3; i++) landed[i] = true;
        bet = (int) Ui.clamp(Math.round(h.var("casino.slots.bet")), 0, BETS.length - 1);
        fixBet();
        lcd = "";
        layout(Game.HUD_H + 74, h.screenH() - 96);
    }

    private void fixBet() {
        while (bet > 0 && h.money() < BETS[bet]) bet--;
    }

    @Override
    void pause() {
        // A spin in flight gives its bet back and the reels simply stand where they were.
        if (state == SPIN) {
            h.pay(BETS[bet]);
            state = IDLE;
            for (int i = 0; i < 3; i++) landed[i] = true;
        }
        winT = 0;
        lever = 0;
        flies.clear();
    }

    @Override
    boolean wantsExit() {
        return leave;
    }

    // ---- reel motion ----

    private static float tb(int i) {
        return 0.55f + 0.45f * i;
    }

    /** Stops still to travel on reel i at time tt: dist at the start, 0 at the landing, a small bounce after. */
    private float rem(int i, float tt) {
        float tf = tb(i), d = dist[i];
        if (tt < tf) {
            float u = tt / tf, a = 0.18f, g;
            g = u < a ? u * u / (2 * a) / (1 - a / 2) : (u - a / 2) / (1 - a / 2);
            return d - (d - LAND) * g;
        }
        float s = tt - tf;
        if (s < LAND_T) {
            float k = 1 - s / LAND_T;
            return LAND * k * k * k;
        }
        float s2 = s - LAND_T;
        return s2 < 0.45f ? -0.12f * (float) (Math.exp(-s2 * 11) * Math.sin(s2 * 26)) : 0;
    }

    private float pos(int i, float tt) {
        return cur[i] - (dist[i] - rem(i, tt));
    }

    private float landTime(int i) {
        return tb(i) + LAND_T;
    }

    private void startSpin() {
        if (state != IDLE) return;
        int b = BETS[bet];
        if (!h.pay(-b)) {
            h.text(W / 2, wy + 1.5f * sh, "אין מספיק כסף", Ui.DIM);
            return;
        }
        h.setVar("casino.slots.bet", bet);
        // The stops are decided here and nowhere else.
        drawStops(h.rnd(), nxt);
        for (int i = 0; i < 3; i++) {
            int d = Math.floorMod(cur[i] - nxt[i], N);
            while (d < LAND + MIN_FAST) d += N;
            dist[i] = d;
            landed[i] = false;
        }
        state = SPIN;
        t = 0;
        winT = 0;
        winAmt = 0;
        winMult = 0;
        lcd = "בהצלחה!";
        lcdCol = 0xFF9BFF6A;
        h.sfx(Sfx.WHOOSH);
    }

    // ---- hooks for CasinoCheck ----

    boolean spinning() {
        return state == SPIN;
    }

    int[] stopsNow() {
        return nxt.clone();
    }

    int lastWin() {
        return winAmt;
    }

    int betNow() {
        return BETS[bet];
    }

    /** True when every reel's planned motion ends exactly on its drawn stop. */
    boolean landsOnStops() {
        for (int i = 0; i < 3; i++) {
            float p = pos(i, 100);
            if (Math.floorMod(Math.round(p), N) != nxt[i] || Math.abs(p - Math.round(p)) > 1e-3) return false;
        }
        return true;
    }

    @Override
    int update(float dt) {
        time += dt;
        if (winT > 0) winT -= dt;
        // lever: pulled down fast, returns slowly
        if (state == SPIN && t < 0.2f) lever = Math.min(1, lever + dt / 0.12f);
        else lever = Math.max(0, lever - dt / 0.5f);
        for (int i = flies.size() - 1; i >= 0; i--) {
            Fly f = flies.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
            } else {
                f.t += dt / 0.8f;
                if (f.t >= 1) flies.remove(i);
            }
        }
        if (winAmt > 0 && lcdShown < winAmt) lcdShown = Math.min(winAmt, lcdShown + Math.max(1, winAmt * dt / 0.9f));
        if (state != SPIN) return RUNNING;
        t += dt;
        for (int i = 0; i < 3; i++) {
            if (!landed[i] && t >= landTime(i)) {
                landed[i] = true;
                h.shake(2);
                h.sfx(Sfx.TICK);
            }
        }
        if (landed[0] && landed[1] && landed[2]) resolve();
        return RUNNING;
    }

    private void resolve() {
        for (int i = 0; i < 3; i++) cur[i] = nxt[i];
        state = IDLE;
        int a = STRIP[nxt[0]], b = STRIP[nxt[1]], c = STRIP[nxt[2]];
        winMult = payMult(a, b, c);
        winAmt = winMult * BETS[bet];
        lcdShown = 0;
        float cy = wy + 1.5f * sh;
        if (winAmt > 0) {
            h.pay(winAmt);
            winT = winMult >= 30 ? 3.4f : 2.2f;
            boolean triple = a == b && b == c;
            winReel0 = triple || a == COIN;
            winReel1 = triple || b == COIN;
            winReel2 = triple || c == COIN;
            lcd = "זכית " + Ui.nis(winAmt);
            lcdCol = 0xFFFFE066;
            h.text(W / 2, cy - sh * 0.4f, "+" + Ui.nis(winAmt), Ui.ACCENT);
            h.burst(W / 2, cy, Ui.ACCENT, winMult >= 30 ? 40 : winMult >= 10 ? 22 : 10);
            h.sfx(winMult >= 75 ? Sfx.LEVEL : winMult >= 15 ? Sfx.PERFECT : Sfx.COIN);
            if (winMult >= 30) h.shake(6);
            int n = Math.min(14, 3 + winMult / 2);
            for (int k = 0; k < n; k++) {
                Fly f = new Fly();
                f.x0 = wx + ww * (0.15f + 0.7f * ((k * 37) % 10) / 10f);
                f.y0 = cy;
                f.x1 = W / 2 + 20;
                f.y1 = lTop - 80;
                f.delay = 0.25f + 0.07f * k;
                flies.add(f);
            }
        } else {
            winT = 0;
            lcd = "נסו שוב";
            lcdCol = 0xFF6FA87A;
        }
        spinsShown++;
        fixBet();
    }

    // ---- touches ----

    @Override
    void down(float x, float y) {
        buildButtons();
        if (exitB.hit(x, y)) {
            leave = true;
            return;
        }
        if (state != IDLE) return;
        boolean lev = Ui.in(x, y, 318, wy, 42, 3 * sh);
        if (lev || Ui.in(x, y, spinX, spinY, spinW, rowH)) {
            startSpin();
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (betB[i].hit(x, y)) {
                bet = i;
                h.sfx(Sfx.TAP);
                return;
            }
        }
    }

    // ---- layout ----

    private void layout(float top, float bottom) {
        lTop = top;
        lBottom = bottom;
        float H = bottom - top, gap = 4;
        ptRow = H >= 420 ? 26 : 19;
        rowH = H >= 420 ? 48 : 38;
        float fixed = 2 * ptRow + 2 * rowH + 3 * gap;
        float body = H - fixed;
        sh = Ui.clamp((body - 44) / 3, 34, 84);
        bodyH = 3 * sh + 44;
        float extra = Math.max(0, H - fixed - bodyH) / 4;
        float g = gap + extra;
        bodyY = top + extra / 2;
        rw = 82;
        gapR = 6;
        ww = 3 * rw + 2 * gapR;
        wx = (W - ww) / 2 - 8;
        wy = bodyY + 12;
        lcdY = wy + 3 * sh + 8;
        ptY = bodyY + bodyH + g;
        betY = ptY + 2 * ptRow + g;
        spinY = betY + rowH + g;
        spinX = 8;
        spinW = W - 16;
    }

    private void buildButtons() {
        boolean idle = state == IDLE;
        int money = h.money();
        exitB = new Btn(0, 8, betY, 76, rowH, "יציאה", idle, false);
        float bw = (W - 16 - 76 - 4 * 3) / 3;
        for (int i = 0; i < 3; i++) {
            betB[i] = new Btn(10 + i, 8 + 76 + 4 + i * (bw + 4), betY, bw, rowH, Ui.nis(BETS[i]), idle && money >= BETS[i], i == bet);
        }
    }

    // ---- drawing ----

    @Override
    void draw(Gfx c, float top, float bottom) {
        if (top != lTop || bottom != lBottom) layout(top, bottom);
        buildButtons();
        // machine body
        float bx = 26, bw = 308;
        c.fillRoundRect(bx + 2, bodyY + 4, bw, bodyH, 16, 0x66000000);
        c.fillRoundRect(bx, bodyY, bw, bodyH, 16, BODY_D);
        c.fillRoundRect(bx, bodyY, bw, bodyH - 5, 16, BODY);
        c.fillRoundRect(bx + 8, bodyY + 3, bw - 16, 5, 3, BODY_L);
        // lever
        drawLever(c);
        // reel window
        c.fillRoundRect(wx - 5, wy - 5, ww + 10, 3 * sh + 10, 8, 0xFF3B1209);
        c.fillRect(wx, wy, ww, 3 * sh, CREAM);
        for (int i = 0; i < 3; i++) drawReel(c, i);
        // soft cylinder shading
        float wh = 3 * sh;
        for (int k = 0; k < 4; k++) {
            int a = (0x44 - k * 0x10) << 24;
            float bh = sh * (0.30f - k * 0.07f);
            c.fillRect(wx, wy + k * sh * 0.07f, ww, bh * 0.5f, a);
            c.fillRect(wx, wy + wh - k * sh * 0.07f - bh * 0.5f, ww, bh * 0.5f, a);
        }
        for (int i = 1; i < 3; i++) c.fillRect(wx + i * (rw + gapR) - gapR, wy, gapR, wh, 0xFF3B1209);
        // pay line with arrows
        float cy = wy + 1.5f * sh;
        c.line(wx, cy, wx + ww, cy, 2, 0xAAE02B2B);
        c.fillPoly(new float[]{wx - 12, cy - 7, wx - 1, cy, wx - 12, cy + 7}, 0xFFFFE066);
        c.fillPoly(new float[]{wx + ww + 12, cy - 7, wx + ww + 1, cy, wx + ww + 12, cy + 7}, 0xFFFFE066);
        if (winT > 0) {
            float p = (float) (0.5 + 0.5 * Math.sin(time * 14));
            boolean[] wr = {winReel0, winReel1, winReel2};
            for (int i = 0; i < 3; i++) {
                if (wr[i]) {
                    c.strokeRoundRect(wx + i * (rw + gapR) + 1, cy - sh / 2 + 1, rw - 2, sh - 2, 8, 3, ((int) (110 + 140 * p) << 24) | 0xFFE066);
                }
            }
        }
        drawBulbs(c);
        // lcd
        float lx = wx, lw = ww;
        c.fillRoundRect(lx, lcdY, lw, 20, 5, 0xFF0B1A12);
        String shown = lcd.isEmpty() ? "בחרו הימור וסובבו" : lcd;
        if (winAmt > 0 && winT > 0 && lcdShown < winAmt) shown = "זכית " + Ui.nis((int) lcdShown);
        int lc = lcd.isEmpty() ? 0xFF6FA87A : lcdCol;
        if (winT > 0 && ((int) (time * 8) % 2 == 0)) lc = 0xFFFFFFFF;
        c.text(shown, lx + lw / 2, lcdY + 15, 14, lc, Gfx.CENTER);

        drawPayTable(c);
        exitB.draw(c);
        for (int i = 0; i < 3; i++) betB[i].draw(c);
        drawSpinButton(c);
        drawFlies(c);
    }

    private void drawReel(Gfx c, int i) {
        float rx = wx + i * (rw + gapR), cx = rx + rw / 2, cy = wy + 1.5f * sh;
        float tt = state == SPIN ? t : 1000;
        float P = state == SPIN ? pos(i, tt) : cur[i];
        float sp = state == SPIN ? Math.abs(rem(i, Math.max(0, tt - 0.03f)) - rem(i, tt)) / 0.03f : 0;
        float bl = state == SPIN && !landed[i] ? Ui.clamp((sp - 10) / 14, 0, 1) : 0;
        float u = Math.min(sh * 0.46f, rw / 2 - 6);
        boolean pulse = winT > 0 && (i == 0 ? winReel0 : i == 1 ? winReel1 : winReel2);
        if (bl < 1) {
            int base = (int) Math.floor(P);
            for (int j = base - 2; j <= base + 3; j++) {
                float o = j - P;
                if (Math.abs(o) > 1.45f) continue;
                float k = 0.75f * o, sy = (float) Math.cos(k), sxs = 0.88f + 0.12f * sy;
                float yy = cy + 1.45f * sh * (float) Math.sin(k);
                float pul = pulse && Math.abs(o) < 0.5f ? 1 + 0.1f * (float) Math.sin(time * 12) : 1;
                Sym.draw(c, STRIP[Math.floorMod(j, N)], cx, yy, u * sxs * pul, u * sy * pul, 1 - bl);
            }
        }
        if (bl > 0) {
            // fast phase: a slowed-down copy of the strip, smeared, so it reads as a blur and not as noise
            float ps = P * 0.12f;
            int base = (int) Math.floor(ps);
            for (int j = base - 2; j <= base + 3; j++) {
                float o = j - ps;
                if (Math.abs(o) > 1.45f) continue;
                float k = 0.75f * o, sy = (float) Math.cos(k);
                float yy = cy + 1.45f * sh * (float) Math.sin(k);
                for (int m = -1; m <= 1; m++) {
                    Sym.draw(c, STRIP[Math.floorMod(j * 5 + 3, N)], cx, yy + m * sh * 0.3f, u * 0.9f, u * sy * 1.1f, 0.42f * bl);
                }
            }
            c.fillRect(rx, wy, rw, 3 * sh, ((int) (60 * bl) << 24) | 0xFBF3DD);
        }
    }

    private void drawLever(Gfx c) {
        float lx = 343, y0 = wy + 1.5f * sh;
        float ballY = y0 - 38 + lever * 66;
        c.fillRoundRect(lx - 9, y0 + 6, 18, 30, 5, 0xFF5B2216);
        c.line(lx, y0 + 14, lx, ballY, 6, 0xFFC9D3DE);
        c.line(lx + 1.5f, y0 + 14, lx + 1.5f, ballY, 2, 0xFFFFFFFF);
        c.fillCircle(lx, ballY, 11, 0xFF8E1E14);
        c.fillCircle(lx - 1, ballY - 1, 10, 0xFFE53B3B);
        c.fillCircle(lx - 4, ballY - 4, 3.4f, 0xFFFFB0A8);
    }

    private void drawBulbs(Gfx c) {
        int n = 30;
        float x0 = wx - 9, y0 = wy - 9, w = ww + 18, hh = 3 * sh + 18, per = 2 * (w + hh);
        int tick = (int) (time * 14);
        for (int k = 0; k < n; k++) {
            float s = (k + 0.5f) / n * per, x, y;
            if (s < w) {
                x = x0 + s;
                y = y0;
            } else if (s < w + hh) {
                x = x0 + w;
                y = y0 + s - w;
            } else if (s < 2 * w + hh) {
                x = x0 + w - (s - w - hh);
                y = y0 + hh;
            } else {
                x = x0;
                y = y0 + hh - (s - 2 * w - hh);
            }
            boolean on;
            if (winT > 0) on = (k + tick) % 3 == 0;
            else if (state == SPIN) on = (k + (int) (time * 9)) % 2 == 0;
            else on = (k + (int) (time * 2.5f)) % 6 == 0;
            if (on) {
                c.fillCircle(x, y, 5.2f, 0x55FFE9A8);
                c.fillCircle(x, y, 3.3f, 0xFFFFF3C4);
            } else {
                c.fillCircle(x, y, 3.0f, 0xFF8E2C1C);
            }
        }
    }

    private void drawPayTable(Gfx c) {
        c.fillRoundRect(8, ptY, W - 16, 2 * ptRow, 8, Ui.PANEL);
        float cw = (W - 16) / 4;
        int[] order = {STAR, PHONE, KEY, BOX, PITA, COIN};
        for (int k = 0; k < 6; k++) {
            int s = order[k], col = k % 3, row = k / 3;
            // 3 + 3 triples laid out in the first three columns, the coin rule and the note in the last
            float x = 8 + col * cw + 5, y = ptY + row * ptRow + ptRow / 2;
            drawPayRow(c, x, y, s, 3, "×" + TRIPLE[s], cw);
        }
        drawPayRow(c, 8 + 3 * cw + 5, ptY + ptRow / 2, COIN, 2, "×" + COIN_PAIR, cw);
        c.text("הקו האמצעי", 8 + 3 * cw + cw / 2, ptY + ptRow + ptRow / 2 + 4, 12, Ui.DIM, Gfx.CENTER);
    }

    private void drawPayRow(Gfx c, float x, float cy, int sym, int count, String txt, float cw) {
        float is = Math.min(8, ptRow * 0.42f);
        for (int i = 0; i < count; i++) Sym.draw(c, sym, x + is + i * (is * 1.7f), cy, is, is, 1);
        c.text(txt, x + 3 * is * 1.7f + 3, cy + 4.5f, 13, Ui.ACCENT, Gfx.LEFT);
    }

    private void drawSpinButton(Gfx c) {
        boolean on = state == IDLE && h.money() >= BETS[bet];
        float pulse = (float) (0.5 + 0.5 * Math.sin(time * 4));
        int face = on ? 0xFF2FBF5F : 0xFF1B2028, edge = on ? 0xFF16803A : 0xFF12161C;
        c.fillRoundRect(spinX, spinY + 3, spinW, rowH, 12, edge);
        c.fillRoundRect(spinX, spinY, spinW, rowH - 1, 12, face);
        if (on) {
            c.fillRoundRect(spinX + 6, spinY + 3, spinW - 12, 5, 3, 0x44FFFFFF);
            c.strokeRoundRect(spinX - 2, spinY - 2, spinW + 4, rowH + 3, 14, 2, ((int) (50 + 110 * pulse) << 24) | 0xA8FFC0);
        }
        String label = state == SPIN ? "מסתובב..." : on ? "סובב!  " + Ui.nis(BETS[bet]) : "אין מספיק כסף";
        c.text(label, spinX + spinW / 2, spinY + rowH / 2 + 6, 19, on ? 0xFF06240F : 0xFF5B6472, Gfx.CENTER);
    }

    private void drawFlies(Gfx c) {
        for (Fly f : flies) {
            if (f.delay > 0) continue;
            float e = f.t * f.t * (3 - 2 * f.t);
            float x = f.x0 + (f.x1 - f.x0) * e, y = f.y0 + (f.y1 - f.y0) * e - (float) Math.sin(Math.PI * e) * 22;
            c.fillCircle(x, y, 7, 0xFFFFC857);
            c.fillCircle(x, y, 4.5f, 0xFFE8A92E);
            c.fillCircle(x - 2, y - 2, 1.6f, 0xFFFFF2B8);
        }
    }

    @Override
    void art(Gfx c, float cx, float cy, float tm) {
        c.fillRoundRect(cx - 100, cy - 52, 200, 104, 14, BODY_D);
        c.fillRoundRect(cx - 100, cy - 52, 200, 99, 14, BODY);
        c.fillRect(cx - 84, cy - 36, 168, 66, CREAM);
        int[] s = {COIN, STAR, PITA};
        for (int i = 0; i < 3; i++) {
            float bob = (float) Math.sin(tm * 3 + i * 1.3f) * 3;
            Sym.draw(c, s[i], cx - 56 + i * 56, cy - 3 + bob, 22, 22, 1);
        }
        for (int k = 0; k < 14; k++) {
            boolean on = (k + (int) (tm * 6)) % 3 == 0;
            c.fillCircle(cx - 84 + k * 168f / 13, cy - 43, on ? 3.4f : 2.8f, on ? 0xFFFFF3C4 : 0xFF8E2C1C);
        }
        c.fillCircle(cx + 112, cy - 28 + (float) (Math.sin(tm * 2) * 4), 8, 0xFFE53B3B);
        c.line(cx + 112, cy - 24, cx + 112, cy + 16, 4, 0xFFC9D3DE);
    }

    // ---- bot ----

    @Override
    void bot(Random r, float skill) {
        if (state == SPIN) {
            botWait = 10;
            botStep = 0;
            return;
        }
        if (botWait-- > 0) return;
        botWait = 4 + r.nextInt(8);
        buildButtons();
        if (botStep == 0) {
            if (h.money() < BETS[0] || (botSpins >= 3 && r.nextInt(9) == 0) || botSpins >= 60) {
                botSpins = 0;
                down(exitB.x + 4, exitB.y + 4);
                return;
            }
            down(betB[0].x + 4, betB[0].y + 4);
            botStep = 1;
        } else {
            down(spinX + 10, spinY + 6);
            botSpins++;
            botStep = 0;
            botWait = 3;
        }
    }

    // ---- symbols ----

    /** Original polygon pictures. Drawn in local units -1..1 with separate horizontal and vertical scale. */
    static final class Sym {
        private static final float[][] POOL = new float[40][];
        private static Gfx g;
        private static float ox, oy, ux, uy, al;

        private Sym() {
        }

        private static int col(int argb) {
            int a = (int) ((argb >>> 24) * al);
            return (a << 24) | (argb & 0xFFFFFF);
        }

        private static float[] buf(int n) {
            if (POOL[n] == null) POOL[n] = new float[n];
            return POOL[n];
        }

        private static void poly(int color, float... p) {
            float[] b = buf(p.length);
            for (int i = 0; i < p.length; i += 2) {
                b[i] = ox + p[i] * ux;
                b[i + 1] = oy + p[i + 1] * uy;
            }
            g.fillPoly(b, col(color));
        }

        private static void ell(int color, float cx, float cy, float rx, float ry) {
            float[] b = buf(28);
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14;
                b[2 * i] = ox + (cx + (float) Math.cos(a) * rx) * ux;
                b[2 * i + 1] = oy + (cy + (float) Math.sin(a) * ry) * uy;
            }
            g.fillPoly(b, col(color));
        }

        private static void rect(int color, float x0, float y0, float x1, float y1) {
            poly(color, x0, y0, x1, y0, x1, y1, x0, y1);
        }

        private static void rotated(int color, float ang, float... p) {
            float[] b = buf(p.length);
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            for (int i = 0; i < p.length; i += 2) {
                float x = p[i] * ca - p[i + 1] * sa, y = p[i] * sa + p[i + 1] * ca;
                b[i] = ox + x * ux;
                b[i + 1] = oy + y * uy;
            }
            g.fillPoly(b, col(color));
        }

        static void draw(Gfx c, int sym, float cx, float cy, float sx, float sy, float alpha) {
            if (alpha <= 0.01f || sx < 0.5f || sy < 0.5f) return;
            g = c;
            ox = cx;
            oy = cy;
            ux = sx;
            uy = sy;
            al = alpha;
            switch (sym) {
                case COIN:
                    ell(0xFFB5761A, 0, 0.05f, 0.94f, 0.94f);
                    ell(0xFFE8A92E, 0, 0, 0.92f, 0.92f);
                    ell(0xFFFFD875, 0, 0, 0.74f, 0.74f);
                    ell(0xFFFFC857, 0, 0.04f, 0.62f, 0.62f);
                    g.text("₪", ox, oy + 0.44f * uy, 1.25f * Math.min(ux, uy * 1.1f), col(0xFF9A5F10), Gfx.CENTER);
                    poly(0x66FFFFFF, -0.62f, -0.25f, -0.4f, -0.58f, -0.2f, -0.52f, -0.45f, -0.18f);
                    break;
                case PITA:
                    // half a pita, opened and stuffed
                    halfDisc(0xFFC98B3C, 0.99f, -0.2f, 0.82f);
                    halfDisc(0xFFEBC27A, 0.88f, -0.2f, 0.72f);
                    ell(0x33C98B3C, -0.35f, 0.35f, 0.14f, 0.1f);
                    ell(0x33C98B3C, 0.25f, 0.5f, 0.12f, 0.09f);
                    ell(0x33C98B3C, 0.45f, 0.2f, 0.1f, 0.08f);
                    poly(0xFF4FB043, -0.85f, -0.18f, -0.72f, -0.52f, -0.52f, -0.3f, -0.34f, -0.62f, -0.12f, -0.34f, 0.1f, -0.66f,
                            0.3f, -0.34f, 0.52f, -0.6f, 0.7f, -0.3f, 0.86f, -0.5f, 0.88f, -0.18f);
                    ell(0xFF8A5A2B, -0.42f, -0.5f, 0.21f, 0.21f);
                    ell(0xFFA9703A, -0.46f, -0.55f, 0.08f, 0.08f);
                    ell(0xFF8A5A2B, 0.0f, -0.62f, 0.2f, 0.2f);
                    ell(0xFFE84B3C, 0.46f, -0.5f, 0.18f, 0.18f);
                    break;
                case BOX:
                    // a plastic food box: clear body, coloured lid, food showing through
                    poly(0xFF7FA6BC, -0.86f, -0.42f, 0.86f, -0.42f, 0.7f, 0.88f, -0.7f, 0.88f);
                    poly(0xFFDCEBF4, -0.78f, -0.38f, 0.78f, -0.38f, 0.64f, 0.8f, -0.64f, 0.8f);
                    poly(0xFFF2A33A, -0.68f, 0.18f, -0.3f, 0.02f, 0.1f, 0.2f, 0.45f, 0.04f, 0.68f, 0.16f, 0.64f, 0.8f, -0.64f, 0.8f);
                    ell(0xFF5BBF4E, 0.2f, 0.4f, 0.16f, 0.14f);
                    ell(0xFF5BBF4E, -0.3f, 0.5f, 0.13f, 0.12f);
                    ell(0xFFFFF4D6, 0.0f, 0.62f, 0.12f, 0.1f);
                    rect(0xFF9E241B, -0.96f, -0.82f, 0.96f, -0.4f);
                    rect(0xFFE5483B, -0.96f, -0.82f, 0.96f, -0.5f);
                    rect(0x55FFFFFF, -0.86f, -0.76f, 0.86f, -0.68f);
                    rect(0xFF9E241B, -1.0f, -0.62f, -0.8f, -0.3f);
                    rect(0xFF9E241B, 0.8f, -0.62f, 1.0f, -0.3f);
                    break;
                case KEY:
                    // a round bow with a little house cut out of it, on a toothed shaft, tilted
                    float a = -0.8f;
                    float[] bow = new float[24];
                    float[] bow2 = new float[24];
                    for (int i = 0; i < 12; i++) {
                        double q = i * Math.PI / 6;
                        bow[2 * i] = (float) Math.cos(q) * 0.52f;
                        bow[2 * i + 1] = -0.5f + (float) Math.sin(q) * 0.52f;
                        bow2[2 * i] = (float) Math.cos(q) * 0.44f;
                        bow2[2 * i + 1] = -0.5f + (float) Math.sin(q) * 0.44f;
                    }
                    rotated(0xFF5C6B80, a, -0.13f, -0.1f, 0.13f, -0.1f, 0.13f, 1.0f, -0.13f, 1.0f);
                    rotated(0xFFB9C6D6, a, -0.09f, -0.1f, 0.09f, -0.1f, 0.09f, 0.95f, -0.09f, 0.95f);
                    rotated(0xFF5C6B80, a, 0.09f, 0.5f, 0.4f, 0.5f, 0.4f, 0.7f, 0.09f, 0.7f);
                    rotated(0xFFB9C6D6, a, 0.09f, 0.54f, 0.35f, 0.54f, 0.35f, 0.66f, 0.09f, 0.66f);
                    rotated(0xFF5C6B80, a, 0.09f, 0.76f, 0.3f, 0.76f, 0.3f, 0.94f, 0.09f, 0.94f);
                    rotated(0xFFB9C6D6, a, 0.09f, 0.8f, 0.26f, 0.8f, 0.26f, 0.9f, 0.09f, 0.9f);
                    rotated(0xFF5C6B80, a, bow);
                    rotated(0xFFD2DCE8, a, bow2);
                    rotated(0xFF4A586C, a, 0, -0.84f, 0.24f, -0.62f, 0.2f, -0.3f, -0.2f, -0.3f, -0.24f, -0.62f);
                    break;
                case PHONE:
                    rect(0xFF1B2438, -0.56f, -1.0f, 0.56f, 1.0f);
                    rect(0xFF2E3C5C, -0.52f, -0.96f, 0.52f, 0.96f);
                    rect(0xFF63C6F2, -0.42f, -0.8f, 0.42f, 0.66f);
                    rect(0x55FFFFFF, -0.42f, -0.8f, 0.42f, -0.5f);
                    // a heart: mom is calling
                    ell(0xFFFF5C7A, -0.12f, -0.34f, 0.14f, 0.14f);
                    ell(0xFFFF5C7A, 0.12f, -0.34f, 0.14f, 0.14f);
                    poly(0xFFFF5C7A, -0.25f, -0.28f, 0.25f, -0.28f, 0, 0.06f);
                    ell(0xFF3BCB6B, 0, 0.36f, 0.2f, 0.2f);
                    rect(0xFFFFFFFF, -0.09f, 0.3f, 0.09f, 0.42f);
                    ell(0xFF3A4A6C, 0, 0.84f, 0.07f, 0.07f);
                    poly(0xFFFFE066, -0.72f, -0.5f, -0.9f, -0.62f, -0.72f, -0.62f);
                    poly(0xFFFFE066, 0.72f, -0.5f, 0.9f, -0.62f, 0.72f, -0.62f);
                    break;
                default:
                    star(0xFFB3225D, 1.0f, 0.05f);
                    star(0xFFFF4D8D, 0.96f, 0f);
                    star(0xFFFF8CB5, 0.58f, 0f);
                    ell(0xAAFFFFFF, -0.2f, -0.28f, 0.1f, 0.1f);
                    break;
            }
        }

        /** Lower half disc whose flat edge lies at y = flatY. */
        private static void halfDisc(int color, float r, float flatY, float depth) {
            float[] b = buf(26);
            for (int i = 0; i < 13; i++) {
                double a = Math.PI * i / 12;
                b[2 * i] = ox + (float) Math.cos(a) * r * ux;
                b[2 * i + 1] = oy + (flatY + (float) Math.sin(a) * depth * 1.12f) * uy;
            }
            g.fillPoly(b, col(color));
        }

        private static void star(int color, float r, float dy) {
            float[] b = buf(20);
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float rr = i % 2 == 0 ? r : r * 0.45f;
                b[2 * i] = ox + (float) Math.cos(a) * rr * ux;
                b[2 * i + 1] = oy + (dy + (float) Math.sin(a) * rr) * uy;
            }
            g.fillPoly(b, col(color));
        }
    }
}
