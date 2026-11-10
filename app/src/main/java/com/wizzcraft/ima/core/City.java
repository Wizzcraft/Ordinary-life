package com.wizzcraft.ima.core;

/**
 * The city map: roads, buildings and walking paths. Units are "city units" (360 x 550).
 * Every building stands just north of its street with its door in the front (south) wall, because the
 * map is drawn in a 3/4 view where only front walls show. A building's rectangle is its whole drawn
 * block (roof plus front wall), which is also what a tap hits.
 */
final class City {
    static final float W = 360, H = 550;
    static final float[] ROADS_H = {120, 255, 390, 530};
    static final float[] ROADS_V = {40, 176, 302};
    static final float ROAD_W = 16;

    static final int SCHOOL = 0, CASINO = 1, BAKERY = 2, BAR = 3, RESTAURANT = 4, MARKET = 5, GAS = 6,
            HOME = 7, ALLEY = 8;

    static final class Building {
        final int id;
        final String name;
        /** "To this place", as it reads after "walking ...". */
        final String to;
        final float x, y, w, h, doorX, doorY;
        final int color;
        /** Opening hours in minutes of the day; openFrom == openTo means always open. */
        final int openFrom, openTo;

        Building(int id, String name, String to, float x, float y, float w, float h, float doorX, float doorY,
                 int color, int openFrom, int openTo) {
            this.id = id;
            this.name = name;
            this.to = to;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.doorX = doorX;
            this.doorY = doorY;
            this.color = color;
            this.openFrom = openFrom;
            this.openTo = openTo;
        }

        boolean isOpen(float minute) {
            if (openFrom == openTo) return true;
            if (openFrom < openTo) return minute >= openFrom && minute < openTo;
            return minute >= openFrom || minute < openTo;
        }

        String hours() {
            if (openFrom == openTo) return "פתוח תמיד";
            return "פתוח " + Ui.clock(openFrom) + " עד " + Ui.clock(openTo % 1440);
        }
    }

    /** Index in this array == building id. Doors always sit on a horizontal road. */
    static final Building[] B = {
            new Building(SCHOOL, "מוסד לימודים", "למוסד הלימודים", 54, 44, 108, 61, 108, 120, 0xFF6C8CE0, 8 * 60, 22 * 60),
            new Building(CASINO, "קזינו", "לקזינו", 190, 46, 98, 59, 244, 120, 0xFFC264D9, 20 * 60, 4 * 60),
            new Building(BAKERY, "מאפייה", "למאפייה", 56, 318, 96, 57, 92, 390, 0xFFDDA869, 5 * 60, 14 * 60),
            new Building(BAR, "בר", "לבר", 192, 190, 88, 50, 262, 255, 0xFF45B3AB, 18 * 60, 3 * 60),
            new Building(RESTAURANT, "מסעדה", "למסעדה", 54, 186, 108, 54, 112, 255, 0xFFE8706A, 8 * 60, 24 * 60),
            new Building(MARKET, "סופר", "לסופר", 190, 322, 98, 53, 232, 390, 0xFF7CCB57, 7 * 60, 22 * 60),
            new Building(GAS, "תחנת דלק", "לתחנת הדלק", 190, 460, 98, 55, 270, 530, 0xFFEBC043, 0, 0),
            new Building(HOME, "דירה", "הביתה", 62, 418, 88, 97, 108, 530, 0xFF9AA7BD, 0, 0),
            new Building(ALLEY, "סמטה", "לסמטה", 316, 440, 42, 75, 336, 530, 0xFF4A5261, 22 * 60, 5 * 60),
    };

    private City() {
    }

    static boolean onRoadH(float y) {
        for (float r : ROADS_H) if (Math.abs(y - r) < 0.5f) return true;
        return false;
    }

    /**
     * Walking path along the roads from a point that is on a road to a door on a horizontal road.
     * Returns x,y pairs of the corners to walk through, ending at the door.
     */
    static float[] path(float px, float py, float tx, float ty) {
        if (!onRoadH(py)) {
            // Somewhere along a vertical road: follow it to the target's street, then along the street.
            return new float[]{px, ty, tx, ty};
        }
        if (Math.abs(py - ty) < 0.5f) return new float[]{tx, ty};
        float best = ROADS_V[0], bestCost = Float.MAX_VALUE;
        for (float xv : ROADS_V) {
            float cost = Math.abs(px - xv) + Math.abs(tx - xv);
            if (cost < bestCost) {
                bestCost = cost;
                best = xv;
            }
        }
        return new float[]{best, py, best, ty, tx, ty};
    }

    static float length(float px, float py, float[] path, int from) {
        float len = 0, x = px, y = py;
        for (int i = from; i + 1 < path.length; i += 2) {
            len += Math.abs(path[i] - x) + Math.abs(path[i + 1] - y);
            x = path[i];
            y = path[i + 1];
        }
        return len;
    }
}
