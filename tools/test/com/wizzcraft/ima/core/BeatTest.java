package com.wizzcraft.ima.core;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Beat construction rules (from the Javadoc, which is the specification) and a lint over every Beat the
 * game declares: any static field of type Beat, Beat[] or a collection of Beats, in any core class, is
 * checked automatically, so narrative authors get the lint for free.
 */
public final class BeatTest {
    private BeatTest() {
    }

    static final Pattern KEY = Pattern.compile("[a-z0-9_.]+");
    static final Pattern ASSIGNMENT = Pattern.compile("([a-z0-9_.]+)(=|\\+=|-=)(-?[0-9]+(\\.[0-9]+)?)");
    static final Pattern LATIN = Pattern.compile(".*[A-Za-z].*");

    /** Returns the list of rule violations of one beat (empty = fine). */
    static List<String> problems(Beat b) {
        List<String> p = new ArrayList<>();
        if (b.id == null || b.id.isBlank()) p.add("id is blank");
        else if (!KEY.matcher(b.id).matches()) p.add("id \"" + b.id + "\" must be lowercase [a-z0-9_.] (it becomes the seen.<id> variable)");
        if (b.speaker == null || b.speaker.isBlank()) p.add("speaker is blank");
        for (String[] pair : new String[][]{{"m", b.m}, {"f", b.f}}) {
            if (pair[1] == null || pair[1].isBlank()) p.add("text " + pair[0] + " is blank");
            else if (pair[1].length() > 220) p.add("text " + pair[0] + " is " + pair[1].length() + " characters, the card fits about 220");
        }
        int n = b.options.length;
        if (n == 1 || n > 3) p.add(n + " options: use none (a plain message) or 2-3 answers");
        for (int i = 0; i < n; i++) {
            String o = b.options[i];
            if (o == null || o.isBlank()) p.add("option " + i + " is blank");
            else if (o.length() > 24) p.add("option " + i + " is " + o.length() + " characters, answers are about 18");
        }
        if (n > 0) {
            if (b.repliesM != null && b.repliesM.length != n) p.add("repliesM has " + b.repliesM.length + " entries for " + n + " options");
            if (b.repliesF != null && b.repliesF.length != n) p.add("repliesF has " + b.repliesF.length + " entries for " + n + " options");
            if ((b.repliesM == null) != (b.repliesF == null)) p.add("repliesM and repliesF must both be given or both be null");
            if (b.effects != null && b.effects.length != n) p.add("effects has " + b.effects.length + " entries for " + n + " options");
        } else if (b.effects != null && b.effects.length > 1) {
            p.add("a beat without options takes at most one effect (effects[0])");
        }
        if (b.effects != null) {
            for (int i = 0; i < b.effects.length; i++) {
                String e = b.effects[i];
                if (e == null || e.isBlank()) continue;
                for (String part : e.split(";")) {
                    if (!ASSIGNMENT.matcher(part.trim()).matches()) p.add("effect " + i + ": \"" + part.trim() + "\" is not key=n, key+=n or key-=n");
                }
            }
        }
        return p;
    }

    // ---- construction

    static void testSayWithTwoGenders() {
        Beat b = Beat.say("mom.hello", "אמא", "מה שלומך, {name}?", "מה שלומך, {name}?");
        T.eq("mom.hello", b.id);
        T.eq("אמא", b.speaker);
        T.eq(0, b.options.length, "no options is an empty array, never null");
        T.isTrue(b.repliesM == null && b.repliesF == null && b.effects == null, "a plain message has no replies or effects");
        T.eq(new ArrayList<String>(), problems(b));
    }

    static void testSayWithOneWording() {
        Beat b = Beat.say("mom.same", "אמא", "תתקשר מתישהו");
        T.eq(b.m, b.f, "one wording is used for both genders");
    }

    static void testNullOptionsBecomeEmpty() {
        Beat b = new Beat("x", "s", "m", "f", null, null, null, null);
        T.isTrue(b.options != null && b.options.length == 0, "options is never null");
    }

    static void testEffectKeepsEverythingElse() {
        Beat b = new Beat("mom.q", "אמא", "מ", "נ", new String[]{"כן", "לא"}, new String[]{"א", "ב"}, new String[]{"ג", "ד"}, null);
        Beat e = b.effect("lie.job=1;mom.trust-=10");
        T.eq(b.id, e.id);
        T.eq(b.speaker, e.speaker);
        T.eq(b.m, e.m);
        T.eq(b.f, e.f);
        T.eq(b.options, e.options);
        T.eq(b.repliesM, e.repliesM);
        T.eq(b.repliesF, e.repliesF);
        T.eq(new String[]{"lie.job=1;mom.trust-=10"}, e.effects, "effect() sets effects[0]");
        T.isTrue(b.effects == null, "the original beat is unchanged (Beat is immutable)");
    }

    // ---- the rules themselves, so the lint is known to catch what it claims

    static void testRulesCatchBadBeats() {
        T.isFalse(problems(Beat.say("A.b", "x", "t")).isEmpty(), "uppercase id");
        T.isFalse(problems(Beat.say("", "x", "t")).isEmpty(), "blank id");
        T.isFalse(problems(Beat.say("a", "x", "x".repeat(221))).isEmpty(), "text over 220");
        T.isFalse(problems(new Beat("a", "x", "m", "f", new String[]{"only"}, null, null, null)).isEmpty(), "a single option");
        T.isFalse(problems(new Beat("a", "x", "m", "f", new String[]{"1", "2", "3", "4"}, null, null, null)).isEmpty(), "four options");
        T.isFalse(problems(new Beat("a", "x", "m", "f", new String[]{"1", "2"}, new String[]{"r"}, new String[]{"r"}, null)).isEmpty(), "replies count");
        T.isFalse(problems(Beat.say("a", "x", "t").effect("money+=ten")).isEmpty(), "bad effect grammar");
        T.isFalse(problems(Beat.say("a", "x", "t").effect("Money+=10")).isEmpty(), "uppercase effect key");
        T.eq(new ArrayList<String>(), problems(Beat.say("a", "x", "t").effect("money+=10;lie.job=1;mom.trust-=2.5")));
        T.eq(new ArrayList<String>(), problems(new Beat("a", "x", "m", "f", new String[]{"כן", "לא"}, new String[]{"1", "2"},
                new String[]{"3", "4"}, new String[]{"lie.job=1", "mom.trust-=5"})));
    }

    // ---- lint over every Beat the game declares

    static void collect(Object v, String where, List<Beat> beats, List<String> wheres) {
        if (v == null) return;
        if (v instanceof Beat) {
            beats.add((Beat) v);
            wheres.add(where);
        } else if (v.getClass().isArray() && !v.getClass().getComponentType().isPrimitive()) {
            for (int i = 0; i < Array.getLength(v); i++) collect(Array.get(v, i), where + "[" + i + "]", beats, wheres);
        } else if (v instanceof Collection) {
            int i = 0;
            for (Object o : (Collection<?>) v) collect(o, where + "[" + i++ + "]", beats, wheres);
        }
    }

    static void testEveryDeclaredBeatFollowsTheRules() throws Exception {
        List<Beat> beats = new ArrayList<>();
        List<String> wheres = new ArrayList<>();
        for (String name : TestEnv.appClassNames()) {
            Class<?> c = Class.forName(name, false, BeatTest.class.getClassLoader());
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                String type = f.getGenericType().getTypeName();
                if (!type.contains("Beat")) continue;
                f.setAccessible(true);
                collect(f.get(null), c.getSimpleName() + "." + f.getName(), beats, wheres);
            }
        }
        List<String> bad = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < beats.size(); i++) {
            Beat b = beats.get(i);
            for (String p : problems(b)) bad.add(wheres.get(i) + " (beat \"" + b.id + "\"): " + p);
            if (!ids.add(b.id)) bad.add(wheres.get(i) + ": duplicate beat id \"" + b.id + "\"");
            for (String t : new String[]{b.m, b.f}) {
                if (t != null && LATIN.matcher(t).matches()) T.warn(wheres.get(i) + " (beat \"" + b.id + "\") has Latin letters: " + t);
            }
        }
        System.out.println("BeatTest: checked " + beats.size() + " declared beat(s)");
        T.isTrue(bad.isEmpty(), bad.size() + " beat problem(s):\n  " + String.join("\n  ", bad));
    }
}
