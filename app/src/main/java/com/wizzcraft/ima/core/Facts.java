package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A read-only snapshot of the player's situation, handed to the narrative code (story beats, mom's
 * phone calls) so it can pick fitting lines without knowing anything about the game engine.
 */
final class Facts {
    boolean female;
    String name = "";
    /** day counts from 1; a month is 10 days; the year (and the game) is 12 months. */
    int day, month, dayOfMonth, daysToRent;
    /** Minutes since midnight, and the hour of the day 0..23. */
    float minute;
    int hour;
    /** The building the player is in or at the door of (a City id), or -1 out on the street. */
    int place = -1;
    /** Living in the hostel after an eviction. */
    boolean homeless;
    /** What is owed after fines and bills that could not be paid (paid off from wages). */
    int debt;
    /** Today's unsolicited family moment has already happened: do not offer another. */
    boolean familyToday;
    int money, rentDue, rentDebt, rentMissed, shiftsDone;
    /** Needs, 0..100. */
    float food, water, energy;
    /** Best-paid job the player holds: its id (-1 = unemployed), name in the player's gender, rank 0..2. */
    int jobId = -1, jobRank;
    String jobName = "";
    int jobsHeld;
    /** Stats, 0..100. */
    float fit, cha, edu;
    /** Street heat 0..100, and whether the player has a criminal record. */
    float heat;
    boolean record;
    /** Yesterday's money in and out. */
    int earnedYesterday, spentYesterday;
    /**
     * Every persistent variable, including the ones set by beat effects. Shared names:
     * lie.job, lie.money, lie.home (1 = told mom that lie), lie.count, mom.trust and mom.worry
     * (0..100, both start at 50), seen.<beatId> (1 once a beat was shown), collapses, arrests.
     */
    Map<String, Float> vars = new HashMap<>();

    float var(String key) {
        Float v = vars.get(key);
        return v == null ? 0 : v;
    }

    boolean seen(String beatId) {
        return var("seen." + beatId) != 0;
    }

    /** Picks the masculine or feminine form. */
    String t(String m, String f) {
        return female ? f : m;
    }
}
