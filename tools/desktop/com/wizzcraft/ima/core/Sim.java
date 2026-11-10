package com.wizzcraft.ima.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * Off-device test harness: drives the real game core with scripted taps, checks the engine piece by
 * piece, plays 30-day bots and runs a random-touch soak with reloads.
 *
 *   java -Djava.awt.headless=true -cp OUT com.wizzcraft.ima.core.Sim outDir [group,group,...] [--shots] [--soak=FRAMES]
 *
 * Without a group list everything runs and the last line is "ALL OK"; any failed check prints
 * "FAILED: ..." as the last line and exits with code 1. This default is the gate mode: no pictures
 * are rendered, so it stays well under a minute on a loaded machine. --shots also photographs every
 * screen at both supported heights (outDir/shots, outDir/sheets) and lets the real renderer draw a
 * frame now and then; --soak sets the length of the random-touch run.
 */
public final class Sim {
    static final float DT = 1f / 30f;
    /** Logical height in use: 760 is the target phone after its bars, 560 the smallest screen supported. */
    static float lh = 760;
    static Game g;
    static final CountGfx gfx = new CountGfx();
    /** The Java2D renderer, created only when pictures were asked for. */
    static AwtGfx real;
    /** Sounds polled since the last heard.clear(), the way the shell polls them every frame. */
    static final BitSet heard = new BitSet();
    static String out;
    static boolean pictures;
    static int checks, frameNo, soak = 200_000;

    interface Test {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        try {
            all(args);
        } catch (Throwable t) {
            // The gate reads the last line and the exit code; the trace goes above them.
            t.printStackTrace(System.out);
            String what = String.valueOf(t.getMessage());
            System.out.println(what.startsWith("FAILED") ? what : "FAILED: " + t);
            System.exit(1);
        }
        System.out.printf("%d checks passed in %.1fs%n", checks, (System.nanoTime() - t0) / 1e9);
        System.out.println(only == null ? "ALL OK" : "SUBSET OK " + only);
    }

    static Set<String> only;

    static void all(String[] args) throws Exception {
        out = "tools/out";
        boolean named = false;
        for (String a : args) {
            if (a.equals("--shots")) pictures = true;
            else if (a.startsWith("--soak=")) soak = Integer.parseInt(a.substring(7));
            else if (!named) {
                out = a;
                named = true;
            } else only = new HashSet<>(Arrays.asList(a.split(",")));
        }
        if (pictures) real = new AwtGfx(36, 76, 0.1f);
        // The real Minis of the build are played first; the engine tests after that run on the cook
        // and on test Minis, so they stay exact whatever the real ones do.
        run(only, "minis", Sim::minis);
        run(only, "school", Sim::school);
        run(only, "realmonkey", () -> monkey(soak / 2, 3));
        for (String id : Minis.ORDER) if (!id.equals("cook")) Minis.unregister(id);
        run(only, "unregistered", Sim::unregistered);
        // From here on the alley, the two street events and the waiter are played by test Minis.
        Minis.register("alley", TestVenue::new);
        Minis.register("chase", TestEvent::new);
        Minis.register("fight", TestEvent::new);
        Minis.register("sim.event", TestEvent::new);
        Minis.register("sim.venue", TestVenue::new);
        Minis.register("waiter", TestWork::new);
        run(only, "tour", () -> {
            tour(760);
            tour(560);
        });
        run(only, "vars", Sim::vars);
        run(only, "money", Sim::money);
        run(only, "sound", Sim::sound);
        run(only, "beats", Sim::beats);
        run(only, "pending", Sim::pendingBeat);
        run(only, "jobs", Sim::jobs);
        run(only, "wages", Sim::wages);
        run(only, "rankup", Sim::rankUp);
        run(only, "stats", Sim::stats);
        run(only, "refusal", Sim::refusal);
        run(only, "regressions", Sim::regressions);
        run(only, "summary", Sim::summary);
        run(only, "mornings", Sim::mornings);
        run(only, "finale", Sim::finale);
        run(only, "events", Sim::events);
        run(only, "venues", Sim::venues);
        run(only, "crime", Sim::crime);
        run(only, "oldsaves", Sim::oldSaves);
        run(only, "saveload", Sim::saveLoad);
        run(only, "resume", Sim::resume);
        run(only, "menupause", Sim::menuPause);
        run(only, "bots", () -> {
            bot(30, 0.75f, 1);
            bot(30, 0.35f, 2);
        });
        run(only, "monkey", () -> monkey(soak, 7));
    }

    static void run(Set<String> only, String name, Test t) throws Exception {
        if (only != null && !only.contains(name)) return;
        long t0 = System.nanoTime();
        lh = 760;
        // Every group starts from the same sequence of random numbers, whichever groups ran before it.
        Game.testSeed = 1_000_003L * (name.hashCode() & 0xFFFF) + 17;
        t.run();
        System.out.printf("%-12s ok (%.1fs)%n", name, (System.nanoTime() - t0) / 1e9);
    }

    // ================= test-only Minis =================

    /** A one-round event: a tap on the left half wins, on the right half loses; four idle seconds are an OK. */
    static final class TestEvent extends Mini {
        static int begins, pauses;
        static float lastDifficulty;
        float time;
        int res = RUNNING;

        @Override
        int rounds() {
            return 1;
        }

        @Override
        String title() {
            return "אירוע ניסיון";
        }

        @Override
        String howTo() {
            return "אירוע לבדיקות בלבד. לחיצה בצד שמאל מנצחת, לחיצה בצד ימין מפסידה.";
        }

        @Override
        void begin(int round, float difficulty) {
            begins++;
            lastDifficulty = difficulty;
            time = 0;
            res = RUNNING;
        }

        @Override
        int update(float dt) {
            time += dt;
            if (res == RUNNING && time > 4) res = OK;
            return res;
        }

        @Override
        void draw(Gfx c, float top, float bottom) {
            float cy = (top + bottom) / 2;
            c.fillRoundRect(16, top + 8, 160, bottom - top - 16, 12, 0xFF1F3A2B);
            c.fillRoundRect(184, top + 8, 160, bottom - top - 16, 12, 0xFF3A1F24);
            c.text("לנצח", 96, cy, 18, Ui.GOOD, Gfx.CENTER);
            c.text("להפסיד", 264, cy, 18, Ui.BAD, Gfx.CENTER);
            Ui.bar(c, 60, bottom - 30, 240, 8, 1 - time / 4, Ui.ACCENT);
        }

        @Override
        void down(float x, float y) {
            if (res == RUNNING) res = x < W / 2 ? PERFECT : MISS;
        }

        @Override
        void art(Gfx c, float cx, float cy, float t) {
            c.fillPoly(new float[]{cx - 50, cy + 40, cx, cy - 50 + (float) Math.sin(t * 4) * 3, cx + 50, cy + 40}, Ui.ORANGE);
        }

        @Override
        void pause() {
            pauses++;
        }

        @Override
        void bot(Random r, float skill) {
            if (time > 0.3f) down(r.nextFloat() < skill ? 60 : 300, 300);
        }
    }

    /**
     * A venue: a table where a bet of 10 is settled a second later (double or nothing), a button
     * that heats the street, and the exit. An unsettled bet goes back to the player on pause().
     */
    static final class TestVenue extends Mini {
        static int begins, pauses, moves;
        boolean exit;
        int bet;
        float betT, top, bottom;

        @Override
        int rounds() {
            return 0;
        }

        @Override
        String title() {
            return "שולחן ניסיון";
        }

        @Override
        String howTo() {
            return "מקום לבדיקות בלבד. הימור של עשרה שקלים מוכרע אחרי שנייה, ויש כפתור שמחמם את הרחוב.";
        }

        @Override
        void begin(int round, float difficulty) {
            begins++;
            exit = false;
            bet = 0;
            // Like a real venue, begin() leaves a trace in the variables.
            h.setVar("simv.visits", h.var("simv.visits") + 1);
        }

        @Override
        int update(float dt) {
            if (bet > 0) {
                betT -= dt;
                if (betT <= 0) {
                    if (h.rnd().nextBoolean()) {
                        h.pay(bet * 2);
                        h.text(W / 2, top + 60, "+" + Ui.nis(bet * 2), Ui.GOOD);
                        h.sfx(Sfx.COIN);
                    }
                    bet = 0;
                }
            }
            return RUNNING;
        }

        float betY() {
            return top + (bottom - top) * 0.45f;
        }

        float exitY() {
            return bottom - 56;
        }

        @Override
        void draw(Gfx c, float t, float b) {
            top = t;
            bottom = b;
            c.fillRoundRect(16, top + 8, 328, bottom - top - 16, 14, 0xFF173524);
            c.text(Ui.nis(h.money()), W / 2, top + 44, 22, Ui.ACCENT, Gfx.CENTER);
            new Btn(0, 184, betY(), 150, 52, bet > 0 ? "מחכים..." : "הימור ₪10", bet == 0, true).draw(c);
            new Btn(1, 26, betY(), 150, 52, "לחמם את הרחוב", true, false).draw(c);
            new Btn(2, 105, exitY(), 150, 48, "יציאה", true, false).draw(c);
        }

        @Override
        void down(float x, float y) {
            if (Ui.in(x, y, 105, exitY(), 150, 48)) {
                exit = true;
            } else if (Ui.in(x, y, 184, betY(), 150, 52)) {
                if (bet == 0 && h.pay(-10)) {
                    bet = 10;
                    betT = 1;
                }
            } else if (Ui.in(x, y, 26, betY(), 150, 52)) {
                h.setVar("heat", h.var("heat") + 25);
                h.sfx(Sfx.SIREN);
            }
        }

        @Override
        void move(float x, float y) {
            moves++;
        }

        @Override
        void pause() {
            pauses++;
            if (bet > 0) {
                h.pay(bet);
                bet = 0;
            }
        }

        @Override
        boolean wantsExit() {
            return exit;
        }
    }

    /**
     * A work Mini with eight one-hour rounds. With a script every round ends with the scripted
     * result; without one the bot (or a finger) decides: left third MISS, middle OK, right PERFECT.
     */
    static final class TestWork extends Mini {
        static int[] script;
        static final float[] difficulties = new float[8];
        int round, res = RUNNING;
        float time;

        @Override
        int rounds() {
            return 8;
        }

        @Override
        String title() {
            return h.t("מלצר", "מלצרית");
        }

        @Override
        String howTo() {
            return "משמרת לבדיקות בלבד: שמונה סיבובים של שעה. לחיצה בימין מושלמת, באמצע טובה, בשמאל פספוס.";
        }

        @Override
        void begin(int round, float difficulty) {
            this.round = round;
            difficulties[round] = difficulty;
            time = 0;
            res = RUNNING;
        }

        @Override
        int update(float dt) {
            time += dt;
            if (script != null) return time > 0.15f ? script[round] : RUNNING;
            if (res != RUNNING) return res;
            return time > 5 ? MISS : RUNNING;
        }

        @Override
        void draw(Gfx c, float top, float bottom) {
            float w = W / 3;
            int[] cols = {0xFF3A1F24, 0xFF2A3140, 0xFF1F3A2B};
            String[] names = {"פספוס", "טוב", "מושלם"};
            for (int i = 0; i < 3; i++) {
                c.fillRoundRect(i * w + 6, top + 10, w - 12, bottom - top - 20, 12, cols[i]);
                c.text(names[i], i * w + w / 2, (top + bottom) / 2, 16, Ui.INK, Gfx.CENTER);
            }
        }

        @Override
        void down(float x, float y) {
            if (script == null && res == RUNNING && time > 0.2f) res = x < W / 3 ? MISS : x < 2 * W / 3 ? OK : PERFECT;
        }

        @Override
        void art(Gfx c, float cx, float cy, float t) {
            c.fillCircle(cx, cy, 46, 0xFF2A3140);
            c.fillRect(cx - 30, cy - 4 + (float) Math.sin(t * 5) * 2, 60, 8, Ui.ACCENT);
        }

        @Override
        void bot(Random r, float skill) {
            if (time > 0.3f) down(r.nextFloat() < skill ? 300 : r.nextBoolean() ? 180 : 60, 300);
        }
    }

    // ================= driving the game =================

    static void frame() {
        g.update(DT, lh);
        gfx.reset();
        g.draw(gfx);
        check(gfx.dx == 0 && gfx.dy == 0, "a frame hands the surface back unshifted");
        // With pictures on, the real renderer draws a frame now and then too.
        if (real != null && ++frameNo % 257 == 0) g.draw(real);
        for (int id = g.pollSound(); id >= 0; id = g.pollSound()) heard.set(id);
    }

    static void frames(int n) {
        for (int i = 0; i < n; i++) frame();
    }

    /** Everything the current frame writes, joined with spaces (wrapped lines read as one sentence again). */
    static String drawn() {
        gfx.texts = new ArrayList<>();
        g.draw(gfx);
        String s = String.join(" ", gfx.texts);
        gfx.texts = null;
        return s;
    }

    static String screenName() {
        return g.screen.getClass().getSimpleName();
    }

    static void tap(float x, float y) {
        g.down(x, y);
        g.up(x, y);
    }

    static void tap(Btn b) {
        tap(b.x + b.w / 2, b.y + b.h / 2);
    }

    /** Taps the button whose label starts with the prefix: on the message that is up, otherwise on the screen. */
    static boolean tapLabel(String prefix) {
        frame();
        for (Btn b : new ArrayList<>(g.hasDialog() ? g.dialogBtns : g.screen.btns)) {
            if (b.label.startsWith(prefix)) {
                if (!b.on) return false;
                tap(b);
                return true;
            }
        }
        return false;
    }

    static boolean hasButton(String prefix) {
        frame();
        for (Btn b : g.hasDialog() ? g.dialogBtns : g.screen.btns) if (b.label.startsWith(prefix)) return true;
        return false;
    }

    /** Answers every waiting message: "continue", or the first answer of a question. */
    static void closeDialogs() {
        for (int i = 0; i < 80 && g.hasDialog(); i++) {
            frame();
            if (!g.dialogBtns.isEmpty()) tap(g.dialogBtns.get(0));
        }
        check(!g.hasDialog(), "messages can be closed");
    }

    static void tapBuilding(int id) {
        frame();
        City.Building b = City.B[id];
        float areaH = lh - Game.HUD_H - MapScreen.BAR_H;
        float s = Math.min(Game.W / City.W, areaH / City.H);
        float ox = (Game.W - City.W * s) / 2, oy = Game.HUD_H + (areaH - City.H * s) / 2;
        tap(ox + (b.x + b.w / 2) * s, oy + (b.y + b.h / 2) * s);
    }

    /** Walks to a building and waits until we are inside it. Returns false if something interrupted. */
    static boolean goTo(int id) {
        // A message that arrives on the way out is answered first, or the tap for the door is lost.
        closeDialogs();
        if (!(g.screen instanceof MapScreen)) {
            if (g.screen instanceof BuildingScreen) tapLabel("חזרה");
            else return false;
        }
        closeDialogs();
        if (g.screen instanceof BuildingScreen) tapLabel("חזרה");
        if (!(g.screen instanceof MapScreen)) return false;
        tapBuilding(id);
        for (int i = 0; i < 30 * 60; i++) {
            frame();
            if (g.hasDialog()) closeDialogs();
            if (g.over) return false;
            if (g.screen instanceof BuildingScreen && g.at == id) return true;
            if (g.screen instanceof MapScreen && (g.at == id || i % 90 == 89)) tapBuilding(id);
        }
        return false;
    }

    /** Puts the player inside a building at a given time of day, without the walk. */
    static void enter(int id, int minute) {
        City.Building b = City.B[id];
        g.px = b.doorX;
        g.py = b.doorY;
        g.at = id;
        g.minute = minute;
        g.screen = new BuildingScreen(g, id);
        frame();
    }

    static void fresh() {
        g = new Game();
        startGame("גבר");
        closeDialogs();
        heard.clear();
    }

    /** Title -> gender -> type a name -> into the game. */
    static void startGame(String gender) {
        frame();
        check(tapLabel(gender), "choose " + gender);
        frame();
        check(g.screen instanceof NameScreen, "name screen opens");
        for (String k : new String[]{"ג", "י", "ל"}) check(tapLabel(k), "type " + k);
        check(tapLabel("המשך"), "confirm name");
        frame();
        check(g.started && g.name.equals("גיל"), "game started with the typed name");
    }

    static float clock() {
        return g.day * 1440f + g.minute;
    }

    /** Leaves the app and comes back in a brand-new process; nothing may change on the way. */
    static void reload() {
        reopen();
        frame();
    }

    /** reload() without the first frame afterwards. */
    static void reopen() {
        g.onAppPause();
        String s = g.save();
        Game n = new Game();
        n.load(s);
        check(n.save().equals(s), "resume: reloading changes nothing\n  " + s + "\n  " + n.save());
        check(n.screen.getClass() == g.screen.getClass(), "resume: same screen, was " + screenName());
        g = n;
    }

    /** Plays the shift that is on screen up to its end card, with the Mini's own bot. */
    static ShiftScreen playShift(Random r, float skill) {
        for (int i = 0; i < 30 * 900 && g.screen instanceof ShiftScreen; i++) {
            ShiftScreen s = (ShiftScreen) g.screen;
            if (g.hasDialog()) {
                closeDialogs();
            } else if (s.state == ShiftScreen.DONE) {
                frame();
                return s;
            } else if (s.state == ShiftScreen.INTRO) {
                check(tapLabel("מתחילים"), "the intro card has a start button");
            } else if (s.state == ShiftScreen.PAUSED) {
                check(tapLabel("ממשיכים"), "the pause card has a continue button");
            } else {
                if (s.playing()) s.mini.bot(r, skill);
                frame();
            }
        }
        return null;
    }

    /** What the rounds of a finished shift should have paid, by the published formula. */
    static int[] expectedPay(ShiftScreen s, float cha) {
        int pay = 0, tips = 0, streak = 0;
        float roundPay = s.job.pay * (8f / s.rounds);
        int n = s.sentHome ? s.round + 1 : s.rounds;
        for (int i = 0; i < n; i++) {
            int res = s.results[i];
            streak = res == Mini.PERFECT ? streak + 1 : 0;
            pay += Math.round(roundPay * Job.RANK_PAY[s.rank] * s.job.resultPay[res]);
            if (res != Mini.MISS) {
                tips += Math.round(roundPay * (0.08f * Math.min(streak, 5) + (s.job.service ? cha / 150f : 0)));
            }
        }
        return new int[]{pay, tips};
    }

    // ================= screenshots =================

    static final List<String> shots = new ArrayList<>();

    static void shot(String name) throws Exception {
        frame();
        if (!pictures) return;
        AwtGfx big = new AwtGfx(720, (int) (lh * 2), 2f);
        g.draw(big);
        String path = out + "/shots/" + (int) lh + "/" + name + ".png";
        big.save(path);
        shots.add(path);
    }

    /** Tiles the screenshots taken so far, six to a sheet, so a whole tour can be looked at quickly. */
    static void sheets(String prefix) throws Exception {
        if (!pictures) return;
        int cols = 3, rows = 2, cw = 480, ch = Math.round(lh * 480 / 360);
        for (int first = 0, n = 1; first < shots.size(); first += cols * rows, n++) {
            BufferedImage sheet = new BufferedImage(cols * (cw + 12) + 12, rows * (ch + 12) + 12, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = sheet.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setColor(new java.awt.Color(0x444444));
            g2.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int i = 0; i < cols * rows && first + i < shots.size(); i++) {
                BufferedImage img = ImageIO.read(new File(shots.get(first + i)));
                g2.drawImage(img, 12 + (i % cols) * (cw + 12), 12 + (i / cols) * (ch + 12), cw, ch, null);
            }
            g2.dispose();
            File f = new File(out + "/sheets/" + prefix + "-" + (n < 10 ? "0" : "") + n + ".png");
            f.getParentFile().mkdirs();
            ImageIO.write(sheet, "png", f);
        }
        shots.clear();
    }

    static final Beat QUESTION = new Beat("sim.uncle", "דוד משה",
            "{name}, שמעתי מאמא שלך שאתה בעיר הגדולה כבר שבוע ועוד לא התקשרת אליי. אני לא נעלב, אני רק שואל: "
                    + "אתה צריך משהו? כסף? אוכל? עצה מדוד שעבד שלושים שנה במס הכנסה ויודע דבר או שניים על החיים?",
            "{name}, שמעתי מאמא שלך שאת בעיר הגדולה כבר שבוע ועוד לא התקשרת אליי. אני לא נעלב, אני רק שואל: "
                    + "את צריכה משהו? כסף? אוכל? עצה מדוד שעבד שלושים שנה במס הכנסה ויודע דבר או שניים על החיים?",
            new String[]{"כן, קצת כסף", "הכל בסדר, דוד", "רק משהו לאכול"},
            new String[]{"קח, {name}. ותתקשר לאמא.", null, "שמתי לך בתיק. אל תגיד לדודה."},
            new String[]{"קחי, {name}. ותתקשרי לאמא.", null, "שמתי לך בתיק. אל תגידי לדודה."},
            new String[]{"money+=50;lie.money=1;mom.trust-=10", "mom.worry+=5", "meals+=2;food=90"});

    /** Walks through every screen once at the given height and photographs it. */
    static void tour(float height) throws Exception {
        lh = height;
        shots.clear();
        g = new Game();
        g.rnd.setSeed(11);
        shot("01-title");
        check(tapLabel("חנות"), "the title screen leads to the store");
        shot("02-store-from-title");
        check(tapLabel("חזרה") && g.screen instanceof TitleScreen, "and back");
        check(tapLabel("אישה"), "choose gender");
        for (String k : new String[]{"ג", "י", "ל"}) check(tapLabel(k), "type " + k);
        shot("03-name");
        check(tapLabel("המשך"), "confirm name");
        check(g.name.equals("גיל") && g.female, "name and gender set");
        shot("04-intro-message");
        closeDialogs();
        shot("05-map-start");
        tapBuilding(City.RESTAURANT);
        frames(90);
        shot("06-walking");
        check(goTo(City.RESTAURANT), "arrive at restaurant");
        System.out.printf("  walk home->restaurant took until %s%n", Ui.clock(g.minute));
        closeDialogs();
        g.minute = 11 * 60;
        shot("07-restaurant-jobs");
        check(tapLabel("לבקש עבודה: מלצרית"), "ask to be a waitress");
        shot("08-refused");
        closeDialogs();
        check(tapLabel("לבקש עבודה: טבחית"), "ask to be a cook");
        shot("09-hired-boss");
        tapLabel("המשך");
        shot("10-hired-details");
        closeDialogs();
        shot("11-restaurant-hired");
        check(tapLabel("להתחיל משמרת: טבחית"), "start shift");
        shot("12-shift-intro");
        check(tapLabel("מתחילים"), "begin");
        Random r = new Random(3);
        ShiftScreen s = (ShiftScreen) g.screen;
        for (int i = 0; i < 60; i++) {
            s.mini.bot(r, 0.9f);
            frame();
        }
        shot("13-shift-play");
        for (int i = 0; i < 30 * 60 && s.state != ShiftScreen.SHOW; i++) {
            s.mini.bot(r, 1f);
            frame();
        }
        shot("14-shift-result");
        frames(40);
        g.energy = 25;
        frames(30);
        g.onAppPause();
        shot("15-shift-paused-tired");
        check(tapLabel("ממשיכים"), "continue");
        frames(20);
        shot("16-shift-tired-play");
        g.energy = 80;
        g.setVar("xp.0", Job.RANK_XP[1] - 2);
        check(playShift(r, 1f) != null, "shift ends");
        shot("17-shift-done-promoted");
        System.out.printf("  after shift: %s money=%d energy=%.0f food=%.0f water=%.0f%n", Ui.clock(g.minute),
                g.money, g.energy, g.food, g.water);
        check(tapLabel("המשך"), "leave the end card");
        shot("18-promotion-message");
        closeDialogs();
        enter(City.MARKET, 12 * 60);
        tapLabel("ארוחה ארוזה");
        shot("19-market");
        enter(City.BAR, 18 * 60 + 20);
        shot("20-bar-requirement");
        enter(City.BAKERY, 15 * 60);
        shot("21-closed");
        enter(City.SCHOOL, 15 * 60);
        shot("22-coming-soon");
        enter(City.GAS, 15 * 60);
        g.setVar("hired.4", 1);
        g.setVar("xp.4", 130);
        shot("23-gas-top-rank-worked-today");
        enter(City.HOME, 21 * 60);
        g.rentDebt = 250;
        shot("24-home");
        check(tapLabel("חנות"), "the flat leads to the store");
        shot("25-store-from-home");
        tapLabel("חזרה");
        g.rentDebt = 0;
        g.energy = 30;
        g.setVar("hired.1", 1);
        g.setVar("hired.5", 1);
        g.setVar("xp.5", 45);
        check(tapLabel("לישון 8"), "sleep");
        frames(30);
        shot("26-sleep");
        for (int i = 0; i < 400 && g.screen instanceof SleepScreen; i++) frame();
        closeDialogs();
        check(g.screen instanceof SummaryScreen, "a night's sleep ends with the summary");
        shot("27-summary");
        tapLabel("המשך");
        closeDialogs();
        tapLabel("חזרה");
        g.minute = 23 * 60;
        tapBuilding(City.CASINO);
        frames(120);
        shot("28-night-map");
        closeDialogs();
        enter(City.ALLEY, 23 * 60 + 30);
        closeDialogs();
        shot("29-alley");
        check(tapLabel("שולחן ניסיון"), "enter the alley");
        frames(5);
        shot("30-venue");
        g.screen = EventScreen.open(g, "chase", 0.5f);
        shot("31-event-intro");
        tapLabel("קדימה");
        frames(20);
        shot("32-event-play");
        tap(300, lh * 0.6f);
        frames(3);
        shot("33-event-lost");
        tapLabel("המשך");
        closeDialogs();
        g.play(QUESTION);
        shot("34-question-three-answers");
        tapLabel("כן");
        shot("35-reply");
        closeDialogs();
        g.toast("אמא: [תאכל/תאכלי] לאט. האוכל לא בורח. ועכשיו גם ירק.");
        shot("36-long-toast");
        g.end("בעל הבית החליף מנעול. ארזת תיק וחזרת לגור אצל אמא. היא דווקא שמחה.");
        shot("37-end-thrown-out");
        g = new Game();
        startGame("גבר");
        closeDialogs();
        g.day = Story.FINAL_DAY;
        g.setVar("eng.morning", Story.FINAL_DAY - 1);
        frames(3);
        shot("38-finale-message");
        closeDialogs();
        frames(2);
        check(g.screen instanceof EndScreen, "the finale ends on the end screen");
        shot("39-end-year");
        sheets("h" + (int) height);
    }

    // ================= the real Minis of the build =================

    /** Runs the Mini that is on screen with its own bot until stop says so; false when it never does. */
    static boolean botUntil(Mini mini, Random r, float skill, int maxFrames, java.util.function.BooleanSupplier playing,
                            java.util.function.BooleanSupplier stop) {
        for (int i = 0; i < maxFrames; i++) {
            if (stop.getAsBoolean()) return true;
            if (g.hasDialog()) closeDialogs();
            else if (playing.getAsBoolean()) mini.bot(r, skill);
            frame();
        }
        return stop.getAsBoolean();
    }

    /**
     * Every Mini of the build is started and finished by its own bot: from the test list on the
     * title screen (both screen heights, with the app leaving once in the middle), and then inside
     * the real game through the screen that hosts it there.
     */
    static void minis() {
        Random r = new Random(11);
        check(Minis.ORDER.length == 17, "seventeen Minis are in the build");
        for (String id : Minis.ORDER) check(Minis.create(id) != null, "registered: " + id);
        for (float height : new float[]{760, 560}) {
            lh = height;
            for (int k = 0; k < Minis.ORDER.length; k++) {
                String id = Minis.ORDER[k];
                g = new Game();
                g.rnd.setSeed(100 + k);
                frame();
                check(tapLabel("בדיקה") && g.screen instanceof TestMenuScreen, "the title screen opens the test list");
                frame();
                Btn entry = null;
                for (Btn b : g.screen.btns) if (b.id == k) entry = b;
                check(entry != null && entry.y + entry.h <= lh - 58, "the test list shows " + id);
                tap(entry);
                frame();
                check(g.screen instanceof TestPlayScreen, "test list opens " + id);
                TestPlayScreen p = (TestPlayScreen) g.screen;
                String before = g.save();
                check(tapLabel("קדימה") && p.state == TestPlayScreen.PLAY, id + " starts from its intro card");
                // The app leaves in the middle of the first round and comes back to the intro card.
                botUntil(p.mini, r, 0.8f, 40, () -> p.state == TestPlayScreen.PLAY, () -> g.screen != p);
                if (g.screen == p) {
                    g.onAppPause();
                    frame();
                    check(p.state == TestPlayScreen.INTRO && tapLabel("קדימה"), id + " restarts after the app was away");
                }
                if (g.screen != p) {
                    check(p.rounds == 0 && g.screen instanceof TestMenuScreen, id + ": only a venue is left by its bot");
                } else if (p.rounds == 0) {
                    botUntil(p.mini, r, 0.8f, 900, () -> p.state == TestPlayScreen.PLAY, () -> g.screen != p);
                    if (g.screen == p) check(tapLabel("יציאה"), id + ": the test host has its own way out");
                    frame();
                    check(g.screen instanceof TestMenuScreen, id + ": leaving a venue returns to the list");
                } else {
                    check(botUntil(p.mini, r, 0.8f, 30 * 240, () -> p.state == TestPlayScreen.PLAY,
                            () -> p.state == TestPlayScreen.DONE), id + " is played to the end by its bot");
                    check(tapLabel("חזרה לרשימה") && g.screen instanceof TestMenuScreen, id + ": back to the list");
                }
                check(g.save().equals(before) && !g.started, id + ": the test list leaves the save alone");
            }
        }
        lh = 760;
        // In the real game: every job's shift through ShiftScreen...
        for (Job j : Job.ALL) {
            fresh();
            g.rnd.setSeed(200 + j.id);
            g.setVar("hired." + j.id, 1);
            g.setVar("dip." + j.id, 1);
            g.energy = 100;
            enter(j.building, startMinute(j));
            check(tapLabel("להתחיל משמרת: " + j.name(false)), j.mini + ": the shift can be started from its building");
            check(g.screen instanceof ShiftScreen && ((ShiftScreen) g.screen).mini.getClass() == Minis.create(j.mini).getClass(),
                    j.mini + ": the shift is played by its own Mini");
            int money = g.money;
            ShiftScreen s = playShift(r, 0.8f);
            check(s != null && g.money > money, j.mini + ": the shift ends and pays");
            reload();
            check(tapLabel("המשך"), j.mini + ": the end card closes");
            closeDialogs();
            check(g.screen instanceof BuildingScreen, j.mini + ": back in the building");
        }
        // ... the two street events through EventScreen ...
        for (String id : new String[]{"chase", "fight"}) {
            fresh();
            g.rnd.setSeed(300);
            g.screen = EventScreen.open(g, id, 0.4f);
            check(g.screen != null && tapLabel("קדימה"), id + " opens as an event");
            EventScreen e = (EventScreen) g.screen;
            check(botUntil(e.mini, r, 0.8f, 30 * 120, e::playing, () -> e.state == EventScreen.DONE), id + " ends");
            reload();
            check(tapLabel("המשך"), id + ": the outcome card closes");
            closeDialogs();
            check(!(g.screen instanceof EventScreen), id + ": the game goes on");
        }
        // ... and every venue from the menu of its building.
        for (int i = 0; i < Minis.VENUE_MINI.length; i++) {
            String id = Minis.VENUE_MINI[i];
            fresh();
            g.rnd.setSeed(400 + i);
            g.money = 2000;
            City.Building b = City.B[Minis.VENUE_AT[i]];
            enter(b.id, b.openFrom + 30);
            Mini named = Minis.create(id);
            named.attach(g.host);
            check(tapLabel(named.title()) && g.screen instanceof VenueScreen, id + " is entered from " + b.name);
            VenueScreen v = (VenueScreen) g.screen;
            frame();
            botUntil(v.mini, r, 0.8f, 600, () -> true, () -> g.screen != v);
            if (g.screen == v) {
                reload();
                frames(30);
                g.back();
            }
            for (int n = 0; n < 30 * 120 && !(g.screen instanceof BuildingScreen || g.screen instanceof MapScreen); n++) {
                if (g.hasDialog()) closeDialogs();
                else if (g.screen instanceof EventScreen) {
                    EventScreen e = (EventScreen) g.screen;
                    if (e.playing()) e.mini.bot(r, 0.8f);
                    else tapLabel(e.state == EventScreen.INTRO ? "קדימה" : "המשך");
                }
                frame();
            }
            closeDialogs();
            check(g.screen instanceof BuildingScreen || g.screen instanceof MapScreen, id + ": the visit ends back in town");
            check(g.var("gems") == 0, id + " never touches the premium currency");
        }
    }

    /** The school: three passed lessons give the diploma, and the diploma gives the profession. */
    static void school() {
        Random r = new Random(5);
        for (int i = 0; i < School.COUNT; i++) {
            fresh();
            g.rnd.setSeed(500 + i);
            g.money = 1000;
            Job job = Job.byId(School.JOB[i]);
            enter(City.OFFICE, 8 * 60);
            check(tapLabel("לבקש עבודה: " + job.name(false)) && !g.hired(job.id), "no profession without a diploma");
            check(drawn().contains("תעודה"), "the refusal names the diploma");
            closeDialogs();
            int lessons = 0;
            for (int n = 0; n < 12 && !School.graduated(g, i); n++) {
                g.energy = 100;
                g.food = 100;
                g.water = 100;
                enter(City.SCHOOL, 9 * 60);
                int money = g.money, passed = School.passed(g, i);
                float edu = g.stat(Host.EDU), clock = clock();
                check(tapLabel("שיעור: " + School.NAME[i]) && g.screen instanceof EventScreen, "a lesson opens");
                check(g.money == money - School.PRICE, "a lesson is paid for at the door");
                check(tapLabel("קדימה"), "the lesson starts");
                EventScreen e = (EventScreen) g.screen;
                check(botUntil(e.mini, r, 1f, 30 * 120, e::playing, () -> e.state == EventScreen.DONE), "the lesson ends");
                reload();
                boolean won = ((EventScreen) g.screen).won();
                check(tapLabel("המשך"), "the lesson's card closes");
                check(g.stat(Host.EDU) > edu && clock() == clock + School.MINUTES, "a lesson teaches and takes two hours");
                check(School.passed(g, i) == passed + (won ? 1 : 0), "only a passed lesson counts");
                closeDialogs();
                check(g.screen instanceof BuildingScreen, "back in the school");
                lessons++;
            }
            check(School.graduated(g, i) && lessons >= School.LESSONS, "the diploma arrives after " + School.LESSONS + " passed lessons");
            enter(City.SCHOOL, 9 * 60);
            check(!hasButton("שיעור: " + School.NAME[i]) && drawn().contains("יש תעודה"), "a graduate is not sold more lessons");
            enter(City.OFFICE, 8 * 60);
            check(tapLabel("לבקש עבודה: " + job.name(false)) && g.hired(job.id), "the diploma opens the profession");
            closeDialogs();
        }
        fresh();
        g.money = School.PRICE - 1;
        enter(City.SCHOOL, 9 * 60);
        check(!tapLabel("שיעור: ") && g.screen instanceof BuildingScreen, "no lesson without the money for it");
    }

    // ================= checks before any test Mini is registered =================

    static void unregistered() {
        check(Minis.has("cook") && Minis.create("cook") instanceof CookMini, "the cook is registered");
        check(Minis.create("nope") == null && Minis.create(null) == null && !Minis.has("nope"), "unknown Mini ids fail soft");
        for (Job j : Job.ALL) check(Minis.forJob(j) != null, "every job has something to play");
        fresh();
        check(EventScreen.open(g, "chase", 0.5f) == null && VenueScreen.open(g, "alley", City.ALLEY) == null,
                "events and venues without a Mini do not open");
        enter(City.ALLEY, 23 * 60);
        check(drawn().contains("בקרוב") && g.screen.btns.size() == 3, "the alley is still 'coming soon'");
        // Walking out of an alley with no event Minis is just walking out.
        g.setVar("heat", 100);
        Crime.leaveAlley(g);
        check(g.screen instanceof BuildingScreen, "no event Minis, no trouble");
        // A save that names a Mini this build does not have falls back to the street or the building.
        String base = g.save();
        base = base.substring(0, base.indexOf("scr="));
        Game n = new Game();
        n.load(base + "scr=event;ek=chase;es=0;er=1;ed=0.5;");
        check(n.screen instanceof MapScreen, "an event without its Mini falls back to the map");
        n.load(base + "scr=venue;vm=alley;vb=8;vi=100;");
        check(n.screen instanceof BuildingScreen, "a venue without its Mini falls back to the building");
        n.load(base + "scr=shift;jg=77;js=1;");
        check(n.screen instanceof MapScreen, "a shift of an unknown job falls back to the map");
        n.load(base + "scr=whatever;");
        check(n.screen instanceof MapScreen, "an unknown screen falls back to the map");
    }

    // ================= variables, money, sound =================

    static void vars() {
        fresh();
        check(g.var("fit") == 5 && g.var("cha") == 5 && g.var("edu") == 0 && g.stat(Host.FIT) == 5, "stats start at 5 / 5 / 0");
        check(g.var("mom.trust") == 50 && g.var("mom.worry") == 50, "mom starts in the middle");
        check(g.var("nope") == 0 && !g.flag("nope") && !g.hasVar("nope"), "unset is 0");
        g.setVar("alley.q0", 3);
        g.addVar("alley.q0", 2);
        check(g.var("alley.q0") == 5 && g.flag("alley.q0"), "set and add");
        int bad = g.badKeys;
        for (String k : new String[]{"Bad Key", "", "a=b", "עברית", "a;b", "a|b", "UPPER"}) g.setVar(k, 1);
        g.setVar(null, 1);
        check(g.badKeys == bad + 8 && g.var("Bad Key") == 0 && g.var(null) == 0, "malformed keys are refused");
        g.badKeys = bad;
        check(g.var("day") == g.day && g.var("minute") == g.minute, "day and minute read the clock");
        g.setVar("day", 99);
        g.setVar("minute", 5);
        check(g.var("day") == g.day && g.var("minute") == g.minute && !g.hasVar("day"), "and cannot be written");
        g.setVar("fit", 250);
        g.setVar("heat", -5);
        g.setVar("gems", -3);
        g.setVar("mom.trust", 130);
        check(g.var("fit") == 100 && g.var("heat") == 0 && g.var("gems") == 0 && g.var("mom.trust") == 100, "ranges are clamped");
        g.setVar("x.nan", Float.NaN);
        g.setVar("x.inf", Float.POSITIVE_INFINITY);
        check(!g.hasVar("x.nan") && !g.hasVar("x.inf"), "no NaN, no infinity");
        g.setVar("fit", 5);
        g.train(Host.FIT, 2.5f);
        g.train(7, 3);
        check(g.stat(Host.FIT) == 7.5f && g.stat(7) == 0, "train and stat");
        g.setVar("xp.2", Job.RANK_XP[1]);
        g.setVar("xp.3", Job.RANK_XP[2] + 500);
        g.setVar("hired.2", 1);
        check(g.rank(2) == 1 && g.rank(3) == 2 && g.rank(0) == 0 && g.hired(2) && !g.hired(3), "rank and hired");
        g.host.setVar("casino.x", 7);
        check(g.host.var("casino.x") == 7 && g.host.var("day") == g.day && g.host.stat(Host.FIT) == 7.5f, "Minis see the same variables");
        check(g.varKeys("alley.").equals(List.of("alley.q0")), "keys by prefix");

        // Everything survives save and load, an explicit zero included.
        g.setVar("opt.sfx", 0);
        g.setVar("big.number", 1.5e9f);
        g.setVar("small.number", -0.000123f);
        String s = g.save();
        Game n = new Game();
        n.load(s);
        check(n.save().equals(s), "variables round-trip");
        check(n.var("alley.q0") == 5 && n.var("casino.x") == 7 && n.var("big.number") == 1.5e9f
                && n.var("small.number") == -0.000123f && n.hasVar("opt.sfx") && !n.optOn("opt.sfx") && n.optOn("opt.music"),
                "values come back, and an option that was never set is on");
        Facts f = n.facts();
        check(f.var("alley.q0") == 5 && f.fit == 7.5f, "facts carry the variables");
        f.vars.put("alley.q0", 99f);
        check(n.var("alley.q0") == 5, "facts.vars is a copy");

        // A new game keeps what belongs to the player and starts everything else over.
        g.setVar("gems", 12);
        g.setVar("own.hat", 1);
        g.setVar("look.hair", 2);
        g.setVar("store.tok.t1", 1);
        g.setVar("heat", 40);
        g.setVar("xp.0", 50);
        g.newGame(true, "דנה");
        check(g.var("gems") == 12 && g.var("own.hat") == 1 && g.var("look.hair") == 2 && g.var("store.tok.t1") == 1
                && !g.optOn("opt.sfx"), "a new game keeps gems, purchases, looks and options");
        check(g.var("heat") == 0 && g.var("xp.0") == 0 && g.var("fit") == 5 && !g.hasVar("alley.q0") && !g.hasVar("casino.x"),
                "and resets the playthrough");
        check(g.badKeys == 0, "no malformed keys in the engine itself");
    }

    static void money() {
        fresh();
        int m0 = g.money;
        g.earn(120);
        g.spend(50);
        g.earn(0);
        g.earn(-5);
        g.spend(-5);
        check(g.money == m0 + 70 && g.var("day.earned") == 120 && g.var("day.spent") == 50, "earn and spend keep the day's books");
        check(!g.buy(g.money + 1) && g.money == m0 + 70 && g.buy(10) && g.money == m0 + 60 && !g.buy(-1), "buy refuses what you can't afford");
        check(g.host.pay(30) && g.money == m0 + 90 && g.var("day.earned") == 150, "a Mini's winnings are earned");
        check(!g.host.pay(-(g.money + 1)) && !g.host.pay(Integer.MIN_VALUE) && g.money == m0 + 90, "a Mini cannot overdraw");
        check(g.host.pay(-90) && g.var("day.spent") == 150 && g.host.money() == m0, "a Mini's bet is spent");
        // Shopping goes through the same door.
        enter(City.MARKET, 12 * 60);
        float spent = g.var("day.spent");
        check(tapLabel("ארוחה ארוזה") && g.var("day.spent") == spent + Game.MEAL_PRICE, "a purchase lands in the books");
        check(g.facts().money == g.money, "facts.money");
    }

    static void sound() {
        g = new Game();
        check(g.pollSound() == -1 && g.musicTrack() == -1, "silence on the title screen");
        heard.clear();
        startGame("אישה");
        check(heard.get(Sfx.TAP), "taps tick");
        check(heard.get(Sfx.PHONE), "mom's first message rings");
        closeDialogs();
        check(g.musicTrack() == 0, "day music on the map at nine in the morning");
        frame();
        check(g.pollSound() == -1, "a frame empties the queue");
        g.sfx(Sfx.COIN);
        g.sfx(Sfx.DING);
        check(g.pollSound() == Sfx.COIN && g.pollSound() == Sfx.DING && g.pollSound() == -1, "sounds come out in order");
        for (int i = 0; i < 40; i++) g.sfx(Sfx.TAP);
        int n = 0;
        while (g.pollSound() >= 0) n++;
        check(n == 16, "the queue holds 16, got " + n);
        g.sfx(-1);
        g.sfx(Sfx.COUNT);
        check(g.pollSound() == -1, "unknown ids are dropped");
        g.setVar("opt.sfx", 0);
        g.sfx(Sfx.TAP);
        check(g.pollSound() == -1, "nothing is queued while sounds are off");
        g.setVar("opt.sfx", 1);
        g.sfx(Sfx.TAP);
        check(g.pollSound() == Sfx.TAP, "and they come back on");

        // The sounds of ordinary life.
        heard.clear();
        g.food = 20;
        check(tapLabel("לאכול ("), "eat");
        g.water = 20;
        check(tapLabel("לשתות ("), "drink");
        frame();
        check(heard.get(Sfx.EAT) && heard.get(Sfx.POUR), "eating and drinking");
        heard.clear();
        check(goTo(City.MARKET), "walk to the market");
        check(heard.get(Sfx.DOOR), "arriving sounds like a door");
        heard.clear();
        g.minute = 12 * 60;
        tapLabel("בקבוק מים");
        frame();
        check(heard.get(Sfx.COIN), "buying");
        enter(City.RESTAURANT, 12 * 60);
        heard.clear();
        tapLabel("לבקש עבודה: טבחית");
        frame();
        check(heard.get(Sfx.HIRED), "hired");
        closeDialogs();
        tapLabel("חזרה");
        heard.clear();
        g.day = 10;
        g.setVar("eng.morning", 10);
        g.minute = 1439;
        g.money = 2000;
        frames(10);
        check(heard.get(Sfx.RENT) && g.hasDialog() && g.money == 2000 - Game.RENT, "rent day");
        closeDialogs();
        heard.clear();
        g.energy = 0.2f;
        frames(40);
        check(heard.get(Sfx.COLLAPSE) && g.var("collapses") == 1, "collapse");
        closeDialogs();

        // Music follows the clock, the switch and the screen.
        g.minute = 13 * 60;
        check(g.musicTrack() == 0, "day track");
        g.minute = 22 * 60;
        check(g.musicTrack() == 1, "night track");
        g.minute = 3 * 60;
        check(g.musicTrack() == 1, "night track before dawn");
        g.setVar("opt.music", 0);
        check(g.musicTrack() == -1, "music off");
        g.setVar("opt.music", 1);
        g.back();
        check(g.screen instanceof TitleScreen && g.musicTrack() == -1, "no music on the title screen");
        tapLabel("המשך");
        g.end("בדיקה");
        check(g.musicTrack() == -1, "no music on the end screen");
    }

    // ================= beats =================

    static void beats() {
        fresh();
        int money = g.money;
        g.play(QUESTION);
        check(g.hasDialog() && !g.flag("seen.sim.uncle"), "a beat waits in the queue");
        frame();
        check(g.flag("seen.sim.uncle") && g.facts().seen("sim.uncle"), "it is marked seen when it appears");
        check(g.dialogBtns.size() == 3 && g.dialogBtns.get(0).label.equals("כן, קצת כסף")
                && g.dialogBtns.get(2).label.equals("רק משהו לאכול"), "three answers");
        String text = drawn();
        check(text.contains("דוד משה") && text.contains("גיל, שמעתי מאמא שלך שאתה בעיר") && !text.contains("{name}"),
                "speaker as title, the masculine text, the name filled in");
        float minute = g.minute;
        frames(60);
        check(g.minute == minute, "the world waits while a message is up");
        check(g.back() && g.hasDialog() && g.money == money, "a question cannot be skipped with back");
        check(tapLabel("כן, קצת כסף"), "pick the first answer");
        check(g.money == money + 50 && g.var("day.earned") == 50 && g.var("lie.money") == 1 && g.var("mom.trust") == 40,
                "its effects are applied, money through the books");
        check(g.hasDialog() && g.dialogBtns.isEmpty(), "stale buttons are gone until the reply is drawn");
        frame();
        text = drawn();
        check(text.contains("דוד משה") && text.contains("קח, גיל. ותתקשר לאמא.") && g.dialogBtns.size() == 1,
                "the reply follows from the same speaker");
        check(g.back() && !g.hasDialog(), "a plain message closes with back");

        g.play(QUESTION);
        check(tapLabel("הכל בסדר") && !g.hasDialog() && g.var("mom.worry") == 55, "an answer without a reply just closes");
        g.meals = 3;
        g.food = 10;
        g.play(QUESTION);
        check(tapLabel("רק משהו") && g.meals == Game.BAG_MAX && g.food == 90, "meals and food are special keys, clamped");
        closeDialogs();

        // A woman hears the feminine wording.
        g = new Game();
        startGame("אישה");
        closeDialogs();
        g.play(QUESTION);
        frame();
        check(drawn().contains("שאת בעיר"), "the feminine text");
        tapLabel("כן");
        frame();
        check(drawn().contains("קחי, גיל. ותתקשרי לאמא."), "the feminine reply");
        closeDialogs();

        // A beat without answers applies its effect when it appears; every special key and every operator.
        g.money = 500;
        g.energy = 50;
        g.water = 50;
        g.bottles = 1;
        g.play(Beat.say("sim.gift", "דודה", "הבאתי לך משהו.").effect(
                "money-=120; energy+=30 ;water=10;bottles+=9;gems+=5;gems-=2;story.x=3;story.x+=1.5;heat=250"));
        float spent = g.var("day.spent");
        frame();
        check(g.money == 380 && g.var("day.spent") == spent + 120 && g.energy == 80 && g.water == 10
                && g.bottles == Game.BAG_MAX && g.var("gems") == 3 && g.var("story.x") == 4.5f && g.var("heat") == 100,
                "effects grammar: =, +=, -=, special keys, ranges");
        frames(5);
        check(g.money == 380, "the effect is applied once");
        closeDialogs();
        g.play(Beat.say("sim.set", "דודה", "ועוד משהו.").effect("money=1000"));
        frame();
        check(g.money == 1000 && g.var("day.earned") >= 620, "money=n goes through the books too");
        closeDialogs();

        // Broken effects are skipped quietly, and the good parts still count.
        int bad = g.badKeys;
        g.play(Beat.say("sim.broken", "דודה", "אופס.").effect("bogus;x=;=3;money+=abc;cha+=NaN;;fit=7;a.b-=--1"));
        frame();
        check(g.var("fit") == 7 && g.money == 1000, "malformed effects do not crash");
        g.badKeys = bad;
        closeDialogs();

        // Odd beats: no id, one answer, four answers, missing arrays.
        g.play(new Beat(null, null, null, null, new String[]{"טוב"}, null, null, null));
        check(tapLabel("טוב") && !g.hasDialog(), "a beat of nulls is harmless");
        g.play(new Beat("sim.four", "מישהו", "כמה?", "כמה?", new String[]{"1", "2", "3", "4"}, new String[]{"א"},
                new String[]{"א"}, new String[]{"story.y=1"}));
        frame();
        check(g.dialogBtns.size() == 3, "at most three answers are offered");
        tapLabel("3");
        check(!g.hasDialog() && g.var("story.y") == 0, "short reply and effect arrays are fine");
        g.play((Beat) null);
        g.play((List<Beat>) null);
        check(!g.hasDialog(), "null beats are ignored");

        // The old plain message still works, and the queue keeps its order.
        g.dialog("ראשון", "אחד");
        g.play(Beat.say("sim.second", "שני", "שתיים"));
        g.dialog("שלישי", "שלוש");
        frame();
        check(drawn().contains("ראשון") && !g.flag("seen.sim.second"), "first in, first shown");
        tapLabel("המשך");
        frame();
        check(drawn().contains("שתיים") && g.flag("seen.sim.second"), "then the second");
        closeDialogs();

        // A player without a name (a save from before names) loses the placeholder together with its comma.
        g.name = "";
        check(g.fill("נו, {name}, הגעת?").equals("נו, הגעת?") && g.fill("{name}, אכלת?").equals("אכלת?")
                && g.fill("{name}! בוא.").equals("בוא.") && g.fill(null).isEmpty(), "{name} without a name");
        g.name = "גיל";

        // Three answers under a long text fit the smallest screen, and the tallest card still fits.
        for (float hgt : new float[]{560, 760}) {
            lh = hgt;
            g.play(QUESTION);
            frames(2);
            float bottom = 0, top = lh;
            for (Btn b : g.dialogBtns) {
                bottom = Math.max(bottom, b.y + b.h);
                top = Math.min(top, b.y);
                check(b.h >= 44 && b.w >= 260, "answers are comfortable touch targets");
            }
            check(g.dialogBtns.size() == 3 && bottom <= lh - 20 && top > 120, "three answers fit a " + (int) hgt + " screen");
            closeDialogs();
            StringBuilder longText = new StringBuilder();
            for (int i = 0; i < 16; i++) longText.append("משפט ארוך במיוחד שנועד לבדוק מה קורה כשכותבים יותר מדי. ");
            g.play(new Beat("sim.long", "פטפטן", longText.toString(), longText.toString(), new String[]{"א", "ב", "ג"}, null, null, null));
            frames(2);
            for (Btn b : g.dialogBtns) check(b.y + b.h <= lh - 8 && b.y > 60, "even a far too long text keeps its answers on screen");
            closeDialogs();
        }
        lh = 760;
        check(g.badKeys == 0, "no malformed keys");
    }

    static void pendingBeat() {
        fresh();
        int money = g.money;
        g.dialog("קודם", "הודעה רגילה.");
        g.play(QUESTION);
        g.play(Beat.say("sim.after", "אחר כך", "עוד אחת.").effect("money+=7"));
        reopen();
        check(g.hasDialog() && !g.flag("seen.sim.uncle"), "a queue that was never shown survives a reload");
        closeFirst();
        frame();
        check(g.flag("seen.sim.uncle") && g.dialogBtns.size() == 3, "the question is up");
        reload();
        check(g.dialogBtns.size() == 3 && g.dialogBtns.get(1).label.equals("הכל בסדר, דוד") && g.money == money,
                "the question, its answers and its effects survive a reload");
        check(tapLabel("כן"), "answer after the reload");
        check(g.money == money + 50 && g.var("mom.trust") == 40, "the effects were waiting");
        reload();
        check(drawn().contains("קח, גיל. ותתקשר לאמא.") && g.money == money + 50, "the reply survives a reload, nothing is paid twice");
        closeFirst();
        frame();
        check(g.money == money + 57 && g.flag("seen.sim.after"), "the next beat's own effect lands when it appears");
        reload();
        frames(3);
        check(g.hasDialog() && g.money == money + 57, "and not again after a reload");
        closeDialogs();
        reload();
        check(!g.hasDialog(), "an empty queue stays empty");
    }

    /** Closes exactly one message. */
    static void closeFirst() {
        frame();
        check(!g.dialogBtns.isEmpty(), "a message is up");
        tap(g.dialogBtns.get(0));
    }

    // ================= jobs =================

    static int startMinute(Job j) {
        return j.from == j.to ? 9 * 60 : j.from + 20;
    }

    /** Every job from asking for it to the end of its first shift, played by its Mini's own bot. */
    static void jobs() {
        Random r = new Random(4);
        for (Job j : Job.ALL) {
            fresh();
            String name = j.name(false);
            if (j.needStat != Job.NONE) g.train(j.needStat, j.needValue - g.stat(j.needStat));
            if (j.profession) g.setVar("dip." + j.id, 1);
            enter(j.building, startMinute(j));
            g.energy = 95;
            check(City.B[j.building].isOpen(g.minute), name + ": the building is open when the shift starts");
            check(tapLabel("לבקש עבודה: " + name) && g.hired(j.id), name + ": hired");
            closeDialogs();
            int money = g.money, day = g.day;
            float earned = g.var("day.earned"), t0 = clock(), food = g.food, water = g.water, cha = g.stat(Host.CHA);
            float stat = j.trains == Job.NONE ? 0 : g.stat(j.trains);
            check(tapLabel("להתחיל משמרת: " + name) && g.screen instanceof ShiftScreen, name + ": the shift opens");
            check(g.lastShiftDay != g.day, name + ": the intro card alone does not use up the day");
            ShiftScreen s = playShift(r, 0.85f);
            check(s != null && s.state == ShiftScreen.DONE && !s.sentHome, name + ": the shift runs to its end card");
            check(Math.abs(clock() - t0 - 480) < 0.5f, name + ": eight hours passed, got " + (clock() - t0));
            int[] exp = expectedPay(s, cha);
            check(s.pay == exp[0] && s.tips == exp[1], name + ": pay " + s.pay + "+" + s.tips + " matches the formula " + exp[0] + "+" + exp[1]);
            check(g.money - money == s.pay + s.tips && g.var("day.earned") - earned == s.pay + s.tips && s.pay > 0,
                    name + ": the wages reached the wallet and the books");
            check(g.xp(j.id) == s.xpGain && s.xpGain > 0, name + ": experience");
            if (j.trains != Job.NONE) check(g.stat(j.trains) > stat && Math.abs(g.stat(j.trains) - stat - s.trained) < 0.001f, name + ": the shift trains " + Job.STAT_NAMES[j.trains]);
            check(g.shiftsDone == 1 && g.lastShiftDay == day, name + ": counted as that day's shift");
            check(g.food > food && g.water > water, name + ": the employer feeds you on shift");
            check(!s.endLine.isEmpty() && !s.startLine.isEmpty(), name + ": the workplace has something to say");
            System.out.printf("  %-8s %d rounds  results %s  pay %3d + tips %3d  xp +%d  %s%n", name, s.rounds,
                    Arrays.toString(s.results), s.pay, s.tips, s.xpGain,
                    j.trains == Job.NONE ? "" : Job.STAT_NAMES[j.trains] + " +" + s.trained);
            reload();
            check(tapLabel("המשך") && g.screen instanceof BuildingScreen && g.at == j.building, name + ": back at work's door");
            closeDialogs();
            g.minute = startMinute(j);
            g.energy = 95;
            if (g.lastShiftDay == g.day) check(!tapLabel("להתחיל משמרת: " + name), name + ": one shift a day");
        }
        check(g.badKeys == 0, "no malformed keys");
    }

    /** The pay formula, the clock and the difficulty, against a work Mini whose results are scripted. */
    static void wages() {
        fresh();
        Job j = Job.ALL[Job.WAITER];
        g.setVar("cha", 30);
        enter(j.building, 10 * 60);
        tapLabel("לבקש עבודה: מלצר");
        closeDialogs();
        g.energy = 100;
        TestWork.script = new int[]{2, 2, 2, 1, 0, 2, 2, 1};
        int money = g.money;
        check(tapLabel("להתחיל משמרת: מלצר"), "shift opens");
        ShiftScreen s = (ShiftScreen) g.screen;
        check(s.rounds == 8 && s.mini instanceof TestWork, "the registered Mini plays the job");
        tapLabel("מתחילים");
        float minute = g.minute;
        int round = 0;
        for (int i = 0; i < 30 * 60 && s.state != ShiftScreen.DONE; i++) {
            frame();
            if (s.round != round) {
                check(Math.abs(g.minute - minute - 60) < 0.01f, "each of eight rounds is one hour");
                minute = g.minute;
                round = s.round;
            }
        }
        // The job's hourly pay, rank 0, charisma 30: tips are 8% per perfect in a row plus 30/150.
        int[] base = new int[8], tip = new int[8];
        for (int i = 0, streak = 0; i < 8; i++) {
            int res = TestWork.script[i];
            streak = res == 2 ? streak + 1 : 0;
            base[i] = Math.round(j.pay * j.resultPay[res]);
            tip[i] = res == 0 ? 0 : Math.round(j.pay * (0.08f * streak + 0.2f));
        }
        int pay = 0, tips = 0;
        for (int i = 0; i < 8; i++) {
            pay += base[i];
            tips += tip[i];
        }
        check(s.pay == pay && s.tips == tips && g.money == money + pay + tips, "pay " + s.pay + " tips " + s.tips + ", expected " + pay + " and " + tips);
        for (int i = 0; i < 8; i++) {
            check(Math.abs(TestWork.difficulties[i] - i / 7f * 0.6f) < 1e-5f, "difficulty rises with progress: round " + i + " got " + TestWork.difficulties[i]);
        }
        check(s.xpGain == Math.round(12 / 16f * 16) && Math.abs(s.trained - (1 + 2 * 12 / 16f)) < 1e-4f, "experience and training follow the quality");
        tapLabel("המשך");
        closeDialogs();

        // Rank and tiredness raise the difficulty; the sum is clamped at 1.
        g.setVar("xp.1", Job.RANK_XP[2]);
        g.lastShiftDay = 0;
        g.minute = 10 * 60;
        g.energy = 100;
        TestWork.script = new int[]{1, 1, 1, 1, 1, 1, 1, 1};
        check(tapLabel("להתחיל משמרת: מלצר"), "second shift");
        s = (ShiftScreen) g.screen;
        check(s.rank == 2, "rank 2");
        tapLabel("מתחילים");
        for (int i = 0; i < 30 * 60 && s.state != ShiftScreen.DONE; i++) {
            if (s.round == 4 && s.state == ShiftScreen.SHOW) g.energy = 29;
            frame();
        }
        check(Math.abs(TestWork.difficulties[0] - 0.3f) < 1e-5f, "rank adds 0.15 a step");
        check(Math.abs(TestWork.difficulties[5] - (5 / 7f * 0.6f + 0.3f + 0.15f)) < 1e-5f, "tiredness adds 0.15, got " + TestWork.difficulties[5]);
        check(TestWork.difficulties[7] == 1f, "clamped at 1");
        check(s.pay == 8 * Math.round(j.pay * Job.RANK_PAY[2]), "rank 2 pays its multiple of the base");
        TestWork.script = null;
    }

    static void rankUp() {
        fresh();
        enter(City.RESTAURANT, 9 * 60);
        tapLabel("לבקש עבודה: טבח");
        closeDialogs();
        g.energy = 95;
        g.setVar("xp.0", Job.RANK_XP[1] - 3);
        check(tapLabel("להתחיל משמרת: טבח"), "shift opens");
        ShiftScreen s = playShift(new Random(8), 1f);
        check(s != null && s.rank == 0 && s.rankedUp && g.rank(0) == 1, "enough experience promotes at the end of the shift");
        check(drawn().contains("קידום! מעכשיו מקצוען"), "the end card says so");
        heard.clear();
        reload();
        check(((ShiftScreen) g.screen).rankedUp, "the promotion survives a reload of the end card");
        check(tapLabel("המשך") && g.hasDialog(), "the boss announces it on the way out");
        frame();
        check(drawn().contains("מקצוען") && heard.get(Sfx.LEVEL), "promotion beat and its fanfare");
        closeDialogs();
        g.lastShiftDay = 0;
        g.minute = 9 * 60;
        g.energy = 95;
        frame();
        check(drawn().contains("מקצוען · " + Ui.nis(Math.round(Job.ALL[Job.COOK].pay * Job.RANK_PAY[1])) + " לשעה"), "the menu shows the new rank and rate: " + drawn());
        check(tapLabel("להתחיל משמרת: טבח"), "next shift");
        s = playShift(new Random(9), 1f);
        int[] exp = expectedPay(s, g.stat(Host.CHA));
        check(s.rank == 1 && s.pay == exp[0] && !s.rankedUp, "the next shift is paid at the new rank");
    }

    /** Walking trains fitness, and fitness makes the walk faster. */
    static void stats() {
        fresh();
        g.rnd.setSeed(3);
        float t0 = clock(), fit = g.stat(Host.FIT);
        check(goTo(City.RESTAURANT), "walk");
        float slow = clock() - t0, gained = g.stat(Host.FIT) - fit;
        check(Math.abs(gained - slow / 60f * 0.25f) < 0.02f, "fitness +0.25 per hour on foot: " + gained + " in " + slow + " minutes");
        // 400 city units at 3 a minute, a little faster for fitness 5 and rising.
        check(Math.abs(slow - 400 / (3 * 1.026f)) < 3, "the walk takes about 130 minutes, got " + slow);
        fresh();
        g.rnd.setSeed(3);
        g.setVar("fit", 100);
        t0 = clock();
        check(goTo(City.RESTAURANT), "walk again");
        float fast = clock() - t0;
        check(Math.abs(fast - 400 / 4.5f) < 2, "at fitness 100 the same walk takes two thirds: " + fast + " vs " + slow);
        check(g.stat(Host.FIT) == 100 && g.walkSpeed() == 4.5f, "fitness stops at 100");
        // Standing still trains nothing.
        fresh();
        frames(300);
        check(g.stat(Host.FIT) == 5, "no training while standing");
        System.out.printf("  walk home->restaurant: %.0f min at fitness 5 (+%.2f fitness), %.0f min at fitness 100%n", slow, gained, fast);
    }

    static void refusal() {
        fresh();
        enter(City.RESTAURANT, 11 * 60);
        int need = Job.ALL[Job.WAITER].needValue;
        String menu = drawn();
        check(menu.contains("דרוש: כריזמה " + need + ", יש לך 5"), "the requirement is on the menu before asking: " + menu);
        check(tapLabel("לבקש עבודה: מלצר"), "the request can be made");
        check(g.hasDialog() && !g.hired(Job.WAITER), "not hired");
        frame();
        String why = drawn();
        check(why.contains("צריך כריזמה " + need + " לפחות, ולך יש 5") && why.contains("כריזמה עולה"), "a clear explanation: " + why);
        check(heard.get(Sfx.BAD), "and a sound to match");
        closeDialogs();
        g.setVar("cha", need - 0.1f);
        tapLabel("לבקש עבודה: מלצר");
        check(!g.hired(Job.WAITER), "just under is not enough");
        closeDialogs();
        g.setVar("cha", need);
        tapLabel("לבקש עבודה: מלצר");
        check(g.hired(Job.WAITER), "hired once the stat is there");
        closeDialogs();

        // The three reasons a shift can't start.
        g.energy = 95;
        g.minute = 17 * 60;
        check(!tapLabel("להתחיל משמרת: מלצר") && drawn().contains("משמרת אפשר להתחיל בין 10:00 ל-16:00."), "outside the window");
        g.minute = 9 * 60 + 59;
        check(!tapLabel("להתחיל משמרת: מלצר"), "a minute too early");
        g.minute = 16 * 60;
        g.energy = 54.9f;
        check(!tapLabel("להתחיל משמרת: מלצר") && drawn().contains("אין לך כוח למשמרת שלמה"), "energy below 55");
        g.energy = 55;
        g.lastShiftDay = g.day;
        check(!tapLabel("להתחיל משמרת: מלצר") && drawn().contains("כבר עבדת היום"), "already worked today");
        g.lastShiftDay = 0;
        check(tapLabel("להתחיל משמרת: מלצר") && g.screen instanceof ShiftScreen, "and then it starts");
        // A stale tap on a start button can never start a shift that is not allowed.
        g.back();
        g.energy = 10;
        Btn start = null;
        for (Btn b : g.screen.btns) if (b.label.startsWith("להתחיל משמרת: מלצר")) start = b;
        if (start != null) tap(start);
        check(g.screen instanceof BuildingScreen, "a tap on a stale button is refused");
        // A closed building offers no work.
        g.energy = 95;
        enter(City.BAKERY, 15 * 60);
        check(!hasButton("לבקש עבודה") && drawn().contains("סגור עכשיו"), "nobody hires at a closed door");
    }

    /** Cases found in review; each one used to go wrong. */
    static void regressions() {
        // Leaving the intro (or saving on it) doesn't burn today's shift.
        fresh();
        enter(City.RESTAURANT, 9 * 60);
        tapLabel("לבקש עבודה: טבח");
        closeDialogs();
        g.energy = 90;
        check(tapLabel("להתחיל משמרת: טבח"), "reg: shift starts");
        check(g.screen instanceof ShiftScreen && g.lastShiftDay != g.day, "reg: intro doesn't count as a shift");
        g.back();
        check(g.screen instanceof BuildingScreen, "reg: back leaves the intro");

        // Running out of strength mid-shift sends you home; no collapse, no ambulance bill.
        check(tapLabel("להתחיל משמרת: טבח"), "reg: shift starts again");
        tapLabel("מתחילים");
        int before = g.money, day = g.day;
        g.energy = 13;
        ShiftScreen s = playShift(new Random(5), 0.8f);
        check(s != null && s.sentHome && !g.hasDialog() && g.day == day && g.money >= before && g.energy > 0
                && g.var("collapses") == 0, "reg: sent home instead of collapsing");
        check(drawn().contains("שלחו אותך הביתה") && s.round < s.rounds - 1, "reg: the end card says why the shift was short");
        check(g.shiftsDone == 1 && s.xpGain <= 8, "reg: a short shift counts, for less");
        g.back();
        check(g.screen == s, "reg: back cannot skip the end card");
        tapLabel("המשך");
        check(g.screen instanceof BuildingScreen, "reg: back in the restaurant");

        // Several taps between two frames can't overfill the bag or overdraw the wallet.
        fresh();
        enter(City.MARKET, 12 * 60);
        g.money = 70;
        g.meals = Game.BAG_MAX - 1;
        frame();
        Btn buy = null;
        for (Btn b : g.screen.btns) if (b.label.startsWith("ארוחה ארוזה")) buy = b;
        check(buy != null, "reg: buy button exists");
        for (int i = 0; i < 5; i++) g.down(buy.x + 5, buy.y + 5);
        check(g.meals == Game.BAG_MAX && g.money == 70 - Game.MEAL_PRICE, "reg: multi-tap buy is clamped");

        // Game over wipes any queued message, and nothing can be queued after it.
        fresh();
        g.dialog("x", "y");
        g.play(QUESTION);
        g.end("בדיקה");
        g.play(QUESTION);
        check(!g.hasDialog() && g.screen instanceof EndScreen, "reg: no message over the end screen");

        // A finger that went down on a message button does not leak its release into the screen below.
        fresh();
        g.screen = VenueScreen.open(g, "sim.venue", City.MARKET);
        frames(2);
        g.dialog("רגע", "משהו");
        frame();
        int moves = TestVenue.moves;
        Btn ok = g.dialogBtns.get(0);
        g.down(ok.x + 5, ok.y + 5);
        g.move(100, 300);
        g.up(100, 300);
        check(!g.hasDialog() && TestVenue.moves == moves, "reg: a Mini never sees a drag it did not get the start of");
    }

    // ================= the day =================

    static void summary() {
        fresh();
        enter(City.HOME, 22 * 60);
        g.energy = 20;
        g.earn(240);
        g.spend(38);
        g.train(Host.FIT, 0.5f);
        g.setVar("hired.0", 1);
        g.setVar("xp.0", 50);
        check(tapLabel("לישון 8"), "go to sleep");
        check(drawn().contains("מים מהברז"), "a tip to read while asleep");
        for (int i = 0; i < 600 && g.screen instanceof SleepScreen; i++) {
            frame();
            if (g.hasDialog()) closeDialogs();
        }
        check(g.screen instanceof SummaryScreen, "eight hours of sleep end with the summary");
        check(g.var("day.earned") == 0 && g.var("day.spent") == 0 && g.var("yday.earned") == 240 && g.var("yday.spent") == 38,
                "the day's counters are reset and remembered as yesterday's");
        Facts f = g.facts();
        check(f.earnedYesterday == 240 && f.spentYesterday == 38, "facts know yesterday's money");
        String text = drawn();
        check(text.contains("+₪240") && text.contains("-₪38") && text.contains("+₪202"), "earned, spent, net: " + text);
        check(text.contains("שכירות ₪900 בעוד 9 ימים"), "rent countdown");
        check(text.contains("5.5") && text.contains("+0.5"), "stat change since the last summary");
        check(text.contains("טבח · מקצוען"), "job and rank");
        check(text.contains("אמא: יום של עבודה זה יום טוב."), "mom's line");
        float minute = g.minute;
        frames(200);
        check(g.minute == minute, "time stands still on the summary");
        reload();
        check(drawn().equals(text), "the summary survives a reload word for word");
        check(tapLabel("המשך") && g.screen instanceof BuildingScreen && g.at == City.HOME, "and leads back into the flat");

        // A nap is not a night: no summary, the counters keep running.
        g.earn(30);
        g.energy = 50;
        check(tapLabel("תנומה"), "nap");
        for (int i = 0; i < 300 && g.screen instanceof SleepScreen; i++) frame();
        check(g.screen instanceof BuildingScreen && g.var("day.earned") == 30, "a nap does not close the day");
        // The next summary covers only what happened since the last one.
        g.spend(100);
        g.energy = 20;
        g.minute = 23 * 60;
        tapLabel("לישון 8");
        for (int i = 0; i < 600 && g.screen instanceof SleepScreen; i++) {
            frame();
            if (g.hasDialog()) closeDialogs();
        }
        text = drawn();
        check(g.screen instanceof SummaryScreen && text.contains("+₪30") && text.contains("-₪100") && text.contains("-₪70")
                && !text.contains("+0.5"), "the second summary starts where the first one ended: " + text);
        check(text.contains("אמא: יצא יותר ממה שנכנס"), "and mom noticed");
        g.back();
        check(g.screen instanceof BuildingScreen, "back leaves the summary too");
    }

    /** Mom's weekly call and the day's beats arrive in the morning, once the player is up and about. */
    static void mornings() {
        fresh();
        enter(City.HOME, 23 * 60);
        g.day = 6;
        g.setVar("eng.morning", 6);
        g.energy = 20;
        heard.clear();
        tapLabel("לישון 8");
        for (int i = 0; i < 600 && g.screen instanceof SleepScreen; i++) {
            frame();
            check(!g.hasDialog(), "nobody phones in the middle of the night");
        }
        check(g.day == 7 && g.screen instanceof SummaryScreen, "woke up on day seven");
        frames(30);
        check(!g.hasDialog(), "the summary comes first");
        tapLabel("המשך");
        frames(2);
        check(g.hasDialog() && heard.get(Sfx.PHONE) && drawn().contains("אמא על הקו"), "then mom calls");
        check(g.var("eng.week") == 1 && g.var("eng.morning") == 7, "once");
        closeDialogs();
        frames(60);
        check(!g.hasDialog(), "and only once that day");
        // The same morning on the street: the call comes at six.
        fresh();
        g.day = 14;
        g.minute = 5 * 60 + 40;
        g.setVar("eng.morning", 13);
        g.setVar("eng.week", 1);
        frames(20);
        check(!g.hasDialog() && g.minute < 6 * 60, "not before six");
        frames(30);
        check(g.hasDialog() && g.var("eng.week") == 2, "at six, wherever the player is");
        reload();
        check(g.hasDialog(), "the call survives a reload");
        closeDialogs();
        // A shift is never interrupted: the morning waits for its end.
        fresh();
        g.setVar("fit", 20);
        enter(City.BAKERY, 5 * 60 + 10);
        g.day = 21;
        g.setVar("eng.morning", 20);
        g.setVar("eng.week", 2);
        g.money = 5000;
        tapLabel("לבקש עבודה: אופה");
        closeDialogs();
        g.energy = 95;
        check(tapLabel("להתחיל משמרת: אופה"), "an early shift");
        ShiftScreen s = (ShiftScreen) g.screen;
        tapLabel("מתחילים");
        Random r = new Random(2);
        for (int i = 0; i < 30 * 600 && s.state != ShiftScreen.DONE; i++) {
            if (s.playing()) s.mini.bot(r, 0.8f);
            frame();
            check(!g.hasDialog(), "no phone call during the shift");
        }
        tapLabel("המשך");
        frames(2);
        check(g.hasDialog() && g.var("eng.week") == 3, "the call was waiting at the door");
        closeDialogs();
    }

    static void finale() {
        fresh();
        g.day = Story.FINAL_DAY - 1;
        g.setVar("eng.morning", g.day);
        g.setVar("eng.week", 99);
        g.minute = 23 * 60 + 50;
        g.money = 5000;
        frames(30);
        check(g.day == Story.FINAL_DAY && !g.hasDialog() && g.money == 5000, "no rent is collected on the last morning");
        g.minute = 5 * 60 + 55;
        frames(20);
        check(g.hasDialog() && g.endPending && !g.over && g.flag("eng.finale"), "at six the visit begins");
        check(drawn().contains("גיל, אנחנו למטה!"), "with the story's first beat");
        reload();
        check(g.hasDialog() && g.endPending, "the visit survives a reload");
        closeDialogs();
        frame();
        check(g.over && g.screen instanceof EndScreen && !g.endPending, "when the last message closes the year is over");
        check(g.endTitle.equals("השנה נגמרה") && g.endReason.contains("שנה שלמה בעיר הגדולה") && g.flag("seen.story.fin.end"),
                "title and body come from the last finale beat");
        String text = drawn();
        check(text.contains("השנה נגמרה") && text.contains("והחזקת מעמד") && text.contains("₪5000 בכיס") && !text.contains("המשחק נגמר"),
                "the end screen is the 'year is over' one: " + text);
        check(g.musicTrack() == -1, "no music");
        reload();
        check(g.over && drawn().equals(text), "the end screen survives a reload");
        frames(100);
        check(g.day == Story.FINAL_DAY, "nothing moves after the end");
        check(tapLabel("משחק חדש") && g.screen instanceof TitleScreen && !g.started, "a new game can start");
        reload();
        check(g.screen instanceof TitleScreen && !g.started && !hasButton("המשך"), "no finished game to continue");
        // The plain game over still looks like itself.
        fresh();
        g.end("בדיקה");
        check(drawn().contains("המשחק נגמר") && drawn().contains("בדיקה"), "game over variant");
        // A finale without beats still ends the year.
        fresh();
        g.day = Story.FINAL_DAY + 30;
        g.setVar("eng.morning", 1);
        frames(3);
        check(g.endPending || g.over, "a save from past the last day goes straight to the visit");
    }

    // ================= events, venues, crime =================

    static void events() {
        fresh();
        enter(City.SCHOOL, 12 * 60);
        g.screen = EventScreen.open(g, "sim.event", 0.4f);
        check(g.screen != null && hasButton("קדימה") && drawn().contains("אירוע ניסיון"), "intro card with title and how-to");
        float minute = g.minute;
        frames(90);
        check(g.minute == minute, "time stands still in an event");
        reload();
        check(hasButton("קדימה"), "the intro survives a reload");
        int begins = TestEvent.begins, pauses = TestEvent.pauses;
        check(tapLabel("קדימה") && ((EventScreen) g.screen).playing(), "play");
        check(TestEvent.begins == begins + 1 && TestEvent.lastDifficulty == 0.4f, "the Mini gets the event's difficulty");
        frames(20);
        reload();
        check(TestEvent.pauses == pauses + 1 && !((EventScreen) g.screen).playing() && hasButton("קדימה"),
                "leaving mid-action goes back to the intro card");
        tapLabel("קדימה");
        frames(10);
        check(g.back() && ((EventScreen) g.screen).playing(), "no way out with back");
        tap(60, lh * 0.6f);
        frame();
        EventScreen e = (EventScreen) g.screen;
        check(e.state == EventScreen.DONE && e.won() && e.result == Mini.PERFECT && heard.get(Sfx.PERFECT), "won");
        check(drawn().contains("זה עבר בשלום.") && hasButton("המשך"), "outcome card");
        reload();
        e = (EventScreen) g.screen;
        check(e.state == EventScreen.DONE && e.won(), "the outcome survives a reload");
        check(tapLabel("המשך") && g.screen instanceof BuildingScreen && g.at == City.SCHOOL, "the callback leads back to town");
        // Lost, and decided by the clock.
        g.screen = EventScreen.open(g, "sim.event", 5f);
        tapLabel("קדימה");
        check(TestEvent.lastDifficulty == 1f, "difficulty is clamped to 0..1");
        tap(300, lh * 0.6f);
        frame();
        e = (EventScreen) g.screen;
        check(!e.won() && drawn().contains("הפעם זה לא הלך."), "lost");
        tapLabel("המשך");
        g.screen = EventScreen.open(g, "sim.event", 0);
        tapLabel("קדימה");
        frames(30 * 5);
        check(((EventScreen) g.screen).state == EventScreen.DONE && ((EventScreen) g.screen).result == Mini.OK, "an event ends by itself");
        tapLabel("המשך");
        check(EventScreen.open(g, "nope", 0) == null, "an unknown event does not open");
    }

    static TestVenue venue() {
        return (TestVenue) ((VenueScreen) g.screen).mini;
    }

    static void venues() {
        fresh();
        enter(City.MARKET, 12 * 60);
        int begins = TestVenue.begins;
        g.screen = VenueScreen.open(g, "sim.venue", City.MARKET);
        check(TestVenue.begins == begins && g.var("simv.visits") == 0, "opening alone does not begin the Mini");
        frame();
        check(TestVenue.begins == begins + 1 && g.var("simv.visits") == 1, "the first frame does");
        check(drawn().contains("שולחן ניסיון") && drawn().contains("מקום לבדיקות בלבד"), "title and how-to are on screen");
        float minute = g.minute, food = g.food;
        frames(150);
        check(g.minute == minute && g.food == food, "time stands still inside");

        // h.pay moves real money, through the books.
        g.rnd.setSeed(1);
        int money = g.money;
        float spent = g.var("day.spent");
        TestVenue v = venue();
        tap(250, v.betY() + 20);
        check(g.money == money - 10 && g.var("day.spent") == spent + 10 && v.bet == 10, "a bet takes real money at once");
        frames(40);
        check(v.bet == 0 && (g.money == money - 10 || g.money == money + 10), "and is settled a second later");

        // Leaving the app with a bet on the table: the Mini settles it, and loading changes nothing.
        money = g.money;
        tap(250, v.betY() + 20);
        check(g.money == money - 10, "second bet");
        int pauses = TestVenue.pauses;
        begins = TestVenue.begins;
        reopen();
        check(TestVenue.pauses == pauses + 1 && g.money == money, "pause() gave the open bet back");
        check(TestVenue.begins == begins && g.var("simv.visits") == 1, "loading does not run begin()");
        frame();
        check(TestVenue.begins == begins + 1 && g.var("simv.visits") == 2, "the first frame after coming back does");
        // Coming back to the same process after a pause begins again too, on the same Mini.
        v = venue();
        g.onAppPause();
        frame();
        check(venue() == v && TestVenue.begins == begins + 2, "a pause without a restart resumes the same Mini");

        // Drags reach the Mini, but only the ones that started on it.
        int moves = TestVenue.moves;
        g.move(100, 300);
        g.up(100, 300);
        check(TestVenue.moves == moves, "a move without a touch is nothing");
        g.down(100, lh - 150);
        g.move(120, lh - 160);
        g.move(140, lh - 170);
        g.up(140, lh - 170);
        check(TestVenue.moves == moves + 2, "a drag is delivered");
        g.down(100, 40);
        g.move(120, 60);
        check(TestVenue.moves == moves + 2, "touches on the HUD are not the Mini's");
        g.up(120, 60);

        // No money, no bet.
        g.money = 5;
        tap(250, venue().betY() + 20);
        check(g.money == 5 && venue().bet == 0, "pay refuses what the player cannot afford");

        // Out through the Mini's own exit button, or with back.
        tap(180, venue().exitY() + 20);
        check(g.screen instanceof VenueScreen, "the exit is taken on the next frame");
        frame();
        check(g.screen instanceof BuildingScreen && g.at == City.MARKET, "exit leads back to the building");
        g.screen = VenueScreen.open(g, "sim.venue", City.MARKET);
        frames(3);
        check(g.back() && g.screen instanceof BuildingScreen, "back leaves too");
        g.screen = VenueScreen.open(g, "sim.venue", City.MARKET);
        check(g.back() && g.screen instanceof BuildingScreen, "even before the first frame");
        check(VenueScreen.open(g, "nope", City.MARKET) == null, "an unknown venue does not open");
    }

    /** Leaves the alley until the wanted event (or none) comes up. */
    static void leaveAlleyUntil(String id) {
        for (int i = 0; i < 500; i++) {
            Crime.leaveAlley(g);
            if (g.screen instanceof EventScreen && ((EventScreen) g.screen).id.equals(id)) return;
        }
        throw new AssertionError("no " + id + " in 500 tries");
    }

    static void crime() {
        fresh();
        enter(City.ALLEY, 23 * 60);
        check(tapLabel("שולחן ניסיון") && g.screen instanceof VenueScreen, "the alley opens once its Mini is registered");
        frame();
        tap(100, venue().betY() + 20);
        frame();
        check(g.var("heat") == 25 && heard.get(Sfx.SIREN), "the Mini heats the street");
        // Through the real exit button: at heat 100 trouble waits six times out of ten.
        g.setVar("heat", 100);
        int trouble = 0;
        g.rnd.setSeed(42);
        for (int i = 0; i < 400; i++) {
            g.screen = VenueScreen.open(g, "alley", City.ALLEY);
            frame();
            tap(180, venue().exitY() + 20);
            frame();
            if (g.screen instanceof EventScreen) trouble++;
            else check(g.screen instanceof BuildingScreen && g.at == City.ALLEY, "no trouble: back at the alley's menu");
        }
        check(trouble > 200 && trouble < 280, "trouble at heat 100 about 60% of the time, got " + trouble + "/400");
        g.setVar("heat", 0);
        trouble = 0;
        for (int i = 0; i < 2000; i++) {
            Crime.leaveAlley(g);
            if (g.screen instanceof EventScreen) trouble++;
        }
        check(trouble > 40 && trouble < 130, "about 4% on a cold street, got " + trouble + "/2000");
        check(Math.abs(Crime.trouble(50) - 0.18f) < 0.001f && Crime.trouble(500) == Crime.trouble(100), "the published curve");

        // A lost chase is an arrest.
        g.setVar("heat", 80);
        g.setVar("alley.q0", 4);
        g.setVar("alley.q3", 2);
        g.setVar("alley.cap", 20);
        g.setVar("alley.debt", 100);
        g.money = 1000;
        g.energy = 80;
        leaveAlleyUntil("chase");
        check(Math.abs(((EventScreen) g.screen).difficulty - 0.8f) < 1e-5f, "the chase is as hard as the street is hot");
        reload();
        tapLabel("קדימה");
        tap(300, lh * 0.6f);
        frame();
        check(drawn().contains("תפסו אותך") && drawn().contains("קנס של ₪400"), "the outcome card says what an arrest costs");
        check(g.money == 1000 && g.var("record") == 0, "nothing happens until the card is closed");
        reload();
        float spent = g.var("day.spent");
        int day = g.day;
        check(tapLabel("המשך"), "close the card");
        check(g.money == 600 && g.var("day.spent") == spent + 400, "fine");
        check(g.var("alley.q0") == 0 && g.var("alley.q3") == 0 && g.var("alley.cap") == 20 && g.var("alley.debt") == 100,
                "the goods are gone, the rest of the alley's notes are not");
        // Heat drops to 10 at the arrest and by another 10 at the midnight spent in the cell.
        check(g.var("record") == 1 && g.var("arrests") == 1 && g.var("heat") == 0 && g.facts().record, "a record, and the street cools");
        check(g.day == day + 1 && g.minute == 8 * 60 && g.at == City.HOME && g.screen instanceof MapScreen && g.energy == 45,
                "released the next morning, at home");
        reload();
        // The second arrest costs more; arrested after midnight means the same morning.
        enter(City.ALLEY, 2 * 60);
        day = g.day;
        g.setVar("heat", 50);
        g.energy = 20;
        leaveAlleyUntil("chase");
        check(Math.abs(((EventScreen) g.screen).difficulty - 0.65f) < 1e-5f, "a tired runner has it harder");
        tapLabel("קדימה");
        tap(300, lh * 0.6f);
        frame();
        check(drawn().contains("קנס של ₪800"), "repeat offender");
        tapLabel("המשך");
        check(g.money == -200 && g.var("arrests") == 2 && g.day == day && g.minute == 8 * 60 && g.var("heat") == 10,
                "fined into debt, out the same morning");

        // An escape changes nothing.
        enter(City.ALLEY, 23 * 60);
        g.money = 500;
        g.setVar("heat", 60);
        g.setVar("alley.q1", 3);
        leaveAlleyUntil("chase");
        tapLabel("קדימה");
        tap(60, lh * 0.6f);
        frame();
        check(drawn().contains("ברחת"), "escaped");
        tapLabel("המשך");
        check(g.money == 500 && g.var("alley.q1") == 3 && g.var("heat") == 60 && g.var("arrests") == 2 && g.screen instanceof MapScreen
                && g.at == City.ALLEY, "an escape costs nothing");

        // A lost fight is a robbery; a won one is not.
        enter(City.ALLEY, 23 * 60);
        leaveAlleyUntil("fight");
        tapLabel("קדימה");
        tap(300, lh * 0.6f);
        frame();
        check(drawn().contains("הוא לקח ₪100 מהכיס שלך"), "the card names the loss");
        tapLabel("המשך");
        check(g.money == 400 && g.var("alley.q1") == 3 && g.var("arrests") == 2 && g.screen instanceof MapScreen, "robbed of a fifth");
        enter(City.ALLEY, 23 * 60);
        leaveAlleyUntil("fight");
        tapLabel("קדימה");
        tap(60, lh * 0.6f);
        frame();
        tapLabel("המשך");
        check(g.money == 400, "a won fight costs nothing");
        g.money = -50;
        enter(City.ALLEY, 23 * 60);
        leaveAlleyUntil("fight");
        tapLabel("קדימה");
        tap(300, lh * 0.6f);
        frame();
        check(drawn().contains("לא מצא כלום"), "nothing to take from empty pockets");
        tapLabel("המשך");
        check(g.money == -50, "and nothing is taken");

        // Heat cools by ten every midnight.
        fresh();
        g.setVar("heat", 35);
        g.minute = 1439;
        frames(5);
        check(g.var("heat") == 25, "heat decays daily");
        g.setVar("heat", 4);
        g.minute = 1439;
        frames(5);
        check(g.var("heat") == 0, "down to zero, not below");
        check(g.badKeys == 0, "no malformed keys");
    }

    // ================= saves =================

    static final String V1 = "v=1;started=true;female=true;over=false;money=392;day=3;minute=674.5;food=61;water=54;energy=73;"
            + "meals=1;bottles=2;cookHired=true;lastShiftDay=2;shiftsDone=2;rentDebt=0;rentMissed=0;px=230.0;py=190.0;at=5;";
    static final String V2 = "v=2;started=true;female=true;name=גיל;over=false;money=392;day=4;minute=674.0041;food=61.067062;"
            + "water=54.37805;energy=73.85522;meals=1;bottles=2;cookHired=true;lastShiftDay=3;shiftsDone=2;rentDebt=0;"
            + "rentMissed=0;px=230.0;py=190.0;at=5;endReason=;";

    static Game loaded(String save) {
        Game n = new Game();
        n.load(save);
        check(n.started, "the save loads: " + save);
        // Whatever an old save turned into must itself be a save that reloads unchanged.
        g = n;
        reopen();
        frame();
        return g;
    }

    static void oldSaves() {
        Game old = loaded(V1);
        check(old.day == 3 && old.money == 392 && old.screen instanceof MapScreen && old.name.isEmpty() && old.hey().isEmpty()
                && old.female && old.meals == 1 && old.bottles == 2 && old.shiftsDone == 2, "v1: the basics");
        check(old.hired(Job.COOK) && !old.hasVar("cookhired") && old.var("fit") == 5 && old.var("cha") == 5 && old.var("edu") == 0
                && old.var("mom.trust") == 50, "v1: cookHired became hired.0, stats got their starting values");
        check(old.save().startsWith("v=3;"), "v1: saved again as v3");

        old = loaded(V2 + "dialogs=אמא על הקו|נו%3b מה נשמע?|שכירות|שילמת.|;scr=building;sb=5;");
        check(old.screen instanceof BuildingScreen && old.at == City.MARKET && old.name.equals("גיל") && old.hired(Job.COOK), "v2: building");
        check(old.hasDialog() && drawn().contains("נו; מה נשמע?"), "v2: waiting messages come along");
        closeDialogs();
        check(!old.hasDialog(), "v2: both old messages close");

        old = loaded(V2.replace("at=5", "at=-1").replace("px=230.0", "px=180.0").replace("py=190.0", "py=300.0") + "dialogs=;scr=map;mt=4;mf=true;");
        check(old.screen instanceof MapScreen && old.at == -1, "v2: mid-walk");
        for (int i = 0; i < 900 && !(g.screen instanceof BuildingScreen); i++) {
            frame();
            closeDialogs();
        }
        check(g.screen instanceof BuildingScreen && g.at == City.RESTAURANT, "v2: the walk goes on to its building");

        // The old cook screen in the middle of its eight dishes: three done, the fourth interrupted.
        old = loaded(V2.replace("at=5", "at=4").replace("minute=674.0041", "minute=720.0") + "dialogs=;scr=cook;cs=4;cr=3;cres=21200000;cpay=79;csent=false;csw=false;");
        check(old.screen instanceof ShiftScreen, "v2: an interrupted cook shift becomes a shift");
        ShiftScreen s = (ShiftScreen) old.screen;
        check(s.state == ShiftScreen.PAUSED && s.job.id == Job.COOK && s.pay == 79 && s.round == 1, "v2: it resumes where the hours were, with the pay so far");
        ShiftScreen done = playShift(new Random(1), 0.9f);
        check(done != null && done.pay > 79 && Math.abs(g.minute - (720 + 6 * 60)) < 1, "v2: and can be finished");
        old = loaded(V2 + "dialogs=;scr=cook;cs=0;cr=0;cres=00000000;cpay=0;csent=false;csw=false;");
        check(old.screen instanceof ShiftScreen && ((ShiftScreen) old.screen).state == ShiftScreen.INTRO, "v2: cook intro");
        old = loaded(V2 + "dialogs=;scr=cook;cs=3;cr=8;cres=21212121;cpay=260;csent=false;csw=false;");
        check(old.screen instanceof BuildingScreen && old.money == 392, "v2: the old end card has nothing left to pay");

        old = loaded(V2.replace("at=5", "at=7") + "dialogs=;scr=sleep;sl=312.5;");
        check(old.screen instanceof SleepScreen, "v2: asleep");
        for (int i = 0; i < 400 && g.screen instanceof SleepScreen; i++) frame();
        check(Math.abs(g.minute - (674.0041f + 312.5f)) < 0.5f && g.screen instanceof SummaryScreen,
                "v2: the rest of the night passes and ends with the new summary");

        old = loaded(V2.replace("over=false", "over=true").replace("endReason=", "endReason=זהו%3b נגמר") + "dialogs=;scr=end;");
        check(old.screen instanceof EndScreen && old.endReason.equals("זהו; נגמר") && old.endTitle.isEmpty(), "v2: game over");
        old = loaded(V2 + "dialogs=;scr=name;nf=false;nn=דנ;");
        check(old.screen instanceof NameScreen && ((NameScreen) old.screen).name.equals("דנ"), "v2: typing a name for a new game");
        old = loaded(V2 + "dialogs=;scr=title;");
        check(old.screen instanceof TitleScreen && hasButton("המשך"), "v2: title with a game to continue");

        // Old saves never had a summary or a morning: the first night and the first morning just work.
        old = loaded(V2.replace("day=4", "day=20") + "dialogs=;scr=map;mt=-1;mf=false;");
        check(old.var("eng.week") == 2 && !old.hasDialog(), "v2: mom does not catch up on calls that already happened");
        // A save from after the last day goes to the visit on its first calm morning.
        old = loaded(V2.replace("day=4", "day=140") + "dialogs=;scr=map;mt=-1;mf=false;");
        frames(3);
        check(old.endPending, "v2: a year that is already over ends");
        Game none = new Game();
        none.load("v=2;started=false;female=false;name=;over=false;money=0;day=0;scr=name;nf=true;nn=ab;");
        check(!none.started && none.screen instanceof TitleScreen, "v2: a save without a game is nothing");
    }

    static void saveLoad() {
        fresh();
        goTo(City.MARKET);
        g.minute = 12 * 60;
        tapLabel("בקבוק מים");
        String s = g.save();
        int money = g.money, bottles = g.bottles, at = g.at;
        float minute = g.minute;
        Game g2 = new Game();
        g2.load(s);
        check(g2.name.equals("גיל") && g2.started && !g2.female && g2.money == money && g2.bottles == bottles && g2.at == at
                && g2.minute == minute, "save/load round trip");
        Game g3 = new Game();
        g3.load("garbage;;=;v=1;started=true;money=abc");
        check(!g3.started && g3.screen instanceof TitleScreen, "corrupt save is ignored");
        g3.load(null);
        g3.load("");
        g3.load("v=9;started=true;");
        g3.load(";;;===;;");
        check(!g3.started, "nonsense is ignored");
        // A damaged v3 save starts clean but keeps what the player owns.
        g3.load("v=3;started=true;money=abc;vars=gems:5.0,own.hat:1.0,fit:9.0,opt.music:0.0,broken,x:y,:3,;scr=map;");
        check(!g3.started && g3.screen instanceof TitleScreen && g3.var("gems") == 5 && g3.var("own.hat") == 1 && !g3.optOn("opt.music")
                && !g3.hasVar("fit"), "a damaged save keeps purchases and options only");
        g3.load("v=3;started=true;money=10;day=2;msgs=a|b|c|-1|0|3|1|x|;scr=map;");
        check(!g3.started && g3.screen instanceof TitleScreen, "a damaged message queue starts clean");
        // Out-of-range numbers are pulled back in.
        g3.load("v=3;started=true;money=10;day=-4;minute=5000;food=300;water=-2;energy=1e9;meals=99;bottles=-3;px=-50;py=9999;at=44;"
                + "vars=fit:900.0,heat:-3.0,BAD:1.0,day:7.0,;scr=map;");
        check(g3.started && g3.day == 1 && g3.minute < 1440 && g3.food == 100 && g3.water == 0 && g3.energy == 100
                && g3.meals == Game.BAG_MAX && g3.bottles == 0 && g3.at == -1 && g3.var("fit") == 100 && g3.var("heat") == 0
                && !g3.hasVar("BAD") && g3.var("day") == 1, "numbers are clamped on load");
        g = g3;
        frames(5);
        // Text with every separator survives.
        fresh();
        g.dialog("כותרת = עם; סימנים | ו-100%", "שורה\nשנייה; a=b|c%");
        g.end("בדיקה = של; סוף | משחק 100%");
        g.dialog("אחרי", "הסוף");
        reload();
        check(g.endReason.equals("בדיקה = של; סוף | משחק 100%"), "separators in text are escaped");
        System.out.println("  a v3 save: " + s);
    }

    /** Closing the app at any point and reopening it lands on the same screen in the same state. */
    static void resume() {
        // Before any game: the title, the name being typed, the store.
        g = new Game();
        frame();
        reload();
        check(g.screen instanceof TitleScreen && !g.started, "resume: a fresh title screen");
        tapLabel("חנות");
        reload();
        check(g.screen instanceof StoreScreen, "resume: the store before any game");
        g.back();
        check(g.screen instanceof TitleScreen, "resume: the store leads back to the title");
        tapLabel("אישה");
        tapLabel("ג");
        tapLabel("י");
        reload();
        check(g.screen instanceof NameScreen && ((NameScreen) g.screen).name.equals("גי") && !g.started, "resume: a half-typed name");
        tapLabel("ל");
        check(tapLabel("המשך") && g.started && g.female && g.name.equals("גיל"), "resume: and the game starts from it");
        reload();
        check(g.hasDialog(), "resume: the opening messages");
        closeDialogs();

        // Keyboard: delete, no leading or double spaces, length limit, empty name can't continue.
        Game keep = g;
        g = new Game();
        frame();
        tapLabel("גבר");
        check(!tapLabel("המשך"), "name: empty name is refused");
        tapLabel("רווח");
        for (int i = 0; i < 20; i++) tapLabel("ש");
        check(((NameScreen) g.screen).name.length() == NameScreen.MAX_LEN, "name: length limit");
        for (int i = 0; i < 20; i++) tapLabel("מחק");
        check(((NameScreen) g.screen).name.isEmpty(), "name: delete");
        g = keep;

        // On the street: standing, walking, walking fast.
        reload();
        check(g.screen instanceof MapScreen, "resume: the map");
        tapBuilding(City.RESTAURANT);
        frames(60);
        float px = g.px, py = g.py;
        reload();
        check(g.screen instanceof MapScreen && Math.abs(g.px - px) + Math.abs(g.py - py) < 10, "resume: mid-walk position");
        tapLabel("הרץ זמן");
        reload();
        check(hasButton("עצור"), "resume: fast-forward stays on");
        for (int i = 0; i < 900 && !(g.screen instanceof BuildingScreen); i++) {
            frame();
            closeDialogs();
        }
        check(g.screen instanceof BuildingScreen && g.at == City.RESTAURANT, "resume: walk finishes after reload");

        // Inside a building, with a message waiting.
        g.minute = 9 * 60;
        tapLabel("לבקש עבודה: טבחית");
        check(g.hasDialog(), "resume: hired message is up");
        reload();
        check(g.screen instanceof BuildingScreen && g.at == City.RESTAURANT && g.hasDialog() && g.hired(Job.COOK),
                "resume: building and its message survive");
        closeDialogs();
        // A closed door keeps its line.
        enter(City.BAKERY, 15 * 60);
        String door = drawn();
        reload();
        check(drawn().equals(door) && door.contains("סגור עכשיו"), "resume: a closed building looks the same");

        // Shift: intro, in the middle of a round, and right after a round was judged.
        enter(City.RESTAURANT, 9 * 60);
        g.energy = 95;
        check(tapLabel("להתחיל משמרת: טבחית"), "resume: shift starts");
        String intro = drawn();
        reload();
        check(g.screen instanceof ShiftScreen && g.lastShiftDay != g.day && drawn().equals(intro), "resume: shift intro survives");
        check(tapLabel("מתחילים"), "resume: begin");
        frames(10);
        check(((ShiftScreen) g.screen).playing(), "resume: playing");
        reload();
        check(g.screen instanceof ShiftScreen && !((ShiftScreen) g.screen).playing() && g.lastShiftDay == g.day,
                "resume: mid-round comes back paused");
        frames(120);
        int money = g.money;
        float minute = g.minute;
        check(tapLabel("ממשיכים") && ((ShiftScreen) g.screen).playing() && ((ShiftScreen) g.screen).round == 0, "resume: the round restarts");
        ShiftScreen s = (ShiftScreen) g.screen;
        Random r = new Random(6);
        for (int i = 0; i < 30 * 60 && s.state == ShiftScreen.PLAY; i++) {
            s.mini.bot(r, 0.9f);
            frame();
        }
        check(s.state == ShiftScreen.SHOW && g.money > money, "resume: round judged and paid");
        money = g.money;
        reload();
        check(g.money == money && g.minute == minute, "resume: nothing passes while paused");
        check(tapLabel("ממשיכים"), "resume: continue after a judged round");
        check(g.money == money && Math.abs(g.minute - minute - 120) < 0.01f && ((ShiftScreen) g.screen).playing()
                && ((ShiftScreen) g.screen).round == 1, "resume: the judged round is not paid twice and its hours pass once");
        // The same without leaving the process: pause and continue on the same screen.
        frames(15);
        s = (ShiftScreen) g.screen;
        g.onAppPause();
        frames(30);
        check(g.screen == s && s.state == ShiftScreen.PAUSED && tapLabel("ממשיכים") && s.playing(), "resume: pause and continue in place");
        check(playShift(r, 0.9f) != null, "resume: the shift ends");
        // Let the last floating "+₪" fade: game feel is not part of the save.
        frames(45);
        String card = drawn();
        reload();
        check(g.screen instanceof ShiftScreen && drawn().equals(card) && g.shiftsDone == 1, "resume: end card survives word for word");
        tapLabel("המשך");
        check(g.screen instanceof BuildingScreen, "resume: back in the restaurant");
        check(g.food > 60 && g.water > 60, "work: the employer's food and water keep you fed (" + g.food + "/" + g.water + ")");
        closeDialogs();

        // Asleep: the rest of the night still passes, no more and no less; then the summary.
        enter(City.HOME, 21 * 60);
        g.energy = 10;
        float start = clock();
        check(tapLabel("לישון 8"), "resume: sleep");
        frames(25);
        String night = drawn();
        reload();
        check(g.screen instanceof SleepScreen, "resume: still asleep");
        for (int i = 0; i < 400 && g.screen instanceof SleepScreen; i++) {
            frame();
            closeDialogs();
        }
        check(g.screen instanceof SummaryScreen && Math.abs(clock() - start - 480) < 1, "resume: slept 8 hours in total, got " + (clock() - start));
        check(night.contains("מים מהברז"), "resume: the tip was up");
        reload();
        check(g.screen instanceof SummaryScreen, "resume: the summary");
        tapLabel("המשך");
        closeDialogs();
        check(tapLabel("חנות"), "resume: store from the flat");
        reload();
        check(g.screen instanceof StoreScreen && g.musicTrack() >= 0, "resume: the store inside the game");
        g.back();
        check(g.screen instanceof BuildingScreen && g.at == City.HOME, "resume: and back into the flat");

        // An event and a venue.
        g.screen = EventScreen.open(g, "sim.event", 0.3f);
        reload();
        check(g.screen instanceof EventScreen, "resume: event intro");
        g.screen = VenueScreen.open(g, "sim.venue", City.HOME);
        frames(3);
        reload();
        check(g.screen instanceof VenueScreen, "resume: venue");
        g.back();

        // Title (after backing out of the map) and both endings.
        tapLabel("חזרה");
        g.back();
        reload();
        check(g.screen instanceof TitleScreen && g.started, "resume: title with a game to continue");
        tapLabel("גבר");
        tapLabel("גבר");
        tapLabel("ד");
        reload();
        check(g.screen instanceof NameScreen && g.started && g.name.equals("גיל"), "resume: naming a new game while the old one still exists");
        g.back();
        check(tapLabel("המשך") && g.screen instanceof MapScreen, "resume: continue from title");
        g.end("בדיקה = של; סוף | משחק 100%");
        reload();
        check(g.screen instanceof EndScreen && g.endReason.equals("בדיקה = של; סוף | משחק 100%"), "resume: end screen and its text");
        tapLabel("משחק חדש");
        reload();
        check(g.screen instanceof TitleScreen && !g.started, "resume: after the end");
    }

    /** Menus pause the clock. */
    static void menuPause() {
        fresh();
        check(goTo(City.MARKET), "pause: at market");
        float minute = g.minute, food = g.food;
        frames(300);
        check(g.minute == minute && g.food == food, "pause: time stands still inside a building menu");
        tapLabel("חזרה");
        frames(30);
        check(g.minute > minute, "pause: time runs again on the street");
        g.back();
        minute = g.minute;
        frames(300);
        check(g.screen instanceof TitleScreen && g.minute == minute, "pause: time stands still on the main menu");
        tapLabel("חנות");
        frames(100);
        check(g.minute == minute, "pause: and in the store");
        System.out.println("  menus pause the clock");
    }

    // ================= the long runs =================

    /**
     * A simple player: sleeps at night, eats and drinks when low, shops when the bag is empty and
     * works one cook shift a day. skill is handed to the cook Mini's own bot.
     */
    static void bot(int days, float skill, long seed) {
        g = new Game();
        Random r = new Random(seed);
        g.rnd.setSeed(seed);
        startGame("גבר");
        closeDialogs();
        int lastDay = 0, guard = 0;
        while (!g.over && g.day <= days && guard++ < 200000) {
            closeDialogs();
            if (g.day != lastDay) {
                lastDay = g.day;
                if (pictures) System.out.printf("  day %2d %s money=%5d food=%3.0f water=%3.0f energy=%3.0f bag=%d/%d debt=%d rank=%d fit=%.1f%n",
                        g.day, Ui.clock(g.minute), g.money, g.food, g.water, g.energy, g.meals, g.bottles, g.rentDebt,
                        g.rank(Job.COOK), g.stat(Host.FIT));
            }
            if (g.screen instanceof SleepScreen) {
                frame();
                continue;
            }
            if (g.screen instanceof SummaryScreen) {
                tapLabel("המשך");
                continue;
            }
            if (g.screen instanceof ShiftScreen) {
                playShift(r, skill);
                tapLabel("המשך");
                continue;
            }
            if (g.screen instanceof MapScreen || g.screen instanceof BuildingScreen) {
                if (g.food < 55) tapLabel("לאכול (");
                if (g.water < 50) tapLabel("לשתות (");
            }
            boolean needShop = (g.meals < 2 || g.bottles < 1) && g.money >= 60;
            boolean canWork = g.hired(Job.COOK) ? (g.lastShiftDay != g.day && g.minute >= 8 * 60 && g.minute <= 14 * 60 && g.energy >= 55)
                    : (g.minute >= 8 * 60 && g.minute < 23 * 60);
            if (canWork) {
                if (!goTo(City.RESTAURANT)) continue;
                if (!g.hired(Job.COOK)) {
                    tapLabel("לבקש עבודה: טבח");
                    closeDialogs();
                }
                if (tapLabel("להתחיל משמרת: טבח")) {
                    playShift(r, skill);
                    tapLabel("המשך");
                    closeDialogs();
                }
                if (g.screen instanceof BuildingScreen && g.food < 50 && g.money > 300) tapLabel("לאכול כאן");
            } else if (needShop && City.B[City.MARKET].isOpen(g.minute) && g.minute < 20 * 60) {
                if (!goTo(City.MARKET)) continue;
                while (g.meals < 3 && tapLabel("ארוחה ארוזה")) frame();
                while (g.bottles < 2 && tapLabel("בקבוק מים")) frame();
            } else {
                if (!goTo(City.HOME)) continue;
                tapLabel("לשתות מהברז");
                if (g.energy < 60 || g.minute > 19 * 60 || g.minute < 5 * 60) {
                    tapLabel("לישון 8");
                    for (int i = 0; i < 400 && g.screen instanceof SleepScreen; i++) frame();
                } else {
                    // Menus pause the clock, so waiting happens out on the street.
                    tapLabel("חזרה");
                    frames(60);
                }
            }
        }
        check(guard < 200000, "the bot does not get stuck");
        System.out.printf("bot skill=%.2f -> day %d over=%s money=%d shifts=%d cook rank=%d xp=%d fitness=%.1f collapses=%.0f reason=%s%n",
                skill, g.day, g.over, g.money, g.shiftsDone, g.rank(Job.COOK), g.xp(Job.COOK), g.stat(Host.FIT),
                g.var("collapses"), g.endReason);
    }

    static final Beat[] MONKEY_BEATS = {
            QUESTION,
            Beat.say("mk.plain", "מישהו", "סתם הודעה."),
            Beat.say("mk.fx", "מישהו", "הנה.").effect("money+=40;energy-=30;heat+=15"),
            new Beat("mk.two", "אמא על הקו", "{name}, אתה אוכל?", "{name}, את אוכלת?", new String[]{"כן", "לא"},
                    new String[]{"יופי.", "ידעתי."}, new String[]{"יופי.", "ידעתי."}, new String[]{"mom.trust+=3", "lie.count+=1;mom.worry+=8;food-=20"}),
    };

    /**
     * Drops the running game into a corner of the engine, so the soak reaches every screen. The
     * corners come round in turn (n counts the jumps): coverage must not depend on luck.
     */
    /** Puts the player at a building's door: the game never has somebody "at" a place they are not standing. */
    static void stand(int id) {
        g.px = City.B[id].doorX;
        g.py = City.B[id].doorY;
        g.at = id;
    }

    static void jump(Random r, int n) {
        if (!g.started) {
            g.newGame(r.nextBoolean(), "קוף");
            return;
        }
        if (g.over || g.hasDialog() || g.endPending) return;
        int b = r.nextInt(City.B.length);
        switch (n % 12) {
            case 0:
            case 1: {
                Job j = Job.ALL[r.nextInt(Job.ALL.length)];
                g.setVar("hired." + j.id, 1);
                g.addVar("xp." + j.id, r.nextInt(50));
                g.energy = 40 + r.nextInt(60);
                stand(j.building);
                Screen s = ShiftScreen.open(g, j);
                if (s != null) g.screen = s;
                break;
            }
            case 2: {
                Screen s = EventScreen.open(g, new String[]{"chase", "fight", "sim.event"}[r.nextInt(3)], r.nextFloat());
                if (s != null) g.screen = s;
                break;
            }
            case 3: {
                stand(b);
                Screen s = VenueScreen.open(g, r.nextBoolean() ? "alley" : "sim.venue", b);
                if (s != null) g.screen = s;
                break;
            }
            case 4:
                g.screen = SummaryScreen.open(g);
                break;
            case 5:
                g.screen = new StoreScreen(g, r.nextInt(2));
                break;
            case 6:
                g.play(MONKEY_BEATS[r.nextInt(MONKEY_BEATS.length)]);
                break;
            case 7:
                enter(City.ALLEY, (23 * 60 + r.nextInt(120)) % 1440);
                g.setVar("heat", r.nextInt(120));
                g.setVar("alley.q" + r.nextInt(4), r.nextInt(9));
                break;
            case 8:
                g.day = Story.FINAL_DAY - 1;
                g.minute = 23 * 60 + 30;
                break;
            case 9:
                g.earn(r.nextInt(900));
                g.energy = 90;
                g.food = 80;
                g.water = 80;
                break;
            case 10:
                enter(b, r.nextInt(1440));
                break;
            default:
                enter(City.HOME, 20 * 60 + r.nextInt(180));
                g.energy = r.nextInt(60);
        }
    }

    static void monkey(int frames, long seed) {
        g = new Game();
        Random r = new Random(seed);
        int games = 0, maxDay = 0, reloads = 0, jumps = 0, yearsEnded = 0;
        TreeMap<String, Integer> visited = new TreeMap<>();
        boolean isDown = false;
        gfx.reset();
        gfx.maxPrims = 0;
        for (int i = 0; i < frames; i++) {
            if (i % 9000 == 0) lh = (i / 9000) % 2 == 0 ? 760 : 560;
            frame();
            float x = r.nextFloat() * Game.W, y = r.nextFloat() * lh;
            int a = r.nextInt(20);
            if (a < 3) {
                tap(x, y);
            } else if (a == 3) {
                g.down(x, y);
                isDown = true;
            } else if (a == 4 && isDown) {
                g.move(x, y);
            } else if (a == 5 && isDown) {
                g.up(x, y);
                isDown = false;
            }
            if (r.nextInt(900) == 0) g.back();
            if (r.nextInt(130) == 0) {
                // Leave the app and come back, usually into a brand-new process.
                g.onAppPause();
                String saved = g.save();
                Class<?> was = g.screen.getClass();
                if (r.nextInt(5) != 0) g = new Game();
                g.load(saved);
                check(g.save().equals(saved), "monkey: reloading changes nothing at frame " + i + "\n  " + saved + "\n  " + g.save());
                // The test list is not a place to come back to: the app reopens on the title screen.
                if (was == TestMenuScreen.class || was == TestPlayScreen.class) was = TitleScreen.class;
                check(g.screen.getClass() == was, "monkey: same screen after reload, was " + was.getSimpleName());
                reloads++;
            }
            if (r.nextInt(1500) == 0) jump(r, jumps++);
            if (g.over) {
                games++;
                if (!g.endTitle.isEmpty()) yearsEnded++;
            }
            maxDay = Math.max(maxDay, g.day);
            visited.merge(screenName(), 1, Integer::sum);
            check(g.food >= 0 && g.food <= 100 && g.water >= 0 && g.water <= 100 && g.energy >= 0 && g.energy <= 100,
                    "meters in range");
            check(g.minute >= 0 && g.minute < 1440, "minute in range: " + g.minute);
            check(g.meals >= 0 && g.meals <= Game.BAG_MAX && g.bottles >= 0 && g.bottles <= Game.BAG_MAX, "bag in range");
            check(g.var("fit") >= 0 && g.var("fit") <= 100 && g.var("heat") >= 0 && g.var("heat") <= 100
                    && g.var("mom.trust") >= 0 && g.var("mom.trust") <= 100, "ranged variables in range");
            check(g.badKeys == 0, "no malformed variable keys");
            check(g.day >= 1 && g.day <= Story.FINAL_DAY + 1, "the calendar ends with the year: day " + g.day);
        }
        lh = 760;
        for (String scr : new String[]{"TitleScreen", "NameScreen", "MapScreen", "BuildingScreen", "SleepScreen", "SummaryScreen",
                "ShiftScreen", "EventScreen", "VenueScreen", "StoreScreen", "EndScreen"}) {
            check(visited.containsKey(scr), "monkey reached " + scr);
        }
        check(yearsEnded > 0, "monkey saw a year end");
        System.out.printf("monkey: %d frames, %d reloads (each checked byte for byte), %d jumps, reached day %d, game-over frames=%d, "
                + "most primitives in one frame=%d, no exceptions%n", frames, reloads, jumps, maxDay, games, gfx.maxPrims);
        System.out.println("  frames per screen: " + visited);
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError("FAILED: " + what + " (screen=" + (g == null ? "-" : screenName() + ", day=" + g.day + " "
                + Ui.clock(g.minute)) + ", height=" + (int) lh + ")");
    }
}
