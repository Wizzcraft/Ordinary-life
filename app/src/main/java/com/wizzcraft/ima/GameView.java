package com.wizzcraft.ima;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import com.wizzcraft.ima.core.Game;

public final class GameView extends View {
    public final Game game = new Game();
    private final AndroidGfx gfx = new AndroidGfx();

    private int insetTop, insetBottom;
    private long lastNanos;
    private boolean running = true;
    private float scale = 1f;
    private float offsetX;

    public GameView(Context context) {
        super(context);
        setFocusable(true);
    }

    public void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        invalidate();
    }

    public void pause() {
        running = false;
        lastNanos = 0;
    }

    public void resume() {
        running = true;
        lastNanos = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        if (running) postInvalidateOnAnimation();
        if (w <= 0) return;
        long now = System.nanoTime();
        float dt = 0f;
        if (lastNanos != 0) {
            dt = (now - lastNanos) / 1_000_000_000f;
            if (dt > 0.1f) dt = 0.1f;
            if (dt < 0f) dt = 0f;
        }
        lastNanos = now;

        float usableH = Math.max(1, getHeight() - insetTop - insetBottom);
        // Fit the 360-wide layout to the width, but never squeeze it below the minimum height:
        // on short or wide screens scale by height instead and centre horizontally.
        scale = Math.min(w / Game.W, usableH / Game.MIN_H);
        offsetX = (w - Game.W * scale) / 2f;
        float logicalH = usableH / scale;

        game.update(dt, logicalH);

        canvas.drawColor(0xFF12161C);
        canvas.save();
        canvas.translate(offsetX, insetTop);
        canvas.scale(scale, scale);
        gfx.begin(canvas);
        game.draw(gfx);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float s = scale > 0 ? scale : 1f;
        float x = (e.getX() - offsetX) / s;
        float y = (e.getY() - insetTop) / s;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                game.down(x, y);
                break;
            case MotionEvent.ACTION_UP:
                game.up(x, y);
                break;
            default:
                break;
        }
        return true;
    }
}
