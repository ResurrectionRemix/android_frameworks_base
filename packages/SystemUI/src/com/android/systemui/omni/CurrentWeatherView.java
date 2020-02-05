/*
* Copyright (C) 2018 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.omni;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Handler;
import android.os.UserHandle;
import androidx.core.graphics.ColorUtils;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.settingslib.Utils;

import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.omnirom.omni.OmniJawsClient;

public class CurrentWeatherView extends FrameLayout implements OmniJawsClient.OmniJawsObserver {

    static final String TAG = "SystemUI:CurrentWeatherView";
    static final boolean DEBUG = false;

    private ImageView mCurrentImage;
    private OmniJawsClient mWeatherClient;
    private TextView mLeftText;
    private TextView mRightText;
    private int mTextColor;
    private float mDarkAmount;
    private boolean mUpdatesEnabled;
    private SettingsObserver mSettingsObserver;
    private int mRightTextColor;
    private int mLeftTextColor;
    private int mCurrentImageColor;
    private LinearLayout mLayout;

    public CurrentWeatherView(Context context) {
        this(context, null);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void enableUpdates() {
        if (mUpdatesEnabled) {
            return;
        }
        if (DEBUG) Log.d(TAG, "enableUpdates");
        setVisibility(View.VISIBLE);
        mWeatherClient = new OmniJawsClient(getContext(), false);
        if (mWeatherClient.isOmniJawsEnabled()) {
            mWeatherClient.addSettingsObserver();
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
            mUpdatesEnabled = true;
        }
    }

    public void disableUpdates() {
        if (!mUpdatesEnabled) {
            return;
        }
        if (DEBUG) Log.d(TAG, "disableUpdates");
        setVisibility(View.GONE);
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
            mWeatherClient.cleanupObserver();
            mUpdatesEnabled = false;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCurrentImage  = (ImageView) findViewById(R.id.current_image);
        mLeftText = (TextView) findViewById(R.id.left_text);
        mRightText = (TextView) findViewById(R.id.right_text);
        mLayout = findViewById(R.id.current);
        mTextColor = mLeftText.getCurrentTextColor();
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        mSettingsObserver.update();
    }

    private void updateWeatherData(OmniJawsClient.WeatherInfo weatherData) {
        mRightTextColor = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.LOCK_SCREEN_WEATHER_TEMP_COLOR, 0xFFFFFFFF, UserHandle.USER_CURRENT);
        mLeftTextColor = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.LOCK_SCREEN_WEATHER_CITY_COLOR, 0xFFFFFFFF, UserHandle.USER_CURRENT);
        mCurrentImageColor = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR, 0xFFFFFFFF, UserHandle.USER_CURRENT);
        if (DEBUG) Log.d(TAG, "updateWeatherData");

        if (!mWeatherClient.isOmniJawsEnabled() || weatherData == null) {
            setErrorView();
            return;
        }
        Drawable d = mWeatherClient.getWeatherConditionImage(weatherData.conditionCode);
        d = d.mutate();
        updateTint(d);
        mCurrentImage.setImageDrawable(d);
        mRightText.setText(weatherData.temp + " " + weatherData.tempUnits);
        mLeftText.setText(weatherData.city);
        mRightText.setTextColor(mRightTextColor);
        mLeftText.setTextColor(mLeftTextColor);
        if (mCurrentImageColor != 0xFFFFFFFF) {
            mCurrentImage.setImageTintList((d instanceof VectorDrawable) ? 
                 ColorStateList.valueOf(mCurrentImageColor) : null);
        } else {
            updateTint(d);
        }
    }

    private int getTintColor() {
        return Utils.getColorAttrDefaultColor(mContext, R.attr.wallpaperTextColor);
    }

    private void setErrorView() {
        Drawable d = mContext.getResources().getDrawable(R.drawable.ic_qs_weather_default_off_white);
        updateTint(d);
        mCurrentImage.setImageDrawable(d);
        mLeftText.setText("");
        mRightText.setText("");
    }

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        // since this is shown in ambient and lock screen
        // it would look bad to show every error since the
        // screen-on revovery of the service had no chance
        // to run fast enough
        // so only show the disabled state
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            setErrorView();
        }
    }

    @Override
    public void weatherUpdated() {
        if (DEBUG) Log.d(TAG, "weatherUpdated");
        queryAndUpdateWeather();
    }

    @Override
    public void updateSettings() {
        if (DEBUG) Log.d(TAG, "updateSettings");
        OmniJawsClient.WeatherInfo weatherData = mWeatherClient.getWeatherInfo();
        updateWeatherData(weatherData);
    }

    private void queryAndUpdateWeather() {
        if (mWeatherClient == null) return;
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather");
            mWeatherClient.queryWeather();
            OmniJawsClient.WeatherInfo weatherData = mWeatherClient.getWeatherInfo();
            updateWeatherData(weatherData);
        } catch(Exception e) {
            // Do nothing
        }
    }

    public void blendARGB(float darkAmount) {
        mDarkAmount = darkAmount;
        mLeftText.setTextColor(ColorUtils.blendARGB(mTextColor, Color.WHITE, darkAmount));
        mRightText.setTextColor(ColorUtils.blendARGB(mTextColor, Color.WHITE, darkAmount));

        if (mWeatherClient != null) {
            // update image with correct tint
            OmniJawsClient.WeatherInfo weatherData = mWeatherClient.getWeatherInfo();
            updateWeatherData(weatherData);
        }
    }

    private void updateTint(Drawable d) {
        if (mDarkAmount == 1) {
            mCurrentImage.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        } else {
            mCurrentImage.setImageTintList((d instanceof VectorDrawable) ? ColorStateList.valueOf(getTintColor()) : null);
        }
    }

    public void onDensityOrFontScaleChanged() {
        mLeftText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        mRightText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        mCurrentImage.getLayoutParams().height =
                getResources().getDimensionPixelSize(R.dimen.current_weather_image_size);
        mCurrentImage.getLayoutParams().width =
                getResources().getDimensionPixelSize(R.dimen.current_weather_image_size);
    }

    public void setViewBackground(Drawable drawRes) {
        setViewBackground(drawRes, 255);
    }

    public void setViewBackground(Drawable drawRes, int bgAlpha) {
        mLayout.setBackground(drawRes);
        mLayout.getBackground().setAlpha(bgAlpha);
    }

    public void setViewBackgroundResource(int drawRes) {
        mLayout.setBackgroundResource(drawRes);
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_WEATHER_TEMP_COLOR),
                    false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_WEATHER_CITY_COLOR),
                    false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_WEATHER_ICON_COLOR),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            queryAndUpdateWeather();
        }
    }
}
