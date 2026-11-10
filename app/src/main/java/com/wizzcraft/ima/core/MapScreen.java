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
        boolean walking = path != null;
        float food = g.food, water = g.water, energy = g.energy, minute = g.minute;
        g.advance(gm, walking ? Game.WALK : Game.IDLE);
        if (g.screen != this) return;
        if (g.hasDialog() || g.food < 12 || g.water < 12 || g.energy < 12) ff = false;
        notice(food, water, energy, minute);
        // Walking is exercise, and a fitter walker covers more ground in the same time.
        if (walking) g.train(Host.FIT, gm / 60f * Game.FIT_PER_WALK_HOUR);
        float dist = gm * g.walkSpeed();
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

    /**
     * Says something when a need drops low or the night gets late, once per crossing. The values
     * are the ones from before this frame's time passed.
     */
    private void notice(float food, float water, float energy, float minute) {
        float low = Game.LOW_NEED;
        if (energy >= low && g.energy < low) {
            g.sfx(Sfx.BEEP);
            if (!g.momToast("tired")) g.toast("העיניים נעצמות. כדאי לישון.");
        } else if (food >= low && g.food < low) {
            g.sfx(Sfx.BEEP);
            g.toast("הבטן מקרקרת. כדאי לאכול משהו.");
        } else if (water >= low && g.water < low) {
            g.sfx(Sfx.BEEP);
            g.toast("הגרון יבש. כדאי לשתות.");
        } else if (minute < 60 && g.minute >= 60) {
            // One in the morning and still out on the street.
            g.momToast("night");
        }
    }

    private void arrive() {
        int id = target;
        path = null;
        target = -1;
        ff = false;
        g.at = id;
        g.sfx(Sfx.DOOR);
        g.screen = new BuildingScreen(g, id);
        // Now and then somebody stops you on the way in.
        if (g.rnd.nextFloat() < Game.STREET_CHANCE) g.play(Lines.street(g.facts(), g.rnd));
    }

    @Override
    void down(float x, float y) {
        Btn b = hit(x, y);
        if (b != null) {
            if (b.id == 0) {
                g.eat();
            } else if (b.id == 1) {
                g.drink();
            } else {
                ff = !ff;
                g.sfx(Sfx.TAP);
            }
            return;
        }
        if (y < Game.HUD_H || y > g.h - BAR_H) return;
        float cx = (x - ox) / s, cy = (y - oy) / s;
        for (City.Building bd : City.B) {
            if (Ui.in(cx, cy, bd.x - 6, bd.y - 8, bd.w + 12, bd.h + 16)) {
                if (g.at == bd.id && path == null) {
                    g.sfx(Sfx.DOOR);
                    g.screen = new BuildingScreen(g, bd.id);
                } else {
                    g.sfx(Sfx.TAP);
                    walkTo(bd.id);
                }
                return;
            }
        }
    }

    @Override
    boolean calm() {
        return true;
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
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        c.fillRect(0, Game.HUD_H, Game.W, h - Game.HUD_H - BAR_H, 0xFF24312A);

        // Roads.
        int road = 0xFF3B434F, mark = 0xFF596372;
        float x0 = City.ROADS_V[0] - City.ROAD_W / 2, x1 = City.ROADS_V[City.ROADS_V.length - 1] + City.ROAD_W / 2;
        float y0 = City.ROADS_H[0] - City.ROAD_W / 2, y1 = City.ROADS_H[City.ROADS_H.length - 1] + City.ROAD_W / 2;
        for (float ry : City.ROADS_H) {
            c.fillRect(ox + x0 * s, oy + (ry - City.ROAD_W / 2) * s, (x1 - x0) * s, City.ROAD_W * s, road);
        }
        for (float rx : City.ROADS_V) {
            c.fillRect(ox + (rx - City.ROAD_W / 2) * s, oy + y0 * s, City.ROAD_W * s, (y1 - y0) * s, road);
        }
        for (float ry : City.ROADS_H) {
            for (float dx = x0 + 14; dx < x1 - 10; dx += 22) {
                c.fillRect(ox + dx * s, oy + (ry - 0.7f) * s, 9 * s, 1.4f * s, mark);
            }
        }

        // Remaining path.
        if (path != null) {
            float lx = g.px, ly = g.py;
            for (int i = pi; i + 1 < path.length; i += 2) {
                c.line(ox + lx * s, oy + ly * s, ox + path[i] * s, oy + path[i + 1] * s, 2.2f * s, 0x99FFC857);
                lx = path[i];
                ly = path[i + 1];
            }
        }

        for (City.Building b : City.B) drawBuilding(c, b);

        boolean walking = path != null;
        float sx = ox + g.px * s, sy = oy + g.py * s;
        c.fillCircle(sx, sy + 3.5f * s, 5.5f * s, 0x55000000);
        Ui.person(c, sx, sy + 4 * s, 1.4f * s, g.female, t, walking, Ui.ACCENT);

        float n = night(g.minute);
        if (n > 0) {
            int a = (int) (n * 110);
            c.fillRect(0, Game.HUD_H, Game.W, h - Game.HUD_H - BAR_H, (a << 24) | 0x060A24);
        }

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
        c.fillRect(0, Game.HUD_H, Game.W, 24, 0x99000000);
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

    private void drawBuilding(Gfx c, City.Building b) {
        float x = ox + b.x * s, y = oy + b.y * s, w = b.w * s, hh = b.h * s;
        boolean open = b.isOpen(g.minute);
        int col = open ? b.color : Ui.darken(b.color, 0.55f);
        c.fillRect(x + 3 * s, y + 3 * s, w, hh, 0x66000000);
        c.fillRect(x, y, w, hh, col);
        // Roof seen from above: a lighter ridge polygon.
        c.fillPoly(new float[]{x + 6 * s, y + 5 * s, x + w - 6 * s, y + 5 * s, x + w - 14 * s, y + hh / 2,
                x + 14 * s, y + hh / 2}, 0x22FFFFFF);
        boolean doorBelow = b.doorY > b.y;
        float dx = ox + b.doorX * s;
        c.fillRect(dx - 5 * s, doorBelow ? y + hh - 7 * s : y, 10 * s, 7 * s, 0xFF151A21);
        int ink = Ui.isDark(col) ? Ui.INK : Ui.DARK_INK;
        c.text(b.name, x + w / 2, y + hh / 2 + 4.5f * s, 12.5f * s, ink, Gfx.CENTER);
        if (target == b.id || (g.at == b.id && path == null)) {
            c.strokeRoundRect(x - 3 * s, y - 3 * s, w + 6 * s, hh + 6 * s, 4 * s, 2 * s, Ui.ACCENT);
        }
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
