package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Waiter shift: remember-and-deliver on a packed restaurant floor. Every guest shows what they
 * ordered in a bubble for a moment, then the bubbles close. Plates come out of the kitchen one at a
 * time onto the pass (three slots); drag each plate to the table that ordered it.
 *
 * The decisions: a closed bubble can be reopened by tapping the table, but that table then pays
 * only a small tip and does not build the streak. A wrong plate is sent back, breaks the streak and
 * costs the table patience. Hasty guests (short bars) leave first, so with several plates on the
 * pass the order of service matters. From round three a guest changes their mind after the bubbles
 * have closed (a bubble pings open again, once), and look-alike dishes share a table row.
 *
 * Score is the tip jar: tips / best possible tips, with two marks drawn on the jar.
 */
final class WaiterMini extends Mini {
    private static final int MAXT = 6, SLOTS = 3, DISHES = 8;
    private static final int WAIT = 0, FED = 1, GONE = 2;
    /** Guests walk in during the first LEAD seconds, then the bubbles open. */
    private static final float LEAD = 0.5f;
    private static final float FOOT = 18;
    /** Tip jar marks as a share of the best possible tips. */
    private static final float OK_Q = 0.30f, PERFECT_Q = 0.80f;
    private static final int[] SHIRT = {0xFF5AA9FF, 0xFFFF9F5A, 0xFF9B7BE0, 0xFF5BD68A, 0xFFE06C9F, 0xFFD8C65A};
    private static final int[] SKIN = {0xFFF2C9A1, 0xFFD9A47C, 0xFFB57A52, 0xFFF7D6B8};
    private static final int[] HAIR = {0xFF3A2A20, 0xFF1C1C1C, 0xFFB5651D, 0xFFE0C068, 0xFF8A8A8A};
    private static final int[] SKIRT = {0xFFB04A4A, 0xFF4A6FB0, 0xFF3E8F63};
    /** Dishes that read alike at a glance: wedges, bowls. */
    private static final int[][] TWINS = {{1, 5}, {2, 4}};

    private static final class Flight {
        int slot, table, dish;
        float t, dur, sx, sy, tx, ty;
        boolean back, judged;
    }

    // ---- round state ----
    private int round, n, result = RUNNING;
    private float diff, t, peekT, plateStart, endT;
    private boolean started;
    private final int[] dish = new int[MAXT], state = new int[MAXT], peeks = new int[MAXT],
            peekSeq = new int[MAXT], look = new int[MAXT];
    private final float[] pat = new float[MAXT], patMax = new float[MAXT], peekOpen = new float[MAXT],
            peekTot = new float[MAXT], annoyT = new float[MAXT], fedT = new float[MAXT], goneT = new float[MAXT],
            changeT = new float[MAXT];
    private final boolean[] hasty = new boolean[MAXT], spawned = new boolean[MAXT];
    private final int[] queue = new int[MAXT];
    private int qHead;
    private float[] gaps = new float[MAXT];
    private float spawnT;
    private float doorT = 9;
    private final float[] changeAt = new float[2];
    private int changesLeft, changeIdx;

    private final int[] slotOwner = new int[SLOTS], slotDish = new int[SLOTS], slotState = new int[SLOTS];
    private final float[] slotAge = new float[SLOTS];
    private final ArrayList<Flight> flights = new ArrayList<>();

    private int sel = -1;
    private boolean dragging, moved, pointerDown;
    private float dragX, dragY, downX, downY;

    private int served, wrong, left, combo, peekCount;
    private float tips, maxTips;

    // ---- layout, recomputed on every draw ----
    private float lt, lb, passH, regionTop, rowH, sc, plateY;
    private final float[] cx = new float[MAXT], cellY = new float[MAXT];
    private final float[] slotX = new float[SLOTS];
    private static final float CELL_W = 176;

    // ---- bot memory ----
    private final int[] botMem = new int[MAXT], botSeen = new int[MAXT];
    private final boolean[] botPeeked = new boolean[MAXT];
    private final boolean[][] botNo = new boolean[MAXT][DISHES];
    private int botState, botSlot, botTab, botChkWrong, botChkTab, botChkDish;
    private float botNext, botAt, botChkAt;
    private boolean botSaw;

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("מלצר", "מלצרית");
    }

    @Override
    String howTo() {
        return "זוכרים מי הזמין מה, וגוררים כל צלחת לשולחן הנכון. אפשר להציץ שוב בלחיצה על שולחן, אבל הטיפ קטן יותר.";
    }

    @Override
    void begin(int r, float difficulty) {
        round = r;
        diff = Ui.clamp(difficulty, 0, 1);
        n = Math.min(MAXT, 3 + r + (diff > 0.85f ? 1 : 0));
        t = 0;
        started = false;
        result = RUNNING;
        endT = 0;
        peekT = Math.max(1.2f, 2.9f - 0.25f * r - 0.8f * diff);
        plateStart = LEAD + peekT + 0.35f;
        Random rnd = h.rnd();

        // Dishes: distinct, and from round two a look-alike pair is always seated.
        int[] pool = new int[DISHES];
        for (int i = 0; i < DISHES; i++) pool[i] = i;
        shuffle(pool, rnd);
        int k = 0;
        boolean twins = r >= 1 && (r >= 2 || rnd.nextBoolean());
        if (twins) {
            int[] pair = TWINS[rnd.nextInt(TWINS.length)];
            dish[k++] = pair[0];
            dish[k++] = pair[1];
        }
        for (int i = 0; i < DISHES && k < n; i++) {
            int d = pool[i];
            boolean used = false;
            for (int j = 0; j < k; j++) if (dish[j] == d) used = true;
            if (!used) dish[k++] = d;
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        shuffle(order, rnd);
        int[] tmp = new int[n];
        for (int i = 0; i < n; i++) tmp[i] = dish[order[i]];
        System.arraycopy(tmp, 0, dish, 0, n);

        int hastyCount = r == 0 ? 0 : (r >= 3 || diff > 0.6f ? 2 : 1);
        for (int i = 0; i < MAXT; i++) {
            state[i] = WAIT;
            peeks[i] = 0;
            peekSeq[i] = 0;
            peekOpen[i] = 0;
            annoyT[i] = fedT[i] = goneT[i] = changeT[i] = 0;
            hasty[i] = false;
            spawned[i] = false;
            look[i] = (r * 7 + i * 3 + (rnd.nextInt(3))) & 0x7fff;
            botMem[i] = -1;
            botSeen[i] = 0;
            botPeeked[i] = false;
            for (int d = 0; d < DISHES; d++) botNo[i][d] = false;
        }
        for (int placed = 0; placed < hastyCount && n > 2; ) {
            int i = rnd.nextInt(n);
            if (!hasty[i]) {
                hasty[i] = true;
                placed++;
            }
        }
        float base = 7.4f - 2.0f * diff;
        for (int i = 0; i < n; i++) {
            patMax[i] = pat[i] = base * (hasty[i] ? 0.62f : 1f) * (0.94f + 0.12f * rnd.nextFloat());
        }

        // The kitchen sends the short-fused guests' plates first, with some noise so it is not a rule.
        float[] key = new float[n];
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
            key[i] = patMax[i] + (rnd.nextFloat() - 0.5f) * 3.2f;
        }
        java.util.Arrays.sort(idx, (a, b) -> Float.compare(key[a], key[b]));
        for (int i = 0; i < n; i++) queue[i] = idx[i];
        qHead = 0;
        for (int i = 0; i < n; i++) gaps[i] = (0.95f - 0.25f * diff) * (0.82f + 0.36f * rnd.nextFloat());
        spawnT = 0;
        doorT = 9;

        changesLeft = r < 2 ? 0 : (r >= 3 || diff > 0.85f ? 2 : 1);
        changeIdx = 0;
        for (int i = 0; i < 2; i++) changeAt[i] = plateStart + 0.5f + i * 1.3f + rnd.nextFloat() * 0.7f;

        for (int s = 0; s < SLOTS; s++) {
            slotState[s] = 0;
            slotOwner[s] = -1;
        }
        flights.clear();
        sel = -1;
        dragging = moved = pointerDown = false;
        served = wrong = left = combo = peekCount = 0;
        tips = 0;
        maxTips = 0;
        for (int i = 0; i < n; i++) maxTips += 10 * mult(i);
        botState = 0;
        botNext = 0;
        botChkWrong = -1;
        botSaw = false;
        layout(Game.HUD_H + 74, h.screenH() - 96);
    }

    private static void shuffle(int[] a, Random r) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int x = a[i];
            a[i] = a[j];
            a[j] = x;
        }
    }

    /** Tip multiplier for the guest served after {@code streak} clean deliveries in a row. */
    private static float mult(int streak) {
        return 1 + 0.5f * Math.min(streak, 4);
    }

    // ---------------------------------------------------------------- logic

    @Override
    int update(float dt) {
        if (result != RUNNING) return result;
        if (dt > 0.1f) dt = 0.1f;
        t += dt;
        doorT += dt;
        if (!started && t >= LEAD) {
            started = true;
            for (int i = 0; i < n; i++) open(i, peekT);
            h.sfx(Sfx.DING);
        }
        boolean patienceRuns = started && t >= LEAD + peekT;
        boolean anyWaiting = false;
        for (int i = 0; i < n; i++) {
            if (peekOpen[i] > 0) peekOpen[i] -= dt;
            if (annoyT[i] > 0) annoyT[i] -= dt;
            if (changeT[i] > 0) changeT[i] -= dt;
            if (state[i] == FED) fedT[i] += dt;
            if (state[i] == GONE) goneT[i] += dt;
            if (state[i] != WAIT) continue;
            anyWaiting = true;
            if (patienceRuns) pat[i] -= dt;
            if (pat[i] <= 0) walkOut(i);
        }
        for (int s = 0; s < SLOTS; s++) if (slotState[s] == 1) slotAge[s] += dt;

        // Kitchen.
        if (t >= plateStart && qHead < n) {
            spawnT -= dt;
            if (spawnT <= 0) {
                while (qHead < n && state[queue[qHead]] != WAIT) qHead++;
                int slot = -1;
                for (int s = 0; s < SLOTS && slot < 0; s++) if (slotState[s] == 0) slot = s;
                if (qHead < n && slot >= 0) {
                    int owner = queue[qHead++];
                    spawned[owner] = true;
                    slotOwner[slot] = owner;
                    slotDish[slot] = dish[owner];
                    slotState[slot] = 1;
                    slotAge[slot] = 0;
                    spawnT = gaps[Math.min(MAXT - 1, qHead)];
                    doorT = 0;
                    h.sfx(Sfx.DOOR);
                }
            }
        }

        // A guest changes their mind, but only while the kitchen has not yet made their plate.
        if (changeIdx < changesLeft && t >= changeAt[changeIdx]) {
            changeIdx++;
            changeMind();
        }

        // Flights.
        for (int i = flights.size() - 1; i >= 0; i--) {
            Flight f = flights.get(i);
            f.t += dt;
            if (!f.judged && f.t >= f.dur) {
                f.judged = true;
                arrive(f);
            }
            if (f.judged && f.back && f.t >= f.dur * 2) {
                land(f);
                flights.remove(i);
            } else if (f.judged && !f.back) {
                flights.remove(i);
            }
        }

        if (!anyWaiting && flights.isEmpty()) {
            endT += dt;
            if (endT > 0.7f) {
                result = judge();
                return result;
            }
        } else {
            endT = 0;
        }
        if (t > 22) {
            // Safety net: nothing may outlast the shift.
            for (int i = 0; i < n; i++) if (state[i] == WAIT) walkOut(i);
        }
        return RUNNING;
    }

    private int judge() {
        float q = maxTips > 0 ? tips / maxTips : 0;
        if (q >= PERFECT_Q && left == 0) return PERFECT;
        if (q >= OK_Q && left * 2 <= n) return OK;
        return MISS;
    }

    private void open(int i, float dur) {
        peekOpen[i] = peekTot[i] = dur;
        peekSeq[i]++;
    }

    private void walkOut(int i) {
        state[i] = GONE;
        goneT[i] = 0;
        left++;
        combo = 0;
        peekOpen[i] = 0;
        for (int s = 0; s < SLOTS; s++) {
            if (slotOwner[s] == i && slotState[s] == 1) {
                slotState[s] = 0;
                h.burst(slotX[s], plateY, 0xFF8A94A3, 6);
            }
        }
        h.text(Ui.clamp(cx[i], 70, 290), cellY[i] + rowH * 0.2f, "נמאס לי!", Ui.BAD);
        h.shake(5);
        h.sfx(Sfx.BAD);
    }

    private void changeMind() {
        int pick = -1;
        for (int k = n - 1; k >= 0; k--) {
            int i = queue[k];
            if (state[i] == WAIT && !spawned[i] && peekOpen[i] <= 0) {
                pick = i;
                break;
            }
        }
        if (pick < 0) return;
        Random r = h.rnd();
        int old = dish[pick], nd = -1;
        if (r.nextBoolean()) {
            for (int[] pair : TWINS) {
                int tw = pair[0] == old ? pair[1] : pair[1] == old ? pair[0] : -1;
                if (tw >= 0 && !taken(tw)) nd = tw;
            }
        }
        for (int tries = 0; nd < 0 && tries < 40; tries++) {
            int d = r.nextInt(DISHES);
            if (d != old && !taken(d)) nd = d;
        }
        if (nd < 0) return;
        dish[pick] = nd;
        open(pick, 1.5f);
        changeT[pick] = 1.5f;
        h.text(Ui.clamp(cx[pick], 80, 280), cellY[pick] + rowH * 0.12f, "שיניתי דעה!", Ui.ORANGE);
        h.sfx(Sfx.DING);
    }

    private boolean taken(int d) {
        for (int i = 0; i < n; i++) if (state[i] == WAIT && dish[i] == d) return true;
        for (int s = 0; s < SLOTS; s++) if (slotState[s] != 0 && slotDish[s] == d) return true;
        return false;
    }

    /** The plate reaches the table: judge it. */
    private void arrive(Flight f) {
        int i = f.table;
        if (state[i] != WAIT) {
            f.back = true;
            return;
        }
        if (f.dish == dish[i]) {
            float heat = Ui.clamp(1f - (slotAge[f.slot] - 1.6f) / 2.6f * 0.4f, 0.6f, 1f);
            slotState[f.slot] = 0;
            boolean clean = peeks[i] == 0;
            float base = 10 * heat * (clean ? mult(combo) : 0.55f * (peeks[i] == 1 ? 1 : 0.55f));
            tips += base;
            served++;
            if (clean) combo++;
            state[i] = FED;
            fedT[i] = 0;
            peekOpen[i] = 0;
            int v = Math.round(base);
            float tx = Ui.clamp(cx[i], 60, 300);
            if (clean && combo >= 3) {
                h.text(tx, cellY[i] + rowH * 0.15f, "רצף " + combo + "  +" + v, Ui.ACCENT);
                h.sfx(Sfx.PERFECT);
                h.burst(f.tx, f.ty, Ui.ACCENT, 14);
            } else {
                h.text(tx, cellY[i] + rowH * 0.15f, "+" + v, clean ? Ui.GOOD : Ui.DIM);
                h.sfx(clean ? Sfx.COIN : Sfx.GOOD);
                h.burst(f.tx, f.ty, clean ? Ui.GOOD : Ui.DIM, 8);
            }
            h.sfx(Sfx.EAT);
        } else {
            wrong++;
            combo = 0;
            f.back = true;
            annoyT[i] = 0.9f;
            pat[i] = Math.max(0.5f, pat[i] - 0.25f * patMax[i]);
            h.text(Ui.clamp(cx[i], 70, 290), cellY[i] + rowH * 0.15f, "זה לא שלי!", Ui.BAD);
            h.shake(6);
            h.sfx(Sfx.BAD);
        }
    }

    /** A rejected plate is back on the pass. */
    private void land(Flight f) {
        int s = f.slot;
        if (state[slotOwner[s]] == WAIT) {
            slotState[s] = 1;
        } else {
            slotState[s] = 0;
        }
    }

    private void send(int slot, int table) {
        if (slotState[slot] != 1 || state[table] != WAIT) return;
        Flight f = new Flight();
        f.slot = slot;
        f.table = table;
        f.dish = slotDish[slot];
        f.sx = slotX[slot];
        f.sy = plateY;
        f.tx = cx[table];
        f.ty = cellY[table] + rowH * 0.62f;
        float dist = (float) Math.hypot(f.tx - f.sx, f.ty - f.sy);
        f.dur = 0.14f + dist / 900f;
        slotState[slot] = 2;
        flights.add(f);
        h.sfx(Sfx.WHOOSH);
    }

    private void peek(int i) {
        if (state[i] != WAIT || peekOpen[i] > 0 || !started) return;
        peeks[i]++;
        peekCount++;
        open(i, 1.0f);
        h.text(Ui.clamp(bubblePos(i)[0], 70, 290), cellY[i] + rowH * 0.12f, "הצצה, טיפ קטן", Ui.DIM);
        h.sfx(Sfx.TICK);
    }

    @Override
    void pause() {
        flights.clear();
        sel = -1;
        dragging = moved = pointerDown = false;
        botState = 0;
    }

    // ---------------------------------------------------------------- input

    private int tableAt(float x, float y) {
        for (int i = 0; i < n; i++) {
            if (x >= cx[i] - CELL_W / 2 && x <= cx[i] + CELL_W / 2 && y >= cellY[i] && y <= cellY[i] + rowH) return i;
        }
        return -1;
    }

    private int slotAt(float x, float y) {
        if (y < lt || y > lt + passH) return -1;
        for (int s = 0; s < SLOTS; s++) {
            if (Math.abs(x - slotX[s]) <= 29 && slotState[s] == 1) return s;
        }
        return -1;
    }

    @Override
    void down(float x, float y) {
        if (result != RUNNING || n == 0) return;
        pointerDown = true;
        downX = dragX = x;
        downY = dragY = y;
        moved = false;
        int s = slotAt(x, y);
        if (s >= 0) {
            if (sel == s) {
                // Tapping the lifted plate puts it back.
                sel = -1;
                dragging = false;
                pointerDown = false;
                return;
            }
            sel = s;
            dragging = true;
            h.sfx(Sfx.TAP);
            return;
        }
        int tb = tableAt(x, y);
        if (sel >= 0 && tb >= 0) {
            send(sel, tb);
            sel = -1;
            dragging = false;
            pointerDown = false;
        } else if (tb >= 0) {
            peek(tb);
            pointerDown = false;
        } else {
            sel = -1;
            dragging = false;
            pointerDown = false;
        }
    }

    @Override
    void move(float x, float y) {
        if (!pointerDown) return;
        dragX = x;
        dragY = y;
        if (Math.hypot(x - downX, y - downY) > 12) moved = true;
    }

    @Override
    void up(float x, float y) {
        if (!pointerDown) return;
        pointerDown = false;
        if (!dragging) return;
        dragging = false;
        if (sel < 0) return;
        if (!moved) return; // a tap: the plate stays lifted, tap a table next
        int tb = tableAt(x, y);
        if (tb >= 0) send(sel, tb);
        sel = -1;
    }

    // ---------------------------------------------------------------- layout

    private void layout(float top, float bottom) {
        lt = top;
        lb = bottom;
        float hh = bottom - top;
        passH = hh < 380 ? 66 : 88;
        plateY = top + passH * 0.46f;
        for (int s = 0; s < SLOTS; s++) slotX[s] = 258 - 58 * s;
        regionTop = top + passH + 4;
        int rows = Math.max(1, (n + 1) / 2);
        rowH = (bottom - FOOT - regionTop) / rows;
        sc = Ui.clamp(rowH / 95f, 0.72f, 1.3f);
        for (int i = 0; i < n; i++) {
            int row = i / 2;
            boolean lone = (n & 1) == 1 && i == n - 1;
            cx[i] = lone ? 180 : (i % 2 == 0 ? 272 : 88);
            cellY[i] = regionTop + row * rowH;
        }
    }

    private float[] bubblePos(int i) {
        float sb = Ui.clamp(sc, 0.78f, 1.15f);
        float dir = (i % 2 == 0) ? -1 : 1;
        float ty = cellY[i] + rowH * 0.70f;
        return new float[]{cx[i] + dir * 52 * sb, ty - 33 * sc, sb};
    }

    // ---------------------------------------------------------------- drawing

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawFloor(c);
        for (int i = 0; i < n; i++) drawTable(c, i);
        for (int i = 0; i < n; i++) drawBubble(c, i);
        drawPass(c);
        drawFlights(c);
        drawDrag(c);
        drawFooter(c);
    }

    private void drawFloor(Gfx c) {
        float y0 = regionTop - 2;
        c.fillRect(0, y0, W, lb - y0, 0xFF2A211B);
        int row = 0;
        for (float y = y0; y < lb; y += 22, row++) {
            if ((row & 1) == 1) c.fillRect(0, y, W, 22, 0xFF2F2620);
            c.fillRect(0, y, W, 1, 0xFF211A15);
            for (float x = (row * 83) % 110; x < W; x += 110) c.fillRect(x, y, 1, 22, 0xFF211A15);
        }
        for (int i = 0; i < n; i++) {
            c.fillCircle(cx[i], cellY[i] + rowH * 0.66f, 50 * sc, 0x10FFD9A0);
            c.fillCircle(cx[i], cellY[i] + rowH * 0.66f, 34 * sc, 0x10FFD9A0);
        }
    }

    private static float hash01(int v) {
        v = v * 0x9E3779B1;
        v ^= v >>> 15;
        v *= 0x85EBCA6B;
        v ^= v >>> 13;
        return (v & 0xffff) / 65535f;
    }

    private void drawTable(Gfx c, int i) {
        float s = sc;
        float ty = cellY[i] + rowH * 0.70f;
        float x = cx[i];
        float pop = Ui.clamp((t - i * 0.07f) / 0.35f, 0, 1);
        float ease = 1 - (1 - pop) * (1 - pop);
        int lk = look[i];
        // Chair back.
        c.fillRoundRect(x - 17 * s, ty - 40 * s, 34 * s, 34 * s, 7 * s, 0xFF4A3426);
        if (state[i] != GONE || goneT[i] < 0.7f) {
            float slide = state[i] == GONE ? goneT[i] * 130 * (x > 180 ? 1 : -1) : 0;
            float bob = state[i] == FED ? (float) Math.sin(fedT[i] * 11) * 1.6f * s * Math.max(0, 1 - fedT[i] / 1.6f)
                    : state[i] == WAIT ? (float) Math.sin(t * (hasty[i] ? 7 : 2.2f) + i * 1.9f) * (hasty[i] ? 1.2f : 0.7f) * s : 0;
            if (pop > 0) drawGuest(c, x + slide, ty + (1 - ease) * 10 * s, s * (0.7f + 0.3f * ease), lk, moodOf(i), bob,
                    annoyT[i] > 0, hasty[i]);
        }
        // Table: cloth top, skirt, little cutlery.
        int skirt = SKIRT[lk % SKIRT.length];
        c.fillRoundRect(x - 46 * s, ty - 4 * s, 92 * s, 22 * s, 8 * s, 0xFFEADFC5);
        c.fillRoundRect(x - 46 * s, ty + 12 * s, 92 * s, 8 * s, 3 * s, skirt);
        c.fillRect(x - 38 * s, ty + 1 * s, 2 * s, 6 * s, 0xFFB9B2A0);
        c.fillRect(x + 36 * s, ty + 1 * s, 2 * s, 6 * s, 0xFFB9B2A0);
        if (state[i] != FED) {
            // Candle, so the table reads as a table.
            c.fillRoundRect(x - 2 * s, ty - 2 * s, 4 * s, 8 * s, 1, 0xFFF4F6F8);
            float fl = 1 + 0.2f * (float) Math.sin(t * 9 + i * 2);
            c.fillPoly(new float[]{x - 1.8f * s, ty - 2 * s, x, ty - 8 * s * fl, x + 1.8f * s, ty - 2 * s}, 0xFFFFC857);
        }
        if (state[i] == FED) {
            c.fillRoundRect(x - 13 * s, ty - 2 * s, 26 * s, 9 * s, 4 * s, 0xFFFFFFFF);
            dish(c, dish[i], x, ty - 5 * s, 8 * s);
        }
        // Patience.
        if (state[i] == WAIT) {
            float frac = pat[i] / patMax[i];
            float bw = (hasty[i] ? 46 : 66) * s;
            float by = ty + 23 * s;
            int col = frac > 0.5f ? Ui.GOOD : frac > 0.25f ? Ui.ACCENT : Ui.BAD;
            if (frac < 0.25f && ((int) (t * 6) & 1) == 0) col = Ui.ORANGE;
            Ui.bar(c, x - bw / 2, by, bw, 6, frac, col);
            if (hasty[i]) {
                // A little flame: this guest burns out fast.
                float fx = x + bw / 2 + 9, fy = by + 8;
                c.fillPoly(new float[]{fx - 5, fy, fx - 4, fy - 6, fx - 1, fy - 9, fx, fy - 14, fx + 3, fy - 8, fx + 5, fy - 4,
                        fx + 5, fy}, Ui.ORANGE);
                c.fillPoly(new float[]{fx - 2, fy, fx - 1, fy - 4, fx + 1, fy - 6, fx + 2, fy}, Ui.ACCENT);
            }
        }
        if (state[i] == GONE && goneT[i] > 0.2f) {
            c.fillCircle(x, ty + 8 * s, 4 * s, 0x66FF6B6B);
        }
        // Valid input hint: tables glow while a plate is lifted.
        if ((sel >= 0 || dragging) && state[i] == WAIT) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 8);
            c.strokeRoundRect(x - CELL_W / 2 + 8, cellY[i] + 2, CELL_W - 16, rowH - 4, 12, 2,
                    ((int) (60 + 70 * pulse) << 24) | 0xFFC857);
        }
    }

    /** 0 happy, 1 neutral, 2 angry, 3 delighted. */
    private int moodOf(int i) {
        if (state[i] == FED) return 3;
        if (state[i] == GONE) return 2;
        float frac = pat[i] / patMax[i];
        if (annoyT[i] > 0) return 2;
        return frac > 0.5f ? 0 : frac > 0.25f ? 1 : 2;
    }

    private static void drawGuest(Gfx c, float x, float ty, float s, int lk, int mood, float bob, boolean flash, boolean hasty) {
        int shirt = SHIRT[lk % SHIRT.length];
        int skin = SKIN[(lk / 3) % SKIN.length];
        int hair = HAIR[(lk / 5) % HAIR.length];
        boolean longHair = (lk & 1) == 1;
        float hy = ty - 33 * s + bob;
        if (longHair) {
            c.fillPoly(new float[]{x - 12 * s, hy - 4 * s, x + 12 * s, hy - 4 * s, x + 13 * s, hy + 14 * s, x - 13 * s, hy + 14 * s}, hair);
        }
        c.fillPoly(new float[]{x - 17 * s, ty + 2 * s, x - 13 * s, ty - 18 * s + bob, x - 6 * s, ty - 23 * s + bob,
                x + 6 * s, ty - 23 * s + bob, x + 13 * s, ty - 18 * s + bob, x + 17 * s, ty + 2 * s}, shirt);
        c.fillRect(x - 3 * s, ty - 26 * s + bob, 6 * s, 5 * s, skin);
        c.fillCircle(x, hy, 10.5f * s, flash ? 0xFFE98B7A : skin);
        c.fillPoly(new float[]{x - 10.8f * s, hy - 0.5f * s, x - 9 * s, hy - 8 * s, x, hy - 12.2f * s, x + 9 * s, hy - 8 * s,
                x + 10.8f * s, hy - 0.5f * s, x + 6 * s, hy - 5.5f * s, x - 6 * s, hy - 5.5f * s}, hair);
        int ink = 0xFF2A1D16;
        float ew = 1.5f * s;
        if (mood == 3) {
            // Eyes closed in pleasure.
            c.line(x - 5.5f * s, hy - 0.5f * s, x - 2.5f * s, hy - 0.5f * s, 1.3f * s, ink);
            c.line(x + 2.5f * s, hy - 0.5f * s, x + 5.5f * s, hy - 0.5f * s, 1.3f * s, ink);
        } else {
            c.fillCircle(x - 4 * s, hy - 0.5f * s, ew, ink);
            c.fillCircle(x + 4 * s, hy - 0.5f * s, ew, ink);
        }
        float lw = 1.4f * s;
        if (mood == 0 || mood == 3) {
            float d = mood == 3 ? 2.6f : 1.8f;
            c.line(x - 4.5f * s, hy + 4 * s, x - 2 * s, hy + (4 + d) * s, lw, ink);
            c.line(x - 2 * s, hy + (4 + d) * s, x + 2 * s, hy + (4 + d) * s, lw, ink);
            c.line(x + 2 * s, hy + (4 + d) * s, x + 4.5f * s, hy + 4 * s, lw, ink);
        } else if (mood == 1) {
            c.line(x - 3 * s, hy + 5.5f * s, x + 3 * s, hy + 5.5f * s, lw, ink);
        } else {
            c.line(x - 4.2f * s, hy + 7.5f * s, x - 2 * s, hy + 5.3f * s, lw, ink);
            c.line(x - 2 * s, hy + 5.3f * s, x + 2 * s, hy + 5.3f * s, lw, ink);
            c.line(x + 2 * s, hy + 5.3f * s, x + 4.2f * s, hy + 7.5f * s, lw, ink);
            c.line(x - 7 * s, hy - 5 * s, x - 1.5f * s, hy - 3 * s, lw, ink);
            c.line(x + 7 * s, hy - 5 * s, x + 1.5f * s, hy - 3 * s, lw, ink);
        }
        if (mood == 2 && !flash) c.fillCircle(x - 7 * s, hy + 3 * s, 2 * s, 0x66FF6B6B);
    }

    private void drawBubble(Gfx c, int i) {
        if (state[i] != WAIT || !started || t < LEAD) return;
        float[] p = bubblePos(i);
        float bx = p[0], by = p[1], sb = p[2];
        float dirTo = cx[i] > bx ? 1 : -1;
        if (peekOpen[i] > 0) {
            float r = 21 * sb;
            float grow = Ui.clamp((peekTot[i] - peekOpen[i]) / 0.12f, 0.4f, 1f);
            r *= grow;
            boolean flashing = changeT[i] > 0;
            if (flashing) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 16);
                c.fillCircle(bx, by, r + 5 * sb, ((int) (90 + 120 * pulse) << 24) | 0xFF9F5A);
            }
            c.fillPoly(new float[]{bx + dirTo * (r - 3), by - 4 * sb, bx + dirTo * (r + 9 * sb), by + 5 * sb, bx + dirTo * (r - 3), by + 6 * sb}, 0xFFFFF4DC);
            c.fillCircle(bx, by, r + 2, 0xFFD9C79C);
            c.fillCircle(bx, by, r, 0xFFFFF4DC);
            dish(c, dish[i], bx, by, r * 0.64f);
            float frac = peekOpen[i] / peekTot[i];
            Ui.bar(c, bx - 15 * sb, by + r + 4, 30 * sb, 4, frac, flashing ? Ui.ORANGE : Ui.ACCENT);
            if (flashing) {
                c.fillCircle(bx + r * 0.75f, by - r * 0.75f, 7 * sb, Ui.BAD);
                c.text("!", bx + r * 0.75f, by - r * 0.75f + 5 * sb, 14 * sb, 0xFFFFFFFF, Gfx.CENTER);
            }
        } else {
            float r = 13 * sb;
            float breath = 1 + 0.06f * (float) Math.sin(t * 3 + i);
            r *= breath;
            c.fillPoly(new float[]{bx + dirTo * (r - 2), by - 2, bx + dirTo * (r + 6 * sb), by + 4 * sb, bx + dirTo * (r - 2), by + 4}, 0x99313A47);
            c.fillCircle(bx, by, r, 0x99313A47);
            c.text("?", bx, by + 5 * sb, 15 * sb, 0xCCB8C2D0, Gfx.CENTER);
        }
    }

    private void drawPass(Gfx c) {
        // Kitchen wall, counter, door.
        c.fillRect(0, lt, W, passH, 0xFF20262E);
        for (float x = 0; x < W; x += 30) c.fillRect(x, lt, 1, passH, 0xFF1A2027);
        float shelfY = plateY + 18;
        c.fillRect(0, shelfY, W, lt + passH - shelfY, 0xFF6B4A2F);
        c.fillRect(0, shelfY, W, 3, 0xFF8C6340);
        c.fillRect(0, lt + passH, W, 3, 0xFF3A2A1E);
        // Swinging kitchen door.
        float sw = doorT < 0.35f ? (float) Math.sin(doorT / 0.35f * Math.PI) : 0;
        float dx = 296, dtop = lt + 20, dh = lt + passH - 2 - dtop;
        float lw = 26 * (1 - 0.55f * sw);
        c.fillRect(dx - 2, dtop - 2, 58, dh + 4, 0xFF14181D);
        if (sw > 0.05f) c.fillRect(dx, dtop, 54, dh, 0xFFFFD9A0);
        c.fillRoundRect(dx, dtop, lw, dh, 3, 0xFF9AA5B4);
        c.fillRoundRect(dx + 54 - lw, dtop, lw, dh, 3, 0xFF8793A3);
        c.fillCircle(dx + 13 * (1 - 0.55f * sw), dtop + dh * 0.35f, 5, 0xFF20262E);
        c.fillCircle(dx + 54 - 13 * (1 - 0.55f * sw), dtop + dh * 0.35f, 5, 0xFF20262E);
        c.text("מטבח", dx + 27, lt + 14, 12, Ui.DIM, Gfx.CENTER);

        // Slots and plates.
        boolean peeking = t < LEAD + peekT;
        for (int s = 0; s < SLOTS; s++) {
            float x = slotX[s];
            c.fillCircle(x, plateY + 2, 23, 0x12FFFFFF);
            if (slotState[s] != 1) continue;
            float age = slotAge[s];
            float e = Ui.clamp(age / 0.3f, 0, 1);
            float px = x + (1 - e) * (dx + 20 - x);
            float py = plateY - (sel == s ? 8 : 0) - (float) Math.sin(e * Math.PI) * 6;
            if (dragging && sel == s) {
                c.fillCircle(x, plateY + 2, 22, 0x22FFFFFF);
                continue;
            }
            drawPlate(c, slotDish[s], px, py, 22, age, sel == s);
        }
        if (peeking && started) {
            c.text("זכרו מי הזמין מה", 204, plateY + 5, 16, Ui.INK, Gfx.CENTER);
            Ui.bar(c, 150, plateY + 14, 108, 4, 1 - (t - LEAD) / peekT, Ui.ACCENT);
        } else if (!started) {
            c.text("אורחים מתיישבים", 204, plateY + 5, 14, Ui.DIM, Gfx.CENTER);
        }

        // Tip jar and streak.
        float jx = 10, jw = 36, jy = lt + 8, jh = passH - 16;
        c.fillRoundRect(jx, jy, jw, jh, 8, 0xFF2C3644);
        c.fillRoundRect(jx + 2, jy + 2, jw - 4, jh - 4, 7, 0xFF141A21);
        float q = maxTips > 0 ? Ui.clamp(tips / maxTips, 0, 1) : 0;
        float inner = jh - 8;
        float fh = inner * q;
        if (fh > 1) {
            c.fillRoundRect(jx + 5, jy + jh - 4 - fh, jw - 10, fh, 4, Ui.ACCENT);
            c.fillRect(jx + 5, jy + jh - 4 - fh, jw - 10, 2, 0xFFFFE9A8);
        }
        float yOk = jy + jh - 4 - inner * OK_Q, yPf = jy + jh - 4 - inner * PERFECT_Q;
        c.line(jx - 2, yOk, jx + jw + 2, yOk, 1.5f, Ui.GOOD);
        c.line(jx - 2, yPf, jx + jw + 2, yPf, 1.5f, Ui.ACCENT);
        star(c, jx + jw + 9, yPf, 6, Ui.ACCENT);
        c.fillCircle(jx + jw + 9, yOk, 3, Ui.GOOD);
        c.text("רצף", 92, lt + 22, 12, Ui.DIM, Gfx.CENTER);
        c.text(String.valueOf(combo), 92, lt + 48, 24, combo >= 3 ? Ui.ACCENT : Ui.INK, Gfx.CENTER);
    }

    private static void star(Gfx c, float x, float y, float r, int col) {
        float[] p = new float[20];
        for (int k = 0; k < 10; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5;
            float rr = (k & 1) == 0 ? r : r * 0.45f;
            p[k * 2] = x + (float) Math.cos(a) * rr;
            p[k * 2 + 1] = y + (float) Math.sin(a) * rr;
        }
        c.fillPoly(p, col);
    }

    private void drawPlate(Gfx c, int d, float x, float y, float r, float age, boolean lifted) {
        if (lifted) c.fillCircle(x, y, r + 5, 0x55FFC857);
        c.fillCircle(x + 1, y + 3, r, 0x55000000);
        c.fillCircle(x, y, r, 0xFFEFF2F5);
        c.fillCircle(x, y, r - 4, 0xFFD5DBE3);
        c.fillCircle(x, y, r - 6, 0xFFF4F6F8);
        dish(c, d, x, y + 1, r * 0.62f);
        float heat = Ui.clamp(1f - (age - 1.6f) / 2.6f, 0, 1);
        if (heat > 0.05f) {
            int a = (int) (110 * heat) << 24;
            for (int k = 0; k < 2; k++) {
                float ph = t * 2.2f + k * 1.7f;
                float sx = x - 6 + k * 12;
                c.line(sx, y - r - 2, sx + (float) Math.sin(ph) * 3, y - r - 8, 1.6f, a | 0xFFFFFF);
                c.line(sx + (float) Math.sin(ph) * 3, y - r - 8, sx - (float) Math.sin(ph + 1) * 3, y - r - 14, 1.6f, a | 0xFFFFFF);
            }
        }
    }

    private void drawFlights(Gfx c) {
        for (Flight f : flights) {
            float u = f.dur > 0 ? Ui.clamp(f.t / f.dur, 0, 1) : 1;
            float x, y;
            if (f.back && f.judged) {
                float v = Ui.clamp((f.t - f.dur) / f.dur, 0, 1);
                x = f.tx + (f.sx - f.tx) * v;
                y = f.ty + (f.sy - f.ty) * v;
            } else {
                x = f.sx + (f.tx - f.sx) * u;
                y = f.sy + (f.ty - f.sy) * u;
            }
            float arc = (float) Math.sin(Math.min(u, 1) * Math.PI) * 6;
            Ui.person(c, x, y + 14, 1.7f, false, f.t, true, 0xFFF4F6F8);
            c.fillPoly(new float[]{x - 3, y - 8, x + 3, y - 8, x + 2, y + 3, x - 2, y + 3}, 0xFF2A2F38);
            c.fillCircle(x, y - 12, 1.6f, Ui.BAD);
            c.line(x + 4, y - 8, x + 14, y - 20, 2, 0xFFF4F6F8);
            c.fillCircle(x + 15, y - 28 - arc * 0.3f, 10.5f, 0xFFEFF2F5);
            dish(c, f.dish, x + 15, y - 28.5f - arc * 0.3f, 7f);
        }
    }

    private void drawDrag(Gfx c) {
        if (!dragging || sel < 0 || !moved) return;
        drawPlate(c, slotDish[sel], dragX, Math.max(dragY - 30, lt + 34), 27, slotAge[sel], true);
    }

    private void drawFooter(Gfx c) {
        String s;
        int col = Ui.DIM;
        if (round < 2 && served + wrong + peekCount == 0) {
            s = "גררו צלחת לשולחן · הצצה בלחיצה על שולחן";
        } else {
            s = "הוגשו " + served + " מתוך " + n + (wrong > 0 ? " · טעויות " + wrong : "") + (left > 0 ? " · עזבו " + left : "");
            if (wrong + left > 0) col = Ui.ORANGE;
        }
        c.text(s, W / 2, lb - 5, 12, col, Gfx.CENTER);
    }

    // ---------------------------------------------------------------- dishes

    private static float[] arc(float x, float y, float rx, float ry, double a0, double a1, int seg) {
        float[] p = new float[(seg + 1) * 2];
        for (int k = 0; k <= seg; k++) {
            double a = a0 + (a1 - a0) * k / seg;
            p[k * 2] = x + (float) Math.cos(a) * rx;
            p[k * 2 + 1] = y + (float) Math.sin(a) * ry;
        }
        return p;
    }

    /** Simple, clearly different silhouettes. (x, y) is the centre, r the half-size. */
    static void dish(Gfx c, int id, float x, float y, float r) {
        switch (id) {
            case 0: // burger
                c.fillRoundRect(x - r, y + 0.3f * r, 2 * r, 0.55f * r, 0.25f * r, 0xFFD9893E);
                c.fillRect(x - 1.05f * r, y - 0.02f * r, 2.1f * r, 0.32f * r, 0xFF6B3A1F);
                c.fillPoly(new float[]{x - 1.1f * r, y - 0.02f * r, x - 0.75f * r, y - 0.3f * r, x - 0.35f * r, y - 0.02f * r,
                        x, y - 0.3f * r, x + 0.4f * r, y - 0.02f * r, x + 0.75f * r, y - 0.3f * r, x + 1.1f * r, y - 0.02f * r}, 0xFF5BD68A);
                c.fillPoly(arc(x, y - 0.3f * r, 1.05f * r, 0.75f * r, Math.PI, 2 * Math.PI, 10), 0xFFEBA24A);
                c.fillCircle(x - 0.4f * r, y - 0.7f * r, 0.08f * r + 0.6f, 0xFFFFF4DC);
                c.fillCircle(x + 0.1f * r, y - 0.85f * r, 0.08f * r + 0.6f, 0xFFFFF4DC);
                c.fillCircle(x + 0.5f * r, y - 0.62f * r, 0.08f * r + 0.6f, 0xFFFFF4DC);
                break;
            case 1: // pizza wedge
                c.fillPoly(new float[]{x - r, y - 0.75f * r, x + r, y - 0.75f * r, x, y + r}, 0xFFFFC857);
                c.fillRoundRect(x - r, y - 0.98f * r, 2 * r, 0.36f * r, 0.15f * r, 0xFFD9893E);
                c.fillCircle(x - 0.3f * r, y - 0.2f * r, 0.24f * r, 0xFFD64545);
                c.fillCircle(x + 0.32f * r, y - 0.28f * r, 0.22f * r, 0xFFD64545);
                c.fillCircle(x, y + 0.38f * r, 0.18f * r, 0xFFD64545);
                break;
            case 2: // soup bowl, blue, steaming
                c.fillPoly(arc(x, y - 0.1f * r, 1.1f * r, 0.95f * r, 0, Math.PI, 10), 0xFF4F8FE0);
                c.fillRoundRect(x - 1.1f * r, y - 0.28f * r, 2.2f * r, 0.3f * r, 0.15f * r, 0xFFFF9F5A);
                c.line(x - 0.45f * r, y - 0.4f * r, x - 0.6f * r, y - 0.7f * r, 0.14f * r + 0.5f, 0xCCFFFFFF);
                c.line(x - 0.6f * r, y - 0.7f * r, x - 0.4f * r, y - 1.0f * r, 0.14f * r + 0.5f, 0xCCFFFFFF);
                c.line(x + 0.35f * r, y - 0.4f * r, x + 0.2f * r, y - 0.7f * r, 0.14f * r + 0.5f, 0xCCFFFFFF);
                c.line(x + 0.2f * r, y - 0.7f * r, x + 0.4f * r, y - 1.0f * r, 0.14f * r + 0.5f, 0xCCFFFFFF);
                break;
            case 3: // fish
                c.fillPoly(new float[]{x + 0.6f * r, y, x + 1.2f * r, y - 0.55f * r, x + 1.2f * r, y + 0.55f * r}, 0xFF3C7FD6);
                c.fillPoly(arc(x - 0.15f * r, y, 0.95f * r, 0.58f * r, 0, 2 * Math.PI, 14), 0xFF5AA9FF);
                c.fillPoly(new float[]{x - 0.3f * r, y - 0.5f * r, x + 0.2f * r, y - 0.95f * r, x + 0.3f * r, y - 0.45f * r}, 0xFF3C7FD6);
                c.fillCircle(x - 0.6f * r, y - 0.12f * r, 0.17f * r + 0.5f, 0xFFFFFFFF);
                c.fillCircle(x - 0.62f * r, y - 0.12f * r, 0.08f * r + 0.3f, 0xFF10141A);
                break;
            case 4: // salad: white bowl, green heap
                c.fillCircle(x - 0.5f * r, y - 0.25f * r, 0.5f * r, 0xFF5BD68A);
                c.fillCircle(x + 0.05f * r, y - 0.5f * r, 0.55f * r, 0xFF3FA866);
                c.fillCircle(x + 0.6f * r, y - 0.2f * r, 0.45f * r, 0xFF5BD68A);
                c.fillCircle(x + 0.3f * r, y - 0.1f * r, 0.2f * r, 0xFFE0453F);
                c.fillPoly(arc(x, y + 0.05f * r, 1.1f * r, 0.85f * r, 0, Math.PI, 10), 0xFFE9EDF2);
                c.fillRect(x - 1.1f * r, y + 0.02f * r, 2.2f * r, 0.1f * r, 0xFFC5CCD6);
                break;
            case 5: // cake wedge
                c.fillPoly(new float[]{x - r, y - 0.75f * r, x + r, y - 0.75f * r, x, y + r}, 0xFFF7E6C4);
                c.fillRoundRect(x - r, y - 0.98f * r, 2 * r, 0.42f * r, 0.15f * r, 0xFFFF8FB1);
                c.fillPoly(new float[]{x - 0.45f * r, y - 0.1f * r, x + 0.45f * r, y - 0.1f * r, x, y + 0.5f * r}, 0xFFE9CFA0);
                c.fillCircle(x, y - 1.05f * r, 0.27f * r + 0.5f, 0xFFD64545);
                break;
            case 6: // fries
                for (int k = 0; k < 5; k++) {
                    float fx = x + (k - 2) * 0.38f * r;
                    float top = y - (0.95f - 0.12f * (k % 3)) * r;
                    c.fillRect(fx - 0.12f * r, top, 0.26f * r, y - top, 0xFFFFD25A);
                }
                c.fillPoly(new float[]{x - 0.85f * r, y - 0.1f * r, x + 0.85f * r, y - 0.1f * r, x + 0.62f * r, y + r, x - 0.62f * r, y + r}, 0xFFE0453F);
                c.fillCircle(x, y + 0.45f * r, 0.22f * r, 0xFFFFF4DC);
                break;
            default: // drink
                c.fillPoly(new float[]{x - 0.6f * r, y - 0.8f * r, x + 0.6f * r, y - 0.8f * r, x + 0.45f * r, y + r, x - 0.45f * r, y + r}, 0xFFBFE6FF);
                c.fillPoly(new float[]{x - 0.54f * r, y - 0.3f * r, x + 0.54f * r, y - 0.3f * r, x + 0.43f * r, y + 0.92f * r, x - 0.43f * r, y + 0.92f * r}, 0xFFFF9F5A);
                c.line(x + 0.15f * r, y - 0.2f * r, x + 0.5f * r, y - 1.15f * r, 0.16f * r + 0.5f, 0xFFE0453F);
                c.fillCircle(x - 0.6f * r, y - 0.8f * r, 0.3f * r, 0xFFFFE066);
                break;
        }
    }

    @Override
    void art(Gfx c, float cx0, float cy, float time) {
        // A table with a waiting guest, a thought bubble, a plate on the way.
        float s = 1.5f;
        float ty = cy + 30;
        c.fillCircle(cx0, ty, 90, 0x14FFD9A0);
        c.fillRoundRect(cx0 - 17 * s, ty - 40 * s, 34 * s, 34 * s, 7 * s, 0xFF4A3426);
        drawGuest(c, cx0, ty, s, 4, ((int) (time * 0.8f) & 1) == 0 ? 0 : 1, 0, false, false);
        c.fillRoundRect(cx0 - 46 * s, ty - 4 * s, 92 * s, 22 * s, 8 * s, 0xFFEADFC5);
        c.fillRoundRect(cx0 - 46 * s, ty + 12 * s, 92 * s, 8 * s, 3 * s, SKIRT[0]);
        float bx = cx0 + 78, by = cy - 22;
        c.fillCircle(bx - 18, by + 26, 3, 0xFFFFF4DC);
        c.fillCircle(bx - 10, by + 18, 5, 0xFFFFF4DC);
        c.fillCircle(bx, by, 26, 0xFFD9C79C);
        c.fillCircle(bx, by, 24, 0xFFFFF4DC);
        dish(c, 0, bx, by, 16);
        float u = (time * 0.6f) % 1f;
        float px = cx0 - 92 + u * 30;
        c.fillCircle(px, cy + 4, 24, 0xFFEFF2F5);
        c.fillCircle(px, cy + 4, 19, 0xFFD5DBE3);
        dish(c, 1, px, cy + 5, 15);
    }

    // ---------------------------------------------------------------- bot

    /**
     * Honest player: it reads a dish only from a bubble that is open right now, and remembers it with a
     * chance that falls with fewer skills, more tables and shorter peeks. It drags plates like a finger.
     */
    @Override
    void bot(Random r, float skill) {
        if (result != RUNNING || n == 0) return;
        float pInit = Ui.clamp(0.1f + 0.86f * skill - 0.045f * (n - 3) - 0.08f * Math.max(0, 2.0f - peekT), 0.05f, 0.97f);
        for (int i = 0; i < n; i++) {
            if (peekOpen[i] > 0 && botSeen[i] != peekSeq[i]) {
                botSeen[i] = peekSeq[i];
                float p = peekSeq[i] == 1 ? pInit : botPeeked[i] ? 0.97f : 0.1f + 0.8f * skill;
                botPeeked[i] = false;
                if (r.nextFloat() < p) botMem[i] = dish[i];
            }
        }
        if (botChkWrong >= 0 && t >= botChkAt) {
            if (wrong > botChkWrong) {
                botNo[botChkTab][botChkDish] = true;
                if (botMem[botChkTab] == botChkDish) botMem[botChkTab] = -1;
            }
            botChkWrong = -1;
        }
        float react = 0.95f - 0.65f * skill;
        if (botState == 1) {
            if (t >= botAt) {
                move(cx[botTab], cellY[botTab] + rowH * 0.6f);
                botState = 2;
                botAt = t + 0.07f;
            }
            return;
        }
        if (botState == 2) {
            if (t >= botAt) {
                up(cx[botTab], cellY[botTab] + rowH * 0.6f);
                botState = 0;
                botNext = t + react + r.nextFloat() * 0.3f;
            }
            return;
        }
        // Which plates are on the pass?
        int present = 0;
        for (int s = 0; s < SLOTS; s++) if (slotState[s] == 1) present++;
        if (present == 0) {
            botSaw = false;
            return;
        }
        if (!botSaw) {
            botSaw = true;
            botNext = Math.max(botNext, t + react * 0.6f + r.nextFloat() * 0.2f);
        }
        if (t < botNext) return;

        // Best known delivery: the most impatient owner first (skill makes it notice).
        int bs = -1, bt = -1;
        float bestPat = 1e9f;
        for (int s = 0; s < SLOTS; s++) {
            if (slotState[s] != 1) continue;
            for (int i = 0; i < n; i++) {
                if (state[i] != WAIT || botMem[i] != slotDish[s] || botNo[i][slotDish[s]]) continue;
                float key = skill > 0.45f ? pat[i] : s;
                if (key < bestPat) {
                    bestPat = key;
                    bs = s;
                    bt = i;
                }
            }
        }
        if (bs >= 0) {
            drag(bs, bt, 0);
            return;
        }
        // Nothing known: take the first plate and either work it out, peek, or guess.
        int s0 = -1;
        for (int s = 0; s < SLOTS && s0 < 0; s++) if (slotState[s] == 1) s0 = s;
        int d = slotDish[s0];
        int cands = 0, only = -1, unknownLow = -1;
        float lowPat = 1e9f;
        for (int i = 0; i < n; i++) {
            if (state[i] != WAIT || botNo[i][d]) continue;
            if (botMem[i] != -1) continue; // remembered as something else
            cands++;
            only = i;
            if (pat[i] < lowPat) {
                lowPat = pat[i];
                unknownLow = i;
            }
        }
        if (cands == 1 && skill > 0.4f) {
            drag(s0, only, 0);
            return;
        }
        if (cands > 0 && r.nextFloat() < skill * 0.9f && peeks[unknownLow] < 2 && peekOpen[unknownLow] <= 0) {
            botPeeked[unknownLow] = true;
            down(cx[unknownLow], cellY[unknownLow] + rowH * 0.6f);
            up(cx[unknownLow], cellY[unknownLow] + rowH * 0.6f);
            botNext = t + 0.3f + react * 0.5f;
            return;
        }
        // Guess among the tables not ruled out.
        int pickN = 0, pick = -1;
        for (int i = 0; i < n; i++) {
            if (state[i] != WAIT || botNo[i][d]) continue;
            if (botMem[i] != -1 && cands > 0) continue;
            if (r.nextInt(++pickN) == 0) pick = i;
        }
        if (pick < 0) {
            for (int i = 0; i < n; i++) if (state[i] == WAIT && r.nextInt(++pickN) == 0) pick = i;
        }
        if (pick >= 0) drag(s0, pick, 0);
        else botNext = t + 0.5f;
    }

    private void drag(int slot, int table, int unused) {
        botSlot = slot;
        botTab = table;
        botChkWrong = wrong;
        botChkTab = table;
        botChkDish = slotDish[slot];
        botChkAt = t + 0.7f;
        down(slotX[slot], plateY);
        move(slotX[slot] + 2, plateY + 20);
        botState = 1;
        botAt = t + 0.06f;
    }
}
