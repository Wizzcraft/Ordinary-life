package com.wizzcraft.ima.core;

import java.util.List;

/**
 * A paragraph wrapped once and drawn many times: right-aligned Hebrew by default, with an optional
 * typewriter reveal. All the work (wrapping, the table of prefixes the reveal needs) happens in
 * {@link #set}; {@link #draw} allocates nothing.
 *
 * The reveal moves in "stops". A stop is normally one character, but a run of digits, currency and
 * Latin letters is one stop, so a price or a clock time never appears half-written (a half-typed
 * left-to-right run would jump around inside a right-to-left line).
 */
final class Para {
    static final int MAX_LINES = 14;

    final String[] line = new String[MAX_LINES];
    /** Width of each line, for centred paragraphs. */
    final float[] lineW = new float[MAX_LINES];
    /** cut[i][k] = line i up to and including its stop k. Only for typed paragraphs. */
    private final String[][] cut = new String[MAX_LINES][];
    /** first[i] = index of the first stop of line i; first[n] = stops. */
    private final int[] first = new int[MAX_LINES + 1];
    /** when[k] = seconds after the start at which stop k appears. */
    private float[] when = new float[256];
    /** quiet[k] = the stop is a space or punctuation (no tick sound). */
    private boolean[] quiet = new boolean[256];

    int n, stops;
    float size, lead, width;
    private String src;
    private float srcW, srcSize;
    private boolean srcTyped;

    /** True if the paragraph already holds exactly this text at this width and size. */
    boolean is(String s, float maxW, float size, boolean typed) {
        return s != null && s.equals(src) && maxW == srcW && size == srcSize && typed == srcTyped;
    }

    /** Wraps s into lines of at most maxW. Call when the text changes, never per frame. */
    void set(Gfx c, String s, float maxW, float size, boolean typed) {
        if (is(s, maxW, size, typed)) return;
        src = s;
        srcW = maxW;
        srcSize = size;
        srcTyped = typed;
        this.size = size;
        this.lead = size * Present.LEAD;
        List<String> ls = Ui.wrap(c, s == null ? "" : s, maxW, size);
        n = Math.min(ls.size(), MAX_LINES);
        width = 0;
        stops = 0;
        float at = 0;
        for (int i = 0; i < n; i++) {
            String l = ls.get(i);
            line[i] = l;
            lineW[i] = c.textWidth(l, size);
            width = Math.max(width, lineW[i]);
            first[i] = stops;
            if (!typed) {
                cut[i] = null;
                continue;
            }
            int len = l.length(), count = 0;
            // Pass 1: count the stops of this line.
            for (int p = 0; p < len; ) {
                p = nextStop(l, p);
                count++;
            }
            String[] cs = new String[count];
            ensure(stops + count);
            int k = 0;
            for (int p = 0; p < len; ) {
                int q = nextStop(l, p);
                cs[k] = q >= len ? l : l.substring(0, q);
                char last = l.charAt(q - 1);
                when[stops + k] = at;
                quiet[stops + k] = last == ' ' || isStopMark(last) || last == ',';
                at += 1f / Motion.TYPE_CPS;
                if (isStopMark(last) && (q >= len || l.charAt(q) == ' ')) at += Motion.PAUSE_STOP;
                else if (last == ',' || last == ':' || last == ';') at += Motion.PAUSE_COMMA;
                p = q;
                k++;
            }
            cut[i] = cs;
            stops += count;
        }
        first[n] = stops;
    }

    private static boolean isStopMark(char ch) {
        return ch == '.' || ch == '?' || ch == '!' || ch == '…';
    }

    private static boolean ltr(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '₪'
                || ch == '%' || ch == '+' || ch == '-';
    }

    /** End (exclusive) of the stop that starts at p. */
    private static int nextStop(String l, int p) {
        int len = l.length();
        if (!ltr(l.charAt(p))) return p + 1;
        int q = p + 1;
        while (q < len) {
            char ch = l.charAt(q);
            boolean joiner = (ch == ':' || ch == '.' || ch == ',') && q + 1 < len && ltr(l.charAt(q + 1));
            if (!ltr(ch) && !joiner) break;
            q++;
        }
        return q;
    }

    private void ensure(int need) {
        if (need <= when.length) return;
        int cap = Math.max(need, when.length * 2);
        when = java.util.Arrays.copyOf(when, cap);
        quiet = java.util.Arrays.copyOf(quiet, cap);
    }

    float height() {
        return n * lead;
    }

    /** Seconds the whole reveal takes. */
    float duration() {
        return stops == 0 ? 0 : when[stops - 1] + 0.05f;
    }

    /** How many stops are visible after {@code seconds}. */
    int stopsAt(float seconds) {
        int k = 0;
        while (k < stops && when[k] <= seconds) k++;
        return k;
    }

    boolean quietStop(int k) {
        return k < 0 || k >= stops || quiet[k];
    }

    /**
     * Draws the lines with the first baseline at y. x is the anchor for align (Gfx.RIGHT: the right
     * edge). shown = how many stops are visible; pass a value >= stops (or use a paragraph that is
     * not typed) for all of it.
     */
    void draw(Gfx c, float x, float y, int col, int align, int shown) {
        for (int i = 0; i < n; i++) {
            float by = y + i * lead;
            if (cut[i] == null || shown >= first[i + 1]) {
                c.text(line[i], x, by, size, col, align);
            } else if (shown > first[i]) {
                // A growing centred line would slide about: pin its right end where the full line will have it.
                String part = cut[i][shown - first[i] - 1];
                if (align == Gfx.CENTER) c.text(part, x + lineW[i] / 2, by, size, col, Gfx.RIGHT);
                else c.text(part, x, by, size, col, align);
                return;
            } else {
                return;
            }
        }
    }

    void draw(Gfx c, float x, float y, int col, int align) {
        draw(c, x, y, col, align, Integer.MAX_VALUE);
    }
}
