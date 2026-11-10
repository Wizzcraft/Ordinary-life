package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Exterminator's night call, a tower defense shrunk to a phone shift: four waves of cartoon pests
 * march along a crumb trail from a hole in the wall to the cake on the counter. Between and during
 * waves the player builds on six spots (tap a card, tap a spot; tap a tower to upgrade it) with an
 * internal coin budget that refills from every pest stopped. The thumb's verb during a wave is the
 * slipper: tap a pest to smack it, with a short cooldown. What is built carries over to later waves.
 *
 * The rules run on a fixed virtual board (VW x VH) that is only stretched vertically for drawing,
 * so the balance is identical on every screen height.
 */
final class PestMini extends Mini {
    // ---- board geometry (virtual units) ----
    static final float VW = 360, VH = 300;
    static final float L1 = 36, L2 = 150, L3 = 264;
    /** The crumb trail: out of the hole in the wall, snaking down the floor to the counter. */
    static final float[] PATH = {236, -16, 236, L1, 40, L1, 40, L2, 320, L2, 320, L3, 78, L3};
    static final float[] SPOT_X = {92, 180, 268, 92, 180, 268};
    static final float[] SPOT_Y = {93, 93, 93, 207, 207, 207};
    static final int SPOTS = 6;
    private static final float[] SEG_LEN = new float[PATH.length / 2 - 1];
    static final float PATH_LEN;

    static {
        float sum = 0;
        for (int i = 0; i < SEG_LEN.length; i++) {
            SEG_LEN[i] = Math.abs(PATH[i * 2 + 2] - PATH[i * 2]) + Math.abs(PATH[i * 2 + 3] - PATH[i * 2 + 1]);
            sum += SEG_LEN[i];
        }
        PATH_LEN = sum;
    }

    // ---- towers ----
    static final int STICKY = 0, SPRAY = 1, ZAP = 2, CAT = 3, TYPES = 4;
    static final String[] T_NAME = {"דבק", "ריסוס", "מנורה", "חתול"};
    /** [type][level]: build cost at level 0, upgrade cost to reach levels 1 and 2. */
    static final int[][] COST = {{4, 4, 6}, {6, 5, 7}, {10, 7, 9}, {13, 8, 10}};
    static final float[][] RANGE = {{64, 72, 80}, {72, 78, 86}, {125, 140, 155}, {58, 64, 70}};
    static final float[][] PERIOD = {{0.5f, 0.5f, 0.5f}, {0.5f, 0.42f, 0.36f}, {1.0f, 0.85f, 0.7f}, {0.7f, 0.58f, 0.48f}};
    static final float[][] DMG = {{0, 0.4f, 0.8f}, {1.6f, 2.2f, 3.0f}, {6, 9, 13}, {4, 5.5f, 7.5f}};
    /** Sticky: speed multiplier on the glue. Spray: splash radius. */
    static final float[] SLOW = {0.55f, 0.45f, 0.38f};
    static final float[] SPLASH = {28, 32, 36};
    static final float CAT_PATROL = 44;
    /** Bug spray against each pest: a mouse only sneezes at it, the boss's shell shrugs off half. */
    static final float[] SPRAY_VS = {1, 1, 1, 0.2f, 0.5f};

    // ---- pests ----
    static final int ANT = 0, ROACH = 1, MOTH = 2, MOUSE = 3, BOSS = 4;
    static final float[] P_HP = {2.6f, 7.2f, 5f, 25, 100};
    static final float[] P_SPEED = {180, 225, 280, 140, 124};
    static final int[] P_BITE = {1, 2, 1, 3, 6};
    static final int[] P_BOUNTY = {1, 2, 2, 4, 12};
    /** Pests toughen each wave: the first one falls to two sprays anywhere, the last needs a real defence. */
    static final float[] ROUND_HP = {0.62f, 0.9f, 1.05f, 1.2f};
    static final int FOOD = 10, START_COINS = 12;

    // ---- slipper (the thumb's verb) ----
    static final float SLIP_CD = 1.0f, SLIP_R = 30, SLIP_DMG = 3;

    static final class Tower {
        int type = -1, level;
        float cool, fxT, fxX, fxY, aim, built, catX, catDir = 1, catAnim, sayT;
    }

    static final class Pest {
        int type;
        float hp, maxHp, s, hitT, stuck, seed;
        boolean dead;
        /** Cached virtual position and heading. */
        float x, y, dx, dy;
    }

    final Tower[] towers = new Tower[SPOTS];
    final ArrayList<Pest> pests = new ArrayList<>();
    private final float[] spawnT = new float[40];
    private final int[] spawnType = new int[40];
    private int spawnN, spawned;

    int coins, selected = SPRAY, round = -1, food, bites, kills;
    float diff, time, waveT, buildLeft, slipCd;
    boolean wave;
    private float slipT, slipX, slipY, foodHit, rangeT;
    private int rangeSpot = -1, resultPending = RUNNING;
    private float endT;
    private String tip = "";
    private float tipT;

    // ---- snapshots: an interrupted round restarts from the board as it stood when it began ----
    private final int[][] snapType = new int[4][SPOTS], snapLevel = new int[4][SPOTS];
    private final int[] snapCoins = new int[4], snapSel = new int[4];
    private final boolean[] snapHas = new boolean[4];

    // ---- puffs (local particles: spray mist, pops) ----
    private static final int PUFFS = 48;
    private final float[] pfX = new float[PUFFS], pfY = new float[PUFFS], pfT = new float[PUFFS], pfR = new float[PUFFS];
    private final int[] pfC = new int[PUFFS];
    private int pfNext;

    // ---- layout (screen units), refreshed every draw ----
    float top = 166, bottom = 664, bTop, bBot, sy, k, trayY, trayH, cardW;

    PestMini() {
        for (int i = 0; i < SPOTS; i++) towers[i] = new Tower();
        layout(top, bottom);
    }

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("מדביר", "מדבירה");
    }

    @Override
    String howTo() {
        return h.t("בוחרים מלכודת למטה ולוחצים על עיגול זוהר כדי לבנות, ועל מלכודת כדי לשדרג. "
                        + "בזמן הגל מכים מזיקים עם הכפכף. שומרים על העוגה!",
                "בוחרות מלכודת למטה ולוחצות על עיגול זוהר כדי לבנות, ועל מלכודת כדי לשדרג. "
                        + "בזמן הגל מכות מזיקים עם הכפכף. שומרות על העוגה!");
    }

    // =========================================================================================
    // rounds
    // =========================================================================================

    @Override
    void begin(int r, float difficulty) {
        r = Math.max(0, Math.min(3, r));
        if (r == 0) {
            for (Tower t : towers) {
                t.type = -1;
                t.level = 0;
            }
            coins = START_COINS;
            selected = SPRAY;
            for (int i = 0; i < 4; i++) snapHas[i] = false;
            save(0);
        } else if (snapHas[r] && r <= round) {
            // A replay of this round: the board exactly as it stood when the round first began.
            restore(r);
        } else if (round < 0 && h.var("pest.round") == r) {
            // The app was closed mid-shift and this is a fresh Mini: the board comes back from vars.
            loadVars();
            save(r);
        } else {
            save(r);
            for (int i = r + 1; i < 4; i++) snapHas[i] = false;
        }
        saveVars(r);
        round = r;
        diff = Ui.clamp(difficulty, 0, 1);
        pests.clear();
        food = FOOD;
        bites = kills = 0;
        wave = false;
        waveT = 0;
        buildLeft = r == 0 ? 2.4f : 1.4f;
        slipCd = 0;
        slipT = 0;
        foodHit = 0;
        endT = 0;
        resultPending = RUNNING;
        rangeSpot = -1;
        for (Tower t : towers) {
            t.cool = 0.3f;
            t.fxT = 0;
            t.catX = 0;
            t.catAnim = 0;
        }
        for (int i = 0; i < PUFFS; i++) pfT[i] = 0;
        planWave(r);
        tip = r == 0 ? h.t("לחץ על עיגול זוהר כדי לבנות", "לחצי על עיגול זוהר כדי לבנות")
                : r == 3 ? "הבוס בדרך!" : "בונים לפני הגל";
        tipT = 99;
    }

    private void save(int r) {
        for (int i = 0; i < SPOTS; i++) {
            snapType[r][i] = towers[i].type;
            snapLevel[r][i] = towers[i].level;
        }
        snapCoins[r] = coins;
        snapSel[r] = selected;
        snapHas[r] = true;
    }

    /** The board at the start of round r, kept in vars so it survives the app being closed mid-shift. */
    private void saveVars(int r) {
        h.setVar("pest.round", r);
        h.setVar("pest.coins", coins);
        for (int i = 0; i < SPOTS; i++) h.setVar("pest.t" + i, towers[i].type * 3 + towers[i].level + 3);
    }

    private void loadVars() {
        coins = Math.max(0, (int) h.var("pest.coins"));
        for (int i = 0; i < SPOTS; i++) {
            int v = (int) h.var("pest.t" + i) - 3;
            boolean ok = v >= 0 && v < TYPES * 3;
            towers[i].type = ok ? v / 3 : -1;
            towers[i].level = ok ? v % 3 : 0;
        }
    }

    private void restore(int r) {
        for (int i = 0; i < SPOTS; i++) {
            towers[i].type = snapType[r][i];
            towers[i].level = snapLevel[r][i];
        }
        coins = snapCoins[r];
        selected = snapSel[r];
    }

    /** The wave's schedule: escalating counts and a new kind of pest each round, the boss last. */
    private void planWave(int r) {
        spawnN = spawned = 0;
        float m = 1 + diff * 0.5f;
        switch (r) {
            case 0:
                add(ROACH, 0.0f, 4, 0.5f);
                add(ANT, 1.8f, Math.round(5 * m), 0.2f);
                add(ROACH, 2.8f, Math.round(1 * m), 0.4f);
                break;
            case 1:
                add(MOUSE, 0.0f, 1, 0);
                add(ANT, 0.5f, Math.round(7 * m), 0.2f);
                add(ROACH, 1.6f, Math.round(5 * m), 0.32f);
                break;
            case 2:
                add(MOUSE, 0.0f, 2 + Math.round(diff), 0.7f);
                add(MOTH, 0.9f, Math.round(5 * m), 0.3f);
                add(ANT, 1.3f, Math.round(6 * m), 0.18f);
                add(ROACH, 2.2f, Math.round(4 * m), 0.32f);
                break;
            default:
                add(BOSS, 0.0f, 1, 0);
                add(ANT, 0.4f, Math.round(6 * m), 0.2f);
                add(ROACH, 1.2f, Math.round(5 * m), 0.3f);
                add(MOTH, 2.0f, Math.round(4 * m), 0.28f);
                add(MOUSE, 2.4f, 1 + Math.round(diff * 2), 0.5f);
                break;
        }
    }

    private void add(int type, float at, int n, float gap) {
        for (int i = 0; i < n && spawnN < spawnT.length; i++) {
            spawnT[spawnN] = at + i * gap;
            spawnType[spawnN++] = type;
        }
    }

    @Override
    int update(float dt) {
        time += dt;
        if (tipT < 50) tipT -= dt;
        if (slipCd > 0) slipCd -= dt;
        if (slipT > 0) slipT -= dt;
        if (foodHit > 0) foodHit -= dt;
        if (rangeT > 0) rangeT -= dt;
        for (int i = 0; i < PUFFS; i++) if (pfT[i] > 0) pfT[i] -= dt;
        for (Tower t : towers) {
            if (t.fxT > 0) t.fxT -= dt;
            if (t.built > 0) t.built -= dt;
            if (t.sayT > 0) t.sayT -= dt;
        }
        if (resultPending != RUNNING) {
            // A short beat so the last pop or the last bite is seen before the banner.
            endT += dt;
            return endT > 0.35f ? resultPending : RUNNING;
        }
        if (!wave) {
            buildLeft -= dt;
            idleCats(dt);
            if (buildLeft <= 0) {
                wave = true;
                waveT = 0;
                tip = round == 0 ? h.t("טפח על מזיק כדי להכות בכפכף", "טפחי על מזיק כדי להכות בכפכף") : "";
                tipT = round == 0 ? 3.5f : 0;
                h.sfx(Sfx.DOOR);
            }
            return RUNNING;
        }
        waveT += dt;
        while (spawned < spawnN && spawnT[spawned] <= waveT) spawn(spawnType[spawned++]);
        movePests(dt);
        if (food <= 0) {
            food = 0;
            h.text(sx(40), syv(L3) - 40, "העוגה נהרסה", Ui.BAD);
            h.shake(9);
            h.sfx(Sfx.BAD);
            resultPending = MISS;
            return RUNNING;
        }
        fireTowers(dt);
        sweepDead();
        if (spawned >= spawnN && pests.isEmpty()) {
            int bonus = 3 + round;
            coins += bonus;
            h.text(W / 2, top + 60, "סיום גל +" + bonus, Ui.ACCENT);
            resultPending = bites == 0 ? PERFECT : OK;
            if (bites == 0) h.burst(sx(40), syv(L3) - 10, Ui.ACCENT, 14);
        }
        return RUNNING;
    }

    private void spawn(int type) {
        Pest p = new Pest();
        p.type = type;
        p.maxHp = p.hp = P_HP[type] * ROUND_HP[round] * (1 + diff * 0.4f);
        p.s = 0;
        p.seed = (spawned * 0.618f) % 1f;
        place(p);
        pests.add(p);
        if (type == BOSS) {
            h.text(sx(300), syv(L1) + 34, "הג'וק הגדול!", Ui.ORANGE);
            h.shake(5);
            h.sfx(Sfx.SIREN);
        }
    }

    private void movePests(float dt) {
        for (int i = 0; i < pests.size(); i++) {
            Pest p = pests.get(i);
            if (p.dead) continue;
            if (p.hitT > 0) p.hitT -= dt;
            float slow = 1;
            p.stuck = 0;
            if (p.type != MOTH) {
                for (int s = 0; s < SPOTS; s++) {
                    Tower t = towers[s];
                    if (t.type != STICKY) continue;
                    if (dist2(p.x, p.y, SPOT_X[s], SPOT_Y[s]) > sq(RANGE[STICKY][t.level])) continue;
                    // The boss is too heavy for weak glue.
                    float f = p.type == BOSS ? 0.5f + SLOW[t.level] * 0.5f : SLOW[t.level];
                    if (f < slow) slow = f;
                    p.stuck = 1;
                }
            }
            // A hit makes a pest flinch for a moment.
            float flinch = p.hitT > 0.12f ? 0.35f : 1;
            p.s += P_SPEED[p.type] * slow * flinch * dt;
            place(p);
            if (p.s >= PATH_LEN) {
                p.dead = true;
                int b = Math.max(0, Math.min(food, P_BITE[p.type]));
                food -= P_BITE[p.type];
                bites += b;
                foodHit = 0.5f;
                h.text(sx(40), syv(L3) - 36, "-" + P_BITE[p.type], Ui.BAD);
                h.shake(p.type == BOSS ? 8 : 3);
                h.sfx(Sfx.EAT);
            }
        }
    }

    private void fireTowers(float dt) {
        for (int s = 0; s < SPOTS; s++) {
            Tower t = towers[s];
            if (t.type < 0) continue;
            t.cool -= dt;
            int lv = t.level;
            if (t.type == CAT) {
                catThink(t, s, dt);
                continue;
            }
            if (t.type == STICKY) {
                if (DMG[STICKY][lv] > 0 && t.cool <= 0) {
                    t.cool = PERIOD[STICKY][lv];
                    for (Pest p : pests) {
                        if (p.dead || p.type == MOTH) continue;
                        if (dist2(p.x, p.y, SPOT_X[s], SPOT_Y[s]) <= sq(RANGE[STICKY][lv])) hurt(p, DMG[STICKY][lv], false);
                    }
                }
                continue;
            }
            Pest best = target(SPOT_X[s], SPOT_Y[s], RANGE[t.type][lv], true);
            if (best != null) t.aim = (float) Math.atan2(best.y - SPOT_Y[s], best.x - SPOT_X[s]);
            if (best == null || t.cool > 0) continue;
            t.cool = PERIOD[t.type][lv];
            t.fxT = t.type == ZAP ? 0.14f : 0.2f;
            t.fxX = best.x;
            t.fxY = best.y;
            if (t.type == SPRAY) {
                float rr = sq(SPLASH[lv]);
                for (Pest p : pests) {
                    if (!p.dead && dist2(p.x, p.y, best.x, best.y) <= rr) hurt(p, DMG[SPRAY][lv] * SPRAY_VS[p.type], false);
                }
                for (int j = 0; j < 3; j++) {
                    float a = j * 2.1f + time * 3;
                    puff(best.x + (float) Math.cos(a) * 9, best.y + (float) Math.sin(a) * 9, SPLASH[lv] * 0.55f,
                            0x5599E6B0);
                }
            } else {
                // Moths fly straight into the lamp's light: double damage.
                hurt(best, DMG[ZAP][lv] * (best.type == MOTH ? 2 : 1), false);
                h.sfx(Sfx.BEEP);
            }
        }
    }

    /** The cat strolls back and forth beside its cushion and swats whatever walks past. */
    private void catThink(Tower t, int s, float dt) {
        Pest prey = null;
        float best = sq(RANGE[CAT][t.level] + CAT_PATROL);
        for (Pest p : pests) {
            if (p.dead || p.type == MOTH) continue;
            float d = dist2(p.x, p.y, SPOT_X[s] + t.catX, SPOT_Y[s]);
            if (d < best) {
                best = d;
                prey = p;
            }
        }
        float want;
        if (prey != null) {
            want = Ui.clamp(prey.x - SPOT_X[s], -CAT_PATROL, CAT_PATROL);
        } else {
            if (Math.abs(t.catX) >= CAT_PATROL - 1) t.catDir = -Math.signum(t.catX);
            want = t.catDir * CAT_PATROL;
        }
        float step = (prey != null ? 110 : 45) * dt;
        t.catX += Ui.clamp(want - t.catX, -step, step);
        t.catAnim += dt * (Math.abs(want - t.catX) > 1 ? 1 : 0.2f);
        if (prey != null && t.cool <= 0
                && dist2(prey.x, prey.y, SPOT_X[s] + t.catX, SPOT_Y[s]) <= sq(RANGE[CAT][t.level])) {
            t.cool = PERIOD[CAT][t.level];
            t.fxT = 0.2f;
            t.fxX = prey.x;
            t.fxY = prey.y;
            hurt(prey, DMG[CAT][t.level] * (prey.type == MOUSE ? 2 : 1), false);
            if (t.sayT <= 0 && ((int) (time * 7 + s) % 3 == 0)) {
                h.text(sx(SPOT_X[s] + t.catX), syv(SPOT_Y[s]) - 26, prey.type == MOUSE ? "מיאו!!" : "פאץ'!", Ui.ORANGE);
                t.sayT = 1.5f;
            }
        }
    }

    private void idleCats(float dt) {
        for (Tower t : towers) {
            if (t.type != CAT) continue;
            float step = 40 * dt;
            t.catX += Ui.clamp(-t.catX, -step, step);
        }
    }

    /** The pest furthest along the trail within range (flying ones only if {@code air}). */
    private Pest target(float x, float y, float range, boolean air) {
        Pest best = null;
        float r2 = range * range;
        for (Pest p : pests) {
            if (p.dead || (!air && p.type == MOTH)) continue;
            if (dist2(p.x, p.y, x, y) > r2) continue;
            if (best == null || p.s > best.s) best = p;
        }
        return best;
    }

    private void hurt(Pest p, float dmg, boolean slipper) {
        if (p.dead) return;
        p.hp -= dmg;
        p.hitT = 0.2f;
        if (p.hp > 0) return;
        p.dead = true;
        kills++;
        int bounty = P_BOUNTY[p.type];
        coins += bounty;
        float x = sx(p.x), y = syv(p.y);
        h.text(x, y - 14, "+" + bounty, Ui.ACCENT);
        h.burst(x, y, p.type == BOSS ? Ui.ACCENT : 0xFFE9D8A6, p.type == BOSS ? 22 : 7);
        for (int j = 0; j < (p.type == BOSS ? 5 : 2); j++) puff(p.x + j * 5 - 4, p.y - j * 3, 14 + j * 4, 0xCCE9EDF2);
        if (p.type == BOSS) {
            h.text(x, y - 40, "הבוס הודח!", Ui.GOOD);
            h.shake(6);
            h.sfx(Sfx.PERFECT);
        } else {
            h.sfx(slipper ? Sfx.PUNCH : Sfx.COIN);
        }
    }

    private void sweepDead() {
        for (int i = pests.size() - 1; i >= 0; i--) if (pests.get(i).dead) pests.remove(i);
    }

    private void puff(float x, float y, float r, int col) {
        int i = pfNext;
        pfNext = (pfNext + 1) % PUFFS;
        pfX[i] = x;
        pfY[i] = y;
        pfR[i] = r;
        pfC[i] = col;
        pfT[i] = 0.4f;
    }

    /** Virtual position and heading of a pest from its distance along the trail. */
    private static void place(Pest p) {
        float d = Math.min(p.s, PATH_LEN);
        for (int i = 0; i < SEG_LEN.length; i++) {
            if (d <= SEG_LEN[i] || i == SEG_LEN.length - 1) {
                float x0 = PATH[i * 2], y0 = PATH[i * 2 + 1], x1 = PATH[i * 2 + 2], y1 = PATH[i * 2 + 3];
                float f = SEG_LEN[i] > 0 ? Math.min(1, d / SEG_LEN[i]) : 1;
                p.x = x0 + (x1 - x0) * f;
                p.y = y0 + (y1 - y0) * f;
                p.dx = Math.signum(x1 - x0);
                p.dy = Math.signum(y1 - y0);
                return;
            }
            d -= SEG_LEN[i];
        }
    }

    @Override
    void pause() {
        // Nothing is owed mid-wave: the round is replayed from its snapshot.
        slipT = 0;
    }

    // =========================================================================================
    // touch
    // =========================================================================================

    @Override
    void down(float x, float y) {
        if (resultPending != RUNNING || round < 0) return;
        if (y >= trayY && y <= trayY + trayH) {
            int c = (int) ((x - 3) / (cardW + 6));
            if (c >= 0 && c < TYPES && x >= 3) {
                if (selected != c) h.sfx(Sfx.TAP);
                selected = c;
                tip = coins >= COST[c][0] ? h.t("עכשיו לחץ על עיגול זוהר", "עכשיו לחצי על עיגול זוהר")
                        : "חסרים " + (COST[c][0] - coins) + " מטבעות";
                tipT = 2;
            }
            return;
        }
        if (y < bTop - 12 || y > bBot + 8) return;
        int spot = -1;
        float ds = 1e9f;
        for (int i = 0; i < SPOTS; i++) {
            float d = (float) Math.hypot(x - sx(SPOT_X[i]), y - syv(SPOT_Y[i]));
            if (d < ds) {
                ds = d;
                spot = i;
            }
        }
        float dp = 1e9f;
        for (Pest p : pests) if (!p.dead) dp = Math.min(dp, (float) Math.hypot(x - sx(p.x), y - syv(p.y)));
        // Tapping right on a bug means the slipper, even beside a build spot.
        if (wave && dp < 22 && dp < ds) {
            slipper(x, y);
        } else if (ds <= 30) {
            tapSpot(spot);
        } else if (wave) {
            slipper(x, y);
        }
    }

    private void tapSpot(int s) {
        Tower t = towers[s];
        float x = sx(SPOT_X[s]), y = syv(SPOT_Y[s]);
        if (t.type < 0) {
            int cost = COST[selected][0];
            if (coins < cost) {
                h.text(x, y - 24, "חסרים " + (cost - coins), Ui.BAD);
                h.sfx(Sfx.TICK);
                return;
            }
            coins -= cost;
            t.type = selected;
            t.level = 0;
            t.cool = 0.25f;
            t.built = 0.35f;
            t.catX = 0;
            rangeSpot = s;
            rangeT = 1.2f;
            h.text(x, y - 26, T_NAME[selected], Ui.INK);
            h.burst(x, y, Ui.GOOD, 8);
            h.sfx(Sfx.DING);
            tipT = Math.min(tipT, 0.01f);
        } else if (t.level < 2) {
            int cost = COST[t.type][t.level + 1];
            rangeSpot = s;
            rangeT = 1.2f;
            if (coins < cost) {
                h.text(x, y - 24, "שדרוג: חסרים " + (cost - coins), Ui.BAD);
                h.sfx(Sfx.TICK);
                return;
            }
            coins -= cost;
            t.level++;
            t.built = 0.35f;
            h.text(x, y - 26, "שדרוג!", Ui.ACCENT);
            h.burst(x, y, Ui.ACCENT, 10);
            h.sfx(Sfx.LEVEL);
        } else {
            rangeSpot = s;
            rangeT = 1.2f;
            h.text(x, y - 24, "בשיא", Ui.DIM);
        }
    }

    private void slipper(float x, float y) {
        if (slipCd > 0) {
            h.sfx(Sfx.TICK);
            return;
        }
        slipCd = SLIP_CD;
        slipT = 0.3f;
        slipX = x;
        slipY = y;
        int hit = 0;
        for (Pest p : pests) {
            if (p.dead) continue;
            if (Math.hypot(x - sx(p.x), y - syv(p.y)) <= SLIP_R) {
                hit++;
                hurt(p, SLIP_DMG, true);
            }
        }
        if (hit > 0) {
            h.text(x, y - 30, hit > 1 ? "דאבל!" : "טראח!", Ui.INK);
            h.shake(3);
            h.sfx(Sfx.PUNCH);
        } else {
            h.sfx(Sfx.WHOOSH);
        }
        sweepDead();
    }

    // =========================================================================================
    // layout
    // =========================================================================================

    void layout(float top, float bottom) {
        this.top = top;
        this.bottom = bottom;
        float hh = bottom - top;
        trayH = hh < 380 ? 58 : 70;
        trayY = bottom - trayH - 2;
        float strip = hh < 380 ? 20 : 26;
        float avail = trayY - 6 - (top + strip);
        sy = Math.min(1.2f, avail / VH);
        bBot = trayY - 6;
        bTop = bBot - VH * sy;
        k = Ui.clamp(0.55f + sy * 0.45f, 0.82f, 1.05f);
        cardW = (W - 6 - 3 * 6) / 4f;
    }

    float sx(float vx) {
        return vx;
    }

    float syv(float vy) {
        return bTop + vy * sy;
    }

    /** Screen centre of a build spot, for bots and simulations. */
    float spotSX(int s) {
        return sx(SPOT_X[s]);
    }

    float spotSY(int s) {
        return syv(SPOT_Y[s]);
    }

    float cardCX(int t) {
        return 3 + t * (cardW + 6) + cardW / 2;
    }

    float cardCY() {
        return trayY + trayH / 2;
    }

    // =========================================================================================
    // drawing
    // =========================================================================================

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawRoom(c, top);
        drawTrail(c);
        drawFood(c);
        for (int s = 0; s < SPOTS; s++) drawSpot(c, s);
        if (rangeT > 0 && rangeSpot >= 0 && towers[rangeSpot].type >= 0) {
            Tower t = towers[rangeSpot];
            ring(c, SPOT_X[rangeSpot], SPOT_Y[rangeSpot], RANGE[t.type][t.level] + (t.type == CAT ? CAT_PATROL * 0.5f : 0),
                    (int) (Math.min(1, rangeT) * 0x99) << 24 | 0xFFFFFF);
        }
        for (Pest p : pests) drawPest(c, p);
        for (int s = 0; s < SPOTS; s++) if (towers[s].type == CAT) drawCat(c, s);
        for (int s = 0; s < SPOTS; s++) drawShot(c, s);
        for (int i = 0; i < PUFFS; i++) {
            if (pfT[i] <= 0) continue;
            float f = pfT[i] / 0.4f;
            int a = (int) (((pfC[i] >>> 24) & 0xFF) * f);
            c.fillCircle(sx(pfX[i]), syv(pfY[i]) - (1 - f) * 6, pfR[i] * (1.3f - f * 0.5f) * k, a << 24 | (pfC[i] & 0xFFFFFF));
        }
        if (slipT > 0) drawSlipper(c);
        drawStrip(c, top);
        drawTray(c);
        if (!wave && resultPending == RUNNING) drawCountdown(c);
    }

    private void drawRoom(Gfx c, float top) {
        wallTop = top + (bottom - top < 380 ? 20 : 26);
        // Kitchen wall above the floor, more of it on tall screens.
        c.fillRect(0, wallTop, W, bTop - wallTop + 2, 0xFF1D2A35);
        for (float x = 0; x < W; x += 30) c.line(x, wallTop, x, bTop, 1, 0xFF22313D);
        float wallH = bTop - wallTop;
        if (wallH > 30) {
            // A night window with the moon and a shelf of jars.
            float wy = wallTop + 6, wh = Math.min(wallH - 14, 56);
            c.fillRoundRect(150, wy, 64, wh, 4, 0xFF0B1730);
            c.fillCircle(198, wy + wh * 0.35f, 7, 0xFFF3E6B0);
            c.fillCircle(201, wy + wh * 0.32f, 6, 0xFF0B1730);
            c.line(182, wy, 182, wy + wh, 2, 0xFF33414E);
            c.line(150, wy + wh / 2, 214, wy + wh / 2, 2, 0xFF33414E);
            c.strokeRoundRect(150, wy, 64, wh, 4, 3, 0xFF3C4A57);
            float sh = wy + wh * 0.7f;
            c.fillRect(244, sh, 90, 4, 0xFF5A4636);
            c.fillRoundRect(250, sh - 18, 14, 18, 3, 0xFFB5553B);
            c.fillRoundRect(270, sh - 14, 12, 14, 3, 0xFF6E9E5B);
            c.fillRoundRect(288, sh - 20, 16, 20, 3, 0xFFD9C27A);
            c.fillRoundRect(310, sh - 12, 14, 12, 3, 0xFF8C6BB5);
            c.fillRect(30, sh, 80, 4, 0xFF5A4636);
            c.fillCircle(52, sh - 8, 8, 0xFF9AA5B1);
            c.fillRect(46, sh - 22, 12, 8, 0xFF9AA5B1);
            c.fillRoundRect(76, sh - 16, 22, 16, 4, 0xFFC9C2B0);
        }
        // Floor: dark checker tiles, night-lit.
        c.fillRect(0, bTop, W, bBot - bTop + 6, 0xFF1A2129);
        float tile = 30, th = tile * sy;
        int rows = (int) Math.ceil(VH / tile) + 1;
        for (int r = 0; r < rows; r++) {
            float y = bTop + r * th;
            if (y > bBot + 6) break;
            float hgt = Math.min(th, bBot + 6 - y);
            for (int col = (r & 1); col * tile < W; col += 2) c.fillRect(col * tile, y, tile, hgt, 0xFF1F2731);
        }
        c.fillRect(0, bTop - 4, W, 5, 0xFF3A2E26);
    }

    private float wallTop;

    private void drawTrail(Gfx c) {
        float w = 22 * Math.max(0.8f, Math.min(1.1f, sy));
        for (int i = 0; i + 3 < PATH.length; i += 2) {
            float x0 = sx(PATH[i]), y0 = syv(Math.max(0, PATH[i + 1]));
            float x1 = sx(PATH[i + 2]), y1 = syv(Math.max(0, PATH[i + 3]));
            c.line(x0, y0, x1, y1, w + 6, 0xFF151A20);
            c.line(x0, y0, x1, y1, w, 0xFF2C2620);
            c.fillCircle(x1, y1, w / 2 + 3, 0xFF151A20);
            c.fillCircle(x1, y1, w / 2, 0xFF2C2620);
        }
        // Crumbs scattered along the trail: what the pests follow.
        for (int i = 0; i < 46; i++) {
            float d = (i + 0.5f) * PATH_LEN / 46;
            float[] p = at(d);
            float o = ((i * 37) % 11 - 5) * 0.9f;
            c.fillCircle(sx(p[0]) + (p[3] != 0 ? o : 0), syv(p[1]) + (p[2] != 0 ? o : 0), 1.8f + (i % 3) * 0.5f,
                    0xFFC79A5B);
        }
        // The mouse hole in the baseboard where they come from, with eyes blinking inside.
        // On short screens there is no wall above the floor, so the arch is squatter.
        float hx = sx(PATH[0]), hb = bTop + 4, a = bTop - wallTop > 20 ? 1 : 0.72f;
        c.fillPoly(new float[]{hx - 19, hb, hx - 19, hb - 12 * a, hx - 14, hb - 20 * a, hx - 6, hb - 25 * a, hx + 6,
                hb - 25 * a, hx + 14, hb - 20 * a, hx + 19, hb - 12 * a, hx + 19, hb}, 0xFF3A2E26);
        c.fillPoly(new float[]{hx - 15, hb, hx - 15, hb - 11 * a, hx - 11, hb - 17 * a, hx - 5, hb - 21 * a, hx + 5,
                hb - 21 * a, hx + 11, hb - 17 * a, hx + 15, hb - 11 * a, hx + 15, hb}, 0xFF050608);
        c.line(hx + 15, hb - 14 * a, hx + 26, hb - 24 * a, 1.5f, 0xFF050608);
        c.line(hx + 26, hb - 24 * a, hx + 31, hb - 22 * a, 1.5f, 0xFF050608);
        float blink = (float) Math.sin(time * 1.7f);
        if (blink > -0.2f && blink < 0.9f) {
            float ex = hx + (float) Math.sin(time * 0.6f) * 5;
            c.fillCircle(ex - 4, hb - 10 * a, 2, 0xFFFFE070);
            c.fillCircle(ex + 4, hb - 10 * a, 2, 0xFFFFE070);
        }
    }

    private final float[] tmp4 = new float[4];

    private float[] at(float d) {
        Pest q = probe;
        q.s = d;
        place(q);
        tmp4[0] = q.x;
        tmp4[1] = q.y;
        tmp4[2] = q.dx;
        tmp4[3] = q.dy;
        return tmp4;
    }

    private final Pest probe = new Pest();

    private void drawFood(Gfx c) {
        float cy = syv(L3), cx = 36;
        float shake = foodHit > 0 ? (float) Math.sin(time * 60) * 3 * foodHit : 0;
        // The counter, the plate and the cake.
        c.fillRoundRect(2, cy - 38, 66, 70, 6, 0xFF6B4B33);
        c.fillRect(2, cy - 38, 66, 8, 0xFF8A6446);
        c.fillCircle(cx + shake, cy - 6, 27, 0xFFDCE3EA);
        c.fillCircle(cx + shake, cy - 6, 22, 0xFFB9C3CE);
        float frac = Math.max(0, food) / (float) FOOD;
        if (frac > 0) {
            float r = 19;
            c.fillCircle(cx + shake, cy - 6, r, 0xFF8A4B2A);
            c.fillCircle(cx + shake, cy - 8, r - 3, 0xFFF29BB7);
            c.fillCircle(cx + shake - 6, cy - 12, 3, 0xFFFFFFFF);
            c.fillCircle(cx + shake + 7, cy - 4, 3, 0xFFFFFFFF);
            c.fillCircle(cx + shake + 1, cy - 14, 4, 0xFFE0283C);
            // Bites taken out of the rim, one per point of damage.
            int missing = FOOD - Math.max(0, food);
            for (int i = 0; i < missing; i++) {
                double a = -0.4 + i * 0.63;
                float bx = cx + shake + (float) Math.cos(a) * r, by = cy - 6 + (float) Math.sin(a) * r;
                c.fillCircle(bx, by, 7.5f, 0xFFB9C3CE);
            }
        } else {
            for (int i = 0; i < 7; i++) c.fillCircle(cx - 12 + i * 4, cy - 2 + (i % 2) * 4, 2.4f, 0xFF8A4B2A);
        }
        // Freshness pips under the plate.
        for (int i = 0; i < FOOD; i++) {
            c.fillRoundRect(7 + i * 5.8f, cy + 22, 4.4f, 6, 1.5f,
                    i < food ? (food <= 4 ? Ui.BAD : food < FOOD ? Ui.ORANGE : Ui.GOOD) : 0xFF3A2C22);
        }
        if (foodHit > 0) c.fillCircle(cx, cy - 6, 30, ((int) (foodHit * 160)) << 24 | 0xFF3030);
    }

    private void drawSpot(Gfx c, int s) {
        Tower t = towers[s];
        float x = sx(SPOT_X[s]), y = syv(SPOT_Y[s]);
        float r = 24;
        if (t.type < 0) {
            boolean can = coins >= COST[selected][0];
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 5 + s);
            c.fillCircle(x, y, r, 0xFF262E38);
            if (can) {
                int a = (int) (60 + 90 * pulse);
                c.fillCircle(x, y, r + 3 + pulse * 3, a << 24 | (Ui.ACCENT & 0xFFFFFF));
                c.fillCircle(x, y, r, 0xFF3A3424);
                c.fillRect(x - 8, y - 1.5f, 16, 3, Ui.ACCENT);
                c.fillRect(x - 1.5f, y - 8, 3, 16, Ui.ACCENT);
            } else {
                c.strokeRoundRect(x - r, y - r, r * 2, r * 2, r, 2, 0xFF3A4350);
                c.fillRect(x - 6, y - 1, 12, 2, 0xFF3A4350);
                c.fillRect(x - 1, y - 6, 2, 12, 0xFF3A4350);
            }
            return;
        }
        float pop = t.built > 0 ? 1 + t.built * 0.8f : 1;
        float kk = k * pop;
        c.fillCircle(x, y + 3, r * 0.9f, 0x55000000);
        switch (t.type) {
            case STICKY: drawSticky(c, x, y, kk, t); break;
            case SPRAY: drawSpray(c, x, y, kk, t); break;
            case ZAP: drawZap(c, x, y, kk, t); break;
            default: drawCushion(c, x, y, kk); break;
        }
        // Level pips and an upgrade hint when the player can afford it.
        for (int i = 0; i <= t.level; i++) c.fillCircle(x - 7 + i * 7, y + 19 * k, 2.6f, Ui.ACCENT);
        if (t.level < 2 && coins >= COST[t.type][t.level + 1]) {
            float b = (float) Math.sin(time * 6 + s) * 2;
            float ax = x + 17, ay = y - 18 + b;
            c.fillCircle(ax, ay, 8, 0xFF1F7A45);
            c.fillPoly(new float[]{ax, ay - 5, ax + 5, ay + 1, ax + 2, ay + 1, ax + 2, ay + 5, ax - 2, ay + 5, ax - 2,
                    ay + 1, ax - 5, ay + 1}, Ui.INK);
        }
    }

    private void drawSticky(Gfx c, float x, float y, float s, Tower t) {
        c.fillRoundRect(x - 17 * s, y - 14 * s, 34 * s, 28 * s, 5 * s, 0xFFE8C547);
        c.fillRoundRect(x - 14 * s, y - 11 * s, 28 * s, 22 * s, 4 * s, 0xFFF6DE6A);
        float w = (float) Math.sin(time * 2 + x) * 1.5f;
        c.fillCircle(x - 6 * s, y - 2 * s + w, 4 * s, 0xFFD4A62A);
        c.fillCircle(x + 6 * s, y + 3 * s - w, 3 * s, 0xFFD4A62A);
        c.fillCircle(x + 2 * s, y - 6 * s, 2.5f * s, 0xFFD4A62A);
        if (t.level > 0) c.strokeRoundRect(x - 17 * s, y - 14 * s, 34 * s, 28 * s, 5 * s, 2, 0xFFFFF2B0);
        // Glue strands to everything caught in range: the trap's job, visible.
        if (t == demoTower) return;
        for (Pest p : pests) {
            if (p.dead || p.stuck == 0) continue;
            float px = sx(p.x), py = syv(p.y);
            if (dist2(p.x, p.y, x, (y - bTop) / sy) > sq(RANGE[STICKY][t.level])) continue;
            c.line(x, y, px, py, 1.2f, 0x66F6DE6A);
        }
    }

    private void drawSpray(Gfx c, float x, float y, float s, Tower t) {
        c.fillRoundRect(x - 10 * s, y - 14 * s, 20 * s, 28 * s, 4 * s, 0xFF2F7FD0);
        c.fillRect(x - 10 * s, y - 4 * s, 20 * s, 7 * s, 0xFFEAF3FB);
        c.fillCircle(x - 4 * s, y - 0.5f * s, 2.5f * s, 0xFF5BD68A);
        c.fillRoundRect(x - 6 * s, y - 20 * s, 12 * s, 7 * s, 2 * s, 0xFFB8C2CC);
        float ax = (float) Math.cos(t.aim), ay = (float) Math.sin(t.aim);
        float nx = x, ny = y - 20 * s;
        c.line(nx, ny, nx + ax * 12 * s, ny + ay * 12 * s, 4 * s, 0xFFE0453A);
        if (t.level >= 1) c.fillRect(x - 10 * s, y + 8 * s, 20 * s, 3 * s, 0xFF1D5A9C);
    }

    private void drawZap(Gfx c, float x, float y, float s, Tower t) {
        float glow = 0.5f + 0.5f * (float) Math.sin(time * 4 + x);
        float hy = y - 10 * s;
        c.fillCircle(x, hy, (20 + glow * 4 + t.level * 3) * s, ((int) (40 + glow * 40)) << 24 | 0x8A7CFF);
        c.line(x, y + 12 * s, x, hy, 4 * s, 0xFF4A5462);
        c.fillRoundRect(x - 13 * s, hy - 11 * s, 26 * s, 20 * s, 5 * s, 0xFF2A2F55);
        c.fillRoundRect(x - 10 * s, hy - 8 * s, 20 * s, 14 * s, 3 * s, t.fxT > 0 ? 0xFFFFFFFF : 0xFFB9A8FF);
        for (int i = -1; i <= 1; i++) c.line(x + i * 6 * s, hy - 8 * s, x + i * 6 * s, hy + 6 * s, 1.5f, 0xFF2A2F55);
        c.fillRoundRect(x - 9 * s, y + 10 * s, 18 * s, 5 * s, 2 * s, 0xFF4A5462);
    }

    private void drawCushion(Gfx c, float x, float y, float s) {
        c.fillRoundRect(x - 18 * s, y - 7 * s, 36 * s, 16 * s, 8 * s, 0xFFB24A5A);
        c.fillRoundRect(x - 14 * s, y - 5 * s, 28 * s, 10 * s, 5 * s, 0xFFD9667A);
    }

    private void drawShot(Gfx c, int s) {
        Tower t = towers[s];
        if (t.type < 0 || t.fxT <= 0) return;
        float x = sx(SPOT_X[s]), y = syv(SPOT_Y[s]);
        float tx = sx(t.fxX), ty = syv(t.fxY);
        if (t.type == ZAP) {
            // A zigzag bolt from the lamp to the bug.
            float hy = y - 10 * k;
            float px = x, py = hy;
            for (int i = 1; i <= 5; i++) {
                float f = i / 5f;
                float jx = i == 5 ? 0 : (float) Math.sin(time * 90 + i * 2.3f) * 7;
                float jy = i == 5 ? 0 : (float) Math.cos(time * 77 + i * 1.7f) * 7;
                float qx = x + (tx - x) * f + jx, qy = hy + (ty - hy) * f + jy;
                c.line(px, py, qx, qy, 4, 0x998A7CFF);
                c.line(px, py, qx, qy, 1.8f, 0xFFF4F0FF);
                px = qx;
                py = qy;
            }
            c.fillCircle(tx, ty, 9, 0x88B9A8FF);
        } else if (t.type == SPRAY) {
            float ny = y - 20 * k;
            float f = t.fxT / 0.2f;
            c.line(x, ny, tx, ty, 6 * f + 1, ((int) (f * 120)) << 24 | 0xB8F0D0);
        }
    }

    private void drawCat(Gfx c, int s) {
        Tower t = towers[s];
        float x = sx(SPOT_X[s] + t.catX), y = syv(SPOT_Y[s]) - 6 * k;
        float kk = k * (1 + t.level * 0.08f);
        boolean asleep = !wave;
        int fur = t.level == 2 ? 0xFFF0A040 : 0xFFE88A3A, dark = 0xFFB8612A;
        if (asleep && Math.abs(t.catX) < 2) {
            // Curled up on the cushion between waves, purring.
            c.fillCircle(x, y + 4 * kk, 14 * kk, fur);
            c.fillCircle(x + 9 * kk, y + 1 * kk, 7.5f * kk, fur);
            c.fillPoly(new float[]{x + 5 * kk, y - 4 * kk, x + 8 * kk, y - 11 * kk, x + 11 * kk, y - 5 * kk}, fur);
            c.fillPoly(new float[]{x + 11 * kk, y - 5 * kk, x + 15 * kk, y - 10 * kk, x + 16 * kk, y - 2 * kk}, fur);
            c.line(x + 6 * kk, y + 1 * kk, x + 9 * kk, y + 1 * kk, 1.5f, dark);
            c.line(x + 11 * kk, y + 1 * kk, x + 14 * kk, y + 1 * kk, 1.5f, dark);
            c.line(x - 12 * kk, y + 10 * kk, x + 4 * kk, y + 15 * kk, 4 * kk, dark);
            float z = (time * 0.8f + s * 0.3f) % 1f;
            c.text("ררר", x - 10, y - 14 - z * 10, 12, ((int) ((1 - z) * 200)) << 24 | 0xE9EDF2, Gfx.CENTER);
            return;
        }
        float dir = t.fxT > 0 ? Math.signum(t.fxX - SPOT_X[s] - t.catX) : t.catDir;
        if (dir == 0) dir = 1;
        float step = (float) Math.sin(t.catAnim * 14);
        // Legs, body, tail.
        for (int i = 0; i < 4; i++) {
            float lx = x + (i < 2 ? -8 : 8) * kk + (i % 2 == 0 ? step : -step) * 3 * kk;
            c.line(lx, y + 4 * kk, lx, y + 12 * kk, 3.2f * kk, dark);
        }
        c.fillRoundRect(x - 13 * kk, y - 4 * kk, 26 * kk, 13 * kk, 6 * kk, fur);
        for (int i = -1; i <= 1; i++) c.line(x + i * 6 * kk, y - 3 * kk, x + i * 6 * kk - 2, y + 4 * kk, 2, dark);
        float tw = (float) Math.sin(time * 3 + s) * 6;
        c.line(x - dir * 12 * kk, y, x - dir * 20 * kk, y - 12 * kk + tw, 3.5f * kk, fur);
        // Head with ears, eyes and whiskers.
        float hx = x + dir * 14 * kk, hy = y - 8 * kk;
        c.fillCircle(hx, hy, 8.5f * kk, fur);
        c.fillPoly(new float[]{hx - 7 * kk, hy - 4 * kk, hx - 5 * kk, hy - 14 * kk, hx - 1 * kk, hy - 7 * kk}, fur);
        c.fillPoly(new float[]{hx + 7 * kk, hy - 4 * kk, hx + 5 * kk, hy - 14 * kk, hx + 1 * kk, hy - 7 * kk}, fur);
        boolean angry = t.fxT > 0;
        c.fillCircle(hx - 3 * kk + dir, hy - 1 * kk, 2.2f * kk, angry ? 0xFFFFE070 : 0xFF9BE07A);
        c.fillCircle(hx + 3 * kk + dir, hy - 1 * kk, 2.2f * kk, angry ? 0xFFFFE070 : 0xFF9BE07A);
        c.fillRect(hx - 3.6f * kk + dir * 1.5f, hy - 2.5f * kk, 1.4f, 3 * kk, 0xFF10141A);
        c.fillRect(hx + 2.4f * kk + dir * 1.5f, hy - 2.5f * kk, 1.4f, 3 * kk, 0xFF10141A);
        c.fillCircle(hx + dir * 3 * kk, hy + 3 * kk, 1.4f, 0xFFE76F8A);
        c.line(hx + dir * 4 * kk, hy + 3 * kk, hx + dir * 12 * kk, hy + 1 * kk, 1, 0xCCFFFFFF);
        c.line(hx + dir * 4 * kk, hy + 4 * kk, hx + dir * 12 * kk, hy + 6 * kk, 1, 0xCCFFFFFF);
        if (t.fxT > 0) {
            // The swat: a big paw slammed onto the pest.
            float f = t.fxT / 0.2f;
            float px = sx(t.fxX), py = syv(t.fxY);
            c.line(hx, hy + 4 * kk, px, py - 3, 5 * kk, fur);
            c.fillCircle(px, py - 3, 7 * kk * (0.8f + f * 0.4f), fur);
            c.fillCircle(px - 3, py - 7, 2, 0xFFF5B6C4);
            c.fillCircle(px + 3, py - 7, 2, 0xFFF5B6C4);
        }
    }

    private void drawPest(Gfx c, Pest p) {
        if (p.y < -4) return;
        float x = sx(p.x), y = syv(p.y);
        float s = k * (p.type == BOSS ? 1.9f : p.type == MOUSE ? 1.15f : p.type == ANT ? 0.62f : 1f);
        float fx = p.dx, fy = p.dy;
        if (fx == 0 && fy == 0) fx = -1;
        // Screen-space heading is the virtual one; the board is only stretched in y.
        float gx = -fy, gy = fx;
        float walk = time * (p.type == ANT ? 30 : 22) + p.seed * 9;
        boolean hit = p.hitT > 0;
        if (hit) {
            float j = (float) Math.sin(time * 70) * 2;
            x += j;
        }
        switch (p.type) {
            case ANT: drawAnt(c, x, y, s, fx, fy, gx, gy, walk); break;
            case MOTH: drawMoth(c, x, y, s, walk); break;
            case MOUSE: drawMouse(c, x, y, s, fx, fy, gx, gy, walk); break;
            default: drawRoach(c, x, y, s, fx, fy, gx, gy, walk, p.type == BOSS); break;
        }
        if (p.stuck > 0) {
            c.fillCircle(x - 6 * s, y + 5 * s, 3 * s, 0xAAF6DE6A);
            c.fillCircle(x + 5 * s, y + 6 * s, 2.5f * s, 0xAAF6DE6A);
        }
        if (hit) c.fillCircle(x, y, 10 * s, 0x66FFFFFF);
        if (p.hp < p.maxHp && p.maxHp > 2) {
            float bw = p.type == BOSS ? 44 : 22;
            Ui.bar(c, x - bw / 2, y - 16 * s - 6, bw, 4, p.hp / p.maxHp, p.type == BOSS ? Ui.ORANGE : Ui.GOOD);
        }
    }

    /** Point in the pest's frame: a along the heading, b to its side. */
    private float px(float x, float fx, float gx, float a, float b) {
        return x + fx * a + gx * b;
    }

    private float py(float y, float fy, float gy, float a, float b) {
        return y + fy * a + gy * b;
    }

    private void drawRoach(Gfx c, float x, float y, float s, float fx, float fy, float gx, float gy, float walk,
                           boolean boss) {
        int body = boss ? 0xFF5E2F1E : 0xFF8B4A2B, shell = boss ? 0xFF7A3D25 : 0xFFA35A35;
        for (int i = -1; i <= 1; i++) {
            float sw = (float) Math.sin(walk + i * 2) * 3 * s;
            for (int side = -1; side <= 1; side += 2) {
                float ax = px(x, fx, gx, i * 4 * s, side * 4 * s), ay = py(y, fy, gy, i * 4 * s, side * 4 * s);
                c.line(ax, ay, px(x, fx, gx, i * 5 * s + sw * side, side * 11 * s),
                        py(y, fy, gy, i * 5 * s + sw * side, side * 11 * s), 1.6f * s, 0xFF3A1E12);
            }
        }
        float[] poly = new float[16];
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float la = (float) Math.cos(a) * 10 * s, lb = (float) Math.sin(a) * 6.5f * s;
            poly[i * 2] = px(x, fx, gx, la, lb);
            poly[i * 2 + 1] = py(y, fy, gy, la, lb);
        }
        c.fillPoly(poly, body);
        c.line(px(x, fx, gx, 7, 0), py(y, fy, gy, 7, 0), px(x, fx, gx, -9 * s, 0), py(y, fy, gy, -9 * s, 0), 1.2f,
                shell);
        float hx = px(x, fx, gx, 11 * s, 0), hy = py(y, fy, gy, 11 * s, 0);
        // Antennae with a swagger.
        float aw = (float) Math.sin(walk * 0.5f) * 3 * s;
        c.line(hx, hy, px(x, fx, gx, 22 * s, -7 * s + aw), py(y, fy, gy, 22 * s, -7 * s + aw), 1.2f, 0xFF3A1E12);
        c.line(hx, hy, px(x, fx, gx, 22 * s, 7 * s + aw), py(y, fy, gy, 22 * s, 7 * s + aw), 1.2f, 0xFF3A1E12);
        c.fillCircle(hx, hy, 5 * s, body);
        // Eyes with attitude: half-lidded and cross.
        float ex1 = px(x, fx, gx, 12 * s, -2.6f * s), ey1 = py(y, fy, gy, 12 * s, -2.6f * s);
        float ex2 = px(x, fx, gx, 12 * s, 2.6f * s), ey2 = py(y, fy, gy, 12 * s, 2.6f * s);
        if (boss) {
            c.line(ex1, ey1, ex2, ey2, 4.2f * s, 0xFF0A0A0A);
            c.fillCircle(ex1, ey1, 2.4f * s, 0xFF0A0A0A);
            c.fillCircle(ex2, ey2, 2.4f * s, 0xFF0A0A0A);
            // A tiny gold crown.
            float cx = px(x, fx, gx, 4 * s, 0), cy = py(y, fy, gy, 4 * s, 0) - 4 * s;
            c.fillPoly(new float[]{cx - 6 * s, cy, cx - 6 * s, cy - 7 * s, cx - 3 * s, cy - 3 * s, cx, cy - 8 * s,
                    cx + 3 * s, cy - 3 * s, cx + 6 * s, cy - 7 * s, cx + 6 * s, cy}, Ui.ACCENT);
        } else {
            c.fillCircle(ex1, ey1, 2.2f * s, 0xFFFFFFFF);
            c.fillCircle(ex2, ey2, 2.2f * s, 0xFFFFFFFF);
            c.fillCircle(ex1 + fx * 0.8f, ey1 + fy * 0.8f, 1.1f * s, 0xFF000000);
            c.fillCircle(ex2 + fx * 0.8f, ey2 + fy * 0.8f, 1.1f * s, 0xFF000000);
            c.line(px(x, fx, gx, 14 * s, -4.5f * s), py(y, fy, gy, 14 * s, -4.5f * s), px(x, fx, gx, 13 * s, -0.5f * s),
                    py(y, fy, gy, 13 * s, -0.5f * s), 1.3f, 0xFF3A1E12);
            c.line(px(x, fx, gx, 14 * s, 4.5f * s), py(y, fy, gy, 14 * s, 4.5f * s), px(x, fx, gx, 13 * s, 0.5f * s),
                    py(y, fy, gy, 13 * s, 0.5f * s), 1.3f, 0xFF3A1E12);
        }
    }

    private void drawAnt(Gfx c, float x, float y, float s, float fx, float fy, float gx, float gy, float walk) {
        int col = 0xFF2B1A1A;
        for (int i = -1; i <= 1; i++) {
            float sw = (float) Math.sin(walk + i) * 3 * s;
            c.line(px(x, fx, gx, i * 3 * s - sw, -9 * s), py(y, fy, gy, i * 3 * s - sw, -9 * s),
                    px(x, fx, gx, i * 3 * s + sw, 9 * s), py(y, fy, gy, i * 3 * s + sw, 9 * s), 1.3f, col);
        }
        c.fillCircle(px(x, fx, gx, -8 * s, 0), py(y, fy, gy, -8 * s, 0), 5.5f * s, 0xFF7A2A22);
        c.fillCircle(x, y, 3.5f * s, col);
        c.fillCircle(px(x, fx, gx, 7 * s, 0), py(y, fy, gy, 7 * s, 0), 4.5f * s, col);
        c.fillCircle(px(x, fx, gx, 8.5f * s, -2 * s), py(y, fy, gy, 8.5f * s, -2 * s), 1.4f * s, 0xFFFFFFFF);
        c.fillCircle(px(x, fx, gx, 8.5f * s, 2 * s), py(y, fy, gy, 8.5f * s, 2 * s), 1.4f * s, 0xFFFFFFFF);
    }

    private void drawMoth(Gfx c, float x, float y, float s, float walk) {
        float bob = (float) Math.sin(walk * 0.4f) * 4;
        // Shadow on the floor: it flies, so glue and the cat cannot reach it.
        c.fillCircle(x, y + 6, 6 * s, 0x44000000);
        y -= 12 + bob;
        float flap = 0.35f + 0.65f * Math.abs((float) Math.sin(walk * 0.9f));
        int wing = 0xFFE7DCC0, spot = 0xFFB59E7A;
        c.fillPoly(new float[]{x, y, x - 15 * s * flap, y - 9 * s, x - 13 * s * flap, y + 5 * s}, wing);
        c.fillPoly(new float[]{x, y, x + 15 * s * flap, y - 9 * s, x + 13 * s * flap, y + 5 * s}, wing);
        c.fillCircle(x - 9 * s * flap, y - 2 * s, 2.4f * s, spot);
        c.fillCircle(x + 9 * s * flap, y - 2 * s, 2.4f * s, spot);
        c.fillRoundRect(x - 2.5f * s, y - 7 * s, 5 * s, 15 * s, 2.5f * s, 0xFF8C7A5C);
        c.line(x, y - 7 * s, x - 5 * s, y - 13 * s, 1, 0xFF8C7A5C);
        c.line(x, y - 7 * s, x + 5 * s, y - 13 * s, 1, 0xFF8C7A5C);
        c.fillCircle(x - 1.5f * s, y - 5 * s, 1.2f * s, 0xFF000000);
        c.fillCircle(x + 1.5f * s, y - 5 * s, 1.2f * s, 0xFF000000);
    }

    private void drawMouse(Gfx c, float x, float y, float s, float fx, float fy, float gx, float gy, float walk) {
        float tw = (float) Math.sin(walk * 0.5f) * 5 * s;
        c.line(px(x, fx, gx, -10 * s, 0), py(y, fy, gy, -10 * s, 0), px(x, fx, gx, -22 * s, tw),
                py(y, fy, gy, -22 * s, tw), 2 * s, 0xFFE89AAE);
        float[] poly = new float[20];
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            float la = (float) Math.cos(a) * 12 * s, lb = (float) Math.sin(a) * 9.5f * s;
            if (Math.cos(a) > 0.5) la *= 1.15f;
            poly[i * 2] = px(x, fx, gx, la, lb);
            poly[i * 2 + 1] = py(y, fy, gy, la, lb);
        }
        c.fillPoly(poly, 0xFF9AA2AD);
        c.fillCircle(px(x, fx, gx, 4 * s, -8 * s), py(y, fy, gy, 4 * s, -8 * s), 5 * s, 0xFF9AA2AD);
        c.fillCircle(px(x, fx, gx, 4 * s, 8 * s), py(y, fy, gy, 4 * s, 8 * s), 5 * s, 0xFF9AA2AD);
        c.fillCircle(px(x, fx, gx, 4 * s, -8 * s), py(y, fy, gy, 4 * s, -8 * s), 3 * s, 0xFFE89AAE);
        c.fillCircle(px(x, fx, gx, 4 * s, 8 * s), py(y, fy, gy, 4 * s, 8 * s), 3 * s, 0xFFE89AAE);
        float nx = px(x, fx, gx, 14 * s, 0), ny = py(y, fy, gy, 14 * s, 0);
        c.fillCircle(nx, ny, 2.2f * s, 0xFFE76F8A);
        c.fillCircle(px(x, fx, gx, 8 * s, -3 * s), py(y, fy, gy, 8 * s, -3 * s), 1.8f * s, 0xFF10141A);
        c.fillCircle(px(x, fx, gx, 8 * s, 3 * s), py(y, fy, gy, 8 * s, 3 * s), 1.8f * s, 0xFF10141A);
        c.line(nx, ny, px(x, fx, gx, 16 * s, -8 * s), py(y, fy, gy, 16 * s, -8 * s), 1, 0xAAFFFFFF);
        c.line(nx, ny, px(x, fx, gx, 16 * s, 8 * s), py(y, fy, gy, 16 * s, 8 * s), 1, 0xAAFFFFFF);
    }

    private void drawSlipper(Gfx c) {
        float f = slipT / 0.3f;
        float sc = 1 + f * 0.7f;
        float x = slipX, y = slipY - f * 18;
        int a = (int) (255 * Math.min(1, f * 2.2f));
        c.fillCircle(slipX, slipY, SLIP_R * (1.2f - f * 0.5f), ((int) (f * 90)) << 24 | 0xFFFFFF);
        int sole = a << 24 | 0x3FA7D6, strap = a << 24 | 0xFFD23F;
        c.fillPoly(new float[]{x - 9 * sc, y - 20 * sc, x + 9 * sc, y - 20 * sc, x + 12 * sc, y + 4 * sc,
                x + 9 * sc, y + 20 * sc, x - 9 * sc, y + 20 * sc, x - 12 * sc, y + 4 * sc}, sole);
        c.line(x, y - 12 * sc, x - 10 * sc, y + 2 * sc, 4 * sc, strap);
        c.line(x, y - 12 * sc, x + 10 * sc, y + 2 * sc, 4 * sc, strap);
    }

    private void drawStrip(Gfx c, float top) {
        float y = top + (bottom - top < 380 ? 15 : 19);
        // Coins (right), what is going on (centre), the slipper (left).
        c.fillCircle(W - 16, y - 5, 8, Ui.ACCENT);
        c.fillCircle(W - 16, y - 5, 5, 0xFFE0A93A);
        c.text("" + coins, W - 30, y, 16, Ui.ACCENT, Gfx.RIGHT);
        String mid;
        int col = Ui.DIM;
        if (tipT > 0 && !tip.isEmpty()) {
            mid = tip;
            col = Ui.INK;
        } else if (wave) {
            int left = spawnN - spawned;
            for (Pest p : pests) if (!p.dead) left++;
            mid = "מזיקים: " + left;
        } else {
            mid = "גל " + (round + 1) + " מתוך 4";
        }
        c.text(mid, 150, y, 13, col, Gfx.CENTER);
        boolean ready = slipCd <= 0;
        float sx0 = 16, sy0 = y - 6;
        c.fillPoly(new float[]{sx0 - 5, sy0 - 9, sx0 + 5, sy0 - 9, sx0 + 6, sy0 + 2, sx0 + 4, sy0 + 9, sx0 - 4,
                sy0 + 9, sx0 - 6, sy0 + 2}, ready ? 0xFF3FA7D6 : 0xFF33414E);
        c.line(sx0, sy0 - 5, sx0 - 5, sy0 + 1, 2, ready ? 0xFFFFD23F : 0xFF4A5462);
        c.line(sx0, sy0 - 5, sx0 + 5, sy0 + 1, 2, ready ? 0xFFFFD23F : 0xFF4A5462);
        if (!ready) Ui.bar(c, 26, y - 9, 30, 5, 1 - slipCd / SLIP_CD, Ui.BLUE);
        else c.text("כפכף", 28, y, 12, wave ? 0xFF8FC9EA : Ui.DIM, Gfx.LEFT);
    }

    private void drawTray(Gfx c) {
        c.fillRoundRect(0, trayY - 2, W, trayH + 4, 8, 0xFF161C24);
        for (int i = 0; i < TYPES; i++) {
            float x = 3 + i * (cardW + 6), y = trayY + 2, hh = trayH - 4;
            boolean sel = selected == i, can = coins >= COST[i][0];
            c.fillRoundRect(x, y, cardW, hh, 8, sel ? 0xFF2E3A48 : Ui.PANEL);
            if (sel) c.strokeRoundRect(x, y, cardW, hh, 8, 2.5f, Ui.ACCENT);
            float ix = x + cardW / 2, iy = y + hh * 0.38f;
            float s = hh < 60 ? 0.62f : 0.75f;
            Tower demo = demoTower;
            demo.level = 0;
            demo.aim = -1.2f;
            demo.fxT = 0;
            switch (i) {
                case STICKY: drawSticky(c, ix, iy, s, demo); break;
                case SPRAY: drawSpray(c, ix, iy + 2, s, demo); break;
                case ZAP: drawZap(c, ix, iy + 4, s, demo); break;
                default: drawCatIcon(c, ix, iy + 2, s); break;
            }
            float ty = y + hh - 7;
            c.text(T_NAME[i], x + cardW - 8, ty, 13, sel ? Ui.INK : Ui.DIM, Gfx.RIGHT);
            c.fillCircle(x + 12, ty - 4.5f, 5.5f, can ? Ui.ACCENT : 0xFF5A4A2A);
            c.text("" + COST[i][0], x + 21, ty, 13, can ? Ui.ACCENT : Ui.BAD, Gfx.LEFT);
        }
    }

    private final Tower demoTower = new Tower();

    private void drawCatIcon(Gfx c, float x, float y, float s) {
        c.fillRoundRect(x - 13 * s, y - 2 * s, 24 * s, 12 * s, 6 * s, 0xFFE88A3A);
        c.fillCircle(x + 12 * s, y - 4 * s, 8 * s, 0xFFE88A3A);
        c.fillPoly(new float[]{x + 6 * s, y - 8 * s, x + 7 * s, y - 17 * s, x + 11 * s, y - 11 * s}, 0xFFE88A3A);
        c.fillPoly(new float[]{x + 18 * s, y - 8 * s, x + 17 * s, y - 17 * s, x + 13 * s, y - 11 * s}, 0xFFE88A3A);
        c.fillCircle(x + 9 * s, y - 5 * s, 1.8f * s, 0xFF9BE07A);
        c.fillCircle(x + 15 * s, y - 5 * s, 1.8f * s, 0xFF9BE07A);
        c.line(x - 12 * s, y + 2 * s, x - 19 * s, y - 10 * s, 3 * s, 0xFFE88A3A);
    }

    private void drawCountdown(Gfx c) {
        float cy = syv(L2);
        int n = (int) Math.ceil(Math.max(0, buildLeft));
        c.fillRoundRect(W / 2 - 74, cy - 22, 148, 40, 12, 0xCC10141A);
        c.text(round == 3 ? "גל אחרון" : "גל " + (round + 1), W / 2 + 26, cy + 5, 16, Ui.INK, Gfx.CENTER);
        float f = buildLeft - (float) Math.floor(buildLeft);
        c.text("" + n, W / 2 - 40, cy + 8, 22 + f * 6, Ui.ACCENT, Gfx.CENTER);
    }

    /** A range ring, drawn as dots on the stretched board. */
    private void ring(Gfx c, float vx, float vy, float r, int col) {
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI / 20;
            c.fillCircle(sx(vx + (float) Math.cos(a) * r), syv(vy + (float) Math.sin(a) * r), 1.8f, col);
        }
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        float old = time;
        time = t;
        c.fillRoundRect(cx - 120, cy + 26, 240, 16, 6, 0xFF2C2620);
        drawRoach(c, cx + 46, cy + 20, 2.6f, -1, 0, 0, -1, t * 9, false);
        drawAnt(c, cx + 98, cy + 30, 1.2f, -1, 0, 0, -1, t * 12);
        Tower demo = demoTower;
        demo.level = 0;
        demo.aim = -0.3f;
        demo.fxT = 0;
        drawSpray(c, cx - 60, cy + 14, 1.8f, demo);
        // The slipper, raised and ready.
        float sx0 = cx + 10, sy0 = cy - 44;
        c.fillPoly(new float[]{sx0 - 14, sy0 - 30, sx0 + 14, sy0 - 30, sx0 + 18, sy0 + 6, sx0 + 14, sy0 + 30, sx0 - 14,
                sy0 + 30, sx0 - 18, sy0 + 6}, 0xFF3FA7D6);
        c.line(sx0, sy0 - 18, sx0 - 15, sy0 + 3, 6, 0xFFFFD23F);
        c.line(sx0, sy0 - 18, sx0 + 15, sy0 + 3, 6, 0xFFFFD23F);
        time = old;
    }

    // =========================================================================================
    // bot: builds and swats through down / up, like a finger
    // =========================================================================================

    /** The expert's build order: {spot, type, level}. Sprays on the two corners, the lamp in the middle, the cat by the cake. */
    static final int[][] PLAN = {
            {0, SPRAY, 0}, {5, SPRAY, 0}, {4, ZAP, 0}, {0, SPRAY, 1}, {3, CAT, 0}, {5, SPRAY, 1},
            {1, STICKY, 0}, {4, ZAP, 1}, {2, SPRAY, 0}, {3, CAT, 1}, {0, SPRAY, 2}, {5, SPRAY, 2},
            {4, ZAP, 2}, {1, STICKY, 1}, {2, SPRAY, 1}, {3, CAT, 2}};

    private float botNext;
    private int botSpot = -1;

    @Override
    void bot(Random r, float skill) {
        if (resultPending != RUNNING) return;
        if (wave && slipCd <= 0 && r.nextFloat() < 0.02f + skill * skill * skill * 0.55f) botSwat(r, skill);
        if (time < botNext) return;
        // A clueless player reacts slowly and only starts building once the wave is already in.
        if (round == 0 && !wave && skill < 0.3f) return;
        botNext = time + (2.6f - skill * 2.35f) * (0.6f + 0.8f * r.nextFloat());
        if (botSpot >= 0) {
            // Second half of a build: the card is chosen, now the spot.
            int s = botSpot;
            botSpot = -1;
            tap(spotSX(s), spotSY(s));
            return;
        }
        if (r.nextFloat() < skill * skill) planStep();
        else if (r.nextFloat() < 0.25f + skill * 0.5f) randomStep(r);
    }

    private void planStep() {
        for (int[] st : PLAN) {
            Tower t = towers[st[0]];
            if (t.type >= 0 && t.type != st[1]) continue;
            if (t.type == st[1] && t.level >= st[2]) continue;
            if (t.type < 0) {
                if (coins < COST[st[1]][0]) return;
                if (selected != st[1]) {
                    tap(cardCX(st[1]), cardCY());
                    botSpot = st[0];
                } else {
                    tap(spotSX(st[0]), spotSY(st[0]));
                }
            } else {
                if (coins >= COST[t.type][t.level + 1]) tap(spotSX(st[0]), spotSY(st[0]));
            }
            return;
        }
    }

    private void randomStep(Random r) {
        int s = r.nextInt(SPOTS);
        if (towers[s].type < 0 && r.nextInt(3) == 0) {
            tap(cardCX(r.nextInt(TYPES)), cardCY());
            botSpot = s;
        } else {
            tap(spotSX(s), spotSY(s));
        }
    }

    private void botSwat(Random r, float skill) {
        Pest best = null;
        for (Pest p : pests) {
            if (p.dead) continue;
            // An expert smacks the bug closest to the cake; a beginner whatever catches the eye.
            if (best == null || (skill > 0.5f ? p.s > best.s : r.nextInt(3) == 0)) best = p;
        }
        if (best == null) return;
        float err = (1 - skill) * 60;
        // Aim where the bug will be a moment from now; beginners aim where it was.
        float lead = skill * 0.08f * P_SPEED[best.type];
        float ax = sx(best.x + best.dx * lead) + (r.nextFloat() * 2 - 1) * err;
        float ay = syv(best.y + best.dy * lead) + (r.nextFloat() * 2 - 1) * err;
        tap(ax, ay);
    }

    private void tap(float x, float y) {
        down(x, y);
        up(x, y);
    }

    private static float dist2(float x0, float y0, float x1, float y1) {
        float dx = x0 - x1, dy = y0 - y1;
        return dx * dx + dy * dy;
    }

    private static float sq(float v) {
        return v * v;
    }
}
