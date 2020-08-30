/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.keyguard;

import static android.app.slice.Slice.HINT_LIST_ITEM;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.ColorInt;
import android.annotation.StyleRes;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.SliceViewManager;
import androidx.slice.core.SliceQuery;
import androidx.slice.widget.ListContent;
import androidx.slice.widget.RowContent;
import androidx.slice.widget.SliceContent;
import androidx.slice.widget.SliceLiveData;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.graphics.ColorUtils;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;
import android.view.Gravity;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View visible under the clock on the lock screen and AoD.
 */
public class KeyguardSliceView extends LinearLayout implements View.OnClickListener,
        Observer<Slice>, TunerService.Tunable, ConfigurationController.ConfigurationListener {

    private static final String TAG = "KeyguardSliceView";
    public static final int DEFAULT_ANIM_DURATION = 550;
    private static final String KEYGUARD_TRANSISITION_ANIMATIONS = "sysui_keyguard_transition_animations";
    private static final String LOCKDATE_FONT_SIZE =
            "system:" + Settings.System.LOCKDATE_FONT_SIZE;
    private static final String LOCK_DATE_ALIGNMENT =
            "system:" + Settings.System.LOCK_DATE_ALIGNMENT;
    private static final String LOCKSCREEN_ITEM_PADDING =
            "system:" + Settings.System.LOCKSCREEN_ITEM_PADDING;

    private final HashMap<View, PendingIntent> mClickActions;
    private final ActivityStarter mActivityStarter;
    private final ConfigurationController mConfigurationController;
    private final LayoutTransition mLayoutTransition;
    private Uri mKeyguardSliceUri;
    @VisibleForTesting
    TextView mTitle;
    TextView mSubTitle;
    private Row mRow;
    private int mTextColor;
    private float mDarkAmount = 0;
    private RelativeLayout mRowContainer;
    private LiveData<Slice> mLiveData;
    private int mDisplayId = INVALID_DISPLAY;
    private int mIconSize;
    private int mIconSizeWithHeader;
    private int mWeatherIconSize;
    private int mDateSize;
    private int mLockDateAlignment;
    private int mItemPadding;
    /**
     * Runnable called whenever the view contents change.
     */
    private Runnable mContentChangeListener;
    private Slice mSlice;
    private boolean mHasHeader;
    private final int mRowWithHeaderPadding;
    private final int mRowPadding;
    private int mRowTextSize;
    private float mRowWithHeaderTextSize;
    private float mHeaderTextSize;

    private static boolean mKeyguardTransitionAnimations = true;

    @Inject
    public KeyguardSliceView(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            ActivityStarter activityStarter, ConfigurationController configurationController) {
        super(context, attrs);

        TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, Settings.Secure.KEYGUARD_SLICE_URI);
        tunerService.addTunable(this, KEYGUARD_TRANSISITION_ANIMATIONS);
        tunerService.addTunable(this, LOCK_DATE_ALIGNMENT);
        tunerService.addTunable(this, LOCKDATE_FONT_SIZE);
        tunerService.addTunable(this, LOCKSCREEN_ITEM_PADDING);

        mClickActions = new HashMap<>();
        mRowPadding = context.getResources().getDimensionPixelSize(R.dimen.subtitle_clock_padding);
        mRowWithHeaderPadding = context.getResources()
                .getDimensionPixelSize(R.dimen.header_subtitle_padding);
        mActivityStarter = activityStarter;
        mConfigurationController = configurationController;

        mLayoutTransition = new LayoutTransition();
        mLayoutTransition.setStagger(LayoutTransition.CHANGE_APPEARING, DEFAULT_ANIM_DURATION / 2);
        mLayoutTransition.setDuration(LayoutTransition.APPEARING, DEFAULT_ANIM_DURATION);
        mLayoutTransition.setDuration(LayoutTransition.DISAPPEARING, DEFAULT_ANIM_DURATION / 2);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        mLayoutTransition.setInterpolator(LayoutTransition.APPEARING,
                Interpolators.FAST_OUT_SLOW_IN);
        mLayoutTransition.setInterpolator(LayoutTransition.DISAPPEARING, Interpolators.ALPHA_OUT);
        mLayoutTransition.setAnimateParentHierarchy(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitle = findViewById(R.id.title);
        mRowContainer = findViewById(R.id.row_maincenter);
        mSubTitle = findViewById(R.id.subTitle);
        mRow = findViewById(R.id.row);
        mTextColor = Utils.getColorAttrDefaultColor(mContext, R.attr.wallpaperTextColor);
        mIconSize = mRowTextSize;
        mIconSizeWithHeader = (int) mContext.getResources().getDimension(R.dimen.header_icon_size);
        mRowTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.lock_date_font_size_18);
        mRowWithHeaderTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.header_row_font_size);
        mHeaderTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.header_font_size);
        mTitle.setOnClickListener(this);
        updateItemPadding();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mDisplayId = getDisplay().getDisplayId();
        // Make sure we always have the most current slice
        mLiveData.observeForever(this);
        mConfigurationController.addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // TODO(b/117344873) Remove below work around after this issue be fixed.
        if (mDisplayId == DEFAULT_DISPLAY) {
            mLiveData.removeObserver(this);
        }
        mConfigurationController.removeCallback(this);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        setLayoutTransition((isVisible && mKeyguardTransitionAnimations) ? mLayoutTransition : null);
    }
    /**
     * Returns whether the current visible slice has a title/header.
     */
    public boolean hasHeader() {
        return mHasHeader;
    }

    private void showSlice() {
        Trace.beginSection("KeyguardSliceView#showSlice");
        if (mSlice == null) {
            mTitle.setVisibility(GONE);
            mRow.setVisibility(GONE);
            mHasHeader = false;
            if (mContentChangeListener != null) {
                mContentChangeListener.run();
            }
            Trace.endSection();
            return;
        }
        mClickActions.clear();

        ListContent lc = new ListContent(getContext(), mSlice);
        SliceContent headerContent = lc.getHeader();
        mHasHeader = headerContent != null && !headerContent.getSliceItem().hasHint(HINT_LIST_ITEM);
        List<SliceContent> subItems = new ArrayList<>();
        for (int i = 0; i < lc.getRowItems().size(); i++) {
            SliceContent subItem = lc.getRowItems().get(i);
            String itemUri = subItem.getSliceItem().getSlice().getUri().toString();
            // Filter out the action row
            if (!KeyguardSliceProvider.KEYGUARD_ACTION_URI.equals(itemUri)) {
                subItems.add(subItem);
            }
        }
        if (!mHasHeader) {
            mTitle.setVisibility(GONE);
            mSubTitle.setVisibility(GONE);
        } else {
            mTitle.setVisibility(VISIBLE);

            RowContent header = lc.getHeader();
            SliceItem mainTitle = header.getTitleItem();
            CharSequence title = mainTitle != null ? mainTitle.getText() : null;
            mTitle.setText(title);
            mTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHeaderTextSize);

            SliceItem subTitle = header.getSubtitleItem();
            if (subTitle != null) {
                mSubTitle.setVisibility(VISIBLE);
                CharSequence subTitleText = subTitle.getText();
                mSubTitle.setText(subTitleText);
                mSubTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, mRowWithHeaderTextSize);
            }

            if (header.getPrimaryAction() != null
                    && header.getPrimaryAction().getAction() != null) {
                mClickActions.put(mTitle, header.getPrimaryAction().getAction());
            }
        }

        final int subItemsCount = subItems.size();
        final int blendedColor = getTextColor();
        final int startIndex = mHasHeader ? 1 : 0; // First item is header; skip it
        mRow.setVisibility(subItemsCount > 0 ? VISIBLE : GONE);

        for (int i = startIndex; i < subItemsCount; i++) {
            RowContent rc = (RowContent) subItems.get(i);
            SliceItem item = rc.getSliceItem();
            final Uri itemTag = item.getSlice().getUri();
            final boolean isWeatherSlice = itemTag.toString().equals(KeyguardSliceProvider.KEYGUARD_WEATHER_URI);
            // Try to reuse the view if already exists in the layout
            KeyguardSliceButton button = mRow.findViewWithTag(itemTag);
            if (button == null) {
                button = new KeyguardSliceButton(mContext);
                button.setShouldTintDrawable(!isWeatherSlice);
                button.setTextColor(blendedColor);
                button.setTag(itemTag);
                final int viewIndex = i - (mHasHeader ? 1 : 0);
                mRow.addView(button, viewIndex);
            } else {
                button.setShouldTintDrawable(!isWeatherSlice);
            }

            PendingIntent pendingIntent = null;
            if (rc.getPrimaryAction() != null) {
                pendingIntent = rc.getPrimaryAction().getAction();
            }
            mClickActions.put(button, pendingIntent);

            final SliceItem titleItem = rc.getTitleItem();
            button.setText(titleItem == null ? null : titleItem.getText());
            button.setContentDescription(rc.getContentDescription());
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, mRowTextSize);

            Drawable iconDrawable = null;
            SliceItem icon = SliceQuery.find(item.getSlice(),
                    android.app.slice.SliceItem.FORMAT_IMAGE);
            if (icon != null) {
                final int iconSize = mHasHeader ? mIconSizeWithHeader : mIconSize;
                iconDrawable = icon.getIcon().loadDrawable(mContext);
                if (iconDrawable != null) {
                    final int width = (int) (iconDrawable.getIntrinsicWidth()
                        / (float) iconDrawable.getIntrinsicHeight() * (isWeatherSlice ? mWeatherIconSize : iconSize));
                    iconDrawable.setBounds(0, 0, Math.max(width, 1), (isWeatherSlice ? mWeatherIconSize : iconSize));
                }
            }
            button.setCompoundDrawables(iconDrawable, null, null, null);
            button.setOnClickListener(this);
            button.setClickable(pendingIntent != null);
        }

        // Removing old views
        for (int i = 0; i < mRow.getChildCount(); i++) {
            View child = mRow.getChildAt(i);
            if (!mClickActions.containsKey(child)) {
                mRow.removeView(child);
                i--;
            }
        }

        if (mContentChangeListener != null) {
            mContentChangeListener.run();
        }
        Trace.endSection();
    }

    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        mRow.setDarkAmount(darkAmount);
        updateTextColors();
    }

    public void setViewsTypeface(Typeface tf) {
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof Button) {
                ((Button) v).setTypeface(tf);
            }
        }
    }

    private void updateTextColors() {
        final int blendedColor = getTextColor();
        mTitle.setTextColor(blendedColor);
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof Button) {
                ((Button) v).setTextColor(blendedColor);
            }
        }
    }


    public void setViewsTextStyles(float textSp, boolean textAllCaps) {
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof Button) {
                ((Button) v).setLetterSpacing(textSp);
                ((Button) v).setAllCaps(textAllCaps);
            }
        }
    }

    public void setViewBackground(Drawable drawRes) {
        setViewBackground(drawRes, 255);
    }

    public void setViewBackground(Drawable drawRes, int bgAlpha) {
        mRow.setBackground(drawRes);
        mRow.getBackground().setAlpha(bgAlpha);
    }

    public void setViewBackgroundResource(int drawRes) {
        mRow.setBackgroundResource(drawRes);
    }

    public void setViewPadding(int left, int top, int right, int bottom) {
        mRow.setPadding(left,top,right,bottom);
    }

    @Override
    public void onClick(View v) {
        final PendingIntent action = mClickActions.get(v);
        if (action != null) {
            mActivityStarter.startPendingIntentDismissingKeyguard(action);
        }
    }

    /**
     * Runnable that gets invoked every time the title or the row visibility changes.
     * @param contentChangeListener The listener.
     */
    public void setContentChangeListener(Runnable contentChangeListener) {
        mContentChangeListener = contentChangeListener;
    }

    /**
     * LiveData observer lifecycle.
     * @param slice the new slice content.
     */
    @Override
    public void onChanged(Slice slice) {
        mSlice = slice;
        showSlice();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (key.equals(KEYGUARD_TRANSISITION_ANIMATIONS)) {
            mKeyguardTransitionAnimations = newValue == null || newValue.equals("1");
        } else if (key.equals(LOCKDATE_FONT_SIZE)) {
            mDateSize = TunerService.parseInteger(newValue, 14);
            refreshdatesize();
        } else if (key.equals(LOCK_DATE_ALIGNMENT)) {
            mLockDateAlignment = TunerService.parseInteger(newValue, 1);
            updateDateposition();
        } else if (key.equals(LOCKSCREEN_ITEM_PADDING)) {
            mItemPadding = TunerService.parseInteger(newValue, 35);
            updateItemPadding();
            updateDateposition();
        } else {
            setupUri(newValue);
        }
    }
    
    public void updateDateposition() {


        if(mRowContainer != null) {
           switch (mLockDateAlignment) {
             case 0:
                mRowContainer.setPaddingRelative(updateItemPadding() + 8, 0, 0, 0);
                mRowContainer.setGravity(Gravity.START);
                break;
             case 1:
             default:
                mRowContainer.setPaddingRelative(0, 0, 0, 0);
                mRowContainer.setGravity(Gravity.CENTER);
                break;
             case 2:
                mRowContainer.setPaddingRelative(0, 0, updateItemPadding() + 8, 0);
                mRowContainer.setGravity(Gravity.END);
                break;
           }
        }
    }

    private int updateItemPadding() {
        switch (mItemPadding) {
            case 0:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_0);
            case 1:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_1);
            case 2:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_2);
            case 3:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_3);
            case 4:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_4);
            case 5:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_5);
            case 6:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_6);
            case 7:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_7);
            case 8:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_8);
            case 9:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_9);
            case 10:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_10);
            case 11:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_11);
            case 12:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_12);
            case 13:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_13);
            case 14:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_14);
            case 15:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_15);
            case 16:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_16);
            case 17:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_17);
            case 18:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_18);
            case 19:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_19);
            case 20:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_20);
            case 21:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_21);
            case 22:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_22);
            case 23:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_23);
            case 24:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_24);
            case 25:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_25);
            case 26:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_26);
            case 27:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_27);
            case 28:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_28);
            case 29:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_29);
            case 30:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_30);
            case 31:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_31);
            case 32:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_32);
            case 33:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_33);
            case 34:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_34);
            case 35:
            default:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_35);
            case 36:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_36);
            case 37:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_37);
            case 38:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_38);
            case 39:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_39);
            case 40:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_40);
            case 41:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_41);
            case 42:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_42);
            case 43:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_43);
            case 44:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_44);
            case 45:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_45);
            case 46:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_46);
            case 47:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_47);
            case 48:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_48);
            case 49:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_49);
            case 50:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_50);
            case 51:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_51);
            case 52:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_52);
            case 53:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_53);
            case 54:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_54);
            case 55:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_55);
            case 56:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_56);
            case 57:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_57);
            case 58:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_58);
            case 59:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_59);
            case 60:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_60);
            case 61:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_61);
            case 62:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_62);
            case 63:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_63);
            case 64:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_64);
            case 65:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_65);
            case 66:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_66);
            case 67:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_67);
            case 68:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_68);
            case 69:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_69);
            case 70:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_70);
            case 71:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_71);
            case 72:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_72);
            case 73:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_73);
            case 74:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_74);
            case 75:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_75);
            case 76:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_76);
            case 77:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_77);
            case 78:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_78);
            case 79:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_79);
            case 80:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_80);
            case 81:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_81);
            case 82:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_82);
            case 83:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_83);
            case 84:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_84);
            case 85:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_85);
            case 86:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_86);
            case 87:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_87);
            case 88:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_88);
            case 89:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_89);
            case 90:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_90);
            case 91:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_91);
            case 92:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_92);
            case 93:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_93);
            case 94:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_94);
            case 95:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_95);
            case 96:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_96);
            case 97:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_97);
            case 98:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_98);
            case 99:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_99);
            case 100:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_100);
        }
    }

    /**
     * Sets the slice provider Uri.
     */
    public void setupUri(String uriString) {
        if (uriString == null) {
            uriString = KeyguardSliceProvider.KEYGUARD_SLICE_URI;
        }

        boolean wasObserving = false;
        if (mLiveData != null && mLiveData.hasActiveObservers()) {
            wasObserving = true;
            mLiveData.removeObserver(this);
        }

        mKeyguardSliceUri = Uri.parse(uriString);
        mLiveData = SliceLiveData.fromUri(mContext, mKeyguardSliceUri);

        if (wasObserving) {
            mLiveData.observeForever(this);
        }
    }

    @VisibleForTesting
    int getTextColor() {
        return ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
    }

    @VisibleForTesting
    void setTextColor(@ColorInt int textColor) {
        mTextColor = textColor;
        updateTextColors();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        mIconSize = mRowTextSize;
        mIconSizeWithHeader = (int) mContext.getResources().getDimension(R.dimen.header_icon_size);
        mRowTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.lock_date_font_size_18);
        mRowWithHeaderTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.header_row_font_size);
        mWeatherIconSize = mContext.getResources().getDimensionPixelSize(R.dimen.weather_icon_size);
    }

    public void refresh() {
        Slice slice;
        Trace.beginSection("KeyguardSliceView#refresh");
        // We can optimize performance and avoid binder calls when we know that we're bound
        // to a Slice on the same process.
        if (KeyguardSliceProvider.KEYGUARD_SLICE_URI.equals(mKeyguardSliceUri.toString())) {
            KeyguardSliceProvider instance = KeyguardSliceProvider.getAttachedInstance();
            if (instance != null) {
                slice = instance.onBindSlice(mKeyguardSliceUri);
            } else {
                Log.w(TAG, "Keyguard slice not bound yet?");
                slice = null;
            }
        } else {
            slice = SliceViewManager.getInstance(getContext()).bindSlice(mKeyguardSliceUri);
        }
        onChanged(slice);
        Trace.endSection();
    }

    public void refreshdatesize() {
        if (mDateSize == 10) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10);
        } else if (mDateSize == 11) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11);
        } else if (mDateSize == 12) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12);
        } else if (mDateSize == 13) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13);
        } else if (mDateSize == 14) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14);
        } else if (mDateSize == 15) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15);
        } else if (mDateSize == 16) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16);
        } else if (mDateSize == 17) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17);
        } else if (mDateSize == 18) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18);
        } else if (mDateSize == 19) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19);
        } else if (mDateSize == 20) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20);
        } else if (mDateSize == 21) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21);
        } else if (mDateSize == 22) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22);
        } else if (mDateSize == 23) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23);
        } else if (mDateSize == 24) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24);
        } else if (mDateSize == 25) {
            mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardSliceView:");
        pw.println("  mClickActions: " + mClickActions);
        pw.println("  mTitle: " + (mTitle == null ? "null" : mTitle.getVisibility() == VISIBLE));
        pw.println("  mRow: " + (mRow == null ? "null" : mRow.getVisibility() == VISIBLE));
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mSlice: " + mSlice);
        pw.println("  mHasHeader: " + mHasHeader);
    }

    public static class Row extends LinearLayout {

        /**
         * This view is visible in AOD, which means that the device will sleep if we
         * don't hold a wake lock. We want to enter doze only after all views have reached
         * their desired positions.
         */
        private final Animation.AnimationListener mKeepAwakeListener;
        private LayoutTransition mLayoutTransition;
        private float mDarkAmount;

        public Row(Context context) {
            this(context, null);
        }

        public Row(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public Row(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public Row(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            mKeepAwakeListener = new KeepAwakeAnimationListener(mContext);
        }

        @Override
        protected void onFinishInflate() {
            mLayoutTransition = new LayoutTransition();
            mLayoutTransition.setDuration(DEFAULT_ANIM_DURATION);

            PropertyValuesHolder left = PropertyValuesHolder.ofInt("left", 0, 1);
            PropertyValuesHolder right = PropertyValuesHolder.ofInt("right", 0, 1);
            ObjectAnimator changeAnimator = ObjectAnimator.ofPropertyValuesHolder((Object) null,
                    left, right);
            mLayoutTransition.setAnimator(LayoutTransition.CHANGE_APPEARING, changeAnimator);
            mLayoutTransition.setAnimator(LayoutTransition.CHANGE_DISAPPEARING, changeAnimator);
            mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_APPEARING,
                    Interpolators.ACCELERATE_DECELERATE);
            mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING,
                    Interpolators.ACCELERATE_DECELERATE);
            mLayoutTransition.setStartDelay(LayoutTransition.CHANGE_APPEARING,
                    DEFAULT_ANIM_DURATION);
            mLayoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING,
                    DEFAULT_ANIM_DURATION);

            ObjectAnimator appearAnimator = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
            mLayoutTransition.setAnimator(LayoutTransition.APPEARING, appearAnimator);
            mLayoutTransition.setInterpolator(LayoutTransition.APPEARING, Interpolators.ALPHA_IN);

            ObjectAnimator disappearAnimator = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
            mLayoutTransition.setInterpolator(LayoutTransition.DISAPPEARING,
                    Interpolators.ALPHA_OUT);
            mLayoutTransition.setDuration(LayoutTransition.DISAPPEARING, DEFAULT_ANIM_DURATION / 4);
            mLayoutTransition.setAnimator(LayoutTransition.DISAPPEARING, disappearAnimator);

            mLayoutTransition.setAnimateParentHierarchy(false);
        }

        @Override
        public void onVisibilityAggregated(boolean isVisible) {
            super.onVisibilityAggregated(isVisible);
            setLayoutTransition((isVisible && mKeyguardTransitionAnimations) ? mLayoutTransition : null);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child instanceof KeyguardSliceButton && childCount > 3) {
                    ((KeyguardSliceButton) child).setMaxWidth(width / childCount);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void setDarkAmount(float darkAmount) {
            boolean isAwake = darkAmount != 0;
            boolean wasAwake = mDarkAmount != 0;
            if (isAwake == wasAwake) {
                return;
            }
            mDarkAmount = darkAmount;
            setLayoutAnimationListener(isAwake ? null : mKeepAwakeListener);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /**
     * Representation of an item that appears under the clock on main keyguard message.
     */
    @VisibleForTesting
    static class KeyguardSliceButton extends Button implements
            ConfigurationController.ConfigurationListener {

        @StyleRes
        private static int sStyleId = R.style.TextAppearance_Keyguard_Secondary;

        private boolean shouldTintDrawable = true;
        public KeyguardSliceButton(Context context) {
            super(context, null /* attrs */, 0 /* styleAttr */, sStyleId);
            onDensityOrFontScaleChanged();
            setEllipsize(TruncateAt.END);
        }

        public void setShouldTintDrawable(boolean shouldTintDrawable){
            this.shouldTintDrawable = shouldTintDrawable;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Dependency.get(ConfigurationController.class).addCallback(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Dependency.get(ConfigurationController.class).removeCallback(this);
        }

        @Override
        public void onDensityOrFontScaleChanged() {
            updatePadding();
        }

        @Override
        public void onOverlayChanged() {
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);
            updatePadding();
        }

        private void updatePadding() {
            boolean hasText = !TextUtils.isEmpty(getText());
            int horizontalPadding = (int) getContext().getResources()
                    .getDimension(R.dimen.widget_horizontal_padding) / 2;
            setPadding(horizontalPadding, 0, horizontalPadding * (hasText ? 1 : -1), 0);
            setCompoundDrawablePadding((int) mContext.getResources()
                    .getDimension(R.dimen.widget_icon_padding));
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(color);
            updateDrawableColors();
        }

        @Override
        public void setCompoundDrawables(Drawable left, Drawable top, Drawable right,
                Drawable bottom) {
            super.setCompoundDrawables(left, top, right, bottom);
            updateDrawableColors();
            updatePadding();
        }

        private void updateDrawableColors() {
            if (!shouldTintDrawable){
                return;
            }
            final int color = getCurrentTextColor();
            for (Drawable drawable : getCompoundDrawables()) {
                if (drawable != null) {
                    drawable.setTint(color);
                }
            }
        }
    }
}
