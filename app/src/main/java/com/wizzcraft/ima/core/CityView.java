package com.wizzcraft.ima.core;

/**
 * How the town is painted and how a tap picks a place. {@link MapScreen} owns walking, the clock,
 * the HUD, the status strip and the bottom bar; a view gets the rectangle between them.
 * The default is {@link FlatCity} (the map from above); the 3D town replaces it with one line:
 * {@code MapScreen.view = new Town3d();}
 */
interface CityView {
    /** Paints the town between y = top and y = bottom, across the full width 0..360. */
    void draw(Gfx c, Town t, float top, float bottom);

    /** The building under a tap at (x, y) in logical screen units: a City id, or -1 for none. */
    int pick(Town t, float x, float y, float top, float bottom);

    /** Called every frame with real seconds, for the view's own animation (camera easing, people). */
    default void update(Town t, float dt) {
    }

    /** What a view may know about the town right now. Refreshed by MapScreen before every call; read-only. */
    final class Town {
        /** The player, in city units (City.W x City.H). */
        float px, py;
        /** Heading: the unit vector of the last step walked; (0, -1) before the first one. */
        float hx, hy = -1;
        boolean walking, fast, female;
        int day;
        /** Minutes since midnight, 0..1439.99. */
        float minute;
        /** Building the player stands at, and the one being walked to; -1 for none. */
        int at = -1, target = -1;
        /** The building whose destination card is open, or -1. */
        int selected = -1;
        /** The rest of the walk: corners x,y,... in city units from index pathAt; null when standing. */
        float[] path;
        int pathAt;
        /** Per building id: open right now. */
        boolean[] open = new boolean[City.B.length];
        /** Real seconds since the map opened (wrapped, so animation never loses precision). */
        float time;
        /** The player's looks, for drawing them: every "look.*" variable is in g.var(); this is the game. */
        Game g;
    }
}
