package com.wizzcraft.ima.core;

import java.util.HashMap;

/**
 * A work shift: hosts the job's Mini and owns everything around it. The intro card, pausing and
 * resuming, the clock (8 game hours split evenly over the Mini's rounds), wages and tips, the result
 * banner, being sent home when out of energy, the end card with promotion, experience and training,
 * and saving. The Mini only plays its rounds.
 */
final class ShiftScreen extends Screen {
    static final int INTRO = 0, PLAY = 1, SHOW = 2, DONE = 3, PAUSED = 4;
    /** Pay for a MISS / OK / PERFECT round, as a share of the round's base pay. */
    static final float[] RESULT_PAY = {0.4f, 1f, 1.3f};
    /**
     * Tips, as a share of the round's pay: for every PERFECT in a row (up to STREAK_MAX), and in
     * service jobs for every point of charisma. A MISS earns no tip.
     */
    static final float STREAK_TIP = 0.08f, CHA_TIP = 1 / 150f;
    static final int STREAK_MAX = 5;
    /** Experience for a flawless shift; a worse or shorter one earns its share. */
    static final int XP_PER_SHIFT = 16;
    /** Stat points a shift trains: TRAIN_BASE plus up to TRAIN_QUALITY for a flawless one. */
    static final float TRAIN_BASE = 1, TRAIN_QUALITY = 2;
    /** A full shift burns 48 energy; nobody may start one they can't finish. */
    static final float MIN_ENERGY = 55;
    /** Difficulty handed to the Mini: progress through the shift, rank, and tiredness. */
    static final float DIFF_PROGRESS = 0.6f, DIFF_RANK = 0.15f, DIFF_TIRED = 0.15f;
    private static final float SHOW_TIME = 0.8f;

    final Job job;
    final Mini mini;
    /** Rank at the start of the shift; it sets the pay and the difficulty for all of it. */
    final int rank, rounds;
    int state = INTRO, round, pay, tips, streak, xpGain;
    final int[] results;
    float trained;
    boolean sentHome, resumeShow, rankedUp;
    /** One-liners from the workplace for the intro and the end card. */
    String startLine = "", endLine = "";
    /** True while a finger that went down on the Mini is still down. */
    private boolean finger;
    private float t, showT;

    private ShiftScreen(Game g, Job job, Mini mini) {
        super(g);
        this.job = job;
        this.mini = mini;
        mini.attach(g.host);
        rank = g.rank(job.id);
        rounds = Math.max(1, mini.rounds());
        results = new int[rounds];
    }

    /** Opens the intro card of a shift, or returns null when there is no Mini to play it. */
    static ShiftScreen open(Game g, Job job) {
        Mini mini = Minis.forJob(job);
        if (mini == null) return null;
        ShiftScreen s = new ShiftScreen(g, job, mini);
        s.startLine = g.fill(Lines.shiftStart(job.id, s.rank, g.facts(), g.rnd));
        return s;
    }

    private float hoursPerRound() {
        return (float) Job.SHIFT_HOURS / rounds;
    }

    private boolean tired() {
        return g.energy < Game.TIRED;
    }

    /** 0..1: rises through the shift, with rank, and when the player is tired. */
    float difficulty() {
        float progress = rounds > 1 ? (float) round / (rounds - 1) : 0;
        return Ui.clamp(progress * DIFF_PROGRESS + rank * DIFF_RANK + (tired() ? DIFF_TIRED : 0), 0, 1);
    }

    boolean playing() {
        return state == PLAY;
    }

    private void startRound() {
        // The shift only counts as today's once the first round starts.
        if (round == 0) g.lastShiftDay = g.day;
        finger = false;
        state = PLAY;
        mini.begin(round, difficulty());
    }

    @Override
    void update(float dt) {
        t += dt;
        if (state == PLAY) {
            int res = mini.update(dt);
            if (res != Mini.RUNNING) finishRound(res < Mini.MISS ? Mini.MISS : Math.min(res, Mini.PERFECT));
        } else if (state == SHOW) {
            showT -= dt;
            if (showT <= 0) finishHours();
        }
    }

    /** The round is decided: pay for it, answer with motion and sound, and show the banner. */
    private void finishRound(int result) {
        results[round] = result;
        streak = result == Mini.PERFECT ? streak + 1 : 0;
        float roundPay = job.pay * hoursPerRound();
        int base = Math.round(roundPay * Job.RANK_PAY[rank] * RESULT_PAY[result]);
        int tip = result == Mini.MISS ? 0 : Math.round(roundPay
                * (STREAK_TIP * Math.min(streak, STREAK_MAX) + (job.service ? g.stat(Host.CHA) * CHA_TIP : 0)));
        g.earn(base + tip);
        pay += base;
        tips += tip;
        float top = Game.PLAY_TOP, cy = (top + g.h - Game.PLAY_FOOT) / 2;
        g.fx.text(Game.W / 2, top + 46, "+" + Ui.nis(base + tip), result == Mini.MISS ? Ui.DIM : Ui.ACCENT);
        if (result == Mini.PERFECT) {
            g.fx.burst(Game.W / 2, cy, Ui.ACCENT, 16);
            g.sfx(Sfx.PERFECT);
        } else if (result == Mini.OK) {
            g.fx.burst(Game.W / 2, cy, Ui.GOOD, 6);
            g.sfx(Sfx.GOOD);
        } else {
            g.fx.shake(7);
            g.sfx(Sfx.BAD);
        }
        state = SHOW;
        showT = SHOW_TIME;
    }

    /** The hours of the round that was just decided pass; then the next round or the end of the shift. */
    private void finishHours() {
        // Out of strength: the manager sends you home instead of letting you drop.
        if (g.energy - Game.WORK_ENERGY_PER_HOUR * hoursPerRound() < 2) {
            sentHome = true;
            endShift();
            return;
        }
        g.advance(60 * hoursPerRound(), Game.WORK);
        if (g.screen != this) return;
        round++;
        if (round >= rounds) endShift();
        else startRound();
    }

    /** How many rounds were actually played. */
    private int played() {
        return Math.min(rounds, sentHome ? round + 1 : rounds);
    }

    private void endShift() {
        state = DONE;
        g.shiftsDone++;
        int n = played(), sum = 0;
        for (int i = 0; i < n; i++) sum += results[i];
        // Quality is measured against a full shift, so one that was cut short counts for less.
        float quality = sum / (2f * rounds);
        int before = g.rank(job.id);
        xpGain = Math.round(quality * XP_PER_SHIFT);
        g.addVar("xp." + job.id, xpGain);
        rankedUp = g.rank(job.id) > before;
        if (job.trains != Job.NONE) {
            float was = g.stat(job.trains);
            g.train(job.trains, TRAIN_BASE + TRAIN_QUALITY * quality);
            trained = g.stat(job.trains) - was;
        }
        endLine = g.fill(Lines.shiftEnd(job.id, (float) sum / n, g.facts(), g.rnd));
        if (rankedUp) {
            g.sfx(Sfx.LEVEL);
            g.fx.burst(Game.W / 2, g.h * 0.4f, Ui.GOOD, 30);
        }
    }

    @Override
    void down(float x, float y) {
        if (state == PLAY) {
            if (y > Game.HUD_H) {
                finger = true;
                mini.down(x, y);
            }
            return;
        }
        if (hit(x, y) == null) return;
        g.sfx(Sfx.TAP);
        if (state == INTRO) {
            startRound();
        } else if (state == DONE) {
            leave();
        } else if (state == PAUSED) {
            if (resumeShow) {
                resumeShow = false;
                finishHours();
            } else {
                startRound();
            }
        }
    }

    @Override
    void move(float x, float y) {
        if (state == PLAY && finger) mini.move(x, y);
    }

    @Override
    void up(float x, float y) {
        if (state == PLAY && finger) mini.up(x, y);
        finger = false;
    }

    /** Back to the workplace; a promotion is announced on the way out. */
    private void leave() {
        g.screen = new BuildingScreen(g, job.building);
        if (rankedUp) {
            g.play(Lines.promotion(job.id, g.rank(job.id), g.facts()), Sfx.LEVEL);
        } else if (g.money >= Game.RICH) {
            g.momToast("rich");
        }
    }

    @Override
    void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        btns.clear();
        float top = Game.PLAY_TOP, bottom = h - Game.PLAY_FOOT;
        if (state == PLAY || state == SHOW) {
            mini.draw(c, top, bottom);
            // The host's own strips are drawn afterwards, so nothing of the Mini spills into them.
            c.fillRect(0, Game.HUD_H, Game.W, top - Game.HUD_H, Ui.BG);
            c.fillRect(0, bottom, Game.W, Game.PLAY_FOOT, Ui.BG);
        }
        float head = Game.HUD_H + 34;
        c.text("משמרת: " + job.name(g.female), Game.W / 2, head, 22, Ui.INK, Gfx.CENTER);

        if (state == INTRO || state == PAUSED) {
            c.text(Job.rankName(g.female, rank), Game.W / 2, head + 22, 13, Ui.DIM, Gfx.CENTER);
            mini.art(c, Game.W / 2, h * 0.38f, t);
            float y = h * 0.38f + 88;
            if (state == INTRO) {
                if (!startLine.isEmpty()) y = Ui.paragraph(c, startLine, Game.W / 2, y, 304, 13, Ui.ACCENT) + 8;
                y = Ui.paragraph(c, mini.howTo(), Game.W / 2, y, 304, howToSize(c, y, h), Ui.DIM);
            } else {
                y = Ui.paragraph(c, "המשמרת בהפסקה. הרווחת עד עכשיו " + Ui.nis(pay + tips) + ".", Game.W / 2, y,
                        304, 15, Ui.DIM);
            }
            btns.add(new Btn(0, 40, Math.min(y + 4, h - 62), 280, 54, state == INTRO ? "מתחילים" : "ממשיכים", true, true));
        } else if (state == DONE) {
            drawDone(c, h);
        } else {
            String unit = rounds == Job.SHIFT_HOURS ? "שעה " : "שלב ";
            c.text(unit + (round + 1) + " מתוך " + rounds, Game.W / 2, head + 22, 13, Ui.DIM, Gfx.CENTER);
            if (state == SHOW) {
                int r = results[round];
                String msg = r == Mini.PERFECT ? "מושלם!" : r == Mini.OK ? "טוב" : "פספוס";
                int col = r == Mini.PERFECT ? Ui.ACCENT : r == Mini.OK ? Ui.GOOD : Ui.BAD;
                float my = (top + bottom) / 2;
                c.fillRoundRect(70, my - 34, 220, 56, 14, 0xE0101418);
                c.text(msg, Game.W / 2, my + 6, 30, col, Gfx.CENTER);
            }
            drawDots(c, h - 72, state == SHOW ? round + 1 : round);
            String line = "הרווחת " + Ui.nis(pay + tips) + (streak >= 2 ? " · רצף ×" + streak : "");
            c.text(line, Game.W / 2, h - 40, 13, streak >= 2 ? Ui.ACCENT : Ui.DIM, Gfx.CENTER);
            if (tired()) {
                c.text(g.t("אתה עייף, הכל קשה יותר", "את עייפה, הכל קשה יותר"), Game.W / 2, h - 20, 12, Ui.BAD,
                        Gfx.CENTER);
            }
        }
        drawButtons(c);
        g.drawHud(c);
    }

    /** The how-to text gets smaller on short screens so the start button always stays on screen. */
    private float howToSize(Gfx c, float y, float h) {
        for (float size = 15; size > 12; size--) {
            if (y + Ui.wrap(c, mini.howTo(), 304, size).size() * size * 1.4f + 62 <= h) return size;
        }
        return 12;
    }

    private void drawDone(Gfx c, float h) {
        int n = played(), perfect = 0, ok = 0, miss = 0;
        for (int i = 0; i < n; i++) {
            if (results[i] == Mini.PERFECT) perfect++;
            else if (results[i] == Mini.OK) ok++;
            else miss++;
        }
        // The card keeps its proportions on tall screens and closes its gaps on short ones.
        float y = Math.max(Game.HUD_H + 66, h * 0.27f);
        c.text(sentHome ? "נגמר לך הכוח, שלחו אותך הביתה" : "סוף משמרת", Game.W / 2, y, sentHome ? 15 : 18,
                sentHome ? Ui.BAD : Ui.DIM, Gfx.CENTER);
        c.text(Ui.nis(pay + tips), Game.W / 2, y + 56, 50, Ui.ACCENT, Gfx.CENTER);
        c.text("שכר " + Ui.nis(pay) + " · טיפים " + Ui.nis(tips), Game.W / 2, y + 82, 14, Ui.DIM, Gfx.CENTER);
        c.text("מושלם: " + perfect + " · טוב: " + ok + " · פספוס: " + miss, Game.W / 2, y + 106, 15, Ui.INK,
                Gfx.CENTER);
        drawDots(c, y + 128, n);
        int now = g.rank(job.id);
        if (rankedUp) {
            c.text("קידום! מעכשיו " + Job.rankName(g.female, now), Game.W / 2, y + 164, 18, Ui.GOOD, Gfx.CENTER);
        } else if (now < Job.RANK_XP.length - 1) {
            int lo = Job.RANK_XP[now], hi = Job.RANK_XP[now + 1];
            c.text("בדרך ל" + Job.rankName(g.female, now + 1), Game.W / 2, y + 156, 12, Ui.DIM, Gfx.CENTER);
            Ui.bar(c, 90, y + 163, 180, 8, (g.xp(job.id) - lo) / (float) (hi - lo), Ui.GOOD);
        } else {
            c.text(Job.rankName(g.female, now), Game.W / 2, y + 164, 14, Ui.DIM, Gfx.CENTER);
        }
        String gains = "ניסיון +" + xpGain;
        if (job.trains != Job.NONE && trained > 0.04f) {
            gains += " · " + Job.STAT_NAMES[job.trains] + " " + Ui.num(trained, true);
        }
        c.text(gains, Game.W / 2, y + 190, 13, Ui.GOOD, Gfx.CENTER);
        float by = y + 204;
        if (!endLine.isEmpty()) by = Ui.paragraph(c, endLine, Game.W / 2, y + 214, 304, 13, Ui.INK);
        btns.add(new Btn(0, 40, Math.min(by + 2, h - 62), 280, 54, "המשך", true, true));
    }

    private void drawDots(Gfx c, float y, int done) {
        float x = Game.W / 2 + (rounds - 1) * 11;
        for (int i = 0; i < rounds; i++) {
            int col = i >= done ? Ui.PANEL2 : results[i] == Mini.PERFECT ? Ui.ACCENT
                    : results[i] == Mini.OK ? Ui.GOOD : Ui.BAD;
            c.fillCircle(x - i * 22, y, 7, col);
        }
    }

    @Override
    boolean back() {
        // Leaving is only possible before the first round; after that the shift has to be finished.
        if (state == INTRO) g.screen = new BuildingScreen(g, job.building);
        return true;
    }

    // ---------------- save / resume ----------------

    @Override
    String kind() {
        return "shift";
    }

    @Override
    void save(StringBuilder sb) {
        StringBuilder res = new StringBuilder();
        for (int r : results) res.append(r);
        Game.put(sb, "jg", job.id);
        Game.put(sb, "js", state);
        Game.put(sb, "jr", round);
        Game.put(sb, "jres", res);
        Game.put(sb, "jpay", pay);
        Game.put(sb, "jtips", tips);
        Game.put(sb, "jstreak", streak);
        Game.put(sb, "jsent", sentHome);
        Game.put(sb, "jsw", resumeShow);
        Game.put(sb, "jup", rankedUp);
        Game.put(sb, "jxp", xpGain);
        Game.put(sb, "jtr", trained);
        Game.put(sb, "jl0", Game.esc(startLine));
        Game.put(sb, "jl1", Game.esc(endLine));
    }

    /** Leaving the app in the middle of a round pauses the shift; the round is replayed on return. */
    @Override
    void onAppPause() {
        if (state == PLAY) {
            mini.pause();
            state = PAUSED;
            resumeShow = false;
        } else if (state == SHOW) {
            // The round was judged and paid; only its hours are left to pass.
            state = PAUSED;
            resumeShow = true;
        }
        finger = false;
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        Job job = Job.byId(Game.intOf(m, "jg", -1));
        if (job == null) return new MapScreen(g);
        Mini mini = Minis.forJob(job);
        if (mini == null) return new BuildingScreen(g, job.building);
        ShiftScreen s = new ShiftScreen(g, job, mini);
        int st = Game.intOf(m, "js", INTRO);
        s.round = (int) Ui.clamp(Game.intOf(m, "jr", 0), 0, st == DONE ? s.rounds : s.rounds - 1);
        String res = m.getOrDefault("jres", "");
        for (int i = 0; i < s.rounds && i < res.length(); i++) {
            s.results[i] = (int) Ui.clamp(res.charAt(i) - '0', Mini.MISS, Mini.PERFECT);
        }
        s.pay = Math.max(0, Game.intOf(m, "jpay", 0));
        s.tips = Math.max(0, Game.intOf(m, "jtips", 0));
        s.streak = Math.max(0, Game.intOf(m, "jstreak", 0));
        s.sentHome = Boolean.parseBoolean(m.get("jsent"));
        s.rankedUp = Boolean.parseBoolean(m.get("jup"));
        s.xpGain = Math.max(0, Game.intOf(m, "jxp", 0));
        s.trained = Game.floatOf(m, "jtr", 0);
        s.startLine = Game.strOf(m, "jl0");
        s.endLine = Game.strOf(m, "jl1");
        // A round can only be resumed from its beginning, so "playing" comes back as "paused".
        s.resumeShow = st == SHOW || (st == PAUSED && Boolean.parseBoolean(m.get("jsw")));
        s.state = (st == INTRO || st == DONE) ? st : PAUSED;
        return s;
    }

    /**
     * A save from the version where the cook's shift was its own screen with eight one-hour dishes:
     * the hours already worked and the pay so far carry over into today's cook shift.
     */
    static Screen restoreOldCook(Game g, HashMap<String, String> m) {
        Job job = Job.ALL[Job.COOK];
        int st = Game.intOf(m, "cs", INTRO);
        Mini mini = Minis.forJob(job);
        // The old end-of-shift card has nothing left to do: the pay is already in the wallet.
        if (st == DONE || mini == null) return new BuildingScreen(g, job.building);
        ShiftScreen s = new ShiftScreen(g, job, mini);
        if (st == INTRO) return s;
        boolean judged = st == SHOW || Boolean.parseBoolean(m.get("csw"));
        int hours = (int) Ui.clamp(Game.intOf(m, "cr", 0) + (judged ? 1 : 0), 0, Job.SHIFT_HOURS - 1);
        s.round = Math.min(s.rounds - 1, hours * s.rounds / Job.SHIFT_HOURS);
        for (int i = 0; i < s.round; i++) s.results[i] = Mini.OK;
        s.pay = Math.max(0, Game.intOf(m, "cpay", 0));
        s.state = PAUSED;
        return s;
    }
}
