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

import android.app.IUiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings.Secure;
import android.widget.Toast;

import com.android.internal.util.slim.ActionConstants;
import com.android.internal.util.slim.Action;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.SecureSetting;
import com.android.systemui.qs.UsageTracker;

/** Quick settings tile: toggle TRDS state **/
public class TrdsTile extends QSTile<QSTile.BooleanState> {

    private final SecureSetting mSetting;

    private boolean mListening;

    public TrdsTile(Host host) {
        super(host);

        mSetting = new SecureSetting(mContext, mHandler,
                Secure.UI_THEME_MODE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                if (mListening) {
                    handleRefreshState(value);
                }
            }
        };
        mSetting.setListening(true);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mSetting.setListening(false);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        mListening = listening;
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        mSetting.setUserId(newUserId);
        handleRefreshState(mSetting.getValue());
    }

    @Override
    protected void handleClick() {
        // toggle theme mode
        Action.processAction(mContext, ActionConstants.ACTION_THEME_SWITCH, false);
        boolean newState = !mState.value;
        refreshState(newState);
    }

    @Override
    protected void handleLongClick() {
        // reset theme to normal
        final IUiModeManager uiModeManagerService = IUiModeManager.Stub.asInterface(
                ServiceManager.getService(Context.UI_MODE_SERVICE));
        try {
            uiModeManagerService.setUiThemeMode(
                    Configuration.UI_THEME_MODE_NORMAL);
        } catch (RemoteException e) {
            return;
        }
        Toast.makeText(mContext, "theme mode reset", Toast.LENGTH_SHORT).show();
        refreshState(false);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean enabled = (value == Configuration.UI_THEME_MODE_HOLO_DARK);
        state.visible = true;
        state.value = enabled;
        state.label = mContext.getString(R.string.quick_settings_trds_label);
        state.icon = ResourceIcon.get(state.value ? R.drawable.ic_qs_trds_on : R.drawable.ic_qs_trds_off);
    }

}
