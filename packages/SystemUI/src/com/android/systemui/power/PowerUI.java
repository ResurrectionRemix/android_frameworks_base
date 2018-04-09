/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.power;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HardwarePropertiesManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.phone.StatusBar;

import lineageos.providers.LineageSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

public class PowerUI extends SystemUI {
    static final String TAG = "PowerUI";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final long TEMPERATURE_INTERVAL = 30 * DateUtils.SECOND_IN_MILLIS;
    private static final long TEMPERATURE_LOGGING_INTERVAL = DateUtils.HOUR_IN_MILLIS;
    private static final int MAX_RECENT_TEMPS = 125; // TEMPERATURE_LOGGING_INTERVAL plus a buffer

    private final Handler mHandler = new Handler();
    private final Receiver mReceiver = new Receiver();

    private PowerManager mPowerManager;
    private HardwarePropertiesManager mHardwarePropertiesManager;
    private WarningsUI mWarnings;
    private final Configuration mLastConfiguration = new Configuration();
    private int mBatteryLevel = 100;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int mPlugType = 0;
    private int mInvalidCharger = 0;

    private int mLowBatteryAlertCloseLevel;
    private final int[] mLowBatteryReminderLevels = new int[2];

    private long mScreenOffTime = -1;

    private float mThresholdTemp;
    private float[] mRecentTemps = new float[MAX_RECENT_TEMPS];
    private int mNumTemps;
    private long mNextLogTime;

    // We create a method reference here so that we are guaranteed that we can remove a callback
    // by using the same instance (method references are not guaranteed to be the same object
    // each time they are created).
    private final Runnable mUpdateTempCallback = this::updateTemperatureWarning;

    // For filtering ACTION_POWER_DISCONNECTED on boot
    private boolean mIgnoredFirstPowerBroadcast;

    public void start() {
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHardwarePropertiesManager = (HardwarePropertiesManager)
                mContext.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE);
        mScreenOffTime = mPowerManager.isScreenOn() ? -1 : SystemClock.elapsedRealtime();
        mWarnings = Dependency.get(WarningsUI.class);
        mLastConfiguration.setTo(mContext.getResources().getConfiguration());

        ContentObserver obs = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                updateBatteryWarningLevels();
            }
        };
        final ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL),
                false, obs, UserHandle.USER_ALL);
        resolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.ALERT_ON_CHARGED_LEVEL),
                false, obs, UserHandle.USER_ALL);
        updateBatteryWarningLevels();
        mReceiver.init();

        // Check to see if we need to let the user know that the phone previously shut down due
        // to the temperature being too high.
        showThermalShutdownDialog();

        initTemperatureWarning();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        final int mask = ActivityInfo.CONFIG_MCC | ActivityInfo.CONFIG_MNC;

        // Safe to modify mLastConfiguration here as it's only updated by the main thread (here).
        if ((mLastConfiguration.updateFrom(newConfig) & mask) != 0) {
            mHandler.post(this::initTemperatureWarning);
        }
    }

    void updateBatteryWarningLevels() {
        int critLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);

        final ContentResolver resolver = mContext.getContentResolver();
        int defWarnLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        int warnLevel = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel);
        if (warnLevel == 0) {
            warnLevel = defWarnLevel;
        }
        if (warnLevel < critLevel) {
            warnLevel = critLevel;
        }

        mLowBatteryReminderLevels[0] = warnLevel;
        mLowBatteryReminderLevels[1] = critLevel;
        mLowBatteryAlertCloseLevel = mLowBatteryReminderLevels[0]
                + mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_lowBatteryCloseWarningBump);

        int level = Settings.System.getIntForUser(resolver,
                Settings.System.ALERT_ON_CHARGED_LEVEL, -1, UserHandle.USER_CURRENT);
        mWarnings.setChargedLevel(level);
    }

    /**
     * Buckets the battery level.
     *
     * The code in this function is a little weird because I couldn't comprehend
     * the bucket going up when the battery level was going down. --joeo
     *
     * 1 means that the battery is "ok"
     * 0 means that the battery is between "ok" and what we should warn about.
     * less than 0 means that the battery is low
     */
    private int findBatteryLevelBucket(int level) {
        if (level >= mLowBatteryAlertCloseLevel) {
            return 1;
        }
        if (level > mLowBatteryReminderLevels[0]) {
            return 0;
        }
        final int N = mLowBatteryReminderLevels.length;
        for (int i=N-1; i>=0; i--) {
            if (level <= mLowBatteryReminderLevels[i]) {
                return -1-i;
            }
        }
        throw new RuntimeException("not possible!");
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            // Register for Intent broadcasts for...
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                final int oldBatteryLevel = mBatteryLevel;
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
                final int oldBatteryStatus = mBatteryStatus;
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                final int oldPlugType = mPlugType;
                mPlugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 1);
                final int oldInvalidCharger = mInvalidCharger;
                mInvalidCharger = intent.getIntExtra(BatteryManager.EXTRA_INVALID_CHARGER, 0);

                final boolean plugged = mPlugType != 0;
                final boolean oldPlugged = oldPlugType != 0;

                if (!mIgnoredFirstPowerBroadcast && plugged) {
                    mIgnoredFirstPowerBroadcast = true;
                }

                int oldBucket = findBatteryLevelBucket(oldBatteryLevel);
                int bucket = findBatteryLevelBucket(mBatteryLevel);

                if (DEBUG) {
                    Slog.d(TAG, "buckets   ....." + mLowBatteryAlertCloseLevel
                            + " .. " + mLowBatteryReminderLevels[0]
                            + " .. " + mLowBatteryReminderLevels[1]);
                    Slog.d(TAG, "level          " + oldBatteryLevel + " --> " + mBatteryLevel);
                    Slog.d(TAG, "status         " + oldBatteryStatus + " --> " + mBatteryStatus);
                    Slog.d(TAG, "plugType       " + oldPlugType + " --> " + mPlugType);
                    Slog.d(TAG, "invalidCharger " + oldInvalidCharger + " --> " + mInvalidCharger);
                    Slog.d(TAG, "bucket         " + oldBucket + " --> " + bucket);
                    Slog.d(TAG, "plugged        " + oldPlugged + " --> " + plugged);
                }

                mWarnings.update(mBatteryLevel, bucket, mScreenOffTime);
                if (oldInvalidCharger == 0 && mInvalidCharger != 0) {
                    Slog.d(TAG, "showing invalid charger warning");
                    mWarnings.showInvalidChargerWarning();
                    return;
                } else if (oldInvalidCharger != 0 && mInvalidCharger == 0) {
                    mWarnings.dismissInvalidChargerWarning();
                } else if (mWarnings.isInvalidChargerWarningShowing()) {
                    // if invalid charger is showing, don't show low battery
                    return;
                }

                boolean isPowerSaver = mPowerManager.isPowerSaveMode();
                if (!plugged
                        && !isPowerSaver
                        && (bucket < oldBucket || oldPlugged)
                        && mBatteryStatus != BatteryManager.BATTERY_STATUS_UNKNOWN
                        && bucket < 0) {

                    // only play SFX when the dialog comes up or the bucket changes
                    final boolean playSound = bucket != oldBucket || oldPlugged;
                    mWarnings.showLowBatteryWarning(playSound);
                } else if (isPowerSaver || plugged || (bucket > oldBucket && bucket > 0)) {
                    mWarnings.dismissLowBatteryWarning();
                } else {
                    mWarnings.updateLowBatteryWarning();
                }

                if (plugged && !oldPlugged
                        && (mPlugType == BatteryManager.BATTERY_PLUGGED_AC
                            || mPlugType == BatteryManager.BATTERY_PLUGGED_USB)) {
                    // "Wireless charging started" sound is handled by
                    // {@link com.android.server.power.Notifier#onWirelessChargingStarted()}
                    mWarnings.notifyBatteryPlugged();
                }

                if (!plugged && oldPlugged
                        && (oldPlugType == BatteryManager.BATTERY_PLUGGED_AC
                            || oldPlugType == BatteryManager.BATTERY_PLUGGED_USB)) {
                    mWarnings.notifyBatteryUnplugged();
                }

                if ((plugged && !oldPlugged
                        && (mPlugType == BatteryManager.BATTERY_PLUGGED_AC
                            || mPlugType == BatteryManager.BATTERY_PLUGGED_USB))
                            || (!plugged && oldPlugged
                            && (oldPlugType == BatteryManager.BATTERY_PLUGGED_AC
                            || oldPlugType == BatteryManager.BATTERY_PLUGGED_USB))) {
                    mWarnings.setPluggedState(plugged);
                }

            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOffTime = SystemClock.elapsedRealtime();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOffTime = -1;
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                mWarnings.userSwitched();
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                if (mIgnoredFirstPowerBroadcast) {
                    if (Settings.Global.getInt(mContext.getContentResolver(),
                            Settings.Global.CHARGING_SOUNDS_ENABLED, 0) == 1) {
                        playPowerNotificationSound();
                    }
                } else {
                    mIgnoredFirstPowerBroadcast = true;
                }
            } else {
                Slog.w(TAG, "unknown intent: " + intent);
            }
        }
    };

    private void initTemperatureWarning() {
        ContentResolver resolver = mContext.getContentResolver();
        Resources resources = mContext.getResources();
        if (Settings.Global.getInt(resolver, Settings.Global.SHOW_TEMPERATURE_WARNING,
                resources.getInteger(R.integer.config_showTemperatureWarning)) == 0) {
            return;
        }

        mThresholdTemp = Settings.Global.getFloat(resolver, Settings.Global.WARNING_TEMPERATURE,
                resources.getInteger(R.integer.config_warningTemperature));

        if (mThresholdTemp < 0f) {
            // Get the throttling temperature. No need to check if we're not throttling.
            float[] throttlingTemps = mHardwarePropertiesManager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                    HardwarePropertiesManager.TEMPERATURE_SHUTDOWN);
            if (throttlingTemps == null
                    || throttlingTemps.length == 0
                    || throttlingTemps[0] == HardwarePropertiesManager.UNDEFINED_TEMPERATURE) {
                return;
            }
            mThresholdTemp = throttlingTemps[0] -
                    resources.getInteger(R.integer.config_warningTemperatureTolerance);
        }

        setNextLogTime();

        // This initialization method may be called on a configuration change. Only one set of
        // ongoing callbacks should be occurring, so remove any now. updateTemperatureWarning will
        // schedule an ongoing callback.
        mHandler.removeCallbacks(mUpdateTempCallback);

        // We have passed all of the checks, start checking the temp
        updateTemperatureWarning();
    }

    private void showThermalShutdownDialog() {
        if (mPowerManager.getLastShutdownReason()
                == PowerManager.SHUTDOWN_REASON_THERMAL_SHUTDOWN) {
            mWarnings.showThermalShutdownWarning();
        }
    }

    @VisibleForTesting
    protected void updateTemperatureWarning() {
        float[] temps = mHardwarePropertiesManager.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        if (temps.length != 0) {
            float temp = temps[0];
            mRecentTemps[mNumTemps++] = temp;

            StatusBar statusBar = getComponent(StatusBar.class);
            if (statusBar != null && !statusBar.isDeviceInVrMode()
                    && temp >= mThresholdTemp) {
                logAtTemperatureThreshold(temp);
                mWarnings.showHighTemperatureWarning();
            } else {
                mWarnings.dismissHighTemperatureWarning();
            }
        }

        logTemperatureStats();

        mHandler.postDelayed(mUpdateTempCallback, TEMPERATURE_INTERVAL);
    }

    private void logAtTemperatureThreshold(float temp) {
        StringBuilder sb = new StringBuilder();
        sb.append("currentTemp=").append(temp)
                .append(",thresholdTemp=").append(mThresholdTemp)
                .append(",batteryStatus=").append(mBatteryStatus)
                .append(",recentTemps=");
        for (int i = 0; i < mNumTemps; i++) {
            sb.append(mRecentTemps[i]).append(',');
        }
        Slog.i(TAG, sb.toString());
    }

    /**
     * Calculates and logs min, max, and average
     * {@link HardwarePropertiesManager#DEVICE_TEMPERATURE_SKIN} over the past
     * {@link #TEMPERATURE_LOGGING_INTERVAL}.
     */
    private void logTemperatureStats() {
        if (mNextLogTime > System.currentTimeMillis() && mNumTemps != MAX_RECENT_TEMPS) {
            return;
        }

        if (mNumTemps > 0) {
            float sum = mRecentTemps[0], min = mRecentTemps[0], max = mRecentTemps[0];
            for (int i = 1; i < mNumTemps; i++) {
                float temp = mRecentTemps[i];
                sum += temp;
                if (temp > max) {
                    max = temp;
                }
                if (temp < min) {
                    min = temp;
                }
            }

            float avg = sum / mNumTemps;
            Slog.i(TAG, "avg=" + avg + ",min=" + min + ",max=" + max);
            MetricsLogger.histogram(mContext, "device_skin_temp_avg", (int) avg);
            MetricsLogger.histogram(mContext, "device_skin_temp_min", (int) min);
            MetricsLogger.histogram(mContext, "device_skin_temp_max", (int) max);
        }
        setNextLogTime();
        mNumTemps = 0;
    }

    private void setNextLogTime() {
        mNextLogTime = System.currentTimeMillis() + TEMPERATURE_LOGGING_INTERVAL;
    }

    private void playPowerNotificationSound() {
        String soundPath = LineageSettings.Global.getString(mContext.getContentResolver(),
                LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE);

        if (soundPath != null && !soundPath.equals("silent")) {
            Ringtone powerRingtone = RingtoneManager.getRingtone(mContext, Uri.parse(soundPath));
            if (powerRingtone != null) {
                powerRingtone.play();
            }
        }

        if (LineageSettings.Global.getInt(mContext.getContentResolver(),
                LineageSettings.Global.POWER_NOTIFICATIONS_VIBRATE, 0) == 1) {
            Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(250);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mLowBatteryAlertCloseLevel=");
        pw.println(mLowBatteryAlertCloseLevel);
        pw.print("mLowBatteryReminderLevels=");
        pw.println(Arrays.toString(mLowBatteryReminderLevels));
        pw.print("mBatteryLevel=");
        pw.println(Integer.toString(mBatteryLevel));
        pw.print("mBatteryStatus=");
        pw.println(Integer.toString(mBatteryStatus));
        pw.print("mPlugType=");
        pw.println(Integer.toString(mPlugType));
        pw.print("mInvalidCharger=");
        pw.println(Integer.toString(mInvalidCharger));
        pw.print("mScreenOffTime=");
        pw.print(mScreenOffTime);
        if (mScreenOffTime >= 0) {
            pw.print(" (");
            pw.print(SystemClock.elapsedRealtime() - mScreenOffTime);
            pw.print(" ago)");
        }
        pw.println();
        pw.print("soundTimeout=");
        pw.println(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.LOW_BATTERY_SOUND_TIMEOUT, 0));
        pw.print("bucket: ");
        pw.println(Integer.toString(findBatteryLevelBucket(mBatteryLevel)));
        pw.print("mThresholdTemp=");
        pw.println(Float.toString(mThresholdTemp));
        pw.print("mNextLogTime=");
        pw.println(Long.toString(mNextLogTime));
        mWarnings.dump(pw);
    }

    public interface WarningsUI {
        void update(int batteryLevel, int bucket, long screenOffTime);
        void dismissLowBatteryWarning();
        void showLowBatteryWarning(boolean playSound);
        void dismissInvalidChargerWarning();
        void showInvalidChargerWarning();
        void updateLowBatteryWarning();
        boolean isInvalidChargerWarningShowing();
        void dismissHighTemperatureWarning();
        void showHighTemperatureWarning();
        void showThermalShutdownWarning();
        void dump(PrintWriter pw);
        void userSwitched();
        void setChargedLevel(int level);
        void setPluggedState(boolean plugged);
    }
}

