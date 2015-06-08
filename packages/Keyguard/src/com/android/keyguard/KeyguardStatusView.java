/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.util.cm.WeatherController;
import com.android.internal.util.cm.WeatherControllerImpl;
import com.android.internal.util.crdroid.ImageHelper;
import com.android.internal.widget.LockPatternUtils;

import java.util.Date;
import java.util.Locale;

public class KeyguardStatusView extends GridLayout implements
        WeatherController.Callback  {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    private LockPatternUtils mLockPatternUtils;

    private TextView mAlarmStatusView;
    private TextClock mDateView;
    private TextClock mClockView;
    private TextView mOwnerInfo;
    private View mWeatherView;
    private TextView mWeatherCity;
    private TextView mWeatherWind;
    private ImageView mWeatherConditionImage;
    private Drawable mWeatherConditionDrawable;
    private TextView mWeatherCurrentTemp;
    private TextView mWeatherHumidity;
    private TextView mWeatherConditionText;
    private TextView mWeatherTimestamp;

    private boolean mShowWeather;
    private int mIconNameValue = 0;

    //On the first boot, keygard will start to receiver TIME_TICK intent.
    //And onScreenTurnedOff will not get called if power off when keyguard is not started.
    //Set initial value to false to skip the above case.
    private boolean mEnableRefresh = false;

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
            }
        }

        @Override
        public void onScreenTurnedOn() {
            setEnableMarquee(true);
            mEnableRefresh = true;
            refresh();
        }

        @Override
        public void onScreenTurnedOff(int why) {
            setEnableMarquee(false);
            mEnableRefresh = false;
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refresh();
            updateOwnerInfo();
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
        mWeatherController = new WeatherControllerImpl(mContext);
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
        mOwnerInfo = (TextView) findViewById(R.id.owner_info);
        mWeatherView = findViewById(R.id.keyguard_weather_view);
        mWeatherCity = (TextView) findViewById(R.id.city);
        mWeatherWind = (TextView) findViewById(R.id.wind);
        mWeatherConditionImage = (ImageView) findViewById(R.id.weather_image);
        mWeatherCurrentTemp = (TextView) findViewById(R.id.current_temp);
        mWeatherHumidity = (TextView) findViewById(R.id.humidity);
        mWeatherConditionText = (TextView) findViewById(R.id.condition);
        mWeatherTimestamp = (TextView) findViewById(R.id.timestamp);
        mLockPatternUtils = new LockPatternUtils(getContext());
        final boolean screenOn = KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        setEnableMarquee(screenOn);
        refresh();
        updateOwnerInfo();

        // Disable elegant text height because our fancy colon makes the ymin value huge for no
        // reason.
        mClockView.setElegantTextHeight(false);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
    }

    public void refreshTime() {
        mDateView.setFormat24Hour(Patterns.dateView);
        mDateView.setFormat12Hour(Patterns.dateView);

        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    private void refresh() {
        AlarmManager.AlarmClockInfo nextAlarm = mLockPatternUtils.getNextAlarm();
        Patterns.update(mContext, nextAlarm != null);

        refreshTime();
        refreshAlarmStatus(nextAlarm);
        updateWeatherSettings(false);
    }

    void refreshAlarmStatus(AlarmManager.AlarmClockInfo nextAlarm) {
        if (nextAlarm != null) {
            String alarm = formatNextAlarm(mContext, nextAlarm);
            mAlarmStatusView.setText(alarm);
            mAlarmStatusView.setContentDescription(
                    getResources().getString(R.string.keyguard_accessibility_next_alarm, alarm));
            mAlarmStatusView.setVisibility(View.VISIBLE);
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
        updateWeatherSettings(false);
        mWeatherController.addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        mWeatherController.removeCallback(this);
    }

    public int getAppWidgetId() {
        return LockPatternUtils.ID_DEFAULT_STATUS_WIDGET;
    }

    private String getOwnerInfo() {
        ContentResolver res = getContext().getContentResolver();
        String info = null;
        final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled();
        if (ownerInfoEnabled) {
            info = mLockPatternUtils.getOwnerInfo(mLockPatternUtils.getCurrentUser());
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
            mWeatherCity.setText("--");
            mWeatherWind.setText(null);
            mWeatherConditionDrawable = null;
            mWeatherCurrentTemp.setText(null);
            mWeatherHumidity.setText(null);
            mWeatherConditionText.setText(null);
            mWeatherTimestamp.setText(null);
            mWeatherView.setVisibility(View.GONE);
            updateWeatherSettings(true);
        } else {
            mWeatherCity.setText(info.city);
            mWeatherWind.setText(info.wind);
            mWeatherConditionDrawable = info.conditionDrawable;
            mWeatherCurrentTemp.setText(info.temp);
            mWeatherHumidity.setText(info.humidity);
            mWeatherConditionText.setText(info.condition);
            mWeatherTimestamp.setText(getCurrentDate());
            mWeatherView.setVisibility(mShowWeather ? View.VISIBLE : View.GONE);
            updateWeatherSettings(false);
        }
    }

    private String getCurrentDate() {
        Date now = new Date();
        long nowMillis = now.getTime();
        StringBuilder sb = new StringBuilder();
        sb.append(DateFormat.format("E", nowMillis));
        sb.append(" ");
        sb.append(DateFormat.getTimeFormat(getContext()).format(nowMillis));
        return sb.toString();
    }

    private void updateWeatherSettings(boolean forceHide) {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        View weatherPanel = findViewById(R.id.weather_panel);
        TextView noWeatherInfo = (TextView) findViewById(R.id.no_weather_info_text);
        int currentVisibleNotifications = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_VISIBLE_NOTIFICATIONS, 0);
        int maxAllowedNotifications = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_MAX_NOTIFICATIONS, 6);
        boolean forceHideByNumberOfNotifications = false;

        mShowWeather = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_SHOW_WEATHER, 0) == 1;
        boolean showLocation = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_SHOW_WEATHER_LOCATION, 1) == 1;
        boolean showTimestamp = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_SHOW_WEATHER_TIMESTAMP, 1) == 1;
        int iconNameValue = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON, 0);
        boolean colorizeAllIcons = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_COLORIZE_ALL_ICONS, 0) == 1;
        int hideMode = Settings.System.getInt(resolver,
                    Settings.System.LOCK_SCREEN_WEATHER_HIDE_PANEL, 0);
        int numberOfNotificationsToHide = Settings.System.getInt(resolver,
                       Settings.System.LOCK_SCREEN_WEATHER_NUMBER_OF_NOTIFICATIONS, 6);                
        int defaultPrimaryTextColor =
                res.getColor(R.color.keyguard_default_primary_text_color);
        int primaryTextColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_TEXT_COLOR, defaultPrimaryTextColor);

        if (hideMode == 0) {
            if (currentVisibleNotifications > maxAllowedNotifications) {
                forceHideByNumberOfNotifications = true;
            }
        } else if (hideMode == 1) {
            if (currentVisibleNotifications >= numberOfNotificationsToHide) {
                forceHideByNumberOfNotifications = true;
            }
        }

        int secondaryTextColor = (179 << 24) | (primaryTextColor & 0x00ffffff); // primaryTextColor with a transparency of 70%
        int defaultIconColor =
                res.getColor(R.color.keyguard_default_icon_color);
        int iconColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR, defaultIconColor);

        if (mWeatherView != null) {
            mWeatherView.setVisibility(
                (mShowWeather && !forceHideByNumberOfNotifications) ? View.VISIBLE : View.GONE);
        }
        if (forceHide) {
            noWeatherInfo.setVisibility(View.VISIBLE);
            weatherPanel.setVisibility(View.GONE);
            mWeatherConditionText.setVisibility(View.GONE);
            mWeatherTimestamp.setVisibility(View.GONE);
        } else {
            noWeatherInfo.setVisibility(View.GONE);
            weatherPanel.setVisibility(View.VISIBLE);
            mWeatherConditionText.setVisibility(View.VISIBLE);
            mWeatherCity.setVisibility(showLocation ? View.VISIBLE : View.INVISIBLE);
            mWeatherTimestamp.setVisibility(showTimestamp ? View.VISIBLE : View.GONE);
        }


        noWeatherInfo.setTextColor(primaryTextColor);
        mWeatherCity.setTextColor(primaryTextColor);
        mWeatherConditionText.setTextColor(primaryTextColor);
        mWeatherCurrentTemp.setTextColor(primaryTextColor);
        mWeatherHumidity.setTextColor(secondaryTextColor);
        mWeatherWind.setTextColor(secondaryTextColor);
        mWeatherTimestamp.setTextColor(secondaryTextColor);

        if (mIconNameValue != iconNameValue) {
            mIconNameValue = iconNameValue;
            mWeatherController.updateWeather();
        }

        mWeatherConditionImage.setImageDrawable(null);
        Drawable weatherIcon = mWeatherConditionDrawable;
        if (iconNameValue == 0 || colorizeAllIcons) {
            Bitmap coloredWeatherIcon =
                    ImageHelper.getColoredBitmap(weatherIcon, iconColor);
            mWeatherConditionImage.setImageBitmap(coloredWeatherIcon);
        } else {
            mWeatherConditionImage.setImageDrawable(weatherIcon);
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
            final String dateViewSkel = res.getString(hasAlarm
                    ? R.string.abbrev_wday_month_day_no_year_alarm
                    : R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);

            if (res.getBoolean(com.android.internal.R.bool.def_custom_dateformat)) {
                final String dateformat = Settings.System.getString(context.getContentResolver(),
                        Settings.System.DATE_FORMAT);
                if(dateformat != null)
                    dateView = dateformat.equals(dateView) ? dateView : dateformat;
                else
                    Log.e(TAG, "def_custom_dateformat is true but dataformat is not defined actually.");
            } else {
                final String key = locale.toString() + dateViewSkel + clockView12Skel
                        + clockView24Skel;
                if (key.equals(cacheKey)) {
                    return;
                }
                dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);
                cacheKey = key;
            }

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

        }
    }
}
