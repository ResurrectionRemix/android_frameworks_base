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

package com.android.systemui.qs;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings.System;

import com.android.systemui.statusbar.policy.Listenable;

/** Helper for managing a System setting. **/
public abstract class SystemSetting extends ContentObserver implements Listenable {
    private final Context mContext;
    private final String mSettingName;

    protected abstract void handleValueChanged(int value);

    public SystemSetting(Context context, Handler handler, String settingName) {
        super(handler);
        mContext = context;
        mSettingName = settingName;
    }

    public int getValue() {
        return System.getInt(mContext.getContentResolver(), mSettingName, 0);
    }

    public void setValue(int value) {
        System.putInt(mContext.getContentResolver(), mSettingName, value);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    System.getUriFor(mSettingName), false, this);
        } else {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        handleValueChanged(getValue());
    }
}
