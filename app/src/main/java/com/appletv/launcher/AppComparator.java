package com.appletv.launcher;

import java.util.Comparator;

public class AppComparator implements Comparator<AppInfo> {
    @Override
    public int compare(AppInfo o1, AppInfo o2) {
        return o1.label.compareToIgnoreCase(o2.label);
    }
}
