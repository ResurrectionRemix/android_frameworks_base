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

import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Secure;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import com.android.systemui.qs.SecureSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.R;

public class PulseTile extends QSTile<QSTile.BooleanState> {

    private final SecureSetting mSetting;

    public PulseTile(Host host) {
        super(host);

        mSetting = new SecureSetting(mContext, mHandler, Secure.FLING_PULSE_ENABLED) {
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
        setEnabled(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
	if (isPulseEnabled()) {
	Settings.Secure.putInt(mContext.getContentResolver(),
               Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, !isLavaLampEnabled() ? 1 : 0);
        }
        return new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$PulseSettingsActivity"));
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_pulse_label);
    }

    private void setEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_ENABLED,
                enabled ? 1 : 0);
    }

    private boolean isPulseEnabled() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private boolean isLavaLampEnabled() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean pulse = value != 0;
        state.value = pulse;
        state.label = mContext.getString(R.string.quick_settings_pulse_label);
        if (pulse) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_pulse);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_pulse_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_pulse_off);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_pulse_off);
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_pulse_changed_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_pulse_changed_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    public void setListening(boolean listening) {
        // Do nothing
    }
}
