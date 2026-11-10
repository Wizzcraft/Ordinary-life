package com.wizzcraft.ima.core;

/**
 * The jobs in town: where they are, what they pay, when a shift can start and what they demand.
 * Pay, windows, tips and the stat a job needs and trains come from {@link Tuning}; this table adds
 * what the engine needs to host a job: the building, the Mini that plays the shift, the names.
 */
final class Job {
    /** Ids 6..9 (dishwasher, exterminator, pancake cook, pastry decorator) and 11 (lawyer) are reserved. */
    static final int COOK = 0, WAITER = 1, BAKER = 2, BARMAN = 3, GAS = 4, CASHIER = 5, DOCTOR = 10, DEV = 12;
    /** "No stat" in the requirement and training columns; otherwise Host.FIT / CHA / EDU. */
    static final int NONE = -1;
    static final String[] STAT_NAMES = {"כושר", "כריזמה", "השכלה"};

    /** A shift is always this long, split evenly over the rounds of the job's Mini. */
    static final int SHIFT_HOURS = (int) Tuning.SHIFT_HOURS;
    /**
     * Experience needed for rank 0, 1 and 2, and what each rank multiplies the pay by.
     * PREVIEW: the experience ladder is the short one (Tuning.RANK_XP is 0 / 100 / 350), so every
     * rank can be seen in a few shifts.
     */
    static final int[] RANK_XP = {0, 40, 120};
    static final float[] RANK_PAY = Tuning.RANK_PAY;
    /**
     * PREVIEW: a stat requirement is this share of the distance Tuning asks for above the starting
     * stat, so every job opens after a shift or two elsewhere instead of after weeks.
     */
    static final float PREVIEW_NEED = 0.2f;
    private static final String[] RANK_M = {"מתלמד", "מקצוען", "אחראי משמרת"};
    private static final String[] RANK_F = {"מתלמדת", "מקצוענית", "אחראית משמרת"};

    final int id, building, pay, from, to, needStat, needValue, trains;
    /** Service jobs get tips that grow with charisma. */
    final boolean service;
    /** A profession is offered only to somebody who finished its studies (variable "dip.<id>"). */
    final boolean profession;
    /** Pay for a MISS / OK / PERFECT round, as a share of the round's base pay. */
    final float[] resultPay;
    /** Id of the Mini that plays the shift (see Minis). */
    final String mini;
    private final String m, f;

    /** A job of Tuning's job arrays (index == id). */
    private Job(int id, String mini, String m, String f, int building) {
        this.id = id;
        this.mini = mini;
        this.m = m;
        this.f = f;
        this.building = building;
        pay = Tuning.JOB_PAY[id];
        // A shift cannot start before the doors open, whatever the rota says.
        City.Building b = City.B[building];
        int start = Tuning.JOB_FROM[id], end = Tuning.JOB_TO[id];
        if (start != end && b.openFrom != b.openTo && start < end && b.openFrom < b.openTo) {
            start = Math.max(start, b.openFrom);
        }
        from = start;
        to = end;
        needStat = Tuning.JOB_NEED_STAT[id];
        needValue = needStat == NONE ? 0 : light(Tuning.JOB_NEED_VALUE[id], needStat);
        trains = Tuning.JOB_TRAINS[id];
        service = Tuning.JOB_TIPS[id] != 0;
        profession = false;
        resultPay = Tuning.RESULT_MULT;
    }

    /** A profession: pro is its index in Tuning's PRO_ arrays. The diploma is the requirement. */
    private Job(int id, int pro, String mini, String m, String f, int building) {
        this.id = id;
        this.mini = mini;
        this.m = m;
        this.f = f;
        this.building = building;
        pay = Tuning.PRO_PAY[pro];
        from = Tuning.PRO_FROM;
        to = Tuning.PRO_TO;
        needStat = NONE;
        needValue = 0;
        trains = NONE;
        service = false;
        profession = true;
        resultPay = Tuning.PRO_RESULT_MULT;
    }

    private static int light(int tuned, int stat) {
        float start = stat == Host.FIT ? Game.START_FIT : stat == Host.CHA ? Game.START_CHA : Game.START_EDU;
        float tunedStart = stat == Host.FIT ? Tuning.START_FIT : stat == Host.CHA ? Tuning.START_CHA : Tuning.START_EDU;
        return (int) Math.ceil(start + Math.max(0, tuned - tunedStart) * PREVIEW_NEED);
    }

    /**
     * Columns: Mini id, name (m / f), building. The numbers are in Tuning.
     * INTEGRATORS: a new job is one more row here with its id, its numbers in Tuning, and its Mini
     * registered in Minis.
     */
    static final Job[] ALL = {
            new Job(COOK, "cook", "טבח", "טבחית", City.RESTAURANT),
            new Job(WAITER, "waiter", "מלצר", "מלצרית", City.RESTAURANT),
            new Job(BAKER, "baker", "אופה", "אופה", City.BAKERY),
            new Job(BARMAN, "barman", "ברמן", "ברמנית", City.BAR),
            new Job(GAS, "gas", "מתדלק", "מתדלקת", City.GAS),
            new Job(CASHIER, "cashier", "קופאי", "קופאית", City.MARKET),
            new Job(DOCTOR, Tuning.DOCTOR, "doctor", "רופא", "רופאה", City.OFFICE),
            new Job(DEV, Tuning.DEVELOPER, "dev", "מפתח תוכנה", "מפתחת תוכנה", City.OFFICE),
    };

    static Job byId(int id) {
        for (Job j : ALL) if (j.id == id) return j;
        return null;
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
