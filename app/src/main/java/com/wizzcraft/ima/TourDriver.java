package com.wizzcraft.ima;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import com.wizzcraft.ima.core.Tour;

import java.util.Locale;

/**
 * Device lab only (docs/DEVICE_LAB.md). Started by the intent extra {@code tour}, e.g.
 * {@code adb shell am start -n com.wizzcraft.ima/.MainActivity --es tour smoke}; a launcher start
 * never carries it, so players never get here.
 *
 * Taps are real MotionEvents dispatched from the window's root view at the pixel where the game
 * drew the button, so they travel the same path a finger does: root view, hit test, GameView's
 * pixel-to-logical transform, Game.down/up.
 */
final class TourDriver implements Tour.Driver {
    static final String TAG = "IMA-TOUR";

    private final GameView view;
    private Tour tour;
    private float ox, oy, scale = 1f;
    private boolean reported;
    private final int[] loc = new int[2];

    private TourDriver(GameView view) {
        this.view = view;
    }

    /** Hook for MainActivity.onCreate: does nothing unless the intent asks for a tour. */
    static void attach(GameView view, Intent intent) {
        String name = intent == null ? null : intent.getStringExtra("tour");
        if (name == null || name.isEmpty()) return;
        TourDriver d = new TourDriver(view);
        d.tour = Tour.create(view.game, name, d);
        if (d.tour == null) {
            Log.w(TAG, "IMA-TOUR FAIL unknown tour '" + name + "'");
            return;
        }
        view.tour = d;
    }

    /** Hook for GameView.onDraw, after game.update and before game.draw. */
    void frame(float dt, float offsetX, float insetTop, float s) {
        ox = offsetX;
        oy = insetTop;
        scale = s;
        if (!reported) {
            reported = true;
            view.getLocationInWindow(loc);
            log(String.format(Locale.ROOT,
                    "IMA-TOUR shell: view %dx%d at (%d,%d) in window, root %dx%d, insetTop=%.0f offsetX=%.1f scale=%.3f",
                    view.getWidth(), view.getHeight(), loc[0], loc[1], view.getRootView().getWidth(),
                    view.getRootView().getHeight(), insetTop, offsetX, s));
        }
        tour.frame(dt);
    }

    @Override
    public void tap(float x, float y) {
        view.getLocationInWindow(loc);
        float px = loc[0] + ox + x * scale, py = loc[1] + oy + y * scale;
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0);
        view.getRootView().dispatchTouchEvent(e);
        e.recycle();
        e = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, px, py, 0);
        view.getRootView().dispatchTouchEvent(e);
        e.recycle();
        Log.d(TAG, String.format(Locale.ROOT, "IMA-TOUR   tap logical (%.0f,%.0f) -> window px (%.0f,%.0f)", x, y, px, py));
    }

    @Override
    public void log(String line) {
        Log.i(TAG, line);
    }
}
