package com.nobloat.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AmbientGradientView extends View {

    private static final int NUM_ORBS = 4;
    private static final float GRADIENT_UNIT_RADIUS = 1000f;
    private static final float[] GAUSSIAN_STOPS = new float[]{0.0f, 0.30f, 0.60f, 0.85f, 1.0f};

    // shared clock across instances
    private static final long sSharedStartTime = SystemClock.uptimeMillis();
    private static final List<WeakReference<AmbientGradientView>> sInstances = new ArrayList<>();
    private static int sLastAccentColor = 0xFF1D2A3A;

    // pre-allocated objects for drawing loop
    private final Paint[] orbPaints = new Paint[NUM_ORBS];
    private final Matrix[] orbMatrices = new Matrix[NUM_ORBS];
    private final int[][] stopsBuffer = new int[NUM_ORBS][5];
    private final float[] hsvBuffer = new float[3];
    private final float[] colorHsvBuffer = new float[3];
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // orb setup
    private final float[] baseCenterXFactor = new float[]{0.35f, 0.68f, 0.50f, 0.45f};
    private final float[] baseCenterYFactor = new float[]{0.42f, 0.40f, 0.68f, 0.32f};
    private final float[] baseRadiusPx = new float[]{720f, 640f, 580f, 480f};

    // travel limits
    private final float[] ampX = new float[]{320f, 360f, 380f, 420f};
    private final float[] ampY = new float[]{200f, 240f, 180f, 220f};

    // speed frequencies
    private final float[] freqX = new float[]{0.72f, 0.88f, 0.52f, 1.12f};
    private final float[] freqY = new float[]{0.60f, 0.74f, 1.02f, 1.22f};
    private final float[] phaseX = new float[]{0.00f, 1.20f, 2.40f, 3.10f};
    private final float[] phaseY = new float[]{1.57f, 0.80f, 1.00f, 1.90f};

    // pulse speed
    private final float[] pulseFreq = new float[]{0.48f, 0.62f, 0.78f, 1.38f};
    private final float[] pulseAmp = new float[]{0.20f, 0.24f, 0.18f, 0.32f};

    // Colors
    private int currentBaseColor = 0xFF111722;
    private int targetBaseColor = 0xFF111722;
    private final int[] currentOrbColors = new int[NUM_ORBS];
    private final int[] targetOrbColors = new int[NUM_ORBS];
    private final int[] startOrbColors = new int[NUM_ORBS];
    private int startBaseColor;

    private boolean isHeaderMode = false;
    private ValueAnimator motionAnimator;
    private ValueAnimator colorAnimator;

    public AmbientGradientView(Context context) {
        this(context, null);
    }

    public AmbientGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadePaint.setDither(true);

        for (int i = 0; i < NUM_ORBS; i++) {
            orbPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            orbPaints[i].setDither(true);
            orbMatrices[i] = new Matrix();
        }

        registerInstance();
        setAppAccentColorInternal(sLastAccentColor, false, false);
    }

    public boolean isHeaderMode() {
        return isHeaderMode;
    }

    public void setHeaderMode(boolean headerMode) {
        this.isHeaderMode = headerMode;
        if (getWidth() > 0 && getHeight() > 0) {
            updateFadeShader(getWidth(), getHeight());
        }
        invalidate();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            updateFadeShader(w, h);
        }
    }

    private void updateFadeShader(int w, int h) {
        float fadeHeight = dpToPx(46);
        float fadeStartY = Math.max(0, h - fadeHeight);
        float stop = (h > 0) ? (fadeStartY / (float) h) : 0f;
        LinearGradient gradient = new LinearGradient(
                0, 0,
                0, h,
                new int[]{0xFF000000, 0xFF000000, 0x00000000},
                new float[]{0f, stop, 1.0f},
                Shader.TileMode.CLAMP
        );
        fadePaint.setShader(gradient);
    }

    private void registerInstance() {
        boolean found = false;
        for (int i = sInstances.size() - 1; i >= 0; i--) {
            AmbientGradientView v = sInstances.get(i).get();
            if (v == null) {
                sInstances.remove(i);
            } else if (v == this) {
                found = true;
            }
        }
        if (!found) {
            sInstances.add(new WeakReference<>(this));
        }
    }

    private void unregisterInstance() {
        for (int i = sInstances.size() - 1; i >= 0; i--) {
            AmbientGradientView v = sInstances.get(i).get();
            if (v == null || v == this) {
                sInstances.remove(i);
            }
        }
    }

    public int getCurrentBaseColor() {
        return currentBaseColor;
    }

    public void setAppAccentPalette(ColorExtractor.Palette palette, boolean animate) {
        if (palette == null) return;
        setAppAccentPaletteInternal(palette, animate, true);
    }

    private void setAppAccentPaletteInternal(ColorExtractor.Palette palette, boolean animate, boolean broadcast) {
        sLastAccentColor = palette.dominantColor;
        if (broadcast) {
            for (int i = sInstances.size() - 1; i >= 0; i--) {
                AmbientGradientView v = sInstances.get(i).get();
                if (v == null) {
                    sInstances.remove(i);
                } else if (v != this) {
                    v.setAppAccentPaletteInternal(palette, animate, false);
                }
            }
        }

        applyColors(palette.baseColor, palette.orbColors[0], palette.orbColors[1], palette.orbColors[2], palette.orbColors[3], animate);
    }

    public void setAppAccentColor(int focusColor, boolean animate) {
        setAppAccentColorInternal(focusColor, animate, true);
    }

    private void setAppAccentColorInternal(int focusColor, boolean animate, boolean broadcast) {
        sLastAccentColor = focusColor;
        if (broadcast) {
            for (int i = sInstances.size() - 1; i >= 0; i--) {
                AmbientGradientView v = sInstances.get(i).get();
                if (v == null) {
                    sInstances.remove(i);
                } else if (v != this) {
                    v.setAppAccentColorInternal(focusColor, animate, false);
                }
            }
        }

        Color.colorToHSV(focusColor, hsvBuffer);
        float rawH = hsvBuffer[0];
        float rawS = hsvBuffer[1];
        float rawV = hsvBuffer[2];

        int newBase;
        int newOrb1, newOrb2, newOrb3, newOrb4;

        if (rawS < 0.12f) {
            // Strictly neutral monochrome: NEVER inject blue!
            newBase = Color.rgb(10, 10, 10);
            newOrb1 = Color.rgb(225, 225, 225);
            newOrb2 = Color.rgb(55, 55, 55);
            newOrb3 = Color.rgb(115, 115, 115);
            newOrb4 = Color.rgb(25, 25, 25);
        } else {
            // Chromatic
            float targetH = rawH;
            float targetS = Math.max(0.65f, Math.min(0.95f, rawS * 1.20f));
            float targetV = Math.max(0.72f, Math.min(0.96f, rawV * 1.15f));

            float hShift1 = (targetH + 18.0f) % 360.0f;
            float hShift2 = (targetH - 15.0f + 360.0f) % 360.0f;

            colorHsvBuffer[0] = targetH;
            colorHsvBuffer[1] = Math.min(targetS * 0.50f, 0.35f);
            colorHsvBuffer[2] = 0.16f;
            newBase = Color.HSVToColor(colorHsvBuffer);

            colorHsvBuffer[0] = targetH;
            colorHsvBuffer[1] = targetS;
            colorHsvBuffer[2] = targetV;
            newOrb1 = Color.HSVToColor(colorHsvBuffer);

            colorHsvBuffer[0] = hShift1;
            colorHsvBuffer[1] = targetS * 0.85f;
            colorHsvBuffer[2] = Math.min(0.92f, targetV * 0.92f);
            newOrb2 = Color.HSVToColor(colorHsvBuffer);

            colorHsvBuffer[0] = hShift2;
            colorHsvBuffer[1] = Math.min(1.0f, targetS * 1.05f);
            colorHsvBuffer[2] = Math.max(0.35f, targetV * 0.75f);
            newOrb3 = Color.HSVToColor(colorHsvBuffer);

            colorHsvBuffer[0] = targetH;
            colorHsvBuffer[1] = targetS * 0.75f;
            colorHsvBuffer[2] = Math.min(0.88f, targetV * 0.85f);
            newOrb4 = Color.HSVToColor(colorHsvBuffer);
        }

        applyColors(newBase, newOrb1, newOrb2, newOrb3, newOrb4, animate);
    }

    private void applyColors(int newBase, int newOrb1, int newOrb2, int newOrb3, int newOrb4, boolean animate) {
        if (!animate) {
            currentBaseColor = newBase;
            currentOrbColors[0] = newOrb1;
            currentOrbColors[1] = newOrb2;
            currentOrbColors[2] = newOrb3;
            currentOrbColors[3] = newOrb4;
            rebuildShaders();
            invalidate();
            return;
        }

        if (colorAnimator != null && colorAnimator.isRunning()) {
            colorAnimator.cancel();
        }

        startBaseColor = currentBaseColor;
        targetBaseColor = newBase;
        for (int i = 0; i < NUM_ORBS; i++) {
            startOrbColors[i] = currentOrbColors[i];
        }
        targetOrbColors[0] = newOrb1;
        targetOrbColors[1] = newOrb2;
        targetOrbColors[2] = newOrb3;
        targetOrbColors[3] = newOrb4;

        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(550);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        final float[] lastRebuild = new float[]{0f};
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                float f = ((Number) anim.getAnimatedValue()).floatValue();
                currentBaseColor = interpolateColor(f, startBaseColor, targetBaseColor);
                for (int i = 0; i < NUM_ORBS; i++) {
                    currentOrbColors[i] = interpolateColor(f, startOrbColors[i], targetOrbColors[i]);
                }
                if (Math.abs(f - lastRebuild[0]) >= 0.12f || f >= 1.0f) {
                    lastRebuild[0] = f;
                    rebuildShaders();
                } else {
                    postInvalidateOnAnimation();
                }
            }
        });
        colorAnimator.start();
    }

    private static int interpolateColor(float fraction, int start, int end) {
        int startA = (start >>> 24);
        int startR = (start >> 16) & 0xff;
        int startG = (start >> 8) & 0xff;
        int startB = start & 0xff;

        int endA = (end >>> 24);
        int endR = (end >> 16) & 0xff;
        int endG = (end >> 8) & 0xff;
        int endB = end & 0xff;

        int a = (int) (startA + fraction * (endA - startA));
        int r = (int) (startR + fraction * (endR - startR));
        int g = (int) (startG + fraction * (endG - startG));
        int b = (int) (startB + fraction * (endB - startB));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void rebuildShaders() {
        for (int i = 0; i < NUM_ORBS; i++) {
            int c = currentOrbColors[i];
            float topA = (i == 0) ? 0.85f : 0.65f; // was 0.60f / 0.40f (boost intensity!)
            int[] stops = stopsBuffer[i];
            stops[0] = adjustAlpha(c, topA);
            stops[1] = adjustAlpha(c, topA * 0.75f);
            stops[2] = adjustAlpha(c, topA * 0.45f);
            stops[3] = adjustAlpha(c, topA * 0.18f);
            stops[4] = 0x00000000;
            RadialGradient shader = new RadialGradient(
                    0, 0, GRADIENT_UNIT_RADIUS,
                    stops, GAUSSIAN_STOPS, Shader.TileMode.CLAMP
            );
            orbPaints[i].setShader(shader);
        }
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerInstance();
        if (sLastAccentColor != 0) {
            setAppAccentColorInternal(sLastAccentColor, false, false);
        }
        startMotion();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterInstance();
        stopMotion();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startMotion();
        } else {
            stopMotion();
        }
    }

    public void startMotion() {
        startMotionInternal(true);
    }

    private void startMotionInternal(boolean broadcast) {
        if (broadcast) {
            for (int i = sInstances.size() - 1; i >= 0; i--) {
                AmbientGradientView v = sInstances.get(i).get();
                if (v != null && v != this) {
                    v.startMotionInternal(false);
                }
            }
        }
        if (motionAnimator != null && motionAnimator.isRunning()) return;
        motionAnimator = ValueAnimator.ofFloat(0f, 1f);
        motionAnimator.setDuration(1000);
        motionAnimator.setRepeatCount(ValueAnimator.INFINITE);
        motionAnimator.setInterpolator(new LinearInterpolator());
        motionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private long lastDrawTime = 0;
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                long now = SystemClock.uptimeMillis();
                if (now - lastDrawTime >= 33) {
                    lastDrawTime = now;
                    postInvalidate();
                }
            }
        });
        motionAnimator.start();
    }

    public void stopMotion() {
        stopMotionInternal(true);
    }

    private void stopMotionInternal(boolean broadcast) {
        if (broadcast) {
            for (int i = sInstances.size() - 1; i >= 0; i--) {
                AmbientGradientView v = sInstances.get(i).get();
                if (v != null && v != this) {
                    v.stopMotionInternal(false);
                }
            }
        }
        if (motionAnimator != null) {
            motionAnimator.cancel();
            motionAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        final boolean header = isHeaderMode;
        int saveCount = 0;
        if (header) {
            saveCount = canvas.saveLayer(0, 0, w, h, null);
        }

        // draw base background
        canvas.drawColor(currentBaseColor);

        // time delta
        float t = (SystemClock.uptimeMillis() - sSharedStartTime) / 1000f;

        // render orbs
        int calcH = header ? getResources().getDisplayMetrics().heightPixels : h;

        for (int i = 0; i < NUM_ORBS; i++) {
            float cx = w * baseCenterXFactor[i] + ampX[i] * (float) Math.sin(t * freqX[i] + phaseX[i]);
            float cy = calcH * baseCenterYFactor[i] + ampY[i] * (float) Math.cos(t * freqY[i] + phaseY[i]);
            float radius = baseRadiusPx[i] * (1.0f + pulseAmp[i] * (float) Math.sin(t * pulseFreq[i]));

            Matrix matrix = orbMatrices[i];
            matrix.reset();
            float scale = radius / GRADIENT_UNIT_RADIUS;
            matrix.postScale(scale, scale);
            matrix.postTranslate(cx, cy);

            Shader shader = orbPaints[i].getShader();
            if (shader != null) {
                shader.setLocalMatrix(matrix);
            }

            canvas.drawCircle(cx, cy, radius, orbPaints[i]);
        }

        // fade bottom edge in header mode
        if (header) {
            if (fadePaint.getShader() == null) {
                updateFadeShader(w, h);
            }
            canvas.drawRect(0, 0, w, h, fadePaint);
            canvas.restoreToCount(saveCount);
        }
    }
}
