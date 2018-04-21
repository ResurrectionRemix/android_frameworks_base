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

public class SmartPixelsReceiver extends BroadcastReceiver {
   private static final String TAG = "SmartPixelsReceiver";

   private Context mContext;
   private Handler mHandler;
   private ContentResolver mResolver;
   private final PowerManager mPowerManager;
   private SettingsObserver mSettingsObserver;
   private Intent mSmartPixelsService;
   private IntentFilter mFilter;

   private boolean mEnabled;
   private boolean mOnPowerSave;
   private boolean mPowerSave;
   private boolean mServiceRunning = false;
   private boolean mRegisteredReceiver = false;

   public SmartPixelsReceiver(Context context) {
       mContext = context;
       mHandler = new Handler();
       mResolver = mContext.getContentResolver();
       mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
       mSmartPixelsService = new Intent(mContext,
               com.android.server.smartpixels.SmartPixelsService.class);

       mFilter = new IntentFilter();
       mFilter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
       mFilter.addAction(Intent.ACTION_USER_FOREGROUND);

       initiateSettingsObserver();
   }

   private void registerReceiver() {
       mContext.registerReceiver(this, mFilter);
       mRegisteredReceiver = true;
   }

   private void unregisterReceiver() {
       mContext.unregisterReceiver(this);
       mRegisteredReceiver = false;
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
           mEnabled = (Settings.System.getIntForUser(
                   mResolver, Settings.System.SMART_PIXELS_ENABLE,
                   0, UserHandle.USER_CURRENT) == 1);
           mOnPowerSave = (Settings.System.getIntForUser(
                   mResolver, Settings.System.SMART_PIXELS_ON_POWER_SAVE,
                   0, UserHandle.USER_CURRENT) == 1);
           mPowerSave = mPowerManager.isPowerSaveMode();

           if (mEnabled || mOnPowerSave) {
               if (!mRegisteredReceiver)
                   registerReceiver();
           } else if (mRegisteredReceiver) {
               unregisterReceiver();
           }

           if (!mEnabled && mOnPowerSave) {
               if (mPowerSave && !mServiceRunning) {
                   mContext.startService(mSmartPixelsService);
                   mServiceRunning = true;
                   Log.d(TAG, "Started Smart Pixels Service by Power Save enable");
               } else if (!mPowerSave && mServiceRunning) {
                   mContext.stopService(mSmartPixelsService);
                   mServiceRunning = false;
                   Log.d(TAG, "Stopped Smart Pixels Service by Power Save disable");
               } else if (mPowerSave && mServiceRunning) {
                   mContext.stopService(mSmartPixelsService);
                   mContext.startService(mSmartPixelsService);
                   Log.d(TAG, "Restarted Smart Pixels Service by Power Save enable");
               }
           } else if (mEnabled && !mServiceRunning) {
               mContext.startService(mSmartPixelsService);
               mServiceRunning = true;
               Log.d(TAG, "Started Smart Pixels Service by enable");
           } else if (!mEnabled && mServiceRunning) {
               mContext.stopService(mSmartPixelsService);
               mServiceRunning = false;
               Log.d(TAG, "Stopped Smart Pixels Service by disable");
           } else if (mEnabled && mServiceRunning) {
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
