package com.wizzcraft.ima;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.graphics.Insets;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        view = new GameView(this);

        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            view.game.version = v;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            view.game.version = "";
        }

        SharedPreferences sp = getSharedPreferences("save", MODE_PRIVATE);
        view.game.load(sp.getString("state", null));

        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setInsets(i.top, i.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pause();
        try {
            getSharedPreferences("save", MODE_PRIVATE).edit()
                    .putString("state", view.game.save()).apply();
        } catch (RuntimeException e) {
            // never crash on save
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!view.game.back()) {
            super.onBackPressed();
        }
    }
}
