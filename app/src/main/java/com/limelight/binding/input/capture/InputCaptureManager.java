package com.limelight.binding.input.capture;

import android.app.Activity;

import com.limelight.LimeLog;
import com.limelight.R;

public class InputCaptureManager {
    public static InputCaptureProvider getInputCaptureProvider(Activity activity) {
        if (AndroidNativePointerCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using Android O+ native mouse capture");
            return new AndroidNativePointerCaptureProvider(activity, activity.findViewById(R.id.surfaceView));
        }
        // LineageOS implemented broken NVIDIA capture extensions, so avoid using them on root builds.
        // See https://github.com/LineageOS/android_frameworks_base/commit/d304f478a023430f4712dbdc3ee69d9ad02cebd3
        else if (ShieldCaptureProvider.isCaptureProviderSupported()) {
            LimeLog.info("Using NVIDIA mouse capture extension");
            return new ShieldCaptureProvider(activity);
        }
        else if (AndroidPointerIconCaptureProvider.isCaptureProviderSupported()) {
            // Android N's native capture can't capture over system UI elements
            // so we want to only use it if there's no other option.
            LimeLog.info("Using Android N+ pointer hiding");
            return new AndroidPointerIconCaptureProvider(activity, activity.findViewById(R.id.surfaceView));
        }
        else {
            LimeLog.info("Mouse capture not available");
            return new NullCaptureProvider();
        }
    }
}
