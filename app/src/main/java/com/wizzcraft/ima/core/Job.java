package com.wizzcraft.ima.core;

/**
 * The jobs in town: where they are, what they pay, when a shift can start and what they demand.
 * Every number comes from {@link Tuning}; this table adds what the engine needs to host a job:
 * the building, the Mini that plays the shift, the names.
 */
final class Job {
    static final int COOK = 0, WAITER = 1, BAKER = 2, BARMAN = 3, GAS = 4, CASHIER = 5, DISH = 6, PEST = 7,
            PANCAKE = 8, CAKE = 9, DOCTOR = 10, LAWYER = 11, DEV = 12;
    /** "No stat" in the requirement and training columns; otherwise Host.FIT / CHA / EDU. */
    static final int NONE = -1;
    static final String[] STAT_NAMES = {"כושר", "כריזמה", "השכלה"};

    /** A shift is this long, split evenly over the rounds of the job's Mini; the day-one trial is half of it. */
    static final int SHIFT_HOURS = (int) Tuning.SHIFT_HOURS, TRIAL_HOURS = SHIFT_HOURS / 2;
    /** Experience needed for rank 0, 1 and 2, and what each rank multiplies the pay by. */
    static final int[] RANK_XP = Tuning.RANK_XP;
    static final float[] RANK_PAY = Tuning.RANK_PAY;
    private static final String[] RANK_M = {"מתלמד", "מקצוען", "אחראי משמרת"};
    private static final String[] RANK_F = {"מתלמדת", "מקצוענית", "אחראית משמרת"};

    final int id, building, pay, from, to, needStat, needValue, trains;
    /** Energy an hour on shift costs. */
    final float energy;
    /** Service jobs get tips that grow with charisma. */
    final boolean service;
    /** A profession is offered only to somebody who finished its studies (variable "dip.<id>"). */
    final boolean profession;
    /** Pay for a MISS / OK / PERFECT round, as a share of the round's base pay. */
    final float[] resultPay;
    /** Id of the Mini that plays the shift (see Minis). A job whose Mini is not registered is not offered. */
    final String mini;
    private final String m, f;

    /** A job of the ten in Tuning's job arrays. */
    private Job(int id, String mini, String m, String f, int building) {
        this.id = id;
        this.mini = mini;
        this.m = m;
        this.f = f;
        this.building = building;
        pay = Tuning.JOB_PAY[id];
        from = Tuning.JOB_FROM[id];
        to = Tuning.JOB_TO[id];
        energy = Tuning.JOB_ENERGY[id];
        needStat = Tuning.JOB_NEED_STAT[id];
        needValue = Tuning.JOB_NEED_VALUE[id];
        trains = Tuning.JOB_TRAINS[id];
        service = Tuning.JOB_TIPS[id] != 0;
        profession = false;
        resultPay = Tuning.RESULT_MULT;
    }

    /** A profession: pro is its index in Tuning's PRO_ arrays. */
    private Job(int id, int pro, String mini, String m, String f, int building) {
        this.id = id;
        this.mini = mini;
        this.m = m;
        this.f = f;
        this.building = building;
        pay = Tuning.PRO_PAY[pro];
        from = Tuning.PRO_FROM;
        to = Tuning.PRO_TO;
        energy = Tuning.ENERGY_RATE[Tuning.A_WORK];
        needStat = Tuning.PRO_NEED_STAT[pro];
        needValue = Tuning.PRO_NEED_VALUE[pro];
        trains = NONE;
        service = false;
        profession = true;
        resultPay = Tuning.PRO_RESULT_MULT;
    }

    /**
     * Index == job id. Columns: Mini id, name (m / f), building.
     * INTEGRATORS: a new job is one more row here with the next id, and its numbers in Tuning.
     */
    static final Job[] ALL = {
            new Job(COOK, "cook", "טבח", "טבחית", City.RESTAURANT),
            new Job(WAITER, "waiter", "מלצר", "מלצרית", City.RESTAURANT),
            new Job(BAKER, "baker", "אופה", "אופה", City.BAKERY),
            new Job(BARMAN, "barman", "ברמן", "ברמנית", City.BAR),
            new Job(GAS, "gas", "מתדלק", "מתדלקת", City.GAS),
            new Job(CASHIER, "cashier", "קופאי", "קופאית", City.MARKET),
            new Job(DISH, "dish", "שוטף כלים", "שוטפת כלים", City.RESTAURANT),
            new Job(PEST, "pest", "מדביר", "מדבירה", City.GAS),
            new Job(PANCAKE, "pancake", "טבח פנקייקים", "טבחית פנקייקים", City.RESTAURANT),
            new Job(CAKE, "cake", "קונדיטור", "קונדיטורית", City.BAKERY),
            new Job(DOCTOR, Tuning.DOCTOR, "doctor", "רופא", "רופאה", City.OFFICE),
            new Job(LAWYER, Tuning.LAWYER, "lawyer", "עורך דין", "עורכת דין", City.OFFICE),
            new Job(DEV, Tuning.DEVELOPER, "dev", "מפתח תוכנה", "מפתחת תוכנה", City.OFFICE),
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

    /** Energy a shift of the given length burns. */
    float energyCost(int hours) {
        return energy * hours;
    }
}
