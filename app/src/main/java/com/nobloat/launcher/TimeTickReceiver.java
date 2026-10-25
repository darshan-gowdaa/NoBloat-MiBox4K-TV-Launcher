package com.nobloat.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimeTickReceiver extends BroadcastReceiver {
    private final MainActivity activity;

    public TimeTickReceiver(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (activity != null) {
            try {
                activity.updateTimeAndGreeting();
            } catch (Throwable ignored) {}
            try {
                activity.updateWifiSsid();
            } catch (Throwable ignored) {}
        }
    }
}
