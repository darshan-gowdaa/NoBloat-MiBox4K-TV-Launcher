package com.appletv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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

public class AppleMusicGradientView extends View {

    private static final int NUM_ORBS = 4;
    private static final float GRADIENT_UNIT_RADIUS = 1000f;
    private static final float[] GAUSSIAN_STOPS = new float[]{0.0f, 0.30f, 0.60f, 0.85f, 1.0f};

    // Pre-allocated objects for zero-allocation 60fps loop
    private final Paint[] orbPaints = new Paint[NUM_ORBS];
    private final Matrix[] orbMatrices = new Matrix[NUM_ORBS];
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    private final int[][] stopsBuffer = new int[NUM_ORBS][5];

    // Orbital configuration constants [Orb 1..4]
    private final float[] baseCenterXFactor = new float[]{0.35f, 0.68f, 0.50f, 0.45f};
    private final float[] baseCenterYFactor = new float[]{0.42f, 0.40f, 0.68f, 0.32f};
    private final float[] baseRadiusPx = new float[]{720f, 640f, 580f, 480f};

    // Travel amplitudes (pixels on 1080p canvas)
    private final float[] ampX = new float[]{320f, 360f, 380f, 420f};
    private final float[] ampY = new float[]{200f, 240f, 180f, 220f};

    // Orbital frequencies (rad/s) - prime-tuned for non-repeating paths
    private final float[] freqX = new float[]{0.42f, 0.51f, 0.31f, 0.68f};
    private final float[] freqY = new float[]{0.35f, 0.44f, 0.62f, 0.73f};
    private final float[] phaseX = new float[]{0.00f, 1.20f, 2.40f, 3.10f};
    private final float[] phaseY = new float[]{1.57f, 0.80f, 1.00f, 1.90f};

    // Scale breathing frequencies & amplitudes
    private final float[] pulseFreq = new float[]{0.28f, 0.37f, 0.48f, 0.85f};
    private final float[] pulseAmp = new float[]{0.20f, 0.24f, 0.18f, 0.32f};

    // Colors (Apple OLED Neutral Dark Surface)
    private int currentBaseColor = 0xFF111215;
    private int targetBaseColor = 0xFF111215;
    private final int[] currentOrbColors = new int[NUM_ORBS];
    private final int[] targetOrbColors = new int[NUM_ORBS];
    private final int[] startOrbColors = new int[NUM_ORBS];
    private int startBaseColor;

    private ValueAnimator motionAnimator;
    private ValueAnimator colorAnimator;
    private long startTime;

    public AppleMusicGradientView(Context context) {
        this(context, null);
    }

    public AppleMusicGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        startTime = SystemClock.uptimeMillis();

        for (int i = 0; i < NUM_ORBS; i++) {
            orbPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            orbPaints[i].setDither(true);
            orbMatrices[i] = new Matrix();
        }

        setAppAccentColor(0xFF23262E, false);
    }

    public int getCurrentBaseColor() {
        return currentBaseColor;
    }

    public void setAppAccentColor(int focusColor, boolean animate) {
        float[] hsv = new float[3];
        Color.colorToHSV(focusColor, hsv);
        float rawH = hsv[0];
        float rawS = hsv[1];
        float rawV = hsv[2];

        // 1. Check monochrome: if saturation < 8%, ignore hsv[0]=0 (RED) bug!
        final boolean isMonochrome = (rawS < 0.08f);

        // 2. Check warm earth / brown / amber hues (~12° to 45°)
        final boolean isWarmEarth = (!isMonochrome && rawH >= 12.0f && rawH <= 45.0f);

        float targetH;
        float targetS;
        float targetV;

        if (isMonochrome) {
            // Elegant Apple TV tvOS deep sapphire slate
            targetH = 215.0f; // Cool sapphire slate
            targetS = 0.28f;  // Subtle, premium saturation
            targetV = 0.48f;  // Balanced cinematic luminance
        } else if (isWarmEarth) {
            // Preserve acoustic brown / terracotta / amber: DO NOT FORCE V >= 0.80 or S >= 0.65
            targetH = rawH;
            targetS = Math.max(0.38f, Math.min(0.68f, rawS * 0.95f));
            targetV = Math.max(0.36f, Math.min(0.62f, rawV * 1.05f));
        } else {
            // Standard chromatic accents (blues, greens, purples, reds)
            targetH = rawH;
            targetS = Math.max(0.48f, Math.min(0.85f, rawS * 1.10f));
            targetV = Math.max(0.58f, Math.min(0.85f, rawV * 1.08f));
        }

        // Multi-orb harmonic color orchestration:
        float hShift1 = isMonochrome ? 230.0f : ((targetH + 18.0f) % 360.0f);
        float hShift2 = isMonochrome ? 200.0f : ((targetH - 15.0f + 360.0f) % 360.0f);

        int newBase = Color.HSVToColor(new float[]{
                targetH,
                Math.min(targetS * 0.45f, 0.25f),
                isMonochrome ? 0.06f : 0.08f
        });

        int newOrb1 = Color.HSVToColor(new float[]{
                targetH,
                targetS,
                targetV
        });

        int newOrb2 = Color.HSVToColor(new float[]{
                hShift1,
                targetS * 0.85f,
                Math.min(0.82f, targetV * 0.92f)
        });

        int newOrb3 = Color.HSVToColor(new float[]{
                hShift2,
                Math.min(1.0f, targetS * 1.05f),
                Math.max(0.30f, targetV * 0.72f)
        });

        int newOrb4 = Color.HSVToColor(new float[]{
                targetH,
                targetS * 0.75f,
                Math.min(0.78f, targetV * 0.82f)
        });

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
                float f = (Float) anim.getAnimatedValue();
                currentBaseColor = (Integer) argbEvaluator.evaluate(f, startBaseColor, targetBaseColor);
                for (int i = 0; i < NUM_ORBS; i++) {
                    currentOrbColors[i] = (Integer) argbEvaluator.evaluate(f, startOrbColors[i], targetOrbColors[i]);
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

    private void rebuildShaders() {
        for (int i = 0; i < NUM_ORBS; i++) {
            int c = currentOrbColors[i];
            float topA = (i == 0) ? 0.60f : 0.40f;
            int[] stops = stopsBuffer[i];
            stops[0] = adjustAlpha(c, topA);
            stops[1] = adjustAlpha(c, topA * 0.65f);
            stops[2] = adjustAlpha(c, topA * 0.35f);
            stops[3] = adjustAlpha(c, topA * 0.10f);
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
        startMotion();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
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

        // 1. Draw base atmospheric plate
        canvas.drawColor(currentBaseColor);

        // 2. Compute elapsed time
        float t = (SystemClock.uptimeMillis() - startTime) / 1000f;

        // 3. Render 4 living orbs
        for (int i = 0; i < NUM_ORBS; i++) {
            float cx = w * baseCenterXFactor[i] + ampX[i] * (float) Math.sin(t * freqX[i] + phaseX[i]);
            float cy = h * baseCenterYFactor[i] + ampY[i] * (float) Math.cos(t * freqY[i] + phaseY[i]);
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
    }
}
