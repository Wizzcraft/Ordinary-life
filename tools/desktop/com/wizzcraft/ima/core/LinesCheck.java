package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exercises every function of {@link Lines} across jobs, ranks, scores, buildings, hours, months and
 * player states, and checks the text contract. Prints "LINES OK" when everything holds.
 *
 *     java -cp tools/out/classes com.wizzcraft.ima.core.LinesCheck
 */
public final class LinesCheck {
    private static final int START_MAX = 60, TIP_MAX = 70, END_MAX = 70, CLOSED_MAX = 80, BEAT_MAX = 220,
            OPTION_MAX = 18, SPEAKER_MAX = 20;
    private static final Pattern ASSIGN = Pattern.compile("([a-z0-9_.]+)(=|\\+=|-=)(\\d+)");

    private static final List<String> errors = new ArrayList<>();
    private static final Set<String> errorKeys = new HashSet<>();

    private static void fail(String where, String what) {
        String key = where + "|" + what;
        if (errorKeys.add(key) && errors.size() < 200) errors.add(where + ": " + what);
    }

    // ---------------- player states ----------------

    private static final String[] STATES = {"normal", "hungry", "thirsty", "tired", "broke", "flush", "heat",
            "record", "goodjob", "unemployed", "payday", "rentsoon", "wrecked"};

    private static Facts facts(String state, boolean female, float hour, int month) {
        Facts f = new Facts();
        f.female = female;
        f.name = female ? "דנה" : "יואב";
        f.month = month;
        f.day = (month - 1) * 10 + 4;
        f.dayOfMonth = 4;
        f.daysToRent = 7;
        f.minute = hour * 60;
        f.money = 250;
        f.rentDue = 900;
        f.food = 70;
        f.water = 70;
        f.energy = 70;
        f.jobId = 0;
        f.jobRank = 0;
        f.jobName = female ? "טבחית" : "טבח";
        f.fit = 20;
        f.cha = 20;
        f.edu = 20;
        switch (state) {
            case "hungry": f.food = 15; break;
            case "thirsty": f.water = 12; break;
            case "tired": f.energy = 18; break;
            case "broke": f.money = 12; break;
            case "flush": f.money = 2400; break;
            case "heat": f.heat = 65; break;
            case "record": f.record = true; f.heat = 20; break;
            case "goodjob": f.jobId = 7; f.jobRank = 2; f.money = 900; break;
            case "unemployed": f.jobId = -1; f.jobName = ""; break;
            case "payday": f.earnedYesterday = 320; break;
            case "rentsoon": f.daysToRent = 1; f.money = 300; break;
            case "wrecked": f.food = 5; f.water = 5; f.energy = 5; f.money = 0; break;
            default:
        }
        return f;
    }

    // ---------------- text checks ----------------

    private static void text(String where, String s, int max) {
        if (s == null || s.trim().isEmpty()) {
            fail(where, "empty text");
            return;
        }
        if (s.length() > max) fail(where, "too long (" + s.length() + " > " + max + "): " + s);
        String rest = s.replace("{name}", "");
        for (int i = 0; i < rest.length(); ) {
            int cp = rest.codePointAt(i);
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) fail(where, "Latin letter: " + s);
            if (cp >= 0x0591 && cp <= 0x05C7) fail(where, "nikud or cantillation: " + s);
            if (cp > 0xFFFF || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0xFE00 && cp <= 0xFE0F)) {
                fail(where, "emoji: " + s);
            }
            if (cp == '[' || cp == ']' || cp == '|' || cp == '{' || cp == '}') fail(where, "unexpanded marker: " + s);
            i += Character.charCount(cp);
        }
    }

    /** Street effects must stay modest; promotions carry none. */
    private static void effect(String where, String e, boolean street) {
        if (e == null) {
            fail(where, "null effect");
            return;
        }
        if (e.isEmpty()) return;
        for (String part : e.split(";", -1)) {
            Matcher m = ASSIGN.matcher(part);
            if (!m.matches()) {
                fail(where, "effect does not parse: '" + part + "' in " + e);
                continue;
            }
            String key = m.group(1);
            int n = Integer.parseInt(m.group(3));
            if (key.equals("gems")) fail(where, "grants gems: " + e);
            if (!street) continue;
            if (m.group(2).equals("=")) fail(where, "street effects should be relative: " + e);
            int limit;
            switch (key) {
                case "money": limit = 60; break;
                case "fit": case "cha": case "edu": limit = 2; break;
                case "energy": case "food": case "water": limit = 15; break;
                case "heat": limit = 8; break;
                default: limit = -1;
            }
            if (limit < 0) fail(where, "unexpected key '" + key + "' in " + e);
            else if (n > limit) fail(where, "effect too big: " + part);
        }
    }

    private static void beat(String where, Beat b, boolean street) {
        if (b == null) {
            fail(where, "null beat");
            return;
        }
        if (b.id == null || !b.id.startsWith("ln.")) fail(where, "bad id " + b.id);
        text(where + " speaker", b.speaker, SPEAKER_MAX);
        text(where + " m", b.m, BEAT_MAX);
        text(where + " f", b.f, BEAT_MAX);
        int n = b.options.length;
        if (n == 1 || n > 3) fail(where, "option count " + n);
        for (String o : b.options) text(where + " option", o, OPTION_MAX);
        if (b.m != null && b.m.equals(b.f) && b.m.contains("[")) fail(where, "gender marker left in text");
        if (n == 0) {
            if (b.repliesM != null || b.repliesF != null) fail(where, "replies without options");
            if (b.effects != null) {
                if (b.effects.length != 1) fail(where, "plain beat with " + b.effects.length + " effects");
                for (String e : b.effects) effect(where, e, street);
            }
            return;
        }
        if (b.repliesM == null || b.repliesF == null) fail(where, "missing replies for one gender");
        else if (b.repliesM.length != n || b.repliesF.length != n) fail(where, "reply arrays do not match options");
        else for (int i = 0; i < n; i++) {
            text(where + " replyM" + i, b.repliesM[i], BEAT_MAX);
            text(where + " replyF" + i, b.repliesF[i], BEAT_MAX);
        }
        if (b.effects == null || b.effects.length != n) fail(where, "effect array does not match options");
        else for (String e : b.effects) effect(where, e, street);
    }

    // ---------------- main ----------------

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                java.nio.charset.StandardCharsets.UTF_8));
        Random r = new Random(7);
        Map<String, String> beatIds = new HashMap<>();
        int streetCalls = 0, streetNulls = 0;
        Map<String, Integer> streetSeen = new TreeMap<>();
        for (String id : Lines.streetIds()) {
            streetSeen.put(id, 0);
            if (beatIds.put(id, "street") != null) fail("street", "duplicate id " + id);
        }

        // Street encounters across every hour, month, state and gender, with the no-repeat rule.
        int choiceBeats = 0, plainBeats = 0;
        Set<String> choiceIds = new HashSet<>();
        for (String state : STATES) {
            for (int month = 1; month <= 12; month++) {
                for (int hr = 0; hr < 24; hr++) {
                    for (int g = 0; g < 2; g++) {
                        Facts f = facts(state, g == 1, hr + 0.5f, month);
                        List<String> fits = Lines.streetCandidates(f);
                        String where = "street " + state + " m" + month + " h" + hr + (g == 1 ? " f" : " m");
                        Set<String> seen = new LinkedHashSet<>();
                        for (int k = 0; k < fits.size() + 3; k++) {
                            Beat b = Lines.street(f, r);
                            streetCalls++;
                            if (b == null) {
                                streetNulls++;
                                if (!fits.isEmpty()) fail(where, "null although " + fits.size() + " fit");
                                break;
                            }
                            if (!fits.contains(b.id)) fail(where, "returned " + b.id + " which does not fit");
                            boolean unseenLeft = seen.size() < fits.size();
                            if (seen.contains(b.id) && unseenLeft) fail(where, "repeated " + b.id + " while an unseen one fits");
                            seen.add(b.id);
                            f.vars.put("seen." + b.id, 1f);
                            streetSeen.merge(b.id, 1, Integer::sum);
                            beat(where + " " + b.id, b, true);
                            if (!beatIds.containsKey(b.id)) fail(where, "unknown id " + b.id);
                            if (b.options.length > 0) choiceIds.add(b.id);
                        }
                    }
                }
            }
        }
        for (String id : Lines.streetIds()) {
            if (choiceIds.contains(id)) choiceBeats++;
            else plainBeats++;
        }
        List<String> never = new ArrayList<>();
        for (Map.Entry<String, Integer> e : streetSeen.entrySet()) if (e.getValue() == 0) never.add(e.getKey());

        // Shift start and end lines.
        String[] jobNames = {"טבח", "מלצר", "אופה", "ברמן", "מתדלק", "קופאי", "רופא", "עורך דין", "מפתח"};
        int[] startCount = new int[9];
        int[][] endCount = new int[9][3];
        String[] startStates = {"normal", "tired", "hungry", "wrecked"};
        for (int job = 0; job < 9; job++) {
            Set<String> starts = new HashSet<>();
            List<Set<String>> ends = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
            for (String st : startStates) {
                for (int rank = 0; rank <= 2; rank++) {
                    for (int hr : new int[]{3, 5, 9, 14, 19, 23}) {
                        for (int g = 0; g < 2; g++) {
                            Facts f = facts(st, g == 1, hr, 4);
                            for (int k = 0; k < 40; k++) {
                                String s = Lines.shiftStart(job, rank, f, r);
                                text("shiftStart j" + job + " r" + rank + " " + st, s, START_MAX);
                                if (g == 0) starts.add(s);
                            }
                        }
                    }
                }
            }
            for (float score = 0; score <= 2.001f; score += 0.25f) {
                for (int g = 0; g < 2; g++) {
                    Facts f = facts("normal", g == 1, 12, 4);
                    for (int k = 0; k < 40; k++) {
                        String s = Lines.shiftEnd(job, score, f, r);
                        text("shiftEnd j" + job + " s" + score, s, END_MAX);
                        if (g == 0) ends.get(score < 0.7f ? 0 : score < 1.6f ? 1 : 2).add(s);
                    }
                }
            }
            startCount[job] = starts.size();
            for (int b = 0; b < 3; b++) endCount[job][b] = ends.get(b).size();
            if (starts.size() < 8) fail("shiftStart j" + job, "only " + starts.size() + " lines");
            for (int b = 0; b < 3; b++) if (endCount[job][b] < 3) fail("shiftEnd j" + job + " band " + b, "only " + endCount[job][b]);
        }

        // Promotions: one beat per job and rank, unique ids.
        int promos = 0;
        for (int job = 0; job < 9; job++) {
            for (int rank = 0; rank <= 2; rank++) {
                for (int g = 0; g < 2; g++) {
                    Facts f = facts("normal", g == 1, 12, 4);
                    Beat b = Lines.promotion(job, rank, f);
                    beat("promotion j" + job + " r" + rank, b, false);
                    if (b != null && g == 0) {
                        promos++;
                        if (beatIds.put(b.id, "promotion") != null) fail("promotion", "duplicate id " + b.id);
                        if (b.m.equals(b.f) && b.m.indexOf('[') < 0) {
                            // Every promotion addresses the player, so the two forms must differ.
                            fail("promotion j" + job + " r" + rank, "same text for both genders");
                        }
                    }
                }
            }
        }

        // Closed doors.
        int[] closedCount = new int[9];
        for (int b = 0; b < 9; b++) {
            Set<String> seen = new HashSet<>();
            for (int g = 0; g < 2; g++) {
                for (int hr = 0; hr < 24; hr += 3) {
                    Facts f = facts("normal", g == 1, hr, 7);
                    for (int k = 0; k < 40; k++) {
                        String s = Lines.closed(b, f, r);
                        text("closed b" + b, s, CLOSED_MAX);
                        if (g == 0) seen.add(s);
                    }
                }
            }
            closedCount[b] = seen.size();
            if (seen.size() < 4) fail("closed b" + b, "only " + seen.size());
        }

        // Tips.
        Set<String> tips = new HashSet<>();
        for (int k = 0; k < 4000; k++) {
            String s = Lines.tip(r);
            text("tip", s, TIP_MAX);
            tips.add(s);
        }
        if (tips.size() < 30) fail("tip", "only " + tips.size());

        // Odd input must never throw.
        try {
            Lines.shiftStart(-1, 7, null, null);
            Lines.shiftStart(99, -3, new Facts(), r);
            Lines.shiftEnd(42, Float.NaN, null, r);
            Lines.shiftEnd(3, -5, new Facts(), null);
            Lines.shiftEnd(3, 9, new Facts(), r);
            Lines.promotion(-4, 9, null);
            Lines.closed(-1, null, r);
            Lines.closed(77, new Facts(), r);
            Lines.street(null, r);
            Lines.street(new Facts(), null);
            Lines.tip(null);
        } catch (RuntimeException e) {
            fail("odd input", e.toString());
        }

        // ---------------- report ----------------
        System.out.println("street: " + Lines.streetIds().size() + " encounters (" + choiceBeats + " with a choice, "
                + plainBeats + " plain); " + streetCalls + " calls, " + streetNulls + " nulls");
        System.out.println("street never shown: " + (never.isEmpty() ? "none" : String.join(", ", never)));
        StringBuilder sb = new StringBuilder("shiftStart lines per job:");
        for (int j = 0; j < 9; j++) sb.append(' ').append(jobNames[j]).append('=').append(startCount[j]);
        System.out.println(sb);
        sb = new StringBuilder("shiftEnd lines per job (bad/fine/flawless):");
        for (int j = 0; j < 9; j++) {
            sb.append(' ').append(jobNames[j]).append('=').append(endCount[j][0]).append('/')
                    .append(endCount[j][1]).append('/').append(endCount[j][2]);
        }
        System.out.println(sb);
        System.out.println("promotion beats: " + promos);
        sb = new StringBuilder("closed lines per building:");
        for (int b = 0; b < 9; b++) sb.append(' ').append(b).append('=').append(closedCount[b]);
        System.out.println(sb);
        System.out.println("tips: " + tips.size());
        System.out.println("unique beat ids: " + beatIds.size());
        if (!errors.isEmpty()) {
            for (String e : errors) System.out.println("FAIL " + e);
            System.out.println("LINES FAILED (" + errors.size() + " problems)");
            System.exit(1);
        }
        System.out.println("LINES OK");
    }
}
