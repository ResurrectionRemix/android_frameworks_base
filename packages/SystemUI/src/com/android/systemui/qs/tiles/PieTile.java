/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

import android.content.Intent;
import android.content.ComponentName;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.service.quicksettings.Tile;
import android.text.TextUtils;

import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.SecureSetting;
import com.android.systemui.R;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

public class PieTile extends QSTileImpl<BooleanState> {

    private static final Intent PIE_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$PieControlSettingsActivity"));

    private final SecureSetting mSetting;

    public PieTile(QSHost host) {
        super(host);

        mSetting = new SecureSetting(mContext, mHandler, Secure.PIE_STATE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        setEnabled(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return PIE_SETTINGS;
    }

    private void setEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.PIE_STATE,
                enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean enable = value != 0;
        state.value = enable;
        state.label = mContext.getString(R.string.quick_settings_pie);
        if (enable) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_pie_on);
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_pie);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_pie_off);
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_pie);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_pie);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.quick_settings_pie);
        } else {
            return mContext.getString(
                    R.string.quick_settings_pie);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
