/*
 * Copyright (C) 2015-2016 The MoKee Open Source Project
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

package com.android.systemui.rr;

import android.content.Context;
import android.text.TextUtils;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LockTaskHelper {

    private static Context mContext;
    private static LockTaskHelper mLockTaskHelper;
    private static Map<String, TaskInfo> lockedTaskMap = new HashMap<String, TaskInfo>();

    private LockTaskHelper(Context context) {
        mContext = context;
        if (context != null) {
            refreshLockedTaskMap();
        }
    }

    public static synchronized LockTaskHelper init(Context context) {
        if (mLockTaskHelper == null) {
            mLockTaskHelper = new LockTaskHelper(context);
        }
        return mLockTaskHelper;
    }

    private void refreshLockedTaskMap() {
        String taskMap = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.LOCKED_RECENT_TASK_LIST);

        lockedTaskMap.clear();

        if (TextUtils.isEmpty(taskMap))
            return;

        String[] array = TextUtils.split(taskMap, "\\|");
        for (String item : array) {
            if (TextUtils.isEmpty(item)) {
                continue;
            }
            TaskInfo taskInfo = TaskInfo.fromString(item);
            lockedTaskMap.put(taskInfo.name, taskInfo);
        }
    }

    public void saveLockedTaskMap() {
        List<String> settings = new ArrayList<String>();
        for (TaskInfo taskInfo : lockedTaskMap.values()) {
            settings.add(taskInfo.toString());
        }
        String value = TextUtils.join("|", settings);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.LOCKED_RECENT_TASK_LIST, value);
    }

    public void removeTask(String packageName) {
        if (lockedTaskMap.remove(packageName) != null) {
            saveLockedTaskMap();
        }
    }

    public void addTask(String packageName) {
        TaskInfo taskInfo = lockedTaskMap.get(packageName);
        if (taskInfo == null) {
            taskInfo = new TaskInfo(packageName);
            lockedTaskMap.put(packageName, taskInfo);
            saveLockedTaskMap();
        }
    }

    public boolean isLockedTask(String packageName) {
        if (lockedTaskMap.get(packageName) != null) {
            return true;
        } else {
            return false;
        }
    }

    public static class TaskInfo {
        public String name;

        public TaskInfo(String name) {
            this.name = name;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(name);
            return builder.toString();
        }

        public static TaskInfo fromString(String value) {
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            try {
                TaskInfo item = new TaskInfo(value);
                return item;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    };

}
