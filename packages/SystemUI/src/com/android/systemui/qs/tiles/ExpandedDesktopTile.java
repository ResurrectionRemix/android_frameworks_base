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

import android.content.Context;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerPolicyControl;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.android.internal.logging.MetricsConstants;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;

import cyanogenmod.app.StatusBarPanelCustomTile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Quick settings tile: Expanded desktop **/
public class ExpandedDesktopTile extends QSTile<QSTile.BooleanState> {

    private static final int STATE_ENABLE_FOR_ALL = 0;
    private static final int STATE_USER_CONFIGURABLE = 1;

    public static final Integer[] EXPANDED_SETTINGS = new Integer[]{
            WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_FULL,
            WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_STATUS,
            WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_NAVIGATION
    };

    private int mExpandedDesktopState;
    private int mExpandedDesktopStyle;
    private final ExpandedDesktopDetailAdapter mDetailAdapter;
    private final List<Integer> mExpandedDesktopList = new ArrayList<>();
    private ExpandedDesktopObserver mObserver;
    private boolean mListening;

    public ExpandedDesktopTile(Host host) {
        super(host);
        mDetailAdapter = new ExpandedDesktopDetailAdapter();
        mObserver = new ExpandedDesktopObserver(mHandler);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        /* wait for ripple animation to end */
        try {
             Thread.sleep(750);
        } catch (InterruptedException ie) {
             // Do nothing
        }
        toggleState();
        refreshState();
    }

    @Override
    protected void handleLongClick() {
        showDetail(true);
    }

    @Override
    protected void handleSecondaryClick() {
        showDetail(true);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsConstants.DONT_TRACK_ME_BRO;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int currentState = mExpandedDesktopState;
        state.visible = true;
        switch (currentState) {
            case STATE_ENABLE_FOR_ALL:
                if (mExpandedDesktopStyle == WindowManagerPolicyControl.
                            ImmersiveDefaultStyles.IMMERSIVE_STATUS) {
                    state.icon = ResourceIcon.get(
                            R.drawable.ic_qs_expanded_desktop_hidestatusbar);
                } else if (mExpandedDesktopStyle == WindowManagerPolicyControl.
                            ImmersiveDefaultStyles.IMMERSIVE_NAVIGATION) {
                    state.icon = ResourceIcon.get(
                            R.drawable.ic_qs_expanded_desktop_hidenavbar);
                } else {
                    state.icon = ResourceIcon.get(
                            R.drawable.ic_qs_expanded_desktop);
                }
                state.label = mContext.getString(
                        R.string.quick_settings_expanded_desktop);
                break;
            case STATE_USER_CONFIGURABLE:
                state.icon = ResourceIcon.get(
                        R.drawable.ic_qs_expanded_desktop_off);
                state.label = mContext.getString(
                        R.string.quick_settings_expanded_desktop_off);
                break;
        }
    }

    private int getStateLabelRes(int currentState) {
        switch (currentState) {
            case WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_FULL:
                return R.string.quick_settings_expanded_hide_both;
            case WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_STATUS:
                return R.string.quick_settings_expanded_hide_status;
            case WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_NAVIGATION:
                return R.string.quick_settings_expanded_hide_navigation;
            default:
                return R.string.quick_settings_expanded_hide_both;
        }
    }

    private void enableForAll() {
        mExpandedDesktopState = STATE_ENABLE_FOR_ALL;
        writeValue("immersive.full=*");
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
        return STATE_USER_CONFIGURABLE;
    }

    private void writeValue(String value) {
        Settings.Global.putString(mContext.getContentResolver(),
             Settings.Global.POLICY_CONTROL, value);
    }

    protected void toggleState() {
        int state = mExpandedDesktopState;
        switch (state) {
            case STATE_ENABLE_FOR_ALL:
                userConfigurableSettings();
                break;
            case STATE_USER_CONFIGURABLE:
                enableForAll();
                break;
        }
    }

    private int getExpandedDesktopStyle() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.POLICY_CONTROL_STYLE,
                WindowManagerPolicyControl.ImmersiveDefaultStyles.IMMERSIVE_FULL);
    }

    @Override
    public void setListening(boolean listening) {
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
            mExpandedDesktopStyle = getExpandedDesktopStyle();
            mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.POLICY_CONTROL),
                    false, mObserver);
            mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.POLICY_CONTROL_STYLE),
                    false, mObserver);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }

    private class ExpandedDesktopAdapter extends ArrayAdapter<Integer> {
        public ExpandedDesktopAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_single_choice, mExpandedDesktopList);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            CheckedTextView label = (CheckedTextView) inflater.inflate(
                    android.R.layout.simple_list_item_single_choice, parent, false);
            label.setText(getStateLabelRes(getItem(position)));
            return label;
        }
    }

    private class ExpandedDesktopDetailAdapter implements DetailAdapter, AdapterView.OnItemClickListener {

        private ExpandedDesktopAdapter mAdapter;
        private QSDetailItemsList mDetails;

        @Override
        public int getTitle() {
            return R.string.quick_settings_expanded_desktop_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            boolean enabled = mExpandedDesktopState == STATE_ENABLE_FOR_ALL;
            rebuildExpandedDesktopList(enabled);
            return enabled;
        }

        @Override
        public Intent getSettingsIntent() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            return intent.setClassName("com.android.settings",
                    "com.android.settings.Settings$ExpandedDesktopSettingsActivity");
        }

        @Override
        public void setToggleState(boolean state) {
            toggleState();
            fireToggleStateChanged(state);
            rebuildExpandedDesktopList(state);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsConstants.DONT_TRACK_ME_BRO;
        }

        @Override
        public StatusBarPanelCustomTile getCustomTile() {
            return null;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mDetails = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            mDetails.setEmptyState(R.drawable.ic_qs_expanded_desktop_off,
                    R.string.accessibility_quick_settings_expanded_desktop_off);
            mAdapter = new ExpandedDesktopTile.ExpandedDesktopAdapter(context);
            mDetails.setAdapter(mAdapter);

            final ListView list = mDetails.getListView();
            list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            list.setOnItemClickListener(this);

            return mDetails;
        }

        private void rebuildExpandedDesktopList(boolean populate) {
            mExpandedDesktopList.clear();
            if (populate) {
                mExpandedDesktopList.addAll(Arrays.asList(EXPANDED_SETTINGS));
                mDetails.getListView().setItemChecked(mAdapter.getPosition(
                        getExpandedDesktopStyle()), true);
            }
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.POLICY_CONTROL_STYLE, position);
            // We need to visually show the change
            // TODO: This is hacky, but it works
            writeValue("");
            writeValue("immersive.full=*");
        }
    }
}
