package com.lms.hidratateapp;

import android.content.Context;
import android.content.SharedPreferences;

public final class HydrationPreferences {
    private static final String PREF_NAME = "hydration_preferences";
    private static final String KEY_GOAL_ML = "daily_goal_ml";
    private static final int DEFAULT_GOAL_ML = 2000;

    private HydrationPreferences() {}

    public static int getDailyGoal(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int goal = prefs.getInt(KEY_GOAL_ML, DEFAULT_GOAL_ML);
        return goal >= 500 && goal <= 10000 && goal % 250 == 0 ? goal : DEFAULT_GOAL_ML;
    }

    public static void saveDailyGoal(Context context, int value) {
        if (value < 500 || value > 10000 || value % 250 != 0) {
            throw new IllegalArgumentException("Meta diaria inválida");
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_GOAL_ML, value).apply();
    }
}
