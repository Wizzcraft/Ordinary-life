package com.wizzcraft.ima.core;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * UX audit harness: plays a first session the way a new player would, at logical heights 760 and 560,
 * and for every screen writes a screenshot, a touch-target overlay, and a measurement log (every text
 * call with its size, colour, the background actually under it and the WCAG contrast ratio; every
 * button with its size and its distance to the nearest neighbour). Run:
 *   java -Djava.awt.headless=true -cp tools/out/classes com.wizzcraft.ima.core.UxShots outDir
 */
public final class UxShots {
    static final float DT = 1f / 30f;
    static final float SCALE = 2f;
    static Game g;
    static float lh;
    static String out;
    static int seq;
    /** Unique (size, fg, bg) text samples across the whole run, keyed for the summary. */
    static final Map<String, String> pairs = new TreeMap<>();
    static final Map<String, String> smallTargets = new TreeMap<>();
    static PrintWriter timeline;

    public static void main(String[] args) throws Exception {
        out = args.length > 0 ? args[0] : "tools/out/ux";
        new File(out).mkdirs();
        timeline = new PrintWriter(new FileWriter(out + "/timeline.txt"));
        for (float h : new float[]{760, 560}) {
            lh = h;
            seq = 0;
            timeline.println("=== height " + (int) h + " ===");
            session();
        }
        timeline.close();
        try (PrintWriter p = new PrintWriter(new FileWriter(out + "/contrast.txt"))) {
            p.println("ratio\tsize\tfg\tbg\tscreen\ttext");
            for (String v : pairs.values()) p.println(v);
        }
        try (PrintWriter p = new PrintWriter(new FileWriter(out + "/targets.txt"))) {
            for (String v : smallTargets.values()) p.println(v);
        }
        pairShots();
        // The numbers docs/UX.md asks to drive to zero (keyboard keys excepted).
        int lowContrast = 0, smallText = 0;
        for (String v : pairs.values()) {
            String[] f = v.trim().split("\t");
            if (Double.parseDouble(f[0]) < 4.5) lowContrast++;
            if (Double.parseDouble(f[1]) < 12) smallText++;
        }
        int smallBtn = 0;
        for (String k : smallTargets.keySet()) if (!k.startsWith("keyboard key")) smallBtn++;
        System.out.printf("text/background pairs below 4.5:1: %d | distinct text styles below 12: %d | "
                + "buttons under 48x48 or closer than 8 (not keys): %d%n", lowContrast, smallText, smallBtn);
        System.out.println("UX OK " + out);
    }

    // ------------------------------------------------------------------ driving

    static void frame() {
        g.update(DT, lh);
        g.draw(new AwtGfx(4, 4, 0.01f));
    }

    static void frames(int n) {
        for (int i = 0; i < n; i++) frame();
    }

    static void log(String s) {
        timeline.printf("[%3d] day %d %s  money %d  food %.0f water %.0f energy %.0f  %s%n", seq, g.day,
                Ui.clock(g.minute), g.money, g.food, g.water, g.energy, s);
    }

    static Btn dialogBtn() {
        return (Btn) field("dialogBtn");
    }

    @SuppressWarnings("unchecked")
    static String dialogTitle() {
        ArrayDeque<String[]> d = (ArrayDeque<String[]>) field("dialogs");
        return d.isEmpty() ? "" : d.peek()[0];
    }

    static Object field(String name) {
        try {
            Field f = Game.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(g);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void tapAt(float x, float y) {
        g.down(x, y);
        g.up(x, y);
        frame();
    }

    static boolean tap(String prefix) {
        frame();
        if (g.hasDialog()) {
            Btn b = dialogBtn();
            if (b == null) return false;
            tapAt(b.x + b.w / 2, b.y + b.h / 2);
            return true;
        }
        for (Btn b : new ArrayList<>(g.screen.btns)) {
            if (b.label.startsWith(prefix) && b.on) {
                tapAt(b.x + b.w / 2, b.y + b.h / 2);
                return true;
            }
        }
        log("!! no enabled button '" + prefix + "' on " + g.screen.getClass().getSimpleName());
        return false;
    }

    /** Screenshots and dismisses every queued dialog. */
    static void dialogs(String tag) throws Exception {
        for (int i = 0; i < 12 && g.hasDialog(); i++) {
            frame();
            shot(tag + "-dlg-" + slug(dialogTitle()));
            tap("המשך");
        }
    }

    static String slug(String s) {
        switch (s) {
            case "אמא על הקו": return "mom";
            case "איך משחקים": return "howto";
            case "התקבלת!": return "hired";
            case "שכירות": return "rent";
            case "התעלפת": return "collapse";
            default: return "x";
        }
    }

    static float[] buildingCentre(int id) {
        float areaH = lh - Game.HUD_H - MapScreen.BAR_H;
        float s = Math.min(Game.W / City.W, areaH / City.H);
        float ox = (Game.W - City.W * s) / 2, oy = Game.HUD_H + (areaH - City.H * s) / 2;
        City.Building b = City.B[id];
        return new float[]{ox + (b.x + b.w / 2) * s, oy + (b.y + b.h / 2) * s};
    }

    static void tapBuilding(int id) {
        frame();
        float[] p = buildingCentre(id);
        tapAt(p[0], p[1]);
    }

    /** Walks like a player: tap the building, wait, accept dialogs along the way. */
    static boolean walk(int id, String tag) throws Exception {
        if (g.screen instanceof BuildingScreen) tap("חזרה");
        float start = g.day * 1440 + g.minute;
        tapBuilding(id);
        for (int i = 0; i < 30 * 90; i++) {
            frame();
            if (g.hasDialog()) dialogs(tag);
            if (g.over) return false;
            if (g.screen instanceof BuildingScreen && g.at == id) {
                log("walked to " + City.B[id].name + " in " + (int) (g.day * 1440 + g.minute - start) + " game min");
                return true;
            }
            if (g.screen instanceof MapScreen && i % 120 == 119) tapBuilding(id);
        }
        return false;
    }

    // ------------------------------------------------------------------ the session

    static void session() throws Exception {
        g = new Game();
        frames(5);
        shot("title-fresh");
        tap("אישה");
        frames(2);
        shot("name-empty");
        for (String k : new String[]{"נ", "ו", "ע", "ה"}) tap(k);
        shot("name-typed");
        tap("המשך");
        dialogs("start");
        frames(10);
        shot("map-start");

        tapBuilding(City.RESTAURANT);
        frames(45);
        shot("map-walking");
        tap("הרץ זמן");
        frames(6);
        shot("map-walking-ff");
        for (int i = 0; i < 30 * 60 && !(g.screen instanceof BuildingScreen); i++) frame();
        log("arrived at restaurant (first walk, with fast-forward)");
        shot("restaurant-new");
        tap("לבקש עבודה");
        dialogs("job");
        shot("restaurant-hired");
        tap("להתחיל משמרת");
        shot("cook-intro");
        tap("מתחילים");
        frames(25);
        shot("cook-play");
        cookShift(true);
        shot("restaurant-after-shift");

        walk(City.MARKET, "market");
        shot("market");
        tap("ארוחה ארוזה");
        frames(3);
        shot("market-toast");
        tap("בקבוק מים");
        tap("בקבוק מים");

        walk(City.HOME, "home");
        shot("home-evening");
        tap("לשתות מהברז");
        tap("לישון 8");
        frames(25);
        shot("sleep");
        for (int i = 0; i < 600 && g.screen instanceof SleepScreen; i++) frame();
        dialogs("wake");
        frames(2);
        shot("home-morning");

        // Day two: the job is blocked until 08:00, walk into a "coming soon" building.
        walk(City.CASINO, "casino");
        shot("casino-soon");
        walk(City.RESTAURANT, "rest2");
        shot("restaurant-day2");
        if (tap("להתחיל משמרת")) {
            tap("מתחילים");
            cookShift(false);
        }

        // Evening on the map, then night.
        tap("חזרה");
        fastForwardUntil(19 * 60 + 40);
        shot("map-night");
        walk(City.MARKET, "mkt-night");
        shot("market-closed-or-open");

        // Resume in the middle of a dish (app killed), and the title with a game to continue.
        g.minute = 9 * 60;
        g.energy = 90;
        g.lastShiftDay = 0;
        walk(City.RESTAURANT, "rest3");
        tap("להתחיל משמרת");
        tap("מתחילים");
        frames(20);
        reload();
        shot("cook-resumed-paused");
        tap("ממשיכים");
        cookShift(false);
        tap("חזרה");
        frame();
        g.back();
        frames(2);
        shot("title-continue");
        tap("גבר");
        frames(2);
        shot("title-confirm-new");
        tapAt(10, 10);
        tap("המשך");
        frames(5);

        // Running out of food: stop eating, let time run on the street.
        g.meals = 0;
        g.bottles = 0;
        frame();
        tap("לאכול");
        frames(2);
        shot("map-empty-bag-toast");
        g.food = 22;
        g.water = 40;
        tap("הרץ זמן");
        for (int i = 0; i < 30 * 40 && g.food > 15; i++) frame();
        frames(5);
        shot("map-low-food");
        // Blink state on the other phase.
        frames(5);
        shot("map-low-food-blink");
        g.water = 25;
        for (int i = 0; i < 30 * 200 && !g.hasDialog(); i++) {
            frame();
            if (g.screen instanceof MapScreen && i % 30 == 0) {
                MapScreen ms = (MapScreen) g.screen;
                boolean ff = false;
                for (Btn b : ms.btns) if (b.label.equals("עצור")) ff = true;
                if (!ff) tap("הרץ זמן");
            }
        }
        log("collapse or dialog reached");
        dialogs("starve");
        shot("map-after-collapse");

        // Rent day with too little money, then the second missed rent.
        g.money = 120;
        g.day = 10;
        g.minute = 23 * 60 + 30;
        g.food = 80;
        g.water = 80;
        g.energy = 80;
        log("jump to rent eve, money 120");
        frame();
        shot("map-rent-eve");
        for (int i = 0; i < 30 * 30 && !g.hasDialog(); i++) frame();
        dialogs("rent1");
        g.day = 20;
        g.minute = 23 * 60 + 30;
        g.money = 50;
        g.food = 80;
        g.water = 80;
        g.energy = 80;
        for (int i = 0; i < 30 * 30 && !g.over; i++) {
            frame();
            if (g.hasDialog()) dialogs("rent2");
        }
        frames(3);
        log("game over: " + g.endReason);
        shot("end");
        tap("משחק חדש");
        frames(3);
        shot("title-after-end");

        // A few states that a normal first session may not reach.
        extras();
    }

    static void extras() throws Exception {
        g = new Game();
        frame();
        tap("גבר");
        for (String k : new String[]{"א", "ב", "י", "ג", "ד", "ו", "ר", "ש", "מ", "ע", "ו", "ן"}) tap(k);
        shot("name-full");
        tap("המשך");
        dialogs("x2");
        walk(City.RESTAURANT, "x2");
        tap("לבקש עבודה");
        dialogs("x2");
        g.energy = 40;
        frame();
        shot("restaurant-low-energy");
        g.minute = 17 * 60;
        g.energy = 90;
        frame();
        shot("restaurant-too-late");
        g.minute = 9 * 60;
        g.money = 900;
        g.food = 10;
        g.water = 10;
        g.energy = 15;
        g.rentDebt = 1000;
        g.day = 9;
        g.meals = 4;
        g.bottles = 4;
        tap("חזרה");
        frames(2);
        shot("map-critical");
        walk(City.HOME, "x2");
        shot("home-debt");
    }

    static void fastForwardUntil(float minute) {
        if (!(g.screen instanceof MapScreen)) return;
        tap("הרץ זמן");
        for (int i = 0; i < 30 * 120 && g.minute < minute; i++) {
            frame();
            if (g.hasDialog()) tap("המשך");
        }
        if (g.screen instanceof MapScreen) {
            for (Btn b : g.screen.btns) if (b.label.equals("עצור")) {
                tap("עצור");
                break;
            }
        }
    }

    /** Plays a shift like a middling first-timer: early taps, late taps, some good ones. */
    static void cookShift(boolean shots) throws Exception {
        java.util.Random r = new java.util.Random(11);
        boolean shotResult = false;
        for (int i = 0; i < 30 * 200 && g.screen instanceof CookGame; i++) {
            frame();
            CookGame cg = (CookGame) g.screen;
            if (cg.btns.size() == 1) {
                if (shots && cg.btns.get(0).label.equals("המשך")) shot("cook-done");
                log("shift ended");
                tap(cg.btns.get(0).label);
                continue;
            }
            if (cg.playing() && (cg.inZone() ? r.nextInt(8) == 0 : r.nextInt(70) == 0)) {
                tapAt(180, lh * 0.7f);
                if (shots && !shotResult) {
                    shot("cook-judged");
                    shotResult = true;
                }
            }
        }
    }

    static void reload() {
        g.onAppPause();
        String s = g.save();
        g = new Game();
        g.load(s);
        frame();
    }

    // ------------------------------------------------------------------ capture and measurement

    /** Delegating renderer that measures what is under every text before drawing it. */
    static final class Probe implements Gfx {
        final AwtGfx a;
        final List<String> texts = new ArrayList<>();
        final String screen;

        Probe(AwtGfx a, String screen) {
            this.a = a;
            this.screen = screen;
        }

        public void fillRect(float x, float y, float w, float h, int c) { a.fillRect(x, y, w, h, c); }
        public void fillRoundRect(float x, float y, float w, float h, float r, int c) { a.fillRoundRect(x, y, w, h, r, c); }
        public void strokeRoundRect(float x, float y, float w, float h, float r, float s, int c) { a.strokeRoundRect(x, y, w, h, r, s, c); }
        public void fillPoly(float[] xy, int c) { a.fillPoly(xy, c); }
        public void fillCircle(float x, float y, float r, int c) { a.fillCircle(x, y, r, c); }
        public void line(float x1, float y1, float x2, float y2, float s, int c) { a.line(x1, y1, x2, y2, s, c); }
        public float textWidth(String s, float size) { return a.textWidth(s, size); }

        public void text(String s, float x, float y, float size, int argb, int align) {
            if (!s.isEmpty() && ((argb >>> 24) > 40)) {
                float w = a.textWidth(s, size);
                float left = align == CENTER ? x - w / 2 : align == RIGHT ? x - w : x;
                float top = y - size * 0.72f;
                // Sample the background just around the glyph box (corners and mid edges).
                int worstBg = 0;
                double worst = 99;
                float[][] pts = {{left - 2, top}, {left + w + 2, top}, {left - 2, y}, {left + w + 2, y},
                        {left + w / 2, top - 2}, {left + w / 2, y + 2}, {left + w / 4, y + 1}, {left + w * 0.75f, top - 1}};
                for (float[] p : pts) {
                    int px = (int) (p[0] * SCALE), py = (int) (p[1] * SCALE);
                    if (px < 0 || py < 0 || px >= a.img.getWidth() || py >= a.img.getHeight()) continue;
                    int bg = a.img.getRGB(px, py) | 0xFF000000;
                    int fg = blend(argb, bg);
                    double cr = contrast(fg, bg);
                    if (cr < worst) {
                        worst = cr;
                        worstBg = bg;
                    }
                }
                int fg = blend(argb, worstBg);
                String line = String.format("%5.2f\t%4.1f\t%06X\t%06X\t%s\t%s", worst, size, fg & 0xFFFFFF,
                        worstBg & 0xFFFFFF, screen, s);
                texts.add(String.format("text %-4.1f fg %06X bg %06X  cr %5.2f %s  at (%.0f,%.0f) w %.0f  \"%s\"",
                        size, fg & 0xFFFFFF, worstBg & 0xFFFFFF, worst, worst < 4.5 ? (worst < 3 ? "FAIL" : "LOW ") : "ok  ",
                        left, y, w, s));
                String key = String.format("%04.1f|%06X|%06X", size, fg & 0xFFFFFF, worstBg & 0xFFFFFF);
                pairs.putIfAbsent(key, line);
            }
            a.text(s, x, y, size, argb, align);
        }
    }

    static int blend(int argb, int bg) {
        int al = argb >>> 24;
        if (al == 255) return argb | 0xFF000000;
        float k = al / 255f;
        int r = (int) (((argb >> 16) & 255) * k + ((bg >> 16) & 255) * (1 - k));
        int gg = (int) (((argb >> 8) & 255) * k + ((bg >> 8) & 255) * (1 - k));
        int b = (int) ((argb & 255) * k + (bg & 255) * (1 - k));
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    static double lum(int c) {
        double[] ch = {((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0};
        for (int i = 0; i < 3; i++) ch[i] = ch[i] <= 0.03928 ? ch[i] / 12.92 : Math.pow((ch[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
    }

    static double contrast(int a, int b) {
        double la = lum(a), lb = lum(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static void shot(String name) throws Exception {
        seq++;
        String id = String.format("%02d-%s", seq, name);
        String dir = out + "/" + (int) lh;
        new File(dir).mkdirs();
        AwtGfx img = new AwtGfx(720, (int) (lh * SCALE), SCALE);
        Probe p = new Probe(img, id);
        g.draw(p);
        img.save(dir + "/" + id + ".png");

        // Touch targets: the screen's buttons plus the dialog button when a dialog is up.
        List<Btn> bs = new ArrayList<>();
        if (g.hasDialog()) {
            if (dialogBtn() != null) bs.add(dialogBtn());
        } else {
            bs.addAll(g.screen.btns);
        }
        BufferedImage ov = new BufferedImage(720, (int) (lh * SCALE), BufferedImage.TYPE_INT_ARGB);
        Graphics2D o = ov.createGraphics();
        o.drawImage(img.img, 0, 0, null);
        o.scale(SCALE, SCALE);
        List<String> lines = new ArrayList<>();
        for (Btn b : bs) {
            float gap = 999;
            for (Btn n : bs) {
                if (n == b) continue;
                float dx = Math.max(0, Math.max(n.x - (b.x + b.w), b.x - (n.x + n.w)));
                float dy = Math.max(0, Math.max(n.y - (b.y + b.h), b.y - (n.y + n.h)));
                gap = Math.min(gap, Math.max(dx, dy));
            }
            boolean small = b.w < 48 || b.h < 48, tight = gap < 8;
            o.setColor(small || tight ? new java.awt.Color(255, 40, 40, 200) : new java.awt.Color(40, 220, 90, 200));
            o.setStroke(new java.awt.BasicStroke(1.5f));
            o.draw(new java.awt.geom.Rectangle2D.Float(b.x, b.y, b.w, b.h));
            String l = String.format("btn \"%s\" x %.0f y %.0f w %.0f h %.0f on=%s primary=%s gap=%.0f%s%s", b.label, b.x, b.y,
                    b.w, b.h, b.on, b.primary, gap, small ? " SMALL" : "", tight ? " TIGHT" : "");
            lines.add(l);
            if (small || tight) {
                String key = (b.label.length() == 1 ? "keyboard key" : b.label) + " " + (int) b.w + "x" + (int) b.h;
                smallTargets.putIfAbsent(key, String.format("%s @%d %s: %s", id, (int) lh, small ? "small" : "", l));
            }
        }
        if (g.screen instanceof MapScreen && !g.hasDialog()) {
            // Buildings are tap targets too (the hit box is the building plus a margin).
            float areaH = lh - Game.HUD_H - MapScreen.BAR_H;
            float s = Math.min(Game.W / City.W, areaH / City.H);
            float ox = (Game.W - City.W * s) / 2, oy = Game.HUD_H + (areaH - City.H * s) / 2;
            for (City.Building b : City.B) {
                float bx = ox + (b.x - 6) * s, by = oy + (b.y - 8) * s, bw = (b.w + 12) * s, bh = (b.h + 16) * s;
                boolean small = bh < 48;
                o.setColor(small ? new java.awt.Color(255, 140, 40, 200) : new java.awt.Color(40, 220, 90, 160));
                o.draw(new java.awt.geom.Rectangle2D.Float(bx, by, bw, bh));
                lines.add(String.format("building \"%s\" hitbox x %.0f y %.0f w %.0f h %.0f (drawn %.0fx%.0f)%s", b.name, bx, by,
                        bw, bh, b.w * s, b.h * s, small ? " SMALL" : ""));
            }
        }
        ImageIO.write(ov, "png", new File(dir + "/" + id + ".targets.png"));
        try (PrintWriter w = new PrintWriter(new FileWriter(dir + "/" + id + ".txt"))) {
            w.println(id + " @" + (int) lh + "  screen=" + g.screen.getClass().getSimpleName() + " dialog=" + g.hasDialog());
            for (String l : lines) w.println(l);
            for (String t : p.texts) w.println(t);
        }
        log("shot " + id);
    }

    /** One image per screen with 760 and 560 side by side, for looking at both in one go. */
    static void pairShots() throws IOException {
        File d760 = new File(out + "/760"), d560 = new File(out + "/560");
        String[] names = d760.list((d, n) -> n.endsWith(".png") && !n.contains(".targets"));
        if (names == null) return;
        java.util.Arrays.sort(names);
        Map<String, File> small = new LinkedHashMap<>();
        String[] sn = d560.list((d, n) -> n.endsWith(".png") && !n.contains(".targets"));
        if (sn != null) for (String n : sn) small.put(n.substring(3), new File(d560, n));
        new File(out + "/pairs").mkdirs();
        for (String n : names) {
            BufferedImage a = ImageIO.read(new File(d760, n));
            File bf = new File(d560, n);
            if (!bf.exists()) bf = small.get(n.substring(3));
            BufferedImage b = bf == null ? null : ImageIO.read(bf);
            int w = 360 + 20 + 360, h = 760;
            BufferedImage pair = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D p = pair.createGraphics();
            p.setColor(java.awt.Color.WHITE);
            p.fillRect(0, 0, w, h);
            p.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            p.drawImage(a, 0, 0, 360, 760, null);
            if (b != null) p.drawImage(b, 380, 0, 360, 560, null);
            ImageIO.write(pair, "png", new File(out + "/pairs/" + n));
        }
    }

    private UxShots() {
    }
}
