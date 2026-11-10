package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Cook shift: four dinner rushes on a small gas stove. Order tickets drop onto the rail; tap an
 * empty pan to start the selected ticket (the front one unless you tap another), tap the pan again
 * when the doneness gauge is in the gold to send the plate across the pass.
 *
 * The decision: three dishes with different cook times (the ticket shows them in flame pips), and red
 * RUSH tickets that pay double but cook fast, have a narrow gold, run out of patience sooner and
 * cost double when they burn. With three pans and more tickets than hands, the player chooses what
 * to start first.
 */
final class CookMini extends Mini {
    private static final int PANS = 3, RAIL_MAX = 4;
    private static final float[] PAN_X = {62, 180, 298};
    /** Seconds from raw to dead centre at difficulty 0: egg, schnitzel, steak. */
    private static final float[] COOK = {2.3f, 3.1f, 4.2f};
    private static final String[] NAME = {"ביצה", "שניצל", "סטייק"};
    /** Heat 1.0 is the dead centre. Below RAW_UNTIL the dish is still raw; at BURN_AT it burns. */
    private static final float RAW_UNTIL = 0.8f, BURN_AT = 1.22f;
    private static final int[] DISHES = {5, 6, 6, 7};
    private static final int[] RUSHES = {0, 1, 1, 2};

    private static final class Ticket {
        int kind;
        boolean rush;
        float left, total, x, dy;
    }

    private static final class Pan {
        boolean unlocked, cooking;
        int kind;
        boolean rush;
        float heat, rate, pw, grace, flame, hop, flash, ruin, badFlash;
    }

    private final Pan[] pan = new Pan[PANS];
    private final ArrayList<Ticket> rail = new ArrayList<>();
    private final ArrayList<float[]> ghosts = new ArrayList<>();
    private final float[] target = new float[PANS];
    /** Plates leaving across the pass: x, y, startX, startY, age, kind, doneness, alive. */
    private final float[][] plates = new float[8][8];
    /** Scratch outline for blobs: always exactly 10 points, because Gfx reads the whole array. */
    private final float[] tri = new float[6];
    private final float[] sc = new float[20];

    private float diff, time, outro;
    private int dishes, issued, served, burnt, gone, streak, sel;
    private float points, maxPoints, arrIn;
    private int[] planKind = new int[8];
    private boolean[] planRush = new boolean[8];

    // Layout, refreshed by draw(); begin() sets a sane default so bots and touches work before the first frame.
    private float railTop, railH = 80, stoveTop, stoveBot, passTop, passH = 52, frontH, panY, panR = 38, gaugeY;

    CookMini() {
        for (int i = 0; i < PANS; i++) pan[i] = new Pan();
    }

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("טבח", "טבחית");
    }

    @Override
    String howTo() {
        return "לוחצים על מחבת ריקה כדי להתחיל את ההזמנה, ושוב כשהמחוון בזהב כדי להגיש. "
                + "כרטיס אדום שווה כפול, אבל נשרף מהר.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = Ui.clamp(difficulty, 0, 1);
        int rd = Math.max(0, Math.min(3, round));
        dishes = DISHES[rd] + (diff > 0.8f ? 1 : 0);
        if (planKind.length < dishes) {
            planKind = new int[dishes];
            planRush = new boolean[dishes];
        }
        Random r = h.rnd();
        int kinds = rd == 0 ? 2 : 3;
        int off = r.nextInt(3);
        maxPoints = 0;
        for (int i = 0; i < dishes; i++) {
            planKind[i] = (i + off) % kinds;
            if (kinds == 3 && r.nextInt(3) == 0) planKind[i] = r.nextInt(3);
            planRush[i] = false;
        }
        for (int k = 0; k < RUSHES[rd] + (diff > 0.8f ? 1 : 0); k++) planRush[1 + r.nextInt(dishes - 1)] = true;
        for (int i = 0; i < dishes; i++) maxPoints += planRush[i] ? 4 : 2;
        issued = served = burnt = gone = streak = sel = 0;
        points = 0;
        arrIn = 0.25f;
        outro = 0;
        rail.clear();
        ghosts.clear();
        for (float[] p : plates) p[7] = 0;
        for (int i = 0; i < PANS; i++) {
            Pan p = pan[i];
            p.unlocked = i < (rd == 0 ? 2 : 3);
            p.cooking = false;
            p.heat = p.grace = p.flame = p.hop = p.flash = p.ruin = p.badFlash = 0;
            target[i] = Float.NaN;
        }
        layout(Game.HUD_H + 74, h.screenH() - 96);
    }

    private void layout(float top, float bottom) {
        float hgt = bottom - top;
        railTop = top;
        railH = 80;
        passH = 52;
        passTop = bottom - passH;
        stoveBot = passTop - 4;
        panR = hgt < 360 ? 33 : 40;
        frontH = hgt < 420 ? 0 : 50;
        float surface = 2 * panR + 86;
        // Spare height becomes wall (shelf, utensils) above the stove, never empty stove.
        stoveTop = Math.max(top + railH + 4, stoveBot - surface - frontH);
        panY = stoveTop + 26 + panR;
        gaugeY = panY + panR + 16;
    }

    private float slotX(int k) {
        return 312 - k * 88;
    }

    // ------------------------------------------------------------------------------------------ update

    @Override
    int update(float dt) {
        time += dt;
        spawnTickets(dt);
        tickets(dt);
        for (int i = 0; i < PANS; i++) updatePan(i, dt);
        for (float[] p : plates) {
            if (p[7] == 0) continue;
            p[4] += dt;
            float t = p[4];
            if (t < 0.3f) {
                float e = t / 0.3f;
                p[0] = p[2];
                p[1] = p[3] + (passTop + 18 - p[3]) * e * e - (float) Math.sin(e * Math.PI) * 26;
            } else {
                p[1] = passTop + 18;
                p[0] = p[2] + (t - 0.3f) * (t - 0.3f) * 150 + (t - 0.3f) * 90;
            }
            if (p[0] > W + 30) p[7] = 0;
        }
        for (int i = ghosts.size() - 1; i >= 0; i--) {
            float[] g = ghosts.get(i);
            g[3] += dt;
            g[1] += dt * dt * 0 + g[3] * 160 * dt;
            if (g[3] > 0.7f) ghosts.remove(i);
        }

        boolean cooking = false;
        for (Pan p : pan) cooking |= p.cooking;
        if (issued < dishes || !rail.isEmpty() || cooking) return RUNNING;
        outro += dt;
        if (outro < 0.4f) return RUNNING;
        float ratio = maxPoints <= 0 ? 0 : Math.max(0, points) / maxPoints;
        return ratio >= 0.8f ? PERFECT : ratio >= 0.4f ? OK : MISS;
    }

    private void spawnTickets(float dt) {
        if (issued >= dishes) return;
        if (rail.size() >= RAIL_MAX) return;
        arrIn -= dt;
        if (arrIn > 0) return;
        Ticket t = new Ticket();
        t.kind = planKind[issued];
        t.rush = planRush[issued];
        issued++;
        t.total = t.left = t.rush ? 4.2f - 0.7f * diff : 5.0f - 0.9f * diff;
        t.x = slotX(rail.size());
        t.dy = -56;
        rail.add(t);
        arrIn = 0.85f - 0.2f * diff;
        h.sfx(t.rush ? Sfx.BEEP : Sfx.DING);
    }

    private void tickets(float dt) {
        for (int k = rail.size() - 1; k >= 0; k--) {
            Ticket t = rail.get(k);
            t.left -= dt;
            t.dy *= Math.max(0, 1 - dt * 12);
            t.x += (slotX(k) - t.x) * Math.min(1, dt * 14);
            if (t.left <= 0) {
                rail.remove(k);
                gone++;
                streak = 0;
                if (t.rush) points -= 1;
                ghosts.add(new float[]{t.x, railTop + 10, t.kind, 0, t.rush ? 1 : 0});
                h.text(t.x, railTop + 96, "עזבו!", Ui.BAD);
                h.sfx(Sfx.WHOOSH);
                h.shake(3);
            }
        }
        if (sel >= rail.size()) sel = 0;
    }

    private void updatePan(int i, float dt) {
        Pan p = pan[i];
        p.hop = Math.max(0, p.hop - dt * 5);
        p.flash = Math.max(0, p.flash - dt * 4);
        p.badFlash = Math.max(0, p.badFlash - dt * 3);
        if (p.grace > 0) p.grace -= dt;
        float goal = p.cooking ? 0.4f + 0.6f * Math.min(p.heat, 1.1f) / 1.1f : p.ruin > 0 ? 0.9f * Math.min(1, p.ruin) : 0;
        p.flame += (goal - p.flame) * Math.min(1, dt * 9);
        if (p.ruin > 0) {
            p.ruin -= dt;
            return;
        }
        if (!p.cooking) return;
        p.heat += p.rate * dt;
        if (p.heat >= BURN_AT) {
            p.cooking = false;
            p.ruin = 1.3f;
            burnt++;
            streak = 0;
            points -= p.rush ? 2 : 1;
            h.text(PAN_X[i], panY - panR - 30, "נשרף!", Ui.BAD);
            h.burst(PAN_X[i], panY, 0xFF50565E, 10);
            h.shake(6);
            h.sfx(Sfx.BAD);
        }
    }

    // ------------------------------------------------------------------------------------------ touch

    @Override
    void down(float x, float y) {
        if (y < railTop + railH && y > railTop - 10) {
            for (int k = 0; k < rail.size(); k++) {
                if (Math.abs(x - slotX(k)) <= 44) {
                    sel = k;
                    h.sfx(Sfx.TAP);
                    return;
                }
            }
            return;
        }
        for (int i = 0; i < PANS; i++) {
            if (Math.abs(x - PAN_X[i]) > 56 || y < panY - panR - 24 || y > gaugeY + 30) continue;
            tapPan(i);
            return;
        }
    }

    private void tapPan(int i) {
        Pan p = pan[i];
        if (!p.unlocked || p.ruin > 0) return;
        float cx = PAN_X[i];
        if (!p.cooking) {
            if (rail.isEmpty()) {
                h.text(cx, panY - panR - 20, "אין הזמנה", Ui.DIM);
                h.sfx(Sfx.TICK);
                return;
            }
            Ticket t = rail.remove(Math.min(sel, rail.size() - 1));
            sel = 0;
            p.cooking = true;
            p.kind = t.kind;
            p.rush = t.rush;
            p.heat = 0;
            p.grace = 0.3f;
            p.rate = (1f / COOK[t.kind]) * (1 + 0.45f * diff) * (t.rush ? 1.5f : 1f);
            p.pw = (0.095f - 0.02f * diff) * (t.rush ? 0.85f : 1f);
            p.hop = 1;
            h.sfx(Sfx.SIZZLE);
            h.burst(cx, panY + panR * 0.6f, 0xFFFFA040, 6);
            h.text(cx, panY - panR - 22, NAME[t.kind], Ui.INK);
            return;
        }
        if (p.grace > 0) return;
        serve(i);
    }

    private void serve(int i) {
        Pan p = pan[i];
        float cx = PAN_X[i];
        float mult = p.rush ? 2 : 1;
        p.cooking = false;
        p.hop = 1;
        if (p.heat < RAW_UNTIL) {
            // Tossed too early: the dish is wasted, nothing else is lost.
            streak = 0;
            p.badFlash = 1;
            h.text(cx, panY - panR - 30, "חי!", Ui.ORANGE);
            h.sfx(Sfx.BAD);
            h.shake(3);
            return;
        }
        boolean perfect = Math.abs(p.heat - 1) <= p.pw;
        served++;
        spawnPlate(cx, panY, p.kind, p.heat);
        p.flash = 1;
        if (perfect) {
            streak++;
            float add = 2 * mult + (streak >= 3 ? 1 : 0);
            points += add;
            h.text(cx, panY - panR - 30, p.rush ? "מושלם כפול!" : "מושלם!", Ui.ACCENT);
            if (streak >= 2) h.text(cx, panY - panR - 56, "רצף x" + streak, 0xFFFFE9A0);
            h.burst(cx, panY, Ui.ACCENT, 12);
            h.sfx(Sfx.PERFECT);
        } else {
            streak = 0;
            points += mult;
            h.text(cx, panY - panR - 30, p.rush ? "טוב x2" : "טוב", Ui.GOOD);
            h.burst(cx, panY, Ui.GOOD, 5);
            h.sfx(Sfx.COIN);
        }
    }

    private void spawnPlate(float x, float y, int kind, float heat) {
        for (float[] p : plates) {
            if (p[7] != 0) continue;
            p[0] = p[2] = x;
            p[1] = p[3] = y;
            p[4] = 0;
            p[5] = kind;
            p[6] = heat;
            p[7] = 1;
            return;
        }
    }

    @Override
    void pause() {
        // An interrupted round is replayed from begin(); only the transient touch state needs clearing.
        for (int i = 0; i < PANS; i++) target[i] = Float.NaN;
    }

    // ------------------------------------------------------------------------------------------ drawing

    private static int rgba(int rgb, float a) {
        int al = (int) (Ui.clamp(a, 0, 1) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = Ui.clamp(t, 0, 1);
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            r |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return r;
    }

    /** Colour along a ramp of stops at heats {0, a, b, c, d}. */
    private static int ramp(int[] cols, float[] at, float v) {
        if (v <= at[0]) return cols[0];
        for (int i = 1; i < cols.length; i++) {
            if (v <= at[i]) return mix(cols[i - 1], cols[i], (v - at[i - 1]) / (at[i] - at[i - 1]));
        }
        return cols[cols.length - 1];
    }

    private static final float[] RAMP_AT = {0, 0.6f, 1.0f, 1.12f, 1.22f};
    private static final int[] EGG_WHITE = {0xCCDDE6EA, 0xFFF7F3E8, 0xFFEBC76A, 0xFF94601E, 0xFF2A1B12};
    private static final int[] EGG_YOLK = {0xFFFFC21F, 0xFFFFB81A, 0xFFE9970F, 0xFF8A5214, 0xFF2A1B12};
    private static final int[] SCHNITZEL = {0xFFEBC3AB, 0xFFE8B35C, 0xFFD9923A, 0xFF8A4A1A, 0xFF25160D};
    private static final int[] STEAK = {0xFFC94B4E, 0xFF9C4A35, 0xFF6A3322, 0xFF4B2417, 0xFF1F120C};

    private float[] blob(float cx, float cy, float rx, float ry, int n, float seed, float wob) {
        float[] p = sc;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float k = 1 + 0.12f * (float) Math.sin(a * 3 + seed) + 0.07f * (float) Math.cos(a * 2 + seed * 2)
                    + wob * (float) Math.sin(time * 9 + i * 1.7 + seed);
            p[i * 2] = cx + (float) Math.cos(a) * rx * k;
            p[i * 2 + 1] = cy + (float) Math.sin(a) * ry * k;
        }
        return p;
    }

    /** One dish, with the colour of doneness {@code heat}. Used in the pan, on tickets and on plates. */
    private void food(Gfx c, int kind, float cx, float cy, float s, float heat, float wob) {
        if (kind == 0) {
            c.fillPoly(blob(cx, cy, 21 * s, 18 * s, 10, 1.3f, wob), ramp(EGG_WHITE, RAMP_AT, heat));
            c.fillPoly(blob(cx, cy, 15 * s, 13 * s, 10, 2.1f, wob), ramp(EGG_WHITE, RAMP_AT, heat - 0.28f));
            c.fillCircle(cx - 2 * s, cy - 1 * s, 8.5f * s, ramp(EGG_YOLK, RAMP_AT, heat));
            if (heat < 1.0f) c.fillCircle(cx - 5 * s, cy - 4 * s, 2.4f * s, 0x99FFFFFF);
        } else if (kind == 1) {
            c.fillPoly(blob(cx, cy, 24 * s, 17 * s, 10, 0.4f, wob), ramp(SCHNITZEL, RAMP_AT, heat));
            int crumb = mix(ramp(SCHNITZEL, RAMP_AT, heat), 0xFFFFE6B0, 0.35f);
            for (int i = 0; i < 6; i++) {
                float a = i * 1.9f + 0.5f;
                c.fillCircle(cx + (float) Math.cos(a) * 13 * s * (0.5f + 0.1f * i), cy + (float) Math.sin(a) * 8 * s,
                        1.9f * s, crumb);
            }
        } else {
            if (heat < 1.1f) c.fillPoly(blob(cx, cy, 24 * s, 18 * s, 10, 3.2f, wob), 0xFFF0D9B5);
            c.fillPoly(blob(cx, cy, 21 * s, 15.5f * s, 10, 3.2f, wob), ramp(STEAK, RAMP_AT, heat));
            int mark = rgba(0x1A0E08, Ui.clamp((heat - 0.25f) * 1.2f, 0, 0.7f));
            for (int i = -1; i <= 1; i++) {
                c.line(cx - 9 * s + i * 8 * s, cy - 8 * s, cx - 1 * s + i * 8 * s, cy + 8 * s, 2.2f * s, mark);
            }
        }
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawWall(c);
        float heatSum = 0;
        for (Pan p : pan) heatSum += p.flame;
        drawStove(c, heatSum);
        for (int i = 0; i < PANS; i++) drawPan(c, i);
        for (int i = 0; i < PANS; i++) drawVapour(c, i);
        drawPass(c);
        drawRail(c);
    }

    private void drawWall(Gfx c) {
        float bottomWall = stoveBot;
        c.fillRect(0, railTop, W, bottomWall - railTop, 0xFF18222C);
        for (int x = 0; x <= 360; x += 40) c.fillRect(x - 0.5f, railTop, 1.2f, bottomWall - railTop, 0xFF1F2B37);
        for (float y = railTop + 40; y < bottomWall; y += 40) c.fillRect(0, y, W, 1.2f, 0xFF1F2B37);
        // The rail the tickets hang from: a steel bar with a warm lamp glow underneath.
        c.fillRect(0, railTop + 4, W, 4, 0xFF9AA6B2);
        c.fillRect(0, railTop + 8, W, 2, 0xFF4A5560);
        c.fillRect(0, railTop + railH - 2, W, 2, 0x33000000);
        float wy = railTop + railH, wallH = stoveTop - wy;
        if (wallH >= 70) {
            // Spice shelf and hanging utensils fill the wall when the screen is tall.
            float sy = wy + 36;
            c.fillRect(0, sy, W, 4, 0xFF5B4630);
            c.fillRect(0, sy + 4, W, 2, 0x44000000);
            int[] lid = {0xFFD94A3A, 0xFFF2C14E, 0xFF4CC28A, 0xFF5CB8FF, 0xFFE8702A, 0xFFB07CE0};
            for (int k = 0; k < 6; k++) {
                float jx = 30 + k * 56, jh = 20 + (k * 7 % 3) * 4;
                c.fillRoundRect(jx - 11, sy - jh, 22, jh, 4, 0xFF3C4A58);
                c.fillRoundRect(jx - 9, sy - jh + 5, 18, jh - 8, 3, rgba(lid[k] & 0xFFFFFF, 0.55f));
                c.fillRoundRect(jx - 11, sy - jh - 4, 22, 6, 2, lid[k]);
            }
            if (wallH >= 110) {
                c.fillRect(40, sy + 22, W - 80, 3, 0xFF8D99A6);
                float ux = 70;
                c.line(ux, sy + 24, ux, sy + 52, 2.5f, 0xFF8D99A6);
                c.fillCircle(ux, sy + 58, 8, 0xFF8D99A6);
                ux = 180;
                c.line(ux, sy + 24, ux, sy + 46, 2.5f, 0xFF8D99A6);
                c.fillPoly(new float[]{ux - 9, sy + 46, ux + 9, sy + 46, ux + 7, sy + 64, ux - 7, sy + 64}, 0xFF8D99A6);
                ux = 290;
                c.line(ux, sy + 24, ux, sy + 56, 2.5f, 0xFF8D99A6);
                c.fillRoundRect(ux - 8, sy + 54, 16, 7, 3, 0xFF8D99A6);
            }
        }
    }

    private void drawStove(Gfx c, float heatSum) {
        float y0 = stoveTop, hh = stoveBot - y0;
        c.fillRoundRect(6, y0, W - 12, hh, 12, 0xFF2A3139);
        c.fillRoundRect(6, y0, W - 12, 4, 2, 0xFF47515C);
        float surf = hh - frontH;
        c.fillRoundRect(12, y0 + 8, W - 24, surf - 12, 10, 0xFF1A2027);
        for (int i = 0; i < PANS; i++) {
            float cx = PAN_X[i], cy = panY, r = panR;
            Pan p = pan[i];
            if (p.flame > 0.02f) {
                c.fillCircle(cx, cy, r + 26, rgba(0xFF8A30, 0.08f * p.flame));
                c.fillCircle(cx, cy, r + 18, rgba(0xFF8A30, 0.10f * p.flame));
            }
            c.fillCircle(cx, cy, r + 13, 0xFF0F1318);
            c.fillCircle(cx, cy, r + 8, 0xFF161B21);
            for (int a = 0; a < 3; a++) {
                double ang = a * Math.PI / 3 + 0.3;
                float dx = (float) Math.cos(ang) * (r + 12), dy = (float) Math.sin(ang) * (r + 12);
                c.line(cx - dx, cy - dy, cx + dx, cy + dy, 3, 0xFF2D353E);
            }
        }
        if (frontH <= 0) return;
        // Front panel: a knob per burner that turns and glows with the heat, and the oven door.
        float fy = y0 + surf;
        c.fillRect(12, fy, W - 24, 2, 0xFF47515C);
        c.fillRoundRect(40, fy + 12, W - 80, frontH - 20, 6, 0xFF12171C);
        float glow = Ui.clamp(heatSum / 2, 0, 1);
        c.fillRoundRect(46, fy + 16, W - 92, frontH - 28, 4, rgba(0xFF7A20, 0.10f + 0.25f * glow));
        c.fillRoundRect(60, fy + 14, W - 120, 3, 1.5f, 0xFF7C8793);
        for (int i = 0; i < PANS; i++) {
            Pan p = pan[i];
            float kx = PAN_X[i], ky = fy + frontH / 2 + 1;
            c.fillCircle(kx, ky, 13, 0xFF0C0F12);
            c.fillCircle(kx, ky, 11, p.unlocked ? 0xFF59636F : 0xFF3A424B);
            double ang = -Math.PI * 0.85 + Math.PI * 1.7 * Ui.clamp(p.flame, 0, 1);
            c.line(kx, ky, kx + (float) Math.cos(ang) * 10, ky + (float) Math.sin(ang) * 10, 3, p.flame > 0.05f ? 0xFFFFB347 : 0xFFC7D0D9);
        }
    }

    private void drawFlames(Gfx c, int i) {
        Pan p = pan[i];
        float lvl = p.flame;
        if (lvl < 0.03f) return;
        float cx = PAN_X[i], cy = panY, rb = panR + 4;
        float hv = p.cooking ? p.heat : 1.2f;
        int outer = ramp(new int[]{0xFFFF9A33, 0xFFFF8A2A, 0xFFFF5A2A, 0xFFFF2E22}, new float[]{0, 0.7f, 1.05f, 1.22f}, hv);
        int inner = hv < 0.7f ? 0xFF63BDFF : 0xFFFFE070;
        int n = 12;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n + 0.1;
            float fl = 0.62f + 0.38f * (float) Math.sin(time * 15 + k * 2.9 + i * 1.9) * (float) Math.sin(time * 9 + k * 1.3);
            float len = (3 + 10 * lvl) * (0.55f + (k % 3) * 0.28f) * fl * 1.6f;
            tongue(c, cx, cy, rb - 3, (float) a, 0.19f, rb + len, outer);
            tongue(c, cx, cy, rb - 3, (float) a, 0.12f, rb + len * 0.55f, inner);
        }
    }

    private void tongue(Gfx c, float cx, float cy, float r0, float a, float half, float r1, int col) {
        float[] q = tri;
        q[0] = cx + (float) Math.cos(a - half) * r0;
        q[1] = cy + (float) Math.sin(a - half) * r0;
        q[2] = cx + (float) Math.cos(a + 0.3f) * r1;
        q[3] = cy + (float) Math.sin(a + 0.3f) * r1;
        q[4] = cx + (float) Math.cos(a + half) * r0;
        q[5] = cy + (float) Math.sin(a + half) * r0;
        c.fillPoly(q, col);
    }

    private void drawPan(Gfx c, int i) {
        Pan p = pan[i];
        float cx = PAN_X[i], r = panR;
        float cy = panY - p.hop * 6 * (float) Math.sin(p.hop * Math.PI);
        if (!p.unlocked) {
            c.fillCircle(cx, panY, r - 6, 0xFF1D232A);
            c.line(cx - 10, panY - 10, cx + 10, panY + 10, 3, 0xFF2F3740);
            c.line(cx + 10, panY - 10, cx - 10, panY + 10, 3, 0xFF2F3740);
            return;
        }
        drawFlames(c, i);
        // Handle up and to the right, away from the gauge.
        float hx = cx + r * 0.7f, hy = cy - r * 0.7f;
        c.line(hx, hy, hx + 28, hy - 22, 8, 0xFF2B323B);
        c.line(hx, hy, hx + 28, hy - 22, 4.5f, 0xFF4B5561);
        int rim = p.badFlash > 0 ? mix(0xFF4B5561, 0xFFE0603A, p.badFlash) : 0xFF4B5561;
        c.fillCircle(cx, cy, r, rim);
        c.fillCircle(cx, cy, r - 5, 0xFF222831);
        c.fillCircle(cx, cy, r - 8, p.cooking ? 0xFF1B2027 : 0xFF2B323B);
        if (p.flash > 0) c.fillCircle(cx, cy, r - 5, rgba(0xFFFFFF, 0.4f * p.flash));
        boolean food = p.cooking || p.ruin > 0;
        if (food) {
            float hv = p.cooking ? p.heat : BURN_AT;
            float wob = p.cooking && p.heat > 0.1f ? 0.012f : 0;
            float sz = panR / 38f * 1.18f;
            food(c, p.kind, cx, cy + 1, sz, hv, wob);
            // Oil bubbles pop around the food while it fries.
            if (p.cooking) {
                for (int b = 0; b < 4; b++) {
                    float ph = (time * 2.3f + b * 0.27f + i * 0.4f) % 1f;
                    double a = b * 1.7 + i + Math.floor(time * 2.3f + b * 0.27f + i * 0.4f) * 2.3;
                    float bx = cx + (float) Math.cos(a) * (r - 11), by = cy + (float) Math.sin(a) * (r - 11);
                    c.fillCircle(bx, by, 1.2f + 2.4f * ph, rgba(0xFFE9A8, 0.55f * (1 - ph)));
                }
            }
        } else if (rail.size() > 0 || issued < dishes) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 4 + i);
            c.strokeRoundRect(cx - r + 5, cy - r + 5, 2 * r - 10, 2 * r - 10, r - 5, 2, rgba(0xF2C14E, 0.25f + 0.45f * pulse));
            c.line(cx - 8, cy, cx + 8, cy, 3, rgba(0xF2C14E, 0.5f + 0.4f * pulse));
            c.line(cx, cy - 8, cx, cy + 8, 3, rgba(0xF2C14E, 0.5f + 0.4f * pulse));
        }
        if (p.ruin > 0) {
            // A little grease fire: orange tongues above the black food.
            float k = Ui.clamp(p.ruin / 1.3f, 0, 1);
            for (int f = 0; f < 3; f++) {
                float fx = cx + (f - 1) * 12, fl = (10 + 14 * k) * (0.7f + 0.3f * (float) Math.sin(time * 20 + f * 2));
                c.fillPoly(new float[]{fx - 6, cy + 4, fx + 0.5f, cy + 4 - fl, fx + 6, cy + 4}, rgba(0xFF5A2A, k));
                c.fillPoly(new float[]{fx - 3, cy + 4, fx, cy + 4 - fl * 0.55f, fx + 3, cy + 4}, rgba(0xFFD34D, k));
            }
        }
        drawGauge(c, i);
    }

    private void zone(Gfx c, float gx, float gw, float h0, float h1, float marker, int col) {
        float x0 = gx + h0 / BURN_AT * gw, x1 = gx + Math.min(h1, BURN_AT) / BURN_AT * gw;
        c.fillRect(x0, gaugeY, x1 - x0, 9, rgba(col, 0.35f));
        float xm = gx + Math.min(marker, BURN_AT) / BURN_AT * gw;
        if (xm > x0) c.fillRect(x0, gaugeY, Math.min(x1, xm) - x0, 9, col | 0xFF000000);
    }

    private void drawGauge(Gfx c, int i) {
        Pan p = pan[i];
        float cx = PAN_X[i], gw = 84, gx = cx - gw / 2;
        if (!p.cooking) {
            if (p.ruin > 0) c.text("מתנקה", cx, gaugeY + 10, 12, Ui.DIM, Gfx.CENTER);
            else if (!rail.isEmpty()) c.text("להתחיל", cx, gaugeY + 10, 12, Ui.DIM, Gfx.CENTER);
            return;
        }
        c.fillRoundRect(gx - 2, gaugeY - 2, gw + 4, 13, 4, 0xFF0C0F13);
        zone(c, gx, gw, 0, RAW_UNTIL, p.heat, 0x7A8591);
        zone(c, gx, gw, RAW_UNTIL, 1 - p.pw, p.heat, 0x4CC28A);
        zone(c, gx, gw, 1 - p.pw, 1 + p.pw, p.heat, 0xF2C14E);
        zone(c, gx, gw, 1 + p.pw, BURN_AT, p.heat, 0x4CC28A);
        float xm = gx + Math.min(p.heat, BURN_AT) / BURN_AT * gw;
        c.line(xm, gaugeY - 4, xm, gaugeY + 13, 3, 0xFFFFFFFF);
        c.fillCircle(xm, gaugeY - 4, 3.4f, 0xFFFFFFFF);
        // Burn warning: the end of the bar blinks red as the dish goes past gold.
        if (p.heat > 1 + p.pw) {
            float blink = 0.5f + 0.5f * (float) Math.sin(time * 22);
            c.fillRect(gx + gw - 5, gaugeY, 5, 9, rgba(0xFF3B2A, 0.4f + 0.6f * blink));
        }
    }

    private void puffs(Gfx c, float cx, float cy, int n, float speed, float rise, int seed, int rgb, float maxA, float grow) {
        for (int k = 0; k < n; k++) {
            float ph = (time * speed + k / (float) n + seed * 0.37f) % 1f;
            float x = cx + (float) Math.sin(time * 2.2 + k * 2.1 + seed) * 9 * ph + (k - (n - 1) / 2f) * 7;
            float y = cy - ph * rise;
            float a = (1 - ph) * (ph < 0.15f ? ph / 0.15f : 1) * maxA;
            c.fillCircle(x, y, 4 + ph * grow, rgba(rgb, a));
        }
    }

    private void drawVapour(Gfx c, int i) {
        Pan p = pan[i];
        if (!p.unlocked) return;
        float cx = PAN_X[i], cy = panY - panR + 4;
        if (p.cooking) {
            float steam = Ui.clamp((p.heat - 0.2f) * 1.4f, 0, 1);
            if (p.heat < 1.05f) puffs(c, cx, cy, 4, 0.55f, 46, i, 0xFFFFFF, 0.32f * steam, 9);
            if (p.heat > 1.0f) {
                float s = Ui.clamp((p.heat - 1.0f) / 0.2f, 0, 1);
                puffs(c, cx, cy, 5, 0.8f, 56, i + 3, 0x30343A, 0.75f * s, 12);
            }
        } else if (p.ruin > 0) {
            puffs(c, cx, cy, 6, 0.9f, 62, i + 5, 0x2A2D32, 0.85f * Ui.clamp(p.ruin, 0, 1), 14);
        }
    }

    private void drawPass(Gfx c) {
        c.fillRect(0, passTop, W, passH, 0xFF3A434D);
        c.fillRect(0, passTop, W, 4, 0xFFB5C0CB);
        c.fillRect(0, passTop + 4, W, 2, 0xFF59636E);
        // Heat-lamp glow along the lane the plates slide on.
        c.fillRoundRect(10, passTop + 8, W - 20, 20, 10, 0xFF2D353E);
        for (float[] pl : plates) {
            if (pl[7] == 0) continue;
            float x = pl[0], y = pl[1];
            c.fillCircle(x, y + 2, 15, 0x44000000);
            c.fillCircle(x, y, 15, 0xFFE9EDF1);
            c.fillCircle(x, y, 11.5f, 0xFFFFFFFF);
            food(c, (int) pl[5], x, y, 0.42f, pl[6], 0);
        }
        c.text(served + "/" + dishes, 14, passTop + passH - 10, 15, Ui.INK, Gfx.LEFT);
        c.text("הוגשו", 14 + c.textWidth(served + "/" + dishes, 15) + 8, passTop + passH - 10, 12, Ui.DIM, Gfx.LEFT);
        int sk = streak;
        int col = sk >= 3 ? Ui.ACCENT : sk >= 1 ? Ui.INK : Ui.DIM;
        c.text("x" + sk, W - 14, passTop + passH - 10, sk >= 3 ? 17 : 15, col, Gfx.RIGHT);
        c.text("רצף", W - 14 - c.textWidth("x" + sk, sk >= 3 ? 17 : 15) - 8, passTop + passH - 10, 12, Ui.DIM, Gfx.RIGHT);
        if (burnt + gone > 0) {
            c.text(String.valueOf(burnt + gone), W / 2 - 4, passTop + passH - 10, 15, Ui.BAD, Gfx.RIGHT);
            c.text("הפסדנו", W / 2 + 4, passTop + passH - 10, 12, Ui.DIM, Gfx.LEFT);
        }
    }

    private void drawRail(Gfx c) {
        for (float[] g : ghosts) {
            Ticket t = new Ticket();
            t.kind = (int) g[2];
            t.rush = g[4] > 0;
            t.left = t.total = 1;
            drawTicket(c, t, g[0], g[1] + g[3] * 40, 1 - g[3] / 0.7f, false);
        }
        for (int k = rail.size() - 1; k >= 0; k--) {
            if (k == sel) continue;
            Ticket t = rail.get(k);
            drawTicket(c, t, t.x + (float) Math.sin(time * 1.6 + k * 2) * 0.9f, railTop + 10 + t.dy, 1, false);
        }
        if (sel < rail.size()) {
            Ticket t = rail.get(sel);
            drawTicket(c, t, t.x, railTop + 5 + t.dy, 1, true);
        }
    }

    private void drawTicket(Gfx c, Ticket t, float cx, float y, float alpha, boolean selected) {
        float w = 78, hh = 70, x = cx - w / 2;
        if (alpha <= 0) return;
        c.fillRoundRect(x + 2, y + 3, w, hh, 5, rgba(0x000000, 0.35f * alpha));
        int paper = t.rush ? 0xFFFFE3D6 : 0xFFF1E9D2;
        c.fillRoundRect(x, y, w, hh, 5, rgba(paper, alpha));
        if (selected) c.strokeRoundRect(x - 1, y - 1, w + 2, hh + 2, 6, 2.5f, Ui.ACCENT);
        c.fillRect(cx - 8, y - 3, 16, 7, rgba(0x7C8793, alpha));
        float iy = y + 28;
        if (t.rush) {
            c.fillRoundRect(x, y + 6, w, 16, 3, rgba(0xD94A3A, alpha));
            c.text("דחוף x2", cx, y + 19, 12, rgba(0xFFFFFF, alpha), Gfx.CENTER);
            iy = y + 38;
        }
        food(c, t.kind, cx, iy, 0.62f, t.kind == 0 ? 0.55f : 0.85f, 0);
        int ink = rgba(0x2A2018, alpha);
        c.text(NAME[t.kind], cx, y + hh - 11, 13, ink, Gfx.CENTER);
        // Cook time in flame pips: one for the egg, three for the steak.
        for (int f = 0; f < 3; f++) {
            c.fillCircle(x + 9 + f * 8, t.rush ? y + 30 : y + 12, 2.8f, rgba(f <= t.kind ? 0xE8702A : 0xBFB59A, alpha));
        }
        float frac = Ui.clamp(t.left / t.total, 0, 1);
        int bar = frac > 0.5f ? 0x4CC28A : frac > 0.25f ? 0xF2C14E : 0xE0503A;
        c.fillRect(x + 6, y + hh - 6, w - 12, 3.5f, rgba(0xCFC4A8, alpha));
        c.fillRect(x + 6, y + hh - 6, (w - 12) * frac, 3.5f, rgba(bar, alpha));
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        float savedTime = time;
        float pr = 44;
        c.fillCircle(cx, cy, pr + 22, 0x14FF8A30);
        c.fillCircle(cx, cy, pr + 12, 0xFF0F1318);
        for (int k = 0; k < 14; k++) {
            double a = k * Math.PI * 2 / 14 + 0.1;
            float fl = 0.7f + 0.3f * (float) Math.sin(t * 17 + k * 2.3);
            tongue(c, cx, cy, pr + 2, (float) a, 0.17f, pr + 6 + 16 * fl, 0xFFFF8A2A);
            tongue(c, cx, cy, pr + 2, (float) a, 0.1f, pr + 6 + 9 * fl, 0xFFFFE070);
        }
        c.line(cx + 32, cy - 32, cx + 74, cy - 56, 9, 0xFF2B323B);
        c.line(cx + 32, cy - 32, cx + 74, cy - 56, 5, 0xFF4B5561);
        c.fillCircle(cx, cy, pr, 0xFF4B5561);
        c.fillCircle(cx, cy, pr - 6, 0xFF1B2027);
        time = t;
        food(c, 1, cx - 2, cy + 2, 1.3f, 0.95f, 0.01f);
        time = savedTime;
        for (int k = 0; k < 4; k++) {
            float ph = (t * 0.6f + k * 0.25f) % 1f;
            c.fillCircle(cx + (float) Math.sin(t * 2 + k * 2) * 8 + (k - 1.5f) * 8, cy - pr - ph * 40, 5 + ph * 9,
                    rgba(0xFFFFFF, (1 - ph) * 0.3f));
        }
    }

    // ------------------------------------------------------------------------------------------ test bot

    @Override
    void bot(Random r, float skill) {
        for (int i = 0; i < PANS; i++) {
            Pan p = pan[i];
            if (!p.unlocked) continue;
            if (p.cooking) {
                if (Float.isNaN(target[i])) {
                    // The player's timing error is in seconds, so slow dishes forgive more in heat terms.
                    float sigma = (1 - skill) * 0.65f + 0.05f;
                    target[i] = skill < 0.1f ? 0.5f + r.nextFloat() * 0.9f
                            : 1 + (float) r.nextGaussian() * sigma * p.rate;
                }
                if (p.heat >= target[i] && p.grace <= 0) down(PAN_X[i], panY);
            } else {
                target[i] = Float.NaN;
                float eager = 0.01f + 0.5f * skill * skill;
                if (p.ruin <= 0 && !rail.isEmpty() && r.nextFloat() < eager) {
                    if (r.nextFloat() < skill) down(slotX(mostUrgent()), railTop + 40);
                    down(PAN_X[i], panY);
                }
            }
        }
    }

    /** Rush tickets first, otherwise whoever is about to walk out. */
    private int mostUrgent() {
        int best = 0;
        float bestV = 1e9f;
        for (int k = 0; k < rail.size(); k++) {
            Ticket t = rail.get(k);
            float v = t.left - (t.rush ? 3 : 0);
            if (v < bestV) {
                bestV = v;
                best = k;
            }
        }
        return best;
    }
}
