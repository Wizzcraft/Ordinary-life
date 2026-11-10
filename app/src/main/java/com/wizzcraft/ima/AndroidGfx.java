package com.wizzcraft.ima;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wizzcraft.ima.core.Gfx;

/** Gfx implementation on top of android.graphics. Reuses Paint/Path/RectF; no per-call allocation. */
public final class AndroidGfx implements Gfx {
    private Canvas canvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public AndroidGfx() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void begin(Canvas c) {
        canvas = c;
    }

    private void fill(int argb) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(argb);
    }

    private void stroke(float w, int argb) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);
        paint.setColor(argb);
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        fill(argb);
        canvas.drawRect(x, y, x + w, y + h, paint);
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float r, int argb) {
        fill(argb);
        rect.set(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, r, r, paint);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float r, float strokeWidth, int argb) {
        stroke(strokeWidth, argb);
        rect.set(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, r, r, paint);
    }

    @Override
    public void fillPoly(float[] xy, int argb) {
        int n = xy.length / 2;
        if (n < 3) return;
        fill(argb);
        path.rewind();
        path.moveTo(xy[0], xy[1]);
        for (int i = 1; i < n; i++) path.lineTo(xy[2 * i], xy[2 * i + 1]);
        path.close();
        canvas.drawPath(path, paint);
    }

    @Override
    public void fillCircle(float cx, float cy, float r, int argb) {
        fill(argb);
        canvas.drawCircle(cx, cy, r, paint);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float strokeWidth, int argb) {
        stroke(strokeWidth, argb);
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    @Override
    public void text(String s, float x, float baselineY, float size, int argb, int align) {
        if (s == null || s.isEmpty()) return;
        fill(argb);
        paint.setTextSize(size);
        paint.setTextAlign(align == CENTER ? Paint.Align.CENTER
                : align == RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT);
        canvas.drawText(s, x, baselineY, paint);
    }

    @Override
    public float textWidth(String s, float size) {
        if (s == null || s.isEmpty()) return 0f;
        paint.setTextSize(size);
        return paint.measureText(s);
    }
}
