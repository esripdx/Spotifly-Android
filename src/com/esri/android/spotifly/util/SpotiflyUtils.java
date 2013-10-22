package com.esri.android.spotifly.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

final public class SpotiflyUtils {
    private static final String PREF_FLIGHT_ID = "flight_id";
    private static final String PREF_XMIN = "x_min";
    private static final String PREF_XMAX = "x_max";
    private static final String PREF_YMIN = "y_min";
    private static final String PREF_YMAX = "y_max";

    private SpotiflyUtils() {}

    public static long getFlightId(Context context) {
        return getLongPreference(context, PREF_FLIGHT_ID);
    }

    public static boolean setFlightId(Context context, long value) {
        return setLongPreference(context, PREF_FLIGHT_ID, value);
    }

    public static float getXMin(Context context) {
        return getFloatPreference(context, PREF_XMIN);
    }

    public static boolean setXMin(Context context, float xmin) {
        return setFloatPreference(context, PREF_XMIN, xmin);
    }

    public static float getXMax(Context context) {
        return getFloatPreference(context, PREF_XMAX);
    }

    public static boolean setXMax(Context context, float xmax) {
        return setFloatPreference(context, PREF_XMAX, xmax);
    }

    public static float getYMin(Context context) {
        return getFloatPreference(context, PREF_YMIN);
    }

    public static boolean setYMin(Context context, float ymin) {
        return setFloatPreference(context, PREF_YMIN, ymin);
    }

    public static float getYMax(Context context) {
        return getFloatPreference(context, PREF_YMAX);
    }

    public static boolean setYMax(Context context, float ymax) {
        return setFloatPreference(context, PREF_YMAX, ymax);
    }

    private static String getStringPreference(Context context, String preferenceKey) {
        String value = null;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences != null) {
            value = preferences.getString(preferenceKey, null);
        }
        return value;
    }

    private static boolean setStringPreference(Context context, String key, String value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences != null && !TextUtils.isEmpty(key)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(key, value);
            return editor.commit();
        }
        return false;
    }

    private static float getFloatPreference(Context context, String key) {
        float value = -1;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences != null) {
            value = preferences.getFloat(key, Float.MIN_VALUE);
        }
        return value;
    }

    private static boolean setFloatPreference(Context context, String key,
                                             float value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences != null) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putFloat(key, value);
            return editor.commit();
        }
        return false;
    }

    private static long getLongPreference(Context context, String key) {
        long value = -1;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences != null) {
            value = preferences.getLong(key, -1);
        }
        return value;
    }

    private static boolean setLongPreference(Context context, String key,
                                             long value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences != null) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putLong(key, value);
            return editor.commit();
        }
        return false;
    }
}
