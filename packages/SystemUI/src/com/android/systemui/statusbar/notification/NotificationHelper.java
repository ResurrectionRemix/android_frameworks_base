/*
 * Copyright (C) 2014 ParanoidAndroid Project.
 * Modified (C) 2015 The Fusion Project.
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

package com.android.systemui.statusbar.notification;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import com.android.systemui.statusbar.NotificationData.Entry;

import java.util.List;

/**
 * Helper class
 * Provides some helper methods
 */
public class NotificationHelper {

    private BaseStatusBar mStatusBar;
    private Context mContext;
    private ActivityManager mActivityManager;

    /**
     * Creates a new instance
     * @Param context the current Context
     * @Param statusBar the current BaseStatusBar
     */
    public NotificationHelper(BaseStatusBar statusBar, Context context) {
        mContext = context;
        mStatusBar = statusBar;

        // we need to know which is the foreground app
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public String getForegroundPackageName() {
        List<RunningTaskInfo> taskInfo = mActivityManager.getRunningTasks(1);
        ComponentName componentInfo = taskInfo.get(0).topActivity;
        return componentInfo.getPackageName();
    }

    public NotificationClicker getNotificationClickListener(Entry entry, boolean floating) {
        NotificationClicker intent = null;
        final PendingIntent contentIntent = entry.notification.getNotification().contentIntent;
        boolean openInFloatingMode = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.HEADS_UP_FLOATING, 0) == 1;

        if (contentIntent != null) {
            //final StatusBarNotification sbn = entry.notification;
            intent = mStatusBar.makeClicker(contentIntent, entry.notification.getKey(), true);
            boolean makeFloating = floating
                    // if the notification is from the foreground app, don't open in floating mode
                    && !entry.notification.getPackageName().equals(getForegroundPackageName())
                    && openInFloatingMode;

            intent.makeFloating(makeFloating);
        }
        return intent;
    }
}
