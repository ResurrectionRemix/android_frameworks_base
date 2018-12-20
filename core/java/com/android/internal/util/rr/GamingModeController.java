/**
 * Copyright (C) 2019 The PixelExperience project
 * Copyright (C) 2019 The Syberia project
 * Copyright (C) 2019 crDroid Android Project
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

package com.android.internal.util.rr;

import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.Global.ZEN_MODE_OFF;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import com.android.internal.R;
import com.android.internal.notification.SystemNotificationChannels;

import android.provider.Settings;
import android.provider.Settings.Global;
import android.widget.Toast;

public class GamingModeController {
    private Context mContext;
    final Context mUiContext;
    ApplicationInfo appInfo;
    AudioManager mAudioManager;
    NotificationManager mNotificationManager;
    PackageManager pm;
    private boolean mGamingModeEnabled;
    private boolean mDynamicGamingMode;
    private Toast toast;
    private String mGamingPackageList;
    private ArrayList<String> mGameApp = new ArrayList<String>();

    private boolean mGamingModeActivated;
    private static int mRingerState;
    private static int mZenState;
    private static int mHwKeysState;
    private static int mAdaptiveBrightness;

    public static final String GAMING_MODE_TURN_OFF = "android.intent.action.GAMING_MODE_TURN_OFF";
    public static final String GAMING_MODE_TURN_ON = "android.intent.action.GAMING_MODE_TURN_ON";

    private static final String TAG = "GamingModeController";
    private static final int GAMING_NOTIFICATION_ID = 420;

    public GamingModeController(Context context) {
        mContext = context;
        mUiContext = ActivityThread.currentActivityThread().getSystemUiContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        mGamingModeEnabled = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.GAMING_MODE_ENABLED, 0) == 1;
        mDynamicGamingMode = Settings.System.getInt(mContext.getContentResolver(),
                               Settings.System.GAMING_MODE_DYNAMIC_STATE, 0) == 1;
        mGamingModeActivated = Settings.System.getInt(mContext.getContentResolver(),
                               Settings.System.GAMING_MODE_ACTIVE, 0) == 1;
        parsePackageList();

        SettingsObserver observer = new SettingsObserver(
                new Handler(Looper.getMainLooper()));
        observer.observe();
    }

    private void parsePackageList() {
        final String gamingApp = Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_VALUES);
        splitAndAddToArrayList(mGameApp, gamingApp, "\\|");
    }

    private void savePackageList(ArrayList<String> arrayList) {
        String setting = Settings.System.GAMING_MODE_VALUES;

        List<String> settings = new ArrayList<String>();
        for (String app : arrayList) {
            settings.add(app.toString());
        }
        final String value = TextUtils.join("|", settings);
            if (TextUtils.equals(setting, Settings.System.GAMING_MODE_VALUES)) {
                mGamingPackageList = value;
            }
        Settings.System.putString(mContext.getContentResolver(),
                setting, value);
    }

    private void addGameApp(String packageName) {
        if (!mGameApp.contains(packageName)) {
            mGameApp.add(packageName);
            savePackageList(mGameApp);
        }
    }

    private boolean isGameApp(String packageName) {
        return (mGameApp.contains(packageName));
    }

    public boolean isGamingModeEnabled() {
        return mGamingModeEnabled;
    }

    public void notePackageUninstalled(String pkgName) {
        // remove from list
        if (mGameApp.remove(pkgName)) {
            savePackageList(mGameApp);
        }
    }

    public boolean topAppChanged(String packageName) {
        if (isGameApp(packageName))
            return true;

        if (!mDynamicGamingMode)
            return false;

        appInfo = getAppInfoFromPkgName(mContext, packageName);
        if (appInfo != null && (appInfo.category == ApplicationInfo.CATEGORY_GAME ||
            (appInfo.flags & ApplicationInfo.FLAG_IS_GAME) == ApplicationInfo.FLAG_IS_GAME)) {
            addGameApp(packageName);
            return true;
        }
        return false;
    }

    private void splitAndAddToArrayList(ArrayList<String> arrayList,
            String baseString, String separator) {
        // clear first
        arrayList.clear();
        if (baseString != null) {
            final String[] array = TextUtils.split(baseString, separator);
            for (String item : array) {
                arrayList.add(item.trim());
            }
        }
    }

    private static ApplicationInfo getAppInfoFromPkgName(Context context, String Packagename) {
      try {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo info = packageManager.getApplicationInfo(Packagename, PackageManager.GET_META_DATA);
        return info;
      } catch (PackageManager.NameNotFoundException e) {
        e.printStackTrace();
        return null;
      }
    }

    private int getZenMode() {
        if (mNotificationManager == null) {
             mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }
        try {
            return mNotificationManager.getZenMode();
        } catch (Exception e) {
             return -1;
        }
    }

    private void setZenMode(int mode) {
        if (mNotificationManager == null) {
             mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }
        try {
            mNotificationManager.setZenMode(mode, null, TAG);
        } catch (Exception e) {
        }
    }

    private int getRingerModeInternal() {
        if (mAudioManager == null) {
             mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
        try {
            return mAudioManager.getRingerModeInternal();
        } catch (Exception e) {
             return -1;
        }
    }

    private void setRingerModeInternal(int mode) {
        if (mAudioManager == null) {
             mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
        try {
            mAudioManager.setRingerModeInternal(mode);
        } catch (Exception e) {
        }
    }

    private void showToast(String msg, int duration) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
        @Override
        public void run() {
            if (toast != null) toast.cancel();
            toast = Toast.makeText(mUiContext, msg, duration);
            toast.show();
            }
        });
    }

    private void enableGamingFeatures() {
        if (!ActivityManager.isSystemReady())
            return;
        // Lock brightness
        boolean lockBrightness = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_MANUAL_BRIGHTNESS_TOGGLE, 1) == 1;
        if (lockBrightness) {
            final boolean isAdaptiveEnabledByUser = Settings.System.getInt(mContext.getContentResolver(),
                              Settings.System.SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL) == SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            mAdaptiveBrightness = isAdaptiveEnabledByUser ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : SCREEN_BRIGHTNESS_MODE_MANUAL;
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
        }
        /* HW Buttons
        boolean disableHwKeys = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_HW_KEYS_TOGGLE, 0) == 1;
        if (disableHwKeys) {
            mHwKeysState = Settings.Secure.getInt(mContext.getContentResolver(),
                              Settings.Secure.HARDWARE_KEYS_DISABLE, 0);
            Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HARDWARE_KEYS_DISABLE, 1);
        }*/

        // Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
        int ringerMode = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.GAMING_MODE_RINGER_MODE, 0);
        if (ringerMode != 0) {
            mRingerState = getRingerModeInternal();
            mZenState = getZenMode();
            if (ringerMode == 1) {
                setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                setZenMode(ZEN_MODE_OFF);
            } else if (ringerMode == 2) {
                setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                setZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
            } else if (ringerMode == 3) {
                setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                setZenMode(ZEN_MODE_OFF);
            }
        }

        int mNotifications = Settings.System.getInt(mContext.getContentResolver(),
                          Settings.System.GAMING_MODE_NOTIFICATIONS, 3);
        if(mNotifications == 2 || mNotifications == 3)
            showToast(mContext.getResources().getString(R.string.gaming_mode_enabled_toast), Toast.LENGTH_LONG);
        if(mNotifications == 1 || mNotifications == 3)
            addNotification();
    }

    private void disableGamingFeatures() {
        if (!ActivityManager.isSystemReady())
             return;
        ContentResolver resolver = mContext.getContentResolver();
        boolean lockBrightness = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_MANUAL_BRIGHTNESS_TOGGLE, 1) == 1;
        if (lockBrightness) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, mAdaptiveBrightness);
        }
        /*boolean disableHwKeys = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_HW_KEYS_TOGGLE, 0) == 1;
        if (disableHwKeys) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HARDWARE_KEYS_DISABLE, mHwKeysState);
        }*/
        int ringerMode = Settings.System.getInt(mContext.getContentResolver(),
                 Settings.System.GAMING_MODE_RINGER_MODE, 0);
        if (ringerMode != 0 && (mRingerState != getRingerModeInternal() ||
                mZenState != getZenMode())) {
            setRingerModeInternal(mRingerState);
            setZenMode(mZenState);
        }
        int mNotifications = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.GAMING_MODE_NOTIFICATIONS, 3);
        if(mNotifications == 2 || mNotifications == 3)
            showToast(mContext.getResources().getString(R.string.gaming_mode_disabled_toast), Toast.LENGTH_LONG);

        mNotificationManager.cancel(GAMING_NOTIFICATION_ID);
    }

    private void addNotification() {
        final Resources r = mContext.getResources();
        Intent intent = new Intent(GAMING_MODE_TURN_OFF);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Display a notification
        Notification.Builder builder = new Notification.Builder(mContext, SystemNotificationChannels.GAMING)
            .setTicker(r.getString(com.android.internal.R.string.gaming_mode_notification_title))
            .setContentTitle(r.getString(com.android.internal.R.string.gaming_mode_notification_title))
            .setContentText(r.getString(com.android.internal.R.string.gaming_mode_notification_content))
            .setSmallIcon(com.android.internal.R.drawable.ic_gaming_notif)
            .setWhen(java.lang.System.currentTimeMillis())
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false);

        Notification notif = builder.build();
        mNotificationManager.notify(GAMING_NOTIFICATION_ID, notif);
    }

    public boolean isGamingModeActivated() {
      return mGamingModeActivated;
    }

    private void activateGamingMode(boolean enabled) {
        if (mGamingModeActivated == enabled && mGamingModeEnabled)
            return;
        mGamingModeActivated = enabled && mGamingModeEnabled;
        if (mGamingModeActivated) {
            enableGamingFeatures();
        } else {
            disableGamingFeatures();
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_ENABLED), false, this,
                    UserHandle.USER_ALL);

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_ACTIVE), false, this,
                    UserHandle.USER_ALL);

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_VALUES), false, this,
                    UserHandle.USER_ALL);

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_DYNAMIC_STATE), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                             Settings.System.GAMING_MODE_VALUES))) {
                parsePackageList();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_DYNAMIC_STATE))) {
                mDynamicGamingMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_DYNAMIC_STATE, 0) == 1;
            } else if (uri.equals(Settings.System.getUriFor(
                                   Settings.System.GAMING_MODE_ENABLED))) {
                mGamingModeEnabled = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_ENABLED, 0) == 1;
                if (!mGamingModeEnabled && mGamingModeActivated)
                    Settings.System.putInt(mContext.getContentResolver(),
                         Settings.System.GAMING_MODE_ACTIVE, 0);
            } else if (uri.equals(Settings.System.getUriFor(
                                   Settings.System.GAMING_MODE_ACTIVE))) {
                boolean enable = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_ACTIVE, 0) == 1;
                activateGamingMode(enable);
            }
        }
    }
}
