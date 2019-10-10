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
package com.android.systemui.rr;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class NotificationLightsView extends RelativeLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";
    private View mNotificationAnimView;
    private ValueAnimator mLightAnimator;
    private boolean mPulsing;
    private boolean mNotification;

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
    }

    private Runnable mLightUpdate = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "run");
            animateNotification(mNotification);
        }
    };

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (DEBUG) Log.d(TAG, "draw");
        //post(mLightUpdate);
    }

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
    }

    public void animateNotification(boolean mNotification) {
        int defaultColor = Color.parseColor("#3980FF");
        boolean useAccent = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_NOTIFICATION_LIGHT_ACCENT,
                0, UserHandle.USER_CURRENT) != 0;
        int color = useAccent ?
                Utils.getColorAccentDefaultColor(getContext()) : defaultColor;
        StringBuilder sb = new StringBuilder();
        sb.append("animateNotification color ");
        sb.append(Integer.toHexString(color));
        if (DEBUG) Log.d(TAG, sb.toString());
        ImageView leftView = (ImageView) findViewById(R.id.notification_animation_left);
        ImageView rightView = (ImageView) findViewById(R.id.notification_animation_right);
        leftView.setColorFilter(color);
        rightView.setColorFilter(color);
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(2000);
        //Infinite animation only on Always On Notifications
        if (mNotification) {
            mLightAnimator.setRepeatCount(ValueAnimator.INFINITE);
        }
        mLightAnimator.setRepeatMode(ValueAnimator.RESTART);
        mLightAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (DEBUG) Log.d(TAG, "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftView.setScaleY(progress);
                rightView.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftView.setAlpha(alpha);
                rightView.setAlpha(alpha);
            }
        });
        if (DEBUG) Log.d(TAG, "start");
        mLightAnimator.start();
    }

}
