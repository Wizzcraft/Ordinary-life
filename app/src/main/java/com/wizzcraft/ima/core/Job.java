package com.wizzcraft.ima.core;

/** The jobs in town: where they are, what they pay, when a shift can start and what they demand. */
final class Job {
    static final int COOK = 0, WAITER = 1, BAKER = 2, BARMAN = 3, GAS = 4, CASHIER = 5;
    /** "No stat" in the requirement and training columns; otherwise Host.FIT / CHA / EDU. */
    static final int NONE = -1;
    static final String[] STAT_NAMES = {"כושר", "כריזמה", "השכלה"};

    /** A shift is always this long, split evenly over the rounds of the job's Mini. */
    static final int SHIFT_HOURS = 8;
    /** Experience needed for rank 0, 1 and 2, and what each rank multiplies the pay by. */
    static final int[] RANK_XP = {0, 40, 120};
    static final float[] RANK_PAY = {1f, 1.25f, 1.6f};
    private static final String[] RANK_M = {"מתלמד", "מקצוען", "אחראי משמרת"};
    private static final String[] RANK_F = {"מתלמדת", "מקצוענית", "אחראית משמרת"};

    final int id, building, pay, from, to, needStat, needValue, trains;
    /** Service jobs get tips that grow with charisma. */
    final boolean service;
    /** Id of the Mini that plays the shift (see Minis). */
    final String mini;
    private final String m, f;

    private Job(int id, String mini, String m, String f, int building, int pay, int from, int to, int needStat,
                int needValue, int trains, boolean service) {
        this.id = id;
        this.mini = mini;
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

    /**
     * Index == job id. Columns: Mini id, name (m / f), building, pay per hour at rank 0, the window
     * in which a shift may START (from == to means any time), required stat and its minimum, the
     * stat a shift trains, service job.
     * INTEGRATORS: a new job (a profession, say) is one more row here, with the next id.
     */
    static final Job[] ALL = {
            new Job(COOK, "cook", "טבח", "טבחית", City.RESTAURANT, 30, 8 * 60, 16 * 60, NONE, 0, NONE, false),
            new Job(WAITER, "waiter", "מלצר", "מלצרית", City.RESTAURANT, 28, 10 * 60, 16 * 60, Host.CHA, 10, Host.CHA, true),
            new Job(BAKER, "baker", "אופה", "אופה", City.BAKERY, 36, 5 * 60, 6 * 60, Host.FIT, 10, Host.FIT, false),
            new Job(BARMAN, "barman", "ברמן", "ברמנית", City.BAR, 40, 18 * 60, 19 * 60, Host.CHA, 25, Host.CHA, true),
            new Job(GAS, "gas", "מתדלק", "מתדלקת", City.GAS, 24, 0, 0, NONE, 0, Host.FIT, false),
            new Job(CASHIER, "cashier", "קופאי", "קופאית", City.MARKET, 26, 7 * 60, 14 * 60, NONE, 0, Host.CHA, false),
    };

    static Job byId(int id) {
        return id < 0 || id >= ALL.length ? null : ALL[id];
    }

    String name(boolean female) {
        return female ? f : m;
    }

    static String rankName(boolean female, int rank) {
        int r = Math.max(0, Math.min(RANK_M.length - 1, rank));
        return female ? RANK_F[r] : RANK_M[r];
    }

    boolean canStartAt(float minute) {
        if (from == to) return true;
        return from < to ? minute >= from && minute <= to : minute >= from || minute <= to;
    }

    /** "בכל שעה" or "בין 08:00 ל-16:00". */
    String window() {
        return from == to ? "בכל שעה" : "בין " + Ui.clock(from) + " ל-" + Ui.clock(to);
    }

    /** "₪30 לשעה" with the rank's raise, and a word about tips where there are any. */
    String payLine(int rank) {
        return Ui.nis(Math.round(pay * RANK_PAY[rank])) + " לשעה" + (service ? " ועוד טיפים" : "");
    }
}
