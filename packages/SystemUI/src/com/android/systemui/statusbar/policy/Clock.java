/*
 * Copyright (C) 2006 The Android Open Source Project
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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import com.android.internal.R;

/**
 * Digital clock for the status bar.
 */
public class Clock extends TextView {
    private boolean mAttached;
    private Calendar mCalendar;
    private String mClockFormatString;
    private SimpleDateFormat mClockFormat;
    private Locale mLocale;
    private SettingsObserver mSettingsObserver;

    private static final int AM_PM_STYLE_NORMAL  = 0;
    private static final int AM_PM_STYLE_SMALL   = 1;
    private static final int AM_PM_STYLE_GONE    = 2;

    protected int mAmPmStyle = AM_PM_STYLE_GONE;

    public static final int WEEKDAY_STYLE_GONE   = 0;
    public static final int WEEKDAY_STYLE_SMALL  = 1;
    public static final int WEEKDAY_STYLE_NORMAL = 2;

    protected int mWeekdayStyle = WEEKDAY_STYLE_GONE;

    public static final int STYLE_HIDE_CLOCK     = 0;
    public static final int STYLE_CLOCK_RIGHT    = 1;
    public static final int STYLE_CLOCK_CENTER   = 2;

    protected int mClockStyle = STYLE_CLOCK_RIGHT;

    protected int mClockColor;

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mClockColor = getTextColors().getDefaultColor();
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_USER_SWITCHED);

            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());

            // NOTE: It's safe to do these after registering the receiver since the receiver always runs
            // in the main thread, therefore the receiver can't run before this method returns.

            // The time zone may have changed while the receiver wasn't registered, so update the Time
            mCalendar = Calendar.getInstance(TimeZone.getDefault());

            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
        }

        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                if (mClockFormat != null) {
                    mClockFormat.setTimeZone(mCalendar.getTimeZone());
                }
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                final Locale newLocale = getResources().getConfiguration().locale;
                if (! newLocale.equals(mLocale)) {
                    mLocale = newLocale;
                    mClockFormatString = ""; // force refresh
                }
            }
            updateClock();
        }
    };

    final void updateClock() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
    }

    private final CharSequence getSmallTime() {
        Context context = getContext();
        boolean b24 = DateFormat.is24HourFormat(context);
        int res;

        if (b24) {
            res = R.string.twenty_four_hour_time_format;
        } else {
            res = R.string.twelve_hour_time_format;
        }

        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        SimpleDateFormat sdf;
        String format = context.getString(res);
        if (!format.equals(mClockFormatString)) {
            mClockFormat = sdf = new SimpleDateFormat(format);
            mClockFormatString = format;
        } else {
            sdf = mClockFormat;
        }

        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_WEEK);

        String todayIs = null;
        String result = sdf.format(mCalendar.getTime());

        if (mWeekdayStyle != WEEKDAY_STYLE_GONE) {
            todayIs = (new SimpleDateFormat("E")).format(mCalendar.getTime()) + " ";
            result = todayIs + result;
        }

        SpannableStringBuilder formatted = new SpannableStringBuilder(result);

        if (!b24) {
            if (mAmPmStyle != AM_PM_STYLE_NORMAL) {
                String AmPm;
                if (format.indexOf("a")==0) {
                    AmPm = (new SimpleDateFormat("a ")).format(mCalendar.getTime());
                } else {
                    AmPm = (new SimpleDateFormat(" a")).format(mCalendar.getTime());
                }
                if (mAmPmStyle == AM_PM_STYLE_GONE) {
                    formatted.delete(result.indexOf(AmPm), result.lastIndexOf(AmPm)+AmPm.length());
                } else {
                    if (mAmPmStyle == AM_PM_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, result.indexOf(AmPm), result.lastIndexOf(AmPm)+AmPm.length(),
                                Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                }
            }
        }
        if (mWeekdayStyle != WEEKDAY_STYLE_NORMAL) {
            if (todayIs != null) {
                if (mWeekdayStyle == WEEKDAY_STYLE_GONE) {
                    formatted.delete(result.indexOf(todayIs), result.lastIndexOf(todayIs)+todayIs.length());
                } else {
                    if (mWeekdayStyle == WEEKDAY_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, result.indexOf(todayIs), result.lastIndexOf(todayIs)+todayIs.length(),
                                          Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                }
            }
        }
        return formatted;
    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_STYLE), false,
                    this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_COLOR), false,
                    this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_WEEKDAY), false,
                    this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        int newColor = 0;

        mAmPmStyle = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE, AM_PM_STYLE_GONE);   
        mClockStyle = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_STYLE, STYLE_CLOCK_RIGHT);
        mWeekdayStyle = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_WEEKDAY, WEEKDAY_STYLE_GONE);

        newColor = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_COLOR, mClockColor);
        if (newColor < 0 && newColor != mClockColor) {
            mClockColor = newColor;
            setTextColor(mClockColor);
        }
        updateClockVisibility();
        updateClock();
    }

    protected void updateClockVisibility() {
        if (mClockStyle == STYLE_CLOCK_RIGHT)
            setVisibility(View.VISIBLE);
        else
            setVisibility(View.GONE);
    }
}

