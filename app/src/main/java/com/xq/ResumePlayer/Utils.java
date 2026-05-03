package com.xq.ResumePlayer;

import android.os.Build;

public class Utils {
    /**
     * Returns true if the device is running Android 13 (API 33) or higher.
     */
    public static boolean isAndroid13orHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }
}
