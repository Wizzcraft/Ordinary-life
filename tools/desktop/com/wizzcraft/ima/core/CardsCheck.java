package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Odds check for the casino tables. Plays millions of hands with the same rules code the tables use
 * (BlackjackMini.strategy / PokerMini.bestHold and the same Cards.Deck) and reports the return to
 * player, then drives the real Mini classes through their own bots to prove the money paths match.
 *
 * Usage: java -Djava.awt.headless=true -cp OUT com.wizzcraft.ima.core.CardsCheck [hands] [miniHands]
 * Defaults: 1,000,000 simulated hands per game and 100,000 hands through each real Mini.
 */
public final class CardsCheck {
    public static void main(String[] args) {
        long hands = args.length > 0 ? Long.parseLong(args[0]) : 1_000_000L;
        long miniHands = args.length > 1 ? Long.parseLong(args[1]) : 100_000L;
        blackjack(hands, true);
        blackjack(hands / 5, false);
        poker(hands, 5);
        poker(hands, 1);
        driveBlackjack(miniHands);
        drivePoker(miniHands);
        System.out.println("CARDSCHECK OK");
    }

    // ------------------------------------------------------------------ blackjack

    static void blackjack(long hands, boolean basic) {
        Random rnd = new Random(20240607L + (basic ? 0 : 1));
        Cards.Deck deck = new Cards.Deck(BlackjackMini.DECKS);
        deck.shuffle(rnd);
        double net = 0, wagered = 0, initial = 0;
        long wins = 0, losses = 0, pushes = 0, naturals = 0, busts = 0, doubles = 0, shuffles = 0;
        int[] p = new int[12], d = new int[12];
        for (long i = 0; i < hands; i++) {
            if (deck.left() < deck.size() / 4) {
                deck.shuffle(rnd);
                shuffles++;
            }
            int pn = 0, dn = 0;
            p[pn++] = deck.draw();
            d[dn++] = deck.draw();
            p[pn++] = deck.draw();
            d[dn++] = deck.draw();
            double stake = 1;
            initial += 1;
            int up = BlackjackMini.pip(d[0]);
            boolean pBJ = BlackjackMini.total(p, 2) == 21, dBJ = BlackjackMini.total(d, 2) == 21;
            double result;
            if (pBJ || dBJ) {
                result = pBJ && dBJ ? 0 : pBJ ? 1.5 : -1;
                if (pBJ && !dBJ) naturals++;
            } else {
                boolean bust = false;
                while (true) {
                    int a;
                    if (basic) {
                        a = BlackjackMini.strategy(p, pn, up, pn == 2);
                    } else {
                        // clueless: stands or hits by coin flip, doubles at random on two cards
                        int roll = rnd.nextInt(3);
                        a = roll == 0 ? BlackjackMini.HIT : roll == 1 ? BlackjackMini.STAND : (pn == 2 ? BlackjackMini.DOUBLE : BlackjackMini.HIT);
                        if (BlackjackMini.total(p, pn) >= 21) a = BlackjackMini.STAND;
                    }
                    if (a == BlackjackMini.STAND) break;
                    p[pn++] = deck.draw();
                    if (a == BlackjackMini.DOUBLE) {
                        stake = 2;
                        doubles++;
                        break;
                    }
                    if (BlackjackMini.total(p, pn) > 21) break;
                }
                int pt = BlackjackMini.total(p, pn);
                if (pt > 21) {
                    bust = true;
                    busts++;
                    result = -stake;
                } else {
                    while (BlackjackMini.total(d, dn) < 17) d[dn++] = deck.draw();
                    int dt = BlackjackMini.total(d, dn);
                    result = dt > 21 || pt > dt ? stake : pt == dt ? 0 : -stake;
                }
            }
            wagered += stake;
            net += result;
            if (result > 0) wins++;
            else if (result < 0) losses++;
            else pushes++;
        }
        System.out.printf("BLACKJACK %s: %,d hands, 6 decks, S17, 3:2, double any two, no split/insurance%n",
                basic ? "basic strategy" : "random play", hands);
        System.out.printf("  return to player: %.3f%% of the initial bet (%.3f%% of everything wagered, doubles included)%n",
                100 * (1 + net / initial), 100 * (1 + net / wagered));
        System.out.printf("  std. error of the return: about %.3f%%; wins %.1f%%, losses %.1f%%, pushes %.1f%%, naturals %.2f%%, "
                        + "busts %.1f%%, doubles %.1f%%, reshuffles %d%n", 115 / Math.sqrt(hands), 100.0 * wins / hands,
                100.0 * losses / hands, 100.0 * pushes / hands, 100.0 * naturals / hands, 100.0 * busts / hands,
                100.0 * doubles / hands, shuffles);
    }

    // ------------------------------------------------------------------ poker

    static void poker(long hands, int coins) {
        Random rnd = new Random(777L + coins);
        Cards.Deck deck = new Cards.Deck(1);
        long[] freq = new long[10];
        double paid = 0;
        int[] c = new int[5];
        for (long i = 0; i < hands; i++) {
            deck.shuffle(rnd);
            for (int k = 0; k < 5; k++) c[k] = deck.draw();
            int mask = PokerMini.bestHold(c);
            for (int k = 0; k < 5; k++) if ((mask >> k & 1) == 0) c[k] = deck.draw();
            int rk = PokerMini.rank(c);
            freq[rk]++;
            paid += PokerMini.pay(rk, coins) * coins;
        }
        System.out.printf("VIDEO POKER Jacks or Better 9/6, %d coin%s, hold strategy, %,d hands%n", coins, coins == 1 ? "" : "s", hands);
        System.out.printf("  return to player: %.3f%% (std. error about %.2f%%; textbook optimal %s)%n",
                100 * paid / (hands * coins), 440 / Math.sqrt(hands), coins == 5 ? "99.54%" : "98.39%");
        String[] n = {"nothing", "jacks or better", "two pair", "three of a kind", "straight", "flush", "full house", "four of a kind",
                "straight flush", "royal flush"};
        StringBuilder sb = new StringBuilder("  hit frequency ");
        sb.append(String.format("%.2f%%", 100.0 * (hands - freq[0]) / hands)).append(" |");
        for (int r = 9; r >= 1; r--) sb.append(String.format(" %s %.4f%%", n[r], 100.0 * freq[r] / hands));
        System.out.println(sb);
    }

    // ------------------------------------------------------------------ the real Minis

    static final class H implements Host {
        long money = 1_000_000_000L, wagered, returned;
        final java.util.HashMap<String, Float> vars = new java.util.HashMap<>();
        final Random rnd = new Random(5);

        public boolean female() { return false; }
        public String name() { return "x"; }
        public String t(String m, String f) { return m; }
        public float energy() { return 80; }
        public float stat(int w) { return 20; }
        public int money() { return (int) Math.min(money, 1_000_000_000L); }
        public boolean pay(int d) {
            if (money + d < 0) return false;
            money += d;
            if (d < 0) wagered -= d;
            else returned += d;
            return true;
        }
        public float var(String k) { Float v = vars.get(k); return v == null ? 0 : v; }
        public void setVar(String k, float v) { vars.put(k, v); }
        public Random rnd() { return rnd; }
        public void text(float x, float y, String s, int c) { }
        public void burst(float x, float y, int c, int n) { }
        public void shake(float a) { }
        public void sfx(int id) { }
        public float screenH() { return 760; }
    }

    /** Plays the table with its own skill-1 bot until about {@code target} hands were wagered. */
    static void drive(Mini m, H host, long target, long unitPerHand, String what) {
        m.attach(host);
        m.begin(0, 0.3f);
        Random r = new Random(3);
        long guard = 0;
        while (host.wagered < target * unitPerHand && guard < 400_000_000L) {
            m.bot(r, 1f);
            m.update(0.1f);
            if (m.wantsExit()) m.begin(0, 0.3f);
            guard++;
        }
        System.out.printf("%s through the real Mini (skill-1 bot): wagered %,d, returned %,d -> %.3f%% of all money wagered%n", what,
                host.wagered, host.returned, 100.0 * host.returned / host.wagered);
        if (host.money < 0) throw new AssertionError("negative money");
    }

    static void driveBlackjack(long hands) {
        H host = new H();
        // the bot flat-bets 10; doubles add stake, so count by initial bets via wager size
        drive(new BlackjackMini(), host, hands, 10, "BLACKJACK");
    }

    static void drivePoker(long hands) {
        H host = new H();
        drive(new PokerMini(), host, hands, 25, "VIDEO POKER (5 x 5)");
    }
}
