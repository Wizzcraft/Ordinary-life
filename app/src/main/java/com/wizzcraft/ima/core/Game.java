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
    /** What the employer's food and drink give back per hour of work. */
    static final float WORK_FOOD_PER_HOUR = 4, WORK_WATER_PER_HOUR = 5;
    static final float MEAL_FOOD = 45, BOTTLE_WATER = 50, TAP_WATER = 40, SLEEP_PER_HOUR = 11;

    static final int IDLE = 0, WALK = 1, WORK = 2, SLEEP = 3;

    public String version = "dev";
    float h = 640;

    // ---- saved state ----
    boolean started, female, over;
    /** The player's name as typed at the start; empty in saves from before names existed. */
    String name = "";
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

    /** "דנה, " when the player has a name, so a sentence can open by addressing them. */
    String hey() {
        return name.isEmpty() ? "" : name + ", ";
    }

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

    void newGame(boolean female, String name) {
        this.female = female;
        this.name = name;
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
        dialog("אמא על הקו", "נו, " + hey() + "הגעת? מצאת את הדירה? אכלת משהו? שמתי לך סנדוויץ' ובקבוק מים בתיק.");
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
                // The employer feeds you on shift: food and water go up instead of down.
                foodRate = -WORK_FOOD_PER_HOUR;
                waterRate = -WORK_WATER_PER_HOUR;
                energyRate = 6f;
                break;
            case WALK:
                waterRate = 7f;
                energyRate = 5f;
                break;
            default:
                energyRate = 3f;
        }
        if (activity != SLEEP && activity != WORK && (food <= 0 || water <= 0)) energyRate *= 3;
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
            body = hey() + "אכלת משהו היום? " + t("אתה נשמע לי חלש", "את נשמעת לי חלשה") + ". "
                    + (add > 0 ? "שלחתי לך קופסאות עם אוכל, שלא יגידו שאני לא דואגת."
                    : "תאכל" + t("", "י") + " את מה שיש לך בתיק!");
        } else if (!cookHired) {
            body = "נו, " + hey() + "מצאת כבר עבודה? הבן של רותי כבר מנהל סניף. רק אומרת.";
        } else if (money > 1500) {
            body = hey() + "שמעתי ש" + t("אתה מסתדר", "את מסתדרת") + " יפה. אבא שואל אם יש שם חדר אורחים.";
        } else {
            body = hey() + "העיקר ש" + t("אתה עובד", "את עובדת") + ". אבל מטבח? חשבת אולי ללמוד משהו?";
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

    /** The shell calls this when the app goes to the background, just before saving. */
    public void onAppPause() {
        screen.onAppPause();
    }

    /**
     * Serializes everything, including the screen the player is on and any waiting messages, so the
     * app reopens at exactly the same point.
     */
    public String save() {
        StringBuilder sb = new StringBuilder();
        put(sb, "v", "2");
        put(sb, "started", started);
        put(sb, "female", female);
        put(sb, "name", esc(name));
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
        put(sb, "endReason", esc(endReason));
        StringBuilder ds = new StringBuilder();
        for (String[] d : dialogs) ds.append(esc(d[0])).append('|').append(esc(d[1])).append('|');
        put(sb, "dialogs", ds);
        put(sb, "scr", screen.kind());
        screen.save(sb);
        return sb.toString();
    }

    static void put(StringBuilder sb, String k, Object v) {
        sb.append(k).append('=').append(v).append(';');
    }

    /** Escapes the characters the save format uses as separators. */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' || ch == ';' || ch == '=' || ch == '|' || ch == '\n') {
                sb.append('%').append(Character.forDigit(ch >> 4, 16)).append(Character.forDigit(ch & 15, 16));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static String unesc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' && i + 2 < s.length()) {
                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static int intOf(HashMap<String, String> m, String k, int def) {
        String v = m.get(k);
        return v == null ? def : Integer.parseInt(v);
    }

    static float floatOf(HashMap<String, String> m, String k, float def) {
        String v = m.get(k);
        if (v == null) return def;
        float f = Float.parseFloat(v);
        if (Float.isNaN(f) || Float.isInfinite(f)) throw new NumberFormatException(k);
        return f;
    }

    public void load(String s) {
        if (s == null || s.isEmpty()) return;
        try {
            HashMap<String, String> m = new HashMap<>();
            for (String kv : s.split(";")) {
                int i = kv.indexOf('=');
                if (i > 0) m.put(kv.substring(0, i), kv.substring(i + 1));
            }
            String v = m.get("v");
            if (!"1".equals(v) && !"2".equals(v)) return;
            if (!Boolean.parseBoolean(m.get("started"))) return;
            female = Boolean.parseBoolean(m.get("female"));
            name = unesc(m.getOrDefault("name", ""));
            if (name.length() > NameScreen.MAX_LEN) name = name.substring(0, NameScreen.MAX_LEN);
            money = intOf(m, "money", START_MONEY);
            day = Math.max(1, intOf(m, "day", 1));
            minute = Ui.clamp(floatOf(m, "minute", 540), 0, 1439.9f);
            food = Ui.clamp(floatOf(m, "food", 70), 0, 100);
            water = Ui.clamp(floatOf(m, "water", 70), 0, 100);
            energy = Ui.clamp(floatOf(m, "energy", 70), 0, 100);
            meals = (int) Ui.clamp(intOf(m, "meals", 0), 0, BAG_MAX);
            bottles = (int) Ui.clamp(intOf(m, "bottles", 0), 0, BAG_MAX);
            cookHired = Boolean.parseBoolean(m.get("cookHired"));
            lastShiftDay = intOf(m, "lastShiftDay", 0);
            shiftsDone = intOf(m, "shiftsDone", 0);
            rentDebt = Math.max(0, intOf(m, "rentDebt", 0));
            rentMissed = Math.max(0, intOf(m, "rentMissed", 0));
            City.Building home = City.B[City.HOME];
            px = Ui.clamp(floatOf(m, "px", home.doorX), 0, City.W);
            py = Ui.clamp(floatOf(m, "py", home.doorY), 0, City.H);
            at = intOf(m, "at", -1);
            if (at < -1 || at >= City.B.length) at = -1;
            over = Boolean.parseBoolean(m.get("over"));
            endReason = unesc(m.getOrDefault("endReason", ""));
            started = true;
            dialogs.clear();
            dialogBtn = null;
            String[] parts = m.getOrDefault("dialogs", "").split("\\|");
            for (int i = 0; i + 1 < parts.length; i += 2) {
                dialogs.add(new String[]{unesc(parts[i]), unesc(parts[i + 1])});
            }
            screen = over ? new EndScreen(this) : restoreScreen(m);
        } catch (RuntimeException e) {
            // A damaged save must never crash the app: start clean instead.
            started = false;
            over = false;
            dialogs.clear();
            dialogBtn = null;
            screen = new TitleScreen(this);
        }
    }

    private Screen restoreScreen(HashMap<String, String> m) {
        String scr = m.getOrDefault("scr", "map");
        switch (scr) {
            case "title":
                return new TitleScreen(this);
            case "name": {
                NameScreen ns = new NameScreen(this, Boolean.parseBoolean(m.get("nf")));
                String typed = unesc(m.getOrDefault("nn", ""));
                ns.name = typed.length() > NameScreen.MAX_LEN ? typed.substring(0, NameScreen.MAX_LEN) : typed;
                return ns;
            }
            case "building": {
                int id = intOf(m, "sb", -1);
                if (id < 0 || id >= City.B.length) return new MapScreen(this);
                return new BuildingScreen(this, id);
            }
            case "sleep": {
                SleepScreen sl = new SleepScreen(this, 0);
                sl.left = Ui.clamp(floatOf(m, "sl", 0), 0, 24 * 60);
                return sl;
            }
            case "cook":
                return CookGame.restore(this, m);
            default: {
                MapScreen ms = new MapScreen(this);
                ms.restore(m);
                return ms;
            }
        }
    }
}
