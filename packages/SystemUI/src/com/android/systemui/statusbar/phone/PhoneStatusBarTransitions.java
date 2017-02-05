/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.view.View;

import com.android.systemui.R;

public final class PhoneStatusBarTransitions extends BarTransitions {
    private static final float ICON_ALPHA_WHEN_NOT_OPAQUE = 1;
    private static final float ICON_ALPHA_WHEN_LIGHTS_OUT_BATTERY_CLOCK = 0.5f;
    private static final float ICON_ALPHA_WHEN_LIGHTS_OUT_NON_BATTERY_CLOCK = 0;

    private final PhoneStatusBarView mView;
    private final float mIconAlphaWhenOpaque;

    private View mLeftSide, mStatusIcons, mSignalCluster, mBattery, mClock,mNetworkTraffic, mRRLogo,mRRLogoRight,mRRLogoLeft,mClogo,mClogoLeft,mClogoRight,mWeatherLeft,mWeatherRight, mMinitBattery;

    private Animator mCurrentAnimation;

    public PhoneStatusBarTransitions(PhoneStatusBarView view) {
        super(view, R.drawable.status_background);
        mView = view;
        final Resources res = mView.getContext().getResources();
        mIconAlphaWhenOpaque = res.getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
    }

    public void init() {
        mLeftSide = mView.findViewById(R.id.notification_icon_area);
        mStatusIcons = mView.findViewById(R.id.statusIcons);
        mSignalCluster = mView.findViewById(R.id.signal_cluster);
        mBattery = mView.findViewById(R.id.battery);
        mClock = mView.findViewById(R.id.clock);
        mNetworkTraffic = mView.findViewById(R.id.networkTraffic);
        mRRLogo = mView.findViewById(R.id.rr_logo);
        mRRLogoRight = mView.findViewById(R.id.rr_logo_right);
        mRRLogoLeft = mView.findViewById(R.id.rr_logo_left);
        mClogo = mView.findViewById(R.id.custom_center);
        mClogoRight = mView.findViewById(R.id.custom_right);
        mClogoLeft = mView.findViewById(R.id.custom_left);
        mWeatherLeft = mView.findViewById(R.id.left_weather_temp);
        mWeatherRight = mView.findViewById(R.id.weather_temp);
        mMinitBattery = mView.findViewById(R.id.minitBattery);
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyMode(getMode(), false /*animate*/);
    }

    public ObjectAnimator animateTransitionTo(View v, float toAlpha) {
        return ObjectAnimator.ofFloat(v, "alpha", v.getAlpha(), toAlpha);
    }

    private float getNonBatteryClockAlphaFor(int mode) {
        return isLightsOut(mode) ? ICON_ALPHA_WHEN_LIGHTS_OUT_NON_BATTERY_CLOCK
                : !isOpaque(mode) ? ICON_ALPHA_WHEN_NOT_OPAQUE
                : mIconAlphaWhenOpaque;
    }

    private float getBatteryClockAlpha(int mode) {
        return isLightsOut(mode) ? ICON_ALPHA_WHEN_LIGHTS_OUT_BATTERY_CLOCK
                : getNonBatteryClockAlphaFor(mode);
    }

    private boolean isOpaque(int mode) {
        return !(mode == MODE_SEMI_TRANSPARENT || mode == MODE_TRANSLUCENT
                || mode == MODE_TRANSPARENT || mode == MODE_LIGHTS_OUT_TRANSPARENT);
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyMode(newMode, animate);
    }

    private void applyMode(int mode, boolean animate) {
        if (mLeftSide == null) return; // pre-init
        float newAlpha = getNonBatteryClockAlphaFor(mode);
        float newAlphaBC = getBatteryClockAlpha(mode);
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
        }
        if (animate) {
            AnimatorSet anims = new AnimatorSet();
            anims.playTogether(
                    animateTransitionTo(mLeftSide, newAlpha),
                    animateTransitionTo(mStatusIcons, newAlpha),
                    animateTransitionTo(mSignalCluster, newAlpha),
                    animateTransitionTo(mNetworkTraffic, newAlpha),
                    animateTransitionTo(mBattery, newAlphaBC),
                    animateTransitionTo(mClock, newAlphaBC),
                    animateTransitionTo(mRRLogo, newAlphaBC),
                    animateTransitionTo(mRRLogoRight, newAlphaBC),
                    animateTransitionTo(mRRLogoLeft, newAlphaBC),
                    animateTransitionTo(mClogoRight, newAlphaBC),
                    animateTransitionTo(mClogo, newAlphaBC),
                    animateTransitionTo(mClogoLeft, newAlphaBC),
                    animateTransitionTo(mWeatherLeft, newAlphaBC),
                    animateTransitionTo(mWeatherRight, newAlphaBC),
                    animateTransitionTo(mMinitBattery, newAlphaBC)
                    );
            if (isLightsOut(mode)) {
                anims.setDuration(LIGHTS_OUT_DURATION);
            }
            anims.start();
            mCurrentAnimation = anims;
        } else {
            mLeftSide.setAlpha(newAlpha);
            mStatusIcons.setAlpha(newAlpha);
            mSignalCluster.setAlpha(newAlpha);
            mNetworkTraffic.setAlpha(newAlpha);
            mBattery.setAlpha(newAlphaBC);
            mClock.setAlpha(newAlphaBC);
            mRRLogo.setAlpha(newAlphaBC);
            mRRLogoRight.setAlpha(newAlphaBC);
            mRRLogoLeft.setAlpha(newAlphaBC);
            mWeatherLeft.setAlpha(newAlphaBC);
            mClogo.setAlpha(newAlphaBC);
            mClogoRight.setAlpha(newAlphaBC);
            mClogoLeft.setAlpha(newAlphaBC);
            mWeatherRight.setAlpha(newAlphaBC);
            mMinitBattery.setAlpha(newAlphaBC);
        }
    }
}
