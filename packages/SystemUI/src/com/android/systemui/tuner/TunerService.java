/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.DemoMode;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;

public abstract class TunerService {

    public abstract void clearAll();
    public abstract void destroy();

    public abstract String getValue(String setting);
    public abstract int getValue(String setting, int def);
    public abstract String getValue(String setting, String def);

    public abstract void setValue(String setting, String value);
    public abstract void setValue(String setting, int value);

    public abstract void addTunable(Tunable tunable, String... keys);
    public abstract void removeTunable(Tunable tunable);

    public interface Tunable {
        void onTuningChanged(String key, String newValue);
    }

    private static Context userContext(Context context) {
        try {
            return context.createPackageContextAsUser(context.getPackageName(), 0,
                    new UserHandle(ActivityManager.getCurrentUser()));
        } catch (NameNotFoundException e) {
            return context;
        }
    }
}
