package com.wizzcraft.ima.core;

/**
 * Touch targets with the behaviour UX.md 3.4 asks of every menu and dialog button: a target shows
 * its pressed state on touch-down and commits on release inside its bounds (plus 8 units of slop);
 * dragging off cancels. Targets are re-registered every frame by the view that draws them, keyed by
 * a small id, so the press survives layout changes between down and up.
 *
 * A fixed pool: no allocation after construction.
 */
final class Taps {
    static final int MAX = 24;
    static final float SLOP = 8;

    private final int[] id = new int[MAX];
    private final float[] x = new float[MAX], y = new float[MAX], w = new float[MAX], h = new float[MAX];
    /** Eased pressed amount per id (ids 0..MAX-1), for drawing. */
    private final float[] press = new float[MAX];
    private int n;
    private int armed = -1;
    private boolean inside;

    /** Forget the targets of the last frame (the press in progress is kept). */
    void begin() {
        n = 0;
    }

    /**
     * Registers a target. The hit area is grown to at least 48 x 48 around the drawn shape.
     * ids must be 0..MAX-1 and unique within a frame.
     */
    void add(int targetId, float tx, float ty, float tw, float th) {
        if (n >= MAX || targetId < 0 || targetId >= MAX) return;
        float gx = Math.max(0, (Present.HIT - tw) / 2), gy = Math.max(0, (Present.HIT - th) / 2);
        id[n] = targetId;
        x[n] = tx - gx;
        y[n] = ty - gy;
        w[n] = tw + 2 * gx;
        h[n] = th + 2 * gy;
        n++;
    }

    private int find(float px, float py, float slop) {
        for (int i = n - 1; i >= 0; i--) {
            if (px >= x[i] - slop && px <= x[i] + w[i] + slop && py >= y[i] - slop && py <= y[i] + h[i] + slop) return i;
        }
        return -1;
    }

    private int slot(int targetId) {
        for (int i = 0; i < n; i++) if (id[i] == targetId) return i;
        return -1;
    }

    /** Returns true if the touch landed on a target (the view should not treat it as a background tap). */
    boolean down(float px, float py) {
        int i = find(px, py, 0);
        armed = i < 0 ? -1 : id[i];
        inside = i >= 0;
        return i >= 0;
    }

    void move(float px, float py) {
        if (armed < 0) return;
        int i = slot(armed);
        inside = i >= 0 && px >= x[i] - SLOP && px <= x[i] + w[i] + SLOP && py >= y[i] - SLOP && py <= y[i] + h[i] + SLOP;
    }

    /** Returns the id committed by this release, or -1. */
    int up(float px, float py) {
        if (armed < 0) return -1;
        move(px, py);
        int res = inside ? armed : -1;
        armed = -1;
        inside = false;
        return res;
    }

    /** Drops the press in progress (the view changed under the finger, or the app paused). */
    void cancel() {
        armed = -1;
        inside = false;
    }

    boolean busy() {
        return armed >= 0;
    }

    /** Eases every press amount; call once per update. */
    void update(float dt) {
        for (int i = 0; i < MAX; i++) {
            boolean on = armed == i && inside;
            press[i] = Motion.approach(press[i], on ? 1 : 0, dt, on ? Motion.PRESS_IN : Motion.PRESS_OUT);
        }
    }

    /** 0..1 pressed amount of a target, for drawing. */
    float pressed(int targetId) {
        return targetId < 0 || targetId >= MAX ? 0 : press[targetId];
    }

    /** Shows a target as held down without a finger (the bench uses it for screenshots). */
    void hold(int targetId) {
        if (targetId >= 0 && targetId < MAX) press[targetId] = 1;
    }
}
