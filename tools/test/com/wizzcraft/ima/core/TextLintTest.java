package com.wizzcraft.ima.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lints every string literal in app/src/main/java/.../core/*.java against the handbook's text rules:
 * Hebrew only, no nikud, no emoji. Literals with Latin letters are listed as warnings (most are keys and
 * format strings; the ones that mix Hebrew and Latin, or look like sentences, are the suspicious ones).
 */
public final class TextLintTest {
    private TextLintTest() {
    }

    static final class Lit {
        final String file, value;
        final int line;

        Lit(String file, int line, String value) {
            this.file = file;
            this.line = line;
            this.value = value;
        }

        String where() {
            return file + ":" + line;
        }
    }

    /** A small Java lexer: finds string literals and text blocks, skipping comments and char literals. */
    static List<Lit> literals(String file, String src) {
        List<Lit> out = new ArrayList<>();
        int i = 0, n = src.length(), line = 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') line++;
                    i++;
                }
                i += 2;
            } else if (c == '\'') {
                i++;
                while (i < n && src.charAt(i) != '\'') i += src.charAt(i) == '\\' ? 2 : 1;
                i++;
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                int start = line;
                i += 3;
                StringBuilder sb = new StringBuilder();
                while (i < n && !src.startsWith("\"\"\"", i)) {
                    char d = src.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        i = unescape(src, i, sb);
                        continue;
                    }
                    if (d == '\n') line++;
                    sb.append(d);
                    i++;
                }
                i += 3;
                out.add(new Lit(file, start, sb.toString()));
            } else if (c == '"') {
                int start = line;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n && src.charAt(i) != '"' && src.charAt(i) != '\n') {
                    if (src.charAt(i) == '\\' && i + 1 < n) i = unescape(src, i, sb);
                    else sb.append(src.charAt(i++));
                }
                i++;
                out.add(new Lit(file, start, sb.toString()));
            } else {
                i++;
            }
        }
        return out;
    }

    /** Handles one escape at src[i] == '\\'; appends the character; returns the next index. */
    static int unescape(String src, int i, StringBuilder sb) {
        char e = src.charAt(i + 1);
        if (e == 'u') {
            int j = i + 1;
            while (j < src.length() && src.charAt(j) == 'u') j++;
            if (j + 4 <= src.length()) {
                try {
                    sb.append((char) Integer.parseInt(src.substring(j, j + 4), 16));
                    return j + 4;
                } catch (NumberFormatException ignored) {
                    // fall through: keep it literally
                }
            }
        }
        switch (e) {
            case 'n': sb.append('\n'); break;
            case 't': sb.append('\t'); break;
            case 'r': sb.append('\r'); break;
            case '"': sb.append('"'); break;
            case '\\': sb.append('\\'); break;
            case '\'': sb.append('\''); break;
            case '\n': break;
            default: sb.append(e);
        }
        return i + 2;
    }

    static boolean isNikud(int cp) {
        return (cp >= 0x0591 && cp <= 0x05BD) || cp == 0x05BF || cp == 0x05C1 || cp == 0x05C2 || cp == 0x05C4
                || cp == 0x05C5 || cp == 0x05C7;
    }

    /** Emoji and pictographs, including text-style symbols that Android may render as colour emoji. */
    static boolean isEmoji(int cp) {
        return cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x2300 && cp <= 0x23FF)
                || (cp >= 0x2B00 && cp <= 0x2BFF) || cp == 0xFE0F || cp == 0x200D || cp == 0x20E3
                || cp == 0x21A9 || cp == 0x21AA;
    }

    static final Pattern KEYLIKE = Pattern.compile("[A-Za-z0-9_.:=+\\-;{}%$#,/<>()\\[\\]|*&!?@~^]*");
    static final Pattern HAS_LATIN = Pattern.compile(".*[A-Za-z].*", Pattern.DOTALL);

    static String show(String s) {
        String t = s.replace("\n", "\\n");
        return t.length() > 70 ? t.substring(0, 67) + "..." : t;
    }

    static String describe(int cp) {
        return String.format("U+%04X", cp);
    }

    static List<Lit> all() throws IOException {
        List<Lit> lits = new ArrayList<>();
        for (Path p : TestEnv.coreJavaFiles()) {
            lits.addAll(literals(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8)));
        }
        return lits;
    }

    static void testLexerFindsLiterals() {
        String src = "String a = \"שלום\"; // \"not me\"\n/* \"nor me\" */ char q = '\"'; String b = \"x\\u05D0\\\"y\";\n"
                + "String t = \"\"\"\n  one\n  two\"\"\";";
        List<Lit> l = literals("t", src);
        T.eq(3, l.size(), "literals found: " + l.size());
        T.eq("שלום", l.get(0).value);
        T.eq("xא\"y", l.get(1).value, "unicode and quote escapes");
        T.eq(2, l.get(1).line, "line numbers follow newlines inside comments");
        T.isTrue(l.get(2).value.contains("one") && l.get(2).value.contains("two"), "text block");
    }

    static void testClassifiers() {
        T.isTrue(isNikud(0x05B0) && isNikud(0x05BC) && !isNikud(0x05D0), "nikud range excludes the letters");
        T.isTrue(isEmoji(0x1F600) && isEmoji(0x2764) && isEmoji(0xFE0F) && !isEmoji(0x20AA) && !isEmoji(0x05D0), "emoji ranges keep ₪ and Hebrew");
    }

    static void testNoNikudNoEmojiInStringLiterals() throws IOException {
        List<String> bad = new ArrayList<>();
        int literalsSeen = 0, hebrew = 0;
        for (Lit l : all()) {
            literalsSeen++;
            boolean he = false;
            for (int k = 0; k < l.value.length(); ) {
                int cp = l.value.codePointAt(k);
                k += Character.charCount(cp);
                if (cp >= 0x05D0 && cp <= 0x05EA) he = true;
                if (isNikud(cp)) {
                    bad.add(l.where() + ": nikud " + describe(cp) + " in \"" + show(l.value) + "\"");
                    break;
                }
                if (isEmoji(cp)) {
                    bad.add(l.where() + ": emoji/symbol " + describe(cp) + " in \"" + show(l.value) + "\"");
                    break;
                }
            }
            if (he) hebrew++;
        }
        System.out.println("TextLintTest: " + literalsSeen + " string literals, " + hebrew + " with Hebrew letters");
        T.isTrue(literalsSeen > 0, "found no string literals at all: is the working directory the repository root (or -Dima.root)?");
        T.isTrue(bad.isEmpty(), bad.size() + " literal(s) with nikud or emoji:\n  " + String.join("\n  ", bad));
    }

    static void testLatinLettersAreListed() throws IOException {
        int keyLike = 0;
        for (Lit l : all()) {
            if (!HAS_LATIN.matcher(l.value).matches()) continue;
            if (KEYLIKE.matcher(l.value).matches()) {
                keyLike++;
                continue;
            }
            T.warn(l.where() + ": Latin letters in \"" + show(l.value) + "\"");
        }
        if (keyLike > 0) T.warn(keyLike + " key/format-like literals with Latin letters were not listed (ids, var keys, formats)");
    }
}
