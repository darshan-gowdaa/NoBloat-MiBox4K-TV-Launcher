package com.nobloat.launcher.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import com.nobloat.launcher.R;

public class EqualizerView extends LinearLayout {

    private static final int BAR_COUNT = 4;
    private final View[] bars = new View[BAR_COUNT];
    private final ObjectAnimator[] animators = new ObjectAnimator[BAR_COUNT];
    private boolean isPlaying = false;

    // Coprime periods to simulate organic dynamic equalizer rhythm
    private final long[] durations = new long[]{410L, 330L, 480L, 370L};
    private final float[] maxScales = new float[]{0.88f, 1.0f, 0.72f, 0.94f};
    private final float minScale = 0.22f;

    public EqualizerView(Context context) {
        this(context, null);
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EqualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);

        float density = context.getResources().getDisplayMetrics().density;
        int barWidth = Math.round(4.5f * density);
        int barHeight = Math.round(24f * density);
        int barMargin = Math.round(2.5f * density);

        for (int i = 0; i < BAR_COUNT; i++) {
            View bar = new View(context);
            LayoutParams lp = new LayoutParams(barWidth, barHeight);
            lp.leftMargin = barMargin;
            lp.rightMargin = barMargin;
            lp.gravity = Gravity.BOTTOM;
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(R.drawable.bg_equalizer_bar);
            bar.setScaleY(minScale);
            bars[i] = bar;
            addView(bar);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePivots();
        if (isPlaying) {
            startAnimators();
        }
    }

    private void updatePivots() {
        for (View bar : bars) {
            if (bar != null) {
                int h = bar.getHeight();
                bar.setPivotY(h > 0 ? h : bar.getMeasuredHeight());
                bar.setPivotX(bar.getWidth() / 2f);
            }
        }
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;

        if (playing) {
            startAnimators();
        } else {
            stopAnimators();
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    private void startAnimators() {
        updatePivots();
        AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();

        for (int i = 0; i < BAR_COUNT; i++) {
            if (animators[i] != null && animators[i].isRunning()) {
                animators[i].cancel();
            }

            View bar = bars[i];
            ObjectAnimator anim = ObjectAnimator.ofFloat(bar, "scaleY", minScale, maxScales[i]);
            anim.setDuration(durations[i]);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(interpolator);
            animators[i] = anim;
            anim.start();
        }
    }

    private void stopAnimators() {
        DecelerateInterpolator decelerator = new DecelerateInterpolator();
        for (int i = 0; i < BAR_COUNT; i++) {
            if (animators[i] != null) {
                animators[i].cancel();
                animators[i] = null;
            }

            View bar = bars[i];
            if (bar != null) {
                bar.animate()
                        .scaleY(minScale)
                        .setDuration(220)
                        .setInterpolator(decelerator)
                        .start();
            }
        }
    }

    public void setBarColor(int color) {
        for (View bar : bars) {
            if (bar != null && bar.getBackground() != null) {
                bar.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPlaying) {
            startAnimators();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i = 0; i < BAR_COUNT; i++) {
            if (animators[i] != null) {
                animators[i].cancel();
                animators[i] = null;
            }
        }
    }
}
