package com.wizzcraft.ima.core;

/**
 * Character art bench: renders sheets of every look, accessory, mood and portrait, and counts the
 * primitives one small person costs. Usage:
 *   java -Djava.awt.headless=true -cp OUT com.wizzcraft.ima.core.CharShots outDir
 * Prints "CHARS OK"; throws if a small person exceeds the primitive budget.
 */
public final class CharShots {
    static final int DARK = Ui.BG, MID = 0xFF6E7A86, MAP = 0xFF24312A;
    static final float CYCLE = (float) (2 * Math.PI / 10.5);
    static String out;

    /** Counts primitives while forwarding to a real renderer. */
    static final class Counter implements Gfx {
        final Gfx in;
        int n;

        Counter(Gfx in) { this.in = in; }

        public void fillRect(float x, float y, float w, float h, int c) { n++; in.fillRect(x, y, w, h, c); }
        public void fillRoundRect(float x, float y, float w, float h, float r, int c) { n++; in.fillRoundRect(x, y, w, h, r, c); }
        public void strokeRoundRect(float x, float y, float w, float h, float r, float s, int c) { n++; in.strokeRoundRect(x, y, w, h, r, s, c); }
        public void fillPoly(float[] xy, int c) { n++; in.fillPoly(xy, c); }
        public void fillCircle(float x, float y, float r, int c) { n++; in.fillCircle(x, y, r, c); }
        public void line(float a, float b, float d, float e, float w, int c) { n++; in.line(a, b, d, e, w, c); }
        public void text(String s, float x, float y, float size, int c, int al) { in.text(s, x, y, size, c, al); }
        public float textWidth(String s, float size) { return in.textWidth(s, size); }
    }

    public static void main(String[] args) throws Exception {
        out = args.length > 0 ? args[0] : "tools/out/chars";
        budget();
        looksS1();
        looksS2(false);
        looksS2(true);
        looksS4();
        walkCycle();
        accessories();
        moods();
        portraits(64, 3, "portraits64");
        portraits(128, 1.5f, "portraits128");
        street();
        System.out.println("CHARS OK");
    }

    static void label(Gfx c, String s, float x, float y) {
        c.text(s, x, y, 7, 0xFFB8C0CC, Gfx.LEFT);
    }

    static void budget() {
        Counter k = new Counter(new AwtGfx(10, 10, 1));
        int worst = 0, sum = 0, n = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Look.PRESETS.length; i++) {
            int m = 0;
            for (int gnd = 0; gnd < 2; gnd++) {
                for (int mood = 0; mood <= PersonArt.THIRSTY; mood++) {
                    for (int ph = 0; ph < 4; ph++) {
                        k.n = 0;
                        PersonArt.draw(k, 50, 50, 1.4f, Look.PRESETS[i], gnd == 1, ph * 0.13f, ph > 0, 1, mood);
                        m = Math.max(m, k.n);
                        sum += k.n;
                        n++;
                    }
                }
            }
            worst = Math.max(worst, m);
            sb.append(Look.PRESETS[i].id).append('=').append(m).append(' ');
        }
        Look crowd = Look.PRESETS[0].copy();
        int crowdMax = 0;
        for (int i = 0; i < 200; i++) {
            k.n = 0;
            PersonArt.draw(k, 50, 50, 1.4f, Look.crowd(i, crowd), (i & 1) == 0, i * 0.1f, true, 1, PersonArt.NORMAL);
            crowdMax = Math.max(crowdMax, k.n);
        }
        k.n = 0;
        PersonArt.draw(k, 50, 50, 4f, Look.PRESETS[1], true, 0.2f, true, 1, PersonArt.HAPPY);
        int bigCount = k.n;
        int portraitMax = 0;
        for (String w : PersonArt.CAST) {
            k.n = 0;
            PersonArt.portrait(k, w, 50, 50, 64, 1.0f);
            portraitMax = Math.max(portraitMax, k.n);
        }
        System.out.println("primitives at map scale, worst per look: " + sb);
        System.out.printf("small person: worst %d, average %.1f; crowd worst %d; s=4 student %d; portrait worst %d%n",
                worst, sum / (float) n, crowdMax, bigCount, portraitMax);
        if (worst > 25 || crowdMax > 25) throw new AssertionError("small person over 25 primitives");
    }

    static AwtGfx sheet(float w, float h, float scale, int bg) {
        AwtGfx c = new AwtGfx((int) (w * scale), (int) (h * scale), scale);
        c.fillRect(0, 0, w, h, bg);
        return c;
    }

    /** Every look at s = 1, both genders, idle both ways and walk phases; dark top half, mid-tone below. */
    static void looksS1() throws Exception {
        int n = Look.PRESETS.length;
        float cw = 30, rh = 30, rows = 6;
        AwtGfx c = sheet(n * cw, rows * rh * 2 + 10, 3, DARK);
        c.fillRect(0, rows * rh + 10, n * cw, rows * rh, MID);
        for (int half = 0; half < 2; half++) {
            float oy = half * (rows * rh + 10);
            for (int i = 0; i < n; i++) {
                for (int r = 0; r < rows; r++) {
                    boolean fem = r >= 3;
                    int kind = r % 3;
                    float t = kind == 0 ? 0.4f : (kind == 1 ? CYCLE * 0.25f : CYCLE * 0.6f);
                    PersonArt.draw(c, i * cw + cw / 2, oy + r * rh + rh - 3, 1, Look.PRESETS[i], fem, t, kind > 0,
                            kind == 2 ? -1 : 1);
                }
            }
        }
        c.save(out + "/looks_s1.png");
    }

    static void looksS2(boolean fem) throws Exception {
        int n = Look.PRESETS.length;
        float cw = 52, rh = 52;
        AwtGfx c = sheet(cw * 7, rh * n, 2, DARK);
        for (int i = 0; i < n; i++) {
            if (i % 2 == 1) c.fillRect(0, i * rh, cw * 7, rh, MID);
            label(c, Look.PRESETS[i].id, 2, i * rh + 9);
            for (int k = 0; k < 7; k++) {
                boolean walk = k >= 2;
                float t = walk ? CYCLE * (k - 2) / 5f : 0.7f;
                PersonArt.draw(c, k * cw + cw / 2, i * rh + rh - 4, 2, Look.PRESETS[i], fem, t, walk, k == 1 ? -1 : 1);
            }
        }
        c.save(out + "/looks_s2_" + (fem ? "female" : "male") + ".png");
    }

    static void looksS4() throws Exception {
        int n = Look.PRESETS.length;
        float cw = 60, rh = 104;
        AwtGfx c = sheet(cw * 6, rh * 4, 2, DARK);
        for (int i = 0; i < n * 2; i++) {
            int li = i % n;
            boolean fem = i >= n;
            int col = i % 6, row = i / 6;
            if (row % 2 == 1) c.fillRect(col * cw, row * rh, cw, rh, ((col & 1) == 0) ? MID : 0xFF5A6570);
            PersonArt.draw(c, col * cw + cw / 2, row * rh + rh - 6, 4, Look.PRESETS[li], fem, 0.3f + i, false, (i & 1) == 0 ? 1 : -1);
            label(c, Look.PRESETS[li].id, col * cw + 2, row * rh + 9);
        }
        c.save(out + "/looks_s4.png");
    }

    static void walkCycle() throws Exception {
        float cw = 45, rh = 100;
        AwtGfx c = sheet(cw * 8, rh * 4, 2, DARK);
        c.fillRect(0, rh * 2, cw * 8, rh * 2, MID);
        for (int r = 0; r < 4; r++) {
            for (int k = 0; k < 8; k++) {
                float t = CYCLE * k / 8f * (r == 3 ? 10.5f / 6.5f : 1);
                int mood = r == 3 ? PersonArt.TIRED : PersonArt.NORMAL;
                PersonArt.draw(c, k * cw + cw / 2, r * rh + rh - 6, 3.6f, Look.PRESETS[r == 1 ? 4 : (r == 2 ? 5 : 0)],
                        r == 1 || r == 2, t, true, r == 2 ? -1 : 1, mood);
            }
        }
        c.save(out + "/walk_cycle.png");
    }

    static void accessories() throws Exception {
        int n = Look.ACC_NAMES.length;
        float cw = 60, rh = 90;
        AwtGfx c = sheet(cw * 6, rh * 3 + 60, 2, DARK);
        Look l = Look.PRESETS[0].copy();
        for (int a = 1; a < n; a++) {
            l.set(Look.PRESETS[0]);
            l.acc[0] = l.acc[1] = l.acc[2] = l.acc[3] = Look.NONE;
            l.wear(a);
            l.accent = 0xFF5AA9FF;
            int i = a - 1, col = i % 6, row = i / 6;
            if (((col + row) & 1) == 1) c.fillRect(col * cw, row * rh, cw, rh, MID);
            PersonArt.draw(c, col * cw + cw / 2 - 8, row * rh + rh - 6, 3.2f, l, (a & 1) == 0, 0.3f, false, 1);
            PersonArt.draw(c, col * cw + cw - 8, row * rh + rh - 6, 1f, l, (a & 1) == 0, 0.3f, false, -1);
            label(c, a + ":" + Look.ACC_NAMES[a], col * cw + 2, row * rh + 9);
        }
        // Small row: every accessory at s = 1 on the dark background, as on the map.
        for (int a = 1; a < n; a++) {
            l.set(Look.PRESETS[0]);
            l.acc[0] = l.acc[1] = l.acc[2] = l.acc[3] = Look.NONE;
            l.wear(a);
            PersonArt.draw(c, 14 + (a - 1) * 28, rh * 3 + 50, 1.4f, l, false, 0.2f + a * 0.05f, true, 1);
        }
        c.save(out + "/accessories.png");
    }

    static void moods() throws Exception {
        float cw = 72, rh = 110;
        AwtGfx c = sheet(cw * 5, rh * 3, 2, DARK);
        c.fillRect(0, rh * 2, cw * 5, rh, MID);
        Look l = Look.PRESETS[3];
        for (int m = 0; m <= PersonArt.THIRSTY; m++) {
            float x = m * cw;
            label(c, PersonArt.MOOD_NAMES[m], x + 2, 9);
            PersonArt.draw(c, x + 22, rh - 6, 3.2f, l, false, 0.8f, false, 1, m);
            PersonArt.moodBubble(c, x + 22, rh - 6, 1.6f, m, 0.8f);
            PersonArt.draw(c, x + 54, rh - 6, 2f, Look.PRESETS[2], true, 0.25f, true, -1, m);
            for (int k = 0; k < 3; k++) {
                float s = 1 + k * 0.4f;
                PersonArt.draw(c, x + 12 + k * 22, rh * 2 - 8, s, Look.PRESETS[k * 4], k == 1, 0.5f + k, k == 2, 1, m);
                PersonArt.moodBubble(c, x + 12 + k * 22, rh * 2 - 8, s, m, 0.5f + k);
                PersonArt.draw(c, x + 12 + k * 22, rh * 3 - 8, s, Look.PRESETS[k * 4 + 1], k != 1, 0.5f + k, k == 0, -1, m);
                PersonArt.moodBubble(c, x + 12 + k * 22, rh * 3 - 8, s, m, 0.5f + k);
            }
        }
        c.save(out + "/moods.png");
    }

    static void portraits(float size, float scale, String name) throws Exception {
        String[] cast = new String[PersonArt.CAST.length + 1];
        System.arraycopy(PersonArt.CAST, 0, cast, 0, PersonArt.CAST.length);
        cast[cast.length - 1] = "stranger";
        int cols = 7;
        float cell = size * 1.15f;
        int rows = (cast.length + cols - 1) / cols;
        AwtGfx c = sheet(cols * cell, rows * cell * 2 + 8, scale, DARK);
        c.fillRect(0, rows * cell + 8, cols * cell, rows * cell, MID);
        for (int half = 0; half < 2; half++) {
            for (int i = 0; i < cast.length; i++) {
                float x = (i % cols) * cell + cell / 2, y = half * (rows * cell + 8) + (i / cols) * cell + cell / 2;
                PersonArt.portrait(c, cast[i], x, y, size, 1.3f + i * 0.71f + half * 2.1f);
                c.text(cast[i], x, y + cell / 2 - 1, size / 9, 0xFFB8C0CC, Gfx.CENTER);
            }
        }
        c.save(out + "/" + name + ".png");
        // Animation strip for mom and the cop: eight moments, to check blink, talk and yawn.
        AwtGfx a = sheet(8 * cell, cell * 3, scale, DARK);
        String[] who = {"mom", "cop", "gasman"};
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 8; k++) {
                PersonArt.portrait(a, who[r], k * cell + cell / 2, r * cell + cell / 2, size, k * 0.9f + 0.07f);
            }
        }
        a.save(out + "/" + name + "_anim.png");
    }

    /** A strip of street at the map's real scale with the player and ten pedestrians. */
    static void street() throws Exception {
        float w = 360, h = 120;
        AwtGfx c = sheet(w, h, 3, MAP);
        c.fillRect(0, 50, w, 26, 0xFF3B434F);
        for (float x = 6; x < w; x += 22) c.fillRect(x, 62.3f, 9, 1.4f, 0xFF596372);
        Look tmp = Look.PRESETS[0].copy();
        for (int i = 0; i < 10; i++) {
            float x = 20 + i * 34, y = i % 2 == 0 ? 48 : 102;
            c.fillCircle(x, y - 0.5f, 5f, 0x55000000);
            PersonArt.draw(c, x, y, 1.4f, Look.crowd(i, tmp), (i * 7 % 3) == 0, 0.37f * i, i % 3 != 0, i % 4 < 2 ? 1 : -1);
        }
        c.fillCircle(180, 76.5f, 7.7f, 0x55000000);
        Ui.person(c, 180, 78, 1.4f, true, 0.3f, true, Ui.ACCENT);
        PersonArt.moodBubble(c, 180, 78, 1.4f, PersonArt.HUNGRY, 0.3f);
        c.save(out + "/street.png");
    }
}
