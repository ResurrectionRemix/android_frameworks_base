/*
 * Copyright (C) 2017 The Nitrogen Project
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

import cyanogenmod.providers.CMSettings;

/** Quick settings tile: HWKeys Actions **/
public class HWKeysTile extends QSTile<QSTile.BooleanState> {

    // Masks for checking presence of hardware keys.
    // Must match values in frameworks/base/core/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_APP_SWITCH = 0x10;

    public HWKeysTile(Host host) {
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
        return mContext.getString(R.string.quick_settings_hwkeys_label);
    }


    @Override
    protected void handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$HwKeysSettingsActivity");
        mHost.startActivityDismissingKeyguard(intent);
    }

    private void setEnabled(boolean enabled) {
        int defaultBrightness = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_buttonBrightnessSettingDefault);
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HARDWARE_KEYS_DISABLE,
                enabled ? 0 : 1);
        if (isHWKeysEnabled()) {
             CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.BUTTON_BRIGHTNESS , defaultBrightness);
             CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.KEYBOARD_BRIGHTNESS , defaultBrightness);
        } else {
             CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.BUTTON_BRIGHTNESS , 0);
             CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.KEYBOARD_BRIGHTNESS , 0);          
        }
    }

   private boolean isHWKeysEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HARDWARE_KEYS_DISABLE, 0) == 0;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = isHWKeysEnabled();
        if (state.value) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_hwkeys_on);
            state.label =  mContext.getString(
                    R.string.accessibility_quick_settings_hwkeys_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_hwkeys_off);
            state.label =  mContext.getString(
                    R.string.accessibility_quick_settings_hwkeys_off);
        }
    }

    @Override
    public boolean isAvailable() {
        final int deviceKeys = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);

        // read bits for present hardware keys
        final boolean hasHomeKey = (deviceKeys & KEY_MASK_HOME) != 0;
        final boolean hasBackKey = (deviceKeys & KEY_MASK_BACK) != 0;
        final boolean hasMenuKey = (deviceKeys & KEY_MASK_MENU) != 0;
        final boolean hasAppSwitchKey = (deviceKeys & KEY_MASK_APP_SWITCH) != 0;

        return (hasHomeKey || hasBackKey || hasMenuKey || hasAppSwitchKey);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_hwkeys_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_hwkeys_off);
        }
    }

    @Override
    public void setListening(boolean listening) {
        // Do nothing
    }
}
