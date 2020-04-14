/*
 * Copyright (C) 2017-2018 I N F I N I T Y (Amal Das)
 * Copyright (C) 2019 The Android Open Source Project
 * Copyright (C) 2020 Bootleggers ROM
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
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class MNMLBoxClockController implements ClockPlugin {

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
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Root view of clock.
     */
    private ClockLayout mView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextView mClock;
    private TextView mDate;

    /**
     * Time and calendars to check the date
     */
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());
    private String mDescFormat;
    private TimeZone mTimeZone;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public MNMLBoxClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.digital_mnml_box, null);
        mClock = mView.findViewById(R.id.clock);
        mDate = mView.findViewById(R.id.bigDate);
        onTimeTick();
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mClock = null;
        mDate = null;
    }

    @Override
    public String getName() {
        return "mnml_box";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_mnml_box);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_bold_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = mLayoutInflater.inflate(R.layout.digital_mnml_box, null);
        TextView previewTime = previewView.findViewById(R.id.clock);
        TextView previewDate = previewView.findViewById(R.id.bigDate);

        // Initialize state of plugin before generating preview.
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        final int hour = mTime.get(Calendar.HOUR) % 12;
        // lazy and ugly workaround for the it's string
        String typeHeader = mResources.getQuantityText(
                R.plurals.type_clock_header, hour).toString();
        typeHeader = typeHeader.replaceAll("\\n", "") + " ";
        SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
        previewTime.setText(typeHeader.substring(0, typeHeader.indexOf("^")) + " " + timeformat.format(mTime.getInstance().getTimeInMillis()));
        DateFormat dateFormat = DateFormat.getInstanceForSkeleton("EEEEMMMMd", Locale.getDefault());
        dateFormat.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        previewDate.setText(dateFormat.format(mTime.getInstance().getTimeInMillis()));

        return mRenderer.createPreview(previewView, width, height);
    }

    @Override
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public View getBigClockView() {
        return null;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return CLOCK_USE_DEFAULT_Y;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        mClock.setTextColor(color);
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {}

    @Override
    public void onTimeTick() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        final int hour = mTime.get(Calendar.HOUR) % 12;
        // lazy and ugly workaround for the it's string
        String typeHeader = mResources.getQuantityText(
                R.plurals.type_clock_header, hour).toString();
        typeHeader = typeHeader.replaceAll("\\n", "");
        SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
        mClock.setText(typeHeader.substring(0, typeHeader.indexOf("^")) + " " + timeformat.format(mTime.getInstance().getTimeInMillis()));
        DateFormat dateFormat = DateFormat.getInstanceForSkeleton("EEEEMMMMd", Locale.getDefault());
        dateFormat.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        mDate.setText(dateFormat.format(mTime.getInstance().getTimeInMillis()));

    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mView.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }
}
