package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Doctor shift: four rushes of triage in a silly little clinic. Patients sit on the bench with their
 * symptom icons over their heads; send each one (drag, or tap the patient and then the station) to
 * the station whose sign shows those symptoms. Every station treats one patient at a time.
 *
 * The decision: patience drains on the bench. Urgent patients (siren) are worth double but give up
 * fast; two-step patients need two stations in order (1 then 2); a wrong station costs patience.
 * Later rushes add stations and look-alike symptoms. Icons are shared with {@link DoctorStudyMini}.
 */
final class DoctorMini extends Mini {
    private static final int SLOTS = 4;
    private static final int[] PATIENTS = {3, 4, 5, 6};
    private static final int[][] STATION_SETS = {{0, 3, 4}, {0, 3, 4, 5}, {0, 1, 2, 3}, {0, 1, 2, 3, 4}};
    private static final int DOOR = 0, SIT = 1, AWAY = 2, CURED = 3, ANGRY = 4, GONE = 5;
    private static final int[] SHIRTS = {0xFF5AA9FF, 0xFFFF9F5A, 0xFFB69CFF, 0xFF5BD68A, 0xFFF59BC8, 0xFFE9D86A};
    private static final int[] SKINS = {0xFFF2C9A1, 0xFFD9A06E, 0xFF9B6A45, 0xFFF7D9BE};
    private static final int HAIR = 0xFF3A2A22;

    private static final class Patient {
        int state = DOOR, slot = -1, step, skin, shirt, hair, station = -1;
        int[] need;
        int[] icons;
        boolean urgent;
        float arrive, pat, patMax, x = -40, outT, value, phase;
    }

    private static final class Station {
        int remedy;
        float x, w, busy, flashT;
        boolean flashBad;
        Patient who;
    }

    private final ArrayList<Patient> pts = new ArrayList<>();
    private final Patient[] seat = new Patient[SLOTS];
    private final Station[] st = new Station[5];
    private int ns = 3, wrongs, lostUrgent, cured, lost;
    private float diff, roundT, endT, treatDur, earned, maxPts, time, botNext;
    private Patient sel, drag;
    private float dragX, dragY, downX, downY;
    private boolean moved;

    // layout
    private float top = 166, bottom = 664, benchY, stTop, stH, ps = 1;

    DoctorMini() {
        for (int i = 0; i < st.length; i++) st[i] = new Station();
    }

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("רופא", "רופאה");
    }

    @Override
    String howTo() {
        return "גוררים כל חולה לעמדה שהסימנים שלה כמו התסמינים שלו. סירנה אומרת דחוף, וחולה עם 1 ו-2 עובר שתי עמדות לפי הסדר.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = Ui.clamp(difficulty, 0, 1);
        int r = Math.max(0, Math.min(3, round));
        Random rn = h.rnd();
        int[] set = STATION_SETS[r];
        ns = set.length;
        for (int i = 0; i < ns; i++) {
            st[i].remedy = set[i];
            st[i].busy = 0;
            st[i].flashT = 0;
            st[i].who = null;
        }
        pts.clear();
        for (int i = 0; i < SLOTS; i++) seat[i] = null;
        sel = drag = null;
        wrongs = lostUrgent = cured = lost = 0;
        roundT = endT = earned = maxPts = 0;
        treatDur = 1.0f - 0.15f * diff;
        int n = PATIENTS[r] + (diff > 0.75f ? 1 : 0);
        int urgents = r == 0 ? 0 : r == 1 ? 1 : 2;
        int twoSteps = r < 2 ? 0 : r == 2 ? 1 : 2;
        float gap = 1.2f - 0.3f * diff;
        float base = 5.4f - 1.0f * diff;
        int[] kind = new int[n]; // 0 plain, 1 urgent, 2 two-step
        for (int i = 0; i < urgents && i < n; i++) kind[1 + rn.nextInt(n - 1)] = 1;
        for (int i = 0, placed = 0; i < 20 && placed < twoSteps; i++) {
            int at = 1 + rn.nextInt(Math.max(1, n - 1));
            if (at < n && kind[at] == 0) {
                kind[at] = 2;
                placed++;
            }
        }
        int lastRem = -1;
        for (int i = 0; i < n; i++) {
            Patient p = new Patient();
            p.arrive = 0.2f + i * gap + rn.nextFloat() * 0.4f;
            p.skin = rn.nextInt(SKINS.length);
            p.shirt = rn.nextInt(SHIRTS.length);
            p.hair = rn.nextInt(5);
            p.phase = rn.nextFloat() * 6;
            int a = set[rn.nextInt(ns)];
            if (a == lastRem) a = set[rn.nextInt(ns)];
            lastRem = a;
            if (kind[i] == 2) {
                int b = a;
                while (b == a) b = set[rn.nextInt(ns)];
                p.need = new int[]{a, b};
                p.icons = new int[]{DoctorStudyMini.Med.symptomOf(a, rn.nextInt(2)),
                        DoctorStudyMini.Med.symptomOf(b, rn.nextInt(2))};
                p.value = 1.6f;
                p.patMax = base * 1.35f;
            } else {
                p.need = new int[]{a};
                int s0 = DoctorStudyMini.Med.symptomOf(a, 0), s1 = DoctorStudyMini.Med.symptomOf(a, 1);
                boolean single = r < 2 && rn.nextInt(5) < 2;
                if (single) p.icons = new int[]{rn.nextBoolean() ? s0 : s1};
                else p.icons = rn.nextBoolean() ? new int[]{s0, s1} : new int[]{s1, s0};
                p.urgent = kind[i] == 1;
                p.value = p.urgent ? 2f : 1f;
                p.patMax = p.urgent ? base * 0.55f : base;
            }
            p.pat = p.patMax;
            maxPts += p.value;
            pts.add(p);
        }
        botNext = time + 0.8f;
        lay(166, Math.max(300, h.screenH()) - 96);
    }

    // ------------------------------------------------------------------ simulation

    @Override
    int update(float dt) {
        time += dt;
        roundT += dt;
        int open = 0;
        for (Patient p : pts) {
            p.phase += dt;
            switch (p.state) {
                case DOOR:
                    open++;
                    if (roundT >= p.arrive) sitDown(p);
                    break;
                case SIT:
                    open++;
                    p.x += (slotX(p.slot) - p.x) * Math.min(1f, dt * 9);
                    p.pat -= dt;
                    if (p.pat <= 0) leaveAngry(p);
                    break;
                case AWAY:
                    open++;
                    break;
                case CURED:
                case ANGRY:
                    p.outT -= dt;
                    if (p.outT <= 0) p.state = GONE;
                    else open++;
                    break;
                default:
                    break;
            }
        }
        for (int i = 0; i < ns; i++) {
            Station s = st[i];
            if (s.flashT > 0) s.flashT -= dt;
            if (s.who == null) continue;
            s.busy -= dt;
            if (s.busy <= 0) finishTreatment(s);
        }
        if (open > 0) return RUNNING;
        endT += dt;
        if (endT < 0.35f) return RUNNING;
        float frac = maxPts <= 0 ? 1 : Ui.clamp((earned - 0.6f * lostUrgent) / maxPts, 0, 1);
        if (frac >= 0.88f && wrongs == 0 && lostUrgent == 0) return PERFECT;
        return frac >= 0.4f ? OK : MISS;
    }

    private float slotX(int i) {
        return 45 + i * 90;
    }

    private void sitDown(Patient p) {
        for (int i = 0; i < SLOTS; i++) {
            if (seat[i] != null) continue;
            seat[i] = p;
            p.slot = i;
            p.state = SIT;
            p.x = -30;
            h.sfx(p.urgent ? Sfx.SIREN : Sfx.DOOR);
            if (p.urgent) h.text(slotX(i), benchY - 112 * ps, "דחוף!", Ui.BAD);
            return;
        }
    }

    private void freeSeat(Patient p) {
        if (p.slot >= 0 && seat[p.slot] == p) seat[p.slot] = null;
    }

    private void leaveAngry(Patient p) {
        p.state = ANGRY;
        p.outT = 0.7f;
        lost++;
        freeSeat(p);
        if (sel == p) sel = null;
        if (drag == p) drag = null;
        if (p.urgent) lostUrgent++;
        h.text(p.x, benchY - 100 * ps, p.urgent ? "הוא כועס!" : "עזב", Ui.BAD);
        h.shake(p.urgent ? 7 : 4);
        h.sfx(Sfx.BAD);
    }

    private void finishTreatment(Station s) {
        Patient p = s.who;
        s.who = null;
        s.busy = 0;
        p.step++;
        float sx = Ui.clamp(s.x + s.w / 2, 56, W - 56), sy = stTop + 30;
        h.burst(sx, sy, DoctorStudyMini.Med.REM_COL[s.remedy], 12);
        if (p.step >= p.need.length) {
            p.state = CURED;
            p.outT = 0.7f;
            cured++;
            earned += p.value;
            freeSeat(p);
            h.text(sx, sy - 24, p.urgent ? "הצלה!" : p.need.length > 1 ? "שני צעדים!" : "נרפא!",
                    p.urgent || p.need.length > 1 ? Ui.ACCENT : Ui.GOOD);
            h.sfx(p.urgent || p.need.length > 1 ? Sfx.PERFECT : Sfx.COIN);
        } else {
            p.state = SIT;
            p.pat = p.patMax * 0.7f;
            h.text(sx, sy - 24, "עוד צעד", Ui.BLUE);
            h.sfx(Sfx.GOOD);
        }
    }

    /** Tries to send patient p to station i. Returns false when it was refused. */
    private boolean send(Patient p, int i) {
        if (p == null || p.state != SIT || i < 0 || i >= ns) return false;
        Station s = st[i];
        float sx = Ui.clamp(s.x + s.w / 2, 56, W - 56);
        if (s.who != null) {
            h.text(sx, stTop - 6, "תפוס", Ui.DIM);
            h.sfx(Sfx.TICK);
            return false;
        }
        if (s.remedy != p.need[p.step]) {
            wrongs++;
            p.pat -= p.patMax * 0.3f;
            s.flashT = 0.5f;
            s.flashBad = true;
            h.text(sx, stTop - 6, "לא פה!", Ui.BAD);
            h.shake(4);
            h.sfx(Sfx.BAD);
            sel = null;
            if (p.pat <= 0) leaveAngry(p);
            return true;
        }
        s.who = p;
        s.busy = treatDur;
        s.flashT = 0.3f;
        s.flashBad = false;
        p.state = AWAY;
        p.station = i;
        if (sel == p) sel = null;
        h.sfx(Sfx.POUR);
        return true;
    }

    // ------------------------------------------------------------------ touch

    private Patient patientAt(float x, float y) {
        float y0 = benchY - 128 * ps, y1 = benchY + 12;
        if (y < y0 || y > y1) return null;
        for (Patient p : pts) {
            if (p.state == SIT && Math.abs(x - slotX(p.slot)) <= 45) return p;
        }
        return null;
    }

    private int stationAt(float x, float y) {
        if (y < stTop - 18 || y > stTop + stH + 4) return -1;
        for (int i = 0; i < ns; i++) if (x >= st[i].x - 3 && x <= st[i].x + st[i].w + 3) return i;
        return -1;
    }

    @Override
    void down(float x, float y) {
        downX = dragX = x;
        downY = dragY = y;
        moved = false;
        drag = null;
        Patient p = patientAt(x, y);
        if (p != null) {
            drag = p;
            sel = p;
            h.sfx(Sfx.TAP);
            return;
        }
        int i = stationAt(x, y);
        if (i >= 0 && sel != null) send(sel, i);
    }

    @Override
    void move(float x, float y) {
        dragX = x;
        dragY = y;
        if (Math.abs(x - downX) + Math.abs(y - downY) > 14) moved = true;
    }

    @Override
    void up(float x, float y) {
        if (drag != null && moved) {
            int i = stationAt(x, y);
            if (i >= 0) send(drag, i);
        }
        drag = null;
    }

    @Override
    void pause() {
        drag = null;
    }

    // ------------------------------------------------------------------ drawing

    private void lay(float t0, float b0) {
        top = t0;
        bottom = b0;
        float H = bottom - top;
        stH = Ui.clamp(H * 0.36f, 96, 144);
        stTop = bottom - stH - 22;
        benchY = stTop - 22;
        ps = Ui.clamp((benchY - top) / 150f, 0.85f, 2.0f);
        float gap = 6, bw = (W - 8 - (ns - 1) * gap) / ns;
        for (int i = 0; i < ns; i++) {
            st[i].w = bw;
            st[i].x = 4 + i * (bw + gap);
        }
    }

    @Override
    void draw(Gfx c, float t0, float b0) {
        lay(t0, b0);
        drawRoom(c);
        // bench back rail, then patients, then the seat front hides their legs
        c.fillRoundRect(8, benchY - 16 * ps, W - 16, 7 * ps, 3, 0xFF6B4A2B);
        for (int i = 0; i < SLOTS; i++) {
            if (seat[i] != null && seat[i].state == AWAY) ghost(c, slotX(i));
        }
        for (Patient p : pts) {
            if (p.state == SIT || p.state == ANGRY) drawSeated(c, p);
        }
        drawBench(c);
        for (Patient p : pts) {
            if (p.state == SIT) drawBubble(c, p);
        }
        for (int i = 0; i < ns; i++) drawStation(c, st[i], i);
        for (Patient p : pts) {
            if (p.state == CURED) drawCured(c, p);
        }
        if (drag != null && drag.state == SIT && moved) {
            drawPerson(c, dragX, dragY + 18, ps * 0.9f, drag, 0.5f, true);
        }
        drawDots(c);
    }

    private void drawRoom(Gfx c) {
        c.fillRect(0, top, W, benchY + 10 - top, 0xFF24323A);
        // wainscot and floor
        c.fillRect(0, benchY - 6, W, 18, 0xFF2E4350);
        c.fillRect(0, benchY + 12, W, bottom - benchY - 12, 0xFF2A2830);
        for (int i = 0; i < 9; i++) c.fillRect(i * 45, benchY + 12, 2, bottom - benchY - 12, 0xFF302E37);
        float room = benchY - 112 * ps - top - 6;
        if (room > 24) {
            float dr = Ui.clamp(room / 2, 12, 30), cy = top + 6 + dr;
            // wall clock
            c.fillCircle(W / 2, cy, dr, 0xFFEDE6D6);
            c.fillCircle(W / 2, cy, dr - 3, 0xFF1B222C);
            float a = time * 0.2f;
            c.line(W / 2, cy, W / 2 + (float) Math.sin(a) * dr * 0.7f, cy - (float) Math.cos(a) * dr * 0.7f, 2, 0xFFEDE6D6);
            c.line(W / 2, cy, W / 2 + (float) Math.sin(a / 12) * dr * 0.45f, cy - (float) Math.cos(a / 12) * dr * 0.45f, 2.6f, 0xFFEDE6D6);
            // health poster with a green plus
            float pw = dr * 1.5f;
            c.fillRoundRect(W - 24 - pw, top + 6, pw, dr * 2, 4, 0xFFEDE6D6);
            c.fillRect(W - 24 - pw / 2 - 3, top + 6 + dr - pw * 0.3f, 6, pw * 0.6f, 0xFF3FB27F);
            c.fillRect(W - 24 - pw / 2 - pw * 0.3f, top + 6 + dr - 3, pw * 0.6f, 6, 0xFF3FB27F);
            // eye chart: rows of ever smaller marks
            float cw = dr * 1.4f, cx0 = 92;
            c.fillRoundRect(cx0, top + 6, cw, dr * 2, 4, 0xFFEDE6D6);
            for (int i = 0; i < 4; i++) {
                float lw = cw * (0.7f - i * 0.14f), ly = top + 6 + dr * 0.35f + i * dr * 0.42f;
                c.fillRoundRect(cx0 + (cw - lw) / 2, ly, lw, 2 + (3 - i) * 1.1f, 1, 0xFF2B2F33);
            }
            // plant
            float px = 30;
            c.fillPoly(new float[]{px - 8, cy + dr, px + 8, cy + dr, px + 6, cy + dr - 12, px - 6, cy + dr - 12}, 0xFFB5673A);
            for (int i = -1; i <= 1; i++) {
                float sw = (float) Math.sin(time * 1.5f + i) * 1.5f;
                c.fillPoly(new float[]{px, cy + dr - 12, px + i * 11 + sw, cy + dr - 30, px + i * 11 + 5 + sw, cy + dr - 14}, 0xFF3FA66A);
            }
        }
        // station row backdrop
        c.fillRect(0, stTop - 20, W, bottom - stTop + 20, 0xFF1A2027);
    }

    private void drawBench(Gfx c) {
        c.fillRoundRect(4, benchY - 3, W - 8, 11 * ps, 4, 0xFF8A6238);
        c.fillRect(4, benchY + 6 * ps, W - 8, 4, 0xFF5E4122);
        for (int i = 0; i < 3; i++) c.fillRect(24 + i * 150, benchY + 8 * ps, 7, 12, 0xFF4A3319);
    }

    private void ghost(Gfx c, float x) {
        c.fillCircle(x, benchY - 33 * ps, 9.5f * ps, 0x16FFFFFF);
        c.fillRoundRect(x - 10 * ps, benchY - 24 * ps, 20 * ps, 24 * ps, 5, 0x16FFFFFF);
    }

    private void drawSeated(Gfx c, Patient p) {
        float x = p.x, bob = (float) Math.sin(p.phase * 2.2f) * 0.8f * ps;
        float mood = p.state == ANGRY ? 0 : Ui.clamp(p.pat / p.patMax, 0, 1);
        if (p.state == ANGRY) x -= (0.7f - p.outT) * 260;
        float shake = mood < 0.3f && p.state == SIT ? (float) Math.sin(time * 40) * 1.4f : 0;
        drawPerson(c, x + shake, benchY + bob, ps, p, mood, false);
        if (p.state == SIT) {
            if (p == sel) {
                float hw = Math.min(24 * ps, 40f);
                c.strokeRoundRect(x - hw, benchY - 46 * ps, hw * 2, 56 * ps, 10, 2.4f, Ui.ACCENT);
            }
        }
    }

    /** A patient: torso, head, hair, eyes and a mouth that follows the mood (1 happy .. 0 furious). */
    private void drawPerson(Gfx c, float x, float yb, float s, Patient p, float mood, boolean lifted) {
        int shirt = SHIRTS[p.shirt], skin = SKINS[p.skin];
        float hy = yb - 33 * s;
        if (p.hair == 4) c.fillRoundRect(x - 11 * s, hy - 7 * s, 22 * s, 26 * s, 8 * s, HAIR);
        c.fillPoly(new float[]{x - 9 * s, yb - 24 * s, x + 9 * s, yb - 24 * s, x + 11 * s, yb, x - 11 * s, yb}, shirt);
        c.fillCircle(x, yb - 24 * s, 9 * s, shirt);
        c.fillCircle(x, hy, 9.5f * s, skin);
        switch (p.hair) {
            case 1:
                c.fillPoly(new float[]{x - 10 * s, hy - 1 * s, x - 7 * s, hy - 9 * s, x + 7 * s, hy - 9 * s, x + 10 * s, hy - 1 * s,
                        x + 5 * s, hy - 4 * s, x - 5 * s, hy - 4 * s}, HAIR);
                break;
            case 2:
                c.fillCircle(x, hy - 11 * s, 4.4f * s, HAIR);
                c.fillPoly(new float[]{x - 9 * s, hy - 2 * s, x - 6 * s, hy - 8 * s, x + 6 * s, hy - 8 * s, x + 9 * s, hy - 2 * s}, HAIR);
                break;
            case 3:
                c.fillPoly(new float[]{x - 9 * s, hy - 3 * s, x - 7 * s, hy - 13 * s, x - 3 * s, hy - 7 * s, x, hy - 14 * s,
                        x + 3 * s, hy - 7 * s, x + 7 * s, hy - 13 * s, x + 9 * s, hy - 3 * s}, HAIR);
                break;
            case 4:
                c.fillPoly(new float[]{x - 9.5f * s, hy, x - 6 * s, hy - 8 * s, x + 6 * s, hy - 8 * s, x + 9.5f * s, hy,
                        x + 4 * s, hy - 3 * s, x - 4 * s, hy - 3 * s}, HAIR);
                break;
            default:
                break;
        }
        boolean blink = ((int) (p.phase * 2.5f + p.shirt)) % 9 == 0 && (p.phase * 2.5f) % 1 < 0.25f;
        float ey = hy - 1 * s;
        if (blink) {
            c.line(x - 5 * s, ey, x - 2 * s, ey, 1.2f * s, 0xFF222222);
            c.line(x + 2 * s, ey, x + 5 * s, ey, 1.2f * s, 0xFF222222);
        } else {
            c.fillCircle(x - 3.4f * s, ey, 1.5f * s, 0xFF222222);
            c.fillCircle(x + 3.4f * s, ey, 1.5f * s, 0xFF222222);
        }
        float my = hy + 4.5f * s;
        float corner = mood > 0.6f ? my - 1.5f * s : mood < 0.3f ? my + 1.8f * s : my;
        float mid = mood > 0.6f ? my + 1.8f * s : mood < 0.3f ? my - 1.2f * s : my;
        c.line(x - 3.6f * s, corner, x, mid, 1.3f * s, 0xFF3A1D14);
        c.line(x, mid, x + 3.6f * s, corner, 1.3f * s, 0xFF3A1D14);
        if (mood < 0.3f) {
            c.line(x - 7 * s, ey - 4 * s, x - 1.5f * s, ey - 2 * s, 1.4f * s, 0xFF222222);
            c.line(x + 7 * s, ey - 4 * s, x + 1.5f * s, ey - 2 * s, 1.4f * s, 0xFF222222);
            float dropY = hy - 8 * s + ((time * 14) % 10) * s;
            c.fillPoly(new float[]{x + 10 * s, dropY - 3 * s, x + 12 * s, dropY + 1 * s, x + 8 * s, dropY + 1 * s}, 0xFF8FD3FF);
        }
        if (lifted) c.fillCircle(x, yb + 4 * s, 2 * s, 0x44FFFFFF);
    }

    private void drawBubble(Gfx c, Patient p) {
        float x = p.x, s = ps;
        float mood = Ui.clamp(p.pat / p.patMax, 0, 1);
        int n = p.icons.length;
        boolean two = p.need.length == 2;
        float ri = Math.min(14 * s, 16.5f);
        float bwid = two ? 4 * ri + 22 : n * (2 * ri + 4) + 8;
        float bh = 2 * ri + 10 + (two ? 12 : 0);
        float yTop = benchY - 58 * s - bh - 6 * s;
        float jig = p.urgent ? (float) Math.sin(time * 18) * 1.6f : 0;
        float bx = x - bwid / 2 + jig;
        c.fillRoundRect(bx, yTop, bwid, bh, 10, 0xFFEFEAE0);
        c.fillPoly(new float[]{x - 6, yTop + bh - 1, x + 6, yTop + bh - 1, x, yTop + bh + 8 * s}, 0xFFEFEAE0);
        if (p.urgent) c.strokeRoundRect(bx, yTop, bwid, bh, 10, 3, ((int) (time * 6)) % 2 == 0 ? Ui.BAD : Ui.ORANGE);
        float cy = yTop + bh / 2 - (two ? 5 : 0);
        if (two) {
            float x1 = x - ri - 5, x2 = x + ri + 5;
            boolean firstDone = p.step >= 1;
            DoctorStudyMini.Med.sym(c, p.icons[0], x1 + jig, cy, ri);
            DoctorStudyMini.Med.sym(c, p.icons[1], x2 + jig, cy, ri);
            c.fillPoly(new float[]{x - 4, cy - 6, x + 4, cy, x - 4, cy + 6}, 0xFF2B2F33);
            c.text("1", x1, yTop + bh - 4, 12, 0xFF2B2F33, Gfx.CENTER);
            c.text("2", x2, yTop + bh - 4, 12, 0xFF2B2F33, Gfx.CENTER);
            if (firstDone) {
                c.fillCircle(x1 + jig, cy, ri, 0xDDEFEAE0);
                c.line(x1 - 6, cy, x1 - 1, cy + 5, 3, Ui.GOOD);
                c.line(x1 - 1, cy + 5, x1 + 7, cy - 6, 3, Ui.GOOD);
            }
        } else {
            for (int i = 0; i < n; i++) {
                float ix = x - (n - 1) * (ri + 2) + i * (2 * ri + 4);
                DoctorStudyMini.Med.sym(c, p.icons[i], ix + jig, cy, ri);
            }
        }
        // patience: a bar over the bubble, shrinking
        float barW = Math.max(40, bwid);
        Ui.bar(c, x - barW / 2, yTop - 8, barW, 5, mood, mood > 0.6f ? Ui.GOOD : mood > 0.3f ? Ui.ACCENT : Ui.BAD);
        if (p.urgent) drawSiren(c, bx + bwid - 4, yTop - 4, s);
    }

    private void drawSiren(Gfx c, float x, float y, float s) {
        boolean on = ((int) (time * 6)) % 2 == 0;
        c.fillPoly(new float[]{x - 9 * s, y, x - 7 * s, y - 10 * s, x - 3 * s, y - 14 * s, x + 3 * s, y - 14 * s, x + 7 * s, y - 10 * s,
                x + 9 * s, y}, on ? Ui.BAD : 0xFFB84A4A);
        c.fillRect(x - 11 * s, y, 22 * s, 4 * s, 0xFF5E6772);
        c.line(x, y - 10 * s, x, y - 4 * s, 1.6f * s, 0xFFFFFFFF);
        c.fillCircle(x, y - 2 * s, 1.1f * s, 0xFFFFFFFF);
        if (on) {
            c.line(x - 14 * s, y - 12 * s, x - 11 * s, y - 9 * s, 1.6f * s, Ui.BAD);
            c.line(x + 14 * s, y - 12 * s, x + 11 * s, y - 9 * s, 1.6f * s, Ui.BAD);
        }
    }

    private void drawStation(Gfx c, Station s, int i) {
        int col = DoctorStudyMini.Med.REM_COL[s.remedy];
        float x = s.x, w = s.w, y = stTop;
        boolean busy = s.who != null;
        // booth body and striped awning
        c.fillRoundRect(x, y, w, stH, 10, busy ? 0xFF232B35 : 0xFF2C3644);
        float ss = Ui.clamp(stH / 110f, 0.9f, 1.3f), aw = 13 * ss;
        for (int k = 0; k < 4; k++) {
            c.fillRect(x + k * w / 4, y, w / 4 + 0.6f, aw, k % 2 == 0 ? col : 0xFFEFEAE0);
        }
        c.fillRect(x, y + aw, w, 3, 0x44000000);
        float rr = Math.min(18 * ss, w * 0.27f);
        float bob = busy ? 0 : (float) Math.sin(time * 2.4f + i * 1.3f) * 1.2f;
        DoctorStudyMini.Med.rem(c, s.remedy, x + w / 2, y + aw + 6 + rr + bob, rr);
        // the key: the two symptoms this station cures
        float sr = Math.min(10.5f * ss, (w - 10) / 4 - 1);
        float sy = y + aw + 6 + rr * 2 + 6 + sr;
        DoctorStudyMini.Med.sym(c, DoctorStudyMini.Med.symptomOf(s.remedy, 0), x + w / 2 - sr - 2, sy, sr);
        DoctorStudyMini.Med.sym(c, DoctorStudyMini.Med.symptomOf(s.remedy, 1), x + w / 2 + sr + 2, sy, sr);
        // counter
        float cyy = y + stH - 14;
        c.fillRoundRect(x, cyy, w, 14, 6, 0xFF8A6238);
        if (busy) {
            Patient p = s.who;
            drawPerson(c, x + w / 2, cyy + 3, 0.62f * ss, p, 0.9f, false);
            c.fillRoundRect(x, cyy, w, 14, 6, 0xFF8A6238);
            Ui.bar(c, x + 6, cyy + 4, w - 12, 6, 1 - s.busy / treatDur, Ui.GOOD);
        }
        if (s.flashT > 0) {
            int fc = s.flashBad ? Ui.BAD : Ui.GOOD;
            c.strokeRoundRect(x, y, w, stH, 10, 3.5f, fc);
            if (s.flashBad) {
                float cx = x + w / 2, cy = y + stH * 0.45f;
                c.line(cx - 10, cy - 10, cx + 10, cy + 10, 4, Ui.BAD);
                c.line(cx + 10, cy - 10, cx - 10, cy + 10, 4, Ui.BAD);
            }
        } else if (drag != null && moved && Math.abs(dragX - (x + w / 2)) < w / 2 && dragY > y - 20) {
            c.strokeRoundRect(x, y, w, stH, 10, 2.5f, Ui.INK);
        } else {
            c.strokeRoundRect(x, y, w, stH, 10, 1.4f, 0x66FFFFFF & col | 0x55000000);
        }
    }

    private void drawCured(Gfx c, Patient p) {
        Station s = st[Math.max(0, Math.min(ns - 1, p.station))];
        float f = 1 - p.outT / 0.7f;
        float x = s.x + s.w / 2, y = stTop - 8 - f * 50;
        int a = (int) (255 * (1 - f)) << 24;
        c.fillPoly(new float[]{x, y, x - 8, y - 8, x - 4, y - 14, x, y - 10, x + 4, y - 14, x + 8, y - 8}, (Ui.GOOD & 0xFFFFFF) | a);
    }

    private void drawDots(Gfx c) {
        int n = pts.size();
        float y = bottom - 10, x0 = W / 2 - (n - 1) * 8;
        for (int i = 0; i < n; i++) {
            Patient p = pts.get(i);
            float x = x0 + i * 16;
            if (p.state == CURED || p.state == GONE && p.step >= p.need.length) {
                c.fillCircle(x, y, 5, Ui.GOOD);
            } else if (p.state == ANGRY || p.state == GONE) {
                c.line(x - 4, y - 4, x + 4, y + 4, 2.2f, Ui.BAD);
                c.line(x + 4, y - 4, x - 4, y + 4, 2.2f, Ui.BAD);
            } else {
                c.fillCircle(x, y, 5, Ui.DIM);
                c.fillCircle(x, y, 3, 0xFF1A2027);
            }
        }
    }

    @Override
    void art(Gfx c, float cx, float cy, float tm) {
        // a booth with a patient waiting in front of it
        float w = 74, x = cx + 8;
        c.fillRoundRect(x - w / 2, cy - 48, w, 96, 10, 0xFF2C3644);
        for (int k = 0; k < 4; k++) c.fillRect(x - w / 2 + k * w / 4, cy - 48, w / 4 + 0.6f, 14, k % 2 == 0 ? DoctorStudyMini.Med.REM_COL[0] : 0xFFEFEAE0);
        DoctorStudyMini.Med.rem(c, 0, x, cy - 14, 18);
        DoctorStudyMini.Med.sym(c, 3, x - 12, cy + 24, 11);
        DoctorStudyMini.Med.sym(c, 0, x + 12, cy + 24, 11);
        float bob = (float) Math.sin(tm * 3) * 2;
        c.fillRoundRect(cx - 106, cy - 78 + bob, 68, 38, 9, 0xFFEFEAE0);
        DoctorStudyMini.Med.sym(c, 0, cx - 88, cy - 59 + bob, 14);
        DoctorStudyMini.Med.sym(c, 3, cx - 56, cy - 59 + bob, 14);
        Patient p = new Patient();
        p.shirt = 1;
        p.hair = 3;
        p.phase = tm;
        drawPerson(c, cx - 72, cy + 40, 1.3f, p, 0.5f, false);
    }

    // ------------------------------------------------------------------ bot

    @Override
    void bot(Random r, float skill) {
        if (time < botNext || drag != null) return;
        botNext = time + 0.25f + (1 - skill) * 1.3f + r.nextFloat() * 0.35f;
        if (sel != null && sel.state == SIT) {
            int target = -1;
            if (r.nextFloat() < 0.42f + 0.57f * skill) {
                for (int i = 0; i < ns; i++) if (st[i].remedy == sel.need[sel.step]) target = i;
            } else {
                target = r.nextInt(ns);
            }
            if (target >= 0) {
                float x = st[target].x + st[target].w / 2, y = stTop + 30;
                down(x, y);
                up(x, y);
            }
            return;
        }
        Patient best = null;
        float bestScore = 1e9f;
        for (Patient p : pts) {
            if (p.state != SIT) continue;
            // An expert triages by how little patience is left, weighted by value; a novice picks at random.
            float key = skill > 0.5f ? p.pat / p.value * (0.6f + 0.4f * r.nextFloat()) : r.nextFloat() * 10;
            if (key < bestScore) {
                bestScore = key;
                best = p;
            }
        }
        if (best == null) return;
        float x = slotX(best.slot), y = benchY - 20;
        down(x, y);
        up(x, y);
    }
}
