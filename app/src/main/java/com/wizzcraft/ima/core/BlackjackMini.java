package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Casino blackjack table (a venue). Six decks, reshuffled when a quarter is left; dealer stands on
 * every 17; blackjack pays 3:2; double down on the first two cards; no split, no insurance.
 *
 * Money moves only through h.pay: the bet leaves the wallet when the cards are dealt (the stake),
 * and whatever comes back is paid once, when the hand is settled. pause() returns an open stake.
 * The deck is never inspected or steered: cards come off a shuffled shoe in order.
 *
 * Persistent variable: casino.bj.last (the previous bet).
 */
final class BlackjackMini extends Mini {
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
    private static final int MAX_BET = 500;
    private static final int[] CHIPS = {10, 25, 50, 100};
    // button ids: 0..3 chips
    private static final int B_CLEAR = 4, B_DEAL = 5, B_HIT = 6, B_STAND = 7, B_DOUBLE = 8, B_AGAIN = 9, B_CHANGE = 10,
            B_EXIT = 11, NB = 12;

    private Cards.Deck deck;
    private final ArrayList<Cards.Sprite> pl = new ArrayList<>(), dl = new ArrayList<>(), gone = new ArrayList<>();
    private int phase, bet, last, staked, stake0, kind, dStep, cueIdx, hands;
    private boolean doubled, natural, playerBust, check, shuffled, exit, settledOnce;
    private float time, dt, dealT, dT, resT, reachT, shuffleT, msgPop, botT;
    private float pileX, pileY;
    private int payoutShown;
    private String msg = "";
    private int msgCol = Ui.INK;

    // chips flying from the tray to the pile
    private final int[] flyV = new int[6];
    private final float[] flyT = new float[6], flyX = new float[6], flyY = new float[6];
    private int flyN;

    // layout, recomputed in lay()
    private float top, bottom, ch, cw, sc, dealerY, playerY, btnY, midY, chipY, chipR, shoeX, shoeY;
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
        return "מניחים הימור ומקבלים קלפים. מתקרבים ל-21 בלי לעבור, והדילר חייב לעצור על 17. בלאקג'ק משלם שלוש על שתיים.";
    }

    @Override
    void begin(int round, float difficulty) {
        if (staked > 0) {
            h.pay(staked);
            staked = 0;
        }
        pl.clear();
        dl.clear();
        gone.clear();
        flyN = 0;
        phase = BET;
        bet = 0;
        last = (int) h.var("casino.bj.last");
        doubled = natural = playerBust = check = exit = false;
        resT = dT = dealT = 0;
        payoutShown = 0;
        hands = 0;
        pressId = -1;
        botT = 0.5f;
        msg = "";
        lay(166, Math.max(h.screenH(), 560) - 96);
        pileX = 180;
        pileY = betPileY();
    }

    @Override
    void pause() {
        if (staked > 0) {
            h.pay(staked);
            staked = 0;
        }
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
        float hh = b - t;
        ch = Ui.clamp(hh * 0.21f, 58, 96);
        cw = ch * 0.7f;
        sc = Ui.clamp(hh * 0.15f / 46f, 0.85f, 1.25f);
        dealerY = top + 46 * sc - 6;
        btnY = bottom - 56;
        playerY = btnY - 10 - ch;
        midY = (dealerY + ch + playerY) / 2 + 4;
        chipR = Ui.clamp(hh * 0.075f, 23, 30);
        chipY = btnY - 10 - chipR;
        shoeX = 276;
        shoeY = top + 4;
        for (int i = 0; i < 4; i++) {
            bx[i] = 180 + (i - 1.5f) * (chipR * 2 + 18) - chipR - 4;
            by[i] = chipY - chipR - 4;
            bw[i] = bh[i] = chipR * 2 + 8;
        }
        set(B_CLEAR, 12, btnY, 104, 48);
        set(B_DEAL, 128, btnY, 220, 48);
        set(B_DOUBLE, 12, btnY, 104, 48);
        set(B_STAND, 128, btnY, 104, 48);
        set(B_HIT, 244, btnY, 104, 48);
        set(B_CHANGE, 12, btnY, 128, 48);
        set(B_AGAIN, 152, btnY, 196, 48);
        set(B_EXIT, 8, top + 6, 70, 44);
    }

    private void set(int id, float x, float y, float w, float h2) {
        bx[id] = x;
        by[id] = y;
        bw[id] = w;
        bh[id] = h2;
    }

    private float betPileY() {
        return chipY - chipR - 30;
    }

    private float playPileY() {
        return playerY - 14;
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
        return phase == PLAY && pl.size() == 2 && !doubled && h.money() >= stake0 && settledAll();
    }

    private void refan(ArrayList<Cards.Sprite> hand, float y) {
        int n = hand.size();
        if (n == 0) return;
        float ox = n > 1 ? Math.min(cw * 0.58f, (330 - cw) / (n - 1)) : 0;
        float x0 = 180 - (cw + (n - 1) * ox) / 2;
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
        if (bet <= 0 || !h.pay(-bet)) return;
        staked = stake0 = bet;
        last = bet;
        h.setVar("casino.bj.last", last);
        if (gone.isEmpty() && (!pl.isEmpty() || !dl.isEmpty())) sweep();
        if (deck == null) deck = new Cards.Deck(DECKS);
        shuffled = false;
        float lead = 0.1f;
        if (deck.left() < deck.size() / 4 || deck.left() == deck.size()) {
            // Not seen from the player's seat: the cut card came out, so the shoe is shuffled whole.
            deck.shuffle(h.rnd());
            shuffled = true;
            shuffleT = 1.0f;
            lead = 1.0f;
            h.sfx(Sfx.POUR);
        }
        doubled = natural = playerBust = false;
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
        msg = shuffled ? "מערבבים את החבילה" : "מחלק";
        msgCol = Ui.DIM;
        pileX = 180;
    }

    private void afterDeal() {
        int pt = totalOf(pl, false);
        int up = pip(dl.get(0).card);
        boolean peek = up >= 10;
        boolean dealerBJ = peek && totalOf(dl, false) == 21;
        boolean playerBJ = pt == 21;
        if (playerBJ || dealerBJ) {
            natural = true;
            startDealer();
        } else {
            phase = PLAY;
            msg = "משוך, עצור או הכפל";
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
        if (!canDouble() || !h.pay(-stake0)) return;
        staked += stake0;
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
        if (playerBust) {
            kind = LOSE;
            back = 0;
        } else if (playerBJ && !dealerBJ) {
            kind = NATURAL;
            back = blackjackReturn(staked);
        } else if (dealerBJ && !playerBJ) {
            kind = LOSE;
            back = 0;
        } else if (dealerBJ) {
            kind = PUSH;
            back = staked;
        } else if (dt2 > 21 || pt > dt2) {
            kind = WIN;
            back = staked * 2;
        } else if (pt == dt2) {
            kind = PUSH;
            back = staked;
        } else {
            kind = LOSE;
            back = 0;
        }
        int stakeNow = staked;
        if (back > 0) h.pay(back);
        staked = 0;
        payoutShown = back > stakeNow ? back - stakeNow : 0;
        phase = RESULT;
        resT = 0;
        hands++;
        settledOnce = true;
        pileX = 300;
        pileY = playPileY();
        float cx = 180;
        switch (kind) {
            case NATURAL:
                msg = "בלאקג'ק!";
                msgCol = Ui.ACCENT;
                h.text(cx, midY - 18, "+" + Ui.nis(payoutShown), Ui.ACCENT);
                h.burst(cx, midY, Ui.ACCENT, 26);
                h.burst(cx, midY, 0xFFFF6B6B, 14);
                h.sfx(Sfx.PERFECT);
                break;
            case WIN:
                msg = dt2 > 21 ? "הדילר חרג! ניצחת" : "ניצחת";
                msgCol = Ui.GOOD;
                h.text(cx, midY - 18, "+" + Ui.nis(payoutShown), Ui.GOOD);
                h.burst(cx, midY, Ui.ACCENT, 14);
                h.sfx(Sfx.COIN);
                break;
            case PUSH:
                msg = "תיקו, ההימור חוזר";
                msgCol = Ui.DIM;
                h.sfx(Sfx.TICK);
                break;
            default:
                msg = playerBust ? "חרגת מ-21" : "הדילר ניצח";
                msgCol = Ui.BAD;
                h.text(cx, midY - 18, "-" + Ui.nis(stakeNow), Ui.BAD);
                h.shake(playerBust ? 6 : 3);
                h.sfx(Sfx.BAD);
        }
        if (kind == WIN || kind == NATURAL) for (Cards.Sprite s : pl) s.glow = 1;
        // The bet is "on the table" until the chips are swept, so keep showing what was staked.
        payoutStake = stakeNow;
    }

    private int payoutStake;

    // ------------------------------------------------------------------ update

    @Override
    int update(float d) {
        dt = d;
        time += d;
        if (botT > 0) botT -= d;
        if (pressT > 0) pressT -= d;
        if (msgPop > 0) msgPop -= d;
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
                        msg = "משוך, עצור או הכפל";
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
        return phase == BET ? 180 : phase == DEAL ? 180 : 300;
    }

    private float targetPileY() {
        return phase == BET ? betPileY() : playPileY();
    }

    // ------------------------------------------------------------------ input

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
                return phase == PLAY;
            case B_AGAIN:
            case B_CHANGE:
                return phase == RESULT && resT > 1.5f;
            case B_EXIT:
                return phase == BET || (phase == RESULT && resT > 1.5f);
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
                return settledAll() && !check;
            case B_DOUBLE:
                return canDouble() && !check;
            case B_AGAIN:
                return last <= h.money() && last > 0;
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
                    bet = Math.min(last, cap() / 10 * 10);
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
                if (bet == 0 && h.money() < 10) {
                    tap(B_EXIT);
                    botT = wait;
                    return;
                }
                if (bet < target && enabled(0)) {
                    tap(r.nextInt(3) == 0 && enabled(1) ? 1 : 0);
                } else if (bet > 0) {
                    tap(B_DEAL);
                } else {
                    tap(B_CLEAR);
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
                if (resT < 1.5f) return;
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
        Cards.felt(c, top, bottom, phase == BET ? "בלאקג'ק משלם 3 על 2 · הדילר עוצר על 17" : null, top + 7 + 46 * sc + 52);
        // dealer behind the rim, shoe at the right
        float reach = reachT > 0 ? (float) Math.sin(Math.PI * Ui.clamp(reachT / 0.3f, 0, 1)) : 0;
        Cards.dealer(c, 180, top + 7, sc, time, reach * 0.8f, shoeX + 4, shoeY + 24);
        drawShoe(c);
        for (Cards.Sprite s : gone) s.draw(c, cw, ch, time);
        for (Cards.Sprite s : dl) {
            s.draw(c, cw, ch, time);
        }
        for (Cards.Sprite s : pl) {
            s.draw(c, cw, ch, time);
        }
        refan(dl, dealerY);
        refan(pl, playerY);
        drawBadges(c);
        drawMessage(c);
        drawPiles(c);
        drawMoney(c);
        drawControls(c);
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
        boolean allIn = true;
        for (Cards.Sprite s : hand) if (!s.settled()) allIn = false;
        String txt = hidden && a.length == 1 ? String.valueOf(tot) : String.valueOf(tot);
        if (hidden) txt = tot + "+?";
        else if (soft(a, a.length) && tot < 21) txt = tot + " רך";
        float w = Math.max(40, c.textWidth(txt, 15) + 18);
        float x = Math.max(6, hand.get(0).tx - w - 6), yy = y + ch / 2 - 12;
        if (hand.size() > 1 || allIn) {
            int fill = tot > 21 ? Ui.BAD : tot == 21 ? Ui.ACCENT : 0xFF0A3524;
            float pulse = (phase == DEALER && dealerHand && dStep == 1 ? (float) Math.sin(time * 14) * 1.5f : 0);
            c.fillRoundRect(x - pulse, yy - pulse, w + 2 * pulse, 24 + 2 * pulse, 12, 0x66000000);
            c.fillRoundRect(x - pulse, yy - pulse - 1, w + 2 * pulse, 24 + 2 * pulse, 12, fill);
            c.text(txt, x + w / 2, yy + 17, 15, tot >= 21 && tot <= 21 || tot > 21 ? 0xFF1A1300 : Ui.INK, Gfx.CENTER);
        }
    }

    private void drawMessage(Gfx c) {
        float y;
        if (phase == BET) {
            y = top + 7 + 46 * sc + 24;
            if (h.money() < 10) {
                Ui.paragraph(c, "אין מספיק כסף לשחק", 180, y, 300, 18, Ui.BAD);
                return;
            }
            c.text(bet > 0 ? "הימור " + Ui.nis(bet) : "הניחו הימור", 180, y, bet > 0 ? 22 : 18, bet > 0 ? Ui.ACCENT : Ui.INK, Gfx.CENTER);
            return;
        }
        y = midY - 6;
        float pop = phase == RESULT ? Math.max(0, 1 - resT * 3) * 0.25f : 0;
        c.text(msg, 180, y, (phase == RESULT ? 22 : 16) * (1 + pop), msgCol, Gfx.CENTER);
        if (phase == RESULT && kind != PUSH) {
            // nothing more: the floating number already carries the amount
        }
    }

    private void drawPiles(Gfx c) {
        float r = Ui.clamp(chipR * 0.78f, 17, 24);
        if (phase == BET) {
            Cards.chipPile(c, pileX, pileY, r + 2, bet, 1);
        } else if (phase == RESULT) {
            float rt = resT;
            int amt = payoutStake * (doubled ? 1 : 1);
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
                Cards.chipPile(c, pileX - 48 + ox, py + oy, r, payoutShown, 1 - out);
            }
        } else {
            Cards.chipPile(c, pileX, pileY, r, staked, 1);
        }
        // chips in flight from the tray
        for (int i = 0; i < flyN; i++) {
            float k = Ui.clamp(flyT[i], 0, 1);
            float e = 1 - (1 - k) * (1 - k);
            float fx = flyX[i] + (pileX - flyX[i]) * e, fy = flyY[i] + (pileY - 8 - flyY[i]) * e - (float) Math.sin(Math.PI * k) * 18;
            Cards.chipFace(c, fx, fy, chipR * (1 - 0.3f * e), flyV[i], true, 0);
        }
    }

    private void drawMoney(Gfx c) {
        String s = Ui.nis(h.money());
        float w = Math.max(64, c.textWidth(s, 15) + 20);
        float x = 352 - w, y = top + 52 * sc * 0.0f + 52;
        if (phase != BET && phase != RESULT) y = top + 52;
        c.fillRoundRect(x, y, w, 22, 11, 0x66000000);
        c.fillCircle(x + 11, y + 11, 6, Ui.ACCENT);
        c.text(s, x + w - 8, y + 16.5f, 15, Ui.INK, Gfx.RIGHT);
    }

    private void drawControls(Gfx c) {
        if (phase == BET) {
            for (int i = 0; i < 4; i++) {
                float cx = bx[i] + bw[i] / 2, cy = by[i] + bh[i] / 2;
                Cards.chipFace(c, cx, cy, chipR, CHIPS[i], enabled(i), pressId == i && pressT > 0 ? 1 : 0);
            }
            boolean useLast = bet == 0 && flyN == 0 && last > 0;
            btn(c, B_CLEAR, useLast ? "כמו קודם" : "נקה", false);
            btn(c, B_DEAL, "חלק קלפים", true);
        } else if (phase == PLAY) {
            btn(c, B_DOUBLE, "הכפל", false);
            btn(c, B_STAND, "עצור", false);
            btn(c, B_HIT, "משוך", true);
        } else if (phase == RESULT && resT > 1.5f) {
            btn(c, B_CHANGE, "שנה הימור", false);
            btn(c, B_AGAIN, "שוב " + Ui.nis(last), true);
        }
        if (visible(B_EXIT)) btn(c, B_EXIT, "יציאה", false);
    }

    private void btn(Gfx c, int id, String label, boolean primary) {
        boolean on = enabled(id);
        Btn bt = new Btn(id, bx[id], by[id], bw[id], bh[id], label, on, primary);
        int fill = !on ? 0xFF1B2028 : primary ? Ui.ACCENT : 0xFF2C3644;
        int ink = !on ? 0xFF5B6472 : primary ? 0xFF1A1300 : Ui.INK;
        float press = pressId == id && pressT > 0 ? 1 : 0;
        if (id == B_EXIT) {
            c.fillRoundRect(bt.x, bt.y, bt.w, bt.h, 12, 0x88000000);
            c.strokeRoundRect(bt.x, bt.y, bt.w, bt.h, 12, 1.5f, 0x88FFFFFF);
            c.text(label, bt.x + bt.w / 2, bt.y + bt.h / 2 + 5.5f, 15, Ui.INK, Gfx.CENTER);
            return;
        }
        Cards.button(c, bt, press, fill, ink);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        c.fillRoundRect(cx - 120, cy - 60, 240, 120, 18, Cards.FELT);
        float bob = (float) Math.sin(t * 2) * 2;
        Cards.face(c, cx - 56, cy - 40 + bob, 56, 80, 12 + 13 * 0); // ace of spades
        Cards.face(c, cx - 14, cy - 44 - bob, 56, 80, 11 + 13 * 1); // king of hearts
        Cards.chipPile(c, cx + 80, cy + 44, 18, 135, 1);
    }
}
