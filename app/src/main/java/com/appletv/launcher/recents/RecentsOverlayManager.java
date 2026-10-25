package com.appletv.launcher.recents;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.appletv.launcher.AppInfo;
import com.appletv.launcher.R;
import com.appletv.launcher.RecentAppsManager;
import com.appletv.launcher.dialog.BluetoothDialogManager.AmbientDriftListener;
import com.appletv.launcher.util.UIUtils;

import java.util.List;
import java.util.Map;

public class RecentsOverlayManager {

    public interface RecentsActionListener {
        void onLaunchApp(AppInfo app);
        void onUpdateSystemMetrics();
    }

    private final Activity activity;
    private final RecentAppsManager recentAppsManager;
    private final FrameLayout layoutRecentsOverlay;
    private final LinearLayout containerRecentsCarousel;
    private final View layoutRecentsEmptyState;
    private final View btnClearRecents;
    private final TextView tvClearRecentsLabel;
    private final ImageView iconClearSwing;
    private final PathInterpolator interpolator;
    private final AmbientDriftListener ambientDriftListener;
    private final RecentsActionListener actionListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Typeface outfitBold;
    private Typeface outfitMedium;
    private View lastFocusedView;

    public RecentsOverlayManager(Activity activity,
                                 RecentAppsManager recentAppsManager,
                                 FrameLayout layoutRecentsOverlay,
                                 LinearLayout containerRecentsCarousel,
                                 View layoutRecentsEmptyState,
                                 View btnClearRecents,
                                 TextView tvClearRecentsLabel,
                                 ImageView iconClearSwing,
                                 PathInterpolator interpolator,
                                 AmbientDriftListener ambientDriftListener,
                                 RecentsActionListener actionListener) {
        this.activity = activity;
        this.recentAppsManager = recentAppsManager;
        this.layoutRecentsOverlay = layoutRecentsOverlay;
        this.containerRecentsCarousel = containerRecentsCarousel;
        this.layoutRecentsEmptyState = layoutRecentsEmptyState;
        this.btnClearRecents = btnClearRecents;
        this.tvClearRecentsLabel = tvClearRecentsLabel;
        this.iconClearSwing = iconClearSwing;
        this.interpolator = interpolator;
        this.ambientDriftListener = ambientDriftListener;
        this.actionListener = actionListener;

        setupViews();
    }

    public void setTypefaces(Typeface bold, Typeface medium) {
        this.outfitBold = bold;
        this.outfitMedium = medium;
    }

    private void setupViews() {
        if (btnClearRecents != null) {
            btnClearRecents.setNextFocusDownId(R.id.btn_clear_recents);
            btnClearRecents.setNextFocusLeftId(R.id.btn_clear_recents);
            btnClearRecents.setNextFocusRightId(R.id.btn_clear_recents);
            btnClearRecents.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(160).start();
                        animateXIconSwing();
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start();
                    }
                }
            });
        }
    }

    public boolean isOpen() {
        return layoutRecentsOverlay != null && layoutRecentsOverlay.getVisibility() == View.VISIBLE;
    }

    public void toggle(View restoreFocus, Map<String, AppInfo> allAppsMap) {
        if (isOpen()) {
            hide();
            if (lastFocusedView != null) {
                lastFocusedView.requestFocus();
            }
        } else {
            show(restoreFocus, allAppsMap);
        }
    }

    public void show(View restoreFocus, final Map<String, AppInfo> allAppsMap) {
        this.lastFocusedView = restoreFocus;
        if (ambientDriftListener != null) {
            ambientDriftListener.onStopAmbientDrift();
        }
        populateCarousel(allAppsMap);

        if (btnClearRecents != null) {
            btnClearRecents.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearRecentsAndFreeRam(allAppsMap);
                }
            });
        }

        if (layoutRecentsOverlay != null) {
            layoutRecentsOverlay.setVisibility(View.VISIBLE);
            layoutRecentsOverlay.setAlpha(0f);
            layoutRecentsOverlay.animate()
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(interpolator)
                    .start();
        }

        if (containerRecentsCarousel != null && containerRecentsCarousel.getChildCount() > 0) {
            containerRecentsCarousel.getChildAt(0).requestFocus();
        } else if (btnClearRecents != null) {
            btnClearRecents.requestFocus();
        }
    }

    public void hide() {
        if (layoutRecentsOverlay != null && layoutRecentsOverlay.getVisibility() == View.VISIBLE) {
            layoutRecentsOverlay.setVisibility(View.GONE);
            if (ambientDriftListener != null) {
                ambientDriftListener.onStartAmbientDrift();
            }
        }
    }

    public void populateCarousel(Map<String, AppInfo> allAppsMap) {
        if (containerRecentsCarousel == null) return;
        containerRecentsCarousel.removeAllViews();
        List<AppInfo> recents = recentAppsManager.getRecentApps(allAppsMap);

        if (recents == null || recents.isEmpty()) {
            if (layoutRecentsEmptyState != null) {
                layoutRecentsEmptyState.setVisibility(View.VISIBLE);
                layoutRecentsEmptyState.setAlpha(1f);
            }
            if (btnClearRecents != null) {
                btnClearRecents.setVisibility(View.GONE);
            }
            return;
        }

        if (layoutRecentsEmptyState != null) {
            layoutRecentsEmptyState.setVisibility(View.GONE);
        }
        if (btnClearRecents != null) {
            btnClearRecents.setVisibility(View.VISIBLE);
            btnClearRecents.setAlpha(1f);
            if (tvClearRecentsLabel != null) tvClearRecentsLabel.setText("Clear All");
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < recents.size(); i++) {
            final AppInfo app = recents.get(i);
            View card = inflater.inflate(R.layout.item_recents_app, containerRecentsCarousel, false);
            card.setId(View.generateViewId());
            card.setTag(app);

            final FrameLayout cardFrame = (FrameLayout) card.findViewById(R.id.recents_card_frame);
            cardFrame.setClipToOutline(true);
            cardFrame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), UIUtils.dpToPx(activity, 18));
                }
            });

            ImageView imgBanner = card.findViewById(R.id.img_recents_banner);
            TextView tvTitle = card.findViewById(R.id.tv_recents_title);
            TextView tvBadge = card.findViewById(R.id.tv_recents_badge);

            if (outfitBold != null) {
                tvTitle.setTypeface(outfitBold);
            }
            if (outfitMedium != null) {
                tvBadge.setTypeface(outfitMedium);
            }

            imgBanner.setImageDrawable(app.banner != null ? app.banner : app.icon);
            tvTitle.setText(app.label);

            if (app.isBanner) {
                imgBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imgBanner.setPadding(0, 0, 0, 0);
            } else {
                imgBanner.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = UIUtils.dpToPx(activity, 18);
                imgBanner.setPadding(pad, pad, pad, pad);
                imgBanner.setBackgroundColor(0xFF191C20);
            }

            card.setNextFocusDownId(R.id.btn_clear_recents);
            card.setNextFocusUpId(card.getId());

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    if (actionListener != null) {
                        actionListener.onLaunchApp(app);
                    }
                }
            });

            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.animate()
                                .scaleX(1.06f)
                                .scaleY(1.06f)
                                .translationZ(10f)
                                .setDuration(180)
                                .setInterpolator(interpolator)
                                .start();
                    } else {
                        v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .translationZ(0f)
                                .setDuration(140)
                                .setInterpolator(interpolator)
                                .start();
                    }
                }
            });

            containerRecentsCarousel.addView(card);
        }

        if (btnClearRecents != null && containerRecentsCarousel.getChildCount() > 0) {
            btnClearRecents.setNextFocusUpId(containerRecentsCarousel.getChildAt(0).getId());
        }
    }

    public void clearRecentsAndFreeRam(Map<String, AppInfo> allAppsMap) {
        animateXIconSwing();

        if (containerRecentsCarousel != null) {
            int childCount = containerRecentsCarousel.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View card = containerRecentsCarousel.getChildAt(i);
                card.animate()
                        .translationY(-90f)
                        .alpha(0f)
                        .scaleX(0.7f)
                        .scaleY(0.7f)
                        .setDuration(240)
                        .setStartDelay(i * 30L)
                        .setInterpolator(interpolator)
                        .start();
            }
        }

        ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            List<AppInfo> recents = recentAppsManager.getRecentApps(allAppsMap);
            for (AppInfo app : recents) {
                try {
                    am.killBackgroundProcesses(app.packageName);
                } catch (Throwable ignored) {}
            }
            try {
                List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (ActivityManager.RunningAppProcessInfo pi : procs) {
                        if (pi.pkgList != null) {
                            for (String pkg : pi.pkgList) {
                                if (!pkg.equals(activity.getPackageName())) {
                                    am.killBackgroundProcesses(pkg);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        recentAppsManager.clearAll();
        System.gc();
        Runtime.getRuntime().runFinalization();

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (containerRecentsCarousel != null) {
                    containerRecentsCarousel.removeAllViews();
                }
                if (layoutRecentsEmptyState != null) {
                    layoutRecentsEmptyState.setVisibility(View.VISIBLE);
                    layoutRecentsEmptyState.setAlpha(0f);
                    layoutRecentsEmptyState.animate().alpha(1f).setDuration(220).start();
                }
                if (tvClearRecentsLabel != null) {
                    tvClearRecentsLabel.setText("RAM Cleared");
                }
                if (btnClearRecents != null) {
                    btnClearRecents.animate().alpha(0.5f).setDuration(280).start();
                }
                if (actionListener != null) {
                    actionListener.onUpdateSystemMetrics();
                }
            }
        }, 300);
    }

    public void animateXIconSwing() {
        if (iconClearSwing == null) return;
        iconClearSwing.setPivotX(UIUtils.dpToPx(activity, 9));
        iconClearSwing.setPivotY(UIUtils.dpToPx(activity, 9));

        Keyframe r0 = Keyframe.ofFloat(0f, 0f);
        Keyframe r1 = Keyframe.ofFloat(0.18f, -32f);
        Keyframe r2 = Keyframe.ofFloat(0.42f, 26f);
        Keyframe r3 = Keyframe.ofFloat(0.68f, -14f);
        Keyframe r4 = Keyframe.ofFloat(0.85f, 6f);
        Keyframe r5 = Keyframe.ofFloat(1.0f, 0f);
        PropertyValuesHolder pvhRotate = PropertyValuesHolder.ofKeyframe(View.ROTATION, r0, r1, r2, r3, r4, r5);

        Keyframe s0 = Keyframe.ofFloat(0f, 1.0f);
        Keyframe s1 = Keyframe.ofFloat(0.25f, 1.30f);
        Keyframe s2 = Keyframe.ofFloat(1.0f, 1.0f);
        PropertyValuesHolder pvhScaleX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, s0, s1, s2);
        PropertyValuesHolder pvhScaleY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, s0, s1, s2);

        ObjectAnimator swingAnim = ObjectAnimator.ofPropertyValuesHolder(iconClearSwing, pvhRotate, pvhScaleX, pvhScaleY);
        swingAnim.setDuration(550);
        swingAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        swingAnim.start();
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (!isOpen()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            hide();
            if (lastFocusedView != null) {
                lastFocusedView.requestFocus();
            }
            return true;
        }

        View curr = activity.getCurrentFocus();
        int recentsCount = (containerRecentsCarousel != null) ? containerRecentsCarousel.getChildCount() : 0;

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (curr == btnClearRecents) {
                if (recentsCount > 0) {
                    containerRecentsCarousel.getChildAt(0).requestFocus();
                    return true;
                }
            }
            return true; // Trap at top
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (curr != btnClearRecents && btnClearRecents != null) {
                btnClearRecents.requestFocus();
                return true;
            }
            return true; // Trap at bottom
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (curr == btnClearRecents) {
                return true; // Trap on button
            }
            if (recentsCount > 0 && curr == containerRecentsCarousel.getChildAt(0)) {
                return true; // Trap on first card
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (curr == btnClearRecents) {
                return true; // Trap on button
            }
            if (recentsCount > 0 && curr == containerRecentsCarousel.getChildAt(recentsCount - 1)) {
                return true; // Trap on last card
            }
        }

        return false;
    }
}
