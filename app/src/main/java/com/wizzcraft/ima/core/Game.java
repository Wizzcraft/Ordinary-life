package com.wizzcraft.ima.core;

import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Game state and simulation. Platform-free: the Android shell (and the desktop test harness) only
 * call the public methods here and provide a {@link Gfx}.
 */
public final class Game {
    public static final float W = 360;
    /** The layout needs at least this much logical height. */
    public static final float MIN_H = 560;
    static final float HUD_H = 92;

    // ---- tuning ----
    /** 1 game hour = 3 real seconds, so a full day is a bit over a minute. */
    static final float MIN_PER_SEC = 20f;
    static final int DAYS_PER_MONTH = 10;
    /** City units per game minute. Home to the centre is 2-3 game hours on foot. */
    static final float WALK_SPEED = 3f;
    static final int RENT = 900, RENT_FINE = 100, START_MONEY = 400;
    static final int MEAL_PRICE = 30, BOTTLE_PRICE = 8, DINE_PRICE = 45, BAG_MAX = 4, AMBULANCE = 150;
    static final float MEAL_FOOD = 45, BOTTLE_WATER = 50, TAP_WATER = 40, SLEEP_PER_HOUR = 11;

    static final int IDLE = 0, WALK = 1, WORK = 2, SLEEP = 3;

    public String version = "dev";
    float h = 640;

    // ---- saved state ----
    boolean started, female, over;
    int money, day;
    float minute;
    float food, water, energy;
    int meals, bottles;
    boolean cookHired;
    int lastShiftDay, shiftsDone;
    int rentDebt, rentMissed;
    float px, py;
    int at;
    String endReason = "";

    // ---- transient ----
    Screen screen;
    private final ArrayDeque<String[]> dialogs = new ArrayDeque<>();
    private Btn dialogBtn;
    private String toast = "";
    private float toastT;
    float blink;

    public Game() {
        screen = new TitleScreen(this);
    }

    // ================= public API for the shell =================

    public void update(float dt, float logicalHeight) {
        h = Math.max(MIN_H, logicalHeight);
        blink += dt;
        if (toastT > 0) toastT -= dt;
        if (!dialogs.isEmpty()) return;
        screen.update(dt);
    }

    public void draw(Gfx c) {
        screen.draw(c);
        if (toastT > 0 && dialogs.isEmpty()) {
            float tw = c.textWidth(toast, 14) + 28;
            c.fillRoundRect((W - tw) / 2, h - 172, tw, 34, 17, 0xF0000000);
            c.text(toast, W / 2, h - 150, 14, Ui.INK, Gfx.CENTER);
        }
        if (!dialogs.isEmpty()) drawDialog(c);
    }

    public void down(float x, float y) {
        if (!dialogs.isEmpty()) {
            if (dialogBtn != null && dialogBtn.hit(x, y)) {
                dialogs.poll();
                dialogBtn = null;
            }
            return;
        }
        screen.down(x, y);
    }

    public void up(float x, float y) {
        if (dialogs.isEmpty()) screen.up(x, y);
    }

    public boolean back() {
        if (!dialogs.isEmpty()) {
            dialogs.poll();
            dialogBtn = null;
            return true;
        }
        return screen.back();
    }

    // ================= helpers =================

    /** Picks the masculine or feminine form. */
    String t(String m, String f) {
        return female ? f : m;
    }

    void dialog(String title, String body) {
        dialogs.add(new String[]{title, body});
    }

    boolean hasDialog() {
        return !dialogs.isEmpty();
    }

    void toast(String s) {
        toast = s;
        toastT = 1.8f;
    }

    int daysToRent() {
        return DAYS_PER_MONTH - ((day - 1) % DAYS_PER_MONTH);
    }

    int rentDue() {
        return RENT + rentDebt;
    }

    // ================= simulation =================

    void newGame(boolean female) {
        this.female = female;
        started = true;
        over = false;
        money = START_MONEY;
        day = 1;
        minute = 9 * 60;
        food = 70;
        water = 70;
        energy = 85;
        meals = 1;
        bottles = 1;
        cookHired = false;
        lastShiftDay = 0;
        shiftsDone = 0;
        rentDebt = 0;
        rentMissed = 0;
        endReason = "";
        City.Building home = City.B[City.HOME];
        px = home.doorX;
        py = home.doorY;
        at = City.HOME;
        dialogs.clear();
        dialog("אמא על הקו", "נו, הגעת? מצאת את הדירה? אכלת משהו? שמתי לך סנדוויץ' ובקבוק מים בתיק.");
        dialog("אמא על הקו", "שכירות משלמים כל " + DAYS_PER_MONTH + " ימים, " + Ui.nis(RENT) + ". "
                + t("תמצא עבודה ואל תעשה", "תמצאי עבודה ואל תעשי")
                + " לנו בושות. בסוף השנה אנחנו באים לבקר!");
        dialog("איך משחקים", "לוחצים על בניין והדמות הולכת אליו. הליכה לוקחת זמן, והזמן לא מחכה. "
                + "במסעדה מחפשים עובדים למטבח.");
        screen = new MapScreen(this);
    }

    /** Moves the clock forward and applies hunger, thirst and tiredness for the given activity. */
    void advance(float min, int activity) {
        if (min <= 0 || over) return;
        float hrs = min / 60f;
        float foodRate = 4f, waterRate = 6f, energyRate;
        switch (activity) {
            case SLEEP:
                foodRate = 2f;
                waterRate = 3f;
                energyRate = -SLEEP_PER_HOUR;
                break;
            case WORK:
                // Staff drink for free on shift, so thirst barely builds up at work.
                waterRate = 3f;
                energyRate = 6f;
                break;
            case WALK:
                waterRate = 7f;
                energyRate = 5f;
                break;
            default:
                energyRate = 3f;
        }
        if (activity != SLEEP && (food <= 0 || water <= 0)) energyRate *= 3;
        food = Ui.clamp(food - foodRate * hrs, 0, 100);
        water = Ui.clamp(water - waterRate * hrs, 0, 100);
        energy = Ui.clamp(energy - energyRate * hrs, 0, 100);
        minute += min;
        while (minute >= 1440 && !over) {
            minute -= 1440;
            day++;
            newDay();
        }
        if (energy <= 0 && activity != SLEEP && !over) collapse();
    }

    private void newDay() {
        if ((day - 1) % DAYS_PER_MONTH == 0) chargeRent();
        if (!over && day % 7 == 0) momCall();
    }

    private void chargeRent() {
        int due = rentDue();
        if (money >= due) {
            money -= due;
            rentDebt = 0;
            rentMissed = 0;
            dialog("שכירות", "שילמת " + Ui.nis(due) + " לבעל הבית. חודש חדש מתחיל.");
            return;
        }
        int paid = Math.max(0, money);
        money -= paid;
        rentDebt = due - paid + RENT_FINE;
        rentMissed++;
        if (rentMissed >= 2) {
            end("בעל הבית החליף מנעול. ארזת תיק וחזרת לגור אצל אמא. היא דווקא שמחה.");
        } else {
            dialog("שכירות", "לא היה לך מספיק לשכירות. החוב, כולל קנס, עבר לחודש הבא: "
                    + Ui.nis(rentDebt) + ". עוד פספוס אחד ובעל הבית זורק אותך.");
        }
    }

    private void momCall() {
        String body;
        if (food < 30 || money < 40) {
            int add = Math.min(2, BAG_MAX - meals);
            meals += add;
            body = "אכלת משהו היום? " + t("אתה נשמע לי חלש", "את נשמעת לי חלשה") + ". "
                    + (add > 0 ? "שלחתי לך קופסאות עם אוכל, שלא יגידו שאני לא דואגת."
                    : "תאכל" + t("", "י") + " את מה שיש לך בתיק!");
        } else if (!cookHired) {
            body = "נו, מצאת כבר עבודה? הבן של רותי כבר מנהל סניף. רק אומרת.";
        } else if (money > 1500) {
            body = "שמעתי ש" + t("אתה מסתדר", "את מסתדרת") + " יפה. אבא שואל אם יש שם חדר אורחים.";
        } else {
            body = "העיקר ש" + t("אתה עובד", "את עובדת") + ". אבל מטבח? חשבת אולי ללמוד משהו?";
        }
        dialog("אמא על הקו", body);
    }

    private void collapse() {
        money -= AMBULANCE;
        dialog("התעלפת", "הגוף אמר די. התעוררת בדירה בשמונה בבוקר, ואמבולנס עלה לך "
                + Ui.nis(AMBULANCE) + ". לאכול, לשתות ולישון זה לא מותרות.");
        City.Building home = City.B[City.HOME];
        px = home.doorX;
        py = home.doorY;
        at = City.HOME;
        energy = 50;
        food = Math.max(food, 25);
        water = Math.max(water, 25);
        if (minute >= 8 * 60) {
            minute = 8 * 60;
            day++;
            newDay();
        } else {
            minute = 8 * 60;
        }
        if (!over) screen = new MapScreen(this);
    }

    void end(String reason) {
        over = true;
        dialogs.clear();
        endReason = reason;
        screen = new EndScreen(this);
    }

    void eat() {
        if (meals <= 0) {
            toast("אין אוכל בתיק. קונים בסופר.");
        } else if (food > 90) {
            toast(t("אתה לא רעב עכשיו", "את לא רעבה עכשיו"));
        } else {
            meals--;
            food = Math.min(100, food + MEAL_FOOD);
            toast("אכלת ארוחה מהתיק");
        }
    }

    void drink() {
        if (bottles <= 0) {
            toast("אין מים בתיק. קונים בסופר.");
        } else if (water > 90) {
            toast(t("אתה לא צמא עכשיו", "את לא צמאה עכשיו"));
        } else {
            bottles--;
            water = Math.min(100, water + BOTTLE_WATER);
            toast("שתית בקבוק מים");
        }
    }

    // ================= shared drawing =================

    void drawHud(Gfx c) {
        c.fillRect(0, 0, W, HUD_H, Ui.PANEL);
        int month = (day - 1) / DAYS_PER_MONTH + 1;
        c.text("יום " + day + " · חודש " + month, W - 14, 25, 13, Ui.DIM, Gfx.RIGHT);
        c.text(Ui.clock(minute), W / 2, 27, 22, Ui.INK, Gfx.CENTER);
        c.text(Ui.nis(money), 14, 26, 17, money < 0 ? Ui.BAD : Ui.ACCENT, Gfx.LEFT);
        meter(c, 244, "אוכל", food, Ui.ORANGE);
        meter(c, 130, "מים", water, Ui.BLUE);
        meter(c, 16, "אנרגיה", energy, Ui.GOOD);
        int left = daysToRent();
        String when = left == 1 ? "מחר" : "בעוד " + left + " ימים";
        boolean worry = left <= 2 && money < rentDue();
        c.text("שכירות " + Ui.nis(rentDue()) + " " + when, W / 2, 81, 12, worry ? Ui.BAD : Ui.DIM, Gfx.CENTER);
    }

    private void meter(Gfx c, float x, String label, float v, int col) {
        boolean low = v < 20;
        int lc = low && ((int) (blink * 3) % 2 == 0) ? Ui.BAD : Ui.DIM;
        c.text(label, x + 100, 47, 11, lc, Gfx.RIGHT);
        c.text(String.valueOf((int) Math.ceil(v)), x, 47, 11, lc, Gfx.LEFT);
        Ui.bar(c, x, 53, 100, 8, v / 100f, low ? Ui.BAD : col);
    }

    private void drawDialog(Gfx c) {
        String[] d = dialogs.peek();
        c.fillRect(0, 0, W, h, 0xC0000000);
        float pw = 312, px0 = (W - pw) / 2, size = 15;
        int lines = Ui.wrap(c, d[1], pw - 40, size).size();
        float ph = 58 + lines * size * 1.4f + 78;
        float py0 = (h - ph) / 2;
        c.fillRoundRect(px0, py0, pw, ph, 16, Ui.PANEL);
        c.strokeRoundRect(px0, py0, pw, ph, 16, 1.5f, Ui.PANEL2);
        c.text(d[0], W / 2, py0 + 36, 20, Ui.ACCENT, Gfx.CENTER);
        Ui.paragraph(c, d[1], W / 2, py0 + 68, pw - 40, size, Ui.INK);
        dialogBtn = new Btn(0, px0 + 20, py0 + ph - 66, pw - 40, 48, "המשך", true, true);
        dialogBtn.draw(c);
    }

    // ================= save / load =================

    public String save() {
        StringBuilder sb = new StringBuilder();
        put(sb, "v", "1");
        put(sb, "started", started);
        put(sb, "female", female);
        put(sb, "over", over);
        put(sb, "money", money);
        put(sb, "day", day);
        put(sb, "minute", minute);
        put(sb, "food", food);
        put(sb, "water", water);
        put(sb, "energy", energy);
        put(sb, "meals", meals);
        put(sb, "bottles", bottles);
        put(sb, "cookHired", cookHired);
        put(sb, "lastShiftDay", lastShiftDay);
        put(sb, "shiftsDone", shiftsDone);
        put(sb, "rentDebt", rentDebt);
        put(sb, "rentMissed", rentMissed);
        put(sb, "px", px);
        put(sb, "py", py);
        put(sb, "at", at);
        return sb.toString();
    }

    private static void put(StringBuilder sb, String k, Object v) {
        sb.append(k).append('=').append(v).append(';');
    }

    public void load(String s) {
        if (s == null || s.isEmpty()) return;
        try {
            HashMap<String, String> m = new HashMap<>();
            for (String kv : s.split(";")) {
                int i = kv.indexOf('=');
                if (i > 0) m.put(kv.substring(0, i), kv.substring(i + 1));
            }
            if (!"1".equals(m.get("v")) || !Boolean.parseBoolean(m.get("started"))) return;
            boolean lOver = Boolean.parseBoolean(m.get("over"));
            if (lOver) return;
            female = Boolean.parseBoolean(m.get("female"));
            money = Integer.parseInt(m.get("money"));
            day = Integer.parseInt(m.get("day"));
            minute = Float.parseFloat(m.get("minute"));
            food = Float.parseFloat(m.get("food"));
            water = Float.parseFloat(m.get("water"));
            energy = Float.parseFloat(m.get("energy"));
            meals = Integer.parseInt(m.get("meals"));
            bottles = Integer.parseInt(m.get("bottles"));
            cookHired = Boolean.parseBoolean(m.get("cookHired"));
            lastShiftDay = Integer.parseInt(m.get("lastShiftDay"));
            shiftsDone = Integer.parseInt(m.get("shiftsDone"));
            rentDebt = Integer.parseInt(m.get("rentDebt"));
            rentMissed = Integer.parseInt(m.get("rentMissed"));
            px = Float.parseFloat(m.get("px"));
            py = Float.parseFloat(m.get("py"));
            at = Integer.parseInt(m.get("at"));
            started = true;
            over = false;
        } catch (RuntimeException e) {
            started = false;
        }
        dialogs.clear();
        screen = new TitleScreen(this);
    }
}
