package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Random;

/**
 * Base of the one-off action events (a police chase, a street fight). It owns the intro card, the
 * outcome card, pausing and saving; an event only implements the action itself.
 * When the player taps through the outcome, {@link Game#eventDone(String, boolean)} applies it.
 */
abstract class EventGame extends Screen {
    static final int INTRO = 0, PLAY = 1, DONE = 2;
    final Random rnd = new Random();
    int state = INTRO;
    boolean won;
    /** Seconds since the screen opened, and since the action started. */
    float t, playT;

    EventGame(Game g) {
        super(g);
    }

    // ---------------- what an event implements ----------------

    /** Stable id used in the save and passed to Game.eventDone: "chase" or "fight". */
    abstract String id();

    abstract String title();

    /** One or two short sentences: what is happening and what to do. */
    abstract String howTo();

    /** Short outcome lines for the result card. */
    abstract String wonText();

    abstract String lostText();

    /** Start (or restart) the action. Use {@link #danger()} to scale it. */
    abstract void begin();

    /** Advance the action. Call {@link #finish(boolean)} exactly once when it is decided. */
    abstract void updatePlay(float dt);

    /** Draw the action only, between y = top and y = bottom, full width (0..360). */
    abstract void drawPlay(Gfx c, float top, float bottom);

    void playDown(float x, float y) {
    }

    void playUp(float x, float y) {
    }

    void drawIntroArt(Gfx c, float cx, float cy) {
    }

    /** Test-bot hook, called every frame during the action. skill is 0..1. */
    void botPlay(Random r, float skill) {
    }

    // ---------------- helpers ----------------

    /** 0..1: how hard this event should be. Grows with the player's heat. */
    final float danger() {
        return Ui.clamp(g.heat / 100f, 0, 1);
    }

    final boolean playing() {
        return state == PLAY;
    }

    final float playTop() {
        return 70;
    }

    final float playBottom() {
        return g.h - 20;
    }

    protected final void finish(boolean won) {
        if (state != PLAY) return;
        this.won = won;
        state = DONE;
        if (won) {
            g.sfx(Sfx.PERFECT);
            g.fx.burst(Game.W / 2, g.h * 0.4f, Ui.GOOD, 20);
        } else {
            g.sfx(Sfx.BAD);
            g.fx.shake(10);
        }
    }

    // ---------------- machinery ----------------

    @Override
    final void update(float dt) {
        t += dt;
        if (state == PLAY) {
            playT += dt;
            updatePlay(dt);
        }
    }

    @Override
    final void down(float x, float y) {
        if (state == PLAY) {
            playDown(x, y);
            return;
        }
        if (hit(x, y) == null) return;
        g.sfx(Sfx.TAP);
        if (state == INTRO) {
            playT = 0;
            state = PLAY;
            begin();
        } else {
            g.eventDone(id(), won);
        }
    }

    @Override
    final void up(float x, float y) {
        if (state == PLAY) playUp(x, y);
    }

    @Override
    final void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, 0xFF0C0F16);
        btns.clear();
        if (state == PLAY) {
            c.text(title(), Game.W / 2, 44, 20, Ui.INK, Gfx.CENTER);
            drawPlay(c, playTop(), playBottom());
        } else {
            c.text(title(), Game.W / 2, h * 0.16f, 30, state == DONE ? (won ? Ui.GOOD : Ui.BAD) : Ui.ACCENT,
                    Gfx.CENTER);
            drawIntroArt(c, Game.W / 2, h * 0.36f);
            String txt = state == INTRO ? howTo() : won ? wonText() : lostText();
            float y = Ui.paragraph(c, txt, Game.W / 2, h * 0.56f, 304, 16, Ui.INK);
            btns.add(new Btn(0, 40, y + 16, 280, 54, state == INTRO ? "קדימה" : "המשך", true, true));
        }
        drawButtons(c);
    }

    /** No way out of an event with the back button. */
    @Override
    final boolean back() {
        return true;
    }

    @Override
    final String kind() {
        return "event";
    }

    @Override
    final void save(StringBuilder sb) {
        Game.put(sb, "ek", id());
        Game.put(sb, "es", state);
        Game.put(sb, "ew", won);
    }

    /** Leaving the app mid-action restarts the event from its intro card. */
    @Override
    final void onAppPause() {
        if (state == PLAY) state = INTRO;
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        EventGame e = g.createEvent(m.getOrDefault("ek", ""));
        if (e == null) return new MapScreen(g);
        if (Game.intOf(m, "es", INTRO) == DONE) {
            e.state = DONE;
            e.won = Boolean.parseBoolean(m.get("ew"));
        }
        return e;
    }
}
