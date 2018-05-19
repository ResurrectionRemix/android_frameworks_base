/*
 * Copyright (C) 2018 CypherOS
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
package android.ambient;

import android.ambient.play.RecoginitionObserver.Observable;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that manages Ambient Play events
 */
public class AmbientIndicationManager {

    private static final String TAG = "AmbientIndicationManager";
    private static AmbientIndicationManager sInstance;

    private BatteryManager mBatteryManager;
    private Context mContext;
    private boolean mSystemBooted;

    private final ArrayList<WeakReference<AmbientIndicationManagerCallback>>
            mCallbacks = Lists.newArrayList();

    private static final int MSG_SYSTEM_BOOTED = 0;
    private static final int MSG_RECOGNITION_RESULT = 1;
    private static final int MSG_RECOGNITION_NO_RESULT = 2;
    private static final int MSG_RECOGNITION_AUDIO = 3;
    private static final int MSG_RECOGNITION_ERROR = 4;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SYSTEM_BOOTED:
                    handleSystemBooted();
                    break;
                case MSG_RECOGNITION_RESULT:
                    handleRecognitionResult((Observable) msg.obj);
                    break;
                case MSG_RECOGNITION_NO_RESULT:
                    handleRecognitionNoResult();
                    break;
                case MSG_RECOGNITION_AUDIO:
                    handleRecognitionAudio(msg.arg1);
                    break;
                case MSG_RECOGNITION_ERROR:
                    handleRecognitionError();
                    break;
            }
        }
    };

    private AmbientIndicationManager(Context context) {
        mContext = context;
        mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    public static AmbientIndicationManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AmbientIndicationManager(context);
        }
        return sInstance;
    }

    /**
     * Unregister the given callback.
     * @param callback The callback to remove
     */
    public void unRegisterCallback(AmbientIndicationManagerCallback callback) {
        Log.v(TAG, "Unregister callback for" + callback);
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }

    /**
     * Register to receive indications of nearby music
     * @param callback The callback to register
     */
    public void registerCallback(AmbientIndicationManagerCallback callback) {
        Log.v(TAG, "Register callback for" + callback);
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                Log.e(TAG, "Object tried to add another callback",
                        new Exception("Called by"));
                return;
            }
        }
        mCallbacks.add(new WeakReference<AmbientIndicationManagerCallback>(callback));
        unRegisterCallback(null);
    }

    /**
     * Handle {@link #MSG_SYSTEM_BOOTED}
     */
    protected void handleSystemBooted() {
        /*if (mSystemBooted) return;
        mSystemBooted = true;
        for (int i = 0; i < mCallbacks.size(); i++) {
            AmbientIndicationManagerCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSystemBooted();
            }
        }*/
    }

    /**
     * Handle {@link #MSG_RECOGNITION_RESULT}
     */
    protected void handleRecognitionResult(Observable observed) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            AmbientIndicationManagerCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRecognitionResult(observed);
            }
        }
    }

    /**
     * Handle {@link #MSG_RECOGNITION_NO_RESULT}
     */
    protected void handleRecognitionNoResult() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            AmbientIndicationManagerCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRecognitionNoResult();
            }
        }
    }

    /**
     * Handle {@link #MSG_RECOGNITION_AUDIO}
     */
    protected void handleRecognitionAudio(float level) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            AmbientIndicationManagerCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRecognitionAudio(level);
            }
        }
    }

    /**
     * Handle {@link #MSG_RECOGNITION_ERROR}
     */
    protected void handleRecognitionError() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            AmbientIndicationManagerCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRecognitionError();
            }
        }
    }

    public void dispatchRecognitionResult(Observable observed) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_RECOGNITION_RESULT, observed));
    }

    public void dispatchRecognitionNoResult() {
        mHandler.sendEmptyMessage(MSG_RECOGNITION_NO_RESULT);
    }

    public void dispatchRecognitionAudio(float level) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_RECOGNITION_AUDIO, level));
    }

    public void dispatchRecognitionError() {
        mHandler.sendEmptyMessage(MSG_RECOGNITION_ERROR);
    }

    public boolean isCharging() {
        return mBatteryManager.isCharging();
    }
}
