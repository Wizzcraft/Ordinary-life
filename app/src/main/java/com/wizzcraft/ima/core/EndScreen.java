package com.wizzcraft.ima.core;

/**
 * The end of a playthrough. Two looks: the plain "game over" when the player was thrown out, and
 * "the year is over" after the parents' visit, whose title and text come from the story's last beat.
 */
final class EndScreen extends Screen {
    EndScreen(Game g) {
        super(g);
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        boolean year = !g.endTitle.isEmpty();
        String title = year ? g.endTitle : "המשחק נגמר";
        float y = h * (year ? 0.2f : 0.26f);
        if (!g.name.isEmpty()) c.text(g.name, Game.W / 2, y - 44, 18, Ui.DIM, Gfx.CENTER);
        c.text(title, Game.W / 2, y, Ui.fit(c, title, 320, 34), year ? Ui.ACCENT : Ui.BAD, Gfx.CENTER);
        // A long ending gets a smaller type so the button stays on screen.
        float size = 16;
        while (size > 12 && y + 44 + Ui.wrap(c, g.endReason, 300, size).size() * size * 1.4f + 130 > h) size--;
        y = Ui.paragraph(c, g.endReason, Game.W / 2, y + 44, 300, size, Ui.INK);
        y += 18;
        if (year) {
            c.text("שנה שלמה בעיר · " + Ui.nis(g.money) + " בכיס", Game.W / 2, y, 15, Ui.DIM, Gfx.CENTER);
        } else {
            c.text("החזקת מעמד " + Ui.count(Math.max(1, g.day - 1), "יום אחד", "ימים"), Game.W / 2, y, 15, Ui.DIM,
                    Gfx.CENTER);
        }
        c.text("משמרות שעבדת: " + g.shiftsDone, Game.W / 2, y + 24, 15, Ui.DIM, Gfx.CENTER);
        btns.clear();
        btns.add(new Btn(0, 40, Math.min(y + 56, h - 62), 280, 54, "משחק חדש", true, true));
        drawButtons(c);
    }

    private void leave() {
        g.sfx(Sfx.TAP);
        g.started = false;
        g.screen = new TitleScreen(g);
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

    @Override
    boolean inGame() {
        return false;
    }

    @Override
    String kind() {
        return "end";
    }
}
