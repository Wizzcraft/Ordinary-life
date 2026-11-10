package com.wizzcraft.ima.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Ui helpers: clock, nis, count, wrap (measured with the desktop renderer's real font metrics). */
public final class UiTest {
    private UiTest() {
    }

    static void testClock() {
        T.eq("00:00", Ui.clock(0));
        T.eq("00:59", Ui.clock(59));
        T.eq("01:00", Ui.clock(60));
        T.eq("09:05", Ui.clock(9 * 60 + 5));
        T.eq("12:30", Ui.clock(12 * 60 + 30));
        T.eq("23:59", Ui.clock(1439));
        T.eq("00:00", Ui.clock(1440), "wraps at midnight");
        T.eq("01:00", Ui.clock(1500));
        T.eq("23:59", Ui.clock(-1), "negative minutes count back from midnight");
        T.eq("23:00", Ui.clock(-60));
        T.eq("02:05", Ui.clock(125.9f), "fractions are dropped, not rounded");
        T.eq("00:00", Ui.clock(1440 * 11), "many days later");
        // Opening hours are written as minutes like 24*60 = 1440 (restaurant closes at 24:00).
        T.eq("00:00", Ui.clock(24 * 60));
    }

    static void testNis() {
        T.eq("₪0", Ui.nis(0));
        T.eq("₪5", Ui.nis(5));
        T.eq("₪1234", Ui.nis(1234));
        T.eq("-₪5", Ui.nis(-5), "a debt shows the minus before the shekel sign");
        T.eq("-₪1", Ui.nis(-1));
        T.isFalse(Ui.nis(70).contains("-"), "positive amounts have no minus");
    }

    static void testCount() {
        T.eq("ארוחה אחת", Ui.count(1, "ארוחה אחת", "ארוחות"));
        T.eq("3 ארוחות", Ui.count(3, "ארוחה אחת", "ארוחות"));
        T.eq("0 ארוחות", Ui.count(0, "ארוחה אחת", "ארוחות"));
    }

    static void testClampAndIn() {
        T.near(0, Ui.clamp(-5, 0, 1), 0);
        T.near(1, Ui.clamp(5, 0, 1), 0);
        T.near(0.5, Ui.clamp(0.5f, 0, 1), 0);
        T.isTrue(Ui.in(10, 10, 10, 10, 5, 5) && Ui.in(15, 15, 10, 10, 5, 5), "edges are inside");
        T.isFalse(Ui.in(9.9f, 12, 10, 10, 5, 5) || Ui.in(12, 15.1f, 10, 10, 5, 5), "just outside");
    }

    static void testDarkenKeepsAlpha() {
        T.eq(0x80000000L, Ui.darken(0x80FFFFFF, 0) & 0xFFFFFFFFL, "darken to black keeps the alpha");
        T.eq(0xFF7F7F7FL, Ui.darken(0xFFFFFFFF, 0.5f) & 0xFFFFFFFFL, "half brightness");
        T.isTrue(Ui.isDark(0xFF000000) && !Ui.isDark(0xFFFFFFFF), "isDark");
    }

    // ---- wrap

    /** The desktop renderer's real font metrics, memoised: measuring through Java2D is slow. */
    static Gfx measuring() {
        AwtGfx awt = new AwtGfx(10, 10, 1f);
        java.util.HashMap<String, Float> memo = new java.util.HashMap<>();
        return new Gfx() {
            public void fillRect(float x, float y, float w, float h, int argb) { }
            public void fillRoundRect(float x, float y, float w, float h, float r, int argb) { }
            public void strokeRoundRect(float x, float y, float w, float h, float r, float sw, int argb) { }
            public void fillPoly(float[] xy, int argb) { }
            public void fillCircle(float cx, float cy, float r, int argb) { }
            public void line(float x1, float y1, float x2, float y2, float sw, int argb) { }
            public void text(String s, float x, float y, float size, int argb, int align) { }
            public float textWidth(String s, float size) {
                return memo.computeIfAbsent(size + "|" + s, k -> awt.textWidth(s, size));
            }
        };
    }

    static final String[] SAMPLES = {
            "לוחצים על מחבת ריקה כדי להתחיל מנה, ושוב כשהטבעת בירוק כדי להגיש. כל מחבת מתחממת בקצב אחר, ומנה שנשכחת נשרפת.",
            "אמא על הקו: בואי תספרי לי מה קורה אצלך בעבודה החדשה, ומה את אוכלת, ואם יש לך מספיק כסף לשכר דירה.",
            "קצר",
            "מילה",
            "שורה אחת\nושורה שנייה ארוכה יותר שצריכה לעבור לשורה נוספת בכל מקרה",
            "ארוך_ארוך_ארוך_ארוך_ארוך_ארוך_ארוך_ארוך_מאוד רגיל",
            "a b c d e f g h i j k l m n o p q r s t u v w x y z",
            "₪120 ו-₪45 ועוד ₪7, בסך הכול ₪172 לחודש",
    };

    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String w : s.split("\\s+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    static void checkWrap(Gfx g, String text, float maxW, float size) {
        List<String> lines = Ui.wrap(g, text, maxW, size);
        T.isTrue(!lines.isEmpty(), "wrap returns at least one line");
        List<String> joined = new ArrayList<>();
        for (String line : lines) {
            joined.addAll(words(line));
            List<String> ws = words(line);
            if (ws.size() > 1) {
                T.isTrue(g.textWidth(line, size) <= maxW + 0.01f, "line wider than " + maxW + " at size " + size + " ("
                        + g.textWidth(line, size) + "): \"" + line + "\"");
            }
        }
        T.eq(words(text), joined, "no word lost or reordered (maxW " + maxW + ", size " + size + ")");
    }

    static void testWrapNoLineTooWideAndNoLostWords() {
        Gfx g = measuring();
        for (String s : SAMPLES) {
            for (float size : new float[]{12, 15, 22}) {
                for (float maxW : new float[]{120, 200, 304, 340}) checkWrap(g, s, maxW, size);
            }
        }
    }

    static void testWrapRandomText() {
        Gfx g = measuring();
        Random r = new Random(5);
        String alphabet = "אבגדהוזחטיכלמנסעפצקרשת";
        for (int n = 0; n < 200; n++) {
            StringBuilder sb = new StringBuilder();
            int words = 1 + r.nextInt(30);
            for (int w = 0; w < words; w++) {
                if (w > 0) sb.append(r.nextInt(15) == 0 ? "\n" : " ");
                int len = 1 + r.nextInt(9);
                for (int i = 0; i < len; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            checkWrap(g, sb.toString(), 100 + r.nextInt(240), 12 + r.nextInt(12));
        }
    }

    static void testWrapEdgeCases() {
        Gfx g = measuring();
        T.eq(1, Ui.wrap(g, "", 100, 14).size(), "empty text is one empty line, not an exception");
        T.eq(2, Ui.wrap(g, "א\nב", 300, 14).size(), "a newline always breaks");
        T.eq(1, Ui.wrap(g, "שלום עולם", 10_000, 14).size(), "fits on one line");
        List<String> tiny = Ui.wrap(g, "שלום עולם יפה", 1, 14);
        T.eq(3, tiny.size(), "a width nothing fits in puts one word per line");
    }
}
