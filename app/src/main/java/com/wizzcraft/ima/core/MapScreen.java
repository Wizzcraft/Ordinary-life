package com.wizzcraft.ima.core;

/** The city seen from above. Tap a building and the character walks there along the roads. */
final class MapScreen extends Screen {
    static final float BAR_H = 70;
    static final float FAST = 6;

    private float[] path;
    private int pi;
    private int target = -1;
    private boolean ff;
    private float t;
    /** City-to-screen transform, refreshed on every draw. */
    private float s = 1, ox, oy;
    private final CityArt art = new CityArt();
    private final float[] mark = new float[6];

    MapScreen(Game g) {
        super(g);
        layout();
    }

    private void layout() {
        float areaH = g.h - Game.HUD_H - BAR_H;
        s = Math.min(Game.W / City.W, areaH / City.H);
        ox = (Game.W - City.W * s) / 2;
        oy = Game.HUD_H + (areaH - City.H * s) / 2;
    }

    void walkTo(int id) {
        City.Building b = City.B[id];
        target = id;
        path = City.path(g.px, g.py, b.doorX, b.doorY);
        pi = 0;
        g.at = -1;
    }

    @Override
    void update(float dt) {
        t += dt;
        float gm = dt * Game.MIN_PER_SEC * (ff ? FAST : 1);
        g.advance(gm, path != null ? Game.WALK : Game.IDLE);
        if (g.screen != this) return;
        if (g.hasDialog() || g.food < 12 || g.water < 12 || g.energy < 12) ff = false;
        float dist = gm * Game.WALK_SPEED;
        while (path != null && dist > 0) {
            float dx = path[pi] - g.px, dy = path[pi + 1] - g.py;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d <= dist) {
                g.px = path[pi];
                g.py = path[pi + 1];
                dist -= d;
                pi += 2;
                if (pi >= path.length) arrive();
            } else {
                g.px += dx / d * dist;
                g.py += dy / d * dist;
                dist = 0;
            }
        }
    }

    private void arrive() {
        int id = target;
        path = null;
        target = -1;
        ff = false;
        g.at = id;
        g.screen = new BuildingScreen(g, id);
    }

    @Override
    void down(float x, float y) {
        Btn b = hit(x, y);
        if (b != null) {
            if (b.id == 0) g.eat();
            else if (b.id == 1) g.drink();
            else ff = !ff;
            return;
        }
        if (y < Game.HUD_H || y > g.h - BAR_H) return;
        float cx = (x - ox) / s, cy = (y - oy) / s;
        for (City.Building bd : City.B) {
            if (Ui.in(cx, cy, bd.x - 6, bd.y - 8, bd.w + 12, bd.h + 16)) {
                if (g.at == bd.id && path == null) g.screen = new BuildingScreen(g, bd.id);
                else walkTo(bd.id);
                return;
            }
        }
    }

    /** 0 in daylight, up to 1 in the middle of the night. */
    static float night(float minute) {
        float dusk = 18 * 60, dark = 19.5f * 60, dawnStart = 5 * 60, dawnEnd = 6.5f * 60;
        if (minute >= dark || minute < dawnStart) return 1;
        if (minute >= dusk) return (minute - dusk) / (dark - dusk);
        if (minute < dawnEnd) return 1 - (minute - dawnStart) / (dawnEnd - dawnStart);
        return 0;
    }

    @Override
    void draw(Gfx c) {
        layout();
        float h = g.h, top = Game.HUD_H, bottom = h - BAR_H;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        art.begin(s, ox, oy, g.minute, t);
        art.ground(c, top, bottom);
        art.scene(c, top, bottom);
        for (City.Building b : City.B) drawBuilding(c, b);

        boolean walking = path != null;
        float night = art.darkness();
        // Remaining path: a dark underlay keeps the gold line readable on asphalt, sidewalk and grass.
        if (walking) {
            float lx = g.px, ly = g.py;
            for (int i = pi; i + 1 < path.length; i += 2) {
                float x0 = ox + lx * s, y0 = oy + ly * s, x1 = ox + path[i] * s, y1 = oy + path[i + 1] * s;
                c.line(x0, y0, x1, y1, 4.4f * s, 0x70101418);
                c.line(x0, y0, x1, y1, 2.2f * s, night > 0.5f ? 0xFFFFD36A : 0xEEFFC857);
                lx = path[i];
                ly = path[i + 1];
            }
        }
        drawPlayer(c, walking);

        // Status strip.
        String status;
        if (walking) {
            float mins = City.length(g.px, g.py, path, pi) / Game.WALK_SPEED;
            int hh = (int) (mins / 60), mm = (int) (mins % 60);
            status = g.t("הולך ", "הולכת ") + City.B[target].to + " · עוד "
                    + hh + ":" + (mm < 10 ? "0" : "") + mm + " שעות";
        } else if (g.at >= 0) {
            status = City.B[g.at].name + " · לחיצה על הבניין כדי להיכנס";
        } else {
            status = "לוחצים על בניין כדי ללכת אליו";
        }
        c.fillRect(0, Game.HUD_H, Game.W, 24, 0xB00E1218);
        c.text(status, Game.W / 2, Game.HUD_H + 17, 12, Ui.INK, Gfx.CENTER);

        g.drawHud(c);

        // Bottom bar.
        float by = h - BAR_H;
        c.fillRect(0, by, Game.W, BAR_H, Ui.PANEL);
        btns.clear();
        btns.add(new Btn(0, 244, by + 12, 100, 46, "לאכול (" + g.meals + ")", true, false));
        btns.add(new Btn(1, 130, by + 12, 100, 46, "לשתות (" + g.bottles + ")", true, false));
        btns.add(new Btn(2, 16, by + 12, 100, 46, ff ? "עצור" : "הרץ זמן", true, ff));
        drawButtons(c);
    }

    /** The player: a ground ring, the figure, and a bobbing marker above the head. */
    private void drawPlayer(Gfx c, boolean walking) {
        float sx = ox + g.px * s, sy = oy + g.py * s;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4);
        c.fillRoundRect(sx - 8 * s, sy - 3.5f * s, 16 * s, 7 * s, 3.5f * s, 0x66000000);
        c.strokeRoundRect(sx - (8 + pulse * 2) * s, sy - (3.6f + pulse) * s, (16 + pulse * 4) * s, (7.2f + pulse * 2) * s,
                (3.6f + pulse) * s, 1.4f * s, Ui.ACCENT);
        Ui.person(c, sx, sy + 1 * s, 1.15f * s, g.female, t, walking, Ui.ACCENT);
        float my = sy - 33 * s - pulse * 2.5f * s;
        mark[0] = sx - 5 * s;
        mark[1] = my - 4 * s;
        mark[2] = sx + 5 * s;
        mark[3] = my - 4 * s;
        mark[4] = sx;
        mark[5] = my + 3.5f * s;
        c.fillPoly(mark, 0xCC101418);
        mark[0] = sx - 3.4f * s;
        mark[1] = my - 2.8f * s;
        mark[2] = sx + 3.4f * s;
        mark[3] = my - 2.8f * s;
        mark[4] = sx;
        mark[5] = my + 1.8f * s;
        c.fillPoly(mark, Ui.ACCENT);
    }

    /** The cue over a building's art: a pulsing outline and a ring on its doorstep when it is the destination. */
    private void drawBuilding(Gfx c, City.Building b) {
        if (target != b.id && !(g.at == b.id && path == null)) return;
        float x = ox + b.x * s, y = oy + b.y * s, w = b.w * s, hh = b.h * s;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 5);
        c.strokeRoundRect(x - 3 * s, y - 3 * s, w + 6 * s, hh + 6 * s, 5 * s, 3.4f * s, 0x66101418);
        c.strokeRoundRect(x - 3 * s, y - 3 * s, w + 6 * s, hh + 6 * s, 5 * s, (1.6f + pulse) * s, Ui.ACCENT);
        float dx = ox + b.doorX * s, dy = oy + b.doorY * s, r = (5 + pulse * 2) * s;
        c.strokeRoundRect(dx - r, dy - r * 0.6f, r * 2, r * 1.2f, r * 0.6f, 1.6f * s, Ui.ACCENT);
    }

    @Override
    boolean back() {
        if (ff) {
            ff = false;
            return true;
        }
        g.screen = new TitleScreen(g);
        return true;
    }

    @Override
    String kind() {
        return "map";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "mt", target);
        Game.put(sb, "mf", ff);
    }

    /** Picks the walk back up where it was saved. */
    void restore(java.util.HashMap<String, String> m) {
        int t0 = Game.intOf(m, "mt", -1);
        if (t0 >= 0 && t0 < City.B.length) walkTo(t0);
        ff = Boolean.parseBoolean(m.get("mf"));
    }
}
