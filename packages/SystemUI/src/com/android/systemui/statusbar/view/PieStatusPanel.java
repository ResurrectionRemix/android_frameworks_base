/*
 * Copyright (C) 2010 ParanoidAndroid Project
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

package com.android.systemui.statusbar.view;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.view.animation.DecelerateInterpolator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.PieControlPanel;

import java.util.ArrayList;
import java.util.List;

public class PieStatusPanel {

    public static final int NOTIFICATIONS_PANEL = 0;
    public static final int QUICK_SETTINGS_PANEL = 1;

    private Context mContext;
    private ScrollView mScrollView;
    private View mContentFrame;
    private QuickSettingsContainerView mQS;
    private NotificationRowLayout mNotificationPanel;
    private PieControlPanel mPanel;
    private ViewGroup[] mPanelParents = new ViewGroup[2];

    private int mCurrentViewState = -1;
    private int mFlipViewState = -1;

    public PieStatusPanel(Context context, PieControlPanel panel) {
        mContext = context;
        mPanel = panel;

        mNotificationPanel = mPanel.getBar().getNotificationRowLayout();
        mNotificationPanel.setTag(NOTIFICATIONS_PANEL);
        mQS = mPanel.getBar().getQuickSettingsPanel();
        mQS.setTag(QUICK_SETTINGS_PANEL);

        mPanelParents[NOTIFICATIONS_PANEL] = (ViewGroup) mNotificationPanel.getParent();
        mPanelParents[QUICK_SETTINGS_PANEL] = (ViewGroup) mQS.getParent();

        mContentFrame = (View) mPanel.getBar().mContainer.findViewById(R.id.content_frame);
        mScrollView = (ScrollView) mPanel.getBar().mContainer.findViewById(R.id.content_scroll);
        mScrollView.setOnTouchListener(new ViewOnTouchListener());
        mContentFrame.setOnTouchListener(new ViewOnTouchListener());

        mPanel.getBar().mContainer.setVisibility(View.GONE);
    }

    class ViewOnTouchListener implements OnTouchListener {
        final int SCROLLING_DISTANCE_TRIGGER = 100;
            float scrollX;
            float scrollY;
            boolean hasScrolled;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        scrollX = event.getX();
                        scrollY = event.getY();
                        hasScrolled = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float distanceY = Math.abs(event.getY() - scrollY);
                        float distanceX = Math.abs(event.getX() - scrollX);
                        if(distanceY > SCROLLING_DISTANCE_TRIGGER ||
                            distanceX > SCROLLING_DISTANCE_TRIGGER) {
                            hasScrolled = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if(!hasScrolled) {
                            hidePanels(true);
                        }
                        break;
                }
                return false;
            }                  
    }

    public int getFlipViewState() {
        return mFlipViewState;
    }

    public void setFlipViewState(int state) {
        mFlipViewState = state;
    }

    public int getCurrentViewState() {
        return mCurrentViewState;
    }

    public void setCurrentViewState(int state) {
        mCurrentViewState = state;
    }

    public void hidePanels(boolean reset) {
        if (mCurrentViewState == NOTIFICATIONS_PANEL) {
            hidePanel(mNotificationPanel);
        } else if (mCurrentViewState == QUICK_SETTINGS_PANEL) {
            hidePanel(mQS);
        }
        if (reset) mCurrentViewState = -1;
    }

    public void swapPanels() {
        hidePanels(false);
        if (mCurrentViewState == NOTIFICATIONS_PANEL) {
            mCurrentViewState = QUICK_SETTINGS_PANEL;
            showPanel(mQS);
        } else if (mCurrentViewState == QUICK_SETTINGS_PANEL) {
            mCurrentViewState = NOTIFICATIONS_PANEL;
            showPanel(mNotificationPanel);
        }
    }

    private ViewGroup getPanelParent(View panel) {
        if (((Integer)panel.getTag()).intValue() == NOTIFICATIONS_PANEL) {
            return mPanelParents[NOTIFICATIONS_PANEL];
        } else {
            return mPanelParents[QUICK_SETTINGS_PANEL];
        }
    }

    public void showTilesPanel() {
        showPanel(mQS);
    }

    public void showNotificationsPanel() {
        showPanel(mNotificationPanel);
    }

    public void hideTilesPanel() {
        hidePanel(mQS);
    }

    public void hideNotificationsPanel() {
        hidePanel(mNotificationPanel);
    }

    private void showPanel(View panel) {
        mContentFrame.setBackgroundColor(0);
        ValueAnimator alphAnimation  = ValueAnimator.ofInt(0, 1);
        alphAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mScrollView.setX(-(int)((1-animation.getAnimatedFraction()) * mPanel.getWidth() * 1.5));
                mContentFrame.setBackgroundColor((int)(animation.getAnimatedFraction() * 0xEE) << 24);
                mPanel.invalidate();
            }
        });
        alphAnimation.setDuration(600);
        alphAnimation.setInterpolator(new DecelerateInterpolator());
        alphAnimation.start();

        ViewGroup parent = getPanelParent(panel);
        parent.removeAllViews();
        mScrollView.removeAllViews();
        mScrollView.addView(panel);
        updateContainer(true);
    }

    private void hidePanel(View panel) {
        ViewGroup parent = getPanelParent(panel);
        mScrollView.removeAllViews();
        parent.removeAllViews();
        parent.addView(panel, panel.getLayoutParams());
        updateContainer(false);
    }

    private void updateContainer(boolean visible) {
        mPanel.getBar().mContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        updatePanelConfiguration();
    }

    public void updatePanelConfiguration() {
        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.pie_panel_padding);
        mScrollView.setPadding(padding,0,padding,0);
    }

    public static WindowManager.LayoutParams getFlipPanelLayoutParams() {
        return new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
    }
}
