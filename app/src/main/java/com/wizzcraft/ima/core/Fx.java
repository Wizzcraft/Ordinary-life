package com.wizzcraft.ima.core;

import java.util.ArrayList;

/** Game feel: floating texts, polygon particles, screen shake. Drawn on top of whatever screen is up. */
final class Fx {
    private static final class Floater {
        float x, y, life, max, size;
        String s;
        int col;
    }

    private static final class Bit {
        float x, y, vx, vy, life, max, size, rot, spin;
        int col;
    }

    private final ArrayList<Floater> floaters = new ArrayList<>();
    private final ArrayList<Bit> bits = new ArrayList<>();
    private float shake, phase;
    private int seed = 12345;

    private float rand() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65535f;
    }

    /** A text that floats up and fades, e.g. "+₪39". */
    void text(float x, float y, String s, int col) {
        text(x, y, s, col, 22);
    }

    void text(float x, float y, String s, int col, float size) {
        if (floaters.size() > 24) floaters.remove(0);
        Floater f = new Floater();
        f.x = x;
        f.y = y;
        f.s = s;
        f.col = col;
        f.size = size;
        f.life = f.max = 1.1f;
        floaters.add(f);
    }

    /** A burst of little polygons flying out of a point. */
    void burst(float x, float y, int col, int n) {
        for (int i = 0; i < n && bits.size() < 120; i++) {
            Bit b = new Bit();
            double a = rand() * Math.PI * 2;
            float sp = 60 + rand() * 170;
            b.x = x;
            b.y = y;
            b.vx = (float) Math.cos(a) * sp;
            b.vy = (float) Math.sin(a) * sp - 60;
            b.life = b.max = 0.5f + rand() * 0.5f;
            b.size = 3 + rand() * 4;
            b.rot = rand() * 6;
            b.spin = (rand() - 0.5f) * 14;
            b.col = col;
            bits.add(b);
        }
    }

    /** Shakes the whole screen; amount is the starting offset in logical units (4 is light, 10 is heavy). */
    void shake(float amount) {
        shake = Math.max(shake, amount);
    }

    void clear() {
        floaters.clear();
        bits.clear();
        shake = 0;
    }

    void update(float dt) {
        phase += dt * 55;
        shake = Math.max(0, shake - dt * 28);
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Floater f = floaters.get(i);
            f.life -= dt;
            f.y -= 46 * dt;
            if (f.life <= 0) floaters.remove(i);
        }
        for (int i = bits.size() - 1; i >= 0; i--) {
            Bit b = bits.get(i);
            b.life -= dt;
            b.vy += 420 * dt;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.rot += b.spin * dt;
            if (b.life <= 0) bits.remove(i);
        }
    }

    float shakeX() {
        return (float) Math.sin(phase) * shake;
    }

    float shakeY() {
        return (float) Math.cos(phase * 1.3f) * shake * 0.6f;
    }

    void draw(Gfx c) {
        for (Bit b : bits) {
            int a = (int) (255 * Math.min(1, b.life / b.max * 2));
            int col = (a << 24) | (b.col & 0xFFFFFF);
            float cs = (float) Math.cos(b.rot) * b.size, sn = (float) Math.sin(b.rot) * b.size;
            c.fillPoly(new float[]{b.x + cs, b.y + sn, b.x - sn, b.y + cs, b.x - cs, b.y - sn}, col);
        }
        for (Floater f : floaters) {
            int a = (int) (255 * Math.min(1, f.life / f.max * 1.6f));
            float pop = 1 + 0.35f * Math.max(0, (f.life / f.max - 0.8f) * 5);
            c.text(f.s, f.x + 1, f.y + 1.5f, f.size * pop, (a / 2 << 24), Gfx.CENTER);
            c.text(f.s, f.x, f.y, f.size * pop, (a << 24) | (f.col & 0xFFFFFF), Gfx.CENTER);
        }
    }
}
