/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import android.os.SystemProperties;

import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.views.DockState;
import com.android.systemui.shared.recents.model.TaskStack;

/**
 * Represents the dock regions for each orientation.
 */
class DockRegion {
    public static DockState[] PHONE_LANDSCAPE = {
            // We only allow docking to the left in landscape for now on small devices
            DockState.LEFT
    };
    public static DockState[] PHONE_PORTRAIT = {
            // We only allow docking to the top for now on small devices
            DockState.TOP
    };
    public static DockState[] TABLET_LANDSCAPE = {
            DockState.LEFT,
            DockState.RIGHT
    };
    public static DockState[] TABLET_PORTRAIT = PHONE_PORTRAIT;
}

/**
 * Application resources that can be retrieved from the application context and are not specifically
 * tied to the current activity.
 */
public class RecentsConfiguration {

    private static final int LARGE_SCREEN_MIN_DP = 600;
    private static final int XLARGE_SCREEN_MIN_DP = 720;

    // Launch states
    public RecentsActivityLaunchState mLaunchState = new RecentsActivityLaunchState();

    // Since the positions in Recents has to be calculated globally (before the RecentsActivity
    // starts), we need to calculate some resource values ourselves, instead of relying on framework
    // resources.
    public final boolean isLargeScreen;
    public final boolean isXLargeScreen;
    public final int smallestWidth;

    /** Misc **/
    public boolean fakeShadows;
    public int svelteLevel;

    // Whether this product supports Grid-based Recents. If this is field is set to true, then
    // Recents will layout task views in a grid mode when there's enough space in the screen.
    public boolean isGridEnabledDefault;
    public boolean mIsGridEnabled;

    // Support for Android Recents for low ram devices. If this field is set to true, then Recents
    // will use the alternative layout.
    public boolean isLowRamDevice;
    public boolean isLowRamDeviceDefault;
    public boolean mIsGoLayoutEnabled;

    // Enable drag and drop split from Recents. Disabled for low ram devices.
    public boolean dragToSplitEnabled;

    private final Context mAppContext;

    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mAppContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENTS_LAYOUT_STYLE),
                    false, this);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            mIsGridEnabled = Settings.System.getIntForUser(mAppContext.getContentResolver(),
                    Settings.System.RECENTS_LAYOUT_STYLE, isGridEnabledDefault ? 1 : 0,
                    UserHandle.USER_CURRENT) == 1;
            mIsGoLayoutEnabled = Settings.System.getIntForUser(mAppContext.getContentResolver(),
                    Settings.System.RECENTS_LAYOUT_STYLE, isLowRamDeviceDefault ? 2 : 0,
                    UserHandle.USER_CURRENT) == 2;
        }
    }

    public RecentsConfiguration(Context context) {
        // Load only resources that can not change after the first load either through developer
        // settings or via multi window
        SystemServicesProxy ssp = Recents.getSystemServices();
        mAppContext = context.getApplicationContext();
        Resources res = mAppContext.getResources();
        fakeShadows = res.getBoolean(R.bool.config_recents_fake_shadows);
        svelteLevel = res.getInteger(R.integer.recents_svelte_level);

        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        isGridEnabledDefault = SystemProperties.getBoolean("ro.recents.grid", false);
        isLowRamDeviceDefault = ActivityManager.isLowRamDeviceStatic();
        isLowRamDevice = mIsGoLayoutEnabled;
        dragToSplitEnabled = mIsGoLayoutEnabled? true : !isLowRamDeviceDefault;

        float screenDensity = context.getResources().getDisplayMetrics().density;
        smallestWidth = ssp.getDeviceSmallestWidth();
        isLargeScreen = smallestWidth >= (int) (screenDensity * LARGE_SCREEN_MIN_DP);
        isXLargeScreen = smallestWidth >= (int) (screenDensity * XLARGE_SCREEN_MIN_DP);
    }

    /**
     * Returns the activity launch state.
     * TODO: This will be refactored out of RecentsConfiguration.
     */
    public RecentsActivityLaunchState getLaunchState() {
        return mLaunchState;
    }

    /**
     * Returns the preferred dock states for the current orientation.
     * @return a list of dock states for device and its orientation
     */
    public DockState[] getDockStatesForCurrentOrientation() {
        boolean isLandscape = mAppContext.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        RecentsConfiguration config = Recents.getConfiguration();
        if (config.isLargeScreen) {
            return isLandscape ? DockRegion.TABLET_LANDSCAPE : DockRegion.TABLET_PORTRAIT;
        } else {
            return isLandscape ? DockRegion.PHONE_LANDSCAPE : DockRegion.PHONE_PORTRAIT;
        }
    }

    public boolean isGridEnabled() {
        return mIsGridEnabled;
    }

}
