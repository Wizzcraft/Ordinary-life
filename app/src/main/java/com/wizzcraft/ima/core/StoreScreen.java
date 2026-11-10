package com.wizzcraft.ima.core;

import java.util.HashMap;

/**
 * PLACEHOLDER for the store: the store team's screen replaces the inside of this one. What the
 * engine relies on is only this: kind "store", the two ways in (the title screen and the flat's
 * menu), and the way back to where the player came from. Time stands still here.
 */
final class StoreScreen extends Screen {
    static final int FROM_TITLE = 0, FROM_HOME = 1;

    private final int from;

    StoreScreen(Game g, int from) {
        super(g);
        // The flat's menu only exists inside a running game.
        this.from = from == FROM_HOME && g.started && !g.over ? FROM_HOME : FROM_TITLE;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        float y = h * 0.2f;
        c.text("חנות", Game.W / 2, y, 34, Ui.ACCENT, Gfx.CENTER);
        // A little shop front out of polygons: awning, window, door.
        float sy = y + 34;
        c.fillRoundRect(110, sy + 24, 140, 86, 6, Ui.PANEL);
        for (int i = 0; i < 7; i++) {
            c.fillPoly(new float[]{104 + i * 22, sy, 126 + i * 22, sy, 126 + i * 22, sy + 26, 104 + i * 22, sy + 26},
                    i % 2 == 0 ? Ui.ACCENT : Ui.INK);
        }
        c.fillRect(122, sy + 44, 60, 40, 0xFF2F5876);
        c.fillRect(196, sy + 44, 40, 66, Ui.PANEL2);
        c.fillCircle(203, sy + 80, 3, Ui.ACCENT);
        y = Ui.paragraph(c, "בקרוב: בגדים, תסרוקות ועוד דברים קטנים שעושים טוב על הלב.", Game.W / 2, sy + 146, 300, 15,
                Ui.DIM);
        btns.clear();
        btns.add(new Btn(0, 40, Math.min(y + 14, h - 70), 280, 54, "חזרה", true, false));
        drawButtons(c);
    }

    private void leave() {
        g.sfx(Sfx.TAP);
        g.screen = from == FROM_HOME ? new BuildingScreen(g, City.HOME) : new TitleScreen(g);
    }

    @Override
    void down(float x, float y) {
        if (hit(x, y) != null) leave();
    }

    @Override
    boolean back() {
        leave();
        return true;
    }

    /** Opened from the title screen it is outside the game; from the flat it is part of it. */
    @Override
    boolean inGame() {
        return from == FROM_HOME;
    }

    @Override
    String kind() {
        return "store";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "sf", from);
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        return new StoreScreen(g, Game.intOf(m, "sf", FROM_TITLE));
    }
}
