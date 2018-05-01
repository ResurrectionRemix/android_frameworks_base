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

package com.android.server.smartpixels;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

// Handles Runtime start, PowerSave, and Settings changes
public class SmartPixelsReceiver extends BroadcastReceiver {
   private static final String TAG = "SmartPixelsReceiver";

   private Context mContext;
   private Handler mHandler;
   private ContentResolver mResolver;
   private final PowerManager mPowerManager;
   private SettingsObserver mSettingsObserver;
   private Intent mSmartPixelsService;

   private boolean mSmartPixelsEnable;
   private boolean mSmartPixelsOnPowerSave;
   private boolean mPowerSaveEnable;
   private boolean mSmartPixelsRunning = false;

   public SmartPixelsReceiver(Context context) {
       mContext = context;
       mHandler = new Handler();
       mResolver = mContext.getContentResolver();
       mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
       mSmartPixelsService = new Intent(mContext,
               com.android.server.smartpixels.SmartPixelsService.class);

       registerReceiver();
       initiateSettingsObserver();
   }

   private void registerReceiver() {
       IntentFilter filter = new IntentFilter();
       filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
       filter.addAction(Intent.ACTION_USER_FOREGROUND);
       mContext.registerReceiver(this, filter);
   }

   private void initiateSettingsObserver() {
       mSettingsObserver = new SettingsObserver(mHandler);
       mSettingsObserver.observe();
       mSettingsObserver.update();
   }

   private class SettingsObserver extends ContentObserver {
       SettingsObserver(Handler handler) {
           super(handler);
       }

       void observe() {
           mResolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.SMART_PIXELS_ENABLE),
                   false, this, UserHandle.USER_ALL);
           mResolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.SMART_PIXELS_ON_POWER_SAVE),
                   false, this, UserHandle.USER_ALL);
           mResolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.SMART_PIXELS_PATTERN),
                   false, this, UserHandle.USER_ALL);
           mResolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.SMART_PIXELS_SHIFT_TIMEOUT),
                   false, this, UserHandle.USER_ALL);
           update();
       }

       @Override
       public void onChange(boolean selfChange) {
           update();
       }

       public void update() {
           mSmartPixelsEnable = (Settings.System.getIntForUser(
                   mResolver, Settings.System.SMART_PIXELS_ENABLE,
                   0, UserHandle.USER_CURRENT) == 1);
           mSmartPixelsOnPowerSave = (Settings.System.getIntForUser(
                   mResolver, Settings.System.SMART_PIXELS_ON_POWER_SAVE,
                   0, UserHandle.USER_CURRENT) == 1);
           mPowerSaveEnable = mPowerManager.isPowerSaveMode();

           if (!mSmartPixelsEnable && mSmartPixelsOnPowerSave) {
               if (mPowerSaveEnable && !mSmartPixelsRunning) {
                   mContext.startService(mSmartPixelsService);
                   mSmartPixelsRunning = true;
                   Log.d(TAG, "Started Smart Pixels Service by Power Save enable");
               } else if (!mPowerSaveEnable && mSmartPixelsRunning) {
                   mContext.stopService(mSmartPixelsService);
                   mSmartPixelsRunning = false;
                   Log.d(TAG, "Stopped Smart Pixels Service by Power Save disable");
               } else if (mPowerSaveEnable && mSmartPixelsRunning) {
                   mContext.stopService(mSmartPixelsService);
                   mContext.startService(mSmartPixelsService);
                   Log.d(TAG, "Restarted Smart Pixels Service by Power Save enable");
               }
           } else if (mSmartPixelsEnable && !mSmartPixelsRunning) {
               mContext.startService(mSmartPixelsService);
               mSmartPixelsRunning = true;
               Log.d(TAG, "Started Smart Pixels Service by enable");
           } else if (!mSmartPixelsEnable && mSmartPixelsRunning) {
               mContext.stopService(mSmartPixelsService);
               mSmartPixelsRunning = false;
               Log.d(TAG, "Stopped Smart Pixels Service by disable");
           } else if (mSmartPixelsEnable && mSmartPixelsRunning) {
               mContext.stopService(mSmartPixelsService);
               mContext.startService(mSmartPixelsService);
               Log.d(TAG, "Restarted Smart Pixels Service");
           }
       }
   }

   @Override
   public void onReceive(final Context context, Intent intent) {
       mSettingsObserver.update();
   }
}
