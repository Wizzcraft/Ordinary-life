package com.wizzcraft.ima.core;

/**
 * The motion language of the story views: a few timings and curves, used the same way everywhere,
 * so the game moves like one thing. docs/PRESENTATION.md section 4 is the specification.
 *
 * Three rules: things arrive from where they belong (a card rises from the thumb, a bubble grows
 * out of its speaker's side, the phone comes up from the pocket); emphasis is one soft overshoot,
 * never a blink; and nothing repeats faster than {@link #MAX_HZ}.
 *
 * {@link #reduce} is the reduce-motion setting: distances collapse to zero, repeating pulses stop
 * at their resting value and text appears whole. Fades stay, because they carry no movement.
 */
final class Motion {
    private Motion() {
    }

    /** Set by the settings screen ("תנועה מופחתת"). */
    static boolean reduce;

    /** Nothing in the story views repeats faster than this (the brief's limit is 3). */
    static final float MAX_HZ = 2.5f;

    // ---- durations, seconds ----
    static final float PRESS_IN = 0.08f, PRESS_OUT = 0.14f;
    /** A card, sheet or bubble arriving / leaving. */
    static final float ENTER = 0.22f, LEAVE = 0.14f;
    /** The phone coming up from the pocket and going back. */
    static final float PHONE_UP = 0.30f, PHONE_DOWN = 0.20f;
    /** Delay between siblings arriving one after another (answers, app icons, rows). */
    static final float STAGGER = 0.055f;
    /** After a card appears its buttons ignore touches for this long (UX.md 3.4). */
    static final float LOCK = 0.30f;
    /** A reaction of the speaker: a nod, a hop, a recoil. */
    static final float REACT = 0.45f;
    /** Emphasis: one overshoot of a card or a word. */
    static final float EMPHASIS = 0.28f;

    // ---- distances, logical units ----
    static final float RISE = 24, SLIDE = 12, NUDGE = 4;

    // ---- typing ----
    /** Characters per second of speech; a comma waits 0.12 s and a full stop 0.26 s. */
    static final float TYPE_CPS = 46;
    static final float PAUSE_COMMA = 0.12f, PAUSE_STOP = 0.26f;
    /** A soft tick every this many characters. */
    static final int TICK_EVERY = 3;

    // ---- curves ----
    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Progress 0..1 of something that started at {@code delay} and lasts {@code dur}. */
    static float in(float t, float delay, float dur) {
        return clamp01((t - delay) / dur);
    }

    static float outCubic(float p) {
        float q = 1 - clamp01(p);
        return 1 - q * q * q;
    }

    static float inCubic(float p) {
        p = clamp01(p);
        return p * p * p;
    }

    /** Ease-out with one soft overshoot of about 6%: the curve of emphasis. */
    static float outBack(float p) {
        p = clamp01(p);
        if (reduce) return outCubic(p);
        float s = 1.25f, q = p - 1;
        return 1 + q * q * ((s + 1) * q + s);
    }

    static float smooth(float p) {
        p = clamp01(p);
        return p * p * (3 - 2 * p);
    }

    /** How far from its place something still is: dist at p = 0, zero at p = 1; zero when reduced. */
    static float away(float p, float dist) {
        return reduce ? 0 : (1 - outCubic(p)) * dist;
    }

    /** A sine pulse 0..1 at hz (capped at MAX_HZ); rests at 0.5 when motion is reduced. */
    static float pulse(float t, float hz) {
        if (reduce) return 0.5f;
        float f = Math.min(hz, MAX_HZ);
        return 0.5f + 0.5f * (float) Math.sin(t * f * Math.PI * 2);
    }

    /** A ripple phase 0..1 that restarts {@code hz} times a second; 1 (finished) when reduced. */
    static float ripple(float t, float hz, float offset) {
        if (reduce) return 1;
        float f = Math.min(hz, MAX_HZ);
        float v = t * f + offset;
        return v - (float) Math.floor(v);
    }

    /** One up-and-back hump over 0..1 (a hop, a nod): 0 at both ends, 1 in the middle. */
    static float hump(float p) {
        if (reduce) return 0;
        p = clamp01(p);
        return (float) Math.sin(p * Math.PI);
    }

    /** A decaying side-to-side wobble over 0..1, {@code cycles} swings in all (keep cycles/dur under MAX_HZ). */
    static float wobble(float p, float cycles) {
        if (reduce) return 0;
        p = clamp01(p);
        return (float) Math.sin(p * cycles * Math.PI * 2) * (1 - p);
    }

    /** Moves cur towards target with time constant dur; for press states and other followers. */
    static float approach(float cur, float target, float dt, float dur) {
        float step = dt / Math.max(0.001f, dur);
        if (cur < target) return Math.min(target, cur + step);
        return Math.max(target, cur - step);
    }
}
