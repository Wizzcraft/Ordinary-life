package com.wizzcraft.ima.core;

/**
 * The city map's art: a small 2.5D town in one oblique projection.
 *
 * <p>A point (x, y, z) in city units, z being the height above the ground, lands on the screen at
 * (x - LEAN * z, y - z), then scaled and offset like the rest of the map. Walls rise straight up the
 * screen and roofs lean a little to the left, so every building shows its front (south) wall, where its
 * door is, and its east wall. The sun is always in the upper left: roofs are the brightest faces, east
 * walls the darkest, and shadows fall to the right.
 *
 * <p>Lighting is a colour multiplier that follows the clock (day, golden hour, dusk, night, dawn).
 * Every painted colour goes through {@link #lit}; things that give off light (lit windows, lamps, neon,
 * headlights) skip it, which is what makes them glow in the dark.
 *
 * <p>Use: {@link #begin} once per frame, then {@link #ground} (everything flat), then {@link #scene}
 * (everything standing, sorted back to front). Holds only scratch buffers; nothing is allocated per frame.
 */
final class CityArt {
    /** Horizontal shift of a point per unit of height (the projection's lean). */
    static final float LEAN = 0.22f;
    /** Half-width of a road plus its sidewalks. */
    static final float BAND = 15;
    /** Half-width of the asphalt. */
    static final float ASPH = City.ROAD_W / 2;

    // ---- palette (daylight values; everything painted goes through lit()) ----
    private static final int GRASS = 0xFF86A95A, GRASS_DK = 0xFF759A4C, GRASS_LT = 0xFF96B866,
            WALK = 0xFFCFC6B3, WALK_EDGE = 0xFFA79D8A, ROAD = 0xFF50555E, ROAD_EDGE = 0xFF41454D,
            PAINT = 0xFFEDE6D2, PAVING = 0xFFBDB6A7, SHADOW = 0x40182030;
    private static final int TRUNK = 0xFF6E4C34, LEAF_DK = 0xFF3F7337, LEAF = 0xFF55903F, LEAF_LT = 0xFF79B356;
    private static final int GLASS = 0xFF9EC4D4, WARM = 0xFFFFD58A, WARM2 = 0xFFFFC46A, TV = 0xFFC6DAFF;
    private static final int[] CARS = {0xFFD9534F, 0xFF4F7FD0, 0xFFF0EEE8, 0xFF3B4049, 0xFFE8B83A, 0xFF5FA86E,
            0xFFB9C0C8, 0xFF8E5BB8};
    private static final int[] CLOTHES = {0xFF5B8DD9, 0xFFD9675B, 0xFF6AB07A, 0xFFE0B04A, 0xFF9B7BD0, 0xFF4FB0B0,
            0xFFE08AB0, 0xFF8A97A8};

    /** Light keyframes: minute of day, red, green and blue multipliers, darkness (0 day .. 1 night). */
    private static final float[] KEYS = {
            0, .30f, .34f, .52f, 1,
            300, .30f, .34f, .52f, 1,
            345, .50f, .46f, .66f, .8f,
            390, .82f, .66f, .72f, .4f,
            440, .98f, .88f, .84f, .08f,
            500, 1, 1, 1, 0,
            990, 1, 1, 1, 0,
            1060, 1.03f, .88f, .70f, .06f,
            1110, .84f, .64f, .62f, .38f,
            1160, .50f, .45f, .63f, .75f,
            1220, .33f, .36f, .54f, .95f,
            1260, .30f, .34f, .52f, 1,
            1440, .30f, .34f, .52f, 1,
    };

    // ---- scenery, in city units: x, ground y, radius ----
    private static final float[] TREES = {
            12, 22, 8, 13, 66, 9, 15, 178, 8, 10, 222, 9, 13, 316, 8, 11, 362, 9, 14, 452, 9, 10, 500, 8,
            334, 52, 9, 351, 94, 8, 331, 170, 9, 349, 226, 9, 338, 300, 9, 329, 358, 8,
            76, 40, 7, 148, 38, 7, 279, 38, 7,
            72, 170, 8, 104, 158, 7, 142, 172, 8, 206, 174, 8, 241, 160, 6, 272, 178, 7,
            70, 302, 8, 121, 292, 7, 206, 304, 7, 250, 292, 8, 280, 308, 6,
            156, 505, 6, 206, 444, 7, 241, 432, 8, 276, 440, 6,
    };
    private static final float[] BUSHES = {
            284, 236, 4, 156, 371, 4, 57, 512, 4, 322, 100, 4, 356, 240, 4, 345, 372, 5,
            20, 100, 4, 18, 240, 4, 22, 372, 4, 20, 512, 4,
    };

    /** Unit circle, 12 points, for ellipses. */
    private static final float[] RING = new float[24];

    static {
        for (int i = 0; i < 12; i++) {
            RING[i * 2] = (float) Math.cos(i * Math.PI / 6);
            RING[i * 2 + 1] = (float) Math.sin(i * Math.PI / 6);
        }
    }

    // ---- per-frame state ----
    private float s = 1, ox, oy, minute, t;
    private float lr = 1, lg = 1, lb = 1, ar, ag, ab;
    /** 0 in daylight, 1 at night: how much artificial light shows. */
    private float dark;
    private float density, walkers;
    private float vx0, vx1, vy0, vy1;

    // current building box (set by setBox)
    private float fx0, fx1, fy0, fy1, hz;

    // ---- scratch ----
    private final float[] q3 = new float[6], q4 = new float[8], q6 = new float[12], el = new float[24];
    private static final int MAX = 160;
    private final float[] key = new float[MAX], ax = new float[MAX], ay = new float[MAX];
    private final int[] kind = new int[MAX], arg = new int[MAX], order = new int[MAX];
    private int n;
    private static final int K_BUILDING = 0, K_TREE = 1, K_BUSH = 2, K_LAMP = 3, K_CAR = 4, K_PED = 5;

    /** Sets the frame: the city-to-screen transform, the clock and the animation time in seconds. */
    void begin(float scale, float offX, float offY, float minuteOfDay, float time) {
        s = scale;
        ox = offX;
        oy = offY;
        minute = ((minuteOfDay % 1440) + 1440) % 1440;
        t = time;
        int i = 0;
        while (i + 5 < KEYS.length && KEYS[i + 5] <= minute) i += 5;
        int j = Math.min(i + 5, KEYS.length - 5);
        float span = KEYS[j] - KEYS[i], k = span <= 0 ? 0 : (minute - KEYS[i]) / span;
        k = k * k * (3 - 2 * k);
        lr = KEYS[i + 1] + (KEYS[j + 1] - KEYS[i + 1]) * k;
        lg = KEYS[i + 2] + (KEYS[j + 2] - KEYS[i + 2]) * k;
        lb = KEYS[i + 3] + (KEYS[j + 3] - KEYS[i + 3]) * k;
        dark = KEYS[i + 4] + (KEYS[j + 4] - KEYS[i + 4]) * k;
        // A little blue lift so the night stays readable rather than black.
        ar = 6 * dark;
        ag = 9 * dark;
        ab = 20 * dark;
        float h = minute / 60f;
        if (h < 5) density = 0.18f;
        else if (h < 7) density = 0.18f + (h - 5) * 0.36f;
        else if (h < 9.5f) density = 0.95f;
        else if (h < 16) density = 0.7f;
        else if (h < 19.5f) density = 0.95f;
        else if (h < 23) density = 0.95f - (h - 19.5f) * 0.18f;
        else density = 0.3f;
        walkers = h < 6 ? 0.1f : h < 8 ? 0.45f : h < 20 ? 0.62f : h < 23 ? 0.4f : 0.15f;
    }

    /** 0 in daylight, 1 at night. */
    float darkness() {
        return dark;
    }

    // ================= colour =================

    /** A painted colour under the current light. */
    int lit(int c) {
        int r = (int) (((c >> 16) & 0xFF) * lr + ar), g = (int) (((c >> 8) & 0xFF) * lg + ag),
                b = (int) ((c & 0xFF) * lb + ab);
        return (c & 0xFF000000) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    static int mix(int a, int b, float k) {
        if (k <= 0) return a;
        if (k >= 1) return b;
        int aa = (a >>> 24), ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF;
        int ab2 = (b >>> 24), rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ab2 - aa) * k) << 24) | ((int) (ra + (rb - ra) * k) << 16)
                | ((int) (ga + (gb - ga) * k) << 8) | (int) (ba + (bb - ba) * k);
    }

    static int alpha(int c, float a) {
        int al = (int) (Ui.clamp(a, 0, 1) * (c >>> 24));
        return (al << 24) | (c & 0xFFFFFF);
    }

    /** Stable pseudo-random 0..1 from an integer. */
    static float hash(int v) {
        v = (v << 13) ^ v;
        v = v * (v * v * 15731 + 789221) + 1376312589;
        return (v & 0x7fffffff) / (float) 0x7fffffff;
    }

    // ================= projection =================

    private float X(float x, float z) {
        return ox + (x - LEAN * z) * s;
    }

    private float Y(float y, float z) {
        return oy + (y - z) * s;
    }

    /** A wall facing south at depth yb, spanning u0..u1 and heights z0..z1. */
    private void fq(Gfx c, float u0, float u1, float z0, float z1, float yb, int col) {
        q4[0] = X(u0, z0);
        q4[1] = Y(yb, z0);
        q4[2] = X(u1, z0);
        q4[3] = q4[1];
        q4[4] = X(u1, z1);
        q4[5] = Y(yb, z1);
        q4[6] = X(u0, z1);
        q4[7] = q4[5];
        c.fillPoly(q4, col);
    }

    /** A wall facing east at x, spanning depths y0..y1 and heights z0..z1. */
    private void eq(Gfx c, float x, float y0, float y1, float z0, float z1, int col) {
        q4[0] = X(x, z0);
        q4[1] = Y(y1, z0);
        q4[2] = q4[0];
        q4[3] = Y(y0, z0);
        q4[4] = X(x, z1);
        q4[5] = Y(y0, z1);
        q4[6] = q4[4];
        q4[7] = Y(y1, z1);
        c.fillPoly(q4, col);
    }

    /** A flat horizontal surface at height z. */
    private void hq(Gfx c, float x0, float x1, float y0, float y1, float z, int col) {
        c.fillRect(X(x0, z), Y(y0, z), (x1 - x0) * s, (y1 - y0) * s, col);
    }

    private void box(Gfx c, float x0, float x1, float y0, float y1, float z0, float z1, int top, int front, int side) {
        eq(c, x1, y0, y1, z0, z1, side);
        fq(c, x0, x1, z0, z1, y1, front);
        hq(c, x0, x1, y0, y1, z1, top);
    }

    private void tri(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, int col) {
        q3[0] = x0;
        q3[1] = y0;
        q3[2] = x1;
        q3[3] = y1;
        q3[4] = x2;
        q3[5] = y2;
        c.fillPoly(q3, col);
    }

    /** A flat ellipse on the ground (z = 0) or at height z. */
    private void ellipse(Gfx c, float cx, float cy, float z, float rx, float ry, int col) {
        float sx = X(cx, z), sy = Y(cy, z);
        for (int i = 0; i < 12; i++) {
            el[i * 2] = sx + RING[i * 2] * rx * s;
            el[i * 2 + 1] = sy + RING[i * 2 + 1] * ry * s;
        }
        c.fillPoly(el, col);
    }

    private void lineZ(Gfx c, float x0, float y0, float z0, float x1, float y1, float z1, float w, int col) {
        c.line(X(x0, z0), Y(y0, z0), X(x1, z1), Y(y1, z1), w * s, col);
    }

    /** Converts a building rectangle (its drawn block) and wall height into its footprint. */
    private void setBox(float bx, float by, float bw, float bh, float h) {
        hz = h;
        fx0 = bx + LEAN * h;
        fx1 = bx + bw;
        fy0 = by + h;
        fy1 = by + bh;
    }

    private void body(Gfx c, int front, int side) {
        eq(c, fx1, fy0, fy1, 0, hz, lit(side));
        fq(c, fx0, fx1, 0, hz, fy1, lit(front));
    }

    private void eaveShade(Gfx c) {
        fq(c, fx0, fx1, hz - 2.5f, hz, fy1, 0x30000000);
    }

    private void plinth(Gfx c, int col, float h) {
        fq(c, fx0, fx1, 0, h, fy1, lit(col));
    }

    private void roofFlat(Gfx c, int rim, int inner) {
        hq(c, fx0 - 0.8f, fx1 + 0.4f, fy0, fy1 + 0.8f, hz, lit(rim));
        hq(c, fx0 + 2.2f, fx1 - 2.2f, fy0 + 2.2f, fy1 - 1.6f, hz, lit(inner));
    }

    /** A pitched roof with its ridge along x. */
    private void roofGable(Gfx c, float hr, int back, int front, int end) {
        float ym = (fy0 + fy1) / 2, zr = hz + hr;
        tri(c, X(fx1, hz), Y(fy1, hz), X(fx1, hz), Y(fy0, hz), X(fx1, zr), Y(ym, zr), lit(end));
        q4[0] = X(fx0 - 1.5f, hz);
        q4[1] = Y(fy0, hz);
        q4[2] = X(fx1 + 1, hz);
        q4[3] = q4[1];
        q4[4] = X(fx1 + 1, zr);
        q4[5] = Y(ym, zr);
        q4[6] = X(fx0 - 1.5f, zr);
        q4[7] = q4[5];
        c.fillPoly(q4, lit(back));
        q4[0] = X(fx0 - 1.5f, zr);
        q4[1] = Y(ym, zr);
        q4[2] = X(fx1 + 1, zr);
        q4[3] = q4[1];
        q4[4] = X(fx1 + 1, hz - 1);
        q4[5] = Y(fy1 + 2, hz - 1);
        q4[6] = X(fx0 - 1.5f, hz - 1);
        q4[7] = q4[5];
        c.fillPoly(q4, lit(front));
        c.line(X(fx0 - 1.5f, zr), Y(ym, zr), X(fx1 + 1, zr), Y(ym, zr), 1.3f * s, lit(Ui.darken(front, 0.7f)));
    }

    /** A window on the front wall: glass by day, dark at night unless it is lit. */
    private void window(Gfx c, float u0, float u1, float z0, float z1, boolean on, int warm) {
        int glass = lit(GLASS);
        int col = on ? mix(glass, warm, Math.min(1, dark * 1.8f)) : glass;
        fq(c, u0, u1, z0, z1, fy1, col);
        if (dark < 0.5f && u1 - u0 > 8.5f) {
            // Daylight reflection: one diagonal sliver per pane.
            q3[0] = X(u0, z1);
            q3[1] = Y(fy1, z1);
            q3[2] = X(u0 + (u1 - u0) * 0.55f, z1);
            q3[3] = q3[1];
            q3[4] = X(u0, z0 + (z1 - z0) * 0.45f);
            q3[5] = Y(fy1, z0 + (z1 - z0) * 0.45f);
            c.fillPoly(q3, alpha(0x66FFFFFF, 1 - dark * 2));
        }
    }

    /** Whether window i of a building shows light now. Stable per window; changes only with the clock. */
    private boolean lightOn(int id, int i, boolean open) {
        if (dark < 0.2f) return false;
        float h = hash(id * 131 + i * 17 + 5);
        if (id == City.HOME) {
            float hr = minute / 60f, occ;
            if (hr >= 17 && hr < 23) occ = 0.7f;
            else if (hr >= 23 || hr < 1) occ = 0.45f;
            else if (hr < 5) occ = 0.12f;
            else occ = 0.35f;
            return h < occ;
        }
        if (!open) return h < 0.06f;
        return h > 0.08f;
    }

    private int warmFor(int id, int i) {
        float h = hash(id * 71 + i * 29 + 3);
        if (id == City.HOME && h < 0.25f) return TV;
        return h < 0.5f ? WARM : WARM2;
    }

    /** A door in the front wall, centred on u. Doors of open places glow a little at night. */
    private void door(Gfx c, float u, float w, float h, int col, boolean open) {
        fq(c, u - w / 2 - 1, u + w / 2 + 1, 0, h + 1.2f, fy1, lit(Ui.darken(col, 0.6f)));
        fq(c, u - w / 2, u + w / 2, 0, h, fy1, open && dark > 0.3f ? mix(lit(col), WARM2, dark * 0.55f) : lit(col));
        c.line(X(u, 0), Y(fy1, 0), X(u, h), Y(fy1, h), 0.7f * s, lit(Ui.darken(col, 0.55f)));
        if (open && dark > 0.25f) {
            float bx = X(u, h + 3), by = Y(fy1, h + 3);
            c.fillCircle(bx, by, 5 * s, alpha(0x66FFD27A, dark));
            c.fillCircle(bx, by, 1.4f * s, 0xFFFFF0C0);
        }
    }

    /** A rolling shutter pulled down over part of the front wall: the universal "closed". */
    private void shutter(Gfx c, float u0, float u1, float z0, float z1) {
        fq(c, u0, u1, z0, z1, fy1, lit(0xFF8A8F96));
        for (float z = z0 + 2.2f; z < z1 - 0.5f; z += 2.6f) {
            c.line(X(u0, z), Y(fy1, z), X(u1, z), Y(fy1, z), 0.6f * s, lit(0xFF5E636A));
        }
        fq(c, u0, u1, z1 - 1.4f, z1, fy1, lit(0xFF5E636A));
    }

    private void closedTag(Gfx c, float u, float z) {
        float ts = Math.max(9f, 9.5f * s);
        float w = c.textWidth("סגור", ts) + 6, hh = ts * 1.35f;
        float cx = X(u, z), cy = Y(fy1, z);
        c.fillRoundRect(cx - w / 2, cy - hh / 2, w, hh, 2, 0xFFB8443A);
        c.text("סגור", cx, cy + ts * 0.36f, ts, 0xFFFFF1E6, Gfx.CENTER);
    }

    /** The name plate. Signs of open places are lit at night; closed ones go dim but stay readable. */
    private void sign(Gfx c, String txt, float u, float z, float yb, int plate, int ink, boolean open) {
        float ts = Math.max(11f, 12f * s);
        float w = c.textWidth(txt, ts) + 12, hh = ts * 1.55f;
        float cx = X(u, z), cy = Y(yb, z);
        int pc = open ? mix(lit(plate), plate, 0.75f) : Ui.darken(lit(plate), 0.8f);
        int ic = open ? ink : mix(ink, 0xFF9AA0A8, 0.45f);
        if (open && dark > 0.3f) c.fillRoundRect(cx - w / 2 - 3, cy - hh / 2 - 3, w + 6, hh + 6, 6, alpha(0x44FFE2A0, dark));
        c.fillRoundRect(cx - w / 2 + 1, cy - hh / 2 + 1.6f, w, hh, 3.5f, 0x66000000);
        c.fillRoundRect(cx - w / 2, cy - hh / 2, w, hh, 3.5f, pc);
        c.strokeRoundRect(cx - w / 2 + 1.5f, cy - hh / 2 + 1.5f, w - 3, hh - 3, 2.5f, 0.8f, alpha(ic, 0.45f));
        c.text(txt, cx, cy + ts * 0.37f, ts, ic, Gfx.CENTER);
    }

    // ================= ground =================

    /** Grass, sidewalks, roads, markings, paving, shadows and pools of light; top/bottom = map area. */
    void ground(Gfx c, float top, float bottom) {
        vx0 = -ox / s - 2;
        vx1 = (Game.W - ox) / s + 2;
        vy0 = (top - oy) / s - 2;
        vy1 = (bottom - oy) / s + 2;
        c.fillRect(0, top, Game.W, bottom - top, lit(GRASS));
        // Mown patches give the lots some texture without per-blade cost.
        for (int i = 0; i < 6; i++) {
            float px = hash(i * 3 + 1) * 380 - 10, py = hash(i * 3 + 2) * 560;
            ellipse(c, px, py, 0, 10 + hash(i * 7) * 14, 5 + hash(i * 5) * 5, lit(i % 2 == 0 ? GRASS_DK : GRASS_LT));
        }
        int walk = lit(WALK), edge = lit(WALK_EDGE), road = lit(ROAD), roadEdge = lit(ROAD_EDGE), paint = lit(PAINT);
        for (float r : City.ROADS_H) c.fillRect(X(vx0, 0), Y(r - BAND, 0), (vx1 - vx0) * s, BAND * 2 * s, walk);
        for (float v : City.ROADS_V) c.fillRect(X(v - BAND, 0), Y(vy0, 0), BAND * 2 * s, (vy1 - vy0) * s, walk);
        for (float r : City.ROADS_H) {
            c.fillRect(X(vx0, 0), Y(r - ASPH - 1, 0), (vx1 - vx0) * s, (ASPH * 2 + 2) * s, edge);
        }
        for (float v : City.ROADS_V) c.fillRect(X(v - ASPH - 1, 0), Y(vy0, 0), (ASPH * 2 + 2) * s, (vy1 - vy0) * s, edge);
        for (float r : City.ROADS_H) c.fillRect(X(vx0, 0), Y(r - ASPH, 0), (vx1 - vx0) * s, ASPH * 2 * s, road);
        for (float v : City.ROADS_V) c.fillRect(X(v - ASPH, 0), Y(vy0, 0), ASPH * 2 * s, (vy1 - vy0) * s, road);
        // Worn asphalt along the wheel tracks.
        for (float r : City.ROADS_H) {
            c.fillRect(X(vx0, 0), Y(r - 4.6f, 0), (vx1 - vx0) * s, 1.2f * s, roadEdge);
            c.fillRect(X(vx0, 0), Y(r + 3.4f, 0), (vx1 - vx0) * s, 1.2f * s, roadEdge);
        }
        // Centre dashes, skipping the junctions.
        for (float r : City.ROADS_H) {
            for (float x = vx0 - (vx0 % 30) - 30; x < vx1; x += 30) {
                if (nearV(x + 4, 22)) continue;
                c.fillRect(X(x, 0), Y(r - 0.6f, 0), 9 * s, 1.2f * s, paint);
            }
        }
        for (float v : City.ROADS_V) {
            for (float y = vy0 - (vy0 % 30) - 30; y < vy1; y += 30) {
                if (nearH(y + 4, 22)) continue;
                c.fillRect(X(v - 0.6f, 0), Y(y, 0), 1.2f * s, 9 * s, paint);
            }
        }
        // Zebra crossings: one across each street just west of a junction, one across each avenue south of it.
        int zebra = lit(0xFFE8E2D2);
        for (float r : City.ROADS_H) {
            for (float v : City.ROADS_V) {
                for (int k = 0; k < 3; k++) {
                    c.fillRect(X(v - ASPH - 9, 0), Y(r - ASPH + 1.5f + k * 5, 0), 6.5f * s, 3 * s, zebra);
                    c.fillRect(X(v - ASPH + 1.5f + k * 5, 0), Y(r + ASPH + 2.5f, 0), 3 * s, 6.5f * s, zebra);
                }
            }
        }
        // Paved yards: the restaurant's terrace, the gas station's forecourt, the alley's floor.
        c.fillRect(X(54, 0), Y(198, 0), 32 * s, 42 * s, lit(0xFFD9B79A));
        for (int k = 0; k < 4; k++) c.fillRect(X(54, 0), Y(205 + k * 9, 0), 32 * s, 0.6f * s, lit(0xFFC59F82));
        c.fillRect(X(188, 0), Y(466, 0), 64 * s, 49 * s, lit(PAVING));
        ellipse(c, 214, 507, 0, 6, 2.5f, alpha(lit(0xFF5A5E66), 0.5f));
        c.fillRect(X(204, 0), Y(492, 0), 34 * s, 7 * s, lit(0xFFDAD5CA));
        c.fillRect(X(324, 0), Y(470, 0), 24 * s, 45 * s, lit(0xFF2B2F35));
        // Door steps: a lighter slab from each door to the curb, so the entrance reads at a glance.
        for (City.Building b : City.B) {
            float y0 = b.y + b.h;
            c.fillRect(X(b.doorX - 6, 0), Y(y0, 0), 12 * s, (b.doorY - ASPH - 1 - y0) * s, lit(0xFFE4DCCB));
        }
        shadows(c);
        if (dark > 0.05f) pools(c);
    }

    private boolean nearV(float x, float d) {
        for (float v : City.ROADS_V) if (Math.abs(x - v) < d) return true;
        return false;
    }

    private boolean nearH(float y, float d) {
        for (float r : City.ROADS_H) if (Math.abs(y - r) < d) return true;
        return false;
    }

    private void shadows(Gfx c) {
        int sh = alpha(SHADOW, 1 - dark * 0.75f);
        for (City.Building b : City.B) {
            float h = wallHeight(b.id);
            if (b.id == City.GAS) {
                buildingShadow(c, 197, 246, 486, 508, 25, sh);
                buildingShadow(c, 255, 288, 494, 515, 24, sh);
            } else if (b.id == City.ALLEY) {
                buildingShadow(c, 345.6f, 366, 470, 515, 30, sh);
            } else if (b.id == City.RESTAURANT) {
                buildingShadow(c, 92, 162, 214, 240, 28, sh);
            } else {
                buildingShadow(c, b.x + LEAN * h, b.x + b.w, b.y + h, b.y + b.h, h, sh);
            }
        }
        for (int i = 0; i < TREES.length; i += 3) {
            float r = TREES[i + 2];
            ellipse(c, TREES[i] + r * 0.9f, TREES[i + 1] + 1, 0, r * 1.15f, r * 0.45f, sh);
        }
    }

    /** The ground shadow of a box: the footprint swept toward the lower right. */
    private void buildingShadow(Gfx c, float x0, float x1, float y0, float y1, float h, int col) {
        float dx = h * 0.42f, dy = h * 0.12f;
        q6[0] = X(x0, 0);
        q6[1] = Y(y0, 0);
        q6[2] = X(x1, 0);
        q6[3] = Y(y0, 0);
        q6[4] = X(x1 + dx, 0);
        q6[5] = Y(y0 + dy, 0);
        q6[6] = X(x1 + dx, 0);
        q6[7] = Y(y1 + dy, 0);
        q6[8] = X(x0 + dx, 0);
        q6[9] = Y(y1 + dy, 0);
        q6[10] = X(x0, 0);
        q6[11] = Y(y1, 0);
        c.fillPoly(q6, col);
    }

    /** Light on the ground: street lamps, the gas canopy, open doorways. */
    private void pools(Gfx c) {
        int pool = alpha(0x2CFFD98A, dark);
        for (float r : City.ROADS_H) {
            for (float v : City.ROADS_V) {
                ellipse(c, v - 8, r - 10, 0, 19, 10, pool);
                ellipse(c, v - 8, r - 11, 0, 10, 5.5f, pool);
            }
        }
        if (dark > 0.3f) {
            c.fillRect(X(193, 0), Y(482, 0), 56 * s, 30 * s, alpha(0x38F4FBFF, dark));
            for (City.Building b : City.B) {
                if (b.isOpen(minute) && b.id != City.ALLEY) {
                    ellipse(c, b.doorX, b.y + b.h + 3, 0, 9, 4, alpha(0x30FFD27A, dark));
                }
            }
        }
    }

    static float wallHeight(int id) {
        switch (id) {
            case City.SCHOOL:
            case City.CASINO:
                return 34;
            case City.HOME:
                return 64;
            case City.MARKET:
                return 26;
            case City.GAS:
                return 30;
            case City.ALLEY:
                return 30;
            default:
                return 28;
        }
    }

    // ================= standing things =================

    /** Buildings, trees, lamps, cars and walkers, back to front; then birds and smoke on top. */
    void scene(Gfx c, float top, float bottom) {
        n = 0;
        for (City.Building b : City.B) add(b.y + b.h, K_BUILDING, b.id, 0, 0);
        for (int i = 0; i < TREES.length; i += 3) add(TREES[i + 1], K_TREE, i, 0, 0);
        for (int i = 0; i < BUSHES.length; i += 3) if (BUSHES[i + 2] > 0) add(BUSHES[i + 1], K_BUSH, i, 0, 0);
        for (float r : City.ROADS_H) for (float v : City.ROADS_V) add(r - 13, K_LAMP, 0, v - 13, r - 13);
        cars();
        walkers();
        for (int i = 1; i < n; i++) {
            int o = order[i], j = i - 1;
            while (j >= 0 && key[order[j]] > key[o]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = o;
        }
        for (int i = 0; i < n; i++) {
            int o = order[i];
            switch (kind[o]) {
                case K_BUILDING:
                    building(c, arg[o]);
                    break;
                case K_TREE:
                    tree(c, TREES[arg[o]], TREES[arg[o] + 1], TREES[arg[o] + 2], arg[o]);
                    break;
                case K_BUSH:
                    bush(c, BUSHES[arg[o]], BUSHES[arg[o] + 1], BUSHES[arg[o] + 2]);
                    break;
                case K_LAMP:
                    lamp(c, ax[o], ay[o]);
                    break;
                case K_CAR:
                    car(c, ax[o], ay[o], arg[o] & 3, CARS[(arg[o] >> 2) % CARS.length]);
                    break;
                default:
                    walker(c, ax[o], ay[o], arg[o]);
                    break;
            }
        }
        smoke(c);
        if (dark < 0.6f) birds(c, top);
    }

    private void add(float k, int kd, int a, float x, float y) {
        if (n >= MAX) return;
        key[n] = k;
        kind[n] = kd;
        arg[n] = a;
        ax[n] = x;
        ay[n] = y;
        order[n] = n;
        n++;
    }

    private void cars() {
        int lane = 0;
        for (float r : City.ROADS_H) {
            for (int dir = 0; dir < 2; dir++, lane++) {
                float y = dir == 0 ? r + 4 : r - 4, len = vx1 - vx0 + 40;
                for (int k = 0; k < 2; k++) {
                    int seed = lane * 7 + k;
                    float p = travel(seed, len);
                    if (p < 0) continue;
                    float x = dir == 0 ? vx0 - 20 + p : vx1 + 20 - p;
                    add(y + 3.5f, K_CAR, dir | (carColour(seed) << 2), x, y);
                }
            }
        }
        for (float v : City.ROADS_V) {
            for (int dir = 2; dir < 4; dir++, lane++) {
                float x = dir == 2 ? v - 4 : v + 4, len = vy1 - vy0 + 40;
                int seed = lane * 7;
                float p = travel(seed, len);
                if (p < 0) continue;
                float y = dir == 2 ? vy0 - 20 + p : vy1 + 20 - p;
                add(y + 7, K_CAR, dir | (carColour(seed) << 2), x, y);
            }
        }
    }

    private int lap;

    /** Distance along a lane, or -1 when this lap of the loop has no car (decided only off-screen). */
    private float travel(int seed, float len) {
        float speed = 20 + 16 * hash(seed * 3 + 1);
        float d = hash(seed * 5 + 2) * len + t * speed;
        lap = (int) Math.floor(d / len);
        if (hash(seed * 131 + lap * 17 + 9) > density) return -1;
        return d - lap * len;
    }

    private int carColour(int seed) {
        return (int) (hash(seed * 11 + lap * 3) * CARS.length) % CARS.length;
    }

    private void walkers() {
        int lane = 0;
        for (float r : City.ROADS_H) {
            for (int side = 0; side < 2; side++, lane++) {
                float y = side == 0 ? r - 11.5f : r + 11.5f, len = vx1 - vx0 + 20;
                for (int k = 0; k < 2; k++) {
                    int seed = lane * 5 + k + 100;
                    float p = stroll(seed, len);
                    if (p < 0) continue;
                    boolean east = hash(seed + 3) < 0.5f;
                    add(y, K_PED, seed, east ? vx0 - 10 + p : vx1 + 10 - p, y);
                }
            }
        }
        for (float v : City.ROADS_V) {
            for (int side = 0; side < 2; side++, lane++) {
                float x = side == 0 ? v - 11.5f : v + 11.5f, len = vy1 - vy0 + 20;
                int seed = lane * 5 + 100;
                float p = stroll(seed, len);
                if (p < 0) continue;
                float y = hash(seed + 3) < 0.5f ? vy0 - 10 + p : vy1 + 10 - p;
                add(y, K_PED, seed, x, y);
            }
        }
    }

    private float stroll(int seed, float len) {
        float speed = 4.5f + 3 * hash(seed * 3 + 1);
        float d = hash(seed * 5 + 2) * len + t * speed;
        int l = (int) Math.floor(d / len);
        if (hash(seed * 37 + l * 13 + 1) > walkers) return -1;
        return d - l * len;
    }

    private void walker(Gfx c, float x, float y, int seed) {
        float sx = X(x, 0), sy = Y(y, 0);
        c.fillCircle(sx + 1.2f * s, sy, 2.6f * s, alpha(SHADOW, 1.2f - dark));
        int col = lit(CLOTHES[(int) (hash(seed * 13) * CLOTHES.length) % CLOTHES.length]);
        Ui.person(c, sx, sy, 0.6f * s, hash(seed * 7) < 0.5f, t * 0.8f + hash(seed) * 3, true, col);
    }

    private void tree(Gfx c, float x, float y, float r, int i) {
        float sway = (float) Math.sin(t * 1.3f + i * 0.7f) * 0.06f * r;
        float kind = hash(i * 3 + 7);
        if (kind < 0.18f) {
            // Cypress: tall, dark and narrow.
            float top = r * 3.2f;
            c.fillRect(X(x, 0) - 0.9f * s, Y(y, 2.5f), 1.8f * s, 2.5f * s, lit(TRUNK));
            q6[0] = X(x, 1.5f);
            q6[1] = Y(y, 1.5f);
            q6[2] = X(x + r * 0.55f, r * 1.1f);
            q6[3] = Y(y, r * 1.1f);
            q6[4] = X(x + r * 0.15f + sway, top);
            q6[5] = Y(y, top);
            q6[6] = X(x - r * 0.05f + sway, top + 1);
            q6[7] = Y(y, top + 1);
            q6[8] = X(x - r * 0.5f, r * 1.3f);
            q6[9] = Y(y, r * 1.3f);
            q6[10] = X(x - r * 0.25f, 1.5f);
            q6[11] = Y(y, 1.5f);
            c.fillPoly(q6, lit(0xFF3A6A3A));
            c.line(X(x - r * 0.2f, r * 0.8f), Y(y, r * 0.8f), X(x - r * 0.05f + sway, top * 0.85f), Y(y, top * 0.85f),
                    1.2f * s, lit(0xFF4F8A48));
            return;
        }
        if (kind > 0.84f) {
            // Palm: a leaning trunk and a crown of fronds that move in the breeze.
            float top = r * 2.6f, lean = r * 0.35f + sway;
            c.line(X(x, 0), Y(y, 0), X(x + lean, top), Y(y, top), 2 * s, lit(0xFF8A6A48));
            float hx = X(x + lean, top), hy = Y(y, top);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + 0.3 + Math.sin(t * 1.1f + k + i) * 0.08;
                float ex = hx + (float) Math.cos(a) * r * 1.15f * s, ey = hy + (float) Math.sin(a) * r * 0.6f * s + 2.5f * s;
                c.line(hx, hy, ex, ey, 2.2f * s, lit(k % 2 == 0 ? LEAF : LEAF_DK));
            }
            c.fillCircle(hx, hy, 1.6f * s, lit(0xFF6E5034));
            return;
        }
        float trunkH = r * 0.9f;
        c.fillRect(X(x, 0) - 1.1f * s, Y(y, trunkH), 2.2f * s, trunkH * s, lit(TRUNK));
        float cx = x + sway, cz = trunkH + r * 0.75f;
        c.fillCircle(X(cx, cz), Y(y, cz), r * s, lit(LEAF_DK));
        c.fillCircle(X(cx - r * 0.18f, cz + r * 0.2f), Y(y, cz + r * 0.2f), r * 0.78f * s, lit(LEAF));
        c.fillCircle(X(cx - r * 0.38f, cz + r * 0.45f), Y(y, cz + r * 0.45f), r * 0.38f * s, lit(LEAF_LT));
    }

    private void bush(Gfx c, float x, float y, float r) {
        c.fillCircle(X(x, 0), Y(y, r * 0.8f), r * s, lit(LEAF_DK));
        c.fillCircle(X(x - r * 0.3f, r), Y(y, r * 1.05f), r * 0.6f * s, lit(LEAF));
    }

    private void lamp(Gfx c, float x, float y) {
        int pole = lit(0xFF3C424B);
        lineZ(c, x, y, 0, x, y, 24, 1.3f, pole);
        lineZ(c, x, y, 24, x + 5, y, 24.5f, 1.1f, pole);
        float hx = X(x + 5, 23.5f), hy = Y(y, 23.5f);
        if (dark > 0.3f) {
            c.fillCircle(hx, hy, 9 * s, alpha(0x22FFDC8C, dark));
            c.fillCircle(hx, hy, 4 * s, alpha(0x44FFE6A8, dark));
            c.fillCircle(hx, hy, 1.7f * s, 0xFFFFF4CC);
        } else {
            c.fillCircle(hx, hy, 1.7f * s, lit(0xFFDDE2E8));
        }
    }

    private void car(Gfx c, float x, float y, int dir, int col) {
        boolean horiz = dir < 2;
        float hl = horiz ? 7.5f : 3.6f, hd = horiz ? 3.4f : 7;
        float x0 = x - hl, x1 = x + hl, y0 = y - hd, y1 = y + hd;
        c.fillRect(X(x0 + 1.8f, 0), Y(y0 + 1.2f, 0), hl * 2 * s, hd * 2 * s, alpha(SHADOW, 1.3f - dark));
        box(c, x0, x1, y0, y1, 0.8f, 4.2f, lit(col), lit(Ui.darken(col, 0.72f)), lit(Ui.darken(col, 0.55f)));
        int glass = lit(0xFF2E3A48);
        if (horiz) {
            float back = dir == 0 ? -1 : 1;
            float cx0 = x - 4 + back * 1.2f, cx1 = x + 4 + back * 1.2f;
            box(c, cx0, cx1, y0 + 0.8f, y1 - 0.8f, 4.2f, 7, lit(mix(col, 0xFFFFFFFF, 0.25f)), glass, glass);
        } else {
            float back = dir == 2 ? -1 : 1;
            float cy0 = y - 3.6f + back * 1.2f, cy1 = y + 3.6f + back * 1.2f;
            box(c, x0 + 0.7f, x1 - 0.7f, cy0, cy1, 4.2f, 7, lit(mix(col, 0xFFFFFFFF, 0.25f)), glass, glass);
        }
        if (dark > 0.3f) {
            int beam = alpha(0x2AFFF1B8, dark);
            float z = 2;
            if (dir == 0) beam(c, x1, y, 1, 0, beam, z);
            else if (dir == 1) beam(c, x0, y, -1, 0, beam, z);
            else if (dir == 2) beam(c, x, y1, 0, 1, beam, z);
            else beam(c, x, y0, 0, -1, beam, z);
            int tail = 0xFFFF4A3A;
            if (dir == 0) fq(c, x0 - 0.3f, x0 + 0.8f, 1.5f, 3.2f, y1, tail);
            else if (dir == 1) fq(c, x1 - 0.8f, x1 + 0.3f, 1.5f, 3.2f, y1, tail);
            else if (dir == 3) fq(c, x0 + 0.5f, x1 - 0.5f, 1.5f, 3f, y1, tail);
        }
    }

    private void beam(Gfx c, float x, float y, float dx, float dy, int col, float z) {
        float len = 16, wNear = 2.6f, wFar = 6.5f;
        float px = -dy, py = dx;
        q4[0] = X(x + px * wNear, z);
        q4[1] = Y(y + py * wNear, z);
        q4[2] = X(x + dx * len + px * wFar, 0);
        q4[3] = Y(y + dy * len + py * wFar, 0);
        q4[4] = X(x + dx * len - px * wFar, 0);
        q4[5] = Y(y + dy * len - py * wFar, 0);
        q4[6] = X(x - px * wNear, z);
        q4[7] = Y(y - py * wNear, z);
        c.fillPoly(q4, col);
    }

    private void birds(Gfx c, float top) {
        int col = alpha(lit(0xFF2E3640), 1 - dark * 1.5f);
        for (int i = 0; i < 3; i++) {
            float x = ((t * 16 + i * 13) % 520) - 80 + i * 9, y = 150 + i * 7 + (float) Math.sin(t * 0.7f + i) * 6;
            float flap = (float) Math.sin(t * 9 + i * 2) * 2.2f;
            float sx = X(x, 0), sy = Y(y, 0);
            if (sy < top + 30) continue;
            c.line(sx - 3.5f * s, sy - flap * s, sx, sy, 1.1f * s, col);
            c.line(sx, sy, sx + 3.5f * s, sy - flap * s, 1.1f * s, col);
        }
    }

    private void smoke(Gfx c) {
        boolean baking = City.B[City.BAKERY].isOpen(minute);
        int puffs = baking ? 5 : 2;
        for (int k = 0; k < puffs; k++) {
            float ph = (t * 0.28f + k / (float) puffs) % 1f;
            float x = 76 + ph * 10 + (float) Math.sin(t * 1.1f + k) * 1.5f, z = 28 + 21 + ph * 26;
            c.fillCircle(X(x, z), Y(355, z), (2.2f + ph * 4.5f) * s, alpha(lit(0xFFE9E6E0), (1 - ph) * (baking ? 0.75f : 0.35f)));
        }
    }

    // ================= buildings =================

    private void building(Gfx c, int id) {
        City.Building b = City.B[id];
        boolean open = b.isOpen(minute);
        switch (id) {
            case City.SCHOOL:
                school(c, b, open);
                break;
            case City.CASINO:
                casino(c, b, open);
                break;
            case City.BAKERY:
                bakery(c, b, open);
                break;
            case City.BAR:
                bar(c, b, open);
                break;
            case City.RESTAURANT:
                restaurant(c, b, open);
                break;
            case City.MARKET:
                market(c, b, open);
                break;
            case City.GAS:
                gas(c, b);
                break;
            case City.HOME:
                home(c, b);
                break;
            default:
                alley(c, b, open);
                break;
        }
    }

    private void school(Gfx c, City.Building b, boolean open) {
        setBox(b.x, b.y, b.w, b.h, 34);
        body(c, 0xFFEAD7A2, 0xFFB79F6E);
        plinth(c, 0xFFB39C72, 3);
        fq(c, fx0, fx1, 15.5f, 17, fy1, lit(0xFFD6C089));
        float[] cols = {70, 82, 94, 122, 134, 146};
        int wi = 0;
        for (float u : cols) {
            window(c, u - 3.5f, u + 3.5f, 5, 13, lightOn(0, wi, open), warmFor(0, wi));
            wi++;
            window(c, u - 3.5f, u + 3.5f, 20, 28, lightOn(0, wi, open), warmFor(0, wi));
            wi++;
        }
        window(c, 104.5f, 111.5f, 20, 28, lightOn(0, wi, open), warmFor(0, wi));
        if (open) door(c, b.doorX, 11, 13, 0xFF3F64B0, true);
        else {
            door(c, b.doorX, 11, 13, 0xFF3F64B0, false);
            shutter(c, b.doorX - 5.5f, b.doorX + 5.5f, 0, 13);
            closedTag(c, 82, 9);
        }
        eaveShade(c);
        roofGable(c, 12, 0xFFDC8B63, 0xFFC56A4A, 0xFF9C4C37);
        // Clock tower: shows the real game time.
        float tx0 = 137, tx1 = 151, ty0 = fy0 + 5, ty1 = fy1 - 4, tz = hz + 6, tt = hz + 19;
        box(c, tx0, tx1, ty0, ty1, tz, tt, lit(0xFFE3CF97), lit(0xFFEAD7A2), lit(0xFFB79F6E));
        float ax0 = X(tx0 - 1, tt), ay0 = Y(ty1 + 1, tt), ax1 = X(tx1 + 1, tt), apx = X((tx0 + tx1) / 2, tt + 9),
                apy = Y((ty0 + ty1) / 2, tt + 9);
        tri(c, X(tx1 + 1, tt), Y(ty1 + 1, tt), X(tx1 + 1, tt), Y(ty0, tt), apx, apy, lit(0xFF9C4C37));
        tri(c, ax0, ay0, ax1, ay0, apx, apy, lit(0xFFC56A4A));
        float ccx = X(144, tz + 6.5f), ccy = Y(ty1, tz + 6.5f);
        c.fillCircle(ccx, ccy, 4.6f * s, lit(0xFF7A5A3A));
        c.fillCircle(ccx, ccy, 3.8f * s, open && dark > 0.3f ? 0xFFFFF3D2 : lit(0xFFF8F4EA));
        double hA = (minute / 60.0 % 12) / 12 * Math.PI * 2, mA = (minute % 60) / 60.0 * Math.PI * 2;
        int hand = 0xFF2A2420;
        c.line(ccx, ccy, ccx + (float) Math.sin(hA) * 2.1f * s, ccy - (float) Math.cos(hA) * 2.1f * s, 0.9f * s, hand);
        c.line(ccx, ccy, ccx + (float) Math.sin(mA) * 3.1f * s, ccy - (float) Math.cos(mA) * 3.1f * s, 0.6f * s, hand);
        // Pennant on the tower.
        float pz = tt + 9, px = (tx0 + tx1) / 2, py = (ty0 + ty1) / 2;
        lineZ(c, px, py, pz, px, py, pz + 10, 0.8f, lit(0xFF5A5048));
        float wave = (float) Math.sin(t * 3.2f) * 1.3f;
        tri(c, X(px, pz + 10), Y(py, pz + 10), X(px, pz + 6), Y(py, pz + 6), X(px + 9, pz + 8 + wave), Y(py, pz + 8 + wave),
                lit(0xFF4C7BD0));
        sign(c, b.name, 100, hz + 2.5f, fy1, 0xFF2D4C8C, 0xFFFFF6DE, open);
    }

    private void casino(Gfx c, City.Building b, boolean open) {
        setBox(b.x, b.y, b.w, b.h, 34);
        body(c, 0xFF6A3484, 0xFF46205A);
        plinth(c, 0xFF3A1A48, 2.5f);
        fq(c, fx0, fx1, hz - 4, hz - 1.5f, fy1, lit(0xFFE2B84E));
        boolean night = open && dark > 0.2f;
        float[] cols = {208, 222, 266, 280};
        for (int i = 0; i < cols.length; i++) {
            float u = cols[i];
            boolean on = open && lightOn(1, i, true);
            int col = on ? mix(lit(0xFF9A7CB6), 0xFFFFCF6A, Math.min(1, dark * 1.8f)) : lit(0xFF7E679A);
            fq(c, u - 4, u + 4, 6, 22, fy1, col);
            c.fillCircle(X(u, 22), Y(fy1, 22), 4 * s, col);
            if (!open) shutter(c, u - 4, u + 4, 6, 18);
        }
        door(c, b.doorX, 12, 14, 0xFFD9B04A, open);
        if (!open) {
            shutter(c, b.doorX - 6, b.doorX + 6, 0, 14);
            closedTag(c, 215, 3.5f);
        }
        // Marquee over the door with chasing bulbs.
        float mu0 = b.doorX - 17, mu1 = b.doorX + 17, my = fy1 + 7;
        box(c, mu0, mu1, fy1, my, 15, 19.5f, lit(0xFF8E3FA8), lit(0xFFE2B84E), lit(0xFFB08A2E));
        int step = (int) (t * 7);
        for (int i = 0; i < 10; i++) {
            float u = mu0 + 2 + i * 3.33f;
            boolean hot = open && ((i + step) % 3 == 0);
            int bc = !open ? lit(0xFF8C7A50) : hot ? 0xFFFFF6C8 : night ? 0xFFE0A030 : lit(0xFFF4D98A);
            float bx = X(u, 17.2f), by = Y(my, 17.2f);
            if (hot && night) c.fillCircle(bx, by, 2.6f * s, alpha(0x88FFE08A, dark));
            c.fillCircle(bx, by, 0.95f * s, bc);
        }
        eaveShade(c);
        roofFlat(c, 0xFF7A4294, 0xFF552A6A);
        // Rooftop diamond on legs: the casino's silhouette.
        float du = 262, dy = fy0 + 4, dz = hz + 14;
        lineZ(c, du - 4, dy, hz, du - 4, dy, dz - 4, 1, lit(0xFF3A1A48));
        lineZ(c, du + 4, dy, hz, du + 4, dy, dz - 4, 1, lit(0xFF3A1A48));
        int gold = open && dark > 0.2f ? 0xFFFFD45E : lit(0xFFE2B84E);
        if (open && dark > 0.2f) c.fillCircle(X(du, dz), Y(dy, dz), 14 * s, alpha(0x44FFC04A, dark));
        diamond(c, du, dy, dz, 11, 9, gold);
        diamond(c, du, dy, dz, 7, 5.6f, open && dark > 0.2f ? 0xFFFF5A6A : lit(0xFFC8384A));
        sign(c, b.name, b.doorX, hz + 1.5f, fy1, 0xFF2E103F, 0xFFFFD970, open);
    }

    private void diamond(Gfx c, float u, float y, float z, float hw, float hh, int col) {
        q4[0] = X(u, z + hh);
        q4[1] = Y(y, z + hh);
        q4[2] = X(u + hw, z);
        q4[3] = Y(y, z);
        q4[4] = X(u, z - hh);
        q4[5] = Y(y, z - hh);
        q4[6] = X(u - hw, z);
        q4[7] = Y(y, z);
        c.fillPoly(q4, col);
    }

    private void bakery(Gfx c, City.Building b, boolean open) {
        setBox(b.x, b.y, b.w, b.h, 28);
        body(c, 0xFFF2E0C2, 0xFFC4AE8C);
        plinth(c, 0xFF9A6A48, 3.5f);
        // Display window with loaves.
        fq(c, 103, 147, 4, 18, fy1, lit(0xFF7A4B2E));
        boolean lights = open && dark > 0.15f;
        fq(c, 104.5f, 145.5f, 5.5f, 16.5f, fy1, lights ? mix(lit(GLASS), 0xFFFFD99A, Math.min(1, dark * 1.8f)) : lit(GLASS));
        if (open) {
            fq(c, 104.5f, 145.5f, 7.5f, 8.3f, fy1, lit(0xFF8A5A36));
            for (int i = 0; i < 4; i++) {
                float u = 110 + i * 10;
                c.fillRoundRect(X(u - 3.6f, 10.6f), Y(fy1, 10.6f), 7.2f * s, 3 * s, 1.5f * s, lit(i % 2 == 0 ? 0xFFD49A52 : 0xFFC07F3C));
            }
        } else {
            shutter(c, 104.5f, 145.5f, 5.5f, 16.5f);
        }
        window(c, 70, 78, 8, 16, lightOn(2, 1, open), WARM);
        door(c, b.doorX, 10, 13, 0xFF7A4B2E, open);
        if (!open) {
            shutter(c, b.doorX - 5, b.doorX + 5, 0, 13);
            closedTag(c, 125, 11);
        }
        // Striped awning over the display.
        for (int i = 0; i < 8; i++) {
            float u0 = 101 + i * 6, u1 = u0 + 6;
            q4[0] = X(u0, 21);
            q4[1] = Y(fy1, 21);
            q4[2] = X(u1, 21);
            q4[3] = q4[1];
            q4[4] = X(u1, 17.5f);
            q4[5] = Y(fy1 + 5, 17.5f);
            q4[6] = X(u0, 17.5f);
            q4[7] = q4[5];
            c.fillPoly(q4, lit(i % 2 == 0 ? 0xFF9C5A3C : 0xFFF4E6CC));
        }
        eaveShade(c);
        roofGable(c, 10, 0xFFC27A54, 0xFFA65A3A, 0xFF7E4129);
        // Chimney.
        box(c, 72, 80, 352, 358, hz + 3, hz + 21, lit(0xFF5A2A1E), lit(0xFFA34E3A), lit(0xFF7A3828));
        sign(c, b.name, 112, hz + 1, fy1, 0xFF5E331D, 0xFFFFEBC8, open);
    }

    private void bar(Gfx c, City.Building b, boolean open) {
        setBox(b.x, b.y, b.w, b.h, 28);
        body(c, 0xFF3D4E58, 0xFF27343B);
        plinth(c, 0xFF222B30, 2.5f);
        // A few brick courses.
        for (int i = 0; i < 4; i++) {
            float z = 4 + i * 6;
            c.line(X(fx0 + 2 + (i % 2) * 6, z), Y(fy1, z), X(fx0 + 14 + (i % 2) * 6, z), Y(fy1, z), 0.6f * s, lit(0xFF324149));
        }
        float[] cols = {212, 233};
        for (int i = 0; i < 2; i++) {
            boolean on = open && dark > 0.15f;
            int col = on ? mix(lit(0xFF6A6050), 0xFFFFB45A, Math.min(1, dark * 1.8f)) : lit(0xFF4E6470);
            fq(c, cols[i] - 6, cols[i] + 6, 7, 17, fy1, col);
            fq(c, cols[i] - 6, cols[i] + 6, 6, 7, fy1, lit(0xFF1C2328));
            if (!open) shutter(c, cols[i] - 6, cols[i] + 6, 7, 17);
        }
        door(c, b.doorX, 9, 13, 0xFF7A2E2E, open);
        if (!open) {
            shutter(c, b.doorX - 4.5f, b.doorX + 4.5f, 0, 13);
            closedTag(c, 222, 11);
        }
        eaveShade(c);
        roofFlat(c, 0xFF4A5B63, 0xFF2F3B41);
        box(c, 254, 266, fy0 + 3, fy0 + 9, hz, hz + 4, lit(0xFF7D8A92), lit(0xFF66737B), lit(0xFF4E5A61));
        // Neon cocktail glass on the roof edge; it stutters now and then like old neon does.
        float cyc = t % 5.3f;
        boolean flick = cyc > 3.1f && cyc < 3.25f || cyc > 3.4f && cyc < 3.48f;
        boolean neon = open && !flick;
        int pink = neon ? 0xFFFF5FB0 : lit(0xFF7A6870), cyan = neon ? 0xFF6FF0FF : lit(0xFF6A7880);
        float gu = 211, gy = fy1 - 1, gz = hz + 4;
        if (neon && dark > 0.2f) c.fillCircle(X(gu, gz + 7), Y(gy, gz + 7), 12 * s, alpha(0x30FF5FB0, dark));
        lineZ(c, gu - 6, gy, gz + 13, gu + 6, gy, gz + 13, 1.2f, pink);
        lineZ(c, gu - 6, gy, gz + 13, gu, gy, gz + 6, 1.2f, pink);
        lineZ(c, gu + 6, gy, gz + 13, gu, gy, gz + 6, 1.2f, pink);
        lineZ(c, gu, gy, gz + 6, gu, gy, gz, 1.2f, pink);
        lineZ(c, gu - 3.5f, gy, gz, gu + 3.5f, gy, gz, 1.2f, pink);
        boolean olive = neon && ((int) (t * 1.5f)) % 2 == 0;
        c.fillCircle(X(gu + 2, gz + 11), Y(gy, gz + 11), 1.5f * s, olive ? cyan : lit(0xFF6A7880));
        float ts = Math.max(11f, 12f * s);
        float cx = X(242, hz + 1), cy = Y(fy1, hz + 1);
        float w = c.textWidth(b.name, ts) + 18, hh = ts * 1.55f;
        if (neon && dark > 0.2f) c.fillRoundRect(cx - w / 2 - 3, cy - hh / 2 - 3, w + 6, hh + 6, 6, alpha(0x40FF5FB0, dark));
        c.fillRoundRect(cx - w / 2 + 1, cy - hh / 2 + 1.6f, w, hh, 3.5f, 0x66000000);
        c.fillRoundRect(cx - w / 2, cy - hh / 2, w, hh, 3.5f, lit(0xFF15181C));
        c.strokeRoundRect(cx - w / 2 + 1.5f, cy - hh / 2 + 1.5f, w - 3, hh - 3, 2.5f, 1, neon ? cyan : lit(0xFF5A6670));
        c.text(b.name, cx, cy + ts * 0.37f, ts, neon ? 0xFFFF7FC4 : 0xFFB9A8B0, Gfx.CENTER);
    }

    private void restaurant(Gfx c, City.Building b, boolean open) {
        setBox(86, b.y, 76, b.h, 28);
        boolean evening = open && dark > 0.3f;
        // The terrace: hedge at the back, two tables, a big umbrella.
        for (int i = 0; i < 4; i++) {
            float x = 58 + i * 8.5f;
            c.fillCircle(X(x, 3), Y(201, 3), 4.6f * s, lit(LEAF_DK));
            c.fillCircle(X(x - 1, 4.5f), Y(201, 4.5f), 2.8f * s, lit(LEAF));
        }
        body(c, 0xFFDE8A6A, 0xFFA95F48);
        plinth(c, 0xFF8E4A38, 3);
        fq(c, fx0, fx1, hz - 4.5f, hz - 1.5f, fy1, lit(0xFFF1DFC0));
        float[] cols = {99, 129, 147};
        for (int i = 0; i < cols.length; i++) {
            float u = cols[i];
            window(c, u - 5, u + 5, 5, 18, open && dark > 0.15f, WARM2);
            c.fillCircle(X(u, 18), Y(fy1, 18), 5 * s, open && dark > 0.15f ? mix(lit(GLASS), WARM2, Math.min(1, dark * 1.8f)) : lit(GLASS));
            fq(c, u - 6, u + 6, 3, 5, fy1 + 2, lit(0xFF4E7A3A));
            c.fillCircle(X(u - 3, 5.5f), Y(fy1 + 2, 5.5f), 1.4f * s, lit(0xFFE04A4A));
            c.fillCircle(X(u + 2, 5.8f), Y(fy1 + 2, 5.8f), 1.4f * s, lit(0xFFF2C14E));
            if (!open) shutter(c, u - 5, u + 5, 5, 18);
        }
        door(c, b.doorX, 10, 14, 0xFF2F5A45, open);
        if (!open) {
            shutter(c, b.doorX - 5, b.doorX + 5, 0, 14);
            closedTag(c, 138, 9);
        }
        eaveShade(c);
        roofFlat(c, 0xFFEADBC0, 0xFFCBB898);
        for (int i = 0; i < 3; i++) {
            float u = 104 + i * 20;
            c.fillCircle(X(u, hz + 3), Y(fy0 + 5, hz + 3), 3.2f * s, lit(LEAF));
        }
        // Tables and the umbrella sit in front of the hedge.
        table(c, 62, 232);
        table(c, 79, 218);
        float uz = 21, ux = 68, uy = 225;
        lineZ(c, ux, uy, 0, ux, uy, uz, 1.1f, lit(0xFF6B5A4A));
        float ucx = X(ux, uz), ucy = Y(uy, uz), ur = 13 * s;
        c.fillCircle(ucx, ucy, ur, lit(0xFFF4E8D2));
        for (int k = 0; k < 4; k++) {
            double a0 = k * Math.PI / 2 + 0.2, a1 = a0 + Math.PI / 4;
            tri(c, ucx, ucy, ucx + (float) Math.cos(a0) * ur, ucy + (float) Math.sin(a0) * ur,
                    ucx + (float) Math.cos(a1) * ur, ucy + (float) Math.sin(a1) * ur, lit(0xFFD0483E));
        }
        c.fillCircle(ucx, ucy, 1.4f * s, lit(0xFF6B5A4A));
        // String lights from the wall to the umbrella.
        float sx0 = X(fx0, 24), sy0 = Y(fy1, 24), sx1 = ucx + ur * 0.7f, sy1 = ucy - ur * 0.3f;
        float px = sx0, py = sy0;
        for (int k = 1; k <= 6; k++) {
            float f = k / 6f, x = sx0 + (sx1 - sx0) * f, y = sy0 + (sy1 - sy0) * f + (float) Math.sin(f * Math.PI) * 4 * s;
            c.line(px, py, x, y, 0.5f * s, lit(0xFF4A4038));
            if (k < 6) {
                if (evening) c.fillCircle(x, y, 2.6f * s, alpha(0x66FFD27A, dark));
                c.fillCircle(x, y, 1 * s, evening ? 0xFFFFE6A8 : lit(0xFFE8E0D0));
            }
            px = x;
            py = y;
        }
        sign(c, b.name, 127, hz + 1, fy1, 0xFF7A2A20, 0xFFFFEEDA, open);
    }

    private void table(Gfx c, float x, float y) {
        int chair = lit(0xFF8A5A3A);
        c.fillRect(X(x - 6, 3), Y(y, 3), 2.5f * s, 3 * s, chair);
        c.fillRect(X(x + 4, 3), Y(y, 3), 2.5f * s, 3 * s, chair);
        lineZ(c, x, y, 0, x, y, 5, 0.9f, lit(0xFF5A4A3A));
        c.fillCircle(X(x, 5.5f), Y(y, 5.5f), 3.6f * s, lit(0xFFF4F0E6));
    }

    private void market(Gfx c, City.Building b, boolean open) {
        setBox(b.x, b.y, b.w, b.h, 26);
        body(c, 0xFFE8EEE8, 0xFFB0BCB2);
        plinth(c, 0xFF8E9A92, 2);
        fq(c, fx0, fx1, hz - 4.5f, hz - 1.5f, fy1, lit(0xFF4E9A44));
        boolean lights = open && dark > 0.15f;
        int glass = lights ? mix(lit(GLASS), 0xFFEAFFF6, Math.min(1, dark * 1.8f)) : lit(GLASS);
        fq(c, 200, 284, 2, 15, fy1, glass);
        if (open) {
            // Shelves and goods behind the glass.
            for (int row = 0; row < 2; row++) {
                fq(c, 201, 283, 5 + row * 5, 5.6f + row * 5, fy1, lit(0xFF9AA49E));
                for (int i = 0; i < 7; i++) {
                    float u = 206 + i * 11.5f;
                    if (Math.abs(u - b.doorX) < 8) continue;
                    int gc = CLOTHES[(i * 3 + row) % CLOTHES.length];
                    fq(c, u - 3, u + 3, 5.6f + row * 5, 8.4f + row * 5, fy1, lights ? mix(lit(gc), gc, 0.6f) : lit(gc));
                }
            }
            for (int i = 1; i < 4; i++) {
                float u = 200 + i * 21;
                c.line(X(u, 2), Y(fy1, 2), X(u, 15), Y(fy1, 15), 0.7f * s, lit(0xFF6E7A72));
            }
        }
        door(c, b.doorX, 12, 14, 0xFFBCD8E0, open);
        if (!open) {
            shutter(c, 200, 284, 0, 15.5f);
        }
        // Green and white awning.
        for (int i = 0; i < 12; i++) {
            float u0 = 196 + i * 7.6f, u1 = u0 + 7.6f;
            q4[0] = X(u0, 20);
            q4[1] = Y(fy1, 20);
            q4[2] = X(u1, 20);
            q4[3] = q4[1];
            q4[4] = X(u1, 16);
            q4[5] = Y(fy1 + 6, 16);
            q4[6] = X(u0, 16);
            q4[7] = q4[5];
            c.fillPoly(q4, lit(i % 2 == 0 ? 0xFF3F9A48 : 0xFFF2F4EE));
        }
        fq(c, 196, 287.2f, 14.6f, 16, fy1 + 6, lit(0xFF2E7A36));
        if (!open) closedTag(c, 262, 5);
        eaveShade(c);
        roofFlat(c, 0xFFC7CEC9, 0xFFAAB2AD);
        box(c, 210, 220, fy0 + 3, fy0 + 9, hz, hz + 4, lit(0xFFD4DAD6), lit(0xFFB9C0BB), lit(0xFF959C97));
        box(c, 266, 276, fy0 + 3, fy0 + 9, hz, hz + 4, lit(0xFFD4DAD6), lit(0xFFB9C0BB), lit(0xFF959C97));
        // Trolleys parked by the wall.
        for (int i = 0; i < 2; i++) {
            float u = 277 + i * 3.2f;
            fq(c, u - 3, u + 3, 2, 6, fy1 + 3.5f, alpha(lit(0xFF8A96A0), 0.85f));
            lineZ(c, u - 3, fy1 + 3.5f, 6, u - 4.5f, fy1 + 3.5f, 7, 0.7f, lit(0xFF5A646C));
        }
        sign(c, b.name, 240, hz + 1, fy1, 0xFF2E6E2A, 0xFFF4FFF0, open);
    }

    private void gas(Gfx c, City.Building b) {
        int post = lit(0xFFC9CED4), postSide = lit(0xFF8E959D);
        float cx0 = 197, cx1 = 246, cy0 = 486, cy1 = 508, cz = 25;
        // Back posts, the pump island, the pumps, then the front posts.
        lineZ(c, 200, cy0 + 2, 0, 200, cy0 + 2, cz, 2.2f, postSide);
        lineZ(c, 243, cy0 + 2, 0, 243, cy0 + 2, cz, 2.2f, postSide);
        for (int i = 0; i < 2; i++) {
            float u = 212 + i * 19;
            box(c, u - 4, u + 4, 494, 499, 0, 12, lit(0xFFE4E6EA), lit(0xFFD8483E), lit(0xFF9E3029));
            fq(c, u - 2.6f, u + 2.6f, 7, 10.5f, 499, dark > 0.3f ? 0xFFCFF5FF : lit(0xFFE8F4F8));
            fq(c, u - 2.6f, u + 2.6f, 2, 3, 499, lit(0xFF7A2420));
            lineZ(c, u + 4, 497, 8, u + 6.5f, 500, 1, 0.8f, lit(0xFF23272C));
        }
        lineZ(c, 200, cy1 - 2, 0, 200, cy1 - 2, cz, 2.2f, post);
        lineZ(c, 243, cy1 - 2, 0, 243, cy1 - 2, cz, 2.2f, post);
        // Kiosk.
        setBox(250, 470, 38, 45, 24);
        body(c, 0xFFE6E3DA, 0xFFA9A69D);
        plinth(c, 0xFF8A877F, 2);
        fq(c, fx0, fx1, hz - 4, hz - 1.5f, fy1, lit(0xFFEBC043));
        window(c, 277, 285, 5, 15, dark > 0.15f, 0xFFEAFFF6);
        door(c, b.doorX, 9, 13, 0xFFBCD8E0, true);
        eaveShade(c);
        roofFlat(c, 0xFFD4D1C8, 0xFFB9B6AD);
        // The canopy: a thin slab whose fascia lights up at night.
        eq(c, cx1, cy0, cy1, cz - 3.5f, cz, lit(0xFFB08F2A));
        hq(c, cx0, cx1, cy0, cy1, cz, lit(0xFFEDEBE5));
        hq(c, cx0 + 2.5f, cx1 - 2.5f, cy0 + 2.5f, cy1 - 2.5f, cz, lit(0xFFD9D6CE));
        int fascia = dark > 0.3f ? mix(lit(0xFFEBC043), 0xFFFFD64A, 0.7f) : lit(0xFFEBC043);
        fq(c, cx0, cx1, cz - 3.5f, cz, cy1, fascia);
        fq(c, cx0, cx1, cz - 2.2f, cz - 1.5f, cy1, lit(0xFFD0483E));
        if (dark > 0.3f) fq(c, cx0 + 1, cx1 - 1, cz - 4.2f, cz - 3.5f, cy1, 0xFFF4FBFF);
        sign(c, b.name, 221, cz + 7, cy1 - 3, 0xFFC0392B, 0xFFFFF4E0, true);
    }

    private void home(Gfx c, City.Building b) {
        setBox(b.x, b.y, b.w, b.h, 64);
        body(c, 0xFFB1B5B6, 0xFF7C8186);
        plinth(c, 0xFF73787C, 3);
        // Rust and rain streaks: the building has seen better decades.
        int streak = alpha(0xFF5A5040, 0.22f);
        fq(c, 89, 91.5f, 18, 42, fy1, streak);
        fq(c, 126, 128, 34, 58, fy1, streak);
        fq(c, 140, 143, 6, 30, fy1, streak);
        float[] cols = {85, 97.5f, 110, 122.5f, 135};
        int wi = 0;
        for (int f = 0; f < 4; f++) {
            float z0 = 4 + f * 15;
            for (int k = 0; k < cols.length; k++) {
                float u = cols[k];
                if (f == 0 && (k == 2 || k == 1)) {
                    wi++;
                    continue;
                }
                boolean on = lightOn(City.HOME, wi, true);
                window(c, u - 4, u + 4, z0, z0 + 8, on, warmFor(City.HOME, wi));
                if (f == 2 && k == 3) {
                    // One boarded-up window.
                    fq(c, u - 4, u + 4, z0, z0 + 8, fy1, lit(0xFF4A4E54));
                    c.line(X(u - 4.5f, z0 + 2), Y(fy1, z0 + 2), X(u + 4.5f, z0 + 6), Y(fy1, z0 + 6), 1.4f * s, lit(0xFF9A7652));
                    c.line(X(u - 4.5f, z0 + 6), Y(fy1, z0 + 6), X(u + 4.5f, z0 + 3), Y(fy1, z0 + 3), 1.4f * s, lit(0xFF8A6844));
                }
                if (f > 0 && (k == 0 || k == 4)) {
                    // Balcony: slab and a railing.
                    hq(c, u - 6, u + 6, fy1, fy1 + 4, z0 - 1, lit(0xFFC4C8CA));
                    fq(c, u - 6, u + 6, z0 - 1, z0 + 3.5f, fy1 + 4, lit(0xFF8D9399));
                }
                if (hash(wi * 7 + 1) < 0.3f && f > 0 && k != 0 && k != 4) {
                    // An air conditioner hanging off the wall.
                    box(c, u + 4.5f, u + 9, fy1, fy1 + 2.5f, z0 + 1, z0 + 5, lit(0xFFE2E4E6), lit(0xFFC9CCCF), lit(0xFF9EA2A6));
                }
                wi++;
            }
        }
        door(c, b.doorX, 10, 12, 0xFF3E5C66, true);
        box(c, b.doorX - 8, b.doorX + 8, fy1, fy1 + 5, 13, 14.6f, lit(0xFFC4C8CA), lit(0xFF8D9399), lit(0xFF73787C));
        eaveShade(c);
        roofFlat(c, 0xFFA3A8AC, 0xFF8A8F94);
        // Rooftop: solar water heaters, an antenna and a washing line that moves in the breeze.
        for (int i = 0; i < 2; i++) {
            float u = 90 + i * 16;
            q4[0] = X(u - 5, hz + 1);
            q4[1] = Y(fy0 + 14, hz + 1);
            q4[2] = X(u + 5, hz + 1);
            q4[3] = q4[1];
            q4[4] = X(u + 5, hz + 6);
            q4[5] = Y(fy0 + 9, hz + 6);
            q4[6] = X(u - 5, hz + 6);
            q4[7] = q4[5];
            c.fillPoly(q4, lit(0xFF34507A));
            c.fillRoundRect(X(u - 5, hz + 8), Y(fy0 + 5, hz + 8), 10 * s, 3.6f * s, 1.8f * s, lit(0xFFE8EAEC));
        }
        lineZ(c, 140, fy0 + 6, hz, 140, fy0 + 6, hz + 16, 0.7f, lit(0xFF4A5058));
        lineZ(c, 136, fy0 + 6, hz + 13, 144, fy0 + 6, hz + 13, 0.7f, lit(0xFF4A5058));
        lineZ(c, 137, fy0 + 6, hz + 10, 143, fy0 + 6, hz + 10, 0.7f, lit(0xFF4A5058));
        float ly = fy0 + 20, lz = hz + 8;
        lineZ(c, 116, ly, hz, 116, ly, lz, 0.8f, lit(0xFF6A6058));
        lineZ(c, 146, ly, hz, 146, ly, lz, 0.8f, lit(0xFF6A6058));
        lineZ(c, 116, ly, lz, 146, ly, lz, 0.5f, lit(0xFF4A4038));
        for (int i = 0; i < 4; i++) {
            float u = 120 + i * 6.5f, sw = (float) Math.sin(t * 2.4f + i) * 0.9f;
            q4[0] = X(u, lz);
            q4[1] = Y(ly, lz);
            q4[2] = X(u + 4, lz);
            q4[3] = q4[1];
            q4[4] = X(u + 4 + sw, lz - 5);
            q4[5] = Y(ly, lz - 5);
            q4[6] = X(u + sw, lz - 5);
            q4[7] = q4[5];
            c.fillPoly(q4, lit(CLOTHES[(i * 3 + 1) % CLOTHES.length]));
        }
        sign(c, b.name, b.doorX, hz + 2.5f, fy1, 0xFF3D4754, 0xFFF2F4F7, true);
    }

    private void alley(Gfx c, City.Building b, boolean open) {
        // Two low brick blocks with a dark slot between them, open to the sky.
        float flick = (t * 9) % 7f;
        boolean lampOn = dark > 0.25f && !(flick > 5.2f && flick < 5.6f || hash((int) (t * 11)) < 0.12f);
        float h = 30, back = 473;
        int iron = lit(0xFF2A2E33), brick = lit(0xFF8A5A48), brickDk = lit(0xFF6E4638), tar = lit(0xFF6A6460);
        // Back fence and bin bags at the far end.
        fq(c, 325, 347, 0, 12, back, lit(0xFF4E4038));
        for (int i = 0; i < 4; i++) {
            float u = 328 + i * 4.5f;
            c.line(X(u, 0), Y(back, 0), X(u, 12), Y(back, 12), 0.6f * s, lit(0xFF3A3028));
        }
        c.fillCircle(X(331, 2.5f), Y(back + 4, 2.5f), 3 * s, lit(0xFF23262B));
        c.fillCircle(X(335.5f, 2.2f), Y(back + 6, 2.2f), 2.6f * s, lit(0xFF30343A));
        // Left block: its east wall is the alley's side, warmed by the lamp.
        setBox(316, b.y, 10, b.h, h);
        int sideCol = lampOn ? mix(lit(0xFF5C4038), 0xFFB08060, 0.35f * dark) : lit(0xFF4E3832);
        eq(c, fx1, fy0, fy1, 0, hz, sideCol);
        fq(c, fx0, fx1, 0, hz, fy1, brick);
        hq(c, fx0 - 0.6f, fx1 + 0.3f, fy0, fy1 + 0.6f, hz, tar);
        float lx = fx1;
        // Puddle, then the dumpster at the mouth with the cat on its lid.
        if (lampOn) ellipse(c, lx + 8, 506, 0, 11, 5, alpha(0x40FFE09A, dark));
        ellipse(c, 337, 492, 0, 5, 1.8f, lampOn ? 0xFF7A8AA0 : lit(0xFF262B33));
        box(c, 328, 340, 504, 511, 0, 8, lit(0xFF2F5A3E), lit(0xFF3F6E4E), lit(0xFF2A4A36));
        fq(c, 327.5f, 340.5f, 8, 9.4f, 511.5f, lit(0xFF24402E));
        cat(c, 333.5f, 507, 9.4f);
        // Right block, running off the edge of town, with a fire escape.
        setBox(339, b.y, 27, b.h, h);
        eq(c, fx1, fy0, fy1, 0, hz, brickDk);
        fq(c, fx0, fx1, 0, hz, fy1, brick);
        for (int i = 0; i < 4; i++) {
            float z = 4 + i * 6.5f;
            c.line(X(fx0 + 1 + (i % 2) * 4, z), Y(fy1, z), X(fx0 + 9 + (i % 2) * 4, z), Y(fy1, z), 0.6f * s, brickDk);
        }
        window(c, 354, 360, 17, 24, lightOn(City.ALLEY, 1, true), WARM2);
        fq(c, fx0 + 1, fx0 + 10, 15, 15.9f, fy1 + 2, iron);
        lineZ(c, fx0 + 2, fy1 + 2, 15, fx0 + 9, fy1 + 2, 27, 0.6f, iron);
        lineZ(c, fx0 + 2, fy1 + 2, 1, fx0 + 9, fy1 + 2, 15, 0.6f, iron);
        // Scribbles near the ground.
        c.line(X(349, 4), Y(fy1, 4), X(352, 9), Y(fy1, 9), 1.1f * s, lit(0xFF4FB0B0));
        c.line(X(352, 9), Y(fy1, 9), X(355, 4), Y(fy1, 4), 1.1f * s, lit(0xFF4FB0B0));
        c.line(X(355, 4), Y(fy1, 4), X(359, 8), Y(fy1, 8), 1.1f * s, lit(0xFFE08AB0));
        eaveShade(c);
        hq(c, fx0 - 0.6f, fx1, fy0, fy1 + 0.6f, hz, tar);
        box(c, 352, 360, fy0 + 6, fy0 + 14, hz, hz + 6, lit(0xFF8A9096), lit(0xFF70767C), lit(0xFF5A6066));
        // Flickering lamp on the left wall.
        float lz = 19, ly = 498;
        lineZ(c, lx, ly, lz, lx + 4, ly, lz + 1, 0.9f, iron);
        float bx = X(lx + 4, lz), by = Y(ly, lz);
        if (lampOn) {
            q4[0] = bx - 1.5f * s;
            q4[1] = by;
            q4[2] = bx + 1.5f * s;
            q4[3] = by;
            q4[4] = X(lx + 14, 0);
            q4[5] = Y(ly + 10, 0);
            q4[6] = X(lx + 1, 0);
            q4[7] = Y(ly + 10, 0);
            c.fillPoly(q4, alpha(0x30FFE9A0, dark));
            c.fillCircle(bx, by, 5 * s, alpha(0x40FFE08A, dark));
            c.fillCircle(bx, by, 1.5f * s, 0xFFFFF4C0);
        } else {
            c.fillCircle(bx, by, 1.4f * s, lit(0xFF8A8A7A));
        }
        fy1 = b.y + b.h;
        if (!open) {
            // A locked gate across the mouth while the alley sleeps.
            float g0 = 326, g1 = 345.5f;
            fq(c, g0, g1, 0, 18, 514, alpha(lit(0xFF2A2E33), 0.3f));
            for (int i = 0; i <= 6; i++) {
                float u = g0 + i * (g1 - g0) / 6;
                c.line(X(u, 0), Y(514, 0), X(u, 18), Y(514, 18), 0.8f * s, lit(0xFF7A8088));
            }
            c.line(X(g0, 18), Y(514, 18), X(g1, 18), Y(514, 18), 1.3f * s, lit(0xFF7A8088));
            closedTag(c, 335.5f, 5);
        }
        // The name rides above the rooftops on a little frame, leaving the alley itself in view.
        lineZ(c, 333, fy1 - 20, hz, 333, fy1 - 20, hz + 14, 0.8f, iron);
        lineZ(c, 343, fy1 - 20, hz, 343, fy1 - 20, hz + 14, 0.8f, iron);
        sign(c, b.name, 338, hz + 17, fy1 - 20, 0xFF22252A, 0xFFC9CED6, open);
    }

    private void cat(Gfx c, float x, float y, float z) {
        float tail = (float) Math.sin(t * 2.2f) * 1.8f;
        int fur = lit(0xFF1E1C1C);
        c.line(X(x + 2, z + 1.5f), Y(y, z + 1.5f), X(x + 4.5f, z + 4 + tail), Y(y, z + 4 + tail), 0.9f * s, fur);
        c.fillCircle(X(x, z + 2), Y(y, z + 2), 2.4f * s, fur);
        c.fillCircle(X(x - 2.2f, z + 4.6f), Y(y, z + 4.6f), 1.6f * s, fur);
        tri(c, X(x - 3.5f, z + 5.5f), Y(y, z + 5.5f), X(x - 2.4f, z + 5.8f), Y(y, z + 5.8f), X(x - 3.3f, z + 7.2f), Y(y, z + 7.2f), fur);
        tri(c, X(x - 1.8f, z + 5.8f), Y(y, z + 5.8f), X(x - 0.8f, z + 5.4f), Y(y, z + 5.4f), X(x - 1.2f, z + 7.2f), Y(y, z + 7.2f), fur);
        if (dark > 0.3f) {
            int eye = 0xFFE8F27A;
            c.fillCircle(X(x - 2.9f, z + 4.8f), Y(y, z + 4.8f), 0.5f * s, eye);
            c.fillCircle(X(x - 1.7f, z + 4.8f), Y(y, z + 4.8f), 0.5f * s, eye);
        }
    }
}
