package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * The alley: a venue where a smooth dealer trades four packages nobody names. Prices move once a
 * game day, deterministically from a per-save seed and the day number, so walking out and back in
 * the same night shows the same board. Every deal warms the shared street {@code heat}; the engine
 * rolls for trouble on the way out and cools the street down overnight.
 *
 * The economy is a set of pure static functions ({@link #price}, {@link #event}, {@link #hint},
 * {@link #dealHeat}, {@link #owed}, {@link #carefulMove}) so that AlleySim, the bot and the engine all
 * read the same rules.
 */
final class AlleyMini extends Mini {

    // =====================================================================================
    // ECONOMY CONSTANTS: every tunable number lives in this block.
    // =====================================================================================

    /** Number of goods. Index order is also the card order (right to left, top to bottom). */
    static final int GOODS = 4;
    /** Card names, and the definite form used inside the dealer's sentences. */
    static final String[] NAME = {"ברווזים", "דודה", "פינגווינים", "פסנתרים"};
    static final String[] THE = {"הברווזים", "הדודה", "הפינגווינים", "הפסנתרים"};
    /** One-line temperament, shown on a card while the player holds none of that good. */
    static final String[] TEMPER = {"זול ויציב", "עולה ויורדת בגלים", "משוגעים לגמרי", "יקרים ומנומנמים"};

    /** Usual price of each good; noise, tides, drift, spikes and crashes are fractions of it. */
    static final int[] MEAN = {14, 52, 110, 400};
    /** Hard floor and ceiling of each band, applied after spikes and crashes. */
    static final int[] LO = {4, 18, 30, 150};
    static final int[] HI = {30, 110, 280, 950};
    /** Day-to-day jitter, as a fraction of the mean (uniform +-). */
    static final float[] NOISE = {0.22f, 0.07f, 0.30f, 0.05f};
    /** Aunt (good 1): a tide. Amplitude and period range in days, the phase comes from the seed. */
    static final float TIDE_AMP = 0.42f;
    static final int TIDE_PERIOD_MIN = 6, TIDE_PERIOD_SPAN = 3;
    /** Pianos (good 3): slow drift through random knots every DRIFT_KNOT days. */
    static final float DRIFT_AMP = 0.28f;
    static final int DRIFT_KNOT = 3;

    /** Chance per day of a market event (spike or crash), which good it hits, and how hard. */
    static final float EVENT_CHANCE = 0.42f;
    static final float[] EVENT_WEIGHT = {1f, 0.8f, 2.4f, 1f};
    static final float SPIKE_SHARE = 0.55f;
    static final float[] SPIKE = {1.9f, 1.6f, 2.1f, 1.9f};
    static final float[] CRASH = {0.5f, 0.6f, 0.42f, 0.55f};
    /** Chance that tomorrow's event leaks tonight as a whisper from the dealer. */
    static final float HINT_CHANCE = 0.5f;

    /** Pocket: starting capacity in units, and the one-off coat upgrade the dealer sells. */
    static final int START_CAP = 10, COAT_CAP = 20, COAT_PRICE = 450;

    /**
     * Heat added by one deal: a base cost, per unit, per ₪100 of value, and for greed (per ₪100 of
     * profit on a sale). Heat is 0..100 and shared with the rest of the game.
     */
    static final float HEAT_DEAL = 3f, HEAT_UNIT = 1.5f, HEAT_VALUE = 3.5f, HEAT_GREED = 5f, HEAT_MAX = 100;
    /** Where the warning text changes (and the siren sounds when a deal crosses 50 or 75). */
    static final float WARN1 = 25, WARN2 = 50, WARN3 = 75;

    /** Loans: offered in steps up to a total, with compound daily interest. */
    static final int LOAN_STEP = 100, LOAN_MAX = 300;
    static final float LOAN_RATE = 0.05f;

    /**
     * ASSUMPTIONS about the engine (it owns these; AlleySim uses them to check the balance):
     * heat cools by HEAT_DECAY_DAY every day; leaving the alley triggers a chase or a scuffle with
     * chance EXIT_BASE + EXIT_K * (heat/100)^2, i.e. about 6% at heat 20, 18% at 50 and 40% at 80;
     * a chase drops CHASE_DROP of every carried good, a scuffle costs SCUFFLE_CASH of the wallet.
     */
    static final float HEAT_DECAY_DAY = 10, EXIT_BASE = 0.04f, EXIT_K = 0.56f, CHASE_DROP = 0.35f, SCUFFLE_CASH = 0.12f;

    /** The careful trader (bot at skill 1, and AlleySim's "careful"). */
    static final float CAREFUL_HEAT = 40, BUY_BELOW = 0.82f, SELL_GAIN = 1.3f, SELL_FAIR = 0.95f, SPIKE_SELL = 1.45f;

    // =====================================================================================

    // ---- pure economy ----

    /** Deterministic hash of (seed, a, b) into [0, 1). */
    static float h01(int seed, int a, int b) {
        long x = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL + 0x632BE59BD9B4E019L;
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 40) / (float) (1 << 24);
    }

    /** Tonight's event: -1, or good * 2 + (0 = spike, 1 = crash). */
    static int event(int seed, int day) {
        if (h01(seed, 900, day) >= EVENT_CHANCE) return -1;
        float total = 0;
        for (float w : EVENT_WEIGHT) total += w;
        float pick = h01(seed, 901, day) * total;
        int g = 0;
        while (g < GOODS - 1 && pick >= EVENT_WEIGHT[g]) pick -= EVENT_WEIGHT[g++];
        return g * 2 + (h01(seed, 902, day) < SPIKE_SHARE ? 0 : 1);
    }

    /** A whisper tonight about tomorrow's event (same encoding as {@link #event}), or -1. */
    static int hint(int seed, int day) {
        int e = event(seed, day + 1);
        return e >= 0 && h01(seed, 903, day) < HINT_CHANCE ? e : -1;
    }

    static int price(int seed, int g, int day) {
        float u = h01(seed, 100 + g, day) * 2 - 1;
        float f = 1 + NOISE[g] * u;
        if (g == 1) {
            int period = TIDE_PERIOD_MIN + (int) (h01(seed, 200, 0) * TIDE_PERIOD_SPAN);
            float phase = h01(seed, 201, 0) * period;
            f += TIDE_AMP * (float) Math.sin(2 * Math.PI * (day + phase) / period);
        } else if (g == 3) {
            int k = Math.floorDiv(day, DRIFT_KNOT);
            float t = (day - k * DRIFT_KNOT) / (float) DRIFT_KNOT;
            float a = h01(seed, 300, k) * 2 - 1, b = h01(seed, 300, k + 1) * 2 - 1;
            float s = (1 - (float) Math.cos(t * Math.PI)) / 2;
            f += DRIFT_AMP * (a + (b - a) * s);
        }
        int e = event(seed, day);
        if (e >= 0 && e / 2 == g) {
            float kick = 0.9f + 0.2f * h01(seed, 904, day);
            f *= (e % 2 == 0 ? SPIKE[g] : CRASH[g]) * kick;
        }
        return Math.max(LO[g], Math.min(HI[g], Math.round(MEAN[g] * f)));
    }

    /** Heat one deal adds. profit is the gain over the average buy price (sales only; may be negative). */
    static float dealHeat(int units, int value, int profit) {
        return HEAT_DEAL + HEAT_UNIT * units + HEAT_VALUE * value / 100f + HEAT_GREED * Math.max(0, profit) / 100f;
    }

    /** What a loan recorded as {@code debt} on {@code debtDay} has grown to by {@code today}. */
    static int owed(float debt, int debtDay, int today) {
        if (debt <= 0) return 0;
        return Math.round(debt * (float) Math.pow(1 + LOAN_RATE, Math.max(0, today - debtDay)));
    }

    /** Engine assumption (see the constants block): chance that leaving at this heat goes wrong. */
    static float exitEventChance(float heat) {
        float k = Ui.clamp(heat, 0, 100) / 100f;
        return EXIT_BASE + EXIT_K * k * k;
    }

    /**
     * The careful trader's next move, one deal at a time: {good, +units to buy / -units to sell},
     * or null when done for the night. Sells what has gained enough or spikes, then buys what is
     * cheap against its usual price, always keeping heat under CAREFUL_HEAT. It knows each good's
     * usual price (a player learns it after a few nights) and listens to the dealer's whispers.
     */
    static int[] carefulMove(int seed, int day, int[] q, float[] avg, int money, int cap, float heat, boolean lastNight) {
        int hint = hint(seed, day);
        for (int g = 0; g < GOODS; g++) {
            if (q[g] <= 0) continue;
            int p = price(seed, g, day);
            boolean crashTomorrow = hint == g * 2 + 1 && p >= avg[g];
            boolean sell = lastNight || crashTomorrow || p >= MEAN[g] * SPIKE_SELL
                    || (p >= avg[g] * SELL_GAIN && p >= MEAN[g] * SELL_FAIR);
            if (!sell) continue;
            int k = q[g];
            if (!lastNight) {
                while (k > 0 && heat + dealHeat(k, k * p, Math.round(k * (p - avg[g]))) > CAREFUL_HEAT) k--;
            }
            if (k > 0) return new int[]{g, -k};
        }
        if (lastNight) return null;
        int room = cap;
        for (int n : q) room -= n;
        int best = -1;
        float bestScore = 9;
        for (int g = 0; g < GOODS; g++) {
            int p = price(seed, g, day);
            float score = p / (float) MEAN[g];
            int e = event(seed, day);
            if (hint == g * 2 && e != g * 2) score -= 0.5f;
            if (score > BUY_BELOW || p > money) continue;
            if (score < bestScore) {
                bestScore = score;
                best = g;
            }
        }
        if (best < 0 || room <= 0) return null;
        int p = price(seed, best, day);
        int k = Math.min(room, money / p);
        while (k > 0 && heat + dealHeat(k, k * p, 0) > CAREFUL_HEAT) k--;
        return k > 0 ? new int[]{best, k} : null;
    }

    // ---- persistent state (alley.*) ----

    private int seed, day, cap;
    private final int[] q = new int[GOODS];
    private final float[] avg = new float[GOODS];
    private final int[] today = new int[GOODS], yesterday = new int[GOODS];
    private int ev, whisper;
    private static final int SPARK = 7;
    private final int[][] history = new int[GOODS][SPARK];

    // ---- visit state ----

    private float time, idle, barkAge, heatFlash, panelAnim;
    private String bark = "";
    private int sel, amount = 1, barkSeq;
    private boolean exit, begun, panel, hintTold;
    private float shownHeat;

    // ---- layout, recomputed every draw ----

    private boolean laid;
    private float top, bot, sceneH, meterY, heatY, cardY, cardH, ctrlY, ctrlH;
    private static final float M = 8, CARD_W = 168, GAP = 8;
    private static final float EXIT_X = 272, EXIT_W = 80, EXIT_H = 42;
    private static final float SELL_X = 8, SELL_W = 86, MINUS_X = 98, MINUS_W = 50, QTY_X = 152, QTY_W = 56,
            PLUS_X = 212, PLUS_W = 48, BUY_X = 264, BUY_W = 88;
    private static final float CHIP_X = 8, CHIP_W = 104;

    @Override
    int rounds() {
        return 0;
    }

    @Override
    String title() {
        return "הסמטה";
    }

    @Override
    String howTo() {
        return "קונים חבילות בזול ומוכרים ביוקר. המחירים זזים כל יום והסוחר מפזר שמועות. כל עסקה מחממת את הרחוב, אז לא להגזים.";
    }

    @Override
    void begin(int round, float difficulty) {
        exit = false;
        panel = false;
        if (h.var("alley.seed") <= 0) h.setVar("alley.seed", 1 + h.rnd().nextInt(1 << 20));
        seed = (int) h.var("alley.seed");
        day = Math.max(1, (int) h.var("day"));
        cap = h.var("alley.cap") > 0 ? (int) h.var("alley.cap") : START_CAP;
        for (int g = 0; g < GOODS; g++) {
            q[g] = Math.max(0, (int) h.var("alley.q" + g));
            avg[g] = h.var("alley.a" + g);
            today[g] = price(seed, g, day);
            yesterday[g] = price(seed, g, day - 1);
            for (int i = 0; i < SPARK; i++) history[g][i] = price(seed, g, day - SPARK + 1 + i);
        }
        ev = event(seed, day);
        whisper = hint(seed, day);
        shownHeat = heat();
        idle = 0;
        if (!begun) {
            begun = true;
            float visits = h.var("alley.visits");
            h.setVar("alley.visits", visits + 1);
            if (visits == 0) say(pick(FIRST));
            else if (h.money() < cheapest() && heldUnits() == 0) say(pick(BROKE));
            else if (heat() >= WARN3) say(pick(HOT));
            else if (owedNow() > 0 && ((int) visits) % 2 == 0) say(pick(DEBT));
            else say(pick(BACK));
            h.sfx(Sfx.DOOR);
        }
    }

    // ---- small helpers ----

    private float heat() {
        return Ui.clamp(h.var("heat"), 0, HEAT_MAX);
    }

    private int heldUnits() {
        int n = 0;
        for (int v : q) n += v;
        return n;
    }

    private int cheapest() {
        int m = Integer.MAX_VALUE;
        for (int p : today) m = Math.min(m, p);
        return m;
    }

    private int owedNow() {
        return owed(h.var("alley.debt"), (int) h.var("alley.debt.day"), day);
    }

    private int buyable() {
        int room = cap - heldUnits();
        return Math.max(0, Math.min(Math.min(amount, room), h.money() / Math.max(1, today[sel])));
    }

    private int sellable() {
        return Math.min(amount, q[sel]);
    }

    private void save(int g) {
        h.setVar("alley.q" + g, q[g]);
        h.setVar("alley.a" + g, q[g] > 0 ? avg[g] : 0);
    }

    private void addHeat(float d) {
        float before = heat(), after = Math.min(HEAT_MAX, before + d);
        h.setVar("heat", after);
        heatFlash = 1;
        h.text(W / 2, heatY - 4, "+" + Math.round(d) + " חום", Ui.ORANGE);
        if ((before < WARN2 && after >= WARN2) || (before < WARN3 && after >= WARN3)) {
            h.sfx(Sfx.SIREN);
            h.shake(3);
            say(pick(HOT));
        }
    }

    private void say(String s) {
        bark = s;
        barkAge = 0;
        barkSeq++;
    }

    /** Picks a line from a category without repeating the previous pick, deterministic per visit. */
    private String pick(String[][] lines) {
        int i = (int) (h01(seed + barkSeq * 31, (int) h.var("alley.visits"), lines.length) * lines.length);
        String s = line(lines[i % lines.length]);
        return s.equals(bark) ? line(lines[(i + 1) % lines.length]) : s;
    }

    private String line(String[] l) {
        return l.length == 1 ? l[0] : h.t(l[0], l[1]);
    }

    private String news(int code, boolean tomorrow) {
        int g = code / 2;
        boolean spike = code % 2 == 0;
        String[] t = tomorrow ? (spike ? HINT_UP : HINT_DOWN) : (spike ? RUMOR_UP : RUMOR_DOWN);
        String s = t[(int) (h01(seed, 950 + (tomorrow ? 1 : 0), day) * t.length)];
        return s.replace("{g}", THE[g]);
    }

    // ---- deals ----

    private void buy() {
        int k = buyable(), p = today[sel];
        if (k <= 0) {
            say(cap - heldUnits() <= 0 ? pick(FULL) : pick(POOR));
            h.sfx(Sfx.TICK);
            return;
        }
        if (!h.pay(-k * p)) return;
        avg[sel] = (avg[sel] * q[sel] + k * p) / (q[sel] + k);
        q[sel] += k;
        save(sel);
        float cx = cardX(sel) + CARD_W / 2, cy = cardTop(sel) + cardH / 2;
        h.text(cx, cy, "-" + Ui.nis(k * p), Ui.INK);
        h.burst(cx, cy, 0xFFB08A5A, 4 + k);
        h.sfx(Sfx.COIN);
        boolean big = k * p >= 300 || k >= 8;
        say(big ? pick(BIG_BUY) : pick(BUY));
        addHeat(dealHeat(k, k * p, 0));
        idle = 0;
    }

    private void sell() {
        int k = sellable(), p = today[sel];
        if (k <= 0) {
            say(pick(NOTHING));
            h.sfx(Sfx.TICK);
            return;
        }
        if (!h.pay(k * p)) return;
        int profit = Math.round(k * (p - avg[sel]));
        q[sel] -= k;
        save(sel);
        h.setVar("alley.earned", h.var("alley.earned") + profit);
        float cx = cardX(sel) + CARD_W / 2, cy = cardTop(sel) + cardH / 2;
        h.text(cx, cy, "+" + Ui.nis(k * p), profit >= 0 ? Ui.GOOD : Ui.BAD);
        if (profit == 0) {
            h.sfx(Sfx.COIN);
            say("יצאת באפס. לפחות נהנינו מהאווירה.");
        } else if (profit > 0) {
            h.burst(cx, cy, Ui.ACCENT, 6 + k);
            h.sfx(profit >= 150 ? Sfx.DING : Sfx.COIN);
            say(profit >= 250 ? pick(BIG_WIN) : pick(PROFIT));
        } else {
            h.shake(3);
            h.sfx(Sfx.BAD);
            say(pick(LOSS));
        }
        addHeat(dealHeat(k, k * p, profit));
        idle = 0;
    }

    private void loan() {
        int owe = owedNow();
        if (owe + LOAN_STEP > LOAN_MAX) {
            say("עד כאן. אני נדיב, לא טיפש.");
            h.sfx(Sfx.TICK);
            return;
        }
        h.pay(LOAN_STEP);
        h.setVar("alley.debt", owe + LOAN_STEP);
        h.setVar("alley.debt.day", day);
        h.text(W / 2, cardY + 20, "+" + Ui.nis(LOAN_STEP), Ui.ACCENT);
        h.sfx(Sfx.COIN);
        say(pick(LOAN));
    }

    private void repay() {
        int owe = owedNow(), pay = Math.min(owe, h.money());
        if (pay <= 0 || !h.pay(-pay)) {
            h.sfx(Sfx.TICK);
            return;
        }
        h.setVar("alley.debt", owe - pay);
        h.setVar("alley.debt.day", day);
        h.text(W / 2, cardY + 20, "-" + Ui.nis(pay), Ui.INK);
        h.sfx(Sfx.COIN);
        say(owe - pay == 0 ? pick(REPAID) : "חלק החזרת. החלק השני מחכה בסבלנות.");
    }

    private void coat() {
        if (cap >= COAT_CAP) return;
        if (!h.pay(-COAT_PRICE)) {
            say(pick(POOR));
            h.sfx(Sfx.TICK);
            return;
        }
        cap = COAT_CAP;
        h.setVar("alley.cap", cap);
        h.burst(W / 2, cardY + 30, Ui.ACCENT, 12);
        h.sfx(Sfx.LEVEL);
        say(pick(COAT));
    }

    @Override
    int update(float dt) {
        time += dt;
        idle += dt;
        barkAge += dt;
        heatFlash = Math.max(0, heatFlash - dt * 1.5f);
        panelAnim = Ui.clamp(panelAnim + (panel ? dt : -dt) * 6, 0, 1);
        shownHeat += (heat() - shownHeat) * Math.min(1, dt * 5);
        // The night's news comes once the greeting has been read; whispers return on idle.
        if (barkAge > 2.8f && ev >= 0 && barkSeq == 1) say(news(ev, false));
        else if (barkAge > 3.2f && whisper >= 0 && !hintTold && barkSeq <= 2) {
            hintTold = true;
            say(news(whisper, true));
        } else if (idle > 9) {
            idle = 0;
            say(whisper >= 0 && h01(seed, barkSeq, 7) < 0.5f ? news(whisper, true)
                    : heat() >= WARN2 ? pick(HOT) : h.money() < cheapest() ? pick(BROKE) : pick(IDLE));
        }
        return RUNNING;
    }

    // ---- input ----

    @Override
    void down(float x, float y) {
        if (!laid || exit) return;
        idle = 0;
        if (Ui.in(x, y, EXIT_X, top + 8, EXIT_W, EXIT_H)) {
            exit = true;
            h.sfx(Sfx.DOOR);
            return;
        }
        if (Ui.in(x, y, CHIP_X, meterY - 14, CHIP_W, 44)) {
            panel = !panel;
            h.sfx(Sfx.TAP);
            if (panel) say(owedNow() > 0 ? "אני זוכר הכל. גם מספרים." : pick(OFFER));
            return;
        }
        if (panel) {
            float pw = (W - 2 * M - GAP) / 2, ph = panelBtnH();
            float y0 = cardY + 30;
            if (Ui.in(x, y, W - M - pw, y0, pw, ph)) loan();
            else if (Ui.in(x, y, M, y0, pw, ph)) repay();
            else if (Ui.in(x, y, W - M - pw, y0 + ph + GAP, pw, ph)) coat();
            else if (Ui.in(x, y, M, y0 + ph + GAP, pw, ph)) {
                panel = false;
                h.sfx(Sfx.TAP);
            }
            return;
        }
        for (int g = 0; g < GOODS; g++) {
            if (Ui.in(x, y, cardX(g), cardTop(g), CARD_W, cardH)) {
                if (sel != g) h.sfx(Sfx.TAP);
                sel = g;
                return;
            }
        }
        if (Ui.in(x, y, BUY_X, ctrlY, BUY_W, ctrlH)) buy();
        else if (Ui.in(x, y, SELL_X, ctrlY, SELL_W, ctrlH)) sell();
        else if (Ui.in(x, y, PLUS_X, ctrlY, PLUS_W, ctrlH)) step(1);
        else if (Ui.in(x, y, MINUS_X, ctrlY, MINUS_W, ctrlH)) step(-1);
        else if (Ui.in(x, y, QTY_X, ctrlY, QTY_W, ctrlH)) {
            amount = amount >= cap ? 1 : cap;
            h.sfx(Sfx.TICK);
        }
    }

    private void step(int d) {
        int n = Math.max(1, Math.min(cap, amount + d));
        if (n == amount) {
            h.sfx(Sfx.TICK);
            return;
        }
        amount = n;
        h.sfx(Sfx.TAP);
    }

    @Override
    void pause() {
        panel = false;
    }

    @Override
    boolean wantsExit() {
        return exit;
    }

    // ---- layout ----

    private void layout(float t, float b) {
        top = t;
        bot = b;
        float hgt = b - t, extra = Math.max(0, hgt - 298);
        cardH = 56 + Math.min(14, extra * 0.07f);
        ctrlH = 52 + Math.min(10, extra * 0.05f);
        float meterH = 30, heatH = 24;
        sceneH = hgt - (meterH + heatH + 2 * cardH + 6 + ctrlH + 4 * 4);
        meterY = t + sceneH + 4;
        heatY = meterY + meterH + 4;
        cardY = heatY + heatH + 4;
        ctrlY = b - ctrlH - 2;
        laid = true;
    }

    private float cardX(int g) {
        return g % 2 == 0 ? W - M - CARD_W : M;
    }

    private float cardTop(int g) {
        return cardY + (g / 2) * (cardH + 6);
    }

    private float panelBtnH() {
        return Math.min(60, (ctrlY + ctrlH - cardY - 30 - GAP) / 2);
    }

    // ---- drawing ----

    @Override
    void draw(Gfx c, float t, float b) {
        layout(t, b);
        drawScene(c);
        drawMeters(c);
        if (panelAnim > 0.5f) drawPanel(c);
        else {
            for (int g = 0; g < GOODS; g++) drawCard(c, g);
            drawControls(c);
        }
    }

    /** True for a short dark blink of the lamp: a few irregular flickers every few seconds. */
    private boolean lampOff() {
        int slot = (int) (time * 14);
        return h01(seed, 77, slot / 40) < 0.45f && slot % 40 < 6 && h01(seed, 78, slot) < 0.6f;
    }

    private void drawScene(Gfx c) {
        float y0 = top, y1 = top + sceneH, ground = y1 - Math.min(18, sceneH * 0.16f);
        c.fillRect(0, y0, W, sceneH, 0xFF151920);
        // Bricks: mortar lines with a running bond.
        int row = 0;
        for (float y = y0 + 12; y < ground; y += 13, row++) {
            c.line(0, y, W, y, 1, 0xFF1D222B);
            for (float x = (row % 2) * 17; x < W; x += 34) c.line(x, y, x, Math.min(ground, y + 13), 1, 0xFF1D222B);
        }
        c.fillRect(0, ground, W, y1 - ground, 0xFF0E1116);
        boolean off = lampOff();
        float lx = 196, ly = y0 + Math.min(22, sceneH * 0.2f);
        if (!off) {
            float spread = 70 + sceneH * 0.5f;
            c.fillPoly(new float[]{lx - 8, ly + 4, lx + 8, ly + 4, lx + spread, y1, lx - spread, y1}, 0x22FFD58A);
            c.fillPoly(new float[]{lx - 4, ly + 4, lx + 4, ly + 4, lx + spread * 0.55f, y1, lx - spread * 0.55f, y1}, 0x18FFD58A);
            c.fillPoly(new float[]{lx - 40, y1 - 3, lx + 40, y1 - 3, lx + 30, y1, lx - 30, y1}, 0x30FFD58A);
        }
        c.line(lx + 30, ly - 10, lx, ly - 10, 3, 0xFF2E3540);
        c.line(lx, ly - 10, lx, ly - 2, 2, 0xFF2E3540);
        c.fillPoly(new float[]{lx - 10, ly - 2, lx + 10, ly - 2, lx + 6, ly + 5, lx - 6, ly + 5}, 0xFF39414D);
        c.fillCircle(lx, ly + 5, 3.5f, off ? 0xFF4A4436 : 0xFFFFE6A8);
        // Moths that never learn.
        if (!off) {
            for (int i = 0; i < 3; i++) {
                float a = time * (2.1f + i * 0.7f) + i * 2.1f, rr = 9 + i * 5;
                c.fillCircle(lx + (float) Math.cos(a) * rr, ly + 8 + (float) Math.sin(a * 1.3f) * rr * 0.6f, 1.3f,
                        0xCCE8DCC0);
            }
        }

        // Dumpster on the right, with a cat whose eyes blink now and then.
        float dh = Math.min(60, sceneH * 0.5f), dw = dh * 1.5f, dx = 262, dy = y1 - dh;
        c.fillPoly(new float[]{dx, dy + 6, dx + dw, dy + 6, dx + dw - 4, y1, dx + 4, y1}, 0xFF24382F);
        c.fillPoly(new float[]{dx - 3, dy, dx + dw + 3, dy + 2, dx + dw + 3, dy + 8, dx - 3, dy + 8}, 0xFF2E4A3D);
        c.line(dx + dw * 0.33f, dy + 12, dx + dw * 0.33f, y1 - 4, 1.5f, 0xFF1B2A23);
        c.line(dx + dw * 0.66f, dy + 12, dx + dw * 0.66f, y1 - 4, 1.5f, 0xFF1B2A23);
        if (h01(seed, 79, (int) (time * 3)) > 0.12f) {
            c.fillCircle(dx + dw * 0.22f, dy - 4, 1.8f, 0xFFE8D25A);
            c.fillCircle(dx + dw * 0.22f + 6, dy - 4, 1.8f, 0xFFE8D25A);
        }

        // Police lights sweep the wall as the street heats up.
        float heat = shownHeat;
        if (heat >= 40) {
            float k = (heat - 40) / 60f, s = (float) Math.sin(time * 5);
            int a = (int) (k * 70 * Math.abs(s));
            c.fillRect(0, y0, W, sceneH, (a << 24) | (s > 0 ? 0xFF3030 : 0x3060FF));
        }

        drawDealer(c, 58, y1 - 2, Math.min(sceneH * 0.92f, 150));
        drawBubble(c);

        // Exit.
        c.fillRoundRect(EXIT_X, y0 + 8, EXIT_W, EXIT_H, 10, 0xE62C3644);
        c.strokeRoundRect(EXIT_X, y0 + 8, EXIT_W, EXIT_H, 10, 1.5f, 0x55FFFFFF);
        c.text("יציאה", EXIT_X + EXIT_W / 2 + 8, y0 + 8 + EXIT_H / 2 + 5.5f, 15, Ui.INK, Gfx.CENTER);
        float ax = EXIT_X + 14, ay = y0 + 8 + EXIT_H / 2;
        c.fillPoly(new float[]{ax - 6, ay, ax + 2, ay - 6, ax + 2, ay + 6}, Ui.INK);
        c.line(ax, ay, ax + 9, ay, 2.5f, Ui.INK);
    }

    /** The dealer: a coat, a hat, two glints under the brim. Never a face. */
    private void drawDealer(Gfx c, float x, float feet, float hgt) {
        float s = hgt / 100f;
        float breathe = (float) Math.sin(time * 1.6f) * 0.8f * s;
        float nod = barkAge < 0.6f ? (float) Math.sin(barkAge * 20) * 1.5f * s : 0;
        int body = 0xFF07090C, rim = 0xFF262D38;
        float sh = feet - 66 * s + breathe;
        // Rim light on the lamp side, then the body on top of it.
        c.fillPoly(new float[]{x - 16 * s, sh, x + 18 * s, sh, x + 24 * s, feet, x - 20 * s, feet}, rim);
        c.fillPoly(new float[]{x - 17 * s, sh + 1, x + 16 * s, sh + 1, x + 21 * s, feet, x - 21 * s, feet}, body);
        c.line(x + 2 * s, sh + 6 * s, x - 2 * s, feet - 4 * s, 1.2f * s, 0xFF161B22);
        float hy = sh - 10 * s + nod;
        c.fillCircle(x, hy, 9.5f * s, body);
        c.fillPoly(new float[]{x - 9 * s, hy - 5 * s, x + 9 * s, hy - 5 * s, x + 7 * s, hy - 16 * s, x - 7 * s, hy - 16 * s}, body);
        c.fillPoly(new float[]{x - 17 * s, hy - 4 * s, x + 18 * s, hy - 5 * s, x + 16 * s, hy - 2 * s, x - 15 * s, hy - 1.5f * s}, body);
        c.line(x - 7 * s, hy - 6 * s, x + 7 * s, hy - 6 * s, 1.4f * s, 0xFF3A2E2A);
        boolean blink = h01(seed, 80, (int) (time * 2.5f)) < 0.1f;
        if (!blink && !lampOff()) {
            c.fillCircle(x - 3 * s, hy + 1 * s, 1.1f * s, 0xFFFFE6A8);
            c.fillCircle(x + 4 * s, hy + 1 * s, 1.1f * s, 0xFFFFE6A8);
        }
        // A toothpick that bobs when he talks.
        float tp = barkAge < 1.2f ? (float) Math.sin(barkAge * 16) * 1.5f * s : 0;
        c.line(x + 4 * s, hy + 6 * s, x + 12 * s, hy + 5 * s + tp, 1f * s, 0xFFB79B6A);
    }

    private void drawBubble(Gfx c) {
        if (bark.isEmpty()) return;
        boolean roomy = sceneH >= 120;
        float bx = roomy ? 100 : 96, bw = roomy ? W - M - bx : EXIT_X - 6 - bx;
        float by = roomy ? top + 60 : top + 6;
        float size = roomy ? 14 : 13;
        java.util.List<String> lines = wrapped(c, bw - 18, size);
        float lh = size * 1.3f;
        float bh = roomy ? Math.max(48, lines.size() * lh + 16) : sceneH - 12;
        if (!roomy && lines.size() > 2) {
            // A long line on a short screen: smaller type, tighter leading, the whole strip.
            size = 12;
            lines = wrapped(c, bw - 12, size);
            lh = lines.size() > 2 ? 13 : 14.5f;
            by = top + 3;
            bh = sceneH - 6;
        }
        float pop = Math.min(1, barkAge * 8);
        float grow = (1 - pop) * 4;
        c.fillRoundRect(bx - grow, by - grow, bw + 2 * grow, bh + 2 * grow, 12, 0xF2E9E2D0);
        float tipY = roomy ? by + bh - 10 : by + bh * 0.55f;
        c.fillPoly(new float[]{bx, tipY - 7, bx, tipY + 5, bx - 12, tipY + 8}, 0xF2E9E2D0);
        float ty = by + bh / 2 - (lines.size() - 1) * lh / 2 + size * 0.36f;
        for (String l : lines) {
            c.text(l, bx + bw / 2, ty, size, 0xFF1C1A16, Gfx.CENTER);
            ty += lh;
        }
    }

    private String wrapKey = "";
    private Gfx wrapGfx;
    private java.util.List<String> wrapLines;

    /** The current bark wrapped to a width, cached so a frame does not re-wrap the same sentence. */
    private java.util.List<String> wrapped(Gfx c, float w, float size) {
        String key = bark + '|' + w + '|' + size;
        if (!key.equals(wrapKey) || c != wrapGfx) {
            wrapKey = key;
            wrapGfx = c;
            wrapLines = Ui.wrap(c, bark, w, size);
        }
        return wrapLines;
    }

    private void drawMeters(Gfx c) {
        float y = meterY, hgt = 30, cy = y + hgt / 2;
        c.fillRoundRect(M, y - 2, W - 2 * M, hgt + 4, 8, 0xFF161B22);
        // Wallet (right).
        c.text("ארנק", W - M - 10, cy + 5, 12, Ui.DIM, Gfx.RIGHT);
        c.text(Ui.nis(h.money()), W - M - 46, cy + 7, 19, Ui.ACCENT, Gfx.RIGHT);
        // Pocket (middle): one cell per unit of capacity.
        int held = heldUnits();
        float px = 128, pw = 110, cw = pw / cap;
        c.text("כיס " + held + "/" + cap, px + pw, cy - 3, 12, held >= cap ? Ui.ORANGE : Ui.DIM, Gfx.RIGHT);
        for (int i = 0; i < cap; i++) {
            float x = px + pw - (i + 1) * cw;
            c.fillRoundRect(x + 1, cy + 3, cw - 2, 7, 2, i < held ? (held >= cap ? Ui.ORANGE : 0xFFC9B48A) : 0xFF2A313C);
        }
        // Dealer chip (left): loans and the coat.
        int owe = owedNow();
        int chipCol = panel ? Ui.ACCENT : owe > 0 ? 0xFF4A2226 : Ui.PANEL2;
        c.fillRoundRect(CHIP_X, y, CHIP_W, hgt, 9, chipCol);
        String chip = panel ? "חזרה לעסקים" : owe > 0 ? "חוב " + Ui.nis(owe) : "הלוואה?";
        c.text(chip, CHIP_X + CHIP_W / 2, cy + 5, 13, panel ? 0xFF1A1300 : owe > 0 ? 0xFFFFB3B3 : Ui.INK, Gfx.CENTER);

        // Heat: the meter that matters most, full width.
        float hy = heatY, heat = shownHeat;
        int col = heat < WARN1 ? Ui.GOOD : heat < WARN2 ? Ui.ACCENT : heat < WARN3 ? Ui.ORANGE : Ui.BAD;
        String warn = heat < WARN1 ? "הרחוב שקט" : heat < WARN2 ? "מתחילים לשים לב אליך"
                : heat < WARN3 ? "ניידת מסתובבת באזור" : "לוהט! עדיף לנוח";
        float pulse = heat >= WARN3 ? 0.5f + 0.5f * (float) Math.sin(time * 8) : heatFlash;
        // A little flame, taller as the street heats up.
        float fx = W - M - 7, fy = hy + 19, fh = 8 + heat * 0.08f + pulse * 2;
        c.fillPoly(new float[]{fx - 6, fy, fx + 6, fy, fx + 3, fy - fh * 0.6f, fx, fy - fh, fx - 3, fy - fh * 0.6f}, col);
        c.text("חום", W - M - 18, hy + 17, 15, col, Gfx.RIGHT);
        c.text(String.valueOf(Math.round(heat)), W - M - 66, hy + 18, 18, Ui.INK, Gfx.CENTER);
        float bx = M + 140, bw = W - M - 82 - bx;
        Ui.bar(c, bx, hy + 4, bw, 16, heat / HEAT_MAX, col);
        for (float mark : new float[]{WARN1, WARN2, WARN3}) {
            c.fillRect(bx + bw * mark / HEAT_MAX, hy + 4, 1.5f, 16, 0x88000000);
        }
        if (pulse > 0) c.strokeRoundRect(bx - 2, hy + 2, bw + 4, 20, 10, 2, ((int) (pulse * 220) << 24) | (col & 0xFFFFFF));
        c.text(warn, M, hy + 17, heat >= WARN2 ? 13 : 12, heat >= WARN2 ? col : Ui.DIM, Gfx.LEFT);
    }

    private void drawCard(Gfx c, int g) {
        float x = cardX(g), y = cardTop(g), w = CARD_W, hh = cardH;
        boolean on = g == sel;
        boolean spike = ev == g * 2, crash = ev == g * 2 + 1;
        c.fillRoundRect(x, y, w, hh, 10, on ? 0xFF2C3644 : Ui.PANEL);
        if (spike || crash) c.fillRoundRect(x, y, w, hh, 10, spike ? 0x22FF9F5A : 0x225AA9FF);
        c.strokeRoundRect(x, y, w, hh, 10, on ? 2.5f : 1, on ? Ui.ACCENT : 0x22FFFFFF);
        drawPackage(c, g, x + w - 15, y + 18, on);
        int p = today[g], yp = yesterday[g];
        // Price on the left (arrow first), name on the right; the price wins any fight for space.
        String ps = Ui.nis(p);
        float psz = 19;
        float nameRight = x + w - 28, nameW = c.textWidth(NAME[g], 14);
        while (psz > 15 && x + 24 + c.textWidth(ps, psz) + 6 > nameRight - nameW) psz -= 1;
        c.text(NAME[g], nameRight, y + 23, 14, Ui.INK, Gfx.RIGHT);
        c.text(ps, x + 24, y + 25, psz, Ui.ACCENT, Gfx.LEFT);
        // Trend arrow against yesterday.
        float ax = x + 13, ay = y + 18;
        if (p > yp * 1.03f) c.fillPoly(new float[]{ax, ay - 7, ax + 6, ay + 3, ax - 6, ay + 3}, Ui.GOOD);
        else if (p < yp * 0.97f) c.fillPoly(new float[]{ax, ay + 7, ax + 6, ay - 3, ax - 6, ay - 3}, Ui.BAD);
        else c.fillRect(ax - 5, ay - 1.5f, 10, 3, Ui.DIM);
        float ly = y + hh - 12;
        if (q[g] > 0) {
            int ac = Math.round(avg[g]);
            int col = p > ac ? Ui.GOOD : p < ac ? Ui.BAD : Ui.DIM;
            c.text("בכיס " + q[g], x + w - 10, ly, 13, Ui.INK, Gfx.RIGHT);
            c.text("שילמת " + Ui.nis(ac), x + 10, ly, 13, col, Gfx.LEFT);
        } else {
            c.text(TEMPER[g], x + w - 10, ly, 12, Ui.DIM, Gfx.RIGHT);
            spark(c, g, x + 10, ly - 10, 44, 12);
        }
        if (spike || crash) {
            String tag = spike ? "זינוק" : "צניחה";
            float tw = c.textWidth(tag, 12) + 14, tx = x + w / 2 - tw / 2 + 10;
            c.fillRoundRect(tx, y - 7, tw, 15, 7, spike ? Ui.ORANGE : Ui.BLUE);
            c.text(tag, tx + tw / 2, y + 4, 12, Ui.DARK_INK, Gfx.CENTER);
        }
    }

    /** The last seven nights of a good's price, so its temperament can be read at a glance. */
    private void spark(Gfx c, int g, float x, float y, float w, float hh) {
        int n = SPARK;
        int lo = Integer.MAX_VALUE, hi = 0;
        for (int i = 0; i < n; i++) {
            int v = history[g][i];
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        float span = Math.max(1, hi - lo);
        float px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            float sx = x + w * i / (n - 1), sy = y + hh - hh * (history[g][i] - lo) / span;
            if (i > 0) c.line(px, py, sx, sy, 1.5f, 0x99C9B48A);
            px = sx;
            py = sy;
        }
        c.fillCircle(px, py, 2.2f, Ui.ACCENT);
    }

    /** A wrapped package, a different shape and string for each good, so they read without names. */
    private void drawPackage(Gfx c, int g, float cx, float cy, boolean on) {
        float wob = on ? (float) Math.sin(time * 4 + g) * 1.2f : 0;
        int paper = new int[]{0xFFC9B48A, 0xFFB98F9A, 0xFF9FB5C9, 0xFF8E8A7A}[g];
        float s = g == 3 ? 9 : g == 2 ? 7.5f : g == 1 ? 7 : 6;
        cy += wob;
        if (g == 1) {
            c.fillCircle(cx, cy, s, paper);
            c.fillPoly(new float[]{cx - 3, cy - s, cx + 3, cy - s, cx + 5, cy - s - 4, cx - 5, cy - s - 4}, paper);
        } else {
            c.fillRoundRect(cx - s, cy - s * 0.8f, 2 * s, 1.6f * s, 2, paper);
        }
        c.line(cx, cy - s, cx, cy + s * 0.8f, 1.2f, 0xFF5A4630);
        c.line(cx - s, cy, cx + s, cy, 1.2f, 0xFF5A4630);
    }

    private void drawControls(Gfx c) {
        float y = ctrlY, hh = ctrlH;
        int kb = buyable(), ks = sellable(), p = today[sel];
        boolean canBuy = kb > 0, canSell = ks > 0;
        // Buy (right, primary).
        c.fillRoundRect(BUY_X, y, BUY_W, hh, 12, canBuy ? Ui.ACCENT : 0xFF1B2028);
        int bi = canBuy ? 0xFF1A1300 : 0xFF6B7482;
        c.text(h.t("קנה ", "קני ") + (canBuy ? kb : amount), BUY_X + BUY_W / 2, y + hh / 2 + 1, 17, bi, Gfx.CENTER);
        String bsub = canBuy ? Ui.nis(kb * p) : cap - heldUnits() <= 0 ? "הכיס מלא" : "אין מספיק";
        c.text(bsub, BUY_X + BUY_W / 2, y + hh / 2 + 17, 12, bi, Gfx.CENTER);
        // Sell (left).
        c.fillRoundRect(SELL_X, y, SELL_W, hh, 12, canSell ? Ui.GOOD : 0xFF1B2028);
        int si = canSell ? 0xFF0B2014 : 0xFF6B7482;
        c.text(h.t("מכור ", "מכרי ") + (canSell ? ks : amount), SELL_X + SELL_W / 2, y + hh / 2 + 1, 17, si, Gfx.CENTER);
        String ssub = canSell ? Ui.nis(ks * p) : "אין בכיס";
        c.text(ssub, SELL_X + SELL_W / 2, y + hh / 2 + 17, 12, si, Gfx.CENTER);
        // Stepper.
        c.fillRoundRect(MINUS_X, y, MINUS_W, hh, 12, Ui.PANEL2);
        c.fillRect(MINUS_X + MINUS_W / 2 - 9, y + hh / 2 - 2, 18, 4, Ui.INK);
        c.fillRoundRect(PLUS_X, y, PLUS_W, hh, 12, Ui.PANEL2);
        c.fillRect(PLUS_X + PLUS_W / 2 - 9, y + hh / 2 - 2, 18, 4, Ui.INK);
        c.fillRect(PLUS_X + PLUS_W / 2 - 2, y + hh / 2 - 9, 4, 18, Ui.INK);
        c.fillRoundRect(QTY_X, y, QTY_W, hh, 10, 0xFF161B22);
        c.text(String.valueOf(amount), QTY_X + QTY_W / 2, y + hh / 2 + 4, 24, Ui.INK, Gfx.CENTER);
        c.text(amount >= cap ? "הכל" : "כמות", QTY_X + QTY_W / 2, y + hh - 6, 11, Ui.DIM, Gfx.CENTER);
    }

    private void drawPanel(Gfx c) {
        float y = cardY, hh = ctrlY + ctrlH - cardY;
        c.fillRoundRect(M, y, W - 2 * M, hh, 12, 0xFF1A1F27);
        c.strokeRoundRect(M, y, W - 2 * M, hh, 12, 1.5f, 0x44FFC857);
        int owe = owedNow();
        String info = owe > 0 ? "חוב " + Ui.nis(owe) + " · ריבית " + Math.round(LOAN_RATE * 100) + "% כל יום"
                : "הלוואה עד " + Ui.nis(LOAN_MAX) + " · ריבית " + Math.round(LOAN_RATE * 100) + "% כל יום";
        c.text(info, W / 2, y + 21, 14, owe > 0 ? 0xFFFFB3B3 : Ui.INK, Gfx.CENTER);
        float pw = (W - 2 * M - GAP) / 2, ph = panelBtnH(), y0 = y + 30;
        boolean canLoan = owe + LOAN_STEP <= LOAN_MAX;
        new Btn(0, W - M - pw, y0, pw, ph, "לקחת " + Ui.nis(LOAN_STEP), canLoan, true).draw(c);
        new Btn(1, M, y0, pw, ph, owe > 0 ? "להחזיר " + Ui.nis(Math.min(owe, h.money())) : "אין חוב",
                owe > 0 && h.money() > 0, false).draw(c);
        new Btn(2, W - M - pw, y0 + ph + GAP, pw, ph, cap >= COAT_CAP ? "כבר יש לך מעיל" : "כיס כפול " + Ui.nis(COAT_PRICE),
                cap < COAT_CAP && h.money() >= COAT_PRICE, false).draw(c);
        new Btn(3, M, y0 + ph + GAP, pw, ph, "חזרה", true, false).draw(c);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        time = t;
        c.fillRoundRect(cx - 110, cy - 70, 220, 140, 14, 0xFF151920);
        c.fillPoly(new float[]{cx + 20, cy - 52, cx + 32, cy - 52, cx + 96, cy + 70, cx - 44, cy + 70}, 0x26FFD58A);
        c.fillCircle(cx + 26, cy - 52, 5, 0xFFFFE6A8);
        float saveH = sceneH, saveAge = barkAge;
        sceneH = 120;
        barkAge = 9;
        drawDealer(c, cx - 30, cy + 66, 120);
        sceneH = saveH;
        barkAge = saveAge;
        drawPackage(c, 0, cx + 40, cy + 54, false);
        drawPackage(c, 2, cx + 62, cy + 52, false);
        drawPackage(c, 3, cx + 52, cy + 34, false);
    }

    // ---- bot ----

    private int botWait;
    private int[] botMove;

    @Override
    void bot(Random r, float skill) {
        if (!laid || exit || --botWait > 0) return;
        botWait = 6 + r.nextInt(6);
        if (r.nextFloat() >= skill) {
            // Noise: a random tap on something tappable, now and then the exit.
            int pick = r.nextInt(20);
            if (pick == 0) tap(EXIT_X + 20, top + 20);
            else if (pick < 6) tap(cardX(pick % 4) + 40, cardTop(pick % 4) + 20);
            else if (pick < 9) tap(BUY_X + 20, ctrlY + 10);
            else if (pick < 12) tap(SELL_X + 20, ctrlY + 10);
            else if (pick < 15) tap(PLUS_X + 10, ctrlY + 10);
            else if (pick < 17) tap(MINUS_X + 10, ctrlY + 10);
            else if (pick < 18) tap(CHIP_X + 20, meterY + 10);
            else tap(r.nextFloat() * W, top + r.nextFloat() * (bot - top));
            return;
        }
        if (panel) {
            tap(CHIP_X + 20, meterY + 10);
            return;
        }
        if (botMove == null) {
            botMove = carefulMove(seed, day, q, avg, h.money(), cap, heat(), false);
            if (botMove == null) {
                tap(EXIT_X + 20, top + 20);
                return;
            }
        }
        int g = botMove[0], want = Math.abs(botMove[1]);
        if (sel != g) tap(cardX(g) + 40, cardTop(g) + 20);
        else if (amount < want) tap(PLUS_X + 10, ctrlY + 10);
        else if (amount > want) tap(MINUS_X + 10, ctrlY + 10);
        else {
            tap(botMove[1] > 0 ? BUY_X + 20 : SELL_X + 20, ctrlY + 10);
            botMove = null;
        }
    }

    private void tap(float x, float y) {
        down(x, y);
        up(x, y);
    }

    // ---- the dealer's lines: {masculine, feminine} or {neutral} ----

    private static final String[][] FIRST = {
            {"ערב טוב. אתה חדש פה, אז נדבר לאט.", "ערב טוב. את חדשה פה, אז נדבר לאט."},
            {"אל תשאל מה בחבילות. זה חלק מהקסם.", "אל תשאלי מה בחבילות. זה חלק מהקסם."},
            {"פנים חדשות. אני אוהב פנים חדשות, הן משלמות מחיר מלא."},
    };
    private static final String[][] BACK = {
            {"חזרת. ידעתי שתחזור.", "חזרת. ידעתי שתחזרי."},
            {"שוב אנחנו. הפנס, הפח ואני."},
            {"הלקוח האהוב עליי. אל תספר לאחרים.", "הלקוחה האהובה עליי. אל תספרי לאחרים."},
            {"המחירים של הערב, טריים מהתנור."},
            {"החתול ואני דיברנו עליך. רק דברים טובים."},
    };
    private static final String[][] BUY = {
            {"בחירה צנועה. אני מכבד צניעות."},
            {"עסקה סגורה. לא ראיתי אותך."},
            {"תחזיק את זה קרוב לגוף.", "תחזיקי את זה קרוב לגוף."},
            {"טעם טוב. או מזל. בדרך כלל זה אותו דבר."},
    };
    private static final String[][] BIG_BUY = {
            {"וואו. מישהו פה חושב בגדול."},
            {"רק אל תלך עם זה ישר הביתה.", "רק אל תלכי עם זה ישר הביתה."},
            {"בכמויות כאלה כבר מגיע לך כרטיסייה."},
            {"יש לך תוכניות, אני רואה. אני אוהב תוכניות."},
    };
    private static final String[][] PROFIT = {
            {"קנית בזול, מכרת ביוקר. אתה לומד.", "קנית בזול, מכרת ביוקר. את לומדת."},
            {"מסחר נקי. נו, כמעט נקי."},
            {"רווח יפה. אני כמעט מתגעגע לחבילות האלה."},
    };
    private static final String[][] BIG_WIN = {
            {"אוהו, מישהו עשה ערב. רק אל תתבלט.", "אוהו, מישהי עשתה ערב. רק אל תתבלטי."},
            {"רווח כזה עושה רעש. ורעש מביא אורחים."},
            {"תקנה לאמא משהו יפה. בלי לספר מאיפה.", "תקני לאמא משהו יפה. בלי לספר מאיפה."},
    };
    private static final String[][] LOSS = {
            {"מכרת בהפסד? אני לא שופט. טוב, קצת."},
            {"השוק נותן, השוק לוקח. בעיקר לוקח."},
            {"אל תדאג, כולם מתחילים ככה.", "אל תדאגי, כולם מתחילים ככה."},
    };
    private static final String[][] HOT = {
            {"יש פה ניידת שעוברת כבר בפעם השלישית."},
            {"אתה חם כמו מחבת. אולי תנוח כמה ימים?", "את חמה כמו מחבת. אולי תנוחי כמה ימים?"},
            {"אני מדבר בשקט, ואתה עושה רעש.", "אני מדבר בשקט, ואת עושה רעש."},
            {"מישהו בחלון למעלה רושם משהו. אולי מתכון. אולי לא."},
    };
    private static final String[][] BROKE = {
            {"ריק בכיס? יש לי פתרון. הוא עולה ריבית."},
            {"בלי כסף אין חבילות. ככה זה בעולם."},
            {"בוא נדבר על הלוואה קטנה. קטנטנה.", "בואי נדבר על הלוואה קטנה. קטנטנה."},
    };
    private static final String[][] OFFER = {
            {"צריך דחיפה? אני מלווה. בחיוך. בינתיים.", "צריכה דחיפה? אני מלווה. בחיוך. בינתיים."},
            {"אני לא בנק. בנק זה יותר מפחיד. קצת."},
    };
    private static final String[][] LOAN = {
            {"הנה. ריבית קטנה, כל יום. אני סומך עליך."},
            {"תזכור: אני זוכר.", "תזכרי: אני זוכר."},
    };
    private static final String[][] REPAID = {
            {"החזרת הכל. איזה כיף לעבוד עם אנשים ישרים."},
            {"נקי. עכשיו אנחנו סתם חברים."},
    };
    private static final String[][] DEBT = {
            {"החוב שלך גדל יפה. כמו עציץ."},
            {"לא שכחתי את הכסף. רק שתדע.", "לא שכחתי את הכסף. רק שתדעי."},
    };
    private static final String[][] FULL = {
            {"הכיסים שלך מתפקעים. מכור משהו קודם.", "הכיסים שלך מתפקעים. מכרי משהו קודם."},
            {"אין מקום. אולי מעיל עם כיסים? יש לי אחד."},
    };
    private static final String[][] POOR = {
            {"זה עולה יותר ממה שיש לך. בדקתי."},
            {"הארנק שלך אומר לא. אני שומע אותו מפה."},
    };
    private static final String[][] NOTHING = {
            {"אי אפשר למכור אוויר. ניסיתי."},
    };
    private static final String[][] COAT = {
            {"מעיל חדש. עכשיו אתה נראה כמו מישהו.", "מעיל חדש. עכשיו את נראית כמו מישהי."},
    };
    private static final String[][] IDLE = {
            {"תחליט, הפנס לא יהבהב לנצח.", "תחליטי, הפנס לא יהבהב לנצח."},
            {"קר פה. בוא נעשה עסקים.", "קר פה. בואי נעשה עסקים."},
            {"החתול מהפח מסתכל עליך. הוא יודע משהו."},
            {"אני פה כל לילה. החבילות, לא תמיד."},
    };
    private static final String[] RUMOR_UP = {
            "כל העיר מחפשת את {g} הערב. המחיר בהתאם.",
            "משלוח של {g} נתקע בנמל. מחירים בשמיים.",
            "מישהו בחתונה ביקש את {g}. עכשיו כולם רוצים.",
    };
    private static final String[] RUMOR_DOWN = {
            "מישהו נלחץ ושפך את {g}. זול הערב.",
            "יש עודף של {g} בשוק. כמעט בחינם.",
            "ספק חדש הגיע עם {g}. המחירים צנחו.",
    };
    private static final String[] HINT_UP = {
            "לחישה: מחר המחיר של {g} קופץ. לא שמעת ממני.",
            "יש לי הרגשה שמחר כולם ירצו את {g}.",
    };
    private static final String[] HINT_DOWN = {
            "לחישה: מחר המחיר של {g} נופל. אני רק אומר.",
            "אם יש לך את {g}, הערב זה זמן טוב להיפרד.",
    };
}
