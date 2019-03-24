/*
 * Copyright (C) 2017-2018 Benzo Rom
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
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

public class CPUInfoTile extends QSTileImpl<BooleanState> {
    private boolean mListening;
    private CPUInfoObserver mObserver;

    public CPUInfoTile(QSHost host) {
        super(host);
        mObserver = new CPUInfoObserver(mHandler);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public void handleLongClick() {
    }

    protected void toggleState() {
        Intent service = (new Intent())
                .setClassName("com.android.systemui",
                "com.android.systemui.CPUInfoService");
        if (CPUInfoEnabled()) {
            Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.SHOW_CPU_OVERLAY, 0);
            mContext.stopService(service);
        } else {
            Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.SHOW_CPU_OVERLAY, 1);
            mContext.startService(service);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_cpuinfo_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(R.string.quick_settings_cpuinfo_label);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_cpuinfo);
	if (CPUInfoEnabled()) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_cpuinfo_on);
            state.state = Tile.STATE_ACTIVE;
	} else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_cpuinfo_off);
            state.state = Tile.STATE_INACTIVE;
	}
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_cpuinfo_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_cpuinfo_changed_off);
        }
    }

    private boolean CPUInfoEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, 0) == 1;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    private class CPUInfoObserver extends ContentObserver {
        public CPUInfoObserver(Handler handler) {
            super(handler);
        }
    }
}

