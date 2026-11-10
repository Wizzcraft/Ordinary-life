package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks the whole year for a set of player profiles and checks the story data against the contract in
 * Beat's Javadoc. Prints a line per profile and STORY OK at the end, or the problems and exit code 1.
 *
 * java -cp tools/out/classes com.wizzcraft.ima.core.StoryCheck
 */
final class StoryCheck {
    private static final Pattern ASSIGN = Pattern.compile("([a-z0-9_.]+)(=|\\+=|-=)(-?\\d+)");
    private static final String[] JOB_M = {"טבח", "מלצר", "אופה", "ברמן", "מתדלק", "קופאי", "רופא"};
    private static final String[] JOB_F = {"טבחית", "מלצרית", "אופה", "ברמנית", "מתדלקת", "קופאית", "רופאה"};

    private static final List<String> problems = new ArrayList<>();
    private static final Map<String, String> validated = new HashMap<>();

    /** A scripted player: what they do every day and which answer they pick. */
    static final class Profile {
        final String name;
        final boolean female;
        int startMoney = 1000;
        int hireDay = -1, job = -1, rank1 = Integer.MAX_VALUE, rank2 = Integer.MAX_VALUE;
        double workRate = 0.9, spend = 45;
        int[] wage = {150, 210, 280};
        int proDay = -1;
        double eduPerDay, fitPerDay;
        int alleyFrom = -1, alleyEvery = 3, alleyHeat = 8, alleyMoney = 220, recordDay = -1, alleyUntil = 999;
        int collapseDay = -1;
        boolean tired;
        float trustPerCall;
        Map<Integer, String> lies = new HashMap<>();
        Map<String, Integer> picks = new HashMap<>();
        String expect;

        Profile(String name, boolean female) {
            this.name = name;
            this.female = female;
        }

        Profile pick(String id, int i) {
            picks.put(id, i);
            return this;
        }

        Profile lie(int day, String fx) {
            lies.put(day, fx);
            return this;
        }
    }

    public static void main(String[] args) {
        List<Profile> ps = profiles();
        Set<String> reached = new HashSet<>();
        Set<String> endings = new LinkedHashSet<>();

        List<Beat> daily = Story.allDaily();
        Set<String> ids = new HashSet<>();
        for (Beat b : daily) {
            if (!ids.add(b.id)) problems.add("duplicate id " + b.id);
            validate(b, false);
        }
        Facts probe = new Facts();
        for (Beat b : Story.intro(probe)) {
            if (!ids.add(b.id)) problems.add("duplicate id " + b.id);
            validate(b, true);
        }
        int n = Story.intro(probe).size();
        if (n < 3 || n > 5) problems.add("intro has " + n + " beats");

        System.out.println("morning beats: " + daily.size() + ", with choices: " + countChoices(daily));
        System.out.println("cast: " + tally(daily));
        for (Profile p : ps) {
            String end = run(p, reached, ids);
            endings.add(end);
            if (p.expect != null && !p.expect.equals(end))
                System.out.println("  (note: " + p.name + " expected " + p.expect + ")");
        }

        List<String> unreached = new ArrayList<>();
        for (Beat b : daily) if (!reached.contains(b.id)) unreached.add(b.id);
        if (!unreached.isEmpty()) problems.add("unreached beats: " + unreached);
        for (String e : Story.ENDINGS) if (!endings.contains(e)) problems.add("ending never reached: " + e);
        System.out.println("reached " + (daily.size() - unreached.size()) + "/" + daily.size()
                + " morning beats, endings " + endings.size() + "/" + Story.ENDINGS.length + ": " + endings);

        if (problems.isEmpty()) {
            System.out.println("STORY OK");
        } else {
            for (String s : new TreeSet<>(problems)) System.out.println("PROBLEM " + s);
            System.exit(1);
        }
    }

    private static int countChoices(List<Beat> bs) {
        int c = 0;
        for (Beat b : bs) if (b.options.length > 0) c++;
        return c;
    }

    // ------------------------------------------------------------------ the simulation

    private static String run(Profile p, Set<String> reached, Set<String> ids) {
        Random r = new Random(p.name.hashCode());
        Facts f = new Facts();
        f.female = p.female;
        f.name = p.female ? "נועה" : "איתי";
        f.money = p.startMoney;
        f.food = f.water = f.energy = 80;
        int seen = 0, daysWith = 0, gap = 0, maxGap = 0;
        int shiftsInJob = 0;
        for (Beat b : Story.intro(f)) apply(f, b, p);

        for (int day = 1; day < Story.FINAL_DAY; day++) {
            f.day = day;
            f.month = (day - 1) / 10 + 1;
            f.dayOfMonth = (day - 1) % 10 + 1;
            f.daysToRent = 10 - (day - 1) % 10;
            f.rentDue = 900;
            if (p.collapseDay == day) f.vars.put("collapses", 1f);
            f.energy = p.tired && day % 9 == 0 ? 15 : 70;

            List<Beat> today = Story.onNewDay(f, r);
            if (today.size() > 2) problems.add(p.name + " day " + day + ": " + today.size() + " beats");
            if (today.isEmpty()) {
                gap++;
            } else {
                daysWith++;
                maxGap = Math.max(maxGap, gap);
                gap = 0;
            }
            if (System.getProperty("trace") != null && p.name.equals(System.getProperty("trace")))
                for (Beat b : today) System.out.println("    day " + day + "  " + b.id);
            for (Beat b : today) {
                if (f.seen(b.id)) problems.add(p.name + ": " + b.id + " repeated on day " + day);
                reached.add(b.id);
                seen++;
                apply(f, b, p);
            }

            // The day itself.
            if (p.hireDay > 0 && day >= p.hireDay && f.jobId < 0) {
                f.jobId = p.job;
                f.vars.put("hired." + p.job, 1f);
                f.jobsHeld = 1;
            }
            if (p.proDay > 0 && day >= p.proDay && f.jobId < 6) {
                f.jobId = 6;
                f.jobRank = 0;
                f.jobsHeld = 2;
            }
            if (f.jobId >= 0) {
                f.jobName = f.female ? JOB_F[f.jobId] : JOB_M[f.jobId];
                if (r.nextDouble() < p.workRate) {
                    f.shiftsDone++;
                    shiftsInJob++;
                    int w = f.jobId >= 6 ? 400 : p.wage[f.jobRank];
                    f.money += w;
                    f.earnedYesterday = w;
                }
                if (f.jobId < 6) f.jobRank = shiftsInJob >= p.rank2 ? 2 : shiftsInJob >= p.rank1 ? 1 : 0;
            }
            f.money -= (int) p.spend;
            if (f.money < 0) f.money = 0;
            f.edu = Math.min(100, f.edu + (float) p.eduPerDay);
            f.fit = Math.min(100, f.fit + (float) p.fitPerDay);
            if (p.alleyFrom > 0 && day >= p.alleyFrom && day <= p.alleyUntil && day % p.alleyEvery == 0) {
                f.heat = Math.min(100, f.heat + p.alleyHeat);
                f.money += p.alleyMoney;
            }
            f.heat = Math.max(0, f.heat - 1);
            if (p.recordDay == day) {
                f.record = true;
                f.vars.put("record", 1f);
                f.vars.put("arrests", 1f);
            }
            // Mom's weekly call, as the phone writer would play it.
            if (day % 7 == 0) {
                f.vars.put("mom.trust", clamp(f.var("mom.trust") + p.trustPerCall, 0, 100));
                String lie = p.lies.get(day);
                if (lie != null) applyFx(f, lie, "mom call");
            }
            if (day % 10 == 0) {
                if (f.money >= 900) f.money -= 900;
                else {
                    f.rentMissed++;
                    f.rentDebt += 900;
                }
            }
            if (f.rentDebt > 0 && f.money >= f.rentDebt + 200) {
                f.money -= f.rentDebt;
                f.rentDebt = 0;
            }
        }

        f.day = Story.FINAL_DAY;
        List<Beat> fin = Story.finale(f);
        if (fin.size() < 5 || fin.size() > 9) problems.add(p.name + ": finale has " + fin.size() + " beats");
        Set<String> finIds = new HashSet<>();
        for (Beat b : fin) {
            validate(b, true);
            if (!finIds.add(b.id)) problems.add(p.name + ": finale repeats " + b.id);
            if (!b.id.startsWith("st.fin.") && !b.id.startsWith("st.end.")) problems.add("finale id " + b.id);
            if (ids.contains(b.id)) problems.add("finale id clashes with a morning beat: " + b.id);
        }
        if (System.getProperty("trace") != null && p.name.equals(System.getProperty("trace")))
            for (Beat b : fin) System.out.println("    finale " + b.id + "  mazal=" + f.var("story.mazal") + " home=" + f.var("story.home"));
        String end = fin.isEmpty() ? "?" : fin.get(fin.size() - 1).speaker;
        boolean known = false;
        for (String e : Story.ENDINGS) known |= e.equals(end);
        if (!known) problems.add(p.name + ": last finale beat is not an ending: " + end);
        System.out.printf("%-22s %s  beats %3d  days with a beat %3d/120  longest quiet %2d  money %5d  lies %d  trust %3.0f  -> %s%n",
                p.name, p.female ? "F" : "M", seen, daysWith, Math.max(maxGap, gap), f.money,
                (int) f.var("lie.count"), f.var("mom.trust"), end);
        return end;
    }

    private static void apply(Facts f, Beat b, Profile p) {
        int i = 0;
        if (b.options.length > 0) {
            Integer want = p.picks.get(b.id);
            i = want == null ? 0 : Math.min(want, b.options.length - 1);
        }
        if (b.effects != null && i < b.effects.length) applyFx(f, b.effects[i], b.id);
        f.vars.put("seen." + b.id, 1f);
    }

    private static void applyFx(Facts f, String fx, String where) {
        if (fx == null || fx.isEmpty()) return;
        for (String part : fx.split(";")) {
            Matcher mt = ASSIGN.matcher(part);
            if (!mt.matches()) {
                problems.add(where + ": bad effect '" + part + "'");
                continue;
            }
            String k = mt.group(1), op = mt.group(2);
            float n = Integer.parseInt(mt.group(3));
            float old = get(f, k);
            float v = op.equals("=") ? n : op.equals("+=") ? old + n : old - n;
            set(f, k, v);
        }
    }

    private static float get(Facts f, String k) {
        switch (k) {
            case "money": return f.money;
            case "food": return f.food;
            case "water": return f.water;
            case "energy": return f.energy;
            case "fit": return f.fit;
            case "cha": return f.cha;
            case "edu": return f.edu;
            case "heat": return f.heat;
            default: return f.var(k);
        }
    }

    private static void set(Facts f, String k, float v) {
        switch (k) {
            case "money": f.money = (int) Math.max(0, v); return;
            case "food": f.food = clamp(v, 0, 100); return;
            case "water": f.water = clamp(v, 0, 100); return;
            case "energy": f.energy = clamp(v, 0, 100); return;
            case "fit": f.fit = clamp(v, 0, 100); break;
            case "cha": f.cha = clamp(v, 0, 100); break;
            case "edu": f.edu = clamp(v, 0, 100); break;
            case "heat": f.heat = clamp(v, 0, 100); break;
            default: break;
        }
        if (k.startsWith("mom.")) v = clamp(v, 0, 100);
        f.vars.put(k, v);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ the contract

    private static void validate(Beat b, boolean dynamicOk) {
        String key = b.id + "|" + b.m + "|" + b.f;
        if (validated.containsKey(key)) return;
        validated.put(key, "");
        String id = b.id;
        if (!id.startsWith("st.")) problems.add(id + ": id must start with st.");
        if (!id.matches("[a-z0-9_.]+")) problems.add(id + ": id has odd characters");
        if (b.speaker == null || b.speaker.isEmpty() || b.speaker.length() > 24) problems.add(id + ": speaker");
        text(id, "speaker", b.speaker, 24);
        if (b.m == null || b.m.isEmpty() || b.f == null || b.f.isEmpty()) problems.add(id + ": missing gender text");
        text(id, "m", b.m, 220);
        text(id, "f", b.f, 220);
        if (b.f != null && Pattern.compile("(^|[^א-ת])אתה([^א-ת]|$)").matcher(b.f).find())
            problems.add(id + ": feminine text says אתה");
        int n = b.options.length;
        if (n == 1 || n > 3) problems.add(id + ": " + n + " options");
        for (String o : b.options) text(id, "option", o, 18);
        if (n > 0) {
            if (b.repliesM != null && b.repliesM.length != n) problems.add(id + ": repliesM length");
            if (b.repliesF != null && b.repliesF.length != n) problems.add(id + ": repliesF length");
            if ((b.repliesM == null) != (b.repliesF == null)) problems.add(id + ": replies for one gender only");
            if (b.effects == null || b.effects.length != n) problems.add(id + ": effects length");
            for (int i = 0; i < n; i++) {
                if (b.repliesM != null && b.repliesM[i] != null) text(id, "reply", b.repliesM[i], 220);
                if (b.repliesF != null && b.repliesF[i] != null) text(id, "reply", b.repliesF[i], 220);
                if (b.repliesF != null && b.repliesF[i] != null
                        && Pattern.compile("(^|[^א-ת])אתה([^א-ת]|$)").matcher(b.repliesF[i]).find())
                    problems.add(id + ": feminine reply says אתה");
            }
        } else if (b.effects != null && b.effects.length != 1) {
            problems.add(id + ": plain beat with " + b.effects.length + " effects");
        }
        if (b.effects != null) {
            boolean marks = false;
            for (String e : b.effects) {
                effect(id, e);
                marks |= e != null && e.contains("seen." + id + "=1");
            }
            if (!marks) problems.add(id + ": does not mark itself seen");
        }
    }

    private static void effect(String id, String fx) {
        if (fx == null || fx.isEmpty()) return;
        for (String part : fx.split(";")) {
            Matcher mt = ASSIGN.matcher(part);
            if (!mt.matches()) {
                problems.add(id + ": effect does not parse '" + part + "'");
                continue;
            }
            String k = mt.group(1), op = mt.group(2);
            int n = Math.abs(Integer.parseInt(mt.group(3)));
            if (k.equals("gems")) problems.add(id + ": grants gems");
            boolean delta = !op.equals("=");
            int max = Integer.MAX_VALUE;
            switch (k) {
                case "money": max = 150; break;
                case "fit": case "cha": case "edu": max = 3; break;
                case "heat": max = 15; break;
                case "food": case "water": case "energy": max = 20; break;
                default:
                    if (k.startsWith("mom.") && delta) max = 10;
            }
            if (!delta && (k.equals("money") || k.equals("heat") || k.equals("fit") || k.equals("cha")
                    || k.equals("edu") || k.equals("food") || k.equals("water") || k.equals("energy")))
                problems.add(id + ": sets " + k + " outright");
            if (n > max) problems.add(id + ": " + k + " change " + n + " over the limit " + max);
            if (k.startsWith("story.") || k.startsWith("seen.") || k.startsWith("lie.") || k.startsWith("mom.")
                    || k.equals("money") || k.equals("heat") || k.equals("fit") || k.equals("cha")
                    || k.equals("edu") || k.equals("food") || k.equals("water") || k.equals("energy")) continue;
            problems.add(id + ": writes a variable outside its lane: " + k);
        }
    }

    private static void text(String id, String what, String s, int max) {
        if (s == null) return;
        String shown = s.replace("{name}", "שמואלה");
        if (shown.length() > max) problems.add(id + ": " + what + " is " + shown.length() + " chars (max " + max + ")");
        if (s.replace("{name}", "").matches("(?s).*[{}].*")) problems.add(id + ": stray brace in " + what);
        String bare = s.replace("{name}", "");
        for (int i = 0; i < bare.length(); ) {
            int cp = bare.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) problems.add(id + ": Latin letter in " + what);
            if (cp >= 0x0591 && cp <= 0x05C7) problems.add(id + ": nikud in " + what);
            if (cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0xFE0F) problems.add(id + ": emoji in " + what);
        }
        if (!s.isEmpty() && Character.isDigit(s.charAt(0))) problems.add(id + ": " + what + " starts with a number");
    }

    // ------------------------------------------------------------------ the players

    private static List<Profile> profiles() {
        List<Profile> ps = new ArrayList<>();

        Profile p = new Profile("honest cook", false);
        p.hireDay = 3; p.job = 0; p.rank1 = 12; p.rank2 = 35; p.fitPerDay = 0.5;
        p.pick("st.raf.meet", 0).pick("st.raf.again", 1);
        p.expect = Story.END_PROUD;
        ps.add(p);

        p = new Profile("modest waiter", true);
        p.startMoney = 120; p.hireDay = 16; p.job = 1; p.workRate = 0.85; p.spend = 55;
        p.pick("st.raf.meet", 2).pick("st.m11.mazal", 1).pick("st.cd.clean", 1);
        p.expect = Story.END_MODEST;
        ps.add(p);

        p = new Profile("liar cashier", true);
        p.hireDay = 5; p.job = 5; p.rank1 = 20; p.trustPerCall = -2;
        p.lie(14, "lie.job=1;lie.count+=1").lie(21, "lie.money=1;lie.count+=1").lie(28, "lie.home=1;lie.count+=1");
        p.pick("st.intro.mazal", 1).pick("st.maz.soup", 1).pick("st.maz.cat", 1).pick("st.maz.nephew", 1)
                .pick("st.m8.holiday", 1).pick("st.m11.mazal", 1).pick("st.maz.shoko", 1).pick("st.m12.land", 1).pick("st.cd.clean", 1)
                .pick("st.cd.landcover", 1).pick("st.raf.meet", 2).pick("st.per.visit", 0).pick("st.m5.land", 1);
        p.spend = 120;
        p.expect = Story.END_FARCE;
        ps.add(p);

        p = new Profile("warm liar baker", false);
        p.hireDay = 3; p.job = 2; p.rank1 = 12; p.rank2 = 30; p.trustPerCall = 2;
        p.lie(14, "lie.job=1;lie.count+=1").lie(35, "lie.home=1;lie.count+=1");
        p.pick("st.intro.mazal", 1).pick("st.maz.soup", 1).pick("st.maz.cat", 1).pick("st.maz.nephew", 1)
                .pick("st.m8.holiday", 1).pick("st.m11.mazal", 1).pick("st.maz.shoko", 1).pick("st.raf.meet", 2)
                .pick("st.maz.cover", 1);
        p.expect = Story.END_KNEW;
        ps.add(p);

        p = new Profile("criminal", false);
        p.hireDay = 6; p.job = 4; p.rank1 = 15; p.rank2 = 45; p.workRate = 0.8;
        p.alleyFrom = 35; p.recordDay = 75; p.alleyHeat = 9;
        p.pick("st.raf.meet", 1).pick("st.cop.warn", 1).pick("st.raf.parents", 0);
        p.expect = Story.END_BOSS;
        ps.add(p);

        p = new Profile("broke", true);
        p.startMoney = 300; p.hireDay = 50; p.job = 5; p.workRate = 0.35; p.spend = 75;
        p.collapseDay = 30; p.tired = true;
        p.pick("st.raf.meet", 2).pick("st.rent.late1", 1).pick("st.rent.work", 1).pick("st.dad.broke", 1);
        p.expect = Story.END_HOME;
        ps.add(p);

        p = new Profile("rich barman", true);
        p.hireDay = 3; p.job = 3; p.rank1 = 10; p.rank2 = 28; p.workRate = 1.0; p.wage = new int[]{180, 260, 340};
        p.lie(14, "lie.job=1;lie.count+=1").lie(21, "lie.money=1;lie.count+=1");
        p.pick("st.raf.meet", 2).pick("st.dana.tips", 1);
        p.expect = Story.END_TRUE;
        ps.add(p);

        p = new Profile("student", true);
        p.hireDay = 4; p.job = 5; p.rank1 = 10; p.rank2 = 30; p.eduPerDay = 0.7; p.proDay = 85;
        p.pick("st.raf.meet", 0).pick("st.raf.again", 1).pick("st.per.expired", 1);
        p.expect = Story.END_PROUD;
        ps.add(p);

        p = new Profile("night barman", false);
        p.hireDay = 4; p.job = 3; p.rank1 = 14; p.rank2 = 40;
        p.alleyFrom = 40; p.alleyUntil = 52; p.alleyEvery = 2; p.alleyHeat = 10; p.alleyMoney = 100;
        p.pick("st.raf.meet", 0).pick("st.raf.again", 0).pick("st.raf.second", 1).pick("st.cop.warn", 0)
                .pick("st.dana.tips", 0).pick("st.gabi.glass", 1);
        ps.add(p);

        p = new Profile("liar waiter", false);
        p.hireDay = 5; p.job = 1; p.workRate = 0.8; p.trustPerCall = 2;
        p.pick("st.dad.relay", 1).pick("st.rina.visit", 0).pick("st.raf.meet", 2)
                .pick("st.intro.mazal", 1).pick("st.maz.soup", 1).pick("st.maz.cat", 1).pick("st.maz.nephew", 1)
                .pick("st.m8.holiday", 1).pick("st.m11.mazal", 1).pick("st.maz.shoko", 1);
        p.expect = Story.END_KNEW;
        ps.add(p);

        return ps;
    }

    private static Map<String, Integer> tally(List<Beat> bs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Beat b : bs) m.merge(b.speaker, 1, Integer::sum);
        return m;
    }
}
