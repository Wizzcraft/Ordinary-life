package com.wizzcraft.ima.core;

final class TitleScreen extends Screen {
    private float t;
    /** Id of the "new game" button that was tapped once while a save exists; needs a second tap. */
    private int confirm;

    TitleScreen(Game g) {
        super(g);
    }

    @Override
    void update(float dt) {
        t += dt;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);

        // Night skyline with parallax, then the logo drawn around the title words.
        float base = h * 0.52f;
        Icons.titleBackdrop(c, Game.W, h, base, h * 0.17f + 44, t);
        Icons.titleLogo(c, Game.W / 2, h * 0.17f, c.textWidth("אמא על הקו", 40), t);

        c.text("אמא על הקו", Game.W / 2, h * 0.17f, 40, Ui.ACCENT, Gfx.CENTER);
        c.text("סימולטור חיים בעיר הגדולה", Game.W / 2, h * 0.17f + 30, 14, Ui.DIM, Gfx.CENTER);

        Ui.person(c, 232, base, 2.8f, false, t, false, Ui.BLUE);
        Ui.person(c, 128, base, 2.8f, true, t, false, Ui.ORANGE);

        btns.clear();
        float y = base + 26;
        boolean canContinue = g.started && !g.over;
        if (canContinue) {
            btns.add(new Btn(0, 40, y, 280, 54, "המשך · " + (g.name.isEmpty() ? "" : g.name + ", ") + "יום " + g.day, true, true));
            y += 68;
        }
        c.text(confirm != 0 ? "לחיצה נוספת תמחק את המשחק השמור" : "משחק חדש", Game.W / 2, y + 12, 13,
                confirm != 0 ? Ui.BAD : Ui.DIM, Gfx.CENTER);
        y += 26;
        btns.add(new Btn(1, 188, y, 132, 54, "גבר", true, !canContinue));
        btns.add(new Btn(2, 40, y, 132, 54, "אישה", true, !canContinue));
        drawButtons(c);

        c.text("גרסה " + g.version, Game.W / 2, h - 14, 10, Ui.DIM, Gfx.CENTER);
    }

    @Override
    void down(float x, float y) {
        Btn b = hit(x, y);
        if (b == null) {
            confirm = 0;
            return;
        }
        if (b.id == 0) {
            g.screen = new MapScreen(g);
            return;
        }
        if (g.started && !g.over && confirm != b.id) {
            confirm = b.id;
            return;
        }
        g.screen = new NameScreen(g, b.id == 2);
    }

    @Override
    String kind() {
        return "title";
    }
}
