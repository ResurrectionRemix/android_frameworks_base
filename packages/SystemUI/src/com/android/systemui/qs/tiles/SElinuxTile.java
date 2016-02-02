/*
 * Copyright (C) 2015 The Dirty Unicorns Project
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
import android.content.ComponentName;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
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
import com.android.internal.util.rr.CMDProcessor;

public class SElinuxTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;

 private static final Intent SELINUX_STATUS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$DeviceInfoSettingsActivity"));

    public SElinuxTile(Host host) {
        super(host);

    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }


    @Override
    public int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }


    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
    }

     @Override
    protected void handleSecondaryClick() {
	mHost.startActivityDismissingKeyguard(SELINUX_STATUS);
    }

    @Override
    public void handleLongClick() {
	mHost.startActivityDismissingKeyguard(SELINUX_STATUS);
    }

 protected void toggleState() {
	if (CMDProcessor.runShellCommand("getenforce").getStdout().contains("Enforcing")) {
   	  CMDProcessor.runSuCommand("setenforce 0");
	} else {
	 CMDProcessor.runSuCommand("setenforce 1");
	}
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        if (CMDProcessor.runSuCommand("getenforce").getStdout().contains("Enforcing")) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_selinux_enforcing);
            state.label = mContext.getString(R.string.quick_settings_selinux_enforcing_title);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_selinux_permissive);
            state.label = mContext.getString(R.string.quick_settings_selinux_permissive_title);
        }
    }

    @Override
    public void setListening(boolean listening) {
    }

 
}
