package com.wizzcraft.ima.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text lint: every string literal of the game's sources against the handbook's Hebrew rules.
 * It is a safety net under the language editor, not a replacement: it catches what a machine can be
 * sure about, so that a human never has to.
 *
 * <pre>
 * java -cp OUT com.wizzcraft.ima.core.TextLint [-v] [--list rule]
 * </pre>
 *
 * Rules that fail the gate (unless listed in known-issues.txt as {@code text <rule>:<File>:<fingerprint>}):
 * <ul>
 * <li>{@code nikud}, {@code emoji}: the handbook forbids both.</li>
 * <li>{@code latin}: Latin letters inside a Hebrew line (placeholders and format codes aside).</li>
 * <li>{@code final}: a final letter in the middle of a word, or a word ending in a non-final מ נ צ.</li>
 * <li>{@code space}: two spaces in a row, or a space before , . ! ? : inside a Hebrew line.</li>
 * <li>{@code bracket}: unbalanced [ ] or { } in a Hebrew line (gender markup and placeholders).</li>
 * <li>{@code samepair}: {@code t("x", "x")}: the masculine and the feminine wording are identical.</li>
 * <li>{@code masc}: "אתה" in a line that has no feminine partner right beside it.</li>
 * <li>{@code forbidden}: a word from tools/test/text-forbidden.txt (substances are never named, ...).</li>
 * <li>{@code control}: an invisible direction or zero-width character.</li>
 * </ul>
 * Listed only (never fail): {@code digit} a Hebrew line that starts with a number (the handbook says
 * avoid); {@code glue} a sentence glued from Hebrew pieces around a value (hard to translate).
 */
public final class TextLint {
    private TextLint() {
    }

    static final class Lit {
        final String file, value;
        final int line;
        /** Offsets of the literal (quotes included) in the file's text; used to look at its neighbours. */
        final int start, end;

        Lit(String file, int line, String value, int start, int end) {
            this.file = file;
            this.line = line;
            this.value = value;
            this.start = start;
            this.end = end;
        }

        String where() {
            return file + ":" + line;
        }
    }

    static final class Finding {
        final String rule, where, file, text, detail;

        Finding(String rule, Lit l, String detail) {
            this.rule = rule;
            this.where = l.where();
            this.file = l.file;
            this.text = l.value;
            this.detail = detail;
        }

        /** Stable across line moves: rule, file and a fingerprint of the literal. */
        String key() {
            String f = file.endsWith(".java") ? file.substring(0, file.length() - 5) : file;
            return rule + ":" + f + ":" + Integer.toHexString(text.hashCode());
        }

        @Override
        public String toString() {
            return where + "  " + rule + ": " + detail + "  \"" + show(text) + "\"";
        }
    }

    static final String[] FAILING = {"nikud", "emoji", "latin", "final", "space", "bracket", "samepair", "masc", "forbidden", "control"};
    static final String[] LISTED = {"digit", "glue"};

    // ------------------------------------------------------------------ lexer

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
                int startLine = line, start = i;
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
                out.add(new Lit(file, startLine, sb.toString(), start, Math.min(i, n)));
            } else if (c == '"') {
                int startLine = line, start = i;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n && src.charAt(i) != '"' && src.charAt(i) != '\n') {
                    if (src.charAt(i) == '\\' && i + 1 < n) i = unescape(src, i, sb);
                    else sb.append(src.charAt(i++));
                }
                i++;
                out.add(new Lit(file, startLine, sb.toString(), start, Math.min(i, n)));
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

    // ------------------------------------------------------------------ character classes

    static boolean isHebrewLetter(int cp) {
        return cp >= 0x05D0 && cp <= 0x05EA;
    }

    static boolean hasHebrew(String s) {
        for (int i = 0; i < s.length(); i++) if (isHebrewLetter(s.charAt(i))) return true;
        return false;
    }

    static boolean isNikud(int cp) {
        return (cp >= 0x0591 && cp <= 0x05BD) || cp == 0x05BF || cp == 0x05C1 || cp == 0x05C2 || cp == 0x05C4
                || cp == 0x05C5 || cp == 0x05C7;
    }

    /** Emoji and pictographs, including text-style symbols that Android may render as colour emoji. */
    static boolean isEmoji(int cp) {
        return cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x2300 && cp <= 0x23FF)
                || (cp >= 0x2B00 && cp <= 0x2BFF) || cp == 0xFE0F || cp == 0x20E3 || cp == 0x21A9 || cp == 0x21AA;
    }

    /** Invisible characters that change how a line is laid out: direction marks, embeddings, zero-width. */
    static boolean isControl(int cp) {
        return cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0x200E || cp == 0x200F || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069) || cp == 0xFEFF || cp == 0x00AD;
    }

    static boolean isFinalForm(char c) {
        return c == 'ך' || c == 'ם' || c == 'ן' || c == 'ף' || c == 'ץ';
    }

    static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-z0-9_.]*\\}|%[-+0-9.,]*[sdfnxc%]");
    static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    static final Pattern MASC_YOU = Pattern.compile("(?<![א-ת])ו?ש?אתה(?![א-ת])");
    static final Pattern FEM_YOU = Pattern.compile("(?<![א-ת])ו?ש?את(?![א-ת])");
    /** t("masculine", "feminine") with two plain literals. */
    static final Pattern T_PAIR = Pattern.compile("\\bt\\(\\s*\"((?:[^\"\\\\\\n]|\\\\.)*)\"\\s*,\\s*\"((?:[^\"\\\\\\n]|\\\\.)*)\"\\s*\\)");

    static String show(String s) {
        String t = s.replace("\n", "\\n");
        return t.length() > 76 ? t.substring(0, 73) + "..." : t;
    }

    // ------------------------------------------------------------------ rules

    /** The findings for one source file. {@code forbidden} maps a word to the reason it is banned. */
    static List<Finding> lint(String file, String src, Map<String, String> forbidden) {
        List<Finding> out = new ArrayList<>();
        List<Lit> lits = literals(file, src);
        for (int idx = 0; idx < lits.size(); idx++) {
            Lit l = lits.get(idx);
            String v = l.value;
            boolean he = hasHebrew(v);
            for (int k = 0; k < v.length(); ) {
                int cp = v.codePointAt(k);
                k += Character.charCount(cp);
                if (isNikud(cp)) {
                    out.add(new Finding("nikud", l, String.format("U+%04X", cp)));
                    break;
                }
                if (isEmoji(cp)) {
                    out.add(new Finding("emoji", l, String.format("U+%04X", cp)));
                    break;
                }
                if (isControl(cp)) {
                    out.add(new Finding("control", l, String.format("invisible character U+%04X", cp)));
                    break;
                }
            }
            if (!he) continue;
            String bare = PLACEHOLDER.matcher(v).replaceAll(" ");
            Matcher lat = LATIN.matcher(bare);
            if (lat.find()) out.add(new Finding("latin", l, "Latin letter '" + lat.group() + "' in a Hebrew line"));
            String fin = finalLetterError(v);
            if (fin != null) out.add(new Finding("final", l, fin));
            if (v.contains("  ")) out.add(new Finding("space", l, "two spaces in a row"));
            Matcher sp = Pattern.compile("[א-ת] [,.!?:](?![.0-9])").matcher(v);
            if (sp.find()) out.add(new Finding("space", l, "a space before '" + v.charAt(sp.end() - 1) + "'"));
            String br = bracketError(v);
            if (br != null) out.add(new Finding("bracket", l, br));
            for (Map.Entry<String, String> e : forbidden.entrySet()) {
                if (Pattern.compile("(?<![א-ת])[ובלהמכש]{0,2}" + Pattern.quote(e.getKey()) + "(?![א-ת])").matcher(v).find()) {
                    out.add(new Finding("forbidden", l, "\"" + e.getKey() + "\": " + e.getValue()));
                }
            }
            if (MASC_YOU.matcher(v).find() && !genderMarked(v) && !hasFemininePartner(lits, idx, src)) {
                out.add(new Finding("masc", l, "\"אתה\" with no feminine wording beside it"));
            }
            if (v.length() > 0 && Character.isDigit(v.charAt(0)) && v.length() > 3) out.add(new Finding("digit", l, "starts with a number"));
            if (isGlued(lits, idx, src)) out.add(new Finding("glue", l, "a sentence glued from pieces around a value"));
        }
        Matcher m = T_PAIR.matcher(src);
        while (m.find()) {
            if (m.group(1).equals(m.group(2)) && hasHebrew(m.group(1))) {
                int line = 1;
                for (int i = 0; i < m.start(); i++) if (src.charAt(i) == '\n') line++;
                out.add(new Finding("samepair", new Lit(file, line, m.group(1), m.start(), m.end()), "t(m, f) with the same wording twice"));
            }
        }
        return out;
    }

    /** Words are runs of Hebrew letters; an apostrophe or a quote after the run means an abbreviation. */
    static String finalLetterError(String v) {
        int n = v.length();
        for (int i = 0; i < n; i++) {
            char c = v.charAt(i);
            if (!isHebrewLetter(c)) continue;
            boolean nextIsLetter = i + 1 < n && isHebrewLetter(v.charAt(i + 1));
            char next = i + 1 < n ? v.charAt(i + 1) : ' ';
            boolean abbreviation = next == '\'' || next == '"' || next == '׳' || next == '״';
            if (isFinalForm(c) && nextIsLetter) return "final letter '" + c + "' in the middle of a word";
            boolean prevIsLetter = i > 0 && isHebrewLetter(v.charAt(i - 1));
            // Markup such as "מוכנ[/ה]" or "[/ה]" continues the word; a hyphen or a bracket is not an ending.
            boolean continues = next == '[' || next == '{' || next == '/' || next == '|' || next == ']';
            if ((c == 'מ' || c == 'נ' || c == 'צ') && prevIsLetter && !nextIsLetter && !abbreviation && !continues) {
                return "word ends in '" + c + "' (should be the final form)";
            }
        }
        return null;
    }

    static String bracketError(String v) {
        int sq = 0, cu = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '[') sq++;
            else if (c == ']') sq--;
            else if (c == '{') cu++;
            else if (c == '}') cu--;
            if (sq < 0) return "a ] with no [ before it";
            if (cu < 0) return "a } with no { before it";
            if (sq > 1) return "a [ inside a [";
        }
        if (sq != 0) return "a [ that is never closed";
        if (cu != 0) return "a { that is never closed";
        return null;
    }

    /** True when the line carries its own gender markup: [masc/fem] or [masc|fem]. */
    static boolean genderMarked(String v) {
        return Pattern.compile("\\[[^\\]]*[/|][^\\]]*\\]").matcher(v).find();
    }

    /**
     * A masculine line is fine when its feminine twin follows close by: the next literal after a comma
     * (t(m, f), Beat.say(id, who, m, f)), or one of the next few literals when the two wordings are
     * built in parallel (two arrays, or two concatenations with a value in the middle).
     */
    static boolean hasFemininePartner(List<Lit> lits, int idx, String src) {
        Lit l = lits.get(idx);
        for (int j = idx + 1; j < lits.size() && j <= idx + 12; j++) {
            Lit nx = lits.get(j);
            if (nx.start - l.end > 1500) break;
            if (j == idx + 1 && onlyComma(src, l.end, nx.start) && FEM_YOU.matcher(nx.value).find()) return true;
            if (FEM_YOU.matcher(nx.value).find() && genderTwin(l.value, nx.value)) return true;
        }
        return false;
    }

    static boolean onlyComma(String src, int from, int to) {
        if (from > to || to > src.length()) return false;
        String gap = src.substring(from, to).trim();
        return gap.equals(",");
    }

    /** Two wordings of one line: same length within a few characters and most words shared. */
    static boolean genderTwin(String a, String b) {
        if (Math.abs(a.length() - b.length()) > Math.max(6, a.length() / 5)) return false;
        String[] wa = a.split("\\s+"), wb = b.split("\\s+");
        if (Math.abs(wa.length - wb.length) > 2) return false;
        int same = 0;
        for (String w : wa) for (String x : wb) if (w.equals(x)) { same++; break; }
        return same * 2 >= wa.length && !a.equals(b);
    }

    /** "עברית " + value + " עברית": the literal is followed by + something + another Hebrew literal. */
    static boolean isGlued(List<Lit> lits, int idx, String src) {
        if (idx + 1 >= lits.size()) return false;
        Lit a = lits.get(idx), b = lits.get(idx + 1);
        if (!hasHebrew(b.value) || b.start - a.end > 120 || b.start < a.end) return false;
        String gap = src.substring(a.end, b.start).trim();
        if (!gap.startsWith("+") || !gap.endsWith("+") || gap.length() < 3) return false;
        String mid = gap.substring(1, gap.length() - 1).trim();
        // Money and plain numbers move with the sentence in any language; a value that is a word does not.
        return !mid.isEmpty() && !mid.contains(";") && !mid.contains("\n") && !mid.startsWith("Ui.nis(") && wordCount(a.value) >= 1
                && wordCount(b.value) >= 1;
    }

    static int wordCount(String s) {
        int n = 0;
        for (String w : s.trim().split("\\s+")) if (hasHebrew(w)) n++;
        return n;
    }

    // ------------------------------------------------------------------ driver

    static Map<String, String> forbidden() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Path p = TestEnv.root().resolve("tools/test/text-forbidden.txt");
        if (!Files.isRegularFile(p)) return out;
        for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            String[] f = line.split("\\s*\\|\\s*", 2);
            out.put(f[0].trim(), f.length > 1 ? f[1].trim() : "forbidden word");
        }
        return out;
    }

    /**
     * By-design exceptions (not bugs): tools/test/text-allow.txt, one per line, {@code key | reason}
     * where key is {@code rule:File:fingerprint} as printed with a finding, or {@code rule:File:*}.
     */
    static List<String> allowed() throws IOException {
        List<String> out = new ArrayList<>();
        Path p = TestEnv.root().resolve("tools/test/text-allow.txt");
        if (!Files.isRegularFile(p)) return out;
        for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            out.add(line.split("\\s*\\|\\s*", 2)[0].trim());
        }
        return out;
    }

    static boolean isAllowed(List<String> allow, String key) {
        for (String a : allow) {
            if (a.equals(key) || (a.endsWith("*") && key.startsWith(a.substring(0, a.length() - 1)))) return true;
        }
        return false;
    }

    static List<Finding> lintTree() throws IOException {
        Map<String, String> forbidden = forbidden();
        List<Finding> all = new ArrayList<>();
        for (Path p : TestEnv.coreJavaFiles()) {
            all.addAll(lint(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8), forbidden));
        }
        return all;
    }

    static boolean failing(String rule) {
        for (String r : FAILING) if (r.equals(rule)) return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        boolean verbose = false;
        String list = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-v")) verbose = true;
            else if (args[i].equals("--list")) list = args[++i];
            else throw new IllegalArgumentException("unknown argument " + args[i]);
        }
        int files = 0, literals = 0, hebrew = 0;
        for (Path p : TestEnv.coreJavaFiles()) {
            files++;
            for (Lit l : literals(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8))) {
                literals++;
                if (hasHebrew(l.value)) hebrew++;
            }
        }
        List<Finding> all = lintTree();
        Known known = Known.load("text");
        List<String> allow = allowed();
        int allowedN = 0;
        Map<String, Integer> perRule = new LinkedHashMap<>();
        for (String r : FAILING) perRule.put(r, 0);
        for (String r : LISTED) perRule.put(r, 0);
        int fresh = 0, knownN = 0, listed = 0;
        List<String> newKeys = new ArrayList<>();
        for (Finding f : all) {
            if (isAllowed(allow, f.key())) {
                allowedN++;
                continue;
            }
            perRule.merge(f.rule, 1, Integer::sum);
            if (!failing(f.rule)) {
                listed++;
                if (verbose || f.rule.equals(list)) System.out.println("list   " + f);
                continue;
            }
            String bug = known.bug(f.key());
            if (bug != null) {
                knownN++;
                if (verbose) System.out.println("KNOWN  " + f + "  " + bug);
            } else {
                fresh++;
                newKeys.add(f.rule + " " + f.where);
                System.out.println("FAIL   " + f + "   [key: text " + f.key() + "]");
            }
        }
        System.out.println();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : perRule.entrySet()) if (e.getValue() > 0) sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        System.out.println("TextLint: " + files + " files, " + literals + " string literals, " + hebrew + " Hebrew; findings by rule:"
                + (sb.length() == 0 ? " none" : sb));
        for (String st : known.stale()) System.out.println("note: known issue no longer fails, remove it from known-issues.txt: text " + st);
        if (literals == 0) {
            System.out.println("@@ FAIL | found no string literals: run from the repository root or pass -Dima.root");
            System.exit(1);
        }
        String shortList = newKeys.size() > 6 ? String.join(", ", newKeys.subList(0, 6)) + ", ..." : String.join(", ", newKeys);
        System.out.println("@@ " + (fresh == 0 ? "PASS" : "FAIL") + " | " + hebrew + " Hebrew lines in " + files + " files"
                + (knownN > 0 ? ", " + knownN + " known" : "") + (listed > 0 ? ", " + listed + " listed" : "")
                + (allowedN > 0 ? ", " + allowedN + " allowed by design" : "")
                + (fresh > 0 ? "; NEW " + fresh + ": " + shortList : ""));
        System.exit(fresh == 0 ? 0 : 1);
    }
}
