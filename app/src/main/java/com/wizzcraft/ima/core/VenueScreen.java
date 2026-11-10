package com.wizzcraft.ima.core;

import java.util.HashMap;

/**
 * An open-ended place (a casino table, the alley market): hosts a Mini with rounds() == 0 until
 * the player leaves through the Mini's own exit button or with back. Time stands still inside,
 * and the Mini moves real money through Host.pay. What happens on the way out is decided by
 * {@link Events#left}.
 */
final class VenueScreen extends Screen {
    /** What the Mini is told about difficulty: there is no shift to progress through, only tiredness. */
    static final float DIFFICULTY = 0.3f, DIFF_TIRED = 0.15f;

    final String id;
    final Mini mini;
    /** The building this venue is in; leaving goes back to its menu. */
    final int building;
    /** The wallet on the way in, so that mom can have an opinion on the way out. */
    int moneyIn;
    /**
     * begin() runs on the first frame after the screen opens, comes back from a pause or is restored,
     * never while loading: a Mini may write variables there, and loading must not change the save.
     */
    private boolean begun;
    private boolean finger;

    private VenueScreen(Game g, String id, Mini mini, int building) {
        super(g);
        this.id = id;
        this.mini = mini;
        this.building = building;
        moneyIn = g.money;
        mini.attach(g.host);
    }

    /** Opens a venue, or returns null when no Mini with that id is registered. */
    static VenueScreen open(Game g, String id, int building) {
        Mini mini = Minis.create(id);
        return mini == null ? null : new VenueScreen(g, id, mini, building);
    }

    @Override
    void update(float dt) {
        if (!begun) {
            begun = true;
            finger = false;
            mini.begin(0, Ui.clamp(DIFFICULTY + (g.energy < Game.TIRED ? DIFF_TIRED : 0), 0, 1));
        }
        mini.update(dt);
        if (g.screen == this && mini.wantsExit()) leave();
    }

    private void leave() {
        // Whatever is still on the table is settled before the player walks away.
        if (begun) mini.pause();
        Events.left(g, id, building, g.money - moneyIn);
    }

    @Override
    void down(float x, float y) {
        if (!begun || y <= Game.HUD_H) return;
        finger = true;
        mini.down(x, y);
    }

    @Override
    void move(float x, float y) {
        if (begun && finger) mini.move(x, y);
    }

    @Override
    void up(float x, float y) {
        if (begun && finger) mini.up(x, y);
        finger = false;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h, top = Game.PLAY_TOP, bottom = h - Game.PLAY_FOOT;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        btns.clear();
        if (begun) mini.draw(c, top, bottom);
        c.fillRect(0, Game.HUD_H, Game.W, top - Game.HUD_H, Ui.BG);
        c.fillRect(0, bottom, Game.W, Game.PLAY_FOOT, Ui.BG);
        c.text(mini.title(), Game.W / 2, Game.HUD_H + 44, 24, Ui.INK, Gfx.CENTER);
        // There is no intro card here, so the instructions stay in view under the table.
        Ui.paragraph(c, mini.howTo(), Game.W / 2, bottom + 22, 328, 12, Ui.DIM);
        g.drawHud(c);
    }

    @Override
    boolean back() {
        leave();
        return true;
    }

    @Override
    String kind() {
        return "venue";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "vm", Game.esc(id));
        Game.put(sb, "vb", building);
        Game.put(sb, "vi", moneyIn);
    }

    @Override
    void onAppPause() {
        if (begun) mini.pause();
        begun = false;
        finger = false;
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        int b = Game.intOf(m, "vb", -1);
        if (b < 0 || b >= City.B.length) return new MapScreen(g);
        VenueScreen v = open(g, Game.strOf(m, "vm"), b);
        if (v == null) return new BuildingScreen(g, b);
        v.moneyIn = Game.intOf(m, "vi", g.money);
        return v;
    }
}
