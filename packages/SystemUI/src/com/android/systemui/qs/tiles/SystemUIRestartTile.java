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

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

import com.android.internal.util.aicp.Helpers;

/** Quick settings tile: Heads Up **/
public class SystemUIRestartTile extends QSTile<QSTile.BooleanState> {

    public SystemUIRestartTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        Helpers.restartSystemUI();
    }

    @Override
    protected void handleLongClick() {
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_systemui_restart_label);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_systemui_restart);
    }

    @Override
    public void setListening(boolean listening) {
    }

}
