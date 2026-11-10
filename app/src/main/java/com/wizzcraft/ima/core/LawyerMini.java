package com.wizzcraft.ima.core;

import java.util.List;
import java.util.Random;

/**
 * Lawyer shift: four petty neighbourhood cases. The opposing witness testifies line by line; some
 * lines are nonsense, irrelevant, or contradict the evidence card the player holds. Tap "התנגדות!"
 * on those, and stay quiet on the rest, because every wrong objection costs the judge's patience
 * (three and the case is thrown out). The risky play: tap the evidence card itself ("הוכחה!") on
 * the line it contradicts to win big, or watch it backfire on any other line. The case meter at the
 * top decides the round.
 */
final class LawyerMini extends Mini {
    private static final int INTRO = 0, LINE = 1, VERDICT = 2;
    private static final float INTRO_T = 1.35f, TYPE_T = 0.25f, AFTER_T = 0.55f, VERDICT_T = 0.85f;
    private static final float OK_AT = 0.45f, PERFECT_AT = 0.85f;

    /**
     * One case per row: witness gender (M/F), title, the player's evidence card, then the testimony in
     * order. Each line starts with its kind: o fine, w odd but fine (a trap for trigger-happy
     * lawyers), n nonsense, i irrelevant, c contradicts the evidence card.
     */
    private static final String[][] CASES = {
            {"F", "הקופסה שלא חזרה", "צילום: הקופסה במקרר של העדה",
                    "oאני מכירה את רחל עשרים שנה",
                    "oהיא הביאה לי קובה בקופסה כחולה",
                    "wלקופסה יש שם. קוראים לה צביקה.",
                    "cהחזרתי את הקופסה עוד באותו שבוע",
                    "nקופסאות בכלל לא קיימות. זו קונספירציה.",
                    "iאגב, המרק שלי זכה בתחרות",
                    "nהקופסה ברחה לבד. יש לה רגליים.",
                    "oמאז אנחנו לא מדברות במעלית"},
            {"M", "מלחמת החניה", "צילום: הקטנוע של העד בחניה",
                    "oאני גר בבניין כבר שש שנים",
                    "oהחניה הזאת תמיד הייתה פנויה",
                    "cאין לי קטנוע. אני הולך רק ברגל.",
                    "wסימנתי את החניה בגיר. בצורת דינוזאור.",
                    "nהחניה שלי. חלמתי עליה, זה נחשב.",
                    "iהבת שלי מתחתנת, כולם מוזמנים",
                    "nהקו הלבן זז בלילות. ראיתי.",
                    "oכהן צפר לי שלוש דקות ברצף"},
            {"F", "הכלב עם המבטא", "יומן: הכלב היה בפנסיון כל השבוע",
                    "oהכלב של גולן נובח כל לילה",
                    "wהוא נובח במבטא צרפתי. אני בטוחה.",
                    "cהשבוע הוא נבח עליי כל ערב",
                    "nהכלב ביקש ממני הלוואה",
                    "iיש לי גם חתול. הוא מושלם.",
                    "nהכלב חתם על החוזה עם הכפה",
                    "oביקשתי מגולן יפה שישקיט אותו",
                    "oבגללו אני לא ישנה כבר חודש"},
            {"F", "התאנה של אביבה", "רישיון: לעדה אין רכב בכלל",
                    "oהעץ של אביבה גדל לי למרפסת",
                    "oכל בוקר יש תאנים על הכביסה",
                    "cהתאנים נופלות לי על האוטו החדש",
                    "wקניתי מטרייה מיוחדת נגד תאנים",
                    "nהעץ בעצם שלי, מגלגול קודם",
                    "iאני דווקא מעדיפה אבטיח",
                    "nהתאנה הצביעה נגדי בוועד הבית",
                    "oביקשתי לגזום. היא הביאה לי ריבה."},
            {"F", "שירה במקלחת", "הודעת ועד: המים נותקו כל השבוע",
                    "oאני גרה בדיוק מתחת לשמעון",
                    "oהוא שר בשש בבוקר, בקולי קולות",
                    "cהשבוע הוא התקלח ושר כל בוקר",
                    "wהוא שר יפה. זה מה שמרגיז.",
                    "nהוא שר כל כך חזק שהירח זז",
                    "iפעם הגעתי לגמר בתחרות ריקודים",
                    "nשמעון זה בעצם שלושה ילדים במעיל",
                    "oדפקתי לו בתקרה עם מטאטא"},
            {"M", "הגמד בלובי", "תקנון: מותר לשים קישוט בלובי",
                    "oאני ראש ועד הבית שלוש שנים",
                    "oיום אחד הופיע גמד בלובי",
                    "wהגמד מסתכל עליי. גם כשאני זז.",
                    "cהתקנון אוסר כל קישוט בלובי",
                    "nהגמד גבה ממני דמי ועד",
                    "iיש לי מתכון מצוין לעוגת גבינה",
                    "nבלילות הגמד עושה מסיבות בלובי",
                    "oביקשתי להזיז אותו. סירבו לי."},
            {"M", "גניבת האינטרנט", "צילום: הרשת של העד בלי סיסמה",
                    "oהאינטרנט שלי נהיה איטי בערבים",
                    "oהשכן צופה בסדרות בלי הפסקה",
                    "cיש לי סיסמה חזקה, עם כוכבית",
                    "wקראתי לרשת שלי ״אל תעז״",
                    "nהכבלים ראו הכול, והם יעידו",
                    "iאני גם אלרגי לבוטנים, אם זה מעניין",
                    "nהשכן שואב לי את האינטרנט עם קש",
                    "oביקשתי שישלם חצי. הוא צחק."},
            {"M", "האופניים במדרגות", "מדידה: נשאר מעבר של מטר",
                    "oהאופניים של נעמה חוסמים את המדרגות",
                    "oכל בוקר אני נתקע שם עם הקניות",
                    "cאי אפשר לעבור שם. אפילו לא חתול.",
                    "wהתאמנתי בבית לקפוץ מעליהם",
                    "nהאופניים נוסעים לבד בשבת",
                    "iיש לי דוד שהיה רוכב מקצועי",
                    "nהאופניים מקללים אותי בצלצולים",
                    "oהשארתי לה פתק מנומס. שלושה פתקים."},
            {"M", "הדג שנעלם", "וטרינר: החתול צמחוני כבר שנה",
                    "oהשארתי דג על אדן החלון",
                    "oחזרתי אחרי דקה. הדג נעלם.",
                    "cראיתי את החתול לועס אותו",
                    "wהחתול חייך אליי אחר כך",
                    "nהחתול השאיר פתק: תודה, היה טעים",
                    "iאגב, אני גם לא סובל יונים",
                    "nהדג ברח לים. הוא השאיר מכתב.",
                    "oהחתול של בתיה תמיד מסתובב בחצר"},
            {"F", "הספה מהקומה השלישית", "כרטיס: השכן היה באילת כל החודש",
                    "oכל לילה גוררים משהו מעל הראש שלי",
                    "oזה נשמע כמו ספה. ספה כבדה.",
                    "cאתמול ראיתי אותו גורר את הספה",
                    "wספרתי. הוא מזיז אותה שבע פעמים.",
                    "nהספה שלו מתאמנת למרתון",
                    "iהבן שלי רופא, דרך אגב",
                    "nהרעש מגיע מהעתיד. בדקתי.",
                    "oשלחתי לו הודעה. קיבלתי לב."},
            {"M", "הגרב הבודדת", "תווית: על הגרב רקום שם העד",
                    "oהכביסה של כולם נופלת לפעמים",
                    "oאני תולה עם אטבים. תמיד.",
                    "cהגרב הזאת בכלל לא שלי",
                    "wיש לי בבית תשע גרביים בודדות",
                    "nהגרב טסה לשם מרצונה החופשי",
                    "iאני מעדיף נעליים בלי שרוכים",
                    "nהרוח עובדת אצל השכנה מלמטה",
                    "oהשכנה מלמטה תמיד מתלוננת עליי"},
            {"M", "המעלית התקועה", "מצלמה: העד במעלית כל בוקר",
                    "oהמעלית נתקעת בקומה שלי כל בוקר",
                    "oמישהו מחזיק אותה עם הרגל",
                    "cאני לא עולה במעלית. רק מדרגות.",
                    "wהמעלית חורקת בדו מז׳ור. בדקתי.",
                    "nהמעלית נתקעת כי חסר לה חיבוק",
                    "iיש לי ביטוח דירה מצוין, אגב",
                    "nבקומה שלוש גרה רוח רפאים נחמדה",
                    "oפעם חיכיתי עשרים דקות. עם אבטיח."},
            {"M", "העוגה מהלובי", "צילום: קצפת על הזקן של העד",
                    "oעברתי בלובי בדרך לפח",
                    "oראיתי עוגה. יפה, עם תותים.",
                    "cאני בכלל לא אוכל מתוקים",
                    "wעצרתי רק להריח. הרחתי הרבה.",
                    "nהעוגה אכלה את עצמה מרוב בושה",
                    "iהזקן שלי, אגב, זכה בפרס",
                    "nחייזרים מתים על תותים. זה ידוע.",
                    "oכשחזרתי, נשארה רק צלחת"},
            {"M", "התרנגול על הגג", "וטרינר: התרנגול אילם כבר חודש",
                    "oעל הגג של מוטי גר תרנגול",
                    "cהחודש הוא צעק כל בוקר בחמש",
                    "wקוראים לו אדון פרנקל. ככה מוטי אמר.",
                    "oקניתי אטמי אוזניים. שלושה זוגות.",
                    "nהתרנגול הוא שעון מעורר מחופש",
                    "iבצבא הייתי טבח, דרך אגב",
                    "nהתרנגול מדבר איתי בחלומות. בצרפתית.",
                    "oמוטי אומר שהוא חלק מהמשפחה"},
            {"F", "המזגן המטפטף", "חשבון חשמל: המזגן כבוי מאז מרץ",
                    "oהמזגן של לאה מעל הכניסה לבניין",
                    "cאתמול הוא טפטף לי ישר על הראש",
                    "oהגעתי לעבודה עם פוני רטוב",
                    "wעכשיו אני נכנסת לבניין עם כובע ים",
                    "nהטיפות כתבו לי על המצח ״לאה״",
                    "iיש לי שן בינה שמציקה",
                    "nהמזגן שלה בוכה בגלל פרידה",
                    "oביקשתי שתשים צינור. היא שמה עציץ."},
    };
    private static final int[] SHIRTS = {0xFF5AA9FF, 0xFFE07B2A, 0xFF8E6BD8, 0xFF3FA37A, 0xFFD8546E, 0xFFC9A93A};

    private int phase, caseIdx, nLines, li, strikes, badCount, verdictRes, round;
    private final String[] line = new String[6];
    private final char[] kind = new char[6];
    private boolean witnessF, acted;
    private float diff, time, phaseT, actT, lineDur, meter, shownMeter, unit;
    // Feedback state.
    private String say = "", stamp = "", verdictText = "";
    private int sayCol, stampCol, mood;
    private float sayT, stampT, gavelT, objPress, proofPress, shock, evGlow, evBad, moodT;
    // Layout, refreshed by draw(); the defaults let touches and the bot work before the first frame.
    private float sceneY, sceneH, bubY, bubH, btnY, btnH;
    private static final float EV_X = 14, EV_W = 158, OBJ_X = 184, OBJ_W = 162;
    private int botLine = -1, botAct;
    private float botAt;

    @Override
    int rounds() {
        return 4;
    }

    @Override
    String title() {
        return h.t("עורך דין", "עורכת דין");
    }

    @Override
    String howTo() {
        return "העד מעיד שורה אחרי שורה. שטות, סתירה או משהו לא רלוונטי? ״התנגדות!״. "
                + "סותר את הראיה שלך? לוחצים עליה: ״הוכחה!״. טעות מעצבנת את השופט.";
    }

    @Override
    void begin(int round, float difficulty) {
        this.round = round;
        diff = Ui.clamp(difficulty, 0, 1);
        caseIdx = fresh("lawyer.seen", CASES.length);
        String[] cs = CASES[caseIdx];
        witnessF = cs[0].equals("F");
        pickLines(cs, round);
        lineDur = 2.2f - 0.55f * diff;
        meter = shownMeter = 0.5f;
        strikes = 0;
        phase = INTRO;
        phaseT = 0;
        li = -1;
        acted = false;
        say = stamp = verdictText = "";
        sayT = stampT = gavelT = objPress = proofPress = shock = evGlow = evBad = moodT = 0;
        mood = 0;
        botLine = -1;
        layout(Game.HUD_H + 74, h.screenH() - 96);
    }

    /**
     * Picks the testimony for this round from the case, keeping its order: always the line that
     * contradicts the evidence, more bad lines later in the shift, and from the second case on the odd
     * but honest line that tempts a reflexive objection.
     */
    private void pickLines(String[] cs, int round) {
        Random r = h.rnd();
        int total = cs.length - 3;
        boolean[] use = new boolean[total];
        int want = round >= 2 ? 5 : 4, bad = round >= 2 ? 2 : 1, chosen = 0;
        int[] badIdx = new int[total], okIdx = new int[total];
        int nb = 0, no = 0;
        for (int i = 0; i < total; i++) {
            char t = cs[3 + i].charAt(0);
            if (t == 'c') {
                use[i] = true;
                chosen++;
            } else if (t == 'w') {
                if (round >= 1 || diff > 0.3f) {
                    use[i] = true;
                    chosen++;
                }
            } else if (t == 'o') {
                okIdx[no++] = i;
            } else {
                badIdx[nb++] = i;
            }
        }
        chosen += take(use, badIdx, nb, bad, r);
        take(use, okIdx, no, want - chosen, r);
        nLines = 0;
        badCount = 0;
        for (int i = 0; i < total && nLines < line.length; i++) {
            if (!use[i]) continue;
            kind[nLines] = cs[3 + i].charAt(0);
            line[nLines] = cs[3 + i].substring(1);
            if (isBad(kind[nLines])) badCount++;
            nLines++;
        }
        unit = 0.4f / Math.max(1, badCount);
    }

    private static int take(boolean[] use, int[] idx, int n, int want, Random r) {
        int got = 0;
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        for (int i = 0; i < n && got < want; i++) {
            use[idx[i]] = true;
            got++;
        }
        return got;
    }

    private static boolean isBad(char k) {
        return k == 'n' || k == 'i' || k == 'c';
    }

    /** A case the player has not seen lately; the memory survives between shifts. */
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
        phaseT += dt;
        if (sayT > 0) sayT -= dt;
        if (stampT > 0) stampT -= dt;
        if (gavelT > 0) gavelT -= dt;
        if (objPress > 0) objPress -= dt;
        if (proofPress > 0) proofPress -= dt;
        if (shock > 0) shock -= dt;
        if (evBad > 0) evBad -= dt;
        if (moodT > 0 && (moodT -= dt) <= 0) mood = strikes >= 2 ? 2 : 0;
        shownMeter += (meter - shownMeter) * Math.min(1, dt * 7);
        switch (phase) {
            case INTRO:
                if (phaseT >= INTRO_T) startLine(0);
                break;
            case LINE:
                if (acted) {
                    actT += dt;
                    if (actT >= AFTER_T) advance();
                } else if (phaseT >= lineDur) {
                    letPass();
                    advance();
                }
                break;
            default:
                if (phaseT >= VERDICT_T) return verdictRes;
        }
        return RUNNING;
    }

    private void startLine(int i) {
        phase = LINE;
        li = i;
        phaseT = 0;
        acted = false;
        actT = 0;
        stampT = 0;
    }

    private void advance() {
        if (phase != LINE) return;
        if (li + 1 < nLines) {
            startLine(li + 1);
        } else {
            verdict(meter >= PERFECT_AT ? PERFECT : meter >= OK_AT ? OK : MISS);
        }
    }

    /** The line ran out with no objection: harmless if it was fine, costly if it was not. */
    private void letPass() {
        if (!isBad(kind[li])) return;
        meter -= unit * 0.5f;
        mood = 2;
        moodT = 0.8f;
        float y = bubY + bubH - 8;
        if (kind[li] == 'c') {
            evBad = 0.9f;
            h.text(EV_X + EV_W / 2, btnY - 4, "סותר את הראיה!", Ui.BAD);
        } else {
            h.text(W / 2, y, "עבר בלי התנגדות", Ui.BAD);
        }
        h.sfx(Sfx.BEEP);
    }

    private void verdict(int res) {
        phase = VERDICT;
        phaseT = 0;
        verdictRes = res;
        gavelT = 0.5f;
        mood = res == MISS ? 3 : 1;
        moodT = 9;
        verdictText = res == PERFECT ? "זכייה מוחצת!" : res == OK ? "זכינו בתיק" : "התיק אבוד";
        if (res == PERFECT) h.burst(W / 2, bubY + bubH / 2, Ui.ACCENT, 18);
        h.sfx(Sfx.PUNCH);
    }

    @Override
    void down(float x, float y) {
        if (phase != LINE || acted || y < btnY - 6 || y > btnY + btnH + 6) return;
        if (x >= OBJ_X - 4 && x <= OBJ_X + OBJ_W + 6) object(false, x, y);
        else if (x >= EV_X - 6 && x <= EV_X + EV_W + 4) object(true, x, y);
    }

    private void object(boolean proof, float x, float y) {
        acted = true;
        actT = 0;
        char k = kind[li];
        boolean bad = isBad(k);
        float speed = 1 - Ui.clamp((phaseT - TYPE_T) / (lineDur * 0.7f), 0, 1);
        gavelT = 0.45f;
        stampT = 1;
        if (proof) {
            proofPress = 0.25f;
            if (k == 'c') {
                meter += unit + 0.25f;
                mood = 1;
                moodT = 1.2f;
                shock = 1.2f;
                speak("הוכחה ניצחת!", Ui.ACCENT);
                stamp("הוכחה!", Ui.ACCENT);
                h.burst(W / 2, bubY + bubH / 2, Ui.ACCENT, 20);
                h.burst(EV_X + EV_W / 2, btnY + btnH / 2, Ui.ACCENT, 10);
                h.shake(5);
                h.sfx(Sfx.PERFECT);
            } else {
                meter -= 0.25f;
                strikes++;
                mood = 3;
                moodT = 1.4f;
                evBad = 0.8f;
                speak(h.rnd().nextBoolean() ? "זאת הראיה שלך?!" : "מה הקשר בכלל?!", Ui.BAD);
                stamp("נדחתה!", Ui.BAD);
                h.shake(9);
                h.sfx(Sfx.BAD);
            }
        } else {
            objPress = 0.25f;
            if (bad) {
                meter += unit * (0.75f + 0.25f * speed);
                mood = 1;
                moodT = 0.9f;
                shock = 0.6f;
                speak(k == 'n' ? "התקבלה. זה לא הגיוני." : k == 'i' ? "התקבלה. לא רלוונטי."
                        : "התקבלה. סותר את הראיה.", Ui.GOOD);
                stamp("התקבלה!", Ui.GOOD);
                if (speed > 0.6f) h.text(OBJ_X + OBJ_W / 2, btnY - 4, "חדה!", Ui.ACCENT);
                h.burst(OBJ_X + OBJ_W / 2, btnY + btnH / 2, Ui.GOOD, speed > 0.6f ? 12 : 7);
                h.sfx(speed > 0.6f ? Sfx.DING : Sfx.GOOD);
            } else {
                meter -= 0.09f;
                strikes++;
                mood = strikes >= 2 ? 3 : 2;
                moodT = 1.2f;
                stamp("נדחתה!", Ui.BAD);
                if (strikes == 2) speak(h.t("עוד אחת ואתה בחוץ!", "עוד אחת ואת בחוץ!"), Ui.BAD);
                else if (k == 'w') speak("נדחתה. מוזר, אבל חוקי.", Ui.BAD);
                else speak(overruled(), Ui.BAD);
                h.shake(5);
                h.sfx(Sfx.BAD);
            }
        }
        meter = Ui.clamp(meter, 0, 1);
        if (strikes >= 3) {
            speak("מספיק! התיק נדחה.", Ui.BAD);
            verdict(MISS);
        } else if (meter >= 1) {
            verdict(PERFECT);
        }
    }

    private String overruled() {
        switch (h.rnd().nextInt(4)) {
            case 0:
                return "נדחתה. מה הבעיה?";
            case 1:
                return h.t("נדחתה. שב בשקט.", "נדחתה. שבי בשקט.");
            case 2:
                return "נדחתה. זה דווקא הגיוני.";
            default:
                return witnessF ? h.t("נדחתה. תן לה לסיים.", "נדחתה. תני לה לסיים.")
                        : h.t("נדחתה. תן לו לסיים.", "נדחתה. תני לו לסיים.");
        }
    }

    private void speak(String s, int col) {
        say = s;
        sayCol = col;
        sayT = 1.3f;
    }

    private void stamp(String s, int col) {
        stamp = s;
        stampCol = col;
    }

    // ------------------------------------------------------------------ layout & drawing

    private void layout(float top, float bottom) {
        float hgt = bottom - top;
        sceneY = top + 26;
        sceneH = hgt < 400 ? 96 : Ui.clamp(hgt * 0.4f, 96, 200);
        bubY = sceneY + sceneH + 4;
        bubH = hgt < 400 ? 66 : 92;
        float rest = bottom - (bubY + bubH + 12);
        btnH = Math.min(140, rest - 4);
        btnY = bottom - 4 - btnH;
    }

    @Override
    void draw(Gfx c, float top, float bottom) {
        layout(top, bottom);
        drawMeter(c, top + 5);
        drawScene(c);
        drawBubble(c);
        drawEvidence(c);
        drawObjection(c);
        drawJudgeSpeech(c);
    }

    private void drawMeter(Gfx c, float y) {
        float bx = 66, bw = 228;
        c.text("הם", bx - 10, y + 12, 13, Ui.BAD, Gfx.RIGHT);
        c.text("אנחנו", bx + bw + 10, y + 12, 13, Ui.GOOD, Gfx.LEFT);
        c.fillRoundRect(bx, y + 2, bw, 12, 6, 0xFF0B0E12);
        float m = Ui.clamp(shownMeter, 0, 1), mx = bx + bw * m;
        c.fillRoundRect(bx, y + 2, Math.max(12, mx - bx), 12, 6, 0xFF8E2F2F);
        if (bx + bw - mx > 1) c.fillRoundRect(mx, y + 2, Math.max(12, bx + bw - mx), 12, 6, 0xFF2F7A4E);
        // Where the case is won, and where it is won outright.
        c.fillRect(bx + bw * OK_AT - 1, y - 1, 2, 18, 0xCCE9EDF2);
        c.fillRect(bx + bw * PERFECT_AT - 1, y - 1, 2, 18, Ui.ACCENT);
        c.fillCircle(mx, y + 8, 9, 0xFFE9EDF2);
        c.fillCircle(mx, y + 8, 5, m >= PERFECT_AT ? Ui.ACCENT : m >= OK_AT ? Ui.GOOD : Ui.BAD);
    }

    private void drawScene(Gfx c) {
        float sy = sceneY, sh = sceneH, s = sh / 180f;
        // Wood-panelled wall with a lighter upper half.
        c.fillRoundRect(6, sy, W - 12, sh, 10, 0xFF2B2420);
        c.fillRoundRect(6, sy, W - 12, sh * 0.5f, 10, 0xFF3A322C);
        for (int i = 1; i < 9; i++) c.line(6 + i * 39, sy + sh * 0.5f, 6 + i * 39, sy + sh, 1, 0x30000000);
        c.fillRect(6, sy + sh * 0.5f - 2, W - 12, 3, 0xFF4E4036);
        // Wall clock, ticking.
        float cx = 40, cy = sy + 26 * s + 6;
        c.fillCircle(cx, cy, 15 * s + 2, 0xFF6B5A48);
        c.fillCircle(cx, cy, 15 * s, 0xFFEDE6D6);
        double a1 = time * 0.9, a2 = -Math.PI / 2 + Math.floor(time) * Math.PI / 30;
        c.line(cx, cy, cx + (float) Math.cos(a1) * 8 * s, cy + (float) Math.sin(a1) * 8 * s, 2, 0xFF2B2420);
        c.line(cx, cy, cx + (float) Math.cos(a2) * 12 * s, cy + (float) Math.sin(a2) * 12 * s, 1.2f, 0xFFB8433A);

        float jx = 206, benchTop = sy + sh * 0.56f;
        drawJudge(c, jx, benchTop, s);
        // Judge's bench with the scales of the case on its front.
        c.fillRect(118, benchTop, 182, sy + sh - benchTop, 0xFF6B4423);
        c.fillRect(112, benchTop - 6, 194, 8, 0xFF8A5A30);
        c.strokeRoundRect(132, benchTop + 8, 154, sy + sh - benchTop - 14, 4, 1.5f, 0x40000000);
        drawScales(c, jx, benchTop + (sy + sh - benchTop) * 0.55f, s);
        drawGavel(c, jx - 50 * s - 14, benchTop - 6, s);
        // The judge's patience: three lamps on the bench, one goes dark per wrong objection.
        for (int i = 0; i < 3; i++) {
            float px = 270 + i * 11, py = benchTop - 12;
            boolean lit = i >= strikes;
            c.fillCircle(px, py, 4, lit ? 0xFFFFD27A : 0xFF3A3A3A);
            if (lit && strikes == 2) c.fillCircle(px, py, 6, 0x33FF6B6B);
        }
        drawWitness(c, s);
    }

    private void drawJudge(Gfx c, float x, float benchTop, float s) {
        float breathe = (float) Math.sin(time * 1.6f) * 1.2f * s;
        float head = benchTop - 44 * s + breathe;
        // Robe and collar.
        c.fillPoly(new float[]{x - 34 * s, benchTop, x - 26 * s, head + 16 * s, x + 26 * s, head + 16 * s,
                x + 34 * s, benchTop}, 0xFF15161A);
        c.fillPoly(new float[]{x - 8 * s, head + 16 * s, x + 8 * s, head + 16 * s, x, head + 30 * s}, 0xFFF2F2F2);
        boolean furious = mood == 3;
        int skin = furious ? 0xFFE58A72 : 0xFFE8B98F;
        c.fillCircle(x, head, 15 * s, skin);
        // Grey hair at the sides, a bun of curls at the back.
        c.fillCircle(x - 14 * s, head - 3 * s, 6 * s, 0xFFC9CDD3);
        c.fillCircle(x + 14 * s, head - 3 * s, 6 * s, 0xFFC9CDD3);
        c.fillPoly(new float[]{x - 14 * s, head - 8 * s, x - 8 * s, head - 15 * s, x + 8 * s, head - 15 * s,
                x + 14 * s, head - 8 * s, x, head - 11 * s}, 0xFFC9CDD3);
        // Glasses and eyes (a slow blink every few seconds).
        boolean blink = (time % 3.7f) < 0.12f;
        float ey = head + 1 * s;
        c.strokeRoundRect(x - 11 * s, ey - 4 * s, 9 * s, 8 * s, 3 * s, 1.3f, 0xFF20242B);
        c.strokeRoundRect(x + 2 * s, ey - 4 * s, 9 * s, 8 * s, 3 * s, 1.3f, 0xFF20242B);
        if (blink) {
            c.line(x - 9 * s, ey, x - 4 * s, ey, 1.4f, 0xFF20242B);
            c.line(x + 4 * s, ey, x + 9 * s, ey, 1.4f, 0xFF20242B);
        } else {
            c.fillCircle(x - 6.5f * s, ey, 1.8f * s, 0xFF20242B);
            c.fillCircle(x + 6.5f * s, ey, 1.8f * s, 0xFF20242B);
        }
        // Brows and mouth carry the mood.
        float bt = ey - 7 * s, tilt = mood >= 2 ? 3 * s : mood == 1 ? -1.5f * s : 0;
        c.line(x - 11 * s, bt - tilt, x - 3 * s, bt + tilt, 1.8f, 0xFF8A8F98);
        c.line(x + 3 * s, bt + tilt, x + 11 * s, bt - tilt, 1.8f, 0xFF8A8F98);
        float my = head + 9 * s;
        if (mood == 1) {
            c.fillPoly(new float[]{x - 5 * s, my - 1 * s, x + 5 * s, my - 1 * s, x, my + 3.5f * s}, 0xFF7A2E2A);
        } else if (mood == 3) {
            c.fillRoundRect(x - 4 * s, my - 1 * s, 8 * s, 4 * s, 2 * s, 0xFF7A2E2A);
            // Steam.
            for (int i = 0; i < 2; i++) {
                float sx = x + (i == 0 ? -20 : 20) * s, ph = (time * 3 + i) % 1;
                c.fillCircle(sx, head - 14 * s - ph * 12 * s, (3 + ph * 3) * s, 0x55FFFFFF);
            }
        } else if (mood == 2) {
            c.line(x - 4 * s, my + 1 * s, x + 4 * s, my, 1.6f, 0xFF7A2E2A);
        } else {
            c.line(x - 3 * s, my, x + 3 * s, my, 1.4f, 0xFF7A2E2A);
        }
    }

    private void drawGavel(Gfx c, float px, float py, float s) {
        // Sound block, then the gavel swinging around its pivot.
        c.fillRoundRect(px - 16 * s, py - 6 * s, 30 * s, 6 * s, 2, 0xFF4A2E18);
        float swing;
        if (gavelT > 0) {
            float t = 1 - gavelT / 0.45f;
            swing = t < 0.45f ? t / 0.45f : 1 - (t - 0.45f) / 0.55f;
            swing = 1 - swing;
        } else {
            swing = 1;
        }
        // swing 0 = struck flat on the block, 1 = resting raised a little.
        double ang = 0.08 + swing * 0.95;
        float dx = (float) -Math.cos(ang), dy = (float) -Math.sin(ang), nx = -dy, ny = dx;
        float hx = px + 26 * s, hy = py - 10 * s;
        float ex = hx + dx * 28 * s, ey = hy + dy * 28 * s;
        c.line(hx, hy, ex, ey, 3.2f * s, 0xFF8A5A30);
        float hw = 6 * s, hl = 10 * s;
        c.fillPoly(new float[]{ex - nx * hl - dx * hw, ey - ny * hl - dy * hw, ex + nx * hl - dx * hw,
                ey + ny * hl - dy * hw, ex + nx * hl + dx * hw, ey + ny * hl + dy * hw, ex - nx * hl + dx * hw,
                ey - ny * hl + dy * hw}, 0xFF5A3418);
        if (gavelT > 0 && gavelT < 0.25f) {
            for (int i = 0; i < 3; i++) {
                float a = (float) (-Math.PI / 2 + (i - 1) * 0.7);
                c.line(px + (float) Math.cos(a) * 14 * s, py - 8 * s + (float) Math.sin(a) * 6 * s,
                        px + (float) Math.cos(a) * 24 * s, py - 8 * s + (float) Math.sin(a) * 14 * s, 2, Ui.ACCENT);
            }
        }
    }

    private void drawScales(Gfx c, float x, float y, float s) {
        int gold = 0xFFD9B45A;
        float tilt = (Ui.clamp(shownMeter, 0, 1) - 0.5f) * 0.7f;
        c.line(x, y - 14 * s, x, y + 12 * s, 2, gold);
        c.fillRect(x - 8 * s, y + 12 * s, 16 * s, 3 * s, gold);
        // Our side is on the right: winning tips the beam down toward us.
        float bx = (float) Math.cos(tilt) * 22 * s, by = (float) Math.sin(tilt) * 22 * s;
        c.line(x - bx, y - 14 * s - by, x + bx, y - 14 * s + by, 2, gold);
        for (int side = -1; side <= 1; side += 2) {
            float ex = x + side * bx, ey = y - 14 * s + side * by;
            c.line(ex, ey, ex - 6 * s, ey + 10 * s, 1, gold);
            c.line(ex, ey, ex + 6 * s, ey + 10 * s, 1, gold);
            c.fillPoly(new float[]{ex - 8 * s, ey + 10 * s, ex + 8 * s, ey + 10 * s, ex + 5 * s, ey + 14 * s,
                    ex - 5 * s, ey + 14 * s}, gold);
        }
    }

    private void drawWitness(Gfx c, float s) {
        float standTop = sceneY + sceneH * 0.64f, x = 72 + (float) Math.sin(time * 1.7f) * 1.5f;
        if (shock > 0) x += (float) Math.sin(time * 60) * 2 * Math.min(1, shock);
        int shirt = SHIRTS[caseIdx % SHIRTS.length];
        float head = standTop - 38 * s;
        // Hair behind the head for longer styles.
        if (witnessF) {
            c.fillPoly(new float[]{x - 15 * s, head - 4 * s, x + 15 * s, head - 4 * s, x + 17 * s, head + 22 * s,
                    x - 17 * s, head + 22 * s}, caseIdx % 2 == 0 ? 0xFF3B2A20 : 0xFF8A5A2B);
        }
        c.fillPoly(new float[]{x - 24 * s, standTop + 4, x - 18 * s, head + 16 * s, x + 18 * s, head + 16 * s,
                x + 24 * s, standTop + 4}, shirt);
        c.fillCircle(x, head, 13 * s, 0xFFE8B98F);
        int hair = witnessF ? (caseIdx % 2 == 0 ? 0xFF3B2A20 : 0xFF8A5A2B) : (caseIdx % 3 == 0 ? 0xFFB9BEC6 : 0xFF2A2420);
        c.fillPoly(new float[]{x - 13 * s, head - 2 * s, x - 9 * s, head - 12 * s, x + 9 * s, head - 12 * s,
                x + 13 * s, head - 2 * s, x + 6 * s, head - 6 * s, x - 6 * s, head - 6 * s}, hair);
        if (!witnessF && caseIdx == 12) {
            // The cake case: a beard, with cream on it.
            c.fillPoly(new float[]{x - 11 * s, head + 3 * s, x + 11 * s, head + 3 * s, x + 6 * s, head + 15 * s,
                    x - 6 * s, head + 15 * s}, 0xFF4A3A2E);
            c.fillCircle(x + 3 * s, head + 10 * s, 3 * s, 0xFFFFF8EE);
        }
        boolean shocked = shock > 0;
        float er = shocked ? 2.6f * s : 1.6f * s;
        c.fillCircle(x - 5 * s, head, er, 0xFF20242B);
        c.fillCircle(x + 5 * s, head, er, 0xFF20242B);
        boolean talking = phase == LINE && !acted && phaseT < lineDur * 0.8f && ((int) (time * 9)) % 2 == 0;
        if (shocked) c.fillCircle(x, head + 7 * s, 3 * s, 0xFF7A2E2A);
        else if (talking) c.fillRoundRect(x - 3.5f * s, head + 5 * s, 7 * s, 4 * s, 2, 0xFF7A2E2A);
        else c.line(x - 3 * s, head + 7 * s, x + 3 * s, head + 7 * s, 1.4f, 0xFF7A2E2A);
        if (shocked) c.fillPoly(new float[]{x + 14 * s, head - 8 * s, x + 17 * s, head - 2 * s, x + 14 * s,
                head + 1 * s, x + 11 * s, head - 2 * s}, 0xFF9FD3FF);
        // The witness stand and its microphone.
        c.fillRect(22, standTop, 104, sceneY + sceneH - standTop, 0xFF5E3B1F);
        c.fillRect(18, standTop - 5, 112, 7, 0xFF8A5A30);
        c.line(104, standTop - 4, 96, standTop - 22 * s, 1.6f, 0xFF9AA3AE);
        c.fillCircle(95, standTop - 23 * s, 3.4f * s, 0xFF2B2F36);
    }

    private void drawBubble(Gfx c) {
        float x = 12, w = W - 24, y = bubY, hh = bubH;
        int paper = 0xFFF4F1EA, ink = 0xFF1B1E24;
        c.fillPoly(new float[]{58, y + 2, 84, y + 2, 66, y - 12}, paper);
        c.fillRoundRect(x, y, w, hh, 12, paper);
        boolean small = hh < 80;
        if (phase == INTRO) {
            float a = Math.min(1, phaseT / 0.3f);
            c.text("תיק: " + CASES[caseIdx][1], W / 2, y + hh * 0.42f, small ? 19 : 22, ink, Gfx.CENTER);
            c.text(witnessF ? "העדה נשבעת לומר רק אמת" : "העד נשבע לומר רק אמת", W / 2, y + hh * 0.78f,
                    small ? 13 : 14, a < 1 ? 0xFF8A94A3 : 0xFF5A6270, Gfx.CENTER);
        } else if (phase == VERDICT) {
            int col = verdictRes == PERFECT ? 0xFFB07A00 : verdictRes == OK ? 0xFF1E7A45 : 0xFFB8332E;
            c.text(verdictText, W / 2, y + hh / 2 + 10, small ? 24 : 28, col, Gfx.CENTER);
        } else if (li >= 0) {
            String s = line[li];
            int shownChars = Math.min(s.length(), (int) (s.length() * Math.min(1, phaseT / TYPE_T)) + 1);
            String vis = s.substring(0, shownChars);
            float size = small ? 17 : 19;
            // Wrap the full line so the words do not jump while it types out.
            List<String> full = Ui.wrap(c, s, w - 28, size);
            float lh = size * 1.3f, ty = y + hh / 2 - (full.size() - 1) * lh / 2 + size * 0.36f - 3;
            int used = 0;
            for (int j = 0; j < full.size(); j++) {
                String part = full.get(j);
                int start = used, end = Math.min(vis.length(), start + part.length());
                if (end > start) c.text(vis.substring(start, end), W / 2, ty + j * lh, size, ink, Gfx.CENTER);
                used += part.length() + 1;
            }
            // How long the line stays up.
            if (!acted) {
                float left = 1 - Ui.clamp(phaseT / lineDur, 0, 1);
                c.fillRoundRect(x + 14, y + hh - 7, (w - 28) * left, 3, 1.5f, left < 0.3f ? 0xFFD9534F : 0xFF9AA3AE);
            }
            if (acted && stampT > 0 && !stamp.isEmpty()) {
                // Wash the line out and press the ruling over it like a rubber stamp.
                c.fillRoundRect(x, y, w, hh, 12, 0xC8F4F1EA);
                float pop = 1 + Math.max(0, stampT - 0.82f) * 2.5f;
                float sw = 150 * pop, shh = 44 * pop, sx = W / 2 - sw / 2, sy = y + hh / 2 - shh / 2;
                int dark = Ui.darken(stampCol, 0.62f);
                c.fillRoundRect(sx, sy, sw, shh, 8, (stampCol & 0x00FFFFFF) | 0x40000000);
                c.strokeRoundRect(sx, sy, sw, shh, 8, 3, dark);
                c.text(stamp, W / 2, sy + shh / 2 + 9 * pop, 25 * pop, dark, Gfx.CENTER);
            }
        }
        // Progress through the testimony.
        float dy = y + hh + 7;
        for (int i = 0; i < nLines; i++) {
            float dx = W / 2 + (nLines - 1) * 7 - i * 14;
            boolean done = i < li || (i == li && acted) || phase == VERDICT;
            c.fillCircle(dx, dy, i == li && phase == LINE ? 4 : 3, done ? 0xFF8A94A3 : i == li ? Ui.INK : 0xFF3A4350);
        }
    }

    private void drawEvidence(Gfx c) {
        float x = EV_X, y = btnY, w = EV_W, hh = btnH;
        if (proofPress > 0) y += 3;
        boolean live = phase == LINE && !acted;
        float glow = phase == INTRO ? 0.5f + 0.5f * (float) Math.sin(time * 8) : 0;
        if (glow > 0) c.fillRoundRect(x - 4, y - 4, w + 8, hh + 8, 12, ((int) (glow * 90) << 24) | 0xFFC857);
        if (evBad > 0) c.fillRoundRect(x - 4, y - 4, w + 8, hh + 8, 12, 0x88FF6B6B);
        c.fillRoundRect(x + 2, y + 4, w, hh, 10, 0x66000000);
        c.fillRoundRect(x, y, w, hh, 10, 0xFFEDE3C8);
        // Paper clip.
        c.strokeRoundRect(x + 12, y - 6, 12, 22, 6, 2, 0xFF9AA3AE);
        boolean small = hh < 100;
        float strip = small ? 24 : 34, head = small ? 4 : 25;
        if (!small) c.text("הראיה שלך", x + w - 10, y + 19, 13, 0xFF7A6A4A, Gfx.RIGHT);
        float fs = small ? 13 : 15;
        List<String> ls = Ui.wrap(c, CASES[caseIdx][2], w - 18, fs);
        float top = y + head, area = hh - strip - 4 - head;
        float lh = fs * 1.22f, ty = top + area / 2 - (ls.size() - 1) * lh / 2 + fs * 0.36f;
        for (int j = 0; j < ls.size(); j++) c.text(ls.get(j), x + w / 2, ty + j * lh, fs, 0xFF2A2418, Gfx.CENTER);
        int sc = live ? 0xFFFFC857 : 0xFFB8A982;
        c.fillRoundRect(x + 6, y + hh - strip - 5, w - 12, strip, 8, sc);
        c.text("הוכחה!", x + w / 2, y + hh - 5 - strip / 2 + (small ? 6 : 7), small ? 16 : 19, 0xFF2A2418,
                Gfx.CENTER);
    }

    private void drawObjection(Gfx c) {
        float x = OBJ_X, y = btnY, w = OBJ_W, hh = btnH;
        boolean live = phase == LINE && !acted;
        float press = objPress > 0 ? 5 : 0;
        int face = live ? 0xFFD8443C : 0xFF7A3A36, edge = live ? 0xFF8E2620 : 0xFF4A2522;
        if (live) {
            float p = 0.5f + 0.5f * (float) Math.sin(time * 5);
            c.fillRoundRect(x - 3, y - 3, w + 6, hh + 6, 18, ((int) (p * 50) << 24) | 0xFF6B6B);
        }
        c.fillRoundRect(x, y + 6, w, hh - 6, 16, edge);
        c.fillRoundRect(x, y + press, w, hh - 6, 16, face);
        c.fillRoundRect(x + 8, y + press + 5, w - 16, 10, 5, 0x22FFFFFF);
        boolean tall = hh > 100;
        float ty = y + press + (hh - 6) / 2 + (tall ? 20 : 9);
        c.text("התנגדות!", x + w / 2, ty, tall ? 26 : 23, 0xFFFFFFFF, Gfx.CENTER);
        if (tall) {
            // A raised index finger above the word.
            float fx = x + w / 2, fy = y + press + (hh - 6) / 2 - 18;
            c.fillRoundRect(fx - 11, fy - 2, 22, 18, 6, 0xFFF2C9A1);
            c.fillRoundRect(fx - 4, fy - 22, 8, 24, 4, 0xFFF2C9A1);
            c.line(fx - 11, fy + 6, fx - 15, fy - 2, 5, 0xFFF2C9A1);
        }
    }

    private void drawJudgeSpeech(Gfx c) {
        if (sayT <= 0 || say.isEmpty()) return;
        float fs = sceneH < 130 ? 14 : 15;
        float tw = c.textWidth(say, fs) + 22, bx = Ui.clamp(206 - tw / 2, 10, W - 10 - tw), by = sceneY + 4;
        float pop = Math.max(0, sayT - 1.15f) * 4;
        c.fillRoundRect(bx, by - pop, tw, fs + 14, 9, 0xF21B1E24);
        c.strokeRoundRect(bx, by - pop, tw, fs + 14, 9, 1.5f, sayCol);
        c.text(say, bx + tw / 2, by - pop + fs + 4, fs, sayCol, Gfx.CENTER);
    }

    @Override
    void art(Gfx c, float cx, float cy, float t) {
        // A gavel coming down on its block, with the impact flash.
        c.fillRoundRect(cx - 50, cy + 30, 100, 18, 5, 0xFF5A3418);
        c.fillRoundRect(cx - 40, cy + 24, 80, 10, 4, 0xFF8A5A30);
        float swing = (float) Math.abs(Math.sin(t * 2.2f));
        double ang = 0.05 + swing * 0.9;
        float dx = (float) -Math.cos(ang), dy = (float) -Math.sin(ang), nx = -dy, ny = dx;
        float hx = cx + 64, hy = cy + 4;
        float ex = hx + dx * 66, ey = hy + dy * 66;
        c.line(hx, hy, ex, ey, 7, 0xFF8A5A30);
        c.fillPoly(new float[]{ex - nx * 24 - dx * 14, ey - ny * 24 - dy * 14, ex + nx * 24 - dx * 14,
                ey + ny * 24 - dy * 14, ex + nx * 24 + dx * 14, ey + ny * 24 + dy * 14, ex - nx * 24 + dx * 14,
                ey - ny * 24 + dy * 14}, 0xFF6B3E1C);
        if (swing < 0.3f) {
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + (i - 2) * 0.5;
                c.line(cx + (float) Math.cos(a) * 46, cy + 22 + (float) Math.sin(a) * 20,
                        cx + (float) Math.cos(a) * 70, cy + 22 + (float) Math.sin(a) * 40, 3, Ui.ACCENT);
            }
        }
    }

    @Override
    void bot(Random r, float skill) {
        if (phase != LINE || acted) return;
        if (botLine != li) {
            botLine = li;
            char k = kind[li];
            // Reading the line takes time; a weak lawyer is slower and misjudges more.
            botAt = 0.5f + (1 - skill) * 1.0f + r.nextFloat() * 0.5f;
            float see = 0.3f + 0.68f * skill;
            if (isBad(k)) {
                botAct = r.nextFloat() < see ? 1 : 0;
                // Only a confident lawyer risks the evidence card, and only on the line that needs it.
                if (botAct == 1 && k == 'c' && r.nextFloat() < skill * skill * 0.95f) botAct = 2;
            } else {
                float wolf = (k == 'w' ? 0.75f : 0.35f) * (1 - skill) + (k == 'w' ? 0.03f : 0);
                botAct = r.nextFloat() < wolf ? 1 : 0;
            }
            // A clueless lawyer sometimes waves the evidence around at random.
            if (r.nextFloat() < (1 - skill) * 0.08f) botAct = 2;
        }
        if (botAct > 0 && phaseT >= botAt) {
            float y = btnY + btnH / 2, x = botAct == 2 ? EV_X + EV_W / 2 : OBJ_X + OBJ_W / 2;
            down(x, y);
            up(x, y);
            botAct = 0;
        }
    }
}
