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

import static android.app.ActivityTaskManager.SPLIT_SCREEN_CREATE_MODE_BOTTOM_OR_RIGHT;
import static android.app.ActivityTaskManager.SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT;
import static android.app.StatusBarManager.WINDOW_STATE_HIDDEN;
import static android.app.StatusBarManager.WINDOW_STATE_SHOWING;
import static android.app.StatusBarManager.WindowType;
import static android.app.StatusBarManager.WindowVisibleState;
import static android.app.StatusBarManager.windowStateToString;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY;

import static com.android.systemui.Dependency.ALLOW_NOTIFICATION_LONG_PRESS_NAME;
import static com.android.systemui.Dependency.BG_HANDLER;
import static com.android.systemui.Dependency.MAIN_HANDLER;
import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_ASLEEP;
import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_AWAKE;
import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_WAKING;
import static com.android.systemui.shared.system.WindowManagerWrapper.NAV_BAR_POS_INVALID;
import static com.android.systemui.shared.system.WindowManagerWrapper.NAV_BAR_POS_LEFT;
import static com.android.systemui.statusbar.NotificationLockscreenUserManager.PERMISSION_SELF;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_OPAQUE;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSLUCENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_WARNING;
import static com.android.systemui.statusbar.phone.BarTransitions.TransitionMode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import android.app.Activity;
import android.animation.TimeInterpolator;
import com.android.systemui.chaos.lab.gestureanywhere.GestureAnywhereView;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IWallpaperManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.om.IOverlayManager;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.metrics.LogMaker;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.RemoteAnimationAdapter;
import android.view.ThreadedRenderer;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.DateTimeView;
import android.widget.FrameLayout;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.statusbar.ThemeAccentUtils;
import com.android.internal.util.rr.RRUtils;
import com.android.internal.util.hwkeys.ActionConstants;
import com.android.internal.util.hwkeys.ActionUtils;
import com.android.internal.util.hwkeys.PackageMonitor;
import com.android.internal.util.hwkeys.PackageMonitor.PackageChangedListener;
import com.android.internal.util.hwkeys.PackageMonitor.PackageState;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.statusbar.phone.Ticker;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.ActivityIntentHelper;
import com.android.systemui.ActivityStarterDelegate;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.DemoMode;
import com.android.systemui.Dependency;
import com.android.systemui.Dumpable;
import com.android.systemui.EventLogTags;
import com.android.systemui.ForegroundServiceController;
import com.android.systemui.InitController;
import com.android.systemui.Interpolators;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.SystemUIFactory;
import com.android.systemui.UiOffloadThread;
import com.android.systemui.appops.AppOpsController;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.bubbles.BubbleController;
import com.android.systemui.charging.WirelessChargingAnimation;
import com.android.systemui.classifier.FalsingLog;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.rr.ImageUtilities;
import com.android.systemui.doze.DozeHost;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.fragments.ExtensionFragmentListener;
import com.android.systemui.fragments.FragmentHostManager;
import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.PluginDependencyProvider;
import com.android.systemui.plugins.qs.QS;
import com.android.systemui.plugins.statusbar.NotificationSwipeActionHelper.SnoozeOption;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSFragment;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.ScreenPinningRequest;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.WindowManagerWrapper;
import com.android.systemui.stackdivider.Divider;
import com.android.systemui.stackdivider.WindowManagerProxy;
import com.android.systemui.statusbar.BackDropView;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.CrossFadeHelper;
import com.android.systemui.statusbar.EmptyShadeView;
import com.android.systemui.statusbar.GestureRecorder;
import com.android.systemui.statusbar.KeyboardShortcuts;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.statusbar.NavigationBarController;
import com.android.systemui.statusbar.NotificationListener;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationPresenter;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShelf;
import com.android.systemui.statusbar.NotificationViewHierarchyManager;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.ScrimView;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.info.DataUsageView;
import com.android.systemui.navigation.pulse.VisualizerView;
import com.android.systemui.statusbar.notification.ActivityLaunchAnimator;
import com.android.systemui.statusbar.notification.BypassHeadsUpNotifier;
import com.android.systemui.statusbar.notification.DynamicPrivacyController;
import com.android.systemui.statusbar.notification.NotificationActivityStarter;
import com.android.systemui.statusbar.notification.NotificationAlertingManager;
import com.android.systemui.statusbar.notification.NotificationClicker;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.NotificationInterruptionStateProvider;
import com.android.systemui.statusbar.notification.NotificationListController;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.ViewGroupFadeHelper;
import com.android.systemui.statusbar.notification.VisualStabilityManager;
import com.android.systemui.statusbar.notification.collection.NotificationData;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.NotificationRowBinderImpl;
import com.android.systemui.statusbar.notification.logging.NotificationLogger;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationGutsManager;
import com.android.systemui.statusbar.notification.stack.NotificationListContainer;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.phone.BarTransitions.BarBackgroundDrawable;
import com.android.systemui.statusbar.phone.UnlockMethodCache.OnUnlockMethodChangedListener;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BurnInProtectionController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController.DeviceProvisionedListener;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcher;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.OnHeadsUpChangedListener;
import com.android.systemui.statusbar.policy.PulseController;
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoControllerImpl;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.InjectionInflationController;
import com.android.systemui.util.leak.RotationUtils;
import com.android.systemui.volume.VolumeComponent;

import lineageos.providers.LineageSettings;
import com.android.internal.util.rr.Utils;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.android.internal.util.rr.ImageHelper;
import javax.inject.Inject;
import javax.inject.Named;

import dagger.Subcomponent;

public class StatusBar extends SystemUI implements DemoMode,
        ActivityStarter, OnUnlockMethodChangedListener,
        OnHeadsUpChangedListener, CommandQueue.Callbacks, ZenModeController.Callback,
        ColorExtractor.OnColorsChangedListener, ConfigurationListener,
        StatusBarStateController.StateListener, ShadeController,
        ActivityLaunchAnimator.Callback, AppOpsController.Callback,
        PackageChangedListener, TunerService.Tunable {
    public static final boolean MULTIUSER_DEBUG = false;

    public static final boolean ENABLE_CHILD_NOTIFICATIONS
            = SystemProperties.getBoolean("debug.child_notifs", true);

    protected static final int MSG_HIDE_RECENT_APPS = 1020;
    protected static final int MSG_PRELOAD_RECENT_APPS = 1022;
    protected static final int MSG_CANCEL_PRELOAD_RECENT_APPS = 1023;
    protected static final int MSG_TOGGLE_KEYBOARD_SHORTCUTS_MENU = 1026;
    protected static final int MSG_DISMISS_KEYBOARD_SHORTCUTS_MENU = 1027;

    // Should match the values in PhoneWindowManager
    public static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_SCREENSHOT = "screenshot";

    public static final String SCREEN_BRIGHTNESS_MODE =
            "system:" + Settings.System.SCREEN_BRIGHTNESS_MODE;
    public static final String STATUS_BAR_BRIGHTNESS_CONTROL =
            "lineagesystem:" + LineageSettings.System.STATUS_BAR_BRIGHTNESS_CONTROL;

    public static final String FORCE_SHOW_NAVBAR =
            "lineagesystem:" + LineageSettings.System.FORCE_SHOW_NAVBAR;
    private static final String BERRY_SWITCH_STYLE =
            "system:" + Settings.System.BERRY_SWITCH_STYLE;
    private static final String DISPLAY_CUTOUT_MODE =
            "system:" + Settings.System.DISPLAY_CUTOUT_MODE;
    private static final String STOCK_STATUSBAR_IN_HIDE =
            "system:" + Settings.System.STOCK_STATUSBAR_IN_HIDE;
    private static final String NAVBAR_STYLE =
            "system:" + Settings.System.NAVBAR_STYLE;

    private static final String BANNER_ACTION_CANCEL =
            "com.android.systemui.statusbar.banner_action_cancel";
    private static final String BANNER_ACTION_SETUP =
            "com.android.systemui.statusbar.banner_action_setup";
    public static final String TAG = "StatusBar";
    public static final boolean DEBUG = false;
    public static final boolean SPEW = false;
    public static final boolean DUMPTRUCK = true; // extra dumpsys info
    public static final boolean DEBUG_GESTURES = false;
    public static final boolean DEBUG_MEDIA_FAKE_ARTWORK = false;
    public static final boolean DEBUG_CAMERA_LIFT = false;

    public static final boolean DEBUG_WINDOW_STATE = false;

    // additional instrumentation for testing purposes; intended to be left on during development
    public static final boolean CHATTY = DEBUG;

    public static final boolean SHOW_LOCKSCREEN_MEDIA_ARTWORK = true;

    public static final String ACTION_FAKE_ARTWORK = "fake_artwork";

    private static final int MSG_OPEN_NOTIFICATION_PANEL = 1000;
    private static final int MSG_CLOSE_PANELS = 1001;
    private static final int MSG_OPEN_SETTINGS_PANEL = 1002;
    private static final int MSG_LAUNCH_TRANSITION_TIMEOUT = 1003;
    // 1020-1040 reserved for BaseStatusBar

    // Time after we abort the launch transition.
    private static final long LAUNCH_TRANSITION_TIMEOUT_MS = 5000;

    protected static final boolean CLOSE_PANEL_WHEN_EMPTIED = true;

    /**
     * The delay to reset the hint text when the hint animation is finished running.
     */
    private static final int HINT_RESET_DELAY_MS = 1200;

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    private static final float BRIGHTNESS_CONTROL_PADDING = 0.15f;
    private static final int BRIGHTNESS_CONTROL_LONG_PRESS_TIMEOUT = 750; // ms
    private static final int BRIGHTNESS_CONTROL_LINGER_THRESHOLD = 20;

    public static final int FADE_KEYGUARD_START_DELAY = 0;
    public static final int FADE_KEYGUARD_DURATION = 0;
    public static final int FADE_KEYGUARD_DURATION_PULSING = 96;

    /** If true, the system is in the half-boot-to-decryption-screen state.
     * Prudently disable QS and notifications.  */
    public static final boolean ONLY_CORE_APPS;

    /** If true, the lockscreen will show a distinct wallpaper */
    public static final boolean ENABLE_LOCKSCREEN_WALLPAPER = true;

    static {
        boolean onlyCoreApps;
        try {
            IPackageManager packageManager =
                    IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
            onlyCoreApps = packageManager.isOnlyCoreApps();
        } catch (RemoteException e) {
            onlyCoreApps = false;
        }
        ONLY_CORE_APPS = onlyCoreApps;
    }

    /**
     * The {@link StatusBarState} of the status bar.
     */
    protected static int mState;
    protected boolean mBouncerShowing;

    private PhoneStatusBarPolicy mIconPolicy;
    private StatusBarSignalPolicy mSignalPolicy;

    private VolumeComponent mVolumeComponent;
    protected BiometricUnlockController mBiometricUnlockController;
    protected LightBarController mLightBarController;
    @Nullable
    protected LockscreenWallpaper mLockscreenWallpaper;
    @VisibleForTesting
    protected AutoHideController mAutoHideController;

    private BurnInProtectionController mBurnInProtectionController;

    private int mNaturalBarHeight = -1;

    private final Point mCurrentDisplaySize = new Point();

    protected StatusBarWindowView mStatusBarWindow;
    protected PhoneStatusBarView mStatusBarView;
    private int mStatusBarWindowState = WINDOW_STATE_SHOWING;
    protected StatusBarWindowController mStatusBarWindowController;
    protected UnlockMethodCache mUnlockMethodCache;
    @VisibleForTesting
    KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    @VisibleForTesting
    DozeServiceHost mDozeServiceHost = new DozeServiceHost();
    private boolean mWakeUpComingFromTouch;
    private PointF mWakeUpTouchLocation;

    private final Object mQueueLock = new Object();

    protected StatusBarIconController mIconController;
    @Inject
    InjectionInflationController mInjectionInflater;
    @Inject
    PulseExpansionHandler mPulseExpansionHandler;
    @Inject
    NotificationWakeUpCoordinator mWakeUpCoordinator;
    @Inject
    KeyguardBypassController mKeyguardBypassController;
    @Inject
    protected HeadsUpManagerPhone mHeadsUpManager;
    @Inject
    DynamicPrivacyController mDynamicPrivacyController;
    @Inject
    BypassHeadsUpNotifier mBypassHeadsUpNotifier;
    @Nullable
    @Inject
    protected KeyguardLiftController mKeyguardLiftController;
    @Inject
    @Named(ALLOW_NOTIFICATION_LONG_PRESS_NAME)
    boolean mAllowNotificationLongPress;

    // viewgroup containing the normal contents of the statusbar
    LinearLayout mStatusBarContent;
    // Other views that need hiding for the notification ticker
    View mCenterClockLayout;

    // expanded notifications
    protected NotificationPanelView mNotificationPanel; // the sliding/resizing panel within the notification window

    // settings
    private QSPanel mQSPanel;
    private CollapsedStatusBarFragment mCollapsedStatusBarFragment;

    KeyguardIndicationController mKeyguardIndicationController;

    // RemoteInputView to be activated after unlock
    private View mPendingRemoteInputView;

    private RemoteInputQuickSettingsDisabler mRemoteInputQuickSettingsDisabler =
            Dependency.get(RemoteInputQuickSettingsDisabler.class);

    private View mReportRejectedTouch;

    private boolean mExpandedVisible;

    private boolean mFpDismissNotifications;
    private boolean mGaEnabled;

    private final int[] mAbsPos = new int[2];
    private final ArrayList<Runnable> mPostCollapseRunnables = new ArrayList<>();

    protected NotificationGutsManager mGutsManager;
    protected NotificationLogger mNotificationLogger;
    protected NotificationEntryManager mEntryManager;
    protected NotificationListController mNotificationListController;
    protected NotificationInterruptionStateProvider mNotificationInterruptionStateProvider;
    protected NotificationViewHierarchyManager mViewHierarchyManager;
    protected ForegroundServiceController mForegroundServiceController;
    protected AppOpsController mAppOpsController;
    protected KeyguardViewMediator mKeyguardViewMediator;
    protected ZenModeController mZenController;
    protected final NotificationAlertingManager mNotificationAlertingManager =
            Dependency.get(NotificationAlertingManager.class);

    private DisplayManager mDisplayManager;

    private int mMinBrightness;
    private int mInitialTouchX;
    private int mInitialTouchY;
    private int mLinger;
    private int mQuickQsTotalHeight;
    private boolean mAutomaticBrightness;
    private boolean mBrightnessControl;
    private boolean mBrightnessChanged;
    private boolean mJustPeeked;

    //Lockscreen Notifications
    private int mMaxKeyguardNotifConfig;

   // status bar notification ticker
    public int mTickerEnabled;
    public Ticker mTicker;
    private boolean mTicking;
    private int mTickerAnimationMode;
    private int mTickerTickDuration;

    // for disabling the status bar
    private int mDisabled1 = 0;
    private int mDisabled2 = 0;

    // tracking calls to View.setSystemUiVisibility()
    private int mSystemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
    private final Rect mLastFullscreenStackBounds = new Rect();
    private final Rect mLastDockedStackBounds = new Rect();

    private final DisplayMetrics mDisplayMetrics = Dependency.get(DisplayMetrics.class);

    private boolean mHeadsUpDisabled, mGamingModeActivated;

    private ImageView mQSBlurView;
    private boolean mQSBlurEnabled;
    private boolean mQSBlurred;
    private boolean blurperformed = false;
    private boolean mSysuiRoundedFwvals;
    private static final String SYSUI_ROUNDED_FWVALS =
            Settings.Secure.SYSUI_ROUNDED_FWVALS;

    private PackageMonitor mPackageMonitor;
    private int mBackgroundFilter;

    // XXX: gesture research
    private final GestureRecorder mGestureRec = DEBUG_GESTURES
        ? new GestureRecorder("/sdcard/statusbar_gestures.dat")
        : null;

    private ScreenPinningRequest mScreenPinningRequest;

    private final MetricsLogger mMetricsLogger = Dependency.get(MetricsLogger.class);

    Runnable mLongPressBrightnessChange = new Runnable() {
        @Override
        public void run() {
            mStatusBarView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            adjustBrightness(mInitialTouchX);
            mLinger = BRIGHTNESS_CONTROL_LINGER_THRESHOLD + 1;
        }
    };

    private boolean dataupdated = false;

    private static Context mStaticContext;
    private static ImageButton mDismissAllButton;
    protected static NotificationPanelView mStaticNotificationPanel;
    public static boolean mClearableNotifications;

    public void resetTrackInfo() {
        if (mTicker != null) {
            mTicker.resetShownMediaMetadata();
        }
    }

    // ensure quick settings is disabled until the current user makes it through the setup wizard
    @VisibleForTesting
    protected boolean mUserSetup = false;
    private final DeviceProvisionedListener mUserSetupObserver = new DeviceProvisionedListener() {
        @Override
        public void onUserSetupChanged() {
            final boolean userSetup = mDeviceProvisionedController.isUserSetup(
                    mDeviceProvisionedController.getCurrentUser());
            Log.d(TAG, "mUserSetupObserver - DeviceProvisionedListener called for user "
                    + mDeviceProvisionedController.getCurrentUser());
            if (MULTIUSER_DEBUG) {
                Log.d(TAG, String.format("User setup changed: userSetup=%s mUserSetup=%s",
                        userSetup, mUserSetup));
            }

            if (userSetup != mUserSetup) {
                mUserSetup = userSetup;
                if (!mUserSetup && mStatusBarView != null)
                    animateCollapseQuickSettings();
                if (mNotificationPanel != null) {
                    mNotificationPanel.setUserSetupComplete(mUserSetup);
                }
                updateQsExpansionEnabled();
            }
        }
    };

    protected final H mHandler = createHandler();

    private int mInteractingWindows;
    private @TransitionMode int mStatusBarMode;

    private ViewMediatorCallback mKeyguardViewMediatorCallback;
    protected ScrimController mScrimController;
    protected DozeScrimController mDozeScrimController;
    private final UiOffloadThread mUiOffloadThread = Dependency.get(UiOffloadThread.class);

    protected boolean mDozing;
    private boolean mDozingRequested;

    protected NotificationMediaManager mMediaManager;
    protected NotificationLockscreenUserManager mLockscreenUserManager;
    protected NotificationRemoteInputManager mRemoteInputManager;
    private boolean mWallpaperSupported;

    private VolumePluginManager mVolumePluginManager;

    private VisualizerView mVisualizerView;

    private final BroadcastReceiver mWallpaperChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mWallpaperSupported) {
                // Receiver should not have been registered at all...
                Log.wtf(TAG, "WallpaperManager not supported");
                return;
            }
            WallpaperManager wallpaperManager = context.getSystemService(WallpaperManager.class);
            WallpaperInfo info = wallpaperManager.getWallpaperInfo(UserHandle.USER_CURRENT);
            final boolean deviceSupportsAodWallpaper = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_dozeSupportsAodWallpaper);
            final boolean imageWallpaperInAmbient =
                    !DozeParameters.getInstance(mContext).getDisplayNeedsBlanking();
            // If WallpaperInfo is null, it must be ImageWallpaper.
            final boolean supportsAmbientMode = deviceSupportsAodWallpaper
                    && ((info == null && imageWallpaperInAmbient)
                        || (info != null && info.supportsAmbientMode()));

            mStatusBarWindowController.setWallpaperSupportsAmbientMode(supportsAmbientMode);
            mScrimController.setWallpaperSupportsAmbientMode(supportsAmbientMode);
        }
    };

    private Runnable mLaunchTransitionEndRunnable;
    private NotificationEntry mDraggedDownEntry;
    private boolean mLaunchCameraWhenFinishedWaking;
    private boolean mLaunchCameraOnFinishedGoingToSleep;
    private int mLastCameraLaunchSource;
    protected PowerManager.WakeLock mGestureWakeLock;
    private Vibrator mVibrator;
    private long[] mCameraLaunchGestureVibePattern;

    private final int[] mTmpInt2 = new int[2];

    // Fingerprint (as computed by getLoggingFingerprint() of the last logged state.
    private int mLastLoggedStateFingerprint;
    private boolean mTopHidesStatusBar;
    private boolean mStatusBarWindowHidden;
    private boolean mHideIconsForBouncer;
    private boolean mIsOccluded;
    private boolean mWereIconsJustHidden;
    private boolean mBouncerWasShowingWhenHidden;
    private int mDarkStyle;
    private int mSwitchStyle;
    private int mNavbarStyle;
    private boolean mPowerSave;
    private boolean mUseDarkTheme;
    private IOverlayManager mOverlayManager;

    private int mImmerseMode;
    private boolean mStockStatusBar = true;
    private boolean mPortrait = true;

    // Notifies StatusBarKeyguardViewManager every time the keyguard transition is over,
    // this animation is tied to the scrim for historic reasons.
    // TODO: notify when keyguard has faded away instead of the scrim.
    private final ScrimController.Callback mUnlockScrimCallback = new ScrimController
            .Callback() {
        @Override
        public void onFinished() {
            if (mStatusBarKeyguardViewManager == null) {
                Log.w(TAG, "Tried to notify keyguard visibility when "
                        + "mStatusBarKeyguardViewManager was null");
                return;
            }
            if (mKeyguardMonitor.isKeyguardFadingAway()) {
                mStatusBarKeyguardViewManager.onKeyguardFadedAway();
            }
        }

        @Override
        public void onCancelled() {
            onFinished();
        }
    };

    private FlashlightController mFlashlightController;
    private KeyguardUserSwitcher mKeyguardUserSwitcher;
    protected UserSwitcherController mUserSwitcherController;
    protected NetworkController mNetworkController;
    protected KeyguardMonitor mKeyguardMonitor;
    protected BatteryController mBatteryController;
    protected boolean mPanelExpanded;
    private UiModeManager mUiModeManager;
    protected boolean mIsKeyguard;
    private LogMaker mStatusBarStateLog;
    protected NotificationIconAreaController mNotificationIconAreaController;
    @Nullable private View mAmbientIndicationContainer;
    protected SysuiColorExtractor mColorExtractor;
    protected ScreenLifecycle mScreenLifecycle;
    @VisibleForTesting
    protected WakefulnessLifecycle mWakefulnessLifecycle;

    private final View.OnClickListener mGoToLockedShadeListener = v -> {
        if (mState == StatusBarState.KEYGUARD) {
            wakeUpIfDozing(SystemClock.uptimeMillis(), v, "SHADE_CLICK");
            goToLockedShade(null);
        }
    };
    private boolean mNoAnimationOnNextBarModeChange;
    protected FalsingManager mFalsingManager;
    private final SysuiStatusBarStateController mStatusBarStateController =
            (SysuiStatusBarStateController) Dependency.get(StatusBarStateController.class);

    private final KeyguardUpdateMonitorCallback mUpdateCallback =
            new KeyguardUpdateMonitorCallback() {
                @Override
                public void onDreamingStateChanged(boolean dreaming) {
                    if (dreaming) {
                        maybeEscalateHeadsUp();
                    }
                }

                @Override
                public void onStrongAuthStateChanged(int userId) {
                    super.onStrongAuthStateChanged(userId);
                    mEntryManager.updateNotifications();
                }
            };
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private HeadsUpAppearanceController mHeadsUpAppearanceController;
    private boolean mVibrateOnOpening;
    protected VibratorHelper mVibratorHelper;
    private ActivityLaunchAnimator mActivityLaunchAnimator;
    protected StatusBarNotificationPresenter mPresenter;
    private NotificationActivityStarter mNotificationActivityStarter;
    private boolean mPulsing;
    protected BubbleController mBubbleController;
    private final BubbleController.BubbleExpandListener mBubbleExpandListener =
            (isExpanding, key) -> {
                mEntryManager.updateNotifications();
                updateScrimController();
            };
    private ActivityIntentHelper mActivityIntentHelper;
    private ShadeController mShadeController;
    private KeyguardSliceProvider mSliceProvider;

    private boolean mShowNavBar;

    private int mRunningTaskId;
    private IntentFilter mDefaultHomeIntentFilter;
    private static final String[] DEFAULT_HOME_CHANGE_ACTIONS = new String[] {
            PackageManagerWrapper.ACTION_PREFERRED_ACTIVITY_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REMOVED
    };
    @Nullable private ComponentName mDefaultHome;
    private boolean mIsLauncherShowing;
    private ComponentName mTaskComponentName = null;

    @Override
    public void onActiveStateChanged(int code, int uid, String packageName, boolean active) {
        Dependency.get(MAIN_HANDLER).post(() -> {
            mForegroundServiceController.onAppOpChanged(code, uid, packageName, active);
            mNotificationListController.updateNotificationsForAppOp(code, uid, packageName, active);
        });
    }

    protected static final int[] APP_OPS = new int[] {AppOpsManager.OP_CAMERA,
            AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
            AppOpsManager.OP_RECORD_AUDIO,
            AppOpsManager.OP_COARSE_LOCATION,
            AppOpsManager.OP_FINE_LOCATION};

    @Override
    public void start() {
        getDependencies();
        if (mScreenLifecycle != null && mScreenObserver != null) {
            mScreenLifecycle.addObserver(mScreenObserver);
        }

        if (mWakefulnessLifecycle != null && mWakefulnessObserver != null) {
            mWakefulnessLifecycle.addObserver(mWakefulnessObserver);
        }

        mNotificationListener.registerAsSystemService();
        if (mBubbleController != null) {
            mBubbleController.setExpandListener(mBubbleExpandListener);
        }

        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        mUiModeManager = mContext.getSystemService(UiModeManager.class);
        mNotificationAlertingManager.setStatusBar(this);
        mKeyguardViewMediator = getComponent(KeyguardViewMediator.class);
        mNavigationBarSystemUiVisibility = mNavigationBarController.createSystemUiVisibility();
        mActivityIntentHelper = new ActivityIntentHelper(mContext);

        mSliceProvider = KeyguardSliceProvider.getAttachedInstance();
        if (mSliceProvider != null) {
            mSliceProvider.initDependencies(mMediaManager, mStatusBarStateController,
                    mKeyguardBypassController, DozeParameters.getInstance(mContext));
        } else {
            Log.w(TAG, "Cannot init KeyguardSliceProvider dependencies");
        }

        mColorExtractor.addOnColorsChangedListener(this);
        mStatusBarStateController.addCallback(this,
                SysuiStatusBarStateController.RANK_STATUS_BAR);

        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, SCREEN_BRIGHTNESS_MODE);
        tunerService.addTunable(this, STATUS_BAR_BRIGHTNESS_CONTROL);
        tunerService.addTunable(this, BERRY_SWITCH_STYLE);
        tunerService.addTunable(this, SYSUI_ROUNDED_FWVALS);
        tunerService.addTunable(this, DISPLAY_CUTOUT_MODE);
        tunerService.addTunable(this, STOCK_STATUSBAR_IN_HIDE);
        tunerService.addTunable(this, NAVBAR_STYLE);

        mDisplayManager = mContext.getSystemService(DisplayManager.class);

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.checkService(DreamService.DREAM_SERVICE));

        mDisplay = mWindowManager.getDefaultDisplay();
        mDisplayId = mDisplay.getDisplayId();
        updateDisplaySize();

        mPackageMonitor = new PackageMonitor();
        mPackageMonitor.register(mContext, mHandler);
        mPackageMonitor.addListener(this);

        mVibrateOnOpening = mContext.getResources().getBoolean(
                R.bool.config_vibrateOnIconAnimation);
        mVibratorHelper = Dependency.get(VibratorHelper.class);

        DateTimeView.setReceiverHandler(Dependency.get(Dependency.TIME_TICK_HANDLER));
        putComponent(StatusBar.class, this);

        // start old BaseStatusBar.start().
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
        mDevicePolicyManager = (DevicePolicyManager) mContext.getSystemService(
                Context.DEVICE_POLICY_SERVICE);

        mAccessibilityManager = (AccessibilityManager)
                mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        mKeyguardUpdateMonitor.setKeyguardBypassController(mKeyguardBypassController);
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        mRecents = getComponent(Recents.class);

        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mFalsingManager = Dependency.get(FalsingManager.class);
        mWallpaperSupported =
                mContext.getSystemService(WallpaperManager.class).isWallpaperSupported();
	mVolumePluginManager = new VolumePluginManager(mContext, mHandler);

        // Connect in to the status bar manager service
        mCommandQueue = getComponent(CommandQueue.class);
        mCommandQueue.addCallback(this);

        RegisterStatusBarResult result = null;
        try {
            result = mBarService.registerStatusBar(mCommandQueue);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }

        createAndAddWindows(result);

        if (mWallpaperSupported) {
            // Make sure we always have the most current wallpaper info.
            IntentFilter wallpaperChangedFilter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
            mContext.registerReceiverAsUser(mWallpaperChangedReceiver, UserHandle.ALL,
                    wallpaperChangedFilter, null /* broadcastPermission */, null /* scheduler */);
            mWallpaperChangedReceiver.onReceive(mContext, null);
        } else if (DEBUG) {
            Log.v(TAG, "start(): no wallpaper service ");
        }

        // Set up the initial notification state. This needs to happen before CommandQueue.disable()
        setUpPresenter();

        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();

        setSystemUiVisibility(mDisplayId, result.mSystemUiVisibility,
                result.mFullscreenStackSysUiVisibility, result.mDockedStackSysUiVisibility,
                0xffffffff, result.mFullscreenStackBounds, result.mDockedStackBounds,
                result.mNavbarColorManagedByIme);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        setImeWindowStatus(mDisplayId, result.mImeToken, result.mImeWindowVis,
                result.mImeBackDisposition, result.mShowImeSwitcher);

        // Set up the initial icon state
        int numIcons = result.mIcons.size();
        for (int i = 0; i < numIcons; i++) {
            mCommandQueue.setIcon(result.mIcons.keyAt(i), result.mIcons.valueAt(i));
        }


        if (DEBUG) {
            Log.d(TAG, String.format(
                    "init: icons=%d disabled=0x%08x lights=0x%08x imeButton=0x%08x",
                    numIcons,
                    result.mDisabledFlags1,
                    result.mSystemUiVisibility,
                    result.mImeWindowVis));
        }

        IntentFilter internalFilter = new IntentFilter();
        internalFilter.addAction(BANNER_ACTION_CANCEL);
        internalFilter.addAction(BANNER_ACTION_SETUP);
        mContext.registerReceiver(mBannerActionBroadcastReceiver, internalFilter, PERMISSION_SELF,
                null);

        if (mWallpaperSupported) {
            IWallpaperManager wallpaperManager = IWallpaperManager.Stub.asInterface(
                    ServiceManager.getService(Context.WALLPAPER_SERVICE));
            try {
                wallpaperManager.setInAmbientMode(false /* ambientMode */, 0L /* duration */);
            } catch (RemoteException e) {
                // Just pass, nothing critical.
            }
        }

        // end old BaseStatusBar.start().

        // Lastly, call to the icon policy to install/update all the icons.
        mIconPolicy = new PhoneStatusBarPolicy(mContext, mIconController);
        mSignalPolicy = new StatusBarSignalPolicy(mContext, mIconController);

        mUnlockMethodCache = UnlockMethodCache.getInstance(mContext);
        mUnlockMethodCache.addListener(this);
        startKeyguard();

        mKeyguardUpdateMonitor.registerCallback(mUpdateCallback);
        putComponent(DozeHost.class, mDozeServiceHost);

        mScreenPinningRequest = new ScreenPinningRequest(mContext);

        Dependency.get(ActivityStarterDelegate.class).setActivityStarterImpl(this);

        Dependency.get(ConfigurationController.class).addCallback(this);

        // set the initial view visibility
        Dependency.get(InitController.class).addPostInitTask(this::updateAreThereNotifications);
        int disabledFlags1 = result.mDisabledFlags1;
        int disabledFlags2 = result.mDisabledFlags2;
        Dependency.get(InitController.class).addPostInitTask(
                () -> setUpDisableFlags(disabledFlags1, disabledFlags2));
        // this will initialize Pulse and begin listening for media events
        mMediaManager.addCallback(Dependency.get(PulseController.class));

        mDefaultHomeIntentFilter = new IntentFilter();
        for (String action : DEFAULT_HOME_CHANGE_ACTIONS) {
            mDefaultHomeIntentFilter.addAction(action);
        }
        ActivityManager.RunningTaskInfo runningTaskInfo =
                ActivityManagerWrapper.getInstance().getRunningTask();
        mRunningTaskId = runningTaskInfo == null ? 0 : runningTaskInfo.taskId;
        mDefaultHome = getCurrentDefaultHome();
        mContext.registerReceiver(mDefaultHomeBroadcastReceiver, mDefaultHomeIntentFilter);
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackChangeListener);
    }

    // ================================================================================
    // Constructing the view
    // ================================================================================
    @ChaosLab(name="GestureAnywhere", classification=Classification.CHANGE_CODE)
    protected void makeStatusBarView(@Nullable RegisterStatusBarResult result) {
        final Context context = mContext;
        mStaticContext = mContext;
        updateDisplaySize(); // populates mDisplayMetrics
        updateResources();
        updateTheme();

        inflateStatusBarWindow(context);
        mStatusBarWindow.setService(this);
        mStatusBarWindow.setBypassController(mKeyguardBypassController);
        mStatusBarWindow.setOnTouchListener(getStatusBarWindowTouchListener());
        mDismissAllButton = mStatusBarWindow.findViewById(R.id.clear_notifications);

        mMinBrightness = context.getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessDim);

        // TODO: Deal with the ugliness that comes from having some of the statusbar broken out
        // into fragments, but the rest here, it leaves some awkward lifecycle and whatnot.
        mNotificationPanel = mStatusBarWindow.findViewById(R.id.notification_panel);
        mStaticNotificationPanel = mNotificationPanel;
        mStackScroller = mStatusBarWindow.findViewById(R.id.notification_stack_scroller);
        mZenController.addCallback(this);
        mQSBlurView = mStatusBarWindow.findViewById(R.id.qs_blur);
        NotificationListContainer notifListContainer = (NotificationListContainer) mStackScroller;
        mNotificationLogger.setUpWithContainer(notifListContainer);

        mNotificationIconAreaController = SystemUIFactory.getInstance()
                .createNotificationIconAreaController(context, this,
                        mWakeUpCoordinator, mKeyguardBypassController,
                        mStatusBarStateController);
        mWakeUpCoordinator.setIconAreaController(mNotificationIconAreaController);
        inflateShelf();
        mNotificationIconAreaController.setupShelf(mNotificationShelf);
        mNotificationPanel.setOnReinflationListener(mNotificationIconAreaController::initAodIcons);
        mNotificationPanel.addExpansionListener(mWakeUpCoordinator);

        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mNotificationIconAreaController);
        // Allow plugins to reference DarkIconDispatcher and StatusBarStateController
        Dependency.get(PluginDependencyProvider.class)
                .allowPluginDependency(DarkIconDispatcher.class);
        Dependency.get(PluginDependencyProvider.class)
                .allowPluginDependency(StatusBarStateController.class);
        FragmentHostManager.get(mStatusBarWindow)
                .addTagListener(CollapsedStatusBarFragment.TAG, (tag, fragment) -> {
                    CollapsedStatusBarFragment statusBarFragment =
                            (CollapsedStatusBarFragment) fragment;
                    mCollapsedStatusBarFragment = (CollapsedStatusBarFragment) statusBarFragment;
                    statusBarFragment.initNotificationIconArea(mNotificationIconAreaController);
                    PhoneStatusBarView oldStatusBarView = mStatusBarView;
                    mStatusBarView = (PhoneStatusBarView) fragment.getView();
                    mStatusBarView.setBar(this);
                    mStatusBarView.setPanel(mNotificationPanel);
                    mStatusBarView.setScrimController(mScrimController);

                    // CollapsedStatusBarFragment re-inflated PhoneStatusBarView and both of
                    // mStatusBarView.mExpanded and mStatusBarView.mBouncerShowing are false.
                    // PhoneStatusBarView's new instance will set to be gone in
                    // PanelBar.updateVisibility after calling mStatusBarView.setBouncerShowing
                    // that will trigger PanelBar.updateVisibility. If there is a heads up showing,
                    // it needs to notify PhoneStatusBarView's new instance to update the correct
                    // status by calling mNotificationPanel.notifyBarPanelExpansionChanged().
                    if (mHeadsUpManager.hasPinnedHeadsUp()) {
                        mNotificationPanel.notifyBarPanelExpansionChanged();
                    }
                    mStatusBarView.setBouncerShowing(mBouncerShowing);
                    if (oldStatusBarView != null) {
                        float fraction = oldStatusBarView.getExpansionFraction();
                        boolean expanded = oldStatusBarView.isExpanded();
                        mStatusBarView.panelExpansionChanged(fraction, expanded);
                    }

                    HeadsUpAppearanceController oldController = mHeadsUpAppearanceController;
                    if (mHeadsUpAppearanceController != null) {
                        // This view is being recreated, let's destroy the old one
                        mHeadsUpAppearanceController.destroy();
                    }
                    mHeadsUpAppearanceController = new HeadsUpAppearanceController(
                            mNotificationIconAreaController, mHeadsUpManager, mStatusBarWindow,
                            mStatusBarStateController, mKeyguardBypassController,
                            mWakeUpCoordinator);
                    mHeadsUpAppearanceController.readFrom(oldController);
                    mStatusBarWindow.setStatusBarView(mStatusBarView);
                    updateAreThereNotifications();
                    checkBarModes();
                    mStatusBarContent = (LinearLayout) mStatusBarView.findViewById(R.id.status_bar_contents);
                    mCenterClockLayout = mStatusBarView.findViewById(R.id.center_clock_layout);
                    mBurnInProtectionController =
                        new BurnInProtectionController(mContext, this, mStatusBarView);
                    handleCutout();
                }).getFragmentManager()
                .beginTransaction()
                .replace(R.id.status_bar_container, new CollapsedStatusBarFragment(),
                        CollapsedStatusBarFragment.TAG)
                .commit();
        mIconController = Dependency.get(StatusBarIconController.class);

        mHeadsUpManager.setUp(mStatusBarWindow, mGroupManager, this, mVisualStabilityManager);
        Dependency.get(ConfigurationController.class).addCallback(mHeadsUpManager);
        mHeadsUpManager.addListener(this);
        mHeadsUpManager.addListener(mNotificationPanel);
        mHeadsUpManager.addListener(mGroupManager);
        mHeadsUpManager.addListener(mGroupAlertTransferHelper);
        mHeadsUpManager.addListener(mVisualStabilityManager);
        mNotificationPanel.setHeadsUpManager(mHeadsUpManager);
        mGroupManager.setHeadsUpManager(mHeadsUpManager);
        mGroupAlertTransferHelper.setHeadsUpManager(mHeadsUpManager);
        mNotificationLogger.setHeadsUpManager(mHeadsUpManager);
        putComponent(HeadsUpManager.class, mHeadsUpManager);
        updateNavigationBar(result, true);
        addGestureAnywhereView();

        if (ENABLE_LOCKSCREEN_WALLPAPER && mWallpaperSupported) {
            mLockscreenWallpaper = new LockscreenWallpaper(mContext, this, mHandler);
        }

        mKeyguardIndicationController =
                SystemUIFactory.getInstance().createKeyguardIndicationController(mContext,
                        mStatusBarWindow.findViewById(R.id.keyguard_indication_area),
                        mStatusBarWindow.findViewById(R.id.lock_icon));
        mNotificationPanel.setKeyguardIndicationController(mKeyguardIndicationController);

        mAmbientIndicationContainer = mStatusBarWindow.findViewById(
                R.id.ambient_indication_container);

        // TODO: Find better place for this callback.
        if (mBatteryController != null) {
            mBatteryController.addCallback(new BatteryStateChangeCallback() {
                @Override
                public void onPowerSaveChanged(boolean isPowerSave) {
                    mHandler.post(mCheckBarModes);
                    if (mDozeServiceHost != null && mPowerSave != isPowerSave) {
                        mDozeServiceHost.firePowerSaveChanged(isPowerSave);
                        mPowerSave = isPowerSave;
                    }
                }

                @Override
                public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
                    // noop
                }
            });
        }

        mAutoHideController = Dependency.get(AutoHideController.class);
        mAutoHideController.setStatusBar(this);

        mLightBarController = Dependency.get(LightBarController.class);

        ScrimView scrimBehind = mStatusBarWindow.findViewById(R.id.scrim_behind);
        ScrimView scrimInFront = mStatusBarWindow.findViewById(R.id.scrim_in_front);
        mScrimController = SystemUIFactory.getInstance().createScrimController(
                scrimBehind, scrimInFront, mLockscreenWallpaper,
                (state, alpha, color) -> mLightBarController.setScrimState(state, alpha, color),
                scrimsVisible -> {
                    if (mStatusBarWindowController != null) {
                        mStatusBarWindowController.setScrimsVisibility(scrimsVisible);
                    }
                    if (mStatusBarWindow != null) {
                        mStatusBarWindow.onScrimVisibilityChanged(scrimsVisible);
                    }
                }, DozeParameters.getInstance(mContext),
                mContext.getSystemService(AlarmManager.class),
                mKeyguardMonitor);
        mNotificationPanel.initDependencies(this, mGroupManager, mNotificationShelf,
                mHeadsUpManager, mNotificationIconAreaController, mScrimController);
        mDozeScrimController = new DozeScrimController(DozeParameters.getInstance(context));

        BackDropView backdrop = mStatusBarWindow.findViewById(R.id.backdrop);
        mMediaManager.setup(backdrop, backdrop.findViewById(R.id.backdrop_front),
                backdrop.findViewById(R.id.backdrop_back), mScrimController, mLockscreenWallpaper);

        mVisualizerView = (VisualizerView) mStatusBarWindow.findViewById(R.id.visualizerview);

        // Other icons
        mVolumeComponent = getComponent(VolumeComponent.class);
        mVolumeComponent.initDependencies(mMediaManager);

        mNotificationPanel.setUserSetupComplete(mUserSetup);
        if (UserManager.get(mContext).isUserSwitcherEnabled()) {
            createUserSwitcher();
        }

        mNotificationPanel.setLaunchAffordanceListener(
                mStatusBarWindow::onShowingLaunchAffordanceChanged);

        // Set up the quick settings tile panel
        setUpQuickSettingsTilePanel();

        mReportRejectedTouch = mStatusBarWindow.findViewById(R.id.report_rejected_touch);
        if (mReportRejectedTouch != null) {
            updateReportRejectedTouchVisibility();
            mReportRejectedTouch.setOnClickListener(v -> {
                Uri session = mFalsingManager.reportRejectedTouch();
                if (session == null) { return; }

                StringWriter message = new StringWriter();
                message.write("Build info: ");
                message.write(SystemProperties.get("ro.build.description"));
                message.write("\nSerial number: ");
                message.write(SystemProperties.get("ro.serialno"));
                message.write("\n");

                PrintWriter falsingPw = new PrintWriter(message);
                FalsingLog.dump(falsingPw);
                falsingPw.flush();

                startActivityDismissingKeyguard(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                .setType("*/*")
                                .putExtra(Intent.EXTRA_SUBJECT, "Rejected touch report")
                                .putExtra(Intent.EXTRA_STREAM, session)
                                .putExtra(Intent.EXTRA_TEXT, message.toString()),
                        "Share rejected touch report")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        true /* onlyProvisioned */, true /* dismissShade */);
            });
        }

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (!pm.isScreenOn()) {
            mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));
        }
        mGestureWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                "GestureWakeLock");
        mVibrator = mContext.getSystemService(Vibrator.class);
        int[] pattern = mContext.getResources().getIntArray(
                R.array.config_cameraLaunchGestureVibePattern);
        mCameraLaunchGestureVibePattern = new long[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            mCameraLaunchGestureVibePattern[i] = pattern[i];
        }

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG);
        filter.addAction(lineageos.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
        filter.addAction("com.android.systemui.ACTION_DISMISS_KEYGUARD");
        context.registerReceiverAsUser(mBroadcastReceiver, UserHandle.ALL, filter, null, null);

        IntentFilter demoFilter = new IntentFilter();
        if (DEBUG_MEDIA_FAKE_ARTWORK) {
            demoFilter.addAction(ACTION_FAKE_ARTWORK);
        }
        demoFilter.addAction(ACTION_DEMO);
        context.registerReceiverAsUser(mDemoReceiver, UserHandle.ALL, demoFilter,
                android.Manifest.permission.DUMP, null);

        // listen for USER_SETUP_COMPLETE setting (per-user)
        mDeviceProvisionedController.addCallback(mUserSetupObserver);
        mUserSetupObserver.onUserSetupChanged();

        // disable profiling bars, since they overlap and clutter the output on app windows
        ThreadedRenderer.overrideProperty("disableProfileBars", "true");

        // Private API call to make the shadows look better for Recents
        ThreadedRenderer.overrideProperty("ambientRatio", String.valueOf(1.5f));

        mFlashlightController = Dependency.get(FlashlightController.class);
    }

    public static void setHasClearableNotifications(boolean state) {
        mClearableNotifications = state;
    }

    public static void setDismissAllVisible(boolean visible) {

        if(mClearableNotifications && mState != StatusBarState.KEYGUARD && visible && isDismissAllButtonEnabled()) {
            mDismissAllButton.setVisibility(View.VISIBLE);
            int DismissAllAlpha = Math.round(255.0f * mStaticNotificationPanel.getExpandedFraction());
            mDismissAllButton.setAlpha(DismissAllAlpha);
            mDismissAllButton.getBackground().setAlpha(DismissAllAlpha);
        } else {
            mDismissAllButton.setAlpha(0);
            mDismissAllButton.getBackground().setAlpha(0);
            mDismissAllButton.setVisibility(View.INVISIBLE);
        }
    }

    public static void updateDismissAllButton(int iconcolor, int bgcolor) {
        if (mDismissAllButton != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mDismissAllButton.getLayoutParams();
            layoutParams.width = mStaticContext.getResources().getDimensionPixelSize(R.dimen.dismiss_all_button_width);
            layoutParams.height = mStaticContext.getResources().getDimensionPixelSize(R.dimen.dismiss_all_button_height);
            layoutParams.bottomMargin = mStaticContext.getResources().getDimensionPixelSize(R.dimen.dismiss_all_button_margin_bottom);
            mDismissAllButton.setElevation(mStaticContext.getResources().getDimension(R.dimen.dismiss_all_button_elevation));
            Drawable d = mStaticContext.getResources().getDrawable(R.drawable.oos_dismiss_all_bcg);
            d.setTint(bgcolor);
            mDismissAllButton.setBackground(d);
            mDismissAllButton.setColorFilter(iconcolor);
        }
    }

    public static void updateDismissAllButtonOnlyDimens() {
        if (mDismissAllButton != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mDismissAllButton.getLayoutParams();
            layoutParams.width = mStaticContext.getResources().getDimensionPixelSize(R.dimen.dismiss_all_button_width);
            layoutParams.height = mStaticContext.getResources().getDimensionPixelSize(R.dimen.dismiss_all_button_height);
            layoutParams.bottomMargin = mStaticContext.getResources().getDimensionPixelSize(R.dimen.dismiss_all_button_margin_bottom);
            mDismissAllButton.setElevation(mStaticContext.getResources().getDimension(R.dimen.dismiss_all_button_elevation));
        }
    }

    private static boolean isDismissAllButtonEnabled() {
        return Settings.System.getInt(mStaticContext.getContentResolver(),
                Settings.System.DISMISS_ALL_BUTTON, 0) != 0;
    }

    protected QS createDefaultQSFragment() {
        return FragmentHostManager.get(mStatusBarWindow).create(QSFragment.class);
    }

    private void setUpPresenter() {
        // Set up the initial notification state.
        mActivityLaunchAnimator = new ActivityLaunchAnimator(
                mStatusBarWindow, this, mNotificationPanel,
                (NotificationListContainer) mStackScroller);

        final NotificationRowBinderImpl rowBinder =
                new NotificationRowBinderImpl(
                        mContext,
                        mAllowNotificationLongPress,
                        mKeyguardBypassController,
                        mStatusBarStateController);

        mPresenter = new StatusBarNotificationPresenter(mContext, mNotificationPanel,
                mHeadsUpManager, mStatusBarWindow, mStackScroller, mDozeScrimController,
                mScrimController, mActivityLaunchAnimator, mDynamicPrivacyController,
                mNotificationAlertingManager, rowBinder);
        mPresenter.addCallback(this);

        mNotificationListController =
                new NotificationListController(
                        mEntryManager,
                        (NotificationListContainer) mStackScroller,
                        mForegroundServiceController,
                        mDeviceProvisionedController);

        if (mAppOpsController != null) {
            mAppOpsController.addCallback(APP_OPS, this);
        }
        mNotificationShelf.setOnActivatedListener(mPresenter);
        mRemoteInputManager.getController().addCallback(mStatusBarWindowController);

        final StatusBarRemoteInputCallback mStatusBarRemoteInputCallback =
                (StatusBarRemoteInputCallback) Dependency.get(
                        NotificationRemoteInputManager.Callback.class);
        mShadeController = Dependency.get(ShadeController.class);
        final ActivityStarter activityStarter = Dependency.get(ActivityStarter.class);

        mNotificationActivityStarter = new StatusBarNotificationActivityStarter(mContext,
                mCommandQueue, mAssistManager, mNotificationPanel, mPresenter, mEntryManager,
                mHeadsUpManager, activityStarter, mActivityLaunchAnimator,
                mBarService, mStatusBarStateController, mKeyguardManager, mDreamManager,
                mRemoteInputManager, mStatusBarRemoteInputCallback, mGroupManager,
                mLockscreenUserManager, mShadeController, mKeyguardMonitor,
                mNotificationInterruptionStateProvider, mMetricsLogger,
                new LockPatternUtils(mContext), Dependency.get(MAIN_HANDLER),
                Dependency.get(BG_HANDLER), mActivityIntentHelper, mBubbleController);

        mGutsManager.setNotificationActivityStarter(mNotificationActivityStarter);

        mEntryManager.setRowBinder(rowBinder);
        rowBinder.setNotificationClicker(new NotificationClicker(
                this, Dependency.get(BubbleController.class), mNotificationActivityStarter));

        mGroupAlertTransferHelper.bind(mEntryManager, mGroupManager);
        mNotificationListController.bind();
    }

    protected void getDependencies() {
        // Icons
        mIconController = Dependency.get(StatusBarIconController.class);
        mLightBarController = Dependency.get(LightBarController.class);

        // Keyguard
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mScreenLifecycle = Dependency.get(ScreenLifecycle.class);
        mWakefulnessLifecycle = Dependency.get(WakefulnessLifecycle.class);

        // Notifications
        mEntryManager = Dependency.get(NotificationEntryManager.class);
        mForegroundServiceController = Dependency.get(ForegroundServiceController.class);
        mGroupAlertTransferHelper = Dependency.get(NotificationGroupAlertTransferHelper.class);
        mGroupManager = Dependency.get(NotificationGroupManager.class);
        mGutsManager = Dependency.get(NotificationGutsManager.class);
        mLockscreenUserManager = Dependency.get(NotificationLockscreenUserManager.class);
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mMediaManager.addCallback(this);
        mNotificationInterruptionStateProvider =
                Dependency.get(NotificationInterruptionStateProvider.class);
        mNotificationListener = Dependency.get(NotificationListener.class);
        mNotificationLogger = Dependency.get(NotificationLogger.class);
        mRemoteInputManager = Dependency.get(NotificationRemoteInputManager.class);
        mViewHierarchyManager = Dependency.get(NotificationViewHierarchyManager.class);
        mVisualStabilityManager = Dependency.get(VisualStabilityManager.class);

        // Policy
        mBatteryController = Dependency.get(BatteryController.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mZenController = Dependency.get(ZenModeController.class);

        // Others
        mAppOpsController = Dependency.get(AppOpsController.class);
        mAssistManager = Dependency.get(AssistManager.class);
        mBubbleController = Dependency.get(BubbleController.class);
        mColorExtractor = Dependency.get(SysuiColorExtractor.class);
        mNavigationBarController = Dependency.get(NavigationBarController.class);
        mUserSwitcherController = Dependency.get(UserSwitcherController.class);
        mVibratorHelper = Dependency.get(VibratorHelper.class);
    }

    protected void setUpQuickSettingsTilePanel() {
        View container = mStatusBarWindow.findViewById(R.id.qs_frame);
        if (container != null) {
            FragmentHostManager fragmentHostManager = FragmentHostManager.get(container);
            ExtensionFragmentListener.attachExtensonToFragment(container, QS.TAG, R.id.qs_frame,
                    Dependency.get(ExtensionController.class)
                            .newExtension(QS.class)
                            .withPlugin(QS.class)
                            .withDefault(this::createDefaultQSFragment)
                            .build());
            fragmentHostManager.addTagListener(QS.TAG, (tag, f) -> {
                QS qs = (QS) f;
                if (qs instanceof QSFragment) {
                    mQSPanel = ((QSFragment) qs).getQsPanel();
                }
            });
        }
    }


    /**
     * Post-init task of {@link #start()}
     * @param state1 disable1 flags
     * @param state2 disable2 flags
     */
    protected void setUpDisableFlags(int state1, int state2) {
        mCommandQueue.disable(mDisplayId, state1, state2, false /* animate */);
    }

    @Override
    public void addAfterKeyguardGoneRunnable(Runnable runnable) {
        mStatusBarKeyguardViewManager.addAfterKeyguardGoneRunnable(runnable);
    }

    @Override
    public boolean isDozing() {
        return mDozing;
    }

    @Override
    public void wakeUpIfDozing(long time, View where, String why) {
        if (mDozing) {
            PowerManager pm = mContext.getSystemService(PowerManager.class);
            pm.wakeUp(time, PowerManager.WAKE_REASON_GESTURE, "com.android.systemui:" + why);
            mWakeUpComingFromTouch = true;
            where.getLocationInWindow(mTmpInt2);
            mWakeUpTouchLocation = new PointF(mTmpInt2[0] + where.getWidth() / 2,
                    mTmpInt2[1] + where.getHeight() / 2);
            mFalsingManager.onScreenOnFromTouch();
        }
    }

    // TODO(b/117478341): This was left such that CarStatusBar can override this method.
    // Try to remove this.
    protected void createNavigationBar(@Nullable RegisterStatusBarResult result) {
        mNavigationBarController.createNavigationBars(true /* includeDefaultDisplay */, result);
    }

    /**
     * Returns the {@link android.view.View.OnTouchListener} that will be invoked when the
     * background window of the status bar is clicked.
     */
    protected View.OnTouchListener getStatusBarWindowTouchListener() {
        return (v, event) -> {
            mAutoHideController.checkUserAutoHide(event);
            mRemoteInputManager.checkRemoteInputOutside(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mExpandedVisible) {
                    animateCollapsePanels();
                }
            }
            return mStatusBarWindow.onTouchEvent(event);
        };
    }

    private void inflateShelf() {
        mNotificationShelf =
                (NotificationShelf) mInjectionInflater.injectable(
                        LayoutInflater.from(mContext)).inflate(
                                R.layout.status_bar_notification_shelf, mStackScroller, false);
        mNotificationShelf.setOnClickListener(mGoToLockedShadeListener);
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        // TODO: Bring these out of StatusBar.
        ((UserInfoControllerImpl) Dependency.get(UserInfoController.class))
                .onDensityOrFontScaleChanged();
        Dependency.get(UserSwitcherController.class).onDensityOrFontScaleChanged();
        if (mKeyguardUserSwitcher != null) {
            mKeyguardUserSwitcher.onDensityOrFontScaleChanged();
        }
        mNotificationIconAreaController.onDensityOrFontScaleChanged(mContext);
        mHeadsUpManager.onDensityOrFontScaleChanged();
    }

    @Override
    public void onThemeChanged() {
        if (mStatusBarKeyguardViewManager != null) {
            mStatusBarKeyguardViewManager.onThemeChanged();
        }
        if (mAmbientIndicationContainer instanceof AutoReinflateContainer) {
            ((AutoReinflateContainer) mAmbientIndicationContainer).inflateLayout();
        }
        mNotificationIconAreaController.onThemeChanged();
    }

    @Override
    public void onOverlayChanged() {
        // We need the new R.id.keyguard_indication_area before recreating
        // mKeyguardIndicationController
        mNotificationPanel.onThemeChanged();
        onThemeChanged();
    }

    @Override
    public void onUiModeChanged() {
        updateTheme();
    }

    protected void createUserSwitcher() {
        mKeyguardUserSwitcher = new KeyguardUserSwitcher(mContext,
                mStatusBarWindow.findViewById(R.id.keyguard_user_switcher),
                mStatusBarWindow.findViewById(R.id.keyguard_header),
                mNotificationPanel);
    }

    protected void inflateStatusBarWindow(Context context) {
        mStatusBarWindow = (StatusBarWindowView) mInjectionInflater.injectable(
                LayoutInflater.from(context)).inflate(R.layout.super_status_bar, null);
    }

    protected void startKeyguard() {
        Trace.beginSection("StatusBar#startKeyguard");
        KeyguardViewMediator keyguardViewMediator = getComponent(KeyguardViewMediator.class);
        mBiometricUnlockController = new BiometricUnlockController(mContext,
                mDozeScrimController, keyguardViewMediator,
                mScrimController, this, UnlockMethodCache.getInstance(mContext),
                new Handler(), mKeyguardUpdateMonitor, mKeyguardBypassController);
        putComponent(BiometricUnlockController.class, mBiometricUnlockController);
        mStatusBarKeyguardViewManager = keyguardViewMediator.registerStatusBar(this,
                getBouncerContainer(), mNotificationPanel, mBiometricUnlockController,
                mStatusBarWindow.findViewById(R.id.lock_icon_container), mStackScroller,
                mKeyguardBypassController, mFalsingManager);
        mKeyguardIndicationController
                .setStatusBarKeyguardViewManager(mStatusBarKeyguardViewManager);
        mBiometricUnlockController.setStatusBarKeyguardViewManager(mStatusBarKeyguardViewManager);
        mRemoteInputManager.getController().addCallback(mStatusBarKeyguardViewManager);
        mDynamicPrivacyController.setStatusBarKeyguardViewManager(mStatusBarKeyguardViewManager);

        mKeyguardViewMediatorCallback = keyguardViewMediator.getViewMediatorCallback();
        mLightBarController.setBiometricUnlockController(mBiometricUnlockController);
        mMediaManager.setBiometricUnlockController(mBiometricUnlockController);
        Dependency.get(KeyguardDismissUtil.class).setDismissHandler(this::executeWhenUnlocked);
        Trace.endSection();
    }

    protected View getStatusBarView() {
        return mStatusBarView;
    }

    public StatusBarWindowView getStatusBarWindow() {
        return mStatusBarWindow;
    }

    protected ViewGroup getBouncerContainer() {
        return mStatusBarWindow;
    }

    public void createTicker(int tickerMode, Context ctx, View statusBarView,
                             TickerView tickerTextView, ImageSwitcher tickerIcon, View tickerView) {
        mTickerEnabled = tickerMode;
        if (mTicker == null) {
            mTicker = new MyTicker(ctx, statusBarView);
        }
        ((MyTicker)mTicker).setView(tickerView);
        tickerTextView.setTicker(mTicker);
        mTicker.setViews(tickerTextView, tickerIcon);
     }

    public void disableTicker() {
        mTickerEnabled = 0;
     }

    public int getStatusBarHeight() {
        if (mNaturalBarHeight < 0) {
            final Resources res = mContext.getResources();
            mNaturalBarHeight =
                    res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
        }
        return mNaturalBarHeight;
    }

    protected boolean toggleSplitScreenMode(int metricsDockAction, int metricsUndockAction) {
        if (mRecents == null) {
            return false;
        }
        int dockSide = WindowManagerProxy.getInstance().getDockSide();
        if (dockSide == WindowManager.DOCKED_INVALID) {
            final int navbarPos = WindowManagerWrapper.getInstance().getNavBarPosition(mDisplayId);
            if (navbarPos == NAV_BAR_POS_INVALID) {
                return false;
            }
            int createMode = navbarPos == NAV_BAR_POS_LEFT
                    ? SPLIT_SCREEN_CREATE_MODE_BOTTOM_OR_RIGHT
                    : SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT;
            return mRecents.splitPrimaryTask(createMode, null, metricsDockAction);
        } else {
            Divider divider = getComponent(Divider.class);
            if (divider != null) {
                if (divider.isMinimized() && !divider.isHomeStackResizable()) {
                    // Undocking from the minimized state is not supported
                    return false;
                } else {
                    divider.onUndockingTask();
                    if (metricsUndockAction != -1) {
                        mMetricsLogger.action(metricsUndockAction);
                    }
                }
            }
        }
        return true;
    }

    private final BroadcastReceiver mDefaultHomeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mDefaultHome = getCurrentDefaultHome();
        }
    };

    private final TaskStackChangeListener mTaskStackChangeListener =
            new TaskStackChangeListener() {
                @Override
                public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
                    handleTaskStackTopChanged(taskInfo.taskId, taskInfo.topActivity);
                }

                @Override
                public void onTaskCreated(int taskId, ComponentName componentName) {
                    handleTaskStackTopChanged(taskId, componentName);
                }
            };

    private void handleTaskStackTopChanged(int taskId, @Nullable ComponentName taskComponentName) {
        if (mRunningTaskId == taskId || taskComponentName == null) {
            return;
        }
        mRunningTaskId = taskId;
        mIsLauncherShowing = taskComponentName.equals(mDefaultHome);
        mTaskComponentName = taskComponentName;
        if (mMediaManager != null) {
            mMediaManager.setRunningPackage(mTaskComponentName.getPackageName());
        }
    }

    @Nullable
    private ComponentName getCurrentDefaultHome() {
        List<ResolveInfo> homeActivities = new ArrayList<>();
        ComponentName defaultHome = PackageManagerWrapper.getInstance().getHomeActivities(homeActivities);
        if (defaultHome != null) {
            return defaultHome;
        }

        int topPriority = Integer.MIN_VALUE;
        ComponentName topComponent = null;
        for (ResolveInfo resolveInfo : homeActivities) {
            if (resolveInfo.priority > topPriority) {
                topComponent = resolveInfo.activityInfo.getComponentName();
                topPriority = resolveInfo.priority;
            } else if (resolveInfo.priority == topPriority) {
                topComponent = null;
            }
        }
        return topComponent;
    }

    /**
     * Disable QS if device not provisioned.
     * If the user switcher is simple then disable QS during setup because
     * the user intends to use the lock screen user switcher, QS in not needed.
     */
    public void updateQsExpansionEnabled() {
        final boolean expandEnabled = mDeviceProvisionedController.isDeviceProvisioned()
                && (mUserSetup || mUserSwitcherController == null
                        || !mUserSwitcherController.isSimpleUserSwitcher())
                && ((mDisabled2 & StatusBarManager.DISABLE2_NOTIFICATION_SHADE) == 0)
                && ((mDisabled2 & StatusBarManager.DISABLE2_QUICK_SETTINGS) == 0)
                && !mDozing
                && !ONLY_CORE_APPS;
        mNotificationPanel.setQsExpansionEnabled(expandEnabled);
        Log.d(TAG, "updateQsExpansionEnabled - QS Expand enabled: " + expandEnabled);
    }

    public void addQsTile(ComponentName tile) {
        if (mQSPanel != null && mQSPanel.getHost() != null) {
            mQSPanel.getHost().addTile(tile);
        }
    }

    public void remQsTile(ComponentName tile) {
        if (mQSPanel != null && mQSPanel.getHost() != null) {
            mQSPanel.getHost().removeTile(tile);
        }
    }

    public void clickTile(ComponentName tile) {
        mQSPanel.clickTile(tile);
    }

    public boolean areNotificationsHidden() {
        return mZenController.areNotificationsHiddenInShade();
    }

    public void requestNotificationUpdate() {
        mEntryManager.updateNotifications();
    }

    protected boolean hasActiveVisibleNotifications() {
        return mEntryManager.getNotificationData().hasActiveVisibleNotifications();
    }

    protected boolean hasActiveOngoingNotifications() {
        return mEntryManager.getNotificationData().hasActiveOngoingNotifications();
    }

    /**
     * Asks {@link KeyguardUpdateMonitor} to run face auth.
     */
    public void requestFaceAuth() {
        if (!mUnlockMethodCache.canSkipBouncer()) {
            mKeyguardUpdateMonitor.requestFaceAuth();
        }
    }

    public void updateAreThereNotifications() {
        if (mNotificationPanel.hasActiveClearableNotifications()) {
            mClearableNotifications = true;
        } else {
            mClearableNotifications = false;
        }
        if (SPEW) {
            final boolean clearable = hasActiveNotifications() &&
                    mNotificationPanel.hasActiveClearableNotifications();
            Log.d(TAG, "updateAreThereNotifications: N=" +
                    mEntryManager.getNotificationData().getActiveNotifications().size() + " any=" +
                    hasActiveNotifications() + " clearable=" + clearable);
        }

        if (mStatusBarView != null) {
            final View nlo = mStatusBarView.findViewById(R.id.notification_lights_out);
            final boolean showDot = hasActiveNotifications() && !areLightsOn();
            if (showDot != (nlo.getAlpha() == 1.0f)) {
                if (showDot) {
                    nlo.setAlpha(0f);
                    nlo.setVisibility(View.VISIBLE);
                }
                nlo.animate()
                        .alpha(showDot ? 1 : 0)
                        .setDuration(showDot ? 750 : 250)
                        .setInterpolator(new AccelerateInterpolator(2.0f))
                        .setListener(showDot ? null : new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator _a) {
                                nlo.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }
        mMediaManager.findAndUpdateMediaNotifications();
    }

    private void updateReportRejectedTouchVisibility() {
        if (mReportRejectedTouch == null) {
            return;
        }
        mReportRejectedTouch.setVisibility(mState == StatusBarState.KEYGUARD && !mDozing
                && mFalsingManager.isReportingEnabled() ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * State is one or more of the DISABLE constants from StatusBarManager.
     */
    @Override
    public void disable(int displayId, int state1, int state2, boolean animate) {
        if (displayId != mDisplayId) {
            return;
        }
        state2 = mRemoteInputQuickSettingsDisabler.adjustDisableFlags(state2);

        animate &= mStatusBarWindowState != WINDOW_STATE_HIDDEN;
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;

        final int old2 = mDisabled2;
        final int diff2 = state2 ^ old2;
        mDisabled2 = state2;

        if (DEBUG) {
            Log.d(TAG, String.format("disable1: 0x%08x -> 0x%08x (diff1: 0x%08x)",
                old1, state1, diff1));
            Log.d(TAG, String.format("disable2: 0x%08x -> 0x%08x (diff2: 0x%08x)",
                old2, state2, diff2));
        }

        StringBuilder flagdbg = new StringBuilder();
        flagdbg.append("disable<");
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_EXPAND))                ? 'E' : 'e');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_EXPAND))                ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_NOTIFICATION_ICONS))    ? 'I' : 'i');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_NOTIFICATION_ICONS))    ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_NOTIFICATION_ALERTS))   ? 'A' : 'a');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_NOTIFICATION_ALERTS))   ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_SYSTEM_INFO))           ? 'S' : 's');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_SYSTEM_INFO))           ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_BACK))                  ? 'B' : 'b');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_BACK))                  ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_HOME))                  ? 'H' : 'h');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_HOME))                  ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_RECENT))                ? 'R' : 'r');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_RECENT))                ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_CLOCK))                 ? 'C' : 'c');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_CLOCK))                 ? '!' : ' ');
        flagdbg.append(0 != ((state1 & StatusBarManager.DISABLE_SEARCH))                ? 'S' : 's');
        flagdbg.append(0 != ((diff1  & StatusBarManager.DISABLE_SEARCH))                ? '!' : ' ');
        flagdbg.append("> disable2<");
        flagdbg.append(0 != ((state2 & StatusBarManager.DISABLE2_QUICK_SETTINGS))       ? 'Q' : 'q');
        flagdbg.append(0 != ((diff2  & StatusBarManager.DISABLE2_QUICK_SETTINGS))       ? '!' : ' ');
        flagdbg.append(0 != ((state2 & StatusBarManager.DISABLE2_SYSTEM_ICONS))         ? 'I' : 'i');
        flagdbg.append(0 != ((diff2  & StatusBarManager.DISABLE2_SYSTEM_ICONS))         ? '!' : ' ');
        flagdbg.append(0 != ((state2 & StatusBarManager.DISABLE2_NOTIFICATION_SHADE))   ? 'N' : 'n');
        flagdbg.append(0 != ((diff2  & StatusBarManager.DISABLE2_NOTIFICATION_SHADE))   ? '!' : ' ');
        flagdbg.append('>');
        Log.d(TAG, flagdbg.toString());

        if ((diff1 & StatusBarManager.DISABLE_EXPAND) != 0) {
            if ((state1 & StatusBarManager.DISABLE_EXPAND) != 0) {
                animateCollapsePanels();
            }
        }

        if ((diff1 & StatusBarManager.DISABLE_RECENT) != 0) {
            if ((state1 & StatusBarManager.DISABLE_RECENT) != 0) {
                // close recents if it's visible
                mHandler.removeMessages(MSG_HIDE_RECENT_APPS);
                mHandler.sendEmptyMessage(MSG_HIDE_RECENT_APPS);
            }
        }

        if ((diff1 & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0) {
            mNotificationInterruptionStateProvider.setDisableNotificationAlerts(
                    (state1 & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0);
        }

         if ((diff1 & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0
                && (state1 & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0
                && mTicking) {
            haltTicker();
        }

        if ((diff2 & StatusBarManager.DISABLE2_QUICK_SETTINGS) != 0) {
            updateQsExpansionEnabled();
        }

        if ((diff2 & StatusBarManager.DISABLE2_NOTIFICATION_SHADE) != 0) {
            updateQsExpansionEnabled();
            if ((state1 & StatusBarManager.DISABLE2_NOTIFICATION_SHADE) != 0) {
                animateCollapsePanels();
            }
        }
    }

    protected H createHandler() {
        return new StatusBar.H();
    }

    @Override
    public void startActivity(Intent intent, boolean onlyProvisioned, boolean dismissShade,
            int flags) {
        startActivityDismissingKeyguard(intent, onlyProvisioned, dismissShade, flags);
    }

    @Override
    public void startActivity(Intent intent, boolean dismissShade) {
        startActivityDismissingKeyguard(intent, false, dismissShade);
    }

    @Override
    public void startActivity(Intent intent, boolean onlyProvisioned, boolean dismissShade) {
        startActivityDismissingKeyguard(intent, onlyProvisioned, dismissShade);
    }

    @Override
    public void startActivity(Intent intent, boolean dismissShade, Callback callback) {
        startActivityDismissingKeyguard(intent, false, dismissShade,
                false /* disallowEnterPictureInPictureWhileLaunching */, callback, 0);
    }

    public void setQsExpanded(boolean expanded) {
        mStatusBarWindowController.setQsExpanded(expanded);
        mNotificationPanel.setStatusAccessibilityImportance(expanded
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (getNavigationBarView() != null) {
            getNavigationBarView().onStatusBarPanelStateChanged();
        }
    }

    public boolean isWakeUpComingFromTouch() {
        return mWakeUpComingFromTouch;
    }

    public boolean isFalsingThresholdNeeded() {
        return mStatusBarStateController.getState() == StatusBarState.KEYGUARD;
    }

    /**
     * To be called when there's a state change in StatusBarKeyguardViewManager.
     */
    public void onKeyguardViewManagerStatesUpdated() {
        logStateToEventlog();
    }

    @Override  // UnlockMethodCache.OnUnlockMethodChangedListener
    public void onUnlockMethodStateChanged() {
        // Unlock method state changed. Notify KeguardMonitor
        updateKeyguardState();
        logStateToEventlog();
    }

    @Override
    public void onHeadsUpPinnedModeChanged(boolean inPinnedMode) {
        if (inPinnedMode) {
            mStatusBarWindowController.setHeadsUpShowing(true);
            mStatusBarWindowController.setForceStatusBarVisible(true);
            if (mNotificationPanel.isFullyCollapsed()) {
                // We need to ensure that the touchable region is updated before the window will be
                // resized, in order to not catch any touches. A layout will ensure that
                // onComputeInternalInsets will be called and after that we can resize the layout. Let's
                // make sure that the window stays small for one frame until the touchableRegion is set.
                mNotificationPanel.requestLayout();
                mStatusBarWindowController.setForceWindowCollapsed(true);
                mNotificationPanel.post(() -> {
                    mStatusBarWindowController.setForceWindowCollapsed(false);
                });
            }
        } else {
            boolean bypassKeyguard = mKeyguardBypassController.getBypassEnabled()
                    && mState == StatusBarState.KEYGUARD;
            if (!mNotificationPanel.isFullyCollapsed() || mNotificationPanel.isTracking()
                    || bypassKeyguard) {
                // We are currently tracking or is open and the shade doesn't need to be kept
                // open artificially.
                mStatusBarWindowController.setHeadsUpShowing(false);
                if (bypassKeyguard) {
                    mStatusBarWindowController.setForceStatusBarVisible(false);
                }
            } else {
                // we need to keep the panel open artificially, let's wait until the animation
                // is finished.
                mHeadsUpManager.setHeadsUpGoingAway(true);
                mNotificationPanel.runAfterAnimationFinished(() -> {
                    if (!mHeadsUpManager.hasPinnedHeadsUp()) {
                        mStatusBarWindowController.setHeadsUpShowing(false);
                        mHeadsUpManager.setHeadsUpGoingAway(false);
                    }
                    mRemoteInputManager.onPanelCollapsed();
                });
            }
        }
    }

    @Override
    public void onHeadsUpStateChanged(NotificationEntry entry, boolean isHeadsUp) {
        mEntryManager.updateNotifications();
        if (isDozing() && isHeadsUp) {
            entry.setPulseSuppressed(false);
            mDozeServiceHost.fireNotificationPulse(entry);
            if (mPulsing) {
                mDozeScrimController.cancelPendingPulseTimeout();
            }
        }
        if (!isHeadsUp && !mHeadsUpManager.hasNotifications()) {
            // There are no longer any notifications to show.  We should end the pulse now.
            mDozeScrimController.pulseOutNow();
        }
    }

    public boolean isKeyguardCurrentlySecure() {
        return !mUnlockMethodCache.canSkipBouncer();
    }

    public void setPanelExpanded(boolean isExpanded) {
        mPanelExpanded = isExpanded;
        updateHideIconsForBouncer(false /* animate */);
        mStatusBarWindowController.setPanelExpanded(isExpanded);
        mVisualStabilityManager.setPanelExpanded(isExpanded);
        if (isExpanded && mStatusBarStateController.getState() != StatusBarState.KEYGUARD) {
            if (DEBUG) {
                Log.v(TAG, "clearing notification effects from setExpandedHeight");
            }
            clearNotificationEffects();
        }

        if (!isExpanded) {
            mRemoteInputManager.onPanelCollapsed();
        }
    }

    public ViewGroup getNotificationScrollLayout() {
        return mStackScroller;
    }

    public boolean isPulsing() {
        return mPulsing;
    }

    public boolean hideStatusBarIconsWhenExpanded() {
        return mNotificationPanel.hideStatusBarIconsWhenExpanded();
    }

    @Override
    public void onColorsChanged(ColorExtractor extractor, int which) {
        updateTheme();
    }

    @Nullable
    public View getAmbientIndicationContainer() {
        return mAmbientIndicationContainer;
    }

    @Override
    public boolean isOccluded() {
        return mIsOccluded;
    }

    public void setOccluded(boolean occluded) {
        mIsOccluded = occluded;
        mScrimController.setKeyguardOccluded(occluded);
        updateHideIconsForBouncer(false /* animate */);
    }

    public boolean hideStatusBarIconsForBouncer() {
        return mHideIconsForBouncer || mWereIconsJustHidden;
    }

    /**
     * Decides if the status bar (clock + notifications + signal cluster) should be visible
     * or not when showing the bouncer.
     *
     * We want to hide it when:
     * • User swipes up on the keyguard
     * • Locked activity that doesn't show a status bar requests the bouncer
     *
     * @param animate should the change of the icons be animated.
     */
    private void updateHideIconsForBouncer(boolean animate) {
        boolean hideBecauseApp = mTopHidesStatusBar && mIsOccluded
                && (mStatusBarWindowHidden || mBouncerShowing);
        boolean hideBecauseKeyguard = !mPanelExpanded && !mIsOccluded && mBouncerShowing;
        boolean shouldHideIconsForBouncer = hideBecauseApp || hideBecauseKeyguard;
        if (mHideIconsForBouncer != shouldHideIconsForBouncer) {
            mHideIconsForBouncer = shouldHideIconsForBouncer;
            if (!shouldHideIconsForBouncer && mBouncerWasShowingWhenHidden) {
                // We're delaying the showing, since most of the time the fullscreen app will
                // hide the icons again and we don't want them to fade in and out immediately again.
                mWereIconsJustHidden = true;
                mHandler.postDelayed(() -> {
                    mWereIconsJustHidden = false;
                    mCommandQueue.recomputeDisableFlags(mDisplayId, true);
                }, 500);
            } else {
                mCommandQueue.recomputeDisableFlags(mDisplayId, animate);
            }
        }
        if (shouldHideIconsForBouncer) {
            mBouncerWasShowingWhenHidden = mBouncerShowing;
        }
    }

    public boolean headsUpShouldBeVisible() {
        return mHeadsUpAppearanceController.shouldBeVisible();
    }

    //TODO: These can / should probably be moved to NotificationPresenter or ShadeController
    @Override
    public void onLaunchAnimationCancelled() {
        if (!mPresenter.isCollapsing()) {
            onClosingFinished();
        }
    }

    @Override
    public void onExpandAnimationFinished(boolean launchIsFullScreen) {
        if (!mPresenter.isCollapsing()) {
            onClosingFinished();
        }
        if (launchIsFullScreen) {
            instantCollapseNotificationPanel();
        }
    }

    @Override
    public void onExpandAnimationTimedOut() {
        if (mPresenter.isPresenterFullyCollapsed() && !mPresenter.isCollapsing()
                && mActivityLaunchAnimator != null
                && !mActivityLaunchAnimator.isLaunchForActivity()) {
            onClosingFinished();
        } else {
            collapsePanel(true /* animate */);
        }
    }

    @Override
    public boolean areLaunchAnimationsEnabled() {
        return mState == StatusBarState.SHADE;
    }

    public boolean isDeviceInVrMode() {
        return mPresenter.isDeviceInVrMode();
    }

    public NotificationPresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void setBlockedGesturalNavigation(boolean blocked) {
        if (getNavigationBarView() != null) {
            getNavigationBarView().setBlockedGesturalNavigation(blocked);
        }
    }

    private CustomSettingsObserver mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
    private class CustomSettingsObserver extends ContentObserver {

        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BRIGHTNESS_SLIDER_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.FP_SWIPE_TO_DISMISS_NOTIFICATIONS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PULSE_ON_NEW_TRACKS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_ROWS_PORTRAIT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_ROWS_LANDSCAPE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_COLUMNS_PORTRAIT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_COLUMNS_LANDSCAPE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TILE_TITLE_VISIBILITY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BACK_SWIPE_TYPE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BACK_GESTURE_HAPTIC),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LESS_BORING_HEADS_UP),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HEADS_UP_BLACKLIST_VALUES),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                          Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG),
                          false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_CARRIER),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_TICKER),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_TICKER_TICK_DURATION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_ACTIVE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GAMING_MODE_HEADSUP_TOGGLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_BACKGROUND_BLUR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_BACKGROUND_BLUR_ALPHA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_BACKGROUND_BLUR_INTENSITY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_DATAUSAGE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SHOW_BACK_ARROW_GESTURE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.QS_TILE_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.AMBIENT_NOTIFICATION_LIGHT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.AMBIENT_LIGHT_COLOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.AMBIENT_NOTIFICATION_LIGHT_HIDE_AOD),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TILE_ACCENT_TINT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_LABEL_USE_NEW_TINT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_LABEL_INACTIVE_TINT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TILE_ACCENT_TINT_INACTIVE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FORCE_SHOW_NAVBAR),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SYSTEM_THEME),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_HEADER_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
	            Settings.System.QS_PANEL_BG_USE_WALL),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_PANEL_BG_USE_FW),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                     Settings.System.QS_PANEL_BG_COLOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_PANEL_BG_COLOR_WALL),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
	            Settings.System.QS_PANEL_BG_USE_ACCENT),
	            false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TILE_ICON_PRIMARY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TILE_RGB_TINT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TILE_GRADIENT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LONG_BACK_SWIPE_TIMEOUT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LEFT_LONG_BACK_SWIPE_ACTION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SHOW_MEDIA_HEADS_UP),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_SHOW_EXPANDINDICATOR),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.GESTURE_ANYWHERE_ENABLED),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_PANEL_BG_FILTER),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_PANEL_FILTER_COLOR),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ANALOG_CLOCK_SIZE),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VOLUME_PANEL_ICON_TINT),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VOLUME_SLIDER_COLOR),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SYSTEMUI_ICON_PACK),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SETTINGS_ICON_PACK),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SETTINGS_SEARCHBAR_COLOR),
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SETTINGS_SEARCHBAR_RADIUS),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
           if (uri.equals(Settings.System.getUriFor(
                    Settings.System.QS_TILE_STYLE))) {
                stockTileStyle();
                updateTileStyle();
                updateQsPanelResources();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.SYSTEM_THEME))) {
                updateSystemTheme();
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.QS_HEADER_STYLE))) {
                stockQSHeaderStyle();
                updateQSHeaderStyle();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.BRIGHTNESS_SLIDER_STYLE))) {
                stockBrightnessStyle();
                updateBrightnessSliderStyle();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.ANALOG_CLOCK_SIZE))) {
                stockAnalogClockSize();
                updateAnalogClockSize();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.VOLUME_PANEL_ICON_TINT))) {
                stockVolumeIconTint();
                updateVolumeIconTint();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.VOLUME_SLIDER_COLOR))) {
                stockVolumeSlider();
                updateVolumeSlider();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.SYSTEMUI_ICON_PACK))) {
                stockSystemUIIcons();
                updateSystemUIIcons();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.SETTINGS_ICON_PACK))) {
                stockSettingsIcons();
                updateSettingsIcons();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.SETTINGS_SEARCHBAR_COLOR))) {
                stockSearchBarColor();
                updateSearchBarColor();
	         } else if (uri.equals(Settings.System.getUriFor(Settings.System.SETTINGS_SEARCHBAR_RADIUS))) {
                stockSearchBarRadius();
                updateSearchBarRadius();
	         }  else if (uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_BG_USE_FW)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_BG_COLOR)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_BG_COLOR_WALL)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_BG_USE_ACCENT)))  {
                mQSPanel.getHost().reloadAllTiles();
             } else if (uri.equals(Settings.System.getUriFor(Settings.System.QS_TILE_GRADIENT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_TILE_RGB_TINT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_TILE_ACCENT_TINT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_LABEL_USE_NEW_TINT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_LABEL_INACTIVE_TINT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_TILE_ACCENT_TINT_INACTIVE)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_SHOW_EXPANDINDICATOR)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_TILE_ICON_PRIMARY)))  {
                if (mQSPanel.getHost().isStock()) {
                    try {
                      mOverlayManager.reloadAssets("com.android.systemui",
                           mLockscreenUserManager.getCurrentUserId());
                    } catch (Exception e) {
                      Log.w(TAG, "Cannot reload assets", e);
                    }
                } else {
                    mQSPanel.getHost().reloadAllTiles();
                }
             } else {
              update();
            }
        }

        public void update() {
            if (mStatusBarWindow != null) {
                mStatusBarWindow.updateSettings();
            }
            setFpToDismissNotifications();
            setPulseOnNewTracks();
            setUseLessBoringHeadsUp();
            updateHeadsUpBlackList();
            setMaxKeyguardNotifConfig();
            updateTickerAnimation();
            updateTickerTickDuration();
            setGamingModeActive();
            setGamingModeHeadsupToggle();
            updateQSBlur();
            if (mCollapsedStatusBarFragment != null) {
                mCollapsedStatusBarFragment.updateSettings(false);
            }
            updateDataUsage();
            setHideArrowForBackGesture();
            updateQsPanelResources();
            updateNavigationBar(getRegisterStatusBarResult(), false);
            updateQSPanel();
            setGestureNavOptions();
            setHapticFeedbackForBackGesture();
            setMediaHeadsup();
            refreshGA();
        }
    }

    private void setMediaHeadsup() {
        if (mMediaManager != null) {
            mMediaManager.setMediaHeadsup();
        }
    }


     public void updateBrightnessSliderStyle() {
         int brighthnessSliderStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                 Settings.System.BRIGHTNESS_SLIDER_STYLE, 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateBrightnessSliderStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), brighthnessSliderStyle);
     }

     public void stockBrightnessStyle() {
        ThemeAccentUtils.stockBrightnessSliderStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
     }

    private void refreshGA() {
        mGaEnabled = Settings.System.getInt(
                    mContext.getContentResolver(), Settings.System.GESTURE_ANYWHERE_ENABLED, 0) == 1;
        if (mGaEnabled) {
            Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.GESTURE_ANYWHERE_SHOW_TRIGGER, 1);
            Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.GESTURE_ANYWHERE_SHOW_TRIGGER, 0);
        }
    }

    private void setHapticFeedbackForBackGesture() {
        if (getNavigationBarView() != null) {
            getNavigationBarView().updateBackGestureHaptic();
        }
    }

    private void setFpToDismissNotifications() {
        mFpDismissNotifications = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FP_SWIPE_TO_DISMISS_NOTIFICATIONS, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private void updateQSBlur() {
       mQSBlurEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_BACKGROUND_BLUR, 1,
                UserHandle.USER_CURRENT) == 1;
       mBackgroundFilter = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_PANEL_BG_FILTER, 0,
                    UserHandle.USER_CURRENT);
       if (!mQSBlurEnabled) {
           mQSBlurView.setVisibility(View.GONE);
       } else {
           updateBlurVisibility();
       }
    }

    private void setPulseOnNewTracks() {
        if (mSliceProvider != null) {
            mSliceProvider.setPulseOnNewTracks(Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.PULSE_ON_NEW_TRACKS, 1,
                    UserHandle.USER_CURRENT) == 1);
        }
    }

    public boolean isDeviceProvisioned() {
        return mDeviceProvisionedController.isDeviceProvisioned();
    }

    private void updateQsPanelResources() {
           if (mQSPanel != null) {
               mQSPanel.getHost().reloadAllTiles();
               mQSPanel.updateSettings();
            }
    }

    /**
     * All changes to the status bar and notifications funnel through here and are batched.
     */
    protected class H extends Handler {
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_TOGGLE_KEYBOARD_SHORTCUTS_MENU:
                    toggleKeyboardShortcuts(m.arg1);
                    break;
                case MSG_DISMISS_KEYBOARD_SHORTCUTS_MENU:
                    dismissKeyboardShortcuts();
                    break;
                // End old BaseStatusBar.H handling.
                case MSG_OPEN_NOTIFICATION_PANEL:
                    animateExpandNotificationsPanel();
                    break;
                case MSG_OPEN_SETTINGS_PANEL:
                    animateExpandSettingsPanel((String) m.obj);
                    break;
                case MSG_CLOSE_PANELS:
                    animateCollapsePanels();
                    break;
                case MSG_LAUNCH_TRANSITION_TIMEOUT:
                    onLaunchTransitionTimeout();
                    break;
            }
        }
    }

    public void maybeEscalateHeadsUp() {
        mHeadsUpManager.getAllEntries().forEach(entry -> {
            final StatusBarNotification sbn = entry.notification;
            final Notification notification = sbn.getNotification();
            if (notification.fullScreenIntent != null) {
                if (DEBUG) {
                    Log.d(TAG, "converting a heads up to fullScreen");
                }
                try {
                    EventLog.writeEvent(EventLogTags.SYSUI_HEADS_UP_ESCALATION,
                            sbn.getKey());
                    notification.fullScreenIntent.send();
                    entry.notifyFullScreenIntentLaunched();
                } catch (PendingIntent.CanceledException e) {
                }
            }
        });
        mHeadsUpManager.releaseAllImmediately();
    }

    /**
     * Called for system navigation gestures. First action opens the panel, second opens
     * settings. Down action closes the entire panel.
     */
    @Override
    public void handleSystemKey(int key) {
        if (SPEW) Log.d(TAG, "handleNavigationKey: " + key);

        if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == key || KeyEvent.KEYCODE_MEDIA_NEXT == key) {
            mMediaManager.onSkipTrackEvent(key);
            return;
        }

        if (!mCommandQueue.panelsEnabled() || !mKeyguardMonitor.isDeviceInteractive()
                || mKeyguardMonitor.isShowing() && !mKeyguardMonitor.isOccluded()) {
            return;
        }

        // Panels are not available in setup
        if (!mUserSetup) return;

        if (KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP == key) {
            mMetricsLogger.action(MetricsEvent.ACTION_SYSTEM_NAVIGATION_KEY_UP);
            mNotificationPanel.collapse(false /* delayed */, 1.0f /* speedUpFactor */);
        } else if (KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN == key) {
            mMetricsLogger.action(MetricsEvent.ACTION_SYSTEM_NAVIGATION_KEY_DOWN);
            if (mNotificationPanel.isFullyCollapsed()) {
                if (mVibrateOnOpening) {
                    mVibratorHelper.vibrate(VibrationEffect.EFFECT_TICK);
                }
                mNotificationPanel.expand(true /* animate */);
                ((NotificationListContainer) mStackScroller).setWillExpand(true);
                mHeadsUpManager.unpinAll(true /* userUnpinned */);
                mMetricsLogger.count(NotificationPanelView.COUNTER_PANEL_OPEN, 1);
            } else if (!mNotificationPanel.isInSettings() && !mNotificationPanel.isExpanding()){
                mNotificationPanel.flingSettings(0 /* velocity */,
                        NotificationPanelView.FLING_EXPAND);
                mMetricsLogger.count(NotificationPanelView.COUNTER_PANEL_OPEN_QS, 1);
            }
        } else if (mFpDismissNotifications && (KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT == key
                || KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT == key)) {
            if (!mNotificationPanel.isFullyCollapsed() && !mNotificationPanel.isExpanding()){
                mMetricsLogger.action(MetricsEvent.ACTION_DISMISS_ALL_NOTES);
                ((NotificationStackScrollLayout)mStackScroller).clearNotifications(
                        ((NotificationStackScrollLayout)mStackScroller).ROWS_ALL/*all notifications*/,
                        true/*collapse the panel*/,
                        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT == key/*left swipe animation*/);
            }
        }

    }

    @Override
    public void showPinningEnterExitToast(boolean entering) {
        if (getNavigationBarView() != null) {
            getNavigationBarView().showPinningEnterExitToast(entering);
        }
    }

    @Override
    public void showPinningEscapeToast() {
        if (getNavigationBarView() != null) {
            getNavigationBarView().showPinningEscapeToast();
        }
    }

    @Override
    public void killForegroundApp() {
        boolean killed = false;
        if (!mIsLauncherShowing && mTaskComponentName != null &&
                mContext.checkCallingOrSelfPermission(android.Manifest.permission.FORCE_STOP_PACKAGES)
                == PackageManager.PERMISSION_GRANTED) {
            IActivityManager iam = ActivityManagerNative.getDefault();
            try {
                iam.forceStopPackage(mTaskComponentName.getPackageName(), UserHandle.USER_CURRENT); // kill app
                iam.removeTask(mRunningTaskId); // remove app from recents
                killed = true;
            } catch (RemoteException e) {
                killed = false;
            }
            if (killed) {
                Toast appKilled = Toast.makeText(mContext, R.string.recents_app_killed,
                        Toast.LENGTH_SHORT);
                appKilled.show();
            } else {
                // maybe show a toast?
            }
        }
        if (mIsLauncherShowing) {
            Toast nope = Toast.makeText(mContext, R.string.nope_cant_kill,
                    Toast.LENGTH_SHORT);
            nope.show();
        }
    }

    @Override
    public void toggleCameraFlash() {
        if (!isScreenFullyOff() && mDeviceInteractive && !mPulsing && !mDozing) {
            toggleFlashlight();
            return;
        }
    }

    private void toggleFlashlight() {
        if (mFlashlightController != null) {
            mFlashlightController.initFlashLight();
            if (mFlashlightController.hasFlashlight() && mFlashlightController.isAvailable()) {
                mFlashlightController.setFlashlight(!mFlashlightController.isEnabled());
            }
        }
    }

    void makeExpandedVisible(boolean force) {
        if (SPEW) Log.d(TAG, "Make expanded visible: expanded visible=" + mExpandedVisible);
        if (!force && (mExpandedVisible || !mCommandQueue.panelsEnabled())) {
            return;
        }

        mExpandedVisible = true;

        // Expand the window to encompass the full screen in anticipation of the drag.
        // This is only possible to do atomically because the status bar is at the top of the screen!
        mStatusBarWindowController.setPanelVisible(true);

        visibilityChanged(true);
        mCommandQueue.recomputeDisableFlags(mDisplayId, !force /* animate */);
        setInteracting(StatusBarManager.WINDOW_STATUS_BAR, true);
    }

    public void animateCollapsePanels() {
        animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
    }

    private final Runnable mAnimateCollapsePanels = this::animateCollapsePanels;

    public void postAnimateCollapsePanels() {
        mHandler.post(mAnimateCollapsePanels);
    }

    public void postAnimateForceCollapsePanels() {
        mHandler.post(() -> {
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE, true /* force */);
        });
    }

    public void postAnimateOpenPanels() {
        mHandler.sendEmptyMessage(MSG_OPEN_SETTINGS_PANEL);
    }

    @Override
    public void togglePanel() {
        if (mPanelExpanded) {
            animateCollapsePanels();
        } else {
            animateExpandNotificationsPanel();
        }
    }

    @Override
    public void toggleSettingsPanel() {
        if (mPanelExpanded) {
            animateCollapsePanels();
        } else {
            animateExpandSettingsPanel(null);
        }
    }

    public void animateCollapsePanels(int flags) {
        animateCollapsePanels(flags, false /* force */, false /* delayed */,
                1.0f /* speedUpFactor */);
    }

    @Override
    public void animateCollapsePanels(int flags, boolean force) {
        animateCollapsePanels(flags, force, false /* delayed */, 1.0f /* speedUpFactor */);
    }

    public void animateCollapsePanels(int flags, boolean force, boolean delayed) {
        animateCollapsePanels(flags, force, delayed, 1.0f /* speedUpFactor */);
    }

    public void animateCollapsePanels(int flags, boolean force, boolean delayed,
            float speedUpFactor) {
        if (!force && mState != StatusBarState.SHADE) {
            runPostCollapseRunnables();
            return;
        }
        if (SPEW) {
            Log.d(TAG, "animateCollapse():"
                    + " mExpandedVisible=" + mExpandedVisible
                    + " flags=" + flags);
        }

        if ((flags & CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL) == 0) {
            if (!mHandler.hasMessages(MSG_HIDE_RECENT_APPS)) {
                mHandler.removeMessages(MSG_HIDE_RECENT_APPS);
                mHandler.sendEmptyMessage(MSG_HIDE_RECENT_APPS);
            }
        }

        // TODO(b/62444020): remove when this bug is fixed
        Log.v(TAG, "mStatusBarWindow: " + mStatusBarWindow + " canPanelBeCollapsed(): "
                + mNotificationPanel.canPanelBeCollapsed());
        if (mStatusBarWindow != null && mNotificationPanel.canPanelBeCollapsed()) {
            // release focus immediately to kick off focus change transition
            mStatusBarWindowController.setStatusBarFocusable(false);

            mStatusBarWindow.cancelExpandHelper();
            mStatusBarView.collapsePanel(true /* animate */, delayed, speedUpFactor);
        } else {
            if (mBubbleController != null) {
                mBubbleController.collapseStack();
            }
        }
    }

    private void runPostCollapseRunnables() {
        ArrayList<Runnable> clonedList = new ArrayList<>(mPostCollapseRunnables);
        mPostCollapseRunnables.clear();
        int size = clonedList.size();
        for (int i = 0; i < size; i++) {
            clonedList.get(i).run();
        }
        mStatusBarKeyguardViewManager.readyForKeyguardDone();
    }

    /**
     * Called when another window is about to transfer it's input focus.
     */
    public void onInputFocusTransfer(boolean start, float velocity) {
        if (!mCommandQueue.panelsEnabled()) {
            return;
        }

        if (start) {
            mNotificationPanel.startWaitingForOpenPanelGesture();
        } else {
            mNotificationPanel.stopWaitingForOpenPanelGesture(velocity);
        }
    }

    @Override
    public void animateExpandNotificationsPanel() {
        if (SPEW) Log.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if (!mCommandQueue.panelsEnabled()) {
            return ;
        }

        mNotificationPanel.expandWithoutQs();

        if (false) postStartTracing();
    }

    @Override
    public void animateExpandSettingsPanel(@Nullable String subPanel) {
        if (SPEW) Log.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if (!mCommandQueue.panelsEnabled()) {
            return;
        }

        // Settings are not available in setup
        if (!mUserSetup) return;

        if (subPanel != null) {
            mQSPanel.openDetails(subPanel);
        }
        mNotificationPanel.expandWithQs();

        if (false) postStartTracing();
    }

    public void animateCollapseQuickSettings() {
        if (mState == StatusBarState.SHADE) {
            mStatusBarView.collapsePanel(true, false /* delayed */, 1.0f /* speedUpFactor */);
        }
    }

    void makeExpandedInvisible() {
        if (SPEW) Log.d(TAG, "makeExpandedInvisible: mExpandedVisible=" + mExpandedVisible
                + " mExpandedVisible=" + mExpandedVisible);

        if (!mExpandedVisible || mStatusBarWindow == null) {
            return;
        }

        // Ensure the panel is fully collapsed (just in case; bug 6765842, 7260868)
        mStatusBarView.collapsePanel(/*animate=*/ false, false /* delayed*/,
                1.0f /* speedUpFactor */);

        mNotificationPanel.closeQs();

        mExpandedVisible = false;
        visibilityChanged(false);

        // Shrink the window to the size of the status bar only
        mStatusBarWindowController.setPanelVisible(false);
        mStatusBarWindowController.setForceStatusBarVisible(false);

        // Close any guts that might be visible
        mGutsManager.closeAndSaveGuts(true /* removeLeavebehind */, true /* force */,
                true /* removeControls */, -1 /* x */, -1 /* y */, true /* resetMenu */);

        runPostCollapseRunnables();
        setInteracting(StatusBarManager.WINDOW_STATUS_BAR, false);
        if (!mNotificationActivityStarter.isCollapsingToShowActivityOverLockscreen()) {
            showBouncerIfKeyguard();
        } else if (DEBUG) {
            Log.d(TAG, "Not showing bouncer due to activity showing over lockscreen");
        }
        mCommandQueue.recomputeDisableFlags(
                mDisplayId, mNotificationPanel.hideStatusBarIconsWhenExpanded() /* animate */);

        // Trimming will happen later if Keyguard is showing - doing it here might cause a jank in
        // the bouncer appear animation.
        if (!mStatusBarKeyguardViewManager.isShowing()) {
            WindowManagerGlobal.getInstance().trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        }
    }

    private void adjustBrightness(int x) {
        mBrightnessChanged = true;
        float raw = ((float) x) / getDisplayWidth();

        // Add a padding to the brightness control on both sides to
        // make it easier to reach min/max brightness
        float padded = Math.min(1.0f - BRIGHTNESS_CONTROL_PADDING,
                Math.max(BRIGHTNESS_CONTROL_PADDING, raw));
        float value = (padded - BRIGHTNESS_CONTROL_PADDING) /
                (1 - (2.0f * BRIGHTNESS_CONTROL_PADDING));
        if (mAutomaticBrightness) {
            float adj = (2 * value) - 1;
            adj = Math.max(adj, -1);
            adj = Math.min(adj, 1);
            final float val = adj;
            mDisplayManager.setTemporaryAutoBrightnessAdjustment(val);
            AsyncTask.execute(new Runnable() {
                public void run() {
                    Settings.System.putFloatForUser(mContext.getContentResolver(),
                            Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, val,
                            UserHandle.USER_CURRENT);
                }
            });
        } else {
            int newBrightness = mMinBrightness + (int) Math.round(value *
                    (PowerManager.BRIGHTNESS_ON - mMinBrightness));
            newBrightness = Math.min(newBrightness, PowerManager.BRIGHTNESS_ON);
            newBrightness = Math.max(newBrightness, mMinBrightness);
            final int val = newBrightness;
            mDisplayManager.setTemporaryBrightness(val);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    Settings.System.putIntForUser(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, val,
                            UserHandle.USER_CURRENT);
                }
            });
        }
    }

    private void brightnessControl(MotionEvent event) {
        final int action = event.getAction();
        final int x = (int) event.getRawX();
        final int y = (int) event.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            if (y < mQuickQsTotalHeight) {
                mLinger = 0;
                mInitialTouchX = x;
                mInitialTouchY = y;
                mJustPeeked = true;
                mHandler.removeCallbacks(mLongPressBrightnessChange);
                mHandler.postDelayed(mLongPressBrightnessChange,
                        BRIGHTNESS_CONTROL_LONG_PRESS_TIMEOUT);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (y < mQuickQsTotalHeight && mJustPeeked) {
                if (mLinger > BRIGHTNESS_CONTROL_LINGER_THRESHOLD) {
                    adjustBrightness(x);
                } else {
                    final int xDiff = Math.abs(x - mInitialTouchX);
                    final int yDiff = Math.abs(y - mInitialTouchY);
                    final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
                    if (xDiff > yDiff) {
                        mLinger++;
                    }
                    if (xDiff > touchSlop || yDiff > touchSlop) {
                        mHandler.removeCallbacks(mLongPressBrightnessChange);
                    }
                }
            } else {
                if (y > mQuickQsTotalHeight) {
                    mJustPeeked = false;
                }
                mHandler.removeCallbacks(mLongPressBrightnessChange);
            }
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            mHandler.removeCallbacks(mLongPressBrightnessChange);
        }
    }

    public boolean interceptTouchEvent(MotionEvent event) {
        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_STATUSBAR_TOUCH,
                        event.getActionMasked(), (int) event.getX(), (int) event.getY(),
                        mDisabled1, mDisabled2);
            }

        }

        if (SPEW) {
            Log.d(TAG, "Touch: rawY=" + event.getRawY() + " event=" + event + " mDisabled1="
                    + mDisabled1 + " mDisabled2=" + mDisabled2);
        } else if (CHATTY) {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                Log.d(TAG, String.format(
                            "panel: %s at (%f, %f) mDisabled1=0x%08x mDisabled2=0x%08x",
                            MotionEvent.actionToString(event.getAction()),
                            event.getRawX(), event.getRawY(), mDisabled1, mDisabled2));
            }
        }

        if (DEBUG_GESTURES) {
            mGestureRec.add(event);
        }

        if (mBrightnessControl) {
            brightnessControl(event);
            if ((mDisabled1 & StatusBarManager.DISABLE_EXPAND) != 0) {
                return true;
            }
        }

        final boolean upOrCancel =
                event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL;

        if (mStatusBarWindowState == WINDOW_STATE_SHOWING) {
            if (upOrCancel && !mExpandedVisible) {
                setInteracting(StatusBarManager.WINDOW_STATUS_BAR, false);
            } else {
                setInteracting(StatusBarManager.WINDOW_STATUS_BAR, true);
            }
        }
        if (mBrightnessChanged && upOrCancel) {
            mBrightnessChanged = false;
            if (mJustPeeked && mExpandedVisible) {
                mNotificationPanel.fling(10, false);
            }
        }
        return false;
    }

    public GestureRecorder getGestureRecorder() {
        return mGestureRec;
    }

    public BiometricUnlockController getBiometricUnlockController() {
        return mBiometricUnlockController;
    }

    @Override // CommandQueue
    public void setWindowState(
            int displayId, @WindowType int window, @WindowVisibleState int state) {
        if (displayId != mDisplayId) {
            return;
        }
        boolean showing = state == WINDOW_STATE_SHOWING;
        if (mStatusBarWindow != null
                && window == StatusBarManager.WINDOW_STATUS_BAR
                && mStatusBarWindowState != state) {
            mStatusBarWindowState = state;
            if (DEBUG_WINDOW_STATE) Log.d(TAG, "Status bar " + windowStateToString(state));
            if (!showing && mState == StatusBarState.SHADE) {
                mStatusBarView.collapsePanel(false /* animate */, false /* delayed */,
                        1.0f /* speedUpFactor */);
            }
            if (mStatusBarView != null) {
                mStatusBarWindowHidden = state == WINDOW_STATE_HIDDEN;
                updateHideIconsForBouncer(false /* animate */);
            }
        }
    }

    @Override // CommandQueue
    public void setSystemUiVisibility(int displayId, int vis, int fullscreenStackVis,
            int dockedStackVis, int mask, Rect fullscreenStackBounds, Rect dockedStackBounds,
            boolean navbarColorManagedByIme) {
        if (displayId != mDisplayId) {
            return;
        }

        mNavigationBarSystemUiVisibility.displayId = displayId;
        mNavigationBarSystemUiVisibility.vis = vis;
        mNavigationBarSystemUiVisibility.fullscreenStackVis = fullscreenStackVis;
        mNavigationBarSystemUiVisibility.dockedStackVis = dockedStackVis;
        mNavigationBarSystemUiVisibility.mask = mask;
        mNavigationBarSystemUiVisibility.fullscreenStackBounds = fullscreenStackBounds;
        mNavigationBarSystemUiVisibility.dockedStackBounds = dockedStackBounds;
        mNavigationBarSystemUiVisibility.navbarColorManagedByIme = navbarColorManagedByIme;

        final int oldVal = mSystemUiVisibility;
        final int newVal = (oldVal&~mask) | (vis&mask);
        final int diff = newVal ^ oldVal;
        if (DEBUG) Log.d(TAG, String.format(
                "setSystemUiVisibility displayId=%d vis=%s mask=%s oldVal=%s newVal=%s diff=%s",
                displayId, Integer.toHexString(vis), Integer.toHexString(mask),
                Integer.toHexString(oldVal), Integer.toHexString(newVal),
                Integer.toHexString(diff)));
        boolean sbModeChanged = false;
        if (diff != 0) {
            mSystemUiVisibility = newVal;

            // update low profile
            if ((diff & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
                updateAreThereNotifications();
                final boolean lightsOut = (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0;
                if (lightsOut) {
                    animateCollapsePanels();
                    if (mTicking) {
                        haltTicker();
                    }
                }
            }

            // ready to unhide
            if ((vis & View.STATUS_BAR_UNHIDE) != 0) {
                mNoAnimationOnNextBarModeChange = true;
            }

            // update status bar mode
            final int sbMode = computeStatusBarMode(oldVal, newVal);

            sbModeChanged = sbMode != -1;
            if (sbModeChanged && sbMode != mStatusBarMode) {
                mStatusBarMode = sbMode;
                checkBarModes();
                mAutoHideController.touchAutoHide();
            }
            mStatusBarStateController.setSystemUiVisibility(mSystemUiVisibility);
        }
        mLightBarController.onSystemUiVisibilityChanged(fullscreenStackVis, dockedStackVis,
                mask, fullscreenStackBounds, dockedStackBounds, sbModeChanged, mStatusBarMode,
                navbarColorManagedByIme);
    }

    protected final int getSystemUiVisibility() {
        return mSystemUiVisibility;
    }

    protected final int getDisplayId() {
        return mDisplayId;
    }

    @Override
    public void showWirelessChargingAnimation(int batteryLevel) {
        if (mDozing || mKeyguardManager.isKeyguardLocked()) {
            // on ambient or lockscreen, hide notification panel
            WirelessChargingAnimation.makeWirelessChargingAnimation(mContext, null,
                    batteryLevel, new WirelessChargingAnimation.Callback() {
                        @Override
                        public void onAnimationStarting() {
                            CrossFadeHelper.fadeOut(mNotificationPanel, 1);
                        }

                        @Override
                        public void onAnimationEnded() {
                            CrossFadeHelper.fadeIn(mNotificationPanel);
                        }
                    }, mDozing).show();
        } else {
            // workspace
            WirelessChargingAnimation.makeWirelessChargingAnimation(mContext, null,
                    batteryLevel, null, false).show();
        }
    }

    @Override
    public void onRecentsAnimationStateChanged(boolean running) {
        setInteracting(StatusBarManager.WINDOW_NAVIGATION_BAR, running);
    }

    protected @TransitionMode int computeStatusBarMode(int oldVal, int newVal) {
        return computeBarMode(oldVal, newVal);
    }

    protected BarTransitions getStatusBarTransitions() {
        return mStatusBarWindow.getBarTransitions();
    }

    protected @TransitionMode int computeBarMode(int oldVis, int newVis) {
        final int oldMode = barMode(oldVis);
        final int newMode = barMode(newVis);
        if (oldMode == newMode) {
            return -1; // no mode change
        }
        return newMode;
    }

    private @TransitionMode int barMode(int vis) {
        int lightsOutTransparent = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.STATUS_BAR_TRANSPARENT;
        if ((vis & View.STATUS_BAR_TRANSIENT) != 0) {
            return MODE_SEMI_TRANSPARENT;
        } else if ((vis & View.STATUS_BAR_TRANSLUCENT) != 0) {
            return MODE_TRANSLUCENT;
        } else if ((vis & lightsOutTransparent) == lightsOutTransparent) {
            return MODE_LIGHTS_OUT_TRANSPARENT;
        } else if ((vis & View.STATUS_BAR_TRANSPARENT) != 0) {
            return MODE_TRANSPARENT;
        } else if ((vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
            return MODE_LIGHTS_OUT;
        } else {
            return MODE_OPAQUE;
        }
    }

    void checkBarModes() {
        if (mDemoMode) return;
        if (mStatusBarView != null && getStatusBarTransitions() != null) {
            checkBarMode(mStatusBarMode, mStatusBarWindowState,
                    getStatusBarTransitions());
        }
        mNavigationBarController.checkNavBarModes(mDisplayId);
        mNoAnimationOnNextBarModeChange = false;
    }

    // Called by NavigationBarFragment
    void setQsScrimEnabled(boolean scrimEnabled) {
        mNotificationPanel.setQsScrimEnabled(scrimEnabled);
    }

    void checkBarMode(@TransitionMode int mode, @WindowVisibleState int windowState,
            BarTransitions transitions) {
        final boolean anim = !mNoAnimationOnNextBarModeChange && mDeviceInteractive
                && windowState != WINDOW_STATE_HIDDEN;
        transitions.transitionTo(mode, anim);
    }

    private void finishBarAnimations() {
        if (mStatusBarWindow != null && mStatusBarWindow.getBarTransitions() != null) {
            mStatusBarWindow.getBarTransitions().finishAnimations();
        }
        mNavigationBarController.finishBarAnimations(mDisplayId);
    }

    private final Runnable mCheckBarModes = this::checkBarModes;

    public void setInteracting(int barWindow, boolean interacting) {
        final boolean changing = ((mInteractingWindows & barWindow) != 0) != interacting;
        mInteractingWindows = interacting
                ? (mInteractingWindows | barWindow)
                : (mInteractingWindows & ~barWindow);
        if (mInteractingWindows != 0) {
            mAutoHideController.suspendAutoHide();
        } else {
            mAutoHideController.resumeSuspendedAutoHide();
        }
        // manually dismiss the volume panel when interacting with the nav bar
        if (changing && interacting && barWindow == StatusBarManager.WINDOW_NAVIGATION_BAR) {
            mNavigationBarController.touchAutoDim(mDisplayId);
            dismissVolumeDialog();
        }
        checkBarModes();
    }

    private void dismissVolumeDialog() {
        if (mVolumeComponent != null) {
            mVolumeComponent.dismissNow();
        }
    }

    /** Returns whether the top activity is in fullscreen mode. */
    public boolean inFullscreenMode() {
        return 0
                != (mSystemUiVisibility
                        & (View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION));
    }

    /** Returns whether the top activity is in immersive mode. */
    public boolean inImmersiveMode() {
        return 0
                != (mSystemUiVisibility
                        & (View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
    }

    private boolean areLightsOn() {
        return 0 == (mSystemUiVisibility & View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }


    public void tick(StatusBarNotification n, boolean firstTime, boolean isMusic,
                      MediaMetadata metaMediaData, String notificationText) {
        if (mTicker == null || mTickerEnabled == 0) return;

        // no ticking on keyguard, we have carrier name in the statusbar
        if (isKeyguardShowing()) return;

        // no ticking in lights-out mode
        if (!areLightsOn()) return;

        // no ticking in Setup
        if (!mDeviceProvisionedController.isDeviceProvisioned()) return;

        // not for you
        if (!isNotificationForCurrentProfiles(n)) return;

        // Show the ticker if one is requested. Also don't do this
        // until status bar window is attached to the window manager,
        // because...  well, what's the point otherwise?  And trying to
        // run a ticker without being attached will crash!
        if ((!isMusic ? (n.getNotification().tickerText != null) : (metaMediaData != null))
                && mStatusBarWindow != null && mStatusBarWindow.getWindowToken() != null) {
            if (0 == (mDisabled1 & (StatusBarManager.DISABLE_NOTIFICATION_ICONS
                    | StatusBarManager.DISABLE_NOTIFICATION_TICKER))) {
                mTicker.addEntry(n, isMusic, metaMediaData, notificationText);
            }
        }
    }

    private class MyTicker extends Ticker {

        // the inflated ViewStub
        public View mTickerView;

        MyTicker(Context context, View sb) {
            super(context, sb, mTickerAnimationMode, mTickerTickDuration);
            if (mTickerEnabled == 0) {
                Log.w(TAG, "MyTicker instantiated with mTickerEnabled=0", new Throwable());
            }
        }

        public void setView(View tv) {
            mTickerView = tv;
        }

        @Override
        public void tickerStarting() {
            if (mTicker == null || mTickerEnabled == 0) return;
            mTicking = true;
            Animation outAnim, inAnim;
            if (mTickerAnimationMode == 1) {
                outAnim = loadAnim(com.android.internal.R.anim.push_up_out, null);
                inAnim = loadAnim(com.android.internal.R.anim.push_up_in, null);
            } else {
                outAnim = loadAnim(true, null);
                inAnim = loadAnim(false, null);
            }
            mStatusBarContent.setVisibility(View.GONE);
            mStatusBarContent.startAnimation(outAnim);
            mCenterClockLayout.setVisibility(View.GONE);
            mCenterClockLayout.startAnimation(outAnim);
            if (mTickerView != null) {
                mTickerView.setVisibility(View.VISIBLE);
                mTickerView.startAnimation(inAnim);
            }
        }

        @Override
        public void tickerDone() {
            Animation outAnim, inAnim;
            if (mTickerAnimationMode == 1) {
                outAnim = loadAnim(com.android.internal.R.anim.push_up_out, mTickingDoneListener);
                inAnim = loadAnim(com.android.internal.R.anim.push_up_in, null);
            } else {
                outAnim = loadAnim(true, mTickingDoneListener);
                inAnim = loadAnim(false, null);
            }
            mStatusBarContent.setVisibility(View.VISIBLE);
            mStatusBarContent.startAnimation(inAnim);
            mCenterClockLayout.setVisibility(View.VISIBLE);
            mCenterClockLayout.startAnimation(inAnim);
            if (mTickerView != null) {
                mTickerView.setVisibility(View.GONE);
                mTickerView.startAnimation(outAnim);
            }
        }

        @Override
        public void tickerHalting() {
            if (mStatusBarContent.getVisibility() != View.VISIBLE) {
                mStatusBarContent.setVisibility(View.VISIBLE);
                mStatusBarContent.startAnimation(loadAnim(false, null));
                mCenterClockLayout.setVisibility(View.VISIBLE);
                mCenterClockLayout.startAnimation(loadAnim(false, null));
            }
            if (mTickerView != null) {
                mTickerView.setVisibility(View.GONE);
                // we do not animate the ticker away at this point, just get rid of it (b/6992707)
            }
        }

        @Override
        public void onDarkChanged(Rect area, float darkIntensity, int tint) {
            applyDarkIntensity(area, mTickerView, tint);
        }
    }

    Animation.AnimationListener mTickingDoneListener = new Animation.AnimationListener() {
        public void onAnimationEnd(Animation animation) {
            mTicking = false;
        }
        public void onAnimationRepeat(Animation animation) {
        }
        public void onAnimationStart(Animation animation) {
        }
    };

    private Animation loadAnim(boolean outAnim, Animation.AnimationListener listener) {
        AlphaAnimation animation = new AlphaAnimation((outAnim ? 1.0f : 0.0f), (outAnim ? 0.0f : 1.0f));
        Interpolator interpolator = AnimationUtils.loadInterpolator(mContext,
                (outAnim ? android.R.interpolator.accelerate_quad : android.R.interpolator.decelerate_quad));
        animation.setInterpolator(interpolator);
        animation.setDuration(350);

        if (listener != null) {
            animation.setAnimationListener(listener);
        }
        return animation;
    }

    private Animation loadAnim(int id, Animation.AnimationListener listener) {
        Animation anim = AnimationUtils.loadAnimation(mContext, id);
        if (listener != null) {
            anim.setAnimationListener(listener);
        }
        return anim;
    }

    public void haltTicker() {
        if (mTicker != null && mTickerEnabled != 0) {
            mTicker.halt();
        }
    }

    public static String viewInfo(View v) {
        return "[(" + v.getLeft() + "," + v.getTop() + ")(" + v.getRight() + "," + v.getBottom()
                + ") " + v.getWidth() + "x" + v.getHeight() + "]";
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mQueueLock) {
            pw.println("Current Status Bar state:");
            pw.println("  mExpandedVisible=" + mExpandedVisible);
            pw.println("  mDisplayMetrics=" + mDisplayMetrics);
            pw.println("  mStackScroller: " + viewInfo(mStackScroller));
            pw.println("  mStackScroller: " + viewInfo(mStackScroller)
                    + " scroll " + mStackScroller.getScrollX()
                    + "," + mStackScroller.getScrollY());
            pw.println("  mTickerEnabled=" + mTickerEnabled);
            if (mTickerEnabled != 0) {
                pw.println("  mTicking=" + mTicking);
	    }
        }

        pw.print("  mInteractingWindows="); pw.println(mInteractingWindows);
        pw.print("  mStatusBarWindowState=");
        pw.println(windowStateToString(mStatusBarWindowState));
        pw.print("  mStatusBarMode=");
        pw.println(BarTransitions.modeToString(mStatusBarMode));
        pw.print("  mDozing="); pw.println(mDozing);
        pw.print("  mZenMode=");
        pw.println(Settings.Global.zenModeToString(Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.ZEN_MODE,
                Settings.Global.ZEN_MODE_OFF)));
        pw.print("  mWallpaperSupported= "); pw.println(mWallpaperSupported);

        if (mStatusBarWindow != null) {
            dumpBarTransitions(pw, "mStatusBarWindow", mStatusBarWindow.getBarTransitions());
        }
        pw.println("  StatusBarWindowView: ");
        if (mStatusBarWindow != null) {
            mStatusBarWindow.dump(fd, pw, args);
        }

        pw.println("  mMediaManager: ");
        if (mMediaManager != null) {
            mMediaManager.dump(fd, pw, args);
        }

        pw.println("  Panels: ");
        if (mNotificationPanel != null) {
            pw.println("    mNotificationPanel=" +
                mNotificationPanel + " params=" + mNotificationPanel.getLayoutParams().debug(""));
            pw.print  ("      ");
            mNotificationPanel.dump(fd, pw, args);
        }
        pw.println("  mStackScroller: ");
        if (mStackScroller instanceof Dumpable) {
            pw.print  ("      ");
            ((Dumpable) mStackScroller).dump(fd, pw, args);
        }
        pw.println("  Theme:");
        String nightMode = mUiModeManager == null ? "null" : mUiModeManager.getNightMode() + "";
        pw.println("    dark theme: " + nightMode +
                " (auto: " + UiModeManager.MODE_NIGHT_AUTO +
                ", yes: " + UiModeManager.MODE_NIGHT_YES +
                ", no: " + UiModeManager.MODE_NIGHT_NO + ")");
        final boolean lightWpTheme = mContext.getThemeResId() == R.style.Theme_SystemUI_Light;
        pw.println("    light wallpaper theme: " + lightWpTheme);

        DozeLog.dump(pw);

        if (mBiometricUnlockController != null) {
            mBiometricUnlockController.dump(pw);
        }

        if (mKeyguardIndicationController != null) {
            mKeyguardIndicationController.dump(fd, pw, args);
        }

        if (mScrimController != null) {
            mScrimController.dump(fd, pw, args);
        }

        if (mStatusBarKeyguardViewManager != null) {
            mStatusBarKeyguardViewManager.dump(pw);
        }

        if (DUMPTRUCK) {
            synchronized (mEntryManager.getNotificationData()) {
                mEntryManager.getNotificationData().dump(pw, "  ");
            }

            if (false) {
                pw.println("see the logcat for a dump of the views we have created.");
                // must happen on ui thread
                mHandler.post(() -> {
                    mStatusBarView.getLocationOnScreen(mAbsPos);
                    Log.d(TAG, "mStatusBarView: ----- (" + mAbsPos[0] + "," + mAbsPos[1] +
                            ") " + mStatusBarView.getWidth() + "x" + getStatusBarHeight());
                    mStatusBarView.debug();
                });
            }
        }

        if (DEBUG_GESTURES) {
            pw.print("  status bar gestures: ");
            mGestureRec.dump(fd, pw, args);
        }

        if (mHeadsUpManager != null) {
            mHeadsUpManager.dump(fd, pw, args);
        } else {
            pw.println("  mHeadsUpManager: null");
        }
        if (mGroupManager != null) {
            mGroupManager.dump(fd, pw, args);
        } else {
            pw.println("  mGroupManager: null");
        }

        if (mBubbleController != null) {
            mBubbleController.dump(fd, pw, args);
        }

        if (mLightBarController != null) {
            mLightBarController.dump(fd, pw, args);
        }

        if (mUnlockMethodCache != null) {
            mUnlockMethodCache.dump(pw);
        }

        if (mKeyguardBypassController != null) {
            mKeyguardBypassController.dump(pw);
        }

        if (mKeyguardUpdateMonitor != null) {
            mKeyguardUpdateMonitor.dump(fd, pw, args);
        }

        Dependency.get(FalsingManager.class).dump(pw);
        FalsingLog.dump(pw);

        pw.println("SharedPreferences:");
        for (Map.Entry<String, ?> entry : Prefs.getAll(mContext).entrySet()) {
            pw.print("  "); pw.print(entry.getKey()); pw.print("="); pw.println(entry.getValue());
        }

        if (mFlashlightController != null) {
            mFlashlightController.dump(fd, pw, args);
        }
    }

    static void dumpBarTransitions(PrintWriter pw, String var, BarTransitions transitions) {
        pw.print("  "); pw.print(var); pw.print(".BarTransitions.mMode=");
        pw.println(BarTransitions.modeToString(transitions.getMode()));
    }

    public void createAndAddWindows(@Nullable RegisterStatusBarResult result) {
        makeStatusBarView(result);
        mStatusBarWindowController = Dependency.get(StatusBarWindowController.class);
        mStatusBarWindowController.add(mStatusBarWindow, getStatusBarHeight());
    }

    // called by makeStatusbar and also by PhoneStatusBarView
    void updateDisplaySize() {
        mDisplay.getMetrics(mDisplayMetrics);
        mDisplay.getSize(mCurrentDisplaySize);
        if (DEBUG_GESTURES) {
            mGestureRec.tag("display",
                    String.format("%dx%d", mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels));
        }
    }

    float getDisplayDensity() {
        return mDisplayMetrics.density;
    }

    float getDisplayWidth() {
        return mDisplayMetrics.widthPixels;
    }

    float getDisplayHeight() {
        return mDisplayMetrics.heightPixels;
    }

    int getRotation() {
        return mDisplay.getRotation();
    }

    public void startActivityDismissingKeyguard(final Intent intent, boolean onlyProvisioned,
            boolean dismissShade, int flags) {
        startActivityDismissingKeyguard(intent, onlyProvisioned, dismissShade,
                false /* disallowEnterPictureInPictureWhileLaunching */, null /* callback */,
                flags);
    }

    public void startActivityDismissingKeyguard(final Intent intent, boolean onlyProvisioned,
            boolean dismissShade) {
        startActivityDismissingKeyguard(intent, onlyProvisioned, dismissShade, 0);
    }

    public void startActivityDismissingKeyguard(final Intent intent, boolean onlyProvisioned,
            final boolean dismissShade, final boolean disallowEnterPictureInPictureWhileLaunching,
            final Callback callback, int flags) {
        if (onlyProvisioned && !mDeviceProvisionedController.isDeviceProvisioned()) return;

        final boolean afterKeyguardGone = mActivityIntentHelper.wouldLaunchResolverActivity(
                intent, mLockscreenUserManager.getCurrentUserId());
        Runnable runnable = () -> {
            mAssistManager.hideAssist();
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(flags);
            int result = ActivityManager.START_CANCELED;
            ActivityOptions options = new ActivityOptions(getActivityOptions(
                    null /* remoteAnimation */));
            options.setDisallowEnterPictureInPictureWhileLaunching(
                    disallowEnterPictureInPictureWhileLaunching);
            if (intent == KeyguardBottomAreaView.INSECURE_CAMERA_INTENT) {
                // Normally an activity will set it's requested rotation
                // animation on its window. However when launching an activity
                // causes the orientation to change this is too late. In these cases
                // the default animation is used. This doesn't look good for
                // the camera (as it rotates the camera contents out of sync
                // with physical reality). So, we ask the WindowManager to
                // force the crossfade animation if an orientation change
                // happens to occur during the launch.
                options.setRotationAnimationHint(
                        WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS);
            }
            if (intent.getAction() == Settings.Panel.ACTION_VOLUME) {
                // Settings Panel is implemented as activity(not a dialog), so
                // underlying app is paused and may enter picture-in-picture mode
                // as a result.
                // So we need to disable picture-in-picture mode here
                // if it is volume panel.
                options.setDisallowEnterPictureInPictureWhileLaunching(true);
            }
            try {
                result = ActivityTaskManager.getService().startActivityAsUser(
                        null, mContext.getBasePackageName(),
                        intent,
                        intent.resolveTypeIfNeeded(mContext.getContentResolver()),
                        null, null, 0, Intent.FLAG_ACTIVITY_NEW_TASK, null,
                        options.toBundle(), UserHandle.CURRENT.getIdentifier());
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to start activity", e);
            }
            if (callback != null) {
                callback.onActivityStarted(result);
            }
        };
        Runnable cancelRunnable = () -> {
            if (callback != null) {
                callback.onActivityStarted(ActivityManager.START_CANCELED);
            }
        };
        executeRunnableDismissingKeyguard(runnable, cancelRunnable, dismissShade,
                afterKeyguardGone, true /* deferred */);
    }

    public void readyForKeyguardDone() {
        mStatusBarKeyguardViewManager.readyForKeyguardDone();
    }

    public void executeRunnableDismissingKeyguard(final Runnable runnable,
            final Runnable cancelAction,
            final boolean dismissShade,
            final boolean afterKeyguardGone,
            final boolean deferred) {
        dismissKeyguardThenExecute(() -> {
            if (runnable != null) {
                if (mStatusBarKeyguardViewManager.isShowing()
                        && mStatusBarKeyguardViewManager.isOccluded()) {
                    mStatusBarKeyguardViewManager.addAfterKeyguardGoneRunnable(runnable);
                } else {
                    AsyncTask.execute(runnable);
                }
            }
            if (dismissShade) {
                if (mExpandedVisible && !mBouncerShowing) {
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL, true /* force */,
                            true /* delayed*/);
                } else {

                    // Do it after DismissAction has been processed to conserve the needed ordering.
                    mHandler.post(this::runPostCollapseRunnables);
                }
            } else if (isInLaunchTransition() && mNotificationPanel.isLaunchTransitionFinished()) {

                // We are not dismissing the shade, but the launch transition is already finished,
                // so nobody will call readyForKeyguardDone anymore. Post it such that
                // keyguardDonePending gets called first.
                mHandler.post(mStatusBarKeyguardViewManager::readyForKeyguardDone);
            }
            return deferred;
        }, cancelAction, afterKeyguardGone);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                KeyboardShortcuts.dismiss();
                if (mRemoteInputManager.getController() != null) {
                    mRemoteInputManager.getController().closeRemoteInputs();
                }
                if (mBubbleController != null && mBubbleController.isStackExpanded()) {
                    mBubbleController.collapseStack();
                }
                if (mLockscreenUserManager.isCurrentProfile(getSendingUserId())) {
                    int flags = CommandQueue.FLAG_EXCLUDE_NONE;
                    String reason = intent.getStringExtra("reason");
                    if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                        flags |= CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL;
                    }
                    animateCollapsePanels(flags);
                }
            }
            else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (mStatusBarWindowController != null) {
                    mStatusBarWindowController.setNotTouchable(false);
                }
                if (mBubbleController != null && mBubbleController.isStackExpanded()) {
                    mBubbleController.collapseStack();
                }
                finishBarAnimations();
                resetUserExpandedStates();
            }
            else if (DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG.equals(action)) {
                mQSPanel.showDeviceMonitoringDialog();
            }
            else if (lineageos.content.Intent.ACTION_SCREEN_CAMERA_GESTURE.equals(action)) {
                boolean userSetupComplete = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
                if (!userSetupComplete) {
                    if (DEBUG) Log.d(TAG, String.format(
                            "userSetupComplete = %s, ignoring camera launch gesture.",
                            userSetupComplete));
                    return;
                }

                onCameraLaunchGestureDetected(StatusBarManager.CAMERA_LAUNCH_SOURCE_SCREEN_GESTURE);
            }
            else if ("com.android.systemui.ACTION_DISMISS_KEYGUARD".equals(action)) {
                if (intent.hasExtra("launch")) {
                    Intent launchIntent = (Intent) intent.getParcelableExtra("launch");
                    startActivityDismissingKeyguard(launchIntent, true, true);
                }
            }
        }
    };

    private final BroadcastReceiver mDemoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            if (ACTION_DEMO.equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    String command = bundle.getString("command", "").trim().toLowerCase();
                    if (command.length() > 0) {
                        try {
                            dispatchDemoCommand(command, bundle);
                        } catch (Throwable t) {
                            Log.w(TAG, "Error running demo command, intent=" + intent, t);
                        }
                    }
                }
            } else if (ACTION_FAKE_ARTWORK.equals(action)) {
                if (DEBUG_MEDIA_FAKE_ARTWORK) {
                    mPresenter.updateMediaMetaData(true, true);
                }
            }
        }
    };

    public void resetUserExpandedStates() {
        ArrayList<NotificationEntry> activeNotifications = mEntryManager.getNotificationData()
                .getActiveNotifications();
        final int notificationCount = activeNotifications.size();
        for (int i = 0; i < notificationCount; i++) {
            NotificationEntry entry = activeNotifications.get(i);
            entry.resetUserExpansion();
        }
    }

    private void executeWhenUnlocked(OnDismissAction action, boolean requiresShadeOpen) {
        if (mStatusBarKeyguardViewManager.isShowing() && requiresShadeOpen) {
            mStatusBarStateController.setLeaveOpenOnKeyguardHide(true);
        }
        dismissKeyguardThenExecute(action, null /* cancelAction */, false /* afterKeyguardGone */);
    }

    protected void dismissKeyguardThenExecute(OnDismissAction action, boolean afterKeyguardGone) {
        dismissKeyguardThenExecute(action, null /* cancelRunnable */, afterKeyguardGone);
    }

    @Override
    public void dismissKeyguardThenExecute(OnDismissAction action, Runnable cancelAction,
            boolean afterKeyguardGone) {
        if (mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_ASLEEP
                && mUnlockMethodCache.canSkipBouncer()
                && !mStatusBarStateController.leaveOpenOnKeyguardHide()
                && isPulsing()) {
            // Reuse the biometric wake-and-unlock transition if we dismiss keyguard from a pulse.
            // TODO: Factor this transition out of BiometricUnlockController.
            mBiometricUnlockController.startWakeAndUnlock(
                    BiometricUnlockController.MODE_WAKE_AND_UNLOCK_PULSING);
        }
        if (mStatusBarKeyguardViewManager.isShowing()) {
            mStatusBarKeyguardViewManager.dismissWithAction(action, cancelAction,
                    afterKeyguardGone);
        } else {
            action.onDismiss();
        }
    }

    // SystemUIService notifies SystemBars of configuration changes, which then calls down here
    @Override
    public void onConfigChanged(Configuration newConfig) {
        updateResources();
        updateDisplaySize(); // populates mDisplayMetrics

        mPortrait = RotationUtils.getExactRotation(mContext) == RotationUtils.ROTATION_NONE;

        mViewHierarchyManager.updateRowStates();
        mScreenPinningRequest.onConfigurationChanged();

        if (blurperformed) {
            mNotificationPanel.setPanelAlpha(0, false);
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    drawBlurView();
                    mNotificationPanel.setPanelAlphaFast(255, true);
                }
            }, Math.max(390, Math.round(455f * Settings.Global.getFloat(
                    mContext.getContentResolver(),
                    Settings.Global.TRANSITION_ANIMATION_SCALE, 1.0f))));
        }

        if (mImmerseMode == 1) {
            mUiOffloadThread.submit(() -> {
                setBlackStatusBar(mPortrait);
            });
        }
    }

    @Override
    public void setLockscreenUser(int newUserId) {
        if (mLockscreenWallpaper != null) {
            mLockscreenWallpaper.setCurrentUser(newUserId);
        }
        mScrimController.setCurrentUser(newUserId);
        if (mWallpaperSupported) {
            mWallpaperChangedReceiver.onReceive(mContext, null);
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
        // Update the quick setting tiles
        if (mQSPanel != null) {
            mQSPanel.updateResources();
        }

        loadDimens();

        if (mStatusBarView != null) {
            mStatusBarView.updateResources();
        }
        if (mNotificationPanel != null) {
            mNotificationPanel.updateResources();
        }
    }

    protected void loadDimens() {
        final Resources res = mContext.getResources();

        int oldBarHeight = mNaturalBarHeight;
        mNaturalBarHeight = res.getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        if (mStatusBarWindowController != null && mNaturalBarHeight != oldBarHeight) {
            mStatusBarWindowController.setBarHeight(mNaturalBarHeight);
        }

        mQuickQsTotalHeight = res.getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_total_height);

        if (DEBUG) Log.v(TAG, "defineSlots");
    }

    // Visibility reporting

    protected void handleVisibleToUserChanged(boolean visibleToUser) {
        if (visibleToUser) {
            handleVisibleToUserChangedImpl(visibleToUser);
            mNotificationLogger.startNotificationLogging();
        } else {
            mNotificationLogger.stopNotificationLogging();
            handleVisibleToUserChangedImpl(visibleToUser);
        }
    }

    void handlePeekToExpandTransistion() {
        try {
            // consider the transition from peek to expanded to be a panel open,
            // but not one that clears notification effects.
            int notificationLoad = mEntryManager.getNotificationData()
                    .getActiveNotifications().size();
            mBarService.onPanelRevealed(false, notificationLoad);
        } catch (RemoteException ex) {
            // Won't fail unless the world has ended.
        }
    }

    /**
     * The LEDs are turned off when the notification panel is shown, even just a little bit.
     * See also StatusBar.setPanelExpanded for another place where we attempt to do this.
     */
    private void handleVisibleToUserChangedImpl(boolean visibleToUser) {
        if (visibleToUser) {
            boolean pinnedHeadsUp = mHeadsUpManager.hasPinnedHeadsUp();
            boolean clearNotificationEffects =
                    !mPresenter.isPresenterFullyCollapsed() &&
                            (mState == StatusBarState.SHADE
                                    || mState == StatusBarState.SHADE_LOCKED);
            int notificationLoad = mEntryManager.getNotificationData().getActiveNotifications()
                    .size();
            if (pinnedHeadsUp && mPresenter.isPresenterFullyCollapsed()) {
                notificationLoad = 1;
            }
            final int finalNotificationLoad = notificationLoad;
            mUiOffloadThread.submit(() -> {
                try {
                    mBarService.onPanelRevealed(clearNotificationEffects,
                            finalNotificationLoad);
                } catch (RemoteException ex) {
                    // Won't fail unless the world has ended.
                }
            });
        } else {
            mUiOffloadThread.submit(() -> {
                try {
                    mBarService.onPanelHidden();
                } catch (RemoteException ex) {
                    // Won't fail unless the world has ended.
                }
            });
        }

    }

    private void logStateToEventlog() {
        boolean isShowing = mStatusBarKeyguardViewManager.isShowing();
        boolean isOccluded = mStatusBarKeyguardViewManager.isOccluded();
        boolean isBouncerShowing = mStatusBarKeyguardViewManager.isBouncerShowing();
        boolean isSecure = mUnlockMethodCache.isMethodSecure();
        boolean canSkipBouncer = mUnlockMethodCache.canSkipBouncer();
        int stateFingerprint = getLoggingFingerprint(mState,
                isShowing,
                isOccluded,
                isBouncerShowing,
                isSecure,
                canSkipBouncer);
        if (stateFingerprint != mLastLoggedStateFingerprint) {
            if (mStatusBarStateLog == null) {
                mStatusBarStateLog = new LogMaker(MetricsEvent.VIEW_UNKNOWN);
            }
            mMetricsLogger.write(mStatusBarStateLog
                    .setCategory(isBouncerShowing ? MetricsEvent.BOUNCER : MetricsEvent.LOCKSCREEN)
                    .setType(isShowing ? MetricsEvent.TYPE_OPEN : MetricsEvent.TYPE_CLOSE)
                    .setSubtype(isSecure ? 1 : 0));
            EventLogTags.writeSysuiStatusBarState(mState,
                    isShowing ? 1 : 0,
                    isOccluded ? 1 : 0,
                    isBouncerShowing ? 1 : 0,
                    isSecure ? 1 : 0,
                    canSkipBouncer ? 1 : 0);
            mLastLoggedStateFingerprint = stateFingerprint;
        }
    }

    /**
     * Returns a fingerprint of fields logged to eventlog
     */
    private static int getLoggingFingerprint(int statusBarState, boolean keyguardShowing,
            boolean keyguardOccluded, boolean bouncerShowing, boolean secure,
            boolean currentlyInsecure) {
        // Reserve 8 bits for statusBarState. We'll never go higher than
        // that, right? Riiiight.
        return (statusBarState & 0xFF)
                | ((keyguardShowing   ? 1 : 0) <<  8)
                | ((keyguardOccluded  ? 1 : 0) <<  9)
                | ((bouncerShowing    ? 1 : 0) << 10)
                | ((secure            ? 1 : 0) << 11)
                | ((currentlyInsecure ? 1 : 0) << 12);
    }

    //
    // tracing
    //

    void postStartTracing() {
        mHandler.postDelayed(mStartTracing, 3000);
    }

    void vibrate() {
        android.os.Vibrator vib = (android.os.Vibrator)mContext.getSystemService(
                Context.VIBRATOR_SERVICE);
        vib.vibrate(250, VIBRATION_ATTRIBUTES);
    }

    final Runnable mStartTracing = new Runnable() {
        @Override
        public void run() {
            vibrate();
            SystemClock.sleep(250);
            Log.d(TAG, "startTracing");
            android.os.Debug.startMethodTracing("/data/statusbar-traces/trace");
            mHandler.postDelayed(mStopTracing, 10000);
        }
    };

    final Runnable mStopTracing = () -> {
        android.os.Debug.stopMethodTracing();
        Log.d(TAG, "stopTracing");
        vibrate();
    };

    @Override
    public void postQSRunnableDismissingKeyguard(final Runnable runnable) {
        mHandler.post(() -> {
            mStatusBarStateController.setLeaveOpenOnKeyguardHide(true);
            executeRunnableDismissingKeyguard(() -> mHandler.post(runnable), null, false, false,
                    false);
        });
    }

    @Override
    public void postStartActivityDismissingKeyguard(final PendingIntent intent) {
        mHandler.post(() -> startPendingIntentDismissingKeyguard(intent));
    }

    @Override
    public void postStartActivityDismissingKeyguard(final Intent intent, int delay) {
        mHandler.postDelayed(() ->
                handleStartActivityDismissingKeyguard(intent, true /*onlyProvisioned*/), delay);
    }

    private void handleStartActivityDismissingKeyguard(Intent intent, boolean onlyProvisioned) {
        startActivityDismissingKeyguard(intent, onlyProvisioned, true /* dismissShade */);
    }

    @Override
    public void onPackageChanged(String pkg, PackageState state) {
        if (state == PackageState.PACKAGE_REMOVED
                || state == PackageState.PACKAGE_CHANGED) {
            final Context ctx = mContext;
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!ActionUtils.hasNavbarByDefault(ctx)) {
                        ActionUtils.resolveAndUpdateButtonActions(ctx,
                                ActionConstants
                                        .getDefaults(ActionConstants.HWKEYS));
                    }
                }
            });
            thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
        }
    }

    private boolean mDemoModeAllowed;
    private boolean mDemoMode;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoModeAllowed) {
            mDemoModeAllowed = Settings.Global.getInt(mContext.getContentResolver(),
                    DEMO_MODE_ALLOWED, 0) != 0;
        }
        if (!mDemoModeAllowed) return;
        if (command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
        } else if (command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            checkBarModes();
        } else if (!mDemoMode) {
            // automatically enter demo mode on first demo command
            dispatchDemoCommand(COMMAND_ENTER, new Bundle());
        }
        boolean modeChange = command.equals(COMMAND_ENTER) || command.equals(COMMAND_EXIT);
        if ((modeChange || command.equals(COMMAND_VOLUME)) && mVolumeComponent != null) {
            mVolumeComponent.dispatchDemoCommand(command, args);
        }
        if (modeChange || command.equals(COMMAND_CLOCK)) {
            dispatchDemoCommandToView(command, args, R.id.clock);
        }
        if (modeChange || command.equals(COMMAND_BATTERY)) {
            mBatteryController.dispatchDemoCommand(command, args);
        }
        if (modeChange || command.equals(COMMAND_STATUS)) {
            ((StatusBarIconControllerImpl) mIconController).dispatchDemoCommand(command, args);
        }
        if (mNetworkController != null && (modeChange || command.equals(COMMAND_NETWORK))) {
            mNetworkController.dispatchDemoCommand(command, args);
        }
        if (modeChange || command.equals(COMMAND_NOTIFICATIONS)) {
            View notifications = mStatusBarView == null ? null
                    : mStatusBarView.findViewById(R.id.notification_icon_area);
            if (notifications != null) {
                String visible = args.getString("visible");
                int vis = mDemoMode && "false".equals(visible) ? View.INVISIBLE : View.VISIBLE;
                notifications.setVisibility(vis);
            }
        }
        if (command.equals(COMMAND_BARS)) {
            String mode = args.getString("mode");
            int barMode = "opaque".equals(mode) ? MODE_OPAQUE :
                    "translucent".equals(mode) ? MODE_TRANSLUCENT :
                    "semi-transparent".equals(mode) ? MODE_SEMI_TRANSPARENT :
                    "transparent".equals(mode) ? MODE_TRANSPARENT :
                    "warning".equals(mode) ? MODE_WARNING :
                    -1;
            if (barMode != -1) {
                boolean animate = true;
                if (mStatusBarWindow != null && mStatusBarWindow.getBarTransitions() != null) {
                    mStatusBarWindow.getBarTransitions().transitionTo(barMode, animate);
                }
                mNavigationBarController.transitionTo(mDisplayId, barMode, animate);
            }
        }
        if (modeChange || command.equals(COMMAND_OPERATOR)) {
            dispatchDemoCommandToView(command, args, R.id.operator_name);
        }
    }

    private void dispatchDemoCommandToView(String command, Bundle args, int id) {
        if (mStatusBarView == null) return;
        View v = mStatusBarView.findViewById(id);
        if (v instanceof DemoMode) {
            ((DemoMode)v).dispatchDemoCommand(command, args);
        }
    }

    public void showKeyguard() {
        mStatusBarStateController.setKeyguardRequested(true);
        mStatusBarStateController.setLeaveOpenOnKeyguardHide(false);
        mPendingRemoteInputView = null;
        updateIsKeyguard();
        if (mAssistManager != null) {
            mAssistManager.onLockscreenShown();
        }
    }

    public boolean hideKeyguard() {
        mStatusBarStateController.setKeyguardRequested(false);
        return updateIsKeyguard();
    }

    /**
     * stop(tag)
     * @return True if StatusBar state is FULLSCREEN_USER_SWITCHER.
     */
    public boolean isFullScreenUserSwitcherState() {
        return mState == StatusBarState.FULLSCREEN_USER_SWITCHER;
    }

    private boolean isAutomotive() {
        return mContext != null
                && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    boolean updateIsKeyguard() {
        return updateIsKeyguard(false /* force */);
    }

    boolean updateIsKeyguard(boolean force) {
        boolean wakeAndUnlocking = mBiometricUnlockController.getMode()
                == BiometricUnlockController.MODE_WAKE_AND_UNLOCK;

        if (mScreenLifecycle == null && isAutomotive()) {
            // TODO(b/146144370): workaround to avoid NPE when device goes into STR (Suspend to RAM)
            Log.w(TAG, "updateIsKeyguard(): mScreenLifeCycle not set yet");
            mScreenLifecycle = Dependency.get(ScreenLifecycle.class);
        }

        // For dozing, keyguard needs to be shown whenever the device is non-interactive. Otherwise
        // there's no surface we can show to the user. Note that the device goes fully interactive
        // late in the transition, so we also allow the device to start dozing once the screen has
        // turned off fully.
        boolean keyguardForDozing = mDozingRequested &&
                (!mDeviceInteractive || isGoingToSleep() && (isScreenFullyOff() || mIsKeyguard));
        boolean shouldBeKeyguard = (mStatusBarStateController.isKeyguardRequested()
                || keyguardForDozing) && !wakeAndUnlocking;
        if (keyguardForDozing) {
            updatePanelExpansionForKeyguard();
        }
        if (shouldBeKeyguard) {
            if (isGoingToSleep()
                    && mScreenLifecycle.getScreenState() == ScreenLifecycle.SCREEN_TURNING_OFF) {
                // Delay showing the keyguard until screen turned off.
            } else {
                showKeyguardImpl();
            }
        } else {
            return hideKeyguardImpl(force);
        }
        return false;
    }

    public void showKeyguardImpl() {
        mIsKeyguard = true;
        updateDataUsage();
        updateBlurVisibility();
        if (mKeyguardMonitor != null && mKeyguardMonitor.isLaunchTransitionFadingAway()) {
            mNotificationPanel.animate().cancel();
            onLaunchTransitionFadingEnded();
        }
        mHandler.removeMessages(MSG_LAUNCH_TRANSITION_TIMEOUT);
        if (mUserSwitcherController != null && mUserSwitcherController.useFullscreenUserSwitcher()) {
            mStatusBarStateController.setState(StatusBarState.FULLSCREEN_USER_SWITCHER);
        } else if (!mPulseExpansionHandler.isWakingToShadeLocked()){
            mStatusBarStateController.setState(StatusBarState.KEYGUARD);
        }
        updatePanelExpansionForKeyguard();
        if (mDraggedDownEntry != null) {
            mDraggedDownEntry.setUserLocked(false);
            mDraggedDownEntry.notifyHeightChanged(false /* needsAnimation */);
            mDraggedDownEntry = null;
        }
    }

    private void updatePanelExpansionForKeyguard() {
        if (mState == StatusBarState.KEYGUARD && mBiometricUnlockController.getMode()
                != BiometricUnlockController.MODE_WAKE_AND_UNLOCK) {
            instantExpandNotificationsPanel();
        } else if (mState == StatusBarState.FULLSCREEN_USER_SWITCHER) {
            instantCollapseNotificationPanel();
        }
    }

    private void onLaunchTransitionFadingEnded() {
        mNotificationPanel.setAlpha(1.0f);
        mNotificationPanel.onAffordanceLaunchEnded();
        releaseGestureWakeLock();
        runLaunchTransitionEndRunnable();
        mKeyguardMonitor.setLaunchTransitionFadingAway(false);
        mPresenter.updateMediaMetaData(true /* metaDataChanged */, true);
    }

    public void addPostCollapseAction(Runnable r) {
        mPostCollapseRunnables.add(r);
    }

    public boolean isInLaunchTransition() {
        return mNotificationPanel.isLaunchTransitionRunning()
                || mNotificationPanel.isLaunchTransitionFinished();
    }

    /**
     * Fades the content of the keyguard away after the launch transition is done.
     *
     * @param beforeFading the runnable to be run when the circle is fully expanded and the fading
     *                     starts
     * @param endRunnable the runnable to be run when the transition is done
     */
    public void fadeKeyguardAfterLaunchTransition(final Runnable beforeFading,
            Runnable endRunnable) {
        mHandler.removeMessages(MSG_LAUNCH_TRANSITION_TIMEOUT);
        mLaunchTransitionEndRunnable = endRunnable;
        Runnable hideRunnable = () -> {
            mKeyguardMonitor.setLaunchTransitionFadingAway(true);
            if (beforeFading != null) {
                beforeFading.run();
            }
            updateScrimController();
            mPresenter.updateMediaMetaData(false, true);
            mNotificationPanel.setAlpha(1);
            mNotificationPanel.animate()
                    .alpha(0)
                    .setStartDelay(FADE_KEYGUARD_START_DELAY)
                    .setDuration(FADE_KEYGUARD_DURATION)
                    .withLayer()
                    .withEndAction(this::onLaunchTransitionFadingEnded);
            mCommandQueue.appTransitionStarting(mDisplayId, SystemClock.uptimeMillis(),
                    LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION, true);
        };
        if (mNotificationPanel.isLaunchTransitionRunning()) {
            mNotificationPanel.setLaunchTransitionEndRunnable(hideRunnable);
        } else {
            hideRunnable.run();
        }
    }

    /**
     * Fades the content of the Keyguard while we are dozing and makes it invisible when finished
     * fading.
     */
    public void fadeKeyguardWhilePulsing() {
        mNotificationPanel.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(FADE_KEYGUARD_DURATION_PULSING)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(()-> {
                    hideKeyguard();
                    mStatusBarKeyguardViewManager.onKeyguardFadedAway();
                }).start();
    }

    /**
     * Plays the animation when an activity that was occluding Keyguard goes away.
     */
    public void animateKeyguardUnoccluding() {
        mNotificationPanel.setExpandedFraction(0f);
        animateExpandNotificationsPanel();
    }

    /**
     * Starts the timeout when we try to start the affordances on Keyguard. We usually rely that
     * Keyguard goes away via fadeKeyguardAfterLaunchTransition, however, that might not happen
     * because the launched app crashed or something else went wrong.
     */
    public void startLaunchTransitionTimeout() {
        mHandler.sendEmptyMessageDelayed(MSG_LAUNCH_TRANSITION_TIMEOUT,
                LAUNCH_TRANSITION_TIMEOUT_MS);
    }

    private void onLaunchTransitionTimeout() {
        Log.w(TAG, "Launch transition: Timeout!");
        mNotificationPanel.onAffordanceLaunchEnded();
        releaseGestureWakeLock();
        mNotificationPanel.resetViews(false /* animate */);
    }

    private void runLaunchTransitionEndRunnable() {
        if (mLaunchTransitionEndRunnable != null) {
            Runnable r = mLaunchTransitionEndRunnable;

            // mLaunchTransitionEndRunnable might call showKeyguard, which would execute it again,
            // which would lead to infinite recursion. Protect against it.
            mLaunchTransitionEndRunnable = null;
            r.run();
        }
    }

    /**
     * @return true if we would like to stay in the shade, false if it should go away entirely
     */
    public boolean hideKeyguardImpl(boolean force) {
        mIsKeyguard = false;
        Trace.beginSection("StatusBar#hideKeyguard");
        boolean staying = mStatusBarStateController.leaveOpenOnKeyguardHide();
        if (!(mStatusBarStateController.setState(StatusBarState.SHADE, force))) {
            //TODO: StatusBarStateController should probably know about hiding the keyguard and
            // notify listeners.

            // If the state didn't change, we may still need to update public mode
            mLockscreenUserManager.updatePublicMode();
        }
        if (mStatusBarStateController.leaveOpenOnKeyguardHide()) {
            if (!mStatusBarStateController.isKeyguardRequested()) {
                mStatusBarStateController.setLeaveOpenOnKeyguardHide(false);
            }
            long delay = mKeyguardMonitor.calculateGoingToFullShadeDelay();
            mNotificationPanel.animateToFullShade(delay);
            if (mDraggedDownEntry != null) {
                mDraggedDownEntry.setUserLocked(false);
                mDraggedDownEntry = null;
            }

            // Disable layout transitions in navbar for this transition because the load is just
            // too heavy for the CPU and GPU on any device.
            mNavigationBarController.disableAnimationsDuringHide(mDisplayId, delay);
        } else if (!mNotificationPanel.isCollapsing()) {
            instantCollapseNotificationPanel();
        }

        // Keyguard state has changed, but QS is not listening anymore. Make sure to update the tile
        // visibilities so next time we open the panel we know the correct height already.
        if (mQSPanel != null) {
            mQSPanel.refreshAllTiles();
        }
        mHandler.removeMessages(MSG_LAUNCH_TRANSITION_TIMEOUT);
        releaseGestureWakeLock();
        mNotificationPanel.onAffordanceLaunchEnded();
        mNotificationPanel.animate().cancel();
        mNotificationPanel.setAlpha(1f);
        ViewGroupFadeHelper.reset(mNotificationPanel);
        updateScrimController();
        Trace.endSection();
        return staying;
    }

    private void releaseGestureWakeLock() {
        if (mGestureWakeLock.isHeld()) {
            mGestureWakeLock.release();
        }
    }

    /**
     * Notifies the status bar that Keyguard is going away very soon.
     */
    public void keyguardGoingAway() {
        // Treat Keyguard exit animation as an app transition to achieve nice transition for status
        // bar.
        mKeyguardIndicationController.setVisible(false);
        mKeyguardMonitor.notifyKeyguardGoingAway(true);
        mCommandQueue.appTransitionPending(mDisplayId, true /* forced */);
        Dependency.get(PulseController.class).notifyKeyguardGoingAway();
    }

    /**
     * Notifies the status bar the Keyguard is fading away with the specified timings.
     *  @param startTime the start time of the animations in uptime millis
     * @param delay the precalculated animation delay in milliseconds
     * @param fadeoutDuration the duration of the exit animation, in milliseconds
     * @param isBypassFading is this a fading away animation while bypassing
     */
    public void setKeyguardFadingAway(long startTime, long delay, long fadeoutDuration,
            boolean isBypassFading) {
        mCommandQueue.appTransitionStarting(mDisplayId, startTime + fadeoutDuration
                        - LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION,
                LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION, true);
        mCommandQueue.recomputeDisableFlags(mDisplayId, fadeoutDuration > 0 /* animate */);
        mCommandQueue.appTransitionStarting(mDisplayId,
                    startTime - LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION,
                    LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION, true);
        mKeyguardMonitor.notifyKeyguardFadingAway(delay, fadeoutDuration, isBypassFading);
    }

    /**
     * Notifies that the Keyguard fading away animation is done.
     */
    public void finishKeyguardFadingAway() {
        mKeyguardMonitor.notifyKeyguardDoneFading();
        mScrimController.setExpansionAffectsAlpha(true);
    }

    public boolean isCurrentRoundedSameAsFw() {
        float density = Resources.getSystem().getDisplayMetrics().density;
        // Resource IDs for framework properties
        int resourceIdRadius = (int) mContext.getResources().getDimension(com.android.internal.R.dimen.rounded_corner_radius);
        int resourceIdPadding = (int) mContext.getResources().getDimension(R.dimen.rounded_corner_content_padding);
        int resourceIdSBPadding = (int) mContext.getResources().getDimension(R.dimen.status_bar_extra_padding);

        // Values on framework resources
        int cornerRadiusRes = (int) (resourceIdRadius / density);
        int contentPaddingRes = (int) (resourceIdPadding / density);
        int sbPaddingRes = (int) (resourceIdSBPadding / density);

        // Values in Settings DBs
        int cornerRadius = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SYSUI_ROUNDED_SIZE, cornerRadiusRes, UserHandle.USER_CURRENT);
        int contentPadding = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SYSUI_ROUNDED_CONTENT_PADDING, contentPaddingRes, UserHandle.USER_CURRENT);
        int sbPadding = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SYSUI_STATUS_BAR_PADDING, sbPaddingRes, UserHandle.USER_CURRENT);

        return (cornerRadiusRes == cornerRadius) && (contentPaddingRes == contentPadding) &&
                (sbPaddingRes == sbPadding);
    }

    /**
     * Switches qs tile style.
     */
     public void updateTileStyle() {
         int qsTileStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                 Settings.System.QS_TILE_STYLE, 0, mLockscreenUserManager.getCurrentUserId());
         mUiOffloadThread.submit(() -> {
              ThemeAccentUtils.updateNewTileStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), qsTileStyle);
         });
     }

    /**
     * Switches theme from light to dark and vice-versa.
     */
    protected void updateTheme() {
        haltTicker();

        // Lock wallpaper defines the color of the majority of the views, hence we'll use it
        // to set our default theme.
        final boolean lockDarkText = mColorExtractor.getNeutralColors().supportsDarkText();
        final int themeResId = lockDarkText ? R.style.Theme_SystemUI_Light : R.style.Theme_SystemUI;
        if (mContext.getThemeResId() != themeResId) {
            mContext.setTheme(themeResId);
            Dependency.get(ConfigurationController.class).notifyThemeChanged();
        }
        updateCorners();
        updateQSPanel();
    }

    private void updateSwitchStyle() {
        mUiOffloadThread.submit(() -> {
            ThemeAccentUtils.setSwitchStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), mSwitchStyle);
        });
    }

    private void updateNavbarStyle() {
        mUiOffloadThread.submit(() -> {
            ThemeAccentUtils.setNavbarStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), mNavbarStyle);
        });
    }

    private void updateCorners() {
        if (mSysuiRoundedFwvals && !isCurrentRoundedSameAsFw()) {
            float density = Resources.getSystem().getDisplayMetrics().density;
            int resourceIdRadius = (int) mContext.getResources().getDimension(com.android.internal.R.dimen.rounded_corner_radius);
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.SYSUI_ROUNDED_SIZE, (int) (resourceIdRadius / density), UserHandle.USER_CURRENT);
            int resourceIdPadding = (int) mContext.getResources().getDimension(R.dimen.rounded_corner_content_padding);
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.SYSUI_ROUNDED_CONTENT_PADDING, (int) (resourceIdPadding / density), UserHandle.USER_CURRENT);
            int resourceIdSBPadding = (int) mContext.getResources().getDimension(R.dimen.status_bar_extra_padding);
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.SYSUI_STATUS_BAR_PADDING, (int) (resourceIdSBPadding / density), UserHandle.USER_CURRENT);
        }
    }

     // Switches qs tile style back to stock.
    public void stockTileStyle() {
        mUiOffloadThread.submit(() -> {
           ThemeAccentUtils.stockNewTileStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
        });
    }
    private void updateQSPanel() {
        int userQsWallColorSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_PANEL_BG_USE_WALL, 0, UserHandle.USER_CURRENT);
        boolean setQsFromWall = userQsWallColorSetting == 1;
        if (setQsFromWall) {
            WallpaperColors systemColors = mColorExtractor
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (systemColors != null)
            {
                Color mColor = systemColors.getPrimaryColor();
                int mColorInt = mColor.toArgb();
                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.QS_PANEL_BG_COLOR_WALL, mColorInt, UserHandle.USER_CURRENT);
            } else {
                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.QS_PANEL_BG_COLOR_WALL, Color.WHITE, UserHandle.USER_CURRENT);
            }

        }
    }

    private void updateDozingState() {
        Trace.traceCounter(Trace.TRACE_TAG_APP, "dozing", mDozing ? 1 : 0);
        Trace.beginSection("StatusBar#updateDozingState");

        boolean sleepingFromKeyguard =
                mStatusBarKeyguardViewManager.isGoingToSleepVisibleNotOccluded();
        boolean wakeAndUnlock = mBiometricUnlockController.getMode()
                == BiometricUnlockController.MODE_WAKE_AND_UNLOCK;
        boolean animate = (!mDozing && mDozeServiceHost.shouldAnimateWakeup() && !wakeAndUnlock)
                || (mDozing && mDozeServiceHost.shouldAnimateScreenOff() && sleepingFromKeyguard);

        mNotificationPanel.setDozing(mDozing, animate, mWakeUpTouchLocation);
        Dependency.get(PulseController.class).setDozing(mDozing);
        updateQsExpansionEnabled();
        Trace.endSection();

    }

    public void userActivity() {
        if (mState == StatusBarState.KEYGUARD) {
            mKeyguardViewMediatorCallback.userActivity();
        }
    }

    public boolean interceptMediaKey(KeyEvent event) {
        return mState == StatusBarState.KEYGUARD
                && mStatusBarKeyguardViewManager.interceptMediaKey(event);
    }

    protected boolean shouldUnlockOnMenuPressed() {
        return mDeviceInteractive && mState != StatusBarState.SHADE
            && mStatusBarKeyguardViewManager.shouldDismissOnMenuPressed();
    }

    public boolean onMenuPressed() {
        if (shouldUnlockOnMenuPressed()) {
            animateCollapsePanels(
                    CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL /* flags */, true /* force */);
            return true;
        }
        return false;
    }

    public void endAffordanceLaunch() {
        releaseGestureWakeLock();
        mNotificationPanel.onAffordanceLaunchEnded();
    }

    public boolean onBackPressed() {
        boolean isScrimmedBouncer = mScrimController.getState() == ScrimState.BOUNCER_SCRIMMED;
        if (mStatusBarKeyguardViewManager.onBackPressed(isScrimmedBouncer /* hideImmediately */)) {
            if (!isScrimmedBouncer) {
                mNotificationPanel.expandWithoutQs();
            }
            return true;
        }
        if (mNotificationPanel.isQsExpanded()) {
            if (mNotificationPanel.isQsDetailShowing()) {
                mNotificationPanel.closeQsDetail();
            } else {
                mNotificationPanel.animateCloseQs(false /* animateAway */);
            }
            return true;
        }
        if (mState != StatusBarState.KEYGUARD && mState != StatusBarState.SHADE_LOCKED) {
            if (mNotificationPanel.canPanelBeCollapsed()) {
                animateCollapsePanels();
            } else {
                if (mBubbleController != null) {
                    mBubbleController.performBackPressIfNeeded();
                }
            }
            return true;
        }
        if (mKeyguardUserSwitcher != null && mKeyguardUserSwitcher.hideIfNotSimple(true)) {
            return true;
        }
        return false;
    }

    public boolean onSpacePressed() {
        if (mDeviceInteractive && mState != StatusBarState.SHADE) {
            animateCollapsePanels(
                    CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL /* flags */, true /* force */);
            return true;
        }
        return false;
    }

    private void showBouncerIfKeyguard() {
        if ((mState == StatusBarState.KEYGUARD || mState == StatusBarState.SHADE_LOCKED)
                && !mKeyguardViewMediator.isHiding()) {
            showBouncer(true /* scrimmed */);
        }
    }

    @Override
    public void showBouncer(boolean scrimmed) {
        mStatusBarKeyguardViewManager.showBouncer(scrimmed);
    }

    @Override
    public void instantExpandNotificationsPanel() {
        // Make our window larger and the panel expanded.
        makeExpandedVisible(true);
        mNotificationPanel.expand(false /* animate */);
        mCommandQueue.recomputeDisableFlags(mDisplayId, false /* animate */);
    }

    @Override
    public boolean closeShadeIfOpen() {
        if (!mNotificationPanel.isFullyCollapsed()) {
            mCommandQueue.animateCollapsePanels(
                    CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL, true /* force */);
            visibilityChanged(false);
            mAssistManager.hideAssist();
        }
        return false;
    }

    @Override
    public void postOnShadeExpanded(Runnable executable) {
        mNotificationPanel.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (getStatusBarWindow().getHeight() != getStatusBarHeight()) {
                            mNotificationPanel.getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                            mNotificationPanel.post(executable);
                        }
                    }
                });
    }

    private void instantCollapseNotificationPanel() {
        mNotificationPanel.instantCollapse();
        runPostCollapseRunnables();
    }

    @Override
    public void onStatePreChange(int oldState, int newState) {
        // If we're visible and switched to SHADE_LOCKED (the user dragged
        // down on the lockscreen), clear notification LED, vibration,
        // ringing.
        // Other transitions are covered in handleVisibleToUserChanged().
        if (mVisible && (newState == StatusBarState.SHADE_LOCKED
                || (((SysuiStatusBarStateController) Dependency.get(StatusBarStateController.class))
                .goingToFullShade()))) {
            clearNotificationEffects();
        }
        if (newState == StatusBarState.KEYGUARD) {
            mRemoteInputManager.onPanelCollapsed();
            maybeEscalateHeadsUp();
        }
    }

    @Override
    public void onStateChanged(int newState) {
        if (mState != newState) {
            setDismissAllVisible(true);
        }
        mState = newState;
        updateReportRejectedTouchVisibility();
        updateDozing();
        updateTheme();
        mNavigationBarController.touchAutoDim(mDisplayId);
        Trace.beginSection("StatusBar#updateKeyguardState");
        if (mState == StatusBarState.KEYGUARD) {
            mKeyguardIndicationController.setVisible(true);
            if (mKeyguardUserSwitcher != null) {
                mKeyguardUserSwitcher.setKeyguard(true,
                        mStatusBarStateController.fromShadeLocked());
            }
            if (mStatusBarView != null) mStatusBarView.removePendingHideExpandedRunnables();
            if (mAmbientIndicationContainer != null) {
                mAmbientIndicationContainer.setVisibility(View.VISIBLE);
            }
        } else {
            if (mKeyguardUserSwitcher != null) {
                mKeyguardUserSwitcher.setKeyguard(false,
                        mStatusBarStateController.goingToFullShade() ||
                                mState == StatusBarState.SHADE_LOCKED ||
                                mStatusBarStateController.fromShadeLocked());
            }
            if (mAmbientIndicationContainer != null) {
                mAmbientIndicationContainer.setVisibility(View.INVISIBLE);
            }
        }
        updateDozingState();
        checkBarModes();
        updateScrimController();
        mPresenter.updateMediaMetaData(false, mState != StatusBarState.KEYGUARD);
        Dependency.get(PulseController.class).setKeyguardShowing(mState == StatusBarState.KEYGUARD);
        
        updateKeyguardState();
        Trace.endSection();
    }

    @Override
    public void onDozingChanged(boolean isDozing) {
        Trace.beginSection("StatusBar#updateDozing");
        mDozing = isDozing;

        // Collapse the notification panel if open
        boolean dozingAnimated = mDozingRequested
                && DozeParameters.getInstance(mContext).shouldControlScreenOff();
        mNotificationPanel.resetViews(dozingAnimated);

        updateQsExpansionEnabled();
        mKeyguardViewMediator.setDozing(mDozing);

        mEntryManager.updateNotifications();
        updateDozingState();
        updateScrimController();
        updateReportRejectedTouchVisibility();
        Trace.endSection();
    }

    private void updateDozing() {
        // When in wake-and-unlock while pulsing, keep dozing state until fully unlocked.
        boolean dozing = mDozingRequested && mState == StatusBarState.KEYGUARD
                || mBiometricUnlockController.getMode()
                == BiometricUnlockController.MODE_WAKE_AND_UNLOCK_PULSING;
        // When in wake-and-unlock we may not have received a change to mState
        // but we still should not be dozing, manually set to false.
        if (mBiometricUnlockController.getMode() ==
                BiometricUnlockController.MODE_WAKE_AND_UNLOCK) {
            dozing = false;
        }

        mStatusBarStateController.setIsDozing(dozing);
    }

    private void updateKeyguardState() {
        if (mKeyguardMonitor != null) {
            mKeyguardMonitor.notifyKeyguardState(mStatusBarKeyguardViewManager.isShowing(),
                    mUnlockMethodCache.isMethodSecure(),
                    mStatusBarKeyguardViewManager.isOccluded());
        }
    }

    public void onActivationReset() {
        mKeyguardIndicationController.hideTransientIndication();
    }

    public void onTrackingStarted() {
        runPostCollapseRunnables();
    }

    public void onClosingFinished() {
        runPostCollapseRunnables();
        if (!mPresenter.isPresenterFullyCollapsed()) {
            // if we set it not to be focusable when collapsing, we have to undo it when we aborted
            // the closing
            mStatusBarWindowController.setStatusBarFocusable(true);
        }
    }

    public void onUnlockHintStarted() {
        mFalsingManager.onUnlockHintStarted();
        mKeyguardIndicationController.showTransientIndication(R.string.keyguard_unlock);
    }

    public void onHintFinished() {
        // Delay the reset a bit so the user can read the text.
        mKeyguardIndicationController.hideTransientIndicationDelayed(HINT_RESET_DELAY_MS);
    }

    public void onCameraHintStarted() {
        mFalsingManager.onCameraHintStarted();
        mKeyguardIndicationController.showTransientIndication(R.string.camera_hint);
    }

    public void onVoiceAssistHintStarted() {
        mFalsingManager.onLeftAffordanceHintStarted();
        mKeyguardIndicationController.showTransientIndication(R.string.voice_hint);
    }

    public void onPhoneHintStarted() {
        mFalsingManager.onLeftAffordanceHintStarted();
        mKeyguardIndicationController.showTransientIndication(R.string.phone_hint);
    }

    public void onCustomHintStarted() {
        mKeyguardIndicationController.showTransientIndication(R.string.custom_hint);
    }

    public void onTrackingStopped(boolean expand) {
        if (mState == StatusBarState.KEYGUARD || mState == StatusBarState.SHADE_LOCKED) {
            if (!expand && !mUnlockMethodCache.canSkipBouncer()) {
                showBouncer(false /* scrimmed */);
            }
        }
    }

    // TODO: Figure out way to remove these.
    public NavigationBarView getNavigationBarView() {
        return mNavigationBarController.getNavigationBarView(mDisplayId);
    }

    public VisualizerView getLsVisualizer() {
        return mVisualizerView;
    }

    /**
     * TODO: Remove this method. Views should not be passed forward. Will cause theme issues.
     * @return bottom area view
     */
    public KeyguardBottomAreaView getKeyguardBottomAreaView() {
        return mNotificationPanel.getKeyguardBottomAreaView();
    }

    /**
     * If secure with redaction: Show bouncer, go to unlocked shade.
     *
     * <p>If secure without redaction or no security: Go to {@link StatusBarState#SHADE_LOCKED}.</p>
     *
     * @param expandView The view to expand after going to the shade.
     */
    public void goToLockedShade(View expandView) {
        if ((mDisabled2 & StatusBarManager.DISABLE2_NOTIFICATION_SHADE) != 0) {
            return;
        }

        int userId = mLockscreenUserManager.getCurrentUserId();
        ExpandableNotificationRow row = null;
        NotificationEntry entry = null;
        if (expandView instanceof ExpandableNotificationRow) {
            entry = ((ExpandableNotificationRow) expandView).getEntry();
            entry.setUserExpanded(true /* userExpanded */, true /* allowChildExpansion */);
            // Indicate that the group expansion is changing at this time -- this way the group
            // and children backgrounds / divider animations will look correct.
            entry.setGroupExpansionChanging(true);
            if (entry.notification != null) {
                userId = entry.notification.getUserId();
            }
        }
        boolean fullShadeNeedsBouncer = !mLockscreenUserManager.
                userAllowsPrivateNotificationsInPublic(mLockscreenUserManager.getCurrentUserId())
                || !mLockscreenUserManager.shouldShowLockscreenNotifications()
                || mFalsingManager.shouldEnforceBouncer();
        if (mKeyguardBypassController.getBypassEnabled()) {
            fullShadeNeedsBouncer = false;
        }
        if (mLockscreenUserManager.isLockscreenPublicMode(userId) && fullShadeNeedsBouncer) {
            mStatusBarStateController.setLeaveOpenOnKeyguardHide(true);
            showBouncerIfKeyguard();
            mDraggedDownEntry = entry;
            mPendingRemoteInputView = null;
        } else {
            mNotificationPanel.animateToFullShade(0 /* delay */);
            mStatusBarStateController.setState(StatusBarState.SHADE_LOCKED);
        }
    }

    /**
     * Goes back to the keyguard after hanging around in {@link StatusBarState#SHADE_LOCKED}.
     */
    public void goToKeyguard() {
        if (mState == StatusBarState.SHADE_LOCKED) {
            mStatusBarStateController.setState(StatusBarState.KEYGUARD);
        }
    }

    /**
     * Propagation of the bouncer state, indicating that it's fully visible.
     */
    public void setBouncerShowing(boolean bouncerShowing) {
        mBouncerShowing = bouncerShowing;
        mKeyguardBypassController.setBouncerShowing(bouncerShowing);
        mPulseExpansionHandler.setBouncerShowing(bouncerShowing);
        mStatusBarWindow.setBouncerShowingScrimmed(isBouncerShowingScrimmed());
        if (mStatusBarView != null) mStatusBarView.setBouncerShowing(bouncerShowing);
        updateHideIconsForBouncer(true /* animate */);
        mCommandQueue.recomputeDisableFlags(mDisplayId, true /* animate */);
        updateScrimController();
        if (!mBouncerShowing) {
            updatePanelExpansionForKeyguard();
        }
    }

    /**
     * Collapses the notification shade if it is tracking or expanded.
     */
    public void collapseShade() {
        if (mNotificationPanel.isTracking()) {
            mStatusBarWindow.cancelCurrentTouch();
        }
        if (mPanelExpanded && mState == StatusBarState.SHADE) {
            animateCollapsePanels();
        }
    }

    @VisibleForTesting
    final WakefulnessLifecycle.Observer mWakefulnessObserver = new WakefulnessLifecycle.Observer() {
        @Override
        public void onFinishedGoingToSleep() {
            mNotificationPanel.onAffordanceLaunchEnded();
            releaseGestureWakeLock();
            mLaunchCameraWhenFinishedWaking = false;
            mDeviceInteractive = false;
            mWakeUpComingFromTouch = false;
            mWakeUpTouchLocation = null;
            mVisualStabilityManager.setScreenOn(false);
            updateVisibleToUser();
            updateNotificationPanelTouchState();
            mStatusBarWindow.cancelCurrentTouch();
            if (mBurnInProtectionController != null) {
                mBurnInProtectionController.stopSwiftTimer();
            }
            if (mLaunchCameraOnFinishedGoingToSleep) {
                mLaunchCameraOnFinishedGoingToSleep = false;

                // This gets executed before we will show Keyguard, so post it in order that the state
                // is correct.
                mHandler.post(() -> onCameraLaunchGestureDetected(mLastCameraLaunchSource));
            }
            // When finished going to sleep, force the status bar state to avoid stale state.
            updateIsKeyguard(true /* force */);
        }

        @Override
        public void onStartedGoingToSleep() {
            updateNotificationPanelTouchState();
            notifyHeadsUpGoingToSleep();
            dismissVolumeDialog();
            mWakeUpCoordinator.setFullyAwake(false);
            mBypassHeadsUpNotifier.setFullyAwake(false);
            mKeyguardBypassController.onStartedGoingToSleep();
        }

        @Override
        public void onStartedWakingUp() {
            mDeviceInteractive = true;
            mWakeUpCoordinator.setWakingUp(true);
            if (!mKeyguardBypassController.getBypassEnabled()) {
                mHeadsUpManager.releaseAllImmediately();
            }
            mVisualStabilityManager.setScreenOn(true);
            updateVisibleToUser();
            updateIsKeyguard();
            mDozeServiceHost.stopDozing();
            // This is intentionally below the stopDozing call above, since it avoids that we're
            // unnecessarily animating the wakeUp transition. Animations should only be enabled
            // once we fully woke up.
            updateNotificationPanelTouchState();
            mPulseExpansionHandler.onStartedWakingUp();
        }

        @Override
        public void onFinishedWakingUp() {
            mWakeUpCoordinator.setFullyAwake(true);
            mBypassHeadsUpNotifier.setFullyAwake(true);
            mWakeUpCoordinator.setWakingUp(false);
            if (mLaunchCameraWhenFinishedWaking) {
                mNotificationPanel.launchCamera(false /* animate */, mLastCameraLaunchSource);
                mLaunchCameraWhenFinishedWaking = false;
            }
            updateScrimController();
            if (mBurnInProtectionController != null) {
                mBurnInProtectionController.startSwiftTimer();
            }
        }
    };

    /**
     * We need to disable touch events because these might
     * collapse the panel after we expanded it, and thus we would end up with a blank
     * Keyguard.
     */
    private void updateNotificationPanelTouchState() {
        boolean goingToSleepWithoutAnimation = isGoingToSleep()
                && !DozeParameters.getInstance(mContext).shouldControlScreenOff();
        boolean disabled = (!mDeviceInteractive && !mPulsing) || goingToSleepWithoutAnimation;
        mNotificationPanel.setTouchAndAnimationDisabled(disabled);
        mNotificationIconAreaController.setAnimationsEnabled(!disabled);
    }

    final ScreenLifecycle.Observer mScreenObserver = new ScreenLifecycle.Observer() {
        @Override
        public void onScreenTurningOn() {
            mFalsingManager.onScreenTurningOn();
            mNotificationPanel.onScreenTurningOn();
        }

        @Override
        public void onScreenTurnedOn() {
            mScrimController.onScreenTurnedOn();
        }

        @Override
        public void onScreenTurnedOff() {
            updateDozing();
            mFalsingManager.onScreenOff();
            mScrimController.onScreenTurnedOff();
            updateIsKeyguard();
        }
    };

    public void updateQSDataUsageInfo() {
        DataUsageView.updateUsage();
    }

    private void setGestureNavOptions() {
        if (getNavigationBarView() != null) {
            getNavigationBarView().setLongSwipeOptions();
        }
    }

    public int getWakefulnessState() {
        return mWakefulnessLifecycle.getWakefulness();
    }

    private void vibrateForCameraGesture() {
        // Make sure to pass -1 for repeat so VibratorService doesn't stop us when going to sleep.
        mVibrator.vibrate(mCameraLaunchGestureVibePattern, -1 /* repeat */);
    }

    /**
     * @return true if the screen is currently fully off, i.e. has finished turning off and has
     *         since not started turning on.
     */
    public boolean isScreenFullyOff() {
        return mScreenLifecycle.getScreenState() == ScreenLifecycle.SCREEN_OFF;
    }

    @Override
    public void showScreenPinningRequest(int taskId) {
        if (mKeyguardMonitor.isShowing()) {
            // Don't allow apps to trigger this from keyguard.
            return;
        }
        // Show screen pinning request, since this comes from an app, show 'no thanks', button.
        showScreenPinningRequest(taskId, true);
    }

    public void showScreenPinningRequest(int taskId, boolean allowCancel) {
        mScreenPinningRequest.showPrompt(taskId, allowCancel);
    }

    public boolean hasActiveNotifications() {
        return !mEntryManager.getNotificationData().getActiveNotifications().isEmpty();
    }

    @Override
    public void appTransitionCancelled(int displayId) {
        if (displayId == mDisplayId) {
            getComponent(Divider.class).onAppTransitionFinished();
        }
    }

    @Override
    public void appTransitionFinished(int displayId) {
        if (displayId == mDisplayId) {
            getComponent(Divider.class).onAppTransitionFinished();
        }
    }

    @Override
    public void onCameraLaunchGestureDetected(int source) {
        mLastCameraLaunchSource = source;
        if (isGoingToSleep()) {
            if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Finish going to sleep before launching camera");
            mLaunchCameraOnFinishedGoingToSleep = true;
            return;
        }
        if (!mNotificationPanel.canCameraGestureBeLaunched(
                mStatusBarKeyguardViewManager.isShowing()
                        && (mExpandedVisible || mBouncerShowing), source)) {
            if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Can't launch camera right now, mExpandedVisible: " +
                    mExpandedVisible);
            return;
        }
        if (!mDeviceInteractive) {
            PowerManager pm = mContext.getSystemService(PowerManager.class);
            pm.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_CAMERA_LAUNCH,
                    "com.android.systemui:CAMERA_GESTURE");
        }
        if (source != StatusBarManager.CAMERA_LAUNCH_SOURCE_SCREEN_GESTURE) {
            vibrateForCameraGesture();
        }
        if (source == StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP) {
            Log.v(TAG, "Camera launch");
            mKeyguardUpdateMonitor.onCameraLaunched();
        }

        if (!mStatusBarKeyguardViewManager.isShowing()) {
            startActivityDismissingKeyguard(KeyguardBottomAreaView.INSECURE_CAMERA_INTENT,
                    false /* onlyProvisioned */, true /* dismissShade */,
                    true /* disallowEnterPictureInPictureWhileLaunching */, null /* callback */, 0);
        } else {
            if (!mDeviceInteractive) {
                // Avoid flickering of the scrim when we instant launch the camera and the bouncer
                // comes on.
                mGestureWakeLock.acquire(LAUNCH_TRANSITION_TIMEOUT_MS + 1000L);
            }
            if (isWakingUpOrAwake()) {
                if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Launching camera");
                if (mStatusBarKeyguardViewManager.isBouncerShowing()) {
                    mStatusBarKeyguardViewManager.reset(true /* hide */);
                }
                mNotificationPanel.launchCamera(mDeviceInteractive /* animate */, source);
                updateScrimController();
            } else {
                // We need to defer the camera launch until the screen comes on, since otherwise
                // we will dismiss us too early since we are waiting on an activity to be drawn and
                // incorrectly get notified because of the screen on event (which resumes and pauses
                // some activities)
                if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Deferring until screen turns on");
                mLaunchCameraWhenFinishedWaking = true;
            }
        }
    }

    boolean isCameraAllowedByAdmin() {
        if (mDevicePolicyManager.getCameraDisabled(null,
                mLockscreenUserManager.getCurrentUserId())) {
            return false;
        } else if (mStatusBarKeyguardViewManager == null ||
                (isKeyguardShowing() && isKeyguardSecure())) {
            // Check if the admin has disabled the camera specifically for the keyguard
            return (mDevicePolicyManager.
                    getKeyguardDisabledFeatures(null, mLockscreenUserManager.getCurrentUserId())
                    & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) == 0;
        }

        return true;
    }

    private boolean isGoingToSleep() {
        return mWakefulnessLifecycle.getWakefulness()
                == WakefulnessLifecycle.WAKEFULNESS_GOING_TO_SLEEP;
    }

    private boolean isWakingUpOrAwake() {
        return mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_AWAKE
                || mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_WAKING;
    }

    public void notifyBiometricAuthModeChanged() {
        updateDozing();
        updateScrimController();
        mStatusBarWindow.onBiometricAuthModeChanged(mBiometricUnlockController.isWakeAndUnlock(),
                mBiometricUnlockController.isBiometricUnlock());
    }

    @VisibleForTesting
    void updateScrimController() {
        Trace.beginSection("StatusBar#updateScrimController");

        // We don't want to end up in KEYGUARD state when we're unlocking with
        // fingerprint from doze. We should cross fade directly from black.
        boolean unlocking = mBiometricUnlockController.isWakeAndUnlock()
                || (mKeyguardMonitor != null && mKeyguardMonitor.isKeyguardFadingAway());

        // Do not animate the scrim expansion when triggered by the fingerprint sensor.
        mScrimController.setExpansionAffectsAlpha(
                !mBiometricUnlockController.isBiometricUnlock());

        boolean launchingAffordanceWithPreview =
                mNotificationPanel.isLaunchingAffordanceWithPreview();
        mScrimController.setLaunchingAffordanceWithPreview(launchingAffordanceWithPreview
                || mBiometricUnlockController.isWakeAndUnlock());

        if (mBouncerShowing) {
            // Bouncer needs the front scrim when it's on top of an activity,
            // tapping on a notification, editing QS or being dismissed by
            // FLAG_DISMISS_KEYGUARD_ACTIVITY.
            ScrimState state = mStatusBarKeyguardViewManager.bouncerNeedsScrimming()
                    ? ScrimState.BOUNCER_SCRIMMED : ScrimState.BOUNCER;
            mScrimController.transitionTo(state);
        } else if (isInLaunchTransition() || mLaunchCameraWhenFinishedWaking
                || launchingAffordanceWithPreview) {
            mScrimController.transitionTo(ScrimState.UNLOCKED, mUnlockScrimCallback);
        } else if (isPulsing()) {
            mScrimController.transitionTo(ScrimState.PULSING,
                    mDozeScrimController.getScrimCallback());
        } else if (mDozing && !unlocking) {
            mScrimController.transitionTo(ScrimState.AOD);
        } else if (mIsKeyguard && !unlocking) {
            mScrimController.transitionTo(ScrimState.KEYGUARD);
        } else if (mBubbleController != null && mBubbleController.isStackExpanded()) {
            mScrimController.transitionTo(ScrimState.BUBBLE_EXPANDED);
        } else {
            mScrimController.transitionTo(ScrimState.UNLOCKED, mUnlockScrimCallback);
        }
        Trace.endSection();
    }

    public boolean isKeyguardShowing() {
        if (mStatusBarKeyguardViewManager == null) {
            Slog.i(TAG, "isKeyguardShowing() called before startKeyguard(), returning true");
            return true;
        }
        return mStatusBarKeyguardViewManager.isShowing();
    }

    @VisibleForTesting
    final class DozeServiceHost implements DozeHost {
        private final ArrayList<Callback> mCallbacks = new ArrayList<>();
        private boolean mAnimateWakeup;
        private boolean mAnimateScreenOff;
        private boolean mIgnoreTouchWhilePulsing;
        @VisibleForTesting
        boolean mWakeLockScreenPerformsAuth = SystemProperties.getBoolean(
                "persist.sysui.wake_performs_auth", true);

        @Override
        public String toString() {
            return "PSB.DozeServiceHost[mCallbacks=" + mCallbacks.size() + "]";
        }

        public void firePowerSaveChanged(boolean active) {
            for (Callback callback : mCallbacks) {
                callback.onPowerSaveChanged(active);
            }
        }

        public void fireNotificationPulse(NotificationEntry entry) {
            Runnable pulseSupressedListener = () -> {
                entry.setPulseSuppressed(true);
                mNotificationIconAreaController.updateAodNotificationIcons();
            };
            for (Callback callback : mCallbacks) {
                callback.onNotificationAlerted(pulseSupressedListener);
            }
        }

        @Override
        public void addCallback(@NonNull Callback callback) {
            mCallbacks.add(callback);
        }

        @Override
        public void removeCallback(@NonNull Callback callback) {
            mCallbacks.remove(callback);
        }

        @Override
        public void startDozing() {
            if (!mDozingRequested) {
                mDozingRequested = true;
                DozeLog.traceDozing(mContext, mDozing);
                updateDozing();
                updateIsKeyguard();
            }else{
                mDozingRequested = true;
            }
        }

        @Override
        public void pulseWhileDozing(@NonNull PulseCallback callback, int reason) {
            if (reason == DozeLog.PULSE_REASON_SENSOR_LONG_PRESS) {
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_GESTURE,
                        "com.android.systemui:LONG_PRESS");
                startAssist(new Bundle());
                return;
            }

            if (reason == DozeLog.PULSE_REASON_SENSOR_WAKE_LOCK_SCREEN) {
                mScrimController.setWakeLockScreenSensorActive(true);
            }

            if (reason == DozeLog.PULSE_REASON_DOCKING && mStatusBarWindow != null) {
                mStatusBarWindow.suppressWakeUpGesture(true);
            }

            boolean passiveAuthInterrupt = reason == DozeLog.PULSE_REASON_SENSOR_WAKE_LOCK_SCREEN
                            && mWakeLockScreenPerformsAuth;
            // Set the state to pulsing, so ScrimController will know what to do once we ask it to
            // execute the transition. The pulse callback will then be invoked when the scrims
            // are black, indicating that StatusBar is ready to present the rest of the UI.
            mPulsing = true;
            mDozeScrimController.pulse(new PulseCallback() {
                @Override
                public void onPulseStarted() {
                    callback.onPulseStarted();
                    updateNotificationPanelTouchState();
                    setPulsing(true);
                    KeyguardUpdateMonitor.getInstance(mContext).setPulsing(true);
                }

                @Override
                public void onPulseFinished() {
                    mPulsing = false;
                    callback.onPulseFinished();
                    updateNotificationPanelTouchState();
                    mScrimController.setWakeLockScreenSensorActive(false);
                    if (mStatusBarWindow != null) {
                        mStatusBarWindow.suppressWakeUpGesture(false);
                    }
                    setPulsing(false);
                    KeyguardUpdateMonitor.getInstance(mContext).setPulsing(false);
                }

                private void setPulsing(boolean pulsing) {
                    mStatusBarStateController.setPulsing(pulsing);
                    mStatusBarKeyguardViewManager.setPulsing(pulsing);
                    mKeyguardViewMediator.setPulsing(pulsing);
                    mNotificationPanel.setPulsing(pulsing);
                    mVisualStabilityManager.setPulsing(pulsing);
                    mStatusBarWindow.setPulsing(pulsing);
                    mIgnoreTouchWhilePulsing = false;
                    if (mKeyguardUpdateMonitor != null && passiveAuthInterrupt) {
                        mKeyguardUpdateMonitor.onAuthInterruptDetected(pulsing /* active */);
                    }
                    updateScrimController();
                    mPulseExpansionHandler.setPulsing(pulsing);
                    mWakeUpCoordinator.setPulsing(pulsing);
                }
            }, reason);
            // DozeScrimController is in pulse state, now let's ask ScrimController to start
            // pulsing and draw the black frame, if necessary.
            updateScrimController();
        }

        @Override
        public void stopDozing() {
            if (mDozingRequested) {
                mDozingRequested = false;
                DozeLog.traceDozing(mContext, mDozing);
                updateDozing();
            }
        }

        @Override
        public void onIgnoreTouchWhilePulsing(boolean ignore) {
            if (ignore != mIgnoreTouchWhilePulsing) {
                DozeLog.tracePulseTouchDisabledByProx(mContext, ignore);
            }
            mIgnoreTouchWhilePulsing = ignore;
            if (isDozing() && ignore) {
                mStatusBarWindow.cancelCurrentTouch();
            }
        }

        @Override
        public void dozeTimeTick() {
            mNotificationPanel.dozeTimeTick();
            if (mAmbientIndicationContainer instanceof DozeReceiver) {
                ((DozeReceiver) mAmbientIndicationContainer).dozeTimeTick();
            }
        }

        @Override
        public boolean isPowerSaveActive() {
            return mBatteryController.isAodPowerSave();
        }

        @Override
        public boolean isPulsingBlocked() {
            return mBiometricUnlockController.getMode()
                    == BiometricUnlockController.MODE_WAKE_AND_UNLOCK;
        }

        @Override
        public boolean isProvisioned() {
            return mDeviceProvisionedController.isDeviceProvisioned()
                    && mDeviceProvisionedController.isCurrentUserSetup();
        }

        @Override
        public boolean isBlockingDoze() {
            if (mBiometricUnlockController.hasPendingAuthentication()) {
                Log.i(TAG, "Blocking AOD because fingerprint has authenticated");
                return true;
            }
            return false;
        }

        @Override
        public void extendPulse(int reason) {
            if (reason == DozeLog.PULSE_REASON_SENSOR_WAKE_LOCK_SCREEN) {
                mScrimController.setWakeLockScreenSensorActive(true);
            }
            if (mDozeScrimController.isPulsing() && mHeadsUpManager.hasNotifications()) {
                mHeadsUpManager.extendHeadsUp();
            } else {
                mDozeScrimController.extendPulse();
            }
        }

        @Override
        public void stopPulsing() {
            if (mDozeScrimController.isPulsing()) {
                mDozeScrimController.pulseOutNow();
            }
        }

        @Override
        public void setAnimateWakeup(boolean animateWakeup) {
            if (mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_AWAKE
                    || mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_WAKING) {
                // Too late to change the wakeup animation.
                return;
            }
            mAnimateWakeup = animateWakeup;
        }

        @Override
        public void setAnimateScreenOff(boolean animateScreenOff) {
            mAnimateScreenOff = animateScreenOff;
        }

        @Override
        public void onSlpiTap(float screenX, float screenY) {
            if (screenX > 0 && screenY > 0 && mAmbientIndicationContainer != null
                && mAmbientIndicationContainer.getVisibility() == View.VISIBLE) {
                mAmbientIndicationContainer.getLocationOnScreen(mTmpInt2);
                float viewX = screenX - mTmpInt2[0];
                float viewY = screenY - mTmpInt2[1];
                if (0 <= viewX && viewX <= mAmbientIndicationContainer.getWidth()
                        && 0 <= viewY && viewY <= mAmbientIndicationContainer.getHeight()) {
                    dispatchTap(mAmbientIndicationContainer, viewX, viewY);
                }
            }
        }

        @Override
        public void setDozeScreenBrightness(int value) {
            mStatusBarWindowController.setDozeScreenBrightness(value);
        }

        @Override
        public void setAodDimmingScrim(float scrimOpacity) {
            mScrimController.setAodFrontScrimAlpha(scrimOpacity);
        }

        @Override
        public void prepareForGentleWakeUp() {
            mScrimController.prepareForGentleWakeUp();
        }

        private void dispatchTap(View view, float x, float y) {
            long now = SystemClock.elapsedRealtime();
            dispatchTouchEvent(view, x, y, now, MotionEvent.ACTION_DOWN);
            dispatchTouchEvent(view, x, y, now, MotionEvent.ACTION_UP);
        }

        private void dispatchTouchEvent(View view, float x, float y, long now, int action) {
            MotionEvent ev = MotionEvent.obtain(now, now, action, x, y, 0 /* meta */);
            view.dispatchTouchEvent(ev);
            ev.recycle();
        }

        private boolean shouldAnimateWakeup() {
            return mAnimateWakeup;
        }

        public boolean shouldAnimateScreenOff() {
            return mAnimateScreenOff;
        }
    }

    public boolean shouldIgnoreTouch() {
        return isDozing() && mDozeServiceHost.mIgnoreTouchWhilePulsing;
    }

    // Begin Extra BaseStatusBar methods.

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;

    // all notifications
    protected ViewGroup mStackScroller;

    protected NotificationGroupManager mGroupManager;

    protected NotificationGroupAlertTransferHelper mGroupAlertTransferHelper;

    // handling reordering
    protected VisualStabilityManager mVisualStabilityManager;

    protected AccessibilityManager mAccessibilityManager;

    protected boolean mDeviceInteractive;

    protected boolean mVisible;

    // mScreenOnFromKeyguard && mVisible.
    private boolean mVisibleToUser;

    protected DevicePolicyManager mDevicePolicyManager;
    protected PowerManager mPowerManager;
    protected StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;

    protected KeyguardManager mKeyguardManager;
    protected DeviceProvisionedController mDeviceProvisionedController =
            Dependency.get(DeviceProvisionedController.class);

    protected NavigationBarController mNavigationBarController;
    private NavigationBarController.SystemUiVisibility mNavigationBarSystemUiVisibility;

    // UI-specific methods

    protected WindowManager mWindowManager;
    protected IWindowManager mWindowManagerService;
    private IDreamManager mDreamManager;

    protected Display mDisplay;
    private int mDisplayId;

    protected Recents mRecents;

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
    protected GestureAnywhereView mGestureAnywhereView;

    protected NotificationShelf mNotificationShelf;
    protected EmptyShadeView mEmptyShadeView;

    protected AssistManager mAssistManager;

    public boolean isDeviceInteractive() {
        return mDeviceInteractive;
    }

    public boolean isNotificationForCurrentProfiles(StatusBarNotification n) {
        final int notificationUserId = n.getUserId();
        if (DEBUG && MULTIUSER_DEBUG) {
            Log.v(TAG, String.format("%s: current userid: %d, notification userid: %d", n,
                    mLockscreenUserManager.getCurrentUserId(), notificationUserId));
        }
        return mLockscreenUserManager.isCurrentProfile(notificationUserId);
    }

    private void updateTickerAnimation() {
        mTickerAnimationMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE, 0, UserHandle.USER_CURRENT);
        if (mTicker != null) {
            mTicker.updateAnimation(mTickerAnimationMode);
        }
    }

    private void updateTickerTickDuration() {
        mTickerTickDuration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_TICK_DURATION, 3000, UserHandle.USER_CURRENT);
        if (mTicker != null) {
            mTicker.updateTickDuration(mTickerTickDuration);
        }
    }

    private final BroadcastReceiver mBannerActionBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BANNER_ACTION_CANCEL.equals(action) || BANNER_ACTION_SETUP.equals(action)) {
                NotificationManager noMan = (NotificationManager)
                        mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                noMan.cancel(com.android.internal.messages.nano.SystemMessageProto.SystemMessage.
                        NOTE_HIDDEN_NOTIFICATIONS);

                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.SHOW_NOTE_ABOUT_NOTIFICATION_HIDING, 0);
                if (BANNER_ACTION_SETUP.equals(action)) {
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                            true /* force */);
                    mContext.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_REDACTION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    );
                }
            }
        }
    };

    @Override
    public void collapsePanel(boolean animate) {
        if (animate) {
            boolean willCollapse = collapsePanel();
            if (!willCollapse) {
                runPostCollapseRunnables();
            }
        } else if (!mPresenter.isPresenterFullyCollapsed()) {
            instantCollapseNotificationPanel();
            visibilityChanged(false);
        } else {
            runPostCollapseRunnables();
        }
    }

    @Override
    public boolean collapsePanel() {
        if (!mNotificationPanel.isFullyCollapsed()) {
            // close the shade if it was open
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL, true /* force */,
                    true /* delayed */);
            visibilityChanged(false);

            return true;
        } else {
            return false;
        }
    }

    protected NotificationListener mNotificationListener;

    public void setNotificationSnoozed(StatusBarNotification sbn, SnoozeOption snoozeOption) {
        if (snoozeOption.getSnoozeCriterion() != null) {
            mNotificationListener.snoozeNotification(sbn.getKey(),
                    snoozeOption.getSnoozeCriterion().getId());
        } else {
            mNotificationListener.snoozeNotification(sbn.getKey(),
                    snoozeOption.getMinutesToSnoozeFor() * 60 * 1000);
        }
    }

    @Override
    public void toggleSplitScreen() {
        toggleSplitScreenMode(-1 /* metricsDockAction */, -1 /* metricsUndockAction */);
    }

    void awakenDreams() {
        Dependency.get(UiOffloadThread.class).submit(() -> {
            try {
                mDreamManager.awaken();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void preloadRecentApps() {
        int msg = MSG_PRELOAD_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void cancelPreloadRecentApps() {
        int msg = MSG_CANCEL_PRELOAD_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void dismissKeyboardShortcutsMenu() {
        int msg = MSG_DISMISS_KEYBOARD_SHORTCUTS_MENU;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleKeyboardShortcutsMenu(int deviceId) {
        int msg = MSG_TOGGLE_KEYBOARD_SHORTCUTS_MENU;
        mHandler.removeMessages(msg);
        mHandler.obtainMessage(msg, deviceId, 0).sendToTarget();
    }

    @Override
    public void setTopAppHidesStatusBar(boolean topAppHidesStatusBar) {
        mTopHidesStatusBar = topAppHidesStatusBar;
        if (!topAppHidesStatusBar && mWereIconsJustHidden) {
            // Immediately update the icon hidden state, since that should only apply if we're
            // staying fullscreen.
            mWereIconsJustHidden = false;
            mCommandQueue.recomputeDisableFlags(mDisplayId, true);
        }
        updateHideIconsForBouncer(true /* animate */);
    }

    public void restartUI() {
        Log.d(TAG, "StatusBar API restartUI! Commiting suicide! Goodbye cruel world!");
        Process.killProcess(Process.myPid());
    }

    protected void toggleKeyboardShortcuts(int deviceId) {
        KeyboardShortcuts.toggle(mContext, deviceId);
    }

    protected void dismissKeyboardShortcuts() {
        KeyboardShortcuts.dismiss();
    }

    /**
     * Called when the notification panel layouts
     */
    public void onPanelLaidOut() {
        updateKeyguardMaxNotifications();
    }

    public void updateKeyguardMaxNotifications() {
        if (mState == StatusBarState.KEYGUARD) {
            // Since the number of notifications is determined based on the height of the view, we
            // need to update them.
            int maxBefore = mPresenter.getMaxNotificationsWhileLocked(false /* recompute */);
            int maxNotifications = mPresenter.getMaxNotificationsWhileLocked(true /* recompute */);
            if (maxBefore != maxNotifications) {
                mViewHierarchyManager.updateRowStates();
            }
        }
    }

    public void executeActionDismissingKeyguard(Runnable action, boolean afterKeyguardGone) {
        if (!mDeviceProvisionedController.isDeviceProvisioned()) return;

        dismissKeyguardThenExecute(() -> {
            new Thread(() -> {
                try {
                    // The intent we are sending is for the application, which
                    // won't have permission to immediately start an activity after
                    // the user switches to home.  We know it is safe to do at this
                    // point, so make sure new activity switches are now allowed.
                    ActivityManager.getService().resumeAppSwitches();
                } catch (RemoteException e) {
                }
                action.run();
            }).start();

            return collapsePanel();
        }, afterKeyguardGone);
    }

    @Override
    public void startPendingIntentDismissingKeyguard(final PendingIntent intent) {
        startPendingIntentDismissingKeyguard(intent, null);
    }

    @Override
    public void startPendingIntentDismissingKeyguard(
            final PendingIntent intent, @Nullable final Runnable intentSentUiThreadCallback) {
        startPendingIntentDismissingKeyguard(intent, intentSentUiThreadCallback, null /* row */);
    }

    @Override
    public void startPendingIntentDismissingKeyguard(
            final PendingIntent intent, @Nullable final Runnable intentSentUiThreadCallback,
            View associatedView) {
        final boolean afterKeyguardGone = intent.isActivity()
                && mActivityIntentHelper.wouldLaunchResolverActivity(intent.getIntent(),
                mLockscreenUserManager.getCurrentUserId());

        executeActionDismissingKeyguard(() -> {
            try {
                intent.send(null, 0, null, null, null, null, getActivityOptions(
                        mActivityLaunchAnimator.getLaunchAnimation(associatedView,
                                mShadeController.isOccluded())));
            } catch (PendingIntent.CanceledException e) {
                // the stack trace isn't very helpful here.
                // Just log the exception message.
                Log.w(TAG, "Sending intent failed: " + e);

                // TODO: Dismiss Keyguard.
            }
            if (intent.isActivity()) {
                mAssistManager.hideAssist();
            }
            if (intentSentUiThreadCallback != null) {
                postOnUiThread(intentSentUiThreadCallback);
            }
        }, afterKeyguardGone);
    }

    private void postOnUiThread(Runnable runnable) {
        mMainThreadHandler.post(runnable);
    }

    public static Bundle getActivityOptions(@Nullable RemoteAnimationAdapter animationAdapter) {
        ActivityOptions options;
        if (animationAdapter != null) {
            options = ActivityOptions.makeRemoteAnimation(animationAdapter);
        } else {
            options = ActivityOptions.makeBasic();
        }
        // Anything launched from the notification shade should always go into the secondary
        // split-screen windowing mode.
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
        return options.toBundle();
    }

    protected void visibilityChanged(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            if (!visible) {
                mGutsManager.closeAndSaveGuts(true /* removeLeavebehind */, true /* force */,
                        true /* removeControls */, -1 /* x */, -1 /* y */, true /* resetMenu */);
            }
        }
        updateVisibleToUser();
    }

    protected void updateVisibleToUser() {
        boolean oldVisibleToUser = mVisibleToUser;
        mVisibleToUser = mVisible && mDeviceInteractive;

        if (oldVisibleToUser != mVisibleToUser) {
            handleVisibleToUserChanged(mVisibleToUser);
        }
    }

    /**
     * Clear Buzz/Beep/Blink.
     */
    public void clearNotificationEffects() {
        try {
            mBarService.clearNotificationEffects();
        } catch (RemoteException e) {
            // Won't fail unless the world has ended.
        }
    }

    protected void notifyHeadsUpGoingToSleep() {
        maybeEscalateHeadsUp();
    }

    /**
     * @return Whether the security bouncer from Keyguard is showing.
     */
    public boolean isBouncerShowing() {
        return mBouncerShowing;
    }

    /**
     * @return Whether the security bouncer from Keyguard is showing.
     */
    public boolean isBouncerShowingScrimmed() {
        return isBouncerShowing() && mStatusBarKeyguardViewManager.bouncerNeedsScrimming();
    }

    /**
     * @return a PackageManger for userId or if userId is < 0 (USER_ALL etc) then
     *         return PackageManager for mContext
     */
    public static PackageManager getPackageManagerForUser(Context context, int userId) {
        Context contextForUser = context;
        // UserHandle defines special userId as negative values, e.g. USER_ALL
        if (userId >= 0) {
            try {
                // Create a context for the correct user so if a package isn't installed
                // for user 0 we can still load information about the package.
                contextForUser =
                        context.createPackageContextAsUser(context.getPackageName(),
                        Context.CONTEXT_RESTRICTED,
                        new UserHandle(userId));
            } catch (NameNotFoundException e) {
                // Shouldn't fail to find the package name for system ui.
            }
        }
        return contextForUser.getPackageManager();
    }

    public boolean isKeyguardSecure() {
        if (mStatusBarKeyguardViewManager == null) {
            // startKeyguard() hasn't been called yet, so we don't know.
            // Make sure anything that needs to know isKeyguardSecure() checks and re-checks this
            // value onVisibilityChanged().
            Slog.w(TAG, "isKeyguardSecure() called before startKeyguard(), returning false",
                    new Throwable());
            return false;
        }
        return mStatusBarKeyguardViewManager.isSecure();
    }

    @Override
    public void showAssistDisclosure() {
        if (mAssistManager != null) {
            mAssistManager.showDisclosure();
        }
    }

    public NotificationPanelView getPanel() {
        return mNotificationPanel;
    }

    @Override
    public void startAssist(Bundle args) {
        if (mAssistManager != null) {
            mAssistManager.startAssist(args);
        }
    }

    public void updateDataUsage() {
        if (!dataupdated && !mIsKeyguard) {
            DataUsageView.updateUsage();
            dataupdated = true;
        }
        if (mIsKeyguard) {
            dataupdated = false;
        }
    }

    public void updateBlurVisibility() {
        int QSUserAlpha = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_BACKGROUND_BLUR_ALPHA, 100);
        float QSBlurAlpha = mNotificationPanel.getExpandedFraction() * (float)((float) QSUserAlpha / 100.0);
        boolean enoughBlurData = (QSBlurAlpha > 0 && qsBlurIntensity() > 0);

        if (enoughBlurData && !blurperformed && !mIsKeyguard && mQSBlurEnabled) {
            drawBlurView();
            blurperformed = true;
            mQSBlurView.setVisibility(View.VISIBLE);
        } else if (!enoughBlurData || mState == StatusBarState.KEYGUARD) {
            blurperformed = false;
            mQSBlurView.setVisibility(View.GONE);
        }
        mQSBlurView.setAlpha(QSBlurAlpha);
    }

    private void drawBlurView() {
        Bitmap bitMap;
        int color = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_PANEL_FILTER_COLOR,  0xffffffff,
                    UserHandle.USER_CURRENT);
        Resources res = mContext.getResources();
        switch (mBackgroundFilter) {
                case 0: // Blur
                default:
                    bitMap = ImageHelper.getBlurredImage(mContext,
                            ImageUtilities.screenshotSurface(mContext), qsBlurIntensity());
                    break;
                case 1: // Garyscale
                    bitMap = ImageHelper.toGrayscale(
                            ImageUtilities.screenshotSurface(mContext));
                    break;
                case 2: // Tint
                    Drawable bittemp = new BitmapDrawable(res,
                            ImageUtilities.screenshotSurface(mContext));
                    bitMap = ImageHelper.getColoredBitmap(bittemp,
                            res.getColor(R.color.accent_device_default_light));
                    break;
                case 3: // Gray blur
                    bitMap = ImageHelper.getGrayscaleBlurredImage(mContext,
                            ImageUtilities.screenshotSurface(mContext), qsBlurIntensity());
                    break;
                case 4: // Tinted blur
                    Drawable bitMapDrawable = new BitmapDrawable(res,
                            ImageUtilities.screenshotSurface(mContext));
                    bitMap = ImageHelper.getColoredBitmap(bitMapDrawable,
                            res.getColor(R.color.accent_device_default_light));
                    bitMap = ImageHelper.getBlurredImage(mContext, bitMap, qsBlurIntensity());
                    break;
                case 5: // Tint custom
                    Drawable bittemp1 = new BitmapDrawable(res,
                            ImageUtilities.screenshotSurface(mContext));
                    bitMap = ImageHelper.getColoredBitmap(bittemp1,
                            color);
                    break;
                case 6: // Tinted blur custom
                    Drawable bitMapDrawable1 = new BitmapDrawable(res,
                            ImageUtilities.screenshotSurface(mContext));
                    bitMap = ImageHelper.getColoredBitmap(bitMapDrawable1,
                             color);
                    bitMap = ImageHelper.getBlurredImage(mContext, bitMap, qsBlurIntensity());
                    break;
            }
        if (bitMap == null) {
            mQSBlurView.setImageDrawable(null);
        } else {
            mQSBlurView.setImageBitmap(bitMap);
        }
    }

    private float qsBlurIntensity() {
        return 0.25f * ((float) Settings.System.getIntForUser(mContext.getContentResolver(),
               Settings.System.QS_BACKGROUND_BLUR_INTENSITY, 30,
               UserHandle.USER_CURRENT));
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (SCREEN_BRIGHTNESS_MODE.equals(key)) {
            try {
                mAutomaticBrightness = newValue != null && Integer.parseInt(newValue)
                        == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            } catch (NumberFormatException ex) {}
        } else if (STATUS_BAR_BRIGHTNESS_CONTROL.equals(key)) {
            mBrightnessControl = TunerService.parseIntegerSwitch(newValue, false);
        } else if (BERRY_SWITCH_STYLE.equals(key)) {
                int switchStyle =
                        TunerService.parseInteger(newValue, 0);
                if (mSwitchStyle != switchStyle) {
                    mSwitchStyle = switchStyle;
                    updateSwitchStyle();
                }
        } else if (SYSUI_ROUNDED_FWVALS.equals(key)) {
                mSysuiRoundedFwvals =
                        TunerService.parseIntegerSwitch(newValue, true);
                updateCorners();
        }   else if (DISPLAY_CUTOUT_MODE.equals(key)) {
                int immerseMode =
                        TunerService.parseInteger(newValue, 0);
                if (mImmerseMode != immerseMode) {
                    mImmerseMode = immerseMode;
                    handleCutout();
                }
        } else if (STOCK_STATUSBAR_IN_HIDE.equals(key)) {
                boolean stockStatusBar =
                        TunerService.parseIntegerSwitch(newValue, true);
                if (mStockStatusBar != stockStatusBar) {
                    mStockStatusBar = stockStatusBar;
                    handleCutout();
                }
        } else if (NAVBAR_STYLE.equals(key)) {
                int navbarStyle =
                        TunerService.parseInteger(newValue, 0);
                if (mNavbarStyle != navbarStyle) {
                    mNavbarStyle = navbarStyle;
                    updateNavbarStyle();
                }
        }
 
    }

    // End Extra BaseStatusBarMethods.

    public NotificationGutsManager getGutsManager() {
        return mGutsManager;
    }

    @Subcomponent
    public interface StatusBarInjector {
        void createStatusBar(StatusBar statusbar);
    }

    public @TransitionMode int getStatusBarMode() {
        return mStatusBarMode;
    }

    private void setCutoutOverlay(boolean enable) {
        try {
            mOverlayManager.setEnabled("com.android.overlay.hidecutout",
                        enable, mLockscreenUserManager.getCurrentUserId());
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to handle cutout overlay", e);
        }
    }

    private void setStatusBarStockOverlay(boolean enable) {
        try {
            mOverlayManager.setEnabled("com.android.overlay.statusbarstock",
                        enable, mLockscreenUserManager.getCurrentUserId());
            mOverlayManager.setEnabled("com.android.overlay.statusbarstocksysui",
                        enable, mLockscreenUserManager.getCurrentUserId());
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to handle statusbar height overlay", e);
        }
    }

    private void setImmersiveOverlay(boolean enable) {
        try {
            mOverlayManager.setEnabled("com.android.overlay.immersive", enable, mLockscreenUserManager.getCurrentUserId());
        } catch (RemoteException e) {
        }
    }

    private void setUseLessBoringHeadsUp() {
        boolean lessBoringHeadsUp = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LESS_BORING_HEADS_UP, 0,
                UserHandle.USER_CURRENT) == 1;
        mNotificationInterruptionStateProvider.setUseLessBoringHeadsUp(lessBoringHeadsUp);
    }

    private void updateHeadsUpBlackList() {
        final String blackString = Settings.System.getString(mContext.getContentResolver(),
              Settings.System.HEADS_UP_BLACKLIST_VALUES);
        if (DEBUG) Log.v(TAG, "blackString: " + blackString);
        final ArrayList<String> blackList = new ArrayList<String>();
        splitAndAddToArrayList(blackList, blackString, "\\|");
        mNotificationInterruptionStateProvider.setHeadsUpBlacklist(blackList);
    }

    private void splitAndAddToArrayList(ArrayList<String> arrayList,
            String baseString, String separator) {
        // clear first
        arrayList.clear();
        if (baseString != null) {
            final String[] array = TextUtils.split(baseString, separator);
            for (String item : array) {
                arrayList.add(item.trim());
            }
        }
    }

    private void setMaxKeyguardNotifConfig() {
        mMaxKeyguardNotifConfig = Settings.System.getIntForUser(mContext.getContentResolver(),
                 Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 3, UserHandle.USER_CURRENT);
        mPresenter.setMaxAllowedNotifUser(mMaxKeyguardNotifConfig);
    }

    private void setGamingModeActive() {
        mGamingModeActivated = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.GAMING_MODE_ACTIVE, 0, UserHandle.USER_CURRENT) == 1;
        mNotificationInterruptionStateProvider.setGamingPeekMode(mGamingModeActivated && mHeadsUpDisabled);
    }

    private void setGamingModeHeadsupToggle() {
        mHeadsUpDisabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.GAMING_MODE_HEADSUP_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
        mNotificationInterruptionStateProvider.setGamingPeekMode(mGamingModeActivated && mHeadsUpDisabled);
    }

    private void setHideArrowForBackGesture() {
        if (getNavigationBarView() != null) {
            getNavigationBarView().updateBackArrowForGesture();
        }
    }

    private RegisterStatusBarResult getRegisterStatusBarResult() {
        RegisterStatusBarResult result = null;
        try {
            result = mBarService.registerStatusBar(mCommandQueue);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
        return result;
    }

    private void updateNavigationBar(@Nullable RegisterStatusBarResult result, boolean init) {
        boolean showNavBar = RRUtils.deviceSupportNavigationBar(mContext);
        if (init) {
            if (showNavBar) {
                mNavigationBarController.createNavigationBars(true, result);
            }
        } else {
            if (showNavBar != mShowNavBar) {
                if (showNavBar) {
                    mNavigationBarController.recreateNavigationBars(true, result, mNavigationBarSystemUiVisibility);
                } else {
                    mNavigationBarController.removeNavigationBar(mDisplayId);
                }
            }
        }
        mShowNavBar = showNavBar;
   }

    private void setBlackStatusBar(boolean enable) {
        if (mStatusBarWindow == null || mStatusBarWindow.getBarTransitions() == null) return;
        if (enable) {
            mStatusBarWindow.getBarTransitions().getBackground().setColorOverride(new Integer(0xFF000000));
        } else {
            mStatusBarWindow.getBarTransitions().getBackground().setColorOverride(null);
        }
    }

    private void handleCutout() {
        final boolean immerseMode = mImmerseMode == 1;
        final boolean hideCutoutMode = mImmerseMode == 2;

        final boolean statusBarStock = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.STOCK_STATUSBAR_IN_HIDE, 1, UserHandle.USER_CURRENT) == 1;
        setBlackStatusBar(mPortrait && immerseMode);
        setImmersiveOverlay(immerseMode || hideCutoutMode);
        setCutoutOverlay(hideCutoutMode);
        setStatusBarStockOverlay(hideCutoutMode && statusBarStock);
    }

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_METHOD)
    protected void addGestureAnywhereView() {
        mGestureAnywhereView = (GestureAnywhereView)View.inflate(
                mContext, R.layout.gesture_anywhere_overlay, null);
        mWindowManager.addView(mGestureAnywhereView, getGestureAnywhereViewLayoutParams(Gravity.LEFT));
        mGestureAnywhereView.setStatusBar(this);
    }

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_METHOD)
    protected void removeGestureAnywhereView() {
        if (mGestureAnywhereView != null)
            mWindowManager.removeView(mGestureAnywhereView);
    }

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_METHOD)
    protected WindowManager.LayoutParams getGestureAnywhereViewLayoutParams(int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
        lp.gravity = Gravity.TOP | gravity;
        lp.setTitle("GestureAnywhereView");

        return lp;
    }


    public void updateSystemTheme() {
          int mSystemTheme = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SYSTEM_THEME, 0, UserHandle.USER_CURRENT);
               switchThemes(mSystemTheme);
    }


    public void switchThemes(int theme) {
        switch (theme) {
            case 0:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
                updateTheme();
           break;
           case 1:
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 2:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);;
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
          case 3:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 4:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 5:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 6:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 7:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 8:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
           case 9:
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.SOLARIZED_DARK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.BAKED_GREEN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.CHOCO_X);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_GREY);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.MATERIAL_OCEAN);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PITCH_BLACK);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.DARK_THEMES);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR);
                RRUtils.disableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.PRIMARY_THEMES);
                RRUtils.enableSystemTheme(mOverlayManager, UserHandle.USER_CURRENT, ThemeAccentUtils.RR_CLEAR2);
           break;
          }
     }

    // Switches qs header style from stock to custom
    public void updateQSHeaderStyle() {
        int qsHeaderStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_HEADER_STYLE, 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateQSHeaderStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), qsHeaderStyle);
    }

    // Unload all qs header styles back to stock
    public void stockQSHeaderStyle() {
        ThemeAccentUtils.stockQSHeaderStyle(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void updateAnalogClockSize() {
        int analogstyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ANALOG_CLOCK_SIZE , 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateAnalogClockSize(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), analogstyle);
    }

    // Unload all clock sizes stock
    public void stockAnalogClockSize() {
        ThemeAccentUtils.stockAnalogClockSize(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void updateVolumeIconTint() {
        int style = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.VOLUME_PANEL_ICON_TINT , 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateVolumeIcon(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), style);
    }

    // Unload volume panel icon tint
    public void stockVolumeIconTint() {
        ThemeAccentUtils.stockVolumeIcon(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void updateVolumeSlider() {
        int style = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.VOLUME_SLIDER_COLOR , 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateVolumeSlider(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), style);
    }

    /// Unload volume panel slider tint
    public void stockVolumeSlider() {
        ThemeAccentUtils.stockVolumeSlider(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void updateSystemUIIcons() {
        int style = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SYSTEMUI_ICON_PACK , 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateSystemUIIcons(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), style);
    }

    public void stockSystemUIIcons() {
        ThemeAccentUtils.stockSystemUIIcons(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void stockSettingsIcons() {
        ThemeAccentUtils.stockSettingsIcons(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void updateSettingsIcons() {
        int style = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SETTINGS_ICON_PACK , 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateSettingsIcons(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), style);
    }

    public void updateSearchBarColor() {
        int style = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SETTINGS_SEARCHBAR_COLOR, 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateSearchBarColor(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), style);
    }

    public void stockSearchBarColor() {
        ThemeAccentUtils.stockSearchBarColor(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void stockSearchBarRadius() {
        ThemeAccentUtils.stockSearchBarRadius(mOverlayManager, mLockscreenUserManager.getCurrentUserId());
    }

    public void updateSearchBarRadius() {
        int style = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SETTINGS_SEARCHBAR_RADIUS , 0, mLockscreenUserManager.getCurrentUserId());
        ThemeAccentUtils.updateSearchBarRadius(mOverlayManager, mLockscreenUserManager.getCurrentUserId(), style);
    }
}
