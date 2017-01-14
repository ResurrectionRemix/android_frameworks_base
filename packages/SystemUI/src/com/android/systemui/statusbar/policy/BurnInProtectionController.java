/*
 * Copyright 2017-2018 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.NavigationBarView;
import com.android.systemui.statusbar.phone.StatusBar;

import java.util.Timer;
import java.util.TimerTask;

public class BurnInProtectionController {

    private static final String TAG = BurnInProtectionController.class.getSimpleName();
    private static final boolean DEBUG = false;

    private boolean mSwiftEnabled;
    private int mHorizontalShift = 0;
    private int mVerticalShift = 0;
    private int mHorizontalDirection = 1;
    private int mVerticalDirection = 1;
    private int mNavigationBarHorizontalMaxShift;
    private int mNavigationBarVerticalMaxShift;
    private int mHorizontalMaxShift;
    private int mVerticalMaxShift;
    private long mShiftInterval;

    private Timer mTimer;

    private StatusBar mStatusBar;
    private PhoneStatusBarView mPhoneStatusBarView;

    private Context mContext;

    public BurnInProtectionController(Context context, StatusBar statusBar,
                                      PhoneStatusBarView phoneStatusBarView) {
        mContext = context;

        mStatusBar = statusBar;

        mPhoneStatusBarView = phoneStatusBarView;

        mSwiftEnabled = mContext.getResources().getBoolean(
                R.bool.config_statusBarBurnInProtection);
        mHorizontalMaxShift = mContext.getResources()
                .getDimensionPixelSize(R.dimen.horizontal_max_swift);
        // total of ((vertical_max_swift - 1) * 2) pixels can be moved
        mVerticalMaxShift = mContext.getResources()
                .getDimensionPixelSize(R.dimen.vertical_max_swift) - 1;
        mShiftInterval = (long) mContext.getResources().getInteger(R.integer.config_shift_interval);
    }

    public void startSwiftTimer() {
        if (!mSwiftEnabled) return;
        if (mTimer == null) {
            mTimer = new Timer();
        }
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final Handler mUiHandler = new Handler(Looper.getMainLooper());
                mUiHandler.post(() -> {
                    swiftItems();
                });
            }
        }, 0, mShiftInterval * 1000);
        if (DEBUG) Log.d(TAG, "Started swift timer");
    }

    public void stopSwiftTimer() {
        if (!mSwiftEnabled) return;
        mTimer.cancel();
        mTimer.purge();
        mTimer = null;
        if (DEBUG) Log.d(TAG, "Canceled swift timer");
    }

    private void swiftItems() {
        mHorizontalShift += mHorizontalDirection;
        if ((mHorizontalShift >=  mHorizontalMaxShift) ||
                (mHorizontalShift <= -mHorizontalMaxShift)) {
            mHorizontalDirection *= -1;
        }

        mVerticalShift += mVerticalDirection;
        if ((mVerticalShift >=  mVerticalMaxShift) ||
                (mVerticalShift <= -mVerticalMaxShift)) {
            mVerticalDirection *= -1;
        }

        mPhoneStatusBarView.swiftStatusBarItems(mHorizontalShift, mVerticalShift);
        NavigationBarView mNavigationBarView = mStatusBar.getNavigationBarView();
        if (mNavigationBarView != null) {
            mNavigationBarView.swiftNavigationBarItems(mHorizontalShift, mVerticalShift);
        }

        if (DEBUG) Log.d(TAG, "Swifting items..");
    }
}
