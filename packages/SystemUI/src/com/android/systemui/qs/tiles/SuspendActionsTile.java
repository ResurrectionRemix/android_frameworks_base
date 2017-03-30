/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.provider.Settings;

import com.android.systemui.qs.QSTile;
import com.android.systemui.R;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;

/** Quick settings tile: Suspend Actions **/
public class SuspendActionsTile extends QSTile<QSTile.BooleanState> {

    public SuspendActionsTile(Host host) {
        super(host);
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_suspend_actions_label);
    }


    @Override
    protected void handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$ScreenStateServiceActivity");
        mHost.startActivityDismissingKeyguard(intent);
    }

    private void setEnabled(boolean enabled) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.START_SCREEN_STATE_SERVICE,
                enabled ? 1 : 0);
    }
    
   private boolean isSuspendActionsEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.START_SCREEN_STATE_SERVICE, 0) == 1;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = isSuspendActionsEnabled();
        state.visible = true;
        if (state.value) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_suspend_actions_on);
            state.label =  mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_suspend_actions_off);
            state.label =  mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_off);
        }
    }

    @Override
    public void setListening(boolean listening) {
        // Do nothing
    }
}
