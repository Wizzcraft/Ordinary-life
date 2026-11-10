package com.wizzcraft.ima.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BooleanSupplier;

/**
 * Device-lab autopilot: plays a scripted session through the same touch path a finger uses, so the
 * CI emulator can drive the game without knowing any pixel coordinates. Buttons are found by their
 * Hebrew label and buildings by their id; the {@link Driver} turns logical points into real touches.
 *
 * Only the shell creates a tour, and only when the activity was started with the intent extra
 * {@code tour} (adb). A normal launch never reaches this class. See docs/DEVICE_LAB.md.
 */
public final class Tour {
    /** What the platform provides: a tap at a logical point, and a log line. */
    public interface Driver {
        void tap(float x, float y);

        void log(String line);
    }

    /** Pause after every action, so a human (and the screenshot loop) can follow along. */
    private static final float GAP = 0.8f;
    /** A step that has not finished after this long fails the whole tour. */
    private static final float STEP_TIMEOUT = 90f;

    private final Game g;
    private final Driver d;
    private final ArrayList<Step> steps = new ArrayList<>();
    private int index = -1, number;
    private float wait = 2f, stepT, total, retapT;
    private boolean finished, failed;
    private String handledDialog;

    private static final class Step {
        final String what;
        final BooleanSupplier act;

        Step(String what, BooleanSupplier act) {
            this.what = what;
            this.act = act;
        }
    }

    private Tour(Game g, Driver d) {
        this.g = g;
        this.d = d;
    }

    /** Builds the named tour, or returns null if there is no tour by that name. */
    public static Tour create(Game g, String name, Driver d) {
        if (g == null || d == null || name == null) return null;
        Tour t = new Tour(g, d);
        if (name.equals("smoke")) t.smoke();
        else return null;
        d.log("IMA-TOUR start '" + name + "', " + t.steps.size() + " scripted steps");
        return t;
    }

    public boolean done() {
        return finished;
    }

    public boolean failed() {
        return failed;
    }

    // ================= the tours =================

    /** First session: title, name, mom, job, a shift, the market, home, sleep, next morning. */
    private void smoke() {
        add("title: tap 'אישה' (new game, female)", () -> {
            if (g.screen instanceof NameScreen) return true;
            tapLabel("אישה"); // a second tap confirms when an old save exists
            return false;
        });
        for (String k : new String[]{"ג", "י", "ל"}) {
            add("name: type '" + k + "'", once(() -> tapLabel(k),
                    () -> g.screen instanceof NameScreen && ((NameScreen) g.screen).name.endsWith(k)));
        }
        add("name: tap 'המשך' to start the game", once(() -> tapLabel("המשך"),
                () -> g.started && g.screen instanceof MapScreen));
        walk("map: walk to the restaurant", City.RESTAURANT, false);
        add("restaurant: tap 'לבקש עבודה במטבח'", once(() -> tapLabel("לבקש עבודה"), () -> g.cookHired));
        add("restaurant: tap 'להתחיל משמרת'", once(() -> tapLabel("להתחיל משמרת"),
                () -> g.screen instanceof CookGame));
        add("cook: intro, tap 'מתחילים'", once(() -> tapLabel("מתחילים"),
                () -> g.screen instanceof CookGame && ((CookGame) g.screen).playing()));
        add("cook: play the shift (8 dishes)", this::cook);
        add("cook: shift summary, tap 'המשך'", once(() -> tapLabel("המשך"), () -> g.screen instanceof BuildingScreen));
        add("restaurant: tap 'חזרה לרחוב'", once(() -> tapLabel("חזרה לרחוב"), () -> g.screen instanceof MapScreen));
        walk("map: walk to the market", City.MARKET, false);
        add("market: buy 'ארוחה ארוזה'", buy("ארוחה ארוזה", () -> g.meals));
        add("market: buy 'בקבוק מים'", buy("בקבוק מים", () -> g.bottles));
        add("market: tap 'חזרה לרחוב'", once(() -> tapLabel("חזרה לרחוב"), () -> g.screen instanceof MapScreen));
        walk("map: walk home on fast-forward ('הרץ זמן')", City.HOME, true);
        add("home: tap 'לישון 8 שעות'", once(() -> tapLabel("לישון 8"), () -> g.screen instanceof SleepScreen));
        add("sleep: wait for the morning", () -> g.screen instanceof BuildingScreen && g.at == City.HOME);
        add("morning: tap 'חזרה לרחוב'", once(() -> tapLabel("חזרה לרחוב"),
                () -> g.screen instanceof MapScreen && g.day == 2));
    }

    // ================= step helpers =================

    private void add(String what, BooleanSupplier act) {
        steps.add(new Step(what, act));
    }

    private interface IntGet {
        int get();
    }

    /** Taps once, then waits for the effect; taps again only if nothing happened for a while. */
    private BooleanSupplier once(BooleanSupplier tap, BooleanSupplier doneWhen) {
        return () -> {
            if (doneWhen.getAsBoolean()) return true;
            if (retapT <= 0) {
                tap.getAsBoolean();
                retapT = 3f;
            }
            return false;
        };
    }

    private BooleanSupplier buy(String label, IntGet count) {
        int[] start = {-1};
        return () -> {
            if (start[0] < 0) start[0] = count.get();
            if (count.get() > start[0]) return true;
            if (retapT <= 0) {
                tapLabel(label);
                retapT = 3f;
            }
            return false;
        };
    }

    private void walk(String what, int id, boolean fast) {
        add(what, () -> {
            if (g.screen instanceof BuildingScreen && g.at == id) return true;
            if (g.screen instanceof BuildingScreen) {
                tapLabel("חזרה לרחוב");
                return false;
            }
            if (!(g.screen instanceof MapScreen)) return false;
            // Standing still somewhere else (walkTo clears g.at): tap the building again.
            if (g.at != -1 && retapT <= 0) {
                tapBuilding(id);
                retapT = 3f;
                return false;
            }
            if (fast && g.at == -1 && stepT > 1.5f && !labelExists("עצור")) tapLabel("הרץ זמן");
            return false;
        });
    }

    private int dishes;

    private boolean cook() {
        if (!(g.screen instanceof CookGame)) return false;
        CookGame cg = (CookGame) g.screen;
        if (labelExists("ממשיכים")) {
            tapLabel("ממשיכים");
            return false;
        }
        if (cg.btns.size() == 1 && labelExists("המשך")) return true;
        if (cg.playing() && cg.inZone()) {
            dishes++;
            d.log("IMA-TOUR   dish " + dishes);
            d.tap(Game.W / 2, Game.HUD_H + (g.h - Game.HUD_H) * 0.75f);
        }
        return false;
    }

    // ================= engine =================

    /** Called by the shell once per frame, after Game.update and before Game.draw. */
    public void frame(float dt) {
        if (finished || failed) return;
        if (dt > 0.1f) dt = 0.1f;
        total += dt;
        stepT += dt;
        if (retapT > 0) retapT -= dt;
        if (wait > 0) {
            wait -= dt;
            return;
        }
        if (g.over) {
            fail("the game ended");
            return;
        }
        // Any message (mom, hired, rent...) is its own step: read it, then tap the dialog button.
        if (g.hasDialog()) {
            String title = dialogTitle();
            if (handledDialog == null) {
                handledDialog = title;
                number++;
                d.log("IMA-TOUR step " + number + ": dialog '" + title + "', tap 'המשך'");
                wait = GAP * 1.5f;
                return;
            }
            Btn b = dialogButton();
            if (b != null) {
                d.tap(b.x + b.w / 2, b.y + b.h / 2);
                handledDialog = null;
                wait = GAP;
            }
            return;
        }
        handledDialog = null;
        if (index < 0 || steps.get(index).act.getAsBoolean()) {
            index++;
            if (index >= steps.size()) {
                finished = true;
                d.log(String.format(java.util.Locale.ROOT,
                        "IMA-TOUR DONE after %.1f s, day %d %s, money %d, shifts %d, h=%.0f",
                        total, g.day, Ui.clock(g.minute), g.money, g.shiftsDone, g.h));
                return;
            }
            number++;
            stepT = 0;
            retapT = 0;
            d.log("IMA-TOUR step " + number + ": " + steps.get(index).what + "  [" + Ui.clock(g.minute)
                    + ", screen " + g.screen.kind() + "]");
            wait = index == 0 ? 0 : GAP;
            return;
        }
        if (stepT > STEP_TIMEOUT) fail("step timed out: " + steps.get(index).what);
    }

    private void fail(String why) {
        failed = true;
        d.log("IMA-TOUR FAIL at step " + number + ": " + why + " (screen " + g.screen.kind() + ", day " + g.day
                + " " + Ui.clock(g.minute) + ")");
    }

    private boolean labelExists(String prefix) {
        for (Btn b : g.screen.btns) if (b.label.startsWith(prefix)) return true;
        return false;
    }

    /** Taps the centre of the first enabled button whose label starts with the prefix. */
    private boolean tapLabel(String prefix) {
        for (Btn b : g.screen.btns) {
            if (b.label.startsWith(prefix) && b.on) {
                d.tap(b.x + b.w / 2, b.y + b.h / 2);
                return true;
            }
        }
        return false;
    }

    /** Same transform as MapScreen.layout(). */
    private void tapBuilding(int id) {
        City.Building b = City.B[id];
        float areaH = g.h - Game.HUD_H - MapScreen.BAR_H;
        float s = Math.min(Game.W / City.W, areaH / City.H);
        float ox = (Game.W - City.W * s) / 2, oy = Game.HUD_H + (areaH - City.H * s) / 2;
        d.tap(ox + (b.x + b.w / 2) * s, oy + (b.y + b.h / 2) * s);
    }

    // The dialog queue is private to Game; read it reflectively so the tour needs no hooks there.
    private String dialogTitle() {
        Object q = field("dialogs");
        if (q instanceof Collection && !((Collection<?>) q).isEmpty()) {
            Object first = ((Collection<?>) q).iterator().next();
            if (first instanceof String[]) return ((String[]) first)[0];
        }
        return "?";
    }

    private Btn dialogButton() {
        Object b = field("dialogBtn");
        return b instanceof Btn ? (Btn) b : null;
    }

    private Object field(String name) {
        try {
            Field f = Game.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(g);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
