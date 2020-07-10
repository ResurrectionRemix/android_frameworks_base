/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tileimpl;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settingslib.Utils;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile;

import java.util.Objects;
import java.util.Random;

/** View that represents a standard quick settings tile. **/
public class QSTileView extends QSTileBaseView {
    private static final int MAX_LABEL_LINES = 2;
    private static final boolean DUAL_TARGET_ALLOWED = true;
    private View mDivider;
    protected TextView mLabel;
    protected TextView mSecondLine;
    private ImageView mPadLock;
    private int mState;
    private ViewGroup mLabelContainer;
    private ImageView mExpandIndicator;
    private View mExpandSpace;
    private ColorStateList mColorLabelDefault;
    private ColorStateList mColorLabelActive;
    private ColorStateList mColorLabelUnavailable;
    private int setQsUseNewTint;
    private boolean setinActivetTint;

    public QSTileView(Context context, QSIconView icon) {
        this(context, icon, false);
    }

    public QSTileView(Context context, QSIconView icon, boolean collapsedView) {
        super(context, icon, collapsedView);

        setClipChildren(false);
        setClipToPadding(false);

        setClickable(true);
        setId(View.generateViewId());
        createLabel();
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        updateTints();
        // The text color for unavailable tiles is textColorSecondary, same as secondaryLabel for
        // contrast purposes
        mColorLabelUnavailable = Utils.getColorAttr(getContext(),
                android.R.attr.textColorSecondary);
    }

    TextView getLabel() {
        return mLabel;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mLabel, R.dimen.qs_tile_text_size);
        FontSizeUtils.updateFontSize(mSecondLine, R.dimen.qs_tile_text_size);
    }

    @Override
    public int getDetailY() {
        return getTop() + mLabelContainer.getTop() + mLabelContainer.getHeight() / 2;
    }

    protected void createLabel() {
        mLabelContainer = (ViewGroup) LayoutInflater.from(getContext())
                .inflate(R.layout.qs_tile_label, this, false);
        mLabelContainer.setClipChildren(false);
        mLabelContainer.setClipToPadding(false);
        mLabel = mLabelContainer.findViewById(R.id.tile_label);
        mPadLock = mLabelContainer.findViewById(R.id.restricted_padlock);
        mDivider = mLabelContainer.findViewById(R.id.underline);
        mExpandIndicator = mLabelContainer.findViewById(R.id.expand_indicator);
        mExpandSpace = mLabelContainer.findViewById(R.id.expand_space);
        mSecondLine = mLabelContainer.findViewById(R.id.app_label);
        addView(mLabelContainer);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Remeasure view if the primary label requires more then 2 lines or the secondary label
        // text will be cut off.
        if (mLabel.getLineCount() > MAX_LABEL_LINES || !TextUtils.isEmpty(mSecondLine.getText())
                        && mSecondLine.getLineHeight() > mSecondLine.getHeight()) {
            mLabel.setSingleLine();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void handleStateChanged(QSTile.State state) {
        super.handleStateChanged(state);
        updateTints();
        if (!Objects.equals(mLabel.getText(), state.label) || mState != state.state) {
            mLabel.setTextColor(state.state == Tile.STATE_UNAVAILABLE ? mColorLabelUnavailable
                    : mColorLabelDefault);
            mState = state.state;
            mLabel.setText(state.label);
        }
        if (!Objects.equals(mSecondLine.getText(), state.secondaryLabel)) {

            if (state.state == Tile.STATE_ACTIVE) {
               if (setQsUseNewTint == 2) {
                   mSecondLine.setTextColor(randomColor());
               } else {
                   mSecondLine.setTextColor(mColorLabelActive);
               }
            } else if (state.state == Tile.STATE_INACTIVE) {
                   if (setinActivetTint) {
                       if (setQsUseNewTint == 0) {
                           mSecondLine.setTextColor(mColorLabelDefault);
                       } else if (setQsUseNewTint == 1) {
                           mSecondLine.setTextColor(mColorLabelActive);
                       } else if (setQsUseNewTint == 2) {
                          mSecondLine.setTextColor(randomColor());
                       }
                 } 
            }

            mSecondLine.setText(state.secondaryLabel);
            mSecondLine.setVisibility(TextUtils.isEmpty(state.secondaryLabel) ? View.GONE
                    : View.VISIBLE);
        }

        if (state.state == Tile.STATE_ACTIVE) {
            if (setQsUseNewTint == 2) {
                mLabel.setTextColor(randomColor());
                mExpandIndicator.setImageTintList(ColorStateList.valueOf(randomColor()));
            } else {
                mLabel.setTextColor(mColorLabelActive);
                mExpandIndicator.setImageTintList(mColorLabelActive);
            }
        } else if (state.state == Tile.STATE_INACTIVE) {
            if (setinActivetTint) {
                 if (setQsUseNewTint == 0) {
                    mLabel.setTextColor(mColorLabelDefault);
                    mExpandIndicator.setImageTintList(mColorLabelDefault);
                 } else if (setQsUseNewTint == 1) {
                     mLabel.setTextColor(mColorLabelActive);
                     mExpandIndicator.setImageTintList(mColorLabelActive);
                 } else if (setQsUseNewTint == 2) {
                     mLabel.setTextColor(randomColor());
                     mExpandIndicator.setImageTintList(ColorStateList.valueOf(randomColor()));
                 } 
            } else {
                mLabel.setTextColor(mColorLabelDefault);
                mExpandIndicator.setImageTintList(mColorLabelDefault);
            }
        }
        boolean dualTarget = DUAL_TARGET_ALLOWED && state.dualTarget;
        mExpandIndicator.setVisibility(View.GONE);
        mExpandSpace.setVisibility(View.GONE);
        mLabelContainer.setContentDescription(dualTarget ? state.dualLabelContentDescription
                : null);
        if (dualTarget != mLabelContainer.isClickable()) {
            mLabelContainer.setClickable(dualTarget);
            mLabelContainer.setLongClickable(dualTarget);
            mLabelContainer.setBackground(dualTarget ? newTileBackground() : null);
        }
        mLabel.setEnabled(!state.disabledByPolicy);
        mPadLock.setVisibility(state.disabledByPolicy ? View.VISIBLE : View.GONE);
    }

    @Override
    public void init(OnClickListener click, OnClickListener secondaryClick,
            OnLongClickListener longClick) {
        super.init(click, secondaryClick, longClick);
        mLabelContainer.setOnClickListener(secondaryClick);
        mLabelContainer.setOnLongClickListener(longClick);
        mLabelContainer.setClickable(false);
        mLabelContainer.setLongClickable(false);
    }

    @Override
    public void textVisibility() {
        if (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_TILE_TITLE_VISIBILITY, 1,
                UserHandle.USER_CURRENT) == 1) {
           mLabelContainer.setVisibility(View.VISIBLE);
        } else {
           mLabelContainer.setVisibility(View.GONE);
        }
    }

    public void setHideLabel(boolean value) {
        mLabelContainer.setVisibility(value ? View.GONE : View.VISIBLE);
    }

    private void updateTints() {
        setQsUseNewTint = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_LABEL_USE_NEW_TINT, 0, UserHandle.USER_CURRENT);
        setinActivetTint = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_LABEL_INACTIVE_TINT, 0, UserHandle.USER_CURRENT) == 1;
        mColorLabelDefault = Utils.getColorAttr(getContext(), android.R.attr.textColorPrimary);
        if (setQsUseNewTint == 0) {
            mColorLabelActive = mColorLabelDefault;
        } else if (setQsUseNewTint == 1) { 
            mColorLabelActive = Utils.getColorAttr(getContext(), android.R.attr.colorAccent);
        } 
    }

    public int randomColor() {
        Random r = new Random();
        float hsl[] = new float[3];
        hsl[0] = r.nextInt(360);
        hsl[1] = r.nextFloat();
        hsl[2] = (isThemeDark(getContext()) ? 0.575f : 0.3f) + (r.nextFloat() * 0.125f);
        return com.android.internal.graphics.ColorUtils.HSLToColor(hsl);
    }

    private static Boolean isThemeDark(Context context) {
        switch (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
              return true;
            case Configuration.UI_MODE_NIGHT_NO:
              return false;
            default:
              return false;
        }
    }

}
