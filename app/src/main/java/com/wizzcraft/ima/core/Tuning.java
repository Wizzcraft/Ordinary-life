package com.wizzcraft.ima.core;

/**
 * Every balance number for the year-long game, in one place. Constants only: the integrators wire
 * these into Game, the jobs, the venues and the school. docs/DESIGN.md gives the reason for each
 * one, and tools/balance/model.py reads THIS file directly, so the model and the game can never
 * disagree. Keep the syntax simple for that parser: one "static final" per statement, numeric
 * literals, simple arithmetic, flat or two-level array initialisers, no method calls.
 *
 * Money is in whole shekels, time in game minutes unless the name says HOURS, needs and stats 0..100.
 * A "month" is DAYS_PER_MONTH days; rent is charged at the start of day 11, 21, ...
 */
final class Tuning {
    private Tuning() {
    }

    // =============================== time ===============================
    static final float MIN_PER_SEC = 20f;
    static final int DAYS_PER_MONTH = 10;
    static final int MONTHS = 12;
    /** City units per game minute on foot. Unchanged: crossing town on foot stays 2-3 hours. */
    static final float WALK_SPEED = 3f;

    // =============================== needs ===============================
    // Activity indices for the rate tables below.
    static final int A_IDLE = 0, A_WALK = 1, A_WORK = 2, A_SLEEP = 3, A_RIDE = 4, A_STUDY = 5;
    /** Hunger per game hour by activity. Work no longer feeds you by the hour (that made food free). */
    static final float[] FOOD_RATE = {5f, 5f, 4f, 2f, 4f, 4f};
    /** Thirst per game hour. Walking is the thirstiest thing you do. */
    static final float[] WATER_RATE = {6f, 8f, 5f, 2f, 5f, 5f};
    /** Tiredness per game hour (sleep restores instead, see HOME_SLEEP). Work uses JOB_ENERGY. */
    static final float[] ENERGY_RATE = {3f, 6f, 6f, 0f, 2f, 5f};
    /** Awake with food or water at 0: tiredness runs this many times faster. */
    static final float STARVING_ENERGY_MULT = 3f;
    /** Each fitness point cuts awake tiredness by this fraction (fitness 100 = 25% less). */
    static final float FIT_ENERGY_SAVING = 0.0025f;
    /** One staff meal per shift, given at the start, replaces the old per-hour feeding. */
    static final float STAFF_MEAL_FOOD = 30f, STAFF_MEAL_WATER = 40f;
    static final float MEAL_FOOD = 45f, BOTTLE_WATER = 50f, TAP_WATER = 40f;
    static final float DINE_FOOD = 70f, DINE_WATER = 20f;
    /** Below this energy the player is tired: minigames arrive harder (the host adds difficulty). */
    static final float TIRED_ENERGY = 30f;
    static final float TIRED_DIFFICULTY = 0.3f;
    /** A shift needs this much energy to start (a full shift costs 32..56). */
    static final float MIN_SHIFT_ENERGY = 40f;
    static final int AMBULANCE = 250;
    static final float COLLAPSE_WAKE_ENERGY = 50f;
    /** No fourth need. Evenings out are an optional action that trains charisma (see OUT_*). */
    static final boolean MOOD_NEED = false;

    // =============================== stats ===============================
    static final int FIT = 0, CHA = 1, EDU = 2, NONE = -1;
    static final float START_FIT = 10f, START_CHA = 10f, START_EDU = 15f;
    /** Stat points a shift adds to the stat its job trains, scaled by (1 - stat/120) so 100 is hard. */
    static final float STAT_PER_SHIFT = 1.2f;
    /** Fitness per hour on foot or on the bicycle. */
    static final float FIT_PER_WALK_HOUR = 0.08f, FIT_PER_BIKE_HOUR = 0.2f;
    /** Evening out (bar or restaurant, 20:00 onwards): price, hours, charisma, and it feeds you. */
    static final int OUT_PRICE = 70;
    static final float OUT_HOURS = 3f, OUT_CHA = 2.5f, OUT_FOOD = 40f, OUT_WATER = 40f;
    /** Evening course at the school: the cheap, slow way to raise education before a profession. */
    static final int COURSE_PRICE = 50;
    static final float COURSE_HOURS = 2f, COURSE_EDU = 2.5f;
    static final int COURSE_FROM = 17 * 60, COURSE_TO = 20 * 60;
    /** Charisma: tips and alley prices. Fitness: energy (above), chase escapes. Education: job XP. */
    static final float EDU_XP_BONUS = 0.005f;
    static final float CHA_ALLEY_PRICE = 0.001f;
    static final float FIT_ESCAPE = 0.002f;

    // =============================== jobs ===============================
    // Job ids: 0 cook, 1 waiter, 2 baker, 3 barman, 4 gas attendant, 5 cashier, 6 dishwasher,
    // 7 exterminator, 8 pancake cook, 9 pastry decorator. Every shift is 8 game hours.
    // Buildings: 0,1,6,8 restaurant · 2,9 bakery · 3 bar · 4 gas station · 5 market · 7 night call
    // (the van leaves from the gas station; the job happens at the client's).
    static final float SHIFT_HOURS = 8f;
    /** Hourly pay at rank 0. Waiter and barman are low on paper because tips come on top. */
    static final int[] JOB_PAY = {19, 17, 21, 22, 16, 18, 15, 24, 20, 22};
    /** Window in which a shift may START (minutes of the day); from == to means any hour. */
    static final int[] JOB_FROM = {8 * 60, 10 * 60, 4 * 60 + 30, 19 * 60, 0, 7 * 60, 8 * 60, 21 * 60, 5 * 60 + 30, 8 * 60};
    static final int[] JOB_TO = {16 * 60, 16 * 60, 6 * 60, 21 * 60, 0, 14 * 60, 18 * 60, 23 * 60, 7 * 60, 12 * 60};
    /** Energy per hour on shift: carrying trays and dough is harder than a till. */
    static final float[] JOB_ENERGY = {6f, 7f, 7f, 6f, 7f, 4f, 6f, 6f, 6f, 4f};
    static final int[] JOB_NEED_STAT = {NONE, CHA, FIT, CHA, NONE, NONE, NONE, FIT, FIT, EDU};
    static final int[] JOB_NEED_VALUE = {0, 15, 15, 30, 0, 0, 0, 25, 10, 25};
    static final int[] JOB_TRAINS = {FIT, CHA, FIT, CHA, FIT, CHA, FIT, FIT, CHA, CHA};
    /** 1 = service job with tips. */
    static final int[] JOB_TIPS = {0, 1, 0, 1, 0, 0, 0, 0, 0, 0};
    /**
     * Each job has ONE core minigame that earns the wage. Optional extras inside a job (side modes,
     * events, bonus orders) are never required, and all of them together add at most this share of
     * that day's shift pay.
     */
    static final float EXTRAS_DAILY_BONUS_CAP = 0.10f;
    /** Tips as a fraction of the shift's base pay: TIP_BASE + cha * TIP_PER_CHA, times the round multiplier. */
    static final float TIP_BASE = 0.10f, TIP_PER_CHA = 0.004f;
    /** Pay multiplier for a MISS / OK / PERFECT round. */
    static final float[] RESULT_MULT = {0.4f, 1f, 1.3f};
    static final int[] RANK_XP = {0, 100, 350};
    static final float[] RANK_PAY = {1f, 1.2f, 1.4f};
    /** XP per shift = XP_BASE + XP_PER_SCORE * average round score (0..2), times (1 + edu * EDU_XP_BONUS). */
    static final float XP_BASE = 4f, XP_PER_SCORE = 8f;

    // =============================== professions ===============================
    // Profession ids: 0 doctor, 1 lawyer, 2 developer. Office/clinic is in the centre.
    static final int DOCTOR = 0, LAWYER = 1, DEVELOPER = 2;
    static final int[] PRO_PAY = {72, 64, 58};
    /**
     * Tuition, paid in two halves: one on enrolment, one when half the sessions are done. What the
     * player cannot pay of the second half becomes a student loan (ordinary DEBT, garnished).
     */
    static final int[] PRO_TUITION = {4400, 3400, 2400};
    static final int[] PRO_SESSIONS = {20, 16, 12};
    static final float PRO_SESSION_HOURS = 4f;
    static final int PRO_SESSION_FROM = 8 * 60, PRO_SESSION_TO = 14 * 60;
    static final int[] PRO_NEED_EDU = {50, 40, 30};
    /** Second requirement: doctor needs fitness, lawyer charisma, developer nothing. */
    static final int[] PRO_NEED_STAT = {FIT, CHA, NONE};
    static final int[] PRO_NEED_VALUE = {40, 30, 0};
    /** Education granted over the whole programme (spread evenly over the sessions). */
    static final int[] PRO_EDU_GRANT = {30, 25, 20};
    /** One-off fee at graduation (licence exam, bar exam, nothing for code). */
    static final int[] PRO_LICENSE = {800, 600, 0};
    /** 1 = a criminal record closes this path for good. */
    static final int[] PRO_CLEAN_RECORD = {1, 1, 0};
    /** Monthly professional costs (insurance, suits, a laptop that keeps breaking). */
    static final int[] PRO_UPKEEP = {250, 250, 120};
    /** Professionals are salaried: a bad round costs less, a perfect one earns less extra. */
    static final float[] PRO_RESULT_MULT = {0.7f, 1f, 1.15f};
    static final int PRO_FROM = 7 * 60, PRO_TO = 11 * 60;

    // =============================== homes ===============================
    // Home tiers: 0 the far cheap flat of today, 1 mid, 2 central nice. -1 = hostel after eviction.
    static final int[] HOME_RENT = {900, 1200, 2400};
    static final int[] HOME_DEPOSIT = {0, 1200, 2400};
    /** Arnona, electricity, internet: due with the rent. */
    static final int[] HOME_BILLS = {100, 150, 220};
    /** Energy restored per hour of sleep. Cheap mattress, thin walls. */
    static final float[] HOME_SLEEP = {10f, 12f, 14f};
    /** Walking minutes from the door to the restaurant/market corner. */
    static final int[] HOME_TO_CENTRE = {135, 80, 30};
    /** Earliest month the listing appears, and the proof of income the landlord wants. */
    static final int[] HOME_FROM_MONTH = {1, 3, 6};
    /** Required: any job at this rank (mid) or a profession (nice), plus deposit + first rent in cash. */
    static final int HOME_MID_NEED_RANK = 1;
    static final int MOVE_COST = 250;
    /** The landlord raises every rent by this fraction from this month on. */
    static final int RENT_RAISE_MONTH = 7;
    static final float RENT_RAISE = 0.1f;

    // =============================== transport ===============================
    // Modes: 0 foot, 1 bus, 2 bicycle, 3 scooter, 4 taxi.
    static final float[] TR_SPEED = {1f, 3f, 2f, 4f, 4f};
    static final int[] TR_BUY = {0, 0, 800, 3000, 0};
    /** Per leg (one trip between two places): bus ticket, scooter fuel, taxi fare. */
    static final int[] TR_PER_LEG = {0, 6, 0, 5, 40};
    /** Average wait for the ride to arrive (bus every 30 minutes). */
    static final int[] TR_WAIT = {0, 15, 0, 0, 5};
    static final int[] TR_UPKEEP = {0, 0, 30, 120, 0};
    /** Activity used for needs while travelling: walking or sitting. */
    static final int[] TR_ACTIVITY = {A_WALK, A_RIDE, A_WALK, A_RIDE, A_RIDE};
    /** The bus does not run at night. */
    static final int BUS_FROM = 6 * 60, BUS_TO = 24 * 60;
    /** Scooter fuel is bought at the gas station by the tank. */
    static final int FUEL_TANK_PRICE = 50, FUEL_TANK_LEGS = 10;

    // =============================== prices ===============================
    static final int START_MONEY = 300;
    static final int MEAL_PRICE = 18, BOTTLE_PRICE = 5, DINE_PRICE = 45;
    static final int BAG_MAX = 4;
    /** A bigger backpack: more meals and bottles, more room for goods. */
    static final int BACKPACK_PRICE = 250, BACKPACK_MAX = 6;

    // =============================== the alley ===============================
    // Goods: 0 fake perfume, 1 fake tickets, 2 phones that "fell off a truck", 3 "סחורה".
    static final int[] GOOD_BASE = {100, 220, 600, 400};
    /** Prices never leave [base * LO, base * HI]. */
    static final float[] GOOD_LO = {0.5f, 0.4f, 0.55f, 0.3f};
    static final float[] GOOD_HI = {1.6f, 1.9f, 1.6f, 2.4f};
    /** Daily standard deviation of log price, and how hard it is pulled back to base (0..1). */
    static final float[] GOOD_VOL = {0.15f, 0.25f, 0.2f, 0.35f};
    static final float GOOD_REVERT = 0.5f;
    /** Chance per night of a crash or a boom in one good (half or double, clamped to the band). */
    static final float GOOD_EVENT = 0.06f;
    /**
     * "The guy" fronts goods on credit: up to FRONT_MAX of stock, repaid with FRONT_FEE on top out of
     * the next sales. Without it nobody could start trading on a cook's savings. An unpaid front
     * after FRONT_DAYS becomes ordinary debt and the guy's friends raise the scuffle chance x3.
     */
    static final int FRONT_MAX = 2000, FRONT_DAYS = 5;
    static final float FRONT_FEE = 0.2f;
    /** Units of goods the bag holds (BACKPACK raises it). */
    static final int GOODS_CAP = 12, GOODS_CAP_BACKPACK = 20;
    /** Heat per deal (one buy or one sell, any amount) plus per ₪500 of value. */
    static final float HEAT_PER_DEAL = 5f, HEAT_PER_500 = 1f;
    /** Heat lost per day: a flat part plus a share of the current heat, so it settles instead of piling up. */
    static final float HEAT_DECAY_DAY = 2f, HEAT_DECAY_SHARE = 0.25f;
    /** Police chance on leaving: POLICE_BASE + POLICE_K * (heat/100)^2. */
    static final float POLICE_BASE = 0.03f, POLICE_K = 1.4f;
    /** A rival picks a fight when you carry at least this much value. */
    static final float SCUFFLE_CHANCE = 0.04f;
    static final int SCUFFLE_MIN_VALUE = 500;
    /** Chase escape: base + skill (the minigame) + fitness. */
    static final float ESCAPE_BASE = 0.35f, ESCAPE_SKILL = 0.35f;
    static final int[] ARREST_FINE = {1000, 2000};
    static final int[] ARREST_DAYS = {1, 3};
    static final float ARREST_HEAT_AFTER = 10f;
    /** Lost scuffle: this share of the goods, and energy. */
    static final float SCUFFLE_LOSS = 0.5f, SCUFFLE_ENERGY = 25f;
    static final int ALLEY_FROM = 22 * 60, ALLEY_TO = 5 * 60;

    // =============================== the casino ===============================
    static final int TABLE_MIN = 10, TABLE_MAX = 200, VIP_MAX = 500;
    /** Lifetime wagered to be offered the VIP table. */
    static final int VIP_AFTER = 5000;
    /** The cage sells at most this many chips per night: no one is ruined in one visit. */
    static final int NIGHT_BUYIN = 500, NIGHT_BUYIN_VIP = 1000;
    /** Return to player per game, for a perfect player: blackjack, roulette, slots, poker. */
    static final float[] GAME_RTP = {0.985f, 0.973f, 0.92f, 0.95f};
    /** Blackjack RTP for a clueless player (bad decisions cost about 5%). */
    static final float BLACKJACK_RTP_CLUELESS = 0.93f;
    /** Free match-play chips on the first visit only. */
    static final int WELCOME_CHIPS = 50;
    /** A free drink per visit; nothing else is ever free. */
    static final float COMP_WATER = 40f;

    // =============================== failure and recovery ===============================
    /** Unpaid rent rolls over with this fine; RENT_EVICT_AFTER misses in a row is eviction. */
    static final int RENT_FINE = 100;
    static final int RENT_EVICT_AFTER = 2;
    /** Hostel after eviction: pay per night, poor sleep, 90 minutes from the centre. */
    static final int HOSTEL_NIGHT = 50;
    static final float HOSTEL_SLEEP = 9f;
    static final int HOSTEL_TO_CENTRE = 90;
    /** Re-renting the cheap flat after an eviction needs a deposit. */
    static final int REENTRY_DEPOSIT = 450;
    /** Mom bails you out once a year when the money runs out. */
    static final int MOM_BAILOUT = 1000;
    static final int MOM_BAILOUT_TRUST = -15, MOM_BAILOUT_WORRY = 20;
    /**
     * Money never goes below zero. A fine or an ambulance you cannot pay becomes DEBT: a separate
     * number, charged 5% a month, paid off by garnishing 30% of every wage. Cash stays spendable on
     * food, so a bad week cannot turn into a starvation spiral.
     */
    static final float DEBT_GARNISH = 0.3f;
    static final float DEBT_INTEREST_MONTH = 0.05f;
    /** Mom's emergency food box: food under 40, no meals in the bag and no money for one. At most every N days. */
    static final int MOM_BOX_MEALS = 2, MOM_BOX_EVERY_DAYS = 3;

    // =============================== sinks through the year ===============================
    /**
     * Income tax on the month's wages, tips and professional pay above the threshold, taken with the
     * rent. Entry jobs never reach it and a top barman only just does; professionals pay it every
     * month. Alley money is untaxed (one more temptation).
     */
    static final int TAX_FREE_MONTH = 3000;
    static final float TAX_RATE = 0.35f;
    /** Weddings you cannot skip without mom hearing about it. */
    static final int[] WEDDING_MONTHS = {3, 7, 10};
    static final int WEDDING_GIFT = 250;
    /** Small mishaps (cracked screen, dentist, fine on the bus): average per month and price range. */
    static final float MISHAPS_PER_MONTH = 1f;
    static final int MISHAP_MIN = 40, MISHAP_MAX = 160;
    static final int MOM_BIRTHDAY_MONTH = 5, MOM_BIRTHDAY_GIFT = 150;
    /** Month 12: getting the flat ready for the parents (optional; it shapes the visit). */
    static final int VISIT_PREP_MONTH = 12, VISIT_PREP_FOOD = 400, VISIT_PREP_FURNITURE = 1500;
}
