package com.nobloat.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ColorExtractor {

    public static class Palette {
        public final int baseColor;
        public final int[] orbColors;
        public final int dominantColor;
        public final boolean isMonochrome;

        public Palette(int baseColor, int o1, int o2, int o3, int o4, int dominantColor, boolean isMonochrome) {
            this.baseColor = baseColor;
            this.orbColors = new int[]{o1, o2, o3, o4};
            this.dominantColor = dominantColor;
            this.isMonochrome = isMonochrome;
        }
    }

    public static final Palette DEFAULT_PALETTE = new Palette(
            0xFF101216,
            0xFF384858,
            0xFF1E2834,
            0xFF4A5A6C,
            0xFF2A3644,
            0xFF384858,
            false
    );

    public static final int FALLBACK_DEFAULT = 0xFF141922;

    public static Palette extractPalette(Bitmap rawBitmap) {
        if (rawBitmap == null || rawBitmap.isRecycled()) {
            return DEFAULT_PALETTE;
        }
        try {
            Bitmap bitmap = (rawBitmap.getWidth() == 32 && rawBitmap.getHeight() == 32)
                    ? rawBitmap
                    : Bitmap.createScaledBitmap(rawBitmap, 32, 32, false);

            Palette palette = extractPaletteInternal(bitmap);

            if (bitmap != rawBitmap) {
                bitmap.recycle();
            }
            return palette;
        } catch (Throwable ignored) {}
        return DEFAULT_PALETTE;
    }

    public static Palette extractPalette(Drawable drawable) {
        if (drawable == null) {
            return DEFAULT_PALETTE;
        }

        try {
            Bitmap origBitmap = null;
            if (drawable instanceof BitmapDrawable) {
                origBitmap = ((BitmapDrawable) drawable).getBitmap();
            }

            Bitmap scaledBitmap;
            if (origBitmap == null || origBitmap.isRecycled()) {
                scaledBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(scaledBitmap);
                drawable.setBounds(0, 0, 32, 32);
                drawable.draw(canvas);
            } else {
                scaledBitmap = Bitmap.createScaledBitmap(origBitmap, 32, 32, false);
            }

            Palette palette = extractPaletteInternal(scaledBitmap);

            if (scaledBitmap != origBitmap) {
                scaledBitmap.recycle();
            }
            return palette;
        } catch (Throwable ignored) {}

        return DEFAULT_PALETTE;
    }

    public static int extractDominantColor(Bitmap rawBitmap) {
        Palette p = extractPalette(rawBitmap);
        return (p != null) ? p.dominantColor : FALLBACK_DEFAULT;
    }

    public static int extractDominantColor(Drawable drawable) {
        Palette p = extractPalette(drawable);
        return (p != null) ? p.dominantColor : FALLBACK_DEFAULT;
    }

    private static class Cluster {
        int r, g, b;
        int count;

        Cluster(int r, int g, int b, int count) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.count = count;
        }
    }

    private static Palette extractPaletteInternal(Bitmap bitmap) {
        if (bitmap == null) return DEFAULT_PALETTE;

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int totalOpaqueCount = 0;
        int chromaticCount = 0;

        int minLum = 255;
        int maxLum = 0;

        final int NUM_BINS = 4096;
        int[] counts = new int[NUM_BINS];
        long[] sumR = new long[NUM_BINS];
        long[] sumG = new long[NUM_BINS];
        long[] sumB = new long[NUM_BINS];

        for (int i = 0; i < size; i++) {
            int pixel = pixels[i];
            int a = (pixel >>> 24) & 0xFF;
            if (a < 64) continue;
            totalOpaqueCount++;

            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            int chroma = max - min;
            int lum = (r * 299 + g * 587 + b * 114) / 1000;

            if (lum < minLum) minLum = lum;
            if (lum > maxLum) maxLum = lum;

            if (chroma > 18) {
                chromaticCount++;
            }

            int qr = (r >> 4);
            int qg = (g >> 4);
            int qb = (b >> 4);
            int bin = (qr << 8) | (qg << 4) | qb;

            counts[bin]++;
            sumR[bin] += r;
            sumG[bin] += g;
            sumB[bin] += b;
        }

        if (totalOpaqueCount == 0) {
            return DEFAULT_PALETTE;
        }

        boolean isMonochrome = (chromaticCount < totalOpaqueCount * 0.08f);

        if (isMonochrome) {
            // Pure monochrome: strictly black, white, and silver gray directly from image pixels.
            // NO BLUE!
            int darkLum = Math.max(8, Math.min(20, minLum));
            int lightLum = Math.max(180, Math.min(245, maxLum));
            int midLum = Math.max(70, Math.min(130, (minLum + maxLum) / 2));
            int deepLum = Math.max(28, Math.min(50, darkLum + 22));
            int starLum = Math.max(90, Math.min(160, (midLum + lightLum) / 2));

            int base = Color.rgb(darkLum, darkLum, darkLum);
            int orb1 = Color.rgb(lightLum, lightLum, lightLum);
            int orb2 = Color.rgb(midLum, midLum, midLum);
            int orb3 = Color.rgb(deepLum, deepLum, deepLum);
            int orb4 = Color.rgb(starLum, starLum, starLum);

            return new Palette(base, orb1, orb2, orb3, orb4, orb1, true);
        }

        // Chromatic: pick distinct major color clusters
        List<Cluster> activeClusters = new ArrayList<>();
        for (int b = 0; b < NUM_BINS; b++) {
            int cnt = counts[b];
            if (cnt > 0) {
                int avgR = (int) (sumR[b] / cnt);
                int avgG = (int) (sumG[b] / cnt);
                int avgB = (int) (sumB[b] / cnt);
                activeClusters.add(new Cluster(avgR, avgG, avgB, cnt));
            }
        }

        Collections.sort(activeClusters, new Comparator<Cluster>() {
            @Override
            public int compare(Cluster c1, Cluster c2) {
                return Integer.compare(c2.count, c1.count);
            }
        });

        List<Cluster> selected = new ArrayList<>();
        for (Cluster c : activeClusters) {
            boolean distinct = true;
            for (Cluster s : selected) {
                int dr = c.r - s.r;
                int dg = c.g - s.g;
                int db = c.b - s.b;
                if (Math.sqrt(dr * dr + dg * dg + db * db) < 36.0) {
                    distinct = false;
                    break;
                }
            }
            if (distinct) {
                selected.add(c);
                if (selected.size() >= 5) break;
            }
        }

        if (selected.isEmpty()) {
            return DEFAULT_PALETTE;
        }

        int baseR, baseG, baseB;
        Cluster darkest = null;
        for (Cluster c : selected) {
            int lum = (c.r * 299 + c.g * 587 + c.b * 114) / 1000;
            if (lum < 42) {
                darkest = c;
                break;
            }
        }

        if (darkest != null) {
            baseR = Math.max(8, Math.min(24, darkest.r));
            baseG = Math.max(8, Math.min(24, darkest.g));
            baseB = Math.max(8, Math.min(24, darkest.b));
        } else {
            Cluster dom = selected.get(0);
            baseR = Math.max(8, (int) (dom.r * 0.14f));
            baseG = Math.max(8, (int) (dom.g * 0.14f));
            baseB = Math.max(8, (int) (dom.b * 0.14f));
        }
        int baseColor = Color.rgb(baseR, baseG, baseB);

        int[] orbColors = new int[4];
        for (int i = 0; i < 4; i++) {
            Cluster c = (i < selected.size()) ? selected.get(i) : selected.get(0);
            orbColors[i] = Color.rgb(c.r, c.g, c.b);
        }

        int domColor = Color.rgb(selected.get(0).r, selected.get(0).g, selected.get(0).b);
        return new Palette(baseColor, orbColors[0], orbColors[1], orbColors[2], orbColors[3], domColor, false);
    }
}
