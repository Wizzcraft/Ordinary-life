package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * A minigame: the playable heart of a work shift, a street event, a study session or a casino
 * table. It is hosted by the engine (or by MiniLab in tests), which owns everything around it:
 * the intro card showing {@link #title()}, {@link #howTo()} and {@link #art}, pausing, the clock,
 * wages, the result banner, saving. A Mini only implements what happens inside its play area.
 *
 * Kinds, by {@link #rounds()}:
 *  - work shift: 8, 4 or 2 rounds (a shift is 8 game hours split evenly). Each round ends with
 *    MISS / OK / PERFECT and the host pays for it.
 *  - event or study session: 1 round. For events MISS means lost; OK and PERFECT mean won.
 *  - venue (casino table, market): 0 rounds = open-ended. update() keeps returning RUNNING and the
 *    player leaves through the Mini's own exit button ({@link #wantsExit()}).
 */
abstract class Mini {
    static final int RUNNING = -1, MISS = 0, OK = 1, PERFECT = 2;
    /** Logical screen width. Height varies; the host passes the play area to draw(). */
    static final float W = 360;

    protected Host h;

    final void attach(Host host) {
        h = host;
    }

    abstract int rounds();

    /** Short title, e.g. "מלצר" or "בלאקג'ק". */
    abstract String title();

    /** One or two short sentences for the intro card: what to do and how to do it well. */
    abstract String howTo();

    /** Sets up round {@code round} (0-based). difficulty is 0..1 and already includes rank and tiredness. */
    abstract void begin(int round, float difficulty);

    /** Advances by dt seconds. Returns RUNNING, or MISS / OK / PERFECT once the round is decided. */
    abstract int update(float dt);

    /** Draws the play area only: between y = top and y = bottom, across the full width 0..360. */
    abstract void draw(Gfx c, float top, float bottom);

    /** Touch, in logical units. move() is a drag while the finger is down. */
    void down(float x, float y) {
    }

    void up(float x, float y) {
    }

    void move(float x, float y) {
    }

    /** Optional polygon illustration for the intro card, centred on (cx, cy), about 120 tall. */
    void art(Gfx c, float cx, float cy, float time) {
    }

    /**
     * The app is going to the background or the session is being interrupted. Settle anything in
     * flight fairly (for example, return a bet that has not been resolved). A round interrupted
     * this way is replayed from begin().
     */
    void pause() {
    }

    /** Venues only: true once the player has chosen to leave. */
    boolean wantsExit() {
        return false;
    }

    /**
     * Test bot, called every frame while a round runs. skill is 0 (clueless) .. 1 (expert). It must
     * act only through down / up / move, exactly like a finger would.
     */
    void bot(Random r, float skill) {
    }
}
