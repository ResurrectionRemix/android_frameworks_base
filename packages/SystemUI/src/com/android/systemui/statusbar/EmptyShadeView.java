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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.internal.util.rr.DeviceUtils;

import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.statusbar.policy.NetworkTraffic;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.SignalCallbackAdapter;
import com.android.systemui.R;

public class EmptyShadeView extends StackScrollerDecorView implements
        OnLayoutChangeListener {
    private final Context mContext;
    private final boolean mSupportsMobileData;

    private final SignalCallback mSignalCallback = new SignalCallback();
    private NetworkController mNetworkController;

    private RelativeLayout mContentLayout;
    private TextView mCarrierName;
    private TextView mWifiName;
    private TextView mNoNotifications;

    private boolean mShowCarrierName = false;
    private boolean mShowWifiName = false;

    private String mCarrierDescription = null;
    private String mWifiDescription = null;

    private boolean mIsNoSims = false;
    private boolean mWifiEnabled = false;
    private boolean mWifiConnected = false;

    private boolean mListening = false;

    public EmptyShadeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mSupportsMobileData = DeviceUtils.deviceSupportsMobileData(mContext);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContentLayout = (RelativeLayout) findViewById(R.id.empty_shade_view_content_layout);
        mCarrierName = (TextView) mContentLayout.findViewById(R.id.empty_shade_view_carrier_name);
        mWifiName = (TextView) mContentLayout.findViewById(R.id.empty_shade_view_wifi_name);
        mWifiName.addOnLayoutChangeListener(this);
        mNoNotifications = (TextView) mContentLayout.findViewById(R.id.no_notifications);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mNoNotifications.setText(R.string.empty_shade_text);
    }

    @Override
    protected View findContentView() {
        return findViewById(R.id.empty_shade_view_content_layout);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (mShowCarrierName && mShowWifiName && (left != oldLeft || right != oldRight)) {
            int carrierNameMaxWidth = Math.round(mWifiName.getX() - mCarrierName.getX());
            mCarrierName.setMaxWidth(carrierNameMaxWidth);
        }
    }

    public void setNetworkController(NetworkController nc) {
        mNetworkController = nc;
    }

    public void setListening(boolean listening) {
        if (mNetworkController == null || mListening == listening) {
            return;
        }
        mListening = listening;
        if (mListening) {
            mNetworkController.addSignalCallback(mSignalCallback);
        } else {
            mNetworkController.removeSignalCallback(mSignalCallback);
        }
    }

    private void updateViews() {
        final Resources res = mContext.getResources();
        TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        if (mCarrierDescription == null || mCarrierDescription.isEmpty()) {
            mCarrierDescription = telephonyManager.getNetworkOperatorName();
        }
        if (TextUtils.isEmpty(mCarrierDescription)) {
            mCarrierDescription = telephonyManager.getSimOperatorName();
        }

        if (mWifiEnabled) {
            if (!mWifiConnected) {
                mWifiDescription = res.getString(R.string.accessibility_no_wifi);
            }
        } else {
            mWifiDescription = res.getString(R.string.accessibility_wifi_off);
        }
        mCarrierName.setText(mCarrierDescription);
        mWifiName.setText(mWifiDescription);
    }

    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            return string.substring(0, length - 1);
        }
        return string;
    }

    private static String removeDoubleQuotes(String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    public void setShowCarrierName(boolean show) {
        mShowCarrierName = show && mSupportsMobileData;
        mCarrierName.setVisibility(mShowCarrierName ? View.VISIBLE : View.INVISIBLE);
        updateNoNotificationsPosition(mShowCarrierName, mShowWifiName);
    }

    public void setShowWifiName(boolean show) {
        mShowWifiName = show;
        mWifiName.setVisibility(mShowWifiName ? View.VISIBLE : View.INVISIBLE);
        updateNoNotificationsPosition(mShowCarrierName, mShowWifiName);
    }

    public void updateTextColor(int color) {
        mCarrierName.setTextColor(color);
        mWifiName.setTextColor(color);
        mNoNotifications.setTextColor(color);
    }

    public void updateNoNotificationsPosition(boolean showCarrierName, boolean showWifiName) {
        boolean isPositionTop = !showCarrierName && !showWifiName;

        int paddingBottom = mContext.getResources().getDimensionPixelSize(
                R.dimen.empty_shade_view_content_padding_top_bottom);
        int paddingTop = isPositionTop
                ? mContext.getResources().getDimensionPixelSize(
                        R.dimen.empty_shade_view_no_notifications_padding_top)
                : paddingBottom;

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mNoNotifications.getLayoutParams();
        lp.removeRule(isPositionTop
                ? RelativeLayout.ALIGN_PARENT_BOTTOM : RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(isPositionTop
            ? RelativeLayout.ALIGN_PARENT_TOP : RelativeLayout.ALIGN_PARENT_BOTTOM);
        mNoNotifications.setLayoutParams(lp);
        mNoNotifications.setPadding(0, paddingTop, 0, paddingBottom);
    }

    private final class SignalCallback extends SignalCallbackAdapter {
        @Override
        public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
                boolean activityIn, boolean activityOut, String description) {
            mWifiEnabled = enabled;
            mWifiConnected = enabled && (statusIcon.icon > 0) && (description != null);
            mWifiDescription = removeDoubleQuotes(description);
            updateViews();
        }

        @Override
        public void setNoSims(boolean show) {
            if (!mSupportsMobileData) {
                return;
            }
            mIsNoSims = show;
            updateViews();
        }
    };
}
