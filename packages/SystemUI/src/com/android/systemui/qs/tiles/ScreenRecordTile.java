/*
 * Copyright (C) 2018 Havoc-OS
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
import android.service.quicksettings.Tile;

import com.android.systemui.R;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.ScreenRecordHelper;

import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

public class ScreenRecordTile extends QSTileImpl<BooleanState> {

    private boolean mListening;
    private ScreenRecordHelper mScreenRecordHelper;

    @Inject
    public ScreenRecordTile(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.global_action_screenrecord);
    }

    @Override
    protected void handleClick() {
        mScreenRecordHelper = new ScreenRecordHelper(mContext);
        mScreenRecordHelper.launchRecordPrompt();
        mHost.collapsePanels();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
       // do nothing
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(com.android.internal.R.drawable.ic_lock_screenrecord);
        state.label = mContext.getString(R.string.global_action_screenrecord);
        state.state = Tile.STATE_INACTIVE;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
