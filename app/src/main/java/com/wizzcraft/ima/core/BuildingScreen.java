package com.wizzcraft.ima.core;

/**
 * Inside a building: a menu of things to do there. Time stands still while the menu is open; only
 * the actions themselves take time.
 */
final class BuildingScreen extends Screen {
    private static final int EXIT = 99, SLEEP8 = 1, NAP = 2, TAP = 3, BUY_MEAL = 10, BUY_WATER = 11,
            ASK_JOB = 20, SHIFT = 21, DINE = 22, BAG_EAT = 30, BAG_DRINK = 31;
    static final int SHIFT_FROM = 8 * 60, SHIFT_TO = 16 * 60;
    /** A full shift burns 48 energy; don't let anyone start one they can't finish. */
    static final float MIN_SHIFT_ENERGY = 55;

    private final City.Building b;
    private float y;

    BuildingScreen(Game g, int id) {
        super(g);
        b = City.B[id];
    }

    private void row(int id, String label, boolean on, boolean primary) {
        btns.add(new Btn(id, 16, y, 328, 50, label, on, primary));
        y += 58;
    }

    private void info(Gfx c, String s) {
        y = Ui.paragraph(c, s, Game.W / 2, y + 14, 312, 13, Ui.DIM) + 2;
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        boolean open = b.isOpen(g.minute);

        y = Game.HUD_H + 14;
        int ink = Ui.isDark(b.color) ? Ui.INK : Ui.DARK_INK;
        c.fillRoundRect(16, y, 328, 78, 12, b.color);
        c.text(b.name, Game.W / 2, y + 36, 26, ink, Gfx.CENTER);
        c.text(open ? b.hours() : "סגור עכשיו · " + b.hours(), Game.W / 2, y + 60, 12, ink, Gfx.CENTER);
        y += 92;

        btns.clear();
        switch (b.id) {
            case City.HOME:
                info(c, "הדירה שלך. קטנה ורחוקה מהכל, אבל זולה.");
                row(SLEEP8, "לישון 8 שעות", g.energy < 95, g.energy < 40);
                row(NAP, "תנומה של שעתיים", g.energy < 95, false);
                row(TAP, "לשתות מהברז (חינם)", g.water < 95, false);
                info(c, "שכירות " + Ui.nis(Game.RENT) + " כל " + Game.DAYS_PER_MONTH + " ימים, יורדת אוטומטית."
                        + (g.rentDebt > 0 ? " חוב פתוח: " + Ui.nis(g.rentDebt) + "." : ""));
                break;
            case City.MARKET:
                if (open) {
                    row(BUY_MEAL, "ארוחה ארוזה · " + Ui.nis(Game.MEAL_PRICE), g.money >= Game.MEAL_PRICE && g.meals < Game.BAG_MAX, false);
                    row(BUY_WATER, "בקבוק מים · " + Ui.nis(Game.BOTTLE_PRICE), g.money >= Game.BOTTLE_PRICE && g.bottles < Game.BAG_MAX, false);
                    info(c, "בתיק: " + Ui.count(g.meals, "ארוחה אחת", "ארוחות") + ", "
                            + Ui.count(g.bottles, "בקבוק אחד", "בקבוקים") + ". נכנסים עד " + Game.BAG_MAX + " מכל סוג.");
                } else {
                    info(c, "הסופר סגור. אפשר לחכות ברחוב או לחזור מחר.");
                }
                break;
            case City.RESTAURANT:
                if (open) {
                    if (!g.cookHired) {
                        row(ASK_JOB, "לבקש עבודה במטבח", true, true);
                    } else {
                        String why = shiftBlock();
                        row(SHIFT, "להתחיל משמרת (8 שעות)", why == null, true);
                        if (why != null) info(c, why);
                    }
                    row(DINE, "לאכול כאן · " + Ui.nis(Game.DINE_PRICE), g.money >= Game.DINE_PRICE && g.food < 90, false);
                    info(c, "שכר טבח: " + Ui.nis(CookGame.BASE) + " לשעה, לפי איך שהמנות יוצאות. "
                            + "אוכל ושתייה במשמרת על חשבון המסעדה.");
                } else {
                    info(c, "המסעדה סגורה.");
                }
                break;
            default:
                info(c, soon(b.id));
        }

        // The bag is reachable from inside any building.
        btns.add(new Btn(BAG_EAT, 184, h - 124, 160, 46, "לאכול (" + g.meals + ")", true, false));
        btns.add(new Btn(BAG_DRINK, 16, h - 124, 160, 46, "לשתות (" + g.bottles + ")", true, false));
        btns.add(new Btn(EXIT, 16, h - 66, 328, 50, "חזרה לרחוב", true, false));
        drawButtons(c);
        g.drawHud(c);
    }

    /** Why a shift can't start right now, or null if it can. */
    private String shiftBlock() {
        if (g.lastShiftDay == g.day) return "כבר עבדת היום. מחר יש משמרת חדשה.";
        if (g.minute < SHIFT_FROM || g.minute > SHIFT_TO) {
            return "משמרת אפשר להתחיל בין " + Ui.clock(SHIFT_FROM) + " ל-" + Ui.clock(SHIFT_TO) + ".";
        }
        if (g.energy < MIN_SHIFT_ENERGY) return "אין לך כוח למשמרת שלמה. קודם לישון.";
        return null;
    }

    private static String soon(int id) {
        switch (id) {
            case City.SCHOOL:
                return "בקרוב: לימודים למקצועות כמו רופא, עורך דין ומפתח תוכנה.";
            case City.CASINO:
                return "בקרוב: בלאקג'ק, רולטה, מכונות מזל ופוקר.";
            case City.BAKERY:
                return "בקרוב: עבודה כאופה, משמרות מוקדמות בבוקר.";
            case City.BAR:
                return "בקרוב: עבודה כברמן ובילויי ערב.";
            case City.GAS:
                return "בקרוב: עבודה כמתדלק, ובהמשך תדלוק לרכב.";
            case City.ALLEY:
                return "בקרוב: כסף מהיר, עם כל הסיכון שבא איתו.";
            default:
                return "בקרוב.";
        }
    }

    @Override
    void down(float x, float y) {
        Btn hit = hit(x, y);
        if (hit == null) return;
        switch (hit.id) {
            case EXIT:
                g.screen = new MapScreen(g);
                break;
            case BAG_EAT:
                g.eat();
                break;
            case BAG_DRINK:
                g.drink();
                break;
            case SLEEP8:
                if (g.energy >= 95) break;
                g.screen = new SleepScreen(g, 8);
                break;
            case NAP:
                if (g.energy >= 95) break;
                g.screen = new SleepScreen(g, 2);
                break;
            case TAP:
                if (g.water >= 95) break;
                g.water = Math.min(100, g.water + Game.TAP_WATER);
                g.toast("שתית מים מהברז");
                break;
            case BUY_MEAL:
                if (g.money < Game.MEAL_PRICE || g.meals >= Game.BAG_MAX) break;
                g.money -= Game.MEAL_PRICE;
                g.meals++;
                g.toast("ארוחה נכנסה לתיק");
                break;
            case BUY_WATER:
                if (g.money < Game.BOTTLE_PRICE || g.bottles >= Game.BAG_MAX) break;
                g.money -= Game.BOTTLE_PRICE;
                g.bottles++;
                g.toast("בקבוק נכנס לתיק");
                break;
            case ASK_JOB:
                g.cookHired = true;
                g.dialog("התקבלת!", g.hey() + "מהיום " + g.t("אתה טבח", "את טבחית") + " במסעדה. משמרת היא 8 שעות, "
                        + Ui.nis(CookGame.BASE) + " לשעה, ואפשר להתחיל בין " + Ui.clock(SHIFT_FROM)
                        + " ל-" + Ui.clock(SHIFT_TO) + ". אוכל ושתייה במשמרת עלינו.");
                break;
            case SHIFT:
                if (!g.cookHired || shiftBlock() != null) break;
                g.screen = new CookGame(g);
                break;
            case DINE:
                if (g.money < Game.DINE_PRICE || g.food >= 90) break;
                g.money -= Game.DINE_PRICE;
                g.food = Math.min(100, g.food + 70);
                g.water = Math.min(100, g.water + 20);
                g.toast("ארוחה חמה. זה היה טוב.");
                g.advance(30, Game.IDLE);
                break;
            default:
        }
    }

    @Override
    boolean back() {
        g.screen = new MapScreen(g);
        return true;
    }

    @Override
    String kind() {
        return "building";
    }

    @Override
    void save(StringBuilder sb) {
        Game.put(sb, "sb", b.id);
    }
}
