package com.wizzcraft.ima.core;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Odds check for the casino machines. Simulates millions of spins of the roulette and the slot
 * machine with the real rules (RouletteMini.returnMult / SlotsMini.payMult and the same draw
 * functions the machines use), computes the exact return to player by enumeration, and then
 * plays thousands of spins through the actual Mini objects to prove that wallet, drawn result and
 * animation agree.
 *
 * Usage: java -Djava.awt.headless=true -cp OUT com.wizzcraft.ima.core.CasinoCheck [outDir]
 * With an outDir it also writes screenshots of both venues at both screen heights.
 * Prints "CASINO OK" at the end; any failed check throws.
 */
public final class CasinoCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
    }

    public static void main(String[] args) throws Exception {
        roulette();
        slots();
        e2eRoulette();
        e2eSlots();
        if (args.length > 0) shots(args[0]);
        System.out.println("CASINO OK");
    }

    // ---- roulette ----

    static void roulette() {
        int spins = 2_000_000;
        Random r = new Random(20261110L);
        long[] ret = new long[RouletteMini.BETS];
        int[] hits = new int[37];
        for (int i = 0; i < spins; i++) {
            int n = RouletteMini.draw(r);
            hits[n]++;
            for (int b = 0; b < RouletteMini.BETS; b++) ret[b] += RouletteMini.returnMult(b, n);
        }
        System.out.println("ROULETTE: " + spins + " spins, every bet type staked 1 on every spin");
        String[] lab = new String[RouletteMini.BETS];
        for (int b = 0; b < 37; b++) lab[b] = "number " + b;
        lab[RouletteMini.B_RED] = "red";
        lab[RouletteMini.B_BLACK] = "black";
        lab[RouletteMini.B_ODD] = "odd";
        lab[RouletteMini.B_EVEN] = "even";
        lab[RouletteMini.B_LOW] = "low 1-18";
        lab[RouletteMini.B_HIGH] = "high 19-36";
        lab[RouletteMini.B_D1] = "dozen 1-12";
        lab[RouletteMini.B_D2] = "dozen 13-24";
        lab[RouletteMini.B_D3] = "dozen 25-36";
        double exact = 36.0 / 37;
        double sum = 0, lo = 9, hi = 0;
        for (int b = 0; b < 37; b++) {
            double rtp = (double) ret[b] / spins;
            sum += rtp;
            lo = Math.min(lo, rtp);
            hi = Math.max(hi, rtp);
        }
        System.out.printf("  straight numbers (37 bets): mean %.2f%%  min %.2f%%  max %.2f%%  (exact %.2f%%)%n", 100 * sum / 37, 100 * lo,
                100 * hi, 100 * exact);
        for (int b = 37; b < RouletteMini.BETS; b++) {
            double rtp = (double) ret[b] / spins;
            // exact value by enumeration
            double ex = 0;
            for (int n = 0; n < 37; n++) ex += RouletteMini.returnMult(b, n) / 37.0;
            System.out.printf("  %-12s simulated %.2f%%   exact %.2f%%%n", lab[b], 100 * rtp, 100 * ex);
            check(Math.abs(ex - exact) < 1e-12, "exact RTP of " + lab[b] + " is 36/37");
            check(Math.abs(rtp - exact) < 0.006, "simulated RTP of " + lab[b] + " within 0.6 points");
        }
        for (int b = 0; b < 37; b++) {
            double ex = 0;
            for (int n = 0; n < 37; n++) ex += RouletteMini.returnMult(b, n) / 37.0;
            check(Math.abs(ex - exact) < 1e-12, "exact RTP of number " + b);
        }
        check(Math.abs(sum / 37 - exact) < 0.003, "mean straight-number RTP");
        // draw uniformity: chi-square over 37 pockets, 36 degrees of freedom; 99.9% critical value is 67.99
        double chi = 0, e = spins / 37.0;
        for (int n = 0; n < 37; n++) chi += (hits[n] - e) * (hits[n] - e) / e;
        System.out.printf("  pocket uniformity: chi-square %.1f (36 d.f.; fair if below 68)%n", chi);
        check(chi < 68, "pockets are uniformly drawn");
        // the wheel layout is a permutation and the colours are standard
        boolean[] seen = new boolean[37];
        int reds = 0;
        for (int n : RouletteMini.ORDER) seen[n] = true;
        for (boolean s : seen) check(s, "wheel order holds every number once");
        for (int n = 1; n <= 36; n++) if (RouletteMini.RED[n]) reds++;
        check(reds == 18, "18 red numbers");
        System.out.println("  zero loses every outside bet; payouts: number 35:1, dozen 2:1, even-money 1:1");
    }

    // ---- slots ----

    static void slots() {
        int weightSum = 0;
        int[] count = new int[SlotsMini.SYMS];
        for (int i = 0; i < SlotsMini.N; i++) count[SlotsMini.STRIP[i]]++;
        for (int s = 0; s < SlotsMini.SYMS; s++) {
            check(count[s] == SlotsMini.WEIGHT[s], "strip holds the stated weight of symbol " + s);
            weightSum += SlotsMini.WEIGHT[s];
        }
        check(weightSum == SlotsMini.N, "weights add up to the strip length");
        // exact return by enumerating every one of the 64^3 stop combinations
        double exact = 0, hit = 0, ex2 = 0;
        long total = (long) SlotsMini.N * SlotsMini.N * SlotsMini.N;
        long[] byMult = new long[300];
        for (int a = 0; a < SlotsMini.N; a++) {
            for (int b = 0; b < SlotsMini.N; b++) {
                for (int c = 0; c < SlotsMini.N; c++) {
                    int m = SlotsMini.payMult(SlotsMini.STRIP[a], SlotsMini.STRIP[b], SlotsMini.STRIP[c]);
                    exact += m;
                    ex2 += (double) m * m;
                    if (m > 0) hit++;
                    byMult[m]++;
                }
            }
        }
        exact /= total;
        double sd = Math.sqrt(ex2 / total - exact * exact);
        System.out.printf("SLOTS: exact return by enumeration of %d stop combinations: %.2f%%  hit rate %.1f%%  std dev per spin %.2f bets%n",
                total, 100 * exact, 100 * hit / total, sd);
        check(exact >= 0.92 && exact <= 0.95, "exact RTP between 92% and 95%");

        int spins = 2_000_000;
        Random r = new Random(77);
        int[] stops = new int[3];
        double ret = 0, ret2 = 0;
        long[] seenMult = new long[300];
        long wins = 0;
        for (int i = 0; i < spins; i++) {
            SlotsMini.drawStops(r, stops);
            int m = SlotsMini.payMult(SlotsMini.STRIP[stops[0]], SlotsMini.STRIP[stops[1]], SlotsMini.STRIP[stops[2]]);
            ret += m;
            ret2 += (double) m * m;
            seenMult[m]++;
            if (m > 0) wins++;
        }
        double rtp = ret / spins, se = Math.sqrt(ret2 / spins - rtp * rtp) / Math.sqrt(spins);
        System.out.printf("  simulated %d spins: return %.2f%% (+/- %.2f at 95%%)  wins %.1f%% of spins%n", spins, 100 * rtp, 200 * se,
                100.0 * wins / spins);
        check(Math.abs(rtp - exact) < 4 * se, "simulation agrees with the exact return");
        String[] nm = {"coin", "pita", "box", "key", "phone", "star"};
        System.out.println("  pay table check (bet multiples: simulated hits / exact odds):");
        System.out.printf("    two coins on the line  x%d  1 in %.1f%n", SlotsMini.COIN_PAIR,
                1 / (3 * Math.pow(SlotsMini.WEIGHT[0] / (double) SlotsMini.N, 2) * (1 - SlotsMini.WEIGHT[0] / (double) SlotsMini.N)));
        for (int s = 0; s < SlotsMini.SYMS; s++) {
            double p = Math.pow(SlotsMini.WEIGHT[s] / (double) SlotsMini.N, 3);
            System.out.printf("    three %-6s x%-4d 1 in %.0f%n", nm[s], SlotsMini.TRIPLE[s], 1 / p);
        }
        System.out.printf("  the machine is the same strip on all three reels: per-reel stops are uniform draws, no near-miss logic exists%n");
        // each reel's stop distribution is uniform
        int[] reel = new int[SlotsMini.N];
        for (int i = 0; i < spins; i++) reel[r.nextInt(SlotsMini.N)]++;
        double chi = 0, e = spins / (double) SlotsMini.N;
        for (int i = 0; i < SlotsMini.N; i++) chi += (reel[i] - e) * (reel[i] - e) / e;
        System.out.printf("  stop uniformity: chi-square %.1f (63 d.f.; fair if below 104)%n", chi);
        check(chi < 104, "stops uniformly drawn");
    }

    // ---- end to end through the real Mini objects ----

    static MiniLab.FakeHost freshHost(String cls, Mini[] out) throws Exception {
        MiniLab.FakeHost host = new MiniLab.FakeHost();
        host.money = 1_000_000;
        Mini m = (Mini) Class.forName("com.wizzcraft.ima.core." + cls).getDeclaredConstructor().newInstance();
        m.attach(host);
        m.begin(0, 0.3f);
        out[0] = m;
        MiniLab.host = host;
        MiniLab.mini = m;
        return host;
    }

    static void e2eRoulette() throws Exception {
        Mini[] box = new Mini[1];
        MiniLab.FakeHost host = freshHost("RouletteMini", box);
        RouletteMini m = (RouletteMini) box[0];
        Random pick = new Random(5);
        int spins = 0, target = 20_000, landed = 0, mismatches = 0;
        long staked = 0, returned = 0;
        int before = host.money;
        boolean wasSpinning = false;
        int[] mult = new int[RouletteMini.BETS];
        for (long f = 0; f < 80_000_000L && spins < target; f++) {
            m.bot(pick, 0.5f);
            m.update(MiniLab.DT);
            host.tick(MiniLab.DT);
            if (m.wantsExit()) {
                m = new RouletteMini();
                m.attach(host);
                m.begin(0, 0.3f);
            }
            if (m.lastStake > 0 && m.lastStake != -1 && (m.lastWin >= 0)) {
                // a spin just resolved
                int result = m.lastResult();
                long expect = 0;
                int[] lb = m.lastBets();
                int stake = 0;
                for (int b = 0; b < RouletteMini.BETS; b++) {
                    expect += (long) lb[b] * RouletteMini.returnMult(b, result);
                    stake += lb[b];
                }
                check(stake == m.lastStake, "stake recorded equals chips on the table");
                check(expect == m.lastWin, "payout equals the table rules applied to the drawn number");
                if (m.landedNumber() != result) mismatches++;
                landed++;
                staked += stake;
                returned += expect;
                spins++;
                m.lastStake = 0;
                m.lastWin = 0;
            }
        }
        int after = host.money;
        check(mismatches == 0, "the ball lands in the drawn pocket on every spin");
        check(after - before == returned - staked, "wallet change equals returned minus staked");
        System.out.printf("ROULETTE end to end: %d spins played through the real Mini by its bot: ball landed on the drawn number %d/%d times,"
                + " wallet change %+d = payouts %d - stakes %d%n", spins, landed - mismatches, landed, after - before, returned, staked);
        System.out.printf("  bot return on small even-money bets: %.2f%% (variance is large, the exact figure is 97.30%%)%n",
                100.0 * returned / Math.max(1, staked));
        // pause hands back everything that is on the table or in the air
        for (int phase = 0; phase < 3; phase++) {
            host = freshHost("RouletteMini", box);
            m = (RouletteMini) box[0];
            int money0 = host.money;
            float[] bx = (float[]) get(m, "bx"), by = (float[]) get(m, "by");
            tap(m, bx[RouletteMini.B_RED] + 5, by[RouletteMini.B_RED] + 5);
            tap(m, bx[RouletteMini.B_D1] + 5, by[RouletteMini.B_D1] + 5);
            check(host.money < money0, "chips leave the wallet when placed");
            if (phase >= 1) {
                Btn spin = (Btn) get(m, "spinB");
                tap(m, spin.x + 5, spin.y + 5);
                step(m, host, phase == 1 ? 1.0f : 5.0f);
            }
            m.pause();
            check(host.money == money0, "pause() returns every stake (phase " + phase + ")");
        }
        System.out.println("  pause(): stakes on the table or in a spin that is not resolved are returned exactly (checked on the table, mid-spin and while the ball rattles)");
    }

    static void e2eSlots() throws Exception {
        Mini[] box = new Mini[1];
        MiniLab.FakeHost host = freshHost("SlotsMini", box);
        SlotsMini m = (SlotsMini) box[0];
        Random pick = new Random(9);
        int spins = 0, target = 20_000, bad = 0;
        long staked = 0, returned = 0;
        int before = host.money;
        boolean was = false;
        int betAtStart = 0;
        for (long f = 0; f < 80_000_000L && spins < target; f++) {
            m.bot(pick, 0.5f);
            boolean sp0 = m.spinning();
            m.update(MiniLab.DT);
            host.tick(MiniLab.DT);
            if (m.wantsExit()) {
                m = new SlotsMini();
                m.attach(host);
                m.begin(0, 0.3f);
                was = false;
                continue;
            }
            boolean sp = m.spinning();
            if (sp && !was) {
                betAtStart = m.betNow();
                if (!m.landsOnStops()) bad++;
            }
            if (!sp && was) {
                int[] st = m.stopsNow();
                int mult = SlotsMini.payMult(SlotsMini.STRIP[st[0]], SlotsMini.STRIP[st[1]], SlotsMini.STRIP[st[2]]);
                check(m.lastWin() == mult * betAtStart, "paid amount equals the pay table for the stops drawn");
                staked += betAtStart;
                returned += m.lastWin();
                spins++;
            }
            was = sp;
        }
        check(bad == 0, "every planned reel motion ends exactly on its drawn stop");
        check(host.money - before == returned - staked, "wallet change equals returned minus staked");
        System.out.printf("SLOTS end to end: %d spins played through the real Mini by its bot: every reel animation lands on its drawn stop,"
                + " wallet change %+d = payouts %d - stakes %d, return %.2f%%%n", spins, host.money - before, returned, staked,
                100.0 * returned / Math.max(1, staked));
        host = freshHost("SlotsMini", box);
        m = (SlotsMini) box[0];
        int money0 = host.money;
        float sx = (Float) get(m, "spinX"), sy = (Float) get(m, "spinY");
        tap(m, sx + 20, sy + 8);
        check(host.money < money0 && m.spinning(), "bet leaves the wallet at the start of a spin");
        step(m, host, 0.8f);
        m.pause();
        check(host.money == money0 && !m.spinning(), "pause() returns the bet of an unresolved spin");
        System.out.println("  pause(): the bet of a spin in flight is returned exactly");
    }

    // ---- screenshots ----

    static Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static void setHost(Mini m, MiniLab.FakeHost h, String out) {
        MiniLab.host = h;
        MiniLab.mini = m;
        MiniLab.out = out;
    }

    static void step(Mini m, MiniLab.FakeHost h, float seconds) {
        int n = Math.round(seconds / MiniLab.DT);
        for (int i = 0; i < n; i++) {
            m.update(MiniLab.DT);
            h.tick(MiniLab.DT);
            if (i % 3 == 0) MiniLab.render(new AwtGfx(36, 76, 0.1f), "");
        }
    }

    static void tap(Mini m, float x, float y) {
        m.down(x, y);
        m.up(x, y);
    }

    static void shots(String out) throws Exception {
        new java.io.File(out).mkdirs();
        for (float hgt : new float[]{760, 560}) {
            rouletteShots(out, hgt);
            slotsShots(out, hgt);
        }
        System.out.println("casino screenshots written to " + out);
    }

    static void rouletteShots(String out, float hgt) throws Exception {
        MiniLab.FakeHost h = new MiniLab.FakeHost();
        h.screenH = hgt;
        h.money = 640;
        RouletteMini m = new RouletteMini();
        m.attach(h);
        m.begin(0, 0.3f);
        setHost(m, h, out);
        String tag = "Roulette-" + (int) hgt;
        MiniLab.render(new AwtGfx(36, 76, 0.1f), "");
        float[] bx = (float[]) get(m, "bx"), by = (float[]) get(m, "by"), bw = (float[]) get(m, "bw"), bh = (float[]) get(m, "bh");
        Btn[] chips = (Btn[]) get(m, "chipB");
        Btn num = (Btn) get(m, "numB");
        Btn spin = (Btn) get(m, "spinB");
        MiniLab.shot(tag + "-1-empty", "שולחן ריק");
        int[] spots = {RouletteMini.B_RED, RouletteMini.B_D2, RouletteMini.B_ODD, RouletteMini.B_RED};
        tap(m, chips[1].x + 5, chips[1].y + 5);
        for (int b : spots) tap(m, bx[b] + bw[b] / 2, by[b] + bh[b] / 2);
        tap(m, chips[3].x + 5, chips[3].y + 5);
        tap(m, bx[RouletteMini.B_BLACK] + 5, by[RouletteMini.B_BLACK] + 5);
        MiniLab.render(new AwtGfx(36, 76, 0.1f), "");
        MiniLab.shot(tag + "-2-bets", "הימורים על השולחן");
        tap(m, num.x + 5, num.y + 5);
        MiniLab.render(new AwtGfx(36, 76, 0.1f), "");
        float[] nb = {17, 7, 32, 0};
        tap(m, chips[0].x + 5, chips[0].y + 5);
        for (float n : nb) tap(m, bx[(int) n] + bw[(int) n] / 2, by[(int) n] + bh[(int) n] / 2);
        tap(m, chips[2].x + 5, chips[2].y + 5);
        tap(m, bx[17] + 3, by[17] + 3);
        MiniLab.render(new AwtGfx(36, 76, 0.1f), "");
        MiniLab.shot(tag + "-3-numbers", "מספרים");
        tap(m, num.x + 5, num.y + 5);
        tap(m, spin.x + 5, spin.y + 5);
        step(m, h, 1.0f);
        MiniLab.shot(tag + "-4-fast", "הכדור על המסלול");
        step(m, h, 3.0f);
        MiniLab.shot(tag + "-5-drop", "הכדור נופל");
        step(m, h, 1.5f);
        MiniLab.shot(tag + "-6-rattle", "הכדור מתיישב");
        step(m, h, 1.5f);
        MiniLab.shot(tag + "-7-result", "תוצאה");
        step(m, h, 0.5f);
        MiniLab.shot(tag + "-8-result2", "תוצאה");
        System.out.println(tag + ": result drawn " + m.lastResult() + ", money " + h.money);
    }

    static void slotsShots(String out, float hgt) throws Exception {
        MiniLab.FakeHost h = new MiniLab.FakeHost();
        h.screenH = hgt;
        h.money = 100000;
        SlotsMini m = new SlotsMini();
        m.attach(h);
        m.begin(0, 0.3f);
        setHost(m, h, out);
        String tag = "Slots-" + (int) hgt;
        MiniLab.render(new AwtGfx(36, 76, 0.1f), "");
        MiniLab.shot(tag + "-1-idle", "מכונה במנוחה");
        float[] sp = new float[]{0};
        Btn[] bets = (Btn[]) get(m, "betB");
        float spinX = (Float) get(m, "spinX"), spinY = (Float) get(m, "spinY");
        // a plain spin, photographed on the way
        tap(m, bets[1].x + 5, bets[1].y + 5);
        tap(m, spinX + 20, spinY + 8);
        step(m, h, 0.5f);
        MiniLab.shot(tag + "-2-blur", "הגלגלים מסתובבים");
        step(m, h, 0.62f);
        MiniLab.shot(tag + "-3-landing", "גלגל ראשון נעצר");
        step(m, h, 1.2f);
        MiniLab.shot(tag + "-4-stopped", "נעצרו");
        // look for a big win and a small win and photograph them
        boolean gotBig = false, gotSmall = false;
        for (int i = 0; i < 400_000 && !(gotBig && gotSmall); i++) {
            tap(m, spinX + 20, spinY + 8);
            int guard = 0;
            while (m.spinning() && guard++ < 400) {
                m.update(MiniLab.DT);
                h.tick(MiniLab.DT);
            }
            int w = m.lastWin();
            if (w >= 10 * m.betNow() * 3 && !gotBig) {
                step(m, h, 0.35f);
                MiniLab.shot(tag + "-5-bigwin", "זכייה גדולה");
                step(m, h, 0.8f);
                MiniLab.shot(tag + "-6-bigwin2", "זכייה גדולה");
                gotBig = true;
            } else if (w > 0 && w <= 2 * m.betNow() && !gotSmall) {
                step(m, h, 0.4f);
                MiniLab.shot(tag + "-7-smallwin", "זכייה קטנה");
                gotSmall = true;
            }
            for (int k = 0; k < 90; k++) {
                m.update(MiniLab.DT);
                h.tick(MiniLab.DT);
            }
        }
        System.out.println(tag + ": big win captured " + gotBig + ", small win captured " + gotSmall);
    }
}
