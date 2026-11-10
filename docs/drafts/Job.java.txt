package com.wizzcraft.ima.core;

/** The jobs in town: where they are, what they pay, when a shift can start and what they demand. */
final class Job {
    static final int COOK = 0, WAITER = 1, BAKER = 2, BARMAN = 3, GAS = 4, CASHIER = 5, COUNT = 6;
    /** Character stats. */
    static final int NONE = -1, FIT = 0, CHA = 1, EDU = 2;
    static final String[] STAT_NAMES = {"כושר", "כריזמה", "השכלה"};

    static final int[] RANK_XP = {0, 40, 120};
    static final float[] RANK_PAY = {1f, 1.25f, 1.6f};
    private static final String[] RANK_M = {"מתלמד", "מקצוען", "אחראי משמרת"};
    private static final String[] RANK_F = {"מתלמדת", "מקצוענית", "אחראית משמרת"};

    final int id, building, pay, from, to, needStat, needValue, trains;
    /** Service jobs get tips that grow with charisma. */
    final boolean service;
    private final String m, f;

    private Job(int id, String m, String f, int building, int pay, int from, int to, int needStat, int needValue,
                int trains, boolean service) {
        this.id = id;
        this.m = m;
        this.f = f;
        this.building = building;
        this.pay = pay;
        this.from = from;
        this.to = to;
        this.needStat = needStat;
        this.needValue = needValue;
        this.trains = trains;
        this.service = service;
    }

    /** Index == job id. from/to is the window in which a shift may START; from == to means any time. */
    static final Job[] ALL = {
            new Job(COOK, "טבח", "טבחית", City.RESTAURANT, 30, 8 * 60, 16 * 60, NONE, 0, NONE, false),
            new Job(WAITER, "מלצר", "מלצרית", City.RESTAURANT, 28, 10 * 60, 16 * 60, CHA, 10, CHA, true),
            new Job(BAKER, "אופה", "אופה", City.BAKERY, 36, 5 * 60, 6 * 60, FIT, 10, FIT, false),
            new Job(BARMAN, "ברמן", "ברמנית", City.BAR, 40, 18 * 60, 19 * 60, CHA, 25, CHA, true),
            new Job(GAS, "מתדלק", "מתדלקת", City.GAS, 24, 0, 0, NONE, 0, FIT, false),
            new Job(CASHIER, "קופאי", "קופאית", City.MARKET, 26, 7 * 60, 14 * 60, NONE, 0, CHA, false),
    };

    String name(Game g) {
        return g.female ? f : m;
    }

    static String rankName(Game g, int rank) {
        return g.female ? RANK_F[rank] : RANK_M[rank];
    }

    boolean canStartAt(float minute) {
        return from == to || (minute >= from && minute <= to);
    }

    String window() {
        return from == to ? "בכל שעה" : "בין " + Ui.clock(from) + " ל-" + Ui.clock(to);
    }

    /** The minigame for a shift at this job. */
    static ShiftGame create(Game g, int id) {
        Job j = ALL[id];
        switch (id) {
            default:
                return new CookGame(g, j);
        }
    }
}
