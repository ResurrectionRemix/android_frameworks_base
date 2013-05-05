/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.ColorUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

import java.util.ArrayList;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    private int mLevel;
    private boolean mPlugged;
    private ColorUtils.ColorSettingInfo mColorInfo;

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    private static int sBatteryLevel = 50;
    private static boolean sBatteryCharging = false;

    public BatteryController(Context context) {
        mContext = context;

        mColorInfo = ColorUtils.getColorSettingInfo(context, Settings.System.STATUS_ICON_COLOR);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(this, filter);
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void setColor(ColorUtils.ColorSettingInfo colorInfo) {
        mColorInfo = colorInfo;
        updateBatteryLevel();
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            updateBatteryLevel();
        }
    }

    public void updateBatteryLevel() {
        final int icon = mPlugged ? R.drawable.stat_sys_battery_charge 
                : R.drawable.stat_sys_battery;
        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            Drawable batteryBitmap = mContext.getResources().getDrawable(icon);
            if (mColorInfo.isLastColorNull) {
                batteryBitmap.clearColorFilter();                
            } else {
                batteryBitmap.setColorFilter(mColorInfo.lastColor, PorterDuff.Mode.SRC_IN);
            }
            v.setImageDrawable(batteryBitmap);
            v.setImageLevel(mLevel);
            v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                    mLevel));
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            v.setText(mContext.getString(R.string.status_bar_settings_battery_meter_format,
                    mLevel));
        }

        for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onBatteryLevelChanged(mLevel, mPlugged);
        }
    }

    public void updateCallback(BatteryStateChangeCallback cb) {
        cb.onBatteryLevelChanged(sBatteryLevel, sBatteryCharging);
    }

    public void updateCallbacks() {
        for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onBatteryLevelChanged(sBatteryLevel, sBatteryCharging);
        }
    }
}
