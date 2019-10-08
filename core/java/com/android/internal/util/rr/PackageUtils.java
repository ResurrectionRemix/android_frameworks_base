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
package com.android.internal.util.rr;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

public class PackageUtils {

    public static boolean isPackageInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPackageAvailable(Context context, String packageName) {
        final PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public static <T> T notNullOrDefault(T value, T defValue) {
        return value == null ? defValue : value;
    }

    public static boolean isDozePackageAvailable(Context context) {
        return isPackageAvailable(context, PackageConstants.DOZE_PACKAGE_NAME) ||
            isPackageAvailable(context, PackageConstants.ONEPLUS_DOZE_PACKAGE_NAME) ||
            isPackageAvailable(context, PackageConstants.CUSTOM_DOZE_PACKAGE_NAME);
    }

    public static boolean isTouchGesturesPackageAvailable(Context context) {
        return isPackageAvailable(context, PackageConstants.TOUCHGESTURES_PACKAGE_NAME);
    }
}
