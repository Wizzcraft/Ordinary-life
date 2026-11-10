package com.wizzcraft.ima.core;

/**
 * Model-building helpers on top of {@link Scene3}: a local frame (u along a facade from left to
 * right as seen from the street, v up, d out of the wall towards the street) and the handful of
 * low-poly shapes the town is made of. Used only while the world is being built.
 */
final class Kit3 {
    /** Box face masks. FRONT is the +d face, RIGHT the +u face. */
    static final int FRONT = 1, BACK = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32;
    static final int WALLS = 15, SOLID = 31, ALL = 63;
    /** Facade directions for {@link #frame}. */
    static final int SOUTH = 0, EAST = 1, NORTH = 2, WEST = 3;

    final Scene3 s;
    /** Frame origin and axes (unit, horizontal): U along the wall, D out of it. */
    float ox, oy, oz, ux, uz, dx, dz;
    private final float[] q = new float[3 * 20];
    private int qn;

    Kit3(Scene3 s) {
        this.s = s;
        frame(0, 0, 0, SOUTH);
    }

    /**
     * Sets the local frame for a wall that faces {@code facing}. The origin is the wall's bottom left
     * corner as seen by someone standing in front of it.
     */
    void frame(float x, float y, float z, int facing) {
        ox = x;
        oy = y;
        oz = z;
        dx = facing == EAST ? 1 : (facing == WEST ? -1 : 0);
        dz = facing == SOUTH ? 1 : (facing == NORTH ? -1 : 0);
        ux = dz;
        uz = -dx;
    }

    /** A free frame: origin and the direction of the U axis in degrees (0 = east, 90 = south). */
    void frameAngle(float x, float y, float z, float deg) {
        ox = x;
        oy = y;
        oz = z;
        ux = (float) Math.cos(Math.toRadians(deg));
        uz = (float) Math.sin(Math.toRadians(deg));
        dx = -uz;
        dz = ux;
    }

    float wx(float u, float d) {
        return ox + u * ux + d * dx;
    }

    float wz(float u, float d) {
        return oz + u * uz + d * dz;
    }

    /** Adds a corner (local coordinates) to the polygon being collected. */
    Kit3 at(float u, float v, float d) {
        q[qn * 3] = ox + u * ux + d * dx;
        q[qn * 3 + 1] = oy + v;
        q[qn * 3 + 2] = oz + u * uz + d * dz;
        qn++;
        return this;
    }

    /** Closes the collected polygon with a normal given in local coordinates. */
    void done(float nu, float nv, float nd, int col) {
        s.face(q, qn, nu * ux + nd * dx, nv, nu * uz + nd * dz, col);
        qn = 0;
    }

    /** Closes the collected polygon, taking the normal from its first three corners (flipped on demand). */
    void doneAuto(boolean flip, int col) {
        float e1x = q[3] - q[0], e1y = q[4] - q[1], e1z = q[5] - q[2];
        float e2x = q[6] - q[0], e2y = q[7] - q[1], e2z = q[8] - q[2];
        float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        if (flip) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        s.face(q, qn, nx, ny, nz, col);
        qn = 0;
    }

    void box(float u0, float v0, float d0, float u1, float v1, float d1, int col, int mask) {
        box(u0, v0, d0, u1, v1, d1, col, col, mask);
    }

    /** An axis-aligned box in the local frame; {@code top} colours the top face. */
    void box(float u0, float v0, float d0, float u1, float v1, float d1, int col, int top, int mask) {
        if ((mask & FRONT) != 0) at(u0, v0, d1).at(u1, v0, d1).at(u1, v1, d1).at(u0, v1, d1).done(0, 0, 1, col);
        if ((mask & BACK) != 0) at(u1, v0, d0).at(u0, v0, d0).at(u0, v1, d0).at(u1, v1, d0).done(0, 0, -1, col);
        if ((mask & LEFT) != 0) at(u0, v0, d0).at(u0, v0, d1).at(u0, v1, d1).at(u0, v1, d0).done(-1, 0, 0, col);
        if ((mask & RIGHT) != 0) at(u1, v0, d1).at(u1, v0, d0).at(u1, v1, d0).at(u1, v1, d1).done(1, 0, 0, col);
        if ((mask & TOP) != 0) at(u0, v1, d1).at(u1, v1, d1).at(u1, v1, d0).at(u0, v1, d0).done(0, 1, 0, top);
        if ((mask & BOTTOM) != 0) at(u0, v0, d0).at(u1, v0, d0).at(u1, v0, d1).at(u0, v0, d1).done(0, -1, 0, col);
    }

    /** A flat rectangle on the plane d = const, facing the street (a decal on a wall). */
    void rect(float u0, float v0, float u1, float v1, float d, int col) {
        at(u0, v0, d).at(u1, v0, d).at(u1, v1, d).at(u0, v1, d).done(0, 0, 1, col);
    }

    /** A decal on a wall that faces +u (the right side wall): spans d0..d1 and v0..v1 at u. */
    void rectU(float d0, float v0, float d1, float v1, float u, boolean positive, int col) {
        at(u, v0, d0).at(u, v0, d1).at(u, v1, d1).at(u, v1, d0).done(positive ? 1 : -1, 0, 0, col);
    }

    /** A flat rectangle lying on the plane v = const, facing up (a decal on the ground or a roof). */
    void flat(float u0, float d0, float u1, float d1, float v, int col) {
        at(u0, v, d1).at(u1, v, d1).at(u1, v, d0).at(u0, v, d0).done(0, 1, 0, col);
    }

    /** A regular polygon decal on the plane d = const (a clock face, a round sign). */
    void disc(float u, float v, float r, float d, int n, int col) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + Math.PI / n;
            at(u + (float) Math.cos(a) * r, v + (float) Math.sin(a) * r, d);
        }
        done(0, 0, 1, col);
    }

    /** A regular polygon lying flat at height v. */
    void discFlat(float u, float d, float r, float v, int n, int col) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + Math.PI / n;
            at(u + (float) Math.cos(a) * r, v, d + (float) Math.sin(a) * r);
        }
        done(0, 1, 0, col);
    }

    /**
     * A gable roof whose ridge runs along u: two slopes facing the street and the back, and two
     * triangular ends. Spans u0..u1 and d0..d1 at the eaves (height v0) and rises to vRidge.
     */
    void gable(float u0, float u1, float d0, float d1, float v0, float vRidge, int roof, int end) {
        float dm = (d0 + d1) * 0.5f, run = (d1 - d0) * 0.5f, rise = vRidge - v0;
        at(u0, v0, d1).at(u1, v0, d1).at(u1, vRidge, dm).at(u0, vRidge, dm).done(0, run, rise, roof);
        at(u1, v0, d0).at(u0, v0, d0).at(u0, vRidge, dm).at(u1, vRidge, dm).done(0, run, -rise, roof);
        at(u0, v0, d0).at(u0, v0, d1).at(u0, vRidge, dm).done(-1, 0, 0, end);
        at(u1, v0, d1).at(u1, v0, d0).at(u1, vRidge, dm).done(1, 0, 0, end);
    }

    /** A gable roof whose ridge runs along d (the gable end faces the street). */
    void gableD(float u0, float u1, float d0, float d1, float v0, float vRidge, int roof, int end) {
        float um = (u0 + u1) * 0.5f, run = (u1 - u0) * 0.5f, rise = vRidge - v0;
        at(u0, v0, d0).at(u0, v0, d1).at(um, vRidge, d1).at(um, vRidge, d0).done(-rise, run, 0, roof);
        at(u1, v0, d1).at(u1, v0, d0).at(um, vRidge, d0).at(um, vRidge, d1).done(rise, run, 0, roof);
        at(u0, v0, d1).at(u1, v0, d1).at(um, vRidge, d1).done(0, 0, 1, end);
        at(u1, v0, d0).at(u0, v0, d0).at(um, vRidge, d0).done(0, 0, -1, end);
    }

    /**
     * An upright prism or frustum with n sides around (u, d): radius r0 at v0 and r1 at v1. A cap
     * colour of 0 leaves the top open.
     */
    void drum(float u, float d, float r0, float r1, float v0, float v1, int n, int col, int cap) {
        double step = Math.PI * 2 / n, off = step * 0.5;
        for (int i = 0; i < n; i++) {
            double a0 = off + step * i, a1 = a0 + step, am = a0 + step * 0.5;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float slope = (r0 - r1) / Math.max(0.001f, v1 - v0);
            at(u + c0 * r0, v0, d + s0 * r0).at(u + c1 * r0, v0, d + s1 * r0);
            if (r1 > 0.001f) at(u + c1 * r1, v1, d + s1 * r1).at(u + c0 * r1, v1, d + s0 * r1);
            else at(u, v1, d);
            done((float) Math.cos(am), slope, (float) Math.sin(am), col);
        }
        if (cap != 0 && r1 > 0.001f) {
            for (int i = 0; i < n; i++) {
                double a = off + step * i;
                at(u + (float) Math.cos(a) * r1, v1, d + (float) Math.sin(a) * r1);
            }
            done(0, 1, 0, cap);
        }
    }

    /** An inverted frustum face set seen from below is never needed; this is a cone pointing down (a canopy belly). */
    void drumDown(float u, float d, float r0, float r1, float v0, float v1, int n, int col) {
        double step = Math.PI * 2 / n, off = step * 0.5;
        for (int i = 0; i < n; i++) {
            double a0 = off + step * i, a1 = a0 + step, am = a0 + step * 0.5;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float slope = (r0 - r1) / Math.max(0.001f, v1 - v0);
            if (r0 > 0.001f) at(u + c0 * r0, v0, d + s0 * r0).at(u + c1 * r0, v0, d + s1 * r0);
            else at(u, v0, d);
            at(u + c1 * r1, v1, d + s1 * r1).at(u + c0 * r1, v1, d + s0 * r1);
            done((float) Math.cos(am), slope, (float) Math.sin(am), col);
        }
    }

    /**
     * A striped awning: a sloped sheet from the wall at vTop out to {@code depth} at vLow, with a
     * short valance hanging from its front edge.
     */
    void awning(float u0, float u1, float vTop, float vLow, float depth, int stripes, int a, int b) {
        float w = (u1 - u0) / stripes, rise = vTop - vLow;
        int old = 0;
        for (int i = 0; i < stripes; i++) {
            int col = (i & 1) == 0 ? a : b;
            float x0 = u0 + w * i, x1 = x0 + w;
            at(x0, vLow, depth).at(x1, vLow, depth).at(x1, vTop, 0.1f).at(x0, vTop, 0.1f).done(0, depth, rise, col);
            at(x0, vLow - 1.1f, depth).at(x1, vLow - 1.1f, depth).at(x1, vLow, depth).at(x0, vLow, depth)
                    .done(0, 0, 1, shadeCol(col, 0.9f));
        }
        // The two triangular cheeks.
        at(u0, vLow, depth).at(u0, vTop, 0.1f).at(u0, vLow, 0.1f).done(-1, 0, 0, shadeCol(a, 0.8f));
        at(u1, vLow, 0.1f).at(u1, vTop, 0.1f).at(u1, vLow, depth).done(1, 0, 0, shadeCol(a, 0.8f));
    }

    // ---- Colour helpers -------------------------------------------------------------------------

    static int mix(int a, int b, float k) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * k), g = (int) (ag + (bg - ag) * k), bl = (int) (ab + (bb - ab) * k);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    /** Multiplies the colour's brightness (k below 1 darkens, above 1 moves towards white). */
    static int shadeCol(int c, float k) {
        if (k <= 1) {
            int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
            return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
        return mix(c, 0xFFFFFFFF, Math.min(1, k - 1));
    }

    static int alpha(int c, float a) {
        int ia = a <= 0 ? 0 : (a >= 1 ? 255 : (int) (a * 255));
        return (ia << 24) | (c & 0xFFFFFF);
    }

    /** A stable pseudo-random number in [0, 1) from two integers. */
    static float hash(int a, int b) {
        int h = a * 0x27D4EB2D + b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
