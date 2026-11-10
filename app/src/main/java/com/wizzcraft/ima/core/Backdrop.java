package com.wizzcraft.ima.core;

/**
 * The quiet place behind a conversation. One room or street per place id, drawn as a shallow stage:
 * a back wall, a floor that runs towards the viewer in perspective, one light source and two or
 * three things that say where we are. Everything stays in the lower half of the brightness range so
 * portraits, cards and the phone always win; the views put their own scrim on top where needed.
 *
 * The layers drift a little against each other (the far wall least, the foreground most), which is
 * what makes the stage read as a space and not as wallpaper. With reduce-motion the drift is off.
 *
 * Draws from top to bottom of the given band and never allocates.
 */
final class Backdrop {
    private Backdrop() {
    }

    /** Known places; anything else draws the street. */
    static final String[] PLACES = {"home", "street", "stairs", "restaurant", "bakery", "bar", "gas", "market",
            "alley", "school"};

    private static final float W = Present.W;
    private static final float[] P8 = new float[8], P6 = new float[6];

    private static void quad(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int col) {
        P8[0] = x0;
        P8[1] = y0;
        P8[2] = x1;
        P8[3] = y1;
        P8[4] = x2;
        P8[5] = y2;
        P8[6] = x3;
        P8[7] = y3;
        c.fillPoly(P8, col);
    }

    private static void tri(Gfx c, float x0, float y0, float x1, float y1, float x2, float y2, int col) {
        P6[0] = x0;
        P6[1] = y0;
        P6[2] = x1;
        P6[3] = y1;
        P6[4] = x2;
        P6[5] = y2;
        c.fillPoly(P6, col);
    }

    /** Deterministic 0..1 noise from two integers. */
    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    /**
     * Draws place into the band top..bottom. t is seconds (idle life), night 0..1 darkens and warms
     * the lights (0 = day, 1 = deep night).
     */
    static void draw(Gfx c, String place, float top, float bottom, float t, float night) {
        float h = bottom - top;
        float horizon = top + h * 0.56f;
        float sway = Motion.reduce ? 0 : (float) Math.sin(t * 0.35f) * 3f;
        String p = place == null ? "street" : place;
        switch (p) {
            case "home" -> home(c, top, bottom, horizon, sway, t, night);
            case "stairs" -> stairs(c, top, bottom, horizon, sway, t, night);
            case "restaurant" -> kitchen(c, top, bottom, horizon, sway, t);
            case "bakery" -> bakery(c, top, bottom, horizon, sway, t);
            case "bar" -> bar(c, top, bottom, horizon, sway, t);
            case "alley" -> alley(c, top, bottom, horizon, sway, t);
            case "market" -> market(c, top, bottom, horizon, sway, t);
            case "gas" -> gas(c, top, bottom, horizon, sway, t);
            case "school" -> school(c, top, bottom, horizon, sway, t);
            default -> street(c, top, bottom, horizon, sway, t, night);
        }
    }

    // ---- shared pieces ---------------------------------------------------------------------------

    /** A floor from the horizon line to the bottom with boards converging on the vanishing point. */
    private static void floor(Gfx c, float horizon, float bottom, float vx, int near, int far, int seam, int boards) {
        Present.vgrad(c, 0, horizon, W, bottom - horizon, far, near);
        float spread = (bottom - horizon) * 2.6f;
        for (int i = -boards; i <= boards; i++) {
            float bx = vx + i * spread / boards * 2.2f;
            c.line(vx + i * 26f / boards * 4, horizon, bx, bottom, 1.2f, seam);
        }
        // Three cross seams, closer together towards the horizon: cheap but convincing depth.
        for (int i = 1; i <= 3; i++) {
            float k = i / 4f;
            float y = horizon + (bottom - horizon) * k * k;
            c.fillRect(0, y, W, 1, seam);
        }
    }

    /** A pool of light on the floor. */
    private static void pool(Gfx c, float cx, float cy, float rx, float ry, int col) {
        for (int i = 4; i >= 1; i--) {
            float k = i / 4f;
            c.fillRoundRect(cx - rx * k, cy - ry * k, rx * 2 * k, ry * 2 * k, ry * k, Present.alpha(col, 0.07f));
        }
    }

    /** A cone of light from (x, y) down to the floor. */
    private static void cone(Gfx c, float x, float y, float halfTop, float halfBottom, float floorY, int col) {
        quad(c, x - halfTop, y, x + halfTop, y, x + halfBottom, floorY, x - halfBottom, floorY, Present.alpha(col, 0.05f));
        quad(c, x - halfTop * 0.6f, y, x + halfTop * 0.6f, y, x + halfBottom * 0.55f, floorY, x - halfBottom * 0.55f, floorY,
                Present.alpha(col, 0.05f));
    }

    /** A night skyline of lit blocks between x0 and x1, standing on baseY. */
    private static void skyline(Gfx c, float x0, float x1, float baseY, float maxH, int body, int light, float t, int seed) {
        float x = x0;
        int i = 0;
        while (x < x1) {
            float bw = 16 + hash(i, seed) * 22;
            float bh = maxH * (0.35f + 0.65f * hash(i, seed + 7));
            if (x + bw > x1) bw = x1 - x;
            c.fillRect(x, baseY - bh, bw + 0.5f, bh, body);
            int cols = (int) (bw / 7), rows = (int) (bh / 9);
            for (int r = 0; r < rows; r++) {
                for (int q = 0; q < cols; q++) {
                    float on = hash(i * 31 + q, r * 17 + seed);
                    if (on > 0.62f) {
                        // A few windows breathe slowly, far below 1 Hz: somebody is home.
                        float a = on > 0.93f ? 0.55f + 0.35f * Motion.pulse(t + on * 9, 0.18f) : 0.8f;
                        c.fillRect(x + 3 + q * 7, baseY - bh + 4 + r * 9, 3, 4, Present.alpha(light, a));
                    }
                }
            }
            x += bw + 2;
            i++;
        }
    }

    // ---- places ------------------------------------------------------------------------------------

    /** The rented room: bare wall, a window on the city, one bulb, a mattress on the floor. */
    private static void home(Gfx c, float top, float bottom, float horizon, float sway, float t, float night) {
        int wall = 0xFF27303C, wallLow = 0xFF1F2731;
        Present.vgrad(c, 0, top, W, horizon - top, wall, wallLow);
        // Window with the night city in it.
        float wx = 36 + sway * 0.4f, wy = top + (horizon - top) * 0.22f, ww = 120, wh = (horizon - top) * 0.50f;
        c.fillRect(wx - 5, wy - 5, ww + 10, wh + 10, 0xFF3A4554);
        Present.vgrad(c, wx, wy, ww, wh, Present.mix(0xFF2B4468, 0xFF101826, night), Present.mix(0xFF6A7FA0, 0xFF1B2740, night));
        skyline(c, wx, wx + ww, wy + wh, wh * 0.62f, 0xFF0E141E, 0xFFFFD98A, t, 3);
        c.fillRect(wx + ww / 2 - 1.5f, wy, 3, wh, 0xFF3A4554);
        c.fillRect(wx, wy + wh * 0.5f - 1.5f, ww, 3, 0xFF3A4554);
        c.fillRect(wx - 9, wy + wh + 5, ww + 18, 5, 0xFF465264);
        // Skirting board and floor.
        c.fillRect(0, horizon - 6, W, 6, 0xFF1A2029);
        floor(c, horizon, bottom, W * 0.62f, 0xFF2B2622, 0xFF1D1B1A, 0x22000000, 5);
        // The bulb on its wire, and its light.
        float bx = 250 - sway * 0.6f, by = top + (horizon - top) * 0.30f;
        c.line(bx, top, bx, by - 8, 1.2f, 0xFF10141A);
        cone(c, bx, by, 10, 150, bottom, 0xFFFFD98A);
        Present.glow(c, bx, by, 46, 0xFFFFD98A, 1f);
        c.fillRect(bx - 3, by - 10, 6, 5, 0xFF10141A);
        c.fillCircle(bx, by, 6.5f, 0xFFFFE9B8);
        pool(c, bx, horizon + (bottom - horizon) * 0.55f, 150, 34, 0xFFFFD98A);
        // Mattress against the wall, a box as a bedside table.
        float my = horizon + 6;
        quad(c, 150 - sway, my - 14, 345 - sway, my - 14, 360, my + 26, 140 - sway, my + 26, 0xFF4B5670);
        quad(c, 150 - sway, my - 22, 345 - sway, my - 22, 345 - sway, my - 14, 150 - sway, my - 14, 0xFF5D6A88);
        c.fillRoundRect(290 - sway, my - 32, 44, 16, 6, 0xFFB9C2D4);
        quad(c, 64 - sway * 1.3f, my - 4, 112 - sway * 1.3f, my - 4, 116 - sway * 1.3f, my + 34, 60 - sway * 1.3f, my + 34, 0xFF7A5C3E);
        c.fillRect(64 - sway * 1.3f, my - 4, 48, 5, 0xFF8E6D4A);
    }

    /** The street at night: far blocks, a pavement, one lamp. */
    private static void street(Gfx c, float top, float bottom, float horizon, float sway, float t, float night) {
        Present.vgrad(c, 0, top, W, horizon - top, Present.mix(0xFF35507A, 0xFF0D1422, night), Present.mix(0xFF8A7FA0, 0xFF222C48, night));
        skyline(c, -10 + sway * 0.3f, W + 10, horizon, (horizon - top) * 0.55f, 0xFF161D2A, 0xFFFFD98A, t, 11);
        skyline(c, -20 + sway * 0.8f, W + 20, horizon, (horizon - top) * 0.30f, 0xFF10161F, 0xFFFFC46B, t, 23);
        floor(c, horizon, bottom, W * 0.5f, 0xFF232830, 0xFF171B22, 0x26000000, 4);
        c.fillRect(0, horizon, W, 4, 0xFF2E3540);
        float lx = 300 - sway * 1.4f;
        c.fillRect(lx - 2, top + (horizon - top) * 0.25f, 4, bottom - top, 0xFF0C1016);
        c.fillRect(lx - 26, top + (horizon - top) * 0.25f, 28, 4, 0xFF0C1016);
        float gx = lx - 24, gy = top + (horizon - top) * 0.25f + 8;
        cone(c, gx, gy, 6, 110, bottom, 0xFFFFD98A);
        Present.glow(c, gx, gy, 40, 0xFFFFD98A, 1);
        c.fillCircle(gx, gy, 5, 0xFFFFE9B8);
        pool(c, gx, horizon + (bottom - horizon) * 0.5f, 120, 28, 0xFFFFD98A);
    }

    /** The stairwell of the building: landing, a door with a peephole, the rail going down. */
    private static void stairs(Gfx c, float top, float bottom, float horizon, float sway, float t, float night) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF2C3238, 0xFF22272D);
        c.fillRect(0, top + (horizon - top) * 0.62f, W, (horizon - top) * 0.38f, 0xFF27333A);
        c.fillRect(0, top + (horizon - top) * 0.62f, W, 2, 0xFF36454E);
        // The neighbour's door.
        float dx = 40 + sway * 0.5f, dy = top + (horizon - top) * 0.16f, dw = 96, dh = horizon - dy;
        c.fillRect(dx - 6, dy - 6, dw + 12, dh + 6, 0xFF1A1F25);
        c.fillRect(dx, dy, dw, dh, 0xFF5A4634);
        c.fillRect(dx + 8, dy + 10, dw - 16, dh * 0.38f, 0xFF513E2E);
        c.fillRect(dx + 8, dy + dh * 0.52f, dw - 16, dh * 0.40f, 0xFF513E2E);
        c.fillCircle(dx + dw / 2, dy + dh * 0.30f, 3.5f, 0xFF1A1410);
        c.fillCircle(dx + dw / 2, dy + dh * 0.30f, 1.6f, 0xFFFFD98A);
        c.fillCircle(dx + dw - 12, dy + dh * 0.55f, 4, 0xFFC9A45A);
        c.fillRoundRect(dx + 14, dy + dh - 4, dw - 28, 8, 2, 0xFF7C6A4F);
        floor(c, horizon, bottom, W * 0.4f, 0xFF30343A, 0xFF1E2226, 0x30000000, 4);
        // The rail and the stairs going down on the right.
        float rx = 230 - sway;
        for (int i = 0; i < 6; i++) {
            float sx = rx + i * 24, sy = horizon - 70 + i * 22;
            c.fillRect(sx, sy, 3, bottom - sy, 0xFF11151A);
        }
        quad(c, rx - 4, horizon - 78, rx + 150, horizon + 60, rx + 150, horizon + 68, rx - 4, horizon - 70, 0xFF8A5A3A);
        float bx = 180 - sway * 0.6f, by = top + 26;
        cone(c, bx, by, 14, 150, bottom, 0xFFE8F0FF);
        Present.glow(c, bx, by, 42, 0xFFE8F0FF, 0.8f);
        c.fillRoundRect(bx - 16, by - 5, 32, 8, 4, 0xFFE8F0FF);
    }

    /** The restaurant kitchen: the pass with its heat lamps, tickets on the rail, pots behind. */
    private static void kitchen(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF2A3038, 0xFF20262E);
        // Tiles.
        for (int i = 0; i < 9; i++) c.fillRect(i * 44 + sway * 0.3f - 10, top, 1, horizon - top, 0x14FFFFFF);
        for (int j = 1; j < 6; j++) c.fillRect(0, top + (horizon - top) * j / 6f, W, 1, 0x14FFFFFF);
        // Shelf with pots.
        float sy = top + (horizon - top) * 0.34f;
        c.fillRect(0, sy, W, 5, 0xFF596574);
        for (int i = 0; i < 5; i++) {
            float px = 22 + i * 70 + sway * 0.5f, pw = 34 + hash(i, 5) * 14, ph = 20 + hash(i, 9) * 12;
            c.fillRoundRect(px, sy - ph, pw, ph, 3, 0xFF7C8898);
            c.fillRect(px - 4, sy - ph + 4, pw + 8, 3, 0xFF8E9AAB);
        }
        // Ticket rail.
        float ry = top + (horizon - top) * 0.50f;
        c.fillRect(0, ry, W, 3, 0xFFB8C2D0);
        for (int i = 0; i < 6; i++) {
            float tx = 30 + i * 54 - sway * 0.8f;
            float th = 22 + hash(i, 2) * 16, wig = Motion.reduce ? 0 : (float) Math.sin(t * 0.9f + i) * 1.2f;
            quad(c, tx, ry + 2, tx + 22, ry + 2, tx + 22 + wig, ry + 2 + th, tx + wig, ry + 2 + th, 0xFFE9E2D0);
            c.fillRect(tx + 4 + wig * 0.5f, ry + 9, 13, 1.5f, 0x55000000);
            c.fillRect(tx + 4 + wig * 0.7f, ry + 14, 9, 1.5f, 0x55000000);
        }
        // The pass: a steel counter in perspective under two heat lamps.
        quad(c, 0, horizon - 8, W, horizon - 8, W, horizon + 26, 0, horizon + 26, 0xFF8A96A6);
        c.fillRect(0, horizon - 8, W, 3, 0xFFB4BECC);
        Present.vgrad(c, 0, horizon + 26, W, bottom - horizon - 26, 0xFF3A4350, 0xFF1D222A);
        for (int i = 0; i < 2; i++) {
            float lx = 110 + i * 150 - sway, ly = top + (horizon - top) * 0.66f;
            c.line(lx, top, lx, ly - 8, 2, 0xFF11151A);
            quad(c, lx - 7, ly - 8, lx + 7, ly - 8, lx + 20, ly + 6, lx - 20, ly + 6, 0xFF3F4854);
            cone(c, lx, ly + 6, 18, 60, horizon + 20, 0xFFFFB066);
            pool(c, lx, horizon + 6, 62, 12, 0xFFFFB066);
        }
    }

    /** The bakery before dawn: the oven mouth glowing, racks of loaves. */
    private static void bakery(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF33291F, 0xFF261E18);
        float ox = 60 + sway * 0.4f, oy = top + (horizon - top) * 0.30f, ow = 130, oh = (horizon - top) * 0.52f;
        c.fillRoundRect(ox - 12, oy - 14, ow + 24, oh + 30, 10, 0xFF4A3A2C);
        float fl = 0.75f + 0.25f * Motion.pulse(t, 0.6f);
        c.fillRoundRect(ox, oy, ow, oh, oh * 0.5f, 0xFF180F0A);
        Present.glow(c, ox + ow / 2, oy + oh * 0.62f, ow * 0.46f, 0xFFFF9A3C, 1.6f * fl);
        c.fillRoundRect(ox + 14, oy + oh * 0.68f, ow - 28, oh * 0.2f, 6, Present.alpha(0xFFFFC46B, 0.5f * fl));
        // Rack with loaves on the right.
        float rx = 232 - sway * 0.7f;
        c.fillRect(rx, top + 20, 4, horizon - top - 20, 0xFF6F7B8A);
        c.fillRect(rx + 96, top + 20, 4, horizon - top - 20, 0xFF6F7B8A);
        for (int j = 0; j < 4; j++) {
            float y = top + 40 + j * (horizon - top - 50) / 4f;
            c.fillRect(rx, y + 16, 100, 3, 0xFF6F7B8A);
            for (int i = 0; i < 3; i++) c.fillRoundRect(rx + 8 + i * 30, y, 26, 16, 8, hash(i, j) > 0.3f ? 0xFFC98B4F : 0xFFB57A40);
        }
        floor(c, horizon, bottom, W * 0.45f, 0xFF3A2E24, 0xFF211A15, 0x2A000000, 4);
        pool(c, ox + ow / 2, horizon + (bottom - horizon) * 0.4f, 130, 30, 0xFFFF9A3C);
    }

    /** The bar: back shelf of bottles lit from below, the counter. */
    private static void bar(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF2A1F3A, 0xFF1C1528);
        for (int j = 0; j < 2; j++) {
            float sy = top + (horizon - top) * (0.34f + j * 0.26f);
            c.fillRect(20, sy, W - 40, 4, 0xFF4A3A60);
            Present.vgrad(c, 20, sy - 26, W - 40, 26, 0x00C58BE8, 0x30C58BE8);
            for (int i = 0; i < 11; i++) {
                float bx = 30 + i * 28 + sway * (0.3f + j * 0.2f), bh = 20 + hash(i, j + 3) * 16;
                int col = switch ((int) (hash(i, j) * 4)) {
                    case 0 -> 0xFF5BA98F;
                    case 1 -> 0xFFC9864A;
                    case 2 -> 0xFF8E6BC9;
                    default -> 0xFFB5475A;
                };
                c.fillRoundRect(bx, sy - bh, 10, bh, 2, col);
                c.fillRect(bx + 3, sy - bh - 7, 4, 8, col);
                c.fillRect(bx + 1.5f, sy - bh + 3, 2, bh - 8, 0x33FFFFFF);
            }
        }
        quad(c, 0, horizon - 6, W, horizon - 6, W, horizon + 34, 0, horizon + 34, 0xFF5A3F2E);
        c.fillRect(0, horizon - 6, W, 4, 0xFF7A5740);
        Present.vgrad(c, 0, horizon + 34, W, bottom - horizon - 34, 0xFF2B2030, 0xFF15101C);
        float nx = 250 - sway, ny = top + 30;
        float fl = 0.8f + 0.2f * Motion.pulse(t, 0.4f);
        Present.glow(c, nx, ny + 10, 60, 0xFFE86BC0, fl);
        c.strokeRoundRect(nx - 34, ny - 4, 68, 28, 14, 3, Present.alpha(0xFFFF9ADB, fl));
        pool(c, 180, horizon + 14, 150, 14, 0xFFC58BE8);
    }

    /** The alley: two walls closing in, one lamp far away, bins. */
    private static void alley(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, bottom - top, 0xFF0E1420, 0xFF141A22);
        float vx = W * 0.52f, vy = horizon - 30;
        // Far end: a slot of sky and a lamp.
        c.fillRect(vx - 34, top, 68, vy - top + 20, 0xFF1C2740);
        skyline(c, vx - 34, vx + 34, vy + 20, 40, 0xFF0B1018, 0xFFFFD98A, t, 41);
        // Walls.
        quad(c, 0, top, vx - 34, top, vx - 34, vy + 20, 0, bottom, 0xFF231C1C);
        quad(c, W, top, vx + 34, top, vx + 34, vy + 20, W, bottom, 0xFF1B1617);
        for (int i = 1; i < 7; i++) {
            float k = i / 7f, y0 = top + (vy + 20 - top) * k;
            c.line(0, top + (bottom - top) * k * 1.0f, vx - 34, y0, 1, 0x18000000);
            c.line(W, top + (bottom - top) * k * 1.0f, vx + 34, y0, 1, 0x18000000);
        }
        // Ground.
        quad(c, 0, bottom, vx - 34, vy + 20, vx + 34, vy + 20, W, bottom, 0xFF191C22);
        float lx = vx + 20 - sway * 0.4f, ly = vy - 40;
        cone(c, lx, ly, 5, 70, bottom, 0xFFB8D0FF);
        Present.glow(c, lx, ly, 34, 0xFFB8D0FF, 0.9f);
        c.fillCircle(lx, ly, 4, 0xFFE8F0FF);
        // Bins in the foreground.
        float bx = 26 - sway * 1.5f;
        c.fillRoundRect(bx, bottom - 110, 70, 110, 6, 0xFF24382E);
        c.fillRect(bx - 4, bottom - 116, 78, 10, 0xFF2E4A3C);
        pool(c, vx, vy + 60, 90, 22, 0xFFB8D0FF);
    }

    /** The supermarket: shelves running into the distance under strip lights. */
    private static void market(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF2B3440, 0xFF232B35);
        for (int j = 0; j < 4; j++) {
            float sy = top + 24 + j * (horizon - top - 30) / 4f;
            c.fillRect(0, sy + 30, W, 4, 0xFF4C5868);
            for (int i = 0; i < 16; i++) {
                float bx = 6 + i * 23 + sway * 0.4f, bh = 16 + hash(i, j) * 12;
                int col = switch ((int) (hash(i, j + 11) * 5)) {
                    case 0 -> 0xFF8C5A5A;
                    case 1 -> 0xFF5A7C8C;
                    case 2 -> 0xFF8C845A;
                    case 3 -> 0xFF5A8C6B;
                    default -> 0xFF7A6A8C;
                };
                c.fillRoundRect(bx, sy + 30 - bh, 18, bh, 2, col);
            }
        }
        floor(c, horizon, bottom, W * 0.5f, 0xFF3A4048, 0xFF23272D, 0x22000000, 5);
        for (int i = 0; i < 2; i++) {
            float lx = 60 + i * 180 - sway;
            c.fillRoundRect(lx, top + 8, 70, 6, 3, 0xFFE8F0FF);
            Present.glow(c, lx + 35, top + 11, 50, 0xFFE8F0FF, 0.5f);
        }
    }

    /** The gas station at night: the canopy edge, a pump, the dark road. */
    private static void gas(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF0B1220, 0xFF1A2438);
        skyline(c, -10 + sway * 0.3f, W + 10, horizon, (horizon - top) * 0.22f, 0xFF0F1622, 0xFFFFD98A, t, 61);
        quad(c, 0, top, W, top, W, top + 34, 0, top + 52, 0xFF2C3644);
        quad(c, 0, top + 52, W, top + 34, W, top + 42, 0, top + 62, 0xFFFF9F5A);
        floor(c, horizon, bottom, W * 0.5f, 0xFF20252C, 0xFF14181E, 0x26000000, 4);
        float px = 70 - sway;
        c.fillRoundRect(px, horizon - 84, 52, 100, 6, 0xFFB8414A);
        c.fillRoundRect(px + 8, horizon - 74, 36, 24, 3, 0xFF14202C);
        c.fillRect(px + 12, horizon - 66, 22, 3, 0xFF5BD68A);
        c.fillRect(px - 6, horizon + 14, 64, 8, 0xFF59616C);
        for (int i = 0; i < 3; i++) {
            float lx = 70 + i * 110 - sway * 0.6f;
            cone(c, lx, top + 50, 14, 70, bottom, 0xFFE8F0FF);
            c.fillRoundRect(lx - 14, top + 46, 28, 5, 2, 0xFFE8F0FF);
        }
        pool(c, 180, horizon + (bottom - horizon) * 0.4f, 170, 30, 0xFFE8F0FF);
    }

    /** The evening school: a green board, a clock, rows of desks. */
    private static void school(Gfx c, float top, float bottom, float horizon, float sway, float t) {
        Present.vgrad(c, 0, top, W, horizon - top, 0xFF2E3437, 0xFF252A2D);
        float bx = 50 + sway * 0.4f, by = top + (horizon - top) * 0.2f, bw = 220, bh = (horizon - top) * 0.5f;
        c.fillRect(bx - 5, by - 5, bw + 10, bh + 10, 0xFF7A5C3E);
        c.fillRect(bx, by, bw, bh, 0xFF24483C);
        for (int i = 0; i < 4; i++) c.fillRect(bx + 16, by + 14 + i * 14, 60 + hash(i, 1) * 100, 2, 0x55FFFFFF);
        c.fillRect(bx, by + bh + 5, bw + 0, 4, 0xFF8E6D4A);
        c.fillCircle(310 - sway * 0.4f, by + 10, 14, 0xFFE9E2D0);
        c.line(310 - sway * 0.4f, by + 10, 310 - sway * 0.4f, by + 1, 1.5f, 0xFF1A1410);
        c.line(310 - sway * 0.4f, by + 10, 316 - sway * 0.4f, by + 13, 1.5f, 0xFF1A1410);
        floor(c, horizon, bottom, W * 0.5f, 0xFF3A3530, 0xFF211F1D, 0x22000000, 4);
        for (int i = 0; i < 3; i++) {
            float dx = 20 + i * 120 - sway * 1.2f, dy = horizon + 30;
            quad(c, dx, dy, dx + 90, dy, dx + 100, dy + 16, dx - 10, dy + 16, 0xFF8E6D4A);
            c.fillRect(dx - 4, dy + 16, 5, bottom - dy, 0xFF1A1410);
            c.fillRect(dx + 89, dy + 16, 5, bottom - dy, 0xFF1A1410);
        }
    }
}
