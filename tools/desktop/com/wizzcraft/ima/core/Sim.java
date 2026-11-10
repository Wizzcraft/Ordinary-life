package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Off-device test harness: drives the real game core with scripted taps, saves screenshots and
 * runs a 30-day bot and a random-tap soak. Run with: java -Djava.awt.headless=true ... Sim outDir
 */
public final class Sim {
    static final float DT = 1f / 30f;
    /** Logical height of a Pixel 10 Pro (20:9) after status and navigation bars. */
    static final float LH = 760;
    static Game g;
    static AwtGfx gfx;
    static String out;

    public static void main(String[] args) throws Exception {
        out = args.length > 0 ? args[0] : "tools/out";
        screenshots();
        bot(30, 0.75f, 1);
        bot(30, 0.35f, 2);
        monkey(400_000, 7);
        saveLoad();
        regressions();
        System.out.println("ALL OK");
    }

    static void frame() {
        g.update(DT, LH);
        g.draw(gfx);
    }

    static void frames(int n) {
        for (int i = 0; i < n; i++) frame();
    }

    static void shot(String name) throws Exception {
        AwtGfx big = new AwtGfx(720, (int) (LH * 2), 2f);
        g.draw(big);
        big.save(out + "/" + name + ".png");
    }

    static String screenName() {
        return g.screen.getClass().getSimpleName();
    }

    static void closeDialogs() {
        for (int i = 0; i < 20 && g.hasDialog(); i++) {
            frame();
            tapLabel("המשך");
        }
    }

    /** Taps the dialog button or a screen button whose label starts with the prefix. */
    static boolean tapLabel(String prefix) {
        frame();
        if (g.hasDialog()) {
            // The dialog button is the only thing tappable; find it by scanning its usual area.
            for (float y = 200; y < LH; y += 6) {
                boolean before = g.hasDialog();
                int n = dialogCount();
                g.down(180, y);
                g.up(180, y);
                if (dialogCount() != n || g.hasDialog() != before) return true;
            }
            return false;
        }
        for (Btn b : g.screen.btns) {
            if (b.label.startsWith(prefix)) {
                if (!b.on) return false;
                g.down(b.x + b.w / 2, b.y + b.h / 2);
                g.up(b.x + b.w / 2, b.y + b.h / 2);
                return true;
            }
        }
        return false;
    }

    static int dialogCount() {
        try {
            java.lang.reflect.Field f = Game.class.getDeclaredField("dialogs");
            f.setAccessible(true);
            return ((java.util.Collection<?>) f.get(g)).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void tapBuilding(int id) {
        frame();
        City.Building b = City.B[id];
        float areaH = LH - Game.HUD_H - MapScreen.BAR_H;
        float s = Math.min(Game.W / City.W, areaH / City.H);
        float ox = (Game.W - City.W * s) / 2, oy = Game.HUD_H + (areaH - City.H * s) / 2;
        float x = ox + (b.x + b.w / 2) * s, y = oy + (b.y + b.h / 2) * s;
        g.down(x, y);
        g.up(x, y);
    }

    /** Walks to a building and waits until we are inside it. Returns false if something interrupted. */
    static boolean goTo(int id) {
        if (!(g.screen instanceof MapScreen)) {
            if (g.screen instanceof BuildingScreen) tapLabel("חזרה");
            else return false;
        }
        closeDialogs();
        tapBuilding(id);
        for (int i = 0; i < 30 * 60; i++) {
            frame();
            if (g.hasDialog()) closeDialogs();
            if (g.over) return false;
            if (g.screen instanceof BuildingScreen && g.at == id) return true;
            if (g.screen instanceof MapScreen && (g.at == id || i % 90 == 89)) tapBuilding(id);
        }
        return false;
    }

    static void screenshots() throws Exception {
        g = new Game();
        gfx = new AwtGfx(36, 76, 0.1f);
        frame();
        shot("01-title");
        tapLabel("אישה");
        frame();
        shot("02-mom-dialog");
        closeDialogs();
        frame();
        shot("03-map-start");
        tapBuilding(City.RESTAURANT);
        frames(90);
        shot("04-walking");
        check(goTo(City.RESTAURANT), "arrive at restaurant");
        System.out.printf("walk home->restaurant took until %s%n", Ui.clock(g.minute));
        shot("05-restaurant");
        check(tapLabel("לבקש עבודה"), "ask for job");
        frame();
        shot("06-hired-dialog");
        closeDialogs();
        check(tapLabel("להתחיל משמרת"), "start shift");
        frame();
        shot("07-cook-intro");
        check(tapLabel("מתחילים"), "begin cooking");
        frames(20);
        shot("08-cook-play");
        Random r = new Random(3);
        boolean shotShow = false;
        for (int i = 0; i < 30 * 120 && "CookGame".equals(screenName()); i++) {
            frame();
            if (r.nextInt(25) == 0) {
                g.down(180, 500);
                g.up(180, 500);
                frame();
                if (!shotShow) {
                    shot("09-cook-result");
                    shotShow = true;
                }
            }
            if (g.screen.btns.size() == 1 && g.screen.btns.get(0).label.equals("המשך")) {
                shot("10-cook-done");
                tapLabel("המשך");
            }
        }
        System.out.printf("after shift: %s money=%d energy=%.0f food=%.0f water=%.0f%n", Ui.clock(g.minute),
                g.money, g.energy, g.food, g.water);
        tapLabel("לאכול (");
        tapLabel("לשתות (");
        check(goTo(City.MARKET), "arrive at market");
        tapLabel("ארוחה ארוזה");
        frame();
        shot("11-market");
        check(goTo(City.HOME), "arrive home");
        shot("12-home");
        check(tapLabel("לישון 8"), "sleep");
        frames(30);
        shot("13-sleep");
        frames(120);
        closeDialogs();
        shot("14-morning");
        tapLabel("חזרה");
        tapBuilding(City.CASINO);
        frames(200);
        shot("15-night-map");
        check(goTo(City.CASINO), "arrive at casino");
        shot("16-coming-soon");
        g.end("בדיקה של מסך הסיום.");
        frame();
        shot("17-end");
    }

    /**
     * A simple player: sleeps at night, eats and drinks when low, shops when the bag is empty and
     * works one shift a day. skill = chance that each dish is at least OK.
     */
    static void bot(int days, float skill, long seed) {
        g = new Game();
        gfx = new AwtGfx(36, 76, 0.1f);
        Random r = new Random(seed);
        frame();
        tapLabel("גבר");
        closeDialogs();
        int lastDay = 0, collapses = 0;
        int guard = 0;
        while (!g.over && g.day <= days && guard++ < 200000) {
            closeDialogs();
            if (g.day != lastDay) {
                lastDay = g.day;
                System.out.printf("  day %2d %s money=%5d food=%3.0f water=%3.0f energy=%3.0f bag=%d/%d debt=%d%n",
                        g.day, Ui.clock(g.minute), g.money, g.food, g.water, g.energy, g.meals, g.bottles, g.rentDebt);
            }
            if (g.screen instanceof SleepScreen || g.screen instanceof CookGame) {
                frame();
                if (g.screen instanceof CookGame) playShift(r, skill);
                continue;
            }
            if (g.screen instanceof MapScreen || g.screen instanceof BuildingScreen) {
                if (g.food < 55) tapLabel("לאכול (");
                if (g.water < 50) tapLabel("לשתות (");
            }
            boolean needShop = (g.meals < 2 || g.bottles < 1) && g.money >= 60;
            boolean canWork = g.cookHired ? (g.lastShiftDay != g.day && g.minute >= 8 * 60 && g.minute <= 14 * 60 && g.energy >= 55)
                    : (g.minute >= 8 * 60 && g.minute < 23 * 60);
            if (canWork) {
                if (!goTo(City.RESTAURANT)) continue;
                if (!g.cookHired) {
                    tapLabel("לבקש עבודה");
                    closeDialogs();
                }
                if (tapLabel("להתחיל משמרת")) {
                    tapLabel("מתחילים");
                    playShift(r, skill);
                }
                if (g.screen instanceof BuildingScreen && g.food < 50 && g.money > 300) tapLabel("לאכול כאן");
            } else if (needShop && City.B[City.MARKET].isOpen(g.minute) && g.minute < 20 * 60) {
                if (!goTo(City.MARKET)) continue;
                while (g.meals < 3 && tapLabel("ארוחה ארוזה")) frame();
                while (g.bottles < 2 && tapLabel("בקבוק מים")) frame();
            } else {
                if (!goTo(City.HOME)) continue;
                tapLabel("לשתות מהברז");
                if (g.energy < 60 || g.minute > 19 * 60 || g.minute < 5 * 60) {
                    tapLabel("לישון 8");
                    for (int i = 0; i < 400 && g.screen instanceof SleepScreen; i++) frame();
                } else {
                    frames(60);
                }
            }
        }
        System.out.printf("bot skill=%.2f -> day %d over=%s money=%d shifts=%d reason=%s%n", skill, g.day, g.over,
                g.money, g.shiftsDone, g.endReason);
    }

    static void playShift(Random r, float skill) {
        // Wait for each round and tap; emulate skill by tapping near the zone or at a random moment.
        for (int i = 0; i < 30 * 200 && g.screen instanceof CookGame; i++) {
            frame();
            if (g.hasDialog()) closeDialogs();
            if (!(g.screen instanceof CookGame)) return;
            CookGame cg = (CookGame) g.screen;
            if (cg.btns.size() == 1) {
                tapLabel("המשך");
                continue;
            }
            if (cg.playing()) {
                boolean good = r.nextFloat() < skill;
                if (good ? cg.inZone() : r.nextInt(40) == 0) {
                    g.down(180, 500);
                    g.up(180, 500);
                }
            }
        }
    }

    static void monkey(int frames, long seed) {
        g = new Game();
        gfx = new AwtGfx(36, 76, 0.1f);
        Random r = new Random(seed);
        int games = 0, maxDay = 0;
        for (int i = 0; i < frames; i++) {
            frame();
            if (r.nextInt(6) == 0) {
                float x = r.nextFloat() * Game.W, y = r.nextFloat() * LH;
                g.down(x, y);
                g.up(x, y);
            }
            if (r.nextInt(900) == 0) g.back();
            if (r.nextInt(5000) == 0) g.load(g.save());
            if (g.over) games++;
            maxDay = Math.max(maxDay, g.day);
            check(g.food >= 0 && g.food <= 100 && g.water >= 0 && g.water <= 100 && g.energy >= 0 && g.energy <= 100,
                    "meters in range");
            check(g.minute >= 0 && g.minute < 1440, "minute in range: " + g.minute);
            check(g.meals >= 0 && g.meals <= Game.BAG_MAX && g.bottles >= 0 && g.bottles <= Game.BAG_MAX, "bag in range");
        }
        System.out.printf("monkey: %d frames, reached day %d, game-over frames=%d, no exceptions%n", frames, maxDay, games);
    }

    static void saveLoad() {
        g = new Game();
        gfx = new AwtGfx(36, 76, 0.1f);
        frame();
        tapLabel("אישה");
        closeDialogs();
        goTo(City.MARKET);
        tapLabel("בקבוק מים");
        String s = g.save();
        int money = g.money, bottles = g.bottles, at = g.at;
        float minute = g.minute;
        Game g2 = new Game();
        g2.load(s);
        check(g2.started && g2.female && g2.money == money && g2.bottles == bottles && g2.at == at
                && Math.abs(g2.minute - minute) < 0.01f, "save/load round trip");
        Game g3 = new Game();
        g3.load("garbage;;=;v=1;started=true;money=abc");
        check(!g3.started, "corrupt save is ignored");
        g3.load(null);
        g3.load("");
        System.out.println("save/load ok: " + s);
    }

    /** Cases found in review; each one used to go wrong. */
    static void regressions() {
        // A shift can't be started without the energy to finish it.
        fresh();
        check(goTo(City.RESTAURANT), "reg: at restaurant");
        tapLabel("לבקש עבודה");
        closeDialogs();
        g.minute = 9 * 60;
        g.energy = 30;
        check(!tapLabel("להתחיל משמרת"), "reg: shift blocked at low energy");
        g.energy = 90;
        g.food = 0;
        check(!tapLabel("להתחיל משמרת"), "reg: shift blocked when starving");

        // Leaving the intro (or saving on it) doesn't burn today's shift.
        g.food = 80;
        check(tapLabel("להתחיל משמרת"), "reg: shift starts");
        check(g.screen instanceof CookGame && g.lastShiftDay != g.day, "reg: intro doesn't count as a shift");
        g.back();
        check(g.screen instanceof BuildingScreen, "reg: back leaves the intro");

        // Running out of strength mid-shift sends you home; no collapse, no ambulance bill.
        check(tapLabel("להתחיל משמרת"), "reg: shift starts again");
        tapLabel("מתחילים");
        int before = g.money, day = g.day;
        g.energy = 20;
        for (int i = 0; i < 30 * 60 && g.screen instanceof CookGame && ((CookGame) g.screen).btns.isEmpty(); i++) {
            frame();
            if (i % 20 == 0) {
                g.down(180, 500);
                g.up(180, 500);
            }
        }
        check(g.screen instanceof CookGame && !g.hasDialog() && g.day == day && g.money >= before && g.energy > 0,
                "reg: sent home instead of collapsing");
        tapLabel("המשך");

        // Several taps between two frames can't overfill the bag or overdraw the wallet.
        fresh();
        check(goTo(City.MARKET), "reg: at market");
        g.money = 70;
        g.meals = Game.BAG_MAX - 1;
        frame();
        Btn buy = null;
        for (Btn b : g.screen.btns) if (b.label.startsWith("ארוחה ארוזה")) buy = b;
        check(buy != null, "reg: buy button exists");
        for (int i = 0; i < 5; i++) g.down(buy.x + 5, buy.y + 5);
        check(g.meals == Game.BAG_MAX && g.money == 70 - Game.MEAL_PRICE, "reg: multi-tap buy is clamped");

        // Game over wipes any queued dialog.
        fresh();
        g.dialog("x", "y");
        g.end("בדיקה");
        check(!g.hasDialog() && g.screen instanceof EndScreen, "reg: no dialog over the end screen");
        System.out.println("regressions ok");
    }

    static void fresh() {
        g = new Game();
        gfx = new AwtGfx(36, 76, 0.1f);
        frame();
        tapLabel("גבר");
        closeDialogs();
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what + " (screen=" + screenName() + ", day=" + g.day + " "
                + Ui.clock(g.minute) + ")");
    }
}
