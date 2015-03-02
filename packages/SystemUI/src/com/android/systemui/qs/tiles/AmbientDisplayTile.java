/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright 2014-2015 The Euphoria-OS Project
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
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Ambient display **/
public class AmbientDisplayTile extends QSTile<QSTile.BooleanState> {

    public AmbientDisplayTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
        qsCollapsePanel();
    }

    @Override
    protected void handleSecondaryClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$AmbientDisplaySettingsActivity");
        mHost.startSettingsActivity(intent);
    }

    @Override
    protected void handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$AmbientDisplaySettingsActivity");
        mHost.startSettingsActivity(intent);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.value = isAmbientDisplayEnabled();
        if (state.value) {
            state.iconId = R.drawable.ic_qs_doze;
            state.label = mContext.getString(R.string.quick_settings_doze);
        } else {
            state.iconId = R.drawable.ic_qs_doze_off;
	    state.label = mContext.getString(R.string.quick_settings_doze_off);
        }
    }

    protected void toggleState() {
        Settings.Secure.putInt(mContext.getContentResolver(),
            Settings.Secure.DOZE_ENABLED, isAmbientDisplayEnabled() ? 0 : 1);
    }

    private boolean isAmbientDisplayEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ENABLED, 1) == 1;
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void destroy() {
        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.DOZE_ENABLED),
                    false, mObserver);
        } else {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }

}
