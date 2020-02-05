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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.omni.DetailedWeatherView;
import org.omnirom.omni.OmniJawsClient;
import com.android.internal.util.rr.RRFontHelper;

import java.util.Arrays;

public class QsHeaderWeather extends TextView implements
        OmniJawsClient.OmniJawsObserver {

    private static final String TAG = QsHeaderWeather.class.getSimpleName();

    private static final boolean DEBUG = false;

    private Context mContext;

    private int mStatusBarWeatherEnabled;
    private TextView mStatusBarWeatherInfo;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherData;
    private boolean mEnabled;
    private boolean mWeatherInHeaderView;
    private int mColor;
    private int mStyle;
    private int mSize;

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
	            Settings.System.STATUS_BAR_SHOW_WEATHER_LOCATION), false, this,
		    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
		    Settings.System.STATUS_BAR_WEATHER_COLOR), false, this,
   	            UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
		    Settings.System.STATUS_BAR_WEATHER_FONT), false, this,
   	            UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
		    Settings.System.STATUS_BAR_WEATHER_FONT_SIZE), false, this,
   	            UserHandle.USER_ALL);
            updateSettings(false);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings(true);
        }
    }

    public QsHeaderWeather(Context context) {
        this(context, null);

    }

    public QsHeaderWeather(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsHeaderWeather(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
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

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
    }

    public void updateSettings(boolean onChange) {
        ContentResolver resolver = mContext.getContentResolver();
        mStatusBarWeatherEnabled = Settings.System.getIntForUser(
                resolver, Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP, 0,
                UserHandle.USER_CURRENT);
        mWeatherInHeaderView = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_WEATHER_LOCATION, 0,
                UserHandle.USER_CURRENT) == 1;
        mColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_WEATHER_COLOR, 0xffffffff,
                UserHandle.USER_CURRENT);
        mStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_WEATHER_FONT, 0,
                UserHandle.USER_CURRENT);
        mSize = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_WEATHER_FONT_SIZE, 14,
                UserHandle.USER_CURRENT);
        setTextSize(mSize);
        setTextColor(mColor);
        RRFontHelper.setFontType(this, mStyle);
        if ((mStatusBarWeatherEnabled != 0 && mStatusBarWeatherEnabled != 5)
                                           && mWeatherInHeaderView) {
            mWeatherClient.setOmniJawsEnabled(true);
            queryAndUpdateWeather();
        } else {
            setVisibility(View.GONE);
        }

        if (onChange && mStatusBarWeatherEnabled == 0) {
            // Disable OmniJaws if tile isn't used either
            String[] tiles = Settings.Secure.getStringForUser(resolver,
                    Settings.Secure.QS_TILES, UserHandle.USER_CURRENT).split(",");
            boolean weatherTileEnabled = Arrays.asList(tiles).contains("weather");
            Log.d(TAG, "Weather tile enabled " + weatherTileEnabled);
            if (!weatherTileEnabled  && !isLockscreenWeatherEnabled()) {
                mWeatherClient.setOmniJawsEnabled(false);
            }
        }
    }

    public boolean isLockscreenWeatherEnabled() {
       return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather " + mEnabled);
            if (mEnabled) {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    if ((mStatusBarWeatherEnabled != 0
                            || mStatusBarWeatherEnabled != 5) && mWeatherInHeaderView) {
                        if (mStatusBarWeatherEnabled == 2 || mStatusBarWeatherEnabled == 4) {
                            setText(mWeatherData.temp);
                        } else {
                            setText(mWeatherData.temp + mWeatherData.tempUnits);
                        }
                        if (mStatusBarWeatherEnabled != 0 && mStatusBarWeatherEnabled != 5 && mWeatherInHeaderView) {
                            setVisibility(View.VISIBLE);
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
    }
}


