package com.wizzcraft.ima.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Renders the Google Play store assets from the game itself (see docs/RELEASE.md):
 * <ul>
 * <li>{@code icon-512.png}: the launcher icon, read from the same vector drawables the app ships,</li>
 * <li>{@code feature-he.png}, {@code feature-en.png}: 1024x500 feature graphics,</li>
 * <li>{@code phone-*.png}: 1080x1920 (9:16) screenshots of real game screens.</li>
 * </ul>
 * Run from the repository root:
 * <pre>
 * java -Djava.awt.headless=true -cp tools/out/classes com.wizzcraft.ima.core.StoreArt tools/out/store [app/src/main/res]
 * </pre>
 * Screenshots are taken at a logical height of 640, so 360x640 scales exactly to 1080x1920; Play
 * refuses screenshots whose long side is more than twice the short side (the game's usual 760 would be).
 * The scenes drive the engine the way a player would; when screens change, adjust {@link #phoneShots}.
 */
public final class StoreArt {
    static final float H = 640, DT = 1f / 30f;
    static String out;
    static String res;
    static Game g;
    static final AwtGfx scratch = new AwtGfx(36, 64, 0.1f);

    public static void main(String[] args) throws Exception {
        out = args.length > 0 ? args[0] : "tools/out/store";
        res = args.length > 1 ? args[1] : "app/src/main/res";
        new File(out).mkdirs();
        icon();
        feature(true);
        feature(false);
        phoneShots();
        System.out.println("STORE ART OK -> " + out);
    }

    // ===================================================================== icon

    /** The adaptive icon as a launcher shows it: background + foreground, cropped to the centre 72x72. */
    static void icon() throws Exception {
        AwtGfx c = new AwtGfx(512, 512, 1f);
        float k = 512f / 72f;
        Vector.draw(c, res + "/drawable/ic_launcher_background.xml", -18, -18, k);
        Vector.draw(c, res + "/drawable/ic_launcher_foreground.xml", -18, -18, k);
        save(c.img, "icon-512.png", true);
    }

    // ===================================================================== feature graphic

    /** 1024x500, no alpha. Key content stays in the centre band, as Play may crop the edges. */
    static void feature(boolean hebrew) throws Exception {
        float W = 512, Hh = 250;
        AwtGfx c = new AwtGfx(1024, 500, 2f);
        c.fillRect(0, 0, W, Hh, 0xFF22325A);
        c.fillPoly(new float[]{0, 0, W, 0, W, 120, 0, 175}, 0xFF2A3D6B);
        // The big city at night, as on the title screen.
        float base = 222;
        float[] ws = {38, 26, 48, 34, 44, 30, 40, 52, 28, 46, 36, 44, 46};
        float[] hs = {70, 104, 58, 120, 86, 112, 66, 96, 128, 74, 100, 62, 90};
        float x = 0;
        for (int i = 0; i < ws.length && x < W; i++) {
            int bc = i % 2 == 0 ? 0xFF1A2440 : 0xFF1E2A4A;
            c.fillRect(x, base - hs[i], ws[i], hs[i] + 40, bc);
            int row = 0;
            for (float wy = base - hs[i] + 10; wy < base - 10; wy += 16, row++) {
                int col = 0;
                for (float wx = x + 6; wx < x + ws[i] - 9; wx += 12, col++) {
                    boolean lit = ((i * 7 + row * 3 + col * 5) % 5) == 0;
                    c.fillRect(wx, wy, 5, 6, lit ? 0x99FFC857 : 0x1FFFFFFF);
                }
            }
            x += ws[i];
        }
        c.fillRect(0, base, W, Hh - base, 0xFF151D30);
        // The ringing phone (the launcher icon's foreground art), on the side the eye reaches last.
        float iconX = hebrew ? 40 : 352, k = 1.95f;
        Vector.draw(c, res + "/drawable/ic_launcher_foreground.xml", iconX / k - 18, 30 / k - 22, k);
        // The two of them, small, on the street.
        float px = hebrew ? 300 : 120;
        Ui.person(c, px, base, 2.6f, true, 0.3f, false, Ui.ORANGE);
        Ui.person(c, px + 44, base, 2.6f, false, 0.3f, false, Ui.BLUE);
        // Title and line.
        float tx = hebrew ? 478 : 34;
        int align = hebrew ? Gfx.RIGHT : Gfx.LEFT;
        String title = hebrew ? "אמא על הקו" : "Mom on the Line";
        String sub = hebrew ? "סימולטור חיים בעיר הגדולה" : "A life sim in the big city";
        float ts = hebrew ? 54 : 38;
        c.text(title, tx + 2, 84 + 3, ts, 0x66000000, align);
        c.text(title, tx, 84, ts, Ui.ACCENT, align);
        c.text(sub, tx, 116, 19, Ui.INK, align);
        save(c.img, hebrew ? "feature-he.png" : "feature-en.png", false);
    }

    // ===================================================================== phone screenshots

    static void phoneShots() throws Exception {
        // 1. The title screen.
        g = new Game();
        g.version = "1.0";
        frames(30);
        shot("phone-1-title");

        // 2. Day one: mom calls as soon as you arrive.
        g.newGame(true, "נועה");
        frames(2);
        shot("phone-2-mom-arrival");
        while (g.hasDialog()) g.back();

        // 3. Walking across the city to the restaurant.
        tapBuilding(City.RESTAURANT);
        frames(110);
        shot("phone-3-city");
        check(goTo(City.RESTAURANT), "arrive at restaurant");

        // 4. Getting hired.
        check(tap("לבקש עבודה"), "ask for job");
        frames(2);
        while (g.hasDialog()) g.back();
        frames(2);
        shot("phone-4-restaurant");

        // 5. The cook minigame, mid-shift.
        check(tap("להתחיל משמרת"), "start shift");
        frames(2);
        check(tap("מתחילים"), "begin cooking");
        // Play three dishes the way a decent cook would: tap once the marker is well inside the green.
        for (int round = 0; round < 3; round++) {
            int inside = 0;
            for (int i = 0; i < 30 * 20 && inside < 3; i++) {
                frames(1);
                CookGame cg = g.screen instanceof CookGame ? (CookGame) g.screen : null;
                inside = cg != null && cg.playing() && cg.inZone() ? inside + 1 : 0;
            }
            g.down(180, H * 0.7f);
            g.up(180, H * 0.7f);
            if (round < 2) frames(5);
        }
        frames(8);
        shot("phone-5-cook");

        // 6. A weekly call from mom, a week into the game, late in the evening.
        g = new Game();
        g.newGame(false, "עומר");
        while (g.hasDialog()) g.back();
        g.day = 6;
        g.money = 1240;
        g.cookHired = false;
        g.minute = 1439;
        g.screen = new MapScreen(g);
        g.advance(2, Game.IDLE);
        g.minute = 21 * 60 + 40;
        g.food = 58;
        g.water = 44;
        g.energy = 37;
        frames(2);
        shot("phone-6-mom-weekly");
    }

    static void frames(int n) {
        for (int i = 0; i < n; i++) {
            g.update(DT, H);
            g.draw(scratch);
        }
    }

    static void shot(String name) throws IOException {
        AwtGfx big = new AwtGfx(1080, 1920, 3f);
        g.draw(big);
        save(big.img, name + ".png", false);
    }

    static boolean tap(String prefix) {
        frames(1);
        for (Btn b : g.screen.btns) {
            if (b.label.startsWith(prefix) && b.on) {
                g.down(b.x + b.w / 2, b.y + b.h / 2);
                g.up(b.x + b.w / 2, b.y + b.h / 2);
                return true;
            }
        }
        return false;
    }

    static void tapBuilding(int id) {
        frames(1);
        City.Building b = City.B[id];
        float areaH = H - Game.HUD_H - MapScreen.BAR_H;
        float s = Math.min(Game.W / City.W, areaH / City.H);
        float ox = (Game.W - City.W * s) / 2, oy = Game.HUD_H + (areaH - City.H * s) / 2;
        g.down(ox + (b.x + b.w / 2) * s, oy + (b.y + b.h / 2) * s);
        g.up(ox + (b.x + b.w / 2) * s, oy + (b.y + b.h / 2) * s);
    }

    static boolean goTo(int id) {
        for (int i = 0; i < 30 * 90; i++) {
            frames(1);
            while (g.hasDialog()) g.back();
            if (g.screen instanceof BuildingScreen && g.at == id) return true;
            if (g.screen instanceof MapScreen && i % 90 == 0) tapBuilding(id);
        }
        return false;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("StoreArt could not: " + what);
    }

    /** Play wants 24-bit PNG (no alpha) for screenshots and the feature graphic, 32-bit for the icon. */
    static void save(BufferedImage img, String name, boolean alpha) throws IOException {
        BufferedImage o = img;
        if (!alpha) {
            o = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = o.createGraphics();
            g2.setColor(new java.awt.Color(Ui.BG));
            g2.fillRect(0, 0, o.getWidth(), o.getHeight());
            g2.drawImage(img, 0, 0, null);
            g2.dispose();
        }
        File f = new File(out, name);
        ImageIO.write(o, "png", f);
        System.out.printf("%-26s %dx%d %s %6d KB%n", name, o.getWidth(), o.getHeight(),
                alpha ? "RGBA" : "RGB ", (f.length() + 1023) / 1024);
    }

    // ===================================================================== vector drawables

    /**
     * Draws the subset of Android vector-drawable XML the launcher icon uses (absolute M/L/H/V/Z
     * paths, fill and round-capped stroke, fillAlpha) through {@link Gfx}, so the store icon is
     * rendered from the very files the app ships. Anything else is rejected loudly.
     */
    static final class Vector {
        static void draw(Gfx c, String file, float dx, float dy, float k) throws Exception {
            Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(file))
                    .getDocumentElement();
            NodeList paths = root.getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++) {
                Element p = (Element) paths.item(i);
                List<float[]> subs = parse(p.getAttribute("android:pathData"), dx, dy, k);
                String fill = p.getAttribute("android:fillColor");
                if (!fill.isEmpty()) {
                    int col = alpha(color(fill), p.getAttribute("android:fillAlpha"));
                    for (float[] s : subs) if (s.length >= 6) c.fillPoly(s, col);
                }
                String stroke = p.getAttribute("android:strokeColor");
                if (!stroke.isEmpty()) {
                    int col = alpha(color(stroke), p.getAttribute("android:strokeAlpha"));
                    float w = Float.parseFloat(p.getAttribute("android:strokeWidth")) * k;
                    for (float[] s : subs) {
                        for (int j = 0; j + 3 < s.length; j += 2) c.line(s[j], s[j + 1], s[j + 2], s[j + 3], w, col);
                    }
                }
            }
        }

        static int color(String s) {
            String h = s.substring(1);
            long v = Long.parseLong(h, 16);
            return (int) (h.length() == 6 ? 0xFF000000L | v : v);
        }

        static int alpha(int argb, String a) {
            if (a == null || a.isEmpty()) return argb;
            int al = Math.round(((argb >>> 24) & 0xFF) * Float.parseFloat(a));
            return (al << 24) | (argb & 0xFFFFFF);
        }

        static List<float[]> parse(String d, float dx, float dy, float k) {
            List<float[]> subs = new ArrayList<>();
            ArrayList<Float> cur = new ArrayList<>();
            String[] tok = d.replaceAll("([MLHVZmlhvz])", " $1 ").replace(',', ' ').trim().split("\\s+");
            char cmd = 'M';
            float x = 0, y = 0;
            for (int i = 0; i < tok.length; i++) {
                String t = tok[i];
                if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                    cmd = t.charAt(0);
                    if (cmd == 'Z') {
                        flush(subs, cur);
                    } else if (Character.isLowerCase(cmd)) {
                        throw new IllegalArgumentException("relative path commands are not supported: " + cmd);
                    }
                    continue;
                }
                switch (cmd) {
                    case 'M':
                        flush(subs, cur);
                        x = Float.parseFloat(t);
                        y = Float.parseFloat(tok[++i]);
                        cmd = 'L';
                        break;
                    case 'L':
                        x = Float.parseFloat(t);
                        y = Float.parseFloat(tok[++i]);
                        break;
                    case 'H':
                        x = Float.parseFloat(t);
                        break;
                    case 'V':
                        y = Float.parseFloat(t);
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported path command: " + cmd);
                }
                cur.add((x + dx) * k);
                cur.add((y + dy) * k);
            }
            flush(subs, cur);
            return subs;
        }

        static void flush(List<float[]> subs, ArrayList<Float> cur) {
            if (cur.isEmpty()) return;
            float[] a = new float[cur.size()];
            for (int i = 0; i < a.length; i++) a[i] = cur.get(i);
            subs.add(a);
            cur.clear();
        }
    }
}
