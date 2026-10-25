package com.nobloat.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class BluetoothTracker extends BroadcastReceiver implements BluetoothProfile.ServiceListener {

    public interface OnBluetoothStateChangedListener {
        void onBluetoothStateChanged(String statusText, boolean isConnected);
    }

    private final Context context;
    private final OnBluetoothStateChangedListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothProfile a2dpProfile;
    private BluetoothProfile headsetProfile;
    private BluetoothProfile inputProfile; // HID Profile (Profile 4)

    private static final int PROFILE_INPUT_DEVICE = 4; // BluetoothProfile.INPUT_DEVICE in AOSP

    public BluetoothTracker(Context context, OnBluetoothStateChangedListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        try {
            this.adapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable ignored) {
            this.adapter = null;
        }
    }

    public void start() {
        if (adapter == null) {
            notifyListener("Unavailable", false);
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED");
        try {
            context.registerReceiver(this, filter);
        } catch (Throwable ignored) {}

        // Bind profile proxies
        try {
            adapter.getProfileProxy(context, this, BluetoothProfile.A2DP);
            adapter.getProfileProxy(context, this, BluetoothProfile.HEADSET);
            adapter.getProfileProxy(context, this, PROFILE_INPUT_DEVICE);
        } catch (Throwable ignored) {}

        refresh();
    }

    public void stop() {
        try {
            context.unregisterReceiver(this);
        } catch (Throwable ignored) {}

        try {
            if (adapter != null) {
                if (a2dpProfile != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile);
                if (headsetProfile != null) adapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile);
                if (inputProfile != null) adapter.closeProfileProxy(PROFILE_INPUT_DEVICE, inputProfile);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        refresh();
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        if (profile == BluetoothProfile.A2DP) {
            a2dpProfile = proxy;
        } else if (profile == BluetoothProfile.HEADSET) {
            headsetProfile = proxy;
        } else if (profile == PROFILE_INPUT_DEVICE) {
            inputProfile = proxy;
        }
        refresh();
    }

    @Override
    public void onServiceDisconnected(int profile) {
        if (profile == BluetoothProfile.A2DP) {
            a2dpProfile = null;
        } else if (profile == BluetoothProfile.HEADSET) {
            headsetProfile = null;
        } else if (profile == PROFILE_INPUT_DEVICE) {
            inputProfile = null;
        }
        refresh();
    }

    public void refresh() {
        if (adapter == null || !adapter.isEnabled()) {
            notifyListener("Bluetooth Off", false);
            return;
        }

        // Check for connected audio or input devices
        BluetoothDevice connectedDevice = null;
        String deviceType = "Device";

        if (a2dpProfile != null) {
            try {
                List<BluetoothDevice> devs = a2dpProfile.getConnectedDevices();
                if (devs != null && !devs.isEmpty()) {
                    connectedDevice = devs.get(0);
                    deviceType = "Audio";
                }
            } catch (Throwable ignored) {}
        }

        if (connectedDevice == null && headsetProfile != null) {
            try {
                List<BluetoothDevice> devs = headsetProfile.getConnectedDevices();
                if (devs != null && !devs.isEmpty()) {
                    connectedDevice = devs.get(0);
                    deviceType = "Headset";
                }
            } catch (Throwable ignored) {}
        }

        if (connectedDevice == null && inputProfile != null) {
            try {
                List<BluetoothDevice> devs = inputProfile.getConnectedDevices();
                if (devs != null && !devs.isEmpty()) {
                    connectedDevice = devs.get(0);
                    deviceType = "Remote";
                }
            } catch (Throwable ignored) {}
        }

        if (connectedDevice != null) {
            String name = connectedDevice.getName();
            if (name == null || name.isEmpty()) {
                name = deviceType;
            } else if (name.length() > 14) {
                name = name.substring(0, 12) + "...";
            }

            int battery = getDeviceBattery(connectedDevice);
            if (battery >= 0 && battery <= 100) {
                notifyListener(name + " " + battery + "%", true);
            } else {
                notifyListener(name, true);
            }
            return;
        }

        // Check bonded count as fallback
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null && !bonded.isEmpty()) {
                notifyListener("Paired (" + bonded.size() + ")", false);
            } else {
                notifyListener("Ready", false);
            }
        } catch (Throwable ignored) {
            notifyListener("Ready", false);
        }
    }

    private int getDeviceBattery(BluetoothDevice device) {
        if (device == null) return -1;
        try {
            Method m = device.getClass().getMethod("getBatteryLevel");
            Object result = m.invoke(device);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public Set<BluetoothDevice> getBondedDevicesList() {
        if (adapter != null && adapter.isEnabled()) {
            try {
                return adapter.getBondedDevices();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public boolean connectToDevice(BluetoothDevice device) {
        if (device == null) return false;
        boolean initiated = false;
        if (a2dpProfile != null) {
            try {
                Method m = a2dpProfile.getClass().getMethod("connect", BluetoothDevice.class);
                m.setAccessible(true);
                Object res = m.invoke(a2dpProfile, device);
                if (res instanceof Boolean) initiated = (Boolean) res;
            } catch (Throwable ignored) {}
        }
        if (!initiated && headsetProfile != null) {
            try {
                Method m = headsetProfile.getClass().getMethod("connect", BluetoothDevice.class);
                m.setAccessible(true);
                Object res = m.invoke(headsetProfile, device);
                if (res instanceof Boolean) initiated = (Boolean) res;
            } catch (Throwable ignored) {}
        }
        refresh();
        return initiated;
    }

    private void notifyListener(final String text, final boolean isConnected) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onBluetoothStateChanged(text, isConnected);
                }
            }
        });
    }
}
