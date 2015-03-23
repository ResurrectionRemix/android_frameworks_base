/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.widget;

import android.content.Context;

<<<<<<< HEAD:packages/SystemUI/src/com/android/systemui/statusbar/policy/Prefs.java
public class Prefs {
    private static final String SHARED_PREFS_NAME = "status_bar";

    public static final String LAST_BATTERY_LEVEL = "last_battery_level";

    public static SharedPreferences read(Context context) {
        return context.getSharedPreferences(Prefs.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
=======
/**
 * To access non public members of classes in {@link Toolbar}
 */
public class Toolbar_Accessor {
    public static ActionMenuPresenter getActionMenuPresenter(Toolbar toolbar) {
        return toolbar.getOuterActionMenuPresenter();
>>>>>>> 0e7c113... Evo Merge Part - 2:tools/layoutlib/bridge/src/android/widget/Toolbar_Accessor.java
    }

    public static Context getPopupContext(Toolbar toolbar) {
        return toolbar.getPopupContext();
    }

    public static void setLastBatteryLevel(Context context, int level) {
        edit(context).putInt(LAST_BATTERY_LEVEL, level).commit();
    }

    public static int getLastBatteryLevel(Context context) {
        return read(context).getInt(LAST_BATTERY_LEVEL, 50);
    }

}
