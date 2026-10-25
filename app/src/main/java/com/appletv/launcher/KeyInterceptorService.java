package com.appletv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class KeyInterceptorService extends AccessibilityService {

    public static final String ACTION_TOGGLE_RECENTS = "com.appletv.launcher.ACTION_TOGGLE_RECENTS";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                // Intercept Overview / Apps key and notify launcher
                Intent intent = new Intent(ACTION_TOGGLE_RECENTS);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
                return true;
            }
        }
        return super.onKeyEvent(event);
    }
}
