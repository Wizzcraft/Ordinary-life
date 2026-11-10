package com.wizzcraft.ima.core;

import java.util.HashMap;

/**
 * The morning after a real night's sleep: what the day that ended brought in and cost, how far the
 * rent is, what changed in the player, where they stand at work, and a word from mom. Opening it
 * closes the day's books, so every summary covers exactly the time since the previous one.
 */
final class SummaryScreen extends Screen {
    private int earned, spent;
    /** How much each stat changed since the previous summary. */
    private final float[] delta = new float[3];
    private String quip = "";

    private SummaryScreen(Game g) {
        super(g);
    }

    /** Takes the day's numbers, closes the books and asks mom for her line. */
    static SummaryScreen open(Game g) {
        SummaryScreen s = new SummaryScreen(g);
        s.earned = (int) g.var("day.earned");
        s.spent = (int) g.var("day.spent");
        for (int i = 0; i < 3; i++) s.delta[i] = g.stat(i) - g.statAtLastSummary(i);
        g.closeDay();
        s.quip = g.fill(Say.summary(g));
        return s;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        // Short screens get the blocks back to back; taller ones let them breathe.
        float air = Ui.clamp((h - 560) / 200f, 0, 1.4f) * 22;
        boolean morning = g.minute >= 5 * 60 && g.minute < 12 * 60;
        float y = Game.HUD_H + 34 + air * 0.6f;
        String hello = (morning ? "בוקר טוב" : "התעוררת") + (g.name.isEmpty() ? "" : ", " + g.name);
        c.text(hello, Game.W / 2, y, Ui.fit(c, hello, 320, 24), Ui.ACCENT, Gfx.CENTER);
        c.text("סיכום היום שעבר", Game.W / 2, y + 20, 12, Ui.DIM, Gfx.CENTER);

        // Money in, money out, and what is left of it.
        y += 32 + air * 0.5f;
        c.fillRoundRect(24, y, 312, 84, 12, Ui.PANEL);
        int net = earned - spent;
        moneyRow(c, y + 24, "הכנסות", "+" + Ui.nis(earned), earned > 0 ? Ui.GOOD : Ui.DIM, 14);
        moneyRow(c, y + 46, "הוצאות", Ui.nis(-spent), spent > 0 ? Ui.BAD : Ui.DIM, 14);
        c.fillRect(40, y + 54, 280, 1, Ui.PANEL2);
        moneyRow(c, y + 74, "נשאר מהיום", (net > 0 ? "+" : "") + Ui.nis(net), net < 0 ? Ui.BAD : Ui.INK, 16);
        y += 84;

        y += 20 + air * 0.4f;
        c.text(g.rentLine(), Game.W / 2, y, 13, g.rentWorry() ? Ui.BAD : Ui.DIM, Gfx.CENTER);

        // The three stats, each with what changed since the last summary.
        y += 14 + air * 0.5f;
        for (int i = 0; i < 3; i++) {
            float cx = 300 - i * 120;
            c.text(Job.STAT_NAMES[i], cx, y + 12, 11, Ui.DIM, Gfx.CENTER);
            c.text(Ui.num(g.stat(i), false), cx, y + 32, 18, Ui.INK, Gfx.CENTER);
            if (Math.abs(delta[i]) >= 0.05f) {
                c.text(Ui.num(delta[i], true), cx, y + 47, 12, delta[i] > 0 ? Ui.GOOD : Ui.BAD, Gfx.CENTER);
            }
        }
        y += 52;

        // Jobs and ranks, two to a row.
        y += 12 + air * 0.5f;
        int shown = 0, more = 0;
        for (Job j : Job.ALL) {
            if (!g.hired(j.id)) continue;
            if (shown == 6) {
                more++;
                continue;
            }
            String s = j.name(g.female) + " · " + Job.rankName(g.female, g.rank(j.id));
            c.text(s, shown % 2 == 0 ? 258 : 102, y + (shown / 2) * 18, 13, Ui.INK, Gfx.CENTER);
            shown++;
        }
        if (shown == 0) c.text("עדיין בלי עבודה", Game.W / 2, y, 13, Ui.DIM, Gfx.CENTER);
        y += Math.max(1, (shown + 1) / 2) * 18 - 18;
        if (more > 0) {
            y += 18;
            c.text("ועוד " + more, Game.W / 2, y, 12, Ui.DIM, Gfx.CENTER);
        }

        if (!quip.isEmpty()) {
            y += 24 + air * 0.5f;
            String said = "אמא: " + quip;
            float size = Ui.wrap(c, said, 304, 13).size() > 3 ? 11.5f : 13;
            Ui.paragraph(c, said, Game.W / 2, y, 304, size, Ui.ACCENT);
        }

        btns.clear();
        btns.add(new Btn(0, 40, h - 66, 280, 50, "המשך", true, true));
        drawButtons(c);
        g.drawHud(c);
    }

    private static void moneyRow(Gfx c, float y, String label, String value, int col, float size) {
        c.text(label, 320, y, size, Ui.DIM, Gfx.RIGHT);
        c.text(value, 40, y, size, col, Gfx.LEFT);
    }

    private void leave() {
        g.sfx(Sfx.TAP);
        g.screen = new BuildingScreen(g, City.HOME);
        boolean morning = g.minute >= 5 * 60 && g.minute < 12 * 60;
        g.toast(g.energy <= 70 ? g.t("התעוררת עייף", "התעוררת עייפה") : morning ? "בוקר טוב" : "התעוררת");
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
    String kind() {
        return "summary";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "ue", earned);
        Game.put(sb, "us", spent);
        for (int i = 0; i < 3; i++) Game.put(sb, "ud" + i, delta[i]);
        Game.put(sb, "uq", Game.esc(quip));
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        SummaryScreen s = new SummaryScreen(g);
        s.earned = Game.intOf(m, "ue", 0);
        s.spent = Game.intOf(m, "us", 0);
        for (int i = 0; i < 3; i++) s.delta[i] = Game.floatOf(m, "ud" + i, 0);
        s.quip = Game.strOf(m, "uq");
        return s;
    }
}
