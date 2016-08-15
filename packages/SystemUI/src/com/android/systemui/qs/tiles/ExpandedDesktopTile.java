/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2012-2015 The CyanogenMod Project
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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerPolicyControl;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

/** Quick settings tile: Expanded desktop **/
public class ExpandedDesktopTile extends QSTileImpl<BooleanState> {

    private static final int STATE_ENABLE_FOR_ALL = 1;
    private static final int STATE_ENABLE_FOR_STATUSBAR = 2;
    private static final int STATE_ENABLE_FOR_NAVBAR = 3;
    private static final int STATE_USER_CONFIGURABLE = 4;

    private int mExpandedDesktopState;
    private ExpandedDesktopObserver mObserver;
    private boolean mListening;
    private boolean mHasNavigationBar;

    public ExpandedDesktopTile(QSHost host) {
        super(host);
        mExpandedDesktopState = getExpandedDesktopState(mContext.getContentResolver());
        mObserver = new ExpandedDesktopObserver(mHandler);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        toggleState();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$ExpandedDesktopSettingsActivity"));
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_expanded_desktop_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mExpandedDesktopState == 1) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_expanded_desktop);
            state.label = mContext.getString(R.string.quick_settings_expanded_desktop);
        } else if (mExpandedDesktopState == 2) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_expanded_statusbar_off);
            state.label = mContext.getString(R.string.quick_settings_expanded_statusbar_off);
        } else if (mExpandedDesktopState == 3) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_expanded_navigation_off);
            state.label = mContext.getString(R.string.quick_settings_expanded_navigation_off);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_expanded_desktop_off);
            state.label = mContext.getString(R.string.quick_settings_expanded_desktop_off);
        }
    }

    protected void toggleState() {
        try {
            mHasNavigationBar = WindowManagerGlobal.getWindowManagerService().hasNavigationBar();
        } catch (RemoteException e) {
            // Do nothing
        }
        int state = mExpandedDesktopState;
        switch (state) {
            case STATE_ENABLE_FOR_ALL:
                if (mHasNavigationBar) {
                  enableForStatusbar();
                } else {
                  userConfigurableSettings();
                }
                break;
            case STATE_ENABLE_FOR_STATUSBAR:
                enableForNavbar();
                break;
            case STATE_ENABLE_FOR_NAVBAR:
                userConfigurableSettings();
                break;
            case STATE_USER_CONFIGURABLE:
                enableForAll();
                break;
        }
    }

    private void writeValue(String value) {
        Settings.Global.putString(mContext.getContentResolver(),
             Settings.Global.POLICY_CONTROL, value);
    }

    private void enableForAll() {
        mExpandedDesktopState = STATE_ENABLE_FOR_ALL;
        writeValue("immersive.full=*");
    }

    private void enableForStatusbar() {
        mExpandedDesktopState = STATE_ENABLE_FOR_STATUSBAR;
        writeValue("immersive.status=*");
    }

    private void enableForNavbar() {
        mExpandedDesktopState = STATE_ENABLE_FOR_NAVBAR;
        writeValue("immersive.navigation=*");
    }

    private void userConfigurableSettings() {
        mExpandedDesktopState = STATE_USER_CONFIGURABLE;
        writeValue("");
        WindowManagerPolicyControl.reloadFromSetting(mContext);
    }

    private int getExpandedDesktopState(ContentResolver cr) {
        String value = Settings.Global.getString(cr, Settings.Global.POLICY_CONTROL);
        if ("immersive.full=*".equals(value)) {
            return STATE_ENABLE_FOR_ALL;
        }
        if ("immersive.status=*".equals(value)) {
            return STATE_ENABLE_FOR_STATUSBAR;
        }
        if ("immersive.navigation=*".equals(value)) {
            return STATE_ENABLE_FOR_NAVBAR;
        }
        return STATE_USER_CONFIGURABLE;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    private class ExpandedDesktopObserver extends ContentObserver {
        public ExpandedDesktopObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }

        public void startObserving() {
            mExpandedDesktopState = getExpandedDesktopState(mContext.getContentResolver());
            mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.POLICY_CONTROL),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }
}
