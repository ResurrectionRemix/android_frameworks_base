/*
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

package com.android.systemui.statusbar.phone;

import android.animation.ArgbEvaluator;
import android.annotation.ColorInt;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class NavigationHandle extends View implements ButtonInterface {
    private float mDarkIntensity = -1;

    private final Paint mPaint = new Paint();
    private @ColorInt final int mLightColor;
    private @ColorInt final int mDarkColor;
    private final int mRadius;
    private final int mBottom;
    private int mWidth;

    private final Resources mRes;
    private final ContentResolver mResolver;
    private final String WIDTH_SETTING = "navigation_handle_width";

    public NavigationHandle(Context context) {
        this(context, null);
    }

    public NavigationHandle(Context context, AttributeSet attr) {
        super(context, attr);
        mRes = context.getResources();
        mResolver = context.getContentResolver();
        mRadius = mRes.getDimensionPixelSize(R.dimen.navigation_handle_radius);
        mBottom = mRes.getDimensionPixelSize(R.dimen.navigation_handle_bottom);

        final int dualToneDarkTheme = Utils.getThemeAttr(context, R.attr.darkIconTheme);
        final int dualToneLightTheme = Utils.getThemeAttr(context, R.attr.lightIconTheme);
        Context lightContext = new ContextThemeWrapper(context, dualToneLightTheme);
        Context darkContext = new ContextThemeWrapper(context, dualToneDarkTheme);
        mLightColor = Utils.getColorAttrDefaultColor(lightContext, R.attr.homeHandleColor);
        mDarkColor = Utils.getColorAttrDefaultColor(darkContext, R.attr.homeHandleColor);
        mPaint.setAntiAlias(true);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw that bar
        int navHeight = getHeight();
        int height = mRadius * 2;
        mWidth = (int) getCustomWidth();
        int y = (navHeight - mBottom - height);
        int padding = (int) getCustomPadding();
        canvas.drawRoundRect(padding, y, mWidth + padding, y + height, mRadius, mRadius, mPaint);
    }

    private double getCustomPadding() {
        int basePadding = (int) (getWidth() / 2) - (int) (mWidth / 2);
        return basePadding;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
    }

    @Override
    public void abortCurrentGesture() {
    }

    @Override
    public void setVertical(boolean vertical) {
    }

    @Override
    public void setDarkIntensity(float intensity) {
        if (mDarkIntensity != intensity) {
            mPaint.setColor((int) ArgbEvaluator.getInstance().evaluate(intensity, mLightColor,
                    mDarkColor));
            mDarkIntensity = intensity;
            invalidate();
        }
    }

    @Override
    public void setDelayTouchFeedback(boolean shouldDelay) {
    }

    private double getCustomWidth() {
        int baseWidth = mRes.getDimensionPixelSize(R.dimen.navigation_home_handle_width);
        /* 0: small (stock AOSP)
           1: medium
           2: long
        */
        int userSelection = Settings.System.getInt(mResolver, WIDTH_SETTING, 0);
        if (userSelection == 0) {
            return baseWidth;
        } else if (userSelection == 1) {
            return 1.33 * baseWidth;
        } else {
            return 2 * baseWidth;
        }
    }
}
