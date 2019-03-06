/**
 * Copyright (C) 2016 The ParanoidAndroid Project
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
package android.pocket;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Slog;

/**
 * A class that coordinates listening for pocket state.
 * <p>
 * Use {@link android.content.Context#getSystemService(java.lang.String)}
 * with argument {@link android.content.Context#POCKET_SERVICE} to get
 * an instance of this class.
 *
 * Usage: import and create a final {@link IPocketCallback.Stub()} and implement your logic in
 * {@link IPocketCallback#onStateChanged(boolean, int)}. Then add your callback to the pocket manager
 *
 * // define a final callback
 * private final IPocketCallback mCallback = new IPocketCallback.Stub() {
 *
 *     @Override
 *     public void onStateChanged(boolean isDeviceInPocket, int reason) {
 *         // Your method to handle logic outside of this callback, ideally with a handler
 *         // posting on UI Thread for view hierarchy operations or with its own background thread.
 *         handlePocketStateChanged(isDeviceInPocket, reason);
 *     }
 *
 * }
 *
 * // add callback to pocket manager
 * private void addCallback() {
 *     PocketManager manager = (PocketManager) context.getSystemService(Context.POCKET_SERVICE);
 *     manager.addCallback(mCallback);
 * }
 *
 * @author Carlo Savignano
 * @hide
 */
public class PocketManager {

    private static final String TAG = PocketManager.class.getSimpleName();
    static final boolean DEBUG = false;

    /**
     * Whether {@link IPocketCallback#onStateChanged(boolean, int)}
     * was fired because of the sensor.
     * @see PocketService#handleDispatchCallbacks()
     */
    public static final int REASON_SENSOR = 0;

    /**
     * Whether {@link IPocketCallback#onStateChanged(boolean, int)}
     * was fired because of an error while accessing service.
     * @see #addCallback(IPocketCallback)
     * @see #removeCallback(IPocketCallback)
     */
    public static final int REASON_ERROR = 1;

    /**
     * Whether {@link IPocketCallback#onStateChanged(boolean, int)}
     * was fired because of a needed reset.
     * @see PocketService#binderDied()
     */
    public static final int REASON_RESET = 2;

    private Context mContext;
    private IPocketService mService;
    private PowerManager mPowerManager;
    private Handler mHandler;
    private boolean mPocketViewTimerActive;

    public PocketManager(Context context, IPocketService service) {
        mContext = context;
        mService = service;
        if (mService == null) {
            Slog.v(TAG, "PocketService was null");
        }
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHandler = new Handler();
    }

    /**
     * Add pocket state callback.
     * @see PocketService#handleRemoveCallback(IPocketCallback)
     */
    public void addCallback(final IPocketCallback callback) {
        if (mService != null) try {
            mService.addCallback(callback);
        } catch (RemoteException e1) {
            Log.w(TAG, "Remote exception in addCallback: ", e1);
            if (callback != null){
                try {
                    callback.onStateChanged(false, REASON_ERROR);
                } catch (RemoteException e2) {
                    Log.w(TAG, "Remote exception in callback.onPocketStateChanged: ", e2);
                }
            }
        }
    }

    /**
     * Remove pocket state callback.
     * @see PocketService#handleAddCallback(IPocketCallback)
     */
    public void removeCallback(final IPocketCallback callback) {
        if (mService != null) try {
            mService.removeCallback(callback);
        } catch (RemoteException e1) {
            Log.w(TAG, "Remote exception in removeCallback: ", e1);
            if (callback != null){
                try {
                    callback.onStateChanged(false, REASON_ERROR);
                } catch (RemoteException e2) {
                    Log.w(TAG, "Remote exception in callback.onPocketStateChanged: ", e2);
                }
            }
        }
    }

    /**
     * Notify service about device interactive state changed.
     * {@link PhoneWindowManager#startedWakingUp()}
     * {@link PhoneWindowManager#startedGoingToSleep(int)}
     */
    public void onInteractiveChanged(boolean interactive) {
        boolean isPocketViewShowing = (interactive && isDeviceInPocket());
        synchronized (mPocketLockTimeout) {
            if (mPocketViewTimerActive != isPocketViewShowing) {
                if (isPocketViewShowing) {
                    if (DEBUG) Log.v(TAG, "Setting pocket timer");
                    mHandler.removeCallbacks(mPocketLockTimeout); // remove any pending requests
                    mHandler.postDelayed(mPocketLockTimeout, 10 * DateUtils.SECOND_IN_MILLIS);
                    mPocketViewTimerActive = true;
                } else {
                    if (DEBUG) Log.v(TAG, "Clearing pocket timer");
                    mHandler.removeCallbacks(mPocketLockTimeout);
                    mPocketViewTimerActive = false;
                }
            }
        }
        if (mService != null) try {
            mService.onInteractiveChanged(interactive);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in addCallback: ", e);
        }
    }

    /**
     * Request listening state change by, but not limited to, external process.
     * @see PocketService#handleSetListeningExternal(boolean)
     */
    public void setListeningExternal(boolean listen) {
        if (mService != null) try {
            mService.setListeningExternal(listen);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in setListeningExternal: ", e);
        }
        // Clear timeout when user hides pocket lock with long press power.
        if (mPocketViewTimerActive && !listen) {
            if (DEBUG) Log.v(TAG, "Clearing pocket timer due to override");
            mHandler.removeCallbacks(mPocketLockTimeout);
            mPocketViewTimerActive = false;
        }
    }

    /**
     * Return whether device is in pocket.
     * @see PocketService#isDeviceInPocket()
     * @return
     */
    public boolean isDeviceInPocket() {
        if (mService != null) try {
            return mService.isDeviceInPocket();
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in isDeviceInPocket: ", e);
        }
        return false;
    }

    class PocketLockTimeout implements Runnable {
        @Override
        public void run() {
            mPowerManager.goToSleep(SystemClock.uptimeMillis());
            mPocketViewTimerActive = false;
        }
    }

    private PocketLockTimeout mPocketLockTimeout = new PocketLockTimeout();

}
