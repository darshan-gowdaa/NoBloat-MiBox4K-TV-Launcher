package com.nobloat.launcher.reorder;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nobloat.launcher.AppInfo;
import com.nobloat.launcher.AppPreferencesManager;
import com.nobloat.launcher.R;
import com.nobloat.launcher.util.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class TileReorderManager {

    public interface ReorderListener {
        void onLinkDpadNavigation();
    }

    private final Activity activity;
    private final GridLayout gridDock;
    private final AppPreferencesManager appPrefsManager;
    private final TextView tvFocusedAppTitle;
    private final PathInterpolator interpolator;
    private final ReorderListener reorderListener;

    private boolean isReorderingMode = false;
    private AppInfo reorderingApp = null;
    private View reorderingView = null;
    private ObjectAnimator reorderPulseAnimator = null;

    public TileReorderManager(Activity activity,
                              GridLayout gridDock,
                              AppPreferencesManager appPrefsManager,
                              TextView tvFocusedAppTitle,
                              PathInterpolator interpolator,
                              ReorderListener reorderListener) {
        this.activity = activity;
        this.gridDock = gridDock;
        this.appPrefsManager = appPrefsManager;
        this.tvFocusedAppTitle = tvFocusedAppTitle;
        this.interpolator = interpolator;
        this.reorderListener = reorderListener;
    }

    public boolean isReordering() {
        return isReorderingMode;
    }

    public AppInfo getReorderingApp() {
        return reorderingApp;
    }

    public View getReorderingView() {
        return reorderingView;
    }

    public void startReordering(AppInfo app, View tile) {
        if (app == null) return;
        this.reorderingApp = app;
        this.isReorderingMode = true;
        this.reorderingView = tile;

        if (reorderingView != null) {
            reorderingView.requestFocus();
            reorderingView.animate()
                    .scaleX(1.18f)
                    .scaleY(1.18f)
                    .translationZ(UIUtils.dpToPx(activity, 20))
                    .setDuration(180)
                    .setInterpolator(interpolator)
                    .start();

            PropertyValuesHolder sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.15f, 1.20f);
            PropertyValuesHolder sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.15f, 1.20f);
            reorderPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(reorderingView, sx, sy);
            reorderPulseAnimator.setDuration(450);
            reorderPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            reorderPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            reorderPulseAnimator.start();
        }

        if (tvFocusedAppTitle != null) {
            tvFocusedAppTitle.setText(app.label);
        }
        Toast.makeText(activity, "Move with D-Pad • Press OK to place", Toast.LENGTH_SHORT).show();
    }

    public void moveTile(int direction) {
        if (!isReorderingMode || reorderingView == null || gridDock == null) return;
        int curr = gridDock.indexOfChild(reorderingView);
        if (curr < 0) return;
        int target = curr + direction;
        if (target < 0 || target >= gridDock.getChildCount()) {
            reorderingView.animate()
                    .translationX(direction * UIUtils.dpToPx(activity, 8))
                    .setDuration(70)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (reorderingView != null) {
                                reorderingView.animate().translationX(0f).setDuration(90).start();
                            }
                        }
                    })
                    .start();
            return;
        }

        gridDock.removeView(reorderingView);
        gridDock.addView(reorderingView, target);
        gridDock.requestLayout();
        reorderingView.requestFocus();

        try {
            reorderingView.playSoundEffect(direction < 0 ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
        } catch (Throwable ignored) {}

        if (reorderListener != null) {
            reorderListener.onLinkDpadNavigation();
        }
    }

    public void stopReordering() {
        if (!isReorderingMode) return;
        isReorderingMode = false;

        if (reorderPulseAnimator != null) {
            reorderPulseAnimator.cancel();
            reorderPulseAnimator = null;
        }

        if (reorderingView != null) {
            reorderingView.animate()
                    .scaleX(1.10f)
                    .scaleY(1.10f)
                    .translationZ(UIUtils.dpToPx(activity, 14))
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(160)
                    .setInterpolator(interpolator)
                    .start();
            reorderingView.requestFocus();
        }

        if (gridDock != null && appPrefsManager != null) {
            List<String> newFavs = new ArrayList<String>();
            for (int i = 0; i < gridDock.getChildCount(); i++) {
                View child = gridDock.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof AppInfo) {
                    newFavs.add(((AppInfo) tag).packageName);
                }
            }
            if (!newFavs.isEmpty()) {
                appPrefsManager.saveFavorites(newFavs);
            }
        }
    }

    public boolean handleKeyEvent(KeyEvent event, int columns) {
        if (!isReorderingMode) return false;

        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                moveTile(-1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                moveTile(1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                moveTile(-columns);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                moveTile(columns);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    || keyCode == KeyEvent.KEYCODE_BACK) {
                stopReordering();
                return true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    || keyCode == KeyEvent.KEYCODE_BACK) {
                return true;
            }
        }

        return true;
    }
}
