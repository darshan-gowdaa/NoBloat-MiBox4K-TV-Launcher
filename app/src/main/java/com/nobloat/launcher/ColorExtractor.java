package com.nobloat.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class ColorExtractor {

    public static final int FALLBACK_DEFAULT = 0xFF00497D;    // Signature deep sapphire
    public static final int FALLBACK_MONO_DARK = 0xFF243447;  // Sleek midnight slate
    public static final int FALLBACK_MONO_LIGHT = 0xFF3A4D63; // Refined steel slate

    public static int extractDominantColor(Bitmap rawBitmap) {
        if (rawBitmap == null || rawBitmap.isRecycled()) {
            return FALLBACK_DEFAULT;
        }
        try {
            Bitmap bitmap = (rawBitmap.getWidth() == 32 && rawBitmap.getHeight() == 32)
                    ? rawBitmap
                    : Bitmap.createScaledBitmap(rawBitmap, 32, 32, false);

            int color = extractDominantColorInternal(bitmap, FALLBACK_DEFAULT);

            if (bitmap != rawBitmap) {
                bitmap.recycle();
            }
            return color;
        } catch (Throwable ignored) {}
        return FALLBACK_DEFAULT;
    }

    public static int extractDominantColor(Drawable drawable) {
        if (drawable == null) {
            return FALLBACK_DEFAULT;
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

            int color = extractDominantColorInternal(scaledBitmap, FALLBACK_DEFAULT);

            if (scaledBitmap != origBitmap) {
                scaledBitmap.recycle();
            }
            return color;
        } catch (Throwable ignored) {}

        return FALLBACK_DEFAULT;
    }

    private static int extractDominantColorInternal(Bitmap bitmap, int fallbackColor) {
        if (bitmap == null) return fallbackColor;

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        final int NUM_BINS = 12;
        float[] binWeights = new float[NUM_BINS];
        float[] binR = new float[NUM_BINS];
        float[] binG = new float[NUM_BINS];
        float[] binB = new float[NUM_BINS];

        long totalNeutralLum = 0;
        int neutralCount = 0;
        int totalOpaqueCount = 0;

        float[] hsv = new float[3];

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

            // Preserve deep tones (lum >= 10) like rich brown/navy if true chroma >= 12
            boolean isChromatic = (chroma >= 12 && lum >= 10 && lum <= 248);

            if (!isChromatic) {
                totalNeutralLum += lum;
                neutralCount++;
                continue;
            }

            Color.colorToHSV(pixel, hsv);
            float hue = hsv[0];
            int bin = ((int) (hue / 30f)) % NUM_BINS;

            // Saliency weighting: prefer midtones, allow dark earth/brown (lum 30-80)
            float lumFactor = (lum < 128)
                    ? (0.45f + 0.55f * (lum / 128f))
                    : (1.0f - 0.45f * ((lum - 128f) / 127f));

            float weight = (chroma / 255.0f) * lumFactor;

            binWeights[bin] += weight;
            binR[bin] += r * weight;
            binG[bin] += g * weight;
            binB[bin] += b * weight;
        }

        // Truly monochrome (white/black/gray icons like AnikenTV or Canta)
        if (totalOpaqueCount == 0 || neutralCount >= totalOpaqueCount * 0.96f) {
            int avgLum = (neutralCount > 0) ? (int) (totalNeutralLum / neutralCount) : 128;
            return (avgLum < 90) ? FALLBACK_MONO_DARK : FALLBACK_MONO_LIGHT;
        }

        // Dominant chromatic hue cluster
        int bestBin = -1;
        float maxWeight = 0;
        for (int k = 0; k < NUM_BINS; k++) {
            if (binWeights[k] > maxWeight) {
                maxWeight = binWeights[k];
                bestBin = k;
            }
        }

        if (bestBin >= 0 && maxWeight > 0.05f) {
            int r = Math.min(255, Math.max(0, Math.round(binR[bestBin] / binWeights[bestBin])));
            int g = Math.min(255, Math.max(0, Math.round(binG[bestBin] / binWeights[bestBin])));
            int b = Math.min(255, Math.max(0, Math.round(binB[bestBin] / binWeights[bestBin])));
            return Color.argb(255, r, g, b);
        }

        int avgLum = (neutralCount > 0) ? (int) (totalNeutralLum / neutralCount) : 128;
        return (avgLum < 90) ? FALLBACK_MONO_DARK : FALLBACK_MONO_LIGHT;
    }
}
