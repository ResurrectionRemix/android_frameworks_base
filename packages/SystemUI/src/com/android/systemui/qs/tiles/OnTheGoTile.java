/*
 * Copyright (C) 2018 Benzo Rom
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
import android.service.quicksettings.Tile;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;


import com.android.internal.util.rr.OnTheGoActions;
import com.android.internal.util.rr.OnTheGoUtils;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

/** Quick settings tile: OnTheGo Mode **/
public class OnTheGoTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_onthego);

    public OnTheGoTile(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
    }

    protected void toggleState() {
        Intent service = (new Intent())
                .setClassName("com.android.systemui",
                "com.android.systemui.rr.onthego.OnTheGoService");
        OnTheGoActions.processAction(mContext,
                OnTheGoActions.ACTION_ONTHEGO_TOGGLE);
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_onthego_label);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.contentDescription =  mContext.getString(
                R.string.quick_settings_onthego_label);
        state.label = mContext.getString(R.string.quick_settings_onthego_label);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_onthego);
        state.state = OnTheGoUtils.isServiceRunning(mContext,
                "com.android.systemui.crdroid.onthego.OnTheGoService")  ?
                Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    protected String composeChangeAnnouncement() {
        return mContext.getString(R.string.quick_settings_onthego_label);
    }
}
