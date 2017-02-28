/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.util.rr.WeatherController;
import com.android.internal.util.rr.WeatherControllerImpl;

import com.android.internal.util.rr.ImageHelper;
import com.android.internal.widget.LockPatternUtils;

import cyanogenmod.weather.util.WeatherUtils;

import java.util.Date;
import java.text.NumberFormat;
import java.util.Locale;

public class KeyguardStatusView extends GridLayout implements
        WeatherController.Callback {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    private final LockPatternUtils mLockPatternUtils;
    private final AlarmManager mAlarmManager;

    private TextView mAlarmStatusView;
    private TextClock mDateView;
    private TextClock mClockView;
    private TextView mAmbientDisplayBatteryView;
    private TextView mOwnerInfo;

    private View mWeatherView;
    private TextView mWeatherCity;
    private ImageView mWeatherConditionImage;
    private Drawable mWeatherConditionDrawable;
    private TextView mWeatherCurrentTemp;
    private TextView mWeatherConditionText;
    private boolean mEnableRefresh = false;
    private boolean mShowWeather;
    private int mIconNameValue = 0;
    private int mWIconColor;

    private final int mWarningColor = 0xfff4511e; // deep orange 600
    private int mPrimaryTextColor;
    private int mLockClockFontSize;
    private int mLockDateFontSize;

    private SettingsObserver mSettingsObserver;
    private boolean mShowClock;
    private boolean mShowDate;
    private boolean mShowAlarm;
    private int dateFont;

    private int mTempColor;
    private int mConditionColor;
    private int mCityColor;
    private int mWindColor;
    private int mIconColor;
    private int alarmColor;

	private WeatherController mWeatherController;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            if (mEnableRefresh) {
                refresh();
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refresh();
                updateOwnerInfo();
                updateClockColor();
                updateClockDateColor();
                refreshLockFont();
            }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
            mEnableRefresh = true;
            refresh();
            
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
            mEnableRefresh = false;
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refresh();
            updateOwnerInfo();
            updateClockColor();
            updateClockDateColor();
            refreshLockFont();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mLockPatternUtils = new LockPatternUtils(getContext());
        mWeatherController = new WeatherControllerImpl(mContext);
        updateClockColor();
        updateClockDateColor();
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mAlarmStatusView != null) mAlarmStatusView.setSelected(enabled);
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        mDateView = (TextClock) findViewById(R.id.date_view);
        mClockView = (TextClock) findViewById(R.id.clock_view);
        mDateView.setShowCurrentUserTime(true);
        mClockView.setShowCurrentUserTime(true);
        mAmbientDisplayBatteryView = (TextView) findViewById(R.id.ambient_display_battery_view);
        mOwnerInfo = (TextView) findViewById(R.id.owner_info);
        mWeatherView = findViewById(R.id.keyguard_weather_view);
        mWeatherCity = (TextView) findViewById(R.id.city);
        mWeatherConditionImage = (ImageView) findViewById(R.id.weather_image);
        mWeatherCurrentTemp = (TextView) findViewById(R.id.current_temp);
        mWeatherConditionText = (TextView) findViewById(R.id.condition);

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refresh();
        updateOwnerInfo();
        updateClockColor();
        updateClockDateColor();
        refreshLockFont();

        // Disable elegant text height because our fancy colon makes the ymin value huge for no
        // reason.
        mClockView.setElegantTextHeight(false);
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        MarginLayoutParams layoutParams = (MarginLayoutParams) mClockView.getLayoutParams();
        layoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.bottom_text_spacing_digital);
        mClockView.setLayoutParams(layoutParams);
        updateclocksize();
        refreshdatesize();
        refreshLockFont();
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 0);
    }

    public void hideLockscreenItems() {
            mClockView = (TextClock) findViewById(R.id.clock_view);
            mClockView.setVisibility(mShowClock ? View.VISIBLE : View.INVISIBLE);
            mDateView = (TextClock) findViewById(R.id.date_view);
            mDateView.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
    }

    public void refreshTime() {
        mDateView.setFormat24Hour(Patterns.dateView);
        mDateView.setFormat12Hour(Patterns.dateView);

        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    private void refresh() {
        AlarmManager.AlarmClockInfo nextAlarm =
                mAlarmManager.getNextAlarmClock(UserHandle.USER_CURRENT);
        Patterns.update(mContext, nextAlarm != null);

        refreshTime();
        refreshAlarmStatus(nextAlarm);
        updateclocksize();
    }

    void refreshAlarmStatus(AlarmManager.AlarmClockInfo nextAlarm) {
        if (nextAlarm != null && mShowAlarm ) {
            String alarm = formatNextAlarm(mContext, nextAlarm);
            mAlarmStatusView.setText(alarm);
            mAlarmStatusView.setContentDescription(
                    getResources().getString(R.string.keyguard_accessibility_next_alarm, alarm));
            mAlarmStatusView.setVisibility(View.VISIBLE);
            mAlarmStatusView.setTextColor(alarmColor);
        } else {
            mAlarmStatusView.setVisibility(View.GONE);
        }
    }

    public static String formatNextAlarm(Context context, AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = DateFormat.is24HourFormat(context, ActivityManager.getCurrentUser())
                ? "EHm"
                : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String ownerInfo = getOwnerInfo();
        if (!TextUtils.isEmpty(ownerInfo)) {
            mOwnerInfo.setVisibility(View.VISIBLE);
            mOwnerInfo.setText(ownerInfo);
        } else {
            mOwnerInfo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        mWeatherController.addCallback(this);
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        mWeatherController.removeCallback(this);
        mSettingsObserver.unobserve();
    }

    private String getOwnerInfo() {
        String info = null;
        if (mLockPatternUtils.isDeviceOwnerInfoEnabled()) {
            // Use the device owner information set by device policy client via
            // device policy manager.
            info = mLockPatternUtils.getDeviceOwnerInfo();
        } else {
            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        return info;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onWeatherChanged(WeatherController.WeatherInfo info) {
        if (info.temp == null || info.condition == null) {
            if (mWeatherCity != null)
                mWeatherCity.setText(null);
            mWeatherConditionDrawable = null;
            if (mWeatherCurrentTemp != null)
                mWeatherCurrentTemp.setText(null);
            if (mWeatherConditionText != null)
                mWeatherConditionText.setText(null);
            if (mWeatherView != null)
                mWeatherView.setVisibility(View.GONE);
            updateSettings(true);
        } else {
            if (mWeatherCity != null)
                mWeatherCity.setText(info.city);
            mWeatherConditionDrawable = info.conditionDrawable;
            if (mWeatherCurrentTemp != null)
                mWeatherCurrentTemp.setText(info.temp);
            if (mWeatherConditionText != null)
                mWeatherConditionText.setText(info.condition);
            if (mWeatherView != null)
                mWeatherView.setVisibility(mShowWeather ? View.VISIBLE : View.GONE);
            updateSettings(false);
        }
    }

    private void refreshBatteryInfo() {
        final Resources res = getContext().getResources();
        KeyguardUpdateMonitor.BatteryStatus batteryStatus =
                KeyguardUpdateMonitor.getInstance(mContext).getBatteryStatus();

        mPrimaryTextColor =
                res.getColor(R.color.keyguard_default_primary_text_color);
        mIconColor =
                res.getColor(R.color.keyguard_default_primary_text_color);

        String percentage = "";
        int resId = 0;
        final int lowLevel = res.getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        final boolean useWarningColor = batteryStatus == null || batteryStatus.status == 1
                || (batteryStatus.level <= lowLevel && !batteryStatus.isPluggedIn());

        if (batteryStatus != null) {
            percentage = NumberFormat.getPercentInstance().format((double) batteryStatus.level / 100.0);
        }
        if (batteryStatus == null || batteryStatus.status == 1) {
            resId = R.drawable.ic_battery_unknown;
        } else {
            if (batteryStatus.level >= 96) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_full : R.drawable.ic_battery_full;
            } else if (batteryStatus.level >= 90) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_90 : R.drawable.ic_battery_90;
            } else if (batteryStatus.level >= 80) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_80 : R.drawable.ic_battery_80;
            } else if (batteryStatus.level >= 60) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_60 : R.drawable.ic_battery_60;
            } else if (batteryStatus.level >= 50) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_50 : R.drawable.ic_battery_50;
            } else if (batteryStatus.level >= 30) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_30 : R.drawable.ic_battery_30;
            } else if (batteryStatus.level >= lowLevel) {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_20 : R.drawable.ic_battery_20;
            } else {
                resId = batteryStatus.isPluggedIn()
                        ? R.drawable.ic_battery_charging_20 : R.drawable.ic_battery_alert;
            }
        }
        Drawable icon = resId > 0 ? res.getDrawable(resId).mutate() : null;
        if (icon != null) {
            icon.setTintList(ColorStateList.valueOf(useWarningColor ? mWarningColor : mIconColor));

        mAmbientDisplayBatteryView.setText(percentage);
        mAmbientDisplayBatteryView.setTextColor(useWarningColor
                ? mWarningColor : mPrimaryTextColor);
        mAmbientDisplayBatteryView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        }
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 0;

        if (lockClockFont == 0) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 2) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 3) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 5) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 6) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 7) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockClockFont == 8) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 9) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 10) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 11) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 12) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
    }

    public void updateclocksize() {
        int size = mLockClockFontSize;
        if (size == 50) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_50));
        } else if (size == 51) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51));
        } else if (size == 52) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52));
        } else if (size == 53) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53));
        } else if (size == 54) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54));
        } else if (size == 55) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55));
        } else if (size == 56) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56));
        } else if (size == 57) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57));
        } else if (size == 58) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58));
        } else if (size == 59) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59));
        } else if (size == 60) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60));
        } else if (size == 61) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61));
        } else if (size == 62) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62));
        } else if (size == 63) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63));
        } else if (size == 64) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64));
        } else if (size == 65) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
        } else if (size == 66) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
        } else if (size == 66) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
        } else if (size == 68) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
        } else if (size == 69) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
        } else if (size == 70) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
        } else if (size == 71) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
        } else if (size == 72) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
        } else if (size == 73) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
        } else if (size == 74) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
        } else if (size == 75) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
        } else if (size == 76) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
        } else if (size == 77) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
        } else if (size == 78) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
        } else if (size == 79) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
        } else if (size == 80) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
        } else if (size == 81) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
        } else if (size == 82) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
        } else if (size == 83) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
        } else if (size == 84) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
        }  else if (size == 85) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
        } else if (size == 86) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
        } else if (size == 87) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
         } else if (size == 88) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
        } else if (size == 89) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
         } else if (size == 90) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
        } else if (size == 91) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
        } else if (size == 92) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
        }  else if (size == 93) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
        } else if (size == 94) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
        } else if (size == 95) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
         } else if (size == 96) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
        } else if (size == 97) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
         } else if (size == 98) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
        } else if (size == 99) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
         } else if (size == 100) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
        } else if (size == 101) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
        } else if (size == 102) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_102));
        }  else if (size == 103) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_103));
        } else if (size == 104) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_104));
        } else if (size == 105) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_105));
         } else if (size == 106) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_106));
        } else if (size == 107) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_107));
         } else if (size == 108) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_108));
         }
    }

    public void refreshdatesize() {
    int size = mLockDateFontSize;
        if (size == 0) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1));
        } else if (size == 1) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1));
        } else if (size == 2) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_2));
        } else if (size == 3) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_3));
        } else if (size == 4) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_4));
        } else if (size == 5) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_5));
        } else if (size == 6) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_6));
        } else if (size == 7) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_7));
        } else if (size == 8) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_8));
        } else if (size == 9) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9));
        } else if (size == 10) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (size == 11) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (size == 12) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (size == 13) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (size == 14) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        }  else if (size == 15) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (size == 16) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (size == 17) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (size == 18) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (size == 19) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (size == 20) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (size == 21) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (size == 22) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (size == 23) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (size == 24) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (size == 25) {
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
        }
    }

    private void updateSettings(boolean forcehide) {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        View weatherPanel = findViewById(R.id.weather_panel);
        TextView noWeatherInfo = (TextView) findViewById(R.id.no_weather_info_text);
        AlarmManager.AlarmClockInfo nextAlarm =
            mAlarmManager.getNextAlarmClock(UserHandle.USER_CURRENT);
        boolean showLocation = Settings.System.getInt(resolver,
            Settings.System.LOCK_SCREEN_SHOW_WEATHER_LOCATION, 1) == 1;
        int iconNameValue = Settings.System.getInt(resolver,
            Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON, 0);

        int maxAllowedNotifications = 6;
        int currentVisibleNotifications = Settings.System.getInt(resolver,
            Settings.System.LOCK_SCREEN_VISIBLE_NOTIFICATIONS, 0);
        int hideMode = Settings.System.getInt(resolver,
            Settings.System.LOCK_SCREEN_WEATHER_HIDE_PANEL, 0);
        int numberOfNotificationsToHide = Settings.System.getInt(resolver,
            Settings.System.LOCK_SCREEN_WEATHER_NUMBER_OF_NOTIFICATIONS, 4);

        int primaryTextColor =
            res.getColor(R.color.keyguard_default_primary_text_color);
        // primaryTextColor with a transparency of 70%
        int secondaryTextColor = (179 << 24) | (primaryTextColor & 0x00ffffff);
        // primaryTextColor with a transparency of 50%
        int alarmTextAndIconColor = (128 << 24) | (primaryTextColor & 0x00ffffff);
        boolean forceHideByNumberOfNotifications = false;
        mWIconColor = res.getColor(R.color.keyguard_default_icon_color);

        int ownerInfoColor = Settings.System.getInt(resolver,
            Settings.System.LOCKSCREEN_OWNER_INFO_COLOR, 0xFFFFFFFF);

        if (hideMode == 0) {
            if (currentVisibleNotifications > maxAllowedNotifications) {
                forceHideByNumberOfNotifications = true;
            }
        } else if (hideMode == 1) {
            if (currentVisibleNotifications >= numberOfNotificationsToHide) {
                forceHideByNumberOfNotifications = true;
            }
        }
        if (mWeatherView != null) {
            mWeatherView.setVisibility(
                (mShowWeather && !forceHideByNumberOfNotifications) ? View.VISIBLE : View.GONE);
        }
        if (forcehide) {
            if (noWeatherInfo != null)
                noWeatherInfo.setVisibility(View.VISIBLE);
            if (weatherPanel != null)
                weatherPanel.setVisibility(View.GONE);
            if (mWeatherConditionText != null)
                mWeatherConditionText.setVisibility(View.GONE);
        } else {
            if (noWeatherInfo != null)
                noWeatherInfo.setVisibility(View.GONE);
            if (weatherPanel != null)
                weatherPanel.setVisibility(View.VISIBLE);
            if (mWeatherConditionText != null)
                mWeatherConditionText.setVisibility(View.VISIBLE);
            if (mWeatherCity != null)
                mWeatherCity.setVisibility(showLocation ? View.VISIBLE : View.INVISIBLE);
        }
        if (mWeatherCurrentTemp != null)
            mWeatherCurrentTemp.setTextColor(mTempColor);
        if (mWeatherConditionText != null)
            mWeatherConditionText.setTextColor(mConditionColor);
        if (mWeatherCity != null)
            mWeatherCity.setTextColor(mCityColor);
        if (dateFont == 0) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (dateFont == 1) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (dateFont == 2) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (dateFont == 3) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (dateFont == 4) {
            mDateView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (dateFont == 5) {
            mDateView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (dateFont == 6) {
            mDateView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (dateFont == 7) {
            mDateView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (dateFont == 8) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (dateFont == 9) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (dateFont == 10) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (dateFont == 11) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (dateFont == 12) {
            mDateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (dateFont == 13) {
            mDateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (dateFont == 14) {
            mDateView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (dateFont == 15) {
            mDateView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (dateFont == 16) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (dateFont == 17) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (dateFont == 18) {
            mDateView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (dateFont == 19) {
            mDateView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (dateFont == 20) {
            mDateView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (dateFont == 21) {
            mDateView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (dateFont == 22) {
            mDateView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (dateFont == 23) {
            mDateView.setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (dateFont == 24) {
            mDateView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }

        if (mOwnerInfo != null) {
            mOwnerInfo.setTextColor(ownerInfoColor);
        }

        if (mIconNameValue != iconNameValue) {
            mIconNameValue = iconNameValue;
            mWeatherController.updateWeather();
        }

        if (mWeatherConditionImage != null)
            mWeatherConditionImage.setImageDrawable(null);
        Drawable weatherIcon = mWeatherConditionDrawable;
        if (mIconColor == -2) {
            if (mWeatherConditionImage != null)
                mWeatherConditionImage.setImageDrawable(weatherIcon);
        } else {
            Bitmap coloredWeatherIcon = ImageHelper.getColoredBitmap(weatherIcon, mIconColor);
            if (mWeatherConditionImage != null)
                mWeatherConditionImage.setImageBitmap(coloredWeatherIcon);
        }
    }

    public void setDozing(boolean dozing) {
        if (dozing && showBattery()) {
            refreshBatteryInfo();
            if (mAmbientDisplayBatteryView.getVisibility() != View.VISIBLE) {
                mAmbientDisplayBatteryView.setVisibility(View.VISIBLE);
            }
        } else {
            if (mAmbientDisplayBatteryView.getVisibility() != View.GONE) {
                mAmbientDisplayBatteryView.setVisibility(View.GONE);
            }
        }
    }

    private boolean showBattery() {
        return Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.AMBIENT_DISPLAY_SHOW_BATTERY, 1) == 1;
    }

    private void updateClockColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CLOCK_COLOR, 0xFFFFFFFF);

        if (mClockView != null) {
            mClockView.setTextColor(color);
        }
    }

    private void updateClockDateColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CLOCK_DATE_COLOR, 0xFFFFFFFF);

        if (mDateView != null) {
            mDateView.setTextColor(color);
        }
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String dateView;
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context, boolean hasAlarm) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final ContentResolver resolver = context.getContentResolver();
            final boolean mShowAlarm = Settings.System.getIntForUser(resolver,
                    Settings.System.SHOW_LOCKSCREEN_ALARM, 1, UserHandle.USER_CURRENT) == 1;
            final String dateViewSkel = res.getString(hasAlarm && mShowAlarm
                    ? R.string.abbrev_wday_month_day_no_year_alarm
                    : R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;
            if (res.getBoolean(com.android.internal.R.bool.config_dateformat)) {
                final String dateformat = Settings.System.getString(context.getContentResolver(),
                        Settings.System.DATE_FORMAT);
                dateView = dateformat;
            } else {
                dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);
            }

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            if(!context.getResources().getBoolean(R.bool.config_showAmpm)){
                // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
                // format.  The following code removes the AM/PM indicator if we didn't want it.
                if (!clockView12Skel.contains("a")) {
                    clockView12 = clockView12.replaceAll("a", "").trim();
                }
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

            cacheKey = key;
        }
    }

    class SettingsObserver extends ContentObserver {
         SettingsObserver(Handler handler) {
             super(handler);
         }
 
         void observe() {
             ContentResolver resolver = mContext.getContentResolver();
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.SHOW_LOCKSCREEN_ALARM), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.HIDE_LOCKSCREEN_CLOCK), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.HIDE_LOCKSCREEN_DATE), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_CLOCK_FONTS), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_DATE_FONTS), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_CLOCK_FONTS), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCKCLOCK_FONT_SIZE), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCKDATE_FONT_SIZE), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_NUMBER_OF_NOTIFICATIONS), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_HIDE_PANEL), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_SHOW_WEATHER), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_TEMP_COLOR), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_CITY_COLOR), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR), false, this, UserHandle.USER_ALL);
             resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.LOCKSCREEN_ALARM_COLOR), false, this, UserHandle.USER_ALL);

             update();
         }
 
         void unobserve() {
             ContentResolver resolver = mContext.getContentResolver();
             resolver.unregisterContentObserver(this);
         }
 
         @Override
         public void onChange(boolean selfChange, Uri uri) {
             if (uri.equals(Settings.System.getUriFor(
                     Settings.System.SHOW_LOCKSCREEN_ALARM))) {
                 refresh();
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_CLOCK_FONTS))) {
                 refreshLockFont();
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_DATE_FONTS))) {
                 refreshdatesize();
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCKCLOCK_FONT_SIZE))) {
                 updateclocksize();
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCKDATE_FONT_SIZE))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_NUMBER_OF_NOTIFICATIONS))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_HIDE_PANEL))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_SHOW_WEATHER))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_TEMP_COLOR))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_CITY_COLOR))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR))) {
                 updateSettings(false);
             } else if (uri.equals(Settings.System.getUriFor(
                     Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR))) {                
                 refresh();
             }
             update();
         }
 
         public void update() {
           ContentResolver resolver = mContext.getContentResolver();
           int currentUserId = ActivityManager.getCurrentUser();
 
           mShowAlarm = Settings.System.getIntForUser(
                     resolver, Settings.System.SHOW_LOCKSCREEN_ALARM, 1, currentUserId) == 1;
           mShowClock = Settings.System.getIntForUser(
                     resolver, Settings.System.HIDE_LOCKSCREEN_CLOCK, 1, currentUserId) == 1;
           mShowDate = Settings.System.getIntForUser(
                     resolver, Settings.System.HIDE_LOCKSCREEN_DATE, 1, currentUserId) == 1;

           dateFont = Settings.System.getIntForUser(resolver,
                Settings.System.LOCK_DATE_FONTS, 8, UserHandle.USER_CURRENT);

           mShowWeather = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_SHOW_WEATHER, 0) == 1;

           mTempColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_TEMP_COLOR, 0xFFFFFFFF);
           mConditionColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR, 0xFFFFFFFF);
           mCityColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_CITY_COLOR, 0xFFFFFFFF);
           mIconColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR, -2);
           alarmColor = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_ALARM_COLOR, 0xFFFFFFFF);

           mLockClockFontSize = Settings.System.getIntForUser(resolver,
             Settings.System.LOCKCLOCK_FONT_SIZE,
             getResources().getDimensionPixelSize(R.dimen.widget_big_font_size),
             UserHandle.USER_CURRENT);
           mLockDateFontSize = Settings.System.getIntForUser(resolver,
             Settings.System.LOCKDATE_FONT_SIZE,
             getResources().getDimensionPixelSize(R.dimen.widget_label_font_size),
             UserHandle.USER_CURRENT);
        	 hideLockscreenItems();
        	 refreshLockFont();
             updateclocksize();
             refreshdatesize();
        	 updateSettings(false);
         }
     }
}
