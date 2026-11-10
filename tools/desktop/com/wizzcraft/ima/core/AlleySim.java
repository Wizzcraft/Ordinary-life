package com.wizzcraft.ima.core;

import java.util.Arrays;
import java.util.Random;

/**
 * Economy simulation for the alley. Plays many 10-day runs (one visit per night) for three
 * strategies, using AlleyMini's own price, heat and loan functions, and the engine assumptions
 * documented in AlleyMini's constants block (daily heat decay, exit event chance, its cost).
 * Finally it replays some careful runs through the real Mini and its bot to check that the bot
 * plays the strategy the numbers describe.
 *
 * Usage: java -cp OUT com.wizzcraft.ima.core.AlleySim [runs] [days]
 */
public final class AlleySim {
    static final int START_MONEY = 300;

    static final class Run {
        int seed, money = START_MONEY, cap = AlleyMini.START_CAP, events;
        final int[] q = new int[AlleyMini.GOODS];
        final float[] avg = new float[AlleyMini.GOODS];
        float heat, heatSum, debt;
        int debtDay, hotNights, deals;

        int held() {
            int n = 0;
            for (int v : q) n += v;
            return n;
        }

        int price(int g, int day) {
            return AlleyMini.price(seed, g, day);
        }

        /** units > 0 buys, < 0 sells; refuses what cannot be afforded or carried, like the Mini. */
        boolean deal(int g, int units, int day) {
            int p = price(g, day);
            if (units > 0) {
                if (units * p > money || held() + units > cap) return false;
                money -= units * p;
                avg[g] = (avg[g] * q[g] + units * p) / (q[g] + units);
                q[g] += units;
                heat += AlleyMini.dealHeat(units, units * p, 0);
            } else {
                int k = Math.min(-units, q[g]);
                if (k <= 0) return false;
                money += k * p;
                heat += AlleyMini.dealHeat(k, k * p, Math.round(k * (p - avg[g])));
                q[g] -= k;
            }
            heat = Math.min(AlleyMini.HEAT_MAX, heat);
            deals++;
            return true;
        }

        int owed(int day) {
            return AlleyMini.owed(debt, debtDay, day);
        }

        /** Leaving the alley and the night after it: the engine's part, as assumed. */
        void night(Random r) {
            heatSum += heat;
            if (heat >= 50) hotNights++;
            if (r.nextFloat() < AlleyMini.exitEventChance(heat)) {
                events++;
                if (r.nextBoolean()) {
                    for (int g = 0; g < q.length; g++) q[g] -= (int) Math.ceil(q[g] * AlleyMini.CHASE_DROP);
                } else {
                    money -= Math.round(money * AlleyMini.SCUFFLE_CASH);
                }
            }
            heat = Math.max(0, heat - AlleyMini.HEAT_DECAY_DAY);
        }
    }

    interface Strategy {
        void visit(Run s, int day, boolean last, Random r);
    }

    static final Strategy CAREFUL = (s, day, last, r) -> {
        for (int guard = 0; guard < 20; guard++) {
            int[] m = AlleyMini.carefulMove(s.seed, day, s.q, s.avg, s.money, s.cap, s.heat, last);
            if (m == null || !s.deal(m[0], m[1], day)) break;
        }
    };

    /**
     * Maxes volume: reads the market exactly like the careful trader but ignores heat, and takes
     * the full loan on night one for more buying power (repaid on the last night).
     */
    static final Strategy GREEDY = (s, day, last, r) -> {
        if (day == 1) {
            s.debt = AlleyMini.LOAN_MAX;
            s.debtDay = day;
            s.money += AlleyMini.LOAN_MAX;
        }
        for (int guard = 0; guard < 20; guard++) {
            int[] m = AlleyMini.carefulMove(s.seed, day, s.q, s.avg, s.money, s.cap, -1000, last);
            if (m == null || !s.deal(m[0], m[1], day)) break;
        }
        if (last) {
            int owe = s.owed(day), pay = Math.min(s.money, owe);
            s.money -= pay;
            s.debt = owe - pay;
            s.debtDay = day;
        }
    };

    /** Taps whatever: random sales and buys, no plan (sells out on the last night only for the count). */
    static final Strategy RANDOM = (s, day, last, r) -> {
        for (int g = 0; g < AlleyMini.GOODS; g++) {
            if (s.q[g] > 0 && (last || r.nextFloat() < 0.4f)) s.deal(g, last ? -s.q[g] : -(1 + r.nextInt(s.q[g])), day);
        }
        if (last) return;
        for (int i = 0; i < 2; i++) {
            if (r.nextFloat() < 0.6f) {
                int g = r.nextInt(AlleyMini.GOODS), p = s.price(g, day);
                int max = Math.min(s.cap - s.held(), s.money / p);
                if (max > 0) s.deal(g, 1 + r.nextInt(max), day);
            }
        }
    };

    public static void main(String[] args) throws Exception {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int days = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        System.out.printf("AlleySim: %d runs x %d days, start %s, one visit per night%n", runs, days, Ui.nis(START_MONEY));
        System.out.printf("assumed exit event chance: heat 20 -> %.0f%%, 50 -> %.0f%%, 80 -> %.0f%%; decay %.0f/day%n",
                100 * AlleyMini.exitEventChance(20), 100 * AlleyMini.exitEventChance(50),
                100 * AlleyMini.exitEventChance(80), AlleyMini.HEAT_DECAY_DAY);
        System.out.println("strategy   p10    median   p90   | per day | avg heat  nights>=50  events/run  deals/run");
        report("careful", CAREFUL, runs, days);
        report("greedy", GREEDY, runs, days);
        report("random", RANDOM, runs, days);
        if (Boolean.getBoolean("quick")) return;
        priceSheet();
        viaBot(60, days);
    }

    static Run play(Strategy st, int seed, int days, Random r) {
        Run s = new Run();
        s.seed = seed;
        for (int d = 1; d <= days; d++) {
            st.visit(s, d, d == days, r);
            s.night(r);
        }
        return s;
    }

    static void report(String name, Strategy st, int runs, int days) {
        int[] fin = new int[runs];
        float heat = 0, hot = 0, ev = 0, deals = 0;
        for (int i = 0; i < runs; i++) {
            Random r = new Random(1000 + i);
            Run s = play(st, 1 + r.nextInt(1 << 20), days, r);
            fin[i] = s.money - s.owed(days);
            heat += s.heatSum / days;
            hot += s.hotNights / (float) days;
            ev += s.events;
            deals += s.deals;
        }
        Arrays.sort(fin);
        int med = fin[runs / 2];
        System.out.printf("%-8s %6s %8s %7s   | %+6d  |  %5.1f     %4.0f%%       %4.2f       %5.1f%n", name,
                Ui.nis(fin[runs / 10]), Ui.nis(med), Ui.nis(fin[runs * 9 / 10]), (med - START_MONEY) / days,
                heat / runs, 100 * hot / runs, ev / runs, deals / runs);
    }

    /** Price ranges actually produced, to check every good keeps its character. */
    static void priceSheet() {
        System.out.println("good          min   p10   median  p90   max   (over 200 seeds x 30 days)");
        for (int g = 0; g < AlleyMini.GOODS; g++) {
            int[] v = new int[200 * 30];
            int n = 0;
            for (int sd = 1; sd <= 200; sd++) for (int d = 1; d <= 30; d++) v[n++] = AlleyMini.price(sd * 7919, g, d);
            Arrays.sort(v);
            System.out.printf("%-10s %5d %5d %7d %5d %5d%n", AlleyMini.NAME[g], v[0], v[n / 10], v[n / 2], v[n * 9 / 10],
                    v[n - 1]);
        }
        int[] seen = new int[AlleyMini.GOODS * 2];
        int hints = 0;
        for (int sd = 1; sd <= 200; sd++) {
            for (int d = 1; d <= 30; d++) {
                int e = AlleyMini.event(sd * 7919, d);
                if (e >= 0) seen[e]++;
                if (AlleyMini.hint(sd * 7919, d) >= 0) hints++;
            }
        }
        System.out.print("events per 30 days (spike/crash):");
        for (int g = 0; g < AlleyMini.GOODS; g++) System.out.printf(" %s %.1f/%.1f", AlleyMini.NAME[g], seen[g * 2] / 200f, seen[g * 2 + 1] / 200f);
        System.out.printf("; whispers %.1f%n", hints / 200f);
    }

    /** Careful runs played through the real Mini by its bot at skill 1, compared with the direct runs. */
    static void viaBot(int runs, int days) throws Exception {
        int[] fin = new int[runs], direct = new int[runs];
        float heat = 0;
        for (int i = 0; i < runs; i++) {
            Random r = new Random(1000 + i);
            int seed = 1 + r.nextInt(1 << 20);
            direct[i] = play(CAREFUL, seed, days, new Random(5)).money;
            MiniLab.FakeHost host = new MiniLab.FakeHost();
            host.money = START_MONEY;
            host.vars.put("alley.seed", (float) seed);
            Random br = new Random(5), ev = new Random(5);
            for (int d = 1; d <= days; d++) {
                host.vars.put("day", (float) d);
                AlleyMini m = new AlleyMini();
                m.attach(host);
                m.begin(0, 0.3f);
                AwtGfx g = new AwtGfx(36, 76, 0.1f);
                int f = 0;
                for (; f < 30 * 60 && !m.wantsExit(); f++) {
                    if (f % 6 == 0) m.draw(g, MiniLab.TOP, 760 - MiniLab.FOOT);
                    if (d == days) break;
                    m.bot(br, 1f);
                    m.update(MiniLab.DT);
                    host.tick(MiniLab.DT);
                }
                if (d == days) {
                    // The measurement sells everything at tonight's prices.
                    for (int k = 0; k < AlleyMini.GOODS; k++) {
                        host.money += (int) host.var("alley.q" + k) * AlleyMini.price(seed, k, d);
                        host.vars.put("alley.q" + k, 0f);
                    }
                }
                float h = host.var("heat");
                heat += h;
                // Same engine assumptions as Run.night().
                if (ev.nextFloat() < AlleyMini.exitEventChance(h)) {
                    if (ev.nextBoolean()) {
                        for (int k = 0; k < AlleyMini.GOODS; k++) {
                            float q = host.var("alley.q" + k);
                            host.vars.put("alley.q" + k, q - (float) Math.ceil(q * AlleyMini.CHASE_DROP));
                        }
                    } else {
                        host.money -= Math.round(host.money * AlleyMini.SCUFFLE_CASH);
                    }
                }
                host.vars.put("heat", Math.max(0, h - AlleyMini.HEAT_DECAY_DAY));
            }
            fin[i] = host.money;
        }
        Arrays.sort(fin);
        Arrays.sort(direct);
        System.out.printf("careful via the Mini's bot (%d runs): median %s (direct %s), p10 %s, p90 %s, avg heat %.1f%n",
                runs, Ui.nis(fin[runs / 2]), Ui.nis(direct[runs / 2]), Ui.nis(fin[runs / 10]),
                Ui.nis(fin[runs * 9 / 10]), heat / runs / days);
    }
}
