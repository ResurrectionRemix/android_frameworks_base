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
 * limitations under the License.
 */

package android.view.animation;

import android.graphics.Rect;
import android.graphics.RectF;

/**
 * An animation that controls the clip of an object. See the
 * {@link android.view.animation full package} description for details and
 * sample code.
 *
 * @hide
 */
public class ClipRectAnimation extends Animation {
    protected RectF mFromRectF = new RectF();
    protected RectF mToRectF = new RectF();
    protected Rect mFromRect = new Rect();
    protected Rect mToRect = new Rect();
    protected Rect mResolvedFrom = new Rect();
    protected Rect mResolvedTo = new Rect();

    /**
     * Constructor to use when building a ClipRectAnimation from code
     *
     * @param fromClip the clip rect to animate from
     * @param toClip the clip rect to animate to
     */
    public ClipRectAnimation(Rect fromClip, Rect toClip) {
        if (fromClip == null || toClip == null) {
            throw new RuntimeException("Expected non-null animation clip rects");
        }
        mFromRect.set(fromClip);
        mToRect.set(toClip);
    }

    /**
     * Constructor to use when building a ClipRectAnimation from code
     */
    public ClipRectAnimation(int fromL, int fromT, int fromR, int fromB,
            int toL, int toT, int toR, int toB) {
        mFromRect.set(fromL, fromT, fromR, fromB);
        mToRect.set(toL, toT, toR, toB);
        mFromRectF.set(1.0f, 1.0f, 1.0f, 1.0f);
        mToRectF.set(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public ClipRectAnimation(float fromL, float fromT, float fromR, float fromB,
                             float toL, float toT, float toR, float toB) {
        mFromRectF.set(fromL, fromT, fromR, fromB);
        mToRectF.set(toL, toT, toR, toB);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mResolvedFrom.left = (int) resolveSize(RELATIVE_TO_SELF, mFromRectF.left, width, parentWidth);
        mResolvedFrom.top = (int) resolveSize(RELATIVE_TO_SELF, mFromRectF.top, height, parentHeight);
        mResolvedFrom.right = (int) resolveSize(RELATIVE_TO_SELF, mFromRectF.right, width, parentWidth);
        mResolvedFrom.bottom = (int) resolveSize(RELATIVE_TO_SELF, mFromRectF.bottom, height, parentHeight);
        mResolvedTo.left = (int) resolveSize(RELATIVE_TO_SELF, mToRectF.left, width, parentWidth);
        mResolvedTo.top = (int) resolveSize(RELATIVE_TO_SELF, mToRectF.top, height, parentHeight);
        mResolvedTo.right = (int) resolveSize(RELATIVE_TO_SELF, mToRectF.right, width, parentWidth);
        mResolvedTo.bottom = (int) resolveSize(RELATIVE_TO_SELF, mToRectF.bottom, height, parentHeight);
    }

    @Override
    protected void applyTransformation(float it, Transformation tr) {
        int l = mResolvedFrom.left + (int) ((mResolvedTo.left - mResolvedFrom.left) * it);
        int t = mResolvedFrom.top + (int) ((mResolvedTo.top - mResolvedFrom.top) * it);
        int r = mResolvedFrom.right + (int) ((mResolvedTo.right - mResolvedFrom.right) * it);
        int b = mResolvedFrom.bottom + (int) ((mResolvedTo.bottom - mResolvedFrom.bottom) * it);
        tr.setClipRect(l, t, r, b);
    }

    @Override
    public boolean willChangeTransformationMatrix() {
        return false;
    }
}
