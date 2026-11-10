package com.wizzcraft.ima.core;

/**
 * The game's icon set, the building illustrations for menu headers and the title-screen art.
 *
 * One style throughout: bold flat polygons, light from the top left (lit faces and highlights on the
 * upper left, shade on the lower right), a single stroke weight per scale, and a restrained palette
 * that reads on {@link Ui#BG}. Small icons are designed on a 24-unit grid (art inside +-10) and use
 * at most about a dozen primitives; everything reuses scratch arrays so lists of icons allocate
 * nothing. Drawing is single-threaded, which is what makes the shared scratch state safe.
 */
final class Icons {
    private Icons() {
    }

    // ---- palette ----
    static final int GOLD = 0xFFFFC857, GOLD_D = 0xFFD9962B, GOLD_L = 0xFFFFE6A6;
    static final int RED = 0xFFFF6B6B, RED_D = 0xFFC94A52, RED_L = 0xFFFF9C96;
    static final int BLUE = 0xFF5AA9FF, BLUE_D = 0xFF3B76C4, BLUE_L = 0xFF9CCBFF;
    static final int GREEN = 0xFF5BD68A, GREEN_D = 0xFF2F9A5D, GREEN_L = 0xFFA3EDBE;
    static final int TEAL = 0xFF4FC9C0, TEAL_D = 0xFF2A8F8F, TEAL_L = 0xFFB4F4EE;
    static final int PURPLE = 0xFFC264D9, PURPLE_D = 0xFF7E3C9C;
    static final int ORANGE = 0xFFFF9F5A, ORANGE_D = 0xFFD0703A;
    static final int CREAM = 0xFFF3E6CC, CREAM_D = 0xFFD2C1A0;
    static final int STEEL = 0xFFC9D2DE, STEEL_D = 0xFF8994A5, STEEL_L = 0xFFEEF2F7;
    static final int SLATE = 0xFF3A4554, SLATE_D = 0xFF232B36;
    static final int BROWN = 0xFFC98B4F, BROWN_D = 0xFF8C5A2E, BROWN_L = 0xFFE7B57A;
    static final int GLASS = 0xFF8FD0FF, SKIN = 0xFFF2C9A1, WHITE = 0xFFF8FAFC;
    static final int WIN = 0xFFFFD98A, WIN_D = 0xFF2A3444;

    /** Icon names for job ids 0..5 (cook, waiter, baker, barman, gas attendant, cashier). */
    static final String[] JOB = {"job.cook", "job.waiter", "job.baker", "job.barman", "job.gas", "job.cashier"};

    /** Every name {@link #draw} knows; anything else draws a neutral placeholder. */
    static final String[] NAMES = {
            "meal", "water", "coin", "money", "gem", "key", "phone", "bag", "heart", "star", "clock", "lock",
            "bed", "tap", "bus", "bike", "scooter", "taxi", "book", "diploma", "chip", "cards", "dice",
            "package", "siren", "handcuffs", "fitness", "charisma", "education", "energy", "food", "heat",
            "rent", "sound", "music", "settings", "back", "check", "cross", "warning", "gift", "crown",
            "job.cook", "job.waiter", "job.baker", "job.barman", "job.gas", "job.cashier", "job.doctor",
            "job.lawyer", "job.dev", "home", "cart", "touch", "calendar",
    };

    // ---- current transform: grid point (x, y) lands at (ox + x*u, oy + y*u) ----
    private static float ox, oy, u = 1;
    private static int tint;

    private static final float[][] BUF = new float[48][];

    /** A reusable array for an n-point polygon. */
    private static float[] buf(int n) {
        float[] b = BUF[n];
        if (b == null) BUF[n] = b = new float[n * 2];
        return b;
    }

    private static float X(float x) {
        return ox + x * u;
    }

    private static float Y(float y) {
        return oy + y * u;
    }

    // =====================================================================================
    // Public API
    // =====================================================================================

    /**
     * Draws the icon called {@code name} centred on (cx, cy) in a size x size box.
     * tintOrZero != 0 redraws the icon in shades of that colour (for disabled or themed rows).
     * Unknown or null names draw a neutral placeholder; this never throws.
     */
    static void draw(Gfx c, String name, float cx, float cy, float size, int tintOrZero) {
        if (c == null || !(size > 0)) return;
        float sox = ox, soy = oy, su = u;
        int st = tint;
        ox = cx;
        oy = cy;
        u = size / 24f;
        tint = tintOrZero;
        try {
            icon(c, name == null ? "" : name);
        } finally {
            ox = sox;
            oy = soy;
            u = su;
            tint = st;
        }
    }

    /**
     * Draws building {@code id} (a {@link City} id) as a small scene centred on (cx, cy), {@code size}
     * units tall and about 1.4 times as wide. {@code t} is seconds and drives the idle animation.
     */
    static void building(Gfx c, int id, float cx, float cy, float size, float t) {
        if (c == null || !(size > 0)) return;
        float sox = ox, soy = oy, su = u;
        int st = tint;
        ox = cx;
        oy = cy;
        u = size / 70f;
        tint = 0;
        try {
            switch (id) {
                case City.SCHOOL -> school(c, t);
                case City.CASINO -> casino(c, t);
                case City.BAKERY -> bakery(c, t);
                case City.BAR -> bar(c, t);
                case City.RESTAURANT -> restaurant(c, t);
                case City.MARKET -> market(c, t);
                case City.GAS -> gas(c, t);
                case City.HOME -> home(c, t);
                case City.ALLEY -> alley(c, t);
                default -> {
                    ground(c);
                    box(c, -22, -12, 22, 29, 5, STEEL_D);
                    draw(c, "warning", ox, Y(8), 22 * u, 0);
                }
            }
        } finally {
            ox = sox;
            oy = soy;
            u = su;
            tint = st;
        }
    }

    // =====================================================================================
    // Primitive helpers in grid units
    // =====================================================================================

    private static void circ(Gfx c, float x, float y, float r, int col) {
        c.fillCircle(X(x), Y(y), r * u, tone(col));
    }

    private static void rect(Gfx c, float x, float y, float w, float h, int col) {
        c.fillRect(X(x), Y(y), w * u, h * u, tone(col));
    }

    private static void rrect(Gfx c, float x, float y, float w, float h, float r, int col) {
        c.fillRoundRect(X(x), Y(y), w * u, h * u, r * u, tone(col));
    }

    private static void srect(Gfx c, float x, float y, float w, float h, float r, float sw, int col) {
        c.strokeRoundRect(X(x), Y(y), w * u, h * u, r * u, sw * u, tone(col));
    }

    private static void ring(Gfx c, float x, float y, float r, float sw, int col) {
        srect(c, x - r, y - r, 2 * r, 2 * r, r, sw, col);
    }

    private static void ln(Gfx c, float x1, float y1, float x2, float y2, float sw, int col) {
        c.line(X(x1), Y(y1), X(x2), Y(y2), sw * u, tone(col));
    }

    /** Transforms a polygon written in grid units in place and fills it. */
    private static void poly(Gfx c, float[] p, int col) {
        for (int i = 0; i + 1 < p.length; i += 2) {
            p[i] = X(p[i]);
            p[i + 1] = Y(p[i + 1]);
        }
        c.fillPoly(p, tone(col));
    }

    private static void tri(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, int col) {
        float[] p = buf(3);
        p[0] = x0; p[1] = y0; p[2] = x1; p[3] = y1; p[4] = x2; p[5] = y2;
        poly(c, p, col);
    }

    private static void quad(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int col) {
        float[] p = buf(4);
        p[0] = x0; p[1] = y0; p[2] = x1; p[3] = y1; p[4] = x2; p[5] = y2; p[6] = x3; p[7] = y3;
        poly(c, p, col);
    }

    /** A w x h rectangle centred on (x, y), turned by deg degrees. */
    private static void rquad(Gfx c, float x, float y, float w, float h, float deg, int col) {
        float a = (float) Math.toRadians(deg), ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float hx = w / 2, hy = h / 2;
        quad(c, x - hx * ca + hy * sa, y - hx * sa - hy * ca, x + hx * ca + hy * sa, y + hx * sa - hy * ca,
                x + hx * ca - hy * sa, y + hx * sa + hy * ca, x - hx * ca - hy * sa, y - hx * sa + hy * ca, col);
    }

    /** A band of a ring (radius r, thickness th) from a0 to a1 degrees, clockwise on screen. */
    private static void arc(Gfx c, float x, float y, float r, float th, float a0, float a1, int col) {
        final int seg = 8;
        float[] p = buf(2 * (seg + 1));
        float ro = r + th / 2, ri = r - th / 2;
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / seg);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            p[2 * i] = x + ca * ro;
            p[2 * i + 1] = y + sa * ro;
            int j = 2 * seg + 1 - i;
            p[2 * j] = x + ca * ri;
            p[2 * j + 1] = y + sa * ri;
        }
        poly(c, p, col);
    }

    /** An n-point star (alternating outer and inner radius), first point straight up. */
    private static void star(Gfx c, float x, float y, int n, float ro, float ri, int col) {
        float[] p = buf(2 * n);
        for (int i = 0; i < 2 * n; i++) {
            double a = -Math.PI / 2 + Math.PI * i / n;
            float r = (i & 1) == 0 ? ro : ri;
            p[2 * i] = x + (float) Math.cos(a) * r;
            p[2 * i + 1] = y + (float) Math.sin(a) * r;
        }
        poly(c, p, col);
    }

    /** An ellipse as a 16-gon (used where a circle would look too round). */
    private static void oval(Gfx c, float x, float y, float rx, float ry, int col) {
        float[] p = buf(16);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            p[2 * i] = x + (float) Math.cos(a) * rx;
            p[2 * i + 1] = y + (float) Math.sin(a) * ry;
        }
        poly(c, p, col);
    }

    // ---- colour ----

    private static int alpha(int col, int a) {
        return (col & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    private static int mix(int a, int b, float k) {
        k = k < 0 ? 0 : (k > 1 ? 1 : k);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * k), g = (int) (ag + (bg - ag) * k), bl = (int) (ab + (bb - ab) * k);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    /** With a tint set, maps a colour to the tint's shade of the same brightness; otherwise identity. */
    private static int tone(int col) {
        if (tint == 0) return col;
        int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
        float lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f;
        // A typical body colour (about 0.6 bright) becomes the tint itself.
        float k = lum / 0.6f;
        int out = k <= 1 ? Ui.darken(tint | 0xFF000000, Math.max(0.3f, k)) : mix(tint | 0xFF000000, 0xFFFFFFFF, (k - 1) * 0.9f);
        int a = (int) (((col >>> 24) & 0xFF) * (((tint >>> 24) & 0xFF) / 255f));
        return (out & 0x00FFFFFF) | (a << 24);
    }

    /** Deterministic pseudo-random value in [0, 1) from two integers. */
    static float hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    // =====================================================================================
    // Icons (24-unit grid, art within +-10, light from the top left)
    // =====================================================================================

    private static final float SW = 2.4f;

    private static void icon(Gfx c, String n) {
        switch (n) {
            case "meal" -> meal(c);
            case "water" -> water(c);
            case "coin" -> coin(c, 0, 0, 1);
            case "money" -> money(c);
            case "gem" -> gem(c);
            case "key" -> key(c);
            case "phone" -> phone(c);
            case "bag" -> bag(c);
            case "heart" -> heart(c, 0, 0, 1, RED);
            case "star" -> starIcon(c);
            case "clock" -> clock(c);
            case "lock" -> lock(c);
            case "bed" -> bed(c);
            case "tap" -> tapIcon(c);
            case "bus" -> bus(c);
            case "bike" -> bike(c);
            case "scooter" -> scooter(c);
            case "taxi" -> taxi(c);
            case "book" -> book(c);
            case "diploma" -> diploma(c);
            case "chip" -> chip(c);
            case "cards" -> cards(c);
            case "dice" -> dice(c);
            case "package" -> pack(c);
            case "siren" -> siren(c);
            case "handcuffs" -> handcuffs(c);
            case "fitness" -> fitness(c);
            case "charisma" -> charisma(c);
            case "education" -> education(c);
            case "energy" -> energy(c);
            case "food" -> food(c);
            case "heat" -> heat(c);
            case "rent" -> house(c, true);
            case "home" -> house(c, false);
            case "sound" -> sound(c);
            case "music" -> music(c);
            case "settings" -> settings(c);
            case "back" -> back(c);
            case "check" -> check(c);
            case "cross" -> cross(c);
            case "warning" -> warning(c);
            case "gift" -> gift(c);
            case "crown" -> crown(c);
            case "cart" -> cart(c);
            case "touch" -> touch(c);
            case "calendar" -> calendar(c);
            case "job.cook" -> cook(c);
            case "job.waiter" -> waiter(c);
            case "job.baker" -> loaf(c);
            case "job.barman" -> cocktail(c);
            case "job.gas" -> pump(c);
            case "job.cashier" -> register(c);
            case "job.doctor" -> stethoscope(c);
            case "job.lawyer" -> scales(c);
            case "job.dev" -> laptop(c);
            default -> placeholder(c);
        }
    }

    private static void placeholder(Gfx c) {
        srect(c, -8, -8, 16, 16, 4, 2, STEEL_D);
        circ(c, 0, 0, 2.2f, STEEL_D);
    }

    private static void meal(Gfx c) {
        // two soft S-shaped wisps of steam
        for (int i = -1; i <= 1; i += 2) {
            float x = i * 3.4f;
            arc(c, x, -9.4f, 1.7f, 1.5f, -90, 90, 0x99EEF2F7);
            arc(c, x, -6.0f, 1.7f, 1.5f, 90, 270, 0x99EEF2F7);
        }
        circ(c, 0, 1.6f, 7.4f, ORANGE);
        circ(c, -2.8f, -3, 1.9f, GREEN);
        circ(c, 2.6f, -3.6f, 1.7f, RED);
        float[] p = buf(6);
        p[0] = -10.5f; p[1] = 0.5f; p[2] = 10.5f; p[3] = 0.5f; p[4] = 8.8f; p[5] = 5.8f;
        p[6] = 5; p[7] = 9.2f; p[8] = -5; p[9] = 9.2f; p[10] = -8.8f; p[11] = 5.8f;
        poly(c, p, BLUE);
        quad(c, 3, 0.5f, 10.5f, 0.5f, 8.8f, 5.8f, 4.2f, 9.2f, BLUE_D);
        ln(c, -10.5f, 0.6f, 10.5f, 0.6f, 1.8f, BLUE_L);
    }

    private static void water(Gfx c) {
        tri(c, 0, -11, -6.6f, -0.2f, 6.6f, -0.2f, BLUE);
        circ(c, 0, 3.4f, 7.6f, BLUE);
        arc(c, 0, 3.4f, 7.6f, 2.2f, 10, 100, BLUE_D);
        arc(c, 0, 3.4f, 4.6f, 1.8f, 150, 215, BLUE_L);
        circ(c, -2.6f, -2.8f, 1.2f, BLUE_L);
    }

    private static void coin(Gfx c, float x, float y, float k) {
        circ(c, x + 0.7f * k, y + 0.9f * k, 9.8f * k, GOLD_D);
        circ(c, x, y, 9.6f * k, GOLD);
        ring(c, x, y, 6.3f * k, 1.6f * k, GOLD_D);
        ln(c, x - 1.7f * k, y - 2.8f * k, x - 1.7f * k, y + 2.8f * k, 1.7f * k, GOLD_D);
        ln(c, x + 1.7f * k, y - 2.8f * k, x + 1.7f * k, y + 2.8f * k, 1.7f * k, GOLD_D);
        arc(c, x, y, 8, 1.4f * k, 195, 255, GOLD_L);
    }

    private static void money(Gfx c) {
        rquad(c, 0.8f, -2.6f, 19, 10.5f, -14, GREEN_D);
        rrect(c, -10, -3.2f, 20, 11.4f, 1.6f, GREEN);
        srect(c, -8.2f, -1.5f, 16.4f, 8, 1, 1.3f, GREEN_D);
        circ(c, 0, 2.5f, 2.6f, GREEN_L);
        ln(c, -9.2f, -2.4f, 9.2f, -2.4f, 1, GREEN_L);
    }

    private static void gem(Gfx c) {
        quad(c, -5.5f, -7.5f, 5.5f, -7.5f, 10.5f, -2, -10.5f, -2, TEAL);
        quad(c, -2.6f, -7.5f, 2.6f, -7.5f, 4, -2, -4, -2, TEAL_L);
        tri(c, -10.5f, -2, 10.5f, -2, 0, 10.5f, TEAL);
        tri(c, -10.5f, -2, -4, -2, 0, 10.5f, TEAL_L);
        tri(c, 4, -2, 10.5f, -2, 0, 10.5f, TEAL_D);
        star(c, -6.5f, -8.8f, 4, 2.8f, 0.7f, WHITE);
    }

    private static void key(Gfx c) {
        ring(c, -4.6f, -4.6f, 4.3f, 3, GOLD);
        ln(c, -1.6f, -1.6f, 8.6f, 8.6f, 3, GOLD);
        ln(c, 4.8f, 4.8f, 7.2f, 2.4f, 2.6f, GOLD);
        ln(c, 7.6f, 7.6f, 10, 5.2f, 2.6f, GOLD);
        arc(c, -4.6f, -4.6f, 4.3f, 1.1f, 190, 260, GOLD_L);
    }

    private static void phone(Gfx c) {
        rrect(c, -6.5f, -10.5f, 13, 21, 2.8f, STEEL);
        rect(c, 3.5f, -9, 3, 18, STEEL_D);
        rrect(c, -4.8f, -7.6f, 9.6f, 14, 1, SLATE_D);
        quad(c, -4.8f, -7.6f, 0.5f, -7.6f, -4.8f, 0, -4.8f, 0, 0x335AA9FF);
        ln(c, -1.6f, -9.1f, 1.6f, -9.1f, 1, STEEL_D);
        circ(c, 0, 8.3f, 1, STEEL_D);
        rrect(c, -3, -5.4f, 6, 2, 1, GREEN);
    }

    private static void bag(Gfx c) {
        srect(c, -4.2f, -10.5f, 8.4f, 12, 4.2f, 2.2f, BROWN_D);
        quad(c, -8, -4, 8, -4, 9.6f, 10.2f, -9.6f, 10.2f, ORANGE);
        quad(c, 3.6f, -4, 8, -4, 9.6f, 10.2f, 4.4f, 10.2f, ORANGE_D);
        ln(c, -8, -3.4f, 8, -3.4f, 1.6f, 0xFFFFC08F);
        circ(c, -4.2f, -0.6f, 1, BROWN_D);
        circ(c, 4.2f, -0.6f, 1, BROWN_D);
    }

    private static void heart(Gfx c, float x, float y, float k, int col) {
        circ(c, x - 4.7f * k, y - 3 * k, 5.4f * k, col);
        circ(c, x + 4.7f * k, y - 3 * k, 5.4f * k, col);
        tri(c, x - 9.9f * k, y - 1.6f * k, x + 9.9f * k, y - 1.6f * k, x, y + 9.6f * k, col);
        if (k > 0.6f) arc(c, x - 4.7f * k, y - 3 * k, 3.3f * k, 1.6f * k, 185, 265, mix(col, 0xFFFFFFFF, 0.45f));
    }

    private static void starIcon(Gfx c) {
        star(c, 0, 1, 5, 10.8f, 4.6f, GOLD);
        // shade the right half of each arm: a cheap facet that sells the light direction
        float[] p = buf(3);
        for (int i = 0; i < 5; i++) {
            double ao = -Math.PI / 2 + Math.PI * 2 * i / 5, ai = ao + Math.PI / 5;
            float ox1 = (float) Math.cos(ao) * 10.8f, oy1 = 1 + (float) Math.sin(ao) * 10.8f;
            float ix = (float) Math.cos(ai) * 4.6f, iy = 1 + (float) Math.sin(ai) * 4.6f;
            p = buf(3);
            p[0] = 0; p[1] = 1; p[2] = ox1; p[3] = oy1; p[4] = ix; p[5] = iy;
            poly(c, p, i == 0 || i == 1 || i == 2 ? GOLD_D : 0x55D9962B);
        }
    }

    private static void clock(Gfx c) {
        circ(c, 0, 0, 10.4f, STEEL);
        circ(c, 0, 0, 8.2f, CREAM);
        arc(c, 0, 0, 9.3f, 2.2f, -20, 110, STEEL_D);
        ln(c, 0, -6.6f, 0, -5.2f, 1.6f, STEEL_D);
        ln(c, 6.6f, 0, 5.2f, 0, 1.6f, STEEL_D);
        ln(c, 0, 0, 0, -5.6f, 2, SLATE);
        ln(c, 0, 0, 3.8f, 2.2f, 2.4f, SLATE);
        circ(c, 0, 0, 1.4f, RED);
    }

    private static void lock(Gfx c) {
        srect(c, -5, -11, 10, 13, 5, 2.8f, STEEL);
        rrect(c, -8.5f, -3, 17, 13.5f, 2.2f, GOLD);
        rect(c, 3.5f, -1.8f, 3.8f, 11, GOLD_D);
        ln(c, -7, -1.4f, 2.5f, -1.4f, 1.2f, GOLD_L);
        circ(c, 0, 2.6f, 2, SLATE_D);
        tri(c, -1.3f, 2.8f, 1.3f, 2.8f, 0, 7.2f, SLATE_D);
    }

    private static void bed(Gfx c) {
        rrect(c, -11, -6, 3.4f, 16, 1.2f, BROWN);
        rrect(c, 8.6f, -1, 2.6f, 11, 1, BROWN_D);
        rect(c, -9, 3.6f, 19, 2.6f, BROWN_D);
        rrect(c, -8.2f, -0.6f, 17.4f, 4.6f, 1.2f, CREAM);
        rrect(c, -2.2f, -1.8f, 12, 5.8f, 1.6f, BLUE);
        ln(c, -1.6f, -1.2f, 9, -1.2f, 1, BLUE_L);
        rrect(c, -7.6f, -3.6f, 5.4f, 3.6f, 1.6f, WHITE);
    }

    private static void tapIcon(Gfx c) {
        rect(c, -11, -6, 2.6f, 10, STEEL_D);
        rrect(c, -9, -3.4f, 13.6f, 5, 2, STEEL);
        rrect(c, 1.4f, -3.4f, 4, 7.4f, 1.2f, STEEL);
        ln(c, -3, -3.4f, -3, -7.6f, 2.2f, STEEL_D);
        ln(c, -6.4f, -8, 0.4f, -8, 2.6f, BLUE);
        ln(c, -8.6f, -2.2f, 3, -2.2f, 1, STEEL_L);
        tri(c, 3.4f, 5.4f, 1.6f, 8.6f, 5.2f, 8.6f, BLUE);
        circ(c, 3.4f, 8.9f, 1.85f, BLUE);
    }

    private static void bus(Gfx c) {
        rrect(c, -8.5f, -10.5f, 17, 18.5f, 3, GREEN);
        rect(c, 4.6f, -9, 3.9f, 16, GREEN_D);
        rrect(c, -6.8f, -7.4f, 13.6f, 7.4f, 1.2f, GLASS);
        quad(c, -6.8f, -7.4f, -1, -7.4f, -6.8f, -2, -6.8f, -2, 0x66FFFFFF);
        rect(c, -4.5f, -9.6f, 9, 1.4f, GOLD);
        circ(c, -5, 3.2f, 1.4f, GOLD_L);
        circ(c, 5, 3.2f, 1.4f, GOLD_L);
        rrect(c, -7.2f, 7, 3.6f, 3.4f, 1, SLATE);
        rrect(c, 3.6f, 7, 3.6f, 3.4f, 1, SLATE);
    }

    private static void bike(Gfx c) {
        ring(c, -6.2f, 4, 4.6f, 1.8f, STEEL);
        ring(c, 6.2f, 4, 4.6f, 1.8f, STEEL);
        ln(c, -6.2f, 4, -3, -3, 1.9f, RED);
        ln(c, -3, -3, 4.2f, -3, 1.9f, RED);
        ln(c, 4.2f, -3, 6.2f, 4, 1.9f, RED);
        ln(c, -6.2f, 4, -0.4f, 4, 1.9f, RED);
        ln(c, -0.4f, 4, 4.2f, -3, 1.9f, RED);
        ln(c, -0.4f, 4, -3, -3, 1.9f, RED);
        ln(c, -5, -5, -1.8f, -5, 2.2f, SLATE);
        ln(c, 4.2f, -3, 3.4f, -6.4f, 1.8f, STEEL_D);
        ln(c, 1.8f, -6.4f, 5.6f, -6.4f, 2, STEEL_D);
    }

    private static void scooter(Gfx c) {
        circ(c, -7.5f, 6.6f, 3.2f, STEEL);
        circ(c, 7.5f, 6.6f, 3.2f, STEEL);
        circ(c, -7.5f, 6.6f, 1.2f, SLATE);
        circ(c, 7.5f, 6.6f, 1.2f, SLATE);
        ln(c, 7.5f, 6.6f, 4.4f, -9, 2.4f, ORANGE);
        rrect(c, -9, 3.6f, 14.5f, 2.6f, 1.3f, ORANGE);
        ln(c, -8.5f, 4.2f, 4, 4.2f, 0.9f, 0xFFFFC9A0);
        ln(c, 2.4f, -9.2f, 6.8f, -9.2f, 2.4f, STEEL_D);
    }

    private static void taxi(Gfx c) {
        quad(c, -6.2f, -1, -3.8f, -6.6f, 4.4f, -6.6f, 7.4f, -1, GOLD);
        rrect(c, -11, -1.6f, 22, 7, 2.4f, GOLD);
        rect(c, -11, 2.6f, 22, 1.4f, GOLD_D);
        quad(c, -4.8f, -1.6f, -3, -5.4f, -0.4f, -5.4f, -0.4f, -1.6f, GLASS);
        quad(c, 0.6f, -1.6f, 0.6f, -5.4f, 3.8f, -5.4f, 6, -1.6f, GLASS);
        rrect(c, -2.2f, -9, 4.4f, 2.4f, 0.8f, WHITE);
        circ(c, -6, 5.6f, 2.9f, SLATE);
        circ(c, 6, 5.6f, 2.9f, SLATE);
        circ(c, -6, 5.6f, 1.2f, STEEL);
        circ(c, 6, 5.6f, 1.2f, STEEL);
        rect(c, 9.4f, -0.4f, 1.6f, 1.6f, GOLD_L);
    }

    private static void book(Gfx c) {
        rrect(c, -7.5f, -10.5f, 16, 19.5f, 1.6f, RED);
        rect(c, -6, 8, 14.5f, 2.6f, CREAM);
        ln(c, -6, 9.3f, 8.4f, 9.3f, 0.7f, CREAM_D);
        rect(c, -9, -10.5f, 3, 21, RED_D);
        rect(c, -3, -6.5f, 8.5f, 2.4f, GOLD);
        rect(c, -3, -3, 5.5f, 1.4f, GOLD);
    }

    private static void diploma(Gfx c) {
        rquad(c, 0, 0, 20, 7, -35, CREAM);
        rquad(c, 1.3f, 1.8f, 20, 2.4f, -35, CREAM_D);
        circ(c, -8.2f, 5.7f, 3.5f, CREAM_D);
        circ(c, -8.2f, 5.7f, 1.4f, BROWN);
        rquad(c, 0, 0, 3, 8, -35, RED);
        tri(c, 0.4f, 2.4f, -1.4f, 9.4f, 1.6f, 8.2f, RED);
        tri(c, 0.4f, 2.4f, 3.6f, 8.6f, 5.4f, 6.2f, RED_D);
    }

    private static void chip(Gfx c) {
        circ(c, 0, 0, 10.2f, RED);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * i / 4 + Math.PI / 8;
            float dx = (float) Math.cos(a) * 8.9f, dy = (float) Math.sin(a) * 8.9f;
            ln(c, -dx, -dy, dx, dy, 2.6f, WHITE);
        }
        circ(c, 0, 0, 6.6f, RED);
        ring(c, 0, 0, 5, 1, 0xCCFFFFFF);
        arc(c, 0, 0, 8.3f, 1.2f, 200, 250, 0x66FFFFFF);
    }

    private static void cards(Gfx c) {
        rquad(c, -2.6f, -0.6f, 11.5f, 16, -14, BLUE);
        rquad(c, -2.6f, -0.6f, 8, 12.4f, -14, BLUE_D);
        rquad(c, 2.6f, 0.6f, 11.5f, 16, 10, WHITE);
        heart(c, 2.9f, 1.4f, 0.36f, RED);
        circ(c, -0.8f, -5.6f, 0.9f, RED);
    }

    private static void dice(Gfx c) {
        quad(c, 0, -10.5f, 9.6f, -5.6f, 0, -0.8f, -9.6f, -5.6f, WHITE);
        quad(c, -9.6f, -5.6f, 0, -0.8f, 0, 10.6f, -9.6f, 5.6f, 0xFFDCE2EA);
        quad(c, 0, -0.8f, 9.6f, -5.6f, 9.6f, 5.6f, 0, 10.6f, 0xFFA9B4C3);
        circ(c, 0, -5.6f, 1.7f, RED);
        circ(c, -6.6f, -1.6f, 1.25f, SLATE);
        circ(c, -3, 5.4f, 1.25f, SLATE);
        circ(c, 2.6f, 5.8f, 1.2f, SLATE);
        circ(c, 4.8f, 2.4f, 1.2f, SLATE);
        circ(c, 7, -1, 1.2f, SLATE);
    }

    private static void pack(Gfx c) {
        quad(c, 0, -10, 10.4f, -5, 0, 0, -10.4f, -5, BROWN_L);
        quad(c, -10.4f, -5, 0, 0, 0, 10.6f, -10.4f, 5.6f, BROWN);
        quad(c, 0, 0, 10.4f, -5, 10.4f, 5.6f, 0, 10.6f, BROWN_D);
        quad(c, -6.6f, -7.4f, 3.8f, -2.4f, 6.6f, -3.8f, -3.8f, -8.8f, 0xFFF2D7A4);
        quad(c, 3.8f, -2.4f, 6.6f, -3.8f, 6.6f, 6.6f, 3.8f, 8, 0xFFC9A673);
        ln(c, -8.6f, 0.6f, -2.2f, 3.8f, 1.2f, BROWN_D);
    }

    private static void siren(Gfx c) {
        float[] p = buf(6);
        p[0] = -7.2f; p[1] = 5; p[2] = -7.2f; p[3] = -1; p[4] = -5.6f; p[5] = -5.2f;
        p[6] = -2.6f; p[7] = -7.4f; p[8] = 0; p[9] = -7.8f; p[10] = 0; p[11] = 5;
        poly(c, p, RED);
        p = buf(6);
        p[0] = 7.2f; p[1] = 5; p[2] = 7.2f; p[3] = -1; p[4] = 5.6f; p[5] = -5.2f;
        p[6] = 2.6f; p[7] = -7.4f; p[8] = 0; p[9] = -7.8f; p[10] = 0; p[11] = 5;
        poly(c, p, BLUE);
        quad(c, -5, -1, -4.2f, -4.2f, -2.6f, -5.4f, -3, -1, 0x99FFFFFF);
        rrect(c, -9.6f, 4.6f, 19.2f, 4.6f, 1.4f, STEEL);
        ln(c, -11, -7.4f, -9, -5.4f, 1.8f, GOLD_L);
        ln(c, 11, -7.4f, 9, -5.4f, 1.8f, GOLD_L);
        ln(c, 0, -11.4f, 0, -9.6f, 1.8f, GOLD_L);
    }

    private static void handcuffs(Gfx c) {
        ln(c, -3.4f, -5, 3.6f, -5.6f, 1.6f, STEEL_D);
        ring(c, -5, 3, 5, 2.6f, STEEL);
        ring(c, 5.2f, 2.2f, 5, 2.6f, STEEL);
        rrect(c, -6.8f, -4.4f, 3.6f, 3.4f, 1, STEEL_D);
        rrect(c, 3.4f, -5.2f, 3.6f, 3.4f, 1, STEEL_D);
        arc(c, -5, 3, 5, 1, 190, 250, STEEL_L);
        arc(c, 5.2f, 2.2f, 5, 1, 190, 250, STEEL_L);
    }

    private static void fitness(Gfx c) {
        ln(c, -10, 0, 10, 0, 2.4f, STEEL_D);
        rrect(c, -7.6f, -6.6f, 3.6f, 13.2f, 1.2f, RED);
        rrect(c, 4, -6.6f, 3.6f, 13.2f, 1.2f, RED);
        rrect(c, -10.6f, -4.4f, 2.8f, 8.8f, 1, RED_D);
        rrect(c, 7.8f, -4.4f, 2.8f, 8.8f, 1, RED_D);
        ln(c, -6.8f, -5.2f, -6.8f, 1, 1, RED_L);
        ln(c, 4.8f, -5.2f, 4.8f, 1, 1, RED_L);
    }

    private static void charisma(Gfx c) {
        circ(c, -1, 1.4f, 9, GOLD);
        arc(c, -1, 1.4f, 9, 2, 0, 90, GOLD_D);
        rrect(c, -8.6f, -2.4f, 6.8f, 4.6f, 2, SLATE_D);
        rrect(c, -0.2f, -2.4f, 6.8f, 4.6f, 2, SLATE_D);
        ln(c, -2.2f, -1.6f, 0.2f, -1.6f, 1.2f, SLATE_D);
        arc(c, -1, 2.4f, 4.4f, 1.6f, 25, 155, SLATE_D);
        star(c, 7.8f, -7.6f, 4, 3.6f, 0.9f, WHITE);
    }

    private static void education(Gfx c) {
        float[] p = buf(5);
        p[0] = -6.6f; p[1] = -2.6f; p[2] = 6.6f; p[3] = -2.6f; p[4] = 6.6f; p[5] = 3.4f;
        p[6] = 0; p[7] = 5.6f; p[8] = -6.6f; p[9] = 3.4f;
        poly(c, p, BLUE_D);
        quad(c, 0, -10, 11.4f, -5, 0, 0, -11.4f, -5, BLUE);
        tri(c, 0, -10, -11.4f, -5, 0, -5, BLUE_L);
        ln(c, 0, -5, 8.4f, -3, 1.2f, GOLD);
        ln(c, 8.4f, -3, 8.4f, 4.2f, 1.4f, GOLD);
        rrect(c, 7.2f, 3.8f, 2.4f, 3.6f, 0.8f, GOLD);
    }

    private static void energy(Gfx c) {
        float[] p = buf(6);
        p[0] = 4.4f; p[1] = -11.4f; p[2] = -8.2f; p[3] = 2.2f; p[4] = -0.8f; p[5] = 2.2f;
        p[6] = -4.4f; p[7] = 11.4f; p[8] = 8.2f; p[9] = -2.4f; p[10] = 0.8f; p[11] = -2.4f;
        poly(c, p, GOLD);
        tri(c, -4.4f, 11.4f, 8.2f, -2.4f, 3.6f, -2.4f, GOLD_D);
        tri(c, 4.4f, -11.4f, -8.2f, 2.2f, -4.8f, 2.2f, GOLD_L);
    }

    private static void food(Gfx c) {
        // fork
        ln(c, -4.6f, -10, -4.6f, 10, 2.2f, STEEL);
        ln(c, -7.4f, -10, -7.4f, -4.6f, 1.6f, STEEL);
        ln(c, -1.8f, -10, -1.8f, -4.6f, 1.6f, STEEL);
        arc(c, -4.6f, -5.4f, 2.8f, 1.8f, 0, 180, STEEL);
        // knife
        float[] p = buf(5);
        p[0] = 4.2f; p[1] = -10.4f; p[2] = 7.8f; p[3] = -6; p[4] = 7.4f; p[5] = 1.4f;
        p[6] = 4.2f; p[7] = 1.4f; p[8] = 4.2f; p[9] = -10.4f;
        poly(c, p, STEEL_L);
        quad(c, 6.2f, -7.6f, 7.8f, -6, 7.4f, 1.4f, 6.2f, 1.4f, STEEL_D);
        rrect(c, 3.8f, 1, 3.4f, 9.6f, 1.6f, ORANGE);
    }

    private static void heat(Gfx c) {
        float[] p = buf(10);
        p[0] = 0.6f; p[1] = -11; p[2] = 5.8f; p[3] = -4.6f; p[4] = 8.4f; p[5] = 1;
        p[6] = 7.6f; p[7] = 6.2f; p[8] = 4; p[9] = 10; p[10] = -2.6f; p[11] = 10.6f;
        p[12] = -7.2f; p[13] = 7.4f; p[14] = -8.4f; p[15] = 2; p[16] = -6.4f; p[17] = -3.4f;
        p[18] = -3.4f; p[19] = 0;
        poly(c, p, 0xFFFF7448);
        p = buf(7);
        p[0] = 0.4f; p[1] = -2.4f; p[2] = 4; p[3] = 2.4f; p[4] = 4.4f; p[5] = 6.4f;
        p[6] = 1.6f; p[7] = 9.4f; p[8] = -2.6f; p[9] = 9.4f; p[10] = -4.6f; p[11] = 5.6f;
        p[12] = -3; p[13] = 2.4f;
        poly(c, p, GOLD);
        quad(c, 0.6f, -11, 5.8f, -4.6f, 3.6f, -3.8f, 0.2f, -8, 0x66FFFFFF);
    }

    private static void house(Gfx c, boolean coin) {
        rect(c, -7.6f, -2.4f, 15.2f, 11.6f, 0xFFA9B6CC);
        rect(c, 3.4f, -2.4f, 4.2f, 11.6f, 0xFF7E8BA3);
        tri(c, -10.6f, -1.4f, 0, -10.8f, 10.6f, -1.4f, RED);
        tri(c, 0, -10.8f, 10.6f, -1.4f, 4, -1.4f, RED_D);
        rrect(c, -3.4f, 2.4f, 4.6f, 6.8f, 1, SLATE);
        rect(c, 4.2f, -8.4f, 2.4f, 3.6f, 0xFF7E8BA3);
        if (coin) {
            circ(c, 6.9f, 6.9f, 5, SLATE_D);
            circ(c, 6.6f, 6.6f, 4.3f, GOLD);
            ring(c, 6.6f, 6.6f, 2.6f, 1.1f, GOLD_D);
        } else {
            rect(c, -6, 0, 2.2f, 2.2f, WIN);
        }
    }

    private static void sound(Gfx c) {
        float[] p = buf(6);
        p[0] = -10; p[1] = -3.4f; p[2] = -5.4f; p[3] = -3.4f; p[4] = 0.8f; p[5] = -9;
        p[6] = 0.8f; p[7] = 9; p[8] = -5.4f; p[9] = 3.4f; p[10] = -10; p[11] = 3.4f;
        poly(c, p, STEEL);
        arc(c, 1.4f, 0, 4.6f, 2.2f, -48, 48, STEEL);
        arc(c, 1.4f, 0, 9, 2.2f, -52, 52, STEEL);
    }

    private static void music(Gfx c) {
        oval(c, -5.6f, 6.4f, 3.6f, 2.8f, STEEL);
        oval(c, 5.6f, 4, 3.6f, 2.8f, STEEL);
        ln(c, -2.5f, 6, -2.5f, -6.2f, 2, STEEL);
        ln(c, 8.7f, 3.6f, 8.7f, -8.8f, 2, STEEL);
        quad(c, -3.5f, -6.6f, 9.7f, -10.4f, 9.7f, -6.6f, -3.5f, -2.8f, STEEL);
    }

    private static void settings(Gfx c) {
        final int teeth = 8;
        float[] p = buf(teeth * 4);
        double step = Math.PI * 2 / teeth;
        for (int i = 0; i < teeth * 4; i++) {
            // per tooth: root, tip, tip, root
            int q = i % 4;
            boolean tip = q == 1 || q == 2;
            double ang = step * (i / 4) + (q - 1.5) * step * (tip ? 0.16 : 0.3);
            float r = tip ? 10.6f : 7.8f;
            p[2 * i] = (float) Math.cos(ang) * r;
            p[2 * i + 1] = (float) Math.sin(ang) * r;
        }
        poly(c, p, STEEL);
        circ(c, 0, 0, 5.4f, STEEL_D);
        circ(c, -0.4f, -0.4f, 4.6f, STEEL);
        circ(c, 0, 0, 2.6f, SLATE);
    }

    private static void back(Gfx c) {
        // Hebrew reads right to left, so "back" points right.
        ln(c, -8.6f, 0, 8.6f, 0, 2.8f, STEEL);
        ln(c, 8.6f, 0, 2.4f, -6.2f, 2.8f, STEEL);
        ln(c, 8.6f, 0, 2.4f, 6.2f, 2.8f, STEEL);
    }

    private static void check(Gfx c) {
        ln(c, -8.4f, 0.8f, -3, 6.4f, 3.4f, GREEN);
        ln(c, -3, 6.4f, 8.6f, -6.4f, 3.4f, GREEN);
    }

    private static void cross(Gfx c) {
        ln(c, -7, -7, 7, 7, 3.4f, RED);
        ln(c, 7, -7, -7, 7, 3.4f, RED);
    }

    private static void warning(Gfx c) {
        float[] p = buf(6);
        p[0] = -1.6f; p[1] = -9.8f; p[2] = 1.6f; p[3] = -9.8f; p[4] = 11.2f; p[5] = 7.4f;
        p[6] = 9.6f; p[7] = 9.8f; p[8] = -9.6f; p[9] = 9.8f; p[10] = -11.2f; p[11] = 7.4f;
        poly(c, p, GOLD);
        quad(c, 1.6f, -9.8f, 11.2f, 7.4f, 9.6f, 9.8f, 6, 9.8f, GOLD_D);
        ln(c, 0, -4, 0, 2.6f, 2.8f, SLATE_D);
        circ(c, 0, 6.4f, 1.6f, SLATE_D);
    }

    private static void gift(Gfx c) {
        rect(c, -8, -2, 16, 11.6f, RED);
        rect(c, 3.6f, -2, 4.4f, 11.6f, RED_D);
        rect(c, -9.6f, -5.6f, 19.2f, 4.2f, RED_L);
        rect(c, -1.6f, -5.6f, 3.2f, 15.2f, GOLD);
        tri(c, 0, -5.4f, -7, -10.2f, -5.4f, -5, GOLD);
        tri(c, 0, -5.4f, 7, -10.2f, 5.4f, -5, GOLD_D);
        circ(c, 0, -5.6f, 1.7f, GOLD_L);
    }

    private static void crown(Gfx c) {
        float[] p = buf(7);
        p[0] = -10; p[1] = 6.4f; p[2] = -10.4f; p[3] = -5.6f; p[4] = -4.8f; p[5] = 0.4f;
        p[6] = 0; p[7] = -8.4f; p[8] = 4.8f; p[9] = 0.4f; p[10] = 10.4f; p[11] = -5.6f; p[12] = 10; p[13] = 6.4f;
        poly(c, p, GOLD);
        tri(c, 0, -8.4f, 4.8f, 0.4f, 0, 2, GOLD_D);
        rect(c, -10, 4, 20, 4.4f, GOLD_D);
        circ(c, -10.4f, -6.4f, 1.7f, GOLD_L);
        circ(c, 0, -9.4f, 1.8f, GOLD_L);
        circ(c, 10.4f, -6.4f, 1.7f, GOLD_L);
        circ(c, 0, 6.2f, 1.6f, RED);
        circ(c, -5.6f, 6.2f, 1.1f, TEAL);
        circ(c, 5.6f, 6.2f, 1.1f, TEAL);
    }

    private static void cart(Gfx c) {
        ln(c, -11, -8.4f, -7.6f, -8.4f, 2, STEEL_D);
        quad(c, -7.8f, -5.6f, 10.4f, -5.6f, 8, 3, -5.6f, 3, STEEL);
        quad(c, 4, -5.6f, 10.4f, -5.6f, 8, 3, 3.4f, 3, STEEL_D);
        ln(c, -7.6f, -8.4f, -4.4f, 6.2f, 2, STEEL_D);
        ln(c, -4.4f, 6.2f, 8, 6.2f, 2, STEEL_D);
        circ(c, -3, 9.2f, 1.9f, STEEL_D);
        circ(c, 6.6f, 9.2f, 1.9f, STEEL_D);
        rect(c, -4.4f, -8.8f, 4.4f, 3.6f, ORANGE);
        circ(c, 3.4f, -7.4f, 2.4f, GREEN);
    }

    private static void touch(Gfx c) {
        arc(c, -1.6f, -5.6f, 7.6f, 1.6f, 200, 340, BLUE_L);
        arc(c, -1.6f, -5.6f, 4.6f, 1.6f, 210, 330, BLUE);
        rrect(c, -4, -6, 4.8f, 13, 2.4f, SKIN);
        rrect(c, -4, 0.8f, 12, 10.4f, 3.4f, SKIN);
        ln(c, 1.6f, 1.8f, 1.6f, 4.8f, 1, 0xFFD8A47A);
        ln(c, 4.8f, 2.2f, 4.8f, 5, 1, 0xFFD8A47A);
        rrect(c, -3, -5, 2.8f, 3.2f, 1.2f, 0xFFFCE3CB);
    }

    private static void calendar(Gfx c) {
        rrect(c, -9.6f, -8, 19.2f, 18, 2, WHITE);
        rrect(c, -9.6f, -8, 19.2f, 6, 2, RED);
        rect(c, -9.6f, -4, 19.2f, 2, RED);
        ln(c, -5, -10.2f, -5, -6, 2.2f, STEEL_D);
        ln(c, 5, -10.2f, 5, -6, 2.2f, STEEL_D);
        rect(c, -6.6f, 1, 3.4f, 3, STEEL_D);
        rect(c, -1.7f, 1, 3.4f, 3, STEEL_D);
        rect(c, 3.2f, 1, 3.4f, 3, RED);
        rect(c, -6.6f, 5.4f, 3.4f, 3, STEEL_D);
    }

    // ---- job tools ----

    private static void cook(Gfx c) {
        // a pan seen from above at a slant, handle out to the right
        ln(c, 5, 2, 11.4f, -1.4f, 3.4f, BROWN);
        oval(c, -3, 2, 8.6f, 7, STEEL_D);
        oval(c, -3, 1.6f, 7, 5.6f, SLATE);
        oval(c, -3.6f, 1.2f, 4.6f, 3.4f, WHITE);
        circ(c, -3, 1.2f, 1.9f, GOLD);
        for (int i = -1; i <= 1; i += 2) {
            arc(c, -3 + i * 2.6f, -8.2f, 1.4f, 1.3f, -90, 90, 0x99EEF2F7);
            arc(c, -3 + i * 2.6f, -5.4f, 1.4f, 1.3f, 90, 270, 0x99EEF2F7);
        }
    }

    private static void waiter(Gfx c) {
        float[] p = buf(8);
        p[0] = -8.6f; p[1] = 5; p[2] = -8; p[3] = 0.4f; p[4] = -5.8f; p[5] = -3.6f; p[6] = -2.4f; p[7] = -5.6f;
        p[8] = 2.4f; p[9] = -5.6f; p[10] = 5.8f; p[11] = -3.6f; p[12] = 8; p[13] = 0.4f; p[14] = 8.6f; p[15] = 5;
        poly(c, p, STEEL);
        quad(c, 2.4f, -5.6f, 5.8f, -3.6f, 8, 0.4f, 8.6f, 5, STEEL_D);
        arc(c, 0, 5, 6.2f, 1.6f, 200, 245, STEEL_L);
        circ(c, 0, -6.6f, 1.7f, STEEL_D);
        ln(c, -11, 6.4f, 11, 6.4f, 2.4f, GOLD);
        ln(c, -4, 9.6f, 4, 9.6f, 2.2f, GOLD_D);
    }

    private static void loaf(Gfx c) {
        float[] p = buf(10);
        p[0] = -10.6f; p[1] = 3; p[2] = -9.4f; p[3] = -2.6f; p[4] = -5; p[5] = -6.4f; p[6] = 1; p[7] = -7.4f;
        p[8] = 7; p[9] = -5.6f; p[10] = 10.6f; p[11] = -1.4f; p[12] = 10.8f; p[13] = 4; p[14] = 8.6f; p[15] = 7;
        p[16] = -8; p[17] = 7; p[18] = -10.6f; p[19] = 5;
        poly(c, p, BROWN);
        quad(c, -10.6f, 3, 10.8f, 2.6f, 8.6f, 7, -8, 7, BROWN_D);
        ln(c, -6, -1, -3.4f, -4.8f, 1.7f, BROWN_L);
        ln(c, -1, -0.6f, 1.6f, -4.8f, 1.7f, BROWN_L);
        ln(c, 4, -0.6f, 6.4f, -4, 1.7f, BROWN_L);
        arc(c, -1, 2, 8, 1.2f, 205, 250, 0x99FFE6B8);
    }

    private static void cocktail(Gfx c) {
        tri(c, -9.6f, -8.4f, 9.6f, -8.4f, 0, 2.4f, 0x55EEF2F7);
        tri(c, -7.2f, -6, 7.2f, -6, 0, 1.2f, RED);
        tri(c, 1, -6, 7.2f, -6, 0, 1.2f, RED_D);
        ln(c, -9.6f, -8.4f, 9.6f, -8.4f, 1.4f, STEEL_L);
        ln(c, 0, 2, 0, 9, 1.8f, STEEL);
        ln(c, -5, 9.6f, 5, 9.6f, 2.2f, STEEL);
        ln(c, -3.4f, -3.2f, -7.4f, -11, 1.1f, BROWN_L);
        circ(c, -3.6f, -4, 2, GREEN);
    }

    private static void pump(Gfx c) {
        rrect(c, -8.6f, -10.4f, 11.6f, 19.6f, 2, RED);
        rect(c, -0.6f, -9, 3.6f, 17, RED_D);
        rrect(c, -6.6f, -7.6f, 7.4f, 5, 1, SLATE_D);
        ln(c, -5.2f, -5.1f, -0.8f, -5.1f, 1.3f, GREEN);
        rect(c, -10, 8, 14.4f, 2.6f, STEEL_D);
        ln(c, 3, -3, 7.4f, -3, 1.8f, SLATE);
        ln(c, 7.4f, -3, 7.4f, 6.4f, 1.8f, SLATE);
        rrect(c, 5.8f, -8.4f, 5, 3, 1, STEEL);
        ln(c, 8.2f, -5.6f, 7.4f, -3, 1.8f, SLATE);
    }

    private static void register(Gfx c) {
        rrect(c, -4.8f, -10, 9.6f, 5.8f, 1, SLATE);
        rect(c, -3.4f, -8.6f, 6.8f, 3, GREEN_D);
        ln(c, -2.4f, -7.1f, 2, -7.1f, 1, GREEN_L);
        rect(c, -1, -4.4f, 2, 2, STEEL_D);
        quad(c, -8.6f, -2.6f, 8.6f, -2.6f, 10.4f, 6, -10.4f, 6, STEEL);
        quad(c, 3.6f, -2.6f, 8.6f, -2.6f, 10.4f, 6, 4.4f, 6, STEEL_D);
        rect(c, -10.6f, 6, 21.2f, 4.4f, STEEL_D);
        rect(c, -2.6f, 7.6f, 5.2f, 1.2f, SLATE);
        rect(c, -6.6f, -0.6f, 2.4f, 1.8f, SLATE);
        rect(c, -2.6f, -0.6f, 2.4f, 1.8f, SLATE);
        rect(c, -6.6f, 2.6f, 2.4f, 1.8f, SLATE);
        rect(c, -2.6f, 2.6f, 2.4f, 1.8f, GOLD);
    }

    private static void stethoscope(Gfx c) {
        ln(c, -6.4f, -10, -6.4f, -3, 2, TEAL);
        ln(c, 3.6f, -10, 3.6f, -3, 2, TEAL);
        arc(c, -1.4f, -3, 5, 2, 0, 180, TEAL);
        ln(c, -1.4f, 2, -1.4f, 5, 2, TEAL);
        arc(c, 2.6f, 5, 4, 2, 90, 180, TEAL);
        circ(c, 6.4f, 4.8f, 3.4f, STEEL);
        circ(c, 6.4f, 4.8f, 1.7f, STEEL_D);
        circ(c, -6.4f, -10.4f, 1.4f, STEEL);
        circ(c, 3.6f, -10.4f, 1.4f, STEEL);
    }

    private static void scales(Gfx c) {
        ln(c, 0, -8.4f, 0, 8.4f, 2.2f, GOLD);
        ln(c, -9, -6.4f, 9, -6.4f, 2, GOLD);
        rrect(c, -5.4f, 7.8f, 10.8f, 2.6f, 1.2f, GOLD_D);
        ln(c, -9, -6.4f, -11.4f, 1.6f, 1, GOLD_L);
        ln(c, -9, -6.4f, -6.6f, 1.6f, 1, GOLD_L);
        ln(c, 9, -6.4f, 6.6f, 1.6f, 1, GOLD_L);
        ln(c, 9, -6.4f, 11.4f, 1.6f, 1, GOLD_L);
        arc(c, -9, 1.6f, 2.6f, 2.2f, 0, 180, GOLD);
        arc(c, 9, 1.6f, 2.6f, 2.2f, 0, 180, GOLD);
        circ(c, 0, -9, 1.8f, GOLD_L);
    }

    private static void laptop(Gfx c) {
        rrect(c, -8.6f, -9.4f, 17.2f, 12.2f, 1.4f, STEEL);
        rect(c, -7.2f, -8, 14.4f, 9.4f, SLATE_D);
        ln(c, -2.6f, -5.6f, -4.8f, -3.4f, 1.5f, GREEN);
        ln(c, -4.8f, -3.4f, -2.6f, -1.2f, 1.5f, GREEN);
        ln(c, 2.6f, -5.6f, 4.8f, -3.4f, 1.5f, GREEN);
        ln(c, 4.8f, -3.4f, 2.6f, -1.2f, 1.5f, GREEN);
        ln(c, 0.9f, -6.2f, -0.9f, -0.6f, 1.3f, GREEN_L);
        quad(c, -10, 3, 10, 3, 12, 7, -12, 7, STEEL_D);
        rect(c, -3, 3.2f, 6, 1.2f, SLATE);
    }

    // =====================================================================================
    // Buildings (grid: x +-50, y +-35, ground line at y = 29)
    // =====================================================================================

    private static final float GY = 29;

    private static void ground(Gfx c) {
        rrect(c, -50, GY - 0.5f, 100, 6, 3, 0xFF262E3A);
        ln(c, -48, GY, 48, GY, 1, 0xFF3A4554);
    }

    /** A block seen slightly from the right: lit roof, mid front, dark right side. Front spans x0..x1, top y0..GY. */
    private static void box(Gfx c, float x0, float y0, float x1, float y1, float d, int front) {
        float dy = d * 0.6f;
        quad(c, x1, y0, x1 + d, y0 - dy, x1 + d, y1 - dy, x1, y1, Ui.darken(front, 0.62f));
        quad(c, x0, y0, x1, y0, x1 + d, y0 - dy, x0 + d, y0 - dy, mix(front, 0xFFFFFFFF, 0.28f));
        rect(c, x0, y0, x1 - x0, y1 - y0, front);
    }

    /** Ground shadow cast to the right (light from the top left). */
    private static void shadow(Gfx c, float x1, float w) {
        quad(c, x1, GY + 0.5f, x1 + w, GY + 0.5f, x1 + w + 3, GY + 4.5f, x1 + 1.5f, GY + 4.5f, 0x38000000);
    }

    private static void window(Gfx c, float x, float y, float w, float h, boolean lit) {
        rect(c, x, y, w, h, lit ? WIN : WIN_D);
        if (lit) rect(c, x, y + h * 0.62f, w, h * 0.38f, 0x33D9962B);
    }

    private static void school(Gfx c, float t) {
        ground(c);
        shadow(c, 27, 12);
        int col = 0xFF6C8CE0;
        // tree on the left
        ln(c, -40, GY, -40, 18, 2.4f, BROWN_D);
        circ(c, -40, 14, 7.5f, GREEN_D);
        circ(c, -42, 11, 5, GREEN);
        box(c, -31, -8, 27, GY, 5, col);
        // pediment
        tri(c, -34, -9, -2, -25, 30, -9, mix(col, 0xFFFFFFFF, 0.3f));
        tri(c, -2, -25, 30, -9, 35, -12, Ui.darken(col, 0.62f));
        tri(c, -2, -25, 30, -9, 12, -9, Ui.darken(col, 0.88f));
        rect(c, -34, -10, 64, 3, CREAM);
        // clock in the pediment; the minute hand creeps round
        circ(c, -2, -15.5f, 4.2f, CREAM);
        float a = t * 0.6f;
        ln(c, -2, -15.5f, -2 + (float) Math.sin(a) * 3.2f, -15.5f - (float) Math.cos(a) * 3.2f, 1, SLATE);
        ln(c, -2, -15.5f, 0, -15.5f, 1.2f, SLATE);
        // columns
        for (int i = 0; i < 5; i++) {
            float x = -28 + i * 12.6f;
            if (i == 2) continue;
            rect(c, x, -7, 4.4f, 30, CREAM);
            rect(c, x + 3, -7, 1.4f, 30, CREAM_D);
        }
        // windows between columns, door in the middle
        window(c, -22.5f, 1, 5.4f, 9, true);
        window(c, 10.4f, 1, 5.4f, 9, false);
        window(c, -22.5f, 13, 5.4f, 8, false);
        window(c, 10.4f, 13, 5.4f, 8, true);
        rrect(c, -6.4f, 6, 9, 18, 4, SLATE_D);
        rect(c, -6.4f, 12, 9, 12, SLATE_D);
        ln(c, -1.9f, 8, -1.9f, 24, 0.8f, SLATE);
        // steps
        rect(c, -33, 23, 60, 3, STEEL_D);
        rect(c, -35, 26, 64, 3, STEEL);
        // flag pole with a waving flag
        ln(c, 38, GY, 38, -16, 1.4f, STEEL);
        circ(c, 38, -16.6f, 1.2f, GOLD);
        float[] p = buf(8);
        for (int i = 0; i < 4; i++) {
            float fx = 38 + i * 3.6f, wave = (float) Math.sin(t * 4 - i * 1.1f) * 1.4f * i / 3f;
            p[2 * i] = fx;
            p[2 * i + 1] = -15 + wave;
            p[14 - 2 * i] = fx;
            p[15 - 2 * i] = -7.4f + wave;
        }
        poly(c, p, WHITE);
        float w1 = (float) Math.sin(t * 4 - 3.3f) * 1.4f;
        ln(c, 38.6f, -13.4f, 48.8f, -13.4f + w1, 1.2f, BLUE);
        ln(c, 38.6f, -9, 48.8f, -9 + w1, 1.2f, BLUE);
    }

    private static void casino(Gfx c, float t) {
        ground(c);
        shadow(c, 33, 12);
        int col = 0xFF7B3D9C;
        box(c, -33, -14, 33, GY, 6, col);
        // rooftop diamond sign, pulsing glow
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
        ln(c, 0, -14, 0, -19, 1.6f, STEEL_D);
        circ(c, 0, -26, 9 + pulse * 2, alpha(TEAL, (int) (30 + 40 * pulse)));
        quad(c, 0, -34, 7.5f, -26, 0, -18, -7.5f, -26, TEAL);
        quad(c, 0, -34, 0, -18, -7.5f, -26, -7.5f, -26, TEAL_L);
        tri(c, 0, -34, 7.5f, -26, 0, -26, TEAL_D);
        // tall arched windows with pink light
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? -27 : 17;
            rrect(c, x, -9, 10, 20, 5, 0xFFE58BD8);
            rect(c, x, 4, 10, 7, 0xFFC064B8);
            ln(c, x + 5, -8, x + 5, 11, 1, col);
        }
        // marquee with chasing bulbs
        rrect(c, -15, -10, 30, 15, 2, 0xFF2A1636);
        coin(c, -6.5f, -2.5f, 0.42f);
        quad(c, 6.5f, -7.5f, 10.5f, -2.5f, 6.5f, 2.5f, 2.5f, -2.5f, RED);
        tri(c, 6.5f, -7.5f, 10.5f, -2.5f, 6.5f, -2.5f, RED_D);
        for (int i = 0; i < 14; i++) {
            float x, y;
            if (i < 5) { x = -14 + i * 7; y = -10; }
            else if (i < 7) { x = 14; y = -10 + (i - 4) * 5; }
            else if (i < 12) { x = 14 - (i - 7) * 7; y = 5; }
            else { x = -14; y = 5 - (i - 11) * 5; }
            boolean on = ((i + (int) (t * 8)) % 3) == 0;
            circ(c, x, y, on ? 1.5f : 1.1f, on ? GOLD_L : 0xFF8C6A3A);
        }
        // door and red carpet
        rect(c, -6, 11, 12, 18, 0xFF2A1636);
        rect(c, -5, 12, 4.6f, 17, GOLD_D);
        rect(c, 0.4f, 12, 4.6f, 17, GOLD_D);
        quad(c, -6, GY, 6, GY, 10, GY + 5, -10, GY + 5, RED);
        quad(c, 2, GY, 6, GY, 10, GY + 5, 4, GY + 5, RED_D);
        // rope posts
        ln(c, -13, GY + 4, -13, 22, 1.4f, GOLD);
        ln(c, 13, GY + 4, 13, 22, 1.4f, GOLD);
        circ(c, -13, 21.6f, 1.4f, GOLD_L);
        circ(c, 13, 21.6f, 1.4f, GOLD_L);
    }

    private static void bakery(Gfx c, float t) {
        ground(c);
        shadow(c, 25, 12);
        int col = 0xFFDDA869;
        // chimney first so the roof overlaps its base; smoke puffs drift up and fade
        rect(c, 10, -23, 5.4f, 12, BROWN_D);
        rect(c, 9.2f, -24.4f, 7, 2.4f, 0xFF6E4524);
        for (int i = 0; i < 3; i++) {
            float ph = frac(t * 0.35f + i / 3f);
            float x = 12.7f + (float) Math.sin((t + i) * 1.7f) * 2 + ph * 5;
            circ(c, x, -26 - ph * 7, 2 + ph * 2.4f, alpha(0xFFE5E8EE, (int) (150 * (1 - ph))));
        }
        box(c, -28, -6, 25, GY, 5, col);
        // pitched tile roof
        tri(c, -31, -6, -1.5f, -22, 28, -6, RED_D);
        tri(c, -31, -6, -1.5f, -22, -1.5f, -6, mix(RED_D, 0xFFFFFFFF, 0.18f));
        quad(c, -1.5f, -22, 28, -6, 33, -9, 3.5f, -25, Ui.darken(RED_D, 0.65f));
        // round sign with a loaf in the gable
        circ(c, -1.5f, -11.8f, 5.6f, CREAM);
        draw(c, "job.baker", X(-1.5f), Y(-11.8f), 8.6f * u, 0);
        // striped awning
        for (int i = 0; i < 7; i++) {
            float x0 = -28 + i * 7.6f, x1 = x0 + 7.6f;
            float b0 = -29.4f + i * 8.2f, b1 = b0 + 8.2f;
            quad(c, x0, -1.6f, x1, -1.6f, b1, 5, b0, 5, (i & 1) == 0 ? CREAM : RED);
            circ(c, (b0 + b1) / 2, 5, 4.1f, (i & 1) == 0 ? CREAM : RED);
        }
        // display window with bread on a shelf
        rrect(c, -23, 11, 21, 14, 1.4f, WIN);
        ln(c, -23, 20.6f, -2, 20.6f, 1.2f, BROWN_D);
        oval(c, -18, 18.4f, 3.4f, 2.2f, BROWN);
        oval(c, -12.4f, 18.4f, 3.4f, 2.2f, BROWN);
        oval(c, -6.8f, 18.4f, 3.4f, 2.2f, BROWN_L);
        circ(c, -15, 23, 1.6f, BROWN);
        circ(c, -9.6f, 23, 1.6f, BROWN_L);
        // door
        rect(c, 5, 11, 11, 18, BROWN_D);
        rect(c, 7, 13, 7, 6, WIN);
        circ(c, 14, 21.6f, 0.9f, GOLD);
    }

    private static void bar(Gfx c, float t) {
        ground(c);
        shadow(c, 26, 12);
        int col = 0xFF2C5552;
        box(c, -27, -16, 26, GY, 5, col);
        ln(c, -27, -16, 26, -16, 1.6f, TEAL_D);
        // neon cocktail sign: mostly on, with short realistic flickers
        float ph = frac(t * 0.45f);
        boolean on = !(ph > 0.82f && ph < 0.86f) && !(ph > 0.9f && ph < 0.92f);
        int neon = on ? 0xFFFF7FD0 : 0xFF5A3A55, glow = on ? 0x22FF7FD0 : 0;
        float sx = -6, sy = -11;
        if (glow != 0) circ(c, sx, sy + 3, 9.6f, glow);
        ln(c, sx - 6, sy - 2, sx + 6, sy - 2, 1.6f, neon);
        ln(c, sx - 6, sy - 2, sx, sy + 5, 1.6f, neon);
        ln(c, sx + 6, sy - 2, sx, sy + 5, 1.6f, neon);
        ln(c, sx, sy + 5, sx, sy + 9, 1.6f, neon);
        ln(c, sx - 3, sy + 9, sx + 3, sy + 9, 1.6f, neon);
        circ(c, sx + 2.4f, sy - 4, 1.6f, on ? 0xFF7FFFE8 : 0xFF3A5A55);
        ln(c, 6, -13, 20, -13, 1.4f, on ? 0xFF7FFFE8 : 0xFF3A5A55);
        ln(c, 6, -9, 16, -9, 1.4f, on ? 0xFF7FFFE8 : 0xFF3A5A55);
        // warm window with two regulars
        rrect(c, -23, 3, 21, 15, 1.4f, 0xFFE6A85A);
        circ(c, -17, 13, 3, 0xFF6E4A2E);
        rect(c, -21, 15, 8, 3, 0xFF6E4A2E);
        circ(c, -8, 12, 3, 0xFF6E4A2E);
        rect(c, -12, 14.6f, 8, 3.4f, 0xFF6E4A2E);
        rect(c, -23, 16.6f, 21, 1.4f, BROWN_D);
        // door with porthole
        rect(c, 4, 4, 12, 25, 0xFF18292A);
        circ(c, 10, 11, 2.8f, 0xFFE6A85A);
        circ(c, 14, 18, 0.9f, GOLD);
        // lamp on a bracket, swinging slightly
        ln(c, 20, -2, 23, -2, 1.2f, STEEL_D);
        float sw = (float) Math.sin(t * 1.8f) * 0.28f;
        float lx = 23 + (float) Math.sin(sw) * 6, ly = -2 + (float) Math.cos(sw) * 6;
        ln(c, 23, -2, lx, ly, 0.9f, STEEL_D);
        circ(c, lx, ly + 1.4f, 5, 0x33FFD98A);
        tri(c, lx - 2.6f, ly + 1.6f, lx + 2.6f, ly + 1.6f, lx, ly - 1.2f, SLATE);
        circ(c, lx, ly + 1.8f, 1.3f, WIN);
        // barrel
        rrect(c, 32, 19, 9, 10, 2.6f, BROWN);
        rect(c, 38, 19.6f, 3, 8.8f, BROWN_D);
        ln(c, 32, 22, 41, 22, 1, SLATE);
        ln(c, 32, 26, 41, 26, 1, SLATE);
    }

    private static void restaurant(Gfx c, float t) {
        ground(c);
        shadow(c, 25, 12);
        int col = 0xFFD9645F;
        box(c, -30, -14, 25, GY, 5, col);
        rect(c, -30, -14, 55, 3, Ui.darken(col, 0.8f));
        // sign band with a plate and cutlery
        rrect(c, -22, -9, 34, 9, 1.4f, CREAM);
        circ(c, -5, -4.5f, 3.4f, WHITE);
        circ(c, -5, -4.5f, 2.2f, CREAM_D);
        ln(c, -10, -7.4f, -10, -1.6f, 1, STEEL_D);
        ln(c, 0, -7.4f, 0, -1.6f, 1, STEEL_D);
        ln(c, -18, -4.5f, -13, -4.5f, 1.4f, RED_D);
        ln(c, 3, -4.5f, 8, -4.5f, 1.4f, RED_D);
        // awning
        for (int i = 0; i < 6; i++) {
            float x0 = -30 + i * 9.16f, x1 = x0 + 9.16f;
            quad(c, x0, 1, x1, 1, x1 + 1, 6, x0 + 1, 6, (i & 1) == 0 ? CREAM : 0xFF8E2F3A);
        }
        // two windows with candle-lit tables
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? -26 : -11;
            rrect(c, x, 9, 12, 13, 1.2f, WIN);
            rect(c, x + 2, 18, 8, 1.4f, BROWN_D);
            float fl = 1 + 0.25f * (float) Math.sin(t * 9 + i * 2);
            circ(c, x + 6, 16.2f, 0.9f * fl, ORANGE);
            rect(c, x + 5.6f, 16.8f, 0.8f, 1.4f, WHITE);
        }
        // door
        rect(c, 6, 9, 11, 20, 0xFF5A2A2C);
        rect(c, 8, 11, 7, 7, WIN);
        circ(c, 15, 20, 0.9f, GOLD);
        // hanging sign that swings on its bracket
        ln(c, 30, -11, 39, -11, 1.4f, STEEL_D);
        float sw = (float) Math.sin(t * 1.6f) * 7;
        float a = (float) Math.toRadians(sw);
        float cxs = 35 - (float) Math.sin(a) * 7, cys = -11 + (float) Math.cos(a) * 7;
        ln(c, 33, -11, cxs - 2 * (float) Math.cos(a), cys - 4 * (float) Math.cos(a), 0.7f, STEEL_D);
        ln(c, 37, -11, cxs + 2 * (float) Math.cos(a), cys - 4 * (float) Math.cos(a), 0.7f, STEEL_D);
        rquad(c, cxs, cys, 10, 9, sw, 0xFF8E2F3A);
        draw(c, "food", X(cxs), Y(cys), 7.4f * u, 0);
        // menu board by the door
        quad(c, 20, 18, 26, 18, 27.4f, GY, 18.6f, GY, SLATE);
        ln(c, 20.6f, 21, 25.4f, 21, 0.7f, CREAM);
        ln(c, 20.6f, 23.4f, 24.4f, 23.4f, 0.7f, CREAM);
        // potted plant
        quad(c, -44, 22, -36, 22, -37, GY, -43, GY, ORANGE_D);
        circ(c, -40, 18, 4.6f, GREEN_D);
        circ(c, -41.4f, 16.4f, 3, GREEN);
    }

    private static void market(Gfx c, float t) {
        ground(c);
        shadow(c, 36, 10);
        int col = 0xFFE4E9EF;
        box(c, -40, -8, 36, GY, 5, col);
        // AC unit on the roof
        rect(c, 18, -14, 9, 5, STEEL_D);
        circ(c, 22.5f, -11.5f, 1.8f, SLATE);
        // green sign band with the cart
        rect(c, -40, -8, 76, 10, 0xFF5DAE3E);
        rect(c, -40, 0, 76, 2, 0xFF3F8A29);
        circ(c, -2, -3, 6.6f, WHITE);
        draw(c, "cart", X(-2), Y(-3), 10.4f * u, 0);
        ln(c, -30, -3, -14, -3, 2, 0xFFD4F5C2);
        ln(c, 10, -3, 26, -3, 2, 0xFFD4F5C2);
        // shelves seen through the windows
        for (int w = 0; w < 2; w++) {
            float x = w == 0 ? -36 : 13;
            rect(c, x, 6, 19, 19, 0xFF9FD3E8);
            for (int r = 0; r < 3; r++) {
                rect(c, x + 1, 10 + r * 5.6f, 17, 1.2f, STEEL_D);
                for (int k = 0; k < 4; k++) {
                    int pc = ((w * 7 + r * 3 + k) % 4) == 0 ? RED : ((w + r + k) % 3 == 0 ? GOLD : ((r + k) % 2 == 0 ? GREEN : ORANGE));
                    rect(c, x + 1.6f + k * 4.2f, 7 + r * 5.6f, 3.2f, 3, pc);
                }
            }
            quad(c, x, 6, x + 7, 6, x, 14, x, 14, 0x40FFFFFF);
        }
        // automatic doors that slide open now and then
        float open = Math.max(0, (float) Math.sin(t * 0.9f)) * 4.2f;
        rect(c, -9, 6, 14, 23, 0xFF2A3A48);
        rect(c, -9 - open + 0.2f, 6.4f, 6.8f, 22.6f, 0xAA9FD3E8);
        rect(c, -2 + open, 6.4f, 6.8f, 22.6f, 0xAA9FD3E8);
        rect(c, -9.6f, 5, 15.2f, 1.6f, STEEL_D);
        // trolley outside
        draw(c, "cart", X(44), Y(23.4f), 12 * u, 0);
    }

    private static void gas(Gfx c, float t) {
        ground(c);
        shadow(c, 41, 8);
        // kiosk on the right
        box(c, 21, 2, 39, GY, 4, CREAM);
        rect(c, 24, 8, 8, 8, WIN);
        rect(c, 33, 12, 4.4f, 17, SLATE);
        boolean open = frac(t * 0.8f) < 0.6f;
        rrect(c, 25, 3.6f, 6, 2.6f, 1, open ? RED : 0xFF6A3A3A);
        // light pool under the canopy
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.2f);
        quad(c, -36, -12, 18, -12, 22, GY, -40, GY, alpha(0xFFFFF2C8, (int) (34 * pulse)));
        // posts and canopy
        rect(c, -34, -12, 3, 41, STEEL);
        rect(c, 13, -12, 3, 41, STEEL_D);
        quad(c, -40, -20, 22, -20, 26, -23, -36, -23, WHITE);
        rect(c, -40, -20, 62, 8, CREAM);
        rect(c, -40, -16, 62, 2.4f, GOLD);
        quad(c, 22, -20, 26, -23, 26, -15, 22, -12, CREAM_D);
        rect(c, -38, -12.6f, 58, 1.2f, alpha(WHITE, (int) (200 * pulse)));
        // pump island and pump
        rect(c, -22, 26, 22, 3, STEEL_D);
        rrect(c, -16, 5, 10, 21, 1.6f, RED);
        rect(c, -9, 6, 3, 19, RED_D);
        rrect(c, -14.4f, 7.4f, 6.8f, 5, 0.8f, SLATE_D);
        boolean blink = frac(t * 1.3f) < 0.5f;
        ln(c, -13.2f, 9.9f, -8.8f, 9.9f, 1.2f, blink ? GREEN : GREEN_D);
        rect(c, -14.4f, 14.6f, 6.8f, 2, STEEL);
        ln(c, -6, 9, -2.6f, 9, 1.4f, SLATE);
        ln(c, -2.6f, 9, -2.6f, 22, 1.4f, SLATE);
        rrect(c, -4, 16, 3, 4.4f, 1, STEEL_D);
        // oil cans
        rrect(c, 2, 20, 4.4f, 6, 0.8f, BLUE);
        rrect(c, 7, 21, 4.4f, 5, 0.8f, GREEN);
        // price sign on a pole at the left
        ln(c, -45, GY, -45, -4, 1.6f, STEEL_D);
        rrect(c, -49.6f, -12, 9.2f, 10, 1, SLATE);
        rect(c, -48.4f, -10.4f, 6.8f, 2.6f, GOLD);
        rect(c, -48.4f, -6.4f, 6.8f, 2.6f, blink ? WHITE : STEEL_D);
    }

    private static void home(Gfx c, float t) {
        ground(c);
        shadow(c, 27, 12);
        int col = 0xFFA4B0C4;
        // solar water heaters on the roof: a tilted panel and a tank each
        for (int i = 0; i < 2; i++) {
            float x = -22 + i * 24;
            ln(c, x, -20, x + 2, -26, 1, STEEL_D);
            quad(c, x - 1, -20, x + 8, -20, x + 10, -25, x + 1, -25, 0xFF2D4C7A);
            ln(c, x + 1.6f, -22.4f, x + 9, -22.4f, 0.6f, 0xFF5A7FB0);
            rrect(c, x + 1, -30.6f, 10, 4, 2, STEEL_L);
            rect(c, x + 8, -30, 3, 3, STEEL);
        }
        ln(c, 18, -20, 18, -32, 0.9f, STEEL_D);
        ln(c, 15, -29, 21, -29, 0.9f, STEEL_D);
        box(c, -30, -20, 26, 19, 5, col);
        // ground floor on pillars (kolonot): a dark open space
        rect(c, -30, 19, 56, 10, 0xFF1B212B);
        quad(c, 26, 19, 31, 16, 31, 26, 26, GY, 0xFF141920);
        for (int i = 0; i < 4; i++) rect(c, -28 + i * 17, 19, 3.6f, 10, STEEL);
        rect(c, 2, 21, 6, 8, WIN_D);
        // windows: one flat switches its light now and then
        for (int fl = 0; fl < 3; fl++) {
            for (int k = 0; k < 4; k++) {
                float x = -26 + k * 13.4f, y = -16 + fl * 11.6f;
                boolean lit = hash(fl, k) < 0.45f;
                if (fl == 1 && k == 2) lit = frac(t * 0.18f) < 0.55f;
                window(c, x, y, 7.4f, 7, lit);
                rect(c, x - 0.6f, y + 7, 8.6f, 1.2f, STEEL_D);
            }
        }
        // AC unit and laundry on a line, swaying
        rect(c, 19.6f, -6, 5, 4, STEEL);
        circ(c, 22.1f, -4, 1.3f, STEEL_D);
        ln(c, -30, 6.4f, -42, 6.4f, 0.7f, STEEL);
        ln(c, -42, 6.4f, -42, 24, 0.9f, STEEL_D);
        int[] cols = LAUNDRY;
        for (int i = 0; i < 3; i++) {
            float x = -40 + i * 3.6f, sway = (float) Math.sin(t * 2 + i) * 0.9f;
            quad(c, x, 6.4f, x + 3, 6.4f, x + 3 + sway, 11 + (i % 2) * 1.6f, x + sway, 11 + (i % 2) * 1.6f, cols[i]);
        }
        // bush by the entrance
        circ(c, 36, 25, 5, GREEN_D);
        circ(c, 34, 23, 3.6f, GREEN);
    }

    private static final int[] LAUNDRY = {RED, WHITE, BLUE};

    private static void alley(Gfx c, float t) {
        // floor receding to the far end
        quad(c, -40, GY + 5, 40, GY + 5, 9, 4, -9, 4, 0xFF20262F);
        ln(c, 0, GY + 4, 0, 6, 0.8f, 0xFF2C343F);
        // far end: a dim doorway with light spilling out
        rect(c, -9, -16, 18, 20, 0xFF1A1F27);
        rect(c, -3.6f, -7, 7.2f, 11, 0xFFB98A4A);
        rect(c, -3.6f, -7, 2.4f, 11, 0xFFE0B06A);
        quad(c, -3.6f, 4, 3.6f, 4, 7, 12, -7, 12, 0x30E0B06A);
        rect(c, -5, -10, 10, 2, 0xFF2C343F);
        // left and right walls in perspective
        quad(c, -50, -33, -9, -16, -9, 4, -50, GY + 5, 0xFF3A414D);
        quad(c, 50, -33, 9, -16, 9, 4, 50, GY + 5, 0xFF2A3039);
        // brick hints
        for (int i = 0; i < 4; i++) {
            float y = -18 + i * 9;
            ln(c, -46, y, -40, y + 1.6f, 1, 0xFF474F5C);
            ln(c, -32, y + 4, -25, y + 5.4f, 1, 0xFF474F5C);
        }
        // abstract graffiti swirl on the left wall
        arc(c, -30, 6, 5.4f, 1.6f, 160, 400, 0xCCFF7FD0);
        arc(c, -22, 8, 3.6f, 1.6f, -40, 200, 0xCC4FC9C0);
        // flickering wall lamp
        float f = frac(t * 0.7f);
        boolean on = !(f > 0.7f && f < 0.73f) && !(f > 0.78f && f < 0.8f);
        ln(c, -46, -14, -38, -14, 1.4f, STEEL_D);
        if (on) quad(c, -40, -12, -36, -12, -20, GY + 4, -46, GY + 4, 0x1EFFE7A8);
        tri(c, -41.6f, -12, -34.4f, -12, -38, -15.4f, SLATE);
        circ(c, -38, -11.6f, 1.8f, on ? WIN : 0xFF5A5040);
        // dumpster with a cat on top whose eyes blink
        rect(c, 18, 11, 22, 16, 0xFF2F6B4E);
        rect(c, 34, 11, 6, 16, 0xFF235139);
        quad(c, 17, 11, 41, 11, 39, 7.4f, 19, 7.4f, 0xFF3D8A64);
        rect(c, 20, 26, 3, 3, SLATE_D);
        rect(c, 35, 26, 3, 3, SLATE_D);
        circ(c, 26, 3.4f, 4, SLATE_D);
        tri(c, 23, 1, 24.4f, -2.6f, 25.6f, 0.6f, SLATE_D);
        tri(c, 26.6f, 0.6f, 27.8f, -2.6f, 29, 1, SLATE_D);
        oval(c, 30, 6, 5, 2.6f, SLATE_D);
        if (frac(t * 0.33f) < 0.93f) {
            circ(c, 24.8f, 3, 0.8f, GOLD);
            circ(c, 27.4f, 3, 0.8f, GOLD);
        }
        // a puddle catching the lamp
        oval(c, -24, GY + 1.6f, 7, 1.6f, 0xFF2C3540);
        if (on) oval(c, -25, GY + 1.4f, 3, 0.7f, 0x88FFE7A8);
        // crates on the left
        rect(c, -34, 18, 10, 10, BROWN_D);
        ln(c, -34, 18, -24, 28, 1, 0xFF6E4524);
        rect(c, -31, 11, 7, 7, BROWN);
    }

    // =====================================================================================
    // Title screen art
    // =====================================================================================

    private static final int SKY_TOP = 0xFF0A0F1E, SKY_LOW = 0xFF1E2540;

    /**
     * Night sky, moon, three parallax layers of skyline and the street at {@code horizon}.
     * Nothing taller than {@code top} (the bottom of the title) is drawn in front of the sky.
     */
    static void titleBackdrop(Gfx c, float w, float h, float horizon, float top, float t) {
        float sox = ox, soy = oy, su = u;
        int st = tint;
        ox = 0;
        oy = 0;
        u = 1;
        tint = 0;
        try {
            // sky in bands, darkest at the top
            final int bands = 14;
            for (int i = 0; i < bands; i++) {
                float y0 = horizon * i / bands, y1 = horizon * (i + 1) / bands;
                c.fillRect(0, y0, w, y1 - y0 + 1, mix(SKY_TOP, SKY_LOW, (float) i / (bands - 1)));
            }
            float maxH = Math.max(60, Math.min(220, horizon - top));
            // stars
            for (int i = 0; i < 42; i++) {
                float sx = hash(i, 1) * w, sy = hash(i, 2) * (horizon - maxH * 0.5f);
                float tw = 0.5f + 0.5f * (float) Math.sin(t * (1 + hash(i, 3) * 2) + i);
                float r = 0.6f + hash(i, 4) * 0.9f;
                c.fillCircle(sx, sy, r, alpha(0xFFEFF3FF, (int) (60 + 160 * tw * hash(i, 5))));
            }
            // a plane crossing slowly with a blinking light
            float px = frac(t / 60f) * (w + 80) - 40, py = top + (horizon - top) * 0.06f;
            c.fillCircle(px, py, 1.2f, 0x99FFFFFF);
            if (frac(t * 1.1f) < 0.15f) c.fillCircle(px - 3, py, 1.6f, 0xFFFF6B6B);
            // moon, lit from the left like everything else
            float mx = 66, my = horizon - maxH * 0.86f, mr = 17;
            for (int i = 6; i >= 1; i--) c.fillCircle(mx, my, mr * (1 + i * 0.32f), 0x07F4EBD0);
            c.fillCircle(mx, my, mr, 0xFFCFC3A3);
            c.fillCircle(mx - 2.2f, my - 1.4f, mr - 2.4f, 0xFFF4EBD0);
            c.fillCircle(mx + 4, my - 5, 3, 0xFFE2D6B6);
            c.fillCircle(mx - 6, my + 4, 2.2f, 0xFFE2D6B6);
            c.fillCircle(mx + 3, my + 7, 1.6f, 0xFFE2D6B6);
            // skyline, back to front; each layer drifts at its own speed for depth
            skyLayer(c, 0, w, horizon, maxH * 1.0f, t * 3, 0xFF1A2236, 0.18f);
            skyLayer(c, 1, w, horizon, maxH * 0.78f, t * 7, 0xFF212A3E, 0.3f);
            skyLayer(c, 2, w, horizon, maxH * 0.55f, t * 14, 0xFF2A3448, 0.32f);
            // street
            c.fillRect(0, horizon, w, h - horizon, 0xFF161B22);
            c.fillRect(0, horizon, w, 2, 0xFF2E3746);
            // lamp posts scroll with the nearest layer
            float off = (t * 14) % 150;
            for (float lx = -off + 40; lx < w + 20; lx += 150) {
                c.fillPoly(lampPool(lx + 8, horizon), 0x0FFFD98A);
                c.fillCircle(lx + 8, horizon - 50, 7, 0x22FFD98A);
                c.line(lx, horizon, lx, horizon - 52, 2.2f, 0xFF10141A);
                c.line(lx, horizon - 52, lx + 8, horizon - 53, 2.2f, 0xFF10141A);
                c.fillCircle(lx + 8, horizon - 50.6f, 2.4f, WIN);
            }
        } finally {
            ox = sox;
            oy = soy;
            u = su;
            tint = st;
        }
    }

    private static float[] lampPool(float x, float horizon) {
        float[] p = buf(4);
        p[0] = x - 2; p[1] = horizon - 49; p[2] = x + 2; p[3] = horizon - 49;
        p[4] = x + 14; p[5] = horizon; p[6] = x - 14; p[7] = horizon;
        return p;
    }

    private static void skyLayer(Gfx c, int L, float w, float horizon, float maxH, float scroll, int body, float lit) {
        final int n = 12;
        float period = 0;
        for (int i = 0; i < n; i++) period += bw(L, i);
        float x = -(scroll % period);
        int side = Ui.darken(body, 0.72f), rim = mix(body, 0xFF8FA3C8, 0.35f);
        for (int k = 0; x < w + 10 && k < 3 * n; k++) {
            int i = k % n;
            float bwid = bw(L, i);
            float bh = maxH * (0.38f + 0.62f * hash(L * 31 + i, 7));
            float x0 = x, y0 = horizon - bh;
            if (x0 + bwid > -10) {
                float depth = 3 + L * 2;
                if (L > 0) {
                    float[] p = buf(4);
                    p[0] = x0 + bwid; p[1] = y0; p[2] = x0 + bwid + depth; p[3] = y0 - depth * 0.6f;
                    p[4] = x0 + bwid + depth; p[5] = horizon; p[6] = x0 + bwid; p[7] = horizon;
                    c.fillPoly(p, side);
                }
                c.fillRect(x0, y0, bwid, bh, body);
                // moonlight catches the left edge and the roof line
                c.fillRect(x0, y0, 1.4f, bh, rim);
                c.fillRect(x0, y0, bwid, 1.2f, rim);
                roof(c, L, i, x0, y0, bwid, t0(scroll, L));
                windows(c, L, i, x0, y0, bwid, bh, horizon, lit, scroll);
            }
            x += bwid;
        }
    }

    private static float t0(float scroll, int L) {
        // the layer scroll is t * speed; recover a time-like value for blinking details
        return scroll / (L == 0 ? 3 : L == 1 ? 7 : 14);
    }

    private static float bw(int L, int i) {
        return (L == 2 ? 44 : L == 1 ? 34 : 26) + hash(L * 31 + i, 9) * (L == 2 ? 30 : 24);
    }

    private static void roof(Gfx c, int L, int i, float x0, float y0, float bwid, float t) {
        float r = hash(L * 31 + i, 11);
        int col = L == 2 ? 0xFF2A3448 : 0xFF212A3E;
        if (L == 0) return;
        if (r < 0.3f) {
            // antenna with a red aircraft light
            float ax = x0 + bwid * 0.7f;
            c.line(ax, y0, ax, y0 - 14 - L * 4, 1.2f, col);
            if (frac(t * 0.7f + r * 5) < 0.5f) c.fillCircle(ax, y0 - 14 - L * 4, 1.6f, 0xFFFF5A5A);
        } else if (r < 0.55f && L == 2) {
            // water tank on legs
            float tx = x0 + bwid * 0.3f;
            c.line(tx - 4, y0, tx - 4, y0 - 6, 1, col);
            c.line(tx + 4, y0, tx + 4, y0 - 6, 1, col);
            c.fillRoundRect(tx - 6, y0 - 15, 12, 10, 2, col);
            c.fillRect(tx - 6, y0 - 15, 1.4f, 10, 0xFF3E4C66);
        } else if (r < 0.8f) {
            // solar water heaters, Israeli roofs are covered in them
            float sx = x0 + bwid * 0.25f;
            float[] p = buf(4);
            p[0] = sx; p[1] = y0; p[2] = sx + 12; p[3] = y0; p[4] = sx + 14; p[5] = y0 - 6; p[6] = sx + 2; p[7] = y0 - 6;
            c.fillPoly(p, L == 2 ? 0xFF2B4466 : 0xFF253752);
            c.fillRoundRect(sx + 3, y0 - 11, 11, 4, 2, col);
        }
    }

    private static void windows(Gfx c, int L, int i, float x0, float y0, float bwid, float bh, float horizon,
                                float lit, float scroll) {
        float t = t0(scroll, L);
        float ww = L == 2 ? 5 : L == 1 ? 4 : 2.4f, wh = L == 2 ? 6 : L == 1 ? 4.4f : 2.4f;
        float gx = L == 2 ? 10 : L == 1 ? 8 : 6, gy = L == 2 ? 12 : L == 1 ? 10 : 8;
        float style = hash(L * 31 + i, 27);
        if (L == 2 && style < 0.3f) {
            // ribbon windows on an office block
            ww = gx * 0.86f;
            wh = 3;
            gy = 9;
        }
        int cols = Math.max(1, (int) ((bwid - 6) / gx));
        float startX = x0 + (bwid - (cols - 1) * gx - ww) / 2;
        int row = 0;
        for (float y = y0 + 8; y < horizon - wh - 4; y += gy, row++) {
            for (int k = 0; k < cols; k++) {
                int id = (L * 31 + i) * 97 + row * 13 + k;
                float hv = hash(id, 21);
                // a few flats switch their lights every few seconds
                int epoch = (int) Math.floor(t / 5 + hash(id, 23) * 7);
                boolean on = hv < lit;
                if (hash(id, epoch) < 0.06f) on = !on;
                if (!on && L < 2) continue; // unlit windows only show on the nearest layer
                int wc = on ? (hash(id, 25) < 0.25f ? 0xFFBFD9FF : WIN) : 0xFF1A2130;
                if (on && L < 2) wc = alpha(wc, L == 0 ? 120 : 190);
                c.fillRect(startX + k * gx, y, ww, wh, wc);
            }
        }
    }

    /**
     * The logo around the title text: a speech bubble behind it, a ringing handset at its corner and
     * a coiled phone cord along the top. The words themselves are drawn by the caller with c.text.
     */
    static void titleLogo(Gfx c, float cx, float baseY, float textW, float t) {
        float sox = ox, soy = oy, su = u;
        int st = tint;
        ox = 0;
        oy = 0;
        u = 1;
        tint = 0;
        try {
            float bw = textW + 52, bh = 62, bx = cx - bw / 2, by = baseY - 44;
            int fill = 0xFF1C2435, edge = 0xDDFFC857;
            c.fillRoundRect(bx + 3, by + 5, bw, bh, 24, 0x55000000);
            c.fillRoundRect(bx, by, bw, bh, 24, fill);
            c.strokeRoundRect(bx, by, bw, bh, 24, 2.6f, edge);
            // tail at the lower left, covering the outline where it joins
            float tx = bx + 22;
            tri(c, tx - 4, by + bh - 2.6f, tx + 20, by + bh - 2.6f, tx - 16, by + bh + 14, fill);
            c.line(tx - 4, by + bh, tx - 16, by + bh + 14, 2.6f, edge);
            c.line(tx + 20, by + bh, tx - 16, by + bh + 14, 2.6f, edge);

            // handset off the top right corner; it rings in bursts and shakes while ringing
            float cycle = t % 3.4f;
            boolean ringing = cycle < 1.5f;
            float shake = ringing ? (float) Math.sin(t * 46) * 0.06f : 0;
            float hx = bx + bw - 14, hy = by - 12 + (ringing ? (float) Math.abs(Math.sin(t * 23)) * -1.5f : 0);
            float ang = -0.3f + shake, k = 1.05f;
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            // the cord leaves the handset's left cup, runs along the top of the bubble and hangs
            // down past its left side
            float sxp = hx + (-24 * ca - 9 * sa) * k, syp = hy + (-24 * sa + 9 * ca) * k;
            float sag = (float) Math.sin(t * 1.3f) * 2;
            cord(c, sxp, syp, bx + bw * 0.55f, by - 24 + sag, bx - 4, by - 26 + sag, bx - 12, by + bh * 0.75f, t);
            handset(c, hx, hy, ca, sa, k);
            if (ringing) {
                float a = 1 - cycle / 1.5f;
                int rc = alpha(GOLD, (int) (90 + 160 * a));
                arc(c, hx + 4, hy + 4, 30, 2.4f, -62, -18, rc);
                arc(c, hx + 4, hy + 4, 37, 2.4f, -56, -24, alpha(rc, (int) (60 + 120 * a)));
            }
        } finally {
            ox = sox;
            oy = soy;
            u = su;
            tint = st;
        }
    }

    private static final float[] HANDSET = {
            -28, -3, -23, -9, 23, -9, 28, -3, 30, 13, 15, 13, 12, 0, -12, 0, -15, 13, -30, 13};
    private static final float[] HANDSET_HL = {-21, -6.6f, 21, -6.6f, 19, -3.8f, -19, -3.8f};

    private static void handset(Gfx c, float hx, float hy, float ca, float sa, float k) {
        float[] p = buf(10);
        for (int i = 0; i < 10; i++) {
            float lx = HANDSET[2 * i] * k, ly = HANDSET[2 * i + 1] * k;
            p[2 * i] = hx + lx * ca - ly * sa;
            p[2 * i + 1] = hy + lx * sa + ly * ca;
        }
        // drop shadow, body, then a lit strip along the top edge
        float[] s = buf(10);
        for (int i = 0; i < 10; i++) {
            s[2 * i] = p[2 * i] + 2.5f;
            s[2 * i + 1] = p[2 * i + 1] + 3.5f;
        }
        c.fillPoly(s, 0x55000000);
        p = buf(10);
        for (int i = 0; i < 10; i++) {
            float lx = HANDSET[2 * i] * k, ly = HANDSET[2 * i + 1] * k;
            p[2 * i] = hx + lx * ca - ly * sa;
            p[2 * i + 1] = hy + lx * sa + ly * ca;
        }
        c.fillPoly(p, RED);
        float[] q = buf(4);
        for (int i = 0; i < 4; i++) {
            float lx = HANDSET_HL[2 * i] * k, ly = HANDSET_HL[2 * i + 1] * k;
            q[2 * i] = hx + lx * ca - ly * sa;
            q[2 * i + 1] = hy + lx * sa + ly * ca;
        }
        c.fillPoly(q, RED_L);
        // cup rims
        for (int side = -1; side <= 1; side += 2) {
            float lx = side * 22.5f * k, ly = 12.4f * k, lw = 7f * k;
            float x1 = hx + (lx - lw) * ca - ly * sa, y1 = hy + (lx - lw) * sa + ly * ca;
            float x2 = hx + (lx + lw) * ca - ly * sa, y2 = hy + (lx + lw) * sa + ly * ca;
            c.line(x1, y1, x2, y2, 2.2f * k, RED_D);
        }
    }

    /** A coiled cord along a cubic curve from (x0,y0) to (x3,y3). */
    private static void cord(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                             float t) {
        final int n = 112;
        final float loops = 15, r = 3.6f;
        float px = 0, py = 0;
        for (int i = 0; i <= n; i++) {
            float s = (float) i / n, is = 1 - s;
            float b0 = is * is * is, b1 = 3 * is * is * s, b2 = 3 * is * s * s, b3 = s * s * s;
            float bx = b0 * x0 + b1 * x1 + b2 * x2 + b3 * x3, by = b0 * y0 + b1 * y1 + b2 * y2 + b3 * y3;
            float tx = 3 * is * is * (x1 - x0) + 6 * is * s * (x2 - x1) + 3 * s * s * (x3 - x2);
            float ty = 3 * is * is * (y1 - y0) + 6 * is * s * (y2 - y1) + 3 * s * s * (y3 - y2);
            float tl = (float) Math.sqrt(tx * tx + ty * ty);
            if (tl < 1e-3f) tl = 1;
            tx /= tl;
            ty /= tl;
            double ph = s * loops * Math.PI * 2 + t * 0.6;
            float cs = (float) Math.cos(ph), sn = (float) Math.sin(ph);
            // the coil relaxes over the last stretch so the cord ends in a loose tail
            float rr = r * (s > 0.88f ? (1 - s) / 0.12f : 1);
            float qx = bx - ty * rr * sn + tx * rr * 0.8f * cs, qy = by + tx * rr * sn + ty * rr * 0.8f * cs;
            if (i > 0) c.line(px, py, qx, qy, 2.1f, cs > 0 ? RED : RED_D);
            px = qx;
            py = qy;
        }
    }
}
