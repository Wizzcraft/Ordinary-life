package com.wizzcraft.ima.core;

/**
 * The perspective camera of the 3D city: a pose (look-at target, yaw, pitch, distance), the basis
 * derived from it, and the projection onto a viewport given in screen units.
 *
 * World axes: x = City x (east), z = City y (south), y = up. Yaw 0 looks north (towards -z) and grows
 * clockwise seen from above, so yaw 90 degrees looks east. Pitch is positive looking down. The
 * camera never rolls, so the horizon is always a horizontal line on screen.
 *
 * The principal point (cx, cy) need not be the middle of the viewport: the city view puts it low so
 * the look-at target sits in the lower half and the sky gets the top (a lens shift, as on a view
 * camera; vertical lines keep their natural convergence).
 */
final class Cam3 {
    /** Geometry closer than this to the eye plane is clipped away. */
    static final float NEAR = 1.2f;

    /** Pose. */
    float tx, ty, tz, yaw, pitch, dist;
    /** Eye position, derived from the pose. */
    float ex, ey, ez;
    /** Orthonormal basis in world space: right, up, forward. */
    float rx, ry, rz, ux, uy, uz, fx, fy, fz;
    /** Focal length in screen units and the principal point. */
    float focal = 386, cx = 180, cy = 300;
    /** Viewport rectangle in screen units. */
    float x0, y0, x1 = 360, y1 = 600;
    /** Frustum side slopes: a view-space point is inside when -z*tl <= x <= z*tr and -z*tb <= y <= z*tt. */
    float tl, tr, tt, tb;

    /** Sets the viewport and lens. {@code centreY} is the principal point as a fraction of the height. */
    void lens(float left, float top, float right, float bottom, float hfovDeg, float centreY) {
        x0 = left;
        y0 = top;
        x1 = right;
        y1 = bottom;
        cx = (left + right) * 0.5f;
        cy = top + (bottom - top) * centreY;
        focal = (right - left) * 0.5f / (float) Math.tan(Math.toRadians(hfovDeg) * 0.5);
        tl = (cx - x0) / focal;
        tr = (x1 - cx) / focal;
        tt = (cy - y0) / focal;
        tb = (y1 - cy) / focal;
    }

    /** Places the camera: it looks at (tx, ty, tz) from {@code dist} away. Angles in radians. */
    void look(float tx, float ty, float tz, float yaw, float pitch, float dist) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dist = dist;
        float sy = (float) Math.sin(yaw), cyw = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch), cp = (float) Math.cos(pitch);
        fx = sy * cp;
        fy = -sp;
        fz = -cyw * cp;
        rx = cyw;
        ry = 0;
        rz = sy;
        // up = right x forward
        ux = sy * sp;
        uy = cp;
        uz = -cyw * sp;
        ex = tx - fx * dist;
        ey = ty - fy * dist;
        ez = tz - fz * dist;
    }

    /** Depth of a world point along the view axis (positive in front of the eye). */
    float depth(float x, float y, float z) {
        return (x - ex) * fx + (y - ey) * fy + (z - ez) * fz;
    }

    /**
     * Projects a world point. Writes screen x, screen y and depth into out[0..2] and returns false
     * (leaving out untouched) when the point is behind the near plane.
     */
    boolean project(float x, float y, float z, float[] out) {
        float dx = x - ex, dy = y - ey, dz = z - ez;
        float vz = dx * fx + dy * fy + dz * fz;
        if (!(vz > NEAR)) return false;
        float k = focal / vz;
        out[0] = cx + (dx * rx + dy * ry + dz * rz) * k;
        out[1] = cy - (dx * ux + dy * uy + dz * uz) * k;
        out[2] = vz;
        return true;
    }

    /** World-space direction (not normalised) of the ray through a screen point, into out[0..2]. */
    void ray(float sx, float sy, float[] out) {
        float a = sx - cx, b = cy - sy;
        out[0] = fx * focal + rx * a + ux * b;
        out[1] = fy * focal + ry * a + uy * b;
        out[2] = fz * focal + rz * a + uz * b;
    }

    /** Screen y of the horizon (the vanishing line of the ground). */
    float horizonY() {
        float t = (float) Math.tan(pitch);
        return cy - focal * t;
    }

    /** Shortest signed difference b - a between two angles, in (-pi, pi]. */
    static float angDiff(float a, float b) {
        float d = (b - a) % 6.2831855f;
        if (d > 3.1415927f) d -= 6.2831855f;
        if (d <= -3.1415927f) d += 6.2831855f;
        return d;
    }

    /** Exponential approach factor for a time constant {@code tau} over {@code dt} seconds. */
    static float ease(float dt, float tau) {
        if (tau <= 0) return 1;
        float k = 1 - (float) Math.exp(-dt / tau);
        return k < 0 ? 0 : (k > 1 ? 1 : k);
    }
}
