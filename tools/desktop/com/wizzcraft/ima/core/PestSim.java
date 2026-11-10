package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Balance simulation for {@link PestMini}: plays whole shifts with the Mini's own bot at several
 * skill levels and with a few fixed build strategies, and prints the bites per wave and the results.
 * Every strategy plays through down() exactly like a finger.
 *
 * Usage: java -cp OUT com.wizzcraft.ima.core.PestSim [shifts]
 */
public final class PestSim {
    static final float DT = 1f / 30f;

    interface Strategy {
        void act(PestMini m, Random r);
    }

    /** Builds a fixed order of {spot, type, level} steps as soon as each is affordable. */
    static final class Order implements Strategy {
        final int[][] steps;
        final boolean slipper;
        final float aimErr;

        Order(int[][] steps, boolean slipper, float aimErr) {
            this.steps = steps;
            this.slipper = slipper;
            this.aimErr = aimErr;
        }

        public void act(PestMini m, Random r) {
            if (slipper) swat(m, r, aimErr);
            for (int[] st : steps) {
                PestMini.Tower t = m.towers[st[0]];
                if (t.type >= 0 && t.type != st[1]) continue;
                if (t.type == st[1] && t.level >= st[2]) continue;
                int cost = t.type < 0 ? PestMini.COST[st[1]][0] : PestMini.COST[t.type][t.level + 1];
                if (m.coins < cost) return;
                if (t.type < 0 && m.selected != st[1]) m.down(m.cardCX(st[1]), m.cardCY());
                m.down(m.spotSX(st[0]), m.spotSY(st[0]));
                return;
            }
        }
    }

    /** Taps a random card and a random spot now and then, whatever it costs. */
    static final class Chaos implements Strategy {
        float next;
        final boolean slipper;

        Chaos(boolean slipper) {
            this.slipper = slipper;
        }

        public void act(PestMini m, Random r) {
            if (slipper) swat(m, r, 16);
            if (m.time < next) return;
            next = m.time + 0.5f + r.nextFloat();
            int s = r.nextInt(PestMini.SPOTS);
            if (m.towers[s].type < 0) m.down(m.cardCX(r.nextInt(PestMini.TYPES)), m.cardCY());
            m.down(m.spotSX(s), m.spotSY(s));
        }
    }

    /** A first-timer: never touches the cards, just taps whichever glowing spot is nearest the thumb. */
    static final class Newbie implements Strategy {
        float next;

        public void act(PestMini m, Random r) {
            if (m.time < next) return;
            next = m.time + 0.7f + r.nextFloat() * 0.6f;
            for (int i = 0; i < PestMini.SPOTS; i++) {
                int s = (i + r.nextInt(2)) % PestMini.SPOTS;
                if (m.towers[s].type < 0 && m.coins >= PestMini.COST[m.selected][0]) {
                    m.down(m.spotSX(s), m.spotSY(s));
                    return;
                }
            }
        }
    }

    static final class Bot implements Strategy {
        final float skill;

        Bot(float skill) {
            this.skill = skill;
        }

        public void act(PestMini m, Random r) {
            m.bot(r, skill);
        }
    }

    /** A decent thumb: smacks the bug nearest the cake whenever the slipper is ready. */
    static void swat(PestMini m, Random r, float err) {
        if (!m.wave || m.slipCd > 0 || m.pests.isEmpty() || r.nextInt(4) != 0) return;
        PestMini.Pest best = null;
        for (PestMini.Pest p : m.pests) if (!p.dead && (best == null || p.s > best.s)) best = p;
        if (best == null) return;
        m.down(m.sx(best.x) + (r.nextFloat() * 2 - 1) * err, m.syv(best.y) + (r.nextFloat() * 2 - 1) * err);
    }

    static final int S = PestMini.SPRAY, K = PestMini.STICKY, Z = PestMini.ZAP, C = PestMini.CAT;

    static final int[][] CHEAP = {{0, S, 0}, {5, S, 0}, {4, S, 0}, {3, S, 0}, {1, K, 0}, {2, K, 0},
            {0, S, 1}, {5, S, 1}, {4, S, 1}, {3, S, 1}, {1, K, 1}, {2, K, 1}, {0, S, 2}, {5, S, 2}, {4, S, 2},
            {3, S, 2}, {1, K, 2}, {2, K, 2}};
    static final int[][] BEST = {{4, Z, 0}, {3, C, 0}, {1, Z, 0}, {4, C, 1}, {1, Z, 1}, {3, C, 1}, {4, C, 2},
            {1, Z, 2}, {3, C, 2}, {0, Z, 0}, {5, C, 0}, {0, Z, 1}, {5, C, 1}};
    static final int[][] LAMPS = {{4, Z, 0}, {1, Z, 0}, {4, Z, 1}, {1, Z, 1}, {3, Z, 0}, {4, Z, 2}, {1, Z, 2},
            {0, Z, 0}, {3, Z, 1}, {5, Z, 0}};
    /** Everything on the last stretch by the cake. */
    static final int[][] LATE = {{3, S, 0}, {4, S, 0}, {5, S, 0}, {3, S, 1}, {4, Z, 0}, {3, S, 2}, {5, K, 0},
            {4, Z, 1}, {5, K, 1}};

    public static void main(String[] args) throws Exception {
        int shifts = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        float[] lab = {0f, 0.2f, 0.4f, 0.6f};
        float[] senior = {0.5f, 0.63f, 0.77f, 0.9f};
        Object[][] runs = {
                {"bot skill 0.0", new Bot(0f)},
                {"bot skill 0.3", new Bot(0.3f)},
                {"bot skill 0.6", new Bot(0.6f)},
                {"bot skill 0.9", new Bot(0.9f)},
                {"bot skill 1.0", new Bot(1f)},
                {"expert plan, no slipper", new Order(PestMini.PLAN, false, 0)},
                {"all cheap, no slipper", new Order(CHEAP, false, 0)},
                {"all cheap + slipper", new Order(CHEAP, true, 12)},
                {"save for best, no slipper", new Order(BEST, false, 0)},
                {"save for best + slipper", new Order(BEST, true, 12)},
                {"lamps only + slipper", new Order(LAMPS, true, 12)},
                {"last stretch + slipper", new Order(LATE, true, 12)},
                {"random taps + slipper", null},
                {"first-timer: glowing spots", "newbie"},
                {"slipper only", new Order(new int[0][], true, 12)},
        };
        for (float[] prof : new float[][]{lab, senior}) {
            System.out.println();
            System.out.println(prof == lab ? "== difficulty 0 / .2 / .4 / .6 (the lab's ramp; a new hire)"
                    : "== difficulty .5 / .63 / .77 / .9 (shift lead, or a tired player)");
            System.out.println("strategy                      bites per wave (avg)    results w1..w4 (P/O/M %)                 score  flawless  sec");
            for (Object[] run : runs) {
                int[][] res = new int[4][3];
                float[] bites = new float[4];
                int flawless = 0;
                float secs = 0;
                Random r = new Random(5);
                for (int sh = 0; sh < shifts; sh++) {
                    Strategy st = run[1] == null ? new Chaos(true) : run[1] instanceof String ? new Newbie() : (Strategy) run[1];
                    MiniLab.FakeHost host = new MiniLab.FakeHost();
                    host.screenH = sh % 3 == 0 ? 560 : 760;
                    PestMini m = new PestMini();
                    m.attach(host);
                    m.layout(MiniLab.TOP, host.screenH - MiniLab.FOOT);
                    boolean all = true;
                    for (int w = 0; w < 4; w++) {
                        m.begin(w, prof[w]);
                        int out = Mini.RUNNING, f = 0;
                        while (out == Mini.RUNNING && f < 30 * 120) {
                            st.act(m, r);
                            out = m.update(DT);
                            f++;
                        }
                        secs += f * DT;
                        res[w][out]++;
                        bites[w] += m.bites;
                        if (out != Mini.PERFECT) all = false;
                    }
                    if (all) flawless++;
                }
                StringBuilder b = new StringBuilder();
                float score = 0;
                for (int w = 0; w < 4; w++) {
                    b.append(String.format("%3.0f/%3.0f/%3.0f  ", 100f * res[w][2] / shifts, 100f * res[w][1] / shifts,
                            100f * res[w][0] / shifts));
                    score += res[w][1] + 2 * res[w][2];
                }
                System.out.printf("%-28s  %4.1f %4.1f %4.1f %4.1f     %s %.2f   %4.0f%%   %4.1f%n", run[0],
                        bites[0] / shifts, bites[1] / shifts, bites[2] / shifts, bites[3] / shifts, b, score / (4f * shifts),
                        100f * flawless / shifts, secs / shifts);
            }
        }
    }
}
