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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification;

import android.app.Notification;
import android.content.Context;
import android.content.res.ColorStateList;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.ExpandableNotificationRow;
import com.android.systemui.statusbar.TransformableView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Wraps a notification containing a media template
 */
public class NotificationMediaTemplateViewWrapper extends NotificationTemplateViewWrapper {

    private Context mContext;
    private final Handler mHandler = Dependency.get(Dependency.MAIN_HANDLER);
    private MediaController mMediaController;
    private NotificationMediaManager mMediaManager;
    private View mSeekBarView;
    private SeekBar mSeekBar;
    private TextView mSeekBarElapsedTime;
    private TextView mSeekBarTotalTime;
    private Timer mSeekBarTimer;
    private long mDuration = 0;
    private boolean mTrackingTouch;
    private MediaController.Callback mMediaCallback = new MediaController.Callback() {

        @Override
        public void onSessionDestroyed() {
            clearTimer();
            mMediaController.unregisterCallback(this);
        }
        
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state.getState() != PlaybackState.STATE_PLAYING) {
                clearTimer();
            } else if (mSeekBarTimer == null) {
                startTimer();
            }
        }
    };
    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mSeekBarElapsedTime.setText(millisecondsToTimeString(progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mTrackingTouch = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mTrackingTouch = false;
            if (mMediaController != null && canSeekMedia()) {
                mMediaController.getTransportControls().seekTo((long) mSeekBar.getProgress());
            }
            mUpdatePlaybackUi.run();
        }
    };
    protected final Runnable mUpdatePlaybackUi = new Runnable() {

        @Override
        public void run() {
            if (mMediaController == null || mMediaController.getMetadata() == null || mSeekBar == null) {
                clearTimer();
                return;
            }
            long duration = mMediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (mDuration != duration) {
                mDuration = duration;
                mSeekBar.setMax((int) mDuration);
                mSeekBarTotalTime.setText(millisecondsToTimeString(duration));
            }
            if (!mTrackingTouch && mMediaController.getPlaybackState() != null) {
                long position = mMediaController.getPlaybackState().getPosition();
                mSeekBar.setProgress((int) position);
            }
        }
    };

    protected NotificationMediaTemplateViewWrapper(Context ctx, View view,
            ExpandableNotificationRow row) {
        super(ctx, view, row);
        mContext = ctx;
        mMediaManager = Dependency.get(NotificationMediaManager.class);
    }

    View mActions;

    private void resolveViews() {
        mActions = mView.findViewById(com.android.internal.R.id.media_actions);
        final MediaSession.Token token =
                mRow.getEntry().notification.getNotification().extras.getParcelable(
                        Notification.EXTRA_MEDIA_SESSION);
        boolean showCompactMediaSeekbar = true;
        if (token != null && !"media".equals(mView.getTag())) {
            if (mMediaController == null || !mMediaController.getSessionToken().equals(token)) {
                if (mMediaController != null) {
                    mMediaController.unregisterCallback(mMediaCallback);
                }
                mMediaController = new MediaController(mContext, token);
            }
            if (mMediaController.getMetadata() != null) {
                if (mMediaController.getMetadata().getLong("android.media.metadata.DURATION") <= 0) {
                    Log.d("NotificationMediaTVW", "removing seekbar");
                    if (mSeekBarView != null) {
                        mSeekBarView.setVisibility(View.GONE);
                    }
                    return;
                }
                if (mSeekBarView != null) {
                    mSeekBarView.setVisibility(View.VISIBLE);
                }
            }
            ViewStub seekBarStub = mView.findViewById(com.android.internal.R.id.notification_media_seekbar_container);
            if (seekBarStub != null) {
                seekBarStub.setLayoutInflater(LayoutInflater.from(seekBarStub.getContext()));
                seekBarStub.setLayoutResource(com.android.internal.R.layout.notification_material_media_seekbar);
                mSeekBarView = seekBarStub.inflate();
                mSeekBar = mSeekBarView.findViewById(com.android.internal.R.id.notification_media_progress_bar);
                mSeekBar.setOnSeekBarChangeListener(mSeekListener);
                mSeekBarElapsedTime = mSeekBarView.findViewById(com.android.internal.R.id.notification_media_elapsed_time);
                mSeekBarTotalTime = mSeekBarView.findViewById(com.android.internal.R.id.notification_media_total_time);
                if (mSeekBarTimer == null) {
                    if (canSeekMedia()) {
                        mSeekBar.getThumb().setAlpha(255);
                        mSeekBar.setEnabled(true);
                    } else {
                        mSeekBar.getThumb().setAlpha(0);
                        mSeekBar.setEnabled(false);
                    }
                    startTimer();
                    mMediaController.registerCallback(mMediaCallback);
                }
            }
            updateSeekBarTint(mSeekBarView);
        } else {
            if (mSeekBarView != null) {
                mSeekBarView.setVisibility(View.GONE);
            }
        }
    }

    private void startTimer() {
        clearTimer();
        mSeekBarTimer = new Timer(true);
        mSeekBarTimer.schedule(new TimerTask() {
            public void run() {
                mHandler.post(mUpdatePlaybackUi);
            }
        }, 0, 1000);
    }

    private void clearTimer() {
        if (mSeekBarTimer != null) {
            mSeekBarTimer.cancel();
            mSeekBarTimer.purge();
            mSeekBarTimer = null;
        }
    }

    private boolean canSeekMedia() {
        if (mMediaController == null || mMediaController.getPlaybackState() == null) {
            return false;
        }
        long actions = mMediaController.getPlaybackState().getActions();
        return actions == 0 || (actions & PlaybackState.ACTION_SEEK_TO) != 0;
    }

    private String millisecondsToTimeString(long millis) {
        return DateUtils.formatElapsedTime(millis / 1000);
    }

    private void updateSeekBarTint(View view) {
        if (view != null && getNotificationHeader() != null) {
            int originalIconColor = getNotificationHeader().getOriginalIconColor();
            mSeekBarElapsedTime.setTextColor(originalIconColor);
            mSeekBarTotalTime.setTextColor(originalIconColor);
            ColorStateList seekBarColor = ColorStateList.valueOf(originalIconColor);
            mSeekBar.setThumbTintList(seekBarColor);
            mSeekBar.setProgressTintList(seekBarColor.withAlpha(192));
            mSeekBar.setProgressBackgroundTintList(seekBarColor.withAlpha(128));
        }
    }

    @Override
    public void onContentUpdated(ExpandableNotificationRow row) {
        // Reinspect the notification. Before the super call, because the super call also updates
        // the transformation types and we need to have our values set by then.
        resolveViews();
        super.onContentUpdated(row);
    }

    @Override
    protected void updateTransformedTypes() {
        // This also clears the existing types
        super.updateTransformedTypes();
        if (mActions != null) {
            mTransformationHelper.addTransformedView(TransformableView.TRANSFORMING_VIEW_ACTIONS,
                    mActions);
        }
    }

    @Override
    public boolean isDimmable() {
        return getCustomBackgroundColor() == 0;
    }

    @Override
    public boolean shouldClipToRounding(boolean topRounded, boolean bottomRounded) {
        return true;
    }
}
