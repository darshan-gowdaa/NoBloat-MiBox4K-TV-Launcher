package com.nobloat.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppPreferencesManager {

    private static final String PREF_NAME = "launcher_app_prefs";
    private static final String KEY_FAVORITES = "user_favorites";
    private static final String KEY_CUSTOM_ORDER = "user_custom_order";

    private final SharedPreferences prefs;

    public AppPreferencesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<String> getFavorites(String[] defaultDock) {
        String raw = prefs.getString(KEY_FAVORITES, null);
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<String>(Arrays.asList(defaultDock));
        }
        List<String> list = new ArrayList<String>();
        String[] parts = raw.split(",");
        for (String p : parts) {
            p = p.trim();
            if (!p.isEmpty() && !list.contains(p)) {
                list.add(p);
            }
        }
        return list;
    }

    public void saveFavorites(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        prefs.edit().putString(KEY_FAVORITES, sb.toString()).apply();
    }

    public boolean moveInFavorites(String pkg, int direction, String[] defaultDock) {
        List<String> favs = getFavorites(defaultDock);
        int idx = favs.indexOf(pkg);
        if (idx < 0) return false;
        int newIdx = idx + direction;
        if (newIdx < 0 || newIdx >= favs.size()) return false;
        favs.remove(idx);
        favs.add(newIdx, pkg);
        saveFavorites(favs);
        return true;
    }

    public void pinToFavorites(String pkg, String[] defaultDock) {
        List<String> favs = getFavorites(defaultDock);
        if (!favs.contains(pkg)) {
            favs.add(pkg);
            saveFavorites(favs);
        }
    }

    public void unpinFromFavorites(String pkg, String[] defaultDock) {
        List<String> favs = getFavorites(defaultDock);
        if (favs.remove(pkg)) {
            saveFavorites(favs);
        }
    }
}
