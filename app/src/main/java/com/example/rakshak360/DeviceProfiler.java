package com.example.rakshak360;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public class DeviceProfiler {

    public static final int TIER_LOW = 1;
    public static final int TIER_MID = 2;
    public static final int TIER_HIGH = 3;

    // यह फंक्शन चेक करेगा कि फोन किस कैटेगरी का है
    public static int getDeviceTier(Context context) {
        ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();

        if (actManager != null) {
            actManager.getMemoryInfo(memInfo);
        }

        long totalRamGB = memInfo.totalMem / (1024 * 1024 * 1024); // RAM in GB

        // High-End Device (> 7GB RAM)
        if (totalRamGB >= 7) {
            return TIER_HIGH;
        }
        // Mid-Range Device (4GB to 6GB RAM)
        else if (totalRamGB >= 4) {
            return TIER_MID;
        }
        // Low-End Device (< 4GB RAM)
        else {
            return TIER_LOW;
        }
    }

    // चेक करें कि फोन में LiDAR/ToF सेंसर है या नहीं (High-End के लिए)
    public static boolean hasLiDARSensor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager pm = context.getPackageManager();
            // Android camera depth feature indicates presence of ToF/LiDAR
            return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT);
        }
        return false;
    }
}