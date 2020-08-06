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

package com.android.systemui.statusbar.phone;

import static android.view.MotionEvent.ACTION_OUTSIDE;

import android.annotation.AttrRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.PulseController;
import com.android.systemui.tuner.TunerService;

public class NavigationBarFrame extends FrameLayout implements KeyguardMonitor.Callback,
        TunerService.Tunable {

    private static final String NAVBAR_PULSE_ENABLED =
            Settings.Secure.NAVBAR_PULSE_ENABLED;

    private DeadZone mDeadZone = null;
    private KeyguardMonitor mKeyguardMonitor;

    private boolean mAttached;
    private boolean mKeyguardShowing;
    private boolean mPulseEnabled;
    
    public NavigationBarFrame(@NonNull Context context) {
        this(context, null);
    }

    public NavigationBarFrame(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NavigationBarFrame(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mKeyguardMonitor.addCallback(this);
        Dependency.get(TunerService.class).addTunable(this, NAVBAR_PULSE_ENABLED);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case NAVBAR_PULSE_ENABLED:
                mPulseEnabled =
                        TunerService.parseIntegerSwitch(newValue, false);
                updatePulseVisibility();
                break;
            default:
                break;
        }
    }

    public void setDeadZone(@NonNull DeadZone deadZone) {
        mDeadZone = deadZone;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == ACTION_OUTSIDE) {
            if (mDeadZone != null) {
                return mDeadZone.onTouchEvent(event);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onAttachedToWindow() {
        mAttached = true;
        updatePulseVisibility();
        super.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        mAttached = false;
        updatePulseVisibility();
        super.onDetachedFromWindow();
    }

    @Override
    public void onKeyguardShowingChanged() {
        mKeyguardShowing = mKeyguardMonitor.isShowing();
        updatePulseVisibility();
    }

    private void updatePulseVisibility() {
        if (mAttached && !mKeyguardShowing && mPulseEnabled) {
            Dependency.get(PulseController.class).attachPulseTo(this);
        } else {
            Dependency.get(PulseController.class).detachPulseFrom(this);
        }
    }
}
