/*
 * Copyright (C) 2018 CypherOS
 * Copyright (C) 2018 PixelExperience
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */
package com.android.systemui.ambient.play;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

import com.android.systemui.R;

public class AmbientIndicationManager {

    private static final String TAG = "AmbientIndicationManager";
    private Context mContext;
    private ContentResolver mContentResolver;
    private boolean isRecognitionEnabled;
    private boolean isRecognitionEnabledOnKeyguard;
    private boolean isRecognitionNotificationEnabled;
    private RecognitionObserver mRecognitionObserver;
    private String ACTION_UPDATE_AMBIENT_INDICATION = "update_ambient_indication";
    private AlarmManager mAlarmManager;
    private BatteryManager mBatteryManager;
    private int NO_MATCH_COUNT = 0;
    private int lastAlarmInterval = 0;
    private long lastUpdated = 0;
    private boolean isRecognitionObserverBusy = false;
    public boolean DEBUG = false;

    private List<AmbientIndicationManagerCallback> mCallbacks;

    private boolean needsUpdate() {
        if (!isRecognitionEnabled) {
            return false;
        }
        return System.currentTimeMillis() - lastUpdated > lastAlarmInterval;
    }

    private void updateAmbientPlayAlarm(boolean cancelOnly) {
        int UPDATE_AMBIENT_INDICATION_PENDING_INTENT_CODE = 96545687;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, UPDATE_AMBIENT_INDICATION_PENDING_INTENT_CODE, new Intent(ACTION_UPDATE_AMBIENT_INDICATION), 0);
        mAlarmManager.cancel(pendingIntent);
        if (cancelOnly) {
            return;
        }
        lastAlarmInterval = 0;
        if (!isRecognitionEnabled) return;
        int networkStatus = getNetworkStatus();
        int duration = 150000; // Default

        /*
         * Let's try to reduce battery consumption here.
         *  - If device is charging then let's not worry about scan interval and let's scan every 2 minutes, else
         *  - If device is not able to find matches for 20 consecutive times.
         *    then chances are that user is probably not listening to music or maybe sleeping
         *    So, Bump the scan interval to 5 minutes, else
         *  - If device is on Mobile Data or anything else then let's set it to 3 minutes.
         */

        if (mBatteryManager.isCharging()) {
            duration = 120000;
        } else if (NO_MATCH_COUNT >= 20) {
            duration = 300000;
        } else if (networkStatus == 1 || networkStatus == 2) {
            duration = 180000;
        }
        lastAlarmInterval = duration;
        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + duration, pendingIntent);
    }

    public int getRecordingMaxTime(){
        return 10000; // 10 seconds
    }

    public int getAmbientClearViewInterval(){
        return 180000; // Interval to clean the view after song is detected. (Default 3 minutes)
    }

    public int getNetworkStatus() {
        final ConnectivityManager connectivityManager
                = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        final Network network = connectivityManager.getActiveNetwork();
        final NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        /*
         * Return -1 if We don't have any network connectivity
         * Return 0 if we are on WiFi  (desired)
         * Return 1 if we are on MobileData (Little less desired)
         * Return 2 if not sure which connection is user on but has network connectivity
         */
        // NetworkInfo object will return null in case device is in flight mode.
        if (activeNetworkInfo == null)
            return -1;
        else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            return 0;
        else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            return 1;
        else if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            return 2;
        else
            return -1;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (needsUpdate()) {
                    updateAmbientPlayAlarm(true);
                    startRecording();
                }
            } else if (ACTION_UPDATE_AMBIENT_INDICATION.equals(intent.getAction())) {
                updateAmbientPlayAlarm(true);
                startRecording();
            } else if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                lastUpdated = 0;
                lastAlarmInterval = 0;
                updateAmbientPlayAlarm(false);
            }
        }
    };

    public AmbientIndicationManager(Context context) {
        mContext = context;
        mCallbacks = new ArrayList<>();
        mContentResolver = context.getContentResolver();
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        mSettingsObserver.update();
        mRecognitionObserver = new RecognitionObserver(context, this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(ACTION_UPDATE_AMBIENT_INDICATION);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiver(broadcastReceiver, filter);
    }

    private void startRecording(){
        if (!isRecognitionObserverBusy && isRecognitionEnabled){
            isRecognitionObserverBusy = true;
            mRecognitionObserver.startRecording();
        }
    }

    public void unregister() {
        mCallbacks = new ArrayList<>();
        mSettingsObserver.unregister();
        mContext.unregisterReceiver(broadcastReceiver);
    }

    private SettingsObserver mSettingsObserver;
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.AMBIENT_RECOGNITION),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.AMBIENT_RECOGNITION_KEYGUARD),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.AMBIENT_RECOGNITION_NOTIFICATION),
                    false, this, UserHandle.USER_ALL);
        }

        void unregister() {
            mContentResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            update();
            if (uri.equals(Settings.System.getUriFor(Settings.System.AMBIENT_RECOGNITION))) {
                lastUpdated = 0;
                lastAlarmInterval = 0;
                dispatchSettingsChanged(Settings.System.AMBIENT_RECOGNITION, isRecognitionEnabled);
                updateAmbientPlayAlarm(false);
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.AMBIENT_RECOGNITION_KEYGUARD))) {
                dispatchSettingsChanged(Settings.System.AMBIENT_RECOGNITION_KEYGUARD, isRecognitionEnabledOnKeyguard);
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.AMBIENT_RECOGNITION_NOTIFICATION))) {
                dispatchSettingsChanged(Settings.System.AMBIENT_RECOGNITION_NOTIFICATION, isRecognitionNotificationEnabled);
            }
        }

        public void update() {
            isRecognitionEnabled = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.AMBIENT_RECOGNITION, 0, UserHandle.USER_CURRENT) != 0;
            isRecognitionEnabledOnKeyguard = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.AMBIENT_RECOGNITION_KEYGUARD, 1, UserHandle.USER_CURRENT) != 0;
            isRecognitionNotificationEnabled = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.AMBIENT_RECOGNITION_NOTIFICATION, 1, UserHandle.USER_CURRENT) != 0;
        }
    }

    public void unRegisterCallback(AmbientIndicationManagerCallback callback) {
        mCallbacks.remove(callback);
    }

    public void registerCallback(AmbientIndicationManagerCallback callback) {
        mCallbacks.add(callback);
        callback.onSettingsChanged(Settings.System.AMBIENT_RECOGNITION, isRecognitionEnabled);
        callback.onSettingsChanged(Settings.System.AMBIENT_RECOGNITION_KEYGUARD, isRecognitionEnabledOnKeyguard);
        callback.onSettingsChanged(Settings.System.AMBIENT_RECOGNITION_NOTIFICATION, isRecognitionNotificationEnabled);
    }

    public void dispatchRecognitionResult(RecognitionObserver.Observable observed) {
        isRecognitionObserverBusy = false;
        lastUpdated = System.currentTimeMillis();
        NO_MATCH_COUNT = 0;
        if (isRecognitionNotificationEnabled) {
            showNotification(observed.Song, observed.Artist);
        }
        for (AmbientIndicationManagerCallback cb : mCallbacks) {
            try {
                cb.onRecognitionResult(observed);
            } catch (Exception ignored) {
            }
        }
        updateAmbientPlayAlarm(false);
    }

    public void dispatchRecognitionNoResult() {
        isRecognitionObserverBusy = false;
        lastUpdated = System.currentTimeMillis();
        if (!mBatteryManager.isCharging()){
            NO_MATCH_COUNT++;
        }else{
            NO_MATCH_COUNT = 0;
        }
        for (AmbientIndicationManagerCallback cb : mCallbacks) {
            try {
                cb.onRecognitionNoResult();
            } catch (Exception ignored) {
            }
        }
        updateAmbientPlayAlarm(false);
    }

    public void dispatchRecognitionError() {
        isRecognitionObserverBusy = false;
        lastUpdated = System.currentTimeMillis();
        if (!mBatteryManager.isCharging()){
            NO_MATCH_COUNT++;
        }else{
            NO_MATCH_COUNT = 0;
        }
        for (AmbientIndicationManagerCallback cb : mCallbacks) {
            try {
                cb.onRecognitionError();
            } catch (Exception ignored) {
            }
        }
        updateAmbientPlayAlarm(false);
    }

    private void dispatchSettingsChanged(String key, boolean newValue) {
        for (AmbientIndicationManagerCallback cb : mCallbacks) {
            try {
                cb.onSettingsChanged(key, newValue);
            } catch (Exception ignored) {
            }
        }
    }

    private void showNotification(String song, String artist) {
        Notification.Builder mBuilder =
                new Notification.Builder(mContext, "music_recognized_channel");
        final Bundle extras = Bundle.forPair(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                mContext.getResources().getString(R.string.ambient_recognition_notification));
        mBuilder.setSmallIcon(R.drawable.ic_music_note_24dp);
        mBuilder.setContentText(String.format(mContext.getResources().getString(
                R.string.ambient_recognition_information), song, artist));
        mBuilder.setColor(mContext.getResources().getColor(com.android.internal.R.color.system_notification_accent_color));
        mBuilder.setAutoCancel(false);
        mBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        mBuilder.setLocalOnly(true);
        mBuilder.setShowWhen(true);
        mBuilder.setWhen(System.currentTimeMillis());
        mBuilder.setTicker(String.format(mContext.getResources().getString(
                R.string.ambient_recognition_information), song, artist));
        mBuilder.setExtras(extras);

        NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel("music_recognized_channel",
                mContext.getResources().getString(R.string.ambient_recognition_notification),
                NotificationManager.IMPORTANCE_MIN);
        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.notify(122306791, mBuilder.build());
    }
}
