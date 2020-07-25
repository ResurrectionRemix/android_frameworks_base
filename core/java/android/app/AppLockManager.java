/**
 * Copyright (C) 2017-2020 Paranoid Android
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

package android.app;

import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;

import java.util.List;

/**
 * @author Anas Karbila
 * @author Rituj Beniwal
 * @hide
 */
@SystemService(Context.APPLOCK_SERVICE)
public class AppLockManager {

    private static final String TAG = "AppLockManager";

    private IAppLockService mService;

    public AppLockManager(IAppLockService service) {
        mService = service;
    }

    public void addAppToList(String packageName) {
        try {
            mService.addAppToList(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void removeAppFromList(String packageName) {
        try {
            mService.removeAppFromList(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isAppLocked(String packageName) {
        try {
            return mService.isAppLocked(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
    
    public boolean isAppOpen(String packageName) {
        try {
            return mService.isAppOpen(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setShowOnlyOnWake(boolean showOnce) {
        try {
            mService.setShowOnlyOnWake(showOnce);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean getShowOnlyOnWake() {
        try {
            return mService.getShowOnlyOnWake();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getLockedAppsCount() {
        try {
            return mService.getLockedAppsCount();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<String> getLockedPackages() {
        try {
            return mService.getLockedPackages();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean getAppNotificationHide(String packageName) {
        try {
            return mService.getAppNotificationHide(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setAppNotificationHide(String packageName, boolean hide) {
        try {
            mService.setAppNotificationHide(packageName, hide);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void addAppLockCallback(IAppLockCallback c) {
        try {
            mService.addAppLockCallback(c);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void removeAppLockCallback(IAppLockCallback c) {
        try {
            mService.removeAppLockCallback(c);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public abstract static class AppLockCallback extends IAppLockCallback.Stub {
        @Override
        public abstract void onAppStateChanged(String pkg);
    };
}
