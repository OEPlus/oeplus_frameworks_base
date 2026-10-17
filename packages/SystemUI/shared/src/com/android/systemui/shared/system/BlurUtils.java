/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.shared.system;

import static android.view.CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED;

import static com.android.systemui.Flags.blurOnMoreSurfaces;
import static com.android.systemui.Flags.enableLockscreenBlur;
import static com.android.systemui.Flags.lockscreenBlurForNotifications;

import android.app.ActivityManager;
import android.content.res.Resources;
import android.os.SystemProperties;

public abstract class BlurUtils {

    /**
     * If this device can render blurs.
     *
     * @return {@code true} when supported.
     */
    public static boolean supportsBlursOnWindows() {
        return CROSS_WINDOW_BLUR_SUPPORTED && ActivityManager.isHighEndGfx()
                && !SystemProperties.getBoolean("persist.sysui.disableBlur", false);
    }

    public static boolean isVolumeAndPowerBlurEnabled() {
        return (blurOnMoreSurfaces() || isOEPlusGlassEnabled()) && isBlurExpansionEnabledOnDevice();
    }

    /** Blur on lock screen surfaces (shortcuts, device entry icon), or the OEPlus glass theme. */
    public static boolean isLockscreenBlurEnabled() {
        return enableLockscreenBlur() || isOEPlusGlassEnabled();
    }

    /** Blurred notification backgrounds (shade and lock screen), or the OEPlus glass theme. */
    public static boolean isNotificationBlurEnabled() {
        return lockscreenBlurForNotifications() || isOEPlusGlassEnabled();
    }

    /**
     * OEPlus glass theme: SystemUI surfaces get their own blur regions, which SurfaceFlinger draws
     * as glass. Read once per process; Settings restarts SystemUI when the theme switch changes.
     */
    public static boolean isOEPlusGlassEnabled() {
        return OEPlusGlass.ENABLED;
    }

    private static final class OEPlusGlass {
        static final boolean ENABLED =
                SystemProperties.getBoolean("persist.sys.oeplus.theme.glass", false);
    }

    private static boolean isBlurExpansionEnabledOnDevice() {
        return Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_enableBlurExpansion);
    }
}
