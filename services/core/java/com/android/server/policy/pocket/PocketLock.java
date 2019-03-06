/*
 * Copyright (C) 2016 The ParanoidAndroid Project
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
package com.android.server.policy.pocket;

import android.animation.Animator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

/**
 * This class provides a fullscreen overlays view, displaying itself
 * even on top of lock screen. While this view is displaying touch
 * inputs are not passed to the the views below.
 * @see android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
 * @author Carlo Savignano
 */
public class PocketLock {

    private final Context mContext;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private Handler mHandler;
    private View mView;
    private View mHintContainer;

    private boolean mAttached;
    private boolean mAnimating;

    /**
     * Creates pocket lock objects, inflate view and set layout parameters.
     * @param context
     */
    public PocketLock(Context context) {
        mContext = context;
        mHandler = new Handler();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mLayoutParams = getLayoutParams();
        mView = LayoutInflater.from(mContext).inflate(
                com.android.internal.R.layout.pocket_lock_view_layout, null);
    }

    public void show(final boolean animate) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (mAttached) {
                    return;
                }

                if (mAnimating) {
                    mView.animate().cancel();
                }

                if (animate) {
                    mView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    mView.animate().alpha(1.0f).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            mAnimating = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mView.setLayerType(View.LAYER_TYPE_NONE, null);
                            mAnimating = false;
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {
                        }
                    }).withStartAction(new Runnable() {
                        @Override
                        public void run() {
                            mView.setAlpha(0.0f);
                            addView();
                        }
                    }).start();
                } else {
                    mView.setAlpha(1.0f);
                    addView();
                }
            }
        };

        mHandler.post(r);
    }

    public void hide(final boolean animate) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!mAttached) {
                    return;
                }

                if (mAnimating) {
                    mView.animate().cancel();
                }

                if (animate) {
                    mView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    mView.animate().alpha(0.0f).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            mAnimating = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mView.setLayerType(View.LAYER_TYPE_NONE, null);
                            mAnimating = false;
                            removeView();
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {
                        }
                    }).start();
                } else {
                    removeView();
                    mView.setAlpha(0.0f);
                }
            }
        };

        mHandler.post(r);
    }

    private void addView() {
        if (mWindowManager != null && !mAttached) {
            mWindowManager.addView(mView, mLayoutParams);          
            mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            mAttached = true;
        }
    }

    private void removeView() {
        if (mWindowManager != null && mAttached) {
            mWindowManager.removeView(mView);
            mAnimating = false;
            mAttached = false;
        }
    }

    private WindowManager.LayoutParams getLayoutParams() {
        mLayoutParams = new WindowManager.LayoutParams();
        mLayoutParams.format = PixelFormat.TRANSLUCENT;
        mLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        mLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        mLayoutParams.gravity = Gravity.CENTER;
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        mLayoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        return mLayoutParams;
    }

}
