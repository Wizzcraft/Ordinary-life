package com.wizzcraft.ima.core;

/**
 * What follows a hosted Mini. The hosting screens know nothing about consequences; they call here
 * when an event's outcome card is closed and when the player walks out of a venue.
 *
 * INTEGRATORS: a new one-round event (a lesson at the school, say) gets a case in outcome() and in
 * done(); a venue that has something to say on the way out gets a case in left().
 */
final class Events {
    private Events() {
    }

    /** The text of an event's outcome card. result is Mini.MISS (lost), OK or PERFECT (won). */
    static String outcome(Game g, String id, int result) {
        switch (id) {
            case "chase":
                return Crime.chaseText(g, result);
            case "fight":
                return Crime.fightText(g, result);
            case "study.doctor":
            case "study.dev":
                return School.outcome(g, id, result);
            default:
                return result == Mini.MISS ? "הפעם זה לא הלך." : "זה עבר בשלום.";
        }
    }

    /** The outcome card was closed: apply the consequences and move on to the next screen. */
    static void done(Game g, String id, int result) {
        switch (id) {
            case "chase":
                Crime.chaseDone(g, result);
                break;
            case "fight":
                Crime.fightDone(g, result);
                break;
            case "study.doctor":
            case "study.dev":
                School.done(g, id, result);
                break;
            default:
                backToTown(g);
        }
    }

    /**
     * The player walked out of a venue; moneyDelta is what the visit did to the wallet. Leaving the
     * alley can end in trouble; everywhere else the way out leads back to the building's menu.
     */
    static void left(Game g, String id, int building, int moneyDelta) {
        g.sfx(Sfx.DOOR);
        if (id.equals("alley")) {
            Crime.leaveAlley(g);
            return;
        }
        g.screen = new BuildingScreen(g, building);
        if (building == City.CASINO && moneyDelta != 0) g.momToast(moneyDelta > 0 ? "won" : "lost");
    }

    /** Back to wherever the player is standing: the menu of that building, or the street. */
    static void backToTown(Game g) {
        g.screen = g.at >= 0 ? new BuildingScreen(g, g.at) : new MapScreen(g);
    }
}
