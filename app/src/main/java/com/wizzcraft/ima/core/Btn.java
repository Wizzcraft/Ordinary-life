package com.wizzcraft.ima.core;

final class Btn {
    final int id;
    final float x, y, w, h;
    final String label;
    final boolean on, primary;

    Btn(int id, float x, float y, float w, float h, String label, boolean on, boolean primary) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.on = on;
        this.primary = primary;
    }

    boolean hit(float px, float py) {
        return on && Ui.in(px, py, x, y, w, h);
    }

    void draw(Gfx c) {
        int fill = !on ? 0xFF1B2028 : primary ? Ui.ACCENT : Ui.PANEL2;
        int ink = !on ? 0xFF5B6472 : primary ? 0xFF1A1300 : Ui.INK;
        c.fillRoundRect(x, y, w, h, 10, fill);
        c.text(label, x + w / 2, y + h / 2 + 5.5f, 15, ink, Gfx.CENTER);
    }
}
