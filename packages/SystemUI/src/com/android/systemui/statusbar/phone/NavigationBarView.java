/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import android.view.animation.AccelerateInterpolator;
import android.view.ContextThemeWrapper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.GestureDetector;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.BaseStatusBar;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import cyanogenmod.providers.CMSettings;

public class NavigationBarView extends LinearLayout implements BaseStatusBar.NavigationBarCallback {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    // NavBar Power Button
    private OnLongClickListener mPowerListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            ((KeyButtonView) v).sendEvent(KeyEvent.KEYCODE_POWER, KeyEvent.FLAG_LONG_PRESS);
            return true;
        }
    };

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;
    boolean mLeftInLandscape;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private Drawable mBackIcon, mBackLandIcon, mBackAltIcon, mBackAltLandIcon;
    private Drawable mRecentIcon;
    private Drawable mRecentLandIcon;
    private Drawable mHomeIcon, mHomeLandIcon;
    private Drawable mRecentAltIcon, mRecentAltLandIcon;

    private NavigationBarViewTaskSwitchHelper mTaskSwitchHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    /**
     * Tracks the current visibilities of the far left (R.id.one) and right (R.id.sev) buttons
     * while dpad arrow keys are visible.
     *
     * We keep track of the orientations separately because they can get in different states,
     * We can be showing dpad arrow keys on vertical, but on portrait that may not be so.
     */
    public int[][] mSideButtonVisibilities = new int[][] {
        {-1, -1} /* portrait */, {-1, -1} /* vertical */
    };


    // Navbar dim
    private boolean mDimNavButtons;
    private int mDimNavButtonsTimeout;
    private float mDimNavButtonsAlpha = 0.5f;
    private float mOriginalAlpha = 1.0f;
    private boolean mIsDim = false;
    private boolean mIsAnimating = false;
    private boolean mDimNavButtonsAnimate;
    private int mDimNavButtonsAnimateDuration;
    private boolean mDimNavButtonsTouchAnywhere;
    private PowerManager mPowerManager;
    private boolean mIsPowerSaveMode = false;
    private ObjectAnimator mFadeOut;
    private boolean mIsExpandedDesktopOn;
    private KeyguardManager mKgm;
    private boolean mIsHandlerCallbackActive = false;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    final static String NAVBAR_EDIT_ACTION = "android.intent.action.NAVBAR_EDIT";

    private boolean mInEditMode;
    private NavbarEditor mEditBar;
    private NavBarReceiver mNavBarReceiver;
    private OnClickListener mRecentsClickListener;
    private OnTouchListener mRecentsPreloadListener;
    private OnTouchListener mHomeSearchActionListener;
    private OnLongClickListener mRecentsBackListener;
    private OnLongClickListener mLongPressHomeListener;
    private OnClickListener mNotificationsClickListener;
    private OnLongClickListener mNotificationsLongListener;
    private OnClickListener mTorchClickListener;
    private OnClickListener mCameraClickListener;
    private OnLongClickListener mCameraLongClickListener;
    private OnClickListener mScreenShotClickListener;
    private OnClickListener mImmersiveClickListener;	
    private OnClickListener mConfigClickListener;
    private OnLongClickListener mConfigLongListener;	
    private OnClickListener mPieClickListener;
    private OnClickListener mScreenClickListener;	
    private OnClickListener mKillClickListener;	
    private OnClickListener mAppPickerClickListener;

    private SettingsObserver mSettingsObserver;
    private boolean mShowDpadArrowKeys;


    private boolean mNavButtonsRotation;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private Resources mThemedResources;

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mIsLayoutRtl;
    private boolean mLayoutTransitionsEnabled = true;
    private boolean mWakeAndUnlocking;

    private GestureDetector mDoubleTapGesture;
    private boolean mDoubleTapToSleep;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (NavbarEditor.NAVBAR_BACK.equals(view.getTag())) {
                mBackTransitioning = true;
            } else if (NavbarEditor.NAVBAR_HOME.equals(view.getTag()) && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = true;
                mStartDelay = transition.getStartDelay(transitionType);
                mDuration = transition.getDuration(transitionType);
                mInterpolator = transition.getInterpolator(transitionType);
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (NavbarEditor.NAVBAR_BACK.equals(view.getTag())) {
                mBackTransitioning = false;
            } else if (NavbarEditor.NAVBAR_HOME.equals(view.getTag()) && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = false;
            }

            if (NavbarEditor.NAVBAR_HOME.equals(view.getTag()))
            onNavButtonTouched();
        }

        public void onBackAltCleared() {
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker(true /* showAuxiliarySubtypes */);
        }
    };

    // provides a listener for the empty space in the navbar
    private final OnTouchListener mNavButtonsTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
 	if (mDoubleTapToSleep) {
                     mDoubleTapGesture.onTouchEvent(event);
                }
                onNavButtonTouched();
            return true;
        }
    };

    public void onNavButtonTouched() {
        if (mIsHandlerCallbackActive) {
            mHandler.removeCallbacks(mNavButtonDimmer);
            mIsHandlerCallbackActive = false;
        }

        // power saving mode is on, do nothing
        if (mIsPowerSaveMode) return;

        final ViewGroup navButtons = getNavButtons();
        if (navButtons != null) {
            // restore alpha to previous state first
            if (mIsDim || mIsAnimating) {
                mIsAnimating = false;
                 resetDim(navButtons);
            }
            if (mDimNavButtons && !mIsExpandedDesktopOn &&
                    !(mKgm != null ? mKgm.isDeviceLocked() : false)) {
                mHandler.postDelayed(mNavButtonDimmer, mDimNavButtonsTimeout);
                mIsHandlerCallbackActive = true;
            }
         }
    }

    private void resetDim(ViewGroup navButtons) {
        if (navButtons == null) {
            navButtons = getNavButtons();
        }
        if (navButtons != null) {
            if (mFadeOut != null) {
                mFadeOut.cancel();
            }
            mIsDim = false;
            navButtons.setAlpha(mOriginalAlpha);
        }
    }

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    // broadcast receiver for power saving mode
    private final BroadcastReceiver mBatteryDimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Log.d(TAG, "Broadcast received: " + intent.getAction());
            }

            mIsPowerSaveMode = mPowerManager.isPowerSaveMode();
            if (mIsPowerSaveMode) {
                // battery is low, no dim until charged
                resetDim(null);
            }
            onNavButtonTouched();
        }
    };

    private final IntentFilter mBatteryFilter = new IntentFilter(
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGING);

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mTaskSwitchHelper = new NavigationBarViewTaskSwitchHelper(context);

        getIcons(res);

        mBarTransitions = new NavigationBarTransitions(this);


        mNavBarReceiver = new NavBarReceiver();
        getContext().registerReceiver(mNavBarReceiver, new IntentFilter(NAVBAR_EDIT_ACTION));
        mSettingsObserver = new SettingsObserver(new Handler());

        mDoubleTapGesture = new GestureDetector(mContext,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) pm.goToSleep(e.getEventTime());
                return true;
            }
        });

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mIsPowerSaveMode = mPowerManager.isPowerSaveMode();

        mKgm = (KeyguardManager)
                mContext.getSystemService(Context.KEYGUARD_SERVICE);
   }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }
        mSettingsObserver.observe();
        mContext.registerReceiver(mBatteryDimReceiver, mBatteryFilter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mBatteryDimReceiver);
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setBar(PhoneStatusBar phoneStatusBar) {
        mTaskSwitchHelper.setBar(phoneStatusBar);
    }

    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
	if (mDoubleTapToSleep) {
            mDoubleTapGesture.onTouchEvent(event);
        }
        if (!mInEditMode && mTaskSwitchHelper.onTouchEvent(event)) {
            return true;
        }
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        if (mDimNavButtonsTouchAnywhere) {
            onNavButtonTouched();
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return !mInEditMode && mTaskSwitchHelper.onInterceptTouchEvent(event);
    }

    public void abortCurrentGesture() {
        getHomeButton().abortCurrentGesture();
    }

    private final H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
    }

    public View getMenuButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_CONDITIONAL_MENU);
    }

    public View getBackButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_BACK);
    }

    public KeyButtonView getHomeButton() {
        return (KeyButtonView) mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
    }

    public View getImeSwitchButton() {
        return mCurrentView.findViewById(R.id.ime_switcher);
    }

    public ViewGroup getNavButtons() {
        return (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
    }

    private void getIcons(Resources res) {
        mBackIcon = res.getDrawable(R.drawable.ic_sysbar_back);
        mBackLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_land);
        mBackAltIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
        mBackAltLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime_land);
        mRecentIcon = res.getDrawable(R.drawable.ic_sysbar_recent);
        mRecentLandIcon = res.getDrawable(R.drawable.ic_sysbar_recent_land);
        mHomeIcon = res.getDrawable(R.drawable.ic_sysbar_home);
	    mHomeLandIcon = res.getDrawable(R.drawable.ic_sysbar_home_land);
        mRecentAltIcon = res.getDrawable(R.drawable.ic_sysbar_clearall_ime);
        mRecentAltLandIcon = res.getDrawable(R.drawable.ic_sysbar_clearall_land);
    }

    public void updateResources(Resources res) {
        mThemedResources = res;
        getIcons(mThemedResources);
        mBarTransitions.updateResources(res);
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            if (container != null) {
                updateLightsOutResources(container);
            }
        }
        if (mEditBar != null) {
            mEditBar.updateResources(res);
        }
    }

    private void updateLightsOutResources(ViewGroup container) {
        ViewGroup lightsOut = (ViewGroup) container.findViewById(R.id.lights_out);
        if (lightsOut != null) {
            final int nChildren = lightsOut.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                final View child = lightsOut.getChildAt(i);
                if (child instanceof ImageView) {
                    final ImageView iv = (ImageView) child;
                    // clear out the existing drawable, this is required since the
                    // ImageView keeps track of the resource ID and if it is the same
                    // it will not update the drawable.
                    iv.setImageDrawable(null);
                    iv.setImageDrawable(mThemedResources.getDrawable(
                            R.drawable.ic_sysbar_lights_out_dot_large));
                }
            }
        }
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(getResources());

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        ((ImageView)getBackButton()).setImageDrawable(backAlt
                ? (mVertical ? mBackAltLandIcon : mBackAltIcon)
                : (mVertical ? mBackLandIcon : mBackIcon));

        ((ImageView)getRecentsButton()).setImageDrawable(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_RECENT_ALT))
                            ? (mVertical ? mRecentAltLandIcon : mRecentAltIcon)
                            : (mVertical ? mRecentLandIcon : mRecentIcon));
        ((ImageView)getHomeButton()).setImageDrawable(mVertical ? mHomeLandIcon : mHomeIcon);

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0)
                                && !mShowDpadArrowKeys;
        getImeSwitchButton().setVisibility(showImeButton ? View.INVISIBLE : View.INVISIBLE);

        setDisabledFlags(mDisabledFlags, true);

        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);

        if (mShowDpadArrowKeys) { // overrides IME button
            final boolean showingIme = ((mNavigationIconHints
                    & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0);

            setVisibleOrGone(getCurrentView().findViewById(R.id.dpad_left), showingIme);
            setVisibleOrGone(getCurrentView().findViewById(R.id.dpad_right), showingIme);

            View one = getCurrentView().findViewById(mVertical ? R.id.sev : R.id.one);
            View sev = getCurrentView().findViewById(mVertical ? R.id.one : R.id.sev);
            if (showingIme) {
                if (one.getVisibility() != View.GONE) {
                    setSideButtonVisibility(true, one.getVisibility());
                    setVisibleOrGone(one, false);
                }

                if (sev.getVisibility() != View.GONE) {
                    setSideButtonVisibility(false, sev.getVisibility());
                    setVisibleOrGone(sev, false);
                }
            } else {
                if (getSideButtonVisibility(true) != -1) {
                    one.setVisibility(getSideButtonVisibility(true));
                    setSideButtonVisibility(true, - 1);
                }
                if (getSideButtonVisibility(false) != -1) {
                    sev.setVisibility(getSideButtonVisibility(false));
                    setSideButtonVisibility(false, -1);
                }
            }
        }
    }

    private int getSideButtonVisibility(boolean left) {
        return mSideButtonVisibilities[mVertical ? 1 : 0][left ? 0 : 1];
    }

    private void setSideButtonVisibility(boolean left, int vis) {
        mSideButtonVisibilities[mVertical ? 1 : 0][left ? 0 : 1] = vis;
    }

    public int getNavigationIconHints() {
        return mNavigationIconHints;
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

         final ViewGroup navButtons = getNavButtons();
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
            }
        }
        if (inLockTask() && disableRecent && !disableHome) {
            // Don't hide recents when in lock task, it is used for exiting.
            // Unless home is hidden, then in DPM locked mode and no exit available.
            disableRecent = false;
        }

        setButtonWithTagVisibility(NavbarEditor.NAVBAR_BACK, !disableBack);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_HOME, !disableHome);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_RECENT, !disableRecent);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_POWER, !disableRecent);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_NOTIFICATIONS, !disableRecent);
	setButtonWithTagVisibility(NavbarEditor.NAVBAR_TORCH, !disableRecent);
	setButtonWithTagVisibility(NavbarEditor.NAVBAR_CAMERA, !disableRecent);
	setButtonWithTagVisibility(NavbarEditor.NAVBAR_SCREENSHOT, !disableRecent);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_APP_PICKER, !disableRecent);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_SEARCH, !disableSearch);
    }

    private boolean inLockTask() {
        try {
            return ActivityManagerNative.getDefault().isInLockTaskMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setLayoutTransitionsEnabled(boolean enabled) {
        mLayoutTransitionsEnabled = enabled;
        updateLayoutTransitionsEnabled();
    }

    public void setWakeAndUnlocking(boolean wakeAndUnlocking) {
        setUseFadingAnimations(wakeAndUnlocking);
        mWakeAndUnlocking = wakeAndUnlocking;
        updateLayoutTransitionsEnabled();
    }

    private void updateLayoutTransitionsEnabled() {
        boolean enabled = !mWakeAndUnlocking && mLayoutTransitionsEnabled;
        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        LayoutTransition lt = navButtons.getLayoutTransition();
        if (lt != null) {
            if (enabled) {
                lt.enableTransitionType(LayoutTransition.APPEARING);
                lt.enableTransitionType(LayoutTransition.DISAPPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            } else {
                lt.disableTransitionType(LayoutTransition.APPEARING);
                lt.disableTransitionType(LayoutTransition.DISAPPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            }
        }
    }

    private void setUseFadingAnimations(boolean useFadingAnimations) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean old = lp.windowAnimations != 0;
            if (!old && useFadingAnimations) {
                lp.windowAnimations = R.style.Animation_NavigationBarFadeIn;
            } else if (old && !useFadingAnimations) {
                lp.windowAnimations = 0;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
        final boolean shouldShowAlwaysMenu = (mNavigationIconHints &
                StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0;
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_ALWAYS_MENU, shouldShowAlwaysMenu);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_CONDITIONAL_MENU, shouldShow);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_SEARCH, shouldShowAlwaysMenu);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_POWER, shouldShowAlwaysMenu);
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] =
                mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);
        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);
        mRotatedViews[Surface.ROTATION_270] = mRotatedViews[Surface.ROTATION_90];
        mCurrentView = mRotatedViews[Surface.ROTATION_0];

        if (getImeSwitchButton() != null)
            getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);
        if (mDimNavButtons) {
            final ViewGroup navButtons = getNavButtons();
            if (navButtons != null)
                navButtons.setOnTouchListener(mNavButtonsTouchListener);  
        }

        updateRTLOrder();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mLeftInLandscape = leftInLandscape;
        mDeadZone.setStartFromRight(leftInLandscape);
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);

        updateLayoutTransitionsEnabled();

        if (NavbarEditor.isDevicePhone(getContext())) {
            int rotation = mDisplay.getRotation();
            mVertical = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        } else {
            mVertical = getWidth() > 0 && getHeight() > getWidth();
        }
        mEditBar = new NavbarEditor(mCurrentView, mVertical, mIsLayoutRtl, getResources());
        updateSettings();

        if (mDimNavButtons) {
            final ViewGroup navButtons = getNavButtons();
            if (navButtons != null)
                navButtons.setOnTouchListener(mNavButtonsTouchListener);
        }

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);
        mDeadZone.setStartFromRight(mLeftInLandscape);

        // force the low profile & disabled states into compliance
        mBarTransitions.init();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        updateTaskSwitchHelper();

        setNavigationIconHints(mNavigationIconHints, true);

        onNavButtonTouched();
    }

    private void updateTaskSwitchHelper() {
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mTaskSwitchHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRTLOrder();
        updateTaskSwitchHelper();
    }

    /**
     * In landscape, the LinearLayout is not auto mirrored since it is vertical. Therefore we
     * have to do it manually
     */
    private void updateRTLOrder() {
        boolean isLayoutRtl = getResources().getConfiguration()
                .getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mIsLayoutRtl != isLayoutRtl) {
            mIsLayoutRtl = isLayoutRtl;
            reorient();
        }
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                            + " " + visibilityToString(button.getVisibility())
                            + " alpha=" + button.getAlpha()
            );
        }
        pw.println();
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

    void setListeners(OnClickListener recentsClickListener, OnTouchListener recentsPreloadListener,
                      OnLongClickListener recentsBackListener, OnTouchListener homeSearchActionListener,
                      OnLongClickListener longPressHomeListener, OnClickListener notificationsClickListener,
                      OnLongClickListener notificationsLongListener,OnClickListener torchClickListener ,  OnClickListener cameraClickListener ,  OnLongClickListener cameraLongClickListener ,OnClickListener screenshotClickListener ,OnClickListener immersiveClickListener , OnClickListener configClickListener , OnLongClickListener configLongListener ,OnClickListener pieClickListener , OnClickListener screenClickListener, OnClickListener killClickListener ,OnClickListener appPickerClickListener ) {
        mRecentsClickListener = recentsClickListener;
        mRecentsPreloadListener = recentsPreloadListener;
        mHomeSearchActionListener = homeSearchActionListener;
        mRecentsBackListener = recentsBackListener;
        mLongPressHomeListener = longPressHomeListener;
	mNotificationsClickListener = notificationsClickListener;
        mNotificationsLongListener = notificationsLongListener;
        mTorchClickListener = torchClickListener;
	mCameraClickListener = cameraClickListener;
	mScreenShotClickListener = screenshotClickListener;
        mCameraLongClickListener = cameraLongClickListener;
        mImmersiveClickListener = immersiveClickListener;
	mConfigClickListener = configClickListener;
        mConfigLongListener = configLongListener;
	mPieClickListener = pieClickListener;
        mScreenClickListener = screenClickListener;
	mKillClickListener = killClickListener;
        mAppPickerClickListener = appPickerClickListener;
        updateButtonListeners();
    }

    private void removeButtonListeners() {
        ViewGroup container = (ViewGroup) mCurrentView.findViewById(R.id.container);
        int viewCount = container.getChildCount();
        for (int i = 0; i < viewCount; i++) {
            View button = container.getChildAt(i);
            if (button instanceof KeyButtonView) {
                button.setOnClickListener(null);
                button.setOnTouchListener(null);
                button.setLongClickable(false);
                button.setOnLongClickListener(null);
            }
        }
    }

    protected void updateButtonListeners() {
        Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.rotate_around_center);
        View recentView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
        if (recentView != null) {
            if (mNavButtonsRotation) {
                recentView.startAnimation(animation);
            }
            recentView.setOnClickListener(mRecentsClickListener);
            recentView.setOnTouchListener(mRecentsPreloadListener);
            recentView.setLongClickable(true);
            recentView.setOnLongClickListener(mRecentsBackListener);
        }
        View backView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_BACK);
        if (backView != null) {
            if (mNavButtonsRotation) {
                backView.startAnimation(animation);
            }
            backView.setLongClickable(true);
            backView.setOnLongClickListener(mRecentsBackListener);
        }
        View homeView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
        if (homeView != null) {
            if (mNavButtonsRotation) {
                homeView.startAnimation(animation);
            }
            homeView.setOnTouchListener(mHomeSearchActionListener);
            homeView.setLongClickable(true);
            homeView.setOnLongClickListener(mLongPressHomeListener);
        }
        View powerView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_POWER);
        if (powerView != null) {
            if (mNavButtonsRotation) {
                powerView.startAnimation(animation);
            }
            powerView.setLongClickable(true);
            powerView.setOnLongClickListener(mPowerListener);
        }
        View notificationsView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_NOTIFICATIONS);
        if (notificationsView != null) {
            if (mNavButtonsRotation) {
                notificationsView.startAnimation(animation);
            }
            notificationsView.setOnClickListener(mNotificationsClickListener);
            notificationsView.setLongClickable(true);
            notificationsView.setOnLongClickListener(mNotificationsLongListener);
        }
     	View torchView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_TORCH);
        if (torchView != null) {
            if (mNavButtonsRotation) {
                torchView.startAnimation(animation);
            }
            torchView.setOnClickListener(mTorchClickListener);
        }
        View cameraView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_CAMERA);
        if (cameraView != null) {
            if (mNavButtonsRotation) {
                cameraView.startAnimation(animation);
            }
            cameraView.setOnClickListener(mCameraClickListener);
            cameraView.setLongClickable(true);
            cameraView.setOnLongClickListener(mCameraLongClickListener);
        }
  	View screenshotView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_SCREENSHOT);
        if (screenshotView != null) {
            if (mNavButtonsRotation) {
                screenshotView.startAnimation(animation);
            }
            screenshotView.setOnClickListener(mScreenShotClickListener);
        }
	View immersivetView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_EXPAND);
        if (immersivetView != null) {
            if (mNavButtonsRotation) {
                immersivetView.startAnimation(animation);
            }
            immersivetView.setOnClickListener(mImmersiveClickListener);
        }
	View configView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_CONFIGURATIONS);
        if (configView != null) {
	     if (mNavButtonsRotation) {
                configView.startAnimation(animation);
            }
            configView.setOnClickListener(mConfigClickListener);
	    configView.setLongClickable(true);
            configView.setOnLongClickListener(mConfigLongListener);
        }
	View pieView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_PIE);
        if (pieView != null) {
	if (mNavButtonsRotation) {
                pieView.startAnimation(animation);
            }
            pieView.setOnClickListener(mPieClickListener);
        }
	View screenView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_SCREENRECORD);
        if (screenView != null) {
	     if (mNavButtonsRotation) {
                screenView.startAnimation(animation);
            }
            screenView.setOnClickListener(mScreenClickListener);
        }
	View killView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_KILLTASK);
        if (killView != null) {
		 if (mNavButtonsRotation) {
                killView.startAnimation(animation);
            }
            killView.setOnClickListener(mKillClickListener);
	}
        View appPickerView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_APP_PICKER);
        if (appPickerView != null) {
            if (mNavButtonsRotation) {
                appPickerView.startAnimation(animation);
            }
            appPickerView.setOnClickListener(mAppPickerClickListener);
        }
    }

    public boolean isInEditMode() {
        return mInEditMode;
    }

    private void setButtonWithTagVisibility(Object tag, boolean visible) {
        View findView = mCurrentView.findViewWithTag(tag);
        if (findView == null) {
            return;
        }
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        // if we're showing dpad arrow keys (e.g. the side button visibility where it's shown != -1)
        // then don't actually update that buttons visibility, but update the stored value
        if (getSideButtonVisibility(true) != -1
                && findView.getId() == (mVertical ? R.id.sev : R.id.one)) {
            setSideButtonVisibility(true, visibility);
        } else if (getSideButtonVisibility(false) != -1
                && findView.getId() == (mVertical ? R.id.one : R.id.sev)) {
            setSideButtonVisibility(false, visibility);
        } else {
            findView.setVisibility(visibility);
        }
    }

    @Override
    public Resources getResources() {
        return mThemedResources != null ? mThemedResources : getContext().getResources();
    }

    public class NavBarReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean edit = intent.getBooleanExtra("edit", false);
            boolean save = intent.getBooleanExtra("save", false);
            if (edit != mInEditMode) {
                mInEditMode = edit;
                if (edit) {
                    removeButtonListeners();
                    mEditBar.setEditMode(true);
                } else {
                    if (save) {
                        mEditBar.saveKeys();
                    }
                    mEditBar.setEditMode(false);
                    updateSettings();
                }
            }
        }
    }

    public void updateSettings() {
        mEditBar.updateKeys();
        removeButtonListeners();
        updateButtonListeners();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true);
    }

    private class SettingsObserver extends UserContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void observe() {
            super.observe();
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(
                    CMSettings.System.getUriFor(CMSettings.System.NAVIGATION_BAR_MENU_ARROW_KEYS),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DIM_NAV_BUTTONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DIM_NAV_BUTTONS_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DIM_NAV_BUTTONS_ALPHA), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DIM_NAV_BUTTONS_ANIMATE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DIM_NAV_BUTTONS_ANIMATE_DURATION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DIM_NAV_BUTTONS_TOUCH_ANYWHERE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DOUBLE_TAP_SLEEP_NAVBAR), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.POLICY_CONTROL), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_BUTTONS_ROTATION), false, this);

            // intialize mModlockDisabled
            onChange(false);
        }

        @Override
        public void unobserve() {
            super.unobserve();
            getContext().getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            update();
            onNavButtonTouched();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();

	mShowDpadArrowKeys = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                    CMSettings.System.NAVIGATION_BAR_MENU_ARROW_KEYS, 0, UserHandle.USER_CURRENT) != 0;
            // reset saved side button visibilities
            for (int i = 0; i < mSideButtonVisibilities.length; i++) {
                for (int j = 0; j < mSideButtonVisibilities[i].length; j++) {
                    mSideButtonVisibilities[i][j] = -1;
                }
            }
            setNavigationIconHints(mNavigationIconHints, true);

            mDimNavButtons = (Settings.System.getIntForUser(resolver,
                    Settings.System.DIM_NAV_BUTTONS, 0,
                    UserHandle.USER_CURRENT) == 1);
            mDimNavButtonsTimeout = Settings.System.getIntForUser(resolver,
                    Settings.System.DIM_NAV_BUTTONS_TIMEOUT, 3000,
                    UserHandle.USER_CURRENT);
            mDimNavButtonsAlpha = (float) Settings.System.getIntForUser(resolver,
                    Settings.System.DIM_NAV_BUTTONS_ALPHA, 50,
                    UserHandle.USER_CURRENT) / 100.0f;
            mDimNavButtonsAnimate = (Settings.System.getIntForUser(resolver,
                    Settings.System.DIM_NAV_BUTTONS_ANIMATE, 0,
                    UserHandle.USER_CURRENT) == 1);
            mDimNavButtonsAnimateDuration = Settings.System.getIntForUser(resolver,
                    Settings.System.DIM_NAV_BUTTONS_ANIMATE_DURATION, 2000,
                    UserHandle.USER_CURRENT);
            mDimNavButtonsTouchAnywhere = (Settings.System.getIntForUser(resolver,
                    Settings.System.DIM_NAV_BUTTONS_TOUCH_ANYWHERE, 0,
                    UserHandle.USER_CURRENT) == 1);
            mDoubleTapToSleep = (Settings.System.getIntForUser(resolver,
                    Settings.System.DOUBLE_TAP_SLEEP_NAVBAR, 0,
                    UserHandle.USER_CURRENT) == 1);
            mNavButtonsRotation = (Settings.System.getIntForUser(resolver,
                    Settings.System.NAV_BUTTONS_ROTATION, 1,
                    UserHandle.USER_CURRENT) == 1);
            String expDeskString = Settings.Global.getStringForUser(resolver,
                    Settings.Global.POLICY_CONTROL, UserHandle.USER_CURRENT);
            mIsExpandedDesktopOn = (expDeskString != null ?
                    expDeskString.equals("immersive.full=*") : false); 
        }
    }

    private Runnable mNavButtonDimmer = new Runnable() {
        public void run() {
            mIsHandlerCallbackActive = false;
            final ViewGroup navButtons = getNavButtons();
            if (navButtons != null && !mIsDim) {
                mIsDim = true;
                if (mDimNavButtonsAnimate) {
                    mFadeOut = ObjectAnimator.ofFloat(
                            navButtons, "alpha", mOriginalAlpha, mDimNavButtonsAlpha);
                    mFadeOut.setInterpolator(new AccelerateInterpolator());
                    mFadeOut.setDuration(mDimNavButtonsAnimateDuration);
                    mFadeOut.setFrameDelay(100);
                    mFadeOut.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mIsAnimating) {
                                mIsAnimating = false;
                            }
                            mFadeOut.removeAllListeners();
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            mFadeOut.removeAllListeners();
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                        }

                        @Override
                        public void onAnimationStart(Animator animation) {
                            mIsAnimating = true;
                        }
                    });
                    mFadeOut.start();
                } else {
                    navButtons.setAlpha(mDimNavButtonsAlpha);
                }
            }
        }
    };
}
