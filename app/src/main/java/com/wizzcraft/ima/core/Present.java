package com.wizzcraft.ima.core;

/**
 * The presentation kit: colour roles, the type scale, spacing and the handful of drawing helpers that
 * every story view shares (TalkView, PhoneView, JournalView). The rules behind the numbers are in
 * docs/PRESENTATION.md; the colour roles are the ones of docs/UX.md section 3.3.
 *
 * Nothing here allocates while drawing: polygons go through reused buffers, text is wrapped once by
 * {@link Para}. Not thread-safe (the game draws from one thread).
 */
final class Present {
    private Present() {
    }

    /** Logical screen width; the same number as Game.W, repeated so the views stand on their own. */
    static final float W = 360;

    // ---- colour roles (UX.md 3.3) -------------------------------------------------------------
    static final int S0 = 0xFF12161C, S1 = 0xFF1E2530, S2 = 0xFF2C3644, S3 = 0xFF3A4658, TRACK = 0xFF0B0E12;
    static final int T1 = 0xFFE9EDF2, T2 = 0xFFA3ACB9, T3 = 0xFF7D8796;
    static final int GOLD = 0xFFFFC857, GOLD_P = 0xFFE0A93A, ON_GOLD = 0xFF1A1300;
    static final int GOOD = 0xFF5BD68A, DANGER = 0xFFFF7A7A, DESTRUCT = 0xFFC8474A, DESTRUCT_P = 0xFFA93B3E;
    static final int INFO = 0xFF5AA9FF, ENERGY = 0xFFB79CFF;

    // ---- colours this layer adds ---------------------------------------------------------------
    /** The player's own voice: answer traces and outgoing bubbles. T1 on it is 8.1:1. */
    static final int ME = 0xFF34427A, ME_EDGE = 0xFF6F86D6;
    /** An incoming bubble and the speech card. */
    static final int THEM = S2;
    /** Call buttons: white glyph on them is at least 4.5:1. */
    static final int ACCEPT = 0xFF1E8E4E, ACCEPT_P = 0xFF17703D, DECLINE = DESTRUCT, DECLINE_P = DESTRUCT_P;
    /** Paper for letters, notes and the journal; PAPER_INK on PAPER is 12.6:1, PAPER_DIM 5.2:1. */
    static final int PAPER = 0xFFF1E4C8, PAPER_SHADE = 0xFFE2D2AF, PAPER_INK = 0xFF2B2219, PAPER_DIM = 0xFF6B5B45;
    static final int PAPER_RULE = 0x332B2219, PAPER_RED = 0xFFB23A3A;
    /** The phone's body and the glass edge. */
    static final int PHONE_BODY = 0xFF07090C, PHONE_EDGE = 0xFF2A313B;
    static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;

    // ---- type scale (UX.md 3.1 plus two story sizes) -------------------------------------------
    static final float DISPLAY = 44, H1 = 26, H2 = 20, SPEECH = 17, BODY = 16, BUTTON = 16, LABEL = 14, SMALL = 13,
            MICRO = 12;
    /** Line height as a multiple of the size: speech and paragraphs. */
    static final float LEAD = 1.5f;

    // ---- spacing --------------------------------------------------------------------------------
    static final float G1 = 4, G2 = 8, G3 = 12, G4 = 16, G5 = 24, G6 = 32;
    static final float BTN_H = 52, HIT = 48, RADIUS = 12, CARD_R = 18;

    // =============================================================================================
    // Colour maths
    // =============================================================================================

    /** The colour with its alpha multiplied by a (0..1). */
    static int alpha(int argb, float a) {
        int al = (int) (((argb >>> 24) & 0xFF) * (a < 0 ? 0 : a > 1 ? 1 : a));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int a, int b, float k) {
        if (k <= 0) return a;
        if (k >= 1) return b;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * k) << 24) | ((int) (ar + (br - ar) * k) << 16)
                | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    /** WCAG relative luminance of an opaque colour. */
    static float luminance(int argb) {
        return 0.2126f * lin((argb >> 16) & 0xFF) + 0.7152f * lin((argb >> 8) & 0xFF) + 0.0722f * lin(argb & 0xFF);
    }

    private static float lin(int v) {
        float s = v / 255f;
        return s <= 0.03928f ? s / 12.92f : (float) Math.pow((s + 0.055f) / 1.055f, 2.4);
    }

    /** WCAG contrast ratio of two opaque colours (1..21). */
    static float contrast(int a, int b) {
        float la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }

    // =============================================================================================
    // Surfaces
    // =============================================================================================

    /** A vertical gradient drawn as bands about 4 units tall. */
    static void vgrad(Gfx c, float x, float y, float w, float h, int top, int bottom) {
        int n = Math.max(2, Math.min(96, (int) (h / 4)));
        float bh = h / n;
        for (int i = 0; i < n; i++) {
            c.fillRect(x, y + i * bh, w, bh + 0.6f, mix(top, bottom, i / (float) (n - 1)));
        }
    }

    /** A soft drop shadow under a rounded rectangle: four widening, fading layers. */
    static void shadow(Gfx c, float x, float y, float w, float h, float r, float strength) {
        for (int i = 4; i >= 1; i--) {
            float g = i * 2.2f;
            c.fillRoundRect(x - g, y - g + 5, w + 2 * g, h + 2 * g, r + g, alpha(0xFF000000, strength * 0.085f));
        }
    }

    /** A raised card with its shadow and a hairline top light. */
    static void card(Gfx c, float x, float y, float w, float h, float r, int fill, float a) {
        if (a <= 0.01f) return;
        shadow(c, x, y, w, h, r, a);
        c.fillRoundRect(x, y, w, h, r, alpha(fill, a));
        c.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, h - 1, r, 1, alpha(0xFFFFFFFF, 0.07f * a));
    }

    /** A soft round glow: concentric discs, brightest in the middle. */
    static void glow(Gfx c, float cx, float cy, float r, int col, float strength) {
        for (int i = 6; i >= 1; i--) {
            c.fillCircle(cx, cy, r * i / 6f, alpha(col, strength * 0.06f));
        }
    }

    // =============================================================================================
    // Buttons
    // =============================================================================================

    static final int SECONDARY = 0, PRIMARY = 1, QUIET = 2, ANSWER = 3;

    /**
     * A button in the styles of UX.md 3.4. press is 0..1 (0 at rest, 1 fully pressed): the shape
     * shrinks to 97% about its centre, the fill darkens and the label drops one unit. a fades it.
     */
    static void button(Gfx c, float x, float y, float w, float h, String label, int style, float press, float a) {
        if (a <= 0.01f) return;
        float k = 1 - 0.03f * press;
        float bw = w * k, bh = h * k, bx = x + (w - bw) / 2, by = y + (h - bh) / 2;
        int fill, ink;
        switch (style) {
            case PRIMARY -> {
                fill = mix(GOLD, GOLD_P, press);
                ink = ON_GOLD;
            }
            case QUIET -> {
                fill = mix(S1, S2, press);
                ink = T1;
            }
            default -> {
                fill = mix(S2, S3, press);
                ink = T1;
            }
        }
        if (press < 0.5f && style != QUIET) {
            c.fillRoundRect(bx, by + 2.5f, bw, bh, RADIUS, alpha(0xFF000000, 0.28f * a));
        }
        c.fillRoundRect(bx, by, bw, bh, RADIUS, alpha(fill, a));
        if (style == QUIET) c.strokeRoundRect(bx + 0.75f, by + 0.75f, bw - 1.5f, bh - 1.5f, RADIUS, 1.5f, alpha(S3, a));
        else c.fillRoundRect(bx + 6, by + 1, bw - 12, 1, 0.5f, alpha(0xFFFFFFFF, 0.10f * a * (1 - press)));
        if (style == ANSWER) {
            // The answer's own edge: the colour of the player's voice, so a choice looks like something to say.
            c.fillRoundRect(bx + bw - 5, by + 10, 3, bh - 20, 1.5f, alpha(ME_EDGE, a));
        }
        float size = Ui.fit(c, label, bw - 28, BUTTON);
        c.text(label, bx + bw / 2, by + bh / 2 + size * 0.36f + press, size, alpha(ink, a), Gfx.CENTER);
    }

    // =============================================================================================
    // Glyphs (drawn, never typed: no text inside art, and no emoji in the font)
    // =============================================================================================

    private static final float[] Q8 = new float[8], Q6 = new float[6];

    static void tri(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, int col) {
        Q6[0] = x0;
        Q6[1] = y0;
        Q6[2] = x1;
        Q6[3] = y1;
        Q6[4] = x2;
        Q6[5] = y2;
        c.fillPoly(Q6, col);
    }

    static void quad(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int col) {
        Q8[0] = x0;
        Q8[1] = y0;
        Q8[2] = x1;
        Q8[3] = y1;
        Q8[4] = x2;
        Q8[5] = y2;
        Q8[6] = x3;
        Q8[7] = y3;
        c.fillPoly(Q8, col);
    }

    /** A ring as a stroked circle. */
    static void ring(Gfx c, float cx, float cy, float r, float sw, int col) {
        if (r <= 0) return;
        c.strokeRoundRect(cx - r, cy - r, r * 2, r * 2, r, sw, col);
    }

    /**
     * A telephone handset about 2*s wide, centred on (cx, cy). deg rotates it: 0 is the lifted
     * receiver of "answer", 135 the laid-down receiver of "hang up".
     */
    static void handset(Gfx c, float cx, float cy, float s, float deg, int col) {
        double a = Math.toRadians(deg);
        float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
        // The grip is an arch drawn as thick round-capped strokes; each end carries a drooping pad.
        int n = 8;
        float lx = 0, ly = 0;
        for (int i = 0; i < n; i++) {
            double t = Math.toRadians(208 + 124.0 * i / (n - 1));
            float ox = (float) Math.cos(t) * s, oy = (float) Math.sin(t) * s * 0.80f + s * 0.52f;
            float x = cx + ox * cs - oy * sn, y = cy + ox * sn + oy * cs;
            if (i > 0) c.line(lx, ly, x, y, s * 0.34f, col);
            lx = x;
            ly = y;
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 2; k++) {
                float px = side * s * (0.86f - k * 0.10f), py = s * (0.16f + k * 0.26f);
                c.fillCircle(cx + px * cs - py * sn, cy + px * sn + py * cs, s * (0.30f - k * 0.03f), col);
            }
        }
    }

    /** A chevron; dir 0 = down, 1 = left (forward in Hebrew), 2 = right (back), 3 = up. */
    static void chevron(Gfx c, float cx, float cy, float s, int dir, float sw, int col) {
        switch (dir) {
            case 0 -> {
                c.line(cx - s, cy - s * 0.5f, cx, cy + s * 0.5f, sw, col);
                c.line(cx, cy + s * 0.5f, cx + s, cy - s * 0.5f, sw, col);
            }
            case 1 -> {
                c.line(cx + s * 0.5f, cy - s, cx - s * 0.5f, cy, sw, col);
                c.line(cx - s * 0.5f, cy, cx + s * 0.5f, cy + s, sw, col);
            }
            case 2 -> {
                c.line(cx - s * 0.5f, cy - s, cx + s * 0.5f, cy, sw, col);
                c.line(cx + s * 0.5f, cy, cx - s * 0.5f, cy + s, sw, col);
            }
            default -> {
                c.line(cx - s, cy + s * 0.5f, cx, cy - s * 0.5f, sw, col);
                c.line(cx, cy - s * 0.5f, cx + s, cy + s * 0.5f, sw, col);
            }
        }
    }

    /** One or two "delivered" ticks, about 9 wide, with their left end at x. */
    static void ticks(Gfx c, float x, float y, boolean two, int col) {
        c.line(x, y, x + 2.4f, y + 2.6f, 1.3f, col);
        c.line(x + 2.4f, y + 2.6f, x + 7, y - 2.6f, 1.3f, col);
        if (two) {
            c.line(x + 4.6f, y + 1.4f, x + 5.8f, y + 2.6f, 1.3f, col);
            c.line(x + 5.8f, y + 2.6f, x + 10.4f, y - 2.6f, 1.3f, col);
        }
    }

    /** A heart about 2*s wide. */
    static void heart(Gfx c, float cx, float cy, float s, int col) {
        c.fillCircle(cx - s * 0.5f, cy - s * 0.3f, s * 0.56f, col);
        c.fillCircle(cx + s * 0.5f, cy - s * 0.3f, s * 0.56f, col);
        tri(c, cx - s * 1.02f, cy - s * 0.05f, cx + s * 1.02f, cy - s * 0.05f, cx, cy + s * 1.05f, col);
    }

    // =============================================================================================
    // Portraits as avatars
    // =============================================================================================

    private static final float[] MASK = new float[2 * 34];

    /**
     * A round avatar of diameter d: the cast portrait on an opaque disc, cut clean along the lower
     * half of the circle (hats and hair may stand out above it, which gives the head some depth).
     * bg must be the flat colour the avatar sits on; ring != 0 draws a 1.5 unit ring.
     */
    static void avatar(Gfx c, String who, float cx, float cy, float d, float t, int disc, int bg, int ring) {
        float r = d / 2, size = d * 64f / 62f, pcy = cy + 2 * size / 64f;
        c.fillCircle(cx, cy, r, disc);
        PersonArt.portrait(c, who, cx, pcy, size, t);
        // Lower half-annulus in the background colour hides the shoulders that leave the circle.
        int n = 17;
        float ro = r * 1.56f;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * (-0.06 + 1.12 * i / (n - 1));
            MASK[2 * i] = cx + (float) Math.cos(a) * ro;
            MASK[2 * i + 1] = cy + (float) Math.sin(a) * ro;
            int j = 2 * n - 1 - i;
            MASK[2 * j] = cx + (float) Math.cos(a) * r;
            MASK[2 * j + 1] = cy + (float) Math.sin(a) * r;
        }
        c.fillPoly(MASK, bg);
        if (ring != 0) ring(c, cx, cy, r, 1.5f, ring);
    }

    /** The colour a character brings with them: their call screen, their name plate, their thread. */
    static int accentOf(String who) {
        if (who == null) return GOLD;
        return switch (who) {
            case "mom" -> 0xFFFF9C7E;
            case "dad" -> 0xFF7DB4F2;
            case "landlord" -> 0xFFC9B458;
            case "boss" -> 0xFFE8707A;
            case "baker" -> 0xFFE7B45A;
            case "barowner" -> 0xFFC58BE8;
            case "gasman" -> 0xFFFFA24D;
            case "manager" -> 0xFF62D2A2;
            case "neighbour" -> 0xFFB79CE0;
            case "dealer" -> 0xFF9AA4B8;
            case "cop" -> 0xFF7F9CF5;
            case "teacher" -> 0xFFD9B26A;
            default -> GOLD;
        };
    }

    // =============================================================================================
    // The phone in the hand
    // =============================================================================================

    /** Screen rectangle of the phone drawn by the last {@link #phone} call. */
    static float scrX, scrY, scrW, scrH;
    static final float STATUS_H = 26;

    /**
     * The player's phone held up over the world: body, glass, status bar. It fills the width, starts
     * at {@code top + 8 + drop} and runs off the bottom edge, so only its top corners show. The app
     * area starts at scrY + STATUS_H. clock is the game clock ("19:42"); battery 0..1 mirrors energy.
     */
    static void phone(Gfx c, float top, float bottom, float drop, int screen, String clock, float battery) {
        float x = 5, y = top + 8 + drop, w = W - 10, h = bottom - y + 40;
        shadow(c, x, y, w, h, 34, 1.6f);
        c.fillRoundRect(x, y, w, h, 34, PHONE_BODY);
        c.strokeRoundRect(x + 0.75f, y + 0.75f, w - 1.5f, h - 1.5f, 34, 1.5f, PHONE_EDGE);
        scrX = x + 6;
        scrY = y + 6;
        scrW = w - 12;
        scrH = bottom - scrY;
        c.fillRoundRect(scrX, scrY, scrW, scrH + 40, 28, screen);
        // Status bar: clock on the right (the start side), battery and signal on the left.
        float by = scrY + 17;
        if (clock != null) c.text(clock, scrX + scrW - 22, by, MICRO, T1, Gfx.RIGHT);
        float bx = scrX + 22;
        c.strokeRoundRect(bx, by - 9, 20, 10, 2.5f, 1, T2);
        c.fillRoundRect(bx + 20.5f, by - 6, 2, 4, 1, T2);
        float fill = Ui.clamp(battery, 0, 1) * 16;
        if (fill > 1) c.fillRoundRect(bx + 2, by - 7, fill, 6, 1.5f, battery < 0.2f ? DANGER : T1);
        for (int i = 0; i < 4; i++) {
            float bh = 3 + i * 2;
            c.fillRoundRect(bx + 30 + i * 4.5f, by - bh + 1, 3, bh, 1, i < 3 ? T1 : T3);
        }
        c.fillCircle(W / 2, scrY + 13, 4, PHONE_BODY);
        c.fillCircle(W / 2 + 1, scrY + 12, 1.1f, 0xFF1C2A3A);
    }
}
