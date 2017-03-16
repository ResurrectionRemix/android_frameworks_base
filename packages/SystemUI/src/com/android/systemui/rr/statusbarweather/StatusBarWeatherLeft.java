/*
 * Copyright (C) 2017 AICP
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

package com.android.systemui.rr.statusbarweather;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.omni.DetailedWeatherView;
import com.android.systemui.omni.OmniJawsClient;

public class StatusBarWeatherLeft extends TextView implements
        OmniJawsClient.OmniJawsObserver {

    private static final String TAG = StatusBarWeatherLeft.class.getSimpleName();

    private static final boolean DEBUG = false;

    private Context mContext;

    private int mStatusBarWeatherEnabled;
    private TextView mStatusBarWeatherInfo;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherData;
    private boolean mEnabled;
    private int mWeatherTempStyle;
    private int mWeatherTempState;
    private int mWeatherTempColor;
    private int mWeatherTempSize;
    private int mWeatherTempFontStyle = FONT_NORMAL;
    // Weather temperature
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


    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_WEATHER_TEMP_STYLE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP),
                  false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.STATUS_BAR_WEATHER_SIZE),
                  false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.STATUS_BAR_WEATHER_FONT_STYLE),
                  false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.STATUS_BAR_WEATHER_COLOR),
                  false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public StatusBarWeatherLeft(Context context) {
        this(context, null);

    }

    public StatusBarWeatherLeft(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatusBarWeatherLeft(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mHandler = new Handler();
        mWeatherClient = new OmniJawsClient(mContext);
        mEnabled = mWeatherClient.isOmniJawsEnabled();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mEnabled = mWeatherClient.isOmniJawsEnabled();
        mWeatherClient.addObserver(this);
        queryAndUpdateWeather();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWeatherClient.removeObserver(this);
        mWeatherClient.cleanupObserver();
    }

    @Override
    public void weatherUpdated() {
        if (DEBUG) Log.d(TAG, "weatherUpdated");
        queryAndUpdateWeather();
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mStatusBarWeatherEnabled = Settings.System.getIntForUser(
                resolver, Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP, 0,
                UserHandle.USER_CURRENT);
        mWeatherTempStyle = Settings.System.getIntForUser(mContext.getContentResolver(), 
                Settings.System.STATUS_BAR_WEATHER_TEMP_STYLE, 0,
                UserHandle.USER_CURRENT);
        mWeatherTempSize = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_WEATHER_SIZE, 14,
                UserHandle.USER_CURRENT);
        mWeatherTempFontStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_WEATHER_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);
        mWeatherTempColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_WEATHER_COLOR, 0xFFFFFFFF,
                UserHandle.USER_CURRENT);
        if(mWeatherTempStyle == 0) {
            setVisibility(View.GONE);
            return;
        }
        if (mStatusBarWeatherEnabled != 0 && mStatusBarWeatherEnabled != 5) {
            queryAndUpdateWeather();
        } else {
            setVisibility(View.GONE);
        }
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather " + mEnabled);
            if (mEnabled) {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    if (mStatusBarWeatherEnabled != 0
                            || mStatusBarWeatherEnabled != 5) {
                        if (mStatusBarWeatherEnabled == 2 || mStatusBarWeatherEnabled == 4) {
                            setText(mWeatherData.temp);
                        } else {
                            setText(mWeatherData.temp + mWeatherData.tempUnits);
                        }
                        if (mStatusBarWeatherEnabled != 0 && mStatusBarWeatherEnabled != 5) {
                            setVisibility(View.VISIBLE);
                            updateattributes();
                        }
                    }
                } else {
                    setVisibility(View.GONE);
                }
            } else {
                setVisibility(View.GONE);
            }
        } catch(Exception e) {
            // Do nothing
        }
        if(mWeatherTempStyle == 0) {
           setVisibility(View.GONE);
        }
    }

   public void updateattributes() {
        try {
            setTextColor(mWeatherTempColor);
            setTextSize(mWeatherTempSize);
            switch (mWeatherTempFontStyle) {
                        case FONT_NORMAL:
                        default:
                              setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                            break;
                        case FONT_ITALIC:
                              setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                            break;
                        case FONT_BOLD:
                              setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                            break;
                        case FONT_BOLD_ITALIC:
                              setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                            break;
                        case FONT_LIGHT:
                              setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                            break;
                        case FONT_LIGHT_ITALIC:
                              setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                            break;
                        case FONT_THIN:
                              setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                            break;
                        case FONT_THIN_ITALIC:
                              setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                            break;
                        case FONT_CONDENSED:
                              setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                            break;
                        case FONT_CONDENSED_ITALIC:
                              setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                            break;
                        case FONT_CONDENSED_LIGHT:
                              setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                            break;
                        case FONT_CONDENSED_LIGHT_ITALIC:
                              setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                            break;
                        case FONT_CONDENSED_BOLD:
                              setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                            break;
                        case FONT_CONDENSED_BOLD_ITALIC:
                              setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                            break;
                        case FONT_MEDIUM:
                              setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                            break;
                        case FONT_MEDIUM_ITALIC:
                              setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                            break;
                        case FONT_BLACK:
                              setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                            break;
                        case FONT_BLACK_ITALIC:
                              setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                            break;
                        case FONT_DANCINGSCRIPT:
                              setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                            break;
                        case FONT_DANCINGSCRIPT_BOLD:
                              setTypeface(Typeface.create("cursive", Typeface.BOLD));
                            break;
                        case FONT_COMINGSOON:
                              setTypeface(Typeface.create("casual", Typeface.NORMAL));
                            break;
                        case FONT_NOTOSERIF:
                              setTypeface(Typeface.create("serif", Typeface.NORMAL));
                            break;
                        case FONT_NOTOSERIF_ITALIC:
                              setTypeface(Typeface.create("serif", Typeface.ITALIC));
                            break;
                        case FONT_NOTOSERIF_BOLD:
                              setTypeface(Typeface.create("serif", Typeface.BOLD));
                            break;
                        case FONT_NOTOSERIF_BOLD_ITALIC:
                              setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                            break;
                }
        } catch(Exception e) {
            // Do nothing
        }
    }
}
