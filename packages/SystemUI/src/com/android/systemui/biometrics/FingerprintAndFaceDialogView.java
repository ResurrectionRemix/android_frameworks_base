/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.biometrics;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.TOP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;

import com.android.systemui.R;

/**
 * This class loads the view for the system-provided dialog. The view consists of:
 * Application Icon, Title, Subtitle, Description, Biometric Icon, Error/Help message area,
 * and positive/negative buttons.
 */
public class FingerprintAndFaceDialogView extends BiometricDialogView {

    private static final String TAG = "FingerprintAndFaceDialogView";
    private static final String KEY_DIALOG_ANIMATED_IN = "key_dialog_animated_in";

    private static final int HIDE_DIALOG_DELAY = 200; // ms

    private IconController mIconController;
    private ImageView mFaceIcon;
    private boolean mDialogAnimatedIn;

    /**
     * Class that handles the biometric icon animations.
     */
    private final class IconController extends Animatable2.AnimationCallback {

        private boolean mLastPulseDirection; // false = dark to light, true = light to dark

        int mState;

        IconController() {
            mState = STATE_IDLE;
        }

        public void animateOnce(int iconRes) {
            animateIcon(iconRes, false);
        }

        public void showStatic(int iconRes) {
            mFaceIcon.setImageDrawable(mContext.getDrawable(iconRes));
        }

        public void startPulsing() {
            mLastPulseDirection = false;
            animateIcon(R.drawable.face_dialog_pulse_dark_to_light, true);
        }

        public void showIcon(int iconRes) {
            final Drawable drawable = mContext.getDrawable(iconRes);
            mFaceIcon.setImageDrawable(drawable);
        }

        private void animateIcon(int iconRes, boolean repeat) {
            final AnimatedVectorDrawable icon =
                    (AnimatedVectorDrawable) mContext.getDrawable(iconRes);
            mFaceIcon.setImageDrawable(icon);
            icon.forceAnimationOnUI();
            if (repeat) {
                icon.registerAnimationCallback(this);
            }
            icon.start();
        }

        private void pulseInNextDirection() {
            int iconRes = mLastPulseDirection ? R.drawable.face_dialog_pulse_dark_to_light
                    : R.drawable.face_dialog_pulse_light_to_dark;
            animateIcon(iconRes, true /* repeat */);
            mLastPulseDirection = !mLastPulseDirection;
        }

        @Override
        public void onAnimationEnd(Drawable drawable) {
            super.onAnimationEnd(drawable);

            if (mState == STATE_AUTHENTICATING) {
                // Still authenticating, pulse the icon
                pulseInNextDirection();
            }
        }
    }

    private final Runnable mErrorToIdleAnimationRunnable = () -> {
        updateState(STATE_IDLE);
        announceAccessibilityEvent();
    };

    public FingerprintAndFaceDialogView(Context context,
            DialogViewCallback callback) {
        super(context, callback);

        mIconController = new IconController();
        mFaceIcon = new ImageView(context);
        final int iconDim = getResources().getDimensionPixelSize(
                R.dimen.biometric_dialog_biometric_icon_size);
        mFaceIcon.setVisibility(View.VISIBLE);
        mLayout.addView(mFaceIcon);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mFaceIcon.getLayoutParams();
        lp.gravity = TOP | CENTER_HORIZONTAL;
        lp.width = iconDim;
        lp.height = iconDim;
        lp.topMargin = iconDim;
    }

    @Override
    public void onSaveState(Bundle bundle) {
        super.onSaveState(bundle);
        bundle.putBoolean(KEY_DIALOG_ANIMATED_IN, mDialogAnimatedIn);
    }

    @Override
    protected void handleResetMessage() {
        mErrorText.setText(getHintStringResourceId());
        mErrorText.setTextColor(mTextColor);
        announceAccessibilityEvent();
    }

    @Override
    public void restoreState(Bundle bundle) {
        super.restoreState(bundle);
        // Keep in mind that this happens before onAttachedToWindow()
        mDialogAnimatedIn = bundle.getBoolean(KEY_DIALOG_ANIMATED_IN);
    }

    @Override
    public void onAuthenticationFailed(String message) {
        super.onAuthenticationFailed(message);
        showTryAgainButton(true);
    }

    @Override
    public void showTryAgainButton(boolean show) {
        if (show) {
            mTryAgainButton.setVisibility(View.VISIBLE);
            mPositiveButton.setVisibility(View.GONE);
            announceAccessibilityEvent();
        } else {
            mTryAgainButton.setVisibility(View.GONE);
            announceAccessibilityEvent();
        }
    }

    @Override
    protected int getHintStringResourceId() {
        return R.string.fingerprint_dialog_touch_sensor;
    }

    @Override
    protected int getAuthenticatedAccessibilityResourceId() {
        return com.android.internal.R.string.fingerprint_authenticated;
    }

    @Override
    protected int getIconDescriptionResourceId() {
        return R.string.accessibility_fingerprint_dialog_fingerprint_icon;
    }

    @Override
    protected void updateIcon(int oldState, int newState) {
        mIconController.mState = newState;

        if (newState == STATE_AUTHENTICATING) {
            mHandler.removeCallbacks(mErrorToIdleAnimationRunnable);
            if (mDialogAnimatedIn) {
                mIconController.startPulsing();
            } else {
                mIconController.showIcon(R.drawable.face_dialog_pulse_dark_to_light);
            }
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_authenticating));
        } else if (oldState == STATE_PENDING_CONFIRMATION && newState == STATE_AUTHENTICATED) {
            mIconController.animateOnce(R.drawable.face_dialog_dark_to_checkmark);
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_confirmed));
        } else if (oldState == STATE_ERROR && newState == STATE_IDLE) {
            mIconController.animateOnce(R.drawable.face_dialog_error_to_idle);
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_idle));
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATED) {
            mHandler.removeCallbacks(mErrorToIdleAnimationRunnable);
            mIconController.animateOnce(R.drawable.face_dialog_dark_to_checkmark);
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_authenticated));
        } else if (newState == STATE_ERROR) {
            // It's easier to only check newState and gate showing the animation on the
            // mErrorToIdleAnimationRunnable as a proxy, than add a ton of extra state. For example,
            // we may go from error -> error due to configuration change which is valid and we
            // should show the animation, or we can go from error -> error by receiving repeated
            // acquire messages in which case we do not want to repeatedly start the animation.
            if (!mHandler.hasCallbacks(mErrorToIdleAnimationRunnable)) {
                mIconController.animateOnce(R.drawable.face_dialog_dark_to_error);
                mHandler.postDelayed(mErrorToIdleAnimationRunnable,
                        BiometricPrompt.HIDE_DIALOG_DELAY);
            }
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_AUTHENTICATED) {
            mIconController.animateOnce(R.drawable.face_dialog_dark_to_checkmark);
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_authenticated));
        } else if (newState == STATE_PENDING_CONFIRMATION) {
            mHandler.removeCallbacks(mErrorToIdleAnimationRunnable);
            mIconController.animateOnce(R.drawable.face_dialog_wink_from_dark);
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_authenticated));
        } else if (newState == STATE_IDLE) {
            mIconController.showStatic(R.drawable.face_dialog_idle_static);
            mFaceIcon.setContentDescription(mContext.getString(
                    R.string.biometric_dialog_face_icon_description_idle));
        } else {
            Log.w(TAG, "Unknown animation from " + oldState + " -> " + newState);
        }

        final Drawable icon = mContext.getDrawable(R.drawable.fingerprint_dialog_fp_to_error);
        mBiometricIcon.setImageDrawable(icon);

        // Note that this must be after the newState == STATE_ERROR check above since this affects
        // the logic.
        if (oldState == STATE_ERROR && newState == STATE_ERROR) {
            // Keep the error icon and text around for a while longer if we keep receiving
            // STATE_ERROR
            mHandler.removeCallbacks(mErrorToIdleAnimationRunnable);
            mHandler.postDelayed(mErrorToIdleAnimationRunnable, BiometricPrompt.HIDE_DIALOG_DELAY);
        }
    }

    @Override
    public void onDialogAnimatedIn() {
        super.onDialogAnimatedIn();
        mDialogAnimatedIn = true;
        mIconController.startPulsing();
    }

    @Override
    protected int getDelayAfterAuthenticatedDurationMs() {
        return HIDE_DIALOG_DELAY;
    }

    @Override
    protected boolean shouldGrayAreaDismissDialog() {
        return true;
    }
}
