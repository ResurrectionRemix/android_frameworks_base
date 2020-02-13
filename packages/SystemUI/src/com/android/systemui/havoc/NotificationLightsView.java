/*
* Copyright (C) 2019 The OmniROM Project
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
package com.android.systemui.havoc;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class NotificationLightsView extends RelativeLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";

    private ValueAnimator mLightAnimator;
    private ImageView mLeftView;
    private ImageView mRightView;
    private AnimatorUpdateListener mAnimatorUpdateListener = new AnimatorUpdateListener() {
        public void onAnimationUpdate(ValueAnimator animation) {
            if (DEBUG) Log.d(TAG, "onAnimationUpdate");
            float progress = ((Float) animation.getAnimatedValue()).floatValue();
            mLeftView.setScaleY(progress);
            mRightView.setScaleY(progress);
            float alpha = 1.0f;
            if (progress <= 0.3f) {
                alpha = progress / 0.3f;
            } else if (progress >= 1.0f) {
                alpha = 2.0f - progress;
            }
            mLeftView.setAlpha(alpha);
            mRightView.setAlpha(alpha);
        }
    };

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (DEBUG) Log.d(TAG, "new");
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(2000);
        mLightAnimator.setRepeatMode(ValueAnimator.RESTART);
    }

    public void animateNotification() {
        animateNotificationWithColor(getNotificationLightsColor());
    }

    public int getNotificationLightsColor() {
        int color = getDefaultNotificationLightsColor();
        boolean useAccent = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.AMBIENT_NOTIFICATION_LIGHT_AUTOMATIC, 1, UserHandle.USER_CURRENT) == 0;
        if (useAccent) {
            color = Utils.getColorAccentDefaultColor(getContext());
        }
        return color;
    }

    public int getDefaultNotificationLightsColor() {
        return getResources().getInteger(com.android.internal.R.integer.config_AmbientPulseLightColor);
    }

    public void endAnimation() {
        mLightAnimator.end();
        mLightAnimator.removeAllUpdateListeners();
    }

    public void animateNotificationWithColor(int color) {
        if (mLeftView == null) {
            mLeftView = (ImageView) findViewById(R.id.notification_animation_left);
        }
        if (mRightView == null) {
            mRightView = (ImageView) findViewById(R.id.notification_animation_right);
        }
        mLeftView.setColorFilter(color);
        mRightView.setColorFilter(color);
        if (!mLightAnimator.isRunning()) {
            if (DEBUG) Log.d(TAG, "start");
            mLightAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mLightAnimator.addUpdateListener(mAnimatorUpdateListener);
            mLightAnimator.start();
        }
    }

}
