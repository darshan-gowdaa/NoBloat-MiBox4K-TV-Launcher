package com.appletv.launcher.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

/**
 * MarqueeTextView: Non-continuous paused marquee.
 * 1. Measures text width vs available width.
 * 2. If text fits: stays stationary at scrollX = 0.
 * 3. If overflows:
 *    - Pauses 1500ms at start
 *    - Scrolls to end at steady 30dp/sec
 *    - Pauses 1500ms at end
 *    - Glides back to start
 *    - Repeats loop
 */
public class MarqueeTextView extends TextView {

    private static final long PAUSE_START_MS = 1500L;
    private static final long PAUSE_END_MS = 1500L;
    private static final float SPEED_DP_PER_SEC = 30f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ValueAnimator scrollAnimator;
    private Runnable scheduledTask;

    private boolean isMarqueeRunning = false;
    private int maxScrollDistance = 0;

    public MarqueeTextView(Context context) {
        this(context, null);
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setSingleLine(true);
        setEllipsize(null); // Do not truncate so scrollX reveals full line
        setHorizontallyScrolling(true);
    }

    private int calculateMaxScroll() {
        CharSequence text = getText();
        if (text == null || text.length() == 0) return 0;

        int availableWidth = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (availableWidth <= 0) return 0;

        float textWidth = getPaint().measureText(text.toString());
        int maxScroll = (int) Math.ceil(textWidth - availableWidth);
        return Math.max(0, maxScroll);
    }

    public void startMarquee() {
        stopMarquee();

        if (getVisibility() != View.VISIBLE) return;

        post(new Runnable() {
            @Override
            public void run() {
                maxScrollDistance = calculateMaxScroll();
                if (maxScrollDistance <= 0) {
                    scrollTo(0, 0);
                    return;
                }

                scrollTo(0, 0);
                isMarqueeRunning = true;
                scheduleScrollForward(PAUSE_START_MS);
            }
        });
    }

    private void scheduleScrollForward(long delayMs) {
        if (!isMarqueeRunning) return;
        cancelPendingTasks();

        scheduledTask = new Runnable() {
            @Override
            public void run() {
                if (!isMarqueeRunning) return;
                startScrollForward();
            }
        };
        mainHandler.postDelayed(scheduledTask, delayMs);
    }

    private void startScrollForward() {
        if (!isMarqueeRunning || maxScrollDistance <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float speedPxPerSec = SPEED_DP_PER_SEC * density;
        long duration = Math.max(500L, (long) ((maxScrollDistance / speedPxPerSec) * 1000f));

        scrollAnimator = ValueAnimator.ofInt(0, maxScrollDistance);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new LinearInterpolator());
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((int) animation.getAnimatedValue(), 0);
            }
        });
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isMarqueeRunning) {
                    scheduleScrollBackward(PAUSE_END_MS);
                }
            }
        });
        scrollAnimator.start();
    }

    private void scheduleScrollBackward(long delayMs) {
        if (!isMarqueeRunning) return;
        cancelPendingTasks();

        scheduledTask = new Runnable() {
            @Override
            public void run() {
                if (!isMarqueeRunning) return;
                startScrollBackward();
            }
        };
        mainHandler.postDelayed(scheduledTask, delayMs);
    }

    private void startScrollBackward() {
        if (!isMarqueeRunning || maxScrollDistance <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float speedPxPerSec = SPEED_DP_PER_SEC * density;
        long duration = Math.max(500L, (long) ((maxScrollDistance / speedPxPerSec) * 1000f));

        scrollAnimator = ValueAnimator.ofInt(maxScrollDistance, 0);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((int) animation.getAnimatedValue(), 0);
            }
        });
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isMarqueeRunning) {
                    scheduleScrollForward(PAUSE_START_MS);
                }
            }
        });
        scrollAnimator.start();
    }

    public void stopMarquee() {
        isMarqueeRunning = false;
        cancelPendingTasks();
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        scrollTo(0, 0);
    }

    private void cancelPendingTasks() {
        if (scheduledTask != null) {
            mainHandler.removeCallbacks(scheduledTask);
            scheduledTask = null;
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        if (isAttachedToWindow()) {
            startMarquee();
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (selected && !isMarqueeRunning) {
            startMarquee();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && isAttachedToWindow()) {
            startMarquee();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startMarquee();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopMarquee();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            startMarquee();
        } else {
            stopMarquee();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            startMarquee();
        } else {
            stopMarquee();
        }
    }
}
