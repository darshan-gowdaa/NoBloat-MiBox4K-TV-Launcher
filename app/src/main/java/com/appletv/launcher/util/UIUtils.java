package com.appletv.launcher.util;

import android.content.Context;
import android.graphics.Color;

public final class UIUtils {

    private UIUtils() {}

    public static int dpToPx(Context context, float dp) {
        if (context == null) return Math.round(dp);
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static int blendColors(int bg, int fg, float ratio) {
        float inv = 1.0f - ratio;
        int a = (int) (Color.alpha(bg) * inv + Color.alpha(fg) * ratio);
        int r = (int) (Color.red(bg) * inv + Color.red(fg) * ratio);
        int g = (int) (Color.green(bg) * inv + Color.green(fg) * ratio);
        int b = (int) (Color.blue(bg) * inv + Color.blue(fg) * ratio);
        return Color.argb(
                Math.max(0, Math.min(255, a)),
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b))
        );
    }
}
