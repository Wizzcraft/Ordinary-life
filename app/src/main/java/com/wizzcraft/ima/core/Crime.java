package com.wizzcraft.ima.core;

/**
 * The risky side of town: street heat, the trouble that can wait outside the alley, arrests and
 * robberies. The alley's own trading is a venue Mini ("alley"); it raises the shared variable
 * "heat" (0..100), and everything that follows from heat happens here.
 */
final class Crime {
    // ---- every number of the crime skeleton lives here; the economy designer retunes them ----
    /** Heat lost every midnight. */
    static final float HEAT_DECAY_DAY = 10;
    /** Chance of trouble on the way out of the alley: EXIT_BASE + EXIT_K * (heat / 100)^2. */
    static final float EXIT_BASE = 0.04f, EXIT_K = 0.56f;
    /** How much of that trouble is the police (a chase); the rest is a mugger (a fight). */
    static final float CHASE_SHARE = 0.65f;
    /** How hard the event Mini arrives: heat / 100, plus this when the player is tired. */
    static final float DIFF_TIRED = 0.15f;
    /** Arrest: the fine for a first and for a repeated arrest, and what is left afterwards. */
    static final int ARREST_FINE = 400, ARREST_FINE_REPEAT = 800;
    static final float ARREST_HEAT_AFTER = 10, ARREST_ENERGY_AFTER = 45;
    /** The goods the police take: every variable with this prefix loses this share (whole units). */
    static final String GOODS_PREFIX = "alley.q";
    static final float ARREST_GOODS_LOST = 1f;
    /** Robbed after a lost fight: this share of the cash in the wallet is gone. */
    static final float ROBBED_SHARE = 0.2f;

    private Crime() {
    }

    /** Midnight: the street cools down a little. */
    static void newDay(Game g) {
        float heat = g.var("heat");
        if (heat > 0) g.setVar("heat", Math.max(0, heat - HEAT_DECAY_DAY));
    }

    static float trouble(float heat) {
        float k = Ui.clamp(heat, 0, 100) / 100f;
        return EXIT_BASE + EXIT_K * k * k;
    }

    /** The player walks out of the alley: trouble may be waiting, more likely the hotter the street. */
    static void leaveAlley(Game g) {
        boolean chase = Minis.has("chase"), fight = Minis.has("fight");
        Screen next = null;
        if ((chase || fight) && g.rnd.nextFloat() < trouble(g.var("heat"))) {
            boolean police = chase && (!fight || g.rnd.nextFloat() < CHASE_SHARE);
            float difficulty = g.var("heat") / 100f + (g.energy < Game.TIRED ? DIFF_TIRED : 0);
            next = EventScreen.open(g, police ? "chase" : "fight", difficulty);
            if (next != null) g.sfx(police ? Sfx.SIREN : Sfx.WHOOSH);
        }
        g.screen = next != null ? next : new BuildingScreen(g, City.ALLEY);
    }

    private static int fine(Game g) {
        return g.var("arrests") > 0 ? ARREST_FINE_REPEAT : ARREST_FINE;
    }

    static String chaseText(Game g, int result) {
        if (result != Mini.MISS) return "ברחת. הלב עוד דופק, אבל אף אחד לא ראה לאן נעלמת.";
        return "תפסו אותך. לילה בתחנה, קנס של " + Ui.nis(fine(g)) + ", וכל מה שהיה עליך הוחרם. "
                + "מהיום יש לך רישום במשטרה.";
    }

    /** A lost chase is an arrest: fine, goods gone, a night in the cell and a record. */
    static void chaseDone(Game g, int result) {
        if (result != Mini.MISS) {
            g.screen = new MapScreen(g);
            return;
        }
        g.spend(fine(g));
        for (String key : g.varKeys(GOODS_PREFIX)) {
            g.setVar(key, (float) Math.floor(g.var(key) * (1 - ARREST_GOODS_LOST)));
        }
        g.setVar("record", 1);
        g.addVar("arrests", 1);
        g.setVar("heat", Math.min(g.var("heat"), ARREST_HEAT_AFTER));
        g.wakeAtHome(ARREST_ENERGY_AFTER);
    }

    private static int robbed(Game g) {
        return Math.round(Math.max(0, g.money) * ROBBED_SHARE);
    }

    static String fightText(Game g, int result) {
        if (result != Mini.MISS) return "הוא ויתר והלך. הכל נשאר אצלך.";
        int loss = robbed(g);
        return loss > 0 ? "הוא לקח " + Ui.nis(loss) + " מהכיס שלך ונעלם בין הפחים."
                : "הוא חיפש בכיסים שלך ולא מצא כלום. נראה שאפילו הוא ריחם.";
    }

    /** A lost fight is a robbery. */
    static void fightDone(Game g, int result) {
        if (result == Mini.MISS) g.spend(robbed(g));
        g.screen = new MapScreen(g);
    }
}
