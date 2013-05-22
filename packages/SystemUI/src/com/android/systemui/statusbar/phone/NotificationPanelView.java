/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

import com.android.systemui.R;
import com.android.systemui.statusbar.GestureRecorder;

public class NotificationPanelView extends PanelView {

    private static final float STATUS_BAR_SETTINGS_FLIP_PERCENTAGE_RIGHT = 0.15f;
    private static final float STATUS_BAR_SETTINGS_FLIP_PERCENTAGE_LEFT = 0.85f;

    Drawable mHandleBar;
    int mHandleBarHeight;
    View mHandleView;
    int mFingers;
    PhoneStatusBar mStatusBar;
    boolean mOkToFlip;
    boolean mFastToggleEnabled;
    int mFastTogglePos;
    ContentObserver mEnableObserver;
    ContentObserver mChangeSideObserver;
    int mToggleStyle;
    Handler mHandler = new Handler();

    public NotificationPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setStatusBar(PhoneStatusBar bar) {
        mStatusBar = bar;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Resources resources = getContext().getResources();
        mHandleBar = resources.getDrawable(R.drawable.status_bar_close);
        mHandleBarHeight = resources.getDimensionPixelSize(R.dimen.close_handle_height);
        mHandleView = findViewById(R.id.handle);

        setContentDescription(resources.getString(
                R.string.accessibility_desc_notification_shade));

        final ContentResolver resolver = getContext().getContentResolver();
        mEnableObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                mFastToggleEnabled = Settings.System.getBoolean(resolver,
                        Settings.System.FAST_TOGGLE, false);
                mToggleStyle = Settings.System.getInt(resolver,
                        Settings.System.TOGGLES_STYLE, 0);
            }
        };

        mChangeSideObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                mFastTogglePos = Settings.System.getInt(resolver,
                        Settings.System.CHOOSE_FASTTOGGLE_SIDE, 1);
            }
        };

        // Initialization
        mFastToggleEnabled = Settings.System.getBoolean(resolver,
                Settings.System.FAST_TOGGLE, false);
        mFastTogglePos = Settings.System.getInt(resolver,
                Settings.System.CHOOSE_FASTTOGGLE_SIDE, 1);
        mToggleStyle = Settings.System.getInt(resolver,
                Settings.System.TOGGLES_STYLE, 0);

        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.FAST_TOGGLE),
                true, mEnableObserver);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.TOGGLES_STYLE),
                true, mEnableObserver);

        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.CHOOSE_FASTTOGGLE_SIDE),
                true, mChangeSideObserver);
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getContentResolver().unregisterContentObserver(mEnableObserver);
        getContext().getContentResolver().unregisterContentObserver(mChangeSideObserver);
        super.onDetachedFromWindow();
    }

    @Override
    public void fling(float vel, boolean always) {
        GestureRecorder gr =
                ((PhoneStatusBarView) mBar).mBar.getGestureRecorder();
        if (gr != null) {
            gr.tag(
                "fling " + ((vel > 0) ? "open" : "closed"),
                "notifications,v=" + vel);
        }
        super.fling(vel, always);
    }

    // We draw the handle ourselves so that it's
    // always glued to the bottom of the window.
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            final int pl = getPaddingLeft();
            final int pr = getPaddingRight();
            mHandleBar.setBounds(pl, 0, getWidth() - pr, (int) mHandleBarHeight);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        final int off = (int) (getHeight() - mHandleBarHeight - getPaddingBottom());
        canvas.translate(0, off);
        mHandleBar.setState(mHandleView.getDrawableState());
        mHandleBar.draw(canvas);
        canvas.translate(0, -off);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (PhoneStatusBar.SETTINGS_DRAG_SHORTCUT
                && mStatusBar.mHasFlipSettings) {
            boolean shouldFlip = false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mOkToFlip = getExpandedHeight() == 0;
                    if(mToggleStyle != 0) {
                        // don't allow settings panel with non-tile toggles
                        shouldFlip = false;
                        break;
                    }
                    if (mFastTogglePos == 1) {
                        if ((event.getX(0) > getWidth()
                                * (1.0f - STATUS_BAR_SETTINGS_FLIP_PERCENTAGE_RIGHT)
                                && mFastToggleEnabled)
                            || (mStatusBar.skipToSettingsPanel())
                                && !mFastToggleEnabled) {
                            shouldFlip = true;
                        }
                    } else if (mFastTogglePos == 2) {
                        if ((event.getX(0) < getWidth()
                                * (1.0f - STATUS_BAR_SETTINGS_FLIP_PERCENTAGE_LEFT)
                                && mFastToggleEnabled)
                            || (mStatusBar.skipToSettingsPanel())
                                && !mFastToggleEnabled) {
                            shouldFlip = true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mOkToFlip) {
                        float miny = event.getY(0);
                        float maxy = miny;
                        for (int i=1; i<event.getPointerCount(); i++) {
                            final float y = event.getY(i);
                            if (y < miny) miny = y;
                            if (y > maxy) maxy = y;
                        }
                        if (maxy - miny < mHandleBarHeight) {
                            shouldFlip = true;
                        }
                    }
                    break;
            }
            if(mOkToFlip && shouldFlip) {
                if (getMeasuredHeight() < mHandleBarHeight) {
                    mStatusBar.switchToSettings();
                } else {
                    mStatusBar.flipToSettings();
                }
                mOkToFlip = false;
            }
        }
        return mHandleView.dispatchTouchEvent(event);
    }
}
