package com.wizzcraft.ima.core;

import java.lang.reflect.Field;

/**
 * Art bench for the city map: renders it at several times of day, idle and mid-walk, at the two
 * reference screen heights; counts primitives per frame and checks the walking distances the layout
 * has to keep. Run with: java -Djava.awt.headless=true ... ArtShots outDir
 */
public final class ArtShots {
    static final int[] TIMES = {8 * 60, 13 * 60, 18 * 60 + 30, 21 * 60, 2 * 60, 6 * 60 + 15};
    static final float[] HEIGHTS = {760, 560};

    /** Counts drawing calls and passes them on. */
    static final class CountGfx implements Gfx {
        final Gfx in;
        int n, text;

        CountGfx(Gfx in) {
            this.in = in;
        }

        public void fillRect(float x, float y, float w, float h, int c) {
            n++;
            in.fillRect(x, y, w, h, c);
        }

        public void fillRoundRect(float x, float y, float w, float h, float r, int c) {
            n++;
            in.fillRoundRect(x, y, w, h, r, c);
        }

        public void strokeRoundRect(float x, float y, float w, float h, float r, float sw, int c) {
            n++;
            in.strokeRoundRect(x, y, w, h, r, sw, c);
        }

        public void fillPoly(float[] xy, int c) {
            n++;
            in.fillPoly(xy, c);
        }

        public void fillCircle(float cx, float cy, float r, int c) {
            n++;
            in.fillCircle(cx, cy, r, c);
        }

        public void line(float x1, float y1, float x2, float y2, float sw, int c) {
            n++;
            in.line(x1, y1, x2, y2, sw, c);
        }

        public void text(String s, float x, float y, float size, int c, int align) {
            n++;
            text++;
            in.text(s, x, y, size, c, align);
        }

        public float textWidth(String s, float size) {
            return in.textWidth(s, size);
        }
    }

    static Game g;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "tools/out/art";
        distances();
        int worst = 0;
        for (float h : HEIGHTS) {
            for (int m : TIMES) {
                for (int walk = 0; walk < 2; walk++) {
                    setUp(h, m, walk == 1, 17.3f + m * 0.01f);
                    AwtGfx big = new AwtGfx(720, (int) (h * 2), 2f);
                    CountGfx cg = new CountGfx(big);
                    g.draw(cg);
                    worst = Math.max(worst, cg.n);
                    String name = String.format("map-%d-%s-%s", (int) h, Ui.clock(m).replace(":", ""), walk == 1 ? "walk" : "idle");
                    big.save(out + "/" + name + ".png");
                    System.out.printf("%-26s primitives=%4d (text %d)%n", name, cg.n, cg.text);
                }
            }
        }
        // Primitive count over a minute of animation at the busiest hour, to catch peaks.
        setUp(760, 8 * 60, true, 0);
        CountGfx cg = new CountGfx(new AwtGfx(36, 76, 0.1f));
        int peak = 0;
        long total = 0;
        for (int i = 0; i < 60 * 30; i++) {
            setT(i / 30f);
            cg.n = 0;
            g.draw(cg);
            peak = Math.max(peak, cg.n);
            total += cg.n;
        }
        System.out.printf("animated 60 s at 08:00: mean %d, peak %d primitives; still frames worst %d%n",
                total / 1800, peak, worst);
        // Where the primitives go, at the busiest still frame.
        for (int m : new int[]{8 * 60, 18 * 60 + 30}) {
            CityArt art = new CityArt();
            float areaH = 760 - Game.HUD_H - MapScreen.BAR_H, s = Math.min(Game.W / City.W, areaH / City.H);
            art.begin(s, (Game.W - City.W * s) / 2, Game.HUD_H + (areaH - City.H * s) / 2, m, 17.3f + m * 0.01f);
            cg.n = 0;
            art.ground(cg, Game.HUD_H, 760 - MapScreen.BAR_H);
            int ground = cg.n;
            cg.n = 0;
            art.scene(cg, Game.HUD_H, 760 - MapScreen.BAR_H);
            int scene = cg.n;
            java.lang.reflect.Method bm = CityArt.class.getDeclaredMethod("building", Gfx.class, int.class);
            bm.setAccessible(true);
            StringBuilder sb = new StringBuilder();
            int sum = 0;
            for (City.Building b : City.B) {
                cg.n = 0;
                bm.invoke(art, cg, b.id);
                sum += cg.n;
                sb.append(' ').append(b.id).append('=').append(cg.n);
            }
            System.out.printf("  %s: ground %d, scene %d (buildings %d:%s)%n", Ui.clock(m), ground, scene, sum, sb);
        }
        // Frames of a short animation strip for checking motion by eye.
        for (int i = 0; i < 4; i++) {
            setUp(760, 21 * 60, false, 30 + i * 0.25f);
            AwtGfx big = new AwtGfx(720, 1520, 2f);
            g.draw(big);
            big.save(out + "/anim-2100-" + i + ".png");
        }
        System.out.println("ART OK");
    }

    static void distances() {
        City.Building home = City.B[City.HOME];
        for (City.Building b : City.B) {
            if (b.id == City.HOME) continue;
            float[] p = City.path(home.doorX, home.doorY, b.doorX, b.doorY);
            System.out.printf("home -> %-12s %5.0f units%n", b.name, City.length(home.doorX, home.doorY, p, 0));
        }
        for (City.Building b : City.B) {
            if (!City.onRoadH(b.doorY)) throw new IllegalStateException("door off road: " + b.name);
            if (b.doorX < b.x || b.doorX > b.x + b.w) throw new IllegalStateException("door outside: " + b.name);
        }
    }

    static void setUp(float h, int minute, boolean walking, float time) throws Exception {
        g = new Game();
        g.newGame(true, "גיל");
        Field f = Game.class.getDeclaredField("dialogs");
        f.setAccessible(true);
        ((java.util.Collection<?>) f.get(g)).clear();
        g.screen = new MapScreen(g);
        g.update(0, h);
        if (walking) {
            MapScreen ms = (MapScreen) g.screen;
            ms.walkTo(City.RESTAURANT);
            // Walk about a third of the way: 1/30 s frames.
            for (int i = 0; i < 70 && g.screen == ms; i++) {
                g.update(1f / 30, h);
                ((java.util.Collection<?>) f.get(g)).clear();
            }
        }
        g.minute = minute;
        g.food = g.water = g.energy = 70;
        setT(time);
    }

    static void setT(float time) throws Exception {
        Field t = MapScreen.class.getDeclaredField("t");
        t.setAccessible(true);
        t.setFloat(g.screen, time);
    }
}
