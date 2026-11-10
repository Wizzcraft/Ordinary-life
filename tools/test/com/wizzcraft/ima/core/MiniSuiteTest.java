package com.wizzcraft.ima.core;

import java.util.Random;

/**
 * Tests the gate itself: a good fixture Mini must pass every check, and each deliberately broken
 * fixture must trip exactly the check it breaks. Without this a silently broken MiniSuite would let
 * everything through.
 */
public final class MiniSuiteTest {
    private MiniSuiteTest() {
    }

    /** A well-behaved one-round event: ends after 2 s, result follows the bot's skill. */
    static class Good extends Mini {
        float t, skill;

        int rounds() { return 1; }
        String title() { return "בדיקה"; }
        String howTo() { return "לוחצים על המסך כדי לבדוק את הערכה הזאת."; }
        void begin(int round, float difficulty) { t = 0; }
        int update(float dt) {
            t += dt;
            if (t < 2) return RUNNING;
            return skill > 0.8f ? PERFECT : skill > 0.4f ? OK : MISS;
        }
        void draw(Gfx c, float top, float bottom) { c.fillRect(0, top, W, bottom - top, Ui.PANEL); }
        void bot(Random r, float s) { skill = s; }
    }

    static class NeverEnds extends Good {
        int update(float dt) { return RUNNING; }
    }

    static class Throws extends Good {
        int n;
        int update(float dt) {
            if (++n > 500) throw new IllegalStateException("boom");
            return super.update(dt);
        }
    }

    static class DrawsOutside extends Good {
        void draw(Gfx c, float top, float bottom) { c.fillRect(0, top, W, bottom - top + 80, Ui.PANEL); }
    }

    static class TooManyPrimitives extends Good {
        void draw(Gfx c, float top, float bottom) {
            for (int i = 0; i < 1600; i++) c.fillCircle(100, top + 20, 2, Ui.INK);
        }
    }

    static class BadHost extends Good {
        int update(float dt) {
            h.setVar("Bad Key", 1);
            h.sfx(99);
            return super.update(dt);
        }
    }

    static class NoSkillEffect extends Good {
        int update(float dt) {
            t += dt;
            return t < 2 ? RUNNING : PERFECT;
        }
    }

    static class BadMeta extends Good {
        String title() { return "כותרת ארוכה מדי לגמרי כאן"; }
        int rounds() { return 3; }
    }

    static class GoodVenue extends Good {
        boolean exit;

        int rounds() { return 0; }
        void begin(int round, float difficulty) { exit = false; }
        int update(float dt) { return RUNNING; }
        void draw(Gfx c, float top, float bottom) {
            c.fillRect(0, top, W, bottom - top, Ui.PANEL);
            c.text("יציאה", 180, bottom - 20, 16, Ui.INK, Gfx.CENTER);
        }
        void up(float x, float y) { if (y > 100) exit = true; }
        boolean wantsExit() { return exit; }
    }

    static class VenueWithResult extends GoodVenue {
        int update(float dt) { return OK; }
    }

    static class VenueNoExit extends GoodVenue {
        void draw(Gfx c, float top, float bottom) { c.fillRect(0, top, W, bottom - top, Ui.PANEL); }
    }

    static MiniSuite.Report run(Class<? extends Mini> cls) {
        MiniSuite.quick = true;
        MiniSuite.tiny = true;
        MiniSuite.Report rep = new MiniSuite.Report(cls.getSimpleName());
        MiniSuite.testMini(rep, cls);
        return rep;
    }

    static String why(MiniSuite.Report r) {
        return r.name + " failures: " + r.fails;
    }

    static void expectOnly(Class<? extends Mini> cls, String... checks) {
        MiniSuite.Report r = run(cls);
        for (String c : checks) T.isTrue(r.fails.containsKey(c), cls.getSimpleName() + " should fail check '" + c + "' but " + why(r));
        T.eq(checks.length, r.fails.size(), cls.getSimpleName() + " should fail exactly " + String.join(",", checks) + "; " + why(r));
    }

    static void testGoodMiniPasses() {
        MiniSuite.Report r = run(Good.class);
        T.isTrue(r.fails.isEmpty(), "a well-behaved Mini passes everything; " + why(r));
        T.near(2.0, r.score1, 0.001, "expert score");
        T.near(0.0, r.score0, 0.001, "clueless score");
    }

    static void testGoodVenuePasses() {
        MiniSuite.Report r = run(GoodVenue.class);
        T.isTrue(r.fails.isEmpty(), "a well-behaved venue passes everything; " + why(r));
    }

    static void testNeverEndingRoundIsCaught() { expectOnly(NeverEnds.class, "ends", "curve", "pause"); }

    static void testExceptionIsCaught() {
        MiniSuite.Report r = run(Throws.class);
        T.isTrue(r.fails.containsKey("monkey") || r.fails.containsKey("curve") || r.fails.containsKey("pause"), "an exception in update() fails a check; " + why(r));
        T.isTrue(r.fails.values().toString().contains("boom"), "the message names the exception; " + why(r));
    }

    static void testDrawingOutsideIsCaught() { expectOnly(DrawsOutside.class, "draw"); }

    static void testTooManyPrimitivesAreCaught() { expectOnly(TooManyPrimitives.class, "draw"); }

    static void testBadHostCallsAreCaught() { expectOnly(BadHost.class, "host"); }

    static void testSkillThatDoesNothingIsCaught() { expectOnly(NoSkillEffect.class, "curve"); }

    static void testBadTitleAndRoundsAreCaught() {
        MiniSuite.Report r = run(BadMeta.class);
        T.isTrue(r.fails.containsKey("meta"), why(r));
        T.isTrue(r.fails.get("meta").toString().contains("rounds()") && r.fails.get("meta").toString().contains("title"),
                "title and rounds both reported; " + why(r));
    }

    static void testVenueReturningAResultIsCaught() { expectOnly(VenueWithResult.class, "venue", "pause"); }

    static void testVenueWithoutExitIsCaught() { expectOnly(VenueNoExit.class, "venue"); }
}
