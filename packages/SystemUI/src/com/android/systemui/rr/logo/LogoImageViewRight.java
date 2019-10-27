/*
 * Copyright (C) 2018 crDroid Android Project
 * Copyright (C) 2018 AICP
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

package com.android.systemui.rr.logo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;

import com.android.systemui.tuner.TunerService;

public class LogoImageViewRight extends ImageView implements
        TunerService.Tunable {

    private Context mContext;

    private boolean mAttached;
    private boolean mLogo;
    private int mLogoColor;
    private boolean mLogoColorAccent;
    private int mLogoPosition;
    private int mLogoStyle;
    private int mTintColor = Color.WHITE;

    private static final String STATUS_BAR_LOGO =
            "system:" + Settings.System.STATUS_BAR_LOGO;
    private static final String STATUS_BAR_LOGO_COLOR =
            "system:" + Settings.System.STATUS_BAR_LOGO_COLOR;
    private static final String STATUS_BAR_LOGO_COLOR_ACCENT =
            "system:" + Settings.System.STATUS_BAR_LOGO_COLOR_ACCENT;
    private static final String STATUS_BAR_LOGO_POSITION =
            "system:" + Settings.System.STATUS_BAR_LOGO_POSITION;
    private static final String STATUS_BAR_LOGO_STYLE =
            "system:" + Settings.System.STATUS_BAR_LOGO_STYLE;

    public LogoImageViewRight(Context context) {
        this(context, null);
    }

    public LogoImageViewRight(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoImageViewRight(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        mAttached = true;

        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);

        Dependency.get(TunerService.class).addTunable(this,
                STATUS_BAR_LOGO,
                STATUS_BAR_LOGO_COLOR,
                STATUS_BAR_LOGO_COLOR_ACCENT,
                STATUS_BAR_LOGO_POSITION,
                STATUS_BAR_LOGO_STYLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached)
            return;

        mAttached = false;
        Dependency.get(TunerService.class).removeTunable(this);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mLogo && (mLogoPosition == 1 || mLogoPosition == 3) &&
                mLogoColor == 0xFFFFFFFF) {
            updateLogo();
        }
    }

    public void updateLogo() {
        Drawable drawable = null;

	if (!mLogo || (mLogoPosition == 0 || mLogoPosition == 2)) {
            setImageDrawable(null);
            setVisibility(View.GONE);
            return;
        } else {
            setVisibility(View.VISIBLE);
        }

        if (mLogoStyle == 0) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_rr_logo);
        } else if (mLogoStyle == 1) {
           drawable = mContext.getResources().getDrawable(R.drawable.ic_android_logo);
        } else if (mLogoStyle == 2) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_apple_logo);
        } else if (mLogoStyle == 3) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ios_logo);
        } else if (mLogoStyle == 4) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon);
        } else if (mLogoStyle == 5) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_cool);
        } else if (mLogoStyle == 6) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_dead);
        } else if (mLogoStyle == 7) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_devil);
        } else if (mLogoStyle == 8) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_happy);
        } else if (mLogoStyle == 9) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_neutral);
        } else if (mLogoStyle == 10) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_poop);
        } else if (mLogoStyle == 11) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_sad);
        } else if (mLogoStyle == 12) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_tongue);
        } else if (mLogoStyle == 13) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_blackberry);
        } else if (mLogoStyle == 14) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_cake);
        } else if (mLogoStyle == 15) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_blogger);
        } else if (mLogoStyle == 16) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_biohazard);
        } else if (mLogoStyle == 17) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_linux);
        } else if (mLogoStyle == 18) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_yin_yang);
        } else if (mLogoStyle == 19) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_windows);
        } else if (mLogoStyle == 20) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_robot);
        } else if (mLogoStyle == 21) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ninja);
        } else if (mLogoStyle == 22) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_heart);
        } else if (mLogoStyle == 23) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_flower);
        } else if (mLogoStyle == 24) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ghost);
        } else if (mLogoStyle == 25) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_google);
        } else if (mLogoStyle == 26) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_human_male);
        } else if (mLogoStyle == 27) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_human_female);
        } else if (mLogoStyle == 28) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_human_male_female);
        } else if (mLogoStyle == 29) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_gender_male);
        } else if (mLogoStyle == 30) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_gender_female);
        } else if (mLogoStyle == 31) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_gender_male_female);
        } else if (mLogoStyle == 32) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_guitar_electric);
        }

        setImageDrawable(null);

        clearColorFilter();

        if (mLogoColorAccent == true) {
            setColorFilter(Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorAccent),PorterDuff.Mode.SRC_IN);
        } else {
            if (mLogoColor == 0xFFFFFFFF) {
                drawable.setTint(mTintColor);
            } else {
                setColorFilter(mLogoColor, PorterDuff.Mode.SRC_IN);
            }
        }
        setImageDrawable(drawable);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        Drawable drawable = null;
        switch (key) {
            case STATUS_BAR_LOGO:
                mLogo = newValue != null && Integer.parseInt(newValue) == 1;
                break;
            case STATUS_BAR_LOGO_COLOR:
                mLogoColor =
                        newValue == null ? 0xFFFFFFFF : Integer.parseInt(newValue);
                break;
            case STATUS_BAR_LOGO_COLOR_ACCENT:
                mLogoColorAccent = !"0".equals(newValue) ? true : false;
                break;
            case STATUS_BAR_LOGO_POSITION:
                mLogoPosition =
                        newValue == null ? 0 : Integer.parseInt(newValue);
                break;
            case STATUS_BAR_LOGO_STYLE:
                mLogoStyle =
                        newValue == null ? 0 : Integer.parseInt(newValue);
                break;
            default:
                break;
        }
        updateLogo();
    }
}
