package com.wizzcraft.ima.core;

import java.util.List;
import java.util.Random;

/**
 * Law school study session: "find the contradiction". The lecturer writes one or two facts on the
 * board; below them sit two or three statement cards, and exactly one of them cannot be true given
 * the facts. Tap it. Fast, correct answers in a row fill the understanding bar; a wrong guess drains
 * it and breaks the streak, so guessing is a gamble against reading. The lesson lasts 30 seconds and
 * the bar at the bell is how much of it sank in.
 */
final class LawStudyMini extends Mini {
    private static final float SESSION = 30f;
    /** Understanding needed at the bell. */
    private static final float OK_AT = 0.22f, PERFECT_AT = 0.7f;
    private static final float CARD_IN = 0.35f;

    /**
     * fact, second fact (may be empty), the claim that cannot be true, a plainly compatible claim, and
     * a compatible claim that only sounds wrong (used as difficulty rises).
     */
    private static final String[][] BANK = {
            {"כל דיירי קומה 2 מגדלים חתולים", "יוסי גר בקומה 2", "ליוסי אין שום חיית מחמד", "יוסי אוהב לבשל",
                    "החתול של יוסי לא סובל את יוסי"},
            {"המאפייה פתוחה רק בימי שלישי", "דנה קנתה שם לחם אתמול", "אתמול היה יום ראשון", "דנה אוהבת לחם טרי",
                    "אתמול דנה לא הלכה לעבודה"},
            {"את המחסן פותחים רק עם מפתח", "לאבי אין מפתח למחסן", "אבי פתח את המחסן הבוקר", "רינה ביקשה מפתח מהוועד",
                    "אבי עמד ליד המחסן הבוקר"},
            {"בישיבת הוועד כולם הצביעו בעד", "מאיר היה בישיבה", "מאיר הצביע נגד", "מאיר הביא בורקס לישיבה",
                    "מאיר הצביע בעד בלי להקשיב"},
            {"הכלב נובח רק כשיורד גשם", "השבוע לא ירד גשם", "הכלב נבח ביום שלישי השבוע", "השבוע היה שמש",
                    "השבוע הכלב ממש רצה לנבוח"},
            {"העוגה נאכלה לפני הצהריים", "גדי הגיע רק בערב", "גדי אכל את העוגה", "גדי מת על עוגות",
                    "גדי מצא רק צלחת ריקה"},
            {"בבניין יש רק מעלית אחת", "המעלית תקועה מהבוקר עד עכשיו", "בצהריים עליתי במעלית לקומה 5",
                    "הדיירים עולים היום במדרגות", "בצהריים מישהו נתקע במעלית"},
            {"לרותי יש רק אופניים כחולים", "", "רותי רכבה היום על אופניים אדומים", "רותי רוכבת כל בוקר",
                    "רותי רכבה היום עם קסדה אדומה"},
            {"הדואר מגיע רק בבוקר", "החבילה הגיעה בדואר", "החבילה הגיעה בעשר בלילה", "החבילה הייתה כבדה",
                    "פתחתי את החבילה בעשר בלילה"},
            {"אורי צמחוני כבר עשר שנים", "", "אורי אכל אתמול שניצל עוף", "אורי אוהב חומוס",
                    "אורי אכל אתמול שניצל תירס"},
            {"משה הכי גבוה בבניין", "שרה גבוהה ממשה", "שרה גרה בקומה 3 בבניין", "שרה משחקת כדורסל",
                    "שרה מבקרת בבניין כל יום"},
            {"הוועד מתכנס רק בימי חמישי", "היום יום ראשון", "הוועד מתכנס עכשיו בלובי", "בחמישי יש ישיבת ועד",
                    "השכנים מתווכחים עכשיו בלובי"},
            {"לחתול של נגה אין זנב", "", "החתול של נגה כשכש בזנב", "החתול של נגה ג׳ינג׳י",
                    "החתול של נגה כשכש באוזניים"},
            {"שכר הדירה 3000 ₪ בחודש", "דן שילם החודש 2000 ₪", "דן שילם את כל השכירות", "דן ביקש הנחה מבעל הבית",
                    "דן שילם בזמן, כמו תמיד"},
            {"בתקנון: אסור לנגן אחרי עשר", "אלון תופף בחצות", "אלון לא עבר על התקנון", "אלון מתופף כבר שנים",
                    "אלון תופף בשקט, יחסית"},
            {"כל הדלתות בקומה אדומות", "ליה גרה בקומה", "הדלת של ליה ירוקה", "לליה יש פעמון חדש",
                    "על הדלת של ליה תלוי שלט ירוק"},
            {"התאנה נותנת פרי רק בקיץ", "עכשיו חורף", "קטפתי הבוקר תאנים טריות מהעץ", "לעץ אין עכשיו עלים",
                    "אכלתי הבוקר ריבת תאנים"},
            {"רק השכנה מקומה 4 שרה בבקרים", "הבוקר מישהו שר", "השכנה מקומה 4 בחו״ל כל החודש",
                    "השירה הייתה ממש מזייפת", "הבוקר השכנה שרה בלי מילים"},
            {"הגמד בלובי עשוי מגבס", "גבס לא זז לבד", "הגמד הלך לבד לחדר המדרגות", "הגמד צבוע באדום",
                    "מישהו העביר את הגמד למדרגות"},
            {"תמר אומרת רק אמת", "תמר אמרה: הקופסה אצל רחל", "הקופסה בכלל אצל תמר", "תמר מכירה את רחל",
                    "רחל לא יודעת שהקופסה אצלה"},
            {"לבן יש רק זוג נעליים אחד", "הנעליים של בן לבנות", "בן הלך היום בנעליים שחורות", "בן הולך הרבה ברגל",
                    "בן הלך היום בגרביים שחורות"},
            {"השעון בלובי מקדים בשעה", "השעון מראה עכשיו שמונה", "עכשיו שמונה בדיוק", "השעון תלוי מעל הדלת",
                    "עכשיו בערך שבע"},
            {"בבניין אין אף חתול", "ירון גר בבניין", "לירון יש חתול בדירה", "לירון יש כלב",
                    "לירון יש חתול, אצל אמא שלו"},
            {"המכולת סגורה בשבת", "גיל קונה רק במכולת", "גיל קנה חלב בשבת", "גיל קנה חלב ביום שישי",
                    "גיל שתה חלב בשבת"},
            {"כל השכנים הוזמנו לחתונה", "צביקה שכן", "צביקה לא הוזמן לחתונה", "צביקה לא אוהב חתונות",
                    "צביקה זרק את ההזמנה לפח"},
            {"התוכי של עדי יודע רק מילה אחת", "המילה היא שלום", "התוכי של עדי אמר תודה", "התוכי של עדי ירוק",
                    "התוכי של עדי אמר שלום פעמיים"},
    };

    private float diff, time, sessionT, pzT, pzLimit, afterT;
    private int k, cur, n, lie, picked, streak, solved, wrong;
    /** 0 thinking, 1 solved, 2 wrong (the lie is revealed), 3 time ran out, 4 bell rang. */
    private int state;
    private boolean subtle;
    private final String[] claim = new String[3];
    private float understand, shown;
    private int mood;
    private float moodT, flashT, shakeT;
    private final float[] cardY = new float[3];
    private float cardH = 56, cardsTop = 400;
    private int botFor = -1, botPick = -1;
    private float botAt;

    @Override
    int rounds() {
        return 1;
    }

    @Override
    String title() {
        return "לימודי משפטים";
    }

    @Override
    String howTo() {
        return "על הלוח עובדות. לוחצים על הכרטיס שלא יכול להיות נכון לפיהן. "
                + "מהר וברצף זה נכנס לראש, וניחוש שגוי שובר את הריכוז.";
    }

    @Override
    void begin(int round, float difficulty) {
        diff = Ui.clamp(difficulty, 0, 1);
        sessionT = 0;
        k = 0;
        streak = solved = wrong = 0;
        understand = shown = 0;
        mood = 0;
        moodT = flashT = shakeT = 0;
        botFor = -1;
        layout(Game.HUD_H + 74, h.screenH() - 96);
        next();
    }

    /** Draws a puzzle the player has not seen lately; the memory survives between sessions. */
    private void next() {
        cur = fresh("lawstudy.seen", BANK.length);
        String[] p = BANK[cur];
        n = diff < 0.15f && k < 2 ? 2 : 3;
        // Subtle decoys sound wrong but are not; they arrive sooner the harder the session.
        subtle = n == 3 || h.rnd().nextFloat() < diff * 0.9f + k * 0.08f;
        String decoy = n == 2 ? (subtle ? p[4] : p[3]) : null;
        lie = h.rnd().nextInt(n);
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i == lie) claim[i] = p[2];
            else if (n == 2) claim[i] = decoy;
            else claim[i] = j++ == 0 ? p[3] : p[4];
        }
        if (n == 3 && h.rnd().nextBoolean()) {
            // Shuffle the two true cards so the subtle one is not always lower.
            int a = lie == 0 ? 1 : 0, b = lie == 2 ? 1 : 2;
            String t = claim[a];
            claim[a] = claim[b];
            claim[b] = t;
        }
        pzT = 0;
        pzLimit = Math.max(4.2f, 8.5f - diff * 3.5f - k * 0.25f);
        state = 0;
        picked = -1;
        k++;
    }

    private int fresh(String key, int count) {
        long mask = (long) h.var(key + "0") | ((long) h.var(key + "1") << 13);
        if (count - Long.bitCount(mask) < 4) mask = 0;
        int free = count - Long.bitCount(mask), pick = h.rnd().nextInt(free), idx = 0;
        for (int i = 0; i < count; i++) {
            if ((mask & (1L << i)) != 0) continue;
            if (pick-- == 0) {
                idx = i;
                break;
            }
        }
        mask |= 1L << idx;
        h.setVar(key + "0", mask & 0x1FFF);
        h.setVar(key + "1", mask >> 13);
        return idx;
    }

    @Override
    int update(float dt) {
        time += dt;
        if (moodT > 0 && (moodT -= dt) <= 0) mood = 0;
        if (flashT > 0) flashT -= dt;
        if (shakeT > 0) shakeT -= dt;
        shown += (understand - shown) * Math.min(1, dt * 5);
        sessionT += dt;
        if (state == 4) {
            afterT += dt;
            if (afterT < 1.1f) return RUNNING;
            return understand >= PERFECT_AT ? PERFECT : understand >= OK_AT ? OK : MISS;
        }
        if (sessionT >= SESSION) {
            ring();
            return RUNNING;
        }
        if (state == 0) {
            pzT += dt;
            if (pzT >= pzLimit) {
                state = 3;
                afterT = 0;
                streak = 0;
                understand = Math.max(0, understand - 0.05f);
                picked = -1;
                mood = 2;
                moodT = 1;
                h.text(W / 2, cardsTop - 6, "הזמן עבר", Ui.ORANGE);
                h.sfx(Sfx.BEEP);
            }
        } else {
            afterT += dt;
            if (afterT >= (state == 1 ? 0.6f : 1.1f)) next();
        }
        return RUNNING;
    }

    private void ring() {
        state = 4;
        afterT = 0;
        boolean great = understand >= PERFECT_AT;
        mood = understand >= OK_AT ? 1 : 2;
        moodT = 2;
        h.text(W / 2, cardsTop + 20, great ? "נכנס לראש!" : understand >= OK_AT ? "סביר בהחלט" : "נכנס ויצא",
                great ? Ui.ACCENT : understand >= OK_AT ? Ui.GOOD : Ui.BAD);
        if (great) h.burst(W / 2, cardsTop + 30, Ui.ACCENT, 16);
        h.sfx(great ? Sfx.LEVEL : Sfx.DING);
    }

    @Override
    void down(float x, float y) {
        if (state != 0 || pzT < CARD_IN * 0.6f) return;
        for (int i = 0; i < n; i++) {
            if (x < 12 || x > W - 12 || y < cardY[i] - 3 || y > cardY[i] + cardH + 3) continue;
            answer(i);
            return;
        }
    }

    private void answer(int i) {
        picked = i;
        afterT = 0;
        float cy = cardY[i] + cardH / 2;
        if (i == lie) {
            state = 1;
            float speed = 1 - Ui.clamp(pzT / pzLimit, 0, 1);
            float gain = 0.12f + 0.08f * speed + 0.02f * Math.min(streak, 3);
            understand = Math.min(1, understand + gain);
            streak++;
            solved++;
            mood = 1;
            moodT = 0.9f;
            flashT = 0.35f;
            boolean sharp = speed > 0.55f;
            h.text(W / 2, cy - 14, streak >= 3 ? "ברצף " + streak + "!" : sharp ? "חד!" : "סתירה!",
                    streak >= 3 || sharp ? Ui.ACCENT : Ui.GOOD);
            h.burst(70, cy, sharp ? Ui.ACCENT : Ui.GOOD, sharp ? 12 : 7);
            h.sfx(sharp ? Sfx.DING : Sfx.GOOD);
        } else {
            state = 2;
            understand = Math.max(0, understand - 0.07f);
            streak = 0;
            wrong++;
            mood = 2;
            moodT = 1.2f;
            shakeT = 0.3f;
            h.text(W / 2, cy - 14, "זה דווקא מסתדר", Ui.BAD);
            h.shake(4);
            h.sfx(Sfx.BAD);
        }
    }

    private void layout(float top, float bottom) {
        float hgt = bottom - top;
        float boardBottom = top + 28 + boardH(hgt);
        float avail = bottom - 6 - (boardBottom + 14);
        cardH = Math.min(80, (avail - 16) / 3);
        float stack = 3 * cardH + 16;
        cardsTop = bottom - 6 - stack;
        int count = Math.max(2, n);
        // Two cards sit low, where the thumb is; three fill the whole stack.
        float y0 = count == 2 ? cardsTop + cardH + 8 : cardsTop;
        for (int i = 0; i < 3; i++) cardY[i] = y0 + i * (cardH + 8);
    }

    private static float boardH(float hgt) {
        return Ui.clamp(hgt * 0.30f, 66, 150);
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        float hgt = bottom - top;
        boolean small = hgt < 400;
        drawStatus(c, top);
        float bt = top + 28, bh = boardH(hgt);
        drawBoard(c, bt, bh, small);
        // Puzzle fuse under the board.
        float left = state == 0 ? 1 - Ui.clamp(pzT / pzLimit, 0, 1) : state == 1 ? 0 : 0;
        int fc = left > 0.5f ? Ui.GOOD : left > 0.25f ? Ui.ORANGE : Ui.BAD;
        c.fillRoundRect(64, bt + bh + 5, 284, 5, 2.5f, 0xFF0B0E12);
        if (state == 0) c.fillRoundRect(64 + 284 * (1 - left), bt + bh + 5, 284 * left, 5, 2.5f, fc);
        drawLecturer(c, 34, bt + bh + 8, Math.min(1.15f, bh / 92));
        if (cardsTop - (bt + bh + 12) >= 20 && state == 0) {
            c.text("מה לא יכול להיות נכון?", W / 2, cardsTop - 7, 13, Ui.DIM, Gfx.CENTER);
        }
        float size = small ? 15 : 17;
        for (int i = 0; i < n; i++) drawCard(c, i, size);
        if (state == 4) {
            c.fillRoundRect(40, cardsTop + 10, W - 80, 54, 12, 0xF01E2530);
            c.text("צלצול! השיעור נגמר", W / 2, cardsTop + 44, 20, Ui.INK, Gfx.CENTER);
        }
    }

    private void drawStatus(Gfx c, float top) {
        float y = top + 6;
        c.text("הבנה", W - 14, y + 11, 13, Ui.INK, Gfx.RIGHT);
        float bx = 150, bw = 150;
        int col = shown >= PERFECT_AT ? Ui.ACCENT : shown >= OK_AT ? Ui.GOOD : Ui.BLUE;
        Ui.bar(c, bx, y + 2, bw, 11, shown, col);
        // Marks for "it sank in" and "fully"; the bar fills toward the right edge.
        c.fillRect(bx + bw * OK_AT - 1, y - 1, 2, 17, 0xAAE9EDF2);
        c.fillRect(bx + bw * PERFECT_AT - 1, y - 1, 2, 17, Ui.ACCENT);
        // Lecture clock: a little face with a sweeping hand and the seconds left.
        float left = Math.max(0, SESSION - sessionT);
        float cx = 26, cy = y + 7;
        c.fillCircle(cx, cy, 10, left < 6 ? Ui.BAD : 0xFFE9EDF2);
        c.fillCircle(cx, cy, 8, 0xFF1E2530);
        double a = -Math.PI / 2 + sessionT / SESSION * Math.PI * 2;
        c.line(cx, cy, cx + (float) Math.cos(a) * 7, cy + (float) Math.sin(a) * 7, 2, Ui.ACCENT);
        c.text(String.valueOf((int) Math.ceil(left)), 42, y + 12, 14, left < 6 ? Ui.BAD : Ui.INK, Gfx.LEFT);
        if (streak >= 2) c.text("רצף " + streak, 108, y + 12, 13, Ui.ACCENT, Gfx.CENTER);
    }

    private void drawBoard(Gfx c, float y, float bh, boolean small) {
        float x = 64, w = W - 12 - x;
        c.fillRoundRect(x - 5, y - 5, w + 10, bh + 10, 6, 0xFF7A5A3A);
        c.fillRoundRect(x, y, w, bh, 3, 0xFF2E4A3B);
        // Old chalk ghosts, so the board does not look brand new.
        for (int i = 0; i < 4; i++) {
            float gy = y + 12 + i * bh / 4.2f, gx = x + 20 + ((i * 53) % 90);
            c.line(gx, gy, gx + 60 + (i * 31) % 70, gy + 3, 5, 0x0EFFFFFF);
        }
        c.fillRect(x + 6, y + bh - 3, w - 12, 3, 0xFF5E4630);
        c.fillRect(x + w - 50, y + bh - 6, 16, 4, 0xFFEFEFE6);
        if (state == 4 || cur < 0) return;
        String[] p = BANK[cur];
        boolean two = !p[1].isEmpty();
        float fs = small ? 15 : 19;
        float head = small ? 0 : 18;
        if (!small) c.text("עובדות:", x + w - 10, y + 18, 13, 0xAAEFF3EA, Gfx.RIGHT);
        float lines = two ? 2 : 1, gap = fs * 1.55f;
        float ty = y + head + (bh - head - lines * gap) / 2 + fs * 1.05f;
        int chalk = flashT > 0 ? 0xFFFFF4C2 : 0xFFEFF3EA;
        for (int i = 0; i < (two ? 2 : 1); i++) {
            float ly = ty + i * gap;
            c.fillCircle(x + w - 12, ly - fs * 0.32f, 2.6f, chalk);
            c.text(p[i], x + w - 20, ly, fs, chalk, Gfx.RIGHT);
        }
    }

    private void drawLecturer(Gfx c, float x, float feet, float s) {
        float bob = (float) Math.sin(time * 2.1f) * 1.2f * s;
        float top = feet - 78 * s + bob;
        // Jacket and legs.
        c.fillRect(x - 7 * s, feet - 22 * s, 5 * s, 22 * s, 0xFF2C3340);
        c.fillRect(x + 2 * s, feet - 22 * s, 5 * s, 22 * s, 0xFF2C3340);
        c.fillPoly(new float[]{x - 13 * s, top + 24 * s, x + 13 * s, top + 24 * s, x + 11 * s, feet - 20 * s,
                x - 11 * s, feet - 20 * s}, 0xFF5B4A6E);
        c.fillPoly(new float[]{x - 3 * s, top + 24 * s, x + 3 * s, top + 24 * s, x, top + 36 * s}, 0xFFE9EDF2);
        c.fillPoly(new float[]{x - 1.6f * s, top + 27 * s, x + 1.6f * s, top + 27 * s, x, top + 38 * s},
                0xFFB8433A);
        // Arm with the pointer: up and pleased, down when disappointed.
        float hx = x + 18 * s, hy;
        if (mood == 1) hy = top + 8 * s;
        else if (mood == 2) hy = top + 14 * s;
        else hy = top + 26 * s + (float) Math.sin(time * 3) * 2 * s;
        c.line(x + 10 * s, top + 27 * s, hx, hy, 4.5f * s, 0xFF5B4A6E);
        if (mood == 2) {
            // Hand on the forehead.
            c.fillCircle(x + 2 * s, top + 8 * s, 3.5f * s, 0xFFE8B98F);
        } else {
            c.line(hx, hy, hx + 24 * s, hy - 14 * s + (mood == 1 ? -6 * s : 0), 2, 0xFFC9A36B);
            c.fillCircle(hx, hy, 3 * s, 0xFFE8B98F);
        }
        // Head: bald on top, grey at the sides, round glasses.
        c.fillCircle(x, top + 12 * s, 11 * s, 0xFFE8B98F);
        c.fillCircle(x - 10 * s, top + 12 * s, 4 * s, 0xFFB9BEC6);
        c.fillCircle(x + 10 * s, top + 12 * s, 4 * s, 0xFFB9BEC6);
        c.strokeRoundRect(x - 8 * s, top + 8 * s, 7 * s, 6 * s, 3 * s, 1.4f, 0xFF20242B);
        c.strokeRoundRect(x + 1 * s, top + 8 * s, 7 * s, 6 * s, 3 * s, 1.4f, 0xFF20242B);
        if (mood == 1) {
            c.fillPoly(new float[]{x - 5 * s, top + 17 * s, x + 5 * s, top + 17 * s, x, top + 20.5f * s}, 0xFF7A2E2A);
        } else if (mood == 2) {
            c.line(x - 4 * s, top + 19 * s, x + 4 * s, top + 18 * s, 1.6f, 0xFF7A2E2A);
        } else {
            c.line(x - 3 * s, top + 18.5f * s, x + 3 * s, top + 18.5f * s, 1.4f, 0xFF7A2E2A);
        }
    }

    private void drawCard(Gfx c, int i, float size) {
        float e = state == 0 ? Ui.clamp((pzT - i * 0.07f) / CARD_IN, 0, 1) : 1;
        e = 1 - (1 - e) * (1 - e);
        float x = 14 + (1 - e) * (W + 20), y = cardY[i], w = W - 28;
        if (i == picked && shakeT > 0) x += (float) Math.sin(shakeT * 70) * 5;
        boolean reveal = state == 1 || state == 2 || state == 3;
        boolean isLie = i == lie;
        int paper = 0xFFF5F0E1;
        if (reveal && !isLie) paper = 0xFFB9B4A8;
        c.fillRoundRect(x + 2, y + 3, w, cardH, 8, 0x55000000);
        c.fillRoundRect(x, y, w, cardH, 8, paper);
        c.fillRect(x + 10, y + 9, w - 20, 1.5f, 0x55D9534F);
        int ink = 0xFF1B1E24;
        List<String> ls = Ui.wrap(c, claim[i], w - 40, size);
        float lh = size * 1.25f;
        float ty = y + cardH / 2 - (ls.size() - 1) * lh / 2 + size * 0.36f;
        for (int j = 0; j < ls.size(); j++) c.text(ls.get(j), x + w / 2, ty + j * lh, size, ink, Gfx.CENTER);
        if (reveal && isLie) {
            int col = state == 1 ? 0xFFD03A32 : 0xFFE07B2A;
            c.strokeRoundRect(x - 1, y - 1, w + 2, cardH + 2, 9, 3, col);
            // Rubber stamp over the left end of the card.
            float sx = x + 14, sy = y + cardH / 2 - 13;
            c.fillRoundRect(sx, sy, 70, 26, 5, 0x22D03A32);
            c.strokeRoundRect(sx, sy, 70, 26, 5, 2.4f, col);
            c.text("סתירה", sx + 35, sy + 19, 15, col, Gfx.CENTER);
        } else if (state == 2 && i == picked) {
            c.strokeRoundRect(x - 1, y - 1, w + 2, cardH + 2, 9, 2, 0xFF6B7280);
        }
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        // A stack of law books under a graduation cap.
        int[] cols = {0xFF7A2E2A, 0xFF2E4A6B, 0xFF4A6B2E};
        for (int i = 0; i < 3; i++) {
            float y = cy + 30 - i * 22, off = (i - 1) * 7;
            c.fillRoundRect(cx - 60 + off, y, 120, 20, 3, cols[i]);
            c.fillRect(cx - 54 + off, y + 3, 108, 2, 0x66FFC857);
            c.fillRect(cx + 48 + off, y + 2, 6, 16, 0xFFF0E6D0);
        }
        float bob = (float) Math.sin(t * 3) * 2;
        float top = cy - 44 + bob;
        c.fillPoly(new float[]{cx - 46, top, cx, top - 16, cx + 46, top, cx, top + 16}, 0xFF1B1E24);
        c.fillRect(cx - 22, top + 4, 44, 14, 0xFF1B1E24);
        c.line(cx, top, cx + 34, top + 10, 2, Ui.ACCENT);
        c.fillCircle(cx + 34, top + 16, 4, Ui.ACCENT);
    }

    @Override
    void bot(Random r, float skill) {
        if (state != 0) return;
        if (botFor != k) {
            botFor = k;
            // Reading takes time: the facts plus every card, slower for a weaker student.
            botAt = 1.3f + n * 0.45f + (1 - skill) * 2.2f + r.nextFloat() * 1.2f;
            float p = 1f / n + (1 - 1f / n) * skill * (subtle ? 0.88f : 0.97f);
            botPick = r.nextFloat() < p ? lie : (lie + 1 + r.nextInt(n - 1)) % n;
            // A lost student sometimes just stares until the time runs out.
            if (r.nextFloat() < (1 - skill) * 0.12f) botPick = -1;
        }
        if (botPick >= 0 && pzT >= botAt) {
            float x = W / 2, y = cardY[botPick] + cardH / 2;
            down(x, y);
            up(x, y);
            botPick = -1;
        }
    }
}
