package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Everything a card table needs that is not a rule: an honest deck, cards drawn as polygons (suits,
 * big indices, a card back of our own), felt, chips, the dealer's silhouette, and small animation
 * helpers (a {@link Sprite} flies from A to B and flips).
 *
 * A card is an int 0..51: rank = c % 13 (0 = deuce ... 12 = ace), suit = c / 13 (0 spade, 1 heart,
 * 2 diamond, 3 club). A multi-deck shoe stores the same ids several times.
 */
final class Cards {
    static final int SPADE = 0, HEART = 1, DIAMOND = 2, CLUB = 3;
    static final int INK_BLACK = 0xFF1B2029, INK_RED = 0xFFC9302C, PAPER = 0xFFF8F4EA, FELT = 0xFF0F5B3D;
    static final int GOLD = 0xFFFFD27A;

    private Cards() {
    }

    // ------------------------------------------------------------------ the deck

    /** An honest shoe: Fisher-Yates over a flat array, drawing from the front. Nothing else touches it. */
    static final class Deck {
        private final int[] cards;
        private int pos;

        Deck(int decks) {
            cards = new int[52 * decks];
            for (int i = 0; i < cards.length; i++) cards[i] = i % 52;
        }

        /** Gathers every card back in and shuffles uniformly. */
        void shuffle(Random r) {
            for (int i = cards.length - 1; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = cards[i];
                cards[i] = cards[j];
                cards[j] = t;
            }
            pos = 0;
        }

        int draw() {
            return cards[pos++];
        }

        int left() {
            return cards.length - pos;
        }

        int size() {
            return cards.length;
        }
    }

    static int rank(int c) {
        return c % 13;
    }

    static int suit(int c) {
        return (c / 13) % 4;
    }

    static boolean red(int c) {
        int s = suit(c);
        return s == HEART || s == DIAMOND;
    }

    /** The universal indices are the one allowed use of Latin letters; digits are digits. */
    static String rankText(int c) {
        int r = rank(c);
        if (r <= 8) return String.valueOf(r + 2);
        return r == 9 ? "J" : r == 10 ? "Q" : r == 11 ? "K" : "A";
    }

    // ------------------------------------------------------------------ suit shapes

    private static final float[] HEART_PTS = new float[44];
    private static final float[] SCRATCH = new float[64];
    private static final float[] COS = new float[24], SIN = new float[24];

    static {
        for (int i = 0; i < 22; i++) {
            double t = Math.PI * 2 * i / 22;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            HEART_PTS[i * 2] = (float) (x / 17);
            HEART_PTS[i * 2 + 1] = (float) (-(y - 2.5) / 17); // y flipped to screen, centred
        }
        for (int i = 0; i < 24; i++) {
            COS[i] = (float) Math.cos(Math.PI * 2 * i / 24);
            SIN[i] = (float) Math.sin(Math.PI * 2 * i / 24);
        }
    }

    /** A suit pip centred at (cx, cy); size is the full height. */
    static void suitShape(Gfx g, int suit, float cx, float cy, float size, int col) {
        float s = size / 2;
        switch (suit) {
            case DIAMOND:
                g.fillPoly(new float[]{cx, cy - s, cx + s * 0.72f, cy, cx, cy + s, cx - s * 0.72f, cy}, col);
                break;
            case HEART:
                scaled(g, HEART_PTS, 22, cx, cy, s * 1.02f, 1f, col);
                break;
            case SPADE:
                scaled(g, HEART_PTS, 22, cx, cy - s * 0.12f, s * 1.02f, -1f, col);
                g.fillPoly(new float[]{cx - s * 0.1f, cy + s * 0.2f, cx + s * 0.1f, cy + s * 0.2f, cx + s * 0.4f, cy + s,
                        cx - s * 0.4f, cy + s}, col);
                break;
            default:
                g.fillCircle(cx, cy - s * 0.42f, s * 0.42f, col);
                g.fillCircle(cx - s * 0.46f, cy + s * 0.2f, s * 0.42f, col);
                g.fillCircle(cx + s * 0.46f, cy + s * 0.2f, s * 0.42f, col);
                g.fillPoly(new float[]{cx - s * 0.1f, cy - s * 0.1f, cx + s * 0.1f, cy - s * 0.1f, cx + s * 0.38f, cy + s,
                        cx - s * 0.38f, cy + s}, col);
        }
    }

    private static void scaled(Gfx g, float[] pts, int n, float cx, float cy, float s, float flipY, int col) {
        for (int i = 0; i < n; i++) {
            SCRATCH[i * 2] = cx + pts[i * 2] * s;
            SCRATCH[i * 2 + 1] = cy + pts[i * 2 + 1] * s * flipY;
        }
        float[] xy = new float[n * 2];
        System.arraycopy(SCRATCH, 0, xy, 0, n * 2);
        g.fillPoly(xy, col);
    }

    /** A flat ellipse (rx, ry) as a polygon; used for chips seen from the side. */
    static void ellipse(Gfx g, float cx, float cy, float rx, float ry, int col) {
        float[] xy = new float[48];
        for (int i = 0; i < 24; i++) {
            xy[i * 2] = cx + COS[i] * rx;
            xy[i * 2 + 1] = cy + SIN[i] * ry;
        }
        g.fillPoly(xy, col);
    }

    // ------------------------------------------------------------------ one card

    /**
     * Draws a card whose top-left is (x, y). flip 0 = back showing, 1 = face showing; in between the
     * card narrows to an edge and widens again. Content (index, pips) is drawn only when wide enough.
     */
    static void card(Gfx g, float x, float y, float w, float h, int card, float flip) {
        float sx = Math.abs((float) Math.cos(Math.PI * Ui.clamp(flip, 0, 1)));
        boolean face = flip >= 0.5f;
        float sw = Math.max(2.5f, w * sx);
        float nx = x + (w - sw) / 2;
        if (sx < 0.55f) {
            g.fillRoundRect(nx, y, sw, h, Math.min(sw / 2, w * 0.1f), face ? PAPER : 0xFF7A1F3A);
            g.fillRect(nx, y + h * 0.1f, sw, h * 0.8f, face ? 0xFFDCD6C6 : 0xFF4B1226);
            return;
        }
        if (face) face(g, nx, y, sw, h, card);
        else back(g, nx, y, sw, h);
    }

    static void face(Gfx g, float x, float y, float w, float h, int card) {
        float rr = w * 0.11f;
        g.fillRoundRect(x, y, w, h, rr, 0xFF9A9482);
        g.fillRoundRect(x + 0.8f, y + 0.8f, w - 1.6f, h - 1.6f, rr, PAPER);
        int col = red(card) ? INK_RED : INK_BLACK;
        int su = suit(card), rk = rank(card);
        String t = rankText(card);
        float fs = h * (t.length() > 1 ? 0.31f : 0.36f);
        g.text(t, x + w * 0.09f, y + h * 0.1f + fs * 0.82f, fs, col, Gfx.LEFT);
        suitShape(g, su, x + w * 0.09f + Math.max(fs * 0.34f, w * 0.12f), y + h * 0.1f + fs * 0.82f + h * 0.15f, h * 0.17f, col);
        float px = x + w * 0.62f, py = y + h * 0.66f;
        if (rk >= 9 && rk <= 11) {
            // Court cards: a small emblem (crown / tiara / plume) above a big pip, in a thin frame.
            g.strokeRoundRect(x + w * 0.3f, y + h * 0.34f, w * 0.62f, h * 0.58f, 3, 1, col & 0x66FFFFFF | 0x66000000);
            float ex = x + w * 0.61f, ey = y + h * 0.48f, e = w * 0.2f;
            int gold = 0xFFE3A82B;
            if (rk == 11) {
                g.fillPoly(new float[]{ex - e, ey + e * 0.6f, ex - e, ey - e * 0.5f, ex - e * 0.45f, ey + e * 0.05f, ex,
                        ey - e * 0.8f, ex + e * 0.45f, ey + e * 0.05f, ex + e, ey - e * 0.5f, ex + e, ey + e * 0.6f}, gold);
            } else if (rk == 10) {
                g.fillPoly(new float[]{ex - e, ey + e * 0.6f, ex - e * 0.7f, ey - e * 0.3f, ex - e * 0.25f, ey + e * 0.2f,
                        ex, ey - e * 0.6f, ex + e * 0.25f, ey + e * 0.2f, ex + e * 0.7f, ey - e * 0.3f, ex + e, ey + e * 0.6f}, gold);
                g.fillCircle(ex, ey - e * 0.7f, e * 0.2f, col);
            } else {
                g.fillPoly(new float[]{ex - e * 0.8f, ey + e * 0.6f, ex - e * 0.2f, ey - e * 0.7f, ex + e * 0.9f, ey - e * 0.9f,
                        ex + e * 0.3f, ey + e * 0.2f, ex + e * 0.8f, ey + e * 0.6f}, gold);
            }
            suitShape(g, su, x + w * 0.61f, y + h * 0.76f, h * 0.2f, col);
        } else if (rk == 12) {
            suitShape(g, su, px - w * 0.04f, py - h * 0.02f, h * 0.44f, col);
        } else {
            suitShape(g, su, px, py, h * 0.3f, col);
        }
    }

    /** The back: wine-red field, a diamond lattice, and a gold heart in a medallion (a nod to Mom). */
    static void back(Gfx g, float x, float y, float w, float h) {
        float rr = w * 0.11f;
        g.fillRoundRect(x, y, w, h, rr, PAPER);
        float m = Math.max(2f, w * 0.07f);
        g.fillRoundRect(x + m, y + m, w - 2 * m, h - 2 * m, rr * 0.7f, 0xFF7A1F3A);
        float ix = x + m * 1.8f, iy = y + m * 1.8f, iw = w - m * 3.6f, ih = h - m * 3.6f;
        g.fillRect(ix, iy, iw, ih, 0xFF5C1329);
        float step = Math.max(5f, w * 0.17f);
        int lat = 0xFF9A3A58;
        for (float d = step; d < iw + ih; d += step) {
            float ax = Math.max(0, d - ih), bx = Math.min(iw, d); // the line x + y = d
            if (bx > ax) g.line(ix + ax, iy + d - ax, ix + bx, iy + d - bx, 0.9f, lat);
        }
        for (float e = -ih + step; e < iw; e += step) {
            float ax = Math.max(0, e), bx = Math.min(iw, e + ih); // the line x - y = e
            if (bx > ax) g.line(ix + ax, iy + ax - e, ix + bx, iy + bx - e, 0.9f, lat);
        }
        float cx = x + w / 2, cy = y + h / 2, r = Math.min(w, h) * 0.26f;
        g.fillPoly(new float[]{cx, cy - r * 1.25f, cx + r * 0.95f, cy, cx, cy + r * 1.25f, cx - r * 0.95f, cy}, 0xFF3E0C1C);
        g.fillPoly(new float[]{cx, cy - r * 1.05f, cx + r * 0.78f, cy, cx, cy + r * 1.05f, cx - r * 0.78f, cy}, GOLD);
        suitShape(g, HEART, cx, cy, r * 1.0f, 0xFFB3243F);
        g.strokeRoundRect(ix, iy, iw, ih, 2, 1, GOLD & 0x99FFFFFF | 0x99000000);
    }

    // ------------------------------------------------------------------ moving cards

    /**
     * A card in motion. It waits {@code delay} seconds, flies from (fx, fy) to (tx, ty) with a small
     * arc, and flips face-up when asked. Positions are the card's top-left.
     */
    static final class Sprite {
        int card;
        float x, y, fx, fy, tx, ty, t = 1, delay, dur = 0.3f, flip, flipTarget;
        boolean visible;
        /** 0..1 glow ring (winners). */
        float glow;

        Sprite(int card, float fromX, float fromY, float toX, float toY, float delay, boolean faceUp) {
            this.card = card;
            fx = x = fromX;
            fy = y = fromY;
            tx = toX;
            ty = toY;
            this.delay = delay;
            t = 0;
            flip = 0;
            flipTarget = faceUp ? 1 : 0;
        }

        /** Slides the card to a new resting place (used when a hand re-fans). */
        void moveTo(float nx, float ny) {
            if (Math.abs(nx - tx) < 0.5f && Math.abs(ny - ty) < 0.5f) return;
            fx = x;
            fy = y;
            tx = nx;
            ty = ny;
            t = 0;
            delay = 0;
            dur = 0.18f;
        }

        /** Like moveTo, but a card that has not left the shoe yet just changes its destination. */
        void retarget(float nx, float ny) {
            if (delay > 0 || !visible) {
                tx = nx;
                ty = ny;
            } else {
                moveTo(nx, ny);
            }
        }

        void flipUp() {
            flipTarget = 1;
        }

        boolean settled() {
            return delay <= 0 && t >= 1 && Math.abs(flip - flipTarget) < 0.01f;
        }

        void update(float dt) {
            if (delay > 0) {
                delay -= dt;
                if (delay > 0) return;
                dt = -delay;
                delay = 0;
            }
            visible = true;
            if (t < 1) {
                t = Math.min(1, t + dt / dur);
                float e = 1 - (1 - t) * (1 - t);
                x = fx + (tx - fx) * e;
                y = fy + (ty - fy) * e;
            }
            // Flipping starts once the card has mostly landed.
            if (t > 0.7f && flip != flipTarget) {
                float step = dt / 0.22f;
                flip = flip < flipTarget ? Math.min(flipTarget, flip + step) : Math.max(flipTarget, flip - step);
            }
        }

        void draw(Gfx g, float w, float h, float time) {
            if (!visible) return;
            float arc = t < 1 ? (float) Math.sin(Math.PI * t) * 10 : 0;
            float sh = 2 + arc * 0.5f;
            g.fillRoundRect(x + sh, y + sh + arc * 0.4f, w, h, w * 0.11f, 0x44000000);
            card(g, x, y - arc, w, h, card, flip);
            if (glow > 0) {
                float pulse = 0.65f + 0.35f * (float) Math.sin(time * 8);
                g.strokeRoundRect(x - 2, y - 2, w + 4, h + 4, w * 0.13f, 3, (((int) (glow * pulse * 255)) << 24) | 0xFFD27A);
            }
        }
    }

    // ------------------------------------------------------------------ the room

    /** Green felt for the whole play area, with a pool of light and a gold line of print. */
    static void felt(Gfx g, float top, float bottom, String arcText, float textY) {
        float h = bottom - top;
        g.fillRect(0, top, 360, h, 0xFF0B4A32);
        g.fillRect(0, top + h * 0.06f, 360, h * 0.88f, FELT);
        float[] xy = new float[48];
        for (int i = 0; i < 24; i++) {
            xy[i * 2] = 180 + COS[i] * 210;
            xy[i * 2 + 1] = top + h * 0.52f + SIN[i] * h * 0.42f;
        }
        g.fillPoly(xy, 0x1AFFFFFF);
        for (int i = 0; i < 24; i++) {
            xy[i * 2] = 180 + COS[i] * 130;
            xy[i * 2 + 1] = top + h * 0.52f + SIN[i] * h * 0.3f;
        }
        g.fillPoly(xy, 0x14FFFFFF);
        if (arcText != null) {
            g.line(36, textY + 6, 324, textY + 6, 1, 0x33FFD27A);
            g.text(arcText, 180, textY, 12, 0x99FFD27A, Gfx.CENTER);
        }
        g.fillRect(0, top, 360, 3, 0x66000000);
        g.fillRect(0, bottom - 3, 360, 3, 0x66000000);
    }

    /**
     * The dealer, from the chest up, standing behind the table edge at (cx, top). reach 0..1 sends the
     * right hand out toward (reachX); the left hand rests. Scale 1 is about 46 tall.
     */
    static void dealer(Gfx g, float cx, float top, float s, float time, float reach, float reachX, float reachY) {
        float breathe = (float) Math.sin(time * 1.7) * 0.8f * s;
        int vest = 0xFF14181F, shirt = 0xFFEDEFF2, skin = 0xFFE0AE86;
        g.fillPoly(new float[]{cx - 38 * s, top + 46 * s, cx - 33 * s, top + 24 * s + breathe, cx - 12 * s, top + 17 * s + breathe,
                cx + 12 * s, top + 17 * s + breathe, cx + 33 * s, top + 24 * s + breathe, cx + 38 * s, top + 46 * s}, vest);
        g.fillPoly(new float[]{cx - 8 * s, top + 17 * s + breathe, cx + 8 * s, top + 17 * s + breathe, cx, top + 40 * s}, shirt);
        g.fillPoly(new float[]{cx - 7 * s, top + 20 * s + breathe, cx, top + 23 * s + breathe, cx - 7 * s, top + 27 * s + breathe}, 0xFFC9302C);
        g.fillPoly(new float[]{cx + 7 * s, top + 20 * s + breathe, cx, top + 23 * s + breathe, cx + 7 * s, top + 27 * s + breathe}, 0xFFC9302C);
        g.fillCircle(cx, top + 22.5f * s + breathe, 1.8f * s, 0xFF8E1D1A);
        g.fillRect(cx - 3 * s, top + 13 * s + breathe, 6 * s, 5 * s, skin);
        g.fillCircle(cx, top + 8 * s + breathe, 8.5f * s, skin);
        g.fillPoly(new float[]{cx - 8.8f * s, top + 6 * s + breathe, cx - 6 * s, top - 1.5f * s + breathe, cx + 6 * s, top - 1.5f * s + breathe,
                cx + 8.8f * s, top + 6 * s + breathe, cx + 5 * s, top + 2.5f * s + breathe, cx - 5 * s, top + 2.5f * s + breathe}, 0xFF2A2018);
        g.fillCircle(cx - 3 * s, top + 8.5f * s + breathe, 0.9f * s, 0xFF2A2018);
        g.fillCircle(cx + 3 * s, top + 8.5f * s + breathe, 0.9f * s, 0xFF2A2018);
        g.line(cx - 2.5f * s, top + 12.5f * s + breathe, cx + 2.5f * s, top + 12.5f * s + breathe, 0.9f * s, 0xFF8A5A40);
        // Arms and hands: left rests on the felt, right can reach out to deal.
        float lx = cx - 30 * s, ly = top + 46 * s;
        g.line(cx - 33 * s, top + 26 * s, lx, ly, 6 * s, vest);
        ellipse(g, lx, ly, 6 * s, 3.4f * s, skin);
        float rx = cx + 30 * s + (reachX - (cx + 30 * s)) * reach, ry = top + 46 * s + (reachY - (top + 46 * s)) * reach;
        g.line(cx + 33 * s, top + 26 * s, rx, ry, 6 * s, vest);
        ellipse(g, rx, ry, 6 * s, 3.4f * s, skin);
    }

    // ------------------------------------------------------------------ chips

    /** Every chip the casino uses, largest first (piles are broken down in this order). */
    static final int[] CHIP_VALUES = {100, 50, 25, 10, 5, 2, 1};
    private static final int[] CHIP_COLS = {0xFF25272E, 0xFFE8862E, 0xFF2E9E5B, 0xFF2F6FD6, 0xFFC9302C, 0xFFF2C94C, 0xFFE9ECEF};
    /** Light chips take dark print and dark edge marks; the rest take white. */
    private static final boolean[] CHIP_LIGHT = {false, true, false, false, false, true, true};

    private static int chipIndex(int value) {
        for (int i = 0; i < CHIP_VALUES.length; i++) if (value >= CHIP_VALUES[i]) return i;
        return CHIP_VALUES.length - 1;
    }

    static int chipColor(int value) {
        return CHIP_COLS[chipIndex(value)];
    }

    /** A chip seen from above, with edge notches; on == false greys it out. */
    static void chipFace(Gfx g, float cx, float cy, float r, int value, boolean on, float press) {
        r *= 1 + press * 0.08f;
        int k = chipIndex(value);
        boolean light = CHIP_LIGHT[k];
        int col = on ? CHIP_COLS[k] : 0xFF3B4048;
        int edge = on ? (light ? 0xFF2B3340 : 0xFFF4F6F8) : 0xFF5B6270;
        g.fillCircle(cx + 1.5f, cy + 2.5f, r, 0x55000000);
        g.fillCircle(cx, cy, r, col);
        for (int i = 0; i < 8; i++) {
            float ax = COS[i * 3], ay = SIN[i * 3];
            float px = -ay, py = ax, hw = r * 0.15f;
            float x0 = cx + ax * r * 0.74f, y0 = cy + ay * r * 0.74f, x1 = cx + ax * r, y1 = cy + ay * r;
            g.fillPoly(new float[]{x0 + px * hw, y0 + py * hw, x1 + px * hw, y1 + py * hw, x1 - px * hw, y1 - py * hw,
                    x0 - px * hw, y0 - py * hw}, edge);
        }
        g.fillCircle(cx, cy, r * 0.66f, on ? Ui.darken(col, 0.82f) : 0xFF2C3139);
        g.fillCircle(cx, cy, r * 0.58f, col);
        int ink = !on ? 0xFF6B7380 : light ? 0xFF1B2029 : 0xFFFFFFFF;
        String t = String.valueOf(value);
        g.text(t, cx, cy + r * 0.2f, r * (t.length() > 2 ? 0.52f : 0.62f), ink, Gfx.CENTER);
    }

    /** A pile of chips for {@code amount}, seen from the side, stacks side by side, bottom at baseY. */
    static void chipPile(Gfx g, float cx, float baseY, float r, int amount, float alpha) {
        int n = CHIP_VALUES.length;
        int[] counts = new int[n];
        int left = Math.max(0, amount);
        int stacks = 0;
        for (int i = 0; i < n; i++) {
            counts[i] = left / CHIP_VALUES[i];
            left -= counts[i] * CHIP_VALUES[i];
            if (counts[i] > 0) stacks++;
        }
        if (stacks == 0) return;
        float gap = r * 2.05f;
        float x = cx - (stacks - 1) * gap / 2;
        int a = ((int) (Ui.clamp(alpha, 0, 1) * 255)) << 24;
        for (int i = 0; i < n; i++) {
            if (counts[i] == 0) continue;
            int m = Math.min(counts[i], 9);
            int col = CHIP_COLS[i];
            int stripe = CHIP_LIGHT[i] ? 0x2F6FD6 : 0xF4F6F8;
            float th = r * 0.3f;
            for (int k = 0; k < m; k++) {
                float y = baseY - k * th;
                ellipse(g, x, y + th * 0.6f, r, r * 0.46f, a | (Ui.darken(col, 0.55f) & 0xFFFFFF));
                g.fillRect(x - r, y - th * 0.3f, r * 2, th * 0.9f + 0.4f, a | (Ui.darken(col, 0.7f) & 0xFFFFFF));
                ellipse(g, x, y, r, r * 0.46f, a | (col & 0xFFFFFF));
                if (k % 2 == 0) {
                    for (int d = -1; d <= 1; d += 2) {
                        g.fillRect(x + d * r * 0.62f - r * 0.1f, y - th * 0.3f, r * 0.2f, th * 0.9f, a | stripe);
                    }
                }
            }
            float topY = baseY - (m - 1) * th;
            ellipse(g, x, topY, r * 0.62f, r * 0.28f, a | (Ui.darken(col, 0.85f) & 0xFFFFFF));
            x += gap;
        }
    }

    // ------------------------------------------------------------------ one casino

    /**
     * The frame the four casino venues share, so they read as one casino:
     * <ul>
     * <li>a stakes strip across the top of the play area: wallet at the right, the current bet and the
     * table maximum at the left, an optional short note in the middle;</li>
     * <li>a bottom bar: "יציאה" at the left, the primary action at the right. Exit and every
     * non-primary action share one neutral look; only the primary may be gold, and after a loss it is
     * drawn neutral too, so nothing then calls louder than the way out;</li>
     * <li>chips in the thumb zone right above the bar, drawn with {@link #chipFace} everywhere;</li>
     * <li>one result vocabulary: the same headline, floater, burst and sound for a win, an even
     * result and a loss at every table ({@link #resultFx}, {@link #result}).</li>
     * </ul>
     * Buttons act on release over the button they were pressed on; nothing repeats by itself.
     */
    static final class Room {
        static final float STRIP_H = 30, BAR_H = 48, PAD = 8, GAP = 4, EXIT_W = 80;
        /** Tray chips are 46 across plus a 2-unit ring: a full touch target. */
        static final float CHIP_R = 23;
        /** A host floater lives 1.1 s; the next hand or spin waits that long, so a number never floats next to the wrong cards. */
        static final float RESULT_HOLD = 1.1f;
        static final int LOST = -1, EVEN = 0, WON = 1;
        static final int NEUTRAL = 0xFF2C3644, OFF = 0xFF1B2028, OFF_INK = 0xFF5B6472;

        private Room() {
        }

        /** Top of the bottom bar. */
        static float barY(float bottom) {
            return bottom - 6 - BAR_H;
        }

        /** Centre line of the chip tray right above the bar. */
        static float trayY(float bottom) {
            return barY(bottom) - GAP - 2 - CHIP_R;
        }

        /** Top of the chip tray (anything above this is table). */
        static float trayTop(float bottom) {
            return trayY(bottom) - CHIP_R - 3;
        }

        /** First y below the stakes strip. */
        static float below(float top) {
            return top + STRIP_H;
        }

        static void strip(Gfx g, float top, int wallet, int bet, int max, String note, int noteCol) {
            float h = STRIP_H, cy = top + h / 2;
            g.fillRect(0, top, 360, h, 0xF00A0E13);
            g.fillRect(0, top + h - 1.5f, 360, 1.5f, 0x66FFD27A);
            String w = Ui.nis(wallet);
            g.fillCircle(343, cy, 7.5f, 0xFFB5761A);
            g.fillCircle(343, cy - 0.6f, 6.5f, Ui.ACCENT);
            g.text("₪", 343, cy + 3.6f, 9, 0xFF9A5F10, Gfx.CENTER);
            float ww = g.textWidth(w, 17);
            g.text(w, 331, cy + 6, 17, Ui.INK, Gfx.RIGHT);
            String b = "הימור " + Ui.nis(bet);
            float bs = Ui.fit(g, b, 110, 15);
            g.text(b, 10, cy + 5.5f, bs, bet > 0 ? Ui.ACCENT : Ui.DIM, Gfx.LEFT);
            float bw = g.textWidth(b, bs);
            String m = "עד " + Ui.nis(max);
            g.text(m, 10 + bw + 8, cy + 5, 12, Ui.DIM, Gfx.LEFT);
            float l = 10 + bw + 8 + g.textWidth(m, 12) + 10, r = 331 - ww - 10;
            if (note != null && !note.isEmpty() && r - l > 40) {
                float ns = Ui.fit(g, note, r - l, 13);
                g.text(note, (l + r) / 2, cy + 5, ns, noteCol, Gfx.CENTER);
            }
        }

        /** The exit button: always neutral, enabled only between hands or spins. */
        static void exit(Gfx g, float x, float y, float w, float h, boolean on, float press) {
            action(g, x, y, w, h, "יציאה", on, false, press);
        }

        /** A bar button. gold marks the primary action; pass false after a loss. */
        static void action(Gfx g, float x, float y, float w, float h, String label, boolean on, boolean gold, float press) {
            int fill = !on ? OFF : gold ? Ui.ACCENT : NEUTRAL;
            int ink = !on ? OFF_INK : gold ? 0xFF1A1300 : Ui.INK;
            float in = press * 2;
            if (on) g.fillRoundRect(x, y + 3, w, h, 12, 0x66000000);
            g.fillRoundRect(x + in, y + in, w - 2 * in, h - 2 * in, 12, fill);
            if (on) g.fillRoundRect(x + in + 2, y + in + 2, w - 2 * in - 4, (h - 2 * in) * 0.4f, 9, 0x1EFFFFFF);
            else g.strokeRoundRect(x + 0.5f, y + 0.5f, w - 1, h - 1, 12, 1, 0xFF2A313C);
            float s = Ui.fit(g, label, w - 14, 17);
            g.text(label, x + w / 2, y + h / 2 + s * 0.36f, s, ink, Gfx.CENTER);
        }

        /** A chip in the tray; selected draws a gold ring (venues where one chip is the current choice). */
        static void chip(Gfx g, float cx, float cy, int value, boolean on, boolean selected, float press) {
            if (selected) {
                g.fillCircle(cx, cy, CHIP_R + 5, 0x55FFE08A);
                g.fillCircle(cx, cy, CHIP_R + 3, 0xFFFFD27A);
            }
            chipFace(g, cx, cy, CHIP_R, value, on, press);
        }

        /** Size of a result: 2 for a jackpot (50 times the bet or more), 1 for a big win (10 times), else 0. */
        static int size(int bet, int back) {
            return back >= 50L * bet ? 2 : back >= 10L * bet ? 1 : 0;
        }

        static int kind(int bet, int back) {
            return back > bet ? WON : back == bet ? EVEN : LOST;
        }

        /**
         * The game feel of a settled hand or spin, the same at every table: a floating net amount, a burst
         * and a sound scaled by the size of the win, or a red amount, a small shake and the loss sound.
         * Returns {@link #WON}, {@link #EVEN} or {@link #LOST}.
         */
        static int resultFx(Host h, float x, float y, int bet, int back) {
            int net = back - bet, k = kind(bet, back), sz = size(bet, back);
            if (k == WON) {
                h.text(x, y, "+" + Ui.nis(net), Ui.ACCENT);
                h.burst(x, y, Ui.ACCENT, sz == 2 ? 40 : sz == 1 ? 22 : 10);
                if (sz == 2) {
                    h.burst(x - 90, y, 0xFFFF6B6B, 18);
                    h.burst(x + 90, y, 0xFF5AA9FF, 18);
                    h.shake(6);
                }
                h.sfx(sz == 2 ? Sfx.LEVEL : sz == 1 ? Sfx.PERFECT : Sfx.COIN);
            } else if (k == EVEN) {
                h.text(x, y, "ההימור חזר", Ui.INK);
                h.sfx(Sfx.TICK);
            } else {
                h.text(x, y, "-" + Ui.nis(-net), Ui.BAD);
                h.shake(3);
                h.sfx(Sfx.BAD);
            }
            return k;
        }

        static String headline(int bet, int back) {
            int k = kind(bet, back);
            return k == WON ? "זכית " + Ui.nis(back - bet) : k == EVEN ? "ההימור חזר" : "הפסדת " + Ui.nis(bet - back);
        }

        static int headlineColor(int bet, int back) {
            int k = kind(bet, back);
            return k == WON ? Ui.ACCENT : k == EVEN ? Ui.INK : Ui.BAD;
        }

        /** "הימור ₪10 · קיבלת ₪25": what went in and what came back, exactly as paid. */
        static String detail(int bet, int back) {
            return "הימור " + Ui.nis(bet) + " · קיבלת " + Ui.nis(back);
        }

        /**
         * The result block, centred at cx: the headline (popping for a moment after age 0) and under it
         * the reason ("הדילר חרג", "שני זוגות", ...) with the money detail. About 40 tall from y - 18.
         */
        static void result(Gfx g, float cx, float y, String reason, int bet, int back, float age) {
            float pop = 1 + Math.max(0, 1 - age * 4) * 0.25f;
            String hl = headline(bet, back);
            g.text(hl, cx, y, Ui.fit(g, hl, 320, 22) * pop, headlineColor(bet, back), Gfx.CENTER);
            String d = reason == null || reason.isEmpty() ? detail(bet, back) : reason + " · " + detail(bet, back);
            g.text(d, cx, y + 19, Ui.fit(g, d, 336, 13), 0xFFC9D2DC, Gfx.CENTER);
        }

        /** The note shown after an interrupted hand was settled while the player was away. */
        static String awayNote(int bet, int back) {
            int net = back - bet;
            return "בזמן שיצאת: " + (net > 0 ? "+" + Ui.nis(net) : net == 0 ? "ההימור חזר" : "-" + Ui.nis(-net));
        }

        static int awayColor(int bet, int back) {
            return headlineColor(bet, back);
        }
    }
}
