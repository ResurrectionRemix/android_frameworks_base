/*
* Copyright (C) 2017 The OmniROM Project
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
package com.android.keyguard;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public class OmniJawsClient {
    private static final String TAG = "WeatherService:OmniJawsClient";
    private static final boolean DEBUG = false;
    public static final String SERVICE_PACKAGE = "org.omnirom.omnijaws";
    public static final Uri WEATHER_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/weather");
    public static final Uri SETTINGS_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/settings");

    private static final String ICON_PACKAGE_DEFAULT = "org.omnirom.omnijaws";
    private static final String ICON_PREFIX_DEFAULT = "weather";

    public static final String[] WEATHER_PROJECTION = new String[]{
            "city",
            "wind_speed",
            "wind_direction",
            "condition_code",
            "temperature",
            "humidity",
            "condition",
            "forecast_low",
            "forecast_high",
            "forecast_condition",
            "forecast_condition_code",
            "time_stamp",
            "forecast_date"
    };

    final String[] SETTINGS_PROJECTION = new String[] {
            "enabled",
            "units"
    };

    private static final DecimalFormat sNoDigitsFormat = new DecimalFormat("0");

    public static class WeatherInfo {
        public String city;
        public String windSpeed;
        public String windDirection;
        public int conditionCode;
        public String temp;
        public String humidity;
        public String condition;
        public Long timeStamp;
        public List<DayForecast> forecasts;
        public String tempUnits;
        public String windUnits;

        public String toString() {
            return city + ":" + new Date(timeStamp) + ": " + windSpeed + ":" + windDirection + ":" +conditionCode + ":" + temp + ":" + humidity + ":" + condition + ":" + tempUnits + ":" + windUnits + ": " + forecasts;
        }

        public String getLastUpdateTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return sdf.format(new Date(timeStamp));
        }
    }

    public static class DayForecast {
        public String low;
        public String high;
        public int conditionCode;
        public String condition;
        public String date;

        public String toString() {
            return "[" + low + ":" + high + ":" +conditionCode + ":" + condition + ":" + date + "]";
        }
    }

    public static interface OmniJawsObserver {
        public void weatherUpdated();
    }

    private class WeatherUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            for (OmniJawsObserver observer : mObserver) {
                observer.weatherUpdated();
            }
        }
    }

    private class OmniJawsSettingsObserver extends ContentObserver {
        OmniJawsSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.OMNIJAWS_WEATHER_ICON_PACK),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            updateSettings();
        }

        public void unregister() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    private Context mContext;
    private WeatherInfo mCachedInfo;
    private Resources mRes;
    private String mPackageName;
    private String mIconPrefix;
    private String mSettingIconPackage;
    private boolean mMetric;
    private List<OmniJawsObserver> mObserver;
    private WeatherUpdateReceiver mReceiver;
    private OmniJawsSettingsObserver mSettingsObserver;
    private Handler mHandler = new Handler();

    public OmniJawsClient(Context context) {
        mContext = context;
        mObserver = new ArrayList<OmniJawsObserver>();
        mSettingsObserver = new OmniJawsSettingsObserver(mHandler);
        mSettingsObserver.observe();
    }

    public void cleanupObserver() {
        mSettingsObserver.unregister();
        if (mReceiver != null) {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (Exception e) {
            }
            mReceiver = null;
        }
    }

    public void updateWeather(boolean force) {
        if (isOmniJawsServiceInstalled()) {
            Intent updateIntent = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".WeatherService");
            updateIntent.setAction(SERVICE_PACKAGE + ".ACTION_UPDATE");
            updateIntent.putExtra("force", force);
            mContext.startService(updateIntent);
        }
    }

    public WeatherInfo getWeatherInfo() {
        return mCachedInfo;
    }

    private static String getFormattedValue(float value) {
        if (Float.isNaN(value)) {
            return "-";
        }
        String formatted = sNoDigitsFormat.format(value);
        if (formatted.equals("-0")) {
            formatted = "0";
        }
        return formatted;
    }

    public void queryWeather() {
        if (!isOmniJawsEnabled()) {
            Log.w(TAG, "queryWeather while disabled");
            mCachedInfo = null;
            return;
        }
        Cursor c = mContext.getContentResolver().query(WEATHER_URI, WEATHER_PROJECTION,
                null, null, null);
        mCachedInfo = null;
        if (c != null) {
            try {
                int count = c.getCount();
                if (count > 0) {
                    mCachedInfo = new WeatherInfo();
                    List<DayForecast> forecastList = new ArrayList<DayForecast>();
                    int i = 0;
                    for (i = 0; i < count; i++) {
                        c.moveToPosition(i);
                        if (i == 0) {
                            mCachedInfo.city = c.getString(0);
                            mCachedInfo.windSpeed = getFormattedValue(c.getFloat(1));
                            mCachedInfo.windDirection = String.valueOf(c.getInt(2)) + "\u00b0";
                            mCachedInfo.conditionCode = c.getInt(3);
                            mCachedInfo.temp = getFormattedValue(c.getFloat(4));
                            mCachedInfo.humidity = c.getString(5);
                            mCachedInfo.condition = c.getString(6);
                            mCachedInfo.timeStamp = Long.valueOf(c.getString(11));
                        } else {
                            DayForecast day = new DayForecast();
                            day.low = getFormattedValue(c.getFloat(7));
                            day.high = getFormattedValue(c.getFloat(8));
                            day.condition = c.getString(9);
                            day.conditionCode = c.getInt(10);
                            day.date = c.getString(12);
                            forecastList.add(day);
                        }
                    }
                    mCachedInfo.forecasts = forecastList;
                }
            } finally {
                c.close();
            }
        }
        updateUnits();
        if (DEBUG) Log.d(TAG, "queryWeather " + mCachedInfo);
    }

    private void loadDefaultIconsPackage() {
        mPackageName = ICON_PACKAGE_DEFAULT;
        mIconPrefix = ICON_PREFIX_DEFAULT;
        mSettingIconPackage = mPackageName + "." + mIconPrefix;
        if (DEBUG) Log.d(TAG, "Load default icon pack " + mSettingIconPackage + " " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = mContext.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "No default package found");
        }
    }

    private void loadCustomIconPackage() {
        int idx = mSettingIconPackage.lastIndexOf(".");
        mPackageName = mSettingIconPackage.substring(0, idx);
        mIconPrefix = mSettingIconPackage.substring(idx + 1);
        if (DEBUG) Log.d(TAG, "Load custom icon pack " + mSettingIconPackage + " " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = mContext.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "Icon pack loading failed - loading default");
            loadDefaultIconsPackage();
        }
    }

    public Drawable getWeatherConditionImage(int conditionCode) {
        if (!isOmniJawsEnabled()) {
            Log.w(TAG, "Requesting condition image while disabled");
            return null;
        }
        if (!isAvailableApp(mPackageName)) {
            Log.w(TAG, "Icon pack no longer available - loading default " + mPackageName);
            loadDefaultIconsPackage();
        }
        if (mRes == null) {
            Log.w(TAG, "Requesting condition image while disabled");
            return null;
        }
        try {
            int resId = mRes.getIdentifier(mIconPrefix + "_" + conditionCode, "drawable", mPackageName);
            return mRes.getDrawable(resId);
        } catch(Exception e) {
            Log.w(TAG, "Failed to get condition image for " + conditionCode);
            return null;
        }
    }

    public boolean isOmniJawsServiceInstalled() {
        return isAvailableApp(SERVICE_PACKAGE);
    }

    public boolean isOmniJawsEnabled() {
        if (!isOmniJawsServiceInstalled()) {
            return false;
        }
        final Cursor c = mContext.getContentResolver().query(SETTINGS_URI, SETTINGS_PROJECTION,
                null, null, null);
        if (c != null) {
            int count = c.getCount();
            if (count == 1) {
                c.moveToPosition(0);
                boolean enabled = c.getInt(0) == 1;
                return enabled;
            }
        }
        return true;
    }

    public void setOmniJawsEnabled(boolean value) {
        if (isOmniJawsServiceInstalled()) {
            Intent updateIntent = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".WeatherService");
            updateIntent.setAction(SERVICE_PACKAGE + ".ACTION_ENABLE");
            updateIntent.putExtra("enable", value);
            mContext.startService(updateIntent);
        }
    }

    private void updateUnits() {
        if (!isOmniJawsServiceInstalled()) {
            return;
        }
        final Cursor c = mContext.getContentResolver().query(SETTINGS_URI, SETTINGS_PROJECTION,
                null, null, null);
        if (c != null) {
            int count = c.getCount();
            if (count == 1) {
                c.moveToPosition(0);
                mMetric = c.getInt(1) == 0;
                if (mCachedInfo != null) {
                    mCachedInfo.tempUnits = getTemperatureUnit();
                    mCachedInfo.windUnits = getWindUnit();
                }
            }
        }
    }

    private String getTemperatureUnit() {
        return "\u00b0" + (mMetric ? "C" : "F");
    }

    private String getWindUnit() {
        return mMetric ? "km/h":"mph";
    }

    private void updateSettings() {
        if (isOmniJawsServiceInstalled()) {
            final String iconPack = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.OMNIJAWS_WEATHER_ICON_PACK, UserHandle.USER_CURRENT);
            if (iconPack == null) {
                loadDefaultIconsPackage();
            } else if (mSettingIconPackage == null || !iconPack.equals(mSettingIconPackage)) {
                mSettingIconPackage = iconPack;
                loadCustomIconPackage();
            }
        }
    }

    private boolean isAvailableApp(String packageName) {
        final PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public Drawable getDefaultWeatherConditionImage() {
        return mContext.getResources().getDrawable(R.drawable.keyguard_weather_default_on);
    }

    public void addObserver(OmniJawsObserver observer) {
        if (mObserver.size() == 0) {
            if (mReceiver != null) {
                try {
                    mContext.unregisterReceiver(mReceiver);
                } catch (Exception e) {
                }
            }
            mReceiver = new WeatherUpdateReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction("org.omnirom.omnijaws.WEATHER_UPDATE");
            mContext.registerReceiver(mReceiver, filter);
        }
        mObserver.add(observer);
    }

    public void removeObserver(OmniJawsObserver observer) {
        mObserver.remove(observer);
        if (mObserver.size() == 0 && mReceiver != null) {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (Exception e) {
            }
            mReceiver = null;
        }
    }
}
