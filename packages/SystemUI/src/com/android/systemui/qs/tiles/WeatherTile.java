/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2017 The OmniROM project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.Log;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.util.rr.PackageUtils;
import com.android.systemui.R;
import com.android.systemui.omni.DetailedWeatherView;
import com.android.systemui.omni.OmniJawsClient;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.DetailAdapter;

import java.util.ArrayList;
import java.util.Arrays;

public class WeatherTile extends QSTile<QSTile.BooleanState> implements OmniJawsClient.OmniJawsObserver {
    private static final String TAG = "WeatherTile";
    private static final boolean DEBUG = false;
    private OmniJawsClient mWeatherClient;
    private boolean mShowingDetail;
    private Drawable mWeatherImage;
    private String mWeatherLabel;
    private DetailedWeatherView mDetailedView;
    private OmniJawsClient.WeatherInfo mWeatherData;
    private boolean mEnabled;

    private static final String[] ALTERNATIVE_WEATHER_APPS = {
            "cz.martykan.forecastie",
    };

    public WeatherTile(Host host) {
        super(host);
        mWeatherClient = new OmniJawsClient(mContext);
        mEnabled = mWeatherClient.isOmniJawsEnabled();
        mWeatherImage = mContext.getResources().getDrawable(R.drawable.ic_qs_weather_default_off);
        mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_label_default);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return new WeatherDetailAdapter();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (DEBUG) Log.d(TAG, "setListening " + listening);
        mEnabled = mWeatherClient.isOmniJawsEnabled();

        if (listening) {
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
        } else {
            mWeatherClient.removeObserver(this);
        }
    }

    @Override
    public void weatherUpdated() {
        if (DEBUG) Log.d(TAG, "weatherUpdated");
        queryAndUpdateWeather();
    }

    @Override
    protected void handleDestroy() {
        // make sure we dont left one
        mWeatherClient.removeObserver(this);
        mWeatherClient.cleanupObserver();
        super.handleDestroy();
    }

    @Override
    protected void handleSecondaryClick() {
        // Secondary clicks are on quickbar tiles
        // TODO what should it to - open details or just disable/enable
        handleClick();
    }

    @Override
    protected void handleClick() {
        if (!mWeatherClient.isOmniJawsServiceInstalled()) {
            Toast.makeText(mContext, R.string.omnijaws_package_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        mShowingDetail = true;
        if (!mState.value) {
            // service enablement is delayed so we keep the status
            // extra and hope service will follow correct :)
            mEnabled = true;
            mWeatherClient.setOmniJawsEnabled(true);
            queryAndUpdateWeather();
        }
        showDetail(true);
    }

    @Override
    public Intent getLongClickIntent() {
        if (PackageUtils.isAvailableApp("com.google.android.googlequicksearchbox", mContext)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("dynact://velour/weather/ProxyActivity"));
            intent.setComponent(new ComponentName("com.google.android.googlequicksearchbox",
                    "com.google.android.apps.gsa.velour.DynamicActivityTrampoline"));
            return intent;
        } else {
            PackageManager pm = mContext.getPackageManager();
            for (String app: ALTERNATIVE_WEATHER_APPS) {
                Intent intent = pm.getLaunchIntentForPackage(app);
                if (intent != null) {
                    return intent;
                }
            }
            return mWeatherClient.getSettingsIntent();
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (DEBUG) Log.d(TAG, "handleUpdateState " + mEnabled);
        if (mEnabled) {
            state.label = mWeatherLabel;
            state.icon = new DrawableIcon(mWeatherImage);
            state.value = true;
        } else {
            state.label = mContext.getResources().getString(R.string.omnijaws_label_default);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_weather_default_off);
            state.value = false;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getResources().getString(R.string.omnijaws_label_default);
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather " + mEnabled);
            mWeatherImage = mWeatherClient.getDefaultWeatherConditionImage();
            if (mEnabled) {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    mWeatherImage = mWeatherClient.getWeatherConditionImage(mWeatherData.conditionCode);
                    mWeatherLabel = mWeatherData.temp + mWeatherData.tempUnits;
                } else {
                    mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_service_unkown);
                }
            } else {
                mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_label_default);
            }
        } catch(Exception e) {
            mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_service_error);
        }
        refreshState();
        if (mDetailedView != null) {
            mDetailedView.updateWeatherData(mWeatherData);
        }
    }

    private class WeatherDetailAdapter implements DetailAdapter {

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QUICK_SETTINGS;
        }

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.omnijaws_detail_header);
        }

        @Override
        public Boolean getToggleState() {
            return mEnabled;
        }

        @Override
        public void setToggleState(boolean state) {
            MetricsLogger.action(mContext, getMetricsCategory());
            if (!state) {
                mWeatherClient.setOmniJawsEnabled(false);
                mEnabled = false;
                refreshState();
                showDetail(false);
                mDetailedView = null;
            }
        }

        @Override
        public Intent getSettingsIntent() {
            return mWeatherClient.getSettingsIntent();
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mDetailedView = (DetailedWeatherView) LayoutInflater.from(context).inflate(
                    R.layout.detailed_weather_view, parent, false);
            mDetailedView.setWeatherClient(mWeatherClient);
            try {
                mDetailedView.updateWeatherData(mWeatherData);
            } catch (Exception e){
            }
            return mDetailedView;
        }
    }
}
