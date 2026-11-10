package com.wizzcraft.ima.core;

/**
 * Hosts one Mini for the test list: intro card, every round in a row (or the venue until the
 * player leaves), a result card, and back to the list. No clock, no wages, nothing saved.
 */
final class TestPlayScreen extends Screen {
    static final int INTRO = 0, PLAY = 1, SHOW = 2, DONE = 3;
    private static final int GO = 0, LIST = 1, QUIT = 2, LEVEL = 3;
    private static final float SHOW_TIME = 0.8f;
    private static final String[] LEVELS = {"קל", "רגיל", "קשה"};
    /** What the level adds to the difficulty a Mini is given. */
    private static final float[] LEVEL_DIFF = {0f, 0.2f, 0.4f};

    final String id;
    final Mini mini;
    final TestHost host;
    final int rounds;
    final int[] results;
    int state = INTRO, round, level;
    private boolean finger;
    private float t, showT;

    private TestPlayScreen(Game g, String id, Mini mini) {
        super(g);
        this.id = id;
        this.mini = mini;
        host = new TestHost(g);
        mini.attach(host);
        rounds = Math.max(0, mini.rounds());
        results = new int[Math.max(1, rounds)];
    }

    static TestPlayScreen open(Game g, String id) {
        Mini mini = Minis.create(id);
        return mini == null ? null : new TestPlayScreen(g, id, mini);
    }

    private float difficulty() {
        float progress = rounds > 1 ? (float) round / (rounds - 1) : 0.5f;
        return Ui.clamp(progress * 0.6f + LEVEL_DIFF[level], 0, 1);
    }

    private void startRound() {
        finger = false;
        state = PLAY;
        mini.begin(round, difficulty());
    }

    private void toList() {
        if (state == PLAY) mini.pause();
        g.screen = new TestMenuScreen(g);
    }

    @Override
    void update(float dt) {
        t += dt;
        if (state == PLAY) {
            int res = mini.update(dt);
            if (rounds == 0) {
                if (g.screen == this && mini.wantsExit()) toList();
                return;
            }
            if (res == Mini.RUNNING) return;
            results[round] = res < Mini.MISS ? Mini.MISS : Math.min(res, Mini.PERFECT);
            g.sfx(results[round] == Mini.PERFECT ? Sfx.PERFECT : results[round] == Mini.OK ? Sfx.GOOD : Sfx.BAD);
            state = SHOW;
            showT = SHOW_TIME;
        } else if (state == SHOW) {
            showT -= dt;
            if (showT > 0) return;
            round++;
            if (round >= rounds) {
                round = rounds;
                state = DONE;
            } else {
                startRound();
            }
        }
    }

    @Override
    void down(float x, float y) {
        Btn b = hit(x, y);
        if (state == PLAY && (b == null || y > Game.HUD_H)) {
            if (y > Game.HUD_H) {
                finger = true;
                mini.down(x, y);
            }
            return;
        }
        if (b == null) return;
        g.sfx(Sfx.TAP);
        switch (b.id) {
            case GO:
                round = 0;
                startRound();
                break;
            case LEVEL:
                level = (level + 1) % LEVELS.length;
                break;
            default:
                toList();
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
        float h = g.h, top = Game.PLAY_TOP, bottom = h - Game.PLAY_FOOT;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        btns.clear();
        boolean live = state == PLAY || state == SHOW;
        if (live) {
            mini.draw(c, top, bottom);
            c.fillRect(0, Game.HUD_H, Game.W, top - Game.HUD_H, Ui.BG);
            c.fillRect(0, bottom, Game.W, Game.PLAY_FOOT, Ui.BG);
        }
        // The strip where the game's HUD sits in real play: the pretend wallet and the way out.
        c.fillRect(0, 0, Game.W, Game.HUD_H, Ui.PANEL);
        c.text("בדיקה · " + LEVELS[level], Game.W - 14, 30, 13, Ui.DIM, Gfx.RIGHT);
        c.text(TestMenuScreen.label(mini), Game.W - 14, 62, 20, Ui.INK, Gfx.RIGHT);
        c.text(Ui.nis(host.money), 14, 30, 17, Ui.ACCENT, Gfx.LEFT);
        if (live) btns.add(new Btn(QUIT, 12, 44, 104, 40, "יציאה", true, false));

        if (state == INTRO || state == DONE) {
            mini.art(c, Game.W / 2, h * 0.36f, t);
            float y = h * 0.36f + 88;
            if (state == DONE) {
                int perfect = 0, ok = 0, miss = 0;
                for (int i = 0; i < rounds; i++) {
                    if (results[i] == Mini.PERFECT) perfect++;
                    else if (results[i] == Mini.OK) ok++;
                    else miss++;
                }
                String sum = rounds == 1 ? (results[0] == Mini.PERFECT ? "מושלם!" : results[0] == Mini.OK ? "עבר בשלום"
                        : "לא הפעם") : "מושלם: " + perfect + " · טוב: " + ok + " · פספוס: " + miss;
                c.text(sum, Game.W / 2, y, 18, miss == 0 ? Ui.GOOD : Ui.INK, Gfx.CENTER);
                y += 16;
            } else {
                y = Ui.paragraph(c, mini.howTo(), Game.W / 2, y, 304, 13, Ui.DIM);
            }
            float by = Math.min(y + 6, h - 176);
            btns.add(new Btn(GO, 40, by, 280, 52, state == INTRO ? "קדימה" : "שוב", true, true));
            if (rounds > 0) btns.add(new Btn(LEVEL, 40, by + 58, 280, 44, "רמה: " + LEVELS[level], true, false));
            btns.add(new Btn(LIST, 40, by + 108, 280, 44, "חזרה לרשימה", true, false));
        } else if (rounds > 1) {
            c.text("שלב " + Math.min(round + 1, rounds) + " מתוך " + rounds, Game.W / 2, Game.HUD_H + 40, 14, Ui.DIM,
                    Gfx.CENTER);
            float x = Game.W / 2 + (rounds - 1) * 11;
            int done = state == SHOW ? round + 1 : round;
            for (int i = 0; i < rounds; i++) {
                int col = i >= done ? Ui.PANEL2 : results[i] == Mini.PERFECT ? Ui.ACCENT
                        : results[i] == Mini.OK ? Ui.GOOD : Ui.BAD;
                c.fillCircle(x - i * 22, h - 60, 7, col);
            }
        } else {
            Ui.paragraph(c, mini.howTo(), Game.W / 2, bottom + 22, 328, 12, Ui.DIM);
        }
        if (state == SHOW) {
            int r = results[round];
            String msg = r == Mini.PERFECT ? "מושלם!" : r == Mini.OK ? "טוב" : "פספוס";
            int col = r == Mini.PERFECT ? Ui.ACCENT : r == Mini.OK ? Ui.GOOD : Ui.BAD;
            float my = (top + bottom) / 2;
            c.fillRoundRect(70, my - 34, 220, 56, 14, 0xE0101418);
            c.text(msg, Game.W / 2, my + 6, 30, col, Gfx.CENTER);
        }
        drawButtons(c);
    }

    @Override
    boolean back() {
        toList();
        return true;
    }

    /** Coming back to the app restarts the round from its intro card. */
    @Override
    void onAppPause() {
        if (state == PLAY) {
            mini.pause();
            state = INTRO;
        } else if (state == SHOW) {
            state = INTRO;
        }
        finger = false;
    }

    @Override
    boolean inGame() {
        return false;
    }

    /** Not saved: the app reopens on the title screen. */
    @Override
    String kind() {
        return "title";
    }
}
