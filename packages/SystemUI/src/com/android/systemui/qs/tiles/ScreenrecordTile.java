/*
 * Copyright (C) 2017 ABC rom
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
import android.view.WindowManager;

import com.android.internal.util.rr.RRFWBUtils;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

/** Quick settings tile: Screenrecord **/
public class ScreenrecordTile extends QSTileImpl<BooleanState> {

    private static final int SCREEN_RECORD_LOW_QUALITY = WindowManager.SCREEN_RECORD_LOW_QUALITY;
    private static final int SCREEN_RECORD_MID_QUALITY = WindowManager.SCREEN_RECORD_MID_QUALITY;
    private static final int SCREEN_RECORD_HIGH_QUALITY = WindowManager.SCREEN_RECORD_HIGH_QUALITY;

    private int mMode;

    public ScreenrecordTile(QSHost host) {
        super(host);
        mMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREENRECORD_QUALITY_MODE, SCREEN_RECORD_LOW_QUALITY,
                UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public void handleClick() {
        switchMode();
        refreshState();
    }


    private void switchMode() {
        if (mMode == SCREEN_RECORD_LOW_QUALITY) {
            mMode = SCREEN_RECORD_MID_QUALITY;
        } else if (mMode == SCREEN_RECORD_MID_QUALITY) {
            mMode = SCREEN_RECORD_HIGH_QUALITY;
        } else if (mMode == SCREEN_RECORD_HIGH_QUALITY) {
            mMode = SCREEN_RECORD_LOW_QUALITY;
        }
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREENRECORD_QUALITY_MODE, mMode,
                UserHandle.USER_CURRENT);
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        //finish collapsing the panel
        try {
             Thread.sleep(1000); //1s
        } catch (InterruptedException ie) {}
        RRFWBUtils.takeScreenrecord(mMode);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_screenrecord_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.contentDescription =  mContext.getString(
                R.string.quick_settings_screenrecord_label);
        if (mMode == SCREEN_RECORD_LOW_QUALITY) {
            state.label = mContext.getString(R.string.quick_settings_screenrecord_lq_label);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_screenrecord_lq);
        } else if (mMode == SCREEN_RECORD_MID_QUALITY) {
            state.label = mContext.getString(R.string.quick_settings_screenrecord_mq_label);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_screenrecord_mq);
        } else if (mMode == SCREEN_RECORD_HIGH_QUALITY) {
            state.label = mContext.getString(R.string.quick_settings_screenrecord_hq_label);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_screenrecord_hq);
        }
    }
}
