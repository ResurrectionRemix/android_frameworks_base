/*
 * Copyright (C) 2016, ParanoidAndroid Project
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

package com.android.server.policy;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AlertSliderObserver extends UEventObserver {
    private static final String TAG = AlertSliderObserver.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private int mState;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final Vibrator mVibrator;
    private final WakeLock mWakeLock;

    private boolean mHasVibrator;

    public AlertSliderObserver(Context context) {
        mContext = context;
        mNotificationManager
               = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = mVibrator != null && mVibrator.hasVibrator();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlertSliderObserver");
        init();
    }

    protected void startObserving(int pathId) {
        String matchPath = mContext.getResources().getString(pathId);
        if (!TextUtils.isEmpty(matchPath)) {
            super.startObserving(matchPath);
        }
    }

    @Override
    public void onUEvent(UEventObserver.UEvent event) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Slog.v(TAG, "Switch UEVENT: " + event.toString());
        }

        try {
            int state = Integer.parseInt(event.get("SWITCH_STATE"));
            if (state != mState) {
                mState = state;
                update();
            }
        } catch (NumberFormatException e) {
            Slog.e(TAG, "Could not parse switch state from event " + event);
        }
    }

    private void init() {
        try {
            final String path = mContext.getResources().getString(
                    com.android.internal.R.string.alert_slider_state_path);
            FileReader file = new FileReader(path);
            BufferedReader br = new BufferedReader(file);
            String value = br.readLine();
            file.close();
            br.close();
            mState = Integer.valueOf(value);
            update();
        } catch (IOException e) {
            Slog.w(TAG, "This device does not have an Alert Slider");
            stopObserving();
        }
    }

    protected void update() {
        // Acquire wakelock when slider state changes
        mWakeLock.acquire();
        mHandler.sendEmptyMessageDelayed(mState, 100);
    }

    private Handler mHandler = new Handler(Looper.myLooper(), null, true) {
        @Override
        public void handleMessage(Message msg) {
            final boolean inverted = isOrderInverted();
            final int silentMode = getSilentMode();
            switch (mState) {
                case 1:
                    setZenMode(inverted ? Settings.Global.ZEN_MODE_OFF : silentMode);
                    break;
                case 2:
                    setZenMode(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                    break;
                case 3:
                    setZenMode(inverted ? silentMode : Settings.Global.ZEN_MODE_OFF);
                    break;
            }
        }
    };

    private void setZenMode(int zenMode) {
        if (mHasVibrator) {
            mVibrator.vibrate(50);
        }
        mNotificationManager.setZenMode(zenMode, null, TAG);
        // Release wakelock when zen mode is set
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    // Check if ordered has been set to inverted.
    private boolean isOrderInverted() {
        return Settings.System.getIntForUser(
                    mContext.getContentResolver(), Settings.System.ALERT_SLIDER_ORDER, 0,
                    UserHandle.USER_CURRENT) != 0;
    }

    // Get silent mode for silent state:
    // - ALARMS ONLY (Default)
    // - TOTAL SILENCE
    private int getSilentMode() {
        int silentMode = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.ALERT_SLIDER_SILENT_MODE, 0,
                UserHandle.USER_CURRENT);
        return silentMode != 0 ? Settings.Global.ZEN_MODE_NO_INTERRUPTIONS : Settings.Global.ZEN_MODE_ALARMS;
    }
}
