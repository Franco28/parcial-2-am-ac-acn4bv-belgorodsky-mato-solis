package com.lms.hidratateapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (HydrationPreferences.remindersEnabled(context)) {
            ReminderNotifier.show(context);
        }
    }
}
