package com.wizzcraft.ima.core;

/**
 * Renders the icon set, the building illustrations and the title screen to PNGs for review.
 * Usage: IconShots outDir
 */
final class IconShots {
    private IconShots() {
    }

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "tools/out/icons";
        String[] names = Icons.NAMES;
        int perSheet = 20, cols = 4;
        float cw = 180, ch = 96;
        for (int sheet = 0; sheet * perSheet < names.length + 2; sheet++) {
            int from = sheet * perSheet, to = Math.min(names.length + 2, from + perSheet);
            int rows = (to - from + cols - 1) / cols;
            AwtGfx g = new AwtGfx((int) (cw * cols * 2), (int) (ch * rows * 2), 2f);
            g.fillRect(0, 0, cw * cols, ch * rows, Ui.BG);
            for (int i = from; i < to; i++) {
                // two extra cells test the fallbacks: an unknown name and null
                String n = i < names.length ? names[i] : (i == names.length ? "no.such.icon" : null);
                int k = i - from;
                float x = (k % cols) * cw, y = (k / cols) * ch;
                g.fillRoundRect(x + 4, y + 4, cw - 8, ch - 8, 8, Ui.PANEL);
                Icons.draw(g, n, x + 22, y + 34, 20, 0);
                Icons.draw(g, n, x + 52, y + 34, 32, 0);
                Icons.draw(g, n, x + 108, y + 40, 64, 0);
                Icons.draw(g, n, x + 156, y + 34, 24, Ui.DIM);
                Icons.draw(g, n, x + 156, y + 62, 20, 0);
                g.text(n == null ? "null" : n, x + 12, y + ch - 12, 11, Ui.INK, Gfx.LEFT);
            }
            g.save(out + "/icons-" + (sheet + 1) + ".png");
        }

        // A realistic list: icons at 20 on the dark background next to Hebrew labels.
        AwtGfx list = new AwtGfx(720, 520, 2f);
        list.fillRect(0, 0, 360, 260, Ui.BG);
        String[] row = {"meal", "water", "coin", "energy", "food", "heart", "fitness", "charisma", "education",
                "rent", "heat", "siren", "gem", "key", "bus", "taxi", "job.cook", "job.cashier", "chip", "package"};
        for (int i = 0; i < row.length; i++) {
            float x = 180 * (i % 2), y = 26 * (i / 2) + 4;
            list.fillRoundRect(x + 6, y, 168, 22, 6, Ui.PANEL);
            Icons.draw(list, row[i], x + 160, y + 11, 20, 0);
            list.text("פריט לדוגמה", x + 146, y + 16, 13, Ui.INK, Gfx.RIGHT);
        }
        list.save(out + "/list-20.png");

        for (float size : new float[]{70, 140}) {
            float bw = size * 1.5f, bh = size * 1.3f;
            AwtGfx g = new AwtGfx((int) (bw * 3 * 2), (int) (bh * 3 * 2), 2f);
            g.fillRect(0, 0, bw * 3, bh * 3, Ui.BG);
            for (int id = 0; id < 9; id++) {
                float x = (id % 3) * bw, y = (id / 3) * bh;
                g.fillRoundRect(x + 4, y + 4, bw - 8, bh - 8, 10, Ui.PANEL);
                Icons.building(g, id, x + bw / 2, y + bh / 2 - size * 0.04f, size, 1.3f + id * 0.7f);
                g.text(City.B[id].name, x + bw / 2, y + bh - 8, size < 100 ? 10 : 13, Ui.DIM, Gfx.CENTER);
            }
            g.save(out + "/buildings-" + (int) size + ".png");
        }
        // animation frames of each building at 70 to check the idle motion
        AwtGfx anim = new AwtGfx(4 * 110 * 2, 9 * 84 * 2, 2f);
        anim.fillRect(0, 0, 440, 9 * 84, Ui.BG);
        for (int id = 0; id < 9; id++) {
            for (int f = 0; f < 4; f++) Icons.building(anim, id, 55 + f * 110, 42 + id * 84, 70, 0.4f + f * 0.9f);
        }
        anim.save(out + "/buildings-anim.png");

        for (int h : new int[]{760, 560}) {
            for (float time : new float[]{0.3f, 2.1f, 7.7f}) {
                Game game = new Game();
                game.update(0, h);
                TitleScreen ts = new TitleScreen(game);
                game.screen = ts;
                for (float tt = 0; tt < time; tt += 0.1f) ts.update(0.1f);
                AwtGfx g = new AwtGfx(720, h * 2, 2f);
                ts.draw(g);
                g.save(out + "/title-" + h + "-t" + time + ".png");
            }
        }
        System.out.println("ICONS OK " + names.length + " icons");
    }
}
