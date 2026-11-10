package com.wizzcraft.ima.core;

/** Sleeping at home: the clock races forward while energy refills. */
final class SleepScreen extends Screen {
    /** Game minutes per real second while asleep (8 hours in about 2.5 seconds). */
    private static final float RATE = 190;
    private float left;
    private float t;

    SleepScreen(Game g, int hours) {
        super(g);
        left = hours * 60;
    }

    @Override
    void update(float dt) {
        t += dt;
        float step = Math.min(left, dt * RATE);
        left -= step;
        g.advance(step, Game.SLEEP);
        if (g.screen != this) return;
        if (left <= 0) {
            g.screen = new BuildingScreen(g, City.HOME);
            g.toast(g.energy > 70 ? "בוקר טוב" : g.t("התעוררת עייף", "התעוררת עייפה"));
        }
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, 0xFF080B14);
        float cy = h * 0.42f;
        // Crescent moon out of two circles, and a few stars.
        c.fillCircle(Game.W / 2, cy, 46, 0xFFF4E7B0);
        c.fillCircle(Game.W / 2 + 20, cy - 12, 40, 0xFF080B14);
        for (int i = 0; i < 14; i++) {
            float sx = (i * 97 + 31) % 340 + 10, sy = Game.HUD_H + 20 + (i * 53) % (int) (h * 0.5f);
            float tw = 1.2f + 0.8f * (float) Math.sin(t * 3 + i);
            c.fillCircle(sx, sy, tw, 0xCCFFFFFF);
        }
        c.text(Ui.clock(g.minute), Game.W / 2, cy + 110, 38, Ui.INK, Gfx.CENTER);
        c.text(g.t("ישן...", "ישנה..."), Game.W / 2, cy + 142, 15, Ui.DIM, Gfx.CENTER);
        g.drawHud(c);
    }

    @Override
    boolean back() {
        return true;
    }
}
