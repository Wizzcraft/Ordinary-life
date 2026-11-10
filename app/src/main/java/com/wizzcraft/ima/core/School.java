package com.wizzcraft.ima.core;

/**
 * The school: two programmes (medicine, software), each a short run of lessons. A lesson is a
 * one-round study Mini hosted by {@link EventScreen}; enough passed lessons give the diploma that
 * opens the profession in the office building.
 *
 * PREVIEW: the programme is LESSONS passed lessons at a flat price, so the professions can be
 * reached in one sitting. The full plan (Tuning.PRO_SESSIONS, PRO_TUITION, the entry requirements,
 * the licence fee) is not wired yet.
 */
final class School {
    static final int LESSONS = 3;
    static final int PRICE = Tuning.COURSE_PRICE;
    static final int MINUTES = (int) (Tuning.COURSE_HOURS * 60);
    /** Education a lesson gives: a failed one still teaches a little. */
    static final float EDU_PASS = Tuning.COURSE_EDU, EDU_PERFECT = Tuning.COURSE_EDU + 1, EDU_FAIL = 0.5f;
    /** How hard the first lesson is, and how much each passed lesson adds. */
    static final float DIFF_FIRST = 0.15f, DIFF_STEP = 0.25f;

    static final int COUNT = 2;
    static final String[] MINI = {"study.doctor", "study.dev"};
    static final int[] JOB = {Job.DOCTOR, Job.DEV};
    static final String[] NAME = {"רפואה", "פיתוח תוכנה"};
    private static final String[] KEY = {"school.doctor", "school.dev"};

    private School() {
    }

    static int index(String mini) {
        for (int i = 0; i < COUNT; i++) if (MINI[i].equals(mini)) return i;
        return -1;
    }

    static int passed(Game g, int i) {
        return (int) Ui.clamp(g.var(KEY[i]), 0, LESSONS);
    }

    static boolean graduated(Game g, int i) {
        return g.flag("dip." + JOB[i]);
    }

    /** Is a lesson of programme i on offer in this build? */
    static boolean offered(int i) {
        return Minis.has(MINI[i]);
    }

    /** Pays for a lesson and opens it; null when it cannot start. */
    static Screen lesson(Game g, int i) {
        if (i < 0 || i >= COUNT || graduated(g, i) || !offered(i) || g.money < PRICE) return null;
        Screen s = EventScreen.open(g, MINI[i], DIFF_FIRST + DIFF_STEP * passed(g, i));
        if (s != null) g.buy(PRICE);
        return s;
    }

    /** The text of the card after a lesson (nothing is applied yet). */
    static String outcome(Game g, String mini, int result) {
        int i = index(mini);
        if (i < 0) return "";
        if (result == Mini.MISS) return "השיעור עבר מעל הראש. משהו בכל זאת נשאר, ואפשר לחזור עליו.";
        int left = LESSONS - passed(g, i) - 1;
        if (left <= 0) return "זה היה השיעור האחרון. התעודה בדרך.";
        return (result == Mini.PERFECT ? "שיעור מצוין. " : "שיעור טוב. ")
                + (left == 1 ? "נשאר עוד שיעור אחד עד התעודה." : "נשארו עוד " + left + " שיעורים עד התעודה.");
    }

    /** The card was closed: the hours pass, education grows, and the last lesson brings the diploma. */
    static void done(Game g, String mini, int result) {
        int i = index(mini);
        Screen was = g.screen;
        g.advance(MINUTES, Game.IDLE);
        if (i >= 0) {
            g.train(Host.EDU, result == Mini.MISS ? EDU_FAIL : result == Mini.PERFECT ? EDU_PERFECT : EDU_PASS);
            if (result != Mini.MISS && !graduated(g, i)) {
                g.setVar(KEY[i], passed(g, i) + 1);
                if (passed(g, i) >= LESSONS) {
                    g.setVar("dip." + JOB[i], 1);
                    Job j = Job.byId(JOB[i]);
                    g.dialog("תעודה!", g.hey() + "סיימת את לימודי ה" + NAME[i] + ". מהיום אפשר לבקש עבודה כ"
                            + (j == null ? "" : j.name(g.female)) + " בבניין המשרדים.", Sfx.LEVEL);
                }
            }
        }
        // A collapse on the way out has already chosen the next screen.
        if (g.screen == was) g.screen = new BuildingScreen(g, City.SCHOOL);
    }
}
