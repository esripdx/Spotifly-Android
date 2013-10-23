package com.esri.android.spotifly.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import com.esri.android.spotifly.R;
import com.esri.android.spotifly.util.SpotiflyUtils;

public class LaunchActivity extends Activity {
    private static final int SPLASH_TIMEOUT = 2000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);
    }

    @Override
    public void onStart() {
        super.onStart();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                nextActivity();
            }
        }, SPLASH_TIMEOUT);
    }

    private void nextActivity() {
        Class nextClass;
        boolean gotFlightId = SpotiflyUtils.getFlightId(this) > 0;
        boolean gotExtent = SpotiflyUtils.getXMax(this) > Float.MIN_VALUE &&
                SpotiflyUtils.getXMin(this) > Float.MIN_VALUE &&
                SpotiflyUtils.getYMax(this) > Float.MIN_VALUE &&
                SpotiflyUtils.getYMin(this) > Float.MIN_VALUE;

        String action = null;
        if (gotFlightId && gotExtent) {
            nextClass = MapActivity.class;
        } else {
            nextClass = SetupActivity.class;
        }

        Intent intent = new Intent(this, nextClass);

        if (!TextUtils.isEmpty(action)) {
            intent.setAction(action);
        }

        startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
