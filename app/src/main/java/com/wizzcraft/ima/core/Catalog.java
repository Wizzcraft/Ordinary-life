package com.wizzcraft.ima.core;

/**
 * The store catalogue and the ways to earn the premium currency. Pure data; docs/STORE.md is the
 * design and the rules behind every number here.
 *
 * Premium currency: "נחת", stored in the shared variable {@code gems}. Ownership is {@code own.<sku>}.
 * Both, and the equipped cosmetics, belong to the player across playthroughs: a new game keeps them.
 *
 * Effects use the grammar of {@link Beat}: {@code effect} is applied once when the item is bought;
 * {@code use} is applied each time a capped convenience is used (null when the engine performs the
 * use itself, as with a taxi ride).
 *
 * Hard rules this data must keep (see STORE.md): nothing grants in-game shekels; nothing is random;
 * every repeatable benefit has a cap; nothing here can be spent in, or won from, the casino or the alley.
 */
final class Catalog {
    private Catalog() {
    }

    /** The premium currency's name, singular and plural alike ("20 נחת"). */
    static final String CURRENCY = "נחת";

    // ---- categories, in the order the store shows its sections ----
    static final int LOOK = 0, ACCESSORY = 1, DECOR = 2, THEME = 3, RINGTONE = 4, VOICE = 5, LINES = 6,
            CONVENIENCE = 7, CONTENT = 8, PACK = 9, SUPPORTER = 10;
    static final String[] CATEGORY_NAMES = {
            "בגדים", "אביזרים", "לדירה", "צבעי העיר", "צלצולים של אמא", "הקול של אמא", "המשפטים של אמא",
            "נוחות", "בקרוב", "נחת בכסף אמיתי", "תמיכה במשחק"};

    /** Real-money tiers in agorot, VAT included. Shown only as a fallback: in real mode Play supplies the price. */
    static final int TIER_490 = 490, TIER_1290 = 1290, TIER_2490 = 2490, TIER_2990 = 2990;

    static final class Item {
        final String sku, icon, name, desc;
        final int category;
        /** Price in נחת; 0 with agorot == 0 means a free starter that every player owns. */
        final int gems;
        /** Real-money price in agorot; 0 for items bought with נחת. */
        final int agorot;
        final String effect, use;
        /** Uses allowed per period (convenience) or 1 for a one-time unlock. */
        final int cap;
        /** Length of a cap period in game days; 0 = once, ever. */
        final int periodDays;
        /** False while the item is announced but not yet in the game. */
        final boolean available;

        Item(String sku, int category, String icon, String name, String desc, int gems, int agorot, String effect,
             String use, int cap, int periodDays, boolean available) {
            this.sku = sku;
            this.category = category;
            this.icon = icon;
            this.name = name;
            this.desc = desc;
            this.gems = gems;
            this.agorot = agorot;
            this.effect = effect;
            this.use = use;
            this.cap = cap;
            this.periodDays = periodDays;
            this.available = available;
        }

        boolean realMoney() {
            return agorot > 0;
        }

        boolean free() {
            return gems == 0 && agorot == 0;
        }
    }

    private static Item unlock(String sku, int cat, String icon, String name, String desc, int gems) {
        return new Item(sku, cat, icon, name, desc, gems, 0, "own." + sku + "=1", null, 1, 0, true);
    }

    private static Item later(String sku, String icon, String name, String desc, int gems) {
        return new Item(sku, CONTENT, icon, name, desc, gems, 0, "own." + sku + "=1", null, 1, 0, false);
    }

    private static Item money(String sku, int cat, String icon, String name, String desc, int agorot, String effect,
                              int cap) {
        return new Item(sku, cat, icon, name, desc, 0, agorot, effect, null, cap, 0, true);
    }

    static final Item[] ALL = {
            // Looks: the first collection. Skus must match the character artist's look ids at integration.
            unlock("look.basic", LOOK, "shirt", "בגדי יום יום", "מה שנכנס למזוודה ביום שעברת לעיר", 0),
            unlock("look.sweater", LOOK, "sweater", "הסוודר שאמא סרגה", "חם, קצת עקום, ואסור להוריד כשהיא מתקשרת", 0),
            unlock("look.hoodie", LOOK, "hoodie", "קפוצ'ון עירוני", "הקפוצ'ון שכל העיר לובשת, רק בצבע שלך", 40),
            unlock("look.shirt", LOOK, "buttons", "חולצה מכופתרת", "לראיונות עבודה ולחתונות של אחרים", 50),
            unlock("look.tracksuit", LOOK, "stripes", "אימונית", "נראית כמו כושר גם בלי לעשות כושר", 40),
            unlock("look.summer", LOOK, "flipflop", "קיץ עירוני", "מכנסיים קצרים, כפכפים, ובלי שום התנצלות", 50),
            unlock("look.retro", LOOK, "star", "שנות השמונים", "צבעים זוהרים, כריות כתפיים וביטחון עצמי מלא", 60),
            unlock("look.apron", LOOK, "apron", "סינר של שף", "קישוט בלבד. לא מבשל במקומך, לצערנו", 60),
            unlock("look.evening", LOOK, "bowtie", "בגדי ערב", "למקרה שמישהו יזמין אותך למקום עם מפות לבנות", 80),
            unlock("look.pajamas", LOOK, "cloud", "פיג'מה עם עננים", "יוצאים ככה לסופר? יוצאים ככה לסופר.", 70),

            // Accessories: the first eight.
            unlock("acc.cap", ACCESSORY, "cap", "כובע מצחייה", "שומר מהשמש ומשיחות עם שכנים", 20),
            unlock("acc.shades", ACCESSORY, "glasses", "משקפי שמש", "שאף אחד לא יראה שלא ישנת", 25),
            unlock("acc.pins", ACCESSORY, "backpack", "תרמיל עם סיכות", "קישוט בלבד. לא מוסיף מקום בתיק", 30),
            unlock("acc.scarf", ACCESSORY, "scarf", "צעיף ארוך", "ארוך מספיק לכרוך פעמיים ועוד להשאיר זנב", 25),
            unlock("acc.phones", ACCESSORY, "headphones", "אוזניות גדולות", "את העיר לא שומעים. את אמא, תמיד.", 35),
            unlock("acc.watch", ACCESSORY, "watch", "השעון של סבא", "מאחר בשתי דקות כבר ארבעים שנה", 35),
            unlock("acc.pendant", ACCESSORY, "pendant", "שרשרת עם תליון", "מתנה ממישהו. לא שואלים ממי.", 35),
            unlock("acc.umbrella", ACCESSORY, "umbrella", "מטרייה צבעונית", "גם כשאין גשם. במיוחד כשאין גשם.", 30),

            // Decor for the flat.
            unlock("decor.plant", DECOR, "plant", "עציץ שעדיין חי", "בינתיים. צריך להשקות אותו מדי פעם", 25),
            unlock("decor.poster", DECOR, "frame", "פוסטר של ים", "חלון לנוף שאין לדירה הזאת", 30),
            unlock("decor.sofa", DECOR, "sofa", "ספה מיד שנייה", "רק כמה כתמים. כמעט לא רואים.", 70),
            unlock("decor.lights", DECOR, "bulbs", "שרשרת נורות", "הופכת חור בקיר לדירת סטודיו", 40),
            unlock("decor.rug", DECOR, "rug", "שטיח כמעט פרסי", "פרסי לגמרי, אם לא מסתכלים על התווית", 45),
            unlock("decor.photos", DECOR, "fridge", "תמונות על המקרר", "המשפחה מסתכלת עליך כל בוקר. באהבה.", 35),

            // City colour themes.
            unlock("theme.sunset", THEME, "sun", "עיר בשקיעה", "כל העיר בכתום ורוד, כל היום", 80),
            unlock("theme.winter", THEME, "drop", "חורף בעיר", "שמיים אפורים, שלוליות ומטריות ברחוב", 80),
            unlock("theme.neon", THEME, "moon", "עיר של לילה", "שלטי ניאון וחלונות דולקים עד הבוקר", 100),

            // Mom's ringtones, voice styles and alternative one-liners. Flavour only: same choices, same effects.
            unlock("ring.drama", RINGTONE, "bell", "צלצול דרמטי", "כאילו כל שיחה היא סוף עונה", 20),
            unlock("ring.lullaby", RINGTONE, "note", "שיר ערש", "מנגינה רכה, כמו פעם, רק עם רטט", 20),
            unlock("ring.march", RINGTONE, "trumpet", "מארש חגיגי", "כל שיחה מאמא היא אירוע ממלכתי", 25),
            unlock("ring.rooster", RINGTONE, "rooster", "תרנגול", "קוקוריקו בכל שיחה. אמא לא צריכה לדעת.", 25),
            unlock("voice.whisper", VOICE, "hush", "אמא לוחשת", "כל שיחה נשמעת כמו סוד מדינה", 40),
            unlock("voice.opera", VOICE, "mask", "אמא באופרה", "כל משפט שלה הוא אריה", 50),
            unlock("voice.fast", VOICE, "rabbit", "אמא על מהיר", "אותן דאגות, פי שניים מהר", 40),
            unlock("lines.grandma", LINES, "pot", "אמא בגרסת סבתא", "אותה אמא, רק שואלת אם אכלת כל שתי דקות", 60),
            unlock("lines.coach", LINES, "whistle", "אמא המאמנת", "כל שיחה נשמעת כמו נאום לפני גמר", 60),
            unlock("lines.sage", LINES, "scroll", "אמא הפילוסופית", "דאגות עם ציטוטים שהיא המציאה לבד", 60),

            // Convenience: every one has a hard cap and none of them gives shekels.
            unlock("conv.bag", CONVENIENCE, "bag", "כיס נוסף בתיק", "מקום לעוד ארוחה או בקבוק. אחד, וזהו.", 60),
            new Item("conv.care", CONVENIENCE, "box", "חבילה מאמא", "פעם בשבוע מחכות בבית שתי ארוחות ושני בקבוקים",
                    120, 0, "own.conv.care=1", "meals+=2;bottles+=2", 1, 7, true),
            new Item("conv.taxi", CONVENIENCE, "taxi", "כרטיסיית מוניות", "שלוש נסיעות חינם בחודש. מה שלא נוצל, לא נצבר.",
                    150, 0, "own.conv.taxi=1", null, 3, Game.DAYS_PER_MONTH, true),

            // Content, later. Announced only once it exists; never sold before it is playable.
            later("content.port", "anchor", "שכונת הנמל", "שכונה חדשה עם עבודות חדשות ודירה ליד הים", 300),
            later("content.vet", "paw", "מקצוע חדש: וטרינריה", "לימודים, עבודה חדשה ומשחק משלה", 300),
            later("content.year2", "calendar", "פרק נוסף: השנה השנייה", "ההורים חזרו הביתה. עכשיו סבתא באה לבקר.", 300),

            // Real money. Packs are consumable; the supporter pack is bought once.
            money("pack.small", PACK, "jar", "צנצנת נחת", "צנצנת עם 60 נחת", TIER_490, "gems+=60", 0),
            money("pack.medium", PACK, "box", "קופסת נחת", "קופסה עם 170 נחת", TIER_1290, "gems+=170", 0),
            money("pack.large", PACK, "crate", "ארגז נחת", "ארגז עם 360 נחת", TIER_2490, "gems+=360", 0),
            money("supporter", SUPPORTER, "heart", "חבילת תומכים", "מגנט תודה למקרר ועוד 500 נחת. תודה שתמכת!",
                    TIER_2990, "gems+=500;own.supporter=1", 1),
    };

    /** A way to earn נחת by playing. Each is awarded at most {@code max} times per playthrough. */
    static final class Earn {
        final String id, name;
        final int amount, max;

        Earn(String id, String name, int amount, int max) {
            this.id = id;
            this.name = name;
            this.amount = amount;
            this.max = max;
        }
    }

    /**
     * Counted in {@code earn.<id>} (reset on a new game). No entry may be triggered by the casino or
     * the alley, and none rewards logging in on consecutive real days.
     */
    static final Earn[] EARN = {
            new Earn("welcome", "מתנת פרידה מאמא", 30, 1),
            new Earn("first_shift", "משמרת ראשונה", 10, 1),
            new Earn("month_clean", "חודש בלי חובות", 30, 12),
            new Earn("promo1", "קידום ראשון בעבודה", 20, 6),
            new Earn("promo2", "ניהול משמרת", 30, 6),
            new Earn("honest3", "שלוש שיחות בלי שקר", 20, 6),
            new Earn("stat50", "חצי הדרך", 15, 3),
            new Earn("stat80", "כמעט מושלם", 25, 3),
            new Earn("course", "סיום קורס", 30, 3),
            new Earn("profession", "עבודה במקצוע", 50, 1),
            new Earn("explorer", "כל העיר בכף היד", 20, 1),
            new Earn("visit", "שרדת את הביקור", 80, 1),
            new Earn("visit_honest", "ביקור בלי אף שקר", 50, 1),
    };

    static Item find(String sku) {
        for (Item it : ALL) if (it.sku.equals(sku)) return it;
        return null;
    }

    /** What all play-affecting items cost together; a free player earns this within one playthrough. */
    static int convenienceTotal() {
        int sum = 0;
        for (Item it : ALL) if (it.category == CONVENIENCE) sum += it.gems;
        return sum;
    }
}
