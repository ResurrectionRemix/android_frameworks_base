/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.systemui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;

import com.android.systemui.statusbar.policy.BatteryController;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback{

    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";

    private BatteryController mBatteryController;
    private boolean mBatteryCharging;
    private boolean mForceShow;
    private boolean mAttached;
    private int mRequestedVisibility;

    private int mStyle;
    private int mPercentMode;
    private int mParentId;

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRequestedVisibility = getVisibility();
    }

    public void setForceShown(boolean forceShow) {
        mForceShow = forceShow;
        updateVisibility();
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        if (mAttached) {
            mBatteryController.addStateChangedCallback(this);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        mRequestedVisibility = visibility;
        updateVisibility();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Respect font size setting.
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.battery_level_text_size));
     }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        String text = getResources().getString(R.string.battery_level_template, level);

        //if we're in text-only mode, AND we're in the statusbar or expanded statusbar
        if ((mParentId == R.id.system_icon_area ||
                mParentId == R.id.system_icons_super_container) &&
                mStyle == BatteryController.STYLE_TEXT) {
            //prepend a '+' if the phone is charging
            if (charging && level < 100) {
                text = getResources().getString(R.string.battery_level_template_charging, text);
            }
        }

        setText(text);
        if (mBatteryCharging != charging) {
            mBatteryCharging = charging;
            updateVisibility();
        }
    }

    @Override
    public void onPowerSaveChanged() {
        // Not used
    }

    @Override
    public void onBatteryStyleChanged(int style, int percentMode) {
        mStyle = style;
        mPercentMode = percentMode;
        updateVisibility();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        View parent = null;
        try {
            parent = ((View)getParent());
        } catch (Exception ex) {
            Log.e("BatteryLevelTextView", ex.toString());
        }

        mParentId = parent == null ? 0 : parent.getId();

        if (mBatteryController != null) {
            mBatteryController.addStateChangedCallback(this);
        }

        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;

        mParentId = 0;

        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }

    private void updateVisibility() {
        boolean showNextPercent = mPercentMode == BatteryController.PERCENTAGE_MODE_OUTSIDE
                || (mBatteryCharging && mPercentMode == BatteryController.PERCENTAGE_MODE_INSIDE);
        if (mStyle == BatteryController.STYLE_GONE) {
            showNextPercent = false;
        } else if (mStyle == BatteryController.STYLE_TEXT) {
            showNextPercent = true;
        }

        if (showNextPercent || mForceShow) {
            super.setVisibility(mRequestedVisibility);
        } else {
            super.setVisibility(GONE);
        }
    }
}
