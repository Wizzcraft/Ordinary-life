package com.wizzcraft.ima.core;

/**
 * The only drawing surface the game core knows about. All art is polygons drawn through this
 * interface, in logical units (screen width is always {@link Game#W}).
 * Colors are 0xAARRGGBB ints.
 */
public interface Gfx {
    int LEFT = 0, CENTER = 1, RIGHT = 2;

    void fillRect(float x, float y, float w, float h, int argb);

    void fillRoundRect(float x, float y, float w, float h, float r, int argb);

    void strokeRoundRect(float x, float y, float w, float h, float r, float strokeWidth, int argb);

    /** xy = x0,y0,x1,y1,... a closed polygon. */
    void fillPoly(float[] xy, int argb);

    void fillCircle(float cx, float cy, float r, int argb);

    void line(float x1, float y1, float x2, float y2, float strokeWidth, int argb);

    /** Draws one line of text. x is the anchor for {@code align}, y is the baseline. */
    void text(String s, float x, float baselineY, float size, int argb, int align);

    float textWidth(String s, float size);
}
