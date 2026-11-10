package com.wizzcraft.ima.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The ratchet. {@code tools/test/known-issues.txt} lists failures the studio has accepted for now, each
 * with a bug id from {@code room/threads/bugs.md}. A listed failure does not turn the gate red, it is
 * counted on the suite's last line; a failure that is not listed does. So the gate is green on the
 * tree as it is and red on anything new, and "zero known bugs" is simply: this file has no entries
 * ({@code tools/check.sh --release} enforces that).
 *
 * <p>Line format: {@code <suite> <key> <bug id> [free text]}; '#' starts a comment. A key may end in
 * {@code *} to cover a family (use sparingly). Suites: {@code unit} (key = Class.testMethod),
 * {@code minis} (key = Mini.check), {@code text} (key = rule:File:fingerprint), {@code checker} (key = class).
 */
final class Known {
    final String suite;
    private final Map<String, String> exact = new LinkedHashMap<>();
    private final List<String[]> prefixes = new ArrayList<>();
    private final Set<String> hit = new TreeSet<>();
    final boolean release;

    private Known(String suite, boolean release) {
        this.suite = suite;
        this.release = release;
    }

    static Path file() {
        return TestEnv.root().resolve("tools/test/known-issues.txt");
    }

    /** Loads the entries of one suite. In release mode nothing is known: every failure counts. */
    static Known load(String suite) {
        boolean release = Boolean.getBoolean("ima.release");
        Known k = new Known(suite, release);
        Path p = file();
        if (release || !Files.isRegularFile(p)) return k;
        try {
            for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                int hash = raw.indexOf('#');
                String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
                if (line.isEmpty()) continue;
                String[] f = line.split("\\s+", 4);
                if (f.length < 3 || !f[0].equals(suite)) continue;
                if (f[1].endsWith("*")) k.prefixes.add(new String[]{f[1].substring(0, f[1].length() - 1), f[2]});
                else k.exact.put(f[1], f[2]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p + ": " + e);
        }
        return k;
    }

    /** The bug id under which this failure is accepted, or null when it is new. */
    String bug(String key) {
        String b = exact.get(key);
        if (b != null) {
            hit.add(key);
            return b;
        }
        for (String[] p : prefixes) {
            if (key.startsWith(p[0])) {
                hit.add(p[0] + "*");
                return p[1];
            }
        }
        return null;
    }

    /** Entries that matched no failure in this run: probably fixed, and due for removal from the file. */
    List<String> stale() {
        List<String> out = new ArrayList<>();
        for (String k : exact.keySet()) if (!hit.contains(k)) out.add(k + " (" + exact.get(k) + ")");
        for (String[] p : prefixes) if (!hit.contains(p[0] + "*")) out.add(p[0] + "* (" + p[1] + ")");
        return out;
    }

    int size() {
        return exact.size() + prefixes.size();
    }

    /** Total number of entries in the file, all suites; used by the release check. */
    static int totalEntries() {
        Path p = file();
        if (!Files.isRegularFile(p)) return 0;
        int n = 0;
        try {
            for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                int hash = raw.indexOf('#');
                String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
                if (!line.isEmpty()) n++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p + ": " + e);
        }
        return n;
    }
}
