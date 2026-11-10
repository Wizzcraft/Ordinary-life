package com.wizzcraft.ima.core;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The fast, headless gate for every minigame. It discovers every concrete {@link Mini} on the classpath
 * (any {@code *Mini.class} in the core package: no list to maintain) and runs the contract checks that
 * {@link MiniLab} does, plus more, through a {@link CountGfx} that only counts primitives, so it runs as
 * fast as the CPU allows. Minis run in parallel with each other; the timing and allocation measurements
 * run afterwards, one Mini at a time, so they are not distorted by the other threads.
 *
 * <pre>
 * java -Djava.awt.headless=true -cp OUT com.wizzcraft.ima.core.MiniSuite [--quick] [--only Bar,Cook]
 *      [--threads n] [--classes dir] [-v]
 * </pre>
 *
 * Checks, per Mini (the column names in the summary table):
 * <ul>
 * <li>meta: title 1-16 chars and howTo 20-170 chars (both genders), rounds() in {0,1,2,4,8}, a no-argument constructor</li>
 * <li>ends: with no input, every round ends by itself within 60 simulated seconds (work shifts and events)</li>
 * <li>curve: five skill levels; FAIL when skill 1 scores under 1.6, skill 0 over 0.6, or the score falls by
 * more than 0.15 below its running best as skill rises</li>
 * <li>venue: update() never returns a result, money never negative, a "יציאה" button is drawn and works</li>
 * <li>pause: pause() then begin() at many moments (finger up or down) leaves a playable Mini</li>
 * <li>monkey: 300k frames of random down / up / move / pause at both screen heights; "wild" adds
 * unbalanced events (up without down, double down) at both heights</li>
 * <li>draw: at most 1500 primitives per frame, finite coordinates, well-formed polygons, no non-text
 * primitive more than 10 units above or below the play area (sideways overflow only warns: the screen clips it)</li>
 * <li>host: no setVar key outside [a-z0-9_.], no sfx id out of range</li>
 * </ul>
 * Warnings (never fail): update+draw above 0.5 ms or above 2 KB allocated per frame; sideways overflow.
 */
public final class MiniSuite {
    static final float DT = MiniLab.DT;
    static final float TOP = MiniLab.TOP, FOOT = MiniLab.FOOT;
    static final int MAX_PRIMS = 1500;
    static final float TOL = 10;
    static final String[] CHECKS = {"meta", "ends", "curve", "venue", "pause", "monkey", "wild", "draw", "host"};
    static final float[] SKILLS = {0f, 0.3f, 0.6f, 0.9f, 1f};

    static boolean quick, verbose;
    /** Even shorter than quick: used by MiniSuiteTest, which only needs the fixtures to trip their checks. */
    static boolean tiny;

    static int pick(int full, int quickV, int tinyV) {
        return tiny ? tinyV : quick ? quickV : full;
    }

    // ------------------------------------------------------------------ gfx that only counts

    /** A Gfx that draws nothing: counts primitives and tracks the bounding box of everything but text. */
    static final class CountGfx implements Gfx {
        int prims, nonFinite, badPoly, nullText;
        float minX, minY, maxX, maxY;
        boolean capture;
        final ArrayList<Object[]> texts = new ArrayList<>();

        void reset() {
            prims = nonFinite = badPoly = nullText = 0;
            minX = minY = Float.POSITIVE_INFINITY;
            maxX = maxY = Float.NEGATIVE_INFINITY;
            texts.clear();
        }

        boolean any() {
            return minX <= maxX;
        }

        private void box(float x0, float y0, float x1, float y1, int argb) {
            if ((argb >>> 24) == 0) return;
            if (!(Float.isFinite(x0) && Float.isFinite(y0) && Float.isFinite(x1) && Float.isFinite(y1))) {
                nonFinite++;
                return;
            }
            if (x0 < minX) minX = x0;
            if (y0 < minY) minY = y0;
            if (x1 > maxX) maxX = x1;
            if (y1 > maxY) maxY = y1;
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            prims++;
            box(Math.min(x, x + w), Math.min(y, y + h), Math.max(x, x + w), Math.max(y, y + h), argb);
        }

        @Override
        public void fillRoundRect(float x, float y, float w, float h, float r, int argb) {
            fillRect(x, y, w, h, argb);
        }

        @Override
        public void strokeRoundRect(float x, float y, float w, float h, float r, float sw, int argb) {
            prims++;
            float k = sw / 2;
            box(Math.min(x, x + w) - k, Math.min(y, y + h) - k, Math.max(x, x + w) + k, Math.max(y, y + h) + k, argb);
        }

        @Override
        public void fillPoly(float[] xy, int argb) {
            prims++;
            if (xy == null || xy.length < 6 || (xy.length & 1) != 0) {
                badPoly++;
                return;
            }
            float x0 = Float.POSITIVE_INFINITY, y0 = x0, x1 = Float.NEGATIVE_INFINITY, y1 = x1;
            for (int i = 0; i + 1 < xy.length; i += 2) {
                x0 = Math.min(x0, xy[i]);
                x1 = Math.max(x1, xy[i]);
                y0 = Math.min(y0, xy[i + 1]);
                y1 = Math.max(y1, xy[i + 1]);
            }
            box(x0, y0, x1, y1, argb);
        }

        @Override
        public void fillCircle(float cx, float cy, float r, int argb) {
            prims++;
            box(cx - r, cy - r, cx + r, cy + r, argb);
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, float sw, int argb) {
            prims++;
            float k = sw / 2;
            box(Math.min(x1, x2) - k, Math.min(y1, y2) - k, Math.max(x1, x2) + k, Math.max(y1, y2) + k, argb);
        }

        @Override
        public void text(String s, float x, float baselineY, float size, int argb, int align) {
            prims++;
            if (s == null) {
                nullText++;
                return;
            }
            if (capture) texts.add(new Object[]{s, x, baselineY, size, align});
        }

        @Override
        public float textWidth(String s, float size) {
            return s == null ? 0 : s.length() * size * 0.55f;
        }
    }

    // ------------------------------------------------------------------ report

    static final class Report {
        final String name;
        String title = "?";
        int rounds = -1;
        final Map<String, List<String>> fails = new LinkedHashMap<>();
        final java.util.Set<String> ran = new java.util.LinkedHashSet<>();
        final List<String> warns = new ArrayList<>(), notes = new ArrayList<>();
        final java.util.Set<String> seenOverflow = new java.util.HashSet<>();
        final StringBuilder curve = new StringBuilder();
        float score0 = Float.NaN, score1 = Float.NaN;
        double us = Double.NaN, bytes = Double.NaN;
        int maxPrims;
        float ovTop, ovBottom, ovSide;
        long ms;
        volatile String phase = "starting";

        Report(String name) {
            this.name = name;
        }

        void ran(String check) {
            ran.add(check);
        }

        void fail(String check, String msg) {
            ran.add(check);
            List<String> l = fails.computeIfAbsent(check, k -> new ArrayList<>());
            if (l.size() < 4 && !l.contains(msg)) l.add(msg);
        }

        boolean failed() {
            return !fails.isEmpty();
        }
    }

    // ------------------------------------------------------------------ fake host

    static final class SuiteHost implements Host {
        final Report rep;
        boolean female;
        float screenH = 760, energy = 80;
        int money = 500, minMoney = 500, texts, bursts, shakes;
        final HashMap<String, Float> vars = new HashMap<>();
        final Random rnd;

        SuiteHost(Report rep, long seed) {
            this.rep = rep;
            this.rnd = new Random(seed);
        }

        public boolean female() { return female; }
        public String name() { return female ? "דנה" : "יואב"; }
        public String t(String m, String f) { return female ? f : m; }
        public float energy() { return energy; }
        public float stat(int which) { return 20; }
        public int money() { return money; }
        public boolean pay(int delta) {
            if ((long) money + delta < 0) return false;
            money += delta;
            minMoney = Math.min(minMoney, money);
            return true;
        }
        public float var(String key) { Float v = vars.get(key); return v == null ? 0 : v; }
        public void setVar(String key, float value) {
            if (key == null || !key.matches("[a-z0-9_.]+")) rep.fail("host", "setVar key \"" + key + "\" is outside [a-z0-9_.]");
            vars.put(key, value);
        }
        public Random rnd() { return rnd; }
        public void text(float x, float y, String s, int argb) {
            texts++;
            if (s == null) rep.fail("host", "h.text called with a null string");
        }
        public void burst(float x, float y, int argb, int count) { bursts++; }
        public void shake(float amount) { shakes++; }
        public void sfx(int id) {
            if (id < 0 || id >= Sfx.COUNT) rep.fail("host", "h.sfx(" + id + ") is out of range 0.." + (Sfx.COUNT - 1));
        }
        public float screenH() { return screenH; }
    }

    // ------------------------------------------------------------------ one Mini under test

    /** The Mini, its host and the counting gfx, plus the bookkeeping for the draw checks. */
    static final class Run {
        final Report rep;
        final Class<? extends Mini> cls;
        SuiteHost host;
        Mini mini;
        final CountGfx g = new CountGfx();
        boolean trackOverflow;
        String ctx = "";
        long seed = 42;

        Run(Report rep, Class<? extends Mini> cls) {
            this.rep = rep;
            this.cls = cls;
        }

        void fresh(boolean female, float screenH) throws Exception {
            host = new SuiteHost(rep, seed++);
            host.female = female;
            host.screenH = screenH;
            java.lang.reflect.Constructor<? extends Mini> k = cls.getDeclaredConstructor();
            k.setAccessible(true);
            mini = k.newInstance();
            mini.attach(host);
        }

        float diff(int round) {
            int n = mini.rounds();
            return n > 1 ? 0.6f * round / (n - 1) : 0.3f;
        }

        int frame(Random r, float skill) {
            if (skill >= 0) mini.bot(r, skill);
            int res = mini.update(DT);
            if (res < Mini.RUNNING || res > Mini.PERFECT) throw new AssertionError("update() returned invalid result " + res);
            return res;
        }

        /** Draws one frame through the counting gfx and applies the per-frame draw checks. */
        void draw() {
            float top = TOP, bottom = host.screenH - FOOT;
            g.reset();
            mini.draw(g, top, bottom);
            if (g.prims > rep.maxPrims) rep.maxPrims = g.prims;
            if (g.prims > MAX_PRIMS) rep.fail("draw", g.prims + " primitives in one frame (limit " + MAX_PRIMS + ") at " + ctx);
            if (g.nonFinite > 0) rep.fail("draw", g.nonFinite + " primitive(s) with NaN/infinite coordinates at " + ctx);
            if (g.badPoly > 0) rep.fail("draw", g.badPoly + " fillPoly call(s) with fewer than 3 points or an odd length at " + ctx);
            if (g.nullText > 0) rep.fail("draw", "text(null) at " + ctx);
            if (trackOverflow && g.any()) {
                float up = top - g.minY, down = g.maxY - bottom, side = Math.max(-g.minX, g.maxX - Game.W);
                if (up > rep.ovTop) rep.ovTop = up;
                if (down > rep.ovBottom) rep.ovBottom = down;
                if (side > rep.ovSide) rep.ovSide = side;
                if (Math.max(up, down) > TOL) {
                    float worst = Math.max(up, down);
                    String key = (up > down ? "above" : "below") + (int) host.screenH + "/" + (int) (worst / 10);
                    if (rep.seenOverflow.add(key)) {
                        rep.fail("draw", String.format(Locale.ROOT,
                                "draws %.0f units %s the play area (limit %.0f; area y=%.0f..%.0f, drawn y=%.0f..%.0f) at %s",
                                worst, up > down ? "above" : "below", TOL, top, bottom, g.minY, g.maxY, ctx));
                    }
                }
            }
        }
    }

    interface Phase {
        void run() throws Throwable;
    }

    /** Runs a phase; an exception becomes a failure of {@code check} instead of ending the Mini's run. */
    static void guard(Run r, String check, Phase p) {
        r.rep.ran(check);
        r.rep.phase = check;
        try {
            p.run();
        } catch (Throwable t) {
            if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) t = t.getCause();
            r.rep.fail(check, (t instanceof AssertionError ? "" : t.getClass().getSimpleName() + " ")
                    + String.valueOf(t.getMessage()) + " at " + r.ctx + trace(t));
        }
    }

    /** The first few stack frames that belong to the game, so the author can find the line. */
    static String trace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : t.getStackTrace()) {
            if (!e.getClassName().startsWith("com.wizzcraft.ima.core.") || e.getClassName().contains("MiniSuite")) continue;
            sb.append(n == 0 ? " [" : " < ").append(e.getFileName()).append(':').append(e.getLineNumber());
            if (++n >= 3) break;
        }
        return n == 0 ? "" : sb + "]";
    }

    // ------------------------------------------------------------------ the checks

    static boolean validRounds(int n) {
        return n == 0 || n == 1 || n == 2 || n == 4 || n == 8;
    }

    static void meta(Run r) throws Exception {
        Report rep = r.rep;
        for (boolean female : new boolean[]{false, true}) {
            r.fresh(female, 760);
            String title = r.mini.title(), how = r.mini.howTo();
            if (!female) {
                rep.title = title;
                rep.rounds = r.mini.rounds();
            }
            String who = female ? " (female wording)" : "";
            if (title == null || title.length() < 1 || title.length() > 16) {
                rep.fail("meta", "title \"" + title + "\"" + who + " must be 1-16 characters, is " + (title == null ? -1 : title.length()));
            }
            if (how == null || how.length() < 20 || how.length() > 170) {
                rep.fail("meta", "howTo" + who + " must be 20-170 characters, is " + (how == null ? -1 : how.length()));
            }
            if (!validRounds(r.mini.rounds())) rep.fail("meta", "rounds() is " + r.mini.rounds() + ", must be 0, 1, 2, 4 or 8");
        }
        // The intro card art must not blow up and must stay reasonable.
        r.fresh(false, 760);
        r.ctx = "art()";
        for (float time : new float[]{0, 0.7f, 3.3f, 100f}) {
            r.g.reset();
            r.mini.art(r.g, Game.W / 2, 288, time);
            if (r.g.prims > MAX_PRIMS) rep.fail("draw", "art() draws " + r.g.prims + " primitives (limit " + MAX_PRIMS + ")");
            if (r.g.nonFinite > 0) rep.fail("draw", "art() has NaN/infinite coordinates");
        }
    }

    /** With no input at all, every round must still finish within 60 simulated seconds. */
    static void ends(Run r) throws Exception {
        float worst = 0;
        String[] modes = {"natural difficulty", "difficulty 0", "difficulty 1"};
        for (int mode = 0; mode < 3; mode++) {
            r.fresh(mode == 1, mode == 2 ? 560 : 760);
            r.trackOverflow = false;
            for (int round = 0; round < r.mini.rounds(); round++) {
                float d = mode == 0 ? r.diff(round) : mode == 1 ? 0f : 1f;
                r.mini.begin(round, d);
                int res = Mini.RUNNING, f = 0;
                while (res == Mini.RUNNING && f < 1800) {
                    r.ctx = "no input, round " + round + ", " + modes[mode] + ", frame " + f;
                    res = r.frame(null, -1);
                    if (f % 3 == 0) r.draw();
                    f++;
                }
                if (res == Mini.RUNNING) {
                    r.rep.fail("ends", "round " + round + " (" + modes[mode] + ") is still running after 60 s with no input");
                    r.mini.pause();
                } else {
                    worst = Math.max(worst, f * DT);
                }
            }
        }
        r.rep.notes.add(String.format(Locale.ROOT, "no input: longest round %.1f s", worst));
    }

    static void curve(Run r) throws Exception {
        int rounds = r.mini.rounds();
        int sessions = Math.max(3, (int) Math.ceil(pick(240, 100, 30) / (double) rounds));
        float[] scores = new float[SKILLS.length];
        StringBuilder sb = r.rep.curve;
        sb.append("  skill   MISS    OK  PERFECT  score  sec/round  sec/session  (").append(sessions).append(" sessions)\n");
        float best = -1;
        for (int si = 0; si < SKILLS.length; si++) {
            float skill = SKILLS[si];
            int[] cnt = new int[3];
            float secs = 0;
            int played = 0;
            Random rnd = new Random(7);
            for (int s = 0; s < sessions; s++) {
                r.fresh(s % 2 == 1, s % 3 == 0 ? 560 : 760);
                r.trackOverflow = true;
                for (int round = 0; round < rounds; round++) {
                    r.mini.begin(round, r.diff(round));
                    int res = Mini.RUNNING, f = 0;
                    while (res == Mini.RUNNING && f < 30 * 120) {
                        r.ctx = "skill " + skill + ", session " + s + ", round " + round + ", frame " + f + ", screenH " + (int) r.host.screenH;
                        res = r.frame(rnd, skill);
                        if (f % 6 == 0) r.draw();
                        f++;
                    }
                    if (res == Mini.RUNNING) {
                        r.rep.fail("curve", "a round did not end within 120 s at skill " + skill);
                        return;
                    }
                    cnt[res]++;
                    secs += f * DT;
                    played++;
                }
            }
            float score = (cnt[1] + 2f * cnt[2]) / played;
            scores[si] = score;
            sb.append(String.format(Locale.ROOT, "  %.1f    %4.0f%% %4.0f%%   %4.0f%%   %.2f   %6.1f     %6.1f%n", skill,
                    100f * cnt[0] / played, 100f * cnt[1] / played, 100f * cnt[2] / played, score, secs / played, secs / sessions));
            if (best >= 0 && score < best - 0.15f) {
                r.rep.fail("curve", String.format(Locale.ROOT, "score drops from %.2f to %.2f as skill rises to %.1f (allowed: 0.15)", best, score, skill));
            }
            best = Math.max(best, score);
        }
        r.rep.score0 = scores[0];
        r.rep.score1 = scores[SKILLS.length - 1];
        if (scores[0] > 0.6f) r.rep.fail("curve", String.format(Locale.ROOT, "skill 0 scores %.2f, a clueless player must stay at or under 0.6", scores[0]));
        if (scores[SKILLS.length - 1] < 1.6f) r.rep.fail("curve", String.format(Locale.ROOT, "skill 1 scores %.2f, an expert must reach at least 1.6", scores[SKILLS.length - 1]));
    }

    /** Open-ended venues: they never return a result, money never goes negative, and they offer an exit. */
    static void venue(Run r) throws Exception {
        int frames = pick(30 * 600, 30 * 120, 300);
        // The exit button: drawn right after begin(), and tapping its label leaves.
        r.fresh(false, 760);
        r.mini.begin(0, 0.3f);
        for (int i = 0; i < 4; i++) {
            r.ctx = "venue start, frame " + i;
            r.frame(null, -1);
        }
        r.g.capture = true;
        r.draw();
        r.g.capture = false;
        Object[] exit = null;
        for (Object[] t : r.g.texts) if (((String) t[0]).contains("יציאה")) exit = t;
        if (exit == null) {
            r.rep.fail("venue", "no \"יציאה\" text is drawn right after begin(): the player has no visible way out");
        } else {
            float size = (Float) exit[3], w = r.g.textWidth((String) exit[0], size);
            int align = (Integer) exit[4];
            float x = (Float) exit[1] + (align == Gfx.LEFT ? w / 2 : align == Gfx.RIGHT ? -w / 2 : 0);
            float y = (Float) exit[2] - size * 0.35f;
            r.mini.down(x, y);
            r.frame(null, -1);
            r.mini.up(x, y);
            r.frame(null, -1);
            if (!r.mini.wantsExit()) {
                r.rep.fail("venue", String.format(Locale.ROOT, "tapping the \"יציאה\" label at (%.0f, %.0f) does not make wantsExit() true", x, y));
            }
        }
        for (float skill : new float[]{0.2f, 0.8f}) {
            r.fresh(false, skill < 0.5f ? 760 : 560);
            r.trackOverflow = true;
            Random rnd = new Random(11);
            r.mini.begin(0, 0.3f);
            int exits = 0;
            for (int f = 0; f < frames; f++) {
                r.ctx = "venue skill " + skill + ", frame " + f;
                int res = r.frame(rnd, skill);
                if (f % 6 == 0) r.draw();
                if (res != Mini.RUNNING) {
                    r.rep.fail("venue", "update() returned " + res + " but a venue (rounds() == 0) must keep returning RUNNING");
                    res = Mini.RUNNING;
                }
                if (r.host.money < 0) r.rep.fail("venue", "money went negative (" + r.host.money + ")");
                if (r.mini.wantsExit()) {
                    exits++;
                    boolean female = r.host.female;
                    float h = r.host.screenH;
                    r.fresh(female, h);
                    r.mini.begin(0, 0.3f);
                }
            }
            r.rep.notes.add(String.format(Locale.ROOT, "venue skill %.1f, %d s: money 500 -> %d (lowest %d), left %d times", skill,
                    frames / 30, r.host.money, r.host.minMoney, exits));
        }
    }

    /** pause() then begin() at many different moments, with the finger up or down. */
    static void pauses(Run r) throws Exception {
        int rounds = r.mini.rounds();
        boolean venue = rounds == 0;
        List<Integer> points = new ArrayList<>(Arrays.asList(0, 1, 2, 5, 15, 30, 60, 100, 150, 220, 330, 500));
        Random pr = new Random(3);
        for (int i = 0; i < pick(14, 3, 0); i++) points.add(1 + pr.nextInt(900));
        points = points.subList(0, Math.min(points.size(), pick(100, 8, 4)));
        int[] roundPick = rounds <= 1 ? new int[]{0} : new int[]{0, rounds - 1};
        for (int round : roundPick) {
            for (int point : points) {
                for (int variant = 0; variant < 3; variant++) {
                    r.fresh(variant == 1, variant == 2 ? 560 : 760);
                    r.trackOverflow = false;
                    Random rnd = new Random(point * 31L + variant);
                    r.mini.begin(round, r.diff(round));
                    int res = Mini.RUNNING;
                    int f = 0;
                    for (; f < point && res == Mini.RUNNING; f++) {
                        r.ctx = "pause test, point " + point + ", variant " + variant + ", pre-pause frame " + f;
                        res = r.frame(rnd, 0.6f);
                        if (f % 5 == 0) r.draw();
                    }
                    if (variant == 0) {
                        // finger held down at the moment the app is backgrounded
                        r.mini.down(120 + rnd.nextInt(120), TOP + 150);
                    }
                    r.ctx = "pause test, point " + point + ", variant " + variant + ", pause()";
                    r.mini.pause();
                    if (variant == 1) r.mini.pause();
                    r.draw();
                    r.host.screenH = variant == 2 ? 760 : r.host.screenH; // the window may change size too
                    r.mini.begin(round, r.diff(round));
                    r.draw();
                    int limit = venue ? 300 : 30 * 120;
                    res = Mini.RUNNING;
                    for (f = 0; f < limit && res == Mini.RUNNING; f++) {
                        r.ctx = "pause test, point " + point + ", variant " + variant + ", after begin, frame " + f;
                        res = r.frame(rnd, 0.6f);
                        if (f % 5 == 0) r.draw();
                        if (venue && r.mini.wantsExit()) break;
                    }
                    if (!venue && res == Mini.RUNNING) {
                        r.rep.fail("pause", "round " + round + " does not finish within 120 s after pause() at frame " + point + " then begin()");
                    }
                    if (venue && res != Mini.RUNNING) r.rep.fail("pause", "venue returned a result after pause() then begin()");
                    if (r.host.money < 0) r.rep.fail("pause", "money negative after pause()");
                }
            }
        }
    }

    /** Random touches and pauses. Well formed: up only after down. Wild: unbalanced events too. */
    static void monkey(Run r, int frames, float screenH, boolean wild, long seed) throws Exception {
        r.fresh(seed % 2 == 1, screenH);
        r.trackOverflow = false;
        Random rnd = new Random(seed);
        int round = 0, decided = 0;
        r.mini.begin(0, 0);
        boolean isDown = false;
        for (int f = 0; f < frames; f++) {
            r.ctx = (wild ? "wild" : "monkey") + " seed " + seed + ", screenH " + (int) screenH + ", frame " + f;
            float x = rnd.nextFloat() * 400 - 20;
            float y = TOP - 60 + rnd.nextFloat() * (r.host.screenH - FOOT - TOP + 120);
            int a = rnd.nextInt(12);
            if (wild) {
                if (a == 0) r.mini.down(x, y);
                else if (a == 1) r.mini.up(x, y);
                else if (a == 2) r.mini.move(x, y);
                else if (a == 3) {
                    r.mini.down(x, y);
                    r.mini.down(y % 360, x);
                } else if (a == 4) {
                    r.mini.up(x, y);
                    r.mini.up(x, y);
                }
            } else if (a == 0) {
                r.mini.down(x, y);
                isDown = true;
            } else if (a == 1 && isDown) {
                r.mini.up(x, y);
                isDown = false;
            } else if ((a == 2 || a == 3) && isDown) {
                r.mini.move(x, y);
            }
            if (rnd.nextInt(3000) == 0) {
                r.mini.pause();
                round = r.mini.rounds() > 0 ? rnd.nextInt(r.mini.rounds()) : 0;
                r.mini.begin(round, rnd.nextBoolean() ? r.diff(round) : rnd.nextFloat());
            }
            if (f % 4 == 0) r.draw();
            int res = r.frame(null, -1);
            if (r.host.money < 0) r.rep.fail(wild ? "wild" : "monkey", "money went negative");
            if (res != Mini.RUNNING) {
                decided++;
                round = r.mini.rounds() > 0 ? (round + 1) % r.mini.rounds() : 0;
                r.mini.begin(round, r.diff(round));
            } else if (r.mini.wantsExit()) {
                r.fresh(r.host.female, r.host.screenH);
                r.mini.begin(0, 0.3f);
                isDown = false;
            }
        }
        r.rep.notes.add((wild ? "wild " : "monkey ") + frames + " frames at screenH " + (int) screenH + ": " + decided + " rounds decided");
    }

    // ------------------------------------------------------------------ performance (serial)

    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static void perf(Run r) {
        Report rep = r.rep;
        rep.phase = "perf";
        try {
            r.fresh(false, 760);
            r.trackOverflow = false;
            Random rnd = new Random(5);
            int[] round = {0};
            int rounds = r.mini.rounds();
            r.mini.begin(0, rounds == 0 ? 0.3f : r.diff(0));
            int warm = quick ? 1500 : 4000, chunk = quick ? 800 : 2000;
            long tid = Thread.currentThread().getId();
            boolean alloc = MX.isThreadAllocatedMemorySupported();
            if (alloc) MX.setThreadAllocatedMemoryEnabled(true);
            for (int f = 0; f < warm; f++) perfFrame(r, rnd, round, false, tid);
            double bestUs = Double.MAX_VALUE;
            for (int c = 0; c < 3; c++) {
                long ns = 0;
                for (int f = 0; f < chunk; f++) ns += perfFrame(r, rnd, round, false, tid);
                bestUs = Math.min(bestUs, ns / 1000.0 / chunk);
            }
            double bytes = Double.NaN;
            if (alloc) {
                long overhead = 0;
                for (int i = 0; i < 2000; i++) {
                    long a = MX.getThreadAllocatedBytes(tid), b = MX.getThreadAllocatedBytes(tid);
                    overhead += b - a;
                }
                overhead /= 2000;
                long total = 0;
                int n = chunk * 2;
                for (int f = 0; f < n; f++) total += Math.max(0, perfFrame(r, rnd, round, true, tid) - overhead);
                bytes = (double) total / n;
            }
            rep.us = bestUs;
            rep.bytes = bytes;
            if (bestUs > 500) rep.warns.add(String.format(Locale.ROOT, "update+draw takes %.0f us per frame (warn above 500 us; phones are slower than this machine)", bestUs));
            if (bytes > 2048) rep.warns.add(String.format(Locale.ROOT, "allocates %.0f bytes per frame (warn above 2048)", bytes));
        } catch (Throwable t) {
            rep.fail("monkey", "perf run threw " + t + trace(t));
        }
    }

    /** One bot-driven frame. Returns the nanoseconds of update+draw, or (alloc mode) the bytes they allocated. */
    static long perfFrame(Run r, Random rnd, int[] round, boolean alloc, long tid) throws Exception {
        r.mini.bot(rnd, 0.7f);
        long t0, a0 = 0, a1 = 0, t1;
        if (alloc) a0 = MX.getThreadAllocatedBytes(tid);
        t0 = System.nanoTime();
        int res = r.mini.update(DT);
        r.draw();
        t1 = System.nanoTime();
        if (alloc) a1 = MX.getThreadAllocatedBytes(tid);
        int rounds = r.mini.rounds();
        if (res != Mini.RUNNING || r.mini.wantsExit()) {
            if (r.mini.wantsExit()) r.fresh(false, 760);
            round[0] = rounds > 0 ? (round[0] + 1) % rounds : 0;
            r.mini.begin(round[0], rounds == 0 ? 0.3f : r.diff(round[0]));
        }
        return alloc ? a1 - a0 : t1 - t0;
    }

    // ------------------------------------------------------------------ driver

    static void testMini(Report rep, Class<? extends Mini> cls) {
        long t0 = System.nanoTime();
        Run r = new Run(rep, cls);
        try {
            guard(r, "meta", () -> meta(r));
            if (rep.fails.containsKey("meta") && rep.rounds < 0) return;
            boolean venue = rep.rounds == 0;
            if (!validRounds(rep.rounds)) return;
            if (!venue) {
                guard(r, "ends", () -> ends(r));
                guard(r, "curve", () -> curve(r));
            } else {
                guard(r, "venue", () -> venue(r));
            }
            guard(r, "pause", () -> pauses(r));
            int n = pick(150_000, 20_000, 3_000);
            guard(r, "monkey", () -> {
                monkey(r, n, 760, false, 99);
                monkey(r, n, 560, false, 100);
            });
            int nw = pick(50_000, 8_000, 1_500);
            guard(r, "wild", () -> {
                monkey(r, nw, 760, true, 201);
                monkey(r, nw, 560, true, 202);
            });
            rep.ran("draw");
            rep.ran("host");
            if (rep.ovSide > TOL) {
                rep.warns.add(String.format(Locale.ROOT, "draws up to %.0f units beyond the left/right screen edge (the screen clips it)", rep.ovSide));
            }
        } finally {
            rep.ms += (System.nanoTime() - t0) / 1_000_000;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Class<? extends Mini>> discover(Path root, String[] only) throws Exception {
        List<Class<? extends Mini>> out = new ArrayList<>();
        Path pkg = root.resolve("com/wizzcraft/ima/core");
        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.list(pkg)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("Mini.class") && !n.contains("$")).sorted()
                    .forEach(names::add);
        }
        for (String f : names) {
            Class<?> c = Class.forName("com.wizzcraft.ima.core." + f.substring(0, f.length() - 6), false, MiniSuite.class.getClassLoader());
            if (c == Mini.class || !Mini.class.isAssignableFrom(c) || java.lang.reflect.Modifier.isAbstract(c.getModifiers())) continue;
            if (only.length > 0) {
                boolean hit = false;
                String simple = c.getSimpleName().toLowerCase(Locale.ROOT);
                for (String o : only) {
                    String q = o.toLowerCase(Locale.ROOT);
                    hit |= simple.equals(q) || simple.equals(q + "mini");
                }
                if (!hit) continue;
            }
            out.add((Class<? extends Mini>) c);
        }
        return out;
    }

    static String mark(Report r, String check) {
        if (!r.ran.contains(check)) return "-";
        return r.fails.containsKey(check) ? "FAIL" : "ok";
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        String classes = null;
        String[] only = new String[0];
        int threads = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--quick": quick = true; break;
                case "-v": case "--verbose": verbose = true; break;
                case "--only": only = args[++i].split(","); break;
                case "--classes": classes = args[++i]; break;
                case "--threads": threads = Math.max(1, Integer.parseInt(args[++i])); break;
                default: throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        Path root = classes != null ? Path.of(classes) : Path.of(MiniSuite.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Class<? extends Mini>> minis = discover(root, only);
        if (minis.isEmpty()) {
            System.out.println("MiniSuite: no Mini found" + (only.length > 0 ? " for --only " + String.join(",", only) : ""));
            System.exit(2);
        }
        long t0 = System.nanoTime();
        System.out.println("MiniSuite" + (quick ? " (quick)" : "") + ": " + minis.size() + " minis, " + threads + " thread(s)");
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, minis.size()), rr -> {
            Thread t = new Thread(rr, "mini-worker");
            t.setDaemon(true);
            return t;
        });
        List<Report> reports = new ArrayList<>();
        List<Future<?>> fs = new ArrayList<>();
        for (Class<? extends Mini> c : minis) {
            Report rep = new Report(c.getSimpleName());
            reports.add(rep);
            fs.add(pool.submit(() -> testMini(rep, c)));
        }
        for (int i = 0; i < fs.size(); i++) {
            Report rep = reports.get(i);
            try {
                fs.get(i).get(20, TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                rep.fail(rep.phase.equals("pause") ? "pause" : rep.phase.equals("ends") ? "ends" : "monkey",
                        "timed out after 20 minutes in phase \"" + rep.phase + "\" (an endless loop?)");
            } catch (Exception e) {
                rep.fail("meta", "suite crashed: " + e.getCause());
            }
        }
        pool.shutdownNow();
        // Timing and allocation, one Mini at a time on a quiet machine.
        for (int i = 0; i < minis.size(); i++) {
            Report rep = reports.get(i);
            if (rep.rounds >= 0 && validRounds(rep.rounds) && !rep.fails.containsKey("meta")) perf(new Run(rep, minis.get(i)));
        }

        print(reports, (System.nanoTime() - t0) / 1_000_000);
        boolean bad = false;
        for (Report r : reports) bad |= r.failed();
        System.out.flush();
        System.exit(bad ? 1 : 0);
    }

    static void print(List<Report> reports, long wallMs) {
        System.out.println();
        System.out.println("Per Mini details");
        for (Report r : reports) {
            System.out.println();
            System.out.println("== " + r.name + "  \"" + r.title + "\"  rounds=" + r.rounds + "  (" + r.ms + " ms)");
            if (r.curve.length() > 0) System.out.print(r.curve);
            for (String n : r.notes) if (verbose || n.startsWith("venue") || n.startsWith("no input")) System.out.println("  " + n);
            for (Map.Entry<String, List<String>> e : r.fails.entrySet()) {
                for (String m : e.getValue()) System.out.println("  FAIL " + e.getKey() + ": " + m);
            }
            for (String w : r.warns) System.out.println("  warn: " + w);
        }
        System.out.println();
        System.out.println("Summary");
        StringBuilder h = new StringBuilder(String.format("%-14s %3s", "Mini", "rnd"));
        for (String c : CHECKS) h.append(String.format(" %-5s", c));
        h.append(String.format(" | %5s %5s | %7s %7s | %5s %5s %5s", "s0", "s1", "us/fr", "B/fr", "prims", "ovTop", "ovBot"));
        System.out.println(h);
        int failedMinis = 0, failedChecks = 0, warnCount = 0;
        for (Report r : reports) {
            StringBuilder sb = new StringBuilder(String.format("%-14s %3d", r.name, r.rounds));
            for (String c : CHECKS) {
                String m = c.equals("draw") || c.equals("host") ? (r.ran.contains(c) ? (r.fails.containsKey(c) ? "FAIL" : "ok") : "-") : mark(r, c);
                sb.append(String.format(" %-5s", m));
            }
            sb.append(String.format(" | %5s %5s | %7s %7s | %5d %5.0f %5.0f", fmt(r.score0, "%.2f"), fmt(r.score1, "%.2f"),
                    fmt((float) r.us, "%.0f"), fmt((float) r.bytes, "%.0f"), r.maxPrims, r.ovTop, r.ovBottom));
            System.out.println(sb);
            if (r.failed()) failedMinis++;
            failedChecks += r.fails.size();
            warnCount += r.warns.size();
        }
        System.out.printf("%n%s: %d minis, %d failed (%d failed checks), %d warnings, %d ms wall%n",
                failedMinis == 0 ? "MINISUITE OK" : "MINISUITE FAILED", reports.size(), failedMinis, failedChecks, warnCount, wallMs);
    }

    static String fmt(float v, String f) {
        return Float.isNaN(v) ? "-" : String.format(Locale.ROOT, f, v);
    }
}
