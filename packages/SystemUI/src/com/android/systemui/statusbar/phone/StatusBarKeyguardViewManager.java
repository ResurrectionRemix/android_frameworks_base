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

import static com.android.keyguard.KeyguardHostView.OnDismissAction;
import static com.android.systemui.statusbar.phone.FingerprintUnlockController.MODE_WAKE_AND_UNLOCK;
import static com.android.systemui.statusbar.phone.FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Trace;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManagerGlobal;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.LatencyTracker;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.DejankUtils;
import com.android.systemui.Dependency;
import com.android.systemui.SystemUIFactory;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.navigation.Navigator;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.RemoteInputController;

import java.util.ArrayList;

/**
 * Manages creating, showing, hiding and resetting the keyguard within the status bar. Calls back
 * via {@link ViewMediatorCallback} to poke the wake lock and report that the keyguard is done,
 * which is in turn, reported to this class by the current
 * {@link com.android.keyguard.KeyguardViewBase}.
 */
public class StatusBarKeyguardViewManager implements RemoteInputController.Callback {

    // When hiding the Keyguard with timing supplied from WindowManager, better be early than late.
    private static final long HIDE_TIMING_CORRECTION_MS = - 16 * 3;

    // Delay for showing the navigation bar when the bouncer appears. This should be kept in sync
    // with the appear animations of the PIN/pattern/password views.
    private static final long NAV_BAR_SHOW_DELAY_BOUNCER = 320;

    private static final long WAKE_AND_UNLOCK_SCRIM_FADEOUT_DURATION_MS = 200;

    // Duration of the Keyguard dismissal animation in case the user is currently locked. This is to
    // make everything a bit slower to bridge a gap until the user is unlocked and home screen has
    // dranw its first frame.
    private static final long KEYGUARD_DISMISS_DURATION_LOCKED = 2000;

    private static String TAG = "StatusBarKeyguardViewManager";

    protected final Context mContext;
    private final StatusBarWindowManager mStatusBarWindowManager;
    private final boolean mDisplayBlanksAfterDoze;

    protected LockPatternUtils mLockPatternUtils;
    protected ViewMediatorCallback mViewMediatorCallback;
    protected StatusBar mStatusBar;
    private ScrimController mScrimController;
    private FingerprintUnlockController mFingerprintUnlockController;

    private ViewGroup mContainer;

    private boolean mScreenTurnedOn;
    protected KeyguardBouncer mBouncer;
    protected boolean mShowing;
    protected boolean mOccluded;
    protected boolean mRemoteInputActive;
    private boolean mDozing;

    protected boolean mFirstUpdate = true;
    protected boolean mLastShowing;
    protected boolean mLastOccluded;
    private boolean mLastBouncerShowing;
    private boolean mLastBouncerDismissible;
    protected boolean mLastRemoteInputActive;
    private boolean mLastDozing;
    private boolean mLastDeferScrimFadeOut;
    private int mLastFpMode;

    private OnDismissAction mAfterKeyguardGoneAction;
    private final ArrayList<Runnable> mAfterKeyguardGoneRunnables = new ArrayList<>();
    private boolean mDeferScrimFadeOut;

    // Dismiss action to be launched when we stop dozing or the keyguard is gone.
    private DismissWithActionRequest mPendingWakeupAction;

    private final KeyguardUpdateMonitorCallback mUpdateMonitorCallback =
            new KeyguardUpdateMonitorCallback() {
        @Override
        public void onEmergencyCallAction() {

            // Since we won't get a setOccluded call we have to reset the view manually such that
            // the bouncer goes away.
            if (mOccluded) {
                reset(true /* hideBouncerWhenShowing */);
            }
        }
    };

    public StatusBarKeyguardViewManager(Context context, ViewMediatorCallback callback,
            LockPatternUtils lockPatternUtils) {
        mContext = context;
        mViewMediatorCallback = callback;
        mLockPatternUtils = lockPatternUtils;
        mStatusBarWindowManager = Dependency.get(StatusBarWindowManager.class);
        KeyguardUpdateMonitor.getInstance(context).registerCallback(mUpdateMonitorCallback);
        mDisplayBlanksAfterDoze = context.getResources().getBoolean(
                com.android.internal.R.bool.config_displayBlanksAfterDoze);
    }

    public void registerStatusBar(StatusBar statusBar,
            ViewGroup container,
            ScrimController scrimController,
            FingerprintUnlockController fingerprintUnlockController,
            DismissCallbackRegistry dismissCallbackRegistry) {
        mStatusBar = statusBar;
        mContainer = container;
        mScrimController = scrimController;
        mFingerprintUnlockController = fingerprintUnlockController;
        mBouncer = SystemUIFactory.getInstance().createKeyguardBouncer(mContext,
                mViewMediatorCallback, mLockPatternUtils, container, dismissCallbackRegistry);
    }

    /**
     * Show the keyguard.  Will handle creating and attaching to the view manager
     * lazily.
     */
    public void show(Bundle options) {
        mShowing = true;
        mStatusBarWindowManager.setKeyguardShowing(true);
        mScrimController.abortKeyguardFadingOut();
        reset(true /* hideBouncerWhenShowing */);
    }

    /**
     * Shows the notification keyguard or the bouncer depending on
     * {@link KeyguardBouncer#needsFullscreenBouncer()}.
     */
    protected void showBouncerOrKeyguard(boolean hideBouncerWhenShowing, boolean isBackPressed) {
        switch (mBouncer.needsFullscreenBouncer()) {
            case KeyguardBouncer.UNLOCK_SEQUENCE_FORCE_BOUNCER:
                // SIM PIN/PUK
                // The keyguard might be showing (already). So we need to hide it.
                mStatusBar.hideKeyguard();
                mBouncer.show(true /* resetSecuritySelection */);
                break;
            case KeyguardBouncer.UNLOCK_SEQUENCE_BOUNCER_FIRST:
                // Pattern / PIN / password with "Directly show " enabled
                if (isBackPressed) {
                    mStatusBar.showKeyguard();
                    if (hideBouncerWhenShowing) {
                        mBouncer.hide(false /* destroyView */);
                        mBouncer.prepare();
                    }
                } else {
                    // The keyguard might be showing (already). So we need to hide it.
                    mStatusBar.hideKeyguard();
                    mBouncer.show(true /* resetSecuritySelection */);
                }
                break;
            case KeyguardBouncer.UNLOCK_SEQUENCE_DEFAULT:
                mStatusBar.showKeyguard();
                if (hideBouncerWhenShowing) {
                    mBouncer.hide(false /* destroyView */);
                    mBouncer.prepare();
                }
                break;
            default:
                break;
        }
        updateStates();
    }

    private void hideBouncer(boolean destroyView) {
        mBouncer.hide(destroyView);
        cancelPendingWakeupAction();
    }

    private void showBouncer() {
        if (mShowing) {
            mBouncer.show(false /* resetSecuritySelection */);
        }
        updateStates();
    }

    public void dismissWithAction(OnDismissAction r, Runnable cancelAction,
            boolean afterKeyguardGone) {
        if (mShowing) {
            cancelPendingWakeupAction();
            // If we're dozing, this needs to be delayed until after we wake up - unless we're
            // wake-and-unlocking, because there dozing will last until the end of the transition.
            if (mDozing && !isWakeAndUnlocking()) {
                mPendingWakeupAction = new DismissWithActionRequest(
                        r, cancelAction, afterKeyguardGone);
                return;
            }

            if (!afterKeyguardGone) {
                mBouncer.showWithDismissAction(r, cancelAction);
            } else {
                mAfterKeyguardGoneAction = r;
                mBouncer.show(false /* resetSecuritySelection */);
            }
        }
        updateStates();
    }

    private boolean isWakeAndUnlocking() {
        int mode = mFingerprintUnlockController.getMode();
        return mode == MODE_WAKE_AND_UNLOCK || mode == MODE_WAKE_AND_UNLOCK_PULSING;
    }

    /**
     * Adds a {@param runnable} to be executed after Keyguard is gone.
     */
    public void addAfterKeyguardGoneRunnable(Runnable runnable) {
        mAfterKeyguardGoneRunnables.add(runnable);
    }

    /**
     * Reset the state of the view.
     */
    public void reset(boolean hideBouncerWhenShowing, boolean isBackPressed) {
        if (mShowing) {
            if (mOccluded && !mDozing) {
                mStatusBar.hideKeyguard();
                mStatusBar.stopWaitingForKeyguardExit();
                if (hideBouncerWhenShowing || (mBouncer.needsFullscreenBouncer() == mBouncer.UNLOCK_SEQUENCE_DEFAULT)) {
                    hideBouncer(false /* destroyView */);
                }
            } else {
                showBouncerOrKeyguard(hideBouncerWhenShowing, isBackPressed);
            }
            KeyguardUpdateMonitor.getInstance(mContext).sendKeyguardReset();
            updateStates();
        }
    }

    /**
     * Reset the state of the view; not caused by back press
     */
    public void reset(boolean hideBouncerWhenShowing) {
        reset(hideBouncerWhenShowing, false);
    }

    public void onStartedGoingToSleep() {
        // TODO: remove
    }

    public void onFinishedGoingToSleep() {
        mBouncer.onScreenTurnedOff();
    }

    public void onStartedWakingUp() {
        // TODO: remove
    }

    public void onScreenTurningOn() {
        // TODO: remove
    }

    public void onScreenTurnedOn() {
        Trace.beginSection("StatusBarKeyguardViewManager#onScreenTurnedOn");
        mScreenTurnedOn = true;
        if (mDeferScrimFadeOut) {
            mDeferScrimFadeOut = false;
            animateScrimControllerKeyguardFadingOut(0, WAKE_AND_UNLOCK_SCRIM_FADEOUT_DURATION_MS,
                    true /* skipFirstFrame */);
            updateStates();
        }
        Trace.endSection();
    }

    @Override
    public void onRemoteInputActive(boolean active) {
        mRemoteInputActive = active;
        updateStates();
    }

    public void setDozing(boolean dozing) {
        if (mDozing != dozing) {
            mDozing = dozing;
            if (dozing || (mBouncer.needsFullscreenBouncer() == mBouncer.UNLOCK_SEQUENCE_DEFAULT) || mOccluded) {
                reset(dozing /* hideBouncerWhenShowing */);
            }
            updateStates();

            if (!dozing) {
                launchPendingWakeupAction();
            }
        }
    }

    public void onScreenTurnedOff() {
        mScreenTurnedOn = false;
    }

    public void notifyDeviceWakeUpRequested() {
        // TODO: remove
    }

    public void setNeedsInput(boolean needsInput) {
        mStatusBarWindowManager.setKeyguardNeedsInput(needsInput);
    }

    public boolean isUnlockWithWallpaper() {
        return mStatusBarWindowManager.isShowingWallpaper();
    }

    public void setOccluded(boolean occluded, boolean animate) {
        mStatusBar.setOccluded(occluded);
        if (occluded && !mOccluded && mShowing) {
            if (mStatusBar.isInLaunchTransition()) {
                mOccluded = true;
                mStatusBar.fadeKeyguardAfterLaunchTransition(null /* beforeFading */,
                        new Runnable() {
                            @Override
                            public void run() {
                                mStatusBarWindowManager.setKeyguardOccluded(mOccluded);
                                reset(true /* hideBouncerWhenShowing */);
                            }
                        });
                return;
            }
        }
        boolean isOccluding = !mOccluded && occluded;
        mOccluded = occluded;
        if (mShowing) {
            mStatusBar.updateMediaMetaData(false, animate && !occluded);
        }
        mStatusBarWindowManager.setKeyguardOccluded(occluded);
        mStatusBar.getVisualizer().setOccluded(occluded);

        // setDozing(false) will call reset once we stop dozing.
        if (!mDozing) {
            // If Keyguard is reshown, don't hide the bouncer as it might just have been requested
            // by a FLAG_DISMISS_KEYGUARD_ACTIVITY.
            reset(isOccluding /* hideBouncerWhenShowing*/);
        }
        if (animate && !occluded && mShowing) {
            mStatusBar.animateKeyguardUnoccluding();
        }
    }

    public boolean isOccluded() {
        return mOccluded;
    }

    /**
     * Starts the animation before we dismiss Keyguard, i.e. an disappearing animation on the
     * security view of the bouncer.
     *
     * @param finishRunnable the runnable to be run after the animation finished, or {@code null} if
     *                       no action should be run
     */
    public void startPreHideAnimation(Runnable finishRunnable) {
        if (mBouncer.isShowing()) {
            mBouncer.startPreHideAnimation(finishRunnable);
        } else if (finishRunnable != null) {
            finishRunnable.run();
        }
    }

    /**
     * Hides the keyguard view
     */
    public void hide(long startTime, long fadeoutDuration) {
        mShowing = false;
        launchPendingWakeupAction();

        if (KeyguardUpdateMonitor.getInstance(mContext).needsSlowUnlockTransition()) {
            fadeoutDuration = KEYGUARD_DISMISS_DURATION_LOCKED;
        }
        long uptimeMillis = SystemClock.uptimeMillis();
        long delay = Math.max(0, startTime + HIDE_TIMING_CORRECTION_MS - uptimeMillis);

        if (mStatusBar.isInLaunchTransition() ) {
            mStatusBar.fadeKeyguardAfterLaunchTransition(new Runnable() {
                @Override
                public void run() {
                    mStatusBarWindowManager.setKeyguardShowing(false);
                    mStatusBarWindowManager.setKeyguardFadingAway(true);
                    hideBouncer(true /* destroyView */);
                    updateStates();
                    mScrimController.animateKeyguardFadingOut(
                            StatusBar.FADE_KEYGUARD_START_DELAY,
                            StatusBar.FADE_KEYGUARD_DURATION, null,
                            false /* skipFirstFrame */);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mStatusBar.hideKeyguard();
                    mStatusBarWindowManager.setKeyguardFadingAway(false);
                    mViewMediatorCallback.keyguardGone();
                    executeAfterKeyguardGoneAction();
                }
            });
        } else {
            executeAfterKeyguardGoneAction();
            boolean wakeUnlockPulsing =
                    mFingerprintUnlockController.getMode() == MODE_WAKE_AND_UNLOCK_PULSING;
            if (wakeUnlockPulsing) {
                delay = 0;
                fadeoutDuration = 240;
            }
            mStatusBar.setKeyguardFadingAway(startTime, delay, fadeoutDuration);
            mFingerprintUnlockController.startKeyguardFadingAway();
            hideBouncer(true /* destroyView */);
            if (wakeUnlockPulsing) {
                mStatusBarWindowManager.setKeyguardFadingAway(true);
                mStatusBar.fadeKeyguardWhilePulsing();
                animateScrimControllerKeyguardFadingOut(delay, fadeoutDuration,
                        mStatusBar::hideKeyguard, false /* skipFirstFrame */);
            } else {
                mFingerprintUnlockController.startKeyguardFadingAway();
                mStatusBar.setKeyguardFadingAway(startTime, delay, fadeoutDuration);
                boolean staying = mStatusBar.hideKeyguard();
                if (!staying) {
                    mStatusBarWindowManager.setKeyguardFadingAway(true);
                    if (mFingerprintUnlockController.getMode() == MODE_WAKE_AND_UNLOCK) {
                        boolean turnedOnSinceAuth =
                                mFingerprintUnlockController.hasScreenTurnedOnSinceAuthenticating();
                        if (!mScreenTurnedOn || mDisplayBlanksAfterDoze && !turnedOnSinceAuth) {
                            // Not ready to animate yet; either because the screen is not on yet,
                            // or it is on but will turn off before waking out of doze.
                            mDeferScrimFadeOut = true;
                        } else {

                            // Screen is already on, don't defer with fading out.
                            animateScrimControllerKeyguardFadingOut(0,
                                    WAKE_AND_UNLOCK_SCRIM_FADEOUT_DURATION_MS,
                                    true /* skipFirstFrame */);
                        }
                    } else {
                        animateScrimControllerKeyguardFadingOut(delay, fadeoutDuration,
                                false /* skipFirstFrame */);
                    }
                } else {
                    mScrimController.animateGoingToFullShade(delay, fadeoutDuration);
                    mStatusBar.finishKeyguardFadingAway();
                    mFingerprintUnlockController.finishKeyguardFadingAway();
                }
            }
            updateStates();
            mStatusBarWindowManager.setKeyguardShowing(false);
            mViewMediatorCallback.keyguardGone();
        }
    }

    public void onDensityOrFontScaleChanged() {
        hideBouncer(true /* destroyView */);
    }

    public void onOverlayChanged() {
        hideBouncer(true /* destroyView */);
        mBouncer.prepare();
    }

    public void hideNoAnimation() {
        mShowing = false;
        mStatusBar.setKeyguardFadingAway(SystemClock.uptimeMillis(), 0, 0);
        mStatusBar.hideKeyguard();
        mStatusBar.finishKeyguardFadingAway();
        mStatusBarWindowManager.setKeyguardShowing(false);
        mBouncer.hide(true /* destroyView */);
        mViewMediatorCallback.keyguardGone();
        mFingerprintUnlockController.finishKeyguardFadingAway();
        updateStates();
        WindowManagerGlobal.getInstance().trimMemory(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    }

    private void animateScrimControllerKeyguardFadingOut(long delay, long duration,
            boolean skipFirstFrame) {
        animateScrimControllerKeyguardFadingOut(delay, duration, null /* endRunnable */,
                skipFirstFrame);
    }

    private void animateScrimControllerKeyguardFadingOut(long delay, long duration,
            final Runnable endRunnable, boolean skipFirstFrame) {
        Trace.asyncTraceBegin(Trace.TRACE_TAG_VIEW, "Fading out", 0);
        mScrimController.animateKeyguardFadingOut(delay, duration, new Runnable() {
            @Override
            public void run() {
                if (endRunnable != null) {
                    endRunnable.run();
                }
                mContainer.postDelayed(() -> mStatusBarWindowManager.setKeyguardFadingAway(false),
                        100);
                mStatusBar.finishKeyguardFadingAway();
                mFingerprintUnlockController.finishKeyguardFadingAway();
                WindowManagerGlobal.getInstance().trimMemory(
                        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
                Trace.asyncTraceEnd(Trace.TRACE_TAG_VIEW, "Fading out", 0);
            }
        }, skipFirstFrame);
        if (mFingerprintUnlockController.getMode() == MODE_WAKE_AND_UNLOCK
                && LatencyTracker.isEnabled(mContext)) {
            DejankUtils.postAfterTraversal(() ->
                    LatencyTracker.getInstance(mContext).onActionEnd(
                            LatencyTracker.ACTION_FINGERPRINT_WAKE_AND_UNLOCK));
        }
    }

    private void executeAfterKeyguardGoneAction() {
        if (mAfterKeyguardGoneAction != null) {
            mAfterKeyguardGoneAction.onDismiss();
            mAfterKeyguardGoneAction = null;
        }
        for (int i = 0; i < mAfterKeyguardGoneRunnables.size(); i++) {
            mAfterKeyguardGoneRunnables.get(i).run();
        }
        mAfterKeyguardGoneRunnables.clear();
    }

    /**
     * Dismisses the keyguard by going to the next screen or making it gone.
     */
    public void dismissAndCollapse() {
        mStatusBar.executeRunnableDismissingKeyguard(null, null, true, false, true);
    }

    public void dismiss() {
        showBouncer();
    }

    /**
     * WARNING: This method might cause Binder calls.
     */
    public boolean isSecure() {
        return mBouncer.isSecure();
    }

    /**
     * @return Whether the keyguard is showing
     */
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Notifies this manager that the back button has been pressed.
     *
     * @return whether the back press has been handled
     */
    public boolean onBackPressed() {
        if (mBouncer.isShowing()) {
            mStatusBar.endAffordanceLaunch();
            reset(true /* hideBouncerWhenShowing */, true /* isBackPressed */);
            return true;
        }
        return false;
    }

    public boolean isBouncerShowing() {
        return mBouncer.isShowing();
    }

    private long getNavBarShowDelay() {
        if (mStatusBar.isKeyguardFadingAway()) {
            return mStatusBar.getKeyguardFadingAwayDelay();
        } else if (mBouncer.isShowing()) {
            return NAV_BAR_SHOW_DELAY_BOUNCER;
        } else {
            // No longer dozing, or remote input is active. No delay.
            return 0;
        }
    }

    private Runnable mMakeNavigationBarVisibleRunnable = new Runnable() {
        @Override
        public void run() {
            mStatusBar.getNavigationBarView().getBaseView().getRootView().setVisibility(View.VISIBLE);
        }
    };

    protected void updateStates() {
        int vis = mContainer.getSystemUiVisibility();
        boolean showing = mShowing;
        boolean occluded = mOccluded;
        boolean bouncerShowing = mBouncer.isShowing();
        boolean bouncerDismissible =
                mBouncer.isFullscreenBouncer() != KeyguardBouncer.UNLOCK_SEQUENCE_FORCE_BOUNCER;
        boolean remoteInputActive = mRemoteInputActive;

        if ((bouncerDismissible || !showing || remoteInputActive) !=
                (mLastBouncerDismissible || !mLastShowing || mLastRemoteInputActive)
                || mFirstUpdate) {
            if (bouncerDismissible || !showing || remoteInputActive) {
                mContainer.setSystemUiVisibility(vis & ~View.STATUS_BAR_DISABLE_BACK);
            } else {
                mContainer.setSystemUiVisibility(vis | View.STATUS_BAR_DISABLE_BACK);
            }
        }

        boolean navBarVisible = isNavBarVisible();
        boolean lastNavBarVisible = getLastNavBarVisible();
        if (navBarVisible != lastNavBarVisible || mFirstUpdate) {
            Navigator navbar = mStatusBar.getNavigationBarView();
            if (navbar != null) {
                if (navBarVisible) {
                    long delay = getNavBarShowDelay();
                    if (delay == 0) {
                        mMakeNavigationBarVisibleRunnable.run();
                    } else {
                        mContainer.postOnAnimationDelayed(mMakeNavigationBarVisibleRunnable,
                                delay);
                    }
                } else {
                    mContainer.removeCallbacks(mMakeNavigationBarVisibleRunnable);
                    navbar.getBaseView().getRootView().setVisibility(View.GONE);
                }
            }
        }

        if (bouncerShowing != mLastBouncerShowing || mFirstUpdate) {
            mStatusBarWindowManager.setBouncerShowing(bouncerShowing);
            mStatusBar.setBouncerShowing(bouncerShowing);
            mScrimController.setBouncerShowing(bouncerShowing);
        }

        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        if ((showing && !occluded) != (mLastShowing && !mLastOccluded) || mFirstUpdate) {
            updateMonitor.onKeyguardVisibilityChanged(showing && !occluded);
        }
        if (bouncerShowing != mLastBouncerShowing || mFirstUpdate) {
            updateMonitor.sendKeyguardBouncerChanged(bouncerShowing);
        }

        mFirstUpdate = false;
        mLastShowing = showing;
        mLastOccluded = occluded;
        mLastBouncerShowing = bouncerShowing;
        mLastBouncerDismissible = bouncerDismissible;
        mLastRemoteInputActive = remoteInputActive;
        mLastDozing = mDozing;
        mLastDeferScrimFadeOut = mDeferScrimFadeOut;
        mLastFpMode = mFingerprintUnlockController.getMode();
        mStatusBar.onKeyguardViewManagerStatesUpdated();
    }

    /**
     * @return Whether the navigation bar should be made visible based on the current state.
     */
    protected boolean isNavBarVisible() {
        int fpMode = mFingerprintUnlockController.getMode();
        boolean keyguardShowing = mShowing && !mOccluded;
        boolean hideWhileDozing = mDozing && fpMode != MODE_WAKE_AND_UNLOCK_PULSING;
        return (!keyguardShowing && !hideWhileDozing || mBouncer.isShowing()
                || mRemoteInputActive) && !mDeferScrimFadeOut;
    }

    /**
     * @return Whether the navigation bar was made visible based on the last known state.
     */
    protected boolean getLastNavBarVisible() {
        boolean keyguardShowing = mLastShowing && !mLastOccluded;
        boolean hideWhileDozing = mLastDozing && mLastFpMode != MODE_WAKE_AND_UNLOCK_PULSING;
        return (!keyguardShowing && !hideWhileDozing || mLastBouncerShowing
                || mLastRemoteInputActive) && !mLastDeferScrimFadeOut;
    }

    public boolean shouldDismissOnMenuPressed() {
        return mBouncer.shouldDismissOnMenuPressed();
    }

    public boolean interceptMediaKey(KeyEvent event) {
        return mBouncer.interceptMediaKey(event);
    }

    public void readyForKeyguardDone() {
        mViewMediatorCallback.readyForKeyguardDone();
    }

    public boolean shouldDisableWindowAnimationsForUnlock() {
        return mStatusBar.isInLaunchTransition();
    }

    public boolean isGoingToNotificationShade() {
        return mStatusBar.isGoingToNotificationShade();
    }

    public boolean isSecure(int userId) {
        return mBouncer.isSecure() || mLockPatternUtils.isSecure(userId);
    }

    public void keyguardGoingAway() {
        mStatusBar.keyguardGoingAway();
    }

    public void animateCollapsePanels(float speedUpFactor) {
        mStatusBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE, true /* force */,
                false /* delayed */, speedUpFactor);
    }

    /**
     * Notifies that the user has authenticated by other means than using the bouncer, for example,
     * fingerprint.
     */
    public void notifyKeyguardAuthenticated(boolean strongAuth) {
        mBouncer.notifyKeyguardAuthenticated(strongAuth);
    }

    public void showBouncerMessage(String message, int color) {
        mBouncer.showMessage(message, color);
    }

    public ViewRootImpl getViewRootImpl() {
        return mStatusBar.getStatusBarView().getViewRootImpl();
    }

    public void launchPendingWakeupAction() {
        DismissWithActionRequest request = mPendingWakeupAction;
        mPendingWakeupAction = null;
        if (request != null) {
            if (mShowing) {
                dismissWithAction(request.dismissAction, request.cancelAction,
                        request.afterKeyguardGone);
            } else if (request.dismissAction != null) {
                request.dismissAction.onDismiss();
            }
        }
    }

    public void cancelPendingWakeupAction() {
        DismissWithActionRequest request = mPendingWakeupAction;
        mPendingWakeupAction = null;
        if (request != null && request.cancelAction != null) {
            request.cancelAction.run();
        }
    }

    private static class DismissWithActionRequest {
        final OnDismissAction dismissAction;
        final Runnable cancelAction;
        final boolean afterKeyguardGone;

        DismissWithActionRequest(OnDismissAction dismissAction, Runnable cancelAction,
                boolean afterKeyguardGone) {
            this.dismissAction = dismissAction;
            this.cancelAction = cancelAction;
            this.afterKeyguardGone = afterKeyguardGone;
        }
    }
}
