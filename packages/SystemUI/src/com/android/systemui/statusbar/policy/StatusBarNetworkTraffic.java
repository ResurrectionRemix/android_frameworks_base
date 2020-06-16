/**
 * Copyright (C) 2019-2020 crDroid Android Project
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

package com.android.systemui.statusbar.policy;

import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;

/** @hide */
public class StatusBarNetworkTraffic extends NetworkTraffic implements DarkReceiver,
        StatusIconDisplayable {


    public static final String SLOT = "networktraffic";

    private int mVisibleState = -1;
    private boolean mSystemIconVisible = true;
    private boolean mColorIsStatic;

    public StatusBarNetworkTraffic(Context context) {
        super(context);
    }

    public StatusBarNetworkTraffic(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusBarNetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setVisibleState(STATE_ICON);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        if (mColorIsStatic) {
            return;
        }
        newTint = DarkIconDispatcher.getTint(area, this, tint);
        checkUpdateTrafficDrawable();
    }

    @Override
    public void setStaticDrawableColor(int color) {
        mColorIsStatic = true;
        newTint = color;
        checkUpdateTrafficDrawable();
    }

    @Override
    public void setDecorColor(int color) {
    }

    @Override
    public String getSlot() {
        return SLOT;
    }

    @Override
    public boolean isIconVisible() {
        return mLocation == 1;
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState) {
            return;
        }
        mVisibleState = state;

        switch (state) {
            case STATE_ICON:
                mSystemIconVisible = true;
                break;
            case STATE_DOT:
            case STATE_HIDDEN:
            default:
                mSystemIconVisible = false;
                break;
        }
        updateVisibility();
    }

    @Override
    protected void updateViews() {
        if (isIconVisible() && mScreenOn) {
            updateViewState();
        } else {
            clearHandlerCallbacks();
            updateVisibility();
        }
    }

    @Override
    protected void updateVisibility() {
        boolean enabled = mIsActive && mSystemIconVisible && isIconVisible() && mScreenOn
            &&  getText() != "";
        if (enabled != mVisible) {
            mVisible = enabled;
            setVisibility(mVisible ? VISIBLE : GONE);
            checkUpdateTrafficDrawable();
        }
    }

    private void checkUpdateTrafficDrawable() {
        // Wait for icon to be visible and tint to be changed
        if (mVisible && mIconTint != newTint) {
            mIconTint = newTint;
            updateTrafficDrawable();
        }
    }
}
