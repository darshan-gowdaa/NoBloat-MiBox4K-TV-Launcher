package com.appletv.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;

public class SystemMetricsTracker {

    public static class MetricsData {
        public long availMemBytes;
        public long totalMemBytes;
        public boolean isLowMem;
        public long availDiskBytes;
        public long totalDiskBytes;

        public String getPillDisplay() {
            String freeStr = formatBytesGb(availMemBytes);
            String totalStr = formatBytesGb(totalMemBytes);
            return freeStr + " Free  |  " + totalStr;
        }

        private String formatBytesGb(long bytes) {
            if (bytes >= 1024L * 1024L * 1024L) {
                double gb = (double) bytes / (1024.0 * 1024.0 * 1024.0);
                return String.format(Locale.US, "%.1fG", gb);
            } else {
                long mb = bytes / (1024L * 1024L);
                return mb + "M";
            }
        }
    }

    public static MetricsData readMetrics(Context context) {
        MetricsData data = new MetricsData();

        // 1. MemoryInfo
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                data.availMemBytes = mi.availMem;
                data.totalMemBytes = mi.totalMem;
                data.isLowMem = mi.lowMemory;
            }
        } catch (Throwable ignored) {}

        // 2. StatFs for /data partition
        try {
            File dataDir = Environment.getDataDirectory();
            if (dataDir != null) {
                StatFs stat = new StatFs(dataDir.getAbsolutePath());
                data.availDiskBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                data.totalDiskBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
            }
        } catch (Throwable ignored) {}

        return data;
    }
}
