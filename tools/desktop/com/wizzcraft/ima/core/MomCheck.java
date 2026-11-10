package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks MomLines: plays the 17 weekly calls of a year for a set of player profiles (applying the
 * effects itself), validates every beat, summary line and toast, reports beats that never fired and
 * prints sample calls. Prints MOM OK when everything holds.
 * Run: java -cp tools/out/classes com.wizzcraft.ima.core.MomCheck
 */
public final class MomCheck {
    static int fails;
    static final Pattern EFFECT = Pattern.compile("[a-z0-9_.]+(=|\\+=|-=)-?\\d+");
    static final int MSG = 220, OPT = 18, SUMMARY = 70, TOAST = 45;
    /** {name} is measured as a 10-letter name. */
    static final String NAME = "אבגדהוזחטי";
    static final int CALLS = 17;

    enum Policy { TRUTH, LIE, DODGE, RANDOM, CONFESS_LATE }

    interface Step {
        void apply(Facts f, int call, Random r);
    }

    static final class Profile {
        final String name;
        final Policy policy;
        final Step step;

        Profile(String name, Policy policy, Step step) {
            this.name = name;
            this.policy = policy;
            this.step = step;
        }
    }

    static final String[][] JOBS = {{"טבח", "טבחית"}, {"מלצר", "מלצרית"}, {"אופה", "אופה"},
            {"ברמן", "ברמנית"}, {"מתדלק", "מתדלקת"}, {"קופאי", "קופאית"}};

    static void setJob(Facts f, int id, int rank) {
        if (id != f.jobId && id >= 0) f.jobsHeld++;
        f.jobId = id;
        f.jobRank = rank;
        f.jobName = id < 0 ? "" : JOBS[id][f.female ? 1 : 0];
    }

    static List<Profile> profiles() {
        List<Profile> p = new ArrayList<>();
        p.add(new Profile("starving liar", Policy.LIE, (f, k, r) -> {
            f.money = 40 + r.nextInt(80);
            f.food = 10 + r.nextInt(25);
            f.energy = 40;
            f.earnedYesterday = 0;
            f.spentYesterday = 20;
        }));
        p.add(new Profile("honest cook", Policy.TRUTH, (f, k, r) -> {
            setJob(f, k >= 2 ? 0 : -1, k >= 12 ? 2 : k >= 6 ? 1 : 0);
            f.money = 300 + k * 60;
            f.food = 60;
            f.energy = 60;
            f.earnedYesterday = 240;
            f.spentYesterday = 60;
        }));
        p.add(new Profile("flush waiter", Policy.RANDOM, (f, k, r) -> {
            setJob(f, k >= 2 ? 1 : -1, k >= 9 ? 1 : 0);
            f.money = k >= 5 ? 1600 + k * 100 : 500;
            f.food = 70;
            f.energy = 70;
            f.cha = 20 + k * 3;
            f.earnedYesterday = 320;
            f.spentYesterday = 250;
        }));
        p.add(new Profile("collapsing baker", Policy.TRUTH, (f, k, r) -> {
            setJob(f, k >= 3 ? 2 : -1, k >= 10 ? 1 : 0);
            if (k == 4 || k == 9 || k == 14) f.vars.put("collapses", f.var("collapses") + 1);
            f.money = 200;
            f.food = 35;
            f.energy = k % 3 == 0 ? 15 : 45;
            f.earnedYesterday = 200;
            f.spentYesterday = 220;
        }));
        p.add(new Profile("alley barman", Policy.LIE, (f, k, r) -> {
            setJob(f, k >= 2 ? 3 : -1, 0);
            f.heat = Math.min(90, 20 + k * 6);
            f.record = k >= 10;
            f.money = 900;
            f.food = 60;
            f.energy = 30;
            f.earnedYesterday = 400;
            f.spentYesterday = 100;
        }));
        p.add(new Profile("rent-missing gas attendant", Policy.DODGE, (f, k, r) -> {
            setJob(f, k >= 5 ? 4 : -1, 0);
            f.rentMissed = k >= 7 ? 1 : 0;
            f.rentDebt = k >= 7 ? 300 : 0;
            f.money = 90 + r.nextInt(100);
            f.daysToRent = 1 + r.nextInt(4);
            f.food = 50;
            f.energy = 50;
            f.earnedYesterday = 150;
            f.spentYesterday = 200;
        }));
        p.add(new Profile("studious cashier", Policy.TRUTH, (f, k, r) -> {
            setJob(f, k >= 2 ? 5 : -1, k >= 8 ? 1 : 0);
            f.edu = k * 4;
            f.fit = k * 4;
            f.cha = k * 4;
            f.money = 600;
            f.food = 60;
            f.energy = 60;
            f.earnedYesterday = 180;
            f.spentYesterday = 90;
        }));
        p.add(new Profile("job hopper", Policy.RANDOM, (f, k, r) -> {
            int[] seq = {-1, 0, 0, 1, 1, 1, 3, 3, 3, 5, 5, 5, 2, 2, 4, 4, 4};
            setJob(f, seq[k - 1], k >= 15 ? 2 : k >= 11 ? 1 : 0);
            f.money = 300 + r.nextInt(1500);
            f.food = 20 + r.nextInt(70);
            f.energy = 20 + r.nextInt(70);
            f.earnedYesterday = r.nextInt(400);
            f.spentYesterday = r.nextInt(400);
        }));
        p.add(new Profile("chaos", Policy.RANDOM, (f, k, r) -> {
            setJob(f, r.nextInt(7) - 1, r.nextInt(3));
            f.money = r.nextInt(3000);
            f.food = r.nextInt(100);
            f.energy = r.nextInt(100);
            f.heat = r.nextInt(100);
            f.record = r.nextInt(4) == 0;
            f.rentMissed = r.nextInt(4) == 0 ? 1 : 0;
            f.rentDebt = f.rentMissed * 200;
            f.daysToRent = r.nextInt(10);
            f.edu = r.nextInt(60);
            f.fit = r.nextInt(60);
            f.cha = r.nextInt(60);
            if (r.nextInt(5) == 0) f.vars.put("collapses", f.var("collapses") + 1);
            f.earnedYesterday = r.nextInt(500);
            f.spentYesterday = r.nextInt(500);
        }));
        p.add(new Profile("honest idler", Policy.TRUTH, (f, k, r) -> {
            f.money = 700;
            f.food = 70;
            f.energy = 80;
            f.earnedYesterday = 0;
            f.spentYesterday = 40;
        }));
        p.add(new Profile("rich criminal", Policy.RANDOM, (f, k, r) -> {
            f.heat = 75;
            f.record = k >= 3;
            f.money = 3000;
            f.food = 50;
            f.energy = 50;
            f.earnedYesterday = 600;
            f.spentYesterday = 300;
        }));
        p.add(new Profile("honest mess", Policy.TRUTH, (f, k, r) -> {
            setJob(f, k >= 6 ? 2 : -1, 0);
            f.money = 60 + r.nextInt(60);
            f.food = 15;
            f.energy = 15;
            f.heat = 45;
            f.earnedYesterday = 0;
            f.spentYesterday = 80;
        }));
        p.add(new Profile("confessor", Policy.CONFESS_LATE, (f, k, r) -> {
            setJob(f, k >= 4 ? 5 : -1, k >= 10 ? 2 : 0);
            f.money = k < 6 ? 120 : 800;
            f.food = k < 4 ? 20 : 60;
            f.energy = 50;
            f.earnedYesterday = 150;
            f.spentYesterday = 140;
        }));
        return p;
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("dump")) {
            dump();
            return;
        }
        validateStatic();

        Map<String, List<Set<String>>> heardByRun = new LinkedHashMap<>();
        Map<String, Integer> fired = new LinkedHashMap<>();
        for (String id : MomLines.allIds()) fired.put(id, 0);
        List<String> samples = new ArrayList<>();
        int runs = 0, repeats = 0, freshTotal = 0;
        List<Profile> all = profiles();
        for (int pi = 0; pi < all.size(); pi++) {
            Profile p = all.get(pi);
            for (int seed = 0; seed < 8; seed++) {
                boolean female = seed % 2 == 1;
                Random r = new Random(seed * 7919L + p.name.hashCode());
                Facts f = new Facts();
                f.female = female;
                f.name = female ? "נועה" : "איתי";
                f.vars.put("mom.trust", 50f);
                f.vars.put("mom.worry", 50f);
                f.daysToRent = 10;
                f.rentDue = 900;
                Set<String> heard = new HashSet<>();
                int offset = seed % 3;
                for (int k = 1; k <= CALLS; k++) {
                    f.day = Math.max(1, 7 * k - offset);
                    f.month = (f.day - 1) / 10 + 1;
                    f.dayOfMonth = (f.day - 1) % 10 + 1;
                    if (p.name.startsWith("rent") || p.name.equals("chaos")) {
                        // set by the profile
                    } else {
                        f.daysToRent = 10 - f.dayOfMonth + 1;
                    }
                    p.step.apply(f, k, r);
                    List<String> fit = MomLines.fittingIds(f);
                    List<String> fresh = new ArrayList<>();
                    for (String id : fit) if (!f.seen(id)) fresh.add(id);
                    Beat b = MomLines.weekly(f, r);
                    if (b == null) {
                        fail(p.name + ": weekly returned null");
                        continue;
                    }
                    if (!fit.contains(b.id)) fail(p.name + ": " + b.id + " fired without fitting");
                    if (heard.contains(b.id)) {
                        repeats++;
                        if (!fresh.isEmpty()) fail(p.name + ": repeated " + b.id + " while unseen fit: " + fresh);
                    } else {
                        freshTotal++;
                    }
                    heard.add(b.id);
                    fired.merge(b.id, 1, Integer::sum);
                    validateBeat(b, female);
                    int pick = choose(b, p.policy, k, r);
                    String effect = b.effects.length == 0 ? "" : b.effects[Math.min(pick, b.effects.length - 1)];
                    if (seed < 2 && (pi == 1 || pi == 4 || pi == 12) && k == 3 + 4 * seed + pi % 3
                            && b.options.length == 3 && samples.size() < 3 && samples.stream().noneMatch(x -> x.contains("(" + p.name))) {
                        samples.add(sample(p.name, f, b));
                    }
                    apply(f, effect);
                    f.vars.put("seen." + b.id, 1f);
                }
                runs++;
                heardByRun.computeIfAbsent(p.name, x -> new ArrayList<>()).add(heard);
            }
        }

        // Second run: how many of a run's 17 calls the same profile hears again in another run.
        double overlap = 0;
        int pairs = 0;
        for (List<Set<String>> hs : heardByRun.values()) {
            for (int i = 0; i + 2 < hs.size(); i += 2) {
                Set<String> both = new HashSet<>(hs.get(i));
                both.retainAll(hs.get(i + 2));
                overlap += both.size();
                pairs++;
            }
        }

        // A very long life (60 calls): repeats are allowed only once nothing unseen fits.
        int longRepeats = 0;
        {
            Random r = new Random(99);
            Facts f = new Facts();
            f.vars.put("mom.trust", 50f);
            f.vars.put("mom.worry", 50f);
            f.money = 700;
            f.food = 70;
            f.energy = 70;
            for (int k = 1; k <= 60; k++) {
                f.day = 50;
                f.dayOfMonth = 5;
                List<String> fresh = new ArrayList<>();
                for (String id : MomLines.fittingIds(f)) if (!f.seen(id)) fresh.add(id);
                Beat b = MomLines.weekly(f, r);
                if (f.seen(b.id)) {
                    longRepeats++;
                    if (!fresh.isEmpty()) fail("long run: repeated " + b.id + " while unseen fit");
                }
                apply(f, b.effects[0]);
                f.vars.put("seen." + b.id, 1f);
            }
        }

        List<String> never = new ArrayList<>();
        for (Map.Entry<String, Integer> e : fired.entrySet()) if (e.getValue() == 0) never.add(e.getKey());

        System.out.println("weekly beats: " + MomLines.allIds().size());
        System.out.println("runs: " + runs + " x " + CALLS + " calls; fresh calls " + freshTotal + ", repeats " + repeats);
        System.out.println("beats that never fired in any profile: " + (never.isEmpty() ? "none" : never));
        System.out.printf("second run of the same profile repeats %.1f of 17 calls on average%n", overlap / Math.max(1, pairs));
        System.out.println("60-call life: " + longRepeats + " repeats, each only after every fitting beat was heard");
        StringBuilder least = new StringBuilder();
        fired.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(8)
                .forEach(e -> least.append(e.getKey()).append('=').append(e.getValue()).append(' '));
        System.out.println("rarest beats (times fired): " + least);
        System.out.println("summary lines: " + MomLines.allSummaryLines(false).size());
        int toasts = 0;
        for (String s : MomLines.TOAST_SITUATIONS) toasts += MomLines.toastLines(s).length;
        System.out.println("toast lines: " + toasts + " in " + MomLines.TOAST_SITUATIONS.length + " situations");
        System.out.println();
        for (String s : samples) System.out.println(s);
        if (fails > 0) {
            System.out.println("MOM FAIL: " + fails);
            System.exit(1);
        }
        System.out.println("MOM OK");
    }

    // ------------------------------------------------------------------ static validation

    static void validateStatic() {
        List<String> ids = MomLines.allIds();
        Set<String> uniq = new HashSet<>();
        for (String id : ids) {
            if (!uniq.add(id)) fail("duplicate id " + id);
            if (!id.startsWith("mom.")) fail("id without mom. prefix: " + id);
        }
        // Render every beat in several situations, both genders.
        for (int g = 0; g < 2; g++) {
            for (int job = -1; job < 6; job++) {
                Facts f = new Facts();
                f.female = g == 1;
                f.jobId = job;
                f.jobRank = (job + 3) % 3;
                f.vars.put("mom.boxes", 12f);
                for (Beat b : MomLines.allBeats(f)) validateBeat(b, f.female);
            }
        }
        // Gender pairs must exist and differ where the markup says they should.
        Facts fm = new Facts(), ff = new Facts();
        ff.female = true;
        int same = 0;
        List<Beat> bm = MomLines.allBeats(fm), bf = MomLines.allBeats(ff);
        for (int i = 0; i < bm.size(); i++) if (bm.get(i).m.equals(bf.get(i).f)) same++;
        System.out.println("beats whose message is the same for both genders: " + same + " of " + bm.size());

        for (int g = 0; g < 2; g++) {
            for (String s : MomLines.allSummaryLines(g == 1)) {
                checkText("summary", s, SUMMARY, g == 1);
                if (s.contains("{name}")) fail("summary uses {name}: " + s);
            }
        }
        if (MomLines.allSummaryLines(false).size() < 70) fail("fewer than 70 summary lines");
        Random r = new Random(5);
        for (int i = 0; i < 3000; i++) {
            Facts f = randomFacts(r);
            String s = MomLines.summary(f, r);
            if (s == null) fail("summary null");
            else checkText("summary", s, SUMMARY, f.female);
        }
        for (String sit : MomLines.TOAST_SITUATIONS) {
            String[] lines = MomLines.toastLines(sit);
            if (lines == null || lines.length < 6) fail("fewer than six toasts for " + sit);
            for (int g = 0; g < 2; g++) {
                Facts f = new Facts();
                f.female = g == 1;
                for (int i = 0; i < 60; i++) {
                    String s = MomLines.toast(sit, f, r);
                    checkText("toast " + sit, s, TOAST, f.female);
                }
            }
        }
        if (MomLines.toast("swim", new Facts(), r) != null) fail("toast for unknown situation not null");
        if (MomLines.toast(null, new Facts(), r) != null) fail("toast for null situation not null");
    }

    static Facts randomFacts(Random r) {
        Facts f = new Facts();
        f.female = r.nextBoolean();
        f.jobId = r.nextInt(7) - 1;
        f.jobRank = r.nextInt(3);
        f.money = r.nextInt(3000);
        f.food = r.nextInt(100);
        f.energy = r.nextInt(100);
        f.heat = r.nextInt(100);
        f.record = r.nextInt(5) == 0;
        f.earnedYesterday = r.nextInt(3) == 0 ? 0 : r.nextInt(600);
        f.spentYesterday = r.nextInt(500);
        return f;
    }

    static void validateBeat(Beat b, boolean female) {
        String where = b.id;
        if (!MomLines.SPEAKER.equals(b.speaker)) fail(where + ": wrong speaker");
        if (b.m == null || b.f == null || b.m.isEmpty() || b.f.isEmpty()) fail(where + ": missing gender text");
        checkText(where + " m", b.m, MSG, false);
        checkText(where + " f", b.f, MSG, true);
        int n = b.options.length;
        if (n > 3 || n == 1) fail(where + ": " + n + " options");
        if (n > 0) {
            if (b.repliesM == null || b.repliesF == null || b.repliesM.length != n || b.repliesF.length != n)
                fail(where + ": replies do not match options");
            if (b.effects == null || b.effects.length != n) fail(where + ": effects do not match options");
            for (int i = 0; i < n; i++) {
                checkText(where + " option", b.options[i], OPT, female);
                if (b.repliesM != null && i < b.repliesM.length) checkText(where + " reply m", b.repliesM[i], MSG, false);
                if (b.repliesF != null && i < b.repliesF.length) checkText(where + " reply f", b.repliesF[i], MSG, true);
            }
        } else if (b.effects == null || b.effects.length != 1) {
            fail(where + ": plain beat needs one effect");
        }
        if (b.effects != null) for (String e : b.effects) checkEffect(where, e);
    }

    static void checkEffect(String where, String e) {
        if (e == null || e.isEmpty()) return;
        for (String part : e.split(";")) {
            if (!EFFECT.matcher(part).matches()) fail(where + ": bad effect '" + part + "'");
        }
    }

    /**
     * Masculine-only second-person forms that must never reach a woman's text (catches forgotten
     * markup). Matched as whole words, also after a ו/ש prefix; skipped after "היא" (the same spelling
     * is third-person feminine) and after "אבא" (mom talking to dad).
     */
    static final Set<String> MASC_ONLY = new HashSet<>(List.of("אתה", "תגיד", "תאכל", "תתקשר", "תשמור", "תחזיר",
            "תקנה", "תישן", "תלבש", "תשים", "תבוא", "תספר", "תיקח", "תעשה", "תדאג", "תשאל", "תענה", "תשתה",
            "תסביר", "תחייך", "תתבייש", "שב", "קח", "תצא", "תשלח", "תסגור", "תפתח", "תחשוב", "תתנהג", "תראה"));

    /** Words after which a masculine-looking form addresses someone else (she / dad). */
    static final Set<String> THIRD = new HashSet<>(List.of("היא", "והיא", "שהיא", "אבא", "צביקה"));

    static void checkFeminine(String where, String s) {
        String[] tok = s.split("[^\\u05D0-\\u05EA]+");
        String prev = "", prev2 = "";
        for (String t : tok) {
            if (t.isEmpty()) continue;
            String bare = (t.length() >= 4 && (t.charAt(0) == 'ו' || t.charAt(0) == 'ש')) ? t.substring(1) : t;
            boolean hit = MASC_ONLY.contains(t) || (!bare.equals(t) && MASC_ONLY.contains(bare));
            boolean otherSubject = THIRD.contains(prev) || (prev.equals("לא") && THIRD.contains(prev2));
            if (hit && !otherSubject) {
                fail(where + ": masculine form '" + t + "' in feminine text: " + s);
            }
            prev2 = prev;
            prev = t;
        }
    }

    static void checkText(String where, String s, int max, boolean female) {
        if (s == null || s.isEmpty()) {
            fail(where + ": empty text");
            return;
        }
        String measured = s.replace("{name}", NAME);
        if (measured.length() > max) fail(where + ": " + measured.length() + " > " + max + ": " + s);
        String rest = s.replace("{name}", "");
        for (int i = 0; i < rest.length(); ) {
            int cp = rest.codePointAt(i);
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) fail(where + ": Latin letter: " + s);
            if (cp >= 0x0591 && cp <= 0x05C7 && cp != 0x05BE) fail(where + ": nikud: " + s);
            if (cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0xFE0F) fail(where + ": emoji: " + s);
            if (cp == '[' || cp == ']' || cp == '/' || cp == '{' || cp == '}') fail(where + ": leftover markup: " + s);
            i += Character.charCount(cp);
        }
        if (female) checkFeminine(where, s);
    }

    // ------------------------------------------------------------------ playing

    static int choose(Beat b, Policy p, int call, Random r) {
        int n = b.options.length;
        if (n == 0) return 0;
        int lie = -1, confess = -1, truth = -1, dodge = -1;
        for (int i = 0; i < n; i++) {
            String e = b.effects[i];
            if (e.contains("lie.count+=")) lie = i;
            else if (e.contains("lie.job=0") || e.contains("lie.money=0") || e.contains("lie.home=0")) confess = i;
            else if (e.contains("mom.honest+=1") && truth < 0) truth = i;
            else if (e.contains("mom.worry+=3") && e.contains("mom.trust-=1")) dodge = i;
        }
        switch (p) {
            case LIE:
                return lie >= 0 ? lie : r.nextInt(n);
            case TRUTH:
                return confess >= 0 ? confess : truth >= 0 ? truth : r.nextInt(n);
            case DODGE:
                return dodge >= 0 ? dodge : r.nextInt(n);
            case CONFESS_LATE:
                if (call < 10) return lie >= 0 ? lie : r.nextInt(n);
                return confess >= 0 ? confess : truth >= 0 ? truth : r.nextInt(n);
            default:
                return r.nextInt(n);
        }
    }

    static void apply(Facts f, String effect) {
        if (effect == null || effect.isEmpty()) return;
        for (String part : effect.split(";")) {
            String key;
            float val;
            int op; // 0 =, 1 +=, 2 -=
            if (part.contains("+=")) {
                key = part.substring(0, part.indexOf("+="));
                val = Float.parseFloat(part.substring(part.indexOf("+=") + 2));
                op = 1;
            } else if (part.contains("-=")) {
                key = part.substring(0, part.indexOf("-="));
                val = Float.parseFloat(part.substring(part.indexOf("-=") + 2));
                op = 2;
            } else {
                key = part.substring(0, part.indexOf('='));
                val = Float.parseFloat(part.substring(part.indexOf('=') + 1));
                op = 0;
            }
            switch (key) {
                case "money":
                    f.money = (int) (op == 0 ? val : op == 1 ? f.money + val : f.money - val);
                    break;
                case "energy":
                    f.energy = Math.max(0, Math.min(100, op == 0 ? val : op == 1 ? f.energy + val : f.energy - val));
                    break;
                case "meals": case "bottles": case "food": case "water": case "gems":
                    break; // inventory; not modelled here
                default: {
                    float cur = f.var(key);
                    float nv = op == 0 ? val : op == 1 ? cur + val : cur - val;
                    if (key.equals("mom.trust") || key.equals("mom.worry")) nv = Math.max(0, Math.min(100, nv));
                    f.vars.put(key, nv);
                }
            }
        }
    }

    /** Prints every line in both genders, for proofreading. */
    static void dump() {
        for (int g = 0; g < 2; g++) {
            Facts f = new Facts();
            f.female = g == 1;
            f.jobId = 2;
            f.vars.put("mom.boxes", 6f);
            System.out.println(g == 0 ? "===== MALE" : "===== FEMALE");
            for (Beat b : MomLines.allBeats(f)) {
                System.out.println("# " + b.id);
                System.out.println(f.female ? b.f : b.m);
                for (int i = 0; i < b.options.length; i++) {
                    System.out.println("  [" + b.options[i] + "] " + (f.female ? b.repliesF[i] : b.repliesM[i]));
                }
            }
            for (String s : MomLines.allSummaryLines(f.female)) System.out.println("S " + s);
            for (String sit : MomLines.TOAST_SITUATIONS) {
                for (String s : MomLines.toastLines(sit)) System.out.println("T " + sit + " " + MomLines.render(s, f.female, f));
            }
        }
    }

    static String sample(String profile, Facts f, Beat b) {
        boolean fem = f.female;
        StringBuilder sb = new StringBuilder();
        sb.append("---- sample call (").append(profile).append(", day ").append(f.day).append(", ")
                .append(fem ? "female" : "male").append(", ").append(b.id).append(")\n");
        sb.append(b.speaker).append(": ").append((fem ? b.f : b.m).replace("{name}", f.name)).append('\n');
        for (int i = 0; i < b.options.length; i++) {
            sb.append("  [").append(b.options[i]).append("] -> ")
                    .append((fem ? b.repliesF[i] : b.repliesM[i]).replace("{name}", f.name))
                    .append("   {").append(b.effects[i]).append("}\n");
        }
        return sb.toString();
    }

    static final Set<String> FAILED = new HashSet<>();

    static void fail(String msg) {
        if (!FAILED.add(msg)) return; // the same text is rendered in several situations
        fails++;
        if (fails <= 80) System.out.println("FAIL " + msg);
    }
}
