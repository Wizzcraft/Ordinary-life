package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Test bench for one Mini, with a fake host. It renders screenshots at both screen heights, plays
 * whole sessions with the Mini's own bot at several skill levels, checks that rounds end on their
 * own, and hammers the Mini with random touches.
 *
 * Usage: java -Djava.awt.headless=true -cp OUT com.wizzcraft.ima.core.MiniLab ClassName outDir
 * Prints "LAB OK" at the end; any failed check throws.
 */
public final class MiniLab {
    static final float DT = 1f / 30f;
    /** Play area insets used by the real host: below the HUD and title, above the progress footer. */
    static final float TOP = Game.HUD_H + 74, FOOT = 96;

    static final class FakeHost implements Host {
        float screenH = 760, energy = 80;
        boolean female;
        int money = 500, texts, bursts, shakes, minMoney = 500;
        final int[] sfx = new int[Sfx.COUNT];
        final HashMap<String, Float> vars = new HashMap<>();
        final Random rnd = new Random(42);
        final ArrayList<Object[]> floaters = new ArrayList<>();

        public boolean female() { return female; }
        public String name() { return female ? "דנה" : "יואב"; }
        public String t(String m, String f) { return female ? f : m; }
        public float energy() { return energy; }
        public float stat(int which) { return 20; }
        public int money() { return money; }
        public boolean pay(int delta) {
            if (money + delta < 0) return false;
            money += delta;
            minMoney = Math.min(minMoney, money);
            return true;
        }
        public float var(String key) { Float v = vars.get(key); return v == null ? 0 : v; }
        public void setVar(String key, float value) {
            if (!key.matches("[a-z0-9_.]+")) throw new AssertionError("bad var key: " + key);
            vars.put(key, value);
        }
        public Random rnd() { return rnd; }
        public void text(float x, float y, String s, int argb) {
            texts++;
            floaters.add(new Object[]{x, y, s, argb, 1.1f});
        }
        public void burst(float x, float y, int argb, int count) { bursts++; }
        public void shake(float amount) { shakes++; }
        public void sfx(int id) {
            if (id < 0 || id >= Sfx.COUNT) throw new AssertionError("bad sfx id " + id);
            sfx[id]++;
        }
        public float screenH() { return screenH; }

        void tick(float dt) {
            for (int i = floaters.size() - 1; i >= 0; i--) {
                Object[] f = floaters.get(i);
                f[4] = (Float) f[4] - dt;
                f[1] = (Float) f[1] - 46 * dt;
                if ((Float) f[4] <= 0) floaters.remove(i);
            }
        }
    }

    static FakeHost host;
    static Mini mini;
    static String name, out;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("usage: MiniLab ClassName outDir");
        name = args[0];
        out = args[1];
        fresh(false, 760);
        System.out.println(name + ": \"" + mini.title() + "\", rounds=" + mini.rounds());
        System.out.println("howTo: " + mini.howTo());
        check(mini.title().length() > 0 && mini.title().length() <= 16, "title is 1-16 characters");
        check(mini.howTo().length() >= 20 && mini.howTo().length() <= 170, "howTo is 20-170 characters");
        check(mini.rounds() == 0 || mini.rounds() == 1 || mini.rounds() == 2 || mini.rounds() == 4 || mini.rounds() == 8,
                "rounds() is 0, 1, 2, 4 or 8");
        shots();
        if (mini.rounds() > 0) {
            endsOnItsOwn();
            skillCurve();
        } else {
            venue();
        }
        monkey();
        System.out.println("LAB OK");
    }

    static void fresh(boolean female, float h) throws Exception {
        host = new FakeHost();
        host.female = female;
        host.screenH = h;
        mini = (Mini) Class.forName("com.wizzcraft.ima.core." + name).getDeclaredConstructor().newInstance();
        mini.attach(host);
    }

    static float diff(int round) {
        int n = mini.rounds();
        return n > 1 ? 0.6f * round / (n - 1) : 0.3f;
    }

    /** One frame: bot (if skill >= 0), update, floaters. Returns the Mini's result. */
    static int frame(Random r, float skill) {
        if (skill >= 0) mini.bot(r, skill);
        int res = mini.update(DT);
        host.tick(DT);
        return res;
    }

    static void render(Gfx c, String label) {
        float h = host.screenH, top = TOP, bottom = h - FOOT;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        mini.draw(c, top, bottom);
        // Everything outside the play area belongs to the host; drawn after so overflow is obvious.
        c.fillRect(0, 0, Game.W, Game.HUD_H, Ui.PANEL);
        c.text("HUD", Game.W / 2, 52, 14, Ui.DIM, Gfx.CENTER);
        c.fillRect(0, Game.HUD_H, Game.W, top - Game.HUD_H, 0xE612161C);
        c.text("משמרת: " + mini.title(), Game.W / 2, Game.HUD_H + 34, 22, Ui.INK, Gfx.CENTER);
        c.text(label, Game.W / 2, Game.HUD_H + 56, 13, Ui.DIM, Gfx.CENTER);
        c.fillRect(0, bottom, Game.W, FOOT, 0xE612161C);
        c.line(0, top, Game.W, top, 1, 0x55FF6B6B);
        c.line(0, bottom, Game.W, bottom, 1, 0x55FF6B6B);
        c.text("(כאן המארח מצייר התקדמות ושכר)", Game.W / 2, bottom + 40, 12, Ui.DIM, Gfx.CENTER);
        for (Object[] f : host.floaters) {
            c.text((String) f[2], (Float) f[0], (Float) f[1], 22, (Integer) f[3], Gfx.CENTER);
        }
    }

    static void shot(String file, String label) throws Exception {
        AwtGfx g = new AwtGfx(720, (int) (host.screenH * 2), 2f);
        render(g, label);
        g.save(out + "/" + file + ".png");
    }

    static void shots() throws Exception {
        Random r = new Random(1);
        for (float hgt : new float[]{760, 560}) {
            fresh(hgt == 560, hgt);
            String tag = name + "-" + (int) hgt;
            if (hgt == 760) {
                // The intro card as the host will show it.
                AwtGfx g = new AwtGfx(720, 1520, 2f);
                g.fillRect(0, 0, Game.W, 760, Ui.BG);
                g.text("משמרת: " + mini.title(), Game.W / 2, Game.HUD_H + 34, 22, Ui.INK, Gfx.CENTER);
                mini.art(g, Game.W / 2, 760 * 0.38f, 1.3f);
                Ui.paragraph(g, mini.howTo(), Game.W / 2, 760 * 0.56f, 304, 15, Ui.DIM);
                g.save(out + "/" + tag + "-intro.png");
            }
            int n = Math.max(1, mini.rounds());
            int[] pick = n >= 3 ? new int[]{0, n / 2, n - 1} : n == 2 ? new int[]{0, 1} : new int[]{0};
            for (int round : pick) {
                mini.begin(round, diff(round));
                render(new AwtGfx(36, (int) (hgt / 10), 0.1f), "");
                int res = Mini.RUNNING;
                for (int i = 0; i < 40 && res == Mini.RUNNING; i++) res = frame(r, 0.7f);
                shot(tag + "-r" + round + "-a", "שלב " + (round + 1) + " מתוך " + n);
                for (int i = 0; i < 30 * 5 && res == Mini.RUNNING; i++) {
                    res = frame(r, 0.7f);
                    if (i % 3 == 0) render(new AwtGfx(36, (int) (hgt / 10), 0.1f), "");
                }
                shot(tag + "-r" + round + "-b", "שלב " + (round + 1) + " מתוך " + n);
                for (int i = 0; i < 30 * 120 && res == Mini.RUNNING && mini.rounds() > 0; i++) res = frame(r, 0.7f);
            }
        }
        System.out.println("screenshots written to " + out + " (look at them at both heights)");
    }

    /** With no input at all, every round must still finish. */
    static void endsOnItsOwn() throws Exception {
        fresh(false, 760);
        float worst = 0;
        for (int round = 0; round < mini.rounds(); round++) {
            mini.begin(round, diff(round));
            int res = Mini.RUNNING, f = 0;
            while (res == Mini.RUNNING && f < 30 * 61) {
                res = frame(null, -1);
                f++;
            }
            check(res != Mini.RUNNING, "round " + round + " ends on its own within 60 s with no input");
            check(res >= Mini.MISS && res <= Mini.PERFECT, "valid result " + res);
            worst = Math.max(worst, f * DT);
        }
        System.out.printf("no input: every round ends by itself (longest %.1f s)%n", worst);
    }

    static void skillCurve() throws Exception {
        float[] skills = {0f, 0.3f, 0.6f, 0.9f, 1f};
        float prev = -1;
        System.out.println("skill   MISS    OK  PERFECT  score  sec/round  sec/session");
        for (float skill : skills) {
            int[] cnt = new int[3];
            float secs = 0;
            int sessions = 30, roundsPlayed = 0;
            Random r = new Random(7);
            for (int s = 0; s < sessions; s++) {
                fresh(s % 2 == 1, s % 3 == 0 ? 560 : 760);
                for (int round = 0; round < mini.rounds(); round++) {
                    mini.begin(round, diff(round));
                    int res = Mini.RUNNING, f = 0;
                    while (res == Mini.RUNNING && f < 30 * 120) {
                        if (f % 6 == 0) render(new AwtGfx(36, 76, 0.1f), "");
                        res = frame(r, skill);
                        f++;
                    }
                    check(res != Mini.RUNNING, "round ends within 120 s at skill " + skill);
                    cnt[res]++;
                    secs += f * DT;
                    roundsPlayed++;
                }
            }
            float score = (cnt[1] + 2f * cnt[2]) / roundsPlayed;
            System.out.printf("%.1f    %4.0f%% %4.0f%%   %4.0f%%   %.2f   %6.1f     %6.1f%n", skill,
                    100f * cnt[0] / roundsPlayed, 100f * cnt[1] / roundsPlayed, 100f * cnt[2] / roundsPlayed, score,
                    secs / roundsPlayed, secs / sessions);
            if (prev >= 0 && score < prev - 0.15f) {
                System.out.println("  WARNING: more skill gives a worse score here. Check bot() and the scoring.");
            }
            prev = score;
            if (skill == 0f && score > 0.9f) System.out.println("  WARNING: a clueless player does too well.");
            if (skill == 1f && score < 1.5f) System.out.println("  WARNING: an expert rarely gets PERFECT.");
        }
        System.out.printf("feedback over the last sessions: %d texts, %d bursts, %d shakes; sounds used:", host.texts,
                host.bursts, host.shakes);
        for (int i = 0; i < Sfx.COUNT; i++) if (host.sfx[i] > 0) System.out.print(" " + i + "x" + host.sfx[i]);
        System.out.println();
    }

    /** Open-ended venues: the bot plays for a while; money may never go negative. */
    static void venue() throws Exception {
        for (float skill : new float[]{0.2f, 0.8f}) {
            fresh(false, 760);
            Random r = new Random(11);
            mini.begin(0, 0.3f);
            int exits = 0;
            for (int f = 0; f < 30 * 600; f++) {
                if (f % 6 == 0) render(new AwtGfx(36, 76, 0.1f), "");
                check(frame(r, skill) == Mini.RUNNING, "a venue's update() keeps returning RUNNING");
                check(host.money >= 0, "money never goes negative");
                if (mini.wantsExit()) {
                    exits++;
                    fresh(false, 760);
                    host.money = 500;
                    mini.begin(0, 0.3f);
                }
            }
            System.out.printf("venue, skill %.1f, 10 minutes: money 500 -> %d (lowest %d), left %d times%n", skill,
                    host.money, host.minMoney, exits);
        }
    }

    static void monkey() throws Exception {
        fresh(false, 760);
        Random r = new Random(99);
        int round = 0, decided = 0;
        mini.begin(0, 0);
        boolean isDown = false;
        for (int f = 0; f < 150_000; f++) {
            float x = r.nextFloat() * 400 - 20, y = TOP - 60 + r.nextFloat() * (host.screenH - FOOT - TOP + 120);
            int a = r.nextInt(12);
            if (a == 0) {
                mini.down(x, y);
                isDown = true;
            } else if (a == 1 && isDown) {
                mini.up(x, y);
                isDown = false;
            } else if (a == 2 && isDown) {
                mini.move(x, y);
            }
            if (r.nextInt(3000) == 0) {
                // App goes to the background mid-round; the round is replayed afterwards.
                mini.pause();
                host.screenH = r.nextBoolean() ? 760 : 560;
                mini.begin(round, diff(round));
            }
            if (f % 7 == 0) render(new AwtGfx(36, (int) (host.screenH / 10), 0.1f), "");
            int res = frame(null, -1);
            check(host.money >= 0, "money never goes negative");
            if (res != Mini.RUNNING) {
                decided++;
                round = mini.rounds() > 0 ? (round + 1) % mini.rounds() : 0;
                mini.begin(round, diff(round));
            } else if (mini.wantsExit()) {
                fresh(false, host.screenH);
                host.money = 500;
                mini.begin(0, 0.3f);
            }
        }
        System.out.println("monkey: 150000 frames of random touches and pauses, no exceptions, " + decided
                + " rounds decided");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
    }
}
