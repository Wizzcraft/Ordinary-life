package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * PLACEHOLDER. The narrative team's Lines.java replaces this whole file; only the six signatures
 * below are the contract with the engine.
 */
final class Lines {
    private Lines() {
    }

    /** A chance encounter at the end of a walk, or null. The engine asks after about one walk in eight. */
    static Beat street(Facts f, Random r) {
        return Beat.say("street.hello", "עובר אורח", "סליחה, איפה פה התחנה המרכזית? לא משנה, אני אשאל מישהו אחר.");
    }

    /** One line from the workplace for the intro card of a shift. */
    static String shiftStart(int jobId, int rank, Facts f, Random r) {
        return "יאללה, לעבודה.";
    }

    /** One line for the end card of a shift; score is the average round result, 0 (all missed) to 2 (all perfect). */
    static String shiftEnd(int jobId, float score0to2, Facts f, Random r) {
        return score0to2 < 0.7f ? "לא היום. מחר יום חדש." : score0to2 < 1.6f ? "משמרת בסדר גמור." : "משמרת מושלמת. שמו לב.";
    }

    /** The boss announces a rank: newRank 0 is played when the player is hired, 1 and 2 on a promotion. */
    static Beat promotion(int jobId, int newRank, Facts f) {
        if (newRank <= 0) return Beat.say("promo." + jobId + ".0", "הבוס", "{name}, ברוך הבא לצוות.", "{name}, ברוכה הבאה לצוות.");
        return Beat.say("promo." + jobId + "." + newRank, "הבוס",
                "{name}, מגיע לך קידום. מהיום אתה " + Job.rankName(false, newRank) + ".",
                "{name}, מגיע לך קידום. מהיום את " + Job.rankName(true, newRank) + ".");
    }

    /** A piece of city wisdom for an idle moment (the engine shows it while the player sleeps). */
    static String tip(Random r) {
        return "מים מהברז בבית הם בחינם.";
    }

    /** What the player finds at a closed door. */
    static String closed(int buildingId, Facts f, Random r) {
        return "סגור עכשיו. אפשר לחכות ברחוב או לחזור מחר.";
    }
}
