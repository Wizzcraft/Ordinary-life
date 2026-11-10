package com.wizzcraft.ima.core;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A tiny test runner and assertion kit (no JUnit here, and nothing can be installed).
 *
 * <p>How a test is found: any top-level class in package {@code com.wizzcraft.ima.core} whose name ends in
 * {@code Test} and sits in the classes directory is a test class (so adding a test is adding one file, with
 * no shared list to edit and therefore no merge conflicts). Every {@code static void test*()} method in it
 * is a test. Classes that do not follow the naming convention can be listed, one name per line, in
 * {@code tools/test/tests.list} (optional file; '#' starts a comment).
 *
 * <p>A test class that cannot run in parallel declares {@code static final boolean SERIAL = true;}.
 *
 * <p>Usage: {@code java -cp OUT com.wizzcraft.ima.core.T [--filter a,b] [--classes dir] [--list file]
 * [--threads n] [-v]}. The filter is a case-insensitive substring of {@code ClassName.testMethod}.
 * Exit code: 0 all passed, 1 a test failed, 2 nothing matched.
 */
public final class T {
    private T() {
    }

    // ---------------------------------------------------------------- assertions

    /** Something a test body may throw; used by {@link #fails}. */
    public interface Body {
        void run() throws Throwable;
    }

    private static String pre(String msg) {
        return msg == null || msg.isEmpty() ? "" : msg + ": ";
    }

    private static String show(Object o) {
        if (o == null) return "null";
        if (o.getClass().isArray()) {
            if (o instanceof Object[]) return Arrays.deepToString((Object[]) o);
            if (o instanceof int[]) return Arrays.toString((int[]) o);
            if (o instanceof long[]) return Arrays.toString((long[]) o);
            if (o instanceof float[]) return Arrays.toString((float[]) o);
            if (o instanceof double[]) return Arrays.toString((double[]) o);
            if (o instanceof boolean[]) return Arrays.toString((boolean[]) o);
            if (o instanceof char[]) return Arrays.toString((char[]) o);
            if (o instanceof byte[]) return Arrays.toString((byte[]) o);
        }
        if (o instanceof CharSequence) return "\"" + o + "\"";
        return String.valueOf(o);
    }

    public static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void isTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void isFalse(boolean cond, String msg) {
        if (cond) throw new AssertionError(msg);
    }

    public static void eq(long expected, long actual, String msg) {
        if (expected != actual) throw new AssertionError(pre(msg) + "expected " + expected + " but was " + actual);
    }

    public static void eq(long expected, long actual) {
        eq(expected, actual, "");
    }

    public static void eq(Object expected, Object actual, String msg) {
        if (!Objects.deepEquals(expected, actual)) {
            throw new AssertionError(pre(msg) + "expected " + show(expected) + " but was " + show(actual));
        }
    }

    public static void eq(Object expected, Object actual) {
        eq(expected, actual, "");
    }

    public static void near(double expected, double actual, double tol, String msg) {
        if (!(Math.abs(expected - actual) <= tol)) {
            throw new AssertionError(pre(msg) + "expected " + expected + " +/- " + tol + " but was " + actual
                    + " (off by " + Math.abs(expected - actual) + ")");
        }
    }

    public static void near(double expected, double actual, double tol) {
        near(expected, actual, tol, "");
    }

    /** Passes only when {@code body} throws an instance of {@code type}; returns the exception. */
    public static <E extends Throwable> E fails(Class<E> type, Body body, String msg) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError(pre(msg) + "expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError(pre(msg) + "expected " + type.getSimpleName() + " but nothing was thrown");
    }

    public static <E extends Throwable> E fails(Class<E> type, Body body) {
        return fails(type, body, "");
    }

    // ---------------------------------------------------------------- warnings

    private static final ThreadLocal<String> CURRENT = ThreadLocal.withInitial(() -> "?");
    private static final List<String[]> WARNINGS = new ArrayList<>();

    /** Records a warning (shown after the results, never fails the run). */
    public static void warn(String msg) {
        synchronized (WARNINGS) {
            WARNINGS.add(new String[]{CURRENT.get(), msg});
        }
    }

    // ---------------------------------------------------------------- runner

    private static final class Case {
        final Class<?> cls;
        final Method method;
        String status = "PASS";
        Throwable error;
        long ms;

        Case(Class<?> cls, Method method) {
            this.cls = cls;
            this.method = method;
        }

        String id() {
            return cls.getSimpleName() + "." + method.getName();
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        String filter = null, classes = null, list = "tools/test/tests.list";
        int threads = Runtime.getRuntime().availableProcessors();
        boolean verbose = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--filter": filter = args[++i]; break;
                case "--classes": classes = args[++i]; break;
                case "--list": list = args[++i]; break;
                case "--threads": threads = Math.max(1, Integer.parseInt(args[++i])); break;
                case "-v": case "--verbose": verbose = true; break;
                default: throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        Path root = classes != null ? Path.of(classes) : codeRoot();
        List<String> names = discover(root, list);
        List<Case> cases = new ArrayList<>();
        List<Case> serial = new ArrayList<>();
        List<String> loadErrors = new ArrayList<>();
        String[] filters = filter == null ? new String[0] : filter.toLowerCase(Locale.ROOT).split(",");
        for (String n : names) {
            Class<?> cls;
            try {
                cls = Class.forName(n, false, T.class.getClassLoader());
            } catch (Throwable t) {
                loadErrors.add(n + ": " + t);
                continue;
            }
            if (Modifier.isAbstract(cls.getModifiers())) continue;
            boolean isSerial = false;
            try {
                isSerial = cls.getDeclaredField("SERIAL").getBoolean(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // no SERIAL flag: parallel is fine
            }
            Method[] ms = cls.getDeclaredMethods();
            Arrays.sort(ms, (a, b) -> a.getName().compareTo(b.getName()));
            for (Method m : ms) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0 || m.getReturnType() != void.class
                        || !Modifier.isStatic(m.getModifiers())) continue;
                Case c = new Case(cls, m);
                if (filters.length > 0) {
                    String id = c.id().toLowerCase(Locale.ROOT);
                    boolean hit = false;
                    for (String f : filters) hit |= !f.isEmpty() && id.contains(f);
                    if (!hit) continue;
                }
                m.setAccessible(true);
                (isSerial ? serial : cases).add(c);
            }
        }
        for (String e : loadErrors) System.out.println("ERROR  cannot load test class " + e);
        List<Case> all = new ArrayList<>(cases);
        all.addAll(serial);
        if (all.isEmpty() && loadErrors.isEmpty()) {
            System.out.println("no tests matched" + (filter != null ? " filter '" + filter + "'" : ""));
            System.exit(2);
        }
        long t0 = System.nanoTime();
        int used = Math.min(threads, Math.max(1, cases.size()));
        ExecutorService pool = Executors.newFixedThreadPool(used, r -> {
            Thread t = new Thread(r, "t-worker");
            t.setDaemon(true);
            return t;
        });
        List<Future<?>> fs = new ArrayList<>();
        for (Case c : cases) fs.add(pool.submit(() -> run(c)));
        for (int i = 0; i < fs.size(); i++) {
            try {
                fs.get(i).get(10, TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                cases.get(i).status = "FAIL";
                cases.get(i).error = new AssertionError("timed out after 10 minutes (still running)");
            }
        }
        pool.shutdown();
        for (Case c : serial) run(c);

        Known known = Known.load("unit");
        int pass = 0, fail = loadErrors.size(), knownFails = 0;
        List<String> newFails = new ArrayList<>();
        for (String e : loadErrors) newFails.add("cannot load " + e);
        for (Case c : all) {
            boolean ok = c.status.equals("PASS");
            String bug = ok ? null : known.bug(c.id());
            if (ok) pass++;
            else if (bug != null) knownFails++;
            else {
                fail++;
                newFails.add(c.id());
            }
            System.out.printf("%-5s  %-48s %6d ms%s%n", bug != null ? "KNOWN" : c.status, c.id(), c.ms, bug != null ? "  " + bug : "");
            if (!ok) System.out.println(indent(describe(c.error)));
        }
        synchronized (WARNINGS) {
            if (!WARNINGS.isEmpty()) {
                System.out.println();
                System.out.println("warnings (" + WARNINGS.size() + ", these never fail the run):");
                int shown = 0;
                for (String[] w : WARNINGS) {
                    if (!verbose && shown >= 30) break;
                    System.out.println("  [" + w[0] + "] " + w[1]);
                    shown++;
                }
                if (shown < WARNINGS.size()) {
                    System.out.println("  ... " + (WARNINGS.size() - shown) + " more (run with -v)");
                }
            }
        }
        System.out.printf("%n%s: %d passed, %d failed, %d known, %d total, %d ms wall (%d thread%s)%n",
                fail == 0 ? "TESTS OK" : "TESTS FAILED", pass, fail, knownFails, all.size() + loadErrors.size(),
                (System.nanoTime() - t0) / 1_000_000, used, used == 1 ? "" : "s");
        for (String st : known.stale()) System.out.println("note: known issue no longer fails, remove it from known-issues.txt: unit " + st);
        // The one line tools/check.sh shows for this suite.
        System.out.println("@@ " + (fail == 0 ? "PASS" : "FAIL") + " | " + pass + " of " + (all.size() + loadErrors.size()) + " tests pass"
                + (knownFails > 0 ? ", " + knownFails + " known" : "") + (fail > 0 ? "; NEW: " + String.join(", ", newFails) : ""));
        System.out.flush();
        System.exit(fail == 0 ? 0 : 1);
    }

    private static void run(Case c) {
        CURRENT.set(c.id());
        long t0 = System.nanoTime();
        try {
            c.method.invoke(null);
        } catch (InvocationTargetException e) {
            c.error = e.getCause();
            c.status = e.getCause() instanceof AssertionError ? "FAIL" : "ERROR";
        } catch (Throwable t) {
            c.error = t;
            c.status = "ERROR";
        }
        c.ms = (System.nanoTime() - t0) / 1_000_000;
    }

    private static Path codeRoot() throws Exception {
        return Path.of(T.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static List<String> discover(Path root, String listFile) throws Exception {
        List<String> names = new ArrayList<>();
        Path pkg = root.resolve("com/wizzcraft/ima/core");
        if (Files.isDirectory(pkg)) {
            try (Stream<Path> s = Files.list(pkg)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith("Test.class") && !n.contains("$"))
                        .sorted()
                        .forEach(n -> names.add("com.wizzcraft.ima.core." + n.substring(0, n.length() - 6)));
            }
        }
        File lf = new File(listFile);
        if (lf.isFile()) {
            for (String line : Files.readAllLines(lf.toPath())) {
                int hash = line.indexOf('#');
                String n = (hash >= 0 ? line.substring(0, hash) : line).trim();
                if (n.isEmpty()) continue;
                if (!n.contains(".")) n = "com.wizzcraft.ima.core." + n;
                if (!names.contains(n)) names.add(n);
            }
        }
        return names;
    }

    /** The message and a short stack trace that starts at the test's own code. */
    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Throwable e = t; e != null && sb.length() < 4000; e = e.getCause() == e ? null : e.getCause()) {
            if (!first) sb.append("Caused by: ");
            String msg = e.getMessage();
            boolean plain = e.getClass() == AssertionError.class;
            sb.append(plain ? "" : e.getClass().getName() + (msg == null ? "" : ": ")).append(msg == null ? "" : msg).append('\n');
            int shown = 0;
            for (StackTraceElement el : e.getStackTrace()) {
                String c = el.getClassName();
                if (c.equals(T.class.getName()) || c.startsWith("java.lang.reflect.") || c.startsWith("jdk.internal.reflect.")
                        || c.startsWith("java.util.concurrent.") || c.equals("java.lang.Thread")
                        || c.startsWith("java.lang.invoke.")) continue;
                sb.append("    at ").append(el).append('\n');
                if (++shown >= 7) {
                    sb.append("    ...\n");
                    break;
                }
            }
            first = false;
        }
        return sb.toString().stripTrailing();
    }

    private static String indent(String s) {
        return "       " + s.replace("\n", "\n       ");
    }
}
