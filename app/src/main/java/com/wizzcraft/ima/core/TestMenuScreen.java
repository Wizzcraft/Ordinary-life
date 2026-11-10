package com.wizzcraft.ima.core;

/**
 * "בדיקה": the list of every minigame in the build, opened from the title screen, to play any of
 * them directly. Nothing played from here touches the saved game (see {@link TestHost}).
 */
final class TestMenuScreen extends Screen {
    private static final int BACK = 999;

    TestMenuScreen(Game g) {
        super(g);
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        c.text("בדיקה", Game.W / 2, 40, 26, Ui.ACCENT, Gfx.CENTER);
        c.text("כל המשחקים, ישר לעניין. לא נוגע במשחק השמור.", Game.W / 2, 62, 12, Ui.DIM, Gfx.CENTER);
        btns.clear();
        int n = Minis.ORDER.length, rows = (n + 1) / 2;
        float top = 78, foot = 66, gap = 6;
        float rowH = Math.min(50, (h - top - foot) / rows - gap);
        TestHost names = new TestHost(g);
        for (int i = 0; i < n; i++) {
            Mini mini = Minis.create(Minis.ORDER[i]);
            if (mini == null) continue;
            mini.attach(names);
            float x = i % 2 == 0 ? 184 : 16, y = top + (i / 2) * (rowH + gap);
            btns.add(new Btn(i, x, y, 160, rowH, label(mini), true, mini.rounds() > 1));
        }
        btns.add(new Btn(BACK, 16, h - 58, 328, 46, "חזרה", true, false));
        drawButtons(c);
    }

    /** Jobs by their name; events, lessons and venues say what they are. */
    static String label(Mini mini) {
        String t = mini.title();
        int r = mini.rounds();
        return r > 1 ? "משמרת: " + t : t;
    }

    @Override
    void down(float x, float y) {
        Btn b = hit(x, y);
        if (b == null) return;
        g.sfx(Sfx.TAP);
        if (b.id == BACK) {
            g.screen = new TitleScreen(g);
            return;
        }
        if (b.id < 0 || b.id >= Minis.ORDER.length) return;
        Screen play = TestPlayScreen.open(g, Minis.ORDER[b.id]);
        if (play != null) g.screen = play;
    }

    @Override
    boolean back() {
        g.screen = new TitleScreen(g);
        return true;
    }

    @Override
    boolean inGame() {
        return false;
    }

    /** Not a place to come back to: the app reopens on the title screen. */
    @Override
    String kind() {
        return "title";
    }
}
