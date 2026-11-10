package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * PLACEHOLDER. The narrative team's MomLines.java replaces this whole file; only the three
 * signatures below are the contract with the engine.
 */
final class MomLines {
    static final String SPEAKER = "אמא על הקו";

    private MomLines() {
    }

    /** The weekly phone call. The engine plays it on the morning of every seventh day. */
    static Beat weekly(Facts f, Random r) {
        if (f.food < 30 || f.money < 40) {
            return Beat.say("mom.week.hungry", SPEAKER,
                    "{name}, אכלת משהו היום? אתה נשמע לי חלש. שלחתי לך קופסאות עם אוכל, שלא יגידו שאני לא דואגת.",
                    "{name}, אכלת משהו היום? את נשמעת לי חלשה. שלחתי לך קופסאות עם אוכל, שלא יגידו שאני לא דואגת.")
                    .effect("meals+=2");
        }
        if (f.jobId < 0) {
            return Beat.say("mom.week.nojob", SPEAKER, "נו, {name}, מצאת כבר עבודה? הבן של רותי כבר מנהל סניף. רק אומרת.");
        }
        if (f.money > 1500) {
            return Beat.say("mom.week.rich", SPEAKER,
                    "{name}, שמעתי שאתה מסתדר יפה. אבא שואל אם יש שם חדר אורחים.",
                    "{name}, שמעתי שאת מסתדרת יפה. אבא שואל אם יש שם חדר אורחים.");
        }
        return Beat.say("mom.week.work", SPEAKER,
                "{name}, העיקר שאתה עובד. אבל חשבת אולי ללמוד משהו?",
                "{name}, העיקר שאת עובדת. אבל חשבת אולי ללמוד משהו?");
    }

    /** One line from mom for the day summary; empty or null when she has nothing to add. */
    static String summary(Facts f, Random r) {
        if (f.spentYesterday > f.earnedYesterday) return "יצא יותר ממה שנכנס. אני רק אומרת.";
        return f.earnedYesterday > 0 ? "יום של עבודה זה יום טוב." : "";
    }

    /**
     * A short reaction to a small moment, shown as a toast, or null to stay quiet. The engine asks
     * about: ate, drank, bought, tired, rich, broke, night, won, lost.
     */
    static String toast(String situation, Facts f, Random r) {
        return null;
    }
}
