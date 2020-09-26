/*
 * Copyright (C) 2019 The OmniROM Project
 * Copyright (C) 2020 crDroid Android Project
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
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.text.TextUtils;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import android.widget.Toast;
import javax.inject.Inject;

public class RefreshRateTile extends QSTileImpl<BooleanState> {
    private boolean mIsAvailable;
    private int mMode;
    private int mDefaultRate;
    private Toast toast;
    private RefreshRateObserver mObserver;
    private int peakRate;
    private static final int REFRESH_RATE_AUTO = 0;
    private static final int REFRESH_RATE_60 = 1;
    private static final int REFRESH_RATE_90 = 2;
    private static final int REFRESH_RATE_120 = 3;

    @Inject
    public RefreshRateTile(QSHost host) {
        super(host);
        mIsAvailable = mContext.getResources().getBoolean(
            com.android.internal.R.bool.config_hasVariableRefreshRate);
        mDefaultRate = mContext.getResources().getInteger(
               com.android.internal.R.integer.config_defaultVariableRefreshRateSetting);
        peakRate = mContext.getResources().getInteger(
              com.android.internal.R.integer.config_defaultPeakRefreshRate);
        mObserver = new RefreshRateObserver(mHandler);
        mObserver.observe();

    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
      if (peakRate > 90) {
          switchModeLarge();
      } else {
          switchMode();
      }
      refreshState();
      showToast(mContext.getResources().getString(R.
          string.refresh_change_triggered), Toast.LENGTH_LONG);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    private void showToast(String msg, int duration) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
        @Override
        public void run() {
            if (toast != null) toast.cancel();
            toast = Toast.makeText(mContext, msg, duration);
            toast.show();
            }
        });
    }

    @Override
    public void handleLongClick() {
      ToggleState();
      mHost.collapsePanels();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.tile_refresh_rate);
    }

    private void switchMode() {
         switch (mMode) {
            case REFRESH_RATE_AUTO:
                mMode = REFRESH_RATE_60;
                break;
            case REFRESH_RATE_60:
                mMode = REFRESH_RATE_90;
                break;
            case REFRESH_RATE_90:
                mMode = REFRESH_RATE_AUTO;
                break;
        }
    }

    private void switchModeLarge() {
         switch (mMode) {
            case REFRESH_RATE_AUTO:
                mMode = REFRESH_RATE_60;
                break;
            case REFRESH_RATE_60:
                mMode = REFRESH_RATE_90;
                break;
            case REFRESH_RATE_90:
                mMode = REFRESH_RATE_120;
                break;
            case REFRESH_RATE_120:
                mMode = REFRESH_RATE_AUTO;
                break;
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        switch (mMode) {
            case REFRESH_RATE_AUTO:
                state.contentDescription = mContext.getString(
                        R.string.auto_refresh_rate_summary);
                state.label = mContext.getString(R.string.auto_refresh_rate_summary);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_auto_refresh);
                break;
            case REFRESH_RATE_60:
                state.contentDescription = mContext.getString(
                        R.string.refresh_rate_60);
                state.label = mContext.getString(R.string.refresh_rate_60);
                state.icon = ResourceIcon.get(R.drawable.ic_refresh_tile_60);
                break;
            case REFRESH_RATE_90:
                state.contentDescription = mContext.getString(
                        R.string.refresh_rate_90);
                state.label = mContext.getString(R.string.refresh_rate_90);
                state.icon = ResourceIcon.get(R.drawable.ic_refresh_tile_90);
                break;
            case REFRESH_RATE_120:
                state.contentDescription = mContext.getString(
                        R.string.refresh_rate_120);
                state.label = mContext.getString(R.string.refresh_rate_120);
                state.icon = ResourceIcon.get(R.drawable.ic_refresh_tile_120);
                break;
        }
    }

    private void ToggleState() {
        switch (mMode) {
            case REFRESH_RATE_AUTO:
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.PEAK_REFRESH_RATE, peakRate);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MIN_REFRESH_RATE, 60);
                showToast(mContext.getResources().getString(R.
                  string.refresh_rate_auto_triggered), Toast.LENGTH_LONG);
                break;
            case REFRESH_RATE_60:
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.PEAK_REFRESH_RATE, 60);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MIN_REFRESH_RATE, 60);
                showToast(mContext.getResources().getString(R.
                  string.refresh_rate_60_triggered), Toast.LENGTH_LONG);
                break;
            case REFRESH_RATE_90:
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.PEAK_REFRESH_RATE, 90);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MIN_REFRESH_RATE, 90);
                showToast(mContext.getResources().getString(R.
                  string.refresh_rate_90_triggered), Toast.LENGTH_LONG);
                break;
            case REFRESH_RATE_120:
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.PEAK_REFRESH_RATE, 120);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MIN_REFRESH_RATE, 120);
                showToast(mContext.getResources().getString(R.
                  string.refresh_rate_120_triggered), Toast.LENGTH_LONG);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean isAvailable() {
        return mIsAvailable;
    }

    @Override
    public void handleSetListening(boolean listening) {
    }

    private class RefreshRateObserver extends ContentObserver {
        public RefreshRateObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.REFRESH_RATE_SETTING), false, this,
                    UserHandle.USER_ALL);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
             update();
        }

        public void update() {
          mMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.REFRESH_RATE_SETTING, mDefaultRate); 
          refreshState();
        }
    }
}
