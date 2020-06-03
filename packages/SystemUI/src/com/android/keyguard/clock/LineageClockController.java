/*
 * Copyright (C) 2020 The LineageOS Project
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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint.Style;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;

import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.util.TimeZone;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class LineageClockController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Root view of clock.
     */
    private ClockLayout mBigClockView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mClock;

    /**
     * Controller for transition into dark state.
     */
    private CrossFadeDarkController mDarkController;

    /**
     * Helper to extract colors from wallpaper palette for clock face.
     */
    private final ClockPalette mPalette = new ClockPalette();

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public LineageClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
    }

    private void createViews() {
        mBigClockView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.lineage_clock, null);
        mClock = (TextClock) mBigClockView.findViewById(R.id.clock);
        mClock.setFormat12Hour("hh\nmm");
        mClock.setFormat24Hour("kk\nmm");
    }

    @Override
    public void onDestroyView() {
        mBigClockView = null;
        mClock = null;
    }

    @Override
    public String getName() {
        return "lineageos";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_lineage);
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return totalHeight / 3;
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.lineage_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = getView();
        TextClock clock = previewView.findViewById(R.id.clock);
        clock.setFormat12Hour("hh\nmm");
        clock.setFormat24Hour("kk\nmm");
        onTimeTick();
        return mRenderer.createPreview(previewView, width, height);
    }

    @Override
    public View getView() {
        return null;
    }

    @Override
    public View getBigClockView() {
        if (mBigClockView  == null) {
            createViews();
        }
        return mBigClockView;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        updateColor();
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        mPalette.setColorPalette(supportsDarkText, colorPalette);
        updateColor();
    }

    private void updateColor() {
        final int primary = mPalette.getPrimaryColor();
        final int secondary = mPalette.getSecondaryColor();
    }

    @Override
    public void onTimeTick() {
        mBigClockView.onTimeChanged();
        mClock.refresh();
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        if (mDarkController != null) {
            mBigClockView.setDarkAmount(darkAmount);
        }
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return true;
    }
}
