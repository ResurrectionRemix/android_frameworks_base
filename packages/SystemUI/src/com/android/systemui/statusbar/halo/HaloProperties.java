/*
 * Copyright (C) 2013 ParanoidAndroid.
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

package com.android.systemui.statusbar.halo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.ColorFilterMaker;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;

import com.android.systemui.R;

public class HaloProperties extends FrameLayout {

    public enum Overlay {
        NONE,
        BLACK_X,
        BACK_LEFT,
        BACK_RIGHT,
        DISMISS,
        SILENCE,
        CLEAR_ALL
    }

    private LayoutInflater mInflater;

    protected int mHaloX = 0, mHaloY = 0;
    protected float mHaloContentAlpha = 0;

    private Drawable mHaloDismiss;
    private Drawable mHaloBackL;
    private Drawable mHaloBackR;
    private Drawable mHaloBlackX;
    private Drawable mHaloClearAll;
    private Drawable mHaloSilence;
    private Drawable mHaloCurrentOverlay;

    protected View mHaloBubble;
    protected ImageView mHaloBg, mHaloBgCustom, mHaloIcon, mHaloOverlay;

    protected View mHaloContentView, mHaloTickerContent;
    protected TextView mHaloTextViewR, mHaloTextViewL;

    protected View mHaloNumberView;
    protected TextView mHaloNumber;

    private boolean mEnableColor;

    private boolean mAttached = false;

    private SettingsObserver mSettingsObserver;
    private Handler mHandler;

    CustomObjectAnimator mHaloOverlayAnimator;

    public HaloProperties(Context context) {
        super(context);

        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mHaloDismiss = mContext.getResources().getDrawable(R.drawable.halo_dismiss);
        mHaloBackL = mContext.getResources().getDrawable(R.drawable.halo_back_left);
        mHaloBackR = mContext.getResources().getDrawable(R.drawable.halo_back_right);
        mHaloBlackX = mContext.getResources().getDrawable(R.drawable.halo_black_x);
        mHaloClearAll = mContext.getResources().getDrawable(R.drawable.halo_clear_all);
        mHaloSilence = mContext.getResources().getDrawable(R.drawable.halo_silence);

        mHaloBubble = mInflater.inflate(R.layout.halo_bubble, null);
        mHaloBg = (ImageView) mHaloBubble.findViewById(R.id.halo_bg);
        mHaloBgCustom = (ImageView) mHaloBubble.findViewById(R.id.halo_bg_custom);
        mHaloIcon = (ImageView) mHaloBubble.findViewById(R.id.app_icon);
        mHaloOverlay = (ImageView) mHaloBubble.findViewById(R.id.halo_overlay);

        mHaloContentView = mInflater.inflate(R.layout.halo_speech, null);
        mHaloTickerContent = mHaloContentView.findViewById(R.id.ticker);
        mHaloTextViewR = (TextView) mHaloTickerContent.findViewById(R.id.bubble_r);
        mHaloTextViewR.setAlpha(0f);
        mHaloTextViewL = (TextView) mHaloTickerContent.findViewById(R.id.bubble_l);
        mHaloTextViewL.setAlpha(0f);

        updateColorView();

        mHaloNumberView = mInflater.inflate(R.layout.halo_number, null);
        mHaloNumber = (TextView) mHaloNumberView.findViewById(R.id.number);
        mHaloNumber.setAlpha(0f);

        mHaloOverlayAnimator = new CustomObjectAnimator(this);
        mHandler = new Handler();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
            updateColorView();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    } 

    public void setHaloX(int value) {
        mHaloX = value;
    }

    public void setHaloY(int value) {
        mHaloY = value;
    }

    public int getHaloX() {
        return mHaloX; 
    }

    public int getHaloY() {
        return mHaloY;
    }

    public void setHaloContentAlpha(float value) {
        mHaloContentAlpha = value;
        mHaloTextViewL.setAlpha(value);
        mHaloTextViewR.setAlpha(value);
    }

    public float getHaloContentAlpha() {
        return mHaloContentAlpha;
    }

    public void setHaloOverlay(Overlay overlay) {
        setHaloOverlay(overlay, mHaloOverlay.getAlpha());
    }

    public void setHaloOverlay(Overlay overlay, float overlayAlpha) {

        Drawable d = null;

        switch(overlay) {
            case BLACK_X:
                d = mHaloBlackX;
                break;
            case BACK_LEFT:
                d = mHaloBackL;
                break;
            case BACK_RIGHT:
                d = mHaloBackR;
                break;
            case DISMISS:
                d = mHaloDismiss;
                break;
            case SILENCE:
                d = mHaloSilence;
                break;
            case CLEAR_ALL:
                d = mHaloClearAll;
                break;
        }

        if (d != mHaloCurrentOverlay) {
            mHaloOverlay.setImageDrawable(d);
            mHaloCurrentOverlay = d;
        }

        mHaloOverlay.setAlpha(overlayAlpha);
        updateResources();
    }

    public void updateResources() {
        mHaloContentView.measure(MeasureSpec.getSize(mHaloContentView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloContentView.getMeasuredHeight()));
        mHaloContentView.layout(0, 0, 0, 0);

        mHaloBubble.measure(MeasureSpec.getSize(mHaloBubble.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloBubble.getMeasuredHeight()));
        mHaloBubble.layout(0, 0, 0, 0);

        mHaloNumberView.measure(MeasureSpec.getSize(mHaloNumberView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloNumberView.getMeasuredHeight()));
        mHaloNumberView.layout(0, 0, 0, 0);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_COLORS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_CIRCLE_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_BUBBLE_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_BUBBLE_TEXT_COLOR), false, this);
            updateColorView();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateColorView();
        }
    }

    private void updateColorView() {
        ContentResolver cr = mContext.getContentResolver();
        mEnableColor = Settings.System.getInt(cr,
               Settings.System.HALO_COLORS, 0) == 1;
        int mCircleColor = Settings.System.getInt(cr,
               Settings.System.HALO_CIRCLE_COLOR, 0xFF33B5E5);
        int mBubbleColor = Settings.System.getInt(cr,
               Settings.System.HALO_BUBBLE_COLOR, 0xFF33B5E5);
        int mTextColor = Settings.System.getInt(cr, 
               Settings.System.HALO_BUBBLE_TEXT_COLOR, 0xFFFFFFFF);

        if (mEnableColor) {
           // Ring
           mHaloBgCustom.setBackgroundResource(R.drawable.halo_bg_custom);
           mHaloBgCustom.getBackground().setColorFilter(ColorFilterMaker.
                   changeColorAlpha(mCircleColor, .32f, 0f));
           mHaloBg.setVisibility(View.GONE);
           mHaloBgCustom.setVisibility(View.VISIBLE);

           // Speech bubbles
           mHaloTextViewL.setBackgroundResource(R.drawable.bubble_l_custom);
           mHaloTextViewL.getBackground().setColorFilter(ColorFilterMaker.
                    changeColorAlpha(mBubbleColor, .32f, 0f));
           mHaloTextViewL.setTextColor(mTextColor);
           mHaloTextViewR.setBackgroundResource(R.drawable.bubble_r_custom);
           mHaloTextViewR.getBackground().setColorFilter(ColorFilterMaker.
                    changeColorAlpha(mBubbleColor, .32f, 0f));
           mHaloTextViewR.setTextColor(mTextColor);
        } else {
           // Ring
           mHaloBg.setVisibility(View.VISIBLE);
           mHaloBgCustom.setVisibility(View.GONE);

           // Speech bubbles
           mHaloTextViewL.setTextColor(getResources().getColor(R.color.halo_text_color));
           mHaloTextViewR.setTextColor(getResources().getColor(R.color.halo_text_color));
        }
    }
}
