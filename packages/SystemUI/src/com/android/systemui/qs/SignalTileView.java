/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile.SignalState;

import android.provider.Settings;

/** View that represents a custom quick settings tile for displaying signal info (wifi/cell). **/
public final class SignalTileView extends QSTileView {
    private static final long DEFAULT_DURATION = new ValueAnimator().getDuration();
    private static final long SHORT_DURATION = DEFAULT_DURATION / 3;

    private FrameLayout mIconFrame;
    private ImageView mSignal;
    private ImageView mOverlay;
    private ImageView mIn;
    private ImageView mOut;
    private boolean mQSColorSwitch = false;
    private SettingsObserver mSettingsObserver;	

    private int mWideOverlayIconStartPadding;

    public SignalTileView(Context context) {
        super(context);

        mIn = addTrafficView(R.drawable.ic_qs_signal_in);
        mOut = addTrafficView(R.drawable.ic_qs_signal_out);

        mWideOverlayIconStartPadding = context.getResources().getDimensionPixelSize(
                R.dimen.wide_type_icon_start_padding_qs);
	mSettingsObserver = new SettingsObserver(mHandler);
    }

    private ImageView addTrafficView(int icon) {
	updateIconColor();
        final ImageView traffic = new ImageView(mContext);
        traffic.setImageResource(icon);
	  if ( mQSColorSwitch) {
            traffic.setColorFilter(mIconColor, Mode.MULTIPLY);	  
        }
        traffic.setAlpha(0f);
        addView(traffic);
        return traffic;
    }

    @Override
    public View createIcon() {
	updateIconColor();
        mIconFrame = new FrameLayout(mContext);
        mSignal = new ImageView(mContext);
        mIconFrame.addView(mSignal);
        mOverlay = new ImageView(mContext);
	 if (mQSColorSwitch) {
            mSignal.setColorFilter(mIconColor, Mode.MULTIPLY);
        }
        mIconFrame.addView(mOverlay, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        return mIconFrame;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int hs = MeasureSpec.makeMeasureSpec(mIconFrame.getMeasuredHeight(), MeasureSpec.EXACTLY);
        int ws = MeasureSpec.makeMeasureSpec(mIconFrame.getMeasuredHeight(), MeasureSpec.AT_MOST);
        mIn.measure(ws, hs);
        mOut.measure(ws, hs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        layoutIndicator(mIn);
        layoutIndicator(mOut);
    }

    private void layoutIndicator(View indicator) {
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int left, right;
        if (isRtl) {
            right = mIconFrame.getLeft();
            left = right - indicator.getMeasuredWidth();
        } else {
            left = mIconFrame.getRight();
            right = left + indicator.getMeasuredWidth();
        }
        indicator.layout(
                left,
                mIconFrame.getBottom() - indicator.getMeasuredHeight(),
                right,
                mIconFrame.getBottom());
    }

    @Override
    protected void handleStateChanged(QSTile.State state) {
        super.handleStateChanged(state);
        final SignalState s = (SignalState) state;
        setIcon(mSignal, s);
        if (s.overlayIconId > 0) {
            mOverlay.setVisibility(VISIBLE);
            mOverlay.setImageResource(s.overlayIconId);
        } else {
            mOverlay.setVisibility(GONE);
        }
        if (s.overlayIconId > 0 && s.isOverlayIconWide) {
            mSignal.setPaddingRelative(mWideOverlayIconStartPadding, 0, 0, 0);
        } else {
            mSignal.setPaddingRelative(0, 0, 0, 0);
        }
        Drawable drawable = mSignal.getDrawable();
        if (state.autoMirrorDrawable && drawable != null) {
            drawable.setAutoMirrored(true);
        }
        final boolean shown = isShown();
        setVisibility(mIn, shown, s.activityIn);
        setVisibility(mOut, shown, s.activityOut);
	updateIconColor();
    }

    private void setVisibility(View view, boolean shown, boolean visible) {
        final float newAlpha = shown && visible ? 1 : 0;
        if (view.getAlpha() == newAlpha) return;
        if (shown) {
            view.animate()
                .setDuration(visible ? SHORT_DURATION : DEFAULT_DURATION)
                .alpha(newAlpha)
                .start();
        } else {
            view.setAlpha(newAlpha);
        }
    }

	public void updateIconColor() {
        mQSColorSwitch = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_COLOR_SWITCH, 0) == 1;
        if (mQSColorSwitch) {
            mIconColor = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.QS_ICON_COLOR, 0xffffffff);
        	}
	}

      public void setIconColor() {
	updateIconColor();
        if (mQSColorSwitch) {
            mSignal.setColorFilter(mIconColor, Mode.MULTIPLY);
            mOverlay.setColorFilter(mIconColor, Mode.MULTIPLY);
            mIn.setColorFilter(mIconColor, Mode.MULTIPLY);
            mOut.setColorFilter(mIconColor, Mode.MULTIPLY);
       		 }
	}

	class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_COLOR_SWITCH),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
	   ContentResolver resolver = mContext.getContentResolver();
	   if (uri.equals(Settings.System.getUriFor(
                    Settings.System.QS_COLOR_SWITCH))) {
               setIconColor();
		} 
	        update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
                setIconColor();
        }
    }

}
