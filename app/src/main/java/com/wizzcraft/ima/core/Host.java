package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Everything a minigame may ask of the game around it. The engine implements this for real play;
 * the MiniLab test harness fakes it. A minigame never touches the engine directly.
 */
interface Host {
    /** Character stats for {@link #stat(int)}. */
    int FIT = 0, CHA = 1, EDU = 2;

    boolean female();

    /** The player's name; may be empty. */
    String name();

    /** Picks the masculine or feminine wording for the player. */
    String t(String m, String f);

    /** 0..100. Below 30 the player is tired. */
    float energy();

    /** 0..100. */
    float stat(int which);

    int money();

    /**
     * Adds money (delta > 0) or takes it (delta < 0). Returns false and changes nothing when the
     * player cannot afford it. Work shifts do NOT pay through this: the host pays wages itself from
     * the round results. Use it for bets, purchases, tips, fines.
     */
    boolean pay(int delta);

    /** Persistent variables that survive save/load. Keys are lowercase [a-z0-9_.], namespaced. */
    float var(String key);

    void setVar(String key, float value);

    Random rnd();

    // ---- game feel ----

    /** A text that pops, floats up and fades, e.g. "+₪20" or "מושלם!". */
    void text(float x, float y, String s, int argb);

    /** A burst of small polygon particles. */
    void burst(float x, float y, int argb, int count);

    /** Screen shake: 4 is light, 10 is heavy. */
    void shake(float amount);

    /** Plays a sound effect by {@link Sfx} id. */
    void sfx(int id);

    /** Logical screen height (width is always 360). */
    float screenH();
}
