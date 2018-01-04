/*
 * Copyright (C) 2018 CarbonROM
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

package com.android.systemui.smartpixels;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

// Handles Boot, PowerSave, and Settings changes
public class SmartPixelsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmartPixelsReceiver";
    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;
    private Context mContext;
    private int mSmartPixelsEnable;
    private int mSmartPixelsOnPowerSave;
    private int mLowPowerMode;
    private boolean mSmartPixelsRunning = false;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SMART_PIXELS_ENABLE),
                    false, this, UserHandle.USER_CURRENT);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SMART_PIXELS_ON_POWER_SAVE),
                    false, this, UserHandle.USER_CURRENT);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SMART_PIXELS_PATTERN),
                    false, this, UserHandle.USER_CURRENT);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SMART_PIXELS_SHIFT_TIMEOUT),
                    false, this, UserHandle.USER_CURRENT);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.LOW_POWER_MODE),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            Intent smartPixels = new Intent(mContext, com.android.systemui.smartpixels.SmartPixelsService.class);
            ContentResolver resolver = mContext.getContentResolver();

            mSmartPixelsEnable = Settings.System.getIntForUser(
                    resolver, Settings.System.SMART_PIXELS_ENABLE,
                    0, UserHandle.USER_CURRENT);
            mSmartPixelsOnPowerSave = Settings.System.getIntForUser(
                    resolver, Settings.System.SMART_PIXELS_ON_POWER_SAVE,
                    0, UserHandle.USER_CURRENT);
            mLowPowerMode = Settings.Global.getInt(
                    resolver, Settings.Global.LOW_POWER_MODE, 0);

            if ((mSmartPixelsEnable == 0) && (mSmartPixelsOnPowerSave != 0)) {
                if ((mLowPowerMode != 0) && !mSmartPixelsRunning) {
                    mContext.startService(smartPixels);
                    mSmartPixelsRunning = true;
                    Log.d(TAG, "Started Smart Pixels service by LowPowerMode enable");
                } else if ((mLowPowerMode == 0) && mSmartPixelsRunning) {
                    mContext.stopService(smartPixels);
                    mSmartPixelsRunning = false;
                    Log.d(TAG, "Stopped Smart Pixels service by LowPowerMode disable");
                } else if ((mLowPowerMode != 0) && mSmartPixelsRunning) {
                    mContext.stopService(smartPixels);
                    mContext.startService(smartPixels);
                    Log.d(TAG, "Restarted Smart Pixels service by LowPowerMode enable");
                }
            } else if ((mSmartPixelsEnable != 0) && !mSmartPixelsRunning) {
                mContext.startService(smartPixels);
                mSmartPixelsRunning = true;
                Log.d(TAG, "Started Smart Pixels service by enable");
            } else if ((mSmartPixelsEnable == 0) && mSmartPixelsRunning) {
                mContext.stopService(smartPixels);
                mSmartPixelsRunning = false;
                Log.d(TAG, "Stopped Smart Pixels service by disable");
            } else if ((mSmartPixelsEnable != 0) && mSmartPixelsRunning) {
                mContext.stopService(smartPixels);
                mContext.startService(smartPixels);
                Log.d(TAG, "Restarted Smart Pixels service");
            }
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        mContext = context;
        try {
            if (mSettingsObserver ==  null) {
                mSettingsObserver = new SettingsObserver(mHandler);
                mSettingsObserver.observe();
            }
            mSettingsObserver.update();
        } catch (Exception e) {
            Log.e(TAG, "Can't start load average service", e);
        }
    }
}
