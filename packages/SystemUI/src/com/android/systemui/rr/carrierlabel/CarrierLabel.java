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

package com.android.systemui.rr.carrierlabel;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
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

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.rr.Utils;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.rr.carrierlabel.SpnOverride;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;
import com.android.internal.utils.du.UserContentObserver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CarrierLabel extends TextView implements DarkReceiver {

    private Context mContext;
    private boolean mAttached;
    private static boolean isCN;
    private RRSettingsObserver mRRSettingsObserver;

    private int mCarrierLabelFontStyle = FONT_NORMAL;
    public static final int FONT_NORMAL = 0;
    public static final int FONT_ITALIC = 1;
    public static final int FONT_BOLD = 2;
    public static final int FONT_BOLD_ITALIC = 3;
    public static final int FONT_LIGHT = 4;
    public static final int FONT_LIGHT_ITALIC = 5;
    public static final int FONT_THIN = 6;
    public static final int FONT_THIN_ITALIC = 7;
    public static final int FONT_CONDENSED = 8;
    public static final int FONT_CONDENSED_ITALIC = 9;
    public static final int FONT_CONDENSED_LIGHT = 10;
    public static final int FONT_CONDENSED_LIGHT_ITALIC = 11;
    public static final int FONT_CONDENSED_BOLD = 12;
    public static final int FONT_CONDENSED_BOLD_ITALIC = 13;
    public static final int FONT_MEDIUM = 14;
    public static final int FONT_MEDIUM_ITALIC = 15;
    public static final int FONT_BLACK = 16;
    public static final int FONT_BLACK_ITALIC = 17;
    public static final int FONT_DANCINGSCRIPT = 18;
    public static final int FONT_DANCINGSCRIPT_BOLD = 19;
    public static final int FONT_COMINGSOON = 20;
    public static final int FONT_NOTOSERIF = 21;
    public static final int FONT_NOTOSERIF_ITALIC = 22;
    public static final int FONT_NOTOSERIF_BOLD = 23;
    public static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;
    private int mCarrierFontSize;

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
        mRRSettingsObserver = new RRSettingsObserver(new Handler());
        mRRSettingsObserver.observe();
        updateSize();
        updateFont();
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
        setTextColor(DarkIconDispatcher.getTint(area, this, tint));
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

    private class RRSettingsObserver extends UserContentObserver {
        RRSettingsObserver(Handler handler) {
            super(handler);
        }

        protected void observe() {
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                   Settings.System.STATUS_BAR_CARRIER_FONT_STYLE), 
                   false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                   Settings.System.STATUS_BAR_CARRIER_FONT_SIZE), 
                   false, this, UserHandle.USER_ALL);
            update();
        }

        protected void unobserve() {
            super.unobserve();
            getContext().getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            updateSize();
            updateFont();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }
    }


    public void getFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.ITALIC));
                break;
            case FONT_BOLD:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                setTypeface(Typeface.create("sans-serif-light",
                    Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-light",
                    Typeface.ITALIC));
                break;
            case FONT_THIN:
                setTypeface(Typeface.create("sans-serif-thin",
                    Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                setTypeface(Typeface.create("sans-serif-thin",
                    Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                setTypeface(Typeface.create("sans-serif-condensed-light",
                    Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed-light",
                    Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                setTypeface(Typeface.create("sans-serif-medium",
                    Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                setTypeface(Typeface.create("sans-serif-medium",
                    Typeface.ITALIC));
                break;
            case FONT_BLACK:
                setTypeface(Typeface.create("sans-serif-black",
                    Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                setTypeface(Typeface.create("sans-serif-black",
                    Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                setTypeface(Typeface.create("cursive",
                    Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                setTypeface(Typeface.create("cursive",
                    Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                setTypeface(Typeface.create("casual",
                    Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                setTypeface(Typeface.create("serif",
                    Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                setTypeface(Typeface.create("serif",
                    Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                setTypeface(Typeface.create("serif",
                    Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                setTypeface(Typeface.create("serif",
                    Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void updateSize() {
        mCarrierFontSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_SIZE, 10,
                UserHandle.USER_CURRENT);

        setTextSize(mCarrierFontSize);
    }

    private void updateFont() {
        mCarrierLabelFontStyle = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);

        getFontStyle(mCarrierLabelFontStyle);
    }
}
