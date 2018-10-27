/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 The SlimRoms Project
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
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import android.service.quicksettings.Tile;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import javax.inject.Inject;

/** Quick settings tile: Music **/
public class MusicTile extends QSTileImpl<BooleanState> {

    private final String TAG = "MusicTile";
    private final boolean DBG = false;
    private final AudioManager mAudioManager;

    private boolean mActive = false;
    private boolean mClientIdLost = true;

    private Metadata mMetadata = new Metadata();
    private Handler mHandler = new Handler();
    private RemoteController mRemoteController;

    private int mTaps = 0;

    @Inject
    public MusicTile(QSHost host) {
        super(host);
        mRemoteController = new RemoteController(mContext, mRCClientUpdateListener);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.registerRemoteController(mRemoteController);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
    }

    @Override
    public void handleClick() {
        if (mActive) {
            checkDoubleClick();
        } else {
            sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }

        refreshState();
    }

    @Override
    public void handleLongClick() {
        sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_music_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mActive) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_media_pause);
            state.label = mMetadata.trackTitle != null
                ? mMetadata.trackTitle : mContext.getString(R.string.quick_settings_music_pause);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_media_play);
            state.label = mContext.getString(R.string.quick_settings_music_play);
        }
        state.state = mActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    private void playbackStateUpdate(int state) {
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mActive) {
            mActive = active;
            refreshState();
        }
    }

    private void checkDoubleClick() {
        mHandler.removeCallbacks(checkDouble);
        if (mTaps > 0) {
            // Music app
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_MUSIC);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTaps = 0;
        } else {
            mTaps += 1;
            mHandler.postDelayed(checkDouble,
                    ViewConfiguration.getDoubleTapTimeout());
        }
    }

    private void sendMediaButtonClick(int keyCode) {
        if (!mClientIdLost) {
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } else {
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent key = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            MediaSessionLegacyHelper.getHelper(mContext).sendMediaButtonEvent(key, true);
            MediaSessionLegacyHelper.getHelper(mContext).sendMediaButtonEvent(
                KeyEvent.changeAction(key, KeyEvent.ACTION_UP), true);
        }
    }

    final Runnable checkDouble = new Runnable () {
        public void run() {
            mTaps = 0;
            sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    };

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {

        private String mCurrentTrack = null;

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mCurrentTrack = null;
                mActive = false;
                mClientIdLost = true;
                refreshState();
            }
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            mClientIdLost = false;
            if (mMetadata.trackTitle != null
                    && !mMetadata.trackTitle.equals(mCurrentTrack)) {
                mCurrentTrack = mMetadata.trackTitle;
                refreshState();
            }
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        private String trackTitle;

        public void clear() {
            trackTitle = null;
        }
    }
}
