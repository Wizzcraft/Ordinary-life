package com.wizzcraft.ima.core;

import java.util.HashMap;

/** Sleeping at home: the clock races forward while energy refills. A full night ends with the day summary. */
final class SleepScreen extends Screen {
    /** Game minutes per real second while asleep (8 hours in about 2.5 seconds). */
    private static final float RATE = 190;
    float left;
    /** How long this sleep is in total; a nap does not close the day. */
    private int hours;
    /** A piece of city wisdom to read while the night passes. */
    private String tip = "";
    private float t;

    SleepScreen(Game g, int hours) {
        super(g);
        this.hours = hours;
        left = hours * 60;
        tip = g.fill(Say.tip(g));
    }

    private SleepScreen(Game g) {
        super(g);
    }

    @Override
    void update(float dt) {
        t += dt;
        float step = Math.min(left, dt * RATE);
        left -= step;
        g.advance(step, Game.SLEEP);
        if (g.screen != this) return;
        if (left <= 0) {
            if (hours >= Game.SUMMARY_SLEEP_HOURS) {
                g.screen = SummaryScreen.open(g);
                return;
            }
            g.screen = new BuildingScreen(g, City.HOME);
            boolean morning = g.minute >= 5 * 60 && g.minute < 12 * 60;
            g.toast(g.energy <= 70 ? g.t("התעוררת עייף", "התעוררת עייפה") : morning ? "בוקר טוב" : "התעוררת");
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
        if (!tip.isEmpty()) Ui.paragraph(c, tip, Game.W / 2, cy + 186, 300, 13, 0xFF9FA9BA);
        g.drawHud(c);
    }

    @Override
    boolean back() {
        return true;
    }

    @Override
    String kind() {
        return "sleep";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "sl", left);
        Game.put(sb, "sh", hours);
        Game.put(sb, "st", Game.esc(tip));
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        SleepScreen s = new SleepScreen(g);
        s.left = Ui.clamp(Game.floatOf(m, "sl", 0), 0, 24 * 60);
        // Saves from before the summary existed don't say how long the sleep is: what is left decides.
        s.hours = (int) Ui.clamp(Game.intOf(m, "sh", (int) Math.ceil(s.left / 60)), 0, 24);
        s.tip = Game.strOf(m, "st");
        return s;
    }
}
