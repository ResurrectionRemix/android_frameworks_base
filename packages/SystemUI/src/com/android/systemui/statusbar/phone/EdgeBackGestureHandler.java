/**
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.view.Display.INVALID_DISPLAY;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.NAVIGATION_BAR_TRANSIENT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ParceledListSlice;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.MathUtils;
import android.util.StatsLog;
import android.view.Gravity;
import android.view.IPinnedStackController;
import android.view.IPinnedStackListener;
import android.view.ISystemGestureExclusionListener;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.util.rr.RRActionUtils;
import com.android.internal.util.rr.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.bubbles.BubbleController;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.io.PrintWriter;
import java.util.concurrent.Executor;

/**
 * Utility class to handle edge swipes for back gesture
 */
public class EdgeBackGestureHandler implements DisplayListener {

    private static final String TAG = "EdgeBackGestureHandler";
    private static final int MAX_LONG_PRESS_TIMEOUT = SystemProperties.getInt(
            "gestures.back_timeout", 250);

    private final IPinnedStackListener.Stub mImeChangedListener = new IPinnedStackListener.Stub() {
        @Override
        public void onListenerRegistered(IPinnedStackController controller) {
        }

        @Override
        public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
            // No need to thread jump, assignments are atomic
            if (mBlockImeSpace) {
                mImeHeight = imeVisible ? imeHeight : 0;
            } else {
                mImeHeight = 0;
            }
            // TODO: Probably cancel any existing gesture
        }

        @Override
        public void onShelfVisibilityChanged(boolean shelfVisible, int shelfHeight) {
        }

        @Override
        public void onMinimizedStateChanged(boolean isMinimized) {
        }

        @Override
        public void onMovementBoundsChanged(Rect insetBounds, Rect normalBounds,
                Rect animatingBounds, boolean fromImeAdjustment, boolean fromShelfAdjustment,
                int displayRotation) {
        }

        @Override
        public void onActionsChanged(ParceledListSlice actions) {
        }
    };

    private ISystemGestureExclusionListener mGestureExclusionListener =
            new ISystemGestureExclusionListener.Stub() {
                @Override
                public void onSystemGestureExclusionChanged(int displayId,
                        Region systemGestureExclusion, Region unrestrictedOrNull) {
                    if (displayId == mDisplayId) {
                        mMainExecutor.execute(() -> {
                            mExcludeRegion.set(systemGestureExclusion);
                            mUnrestrictedExcludeRegion.set(unrestrictedOrNull != null
                                    ? unrestrictedOrNull : systemGestureExclusion);
                        });
                    }
                }
            };

    private final Context mContext;
    private final OverviewProxyService mOverviewProxyService;

    private final Point mDisplaySize = new Point();
    private final int mDisplayId;

    private final Executor mMainExecutor;

    private final Region mExcludeRegion = new Region();
    private final Region mUnrestrictedExcludeRegion = new Region();

    // The edge width where touch down is allowed
    private int mEdgeWidth;
    // The slop to distinguish between horizontal and vertical motion
    private final float mTouchSlop;
    // Duration after which we consider the event as longpress.
    private final int mLongPressTimeout;
    // The threshold where the touch needs to be at most, such that the arrow is displayed above the
    // finger, otherwise it will be below
    private final int mMinArrowPosition;
    // The amount by which the arrow is shifted to avoid the finger
    private final int mFingerOffset;


    private final int mNavBarHeight;

    private final PointF mDownPoint = new PointF();
    private boolean mThresholdCrossed = false;
    private boolean mAllowGesture = false;
    private boolean mInRejectedExclusion = false;
    private boolean mIsOnLeftEdge;

    private int mImeHeight = 0;

    private boolean mIsAttached;
    private boolean mIsGesturalModeEnabled;
    private boolean mIsEnabled;
    private boolean mIsInTransientImmersiveStickyState;

    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;

    private final WindowManager mWm;

    private NavigationBarEdgePanel mEdgePanel;
    private WindowManager.LayoutParams mEdgePanelLp;
    private final Rect mSamplingRect = new Rect();
    private RegionSamplingHelper mRegionSamplingHelper;
    private int mLeftInset;
    private int mRightInset;

    private int mEdgeHeight;
    private boolean mBlockImeSpace = true;

    private IntentFilter mIntentFilter;

    private Handler mHandler;
    private AssistManager mAssistManager;
    private int mTimeout = 3000; //ms
    private int mBackSwipeType;
    private int mLeftLongSwipeAction;
    private int mRightLongSwipeAction;
    private boolean mBlockNextEvent;
    private boolean mBackHapticEnabled;

    private final Vibrator mVibrator;
    private boolean mAltVib;

    public EdgeBackGestureHandler(Context context, OverviewProxyService overviewProxyService) {
        final Resources res = context.getResources();
        mContext = context;
        mDisplayId = context.getDisplayId();
        mMainExecutor = context.getMainExecutor();
        mWm = context.getSystemService(WindowManager.class);
        mOverviewProxyService = overviewProxyService;

        // Reduce the default touch slop to ensure that we can intercept the gesture
        // before the app starts to react to it.
        // TODO(b/130352502) Tune this value and extract into a constant
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 0.75f;
        mLongPressTimeout = Math.min(MAX_LONG_PRESS_TIMEOUT,
                ViewConfiguration.getLongPressTimeout());

        mNavBarHeight = res.getDimensionPixelSize(R.dimen.navigation_bar_frame_height);
        mMinArrowPosition = res.getDimensionPixelSize(R.dimen.navigation_edge_arrow_min_y);
        mFingerOffset = res.getDimensionPixelSize(R.dimen.navigation_edge_finger_offset);
        updateCurrentUserResources(res);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mIntentFilter.addDataScheme("package");

        mAssistManager = Dependency.get(AssistManager.class);
        mHandler = new Handler();
        setLongSwipeOptions();

        onSettingsChanged();
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mAltVib = context.getResources().getBoolean(
                R.bool.config_vibrateOnIconAnimation);
    }

    public void updateCurrentUserResources(Resources res) {
        mEdgeWidth = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_backGestureInset);
    }

    private void updateEdgeHeightValue() {
        if (mDisplaySize == null) {
            return;
        }
        int edgeHeightSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.BACK_GESTURE_HEIGHT, 0, UserHandle.USER_CURRENT);
        // edgeHeigthSettings cant be range 0 - 3
        // 0 means full height
        // 1 measns half of the screen
        // 2 means lower third of the screen
        // 3 means lower sixth of the screen
        if (edgeHeightSetting == 0) {
            mEdgeHeight = mDisplaySize.y;
        } else if (edgeHeightSetting == 1) {
            mEdgeHeight = (mDisplaySize.y * 3) / 4;
        } else if (edgeHeightSetting == 2) {
            mEdgeHeight = mDisplaySize.y / 2;
        } else {
            mEdgeHeight = mDisplaySize.y / 4;
        }
    }

    /**
     * @see NavigationBarView#onAttachedToWindow()
     */
    public void onNavBarAttached() {
        mIsAttached = true;
        updateIsEnabled();
    }

    /**
     * @see NavigationBarView#onDetachedFromWindow()
     */
    public void onNavBarDetached() {
        mIsAttached = false;
        updateIsEnabled();
    }

    public void onNavigationModeChanged(int mode, Context currentUserContext) {
        mIsGesturalModeEnabled = QuickStepContract.isGesturalMode(mode);
        updateIsEnabled();
        updateCurrentUserResources(currentUserContext.getResources());
    }

    public void setStateForBackArrowGesture() {
        if (mEdgePanel != null) {
            mEdgePanel.setBackArrowVisibility();
        }
    }

    public void setStateForBackGestureHaptic() {
        if (mEdgePanel != null) {
            mEdgePanel.setBackGestureHaptic();
        }
    }

    public void onSystemUiVisibilityChanged(int systemUiVisibility) {
        mIsInTransientImmersiveStickyState =
                (systemUiVisibility & SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0
                && (systemUiVisibility & NAVIGATION_BAR_TRANSIENT) != 0;
    }

    public void onSettingsChanged() {
        updateEdgeHeightValue();
        updateBlockImeSpace();
    }

    private void updateBlockImeSpace() {
        mBlockImeSpace = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.BACK_GESTURE_BLOCK_IME, 1, UserHandle.USER_CURRENT) == 1;
    }

    private void disposeInputChannel() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
                // Get packageName from Uri
                String packageName = intent.getData().getSchemeSpecificPart();
                // If the package is still installed
                if (Utils.isPackageInstalled(context, packageName)) {
                    // it's an application update, we can skip the rest.
                    return;
                }
                // Get package names currently set as default
                String leftPackageName = Settings.System.getStringForUser(context.getContentResolver(),
                        Settings.System.LEFT_LONG_BACK_SWIPE_APP_ACTION,
                        UserHandle.USER_CURRENT);
                String rightPackageName = Settings.System.getStringForUser(context.getContentResolver(),
                        Settings.System.RIGHT_LONG_BACK_SWIPE_APP_ACTION,
                        UserHandle.USER_CURRENT);
                // if the package name equals to some set value
                if(packageName.equals(leftPackageName)) {
                    // The short application action has to be reset
                    resetApplicationAction(/* isLeftAction */ true);
                }
                if (packageName.equals(rightPackageName)) {
                    // The long application action has to be reset
                    resetApplicationAction(/* isLeftAction */ false);
                }
            }
        }
    };

    private void resetApplicationAction(boolean isLeftAction) {
        if (isLeftAction) {
            // Remove stored values
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.LEFT_LONG_BACK_SWIPE_ACTION, /* no action */ 0,
                    UserHandle.USER_CURRENT);
            Settings.System.putStringForUser(mContext.getContentResolver(),
                    Settings.System.LEFT_LONG_BACK_SWIPE_APP_FR_ACTION, /* none */ "",
                    UserHandle.USER_CURRENT);
        } else {
            // Remove stored values
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION, /* no action */ 0,
                    UserHandle.USER_CURRENT);
            Settings.System.putStringForUser(mContext.getContentResolver(),
                    Settings.System.RIGHT_LONG_BACK_SWIPE_APP_FR_ACTION, /* none */ "",
                    UserHandle.USER_CURRENT);
        }
        // statusbar settings observer will trigger mEdgePanel.setLongSwipeOptions()
    }

    private void updateIsEnabled() {
        boolean isEnabled = mIsAttached && mIsGesturalModeEnabled;
        if (isEnabled == mIsEnabled) {
            return;
        }
        mIsEnabled = isEnabled;
        disposeInputChannel();

        if (mEdgePanel != null) {
            mWm.removeView(mEdgePanel);
            mEdgePanel = null;
            mRegionSamplingHelper.stop();
            mRegionSamplingHelper = null;
        }

        if (!mIsEnabled) {
            WindowManagerWrapper.getInstance().removePinnedStackListener(mImeChangedListener);
            mContext.getSystemService(DisplayManager.class).unregisterDisplayListener(this);

            try {
                WindowManagerGlobal.getWindowManagerService()
                        .unregisterSystemGestureExclusionListener(
                                mGestureExclusionListener, mDisplayId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister window manager callbacks", e);
            }

            mContext.unregisterReceiver(mBroadcastReceiver);
        } else {
            updateDisplaySize();
            mContext.getSystemService(DisplayManager.class).registerDisplayListener(this,
                    mContext.getMainThreadHandler());

            try {
                WindowManagerWrapper.getInstance().addPinnedStackListener(mImeChangedListener);
                WindowManagerGlobal.getWindowManagerService()
                        .registerSystemGestureExclusionListener(
                                mGestureExclusionListener, mDisplayId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to register window manager callbacks", e);
            }

            // Register input event receiver
            mInputMonitor = InputManager.getInstance().monitorGestureInput(
                    "edge-swipe", mDisplayId);
            mInputEventReceiver = new SysUiInputEventReceiver(
                    mInputMonitor.getInputChannel(), Looper.getMainLooper());

            // Add a nav bar panel window
            mEdgePanel = new NavigationBarEdgePanel(mContext);
            mEdgePanelLp = new WindowManager.LayoutParams(
                    mContext.getResources()
                            .getDimensionPixelSize(R.dimen.navigation_edge_panel_width),
                    mContext.getResources()
                            .getDimensionPixelSize(R.dimen.navigation_edge_panel_height),
                    WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            mEdgePanelLp.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mEdgePanelLp.setTitle(TAG + mDisplayId);
            mEdgePanelLp.accessibilityTitle = mContext.getString(R.string.nav_bar_edge_panel);
            mEdgePanelLp.windowAnimations = 0;
            mEdgePanel.setLayoutParams(mEdgePanelLp);
            mWm.addView(mEdgePanel, mEdgePanelLp);
            mRegionSamplingHelper = new RegionSamplingHelper(mEdgePanel,
                    new RegionSamplingHelper.SamplingCallback() {
                        @Override
                        public void onRegionDarknessChanged(boolean isRegionDark) {
                            mEdgePanel.setIsDark(!isRegionDark, true /* animate */);
                        }

                        @Override
                        public Rect getSampledRegion(View sampledView) {
                            return mSamplingRect;
                        }
                    });
            mContext.registerReceiver(mBroadcastReceiver, mIntentFilter);
        }
    }

    private void onInputEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            onMotionEvent((MotionEvent) ev);
        }
    }

    private boolean isWithinTouchRegion(int x, int y) {
        // Disallow if over the IME
        if (y > (mDisplaySize.y - Math.max(mImeHeight, mNavBarHeight))) {
            return false;
        }

        if (mEdgeHeight != 0) {
            if (y < (mDisplaySize.y - Math.max(mImeHeight, mNavBarHeight) - mEdgeHeight)) {
                return false;
            }
        }

        // Disallow if too far from the edge
        if (x > mEdgeWidth + mLeftInset && x < (mDisplaySize.x - mEdgeWidth - mRightInset)) {
            return false;
        }

        // Always allow if the user is in a transient sticky immersive state
        if (mIsInTransientImmersiveStickyState) {
            return true;
        }

        /* if Launcher is showing and want to block back gesture, let's still trigger our custom
        swipe actions at the very bottom of the screen, because we are cool */
        boolean isInExcludedRegion = false;
        if (mBackSwipeType == 1
                || (mLeftLongSwipeAction != 0 && mIsOnLeftEdge)  || (mRightLongSwipeAction != 0 && !mIsOnLeftEdge)) {
            isInExcludedRegion= mExcludeRegion.contains(x, y)
                && y < ((mDisplaySize.y / 4) * 3);
        } else {
            isInExcludedRegion= mExcludeRegion.contains(x, y);
        }
        if (isInExcludedRegion) {
            mOverviewProxyService.notifyBackAction(false /* completed */, -1, -1,
                    false /* isButton */, !mIsOnLeftEdge);
            StatsLog.write(StatsLog.BACK_GESTURE_REPORTED_REPORTED,
                    StatsLog.BACK_GESTURE__TYPE__INCOMPLETE_EXCLUDED, y,
                    mIsOnLeftEdge ? StatsLog.BACK_GESTURE__X_LOCATION__LEFT :
                            StatsLog.BACK_GESTURE__X_LOCATION__RIGHT);
        } else {
            mInRejectedExclusion = mUnrestrictedExcludeRegion.contains(x, y);
        }
        return !isInExcludedRegion;
    }

    private void cancelGesture(MotionEvent ev) {
        // Send action cancel to reset all the touch events
        mHandler.removeCallbacksAndMessages(null);
        mAllowGesture = false;
        mInRejectedExclusion = false;
        MotionEvent cancelEv = MotionEvent.obtain(ev);
        cancelEv.setAction(MotionEvent.ACTION_CANCEL);
        mEdgePanel.handleTouch(cancelEv);
        cancelEv.recycle();
    }

    public void setLongSwipeOptions() {
        mBackSwipeType = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.BACK_SWIPE_TYPE, 0,
            UserHandle.USER_CURRENT);
        if (mEdgePanel != null) {
            mEdgePanel.setExtendedSwipe();
        }
        mTimeout = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.LONG_BACK_SWIPE_TIMEOUT, 2000,
            UserHandle.USER_CURRENT);
        mLeftLongSwipeAction = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.LEFT_LONG_BACK_SWIPE_ACTION, 0,
            UserHandle.USER_CURRENT);
        mRightLongSwipeAction = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION, 0,
            UserHandle.USER_CURRENT);
        mBackHapticEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.BACK_GESTURE_HAPTIC, 1,
            UserHandle.USER_CURRENT) == 1;
    }

    private void onMotionEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // Verify if this is in within the touch region and we aren't in immersive mode, and
            // either the bouncer is showing or the notification panel is hidden
            int stateFlags = mOverviewProxyService.getSystemUiStateFlags();
            mIsOnLeftEdge = ev.getX() <= mEdgeWidth + mLeftInset;
            mInRejectedExclusion = false;
            mAllowGesture = !QuickStepContract.isBackGestureDisabled(stateFlags)
                    && isWithinTouchRegion((int) ev.getX(), (int) ev.getY());
            if (mAllowGesture) {
                mEdgePanelLp.gravity = mIsOnLeftEdge
                        ? (Gravity.LEFT | Gravity.TOP)
                        : (Gravity.RIGHT | Gravity.TOP);
                mEdgePanel.setIsLeftPanel(mIsOnLeftEdge);
                mEdgePanel.handleTouch(ev);
                updateEdgePanelPosition(ev.getY());
                mWm.updateViewLayout(mEdgePanel, mEdgePanelLp);
                mRegionSamplingHelper.start(mSamplingRect);

                mDownPoint.set(ev.getX(), ev.getY());
                mThresholdCrossed = false;
            }
        } else if (mAllowGesture && !mBlockNextEvent) {
            if (!mThresholdCrossed) {
                // mThresholdCrossed is true only after the first move event
                // then other events will go straight to "forward touch" line
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    // We do not support multi touch for back gesture
                    cancelGesture(ev);
                    return;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    int elapsedTime = (int)(ev.getEventTime() - ev.getDownTime());
                    if (elapsedTime > mLongPressTimeout) {
                        cancelGesture(ev);
                        return;
                    }
                    float dx = Math.abs(ev.getX() - mDownPoint.x);
                    float dy = Math.abs(ev.getY() - mDownPoint.y);
                    if (dy > dx && dy > mTouchSlop) {
                        cancelGesture(ev);
                        return;

                    } else if (dx > dy && dx > mTouchSlop) {
                        mThresholdCrossed = true;
                        if (mBackSwipeType == 0 && ((mLeftLongSwipeAction != 0 && mIsOnLeftEdge)
                                || (mRightLongSwipeAction != 0 && !mIsOnLeftEdge))) {
                            mHandler.postDelayed(mLongSwipeAction, (mTimeout - elapsedTime));
                        }
                        // Capture inputs
                        mInputMonitor.pilferPointers();
                    }
                }

            }

            // forward touch
            mEdgePanel.handleTouch(ev);

            boolean isUp = action == MotionEvent.ACTION_UP;
            boolean isCancel = action == MotionEvent.ACTION_CANCEL;
            boolean isMove = action == MotionEvent.ACTION_MOVE;

            if (isMove && mBackSwipeType == 1) {
                float deltaX = Math.abs(ev.getX() - mDownPoint.x);
                if (deltaX  > ((mDisplaySize.x / 4) * 3)) {
                    mLongSwipeAction.run();
                }
            }

            if (isUp || isCancel) {
                mHandler.removeCallbacksAndMessages(null);
            }
            if (isUp) {
                boolean performAction = mEdgePanel.shouldTriggerBack();
                if (performAction) {
                    // Perform back
                    sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
                    sendEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK);
                }
                mOverviewProxyService.notifyBackAction(performAction, (int) mDownPoint.x,
                        (int) mDownPoint.y, false /* isButton */, !mIsOnLeftEdge);
                int backtype = performAction ? (mInRejectedExclusion
                        ? StatsLog.BACK_GESTURE__TYPE__COMPLETED_REJECTED :
                                StatsLog.BACK_GESTURE__TYPE__COMPLETED) :
                                        StatsLog.BACK_GESTURE__TYPE__INCOMPLETE;
                StatsLog.write(StatsLog.BACK_GESTURE_REPORTED_REPORTED, backtype,
                        (int) mDownPoint.y, mIsOnLeftEdge
                                ? StatsLog.BACK_GESTURE__X_LOCATION__LEFT :
                                StatsLog.BACK_GESTURE__X_LOCATION__RIGHT);
            }
            if (isUp || isCancel) {
                mRegionSamplingHelper.stop();
            } else {
                updateSamplingRect();
                mRegionSamplingHelper.updateSamplingRect();
            }
        } else if (mBlockNextEvent) {
            mBlockNextEvent = false;
            cancelGesture(ev);
        }
    }

    private LongSwipeRunnable mLongSwipeAction = new LongSwipeRunnable();
    private class LongSwipeRunnable implements Runnable {
        @Override
        public void run() {
            mBlockNextEvent = true;
            mEdgePanel.resetOnDown();
            triggerAction(mIsOnLeftEdge);
            if (mBackHapticEnabled) {
                if (mAltVib) {
                    mVibrator.vibrate(VibrationEffect.EFFECT_CLICK);
                } else {
                    mVibrator.vibrate(VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK));
                }
            }
        }
    }

    public void triggerAction(boolean isLeftPanel) {
        int action = isLeftPanel ? mLeftLongSwipeAction : mRightLongSwipeAction;
        switch (action) {
            case 0: // No action
            default:
                break;
            case 1: // Assistant
                mAssistManager.startAssist(new Bundle() /* args */);
                break;
            case 2: // Voice search
                RRActionUtils.launchVoiceSearch(mContext);
                break;
            case 3: // Camera
                RRActionUtils.launchCamera(mContext);
                break;
            case 4: // Flashlight
                RRActionUtils.toggleCameraFlash();
                break;
            case 5: // Application
                launchApp(mContext, isLeftPanel);
                break;
            case 6: // Volume panel
                RRActionUtils.toggleVolumePanel(mContext);
                break;
            case 7: // Screen off
                RRActionUtils.switchScreenOff(mContext);
                break;
            case 8: // Screenshot
                RRActionUtils.takeScreenshot(true);
                break;
            case 9: // Notification panel
                RRActionUtils.toggleNotifications();
                break;
            case 10: // QS panel
                RRActionUtils.toggleQsPanel();
                break;
            case 11: // Clear notifications
                RRActionUtils.clearAllNotifications();
                break;
            case 12: // Ringer modes
                RRActionUtils.toggleRingerModes(mContext);
                break;
            case 13: // Kill app
                RRActionUtils.killForegroundApp();
                break;
            case 14: // Skip song
                RRActionUtils.sendSystemKeyToStatusBar(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case 15: // Previous song
                RRActionUtils.sendSystemKeyToStatusBar(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case 16: // Partial screenshot
                RRActionUtils.takeScreenshot(false);
                break;
        }
    }

    private void launchApp(Context context, boolean isLeftPanel) {
        Intent intent = null;
        String packageName = Settings.System.getStringForUser(context.getContentResolver(),
                isLeftPanel ? Settings.System.LEFT_LONG_BACK_SWIPE_APP_ACTION
                : Settings.System.RIGHT_LONG_BACK_SWIPE_APP_ACTION,
                UserHandle.USER_CURRENT);
        String activity = Settings.System.getStringForUser(context.getContentResolver(),
                isLeftPanel ? Settings.System.LEFT_LONG_BACK_SWIPE_APP_ACTIVITY_ACTION
                : Settings.System.RIGHT_LONG_BACK_SWIPE_APP_ACTIVITY_ACTION,
                UserHandle.USER_CURRENT);
        boolean launchActivity = activity != null && !TextUtils.equals("NONE", activity);
        try {
            if (launchActivity) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName(packageName, activity);
            } else {
                intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Exception e) {
        }
    }

    private void updateEdgePanelPosition(float touchY) {
        float position = touchY - mFingerOffset;
        position = Math.max(position, mMinArrowPosition);
        position = (position - mEdgePanelLp.height / 2.0f);
        mEdgePanelLp.y = MathUtils.constrain((int) position, 0, mDisplaySize.y);
        updateSamplingRect();
    }

    private void updateSamplingRect() {
        int top = mEdgePanelLp.y;
        int left = mIsOnLeftEdge ? mLeftInset : mDisplaySize.x - mRightInset - mEdgePanelLp.width;
        int right = left + mEdgePanelLp.width;
        int bottom = top + mEdgePanelLp.height;
        mSamplingRect.set(left, top, right, bottom);
        mEdgePanel.adjustRectToBoundingBox(mSamplingRect);
    }

    @Override
    public void onDisplayAdded(int displayId) { }

    @Override
    public void onDisplayRemoved(int displayId) { }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == mDisplayId) {
            updateDisplaySize();
        }
    }

    private void updateDisplaySize() {
        mContext.getSystemService(DisplayManager.class)
                .getDisplay(mDisplayId)
                .getRealSize(mDisplaySize);
        updateEdgeHeightValue();
    }

    private void sendEvent(int action, int code) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent ev = new KeyEvent(when, when, action, code, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_NAVIGATION_BAR);

        // Bubble controller will give us a valid display id if it should get the back event
        BubbleController bubbleController = Dependency.get(BubbleController.class);
        int bubbleDisplayId = bubbleController.getExpandedDisplayId(mContext);
        if (code == KeyEvent.KEYCODE_BACK && bubbleDisplayId != INVALID_DISPLAY) {
            ev.setDisplayId(bubbleDisplayId);
        }
        InputManager.getInstance().injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public void setInsets(int leftInset, int rightInset) {
        mLeftInset = leftInset;
        mRightInset = rightInset;
    }

    public void dump(PrintWriter pw) {
        pw.println("EdgeBackGestureHandler:");
        pw.println("  mIsEnabled=" + mIsEnabled);
        pw.println("  mAllowGesture=" + mAllowGesture);
        pw.println("  mInRejectedExclusion" + mInRejectedExclusion);
        pw.println("  mExcludeRegion=" + mExcludeRegion);
        pw.println("  mUnrestrictedExcludeRegion=" + mUnrestrictedExcludeRegion);
        pw.println("  mImeHeight=" + mImeHeight);
        pw.println("  mIsAttached=" + mIsAttached);
        pw.println("  mEdgeWidth=" + mEdgeWidth);
    }

    class SysUiInputEventReceiver extends InputEventReceiver {
        SysUiInputEventReceiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        public void onInputEvent(InputEvent event) {
            EdgeBackGestureHandler.this.onInputEvent(event);
            finishInputEvent(event, true);
        }
    }
}

