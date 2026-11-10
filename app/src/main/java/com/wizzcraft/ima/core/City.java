package com.wizzcraft.ima.core;

/** The city map: roads, buildings and walking paths. Units are "city units" (360 x 520). */
final class City {
    static final float W = 360, H = 520;
    static final float[] ROADS_H = {60, 190, 330, 470};
    static final float[] ROADS_V = {40, 180, 320};
    static final float ROAD_W = 16;

    static final int SCHOOL = 0, CASINO = 1, BAKERY = 2, BAR = 3, RESTAURANT = 4, MARKET = 5, GAS = 6,
            HOME = 7, ALLEY = 8, HOSTEL = 9, OFFICE = 10;

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
            new Building(SCHOOL, "מוסד לימודים", "למוסד הלימודים", 58, 72, 104, 46, 110, 60, 0xFF6C8CE0, 8 * 60, 22 * 60),
            new Building(CASINO, "קזינו", "לקזינו", 198, 72, 104, 46, 250, 60, 0xFFC264D9, 20 * 60, 4 * 60),
            new Building(BAKERY, "מאפייה", "למאפייה", 58, 132, 104, 46, 90, 190, 0xFFDDA869, 5 * 60, 14 * 60),
            new Building(BAR, "בר", "לבר", 198, 132, 104, 46, 270, 190, 0xFF45B3AB, 18 * 60, 3 * 60),
            new Building(RESTAURANT, "מסעדה", "למסעדה", 58, 202, 104, 46, 130, 190, 0xFFE8706A, 8 * 60, 24 * 60),
            new Building(MARKET, "סופר", "לסופר", 198, 202, 104, 46, 230, 190, 0xFF7CCB57, 7 * 60, 22 * 60),
            new Building(GAS, "תחנת דלק", "לתחנת הדלק", 198, 272, 104, 46, 250, 330, 0xFFEBC043, 0, 0),
            new Building(HOME, "דירה", "הביתה", 58, 412, 104, 46, 110, 470, 0xFF9AA7BD, 0, 0),
            new Building(ALLEY, "סמטה", "לסמטה", 198, 482, 104, 34, 250, 470, 0xFF4A5261, 22 * 60, 5 * 60),
            // The hostel is 87 walking minutes from the restaurant corner (Tuning.HOSTEL_TO_CENTRE is 90).
            new Building(HOSTEL, "אכסניה", "לאכסניה", 58, 272, 104, 46, 110, 330, 0xFFB98A6A, 0, 0),
            new Building(OFFICE, "בניין משרדים", "לבניין המשרדים", 198, 342, 104, 46, 230, 330, 0xFF7FA0B8, 7 * 60, 20 * 60),
    };

    private City() {
    }

    /** Length of the walk between the doors of two buildings, in city units. */
    static float distance(int from, int to) {
        Building a = B[from], b = B[to];
        return length(a.doorX, a.doorY, path(a.doorX, a.doorY, b.doorX, b.doorY), 0);
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
