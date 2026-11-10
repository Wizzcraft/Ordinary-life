package com.wizzcraft.ima.core;

/**
 * All character drawing: the little person on the map and in menus, and the dialogue portraits.
 *
 * Style: flat polygons, no outlines, one light from the upper left. Every body part gets at most one
 * darker facet on its right side, the same rule the city uses, so people sit in the 2.5D streets.
 * Heads are big (about 40% of the height) so a 22-unit person still shows hair, face and mood.
 *
 * Drawing never allocates: polygons are written into reused buffers and every shape is a static
 * template. Not thread-safe (the game draws from one thread).
 */
final class PersonArt {
    static final int NORMAL = 0, TIRED = 1, HUNGRY = 2, HAPPY = 3, THIRSTY = 4;
    static final String[] MOOD_NAMES = {"רגיל", "עייפות", "רעב", "שמחה", "צמא"};

    /** Every portrait name the game uses; anything else draws a neutral bust. */
    static final String[] CAST = {"mom", "dad", "landlord", "boss", "baker", "barowner", "gasman", "manager",
            "neighbour", "dealer", "cop", "teacher", "croupier"};

    private PersonArt() {
    }

    // =========================================================================================
    // Shared polygon buffers
    // =========================================================================================

    private static final float[][] BUFS = new float[48][];

    private static float[] buf(int points) {
        float[] b = BUFS[points];
        if (b == null) b = BUFS[points] = new float[points * 2];
        return b;
    }

    private static Gfx g;

    /** Draws template t (x,y pairs) at (ax, ay) scaled by k; mx = -1 mirrors it horizontally. */
    private static void tpoly(float[] t, float ax, float ay, float k, float mx, int col) {
        int n = t.length / 2;
        float[] b = buf(n);
        for (int i = 0; i < n; i++) {
            b[2 * i] = ax + mx * t[2 * i] * k;
            b[2 * i + 1] = ay + t[2 * i + 1] * k;
        }
        g.fillPoly(b, col);
    }

    private static float[] arcPts(float[] dst, int at, float cx, float cy, float rx, float ry, float a0, float a1, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / (n - 1));
            dst[at + 2 * i] = cx + (float) Math.cos(a) * rx;
            dst[at + 2 * i + 1] = cy + (float) Math.sin(a) * ry;
        }
        return dst;
    }

    /** Builds a template from an arc (degrees, y down, +x = facing side) followed by extra points. */
    private static float[] arcThen(float r, float a0, float a1, int n, float... extra) {
        float[] t = new float[n * 2 + extra.length];
        arcPts(t, 0, 0, 0, r, r, a0, a1, n);
        System.arraycopy(extra, 0, t, n * 2, extra.length);
        return t;
    }

    static int mix(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * k), gg = (int) (ag + (bg - ag) * k), bl = (int) (ab + (bb - ab) * k);
        return (a & 0xFF000000) | (r << 16) | (gg << 8) | bl;
    }

    static int shade(int argb) {
        return Ui.darken(argb, 0.78f);
    }

    private static int alpha(int argb, int a) {
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // =========================================================================================
    // The little person
    // =========================================================================================

    // Body templates, in units of s; feet at y = 0, +x is the side the person faces.
    // Male: broad shoulders tapering to the hips. Female: narrower shoulders, a waist, wider hips.
    private static final float[] TORSO_M = {-3.7f, -13.0f, 3.7f, -13.0f, 4.4f, -12.0f, 3.3f, -6.0f, -3.3f, -6.0f, -4.4f, -12.0f};
    private static final float[] SHADE_M = {1.6f, -13.0f, 3.7f, -13.0f, 4.4f, -12.0f, 3.3f, -6.0f, 1.6f, -6.0f};
    private static final float[] TORSO_F = {-2.55f, -12.8f, 2.55f, -12.8f, 3.05f, -12.0f, 2.3f, -9.4f, 3.65f, -6.0f,
            -3.65f, -6.0f, -2.3f, -9.4f, -3.05f, -12.0f};
    private static final float[] SHADE_F = {1.2f, -12.8f, 2.55f, -12.8f, 3.05f, -12.0f, 2.3f, -9.4f, 3.65f, -6.0f, 1.2f, -6.0f};
    private static final float[] PANTS = {-3.2f, -6.7f, 3.2f, -6.7f, 2.5f, -4.3f, -2.5f, -4.3f};
    private static final float[] SHORTS = {-3.25f, -6.7f, 3.25f, -6.7f, 3.15f, -3.5f, 0.45f, -3.5f, 0f, -4.4f, -0.45f, -3.5f, -3.15f, -3.5f};
    private static final float[] SKIRT = {-3.0f, -6.7f, 3.0f, -6.7f, 4.1f, -2.9f, -4.1f, -2.9f};
    private static final float[] JACKET_OPEN = {0.35f, -12.9f, 1.55f, -12.9f, 1.25f, -6.1f, 0.6f, -6.1f};
    private static final float[] COLLAR = {-1.4f, -13.0f, 2.2f, -13.0f, 1.4f, -11.3f, 0.4f, -12.3f, -0.6f, -11.4f};
    private static final float[] TIE_T = {0.25f, -12.8f, 1.05f, -12.8f, 0.85f, -12.1f, 1.1f, -8.4f, 0.65f, -7.7f, 0.2f, -8.4f, 0.45f, -12.1f};
    private static final float[] SCARF_BAND = {-3.3f, -13.6f, 3.4f, -13.6f, 3.2f, -11.6f, -3.1f, -11.6f};
    private static final float[] SCARF_TAIL = {0.6f, -12.2f, 2.2f, -12.2f, 2.6f, -7.6f, 1.2f, -7.9f};
    private static final float[] TOTE_T = {-4.7f, -8.8f, -1.7f, -8.8f, -1.4f, -4.8f, -5.0f, -4.8f};

    // Hair templates, in units of the head radius around the head centre.
    private static final float[] H_SHORT = arcThen(1.08f, 165, 355, 9,
            0.9f, -0.28f, 0.45f, -0.5f, -0.1f, -0.42f, -0.62f, -0.05f, -0.8f, 0.32f);
    private static final float[] H_BUZZ = arcThen(1.03f, 170, 350, 8,
            0.88f, -0.42f, 0.3f, -0.72f, -0.45f, -0.55f, -0.82f, 0.05f);
    private static final float[] H_CURLY_F = {
            -1.15f, 0.3f, -1.32f, -0.2f, -1.15f, -0.72f, -0.95f, -1.12f, -0.45f, -1.22f, 0f, -1.38f, 0.5f, -1.22f,
            0.92f, -1.08f, 1.12f, -0.62f, 1.18f, -0.18f, 0.92f, -0.2f, 0.62f, -0.45f, 0.1f, -0.35f, -0.4f, -0.2f, -0.78f, 0.25f};
    private static final float[] H_LONG_B = arcThen(1.1f, -10, -190, 9,
            -1.28f, 0.9f, -1.24f, 1.62f, -0.3f, 1.75f, 0.72f, 1.5f, 1.02f, 0.7f);
    private static final float[] H_LONG_F = arcThen(1.1f, 160, 355, 9,
            0.98f, 0.12f, 0.62f, -0.45f, 0.15f, -0.72f, -0.4f, -0.45f, -0.95f, 0.35f);
    private static final float[] H_BOB_B = arcThen(1.13f, -10, -190, 9,
            -1.24f, 0.55f, -1.02f, 0.98f, 0.2f, 0.98f, 0.95f, 0.78f, 1.12f, 0.3f);
    private static final float[] H_BOB_F = arcThen(1.12f, 160, 357, 9,
            0.96f, -0.18f, 0.5f, -0.3f, 0f, -0.3f, -0.55f, -0.18f, -0.92f, 0.42f);
    private static final float[] H_PONY_F = arcThen(1.07f, 165, 352, 9,
            0.9f, -0.35f, 0.4f, -0.62f, -0.2f, -0.6f, -0.65f, -0.2f, -0.82f, 0.3f);
    private static final float[] H_PONY_TAIL = {-0.8f, -0.75f, -1.55f, -0.5f, -1.8f, 0.4f, -1.55f, 1.25f, -1.25f, 0.45f, -0.95f, -0.15f};
    private static final float[] H_QUIFF = arcThen(1.07f, 165, 300, 8,
            0.85f, -1.38f, 1.25f, -0.88f, 1.02f, -0.42f, 0.55f, -0.58f, 0f, -0.52f, -0.6f, -0.1f, -0.84f, 0.32f);
    private static final float[] H_WAVY_B = arcThen(1.12f, -10, -190, 9,
            -1.3f, 0.5f, -1.12f, 0.95f, -1.38f, 1.32f, -0.7f, 1.28f, 0.35f, 1.12f, 0.92f, 1.3f, 1.18f, 0.72f, 1.05f, 0.3f);
    private static final float[] H_WAVY_F = arcThen(1.11f, 160, 356, 9,
            0.98f, -0.08f, 0.58f, -0.48f, -0.1f, -0.62f, -0.7f, -0.22f, -0.95f, 0.4f);

    // Accessory templates around the head centre (head-radius units).
    private static final float[] CAP_CROWN = arcThen(1.13f, 180, 360, 9, 1.0f, -0.1f, -1.0f, -0.1f);
    private static final float[] CAP_BRIM = {0.55f, -0.36f, 1.75f, -0.22f, 1.7f, -0.04f, 0.55f, -0.12f};
    private static final float[] BEANIE_T = {-1.18f, -0.15f, -1.16f, -0.62f, -0.92f, -1.08f, -0.42f, -1.36f, 0.18f, -1.42f,
            0.74f, -1.2f, 1.1f, -0.78f, 1.2f, -0.15f};
    private static final float[] BEANIE_BAND = {-1.24f, -0.46f, 1.24f, -0.46f, 1.2f, -0.06f, -1.2f, -0.06f};
    private static final float[] HAT_BRIM = arcThen(1f, 0, 360, 14);
    private static final float[] HAT_CROWN = arcThen(0.92f, 185, 355, 8, 0.92f, -0.4f, -0.92f, -0.4f);
    private static final float[] SUNGL = {-0.05f, -0.16f, 1.0f, -0.18f, 0.94f, 0.16f, 0.62f, 0.22f, 0.4f, 0.08f, 0.22f, 0.22f, -0.04f, 0.16f};

    // Mood bubble icons, units of s.
    private static final float[] HEART_T = {-1.9f, -0.3f, 0f, 2.0f, 1.9f, -0.3f};
    private static final float[] DROP_T = {0f, -2.4f, 1.15f, -0.2f, -1.15f, -0.2f};
    private static final float[] Z_T = {-1.2f, -1.2f, 1.2f, -1.2f, 1.2f, -0.7f, -0.3f, 0.7f, 1.2f, 0.7f, 1.2f, 1.2f, -1.2f, 1.2f, -1.2f, 0.7f, 0.3f, -0.7f, -1.2f, -0.7f};

    private static final int EYE = 0xFF1C1614, MOUTH = 0xFF6E2E2A, LENS = 0xFF15181E;

    // Per-draw state (the world origin, scale and facing) shared by the helpers below.
    private static float ox, oy, sc, fx;

    private static float X(float lx) {
        return ox + fx * lx * sc;
    }

    private static float Y(float ly) {
        return oy + ly * sc;
    }

    private static void line(float x1, float y1, float x2, float y2, float w, int col) {
        g.line(X(x1), Y(y1), X(x2), Y(y2), w * sc, col);
    }

    private static void dot(float lx, float ly, float r, int col) {
        g.fillCircle(X(lx), Y(ly), r * sc, col);
    }

    static void draw(Gfx c, float x, float y, float s, Look look, boolean female, float phase, boolean walking, int facing) {
        draw(c, x, y, s, look, female, phase, walking, facing, NORMAL);
    }

    private static final Look TINTED = Look.PRESETS[0].copy();

    /** The default look with its top recoloured: keeps the old {@code Ui.person} calls working. */
    static void drawTinted(Gfx c, float x, float y, float s, boolean female, float phase, boolean walking, int col) {
        TINTED.set(Look.PRESETS[0]);
        TINTED.top = col;
        // The title and name screens show both genders side by side in this look: a ponytail keeps
        // the pair telling apart at a glance without leaning on anything more loaded.
        if (female) TINTED.hairStyle = Look.HAIR_PONY;
        draw(c, x, y, s, TINTED, female, phase, walking, 1, NORMAL);
    }

    /**
     * The little person. (x, y) is between the feet; at s = 1 the figure is about 22 units tall.
     * phase is a running time in seconds (drives the walk cycle and breathing); facing is -1 or +1.
     * mood is one of NORMAL, TIRED, HUNGRY, HAPPY, THIRSTY and changes posture and face only; the
     * optional icon above the head is {@link #moodBubble}.
     */
    static void draw(Gfx c, float x, float y, float s, Look L, boolean female, float phase, boolean walking,
                     int facing, int mood) {
        if (L == null) L = Look.PRESETS[0];
        g = c;
        ox = x;
        oy = y;
        sc = s;
        fx = facing < 0 ? -1 : 1;
        boolean tired = mood == TIRED, happy = mood == HAPPY, hungry = mood == HUNGRY;
        boolean big = s >= 1.6f;

        float speed = tired ? 6.5f : 10.5f, stride = tired ? 1.7f : (happy ? 2.8f : 2.5f);
        float w = phase * speed, sw = walking ? (float) Math.sin(w) : 0, cw = walking ? (float) Math.cos(w) : 0;
        float bob = walking ? (1 - Math.abs(sw)) * 0.75f : 0;
        if (happy && !walking) bob = Math.abs((float) Math.sin(phase * 5.2f)) * 1.1f;
        float breath = (float) Math.sin(phase * (tired ? 1.4f : 2.3f)) * (tired ? 0.35f : 0.22f);
        float lean = (walking ? 0.35f : 0) + (tired ? 0.7f : 0) + (mood == THIRSTY ? 0.3f : 0);
        float slump = (tired ? 0.9f : 0) + (hungry ? 0.45f : 0) + (mood == THIRSTY ? 0.4f : 0);
        float by = -bob - breath * 0.5f;          // upper body offset
        float hdy = by + slump - breath * 0.4f;    // head offset

        int skin = L.skin, top = L.top, bottom = L.bottom;
        boolean bareLegs = L.bottomStyle != Look.BOTTOM_PANTS;
        int legCol = bareLegs ? skin : bottom;
        int back = L.acc[Look.SLOT_BACK];

        // Legs: the far leg (local -x) first.
        for (int i = 0; i < 2; i++) {
            float hip = (i == 0 ? -1 : 1) * (female ? 1.4f : 1.2f);
            float ph = i == 0 ? 0 : (float) Math.PI;
            float swing = walking ? (float) Math.sin(w + ph) : 0;
            float lift = walking ? Math.max(0, (float) Math.cos(w + ph)) * 1.3f : 0;
            float fxl = hip * 1.05f + swing * stride + (happy && !walking ? 0 : 0);
            float fy = -lift - (happy && !walking ? bob : 0);
            float hy = -6.3f + by * 0.6f;
            int col = i == 0 && !bareLegs ? shade(legCol) : legCol;
            if (big && walking) {
                float kx = (hip + fxl) * 0.5f + 0.45f + lift * 0.35f, ky = (hy + fy) * 0.5f;
                line(hip, hy, kx, ky, 1.9f, col);
                line(kx, ky, fxl, fy - 0.5f, 1.8f, col);
            } else {
                line(hip, hy, fxl, fy - 0.5f, 1.9f, col);
            }
            int sh = i == 0 ? shade(L.shoes) : L.shoes;
            line(fxl - 0.45f, fy - 0.55f, fxl + 1.15f, fy - 0.45f - lift * 0.3f, 1.45f, sh);
        }

        float shW = female ? 2.85f : 3.95f;
        float shy = -12.1f + by;

        // Things worn on the back sit behind everything else.
        if (back == Look.BACKPACK) {
            g.fillRoundRect(Math.min(X(-1.6f), X(-4.9f)), Y(-12.4f + by), 3.3f * s, 5.8f * s, 1.1f * s, L.accent);
        } else if (back == Look.GUITAR) {
            line(0.2f, -18.6f + by, -3.9f, -6.4f + by, 1.0f, 0xFF3A2618);
            dot(-4.6f, -5.2f + by, 2.5f, 0xFFB9783C);
            dot(-3.8f, -8.1f + by, 1.85f, 0xFFB9783C);
            dot(-4.2f, -6.4f + by, 0.6f, 0xFF3A2618);
        }

        // Far arm.
        float armSwing = walking ? sw * stride * 0.75f : 0;
        float handY = shy + 5.6f;
        arm(-shW + 0.5f, shy, -shW + 0.15f - armSwing + (tired ? 0.4f : 0), handY - Math.abs(armSwing) * 0.25f,
                happy && !walking, L, true);

        if (L.topStyle == Look.TOP_HOODIE) dot(-0.9f, -12.8f + by, 2.5f, shade(top));
        float[] hair = null, hairBack = null;
        switch (L.hairStyle) {
            case Look.HAIR_LONG -> { hairBack = H_LONG_B; hair = H_LONG_F; }
            case Look.HAIR_BOB -> { hairBack = H_BOB_B; hair = H_BOB_F; }
            case Look.HAIR_WAVY -> { hairBack = H_WAVY_B; hair = H_WAVY_F; }
            case Look.HAIR_PONY -> { hair = H_PONY_F; }
            case Look.HAIR_BUN -> { hair = H_PONY_F; }
            case Look.HAIR_CURLY -> { hair = H_CURLY_F; }
            case Look.HAIR_QUIFF -> { hair = H_QUIFF; }
            case Look.HAIR_BUZZ -> { hair = H_BUZZ; }
            default -> { hair = H_SHORT; }
        }
        float r = 4.35f, hx = X(0.15f + lean), hyw = Y(-17.0f + hdy), hr = r * s;
        if (hairBack != null) tpoly(hairBack, hx, hyw, hr, fx, shade(L.hair));
        if (L.hairStyle == Look.HAIR_PONY) {
            float sway = walking ? sw * 0.12f : (float) Math.sin(phase * 2.3f) * 0.04f;
            tpoly(H_PONY_TAIL, hx - fx * sway * hr, hyw, hr, fx, L.hair);
        }

        // Torso.
        tpoly(female ? TORSO_F : TORSO_M, X(lean * 0.4f), Y(by), s, fx, top);
        tpoly(female ? SHADE_F : SHADE_M, X(lean * 0.4f), Y(by), s, 1, alpha(0x000000, 0x38));
        if (L.topStyle == Look.TOP_JACKET) {
            tpoly(JACKET_OPEN, X(lean * 0.4f), Y(by), s, fx, L.accent == top ? 0xFFE9E6DF : mix(top, 0xFFF4F0E8, 0.55f));
        } else if (L.topStyle == Look.TOP_SHIRT && big) {
            tpoly(COLLAR, X(lean * 0.4f), Y(by), s, fx, mix(top, 0xFFFFFFFF, 0.35f));
        }
        tpoly(L.bottomStyle == Look.BOTTOM_SKIRT ? SKIRT : (L.bottomStyle == Look.BOTTOM_SHORTS ? SHORTS : PANTS),
                X(0), Y(by * 0.6f), s, fx, bottom);
        if (L.acc[Look.SLOT_NECK] == Look.TIE) tpoly(TIE_T, X(lean * 0.4f), Y(by), s, fx, L.accent);
        if (back == Look.TOTE) {
            line(-2.4f, -12.5f + by, -3.4f, -8.8f + by, 0.55f, shade(L.accent));
            tpoly(TOTE_T, X(0), Y(by), s, fx, L.accent);
        } else if (back != Look.NONE && big) {
            line(1.6f, -12.8f + by, 1.1f, -7.2f + by, 0.75f, shade(back == Look.GUITAR ? 0xFF6B4A2E : L.accent));
        }

        // Head.
        g.fillCircle(hx, hyw, hr, skin);
        boolean ears = L.hairStyle == Look.HAIR_SHORT || L.hairStyle == Look.HAIR_BUZZ || L.hairStyle == Look.HAIR_QUIFF
                || L.hairStyle == Look.HAIR_PONY || L.hairStyle == Look.HAIR_BUN;
        if (L.hairStyle == Look.HAIR_BUN) g.fillCircle(hx - fx * 0.45f * hr, hyw - 1.05f * hr, 0.52f * hr, L.hair);
        if (L.hairStyle == Look.HAIR_CURLY) {
            g.fillCircle(hx - fx * 0.25f * hr, hyw - 0.3f * hr, 1.25f * hr, shade(L.hair));
            g.fillCircle(hx, hyw, hr, skin);
        }
        int hairCol = L.hairStyle == Look.HAIR_BUZZ ? mix(L.hair, skin, 0.3f) : L.hair;
        tpoly(hair, hx, hyw, hr, fx, hairCol);
        if (ears && big) g.fillCircle(hx - fx * 0.7f * hr, hyw + 0.2f * hr, 0.2f * hr, mix(skin, 0xFF000000, 0.12f));

        // Face.
        float eyR = Math.max(0.5f, 0.12f * r) * s;
        float e1 = 0.12f, e2 = 0.62f, ey = 0.06f;
        if (tired) {
            float ew = eyR * 1.1f;
            g.line(hx + fx * e1 * hr - ew, hyw + (ey + 0.06f) * hr, hx + fx * e1 * hr + ew, hyw + (ey + 0.06f) * hr, eyR * 0.9f, EYE);
            g.line(hx + fx * e2 * hr - ew, hyw + (ey + 0.06f) * hr, hx + fx * e2 * hr + ew, hyw + (ey + 0.06f) * hr, eyR * 0.9f, EYE);
        } else if (happy && big) {
            happyEye(hx + fx * e1 * hr, hyw + ey * hr, eyR);
            happyEye(hx + fx * e2 * hr, hyw + ey * hr, eyR);
        } else {
            float blink = (phase * 0.31f + (L.skin & 7) * 0.13f) % 1f;
            float eh = blink < 0.035f ? 0.25f : 1f;
            if (eh < 1) {
                g.line(hx + fx * e1 * hr - eyR, hyw + ey * hr, hx + fx * e1 * hr + eyR, hyw + ey * hr, eyR * 0.7f, EYE);
                g.line(hx + fx * e2 * hr - eyR, hyw + ey * hr, hx + fx * e2 * hr + eyR, hyw + ey * hr, eyR * 0.7f, EYE);
            } else {
                g.fillCircle(hx + fx * e1 * hr, hyw + ey * hr, eyR, EYE);
                g.fillCircle(hx + fx * e2 * hr, hyw + ey * hr, eyR, EYE);
            }
        }
        if (big) {
            float mx = hx + fx * 0.48f * hr, my = hyw + 0.52f * hr;
            if (happy) {
                g.fillCircle(mx, my - 0.02f * hr, 0.2f * hr, MOUTH);
                g.fillRect(mx - 0.22f * hr, my - 0.24f * hr, 0.44f * hr, 0.22f * hr, skin);
                g.fillCircle(hx + fx * 0.86f * hr, hyw + 0.36f * hr, 0.15f * hr, 0x55FF6B6B);
            } else if (hungry || mood == THIRSTY) {
                g.fillCircle(mx, my, 0.1f * hr, MOUTH);
            } else {
                g.line(mx - 0.14f * hr, my + (tired ? 0.03f : 0) * hr, mx + 0.14f * hr, my - (tired ? -0.03f : 0.03f) * hr,
                        0.07f * hr, MOUTH);
            }
            float nx = hx + fx * 0.98f * hr;
            g.line(nx - fx * 0.06f * hr, hyw + 0.18f * hr, nx + fx * 0.04f * hr, hyw + 0.3f * hr, 0.13f * hr, mix(skin, 0xFF000000, 0.14f));
        }

        // Head and face accessories.
        int headAcc = L.acc[Look.SLOT_HEAD], accent = L.accent;
        switch (headAcc) {
            case Look.CAP -> {
                tpoly(CAP_CROWN, hx, hyw, hr, fx, accent);
                tpoly(CAP_BRIM, hx, hyw, hr, fx, shade(accent));
            }
            case Look.BEANIE -> {
                tpoly(BEANIE_T, hx, hyw - 0.1f * hr, hr, fx, accent);
                tpoly(BEANIE_BAND, hx, hyw - 0.1f * hr, hr, fx, shade(accent));
                g.fillCircle(hx + fx * 0.15f * hr, hyw - 1.55f * hr, 0.3f * hr, mix(accent, 0xFFFFFFFF, 0.5f));
            }
            case Look.SUNHAT -> {
                float[] b = buf(14);
                for (int i = 0; i < 14; i++) {
                    b[2 * i] = hx + HAT_BRIM[2 * i] * hr * 1.9f;
                    b[2 * i + 1] = hyw - 0.5f * hr + HAT_BRIM[2 * i + 1] * hr * 0.42f;
                }
                g.fillPoly(b, 0xFFE8CF8A);
                tpoly(HAT_CROWN, hx - fx * 0.05f * hr, hyw - 0.55f * hr, hr, fx, 0xFFD8BC74);
                g.line(hx - 0.86f * hr, hyw - 0.82f * hr, hx + 0.86f * hr, hyw - 0.82f * hr, 0.22f * hr, accent);
            }
            case Look.HEADPHONES -> {
                // A thin band hugging the skull, a big cup over the near ear.
                float[] b = buf(6);
                arcPts(b, 0, hx - fx * 0.1f * hr, hyw, hr * 1.1f, hr * 1.12f, fx > 0 ? 200 : 340, fx > 0 ? 330 : 210, 4);
                int band = shade(accent);
                g.line(b[0], b[1], b[2], b[3], 0.17f * hr, band);
                g.line(b[2], b[3], b[4], b[5], 0.17f * hr, band);
                g.line(b[4], b[5], b[6], b[7], 0.17f * hr, band);
                g.fillCircle(hx - fx * 0.78f * hr, hyw + 0.14f * hr, 0.38f * hr, accent);
            }
            case Look.FLOWER -> {
                float fxp = hx - fx * 0.5f * hr, fyp = hyw - 0.72f * hr;
                g.fillCircle(fxp, fyp, 0.34f * hr, accent);
                g.fillCircle(fxp, fyp, 0.13f * hr, 0xFFFFE27A);
            }
            default -> {
            }
        }
        int faceAcc = L.acc[Look.SLOT_FACE];
        if (faceAcc == Look.SUNGLASSES) {
            tpoly(SUNGL, hx, hyw, hr, fx, LENS);
            if (big) g.line(hx - fx * 0.02f * hr, hyw - 0.05f * hr, hx - fx * 0.5f * hr, hyw - 0.0f * hr, 0.1f * hr, LENS);
        } else if (faceAcc == Look.GLASSES) {
            int fr = 0xFF2A2420;
            if (big) {
                float lw = 0.4f * hr, lh = 0.34f * hr, sw2 = 0.08f * hr;
                g.strokeRoundRect(hx + fx * e1 * hr - lw / 2, hyw + ey * hr - lh / 2, lw, lh, 0.1f * hr, sw2, fr);
                g.strokeRoundRect(hx + fx * e2 * hr - lw / 2, hyw + ey * hr - lh / 2, lw, lh, 0.1f * hr, sw2, fr);
                g.line(hx + fx * (e1 - 0.2f) * hr, hyw + ey * hr - 0.05f * hr, hx - fx * 0.5f * hr, hyw - 0.02f * hr, sw2, fr);
            } else {
                g.line(hx + fx * (e1 - 0.25f) * hr, hyw + (ey - 0.17f) * hr, hx + fx * (e2 + 0.25f) * hr, hyw + (ey - 0.17f) * hr,
                        0.13f * hr, fr);
            }
        }
        if (L.acc[Look.SLOT_NECK] == Look.SCARF) {
            tpoly(SCARF_BAND, X(lean * 0.5f), Y(by), s, fx, accent);
            tpoly(SCARF_TAIL, X(lean * 0.4f), Y(by), s, fx, shade(accent));
        }

        // Near arm last so it reads in front of the body.
        if (hungry && !walking) {
            arm(shW - 0.5f, shy, 0.9f, shy + 4.2f, false, L, false);
        } else {
            arm(shW - 0.5f, shy, shW - 0.15f + armSwing + (tired ? 0.4f : 0), handY - Math.abs(armSwing) * 0.25f,
                    happy && !walking, L, false);
        }
    }

    private static void happyEye(float cx, float cy, float r) {
        g.line(cx - r * 1.1f, cy + r * 0.5f, cx, cy - r * 0.6f, r * 0.75f, EYE);
        g.line(cx, cy - r * 0.6f, cx + r * 1.1f, cy + r * 0.5f, r * 0.75f, EYE);
    }

    /** One arm from the shoulder to the hand; a tee shows a short sleeve, other tops cover the arm. */
    private static void arm(float sx, float sy, float hx, float hy, boolean cheer, Look L, boolean far) {
        if (cheer) {
            hx = sx + (sx < 0 ? -1.9f : 1.9f);
            hy = sy + 3.0f;
        }
        int top = far ? shade(L.top) : L.top;
        int skin = far ? mix(L.skin, 0xFF000000, 0.12f) : L.skin;
        if (L.topStyle == Look.TOP_TEE) {
            line(sx, sy, hx, hy, 1.45f, skin);
            line(sx, sy, sx + (hx - sx) * 0.42f, sy + (hy - sy) * 0.42f, 1.9f, top);
        } else {
            line(sx, sy, hx, hy, 1.6f, top);
            dot(hx, hy + 0.25f, 0.8f, skin);
        }
    }

    /**
     * A small icon above the head for a need or mood (TIRED, HUNGRY, THIRSTY, HAPPY; NORMAL draws
     * nothing). (x, y) and s are the same as for {@link #draw}. About 4 primitives.
     */
    static void moodBubble(Gfx c, float x, float y, float s, int mood, float phase) {
        if (mood <= NORMAL || mood > THIRSTY) return;
        g = c;
        float bob = (float) Math.sin(phase * 3f) * 0.6f * s;
        float cx = x + 6.5f * s, cy = y - 27f * s + bob;
        c.fillCircle(cx - 2.6f * s, cy + 3.4f * s, 0.9f * s, 0xDD1A1F28);
        c.fillCircle(cx, cy, 3.6f * s, 0xDD1A1F28);
        switch (mood) {
            case TIRED -> {
                float k = 0.8f + 0.15f * (float) Math.sin(phase * 2f);
                tpoly(Z_T, cx, cy, 1.15f * s * k, 1, 0xFFB9C8FF);
            }
            case HUNGRY -> {
                c.line(cx - 1.6f * s, cy + 1.7f * s, cx + 0.3f * s, cy - 0.2f * s, 1.0f * s, 0xFFEFE3CF);
                c.fillCircle(cx + 0.8f * s, cy - 0.8f * s, 1.6f * s, 0xFFD08A4A);
                c.fillCircle(cx - 1.9f * s, cy + 2.0f * s, 0.6f * s, 0xFFEFE3CF);
            }
            case THIRSTY -> {
                c.fillCircle(cx, cy + 0.6f * s, 1.25f * s, 0xFF5AB4FF);
                tpoly(DROP_T, cx, cy + 0.6f * s, s, 1, 0xFF5AB4FF);
            }
            default -> {
                float k = 1 + 0.12f * (float) Math.sin(phase * 6f);
                c.fillCircle(cx - 0.95f * s * k, cy - 0.7f * s, 1.15f * s * k, 0xFFFF6F91);
                c.fillCircle(cx + 0.95f * s * k, cy - 0.7f * s, 1.15f * s * k, 0xFFFF6F91);
                tpoly(HEART_T, cx, cy - 0.35f * s, s * k, 1, 0xFFFF6F91);
            }
        }
    }

    // =========================================================================================
    // Portraits
    // =========================================================================================

    // Portrait frame: origin at the bust centre, 64 units tall, head centre near (0, -9).
    private static float pcx, pcy, pu;

    private static float PX(float x) {
        return pcx + x * pu;
    }

    private static float PY(float y) {
        return pcy + y * pu;
    }

    private static void pc(float x, float y, float r, int col) {
        g.fillCircle(PX(x), PY(y), r * pu, col);
    }

    private static void pl(float x1, float y1, float x2, float y2, float w, int col) {
        g.line(PX(x1), PY(y1), PX(x2), PY(y2), w * pu, col);
    }

    private static void pr(float x, float y, float w, float h, float r, int col) {
        g.fillRoundRect(PX(x), PY(y), w * pu, h * pu, r * pu, col);
    }

    /** A portrait-space template at offset (dx, dy); mx = -1 mirrors it. */
    private static void pp(float[] t, float dx, float dy, float mx, int col) {
        tpoly(t, PX(dx), PY(dy), pu, mx, col);
    }

    /** Polygon from inline coordinates (portraits are not a hot path, but still use a reused buffer). */
    private static void poly(int col, float... xy) {
        int n = xy.length / 2;
        float[] b = buf(n);
        for (int i = 0; i < n; i++) {
            b[2 * i] = PX(xy[2 * i]);
            b[2 * i + 1] = PY(xy[2 * i + 1]);
        }
        g.fillPoly(b, col);
    }

    // Face shape of the current portrait.
    private static float fw, fh, jaw, hx0, hy0;

    private static float fxAt(double a) {
        float sn = (float) Math.sin(a);
        float k = sn > 0 ? 1 - jaw * sn * sn * 0.4f : 1;
        return hx0 + (float) Math.cos(a) * fw * k;
    }

    private static float fyAt(double a) {
        return hy0 + (float) Math.sin(a) * fh;
    }

    private static void face(int skin) {
        int n = 26;
        float[] b = buf(n);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            b[2 * i] = PX(fxAt(a));
            b[2 * i + 1] = PY(fyAt(a));
        }
        g.fillPoly(b, skin);
        // Right-hand facet, the same light rule as the bodies.
        int m = 10;
        float[] s = buf(m * 2);
        for (int i = 0; i < m; i++) {
            double a = Math.toRadians(-62 + 152.0 * i / (m - 1));
            s[2 * i] = PX(fxAt(a));
            s[2 * i + 1] = PY(fyAt(a));
            int j = 2 * m - 1 - i;
            double a2 = Math.toRadians(-62 + 152.0 * i / (m - 1));
            s[2 * j] = PX(hx0 + (fxAt(a2) - hx0) * 0.72f + 0.6f);
            s[2 * j + 1] = PY(hy0 + (fyAt(a2) - hy0) * 0.96f);
        }
        g.fillPoly(s, 0x1F000000);
    }

    /** Lower-face beard or stubble following the jaw; full = opaque beard covering cheeks. */
    private static void beard(int col, float top, float thick) {
        int m = 11;
        float[] b = buf(m * 2);
        for (int i = 0; i < m; i++) {
            double a = Math.toRadians(-8 + 196.0 * i / (m - 1));
            b[2 * i] = PX(fxAt(a) + (fxAt(a) - hx0) * thick * 0.04f);
            b[2 * i + 1] = PY(fyAt(a) + thick * (float) Math.max(0, Math.sin(a)));
            int j = 2 * m - 1 - i;
            double a2 = Math.toRadians(-8 + 196.0 * i / (m - 1));
            float ix = hx0 + (fxAt(a2) - hx0) * 0.78f, iy = hy0 + top + (fyAt(a2) - hy0 - top) * 0.25f;
            b[2 * j] = PX(ix);
            b[2 * j + 1] = PY(iy);
        }
        g.fillPoly(b, col);
    }

    private static void ears(int skin) {
        pc(hx0 - fw * 0.98f, hy0 + 1.5f, 2.4f, skin);
        pc(hx0 + fw * 0.98f, hy0 + 1.5f, 2.4f, mix(skin, 0xFF000000, 0.14f));
    }

    /** Eyes at (±ex, ey); open 0..1 (lids drop from the top); lx/ly shift the pupils. */
    private static void eyes(float ex, float ey, float r, float open, float lx, float ly, int skin) {
        for (int i = -1; i <= 1; i += 2) {
            float x = hx0 + i * ex;
            if (open < 0.2f) {
                pl(x - r * 1.1f, ey + r * 0.3f, x + r * 1.1f, ey + r * 0.3f, 0.75f, EYE);
                continue;
            }
            pc(x + lx, ey + ly, r, EYE);
            pc(x + lx + r * 0.38f, ey + ly - r * 0.38f, r * 0.32f, 0xEEFFFFFF);
            if (open < 1) {
                float lid = 2 * r * (1 - open);
                g.fillRect(PX(x - r * 1.4f), PY(ey - r * 1.5f), r * 2.8f * pu, (lid + r * 0.5f) * pu, skin);
                pl(x - r * 1.15f, ey - r * 1.0f + lid, x + r * 1.15f, ey - r * 1.0f + lid, 0.6f, mix(skin, 0xFF000000, 0.3f));
            }
        }
    }

    /** Brows: inner and outer heights relative to y for the left (viewer) and right brow. */
    private static void brows(float y, float lIn, float lOut, float rIn, float rOut, float thick, int col) {
        pl(hx0 - 2.0f, y + lIn, hx0 - 7.0f, y + lOut, thick, col);
        pl(hx0 + 2.0f, y + rIn, hx0 + 7.0f, y + rOut, thick, col);
    }

    private static void nose(int skin) {
        poly(mix(skin, 0xFF000000, 0.2f), hx0 + 0.4f, hy0 + 1.6f, hx0 + 2.0f, hy0 + 5.4f, hx0 - 0.6f, hy0 + 5.8f);
    }

    static final int M_SMILE = 0, M_FLAT = 1, M_SMIRK = 2, M_GRIN = 3, M_O = 4, M_SOFT = 5;

    private static void mouth(int kind, float y, float open) {
        float x = hx0;
        switch (kind) {
            case M_SMILE -> poly(MOUTH, x - 3.3f, y, x + 3.3f, y, x + 2.2f, y + 1.7f + open, x, y + 2.3f + open, x - 2.2f, y + 1.7f + open);
            case M_GRIN -> {
                poly(MOUTH, x - 3.8f, y - 0.3f, x + 3.8f, y - 0.3f, x + 2.6f, y + 2.4f, x, y + 3.1f, x - 2.6f, y + 2.4f);
                poly(0xFFF4EFE6, x - 3.2f, y - 0.1f, x + 3.2f, y - 0.1f, x + 2.6f, y + 0.8f, x - 2.6f, y + 0.8f);
            }
            case M_FLAT -> pl(x - 2.6f, y + 0.8f, x + 2.6f, y + 0.8f, 0.9f, MOUTH);
            case M_SMIRK -> {
                pl(x - 2.6f, y + 1.0f, x + 1.4f, y + 0.9f, 0.9f, MOUTH);
                pl(x + 1.4f, y + 0.9f, x + 3.0f, y - 0.1f, 0.9f, MOUTH);
            }
            case M_O -> pc(x, y + 1.2f, 1.2f + open, MOUTH);
            default -> {
                pl(x - 2.4f, y + 0.3f, x, y + 1.2f, 0.9f, MOUTH);
                pl(x, y + 1.2f, x + 2.4f, y + 0.3f, 0.9f, MOUTH);
            }
        }
    }

    /** Shoulders and chest; sw = half width at the bottom. Returns nothing; draws cloth plus facet. */
    private static void shoulders(float sw, float br, int cloth) {
        poly(cloth, -sw, 33, -sw + 0.6f, 22 + br, -sw * 0.66f, 15 + br, -6.5f, 12.2f + br, 6.5f, 12.2f + br,
                sw * 0.66f, 15 + br, sw - 0.6f, 22 + br, sw, 33);
        poly(0x26000000, sw * 0.42f, 13.8f + br, sw * 0.66f, 15 + br, sw - 0.6f, 22 + br, sw, 33, sw * 0.5f, 33);
    }

    private static void neck(int skin, float br) {
        g.fillRect(PX(hx0 - 4.3f), PY(hy0 + 6), 8.6f * pu, (15.5f + br - hy0 - 6) * pu, mix(skin, 0xFF000000, 0.16f));
    }

    private static float blinkOpen(float t, int seed, float open) {
        float period = 3.3f + (seed & 7) * 0.21f;
        float ph = (t + (seed >>> 3 & 15) * 0.37f) % period;
        return ph < 0.14f ? 0 : open;
    }

    /** Generic hair cap over the top of the head; fringe heights are relative to the head centre. */
    private static void hairCap(int col, float vol, float side, float mid, float centre, float part) {
        int n = 13;
        float[] b = buf(n + 5);
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(172 + 196.0 * i / (n - 1));
            b[2 * i] = PX(hx0 + (float) Math.cos(a) * fw * vol);
            b[2 * i + 1] = PY(hy0 + (float) Math.sin(a) * fh * vol);
        }
        float[] f = {fw * 0.98f, side, fw * 0.5f, mid - part, part, centre, -fw * 0.5f, mid + part, -fw * 0.98f, side};
        for (int i = 0; i < 5; i++) {
            b[2 * (n + i)] = PX(hx0 + f[2 * i]);
            b[2 * (n + i) + 1] = PY(hy0 + f[2 * i + 1]);
        }
        g.fillPoly(b, col);
    }

    /** Hair mass behind the head: an ellipse-ish block down to y = bottom (relative to head centre). */
    private static void hairBack(int col, float width, float bottom, float topVol) {
        poly(col, hx0 - fw * width, hy0 - 2, hx0 - fw * topVol, hy0 - fh * 0.85f, hx0, hy0 - fh * 1.1f,
                hx0 + fw * topVol, hy0 - fh * 0.85f, hx0 + fw * width, hy0 - 2, hx0 + fw * width * 0.98f, hy0 + bottom,
                hx0 - fw * width * 0.98f, hy0 + bottom);
    }

    /** A tapered limb as a quad: flat ends, so a sleeve can stop exactly at the bottom edge. */
    private static void limb(float x1, float y1, float w1, float x2, float y2, float w2, int col) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float nx = -dy / len, ny = dx / len;
        poly(col, x1 + nx * w1 / 2, y1 + ny * w1 / 2, x2 + nx * w2 / 2, y2 + ny * w2 / 2,
                x2 - nx * w2 / 2, y2 - ny * w2 / 2, x1 - nx * w1 / 2, y1 - ny * w1 / 2);
    }

    /** A band of hair hugging the head between two angles (degrees), from radius rIn to rOut. */
    private static void hairBand(float a0, float a1, float rOut, float rIn, int col) {
        int n = 7;
        float[] b = buf(n * 2);
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / (n - 1));
            b[2 * i] = PX(hx0 + (float) Math.cos(a) * fw * rOut);
            b[2 * i + 1] = PY(hy0 + (float) Math.sin(a) * fh * rOut);
            int j = 2 * n - 1 - i;
            b[2 * j] = PX(hx0 + (float) Math.cos(a) * fw * rIn);
            b[2 * j + 1] = PY(hy0 + (float) Math.sin(a) * fh * rIn);
        }
        g.fillPoly(b, col);
    }

    private static void backdrop(int col) {
        g.fillCircle(PX(0), PY(-2), 31 * pu, col);
    }

    private static void star(float x, float y, float r, int col) {
        poly(col, x, y - r, x + r * 0.22f, y - r * 0.22f, x + r, y, x + r * 0.22f, y + r * 0.22f, x, y + r,
                x - r * 0.22f, y + r * 0.22f, x - r, y, x - r * 0.22f, y - r * 0.22f);
    }

    private static void setHead(float w, float h, float j, float hy) {
        fw = w;
        fh = h;
        jaw = j;
        hx0 = 0;
        hy0 = hy;
    }

    /**
     * Head-and-shoulders bust for a dialogue card, centred on (cx, cy), {@code size} units tall
     * (64 is the design size; 128 doubles everything). t is a running time in seconds for blinking,
     * breathing and small idle motions. Unknown names draw a neutral bust.
     */
    static void portrait(Gfx c, String who, float cx, float cy, float size, float t) {
        g = c;
        pcx = cx;
        pcy = cy;
        pu = size / 64f;
        String k = who == null ? "" : who;
        int seed = k.hashCode() & 0x7FFFFFFF;
        float br = (float) Math.sin(t * 1.9f + (seed & 15)) * 0.55f;
        float hy = -9 + br * 0.5f;
        switch (k) {
            case "mom" -> mom(t, br, hy, seed);
            case "dad" -> dad(t, br, hy, seed);
            case "landlord" -> landlord(t, br, hy, seed);
            case "boss" -> boss(t, br, hy, seed);
            case "baker" -> baker(t, br, hy, seed);
            case "barowner" -> barowner(t, br, hy, seed);
            case "gasman" -> gasman(t, br, hy, seed);
            case "manager" -> manager(t, br, hy, seed);
            case "neighbour" -> neighbour(t, br, hy, seed);
            case "dealer" -> dealer(t, br, hy);
            case "cop" -> cop(t, br, hy, seed);
            case "teacher" -> teacher(t, br, hy, seed);
            case "croupier" -> croupier(t, br, hy, seed);
            default -> neutral(t, br, hy, seed);
        }
    }

    // ---- The cast -----------------------------------------------------------------------------

    private static void mom(float t, float br, float hy, int seed) {
        int skin = 0xFFE3AE86, hair = 0xFF4A3026, cardi = 0xFFE0705A;
        backdrop(0x50FFA88A);
        setHead(11.6f, 13.4f, 0.55f, hy);
        hairBack(hair, 1.12f, 5f, 0.9f);
        pc(hx0 + 2, hy0 - 15.5f, 5.6f, hair);                       // bun
        neck(skin, br);
        shoulders(27, br, cardi);
        poly(0xFFF3E6D2, -6.5f, 12.4f + br, 6.5f, 12.4f + br, 3.5f, 22 + br, 0, 24 + br, -3.5f, 22 + br); // blouse
        pl(-6, 13 + br, -2.5f, 33, 1.0f, Ui.darken(cardi, 0.8f));
        pl(6, 13 + br, 2.5f, 33, 1.0f, Ui.darken(cardi, 0.8f));
        ears(skin);
        face(skin);
        hairCap(hair, 1.07f, 0.5f, -7.5f, -8.2f, 2.6f);
        hairBand(196, 214, 1.075f, 0.93f, 0xFF9A8E86);                       // grey at the temple
        // Reading glasses pushed up into the hair: she was reading your bank statement.
        g.strokeRoundRect(PX(-7.4f), PY(hy0 - 13.6f), 5.6f * pu, 3.6f * pu, 1.5f * pu, 0.8f * pu, 0xFFC0404E);
        g.strokeRoundRect(PX(1.8f), PY(hy0 - 13.6f), 5.6f * pu, 3.6f * pu, 1.5f * pu, 0.8f * pu, 0xFFC0404E);
        pl(-1.8f, hy0 - 12.2f, 1.8f, hy0 - 12.2f, 0.8f, 0xFFC0404E);
        float open = blinkOpen(t, seed, 0.85f);
        float look = (float) Math.sin(t * 0.5f) * 0.4f;
        eyes(4.6f, hy0 + 0.6f, 1.75f, open, look + 0.3f, 0, skin);
        brows(hy0 - 3.6f, 0.2f, 0.4f, -0.6f, -2.2f, 1.25f, 0xFF3A251E);   // one brow up: she knows
        nose(skin);
        float talk = (float) Math.max(0, Math.sin(t * 9.0) * Math.sin(t * 2.3 + 1));
        mouth(M_SMILE, hy0 + 8.4f, talk * 1.6f);
        pc(hx0 - 7.4f, hy0 + 5.2f, 1.8f, 0x30FF5A5A);
        pc(hx0 + 7.4f, hy0 + 5.2f, 1.8f, 0x30FF5A5A);
        pc(hx0 + fw * 0.98f, hy0 + 4.8f, 0.9f, Ui.ACCENT);              // earring
        // The phone at her ear (bright case, so it reads), her hand around it, the sleeve out of frame.
        float tilt = (float) Math.sin(t * 1.3f) * 0.35f, py = hy0;
        limb(-15.2f + tilt, py + 9, 5.6f, -21.5f, 33, 9.5f, Ui.darken(cardi, 0.86f));
        poly(0xFF3FB0A6, -16.0f + tilt, py - 6.2f, -12.2f + tilt, py - 7.0f, -8.9f + tilt, py + 8.0f, -12.7f + tilt, py + 8.8f);
        pc(-14.0f + tilt, py - 4.6f, 0.75f, 0xFF20302E);
        pr(-18.2f + tilt, py + 0.6f, 7.0f, 9.4f, 3.3f, skin);
        pl(-12.6f + tilt, py + 2.0f, -10.0f + tilt, py + 0.8f, 1.8f, skin);
        pl(-12.8f + tilt, py + 4.6f, -9.8f + tilt, py + 3.6f, 1.8f, mix(skin, 0xFF000000, 0.08f));
        pl(-13.0f + tilt, py + 7.2f, -10.4f + tilt, py + 6.4f, 1.8f, mix(skin, 0xFF000000, 0.12f));
    }

    private static void dad(float t, float br, float hy, int seed) {
        int skin = 0xFFD9A07A, hair = 0xFF6E655E, sweater = 0xFF3B5A86;
        backdrop(0x405AA9FF);
        setHead(12.2f, 13.6f, 0.35f, hy);
        neck(skin, br);
        shoulders(30, br, sweater);
        poly(0xFFBFD7F0, -6.5f, 12.2f + br, 0, 17.5f + br, -1.5f, 20 + br, -7.5f, 16 + br);
        poly(0xFFBFD7F0, 6.5f, 12.2f + br, 0, 17.5f + br, 1.5f, 20 + br, 7.5f, 16 + br);
        poly(Ui.darken(sweater, 0.85f), -5, 15 + br, 5, 15 + br, 0, 25 + br);
        poly(0xFFBFD7F0, -1.6f, 16 + br, 1.6f, 16 + br, 0, 21 + br);
        ears(skin);
        face(skin);
        // Hair only on the sides and a little on top.
        hairBand(160, 222, 1.07f, 0.9f, hair);
        hairBand(-42, 20, 1.07f, 0.9f, hair);
        poly(alpha(hair, 0x90), hx0 - 4, hy0 - 12.6f, hx0 + 1, hy0 - 13.8f, hx0 + 4.5f, hy0 - 12.8f, hx0 + 1, hy0 - 12.2f);
        float open = blinkOpen(t, seed, 1);
        eyes(4.8f, hy0 + 0.5f, 1.6f, open, 0, 0.2f, skin);
        g.strokeRoundRect(PX(-8.6f), PY(hy0 - 2.6f), 7.0f * pu, 6.0f * pu, 3.0f * pu, 0.85f * pu, 0xFF3A3632);
        g.strokeRoundRect(PX(1.6f), PY(hy0 - 2.6f), 7.0f * pu, 6.0f * pu, 3.0f * pu, 0.85f * pu, 0xFF3A3632);
        pl(-1.6f, hy0 - 0.2f, 1.6f, hy0 - 0.2f, 0.8f, 0xFF3A3632);
        brows(hy0 - 4.6f, -0.3f, 0.6f, -0.3f, 0.6f, 1.2f, 0xFF5B534D);
        nose(skin);
        mouth(M_SMILE, hy0 + 8.6f, 0);
        poly(0xFF5B534D, hx0 - 4.8f, hy0 + 8.2f, hx0 - 0.6f, hy0 + 6.4f, hx0, hy0 + 7.0f, hx0 + 0.6f, hy0 + 6.4f,
                hx0 + 4.8f, hy0 + 8.2f, hx0 + 2.5f, hy0 + 8.6f, hx0, hy0 + 8.0f, hx0 - 2.5f, hy0 + 8.6f);
        pc(hx0 - 7.6f, hy0 + 4.6f, 1.7f, 0x26FF5A5A);
        pc(hx0 + 7.6f, hy0 + 4.6f, 1.7f, 0x26FF5A5A);
    }

    private static void landlord(float t, float br, float hy, int seed) {
        int skin = 0xFFC48C64, hair = 0xFF9C9690, polo = 0xFF7C8A4A;
        backdrop(0x40C9B458);
        setHead(12.6f, 13.2f, 0.2f, hy);
        neck(skin, br);
        shoulders(31, br, polo);
        poly(Ui.darken(polo, 1.15f), -6.5f, 12.2f + br, -1, 17 + br, -3, 19 + br, -9.5f, 14.5f + br);
        poly(Ui.darken(polo, 1.15f), 6.5f, 12.2f + br, 1, 17 + br, 3, 19 + br, 9.5f, 14.5f + br);
        pc(0, 20 + br, 0.7f, 0xFFE8E2D0);
        pc(0, 23.5f + br, 0.7f, 0xFFE8E2D0);
        ears(skin);
        face(skin);
        beard(0x2A1A1410, 3.5f, 0.4f);
        hairCap(hair, 1.04f, -3.5f, -10.2f, -11.5f, 0);
        float open = blinkOpen(t, seed, 0.65f);
        eyes(4.9f, hy0 + 0.5f, 1.6f, open, 0.4f, 0, skin);
        brows(hy0 - 3.4f, 0.8f, 0, -1.0f, -2.0f, 1.9f, 0xFF6E6862);
        nose(skin);
        mouth(M_SMIRK, hy0 + 8.4f, 0);
        pl(hx0 + 9.6f, hy0 - 4.4f, hx0 + 13.5f, hy0 - 8.4f, 1.1f, 0xFFE8C547);  // pencil behind the ear
        // Keys held up, swinging: rent is due.
        float sway = (float) Math.sin(t * 2.6f) * 0.25f;
        limb(17, 12 + br, 5.4f, 21, 33, 8, Ui.darken(polo, 0.9f));
        pr(13.5f, 6 + br, 7, 7.5f, 3, skin);
        float kx = 17, ky = 13.5f + br;
        g.strokeRoundRect(PX(kx - 2.2f), PY(ky - 0.4f), 4.4f * pu, 4.4f * pu, 2.2f * pu, 0.7f * pu, 0xFFD9DEE5);
        for (int i = 0; i < 3; i++) {
            float a = sway + (i - 1) * 0.45f;
            float ex = kx + (float) Math.sin(a) * 6.5f, ey = ky + 3.6f + (float) Math.cos(a) * 6.5f;
            pl(kx + (i - 1) * 0.8f, ky + 3.6f, ex, ey, 1.1f, i == 1 ? 0xFFE8C547 : 0xFFC9CED6);
            pc(ex, ey, 1.4f, i == 1 ? 0xFFE8C547 : 0xFFC9CED6);
        }
    }

    private static void boss(float t, float br, float hy, int seed) {
        int skin = 0xFF9A6444, hair = 0xFF1E1714, white = 0xFFF4F1EA;
        backdrop(0x40FF6B6B);
        setHead(11.4f, 13.3f, 0.5f, hy);
        hairBack(hair, 1.06f, 2f, 0.9f);
        neck(skin, br);
        shoulders(28, br, white);
        poly(0xFFD94A4A, -6.5f, 12.2f + br, 6.5f, 12.2f + br, 4, 16.5f + br, 0, 18 + br, -4, 16.5f + br);
        poly(0xFFD94A4A, -1.5f, 16 + br, 1.5f, 16 + br, 2.5f, 21 + br, -1, 20 + br);
        for (int i = 0; i < 3; i++) {
            pc(-4.5f, 21.5f + i * 4 + br, 0.8f, 0xFF3A3632);
            pc(4.5f, 21.5f + i * 4 + br, 0.8f, 0xFF3A3632);
        }
        // A tasting spoon held up: she will taste it, and she will have notes.
        float sp = (float) Math.sin(t * 1.5f) * 0.4f;
        limb(18 + sp, 13 + br, 6, 23, 33, 9, 0xFFE6E2D8);
        pl(17.5f + sp, 9 + br, 21.5f + sp, -9 + br, 1.3f, 0xFFB07A48);
        pc(22 + sp, -11.2f + br, 2.4f, 0xFFB07A48);
        pc(22.4f + sp, -11.6f + br, 1.3f, 0xFFC98E5C);
        pr(14.8f + sp, 6.5f + br, 6.6f, 8, 3, skin);
        ears(skin);
        face(skin);
        hairCap(hair, 1.05f, -3, -8.8f, -9.5f, -2);
        // Chef's toque.
        poly(white, -10.5f, hy0 - 9.5f, 10.5f, hy0 - 9.5f, 9.5f, hy0 - 15.5f, -9.5f, hy0 - 15.5f);
        pc(-6, hy0 - 19.5f, 6.2f, white);
        pc(5, hy0 - 20.5f, 6.8f, white);
        pc(0, hy0 - 23.5f, 6.4f, white);
        poly(0x1E000000, 2, hy0 - 15.5f, 9.5f, hy0 - 15.5f, 10.5f, hy0 - 9.5f, 3, hy0 - 9.5f);
        float open = blinkOpen(t, seed, 0.9f);
        eyes(4.5f, hy0 + 0.6f, 1.65f, open, 0, 0, skin);
        brows(hy0 - 3.3f, 1.0f, -0.6f, 1.0f, -0.6f, 1.6f, 0xFF140F0D);
        nose(skin);
        mouth(M_FLAT, hy0 + 8.2f, 0);
        pc(hx0 + fw * 0.98f, hy0 + 4.4f, 0.7f, 0xFFE8E2D0);
    }

    private static void baker(float t, float br, float hy, int seed) {
        int skin = 0xFFEDBE96, hair = 0xFFB5582A, apron = 0xFFF0E6D2, shirt = 0xFF6FA8C8;
        backdrop(0x40FFC857);
        setHead(13.2f, 13.2f, 0.1f, hy);
        neck(skin, br);
        shoulders(31, br, shirt);
        poly(apron, -11, 16 + br, 11, 16 + br, 13, 33, -13, 33);
        pl(-11, 16 + br, -7, 12.5f + br, 1.4f, apron);
        pl(11, 16 + br, 7, 12.5f + br, 1.4f, apron);
        pc(-6, 26, 2.2f, 0x55FFFFFF);                                  // flour
        pc(-3.5f, 28.5f, 1.3f, 0x55FFFFFF);
        ears(skin);
        face(skin);
        // Curls peeking under the cap.
        for (int i = 0; i < 5; i++) {
            float a = -0.25f + i * 0.4f;
            pc(hx0 - fw * 0.95f + (i % 2) * 0.6f, hy0 - 7 + i * 1.8f - (i > 2 ? 4 : 0) * 0, 2.4f, hair);
            if (i < 4) pc(hx0 + fw * 0.95f - (i % 2) * 0.6f, hy0 - 6.5f + i * 1.9f, 2.4f, hair);
            if (a > 99) break;
        }
        hairCap(hair, 1.05f, -4, -8, -7.5f, 1);
        // Paper baker's cap.
        poly(0xFFFFFFFF, hx0 - 12.8f, hy0 - 8, hx0 + 12.8f, hy0 - 8, hx0 + 11.5f, hy0 - 15.5f, hx0 - 11.5f, hy0 - 15.5f);
        poly(0xFFE6E3DC, hx0 - 11.5f, hy0 - 15.5f, hx0 + 11.5f, hy0 - 15.5f, hx0 + 6, hy0 - 19, hx0 - 6, hy0 - 19);
        float open = blinkOpen(t, seed, 1);
        eyes(5.0f, hy0 + 0.8f, 1.55f, open, 0, 0, skin);
        brows(hy0 - 3.5f, -0.6f, 0.4f, -0.6f, 0.4f, 1.5f, 0xFF9A4520);
        nose(skin);
        mouth(M_GRIN, hy0 + 7.8f, 0);
        pc(hx0 - 8.2f, hy0 + 4.6f, 2.2f, 0x40FF5A5A);
        pc(hx0 + 8.2f, hy0 + 4.6f, 2.2f, 0x40FF5A5A);
        pc(hx0 - 9.0f, hy0 - 1.0f, 1.5f, 0x66FFFFFF);                  // flour on the cheek
        pc(hx0 - 7.8f, hy0 - 0.2f, 1.0f, 0x66FFFFFF);
        // A baguette on the shoulder.
        float w = (float) Math.sin(t * 1.7f) * 0.3f;
        pl(-24 + w, 31, -12 + w, -2, 5.4f, 0xFFD7A15C);
        for (int i = 0; i < 3; i++) {
            float k = 0.25f + i * 0.25f;
            pl(-24 + w + 12 * k - 1.2f, 31 - 33 * k + 0.6f, -24 + w + 12 * k + 1.0f, 31 - 33 * k - 1.2f, 0.8f, 0xFFF0CF92);
        }
        pr(-27, 22 + br, 7, 7, 3, skin);
    }

    private static void barowner(float t, float br, float hy, int seed) {
        int skin = 0xFFB57A52, hair = 0xFF8A2A55, tee = 0xFF22252B;
        backdrop(0x40B85AFF);
        setHead(11.2f, 13.4f, 0.55f, hy);
        neck(skin, br);
        shoulders(28, br, tee);
        poly(skin, -6.5f, 12.2f + br, 6.5f, 12.2f + br, 4, 15.5f + br, -4, 15.5f + br);
        // Bar towel over the shoulder, striped.
        poly(0xFFE9E6DF, 9, 13.5f + br, 15, 15 + br, 14, 33, 8, 33);
        pl(8.6f, 20, 14.6f, 20.5f, 1.2f, 0xFF5AA9FF);
        pl(8.4f, 25, 14.4f, 25.5f, 1.2f, 0xFF5AA9FF);
        // A small line tattoo on the collarbone.
        pl(-13, 16 + br, -9, 18 + br, 0.6f, 0x995A8AA0);
        pl(-9, 18 + br, -6.5f, 16.5f + br, 0.6f, 0x995A8AA0);
        pc(-13, 16 + br, 0.9f, 0x995A8AA0);
        ears(skin);
        face(skin);
        // Undercut: shaved left side, long top swept to the right.
        poly(mix(hair, skin, 0.65f), hx0 - fw * 1.0f, hy0 + 1, hx0 - fw * 1.02f, hy0 - 7, hx0 - fw * 0.55f, hy0 - 11.5f, hx0 - fw * 0.65f, hy0 - 4);
        poly(hair, hx0 - fw * 0.75f, hy0 - 9, hx0 - fw * 0.3f, hy0 - 15.5f, hx0 + fw * 0.6f, hy0 - 16, hx0 + fw * 1.25f, hy0 - 10,
                hx0 + fw * 1.25f, hy0 + 1, hx0 + fw * 0.95f, hy0 + 4, hx0 + fw * 0.85f, hy0 - 4, hx0 + fw * 0.1f, hy0 - 7.5f);
        float open = blinkOpen(t, seed, 0.8f);
        eyes(4.5f, hy0 + 0.6f, 1.65f, open, -0.3f, 0, skin);
        pl(hx0 - 6.6f, hy0 - 0.3f, hx0 - 7.6f, hy0 - 1.0f, 0.7f, EYE);   // eyeliner flick
        pl(hx0 + 6.6f, hy0 - 0.3f, hx0 + 7.6f, hy0 - 1.0f, 0.7f, EYE);
        brows(hy0 - 3.6f, 0, -0.6f, -0.8f, -2.4f, 1.3f, 0xFF3A1A26);
        nose(skin);
        mouth(M_SMIRK, hy0 + 8.3f, 0);
        for (int i = 0; i < 3; i++) pc(hx0 - fw * 1.02f, hy0 - 0.5f + i * 1.9f, 0.55f, 0xFFD9DEE5);
    }

    private static void gasman(float t, float br, float hy, int seed) {
        int skin = 0xFF8A5A3E, beardC = 0xFF2E221C, vest = 0xFFFF8A2A, hood = 0xFF3D4656;
        backdrop(0x402E4A7A);
        setHead(11.8f, 13.6f, 0.3f, hy);
        neck(skin, br);
        shoulders(30, br, hood);
        poly(vest, -30, 33, -29.4f, 22 + br, -19.8f, 15 + br, -9, 13.8f + br, -6, 33);
        poly(vest, 30, 33, 29.4f, 22 + br, 19.8f, 15 + br, 9, 13.8f + br, 6, 33);
        poly(0xFFD9DEE5, -29.6f, 25, -7, 25, -7, 27.5f, -29.8f, 27.5f);
        poly(0xFFD9DEE5, 29.6f, 25, 7, 25, 7, 27.5f, 29.8f, 27.5f);
        ears(skin);
        face(skin);
        beard(beardC, 4.2f, 1.6f);
        poly(beardC, hx0 - 4.5f, hy0 + 7.6f, hx0, hy0 + 6.2f, hx0 + 4.5f, hy0 + 7.6f, hx0 + 3.2f, hy0 + 8.4f, hx0 - 3.2f, hy0 + 8.4f);
        // Beanie.
        poly(0xFF3E7A5A, hx0 - fw * 1.08f, hy0 - 4.5f, hx0 - fw * 0.95f, hy0 - 12, hx0 - 5, hy0 - 17, hx0 + 5, hy0 - 17,
                hx0 + fw * 0.95f, hy0 - 12, hx0 + fw * 1.08f, hy0 - 4.5f);
        poly(0xFF2F5E45, hx0 - fw * 1.12f, hy0 - 7.5f, hx0 + fw * 1.12f, hy0 - 7.5f, hx0 + fw * 1.1f, hy0 - 3.8f, hx0 - fw * 1.1f, hy0 - 3.8f);
        // Eyes half-closed and looking up at the stars: it is 3 a.m. and he has a theory.
        float open = blinkOpen(t, seed, 0.55f);
        eyes(4.6f, hy0 + 0.8f, 1.6f, open, 0.3f, -0.5f, skin);
        brows(hy0 - 3.2f, -1.0f, 0.2f, -1.0f, 0.2f, 1.3f, beardC);
        nose(skin);
        mouth(M_SOFT, hy0 + 9.3f, 0);
        float tw = 0.5f + 0.5f * (float) Math.sin(t * 2.4f);
        star(hx0 + 17, hy0 - 15, 1.6f + tw * 1.2f, alpha(0xFFF4E7A1, 120 + (int) (tw * 135)));
        star(hx0 - 19, hy0 - 6, 1.2f, 0x99F4E7A1);
        // Coffee cup with steam.
        limb(16.5f, 21 + br, 5.6f, 19, 33, 8, Ui.darken(hood, 0.9f));
        poly(0xFFF0EBE2, 12.5f, 13 + br, 20.5f, 13 + br, 19.5f, 23 + br, 13.5f, 23 + br);
        poly(0xFF9A6A4A, 12.7f, 15 + br, 20.3f, 15 + br, 20.1f, 17.5f + br, 12.9f, 17.5f + br);
        pr(12, 19 + br, 9, 6.5f, 3, skin);
        for (int i = 0; i < 2; i++) {
            float st = (t * 0.7f + i * 0.5f) % 1f;
            float sx = 15 + i * 3 + (float) Math.sin(t * 3 + i * 2) * 1.2f, sy = 11 + br - st * 8;
            pl(sx, sy, sx + (float) Math.sin(t * 3 + i) * 1.4f, sy - 2.6f, 0.9f, alpha(0xFFFFFFFF, (int) (150 * (1 - st))));
        }
    }

    private static void manager(float t, float br, float hy, int seed) {
        int skin = 0xFFF0C8A6, hair = 0xFFA07A48, shirt = 0xFF5BB08A;
        backdrop(0x405BD68A);
        setHead(11.8f, 13.6f, 0.4f, hy);
        neck(skin, br);
        shoulders(30, br, shirt);
        poly(Ui.darken(shirt, 1.12f), -6.5f, 12.2f + br, -0.6f, 16.5f + br, -3.4f, 19 + br, -9.2f, 14.6f + br);
        poly(Ui.darken(shirt, 1.12f), 6.5f, 12.2f + br, 0.6f, 16.5f + br, 3.4f, 19 + br, 9.2f, 14.6f + br);
        poly(0xFF2F4A7A, -1.4f, 15.8f + br, 1.4f, 15.8f + br, 1.1f, 17.6f + br, 2.2f, 31 + br, 0, 33, -2.2f, 31 + br, -1.1f, 17.6f + br);
        // Name badge and a pen in the pocket.
        pr(-17, 20 + br, 9, 5, 1.2f, 0xFFF7F4EC);
        pl(-15.5f, 22.5f + br, -9.5f, 22.5f + br, 1.0f, 0xFF5BB08A);
        pl(12, 18 + br, 12, 23.5f + br, 1.3f, 0xFF2F4A7A);
        ears(skin);
        face(skin);
        hairCap(hair, 1.07f, -2, -10, -10.5f, 4.5f);
        poly(hair, hx0 - fw * 0.9f, hy0 - 10, hx0 - 3, hy0 - 14.8f, hx0 + 6, hy0 - 14.5f, hx0 + 0.5f, hy0 - 10.5f);
        float open = blinkOpen(t, seed, 1);
        eyes(4.7f, hy0 + 0.5f, 1.6f, open, 0, 0, skin);
        brows(hy0 - 4.0f, -1.2f, 0.2f, -1.2f, 0.2f, 1.2f, 0xFF7A5A33);   // earnest, a bit worried
        nose(skin);
        mouth(M_SOFT, hy0 + 8.6f, 0);
        pc(hx0 - 7.4f, hy0 + 4.6f, 1.5f, 0x22FF5A5A);
        pc(hx0 + 7.4f, hy0 + 4.6f, 1.5f, 0x22FF5A5A);
        // Clipboard.
        float bob = (float) Math.sin(t * 2.1f) * 0.4f;
        pr(14 + bob, 21 + br, 12, 12, 1.2f, 0xFF9A6A3E);
        pr(15.5f + bob, 22.5f + br, 9, 11, 0.6f, 0xFFF7F4EC);
        pl(17 + bob, 25.5f + br, 23 + bob, 25.5f + br, 0.7f, 0xFFB0B6C0);
        pl(17 + bob, 28 + br, 22 + bob, 28 + br, 0.7f, 0xFFB0B6C0);
        pr(18 + bob, 20.2f + br, 4, 2, 0.8f, 0xFF8A949E);
    }

    private static void neighbour(float t, float br, float hy, int seed) {
        int skin = 0xFFEAC2A0, hair = 0xFFC9C6C2, robe = 0xFF9C88C8;
        backdrop(0x40C8A8FF);
        setHead(11.8f, 13.0f, 0.4f, hy);
        // Curls: a cloud of circles.
        for (int i = 0; i < 9; i++) {
            double a = Math.toRadians(150 + i * 30);
            pc(hx0 + (float) Math.cos(a) * fw * 1.0f, hy0 - 2 + (float) Math.sin(a) * fh * 0.95f, 4.6f, Ui.darken(hair, 0.9f));
        }
        neck(skin, br);
        shoulders(28, br, robe);
        for (int i = 0; i < 5; i++) {
            float fx2 = -22 + i * 11, fy2 = 22 + (i % 2) * 6 + br;
            pc(fx2, fy2, 1.8f, 0xFFF4D36A);
            pc(fx2, fy2, 0.7f, 0xFFE07A3F);
        }
        poly(0xFFF3E6D2, -6.5f, 12.4f + br, 6.5f, 12.4f + br, 0, 19 + br);
        ears(skin);
        face(skin);
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(195 + i * 25);
            pc(hx0 + (float) Math.cos(a) * fw * 0.92f, hy0 - 1 + (float) Math.sin(a) * fh * 0.9f, 3.9f, hair);
        }
        // Peeking: eyes glance sideways now and then.
        float glance = (float) Math.sin(t * 0.8f) > 0.6f ? 0.6f : -0.3f;
        float open = blinkOpen(t, seed, 1);
        eyes(4.8f, hy0 + 1.0f, 1.9f, open, glance, 0, skin);
        g.strokeRoundRect(PX(-9.4f), PY(hy0 - 3.0f), 8.0f * pu, 8.0f * pu, 4 * pu, 1.0f * pu, 0xFFB23A6A);
        g.strokeRoundRect(PX(1.4f), PY(hy0 - 3.0f), 8.0f * pu, 8.0f * pu, 4 * pu, 1.0f * pu, 0xFFB23A6A);
        pl(-1.4f, hy0 + 0.5f, 1.4f, hy0 + 0.5f, 0.9f, 0xFFB23A6A);
        brows(hy0 - 4.6f, -1.2f, -0.6f, -1.2f, -0.6f, 1.0f, 0xFF9A9690);
        pl(hx0 - 8.5f, hy0 + 4.2f, hx0 - 6.8f, hy0 + 5.5f, 0.5f, mix(skin, 0xFF000000, 0.25f));
        pl(hx0 + 8.5f, hy0 + 4.2f, hx0 + 6.8f, hy0 + 5.5f, 0.5f, mix(skin, 0xFF000000, 0.25f));
        nose(skin);
        mouth(M_SMILE, hy0 + 8.4f, 0);
        // The cat she is holding.
        float ear = (float) Math.sin(t * 3.1f) > 0.95f ? 1.2f : 0;
        int cat = 0xFFE8944A;
        pc(-13, 27 + br, 7, cat);
        poly(cat, -19, 22 + br, -18.2f, 15.5f + br - ear, -14.5f, 20 + br);
        poly(cat, -7, 22 + br, -7.8f, 15.5f + br, -11.5f, 20 + br);
        pc(-15.4f, 25.5f + br, 1.0f, 0xFF2A2420);
        pc(-10.6f, 25.5f + br, 1.0f, 0xFF2A2420);
        pc(-13, 28 + br, 0.7f, 0xFFB0505A);
        pl(-20, 27.5f + br, -23, 27 + br, 0.4f, 0x99FFFFFF);
        pl(-6, 27.5f + br, -3, 27 + br, 0.4f, 0x99FFFFFF);
    }

    private static void dealer(float t, float br, float hy) {
        int body = 0xFF0C0E12, rim = 0xFF6E7A8C;
        backdrop(0x30605080);
        setHead(12.4f, 14f, 0.2f, hy);
        // Rim light first, then the silhouette slightly offset, so a lit edge stays on the left.
        float o = 0.9f;
        poly(rim, -31 - o, 33, -30 - o, 21 + br, -19 - o, 13 + br, -12 - o, 11 + br, -13 - o, -6 + hy, -9 - o, -19 + hy, 0, -24.5f + hy,
                9, -19 + hy, 13, -6 + hy, 12, 11 + br, 19, 13 + br, 30, 21 + br, 31, 33);
        poly(body, -31, 33, -30, 21 + br, -19, 13 + br, -12, 11 + br, -13, -6 + hy, -9, -19 + hy, 0, -24 + hy,
                9, -19 + hy, 13, -6 + hy, 12, 11 + br, 19, 13 + br, 30, 21 + br, 31 + o, 33);
        poly(0xFF050608, -8.5f, -5 + hy, -6.5f, -14 + hy, 0, -17 + hy, 6.5f, -14 + hy, 8.5f, -5 + hy, 5, 6 + hy, -5, 6 + hy);
        pl(-3, 13 + br, -4, 24 + br, 0.8f, 0xFF262A32);
        pl(3, 13 + br, 4, 24 + br, 0.8f, 0xFF262A32);
        // The glint: never a face.
        float tw = (float) Math.pow(Math.max(0, Math.sin(t * 1.6)), 6);
        star(3.5f, -7 + hy, 1.8f + tw * 3.6f, alpha(0xFFFFF1B0, 170 + (int) (tw * 85)));
        pc(3.5f, -7 + hy, 0.7f, 0xFFFFF1B0);
    }

    private static void cop(float t, float br, float hy, int seed) {
        int skin = 0xFFCC9670, hair = 0xFF3A2820, navy = 0xFF2B3A5C;
        backdrop(0x405A7AB0);
        setHead(11.3f, 13.4f, 0.5f, hy + 1);
        pc(hx0 - 7.5f, hy0 + 6, 4.2f, hair);                            // low bun
        neck(skin, br);
        shoulders(28, br, navy);
        poly(Ui.darken(navy, 1.25f), -6.5f, 12.2f + br, -0.6f, 16.5f + br, -3.4f, 19 + br, -9.2f, 14.6f + br);
        poly(Ui.darken(navy, 1.25f), 6.5f, 12.2f + br, 0.6f, 16.5f + br, 3.4f, 19 + br, 9.2f, 14.6f + br);
        pl(-26, 18 + br, -16, 15 + br, 2.2f, 0xFF1D2740);
        pl(26, 18 + br, 16, 15 + br, 2.2f, 0xFF1D2740);
        star(-12, 23 + br, 2.6f, 0xFFE8C547);
        pc(-12, 23 + br, 1.0f, 0xFFE8C547);
        pr(9, 21 + br, 6, 2, 0.6f, 0xFFD9DEE5);
        ears(skin);
        face(skin);
        hairCap(hair, 1.04f, 0, -6, -6.5f, 3);
        // Cap.
        poly(navy, hx0 - 12.5f, hy0 - 8.5f, hx0 - 13.5f, hy0 - 15, hx0 - 4, hy0 - 19, hx0 + 8, hy0 - 19, hx0 + 14, hy0 - 15,
                hx0 + 12.5f, hy0 - 8.5f);
        poly(0xFF141B2C, hx0 - 12.2f, hy0 - 9.6f, hx0 + 12.2f, hy0 - 9.6f, hx0 + 10, hy0 - 6.0f, hx0 - 10, hy0 - 6.0f);
        pl(hx0 - 12, hy0 - 10.8f, hx0 + 12, hy0 - 10.8f, 1.2f, 0xFFD9DEE5);
        star(hx0, hy0 - 14, 2.0f, 0xFFE8C547);
        // Tired: heavy lids, bags under the eyes, and a yawn every few seconds.
        float cyc = (t + (seed & 7)) % 7f;
        boolean yawn = cyc > 5.6f && cyc < 6.8f;
        float open = yawn ? 0.1f : blinkOpen(t, seed, 0.42f);
        eyes(4.6f, hy0 + 0.6f, 1.6f, open, 0, 0.3f, skin);
        pl(hx0 - 6.4f, hy0 + 3.2f, hx0 - 3.0f, hy0 + 3.4f, 0.8f, mix(skin, 0xFF4A2A40, 0.35f));
        pl(hx0 + 6.4f, hy0 + 3.2f, hx0 + 3.0f, hy0 + 3.4f, 0.8f, mix(skin, 0xFF4A2A40, 0.35f));
        brows(hy0 - 3.2f, -0.6f, 0.8f, -0.6f, 0.8f, 1.2f, 0xFF2A1C16);
        nose(skin);
        if (yawn) {
            float k = (float) Math.sin((cyc - 5.6f) / 1.2f * Math.PI);
            mouth(M_O, hy0 + 8.2f, k * 1.8f);
        } else {
            mouth(M_FLAT, hy0 + 8.6f, 0);
        }
    }

    private static void teacher(float t, float br, float hy, int seed) {
        int skin = 0xFF6E4630, hair = 0xFFD8D4CE, jacket = 0xFF8A6A4A;
        backdrop(0x40E7B04A);
        setHead(11.8f, 13.8f, 0.45f, hy);
        neck(skin, br);
        shoulders(30, br, jacket);
        poly(0xFFE9E6DF, -6.5f, 12.2f + br, 6.5f, 12.2f + br, 4, 33, -4, 33);
        poly(0xFFC9963A, -6, 16 + br, 6, 16 + br, 4, 33, -4, 33);              // vest
        poly(Ui.darken(jacket, 1.12f), -6.5f, 12.2f + br, -2, 33, -10, 33, -11, 16 + br);
        poly(Ui.darken(jacket, 1.12f), 6.5f, 12.2f + br, 2, 33, 10, 33, 11, 16 + br);
        poly(0xFF9A3A3A, -3.5f, 12.4f + br, 0, 14 + br, 3.5f, 12.4f + br, 3.5f, 16.4f + br, 0, 14.8f + br, -3.5f, 16.4f + br); // bow tie
        pc(-20, 17 + br, 1.5f, 0x55FFFFFF);                                       // chalk dust
        pc(-17, 19 + br, 1.0f, 0x55FFFFFF);
        ears(skin);
        face(skin);
        beard(hair, 5.2f, 0.9f);
        hairCap(hair, 1.04f, -2.5f, -10.5f, -11.8f, 1.5f);
        float open = blinkOpen(t, seed, 1);
        float look = (float) Math.sin(t * 0.6f) * 0.5f;
        eyes(4.7f, hy0 + 0.6f, 1.55f, open, look, -0.2f, skin);
        // Half-moon glasses low on the nose, looked over.
        g.strokeRoundRect(PX(hx0 - 8.0f), PY(hy0 + 2.0f), 5.8f * pu, 3.2f * pu, 1.5f * pu, 0.7f * pu, 0xFFE0B04A);
        g.strokeRoundRect(PX(hx0 + 2.2f), PY(hy0 + 2.0f), 5.8f * pu, 3.2f * pu, 1.5f * pu, 0.7f * pu, 0xFFE0B04A);
        pl(hx0 - 2.2f, hy0 + 2.8f, hx0 + 2.2f, hy0 + 2.8f, 0.7f, 0xFFE0B04A);
        brows(hy0 - 4.2f, -1.0f, 0.2f, -1.6f, -0.6f, 1.4f, hair);
        nose(skin);
        mouth(M_SMILE, hy0 + 8.6f, 0);
        poly(hair, hx0 - 4.4f, hy0 + 8.0f, hx0, hy0 + 6.6f, hx0 + 4.4f, hy0 + 8.0f, hx0 + 2.8f, hy0 + 8.4f, hx0 - 2.8f, hy0 + 8.4f);
    }

    private static void croupier(float t, float br, float hy, int seed) {
        int skin = 0xFFE6B48E, hair = 0xFF14100E, vest = 0xFF1A1C22;
        backdrop(0x40E8C547);
        setHead(11.2f, 13.4f, 0.55f, hy);
        pc(hx0, hy0 - 14.5f, 4.6f, hair);                                         // bun
        neck(skin, br);
        shoulders(28, br, 0xFFF4F1EA);
        poly(vest, -28, 33, -27.4f, 22 + br, -18.5f, 15 + br, -9, 16 + br, -2, 33);
        poly(vest, 28, 33, 27.4f, 22 + br, 18.5f, 15 + br, 9, 16 + br, 2, 33);
        poly(0xFFC8303F, -4, 12.6f + br, 0, 14.4f + br, 4, 12.6f + br, 4, 17 + br, 0, 15.2f + br, -4, 17 + br);
        ears(skin);
        face(skin);
        hairCap(hair, 1.05f, -1, -9, -11, 0.2f);
        poly(0x40FFFFFF, hx0 - 3, hy0 - 13.5f, hx0 - 1, hy0 - 14.2f, hx0 - 5, hy0 - 9, hx0 - 6.5f, hy0 - 9.3f);
        float open = blinkOpen(t, seed, 0.9f);
        eyes(4.5f, hy0 + 0.6f, 1.6f, open, 0, 0, skin);
        pl(hx0 - 6.5f, hy0 - 0.4f, hx0 - 7.6f, hy0 - 1.2f, 0.7f, EYE);
        pl(hx0 + 6.5f, hy0 - 0.4f, hx0 + 7.6f, hy0 - 1.2f, 0.7f, EYE);
        brows(hy0 - 3.7f, -0.2f, -0.8f, -0.2f, -0.8f, 1.1f, 0xFF14100E);
        nose(skin);
        mouth(M_SOFT, hy0 + 8.4f, 0);
        pc(hx0 + fw * 0.98f, hy0 + 4.2f, 0.6f, 0xFFE8C547);
        pc(hx0 - fw * 0.98f, hy0 + 4.2f, 0.6f, 0xFFE8C547);
        // A chip flipping between the fingers.
        float flip = (float) Math.abs(Math.cos(t * 3.2f));
        pr(13, 18 + br, 8, 8, 3.5f, skin);
        float chx = 17, chy = 15.5f + br;
        g.fillRoundRect(PX(chx - 4.0f), PY(chy - 4.0f * flip), 8.0f * pu, Math.max(0.8f, 8.0f * flip) * pu, 4.0f * flip * pu, 0xFFC8303F);
        if (flip > 0.35f) {
            g.strokeRoundRect(PX(chx - 2.6f), PY(chy - 2.6f * flip), 5.2f * pu, 5.2f * flip * pu, 2.6f * flip * pu, 0.7f * pu, 0xFFF4F1EA);
        }
    }

    private static void neutral(float t, float br, float hy, int seed) {
        int skin = 0xFFD9A884, hair = 0xFF3A2C24, tee = 0xFF5A6A82;
        backdrop(0x308A94A3);
        setHead(11.6f, 13.4f, 0.4f, hy);
        neck(skin, br);
        shoulders(29, br, tee);
        poly(mix(skin, 0xFF000000, 0.16f), -6.5f, 12.2f + br, 6.5f, 12.2f + br, 0, 16.5f + br);
        ears(skin);
        face(skin);
        hairCap(hair, 1.06f, -2, -8.5f, -9.5f, 2);
        float open = blinkOpen(t, seed, 1);
        eyes(4.6f, hy0 + 0.6f, 1.6f, open, 0, 0, skin);
        brows(hy0 - 3.8f, -0.4f, 0, -0.4f, 0, 1.2f, Ui.darken(hair, 0.8f));
        nose(skin);
        mouth(M_SOFT, hy0 + 8.4f, 0);
    }
}
