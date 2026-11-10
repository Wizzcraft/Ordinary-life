package com.wizzcraft.ima.core;

/** Walking paths on the city map: every door reachable, lengths symmetric, distances in the brief's bands. */
public final class CityTest {
    private CityTest() {
    }

    static boolean has(float[] set, float v) {
        for (float s : set) if (Math.abs(s - v) < 0.01f) return true;
        return false;
    }

    /** Checks that the path walks only along roads, axis-aligned, and ends exactly at (tx, ty). */
    static void checkPath(float px, float py, float[] p, float tx, float ty, String what) {
        T.isTrue(p != null && p.length >= 2 && p.length % 2 == 0, what + ": path has an even number (>= 2) of values");
        float x = px, y = py;
        for (int i = 0; i < p.length; i += 2) {
            float nx = p[i], ny = p[i + 1];
            boolean horizontal = Math.abs(ny - y) < 0.01f, vertical = Math.abs(nx - x) < 0.01f;
            T.isTrue(horizontal || vertical, what + ": segment " + (i / 2) + " from (" + x + "," + y + ") to (" + nx + "," + ny + ") is diagonal");
            if (horizontal && Math.abs(nx - x) > 0.01f) T.isTrue(has(City.ROADS_H, y), what + ": horizontal segment at y=" + y + " is not on a road");
            if (vertical && Math.abs(ny - y) > 0.01f) T.isTrue(has(City.ROADS_V, x), what + ": vertical segment at x=" + x + " is not on a road");
            x = nx;
            y = ny;
        }
        T.near(tx, x, 0.01, what + ": ends at the door x");
        T.near(ty, y, 0.01, what + ": ends at the door y");
    }

    static void testDoorsSitOnHorizontalRoads() {
        T.eq(City.B.length, 9, "building count");
        for (int i = 0; i < City.B.length; i++) {
            City.Building b = City.B[i];
            T.eq(i, b.id, b.name + " id equals its index");
            T.isTrue(City.onRoadH(b.doorY), b.name + " door y=" + b.doorY + " is on a horizontal road");
            T.isTrue(b.doorX >= b.x && b.doorX <= b.x + b.w, b.name + " door x is under the building");
        }
    }

    static void testEveryDoorReachesEveryDoor() {
        for (City.Building a : City.B) {
            for (City.Building b : City.B) {
                float[] p = City.path(a.doorX, a.doorY, b.doorX, b.doorY);
                checkPath(a.doorX, a.doorY, p, b.doorX, b.doorY, a.name + " -> " + b.name);
                float len = City.length(a.doorX, a.doorY, p, 0);
                float manhattan = Math.abs(a.doorX - b.doorX) + Math.abs(a.doorY - b.doorY);
                T.isTrue(len >= manhattan - 0.01f, a.name + " -> " + b.name + ": path " + len + " is shorter than the straight Manhattan distance " + manhattan);
                if (a == b) T.near(0, len, 0.01, a.name + " to itself");
            }
        }
    }

    static void testPointsOnVerticalRoadsReachEveryDoor() {
        for (float vx : City.ROADS_V) {
            for (float y = 0; y <= City.H; y += 7) {
                if (City.onRoadH(y)) continue;
                for (City.Building b : City.B) {
                    float[] p = City.path(vx, y, b.doorX, b.doorY);
                    checkPath(vx, y, p, b.doorX, b.doorY, "from road x=" + vx + ",y=" + y + " to " + b.name);
                    float len = City.length(vx, y, p, 0);
                    float manhattan = Math.abs(vx - b.doorX) + Math.abs(y - b.doorY);
                    T.near(manhattan, len, 0.01, "from a vertical road the walk is exactly Manhattan to " + b.name);
                }
            }
        }
    }

    static void testPathLengthIsSymmetric() {
        for (City.Building a : City.B) {
            for (City.Building b : City.B) {
                float ab = City.length(a.doorX, a.doorY, City.path(a.doorX, a.doorY, b.doorX, b.doorY), 0);
                float ba = City.length(b.doorX, b.doorY, City.path(b.doorX, b.doorY, a.doorX, a.doorY), 0);
                T.near(ab, ba, 0.5, a.name + " <-> " + b.name + " length");
            }
        }
    }

    static void testLengthFromIndexSkipsTheWalkedPart() {
        City.Building a = City.B[City.HOME], b = City.B[City.CASINO];
        float[] p = City.path(a.doorX, a.doorY, b.doorX, b.doorY);
        float whole = City.length(a.doorX, a.doorY, p, 0);
        float rest = City.length(p[0], p[1], p, 2);
        float first = Math.abs(p[0] - a.doorX) + Math.abs(p[1] - a.doorY);
        T.near(whole, first + rest, 0.01, "length(from=0) == first leg + length(from=2)");
        T.near(0, City.length(b.doorX, b.doorY, p, p.length), 0.01, "nothing left once the walk is over");
    }

    /**
     * The brief: a game hour is 3 real seconds and walking across town costs 2-3 game hours; home is
     * rented cheap and far from everything. Walking speed is Game.WALK_SPEED city units per game minute.
     */
    static void testDistancesMatchTheBrief() {
        float maxHours = 0, minHours = Float.MAX_VALUE, homeMin = Float.MAX_VALUE;
        String maxPair = "", minPair = "";
        for (City.Building a : City.B) {
            for (City.Building b : City.B) {
                if (a == b) continue;
                float hours = City.length(a.doorX, a.doorY, City.path(a.doorX, a.doorY, b.doorX, b.doorY), 0) / Game.WALK_SPEED / 60f;
                if (hours > maxHours) {
                    maxHours = hours;
                    maxPair = a.name + " - " + b.name;
                }
                if (hours < minHours) {
                    minHours = hours;
                    minPair = a.name + " - " + b.name;
                }
                if (a.id == City.HOME) homeMin = Math.min(homeMin, hours);
            }
        }
        // The brief says "2-3 game hours": a hard failure outside 1.8-3.3, a warning outside 2-3.
        T.isTrue(maxHours >= 1.8f && maxHours <= 3.3f,
                "the longest walk across town (" + maxPair + ") takes " + maxHours + " game hours, the brief says 2-3");
        if (maxHours < 2.0f || maxHours > 3.0f) {
            T.warn("the longest walk across town (" + maxPair + ") takes " + maxHours + " game hours; the brief says 2-3");
        }
        T.isTrue(minHours >= 0.1f, "the shortest walk (" + minPair + ") takes " + minHours + " game hours: two doors that close are pointless");
        T.isTrue(homeMin >= 0.5f, "home is 'far from everything' but its nearest building is only " + homeMin + " game hours away (want >= 0.5)");
        float[] mean = new float[City.B.length];
        float all = 0;
        for (City.Building a : City.B) {
            for (City.Building b : City.B) {
                if (a == b) continue;
                mean[a.id] += City.length(a.doorX, a.doorY, City.path(a.doorX, a.doorY, b.doorX, b.doorY), 0) / Game.WALK_SPEED / 60f / (City.B.length - 1);
            }
            all += mean[a.id] / City.B.length;
        }
        T.isTrue(mean[City.HOME] >= 1.2f * all, "home's mean walk to the others is " + mean[City.HOME] + " game hours, the town average is " + all
                + ": home should be at least 20% farther than average from everything");
        for (City.Building b : City.B) {
            if (b.id == City.HOME) continue;
            float h = City.length(City.B[City.HOME].doorX, City.B[City.HOME].doorY,
                    City.path(City.B[City.HOME].doorX, City.B[City.HOME].doorY, b.doorX, b.doorY), 0) / Game.WALK_SPEED / 60f;
            T.isTrue(h <= 3.3f, "home -> " + b.name + " takes " + h + " game hours (more than 3.3 makes the day unplayable)");
        }
    }

    static void testOpeningHours() {
        City.Building bakery = City.B[City.BAKERY], bar = City.B[City.BAR], gas = City.B[City.GAS];
        T.isTrue(bakery.isOpen(5 * 60), "bakery opens at 05:00");
        T.isFalse(bakery.isOpen(14 * 60), "bakery is closed from 14:00");
        T.isTrue(bar.isOpen(23 * 60) && bar.isOpen(60) && !bar.isOpen(12 * 60), "bar hours wrap midnight");
        T.isTrue(gas.isOpen(0) && gas.isOpen(12 * 60), "gas station is always open");
        for (City.Building b : City.B) T.isTrue(b.hours().length() > 0, b.name + " has a readable hours line");
    }
}
