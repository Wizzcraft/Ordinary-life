package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The engine's one door to the written content (Story, MomLines, Lines). Every call goes through
 * here so that a line that fails can never take the game down: the answer is then simply nothing.
 * The family's budget lives here too: at most one unsolicited family moment a day.
 */
final class Say {
    private Say() {
    }

    // ---------------- the family's budget ----------------

    /** Mom, dad and the family: the speakers whose unprompted messages count against the day's budget. */
    static boolean family(Beat b) {
        String s = b == null || b.speaker == null ? "" : b.speaker;
        return s.startsWith("אמא") || s.startsWith("אבא") || s.contains("סבתא") || s.contains("דודה");
    }

    /** May the family speak up unprompted on that day? */
    static boolean familyFree(Game g, int day) {
        return g.var("eng.family") < day;
    }

    static void familyUsed(Game g, int day) {
        if (day > g.var("eng.family")) g.setVar("eng.family", day);
    }

    // ---------------- story ----------------

    static List<Beat> intro(Game g) {
        try {
            return clean(Story.intro(g.facts()));
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /** The morning's beats; a family message that would be the day's second one waits for another day. */
    static List<Beat> newDay(Game g) {
        List<Beat> out = new ArrayList<>();
        try {
            for (Beat b : clean(Story.onNewDay(g.facts(), g.rnd))) {
                if (family(b)) {
                    if (!familyFree(g, g.day)) continue;
                    familyUsed(g, g.day);
                }
                out.add(b);
            }
        } catch (RuntimeException e) {
            // A morning without a story beat is still a morning.
        }
        return out;
    }

    static List<Beat> finale(Game g) {
        try {
            return clean(Story.finale(g.facts()));
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private static List<Beat> clean(List<Beat> beats) {
        List<Beat> out = new ArrayList<>();
        if (beats != null) for (Beat b : beats) if (b != null) out.add(b);
        return out;
    }

    // ---------------- mom ----------------

    /**
     * Mom's weekly call: ONE beat, so the call is two cards at most (what she says with the answers,
     * and her reply to the answer that was picked).
     */
    static Beat weekly(Game g) {
        try {
            return MomLines.weekly(g.facts(), g.rnd);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Mom's word on the day that ended, or "" when the family already had its moment that day.
     * The remark belongs to the day it is about, so it does not use up the new day's moment.
     */
    static String summary(Game g) {
        int about = g.minute < 12 * 60 ? g.day - 1 : g.day;
        if (!familyFree(g, about)) return "";
        try {
            String s = MomLines.summary(g.facts(), g.rnd);
            if (s == null || s.isEmpty()) return "";
            familyUsed(g, about);
            return s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A toast from mom about a small moment, or "" when she already spoke today (or has nothing to say). */
    static String toast(Game g, String situation) {
        if (!familyFree(g, g.day)) return "";
        try {
            String s = MomLines.toast(situation, g.facts(), g.rnd);
            if (s == null || s.isEmpty()) return "";
            familyUsed(g, g.day);
            return s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---------------- the city's small voices ----------------

    static Beat street(Game g) {
        try {
            Beat b = Lines.street(g.facts(), g.rnd);
            if (b != null && family(b)) {
                if (!familyFree(g, g.day)) return null;
                familyUsed(g, g.day);
            }
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String shiftStart(Game g, Job job, int rank) {
        try {
            return str(Lines.shiftStart(job.profession ? -1 : job.id, rank, g.facts(), g.rnd));
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String shiftEnd(Game g, Job job, float score0to2) {
        try {
            return str(Lines.shiftEnd(job.profession ? -1 : job.id, score0to2, g.facts(), g.rnd));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The boss on a hire (rank 0) or a promotion. The professions have no written boss yet: no beat. */
    static Beat promotion(Game g, Job job, int rank) {
        if (job.profession) return null;
        try {
            return Lines.promotion(job.id, rank, g.facts());
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String closed(Game g, int building) {
        try {
            return str(Lines.closed(building, g.facts(), g.rnd));
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String tip(Game g) {
        try {
            return str(Lines.tip(g.rnd));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
