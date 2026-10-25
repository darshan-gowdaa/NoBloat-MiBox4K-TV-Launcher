package com.nobloat.launcher;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RecentAppsManager {

    private static final String PREF_NAME = "recent_apps_pref";
    private static final String KEY_RECENTS = "recents_list";
    private static final String KEY_CLEARED_TIMESTAMP = "cleared_timestamp";
    private static final int MAX_RECENTS = 10;

    private final Context context;
    private final SharedPreferences prefs;

    public RecentAppsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void recordAppLaunched(String packageName) {
        if (packageName == null || packageName.isEmpty() || packageName.equals(context.getPackageName())) {
            return;
        }
        List<String> list = getSavedRecentPackages();
        list.remove(packageName);
        list.add(0, packageName);
        while (list.size() > MAX_RECENTS) {
            list.remove(list.size() - 1);
        }
        saveRecentPackages(list);
    }

    public void clearAll() {
        prefs.edit()
                .putLong(KEY_CLEARED_TIMESTAMP, System.currentTimeMillis())
                .remove(KEY_RECENTS)
                .apply();
    }

    public void removePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        List<String> list = getSavedRecentPackages();
        if (list.remove(packageName)) {
            saveRecentPackages(list);
        }
    }

    public List<AppInfo> getRecentApps(Map<String, AppInfo> allAppsMap) {
        long clearedTimestamp = prefs.getLong(KEY_CLEARED_TIMESTAMP, 0L);
        long now = System.currentTimeMillis();
        List<String> recentPkgs = new ArrayList<String>();

        // 1. Try UsageStatsManager first (only for apps launched AFTER the clear)
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long queryStart = Math.max(clearedTimestamp, now - (1000L * 60 * 60 * 24)); // max 24h, after cleared
                if (queryStart < now) {
                    List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, queryStart, now);
                    if (stats != null && !stats.isEmpty()) {
                        Collections.sort(stats, new Comparator<UsageStats>() {
                            @Override
                            public int compare(UsageStats o1, UsageStats o2) {
                                return Long.compare(o2.getLastTimeUsed(), o1.getLastTimeUsed());
                            }
                        });

                        for (UsageStats us : stats) {
                            String pkg = us.getPackageName();
                            if (pkg != null && !pkg.equals(context.getPackageName()) && allAppsMap.containsKey(pkg)) {
                                if (us.getLastTimeUsed() > clearedTimestamp) {
                                    if (!recentPkgs.contains(pkg)) {
                                        recentPkgs.add(pkg);
                                        if (recentPkgs.size() >= MAX_RECENTS) break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. If UsageStats returned empty, fall back to locally tracked launches
        if (recentPkgs.isEmpty()) {
            recentPkgs = getSavedRecentPackages();
        }

        // 3. Resolve AppInfo
        List<AppInfo> result = new ArrayList<AppInfo>();
        for (String pkg : recentPkgs) {
            if (allAppsMap.containsKey(pkg)) {
                result.add(allAppsMap.get(pkg));
            }
        }

        return result;
    }

    private List<String> getSavedRecentPackages() {
        String raw = prefs.getString(KEY_RECENTS, "");
        List<String> list = new ArrayList<String>();
        if (!raw.isEmpty()) {
            String[] parts = raw.split(",");
            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty() && !list.contains(p)) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    private void saveRecentPackages(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        prefs.edit().putString(KEY_RECENTS, sb.toString()).apply();
    }
}
