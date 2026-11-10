package com.wizzcraft.ima.core;

import java.util.ArrayList;

/** One full-screen state of the game (map, a building, a minigame...). */
abstract class Screen {
    final Game g;
    /** Rebuilt on every draw; hit-tested on touch. */
    final ArrayList<Btn> btns = new ArrayList<>();

    Screen(Game g) {
        this.g = g;
    }

    void update(float dt) {
    }

    abstract void draw(Gfx c);

    void down(float x, float y) {
    }

    void up(float x, float y) {
    }

    /** Android back button. Return true if handled. */
    boolean back() {
        return false;
    }

    Btn hit(float x, float y) {
        for (Btn b : btns) if (b.hit(x, y)) return b;
        return null;
    }

    void drawButtons(Gfx c) {
        for (Btn b : btns) b.draw(c);
    }
}
