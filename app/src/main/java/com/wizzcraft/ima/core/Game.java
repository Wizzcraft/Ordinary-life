package com.wizzcraft.ima.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * Game state and simulation. Platform-free: the Android shell (and the desktop test harness) only
 * call the public methods here and provide a {@link Gfx}.
 */
public final class Game {
    public static final float W = 360;
    /** The layout needs at least this much logical height. */
    public static final float MIN_H = 560;
    static final float HUD_H = 92;
    /** Every hosting screen gives its Mini the area from PLAY_TOP down to PLAY_FOOT above the bottom edge. */
    static final float PLAY_TOP = HUD_H + 74, PLAY_FOOT = 96;

    // ---- tuning ----
    /** 1 game hour = 3 real seconds, so a full day is a bit over a minute. */
    static final float MIN_PER_SEC = 20f;
    static final int DAYS_PER_MONTH = 10;
    /** City units per game minute. Home to the centre is 2-3 game hours on foot. */
    static final float WALK_SPEED = 3f;
    static final int RENT = Tuning.HOME_RENT[0], RENT_FINE = Tuning.RENT_FINE, START_MONEY = Tuning.START_MONEY;
    static final int MEAL_PRICE = Tuning.MEAL_PRICE, BOTTLE_PRICE = Tuning.BOTTLE_PRICE,
            DINE_PRICE = Tuning.DINE_PRICE, BAG_MAX = Tuning.BAG_MAX, AMBULANCE = Tuning.AMBULANCE;
    /** What the employer's food and drink give back per hour of work, and the energy that hour costs. */
    static final float WORK_FOOD_PER_HOUR = 4, WORK_WATER_PER_HOUR = 5, WORK_ENERGY_PER_HOUR = 6;
    static final float MEAL_FOOD = 45, BOTTLE_WATER = 50, TAP_WATER = 40, SLEEP_PER_HOUR = 11;
    /** Stats at the start of a game. */
    static final float START_FIT = 5, START_CHA = 5, START_EDU = 0;
    /** Fitness gained per game hour on foot, and how much faster each fitness point makes the walk. */
    static final float FIT_PER_WALK_HOUR = 0.25f, FIT_WALK_BONUS = 1 / 200f;
    /** Below this energy the player is tired and minigames arrive harder. */
    static final float TIRED = 30;
    /** A need that drops below this on the street gets a warning toast. */
    static final float LOW_NEED = 25;
    /** Mom phones every this many days. */
    static final int MOM_EVERY_DAYS = 7;
    /** The day's story beats and mom's call arrive from this hour on, once the player is up and about. */
    static final int MORNING = 6 * 60;
    /** Chance that a finished walk ends in a street encounter. */
    static final float STREET_CHANCE = 0.12f;
    /** How often mom has something to say about a small moment, and what she calls rich and broke. */
    static final float MOM_TOAST_CHANCE = 0.35f;
    static final int RICH = 1500, BROKE = 50;
    /** Waking-up hour after a collapse or a night in custody. */
    static final int WAKE_MINUTE = 8 * 60;
    /** A sleep at least this long ends with the day summary. */
    static final int SUMMARY_SLEEP_HOURS = 6;

    static final int IDLE = 0, WALK = 1, WORK = 2, SLEEP = 3;
    /** The largest minute of the day that is still below 1440. */
    private static final float LAST_MINUTE = Math.nextDown(1440f);
    private static final int SOUND_CAP = 16;
    private static final String[] STAT_KEYS = {"fit", "cha", "edu"};
    /** Variables that belong to the player rather than to one playthrough: a new game keeps them. */
    private static final String[] KEPT = {"gems", "opt.", "own.", "look.", "store."};

    public String version = "dev";
    float h = 640;

    // ---- saved state ----
    boolean started, female, over;
    /** The player's name as typed at the start; empty in saves from before names existed. */
    String name = "";
    int money, day = 1;
    float minute;
    float food, water, energy;
    int meals, bottles;
    int lastShiftDay, shiftsDone;
    int rentDebt, rentMissed;
    float px, py;
    int at = -1;
    /** The end screen's text. An empty title means the plain "game over"; the finale sets its own. */
    String endTitle = "", endReason = "";
    /** The finale is being read; the game ends when its last message is closed. */
    boolean endPending;
    /** Persistent variables: everything that is not one of the fields above. Sorted, so saves are stable. */
    private final TreeMap<String, Float> vars = new TreeMap<>();

    // ---- transient ----
    Screen screen;
    final Fx fx = new Fx();
    final Host host = new GameHost(this);
    final Random rnd = new Random();
    /** Writes to variables with a malformed key; the harness expects this to stay 0. */
    int badKeys;
    private final ArrayDeque<Msg> dialogs = new ArrayDeque<>();
    /** Buttons of the message on screen. Rebuilt on every draw; hit-tested on touch. */
    final ArrayList<Btn> dialogBtns = new ArrayList<>();
    private final int[] sounds = new int[SOUND_CAP];
    private int soundHead, soundCount;
    private String toast = "";
    private float toastT;
    float blink;

    /** One message waiting in the dialog queue: a plain text or a resolved {@link Beat}. */
    private static final class Msg {
        String id = "", title = "", body = "";
        String[] options = {}, replies = {}, effects = {};
        /** Sound played when the message appears; -1 for none. */
        int sfx = -1;
        /** True once the message appeared and its on-show effects were applied. */
        boolean shown;
    }

    public Game() {
        screen = new TitleScreen(this);
    }

    // ================= public API for the shell =================

    public void update(float dt, float logicalHeight) {
        h = Math.max(MIN_H, logicalHeight);
        if (!(dt > 0)) dt = 0;
        blink += dt;
        fx.update(dt);
        if (toastT > 0) toastT -= dt;
        showFront();
        if (!dialogs.isEmpty()) return;
        if (endPending) {
            finishYear();
            return;
        }
        if (started && !over && screen.calm() && minute >= MORNING && var("eng.morning") < day) {
            morning();
            if (!dialogs.isEmpty() || endPending) return;
        }
        screen.update(dt);
    }

    public void draw(Gfx c) {
        float sx = fx.shakeX(), sy = fx.shakeY();
        boolean shaking = sx != 0 || sy != 0;
        if (shaking) c.translate(sx, sy);
        screen.draw(c);
        fx.draw(c);
        if (toastT > 0 && dialogs.isEmpty()) {
            float size = Ui.fit(c, toast, W - 44, 14);
            float tw = c.textWidth(toast, size) + 28;
            c.fillRoundRect((W - tw) / 2, h - 172, tw, 34, 17, 0xF0000000);
            c.text(toast, W / 2, h - 150, size, Ui.INK, Gfx.CENTER);
        }
        if (shaking) c.translate(-sx, -sy);
        if (!dialogs.isEmpty()) drawDialog(c);
    }

    public void down(float x, float y) {
        if (!dialogs.isEmpty()) {
            for (Btn b : dialogBtns) {
                if (b.hit(x, y)) {
                    pick(b.id);
                    return;
                }
            }
            return;
        }
        screen.down(x, y);
    }

    /** The finger moved while down (a drag). */
    public void move(float x, float y) {
        if (dialogs.isEmpty()) screen.move(x, y);
    }

    public void up(float x, float y) {
        if (dialogs.isEmpty()) screen.up(x, y);
    }

    public boolean back() {
        if (!dialogs.isEmpty()) {
            // A plain message can be closed with back; a question has to be answered.
            if (dialogs.peek().options.length == 0) pick(0);
            return true;
        }
        return screen.back();
    }

    /** The next queued sound effect ({@link Sfx} id), or -1 when there is none. Called by the shell every frame. */
    public int pollSound() {
        if (soundCount == 0) return -1;
        int id = sounds[soundHead];
        soundHead = (soundHead + 1) % SOUND_CAP;
        soundCount--;
        return id;
    }

    /** Which music should play: -1 none (switched off, or outside the game), 0 the day track, 1 the night track. */
    public int musicTrack() {
        if (!optOn("opt.music") || !started || over || !screen.inGame()) return -1;
        return isNight() ? 1 : 0;
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

    /** Replaces "{name}". Without a name (saves from before names) the word goes together with its comma. */
    String fill(String s) {
        if (s == null) return "";
        if (s.indexOf("{name}") < 0) return s;
        if (!name.isEmpty()) return s.replace("{name}", name);
        return s.replace("{name}, ", "").replace("{name}! ", "").replace("{name}. ", "").replace(", {name}", "")
                .replace(" {name}", "").replace("{name} ", "").replace("{name}", "");
    }

    void toast(String s) {
        toast = s == null ? "" : s;
        // Longer lines stay up a little longer.
        toastT = toast.isEmpty() ? 0 : 1.8f + Math.min(1.5f, toast.length() * 0.03f);
    }

    /**
     * Gives mom the chance to say something about a small moment (situations are the ones
     * {@link MomLines#toast} knows: ate, drank, bought, tired, rich, broke, night, won, lost).
     * Returns false when she stays quiet, so the caller can show its own toast.
     */
    boolean momToast(String situation) {
        if (rnd.nextFloat() >= MOM_TOAST_CHANCE) return false;
        String s = fill(MomLines.toast(situation, facts(), rnd));
        if (s.isEmpty()) return false;
        toast("אמא: " + s);
        return true;
    }

    int daysToRent() {
        return DAYS_PER_MONTH - ((day - 1) % DAYS_PER_MONTH);
    }

    int rentDue() {
        return RENT + rentDebt;
    }

    boolean isNight() {
        return minute >= 19 * 60 || minute < 6 * 60;
    }

    float walkSpeed() {
        return WALK_SPEED * (1 + stat(Host.FIT) * FIT_WALK_BONUS);
    }

    // ================= persistent variables =================

    static boolean validKey(String k) {
        if (k == null || k.isEmpty() || k.length() > 64) return false;
        for (int i = 0; i < k.length(); i++) {
            char ch = k.charAt(i);
            if (!(ch >= 'a' && ch <= 'z') && !(ch >= '0' && ch <= '9') && ch != '_' && ch != '.') return false;
        }
        return true;
    }

    /** Names that are computed from the clock and can only be read. */
    private static boolean computed(String key) {
        return key.equals("day") || key.equals("minute");
    }

    /** Variables with a fixed range are clamped on every write, whoever writes them. */
    private static float ranged(String key, float v) {
        switch (key) {
            case "fit":
            case "cha":
            case "edu":
            case "heat":
            case "mom.trust":
            case "mom.worry":
                return Ui.clamp(v, 0, 100);
            case "gems":
                return Math.max(0, v);
            default:
                return v;
        }
    }

    private static boolean kept(String key) {
        for (String k : KEPT) if (key.equals(k) || (k.endsWith(".") && key.startsWith(k))) return true;
        return false;
    }

    /** A persistent variable; 0 when it was never set. "day" and "minute" read the clock. */
    float var(String key) {
        if (key == null) return 0;
        Float v = vars.get(key);
        if (v != null) return v;
        if (key.equals("day")) return day;
        if (key.equals("minute")) return minute;
        return 0;
    }

    void setVar(String key, float value) {
        if (!validKey(key)) {
            badKeys++;
            return;
        }
        if (computed(key) || Float.isNaN(value) || Float.isInfinite(value)) return;
        vars.put(key, ranged(key, value));
    }

    void addVar(String key, float delta) {
        setVar(key, var(key) + delta);
    }

    boolean flag(String key) {
        return var(key) != 0;
    }

    /** True when the variable was ever written, even to 0. */
    boolean hasVar(String key) {
        return vars.containsKey(key);
    }

    /** Options are on until somebody switches them off: an unset option counts as on. */
    boolean optOn(String key) {
        Float v = vars.get(key);
        return v == null || v != 0;
    }

    /** The names of all variables that start with the prefix. */
    List<String> varKeys(String prefix) {
        List<String> out = new ArrayList<>();
        for (String k : vars.keySet()) if (k.startsWith(prefix)) out.add(k);
        return out;
    }

    /** A character stat (Host.FIT / CHA / EDU), 0..100. */
    float stat(int which) {
        return which < 0 || which >= STAT_KEYS.length ? 0 : var(STAT_KEYS[which]);
    }

    void train(int which, float amount) {
        if (which >= 0 && which < STAT_KEYS.length) addVar(STAT_KEYS[which], amount);
    }

    boolean hired(int jobId) {
        return flag("hired." + jobId);
    }

    int xp(int jobId) {
        return (int) var("xp." + jobId);
    }

    /** 0..2, from the experience collected in that job. */
    int rank(int jobId) {
        int xp = xp(jobId), r = 0;
        while (r + 1 < Job.RANK_XP.length && xp >= Job.RANK_XP[r + 1]) r++;
        return r;
    }

    // ================= money =================

    /** Money comes in only through here, so the day's books stay right. */
    void earn(int n) {
        if (n <= 0) return;
        money = (int) Math.min(1_000_000_000L, (long) money + n);
        addVar("day.earned", n);
    }

    /** Money goes out only through here. A forced charge may push the wallet below zero. */
    void spend(int n) {
        if (n <= 0) return;
        money = (int) Math.max(-1_000_000_000L, (long) money - n);
        addVar("day.spent", n);
    }

    /** Pays a price the player chooses to pay: false, and nothing changes, when they can't afford it. */
    boolean buy(int price) {
        if (price < 0 || money < price) return false;
        spend(price);
        return true;
    }

    // ================= sound =================

    /** Queues a sound effect for the shell. Dropped when sounds are switched off or the queue is full. */
    void sfx(int id) {
        if (id < 0 || id >= Sfx.COUNT || soundCount >= SOUND_CAP || !optOn("opt.sfx")) return;
        sounds[(soundHead + soundCount) % SOUND_CAP] = id;
        soundCount++;
    }

    // ================= dialogs and beats =================

    void dialog(String title, String body) {
        dialog(title, body, -1);
    }

    /** A plain message with one "continue" button; sfx is played when it appears (-1 for none). */
    void dialog(String title, String body, int sfx) {
        Msg d = new Msg();
        d.title = title == null ? "" : title;
        d.body = body == null ? "" : body;
        d.sfx = sfx;
        dialogs.add(d);
    }

    /** Plays a scripted moment. A beat whose speaker is "on the line" rings the phone. */
    void play(Beat b) {
        play(b, b != null && b.speaker != null && b.speaker.contains("על הקו") ? Sfx.PHONE : -1);
    }

    void play(Beat b, int sfx) {
        if (b == null || over) return;
        Msg d = new Msg();
        d.id = b.id == null ? "" : b.id;
        d.title = fill(b.speaker);
        d.body = fill(female ? b.f : b.m);
        d.sfx = sfx;
        int n = Math.min(3, b.options.length);
        d.options = new String[n];
        d.replies = new String[n];
        // Without options the single effect belongs to the moment the message appears.
        d.effects = new String[Math.max(1, n)];
        d.effects[0] = at(b.effects, 0);
        for (int i = 0; i < n; i++) {
            d.options[i] = fill(b.options[i]);
            d.replies[i] = fill(at(female ? b.repliesF : b.repliesM, i));
            d.effects[i] = at(b.effects, i);
        }
        dialogs.add(d);
    }

    void play(List<Beat> beats) {
        if (beats == null) return;
        for (Beat b : beats) play(b);
    }

    private static String at(String[] a, int i) {
        return a == null || i >= a.length || a[i] == null ? "" : a[i];
    }

    boolean hasDialog() {
        return !dialogs.isEmpty();
    }

    /** What has to happen once when a message reaches the screen: its sound, its seen flag, its effect. */
    private void showFront() {
        Msg d = dialogs.peek();
        if (d == null || d.shown) return;
        d.shown = true;
        sfx(d.sfx);
        if (!d.id.isEmpty()) setVar("seen." + d.id, 1);
        if (d.options.length == 0) apply(d.effects.length > 0 ? d.effects[0] : "");
    }

    /** The player pressed button i of the message on screen. */
    private void pick(int i) {
        Msg d = dialogs.peek();
        if (d == null) return;
        // A message is answered only after it appeared, so its on-show effects are never skipped.
        if (!d.shown) showFront();
        dialogs.poll();
        dialogBtns.clear();
        sfx(Sfx.TAP);
        if (i < 0 || i >= d.options.length) return;
        apply(d.effects[i]);
        if (!d.replies[i].isEmpty()) {
            Msg reply = new Msg();
            reply.title = d.title;
            reply.body = d.replies[i];
            dialogs.addFirst(reply);
        }
    }

    /**
     * Applies a Beat effects string: assignments separated by ';', each "key=n", "key+=n" or
     * "key-=n". Anything malformed is skipped; a story line must never crash the game.
     */
    void apply(String effects) {
        if (effects == null || effects.isEmpty()) return;
        for (String part : effects.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            char op = part.charAt(eq - 1);
            boolean relative = op == '+' || op == '-';
            String key = part.substring(0, relative ? eq - 1 : eq).trim();
            float n;
            try {
                n = Float.parseFloat(part.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (Float.isNaN(n) || Float.isInfinite(n)) continue;
            if (op == '-') n = -n;
            switch (key) {
                case "money": {
                    int delta = Math.round(relative ? n : n - money);
                    if (delta > 0) earn(delta);
                    else spend(-delta);
                    break;
                }
                case "meals":
                    meals = (int) Ui.clamp((relative ? meals : 0) + n, 0, BAG_MAX);
                    break;
                case "bottles":
                    bottles = (int) Ui.clamp((relative ? bottles : 0) + n, 0, BAG_MAX);
                    break;
                case "food":
                    food = Ui.clamp((relative ? food : 0) + n, 0, 100);
                    break;
                case "water":
                    water = Ui.clamp((relative ? water : 0) + n, 0, 100);
                    break;
                case "energy":
                    energy = Ui.clamp((relative ? energy : 0) + n, 0, 100);
                    break;
                default:
                    setVar(key, (relative ? var(key) : 0) + n);
            }
        }
    }

    // ================= facts for the narrative code =================

    /** A snapshot of the player's situation for the story, mom and the street lines. */
    Facts facts() {
        Facts f = new Facts();
        f.female = female;
        f.name = name;
        f.day = day;
        f.month = (day - 1) / DAYS_PER_MONTH + 1;
        f.dayOfMonth = (day - 1) % DAYS_PER_MONTH + 1;
        f.daysToRent = daysToRent();
        f.minute = minute;
        f.money = money;
        f.rentDue = rentDue();
        f.rentDebt = rentDebt;
        f.rentMissed = rentMissed;
        f.shiftsDone = shiftsDone;
        f.food = food;
        f.water = water;
        f.energy = energy;
        float best = -1;
        for (Job j : Job.ALL) {
            if (!hired(j.id)) continue;
            f.jobsHeld++;
            float hourly = j.pay * Job.RANK_PAY[rank(j.id)];
            if (hourly > best) {
                best = hourly;
                f.jobId = j.id;
                f.jobRank = rank(j.id);
                f.jobName = j.name(female);
            }
        }
        f.fit = stat(Host.FIT);
        f.cha = stat(Host.CHA);
        f.edu = stat(Host.EDU);
        f.heat = var("heat");
        f.record = flag("record");
        f.earnedYesterday = (int) var("yday.earned");
        f.spentYesterday = (int) var("yday.spent");
        f.vars = new HashMap<>(vars);
        return f;
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
        lastShiftDay = 0;
        shiftsDone = 0;
        rentDebt = 0;
        rentMissed = 0;
        endTitle = "";
        endReason = "";
        endPending = false;
        goHome();
        dialogs.clear();
        dialogBtns.clear();
        fx.clear();
        toastT = 0;
        // What the player owns and their settings outlive a playthrough; everything else starts over.
        vars.keySet().removeIf(k -> !kept(k));
        setVar("fit", START_FIT);
        setVar("cha", START_CHA);
        setVar("edu", START_EDU);
        setVar("mom.trust", 50);
        setVar("mom.worry", 50);
        setVar("eng.morning", day);
        markSummary();
        play(Story.intro(facts()));
        screen = new MapScreen(this);
    }

    private void goHome() {
        City.Building home = City.B[City.HOME];
        px = home.doorX;
        py = home.doorY;
        at = City.HOME;
    }

    /** Moves the clock forward and applies hunger, thirst and tiredness for the given activity. */
    void advance(float min, int activity) {
        if (min <= 0 || over || endPending) return;
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
                energyRate = WORK_ENERGY_PER_HOUR;
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
        minute = Ui.clamp(minute, 0, LAST_MINUTE);
        if (energy <= 0 && activity != SLEEP && !over && !endPending) collapse();
    }

    /** Midnight: the street cools down and the landlord collects. */
    private void newDay() {
        Crime.newDay(this);
        // On the day of the visit the story takes over; nobody collects rent from a finished year.
        if (day >= Story.FINAL_DAY) return;
        if ((day - 1) % DAYS_PER_MONTH == 0) chargeRent();
    }

    /** The first calm moment of the morning: mom's weekly call, the day's story beats, or the finale. */
    private void morning() {
        setVar("eng.morning", day);
        if (day >= Story.FINAL_DAY && !flag("eng.finale")) {
            finale();
            return;
        }
        int week = day / MOM_EVERY_DAYS;
        if (week > var("eng.week")) {
            setVar("eng.week", week);
            play(MomLines.weekly(facts(), rnd), Sfx.PHONE);
        }
        play(Story.onNewDay(facts(), rnd));
    }

    /** The parents' visit: every beat but the last is read as a message, the last one is the end screen. */
    private void finale() {
        setVar("eng.finale", 1);
        List<Beat> beats = Story.finale(facts());
        int n = beats == null ? 0 : beats.size();
        for (int i = 0; i < n - 1; i++) play(beats.get(i));
        Beat last = n == 0 ? null : beats.get(n - 1);
        endTitle = last == null ? "" : fill(last.speaker);
        endReason = last == null ? "" : fill(female ? last.f : last.m);
        if (endTitle.isEmpty()) endTitle = "השנה נגמרה";
        if (last != null) {
            if (last.id != null && !last.id.isEmpty()) setVar("seen." + last.id, 1);
            apply(at(last.effects, 0));
        }
        endPending = true;
    }

    private void finishYear() {
        endPending = false;
        over = true;
        dialogs.clear();
        dialogBtns.clear();
        screen = new EndScreen(this);
    }

    private void chargeRent() {
        int due = rentDue();
        if (buy(due)) {
            rentDebt = 0;
            rentMissed = 0;
            dialog("שכירות", "שילמת " + Ui.nis(due) + " לבעל הבית. חודש חדש מתחיל.", Sfx.RENT);
            return;
        }
        int paid = Math.max(0, money);
        spend(paid);
        rentDebt = due - paid + RENT_FINE;
        rentMissed++;
        if (rentMissed >= 2) {
            end("בעל הבית החליף מנעול. ארזת תיק וחזרת לגור אצל אמא. היא דווקא שמחה.");
        } else {
            dialog("שכירות", "לא היה לך מספיק לשכירות. החוב, כולל קנס, עבר לחודש הבא: "
                    + Ui.nis(rentDebt) + ". עוד פספוס אחד ובעל הבית זורק אותך.", Sfx.RENT);
        }
    }

    private void collapse() {
        spend(AMBULANCE);
        addVar("collapses", 1);
        dialog("התעלפת", "הגוף אמר די. התעוררת בדירה בשמונה בבוקר, ואמבולנס עלה לך "
                + Ui.nis(AMBULANCE) + ". לאכול, לשתות ולישון זה לא מותרות.", Sfx.COLLAPSE);
        goHome();
        energy = 50;
        food = Math.max(food, 25);
        water = Math.max(water, 25);
        skipToMorning();
        if (!over) screen = new MapScreen(this);
    }

    /** Jumps to the waking-up hour: today if it is still night, otherwise tomorrow with all a new day brings. */
    void skipToMorning() {
        if (minute >= WAKE_MINUTE) {
            minute = WAKE_MINUTE;
            day++;
            newDay();
        } else {
            minute = WAKE_MINUTE;
        }
    }

    /** Wakes up at home in the morning after a night somewhere else (the cell, for instance). */
    void wakeAtHome(float energyAfter) {
        goHome();
        energy = Ui.clamp(energyAfter, 1, 100);
        food = Math.max(food, 25);
        water = Math.max(water, 25);
        skipToMorning();
        if (!over) screen = new MapScreen(this);
    }

    void end(String reason) {
        over = true;
        endPending = false;
        dialogs.clear();
        dialogBtns.clear();
        endTitle = "";
        endReason = reason;
        screen = new EndScreen(this);
    }

    void eat() {
        if (meals <= 0) {
            sfx(Sfx.TICK);
            toast("אין אוכל בתיק. קונים בסופר.");
        } else if (food > 90) {
            sfx(Sfx.TICK);
            toast(t("אתה לא רעב עכשיו", "את לא רעבה עכשיו"));
        } else {
            meals--;
            food = Math.min(100, food + MEAL_FOOD);
            sfx(Sfx.EAT);
            if (!momToast("ate")) toast("אכלת ארוחה מהתיק");
        }
    }

    void drink() {
        if (bottles <= 0) {
            sfx(Sfx.TICK);
            toast("אין מים בתיק. קונים בסופר.");
        } else if (water > 90) {
            sfx(Sfx.TICK);
            toast(t("אתה לא צמא עכשיו", "את לא צמאה עכשיו"));
        } else {
            bottles--;
            water = Math.min(100, water + BOTTLE_WATER);
            sfx(Sfx.POUR);
            if (!momToast("drank")) toast("שתית בקבוק מים");
        }
    }

    // ================= the day's books =================

    /** Remembers the stats as they are now, so the next summary can show what changed. */
    private void markSummary() {
        for (String k : STAT_KEYS) setVar("sum." + k, var(k));
    }

    /** What the last summary remembered of a stat. */
    float statAtLastSummary(int which) {
        return which < 0 || which >= STAT_KEYS.length ? 0 : var("sum." + STAT_KEYS[which]);
    }

    /** Closes the books after a night's sleep: today's counters become "yesterday" and start again from zero. */
    void closeDay() {
        setVar("yday.earned", var("day.earned"));
        setVar("yday.spent", var("day.spent"));
        setVar("day.earned", 0);
        setVar("day.spent", 0);
        markSummary();
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
        c.text(rentLine(), W / 2, 81, 12, rentWorry() ? Ui.BAD : Ui.DIM, Gfx.CENTER);
    }

    /** "שכירות ₪900 בעוד 3 ימים". */
    String rentLine() {
        int left = daysToRent();
        return "שכירות " + Ui.nis(rentDue()) + " " + (left == 1 ? "מחר" : "בעוד " + left + " ימים");
    }

    boolean rentWorry() {
        return daysToRent() <= 2 && money < rentDue();
    }

    private void meter(Gfx c, float x, String label, float v, int col) {
        boolean low = v < 20;
        int lc = low && ((int) (blink * 3) % 2 == 0) ? Ui.BAD : Ui.DIM;
        c.text(label, x + 100, 47, 11, lc, Gfx.RIGHT);
        c.text(String.valueOf((int) Math.ceil(v)), x, 47, 11, lc, Gfx.LEFT);
        Ui.bar(c, x, 53, 100, 8, v / 100f, low ? Ui.BAD : col);
    }

    private void drawDialog(Gfx c) {
        Msg d = dialogs.peek();
        c.fillRect(0, 0, W, h, 0xC0000000);
        float pw = 312, px0 = (W - pw) / 2, tw = pw - 40;
        int n = d.options.length;
        float bh = n >= 3 ? 44 : 48, gap = 8;
        float buttons = n == 0 ? 48 : n * bh + (n - 1) * gap;
        // A long text shrinks until the whole card, buttons included, fits the screen.
        float size = 15, frame = 58 + 14 + buttons + 18;
        List<String> lines = Ui.wrap(c, d.body, tw, size);
        while (size > 11 && frame + lines.size() * size * 1.4f > h - 24) {
            size -= 1;
            lines = Ui.wrap(c, d.body, tw, size);
        }
        // A text far beyond the agreed length is cut rather than allowed to push the buttons off screen.
        int room = Math.max(1, (int) ((h - 16 - frame) / (size * 1.4f)));
        if (lines.size() > room) {
            lines = new ArrayList<>(lines.subList(0, room));
            lines.set(room - 1, lines.get(room - 1) + "...");
        }
        float ph = frame + lines.size() * size * 1.4f;
        float py0 = Math.max(8, (h - ph) / 2);
        c.fillRoundRect(px0, py0, pw, ph, 16, Ui.PANEL);
        c.strokeRoundRect(px0, py0, pw, ph, 16, 1.5f, Ui.PANEL2);
        c.text(d.title, W / 2, py0 + 36, Ui.fit(c, d.title, tw, 20), Ui.ACCENT, Gfx.CENTER);
        float y = py0 + 53 + size;
        for (String line : lines) {
            c.text(line, W / 2, y, size, Ui.INK, Gfx.CENTER);
            y += size * 1.4f;
        }
        dialogBtns.clear();
        float by = py0 + ph - 18 - buttons;
        if (n == 0) {
            Btn b = new Btn(0, px0 + 20, by, tw, 48, "המשך", true, true);
            dialogBtns.add(b);
            b.draw(c);
            return;
        }
        for (int i = 0; i < n; i++) {
            Btn b = new Btn(i, px0 + 20, by + i * (bh + gap), tw, bh, d.options[i], true, false);
            dialogBtns.add(b);
            // Answers are drawn here rather than by Btn so that a long one can shrink to fit.
            c.fillRoundRect(b.x, b.y, b.w, b.h, 10, Ui.PANEL2);
            c.strokeRoundRect(b.x, b.y, b.w, b.h, 10, 1.5f, 0x66FFC857);
            float ls = Ui.fit(c, b.label, b.w - 16, 15);
            c.text(b.label, b.x + b.w / 2, b.y + b.h / 2 + ls * 0.36f, ls, Ui.INK, Gfx.CENTER);
        }
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
        put(sb, "v", "3");
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
        put(sb, "lastShiftDay", lastShiftDay);
        put(sb, "shiftsDone", shiftsDone);
        put(sb, "rentDebt", rentDebt);
        put(sb, "rentMissed", rentMissed);
        put(sb, "px", px);
        put(sb, "py", py);
        put(sb, "at", at);
        put(sb, "endTitle", esc(endTitle));
        put(sb, "endReason", esc(endReason));
        put(sb, "endPending", endPending);
        StringBuilder vs = new StringBuilder();
        for (java.util.Map.Entry<String, Float> e : vars.entrySet()) {
            vs.append(e.getKey()).append(':').append(e.getValue()).append(',');
        }
        put(sb, "vars", vs);
        StringBuilder ms = new StringBuilder();
        for (Msg d : dialogs) {
            field(ms, d.id);
            field(ms, d.title);
            field(ms, d.body);
            field(ms, String.valueOf(d.sfx));
            field(ms, d.shown ? "1" : "0");
            field(ms, String.valueOf(d.options.length));
            field(ms, String.valueOf(d.effects.length));
            for (int i = 0; i < d.options.length; i++) {
                field(ms, d.options[i]);
                field(ms, d.replies[i]);
            }
            for (String e : d.effects) field(ms, e);
        }
        put(sb, "msgs", ms);
        put(sb, "scr", screen.kind());
        screen.save(sb);
        return sb.toString();
    }

    static void put(StringBuilder sb, String k, Object v) {
        sb.append(k).append('=').append(v).append(';');
    }

    private static void field(StringBuilder sb, String s) {
        sb.append(esc(s)).append('|');
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

    static String strOf(HashMap<String, String> m, String k) {
        return unesc(m.getOrDefault(k, ""));
    }

    /** Restores a save written by this or any earlier version. Anything unreadable is ignored. */
    public void load(String s) {
        if (s == null || s.isEmpty()) return;
        HashMap<String, String> m = new HashMap<>();
        for (String kv : s.split(";")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(kv.substring(0, i), kv.substring(i + 1));
        }
        String v = m.get("v");
        if (!"1".equals(v) && !"2".equals(v) && !"3".equals(v)) return;
        boolean old = !"3".equals(v);
        // Before version 3 a save held nothing worth keeping until a game had started.
        if (old && !Boolean.parseBoolean(m.get("started"))) return;
        try {
            read(m, old);
        } catch (RuntimeException e) {
            // A damaged save must never crash the app: start clean, keeping what the player owns.
            reset();
            readVars(m.getOrDefault("vars", ""));
            vars.keySet().removeIf(k -> !kept(k));
        }
    }

    private void reset() {
        started = false;
        female = false;
        over = false;
        name = "";
        money = 0;
        day = 1;
        minute = 0;
        food = 0;
        water = 0;
        energy = 0;
        meals = 0;
        bottles = 0;
        lastShiftDay = 0;
        shiftsDone = 0;
        rentDebt = 0;
        rentMissed = 0;
        px = 0;
        py = 0;
        at = -1;
        endTitle = "";
        endReason = "";
        endPending = false;
        vars.clear();
        clearTransient();
        screen = new TitleScreen(this);
    }

    private void clearTransient() {
        dialogs.clear();
        dialogBtns.clear();
        fx.clear();
        soundHead = 0;
        soundCount = 0;
        toastT = 0;
    }

    private void read(HashMap<String, String> m, boolean old) {
        started = Boolean.parseBoolean(m.get("started"));
        female = Boolean.parseBoolean(m.get("female"));
        name = strOf(m, "name");
        if (name.length() > NameScreen.MAX_LEN) name = name.substring(0, NameScreen.MAX_LEN);
        money = intOf(m, "money", START_MONEY);
        day = Math.max(1, intOf(m, "day", 1));
        minute = Ui.clamp(floatOf(m, "minute", 540), 0, LAST_MINUTE);
        food = Ui.clamp(floatOf(m, "food", 70), 0, 100);
        water = Ui.clamp(floatOf(m, "water", 70), 0, 100);
        energy = Ui.clamp(floatOf(m, "energy", 70), 0, 100);
        meals = (int) Ui.clamp(intOf(m, "meals", 0), 0, BAG_MAX);
        bottles = (int) Ui.clamp(intOf(m, "bottles", 0), 0, BAG_MAX);
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
        endTitle = strOf(m, "endTitle");
        endReason = strOf(m, "endReason");
        endPending = Boolean.parseBoolean(m.get("endPending"));
        vars.clear();
        readVars(m.getOrDefault("vars", ""));
        clearTransient();
        if (old) {
            migrate(m);
            String[] parts = m.getOrDefault("dialogs", "").split("\\|");
            for (int i = 0; i + 1 < parts.length; i += 2) dialog(unesc(parts[i]), unesc(parts[i + 1]));
        } else {
            readMsgs(m.getOrDefault("msgs", ""));
        }
        screen = started && over ? new EndScreen(this) : restoreScreen(m);
    }

    private void readVars(String s) {
        for (String kv : s.split(",")) {
            int i = kv.indexOf(':');
            if (i <= 0) continue;
            String k = kv.substring(0, i);
            if (!validKey(k) || computed(k)) continue;
            try {
                float f = Float.parseFloat(kv.substring(i + 1));
                if (!Float.isNaN(f) && !Float.isInfinite(f)) vars.put(k, ranged(k, f));
            } catch (NumberFormatException e) {
                // One unreadable number is not worth losing the rest of the save for.
            }
        }
    }

    private void readMsgs(String s) {
        String[] f = s.split("\\|", -1);
        int i = 0;
        // The last element is the empty remainder after the final separator.
        while (i + 7 <= f.length - 1) {
            Msg d = new Msg();
            d.id = unesc(f[i++]);
            d.title = unesc(f[i++]);
            d.body = unesc(f[i++]);
            d.sfx = Integer.parseInt(f[i++]);
            d.shown = "1".equals(f[i++]);
            int n = Integer.parseInt(f[i++]), ne = Integer.parseInt(f[i++]);
            if (n < 0 || n > 3 || ne < 0 || ne > 3 || i + 2 * n + ne > f.length - 1) throw new IllegalArgumentException("msgs");
            d.options = new String[n];
            d.replies = new String[n];
            d.effects = new String[ne];
            for (int k = 0; k < n; k++) {
                d.options[k] = unesc(f[i++]);
                d.replies[k] = unesc(f[i++]);
            }
            for (int k = 0; k < ne; k++) d.effects[k] = unesc(f[i++]);
            if (n > 0 && ne < n) throw new IllegalArgumentException("msgs");
            dialogs.add(d);
        }
    }

    /** Saves from before variables existed: give the player what a new game would have set up. */
    private void migrate(HashMap<String, String> m) {
        setVar("fit", START_FIT);
        setVar("cha", START_CHA);
        setVar("edu", START_EDU);
        setVar("mom.trust", 50);
        setVar("mom.worry", 50);
        if (Boolean.parseBoolean(m.get("cookHired"))) setVar("hired." + Job.COOK, 1);
        // Mom's weekly calls so far have happened; the next one comes with the next full week.
        setVar("eng.week", day / MOM_EVERY_DAYS);
        markSummary();
    }

    private Screen restoreScreen(HashMap<String, String> m) {
        String scr = m.getOrDefault("scr", "map");
        if (!started) {
            // Before a game starts there are only the title, the name entry and the store.
            if (scr.equals("name")) return NameScreen.restore(this, m);
            if (scr.equals("store")) return StoreScreen.restore(this, m);
            return new TitleScreen(this);
        }
        switch (scr) {
            case "title":
                return new TitleScreen(this);
            case "name":
                return NameScreen.restore(this, m);
            case "building":
                return BuildingScreen.restore(this, m);
            case "sleep":
                return SleepScreen.restore(this, m);
            case "summary":
                return SummaryScreen.restore(this, m);
            case "store":
                return StoreScreen.restore(this, m);
            case "shift":
                return ShiftScreen.restore(this, m);
            case "cook":
                return ShiftScreen.restoreOldCook(this, m);
            case "event":
                return EventScreen.restore(this, m);
            case "venue":
                return VenueScreen.restore(this, m);
            default: {
                MapScreen ms = new MapScreen(this);
                ms.restore(m);
                return ms;
            }
        }
    }
}
