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
package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.view.LayoutInflater;
import android.view.View;
import android.util.MathUtils;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.util.TimeZone;

/**
 * Plugin for a custom Typographic clock face that displays the time in words.
 */
public class TypeClockAccentController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Computes preferred position of clock.
     */
    private float mDarkAmount;
    private final int mStatusBarHeight;
    private final int mKeyguardLockPadding;
    private final int mKeyguardLockHeight;
    private final int mBurnInOffsetY;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Custom clock shown on AOD screen and behind stack scroller on lock.
     */
    private View mView;
    private TypographicClock mTypeClock;
    private TypographicClock mBigClockView;
    private int mAccentColor;

    /**
     * Controller for transition into dark state.
     */
    private CrossFadeDarkController mDarkController;

    /**
     * Create a TypeClockAccentController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    TypeClockAccentController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mStatusBarHeight = res.getDimensionPixelSize(R.dimen.status_bar_height);
        mKeyguardLockPadding = res.getDimensionPixelSize(R.dimen.keyguard_lock_padding);
        mKeyguardLockHeight = res.getDimensionPixelSize(R.dimen.keyguard_lock_height);
        mBurnInOffsetY = res.getDimensionPixelSize(R.dimen.burn_in_prevention_offset_y);
    }

    private void createViews() {
        mView = mLayoutInflater.inflate(R.layout.type_aod_clock, null);

        mBigClockView  = (TypographicClock) mLayoutInflater.inflate(R.layout.typographic_clock, null);
        mTypeClock = mBigClockView.findViewById(R.id.type_clock);
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mBigClockView = null;
        mTypeClock = null;
        mDarkController = null;
    }

    @Override
    public String getName() {
        return "type";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_type_accent);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.type_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        // Use the big clock view for the preview
        View view = getBigClockView();
        mAccentColor = mResources.getColor(R.color.accent_device_default_light);
        // Initialize state of plugin before generating preview.
        setDarkAmount(1f);
        setTextColor(Color.WHITE);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        onTimeTick();
        return mRenderer.createPreview(view, width, height);
    }

    @Override
    public View getView() {
        if (mBigClockView == null) {
            createViews();
        }
        return mBigClockView;
    }

    @Override
    public View getBigClockView() {
        if (mBigClockView  == null) {
            createViews();
        }
        return mBigClockView ;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public int getPreferredY(int totalHeight) {
        // On AOD, clock needs to appear below the status bar with enough room for pixel shifting
        int aodY = mStatusBarHeight + mKeyguardLockHeight + 2 * mKeyguardLockPadding
                + mBurnInOffsetY + mTypeClock.getHeight() + (mTypeClock.getHeight() / 5);
        // On lock screen, clock needs to appear below the lock icon
        int lockY =  mStatusBarHeight + mKeyguardLockHeight + 2 * mKeyguardLockPadding + (mTypeClock.getHeight() / 2);
        return (int) MathUtils.lerp(lockY, aodY, mDarkAmount);
    }


    @Override
    public void setTextColor(int color) {
        mTypeClock.setTextColor(color);
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        if (colorPalette == null || colorPalette.length == 0) {
            return;
        }
        mAccentColor = mResources.getColor(R.color.accent_device_default_light);
        mTypeClock.setClockColor(mAccentColor);
    }

    @Override
    public void onTimeTick() {
        mTypeClock.onTimeChanged();
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        if (mDarkController != null) {
            mDarkController.setDarkAmount(darkAmount);
        }
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {
        mTypeClock.onTimeZoneChanged(timeZone);
    }

    @Override
    public boolean shouldShowStatusArea() {
        return true;
    }
}
