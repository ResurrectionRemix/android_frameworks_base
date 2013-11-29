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

import android.animation.TimeInterpolator;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;

/**
 * 
 */
public class QuickSettingsTileFlip3d extends GestureDetector.SimpleOnGestureListener
    implements View.OnTouchListener, TimeInterpolator {

    private static final float VELOCITY_THRESHOLD = 500.0f;

    private ViewGroup mFront;
    private ViewGroup mBack;
    private float mDegrees;
    private GestureDetector mDetector;
    private DecelerateInterpolator mInterpolator;
    private boolean mFlingCancelClamp = false;
    private boolean mFrontSideOnDown = true;

    public QuickSettingsTileFlip3d(ViewGroup front, ViewGroup back) {
        // In the initial state, the front tile is displayed, so degrees = 0
        mDegrees = 0.0f;
        mFront = front;
        mBack = back;
        mDetector = new GestureDetector(front.getContext(), this);
        mInterpolator = new DecelerateInterpolator();
    }

    public boolean isFrontSide() {
        return (mFront.getVisibility() == View.VISIBLE);
    }

    public void rotateToFront(boolean fromLeft) {
        if (fromLeft) {
            if (mDegrees >= 180) {
                rotateTo(360);
            } else {
                rotateTo(0);
            }
        } else {
            rotateTo(-360);
        }
    }

    public void rotateToBack(boolean fromLeft) {
        if (fromLeft) {
            rotateTo(180.0f);
        } else {
            rotateTo(-180.0f);
        }
    }

    public void rotateBy(float degrees) {
        mDegrees += degrees;
        mDegrees = mDegrees % 360.0f;

        updateRotation();
    }

    public void rotateTo(float degrees) {
        mDegrees = degrees;
        mFront.animate().setInterpolator(this).setDuration(150).rotationY(degrees).start();
        mBack.animate().setInterpolator(this).setDuration(150).rotationY(-180.0f + degrees).start();
        updateVisibility();
        mDegrees = mDegrees % 360.0f;
    }

    private void updateRotation() {
        // Decide what view to display. We can rotate both to the right or to the left, so we
        // have to catch both -90 and 90 degrees.
        mFront.setRotationY(mDegrees);
        mBack.setRotationY(-180.0f + mDegrees);
        updateVisibility();
    }

    private void updateVisibility() {
        double absRotationY = Math.abs(mFront.getRotationY());

        if (absRotationY > 90 && absRotationY < 270) {
            mFront.setVisibility(View.GONE);
            mBack.setVisibility(View.VISIBLE);
        } else {
            mFront.setVisibility(View.VISIBLE);
            mBack.setVisibility(View.GONE);
        }
    }

    private void clampRotation() {
        if (mDegrees < -270) {
            mDegrees = -360.0f;
        } else if (mDegrees < -90) {
            mDegrees = -180.0f;
        } else if (mDegrees > 270) {
            mDegrees = 360.0f;
        } else if (mDegrees > 90) {
            mDegrees = 180.0f;
        } else {
            mDegrees = 0.0f;
        }

        rotateTo(mDegrees);
    }

    private void dispatchEventToActive(MotionEvent event) {
        if (isFrontSide()) {
            mFront.onTouchEvent(event);
        } else {
            mBack.onTouchEvent(event);
        }
    }

    private void dispatchEventToAll(MotionEvent event) {
        mFront.onTouchEvent(event);
        mBack.onTouchEvent(event);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (!mFlingCancelClamp) {
              clampRotation();
            }
            mFlingCancelClamp = false;

            dispatchEventToActive(event);
        }
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) {
        float width = mFront.getVisibility() == View.VISIBLE ? mFront.getWidth() : mBack.getWidth();

        if (width > 0) {
            double radians = Math.toRadians(-dX * 0.5f);
            radians = Math.max(-1, radians);
            radians = Math.min(1, radians);

            float angle = (float) Math.toDegrees(Math.asin(radians));
            rotateBy(angle);
        }

        // Cancel events on the children view (if any)
        MotionEvent evt = MotionEvent.obtain(0, 0,
            MotionEvent.ACTION_CANCEL, e1.getX(), e1.getY(), 0);
        dispatchEventToAll(evt);

        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityX) > VELOCITY_THRESHOLD && isFrontSide() == mFrontSideOnDown) {
            if (isFrontSide()) {
                rotateToBack(velocityX > 0.0);
            } else {
                rotateToFront(velocityX > 0.0);
            }

            mFlingCancelClamp = true;
        }

        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        mFrontSideOnDown = isFrontSide();
        dispatchEventToActive(e);

        return true;
    }

    @Override
    public float getInterpolation(float input) {
        updateVisibility();
        return mInterpolator.getInterpolation(input);
    }
}