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


public class PieTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;
    private PieObserver mObserver;

 private static final Intent PIE_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$PieControlSettingsActivity"));

    public PieTile(Host host) {
        super(host);
        mObserver = new PieObserver(mHandler);
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
	mHost.startActivityDismissingKeyguard(PIE_SETTINGS);
    }

    @Override
    public void handleLongClick() {
	mHost.startActivityDismissingKeyguard(PIE_SETTINGS);
    }

 protected void toggleState() {
         Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.PA_PIE_STATE, !isPieEnabled() ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        if (isPieEnabled()) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_pie_on);
            state.label = mContext.getString(R.string.quick_settings_pie);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_pie_off);
            state.label = mContext.getString(R.string.quick_settings_pie);
        }
    }

    private boolean isPieEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PA_PIE_STATE, 0,
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

    private class PieObserver extends ContentObserver {
        public PieObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.PA_PIE_STATE),
                    false, this, UserHandle.USER_ALL);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }
}
