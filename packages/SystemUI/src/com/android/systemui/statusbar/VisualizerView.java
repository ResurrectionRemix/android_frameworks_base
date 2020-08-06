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

package com.android.systemui.statusbar;

import android.annotation.AttrRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.policy.PulseController;
import com.android.systemui.tuner.TunerService;

public class VisualizerView extends FrameLayout implements TunerService.Tunable {

    private static final String LOCKSCREEN_PULSE_ENABLED =
            Settings.Secure.LOCKSCREEN_PULSE_ENABLED;

    private boolean mVisible;
    private boolean mAttached;
    private boolean mPulseEnabled;
    private boolean mDozing;
    private int mStatusBarState;

    public VisualizerView(@NonNull Context context) {
        this(context, null);
    }

    public VisualizerView(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Dependency.get(TunerService.class).addTunable(this, LOCKSCREEN_PULSE_ENABLED);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_PULSE_ENABLED:
                mPulseEnabled =
                        TunerService.parseIntegerSwitch(newValue, false /*default 0*/);
                updatePulseVisibility();
                break;
            default:
                break;
        }
    }

    public void setDozing(boolean dozing) {
        if (mDozing != dozing) {
            mDozing = dozing;
            updatePulseVisibility();
        }
    }

    public void setStatusBarState(int statusBarState) {
        if (mStatusBarState != statusBarState) {
            mStatusBarState = statusBarState;
            updatePulseVisibility();
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        updatePulseVisibility();
    }

    @Override
    public void onDetachedFromWindow() {
        mAttached = false;
        updatePulseVisibility();
        super.onDetachedFromWindow();
    }

    private void updatePulseVisibility() {
        boolean visible = mPulseEnabled && mStatusBarState != StatusBarState.SHADE
            && mAttached && !mDozing;
        if (visible != mVisible) {
            mVisible = visible;
            if (mVisible) {
                Dependency.get(PulseController.class).attachPulseTo(this);
            } else {
                Dependency.get(PulseController.class).detachPulseFrom(this);
            }
        }
    }
}
