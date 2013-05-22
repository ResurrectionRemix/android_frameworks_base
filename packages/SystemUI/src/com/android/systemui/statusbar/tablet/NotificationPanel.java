/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.tablet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.systemui.ExpandHelper;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.SettingsPanelView;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.toggles.ToggleManager;
import com.android.systemui.aokp.AokpSwipeRibbon;

public class NotificationPanel extends RelativeLayout implements StatusBarPanel,
        View.OnClickListener {
    private ExpandHelper mExpandHelper;
    private NotificationRowLayout latestItems;

    static final String TAG = "Tablet/NotificationPanel";
    static final boolean DEBUG = false;

    final static int PANEL_FADE_DURATION = 150;

    boolean mShowing;
    boolean mHasClearableNotifications = false;
    int mNotificationCount = 0;
    ImageView mSettingsButton;
    ImageView mNotificationButton;
    View mNotificationScroller;
    ViewGroup mContentFrame;
    Rect mContentArea = new Rect();
    ViewGroup mContentParent;
    TabletStatusBar mBar;
    View mClearButton;
    Interpolator mAccelerateInterpolator = new AccelerateInterpolator();
    Interpolator mDecelerateInterpolator = new DecelerateInterpolator();

    // settings
    ToggleManager mToggleManager;

    private AokpSwipeRibbon mAokpSwipeRibbonLeft;
    private AokpSwipeRibbon mAokpSwipeRibbonRight;
    private AokpSwipeRibbon mAokpSwipeRibbonBottom;
    boolean mHasSettingsPanel, mHasFlipSettings;
    int mToggleStyle;
    SettingsPanelView mSettingsPanel;
    View mFlipSettingsView;
    QuickSettingsContainerView mSettingsContainer;
    int mSettingsPanelGravity;
    boolean mNotificationPanelIsFullScreenWidth;

    // amount to slide mContentParent down by when mContentFrame is missing
    float mContentFrameMissingTranslation;

    Choreographer mChoreo = new Choreographer();

    final int FLIP_DURATION_OUT = 125;
    final int FLIP_DURATION_IN = 225;
    final int FLIP_DURATION = (FLIP_DURATION_IN + FLIP_DURATION_OUT);
    Animator mScrollViewAnim, mFlipSettingsViewAnim, mNotificationButtonAnim,
    mSettingsButtonAnim, mClearButtonAnim;

    public NotificationPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setBar(TabletStatusBar b) {
        mBar = b;
        // Since we are putting QuickSettings inside the NoticationPanel for TabletBar.
        // we can't set the services on inflate.  Need to wait until the StatusBar gets attached to
        // notification Panel.
        if (mToggleManager != null && mBar != null) {
            mToggleManager.setControllers(mBar.mBluetoothController,mBar.mNetworkController, mBar.mBatteryController,
                mBar.mLocationController, null);
            mToggleManager.updateSettings();
            mAokpSwipeRibbonLeft.setControllers(mBar.mBluetoothController,mBar.mNetworkController, mBar.mBatteryController,
                mBar.mLocationController, null);
            mAokpSwipeRibbonRight.setControllers(mBar.mBluetoothController,mBar.mNetworkController, mBar.mBatteryController,
                mBar.mLocationController, null);
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        setWillNotDraw(false);

        mContentParent = (ViewGroup)findViewById(R.id.content_parent);
        mContentParent.bringToFront();

        mNotificationScroller = findViewById(R.id.notification_scroller);
        mContentFrame = (ViewGroup)findViewById(R.id.content_frame);
        mContentFrameMissingTranslation = 0; // not needed with current assets

        // the "X" that appears in place of the clock when the panel is showing notifications
        mClearButton = findViewById(R.id.clear_all_button);
        mClearButton.setOnClickListener(mClearButtonListener);

        mShowing = false;

        // Let's force this to be a 'flip' settings screen for now.
        // we may figure out a way to use the dual panels later on.
        mHasSettingsPanel = true;
        mHasFlipSettings = true;
        mNotificationPanelIsFullScreenWidth = true;

        mSettingsButton = (ImageView) findViewById(R.id.settings_button);
        if (mSettingsButton != null) {
            mSettingsButton.setOnClickListener(mSettingsButtonListener);
            if (mHasSettingsPanel) {
                if (mNotificationPanelIsFullScreenWidth) {
                    // the settings panel is hiding behind this button
                    mSettingsButton.setImageResource(R.drawable.ic_notify_quicksettings);
                    mSettingsButton.setVisibility(View.VISIBLE);
                } else {
                    // there is a settings panel, but it's on the other side of the (large) screen
                    final View buttonHolder = findViewById(R.id.settings_button_holder);
                    if (buttonHolder != null) {
                        buttonHolder.setVisibility(View.GONE);
                    }
                }
            } else {
                // no settings panel, go straight to settings
                mSettingsButton.setVisibility(View.VISIBLE);
                mSettingsButton.setImageResource(R.drawable.ic_notify_settings);
            }
        }
        if (mHasFlipSettings) {
            mNotificationButton = (ImageView) findViewById(R.id.notification_button);
            if (mNotificationButton != null) {
                mNotificationButton.setOnClickListener(mNotificationButtonListener);
            }
        }
        // Quick Settings (where available, some restrictions apply)
        if (mHasSettingsPanel) {
            // first, figure out where quick settings should be inflated
            final View settings_stub;
            if (mHasFlipSettings) {
                // a version of quick settings that flips around behind the notifications
                settings_stub = findViewById(R.id.flip_settings_stub);
                if (settings_stub != null) {
                    mFlipSettingsView = ((ViewStub)settings_stub).inflate();
                    mFlipSettingsView.setVisibility(View.GONE);
                    mFlipSettingsView.setVerticalScrollBarEnabled(false);
                }
            } else {
                // full quick settings panel
                settings_stub = findViewById(R.id.quick_settings_stub);
                if (settings_stub != null) {
                    mSettingsPanel = (SettingsPanelView) ((ViewStub)settings_stub).inflate();
                } else {
                    mSettingsPanel = (SettingsPanelView) findViewById(R.id.settings_panel);
                }

                if (mSettingsPanel != null) {
                    if (!ActivityManager.isHighEndGfx()) {
                        mSettingsPanel.setBackground(new FastColorDrawable(mContext.getResources().getColor(
                                R.color.notification_panel_solid_background)));
                    }
                }
            }

            mSettingsContainer = (QuickSettingsContainerView)
                    findViewById(R.id.quick_settings_container);

            // wherever you find it, Quick Settings needs a container to survive
            mToggleManager = new ToggleManager(mContext);
            mAokpSwipeRibbonBottom = new AokpSwipeRibbon(mContext,null,"bottom");
            mAokpSwipeRibbonLeft = new AokpSwipeRibbon(mContext,null,"left");
            mAokpSwipeRibbonRight = new AokpSwipeRibbon(mContext,null,"right");
            mToggleStyle = Settings.System.getInt(mContext.getContentResolver(), 
                    Settings.System.TOGGLES_STYLE,ToggleManager.STYLE_TILE);
            if (mToggleStyle == ToggleManager.STYLE_SCROLLABLE) {
                mToggleManager.setContainer((LinearLayout) findViewById(R.id.quick_toggles),
                        ToggleManager.STYLE_SCROLLABLE);
            } else {
                mToggleManager.setContainer((LinearLayout) findViewById(R.id.quick_toggles),
                    ToggleManager.STYLE_TRADITIONAL);
            }
            mSettingsContainer = (QuickSettingsContainerView) findViewById(R.id.quick_settings_container);
            if (mSettingsContainer != null) {
                mToggleManager.setContainer(mSettingsContainer, ToggleManager.STYLE_TILE);
                if (!mNotificationPanelIsFullScreenWidth) {
                    mSettingsContainer.setSystemUiVisibility(
                            View.STATUS_BAR_DISABLE_NOTIFICATION_TICKER
                            | View.STATUS_BAR_DISABLE_SYSTEM_INFO);
                }
            }
            mToggleManager.updateSettings();
        }
    }

    @Override
    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        latestItems = (NotificationRowLayout) findViewById(R.id.content);
        int minHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_max_height);
        mExpandHelper = new ExpandHelper(mContext, latestItems, minHeight, maxHeight);
        mExpandHelper.setEventSource(this);
        mExpandHelper.setGravity(Gravity.BOTTOM);
    }

    private View.OnClickListener mClearButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            mBar.clearAll();
        }
    };

    private View.OnClickListener mSettingsButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mToggleManager != null && mToggleManager.shouldFlipToSettings()) {
                if (mHasSettingsPanel) {
                    animateExpandSettingsPanel();
                } else {
                    startActivityDismissingKeyguard(
                            new Intent(android.provider.Settings.ACTION_SETTINGS), true);
                }
            }
        }
    };

    private View.OnClickListener mNotificationButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mShowing) {
                flipToNotifications();
            }
        }
    };

    public View getClearButton() {
        return mClearButton;
    }

    public void show(boolean show, boolean animate) {
        if (animate) {
            if (mShowing != show) {
                mShowing = show;
                if (show) {
                    setVisibility(View.VISIBLE);
                    // Don't start the animation until we've created the layer, which is done
                    // right before we are drawn
                    mContentParent.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
                } else {
                    mChoreo.startAnimation(show);
                }
            }
        } else {
            mShowing = show;
            setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show && !mHasClearableNotifications) { // go to settings panel is no notifications
            flipToSettings();
        } else if(show) {
            flipToNotifications();
        }
    }

    /**
     * This is used only when we've created a hardware layer and are waiting until it's
     * been created in order to start the appearing animation.
     */
    private ViewTreeObserver.OnPreDrawListener mPreDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mChoreo.startAnimation(true);
            return false;
        }
    };

    /**
     * Whether the panel is showing, or, if it's animating, whether it will be
     * when the animation is done.
     */
    public boolean isShowing() {
        return mShowing;
    }

    @Override
    public void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        // when we hide, put back the notifications
        if (vis != View.VISIBLE) {
            mNotificationScroller.setVisibility(View.VISIBLE);
            mNotificationScroller.setAlpha(1f);
            mNotificationScroller.scrollTo(0, 0);
        }
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Ignore hover events outside of this panel bounds since such events
        // generate spurious accessibility events with the panel content when
        // tapping outside of it, thus confusing the user.
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            return super.dispatchHoverEvent(event);
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    final int keyCode = event.getKeyCode();
        switch (keyCode) {
            // We exclusively handle the back key by hiding this panel.
            case KeyEvent.KEYCODE_BACK: {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mBar.animateCollapsePanels();
                }
                return true;
            }
            // We react to the home key but let the system handle it.
            case KeyEvent.KEYCODE_HOME: {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mBar.animateCollapsePanels();
                }
            } break;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onClick(View v) {  
    }

    public void setNotificationCount(int n) {
        mNotificationCount = n;
    }

    public void setContentFrameVisible(final boolean showing, boolean animate) {
    }

    public void updateClearButton() {
        if (mBar != null) {
            final boolean showX 
                = (isShowing()
                        && mHasClearableNotifications
                        && mNotificationScroller.getVisibility() == View.VISIBLE);
            getClearButton().setVisibility(showX ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void setClearable(boolean clearable) {
        mHasClearableNotifications = clearable;
    }

    public boolean isInContentArea(int x, int y) {
        mContentArea.left = mContentFrame.getLeft() + mContentFrame.getPaddingLeft();
        mContentArea.top = mContentFrame.getTop() + mContentFrame.getPaddingTop()
            + (int)mContentParent.getTranslationY(); // account for any adjustment
        mContentArea.right = mContentFrame.getRight() - mContentFrame.getPaddingRight();
        mContentArea.bottom = mContentFrame.getBottom() - mContentFrame.getPaddingBottom();

        offsetDescendantRectToMyCoords(mContentParent, mContentArea);
        return mContentArea.contains(x, y);
    }

    public void startActivityDismissingKeyguard(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !mBar.isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        mBar.animateCollapsePanels();
    }

    public void animateExpandSettingsPanel() {
        if ((mBar.mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return;
        }

        if (mHasFlipSettings) {
            show(true,true);
            if (mFlipSettingsView.getVisibility() != View.VISIBLE) {
                flipToSettings();
            }
        } else if (mSettingsPanel != null) {
            mSettingsPanel.expand();
        }
    }

    public void switchToSettings() {
        if(mToggleManager != null && mToggleManager.getStyle() != ToggleManager.STYLE_TILE) {
            return;
        }
        mFlipSettingsView.setScaleX(1f);
        mFlipSettingsView.setVisibility(View.VISIBLE);
        mSettingsButton.setVisibility(View.GONE);
        mNotificationScroller.setVisibility(View.GONE);
        mNotificationScroller.setScaleX(0f);
        mNotificationButton.setVisibility(View.VISIBLE);
        mNotificationButton.setAlpha(1f);
        mClearButton.setVisibility(View.GONE);
    }

    public void flipToSettings() {
        if(mToggleManager != null && mToggleManager.getStyle() != ToggleManager.STYLE_TILE) {
            return;
        }
        if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
        if (mScrollViewAnim != null) mScrollViewAnim.cancel();
        if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
        if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
        if (mClearButtonAnim != null) mClearButtonAnim.cancel();

        mFlipSettingsView.setVisibility(View.VISIBLE);
        mFlipSettingsView.setScaleX(0f);
        mFlipSettingsViewAnim = start(
            startDelay(FLIP_DURATION_OUT,
                interpolator(mDecelerateInterpolator,
                    ObjectAnimator.ofFloat(mFlipSettingsView, View.SCALE_X, 0f, 1f)
                        .setDuration(FLIP_DURATION_IN)
                    )));
        mScrollViewAnim = start(
            setVisibilityWhenDone(
                interpolator(mAccelerateInterpolator,
                        ObjectAnimator.ofFloat(mNotificationScroller, View.SCALE_X, 1f, 0f)
                        )
                    .setDuration(FLIP_DURATION_OUT), 
                    mNotificationScroller, View.INVISIBLE));
        mSettingsButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA, 0f)
                    .setDuration(FLIP_DURATION),
                    mNotificationScroller, View.INVISIBLE));
        mNotificationButton.setVisibility(View.VISIBLE);
        mNotificationButtonAnim = start(
            ObjectAnimator.ofFloat(mNotificationButton, View.ALPHA, 1f)
                .setDuration(FLIP_DURATION));
        mClearButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mClearButton, View.ALPHA, 0f)
                .setDuration(FLIP_DURATION),
                mClearButton, View.INVISIBLE));
    }

    public void flipPanels() {
        if (mHasFlipSettings) {
            if (mFlipSettingsView.getVisibility() != View.VISIBLE) {
                flipToSettings();
            } else {
                flipToNotifications();
            }
        }
    }

    public void animateCollapseQuickSettings() {
        mBar.mStatusBarView.collapseAllPanels(true);
    }

    public void flipToNotifications() {
        if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
        if (mScrollViewAnim != null) mScrollViewAnim.cancel();
        if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
        if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
        if (mClearButtonAnim != null) mClearButtonAnim.cancel();

        mNotificationScroller.setVisibility(View.VISIBLE);
        mScrollViewAnim = start(
            startDelay(FLIP_DURATION_OUT,
                interpolator(mDecelerateInterpolator,
                    ObjectAnimator.ofFloat(mNotificationScroller, View.SCALE_X, 0f, 1f)
                        .setDuration(FLIP_DURATION_IN)
                    )));
        mFlipSettingsViewAnim = start(
            setVisibilityWhenDone(
                interpolator(mAccelerateInterpolator,
                        ObjectAnimator.ofFloat(mFlipSettingsView, View.SCALE_X, 1f, 0f)
                        )
                    .setDuration(FLIP_DURATION_OUT),
                mFlipSettingsView, View.INVISIBLE));
        mNotificationButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mNotificationButton, View.ALPHA, 0f)
                    .setDuration(FLIP_DURATION),
                mNotificationButton, View.INVISIBLE));
        mSettingsButton.setVisibility(View.VISIBLE);
        mSettingsButtonAnim = start(
            ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA, 1f)
                .setDuration(FLIP_DURATION));
        mClearButton.setVisibility(View.VISIBLE);
        updateClearButton();
        mBar.setAreThereNotifications(); // this will show/hide the button as necessary
    }

    public ViewPropertyAnimator setVisibilityWhenDone(
            final ViewPropertyAnimator a, final View v, final int vis) {
        a.setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(vis);
                a.setListener(null); // oneshot
            }
        });
        return a;
    }

    public Animator setVisibilityWhenDone(
            final Animator a, final View v, final int vis) {
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(vis);
            }
        });
        return a;
    }

    public Animator interpolator(TimeInterpolator ti, Animator a) {
        a.setInterpolator(ti);
        return a;
    }

    public Animator startDelay(int d, Animator a) {
        a.setStartDelay(d);
        return a;
    }
    
    public Animator start(Animator a) {
        a.start();
        return a;
    }

    private class Choreographer implements Animator.AnimatorListener {
        boolean mVisible;
        int mPanelHeight;
        AnimatorSet mContentAnim;

        // should group this into a multi-property animation
        final static int OPEN_DURATION = 250;
        final static int CLOSE_DURATION = 250;

        // the panel will start to appear this many px from the end
        final int HYPERSPACE_OFFRAMP = 200;

        Choreographer() {
        }

        void createAnimation(boolean appearing) {
            // mVisible: previous state; appearing: new state
            
            float start, end;

            // 0: on-screen
            // height: off-screen
            float y = mContentParent.getTranslationY();
            if (appearing) {
                // we want to go from near-the-top to the top, unless we're half-open in the right
                // general vicinity
                end = 0;
                if (mNotificationCount == 0) {
                    end += mContentFrameMissingTranslation;
                }
                start = HYPERSPACE_OFFRAMP+end;
            } else {
                start = y;
                end = y + HYPERSPACE_OFFRAMP;
            }

            Animator posAnim = ObjectAnimator.ofFloat(mContentParent, "translationY",
                    start, end);
            posAnim.setInterpolator(appearing ? mDecelerateInterpolator : mAccelerateInterpolator);

            if (mContentAnim != null && mContentAnim.isRunning()) {
                mContentAnim.cancel();
            }

            Animator fadeAnim = ObjectAnimator.ofFloat(mContentParent, "alpha",
                    appearing ? 1.0f : 0.0f);
            fadeAnim.setInterpolator(appearing ? mAccelerateInterpolator : mDecelerateInterpolator);

            mContentAnim = new AnimatorSet();
            mContentAnim
                .play(fadeAnim)
                .with(posAnim)
                ;
            mContentAnim.setDuration((DEBUG?10:1)*(appearing ? OPEN_DURATION : CLOSE_DURATION));
            mContentAnim.addListener(this);
        }

        void startAnimation(boolean appearing) {
            if (DEBUG) Slog.d(TAG, "startAnimation(appearing=" + appearing + ")");

            createAnimation(appearing);
            mContentAnim.start();

            mVisible = appearing;

            // we want to start disappearing promptly
            if (!mVisible) updateClearButton();
        }

        public void onAnimationCancel(Animator animation) {
            if (DEBUG) Slog.d(TAG, "onAnimationCancel");
        }

        public void onAnimationEnd(Animator animation) {
            if (DEBUG) Slog.d(TAG, "onAnimationEnd");
            if (! mVisible) {
                setVisibility(View.GONE);
            }
            mContentParent.setLayerType(View.LAYER_TYPE_NONE, null);
            mContentAnim = null;

            // we want to show the X lazily
            if (mVisible) updateClearButton();
        }

        public void onAnimationRepeat(Animator animation) {
        }

        public void onAnimationStart(Animator animation) {
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        MotionEvent cancellation = MotionEvent.obtain(ev);
        cancellation.setAction(MotionEvent.ACTION_CANCEL);

        boolean intercept = mExpandHelper.onInterceptTouchEvent(ev) ||
                super.onInterceptTouchEvent(ev);
        if (intercept) {
            latestItems.onInterceptTouchEvent(cancellation);
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = mExpandHelper.onTouchEvent(ev) ||
                super.onTouchEvent(ev);
        return handled;
    }

    public void setSettingsEnabled(boolean settingsEnabled) {
        if (mSettingsButton != null) {
            mSettingsButton.setEnabled(settingsEnabled);
            mSettingsButton.setVisibility(settingsEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private static class FastColorDrawable extends Drawable {
        private final int mColor;

        public FastColorDrawable(int color) {
            mColor = 0xff000000 | color;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(mColor, PorterDuff.Mode.SRC);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
        }

        @Override
        public void setBounds(Rect bounds) {
        }
    }
}
