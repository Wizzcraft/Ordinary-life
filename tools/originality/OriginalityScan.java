import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Originality scanner, the engine behind tools/originality-check.sh (see docs/ORIGINALITY.md).
 *
 * Run as a single-file program (JDK 11+), no build step:
 *   java tools/originality/OriginalityScan.java <treeRoot> <blocklist> <wrapperSums> [label]
 *
 * What it checks, and how hard:
 *   ERROR  a blocklisted title, studio, character or brand in a string literal of the app code, in the
 *          Android resources or manifest, or in a shipping document (docs/listing/, fastlane/, store-listing/)
 *   ERROR  a Latin word of two or more letters inside a player-facing literal of the app code
 *          (a literal that contains Hebrew or ₪, or is passed straight to a text-drawing call)
 *   ERROR  licence headers, copyright lines, SPDX tags, "taken from" notes or web links in app code
 *   ERROR  a dependency or a Gradle plugin other than the Android application plugin; an unknown
 *          gradle-wrapper.jar; a wrapper distribution not downloaded from services.gradle.org
 *   WARN   blocklisted names in internal documents, in code comments, and in the desktop tools
 *
 * A line that carries the marker "originality-ok:" followed by a reason is reported as ACCEPTED instead,
 * so every exception is visible and has a written reason. The exit status is 1 when any ERROR remains.
 */
public class OriginalityScan {

    static final class Term {
        final Pattern p;
        final String category, note, raw;
        /** Common English words (Operation, Sprite, Bit...) count only in text a player or store visitor reads. */
        final boolean playerTextOnly;
        Term(Pattern p, String category, String note, String raw) {
            this.p = p; this.category = category; this.note = note; this.raw = raw;
            this.playerTextOnly = note.contains("only a hit in player text");
        }
    }

    static final List<String> out = new ArrayList<>();
    static int errors, warnings, accepted;
    static final List<Term> terms = new ArrayList<>();
    static Path root;

    static final String HEB = "\\u05D0-\\u05EA";
    static final Pattern HAS_HEBREW = Pattern.compile("[" + HEB + "₪]");
    static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{2,}");
    static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_.]*\\}|%[-0-9.]*[sdfx]|\\\\[ntr\"'\\\\u]");
    /** A literal passed straight to one of these calls is shown to the player even without Hebrew in it. */
    static final Pattern TEXT_CALL = Pattern.compile("(\\.text|\\.toast|\\.say|\\.banner|\\.paragraph|\\.floatText)\\(\\s*$");
    static final Pattern PROVENANCE = Pattern.compile(
            "(?i)(copyright|\\(c\\)\\s*\\d{4}|all rights reserved|licen[cs]ed under|SPDX-License|"
            + "apache license|gnu (general|lesser)|mit license|creative commons|stackoverflow|stack overflow|"
            + "taken from|copied from|adapted from|ported from|based on code|https?://(?!schemas\\.android\\.com))");

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: java OriginalityScan.java <treeRoot> <blocklist> <wrapperSums> [label]");
            System.exit(2);
        }
        root = Paths.get(args[0]).toAbsolutePath().normalize();
        loadTerms(Paths.get(args[1]));
        String label = args.length > 3 ? args[3] : root.toString();

        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(null);
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (rel.startsWith(".git/") || rel.contains("/build/") || rel.startsWith("build/")
                    || rel.startsWith("tools/out/") || rel.startsWith(".gradle/")) continue;
            // The blocklist and the review document name every title on purpose.
            if (rel.startsWith("tools/originality/") || rel.equals("docs/ORIGINALITY.md")) continue;
            scanFile(f, rel);
        }
        checkBuild(Paths.get(args[2]));

        PrintStream o = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.setOut(o);
        System.out.println("== originality check: " + label);
        for (String line : out) System.out.println(line);
        System.out.println("== " + errors + " error(s), " + warnings + " warning(s), " + accepted
                + " accepted exception(s) in " + label);
        System.exit(errors > 0 ? 1 : 0);
    }

    // ---------------------------------------------------------------- blocklist

    /**
     * Lines: kind | pattern | category | note (fields separated by a pipe with spaces around it).
     * kind = en (Latin name), he (Hebrew word or phrase), re (raw Java regex, case-insensitive).
     */
    static void loadTerms(Path list) throws IOException {
        int n = 0;
        for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
            n++;
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\\s+\\|\\s+", 4);
            if (c.length < 3) {
                System.err.println("blocklist line " + n + " needs: kind | pattern | category [| note]");
                System.exit(2);
            }
            String kind = c[0].trim(), pat = c[1].trim(), cat = c[2].trim(), note = c.length > 3 ? c[3].trim() : "";
            String rx;
            switch (kind) {
                case "en":
                    rx = "(?<![A-Za-z])" + loosen(pat) + "(?![A-Za-z])";
                    break;
                case "he":
                    // Hebrew glues prefixes (ו ה ב כ ל מ ש ד) to the word; allow up to two of them.
                    rx = "(?<![" + HEB + "])[ובכלמשהד]{0,2}" + loosen(pat) + "(?![" + HEB + "])";
                    break;
                case "re":
                    rx = pat;
                    break;
                default:
                    System.err.println("blocklist line " + n + ": unknown kind " + kind);
                    System.exit(2);
                    return;
            }
            terms.add(new Term(Pattern.compile(rx, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), cat, note, pat));
        }
    }

    /** Spaces, hyphens and apostrophes in a listed name match any of each other, or nothing. */
    static String loosen(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == ' ' || ch == '-' || ch == '\'' || ch == '’' || ch == '׳' || ch == ':' || ch == '.') {
                b.append("[\\s\\-'’׳\":.־]*");
            } else {
                b.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- files

    static void scanFile(Path f, String rel) throws IOException {
        String name = rel.toLowerCase(Locale.ROOT);
        boolean text = name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".xml") || name.endsWith(".md")
                || name.endsWith(".txt") || name.endsWith(".gradle") || name.endsWith(".properties")
                || name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".html");
        if (!text) return;
        String src;
        try {
            src = Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            src = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
        }
        boolean app = rel.startsWith("app/src/main/");
        boolean shipping = rel.startsWith("docs/listing/") || rel.startsWith("fastlane/") || rel.startsWith("store-listing/");

        if (app && (name.endsWith(".java") || name.endsWith(".kt"))) {
            scanCode(src, rel, true);
        } else if (app && name.endsWith(".xml")) {
            scanResources(src, rel);
        } else if (shipping) {
            scanPlain(src, rel, true, "shipping text");
        } else if (rel.startsWith("tools/") && name.endsWith(".java")) {
            scanCode(src, rel, false);
        } else if (name.endsWith(".md") || rel.startsWith("docs/")) {
            scanPlain(src, rel, false, "internal doc");
        }
    }

    static void scanCode(String src, String rel, boolean app) {
        String[] lines = src.split("\n", -1);
        boolean english = false;
        for (int i = 0; i < Math.min(5, lines.length); i++) english |= lines[i].contains("originality: english-text");
        for (Piece p : pieces(src)) {
            String lineText = p.line - 1 < lines.length ? lines[p.line - 1] : "";
            boolean ok = lineText.contains("originality-ok:");
            if (p.comment) {
                hitTerms(p.text, rel, p.line, "WARN", ok, "comment", NOTE);
                if (app) provenance(p.text, rel, p.line, ok);
                else provenanceWarn(p.text, rel, p.line);
                continue;
            }
            String before = lineText.substring(0, Math.max(0, Math.min(lineText.length(), p.col)));
            boolean facing = app && (english || HAS_HEBREW.matcher(p.text).find() || TEXT_CALL.matcher(before).find());
            hitTerms(p.text, rel, p.line, app ? "ERROR" : "WARN", ok, app ? "string" : "tool string",
                    facing ? PLAYER : CODE);
            if (!facing || english) continue;
            Matcher m = LATIN_WORD.matcher(PLACEHOLDER.matcher(p.text).replaceAll(" "));
            if (m.find()) report(ok ? "ACCEPTED" : "ERROR", rel, p.line, "latin",
                    "Latin word \"" + m.group() + "\" in player-facing text", p.text);
        }
    }

    static void scanResources(String src, String rel) {
        String[] lines = src.split("\n", -1);
        Pattern shown = Pattern.compile("android:(label|description|title)=\"([^\"]*)\"|<string[^>]*>(.*?)</string>|<item[^>]*>(.*?)</item>");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = shown.matcher(lines[i]);
            boolean ok = lines[i].contains("originality-ok:");
            while (m.find()) {
                String t = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
                if (t == null || t.startsWith("@")) continue;
                hitTerms(t, rel, i + 1, "ERROR", ok, "resource", PLAYER);
            }
            provenance(lines[i], rel, i + 1, ok);
        }
    }

    static void scanPlain(String src, String rel, boolean shipping, String where) {
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            boolean ok = lines[i].contains("originality-ok:");
            hitTerms(lines[i], rel, i + 1, shipping ? "ERROR" : "WARN", ok, where, shipping ? PLAYER : NOTE);
        }
    }

    /**
     * mode: PLAYER = text players or store visitors read (every entry counts); CODE = identifiers and keys
     * (common-word entries skipped); NOTE = comments and internal docs (only games, studios, characters and
     * brands: "Android" in a comment is not a finding, "Diner Dash" is).
     */
    static final int PLAYER = 0, CODE = 1, NOTE = 2;

    static void hitTerms(String text, String rel, int line, String level, boolean ok, String where, int mode) {
        List<int[]> spans = new ArrayList<>(); // one report per name, however many entries spell it
        for (Term t : terms) {
            if (mode != PLAYER && t.playerTextOnly) continue;
            if (mode == NOTE && t.category.equals("platform")) continue;
            Matcher m = t.p.matcher(text);
            if (!m.find()) continue;
            boolean overlap = false;
            for (int[] sp : spans) overlap |= m.start() < sp[1] && sp[0] < m.end();
            if (!overlap) {
                spans.add(new int[] {m.start(), m.end()});
                String lvl = ok ? "ACCEPTED" : level;
                report(lvl, rel, line, t.category, "\"" + m.group().trim() + "\" (" + where + ")"
                        + (t.note.isEmpty() ? "" : ": " + t.note), text);
            }
        }
    }

    static void provenance(String text, String rel, int line, boolean ok) {
        Matcher m = PROVENANCE.matcher(text);
        if (m.find()) report(ok ? "ACCEPTED" : "ERROR", rel, line, "provenance",
                "\"" + m.group() + "\": licence, attribution or link in app code; was this pasted?", text);
    }

    static void provenanceWarn(String text, String rel, int line) {
        Matcher m = PROVENANCE.matcher(text);
        if (m.find()) report("WARN", rel, line, "provenance", "\"" + m.group() + "\" in a tool", text);
    }

    static void report(String level, String rel, int line, String cat, String msg, String context) {
        if (level.equals("ERROR")) errors++;
        else if (level.equals("WARN")) warnings++;
        else accepted++;
        String ctx = context.replace('\n', ' ').replace('\t', ' ').trim();
        if (ctx.length() > 110) ctx = ctx.substring(0, 107) + "...";
        out.add(String.format("%-8s %s:%d [%s] %s | %s", level, rel, line, cat, msg, ctx));
    }

    // ---------------------------------------------------------------- build and wrapper

    static void checkBuild(Path sumsFile) throws Exception {
        Pattern dep = Pattern.compile("^\\s*(implementation|api|compileOnly|runtimeOnly|testImplementation|"
                + "androidTestImplementation|kapt|ksp|annotationProcessor|classpath)\\b");
        Pattern plugin = Pattern.compile("id\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
        List<Path> gradleFiles = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root, 3)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".gradle") || n.endsWith(".gradle.kts");
            }).forEach(gradleFiles::add);
        }
        for (Path g : gradleFiles) {
            String rel = root.relativize(g).toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(g, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                if (dep.matcher(l).find()) report("ERROR", rel, i + 1, "dependency",
                        "a library or build dependency; the project allows none", l);
                Matcher m = plugin.matcher(l);
                while (m.find()) {
                    if (!m.group(1).equals("com.android.application")) report("ERROR", rel, i + 1, "dependency",
                            "plugin " + m.group(1) + " (only com.android.application is allowed)", l);
                }
            }
        }
        Path props = root.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (Files.exists(props)) {
            for (String l : Files.readAllLines(props, StandardCharsets.UTF_8)) {
                if (l.startsWith("distributionUrl") && !l.contains("services.gradle.org/distributions/gradle-")) {
                    report("ERROR", "gradle/wrapper/gradle-wrapper.properties", 0, "wrapper",
                            "distribution not from services.gradle.org", l);
                }
            }
        }
        Path jar = root.resolve("gradle/wrapper/gradle-wrapper.jar");
        if (Files.exists(jar)) {
            String sum = sha256(jar);
            Set<String> known = new HashSet<>();
            for (String l : Files.readAllLines(sumsFile, StandardCharsets.UTF_8)) {
                if (!l.isBlank() && !l.startsWith("#")) known.add(l.trim().split("\\s+")[0].toLowerCase(Locale.ROOT));
            }
            if (!known.contains(sum)) report("ERROR", "gradle/wrapper/gradle-wrapper.jar", 0, "wrapper",
                    "sha256 " + sum + " is not a published Gradle wrapper checksum in tools/originality/wrapper-sha256.txt", "");
        }
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(Files.readAllBytes(p));
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }

    // ---------------------------------------------------------------- Java lexer (strings and comments)

    static final class Piece {
        final int line, col;
        final String text;
        final boolean comment;
        Piece(int line, int col, String text, boolean comment) {
            this.line = line; this.col = col; this.text = text; this.comment = comment;
        }
    }

    /** String literals (with the line and column they start on) and comments, in source order. */
    static List<Piece> pieces(String s) {
        List<Piece> r = new ArrayList<>();
        int i = 0, n = s.length(), line = 1, lineStart = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') { line++; lineStart = i + 1; i++; continue; }
            if (s.startsWith("//", i)) {
                int j = s.indexOf('\n', i);
                if (j < 0) j = n;
                r.add(new Piece(line, i - lineStart, s.substring(i + 2, j), true));
                i = j;
                continue;
            }
            if (s.startsWith("/*", i)) {
                int j = s.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                String body = s.substring(i, j);
                String[] parts = body.split("\n", -1);
                for (int k = 0; k < parts.length; k++) r.add(new Piece(line + k, 0, parts[k], true));
                for (int k = i; k < j; k++) if (s.charAt(k) == '\n') { line++; lineStart = k + 1; }
                i = j;
                continue;
            }
            if (s.startsWith("\"\"\"", i)) {
                int j = s.indexOf("\"\"\"", i + 3);
                if (j < 0) j = n;
                String body = s.substring(i + 3, j);
                String[] parts = body.split("\n", -1);
                for (int k = 0; k < parts.length; k++) r.add(new Piece(line + k, 0, parts[k], false));
                for (int k = i; k < j; k++) if (s.charAt(k) == '\n') { line++; lineStart = k + 1; }
                i = Math.min(n, j + 3);
                continue;
            }
            if (c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != '\'' && s.charAt(j) != '\n') j += s.charAt(j) == '\\' ? 2 : 1;
                i = j + 1;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                StringBuilder b = new StringBuilder();
                while (j < n && s.charAt(j) != '"' && s.charAt(j) != '\n') {
                    if (s.charAt(j) == '\\' && j + 1 < n) { b.append(s, j, j + 2); j += 2; }
                    else { b.append(s.charAt(j)); j++; }
                }
                r.add(new Piece(line, i - lineStart, b.toString(), false));
                i = j + 1;
                continue;
            }
            i++;
        }
        return r;
    }
}
