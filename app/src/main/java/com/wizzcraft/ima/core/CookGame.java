package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Cook: four rushes. Each rush has a stack of orders and up to three pans that heat at different
 * speeds. Tap an empty pan to start a dish, tap it again while its ring is in the green to serve it.
 * Leave it too long and it burns.
 */
final class CookGame extends ShiftGame {
    private static final int PANS = 3;
    private static final float[] PAN_X = {66, 180, 294};
    private static final float[] PAN_SPEED = {0.34f, 0.44f, 0.56f};
    /** Heat 0..1; serve between GREEN_FROM and 1, perfect inside the core. Past 1 the dish burns. */
    private static final float GREEN_FROM = 0.62f, CORE_FROM = 0.78f, CORE_TO = 0.92f, BURN_AT = 1.12f;

    private int pans, ordersLeft, served, perfect, burnt, total;
    private final float[] heat = new float[PANS];
    private final boolean[] cooking = new boolean[PANS];
    private final float[] flash = new float[PANS];
    private float panY;

    CookGame(Game g, Job job) {
        super(g, job);
    }

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String howTo() {
        return "לוחצים על מחבת ריקה כדי להתחיל מנה, ושוב כשהטבעת בירוק כדי להגיש. "
                + "כל מחבת מתחממת בקצב אחר, ומנה שנשכחת נשרפת.";
    }

    @Override
    void beginRound() {
        pans = Math.min(PANS, 1 + round);
        total = ordersLeft = 3 + round + rank;
        served = perfect = burnt = 0;
        for (int i = 0; i < PANS; i++) {
            cooking[i] = false;
            heat[i] = 0;
            flash[i] = 0;
        }
    }

    private float speed(int i) {
        return PAN_SPEED[i] * (1 + difficulty() * 0.7f);
    }

    @Override
    void updateRound(float dt) {
        for (int i = 0; i < pans; i++) {
            if (flash[i] > 0) flash[i] -= dt;
            if (!cooking[i]) continue;
            heat[i] += speed(i) * dt;
            if (heat[i] >= BURN_AT) {
                cooking[i] = false;
                burnt++;
                g.fx.text(PAN_X[i], panY - 50, "נשרפה", Ui.BAD, 16);
                g.fx.shake(4);
                g.sfx(Sfx.BAD);
            }
        }
        boolean busy = false;
        for (int i = 0; i < pans; i++) busy |= cooking[i];
        if (ordersLeft == 0 && !busy) {
            int done = served + burnt;
            int res = burnt == 0 && perfect * 2 >= done ? PERFECT : served * 2 >= done ? OK : MISS;
            finishRound(res);
        }
    }

    @Override
    void roundDown(float x, float y) {
        for (int i = 0; i < pans; i++) {
            if (Math.abs(x - PAN_X[i]) > 54 || Math.abs(y - panY) > 70) continue;
            if (!cooking[i]) {
                if (ordersLeft > 0) {
                    ordersLeft--;
                    cooking[i] = true;
                    heat[i] = 0;
                    g.sfx(Sfx.SIZZLE);
                }
            } else if (heat[i] >= GREEN_FROM) {
                boolean core = heat[i] >= CORE_FROM && heat[i] <= CORE_TO;
                cooking[i] = false;
                served++;
                if (core) perfect++;
                flash[i] = 0.25f;
                g.fx.text(PAN_X[i], panY - 50, core ? "מושלמת!" : "טובה", core ? Ui.ACCENT : Ui.GOOD, 16);
                if (core) g.fx.burst(PAN_X[i], panY, Ui.ACCENT, 8);
                g.sfx(core ? Sfx.DING : Sfx.COIN);
            } else {
                // Too early: the dish isn't ready, nothing happens but a nudge.
                g.fx.text(PAN_X[i], panY - 50, "עוד לא", Ui.DIM, 14);
                g.sfx(Sfx.TICK);
            }
            return;
        }
    }

    @Override
    void drawRound(Gfx c, float top, float bottom) {
        panY = top + (bottom - top) * 0.52f;
        // Order tickets still waiting.
        c.text("הזמנות בתור", Game.W / 2, top + 16, 12, Ui.DIM, Gfx.CENTER);
        float tx = Game.W / 2 + (total - 1) * 11;
        for (int i = 0; i < total; i++) {
            boolean waiting = i < ordersLeft;
            c.fillRoundRect(tx - i * 22 - 8, top + 26, 16, 22, 3, waiting ? Ui.INK : Ui.PANEL2);
            if (waiting) c.fillRect(tx - i * 22 - 5, top + 32, 10, 2, Ui.DIM);
        }
        for (int i = 0; i < PANS; i++) drawPan(c, i, i < pans);
        c.text("הוגשו " + served + " · נשרפו " + burnt, Game.W / 2, bottom - 8, 13, burnt > 0 ? Ui.BAD : Ui.DIM,
                Gfx.CENTER);
    }

    private void drawPan(Gfx c, int i, boolean active) {
        float cx = PAN_X[i], cy = panY, r = 44;
        int body = active ? 0xFF3A4350 : 0xFF20262F;
        c.fillPoly(new float[]{cx - 6, cy + r - 4, cx + 6, cy + r - 4, cx + 5, cy + r + 44, cx - 5, cy + r + 44}, body);
        c.fillCircle(cx, cy, r, body);
        c.fillCircle(cx, cy, r - 8, active ? 0xFF1A1F27 : 0xFF171B22);
        if (!active) return;
        if (flash[i] > 0) c.fillCircle(cx, cy, r - 8, 0x55FFFFFF);
        if (!cooking[i]) {
            if (ordersLeft > 0) c.text("להתחיל", cx, cy + 5, 13, Ui.DIM, Gfx.CENTER);
            return;
        }
        float hgt = heat[i];
        int food = hgt < GREEN_FROM ? 0xFFE9D8A6 : hgt <= 1 ? 0xFFE0A050 : 0xFF6B3A12;
        float wob = (float) Math.sin(t * 9 + i) * 1.5f;
        c.fillPoly(new float[]{cx - 18, cy - 10 + wob, cx - 2, cy - 20, cx + 17, cy - 11 - wob, cx + 21, cy + 6,
                cx + 5, cy + 18 + wob, cx - 16, cy + 12}, food);
        // Heat ring: a row of segments around the pan that light up as it heats.
        int segs = 20;
        for (int s = 0; s < segs; s++) {
            float f = (s + 0.5f) / segs * BURN_AT;
            if (f > hgt) break;
            double a = -Math.PI / 2 + (s + 0.5) / segs * Math.PI * 2;
            int col = f < GREEN_FROM ? Ui.DIM : f >= CORE_FROM && f <= CORE_TO ? Ui.ACCENT : f <= 1 ? Ui.GOOD : Ui.BAD;
            c.fillCircle(cx + (float) Math.cos(a) * (r + 9), cy + (float) Math.sin(a) * (r + 9), 3.4f, col);
        }
    }

    @Override
    void drawIntroArt(Gfx c, float cx, float cy) {
        c.fillPoly(new float[]{cx + 44, cy - 6, cx + 108, cy - 4, cx + 108, cy + 4, cx + 44, cy + 6}, 0xFF3A4350);
        c.fillCircle(cx, cy, 52, 0xFF3A4350);
        c.fillCircle(cx, cy, 43, 0xFF1A1F27);
        float wob = (float) Math.sin(t * 6) * 1.5f;
        c.fillPoly(new float[]{cx - 22, cy - 12 + wob, cx - 3, cy - 24, cx + 20, cy - 13 - wob, cx + 25, cy + 7,
                cx + 6, cy + 22 + wob, cx - 19, cy + 15}, 0xFFE0A050);
    }

    @Override
    void botPlay(Random r, float skill) {
        for (int i = 0; i < pans; i++) {
            if (!cooking[i]) {
                if (ordersLeft > 0 && r.nextInt(8) == 0) roundDown(PAN_X[i], panY);
            } else {
                boolean careful = r.nextFloat() < skill;
                float at = careful ? CORE_FROM + 0.03f : GREEN_FROM + r.nextFloat() * 0.6f;
                if (heat[i] >= at && (careful || r.nextInt(6) == 0)) roundDown(PAN_X[i], panY);
            }
        }
    }
}
