package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Casino video poker (a venue): Jacks or Better, five-card draw, the full-pay 9/6 table. Choose a
 * coin value and 1-5 coins, deal, tap cards to hold, draw once. The deck is a fresh honest shuffle
 * every hand; nothing looks at it or steers it. The bet leaves the wallet at the deal and the win is
 * paid once, at the draw.
 *
 * pause() never refunds a dealt hand: it draws to the cards held at that moment and pays the result
 * (a hand interrupted while it is still being dealt has nothing held yet, so all five are replaced).
 *
 * Persistent variables: casino.pk.coins (1..5), casino.pk.val (index into VALUES, stored plus one so
 * that an unset variable means "default").
 */
final class PokerMini extends Mini {
    // ================================================================ stakes
    // One block for the economy designer. Every amount is in shekels.
    /** Coin values the player can choose. */
    static final int[] VALUES = {1, 2, 5, 10};
    /** Coins per hand, 1..MAX_COINS. The largest bet is MAX_COINS x the largest value = ₪50. */
    static final int MAX_COINS = 5;
    /** Most that can ride on one hand at any casino table. */
    static final int TABLE_MAX = 100;
    /** Coin value picked on a first visit (index into VALUES). */
    private static final int DEFAULT_VALUE = 1;
    // The pay table below is the full-pay 9/6 table and sets the return to player (99.5% with perfect
    // play); retune the stakes above, not the table, unless the return is meant to change.
    // ================================================================

    // ---- pure rules, shared with the odds check ----
    static final int NONE = 0, JACKS = 1, TWO_PAIR = 2, TRIPS = 3, STRAIGHT = 4, FLUSH = 5, FULL = 6, QUADS = 7,
            STRAIGHT_FLUSH = 8, ROYAL = 9;
    /** Coins paid per coin bet, by hand rank (the royal on a full five-coin bet is handled in {@link #pay}). */
    private static final int[] PER_COIN = {0, 1, 2, 3, 4, 6, 9, 25, 50, 250};
    /** A royal flush on a full MAX_COINS bet pays this per coin instead of 250 (4000 coins). */
    private static final int ROYAL_MAX_PER_COIN = 800;
    static final String[] NAMES = {"", "זוג נסיכים ומעלה", "שני זוגות", "שלישייה", "רצף", "צבע", "פול האוס",
            "רביעייה", "סטרייט פלאש", "רויאל פלאש"};
    private static final String[] SHORT = {"", "נסיכים ומעלה", "שני זוגות", "שלישייה", "רצף", "צבע", "פול האוס",
            "רביעייה", "סטרייט פלאש", "רויאל פלאש"};

    /** Payout per coin: the royal flush pays 250 a coin, and 800 a coin on a full five-coin bet. */
    static int pay(int rank, int coins) {
        return rank == ROYAL && coins == MAX_COINS ? ROYAL_MAX_PER_COIN : PER_COIN[rank];
    }

    /** Shekels paid back for a hand of this rank on a bet of coins x value: the one payout formula. */
    static int payout(int rank, int coins, int value) {
        return pay(rank, coins) * coins * value;
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
            if (same >= 2 && (rank != JACKS || Cards.rank(c[i]) >= 9)) m |= 1 << i;
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
            default:
                return pay(rank(c), MAX_COINS);
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
                sum += pay(rank(hand), MAX_COINS);
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
    private static final int BET = 0, DEAL = 1, HOLD = 2, DRAW = 3;
    // buttons: 0..3 coin-value chips, then the rest
    private static final int B_COINS = 4, B_EXIT = 5, B_MAX = 6, B_DEAL = 7, B_DRAW = 8, B_CARD = 9, NB = 14;

    private Cards.Deck deck;
    private final Cards.Sprite[] hand = new Cards.Sprite[5];
    private final ArrayList<Cards.Sprite> gone = new ArrayList<>();
    private final boolean[] held = new boolean[5];
    private final float[] lift = new float[5];
    private int phase, coins = MAX_COINS, vi = DEFAULT_VALUE, staked, rank = NONE, win, scoring, betShown;
    private boolean exit, resultShown, anyHand, lostLast;
    private float time, resT = 99, botT, pressT, shownWin;
    private int pressId = -1;
    /** What an interrupted hand came to, shown in the strip until the next deal. */
    private int awayBet, awayBack;
    /** Read by CardsCheck: the stake and the return of the latest drawn hand. */
    int lastStake, lastBack;

    private float top, bottom, rh, ch, cw, cardsY, msgY, tableH, trayTop;
    private int cols, rows;
    private boolean compact;
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
        return "בוחרים קלפים לשמור ומחליפים את השאר פעם אחת. זוג נסיכים ומעלה כבר מחזיר את ההימור, ורויאל פלאש משלם בגדול.";
    }

    @Override
    void begin(int round, float difficulty) {
        // Normally pause() has already settled everything; this only guards a begin() without one.
        settleNow();
        int c = (int) h.var("casino.pk.coins");
        coins = c >= 1 && c <= MAX_COINS ? c : MAX_COINS;
        int v = (int) h.var("casino.pk.val") - 1;
        vi = v >= 0 && v < VALUES.length ? v : DEFAULT_VALUE;
        fitBet();
        reset();
        lay(Game.PLAY_TOP, Math.max(h.screenH(), 560) - Game.PLAY_FOOT);
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
        resultShown = anyHand = lostLast = false;
        resT = 99;
        exit = false;
        pressId = -1;
        botT = 0.5f;
    }

    /**
     * Settles a dealt hand at once: draws to whatever is held now and pays it. A hand that is
     * already drawn (paid at the draw) only finishes its show; a bet not yet dealt never left the wallet.
     */
    private void settleNow() {
        if (phase == DEAL || phase == HOLD) doDraw(true);
        if (phase == DRAW) {
            finishHand();
            awayBet = lastStake;
            awayBack = lastBack;
        }
    }

    @Override
    void pause() {
        settleNow();
        reset();
    }

    @Override
    boolean wantsExit() {
        return exit;
    }

    private int cost() {
        return coins * VALUES[vi];
    }

    /** Steps the bet down to one the wallet covers, if there is one (never below one coin of the smallest value). */
    private void fitBet() {
        while (cost() > h.money() && (coins > 1 || vi > 0)) {
            if (vi > 0) vi--;
            else coins--;
        }
    }

    // ------------------------------------------------------------------ layout

    private void lay(float t, float b) {
        top = t;
        bottom = b;
        float hh = b - t;
        compact = hh < 400;
        cols = compact ? 3 : 2;
        rows = compact ? 3 : 5;
        rh = compact ? 15 : Ui.clamp(hh * 0.038f, 16, 19);
        tableH = rows * rh + 6;
        float barY = Cards.Room.barY(b);
        trayTop = Cards.Room.trayTop(b);
        float cardsTop = Cards.Room.below(top) + 3 + tableH + 8;
        float msgH = 40;
        ch = Ui.clamp(trayTop - msgH - cardsTop, 58, 96);
        cw = Math.min(64, ch * 0.7f);
        cardsY = cardsTop + Math.max(0, trayTop - msgH - cardsTop - ch) * 0.35f;
        msgY = Math.max(cardsY + ch + 20, (cardsY + ch + trayTop) / 2 - 1);
        msgY = Math.min(msgY, trayTop - 21);
        float gap = Math.min(10, (344 - 5 * cw) / 4);
        float x0 = 180 - (5 * cw + 4 * gap) / 2;
        for (int i = 0; i < 5; i++) set(B_CARD + i, x0 + i * (cw + gap), cardsY - 12, cw, ch + 12);
        float ty = Cards.Room.trayY(b), r = Cards.Room.CHIP_R;
        for (int i = 0; i < 4; i++) {
            float cx = 8 + r + 3 + i * (2 * r + 10);
            set(i, cx - r - 3, ty - r - 3, 2 * r + 6, 2 * r + 6);
        }
        float cx3 = bx[3] + bw[3] + 8;
        set(B_COINS, cx3, ty - 24, 352 - cx3, 48);
        float p = Cards.Room.PAD, g = Cards.Room.GAP, ew = Cards.Room.EXIT_W, bh2 = Cards.Room.BAR_H;
        set(B_EXIT, p, barY, ew, bh2);
        float x1 = p + ew + g;
        set(B_MAX, x1, barY, 100, bh2);
        set(B_DEAL, x1 + 100 + g, barY, 360 - p - (x1 + 100 + g), bh2);
        set(B_DRAW, x1, barY, 360 - p - x1, bh2);
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
        if (phase != BET || cst > TABLE_MAX || resT < Cards.Room.RESULT_HOLD || !h.pay(-cst)) return;
        staked = cst;
        betShown = cst;
        awayBet = awayBack = 0;
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
        resultShown = false;
        anyHand = true;
        h.sfx(Sfx.WHOOSH);
    }

    /** Replaces every card not held and pays the hand. force skips the wait for the cards to land (pause). */
    private void doDraw(boolean force) {
        if (phase != HOLD && !(force && phase == DEAL)) return;
        if (!force && !settledAll()) return;
        for (int i = 0; i < 5; i++) {
            if (held[i]) continue;
            hand[i].moveTo(400, hand[i].y - lift[i] * 12);
            hand[i].dur = 0.28f;
            gone.add(hand[i]);
            hand[i] = new Cards.Sprite(deck.draw(), -cw - 10, cardsY, cardX(i), cardsY, 0.1f + i * 0.12f, true);
        }
        rank = rank(cards());
        win = payout(rank, coins, VALUES[vi]);
        if (win > 0) h.pay(win);
        lastStake = staked;
        lastBack = win;
        staked = 0;
        phase = DRAW;
        resT = 0;
        h.sfx(Sfx.WHOOSH);
    }

    private void finishHand() {
        resultShown = true;
        phase = BET;
        scoring = scoringMask(cards(), rank);
        for (int i = 0; i < 5; i++) hand[i].glow = (scoring >> i & 1) != 0 ? 1 : 0;
        resT = 0;
        shownWin = 0;
        lostLast = Cards.Room.resultFx(h, 180, cardsY + ch * 0.45f, lastStake, lastBack) == Cards.Room.LOST;
        if (rank == ROYAL) {
            // the one hand worth a fanfare: every card bursts in turn
            for (int i = 0; i < 5; i++) h.burst(cardX(i) + cw / 2, cardsY + ch / 2, i % 2 == 0 ? Ui.ACCENT : 0xFFFF6B6B, 16);
        }
        fitBet();
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
            if (rank >= JACKS) h.sfx(Sfx.TICK);
        } else if (phase == DRAW && settledAll()) {
            finishHand();
        }
        if (resultShown && win > 0) shownWin = Math.min(win, shownWin + win * d / 0.7f);
        return RUNNING;
    }

    // ------------------------------------------------------------------ input

    private boolean enabled(int id) {
        boolean ready = phase == BET && resT >= Cards.Room.RESULT_HOLD;
        switch (id) {
            case 0:
            case 1:
            case 2:
            case 3:
                return ready && VALUES[id] <= h.money();
            case B_COINS:
                return ready && VALUES[vi] <= h.money();
            case B_EXIT:
                return ready;
            case B_MAX:
                return ready && MAX_COINS * VALUES[vi] <= h.money();
            case B_DEAL:
                return ready && cost() <= h.money();
            case B_DRAW:
                return phase == HOLD && settledAll();
            default:
                return id >= B_CARD && id < NB && phase == HOLD && settledAll();
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
            case 0:
            case 1:
            case 2:
            case 3:
                vi = id;
                while (cost() > h.money() && coins > 1) coins--;
                h.setVar("casino.pk.val", vi + 1);
                h.setVar("casino.pk.coins", coins);
                h.sfx(Sfx.TAP);
                break;
            case B_COINS:
                coins = coins % MAX_COINS + 1;
                if (cost() > h.money()) coins = 1;
                h.setVar("casino.pk.coins", coins);
                h.sfx(Sfx.TAP);
                break;
            case B_EXIT:
                exit = true;
                break;
            case B_MAX:
                coins = MAX_COINS;
                h.setVar("casino.pk.coins", coins);
                startDeal();
                break;
            case B_DEAL:
                startDeal();
                break;
            case B_DRAW:
                doDraw(false);
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
            if (resT < Cards.Room.RESULT_HOLD) return;
            if (!enabled(B_DEAL)) {
                tap(B_EXIT);
                return;
            }
            if (anyHand && r.nextInt(60) == 0) {
                tap(B_EXIT);
                return;
            }
            int wantCoins = skill >= 0.99f ? MAX_COINS : 1 + r.nextInt(MAX_COINS);
            if (coins != wantCoins && enabled(B_COINS)) tap(B_COINS);
            else if (skill >= 0.99f && vi != 2 && enabled(2)) tap(2);
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
                if (phase == BET && !anyHand) Cards.card(c, cardX(i), cardsY, cw, ch, 0, 0);
                continue;
            }
            float dy = lift[i] * 10;
            s.y -= dy;
            s.draw(c, cw, ch, time);
            s.y += dy;
            if (lift[i] > 0.05f) {
                float tx = cardX(i);
                c.fillRoundRect(tx + 2, cardsY - dy + ch - 17, cw - 4, 15, 7, 0xFFFFC857);
                c.text("נשמר", tx + cw / 2, cardsY - dy + ch - 5.5f, 12, 0xFF1A1300, Gfx.CENTER);
            }
        }
        drawZone(c);
        drawControls(c);
        int shownBet = phase == BET && !resultShown ? cost() : betShown;
        if (phase == BET && resultShown && resT >= Cards.Room.RESULT_HOLD) shownBet = cost();
        String note = awayBet > 0 ? Cards.Room.awayNote(awayBet, awayBack) : "";
        Cards.Room.strip(c, top, h.money(), shownBet, TABLE_MAX, note, Cards.Room.awayColor(awayBet, awayBack));
    }

    /** What the pay table promises for a rank at the current bet; it is exactly what the draw pays. */
    int tableAmount(int rk) {
        return payout(rk, coins, VALUES[vi]);
    }

    private void drawTable(Gfx c) {
        // The pay table as a lit panel; the current hand's line is highlighted.
        float y0 = Cards.Room.below(top) + 3;
        c.fillRoundRect(6, y0, 348, tableH, 10, 0xCC06140E);
        c.strokeRoundRect(6, y0, 348, tableH, 10, 1.5f, 0x88FFD27A);
        int hl = phase == HOLD || phase == DRAW || (phase == BET && resultShown) ? rank : -1;
        float fs = rh < 16 ? 12 : 13;
        float colW = 348f / cols;
        for (int k = 0; k < 9; k++) {
            int rk = 9 - k; // royal first, reading right to left, then down
            int col = k / rows, row = k % rows;
            float colR = 354 - col * colW - 8, colL = colR - colW + 14;
            float y = y0 + 3 + row * rh;
            boolean on = hl == rk;
            if (on) {
                float pulse = resultShown && win > 0 ? 0.75f + 0.25f * (float) Math.sin(time * 9) : 1;
                int a = (int) (255 * pulse);
                c.fillRoundRect(colL - 4, y + 1, colR - colL + 8, rh - 1, 5, (a << 24) | 0xFFC857);
            }
            int ink = on ? 0xFF1A1300 : rk >= STRAIGHT ? 0xFFFFE3A3 : 0xFFD9E7DF;
            String nm = compact ? SHORT[rk] : NAMES[rk];
            c.text(nm, colR, y + rh - 3.5f, fs, ink, Gfx.RIGHT);
            c.text(Ui.nis(tableAmount(rk)), colL, y + rh - 3.5f, fs, ink, Gfx.LEFT);
        }
    }

    private void drawZone(Gfx c) {
        if (phase == BET && !anyHand) {
            boolean broke = h.money() < VALUES[0];
            c.text(broke ? "אין מספיק כסף לשחק" : h.t("בחר מטבע וחלק", "בחרי מטבע וחלקי"), 180, msgY, 18, broke ? Ui.BAD : Ui.INK, Gfx.CENTER);
        } else if (resultShown) {
            Cards.Room.result(c, 180, msgY, rank == NONE ? "אין זכייה" : NAMES[rank], lastStake, lastBack, resT);
        } else if (phase == HOLD) {
            c.text(h.t("סמן קלפים לשמירה ולחץ החלף", "סמני קלפים לשמירה ולחצי החלף"), 180, msgY, 15, Ui.INK, Gfx.CENTER);
        } else if (phase == DEAL || phase == DRAW) {
            c.text(phase == DEAL ? "מחלק..." : "מחליף...", 180, msgY, 15, Ui.DIM, Gfx.CENTER);
        }
    }

    private float press(int id) {
        return pressId == id && pressT > 0 ? 1 : 0;
    }

    private void drawControls(Gfx c) {
        for (int i = 0; i < 4; i++) {
            Cards.Room.chip(c, bx[i] + bw[i] / 2, by[i] + bh[i] / 2, VALUES[i], enabled(i), i == vi, press(i));
        }
        Cards.Room.action(c, bx[B_COINS], by[B_COINS], bw[B_COINS], bh[B_COINS],
                coins == 1 ? "מטבע אחד" : coins + " מטבעות", enabled(B_COINS), false, press(B_COINS));
        Cards.Room.exit(c, bx[B_EXIT], by[B_EXIT], bw[B_EXIT], bh[B_EXIT], enabled(B_EXIT), press(B_EXIT));
        if (phase == HOLD || phase == DEAL || phase == DRAW) {
            boolean all = held[0] && held[1] && held[2] && held[3] && held[4];
            Cards.Room.action(c, bx[B_DRAW], by[B_DRAW], bw[B_DRAW], bh[B_DRAW], all ? h.t("שמור הכל", "שמרי הכל") : h.t("החלף", "החליפי"),
                    enabled(B_DRAW), true, press(B_DRAW));
        } else {
            Cards.Room.action(c, bx[B_MAX], by[B_MAX], bw[B_MAX], bh[B_MAX], "מקסימום", enabled(B_MAX), false, press(B_MAX));
            Cards.Room.action(c, bx[B_DEAL], by[B_DEAL], bw[B_DEAL], bh[B_DEAL], h.t("חלק ", "חלקי ") + Ui.nis(cost()),
                    enabled(B_DEAL), !lostLast, press(B_DEAL));
        }
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
