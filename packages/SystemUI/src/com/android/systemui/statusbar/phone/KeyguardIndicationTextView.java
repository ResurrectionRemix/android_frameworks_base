/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;

import com.android.systemui.Interpolators;

/**
 * A view to show hints on Keyguard ("Swipe up to unlock", "Tap again to open").
 */
public class KeyguardIndicationTextView extends TextView {

    private static final long ANIMATION_DURATION = 500;

    public KeyguardIndicationTextView(Context context) {
        super(context);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Changes the text, optionally with an animation.
     *
     * @param text The text to show.
     * @param animate Whether or not the change should be animated.
     */
    public void switchIndication(CharSequence text, boolean animate) {
        if(animate) {
            switchIndication(text);
        } else if(TextUtils.isEmpty(text)) {
            setVisibility(View.INVISIBLE);
        } else {
            setText(text);
            setVisibility(View.VISIBLE);
        }
    }

    /**
     * Changes the text with an animation and makes sure a single indication is shown long enough.
     *
     * @param text The text to show.
     */
    public void switchIndication(CharSequence text) {
        // TODO: Make sure that we will show one indication long enough.
        if (TextUtils.isEmpty(text) && getVisibility() == View.VISIBLE) {
            animateVisibility(View.INVISIBLE, ANIMATION_DURATION, Interpolators.ALPHA_OUT, null);
        } else if(!TextUtils.isEmpty(text) && getVisibility() == View.INVISIBLE) {
            setText(text);
            animateVisibility(View.VISIBLE, ANIMATION_DURATION, Interpolators.ALPHA_IN, null);
        } else if(getVisibility() == View.VISIBLE){
            // Fade out the current indication
            animateVisibility(View.INVISIBLE, ANIMATION_DURATION / 2, Interpolators.ALPHA_OUT, () -> {
                // Fade in the new indication
                setText(text);
                animateVisibility(View.VISIBLE, ANIMATION_DURATION / 2, Interpolators.ALPHA_IN, null);
            });
        }
    }

    private void animateVisibility(int targetVisibility, long duration, Interpolator interpolator,
      Runnable endAction) {
        float targetAlpha = 0f;
        if (targetVisibility == View.VISIBLE) {
            setVisibility(View.VISIBLE);
            targetAlpha = 1f;
        }
        animate()
        .alpha(targetAlpha)
        .setDuration(duration)
        .setInterpolator(interpolator)
        .withEndAction(() -> {
            if (targetVisibility == View.INVISIBLE) {
               setVisibility(View.INVISIBLE);
            }
            if (endAction != null) {
                endAction.run();
            }
        });
    }

    /**
     * See {@link #switchIndication}.
     */
    public void switchIndication(int textResId) {
        switchIndication(getResources().getText(textResId));
    }
}
