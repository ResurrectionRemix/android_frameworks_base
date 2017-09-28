/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2017 AICP
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.LocaleList;
import android.provider.Settings;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.internal.app.LocalePicker;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.Locale;

/** Quick settings tile: Locale **/
public class LocaleTile extends QSTileImpl<State> {

    private boolean mListening;

    private LocaleList mLocaleList;

    // If not null: update pending
    private Locale currentLocaleBackup;

    // Allow multiple clicks to find the desired locale without immediately applying
    private static final int TOGGLE_DELAY = 800;

    public LocaleTile(QSHost host) {
        super(host);
        updateLocaleList();
    }

    @Override
    public State newTileState() {
        return new QSTile.State();
    }

    @Override
    protected void handleClick() {
        if (checkToggleDisabled()) return;
        toggleLocale();
    }

    @Override
    protected void handleSecondaryClick() {
        if (checkToggleDisabled()) return;
        toggleLocale();
    }

    private void toggleLocale() {
        if (currentLocaleBackup == null) {
            currentLocaleBackup = mLocaleList.get(0);
        }
        Locale[] newLocales = new Locale[mLocaleList.size()];
        for (int i = 0; i < newLocales.length; i++) {
            newLocales[i] = mLocaleList.get((i+1)%newLocales.length);
        }
        mLocaleList = new LocaleList(newLocales);
        mHandler.removeCallbacks(applyLocale);
        mHandler.postDelayed(applyLocale, TOGGLE_DELAY);
        refreshState();
    }

    private Runnable applyLocale = new Runnable() {
        @Override
        public void run() {
            if (!mLocaleList.get(0).equals(currentLocaleBackup)) {
                mHost.collapsePanels();
                LocalePicker.updateLocales(mLocaleList);
            }
            currentLocaleBackup = null;
        }
    };

    @Override
    public Intent getLongClickIntent() {
        return new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.LanguageSettings"));
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_locale_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.AICP_METRICS;
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.icon = ResourceIcon.get(
                currentLocaleBackup == null || currentLocaleBackup.equals(mLocaleList.get(0)) ?
                        R.drawable.ic_qs_locale :
                        R.drawable.ic_qs_locale_pending);
        state.label = mLocaleList.get(0).getDisplayLanguage();
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
                updateLocaleList();
                refreshState();
            }
        }
    };

    private void updateLocaleList() {
        if (currentLocaleBackup != null) return;
        mLocaleList = LocaleList.getAdjustedDefault();
    }

    private boolean checkToggleDisabled() {
        updateLocaleList();
        if (mLocaleList.size() <= 1) {
            handleLongClick();
            Toast.makeText(mContext,
                    mContext.getString(R.string.quick_settings_locale_more_locales_toast),
                    Toast.LENGTH_LONG).show();
            return true;
        } else {
            return false;
        }
    }
}
