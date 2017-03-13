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
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.omni.DetailedWeatherView;
import com.android.systemui.omni.OmniJawsClient;

public class StatusBarWeatherImageLeft extends ImageView implements
        OmniJawsClient.OmniJawsObserver {

    private String TAG = StatusBarWeatherImageLeft.class.getSimpleName();

    private static final boolean DEBUG = false;

    private Context mContext;

    private int mStatusBarWeatherEnabled;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherData;
    private boolean mEnabled;
    private int mWeatherTempStyle;
    private int mWeatherImageColor;

    Handler mHandler;

    public StatusBarWeatherImageLeft(Context context) {
        this(context, null);
    }

    public StatusBarWeatherImageLeft(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatusBarWeatherImageLeft(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mHandler = new Handler();
        mWeatherClient = new OmniJawsClient(mContext);
        mEnabled = mWeatherClient.isOmniJawsEnabled();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
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
                    Settings.System.STATUS_BAR_WEATHER_IMAGE_COLOR),
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
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
        mWeatherImageColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_WEATHER_IMAGE_COLOR, 0xFFFFFFFF, UserHandle.USER_CURRENT);
        if(mWeatherTempStyle == 0) {
            setVisibility(View.GONE);
            return;
        }
        if (mStatusBarWeatherEnabled == 1
                || mStatusBarWeatherEnabled == 2
                || mStatusBarWeatherEnabled == 5) {
            queryAndUpdateWeather();
        } else {
            setVisibility(View.GONE);
        }
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather " + mEnabled);
            setImageDrawable(mWeatherClient.getDefaultWeatherConditionImage());
            if (mEnabled) {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    if (mStatusBarWeatherEnabled == 1
                            || mStatusBarWeatherEnabled == 2
                            || mStatusBarWeatherEnabled == 5) {
                        setImageDrawable(mWeatherClient.getWeatherConditionImage(
                                mWeatherData.conditionCode));
                        setVisibility(View.VISIBLE);
                        if(mWeatherImageColor != 0xFFFFFFFF) {
                           setColorFilter(mWeatherImageColor);
                        } else {
                          clearColorFilter();
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
}
