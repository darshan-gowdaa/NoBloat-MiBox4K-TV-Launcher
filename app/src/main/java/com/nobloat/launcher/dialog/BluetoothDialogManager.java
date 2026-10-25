package com.nobloat.launcher.dialog;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nobloat.launcher.BluetoothTracker;
import com.nobloat.launcher.R;
import com.nobloat.launcher.util.UIUtils;

import java.util.Locale;
import java.util.Set;

public class BluetoothDialogManager {

    public interface AmbientDriftListener {
        void onStartAmbientDrift();
        void onStopAmbientDrift();
    }

    private final Activity activity;
    private final BluetoothTracker bluetoothTracker;
    private final FrameLayout layoutBluetoothScrim;
    private final LinearLayout layoutBluetoothDialog;
    private final LinearLayout containerBtDevices;
    private final TextView btnOpenBtSettings;
    private final View btnBluetoothAnchor;
    private final PathInterpolator interpolator;
    private final AmbientDriftListener ambientDriftListener;

    private Typeface outfitBold;
    private Typeface outfitMedium;

    public BluetoothDialogManager(Activity activity,
                                  BluetoothTracker bluetoothTracker,
                                  FrameLayout layoutBluetoothScrim,
                                  LinearLayout layoutBluetoothDialog,
                                  LinearLayout containerBtDevices,
                                  TextView btnOpenBtSettings,
                                  View btnBluetoothAnchor,
                                  PathInterpolator interpolator,
                                  AmbientDriftListener ambientDriftListener) {
        this.activity = activity;
        this.bluetoothTracker = bluetoothTracker;
        this.layoutBluetoothScrim = layoutBluetoothScrim;
        this.layoutBluetoothDialog = layoutBluetoothDialog;
        this.containerBtDevices = containerBtDevices;
        this.btnOpenBtSettings = btnOpenBtSettings;
        this.btnBluetoothAnchor = btnBluetoothAnchor;
        this.interpolator = interpolator;
        this.ambientDriftListener = ambientDriftListener;

        setupViews();
    }

    public void setTypefaces(Typeface bold, Typeface medium) {
        this.outfitBold = bold;
        this.outfitMedium = medium;
    }

    private void setupViews() {
        if (btnOpenBtSettings != null) {
            btnOpenBtSettings.setNextFocusLeftId(R.id.btn_open_bt_settings);
            btnOpenBtSettings.setNextFocusRightId(R.id.btn_open_bt_settings);
            btnOpenBtSettings.setNextFocusDownId(R.id.btn_open_bt_settings);
            btnOpenBtSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    } catch (Throwable e) {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
                        } catch (Throwable ignored) {}
                    }
                }
            });
        }
    }

    public boolean isOpen() {
        return layoutBluetoothScrim != null && layoutBluetoothScrim.getVisibility() == View.VISIBLE;
    }

    public void toggle() {
        if (isOpen()) {
            hide();
            if (btnBluetoothAnchor != null) btnBluetoothAnchor.requestFocus();
        } else {
            show();
        }
    }

    public void show() {
        if (ambientDriftListener != null) {
            ambientDriftListener.onStopAmbientDrift();
        }
        populateBluetoothDevices();

        if (layoutBluetoothDialog != null && btnBluetoothAnchor != null) {
            int[] loc = new int[2];
            btnBluetoothAnchor.getLocationInWindow(loc);
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = UIUtils.dpToPx(activity, 260);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layoutBluetoothDialog.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.width = dialogWidth;

            int targetLeft = loc[0] + (btnBluetoothAnchor.getWidth() - dialogWidth) / 2;
            if (targetLeft + dialogWidth > screenWidth - UIUtils.dpToPx(activity, 24)) {
                targetLeft = screenWidth - UIUtils.dpToPx(activity, 24) - dialogWidth;
            }
            lp.leftMargin = Math.max(UIUtils.dpToPx(activity, 24), targetLeft);
            lp.topMargin = loc[1] + btnBluetoothAnchor.getHeight() + UIUtils.dpToPx(activity, 6);
            layoutBluetoothDialog.setLayoutParams(lp);
        }

        if (layoutBluetoothScrim != null) {
            layoutBluetoothScrim.setVisibility(View.VISIBLE);
            layoutBluetoothScrim.setAlpha(0f);
            layoutBluetoothScrim.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(interpolator)
                    .start();
        }

        if (containerBtDevices != null && containerBtDevices.getChildCount() > 0) {
            containerBtDevices.getChildAt(0).requestFocus();
        } else if (btnOpenBtSettings != null) {
            btnOpenBtSettings.requestFocus();
        }
    }

    public void hide() {
        if (layoutBluetoothScrim != null && layoutBluetoothScrim.getVisibility() == View.VISIBLE) {
            layoutBluetoothScrim.setVisibility(View.GONE);
            if (ambientDriftListener != null) {
                ambientDriftListener.onStartAmbientDrift();
            }
        }
    }

    public int getBluetoothDeviceIcon(BluetoothDevice dev) {
        if (dev == null) return R.drawable.ic_device;
        String name = dev.getName();
        String lower = (name != null) ? name.toLowerCase(Locale.ROOT) : "";

        // Keyword checks for TV accessories
        if (lower.contains("remote") || lower.contains("controller") || lower.contains("gamepad") || lower.contains("dualshock") || lower.contains("xbox")) {
            return R.drawable.ic_gamepad;
        }
        if (lower.contains("headphone") || lower.contains("headset") || lower.contains("earbud") || lower.contains("airpod") || lower.contains("buds") || lower.contains("tws")) {
            return R.drawable.ic_headset;
        }
        if (lower.contains("speaker") || lower.contains("soundbar") || lower.contains("audio") || lower.contains("hifi")) {
            return R.drawable.ic_speaker;
        }
        if (lower.contains("keyboard") || lower.contains("mouse")) {
            return R.drawable.ic_keyboard;
        }
        if (lower.contains("tv") || lower.contains("box") || lower.contains("stick")) {
            return R.drawable.ic_tv;
        }
        if (lower.contains("phone") || lower.contains("pixel") || lower.contains("galaxy") || lower.contains("iphone")) {
            return R.drawable.ic_phone;
        }

        // BluetoothClass constants
        try {
            android.bluetooth.BluetoothClass btClass = dev.getBluetoothClass();
            if (btClass != null) {
                int devClass = btClass.getDeviceClass();
                int major = btClass.getMajorDeviceClass();

                if (devClass == 1048 || devClass == 1028 || devClass == 1032) return R.drawable.ic_headset;
                if (devClass == 1044 || devClass == 1052 || devClass == 1064 || devClass == 1056) return R.drawable.ic_speaker;
                if (devClass == 1060 || devClass == 1080 || devClass == 1084) return R.drawable.ic_tv;
                if (devClass == 0x0540 || devClass == 0x0580 || devClass == 0x05C0) return R.drawable.ic_keyboard;
                if (major == 1280 || devClass == 1096 || devClass == 2064 || devClass == 2068 || devClass == 0x0508 || devClass == 0x0504 || devClass == 0x050C) return R.drawable.ic_gamepad;
                if (major == 1024) return R.drawable.ic_headset;
                if (major == 512) return R.drawable.ic_phone;
                if (major == 256) return R.drawable.ic_keyboard;
            }
        } catch (Throwable ignored) {}

        return R.drawable.ic_device;
    }

    public void populateBluetoothDevices() {
        if (containerBtDevices == null) return;
        containerBtDevices.removeAllViews();
        Set<BluetoothDevice> bonded = bluetoothTracker != null ? bluetoothTracker.getBondedDevicesList() : null;

        if (bonded != null && !bonded.isEmpty()) {
            for (final BluetoothDevice dev : bonded) {
                String devName = dev.getName();
                if (devName == null || devName.trim().isEmpty()) {
                    devName = dev.getAddress();
                }

                TextView row = new TextView(activity);
                row.setId(View.generateViewId());
                row.setNextFocusLeftId(row.getId());
                row.setNextFocusRightId(row.getId());
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dpToPx(activity, 36));
                rowLp.bottomMargin = UIUtils.dpToPx(activity, 4);
                row.setLayoutParams(rowLp);
                row.setBackgroundResource(R.drawable.bg_menu_item);
                row.setFocusable(true);
                row.setClickable(true);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(UIUtils.dpToPx(activity, 14), 0, UIUtils.dpToPx(activity, 14), 0);
                row.setCompoundDrawablesWithIntrinsicBounds(getBluetoothDeviceIcon(dev), 0, 0, 0);
                row.setCompoundDrawablePadding(UIUtils.dpToPx(activity, 10));
                row.setText(devName);
                row.setTextColor(activity.getResources().getColor(R.color.sys_text_primary));
                row.setTextSize(13);
                if (outfitBold != null) row.setTypeface(outfitBold);

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        if (bluetoothTracker != null) {
                            bluetoothTracker.connectToDevice(dev);
                        }
                        if (btnBluetoothAnchor != null) btnBluetoothAnchor.requestFocus();
                    }
                });

                containerBtDevices.addView(row);
            }
            if (btnOpenBtSettings != null && containerBtDevices.getChildCount() > 0) {
                View firstChild = containerBtDevices.getChildAt(0);
                firstChild.setNextFocusUpId(firstChild.getId()); // Trap at top
                View lastChild = containerBtDevices.getChildAt(containerBtDevices.getChildCount() - 1);
                lastChild.setNextFocusDownId(R.id.btn_open_bt_settings);
                btnOpenBtSettings.setNextFocusUpId(lastChild.getId());
                btnOpenBtSettings.setNextFocusDownId(R.id.btn_open_bt_settings); // Trap at bottom
            }
        } else {
            TextView emptyRow = new TextView(activity);
            emptyRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dpToPx(activity, 38)));
            emptyRow.setGravity(Gravity.CENTER_VERTICAL);
            emptyRow.setPadding(UIUtils.dpToPx(activity, 12), 0, UIUtils.dpToPx(activity, 12), 0);
            emptyRow.setText("No paired devices found");
            emptyRow.setTextColor(activity.getResources().getColor(R.color.sys_text_muted));
            emptyRow.setTextSize(12);
            if (outfitMedium != null) emptyRow.setTypeface(outfitMedium);
            containerBtDevices.addView(emptyRow);
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (!isOpen()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            hide();
            if (btnBluetoothAnchor != null) btnBluetoothAnchor.requestFocus();
            return true;
        }

        View curr = activity.getCurrentFocus();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (containerBtDevices != null && containerBtDevices.getChildCount() > 0 && curr == containerBtDevices.getChildAt(0)) {
                return true; // Trap at top
            } else if (curr == btnOpenBtSettings && containerBtDevices != null && containerBtDevices.getChildCount() > 0) {
                containerBtDevices.getChildAt(containerBtDevices.getChildCount() - 1).requestFocus();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (curr == btnOpenBtSettings) {
                return true; // Trap at bottom
            } else if (containerBtDevices != null && containerBtDevices.getChildCount() > 0 && curr == containerBtDevices.getChildAt(containerBtDevices.getChildCount() - 1)) {
                if (btnOpenBtSettings != null) btnOpenBtSettings.requestFocus();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return true; // Trap laterally
        }

        return false;
    }
}
