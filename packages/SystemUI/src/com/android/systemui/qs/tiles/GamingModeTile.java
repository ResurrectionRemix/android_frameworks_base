/*
 * Copyright (C) 2018 FireHound
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
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.provider.Settings.System;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SystemSetting;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.phone.SystemUIDialog;

/** Quick settings tile: Gaming Mode tile **/
public class GamingModeTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_gaming_mode);
    private final SystemSetting mSetting;

    public GamingModeTile(QSHost host) {
        super(host);

        mSetting = new SystemSetting(mContext, mHandler, System.ENABLE_GAMING_MODE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        if (Prefs.getBoolean(mContext, Prefs.Key.QS_GAMING_MODE_DIALOG_SHOWN, false)) {
            enableGamingMode();
            return;
        }
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(R.string.gaming_mode_dialog_title);
        dialog.setMessage(R.string.gaming_mode_dialog_message);
        dialog.setPositiveButton(com.android.internal.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        enableGamingMode();
                        Prefs.putBoolean(mContext, Prefs.Key.QS_GAMING_MODE_DIALOG_SHOWN, true);
                    }
                });
        dialog.setShowForAllUsers(true);
        dialog.show();
    }

    public void enableGamingMode() {
        handleState(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    private void handleState(boolean enabled) {
        // Heads up
        boolean headsUpEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 1;
        if (enabled && headsUpEnabled) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 0);
        } else if (!enabled) {
            Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1);
        }
        // Hardware keys
        boolean isHwKeysOn = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HARDWARE_KEYS_DISABLE, 0) == 0;
        if (enabled && isHwKeysOn) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.HARDWARE_KEYS_DISABLE, 1);
        } else if (!enabled) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.HARDWARE_KEYS_DISABLE, 0);
        }
        // Show a toast
        if (enabled) {
            SysUIToast.makeText(mContext, mContext.getString(
                R.string.gaming_mode_tile_toast),
                Toast.LENGTH_SHORT).show();
        } else if (!enabled) {
            SysUIToast.makeText(mContext, mContext.getString(
                R.string.gaming_mode_tile_toast_disabled),
                Toast.LENGTH_SHORT).show();
        }

        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.ENABLE_GAMING_MODE,
                enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean enable = value != 0;
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        state.icon = mIcon;
        state.value = enable;
        state.slash.isSlashed = !state.value;
        state.label = mContext.getString(R.string.gaming_mode_tile_title);
        if (enable) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.gaming_mode_tile_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // no-op
    }
}
