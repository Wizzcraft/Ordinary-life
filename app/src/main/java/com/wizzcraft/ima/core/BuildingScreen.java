package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Inside a building: a menu of things to do there. Time stands still while the menu is open; only
 * the actions themselves take time.
 *
 * The menu is built top to bottom by {@link #menu}: the jobs of this building (from the Job table),
 * the building's own actions, then its venues (from the Minis registry).
 * INTEGRATORS: a new action is three small steps: an id constant, a row(...) line in menu() under
 * the building's case, and a case in act(...) that does it.
 */
final class BuildingScreen extends Screen {
    private static final int EXIT = 99, SLEEP8 = 1, NAP = 2, TAP = 3, STORE = 4, BUY_MEAL = 10, BUY_WATER = 11,
            DINE = 22, BAG_EAT = 30, BAG_DRINK = 31;
    /** Ids of the generated rows: VENUE + index in this building, APPLY / SHIFT + job id. */
    private static final int STUDY = 200, VENUE = 300, APPLY = 400, SHIFT = 500;
    /** Three looks, roomiest first: row height, gap between rows, header height, info text size. */
    private static final float[][] LOOKS = {{50, 8, 78, 13}, {44, 6, 56, 12}, {38, 4, 44, 11.5f}};

    private final City.Building b;
    /** What the player found at the closed door; chosen once per visit. */
    private String closedLine = "";
    /** The venues that can be entered from here right now: Mini ids and their titles. */
    private final List<String> venueIds = new ArrayList<>(), venueTitles = new ArrayList<>();

    // Layout state of the pass that is running (measuring or painting).
    private Gfx c;
    private float[] look;
    private boolean paint;
    private float y;
    private int offered;

    BuildingScreen(Game g, int id) {
        this(g, id, null);
    }

    /** saved == null is a new visit; otherwise the visit a save remembers, with its closed-door line. */
    private BuildingScreen(Game g, int id, String saved) {
        super(g);
        b = City.B[id];
        for (int i = 0; i < Minis.VENUE_AT.length; i++) {
            if (Minis.VENUE_AT[i] != id) continue;
            Mini mini = Minis.create(Minis.VENUE_MINI[i]);
            if (mini == null) continue;
            mini.attach(g.host);
            venueIds.add(Minis.VENUE_MINI[i]);
            venueTitles.add(mini.title());
        }
        if (saved != null) closedLine = saved;
        else if (!b.isOpen(g.minute)) closedLine();
    }

    private String closedLine() {
        if (closedLine.isEmpty()) closedLine = g.fill(Lines.closed(b.id, g.facts(), g.rnd));
        return closedLine;
    }

    // ---------------- the menu ----------------

    private void menu(boolean open) {
        offered = 0;
        if (!open) {
            info(closedLine());
            return;
        }
        for (Job j : Job.ALL) if (j.building == b.id) jobRows(j);
        switch (b.id) {
            case City.HOME:
                info("הדירה שלך. קטנה ורחוקה מהכל, אבל זולה.");
                pair(SLEEP8, "לישון 8 שעות", g.energy < 95, g.energy < 40, NAP, "תנומה של שעתיים", g.energy < 95);
                row(TAP, "לשתות מהברז (חינם)", g.water < 95, false);
                row(STORE, "חנות", true, false);
                info("שכירות " + Ui.nis(Game.RENT) + " כל " + Game.DAYS_PER_MONTH + " ימים, יורדת אוטומטית."
                        + (g.rentDebt > 0 ? " חוב פתוח: " + Ui.nis(g.rentDebt) + "." : ""));
                break;
            case City.MARKET:
                pair(BUY_MEAL, "ארוחה ארוזה · " + Ui.nis(Game.MEAL_PRICE), g.money >= Game.MEAL_PRICE && g.meals < Game.BAG_MAX,
                        false, BUY_WATER, "בקבוק מים · " + Ui.nis(Game.BOTTLE_PRICE),
                        g.money >= Game.BOTTLE_PRICE && g.bottles < Game.BAG_MAX);
                info("בתיק: " + Ui.count(g.meals, "ארוחה אחת", "ארוחות") + ", "
                        + Ui.count(g.bottles, "בקבוק אחד", "בקבוקים") + ". נכנסים עד " + Game.BAG_MAX + " מכל סוג.");
                break;
            case City.SCHOOL:
                school();
                break;
            case City.RESTAURANT:
                row(DINE, "לאכול כאן · " + Ui.nis(Game.DINE_PRICE), g.money >= Game.DINE_PRICE && g.food < 90, false);
                break;
            default:
        }
        for (int i = 0; i < venueIds.size(); i++) row(VENUE + i, venueTitles.get(i), true, true);
        if (offered == 0) info(soon(b.id));
    }

    /** The school's programmes: a lesson row for each one still being studied. */
    private void school() {
        boolean any = false;
        for (int i = 0; i < School.COUNT; i++) {
            if (!School.offered(i)) continue;
            any = true;
            if (School.graduated(g, i)) {
                info("לימודי " + School.NAME[i] + ": יש תעודה. העבודה מחכה בבניין המשרדים.");
            } else {
                row(STUDY + i, "שיעור: " + School.NAME[i] + " (" + School.passed(g, i) + " מתוך " + School.LESSONS
                        + ")", g.money >= School.PRICE, true);
            }
        }
        if (any) {
            info("שיעור עולה " + Ui.nis(School.PRICE) + " ונמשך שעתיים. " + School.LESSONS
                    + " שיעורים שעברו בהצלחה נותנים תעודה ומקצוע.");
        }
    }

    private void jobRows(Job j) {
        String name = j.name(g.female);
        if (!g.hired(j.id)) {
            boolean fits = meets(j);
            row(APPLY + j.id, "לבקש עבודה: " + name, true, fits);
            note(fits ? j.payLine(0) + " · מתחילים " + j.window()
                    : !diploma(j) ? "דרושה תעודה מהמוסד ללימודים · " + j.payLine(0)
                    : "דרוש: " + Job.STAT_NAMES[j.needStat] + " " + j.needValue + ", יש לך "
                    + (int) g.stat(j.needStat) + " · " + j.payLine(0), fits ? Ui.DIM : Ui.ORANGE);
        } else {
            int rank = g.rank(j.id);
            String why = shiftBlock(j);
            row(SHIFT + j.id, "להתחיל משמרת: " + name, why == null, true);
            note(why != null ? why : Job.rankName(g.female, rank) + " · " + j.payLine(rank) + " · "
                    + Job.SHIFT_HOURS + " שעות", Ui.DIM);
        }
    }

    private boolean diploma(Job j) {
        return !j.profession || g.flag("dip." + j.id);
    }

    private boolean meets(Job j) {
        return diploma(j) && (j.needStat == Job.NONE || g.stat(j.needStat) >= j.needValue);
    }

    /** Why a shift can't start right now, or null if it can. */
    private String shiftBlock(Job j) {
        if (g.lastShiftDay == g.day) return "כבר עבדת היום. מחר יש משמרת חדשה.";
        if (!j.canStartAt(g.minute)) return "משמרת אפשר להתחיל " + j.window() + ".";
        if (g.energy < ShiftScreen.MIN_ENERGY) return "אין לך כוח למשמרת שלמה. קודם לישון.";
        return null;
    }

    private static String soon(int id) {
        switch (id) {
            case City.SCHOOL:
                return "בקרוב: לימודים למקצועות כמו רופא, עורך דין ומפתח תוכנה.";
            case City.OFFICE:
                return "מרפאה וחברת תוכנה. כאן עובדים מי שסיימו ללמוד במוסד הלימודים.";
            case City.CASINO:
                return "בקרוב: בלאקג'ק, רולטה, מכונות מזל ופוקר.";
            case City.ALLEY:
                return "בקרוב: כסף מהיר, עם כל הסיכון שבא איתו.";
            default:
                return "בקרוב.";
        }
    }

    // ---------------- layout: the same calls measure a look and paint it ----------------

    private void row(int id, String label, boolean on, boolean primary) {
        if (paint) btns.add(new Btn(id, 16, y, 328, look[0], label, on, primary));
        y += look[0] + look[1];
        offered++;
    }

    /** Two short actions side by side; the first one sits on the right, where reading starts. */
    private void pair(int id1, String label1, boolean on1, boolean primary1, int id2, String label2, boolean on2) {
        if (paint) {
            btns.add(new Btn(id1, 184, y, 160, look[0], label1, on1, primary1));
            btns.add(new Btn(id2, 16, y, 160, look[0], label2, on2, false));
        }
        y += look[0] + look[1];
        offered++;
    }

    private void text(String s, int col, float before, float after) {
        float size = look[3];
        y += before + size;
        if (paint) y = Ui.paragraph(c, s, Game.W / 2, y, 312, size, col);
        else y += Ui.wrap(c, s, 312, size).size() * size * 1.4f;
        y += after - size;
    }

    /** A line or two of explanation between rows. */
    private void info(String s) {
        text(s, Ui.DIM, 1, 4);
    }

    /** A line that belongs to the row right above it. */
    private void note(String s, int col) {
        y -= look[1];
        text(s, col, 3, look[1] + 4);
    }

    private float layout(Gfx gfx, float[] metrics, boolean open, boolean painting) {
        c = gfx;
        look = metrics;
        paint = painting;
        float hh = look[2];
        boolean roomy = hh >= 78, compact = hh >= 56;
        y = Game.HUD_H + (roomy ? 14 : 8);
        if (paint) {
            int ink = Ui.isDark(b.color) ? Ui.INK : Ui.DARK_INK;
            c.fillRoundRect(16, y, 328, hh, 12, b.color);
            c.text(b.name, Game.W / 2, y + (roomy ? 36 : compact ? 26 : 21), roomy ? 26 : compact ? 22 : 18, ink,
                    Gfx.CENTER);
            c.text(open ? b.hours() : "סגור עכשיו · " + b.hours(), Game.W / 2, y + (roomy ? 60 : compact ? 44 : 36),
                    roomy ? 12 : 11, ink, Gfx.CENTER);
        }
        y += hh + (roomy ? 14 : 8);
        menu(open);
        return y;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        boolean open = b.isOpen(g.minute);
        // Take the roomiest look whose menu ends above the bag buttons.
        float limit = h - 124 - 4;
        int pick = 0;
        while (pick < LOOKS.length - 1 && layout(c, LOOKS[pick], open, false) > limit) pick++;
        btns.clear();
        layout(c, LOOKS[pick], open, true);

        // The bag is reachable from inside any building.
        btns.add(new Btn(BAG_EAT, 184, h - 124, 160, 46, "לאכול (" + g.meals + ")", true, false));
        btns.add(new Btn(BAG_DRINK, 16, h - 124, 160, 46, "לשתות (" + g.bottles + ")", true, false));
        btns.add(new Btn(EXIT, 16, h - 66, 328, 50, "חזרה לרחוב", true, false));
        drawButtons(c);
        g.drawHud(c);
    }

    // ---------------- actions ----------------

    @Override
    void down(float x, float y) {
        Btn hit = hit(x, y);
        if (hit != null) act(hit.id);
    }

    private void act(int id) {
        if (id >= SHIFT) {
            Job j = Job.byId(id - SHIFT);
            if (j == null || j.building != b.id || !g.hired(j.id) || shiftBlock(j) != null) return;
            Screen shift = ShiftScreen.open(g, j);
            if (shift == null) return;
            g.sfx(Sfx.TAP);
            g.screen = shift;
            return;
        }
        if (id >= APPLY) {
            Job j = Job.byId(id - APPLY);
            if (j != null && j.building == b.id && !g.hired(j.id)) apply(j);
            return;
        }
        if (id >= VENUE) {
            if (id - VENUE >= venueIds.size()) return;
            Screen venue = VenueScreen.open(g, venueIds.get(id - VENUE), b.id);
            if (venue == null) return;
            g.sfx(Sfx.DOOR);
            g.screen = venue;
            return;
        }
        if (id >= STUDY) {
            Screen lesson = School.lesson(g, id - STUDY);
            if (lesson == null) return;
            g.sfx(Sfx.COIN);
            g.screen = lesson;
            return;
        }
        switch (id) {
            case EXIT:
                g.sfx(Sfx.TAP);
                g.screen = new MapScreen(g);
                break;
            case BAG_EAT:
                g.eat();
                break;
            case BAG_DRINK:
                g.drink();
                break;
            case SLEEP8:
            case NAP:
                if (g.energy >= 95) break;
                g.sfx(Sfx.TAP);
                g.screen = new SleepScreen(g, id == SLEEP8 ? 8 : 2);
                break;
            case TAP:
                if (g.water >= 95) break;
                g.water = Math.min(100, g.water + Game.TAP_WATER);
                g.sfx(Sfx.POUR);
                if (!g.momToast("drank")) g.toast("שתית מים מהברז");
                break;
            case STORE:
                g.sfx(Sfx.TAP);
                g.screen = new StoreScreen(g, StoreScreen.FROM_HOME);
                break;
            case BUY_MEAL:
                if (g.meals >= Game.BAG_MAX || !g.buy(Game.MEAL_PRICE)) break;
                g.meals++;
                bought("ארוחה נכנסה לתיק");
                break;
            case BUY_WATER:
                if (g.bottles >= Game.BAG_MAX || !g.buy(Game.BOTTLE_PRICE)) break;
                g.bottles++;
                bought("בקבוק נכנס לתיק");
                break;
            case DINE:
                if (g.food >= 90 || !g.buy(Game.DINE_PRICE)) break;
                g.food = Math.min(100, g.food + 70);
                g.water = Math.min(100, g.water + 20);
                g.sfx(Sfx.EAT);
                if (!g.momToast("ate")) g.toast("ארוחה חמה. זה היה טוב.");
                g.advance(30, Game.IDLE);
                break;
            default:
        }
    }

    private void bought(String said) {
        g.sfx(Sfx.COIN);
        if (!g.momToast(g.money < Game.BROKE ? "broke" : "bought")) g.toast(said);
    }

    /** Asking for a job: a clear no with the reason when a stat is too low, otherwise hired on the spot. */
    private void apply(Job j) {
        String name = j.name(g.female);
        if (!diploma(j)) {
            g.sfx(Sfx.BAD);
            g.dialog("עוד לא", "כדי לעבוד כ" + name + " צריך תעודה. לומדים במוסד הלימודים: " + School.LESSONS
                    + " שיעורים, " + Ui.nis(School.PRICE) + " לשיעור.");
            return;
        }
        if (!meets(j)) {
            String stat = Job.STAT_NAMES[j.needStat];
            String how = j.needStat == Host.FIT ? "כושר עולה מהליכה ומעבודה פיזית."
                    : j.needStat == Host.CHA ? "כריזמה עולה מעבודה מול אנשים." : "השכלה עולה מלימודים.";
            g.sfx(Sfx.BAD);
            g.dialog("עוד לא", "כדי לעבוד כ" + name + " צריך " + stat + " " + j.needValue + " לפחות, ולך יש "
                    + (int) g.stat(j.needStat) + ". " + how);
            return;
        }
        g.setVar("hired." + j.id, 1);
        g.play(Lines.promotion(j.id, 0, g.facts()), Sfx.HIRED);
        g.dialog("התקבלת!", g.hey() + "מהיום " + g.t("אתה ", "את ") + name + ". משמרת היא " + Job.SHIFT_HOURS
                + " שעות, " + j.payLine(0) + ", ואפשר להתחיל " + j.window()
                + ". אוכל ושתייה במשמרת על חשבון המעסיק.");
    }

    @Override
    boolean back() {
        g.screen = new MapScreen(g);
        return true;
    }

    @Override
    boolean calm() {
        return true;
    }

    @Override
    String kind() {
        return "building";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "sb", b.id);
        Game.put(sb, "sc", Game.esc(closedLine));
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        int id = Game.intOf(m, "sb", -1);
        if (id < 0 || id >= City.B.length) return new MapScreen(g);
        // The line at the closed door is part of the visit; a reload must not pick a new one.
        return new BuildingScreen(g, id, Game.strOf(m, "sc"));
    }
}
