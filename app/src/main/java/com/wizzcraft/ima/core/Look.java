package com.wizzcraft.ima.core;

/**
 * What a person wears: plain data that {@link PersonArt} draws from. Every visible choice lives here
 * so the store can sell any part of it. Instances are mutable on purpose: the engine keeps one Look
 * for the player and changes it in place when something is equipped, so drawing never allocates.
 *
 * Accessories sit in four slots (head, face, neck, back); each slot holds one accessory id or
 * {@link #NONE}. {@link #ACC_SLOT} says which slot an id belongs in.
 */
final class Look {
    // ---- Hair styles --------------------------------------------------------------------------
    static final int HAIR_BUZZ = 0, HAIR_SHORT = 1, HAIR_CURLY = 2, HAIR_LONG = 3, HAIR_PONY = 4,
            HAIR_BUN = 5, HAIR_BOB = 6, HAIR_QUIFF = 7, HAIR_WAVY = 8;
    static final String[] HAIR_NAMES = {
            "קצוץ", "קצר", "מתולתל", "ארוך", "קוקו", "פקעת", "קארה", "בלורית", "גלי"};

    // ---- Tops and bottoms ---------------------------------------------------------------------
    static final int TOP_TEE = 0, TOP_JACKET = 1, TOP_HOODIE = 2, TOP_SHIRT = 3;
    static final String[] TOP_NAMES = {"חולצת טי", "ז'קט", "קפוצ'ון", "חולצה מכופתרת"};
    static final int BOTTOM_PANTS = 0, BOTTOM_SHORTS = 1, BOTTOM_SKIRT = 2;
    static final String[] BOTTOM_NAMES = {"מכנסיים", "מכנסיים קצרים", "חצאית"};

    // ---- Accessories --------------------------------------------------------------------------
    static final int NONE = 0, CAP = 1, BEANIE = 2, SUNHAT = 3, HEADPHONES = 4, FLOWER = 5,
            GLASSES = 6, SUNGLASSES = 7, SCARF = 8, TIE = 9, BACKPACK = 10, TOTE = 11, GUITAR = 12;
    static final int SLOT_HEAD = 0, SLOT_FACE = 1, SLOT_NECK = 2, SLOT_BACK = 3;
    static final int[] ACC_SLOT = {
            -1, SLOT_HEAD, SLOT_HEAD, SLOT_HEAD, SLOT_HEAD, SLOT_HEAD,
            SLOT_FACE, SLOT_FACE, SLOT_NECK, SLOT_NECK, SLOT_BACK, SLOT_BACK, SLOT_BACK};
    static final String[] ACC_NAMES = {
            "בלי", "כובע מצחייה", "כובע גרב", "כובע קש", "אוזניות", "סיכת פרח",
            "משקפיים", "משקפי שמש", "צעיף", "עניבה", "תיק גב", "תיק צד", "גיטרה"};
    static final String[] SLOT_NAMES = {"ראש", "פנים", "צוואר", "גב"};

    // ---- Skin tones (light to deep); any argb works, these are the ones offered -------------
    static final int[] SKINS = {
            0xFFF6D3B3, 0xFFEFC19A, 0xFFE0A87E, 0xFFC98E62, 0xFFA8704A, 0xFF85543A, 0xFF5E3B28};
    static final String[] SKIN_NAMES = {"בהיר מאוד", "בהיר", "שקדי", "שזוף", "חום", "כהה", "כהה מאוד"};

    // ---- Fields -------------------------------------------------------------------------------
    String id, name;
    int skin, hair, hairStyle, top, topStyle, bottom, bottomStyle, shoes;
    /** Colour for the accessories: hat, scarf, bag, headphones, flower, tie. */
    int accent;
    /** Accessory id per slot, indexed by SLOT_*. */
    final int[] acc = new int[4];

    Look(String id, String name, int skin, int hair, int hairStyle, int top, int topStyle,
         int bottom, int bottomStyle, int shoes, int accent, int head, int face, int neck, int back) {
        this.id = id;
        this.name = name;
        this.skin = skin;
        this.hair = hair;
        this.hairStyle = hairStyle;
        this.top = top;
        this.topStyle = topStyle;
        this.bottom = bottom;
        this.bottomStyle = bottomStyle;
        this.shoes = shoes;
        this.accent = accent;
        acc[SLOT_HEAD] = head;
        acc[SLOT_FACE] = face;
        acc[SLOT_NECK] = neck;
        acc[SLOT_BACK] = back;
    }

    Look copy() {
        Look l = new Look(id, name, skin, hair, hairStyle, top, topStyle, bottom, bottomStyle, shoes, accent,
                acc[0], acc[1], acc[2], acc[3]);
        return l;
    }

    /** Copies every field of {@code o} into this look (no allocation). */
    Look set(Look o) {
        id = o.id;
        name = o.name;
        skin = o.skin;
        hair = o.hair;
        hairStyle = o.hairStyle;
        top = o.top;
        topStyle = o.topStyle;
        bottom = o.bottom;
        bottomStyle = o.bottomStyle;
        shoes = o.shoes;
        accent = o.accent;
        System.arraycopy(o.acc, 0, acc, 0, 4);
        return this;
    }

    /** Puts an accessory in its own slot, replacing what was there. Unknown ids are ignored. */
    Look wear(int accessory) {
        if (accessory > 0 && accessory < ACC_SLOT.length) acc[ACC_SLOT[accessory]] = accessory;
        return this;
    }

    /** Empties one slot (SLOT_*). */
    Look takeOff(int slot) {
        if (slot >= 0 && slot < 4) acc[slot] = NONE;
        return this;
    }

    boolean wears(int accessory) {
        return accessory > 0 && accessory < ACC_SLOT.length && acc[ACC_SLOT[accessory]] == accessory;
    }

    /** Index 0 is the default look; the rest are wardrobe outfits the store can sell. */
    static final Look[] PRESETS = {
            new Look("basic", "יום ראשון בעיר", SKINS[2], 0xFF3A2A20, HAIR_SHORT,
                    0xFFFFC857, TOP_TEE, 0xFF34507A, BOTTOM_PANTS, 0xFFECEFF3, 0xFFFF6B6B,
                    NONE, NONE, NONE, BACKPACK),
            new Look("student", "חיי קמפוס", SKINS[1], 0xFF6B4A2E, HAIR_CURLY,
                    0xFF6FA86A, TOP_HOODIE, 0xFF2E3A48, BOTTOM_PANTS, 0xFFE8E0D0, 0xFFD9534F,
                    BEANIE, GLASSES, NONE, BACKPACK),
            new Look("runner", "ריצת בוקר", SKINS[4], 0xFF1E1A18, HAIR_PONY,
                    0xFFFF6F91, TOP_TEE, 0xFF22272F, BOTTOM_SHORTS, 0xFF62E0C4, 0xFF62E0C4,
                    CAP, NONE, NONE, NONE),
            new Look("office", "משרד היי־טק", SKINS[0], 0xFF8A5A33, HAIR_QUIFF,
                    0xFFE9EEF5, TOP_SHIRT, 0xFF39414E, BOTTOM_PANTS, 0xFF3A2A22, 0xFF4F7BE0,
                    NONE, GLASSES, TIE, TOTE),
            new Look("beach", "קיץ על החוף", SKINS[3], 0xFFE3C07A, HAIR_WAVY,
                    0xFF5FD0E6, TOP_TEE, 0xFFF2E6CC, BOTTOM_SHORTS, 0xFFC48A5A, 0xFFF3E1A6,
                    SUNHAT, SUNGLASSES, NONE, NONE),
            new Look("rocker", "לילה בבר", SKINS[1], 0xFF161616, HAIR_BOB,
                    0xFF3E4452, TOP_JACKET, 0xFF23262C, BOTTOM_SKIRT, 0xFF101214, 0xFFD94A5A,
                    NONE, SUNGLASSES, NONE, NONE),
            new Look("musician", "הופעת רחוב", SKINS[5], 0xFF15110E, HAIR_BUZZ,
                    0xFFE07A3F, TOP_SHIRT, 0xFF3F3A33, BOTTOM_PANTS, 0xFF7A5232, 0xFFB9783C,
                    HEADPHONES, NONE, NONE, GUITAR),
            new Look("winter", "חורף ירושלמי", SKINS[2], 0xFF4A2C1E, HAIR_LONG,
                    0xFF8C6BC9, TOP_JACKET, 0xFF2D3340, BOTTOM_PANTS, 0xFF2A2420, 0xFFE8C547,
                    NONE, NONE, SCARF, NONE),
            new Look("market", "יום שישי בשוק", SKINS[6], 0xFF100C0A, HAIR_BUN,
                    0xFFF2D25C, TOP_SHIRT, 0xFF5A7D5A, BOTTOM_SKIRT, 0xFFB76B4B, 0xFFE8655A,
                    FLOWER, NONE, NONE, TOTE),
            new Look("gamer", "משחקים עד הבוקר", SKINS[3], 0xFF2C2440, HAIR_SHORT,
                    0xFF4A4F9E, TOP_HOODIE, 0xFF23262E, BOTTOM_SHORTS, 0xFFE8ECF2, 0xFF5BF0A0,
                    HEADPHONES, GLASSES, NONE, NONE),
            new Look("chef", "אחרי המשמרת", SKINS[4], 0xFF8E8E8E, HAIR_SHORT,
                    0xFFF4F1EA, TOP_SHIRT, 0xFF26303C, BOTTOM_PANTS, 0xFF2B2B2B, 0xFFFF9F5A,
                    NONE, NONE, SCARF, NONE),
            new Look("fancy", "ערב חגיגי", SKINS[5], 0xFF2A1A12, HAIR_WAVY,
                    0xFFB8323F, TOP_JACKET, 0xFF1D1F26, BOTTOM_SKIRT, 0xFF1A1A1A, 0xFFFFD67A,
                    NONE, NONE, TIE, NONE),
    };

    /** A preset by index, clamped so a bad saved value still draws something. */
    static Look preset(int i) {
        return PRESETS[i < 0 || i >= PRESETS.length ? 0 : i];
    }

    private static final int[] CROWD_HAIR = {
            0xFF1A1512, 0xFF3A2A20, 0xFF6B4A2E, 0xFF8A5A33, 0xFFC9A36A, 0xFF9A9A9A, 0xFF5A2E22, 0xFF2A2A2A};
    private static final int[] CROWD_CLOTH = {
            0xFF5AA9FF, 0xFFFF9F5A, 0xFF6FA86A, 0xFFE9EEF5, 0xFFB8323F, 0xFF8C6BC9, 0xFF3D4656, 0xFFF2D25C,
            0xFF5FD0E6, 0xFFE07A3F, 0xFF4A4F9E, 0xFFD9D2C3};
    private static final int[] CROWD_BOTTOM = {
            0xFF34507A, 0xFF2E3A48, 0xFF22272F, 0xFF5A4A3A, 0xFF3F3A33, 0xFF6E7B8A, 0xFF1D1F26};

    /**
     * Fills {@code out} with a deterministic passer-by for index {@code i}: a preset's accessories with
     * varied skin, hair and colours, so ten pedestrians never look like clones. No allocation.
     */
    static Look crowd(int i, Look out) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        out.set(PRESETS[1 + Math.floorMod(h, PRESETS.length - 1)]);
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        out.skin = SKINS[Math.floorMod(h, SKINS.length)];
        out.hair = CROWD_HAIR[Math.floorMod(h >> 3, CROWD_HAIR.length)];
        out.hairStyle = Math.floorMod(h >> 6, HAIR_NAMES.length);
        out.top = CROWD_CLOTH[Math.floorMod(h >> 10, CROWD_CLOTH.length)];
        out.bottom = CROWD_BOTTOM[Math.floorMod(h >> 14, CROWD_BOTTOM.length)];
        out.accent = CROWD_CLOTH[Math.floorMod(h >> 18, CROWD_CLOTH.length)];
        if (Math.floorMod(h >> 22, 3) == 0) out.acc[SLOT_FACE] = NONE;
        if (Math.floorMod(h >> 24, 3) == 0) out.acc[SLOT_BACK] = NONE;
        return out;
    }

    /** A preset by id, or the default. */
    static Look preset(String id) {
        for (Look l : PRESETS) if (l.id.equals(id)) return l;
        return PRESETS[0];
    }
}
