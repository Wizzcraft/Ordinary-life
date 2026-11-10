package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Supermarket cashier shift: four rounds of two customers each. A customer's items slide along the
 * belt (tap to scan, the till adds them up), then they pay with a note and the big number turns into
 * the change you owe. Tap coins and notes from the drawer until it reads zero. Fewer pieces and a
 * patience bar still full make the customer happy and the round PERFECT.
 *
 * The decision: now and then a customer says "take 5 more, make it round". Taking the coin changes
 * the change you owe, and it only pays off when the new sum needs fewer pieces. Also, an overshoot
 * must be undone, and every undo costs a second of the customer's patience.
 */
final class CashierMini extends Mini {
    private static final int[] DEN = {1, 2, 5, 10, 20, 50, 100};
    private static final int[] NOTE_PAID = {50, 100, 200};
    private static final float UNDO_COST = 0.9f, OFFER_LIFE = 2.8f, ELEGANT_LEFT = 0.4f;

    private static final int ARRIVE = 0, SCAN = 1, PAYIN = 2, CHANGE = 3, LEAVE = 4;
    private static final int CUSTOMERS = 2;

    // Customer palettes: skin, shirt, hair.
    private static final int[] SKIN = {0xFFF2C9A1, 0xFFD9A074, 0xFFB57A52, 0xFF8D5A3C};
    private static final int[] SHIRT = {0xFF5AA9FF, 0xFFE0707A, 0xFF8FCB6A, 0xFFB78CE8, 0xFFFFB45A, 0xFF4FC1B5};
    private static final int[] HAIR = {0xFF2B2118, 0xFF5A3A22, 0xFF8A6A3A, 0xFFBFC4CC, 0xFF1B1B22};

    // Personalities: 0 calm, 1 in a hurry, 2 granny, 3 teenager, 4 suit.
    private static final String[][] SAY = {
            {"בוקר טוב!", "יש לכם שקיות?", "יום יפה היום"},
            {"יאללה, אני מאחר!", "קדימה קדימה", "יש לי פגישה"},
            {"הנכד שלי אוהב את זה", "פעם זה היה זול", "תן לי לספור רגע"},
            {"וואלה, אפשר שקית?", "שניות ואני בחוץ", "מה קורה אחי"},
            {"אני צריך חשבונית", "בלי קבלה, תודה", "הכל על החברה"}};
    private static final String[] SAY_F = {"הנכד שלי אוהב את זה", "פעם זה היה זול", "תני לי לספור רגע"};

    private int round, custIdx, phase;
    private float lvl, time, phaseT;
    private int[] stars = new int[CUSTOMERS];
    private int walks;

    // Current customer.
    private int pers, skin, shirt, hair, paid, total, due, par, scanned, nItems, runTotal;
    private final int[] price = new int[4];
    private final float[] itemX = new float[4], outT = new float[4];
    private final int[] itemKind = new int[4];
    private float patience, patMax, scanIdle, custX, flash, over, doneT, offerT;
    private boolean won, walked, elegant;
    private int offerV, offerState; // state: 0 none, 1 showing, 2 taken, 3 expired
    private float offerAt;
    private String bubble = "";
    private float bubbleT;
    private final int[] given = new int[40];
    private int nGiven, sumGiven;
    private final float[] press = new float[7];
    private float undoPress, offerPulse;
    private int nextRoundResult = RUNNING;

    // Geometry, recomputed in draw() and used by touches.
    private float zoneT = 166, zoneB = 300, dispY = 300, dispH = 58, trayY = 365, trayH = 32, drawY = 400, rowH = 48;
    private final float[] bx = new float[7], by = new float[7], bw = new float[7], bh = new float[7];
    private float undoX, undoY, undoW, undoH, small = 1;
    private boolean laidOut;
    private float zoneSc = 1;

    /** Where the customer's extra coin sits on the counter. */
    private float offerY() {
        return zoneB - 24 * Math.min(zoneSc, 1.6f) - 15 * Math.min(zoneSc, 1.5f);
    }

    // Bot state.
    private float botNext;
    private boolean botDecidedOffer;

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("קופאי", "קופאית");
    }

    @Override
    String howTo() {
        return "סורקים את הקניות בלחיצה, ואז נותנים עודף מהמגירה עד שהמספר הגדול מגיע לאפס. "
                + "פחות מטבעות ומהר יותר זה מושלם.";
    }

    // ---------------------------------------------------------------- rules

    /** Fewest pieces for an amount; the denominations are canonical, so greedy is optimal. */
    private static int pieces(int amount) {
        int n = 0;
        for (int i = DEN.length - 1; i >= 0; i--) {
            n += amount / DEN[i];
            amount %= DEN[i];
        }
        return n;
    }

    private static int largestUpTo(int amount) {
        for (int i = DEN.length - 1; i >= 0; i--) if (DEN[i] <= amount) return i;
        return -1;
    }

    @Override
    void begin(int r, float difficulty) {
        round = r;
        lvl = Ui.clamp(r / 3f * 0.7f + difficulty * 0.5f, 0, 1);
        custIdx = 0;
        walks = 0;
        for (int i = 0; i < CUSTOMERS; i++) stars[i] = 0;
        nextRoundResult = RUNNING;
        newCustomer();
    }

    private void newCustomer() {
        Random r = h.rnd();
        pers = (round == 0 && custIdx == 0) ? 0 : r.nextInt(5);
        skin = SKIN[r.nextInt(SKIN.length)];
        shirt = SHIRT[r.nextInt(SHIRT.length)];
        hair = pers == 2 ? HAIR[3] : HAIR[r.nextInt(3) == 0 ? 4 : r.nextInt(3)];

        // Pick what they pay with and how much change comes back.
        int pidx = lvl < 0.25f ? 0 : lvl < 0.55f ? r.nextInt(2) : lvl < 0.8f ? r.nextInt(3) : 1 + r.nextInt(2);
        paid = NOTE_PAID[pidx];
        int cHi = Math.min((int) (44 + 100 * lvl), paid - 3);
        int cLo = Math.max(7 + (int) (12 * lvl), paid / 4);
        if (cLo > cHi) cLo = Math.max(3, cHi - 10);
        int minP = lvl < 0.5f ? 3 : 4;
        int c = cLo + r.nextInt(cHi - cLo + 1);
        for (int tries = 0; tries < 30 && pieces(c) < minP; tries++) c = cLo + r.nextInt(cHi - cLo + 1);
        total = paid - c;
        due = c;
        par = pieces(c);

        // Optional "make it round" offer.
        offerV = 0;
        offerState = 0;
        float offerP = lvl < 0.2f ? 0 : pers == 2 ? 0.85f : 0.5f;
        if (r.nextFloat() < offerP) {
            boolean wantGood = r.nextFloat() < 0.6f;
            int[] vs = {1, 2, 5, 10};
            int start = r.nextInt(4);
            for (int k = 0; k < 4; k++) {
                int v = vs[(start + k) % 4];
                boolean good = pieces(c + v) < pieces(c);
                if (good == wantGood) {
                    offerV = v;
                    break;
                }
            }
            if (offerV > 0) par = Math.min(par, pieces(c + offerV));
        }
        offerAt = 0.5f;

        // Split the total into 2-3 item prices.
        nItems = total >= 6 && (lvl > 0.5f || lvl > 0.2f && r.nextBoolean()) ? 3 : 2;
        int left = total;
        for (int k = 0; k < nItems; k++) {
            int rest = nItems - 1 - k;
            int p = rest == 0 ? left : 1 + r.nextInt(Math.max(1, Math.min(left - rest, left * 2 / (rest + 1))));
            p = Math.max(1, Math.min(p, left - rest));
            price[k] = p;
            left -= p;
            itemKind[k] = r.nextInt(6);
            itemX[k] = -40 - k * 60;
            outT[k] = -1;
        }
        scanned = 0;
        runTotal = 0;
        float hurry = pers == 1 ? 0.82f : pers == 2 ? 1.2f : 1f;
        patMax = (2.6f + 0.68f * par) * (1 - 0.28f * lvl) * hurry;
        patience = patMax;
        nGiven = 0;
        sumGiven = 0;
        over = 0;
        won = walked = elegant = false;
        scanIdle = 0;
        custX = -70;
        flash = 0;
        phase = ARRIVE;
        phaseT = 0;
        String[] pool = SAY[pers];
        bubble = pool[r.nextInt(pool.length)];
        if (pers == 2 && h.female()) bubble = SAY_F[r.nextInt(SAY_F.length)];
        bubbleT = 2.2f;
        botNext = 0;
        botDecidedOffer = false;
    }

    // ---------------------------------------------------------------- update

    @Override
    int update(float dt) {
        time += dt;
        for (int i = 0; i < press.length; i++) if (press[i] > 0) press[i] -= dt;
        if (undoPress > 0) undoPress -= dt;
        if (flash > 0) flash -= dt;
        if (over > 0 && sumGiven <= due) over = 0;
        if (bubbleT > 0) bubbleT -= dt;
        phaseT += dt;
        for (int k = 0; k < nItems; k++) {
            if (outT[k] >= 0) outT[k] += dt;
            else itemX[k] += (slotX(k - scanned) - itemX[k]) * Math.min(1, dt * 9);
        }
        switch (phase) {
            case ARRIVE:
                custX += (66 - custX) * Math.min(1, dt * 8);
                if (phaseT > 0.35f) setPhase(SCAN);
                break;
            case SCAN:
                scanIdle += dt;
                if (scanIdle > 1.2f) scanNext(); // a patient scanner: the belt never stalls the shift
                break;
            case PAYIN:
                if (phaseT > 0.35f) {
                    setPhase(CHANGE);
                    bubble = paid == 200 ? "יש לי רק מאתיים" : paid == 100 ? "הנה מאה" : "הנה חמישים";
                    bubbleT = 1.6f;
                    h.sfx(Sfx.COIN);
                }
                break;
            case CHANGE:
                patience -= dt;
                updateOffer(dt);
                if (patience <= 0) walkOut();
                break;
            default:
                custX -= (phaseT * phaseT * 260 + 20) * dt;
                if (phaseT > 0.5f) {
                    custIdx++;
                    if (custIdx >= CUSTOMERS) return finishRound();
                    newCustomer();
                }
        }
        return RUNNING;
    }

    private void setPhase(int p) {
        phase = p;
        phaseT = 0;
        botNext = time + 0.35f;
    }

    private float slotX(int slot) {
        return 312 - 58 * Math.max(0, slot);
    }

    private void scanNext() {
        if (scanned >= nItems) return;
        scanIdle = 0;
        int k = scanned++;
        outT[k] = 0;
        runTotal += price[k];
        flash = 0.25f;
        h.sfx(Sfx.BEEP);
        h.burst(312, zoneB - 36, 0xFFFF6B6B, 4);
        if (scanned == nItems) {
            h.text(W / 2, dispY - 6, "סה״כ " + Ui.nis(total), Ui.ACCENT);
            setPhase(PAYIN);
            phaseT = -0.25f; // let the last beep breathe before the note lands
        }
    }

    private void updateOffer(float dt) {
        if (offerV == 0) return;
        if (offerState == 0 && phaseT > offerAt) {
            offerState = 1;
            offerT = OFFER_LIFE;
            bubble = h.t("קח", "קחי") + " עוד " + offerV + ", שיהיה עגול";
            bubbleT = OFFER_LIFE;
            h.sfx(Sfx.DING);
        } else if (offerState == 1) {
            offerT -= dt;
            offerPulse += dt;
            if (offerT <= 0) {
                offerState = 3;
                bubble = "טוב, לא";
                bubbleT = 1;
            }
        }
    }

    private void walkOut() {
        walked = true;
        walks++;
        stars[custIdx] = 0;
        h.text(70, zoneT + 20, "הלך!", Ui.BAD);
        h.shake(6);
        h.sfx(Sfx.BAD);
        setPhase(LEAVE);
    }

    private int finishRound() {
        int sum = 0;
        for (int s : stars) sum += s;
        if (walks == 0 && sum >= 3) return PERFECT;
        if (sum >= 2) return OK;
        return MISS;
    }

    // ---------------------------------------------------------------- touch

    @Override
    void down(float x, float y) {
        if (!laidOut) return;
        if (phase == SCAN) {
            if (y >= zoneT && y <= dispY) scanNext();
            return;
        }
        if (phase != CHANGE) return;
        if (offerState == 1 && Math.abs(x - 292) < 40 && Math.abs(y - offerY()) < 44) {
            takeOffer();
            return;
        }
        if (Ui.in(x, y, undoX, undoY, undoW, undoH) || (y >= trayY && y <= trayY + trayH)) {
            undo();
            return;
        }
        for (int i = 0; i < 7; i++) {
            if (Ui.in(x, y, bx[i] - 3, by[i] - 3, bw[i] + 6, bh[i] + 6)) {
                give(i);
                return;
            }
        }
    }

    private void takeOffer() {
        offerState = 2;
        due += offerV;
        flash = 0.3f;
        h.text(292, offerY() - 34, "+" + offerV, Ui.ACCENT);
        h.burst(292, offerY(), 0xFFE3E6EA, 6);
        h.sfx(Sfx.COIN);
        bubble = "בבקשה";
        bubbleT = 0.8f;
    }

    private void give(int i) {
        if (nGiven >= given.length) return;
        given[nGiven++] = DEN[i];
        sumGiven += DEN[i];
        press[i] = 0.14f;
        h.sfx(DEN[i] >= 20 ? Sfx.WHOOSH : Sfx.COIN);
        h.burst(bx[i] + bw[i] / 2, by[i] + bh[i] / 2, DEN[i] >= 20 ? noteColor(DEN[i]) : coinColor(DEN[i]), 4);
        if (sumGiven == due) {
            finishCustomer();
        } else if (sumGiven > due) {
            over = 1;
            h.text(W / 2, dispY - 6, "יותר מדי!", Ui.BAD);
            h.shake(4);
            h.sfx(Sfx.BAD);
        }
    }

    private void undo() {
        if (nGiven == 0) return;
        sumGiven -= given[--nGiven];
        undoPress = 0.15f;
        patience -= UNDO_COST;
        h.text(undoX + undoW / 2, undoY - 6, "-" + UNDO_COST + " שנ'", Ui.ORANGE);
        h.sfx(Sfx.TICK);
    }

    private void finishCustomer() {
        elegant = nGiven <= par && patience / patMax >= ELEGANT_LEFT;
        stars[custIdx] = elegant ? 2 : 1;
        won = true;
        if (elegant) {
            h.text(W / 2, dispY - 6, "מושלם!", Ui.ACCENT);
            h.burst(W / 2, dispY + dispH / 2, Ui.ACCENT, 14);
            h.sfx(Sfx.PERFECT);
        } else {
            h.text(W / 2, dispY - 6, nGiven > par ? "אפשר היה פחות" : "לאט", Ui.GOOD);
            h.burst(W / 2, dispY + dispH / 2, Ui.GOOD, 8);
            h.sfx(Sfx.GOOD);
        }
        bubble = elegant ? "תודה רבה!" : "יאללה, להתראות";
        bubbleT = 0.9f;
        setPhase(LEAVE);
        phaseT = -0.15f;
    }

    @Override
    void pause() {
        // Nothing to settle: no money moves, and an interrupted round replays from begin().
        over = 0;
    }

    // ---------------------------------------------------------------- drawing

    private void layout(float t, float b) {
        float H = b - t;
        float u = Ui.clamp((H - 298) / 200f, 0, 1);
        small = 1 + u * 0.7f;
        rowH = 48 + 14 * u;
        float drawerH = 2 * rowH + 10;
        trayH = 32 + 10 * u;
        dispH = 58 + 34 * u;
        drawY = b - drawerH - 4;
        trayY = drawY - trayH - 5;
        dispY = trayY - dispH - 5;
        zoneT = t;
        zoneB = dispY - 3;
        zoneSc = Ui.clamp((zoneB - zoneT) / 92f, 0.9f, 2.0f);
        float cw = (W - 24 - 18) / 4f;
        for (int i = 0; i < 4; i++) {
            bx[i] = 12 + i * (cw + 6);
            by[i] = drawY;
            bw[i] = cw;
            bh[i] = rowH;
        }
        float nw = (W - 24 - 12) / 3f;
        for (int i = 4; i < 7; i++) {
            bx[i] = 12 + (i - 4) * (nw + 6);
            by[i] = drawY + rowH + 6;
            bw[i] = nw;
            bh[i] = rowH;
        }
        undoW = 70;
        undoH = trayH;
        undoX = W - 12 - undoW;
        undoY = trayY;
        laidOut = true;
    }

    private static int coinColor(int v) {
        return v == 1 ? 0xFFB8BEC8 : v == 2 ? 0xFF8FB4D8 : v == 5 ? 0xFFE9ECEF : 0xFFE0A93C;
    }

    private static int noteColor(int v) {
        return v == 20 ? 0xFFE4626E : v == 50 ? 0xFF5FB36F : v == 100 ? 0xFFE59A4A : 0xFF5A8FE0;
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawZone(c);
        drawDisplay(c);
        drawTray(c);
        drawDrawer(c);
    }

    private void drawZone(Gfx c) {
        float zt = zoneT, zb = zoneB, zh = zb - zt;
        float sc = Ui.clamp(zh / 92f, 0.9f, 2.0f);
        float counterH = 24 * Math.min(sc, 1.6f);
        float cTop = zb - counterH;
        c.fillRoundRect(6, zt, W - 12, zh, 10, 0xFF181E27);
        // Shelves on the back wall.
        for (int i = 0; i < 3; i++) {
            float sx = 126 + i * 76;
            c.fillRect(sx, zt + 12, 60, 3, 0xFF2A323E);
            c.fillRect(sx + 6, zt + 4, 10, 8, 0xFF3A4656);
            c.fillRect(sx + 22, zt + 6, 8, 6, 0xFF4A3E3A);
            c.fillRect(sx + 36, zt + 3, 12, 9, 0xFF35485A);
        }
        // The endless queue behind the counter.
        float qs = 1.35f * Math.min(sc, 1.7f);
        for (int i = 0; i < 2; i++) {
            float bob = (float) Math.sin(time * 2.2 + i * 2) * 0.8f;
            Ui.person(c, 124 + i * 28, cTop + 2 + bob, qs, i == 1, 0, false, i == 0 ? 0xFF3E4A5C : 0xFF4C3E58);
        }
        // Counter and belt.
        c.fillRect(6, cTop, W - 12, counterH, 0xFF3A4350);
        float beltX = 104;
        c.fillRoundRect(beltX, cTop - 3, W - 6 - beltX, 9, 3, 0xFF262C35);
        float off = (time * 24) % 14;
        for (float x = beltX + 4 + off; x < W - 12; x += 14) c.fillRect(x, cTop - 1, 4, 5, 0xFF303844);
        // Scanner glass.
        c.fillRoundRect(296, cTop - 5, 34, 7, 3, 0xFF59636F);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 6);
        c.fillRect(300, cTop - 4, 26, 2, ((int) (120 + 100 * pulse) << 24) | 0xFF4040);
        if (flash > 0) c.fillCircle(313, cTop - 4, 14 * flash / 0.25f + 4, 0x55FF6B6B);

        // Customer.
        float breathe = (float) Math.sin(time * 2.6 + custIdx) * 1.2f;
        drawCustomer(c, custX, cTop, sc, breathe);

        // Items on the belt.
        for (int k = 0; k < nItems; k++) {
            if (outT[k] > 0.45f) continue;
            float ix = outT[k] >= 0 ? 312 + outT[k] * 90 : itemX[k];
            float iy = cTop - 3 - (outT[k] >= 0 ? outT[k] * 30 : 0);
            boolean next = outT[k] < 0 && k == scanned && phase == SCAN;
            if (next) c.fillCircle(ix, iy - 12 * sc * 0.8f, 22 * sc * 0.8f, 0x22FFC857);
            drawItem(c, itemKind[k], ix, iy, sc * 0.85f, outT[k] >= 0 ? 0.55f : 1);
            if (outT[k] < 0) {
                String p = Ui.nis(price[k]);
                float tw = c.textWidth(p, 12) + 8;
                c.fillRoundRect(ix - tw / 2, iy + 1, tw, 14, 4, 0xFF0B0E12);
                c.text(p, ix, iy + 12, 12, next ? Ui.ACCENT : Ui.INK, Gfx.CENTER);
            }
        }
        if (phase == SCAN && scanned == 0 && phaseT > 0.4f) {
            c.text("לוחצים לסרוק", 220, cTop - 44 * sc, 13, Ui.ACCENT, Gfx.CENTER);
        }
        // Paid note and the offered coin lie on the counter.
        if (phase >= PAYIN && !walked || phase == CHANGE) drawPaidNote(c, cTop, sc);
        if (phase == CHANGE && offerState == 1) drawOffer(c, cTop);

        // Speech bubble.
        if (bubbleT > 0 && phase != ARRIVE) {
            float tw = Math.max(70, c.textWidth(bubble, 13) + 22);
            float bxx = Ui.clamp(custX + 54, 8, W - tw - 8);
            float byy = zt + 6;
            float a = Math.min(1, bubbleT * 3);
            int bg = ((int) (235 * a) << 24) | 0xE9EDF2, ink = ((int) (255 * a) << 24) | 0x10141A;
            c.fillRoundRect(bxx, byy, tw, 24, 9, bg);
            c.fillPoly(new float[]{bxx + 8, byy + 22, bxx + 18, byy + 22, bxx + 3, byy + 32}, bg);
            c.text(bubble, bxx + tw / 2, byy + 17, 13, ink, Gfx.CENTER);
        }
    }

    private void drawPaidNote(Gfx c, float cTop, float sc) {
        float nx = 214, ny = cTop - 10 * Math.min(sc, 1.2f);
        float k = Math.min(sc, 1.2f);
        float slide = phase == PAYIN ? Ui.clamp(phaseT / 0.5f, 0, 1) : 1;
        nx = 120 + (nx - 120) * slide;
        ny -= (1 - slide) * 10;
        c.fillPoly(new float[]{nx - 36 * k, ny - 20 * k, nx + 34 * k, ny - 24 * k, nx + 38 * k, ny + 2 * k, nx - 32 * k, ny + 5 * k},
                noteColor(paid));
        c.fillPoly(new float[]{nx - 30 * k, ny - 15 * k, nx + 29 * k, ny - 18 * k, nx + 32 * k, ny - 3 * k, nx - 27 * k, ny},
                Ui.darken(noteColor(paid), 0.82f));
        c.text("" + paid, nx + 2, ny - 4 * k, 16 * k + 2, 0xFFFFFFFF, Gfx.CENTER);
    }

    private void drawOffer(Gfx c, float cTop) {
        float ox = 292, oy = offerY();
        float pulse = 1 + 0.08f * (float) Math.sin(offerPulse * 12);
        float r = 15 * Math.min(zoneSc, 1.3f) * pulse;
        c.fillCircle(ox, oy, r + 7, 0x33FFC857);
        drawCoin(c, ox, oy, r, offerV);
        // Countdown ring of dots.
        int dots = 14;
        int lit = (int) Math.ceil(dots * offerT / OFFER_LIFE);
        for (int s = 0; s < lit; s++) {
            double a = -Math.PI / 2 + s * Math.PI * 2 / dots;
            c.fillCircle(ox + (float) Math.cos(a) * (r + 10), oy + (float) Math.sin(a) * (r + 10), 2.2f, Ui.ACCENT);
        }
    }

    private void drawCustomer(Gfx c, float x, float cTop, float sc, float breathe) {
        float s = sc;
        float f = patience / patMax;
        boolean angry = phase == CHANGE && f < 0.3f || walked;
        boolean happy = won;
        float shoulderY = cTop - 4 * s + breathe * 0.4f;
        int sh = shirt;
        // Shoulders and neck.
        c.fillPoly(new float[]{x - 26 * s, cTop + 2, x - 20 * s, shoulderY - 14 * s, x - 8 * s, shoulderY - 20 * s,
                x + 8 * s, shoulderY - 20 * s, x + 20 * s, shoulderY - 14 * s, x + 26 * s, cTop + 2}, sh);
        if (pers == 4) c.fillPoly(new float[]{x - 4 * s, shoulderY - 20 * s, x + 4 * s, shoulderY - 20 * s, x, shoulderY - 6 * s}, 0xFFE9EDF2);
        float hy = shoulderY - 33 * s + breathe;
        if (pers == 2) c.fillCircle(x, hy - 12 * s, 7 * s, hair); // bun
        c.fillCircle(x, hy, 13 * s, skin);
        // Hair / hats.
        if (pers == 1) {
            c.fillPoly(new float[]{x - 13 * s, hy - 2 * s, x - 11 * s, hy - 11 * s, x + 11 * s, hy - 11 * s, x + 13 * s, hy - 2 * s,
                    x + 8 * s, hy - 7 * s, x - 8 * s, hy - 7 * s}, hair);
        } else if (pers == 3) {
            c.fillPoly(new float[]{x - 14 * s, hy - 1 * s, x - 11 * s, hy - 14 * s, x + 11 * s, hy - 14 * s, x + 14 * s, hy - 1 * s,
                    x + 10 * s, hy - 8 * s, x - 10 * s, hy - 8 * s}, 0xFFFF9F5A);
            c.line(x - 14 * s, hy + 2 * s, x - 14 * s, hy + 8 * s, 3 * s, 0xFF10141A);
            c.line(x + 14 * s, hy + 2 * s, x + 14 * s, hy + 8 * s, 3 * s, 0xFF10141A);
            c.line(x - 14 * s, hy - 4 * s, x + 14 * s, hy - 4 * s, 1.6f * s, 0xFF10141A);
        } else {
            c.fillPoly(new float[]{x - 13 * s, hy, x - 12 * s, hy - 9 * s, x, hy - 14 * s, x + 12 * s, hy - 9 * s, x + 13 * s, hy,
                    x + 7 * s, hy - 7 * s, x - 7 * s, hy - 7 * s}, hair);
        }
        // Face.
        float ey = hy + 1 * s;
        boolean blink = (time * 0.8f + custIdx * 0.37f) % 3.4f < 0.12f;
        if (!blink) {
            c.fillCircle(x - 5 * s, ey, 1.7f * s, 0xFF10141A);
            c.fillCircle(x + 5 * s, ey, 1.7f * s, 0xFF10141A);
        } else {
            c.line(x - 7 * s, ey, x - 3 * s, ey, 1.2f * s, 0xFF10141A);
            c.line(x + 3 * s, ey, x + 7 * s, ey, 1.2f * s, 0xFF10141A);
        }
        if (pers == 4 || pers == 2) {
            c.line(x - 9 * s, ey - 3 * s, x - 1 * s, ey - 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x - 9 * s, ey + 3 * s, x - 1 * s, ey + 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x + 1 * s, ey - 3 * s, x + 9 * s, ey - 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x + 1 * s, ey + 3 * s, x + 9 * s, ey + 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x - 9 * s, ey - 3 * s, x - 9 * s, ey + 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x - 1 * s, ey - 3 * s, x - 1 * s, ey + 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x + 1 * s, ey - 3 * s, x + 1 * s, ey + 3 * s, 1.1f * s, 0xFF2A323E);
            c.line(x + 9 * s, ey - 3 * s, x + 9 * s, ey + 3 * s, 1.1f * s, 0xFF2A323E);
        }
        if (angry) {
            c.line(x - 8 * s, ey - 5 * s, x - 2 * s, ey - 3 * s, 1.5f * s, 0xFF10141A);
            c.line(x + 8 * s, ey - 5 * s, x + 2 * s, ey - 3 * s, 1.5f * s, 0xFF10141A);
            c.line(x - 4 * s, ey + 8 * s, x + 4 * s, ey + 8 * s, 1.5f * s, 0xFF10141A);
            if (walked) c.fillCircle(x + 15 * s, hy - 9 * s, 2.5f * s, Ui.BAD);
        } else if (happy) {
            c.line(x - 4 * s, ey + 6 * s, x, ey + 8.5f * s, 1.5f * s, 0xFF10141A);
            c.line(x, ey + 8.5f * s, x + 4 * s, ey + 6 * s, 1.5f * s, 0xFF10141A);
        } else {
            c.line(x - 3 * s, ey + 7 * s, x + 3 * s, ey + 7 * s, 1.4f * s, 0xFF10141A);
        }
    }

    private static void drawItem(Gfx c, int kind, float x, float y, float s, float alpha) {
        int a = ((int) (255 * alpha)) << 24;
        switch (kind) {
            case 0: // milk carton
                c.fillRect(x - 8 * s, y - 24 * s, 16 * s, 24 * s, a | 0xEEF2F6);
                c.fillPoly(new float[]{x - 8 * s, y - 24 * s, x, y - 31 * s, x + 8 * s, y - 24 * s}, a | 0xC9D3DE);
                c.fillRect(x - 8 * s, y - 15 * s, 16 * s, 6 * s, a | 0x5AA9FF);
                break;
            case 1: // can
                c.fillRoundRect(x - 8 * s, y - 20 * s, 16 * s, 20 * s, 3 * s, a | 0xD9604A);
                c.fillRect(x - 8 * s, y - 13 * s, 16 * s, 5 * s, a | 0xF2E3C0);
                break;
            case 2: // bread
                c.fillRoundRect(x - 14 * s, y - 15 * s, 28 * s, 15 * s, 7 * s, a | 0xD79A52);
                c.line(x - 6 * s, y - 12 * s, x - 3 * s, y - 4 * s, 1.3f * s, a | 0xA8692E);
                c.line(x + 1 * s, y - 12 * s, x + 4 * s, y - 4 * s, 1.3f * s, a | 0xA8692E);
                break;
            case 3: // bottle
                c.fillRoundRect(x - 6 * s, y - 22 * s, 12 * s, 22 * s, 4 * s, a | 0x4FC1B5);
                c.fillRect(x - 2.5f * s, y - 30 * s, 5 * s, 9 * s, a | 0x4FC1B5);
                c.fillRect(x - 3 * s, y - 32 * s, 6 * s, 3 * s, a | 0xE9EDF2);
                break;
            case 4: // cereal box
                c.fillRect(x - 9 * s, y - 28 * s, 18 * s, 28 * s, a | 0xFFB45A);
                c.fillCircle(x, y - 15 * s, 5 * s, a | 0xFFF1C9);
                break;
            default: // apple
                c.fillCircle(x, y - 9 * s, 9 * s, a | 0xE0505A);
                c.line(x, y - 18 * s, x + 2 * s, y - 23 * s, 1.5f * s, a | 0x6B4A2A);
                c.fillPoly(new float[]{x + 2 * s, y - 20 * s, x + 8 * s, y - 24 * s, x + 6 * s, y - 18 * s}, a | 0x5BD68A);
        }
    }

    private void drawDisplay(Gfx c) {
        float y = dispY, hgt = dispH;
        float u = Ui.clamp((hgt - 58) / 34f, 0, 1);
        boolean losing = phase == CHANGE && patience / patMax < 0.3f;
        c.fillRoundRect(8, y, W - 16, hgt, 10, 0xFF2C3644);
        c.fillRoundRect(12, y + 4, W - 24, hgt - 8, 8, 0xFF080C0A);
        // Patience strip along the top edge of the screen.
        if (phase == CHANGE || phase == LEAVE && !won) {
            float f = Ui.clamp(patience / patMax, 0, 1);
            Ui.bar(c, 16, y + 6, W - 32, 5, f, f > 0.5f ? Ui.GOOD : f > 0.25f ? Ui.ORANGE : Ui.BAD);
        }
        int amber = 0xFFFFB84D, green = 0xFF5BF08A;
        float numSize = 34 + 12 * u;
        String label;
        int val;
        int col = amber;
        if (phase == CHANGE || phase == LEAVE) {
            val = due - sumGiven;
            label = val < 0 ? "יותר מדי" : val == 0 ? "מעולה" : "עודף לתת";
            col = val < 0 ? Ui.BAD : val == 0 ? green : amber;
            if (val < 0 && ((int) (time * 6) & 1) == 0) col = 0xFFFF9A9A;
        } else if (phase == PAYIN) {
            val = total;
            label = "סה״כ";
        } else {
            val = runTotal;
            label = "סה״כ";
        }
        float cy = y + hgt - 8 - (hgt - 58) * 0.25f;
        c.text(label, 244, y + 22 + 6 * u, 12 + 2 * u, 0xFF7FA08A, Gfx.CENTER);
        String num = (val < 0 ? "-" : "") + "₪" + Math.abs(val);
        c.text(num, 244, cy, numSize, col, Gfx.CENTER);
        // Small ledger on the left.
        float lx = 28;
        c.text("סה״כ " + Ui.nis(phase >= PAYIN ? total : runTotal), lx, y + 26 + 8 * u, 14, 0xFFB8D8C0, Gfx.LEFT);
        if (phase >= CHANGE || phase == PAYIN && phaseT > 0.2f) {
            c.text("שילם " + Ui.nis(paid), lx, y + 46 + 14 * u, 14, noteColor(paid), Gfx.LEFT);
        }
        if (losing && ((int) (time * 5) & 1) == 0) c.strokeRoundRect(12, y + 4, W - 24, hgt - 8, 8, 2, Ui.BAD);
        if (over > 0) c.strokeRoundRect(12, y + 4, W - 24, hgt - 8, 8, 2, Ui.BAD);
    }

    private void drawTray(Gfx c) {
        float y = trayY, hgt = trayH;
        c.fillRoundRect(8, y, W - 16, hgt, 8, 0xFF1E2530);
        float ax = 16, aw = undoX - 8 - ax;
        if (nGiven == 0) {
            if (phase == CHANGE) c.text("הלקוח מחכה לעודף", ax + aw / 2, y + hgt / 2 + 4, 12, Ui.DIM, Gfx.CENTER);
        } else {
            float step = Math.min(26, (aw - 24) / Math.max(1, nGiven));
            float x0 = ax + 14;
            for (int i = 0; i < nGiven; i++) {
                int v = given[i];
                float px = x0 + i * step, py = y + hgt / 2;
                if (v >= 20) {
                    c.fillRoundRect(px - 10, py - 7, 24, 14, 2, noteColor(v));
                    c.fillRect(px - 8, py - 5, 20, 10, Ui.darken(noteColor(v), 0.85f));
                } else {
                    c.fillCircle(px + 2, py, v == 10 ? 9 : v == 5 ? 8.5f : 7.5f, coinColor(v));
                }
            }
            c.text(nGiven + "", ax + aw - 2, y + hgt / 2 + 4, 12, Ui.DIM, Gfx.RIGHT);
        }
        boolean can = phase == CHANGE && nGiven > 0;
        c.fillRoundRect(undoX, undoY, undoW, undoH, 8, undoPress > 0 ? Ui.ORANGE : can ? 0xFF3A4656 : 0xFF222A35);
        c.strokeRoundRect(undoX, undoY, undoW, undoH, 8, 1.5f, can ? Ui.DIM : 0xFF2C3644);
        c.text("בטל", undoX + undoW / 2, undoY + undoH / 2 + 5, 14, can ? Ui.INK : Ui.DIM, Gfx.CENTER);
        if (over > 0 && ((int) (time * 5) & 1) == 0) c.strokeRoundRect(undoX, undoY, undoW, undoH, 8, 2.5f, Ui.ORANGE);
    }

    private void drawDrawer(Gfx c) {
        float dh = 2 * rowH + 10;
        c.fillRoundRect(6, drawY - 4, W - 12, dh + 8, 10, 0xFF3A4350);
        for (int i = 0; i < 7; i++) {
            float x = bx[i], y = by[i], w = bw[i], hh = bh[i];
            boolean pr = press[i] > 0;
            c.fillRoundRect(x, y, w, hh, 8, pr ? 0xFF4A586B : 0xFF121820);
            float cx = x + w / 2, cy = y + hh / 2;
            float pop = pr ? 1.1f : 1;
            if (DEN[i] < 20) {
                float r = (hh / 2 - 4) * (0.62f + 0.38f * (i / 3f)) * pop;
                drawCoin(c, cx, cy, r, DEN[i]);
            } else {
                float nw = (w - 22) * pop, nh = (hh - 12) * pop;
                c.fillRoundRect(cx - nw / 2, cy - nh / 2, nw, nh, 4, noteColor(DEN[i]));
                c.fillRoundRect(cx - nw / 2 + 4, cy - nh / 2 + 4, nw - 8, nh - 8, 2, Ui.darken(noteColor(DEN[i]), 0.86f));
                c.fillCircle(cx - nw / 2 + 14, cy, nh * 0.26f, Ui.darken(noteColor(DEN[i]), 0.7f));
                c.text("" + DEN[i], cx + 8, cy + 8, 24, 0xFFFFFFFF, Gfx.CENTER);
            }
        }
    }

    private static void drawCoin(Gfx c, float cx, float cy, float r, int v) {
        int col = coinColor(v);
        c.fillCircle(cx, cy, r, Ui.darken(col, 0.7f));
        c.fillCircle(cx, cy, r - 1.6f, col);
        if (v == 10) {
            c.fillCircle(cx, cy, r * 0.62f, 0xFFD6DAE0);
        } else if (v == 5) {
            for (int k = 0; k < 10; k++) {
                double a = k * Math.PI / 5;
                c.fillCircle(cx + (float) Math.cos(a) * (r - 3), cy + (float) Math.sin(a) * (r - 3), 1.1f, 0xFF8A94A3);
            }
        } else if (v == 2) {
            c.fillCircle(cx, cy, r * 0.74f, Ui.darken(col, 0.9f));
        }
        c.text("" + v, cx, cy + r * 0.36f, Math.max(12, r * 1.05f), 0xFF10141A, Gfx.CENTER);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        c.fillRoundRect(cx - 70, cy - 8, 140, 52, 8, 0xFF3A4350);
        c.fillRoundRect(cx - 52, cy - 52, 104, 40, 8, 0xFF080C0A);
        c.text("₪" + (13 + (int) (t * 2) % 5), cx, cy - 20, 30, 0xFFFFB84D, Gfx.CENTER);
        c.fillRoundRect(cx - 62, cy + 8, 124, 30, 5, 0xFF121820);
        drawCoin(c, cx - 38, cy + 23, 11, 5);
        drawCoin(c, cx - 8, cy + 23, 13, 10);
        c.fillRoundRect(cx + 16, cy + 12, 40, 22, 3, noteColor(20));
        c.text("20", cx + 36, cy + 29, 14, 0xFFFFFFFF, Gfx.CENTER);
    }

    // ---------------------------------------------------------------- bot

    @Override
    void bot(Random r, float skill) {
        if (r == null) return;
        if (phase == SCAN) {
            if (time >= botNext) {
                botNext = time + 0.2f + (1 - skill) * 0.5f + r.nextFloat() * 0.15f;
                down(220, zoneT + 30);
            }
            return;
        }
        if (phase != CHANGE || time < botNext) return;
        float gap = 0.62f - 0.34f * skill + r.nextFloat() * 0.14f;
        botNext = time + gap;
        // The offer: weigh whether the rounder sum needs fewer pieces.
        if (offerState == 1 && !botDecidedOffer) {
            botDecidedOffer = true;
            boolean good = pieces(due + offerV) < pieces(due);
            boolean sees = r.nextFloat() < 0.2f + 0.8f * skill;
            if (sees ? good : r.nextBoolean()) {
                down(292, offerY());
                return;
            }
        }
        if (sumGiven > due) {
            if (r.nextFloat() < skill * 0.95f) down(undoX + 5, undoY + 5);
            else if (r.nextFloat() < 0.5f) tapDenom(r, r.nextInt(7));
            return;
        }
        int rem = due - sumGiven;
        int pick;
        float smart = skill * skill * 0.15f + skill * 0.8f;
        if (r.nextFloat() < smart) {
            pick = largestUpTo(rem);
        } else if (r.nextFloat() < 0.35f + 0.65f * skill) {
            // A willing but unpolished player: any piece that fits.
            pick = r.nextInt(largestUpTo(rem) + 1);
        } else {
            pick = r.nextInt(7);
        }
        tapDenom(r, pick);
    }

    private void tapDenom(Random r, int i) {
        down(bx[i] + bw[i] / 2, by[i] + bh[i] / 2);
    }
}
