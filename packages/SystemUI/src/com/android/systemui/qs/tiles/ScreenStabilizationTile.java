/*
 * Copyright (C) 2020 The AOSPA-Extended Project
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
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

/** Quick settings tile: Enable/Disable ScreenStabilization **/
public class ScreenStabilizationTile extends QSTileImpl<BooleanState> {

    private boolean mListening;
    private ContentResolver mResolver;

    private final ScreenStabilizationDetailAdapter mDetailAdapter;

    private final SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged (SeekBar seekBar, int progress, boolean fromUser) {

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            final int id = seekBar.getId();
            if (id == R.id.stabilization_velocity_friction_seekbar) {
                updateValuesFloat(seekBar.getProgress(), false);
            } else if (id == R.id.stabilization_velocity_amplitude_seekbar) {
                updateValuesInt(seekBar.getProgress());
            } else if (id == R.id.stabilization_position_friction_seekbar) {
                updateValuesFloat(seekBar.getProgress(), true);
            }
        }
    };

    @Inject
    public ScreenStabilizationTile(QSHost host) {
        super(host);
        mResolver = mContext.getContentResolver();
        mDetailAdapter = (ScreenStabilizationDetailAdapter) createDetailAdapter();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected DetailAdapter createDetailAdapter() {
        return new ScreenStabilizationDetailAdapter();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleLongClick() {
        showDetail(true);
    }

    @Override
    protected void handleClick() {
        Settings.System.putInt(mResolver, Settings.System.STABILIZATION_ENABLE, (Settings.System.getInt(mResolver, Settings.System.STABILIZATION_ENABLE, 0) == 1) ? 0:1);
        refreshState();
    }

    @Override
    protected void handleSecondaryClick() {
        handleLongClick();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_stabilization_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final Drawable mEnable = mContext.getDrawable(R.drawable.ic_screen_stabilization_enabled);
        final Drawable mDisable = mContext.getDrawable(R.drawable.ic_screen_stabilization_disabled);
        state.value = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.STABILIZATION_ENABLE, 0) == 1);
        state.label = mContext.getString(R.string.quick_settings_stabilization_label);
        if (state.value) {
            state.icon = new DrawableIcon(mEnable);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.icon = new DrawableIcon(mDisable);
            state.state = Tile.STATE_INACTIVE;
        }
        state.contentDescription = state.label;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.HAVOC_SETTINGS;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.quick_settings_stabilization_on);
        } else {
            return mContext.getString(R.string.quick_settings_stabilization_off);
        }
    }

    private class ScreenStabilizationDetailAdapter implements DetailAdapter {
        private SeekBar mVelocityFriction;
        private SeekBar mVelocityAmplitude;
        private SeekBar mPositionFriction;

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QS_CUSTOM;
        }

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_stabilization_label);
        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public Intent getSettingsIntent() {
            return null;
        }

        @Override
        public void setToggleState(boolean state) {
            Settings.System.putIntForUser(mResolver, Settings.System.STABILIZATION_ENABLE, state ? 1 : 0, UserHandle.USER_CURRENT);
            refreshState();
            if (!state) {
                showDetail(false);
            }
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final View view = convertView != null ? convertView : LayoutInflater.from(context).inflate(
                            R.layout.screen_stabilization_panel, parent, false);

            if (convertView == null) {
                mVelocityFriction = (SeekBar) view.findViewById(R.id.stabilization_velocity_friction_seekbar);
                mVelocityFriction.setOnSeekBarChangeListener(mSeekBarListener);

                mVelocityAmplitude = (SeekBar) view.findViewById(R.id.stabilization_velocity_amplitude_seekbar);
                mVelocityAmplitude.setOnSeekBarChangeListener(mSeekBarListener);

                mPositionFriction = (SeekBar) view.findViewById(R.id.stabilization_position_friction_seekbar);
                mPositionFriction.setOnSeekBarChangeListener(mSeekBarListener);
            }

            refreshFloat(Settings.System.getFloatForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_FRICTION, 0.1f, UserHandle.USER_CURRENT), mVelocityFriction);
            refreshFloat(Settings.System.getFloatForUser(mResolver, Settings.System.STABILIZATION_POSITION_FRICTION, 0.1f, UserHandle.USER_CURRENT), mPositionFriction);
            refreshInt(Settings.System.getIntForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 8000, UserHandle.USER_CURRENT), mVelocityAmplitude);

            return view;
        }

        private void refreshFloat(float progress, SeekBar pref) {
            if (progress < 0.02f) {
                pref.setProgress(1);
            } else if (progress < 0.06f ) {
                pref.setProgress(2);
            } else if (progress < 0.11f) {
                pref.setProgress(3);
            } else if (progress < 0.21f) {
                pref.setProgress(4);
            } else if (progress < 0.31f) {
                pref.setProgress(5);
            }
        }

        private void refreshInt(int progress, SeekBar pref) {
            switch (progress) {
                case 4000:
                    pref.setProgress(1);
                    break;
                case 6000:
                    pref.setProgress(2);
                    break;
                case 8000:
                    pref.setProgress(3);
                    break;
                case 10000:
                    pref.setProgress(4);
                    break;
                case 12000:
                    pref.setProgress(5);
                    break;
            }
        }
    }

    private void updateValuesFloat(int progress, boolean isPosition) {
        final String key = isPosition ? Settings.System.STABILIZATION_POSITION_FRICTION : Settings.System.STABILIZATION_VELOCITY_FRICTION;
        switch (progress) {
            case 1:
                Settings.System.putFloatForUser(mResolver, key, 0.01f, UserHandle.USER_CURRENT);
                break;
            case 2:
                Settings.System.putFloatForUser(mResolver, key, 0.05f, UserHandle.USER_CURRENT);
                break;
            case 3:
                Settings.System.putFloatForUser(mResolver, key, 0.1f, UserHandle.USER_CURRENT);
                break;
            case 4:
                Settings.System.putFloatForUser(mResolver, key, 0.2f, UserHandle.USER_CURRENT);
                break;
            case 5:
                Settings.System.putFloatForUser(mResolver, key, 0.3f, UserHandle.USER_CURRENT);
                break;
        }
    }

    private void updateValuesInt(int progress) {
        switch (progress) {
            case 1:
                Settings.System.putFloatForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 4000, UserHandle.USER_CURRENT);
                break;
            case 2:
                Settings.System.putFloatForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 6000, UserHandle.USER_CURRENT);
                break;
            case 3:
                Settings.System.putFloatForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 8000, UserHandle.USER_CURRENT);
                break;
            case 4:
                Settings.System.putFloatForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 10000, UserHandle.USER_CURRENT);
                break;
            case 5:
                Settings.System.putFloatForUser(mResolver, Settings.System.STABILIZATION_VELOCITY_AMPLITUDE, 12000, UserHandle.USER_CURRENT);
                break;
        }
    }
}