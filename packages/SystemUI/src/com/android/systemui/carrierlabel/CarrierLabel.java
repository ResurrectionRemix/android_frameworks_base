/*
 * Copyright (C) 2014-2015 The MoKee OpenSource Project
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

package com.android.systemui.carrierlabel;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.internal.util.rr.Utils;
import com.android.internal.telephony.TelephonyIntents;

import com.android.systemui.Dependency;
import com.android.systemui.carrierlabel.SpnOverride;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.systemui.R;

public class CarrierLabel extends TextView implements DarkReceiver {

    private Context mContext;
    private boolean mAttached;
    private static boolean isCN;
    private int mCarrierFontSize = 14;
    private int mCarrierColor = 0xffffffff;
    private int mTintColor = Color.WHITE;

    private int mCarrierLabelFontStyle = FONT_NORMAL;
    public static final int FONT_NORMAL = 0;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_COLOR), false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_FONT_SIZE), false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_FONT_STYLE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
	     updateColor();
	     updateSize();
	     updateStyle();
        }
    }

    public CarrierLabel(Context context) {
        this(context, null);
    }

    public CarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        updateNetworkName(true, null, false, null);
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        updateColor();
        updateSize();
        updateStyle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
            filter.addAction(Intent.ACTION_CUSTOM_CARRIER_LABEL_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)
                    || Intent.ACTION_CUSTOM_CARRIER_LABEL_CHANGED.equals(action)) {
                        updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, true),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
                isCN = Utils.isChineseLanguage();
            }
        }
    };

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        final String str;
        final boolean plmnValid = showPlmn && !TextUtils.isEmpty(plmn);
        final boolean spnValid = showSpn && !TextUtils.isEmpty(spn);
        if (spnValid) {
            str = spn;
        } else if (plmnValid) {
            str = plmn;
        } else {
            str = "";
        }
        String customCarrierLabel = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.CUSTOM_CARRIER_LABEL, UserHandle.USER_CURRENT);
        if (!TextUtils.isEmpty(customCarrierLabel)) {
            setText(customCarrierLabel);
        } else {
            setText(TextUtils.isEmpty(str) ? getOperatorName() : str);
        }
    }

    private String getOperatorName() {
        String operatorName = getContext().getString(R.string.quick_settings_wifi_no_network);
        TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        if (isCN) {
            String operator = telephonyManager.getNetworkOperator();
            if (TextUtils.isEmpty(operator)) {
                operator = telephonyManager.getSimOperator();
            }
            SpnOverride mSpnOverride = new SpnOverride();
            operatorName = mSpnOverride.getSpn(operator);
        } else {
            operatorName = telephonyManager.getNetworkOperatorName();
        }
        if (TextUtils.isEmpty(operatorName)) {
            operatorName = telephonyManager.getSimOperatorName();
        }
        return operatorName;
    }

     public void getFontStyle(int font) {
        if (font == 0) {
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (font == 1) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (font == 2) {
            setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (font == 3) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (font == 4) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (font == 5) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (font == 6) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (font == 7) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (font == 8) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (font == 9) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (font == 10) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (font == 11) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (font == 12) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (font == 13) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (font == 14) {
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (font == 15) {
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (font == 16) {
                setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (font == 17) {
                setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (font == 18) {
                setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (font == 19) {
                setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (font == 20) {
                setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (font == 21) {
                setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (font == 22) {
                setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (font == 23) {
                setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (font == 24) {
                setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (font == 25) {
            setTypeface(Typeface.create("accuratist", Typeface.NORMAL));
        }
        if (font == 26) {
            setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
        }
        if (font == 27) {
            setTypeface(Typeface.create("amarante", Typeface.NORMAL));
        }
        if (font == 28) {
            setTypeface(Typeface.create("bariol", Typeface.NORMAL));
        }
        if (font == 29) {
            setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
        }
        if (font == 30) {
            setTypeface(Typeface.create("cocon", Typeface.NORMAL));
        }
        if (font == 31) {
            setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
        }

        if (font == 32) {
                setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
        }
        if (font == 33) {
                setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
        }
        if (font == 34) {
                setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
        }
        if (font == 35) {
                setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
        }
        if (font == 36) {
            setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
        }
        if (font == 37) {
            setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
        }
        if (font == 38) {
            setTypeface(Typeface.create("lato", Typeface.NORMAL));
        }
        if (font == 39) {
            setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
        }
        if (font == 40) {
            setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
        }
        if (font == 41) {
            setTypeface(Typeface.create("nunito", Typeface.NORMAL));
        }
        if (font == 42) {
            setTypeface(Typeface.create("quando", Typeface.NORMAL));
        }

        if (font == 43) {
                setTypeface(Typeface.create("redressed", Typeface.NORMAL));
        }
        if (font == 44) {
            setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (font == 45) {
            setTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
        }
        if (font == 46) {
            setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
        }
        if (font == 47) {
            setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
        }
        if (font == 48) {
            setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
        }
        if (font == 49) {
            setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
        }
        if (font == 50) {
            setTypeface(Typeface.create("storopia", Typeface.NORMAL));
        }
        if (font == 51) {
            setTypeface(Typeface.create("surfer", Typeface.NORMAL));
        }
        if (font == 52) {
            setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
        }
    }

    private void updateColor() {
        mCarrierColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_COLOR, 0xffffffff);
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
        }
    }

    private void updateSize() {
        mCarrierFontSize = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_SIZE, 14);
        setTextSize(mCarrierFontSize);
    }

    private void updateStyle() {
        mCarrierLabelFontStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_STYLE, FONT_NORMAL);
        getFontStyle(mCarrierLabelFontStyle);
    }
}

