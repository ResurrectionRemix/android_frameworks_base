/*
 * Copyright (C) 2020 ABC ROM
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
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.content.pm.ActivityInfo;
import android.os.UserHandle;
import android.provider.Settings;

public class RightBackSwipeCustomApp extends LeftBackSwipeCustomApp {

    @Override
    protected void setPackage(String packageName, String friendlyAppString) {
        Settings.System.putStringForUser(getContentResolver(),
                Settings.System.RIGHT_LONG_BACK_SWIPE_APP_ACTION, packageName,
                UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(getContentResolver(),
                Settings.System.RIGHT_LONG_BACK_SWIPE_APP_FR_ACTION, friendlyAppString,
                UserHandle.USER_CURRENT);
    }

    @Override
    protected void setPackageActivity(ActivityInfo ai) {
        Settings.System.putStringForUser(
                getContentResolver(), Settings.System.RIGHT_LONG_BACK_SWIPE_APP_ACTIVITY_ACTION,
                ai != null ? ai.name : "NONE",
                UserHandle.USER_CURRENT);
    }
}
