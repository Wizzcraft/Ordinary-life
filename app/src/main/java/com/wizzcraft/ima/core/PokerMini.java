package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Casino video poker (a venue): Jacks or Better, five-card draw, the full-pay 9/6 table. Choose a
 * coin value and 1-5 coins, deal, tap cards to hold, draw once. The deck is a fresh honest shuffle
 * every hand; nothing looks at it or steers it. The bet leaves the wallet at the deal, the win is
 * paid once at the draw; pause() returns a bet whose hand has not been drawn.
 *
 * Persistent variables: casino.pk.coins (1..5), casino.pk.val (index into VALUES).
 */
final class PokerMini extends Mini {
    // ---- pure rules, shared with the odds check ----
    static final int NONE = 0, JACKS = 1, TWO_PAIR = 2, TRIPS = 3, STRAIGHT = 4, FLUSH = 5, FULL = 6, QUADS = 7,
            STRAIGHT_FLUSH = 8, ROYAL = 9;
    /** Payout per coin by hand rank (royal handled in {@link #pay}). */
    private static final int[] PER_COIN = {0, 1, 2, 3, 4, 6, 9, 25, 50, 250};
    private static final String[] NAMES = {"", "זוג נסיכים ומעלה", "שני זוגות", "שלישייה", "רצף", "צבע", "פול האוס",
            "רביעייה", "סטרייט פלאש", "רויאל פלאש"};

    /** Payout per coin: the royal flush pays 250 a coin, and 800 a coin on a full five-coin bet. */
    static int pay(int rank, int coins) {
        return rank == ROYAL && coins == 5 ? 800 : PER_COIN[rank];
    }

    static int rank(int[] c) {
        int[] cnt = new int[13];
        int suit0 = Cards.suit(c[0]);
        boolean flush = true;
        int min = 12, max = 0;
        for (int i = 0; i < 5; i++) {
            int r = Cards.rank(c[i]);
            cnt[r]++;
            if (Cards.suit(c[i]) != suit0) flush = false;
            if (r < min) min = r;
            if (r > max) max = r;
        }
        int pairs = 0, trips = 0, quads = 0, highPair = 0, distinct = 0;
        for (int r = 0; r < 13; r++) {
            if (cnt[r] > 0) distinct++;
            if (cnt[r] == 2) {
                pairs++;
                if (r >= 9) highPair = 1;
            } else if (cnt[r] == 3) {
                trips++;
            } else if (cnt[r] == 4) {
                quads++;
            }
        }
        boolean straight = distinct == 5 && (max - min == 4 || (max == 12 && min == 0 && cnt[1] == 1 && cnt[2] == 1 && cnt[3] == 1));
        if (straight && flush) return min == 8 ? ROYAL : STRAIGHT_FLUSH;
        if (quads == 1) return QUADS;
        if (trips == 1 && pairs == 1) return FULL;
        if (flush) return FLUSH;
        if (straight) return STRAIGHT;
        if (trips == 1) return TRIPS;
        if (pairs == 2) return TWO_PAIR;
        if (pairs == 1 && highPair == 1) return JACKS;
        return NONE;
    }

    /** Which of the five cards make the winning hand (for the glow). */
    static int scoringMask(int[] c, int rank) {
        if (rank >= STRAIGHT || rank == NONE) return rank == NONE ? 0 : 31;
        int m = 0;
        for (int i = 0; i < 5; i++) {
            int same = 0;
            for (int j = 0; j < 5; j++) if (Cards.rank(c[i]) == Cards.rank(c[j])) same++;
            if (same >= 2) m |= 1 << i;
        }
        return m;
    }

    // Estimated value (in coins, at five coins) of keeping certain holdings; only the ordering matters.
    // The made hands and the three-suited-cards holds are computed exactly; the rest use the textbook
    // averages (Jacks or Better 9/6 optimal strategy tables).
    private static double drawValue(int[] c, int mask) {
        int n = Integer.bitCount(mask);
        int[] r = new int[n], s = new int[n];
        int k = 0;
        for (int i = 0; i < 5; i++) {
            if ((mask >> i & 1) != 0) {
                r[k] = Cards.rank(c[i]);
                s[k] = Cards.suit(c[i]);
                k++;
            }
        }
        int highs = 0;
        boolean suited = true;
        for (int i = 0; i < n; i++) {
            if (r[i] >= 9) highs++;
            if (s[i] != s[0]) suited = false;
        }
        switch (n) {
            case 0:
                return 0.36;
            case 1:
                return highs == 1 ? 0.447 : 0;
            case 2:
                if (r[0] == r[1]) return r[0] >= 9 ? 1.54 : 0.82;
                if (highs == 2) return suited ? 0.60 : 0.49;
                if (suited && highs == 1 && (r[0] == 8 || r[1] == 8)) return 0.50;
                return 0;
            case 3:
                if (r[0] == r[1] && r[1] == r[2]) return 4.30;
                if (suited && sfWindow(r, 3)) return exact(c, mask);
                return 0;
            case 4: {
                int[] cnt = new int[13];
                for (int i = 0; i < 4; i++) cnt[r[i]]++;
                int pairs = 0, trips = 0, quads = 0;
                for (int i = 0; i < 13; i++) {
                    if (cnt[i] == 2) pairs++;
                    else if (cnt[i] == 3) trips++;
                    else if (cnt[i] == 4) quads++;
                }
                if (quads == 1) return 25;
                if (pairs == 2) return 2.596;
                if (trips == 1) return 3.84;
                if (pairs == 1) return 0;
                int outs = straightOuts(r);
                if (suited) {
                    boolean royal = true;
                    for (int i = 0; i < 4; i++) if (r[i] < 8) royal = false;
                    if (royal) return 18.6;
                    if (outs == 2) return 3.53 + 0.064 * highs;
                    if (outs == 1) return 2.34 + 0.064 * highs;
                    return 1.149 + 0.064 * highs;
                }
                if (outs == 2) return 0.68 + 0.064 * highs;
                if (outs == 1) return 0.34 + 0.064 * highs;
                return 0;
            }
            default: {
                int rk = rank(c);
                return rk == NONE || rk == JACKS || rk == TWO_PAIR || rk == TRIPS ? PER_COIN[rk] : pay(rk, 5);
            }
        }
    }

    /** Distinct ranks the four cards need to complete a straight (ace high or low): 2 open, 1 inside, 0 none. */
    private static int straightOuts(int[] r) {
        boolean[] need = new boolean[13];
        int outs = 0;
        for (int lo = -1; lo <= 8; lo++) {
            int have = 0, missing = -99;
            for (int v = lo; v < lo + 5; v++) {
                int rr = v == -1 ? 12 : v;
                boolean in = false;
                for (int x : r) if (x == rr) in = true;
                if (in) have++;
                else missing = rr;
            }
            if (have == 4 && !need[missing]) {
                need[missing] = true;
                outs++;
            }
        }
        return outs;
    }

    /** True when the n suited cards fit inside some five-rank window (a straight-flush draw). */
    private static boolean sfWindow(int[] r, int n) {
        for (int lo = -1; lo <= 8; lo++) {
            int have = 0;
            for (int i = 0; i < n; i++) {
                int rr = r[i];
                if ((rr >= lo && rr < lo + 5) || (rr == 12 && lo == -1)) have++;
            }
            if (have == n) return true;
        }
        return false;
    }

    /** Exact expected payout (in coins at five) of keeping the cards in mask and drawing the rest. */
    private static double exact(int[] c, int mask) {
        boolean[] used = new boolean[52];
        for (int x : c) used[x] = true;
        int[] pool = new int[47];
        int p = 0;
        for (int i = 0; i < 52; i++) if (!used[i]) pool[p++] = i;
        int[] hand = new int[5];
        int k = 0;
        for (int i = 0; i < 5; i++) if ((mask >> i & 1) != 0) hand[k++] = c[i];
        double sum = 0;
        for (int a = 0; a < 47; a++) {
            hand[3] = pool[a];
            for (int b = a + 1; b < 47; b++) {
                hand[4] = pool[b];
                sum += pay(rank(hand), 5);
            }
        }
        return sum / (47 * 46 / 2);
    }

    /** A sensible hold: the subset of the five cards with the best estimated value. Returns a bit mask. */
    static int bestHold(int[] c) {
        double best = -1;
        int bm = 0;
        for (int m = 0; m < 32; m++) {
            double v = drawValue(c, m);
            if (v > best + 1e-9) {
                best = v;
                bm = m;
            }
        }
        return bm;
    }

    // ---- the machine ----
    private static final int[] VALUES = {2, 5, 10, 20};
    private static final int BET = 0, DEAL = 1, HOLD = 2, DRAW = 3;
    // buttons: 0 value, 1 coins, 2 exit, 3 max, 4 deal, 5 draw, 6..10 cards
    private static final int B_VAL = 0, B_COINS = 1, B_EXIT = 2, B_MAX = 3, B_DEAL = 4, B_DRAW = 5, B_CARD = 6, NB = 11;

    private Cards.Deck deck;
    private final Cards.Sprite[] hand = new Cards.Sprite[5];
    private final ArrayList<Cards.Sprite> gone = new ArrayList<>();
    private final boolean[] held = new boolean[5];
    private final float[] lift = new float[5];
    private int phase, coins = 5, vi = 1, staked, rank = NONE, win, scoring, bursts;
    private boolean exit, resultShown, settledFx, anyHand;
    private float time, resT, botT, pressT, shownWin;
    private int pressId = -1;
    private String msg = "";

    private float top, bottom, rh, ch, cw, cardsY, rowAy, rowBy, tableH, zoneY;
    private final float[] bx = new float[NB], by = new float[NB], bw = new float[NB], bh = new float[NB];

    @Override
    int rounds() {
        return 0;
    }

    @Override
    String title() {
        return "וידאו פוקר";
    }

    @Override
    String howTo() {
        return "לוחצים על קלפים כדי לשמור אותם, ואז מחליפים את השאר. זוג נסיכים ומעלה כבר משלם, ורויאל פלאש משלם בגדול.";
    }

    @Override
    void begin(int round, float difficulty) {
        if (staked > 0) {
            h.pay(staked);
            staked = 0;
        }
        coins = Math.max(1, Math.min(5, (int) h.var("casino.pk.coins")));
        if (h.var("casino.pk.coins") == 0) coins = 5;
        vi = Math.max(0, Math.min(VALUES.length - 1, (int) h.var("casino.pk.val")));
        if (h.var("casino.pk.val") == 0) vi = 1;
        reset();
        lay(166, Math.max(h.screenH(), 560) - 96);
        msg = "";
    }

    private void reset() {
        phase = BET;
        for (int i = 0; i < 5; i++) {
            hand[i] = null;
            held[i] = false;
            lift[i] = 0;
        }
        gone.clear();
        rank = NONE;
        win = 0;
        scoring = 0;
        resultShown = settledFx = anyHand = false;
        exit = false;
        pressId = -1;
        botT = 0.5f;
    }

    @Override
    void pause() {
        if (staked > 0) {
            h.pay(staked);
            staked = 0;
        }
        reset();
    }

    @Override
    boolean wantsExit() {
        return exit;
    }

    private int cost() {
        return coins * VALUES[vi];
    }

    // ------------------------------------------------------------------ layout

    private void lay(float t, float b) {
        top = t;
        bottom = b;
        float hh = b - t;
        rh = Ui.clamp(hh * 0.052f, 15, 19);
        tableH = rh * 5 + 8;
        ch = Ui.clamp(hh * 0.22f, 60, 92);
        cw = Math.min(64, ch * 0.7f);
        rowBy = bottom - 52;
        rowAy = rowBy - 48;
        cardsY = top + tableH + 18 + Math.max(0, (rowAy - (top + tableH + 18) - ch - 58) * 0.45f);
        zoneY = (cardsY + ch + rowAy) / 2 + 2;
        float gap = Math.min(10, (344 - 5 * cw) / 4);
        float x0 = 180 - (5 * cw + 4 * gap) / 2;
        for (int i = 0; i < 5; i++) set(B_CARD + i, x0 + i * (cw + gap), cardsY - 14, cw, ch + 14);
        set(B_VAL, 252, rowAy, 96, 44);
        set(B_COINS, 132, rowAy, 108, 44);
        set(B_EXIT, 12, rowAy, 108, 44);
        set(B_MAX, 12, rowBy, 100, 48);
        set(B_DEAL, 124, rowBy, 224, 48);
        set(B_DRAW, 12, rowBy, 336, 48);
    }

    private void set(int id, float x, float y, float w, float h2) {
        bx[id] = x;
        by[id] = y;
        bw[id] = w;
        bh[id] = h2;
    }

    private float cardX(int i) {
        return bx[B_CARD + i];
    }

    // ------------------------------------------------------------------ flow

    private int[] cards() {
        int[] c = new int[5];
        for (int i = 0; i < 5; i++) c[i] = hand[i] == null ? 0 : hand[i].card;
        return c;
    }

    private boolean settledAll() {
        for (Cards.Sprite s : hand) if (s != null && !s.settled()) return false;
        return true;
    }

    private void startDeal() {
        int cst = cost();
        if (phase != BET || !h.pay(-cst)) return;
        staked = cst;
        if (deck == null) deck = new Cards.Deck(1);
        deck.shuffle(h.rnd());
        for (int i = 0; i < 5; i++) {
            if (hand[i] != null) {
                hand[i].moveTo(400, hand[i].y);
                hand[i].dur = 0.3f;
                gone.add(hand[i]);
            }
            hand[i] = new Cards.Sprite(deck.draw(), -cw - 10, cardsY, cardX(i), cardsY, 0.08f + i * 0.13f, true);
            held[i] = false;
            lift[i] = 0;
        }
        phase = DEAL;
        rank = NONE;
        win = 0;
        scoring = 0;
        resultShown = settledFx = false;
        anyHand = true;
        bursts = 0;
        msg = "";
        h.sfx(Sfx.WHOOSH);
    }

    private void doDraw() {
        if (phase != HOLD || !settledAll()) return;
        for (int i = 0; i < 5; i++) {
            if (held[i]) continue;
            hand[i].moveTo(400, hand[i].y - lift[i] * 12);
            hand[i].dur = 0.28f;
            gone.add(hand[i]);
            hand[i] = new Cards.Sprite(deck.draw(), -cw - 10, cardsY, cardX(i), cardsY, 0.1f + i * 0.12f, true);
        }
        rank = rank(cards());
        win = pay(rank, coins) * VALUES[vi] * coins;
        if (win > 0) h.pay(win);
        staked = 0;
        phase = DRAW;
        resT = 0;
        settledFx = false;
        h.sfx(Sfx.WHOOSH);
    }

    private void finishHand() {
        settledFx = true;
        resultShown = true;
        phase = BET;
        scoring = scoringMask(cards(), rank);
        for (int i = 0; i < 5; i++) hand[i].glow = (scoring >> i & 1) != 0 ? 1 : 0;
        resT = 0;
        shownWin = 0;
        float cx = 180;
        if (win > 0) {
            msg = "זכית";
            h.text(cx, cardsY + ch * 0.5f, "+" + Ui.nis(win), Ui.ACCENT);
            int n = rank >= FULL ? 30 : rank >= STRAIGHT ? 18 : 8;
            h.burst(cx, cardsY + ch * 0.5f, Ui.ACCENT, n);
            if (rank >= STRAIGHT_FLUSH) {
                h.burst(80, cardsY + ch * 0.3f, 0xFFFF6B6B, 20);
                h.burst(280, cardsY + ch * 0.3f, 0xFF5AA9FF, 20);
            }
            if (rank >= STRAIGHT) h.shake(rank >= FULL ? 7 : 4);
            h.sfx(rank >= ROYAL ? Sfx.LEVEL : rank >= FULL ? Sfx.PERFECT : rank >= TRIPS ? Sfx.GOOD : Sfx.COIN);
        } else {
            msg = "אין זכייה, ננסה שוב";
            h.sfx(Sfx.TICK);
        }
    }

    // ------------------------------------------------------------------ update

    @Override
    int update(float d) {
        time += d;
        if (botT > 0) botT -= d;
        if (pressT > 0) pressT -= d;
        resT += d;
        for (Cards.Sprite s : hand) if (s != null) s.update(d);
        for (int i = gone.size() - 1; i >= 0; i--) {
            gone.get(i).update(d);
            if (gone.get(i).t >= 1) gone.remove(i);
        }
        for (int i = 0; i < 5; i++) {
            float want = held[i] && phase != BET ? 1 : 0;
            lift[i] += (want - lift[i]) * Math.min(1, d * 14);
        }
        if (phase == DEAL && settledAll()) {
            phase = HOLD;
            rank = rank(cards());
            msg = "לחצו על קלפים כדי לשמור";
            if (rank >= JACKS) h.sfx(Sfx.TICK);
        } else if (phase == DRAW && settledAll()) {
            finishHand();
        }
        if (resultShown && win > 0) shownWin = Math.min(win, shownWin + win * d / 0.7f);
        return RUNNING;
    }

    // ------------------------------------------------------------------ input

    private boolean enabled(int id) {
        switch (id) {
            case B_VAL:
            case B_COINS:
            case B_EXIT:
                return phase == BET;
            case B_MAX:
                return phase == BET && 5 * VALUES[vi] <= h.money();
            case B_DEAL:
                return phase == BET && cost() <= h.money();
            case B_DRAW:
                return phase == HOLD && settledAll();
            default:
                return id >= B_CARD && phase == HOLD && settledAll();
        }
    }

    private int hitId(float x, float y) {
        for (int i = 0; i < NB; i++) {
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
        switch (id) {
            case B_VAL:
                vi = (vi + 1) % VALUES.length;
                h.setVar("casino.pk.val", vi);
                h.sfx(Sfx.TAP);
                break;
            case B_COINS:
                coins = coins % 5 + 1;
                h.setVar("casino.pk.coins", coins);
                h.sfx(Sfx.TAP);
                break;
            case B_EXIT:
                exit = true;
                break;
            case B_MAX:
                coins = 5;
                h.setVar("casino.pk.coins", coins);
                startDeal();
                break;
            case B_DEAL:
                startDeal();
                break;
            case B_DRAW:
                doDraw();
                break;
            default: {
                int i = id - B_CARD;
                held[i] = !held[i];
                h.sfx(Sfx.TICK);
            }
        }
    }

    // ------------------------------------------------------------------ bot

    @Override
    void bot(Random r, float skill) {
        if (botT > 0) return;
        botT = 0.3f + r.nextFloat() * 0.3f;
        if (phase == BET) {
            if (!enabled(B_DEAL)) {
                if (h.money() < VALUES[0]) tap(B_EXIT);
                else if (coins > 1) tap(B_COINS);
                else tap(B_VAL);
                return;
            }
            if (anyHand && r.nextInt(60) == 0) {
                tap(B_EXIT);
                return;
            }
            int wantCoins = skill >= 0.99f ? 5 : 1 + r.nextInt(5);
            if (coins != wantCoins) tap(B_COINS);
            else if (skill >= 0.99f && vi != 1) tap(B_VAL);
            else tap(B_DEAL);
        } else if (phase == HOLD) {
            if (!settledAll()) return;
            int mask;
            if (r.nextFloat() < skill) {
                mask = bestHold(cards());
            } else {
                mask = r.nextInt(32);
            }
            for (int i = 0; i < 5; i++) {
                if ((mask >> i & 1) != (held[i] ? 1 : 0)) {
                    tap(B_CARD + i);
                    botT = 0.12f;
                    return;
                }
            }
            tap(B_DRAW);
        }
    }

    private void tap(int id) {
        if (!enabled(id)) return;
        float x = bx[id] + bw[id] / 2, y = by[id] + bh[id] / 2;
        down(x, y);
        up(x, y);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void draw(Gfx c, float t, float b) {
        lay(t, b);
        Cards.felt(c, top, bottom, null, 0);
        drawTable(c);
        for (Cards.Sprite s : gone) s.draw(c, cw, ch, time);
        for (int i = 0; i < 5; i++) {
            Cards.Sprite s = hand[i];
            if (s == null) {
                if (phase == BET && !anyHand) {
                    Cards.card(c, cardX(i), cardsY, cw, ch, 0, 0);
                }
                continue;
            }
            float dy = lift[i] * 12;
            s.y -= dy;
            s.draw(c, cw, ch, time);
            s.y += dy;
            if (lift[i] > 0.05f) {
                float tx = cardX(i);
                c.fillRoundRect(tx + 2, cardsY - dy + ch - 17, cw - 4, 15, 7, 0xFFFFC857);
                c.text("שמור", tx + cw / 2, cardsY - dy + ch - 5.5f, 12, 0xFF1A1300, Gfx.CENTER);
            }
        }
        drawZone(c);
        drawControls(c);
    }

    private void drawTable(Gfx c) {
        // The pay table as a lit panel; the current hand's line is highlighted.
        c.fillRoundRect(6, top + 6, 348, tableH, 10, 0xCC06140E);
        c.strokeRoundRect(6, top + 6, 348, tableH, 10, 1.5f, 0x88FFD27A);
        int hl = phase == HOLD || (phase == BET && resultShown) ? rank : -1;
        float fs = rh < 16 ? 12 : 13;
        for (int k = 0; k < 9; k++) {
            int rk = 9 - k; // royal first
            boolean left = k < 5;
            int row = left ? k : k - 5;
            float colR = left ? 170 : 346, colL = left ? 14 : 190;
            float y = top + 10 + row * rh;
            boolean on = hl == rk;
            if (on) {
                float pulse = resultShown && win > 0 ? 0.75f + 0.25f * (float) Math.sin(time * 9) : 1;
                int a = (int) (255 * pulse);
                c.fillRoundRect(colL - 4, y + 1, colR - colL + 8, rh - 1, 5, (a << 24) | 0xFFC857);
            }
            int ink = on ? 0xFF1A1300 : rk >= TRIPS && rk != 3 ? 0xFFFFE3A3 : 0xFFD9E7DF;
            c.text(NAMES[rk], colR, y + rh - 3.5f, fs, ink, Gfx.RIGHT);
            int val = pay(rk, coins) * VALUES[vi] * coins;
            c.text(Ui.nis(val), colL, y + rh - 3.5f, fs, ink, Gfx.LEFT);
        }
    }

    private void drawZone(Gfx c) {
        // balance pill, left of the zone
        String s = Ui.nis(h.money());
        float w = Math.max(60, c.textWidth(s, 15) + 26);
        float py = cardsY + ch + 6;
        c.fillRoundRect(8, py, w, 22, 11, 0x77000000);
        c.fillCircle(19, py + 11, 6, Ui.ACCENT);
        c.text(s, 8 + w - 8, py + 16.5f, 15, Ui.INK, Gfx.RIGHT);
        float y = phase == HOLD || phase == DEAL ? (cardsY + ch + rowBy) / 2 + 2 : zoneY;
        if (phase == BET && !anyHand) {
            c.text(h.money() < VALUES[0] ? "אין מספיק כסף לשחק" : "בחרו הימור וחלקו", 180, y + 4, 18, h.money() < VALUES[0] ? Ui.BAD : Ui.INK, Gfx.CENTER);
        } else if (resultShown) {
            if (win > 0) {
                c.text("זכית " + Ui.nis((int) shownWin), 180, y + 2, 19 + Math.max(0, 1 - resT * 2) * 4, Ui.ACCENT, Gfx.CENTER);
                if (resT > 0.6f) c.text(NAMES[rank], 180, y + 20, 13, Ui.DIM, Gfx.CENTER);
            } else {
                c.text(msg, 180, y + 4, 16, Ui.DIM, Gfx.CENTER);
            }
        } else if (phase == HOLD) {
            c.text(msg, 180, y, 15, Ui.INK, Gfx.CENTER);
        }
    }

    private void drawControls(Gfx c) {
        if (phase == BET) {
            btn(c, B_VAL, "מטבע " + Ui.nis(VALUES[vi]), false);
            btn(c, B_COINS, coins + (coins == 1 ? " מטבע" : " מטבעות"), false);
            btn(c, B_EXIT, "יציאה", false);
            btn(c, B_MAX, "מקסימום", false);
            btn(c, B_DEAL, "חלק " + Ui.nis(cost()), true);
        } else if (phase == HOLD) {
            boolean all = held[0] && held[1] && held[2] && held[3] && held[4];
            btn(c, B_DRAW, all ? "שמור הכל" : "החלף", true);
        }
    }

    private void btn(Gfx c, int id, String label, boolean primary) {
        boolean on = enabled(id);
        Btn bt = new Btn(id, bx[id], by[id], bw[id], bh[id], label, on, primary);
        int fill = !on ? 0xFF1B2028 : primary ? Ui.ACCENT : 0xFF2C3644;
        int ink = !on ? 0xFF5B6472 : primary ? 0xFF1A1300 : Ui.INK;
        if (id == B_EXIT) {
            c.fillRoundRect(bt.x, bt.y, bt.w, bt.h, 12, 0x88000000);
            c.strokeRoundRect(bt.x, bt.y, bt.w, bt.h, 12, 1.5f, 0x88FFFFFF);
            c.text(label, bt.x + bt.w / 2, bt.y + bt.h / 2 + 5.5f, 15, Ui.INK, Gfx.CENTER);
            return;
        }
        Cards.button(c, bt, pressId == id && pressT > 0 ? 1 : 0, fill, ink);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        c.fillRoundRect(cx - 130, cy - 56, 260, 112, 18, Cards.FELT);
        int[] royal = {8 + 13, 9 + 13, 10 + 13, 11 + 13, 12 + 13};
        for (int i = 0; i < 5; i++) {
            float bob = (float) Math.sin(t * 3 + i) * 2;
            Cards.face(c, cx - 112 + i * 44, cy - 38 + bob - (i == 2 ? 6 : 0), 40, 58, royal[i]);
        }
    }
}
