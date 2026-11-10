package com.wizzcraft.ima.core;

import java.util.HashMap;
import java.util.Random;

/**
 * Base of every work minigame. It owns everything that is the same for all jobs: the intro card,
 * pausing and resuming, the clock, pay and tips, streaks, being sent home when out of energy, the
 * end-of-shift summary, promotions, and saving. A job only implements what happens inside one round.
 *
 * A shift is always 8 game hours, split evenly over {@link #rounds()} rounds.
 */
abstract class ShiftGame extends Screen {
    static final int INTRO = 0, PLAY = 1, SHOW = 2, DONE = 3, PAUSED = 4;
    /** Round results. */
    static final int MISS = 0, OK = 1, PERFECT = 2;
    static final float[] MULT = {0.4f, 1f, 1.3f};
    static final int SHIFT_HOURS = 8;
    /** A full shift burns 48 energy; nobody may start one they can't finish. */
    static final float MIN_ENERGY = 55;

    final Job job;
    /** Rank (0..2) at the start of the shift. Higher rank pays more and should play harder. */
    final int rank;
    final Random rnd = new Random();
    int state = INTRO, round, pay, tips, streak;
    final int[] results;
    boolean sentHome, resumeShow, rankedUp;
    /** Seconds since the screen opened, and since the current round started. */
    float t, roundT;
    private float showT;

    ShiftGame(Game g, Job job) {
        super(g);
        this.job = job;
        rank = g.rank(job.id);
        results = new int[rounds()];
    }

    // ---------------- what a job implements ----------------

    /** How many rounds the 8 hours are split into (8, 4 or 2 work best). Must be constant. */
    int rounds() {
        return 8;
    }

    /** One or two short sentences for the intro card: what to do and how to do it well. */
    abstract String howTo();

    /** Set up round number {@link #round}. Use {@link #difficulty()} to scale it. */
    abstract void beginRound();

    /** Advance the round. Call {@link #finishRound(int)} exactly once when the round is decided. */
    abstract void updateRound(float dt);

    /** Draw the play area only, between y = top and y = bottom, full width (0..360). */
    abstract void drawRound(Gfx c, float top, float bottom);

    void roundDown(float x, float y) {
    }

    void roundUp(float x, float y) {
    }

    /** Optional polygon illustration on the intro and pause cards, centred on (cx, cy), about 120 high. */
    void drawIntroArt(Gfx c, float cx, float cy) {
    }

    /** Test-bot hook, called every frame during a round. skill is 0..1; act through roundDown/roundUp. */
    void botPlay(Random r, float skill) {
    }

    // ---------------- helpers for jobs ----------------

    final float hoursPerRound() {
        return (float) SHIFT_HOURS / rounds();
    }

    final boolean tired() {
        return g.energy < 30;
    }

    /** 0..1: rises through the shift, with rank, and when the player is tired. */
    final float difficulty() {
        float prog = rounds() > 1 ? (float) round / (rounds() - 1) : 0;
        return Ui.clamp(prog * 0.6f + rank * 0.15f + (tired() ? 0.15f : 0), 0, 1);
    }

    final boolean playing() {
        return state == PLAY;
    }

    final float playTop() {
        return Game.HUD_H + 74;
    }

    final float playBottom() {
        return g.h - 96;
    }

    /** Ends the current round with MISS, OK or PERFECT: pays, gives feedback, and moves the clock on. */
    protected final void finishRound(int result) {
        if (state != PLAY) return;
        results[round] = result;
        streak = result == PERFECT ? streak + 1 : 0;
        float hourly = job.pay * hoursPerRound();
        int base = Math.round(hourly * Job.RANK_PAY[rank] * MULT[result]);
        int tip = result == MISS ? 0
                : Math.round(hourly * (0.08f * Math.min(streak, 5) + (job.service ? g.cha / 150f : 0)));
        g.earn(base + tip);
        pay += base;
        tips += tip;
        float cy = (playTop() + playBottom()) / 2;
        g.fx.text(Game.W / 2, playTop() + 46, "+" + Ui.nis(base + tip), result == MISS ? Ui.DIM : Ui.ACCENT);
        if (result == PERFECT) {
            g.fx.burst(Game.W / 2, cy, Ui.ACCENT, 16);
            g.sfx(Sfx.PERFECT);
        } else if (result == OK) {
            g.fx.burst(Game.W / 2, cy, Ui.GOOD, 6);
            g.sfx(Sfx.GOOD);
        } else {
            g.fx.shake(7);
            g.sfx(Sfx.BAD);
        }
        state = SHOW;
        showT = 0.8f;
    }

    // ---------------- machinery ----------------

    private void startRound() {
        // The shift only counts as today's once the first round starts.
        if (round == 0) g.lastShiftDay = g.day;
        roundT = 0;
        state = PLAY;
        beginRound();
    }

    @Override
    final void update(float dt) {
        t += dt;
        if (state == PLAY) {
            roundT += dt;
            updateRound(dt);
        } else if (state == SHOW) {
            showT -= dt;
            if (showT <= 0) finishHours();
        }
    }

    /** The hours of the round that was just decided pass; then the next round or the end of the shift. */
    private void finishHours() {
        // Out of strength: the manager sends you home instead of letting you drop.
        if (g.energy - 6 * hoursPerRound() < 2) {
            sentHome = true;
            endShift();
            return;
        }
        g.advance(60 * hoursPerRound(), Game.WORK);
        if (g.screen != this) return;
        round++;
        if (round >= rounds()) endShift();
        else startRound();
    }

    private int played() {
        return Math.min(rounds(), sentHome ? round + 1 : rounds());
    }

    private void endShift() {
        state = DONE;
        g.shiftsDone++;
        int n = played(), q = 0;
        for (int i = 0; i < n; i++) q += results[i];
        float quality = q / (2f * rounds());
        int before = g.rank(job.id);
        g.jobXp[job.id] += Math.round(quality * 16);
        rankedUp = g.rank(job.id) > before;
        if (job.trains != Job.NONE) g.train(job.trains, 1 + 2 * quality);
        if (rankedUp) {
            g.sfx(Sfx.LEVEL);
            g.fx.burst(Game.W / 2, g.h * 0.4f, Ui.GOOD, 30);
        }
    }

    @Override
    final void down(float x, float y) {
        if (state == PLAY) {
            if (y > Game.HUD_H) roundDown(x, y);
            return;
        }
        if (hit(x, y) == null) return;
        g.sfx(Sfx.TAP);
        if (state == INTRO) {
            startRound();
        } else if (state == DONE) {
            g.screen = new BuildingScreen(g, job.building);
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
    final void up(float x, float y) {
        if (state == PLAY) roundUp(x, y);
    }

    @Override
    final void draw(Gfx c) {
        float h = g.h;
        c.fillRect(0, 0, Game.W, h, Ui.BG);
        btns.clear();
        float top = Game.HUD_H + 34;
        c.text("משמרת: " + job.name(g), Game.W / 2, top, 22, Ui.INK, Gfx.CENTER);

        if (state == INTRO || state == PAUSED) {
            c.text(Job.rankName(g, rank), Game.W / 2, top + 22, 13, Ui.DIM, Gfx.CENTER);
            drawIntroArt(c, Game.W / 2, h * 0.38f);
            String txt = state == INTRO ? howTo()
                    : "המשמרת בהפסקה. הרווחת עד עכשיו " + Ui.nis(pay + tips) + ".";
            float y = Ui.paragraph(c, txt, Game.W / 2, h * 0.56f, 304, 15, Ui.DIM);
            btns.add(new Btn(0, 40, y + 14, 280, 54, state == INTRO ? "מתחילים" : "ממשיכים", true, true));
        } else if (state == DONE) {
            int n = played(), perfect = 0, ok = 0, miss = 0;
            for (int i = 0; i < n; i++) {
                if (results[i] == PERFECT) perfect++;
                else if (results[i] == OK) ok++;
                else miss++;
            }
            float y = h * 0.28f;
            c.text(sentHome ? "נגמר לך הכוח, שלחו אותך הביתה" : "סוף משמרת", Game.W / 2, y, sentHome ? 15 : 20,
                    sentHome ? Ui.BAD : Ui.DIM, Gfx.CENTER);
            c.text(Ui.nis(pay + tips), Game.W / 2, y + 62, 54, Ui.ACCENT, Gfx.CENTER);
            c.text("שכר " + Ui.nis(pay) + " · טיפים " + Ui.nis(tips), Game.W / 2, y + 92, 14, Ui.DIM, Gfx.CENTER);
            c.text("מושלם: " + perfect + " · טוב: " + ok + " · פספוס: " + miss, Game.W / 2, y + 118, 15, Ui.INK,
                    Gfx.CENTER);
            drawDots(c, y + 144, n);
            int now = g.rank(job.id);
            if (rankedUp) {
                c.text("קידום! מעכשיו " + Job.rankName(g, now), Game.W / 2, y + 180, 18, Ui.GOOD, Gfx.CENTER);
            } else if (now < Job.RANK_XP.length - 1) {
                int lo = Job.RANK_XP[now], hi = Job.RANK_XP[now + 1];
                c.text("בדרך ל" + Job.rankName(g, now + 1), Game.W / 2, y + 174, 12, Ui.DIM, Gfx.CENTER);
                Ui.bar(c, 90, y + 182, 180, 8, (g.jobXp[job.id] - lo) / (float) (hi - lo), Ui.GOOD);
            }
            btns.add(new Btn(0, 40, y + 208, 280, 54, "המשך", true, true));
        } else {
            String unit = rounds() == SHIFT_HOURS ? "שעה " : "שלב ";
            c.text(unit + (round + 1) + " מתוך " + rounds(), Game.W / 2, top + 22, 13, Ui.DIM, Gfx.CENTER);
            drawRound(c, playTop(), playBottom());
            if (state == SHOW) {
                int r = results[round];
                String msg = r == PERFECT ? "מושלם!" : r == OK ? "טוב" : "פספוס";
                int col = r == PERFECT ? Ui.ACCENT : r == OK ? Ui.GOOD : Ui.BAD;
                float my = (playTop() + playBottom()) / 2;
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

    private void drawDots(Gfx c, float y, int done) {
        int n = rounds();
        float x = Game.W / 2 + (n - 1) * 11;
        for (int i = 0; i < n; i++) {
            int col = i >= done ? Ui.PANEL2 : results[i] == PERFECT ? Ui.ACCENT : results[i] == OK ? Ui.GOOD : Ui.BAD;
            c.fillCircle(x - i * 22, y, 7, col);
        }
    }

    @Override
    final boolean back() {
        // Leaving is only possible before the first round; after that the shift has to be finished.
        if (state == INTRO) g.screen = new BuildingScreen(g, job.building);
        return true;
    }

    // ---------------- save / resume ----------------

    @Override
    final String kind() {
        return "shift";
    }

    @Override
    final void save(StringBuilder sb) {
        StringBuilder res = new StringBuilder();
        for (int r : results) res.append(r);
        Game.put(sb, "jg", job.id);
        Game.put(sb, "cs", state);
        Game.put(sb, "cr", round);
        Game.put(sb, "cres", res);
        Game.put(sb, "cpay", pay);
        Game.put(sb, "ctips", tips);
        Game.put(sb, "cstreak", streak);
        Game.put(sb, "csent", sentHome);
        Game.put(sb, "csw", resumeShow);
        Game.put(sb, "cup", rankedUp);
    }

    /** Leaving the app in the middle of a round pauses the shift; the round is replayed on return. */
    @Override
    final void onAppPause() {
        if (state == PLAY) {
            state = PAUSED;
            resumeShow = false;
        } else if (state == SHOW) {
            state = PAUSED;
            resumeShow = true;
        }
    }

    static Screen restore(Game g, HashMap<String, String> m) {
        int id = Game.intOf(m, "jg", -1);
        if (id < 0 || id >= Job.COUNT) return new MapScreen(g);
        ShiftGame c = Job.create(g, id);
        int st = Game.intOf(m, "cs", INTRO);
        int n = c.rounds();
        c.round = (int) Ui.clamp(Game.intOf(m, "cr", 0), 0, st == DONE ? n : n - 1);
        String res = m.getOrDefault("cres", "");
        for (int i = 0; i < n && i < res.length(); i++) {
            c.results[i] = (int) Ui.clamp(res.charAt(i) - '0', MISS, PERFECT);
        }
        c.pay = Math.max(0, Game.intOf(m, "cpay", 0));
        c.tips = Math.max(0, Game.intOf(m, "ctips", 0));
        c.streak = Math.max(0, Game.intOf(m, "cstreak", 0));
        c.sentHome = Boolean.parseBoolean(m.get("csent"));
        c.rankedUp = Boolean.parseBoolean(m.get("cup"));
        c.resumeShow = Boolean.parseBoolean(m.get("csw")) || st == SHOW;
        c.state = (st == INTRO || st == DONE) ? st : PAUSED;
        return c;
    }
}
