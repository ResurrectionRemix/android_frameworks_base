/*
 * Copyright (C) 2016 ResurrectionRemix
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
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.content.ComponentName;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;


import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.android.internal.logging.MetricsLogger;

import cyanogenmod.providers.CMSettings;


public class HardwareKeysTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;
    private HardwareKeysObserver mObserver;
    private static final Intent BUTTON_Settings = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$ButtonSettingsActivity"));

    public HardwareKeysTile(Host host) {
        super(host);
        mObserver = new HardwareKeysObserver(mHandler);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
    }

   @Override
    public int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }

     @Override
    protected void handleSecondaryClick() {
	mHost.startActivityDismissingKeyguard(BUTTON_Settings);
    }

    @Override
    public void handleLongClick() {
	mHost.startActivityDismissingKeyguard(BUTTON_Settings);
    }

   protected void toggleState() {
	boolean isSingleValue = !mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_deviceHasVariableButtonBrightness);
        int defaultBrightness = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_buttonBrightnessSettingDefault);

	Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.ENABLE_HW_KEYS, !HwkeysEnabled() ? 1 : 0);

        if (HwkeysEnabled()) {
             CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.BUTTON_BRIGHTNESS , defaultBrightness);
	     CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.KEYBOARD_BRIGHTNESS , defaultBrightness);
        } else {
		 CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.BUTTON_BRIGHTNESS , 0);
         	 CMSettings.Secure.putInt(mContext.getContentResolver(),  CMSettings.Secure.KEYBOARD_BRIGHTNESS , 0);          
        }
    }


    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if(mContext.getResources().getBoolean(com.android.internal.R.bool.config_hwKeysPref)) {   	        
	state.visible = true;
	}
	if (HwkeysEnabled()) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_hwkeys_on);
        state.label = mContext.getString(R.string.quick_settings_hwkeys_on);
	} else {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_hwkeys_off);
	state.label = mContext.getString(R.string.quick_settings_hwkeys_off);
	    }
	}


	private boolean HwkeysEnabled()
	{
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ENABLE_HW_KEYS, 0,
                UserHandle.USER_CURRENT) == 1;
	}

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    private class HardwareKeysObserver extends ContentObserver {
        public HardwareKeysObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ENABLE_HW_KEYS),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }	
}
