package com.nobloat.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String packageName;
    public final String activityName;
    public final String label;
    public Drawable banner = null;
    public Drawable icon = null;
    public boolean isBanner = false;
    public int dominantColor = 0xFF141922;
    public ColorExtractor.Palette palette = null;
    public volatile boolean isLoaded = false;
    public Intent launchIntent = null;

    public AppInfo(String packageName, String activityName, String label) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        if (packageName != null && activityName != null) {
            this.launchIntent = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(packageName, activityName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    public AppInfo(String packageName, String activityName, String label, Drawable banner, Drawable icon) {
        this(packageName, activityName, label);
        this.banner = banner;
        this.icon = icon;
        this.isLoaded = (banner != null || icon != null);
    }
}
