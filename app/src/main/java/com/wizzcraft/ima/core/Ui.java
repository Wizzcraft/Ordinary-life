package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.List;

/** Shared colors and small drawing helpers. */
final class Ui {
    static final int BG = 0xFF12161C, PANEL = 0xFF1E2530, PANEL2 = 0xFF2C3644, INK = 0xFFE9EDF2,
            DIM = 0xFF8A94A3, ACCENT = 0xFFFFC857, GOOD = 0xFF5BD68A, BAD = 0xFFFF6B6B, BLUE = 0xFF5AA9FF,
            ORANGE = 0xFFFF9F5A, DARK_INK = 0xFF10141A;

    private Ui() {
    }

    static boolean in(float x, float y, float bx, float by, float bw, float bh) {
        return x >= bx && x <= bx + bw && y >= by && y <= by + bh;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static String clock(float minute) {
        int m = ((int) minute) % 1440;
        if (m < 0) m += 1440;
        int hh = m / 60, mm = m % 60;
        return (hh < 10 ? "0" : "") + hh + ":" + (mm < 10 ? "0" : "") + mm;
    }

    static String nis(int v) {
        return v < 0 ? "-₪" + (-v) : "₪" + v;
    }

    static List<String> wrap(Gfx c, String s, float maxW, float size) {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n")) {
            String line = "";
            for (String word : para.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && c.textWidth(candidate, size) > maxW) {
                    out.add(line);
                    line = word;
                } else {
                    line = candidate;
                }
            }
            out.add(line);
        }
        return out;
    }

    /** Draws centered wrapped text starting at baseline y; returns the baseline for the next line. */
    static float paragraph(Gfx c, String s, float cx, float y, float maxW, float size, int col) {
        for (String line : wrap(c, s, maxW, size)) {
            c.text(line, cx, y, size, col, Gfx.CENTER);
            y += size * 1.4f;
        }
        return y;
    }

    static void bar(Gfx c, float x, float y, float w, float h, float frac, int col) {
        c.fillRoundRect(x, y, w, h, h / 2, 0xFF0B0E12);
        float fw = w * clamp(frac, 0, 1);
        if (fw > 0.5f) c.fillRoundRect(x, y, Math.max(fw, h), h, h / 2, col);
    }

    static int darken(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static boolean isDark(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 < 110;
    }

    /** A little polygon person. (x, y) is where the feet stand; walk > 0 animates the legs. */
    static void person(Gfx c, float x, float y, float s, boolean female, float phase, boolean walking, int col) {
        float swing = walking ? (float) Math.sin(phase * 11) * 2.4f * s : 0;
        float bob = walking ? Math.abs((float) Math.sin(phase * 11)) * 0.8f * s : 0;
        int leg = 0xFFD7DDE5;
        c.line(x - 1.8f * s, y - 6 * s, x - 1.8f * s - swing, y, 1.7f * s, leg);
        c.line(x + 1.8f * s, y - 6 * s, x + 1.8f * s + swing, y, 1.7f * s, leg);
        float top = y - 15.5f * s - bob;
        if (female) {
            c.fillPoly(new float[]{x - 2.2f * s, top, x + 2.2f * s, top, x + 6 * s, y - 5 * s, x - 6 * s, y - 5 * s}, col);
        } else {
            c.fillPoly(new float[]{x - 5 * s, top, x + 5 * s, top, x + 3.6f * s, y - 5.5f * s, x - 3.6f * s, y - 5.5f * s}, col);
        }
        c.fillCircle(x, top - 3.4f * s, 3.7f * s, 0xFFF2C9A1);
    }
}
