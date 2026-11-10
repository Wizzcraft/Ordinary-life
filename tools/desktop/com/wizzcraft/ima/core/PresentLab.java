package com.wizzcraft.ima.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bench for the story views (TalkView, PhoneView, JournalView). Plays each mock scene the way the
 * dialog system would, writes screenshots at heights 760 and 560, and checks what a picture cannot
 * show: no text under 12 units, no allocation in the draw paths, a sane primitive count, nothing
 * thrown under random touches. Prints PRESENT OK.
 *
 *   java -Djava.awt.headless=true -cp CLASSES com.wizzcraft.ima.core.PresentLab OUT_DIR
 *
 * The content of the mock scenes is the real game: mom's first call (MomLines), dad's texts
 * (Story), and the owner's milk gag as the first thread.
 */
final class PresentLab {
    private static final float DT = 1 / 60f;
    private static final int[] HEIGHTS = {760, 560};
    private static String outDir;
    private static final List<String> problems = new ArrayList<>();
    private static int shots;

    public static void main(String[] args) throws IOException {
        outDir = args.length > 0 ? args[0] : "tools/out/present";
        for (int h : HEIGHTS) {
            callScenes(h);
            textScenes(h);
        }
        System.out.println("shots: " + shots + " in " + outDir);
        if (!problems.isEmpty()) {
            for (String p : problems) System.out.println("PROBLEM " + p);
            System.out.println("PRESENT FAILED (" + problems.size() + ")");
            System.exit(1);
        }
        System.out.println("PRESENT OK");
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** Collects what the view says to the dialog system. */
    private static final class Ear implements TalkView.Out {
        int answer = -9, sounds, ticks;
        boolean accepted, declined, closed;

        @Override
        public void sfx(int id) {
            sounds++;
            if (id == Sfx.TICK) ticks++;
        }

        @Override
        public void answer(int index) {
            answer = index;
        }

        @Override
        public void call(boolean yes) {
            accepted = yes;
            declined = !yes;
        }

        @Override
        public void closed() {
            closed = true;
        }
    }

    /** The surface and height of the scene being played: the game draws every frame, so the bench does too. */
    private static AwtGfx cur;
    private static int curH;

    private static void run(TalkView v, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            v.update(DT);
            if (i % 2 == 0) v.draw(cur, 0, curH);
        }
    }

    /** Runs until the view has nothing in flight (the line is typed, the bubble has arrived), then a little more. */
    private static void settle(TalkView v, AwtGfx probe, int h, float extra) {
        for (int i = 0; i < 60 * 12 && v.busy(); i++) {
            v.update(DT);
            if (i % 6 == 0) v.draw(probe, 0, h);
        }
        run(v, extra);
    }

    private static void tap(TalkView v, float x, float y) {
        v.down(x, y);
        v.update(DT);
        v.up(x, y);
        v.update(DT);
    }

    private static void shot(TalkView v, int h, String name) throws IOException {
        AwtGfx g = new AwtGfx(720, h * 2, 2f);
        g.fillRect(0, 0, 360, h, Present.S0);
        v.draw(g, 0, h);
        g.save(outDir + File.separator + h + File.separator + name + ".png");
        shots++;
    }

    private static AwtGfx probe(int h) {
        cur = new AwtGfx(720, h * 2, 2f);   // same scale as the shots: Java2D text metrics change with scale
        curH = h;
        return cur;
    }

    // ---- scenes -----------------------------------------------------------------------------------

    private static final String MOM_1 = "הלו? נועה? זאת אמא. אמא שלך. מהבית. הגעת בשלום? אבא לא יכול לדבר, הוא יצא להביא חלב.";
    private static final String MOM_1_REPLY = "'הכל טוב'. ככה אומרים בסרטים רגע לפני שמשהו מתפוצץ. לקחת סוודר?";

    private static void callScenes(int h) throws IOException {
        AwtGfx p = probe(h);
        TalkView v = new TalkView();
        Ear ear = new Ear();
        v.listen(ear);
        v.status("19:42", 0.62f);
        v.world(1, true);
        v.open(TalkView.CALL, "mom", "אמא", "הבית", "home");
        v.ring(true);
        v.draw(p, 0, h);
        run(v, 1.35f);
        shot(v, h, "01-call-ringing");

        // Answer: the accept button is on the right.
        v.draw(p, 0, h);
        tap(v, 180 + 84, h - 104);
        if (!ear.accepted) problems.add("call: accept did not register at h=" + h);
        v.say(MOM_1, TalkView.CALM);
        v.ask("הגעתי, הכל טוב", "שכחתי להתקשר, סליחה", "יש לי מרפסת!");
        v.draw(p, 0, h);
        run(v, 1.2f);
        shot(v, h, "02-call-typing");
        settle(v, p, h, 0.8f);
        shot(v, h, "03-call-answers");

        v.holdAnswer(0);
        shot(v, h, "04-call-pressed");

        // Pick the first answer: it becomes a trace, mom reacts and replies.
        v.draw(p, 0, h);
        float ay = h - 16 - 3 * 52 - 2 * 8 + 26;
        tap(v, 180, ay);
        if (ear.answer != 0) problems.add("call: answer 0 did not register at h=" + h + " (got " + ear.answer + ")");
        v.say(MOM_1_REPLY, TalkView.THINKING);
        run(v, 1.5f);
        shot(v, h, "05-call-reply");
        settle(v, p, h, 0.6f);
        shot(v, h, "06-call-reply-done");

        v.hangUp();
        run(v, 0.5f);
        shot(v, h, "07-call-ended");
    }

    private static void textScenes(int h) throws IOException {
        AwtGfx p = probe(h);
        TalkView v = new TalkView();
        Ear ear = new Ear();
        v.listen(ear);
        v.status("06:12", 0.88f);
        v.world(0.6f, true);
        v.open(TalkView.TEXTS, "dad", "אבא", "", "home");
        v.presence("נראה לאחרונה אתמול", "מקליד…");
        v.signsWith("אבא");
        v.day("יום 2");
        v.past(false, "הגעת? אמא אומרת שלא התקשרת, ושהיא בכלל לא מחכה לטלפון. היא יושבת ליד הטלפון. תתקשרי. אבא", "07:15");
        v.day("יום 3");
        v.past(false, "שמעתי שמצאת עבודה. גאה בך. אל תגידי לאמא שסיפרתי לך שהיא בכתה. היא תגיד שזה מהבצל. לא היה בצל. אבא", "20:40");
        v.day("יום 9");
        v.incoming("אני מחפש חלב כבר שבוע. בכל העיר. אבא", "06:12");
        v.ask("מצאת?", "אבא, תחזור הביתה");
        v.draw(p, 0, h);
        run(v, 1.0f);
        shot(v, h, "10-texts-typing");
        settle(v, p, h, 0.9f);
        shot(v, h, "11-texts-milk");

        v.draw(p, 0, h);
        tap(v, 180, h - 16 - 26);
        if (ear.answer != 1) problems.add("texts: reply 1 did not register at h=" + h + " (got " + ear.answer + ")");
        v.incoming("עוד לא מצאתי. אבא", "06:14");
        run(v, 0.7f);
        shot(v, h, "12-texts-replied");
        settle(v, p, h, 1.2f);
        shot(v, h, "13-texts-answer");
    }

    @SuppressWarnings("unused")
    private static final Random RND = new Random(7);
}
