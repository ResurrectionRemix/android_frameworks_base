/*
 *  Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.systemui.statusbar.phone;

import com.android.systemui.R;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;


class QuickSettingsBasicBackTile extends QuickSettingsTileView {
    private final TextView mLabelView;
    private final TextView mFunctionView;
    private final ImageView mImageView;

    public QuickSettingsBasicBackTile(Context context) {
        this(context, null);
    }

    public QuickSettingsBasicBackTile(Context context, AttributeSet attrs) {
        super(context, attrs);

        setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            context.getResources().getDimensionPixelSize(R.dimen.quick_settings_cell_height)
        ));
        setBackgroundResource(R.drawable.qs_tile_background);
        addView(LayoutInflater.from(context).inflate(
                R.layout.quick_settings_tile_back, null),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        mLabelView = (TextView) findViewById(R.id.label);
        mFunctionView = (TextView) findViewById(R.id.function);
        mImageView = (ImageView) findViewById(R.id.image);
    }

    @Override
    void setContent(int layoutId, LayoutInflater inflater) {
        throw new RuntimeException("why?");
    }

    public ImageView getImageView() {
        return mImageView;
    }

    public TextView getLabelView() {
        return mLabelView;
    }

    public TextView getFunctionView() {
        return mFunctionView;
    }

    public void setImageDrawable(Drawable drawable) {
        mImageView.setImageDrawable(drawable);
    }

    public void setImageResource(int resId) {
        mImageView.setImageResource(resId);
    }

    public void setLabel(CharSequence text) {
        mLabelView.setText(text);
    }

    public void setLabelResource(int resId) {
        mLabelView.setText(resId);
    }

    public void setFunction(CharSequence text) {
        mFunctionView.setText(text);
    }

    public void setFunctionResource(int resId) {
        mFunctionView.setText(resId);
    }
}
