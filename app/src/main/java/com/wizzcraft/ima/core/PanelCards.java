package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.List;

/** The plain card: a centred panel with the speaker as its title, the text, and the answers below. */
final class PanelCards implements CardView {
    @Override
    public void draw(Gfx c, Game g, Card d, float h, List<Btn> btns) {
        c.fillRect(0, -g.edgeTop, Game.W, h + g.edgeTop + g.edgeBottom, 0xC0000000);
        float pw = 312, px0 = (Game.W - pw) / 2, tw = pw - 40;
        int n = d.options.length;
        float bh = n >= 3 ? 44 : 48, gap = 8;
        float buttons = n == 0 ? 48 : n * bh + (n - 1) * gap;
        // A long text shrinks until the whole card, buttons included, fits the screen.
        float size = 15, frame = 58 + 14 + buttons + 18;
        List<String> lines = Ui.wrap(c, d.text, tw, size);
        while (size > 11 && frame + lines.size() * size * 1.4f > h - 24) {
            size -= 1;
            lines = Ui.wrap(c, d.text, tw, size);
        }
        // A text far beyond the agreed length is cut rather than allowed to push the buttons off screen.
        int room = Math.max(1, (int) ((h - 16 - frame) / (size * 1.4f)));
        if (lines.size() > room) {
            lines = new ArrayList<>(lines.subList(0, room));
            lines.set(room - 1, lines.get(room - 1) + "...");
        }
        float ph = frame + lines.size() * size * 1.4f;
        float py0 = Math.max(8, (h - ph) / 2);
        c.fillRoundRect(px0, py0, pw, ph, 16, Ui.PANEL);
        c.strokeRoundRect(px0, py0, pw, ph, 16, 1.5f, d.call ? Ui.ACCENT : Ui.PANEL2);
        c.text(d.speaker, Game.W / 2, py0 + 36, Ui.fit(c, d.speaker, tw, 20), Ui.ACCENT, Gfx.CENTER);
        float y = py0 + 53 + size;
        for (String line : lines) {
            c.text(line, Game.W / 2, y, size, Ui.INK, Gfx.CENTER);
            y += size * 1.4f;
        }
        float by = py0 + ph - 18 - buttons;
        if (n == 0) {
            Btn b = new Btn(0, px0 + 20, by, tw, 48, "המשך", true, true);
            btns.add(b);
            b.draw(c);
            return;
        }
        for (int i = 0; i < n; i++) {
            Btn b = new Btn(i, px0 + 20, by + i * (bh + gap), tw, bh, d.options[i], true, false);
            btns.add(b);
            // Answers are drawn here rather than by Btn so that a long one can shrink to fit.
            boolean pickUp = d.call && i == 0;
            c.fillRoundRect(b.x, b.y, b.w, b.h, 10, pickUp ? Ui.GOOD : Ui.PANEL2);
            c.strokeRoundRect(b.x, b.y, b.w, b.h, 10, 1.5f, 0x66FFC857);
            float ls = Ui.fit(c, b.label, b.w - 16, 15);
            c.text(b.label, b.x + b.w / 2, b.y + b.h / 2 + ls * 0.36f, ls, pickUp ? Ui.DARK_INK : Ui.INK, Gfx.CENTER);
        }
    }
}
