package com.wizzcraft.ima.core;

/**
 * A drawing surface that draws nothing: it only counts what a frame asks for and estimates text
 * widths. The long test runs use it so that every screen's draw code is exercised (buttons are
 * rebuilt in draw) without paying for Java2D text layout hundreds of thousands of times.
 */
final class CountGfx implements Gfx {
    /** Primitives drawn since the last reset(), and the most one frame ever asked for. */
    int prims, maxPrims;
    /** The running shift; a frame must hand the surface back unshifted. */
    float dx, dy;

    void reset() {
        maxPrims = Math.max(maxPrims, prims);
        prims = 0;
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        prims++;
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float r, int argb) {
        prims++;
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float r, float strokeWidth, int argb) {
        prims++;
    }

    @Override
    public void fillPoly(float[] xy, int argb) {
        if (xy.length < 6 || xy.length % 2 != 0) throw new IllegalArgumentException("polygon with " + xy.length + " numbers");
        prims++;
    }

    @Override
    public void fillCircle(float cx, float cy, float r, int argb) {
        prims++;
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float strokeWidth, int argb) {
        prims++;
    }

    @Override
    public void text(String s, float x, float baselineY, float size, int argb, int align) {
        if (s == null) throw new IllegalArgumentException("null text");
        prims++;
    }

    /** Close to DejaVu Sans for Hebrew: a little over half an em per character. */
    @Override
    public float textWidth(String s, float size) {
        return s.length() * size * 0.56f;
    }

    @Override
    public void translate(float ddx, float ddy) {
        dx += ddx;
        dy += ddy;
    }
}
