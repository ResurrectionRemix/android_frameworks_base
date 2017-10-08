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
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.omni.DetailedWeatherView;
import com.android.systemui.omni.OmniJawsClient;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import java.util.ArrayList;
import java.util.Arrays;

public class WeatherTile extends QSTileImpl<BooleanState> implements OmniJawsClient.OmniJawsObserver {
    private static final String TAG = "WeatherTile";
    private static final boolean DEBUG = false;
    private OmniJawsClient mWeatherClient;
    private Drawable mWeatherImage;
    private String mWeatherLabel;
    private DetailedWeatherView mDetailedView;
    private OmniJawsClient.WeatherInfo mWeatherData;
    private boolean mEnabled;
    private final ActivityStarter mActivityStarter;
    private WeatherDetailAdapter mDetailAdapter;

    public WeatherTile(QSHost host) {
        super(host);
        mWeatherClient = new OmniJawsClient(mContext);
        mEnabled = mWeatherClient.isOmniJawsEnabled();
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mDetailAdapter = (WeatherDetailAdapter) createDetailAdapter();
    }

    @Override
    public boolean isDualTarget() {
        return true;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.OMNI_QS_WEATHER;
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
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
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_service_error);
        refreshState();
        if (isShowingDetail()) {
            mDetailedView.weatherError(errorReason);
        }
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
        if (DEBUG) Log.d(TAG, "handleSecondaryClick");
        // Secondary clicks are also on quickbar tiles
        showDetail(true);
    }

    @Override
    protected void handleClick() {
        if (DEBUG) Log.d(TAG, "handleClick");
        if (!mWeatherClient.isOmniJawsServiceInstalled()) {
            Toast.makeText(mContext, R.string.omnijaws_package_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mState.value) {
            if (!mWeatherClient.isOmniJawsSetupDone()) {
                mActivityStarter.postStartActivityDismissingKeyguard(mWeatherClient.getSettingsIntent(), 0);
            } else {
                // service enablement is delayed so we keep the status
                // extra and hope service will follow correct :)
                mEnabled = true;
                mWeatherData = null;
                mWeatherClient.setOmniJawsEnabled(true);
            }
        } else {
            mEnabled = false;
            mWeatherData = null;
            mWeatherClient.setOmniJawsEnabled(false);
        }
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        if (DEBUG) Log.d(TAG, "getLongClickIntent");
        return mWeatherClient.getSettingsIntent();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (DEBUG) Log.d(TAG, "handleUpdateState " + mEnabled);
        state.dualTarget = true;
        state.value = mEnabled;
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        if (mEnabled) {
            if (mWeatherImage == null) {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_weather_default_on);
                state.label = mContext.getResources().getString(R.string.omnijaws_label_default);
            } else {
                state.icon = new DrawableIcon(mWeatherImage);
                state.label = mWeatherLabel;
            }
        } else {
            mWeatherImage = null;
            state.icon = ResourceIcon.get(R.drawable.ic_qs_weather_default_off);
            state.label = mContext.getResources().getString(R.string.omnijaws_label_default);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getResources().getString(R.string.omnijaws_label_default);
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather " + mEnabled);
            mWeatherImage = null;
            mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_label_default);
            if (mEnabled) {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    mWeatherImage = mWeatherClient.getWeatherConditionImage(mWeatherData.conditionCode);
                    if (mWeatherImage instanceof VectorDrawable) {
                        mWeatherImage = applyTint(mWeatherImage);
                    }
                    mWeatherLabel = mWeatherData.temp + mWeatherData.tempUnits;
                } else {
                    mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_service_unkown);
                }
            } else {
                mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_label_default);
            }
        } catch(Exception e) {
            mWeatherLabel = mContext.getResources().getString(R.string.omnijaws_label_default);
        }
        refreshState();
        if (isShowingDetail()) {
            mDetailedView.updateWeatherData(mWeatherData);
        }
    }

    private Drawable applyTint(Drawable icon) {
        TypedArray array =
                mContext.obtainStyledAttributes(new int[]{android.R.attr.colorControlNormal});
        icon = icon.mutate();
        icon.setTint(array.getColor(0, 0));
        array.recycle();
        return icon;
    }

    @Override
    protected DetailAdapter createDetailAdapter() {
        return new WeatherDetailAdapter();
    }

    private class WeatherDetailAdapter implements DetailAdapter {

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.OMNI_QS_WEATHER_DETAILS;
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
            mWeatherData = null;
            mEnabled = state;
            mWeatherClient.setOmniJawsEnabled(state);
            if (state) {
                mDetailedView.startProgress();
            } else {
                mDetailedView.stopProgress();
                mDetailedView.post(() -> {
                    mDetailedView.updateWeatherData(null);
                });
            }
            refreshState();
        }

        @Override
        public Intent getSettingsIntent() {
            return mWeatherClient.getSettingsIntent();
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            if (DEBUG) Log.d(TAG, "createDetailView ");
            mDetailedView = (DetailedWeatherView) LayoutInflater.from(context).inflate(
                    R.layout.detailed_weather_view, parent, false);
            mDetailedView.setWeatherClient(mWeatherClient);
            mDetailedView.post(() -> {
                try {
                    mDetailedView.updateWeatherData(mWeatherData);
                }   catch (Exception e){
                }
            });

            return mDetailedView;
        }
    }
}
