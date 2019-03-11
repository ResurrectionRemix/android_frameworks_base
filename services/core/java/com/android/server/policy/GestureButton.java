/*
 *  Copyright (C) 2018 The OmniROM Project
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
package com.android.server.policy;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.System;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerPolicyConstants.PointerEventListener;
import android.view.inputmethod.InputMethodManagerInternal;

import com.android.internal.R;
import com.android.internal.util.rr.RRFWBUtils;
import com.android.internal.util.rr.RRTaskUtils;
import com.android.server.LocalServices;

public class GestureButton implements PointerEventListener {
    private static final String TAG = "GestureButton";
    private static boolean DEBUG = false;

    private static final int GESTURE_KEY_DISTANCE_TIMEOUT = 250;
    private static final int GESTURE_KEY_LONG_CLICK_TIMEOUT = 500;
    private static final int MSG_SEND_SWITCH_KEY = 5;
    private static final int MSG_SEND_KEY = 6;
    private static final int MSG_SEND_LONG_PRESS = 7;
    private static float mRecentMoveTolerance = 5.0f;

    private long mDownTime;
    private float mFromX;
    private float mFromY;
    private boolean mIsKeyguardShowing;
    private float mLastX;
    private float mLastY;
    private int mNavigationBarPosition = 0;
    private GestureButtonHandler mGestureButtonHandler;
    private int mPreparedKeycode;
    private PhoneWindowManager mPwm;
    private int mScreenHeight = -1;
    private int mScreenWidth = -1;
    private boolean mSwipeStartFromEdge;
    private final int mSwipeStartThreshold;
    private boolean mKeyEventHandled;
    private boolean mRecentsTriggered;
    private boolean mLongSwipePossible;
    private int mEventDeviceId;
    private boolean mDismissInputMethod;
    private int mSwipeMinLength;
    private int mLongPressMaxLength;
    private Context mContext;

    private OnTouchListener mTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            handleTouch(event);
            return true;
        }
    };

    private class GestureButtonHandler extends Handler {

        public GestureButtonHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SEND_SWITCH_KEY:
                    if (DEBUG) Slog.i(TAG, "MSG_SEND_SWITCH_KEY");
                    mKeyEventHandled = true;
                    mPwm.performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
                    toggleRecentApps();
                    break;
                case MSG_SEND_KEY:
                    if (DEBUG) Slog.i(TAG, "MSG_SEND_KEY " + mPreparedKeycode);
                    mKeyEventHandled = true;
                    mPwm.performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
                    triggerGestureVirtualKeypress(mPreparedKeycode);
                    break;
                case MSG_SEND_LONG_PRESS:
                    if (DEBUG) Slog.i(TAG, "MSG_SEND_LONG_PRESS");
                    mKeyEventHandled = true;
                    mPwm.performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                    RRTaskUtils.toggleLastApp(mContext, UserHandle.USER_CURRENT);
                    break;
            }
        }
    }

    public GestureButton(Context context, PhoneWindowManager pwm) {
        Slog.i(TAG, "GestureButton init");
        mContext = context;
        mPwm = pwm;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        mScreenHeight = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
        mScreenWidth = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
        mSwipeStartThreshold = 20;
        mSwipeMinLength = context.getResources().getDimensionPixelSize(R.dimen.nav_gesture_swipe_min_length);
        mLongPressMaxLength = context.getResources().getDimensionPixelSize(R.dimen.nav_gesture_long_press_max_length);
        HandlerThread gestureButtonThread = new HandlerThread("GestureButtonThread", -8);
        gestureButtonThread.start();
        mGestureButtonHandler = new GestureButtonHandler(gestureButtonThread.getLooper());
    }

    private void dismissInputMethod() {
        mDismissInputMethod = true;
        if (mPwm.mInputMethodManagerInternal == null) {
            mPwm.mInputMethodManagerInternal = (InputMethodManagerInternal) LocalServices.getService(InputMethodManagerInternal.class);
        }
        if (mPwm.mInputMethodManagerInternal != null) {
            mPwm.mInputMethodManagerInternal.hideCurrentInputMethod();
        }
    }

    public void onPointerEvent(MotionEvent event) {
        handleTouch(event);
    }

    private void handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        mEventDeviceId = event.getDeviceId();

        if (action == MotionEvent.ACTION_DOWN || mSwipeStartFromEdge) {
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mIsKeyguardShowing = mPwm.isKeyguardLocked();
                    if (mIsKeyguardShowing) {
                        break;
                    }

                    mPreparedKeycode = -1;
                    if (mNavigationBarPosition == 0) {
                        if (rawY >= ((float) (mScreenHeight - mSwipeStartThreshold))) {
                            mFromX = rawX;
                            mFromY = rawY;
                            if (mFromX < ((float) (mScreenWidth / 3)) || mFromX > ((float) ((mScreenWidth * 2) / 3))) {
                                mPreparedKeycode = KeyEvent.KEYCODE_BACK;
                            } else {
                                mPreparedKeycode = KeyEvent.KEYCODE_HOME;
                            }
                        }
                    } else if ((mNavigationBarPosition != 1 || rawX >= ((float) (mScreenHeight - mSwipeStartThreshold))) && (mNavigationBarPosition != 2 || rawX <= ((float) mSwipeStartThreshold))) {
                        mFromX = rawX;
                        mFromY = rawY;
                        if (mFromY < ((float) (mScreenWidth / 3)) || mFromY > ((float) ((mScreenWidth * 2) / 3))) {
                            mPreparedKeycode = KeyEvent.KEYCODE_BACK;
                        } else {
                            mPreparedKeycode = KeyEvent.KEYCODE_HOME;
                        }
                    }
                    if (mPreparedKeycode != -1) {
                        mLastY = mFromY;
                        mLastX = mFromX;
                        mDownTime = event.getEventTime();
                        mSwipeStartFromEdge = true;
                        mKeyEventHandled = false;
                        mRecentsTriggered = false;
                        mLongSwipePossible = false;
                        if (DEBUG) Slog.i(TAG, "ACTION_DOWN " + mPreparedKeycode);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!mIsKeyguardShowing && mPreparedKeycode != -1) {
                        if (DEBUG)
                            Slog.i(TAG, "ACTION_UP " + mPreparedKeycode + " " + mRecentsTriggered + " " + mKeyEventHandled + " " + mLongSwipePossible);
                        mGestureButtonHandler.removeMessages(MSG_SEND_SWITCH_KEY);
                        cancelPreloadRecentApps();

                        if (!mKeyEventHandled && mLongSwipePossible) {
                            if (mPreparedKeycode == KeyEvent.KEYCODE_HOME) {
                                if (!mDismissInputMethod) {
                                    dismissInputMethod();
                                }
                            }
                            mGestureButtonHandler.sendEmptyMessage(MSG_SEND_KEY);
                        }
                        mSwipeStartFromEdge = false;
                        mKeyEventHandled = false;
                        mRecentsTriggered = false;
                        mLongSwipePossible = false;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mKeyEventHandled && !mRecentsTriggered && !mIsKeyguardShowing && mPreparedKeycode != -1) {
                        float moveDistanceSinceDown;
                        if (mNavigationBarPosition == 0) {
                            moveDistanceSinceDown = Math.abs(mFromY - rawY);
                        } else {
                            moveDistanceSinceDown = Math.abs(mFromX - rawX);
                        }
                        float moveDistanceSinceLast;
                        if (mNavigationBarPosition == 0) {
                            moveDistanceSinceLast = Math.abs(mLastY - rawY);
                        } else {
                            moveDistanceSinceLast = Math.abs(mLastX - rawX);
                        }
                        long deltaSinceDown = event.getEventTime() - mDownTime;
                        if (mPreparedKeycode == KeyEvent.KEYCODE_HOME && moveDistanceSinceDown < mLongPressMaxLength) {
                            if (deltaSinceDown > GESTURE_KEY_LONG_CLICK_TIMEOUT) {
                                if (DEBUG) Slog.i(TAG, "long click: moveDistanceSinceDown = " + moveDistanceSinceDown);
                                mGestureButtonHandler.sendEmptyMessage(MSG_SEND_LONG_PRESS);
                            }
                        }

                        if (moveDistanceSinceDown > mSwipeMinLength) {
                            if (DEBUG) Slog.i(TAG, "swipe: moveDistanceSinceDown = " + moveDistanceSinceDown);
                            mLongSwipePossible = true;
                            if (mPreparedKeycode == KeyEvent.KEYCODE_BACK) {
                                // TODO should back be triggered already while move? so without up
                                //mGestureButtonHandler.sendEmptyMessage(MSG_SEND_KEY);
                            } else if (!mRecentsTriggered) {
                                // swipe comes to an stop
                                if (moveDistanceSinceLast < mRecentMoveTolerance) {
                                    mRecentsTriggered = true;
                                    preloadRecentApps();
                                    mGestureButtonHandler.removeMessages(MSG_SEND_SWITCH_KEY);
                                    mGestureButtonHandler.sendEmptyMessageDelayed(MSG_SEND_SWITCH_KEY, GESTURE_KEY_DISTANCE_TIMEOUT);
                                }
                            }
                        }
                        mLastX = rawX;
                        mLastY = rawY;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    break;
                default:
                    break;
            }
        }
    }

    private void toggleRecentApps() {
        mPwm.toggleRecentApps();
    }

    private void preloadRecentApps() {
        mPwm.preloadRecentApps();
    }

    private void cancelPreloadRecentApps() {
        mPwm.cancelPreloadRecentApps();
    }

    private void triggerGestureVirtualKeypress(int keyCode) {
        RRFWBUtils.sendKeycode(keyCode);
    }

    void navigationBarPosition(int displayWidth, int displayHeight, int displayRotation) {
        int navigationBarPosition = 0;
        if (displayWidth > displayHeight) {
            if (displayRotation == 3) {
                navigationBarPosition = 2;
            } else {
                navigationBarPosition = 1;
            }
        }
        if (navigationBarPosition != mNavigationBarPosition) {
            mNavigationBarPosition = navigationBarPosition;
        }
    }

    boolean isGestureButtonRegion(int x, int y) {
        boolean isregion = true;
        if (mNavigationBarPosition == 0) {
            if (y < mScreenHeight - mSwipeStartThreshold) {
                isregion = false;
            }
        } else if (mNavigationBarPosition == 1) {
            if (x < mScreenHeight - mSwipeStartThreshold) {
                isregion = false;
            }
        } else {
            if (x > mSwipeStartThreshold) {
                isregion = false;
            }
        }
        return isregion;
    }
}
