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
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import static com.android.systemui.plugins.DarkIconDispatcher.getTint;
import static com.android.systemui.plugins.DarkIconDispatcher.isInArea;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.DualToneHandler;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.MobileIconState;

public class StatusBarMobileView extends FrameLayout implements DarkReceiver,
        StatusIconDisplayable {
    private static final String TAG = "StatusBarMobileView";

    /// Used to show etc dots
    private StatusBarIconView mDotView;
    /// The main icon view
    private LinearLayout mMobileGroup;
    private String mSlot;
    private MobileIconState mState;
    private SignalDrawable mMobileDrawable;
    private View mInoutContainer;
    private ImageView mIn;
    private ImageView mOut;
    private ImageView mMobile, mMobileType, mMobileRoaming;
    private View mMobileRoamingSpace;
    private int mVisibleState = -1;
    private DualToneHandler mDualToneHandler;
    private ImageView mVolte;
    private View mMobileSignalType;
    private boolean mOldStyleType;
    private ImageView mMobileTypeSmall;

    public static StatusBarMobileView fromContext(Context context, String slot) {
        LayoutInflater inflater = LayoutInflater.from(context);
        StatusBarMobileView v = (StatusBarMobileView)
                inflater.inflate(R.layout.status_bar_mobile_signal_group, null);

        v.setSlot(slot);
        v.init();
        v.setVisibleState(STATE_ICON);
        return v;
    }

    public StatusBarMobileView(Context context) {
        super(context);
    }

    public StatusBarMobileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusBarMobileView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StatusBarMobileView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        float translationX = getTranslationX();
        float translationY = getTranslationY();
        outRect.left += translationX;
        outRect.right += translationX;
        outRect.top += translationY;
        outRect.bottom += translationY;
    }

    private void init() {
        mDualToneHandler = new DualToneHandler(getContext());
        mMobileGroup = findViewById(R.id.mobile_group);
        mMobile = findViewById(R.id.mobile_signal);
        mMobileType = findViewById(R.id.mobile_type);
        mMobileRoaming = findViewById(R.id.mobile_roaming);
        mMobileRoamingSpace = findViewById(R.id.mobile_roaming_space);
        mIn = findViewById(R.id.mobile_in);
        mOut = findViewById(R.id.mobile_out);
        mInoutContainer = findViewById(R.id.inout_container);
        mVolte = findViewById(R.id.mobile_volte);
        mMobileSignalType = findViewById(R.id.mobile_signal_type);
        mMobileTypeSmall = findViewById(R.id.mobile_type_small);

        mMobileDrawable = new SignalDrawable(getContext());
        mMobile.setImageDrawable(mMobileDrawable);

        initDotView();
    }

    private void initDotView() {
        mDotView = new StatusBarIconView(mContext, mSlot, null);
        mDotView.setVisibleState(STATE_DOT);

        int width = mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_icon_size);
        LayoutParams lp = new LayoutParams(width, width);
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        addView(mDotView, lp);
    }

    public void applyMobileState(MobileIconState state, boolean oldStyleType) {
        boolean requestLayout = false;
        if (state == null) {
            requestLayout = getVisibility() != View.GONE;
            setVisibility(View.GONE);
            mState = null;
        } else if (mState == null) {
            requestLayout = true;
            mState = state.copy();
            mOldStyleType = oldStyleType;
            initViewState();
        } else if (!mState.equals(state) || mOldStyleType != oldStyleType) {
            requestLayout = updateState(state.copy(), oldStyleType);
        }

        if (requestLayout) {
            requestLayout();
        }
    }

    private void initViewState() {
        setContentDescription(mState.contentDescription);
        if (!mState.visible) {
            mMobileGroup.setVisibility(View.GONE);
        } else {
            mMobileGroup.setVisibility(View.VISIBLE);
        }
        if (mState.strengthId > 0) {
            mMobile.setVisibility(View.VISIBLE);
            mMobileDrawable.setLevel(mState.strengthId);
        }else {
            mMobile.setVisibility(View.GONE);
        }
        boolean showRoamingSpace = false;
        if (mState.typeId > 0) {
            if (mOldStyleType) {
                mMobileType.setVisibility(View.GONE);
                mMobileTypeSmall.setContentDescription(mState.typeContentDescription);
                mMobileTypeSmall.setImageResource(mState.typeId);
                mMobileTypeSmall.setVisibility(View.VISIBLE);
                showRoamingSpace = true;
                setMobileSignalWidth(false);
            } else {
                mMobileType.setContentDescription(mState.typeContentDescription);
                mMobileType.setImageResource(mState.typeId);
                mMobileType.setVisibility(View.VISIBLE);
                mMobileTypeSmall.setVisibility(View.GONE);
                setMobileSignalWidth(true);
            }
        } else {
            mMobileType.setVisibility(View.GONE);
            mMobileTypeSmall.setVisibility(View.GONE);
            setMobileSignalWidth(true);
        }
        if (mState.roaming) {
            mMobileTypeSmall.setVisibility(View.GONE);
            setMobileSignalWidth(true);
        }
        mMobileRoaming.setVisibility(mState.roaming ? View.VISIBLE : View.GONE);
        mMobileRoamingSpace.setVisibility(mState.roaming || showRoamingSpace ? View.VISIBLE : View.GONE);
        mIn.setVisibility(mState.activityIn ? View.VISIBLE : View.GONE);
        mOut.setVisibility(mState.activityOut ? View.VISIBLE : View.GONE);
        mInoutContainer.setVisibility((mState.activityIn || mState.activityOut)
                ? View.VISIBLE : View.GONE);
        if (mState.volteId > 0 ) {
            mVolte.setImageResource(mState.volteId);
            mVolte.setVisibility(View.VISIBLE);
        }else {
            mVolte.setVisibility(View.GONE);
        }
    }

    private void setMobileSignalWidth(boolean small) {
        ViewGroup.LayoutParams p = mMobileSignalType.getLayoutParams();
        if (small) {
            p.width = mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_mobile_signal_width);
        } else {
            p.width = mContext.getResources().getDimensionPixelSize(
                    R.dimen.status_bar_mobile_signal_with_type_width);
            int paddingLimit = mContext.getResources().getDimensionPixelSize(
                    R.dimen.status_bar_mobile_type_padding_limit);
            int padding = mMobileTypeSmall.getWidth() < paddingLimit ?
                    mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_mobile_type_padding) : 0;
            mMobileTypeSmall.setPadding(padding, 0, 0, 0);
        }
        mMobileSignalType.setLayoutParams(p);
    }

    private boolean updateState(MobileIconState state, boolean oldStyleType) {
        boolean needsLayout = false;

        setContentDescription(state.contentDescription);
        if (mState.visible != state.visible || mState.provisioned != state.provisioned) {
            mMobileGroup.setVisibility(state.visible && state.provisioned
                    ? View.VISIBLE : View.GONE);
            needsLayout = true;
        }
        if (state.strengthId > 0) {
            mMobileDrawable.setLevel(state.strengthId);
            mMobile.setVisibility(View.VISIBLE);
        }else {
            mMobile.setVisibility(View.GONE);
        }
        boolean showRoamingSpace = false;
        if (mState.typeId != state.typeId || mOldStyleType != oldStyleType) {
            needsLayout |= state.typeId == 0 || mState.typeId == 0;
            if (state.typeId != 0) {
                if (oldStyleType) {
                    mMobileType.setVisibility(View.GONE);
                    mMobileTypeSmall.setContentDescription(state.typeContentDescription);
                    mMobileTypeSmall.setImageResource(state.typeId);
                    mMobileTypeSmall.setVisibility(View.VISIBLE);
                    showRoamingSpace = true;
                    setMobileSignalWidth(false);
                } else {
                    mMobileType.setContentDescription(state.typeContentDescription);
                    mMobileType.setImageResource(state.typeId);
                    mMobileType.setVisibility(View.VISIBLE);
                    mMobileTypeSmall.setVisibility(View.GONE);
                    setMobileSignalWidth(true);
                }
            } else {
                mMobileType.setVisibility(View.GONE);
                mMobileTypeSmall.setVisibility(View.GONE);
                setMobileSignalWidth(true);
            }
        }
        if (state.roaming) {
            mMobileTypeSmall.setVisibility(View.GONE);
            setMobileSignalWidth(true);
        }
        mMobileRoaming.setVisibility(state.roaming ? View.VISIBLE : View.GONE);
        mMobileRoamingSpace.setVisibility(showRoamingSpace || state.roaming ? View.VISIBLE : View.GONE);
        mIn.setVisibility(state.activityIn ? View.VISIBLE : View.GONE);
        mOut.setVisibility(state.activityOut ? View.VISIBLE : View.GONE);
        mInoutContainer.setVisibility((state.activityIn || state.activityOut)
                ? View.VISIBLE : View.GONE);

        if (mState.volteId != state.volteId) {
            if (state.volteId != 0) {
                mVolte.setImageResource(state.volteId);
                mVolte.setVisibility(View.VISIBLE);
            } else {
                mVolte.setVisibility(View.GONE);
            }
        }

        needsLayout |= state.roaming != mState.roaming
                || state.activityIn != mState.activityIn
                || state.activityOut != mState.activityOut
                || mOldStyleType != oldStyleType;

        mState = state;
        mOldStyleType = oldStyleType;
        return needsLayout;
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        float intensity = isInArea(area, this) ? darkIntensity : 0;
        mMobileDrawable.setTintList(
                ColorStateList.valueOf(mDualToneHandler.getSingleColor(intensity)));
        ColorStateList color = ColorStateList.valueOf(getTint(area, this, tint));
        mIn.setImageTintList(color);
        mOut.setImageTintList(color);
        mMobileType.setImageTintList(color);
        mMobileTypeSmall.setImageTintList(color);
        mMobileRoaming.setImageTintList(color);
        mVolte.setImageTintList(color);
        mDotView.setDecorColor(tint);
        mDotView.setIconColor(tint, false);
    }

    @Override
    public String getSlot() {
        return mSlot;
    }

    public void setSlot(String slot) {
        mSlot = slot;
    }

    @Override
    public void setStaticDrawableColor(int color) {
        ColorStateList list = ColorStateList.valueOf(color);
        float intensity = color == Color.WHITE ? 0 : 1;
        // We want the ability to change the theme from the one set by SignalDrawable in certain
        // surfaces. In this way, we can pass a theme to the view.
        mMobileDrawable.setTintList(
                ColorStateList.valueOf(mDualToneHandler.getSingleColor(intensity)));
        mIn.setImageTintList(list);
        mOut.setImageTintList(list);
        mMobileType.setImageTintList(list);
        mMobileRoaming.setImageTintList(list);
        mVolte.setImageTintList(list);
        mMobileTypeSmall.setImageTintList(list);
        mDotView.setDecorColor(color);
    }

    @Override
    public void setDecorColor(int color) {
        mDotView.setDecorColor(color);
    }

    @Override
    public boolean isIconVisible() {
        return mState.visible;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState) {
            return;
        }

        mVisibleState = state;
        switch (state) {
            case STATE_ICON:
                mMobileGroup.setVisibility(View.VISIBLE);
                mDotView.setVisibility(View.GONE);
                break;
            case STATE_DOT:
                mMobileGroup.setVisibility(View.INVISIBLE);
                mDotView.setVisibility(View.VISIBLE);
                break;
            case STATE_HIDDEN:
            default:
                mMobileGroup.setVisibility(View.INVISIBLE);
                mDotView.setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @VisibleForTesting
    public MobileIconState getState() {
        return mState;
    }

    @Override
    public String toString() {
        return "StatusBarMobileView(slot=" + mSlot + " state=" + mState + ")";
    }

    public void updateDisplayType(boolean oldStyleType) {
        boolean needsLayout = false;
        boolean showRoamingSpace = false;

        if (mOldStyleType != oldStyleType) {
            if (mState.typeId != 0) {
                if (oldStyleType) {
                    mMobileType.setVisibility(View.GONE);
                    mMobileTypeSmall.setContentDescription(mState.typeContentDescription);
                    mMobileTypeSmall.setImageResource(mState.typeId);
                    mMobileTypeSmall.setVisibility(View.VISIBLE);
                    showRoamingSpace = true;
                    setMobileSignalWidth(false);
                } else {
                    mMobileType.setContentDescription(mState.typeContentDescription);
                    mMobileType.setImageResource(mState.typeId);
                    mMobileType.setVisibility(View.VISIBLE);
                    setMobileSignalWidth(true);
                }
            } else {
                mMobileType.setVisibility(View.GONE);
                mMobileTypeSmall.setVisibility(View.GONE);
                setMobileSignalWidth(true);
            }
        }
        if (mState.roaming) {
            mMobileTypeSmall.setVisibility(View.GONE);
            setMobileSignalWidth(true);
        }
        mMobileRoaming.setVisibility(mState.roaming ? View.VISIBLE : View.GONE);
        mMobileRoamingSpace.setVisibility(showRoamingSpace || mState.roaming ? View.VISIBLE : View.GONE);
        mIn.setVisibility(mState.activityIn ? View.VISIBLE : View.GONE);
        mOut.setVisibility(mState.activityOut ? View.VISIBLE : View.GONE);
        mInoutContainer.setVisibility((mState.activityIn || mState.activityOut)
                ? View.VISIBLE : View.GONE);

        needsLayout = mOldStyleType != oldStyleType;
        mOldStyleType = oldStyleType;

        if (needsLayout) {
            requestLayout();
        }
    }
}
