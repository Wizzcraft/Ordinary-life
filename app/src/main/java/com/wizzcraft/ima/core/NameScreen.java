package com.wizzcraft.ima.core;

/** New game, step two: type your name on an in-game Hebrew keyboard. The game then calls you by it. */
final class NameScreen extends Screen {
    static final int MAX_LEN = 12;
    private static final int OK = 1, DEL = 2, SPACE = 3, KEY = 100;
    /** Rows in the order of a standard Hebrew keyboard, left to right. */
    private static final String[] ROWS = {"קראטוןםפ", "שדגכעיחלךף", "זסבהנמצתץ"};

    private final boolean female;
    String name = "";
    private float t;

    NameScreen(Game g, boolean female) {
        super(g);
        this.female = female;
    }

    @Override
    void update(float dt) {
        t += dt;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        float y = h * 0.14f;
        c.text("איך קוראים לך?", Game.W / 2, y, 28, Ui.ACCENT, Gfx.CENTER);
        c.text("ככה אמא תפנה אליך", Game.W / 2, y + 26, 13, Ui.DIM, Gfx.CENTER);
        Ui.person(c, Game.W / 2, y + 112, 3f, female, t, false, female ? Ui.ORANGE : Ui.BLUE);

        // Text field with a blinking cursor.
        float fy = y + 134;
        c.fillRoundRect(40, fy, 280, 54, 12, Ui.PANEL);
        c.strokeRoundRect(40, fy, 280, 54, 12, 1.5f, Ui.ACCENT);
        boolean cursor = ((int) (t * 2)) % 2 == 0 && name.length() < MAX_LEN;
        if (name.isEmpty()) {
            if (cursor) c.fillRect(Game.W / 2 - 1, fy + 14, 2, 26, Ui.INK);
        } else {
            c.text(name, Game.W / 2, fy + 36, 26, Ui.INK, Gfx.CENTER);
            float tw = c.textWidth(name, 26);
            // Hebrew runs right to left, so the next letter goes on the left end.
            if (cursor) c.fillRect(Game.W / 2 - tw / 2 - 5, fy + 14, 2, 26, Ui.INK);
        }

        btns.clear();
        float kw = 31, kh = 46, gap = 4;
        float ky = fy + 76;
        for (String row : ROWS) {
            float rowW = row.length() * kw + (row.length() - 1) * gap;
            float kx = (Game.W - rowW) / 2;
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(i);
                btns.add(new Btn(KEY + ch, kx + i * (kw + gap), ky, kw, kh, String.valueOf(ch), true, false));
            }
            ky += kh + gap;
        }
        btns.add(new Btn(DEL, 12, ky, 80, kh, "מחק", !name.isEmpty(), false));
        btns.add(new Btn(SPACE, 98, ky, 164, kh, "רווח", true, false));
        btns.add(new Btn(KEY + '\'', 268, ky, 80, kh, "'", true, false));
        btns.add(new Btn(OK, 40, ky + kh + 18, 280, 54, "המשך", !name.trim().isEmpty(), true));
        drawButtons(c);
    }

    @Override
    void down(float x, float y) {
        Btn b = hit(x, y);
        if (b == null) return;
        if (b.id == OK) {
            if (!name.trim().isEmpty()) g.newGame(female, name.trim());
        } else if (b.id == DEL) {
            if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
        } else if (b.id == SPACE) {
            if (!name.isEmpty() && !name.endsWith(" ") && name.length() < MAX_LEN) name += " ";
        } else if (b.id >= KEY) {
            if (name.length() < MAX_LEN) name += (char) (b.id - KEY);
        }
    }

    @Override
    boolean back() {
        g.screen = new TitleScreen(g);
        return true;
    }

    @Override
    String kind() {
        return "name";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "nf", female);
        Game.put(sb, "nn", Game.esc(name));
    }
}
