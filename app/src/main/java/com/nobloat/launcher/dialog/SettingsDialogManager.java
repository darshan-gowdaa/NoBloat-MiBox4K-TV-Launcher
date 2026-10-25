package com.nobloat.launcher.dialog;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nobloat.launcher.dialog.BluetoothDialogManager.AmbientDriftListener;

public class SettingsDialogManager {

    public interface AmbientToggleListener {
        boolean onToggleAmbientMotion();
        boolean isAmbientMotionActive();
    }

    private final Activity activity;
    private final FrameLayout layoutSettingsScrim;
    private final LinearLayout layoutSettingsDialog;
    private final View btnSettingsAnchor;
    private final TextView settingItemSystem;
    private final TextView settingItemNetwork;
    private final TextView settingItemBluetooth;
    private final TextView settingItemApps;
    private final TextView settingItemDevice;
    private final TextView settingItemAmbient;
    private final PathInterpolator interpolator;
    private final AmbientDriftListener ambientDriftListener;
    private final AmbientToggleListener ambientToggleListener;
    private final Runnable openBluetoothDialogFallback;

    public SettingsDialogManager(Activity activity,
                                 FrameLayout layoutSettingsScrim,
                                 LinearLayout layoutSettingsDialog,
                                 View btnSettingsAnchor,
                                 TextView settingItemSystem,
                                 TextView settingItemNetwork,
                                 TextView settingItemBluetooth,
                                 TextView settingItemApps,
                                 TextView settingItemDevice,
                                 TextView settingItemAmbient,
                                 PathInterpolator interpolator,
                                 AmbientDriftListener ambientDriftListener,
                                 AmbientToggleListener ambientToggleListener,
                                 Runnable openBluetoothDialogFallback) {
        this.activity = activity;
        this.layoutSettingsScrim = layoutSettingsScrim;
        this.layoutSettingsDialog = layoutSettingsDialog;
        this.btnSettingsAnchor = btnSettingsAnchor;
        this.settingItemSystem = settingItemSystem;
        this.settingItemNetwork = settingItemNetwork;
        this.settingItemBluetooth = settingItemBluetooth;
        this.settingItemApps = settingItemApps;
        this.settingItemDevice = settingItemDevice;
        this.settingItemAmbient = settingItemAmbient;
        this.interpolator = interpolator;
        this.ambientDriftListener = ambientDriftListener;
        this.ambientToggleListener = ambientToggleListener;
        this.openBluetoothDialogFallback = openBluetoothDialogFallback;

        setupListeners();
    }

    private void setupListeners() {
        if (settingItemSystem != null) {
            settingItemSystem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) {}
                }
            });
        }
        if (settingItemNetwork != null) {
            settingItemNetwork.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    try { activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); } catch (Throwable ignored) {}
                }
            });
        }
        if (settingItemBluetooth != null) {
            settingItemBluetooth.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    } catch (Throwable e) {
                        if (openBluetoothDialogFallback != null) {
                            openBluetoothDialogFallback.run();
                        }
                    }
                }
            });
        }
        if (settingItemApps != null) {
            settingItemApps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)); } catch (Throwable ignored) {}
                }
            });
        }
        if (settingItemDevice != null) {
            settingItemDevice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                    } catch (Throwable e1) {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                        } catch (Throwable e2) {
                            try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) {}
                        }
                    }
                }
            });
        }
        if (settingItemAmbient != null) {
            settingItemAmbient.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ambientToggleListener != null) {
                        boolean active = ambientToggleListener.onToggleAmbientMotion();
                        settingItemAmbient.setText(active ? "Ambient Glow: Active" : "Ambient Glow: Paused");
                    }
                }
            });
        }
    }

    public boolean isOpen() {
        return layoutSettingsScrim != null && layoutSettingsScrim.getVisibility() == View.VISIBLE;
    }

    public void toggle() {
        if (isOpen()) {
            hide();
            if (btnSettingsAnchor != null) btnSettingsAnchor.requestFocus();
        } else {
            show();
        }
    }

    public void show() {
        if (ambientDriftListener != null) {
            ambientDriftListener.onStopAmbientDrift();
        }
        if (layoutSettingsScrim != null) {
            layoutSettingsScrim.setVisibility(View.VISIBLE);
            layoutSettingsScrim.setAlpha(0f);
            layoutSettingsScrim.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(interpolator)
                    .start();
        }
        if (settingItemSystem != null) {
            settingItemSystem.requestFocus();
        }
    }

    public void hide() {
        if (layoutSettingsScrim != null && layoutSettingsScrim.getVisibility() == View.VISIBLE) {
            layoutSettingsScrim.setVisibility(View.GONE);
            if (ambientDriftListener != null) {
                ambientDriftListener.onStartAmbientDrift();
            }
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (!isOpen()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            hide();
            if (btnSettingsAnchor != null) btnSettingsAnchor.requestFocus();
            return true;
        }

        View curr = activity.getCurrentFocus();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (curr == settingItemSystem) {
                return true; // Trap at top
            } else if (curr == settingItemNetwork) {
                if (settingItemSystem != null) settingItemSystem.requestFocus();
                return true;
            } else if (curr == settingItemBluetooth) {
                if (settingItemNetwork != null) settingItemNetwork.requestFocus();
                return true;
            } else if (curr == settingItemApps) {
                if (settingItemBluetooth != null) settingItemBluetooth.requestFocus();
                return true;
            } else if (curr == settingItemDevice) {
                if (settingItemApps != null) settingItemApps.requestFocus();
                return true;
            } else if (curr == settingItemAmbient) {
                if (settingItemDevice != null) settingItemDevice.requestFocus();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (curr == settingItemSystem) {
                if (settingItemNetwork != null) settingItemNetwork.requestFocus();
                return true;
            } else if (curr == settingItemNetwork) {
                if (settingItemBluetooth != null) settingItemBluetooth.requestFocus();
                return true;
            } else if (curr == settingItemBluetooth) {
                if (settingItemApps != null) settingItemApps.requestFocus();
                return true;
            } else if (curr == settingItemApps) {
                if (settingItemDevice != null) settingItemDevice.requestFocus();
                return true;
            } else if (curr == settingItemDevice) {
                if (settingItemAmbient != null) settingItemAmbient.requestFocus();
                return true;
            } else if (curr == settingItemAmbient) {
                return true; // Trap at bottom
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return true; // Trap laterally
        }

        return false;
    }
}
