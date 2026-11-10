package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A condition written as a short string, for example
 * {@code "money<100 & day>=15 & !seen.b1.mazal.03"}. Writers use it instead of code so that the tools
 * can read every condition: which variables it needs, and whether it can ever be true.
 *
 * Grammar: comparisons ({@code < <= > >= = == !=}) of sums ({@code a+b}, {@code a-b}) of numbers and
 * names; joined by {@code &} (also "," or "and") and {@code |} (or "or"); {@code !} or "not" negates;
 * brackets group. A bare name is true when it is not 0. Names are variables, the built-ins listed in
 * {@link #BUILT_IN}, or the word constants in {@link #WORDS} ({@code job=cook}, {@code tie.mazal>=friend}).
 */
final class Cond {
    static final int NUM = 0, VAR = 1, NOT = 2, AND = 3, OR = 4, CMP = 5, ADD = 6, SUB = 7;
    static final int LT = 0, LE = 1, GT = 2, GE = 3, EQ = 4, NE = 5;
    private static final String[] OPS = {"<", "<=", ">", ">=", "=", "!="};

    /** Names that are computed from the player's situation rather than stored. */
    static final String[] BUILT_IN = {"day", "hour", "minute", "month", "phase", "daysleft", "money", "food", "water",
            "energy", "job", "rank", "jobs", "shifts", "rent.due", "rent.debt", "rent.missed", "rent.days", "place",
            "female", "homeless"};
    /** Word constants, so a condition reads like a sentence. A variable may not share a name with one. */
    static final Map<String, Float> WORDS = new HashMap<>();

    static {
        String[] names = {
                // tie stages (computed from rel.<id>)
                "stranger", "0", "known", "1", "friend", "2", "family", "3",
                // ledger states
                "none", "0", "open", "1", "real", "2", "staged", "3", "told", "4", "kept", "5", "broken", "6",
                "sent", "7", "ignored", "8", "paid", "9",
                // jobs (ids from Job and the engine surface)
                "nojob", "-1", "cook", "0", "waiter", "1", "baker", "2", "barman", "3", "gas", "4", "cashier", "5",
                "dishwasher", "6", "exterminator", "7", "pancakes", "8", "pastry", "9", "doctor", "10", "lawyer", "11",
                "developer", "12",
                // places (City ids)
                "street", "-1", "school", "0", "casino", "1", "bakery", "2", "bar", "3", "restaurant", "4",
                "market", "5", "station", "6", "home", "7", "alley", "8",
                // shift results and plain words
                "miss", "0", "ok", "1", "perfect", "2", "yes", "1", "no", "0"};
        for (int i = 0; i < names.length; i += 2) WORDS.put(names[i], Float.parseFloat(names[i + 1]));
    }

    final int type, op;
    final float num;
    final String name;
    /** Index in BUILT_IN, or -1 for a stored variable. */
    final int builtIn;
    final Cond a, b;

    private Cond(int type, int op, float num, String name, Cond a, Cond b) {
        this.type = type;
        this.op = op;
        this.num = num;
        this.name = name;
        this.a = a;
        this.b = b;
        int bi = -1;
        if (type == VAR) for (int i = 0; i < BUILT_IN.length; i++) if (BUILT_IN[i].equals(name)) bi = i;
        builtIn = bi;
    }

    static Cond num(float v) {
        return new Cond(NUM, 0, v, null, null, null);
    }

    static Cond var(String name) {
        return new Cond(VAR, 0, 0, name, null, null);
    }

    static Cond not(Cond c) {
        return new Cond(NOT, 0, 0, null, c, null);
    }

    static Cond and(Cond x, Cond y) {
        return x == null ? y : y == null ? x : new Cond(AND, 0, 0, null, x, y);
    }

    static Cond or(Cond x, Cond y) {
        return new Cond(OR, 0, 0, null, x, y);
    }

    static Cond cmp(Cond x, int op, Cond y) {
        return new Cond(CMP, op, 0, null, x, y);
    }

    /** {@code name op value}, the shape the scene header builds for day ranges and delays. */
    static Cond cmp(String name, int op, float value) {
        return cmp(var(name), op, num(value));
    }

    // ------------------------------------------------------------------ evaluation

    /** What a condition reads from. {@link StoryState} is the real one; the lint has its own. */
    interface Source {
        float var(String key);

        float builtIn(int index);
    }

    boolean test(Source s) {
        switch (type) {
            case NOT:
                return !a.test(s);
            case AND:
                return a.test(s) && b.test(s);
            case OR:
                return a.test(s) || b.test(s);
            case CMP: {
                float x = a.value(s), y = b.value(s);
                // "Never happened" is NaN: every comparison with it is false, so a delay cannot fire early.
                if (Float.isNaN(x) || Float.isNaN(y)) return false;
                switch (op) {
                    case LT:
                        return x < y;
                    case LE:
                        return x <= y;
                    case GT:
                        return x > y;
                    case GE:
                        return x >= y;
                    case EQ:
                        return x == y;
                    default:
                        return x != y;
                }
            }
            default: {
                float v = value(s);
                return v != 0 && !Float.isNaN(v);
            }
        }
    }

    float value(Source s) {
        switch (type) {
            case NUM:
                return num;
            case VAR:
                return builtIn >= 0 ? s.builtIn(builtIn) : s.var(name);
            case ADD:
                return a.value(s) + b.value(s);
            case SUB:
                return a.value(s) - b.value(s);
            default:
                return test(s) ? 1 : 0;
        }
    }

    /** Adds every name this condition reads (variables and built-ins) to the set. */
    void reads(Set<String> out) {
        if (type == VAR) out.add(name);
        if (a != null) a.reads(out);
        if (b != null) b.reads(out);
    }

    @Override
    public String toString() {
        switch (type) {
            case NUM:
                return num == (int) num ? String.valueOf((int) num) : String.valueOf(num);
            case VAR:
                return name;
            case NOT:
                return "!" + (a.type == VAR ? a.toString() : "(" + a + ")");
            case AND:
                return a + " & " + b;
            case OR:
                return "(" + a + " | " + b + ")";
            case CMP:
                return a + OPS[op] + b;
            case ADD:
                return a + "+" + b;
            default:
                return a + "-" + b;
        }
    }

    // ------------------------------------------------------------------ parsing

    /** Parses a condition. An empty string is "always"; a malformed one throws with the reason. */
    static Cond parse(String src) {
        if (src == null || src.trim().isEmpty()) return null;
        Parser p = new Parser(src);
        Cond c = p.or();
        p.skip();
        if (p.i < p.s.length()) throw p.fail("unexpected '" + p.s.charAt(p.i) + "'");
        return c;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException fail(String why) {
            return new IllegalArgumentException("condition \"" + s + "\": " + why + " at " + i);
        }

        void skip() {
            while (i < s.length() && s.charAt(i) == ' ') i++;
        }

        boolean eat(String t) {
            skip();
            if (!s.startsWith(t, i)) return false;
            // A word operator must end at a word boundary: "and" is not the start of "android".
            if (Character.isLetter(t.charAt(0)) && i + t.length() < s.length() && isName(s.charAt(i + t.length()))) {
                return false;
            }
            i += t.length();
            return true;
        }

        Cond or() {
            Cond c = and();
            while (eat("|") || eat("or")) c = Cond.or(c, and());
            return c;
        }

        Cond and() {
            Cond c = unary();
            while (eat("&") || eat(",") || eat("and")) c = Cond.and(c, unary());
            return c;
        }

        Cond unary() {
            skip();
            if (i < s.length() && s.charAt(i) == '!' && !s.startsWith("!=", i)) {
                i++;
                return Cond.not(unary());
            }
            if (eat("not")) return Cond.not(unary());
            if (eat("(")) {
                Cond c = or();
                if (!eat(")")) throw fail("missing ')'");
                return c;
            }
            Cond left = sum();
            int op = -1;
            if (eat("<=")) op = LE;
            else if (eat(">=")) op = GE;
            else if (eat("!=")) op = NE;
            else if (eat("==") || eat("=")) op = EQ;
            else if (eat("<")) op = LT;
            else if (eat(">")) op = GT;
            return op < 0 ? left : cmp(left, op, sum());
        }

        Cond sum() {
            Cond c = term();
            while (true) {
                skip();
                if (i < s.length() && s.charAt(i) == '+') {
                    i++;
                    c = new Cond(ADD, 0, 0, null, c, term());
                } else if (i < s.length() && s.charAt(i) == '-') {
                    i++;
                    c = new Cond(SUB, 0, 0, null, c, term());
                } else {
                    return c;
                }
            }
        }

        Cond term() {
            skip();
            int from = i;
            if (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-')) {
                i++;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                try {
                    return num(Float.parseFloat(s.substring(from, i)));
                } catch (NumberFormatException e) {
                    throw fail("bad number");
                }
            }
            while (i < s.length() && isName(s.charAt(i))) i++;
            if (i == from) throw fail("a name or a number is missing");
            String name = s.substring(from, i);
            if (name.endsWith(".")) throw fail("a name cannot end with a dot");
            Float word = WORDS.get(name);
            return word != null ? num(word) : var(name);
        }
    }

    static boolean isName(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '.';
    }

    /** True for a name that can be a variable key: lowercase letters, digits, '_' and '.', as the engine demands. */
    static boolean validKey(String k) {
        if (k == null || k.isEmpty() || k.length() > 64) return false;
        for (int i = 0; i < k.length(); i++) if (!isName(k.charAt(i))) return false;
        return true;
    }
}
