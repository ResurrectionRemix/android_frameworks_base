/*
* Copyright (C) 2015 The Android Open Source Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.rr;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Outline;
import android.graphics.PorterDuff.Mode;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.UserHandle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationMediaManager;

import com.android.internal.graphics.palette.Palette;
import com.android.internal.graphics.ColorUtils;

public class RRMusic extends RelativeLayout implements NotificationMediaManager.MediaListener, Palette.PaletteAsyncListener {
   private static final boolean DEBUG = true;
   private static final String TAG = "RRMusic";

   private Context mContext;
   private NotificationMediaManager mMediaManager;
   private MediaController mMediaController;
   private final Handler mHandler = new Handler();

   private CharSequence mMediaTitle;
   private CharSequence mMediaArtist;
   private Drawable mMediaArtwork;
   private boolean mMediaIsVisible;

   private TextView mTitle;
   private TextView mArtist;
   private ImageView mArtwork;
   private int shadow;
   private int colorArtwork;
   private int colorTextIcons;

   private ImageButton mPrevious;
   private ImageButton mPlayPause;
   private ImageButton mNext;

   private RRMusic mBackground;

   public RRMusic(Context context) {
       this(context, null);
   }

   public RRMusic(Context context, AttributeSet attrs) {
       this(context, attrs, 0);
   }

   public RRMusic(Context context, AttributeSet attrs, int defStyleAttr) {
       this(context, attrs, defStyleAttr, 0);
   }

   public RRMusic(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
       super(context, attrs, defStyleAttr, defStyleRes);
       if (DEBUG) Log.d(TAG, "New Instance");
   }

   public void initDependencies(NotificationMediaManager mediaManager, Context context) {
      mContext = context;
      mMediaManager = mediaManager;
      mMediaManager.addCallback(this);
      updateObjects();
   }

   /**
    * Called whenever new media metadata is available.
    * @param metadata New metadata.
    */
   @Override
   public void onMetadataOrStateChanged(MediaMetadata metadata, @PlaybackState.State int state) {

          CharSequence title = metadata == null ? null : metadata.getText(
                  MediaMetadata.METADATA_KEY_TITLE);
          CharSequence artist = metadata == null ? null : metadata.getText(
                  MediaMetadata.METADATA_KEY_ARTIST);
          Bitmap artwork = metadata == null ? null : metadata.getBitmap(
                  MediaMetadata.METADATA_KEY_ALBUM_ART);
          Drawable d = new BitmapDrawable(mContext.getResources(), artwork);

          mMediaTitle = title;
          mMediaArtist = artist;
          mMediaArtwork = d;

          update();
   }

   public void update() {
      updateObjects();
      updateButtons();
      updateViews();
      updateIconPlayPause();
   }

   public void updateIconPlayPause() {
       if ( !(mMediaManager.getPlaybackStateIsEqual(PlaybackState.STATE_PLAYING)) ) {
           mPlayPause.setImageResource(R.drawable.ic_play_arrow_white);
       } else {
           mPlayPause.setImageResource(R.drawable.ic_pause_white);
       }
   }

   public void updateObjects() {
      if (mTitle == null || mArtist == null || mArtwork == null || mPrevious == null || mPlayPause == null || mNext == null) {
          mArtwork = findViewById(R.id.artwork);
          mTitle = (TextView) findViewById(R.id.title);
          mArtist = (TextView) findViewById(R.id.artist);
          mPrevious = findViewById(R.id.button_previous);
          mPlayPause = findViewById(R.id.button_play_pause);
          mNext = findViewById(R.id.button_next);
      }
   }

   public void updateButtons() {
       mPrevious.setOnClickListener(v -> {
            mMediaManager.skipTrackPrevious();
       });

       mPlayPause.setOnClickListener(v -> {
            mMediaManager.playPauseTrack();
       });

       mNext.setOnClickListener(v -> {
            mMediaManager.skipTrackNext();
       });
   }

   public void updateViews() {

        boolean show = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.MUSIC_VOLUME_PANEL_DIALOG, 0, UserHandle.USER_CURRENT) == 1;

        if (mMediaManager != null && mMediaTitle != null && mMediaArtist != null && mMediaArtwork != null) {
            mTitle.setText(mMediaTitle.toString());
            mTitle.setSelected(true);
            mArtist.setText(mMediaArtist.toString());
            mArtist.setSelected(true);

            mArtwork.setImageDrawable(mMediaArtwork);

            if (mMediaManager.getMediaMetadata().getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null) {
                Palette.generateAsync((mMediaManager.getMediaMetadata().getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)), this);
            }

            setVisibility(show ? View.VISIBLE : View.GONE);

        } else {
            setVisibility(View.GONE);
        }

        mArtwork.setClipToOutline(true);

   }

   @Override
   public void onGenerated(Palette palette) {

        shadow = 115;
        colorArtwork = Color.BLACK;
        colorTextIcons = Color.WHITE;

        colorTextIcons = palette.getLightVibrantColor(colorTextIcons);
        colorArtwork = ColorUtils.setAlphaComponent(palette.getDarkVibrantColor(colorArtwork), shadow);

        mArtwork.setColorFilter(colorArtwork, Mode.SRC_ATOP);
        mTitle.setTextColor(colorTextIcons);
        mArtist.setTextColor(colorTextIcons);
        mPrevious.setColorFilter(colorTextIcons);
        mPlayPause.setColorFilter(colorTextIcons);
        mNext.setColorFilter(colorTextIcons);
   }
}

