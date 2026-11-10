package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PLACEHOLDER. The narrative team's Story.java replaces this whole file; only FINAL_DAY and the
 * three signatures below are the contract with the engine.
 */
final class Story {
    /** The day the parents visit: the year (12 months of 10 days) is over. */
    static final int FINAL_DAY = 121;
    private static final String MOM = "אמא על הקו";

    private Story() {
    }

    /** Played once when a new game starts. */
    static List<Beat> intro(Facts f) {
        List<Beat> out = new ArrayList<>();
        out.add(Beat.say("story.intro.arrived", MOM,
                "נו, {name}, הגעת? מצאת את הדירה? אכלת משהו? שמתי לך סנדוויץ' ובקבוק מים בתיק."));
        out.add(Beat.say("story.intro.rent", MOM,
                "שכירות משלמים כל " + Game.DAYS_PER_MONTH + " ימים, " + Ui.nis(f.rentDue)
                        + ". תמצא עבודה ואל תעשה לנו בושות. בסוף השנה אנחנו באים לבקר!",
                "שכירות משלמים כל " + Game.DAYS_PER_MONTH + " ימים, " + Ui.nis(f.rentDue)
                        + ". תמצאי עבודה ואל תעשי לנו בושות. בסוף השנה אנחנו באים לבקר!"));
        out.add(Beat.say("story.intro.howto", "איך משחקים",
                "לוחצים על בניין והדמות הולכת אליו. הליכה לוקחת זמן, והזמן לא מחכה. במסעדה מחפשים עובדים למטבח."));
        return out;
    }

    /** Asked every morning: the beats to play today, in order. May be empty. */
    static List<Beat> onNewDay(Facts f, Random r) {
        return new ArrayList<>();
    }

    /**
     * The parents' visit on FINAL_DAY. Every beat but the last is played as a message; the last
     * one becomes the end screen: its speaker is the title, its text the body.
     */
    static List<Beat> finale(Facts f) {
        List<Beat> out = new ArrayList<>();
        out.add(Beat.say("story.fin.door", MOM,
                "{name}, אנחנו למטה! אבא מחפש חניה כבר עשרים דקות. תפתח לנו!",
                "{name}, אנחנו למטה! אבא מחפש חניה כבר עשרים דקות. תפתחי לנו!"));
        out.add(Beat.say("story.fin.end", "השנה נגמרה",
                "שנה שלמה בעיר הגדולה, והחזקת מעמד. אמא בודקת את המקרר, אבא בודק את הברז, ושניהם גאים בך. בערך.",
                "שנה שלמה בעיר הגדולה, והחזקת מעמד. אמא בודקת את המקרר, אבא בודק את הברז, ושניהם גאים בך. בערך."));
        return out;
    }
}
