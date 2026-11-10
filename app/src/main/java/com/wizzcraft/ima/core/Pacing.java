package com.wizzcraft.ima.core;

/**
 * THE PACING TABLE. Every number that decides how much story the player meets, and when, lives here
 * and nowhere else. The narrative director owns the numbers; {@link StoryDirector} owns the mechanism.
 * {@code StorySim} prints what a change here does to a thousand simulated years: run it after any edit.
 */
final class Pacing {
    private Pacing() {
    }

    // ---- the year ----
    /** First day of each phase: arrival, grind, temptation, turning, countdown. "phase" in conditions is 1..5. */
    static final int[] PHASE_FROM = {1, 11, 36, 66, 96};
    static final String[] PHASE_NAME = {"arrival", "grind", "temptation", "turning", "countdown"};
    /** The day the parents visit. Kept equal to Story.FINAL_DAY; the lint checks it. */
    static final int VISIT_DAY = 121;

    // ---- how much story in a day ----
    /** Unsolicited scenes a day, all classes together. The weekly call and scenes the player asked for are extra. */
    static final int MAX_PER_DAY = 3;
    /** Per class, indexed by Scene.SPINE, CALL, BEAT, COLOUR. */
    static final int[] MAX_PER_DAY_BY_CLASS = {1, 1, 2, 1};
    /** Game minutes that must pass between the end of one unsolicited scene and the start of the next. */
    static final int MIN_GAP_MINUTES = 60;
    /** Cards in one scene: more than this and the lint asks for a split (heavy scenes may run longer). */
    static final int MAX_CARDS_LIGHT = 6, MAX_CARDS_HEAVY = 14;

    // ---- weight ----
    /**
     * Never two heavy scenes in a row: after a heavy scene the next one must be lighter. So that a
     * heavy spine scene can never be locked out for good, the rule lapses after this many days.
     */
    static final int HEAVY_GAP_DAYS = 2;
    /** On a day with a heavy scene, colour stays out of the way. */
    static final boolean NO_COLOUR_ON_HEAVY_DAY = true;

    // ---- family ----
    /** Unsolicited family moments a day (mom or dad reaching out). The weekly call uses the slot on its day. */
    static final int FAMILY_PER_DAY = 1;
    /** Mom phones every this many days; the call may come between these hours and waits a day if missed. */
    static final int CALL_EVERY_DAYS = 7;
    static final int CALL_FROM_HOUR = 8, CALL_TO_HOUR = 22;
    /** A weekly call is this many cards on every path, at most (ruling 6). */
    static final int CALL_MAX_CARDS = 2;

    // ---- the phone ----
    /** Nobody rings or texts between these hours, unless the scene says nightOwl(). */
    static final int PHONE_QUIET_FROM = 23, PHONE_QUIET_TO = 7;

    // ---- arcs ----
    /** A side arc with a scene ready that has not moved for this many days jumps the queue of its class. */
    static final int ARC_STARVE_DAYS = 6;
    /** A starving arc may also borrow the colour slot when its own class is full. */
    static final boolean STARVING_ARC_BORROWS_COLOUR = true;
    /** After this many days with no scene at all, colour scenes skip their chance roll: the town speaks up. */
    static final int DROUGHT_DAYS = 3;

    // ---- events ----
    /** A scene that waits for an event fires within this many days of it, or not at all (per scene: onEvent(name, days)). */
    static final int EVENT_FRESH_DAYS = 2;

    // ---- the first days ----
    /** Up to and including this day only spine scenes and the weekly call play: the player is learning the game. */
    static final int SPINE_ONLY_UNTIL_DAY = 1;

    // ---- ties ----
    /** Closeness (rel.<id>, 0..100) needed for each tie: stranger, known, friend, family (design bible). */
    static final int[] TIE_AT = {0, 20, 50, 80};
    static final String[] TIE_NAME = {"stranger", "known", "friend", "family"};
    static final String[] TIE_WORD = {"זר", "מכר", "חבר", "כמו משפחה"};
    static final String[] TIE_WORD_F = {"זרה", "מכרה", "חברה", "כמו משפחה"};
    /** What closer(person, 1..3) adds and apart(person, 1..3) takes. Closeness never decays by itself. */
    static final int[] CLOSER = {0, 5, 10, 15};
    static final int[] APART = {0, 5, 10, 20};
    /** Favours (owe.<id>): above zero they owe the player, below zero the player owes them. Nobody keeps a long tab. */
    static final int OWE_MAX = 2;
    /** An accepted appointment that the player misses costs this much closeness. */
    static final int MISSED_DATE_REL = 10;
    /** Three refusals in a row and that person stops asking for this many days. */
    static final int REFUSALS_IN_A_ROW = 3, STOPS_ASKING_DAYS = 30;
    /** Gossip: once a day one open person whose closeness is below this passes one fact to their listener. */
    static final int GOSSIP_BELOW_REL = 50;
    /** New messages a day (design bible: the phone must not become a second job). */
    static final int MESSAGES_PER_DAY = 3;
    /** An unanswered message with answers gives up after this many days (per scene: expires(days)). */
    static final int MESSAGE_EXPIRES_DAYS = 3;

    // ---- gags ----
    /** A planted gag pays off no sooner than the next day and no later than this (comedy head: after that it is dead). */
    static final int GAG_MIN_DAYS = 1, GAG_MAX_DAYS = 10;
    /** A prop (the blue box, the pool) may be mentioned this many times in one year before the sim warns. */
    static final int PROP_MENTIONS_MAX = 8;

    // ---- text (the lint enforces these) ----
    /** Characters on one card: above SOFT the lint warns, above HARD it fails. */
    static final int LINE_SOFT = 160, LINE_HARD = 220;
    /** Characters on an answer button. */
    static final int OPTION_SOFT = 20, OPTION_HARD = 26;
    static final int TEXT_MESSAGE_HARD = 140, JOURNAL_HARD = 100, LEDGER_HARD = 60;
    /** Scene ids, flags and facts become variable names: keep them short. */
    static final int ID_MAX = 40;

    /** 1..5 for a day of the year. */
    static int phase(int day) {
        int p = 1;
        for (int i = 1; i < PHASE_FROM.length; i++) if (day >= PHASE_FROM[i]) p = i + 1;
        return p;
    }

    /** The tie (0..3) that a closeness of that size has reached. */
    static int tieFor(float rel) {
        int s = 0;
        for (int i = 1; i < TIE_AT.length; i++) if (rel >= TIE_AT[i]) s = i;
        return s;
    }
}
