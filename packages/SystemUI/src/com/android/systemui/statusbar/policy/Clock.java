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

import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
<<<<<<< HEAD
import android.provider.AlarmClock;
=======
import android.os.Handler;
>>>>>>> 27d83b0... [1/2] Clock mode
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.View;
<<<<<<< HEAD
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
=======
>>>>>>> 27d83b0... [1/2] Clock mode
import android.widget.TextView;

import com.android.systemui.DemoMode;

<<<<<<< HEAD
import com.android.internal.R;
=======
import com.android.systemui.R;
>>>>>>> 27d83b0... [1/2] Clock mode

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import libcore.icu.LocaleData;

/**
 * Digital clock for the status bar.
 */
public class Clock extends TextView implements DemoMode, OnClickListener, OnLongClickListener {
    private boolean mAttached;
    private Calendar mCalendar;
    private String mClockFormatString;
    private SimpleDateFormat mClockFormat;
    private Locale mLocale;

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

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            updateView();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
            updateView();
        }
    };
    private int mClockColor;

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (isClickable()) {
            setOnClickListener(this);
            setOnLongClickListener(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            ContentResolver resolver = mContext.getContentResolver();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_USER_SWITCHED);

            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.STATUSBAR_CLOCK_AM_PM_STYLE),
                    false, mSettingsObserver);
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.STATUSBAR_CLOCK_STYLE),
                    false, mSettingsObserver);
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.STATUSBAR_CLOCK_COLOR),
                    false, mSettingsObserver);
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.STATUSBAR_CLOCK_WEEKDAY),
                    false, mSettingsObserver);
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());
        updateSettings();
        updateView();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            getContext().unregisterReceiver(mIntentReceiver);
            getContext().getContentResolver().unregisterContentObserver(mSettingsObserver);
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
        if (mDemoMode) return;
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
    }

    private final CharSequence getSmallTime() {
        Context context = getContext();
        boolean is24 = DateFormat.is24HourFormat(context);
        LocaleData d = LocaleData.get(context.getResources().getConfiguration().locale);

        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        SimpleDateFormat sdf;
        String format = is24 ? d.timeFormat24 : d.timeFormat12;
        if (!format.equals(mClockFormatString)) {
            mClockFormat = sdf = new SimpleDateFormat(format);
            mClockFormatString = format;
        } else {
            sdf = mClockFormat;
        }

        String todayIs = null;
        String result = sdf.format(mCalendar.getTime());

        if (mWeekdayStyle != WEEKDAY_STYLE_GONE) {
            todayIs = (new SimpleDateFormat("E")).format(mCalendar.getTime()) + " ";
            result = todayIs + result;
        }

        SpannableStringBuilder formatted = new SpannableStringBuilder(result);

        if (!is24) {
            if (mAmPmStyle != AM_PM_STYLE_NORMAL) {
                String AmPm;
                if (format.indexOf("a") == 0) {
                    if (format.indexOf("a ") == 0) {
                        AmPm = (new SimpleDateFormat("a ")).format(mCalendar.getTime());
                    } else {
                        AmPm = (new SimpleDateFormat("a")).format(mCalendar.getTime());
                    }
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

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        int defaultColor = getResources().getColor(R.color.status_bar_clock_color);

        mAmPmStyle = Settings.AOKP.getInt(resolver,
                Settings.AOKP.STATUSBAR_CLOCK_AM_PM_STYLE, AM_PM_STYLE_GONE);
        mClockStyle = Settings.AOKP.getInt(resolver,
                Settings.AOKP.STATUSBAR_CLOCK_STYLE, STYLE_CLOCK_RIGHT);
        mWeekdayStyle = Settings.AOKP.getInt(resolver,
                Settings.AOKP.STATUSBAR_CLOCK_WEEKDAY, WEEKDAY_STYLE_GONE);
        mClockColor = Settings.AOKP.getInt(resolver,
                Settings.AOKP.STATUSBAR_CLOCK_COLOR, defaultColor);
        if (mClockColor == Integer.MIN_VALUE) {
            // flag to reset the color
            mClockColor = defaultColor;
        }
    }

    protected void updateView() {
        setTextColor(mClockColor);
        updateClockVisibility();
        updateClock();
    }

    protected void updateClockVisibility() {
        if (mClockStyle == STYLE_CLOCK_RIGHT)
            setVisibility(View.VISIBLE);
        else
            setVisibility(View.GONE);
    }

    private void collapseStartActivity(Intent what) {
        // don't do anything if the activity can't be resolved (e.g. app disabled)
        if (getContext().getPackageManager().resolveActivity(what, 0) == null) {
            return;
        }

        // collapse status bar
        StatusBarManager statusBarManager = (StatusBarManager) getContext().getSystemService(
                Context.STATUS_BAR_SERVICE);
        statusBarManager.collapsePanels();

        // dismiss keyguard in case it was active and no passcode set
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (Exception ex) {
            // no action needed here
        }

        // start activity
        what.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(what);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        collapseStartActivity(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.settings.DATE_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        collapseStartActivity(intent);

        // consume event
        return true;
    }

    private boolean mDemoMode;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            updateClock();
        } else if (mDemoMode && command.equals(COMMAND_CLOCK)) {
            String millis = args.getString("millis");
            String hhmm = args.getString("hhmm");
            if (millis != null) {
                mCalendar.setTimeInMillis(Long.parseLong(millis));
            } else if (hhmm != null && hhmm.length() == 4) {
                int hh = Integer.parseInt(hhmm.substring(0, 2));
                int mm = Integer.parseInt(hhmm.substring(2));
                mCalendar.set(Calendar.HOUR, hh);
                mCalendar.set(Calendar.MINUTE, mm);
            }
            setText(getSmallTime());
        }
    }
}
