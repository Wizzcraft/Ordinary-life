package com.wizzcraft.ima.core;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests the text lint itself: the lexer, and one fixture per rule that must trip it (and one that must not). */
public final class TextLintTest {
    private TextLintTest() {
    }

    static Set<String> rules(String javaSource) {
        Map<String, String> forbidden = new LinkedHashMap<>();
        forbidden.put("קוקאין", "substances are never named");
        Set<String> out = new HashSet<>();
        for (TextLint.Finding f : TextLint.lint("Fixture.java", javaSource, forbidden)) out.add(f.rule);
        return out;
    }

    static void expect(String javaSource, String... want) {
        Set<String> got = rules(javaSource);
        got.remove("glue");
        got.remove("digit");
        Set<String> w = new HashSet<>(java.util.Arrays.asList(want));
        T.eq(w, got, "rules tripped by: " + javaSource);
    }

    static void testLexerFindsLiterals() {
        String src = "String a = \"שלום\"; // \"not me\"\n/* \"nor me\" */ char q = '\"'; String b = \"x\\u05D0\\\"y\";\n"
                + "String t = \"\"\"\n  one\n  two\"\"\";";
        List<TextLint.Lit> l = TextLint.literals("t", src);
        T.eq(3, l.size(), "literals found: " + l.size());
        T.eq("שלום", l.get(0).value);
        T.eq("xא\"y", l.get(1).value, "unicode and quote escapes");
        T.eq(2, l.get(1).line, "line numbers follow newlines inside comments");
        T.isTrue(l.get(2).value.contains("one") && l.get(2).value.contains("two"), "text block");
        T.eq("\"שלום\"", src.substring(l.get(0).start, l.get(0).end), "offsets cover the quotes");
    }

    static void testClassifiers() {
        T.isTrue(TextLint.isNikud(0x05B0) && TextLint.isNikud(0x05BC) && !TextLint.isNikud(0x05D0), "nikud range excludes the letters");
        T.isTrue(TextLint.isEmoji(0x1F600) && TextLint.isEmoji(0x2764) && TextLint.isEmoji(0xFE0F) && !TextLint.isEmoji(0x20AA)
                && !TextLint.isEmoji(0x05D0), "emoji ranges keep the shekel sign and Hebrew");
    }

    static void testCleanLinesPass() {
        expect("x(\"לוחצים על המסך כדי להתחיל.\");");
        expect("x(\"הרווחת ₪120 היום, {name}!\");");
        expect("x(\"שלב %d מתוך %d\");");
        expect("x(\"key.with.latin\"); y(\"OK\");");
        expect("x(\"בלאקג'ק\"); y(\"ק\\\"מ\"); z(\"טיפ\"); w(\"ג'יפ\");", new String[0]);
        expect("x(\"מוכנ[/ה] לעבודה?\"); y(\"[אתה/את] בטוח[/ה]?\");");
    }

    static void testNikudAndEmoji() {
        expect("x(\"שָלום\");", "nikud");
        expect("x(\"כל הכבוד \\uD83D\\uDE00\");", "emoji");
        expect("x(\"שלום\\u200F עולם\");", "control");
    }

    static void testLatinInsideHebrew() {
        expect("x(\"יש לך WiFi בדירה\");", "latin");
    }

    static void testFinalLetters() {
        expect("x(\"שלוםם עולם\");", "final");
        expect("x(\"הכסף נגמר, חברימ\");", "final");
        expect("x(\"אני הולכ הביתה\");");   // כ and פ at a word's end are legal in loan words; not flagged
    }

    static void testSpaces() {
        expect("x(\"יש כאן  שני רווחים\");", "space");
        expect("x(\"רווח לפני פסיק , וזהו\");", "space");
        expect("x(\"מחיר: ₪5 .5\");");
    }

    static void testBrackets() {
        expect("x(\"[עייף/עייפה מאוד\");", "bracket");
        expect("x(\"שלום {name\");", "bracket", "latin");
    }

    static void testGenderPairs() {
        expect("h.t(\"אתה עייף\", \"את עייפה\");");
        expect("h.t(\"הרווחת יפה\", \"הרווחת יפה\");", "samepair");
        expect("x(\"אתה עייף, הכל קשה יותר\");", "masc");
        expect("Beat.say(\"a\", \"mom\", \"אתה אוכל?\", \"את אוכלת?\");");
        expect("String[] m = {\"מה שאתה רוצה\", \"בסדר\"}, f = {\"מה שאת רוצה\", \"בסדר\"};");   // parallel arrays
        expect("x(\"אתה עובד פה?\"); y(\"את השולחן הזה מנקים כל ערב\");", "masc");   // the neighbour is not its twin
    }

    static void testForbiddenWords() {
        expect("x(\"יש לי קוקאין\");", "forbidden");
        expect("x(\"יש לי סחורה\");");
    }

    static void testListedRulesNeverFail() {
        T.isTrue(rules("x(\"הרווחת \" + count + \" שקלים היום\");").contains("glue"), "a glued sentence is listed");
        T.isTrue(rules("x(\"120 שקלים\");").contains("digit"), "a line that starts with a number is listed");
        T.isFalse(TextLint.failing("glue") || TextLint.failing("digit"), "listed rules do not fail the gate");
    }

    static void testFindingKeyIsStableAcrossLines() {
        TextLint.Finding a = TextLint.lint("A.java", "x(\"שלוםם\");", new LinkedHashMap<>()).get(0);
        TextLint.Finding b = TextLint.lint("A.java", "\n\n\nx(\"שלוםם\");", new LinkedHashMap<>()).get(0);
        T.eq(a.key(), b.key(), "moving a line does not change its key");
        T.isTrue(a.key().startsWith("final:A:"), a.key());
    }
}
