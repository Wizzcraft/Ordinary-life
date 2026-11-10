package com.wizzcraft.ima.core;

final class EndScreen extends Screen {
    EndScreen(Game g) {
        super(g);
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        float y = h * 0.26f;
        c.text("המשחק נגמר", Game.W / 2, y, 34, Ui.BAD, Gfx.CENTER);
        y = Ui.paragraph(c, g.endReason, Game.W / 2, y + 44, 300, 16, Ui.INK);
        y += 18;
        c.text("החזקת מעמד " + g.day + " ימים", Game.W / 2, y, 15, Ui.DIM, Gfx.CENTER);
        c.text("משמרות שעבדת: " + g.shiftsDone, Game.W / 2, y + 24, 15, Ui.DIM, Gfx.CENTER);
        btns.clear();
        btns.add(new Btn(0, 40, y + 56, 280, 54, "משחק חדש", true, true));
        drawButtons(c);
    }

    @Override
    void down(float x, float y) {
        if (hit(x, y) != null) {
            g.started = false;
            g.screen = new TitleScreen(g);
        }
    }

    @Override
    boolean back() {
        g.started = false;
        g.screen = new TitleScreen(g);
        return true;
    }
}
