/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ZenModeController;

public class SoundTile extends QSTileImpl<BooleanState> {

    private final ZenModeController mZenController;
    private final AudioManager mAudioManager;

    private boolean mListening = false;

    private BroadcastReceiver mReceiver;
    private IntentFilter mFilter;

    public SoundTile(QSHost host) {
        super(host);
        mZenController = Dependency.get(ZenModeController.class);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshState();
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void handleClick() {
        updateState();
    }

    @Override
    public void handleLongClick() {
        mAudioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    private void updateState() {
        int oldState = mAudioManager.getRingerModeInternal();
        int newState = oldState;
        switch (oldState) {
            case AudioManager.RINGER_MODE_NORMAL:
                newState = AudioManager.RINGER_MODE_VIBRATE;
                mAudioManager.setRingerModeInternal(newState);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                newState = AudioManager.RINGER_MODE_SILENT;
                mZenController.setZen(Global.ZEN_MODE_NO_INTERRUPTIONS, null, TAG);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                newState = AudioManager.RINGER_MODE_NORMAL;
                mZenController.setZen(Global.ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(newState);
                break;
            default:
                break;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_sound_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        switch (mAudioManager.getRingerModeInternal()) {
            case AudioManager.RINGER_MODE_NORMAL:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_ringer_audible);
                state.label = mContext.getString(R.string.quick_settings_sound_ring);
                state.contentDescription =  mContext.getString(
                        R.string.quick_settings_sound_ring);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_ringer_vibrate);
                state.label = mContext.getString(R.string.quick_settings_sound_vibrate);
                state.contentDescription =  mContext.getString(
                        R.string.quick_settings_sound_vibrate);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_ringer_silent);
                state.label = mContext.getString(R.string.quick_settings_sound_dnd);
                state.contentDescription =  mContext.getString(
                        R.string.quick_settings_sound_dnd);
                break;
            default:
                break;
        }
    }

    @Override
    public int getMetricsCategory() {
return MetricsEvent.RESURRECTED;
    }
}
