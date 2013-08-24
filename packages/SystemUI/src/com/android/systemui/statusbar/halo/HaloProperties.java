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

import android.os.Handler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.ColorFilterMaker;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.util.Log;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.util.TypedValue;
import android.provider.Settings;

import com.android.systemui.R;

public class HaloProperties extends FrameLayout {

    public enum Overlay {
        NONE,
        BLACK_X,
        BACK_LEFT,
        BACK_RIGHT,
        DISMISS,
        SILENCE_LEFT,
        SILENCE_RIGHT,
        CLEAR_ALL,
        MESSAGE
    }

    public enum ContentStyle {
        CONTENT_NONE,
        CONTENT_DOWN,
        CONTENT_UP
    }

    public enum MessageType {
        MESSAGE,
        PINNED,
        SYSTEM
    }

    private Handler mAnimQueue = new Handler();
    private LayoutInflater mInflater;

    protected int mHaloX = 0, mHaloY = 0;
    protected int mHaloContentY = 0;
    protected float mHaloContentAlpha = 0;
    private int mHaloContentHeight = 0;

    private Drawable mHaloDismiss;
    private Drawable mHaloBackL;
    private Drawable mHaloBackR;
    private Drawable mHaloBlackX;
    private Drawable mHaloClearAll;
    private Drawable mHaloSilenceL;
    private Drawable mHaloSilenceR;
    private Drawable mHaloMessage;
    private Drawable mHaloCurrentOverlay;

    protected Drawable mHaloSpeechL, mHaloSpeechR, mHaloSpeechLD, mHaloSpeechRD;

    protected View mHaloBubble;
    protected ImageView mHaloBg, mHaloBgCustom, mHaloIcon, mHaloOverlay;

    protected View mHaloContentView, mHaloTickerContent, mHaloTickerWrapper;
    protected TextView mHaloTextView;

    protected View mHaloNumberView;
    protected TextView mHaloNumber, mHaloCount;
    protected ImageView mHaloNumberIcon, mHaloSystemIcon, mHaloPinned;
    protected RelativeLayout mHaloNumberContainer;

    private boolean mEnableColor;

    private boolean mAttached = false;

    private SettingsObserver mSettingsObserver;
    private Handler mHandler;

    private float mFraction = 1.0f;
    private int mHaloMessageNumber = 0;
    private MessageType mHaloMessageType = MessageType.MESSAGE;

    private boolean mLastContentStateLeft = true;

    CustomObjectAnimator mHaloOverlayAnimator;

    public HaloProperties(Context context) {
        super(context);

        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mHaloDismiss = mContext.getResources().getDrawable(R.drawable.halo_dismiss);
        mHaloBackL = mContext.getResources().getDrawable(R.drawable.halo_back_left);
        mHaloBackR = mContext.getResources().getDrawable(R.drawable.halo_back_right);
        mHaloBlackX = mContext.getResources().getDrawable(R.drawable.halo_black_x);
        mHaloClearAll = mContext.getResources().getDrawable(R.drawable.halo_clear_all);
        mHaloSilenceL = mContext.getResources().getDrawable(R.drawable.halo_silence_left);
        mHaloSilenceR = mContext.getResources().getDrawable(R.drawable.halo_silence_right);
        mHaloMessage = mContext.getResources().getDrawable(R.drawable.halo_message);

        mHaloSpeechL = mContext.getResources().getDrawable(R.drawable.halo_speech_l_u);
        mHaloSpeechR = mContext.getResources().getDrawable(R.drawable.halo_speech_r_u);
        mHaloSpeechLD = mContext.getResources().getDrawable(R.drawable.halo_speech_l_d);
        mHaloSpeechRD = mContext.getResources().getDrawable(R.drawable.halo_speech_r_d);

        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mHaloBubble = mInflater.inflate(R.layout.halo_bubble, null);
        mHaloBg = (ImageView) mHaloBubble.findViewById(R.id.halo_bg);
        mHaloBgCustom = (ImageView) mHaloBubble.findViewById(R.id.halo_bg_custom);
        mHaloIcon = (ImageView) mHaloBubble.findViewById(R.id.app_icon);
        mHaloOverlay = (ImageView) mHaloBubble.findViewById(R.id.halo_overlay);

        mHaloContentView = mInflater.inflate(R.layout.halo_speech, null);
        mHaloTickerWrapper = mHaloContentView.findViewById(R.id.ticker_wrapper);
        mHaloTickerContent = mHaloContentView.findViewById(R.id.ticker);
        mHaloTextView = (TextView) mHaloContentView.findViewById(R.id.bubble);
        mHaloTextView.setAlpha(1f);

        mHaloNumberView = mInflater.inflate(R.layout.halo_number, null);
        mHaloNumberContainer = (RelativeLayout)mHaloNumberView.findViewById(R.id.container);
        mHaloNumber = (TextView) mHaloNumberView.findViewById(R.id.number);
        mHaloCount = (TextView) mHaloNumberView.findViewById(R.id.haloCount);
        mHaloNumberIcon = (ImageView) mHaloNumberView.findViewById(R.id.icon);
        mHaloNumberIcon.setImageDrawable(mContext.getResources().getDrawable(R.drawable.halo_batch_message));
        mHaloSystemIcon = (ImageView) mHaloNumberView.findViewById(R.id.system);
        mHaloSystemIcon.setImageDrawable(mContext.getResources().getDrawable(R.drawable.halo_system_message));
        mHaloPinned = (ImageView) mHaloNumberView.findViewById(R.id.pinned);
        mHaloPinned.setImageDrawable(mContext.getResources().getDrawable(R.drawable.halo_pinned_app));

        mHaloContentHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height);

        mFraction = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        setHaloSize(mFraction);

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

    int newPaddingHShort;
    int newPaddingHWide;
    int newPaddingVTop;
    int newPaddingVBottom;
    public void setHaloSize(float fraction) {

        final int newBubbleSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * fraction);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newBubbleSize, newBubbleSize);
        mHaloBg.setLayoutParams(layoutParams);
        mHaloBgCustom.setLayoutParams(layoutParams);
        mHaloIcon.setLayoutParams(layoutParams);
        mHaloOverlay.setLayoutParams(layoutParams);

        newPaddingHShort = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_hpadding_short) * fraction);
        newPaddingHWide = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_hpadding_wide) * fraction);
        newPaddingVTop = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_vpadding_top) * fraction);
        newPaddingVBottom = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_vpadding_bottom) * fraction);

        final int newNumberSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_size) * fraction);
        final int newNumberTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_text_size) * fraction);
        RelativeLayout.LayoutParams layoutParams2 = new RelativeLayout.LayoutParams(newNumberSize, newNumberSize);
        mHaloNumber.setLayoutParams(layoutParams2);
        mHaloNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, newNumberTextSize);
        mHaloCount.setLayoutParams(layoutParams2);
        mHaloCount.setTextSize(TypedValue.COMPLEX_UNIT_PX, newNumberTextSize);

        final int newSpeechTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_text_size) * fraction);
        mHaloTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSpeechTextSize);

        final int newBatchIconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_icon_size) * fraction);
        RelativeLayout.LayoutParams layoutParams3 = new RelativeLayout.LayoutParams(newBatchIconSize, newBatchIconSize);
        layoutParams3.addRule(RelativeLayout.CENTER_VERTICAL);
        layoutParams3.addRule(RelativeLayout.CENTER_HORIZONTAL);
        mHaloNumberIcon.setLayoutParams(layoutParams3);
        mHaloSystemIcon.setLayoutParams(layoutParams3);
        mHaloPinned.setLayoutParams(layoutParams3);

        updateResources(mLastContentStateLeft);
        updateColorView();

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

    public void setHaloContentY(int value) {
        mHaloContentY = value;
    }

    public int getHaloContentY() {
        return mHaloContentY;
    }

    protected CustomObjectAnimator msgNumberFlipAnimator = new CustomObjectAnimator(this);
    protected CustomObjectAnimator msgNumberAlphaAnimator = new CustomObjectAnimator(this);
    public void animateHaloBatch(final int value, final int msgCount, final boolean alwaysFlip, int delay, final MessageType msgType) {
        if (msgCount == 0) {
            msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "alpha", 0f).setDuration(1000),
                    new DecelerateInterpolator(), null, delay, null);
            return;
        }
        mAnimQueue.removeCallbacksAndMessages(null);
        mAnimQueue.postDelayed(new Runnable() {
            public void run() {
                // Allow transitions only if no overlay is set
                if (mHaloCurrentOverlay == null) {
                    msgNumberAlphaAnimator.cancel(true);
                    float oldAlpha = mHaloNumberContainer.getAlpha();

                    mHaloNumberContainer.setAlpha(1f);
                    mHaloNumber.setAlpha(1f);
                    mHaloCount.setAlpha(0f);
                    mHaloCount.setText("");
                    mHaloNumberIcon.setAlpha(0f);
                    mHaloSystemIcon.setAlpha(0f);
                    mHaloPinned.setAlpha(0f);
                    if (msgCount > 0) {
                        mHaloNumber.setAlpha(0f);
                        mHaloCount.setText(String.valueOf(msgCount));
                        mHaloCount.setAlpha(1f);
                    } else if (value < 1 && msgCount < 1) {
                        mHaloNumber.setText("");
                        if (msgType == MessageType.PINNED) {
                            mHaloPinned.setAlpha(1f);
                        } else if (msgType == MessageType.SYSTEM) {
                            mHaloSystemIcon.setAlpha(1f);
                        } else {
                            mHaloNumberIcon.setAlpha(1f);
                        }
                    } else if (value < 100) {
                        mHaloNumber.setText(String.valueOf(value));
                    } else {
                        mHaloNumber.setText("+");
                    }

                    if (value < 1 && msgCount < 1) {
                        msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "alpha", 0f).setDuration(1000),
                                new DecelerateInterpolator(), null, 1500, null);
                    }

                    // Do NOT flip when ...
                    if (!alwaysFlip && oldAlpha == 1f && mHaloMessageType == msgType
                            && (value == mHaloMessageNumber || (value > 99 && mHaloMessageNumber > 99))) return;
                    msgNumberFlipAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "rotationY", -180, 0).setDuration(500),
                                new DecelerateInterpolator(), null);
                }
                mHaloMessageNumber = value;
                mHaloMessageType = msgType;
            }}, delay);
    }

    void setHaloMessageNumber(int count) {
        mHaloCount.setText(String.valueOf(count));
        invalidate();
    }

    public void setHaloContentAlpha(float value) {
        mHaloTickerWrapper.setAlpha(value);
        mHaloTextView.setTextColor(mHaloTextView.getTextColors().withAlpha((int)(value * 255)));
        mHaloTickerWrapper.invalidate();
        mHaloContentAlpha = value;
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
            case SILENCE_LEFT:
                d = mHaloSilenceL;
                break;
            case SILENCE_RIGHT:
                d = mHaloSilenceR;
                break;
            case CLEAR_ALL:
                d = mHaloClearAll;
                break;
            case MESSAGE:
                d = mHaloMessage;
                break;
        }

        if (d != mHaloCurrentOverlay) {
            mHaloOverlay.setImageDrawable(d);
            mHaloCurrentOverlay = d;

            // Fade out number batch
            if (overlay != Overlay.NONE) {
            msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "alpha", 0f).setDuration(100),
                        new DecelerateInterpolator(), null);
            }
        }

        mHaloOverlay.setAlpha(overlayAlpha);
        updateResources(mLastContentStateLeft);
    }

    private ContentStyle mLastContentStyle = ContentStyle.CONTENT_NONE;
    public void setHaloContentBackground(boolean contentLeft, ContentStyle style) {
        if (style != mLastContentStyle) {
            // Set background
            switch(style) {
                case CONTENT_UP:
                    mHaloTickerWrapper.setBackground(contentLeft ? mHaloSpeechL : mHaloSpeechR);
                    break;
                case CONTENT_DOWN:
                    mHaloTickerWrapper.setBackground(contentLeft ? mHaloSpeechLD : mHaloSpeechRD);
                    break;
            }

            // ... and override its padding
            if (contentLeft) {
                mHaloTickerWrapper.setPadding(newPaddingHWide, newPaddingVTop, newPaddingHShort, newPaddingVBottom);
            } else {
                mHaloTickerWrapper.setPadding(newPaddingHShort, newPaddingVTop, newPaddingHWide, newPaddingVBottom);
            }

            mLastContentStyle = style;
        }
    }

    public void setHaloContentHeight(int size) {
        mHaloContentHeight = size;
    }

    public void updateResources(boolean contentLeft) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, mHaloContentHeight);
        mHaloTickerWrapper.setLayoutParams(layoutParams);

        // Set background and override its padding
        setHaloContentBackground(contentLeft, mLastContentStyle);

        // Measure controls
        mHaloContentView.measure(MeasureSpec.getSize(mHaloContentView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloContentView.getMeasuredHeight()));
        mHaloContentView.layout(0, 0, 0, 0);

        mHaloBubble.measure(MeasureSpec.getSize(mHaloBubble.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloBubble.getMeasuredHeight()));
        mHaloBubble.layout(0, 0, 0, 0);

        mHaloNumberView.measure(MeasureSpec.getSize(mHaloNumberView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloNumberView.getMeasuredHeight()));
        mHaloNumberView.layout(0, 0, 0, 0);

        // Maximal stretch for speech bubble
        final int iconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * mFraction);
        final int maximumWidth = (int)(getWidth() * 0.95f) - iconSize;

        if (mHaloTickerWrapper.getMeasuredWidth() > maximumWidth) {
            final int optimalWidth = iconSize * 5;
            final int newSize = maximumWidth > optimalWidth ? optimalWidth : maximumWidth;
            layoutParams = new LinearLayout.LayoutParams(newSize, mHaloContentHeight);
            mHaloTickerWrapper.setLayoutParams(layoutParams);

            mHaloContentView.measure(MeasureSpec.getSize(mHaloContentView.getMeasuredWidth()),
                    MeasureSpec.getSize(mHaloContentView.getMeasuredHeight()));
            mHaloContentView.layout(0, 0, 0, 0);
        }

        mLastContentStateLeft = contentLeft;
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
