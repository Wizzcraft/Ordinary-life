package com.wizzcraft.ima.core;

import java.util.Random;

/** The real {@link Host}: everything a Mini is allowed to see and do in the running game. */
final class GameHost implements Host {
    private final Game g;

    GameHost(Game g) {
        this.g = g;
    }

    @Override
    public boolean female() {
        return g.female;
    }

    @Override
    public String name() {
        return g.name;
    }

    @Override
    public String t(String m, String f) {
        return g.t(m, f);
    }

    @Override
    public float energy() {
        return g.energy;
    }

    @Override
    public float stat(int which) {
        return g.stat(which);
    }

    @Override
    public int money() {
        return g.money;
    }

    @Override
    public boolean pay(int delta) {
        if (delta >= 0) {
            g.earn(delta);
            return true;
        }
        // -delta overflows for the smallest int; buy() refuses negative prices.
        return g.buy(-delta);
    }

    @Override
    public float var(String key) {
        return g.var(key);
    }

    @Override
    public void setVar(String key, float value) {
        g.setVar(key, value);
    }

    @Override
    public Random rnd() {
        return g.rnd;
    }

    @Override
    public void text(float x, float y, String s, int argb) {
        if (s != null && !s.isEmpty()) g.fx.text(x, y, s, argb);
    }

    @Override
    public void burst(float x, float y, int argb, int count) {
        if (count > 0) g.fx.burst(x, y, argb, Math.min(count, 40));
    }

    @Override
    public void shake(float amount) {
        // The comparison also keeps NaN out: a broken shake would move the whole screen forever.
        if (amount > 0) g.fx.shake(Math.min(amount, 14));
    }

    @Override
    public void sfx(int id) {
        g.sfx(id);
    }

    @Override
    public float screenH() {
        return g.h;
    }
}
