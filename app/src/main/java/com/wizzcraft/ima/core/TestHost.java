package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Random;

/**
 * The Host of the test list: a pretend wallet and pretend variables, so that playing a minigame
 * from the title screen never touches the saved game. Motion and sound are the real ones.
 */
final class TestHost implements Host {
    static final int WALLET = 1000;
    private final Game g;
    private final HashMap<String, Float> vars = new HashMap<>();
    int money = WALLET;

    TestHost(Game g) {
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
        return 80;
    }

    @Override
    public float stat(int which) {
        return 30;
    }

    @Override
    public int money() {
        return money;
    }

    @Override
    public boolean pay(int delta) {
        long after = (long) money + delta;
        if (delta < 0 && after < 0) return false;
        money = (int) Math.min(1_000_000_000L, after);
        return true;
    }

    @Override
    public float var(String key) {
        if (key == null) return 0;
        Float v = vars.get(key);
        return v != null ? v : key.equals("day") ? 1 : 0;
    }

    @Override
    public void setVar(String key, float value) {
        if (!Game.validKey(key) || Float.isNaN(value) || Float.isInfinite(value)) return;
        vars.put(key, key.equals("heat") ? Ui.clamp(value, 0, 100) : value);
    }

    @Override
    public Random rnd() {
        return g.rnd;
    }

    @Override
    public void text(float x, float y, String s, int argb) {
        g.host.text(x, y, s, argb);
    }

    @Override
    public void burst(float x, float y, int argb, int count) {
        g.host.burst(x, y, argb, count);
    }

    @Override
    public void shake(float amount) {
        g.host.shake(amount);
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
