package com.nobloat.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public class SystemMetricsTracker {

    private static final ActivityManager.MemoryInfo MEMORY_INFO = new ActivityManager.MemoryInfo();
    private static final MetricsData CACHED_METRICS = new MetricsData();
    private static String cachedDataPath = null;
    private static StatFs cachedStatFs = null;

    public static class MetricsData {
        public long availMemBytes;
        public long totalMemBytes;
        public boolean isLowMem;
        public long availDiskBytes;
        public long totalDiskBytes;

        private String cachedDisplay = null;
        private long lastAvail = -1;
        private long lastTotal = -1;

        public String getPillDisplay() {
            if (cachedDisplay != null && availMemBytes == lastAvail && totalMemBytes == lastTotal) {
                return cachedDisplay;
            }
            StringBuilder sb = new StringBuilder(24);
            appendFormattedBytes(sb, availMemBytes);
            sb.append(" Free  |  ");
            appendFormattedBytes(sb, totalMemBytes);
            cachedDisplay = sb.toString();
            lastAvail = availMemBytes;
            lastTotal = totalMemBytes;
            return cachedDisplay;
        }

        private static void appendFormattedBytes(StringBuilder sb, long bytes) {
            final long GB = 1024L * 1024L * 1024L;
            if (bytes >= GB) {
                long tenths = ((bytes * 100L) / GB + 5L) / 10L;
                sb.append(tenths / 10L).append('.').append(tenths % 10L).append('G');
            } else {
                sb.append(bytes / (1024L * 1024L)).append('M');
            }
        }
    }

    public static synchronized MetricsData readMetrics(Context context) {
        MetricsData data = CACHED_METRICS;

        // 1. MemoryInfo (reused MEMORY_INFO to avoid GC churn)
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(MEMORY_INFO);
                data.availMemBytes = MEMORY_INFO.availMem;
                data.totalMemBytes = MEMORY_INFO.totalMem;
                data.isLowMem = MEMORY_INFO.lowMemory;
            }
        } catch (Throwable ignored) {}

        // 2. StatFs for /data partition (reused StatFs instance via restat)
        try {
            if (cachedDataPath == null) {
                File dataDir = Environment.getDataDirectory();
                if (dataDir != null) {
                    cachedDataPath = dataDir.getAbsolutePath();
                }
            }
            if (cachedDataPath != null) {
                if (cachedStatFs == null) {
                    cachedStatFs = new StatFs(cachedDataPath);
                } else {
                    cachedStatFs.restat(cachedDataPath);
                }
                data.availDiskBytes = cachedStatFs.getAvailableBlocksLong() * cachedStatFs.getBlockSizeLong();
                data.totalDiskBytes = cachedStatFs.getBlockCountLong() * cachedStatFs.getBlockSizeLong();
            }
        } catch (Throwable ignored) {}

        return data;
    }
}
