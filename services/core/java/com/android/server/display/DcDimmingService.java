/*
 * Copyright (C) 2020 Paranoid Android
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

package com.android.server.display;

import static android.hardware.display.DcDimmingManager.MODE_AUTO_OFF;
import static android.hardware.display.DcDimmingManager.MODE_AUTO_TIME;
import static android.hardware.display.DcDimmingManager.MODE_AUTO_BRIGHTNESS;
import static android.hardware.display.DcDimmingManager.MODE_AUTO_FULL;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DcDimmingManager;
import android.hardware.display.IDcDimmingManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.os.BatteryStatsImpl.SystemClocks;
import com.android.server.SystemService;
import com.android.server.twilight.TwilightListener;
import com.android.server.twilight.TwilightManager;
import com.android.server.twilight.TwilightState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class DcDimmingService extends SystemService {
    private static final String TAG = "DcDimmingService";

    private final Context mContext;
    private final Handler mHandler = new Handler();
    private final Object mLock = new Object();
    private final String mDcDimmingNode;
    private TwilightManager mTwilightManager;
    private TwilightState mTwilightState;
    private SettingsObserver mSettingsObserver;

    private int mBrightness;
    private int mBrightnessAvg;
    private int mBrightnessThreshold;
    private int mAutoMode;
    private boolean mEnabled;
    private boolean mDcOn;
    private boolean mScreenOff;
    private boolean mPendingOnScreenOn;
    private boolean mForce;

    private long mAvgStartTime;

    private final ArrayMap<Integer, Long> mBrightnessMap = new ArrayMap<>(20);

    private final Runnable mBrightnessRunnable = new Runnable() {
        @Override
        public void run() {
            boolean prevState = shouldEnableDc();
            updateBrightnessAvg();
            Slog.v(TAG, "mBrightnessRunnable mBrightnessAvg:" + mBrightnessAvg);
            mPendingOnScreenOn = mScreenOff;
            if (!mHandler.hasCallbacks(mBrightnessRunnable)) {
                mHandler.postDelayed(mBrightnessRunnable, 60000);
            }
            updateForcing(mDcOn);
            if (!mScreenOff) {
                synchronized (mLock) {
                    updateLocked(mDcOn);
                }
            }
        }
    };

    private final TwilightListener mTwilightListener = new TwilightListener() {
        @Override
        public void onTwilightStateChanged(@Nullable TwilightState state) {
            Slog.v(TAG, "onTwilightStateChanged state:" + state);
            boolean changed = mTwilightState == null || (state.isNight() != mTwilightState.isNight());
            mPendingOnScreenOn = mScreenOff && changed;
            mTwilightState = state;
            if (mAutoMode == MODE_AUTO_TIME || mAutoMode == MODE_AUTO_FULL) {
                updateForcing(mDcOn);
                if (!mScreenOff && changed) {
                    synchronized (mLock) {
                        updateLocked(mDcOn);
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Slog.v(TAG, "mIntentReceiver ACTION_SCREEN_ON");
                mScreenOff = false;
                if (!mPendingOnScreenOn && !mHandler.hasCallbacks(mBrightnessRunnable)) {
                    mHandler.postDelayed(mBrightnessRunnable, 500);
                }
                mHandler.postDelayed(() -> {
                    if (mPendingOnScreenOn) {
                        synchronized (mLock) {
                            updateLocked(mDcOn);
                        }
                    }
                    mPendingOnScreenOn = false;
                }, 300);
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                Slog.v(TAG, "mIntentReceiver ACTION_SCREEN_OFF");
                mScreenOff = true;
                mHandler.removeCallbacks(mBrightnessRunnable);
            }
        }
    };

    public DcDimmingService(Context context) {
        super(context);
        mContext = context;
        mDcDimmingNode = context.getResources().getString(
                com.android.internal.R.string.config_deviceDcDimmingSysfsNode);
        mSettingsObserver = new SettingsObserver(mHandler);
        final IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mIntentReceiver, intentFilter);
    }

    @Override
    public void onStart() {
        Slog.v(TAG, "Starting DcDimmingService");
        publishBinderService(Context.DC_DIM_SERVICE, mService);
        publishLocalService(DcDimmingService.class, this);
        mEnabled = nodeExists() && nodeReadable() && nodeWritable();
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            Slog.v(TAG, "onBootPhase PHASE_SYSTEM_SERVICES_READY");
            mTwilightManager = getLocalService(TwilightManager.class);
        } else if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            Slog.v(TAG, "onBootPhase PHASE_BOOT_COMPLETED");
            mTwilightManager.registerListener(mTwilightListener, mHandler);
            mTwilightState = mTwilightManager.getLastTwilightState();
        }
    }

    @Override
    public void onUnlockUser(int userHandle) {
        mEnabled = nodeExists() && nodeReadable() && nodeWritable();
        Slog.v(TAG, "onUnlockUser mEnabled:" + mEnabled);
        if (!mEnabled) {
            return;
        }
        mSettingsObserver.observe();
        mSettingsObserver.init();
        synchronized (mLock) {
            updateLocked(mDcOn);
        }
    }

    private void updateLocked(boolean enable) {
        if (!mEnabled) {
            return;
        }
        String nodeVal = readSysfsNode();
        Slog.v(TAG, "updateLocked mForce:" + mForce + " enable:" + enable
                + " nodeVal:" + nodeVal);
        if (nodeVal == null) {
            return;
        }
        boolean on = nodeVal.equals("1");
        if (mAutoMode != MODE_AUTO_OFF) {
            if (!mHandler.hasCallbacks(mBrightnessRunnable)) {
                mHandler.postDelayed(mBrightnessRunnable, 30000);
            }
            if (mTwilightState == null && (mAutoMode == MODE_AUTO_TIME
                    || mAutoMode == MODE_AUTO_FULL)) {
                mTwilightState = mTwilightManager.getLastTwilightState();
                if (enable != on) {
                    writeSysfsNode(enable);
                }
                return;
            }
            Slog.v(TAG, "updateLocked mTwilightState:" + mTwilightState
                    + " mBrightnessAvg:" + mBrightnessAvg
                    + " mBrightnessThreshold:" + mBrightnessThreshold);
            if (mForce) {
                if (enable != on) {
                    writeSysfsNode(enable);
                }
            } else {
                writeSysfsNode(shouldEnableDc());
            }
        } else {
            if (on != enable) {
                writeSysfsNode(enable);
            }
            mHandler.removeCallbacks(mBrightnessRunnable);
        }
    }

    private boolean shouldEnableDc() {
        switch (mAutoMode) {
            case MODE_AUTO_TIME:
                return shouldEnableDcTime();
            case MODE_AUTO_BRIGHTNESS:
                return shouldEnableDcBrightness();
            case MODE_AUTO_FULL:
                return shouldEnableDcFull();
            default:
                return false;
        }
    }

    private boolean shouldEnableDcTime() {
        return mTwilightState != null && mTwilightState.isNight();
    }

    private boolean shouldEnableDcBrightness() {
        return mBrightnessAvg != 0 && mBrightnessAvg <= mBrightnessThreshold;
    }

    private boolean shouldEnableDcFull() {
        return mTwilightState != null && mTwilightState.isNight()
                && (mBrightnessAvg != 0 && mBrightnessAvg <= mBrightnessThreshold);
    }

    private void updateForcing(boolean enable) {
        mForce = (enable ^ shouldEnableDc()) && mForce;
    }

    private final IDcDimmingManager.Stub mService = new IDcDimmingManager.Stub() {
        @Override
        public void setAutoMode(int mode) {
            synchronized (mLock) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    if (mAutoMode != mode) {
                        Slog.v(TAG, "setAutoMode(" + mode + ")");
                        mAutoMode = mode;
                        Settings.System.putIntForUser(mContext
                                .getContentResolver(),
                                Settings.System.DC_DIMMING_AUTO_MODE, mode,
                                UserHandle.USER_CURRENT);
                        updateLocked(mDcOn);
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }

        @Override
        public void setDcDimming(boolean enable) {
            synchronized (mLock) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    mForce = shouldEnableDc() ^ enable;
                    updateLocked(enable);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }

        @Override
        public int getAutoMode() {
            return mAutoMode;
        }

        @Override
        public boolean isAvailable() {
            return mEnabled;
        }

        @Override
        public boolean isDcDimmingOn() {
            return mDcOn;
        }

        @Override
        public void setBrightnessThreshold(int thresh) {
            mBrightnessThreshold = thresh;
            mPendingOnScreenOn = mScreenOff;
            if (!mScreenOff) {
                synchronized (mLock) {
                    final long ident = Binder.clearCallingIdentity();
                    try {
                        Settings.System.putIntForUser(
                                mContext.getContentResolver(),
                                Settings.System.DC_DIMMING_BRIGHTNESS,
                                thresh, UserHandle.USER_CURRENT);
                        updateLocked(mDcOn);
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                }
            }
        }

        @Override
        public int getBrightnessThreshold() {
            return mBrightnessThreshold;
        }

        @Override
        public boolean isForcing() {
            return (mAutoMode != MODE_AUTO_OFF) && mForce;
        }

        @Override
        public void restoreAutoMode() {
            synchronized (mLock) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    mForce = false;
                    updateLocked(mDcOn);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    };

    private class SettingsObserver extends ContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_BRIGHTNESS), false, this,
                    UserHandle.USER_ALL);
        }

        void init() {
            mBrightness = Settings.System.getIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 0,
                    UserHandle.USER_CURRENT);
            mBrightnessThreshold = Settings.System.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.System.DC_DIMMING_BRIGHTNESS, 0,
                    UserHandle.USER_CURRENT);
            mAutoMode = Settings.System.getIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.DC_DIMMING_AUTO_MODE, 0,
                    UserHandle.USER_CURRENT);
            mDcOn = Settings.System.getIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.DC_DIMMING_STATE, 0,
                    UserHandle.USER_CURRENT) == 1;
            mAvgStartTime = SystemClock.uptimeMillis();
            mBrightnessMap.put(mBrightness, mAvgStartTime);
        }

        @Override
        public void onChange(boolean selfChange) {
            long currTime = SystemClock.uptimeMillis();
            if (mBrightnessMap.containsKey(mBrightness)) {
                mBrightnessMap.put(mBrightness, currTime - mBrightnessMap.get(mBrightness));
            }
            mBrightness = Settings.System.getIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 0,
                    UserHandle.USER_CURRENT);
            Slog.v(TAG, "onChange brightness:" + mBrightness);
            mBrightnessMap.put(mBrightness, currTime);
            if (mBrightnessMap.size() == 20) {
                updateBrightnessAvg();
            }
        }
    }

    private void updateBrightnessAvg() {
        Slog.v(TAG, "updateBrightnessAvg()");
        final int size = mBrightnessMap.size();
        long totalTime = SystemClock.uptimeMillis() - mAvgStartTime;
        float tmpFrac = 0.0f;
        for (int i = 0; i < size; i++) {
            int brght = mBrightnessMap.keyAt(i);
            long diffTime = mBrightnessMap.valueAt(i);
            if (brght == mBrightness) {
                diffTime = SystemClock.uptimeMillis() - diffTime;
                mBrightnessMap.put(brght, SystemClock.uptimeMillis());
            }
            tmpFrac += (float) brght * ((float) diffTime/totalTime);
        }
        ArrayList<Integer> c = new ArrayList<>(1);
        c.add(mBrightness);
        mBrightnessMap.retainAll(c);
        mAvgStartTime = SystemClock.uptimeMillis();
        mBrightnessAvg = (int) tmpFrac;
    }

    private boolean writeSysfsNode(boolean enable) {
        Slog.v(TAG, "writeSysfsNode enable:" + enable);
        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(mDcDimmingNode));
            writer.write(enable ? "1" : "0");
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "No such file " + mDcDimmingNode + " for writing", e);
            return false;
        } catch (IOException e) {
            Slog.e(TAG, "Could not write to file " + mDcDimmingNode, e);
            return false;
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                // Ignored, not much we can do anyway
            }
        }
        if (mDcOn != enable) {
            mDcOn = enable;
            Settings.System.putIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.DC_DIMMING_STATE, enable ? 1 : 0,
                    UserHandle.USER_CURRENT);
        }
        return true;
    }

    private String readSysfsNode() {
        String line = null;
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(mDcDimmingNode), 512);
            line = reader.readLine();
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "No such file " + mDcDimmingNode + " for reading", e);
        } catch (IOException e) {
            Slog.e(TAG, "Could not read from file " + mDcDimmingNode, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // Ignored, not much we can do anyway
            }
        }
        return line;
    }

    private boolean nodeExists() {
        final File file = new File(mDcDimmingNode);
        return file.exists();
    }

    private boolean nodeReadable() {
        final File file = new File(mDcDimmingNode);
        return file.exists() && file.canRead();
    }

    private boolean nodeWritable() {
        final File file = new File(mDcDimmingNode);
        return file.exists() && file.canWrite();
    }
}