/*
 * Copyright (C) 2019 crDroid Android Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import javax.inject.Inject;

public class GamingModeTile extends QSTileImpl<BooleanState> {

    private final GlobalSetting mGamingModeActivated;
    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_gaming_mode);
    private static final Intent GAMING_MODE_SETTINGS = new Intent("android.settings.GAMING_MODE_SETTINGS");

    @Inject
    public GamingModeTile(QSHost host) {
        super(host);
        mGamingModeActivated = new GlobalSetting(mContext, mHandler, Settings.System.GAMING_MODE_ACTIVE) {
            @Override
            protected void handleValueChanged(int value) {
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
        boolean gamingModeEnabled = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.GAMING_MODE_ENABLED, 0) == 1;
        mHost.collapsePanels();
        if (gamingModeEnabled) {
            mGamingModeActivated.setValue(mState.value ? 0 : 1);
        } else {
            SysUIToast.makeText(mContext, mContext.getString(
                    R.string.gaming_mode_not_enabled),
                    Toast.LENGTH_LONG).show();
        }
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return GAMING_MODE_SETTINGS;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_gaming_mode_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mGamingModeActivated.getValue();
        final boolean enable = value == 1;
        state.value = enable;
        state.label = mContext.getString(R.string.quick_settings_gaming_mode_label);
        state.icon = mIcon;
        if (enable) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_enabled);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_disabled);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_enabled);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_disabled);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
