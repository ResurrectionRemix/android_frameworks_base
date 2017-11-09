/*
 * Copyright (C) 2020 The AOSPA-Extended Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Parcel;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import com.android.server.SystemService;

public class ScreenStabilization extends SystemService {
    private static final String TAG = "ScreenStabilization";

    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float MAX_ACC = 10.0f;
    private static final float MAX_POS_SHIFT = 100.0f;
    private static final float MAX_ZOOM_FACTOR = 0.2f;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private SettingsObserver mSettingsObserver;
    private Context mContext;

    private boolean accListenerRegistered = false;

    private final float[] tempAcc = new float[3];
    private final float[] acc = new float[3];
    private final float[] velocity = new float[3];
    private final float[] position = new float[3];
    private long timestamp = 0;

    private boolean mEnable;
    private float mVelocityFriction = 0.3f;
    private float mPositionFriction = 0.1f;
    private float mLowPassAlpha = 0.85f;
    private int mVelocityAmpl = 10000;

    private int x = 0, y = 0;

    private IBinder flinger = null;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        public void register() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STABILIZATION_ENABLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STABILIZATION_VELOCITY_FRICTION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STABILIZATION_POSITION_FRICTION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STABILIZATION_LOWPASS_ALPHA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STABILIZATION_VELOCITY_AMPLITUDE),
                    false, this, UserHandle.USER_ALL);
            updateParameters();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateParameters();
        }
    }

    private void updateParameters() {
        ContentResolver resolver = mContext.getContentResolver();
        mEnable = (Settings.System.getIntForUser(resolver, Settings.System.STABILIZATION_ENABLE, 0, UserHandle.USER_CURRENT) == 1);
        if(mEnable) {
            mVelocityFriction = (float) Settings.System.getFloatForUser(resolver, Settings.System.STABILIZATION_VELOCITY_FRICTION, 0.1f, UserHandle.USER_CURRENT);
            mPositionFriction = (float) Settings.System.getFloatForUser(resolver, Settings.System.STABILIZATION_POSITION_FRICTION, 0.1f, UserHandle.USER_CURRENT);
            mLowPassAlpha = (float) Settings.System.getFloatForUser(resolver, Settings.System.STABILIZATION_POSITION_FRICTION, 0.9f, UserHandle.USER_CURRENT);;
            mVelocityAmpl = (int) Settings.System.getIntForUser(resolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 8000, UserHandle.USER_CURRENT);
        }
        if(!accListenerRegistered && mEnable) {
            reset();
            registerAccListener();
        }else if(!mEnable) {
            unregisterAccListener();
            reset();
            setSurfaceFlingerTranslate(0, 0);
        }
    }

    private boolean isScreenOn() {
        DisplayManager dm = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            if (display.getState() != Display.STATE_OFF)
            return true;
        }
        return false;
    }

    private void lowPassFilter(float[] input, float[] output, float alpha) {
        for (int i = 0; i < input.length; i++)
            output[i] = output[i] + alpha * (input[i] - output[i]);
    }

    private float rangeValue(float value, float min, float max) {
        if (value > max) return max;
        if (value < min) return min;
        return value;
    }

    private float fixNanOrInfinite(float value){
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0;
        return value;
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (timestamp != 0) {
                tempAcc[0] =  rangeValue(event.values[0], - MAX_ACC,  MAX_ACC);
                tempAcc[1] =  rangeValue(event.values[1], - MAX_ACC,  MAX_ACC);
                tempAcc[2] =  rangeValue(event.values[2], - MAX_ACC,  MAX_ACC);

                lowPassFilter(tempAcc, acc, mLowPassAlpha);

                float dt = (event.timestamp - timestamp) *  NS2S;

                for(int index = 0; index < 3; ++index) {
                    velocity[index] += acc[index] * dt - mVelocityFriction * velocity[index];
                    velocity[index] =  fixNanOrInfinite(velocity[index]);

                    position[index] += velocity[index] * mVelocityAmpl * dt - mPositionFriction * position[index];
                    position[index] =  rangeValue(position[index], - MAX_POS_SHIFT,  MAX_POS_SHIFT);
                }
            } else {
                velocity[0] = velocity[1] = velocity[2] = 0f;
                position[0] = position[1] = position[2] = 0f;

                acc[0] =  rangeValue(event.values[0], - MAX_ACC,  MAX_ACC);
                acc[1] =  rangeValue(event.values[1], - MAX_ACC,  MAX_ACC);
                acc[2] =  rangeValue(event.values[2], - MAX_ACC,  MAX_ACC);
            }

            timestamp = event.timestamp;

            int newPosX = Math.round(position[0]);
            int newPosY = Math.round(position[1]);
            if ((newPosX != x) || (newPosY != y) && mEnable) {
                x = newPosX;
                y = newPosY;
                setSurfaceFlingerTranslate(-x, y);
            }
        }
    };

    private final BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setSurfaceFlingerTranslate(0, 0);
            reset();
            if(mEnable) registerAccListener();
        }
    };

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterAccListener();
            setSurfaceFlingerTranslate(0, 0);
        }
    };

    public ScreenStabilization(Context context) {
        super(context);
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        mContext.registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        mContext.registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        if (isScreenOn()) registerAccListener();
    }

    @Override
    public void onStart() {
        mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.onChange(true);
            mSettingsObserver.register();
    }

    private void reset() {
        position[0] = position[1] = position[2] = 0;
        velocity[0] = velocity[1] = velocity[2] = 0;
        acc[0] = acc[1] = acc[2] = 0;
        timestamp = 0;
        x = y = 0;
    }

    private void registerAccListener() {
        if (accListenerRegistered) {
            return;
        }

        accListenerRegistered = mSensorManager.registerListener(sensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        if (!accListenerRegistered) {
            Log.wtf(TAG, "Sensor listener not registered");
        }
    }

    private void unregisterAccListener() {
        if (accListenerRegistered) {
            accListenerRegistered = false;
            mSensorManager.unregisterListener(sensorEventListener);
        }
    }

    private void setSurfaceFlingerTranslate(int x, int y)
    {
        try {
            if (flinger == null) {
                flinger = ServiceManager.getService("SurfaceFlinger");
            }
            if (flinger == null) {
                Log.wtf(TAG, "SurfaceFlinger is null");
                return;
            }

            Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeInt(x);
            data.writeInt(y);
            flinger.transact(2020, data, null, 0);
            data.recycle();
        } catch(Exception e) {
            Log.e(TAG, "SurfaceFlinger error", e);
        }
    }
}
