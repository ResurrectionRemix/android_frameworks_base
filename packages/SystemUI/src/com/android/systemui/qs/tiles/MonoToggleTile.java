/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

/** Quick settings tile: MonoToggleTile **/
public class MonoToggleTile extends QSTileImpl<BooleanState> {

    @Inject
    public MonoToggleTile(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_monotoggle_tile);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public void handleClick() {
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.MASTER_MONO, isMonoEnabled() ? 0 : 1,
                UserHandle.USER_CURRENT);
        refreshState();
    }

    private boolean isMonoEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.MASTER_MONO, 0, UserHandle.USER_CURRENT) == 1;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (isMonoEnabled()) {
            state.label = mContext.getString(R.string.quick_settings_monotoggle_tile_mono);
            state.icon = ResourceIcon.get(R.drawable.ic_mono_toggle_on);
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_monotoggle_tile_mono);
        } else {
            state.label = mContext.getString(R.string.quick_settings_monotoggle_tile_stereo);
            state.icon = ResourceIcon.get(R.drawable.ic_mono_toggle_off);
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_monotoggle_tile_stereo);
        }
    }
}
