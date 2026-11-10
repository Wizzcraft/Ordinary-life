package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Casino blackjack table (a venue). Six decks, reshuffled when a quarter is left; dealer stands on
 * every 17; blackjack pays 3:2; double down on the first two cards; no split, no insurance.
 *
 * Money moves only through h.pay: the bet leaves the wallet when the cards are dealt, and whatever
 * comes back is paid once, when the hand is settled. The deck is never inspected or steered: cards
 * come off a shuffled shoe in order, and every card of a hand is taken from the shoe the moment it is
 * dealt (the animation only shows it).
 *
 * pause() never refunds a dealt hand: the player's hand stands as it is, the dealer plays it out by
 * the rules and the hand is paid. Chips that were only placed (not dealt) never left the wallet.
 *
 * Persistent variable: casino.bj.last (the previous bet).
 */
final class BlackjackMini extends Mini {
    // ================================================================ stakes
    // One block for the economy designer. Every amount is in shekels.
    /** Most that can ride on one hand, a double included. */
    static final int TABLE_MAX = 100;
    /** Largest opening bet: half the table maximum, so that a double always fits under it. */
    static final int MAX_BET = TABLE_MAX / 2;
    /** The chips in the tray. */
    static final int[] CHIPS = {5, 10, 25, 50};
    // ================================================================

    // ---- pure rules, shared with the odds check ----
    static final int HIT = 0, STAND = 1, DOUBLE = 2;
    static final int DECKS = 6;

    /** Blackjack value of a card, aces as 11. */
    static int pip(int card) {
        int r = Cards.rank(card);
        return r <= 8 ? r + 2 : r <= 11 ? 10 : 11;
    }

    static int total(int[] cs, int n) {
        int t = 0, aces = 0;
        for (int i = 0; i < n; i++) {
            t += pip(cs[i]);
            if (Cards.rank(cs[i]) == 12) aces++;
        }
        while (t > 21 && aces > 0) {
            t -= 10;
            aces--;
        }
        return t;
    }

    /** True when an ace is still counted as 11. */
    static boolean soft(int[] cs, int n) {
        int t = 0, aces = 0;
        for (int i = 0; i < n; i++) {
            t += pip(cs[i]);
            if (Cards.rank(cs[i]) == 12) aces++;
        }
        while (t > 21 && aces > 0) {
            t -= 10;
            aces--;
        }
        return aces > 0;
    }

    /**
     * Basic strategy for six decks, dealer stands on 17, no splitting (pairs play as plain totals),
     * no surrender. up is the dealer's upcard value 2..11.
     */
    static int strategy(int[] cs, int n, int up, boolean canDouble) {
        int t = total(cs, n);
        if (t >= 21) return STAND;
        if (!soft(cs, n)) {
            if (t <= 8) return HIT;
            if (t == 9) return canDouble && up >= 3 && up <= 6 ? DOUBLE : HIT;
            if (t == 10) return canDouble && up <= 9 ? DOUBLE : HIT;
            if (t == 11) return canDouble && up <= 10 ? DOUBLE : HIT;
            if (t == 12) return up >= 4 && up <= 6 ? STAND : HIT;
            if (t <= 16) return up <= 6 ? STAND : HIT;
            return STAND;
        }
        if (t <= 14) return canDouble && up >= 5 && up <= 6 ? DOUBLE : HIT;
        if (t <= 16) return canDouble && up >= 4 && up <= 6 ? DOUBLE : HIT;
        if (t == 17) return canDouble && up >= 3 && up <= 6 ? DOUBLE : HIT;
        if (t == 18) {
            if (up >= 3 && up <= 6) return canDouble ? DOUBLE : STAND;
            return up == 2 || up == 7 || up == 8 ? STAND : HIT;
        }
        return STAND;
    }

    /** Cash returned for a stake on a win by blackjack: the stake plus 3:2, a half shekel rounds up. */
    static int blackjackReturn(int stake) {
        return stake + (stake * 3 + 1) / 2;
    }

    // ---- the table ----
    private static final int BET = 0, DEAL = 1, PLAY = 2, DEALER = 3, RESULT = 4;
    private static final int WIN = 0, LOSE = 1, PUSH = 2, NATURAL = 3;
    // button ids: 0..3 chips
    private static final int B_CLEAR = 4, B_DEAL = 5, B_HIT = 6, B_STAND = 7, B_DOUBLE = 8, B_AGAIN = 9, B_CHANGE = 10,
            B_EXIT = 11, NB = 12;

    private Cards.Deck deck;
    private final ArrayList<Cards.Sprite> pl = new ArrayList<>(), dl = new ArrayList<>(), gone = new ArrayList<>();
    private int phase, bet, last, staked, kind, dStep, cueIdx, hands;
    private boolean doubled, natural, playerBust, check, shuffled, exit, lostLast;
    private float time, dealT, dT, resT, reachT, shuffleT, botT;
    private float pileX, pileY;
    private int payoutShown, payoutStake, payoutBack;
    private String msg = "", reason = "";
    private int msgCol = Ui.INK;
    /** What an interrupted hand came to, shown in the strip until the next deal. */
    private int awayBet, awayBack;
    /** Read by CardsCheck: the stake and the return of the latest settled hand. */
    int lastStake, lastBack;

    // chips flying from the tray to the pile
    private final int[] flyV = new int[6];
    private final float[] flyT = new float[6], flyX = new float[6], flyY = new float[6];
    private int flyN;

    // layout, recomputed in lay()
    private float top, bottom, ch, cw, sc, dealerY, playerY, midY, shoeX, shoeY, trayTop, barY;
    private final float[] bx = new float[NB], by = new float[NB], bw = new float[NB], bh = new float[NB];
    private int pressId = -1;
    private float pressT;

    @Override
    int rounds() {
        return 0;
    }

    @Override
    String title() {
        return "בלאקג'ק";
    }

    @Override
    String howTo() {
        return "מניחים הימור ומקבלים קלפים. מתקרבים ל-21 בלי לעבור, והדילר חייב לעצור על 17. בלאקג'ק משלם 3 על 2.";
    }

    @Override
    void begin(int round, float difficulty) {
        // Normally pause() has already settled everything; this only guards a begin() without one.
        settleNow();
        pl.clear();
        dl.clear();
        gone.clear();
        flyN = 0;
        phase = BET;
        bet = 0;
        last = Math.min(MAX_BET, (int) h.var("casino.bj.last"));
        doubled = natural = playerBust = check = exit = lostLast = false;
        resT = dT = dealT = 0;
        payoutShown = 0;
        hands = 0;
        pressId = -1;
        botT = 0.5f;
        msg = "";
        lay(Game.PLAY_TOP, Math.max(h.screenH(), 560) - Game.PLAY_FOOT);
        pileX = 180;
        pileY = betPileY();
    }

    /**
     * Settles a dealt hand at once, honestly: the player's hand stands as it is (a bust is a bust),
     * the dealer turns the hole card and draws to 17 from the same shoe, and the hand is paid. A
     * hand that is already settled, or a bet that was never dealt, is left alone.
     */
    private void settleNow() {
        if (staked <= 0 || phase == BET || phase == RESULT) return;
        int pt = totalOf(pl, false);
        playerBust = pt > 21;
        boolean playerBJ = pl.size() == 2 && pt == 21 && !doubled;
        boolean dealerBJ = dl.size() == 2 && totalOf(dl, false) == 21;
        if (phase == DEAL) natural = playerBJ || (pip(dl.get(0).card) >= 10 && dealerBJ);
        if (!playerBust && !natural) {
            while (totalOf(dl, false) < 17) deal(dl, true, 0, dealerY);
        }
        for (Cards.Sprite s : dl) s.flipUp();
        settle();
        awayBet = lastStake;
        awayBack = lastBack;
    }

    @Override
    void pause() {
        settleNow();
        pl.clear();
        dl.clear();
        gone.clear();
        flyN = 0;
        phase = BET;
        bet = 0;
        doubled = natural = playerBust = check = false;
        pressId = -1;
    }

    @Override
    boolean wantsExit() {
        return exit;
    }

    // ------------------------------------------------------------------ layout

    private void lay(float t, float b) {
        top = t;
        bottom = b;
        float stripB = Cards.Room.below(top);
        barY = Cards.Room.barY(b);
        trayTop = Cards.Room.trayTop(b);
        float hh = b - t;
        sc = Ui.clamp(hh * 0.15f / 46f, 0.85f, 1.25f);
        dealerY = stripB + 46 * sc - 8;
        // two hands and the 44-unit result block between them, all above the bar
        ch = Ui.clamp((barY - 8 - dealerY - 46) / 2, 58, 96);
        cw = ch * 0.7f;
        playerY = barY - 8 - ch;
        midY = (dealerY + ch + playerY) / 2;
        shoeX = 280;
        shoeY = stripB + 4;
        float ty = Cards.Room.trayY(b), d = 2 * Cards.Room.CHIP_R + 18;
        for (int i = 0; i < 4; i++) {
            float cx = 180 + (i - 1.5f) * d;
            set(i, cx - Cards.Room.CHIP_R - 3, ty - Cards.Room.CHIP_R - 3, 2 * Cards.Room.CHIP_R + 6, 2 * Cards.Room.CHIP_R + 6);
        }
        float p = Cards.Room.PAD, g = Cards.Room.GAP, ew = Cards.Room.EXIT_W, bh2 = Cards.Room.BAR_H;
        set(B_EXIT, p, barY, ew, bh2);
        float x1 = p + ew + g;
        set(B_CLEAR, x1, barY, 100, bh2);
        set(B_DEAL, x1 + 100 + g, barY, 360 - p - (x1 + 100 + g), bh2);
        float w3 = (360 - p - x1 - 2 * g) / 3;
        set(B_DOUBLE, x1, barY, w3, bh2);
        set(B_STAND, x1 + w3 + g, barY, w3, bh2);
        set(B_HIT, x1 + 2 * (w3 + g), barY, w3, bh2);
        set(B_CHANGE, x1, barY, 108, bh2);
        set(B_AGAIN, x1 + 108 + g, barY, 360 - p - (x1 + 108 + g), bh2);
    }

    private void set(int id, float x, float y, float w, float h2) {
        bx[id] = x;
        by[id] = y;
        bw[id] = w;
        bh[id] = h2;
    }

    private float betPileY() {
        return (dealerY + 24 + trayTop) / 2 + 26;
    }

    private float playPileY() {
        return playerY - 12;
    }

    // ------------------------------------------------------------------ rules in play

    private int[] ids(ArrayList<Cards.Sprite> hand, boolean onlyShown) {
        int[] a = new int[hand.size()];
        int n = 0;
        for (Cards.Sprite s : hand) {
            if (onlyShown && s.flipTarget < 1) continue;
            a[n++] = s.card;
        }
        return java.util.Arrays.copyOf(a, n);
    }

    private int totalOf(ArrayList<Cards.Sprite> hand, boolean onlyShown) {
        int[] a = ids(hand, onlyShown);
        return total(a, a.length);
    }

    private boolean settledAll() {
        for (Cards.Sprite s : pl) if (!s.settled()) return false;
        for (Cards.Sprite s : dl) if (!s.settled()) return false;
        return true;
    }

    private boolean canDouble() {
        return phase == PLAY && pl.size() == 2 && !doubled && 2 * staked <= TABLE_MAX && h.money() >= staked && settledAll();
    }

    private void refan(ArrayList<Cards.Sprite> hand, float y) {
        int n = hand.size();
        if (n == 0) return;
        float ox = n > 1 ? Math.min(cw * 0.58f, (300 - cw) / (n - 1)) : 0;
        float x0 = 186 - (cw + (n - 1) * ox) / 2;
        for (int i = 0; i < n; i++) hand.get(i).retarget(x0 + i * ox, y);
    }

    private Cards.Sprite deal(ArrayList<Cards.Sprite> hand, boolean up, float delay, float y) {
        Cards.Sprite s = new Cards.Sprite(deck.draw(), shoeX + 4, shoeY + 6, 180, y, delay, up);
        hand.add(s);
        refan(hand, y);
        return s;
    }

    private void sweep() {
        for (ArrayList<Cards.Sprite> hand : java.util.List.of(pl, dl)) {
            for (Cards.Sprite s : hand) {
                s.moveTo(-90, s.y);
                s.dur = 0.32f;
                gone.add(s);
            }
            hand.clear();
        }
        payoutShown = 0;
    }

    private void startDeal() {
        if (bet <= 0 || bet > MAX_BET || !h.pay(-bet)) return;
        staked = bet;
        last = bet;
        h.setVar("casino.bj.last", last);
        awayBet = awayBack = 0;
        if (gone.isEmpty() && (!pl.isEmpty() || !dl.isEmpty())) sweep();
        if (deck == null) deck = new Cards.Deck(DECKS);
        shuffled = false;
        float lead = 0.1f;
        if (deck.left() < deck.size() / 4 || deck.left() == deck.size()) {
            // The cut card came out, so the whole shoe is shuffled before this hand.
            deck.shuffle(h.rnd());
            shuffled = true;
            shuffleT = 1.0f;
            lead = 1.0f;
            h.sfx(Sfx.POUR);
        }
        doubled = natural = playerBust = check = false;
        deal(pl, true, lead, playerY);
        deal(dl, true, lead + 0.3f, dealerY);
        deal(pl, true, lead + 0.6f, playerY);
        deal(dl, false, lead + 0.9f, dealerY);
        phase = DEAL;
        dealT = 0;
        cueIdx = 0;
        resT = 0;
        payoutShown = 0;
        kind = -1;
        msg = shuffled ? "מערבבים את החבילה" : "הדילר מחלק";
        msgCol = Ui.DIM;
        pileX = 180;
    }

    private void afterDeal() {
        int pt = totalOf(pl, false);
        int up = pip(dl.get(0).card);
        boolean dealerBJ = up >= 10 && totalOf(dl, false) == 21;
        if (pt == 21 || dealerBJ) {
            natural = true;
            startDealer();
        } else {
            phase = PLAY;
            msg = "עוד קלף, לעצור או להכפיל?";
            msgCol = Ui.INK;
        }
    }

    private void startDealer() {
        phase = DEALER;
        dStep = 0;
        dT = 0;
        msg = "תור הדילר";
        msgCol = Ui.DIM;
    }

    private void hit() {
        if (phase != PLAY || !settledAll()) return;
        deal(pl, true, 0, playerY);
        h.sfx(Sfx.WHOOSH);
        reachT = 0.3f;
        check = true;
    }

    private void stand() {
        if (phase != PLAY || !settledAll()) return;
        h.sfx(Sfx.TAP);
        startDealer();
    }

    private void doubleDown() {
        int add = staked;
        if (!canDouble() || !h.pay(-add)) return;
        staked += add;
        doubled = true;
        h.text(180, midY - 24, "הכפלה!", Ui.ACCENT);
        h.sfx(Sfx.COIN);
        deal(pl, true, 0.15f, playerY);
        check = true;
    }

    private void settle() {
        int pt = totalOf(pl, false), dt2 = totalOf(dl, false);
        boolean dealerBJ = dl.size() == 2 && dt2 == 21;
        boolean playerBJ = pl.size() == 2 && pt == 21 && !doubled;
        int back;
        if (pt > 21) {
            kind = LOSE;
            back = 0;
            reason = "חרגת מ-21";
        } else if (playerBJ && !dealerBJ) {
            kind = NATURAL;
            back = blackjackReturn(staked);
            reason = "בלאקג'ק!";
        } else if (dealerBJ && !playerBJ) {
            kind = LOSE;
            back = 0;
            reason = "לדילר יש בלאקג'ק";
        } else if (dealerBJ) {
            kind = PUSH;
            back = staked;
            reason = "בלאקג'ק מול בלאקג'ק";
        } else if (dt2 > 21) {
            kind = WIN;
            back = staked * 2;
            reason = "הדילר חרג";
        } else if (pt > dt2) {
            kind = WIN;
            back = staked * 2;
            reason = pt + " מול " + dt2;
        } else if (pt == dt2) {
            kind = PUSH;
            back = staked;
            reason = "תיקו " + pt;
        } else {
            kind = LOSE;
            back = 0;
            reason = pt + " מול " + dt2;
        }
        int stakeNow = staked;
        if (back > 0) h.pay(back);
        staked = 0;
        lastStake = stakeNow;
        lastBack = back;
        payoutStake = stakeNow;
        payoutBack = back;
        payoutShown = back > stakeNow ? back - stakeNow : 0;
        phase = RESULT;
        resT = 0;
        hands++;
        pileX = 300;
        pileY = playPileY();
        lostLast = Cards.Room.resultFx(h, 180, midY - 22, stakeNow, back) == Cards.Room.LOST;
        if (kind == WIN || kind == NATURAL) for (Cards.Sprite s : pl) s.glow = 1;
    }

    // ------------------------------------------------------------------ update

    @Override
    int update(float d) {
        time += d;
        if (botT > 0) botT -= d;
        if (pressT > 0) pressT -= d;
        if (reachT > 0) reachT -= d;
        for (Cards.Sprite s : pl) s.update(d);
        for (Cards.Sprite s : dl) s.update(d);
        for (int i = gone.size() - 1; i >= 0; i--) {
            Cards.Sprite s = gone.get(i);
            s.update(d);
            if (s.t >= 1) gone.remove(i);
        }
        // chips from the tray land on the pile
        for (int i = flyN - 1; i >= 0; i--) {
            flyT[i] += d / 0.22f;
            if (flyT[i] >= 1) {
                bet += flyV[i];
                h.sfx(Sfx.COIN);
                flyV[i] = flyV[flyN - 1];
                flyT[i] = flyT[flyN - 1];
                flyX[i] = flyX[flyN - 1];
                flyY[i] = flyY[flyN - 1];
                flyN--;
            }
        }
        float k = Math.min(1, d * 9);
        pileX += (targetPileX() - pileX) * k;
        pileY += (targetPileY() - pileY) * k;

        switch (phase) {
            case DEAL:
                dealT += d;
                if (shuffleT > 0) shuffleT -= d;
                float lead = shuffled ? 1.0f : 0.1f;
                while (cueIdx < 4 && dealT >= lead + cueIdx * 0.3f) {
                    h.sfx(Sfx.WHOOSH);
                    reachT = 0.3f;
                    cueIdx++;
                }
                if (cueIdx == 4 && settledAll()) afterDeal();
                break;
            case PLAY:
                if (check && settledAll()) {
                    check = false;
                    int t = totalOf(pl, false);
                    if (t > 21) {
                        playerBust = true;
                        h.text(180, midY - 24, "חרגת!", Ui.BAD);
                        h.shake(5);
                        h.sfx(Sfx.BAD);
                        startDealer();
                    } else if (t == 21 || doubled) {
                        startDealer();
                    } else {
                        msg = "עוד קלף, לעצור או להכפיל?";
                    }
                }
                break;
            case DEALER:
                if (!settledAll()) break;
                dT += d;
                if (dStep == 0) {
                    dl.get(1).flipUp();
                    h.sfx(Sfx.TICK);
                    dStep = 1;
                    dT = 0;
                } else if (dStep == 1) {
                    if (dT >= (playerBust || natural ? 0.45f : 0.65f)) dStep = 2;
                } else {
                    int t = totalOf(dl, false);
                    if (!playerBust && !natural && t < 17) {
                        deal(dl, true, 0.1f, dealerY);
                        h.sfx(Sfx.WHOOSH);
                        reachT = 0.3f;
                        dStep = 1;
                        dT = 0;
                        msg = "הדילר מושך";
                    } else {
                        settle();
                    }
                }
                break;
            case RESULT:
                resT += d;
                break;
            default:
        }
        return RUNNING;
    }

    private float targetPileX() {
        return phase == BET || phase == DEAL ? 180 : 300;
    }

    private float targetPileY() {
        return phase == BET ? betPileY() : playPileY();
    }

    // ------------------------------------------------------------------ input

    private boolean between() {
        return phase == BET || (phase == RESULT && resT >= Cards.Room.RESULT_HOLD);
    }

    private boolean visible(int id) {
        switch (id) {
            case 0:
            case 1:
            case 2:
            case 3:
            case B_CLEAR:
            case B_DEAL:
                return phase == BET;
            case B_HIT:
            case B_STAND:
            case B_DOUBLE:
                return phase == PLAY || phase == DEAL || phase == DEALER;
            case B_AGAIN:
            case B_CHANGE:
                return phase == RESULT;
            case B_EXIT:
                return true;
            default:
                return false;
        }
    }

    private int flying() {
        int s = 0;
        for (int i = 0; i < flyN; i++) s += flyV[i];
        return s;
    }

    private int cap() {
        return Math.min(MAX_BET, h.money());
    }

    private boolean enabled(int id) {
        if (!visible(id)) return false;
        switch (id) {
            case 0:
            case 1:
            case 2:
            case 3:
                return bet + flying() + CHIPS[id] <= cap() && flyN < 6;
            case B_CLEAR:
                return bet > 0 || flyN > 0 || (last > 0 && last <= cap());
            case B_DEAL:
                return bet > 0 && flyN == 0 && bet <= h.money();
            case B_HIT:
            case B_STAND:
                return phase == PLAY && settledAll() && !check;
            case B_DOUBLE:
                return canDouble() && !check;
            case B_AGAIN:
                return resT >= Cards.Room.RESULT_HOLD && last > 0 && last <= Math.min(MAX_BET, h.money());
            case B_CHANGE:
                return resT >= Cards.Room.RESULT_HOLD;
            case B_EXIT:
                return between() && flyN == 0;
            default:
                return true;
        }
    }

    private int hitId(float x, float y) {
        for (int i = 0; i < NB; i++) {
            if (!enabled(i)) continue;
            if (Ui.in(x, y, bx[i], by[i], bw[i], bh[i])) return i;
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
            case 3: {
                int n = flyN++;
                flyV[n] = CHIPS[id];
                flyT[n] = 0;
                flyX[n] = bx[id] + bw[id] / 2;
                flyY[n] = by[id] + bh[id] / 2;
                h.sfx(Sfx.TAP);
                break;
            }
            case B_CLEAR:
                if (bet == 0 && flyN == 0) {
                    bet = Math.min(last, cap() / 5 * 5);
                    h.sfx(Sfx.COIN);
                } else {
                    bet = 0;
                    flyN = 0;
                    h.sfx(Sfx.TAP);
                }
                break;
            case B_DEAL:
                startDeal();
                break;
            case B_HIT:
                hit();
                break;
            case B_STAND:
                stand();
                break;
            case B_DOUBLE:
                doubleDown();
                break;
            case B_AGAIN:
                sweep();
                bet = last;
                startDeal();
                break;
            case B_CHANGE:
                sweep();
                bet = 0;
                phase = BET;
                msg = "";
                pileX = 300;
                h.sfx(Sfx.TAP);
                break;
            case B_EXIT:
                exit = true;
                break;
            default:
        }
    }

    // ------------------------------------------------------------------ bot

    @Override
    void bot(Random r, float skill) {
        if (botT > 0) return;
        float wait = 0.3f + r.nextFloat() * 0.35f;
        switch (phase) {
            case BET: {
                if (flyN > 0) return;
                int target = skill >= 0.99f ? 10 : (1 + r.nextInt(3)) * 10 + (r.nextInt(3) == 0 ? 15 : 0);
                if (bet == 0 && h.money() < CHIPS[0]) {
                    tap(B_EXIT);
                    botT = wait;
                    return;
                }
                if (bet < target && enabled(1)) {
                    tap(r.nextInt(3) == 0 && enabled(0) ? 0 : 1);
                } else if (bet > 0) {
                    tap(B_DEAL);
                } else {
                    tap(enabled(0) ? 0 : B_EXIT);
                }
                botT = wait;
                break;
            }
            case PLAY:
                if (!settledAll() || check) return;
                if (r.nextFloat() < skill) {
                    int[] a = ids(pl, false);
                    int act = strategy(a, a.length, pip(dl.get(0).card), canDouble());
                    tap(act == HIT ? B_HIT : act == STAND ? B_STAND : B_DOUBLE);
                } else {
                    int pick = r.nextInt(3);
                    tap(pick == 0 ? B_HIT : pick == 1 ? B_STAND : (canDouble() ? B_DOUBLE : B_HIT));
                }
                botT = wait;
                break;
            case RESULT:
                if (resT < Cards.Room.RESULT_HOLD + 0.2f) return;
                if (hands >= 25 && r.nextInt(40) == 0) tap(B_EXIT);
                else if (enabled(B_AGAIN) && (skill >= 0.99f || r.nextBoolean())) tap(B_AGAIN);
                else tap(B_CHANGE);
                botT = wait;
                break;
            default:
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
        // dealer behind the rim, shoe at the right
        float reach = reachT > 0 ? (float) Math.sin(Math.PI * Ui.clamp(reachT / 0.3f, 0, 1)) : 0;
        Cards.dealer(c, 180, Cards.Room.below(top) + 2, sc, time, reach * 0.8f, shoeX + 4, shoeY + 24);
        drawShoe(c);
        for (Cards.Sprite s : gone) s.draw(c, cw, ch, time);
        for (Cards.Sprite s : dl) s.draw(c, cw, ch, time);
        for (Cards.Sprite s : pl) s.draw(c, cw, ch, time);
        refan(dl, dealerY);
        refan(pl, playerY);
        drawBadges(c);
        drawMessage(c);
        drawPiles(c);
        drawControls(c);
        int shownBet = phase == BET ? bet + flying() : phase == RESULT ? payoutStake : staked;
        String note = awayBet > 0 ? Cards.Room.awayNote(awayBet, awayBack) : shuffleT > 0 ? "מערבבים" : "";
        int noteCol = awayBet > 0 ? Cards.Room.awayColor(awayBet, awayBack) : Ui.DIM;
        Cards.Room.strip(c, top, h.money(), shownBet, TABLE_MAX, note, noteCol);
    }

    private void drawShoe(Gfx c) {
        float x = shoeX, y = shoeY;
        c.fillPoly(new float[]{x, y + 4, x + 62, y + 14, x + 62, y + 36, x, y + 28}, 0xFF2A1F18);
        c.fillPoly(new float[]{x + 4, y + 8, x + 58, y + 17, x + 58, y + 22, x + 4, y + 13}, 0xFF7A1F3A);
        c.fillPoly(new float[]{x, y + 4, x + 62, y + 14, x + 62, y + 18, x, y + 8}, 0xFF4A382B);
        if (shuffleT > 0) {
            float j = (float) Math.sin(time * 40) * 2;
            c.fillRect(x + j, y + 36, 62, 3, 0x44FFD27A);
        }
        float frac = deck == null ? 1 : deck.left() / (float) deck.size();
        c.fillRoundRect(x + 2, y + 41, 58, 5, 2.5f, 0x55000000);
        c.fillRoundRect(x + 2, y + 41, 58 * frac, 5, 2.5f, frac < 0.3f ? 0xFFFF9F5A : 0xFFFFD27A);
    }

    private void drawBadges(Gfx c) {
        if (!pl.isEmpty()) badge(c, pl, playerY, false);
        if (!dl.isEmpty()) badge(c, dl, dealerY, true);
    }

    private void badge(Gfx c, ArrayList<Cards.Sprite> hand, float y, boolean dealerHand) {
        Cards.Sprite first = hand.get(0);
        if (!first.visible || first.t < 1) return;
        boolean hidden = dealerHand && hand.size() > 1 && hand.get(1).flipTarget < 1;
        int[] a = ids(hand, hidden);
        int tot = total(a, a.length);
        String txt = String.valueOf(tot);
        if (hidden) txt = tot + "+?";
        else if (soft(a, a.length) && tot < 21) txt = tot + " רך";
        float w = Math.max(40, c.textWidth(txt, 15) + 18);
        float x = Math.max(6, hand.get(0).tx - w - 6), yy = y + ch / 2 - 12;
        if (hand.size() > 1) {
            int fill = tot > 21 ? Ui.BAD : tot == 21 ? Ui.ACCENT : 0xFF0A3524;
            float pulse = (phase == DEALER && dealerHand && dStep == 1 ? (float) Math.sin(time * 14) * 1.5f : 0);
            c.fillRoundRect(x - pulse, yy - pulse, w + 2 * pulse, 24 + 2 * pulse, 12, 0x66000000);
            c.fillRoundRect(x - pulse, yy - pulse - 1, w + 2 * pulse, 24 + 2 * pulse, 12, fill);
            c.text(txt, x + w / 2, yy + 17, 15, tot >= 21 ? 0xFF1A1300 : Ui.INK, Gfx.CENTER);
        }
    }

    private void drawMessage(Gfx c) {
        if (phase == BET) {
            float y = dealerY + 22;
            if (h.money() < CHIPS[0]) {
                c.text("אין מספיק כסף לשחק", 180, y, 18, Ui.BAD, Gfx.CENTER);
                return;
            }
            int shown = bet + flying();
            c.text(shown > 0 ? "הימור " + Ui.nis(shown) : "מה ההימור?", 180, y, shown > 0 ? 22 : 18, shown > 0 ? Ui.ACCENT : Ui.INK,
                    Gfx.CENTER);
            c.text("בלאקג'ק משלם 3 על 2 · הדילר עוצר על 17", 180, y + 20, 12, 0x99FFD27A, Gfx.CENTER);
            return;
        }
        if (phase == RESULT) {
            Cards.Room.result(c, 180, midY + 2, reason, payoutStake, payoutBack, resT);
            return;
        }
        c.text(msg, 180, midY + 6, 16, msgCol, Gfx.CENTER);
    }

    private void drawPiles(Gfx c) {
        float r = Ui.clamp(ch * 0.26f, 15, 22);
        if (phase == BET) {
            Cards.chipPile(c, pileX, pileY, r + 2, bet, 1);
        } else if (phase == RESULT) {
            float rt = resT;
            int amt = payoutStake;
            if (kind == LOSE) {
                // the dealer rakes the chips away
                float k = Ui.clamp((rt - 0.3f) / 0.6f, 0, 1);
                Cards.chipPile(c, pileX + (180 - pileX) * k, pileY + (dealerY + 20 - pileY) * k, r, amt, 1 - k);
            } else if (kind == PUSH) {
                float k = Ui.clamp((rt - 0.8f) / 0.5f, 0, 1);
                Cards.chipPile(c, pileX + 30 * k, pileY + 110 * k, r, amt, 1 - k);
            } else {
                float in = Ui.clamp(rt / 0.5f, 0, 1);
                in = 1 - (1 - in) * (1 - in);
                float out = Ui.clamp((rt - 1.0f) / 0.5f, 0, 1);
                float ox = 40 * out, oy = 120 * out;
                Cards.chipPile(c, pileX + ox, pileY + oy, r, amt, 1 - out);
                float py = dealerY + 24 + (pileY - dealerY - 24) * in;
                Cards.chipPile(c, pileX - 2.3f * r + ox, py + oy, r, payoutShown, 1 - out);
            }
        } else {
            Cards.chipPile(c, pileX, pileY, r, staked, 1);
        }
        // chips in flight from the tray
        for (int i = 0; i < flyN; i++) {
            float k = Ui.clamp(flyT[i], 0, 1);
            float e = 1 - (1 - k) * (1 - k);
            float fx = flyX[i] + (pileX - flyX[i]) * e, fy = flyY[i] + (pileY - 8 - flyY[i]) * e - (float) Math.sin(Math.PI * k) * 18;
            Cards.chipFace(c, fx, fy, Cards.Room.CHIP_R * (1 - 0.3f * e), flyV[i], true, 0);
        }
    }

    private float press(int id) {
        return pressId == id && pressT > 0 ? 1 : 0;
    }

    private void drawControls(Gfx c) {
        if (phase == BET) {
            for (int i = 0; i < 4; i++) {
                Cards.Room.chip(c, bx[i] + bw[i] / 2, by[i] + bh[i] / 2, CHIPS[i], enabled(i), false, press(i));
            }
            boolean useLast = bet == 0 && flyN == 0 && last > 0;
            btn(c, B_CLEAR, useLast ? "כמו קודם" : h.t("נקה", "נקי"), false);
            int shown = bet + flying();
            btn(c, B_DEAL, shown > 0 ? h.t("חלק ", "חלקי ") + Ui.nis(shown) : h.t("חלק", "חלקי"), true);
        } else if (phase == RESULT) {
            btn(c, B_CHANGE, h.t("שנה הימור", "שני הימור"), false);
            btn(c, B_AGAIN, "שוב " + Ui.nis(last), !lostLast);
        } else {
            btn(c, B_DOUBLE, h.t("הכפל", "הכפילי"), false);
            btn(c, B_STAND, h.t("עצור", "עצרי"), false);
            btn(c, B_HIT, "עוד קלף", true);
        }
        Cards.Room.exit(c, bx[B_EXIT], by[B_EXIT], bw[B_EXIT], bh[B_EXIT], enabled(B_EXIT), press(B_EXIT));
    }

    private void btn(Gfx c, int id, String label, boolean gold) {
        Cards.Room.action(c, bx[id], by[id], bw[id], bh[id], label, enabled(id), gold, press(id));
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        c.fillRoundRect(cx - 120, cy - 60, 240, 120, 18, Cards.FELT);
        float bob = (float) Math.sin(t * 2) * 2;
        Cards.face(c, cx - 56, cy - 40 + bob, 56, 80, 12); // ace of spades
        Cards.face(c, cx - 14, cy - 44 - bob, 56, 80, 11 + 13); // king of hearts
        Cards.chipPile(c, cx + 80, cy + 44, 18, 35, 1);
    }
}
