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

package android.hardware.display;

import android.content.Context;
import android.annotation.SystemService;
import android.os.RemoteException;

/**
 * Manages the DC Dimming mode
 * @author Rituj Beniwal
 * @hide
 */
@SystemService(Context.DC_DIM_SERVICE)
public class DcDimmingManager {
    private static final String TAG = "DcDimmingManager";

    public static final int MODE_AUTO_OFF = 0;
    public static final int MODE_AUTO_TIME = 1;
    public static final int MODE_AUTO_BRIGHTNESS = 2;
    public static final int MODE_AUTO_FULL = 3;

    private IDcDimmingManager mService;

    public DcDimmingManager (IDcDimmingManager service) {
        mService = service;
    }

    /**
     * Set the DC Dimming mode
     * @hide
     */
    public void setAutoMode(int mode) {
        if (mService != null) {
            try {
                mService.setAutoMode(mode);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Get the DC Dimming mode
     * @hide
     */
    public int getAutoMode() {
        if (mService != null) {
            try {
                return mService.getAutoMode();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return 0;
    }

    /**
     * Get the DC Dimming mode
     * @hide
     */
    public boolean isAvailable() {
        if (mService != null) {
            try {
                return mService.isAvailable();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return false;
    }

    /**
     * Get the DC Dimming mode
     * @hide
     */
    public void setDcDimming(boolean enable) {
        if (mService != null) {
            try {
                mService.setDcDimming(enable);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    public boolean isDcDimmingOn() {
        if (mService != null) {
            try {
                return mService.isDcDimmingOn();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return false;
    }

    public void setBrightnessThreshold(int thresh) {
        if (mService != null) {
            try {
                mService.setBrightnessThreshold(thresh);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    public int getBrightnessThreshold() {
        if (mService != null) {
            try {
                return mService.getBrightnessThreshold();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return 0;
    }

    public boolean isForcing() {
        if (mService != null) {
            try {
                return mService.isForcing();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return false;
    }

    public void restoreAutoMode() {
        if (mService != null) {
            try {
                mService.restoreAutoMode();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }
}