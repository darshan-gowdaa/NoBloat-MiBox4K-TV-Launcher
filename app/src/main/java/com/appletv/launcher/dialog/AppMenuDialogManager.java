package com.appletv.launcher.dialog;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.appletv.launcher.AppInfo;
import com.appletv.launcher.AppPreferencesManager;
import com.appletv.launcher.R;
import com.appletv.launcher.RecentAppsManager;
import com.appletv.launcher.util.UIUtils;

public class AppMenuDialogManager {

    public interface AppMenuActionListener {
        void onLaunchApp(AppInfo app);
        void onStartReordering(AppInfo app);
        void onFavoritesChanged(String changedPkg);
        View getFallbackFocusView();
    }

    private final Activity activity;
    private final AppPreferencesManager appPrefsManager;
    private final RecentAppsManager recentAppsManager;
    private final FrameLayout layoutAppMenuScrim;
    private final LinearLayout layoutAppMenuCard;
    private final FrameLayout iconFrame;
    private final ImageView menuAppIcon;
    private final TextView menuAppTitle;
    private final TextView menuAppPackage;
    private final TextView btnMenuOpen;
    private final TextView btnMenuMove;
    private final TextView btnMenuTogglePin;
    private final TextView btnMenuForceStop;
    private final TextView btnMenuUninstall;
    private final TextView btnMenuClose;
    private final android.view.animation.PathInterpolator interpolator;
    private final AppMenuActionListener actionListener;

    private AppInfo selectedMenuApp;
    private boolean selectedMenuAppIsDock;
    private View lastAnchorView;
    private long appMenuOpenedTimestamp = 0;

    public AppMenuDialogManager(Activity activity,
                                AppPreferencesManager appPrefsManager,
                                RecentAppsManager recentAppsManager,
                                FrameLayout layoutAppMenuScrim,
                                LinearLayout layoutAppMenuCard,
                                FrameLayout iconFrame,
                                ImageView menuAppIcon,
                                TextView menuAppTitle,
                                TextView menuAppPackage,
                                TextView btnMenuOpen,
                                TextView btnMenuMove,
                                TextView btnMenuTogglePin,
                                TextView btnMenuForceStop,
                                TextView btnMenuUninstall,
                                TextView btnMenuClose,
                                android.view.animation.PathInterpolator interpolator,
                                AppMenuActionListener actionListener) {
        this.activity = activity;
        this.appPrefsManager = appPrefsManager;
        this.recentAppsManager = recentAppsManager;
        this.layoutAppMenuScrim = layoutAppMenuScrim;
        this.layoutAppMenuCard = layoutAppMenuCard;
        this.iconFrame = iconFrame;
        this.menuAppIcon = menuAppIcon;
        this.menuAppTitle = menuAppTitle;
        this.menuAppPackage = menuAppPackage;
        this.btnMenuOpen = btnMenuOpen;
        this.btnMenuMove = btnMenuMove;
        this.btnMenuTogglePin = btnMenuTogglePin;
        this.btnMenuForceStop = btnMenuForceStop;
        this.btnMenuUninstall = btnMenuUninstall;
        this.btnMenuClose = btnMenuClose;
        this.interpolator = interpolator;
        this.actionListener = actionListener;

        setupOutlineAndListeners();
    }

    private void setupOutlineAndListeners() {
        if (iconFrame != null) {
            iconFrame.setClipToOutline(true);
            iconFrame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), UIUtils.dpToPx(activity, 12));
                }
            });
        }

        btnMenuOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SystemClock.uptimeMillis() - appMenuOpenedTimestamp < 600) return;
                AppInfo app = selectedMenuApp;
                dismiss();
                if (app != null && actionListener != null) {
                    actionListener.onLaunchApp(app);
                }
            }
        });

        if (btnMenuMove != null) {
            btnMenuMove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppInfo app = selectedMenuApp;
                    dismiss();
                    if (app != null && actionListener != null) {
                        actionListener.onStartReordering(app);
                    }
                }
            });
        }

        btnMenuTogglePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedMenuApp == null) return;
                String pkg = selectedMenuApp.packageName;
                dismiss();
                String[] defaultDock = activity.getResources().getStringArray(R.array.dock_apps);
                if (selectedMenuAppIsDock) {
                    appPrefsManager.unpinFromFavorites(pkg, defaultDock);
                } else {
                    appPrefsManager.pinToFavorites(pkg, defaultDock);
                }
                if (actionListener != null) {
                    actionListener.onFavoritesChanged(pkg);
                }
            }
        });

        btnMenuForceStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedMenuApp == null) return;
                String pkg = selectedMenuApp.packageName;
                String label = selectedMenuApp.label != null ? selectedMenuApp.label : pkg;
                dismiss();

                boolean rootOrShellStopped = false;
                try {
                    Process suProc = Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop " + pkg});
                    if (suProc.waitFor() == 0) rootOrShellStopped = true;
                } catch (Throwable ignored) {}

                if (!rootOrShellStopped) {
                    try {
                        Process shellProc = Runtime.getRuntime().exec(new String[]{"am", "force-stop", pkg});
                        if (shellProc.waitFor() == 0) rootOrShellStopped = true;
                    } catch (Throwable ignored) {}
                }

                try {
                    ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        am.killBackgroundProcesses(pkg);
                    }
                } catch (Throwable ignored) {}

                if (recentAppsManager != null) {
                    recentAppsManager.removePackage(pkg);
                }

                if (rootOrShellStopped) {
                    Toast.makeText(activity, "Force stopped " + label, Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + pkg));
                        activity.startActivity(intent);
                        Toast.makeText(activity, "Opening App Info to stop " + label + "...", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(activity, "Force stopped background processes for " + label, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnMenuUninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedMenuApp == null) return;
                String pkg = selectedMenuApp.packageName;
                String label = selectedMenuApp.label != null ? selectedMenuApp.label : pkg;
                dismiss();

                try {
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + pkg));
                    uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    activity.startActivity(uninstallIntent);
                } catch (Throwable e) {
                    try {
                        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                        uninstallIntent.setData(Uri.parse("package:" + pkg));
                        uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                        activity.startActivity(uninstallIntent);
                    } catch (Throwable t) {
                        Toast.makeText(activity, "Cannot uninstall " + label, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnMenuClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        layoutAppMenuScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    public boolean isOpen() {
        return layoutAppMenuScrim != null && layoutAppMenuScrim.getVisibility() == View.VISIBLE;
    }

    public long getOpenedTimestamp() {
        return appMenuOpenedTimestamp;
    }

    public void open(AppInfo app, boolean isDock, View anchor) {
        if (app == null) return;
        this.selectedMenuApp = app;
        this.selectedMenuAppIsDock = isDock;
        this.lastAnchorView = anchor;
        this.appMenuOpenedTimestamp = SystemClock.uptimeMillis();

        Drawable appIconDrawable = null;
        try {
            PackageManager pm = activity.getPackageManager();
            appIconDrawable = pm.getApplicationIcon(app.packageName);
        } catch (Throwable ignored) {}

        if (appIconDrawable == null) {
            appIconDrawable = (app.icon != null ? app.icon : app.banner);
        }

        if (appIconDrawable != null && menuAppIcon != null) {
            int w = appIconDrawable.getIntrinsicWidth();
            int h = appIconDrawable.getIntrinsicHeight();
            if (w > 0 && h > 0 && (float) w / (float) h > 1.3f) {
                menuAppIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                menuAppIcon.setPadding(0, 0, 0, 0);
            } else {
                menuAppIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = UIUtils.dpToPx(activity, 3);
                menuAppIcon.setPadding(pad, pad, pad, pad);
            }
            menuAppIcon.setImageDrawable(appIconDrawable);
        }
        menuAppTitle.setText(app.label);
        menuAppPackage.setText(app.packageName);

        // Clear all lateral focus escapes
        btnMenuOpen.setNextFocusLeftId(R.id.btn_menu_open);
        btnMenuOpen.setNextFocusRightId(R.id.btn_menu_open);
        btnMenuOpen.setNextFocusUpId(R.id.btn_menu_open);

        if (isDock) {
            btnMenuTogglePin.setText("Remove from Favorites");
            if (btnMenuMove != null) {
                btnMenuMove.setVisibility(View.VISIBLE);
                btnMenuMove.setNextFocusUpId(R.id.btn_menu_open);
                btnMenuMove.setNextFocusDownId(R.id.btn_menu_toggle_pin);
                btnMenuMove.setNextFocusLeftId(R.id.btn_menu_move);
                btnMenuMove.setNextFocusRightId(R.id.btn_menu_move);
            }
            btnMenuOpen.setNextFocusDownId(R.id.btn_menu_move);
            btnMenuTogglePin.setNextFocusUpId(R.id.btn_menu_move);
        } else {
            btnMenuTogglePin.setText("Pin to Favorites");
            if (btnMenuMove != null) btnMenuMove.setVisibility(View.GONE);
            btnMenuOpen.setNextFocusDownId(R.id.btn_menu_toggle_pin);
            btnMenuTogglePin.setNextFocusUpId(R.id.btn_menu_open);
        }

        btnMenuTogglePin.setNextFocusLeftId(R.id.btn_menu_toggle_pin);
        btnMenuTogglePin.setNextFocusRightId(R.id.btn_menu_toggle_pin);
        btnMenuTogglePin.setNextFocusDownId(R.id.btn_menu_force_stop);

        btnMenuForceStop.setNextFocusUpId(R.id.btn_menu_toggle_pin);
        btnMenuForceStop.setNextFocusLeftId(R.id.btn_menu_force_stop);
        btnMenuForceStop.setNextFocusRightId(R.id.btn_menu_force_stop);
        btnMenuForceStop.setNextFocusDownId(R.id.btn_menu_uninstall);

        btnMenuUninstall.setNextFocusUpId(R.id.btn_menu_force_stop);
        btnMenuUninstall.setNextFocusLeftId(R.id.btn_menu_uninstall);
        btnMenuUninstall.setNextFocusRightId(R.id.btn_menu_uninstall);
        btnMenuUninstall.setNextFocusDownId(R.id.btn_menu_close);

        btnMenuClose.setNextFocusUpId(R.id.btn_menu_uninstall);
        btnMenuClose.setNextFocusLeftId(R.id.btn_menu_close);
        btnMenuClose.setNextFocusRightId(R.id.btn_menu_close);
        btnMenuClose.setNextFocusDownId(R.id.btn_menu_close);

        View targetAnchor = anchor != null ? anchor : activity.getCurrentFocus();
        if (layoutAppMenuCard != null && targetAnchor != null) {
            int[] loc = new int[2];
            targetAnchor.getLocationInWindow(loc);
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            int menuWidth = UIUtils.dpToPx(activity, 270);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layoutAppMenuCard.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.width = menuWidth;

            int tileRight = loc[0] + targetAnchor.getWidth();
            int tileLeft = loc[0];
            int gap = UIUtils.dpToPx(activity, 12);

            if (tileRight + gap + menuWidth <= screenWidth - UIUtils.dpToPx(activity, 24)) {
                lp.leftMargin = tileRight + gap;
            } else if (tileLeft - gap - menuWidth >= UIUtils.dpToPx(activity, 24)) {
                lp.leftMargin = tileLeft - gap - menuWidth;
            } else {
                lp.leftMargin = Math.max(UIUtils.dpToPx(activity, 24), (screenWidth - menuWidth) / 2);
            }

            int targetTop = loc[1] - UIUtils.dpToPx(activity, 12);
            int maxTop = screenHeight - UIUtils.dpToPx(activity, 380);
            lp.topMargin = Math.max(UIUtils.dpToPx(activity, 48), Math.min(maxTop, targetTop));
            layoutAppMenuCard.setLayoutParams(lp);
        }

        layoutAppMenuScrim.setVisibility(View.VISIBLE);
        layoutAppMenuScrim.setAlpha(0f);
        layoutAppMenuScrim.animate().alpha(1f).setDuration(180).setInterpolator(interpolator).start();
        btnMenuOpen.requestFocus();
    }

    public void dismiss() {
        if (layoutAppMenuScrim != null && layoutAppMenuScrim.getVisibility() == View.VISIBLE) {
            layoutAppMenuScrim.setVisibility(View.GONE);
            if (lastAnchorView != null) {
                lastAnchorView.requestFocus();
            } else if (actionListener != null && actionListener.getFallbackFocusView() != null) {
                actionListener.getFallbackFocusView().requestFocus();
            }
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (!isOpen()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();
            return true;
        }

        View curr = activity.getCurrentFocus();
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (SystemClock.uptimeMillis() - appMenuOpenedTimestamp < 600) {
                return true;
            }
            if (curr != null) {
                curr.performClick();
                return true;
            }
        }

        boolean isMoveVisible = (btnMenuMove != null && btnMenuMove.getVisibility() == View.VISIBLE);

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (curr == btnMenuOpen) {
                return true;
            } else if (curr == btnMenuMove) {
                btnMenuOpen.requestFocus();
                return true;
            } else if (curr == btnMenuTogglePin) {
                if (isMoveVisible) {
                    btnMenuMove.requestFocus();
                } else {
                    btnMenuOpen.requestFocus();
                }
                return true;
            } else if (curr == btnMenuForceStop) {
                btnMenuTogglePin.requestFocus();
                return true;
            } else if (curr == btnMenuUninstall) {
                btnMenuForceStop.requestFocus();
                return true;
            } else if (curr == btnMenuClose) {
                btnMenuUninstall.requestFocus();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (curr == btnMenuOpen) {
                if (isMoveVisible) {
                    btnMenuMove.requestFocus();
                } else {
                    btnMenuTogglePin.requestFocus();
                }
                return true;
            } else if (curr == btnMenuMove) {
                btnMenuTogglePin.requestFocus();
                return true;
            } else if (curr == btnMenuTogglePin) {
                btnMenuForceStop.requestFocus();
                return true;
            } else if (curr == btnMenuForceStop) {
                btnMenuUninstall.requestFocus();
                return true;
            } else if (curr == btnMenuUninstall) {
                btnMenuClose.requestFocus();
                return true;
            } else if (curr == btnMenuClose) {
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return true;
        }

        return false;
    }
}
