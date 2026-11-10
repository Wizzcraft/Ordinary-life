package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Cook shift: eight dishes, one per hour. A marker sweeps over the heat bar; tap to take the pan
 * off while the marker is in the green zone. Pay is added dish by dish.
 */
final class CookGame extends Screen {
    static final int ROUNDS = 8, BASE = 30;
    private static final int INTRO = 0, PLAY = 1, SHOW = 2, DONE = 3, PAUSED = 4;
    private static final int BURNT = 0, OK = 1, PERFECT = 2;
    private static final float[] MULT = {0.4f, 1f, 1.3f};
    private static final float BAR_X = 30, BAR_W = 300;

    private int state = INTRO, round;
    private final int[] results = new int[ROUNDS];
    private float pos, dir = 1, zc, zw, speed, timer, t;
    private int pay;
    private boolean tired, sentHome;
    /** While PAUSED: the dish was already judged and only its hour is left to pass. */
    private boolean resumeShow;
    private final Random rnd = new Random();

    CookGame(Game g) {
        super(g);
    }

    boolean playing() {
        return state == PLAY;
    }

    /** True while the marker is comfortably inside the green zone (used by the test bot). */
    boolean inZone() {
        return Math.abs(pos - zc) <= zw * 0.8f;
    }

    private void startRound() {
        // The shift only counts as today's once the first dish is on the fire.
        if (round == 0) g.lastShiftDay = g.day;
        tired = g.energy < 30;
        zw = 0.14f - round * 0.008f;
        zc = 0.2f + rnd.nextFloat() * 0.6f;
        speed = (0.9f + round * 0.12f) * (tired ? 1.35f : 1f);
        pos = rnd.nextFloat();
        dir = rnd.nextBoolean() ? 1 : -1;
        timer = 5f;
        state = PLAY;
    }

    @Override
    void update(float dt) {
        t += dt;
        if (state == PLAY) {
            pos += dir * speed * dt;
            if (pos > 1) {
                pos = 2 - pos;
                dir = -1;
            } else if (pos < 0) {
                pos = -pos;
                dir = 1;
            }
            timer -= dt;
            if (timer <= 0) judge(true);
        } else if (state == SHOW) {
            timer -= dt;
            if (timer <= 0) finishDish();
        }
    }

    /** The hour of the dish that was just judged passes; then the next dish or the end of the shift. */
    private void finishDish() {
        // Out of strength: the manager sends you home instead of letting you drop.
        if (g.energy - 6 < 2) {
            sentHome = true;
            state = DONE;
            g.shiftsDone++;
            return;
        }
        g.advance(60, Game.WORK);
        if (g.screen != this) return;
        round++;
        if (round >= ROUNDS) {
            state = DONE;
            g.shiftsDone++;
        } else {
            startRound();
        }
    }

    private void judge(boolean timeout) {
        float d = Math.abs(pos - zc);
        int res = timeout ? BURNT : d <= zw * 0.35f ? PERFECT : d <= zw ? OK : BURNT;
        results[round] = res;
        int earn = Math.round(BASE * MULT[res]);
        g.money += earn;
        pay += earn;
        state = SHOW;
        timer = 0.7f;
    }

    @Override
    void down(float x, float y) {
        if (state == PLAY) {
            if (y > Game.HUD_H) judge(false);
            return;
        }
        Btn b = hit(x, y);
        if (b == null) return;
        if (state == INTRO) {
            startRound();
        } else if (state == DONE) {
            g.screen = new BuildingScreen(g, City.RESTAURANT);
        } else if (state == PAUSED) {
            if (resumeShow) {
                resumeShow = false;
                finishDish();
            } else {
                startRound();
            }
        }
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        btns.clear();
        float top = Game.HUD_H + 40;
        c.text("משמרת במטבח", Game.W / 2, top, 24, Ui.INK, Gfx.CENTER);

        if (state == INTRO) {
            drawPan(c, h * 0.42f, 0.5f);
            float y = Ui.paragraph(c, "הסמן רץ על פס החום. לוחצים על המסך כשהוא בתוך האזור הירוק, "
                    + "כמה שיותר קרוב לאמצע. 8 מנות, אחת לכל שעה.", Game.W / 2, h * 0.58f, 300, 15, Ui.DIM);
            btns.add(new Btn(0, 40, y + 14, 280, 54, "מתחילים", true, true));
        } else if (state == PAUSED) {
            drawPan(c, h * 0.42f, 0.5f);
            float y = Ui.paragraph(c, "המשמרת בהפסקה. הרווחת עד עכשיו " + Ui.nis(pay) + ".", Game.W / 2,
                    h * 0.58f, 300, 15, Ui.DIM);
            btns.add(new Btn(0, 40, y + 14, 280, 54, "ממשיכים", true, true));
        } else if (state == DONE) {
            int perfect = 0, ok = 0, burnt = 0;
            int cooked = sentHome ? round + 1 : ROUNDS;
            for (int i = 0; i < cooked; i++) {
                if (results[i] == PERFECT) perfect++;
                else if (results[i] == OK) ok++;
                else burnt++;
            }
            float y = h * 0.34f;
            c.text(sentHome ? "נגמר לך הכוח, המנהל שלח אותך הביתה" : "סוף משמרת", Game.W / 2, y,
                    sentHome ? 15 : 20, sentHome ? Ui.BAD : Ui.DIM, Gfx.CENTER);
            c.text(Ui.nis(pay), Game.W / 2, y + 62, 54, Ui.ACCENT, Gfx.CENTER);
            c.text("מושלמות: " + perfect + " · טובות: " + ok + " · שרופות: " + burnt, Game.W / 2, y + 100, 15,
                    Ui.INK, Gfx.CENTER);
            drawDots(c, y + 126, cooked);
            btns.add(new Btn(0, 40, y + 160, 280, 54, "המשך", true, true));
        } else {
            c.text("מנה " + (round + 1) + " מתוך " + ROUNDS, Game.W / 2, top + 26, 14, Ui.DIM, Gfx.CENTER);
            float barY = h * 0.60f;
            drawPan(c, h * 0.42f, pos);

            // Heat bar with the green zone and its perfect core.
            c.fillRoundRect(BAR_X, barY, BAR_W, 34, 8, 0xFF0B0E12);
            c.fillRect(BAR_X + (zc - zw) * BAR_W, barY, zw * 2 * BAR_W, 34, 0xFF2E7D4F);
            c.fillRect(BAR_X + (zc - zw * 0.35f) * BAR_W, barY, zw * 0.7f * BAR_W, 34, Ui.GOOD);
            float mx = BAR_X + pos * BAR_W;
            c.fillRect(mx - 2.5f, barY - 8, 5, 50, Ui.INK);
            c.fillPoly(new float[]{mx - 9, barY - 18, mx + 9, barY - 18, mx, barY - 6}, Ui.INK);

            if (state == SHOW) {
                int r = results[round];
                String msg = r == PERFECT ? "מושלמת!" : r == OK ? "טובה" : "נשרפה...";
                int col = r == PERFECT ? Ui.ACCENT : r == OK ? Ui.GOOD : Ui.BAD;
                c.text(msg, Game.W / 2, barY + 84, 30, col, Gfx.CENTER);
            } else {
                c.text(tired ? g.t("אתה עייף, הכל זז מהר יותר", "את עייפה, הכל זז מהר יותר")
                        : "לוחצים כשהסמן בירוק", Game.W / 2, barY + 78, 14, tired ? Ui.BAD : Ui.DIM, Gfx.CENTER);
            }
            drawDots(c, barY + 116, state == SHOW ? round + 1 : round);
            c.text("הרווחת עד עכשיו " + Ui.nis(pay), Game.W / 2, barY + 150, 13, Ui.DIM, Gfx.CENTER);
        }
        drawButtons(c);
        g.drawHud(c);
    }

    private void drawDots(Gfx c, float y, int done) {
        float x = Game.W / 2 + (ROUNDS - 1) * 11;
        for (int i = 0; i < ROUNDS; i++) {
            int col = i >= done ? 0xFF2C3644 : results[i] == PERFECT ? Ui.ACCENT : results[i] == OK ? Ui.GOOD : Ui.BAD;
            c.fillCircle(x - i * 22, y, 7, col);
        }
    }

    /** A frying pan seen from above; the food changes colour with the heat. */
    private void drawPan(Gfx c, float cy, float heat) {
        float cx = Game.W / 2;
        c.fillPoly(new float[]{cx + 50, cy - 7, cx + 122, cy - 5, cx + 122, cy + 5, cx + 50, cy + 7}, 0xFF3A4350);
        c.fillCircle(cx, cy, 58, 0xFF3A4350);
        c.fillCircle(cx, cy, 49, 0xFF1A1F27);
        float wob = (float) Math.sin(t * 9) * 1.5f;
        int food = heat < 0.33f ? 0xFFE9D8A6 : heat < 0.66f ? 0xFFE0A050 : 0xFFB5651D;
        c.fillPoly(new float[]{cx - 26, cy - 14 + wob, cx - 4, cy - 28, cx + 24, cy - 16 - wob, cx + 30, cy + 8,
                cx + 8, cy + 26 + wob, cx - 22, cy + 18}, food);
    }

    @Override
    boolean back() {
        // Leaving is only possible before the first dish; after that the shift has to be finished.
        if (state == INTRO) g.screen = new BuildingScreen(g, City.RESTAURANT);
        return true;
    }

    @Override
    String kind() {
        return "cook";
    }

    @Override
    void save(StringBuilder sb) {
        StringBuilder res = new StringBuilder();
        for (int r : results) res.append(r);
        Game.put(sb, "cs", state);
        Game.put(sb, "cr", round);
        Game.put(sb, "cres", res);
        Game.put(sb, "cpay", pay);
        Game.put(sb, "csent", sentHome);
        Game.put(sb, "csw", resumeShow);
    }

    /** Leaving the app in the middle of a dish pauses the shift; the dish is redone on return. */
    @Override
    void onAppPause() {
        if (state == PLAY) {
            state = PAUSED;
            resumeShow = false;
        } else if (state == SHOW) {
            state = PAUSED;
            resumeShow = true;
        }
    }

    static Screen restore(Game g, java.util.HashMap<String, String> m) {
        CookGame c = new CookGame(g);
        int st = Game.intOf(m, "cs", INTRO);
        c.round = (int) Ui.clamp(Game.intOf(m, "cr", 0), 0, st == DONE ? ROUNDS : ROUNDS - 1);
        String res = m.getOrDefault("cres", "");
        for (int i = 0; i < ROUNDS && i < res.length(); i++) {
            c.results[i] = (int) Ui.clamp(res.charAt(i) - '0', BURNT, PERFECT);
        }
        c.pay = Math.max(0, Game.intOf(m, "cpay", 0));
        c.sentHome = Boolean.parseBoolean(m.get("csent"));
        c.resumeShow = Boolean.parseBoolean(m.get("csw")) || st == SHOW;
        c.state = (st == INTRO || st == DONE) ? st : PAUSED;
        return c;
    }
}
