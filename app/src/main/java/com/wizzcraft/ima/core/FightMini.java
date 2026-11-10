package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Street scuffle: a local tough blocks the way out of the alley. One bout (about 25 s), played as a
 * reading duel. He telegraphs every move with a wind-up: a swing from the left or the right (dodge
 * to the other side) or a shove (cannot be dodged, only blocked). A clean dodge opens a short
 * window to hit back: tap for quick jabs, or HOLD to charge a heavy counter that needs a longer
 * opening and fumbles if the opening closes first. Blocking works against anything but costs
 * stamina and gives no counter. Both sides have a stamina bar. MISS = you ran out first (robbed),
 * OK = you held your own, PERFECT = you won without being hit once.
 *
 * Input is read from x only, so it works wherever the finger lands: left third = dodge left, right
 * third = dodge right, centre = block / hit (whatever the situation calls for). A swipe sideways
 * from anywhere also dodges. Tone: playground slapstick. Nobody bleeds; the loser sits dizzy.
 */
final class FightMini extends Mini {
    // Opponent states.
    private static final int IDLE = 0, FEINT = 1, WIND = 2, STRIKE = 3, RECOVER = 4;
    private static final int SWING = 0, SHOVE = 1;
    // Pose slots (character units, screen directions, relative to shoulders for the fists).
    private static final int LEAN = 0, CROUCH = 1, HDX = 2, HDY = 3, LFX = 4, LFY = 5, RFX = 6, RFY = 7,
            LBEND = 8, RBEND = 9, LFS = 10, RFS = 11, SPREAD = 12, NP = 13;
    private static final int F_NEUTRAL = 0, F_ANGRY = 1, F_OUCH = 2, F_DIZZY = 3, F_SMUG = 4, F_EFFORT = 5,
            F_PANT = 6, F_WORRY = 7, F_GRIN = 8;
    private static final int FX_PUFF = 0, FX_STAR = 1, FX_POW = 2, FX_SWEAT = 3, FX_SWOOSH = 4, FX_BILL = 5;

    // ---- tuning ----
    private static final float BELL = 25f, INTRO = 1.0f, END_T = 2.1f;
    private static final float STRIKE_T = 0.20f, FEINT_T = 0.30f;
    /** Dodge and block stay up this long after the tap; the lock stops instant side-flipping. */
    private static final float DODGE_T = 0.75f, DODGE_LOCK = 0.28f, BLOCK_T = 0.75f, BLOCK_LOCK = 0.85f;
    /** Hold time for the heavy counter. */
    private static final float CH = 0.55f;
    private static final float JAB = 5, HAY = 16, HIT = 8, SHOVE_HIT = 7, BLOCK_COST = 9, WHIFF_COST = 2,
            FUMBLE_COST = 6, OPP_WHIFF = 2;
    private static final float LANE = 96;

    private static final int GOLD = 0xFFFFC857, RED = 0xFFFF5A4D;

    private static final class Style {
        int skin, shirt, shirt2, pants, shoe, hair, accent;
        float sh, hip, torso, head, leg, armW, spread0;
        boolean female, big;
    }

    private static final class Fx {
        int kind, col;
        float x, y, vx, vy, age, life, size;
        String word;
    }

    private final Style opp = new Style(), pl = new Style();
    private final Fx[] fx = new Fx[30];
    private int fxNext;

    // ---- bout state ----
    private float diff, time, bout, intro;
    private float windSwing, windShove, openBase, gapBase, featP, comboP, shoveP;
    private float oStam, pStam;
    private int hits, moveNo, windSerial, openSerial;
    private boolean lastWasShove, oFeint, chainNext;
    private int lastSide, sameRun, comboForce;
    private boolean comboLeft;

    // opponent
    private int oSt, oMove, oSide, oFake;
    private float oT, oWind, oGap, openLen, recLen, nextGap;
    private boolean oHeld;
    private boolean oSmug, oBlockedHit, oComboShort;
    private float hurtO, hurtOHeavy;

    // player
    private int dDir;
    private float dT, dLock, bT, bLock, stun, jabCd, hayT, jabT, hurtP, flyT, whiffT;
    private boolean pressedC, charging, chargeFull;
    private float chargeT, downX;
    private int jabsDone, jabParity;
    private final float[] padFlash = new float[3];
    private float pX;

    // end of bout
    private int endKind, endRes;
    private float endT;
    private boolean endFxDone;

    // hints (each shown once per bout)
    private boolean hintSwing, hintShove, hintOpen;
    private float hintT;
    private String hintText = "";

    // poses
    private final float[] oPose = new float[NP], pPose = new float[NP], oTgt = new float[NP], pTgt = new float[NP];
    private int oFace, pFace;
    private float headX, headY, headR;

    // layout (filled by layout(); defaults are set in begin())
    private float padY, padH, stT, stB, sc, so, sp, gT, oFy, pFy;

    // bot
    private int botSeenWind, botSeenOpen;
    private final float[] qAt = new float[6], qX = new float[6];
    private final int[] qKind = new int[6];
    private int qN;

    FightMini() {
        for (int i = 0; i < fx.length; i++) fx[i] = new Fx();
        opp.skin = 0xFFD39A6A;
        opp.shirt = 0xFFE0573D;
        opp.shirt2 = 0xFFB63D2A;
        opp.pants = 0xFF3E4A6B;
        opp.shoe = 0xFFF2F2F2;
        opp.hair = 0xFF1E1A1A;
        opp.accent = 0xFFFFC857;
        opp.sh = 22;
        opp.hip = 17;
        opp.torso = 38;
        opp.head = 14.5f;
        opp.leg = 32;
        opp.armW = 9.5f;
        opp.spread0 = 6;
        opp.big = true;
        pl.skin = 0xFFF2C9A1;
        pl.shirt = 0xFF5AA9FF;
        pl.shirt2 = 0xFF3F7FCB;
        pl.pants = 0xFF565E70;
        pl.shoe = 0xFFFF9F5A;
        pl.hair = 0xFF5B3A29;
        pl.accent = 0xFFFF9F5A;
        pl.sh = 15;
        pl.hip = 12;
        pl.torso = 30;
        pl.head = 12.5f;
        pl.leg = 28;
        pl.armW = 6.5f;
        pl.spread0 = 5;
    }

    @Override
    int rounds() {
        return 1;
    }

    @Override
    String title() {
        return "קרב רחוב";
    }

    @Override
    String howTo() {
        return "הוא מרים יד לצד אחד: מתחמקים לצד השני ואז מכים בחזרה. דחיפה אפשר רק לחסום. "
                + "להחזיק את האמצע = מכה חזקה, אבל היא צריכה זמן.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = Ui.clamp(difficulty, 0, 1);
        windSwing = lerp(0.80f, 0.52f, diff);
        windShove = lerp(0.85f, 0.65f, diff);
        openBase = lerp(1.20f, 0.85f, diff);
        gapBase = lerp(0.85f, 0.45f, diff);
        featP = Ui.clamp((diff - 0.15f) * 0.9f, 0, 0.5f);
        comboP = Ui.clamp((diff - 0.25f) * 0.8f, 0, 0.45f);
        shoveP = 0.20f + 0.15f * diff;
        pl.female = h.female();
        pl.hair = pl.female ? 0xFF8A4B2D : 0xFF5B3A29;
        time = 0;
        bout = 0;
        intro = INTRO;
        oStam = pStam = 100;
        hits = 0;
        moveNo = 0;
        windSerial = openSerial = botSeenWind = botSeenOpen = 0;
        lastWasShove = oFeint = chainNext = comboLeft = false;
        lastSide = 0;
        sameRun = 0;
        comboForce = 0;
        oSt = IDLE;
        oMove = SWING;
        oSide = -1;
        oFake = 1;
        oT = 0;
        oWind = windSwing;
        oGap = 1.0f;
        openLen = recLen = 0;
        nextGap = 0;
        oSmug = oBlockedHit = oComboShort = false;
        hurtO = hurtOHeavy = 0;
        dDir = 0;
        dT = dLock = bT = bLock = stun = jabCd = hayT = jabT = hurtP = flyT = whiffT = 0;
        pressedC = charging = chargeFull = false;
        chargeT = 0;
        jabsDone = 0;
        jabParity = 0;
        for (int i = 0; i < 3; i++) padFlash[i] = 0;
        pX = 180;
        endKind = 0;
        endRes = MISS;
        endT = 0;
        endFxDone = false;
        hintSwing = hintShove = hintOpen = false;
        hintT = 0;
        hintText = "";
        qN = 0;
        for (Fx f : fx) f.life = 0;
        layout(Game.HUD_H + 74, h.screenH() - 96);
        poseTargets();
        System.arraycopy(oTgt, 0, oPose, 0, NP);
        System.arraycopy(pTgt, 0, pPose, 0, NP);
    }

    @Override
    void pause() {
        pressedC = false;
        charging = false;
        chargeT = 0;
        qN = 0;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float hash(int n) {
        int x = n * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return (x & 0xFFFF) / 65535f;
    }

    // =====================================================================================
    // Rules
    // =====================================================================================

    @Override
    int update(float dt) {
        if (dt < 0) dt = 0;
        time += dt;
        float left = Math.min(dt, 0.25f);
        while (left > 1e-6f) {
            float d = Math.min(left, 1f / 60f);
            step(d);
            left -= d;
        }
        animate(dt);
        if (endKind != 0 && endT >= END_T) return endRes;
        return RUNNING;
    }

    private void step(float d) {
        if (endKind != 0) {
            endT += d;
            return;
        }
        if (intro > 0) {
            intro -= d;
            if (intro <= 0) {
                h.text(W / 2, stT + (stB - stT) * 0.28f, "קרב!", GOLD);
                h.sfx(Sfx.DING);
            }
            return;
        }
        bout += d;
        dT -= d;
        dLock -= d;
        bT -= d;
        bLock -= d;
        stun -= d;
        jabCd -= d;
        hayT -= d;
        jabT -= d;
        hurtP -= d;
        hurtO -= d;
        flyT -= d;
        whiffT -= d;
        hintT -= d;
        if (dT <= 0) dDir = 0;
        if (charging) {
            chargeT += d;
            if (chargeT >= CH && !chargeFull) {
                chargeFull = true;
                h.sfx(Sfx.TICK);
                h.burst(pX, pFy - 60 * sp, GOLD, 6);
            }
        }
        oT += d;
        switch (oSt) {
            case IDLE:
                if (oT >= oGap) startMove();
                break;
            case FEINT:
                if (oT >= FEINT_T) {
                    oSt = WIND;
                    oT = 0;
                    windSerial++;
                    h.sfx(Sfx.TICK);
                }
                break;
            case WIND:
                if (oT >= oWind) resolveStrike();
                break;
            case STRIKE:
                if (oT >= STRIKE_T) {
                    oSt = RECOVER;
                    oT = 0;
                }
                break;
            default:
                if (oT >= recLen) {
                    oSt = IDLE;
                    oT = 0;
                    oGap = nextGap;
                    oSmug = oBlockedHit = oComboShort = false;
                }
                break;
        }
        if (charging && !canStrike()) fumble();
        judge();
    }

    private boolean canStrike() {
        return (oSt == STRIKE && openLen > 0) || (oSt == RECOVER && openLen > 0 && oT < openLen);
    }

    private float openRemain() {
        return oSt == STRIKE ? openLen : openLen - oT;
    }

    private boolean threat() {
        return oSt == FEINT || oSt == WIND;
    }

    private void startMove() {
        Random r = h.rnd();
        moveNo++;
        boolean shove = !chainNext && moveNo >= 3 && !lastWasShove && r.nextFloat() < shoveP;
        lastWasShove = shove;
        oFeint = false;
        if (shove) {
            oMove = SHOVE;
            oSide = 0;
            oWind = windShove;
        } else {
            oMove = SWING;
            oWind = windSwing;
            int side = r.nextBoolean() ? -1 : 1;
            if (comboForce != 0) {
                side = comboForce;
            } else if (side == lastSide && sameRun >= 2) {
                side = -side;
            }
            sameRun = side == lastSide ? sameRun + 1 : 1;
            lastSide = side;
            oSide = side;
            oFeint = !chainNext && moveNo >= 3 && r.nextFloat() < featP;
        }
        comboForce = 0;
        comboLeft = !chainNext && !shove && !oFeint && moveNo >= 3 && r.nextFloat() < comboP;
        chainNext = false;
        oT = 0;
        windSerial++;
        if (oFeint) {
            oSt = FEINT;
            oFake = -oSide;
        } else {
            oSt = WIND;
            h.sfx(Sfx.TICK);
        }
        if (oMove == SWING && !hintSwing) {
            hintSwing = true;
            showHint("מתחמקים לצד הנגדי!");
        } else if (oMove == SHOVE && !hintShove) {
            hintShove = true;
            showHint("דחיפה? לחסום באמצע!");
        }
    }

    private void showHint(String s) {
        hintText = s;
        hintT = 1.8f;
    }

    private float gapNow(Random r) {
        return gapBase + 0.45f * r.nextFloat();
    }

    private void resolveStrike() {
        Random r = h.rnd();
        boolean swing = oMove == SWING;
        oSt = STRIKE;
        oT = 0;
        openLen = 0;
        recLen = 0.55f;
        nextGap = gapNow(r);
        float hx = pX, hy = pFy - 60 * sp;
        if (bT > 0) {
            // Blocked: costs him nothing, but it costs you stamina (paid at the tap).
            oBlockedHit = true;
            h.text(hx, hy - 24, "נחסם!", Ui.BLUE);
            h.sfx(Sfx.TAP);
            spawn(FX_PUFF, hx - 22, hy - 26, 0, 0, 0.4f, 0xFFDDE8FF, 14);
            spawn(FX_PUFF, hx + 22, hy - 26, 0, 0, 0.4f, 0xFFDDE8FF, 14);
            h.shake(3);
            if (!swing) {
                openLen = 0.55f;
                recLen = 0.55f;
                openSerial++;
                jabsDone = 1;
                jabCd = 0;
                if (!hintOpen) {
                    hintOpen = true;
                    showHint("לחיצה קצרה = מכה");
                }
            } else {
                recLen = 0.5f;
            }
            comboLeft = false;
        } else if (swing && dT > 0 && dDir == -oSide) {
            // Clean dodge: he whiffs, loses a little steam and is open for a moment.
            oStam -= OPP_WHIFF;
            boolean quick = comboLeft;
            openLen = quick ? 0.42f : openBase;
            recLen = openLen;
            if (quick) {
                chainNext = true;
                comboForce = -oSide;
                nextGap = 0.02f;
                oComboShort = true;
            }
            comboLeft = false;
            openSerial++;
            jabsDone = 0;
            jabCd = 0;
            h.text(hx, hy - 30, DODGE_WORDS[moveNo % DODGE_WORDS.length], Ui.GOOD);
            h.sfx(Sfx.WHOOSH);
            spawn(FX_SWOOSH, oSide < 0 ? pX - 34 : pX + 34, hy, oSide, 0, 0.35f, 0xFFFFFFFF, 1);
            spawn(FX_PUFF, 180 + oSide * 70, hy - 20, 0, 0, 0.5f, 0xFFFFFFFF, 26);
            if (!hintOpen) {
                hintOpen = true;
                showHint("לחיצה קצרה = מכה · החזקה = חזקה");
            }
        } else {
            // You got hit.
            float dmg = swing ? HIT : SHOVE_HIT;
            pStam -= dmg;
            hits++;
            stun = swing ? 0.42f : 0.55f;
            hurtP = 0.55f;
            flyT = swing ? 0 : 0.6f;
            charging = false;
            pressedC = false;
            oSmug = true;
            comboLeft = false;
            h.text(hx, hy - 34, swing ? "אוי!" : "וואו!", Ui.BAD);
            h.sfx(Sfx.PUNCH);
            h.sfx(Sfx.BAD);
            h.shake(swing ? 7 : 8);
            h.burst(hx, hy - 20, GOLD, 8);
            spawn(FX_POW, hx, hy - 8, 0, 0, 0.5f, GOLD, swing ? 1 : 2);
            for (int i = 0; i < 3; i++) {
                spawn(FX_STAR, hx, pFy - 78 * sp, (i - 1) * 70, -80 - 20 * i, 0.7f, GOLD, 9);
            }
        }
    }

    private static final String[] DODGE_WORDS = {"חמקת!", "פספס!", "וואלה!", "ביי ביי!", "אוויר!"};

    private void fumble() {
        charging = false;
        chargeFull = false;
        chargeT = 0;
        pressedC = false;
        pStam -= FUMBLE_COST;
        stun = Math.max(stun, 0.25f);
        whiffT = 0.35f;
        h.text(pX, pFy - 100 * sp, "מאוחר מדי!", Ui.ORANGE);
        h.sfx(Sfx.BAD);
        spawn(FX_PUFF, pX + 14, pFy - 50 * sp, 0, 0, 0.4f, 0xFFFFFFFF, 20);
        spawn(FX_SWEAT, pX - 14, pFy - 90 * sp, -30, -50, 0.7f, 0xFF8FD3FF, 5);
    }

    private void hurtOpp(float dmg, boolean heavy) {
        oStam -= dmg;
        hurtO = heavy ? 0.55f : 0.3f;
        hurtOHeavy = heavy ? 1 : 0;
        float hx = 180, hy = oFy - 92 * so;
        if (heavy) {
            h.text(hx, oFy - 46 * so, "בום!", GOLD);
            h.sfx(Sfx.PUNCH);
            h.sfx(Sfx.PERFECT);
            h.shake(8);
            h.burst(hx, hy, GOLD, 14);
            spawn(FX_POW, hx, hy, 0, 0, 0.6f, GOLD, 3);
            for (int i = 0; i < 5; i++) {
                spawn(FX_STAR, hx, hy - 10, (i - 2) * 60, -90 - 25 * (i % 2), 0.9f, GOLD, 11);
            }
        } else {
            h.text(hx + (jabParity % 2 == 0 ? -34 : 34), oFy - 36 * so, JAB_WORDS[jabParity % JAB_WORDS.length], Ui.INK);
            h.sfx(Sfx.PUNCH);
            h.shake(3);
            h.burst(hx, hy, 0xFFFFFFFF, 5);
            spawn(FX_STAR, hx, hy - 10, (jabParity % 2 == 0 ? -50 : 50), -70, 0.55f, GOLD, 8);
            spawn(FX_PUFF, hx, hy + 6, 0, 0, 0.35f, 0xFFFFFFFF, 18);
        }
    }

    private static final String[] JAB_WORDS = {"פוף!", "טפח!", "פאו!"};

    private void jab() {
        if (!canStrike() || jabsDone >= 2 || jabCd > 0) return;
        jabsDone++;
        jabCd = 0.2f;
        jabT = 0.2f;
        jabParity++;
        hurtOpp(JAB, false);
    }

    private void haymaker() {
        jabsDone = 2;
        hayT = 0.32f;
        jabCd = 0.4f;
        hurtOpp(HAY, true);
    }

    private void judge() {
        if (endKind != 0) return;
        if (oStam <= 0) {
            oStam = 0;
            end(1);
        } else if (pStam <= 0) {
            pStam = 0;
            end(2);
        } else if (bout >= BELL) {
            if (pStam > oStam + 10) end(3);
            else if (pStam >= oStam - 10) end(4);
            else end(5);
        }
    }

    private void end(int kind) {
        endKind = kind;
        endT = 0;
        charging = false;
        pressedC = false;
        switch (kind) {
            case 1:
                endRes = hits == 0 ? PERFECT : OK;
                break;
            case 3:
                endRes = hits == 0 && oStam <= 45 ? PERFECT : OK;
                break;
            case 4:
                endRes = OK;
                break;
            default:
                endRes = MISS;
                break;
        }
    }

    // =====================================================================================
    // Input
    // =====================================================================================

    @Override
    void down(float x, float y) {
        if (endKind != 0 || intro > 0) return;
        downX = x;
        if (pressedC) {
            // A second finger-down without an up (lost event): start clean.
            pressedC = false;
            charging = false;
        }
        if (stun > 0) return;
        if (x < 120) {
            dodge(-1);
        } else if (x > 240) {
            dodge(1);
        } else {
            centreDown();
        }
    }

    @Override
    void move(float x, float y) {
        if (endKind != 0 || !pressedC) return;
        if (Math.abs(x - downX) >= 40) {
            // A sideways swipe from the middle is a dodge, not a punch.
            pressedC = false;
            charging = false;
            chargeT = 0;
            chargeFull = false;
            if (stun <= 0) dodge(x < downX ? -1 : 1);
        }
    }

    @Override
    void up(float x, float y) {
        if (!pressedC) return;
        pressedC = false;
        if (endKind != 0 || !charging) return;
        charging = false;
        boolean full = chargeFull;
        chargeFull = false;
        chargeT = 0;
        if (!canStrike()) return;
        if (full) haymaker();
        else jab();
    }

    private void dodge(int dir) {
        padFlash[dir < 0 ? 0 : 2] = 0.18f;
        if (dLock > 0) return;
        dDir = dir;
        dT = DODGE_T;
        dLock = DODGE_LOCK;
        charging = false;
        chargeT = 0;
        chargeFull = false;
        h.sfx(Sfx.WHOOSH);
        spawn(FX_SWOOSH, pX, pFy - 40 * sp, -dir, 0, 0.3f, 0xFFFFFFFF, 0);
    }

    private void centreDown() {
        padFlash[1] = 0.18f;
        if (threat()) {
            if (bLock > 0) return;
            bT = BLOCK_T;
            bLock = BLOCK_LOCK;
            dT = 0;
            dDir = 0;
            pStam -= BLOCK_COST;
            h.sfx(Sfx.TAP);
            spawn(FX_PUFF, pX, pFy - 60 * sp, 0, 0, 0.3f, 0xFFBFD8FF, 16);
            return;
        }
        if (canStrike()) {
            pressedC = true;
            charging = true;
            chargeT = 0;
            chargeFull = false;
            return;
        }
        if (oSt == STRIKE) return; // a beat too early or too late, no charge
        // Swinging at thin air tires you out a little.
        pStam -= WHIFF_COST;
        jabT = 0.18f;
        jabParity++;
        h.sfx(Sfx.TICK);
    }

    // =====================================================================================
    // Bot
    // =====================================================================================

    private static final int Q_DOWN = 0, Q_UP = 1, Q_JAB = 2;

    private void queue(float at, int kind, float x) {
        if (qN >= qAt.length) return;
        qAt[qN] = at;
        qKind[qN] = kind;
        qX[qN] = x;
        qN++;
    }

    @Override
    void bot(Random r, float skill) {
        float react = 0.40f - 0.26f * skill + r.nextFloat() * 0.08f;
        // Run what is due.
        for (int i = 0; i < qN; ) {
            if (time >= qAt[i]) {
                int k = qKind[i];
                float x = qX[i];
                qN--;
                for (int j = i; j < qN; j++) {
                    qAt[j] = qAt[j + 1];
                    qKind[j] = qKind[j + 1];
                    qX[j] = qX[j + 1];
                }
                if (k == Q_DOWN) down(x, 400);
                else if (k == Q_UP) up(x, 400);
                else if (canStrike()) {
                    down(180, 400);
                    queue(time + 0.07f, Q_UP, 180);
                }
            } else {
                i++;
            }
        }
        if (windSerial != botSeenWind) {
            botSeenWind = windSerial;
            if (oSt == FEINT) {
                // The fake: novices bite, experts wait for the real arm.
                if (r.nextFloat() < (1 - skill) * 0.8f) tapSide(time + react, -oFake);
            } else if (oSt == WIND) {
                boolean reads = r.nextFloat() < 0.12f + 0.88f * (float) Math.pow(skill, 0.45);
                if (reads) {
                    if (oMove == SHOVE) {
                        queue(time + react, Q_DOWN, 180);
                        queue(time + react + 0.06f, Q_UP, 180);
                    } else {
                        tapSide(time + react, -oSide);
                    }
                } else {
                    float roll = r.nextFloat();
                    float at = time + react + 0.08f;
                    if (roll < 0.4f) {
                        // freezes
                    } else if (roll < 0.8f) {
                        tapSide(at, r.nextBoolean() ? -1 : 1);
                    } else {
                        queue(at, Q_DOWN, 180);
                        queue(at + 0.06f, Q_UP, 180);
                    }
                }
            }
        }
        if (openSerial != botSeenOpen) {
            botSeenOpen = openSerial;
            if (r.nextFloat() < 0.05f + 0.90f * skill) {
                float at = time + react * 0.85f;
                float noise = (r.nextFloat() - 0.5f) * 0.5f * (1 - skill);
                float left = openLen - STRIKE_T * 0 - react * 0.85f + noise;
                boolean greedy = left > CH + 0.12f && r.nextFloat() < 0.25f + 0.65f * skill;
                if (greedy) {
                    queue(at, Q_DOWN, 180);
                    queue(at + CH + 0.04f + r.nextFloat() * 0.06f * (1 - skill), Q_UP, 180);
                } else {
                    queue(at, Q_JAB, 180);
                    if (r.nextFloat() < skill && left > 0.4f) queue(at + 0.28f, Q_JAB, 180);
                }
            }
        }
    }

    private void tapSide(float at, int dir) {
        float x = dir < 0 ? 60 : 300;
        queue(at, Q_DOWN, x);
        queue(at + 0.06f, Q_UP, x);
    }

    // =====================================================================================
    // Animation
    // =====================================================================================

    private void layout(float top, float bottom) {
        padH = 50;
        padY = bottom - padH - 4;
        stT = top + 44;
        stB = padY - 6;
        sc = Ui.clamp((stB - stT - 30) / 150f, 0.8f, 1.6f);
        float gH = 150 * sc;
        gT = Math.max(stT + 30, stB - gH);
        so = sc;
        sp = sc * 0.82f;
        oFy = gT + 108 * sc;
        pFy = stB - 2;
    }

    private void animate(float dt) {
        poseTargets();
        float k = Math.min(1f, dt * (oSt == STRIKE ? 40f : 22f));
        for (int i = 0; i < NP; i++) oPose[i] += (oTgt[i] - oPose[i]) * k;
        float k2 = Math.min(1f, dt * 24f);
        for (int i = 0; i < NP; i++) pPose[i] += (pTgt[i] - pPose[i]) * k2;
        float want = 180 + dDir * LANE * (dT > 0 ? 1 : 0);
        if (endKind == 1 || endKind == 3) want = 180 + LANE * 0.95f;
        if (endKind == 2 || endKind == 5) want = 180;
        pX += (want - pX) * Math.min(1f, dt * 20f);
        for (int i = 0; i < 3; i++) if (padFlash[i] > 0) padFlash[i] -= dt;
        for (Fx f : fx) {
            if (f.life <= 0) continue;
            f.age += dt;
            if (f.age >= f.life) {
                f.life = 0;
                continue;
            }
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            if (f.kind == FX_STAR) f.vy += 160 * dt;
        }
        if (endKind != 0 && !endFxDone) {
            endFxDone = true;
            endEffects();
        }
        if (endKind != 0 && endT > 0.5f && ((int) (endT * 6)) != ((int) ((endT - dt) * 6)) && (endKind == 2 || endKind == 5)) {
            spawn(FX_BILL, pX + (hash((int) (endT * 100)) - 0.5f) * 40, pFy - 40 * sp, (hash((int) (endT * 77)) - 0.5f) * 80, -70, 1.2f, 0xFF5BD68A, 7);
        }
    }

    private void endEffects() {
        float cx = 180;
        switch (endKind) {
            case 1:
            case 3:
                h.text(cx, stT + (stB - stT) * 0.30f, endKind == 1 ? "נוק-אאוט!" : "ניצחון בנקודות!", GOLD);
                h.sfx(endRes == PERFECT ? Sfx.PERFECT : Sfx.GOOD);
                h.burst(cx, oFy - 40 * so, GOLD, 16);
                h.shake(6);
                break;
            case 4:
                h.text(cx, stT + (stB - stT) * 0.30f, "צלצול! תיקו", Ui.INK);
                h.sfx(Sfx.DING);
                break;
            default:
                h.text(cx, stT + (stB - stT) * 0.30f, endKind == 2 ? "הארנק עף!" : "הפסדת בנקודות", Ui.BAD);
                h.sfx(Sfx.COLLAPSE);
                h.shake(6);
                break;
        }
    }

    /** Fills oTgt / pTgt and the face ids for the current state. */
    private void poseTargets() {
        float bob = (float) Math.sin(time * 5.2f);
        float[] g = oTgt;
        java.util.Arrays.fill(g, 0);
        g[LFX] = 2;
        g[LFY] = -4 + bob * 1.5f;
        g[RFX] = -2;
        g[RFY] = -4 - bob * 1.5f;
        g[LBEND] = g[RBEND] = 14;
        g[LFS] = g[RFS] = 1;
        g[CROUCH] = bob * 1.2f;
        oFace = (int) (time * 0.7f) % 3 == 2 ? F_NEUTRAL : F_ANGRY;
        if (oStam < 30) {
            g[CROUCH] += 3;
            oFace = F_PANT;
        }
        int sd = oSide < 0 ? -1 : 1;
        int ai = sd < 0 ? LFX : RFX; // arm that swings: left arm for a left swing
        switch (oSt) {
            case FEINT: {
                int fs = oFake < 0 ? -1 : 1;
                int fi = fs < 0 ? LFX : RFX;
                g[fi] = fs * 17;
                g[fi + 1] = -20;
                g[LEAN] = fs * 3 + (float) Math.sin(time * 55) * 1.4f;
                oFace = F_SMUG;
                break;
            }
            case WIND:
                if (oMove == SHOVE) {
                    g[LFX] = -14;
                    g[LFY] = -38;
                    g[RFX] = 14;
                    g[RFY] = -38;
                    g[CROUCH] = 9;
                    g[SPREAD] = 7;
                    g[HDY] = 3;
                    g[LEAN] = (float) Math.sin(time * 60) * 1.3f;
                    g[LFS] = g[RFS] = 1.2f;
                    oFace = F_EFFORT;
                } else {
                    g[ai] = sd * 36;
                    g[ai + 1] = -40;
                    g[ai == LFX ? LFS : RFS] = 1.25f;
                    g[LEAN] = sd * 9 + (float) Math.sin(time * 60) * 1.0f;
                    g[CROUCH] = 3;
                    g[HDX] = sd * 2;
                    oFace = F_EFFORT;
                }
                break;
            case STRIKE:
                if (oMove == SHOVE) {
                    g[LFX] = 18;
                    g[LFY] = 44;
                    g[RFX] = -18;
                    g[RFY] = 44;
                    g[LFS] = g[RFS] = 2.0f;
                    g[CROUCH] = -4;
                } else {
                    g[ai] = -sd * 24;
                    g[ai + 1] = 46;
                    g[ai == LFX ? LFS : RFS] = 2.4f;
                    g[LEAN] = -sd * 9;
                    g[CROUCH] = -2;
                }
                oFace = F_ANGRY;
                break;
            case RECOVER:
                if (oBlockedHit) {
                    g[CROUCH] = -2;
                    g[HDY] = -3;
                    g[LEAN] = oMove == SHOVE ? 4 : 0;
                    g[LFY] = -16;
                    g[RFY] = -16;
                    oFace = F_WORRY;
                } else if (oSmug) {
                    g[LEAN] = 0;
                    g[RFX] = -12;
                    g[RFY] = -26;
                    g[RFS] = 1.2f;
                    g[CROUCH] = -1 + bob;
                    oFace = F_SMUG;
                } else if (oComboShort) {
                    g[LEAN] = -sd * 4;
                    oFace = F_ANGRY;
                } else {
                    // Overbalanced by his own swing: staggering, tongue out.
                    g[ai] = sd * 14;
                    g[ai + 1] = 40;
                    int other = ai == LFX ? RFX : LFX;
                    g[other] = (other == LFX ? 1 : -1) * 10;
                    g[other + 1] = 34;
                    g[LEAN] = -sd * 14 + (float) Math.sin(time * 14) * 3;
                    g[HDX] = -sd * 4;
                    g[CROUCH] = 6;
                    g[SPREAD] = 6;
                    oFace = F_PANT;
                }
                break;
            default:
                break;
        }
        if (hurtO > 0) {
            float hv = hurtOHeavy > 0 ? 1.6f : 0.9f;
            g[HDY] = -5 * hv;
            g[LEAN] = (hurtOHeavy > 0 ? 12 : 5) * (jabParity % 2 == 0 ? -1 : 1) * 0.6f;
            g[CROUCH] = hurtOHeavy > 0 ? -4 : -1;
            g[LFX] = 10;
            g[LFY] = 6;
            g[RFX] = -10;
            g[RFY] = 6;
            oFace = F_OUCH;
        }
        if (endKind == 1 || endKind == 3) oFace = F_DIZZY;
        if (endKind == 2 || endKind == 5) {
            java.util.Arrays.fill(g, 0);
            g[LFX] = 2;
            g[LFY] = -4;
            g[RFX] = 14;
            g[RFY] = -16 + bob * 2;
            g[RFS] = 1.3f;
            g[LBEND] = g[RBEND] = 14;
            g[LFS] = 1;
            g[CROUCH] = bob;
            oFace = F_SMUG;
        }
        if (endKind == 4) {
            g[CROUCH] = 4 + bob * 1.5f;
            g[LFY] = 14;
            g[RFY] = 14;
            oFace = F_PANT;
        }

        // ---- player ----
        float[] p = pTgt;
        java.util.Arrays.fill(p, 0);
        p[LFX] = -1;
        p[LFY] = -3 + bob;
        p[RFX] = 1;
        p[RFY] = -3 - bob;
        p[LBEND] = p[RBEND] = 12;
        p[LFS] = p[RFS] = 1;
        p[CROUCH] = bob * 0.8f + 2;
        pFace = F_NEUTRAL;
        if (oSt == WIND && oStam > 0) pFace = F_WORRY;
        if (pStam < 30) pFace = F_WORRY;
        if (dT > 0 && dDir != 0) {
            p[LEAN] = dDir * 10;
            p[HDX] = dDir * 5;
            p[CROUCH] = 5;
            p[SPREAD] = 6;
            p[LFY] = 2;
            p[RFY] = 2;
            pFace = F_SMUG;
        }
        if (bT > 0) {
            p[LFX] = 22;
            p[LFY] = -18;
            p[RFX] = -22;
            p[RFY] = -18;
            p[LFS] = p[RFS] = 1.2f;
            p[CROUCH] = 6;
            p[SPREAD] = 5;
            pFace = F_EFFORT;
        }
        if (charging) {
            p[RFX] = 16;
            p[RFY] = 36 - Math.min(1, chargeT / CH) * 4;
            p[RBEND] = 10;
            p[RFS] = 1.2f + 0.35f * Math.min(1, chargeT / CH);
            p[LEAN] = 6;
            p[CROUCH] = 7;
            p[SPREAD] = 7;
            pFace = chargeFull ? F_ANGRY : F_EFFORT;
        }
        if (hayT > 0) {
            p[RFX] = -2;
            p[RFY] = -64;
            p[RFS] = 2.1f;
            p[LEAN] = -3;
            p[CROUCH] = -3;
            pFace = F_ANGRY;
        } else if (jabT > 0) {
            boolean left = jabParity % 2 == 0;
            p[left ? LFX : RFX] = left ? 4 : -4;
            p[left ? LFY : RFY] = -50;
            p[left ? LFS : RFS] = 1.7f;
            p[LEAN] = left ? 3 : -3;
            p[CROUCH] = 0;
            pFace = F_ANGRY;
        }
        if (whiffT > 0) {
            p[LEAN] = 8 * (float) Math.sin(time * 30);
            pFace = F_WORRY;
        }
        if (hurtP > 0) {
            p[HDY] = -4;
            p[LEAN] = flyT > 0 ? 0 : (float) Math.sin(time * 40) * 2.5f;
            p[CROUCH] = -3;
            p[LFX] = 10;
            p[LFY] = 8;
            p[RFX] = -10;
            p[RFY] = 8;
            pFace = F_OUCH;
        }
        if (endKind == 1 || endKind == 3) {
            float hop = (float) Math.abs(Math.sin(time * 7));
            p[LFX] = -6;
            p[LFY] = -46 - hop * 4;
            p[RFX] = 6;
            p[RFY] = -46 - hop * 4;
            p[LFS] = p[RFS] = 1.2f;
            p[CROUCH] = -2;
            pFace = F_GRIN;
        }
        if (endKind == 4) {
            p[CROUCH] = 5 + bob;
            p[LFY] = 14;
            p[RFY] = 14;
            pFace = F_PANT;
        }
    }

    // =====================================================================================
    // Effects
    // =====================================================================================

    private void spawn(int kind, float x, float y, float vx, float vy, float life, int col, float size) {
        Fx f = fx[fxNext];
        fxNext = (fxNext + 1) % fx.length;
        f.kind = kind;
        f.x = x;
        f.y = y;
        f.vx = vx;
        f.vy = vy;
        f.age = 0;
        f.life = life;
        f.col = col;
        f.size = size;
        f.word = null;
    }

    private void drawFx(Gfx c) {
        for (Fx f : fx) {
            if (f.life <= 0) continue;
            float u = f.age / f.life;
            int fade = (int) ((1 - u) * 255);
            switch (f.kind) {
                case FX_PUFF: {
                    float r = f.size * (0.5f + u);
                    int col = (f.col & 0x00FFFFFF) | ((int) (150 * (1 - u)) << 24);
                    c.fillCircle(f.x - r * 0.6f, f.y, r * 0.7f, col);
                    c.fillCircle(f.x + r * 0.6f, f.y + 2, r * 0.7f, col);
                    c.fillCircle(f.x, f.y - r * 0.5f, r * 0.8f, col);
                    break;
                }
                case FX_STAR:
                    star(c, f.x, f.y, f.size * (1 - u * 0.4f), f.age * 9, (f.col & 0x00FFFFFF) | (fade << 24));
                    break;
                case FX_POW: {
                    float pop = Math.min(1, u * 5) * (1 - u * 0.3f);
                    float r = (f.size >= 3 ? 46 : f.size >= 2 ? 36 : 28) * pop * Math.max(0.8f, sc * 0.85f);
                    burstShape(c, f.x, f.y, r, f.age * 2, (f.col & 0x00FFFFFF) | (fade << 24));
                    burstShape(c, f.x, f.y, r * 0.62f, f.age * 2 + 0.3f, 0x00FFFFFF | ((fade * 2 / 3) << 24));
                    break;
                }
                case FX_SWEAT: {
                    int col = (f.col & 0x00FFFFFF) | (fade << 24);
                    c.fillCircle(f.x, f.y + 2, f.size, col);
                    c.fillPoly(new float[]{f.x - f.size * 0.8f, f.y + 0.5f, f.x + f.size * 0.8f, f.y + 0.5f, f.x, f.y - f.size * 2.2f}, col);
                    break;
                }
                case FX_SWOOSH: {
                    int col = (f.col & 0x00FFFFFF) | ((int) (200 * (1 - u)) << 24);
                    float dir = f.vx;
                    for (int i = 0; i < 3; i++) {
                        float yy = f.y - 28 + i * 24;
                        float len = 40 + 30 * u;
                        c.line(f.x - dir * (10 + 40 * u), yy, f.x - dir * (10 + 40 * u) - dir * len, yy + (i - 1) * 3, 3, col);
                    }
                    break;
                }
                default: {
                    float rot = f.age * 6;
                    float w = 9, hh = 5;
                    int col = (f.col & 0x00FFFFFF) | (fade << 24);
                    float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
                    c.fillPoly(new float[]{f.x + (-w) * cs + hh * sn, f.y + (-w) * sn - hh * cs, f.x + w * cs + hh * sn,
                            f.y + w * sn - hh * cs, f.x + w * cs - hh * sn, f.y + w * sn + hh * cs, f.x - w * cs - hh * sn,
                            f.y - w * sn + hh * cs}, col);
                    break;
                }
            }
        }
    }

    private static void star(Gfx c, float cx, float cy, float r, float rot, int col) {
        float[] xy = new float[20];
        for (int i = 0; i < 10; i++) {
            float a = rot + i * (float) Math.PI / 5 - (float) Math.PI / 2;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            xy[i * 2] = cx + (float) Math.cos(a) * rr;
            xy[i * 2 + 1] = cy + (float) Math.sin(a) * rr;
        }
        c.fillPoly(xy, col);
    }

    private static void burstShape(Gfx c, float cx, float cy, float r, float rot, int col) {
        int n = 16;
        float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float a = rot * 0.1f + i * (float) Math.PI * 2 / n;
            float rr = (i % 2 == 0) ? r : r * 0.6f;
            xy[i * 2] = cx + (float) Math.cos(a) * rr;
            xy[i * 2 + 1] = cy + (float) Math.sin(a) * rr;
        }
        c.fillPoly(xy, col);
    }

    // =====================================================================================
    // Drawing: scene
    // =====================================================================================

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        scene(c, top, bottom);
        boolean realThreat = oSt == WIND || (oSt == STRIKE && false);
        if (realThreat || oSt == FEINT) dangerBand(c);
        // Opponent first (behind), then the player in front of his legs.
        drawOpponent(c);
        drawPlayer(c);
        drawFx(c);
        overlays(c);
        hud(c, top);
        pads(c);
    }

    private void scene(Gfx c, float top, float bottom) {
        float wallTop = top + 40;
        float ground = oFy - 4 * so;
        c.fillRect(0, wallTop, W, ground - wallTop, 0xFF241E2C);
        // Bricks: rows of short ticks, offset on alternate rows.
        int rows = (int) ((ground - wallTop) / 15f) + 1;
        for (int i = 0; i < rows; i++) {
            float y = wallTop + i * 15;
            if (y > ground) break;
            c.fillRect(0, y, W, 1.5f, 0xFF1A1522);
            float off = (i % 2 == 0) ? 0 : 20;
            for (float x = off; x < W; x += 40) c.fillRect(x, y, 1.5f, Math.min(15, ground - y), 0xFF1A1522);
        }
        // Window with a warm light and a sleeping neighbour's curtain.
        float wy = wallTop + 14;
        c.fillRoundRect(278, wy, 50, 62, 4, 0xFF171320);
        c.fillRect(282, wy + 4, 42, 54, 0xFF6B5428);
        c.fillRect(282, wy + 4, 42, 54, 0x55FFC857);
        c.fillRect(302, wy + 4, 2, 54, 0xFF171320);
        c.fillPoly(new float[]{282, wy + 4, 296, wy + 4, 290, wy + 30, 282, wy + 36}, 0xFFB5586A);
        // Street lamp on the left with a soft cone.
        float lx = 34;
        c.fillRect(lx - 2, wallTop + 22, 4, ground - wallTop - 22, 0xFF3A3548);
        c.fillRoundRect(lx - 14, wallTop + 14, 30, 10, 5, 0xFF3A3548);
        c.fillPoly(new float[]{lx - 10, wallTop + 24, lx + 12, wallTop + 24, lx + 44, ground, lx - 42, ground}, 0x14FFE9A8);
        c.fillCircle(lx - 1, wallTop + 26, 5, 0xFFFFE9A8);
        // Dumpster and the cat that watches the fight.
        float dx = 262, dy = ground - 36;
        c.fillRoundRect(dx, dy, 80, 36, 3, 0xFF2D4A47);
        c.fillPoly(new float[]{dx - 3, dy, dx + 83, dy, dx + 79, dy - 7, dx + 1, dy - 7}, 0xFF3A5E5A);
        c.fillRect(dx + 8, dy + 10, 64, 2, 0xFF223836);
        c.fillRect(dx + 8, dy + 20, 64, 2, 0xFF223836);
        drawCat(c, dx + 56, dy - 7);
        // Crates on the far left.
        c.fillRoundRect(2, ground - 24, 30, 24, 2, 0xFF5A4630);
        c.fillRect(2, ground - 13, 30, 2, 0xFF3F3122);
        c.fillRoundRect(8, ground - 46, 22, 22, 2, 0xFF6B5539);
        c.fillRect(8, ground - 36, 22, 2, 0xFF3F3122);
        // Floor.
        c.fillRect(0, ground, W, bottom - ground, 0xFF1D222B);
        c.fillRect(0, ground, W, 3, 0xFF2C3340);
        for (int i = 1; i < 4; i++) {
            float y = ground + (bottom - ground) * (i / 4f) * (i / 4f) * 1.2f;
            if (y < bottom) c.fillRect(0, y, W, 1.5f, 0xFF262C37);
        }
        // Lane markers on the floor: where you can stand.
        float ly = pFy - 4;
        for (int s = -1; s <= 1; s += 2) {
            c.fillRoundRect(180 + s * LANE - 34, ly - 3, 68, 8, 4, 0x22FFFFFF);
        }
        // Shadows.
        c.fillRoundRect(180 - 30 * so, oFy - 4 * so, 60 * so, 9 * so, 4 * so, 0x66000000);
        c.fillRoundRect(pX - 24 * sp, pFy - 5 * sp, 48 * sp, 9 * sp, 4 * sp, 0x66000000);
    }

    private void drawCat(Gfx c, float x, float y) {
        int fur = 0xFF8C8F99, dark = 0xFF5E616B;
        boolean shocked = endKind != 0;
        float tail = (float) Math.sin(time * 3.2f) * 8;
        c.line(x - 12, y - 2, x - 24 + tail * 0.4f, y - 14, 4, dark);
        c.line(x - 24 + tail * 0.4f, y - 14, x - 26 + tail, y - 26, 4, dark);
        c.fillPoly(new float[]{x - 14, y, x + 12, y, x + 9, y - 14, x - 8, y - 18}, fur);
        c.fillCircle(x + 4, y - 22, 9, fur);
        c.fillPoly(new float[]{x - 4, y - 28, x, y - 40, x + 3, y - 28}, fur);
        c.fillPoly(new float[]{x + 5, y - 28, x + 11, y - 39, x + 13, y - 26}, fur);
        float look = (float) Math.sin(time * 1.3f);
        float er = shocked ? 3.6f : 2.4f;
        c.fillCircle(x, y - 23, er, 0xFFE8F25A);
        c.fillCircle(x + 9, y - 23, er, 0xFFE8F25A);
        c.fillCircle(x + look * 0.9f, y - 23, 1.2f, 0xFF10141A);
        c.fillCircle(x + 9 + look * 0.9f, y - 23, 1.2f, 0xFF10141A);
    }

    private void dangerBand(Gfx c) {
        boolean fake = oSt == FEINT;
        float yT = pFy - 100 * sp, yB = pFy + 1;
        float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(time * 14));
        boolean shove = oMove == SHOVE && !fake;
        int side = fake ? oFake : oSide;
        float x0 = 0, x1 = W;
        if (!shove) {
            if (side < 0) x1 = 180 + LANE * 0.85f;
            else x0 = 180 - LANE * 0.85f;
        }
        int base = fake ? 0x1AFFC857 : (int) (0x30 + 0x20 * pulse) << 24 | 0xFF4D4D;
        c.fillRect(x0, yT, x1 - x0, yB - yT, base);
        int stripe = fake ? 0x22FFC857 : 0x38FF4D4D;
        for (float x = x0 - 40; x < x1; x += 34) {
            float xa = Math.max(x0, x), xb = Math.min(x1, x + 14);
            if (xb <= xa) continue;
            c.fillPoly(new float[]{xa, yB, xb, yB, Math.min(x1, xb + 30), yT, Math.max(x0, xa + 30 > x1 ? x1 : xa + 30), yT}, stripe);
        }
        int edge = fake ? 0x88FFC857 : 0xCCFF5A4D;
        c.fillRect(x0, yT, x1 - x0, 3, edge);
        c.fillRect(x0, yB - 3, x1 - x0, 3, edge);
        // Converging ring on the lane(s) where the fist will land.
        float prog = fake ? 0 : Ui.clamp(oT / oWind, 0, 1);
        if (!fake) {
            float r = lerp(46, 16, prog);
            float ry = (yT + yB) * 0.5f;
            if (shove) {
                ringMark(c, 180 - LANE, ry, r);
                ringMark(c, 180 + LANE, ry, r);
            } else {
                ringMark(c, 180 + side * LANE, ry, r);
            }
        } else {
            c.text("?", 180 + side * LANE, (yT + yB) * 0.5f + 12, 34, 0xAAFFC857, Gfx.CENTER);
        }
        // Safe lane: a green outline with arrows pointing into it. Not for a shove (no safe lane).
        if (!shove) {
            int safe = -side;
            float sx = 180 + safe * LANE;
            int col = fake ? 0x66B5E8C6 : Ui.GOOD;
            c.strokeRoundRect(sx - 48, yT + 4, 96, yB - yT - 8, 12, 3, col);
            float ay = (yT + yB) * 0.5f;
            for (int i = 0; i < 2; i++) {
                float ax = sx - safe * (14 - i * 12) + safe * ((float) Math.sin(time * 10) * 3);
                chevron(c, ax, ay, 15, safe, col);
            }
        }
    }

    private void ringMark(Gfx c, float x, float y, float r) {
        c.strokeRoundRect(x - r, y - r, r * 2, r * 2, r, 4, RED);
        c.fillCircle(x, y, 7, RED);
        c.text("!", x, y + 6, 18, 0xFFFFFFFF, Gfx.CENTER);
    }

    /** A chevron pointing toward dir (+1 right, -1 left), centred on (x, y). */
    private static void chevron(Gfx c, float x, float y, float s, int dir, int col) {
        c.fillPoly(new float[]{x - dir * s * 0.5f, y - s, x + dir * s * 0.05f, y - s, x + dir * s * 0.6f, y,
                x + dir * s * 0.05f, y + s, x - dir * s * 0.5f, y + s, x + dir * s * 0.05f, y}, col);
    }

    private static void shield(Gfx c, float x, float y, float s, int col) {
        c.fillPoly(new float[]{x - s, y - s, x + s, y - s, x + s, y + s * 0.15f, x, y + s * 1.1f, x - s, y + s * 0.15f}, col);
        c.fillPoly(new float[]{x - s * 0.6f, y - s * 0.6f, x + s * 0.6f, y - s * 0.6f, x + s * 0.6f, y + s * 0.05f, x,
                y + s * 0.65f, x - s * 0.6f, y + s * 0.05f}, 0xFF10141A);
        c.fillRect(x - 2, y - s * 0.45f, 4, s * 0.8f, col);
    }

    private static void fist(Gfx c, float x, float y, float s, int col) {
        c.fillRoundRect(x - s, y - s * 0.8f, s * 2, s * 1.6f, s * 0.5f, col);
        for (int i = 0; i < 3; i++) c.fillRect(x - s * 0.55f + i * s * 0.55f, y - s * 0.75f, 1.5f, s * 0.7f, 0xFF10141A);
    }

    // =====================================================================================
    // Drawing: fighters
    // =====================================================================================

    private void drawOpponent(Gfx c) {
        float x = 180;
        float yoff = 0;
        if (endKind == 1 || endKind == 3) {
            drawSit(c, opp, x, oFy + 6 * so, so, true);
            return;
        }
        float t = time;
        // Strike lunge: he grows toward the viewer a touch.
        float grow = oSt == STRIKE ? 1.06f : 1f;
        yoff = oSt == STRIKE ? 4 * so : 0;
        float look = pX < 170 ? -1 : pX > 190 ? 1 : 0;
        fighter(c, opp, x, oFy + yoff, so * grow, oPose, oFace, t, look);
        float hx = headX, hy = headY, hr = headR;
        // Speech-bubble telegraph: unmistakable at a glance.
        if (oSt == WIND) {
            bubble(c, hx + (oMove == SHOVE ? 0 : (oSide < 0 ? -hr * 2.4f : hr * 2.4f)), hy - hr * 2.0f,
                    oMove == SHOVE ? "!!" : "!", RED, 1f + 0.1f * (float) Math.sin(time * 30));
        } else if (oSt == FEINT) {
            bubble(c, hx + (oFake < 0 ? -hr * 2.4f : hr * 2.4f), hy - hr * 2.2f, "?", 0xFFB8892B, 1f);
        }
        if (oSt == RECOVER && openLen > 0 && !oBlockedHit) {
            sweatAround(c, hx, hy, hr, so);
        }
        if (hurtO > 0 || (oSt == RECOVER && canStrike())) {
            // Orbiting dizzy stars while he is open.
            if (oSt == RECOVER && canStrike()) {
                for (int i = 0; i < 3; i++) {
                    float a = time * 6 + i * 2.09f;
                    star(c, hx + (float) Math.cos(a) * hr * 1.3f, hy - hr * 1.3f + (float) Math.sin(a) * hr * 0.35f, 4.5f * so,
                            time * 4, 0xFFFFE27A);
                }
            }
        }
        // The wallet, once he has won it.
        if (endKind == 2 || endKind == 5) {
            float wx = x + 40 * so, wy = oFy - 90 * so + (float) Math.sin(time * 6) * 2;
            c.fillRoundRect(wx - 9, wy - 6, 18, 12, 2, 0xFF7B4A24);
            c.fillRect(wx - 9, wy - 1, 18, 2, 0xFF55341A);
            c.fillCircle(wx + 4, wy, 2.5f, GOLD);
        }
    }

    private void sweatAround(Gfx c, float hx, float hy, float hr, float s) {
        float ph = (time * 2.2f) % 1f;
        c.fillCircle(hx + hr * 1.25f, hy - hr * 0.2f + ph * hr, 2.6f * s, 0xAA8FD3FF);
        float ph2 = (time * 2.2f + 0.5f) % 1f;
        c.fillCircle(hx - hr * 1.25f, hy - hr * 0.3f + ph2 * hr, 2.2f * s, 0xAA8FD3FF);
    }

    private void bubble(Gfx c, float x, float y, String s, int col, float k) {
        float w = (s.length() > 1 ? 44 : 34) * k * Math.max(0.9f, so * 0.8f), hh = 34 * k * Math.max(0.9f, so * 0.8f);
        y = Math.max(y, stT + hh * 0.5f + 3);
        c.fillPoly(new float[]{x - 7 * k, y + hh * 0.4f, x + 7 * k, y + hh * 0.4f, x + (x < 180 ? 10 : -10), y + hh * 0.4f + 14 * k}, col);
        c.fillRoundRect(x - w / 2, y - hh / 2, w, hh, 10, col);
        c.strokeRoundRect(x - w / 2, y - hh / 2, w, hh, 10, 2.5f, 0xFFFFFFFF);
        c.text(s, x, y + hh * 0.3f, hh * 0.8f, 0xFFFFFFFF, Gfx.CENTER);
    }

    private void drawPlayer(Gfx c) {
        float x = pX;
        if (endKind == 2 || endKind == 5) {
            drawSit(c, pl, x, pFy - 16 * sp, sp, false);
            return;
        }
        float yoff = 0;
        if (flyT > 0) yoff = -(float) Math.sin(flyT / 0.6f * Math.PI) * 12 * sp;
        else if (endKind == 1 || endKind == 3) yoff = -(float) Math.abs(Math.sin(time * 7)) * 10 * sp;
        float tilt = flyT > 0 ? (1 - flyT / 0.6f) * 0 : 0;
        float look = 0;
        // Afterimage while dodging.
        if (dT > DODGE_T - 0.2f && dDir != 0) {
            float gx = 180;
            c.fillCircle(gx, pFy - 78 * sp, pl.head * sp, 0x2AFFFFFF);
            c.fillRoundRect(gx - 16 * sp, pFy - 66 * sp, 32 * sp, 40 * sp, 8, 0x22FFFFFF);
        }
        fighter(c, pl, x, pFy + yoff + tilt, sp, pPose, pFace, time, look);
        if (bT > 0) shield(c, x, pFy - 96 * sp, 13 * sp, 0xDD5AA9FF);
        if (hurtP > 0) {
            for (int i = 0; i < 3; i++) {
                float a = time * 8 + i * 2.09f;
                star(c, headX + (float) Math.cos(a) * headR * 1.4f, headY - headR * 1.2f + (float) Math.sin(a) * headR * 0.4f,
                        4.5f * sp, time * 5, 0xFFFFE27A);
            }
        }
        if (charging) {
            // Charge ring around the cocked fist: fills as you hold, turns into a gold star when ready.
            float fx0 = x + (pl.sh + 16) * sp, fy0 = pFy - 30 * sp + 36 * sp - 4;
            float f = Math.min(1, chargeT / CH);
            int segs = 14;
            for (int s = 0; s < segs; s++) {
                if ((s + 0.5f) / segs > f) break;
                double a = -Math.PI / 2 + (s + 0.5) / segs * Math.PI * 2;
                c.fillCircle(fx0 + (float) Math.cos(a) * 17 * sp, fy0 + (float) Math.sin(a) * 17 * sp, 3, chargeFull ? GOLD : Ui.DIM);
            }
            if (chargeFull) star(c, fx0, fy0, 11 * sp + 2 * (float) Math.sin(time * 20), time * 5, GOLD);
        }
    }

    /**
     * Draws a standing fighter from a pose. (cx, fy) is where the feet stand, sc the scale. Sets
     * headX / headY / headR for whoever needs to attach something to the head.
     */
    private void fighter(Gfx c, Style st, float cx, float fy, float sc, float[] p, int face, float t, float look) {
        float spread = (st.spread0 + p[SPREAD]) * sc;
        float hipY = fy - st.leg * sc + Math.max(0, p[CROUCH]) * 0.45f * sc;
        float shY = hipY - st.torso * sc + p[CROUCH] * 0.55f * sc;
        float shx = cx + p[LEAN] * sc;
        float sw = st.sh * sc, hw = st.hip * sc, hx2 = st.hip * 0.5f * sc;
        float lw = st.armW * 1.15f * sc;
        // Legs and shoes.
        for (int s = -1; s <= 1; s += 2) {
            float fx0 = cx + s * (hx2 + spread);
            c.line(cx + s * hx2, hipY, fx0, fy - 3 * sc, lw, st.pants);
            c.fillPoly(new float[]{fx0 - 8 * sc, fy, fx0 + 8 * sc, fy, fx0 + 9 * sc, fy - 4 * sc, fx0 + 1 * sc, fy - 8 * sc,
                    fx0 - 8 * sc, fy - 6 * sc}, st.shoe);
            c.fillRect(fx0 - 8 * sc, fy - 1.6f * sc, 17 * sc, 1.6f * sc, st.big ? 0xFFB7BCC6 : 0xFFFFFFFF);
        }
        // Torso.
        c.fillPoly(new float[]{shx - sw, shY, shx + sw, shY, cx + hw, hipY, cx - hw, hipY}, st.shirt);
        c.fillPoly(new float[]{shx - sw, shY, shx - sw * 0.55f, shY, cx - hw * 0.55f, hipY, cx - hw, hipY}, st.shirt2);
        c.fillRect(cx - hw * 1.02f, hipY - 3 * sc, hw * 2.04f, 6 * sc, st.big ? 0xFF2A3350 : 0xFF3F4552);
        if (st.big) {
            // Tank top: bare shoulders, a V neck, a gold chain, a stripe.
            c.fillPoly(new float[]{shx - 8 * sc, shY, shx + 8 * sc, shY, shx, shY + 13 * sc}, st.skin);
            for (int i = 0; i < 4; i++) {
                float a = i / 3f;
                c.fillCircle(shx + (a - 0.5f) * 11 * sc, shY + (1 - Math.abs(a - 0.5f) * 2) * 9 * sc + 1, 1.6f * sc, st.accent);
            }
            c.fillRect(cx - hw * 0.8f, hipY - 16 * sc, hw * 1.6f, 3 * sc, 0xFFF2F2F2);
        } else {
            // Hoodie: pocket, strings, a hood bunched at the neck.
            c.fillRoundRect(cx - 9 * sc, hipY - 13 * sc, 18 * sc, 9 * sc, 3 * sc, st.shirt2);
            c.fillCircle(shx, shY + 1, 8.5f * sc, st.shirt2);
            c.line(shx - 3 * sc, shY + 3 * sc, shx - 3.5f * sc, shY + 13 * sc, 1.4f * sc, 0xFFFFFFFF);
            c.line(shx + 3 * sc, shY + 3 * sc, shx + 3.5f * sc, shY + 13 * sc, 1.4f * sc, 0xFFFFFFFF);
        }
        // Head first so guard fists can cover the chin.
        float hr = st.head * sc;
        float hx = shx + p[HDX] * sc, hy = shY - st.head * 1.05f * sc + p[HDY] * sc;
        headX = hx;
        headY = hy;
        headR = hr;
        head(c, st, hx, hy, hr, face, t, look);
        // Arms on top.
        arm(c, st, shx - sw, shY + 3 * sc, shx - sw + p[LFX] * sc, shY + 3 * sc + p[LFY] * sc, p[LBEND] * sc, -1, p[LFS], sc);
        arm(c, st, shx + sw, shY + 3 * sc, shx + sw + p[RFX] * sc, shY + 3 * sc + p[RFY] * sc, p[RBEND] * sc, 1, p[RFS], sc);
    }

    private void arm(Gfx c, Style st, float sx, float sy, float fx0, float fy0, float bend, int side, float fs, float sc) {
        float dx = fx0 - sx, dy = fy0 - sy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) len = 1;
        float nx = -dy / len, ny = dx / len;
        if (nx * side < -0.01f || (Math.abs(nx) <= 0.01f && ny < 0)) {
            nx = -nx;
            ny = -ny;
        }
        float b = bend * Ui.clamp(1 - len / (44 * sc), 0.1f, 1f);
        float ex = (sx + fx0) / 2 + nx * b, ey = (sy + fy0) / 2 + ny * b;
        float w = st.armW * sc;
        int upper = st.big ? st.skin : st.shirt;
        c.line(sx, sy, ex, ey, w, upper);
        c.line(ex, ey, fx0, fy0, w * 0.88f, st.skin);
        c.fillCircle(sx, sy, w * 0.62f, upper);
        if (st.big && side < 0) {
            // A heart tattoo, because of course he has one.
            float tx = (sx + ex) / 2, ty = (sy + ey) / 2;
            c.fillCircle(tx - 1.6f * sc, ty - 1, 2 * sc, 0xFFC0392B);
            c.fillCircle(tx + 1.6f * sc, ty - 1, 2 * sc, 0xFFC0392B);
            c.fillPoly(new float[]{tx - 3.4f * sc, ty - 0.2f, tx + 3.4f * sc, ty - 0.2f, tx, ty + 4 * sc}, 0xFFC0392B);
        }
        if (!st.big) {
            float wx = fx0 + (ex - fx0) * 0.22f, wy = fy0 + (ey - fy0) * 0.22f;
            c.fillCircle(wx, wy, w * 0.62f, st.accent);
        }
        float fr = (st.big ? 7.2f : 6.0f) * sc * fs;
        c.fillCircle(fx0, fy0, fr, st.skin);
        c.fillCircle(fx0 - fr * 0.25f, fy0 - fr * 0.3f, fr * 0.35f, st.big ? 0x33FFFFFF : 0x44FFFFFF);
        c.line(fx0 - fr * 0.55f, fy0 + fr * 0.15f, fx0 + fr * 0.55f, fy0 + fr * 0.15f, Math.max(1, sc * 0.9f), 0x33000000);
        if (st.big && fs > 1.1f) c.fillCircle(fx0 + fr * 0.45f, fy0 + fr * 0.45f, fr * 0.22f, st.accent);
    }

    private void head(Gfx c, Style st, float hx, float hy, float r, int face, float t, float look) {
        // Ponytail swings behind the head.
        if (st.female) {
            float sw = (float) Math.sin(t * 5) * r * 0.25f;
            c.line(hx + r * 0.7f, hy - r * 0.6f, hx + r * 1.5f + sw, hy + r * 0.1f, r * 0.7f, st.hair);
            c.fillCircle(hx + r * 1.55f + sw, hy + r * 0.3f, r * 0.45f, st.hair);
            c.fillCircle(hx + r * 0.85f, hy - r * 0.62f, r * 0.2f, st.accent);
        }
        // Neck, ears, head.
        c.fillRect(hx - r * 0.35f, hy + r * 0.5f, r * 0.7f, r * 0.9f, st.skin);
        c.fillCircle(hx - r * 0.98f, hy + r * 0.05f, r * 0.26f, st.skin);
        c.fillCircle(hx + r * 0.98f, hy + r * 0.05f, r * 0.26f, st.skin);
        c.fillCircle(hx, hy, r, st.skin);
        if (st.big) {
            // Square jaw and stubble.
            c.fillPoly(new float[]{hx - r * 0.92f, hy, hx + r * 0.92f, hy, hx + r * 0.72f, hy + r * 1.0f, hx - r * 0.72f,
                    hy + r * 1.0f}, st.skin);
            for (int i = 0; i < 7; i++) {
                c.fillCircle(hx + (i - 3) * r * 0.22f, hy + r * (0.78f + (i % 2) * 0.1f), r * 0.045f, 0x66201410);
            }
        }
        // Hair.
        if (st.big) {
            c.fillPoly(new float[]{hx - r * 0.98f, hy - r * 0.1f, hx - r * 0.92f, hy - r * 1.3f, hx + r * 0.92f, hy - r * 1.3f,
                    hx + r * 0.98f, hy - r * 0.1f, hx + r * 0.72f, hy - r * 0.55f, hx - r * 0.72f, hy - r * 0.55f}, st.hair);
        } else if (st.female) {
            c.fillPoly(new float[]{hx - r * 1.02f, hy + r * 0.25f, hx - r * 1.0f, hy - r * 0.8f, hx - r * 0.4f, hy - r * 1.22f,
                    hx + r * 0.5f, hy - r * 1.18f, hx + r * 1.0f, hy - r * 0.6f, hx + r * 0.95f, hy + r * 0.2f, hx + r * 0.6f,
                    hy - r * 0.55f, hx - r * 0.5f, hy - r * 0.5f}, st.hair);
        } else {
            c.fillPoly(new float[]{hx - r * 1.0f, hy - r * 0.05f, hx - r * 0.95f, hy - r * 0.9f, hx - r * 0.55f, hy - r * 0.75f,
                    hx - r * 0.3f, hy - r * 1.35f, hx + r * 0.1f, hy - r * 0.85f, hx + r * 0.5f, hy - r * 1.3f,
                    hx + r * 0.72f, hy - r * 0.7f, hx + r * 1.0f, hy - r * 0.75f, hx + r * 1.0f, hy - r * 0.05f,
                    hx + r * 0.65f, hy - r * 0.5f, hx - r * 0.65f, hy - r * 0.5f}, st.hair);
        }
        face(c, st, hx, hy, r, face, t, look);
    }

    private void face(Gfx c, Style st, float hx, float hy, float r, int face, float t, float look) {
        float ex = r * 0.40f, ey = hy - r * 0.02f, er = r * 0.24f;
        int ink = 0xFF14101A;
        int browCol = st.big ? st.hair : st.hair;
        float browW = Math.max(1.5f, r * (st.big ? 0.26f : 0.17f));
        float slantL = 0, slantR = 0; // positive = angry
        boolean eyesOpen = true;
        switch (face) {
            case F_ANGRY:
                slantL = slantR = 0.8f;
                break;
            case F_OUCH:
                slantL = slantR = -0.8f;
                eyesOpen = false;
                break;
            case F_DIZZY:
                slantL = slantR = -0.3f;
                break;
            case F_SMUG:
                slantL = 0.5f;
                slantR = -0.4f;
                break;
            case F_EFFORT:
                slantL = slantR = 1.0f;
                eyesOpen = false;
                break;
            case F_PANT:
                slantL = slantR = -0.6f;
                break;
            case F_WORRY:
                slantL = slantR = -0.9f;
                break;
            case F_GRIN:
                eyesOpen = false;
                break;
            default:
                break;
        }
        // Eyes.
        for (int s = -1; s <= 1; s += 2) {
            float x = hx + s * ex;
            if (face == F_DIZZY) {
                c.fillCircle(x, ey, er * 1.35f, 0xFFFFFFFF);
                c.fillCircle(x, ey, er * 1.0f, ink);
                c.fillCircle(x, ey, er * 0.72f, 0xFFFFFFFF);
                c.fillCircle(x, ey, er * 0.4f, ink);
                float a = t * 7 + s;
                c.fillCircle(x + (float) Math.cos(a) * er * 0.5f, ey + (float) Math.sin(a) * er * 0.5f, er * 0.2f, 0xFFFFFFFF);
            } else if (face == F_OUCH) {
                float q = er * 0.9f;
                c.line(x - q, ey - q, x + q, ey + q, Math.max(1.5f, r * 0.14f), ink);
                c.line(x - q, ey + q, x + q, ey - q, Math.max(1.5f, r * 0.14f), ink);
            } else if (face == F_EFFORT) {
                c.line(x - er * 1.1f, ey, x + er * 1.1f, ey + s * er * 0.1f, Math.max(1.8f, r * 0.16f), ink);
            } else if (face == F_GRIN) {
                c.line(x - er, ey + er * 0.5f, x, ey - er * 0.6f, Math.max(1.8f, r * 0.15f), ink);
                c.line(x, ey - er * 0.6f, x + er, ey + er * 0.5f, Math.max(1.8f, r * 0.15f), ink);
            } else {
                float rr = face == F_WORRY ? er * 1.2f : er;
                c.fillCircle(x, ey, rr, 0xFFFFFFFF);
                float pr = face == F_WORRY ? rr * 0.38f : rr * 0.52f;
                c.fillCircle(x + look * rr * 0.4f, ey + (face == F_PANT ? rr * 0.25f : 0), pr, ink);
                if (face == F_SMUG || face == F_PANT) {
                    // Heavy lid over the top half.
                    float lid = face == F_PANT ? 0.85f : 0.55f;
                    c.fillRect(x - rr - 0.5f, ey - rr - 0.5f, rr * 2 + 1, rr * lid + 0.5f, st.skin);
                    c.line(x - rr, ey - rr + rr * lid, x + rr, ey - rr + rr * lid, Math.max(1.2f, r * 0.1f), ink);
                }
            }
        }
        // Brows (a single thick one for the tough).
        if (eyesOpen || face == F_EFFORT || face == F_OUCH) {
            float by = ey - er * 2.0f;
            c.line(hx - ex - er * 1.2f, by - slantL * er * 0.7f, hx - ex + er * 1.2f, by + slantL * er * 0.7f, browW, browCol);
            c.line(hx + ex - er * 1.2f, by + slantR * er * 0.7f, hx + ex + er * 1.2f, by - slantR * er * 0.7f, browW, browCol);
            if (st.big) c.line(hx - ex + er * 0.9f, by + slantL * er * 0.55f, hx + ex - er * 0.9f, by + slantR * er * 0.55f, browW, browCol);
        }
        // Nose (the tough has a plaster across his).
        if (st.big) {
            c.fillRoundRect(hx - r * 0.2f, hy + r * 0.12f, r * 0.4f, r * 0.2f, 1, 0xFFF4E3C4);
            c.line(hx - r * 0.12f, hy + r * 0.14f, hx + r * 0.12f, hy + r * 0.3f, 1, 0xFFC9B58D);
            c.line(hx + r * 0.12f, hy + r * 0.14f, hx - r * 0.12f, hy + r * 0.3f, 1, 0xFFC9B58D);
        } else {
            c.fillCircle(hx, hy + r * 0.2f, r * 0.07f, 0x33000000);
        }
        // Mouth.
        float my = hy + r * 0.58f;
        switch (face) {
            case F_ANGRY:
            case F_EFFORT: {
                c.fillRoundRect(hx - r * 0.42f, my - r * 0.08f, r * 0.84f, r * 0.3f, r * 0.08f, ink);
                c.fillRect(hx - r * 0.38f, my - r * 0.04f, r * 0.76f, r * 0.12f, 0xFFFFFFFF);
                c.fillRect(hx - r * 0.02f, my - r * 0.04f, r * 0.04f, r * 0.12f, ink);
                if (st.big) c.fillRect(hx + r * 0.14f, my - r * 0.04f, r * 0.12f, r * 0.12f, st.accent);
                break;
            }
            case F_OUCH:
                c.fillCircle(hx, my + r * 0.05f, r * 0.24f, ink);
                c.fillCircle(hx, my + r * 0.12f, r * 0.12f, 0xFFD2647A);
                break;
            case F_DIZZY: {
                float prev = hx - r * 0.4f;
                float py = my;
                for (int i = 1; i <= 4; i++) {
                    float nx = hx - r * 0.4f + i * r * 0.2f;
                    float ny = my + ((i % 2 == 0) ? -1 : 1) * r * 0.08f + (float) Math.sin(t * 6) * r * 0.04f;
                    c.line(prev, py, nx, ny, Math.max(1.5f, r * 0.11f), ink);
                    prev = nx;
                    py = ny;
                }
                break;
            }
            case F_SMUG:
                c.line(hx - r * 0.3f, my, hx + r * 0.1f, my + r * 0.04f, Math.max(1.5f, r * 0.11f), ink);
                c.line(hx + r * 0.1f, my + r * 0.04f, hx + r * 0.42f, my - r * 0.14f, Math.max(1.5f, r * 0.11f), ink);
                break;
            case F_PANT:
                c.fillCircle(hx, my + r * 0.08f, r * 0.22f, ink);
                c.fillPoly(new float[]{hx - r * 0.12f, my + r * 0.12f, hx + r * 0.14f, my + r * 0.12f, hx + r * 0.12f,
                        my + r * 0.55f + (float) Math.sin(t * 9) * r * 0.05f, hx - r * 0.1f, my + r * 0.5f}, 0xFFEE7C92);
                break;
            case F_WORRY: {
                float prev = hx - r * 0.3f;
                float py = my + r * 0.1f;
                for (int i = 1; i <= 3; i++) {
                    float nx = hx - r * 0.3f + i * r * 0.2f;
                    float ny = my + r * 0.1f + ((i % 2 == 1) ? -1 : 1) * r * 0.07f;
                    c.line(prev, py, nx, ny, Math.max(1.5f, r * 0.11f), ink);
                    prev = nx;
                    py = ny;
                }
                break;
            }
            case F_GRIN: {
                c.fillPoly(new float[]{hx - r * 0.5f, my - r * 0.1f, hx + r * 0.5f, my - r * 0.1f, hx + r * 0.38f, my + r * 0.3f,
                        hx, my + r * 0.46f, hx - r * 0.38f, my + r * 0.3f}, ink);
                c.fillRect(hx - r * 0.44f, my - r * 0.1f, r * 0.88f, r * 0.14f, 0xFFFFFFFF);
                break;
            }
            default:
                c.line(hx - r * 0.25f, my, hx + r * 0.25f, my, Math.max(1.5f, r * 0.11f), ink);
                break;
        }
        if (face == F_EFFORT) {
            // Puffed, flushed cheeks.
            c.fillCircle(hx - r * 0.72f, hy + r * 0.45f, r * 0.2f, 0x44FF6B6B);
            c.fillCircle(hx + r * 0.72f, hy + r * 0.45f, r * 0.2f, 0x44FF6B6B);
        }
    }

    /** The loser, sitting on the ground, seeing stars. (cx, gy) is the sitting spot. */
    private void drawSit(Gfx c, Style st, float cx, float gy, float s, boolean isOpp) {
        float wob = (float) Math.sin(time * 2.4f);
        float k = Math.min(1f, endT * 3f);
        float drop = (1 - k) * 18 * s;
        gy -= drop * 0;
        c.fillRoundRect(cx - 34 * s, gy - 4 * s, 68 * s, 11 * s, 5 * s, 0x66000000);
        float hipY = gy - 8 * s;
        float hw = st.hip * s;
        // Legs splayed toward us, shoes pointing up.
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float fx0 = cx + sgn * 25 * s, fy0 = gy + 9 * s;
            c.line(cx + sgn * hw * 0.5f, hipY, fx0, fy0, st.armW * 1.2f * s, st.pants);
            c.fillPoly(new float[]{fx0 - 8 * s, fy0 + 3 * s, fx0 + 8 * s, fy0 + 3 * s, fx0 + 7 * s, fy0 - 8 * s, fx0 - 7 * s, fy0 - 8 * s}, st.shoe);
            c.fillRect(fx0 - 8 * s, fy0 + 1 * s, 16 * s, 2 * s, 0xFFB7BCC6);
        }
        float shY = hipY - st.torso * 0.82f * s;
        float shx = cx + wob * 3 * s;
        float sw = st.sh * s;
        c.fillPoly(new float[]{shx - sw, shY, shx + sw, shY, cx + hw, hipY, cx - hw, hipY}, st.shirt);
        c.fillPoly(new float[]{shx - sw, shY, shx - sw * 0.55f, shY, cx - hw * 0.55f, hipY, cx - hw, hipY}, st.shirt2);
        if (st.big) {
            c.fillPoly(new float[]{shx - 8 * s, shY, shx + 8 * s, shY, shx, shY + 12 * s}, st.skin);
        } else {
            c.fillCircle(shx, shY + 1, 8 * s, st.shirt2);
        }
        // Limp arms resting on the ground.
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float sx = shx + sgn * sw, sy = shY + 3 * s;
            float hx0 = cx + sgn * (sw + 17 * s), hy0 = gy + 2 * s;
            float ex = sx + sgn * 12 * s, ey = (sy + hy0) * 0.5f;
            c.line(sx, sy, ex, ey, st.armW * s, st.big ? st.skin : st.shirt);
            c.line(ex, ey, hx0, hy0, st.armW * 0.88f * s, st.skin);
            c.fillCircle(hx0, hy0, 5.5f * s, st.skin);
            c.fillCircle(sx, sy, st.armW * 0.62f * s, st.big ? st.skin : st.shirt);
        }
        float hr = st.head * s;
        float hx = shx + wob * 5 * s, hy = shY - st.head * 0.85f * s + 3 * s;
        head(c, st, hx, hy, hr, F_DIZZY, time, wob);
        // Stars circling over the head.
        for (int i = 0; i < 4; i++) {
            float a = time * 4.5f + i * 1.57f;
            star(c, hx + (float) Math.cos(a) * hr * 1.5f, hy - hr * 1.35f + (float) Math.sin(a) * hr * 0.4f, 5 * s, time * 3 + i, 0xFFFFE27A);
        }
        // Little "?" and a lump to be proud of.
        c.fillCircle(hx - hr * 0.55f, hy - hr * 1.05f, hr * 0.17f, 0xFFF0A070);
    }

    // =====================================================================================
    // Drawing: overlays, HUD, pads
    // =====================================================================================

    private void overlays(Gfx c) {
        // Opening bar above the opponent: how long you can hit him, and whether a heavy counter fits.
        if (canStrike() && endKind == 0) {
            float rem = Math.max(0, openRemain());
            float frac = Ui.clamp(rem / 1.3f, 0, 1);
            float bw = 120, bx = 180 - bw / 2, by = oFy - 112 * so - 14;
            by = Math.max(by, stT + 6);
            boolean heavyFits = rem >= (charging ? CH - chargeT : CH) + 0.05f;
            int col = heavyFits ? GOLD : Ui.ORANGE;
            c.fillRoundRect(bx - 3, by - 3, bw + 6, 16, 8, 0xCC10141A);
            Ui.bar(c, bx, by, bw, 10, frac, col);
            // Marker where a heavy counter stops fitting.
            float mk = bx + bw * Ui.clamp(CH / 1.3f, 0, 1);
            c.fillPoly(new float[]{mk, by + 12, mk - 4, by + 18, mk + 4, by + 18}, 0xFFFFFFFF);
            c.text(heavyFits ? "פתוח!" : "מהר!", bx + bw + 10, by + 10, 13, col, Gfx.LEFT);
        }
        // Hint ribbon (first time each thing appears).
        if (hintT > 0 && endKind == 0) {
            float w = Math.min(340, c.textWidth(hintText, 14) + 26);
            // Over the empty wall when the screen is tall, else on the floor above the buttons.
            float y = gT - stT > 40 ? stT + 22 : stB - 8;
            int a = (int) (Ui.clamp(hintT / 0.4f, 0, 1) * 225);
            c.fillRoundRect(180 - w / 2, y - 17, w, 26, 13, (a << 24) | 0x10141A);
            c.text(hintText, 180, y + 1, 14, (a << 24) | 0xFFFFFF, Gfx.CENTER);
        }
        if (intro > 0) {
            c.text("מוכנים?", 180, stT + (stB - stT) * 0.30f, 26, Ui.INK, Gfx.CENTER);
        }
    }

    private void hud(Gfx c, float top) {
        float y = top + 20;
        String me = h.t("אתה", "את");
        // Labels sit over their bars; the bars drain as stamina is lost.
        stamBar(c, 12, y, 156, pStam, me, false);
        stamBar(c, 192, y, 156, oStam, "הבריון", true);
        // Round timer.
        float f = Ui.clamp(1 - bout / BELL, 0, 1);
        Ui.bar(c, 12, top + 40 - 4, 336, 4, f, f < 0.2f ? Ui.BAD : Ui.DIM);
    }

    private void stamBar(Gfx c, float x, float y, float w, float v, String label, boolean enemy) {
        float f = Ui.clamp(v / 100f, 0, 1);
        int col = f > 0.5f ? Ui.GOOD : f > 0.25f ? GOLD : Ui.BAD;
        boolean blink = f < 0.25f && ((int) (time * 6) % 2 == 0);
        c.text(label, x + w, y - 4, 13, enemy ? 0xFFFFB199 : Ui.INK, Gfx.RIGHT);
        c.text(String.valueOf(Math.round(v)), x, y - 4, 13, Ui.DIM, Gfx.LEFT);
        c.fillRoundRect(x - 1, y - 1, w + 2, 15, 7.5f, blink ? 0xFFFF6B6B : 0xFF0B0E12);
        Ui.bar(c, x, y, w, 13, f, col);
        // Segment ticks make the amount readable without colour.
        for (int i = 1; i < 4; i++) c.fillRect(x + w * i / 4f - 0.75f, y, 1.5f, 13, 0x66000000);
    }

    private void pads(Gfx c) {
        float y = padY, hh = padH;
        float[] xs = {6, 125, 244};
        boolean can = canStrike();
        boolean thr = threat();
        for (int i = 0; i < 3; i++) {
            boolean centre = i == 1;
            float flash = Ui.clamp(padFlash[i] / 0.18f, 0, 1);
            int fill = 0xFF1E2530;
            if (centre) {
                if (can) fill = 0xFF3A3220;
                else if (thr) fill = 0xFF1F2E48;
            }
            c.fillRoundRect(xs[i], y, 110, hh, 12, fill);
            if (flash > 0) c.fillRoundRect(xs[i], y, 110, hh, 12, ((int) (flash * 70) << 24) | 0xFFFFFF);
            int edge = centre ? (can ? GOLD : thr ? Ui.BLUE : 0xFF3A4556) : (dLock > 0 ? 0xFF2C3644 : 0xFF4A5A70);
            c.strokeRoundRect(xs[i], y, 110, hh, 12, 2.5f, edge);
            float cx = xs[i] + 55, cy = y + hh * 0.42f;
            if (!centre) {
                int dir = i == 0 ? -1 : 1;
                int col = dLock > 0 ? Ui.DIM : Ui.INK;
                chevron(c, cx - dir * 4 - 8 * dir, cy, 12, dir, col);
                chevron(c, cx - dir * 4 + 8 * dir, cy, 12, dir, col);
                c.text("התחמקות", cx, y + hh - 7, 13, Ui.DIM, Gfx.CENTER);
            } else if (thr) {
                shield(c, cx, cy - 2, 11, bLock > 0 ? Ui.DIM : Ui.BLUE);
                c.text("חסימה", cx, y + hh - 7, 13, bLock > 0 ? Ui.DIM : Ui.INK, Gfx.CENTER);
            } else {
                fist(c, cx, cy, 10, can ? GOLD : Ui.DIM);
                String lab = charging ? (chargeFull ? "שחרר!" : "ממשיכים...") : can ? "מכה" : "מכה";
                c.text(lab, cx, y + hh - 7, 13, can ? Ui.INK : Ui.DIM, Gfx.CENTER);
                if (charging) {
                    Ui.bar(c, xs[i] + 12, y + 5, 86, 6, chargeT / CH, chargeFull ? GOLD : Ui.ORANGE);
                }
            }
        }
    }

    // =====================================================================================
    // Intro card art
    // =====================================================================================

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        float s = 0.9f;
        float[] g = new float[NP];
        float ph = (t * 0.9f) % 2f;
        // Phase 0..1.5: he winds up on his left; then he swings and the player is already out of the way.
        boolean swing = ph > 1.5f;
        g[LFX] = 2;
        g[LFY] = -4;
        g[RFX] = -2;
        g[RFY] = -4;
        g[LBEND] = g[RBEND] = 14;
        g[LFS] = g[RFS] = 1;
        if (!swing) {
            g[LFX] = -36;
            g[LFY] = -40;
            g[LFS] = 1.25f;
            g[LEAN] = -9;
            g[CROUCH] = 3;
        } else {
            g[LFX] = 28;
            g[LFY] = 34;
            g[LFS] = 2.2f;
            g[LEAN] = 9;
        }
        float oy = cy + 46;
        c.fillRoundRect(cx - 100, oy - 6, 200, 9, 4, 0x44000000);
        fighter(c, opp, cx - 18, oy, s, g, swing ? F_ANGRY : F_EFFORT, t, 0);
        if (!swing) bubble(c, cx - 52, oy - 112 * s, "!", RED, 1f);
        float[] p = new float[NP];
        p[LFX] = -1;
        p[LFY] = -3;
        p[RFX] = 1;
        p[RFY] = -3;
        p[LBEND] = p[RBEND] = 12;
        p[LFS] = p[RFS] = 1;
        float px = cx + 62;
        if (swing) {
            px = cx + 74;
            p[LEAN] = 10;
            p[HDX] = 5;
            p[CROUCH] = 5;
        }
        pl.female = h != null && h.female();
        fighter(c, pl, px, oy + 4, s * 0.85f, p, swing ? F_SMUG : F_WORRY, t, -1);
        if (!swing) chevron(c, cx + 112, oy - 40, 10, 1, Ui.GOOD);
    }
}
