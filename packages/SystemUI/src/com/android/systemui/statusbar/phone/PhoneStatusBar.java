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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.CustomTheme;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewPropertyAnimator;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarNotification;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.GestureCatcherView;
import com.android.systemui.statusbar.GestureRecorder;
import com.android.systemui.statusbar.NavigationBarView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationData.Entry;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.SbBatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.DateView;
import com.android.systemui.statusbar.policy.IntruderAlertView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.policy.OnSizeChangedListener;
import com.android.systemui.statusbar.policy.Prefs;
import com.android.systemui.statusbar.toggles.ToggleManager;
import com.android.systemui.aokp.AwesomeAction;
import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.systemui.aokp.AokpSwipeRibbon;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class PhoneStatusBar extends BaseStatusBar {
    static final String TAG = "PhoneStatusBar";
    public static final boolean DEBUG = BaseStatusBar.DEBUG;
    public static final boolean SPEW = DEBUG;
    public static final boolean DEBUG_GESTURES = false;

    public static final boolean DEBUG_CLINGS = false;

    public static final boolean ENABLE_NOTIFICATION_PANEL_CLING = false;

    public static final boolean SETTINGS_DRAG_SHORTCUT = true;

    // additional instrumentation for testing purposes; intended to be left on during development
    public static final boolean CHATTY = DEBUG;

    public static final String ACTION_STATUSBAR_START
            = "com.android.internal.policy.statusbar.START";

    private static final int MSG_OPEN_NOTIFICATION_PANEL = 1000;
    private static final int MSG_CLOSE_PANELS = 1001;
    private static final int MSG_OPEN_SETTINGS_PANEL = 1002;
    // 1020-1030 reserved for BaseStatusBar

    // will likely move to a resource or other tunable param at some point
    private static final int INTRUDER_ALERT_DECAY_MS = 0; // disabled, was 10000;

    private static final boolean CLOSE_PANEL_WHEN_EMPTIED = true;

    private static final int NOTIFICATION_PRIORITY_MULTIPLIER = 10; // see NotificationManagerService
    private static final int HIDE_ICONS_BELOW_SCORE = Notification.PRIORITY_LOW * NOTIFICATION_PRIORITY_MULTIPLIER;

    // fling gesture tuning parameters, scaled to display density
    private float mSelfExpandVelocityPx; // classic value: 2000px/s
    private float mSelfCollapseVelocityPx; // classic value: 2000px/s (will be negated to collapse "up")
    private float mFlingExpandMinVelocityPx; // classic value: 200px/s
    private float mFlingCollapseMinVelocityPx; // classic value: 200px/s
    private float mCollapseMinDisplayFraction; // classic value: 0.08 (25px/min(320px,480px) on G1)
    private float mExpandMinDisplayFraction; // classic value: 0.5 (drag open halfway to expand)
    private float mFlingGestureMaxXVelocityPx; // classic value: 150px/s

    private float mExpandAccelPx; // classic value: 2000px/s/s
    private float mCollapseAccelPx; // classic value: 2000px/s/s (will be negated to collapse "up")

    private float mFlingGestureMaxOutputVelocityPx; // how fast can it really go? (should be a little 
                                                    // faster than mSelfCollapseVelocityPx)

    PhoneStatusBarPolicy mIconPolicy;

    private IWindowManager mWm;

    private AokpSwipeRibbon mAokpSwipeRibbonLeft;
    private AokpSwipeRibbon mAokpSwipeRibbonRight;
    private AokpSwipeRibbon mAokpSwipeRibbonBottom;

    // These are no longer handled by the policy, because we need custom strategies for them
    BluetoothController mBluetoothController;
    BatteryController mBatteryController;
    SbBatteryController mSbBatteryController;
    LocationController mLocationController;
    NetworkController mNetworkController;

    int mNaturalBarHeight = -1;
    int mIconSize = -1;
    int mIconHPadding = -1;
    Display mDisplay;
    Point mCurrentDisplaySize = new Point();

    IDreamManager mDreamManager;

    StatusBarWindowView mStatusBarWindow;
    PhoneStatusBarView mStatusBarView;

    int mPixelFormat;
    Object mQueueLock = new Object();

    // viewgroup containing the normal contents of the statusbar
    LinearLayout mStatusBarContents;

    LinearLayout mRibbonNotif;
    LinearLayout mRibbonQS;
    
    // right-hand icons
    LinearLayout mSystemIconArea;
    
    // left-hand icons 
    LinearLayout mStatusIcons;
    // layout for center clock
    LinearLayout mCenterClockLayout;
    // the icons themselves
    IconMerger mNotificationIcons;
    // [+>
    View mMoreIcon;

    // expanded notifications
    NotificationPanelView mNotificationPanel; // the sliding/resizing panel within the notification window
    ScrollView mScrollView;
    View mExpandedContents;
    int mNotificationPanelGravity;
    int mNotificationPanelMarginBottomPx, mNotificationPanelMarginPx;
    float mNotificationPanelMinHeightFrac;
    boolean mNotificationPanelIsFullScreenWidth;
    TextView mNotificationPanelDebugText;

    // settings
    ToggleManager mToggleManager;
    boolean mHasSettingsPanel, mHasFlipSettings;
    int mToggleStyle;
    SettingsPanelView mSettingsPanel;
    View mFlipSettingsView;
    QuickSettingsContainerView mSettingsContainer;
    int mSettingsPanelGravity;

    // top bar
    View mNotificationPanelHeader;
    View mDateTimeView; 
    View mClearButton;
    ImageView mSettingsButton, mNotificationButton;

    private int shortClick = 0;
    private int longClick = 1;
    private int doubleClick = 2;
    private int doubleClickCounter = 0;

    public String[] mClockActions = new String[3];
    private boolean mClockDoubleClicked;

    // carrier/wifi label
    private TextView mCarrierLabel;
    private boolean mCarrierLabelVisible = false;
    private int mCarrierLabelHeight;
    private TextView mEmergencyCallLabel;
    private int mNotificationHeaderHeight;

    private boolean mShowCarrierInPanel = false;

    // position
    int[] mPositionTmp = new int[2];
    boolean mExpandedVisible;

    // the date view
    DateView mDateView;

    // for immersive activities
    private IntruderAlertView mIntruderAlertView;

    // on-screen navigation buttons
    private NavigationBarView mNavigationBarView = null;
    private boolean mNavBarAutoHide = false;
    private GestureCatcherView mGesturePanel;
    private boolean mAutoHideVisible = false;
    private int mAutoHideTimeOut = 3000;
    private boolean mLockscreenOn;

    // the tracker view
    int mTrackingPosition; // the position of the top of the tracking view.

    // ticker
    private Ticker mTicker;
    private View mTickerView;
    private boolean mTicking;

    // Tracking finger for opening/closing.
    int mEdgeBorder; // corresponds to R.dimen.status_bar_edge_ignore
    boolean mTracking;
    VelocityTracker mVelocityTracker;

    // help screen
    private boolean mClingShown;
    private ViewGroup mCling;
    private boolean mSuppressStatusBarDrags; // while a cling is up, briefly deaden the bar to give things time to settle

    boolean mAnimating;
    boolean mClosing; // only valid when mAnimating; indicates the initial acceleration
    float mAnimY;
    float mAnimVel;
    float mAnimAccel;
    long mAnimLastTimeNanos;
    boolean mAnimatingReveal = false;
    int mViewDelta;
    float mFlingVelocity;
    int mFlingY;
    int[] mAbsPos = new int[2];
    Runnable mPostCollapseCleanup = null;

    private Animator mLightsOutAnimation;
    private Animator mLightsOnAnimation;

    // last theme that was applied in order to detect theme change (as opposed
    // to some other configuration change).
    CustomTheme mCurrentTheme;
    private boolean mRecreating = false;

    // for disabling the status bar
    int mDisabled = 0;

    // tracking calls to View.setSystemUiVisibility()
    int mSystemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;

    DisplayMetrics mDisplayMetrics = new DisplayMetrics();

    // XXX: gesture research
    private final GestureRecorder mGestureRec = DEBUG_GESTURES
        ? new GestureRecorder("/sdcard/statusbar_gestures.dat") 
        : null;

    private int mNavigationIconHints = 0;
    private final Animator.AnimatorListener mMakeIconsInvisible = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            // double-check to avoid races
            if (mStatusBarContents.getAlpha() == 0) {
                if (DEBUG) Slog.d(TAG, "makeIconsInvisible");
                mStatusBarContents.setVisibility(View.INVISIBLE);
            }
        }
    };

    // ensure quick settings is disabled until the current user makes it through the setup wizard
    private boolean mUserSetup = false;
    private ContentObserver mUserSetupObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean userSetup = 0 != Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE,
                    0 /*default */,
                    mCurrentUserId);
            if (MULTIUSER_DEBUG) Slog.d(TAG, String.format("User setup changed: " +
                    "selfChange=%s userSetup=%s mUserSetup=%s",
                    selfChange, userSetup, mUserSetup));
            if (mSettingsButton != null && mHasFlipSettings) {
                mSettingsButton.setVisibility(userSetup ? View.VISIBLE : View.INVISIBLE);
            }
            if (mSettingsPanel != null) {
                mSettingsPanel.setEnabled(userSetup);
            }
            if (userSetup != mUserSetup) {
                mUserSetup = userSetup;
                if (!mUserSetup && mStatusBarView != null)
                    animateCollapseQuickSettings();
            }
        }
    };

    @Override
    public void start() {
        mDisplay = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();

        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.checkService(DreamService.DREAM_SERVICE));

        CustomTheme currentTheme = mContext.getResources().getConfiguration().customTheme;
        if (currentTheme != null) {
            mCurrentTheme = (CustomTheme)currentTheme.clone();
        }

        super.start(); // calls createAndAddWindows()

        addNavigationBar();

        if (ENABLE_INTRUDERS) addIntruderView();

        // Lastly, call to the icon policy to install/update all the icons.
        mIconPolicy = new PhoneStatusBarPolicy(mContext);
    }

    // ================================================================================
    // Constructing the view
    // ================================================================================
    protected PhoneStatusBarView makeStatusBarView() {
        final Context context = mContext;

        Resources res = context.getResources();

        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        updateDisplaySize(); // populates mDisplayMetrics
        loadDimens();

        mIconSize = res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_icon_size);

        mStatusBarWindow = (StatusBarWindowView) View.inflate(context,
                R.layout.super_status_bar, null);
        mStatusBarWindow.mService = this;
        mStatusBarWindow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mExpandedVisible && !mAnimating) {
                        animateCollapsePanels();
                    }
                }
                return mStatusBarWindow.onTouchEvent(event);
            }});

        mStatusBarView = (PhoneStatusBarView) mStatusBarWindow.findViewById(R.id.status_bar);
        mStatusBarView.setBar(this);

        PanelHolder holder = (PanelHolder) mStatusBarWindow.findViewById(R.id.panel_holder);
        mStatusBarView.setPanelHolder(holder);

        mNotificationPanel = (NotificationPanelView) mStatusBarWindow.findViewById(R.id.notification_panel);
        mNotificationPanel.setStatusBar(this);
        mNotificationPanelIsFullScreenWidth =
            (mNotificationPanel.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT);

        // make the header non-responsive to clicks
        mNotificationPanel.findViewById(R.id.header).setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true; // e eats everything
                    }
                });

        if (!ActivityManager.isHighEndGfx()) {
            mStatusBarWindow.setBackground(null);
            mNotificationPanel.setBackground(new FastColorDrawable(context.getResources().getColor(
                    R.color.notification_panel_solid_background)));
        }
        if (ENABLE_INTRUDERS) {
            mIntruderAlertView = (IntruderAlertView) View.inflate(context, R.layout.intruder_alert, null);
            mIntruderAlertView.setVisibility(View.GONE);
            mIntruderAlertView.setBar(this);
        }
        if (MULTIUSER_DEBUG) {
            mNotificationPanelDebugText = (TextView) mNotificationPanel.findViewById(R.id.header_debug_info);
            mNotificationPanelDebugText.setVisibility(View.VISIBLE);
        }

        try {
            boolean showNav = mWindowManagerService.hasNavigationBar();
            if (DEBUG) Slog.v(TAG, "hasNavigationBar=" + showNav);
            if (showNav || mNavBarAutoHide) {
                mNavigationBarView =
                    (NavigationBarView) View.inflate(context, R.layout.navigation_bar, null);

                mNavigationBarView.setDisabledFlags(mDisabled);
                mNavigationBarView.setBar(this);
                if (mNavBarAutoHide) {
                    setupAutoHide();
                }
            }
        } catch (RemoteException ex) {
            // no window manager? good luck with that
        }

        // figure out which pixel-format to use for the status bar.
        mPixelFormat = PixelFormat.OPAQUE;

        mSystemIconArea = (LinearLayout) mStatusBarView.findViewById(R.id.system_icon_area);
        mStatusIcons = (LinearLayout)mStatusBarView.findViewById(R.id.statusIcons);
        mNotificationIcons = (IconMerger)mStatusBarView.findViewById(R.id.notificationIcons);
        mNotificationIcons.setOverflowIndicator(mMoreIcon);
        mStatusBarContents = (LinearLayout)mStatusBarView.findViewById(R.id.status_bar_contents);
        mCenterClockLayout = (LinearLayout)mStatusBarView.findViewById(R.id.center_clock_layout);
        mTickerView = mStatusBarView.findViewById(R.id.ticker);

        mPile = (NotificationRowLayout)mStatusBarWindow.findViewById(R.id.latestItems);
        mPile.setLayoutTransitionsEnabled(false);
        mPile.setLongPressListener(getNotificationLongClicker());
        mExpandedContents = mPile; // was: expanded.findViewById(R.id.notificationLinearLayout);

        mNotificationPanelHeader = mStatusBarWindow.findViewById(R.id.header);

        mClearButton = mStatusBarWindow.findViewById(R.id.clear_all_button);
        mClearButton.setOnClickListener(mClearButtonListener);
        mClearButton.setAlpha(0f);
        mClearButton.setVisibility(View.INVISIBLE);
        mClearButton.setEnabled(false);
        mDateView = (DateView)mStatusBarWindow.findViewById(R.id.date);

        mHasSettingsPanel = res.getBoolean(R.bool.config_hasSettingsPanel);
        mHasFlipSettings = res.getBoolean(R.bool.config_hasFlipSettingsPanel);

        mDateTimeView = mNotificationPanelHeader.findViewById(R.id.datetime);
        if (mDateTimeView != null) {
            mDateTimeView.setOnClickListener(mClockClickListener);
            mDateTimeView.setOnLongClickListener(mClockLongClickListener);
            mDateTimeView.setEnabled(true);
        }

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();

        mSettingsButton = (ImageView) mStatusBarWindow.findViewById(R.id.settings_button);
        if (mSettingsButton != null) {
            mSettingsButton.setOnClickListener(mSettingsButtonListener);
            if (mHasSettingsPanel) {
                if (mStatusBarView.hasFullWidthNotifications()) {
                    // the settings panel is hiding behind this button
                    mSettingsButton.setImageResource(R.drawable.ic_notify_quicksettings);
                    mSettingsButton.setVisibility(View.VISIBLE);
                } else {
                    // there is a settings panel, but it's on the other side of the (large) screen
                    final View buttonHolder = mStatusBarWindow.findViewById(
                            R.id.settings_button_holder);
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
            mNotificationButton = (ImageView) mStatusBarWindow.findViewById(R.id.notification_button);
            if (mNotificationButton != null) {
                mNotificationButton.setOnClickListener(mNotificationButtonListener);
            }
        }

        mScrollView = (ScrollView)mStatusBarWindow.findViewById(R.id.scroll);
        mScrollView.setVerticalScrollBarEnabled(false); // less drawing during pulldowns
        if (!mNotificationPanelIsFullScreenWidth) {
            mScrollView.setSystemUiVisibility(
                    View.STATUS_BAR_DISABLE_NOTIFICATION_TICKER |
                    View.STATUS_BAR_DISABLE_NOTIFICATION_ICONS |
                    View.STATUS_BAR_DISABLE_CLOCK);
        }

        mTicker = new MyTicker(context, mStatusBarView);

        TickerView tickerView = (TickerView)mStatusBarView.findViewById(R.id.tickerText);
        tickerView.mTicker = mTicker;

        mEdgeBorder = res.getDimensionPixelSize(R.dimen.status_bar_edge_ignore);

        // set the inital view visibility
        setAreThereNotifications();

        // Other icons
        mLocationController = new LocationController(mContext); // will post a notification
        mBatteryController = new BatteryController(mContext);
        mSbBatteryController = (SbBatteryController)mStatusBarView.findViewById(R.id.battery_cluster);
        mNetworkController = new NetworkController(mContext);
        mBluetoothController = new BluetoothController(mContext);

        SignalClusterView signalCluster = (SignalClusterView)mStatusBarView.findViewById(R.id.signal_cluster);
        mNetworkController.addSignalCluster(signalCluster);
        signalCluster.setNetworkController(mNetworkController);

        signalCluster = (SignalClusterView)mStatusBarView.findViewById(R.id.signal_cluster_alt);
        mNetworkController.addSignalCluster(signalCluster);
        signalCluster.setNetworkController(mNetworkController);

        mEmergencyCallLabel = (TextView)mStatusBarWindow.findViewById(R.id.emergency_calls_only);
        if (mEmergencyCallLabel != null) {
            mNetworkController.addEmergencyLabelView(mEmergencyCallLabel);
            mEmergencyCallLabel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { }});
            mEmergencyCallLabel.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    updateCarrierLabelVisibility(false);
                }});
        }

        mCarrierLabel = (TextView)mStatusBarWindow.findViewById(R.id.carrier_label);
        mShowCarrierInPanel = (mCarrierLabel != null);
        if (DEBUG) Slog.v(TAG, "carrierlabel=" + mCarrierLabel + " show=" + mShowCarrierInPanel);
        if (mShowCarrierInPanel) {
            mCarrierLabel.setVisibility(mCarrierLabelVisible ? View.VISIBLE : View.INVISIBLE);

            // for mobile devices, we always show mobile connection info here (SPN/PLMN)
            // for other devices, we show whatever network is connected
            if (mNetworkController.hasMobileDataFeature()) {
                mNetworkController.addMobileLabelView(mCarrierLabel);
            } else {
                mNetworkController.addCombinedLabelView(mCarrierLabel);
            }

            // set up the dynamic hide/show of the label
            mPile.setOnSizeChangedListener(new OnSizeChangedListener() {
                @Override
                public void onSizeChanged(View view, int w, int h, int oldw, int oldh) {
                    updateCarrierLabelVisibility(false);
                }
            });
        }

        // Quick Settings (where available, some restrictions apply)
        if (mHasSettingsPanel) {
            // first, figure out where quick settings should be inflated
            final View settings_stub;
            if (mHasFlipSettings) {
                // a version of quick settings that flips around behind the notifications
                settings_stub = mStatusBarWindow.findViewById(R.id.flip_settings_stub);
                if (settings_stub != null) {
                    mFlipSettingsView = ((ViewStub)settings_stub).inflate();
                    mFlipSettingsView.setVisibility(View.GONE);
                    mFlipSettingsView.setVerticalScrollBarEnabled(false);
                }
            } else {
                // full quick settings panel
                settings_stub = mStatusBarWindow.findViewById(R.id.quick_settings_stub);
                if (settings_stub != null) {
                    mSettingsPanel = (SettingsPanelView) ((ViewStub)settings_stub).inflate();
                } else {
                    mSettingsPanel = (SettingsPanelView) mStatusBarWindow.findViewById(R.id.settings_panel);
                }

                if (mSettingsPanel != null) {
                    if (!ActivityManager.isHighEndGfx()) {
                        mSettingsPanel.setBackground(new FastColorDrawable(context.getResources().getColor(
                                R.color.notification_panel_solid_background)));
                    }
                }
            }

            // wherever you find it, Quick Settings needs a container to survive
            mSettingsContainer = (QuickSettingsContainerView)
                    mStatusBarWindow.findViewById(R.id.quick_settings_container);

            mAokpSwipeRibbonBottom = new AokpSwipeRibbon(mContext,null,"bottom");
            mAokpSwipeRibbonLeft = new AokpSwipeRibbon(mContext,null,"left");
            mAokpSwipeRibbonRight = new AokpSwipeRibbon(mContext,null,"right");

            mToggleManager = new ToggleManager(mContext);
            mToggleManager.setControllers(mBluetoothController, mNetworkController, mBatteryController,
                    mLocationController, null);
            mAokpSwipeRibbonLeft.setControllers(mBluetoothController, mNetworkController, mBatteryController,
                    mLocationController, null);
            mAokpSwipeRibbonRight.setControllers(mBluetoothController, mNetworkController, mBatteryController,
                    mLocationController, null);
            if (mToggleStyle == ToggleManager.STYLE_SCROLLABLE) {
                mToggleManager.setContainer((LinearLayout) mNotificationPanel.findViewById(R.id.quick_toggles),
                        ToggleManager.STYLE_SCROLLABLE);
            } else {
                mToggleManager.setContainer((LinearLayout) mNotificationPanel.findViewById(R.id.quick_toggles),
                    ToggleManager.STYLE_TRADITIONAL);
            }
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

        mClingShown = ! (DEBUG_CLINGS 
            || !Prefs.read(mContext).getBoolean(Prefs.SHOWN_QUICK_SETTINGS_HELP, false));

        if (!ENABLE_NOTIFICATION_PANEL_CLING || ActivityManager.isRunningInTestHarness()) {
            mClingShown = true;
        }

//        final ImageView wimaxRSSI =
//                (ImageView)sb.findViewById(R.id.wimax_signal);
//        if (wimaxRSSI != null) {
//            mNetworkController.addWimaxIconView(wimaxRSSI);
//        }

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        context.registerReceiver(mBroadcastReceiver, filter);

        // listen for USER_SETUP_COMPLETE setting (per-user)
        resetUserSetupObserver();

        mTransparencyManager.setStatusbar(mStatusBarView);
        updateRibbonTargets();
        return mStatusBarView;
    }

    @Override
    protected View getStatusBarView() {
        return mStatusBarView;
    }

    @Override
    protected WindowManager.LayoutParams getRecentsLayoutParams(LayoutParams layoutParams) {
        boolean opaque = false;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                layoutParams.width,
                layoutParams.height,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                (opaque ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT));
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        } else {
            lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            lp.dimAmount = 0.75f;
        }
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        lp.setTitle("RecentsPanel");
        lp.windowAnimations = com.android.internal.R.style.Animation_RecentApplications;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        return lp;
    }

    @Override
    protected WindowManager.LayoutParams getSearchLayoutParams(LayoutParams layoutParams) {
        boolean opaque = false;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                (opaque ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT));
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        lp.setTitle("SearchPanel");
        // TODO: Define custom animation for Search panel
        lp.windowAnimations = com.android.internal.R.style.Animation_RecentApplications;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        return lp;
    }

    @Override
    public void toggleNotificationShade() {
        int msg = (mExpandedVisible)
                ? MSG_CLOSE_PANELS : MSG_OPEN_NOTIFICATION_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    void onBarViewDetached() {
     //   WindowManagerImpl.getDefault().removeView(mStatusBarWindow);
    }

    @Override
    protected void updateSearchPanel() {
        super.updateSearchPanel();
        mSearchPanelView.setStatusBarView(mNavigationBarView);
        mNavigationBarView.setDelegateView(mSearchPanelView);
    }

    @Override
    public void showSearchPanel() {
        super.showSearchPanel();
        mHandler.removeCallbacks(mShowSearchPanel);
        // need to keep NavBar from trying to hide
        if (mNavBarAutoHide) {
            mHandler.removeCallbacks(delayHide);
        }

        // we want to freeze the sysui state wherever it is
        mSearchPanelView.setSystemUiVisibility(mSystemUiVisibility);

        WindowManager.LayoutParams lp =
            (android.view.WindowManager.LayoutParams) mNavigationBarView.getLayoutParams();
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mWindowManager.updateViewLayout(mNavigationBarView, lp);
    }

    @Override
    public void hideSearchPanel() {
        super.hideSearchPanel();
        WindowManager.LayoutParams lp =
            (android.view.WindowManager.LayoutParams) mNavigationBarView.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mWindowManager.updateViewLayout(mNavigationBarView, lp);
        // reset time for autohide NavBar if necessary
        if (mNavBarAutoHide && mAutoHideTimeOut > 0) {
            mHandler.removeCallbacks(delayHide); // reset
            mHandler.postDelayed(delayHide,mAutoHideTimeOut);
        }
    }

    protected int getStatusBarGravity() {
        return Gravity.TOP | Gravity.FILL_HORIZONTAL;
    }

    public int getStatusBarHeight() {
        if (mNaturalBarHeight < 0) {
            final Resources res = mContext.getResources();
            mNaturalBarHeight =
                    res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
        }
        return mNaturalBarHeight;
    }

    private int mShowSearchHoldoff = 0;
    private Runnable mShowSearchPanel = new Runnable() {
        public void run() {
            showSearchPanel();
            awakenDreams();
        }
    };

    View.OnTouchListener mHomeSearchActionListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!shouldDisableNavbarGestures()) {
                    mHandler.removeCallbacks(mShowSearchPanel);
                    mHandler.postDelayed(mShowSearchPanel, mShowSearchHoldoff);
                }
            break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mShowSearchPanel);
                awakenDreams();
            break;
        }
        return false;
        }
    };

    private void awakenDreams() {
        if (mDreamManager != null) {
            try {
                mDreamManager.awaken();
            } catch (RemoteException e) {
                // fine, stay asleep then
            }
        }
    }

    View.OnTouchListener mNavBarListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v,MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                // Action is outside the NavBar, immediately hide the NavBar
                mHandler.removeCallbacks(delayHide);
                if (mGesturePanel != null) {
                    // possible that we disabled AutoHide, but still have this listener 
                    // hanging around.
                    hideNavBar();
                }
            }
            return false;
        }
    };

    private void setupAutoHide(){
        if (mNavigationBarView !=null){
            mNavigationBarView.setOnTouchListener(mNavBarListener);
            if (mGesturePanel == null) {
                mGesturePanel = new GestureCatcherView(mContext,null,this);
            }
        }
        hideNavBar();
    }

    private Runnable delayHide = new Runnable() {
        public void run() {
            hideNavBar();
        }
    };

    private void showNavBar(){
        if (mWindowManager != null && !mAutoHideVisible){
            mWindowManager.removeView(mGesturePanel);
            mAutoHideVisible = true;
            try {
                mWindowManager.addView(mNavigationBarView, getNavigationBarLayoutParams());
                repositionNavigationBar();
                if (mAutoHideTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mAutoHideTimeOut);
                }
            } catch (BadTokenException e) {
                // We failed adding the NavBar & we have removed the GestureCatcher
                // this happens when we touch the NavBar while it is trying to hide.
                // Let's go back to hidden Mode
                hideNavBar();
            }
                // Start the timer to hide the NavBar; <-- I LOLZ. Mike even comments in java. ;-)
        }
    }

    private void hideNavBar() {
        if (mNavigationBarView != null && !mLockscreenOn) {
            try {
                mWindowManager.removeView(mNavigationBarView);
            } catch (IllegalArgumentException e) {
                // we are probably in a state where NavBar has been created, but not actually added to the window
                Log.d("PopUpNav","Failed Removing NavBar");
            }
            try {
                // we remove the GesturePanel just to make sure we don't have a case where we are
                // trying to add two gesture panels
                mWindowManager.removeView(mGesturePanel);
            } catch (IllegalArgumentException e) {
                // we are probably in a state where NavBar has been created, but not actually added to the window
                Log.d("PopUpNav","Failed Removing Gesture");
            }
            mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
            mAutoHideVisible = false;
        }
    }

    private void disableAutoHide(){
        try {
            mWindowManager.removeView(mGesturePanel);
        } catch (IllegalArgumentException e) {
            Log.d("PopUpNav","Failed Removing Gesture on disableAutoHide");
        }
        mGesturePanel = null;
        mHandler.removeCallbacks(delayHide);
        addNavigationBar();
        // let's force the NavBar back On
        Settings.System.putBoolean(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW_NOW, true);
    }

    @Override
    public void setSearchLightOn(boolean on) {
        try {
            mLockscreenOn = mWm.isKeyguardLocked();
        } catch (RemoteException e) {

        }
        if (on)
            showNavBar();
        else
            hideNavBar();
    }

    @Override
    protected void showBar(boolean showSearch){
        showNavBar();
        if (showSearch) {
            mHandler.removeCallbacks(mShowSearchPanel);
            mHandler.postDelayed(mShowSearchPanel, mShowSearchHoldoff);
        }
    }

    @Override
    protected void onBarTouchEvent(MotionEvent ev){
        // NavBar/SystemBar reports a touch event - reset the hide timer if applicable
        if (mNavBarAutoHide && mAutoHideTimeOut > 0) {
            mHandler.removeCallbacks(delayHide); // reset
            mHandler.postDelayed(delayHide,mAutoHideTimeOut);
        }
    }

    private void prepareNavigationBarView() {
        mNavigationBarView.reorient();
        mNavigationBarView.getSearchLight().setOnTouchListener(mHomeSearchActionListener);
        updateSearchPanel();
    }

    // For small-screen devices (read: phones) that lack hardware navigation buttons
    private void addNavigationBar() {
        if (DEBUG) Slog.v(TAG, "addNavigationBar: about to add " + mNavigationBarView);
        if (mNavigationBarView == null) return;

        prepareNavigationBarView();

        if (!mNavBarAutoHide) {
            // we don't add the NavBar if AutoHide is on.
            mWindowManager.addView(mNavigationBarView, getNavigationBarLayoutParams());
        }
        mNavigationBarView.setTransparencyManager(mTransparencyManager);
        mTransparencyManager.setNavbar(mNavigationBarView);
        mTransparencyManager.update();
    }

    private void repositionNavigationBar() {
        if (mNavigationBarView == null) return;

        CustomTheme newTheme = mContext.getResources().getConfiguration().customTheme;
        if (newTheme != null &&
                (mCurrentTheme == null || !mCurrentTheme.equals(newTheme))) {
            // Nevermind, this will be re-created
            return;
        }
        prepareNavigationBarView();
        if (!mNavBarAutoHide || (mNavBarAutoHide && mAutoHideVisible)) {
            // we only want to update the NavBar if we know it's attached to the window.
            mWindowManager.updateViewLayout(mNavigationBarView, getNavigationBarLayoutParams());
        }
    }

    private void notifyNavigationBarScreenOn(boolean screenOn) {
        if (mNavigationBarView == null) return;
        mNavigationBarView.notifyScreenOn(screenOn);
    }

    private WindowManager.LayoutParams getNavigationBarLayoutParams() {
        // if we are an AutoHide NavBar, We want to use System_alert so
        // that we float above all other windows.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_NAVIGATION_BAR,
                    0
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                    PixelFormat.TRANSLUCENT);
        // this will allow the navbar to run in an overlay on devices that support this
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("NavigationBar");
        lp.windowAnimations = 0;
        return lp;
    }

    private void addIntruderView() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL, // above the status bar!
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
        //lp.y += height * 1.5; // FIXME
        lp.setTitle("IntruderAlert");
        lp.packageName = mContext.getPackageName();
        lp.windowAnimations = R.style.Animation_StatusBar_IntruderAlert;

        mWindowManager.addView(mIntruderAlertView, lp);
    }

    public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        if (SPEW) Slog.d(TAG, "addIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex
                + " icon=" + icon);
        StatusBarIconView view = new StatusBarIconView(mContext, slot, null);
        view.set(icon);
        mStatusIcons.addView(view, viewIndex, new LinearLayout.LayoutParams(mIconSize, mIconSize));
    }

    public void updateIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {
        if (SPEW) Slog.d(TAG, "updateIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex
                + " old=" + old + " icon=" + icon);
        StatusBarIconView view = (StatusBarIconView)mStatusIcons.getChildAt(viewIndex);
        view.set(icon);
    }

    public void removeIcon(String slot, int index, int viewIndex) {
        if (SPEW) Slog.d(TAG, "removeIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex);
        mStatusIcons.removeViewAt(viewIndex);
    }

    public void addNotification(IBinder key, StatusBarNotification notification) {
        if (DEBUG) Slog.d(TAG, "addNotification score=" + notification.score);
        StatusBarIconView iconView = addNotificationViews(key, notification);
        if (iconView == null) return;

        boolean immersive = false;
        try {
            immersive = ActivityManagerNative.getDefault().isTopActivityImmersive();
            if (DEBUG) {
                Slog.d(TAG, "Top activity is " + (immersive?"immersive":"not immersive"));
            }
        } catch (RemoteException ex) {
        }

        /*
         * DISABLED due to missing API
        if (ENABLE_INTRUDERS && (
                   // TODO(dsandler): Only if the screen is on
                notification.notification.intruderView != null)) {
            Slog.d(TAG, "Presenting high-priority notification");
            // special new transient ticker mode
            // 1. Populate mIntruderAlertView

            if (notification.notification.intruderView == null) {
                Slog.e(TAG, notification.notification.toString() + " wanted to intrude but intruderView was null");
                return;
            }

            // bind the click event to the content area
            PendingIntent contentIntent = notification.notification.contentIntent;
            final View.OnClickListener listener = (contentIntent != null)
                    ? new NotificationClicker(contentIntent,
                            notification.pkg, notification.tag, notification.id)
                    : null;

            mIntruderAlertView.applyIntruderContent(notification.notification.intruderView, listener);

            mCurrentlyIntrudingNotification = notification;

            // 2. Animate mIntruderAlertView in
            mHandler.sendEmptyMessage(MSG_SHOW_INTRUDER);

            // 3. Set alarm to age the notification off (TODO)
            mHandler.removeMessages(MSG_HIDE_INTRUDER);
            if (INTRUDER_ALERT_DECAY_MS > 0) {
                mHandler.sendEmptyMessageDelayed(MSG_HIDE_INTRUDER, INTRUDER_ALERT_DECAY_MS);
            }
        } else
         */

        if (notification.notification.fullScreenIntent != null) {
            // Stop screensaver if the notification has a full-screen intent.
            // (like an incoming phone call)
            awakenDreams();

            // not immersive & a full-screen alert should be shown
            if (DEBUG) Slog.d(TAG, "Notification has fullScreenIntent; sending fullScreenIntent");
            try {
                notification.notification.fullScreenIntent.send();
            } catch (PendingIntent.CanceledException e) {
            }
        } else if (!mRecreating) {
            // usual case: status bar visible & not immersive

            // show the ticker if there isn't an intruder too
            if (mCurrentlyIntrudingNotification == null) {
                tick(null, notification, true);
            }
        }

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
    }

    public void removeNotification(IBinder key) {
        StatusBarNotification old = removeNotificationViews(key);
        if (SPEW) Slog.d(TAG, "removeNotification key=" + key + " old=" + old);

        if (old != null) {
            // Cancel the ticker if it's still running
            mTicker.removeEntry(old);

            // Recalculate the position of the sliding windows and the titles.
            updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

            if (ENABLE_INTRUDERS && old == mCurrentlyIntrudingNotification) {
                mHandler.sendEmptyMessage(MSG_HIDE_INTRUDER);
            }

            if (CLOSE_PANEL_WHEN_EMPTIED && mNotificationData.size() == 0 && !mAnimating) {
                animateCollapsePanels();
            }
        }

        setAreThereNotifications();
    }

    private void loadNotificationShade() {
        if (mPile == null) return;

        int N = mNotificationData.size();

        ArrayList<View> toShow = new ArrayList<View>();

        final boolean provisioned = isDeviceProvisioned();
        // If the device hasn't been through Setup, we only show system notifications
        for (int i=0; i<N; i++) {
            Entry ent = mNotificationData.get(N-i-1);
            if (!(provisioned || showNotificationEvenIfUnprovisioned(ent.notification))) continue;
            if (!notificationIsForCurrentUser(ent.notification)) continue;
            toShow.add(ent.row);
        }

        ArrayList<View> toRemove = new ArrayList<View>();
        for (int i=0; i<mPile.getChildCount(); i++) {
            View child = mPile.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        for (View remove : toRemove) {
            mPile.removeView(remove);
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mPile.addView(v, i);
            }
        }

        if (mSettingsButton != null) {
            mSettingsButton.setEnabled(isDeviceProvisioned());
        }
    }

    @Override
    protected void updateNotificationIcons() {
        if (mNotificationIcons == null) return;

        loadNotificationShade();

        final LinearLayout.LayoutParams params
            = new LinearLayout.LayoutParams(mIconSize + 2*mIconHPadding, mNaturalBarHeight);

        int N = mNotificationData.size();

        if (DEBUG) {
            Slog.d(TAG, "refreshing icons: " + N + " notifications, mNotificationIcons=" + mNotificationIcons);
        }

        ArrayList<View> toShow = new ArrayList<View>();

        final boolean provisioned = isDeviceProvisioned();
        // If the device hasn't been through Setup, we only show system notifications
        for (int i=0; i<N; i++) {
            Entry ent = mNotificationData.get(N-i-1);
            if (!((provisioned && ent.notification.score >= HIDE_ICONS_BELOW_SCORE)
                    || showNotificationEvenIfUnprovisioned(ent.notification))) continue;
            if (!notificationIsForCurrentUser(ent.notification)) continue;
            toShow.add(ent.icon);
        }

        ArrayList<View> toRemove = new ArrayList<View>();
        for (int i=0; i<mNotificationIcons.getChildCount(); i++) {
            View child = mNotificationIcons.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        for (View remove : toRemove) {
            mNotificationIcons.removeView(remove);
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mNotificationIcons.addView(v, i, params);
            }
        }
    }

    protected void updateCarrierLabelVisibility(boolean force) {
        if (!mShowCarrierInPanel) return;
        // The idea here is to only show the carrier label when there is enough room to see it, 
        // i.e. when there aren't enough notifications to fill the panel.
        if (DEBUG) {
            Slog.d(TAG, String.format("pileh=%d scrollh=%d carrierh=%d",
                    mPile.getHeight(), mScrollView.getHeight(), mCarrierLabelHeight));
        }

        final boolean emergencyCallsShownElsewhere = mEmergencyCallLabel != null;
        final boolean makeVisible =
            !(emergencyCallsShownElsewhere && mNetworkController.isEmergencyOnly())
            && mPile.getHeight() < (mNotificationPanel.getHeight() - mCarrierLabelHeight - mNotificationHeaderHeight)
            && mScrollView.getVisibility() == View.VISIBLE;
        
        if (force || mCarrierLabelVisible != makeVisible) {
            mCarrierLabelVisible = makeVisible;
            if (DEBUG) {
                Slog.d(TAG, "making carrier label " + (makeVisible?"visible":"invisible"));
            }
            mCarrierLabel.animate().cancel();
            if (makeVisible) {
                mCarrierLabel.setVisibility(View.VISIBLE);
            }
            mCarrierLabel.animate()
                .alpha(makeVisible ? 1f : 0f)
                //.setStartDelay(makeVisible ? 500 : 0)
                //.setDuration(makeVisible ? 750 : 100)
                .setDuration(150)
                .setListener(makeVisible ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!mCarrierLabelVisible) { // race
                            mCarrierLabel.setVisibility(View.INVISIBLE);
                            mCarrierLabel.setAlpha(0f);
                        }
                    }
                })
                .start();
        }
    }

    @Override
    protected void setAreThereNotifications() {
        final boolean any = mNotificationData.size() > 0;

        final boolean clearable = any && mNotificationData.hasClearableItems();

        if (DEBUG) {
            Slog.d(TAG, "setAreThereNotifications: N=" + mNotificationData.size()
                    + " any=" + any + " clearable=" + clearable);
        }

        if (mHasFlipSettings 
                && mFlipSettingsView != null 
                && mFlipSettingsView.getVisibility() == View.VISIBLE
                && mScrollView.getVisibility() != View.VISIBLE) {
            // the flip settings panel is unequivocally showing; we should not be shown
            mClearButton.setVisibility(View.INVISIBLE);
        } else if (mClearButton.isShown()) {
            if (clearable != (mClearButton.getAlpha() == 1.0f)) {
                ObjectAnimator clearAnimation = ObjectAnimator.ofFloat(
                        mClearButton, "alpha", clearable ? 1.0f : 0.0f).setDuration(250);
                clearAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mClearButton.getAlpha() <= 0.0f) {
                            mClearButton.setVisibility(View.INVISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (mClearButton.getAlpha() <= 0.0f) {
                            mClearButton.setVisibility(View.VISIBLE);
                        }
                    }
                });
                clearAnimation.start();
            }
        } else {
            mClearButton.setAlpha(clearable ? 1.0f : 0.0f);
            mClearButton.setVisibility(clearable ? View.VISIBLE : View.INVISIBLE);
        }
        mClearButton.setEnabled(clearable);

        final View nlo = mStatusBarView.findViewById(R.id.notification_lights_out);
        final boolean showDot = (any&&!areLightsOn());
        if (showDot != (nlo.getAlpha() == 1.0f)) {
            if (showDot) {
                nlo.setAlpha(0f);
                nlo.setVisibility(View.VISIBLE);
            }
            nlo.animate()
                .alpha(showDot?1:0)
                .setDuration(showDot?750:250)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(showDot ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        nlo.setVisibility(View.GONE);
                    }
                })
                .start();
        }

        updateCarrierLabelVisibility(false);
    }

    public void showClock(boolean show) {
        if (mStatusBarView == null) return;
        View clock = mStatusBarView.findViewById(R.id.clock);
        View cclock = mStatusBarView.findViewById(R.id.center_clock);
        boolean rightClock = (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUSBAR_CLOCK_STYLE, 1) == 1);
        boolean centerClock = (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUSBAR_CLOCK_STYLE, 1) == 2);
        if (rightClock && clock != null) {
            clock.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (centerClock && cclock != null) {
            cclock.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * State is one or more of the DISABLE constants from StatusBarManager.
     */
    public void disable(int state) {
        final int old = mDisabled;
        final int diff = state ^ old;
        mDisabled = state;

        mAokpSwipeRibbonBottom.setDisabledFlags(state);
        mAokpSwipeRibbonLeft.setDisabledFlags(state);
        mAokpSwipeRibbonRight.setDisabledFlags(state);

        if (DEBUG) {
            Slog.d(TAG, String.format("disable: 0x%08x -> 0x%08x (diff: 0x%08x)",
                old, state, diff));
        }

        StringBuilder flagdbg = new StringBuilder();
        flagdbg.append("disable: < ");
        flagdbg.append(((state & StatusBarManager.DISABLE_EXPAND) != 0) ? "EXPAND" : "expand");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_EXPAND) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) ? "ICONS" : "icons");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0) ? "ALERTS" : "alerts");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) ? "TICKER" : "ticker");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) ? "SYSTEM_INFO" : "system_info");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_BACK) != 0) ? "BACK" : "back");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_BACK) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_HOME) != 0) ? "HOME" : "home");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_HOME) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_RECENT) != 0) ? "RECENT" : "recent");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_RECENT) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_CLOCK) != 0) ? "CLOCK" : "clock");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_CLOCK) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_SEARCH) != 0) ? "SEARCH" : "search");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_SEARCH) != 0) ? "* " : " ");
        flagdbg.append(">");
        Slog.d(TAG, flagdbg.toString());

        if ((diff & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) {
            mSystemIconArea.animate().cancel();
            if ((state & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) {
                mSystemIconArea.animate()
                    .alpha(0f)
                    .translationY(mNaturalBarHeight*0.5f)
                    .setDuration(175)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setListener(mMakeIconsInvisible)
                    .start();
            } else {
                mSystemIconArea.setVisibility(View.VISIBLE);
                mSystemIconArea.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setStartDelay(0)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setDuration(175)
                    .start();
            }
        }

        if ((diff & StatusBarManager.DISABLE_CLOCK) != 0) {
            boolean show = (state & StatusBarManager.DISABLE_CLOCK) == 0;
            showClock(show);
        }
        if ((diff & StatusBarManager.DISABLE_EXPAND) != 0) {
            if ((state & StatusBarManager.DISABLE_EXPAND) != 0) {
                animateCollapsePanels();
            }
        }

        if ((diff & (StatusBarManager.DISABLE_HOME
                        | StatusBarManager.DISABLE_RECENT
                        | StatusBarManager.DISABLE_BACK
                        | StatusBarManager.DISABLE_SEARCH)) != 0) {
            // the nav bar will take care of these
            if (mNavigationBarView != null) 
                mNavigationBarView.setDisabledFlags(state);

            if ((state & StatusBarManager.DISABLE_RECENT) != 0) {
                // close recents if it's visible
                mHandler.removeMessages(MSG_CLOSE_RECENTS_PANEL);
                mHandler.sendEmptyMessage(MSG_CLOSE_RECENTS_PANEL);
            }
        }

        if ((diff & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
                if (mTicking) {
                    haltTicker();
                }

                mNotificationIcons.animate()
                    .alpha(0f)
                    .translationY(mNaturalBarHeight*0.5f)
                    .setDuration(175)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setListener(mMakeIconsInvisible)
                    .start();
            } else {
                mNotificationIcons.setVisibility(View.VISIBLE);
                mNotificationIcons.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setStartDelay(0)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setDuration(175)
                    .start();
            }
        } else if ((diff & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
            if (mTicking && (state & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
                haltTicker();
            }
        }
        mTransparencyManager.update();
    }

    @Override
    protected BaseStatusBar.H createHandler() {
        return new PhoneStatusBar.H();
    }

    /**
     * All changes to the status bar and notifications funnel through here and are batched.
     */
    private class H extends BaseStatusBar.H {
        public void handleMessage(Message m) {
            super.handleMessage(m);
            switch (m.what) {
                case MSG_OPEN_NOTIFICATION_PANEL:
                    animateExpandNotificationsPanel();
                    break;
                case MSG_OPEN_SETTINGS_PANEL:
                    animateExpandSettingsPanel();
                    break;
                case MSG_CLOSE_PANELS:
                    animateCollapsePanels();
                    break;
                case MSG_SHOW_INTRUDER:
                    setIntruderAlertVisibility(true);
                    break;
                case MSG_HIDE_INTRUDER:
                    setIntruderAlertVisibility(false);
                    mCurrentlyIntrudingNotification = null;
                    break;
            }
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    View.OnFocusChangeListener mFocusChangeListener = new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            // Because 'v' is a ViewGroup, all its children will be (un)selected
            // too, which allows marqueeing to work.
            v.setSelected(hasFocus);
        }
    };

    void makeExpandedVisible(boolean revealAfterDraw) {
        if (SPEW) Slog.d(TAG, "Make expanded visible: expanded visible=" + mExpandedVisible);
        if (mExpandedVisible) {
            return;
        }

        mExpandedVisible = true;
        mPile.setLayoutTransitionsEnabled(true);
        if (mNavigationBarView != null)
            mNavigationBarView.setSlippery(true);

        updateCarrierLabelVisibility(true);

        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

        // Expand the window to encompass the full screen in anticipation of the drag.
        // This is only possible to do atomically because the status bar is at the top of the screen!
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) mStatusBarWindow.getLayoutParams();
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mWindowManager.updateViewLayout(mStatusBarWindow, lp);

        // Updating the window layout will force an expensive traversal/redraw.
        // Kick off the reveal animation after this is complete to avoid animation latency.
        if (revealAfterDraw) {
//            mHandler.post(mStartRevealAnimation);
        }

        visibilityChanged(true);
    }

    public void animateCollapsePanels() {
        animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
    }

    public void animateCollapsePanels(int flags) {
        if (SPEW) {
            Slog.d(TAG, "animateCollapse():"
                    + " mExpandedVisible=" + mExpandedVisible
                    + " mAnimating=" + mAnimating
                    + " mAnimatingReveal=" + mAnimatingReveal
                    + " mAnimY=" + mAnimY
                    + " mAnimVel=" + mAnimVel
                    + " flags=" + flags);
        }

        if ((flags & CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_RECENTS_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_RECENTS_PANEL);
        }

        if ((flags & CommandQueue.FLAG_EXCLUDE_SEARCH_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_SEARCH_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_SEARCH_PANEL);
        }

        mStatusBarWindow.cancelExpandHelper();
        mStatusBarView.collapseAllPanels(true);
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

    final TimeInterpolator mAccelerateInterpolator = new AccelerateInterpolator();
    final TimeInterpolator mDecelerateInterpolator = new DecelerateInterpolator();
    final int FLIP_DURATION_OUT = 125;
    final int FLIP_DURATION_IN = 225;
    final int FLIP_DURATION = (FLIP_DURATION_IN + FLIP_DURATION_OUT);

    Animator mScrollViewAnim, mFlipSettingsViewAnim, mNotificationButtonAnim,
        mSettingsButtonAnim, mClearButtonAnim;

    @Override
    public void animateExpandNotificationsPanel() {
        if (SPEW) Slog.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return ;
        }

        mNotificationPanel.expand();
        if (mHasFlipSettings && mScrollView.getVisibility() != View.VISIBLE) {
            flipToNotifications();
        }

        if (false) postStartTracing();
    }

    public void flipToNotifications() {
        if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
        if (mScrollViewAnim != null) mScrollViewAnim.cancel();
        if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
        if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
        if (mClearButtonAnim != null) mClearButtonAnim.cancel();

        mScrollView.setVisibility(View.VISIBLE);
        mScrollViewAnim = start(
            startDelay(FLIP_DURATION_OUT,
                interpolator(mDecelerateInterpolator,
                    ObjectAnimator.ofFloat(mScrollView, View.SCALE_X, 0f, 1f)
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
        mClearButton.setAlpha(0f);
        setAreThereNotifications(); // this will show/hide the button as necessary
        mNotificationPanel.postDelayed(new Runnable() {
            public void run() {
                updateCarrierLabelVisibility(false);
            }
        }, FLIP_DURATION - 150);
        updateRibbon(false);
    }

    @Override
    public void animateExpandSettingsPanel() {
        if (SPEW) Slog.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return;
        }

        // Settings are not available in setup
        if (!mUserSetup) return;

        if (mHasFlipSettings) {
            mNotificationPanel.expand();
            if (mFlipSettingsView.getVisibility() != View.VISIBLE) {
                flipToSettings();
            }
        } else if (mSettingsPanel != null) {
            mSettingsPanel.expand();
        }

        if (false) postStartTracing();
    }


    public void updateRibbonTargets() {
            ContentResolver cr = mContext.getContentResolver();
            mRibbonNotif = (LinearLayout) mNotificationPanel.findViewById(R.id.ribbon_notif);
            if (!mHasFlipSettings) {
                if (mSettingsPanel != null) {
                    mRibbonQS = (LinearLayout) mSettingsPanel.findViewById(R.id.ribbon_settings);
                }
            } else {
                mRibbonQS = (LinearLayout) mNotificationPanel.findViewById(R.id.ribbon_qs);
            }
            if (mRibbonQS != null) {
                mRibbonQS.removeAllViews();
                mRibbonQS.addView(AokpRibbonHelper.getRibbon(mContext,
                    Settings.System.getArrayList(cr,
                         Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.QUICK_SETTINGS]),
                    Settings.System.getArrayList(cr,
                         Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.QUICK_SETTINGS]),
                    Settings.System.getArrayList(cr,
                         Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.QUICK_SETTINGS]),
                    Settings.System.getBoolean(cr,
                         Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.QUICK_SETTINGS], true),
                    Settings.System.getInt(cr,
                         Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.QUICK_SETTINGS], -1),
                    Settings.System.getInt(cr,
                         Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.QUICK_SETTINGS], 0),
                    Settings.System.getInt(cr,
                         Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.QUICK_SETTINGS], 5),
                    Settings.System.getBoolean(cr,
                         Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.QUICK_SETTINGS], true),
                    Settings.System.getBoolean(cr,
                         Settings.System.RIBBON_ICON_COLORIZE[AokpRibbonHelper.QUICK_SETTINGS], false), 0));
            }
            mRibbonNotif.removeAllViews();
            mRibbonNotif.addView(AokpRibbonHelper.getRibbon(mContext,
                Settings.System.getArrayList(cr,
                     Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.NOTIFICATIONS]),
                Settings.System.getArrayList(cr,
                     Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.NOTIFICATIONS]),
                Settings.System.getArrayList(cr,
                     Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.NOTIFICATIONS]),
                Settings.System.getBoolean(cr,
                     Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.NOTIFICATIONS], true),
                Settings.System.getInt(cr,
                     Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.NOTIFICATIONS], -1),
                Settings.System.getInt(cr,
                     Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.NOTIFICATIONS], 0),
                Settings.System.getInt(cr,
                     Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.NOTIFICATIONS], 5),
                Settings.System.getBoolean(cr,
                     Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.NOTIFICATIONS], true),
                Settings.System.getBoolean(cr,
                     Settings.System.RIBBON_ICON_COLORIZE[AokpRibbonHelper.NOTIFICATIONS], false), 0));
    }

    public void updateRibbon() {
        if (mHasFlipSettings) {
            boolean ribbonSettings = mNotificationButton.getVisibility() == View.VISIBLE;
            updateRibbon(ribbonSettings);
        }
    }

    public void updateRibbon(boolean settings) {
        if (mHasFlipSettings) {
            if (settings) {
                mRibbonNotif.setVisibility(View.GONE);
                mRibbonQS.setVisibility(View.VISIBLE);
            } else {
                mRibbonQS.setVisibility(View.GONE);
                mRibbonNotif.setVisibility(View.VISIBLE);
            }
        }
    }

    public void switchToSettings() {
        // Settings are not available in setup
        if (!mUserSetup) return;

        mFlipSettingsView.setScaleX(1f);
        mFlipSettingsView.setVisibility(View.VISIBLE);
        mSettingsButton.setVisibility(View.GONE);
        mScrollView.setVisibility(View.GONE);
        mScrollView.setScaleX(0f);
        mNotificationButton.setVisibility(View.VISIBLE);
        mNotificationButton.setAlpha(1f);
        mClearButton.setVisibility(View.GONE);
        updateRibbon(true);
    }

    public void flipToSettings() {
        // Settings are not available in setup
        if (!mUserSetup) return;
        if(mToggleManager != null && !mToggleManager.shouldFlipToSettings()) {
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
                        ObjectAnimator.ofFloat(mScrollView, View.SCALE_X, 1f, 0f)
                        )
                    .setDuration(FLIP_DURATION_OUT), 
                mScrollView, View.INVISIBLE));
        mSettingsButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA, 0f)
                    .setDuration(FLIP_DURATION),
                    mScrollView, View.INVISIBLE));
        mNotificationButton.setVisibility(View.VISIBLE);
        mNotificationButtonAnim = start(
            ObjectAnimator.ofFloat(mNotificationButton, View.ALPHA, 1f)
                .setDuration(FLIP_DURATION));
        mClearButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mClearButton, View.ALPHA, 0f)
                .setDuration(FLIP_DURATION),
                mClearButton, View.INVISIBLE));
        mNotificationPanel.postDelayed(new Runnable() {
            public void run() {
                updateCarrierLabelVisibility(false);
            }
        }, FLIP_DURATION - 150);
        updateRibbon(true);
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
        mStatusBarView.collapseAllPanels(true);
    }

    void makeExpandedInvisibleSoon() {
        mHandler.postDelayed(new Runnable() { public void run() { makeExpandedInvisible(); }}, 50);
    }

    void makeExpandedInvisible() {
        if (SPEW) Slog.d(TAG, "makeExpandedInvisible: mExpandedVisible=" + mExpandedVisible
                + " mExpandedVisible=" + mExpandedVisible);

        if (!mExpandedVisible) {
            return;
        }

        // Ensure the panel is fully collapsed (just in case; bug 6765842, 7260868)
        mStatusBarView.collapseAllPanels(/*animate=*/ false);

        if (mHasFlipSettings) {
            // reset things to their proper state
            if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
            if (mScrollViewAnim != null) mScrollViewAnim.cancel();
            if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
            if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
            if (mClearButtonAnim != null) mClearButtonAnim.cancel();

            mScrollView.setScaleX(1f);
            mScrollView.setVisibility(View.VISIBLE);
            mSettingsButton.setAlpha(1f);
            mSettingsButton.setVisibility(View.VISIBLE);
            mNotificationPanel.setVisibility(View.GONE);
            mFlipSettingsView.setVisibility(View.GONE);
            mNotificationButton.setVisibility(View.GONE);
            setAreThereNotifications(); // show the clear button
        }

        mExpandedVisible = false;
        mPile.setLayoutTransitionsEnabled(false);
        if (mNavigationBarView != null)
            mNavigationBarView.setSlippery(false);
        visibilityChanged(false);

        // Shrink the window to the size of the status bar only
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) mStatusBarWindow.getLayoutParams();
        lp.height = getStatusBarHeight();
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mWindowManager.updateViewLayout(mStatusBarWindow, lp);

        if ((mDisabled & StatusBarManager.DISABLE_NOTIFICATION_ICONS) == 0) {
            setNotificationIconVisibility(true, com.android.internal.R.anim.fade_in);
        }

        // Close any "App info" popups that might have snuck on-screen
        dismissPopups();

        if (mPostCollapseCleanup != null) {
            mPostCollapseCleanup.run();
            mPostCollapseCleanup = null;
        }
    }

    /**
     * Enables or disables layers on the children of the notifications pile.
     * 
     * When layers are enabled, this method attempts to enable layers for the minimal
     * number of children. Only children visible when the notification area is fully
     * expanded will receive a layer. The technique used in this method might cause
     * more children than necessary to get a layer (at most one extra child with the
     * current UI.)
     * 
     * @param layerType {@link View#LAYER_TYPE_NONE} or {@link View#LAYER_TYPE_HARDWARE}
     */
    private void setPileLayers(int layerType) {
        final int count = mPile.getChildCount();

        switch (layerType) {
            case View.LAYER_TYPE_NONE:
                for (int i = 0; i < count; i++) {
                    mPile.getChildAt(i).setLayerType(layerType, null);
                }
                break;
            case View.LAYER_TYPE_HARDWARE:
                final int[] location = new int[2]; 
                mNotificationPanel.getLocationInWindow(location);

                final int left = location[0];
                final int top = location[1];
                final int right = left + mNotificationPanel.getWidth();
                final int bottom = top + getExpandedViewMaxHeight();

                final Rect childBounds = new Rect();

                for (int i = 0; i < count; i++) {
                    final View view = mPile.getChildAt(i);
                    view.getLocationInWindow(location);

                    childBounds.set(location[0], location[1],
                            location[0] + view.getWidth(), location[1] + view.getHeight());

                    if (childBounds.intersects(left, top, right, bottom)) {
                        view.setLayerType(layerType, null);
                    }
                }

                break;
        }
    }

    public boolean isClinging() {
        return mCling != null && mCling.getVisibility() == View.VISIBLE;
    }

    public void hideCling() {
        if (isClinging()) {
            mCling.animate().alpha(0f).setDuration(250).start();
            mCling.setVisibility(View.GONE);
            mSuppressStatusBarDrags = false;
        }
    }

    public void showCling() {
        // lazily inflate this to accommodate orientation change
        final ViewStub stub = (ViewStub) mStatusBarWindow.findViewById(R.id.status_bar_cling_stub);
        if (stub == null) {
            mClingShown = true;
            return; // no clings on this device
        }

        mSuppressStatusBarDrags = true;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCling = (ViewGroup) stub.inflate();

                mCling.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true; // e eats everything
                    }});
                mCling.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideCling();
                    }});

                mCling.setAlpha(0f);
                mCling.setVisibility(View.VISIBLE);
                mCling.animate().alpha(1f);

                mClingShown = true;
                SharedPreferences.Editor editor = Prefs.edit(mContext);
                editor.putBoolean(Prefs.SHOWN_QUICK_SETTINGS_HELP, true);
                editor.apply();

                makeExpandedVisible(true); // enforce visibility in case the shade is still animating closed
                animateExpandNotificationsPanel();

                mSuppressStatusBarDrags = false;
            }
        }, 500);

        animateExpandNotificationsPanel();
    }

    public boolean interceptTouchEvent(MotionEvent event) {
        if (SPEW) {
            Slog.d(TAG, "Touch: rawY=" + event.getRawY() + " event=" + event + " mDisabled="
                + mDisabled + " mTracking=" + mTracking);
        } else if (CHATTY) {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                Slog.d(TAG, String.format(
                            "panel: %s at (%f, %f) mDisabled=0x%08x",
                            MotionEvent.actionToString(event.getAction()),
                            event.getRawX(), event.getRawY(), mDisabled));
            }
        }

        if (DEBUG_GESTURES) {
            mGestureRec.add(event);
        }

        // Cling (first-run help) handling.
        // The cling is supposed to show the first time you drag, or even tap, the status bar.
        // It should show the notification panel, then fade in after half a second, giving you 
        // an explanation of what just happened, as well as teach you how to access quick
        // settings (another drag). The user can dismiss the cling by clicking OK or by 
        // dragging quick settings into view.
        final int act = event.getActionMasked();
        if (mSuppressStatusBarDrags) {
            return true;
        } else if (act == MotionEvent.ACTION_UP && !mClingShown) {
            showCling();
        } else {
            hideCling();
        }

        return false;
    }

    public GestureRecorder getGestureRecorder() {
        return mGestureRec;
    }

    @Override // CommandQueue
    public void setNavigationIconHints(int hints) {

        if (hints == mNavigationIconHints) return;

        mNavigationIconHints = hints;

        if (mNavigationBarView != null) {
            mNavigationBarView.setNavigationIconHints(hints);
        }
    }

    @Override // CommandQueue
    public void setSystemUiVisibility(int vis, int mask) {
        final int oldVal = mSystemUiVisibility;
        final int newVal = (oldVal&~mask) | (vis&mask);
        final int diff = newVal ^ oldVal;

        if (diff != 0) {
            mSystemUiVisibility = newVal;

            if (0 != (diff & View.SYSTEM_UI_FLAG_LOW_PROFILE)) {
                final boolean lightsOut = (0 != (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE));
                if (lightsOut) {
                    animateCollapsePanels();
                    if (mTicking) {
                        haltTicker();
                    }
                }

                if (mNavigationBarView != null) {
                    mNavigationBarView.setLowProfile(lightsOut);
                }

                setStatusBarLowProfile(lightsOut);
            }

            notifyUiVisibilityChanged();
        }
    }

    private void setStatusBarLowProfile(boolean lightsOut) {
        if (mLightsOutAnimation == null) {
            final View notifications = mStatusBarView.findViewById(R.id.notification_icon_area);
            final View systemIcons = mStatusBarView.findViewById(R.id.statusIcons);
            final View signal = mStatusBarView.findViewById(R.id.signal_cluster);
            final View battery = mStatusBarView.findViewById(R.id.battery);
            final View clock = mStatusBarView.findViewById(R.id.clock);

            final AnimatorSet lightsOutAnim = new AnimatorSet();
            lightsOutAnim.playTogether(
                    ObjectAnimator.ofFloat(notifications, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(systemIcons, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(signal, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(battery, View.ALPHA, 0.5f),
                    ObjectAnimator.ofFloat(clock, View.ALPHA, 0.5f)
                );
            lightsOutAnim.setDuration(750);

            final AnimatorSet lightsOnAnim = new AnimatorSet();
            lightsOnAnim.playTogether(
                    ObjectAnimator.ofFloat(notifications, View.ALPHA, 1),
                    ObjectAnimator.ofFloat(systemIcons, View.ALPHA, 1),
                    ObjectAnimator.ofFloat(signal, View.ALPHA, 1),
                    ObjectAnimator.ofFloat(battery, View.ALPHA, 1),
                    ObjectAnimator.ofFloat(clock, View.ALPHA, 1)
                );
            lightsOnAnim.setDuration(250);

            mLightsOutAnimation = lightsOutAnim;
            mLightsOnAnimation = lightsOnAnim;
        }

        mLightsOutAnimation.cancel();
        mLightsOnAnimation.cancel();

        final Animator a = lightsOut ? mLightsOutAnimation : mLightsOnAnimation;
        a.start();

        setAreThereNotifications();
    }

    private boolean areLightsOn() {
        return 0 == (mSystemUiVisibility & View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    public void setLightsOn(boolean on) {
        Log.v(TAG, "setLightsOn(" + on + ")");
        if (on) {
            setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    private void notifyUiVisibilityChanged() {
        try {
            mWindowManagerService.statusBarVisibilityChanged(mSystemUiVisibility);
        } catch (RemoteException ex) {
        }
    }

    public void topAppWindowChanged(boolean showMenu) {
        mTransparencyManager.update();
        if (DEBUG) {
            Slog.d(TAG, (showMenu?"showing":"hiding") + " the MENU button");
        }
        if (mNavigationBarView != null) {
            mNavigationBarView.setMenuVisibility(showMenu);
        }

        // See above re: lights-out policy for legacy apps.
        if (showMenu) setLightsOn(true);
    }

    @Override
    public void setImeWindowStatus(IBinder token, int vis, int backDisposition) {
        boolean altBack = (backDisposition == InputMethodService.BACK_DISPOSITION_WILL_DISMISS)
            || ((vis & InputMethodService.IME_VISIBLE) != 0);

        mAokpSwipeRibbonBottom.setNavigationIconHints(vis);
        mAokpSwipeRibbonLeft.setNavigationIconHints(vis);
        mAokpSwipeRibbonRight.setNavigationIconHints(vis);

        mCommandQueue.setNavigationIconHints(
                altBack ? (mNavigationIconHints | StatusBarManager.NAVIGATION_HINT_BACK_ALT)
                        : (mNavigationIconHints & ~StatusBarManager.NAVIGATION_HINT_BACK_ALT));
    }

    @Override
    public void setHardKeyboardStatus(boolean available, boolean enabled) {}

    @Override
    protected void tick(IBinder key, StatusBarNotification n, boolean firstTime) {
        // no ticking in lights-out mode
        if (!areLightsOn()) return;

        // no ticking in Setup
        if (!isDeviceProvisioned()) return;

        // not for you
        if (!notificationIsForCurrentUser(n)) return;

        // Show the ticker if one is requested. Also don't do this
        // until status bar window is attached to the window manager,
        // because...  well, what's the point otherwise?  And trying to
        // run a ticker without being attached will crash!
        if (n.notification.tickerText != null && mStatusBarWindow.getWindowToken() != null) {
            if (0 == (mDisabled & (StatusBarManager.DISABLE_NOTIFICATION_ICONS
                            | StatusBarManager.DISABLE_NOTIFICATION_TICKER))) {
                mTicker.addEntry(n);
            }
        }
    }

    private class MyTicker extends Ticker {
        MyTicker(Context context, View sb) {
            super(context, sb);
        }

        @Override
        public void tickerStarting() {
            mTicking = true;
            mStatusBarContents.setVisibility(View.GONE);
            mCenterClockLayout.setVisibility(View.GONE);
            mTickerView.setVisibility(View.VISIBLE);
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.push_up_in, null));
            mStatusBarContents.startAnimation(loadAnim(com.android.internal.R.anim.push_up_out, null));
            mCenterClockLayout.startAnimation(loadAnim(com.android.internal.R.anim.push_up_out, null));
        }

        @Override
        public void tickerDone() {
            mStatusBarContents.setVisibility(View.VISIBLE);
            mCenterClockLayout.setVisibility(View.VISIBLE);
            mTickerView.setVisibility(View.GONE);
            mStatusBarContents.startAnimation(loadAnim(com.android.internal.R.anim.push_down_in, null));
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.push_down_out,
                        mTickingDoneListener));
            mCenterClockLayout.startAnimation(loadAnim(com.android.internal.R.anim.push_down_in, null));
        }

        public void tickerHalting() {
            mStatusBarContents.setVisibility(View.VISIBLE);
            mCenterClockLayout.setVisibility(View.VISIBLE);
            mTickerView.setVisibility(View.GONE);
            mStatusBarContents.startAnimation(loadAnim(com.android.internal.R.anim.fade_in, null));
            mCenterClockLayout.startAnimation(loadAnim(com.android.internal.R.anim.fade_in, null));
            // we do not animate the ticker away at this point, just get rid of it (b/6992707)
        }
    }

    Animation.AnimationListener mTickingDoneListener = new Animation.AnimationListener() {;
        public void onAnimationEnd(Animation animation) {
            mTicking = false;
        }
        public void onAnimationRepeat(Animation animation) {
        }
        public void onAnimationStart(Animation animation) {
        }
    };

    private Animation loadAnim(int id, Animation.AnimationListener listener) {
        Animation anim = AnimationUtils.loadAnimation(mContext, id);
        if (listener != null) {
            anim.setAnimationListener(listener);
        }
        return anim;
    }

    public static String viewInfo(View v) {
        return "[(" + v.getLeft() + "," + v.getTop() + ")(" + v.getRight() + "," + v.getBottom()
                + ") " + v.getWidth() + "x" + v.getHeight() + "]";
    }

    @Override
    public void createAndAddWindows() {
        addStatusBarWindow();
    }

    private void addStatusBarWindow() {
        // Put up the view
        final int height = getStatusBarHeight();

        // Now that the status bar window encompasses the sliding panel and its
        // translucent backdrop, the entire thing is made TRANSLUCENT and is
        // hardware-accelerated.
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);

        lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        lp.gravity = getStatusBarGravity();
        lp.setTitle("StatusBar");
        lp.packageName = mContext.getPackageName();

        makeStatusBarView();
        mWindowManager.addView(mStatusBarWindow, lp);
    }

    void setNotificationIconVisibility(boolean visible, int anim) {
        int old = mNotificationIcons.getVisibility();
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        if (old != v) {
            mNotificationIcons.setVisibility(v);
            mNotificationIcons.startAnimation(loadAnim(anim, null));
        }
    }

    void updateExpandedInvisiblePosition() {
        mTrackingPosition = -mDisplayMetrics.heightPixels;
    }

    static final float saturate(float a) {
        return a < 0f ? 0f : (a > 1f ? 1f : a);
    }

    @Override
    protected int getExpandedViewMaxHeight() {
        return mDisplayMetrics.heightPixels - mNotificationPanelMarginBottomPx;
    }

    @Override
    public void updateExpandedViewPos(int thingy) {
        if (DEBUG) Slog.v(TAG, "updateExpandedViewPos");

        // on larger devices, the notification panel is propped open a bit
        mNotificationPanel.setMinimumHeight(
                (int)(mNotificationPanelMinHeightFrac * mCurrentDisplaySize.y));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mNotificationPanel.getLayoutParams();
        lp.gravity = mNotificationPanelGravity;
        lp.leftMargin = mNotificationPanelMarginPx;
        mNotificationPanel.setLayoutParams(lp);

        if (mSettingsPanel != null) {
            lp = (FrameLayout.LayoutParams) mSettingsPanel.getLayoutParams();
            lp.gravity = mSettingsPanelGravity;
            lp.rightMargin = mNotificationPanelMarginPx;
            mSettingsPanel.setLayoutParams(lp);
        }

        updateCarrierLabelVisibility(false);
    }

    // called by makeStatusbar and also by PhoneStatusBarView
    void updateDisplaySize() {
        mDisplay.getMetrics(mDisplayMetrics);
        if (DEBUG_GESTURES) {
            mGestureRec.tag("display", 
                    String.format("%dx%d", mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels));
        }
    }

    private View.OnClickListener mClearButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            synchronized (mNotificationData) {
                // animate-swipe all dismissable notifications, then animate the shade closed
                int numChildren = mPile.getChildCount();

                int scrollTop = mScrollView.getScrollY();
                int scrollBottom = scrollTop + mScrollView.getHeight();
                final ArrayList<View> snapshot = new ArrayList<View>(numChildren);
                for (int i=0; i<numChildren; i++) {
                    final View child = mPile.getChildAt(i);
                    if (mPile.canChildBeDismissed(child) && child.getBottom() > scrollTop &&
                            child.getTop() < scrollBottom) {
                        snapshot.add(child);
                    }
                }
                if (snapshot.isEmpty()) {
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Decrease the delay for every row we animate to give the sense of
                        // accelerating the swipes
                        final int ROW_DELAY_DECREMENT = 10;
                        int currentDelay = 140;
                        int totalDelay = 0;

                        // Set the shade-animating state to avoid doing other work during
                        // all of these animations. In particular, avoid layout and
                        // redrawing when collapsing the shade.
                        mPile.setViewRemoval(false);

                        mPostCollapseCleanup = new Runnable() {
                            @Override
                            public void run() {
                                if (DEBUG) {
                                    Slog.v(TAG, "running post-collapse cleanup");
                                }
                                try {
                                    mPile.setViewRemoval(true);
                                    mBarService.onClearAllNotifications();
                                } catch (Exception ex) { }
                            }
                        };

                        View sampleView = snapshot.get(0);
                        int width = sampleView.getWidth();
                        final int velocity = width * 8; // 1000/8 = 125 ms duration
                        for (final View _v : snapshot) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mPile.dismissRowAnimated(_v, velocity);
                                }
                            }, totalDelay);
                            currentDelay = Math.max(50, currentDelay - ROW_DELAY_DECREMENT);
                            totalDelay += currentDelay;
                        }
                        // Delay the collapse animation until after all swipe animations have
                        // finished. Provide some buffer because there may be some extra delay
                        // before actually starting each swipe animation. Ideally, we'd
                        // synchronize the end of those animations with the start of the collaps
                        // exactly.
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                            }
                        }, totalDelay + 225);
                    }
                }).start();
            }
        }
    };

    public void startActivityDismissingKeyguard(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        animateCollapsePanels();
    }

    private View.OnClickListener mSettingsButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mHasSettingsPanel) {
                animateExpandSettingsPanel();
            } else {
                startActivityDismissingKeyguard(
                        new Intent(android.provider.Settings.ACTION_SETTINGS), true);
            }
        }
    };

       final Runnable DelayShortPress = new Runnable () {
            public void run() {
                    doubleClickCounter = 0;
                    animateCollapsePanels();
                    AwesomeAction.launchAction(mContext, mClockActions[shortClick]);
            }
        };

       final Runnable ResetDoubleClickCounter = new Runnable () {
            public void run() {
                    doubleClickCounter = 0;
            }
        };

    private View.OnClickListener mClockClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mClockDoubleClicked) {
                if (doubleClickCounter > 0) {
                    mHandler.removeCallbacks(DelayShortPress);
                    vibrate();
                    animateCollapsePanels();
                    AwesomeAction.launchAction(mContext, mClockActions[doubleClick]);
                    mHandler.postDelayed(ResetDoubleClickCounter, 50);
                } else {
                    doubleClickCounter = doubleClickCounter + 1;
                    vibrate();
                    mHandler.postDelayed(DelayShortPress, 400);
                }
            } else {
                vibrate();
                animateCollapsePanels();
                AwesomeAction.launchAction(mContext, mClockActions[shortClick]);
            }

        }
    };

    private View.OnLongClickListener mClockLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            animateCollapsePanels();
            AwesomeAction.launchAction(mContext, mClockActions[longClick]);
            return true;
        }
    };

    private View.OnClickListener mNotificationButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            animateExpandNotificationsPanel();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Slog.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                int flags = CommandQueue.FLAG_EXCLUDE_NONE;
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                    String reason = intent.getStringExtra("reason");
                    if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                        flags |= CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL;
                    }
                }
                animateCollapsePanels(flags);
            }
            else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // no waiting!
                makeExpandedInvisible();
                notifyNavigationBarScreenOn(false);
            }
            else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                if (DEBUG) {
                    Slog.v(TAG, "configuration changed: " + mContext.getResources().getConfiguration());
                }
                mDisplay.getSize(mCurrentDisplaySize);

                updateResources();
                repositionNavigationBar();
                updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
                if (mNavigationBarView != null && mNavigationBarView.mDelegateHelper != null) {
                    // if We are in Landscape/Phone Mode then swap the XY coordinates for NaVRing Swipe
                    mNavigationBarView.mDelegateHelper.setSwapXY((
                            mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) 
                            && (mCurrentUIMode == 0));
                }
                if (mGesturePanel !=null) {
                    mGesturePanel.setSwapXY((mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                            && (mCurrentUIMode == 0));
                    hideNavBar(); // Reset the Gesture window to the new orientation.
                }
            }
            else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                // work around problem where mDisplay.getRotation() is not stable while screen is off (bug 7086018)
                repositionNavigationBar();
                notifyNavigationBarScreenOn(true);
            }
        }
    };

    @Override
    public void userSwitched(int newUserId) {
        if (MULTIUSER_DEBUG) mNotificationPanelDebugText.setText("USER " + newUserId);
        animateCollapsePanels();
        updateNotificationIcons();
        resetUserSetupObserver();
    }

    private void resetUserSetupObserver() {
        mContext.getContentResolver().unregisterContentObserver(mUserSetupObserver);
        mUserSetupObserver.onChange(false);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), true,
                mUserSetupObserver,
                mCurrentUserId);
    }

    private void setIntruderAlertVisibility(boolean vis) {
        if (!ENABLE_INTRUDERS) return;
        if (DEBUG) {
            Slog.v(TAG, (vis ? "showing" : "hiding") + " intruder alert window");
        }
        mIntruderAlertView.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    public void dismissIntruder() {
        if (mCurrentlyIntrudingNotification == null) return;

        try {
            mBarService.onNotificationClear(
                    mCurrentlyIntrudingNotification.pkg,
                    mCurrentlyIntrudingNotification.tag,
                    mCurrentlyIntrudingNotification.id);
        } catch (android.os.RemoteException ex) {
            // oh well
        }
    }

    /**
     * Reload some of our resources when the configuration changes.
     *
     * We don't reload everything when the configuration changes -- we probably
     * should, but getting that smooth is tough.  Someday we'll fix that.  In the
     * meantime, just update the things that we know change.
     */
    void updateResources() {
        final Context context = mContext;
        final Resources res = context.getResources();

        // detect theme change.
        CustomTheme newTheme = res.getConfiguration().customTheme;
        if (newTheme != null &&
                (mCurrentTheme == null || !mCurrentTheme.equals(newTheme))) {
            mCurrentTheme = (CustomTheme)newTheme.clone();
            try {
                Runtime.getRuntime().exec("pkill -TERM -f com.android.systemui");
            } catch (IOException e) {
                // we're screwed here fellas
            }
        } else {

            if (mClearButton instanceof TextView) {
                ((TextView)mClearButton).setText(context.getText(R.string.status_bar_clear_all_button));
            }

            loadDimens();
        }
    }

    protected void loadDimens() {
        final Resources res = mContext.getResources();

        mNaturalBarHeight = res.getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);

        int newIconSize = res.getDimensionPixelSize(
            com.android.internal.R.dimen.status_bar_icon_size);
        int newIconHPadding = res.getDimensionPixelSize(
            R.dimen.status_bar_icon_padding);

        if (newIconHPadding != mIconHPadding || newIconSize != mIconSize) {
//            Slog.d(TAG, "size=" + newIconSize + " padding=" + newIconHPadding);
            mIconHPadding = newIconHPadding;
            mIconSize = newIconSize;
            //reloadAllNotificationIcons(); // reload the tray
        }

        mEdgeBorder = res.getDimensionPixelSize(R.dimen.status_bar_edge_ignore);

        mSelfExpandVelocityPx = res.getDimension(R.dimen.self_expand_velocity);
        mSelfCollapseVelocityPx = res.getDimension(R.dimen.self_collapse_velocity);
        mFlingExpandMinVelocityPx = res.getDimension(R.dimen.fling_expand_min_velocity);
        mFlingCollapseMinVelocityPx = res.getDimension(R.dimen.fling_collapse_min_velocity);

        mCollapseMinDisplayFraction = res.getFraction(R.dimen.collapse_min_display_fraction, 1, 1);
        mExpandMinDisplayFraction = res.getFraction(R.dimen.expand_min_display_fraction, 1, 1);

        mExpandAccelPx = res.getDimension(R.dimen.expand_accel);
        mCollapseAccelPx = res.getDimension(R.dimen.collapse_accel);

        mFlingGestureMaxXVelocityPx = res.getDimension(R.dimen.fling_gesture_max_x_velocity);

        mFlingGestureMaxOutputVelocityPx = res.getDimension(R.dimen.fling_gesture_max_output_velocity);

        mNotificationPanelMarginBottomPx
            = (int) res.getDimension(R.dimen.notification_panel_margin_bottom);
        mNotificationPanelMarginPx
            = (int) res.getDimension(R.dimen.notification_panel_margin_left);
        mNotificationPanelGravity = res.getInteger(R.integer.notification_panel_layout_gravity);
        if (mNotificationPanelGravity <= 0) {
            mNotificationPanelGravity = Gravity.LEFT | Gravity.TOP;
        }
        mSettingsPanelGravity = res.getInteger(R.integer.settings_panel_layout_gravity);
        if (mSettingsPanelGravity <= 0) {
            mSettingsPanelGravity = Gravity.RIGHT | Gravity.TOP;
        }

        mCarrierLabelHeight = res.getDimensionPixelSize(R.dimen.carrier_label_height);
        mNotificationHeaderHeight = res.getDimensionPixelSize(R.dimen.notification_panel_header_height);

        mNotificationPanelMinHeightFrac = res.getFraction(R.dimen.notification_panel_min_height_frac, 1, 1);
        if (mNotificationPanelMinHeightFrac < 0f || mNotificationPanelMinHeightFrac > 1f) {
            mNotificationPanelMinHeightFrac = 0f;
        }

        if (false) Slog.v(TAG, "updateResources");
    }

    //
    // tracing
    //

    void postStartTracing() {
        mHandler.postDelayed(mStartTracing, 3000);
    }

    void vibrate() {
        if (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0) {
            android.os.Vibrator vib = (android.os.Vibrator)mContext.getSystemService(
                    Context.VIBRATOR_SERVICE);
            vib.vibrate(50);
        }
    }

    Runnable mStartTracing = new Runnable() {
        public void run() {
            vibrate();
            SystemClock.sleep(250);
            Slog.d(TAG, "startTracing");
            android.os.Debug.startMethodTracing("/data/statusbar-traces/trace");
            mHandler.postDelayed(mStopTracing, 10000);
        }
    };

    Runnable mStopTracing = new Runnable() {
        public void run() {
            android.os.Debug.stopMethodTracing();
            Slog.d(TAG, "stopTracing");
            vibrate();
        }
    };

    @Override
    protected void haltTicker() {
        mTicker.halt();
    }

    @Override
    protected boolean shouldDisableNavbarGestures() {
        return !isDeviceProvisioned()
                || mExpandedVisible
                || (mDisabled & StatusBarManager.DISABLE_SEARCH) != 0;
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

   class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

		void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_CLOCK[shortClick]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_CLOCK[longClick]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_CLOCK[doubleClick]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CURRENT_UI_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_ENABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_COLORIZE[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.NOTIFICATIONS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_COLORIZE[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.QUICK_SETTINGS]), false, this);
        }

         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();

        mClockActions[shortClick] = Settings.System.getString(cr,
                Settings.System.NOTIFICATION_CLOCK[shortClick]);

        mClockActions[longClick] = Settings.System.getString(cr,
                Settings.System.NOTIFICATION_CLOCK[longClick]);

        mClockActions[doubleClick] = Settings.System.getString(cr,
                Settings.System.NOTIFICATION_CLOCK[doubleClick]);

        if (mClockActions[shortClick]  == null ||mClockActions[shortClick].equals("")) {
            mClockActions[shortClick] = "**clockoptions**";
        }
        if (mClockActions[longClick]  == null || mClockActions[longClick].equals("")) {
            mClockActions[longClick] = "**null**";
        }
        if (mClockActions[doubleClick] == null || mClockActions[doubleClick].equals("") || mClockActions[doubleClick].equals("**null**")) {
            mClockActions[doubleClick] = "**null**";
            mClockDoubleClicked = false;
        } else {
            mClockDoubleClicked = true;
        }
        mCurrentUIMode = Settings.System.getInt(cr,Settings.System.CURRENT_UI_MODE, 0);
        mNavBarAutoHide = Settings.System.getBoolean(cr, Settings.System.NAV_HIDE_ENABLE, false);
        mAutoHideTimeOut = Settings.System.getInt(cr, Settings.System.NAV_HIDE_TIMEOUT, mAutoHideTimeOut);
        mToggleStyle = Settings.System.getInt(cr, Settings.System.TOGGLES_STYLE,ToggleManager.STYLE_TILE);
        if (mNavBarAutoHide) {
            setupAutoHide();
        } else if (mGesturePanel != null) {
            disableAutoHide();
        }
        updateRibbonTargets();
    }

    public boolean skipToSettingsPanel() {
        if (mPile == null || mNotificationData == null) return false;

        int N = mNotificationData.size();
        int thisUsersNotifications = 0;
        for (int i=0; i<N; i++) {
            Entry ent = mNotificationData.get(N-i-1);
            if(ent != null
                    && ent.notification != null
                    && notificationIsForCurrentUser(ent.notification)) {
                switch(ent.notification.id) {
                    // ignore adb icon
                    case com.android.internal.R.drawable.stat_sys_adb:
                        continue;
                }
                thisUsersNotifications++;
            }
        }
        if(thisUsersNotifications == 0)
            return true;

        return false;
    }
}
