/**
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.biometrics;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.systemui.R;

public class FODAnimation extends ImageView {

    private final WindowManager.LayoutParams mAnimParams = new WindowManager.LayoutParams();

    private boolean mShowing = false;
    private Context mContext;
    private int mAnimationSize;
    private int mAnimationPositionY;
    private AnimationDrawable recognizingAnim;
    private WindowManager mWindowManager;
    private boolean mIsKeyguard;

    private int mSelectedAnim;
    private final int[] ANIMATION_STYLES = {
        R.drawable.fod_miui_normal_recognizing_anim,
        R.drawable.fod_miui_aod_recognizing_anim,
        R.drawable.fod_miui_light_recognizing_anim,
        R.drawable.fod_miui_pop_recognizing_anim,
        R.drawable.fod_miui_pulse_recognizing_anim,
        R.drawable.fod_miui_pulse_recognizing_white_anim,
        R.drawable.fod_miui_rhythm_recognizing_anim,
        R.drawable.fod_op_cosmos_recognizing_anim,
        R.drawable.fod_op_mclaren_recognizing_anim,
        R.drawable.fod_op_stripe_recognizing_anim,
        R.drawable.fod_op_wave_recognizing_anim,
        R.drawable.fod_pureview_dna_recognizing_anim,
        R.drawable.fod_pureview_future_recognizing_anim,
        R.drawable.fod_pureview_halo_ring_recognizing_anim,
        R.drawable.fod_pureview_molecular_recognizing_anim
    };

    public FODAnimation(Context context, int mPositionX, int mPositionY) {
        super(context);

        mContext = context;
        mWindowManager = mContext.getSystemService(WindowManager.class);

        mAnimationSize = mContext.getResources().getDimensionPixelSize(R.dimen.fod_animation_size);
        mAnimParams.height = mAnimationSize;
        mAnimParams.width = mAnimationSize;

        mAnimParams.format = PixelFormat.TRANSLUCENT;
        mAnimParams.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY; // it must be behind FOD icon
        mAnimParams.flags =  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mAnimParams.gravity = Gravity.TOP | Gravity.CENTER;
        mAnimParams.y = mPositionY - (mAnimationSize / 2);

        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        update();
    }

    public void update() {
        mSelectedAnim = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ANIM, 0);

        setBackgroundResource(ANIMATION_STYLES[mSelectedAnim]);
        recognizingAnim = (AnimationDrawable) getBackground();
    }

    public void updateParams(int mDreamingOffsetY) {
        mAnimParams.y = mDreamingOffsetY - (mAnimationSize / 2);
    }

    public void setAnimationKeyguard(boolean state) {
        mIsKeyguard = state;
    }

    public void showFODanimation() {
        if (mAnimParams != null && !mShowing && mIsKeyguard) {
            mShowing = true;
            mWindowManager.addView(this, mAnimParams);
            mWindowManager.updateViewLayout(this, mAnimParams);
            recognizingAnim.start();
        }
    }

    public void hideFODanimation() {
        if (mShowing) {
            mShowing = false;
            if (recognizingAnim != null) {
                clearAnimation();
                recognizingAnim.stop();
                recognizingAnim.selectDrawable(0);
            }
            if (this.getWindowToken() != null) {
                mWindowManager.removeView(this);
            }
        }
    }
}
