/*
 * Copyright (C) 2016 RR
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import com.android.systemui.R;


import java.util.ArrayList;
import java.util.List;

/**
 * A controller to manage changes of gps and update the views accordingly.
 */
public class GpsControllerImpl extends BroadcastReceiver implements GpsController {
    public static final int LOCATION_STATUS_ICON_ID = R.drawable.stat_sys_location;

    private static final int[] mHighPowerRequestAppOpArray =
        new int[] { AppOpsManager.OP_MONITOR_HIGH_POWER_LOCATION};

    private Context mContext;

    private AppOpsManager mAppOpsManager;
    private StatusBarManager mStatusBarManager;

    private boolean mAreActiveLocationRequests;

    private ArrayList<GpsSettingsChangeCallback> mSettingsChangeCallbacks =
        new ArrayList<GpsSettingsChangeCallback>();
    private final H mHandler = new H();
    public final String mSlotLocation;

    public GpsControllerImpl(Context context, Looper bgLooper) {
        mContext = context;
        mSlotLocation = mContext.getString(com.android.internal.R.string.status_bar_location);

        // Register to listen for changes in location settings
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.HIGH_POWER_REQUEST_CHANGE_ACTION);
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);
        context.registerReceiverAsUser(this, UserHandle.ALL, filter, null, new Handler(bgLooper));

        mAppOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mStatusBarManager = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);

        // Examine the current location state and initialize the status view.
        updateActiveLocationRequests();
        refreshViews();
    }

    /**
     * Add a callback to listen for changes in gps settings
     */
    public void addSettingsChangedCallback(GpsSettingsChangeCallback callback) {
        mSettingsChangeCallbacks.add(callback);
        mHandler.sendEmptyMessage(H.MSG_GPS_SETTINGS_CHANGED);
    }

    public void removeSettingsChangedCallback(GpsSettingsChangeCallback callback) {
        mSettingsChangeCallbacks.remove(callback);
    }

    public boolean isNetworkLocationEnabled() {
        ContentResolver resolver = mContext.getContentResolver();
        
        // QuickSettings always runs as the owner, so specifically retrieve the settings
        // for the current foregound user.
        int mode = Settings.Secure.getIntForUser(resolver, Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF, ActivityManager.getCurrentUser());
        
        return mode ==
            Settings.Secure.LOCATION_MODE_BATTERY_SAVING || mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
    }
    
    /**
     * 
     * @return true if gps is enabled
     */
    public boolean isGpsEnabled() {
        ContentResolver resolver = mContext.getContentResolver();
        
        // QuickSettings always runs as the owner, so specifically retrieve the settings
        // for the current foreground user.
        int mode = Settings.Secure.getIntForUser(resolver, Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF, ActivityManager.getCurrentUser());
        
        return mode ==
            Settings.Secure.LOCATION_MODE_SENSORS_ONLY || mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
    }

    /**
     * Enable or disable gps
     * @return true if attempt to change setting was successful
     */
    public boolean setGpsEnabled(boolean enabled) {
        int currentUserId = ActivityManager.getCurrentUser();
        if (isUserLocationRestricted(currentUserId)) {
            return false;
        }
        final ContentResolver contentResolver = mContext.getContentResolver();

        int mode;

        if (isNetworkLocationEnabled()) {
            mode = enabled
                ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
        } else {
            mode = enabled
                ? Settings.Secure.LOCATION_MODE_SENSORS_ONLY : Settings.Secure.LOCATION_MODE_OFF;
        }

        // QuickSettings always runs as the owner, so specifically set the settings
        // for the current foreground user.
        return Settings.Secure
            .putIntForUser(contentResolver, Settings.Secure.LOCATION_MODE, mode, currentUserId);
    }

    /**
     * Returns true if the current user is restricted from using location.
     */
    private boolean isUserLocationRestricted(int userId) {
        final UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        return userManager.hasUserRestriction(
            UserManager.DISALLOW_SHARE_LOCATION,
            new UserHandle(userId));
    }

    /**
     * Returns true if there currently exist active high power location requests.
     */
    private boolean areActiveHighPowerLocationRequests() {
        List<AppOpsManager.PackageOps> packages
            = mAppOpsManager.getPackagesForOps(mHighPowerRequestAppOpArray);
        // AppOpsManager can return null when there is no requested data.
        if (packages != null) {
            final int numPackages = packages.size();
            for (int packageInd = 0; packageInd < numPackages; packageInd++) {
                AppOpsManager.PackageOps packageOp = packages.get(packageInd);
                List<AppOpsManager.OpEntry> opEntries = packageOp.getOps();
                if (opEntries != null) {
                    final int numOps = opEntries.size();
                    for (int opInd = 0; opInd < numOps; opInd++) {
                        AppOpsManager.OpEntry opEntry = opEntries.get(opInd);
                        // AppOpsManager should only return OP_MONITOR_HIGH_POWER_LOCATION because
                        // of the mHighPowerRequestAppOpArray filter, but checking defensively.
                        if (opEntry.getOp() == AppOpsManager.OP_MONITOR_HIGH_POWER_LOCATION) {
                            if (opEntry.isRunning()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }
    
    private void updateActiveLocationRequests() {
        boolean hadActiveLocationRequests = mAreActiveLocationRequests;
        mAreActiveLocationRequests = areActiveHighPowerLocationRequests();
        if (mAreActiveLocationRequests != hadActiveLocationRequests) {
            refreshViews();
        }
    }

    /**
     * Updates the status view based on the current state of location requests.
     */
    private void refreshViews() {
        if (mAreActiveLocationRequests) {
            mStatusBarManager.setIcon(mSlotLocation, LOCATION_STATUS_ICON_ID,
                0, mContext.getString(R.string.accessibility_location_active));
        } else {
            mStatusBarManager.removeIcon(mSlotLocation);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (LocationManager.HIGH_POWER_REQUEST_CHANGE_ACTION.equals(action)) {
            updateActiveLocationRequests();
        } else if (LocationManager.MODE_CHANGED_ACTION.equals(action)) {
            mHandler.sendEmptyMessage(H.MSG_GPS_SETTINGS_CHANGED);
        }
    }

    private final class H extends Handler {
        private static final int MSG_GPS_SETTINGS_CHANGED = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GPS_SETTINGS_CHANGED:
                    gpsSettingsChanged();
                    break;
            }
        }

        private void gpsSettingsChanged() {
            boolean isEnabled = isGpsEnabled();
            for (GpsSettingsChangeCallback callback : mSettingsChangeCallbacks) {
                callback.onGpsSettingsChanged(isEnabled);
            }
        }
    }
}
