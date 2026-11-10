package com.wizzcraft.ima.core;

import java.util.HashMap;

/**
 * A one-round event (a chase, a fight, a lesson): hosts the Mini between an intro card and an
 * outcome card. What the outcome means is not decided here: when the player closes the outcome
 * card, {@link Events#done} applies the consequences and picks the next screen. Time stands still
 * while the event is played.
 */
final class EventScreen extends Screen {
    static final int INTRO = 0, PLAY = 1, DONE = 2;

    /** The Mini's id; it also names the consequences in {@link Events}. */
    final String id;
    final Mini mini;
    /** 0..1, chosen by whoever started the event. */
    final float difficulty;
    int state = INTRO, result = Mini.OK;
    private boolean finger;
    private float t;

    private EventScreen(Game g, String id, Mini mini, float difficulty) {
        super(g);
        this.id = id;
        this.mini = mini;
        this.difficulty = Ui.clamp(difficulty, 0, 1);
        mini.attach(g.host);
    }

    /** Opens the intro card of an event, or returns null when no Mini with that id is registered. */
    static EventScreen open(Game g, String id, float difficulty) {
        Mini mini = Minis.create(id);
        return mini == null ? null : new EventScreen(g, id, mini, difficulty);
    }

    boolean playing() {
        return state == PLAY;
    }

    /** For events MISS means lost; OK and PERFECT mean won. */
    boolean won() {
        return result != Mini.MISS;
    }

    @Override
    void update(float dt) {
        t += dt;
        if (state != PLAY) return;
        int res = mini.update(dt);
        if (res == Mini.RUNNING) return;
        result = res < Mini.MISS ? Mini.MISS : Math.min(res, Mini.PERFECT);
        state = DONE;
        if (won()) {
            g.sfx(Sfx.PERFECT);
            g.fx.burst(Game.W / 2, g.h * 0.4f, Ui.GOOD, 20);
        } else {
            g.sfx(Sfx.BAD);
            g.fx.shake(10);
        }
    }

    @Override
    void down(float x, float y) {
        if (state == PLAY) {
            if (y > Game.HUD_H) {
                finger = true;
                mini.down(x, y);
            }
            return;
        }
        if (hit(x, y) == null) return;
        g.sfx(Sfx.TAP);
        if (state == INTRO) {
            finger = false;
            state = PLAY;
            mini.begin(0, difficulty);
        } else {
            Events.done(g, id, result);
        }
    }

    @Override
    void move(float x, float y) {
        if (state == PLAY && finger) mini.move(x, y);
    }

    @Override
    void up(float x, float y) {
        if (state == PLAY && finger) mini.up(x, y);
        finger = false;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        btns.clear();
        if (state == PLAY) {
            float top = Game.PLAY_TOP, bottom = h - Game.PLAY_FOOT;
            mini.draw(c, top, bottom);
            c.fillRect(0, Game.HUD_H, Game.W, top - Game.HUD_H, Ui.BG);
            c.fillRect(0, bottom, Game.W, Game.PLAY_FOOT, Ui.BG);
            c.text(mini.title(), Game.W / 2, Game.HUD_H + 44, 24, Ui.INK, Gfx.CENTER);
            // The instructions stay in view under the action: nobody reads an intro card in a panic.
            Ui.paragraph(c, mini.howTo(), Game.W / 2, bottom + 22, 328, 12, Ui.DIM);
        } else {
            boolean done = state == DONE;
            c.text(mini.title(), Game.W / 2, Game.HUD_H + 50, 30, !done ? Ui.ACCENT : won() ? Ui.GOOD : Ui.BAD,
                    Gfx.CENTER);
            mini.art(c, Game.W / 2, h * 0.40f, t);
            String txt = done ? g.fill(Events.outcome(g, id, result)) : mini.howTo();
            float y = Ui.paragraph(c, txt, Game.W / 2, h * 0.40f + 92, 304, 16, Ui.INK);
            btns.add(new Btn(0, 40, Math.min(y + 4, h - 62), 280, 54, done ? "המשך" : "קדימה", true, true));
        }
        drawButtons(c);
        g.drawHud(c);
    }

    /** There is no way out of an event with the back button. */
    @Override
    boolean back() {
        return true;
    }

    @Override
    String kind() {
        return "event";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "ek", Game.esc(id));
        Game.put(sb, "es", state);
        Game.put(sb, "er", result);
        Game.put(sb, "ed", difficulty);
    }

    /** Leaving the app mid-action restarts the event from its intro card. */
    @Override
    void onAppPause() {
        if (state == PLAY) {
            mini.pause();
            state = INTRO;
        }
        finger = false;
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        EventScreen e = open(g, Game.strOf(m, "ek"), Game.floatOf(m, "ed", 0));
        if (e == null) return new MapScreen(g);
        if (Game.intOf(m, "es", INTRO) == DONE) {
            e.state = DONE;
            e.result = (int) Ui.clamp(Game.intOf(m, "er", Mini.OK), Mini.MISS, Mini.PERFECT);
        }
        return e;
    }
}
