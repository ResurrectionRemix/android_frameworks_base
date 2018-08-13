/*
* Copyright (C) 2014-2018 The OmniROM Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.internal.util.omni;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.DisplayInfo;
import android.view.WindowManager;
import android.provider.Settings;

import com.android.internal.telephony.PhoneConstants;
import static android.hardware.Sensor.TYPE_LIGHT;
import static android.hardware.Sensor.TYPE_PROXIMITY;

import java.util.List;

public class DeviceUtils {

    // Device types
    public static final int DEVICE_PHONE  = 0;
    public static final int DEVICE_HYBRID = 1;
    public static final int DEVICE_TABLET = 2;

    public static boolean deviceSupportsNfc(Context context) {
        return (NfcAdapter.getDefaultAdapter(context) != null) ||
           context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    public static boolean deviceSupportsVibrator(Context ctx) {
        Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        return vibrator.hasVibrator();
    }

    public static boolean deviceSupportNavigationBar(Context context) {
        return deviceSupportNavigationBarForUser(context, UserHandle.USER_CURRENT);
    }

    public static boolean deviceSupportNavigationBarForUser(Context context, int userId) {
        final boolean showByDefault = context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
        /*final int hasNavigationBar = Settings.System.getIntForUser(
                context.getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW, -1,
                userId);*/
        final int hasNavigationBar = -1;

        if (hasNavigationBar == -1) {
            String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                return false;
            } else if ("0".equals(navBarOverride)) {
                return true;
            } else {
                return showByDefault;
            }
        } else {
            return hasNavigationBar == 1;
        }
    }

    private static int getScreenType(Context con) {
        WindowManager wm = (WindowManager)con.getSystemService(Context.WINDOW_SERVICE);
        DisplayInfo outDisplayInfo = new DisplayInfo();
        wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);
        int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
        int shortSizeDp =
            shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;
        if (shortSizeDp < 600) {
            return DEVICE_PHONE;
        } else if (shortSizeDp < 720) {
            return DEVICE_HYBRID;
        } else {
            return DEVICE_TABLET;
        }
    }

    public static boolean isPhone(Context con) {
        return getScreenType(con) == DEVICE_PHONE;
    }

    public static boolean isHybrid(Context con) {
        return getScreenType(con) == DEVICE_HYBRID;
    }

    public static boolean isTablet(Context con) {
        return getScreenType(con) == DEVICE_TABLET;
    }

    public static boolean isLandscapePhone(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && config.smallestScreenWidthDp < 600;
    }

    public static boolean isDataEncrypted() {
        String voldState = SystemProperties.get("vold.decrypt");
        return "1".equals(voldState) || "trigger_restart_min_framework".equals(voldState);
    }
}
