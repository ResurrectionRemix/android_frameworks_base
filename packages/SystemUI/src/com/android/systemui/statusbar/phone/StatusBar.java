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

import static android.app.StatusBarManager.WINDOW_STATE_HIDDEN;
import static android.app.StatusBarManager.WINDOW_STATE_SHOWING;
import static android.app.StatusBarManager.windowStateToString;

import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_ASLEEP;
import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_AWAKE;
import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_WAKING;
import static com.android.systemui.statusbar.notification.NotificationInflater.InflationCallback;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_OPAQUE;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSLUCENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_WARNING;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_POWERSAVE_WARNING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import android.app.Activity;
import android.animation.TimeInterpolator;
import com.android.systemui.chaos.lab.gestureanywhere.GestureAnywhereView;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityManager.StackId;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.StatusBarManager;
import android.app.TaskStackBuilder;
import android.app.UiModeManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.metrics.LogMaker;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.service.vr.IVrManager;
import android.service.vr.IVrStateCallbacks;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.Pair;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ThreadedRenderer;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.DateTimeView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.NotificationVisibility;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.NotificationMessagingUtil;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.DUPackageMonitor;
import com.android.internal.utils.du.DUPackageMonitor.PackageChangedListener;
import com.android.internal.utils.du.DUPackageMonitor.PackageState;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardHostView.OnDismissAction;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.ActivityStarterDelegate;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.DejankUtils;
import com.android.systemui.DemoMode;
import com.android.systemui.Dependency;
import com.android.systemui.EventLogTags;
import com.android.systemui.ForegroundServiceController;
import com.android.systemui.Interpolators;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.RecentsComponent;
import com.android.systemui.SwipeHelper;
import com.android.systemui.SystemUI;
import com.android.systemui.SystemUIFactory;
import com.android.systemui.UiOffloadThread;
import com.android.systemui.ambientmusic.AmbientIndicationContainer;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.classifier.FalsingLog;
import com.android.systemui.classifier.FalsingManager;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.doze.DozeHost;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.fragments.ExtensionFragmentListener;
import com.android.systemui.fragments.FragmentHostManager;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.navigation.Navigator;
import com.android.systemui.omni.StatusBarHeaderMachine;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QS;
import com.android.systemui.plugins.statusbar.NotificationMenuRowPlugin.MenuItem;
import com.android.systemui.plugins.statusbar.NotificationSwipeActionHelper.SnoozeOption;
import com.android.systemui.qs.QSFragment;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.qs.QuickStatusBarHeader;
import com.android.systemui.qs.car.CarQSFragment;
import com.android.systemui.qs.QuickStatusBarHeader;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.RecentsActivity;
import com.android.systemui.recents.ScreenPinningRequest;
import com.android.systemui.recents.events.EventBus;
import com.android.systemui.recents.events.activity.AppTransitionFinishedEvent;
import com.android.systemui.recents.events.activity.UndockingTaskEvent;
import com.android.systemui.recents.misc.IconPackHelper;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.slimrecent.RecentController;
import com.android.systemui.stackdivider.Divider;
import com.android.systemui.stackdivider.WindowManagerProxy;
import com.android.systemui.statusbar.ActivatableNotificationView;
import com.android.systemui.statusbar.BackDropView;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.DismissView;
import com.android.systemui.statusbar.DragDownHelper;
import com.android.systemui.statusbar.EmptyShadeView;
import com.android.systemui.statusbar.ExpandableNotificationRow;
import com.android.systemui.statusbar.GestureRecorder;
import com.android.systemui.statusbar.KeyboardShortcuts;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationData.Entry;
import com.android.systemui.statusbar.appcirclesidebar.AppCircleSidebar;
import com.android.systemui.statusbar.NotificationGuts;
import com.android.systemui.statusbar.NotificationInfo;
import com.android.systemui.statusbar.NotificationShelf;
import com.android.systemui.statusbar.NotificationSnooze;
import com.android.systemui.statusbar.RemoteInputController;
import com.android.systemui.statusbar.ScrimView;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.notification.AboveShelfObserver;
import com.android.systemui.statusbar.VisualizerView;
import com.android.systemui.statusbar.notification.InflationException;
import com.android.systemui.statusbar.notification.RowInflaterTask;
import com.android.systemui.statusbar.notification.VisualStabilityManager;
import com.android.systemui.statusbar.phone.Ticker;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.systemui.statusbar.phone.UnlockMethodCache.OnUnlockMethodChangedListener;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController.DeviceProvisionedListener;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.KeyguardMonitorImpl;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcher;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.pie.PieController;
import com.android.systemui.statusbar.policy.OnHeadsUpChangedListener;
import com.android.systemui.statusbar.policy.PreviewInflater;
import com.android.systemui.statusbar.policy.RemoteInputView;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoControllerImpl;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout
        .OnChildLocationsChangedListener;
import com.android.systemui.statusbar.stack.StackStateAnimator;
import com.android.systemui.statusbar.NotificationBackgroundView;
import com.android.systemui.util.NotificationChannels;
import com.android.systemui.util.leak.LeakDetector;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import com.android.systemui.volume.VolumeComponent;

import java.lang.reflect.Field;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import lineageos.hardware.LiveDisplayManager;
import lineageos.providers.LineageSettings;

public class StatusBar extends SystemUI implements DemoMode,
        DragDownHelper.DragDownCallback, ActivityStarter, OnUnlockMethodChangedListener,
        OnHeadsUpChangedListener, VisualStabilityManager.Callback, CommandQueue.Callbacks,
        ActivatableNotificationView.OnActivatedListener,
        ExpandableNotificationRow.ExpansionLogger, NotificationData.Environment,
        ExpandableNotificationRow.OnExpandClickListener, InflationCallback,
        ColorExtractor.OnColorsChangedListener, ConfigurationListener, Tunable, PackageChangedListener  {
    public static final boolean MULTIUSER_DEBUG = false;

    public static final boolean ENABLE_REMOTE_INPUT =
            SystemProperties.getBoolean("debug.enable_remote_input", true);
    public static final boolean ENABLE_CHILD_NOTIFICATIONS
            = SystemProperties.getBoolean("debug.child_notifs", true);
    public static final boolean FORCE_REMOTE_INPUT_HISTORY =
            SystemProperties.getBoolean("debug.force_remoteinput_history", false);
    private static boolean ENABLE_LOCK_SCREEN_ALLOW_REMOTE_INPUT = false;

    protected static final int MSG_SHOW_RECENT_APPS = 1019;
    protected static final int MSG_HIDE_RECENT_APPS = 1020;
    protected static final int MSG_TOGGLE_RECENTS_APPS = 1021;
    protected static final int MSG_PRELOAD_RECENT_APPS = 1022;
    protected static final int MSG_CANCEL_PRELOAD_RECENT_APPS = 1023;
    protected static final int MSG_TOGGLE_KEYBOARD_SHORTCUTS_MENU = 1026;
    protected static final int MSG_DISMISS_KEYBOARD_SHORTCUTS_MENU = 1027;

    protected static final boolean ENABLE_HEADS_UP = true;
    protected static final String SETTING_HEADS_UP_TICKER = "ticker_gets_heads_up";

    // Must match constant in Settings. Used to highlight preferences when linking to Settings.
    private static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";

    private static final String PERMISSION_SELF = "com.android.systemui.permission.SELF";

    // Should match the values in PhoneWindowManager
    public static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_SCREENSHOT = "screenshot";

    private static final String BANNER_ACTION_CANCEL =
            "com.android.systemui.statusbar.banner_action_cancel";
    private static final String BANNER_ACTION_SETUP =
            "com.android.systemui.statusbar.banner_action_setup";
    private static final String NOTIFICATION_UNLOCKED_BY_WORK_CHALLENGE_ACTION
            = "com.android.systemui.statusbar.work_challenge_unlocked_notification_action";
    public static final String TAG = "StatusBar";
    public static final boolean DEBUG = false;
    public static final boolean SPEW = false;
    public static final boolean DUMPTRUCK = true; // extra dumpsys info
    public static final boolean DEBUG_GESTURES = false;
    public static final boolean DEBUG_MEDIA = false;
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

    private static final boolean CLOSE_PANEL_WHEN_EMPTIED = true;

    private static final int STATUS_OR_NAV_TRANSIENT =
            View.STATUS_BAR_TRANSIENT | View.NAVIGATION_BAR_TRANSIENT;
    private static final long AUTOHIDE_TIMEOUT_MS = 2250;

    /** The minimum delay in ms between reports of notification visibility. */
    private static final int VISIBILITY_REPORT_MIN_DELAY_MS = 500;

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

    public static final int FADE_KEYGUARD_START_DELAY = 100;
    public static final int FADE_KEYGUARD_DURATION = 300;
    public static final int FADE_KEYGUARD_DURATION_PULSING = 96;

    /** If true, the system is in the half-boot-to-decryption-screen state.
     * Prudently disable QS and notifications.  */
    private static final boolean ONLY_CORE_APPS;

    /** If true, the lockscreen will show a distinct wallpaper */
    private static final boolean ENABLE_LOCKSCREEN_WALLPAPER = true;

    /* If true, the device supports freeform window management.
     * This affects the status bar UI. */
    private static final boolean FREEFORM_WINDOW_MANAGEMENT;

    private static final String STATUS_BAR_BATTERY_SAVER_COLOR =
            Settings.Secure.STATUS_BAR_BATTERY_SAVER_COLOR;

    /**
     * How long to wait before auto-dismissing a notification that was kept for remote input, and
     * has now sent a remote input. We auto-dismiss, because the app may not see a reason to cancel
     * these given that they technically don't exist anymore. We wait a bit in case the app issues
     * an update.
     */
    private static final int REMOTE_INPUT_KEPT_ENTRY_AUTO_CANCEL_DELAY = 200;

    /**
     * Never let the alpha become zero for surfaces that draw with SRC - otherwise the RenderNode
     * won't draw anything and uninitialized memory will show through
     * if mScrimSrcModeEnabled. Note that 0.001 is rounded down to 0 in
     * libhwui.
     */
    private static final float SRC_MIN_ALPHA = 0.002f;

    private static final String SCREEN_BRIGHTNESS_MODE =
            "system:" + Settings.System.SCREEN_BRIGHTNESS_MODE;
    private static final String STATUS_BAR_BRIGHTNESS_CONTROL =
            "lineagesystem:" + LineageSettings.System.STATUS_BAR_BRIGHTNESS_CONTROL;
    private static final String LOCKSCREEN_MEDIA_METADATA =
            "lineagesecure:" + LineageSettings.Secure.LOCKSCREEN_MEDIA_METADATA;

    static {
        boolean onlyCoreApps;
        boolean freeformWindowManagement;
        try {
            IPackageManager packageManager =
                    IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
            onlyCoreApps = packageManager.isOnlyCoreApps();
            freeformWindowManagement = packageManager.hasSystemFeature(
                    PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT, 0);
        } catch (RemoteException e) {
            onlyCoreApps = false;
            freeformWindowManagement = false;
        }
        ONLY_CORE_APPS = onlyCoreApps;
        FREEFORM_WINDOW_MANAGEMENT = freeformWindowManagement;
    }

    /**
     * The {@link StatusBarState} of the status bar.
     */
    protected int mState;
    protected boolean mBouncerShowing;
    protected boolean mShowLockscreenNotifications;
    protected boolean mAllowLockscreenRemoteInput;

    PhoneStatusBarPolicy mIconPolicy;

    VolumeComponent mVolumeComponent;
    protected FingerprintUnlockController mFingerprintUnlockController;
    LightBarController mLightBarController;
    protected LockscreenWallpaper mLockscreenWallpaper;

    int mNaturalBarHeight = -1;

    Point mCurrentDisplaySize = new Point();

    protected StatusBarWindowView mStatusBarWindow;
    protected PhoneStatusBarView mStatusBarView;
    private int mStatusBarWindowState = WINDOW_STATE_SHOWING;
    protected StatusBarWindowManager mStatusBarWindowManager;
    protected UnlockMethodCache mUnlockMethodCache;
    private DozeServiceHost mDozeServiceHost = new DozeServiceHost();
    private boolean mWakeUpComingFromTouch;
    private PointF mWakeUpTouchLocation;

    int mPixelFormat;
    Object mQueueLock = new Object();

    protected StatusBarIconController mIconController;

    // viewgroup containing the normal contents of the statusbar
    LinearLayout mStatusBarContent;
    // Other views that need hiding for the notification ticker
    View mCenterClockLayout;

    // expanded notifications
    protected NotificationPanelView mNotificationPanel; // the sliding/resizing panel within the notification window
    View mExpandedContents;
    TextView mNotificationPanelDebugText;

    /**
     * {@code true} if notifications not part of a group should by default be rendered in their
     * expanded state. If {@code false}, then only the first notification will be expanded if
     * possible.
     */
    private boolean mAlwaysExpandNonGroupedNotification;

    // settings
    private QSPanel mQSPanel;
    private QuickStatusBarHeader mQuickStatusBarHeader;

    // top bar
    protected KeyguardStatusBarView mKeyguardStatusBar;
    boolean mLeaveOpenOnKeyguardHide;
    KeyguardIndicationController mKeyguardIndicationController;

    // Keyguard is going away soon.
    private boolean mKeyguardGoingAway;
    // Keyguard is actually fading away now.
    protected boolean mKeyguardFadingAway;
    protected long mKeyguardFadingAwayDelay;
    protected long mKeyguardFadingAwayDuration;

    //Blur stuff
    private int mBlurScale;
    private int mBlurRadius;
    private boolean mTranslucentQuickSettings;
    private boolean mBlurredStatusBarExpandedEnabled;
    private boolean mTranslucentNotifications;
    private int mQSTranslucencyPercentage;
    private int mNotTranslucencyPercentage;
    private boolean mBlurredRecents;
    private int mRadiusRecents;
    private int mScaleRecents;
    private int mBlurDarkColorFilter;
    private int mBlurMixedColorFilter;
    private int mBlurLightColorFilter;

    // RemoteInputView to be activated after unlock
    private View mPendingRemoteInputView;
    private View mPendingWorkRemoteInputView;

    private View mReportRejectedTouch;

    int mMaxAllowedKeyguardNotifications;

    boolean mExpandedVisible;
    private int mStatusBarHeaderHeight;

    ActivityManager mAm;
    private ArrayList<String> mBlacklist = new ArrayList<String>();

    private int mMaxKeyguardNotifConfig;
    private boolean mCustomMaxKeyguard;

    private boolean mFingerprintQuickPulldown;

    private boolean mFpDismissNotifications;

    // the tracker view
    int mTrackingPosition; // the position of the top of the tracking view.

    // status bar notification ticker
    private int mTickerEnabled;
    private Ticker mTicker;
    private boolean mTicking;
    private int mTickerAnimationMode;

    protected AppCircleSidebar mAppCircleSidebar;

   // Pie controls
    protected PieController mPieController;
    public int mOrientation = 0;
    private OrientationEventListener mOrientationListener;

    private int mAmbientMediaPlaying;

    // Tracking finger for opening/closing.
    boolean mTracking;

    int[] mAbsPos = new int[2];
    ArrayList<Runnable> mPostCollapseRunnables = new ArrayList<>();

    private boolean mAutomaticBrightness;
    private boolean mBrightnessControl;
    private boolean mBrightnessChanged;
    private float mScreenWidth;
    private int mMinBrightness;
    private boolean mJustPeeked;
    int mLinger;
    int mInitialTouchX;
    int mInitialTouchY;

    // for disabling the status bar
    int mDisabled1 = 0;
    int mDisabled2 = 0;

    // tracking calls to View.setSystemUiVisibility()
    int mSystemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
    private final Rect mLastFullscreenStackBounds = new Rect();
    private final Rect mLastDockedStackBounds = new Rect();
    private final Rect mTmpRect = new Rect();

    // last value sent to window manager
    private int mLastDispatchedSystemUiVisibility = ~View.SYSTEM_UI_FLAG_VISIBLE;

    DisplayMetrics mDisplayMetrics = new DisplayMetrics();

    private DUPackageMonitor mPackageMonitor;

    // XXX: gesture research
    private final GestureRecorder mGestureRec = DEBUG_GESTURES
        ? new GestureRecorder("/sdcard/statusbar_gestures.dat")
        : null;

    private ScreenPinningRequest mScreenPinningRequest;

    private int mBatterySaverColor;

    private UiModeManager mUiModeManager;

    private final MetricsLogger mMetricsLogger = Dependency.get(MetricsLogger.class);

    Runnable mLongPressBrightnessChange = new Runnable() {
        @Override
        public void run() {
            mStatusBarView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            adjustBrightness(mInitialTouchX);
            mLinger = BRIGHTNESS_CONTROL_LINGER_THRESHOLD + 1;
        }
    };

    // ensure quick settings is disabled until the current user makes it through the setup wizard
    private boolean mUserSetup = false;
    private DeviceProvisionedListener mUserSetupObserver = new DeviceProvisionedListener() {
        @Override
        public void onUserSetupChanged() {
            final boolean userSetup = mDeviceProvisionedController.isUserSetup(
                    mDeviceProvisionedController.getCurrentUser());
            if (MULTIUSER_DEBUG) Log.d(TAG, String.format("User setup changed: " +
                    "userSetup=%s mUserSetup=%s", userSetup, mUserSetup));

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

    protected H mHandler = createHandler();
    final private ContentObserver mHeadsUpObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            boolean wasUsing = mUseHeadsUp;
            mUseHeadsUp = ENABLE_HEADS_UP && !mDisableNotificationAlerts
                    && Settings.Global.HEADS_UP_OFF != Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                    Settings.Global.HEADS_UP_OFF);
            mHeadsUpTicker = mUseHeadsUp && 0 != Settings.Global.getInt(
                    mContext.getContentResolver(), SETTING_HEADS_UP_TICKER, 0);
            Log.d(TAG, "heads up is " + (mUseHeadsUp ? "enabled" : "disabled"));
            if (wasUsing != mUseHeadsUp) {
                if (!mUseHeadsUp) {
                    Log.d(TAG, "dismissing any existing heads up notification on disable event");
                    mHeadsUpManager.releaseAllImmediately();
                }
            }
        }
    };

    private int mInteractingWindows;
    private boolean mAutohideSuspended;
    private int mStatusBarMode;
    private int mMaxKeyguardNotifications;

    private ViewMediatorCallback mKeyguardViewMediatorCallback;
    protected ScrimController mScrimController;
    protected DozeScrimController mDozeScrimController;
    private final UiOffloadThread mUiOffloadThread = Dependency.get(UiOffloadThread.class);

    private final Runnable mAutohide = () -> {
        int requested = mSystemUiVisibility & ~STATUS_OR_NAV_TRANSIENT;
        if (mSystemUiVisibility != requested) {
            notifyUiVisibilityChanged(requested);
        }
    };

    private boolean mWaitingForKeyguardExit;
    protected boolean mDozing;
    private boolean mDozingRequested;
    protected boolean mScrimSrcModeEnabled;

    public static final Interpolator ALPHA_IN = Interpolators.ALPHA_IN;
    public static final Interpolator ALPHA_OUT = Interpolators.ALPHA_OUT;

    protected BackDropView mBackdrop;
    protected ImageView mBackdropFront, mBackdropBack;
    protected PorterDuffXfermode mSrcXferMode = new PorterDuffXfermode(PorterDuff.Mode.SRC);
    protected PorterDuffXfermode mSrcOverXferMode =
            new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER);

    private Entry mEntryToRefresh;
    private NotificationManager mNoMan;
    private String[] mNavMediaArrowsExcludeList;
    private VisualizerView mVisualizerView;
    private boolean mScreenOn;
    private boolean mKeyguardShowingMedia;
    private boolean mShowMediaMetadata;

    private MediaSessionManager mMediaSessionManager;
    private MediaController mMediaController;
    private String mMediaNotificationKey;
    private MediaMetadata mMediaMetadata;
    private MediaController.Callback mMediaListener
            = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            super.onPlaybackStateChanged(state);
            if (DEBUG_MEDIA) Log.v(TAG, "DEBUG_MEDIA: onPlaybackStateChanged: " + state);
            if (state != null) {
                if (!isPlaybackActive(state.getState())) {
                    clearCurrentMediaNotification();
                    updateMediaMetaData(true, true);
                }
                mVisualizerView.setPlaying(state.getState() == PlaybackState.STATE_PLAYING);
                setMediaPlaying();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (DEBUG_MEDIA) Log.v(TAG, "DEBUG_MEDIA: onMetadataChanged: " + metadata);
            mMediaMetadata = metadata;
            updateMediaMetaData(true, true);
            setMediaPlaying();
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            if (mTicker != null) {
                mTicker.resetShownMediaMetadata();
            }
            setMediaPlaying();
        }
    };

    public void setMediaPlaying() {
        if (PlaybackState.STATE_PLAYING ==
                getMediaControllerPlaybackState(mMediaController)
                || PlaybackState.STATE_BUFFERING ==
                getMediaControllerPlaybackState(mMediaController)) {
            tickTrackInfo(mMediaController);
            mNoMan.setMediaPlaying(true);
            final String currentPkg = mMediaController.getPackageName().toLowerCase();
            for (String packageName : mNavMediaArrowsExcludeList) {
                if (currentPkg.contains(packageName)) {
                    return;
                }
            }
            if (mNavigationBar != null) {
                // pulse colors already set by titckTrackInfo
                mNavigationBar.setMediaPlaying(true);
            }
        } else {
            if (mAmbientMediaPlaying != 0 && mAmbientIndicationContainer != null) {
                ((AmbientIndicationContainer)mAmbientIndicationContainer).hideIndication();
            }
            mNoMan.setMediaPlaying(false);
            if (mNavigationBar != null) {
                mNavigationBar.setMediaPlaying(false);
            }
        }
    }

    private void tickTrackInfo(MediaController mc) {
        ArrayList<Entry> activeNotifications = mNotificationData.getAllNotifications();
        int N = activeNotifications.size();
        final String pkg = mc.getPackageName();
        for (int i = 0; i < N; i++) {
            final Entry entry = activeNotifications.get(i);
            if (entry.notification.getPackageName().equals(pkg)) {
                if (mTickerEnabled == 2) {
                    tick(entry.notification, true, true, mMediaMetadata);
                }
                if (mAmbientMediaPlaying != 0 && mAmbientIndicationContainer != null) {
                    ((AmbientIndicationContainer)mAmbientIndicationContainer).setIndication(mMediaMetadata);
                }
                // NotificationInflater calls async MediaNotificationProcessoron to create notification
                // colors and when finished will trigger AsyncInflationFinished for all registered callbacks
                // like StatusBar. From there we'll send updated colors to Pulse
                mEntryToRefresh = entry;
                break;
            }
        }
    }

    public void triggerAmbientForMedia() {
        if (mAmbientMediaPlaying == 2 || mAmbientMediaPlaying == 3) {
            mDozeServiceHost.fireNotificationMedia();
        }
    }

    private final OnChildLocationsChangedListener mOnChildLocationsChangedListener =
            new OnChildLocationsChangedListener() {
        @Override
        public void onChildLocationsChanged(NotificationStackScrollLayout stackScrollLayout) {
            userActivity();
        }
    };

    private int mDisabledUnmodified1;
    private int mDisabledUnmodified2;

    /** Keys of notifications currently visible to the user. */
    private final ArraySet<NotificationVisibility> mCurrentlyVisibleNotifications =
            new ArraySet<>();
    private long mLastVisibilityReportUptimeMs;

    private Runnable mLaunchTransitionEndRunnable;
    protected boolean mLaunchTransitionFadingAway;
    private ExpandableNotificationRow mDraggedDownRow;
    private boolean mLaunchCameraOnScreenTurningOn;
    private boolean mLaunchCameraOnFinishedGoingToSleep;
    private int mLastCameraLaunchSource;
    private PowerManager.WakeLock mGestureWakeLock;
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

    private static final int CLOCK_DATE_POSITION_DEFAULT  = 0;
    private static final int CLOCK_DATE_POSITION_CENTERED = 1;
    private static final int CLOCK_DATE_POSITION_HIDDEN   = 2;
    private static final int CLOCK_DATE_POSITION_LEFT   = 3;
    private int mClockPos;

    public boolean isStartedGoingToSleep() {
        return mStartedGoingToSleep;
    }

    /**
     * If set, the device has started going to sleep but isn't fully non-interactive yet.
     */
    protected boolean mStartedGoingToSleep;

    private final OnChildLocationsChangedListener mNotificationLocationsChangedListener =
            new OnChildLocationsChangedListener() {
                @Override
                public void onChildLocationsChanged(
                        NotificationStackScrollLayout stackScrollLayout) {
                    if (mHandler.hasCallbacks(mVisibilityReporter)) {
                        // Visibilities will be reported when the existing
                        // callback is executed.
                        return;
                    }
                    // Calculate when we're allowed to run the visibility
                    // reporter. Note that this timestamp might already have
                    // passed. That's OK, the callback will just be executed
                    // ASAP.
                    long nextReportUptimeMs =
                            mLastVisibilityReportUptimeMs + VISIBILITY_REPORT_MIN_DELAY_MS;
                    mHandler.postAtTime(mVisibilityReporter, nextReportUptimeMs);
                }
            };

    // Tracks notifications currently visible in mNotificationStackScroller and
    // emits visibility events via NoMan on changes.
    protected final Runnable mVisibilityReporter = new Runnable() {
        private final ArraySet<NotificationVisibility> mTmpNewlyVisibleNotifications =
                new ArraySet<>();
        private final ArraySet<NotificationVisibility> mTmpCurrentlyVisibleNotifications =
                new ArraySet<>();
        private final ArraySet<NotificationVisibility> mTmpNoLongerVisibleNotifications =
                new ArraySet<>();

        @Override
        public void run() {
            mLastVisibilityReportUptimeMs = SystemClock.uptimeMillis();
            final String mediaKey = getCurrentMediaNotificationKey();

            // 1. Loop over mNotificationData entries:
            //   A. Keep list of visible notifications.
            //   B. Keep list of previously hidden, now visible notifications.
            // 2. Compute no-longer visible notifications by removing currently
            //    visible notifications from the set of previously visible
            //    notifications.
            // 3. Report newly visible and no-longer visible notifications.
            // 4. Keep currently visible notifications for next report.
            ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
            int N = activeNotifications.size();
            for (int i = 0; i < N; i++) {
                Entry entry = activeNotifications.get(i);
                String key = entry.notification.getKey();
                boolean isVisible = mStackScroller.isInVisibleLocation(entry.row);
                NotificationVisibility visObj = NotificationVisibility.obtain(key, i, isVisible);
                boolean previouslyVisible = mCurrentlyVisibleNotifications.contains(visObj);
                if (isVisible) {
                    // Build new set of visible notifications.
                    mTmpCurrentlyVisibleNotifications.add(visObj);
                    if (!previouslyVisible) {
                        mTmpNewlyVisibleNotifications.add(visObj);
                    }
                } else {
                    // release object
                    visObj.recycle();
                }
            }
            mTmpNoLongerVisibleNotifications.addAll(mCurrentlyVisibleNotifications);
            mTmpNoLongerVisibleNotifications.removeAll(mTmpCurrentlyVisibleNotifications);

            logNotificationVisibilityChanges(
                    mTmpNewlyVisibleNotifications, mTmpNoLongerVisibleNotifications);

            recycleAllVisibilityObjects(mCurrentlyVisibleNotifications);
            mCurrentlyVisibleNotifications.addAll(mTmpCurrentlyVisibleNotifications);

            recycleAllVisibilityObjects(mTmpNoLongerVisibleNotifications);
            mTmpCurrentlyVisibleNotifications.clear();
            mTmpNewlyVisibleNotifications.clear();
            mTmpNoLongerVisibleNotifications.clear();
        }
    };

    private NotificationMessagingUtil mMessagingUtil;
    private KeyguardUserSwitcher mKeyguardUserSwitcher;
    private UserSwitcherController mUserSwitcherController;
    private NetworkController mNetworkController;
    private KeyguardMonitorImpl mKeyguardMonitor
            = (KeyguardMonitorImpl) Dependency.get(KeyguardMonitor.class);
    private BatteryController mBatteryController;
    protected boolean mPanelExpanded;
    private IOverlayManager mOverlayManager;
    private boolean mKeyguardRequested;
    private boolean mIsKeyguard;
    private LogMaker mStatusBarStateLog;
    private LockscreenGestureLogger mLockscreenGestureLogger = new LockscreenGestureLogger();
    protected NotificationIconAreaController mNotificationIconAreaController;
    private boolean mReinflateNotificationsOnUserSwitched;
    private HashMap<String, Entry> mPendingNotifications = new HashMap<>();
    private boolean mClearAllEnabled;
    @Nullable private View mAmbientIndicationContainer;
    private String mKeyToRemoveOnGutsClosed;
    private SysuiColorExtractor mColorExtractor;
    private ForegroundServiceController mForegroundServiceController;
    private ScreenLifecycle mScreenLifecycle;
    @VisibleForTesting WakefulnessLifecycle mWakefulnessLifecycle;

    // Force black theme
    private boolean mForceBlackTheme;

    private void recycleAllVisibilityObjects(ArraySet<NotificationVisibility> array) {
        final int N = array.size();
        for (int i = 0 ; i < N; i++) {
            array.valueAt(i).recycle();
        }
        array.clear();
    }

    private final View.OnClickListener mGoToLockedShadeListener = v -> {
        if (mState == StatusBarState.KEYGUARD) {
            wakeUpIfDozing(SystemClock.uptimeMillis(), v);
            goToLockedShade(null);
        }
    };
    private HashMap<ExpandableNotificationRow, List<ExpandableNotificationRow>> mTmpChildOrderMap
            = new HashMap<>();
    private RankingMap mLatestRankingMap;
    private boolean mNoAnimationOnNextBarModeChange;
    private FalsingManager mFalsingManager;

    private KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            if (dreaming) {
                maybeEscalateHeadsUp();
            }
        }
    };

    private NavigationBarFragment mNavigationBar;
    private View mNavigationBarView;
    private FlashlightController mFlashlightController;

    private boolean mLockscreenMediaMetadata;
    private StatusBarHeaderMachine mStatusBarHeaderMachine;

    @Override
    public void start() {
        mNetworkController = Dependency.get(NetworkController.class);
        mUserSwitcherController = Dependency.get(UserSwitcherController.class);
        mScreenLifecycle = Dependency.get(ScreenLifecycle.class);
        mScreenLifecycle.addObserver(mScreenObserver);
        mWakefulnessLifecycle = Dependency.get(WakefulnessLifecycle.class);
        mWakefulnessLifecycle.addObserver(mWakefulnessObserver);
        mBatteryController = Dependency.get(BatteryController.class);
        mAssistManager = Dependency.get(AssistManager.class);
        mSystemServicesProxy = SystemServicesProxy.getInstance(mContext);
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

        mColorExtractor = Dependency.get(SysuiColorExtractor.class);
        mColorExtractor.addOnColorsChangedListener(this);

        mForceBlackTheme = LineageSettings.System.getInt(mContext.getContentResolver(),
                LineageSettings.System.BERRY_FORCE_BLACK, 0) == 1;

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        mForegroundServiceController = Dependency.get(ForegroundServiceController.class);

        mDisplay = mWindowManager.getDefaultDisplay();

        mPackageMonitor = new DUPackageMonitor();
        mPackageMonitor.register(mContext, mHandler);
        mPackageMonitor.addListener(this);

        updateDisplaySize();

        Resources res = mContext.getResources();
        mScrimSrcModeEnabled = res.getBoolean(R.bool.config_status_bar_scrim_behind_use_src);
        mClearAllEnabled = res.getBoolean(R.bool.config_enableNotificationsClearAll);
        mAlwaysExpandNonGroupedNotification =
                res.getBoolean(R.bool.config_alwaysExpandNonGroupedNotifications);

        DateTimeView.setReceiverHandler(Dependency.get(Dependency.TIME_TICK_HANDLER));
        putComponent(StatusBar.class, this);

        // start old BaseStatusBar.start().
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
        mDevicePolicyManager = (DevicePolicyManager) mContext.getSystemService(
                Context.DEVICE_POLICY_SERVICE);

        mNotificationData = new NotificationData(this);
        mMessagingUtil = new NotificationMessagingUtil(mContext);

        mAccessibilityManager = (AccessibilityManager)
                mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        mAm = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

        mDeviceProvisionedController = Dependency.get(DeviceProvisionedController.class);
        mDeviceProvisionedController.addCallback(mDeviceProvisionedListener);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ZEN_MODE), false,
                mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS), false,
                mLockscreenSettingsObserver,
                UserHandle.USER_ALL);
        if (ENABLE_LOCK_SCREEN_ALLOW_REMOTE_INPUT) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_ALLOW_REMOTE_INPUT),
                    false,
                    mSettingsObserver,
                    UserHandle.USER_ALL);
        }

        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS),
                true,
                mLockscreenSettingsObserver,
                UserHandle.USER_ALL);


        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.NAVIGATION_BAR_VISIBLE), 
                false, 
                mNavbarObserver, 
                UserHandle.USER_ALL);
 

        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        mRecents = getComponent(Recents.class);

        final Configuration currentConfig = res.getConfiguration();
        mLocale = currentConfig.locale;
        mLayoutDirection = TextUtils.getLayoutDirectionFromLocale(mLocale);

        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mLockPatternUtils = new LockPatternUtils(mContext);

        // Connect in to the status bar manager service
        mCommandQueue = getComponent(CommandQueue.class);
        mCommandQueue.addCallbacks(this);

        int[] switches = new int[9];
        ArrayList<IBinder> binders = new ArrayList<>();
        ArrayList<String> iconSlots = new ArrayList<>();
        ArrayList<StatusBarIcon> icons = new ArrayList<>();
        Rect fullscreenStackBounds = new Rect();
        Rect dockedStackBounds = new Rect();
        try {
            mBarService.registerStatusBar(mCommandQueue, iconSlots, icons, switches, binders,
                    fullscreenStackBounds, dockedStackBounds);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }

        createAndAddWindows();

        mSettingsObserver.onChange(false); // set up
        mRRSettingsObserver.observe();
        mRRSettingsObserver.update();
        mCommandQueue.disable(switches[0], switches[6], false /* animate */);
        setSystemUiVisibility(switches[1], switches[7], switches[8], 0xffffffff,
                fullscreenStackBounds, dockedStackBounds);
        topAppWindowChanged(switches[2] != 0);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        setImeWindowStatus(binders.get(0), switches[3], switches[4], switches[5] != 0);

        // Set up the initial icon state
        int N = iconSlots.size();
        for (int i=0; i < N; i++) {
            mCommandQueue.setIcon(iconSlots.get(i), icons.get(i));
        }

        // Set up the initial notification state.
        try {
            mNotificationListener.registerAsSystemService(mContext,
                    new ComponentName(mContext.getPackageName(), getClass().getCanonicalName()),
                    UserHandle.USER_ALL);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register notification listener", e);
        }


        if (DEBUG) {
            Log.d(TAG, String.format(
                    "init: icons=%d disabled=0x%08x lights=0x%08x menu=0x%08x imeButton=0x%08x",
                   icons.size(),
                   switches[0],
                   switches[1],
                   switches[2],
                   switches[3]
                   ));
        }

        mCurrentUserId = ActivityManager.getCurrentUser();
        setHeadsUpUser(mCurrentUserId);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(mBaseBroadcastReceiver, filter);

        IntentFilter internalFilter = new IntentFilter();
        internalFilter.addAction(NOTIFICATION_UNLOCKED_BY_WORK_CHALLENGE_ACTION);
        internalFilter.addAction(BANNER_ACTION_CANCEL);
        internalFilter.addAction(BANNER_ACTION_SETUP);
        mContext.registerReceiver(mBaseBroadcastReceiver, internalFilter, PERMISSION_SELF, null);

        IntentFilter allUsersFilter = new IntentFilter();
        allUsersFilter.addAction(
                DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        allUsersFilter.addAction(Intent.ACTION_DEVICE_LOCKED_CHANGED);
        mContext.registerReceiverAsUser(mAllUsersReceiver, UserHandle.ALL, allUsersFilter,
                null, null);
        updateCurrentProfilesCache();

        IVrManager vrManager = IVrManager.Stub.asInterface(ServiceManager.getService(
                Context.VR_SERVICE));
        try {
            vrManager.registerListener(mVrStateCallbacks);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register VR mode state listener: " + e);
        }

        mNonBlockablePkgs = new HashSet<>();
        Collections.addAll(mNonBlockablePkgs, res.getStringArray(
                com.android.internal.R.array.config_nonBlockableNotificationPackages));
        // end old BaseStatusBar.start().

        mPieSettingsObserver.onChange(false);
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.PIE_STATE), false, mPieSettingsObserver);
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.PIE_GRAVITY), false, mPieSettingsObserver);

        mMediaSessionManager
                = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        // TODO: use MediaSessionManager.SessionListener to hook us up to future updates
        // in session state

        Dependency.get(TunerService.class).addTunable(this,
                SCREEN_BRIGHTNESS_MODE,
                STATUS_BAR_BRIGHTNESS_CONTROL,
                LOCKSCREEN_MEDIA_METADATA);

        // Lastly, call to the icon policy to install/update all the icons.
        mIconPolicy = new PhoneStatusBarPolicy(mContext, mIconController);
        mSettingsObserver.onChange(false); // set up

        mHeadsUpObserver.onChange(true); // set up
        if (ENABLE_HEADS_UP) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED), true,
                    mHeadsUpObserver);
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(SETTING_HEADS_UP_TICKER), true,
                    mHeadsUpObserver);
        }
        mUnlockMethodCache = UnlockMethodCache.getInstance(mContext);
        mUnlockMethodCache.addListener(this);
        startKeyguard();

        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateCallback);
        putComponent(DozeHost.class, mDozeServiceHost);

        notifyUserAboutHiddenNotifications();

        mScreenPinningRequest = new ScreenPinningRequest(mContext);
        mFalsingManager = FalsingManager.getInstance(mContext);

        mUiModeManager = mContext.getSystemService(UiModeManager.class);

        Dependency.get(ActivityStarterDelegate.class).setActivityStarterImpl(this);

        Dependency.get(ConfigurationController.class).addCallback(this);
        mFlashlightController = Dependency.get(FlashlightController.class);

        final String list = mContext.getResources().getString(R.string.navMediaArrowsExcludeList);
        mNavMediaArrowsExcludeList = list.split(",");
    }

    protected void createIconController() {
    }

    // ================================================================================
    // Constructing the view
    // ================================================================================
    @ChaosLab(name="GestureAnywhere", classification=Classification.CHANGE_CODE)
    protected void makeStatusBarView() {
        final Context context = mContext;

        mScreenWidth = (float) context.getResources().getDisplayMetrics().widthPixels;
        mMinBrightness = context.getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessDim);

        updateDisplaySize(); // populates mDisplayMetrics
        updateResources();
        updateTheme();

        inflateStatusBarWindow(context);
        mStatusBarWindow.setService(this);
        mStatusBarWindow.setOnTouchListener(getStatusBarWindowTouchListener());

        // TODO: Deal with the ugliness that comes from having some of the statusbar broken out
        // into fragments, but the rest here, it leaves some awkward lifecycle and whatnot.
        mNotificationPanel = (NotificationPanelView) mStatusBarWindow.findViewById(
                R.id.notification_panel);
        mStackScroller = (NotificationStackScrollLayout) mStatusBarWindow.findViewById(
                R.id.notification_stack_scroller);
        mNotificationPanel.setStatusBar(this);
        mNotificationPanel.setGroupManager(mGroupManager);
        mAboveShelfObserver = new AboveShelfObserver(mStackScroller);
        mAboveShelfObserver.setListener(mStatusBarWindow.findViewById(
                R.id.notification_container_parent));
        mKeyguardStatusBar = (KeyguardStatusBarView) mStatusBarWindow.findViewById(R.id.keyguard_header);

        mNotificationIconAreaController = SystemUIFactory.getInstance()
                .createNotificationIconAreaController(context, this);
        inflateShelf();
        mNotificationIconAreaController.setupShelf(mNotificationShelf);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mNotificationIconAreaController);
        FragmentHostManager.get(mStatusBarWindow)
                .addTagListener(CollapsedStatusBarFragment.TAG, (tag, fragment) -> {
                    CollapsedStatusBarFragment statusBarFragment =
                            (CollapsedStatusBarFragment) fragment;
                    statusBarFragment.initNotificationIconArea(mNotificationIconAreaController);
                    mStatusBarView = (PhoneStatusBarView) fragment.getView();
                    mStatusBarView.setBar(this);
                    mStatusBarView.setPanel(mNotificationPanel);
                    mStatusBarView.setScrimController(mScrimController);
                    mStatusBarView.setBouncerShowing(mBouncerShowing);
                    mStatusBarContent = (LinearLayout) mStatusBarView.findViewById(R.id.status_bar_contents);
                    mCenterClockLayout = mStatusBarView.findViewById(R.id.center_clock_layout);
                    setAreThereNotifications();
                    checkBarModes();
                }).getFragmentManager()
                .beginTransaction()
                .replace(R.id.status_bar_container, new CollapsedStatusBarFragment(),
                        CollapsedStatusBarFragment.TAG)
                .commit();
        mIconController = Dependency.get(StatusBarIconController.class);

        mHeadsUpManager = new HeadsUpManager(context, mStatusBarWindow, mGroupManager);
        mHeadsUpManager.setBar(this);
        mHeadsUpManager.addListener(this);
        mHeadsUpManager.addListener(mNotificationPanel);
        mHeadsUpManager.addListener(mGroupManager);
        mHeadsUpManager.addListener(mVisualStabilityManager);
        mNotificationPanel.setHeadsUpManager(mHeadsUpManager);
        mNotificationData.setHeadsUpManager(mHeadsUpManager);
        mGroupManager.setHeadsUpManager(mHeadsUpManager);
        mHeadsUpManager.setVisualStabilityManager(mVisualStabilityManager);

        if (MULTIUSER_DEBUG) {
            mNotificationPanelDebugText = (TextView) mNotificationPanel.findViewById(
                    R.id.header_debug_info);
            mNotificationPanelDebugText.setVisibility(View.VISIBLE);
        }

        mNoMan = (NotificationManager)
                        mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        setMediaPlaying();

        boolean showNav = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_VISIBLE,
                DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0) != 0;
        if (DEBUG)
            Log.v(TAG, "hasNavigationBar=" + showNav);
        if (showNav) {
            createNavigationBar();
        }

        addGestureAnywhereView();
        addAppCircleSidebar();

        // figure out which pixel-format to use for the status bar.
        mPixelFormat = PixelFormat.OPAQUE;

        mStackScroller.setLongPressListener(getNotificationLongClicker());
        mStackScroller.setStatusBar(this);
        mStackScroller.setGroupManager(mGroupManager);
        mStackScroller.setHeadsUpManager(mHeadsUpManager);
        mGroupManager.setOnGroupChangeListener(mStackScroller);
        mVisualStabilityManager.setVisibilityLocationProvider(mStackScroller);

        inflateEmptyShadeView();
        inflateDismissView();
        mExpandedContents = mStackScroller;

        mBackdrop = (BackDropView) mStatusBarWindow.findViewById(R.id.backdrop);
        mBackdropFront = (ImageView) mBackdrop.findViewById(R.id.backdrop_front);
        mBackdropBack = (ImageView) mBackdrop.findViewById(R.id.backdrop_back);

        if (ENABLE_LOCKSCREEN_WALLPAPER) {
            mLockscreenWallpaper = new LockscreenWallpaper(mContext, this, mHandler);
        }

        mKeyguardIndicationController =
                SystemUIFactory.getInstance().createKeyguardIndicationController(mContext,
                (ViewGroup) mStatusBarWindow.findViewById(R.id.keyguard_indication_area),
                mNotificationPanel.getLockIcon());
        mNotificationPanel.setKeyguardIndicationController(mKeyguardIndicationController);


        mAmbientIndicationContainer = mStatusBarWindow.findViewById(
                R.id.ambient_indication_container);
        if (mAmbientIndicationContainer != null) {
            ((AmbientIndicationContainer) mAmbientIndicationContainer).initializeView(this);
        }

        // set the initial view visibility
        setAreThereNotifications();

        // TODO: Find better place for this callback.
        mBatteryController.addCallback(new BatteryStateChangeCallback() {
            @Override
            public void onPowerSaveChanged(boolean isPowerSave) {
                mHandler.post(mCheckBarModes);
                if (mDozeServiceHost != null) {
                    mDozeServiceHost.firePowerSaveChanged(isPowerSave);
                }
            }

            @Override
            public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
                // noop
            }
        });

        mLightBarController = Dependency.get(LightBarController.class);
        if (mNavigationBar != null) {
            mNavigationBar.setLightBarController(mLightBarController);
        }

        ScrimView scrimBehind = (ScrimView) mStatusBarWindow.findViewById(R.id.scrim_behind);
        ScrimView scrimInFront = (ScrimView) mStatusBarWindow.findViewById(R.id.scrim_in_front);
        View headsUpScrim = mStatusBarWindow.findViewById(R.id.heads_up_scrim);
        mScrimController = SystemUIFactory.getInstance().createScrimController(mLightBarController,
                scrimBehind, scrimInFront, headsUpScrim, mLockscreenWallpaper,
                scrimsVisible -> {
                    if (mStatusBarWindowManager != null) {
                        mStatusBarWindowManager.setScrimsVisible(scrimsVisible);
                    }
                });
        if (mScrimSrcModeEnabled) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    boolean asSrc = mBackdrop.getVisibility() != View.VISIBLE;
                    mScrimController.setDrawBehindAsSrc(asSrc);
                    mStackScroller.setDrawBackgroundAsSrc(asSrc);
                }
            };
            mBackdrop.setOnVisibilityChangedRunnable(runnable);
            runnable.run();
        }
        mHeadsUpManager.addListener(mScrimController);
        mStackScroller.setScrimController(mScrimController);
        mDozeScrimController = new DozeScrimController(mScrimController, context);
        mVisualizerView = (VisualizerView) mStatusBarWindow.findViewById(R.id.visualizerview);

        // Other icons
        mVolumeComponent = getComponent(VolumeComponent.class);

        mNotificationPanel.setUserSetupComplete(mUserSetup);
        if (UserManager.get(mContext).isUserSwitcherEnabled()) {
            createUserSwitcher();
        }

        mStatusBarHeaderMachine = new StatusBarHeaderMachine(mContext);

        // Set up the quick settings tile panel
        View container = mStatusBarWindow.findViewById(R.id.qs_frame);
        if (container != null) {
            FragmentHostManager fragmentHostManager = FragmentHostManager.get(container);
            ExtensionFragmentListener.attachExtensonToFragment(container, QS.TAG, R.id.qs_frame,
                    Dependency.get(ExtensionController.class).newExtension(QS.class)
                            .withPlugin(QS.class)
                            .withFeature(
                                    PackageManager.FEATURE_AUTOMOTIVE, () -> new CarQSFragment())
                            .withDefault(() -> new QSFragment())
                            .build());
            final QSTileHost qsh = SystemUIFactory.getInstance().createQSTileHost(mContext, this,
                    mIconController);
            fragmentHostManager.addTagListener(QS.TAG, (tag, f) -> {
                QS qs = (QS) f;
                if (qs instanceof QSFragment) {
                    ((QSFragment) qs).setHost(qsh);
                    mQSPanel = ((QSFragment) qs).getQsPanel();
                    mKeyguardStatusBar.setQSPanel(mQSPanel);
                    mQuickStatusBarHeader = ((QSFragment) qs).getQsHeader();
                    mStatusBarHeaderMachine.addObserver(mQuickStatusBarHeader);
                    mStatusBarHeaderMachine.updateEnablement();
                }
            });
        }

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
        mBroadcastReceiver.onReceive(mContext,
                new Intent(pm.isScreenOn() ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF));
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
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG);
        filter.addAction(lineageos.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
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

        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (NotificationPanelView.mKeyguardShowing) {
                        return;
                    }
                    String action = intent.getAction();

               if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                        if (NotificationPanelView.mKeyguardShowing) {
                            return;
                        }
                        RecentsActivity.onConfigurationChanged();

                        if (mExpandedVisible && NotificationPanelView.mBlurredStatusBarExpandedEnabled && (!NotificationPanelView.mKeyguardShowing)) {
                            makeExpandedInvisible();
                        }
                    }
                }
            };

            IntentFilter intent = new IntentFilter();
            intent.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            this.mContext.registerReceiver(receiver, intent);

            RecentsActivity.init(this.mContext);

            updatePreferences(mContext);
        } catch (Exception e){
            Log.d("mango918", String.valueOf(e));
        }
    }

    protected void createNavigationBar() {
        mNavigationBarView = NavigationBarFragment.create(mContext, (tag, fragment) -> {
            mNavigationBar = (NavigationBarFragment) fragment;
            if (mLightBarController != null) {
                mNavigationBar.setLightBarController(mLightBarController);
            }
            if (!mNavigationBar.isUsingStockNav()) {
                ((NavigationBarFrame)mNavigationBarView).disableDeadZone();
                setMediaPlaying();
            }
            mNavigationBar.setCurrentSysuiVisibility(mSystemUiVisibility);
        });
    }

    protected void removeNavigationBar() {
        if (mNavigationBar != null && mNavigationBarView != null) {
            FragmentHostManager fragmentHost = FragmentHostManager.get(mNavigationBarView);
            if (mNavigationBarView.isAttachedToWindow()) {
                mWindowManager.removeViewImmediate(mNavigationBarView);
                mNavigationBarView = null;
            }
            fragmentHost.getFragmentManager().beginTransaction().remove(mNavigationBar).commit();
            mNavigationBar = null;
        }
    }

    /**
     * Returns the {@link android.view.View.OnTouchListener} that will be invoked when the
     * background window of the status bar is clicked.
     */
    protected View.OnTouchListener getStatusBarWindowTouchListener() {
        return (v, event) -> {
            checkUserAutohide(v, event);
            checkRemoteInputOutside(event);
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
                (NotificationShelf) LayoutInflater.from(mContext).inflate(
                        R.layout.status_bar_notification_shelf, mStackScroller, false);
        mNotificationShelf.setOnActivatedListener(this);
        mStackScroller.setShelf(mNotificationShelf);
        mNotificationShelf.setOnClickListener(mGoToLockedShadeListener);
        mNotificationShelf.setStatusBarState(mState);
    }

    public NetworkController getNetworkController() {
        return mNetworkController;
    }

    public void onDensityOrFontScaleChanged() {
        // start old BaseStatusBar.onDensityOrFontScaleChanged().
        if (!KeyguardUpdateMonitor.getInstance(mContext).isSwitchingUser()) {
            updateNotificationsOnDensityOrFontScaleChanged();
        } else {
            mReinflateNotificationsOnUserSwitched = true;
        }
        // end old BaseStatusBar.onDensityOrFontScaleChanged().
        mScrimController.onDensityOrFontScaleChanged();
        // TODO: Remove this.
        if (mStatusBarView != null) mStatusBarView.onDensityOrFontScaleChanged();
        mStatusBarKeyguardViewManager.onDensityOrFontScaleChanged();
        // TODO: Bring these out of StatusBar.
        ((UserInfoControllerImpl) Dependency.get(UserInfoController.class))
                .onDensityOrFontScaleChanged();
        Dependency.get(UserSwitcherController.class).onDensityOrFontScaleChanged();
        if (mKeyguardUserSwitcher != null) {
            mKeyguardUserSwitcher.onDensityOrFontScaleChanged();
        }
        mNotificationIconAreaController.onDensityOrFontScaleChanged(mContext);

        reevaluateStyles();
        ContentResolver resolver = mContext.getContentResolver();
        boolean pieEnabled = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.PIE_STATE, 0, UserHandle.USER_CURRENT) == 1;
        updatePieControls(!pieEnabled);
    }

    @Override
    public void onOverlayChanged() {
        reinflateViews();
        updateNotificationsOnOverlayChanged();
        mStackScroller.onOverlayChanged();
        mNotificationShelf.onOverlayChanged();
        mNotificationPanel.onOverlayChanged();
        Dependency.get(DarkIconDispatcher.class).onOverlayChanged(mContext);
    }

    private void reinflateViews() {
        reevaluateStyles();

        // Clock and bottom icons
        mNotificationPanel.onOverlayChanged();
        // The status bar on the keyguard is a special layout.
        if (mKeyguardStatusBar != null) mKeyguardStatusBar.onOverlayChanged();
        // Recreate Indication controller because internal references changed
        mKeyguardIndicationController =
                SystemUIFactory.getInstance().createKeyguardIndicationController(mContext,
                        mStatusBarWindow.findViewById(R.id.keyguard_indication_area),
                        mNotificationPanel.getLockIcon());
        mNotificationPanel.setKeyguardIndicationController(mKeyguardIndicationController);
        mKeyguardIndicationController
                .setStatusBarKeyguardViewManager(mStatusBarKeyguardViewManager);
        mKeyguardIndicationController.setVisible(mState == StatusBarState.KEYGUARD);
        mKeyguardIndicationController.setDozing(mDozing);
        if (mStatusBarKeyguardViewManager != null) {
            mStatusBarKeyguardViewManager.onOverlayChanged();
        }
        if (mAmbientIndicationContainer instanceof AutoReinflateContainer) {
            ((AutoReinflateContainer) mAmbientIndicationContainer).inflateLayout();
        }
    }

    protected void reevaluateStyles() {
        inflateSignalClusters();
        inflateDismissView();
        updateClearAll();
        inflateEmptyShadeView();
        updateEmptyShadeView();
    }

    private void updateNotificationsOnDensityOrFontScaleChanged() {
        ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        for (int i = 0; i < activeNotifications.size(); i++) {
            Entry entry = activeNotifications.get(i);
            boolean exposedGuts = mNotificationGutsExposed != null
                    && entry.row.getGuts() == mNotificationGutsExposed;
            entry.row.onDensityOrFontScaleChanged();
            if (exposedGuts) {
                mNotificationGutsExposed = entry.row.getGuts();
                bindGuts(entry.row, mGutsMenuItem);
            }
        }
    }

    private void updateNotificationsOnOverlayChanged() {
        ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        for (int i = 0; i < activeNotifications.size(); i++) {
            Entry entry = activeNotifications.get(i);
            boolean exposedGuts = mNotificationGutsExposed != null
                    && entry.row.getGuts() == mNotificationGutsExposed;
            entry.row.onOverlayChanged();
            if (exposedGuts) {
                mNotificationGutsExposed = entry.row.getGuts();
                bindGuts(entry.row, mGutsMenuItem);
            }
        }
    }

    private void inflateSignalClusters() {
        if (mKeyguardStatusBar != null) reinflateSignalCluster(mKeyguardStatusBar);
    }

    public static SignalClusterView reinflateSignalCluster(View view) {
        Context context = view.getContext();
        SignalClusterView signalCluster =
                (SignalClusterView) view.findViewById(R.id.signal_cluster);
        if (signalCluster != null) {
            ViewParent parent = signalCluster.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup viewParent = (ViewGroup) parent;
                int index = viewParent.indexOfChild(signalCluster);
                viewParent.removeView(signalCluster);
                SignalClusterView newCluster = (SignalClusterView) LayoutInflater.from(context)
                        .inflate(R.layout.signal_cluster_view, viewParent, false);
                ViewGroup.MarginLayoutParams layoutParams =
                        (ViewGroup.MarginLayoutParams) viewParent.getLayoutParams();
                layoutParams.setMarginsRelative(
                        context.getResources().getDimensionPixelSize(
                                R.dimen.signal_cluster_margin_start),
                        0, 0, 0);
                newCluster.setLayoutParams(layoutParams);
                viewParent.addView(newCluster, index);
                return newCluster;
            }
            return signalCluster;
        }
        return null;
    }

    private void inflateEmptyShadeView() {
        if (mStackScroller == null) {
            return;
        }
        mEmptyShadeView = (EmptyShadeView) LayoutInflater.from(mContext).inflate(
                R.layout.status_bar_no_notifications, mStackScroller, false);
        mStackScroller.setEmptyShadeView(mEmptyShadeView);
    }

    private void inflateDismissView() {
        if (!mClearAllEnabled || mStackScroller == null) {
            return;
        }

        mDismissView = (DismissView) LayoutInflater.from(mContext).inflate(
                R.layout.status_bar_notification_dismiss_all, mStackScroller, false);
        mDismissView.setOnButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMetricsLogger.action(MetricsEvent.ACTION_DISMISS_ALL_NOTES);
                clearAllNotifications();
            }
        });
        mStackScroller.setDismissView(mDismissView);
    }

    protected void createUserSwitcher() {
        mKeyguardUserSwitcher = new KeyguardUserSwitcher(mContext,
                (ViewStub) mStatusBarWindow.findViewById(R.id.keyguard_user_switcher),
                mKeyguardStatusBar, mNotificationPanel);
    }

    protected void inflateStatusBarWindow(Context context) {
        mStatusBarWindow = (StatusBarWindowView) View.inflate(context,
                R.layout.super_status_bar, null);
    }

    public void clearAllNotifications() {
        clearAllNotifications(false);
    }

    private void clearAllNotifications(boolean forceToLeft) {

        // animate-swipe all dismissable notifications, then animate the shade closed
        int numChildren = mStackScroller.getChildCount();

        final ArrayList<View> viewsToHide = new ArrayList<View>(numChildren);
        final ArrayList<ExpandableNotificationRow> viewsToRemove = new ArrayList<>(numChildren);
        for (int i = 0; i < numChildren; i++) {
            final View child = mStackScroller.getChildAt(i);
            if (child instanceof ExpandableNotificationRow) {
                ExpandableNotificationRow row = (ExpandableNotificationRow) child;
                boolean parentVisible = false;
                boolean hasClipBounds = child.getClipBounds(mTmpRect);
                if (mStackScroller.canChildBeDismissed(child)) {
                    viewsToRemove.add(row);
                    if (child.getVisibility() == View.VISIBLE
                            && (!hasClipBounds || mTmpRect.height() > 0)) {
                        viewsToHide.add(child);
                        parentVisible = true;
                    }
                } else if (child.getVisibility() == View.VISIBLE
                        && (!hasClipBounds || mTmpRect.height() > 0)) {
                    parentVisible = true;
                }
                List<ExpandableNotificationRow> children = row.getNotificationChildren();
                if (children != null) {
                    for (ExpandableNotificationRow childRow : children) {
                        viewsToRemove.add(childRow);
                        if (parentVisible && row.areChildrenExpanded()
                                && mStackScroller.canChildBeDismissed(childRow)) {
                            hasClipBounds = childRow.getClipBounds(mTmpRect);
                            if (childRow.getVisibility() == View.VISIBLE
                                    && (!hasClipBounds || mTmpRect.height() > 0)) {
                                viewsToHide.add(childRow);
                            }
                        }
                    }
                }
            }
        }
        if (viewsToRemove.isEmpty()) {
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
            return;
        }

        addPostCollapseAction(new Runnable() {
            @Override
            public void run() {
                mStackScroller.setDismissAllInProgress(false);
                for (ExpandableNotificationRow rowToRemove : viewsToRemove) {
                    if (mStackScroller.canChildBeDismissed(rowToRemove)) {
                        removeNotification(rowToRemove.getEntry().key, null);
                    } else {
                        rowToRemove.resetTranslation();
                    }
                }
                try {
                    mBarService.onClearAllNotifications(mCurrentUserId);
                } catch (Exception ex) { }
            }
        });

        performDismissAllAnimations(viewsToHide, forceToLeft);

    }

    private void performDismissAllAnimations(ArrayList<View> hideAnimatedList, boolean forceToLeft) {
        Runnable animationFinishAction = new Runnable() {
            @Override
            public void run() {
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
            }
        };

        if (hideAnimatedList.isEmpty()) {
            animationFinishAction.run();
            return;
        }

        // let's disable our normal animations
        mStackScroller.setDismissAllInProgress(true);

        // Decrease the delay for every row we animate to give the sense of
        // accelerating the swipes
        int rowDelayDecrement = 10;
        int currentDelay = 140;
        int totalDelay = 180;
        int numItems = hideAnimatedList.size();
        for (int i = numItems - 1; i >= 0; i--) {
            View view = hideAnimatedList.get(i);
            Runnable endRunnable = null;
            if (i == 0) {
                endRunnable = animationFinishAction;
            }
            mStackScroller.dismissViewAnimated(view, endRunnable, totalDelay, 260, forceToLeft);
            currentDelay = Math.max(50, currentDelay - rowDelayDecrement);
            totalDelay += currentDelay;
        }
    }

    protected void setZenMode(int mode) {
        // start old BaseStatusBar.setZenMode().
        if (isDeviceProvisioned()) {
            mZenMode = mode;
            updateNotifications();
        }
        // end old BaseStatusBar.setZenMode().
    }

    protected void startKeyguard() {
        Trace.beginSection("StatusBar#startKeyguard");
        KeyguardViewMediator keyguardViewMediator = getComponent(KeyguardViewMediator.class);
        mFingerprintUnlockController = new FingerprintUnlockController(mContext,
                mDozeScrimController, keyguardViewMediator,
                mScrimController, this, UnlockMethodCache.getInstance(mContext));
        mStatusBarKeyguardViewManager = keyguardViewMediator.registerStatusBar(this,
                getBouncerContainer(), mScrimController,
                mFingerprintUnlockController);
        mKeyguardIndicationController
                .setStatusBarKeyguardViewManager(mStatusBarKeyguardViewManager);
        mFingerprintUnlockController.setStatusBarKeyguardViewManager(mStatusBarKeyguardViewManager);
        mRemoteInputController.addCallback(mStatusBarKeyguardViewManager);

        mRemoteInputController.addCallback(new RemoteInputController.Callback() {
            @Override
            public void onRemoteInputSent(Entry entry) {
                if (FORCE_REMOTE_INPUT_HISTORY && mKeysKeptForRemoteInput.contains(entry.key)) {
                    removeNotification(entry.key, null);
                } else if (mRemoteInputEntriesToRemoveOnCollapse.contains(entry)) {
                    // We're currently holding onto this notification, but from the apps point of
                    // view it is already canceled, so we'll need to cancel it on the apps behalf
                    // after sending - unless the app posts an update in the mean time, so wait a
                    // bit.
                    mHandler.postDelayed(() -> {
                        if (mRemoteInputEntriesToRemoveOnCollapse.remove(entry)) {
                            removeNotification(entry.key, null);
                        }
                    }, REMOTE_INPUT_KEPT_ENTRY_AUTO_CANCEL_DELAY);
                }
            }
        });

        mKeyguardViewMediatorCallback = keyguardViewMediator.getViewMediatorCallback();
        mLightBarController.setFingerprintUnlockController(mFingerprintUnlockController);
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

    public void createTicker(
            int tickerMode, Context ctx, View statusBarView, TickerView tickerTextView, ImageSwitcher tickerIcon, View tickerView) {
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
            boolean isInLockTaskMode = false;
            try {
                IActivityManager activityManager = ActivityManagerNative.getDefault();
                if (activityManager.isInLockTaskMode()) {
                    isInLockTaskMode = true;
                }
            } catch (RemoteException e) {}
            if (mSlimRecents != null && !isInLockTaskMode) {
                mSlimRecents.startMultiWindow();
            } else {
                return mRecents.dockTopTask(NavigationBarGestureHelper.DRAG_MODE_NONE,
                        ActivityManager.DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT, null, metricsDockAction);
            }
        } else {
            Divider divider = getComponent(Divider.class);
            if (divider != null && divider.isMinimized() && !divider.isHomeStackResizable()) {
                // Undocking from the minimized state is not supported
                return false;
            } else {
                EventBus.getDefault().send(new UndockingTaskEvent());
                if (metricsUndockAction != -1) {
                    mMetricsLogger.action(metricsUndockAction);
                }
            }
        }
        return true;
    }

    void awakenDreams() {
        SystemServicesProxy.getInstance(mContext).awakenDreamsAsync();
    }

    public UserHandle getCurrentUserHandle() {
        return new UserHandle(mCurrentUserId);
    }

    public void addNotification(StatusBarNotification notification, RankingMap ranking)
            throws InflationException {
        String key = notification.getKey();
        if (DEBUG) Log.d(TAG, "addNotification key=" + key);

        mNotificationData.updateRanking(ranking);
        Entry shadeEntry = createNotificationViews(notification);
        boolean isHeadsUped = shouldPeek(shadeEntry);
        if (!isHeadsUped && notification.getNotification().fullScreenIntent != null) {
            if (shouldSuppressFullScreenIntent(key)) {
                if (DEBUG) {
                    Log.d(TAG, "No Fullscreen intent: suppressed by DND: " + key);
                }
            } else if (mNotificationData.getImportance(key)
                    < NotificationManager.IMPORTANCE_HIGH) {
                if (DEBUG) {
                    Log.d(TAG, "No Fullscreen intent: not important enough: "
                            + key);
                }
            } else {
                // Stop screensaver if the notification has a full-screen intent.
                // (like an incoming phone call)
                awakenDreams();

                // not immersive & a full-screen alert should be shown
                if (DEBUG)
                    Log.d(TAG, "Notification has fullScreenIntent; sending fullScreenIntent");
                try {
                    EventLog.writeEvent(EventLogTags.SYSUI_FULLSCREEN_NOTIFICATION,
                            key);
                    notification.getNotification().fullScreenIntent.send();
                    shadeEntry.notifyFullScreenIntentLaunched();
                    mMetricsLogger.count("note_fullscreen", 1);
                } catch (PendingIntent.CanceledException e) {
                }
            }
        }
        abortExistingInflation(key);

        mForegroundServiceController.addNotification(notification,
                mNotificationData.getImportance(key));

        mPendingNotifications.put(key, shadeEntry);
    }

    private void abortExistingInflation(String key) {
        if (mPendingNotifications.containsKey(key)) {
            Entry entry = mPendingNotifications.get(key);
            entry.abortTask();
            mPendingNotifications.remove(key);
        }
        Entry addedEntry = mNotificationData.get(key);
        if (addedEntry != null) {
            addedEntry.abortTask();
        }
    }

    private void addEntry(Entry shadeEntry) {
        boolean isHeadsUped = shouldPeek(shadeEntry);
        if (isHeadsUped) {
            mHeadsUpManager.showNotification(shadeEntry);
            // Mark as seen immediately
            setNotificationShown(shadeEntry.notification);
        } else {
            tick(shadeEntry.notification, true, false, null);
        }
        addNotificationViews(shadeEntry);
        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
    }

    @Override
    public void handleInflationException(StatusBarNotification notification, Exception e) {
        handleNotificationError(notification, e.getMessage());
    }

    @Override
    public void onAsyncInflationFinished(Entry entry) {
        mPendingNotifications.remove(entry.key);
        // If there was an async task started after the removal, we don't want to add it back to
        // the list, otherwise we might get leaks.
        boolean isNew = mNotificationData.get(entry.key) == null;
        if (isNew && !entry.row.isRemoved()) {
            addEntry(entry);
        } else if (!isNew && entry.row.hasLowPriorityStateUpdated()) {
            mVisualStabilityManager.onLowPriorityUpdated(entry);
            updateNotificationShade();
        }
        entry.row.setLowPriorityStateUpdated(false);

        if (mEntryToRefresh == entry) {
            if (mNavigationBar != null) {
                Notification n = entry.notification.getNotification();
                int[] colors = {n.backgroundColor, n.foregroundColor,
                        n.primaryTextColor, n.secondaryTextColor};
                mNavigationBar.setPulseColors(n.isColorizedMedia(), colors);
            }
        }
    }

    private boolean shouldSuppressFullScreenIntent(String key) {
        if (isDeviceInVrMode()) {
            return true;
        }

        if (mPowerManager.isInteractive()) {
            return mNotificationData.shouldSuppressScreenOn(key);
        } else {
            return mNotificationData.shouldSuppressScreenOff(key);
        }
    }

    protected void updateNotificationRanking(RankingMap ranking) {
        mNotificationData.updateRanking(ranking);
        updateNotifications();
    }

    public void removeNotification(String key, RankingMap ranking) {
        boolean deferRemoval = false;
        abortExistingInflation(key);
        if (mHeadsUpManager.isHeadsUp(key)) {
            // A cancel() in repsonse to a remote input shouldn't be delayed, as it makes the
            // sending look longer than it takes.
            // Also we should not defer the removal if reordering isn't allowed since otherwise
            // some notifications can't disappear before the panel is closed.
            boolean ignoreEarliestRemovalTime = mRemoteInputController.isSpinning(key)
                    && !FORCE_REMOTE_INPUT_HISTORY
                    || !mVisualStabilityManager.isReorderingAllowed();
            deferRemoval = !mHeadsUpManager.removeNotification(key,  ignoreEarliestRemovalTime);
        }
        if (key.equals(mMediaNotificationKey)) {
            clearCurrentMediaNotification();
            updateMediaMetaData(true, true);
        }
        if (FORCE_REMOTE_INPUT_HISTORY && mRemoteInputController.isSpinning(key)) {
            Entry entry = mNotificationData.get(key);
            StatusBarNotification sbn = entry.notification;

            Notification.Builder b = Notification.Builder
                    .recoverBuilder(mContext, sbn.getNotification().clone());
            CharSequence[] oldHistory = sbn.getNotification().extras
                    .getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            CharSequence[] newHistory;
            if (oldHistory == null) {
                newHistory = new CharSequence[1];
            } else {
                newHistory = new CharSequence[oldHistory.length + 1];
                for (int i = 0; i < oldHistory.length; i++) {
                    newHistory[i + 1] = oldHistory[i];
                }
            }
            newHistory[0] = String.valueOf(entry.remoteInputText);
            b.setRemoteInputHistory(newHistory);

            Notification newNotification = b.build();

            // Undo any compatibility view inflation
            newNotification.contentView = sbn.getNotification().contentView;
            newNotification.bigContentView = sbn.getNotification().bigContentView;
            newNotification.headsUpContentView = sbn.getNotification().headsUpContentView;

            StatusBarNotification newSbn = new StatusBarNotification(sbn.getPackageName(),
                    sbn.getOpPkg(),
                    sbn.getId(), sbn.getTag(), sbn.getUid(), sbn.getInitialPid(),
                    newNotification, sbn.getUser(), sbn.getOverrideGroupKey(), sbn.getPostTime());
            boolean updated = false;
            try {
                updateNotification(newSbn, null);
                updated = true;
            } catch (InflationException e) {
                deferRemoval = false;
            }
            if (updated) {
                mKeysKeptForRemoteInput.add(entry.key);
                return;
            }
        }
        if (deferRemoval) {
            mLatestRankingMap = ranking;
            mHeadsUpEntriesToRemoveOnSwitch.add(mHeadsUpManager.getEntry(key));
            return;
        }
        Entry entry = mNotificationData.get(key);

        if (entry != null && mRemoteInputController.isRemoteInputActive(entry)
                && (entry.row != null && !entry.row.isDismissed())) {
            mLatestRankingMap = ranking;
            mRemoteInputEntriesToRemoveOnCollapse.add(entry);
            return;
        }
        if (entry != null && mNotificationGutsExposed != null
                && mNotificationGutsExposed == entry.row.getGuts() && entry.row.getGuts() != null
                && !entry.row.getGuts().isLeavebehind()) {
            Log.w(TAG, "Keeping notification because it's showing guts. " + key);
            mLatestRankingMap = ranking;
            mKeyToRemoveOnGutsClosed = key;
            return;
        }

        if (entry != null) {
            mForegroundServiceController.removeNotification(entry.notification);
        }

        if (entry != null && entry.row != null) {
            entry.row.setRemoved();
            mStackScroller.cleanUpViewState(entry.row);
        }
        // Let's remove the children if this was a summary
        handleGroupSummaryRemoved(key, ranking);
        StatusBarNotification old = removeNotificationViews(key, ranking);
        if (SPEW) Log.d(TAG, "removeNotification key=" + key + " old=" + old);

        if (old != null) {
            // Cancel the ticker if it's still running
            if (mTickerEnabled != 0) {
                try {
                    mTicker.removeEntry(old);
                } catch (Exception e) {}
            }
            if (CLOSE_PANEL_WHEN_EMPTIED && !hasActiveNotifications()
                    && !mNotificationPanel.isTracking() && !mNotificationPanel.isQsExpanded()) {
                if (mState == StatusBarState.SHADE) {
                    animateCollapsePanels();
                } else if (mState == StatusBarState.SHADE_LOCKED && !isCollapsing()) {
                    goToKeyguard();
                }
            }
        }
        setAreThereNotifications();
    }

    /**
     * Ensures that the group children are cancelled immediately when the group summary is cancelled
     * instead of waiting for the notification manager to send all cancels. Otherwise this could
     * lead to flickers.
     *
     * This also ensures that the animation looks nice and only consists of a single disappear
     * animation instead of multiple.
     *
     * @param key the key of the notification was removed
     * @param ranking the current ranking
     */
    private void handleGroupSummaryRemoved(String key,
            RankingMap ranking) {
        Entry entry = mNotificationData.get(key);
        if (entry != null && entry.row != null
                && entry.row.isSummaryWithChildren()) {
            if (entry.notification.getOverrideGroupKey() != null && !entry.row.isDismissed()) {
                // We don't want to remove children for autobundled notifications as they are not
                // always cancelled. We only remove them if they were dismissed by the user.
                return;
            }
            List<ExpandableNotificationRow> notificationChildren =
                    entry.row.getNotificationChildren();
            ArrayList<ExpandableNotificationRow> toRemove = new ArrayList<>();
            for (int i = 0; i < notificationChildren.size(); i++) {
                ExpandableNotificationRow row = notificationChildren.get(i);
                if ((row.getStatusBarNotification().getNotification().flags
                        & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                    // the child is a forground service notification which we can't remove!
                    continue;
                }
                toRemove.add(row);
                row.setKeepInParent(true);
                // we need to set this state earlier as otherwise we might generate some weird
                // animations
                row.setRemoved();
            }
        }
    }

    public void performRemoveNotification(StatusBarNotification n) {
        Entry entry = mNotificationData.get(n.getKey());
        if (mRemoteInputController.isRemoteInputActive(entry)) {
            mRemoteInputController.removeRemoteInput(entry, null);
        }
        // start old BaseStatusBar.performRemoveNotification.
        final String pkg = n.getPackageName();
        final String tag = n.getTag();
        final int id = n.getId();
        final int userId = n.getUserId();
        try {
            mBarService.onNotificationClear(pkg, tag, id, userId);
            if (FORCE_REMOTE_INPUT_HISTORY
                    && mKeysKeptForRemoteInput.contains(n.getKey())) {
                mKeysKeptForRemoteInput.remove(n.getKey());
            }
            removeNotification(n.getKey(), null);

        } catch (RemoteException ex) {
            // system process is dead if we're here.
        }
        if (mStackScroller.hasPulsingNotifications() && mHeadsUpManager.getAllEntries().isEmpty()) {
            // We were showing a pulse for a notification, but no notifications are pulsing anymore.
            // Finish the pulse.
            mDozeScrimController.pulseOutNow();
        }
        if (mPieController != null) {
            mPieController.updateNotifications();
        }
        // end old BaseStatusBar.performRemoveNotification.
    }

    private void updateNotificationShade() {
        if (mStackScroller == null) return;

        // Do not modify the notifications during collapse.
        if (isCollapsing()) {
            addPostCollapseAction(new Runnable() {
                @Override
                public void run() {
                    updateNotificationShade();
                }
            });
            return;
        }

        ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        ArrayList<ExpandableNotificationRow> toShow = new ArrayList<>(activeNotifications.size());
        final int N = activeNotifications.size();
        for (int i=0; i<N; i++) {
            Entry ent = activeNotifications.get(i);
            if (ent.row.isDismissed() || ent.row.isRemoved()) {
                // we don't want to update removed notifications because they could
                // temporarily become children if they were isolated before.
                continue;
            }
            int userId = ent.notification.getUserId();

            // Display public version of the notification if we need to redact.
            boolean devicePublic = isLockscreenPublicMode(mCurrentUserId);
            boolean userPublic = devicePublic || isLockscreenPublicMode(userId);
            boolean needsRedaction = needsRedaction(ent);
            boolean sensitive = userPublic && needsRedaction;
            boolean deviceSensitive = devicePublic
                    && !userAllowsPrivateNotificationsInPublic(mCurrentUserId);
            ent.row.setSensitive(sensitive, deviceSensitive);
            ent.row.setNeedsRedaction(needsRedaction);
            if (mGroupManager.isChildInGroupWithSummary(ent.row.getStatusBarNotification())) {
                ExpandableNotificationRow summary = mGroupManager.getGroupSummary(
                        ent.row.getStatusBarNotification());
                List<ExpandableNotificationRow> orderedChildren =
                        mTmpChildOrderMap.get(summary);
                if (orderedChildren == null) {
                    orderedChildren = new ArrayList<>();
                    mTmpChildOrderMap.put(summary, orderedChildren);
                }
                orderedChildren.add(ent.row);
            } else {
                toShow.add(ent.row);
            }

        }

        ArrayList<ExpandableNotificationRow> toRemove = new ArrayList<>();
        for (int i=0; i< mStackScroller.getChildCount(); i++) {
            View child = mStackScroller.getChildAt(i);
            if (!toShow.contains(child) && child instanceof ExpandableNotificationRow) {
                toRemove.add((ExpandableNotificationRow) child);
            }
        }

        for (ExpandableNotificationRow remove : toRemove) {
            if (mGroupManager.isChildInGroupWithSummary(remove.getStatusBarNotification())) {
                // we are only transfering this notification to its parent, don't generate an animation
                mStackScroller.setChildTransferInProgress(true);
            }
            if (remove.isSummaryWithChildren()) {
                remove.removeAllChildren();
            }
            mStackScroller.removeView(remove);
            mStackScroller.setChildTransferInProgress(false);
        }

        removeNotificationChildren();

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mVisualStabilityManager.notifyViewAddition(v);
                mStackScroller.addView(v);
            }
        }

        addNotificationChildrenAndSort();

        // So after all this work notifications still aren't sorted correctly.
        // Let's do that now by advancing through toShow and mStackScroller in
        // lock-step, making sure mStackScroller matches what we see in toShow.
        int j = 0;
        for (int i = 0; i < mStackScroller.getChildCount(); i++) {
            View child = mStackScroller.getChildAt(i);
            if (!(child instanceof ExpandableNotificationRow)) {
                // We don't care about non-notification views.
                continue;
            }

            ExpandableNotificationRow targetChild = toShow.get(j);
            if (child != targetChild) {
                // Oops, wrong notification at this position. Put the right one
                // here and advance both lists.
                if (mVisualStabilityManager.canReorderNotification(targetChild)) {
                    mStackScroller.changeViewPosition(targetChild, i);
                } else {
                    mVisualStabilityManager.addReorderingAllowedCallback(this);
                }
            }
            j++;

        }

        mVisualStabilityManager.onReorderingFinished();
        // clear the map again for the next usage
        mTmpChildOrderMap.clear();

        updateRowStates();
        updateSpeedBumpIndex();
        updateClearAll();
        updateEmptyShadeView();

        updateQsExpansionEnabled();

        // Let's also update the icons
        mNotificationIconAreaController.updateNotificationIcons(mNotificationData);
    }

    /** @return true if the entry needs redaction when on the lockscreen. */
    private boolean needsRedaction(Entry ent) {
        int userId = ent.notification.getUserId();

        boolean currentUserWantsRedaction = !userAllowsPrivateNotificationsInPublic(mCurrentUserId);
        boolean notiUserWantsRedaction = !userAllowsPrivateNotificationsInPublic(userId);
        boolean redactedLockscreen = currentUserWantsRedaction || notiUserWantsRedaction;

        boolean notificationRequestsRedaction =
                ent.notification.getNotification().visibility == Notification.VISIBILITY_PRIVATE;
        boolean userForcesRedaction = packageHasVisibilityOverride(ent.notification.getKey());

        return userForcesRedaction || notificationRequestsRedaction && redactedLockscreen;
    }

    /**
     * Disable QS if device not provisioned.
     * If the user switcher is simple then disable QS during setup because
     * the user intends to use the lock screen user switcher, QS in not needed.
     */
    private void updateQsExpansionEnabled() {
        mNotificationPanel.setQsExpansionEnabled(isDeviceProvisioned()
                && (mUserSetup || mUserSwitcherController == null
                        || !mUserSwitcherController.isSimpleUserSwitcher())
                && ((mDisabled2 & StatusBarManager.DISABLE2_QUICK_SETTINGS) == 0)
                && !mDozing
                && !ONLY_CORE_APPS);
    }

    private void addNotificationChildrenAndSort() {
        // Let's now add all notification children which are missing
        boolean orderChanged = false;
        for (int i = 0; i < mStackScroller.getChildCount(); i++) {
            View view = mStackScroller.getChildAt(i);
            if (!(view instanceof ExpandableNotificationRow)) {
                // We don't care about non-notification views.
                continue;
            }

            ExpandableNotificationRow parent = (ExpandableNotificationRow) view;
            List<ExpandableNotificationRow> children = parent.getNotificationChildren();
            List<ExpandableNotificationRow> orderedChildren = mTmpChildOrderMap.get(parent);

            for (int childIndex = 0; orderedChildren != null && childIndex < orderedChildren.size();
                    childIndex++) {
                ExpandableNotificationRow childView = orderedChildren.get(childIndex);
                if (children == null || !children.contains(childView)) {
                    if (childView.getParent() != null) {
                        Log.wtf(TAG, "trying to add a notification child that already has " +
                                "a parent. class:" + childView.getParent().getClass() +
                                "\n child: " + childView);
                        // This shouldn't happen. We can recover by removing it though.
                        ((ViewGroup) childView.getParent()).removeView(childView);
                    }
                    mVisualStabilityManager.notifyViewAddition(childView);
                    parent.addChildNotification(childView, childIndex);
                    mStackScroller.notifyGroupChildAdded(childView);
                }
            }

            // Finally after removing and adding has been beformed we can apply the order.
            orderChanged |= parent.applyChildOrder(orderedChildren, mVisualStabilityManager, this);
        }
        if (orderChanged) {
            mStackScroller.generateChildOrderChangedEvent();
        }
    }

    private void removeNotificationChildren() {
        // First let's remove all children which don't belong in the parents
        ArrayList<ExpandableNotificationRow> toRemove = new ArrayList<>();
        for (int i = 0; i < mStackScroller.getChildCount(); i++) {
            View view = mStackScroller.getChildAt(i);
            if (!(view instanceof ExpandableNotificationRow)) {
                // We don't care about non-notification views.
                continue;
            }

            ExpandableNotificationRow parent = (ExpandableNotificationRow) view;
            List<ExpandableNotificationRow> children = parent.getNotificationChildren();
            List<ExpandableNotificationRow> orderedChildren = mTmpChildOrderMap.get(parent);

            if (children != null) {
                toRemove.clear();
                for (ExpandableNotificationRow childRow : children) {
                    if ((orderedChildren == null
                            || !orderedChildren.contains(childRow))
                            && !childRow.keepInParent()) {
                        toRemove.add(childRow);
                    }
                }
                for (ExpandableNotificationRow remove : toRemove) {
                    parent.removeChildNotification(remove);
                    if (mNotificationData.get(remove.getStatusBarNotification().getKey()) == null) {
                        // We only want to add an animation if the view is completely removed
                        // otherwise it's just a transfer
                        mStackScroller.notifyGroupChildRemoved(remove,
                                parent.getChildrenContainer());
                    }
                }
            }
        }
    }

    public void addQsTile(ComponentName tile) {
        mQSPanel.getHost().addTile(tile);
    }

    public void remQsTile(ComponentName tile) {
        mQSPanel.getHost().removeTile(tile);
    }

    public void clickTile(ComponentName tile) {
        mQSPanel.clickTile(tile);
    }

    private boolean packageHasVisibilityOverride(String key) {
        return mNotificationData.getVisibilityOverride(key) == Notification.VISIBILITY_PRIVATE;
    }

    private void updateClearAll() {
        if (!mClearAllEnabled) {
            return;
        }
        boolean showDismissView = mState != StatusBarState.KEYGUARD
                && hasActiveClearableNotifications();
        mStackScroller.updateDismissView(showDismissView);
    }

    /**
     * Return whether there are any clearable notifications
     */
    private boolean hasActiveClearableNotifications() {
        int childCount = mStackScroller.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mStackScroller.getChildAt(i);
            if (!(child instanceof ExpandableNotificationRow)) {
                continue;
            }
            if (((ExpandableNotificationRow) child).canViewBeDismissed()) {
                    return true;
            }
        }
        return false;
    }

    private void updateEmptyShadeView() {
        boolean showEmptyShadeView =
                mState != StatusBarState.KEYGUARD &&
                        mNotificationData.getActiveNotifications().size() == 0;
        mNotificationPanel.showEmptyShadeView(showEmptyShadeView);
    }

    private void updateSpeedBumpIndex() {
        int speedBumpIndex = 0;
        int currentIndex = 0;
        final int N = mStackScroller.getChildCount();
        for (int i = 0; i < N; i++) {
            View view = mStackScroller.getChildAt(i);
            if (view.getVisibility() == View.GONE || !(view instanceof ExpandableNotificationRow)) {
                continue;
            }
            ExpandableNotificationRow row = (ExpandableNotificationRow) view;
            currentIndex++;
            if (!mNotificationData.isAmbient(row.getStatusBarNotification().getKey())) {
                speedBumpIndex = currentIndex;
            }
        }
        boolean noAmbient = speedBumpIndex == N;
        mStackScroller.updateSpeedBumpIndex(speedBumpIndex, noAmbient);
    }

    public static boolean isTopLevelChild(Entry entry) {
        return entry.row.getParent() instanceof NotificationStackScrollLayout;
    }

    protected void updateNotifications() {
        mNotificationData.filterAndSort();

        updateNotificationShade();
    }

    public void requestNotificationUpdate() {
        updateNotifications();
    }

    protected boolean hasActiveVisibleNotifications() {
        return mNotificationData.hasActiveVisibleNotifications();
    }

    protected boolean hasActiveOngoingNotifications() {
        return mNotificationData.hasActiveOngoingNotifications();
    }

    protected boolean hasActiveClearableNotificationsQS() {
        return hasActiveClearableNotifications();
    }

    protected void setAreThereNotifications() {

        if (SPEW) {
            final boolean clearable = hasActiveNotifications() &&
                    hasActiveClearableNotifications();
            Log.d(TAG, "setAreThereNotifications: N=" +
                    mNotificationData.getActiveNotifications().size() + " any=" +
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

        findAndUpdateMediaNotifications();
    }

    public void findAndUpdateMediaNotifications() {
        boolean metaDataChanged = false;

        synchronized (mNotificationData) {
            ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
            final int N = activeNotifications.size();

            // Promote the media notification with a controller in 'playing' state, if any.
            Entry mediaNotification = null;
            MediaController controller = null;
            for (int i = 0; i < N; i++) {
                final Entry entry = activeNotifications.get(i);
                if (isMediaNotification(entry)) {
                    final MediaSession.Token token =
                            entry.notification.getNotification().extras
                            .getParcelable(Notification.EXTRA_MEDIA_SESSION);
                    if (token != null) {
                        MediaController aController = new MediaController(mContext, token);
                        if (PlaybackState.STATE_PLAYING ==
                                getMediaControllerPlaybackState(aController)) {
                            if (DEBUG_MEDIA) {
                                Log.v(TAG, "DEBUG_MEDIA: found mediastyle controller matching "
                                        + entry.notification.getKey());
                            }
                            mediaNotification = entry;
                            controller = aController;
                            break;
                        }
                    }
                }
            }
            if (mediaNotification == null) {
                // Still nothing? OK, let's just look for live media sessions and see if they match
                // one of our notifications. This will catch apps that aren't (yet!) using media
                // notifications.

                if (mMediaSessionManager != null) {
                    final List<MediaController> sessions
                            = mMediaSessionManager.getActiveSessionsForUser(
                                    null,
                                    UserHandle.USER_ALL);

                    for (MediaController aController : sessions) {
                        if (PlaybackState.STATE_PLAYING ==
                                getMediaControllerPlaybackState(aController)) {
                            // now to see if we have one like this
                            final String pkg = aController.getPackageName();

                            for (int i = 0; i < N; i++) {
                                final Entry entry = activeNotifications.get(i);
                                if (entry.notification.getPackageName().equals(pkg)) {
                                    if (DEBUG_MEDIA) {
                                        Log.v(TAG, "DEBUG_MEDIA: found controller matching "
                                            + entry.notification.getKey());
                                    }
                                    controller = aController;
                                    mediaNotification = entry;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (controller != null && !sameSessions(mMediaController, controller)) {
                // We have a new media session
                clearCurrentMediaNotification();
                mMediaController = controller;
                mMediaController.registerCallback(mMediaListener);
                mMediaMetadata = mMediaController.getMetadata();
                if (mTicker != null) {
                    mTicker.resetShownMediaMetadata();
                }
                setMediaPlaying();
                if (DEBUG_MEDIA) {
                    Log.v(TAG, "DEBUG_MEDIA: insert listener, receive metadata: "
                            + mMediaMetadata);
                }

                if (mediaNotification != null) {
                    mMediaNotificationKey = mediaNotification.notification.getKey();
                    if (DEBUG_MEDIA) {
                        Log.v(TAG, "DEBUG_MEDIA: Found new media notification: key="
                                + mMediaNotificationKey + " controller=" + mMediaController);
                    }
                }
                metaDataChanged = true;
            }
        }

        if (metaDataChanged) {
            updateNotifications();
        }
        updateMediaMetaData(metaDataChanged, true);
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }

    private boolean isPlaybackActive(int state) {
        if (state != PlaybackState.STATE_STOPPED
                && state != PlaybackState.STATE_ERROR
                && state != PlaybackState.STATE_NONE) {
            return true;
        }
        return false;
    }

    private void clearCurrentMediaNotification() {
        mMediaNotificationKey = null;
        mMediaMetadata = null;
        if (mMediaController != null) {
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: Disconnecting from old controller: "
                        + mMediaController.getPackageName());
            }
            mMediaController.unregisterCallback(mMediaListener);
            setMediaPlaying();
        }
        mMediaController = null;
    }

    private boolean sameSessions(MediaController a, MediaController b) {
        if (a == b) return true;
        if (a == null) return false;
        return a.controlsSameSession(b);
    }

    /**
     * Hide the album artwork that is fading out and release its bitmap.
     */
    protected Runnable mHideBackdropFront = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: removing fade layer");
            }
            mBackdropFront.setVisibility(View.INVISIBLE);
            mBackdropFront.animate().cancel();
            mBackdropFront.setImageDrawable(null);
        }
    };

    /**
     * Refresh or remove lockscreen artwork from media metadata or the lockscreen wallpaper.
     */
    public void updateMediaMetaData(boolean metaDataChanged, boolean allowEnterAnimation) {
        Trace.beginSection("StatusBar#updateMediaMetaData");
        if (!SHOW_LOCKSCREEN_MEDIA_ARTWORK) {
            Trace.endSection();
            return;
        }

        if (mBackdrop == null) {
            Trace.endSection();
            return; // called too early
        }

        if (mLaunchTransitionFadingAway) {
            mBackdrop.setVisibility(View.INVISIBLE);
            Trace.endSection();
            return;
        }

        if (DEBUG_MEDIA) {
            Log.v(TAG, "DEBUG_MEDIA: updating album art for notification " + mMediaNotificationKey
                    + " metadata=" + mMediaMetadata
                    + " metaDataChanged=" + metaDataChanged
                    + " state=" + mState);
        }

        Drawable artworkDrawable = null;
        if (mMediaMetadata != null && mLockscreenMediaMetadata) {
            Bitmap artworkBitmap = null;
            artworkBitmap = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (artworkBitmap == null) {
                artworkBitmap = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                // might still be null
            }
            if (artworkBitmap != null) {
                artworkDrawable = new BitmapDrawable(mBackdropBack.getResources(), artworkBitmap);
            }
        }
        mKeyguardShowingMedia = artworkDrawable != null;

        boolean allowWhenShade = false;
        if (ENABLE_LOCKSCREEN_WALLPAPER && artworkDrawable == null) {
            Bitmap lockWallpaper = mLockscreenWallpaper.getBitmap();
            if (lockWallpaper != null) {
                artworkDrawable = new LockscreenWallpaper.WallpaperDrawable(
                        mBackdropBack.getResources(), lockWallpaper);
                // We're in the SHADE mode on the SIM screen - yet we still need to show
                // the lockscreen wallpaper in that mode.
                allowWhenShade = mStatusBarKeyguardViewManager != null
                        && mStatusBarKeyguardViewManager.isShowing();
            }
        }

        boolean hideBecauseOccluded = mStatusBarKeyguardViewManager != null
                && mStatusBarKeyguardViewManager.isOccluded();

        final boolean keyguardVisible = (mState != StatusBarState.SHADE);
        final boolean hasArtwork = artworkDrawable != null;

        if (!mKeyguardFadingAway && keyguardVisible && hasArtwork && mScreenOn) {
            // if there's album art, ensure visualizer is visible
            mVisualizerView.setPlaying(mMediaController != null
                    && mMediaController.getPlaybackState() != null
                    && mMediaController.getPlaybackState().getState()
                            == PlaybackState.STATE_PLAYING);
        }

        if (keyguardVisible && mKeyguardShowingMedia &&
                (artworkDrawable instanceof BitmapDrawable)) {
            // always use current backdrop to color eq
            mVisualizerView.setBitmap(((BitmapDrawable)artworkDrawable).getBitmap());
        }

        if ((hasArtwork || DEBUG_MEDIA_FAKE_ARTWORK)
                && (mState != StatusBarState.SHADE || allowWhenShade)
                && mFingerprintUnlockController.getMode()
                        != FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING
                && !hideBecauseOccluded) {
            // time to show some art!
            if (mBackdrop.getVisibility() != View.VISIBLE) {
                mBackdrop.setVisibility(View.VISIBLE);
                if (allowEnterAnimation) {
                    mBackdrop.setAlpha(SRC_MIN_ALPHA);
                    mBackdrop.animate().alpha(1f);
                } else {
                    mBackdrop.animate().cancel();
                    mBackdrop.setAlpha(1f);
                }
                mStatusBarWindowManager.setBackdropShowing(true);
                metaDataChanged = true;
                if (DEBUG_MEDIA) {
                    Log.v(TAG, "DEBUG_MEDIA: Fading in album artwork");
                }
            }
            if (metaDataChanged) {
                if (mBackdropBack.getDrawable() != null) {
                    Drawable drawable =
                            mBackdropBack.getDrawable().getConstantState()
                                    .newDrawable(mBackdropFront.getResources()).mutate();
                    mBackdropFront.setImageDrawable(drawable);
                    if (mScrimSrcModeEnabled) {
                        mBackdropFront.getDrawable().mutate().setXfermode(mSrcOverXferMode);
                    }
                    mBackdropFront.setAlpha(1f);
                    mBackdropFront.setVisibility(View.VISIBLE);
                } else {
                    mBackdropFront.setVisibility(View.INVISIBLE);
                }

                if (DEBUG_MEDIA_FAKE_ARTWORK) {
                    final int c = 0xFF000000 | (int)(Math.random() * 0xFFFFFF);
                    Log.v(TAG, String.format("DEBUG_MEDIA: setting new color: 0x%08x", c));
                    mBackdropBack.setBackgroundColor(0xFFFFFFFF);
                    mBackdropBack.setImageDrawable(new ColorDrawable(c));
                } else {
                    mBackdropBack.setImageDrawable(artworkDrawable);
                }
                if (mScrimSrcModeEnabled) {
                    mBackdropBack.getDrawable().mutate().setXfermode(mSrcXferMode);
                }

                if (mBackdropFront.getVisibility() == View.VISIBLE) {
                    if (DEBUG_MEDIA) {
                        Log.v(TAG, "DEBUG_MEDIA: Crossfading album artwork from "
                                + mBackdropFront.getDrawable()
                                + " to "
                                + mBackdropBack.getDrawable());
                    }
                    mBackdropFront.animate()
                            .setDuration(250)
                            .alpha(0f).withEndAction(mHideBackdropFront);
                }
            }
        } else {
            // need to hide the album art, either because we are unlocked or because
            // the metadata isn't there to support it
            if (mBackdrop.getVisibility() != View.GONE) {
                if (DEBUG_MEDIA) {
                    Log.v(TAG, "DEBUG_MEDIA: Fading out album artwork");
                }
                int fpMode = mFingerprintUnlockController.getMode();
                if (fpMode == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING ||
                        fpMode == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK ||
                        hideBecauseOccluded) {

                    // We are unlocking directly - no animation!
                    mBackdrop.setVisibility(View.GONE);
                    mBackdropBack.setImageDrawable(null);
                    mStatusBarWindowManager.setBackdropShowing(false);
                } else {
                    mStatusBarWindowManager.setBackdropShowing(false);
                    mBackdrop.animate()
                            .alpha(SRC_MIN_ALPHA)
                            .setInterpolator(Interpolators.ACCELERATE_DECELERATE)
                            .setDuration(300)
                            .setStartDelay(0)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    mBackdrop.setVisibility(View.GONE);
                                    mBackdropFront.animate().cancel();
                                    mBackdropBack.setImageDrawable(null);
                                    mHandler.post(mHideBackdropFront);
                                }
                            });
                    if (mKeyguardFadingAway) {
                        mBackdrop.animate()
                                // Make it disappear faster, as the focus should be on the activity
                                // behind.
                                .setDuration(mKeyguardFadingAwayDuration / 2)
                                .setStartDelay(mKeyguardFadingAwayDelay)
                                .setInterpolator(Interpolators.LINEAR)
                                .start();
                    }
                }
            }
        }
        Trace.endSection();
    }

    private void updateReportRejectedTouchVisibility() {
        if (mReportRejectedTouch == null) {
            return;
        }
        mReportRejectedTouch.setVisibility(mState == StatusBarState.KEYGUARD
                && mFalsingManager.isReportingEnabled() ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * State is one or more of the DISABLE constants from StatusBarManager.
     */
    @Override
    public void disable(int state1, int state2, boolean animate) {
        animate &= mStatusBarWindowState != WINDOW_STATE_HIDDEN;
        mDisabledUnmodified1 = state1;
        mDisabledUnmodified2 = state2;
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
        flagdbg.append(0 != ((state2 & StatusBarManager.DISABLE2_QUICK_SETTINGS))       ? 'Q' : 'q');
        flagdbg.append(0 != ((diff2  & StatusBarManager.DISABLE2_QUICK_SETTINGS))       ? '!' : ' ');
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
            mDisableNotificationAlerts =
                    (state1 & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0;
            mHeadsUpObserver.onChange(true);
        }

         if ((diff1 & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0
                && (state1 & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0
                && mTicking) {
            haltTicker();
        }

        if ((diff2 & StatusBarManager.DISABLE2_QUICK_SETTINGS) != 0) {
            updateQsExpansionEnabled();
        }
    }

    /**
     * Reapplies the disable flags as last requested by StatusBarManager.
     *
     * This needs to be called if state used by {@link #adjustDisableFlags} changes.
     */
    public void recomputeDisableFlags(boolean animate) {
        mCommandQueue.recomputeDisableFlags(animate);
    }

    protected H createHandler() {
        return new StatusBar.H();
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
                false /* disallowEnterPictureInPictureWhileLaunching */, callback);
    }

    public void setQsExpanded(boolean expanded) {
        mStatusBarWindowManager.setQsExpanded(expanded);
        mNotificationPanel.setStatusAccessibilityImportance(expanded
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    public boolean isGoingToNotificationShade() {
        return mLeaveOpenOnKeyguardHide;
    }

    public boolean isWakeUpComingFromTouch() {
        return mWakeUpComingFromTouch;
    }

    public boolean isFalsingThresholdNeeded() {
        return getBarState() == StatusBarState.KEYGUARD;
    }

    public boolean isDozing() {
        return mDozing;
    }

    @Override  // NotificationData.Environment
    public String getCurrentMediaNotificationKey() {
        return mMediaNotificationKey;
    }

    public boolean isScrimSrcModeEnabled() {
        return mScrimSrcModeEnabled;
    }

    /**
     * To be called when there's a state change in StatusBarKeyguardViewManager.
     */
    public void onKeyguardViewManagerStatesUpdated() {
        logStateToEventlog();
    }

    @Override  // UnlockMethodCache.OnUnlockMethodChangedListener
    public void onUnlockMethodStateChanged() {
        logStateToEventlog();
    }

    @Override
    public void onHeadsUpPinnedModeChanged(boolean inPinnedMode) {
        if (inPinnedMode) {
            mStatusBarWindowManager.setHeadsUpShowing(true);
            mStatusBarWindowManager.setForceStatusBarVisible(true);
            if (mNotificationPanel.isFullyCollapsed()) {
                // We need to ensure that the touchable region is updated before the window will be
                // resized, in order to not catch any touches. A layout will ensure that
                // onComputeInternalInsets will be called and after that we can resize the layout. Let's
                // make sure that the window stays small for one frame until the touchableRegion is set.
                mNotificationPanel.requestLayout();
                mStatusBarWindowManager.setForceWindowCollapsed(true);
                mNotificationPanel.post(new Runnable() {
                    @Override
                    public void run() {
                        mStatusBarWindowManager.setForceWindowCollapsed(false);
                    }
                });
            }
        } else {
            if (!mNotificationPanel.isFullyCollapsed() || mNotificationPanel.isTracking()) {
                // We are currently tracking or is open and the shade doesn't need to be kept
                // open artificially.
                mStatusBarWindowManager.setHeadsUpShowing(false);
            } else {
                // we need to keep the panel open artificially, let's wait until the animation
                // is finished.
                mHeadsUpManager.setHeadsUpGoingAway(true);
                mStackScroller.runAfterAnimationFinished(new Runnable() {
                    @Override
                    public void run() {
                        if (!mHeadsUpManager.hasPinnedHeadsUp()) {
                            mStatusBarWindowManager.setHeadsUpShowing(false);
                            mHeadsUpManager.setHeadsUpGoingAway(false);
                        }
                        removeRemoteInputEntriesKeptUntilCollapsed();
                    }
                });
            }
        }
    }

    @Override
    public void onHeadsUpPinned(ExpandableNotificationRow headsUp) {
        dismissVolumeDialog();
    }

    @Override
    public void onHeadsUpUnPinned(ExpandableNotificationRow headsUp) {
    }

    @Override
    public void onHeadsUpStateChanged(Entry entry, boolean isHeadsUp) {
        if (!isHeadsUp && mHeadsUpEntriesToRemoveOnSwitch.contains(entry)) {
            removeNotification(entry.key, mLatestRankingMap);
            mHeadsUpEntriesToRemoveOnSwitch.remove(entry);
            if (mHeadsUpEntriesToRemoveOnSwitch.isEmpty()) {
                mLatestRankingMap = null;
            }
        } else {
            updateNotificationRanking(null);
            if (isHeadsUp) {
                mDozeServiceHost.fireNotificationHeadsUp();
            }
        }

    }

    protected void updateHeadsUp(String key, Entry entry, boolean shouldPeek,
            boolean alertAgain) {
        final boolean wasHeadsUp = isHeadsUp(key);
        if (wasHeadsUp) {
            if (!shouldPeek) {
                // We don't want this to be interrupting anymore, lets remove it
                mHeadsUpManager.removeNotification(key, false /* ignoreEarliestRemovalTime */);
            } else {
                mHeadsUpManager.updateNotification(entry, alertAgain);
            }
        } else if (shouldPeek && alertAgain) {
            // This notification was updated to be a heads-up, show it!
            mHeadsUpManager.showNotification(entry);
        }
    }

    protected void setHeadsUpUser(int newUserId) {
        if (mHeadsUpManager != null) {
            mHeadsUpManager.setUser(newUserId);
        }
    }

    public boolean isHeadsUp(String key) {
        return mHeadsUpManager.isHeadsUp(key);
    }

    protected boolean isSnoozedPackage(StatusBarNotification sbn) {
        return mHeadsUpManager.isSnoozed(sbn.getPackageName());
    }

    public boolean isKeyguardCurrentlySecure() {
        return !mUnlockMethodCache.canSkipBouncer();
    }

    public void setPanelExpanded(boolean isExpanded) {
        mPanelExpanded = isExpanded;
        updateHideIconsForBouncer(false /* animate */);
        mStatusBarWindowManager.setPanelExpanded(isExpanded);
        mVisualStabilityManager.setPanelExpanded(isExpanded);
        if (isExpanded && getBarState() != StatusBarState.KEYGUARD) {
            if (DEBUG) {
                Log.v(TAG, "clearing notification effects from setPanelExpanded");
            }
            clearNotificationEffects();
        }

        if (!isExpanded) {
            removeRemoteInputEntriesKeptUntilCollapsed();
        }
        if (mNavigationBar != null) {
            mNavigationBar.setPanelExpanded(isExpanded);
        }
    }

    private void removeRemoteInputEntriesKeptUntilCollapsed() {
        for (int i = 0; i < mRemoteInputEntriesToRemoveOnCollapse.size(); i++) {
            Entry entry = mRemoteInputEntriesToRemoveOnCollapse.valueAt(i);
            mRemoteInputController.removeRemoteInput(entry, null);
            removeNotification(entry.key, mLatestRankingMap);
        }
        mRemoteInputEntriesToRemoveOnCollapse.clear();
    }

    public NotificationStackScrollLayout getNotificationScrollLayout() {
        return mStackScroller;
    }

    public boolean isPulsing() {
        return mDozeScrimController.isPulsing();
    }

    @Override
    public void onReorderingAllowed() {
        updateNotifications();
    }

    public boolean isLaunchTransitionFadingAway() {
        return mLaunchTransitionFadingAway;
    }

    public boolean hideStatusBarIconsWhenExpanded() {
        return mNotificationPanel.hideStatusBarIconsWhenExpanded();
    }

    @Override
    public void onColorsChanged(ColorExtractor extractor, int which) {
        updateTheme();
    }

    public boolean isUsingDarkOrBlackTheme() {
        OverlayInfo systemuiThemeInfoDark = null;
        OverlayInfo systemuiThemeInfoBlack = null;
        try {
            systemuiThemeInfoDark = mOverlayManager.getOverlayInfo("org.lineageos.overlay.dark",
                    mCurrentUserId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        try {
            systemuiThemeInfoBlack = mOverlayManager.getOverlayInfo("org.lineageos.overlay.black",
                    mCurrentUserId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return (systemuiThemeInfoDark != null && systemuiThemeInfoDark.isEnabled()) || 
        (systemuiThemeInfoBlack != null && systemuiThemeInfoBlack.isEnabled());
    }

    private boolean isLiveDisplayNightModeOn() {
        // SystemUI is initialized before LiveDisplay, so the service may not
        // be ready when this is called the first time
        LiveDisplayManager manager = LiveDisplayManager.getInstance(mContext);
        try {
            return manager.isNightModeEnabled();
        } catch (NullPointerException e) {
            Log.w(TAG, e.getMessage());
        }
        return false;
    }

    @Nullable
    public View getAmbientIndicationContainer() {
        return mAmbientIndicationContainer;
    }

    public void setOccluded(boolean occluded) {
        mIsOccluded = occluded;
        updateHideIconsForBouncer(false /* animate */);
    }

    public boolean hideStatusBarIconsForBouncer() {
        return mHideIconsForBouncer || mWereIconsJustHidden;
    }

    /**
     * @param animate should the change of the icons be animated.
     */
    private void updateHideIconsForBouncer(boolean animate) {
        boolean shouldHideIconsForBouncer = !mPanelExpanded && mTopHidesStatusBar && mIsOccluded
                && (mBouncerShowing || mStatusBarWindowHidden);
        if (mHideIconsForBouncer != shouldHideIconsForBouncer) {
            mHideIconsForBouncer = shouldHideIconsForBouncer;
            if (!shouldHideIconsForBouncer && mBouncerWasShowingWhenHidden) {
                // We're delaying the showing, since most of the time the fullscreen app will
                // hide the icons again and we don't want them to fade in and out immediately again.
                mWereIconsJustHidden = true;
                mHandler.postDelayed(() -> {
                    mWereIconsJustHidden = false;
                    recomputeDisableFlags(true);
                }, 500);
            } else {
                recomputeDisableFlags(animate);
            }
        }
        if (shouldHideIconsForBouncer) {
            mBouncerWasShowingWhenHidden = mBouncerShowing;
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
        Collection<HeadsUpManager.HeadsUpEntry> entries = mHeadsUpManager.getAllEntries();
        for (HeadsUpManager.HeadsUpEntry entry : entries) {
            final StatusBarNotification sbn = entry.entry.notification;
            final Notification notification = sbn.getNotification();
            if (notification.fullScreenIntent != null) {
                if (DEBUG) {
                    Log.d(TAG, "converting a heads up to fullScreen");
                }
                try {
                    EventLog.writeEvent(EventLogTags.SYSUI_HEADS_UP_ESCALATION,
                            sbn.getKey());
                    notification.fullScreenIntent.send();
                    entry.entry.notifyFullScreenIntentLaunched();
                } catch (PendingIntent.CanceledException e) {
                }
            }
        }
        mHeadsUpManager.releaseAllImmediately();
    }

    /**
     * Called for system navigation gestures. First action opens the panel, second opens
     * settings. Down action closes the entire panel.
     */
    @Override
    public void handleSystemKey(int key) {
        if (SPEW) Log.d(TAG, "handleNavigationKey: " + key);
        if (!panelsEnabled() || !mKeyguardMonitor.isDeviceInteractive()
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
                if (mFingerprintQuickPulldown) {
                    mNotificationPanel.expandWithQs();
                    mMetricsLogger.count(NotificationPanelView.COUNTER_PANEL_OPEN_QS, 1);
                } else {
                    mNotificationPanel.expand(true /* animate */);
                    mMetricsLogger.count(NotificationPanelView.COUNTER_PANEL_OPEN, 1);
                }
            } else if (!mNotificationPanel.isInSettings() && !mNotificationPanel.isExpanding()){
                mNotificationPanel.flingSettings(0 /* velocity */, true /* expand */);
                mMetricsLogger.count(NotificationPanelView.COUNTER_PANEL_OPEN_QS, 1);
            }
        } else if (mFpDismissNotifications && (KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT == key
                || KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT == key)) {
            if (!mNotificationPanel.isFullyCollapsed() && !mNotificationPanel.isExpanding()){
                mMetricsLogger.action(MetricsEvent.ACTION_DISMISS_ALL_NOTES);
                clearAllNotifications(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT == key ? true : false);
            }
        }

    }

    boolean panelsEnabled() {
        return (mDisabled1 & StatusBarManager.DISABLE_EXPAND) == 0 && !ONLY_CORE_APPS;
    }

    void makeExpandedVisible(boolean force) {
        NotificationPanelView.startBlurTask();
        if (SPEW) Log.d(TAG, "Make expanded visible: expanded visible=" + mExpandedVisible);
        if (!force && (mExpandedVisible || !panelsEnabled())) {
            return;
        }

        mExpandedVisible = true;

        // Expand the window to encompass the full screen in anticipation of the drag.
        // This is only possible to do atomically because the status bar is at the top of the screen!
        mStatusBarWindowManager.setPanelVisible(true);

        visibilityChanged(true);
        mWaitingForKeyguardExit = false;
        recomputeDisableFlags(!force /* animate */);
        setInteracting(StatusBarManager.WINDOW_STATUS_BAR, true);
    }

    public void animateCollapsePanels() {
        animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
    }

    private final Runnable mAnimateCollapsePanels = new Runnable() {
        @Override
        public void run() {
            animateCollapsePanels();
        }
    };

    public void postAnimateCollapsePanels() {
        mHandler.post(mAnimateCollapsePanels);
    }

    public void postAnimateForceCollapsePanels() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE, true /* force */);
            }
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
    public void animateCollapsePanels(int flags) {
        animateCollapsePanels(flags, false /* force */, false /* delayed */,
                1.0f /* speedUpFactor */);
    }

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

        if (mStatusBarWindow != null && mNotificationPanel.canPanelBeCollapsed()) {
            // release focus immediately to kick off focus change transition
            mStatusBarWindowManager.setStatusBarFocusable(false);

            mStatusBarWindow.cancelExpandHelper();
            mStatusBarView.collapsePanel(true /* animate */, delayed, speedUpFactor);
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

    @Override
    public void animateExpandNotificationsPanel() {
        if (SPEW) Log.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if (!panelsEnabled()) {
            return ;
        }

        mNotificationPanel.expand(true /* animate */);

        if (false) postStartTracing();
    }

    @Override
    public void animateExpandSettingsPanel(String subPanel) {
        if (SPEW) Log.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if (!panelsEnabled()) {
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
        mStatusBarWindowManager.setPanelVisible(false);
        mStatusBarWindowManager.setForceStatusBarVisible(false);

        // Close any guts that might be visible
        closeAndSaveGuts(true /* removeLeavebehind */, true /* force */, true /* removeControls */,
                -1 /* x */, -1 /* y */, true /* resetMenu */);

        runPostCollapseRunnables();
        setInteracting(StatusBarManager.WINDOW_STATUS_BAR, false);
        showBouncerIfKeyguard();
        recomputeDisableFlags(mNotificationPanel.hideStatusBarIconsWhenExpanded() /* animate */);

        // Trimming will happen later if Keyguard is showing - doing it here might cause a jank in
        // the bouncer appear animation.
        if (!mStatusBarKeyguardViewManager.isShowing()) {
            WindowManagerGlobal.getInstance().trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        }
        NotificationPanelView.recycle();
    }

    private void adjustBrightness(int x) {
        mBrightnessChanged = true;
        float raw = ((float) x) / mScreenWidth;

        // Add a padding to the brightness control on both sides to
        // make it easier to reach min/max brightness
        float padded = Math.min(1.0f - BRIGHTNESS_CONTROL_PADDING,
                Math.max(BRIGHTNESS_CONTROL_PADDING, raw));
        float value = (padded - BRIGHTNESS_CONTROL_PADDING) /
                (1 - (2.0f * BRIGHTNESS_CONTROL_PADDING));
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));
            if (power != null) {
                if (mAutomaticBrightness) {
                    float adj = (2 * value) - 1;
                    adj = Math.max(adj, -1);
                    adj = Math.min(adj, 1);
                    final float val = adj;
                    power.setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(val);
                    AsyncTask.execute(new Runnable() {
                        public void run() {
                            Settings.System.putFloatForUser(mContext.getContentResolver(),
                                    Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, val,
                                    UserHandle.USER_CURRENT);
                        }
                    });
                } else {
                    int newBrightness = mMinBrightness + (int) Math.round(value *
                            (android.os.PowerManager.BRIGHTNESS_ON - mMinBrightness));
                    newBrightness = Math.min(newBrightness, android.os.PowerManager.BRIGHTNESS_ON);
                    newBrightness = Math.max(newBrightness, mMinBrightness);
                    final int val = newBrightness;
                    power.setTemporaryScreenBrightnessSettingOverride(val);
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
        } catch (RemoteException e) {
            Log.w(TAG, "Setting Brightness failed: " + e);
        }
    }

    private void brightnessControl(MotionEvent event) {
        final int action = event.getAction();
        final int x = (int) event.getRawX();
        final int y = (int) event.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            if (y < mStatusBarHeaderHeight) {
                mLinger = 0;
                mInitialTouchX = x;
                mInitialTouchY = y;
                mJustPeeked = true;
                mHandler.removeCallbacks(mLongPressBrightnessChange);
                mHandler.postDelayed(mLongPressBrightnessChange,
                        BRIGHTNESS_CONTROL_LONG_PRESS_TIMEOUT);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (y < mStatusBarHeaderHeight && mJustPeeked) {
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
                if (y > mStatusBarHeaderHeight) {
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
                + mDisabled1 + " mDisabled2=" + mDisabled2 + " mTracking=" + mTracking);
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

    public FingerprintUnlockController getFingerprintUnlockController() {
        return mFingerprintUnlockController;
    }

    @Override // CommandQueue
    public void setWindowState(int window, int state) {
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
    public void setSystemUiVisibility(int vis, int fullscreenStackVis, int dockedStackVis,
            int mask, Rect fullscreenStackBounds, Rect dockedStackBounds) {
        final int oldVal = mSystemUiVisibility;
        final int newVal = (oldVal&~mask) | (vis&mask);
        final int diff = newVal ^ oldVal;
        if (DEBUG) Log.d(TAG, String.format(
                "setSystemUiVisibility vis=%s mask=%s oldVal=%s newVal=%s diff=%s",
                Integer.toHexString(vis), Integer.toHexString(mask),
                Integer.toHexString(oldVal), Integer.toHexString(newVal),
                Integer.toHexString(diff)));
        boolean sbModeChanged = false;
        if (diff != 0) {
            mSystemUiVisibility = newVal;

            // update low profile
            if ((diff & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
                final boolean lightsOut = (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0;
                if (lightsOut) {
                    animateCollapsePanels();
                    if (mTicking) {
                        haltTicker();
                    }
                }
                setAreThereNotifications();
            }

            // ready to unhide
            if ((vis & View.STATUS_BAR_UNHIDE) != 0) {
                mSystemUiVisibility &= ~View.STATUS_BAR_UNHIDE;
                mNoAnimationOnNextBarModeChange = true;
            }

            // update status bar mode
            final int sbMode = computeStatusBarMode(oldVal, newVal);

            sbModeChanged = sbMode != -1;
            if (sbModeChanged && sbMode != mStatusBarMode) {
                if (sbMode != mStatusBarMode) {
                    mStatusBarMode = sbMode;
                    checkBarModes();
                }
                touchAutoHide();
            }

            if ((vis & View.NAVIGATION_BAR_UNHIDE) != 0) {
                mSystemUiVisibility &= ~View.NAVIGATION_BAR_UNHIDE;
            }

            // send updated sysui visibility to window manager
            notifyUiVisibilityChanged(mSystemUiVisibility);
        }

        mLightBarController.onSystemUiVisibilityChanged(fullscreenStackVis, dockedStackVis,
                mask, fullscreenStackBounds, dockedStackBounds, sbModeChanged, mStatusBarMode);
    }

    @Override
    public void toggleFlashlight() {
        if (mFlashlightController != null) {
            mFlashlightController.initFlashLight();
            if (mFlashlightController.hasFlashlight() && mFlashlightController.isAvailable()) {
                mFlashlightController.setFlashlight(!mFlashlightController.isEnabled());
            }
        }
    }

    void touchAutoHide() {
        // update transient bar autohide
        if (mStatusBarMode == MODE_SEMI_TRANSPARENT || (mNavigationBar != null
                && mNavigationBar.isSemiTransparent())) {
            scheduleAutohide();
        } else {
            cancelAutohide();
        }
    }

    protected int computeStatusBarMode(int oldVal, int newVal) {
        return computeBarMode(oldVal, newVal, View.STATUS_BAR_TRANSIENT,
                View.STATUS_BAR_TRANSLUCENT, View.STATUS_BAR_TRANSPARENT);
    }

    protected BarTransitions getStatusBarTransitions() {
        return mStatusBarView.getBarTransitions();
    }

    @Override  // CommandQueue
    public void setAutoRotate(boolean enabled) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION,
                enabled ? 1 : 0);
    }

    protected int computeBarMode(int oldVis, int newVis,
            int transientFlag, int translucentFlag, int transparentFlag) {
        final int oldMode = barMode(oldVis, transientFlag, translucentFlag, transparentFlag);
        final int newMode = barMode(newVis, transientFlag, translucentFlag, transparentFlag);
        if (oldMode == newMode) {
            return -1; // no mode change
        }
        return newMode;
    }

    private int barMode(int vis, int transientFlag, int translucentFlag, int transparentFlag) {
        int lightsOutTransparent = View.SYSTEM_UI_FLAG_LOW_PROFILE | transparentFlag;
        return (vis & transientFlag) != 0 ? MODE_SEMI_TRANSPARENT
                : (vis & translucentFlag) != 0 ? MODE_TRANSLUCENT
                : (vis & lightsOutTransparent) == lightsOutTransparent ? MODE_LIGHTS_OUT_TRANSPARENT
                : (vis & transparentFlag) != 0 ? MODE_TRANSPARENT
                : (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0 ? MODE_LIGHTS_OUT
                : MODE_OPAQUE;
    }

    void checkBarModes() {
        if (mDemoMode) return;
        if (mStatusBarView != null) checkBarMode(mStatusBarMode, mStatusBarWindowState,
                getStatusBarTransitions());
        if (mNavigationBar != null) mNavigationBar.checkNavBarModes();
        mNoAnimationOnNextBarModeChange = false;
    }

    // Called by NavigationBarFragment
    void setQsScrimEnabled(boolean scrimEnabled) {
        mNotificationPanel.setQsScrimEnabled(scrimEnabled);
    }

    void checkBarMode(int mode, int windowState, BarTransitions transitions) {
        final boolean powerSave = mBatteryController.isPowerSave();
        final boolean anim = !mNoAnimationOnNextBarModeChange && mDeviceInteractive
                && windowState != WINDOW_STATE_HIDDEN && !powerSave;
        if (powerSave && getBarState() == StatusBarState.SHADE) {
            mode = MODE_POWERSAVE_WARNING;
        }
        if (mode == MODE_WARNING) {
            transitions.setBatterySaverColor(mBatterySaverColor);
        }
        transitions.transitionTo(mode, anim);
    }

    private void finishBarAnimations() {
        if (mStatusBarView != null) {
            mStatusBarView.getBarTransitions().finishAnimations();
        }
        if (mNavigationBar != null) {
            mNavigationBar.finishBarAnimations();
        }
    }

    private final Runnable mCheckBarModes = new Runnable() {
        @Override
        public void run() {
            checkBarModes();
        }
    };

    public void setInteracting(int barWindow, boolean interacting) {
        final boolean changing = ((mInteractingWindows & barWindow) != 0) != interacting;
        mInteractingWindows = interacting
                ? (mInteractingWindows | barWindow)
                : (mInteractingWindows & ~barWindow);
        if (mInteractingWindows != 0) {
            suspendAutohide();
        } else {
            resumeSuspendedAutohide();
        }
        // manually dismiss the volume panel when interacting with the nav bar
        if (changing && interacting && barWindow == StatusBarManager.WINDOW_NAVIGATION_BAR) {
            touchAutoDim();
            dismissVolumeDialog();
        }
        checkBarModes();
    }

    private void dismissVolumeDialog() {
        if (mVolumeComponent != null) {
            mVolumeComponent.dismissNow();
        }
    }

    private void resumeSuspendedAutohide() {
        if (mAutohideSuspended) {
            scheduleAutohide();
            mHandler.postDelayed(mCheckBarModes, 500); // longer than home -> launcher
        }
    }

    private void suspendAutohide() {
        mHandler.removeCallbacks(mAutohide);
        mHandler.removeCallbacks(mCheckBarModes);
        mAutohideSuspended = (mSystemUiVisibility & STATUS_OR_NAV_TRANSIENT) != 0;
    }

    private void cancelAutohide() {
        mAutohideSuspended = false;
        mHandler.removeCallbacks(mAutohide);
    }

    private void scheduleAutohide() {
        cancelAutohide();
        mHandler.postDelayed(mAutohide, AUTOHIDE_TIMEOUT_MS);
    }

    public void touchAutoDim() {
        if (mNavigationBar != null) {
            mNavigationBar.getBarTransitions().setAutoDim(false);
        }
        mHandler.removeCallbacks(mAutoDim);
        if (mState != StatusBarState.KEYGUARD && mState != StatusBarState.SHADE_LOCKED) {
            mHandler.postDelayed(mAutoDim, AUTOHIDE_TIMEOUT_MS);
        }
    }

    void checkUserAutohide(View v, MotionEvent event) {
        if ((mSystemUiVisibility & STATUS_OR_NAV_TRANSIENT) != 0  // a transient bar is revealed
                && event.getAction() == MotionEvent.ACTION_OUTSIDE // touch outside the source bar
                && event.getX() == 0 && event.getY() == 0  // a touch outside both bars
                && !mRemoteInputController.isRemoteInputActive()) { // not due to typing in IME
            userAutohide();
        }
    }

    private void checkRemoteInputOutside(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE // touch outside the source bar
                && event.getX() == 0 && event.getY() == 0  // a touch outside both bars
                && mRemoteInputController.isRemoteInputActive()) {
            mRemoteInputController.closeRemoteInputs();
        }
    }

    private void userAutohide() {
        cancelAutohide();
        mHandler.postDelayed(mAutohide, 350); // longer than app gesture -> flag clear
    }

    private boolean areLightsOn() {
        return 0 == (mSystemUiVisibility & View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    public void setLightsOn(boolean on) {
        Log.v(TAG, "setLightsOn(" + on + ")");
        if (on) {
            setSystemUiVisibility(0, 0, 0, View.SYSTEM_UI_FLAG_LOW_PROFILE,
                    mLastFullscreenStackBounds, mLastDockedStackBounds);
        } else {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, 0, 0,
                    View.SYSTEM_UI_FLAG_LOW_PROFILE, mLastFullscreenStackBounds,
                    mLastDockedStackBounds);
        }
    }

    private void notifyUiVisibilityChanged(int vis) {
        try {
            if (mLastDispatchedSystemUiVisibility != vis) {
                mWindowManagerService.statusBarVisibilityChanged(vis);
                mLastDispatchedSystemUiVisibility = vis;
            }
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void topAppWindowChanged(boolean showMenu) {
        if (SPEW) {
            Log.d(TAG, (showMenu?"showing":"hiding") + " the MENU button");
        }

        // See above re: lights-out policy for legacy apps.
        if (showMenu) setLightsOn(true);
    }

    private void tick(StatusBarNotification n, boolean firstTime, boolean isMusic, MediaMetadata metaMediaData) {
        if (mTicker == null || mTickerEnabled == 0) return;

        // no ticking on keyguard, we have carrier name in the statusbar
        if (isKeyguardShowing() || isDozing()) return;

        // no ticking in lights-out mode
        if (!areLightsOn()) return;

        // no ticking in Setup
        if (!isDeviceProvisioned()) return;

        // not for you
        if (!isNotificationForCurrentProfiles(n)) return;

        // Show the ticker if one is requested. Also don't do this
        // until status bar window is attached to the window manager,
        // because...  well, what's the point otherwise?  And trying to
        // run a ticker without being attached will crash!
        if ((!isMusic ? (n.getNotification().tickerText != null) : (metaMediaData != null)) && mStatusBarWindow != null
                && mStatusBarWindow.getWindowToken() != null) {
            if (0 == (mDisabled1 & (StatusBarManager.DISABLE_NOTIFICATION_ICONS
                    | StatusBarManager.DISABLE_NOTIFICATION_TICKER))) {
                mTicker.addEntry(n, isMusic, metaMediaData);
            }
        }
    }

    private class MyTicker extends Ticker {

        // the inflated ViewStub
        public View mTickerView;

        MyTicker(Context context, View sb) {
            super(context, sb, mTickerAnimationMode);
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
            if(mClockPos == CLOCK_DATE_POSITION_CENTERED) {
               mCenterClockLayout.setVisibility(View.GONE);
               mCenterClockLayout.startAnimation(loadAnim(true, null));
            }
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
            if(mClockPos == CLOCK_DATE_POSITION_CENTERED) {
               mCenterClockLayout.setVisibility(View.VISIBLE);
               mCenterClockLayout.startAnimation(loadAnim(false, null));
            }
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
                if(mClockPos == CLOCK_DATE_POSITION_CENTERED) {
                   mCenterClockLayout.setVisibility(View.VISIBLE);
                   mCenterClockLayout.startAnimation(loadAnim(false, null));
                }
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

    private void haltTicker() {
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
            pw.println("  mExpandedVisible=" + mExpandedVisible
                    + ", mTrackingPosition=" + mTrackingPosition);
            pw.println("  mTickerEnabled=" + mTickerEnabled);
            if (mTickerEnabled != 0) {
                pw.println("  mTicking=" + mTicking);
            }
            pw.println("  mTracking=" + mTracking);
            pw.println("  mDisplayMetrics=" + mDisplayMetrics);
            pw.println("  mStackScroller: " + viewInfo(mStackScroller));
            pw.println("  mStackScroller: " + viewInfo(mStackScroller)
                    + " scroll " + mStackScroller.getScrollX()
                    + "," + mStackScroller.getScrollY());
        }
        pw.print("  mPendingNotifications=");
        if (mPendingNotifications.size() == 0) {
            pw.println("null");
        } else {
            for (Entry entry : mPendingNotifications.values()) {
                pw.println(entry.notification);
            }
        }

        pw.print("  mInteractingWindows="); pw.println(mInteractingWindows);
        pw.print("  mStatusBarWindowState=");
        pw.println(windowStateToString(mStatusBarWindowState));
        pw.print("  mStatusBarMode=");
        pw.println(BarTransitions.modeToString(mStatusBarMode));
        pw.print("  mDozing="); pw.println(mDozing);
        pw.print("  mZenMode=");
        pw.println(Settings.Global.zenModeToString(mZenMode));
        pw.print("  mUseHeadsUp=");
        pw.println(mUseHeadsUp);
        pw.print("  mKeyToRemoveOnGutsClosed=");
        pw.println(mKeyToRemoveOnGutsClosed);
        if (mStatusBarView != null) {
            dumpBarTransitions(pw, "mStatusBarView", mStatusBarView.getBarTransitions());
        }

        pw.print("  mMediaSessionManager=");
        pw.println(mMediaSessionManager);
        pw.print("  mMediaNotificationKey=");
        pw.println(mMediaNotificationKey);
        pw.print("  mMediaController=");
        pw.print(mMediaController);
        if (mMediaController != null) {
            pw.print(" state=" + mMediaController.getPlaybackState());
        }
        pw.println();
        pw.print("  mMediaMetadata=");
        pw.print(mMediaMetadata);
        if (mMediaMetadata != null) {
            pw.print(" title=" + mMediaMetadata.getText(MediaMetadata.METADATA_KEY_TITLE));
        }
        pw.println();

        pw.println("  Panels: ");
        if (mNotificationPanel != null) {
            pw.println("    mNotificationPanel=" +
                mNotificationPanel + " params=" + mNotificationPanel.getLayoutParams().debug(""));
            pw.print  ("      ");
            mNotificationPanel.dump(fd, pw, args);
        }
        pw.println("  mStackScroller: ");
        if (mStackScroller != null) {
            pw.print  ("      ");
            mStackScroller.dump(fd, pw, args);
        }
        pw.println("  Theme:");
        if (mOverlayManager == null) {
            pw.println("    overlay manager not initialized!");
        } else {
            pw.println("    dark overlay on: " + isUsingDarkOrBlackTheme());
        }
        final boolean lightWpTheme = mContext.getThemeResId() == R.style.Theme_SystemUI_Light;
        pw.println("    light wallpaper theme: " + lightWpTheme);

        DozeLog.dump(pw);

        if (mFingerprintUnlockController != null) {
            mFingerprintUnlockController.dump(pw);
        }

        if (mScrimController != null) {
            mScrimController.dump(pw);
        }

        if (DUMPTRUCK) {
            synchronized (mNotificationData) {
                mNotificationData.dump(pw, "  ");
            }

            if (false) {
                pw.println("see the logcat for a dump of the views we have created.");
                // must happen on ui thread
                mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mStatusBarView.getLocationOnScreen(mAbsPos);
                            Log.d(TAG, "mStatusBarView: ----- (" + mAbsPos[0] + "," + mAbsPos[1]
                                    + ") " + mStatusBarView.getWidth() + "x"
                                    + getStatusBarHeight());
                            mStatusBarView.debug();
                        }
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

        if (mLightBarController != null) {
            mLightBarController.dump(fd, pw, args);
        }

        if (KeyguardUpdateMonitor.getInstance(mContext) != null) {
            KeyguardUpdateMonitor.getInstance(mContext).dump(fd, pw, args);
        }

        FalsingManager.getInstance(mContext).dump(pw);
        FalsingLog.dump(pw);

        pw.println("SharedPreferences:");
        for (Map.Entry<String, ?> entry : Prefs.getAll(mContext).entrySet()) {
            pw.print("  "); pw.print(entry.getKey()); pw.print("="); pw.println(entry.getValue());
        }
    }

    static void dumpBarTransitions(PrintWriter pw, String var, BarTransitions transitions) {
        pw.print("  "); pw.print(var); pw.print(".BarTransitions.mMode=");
        pw.println(BarTransitions.modeToString(transitions.getMode()));
    }

    public void createAndAddWindows() {
        addStatusBarWindow();
    }

    private void addStatusBarWindow() {
        makeStatusBarView();
        mStatusBarWindowManager = Dependency.get(StatusBarWindowManager.class);
        mRemoteInputController = new RemoteInputController(mHeadsUpManager);
        mStatusBarWindowManager.add(mStatusBarWindow, getStatusBarHeight());
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

    public void startActivityDismissingKeyguard(final Intent intent, boolean onlyProvisioned,
            boolean dismissShade) {
        startActivityDismissingKeyguard(intent, onlyProvisioned, dismissShade,
                false /* disallowEnterPictureInPictureWhileLaunching */, null /* callback */);
    }

    public void startActivityDismissingKeyguard(final Intent intent, boolean onlyProvisioned,
            final boolean dismissShade, final boolean disallowEnterPictureInPictureWhileLaunching,
            final Callback callback) {
        if (onlyProvisioned && !isDeviceProvisioned()) return;

        final boolean afterKeyguardGone = PreviewInflater.wouldLaunchResolverActivity(
                mContext, intent, mCurrentUserId);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mAssistManager.hideAssist();
                intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                int result = ActivityManager.START_CANCELED;
                ActivityOptions options = new ActivityOptions(getActivityOptions());
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
                try {
                    result = ActivityManager.getService().startActivityAsUser(
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
            }
        };
        Runnable cancelRunnable = new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onActivityStarted(ActivityManager.START_CANCELED);
                }
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
                if (mExpandedVisible) {
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

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                KeyboardShortcuts.dismiss();
                if (mRemoteInputController != null) {
                    mRemoteInputController.closeRemoteInputs();
                }
                if (isCurrentProfile(getSendingUserId())) {
                    int flags = CommandQueue.FLAG_EXCLUDE_NONE;
                    String reason = intent.getStringExtra("reason");
                    if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                        flags |= CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL;
                    }
                    animateCollapsePanels(flags);
                }
            }
            else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                finishBarAnimations();
                resetUserExpandedStates();
                // detach pie when screen is turned off
                if (mPieController != null) mPieController.detachPie();
            }
            else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                NotificationPanelView.recycle();
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
        }
    };

    private BroadcastReceiver mDemoReceiver = new BroadcastReceiver() {
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
                    updateMediaMetaData(true, true);
                }
            }
        }
    };

    public void resetUserExpandedStates() {
        ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        final int notificationCount = activeNotifications.size();
        for (int i = 0; i < notificationCount; i++) {
            NotificationData.Entry entry = activeNotifications.get(i);
            if (entry.row != null) {
                entry.row.resetUserExpansion();
            }
        }
    }

    protected void dismissKeyguardThenExecute(OnDismissAction action, boolean afterKeyguardGone) {
        dismissKeyguardThenExecute(action, null /* cancelRunnable */, afterKeyguardGone);
    }

    private void dismissKeyguardThenExecute(OnDismissAction action, Runnable cancelAction,
            boolean afterKeyguardGone) {
        if (mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_ASLEEP
                && mUnlockMethodCache.canSkipBouncer()
                && !mLeaveOpenOnKeyguardHide
                && isPulsing()) {
            // Reuse the fingerprint wake-and-unlock transition if we dismiss keyguard from a pulse.
            // TODO: Factor this transition out of FingerprintUnlockController.
            mFingerprintUnlockController.startWakeAndUnlock(
                    FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING);
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

        if (DEBUG) {
            Log.v(TAG, "configuration changed: " + mContext.getResources().getConfiguration());
        }

        updateRowStates();
        mScreenPinningRequest.onConfigurationChanged();
       int rotation = mDisplay.getRotation();
        if (rotation != mOrientation) {
            if (mPieController != null) mPieController.detachPie();
            mOrientation = rotation;
        }

        if (mSlimRecents != null) {
            mSlimRecents.onConfigurationChanged(newConfig);
        }
    }

    public void userSwitched(int newUserId) {
        // Begin old BaseStatusBar.userSwitched
        setHeadsUpUser(newUserId);
        // End old BaseStatusBar.userSwitched
        if (MULTIUSER_DEBUG) mNotificationPanelDebugText.setText("USER " + newUserId);
        animateCollapsePanels();
        updatePublicMode();
        mNotificationData.filterAndSort();
        if (mReinflateNotificationsOnUserSwitched) {
            updateNotificationsOnDensityOrFontScaleChanged();
            mReinflateNotificationsOnUserSwitched = false;
        }
        updateNotificationShade();
        clearCurrentMediaNotification();
        setLockscreenUser(newUserId);
    }

    protected void setLockscreenUser(int newUserId) {
        mLockscreenWallpaper.setCurrentUser(newUserId);
        mScrimController.setCurrentUser(newUserId);
        updateMediaMetaData(true, false);
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

        if (mNotificationPanel != null) {
            mNotificationPanel.updateResources();
        }
    }

    protected void loadDimens() {
        final Resources res = mContext.getResources();

        int oldBarHeight = mNaturalBarHeight;
        mNaturalBarHeight = res.getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        if (mStatusBarWindowManager != null && mNaturalBarHeight != oldBarHeight) {
            mStatusBarWindowManager.setBarHeight(mNaturalBarHeight);
            mStatusBarView.onDensityOrFontScaleChanged();
        }
        mMaxAllowedKeyguardNotifications = res.getInteger(
                R.integer.keyguard_max_notification_count);

        mStatusBarHeaderHeight = res.getDimensionPixelSize(R.dimen.status_bar_header_height);

        if (DEBUG) Log.v(TAG, "defineSlots");
    }

    // Visibility reporting

    protected void handleVisibleToUserChanged(boolean visibleToUser) {
        if (visibleToUser) {
            handleVisibleToUserChangedImpl(visibleToUser);
            startNotificationLogging();
        } else {
            stopNotificationLogging();
            handleVisibleToUserChangedImpl(visibleToUser);
        }
    }

    void handlePeekToExpandTransistion() {
        try {
            // consider the transition from peek to expanded to be a panel open,
            // but not one that clears notification effects.
            int notificationLoad = mNotificationData.getActiveNotifications().size();
            mBarService.onPanelRevealed(false, notificationLoad);
        } catch (RemoteException ex) {
            // Won't fail unless the world has ended.
        }
    }

    /**
     * The LEDs are turned off when the notification panel is shown, even just a little bit.
     * See also StatusBar.setPanelExpanded for another place where we attempt to do this.
     */
    // Old BaseStatusBar.handleVisibileToUserChanged
    private void handleVisibleToUserChangedImpl(boolean visibleToUser) {
        try {
            if (visibleToUser) {
                boolean pinnedHeadsUp = mHeadsUpManager.hasPinnedHeadsUp();
                boolean clearNotificationEffects =
                        !isPanelFullyCollapsed() &&
                        (mState == StatusBarState.SHADE || mState == StatusBarState.SHADE_LOCKED);
                int notificationLoad = mNotificationData.getActiveNotifications().size();
                if (pinnedHeadsUp && isPanelFullyCollapsed())  {
                    notificationLoad = 1;
                }
                mBarService.onPanelRevealed(clearNotificationEffects, notificationLoad);
            } else {
                mBarService.onPanelHidden();
            }
        } catch (RemoteException ex) {
            // Won't fail unless the world has ended.
        }
    }

    private void stopNotificationLogging() {
        // Report all notifications as invisible and turn down the
        // reporter.
        if (!mCurrentlyVisibleNotifications.isEmpty()) {
            logNotificationVisibilityChanges(Collections.<NotificationVisibility>emptyList(),
                    mCurrentlyVisibleNotifications);
            recycleAllVisibilityObjects(mCurrentlyVisibleNotifications);
        }
        mHandler.removeCallbacks(mVisibilityReporter);
        mStackScroller.setChildLocationsChangedListener(null);
    }

    private void startNotificationLogging() {
        mStackScroller.setChildLocationsChangedListener(mNotificationLocationsChangedListener);
        // Some transitions like mVisibleToUser=false -> mVisibleToUser=true don't
        // cause the scroller to emit child location events. Hence generate
        // one ourselves to guarantee that we're reporting visible
        // notifications.
        // (Note that in cases where the scroller does emit events, this
        // additional event doesn't break anything.)
        mNotificationLocationsChangedListener.onChildLocationsChanged(mStackScroller);
    }

    private void logNotificationVisibilityChanges(
            Collection<NotificationVisibility> newlyVisible,
            Collection<NotificationVisibility> noLongerVisible) {
        if (newlyVisible.isEmpty() && noLongerVisible.isEmpty()) {
            return;
        }
        NotificationVisibility[] newlyVisibleAr =
                newlyVisible.toArray(new NotificationVisibility[newlyVisible.size()]);
        NotificationVisibility[] noLongerVisibleAr =
                noLongerVisible.toArray(new NotificationVisibility[noLongerVisible.size()]);
        try {
            mBarService.onNotificationVisibilityChanged(newlyVisibleAr, noLongerVisibleAr);
        } catch (RemoteException e) {
            // Ignore.
        }

        final int N = newlyVisible.size();
        if (N > 0) {
            String[] newlyVisibleKeyAr = new String[N];
            for (int i = 0; i < N; i++) {
                newlyVisibleKeyAr[i] = newlyVisibleAr[i].key;
            }

            setNotificationsShown(newlyVisibleKeyAr);
        }
    }

    // State logging

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

    Runnable mStartTracing = new Runnable() {
        @Override
        public void run() {
            vibrate();
            SystemClock.sleep(250);
            Log.d(TAG, "startTracing");
            android.os.Debug.startMethodTracing("/data/statusbar-traces/trace");
            mHandler.postDelayed(mStopTracing, 10000);
        }
    };

    Runnable mStopTracing = new Runnable() {
        @Override
        public void run() {
            android.os.Debug.stopMethodTracing();
            Log.d(TAG, "stopTracing");
            vibrate();
        }
    };

    @Override
    public void postQSRunnableDismissingKeyguard(final Runnable runnable) {
        mHandler.post(() -> {
            mLeaveOpenOnKeyguardHide = true;
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
        public void setColorFilter(ColorFilter colorFilter) {
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

    public void destroy() {
        // Begin old BaseStatusBar.destroy().
        mContext.unregisterReceiver(mBaseBroadcastReceiver);
        try {
            mNotificationListener.unregisterAsSystemService();
        } catch (RemoteException e) {
            // Ignore.
        }
        mDeviceProvisionedController.removeCallback(mDeviceProvisionedListener);
        // End old BaseStatusBar.destroy().
        if (mStatusBarWindow != null) {
            mWindowManager.removeViewImmediate(mStatusBarWindow);
            mStatusBarWindow = null;
        }
        if (mNavigationBarView != null) {
            mWindowManager.removeViewImmediate(mNavigationBarView);
            mNavigationBarView = null;
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext.unregisterReceiver(mDemoReceiver);
        mAssistManager.destroy();

        if (mQSPanel != null && mQSPanel.getHost() != null) {
            mQSPanel.getHost().destroy();
        }

        mPackageMonitor.removeListener(this);
        mPackageMonitor.unregister();

        Dependency.get(ActivityStarterDelegate.class).setActivityStarterImpl(null);
        mDeviceProvisionedController.removeCallback(mUserSetupObserver);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onPackageChanged(String pkg, PackageState state) {
        if (state == PackageState.PACKAGE_REMOVED
                || state == PackageState.PACKAGE_CHANGED) {
            final Context ctx = mContext;
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!DUActionUtils.hasNavbarByDefault(ctx)) {
                        DUActionUtils.resolveAndUpdateButtonActions(ctx,
                                ActionConstants
                                        .getDefaults(ActionConstants.HWKEYS));
                    }
                    DUActionUtils
                            .resolveAndUpdateButtonActions(ctx, ActionConstants
                                    .getDefaults(ActionConstants.SMARTBAR));
                    DUActionUtils.resolveAndUpdateButtonActions(ctx,
                            ActionConstants.getDefaults(ActionConstants.FLING));
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
            dispatchDemoCommandToView(command, args, R.id.center_clock);
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
                if (mStatusBarView != null) {
                    mStatusBarView.getBarTransitions().transitionTo(barMode, animate);
                }
                if (mNavigationBar != null) {
                    mNavigationBar.getBarTransitions().transitionTo(barMode, animate);
                }
            }
        }
    }

    private void dispatchDemoCommandToView(String command, Bundle args, int id) {
        if (mStatusBarView == null) return;
        View v = mStatusBarView.findViewById(id);
        if (v instanceof DemoMode) {
            ((DemoMode)v).dispatchDemoCommand(command, args);
        }
    }

    /**
     * @return The {@link StatusBarState} the status bar is in.
     */
    public int getBarState() {
        return mState;
    }

    public boolean isPanelFullyCollapsed() {
        return mNotificationPanel.isFullyCollapsed();
    }

    public void showKeyguard() {
        mKeyguardRequested = true;
        mLeaveOpenOnKeyguardHide = false;
        mPendingRemoteInputView = null;
        updateIsKeyguard();
        mAssistManager.onLockscreenShown();
    }

    public boolean hideKeyguard() {
        mKeyguardRequested = false;
        return updateIsKeyguard();
    }

    private boolean updateIsKeyguard() {
        boolean wakeAndUnlocking = mFingerprintUnlockController.getMode()
                == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK;

        // For dozing, keyguard needs to be shown whenever the device is non-interactive. Otherwise
        // there's no surface we can show to the user. Note that the device goes fully interactive
        // late in the transition, so we also allow the device to start dozing once the screen has
        // turned off fully.
        boolean keyguardForDozing = mDozingRequested &&
                (!mDeviceInteractive || isGoingToSleep() && (isScreenFullyOff() || mIsKeyguard));
        boolean shouldBeKeyguard = (mKeyguardRequested || keyguardForDozing) && !wakeAndUnlocking;
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
            return hideKeyguardImpl();
        }
        return false;
    }

    public void showKeyguardImpl() {
        mIsKeyguard = true;
        if (mLaunchTransitionFadingAway) {
            mNotificationPanel.animate().cancel();
            onLaunchTransitionFadingEnded();
        }
        mHandler.removeMessages(MSG_LAUNCH_TRANSITION_TIMEOUT);
        if (mUserSwitcherController != null && mUserSwitcherController.useFullscreenUserSwitcher()) {
            setBarState(StatusBarState.FULLSCREEN_USER_SWITCHER);
        } else {
            setBarState(StatusBarState.KEYGUARD);
        }
        updateKeyguardState(false /* goingToFullShade */, false /* fromShadeLocked */);
        updatePanelExpansionForKeyguard();
        if (mDraggedDownRow != null) {
            mDraggedDownRow.setUserLocked(false);
            mDraggedDownRow.notifyHeightChanged(false  /* needsAnimation */);
            mDraggedDownRow = null;
        }
    }

    private void updatePanelExpansionForKeyguard() {
        if (mState == StatusBarState.KEYGUARD && mFingerprintUnlockController.getMode()
                != FingerprintUnlockController.MODE_WAKE_AND_UNLOCK) {
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
        mLaunchTransitionFadingAway = false;
        mScrimController.forceHideScrims(false /* hide */, false /* animated */);
        updateMediaMetaData(true /* metaDataChanged */, true);
    }

    public boolean isCollapsing() {
        return mNotificationPanel.isCollapsing();
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
        Runnable hideRunnable = new Runnable() {
            @Override
            public void run() {
                mLaunchTransitionFadingAway = true;
                if (beforeFading != null) {
                    beforeFading.run();
                }
                mScrimController.forceHideScrims(true /* hide */, false /* animated */);
                updateMediaMetaData(false, true);
                mNotificationPanel.setAlpha(1);
                mStackScroller.setParentNotFullyVisible(true);
                mNotificationPanel.animate()
                        .alpha(0)
                        .setStartDelay(FADE_KEYGUARD_START_DELAY)
                        .setDuration(FADE_KEYGUARD_DURATION)
                        .withLayer()
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                onLaunchTransitionFadingEnded();
                            }
                        });
                mCommandQueue.appTransitionStarting(SystemClock.uptimeMillis(),
                        LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION, true);
            }
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
        mNotificationPanel.notifyStartFading();
        mNotificationPanel.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(FADE_KEYGUARD_DURATION_PULSING)
                .setInterpolator(ScrimController.KEYGUARD_FADE_OUT_INTERPOLATOR)
                .start();
    }

    /**
     * Plays the animation when an activity that was occluding Keyguard goes away.
     */
    public void animateKeyguardUnoccluding() {
        mScrimController.animateKeyguardUnoccluding(500);
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
        mNotificationPanel.resetViews();
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
    public boolean hideKeyguardImpl() {
        mIsKeyguard = false;
        Trace.beginSection("StatusBar#hideKeyguard");
        boolean staying = mLeaveOpenOnKeyguardHide;
        setBarState(StatusBarState.SHADE);
        View viewToClick = null;
        if (mLeaveOpenOnKeyguardHide) {
            if (!mKeyguardRequested) {
                mLeaveOpenOnKeyguardHide = false;
            }
            long delay = calculateGoingToFullShadeDelay();
            mNotificationPanel.animateToFullShade(delay);
            if (mDraggedDownRow != null) {
                mDraggedDownRow.setUserLocked(false);
                mDraggedDownRow = null;
            }
            if (!mKeyguardRequested) {
                viewToClick = mPendingRemoteInputView;
                mPendingRemoteInputView = null;
            }

            // Disable layout transitions in navbar for this transition because the load is just
            // too heavy for the CPU and GPU on any device.
            if (mNavigationBar != null) {
                mNavigationBar.disableAnimationsDuringHide(delay);
            }
        } else if (!mNotificationPanel.isCollapsing()) {
            instantCollapseNotificationPanel();
        }
        updateKeyguardState(staying, false /* fromShadeLocked */);

        if (viewToClick != null && viewToClick.isAttachedToWindow()) {
            viewToClick.callOnClick();
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
        Trace.endSection();
        return staying;
    }

    private void releaseGestureWakeLock() {
        if (mGestureWakeLock.isHeld()) {
            mGestureWakeLock.release();
        }
    }

    public long calculateGoingToFullShadeDelay() {
        return mKeyguardFadingAwayDelay + mKeyguardFadingAwayDuration;
    }

    /**
     * Notifies the status bar that Keyguard is going away very soon.
     */
    public void keyguardGoingAway() {

        // Treat Keyguard exit animation as an app transition to achieve nice transition for status
        // bar.
        mKeyguardGoingAway = true;
        mKeyguardMonitor.notifyKeyguardGoingAway(true);
        mCommandQueue.appTransitionPending(true);
    }

    /**
     * Notifies the status bar the Keyguard is fading away with the specified timings.
     *
     * @param startTime the start time of the animations in uptime millis
     * @param delay the precalculated animation delay in miliseconds
     * @param fadeoutDuration the duration of the exit animation, in milliseconds
     */
    public void setKeyguardFadingAway(long startTime, long delay, long fadeoutDuration) {
        mKeyguardFadingAway = true;
        mKeyguardFadingAwayDelay = delay;
        mKeyguardFadingAwayDuration = fadeoutDuration;
        mWaitingForKeyguardExit = false;
        mCommandQueue.appTransitionStarting(startTime + fadeoutDuration
                        - LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION,
                LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION, true);
        recomputeDisableFlags(fadeoutDuration > 0 /* animate */);
        mCommandQueue.appTransitionStarting(
                    startTime - LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION,
                    LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION, true);
        mKeyguardMonitor.notifyKeyguardFadingAway(delay, fadeoutDuration);
    }

    public boolean isKeyguardFadingAway() {
        return mKeyguardFadingAway;
    }

    /**
     * Notifies that the Keyguard fading away animation is done.
     */
    public void finishKeyguardFadingAway() {
        mKeyguardFadingAway = false;
        mKeyguardGoingAway = false;
        mKeyguardMonitor.notifyKeyguardDoneFading();
    }

    public void stopWaitingForKeyguardExit() {
        mWaitingForKeyguardExit = false;
    }

    private void updatePublicMode() {
        final boolean showingKeyguard = mStatusBarKeyguardViewManager.isShowing();
        final boolean devicePublic = showingKeyguard
                && mStatusBarKeyguardViewManager.isSecure(mCurrentUserId);

        // Look for public mode users. Users are considered public in either case of:
        //   - device keyguard is shown in secure mode;
        //   - profile is locked with a work challenge.
        for (int i = mCurrentProfiles.size() - 1; i >= 0; i--) {
            final int userId = mCurrentProfiles.valueAt(i).id;
            boolean isProfilePublic = devicePublic;
            if (!devicePublic && userId != mCurrentUserId) {
                // We can't rely on KeyguardManager#isDeviceLocked() for unified profile challenge
                // due to a race condition where this code could be called before
                // TrustManagerService updates its internal records, resulting in an incorrect
                // state being cached in mLockscreenPublicMode. (b/35951989)
                if (mLockPatternUtils.isSeparateProfileChallengeEnabled(userId)
                        && mStatusBarKeyguardViewManager.isSecure(userId)) {
                    isProfilePublic = mKeyguardManager.isDeviceLocked(userId);
                }
            }
            setLockscreenPublicMode(isProfilePublic, userId);
        }
    }

    protected void updateKeyguardState(boolean goingToFullShade, boolean fromShadeLocked) {
        Trace.beginSection("StatusBar#updateKeyguardState");
        if (mState == StatusBarState.KEYGUARD) {
            mKeyguardIndicationController.setVisible(true);
            mNotificationPanel.resetViews();
            if (mKeyguardUserSwitcher != null) {
                mKeyguardUserSwitcher.setKeyguard(true, fromShadeLocked);
            }
            if (mStatusBarView != null) mStatusBarView.removePendingHideExpandedRunnables();
            /*if (mAmbientIndicationContainer != null) {
                mAmbientIndicationContainer.setVisibility(View.VISIBLE);
            }*/
        } else {
            mKeyguardIndicationController.setVisible(false);
            if (mKeyguardUserSwitcher != null) {
                mKeyguardUserSwitcher.setKeyguard(false,
                        goingToFullShade ||
                        mState == StatusBarState.SHADE_LOCKED ||
                        fromShadeLocked);
            }
            /*if (mAmbientIndicationContainer != null) {
                mAmbientIndicationContainer.setVisibility(View.INVISIBLE);
            }*/
        }
        if (mState == StatusBarState.KEYGUARD || mState == StatusBarState.SHADE_LOCKED) {
            mScrimController.setKeyguardShowing(true);
        } else {
            mScrimController.setKeyguardShowing(false);
        }
        mNotificationPanel.setBarState(mState, mKeyguardFadingAway, goingToFullShade);
        updateTheme();
        updateDozingState();
        updatePublicMode();
        updateStackScrollerState(goingToFullShade, fromShadeLocked);
        updateNotifications();
        checkBarModes();
        updateMediaMetaData(false, mState != StatusBarState.KEYGUARD);
        mKeyguardMonitor.notifyKeyguardState(mStatusBarKeyguardViewManager.isShowing(),
                mUnlockMethodCache.isMethodSecure(),
                mStatusBarKeyguardViewManager.isOccluded());
        Trace.endSection();
    }

    /**
     * Switches theme from light to dark and vice-versa.
     */
    protected void updateTheme() {
        final boolean inflated = mStackScroller != null;

        // 0 = auto, 1 = time-based, 2 = light, 3 = dark
        final int globalStyleSetting = LineageSettings.System.getInt(mContext.getContentResolver(),
                LineageSettings.System.BERRY_GLOBAL_STYLE, 0);
        WallpaperColors systemColors = mColorExtractor
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
        final boolean useDarkTheme;

        // force black theme
        boolean forceBlackTheme = LineageSettings.System.getInt(mContext.getContentResolver(),
                LineageSettings.System.BERRY_FORCE_BLACK, 0) == 1;

        switch (globalStyleSetting) {
            case 1:
                useDarkTheme = isLiveDisplayNightModeOn();
                break;
            case 2:
                useDarkTheme = false;
                break;
            case 3:
                useDarkTheme = true;
                break;
            default:
                useDarkTheme = systemColors != null && (systemColors.getColorHints() &
                        WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0;
                break;
        }

        if (isUsingDarkOrBlackTheme() != useDarkTheme || mForceBlackTheme != forceBlackTheme) {
            mForceBlackTheme = forceBlackTheme;
            boolean darkThemeEnabled = useDarkTheme;
            boolean blackThemeEnabled = forceBlackTheme && useDarkTheme;
            if (blackThemeEnabled){
                darkThemeEnabled = false;
            }
            try {
                // Dark
                mOverlayManager.setEnabled("org.lineageos.overlay.dark",
                        darkThemeEnabled, mCurrentUserId);
                mOverlayManager.setEnabled("org.lineageos.overlay.sysuidark",
                        darkThemeEnabled, mCurrentUserId);
                mOverlayManager.setEnabled("org.lineageos.overlay.settingsdark",
                        darkThemeEnabled, mCurrentUserId);

                // Black
                mOverlayManager.setEnabled("org.lineageos.overlay.black",
                        blackThemeEnabled, mCurrentUserId);
                mOverlayManager.setEnabled("org.lineageos.overlay.sysuiblack",
                        blackThemeEnabled, mCurrentUserId);
                mOverlayManager.setEnabled("org.lineageos.overlay.settingsblack",
                        blackThemeEnabled, mCurrentUserId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change theme", e);
            }

            if (mUiModeManager != null) {
                mUiModeManager.setNightMode(useDarkTheme ?
                        UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
            }
        }

        // Lock wallpaper defines the color of the majority of the views, hence we'll use it
        // to set our default theme.
        final boolean lockDarkText = mColorExtractor.getColors(WallpaperManager.FLAG_LOCK, true
                /* ignoreVisibility */).supportsDarkText();
        final int themeResId = lockDarkText ? R.style.Theme_SystemUI_Light : R.style.Theme_SystemUI;
        if (mContext.getThemeResId() != themeResId) {
            mContext.setTheme(themeResId);
            if (inflated) {
                reinflateViews();
            }
        }

        if (inflated) {
            int which;
            if (mState == StatusBarState.KEYGUARD || mState == StatusBarState.SHADE_LOCKED) {
                which = WallpaperManager.FLAG_LOCK;
            } else {
                which = WallpaperManager.FLAG_SYSTEM;
            }
            final boolean useDarkText = mColorExtractor.getColors(which,
                    true /* ignoreVisibility */).supportsDarkText();
            mStackScroller.updateDecorViews(useDarkText);

            // Make sure we have the correct navbar/statusbar colors.
            mStatusBarWindowManager.setKeyguardDark(useDarkText);
        }
    }

    private void updateDozingState() {
        Trace.traceCounter(Trace.TRACE_TAG_APP, "dozing", mDozing ? 1 : 0);
        Trace.beginSection("StatusBar#updateDozingState");
        boolean animate = !mDozing && mDozeServiceHost.shouldAnimateWakeup();
        mNotificationPanel.setDozing(mDozing, animate);
        mStackScroller.setDark(mDozing, animate, mWakeUpTouchLocation);
        mScrimController.setDozing(mDozing);
        mKeyguardIndicationController.setDozing(mDozing);
        mNotificationPanel.setDark(mDozing, animate);
        updateQsExpansionEnabled();
        mDozeScrimController.setDozing(mDozing, animate);
        mVisualizerView.setDozing(mDozing);
        updateRowStates();
        if (isDozing()) {
            haltTicker();
        }
        Trace.endSection();
    }

    public void updateStackScrollerState(boolean goingToFullShade, boolean fromShadeLocked) {
        if (mStackScroller == null) return;
        boolean onKeyguard = mState == StatusBarState.KEYGUARD;
        boolean publicMode = isAnyProfilePublicMode();
        mStackScroller.setHideSensitive(publicMode, goingToFullShade);
        mStackScroller.setDimmed(onKeyguard, fromShadeLocked /* animate */);
        mStackScroller.setExpandingEnabled(!onKeyguard);
        ActivatableNotificationView activatedChild = mStackScroller.getActivatedChild();
        mStackScroller.setActivatedChild(null);
        if (activatedChild != null) {
            activatedChild.makeInactive(false /* animate */);
        }
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
        if (mStatusBarKeyguardViewManager.onBackPressed()) {
            return true;
        }
        if (mNotificationPanel.isQsExpanded()) {
            if (mNotificationPanel.isQsDetailShowing()) {
                mNotificationPanel.closeQsDetail();
            } else {
                mNotificationPanel.animateCloseQs();
            }
            return true;
        }
        if (mState != StatusBarState.KEYGUARD && mState != StatusBarState.SHADE_LOCKED) {
            animateCollapsePanels();
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
        if (mState == StatusBarState.KEYGUARD || mState == StatusBarState.SHADE_LOCKED) {
            showBouncer();
        }
    }

    protected void showBouncer() {
        mWaitingForKeyguardExit = mStatusBarKeyguardViewManager.isShowing();
        mStatusBarKeyguardViewManager.dismiss();
    }

    private void instantExpandNotificationsPanel() {
        // Make our window larger and the panel expanded.
        makeExpandedVisible(true);
        mNotificationPanel.expand(false /* animate */);
        recomputeDisableFlags(false /* animate */);
    }

    private void instantCollapseNotificationPanel() {
        mNotificationPanel.instantCollapse();
    }

    @Override
    public void onActivated(ActivatableNotificationView view) {
        onActivated((View)view);
        mStackScroller.setActivatedChild(view);
    }

    public void onActivated(View view) {
        mLockscreenGestureLogger.write(
                MetricsEvent.ACTION_LS_NOTE,
                0 /* lengthDp - N/A */, 0 /* velocityDp - N/A */);
        mKeyguardIndicationController.showTransientIndication(R.string.notification_tap_again);
        ActivatableNotificationView previousView = mStackScroller.getActivatedChild();
        if (previousView != null) {
            previousView.makeInactive(true /* animate */);
        }
    }

    /**
     * @param state The {@link StatusBarState} to set.
     */
    public void setBarState(int state) {
        // If we're visible and switched to SHADE_LOCKED (the user dragged
        // down on the lockscreen), clear notification LED, vibration,
        // ringing.
        // Other transitions are covered in handleVisibleToUserChanged().
        if (state != mState && mVisible && (state == StatusBarState.SHADE_LOCKED
                || (state == StatusBarState.SHADE && isGoingToNotificationShade()))) {
            clearNotificationEffects();
        }
        if (state == StatusBarState.KEYGUARD) {
            removeRemoteInputEntriesKeptUntilCollapsed();
            maybeEscalateHeadsUp();
        }
        mState = state;
        mVisualizerView.setStatusBarState(state);
        mGroupManager.setStatusBarState(state);
        mHeadsUpManager.setStatusBarState(state);
        mFalsingManager.setStatusBarState(state);
        mStatusBarWindowManager.setStatusBarState(state);
        mStackScroller.setStatusBarState(state);
        updateReportRejectedTouchVisibility();
        updateDozing();
        updateTheme();
        touchAutoDim();
        mNotificationShelf.setStatusBarState(state);
    }

    @Override
    public void onActivationReset(ActivatableNotificationView view) {
        if (view == mStackScroller.getActivatedChild()) {
            mStackScroller.setActivatedChild(null);
            onActivationReset((View)view);
        }
    }

    public void onActivationReset(View view) {
        mKeyguardIndicationController.hideTransientIndication();
    }

    public void onTrackingStarted() {
        runPostCollapseRunnables();
    }

    public void onClosingFinished() {
        runPostCollapseRunnables();
        if (mQuickStatusBarHeader != null) {
            mQuickStatusBarHeader.onClosingFinished();
        }
        if (!isPanelFullyCollapsed()) {
            // if we set it not to be focusable when collapsing, we have to undo it when we aborted
            // the closing
            mStatusBarWindowManager.setStatusBarFocusable(true);
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
                showBouncerIfKeyguard();
            }
        }
    }

    protected int getMaxKeyguardNotifications(boolean recompute) {
        mCustomMaxKeyguard = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.LOCK_SCREEN_CUSTOM_NOTIF, 0, UserHandle.USER_CURRENT) == 1;
        if (mCustomMaxKeyguard) {
            return mMaxKeyguardNotifConfig;
        } else {
           if (recompute) {
               mMaxKeyguardNotifications = Math.max(1,
                       mNotificationPanel.computeMaxKeyguardNotifications(
                               mMaxAllowedKeyguardNotifications));
               return mMaxKeyguardNotifications;
           } else {
               return mMaxKeyguardNotifications;
           }
        }
    }

    public int getMaxKeyguardNotifications() {
        return getMaxKeyguardNotifications(false /* recompute */);
    }

    // TODO: Figure out way to remove these.
    // We can't remove you yet but we can make you more abstract ;D
    public Navigator getNavigationBarView() {
        return mNavigationBar != null ? mNavigationBar.getNavigator() : null;
    }

    public View getNavigationBarWindow() {
        return mNavigationBarView;
    }

    /**
     * TODO: Remove this method. Views should not be passed forward. Will cause theme issues.
     * @return bottom area view
     */
    public KeyguardBottomAreaView getKeyguardBottomAreaView() {
        return mNotificationPanel.getKeyguardBottomAreaView();
    }

    // ---------------------- DragDownHelper.OnDragDownListener ------------------------------------


    /* Only ever called as a consequence of a lockscreen expansion gesture. */
    @Override
    public boolean onDraggedDown(View startingChild, int dragLengthY) {
        if (mState == StatusBarState.KEYGUARD
                && hasActiveNotifications() && (!isDozing() || isPulsing())) {
            mLockscreenGestureLogger.write(
                    MetricsEvent.ACTION_LS_SHADE,
                    (int) (dragLengthY / mDisplayMetrics.density),
                    0 /* velocityDp - N/A */);

            // We have notifications, go to locked shade.
            goToLockedShade(startingChild);
            if (startingChild instanceof ExpandableNotificationRow) {
                ExpandableNotificationRow row = (ExpandableNotificationRow) startingChild;
                row.onExpandedByGesture(true /* drag down is always an open */);
            }
            return true;
        } else {
            // abort gesture.
            return false;
        }
    }

    @Override
    public void onDragDownReset() {
        mStackScroller.setDimmed(true /* dimmed */, true /* animated */);
        mStackScroller.resetScrollPosition();
        mStackScroller.resetCheckSnoozeLeavebehind();
    }

    @Override
    public void onCrossedThreshold(boolean above) {
        mStackScroller.setDimmed(!above /* dimmed */, true /* animate */);
    }

    @Override
    public void onTouchSlopExceeded() {
        mStackScroller.removeLongPressCallback();
        mStackScroller.checkSnoozeLeavebehind();
    }

    @Override
    public void setEmptyDragAmount(float amount) {
        mNotificationPanel.setEmptyDragAmount(amount);
    }

    @Override
    public boolean isFalsingCheckNeeded() {
        return mState == StatusBarState.KEYGUARD;
    }

    /**
     * If secure with redaction: Show bouncer, go to unlocked shade.
     *
     * <p>If secure without redaction or no security: Go to {@link StatusBarState#SHADE_LOCKED}.</p>
     *
     * @param expandView The view to expand after going to the shade.
     */
    public void goToLockedShade(View expandView) {
        int userId = mCurrentUserId;
        ExpandableNotificationRow row = null;
        if (expandView instanceof ExpandableNotificationRow) {
            row = (ExpandableNotificationRow) expandView;
            row.setUserExpanded(true /* userExpanded */, true /* allowChildExpansion */);
            // Indicate that the group expansion is changing at this time -- this way the group
            // and children backgrounds / divider animations will look correct.
            row.setGroupExpansionChanging(true);
            if (row.getStatusBarNotification() != null) {
                userId = row.getStatusBarNotification().getUserId();
            }
        }
        boolean fullShadeNeedsBouncer = !userAllowsPrivateNotificationsInPublic(mCurrentUserId)
                || !mShowLockscreenNotifications || mFalsingManager.shouldEnforceBouncer();
        if (isLockscreenPublicMode(userId) && fullShadeNeedsBouncer) {
            mLeaveOpenOnKeyguardHide = true;
            showBouncerIfKeyguard();
            mDraggedDownRow = row;
            mPendingRemoteInputView = null;
        } else {
            mNotificationPanel.animateToFullShade(0 /* delay */);
            setBarState(StatusBarState.SHADE_LOCKED);
            updateKeyguardState(false /* goingToFullShade */, false /* fromShadeLocked */);
        }
    }

    public void onLockedNotificationImportanceChange(OnDismissAction dismissAction) {
        mLeaveOpenOnKeyguardHide = true;
        dismissKeyguardThenExecute(dismissAction, true /* afterKeyguardGone */);
    }

    protected void onLockedRemoteInput(ExpandableNotificationRow row, View clicked) {
        mLeaveOpenOnKeyguardHide = true;
        showBouncer();
        mPendingRemoteInputView = clicked;
    }

    protected void onMakeExpandedVisibleForRemoteInput(ExpandableNotificationRow row,
            View clickedView) {
        if (isKeyguardShowing()) {
            onLockedRemoteInput(row, clickedView);
        } else {
            row.setUserExpanded(true);
            row.getPrivateLayout().setOnExpandedVisibleListener(clickedView::performClick);
        }
    }

    protected boolean startWorkChallengeIfNecessary(int userId, IntentSender intendSender,
            String notificationKey) {
        // Clear pending remote view, as we do not want to trigger pending remote input view when
        // it's called by other code
        mPendingWorkRemoteInputView = null;
        // Begin old BaseStatusBar.startWorkChallengeIfNecessary.
        final Intent newIntent = mKeyguardManager.createConfirmDeviceCredentialIntent(null,
                null, userId);
        if (newIntent == null) {
            return false;
        }
        final Intent callBackIntent = new Intent(NOTIFICATION_UNLOCKED_BY_WORK_CHALLENGE_ACTION);
        callBackIntent.putExtra(Intent.EXTRA_INTENT, intendSender);
        callBackIntent.putExtra(Intent.EXTRA_INDEX, notificationKey);
        callBackIntent.setPackage(mContext.getPackageName());

        PendingIntent callBackPendingIntent = PendingIntent.getBroadcast(
                mContext,
                0,
                callBackIntent,
                PendingIntent.FLAG_CANCEL_CURRENT |
                        PendingIntent.FLAG_ONE_SHOT |
                        PendingIntent.FLAG_IMMUTABLE);
        newIntent.putExtra(
                Intent.EXTRA_INTENT,
                callBackPendingIntent.getIntentSender());
        try {
            ActivityManager.getService().startConfirmDeviceCredentialIntent(newIntent,
                    null /*options*/);
        } catch (RemoteException ex) {
            // ignore
        }
        return true;
        // End old BaseStatusBar.startWorkChallengeIfNecessary.
    }

    protected void onLockedWorkRemoteInput(int userId, ExpandableNotificationRow row,
            View clicked) {
        // Collapse notification and show work challenge
        animateCollapsePanels();
        startWorkChallengeIfNecessary(userId, null, null);
        // Add pending remote input view after starting work challenge, as starting work challenge
        // will clear all previous pending review view
        mPendingWorkRemoteInputView = clicked;
    }

    private boolean isAnyProfilePublicMode() {
        for (int i = mCurrentProfiles.size() - 1; i >= 0; i--) {
            if (isLockscreenPublicMode(mCurrentProfiles.valueAt(i).id)) {
                return true;
            }
        }
        return false;
    }

    protected void onWorkChallengeChanged() {
        updatePublicMode();
        updateNotifications();
        if (mPendingWorkRemoteInputView != null && !isAnyProfilePublicMode()) {
            // Expand notification panel and the notification row, then click on remote input view
            final Runnable clickPendingViewRunnable = new Runnable() {
                @Override
                public void run() {
                    final View pendingWorkRemoteInputView = mPendingWorkRemoteInputView;
                    if (pendingWorkRemoteInputView == null) {
                        return;
                    }

                    // Climb up the hierarchy until we get to the container for this row.
                    ViewParent p = pendingWorkRemoteInputView.getParent();
                    while (!(p instanceof ExpandableNotificationRow)) {
                        if (p == null) {
                            return;
                        }
                        p = p.getParent();
                    }

                    final ExpandableNotificationRow row = (ExpandableNotificationRow) p;
                    ViewParent viewParent = row.getParent();
                    if (viewParent instanceof NotificationStackScrollLayout) {
                        final NotificationStackScrollLayout scrollLayout =
                                (NotificationStackScrollLayout) viewParent;
                        row.makeActionsVisibile();
                        row.post(new Runnable() {
                            @Override
                            public void run() {
                                final Runnable finishScrollingCallback = new Runnable() {
                                    @Override
                                    public void run() {
                                        mPendingWorkRemoteInputView.callOnClick();
                                        mPendingWorkRemoteInputView = null;
                                        scrollLayout.setFinishScrollingCallback(null);
                                    }
                                };
                                if (scrollLayout.scrollTo(row)) {
                                    // It scrolls! So call it when it's finished.
                                    scrollLayout.setFinishScrollingCallback(
                                            finishScrollingCallback);
                                } else {
                                    // It does not scroll, so call it now!
                                    finishScrollingCallback.run();
                                }
                            }
                        });
                    }
                }
            };
            mNotificationPanel.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (mNotificationPanel.mStatusBar.getStatusBarWindow()
                                    .getHeight() != mNotificationPanel.mStatusBar
                                            .getStatusBarHeight()) {
                                mNotificationPanel.getViewTreeObserver()
                                        .removeOnGlobalLayoutListener(this);
                                mNotificationPanel.post(clickPendingViewRunnable);
                            }
                        }
                    });
            instantExpandNotificationsPanel();
        }
    }

    @Override
    public void onExpandClicked(Entry clickedEntry, boolean nowExpanded) {
        mHeadsUpManager.setExpanded(clickedEntry, nowExpanded);
        if (mState == StatusBarState.KEYGUARD && nowExpanded) {
            goToLockedShade(clickedEntry.row);
        }
    }

    /**
     * Goes back to the keyguard after hanging around in {@link StatusBarState#SHADE_LOCKED}.
     */
    public void goToKeyguard() {
        if (mState == StatusBarState.SHADE_LOCKED) {
            mStackScroller.onGoToKeyguard();
            setBarState(StatusBarState.KEYGUARD);
            updateKeyguardState(false /* goingToFullShade */, true /* fromShadeLocked*/);
        }
    }

    public long getKeyguardFadingAwayDelay() {
        return mKeyguardFadingAwayDelay;
    }

    public long getKeyguardFadingAwayDuration() {
        return mKeyguardFadingAwayDuration;
    }

    public void setBouncerShowing(boolean bouncerShowing) {
        mBouncerShowing = bouncerShowing;
        if (mStatusBarView != null) mStatusBarView.setBouncerShowing(bouncerShowing);
        updateHideIconsForBouncer(true /* animate */);
        recomputeDisableFlags(true /* animate */);
    }

    public void cancelCurrentTouch() {
        if (mNotificationPanel.isTracking()) {
            mStatusBarWindow.cancelCurrentTouch();
            if (mState == StatusBarState.SHADE) {
                animateCollapsePanels();
            }
        }
    }

    WakefulnessLifecycle.Observer mWakefulnessObserver = new WakefulnessLifecycle.Observer() {
        @Override
        public void onFinishedGoingToSleep() {
            mNotificationPanel.onAffordanceLaunchEnded();
            releaseGestureWakeLock();
            mLaunchCameraOnScreenTurningOn = false;
            mDeviceInteractive = false;
            mWakeUpComingFromTouch = false;
            mWakeUpTouchLocation = null;
            mStackScroller.setAnimationsEnabled(false);
            mVisualStabilityManager.setScreenOn(false);
            updateVisibleToUser();

            // We need to disable touch events because these might
            // collapse the panel after we expanded it, and thus we would end up with a blank
            // Keyguard.
            mNotificationPanel.setTouchDisabled(true);
            mStatusBarWindow.cancelCurrentTouch();
            if (mLaunchCameraOnFinishedGoingToSleep) {
                mLaunchCameraOnFinishedGoingToSleep = false;

                // This gets executed before we will show Keyguard, so post it in order that the state
                // is correct.
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onCameraLaunchGestureDetected(mLastCameraLaunchSource);
                    }
                });
            }
            updateIsKeyguard();
        }

        @Override
        public void onStartedGoingToSleep() {
            notifyHeadsUpGoingToSleep();
            dismissVolumeDialog();
        }

        @Override
        public void onStartedWakingUp() {
            mDeviceInteractive = true;
            mStackScroller.setAnimationsEnabled(true);
            mVisualStabilityManager.setScreenOn(true);
            mNotificationPanel.setTouchDisabled(false);

            maybePrepareWakeUpFromAod();

            mDozeServiceHost.stopDozing();
            updateVisibleToUser();
            updateIsKeyguard();
        }
    };

    ScreenLifecycle.Observer mScreenObserver = new ScreenLifecycle.Observer() {
        @Override
        public void onScreenTurningOn() {
            mFalsingManager.onScreenTurningOn();
            mNotificationPanel.onScreenTurningOn();

            maybePrepareWakeUpFromAod();

            if (mLaunchCameraOnScreenTurningOn) {
                mNotificationPanel.launchCamera(false, mLastCameraLaunchSource);
                mLaunchCameraOnScreenTurningOn = false;
            }
        }

        @Override
        public void onScreenTurnedOn() {
            mScrimController.wakeUpFromAod();
            mDozeScrimController.onScreenTurnedOn();
            mVisualizerView.setVisible(true);
        }

        @Override
        public void onScreenTurnedOff() {
            mFalsingManager.onScreenOff();
            mVisualizerView.setVisible(false);
            // If we pulse in from AOD, we turn the screen off first. However, updatingIsKeyguard
            // in that case destroys the HeadsUpManager state, so don't do it in that case.
            if (!isPulsing()) {
                updateIsKeyguard();
            }
        }
    };

    public int getWakefulnessState() {
        return mWakefulnessLifecycle.getWakefulness();
    }

    private void maybePrepareWakeUpFromAod() {
        int wakefulness = mWakefulnessLifecycle.getWakefulness();
        if (mDozing && wakefulness == WAKEFULNESS_WAKING && !isPulsing()) {
            mScrimController.prepareWakeUpFromAod();
        }
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
        return !mNotificationData.getActiveNotifications().isEmpty();
    }

    public void wakeUpIfDozing(long time, View where) {
        if (mDozing) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            pm.wakeUp(time, "com.android.systemui:NODOZE");
            mWakeUpComingFromTouch = true;
            where.getLocationInWindow(mTmpInt2);
            mWakeUpTouchLocation = new PointF(mTmpInt2[0] + where.getWidth() / 2,
                    mTmpInt2[1] + where.getHeight() / 2);
            mStatusBarKeyguardViewManager.notifyDeviceWakeUpRequested();
            mFalsingManager.onScreenOnFromTouch();
        }
    }

    @Override
    public void appTransitionCancelled() {
        EventBus.getDefault().send(new AppTransitionFinishedEvent());
    }

    @Override
    public void appTransitionFinished() {
        EventBus.getDefault().send(new AppTransitionFinishedEvent());
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
                mStatusBarKeyguardViewManager.isShowing() && mExpandedVisible, source)) {
            if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Can't launch camera right now, mExpandedVisible: " +
                    mExpandedVisible);
            return;
        }
        if (!mDeviceInteractive) {
            PowerManager pm = mContext.getSystemService(PowerManager.class);
            pm.wakeUp(SystemClock.uptimeMillis(), "com.android.systemui:CAMERA_GESTURE");
            mStatusBarKeyguardViewManager.notifyDeviceWakeUpRequested();
        }
        if (source != StatusBarManager.CAMERA_LAUNCH_SOURCE_SCREEN_GESTURE) {
            vibrateForCameraGesture();
        }
        if (!mStatusBarKeyguardViewManager.isShowing()) {
            startActivityDismissingKeyguard(KeyguardBottomAreaView.INSECURE_CAMERA_INTENT,
                    false /* onlyProvisioned */, true /* dismissShade */,
                    true /* disallowEnterPictureInPictureWhileLaunching */, null /* callback */);
        } else {
            if (!mDeviceInteractive) {
                // Avoid flickering of the scrim when we instant launch the camera and the bouncer
                // comes on.
                mScrimController.dontAnimateBouncerChangesUntilNextFrame();
                mGestureWakeLock.acquire(LAUNCH_TRANSITION_TIMEOUT_MS + 1000L);
            }
            if (isScreenTurningOnOrOn()) {
                if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Launching camera");
                mNotificationPanel.launchCamera(mDeviceInteractive /* animate */, source);
            } else {
                // We need to defer the camera launch until the screen comes on, since otherwise
                // we will dismiss us too early since we are waiting on an activity to be drawn and
                // incorrectly get notified because of the screen on event (which resumes and pauses
                // some activities)
                if (DEBUG_CAMERA_LIFT) Slog.d(TAG, "Deferring until screen turns on");
                mLaunchCameraOnScreenTurningOn = true;
            }
        }
    }

    boolean isCameraAllowedByAdmin() {
        if (mDevicePolicyManager.getCameraDisabled(null, mCurrentUserId)) {
            return false;
        } else if (isKeyguardShowing() && isKeyguardSecure()) {
            // Check if the admin has disabled the camera specifically for the keyguard
            return (mDevicePolicyManager.getKeyguardDisabledFeatures(null, mCurrentUserId)
                    & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) == 0;
        }

        return true;
    }

    private boolean isGoingToSleep() {
        return mWakefulnessLifecycle.getWakefulness()
                == WakefulnessLifecycle.WAKEFULNESS_GOING_TO_SLEEP;
    }

    private boolean isScreenTurningOnOrOn() {
        return mScreenLifecycle.getScreenState() == ScreenLifecycle.SCREEN_TURNING_ON
                || mScreenLifecycle.getScreenState() == ScreenLifecycle.SCREEN_ON;
    }

    public void notifyFpAuthModeChanged() {
        updateDozing();
    }

    private void updateDozing() {
        Trace.beginSection("StatusBar#updateDozing");
        // When in wake-and-unlock while pulsing, keep dozing state until fully unlocked.
        mDozing = mDozingRequested && mState == StatusBarState.KEYGUARD
                || mFingerprintUnlockController.getMode()
                        == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING;
        // When in wake-and-unlock we may not have received a change to mState
        // but we still should not be dozing, manually set to false.
        if (mFingerprintUnlockController.getMode() ==
                FingerprintUnlockController.MODE_WAKE_AND_UNLOCK) {
            mDozing = false;
        }
        mStatusBarWindowManager.setDozing(mDozing);
        mStatusBarKeyguardViewManager.setDozing(mDozing);
        if (mAmbientMediaPlaying != 0 && mAmbientIndicationContainer instanceof DozeReceiver) {
            ((DozeReceiver) mAmbientIndicationContainer).setDozing(mDozing);
        }
        updateDozingState();
        Trace.endSection();
    }

    public VisualizerView getVisualizer() {
        return mVisualizerView;
    }

    public boolean isKeyguardShowing() {
        if (mStatusBarKeyguardViewManager == null) {
            Slog.i(TAG, "isKeyguardShowing() called before startKeyguard(), returning true");
            return true;
        }
        return mStatusBarKeyguardViewManager.isShowing();
    }

    private final class DozeServiceHost implements DozeHost {
        private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
        private boolean mAnimateWakeup;
        private boolean mIgnoreTouchWhilePulsing;

        @Override
        public String toString() {
            return "PSB.DozeServiceHost[mCallbacks=" + mCallbacks.size() + "]";
        }

        public void firePowerSaveChanged(boolean active) {
            for (Callback callback : mCallbacks) {
                callback.onPowerSaveChanged(active);
            }
        }

        public void fireNotificationHeadsUp() {
            for (Callback callback : mCallbacks) {
                callback.onNotificationHeadsUp();
            }
        }

        public void fireNotificationMedia() {
            for (Callback callback : mCallbacks) {
                callback.onNotificationMedia();
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
            }
        }

        @Override
        public void pulseWhileDozing(@NonNull PulseCallback callback, int reason) {
            if (reason == DozeLog.PULSE_REASON_SENSOR_LONG_PRESS) {
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), "com.android.systemui:NODOZE");
                startAssist(new Bundle());
                return;
            }

            mDozeScrimController.pulse(new PulseCallback() {

                @Override
                public void onPulseStarted() {
                    callback.onPulseStarted();
                    Collection<HeadsUpManager.HeadsUpEntry> pulsingEntries =
                            mHeadsUpManager.getAllEntries();
                    if (!pulsingEntries.isEmpty()) {
                        // Only pulse the stack scroller if there's actually something to show.
                        // Otherwise just show the always-on screen.
                        setPulsing(pulsingEntries);
                    }
                    setCleanLayout(mAmbientMediaPlaying == 3 ? reason : -1);
                }

                @Override
                public void onPulseFinished() {
                    callback.onPulseFinished();
                    setPulsing(null);
                    setCleanLayout(-1);
                }

                private void setPulsing(Collection<HeadsUpManager.HeadsUpEntry> pulsing) {
                    mStackScroller.setPulsing(pulsing);
                    mNotificationPanel.setPulsing(pulsing != null);
                    mVisualStabilityManager.setPulsing(pulsing != null);
                    mIgnoreTouchWhilePulsing = false;
                }

                private void setCleanLayout(int reason) {
                    mNotificationPanel.setCleanLayout(reason);
                    mNotificationShelf.setCleanLayout(reason);
                    if (mAmbientMediaPlaying != 0 && mAmbientIndicationContainer != null) {
                        ((AmbientIndicationContainer)mAmbientIndicationContainer).setCleanLayout(reason);
                    }
                }
            }, reason);
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
            mNotificationPanel.refreshTime();
        }

        @Override
        public boolean isPowerSaveActive() {
            return mBatteryController.isPowerSave();
        }

        @Override
        public boolean isPulsingBlocked() {
            return mFingerprintUnlockController.getMode()
                    == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK;
        }

        @Override
        public boolean isProvisioned() {
            return mDeviceProvisionedController.isDeviceProvisioned()
                    && mDeviceProvisionedController.isCurrentUserSetup();
        }

        @Override
        public boolean isBlockingDoze() {
            if (mFingerprintUnlockController.hasPendingAuthentication()) {
                Log.i(TAG, "Blocking AOD because fingerprint has authenticated");
                return true;
            }
            return false;
        }

        @Override
        public void startPendingIntentDismissingKeyguard(PendingIntent intent) {
            StatusBar.this.startPendingIntentDismissingKeyguard(intent);
        }

        @Override
        public void abortPulsing() {
            mDozeScrimController.abortPulsing();
        }

        @Override
        public void extendPulse() {
            mDozeScrimController.extendPulse();
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
        public void onDoubleTap(float screenX, float screenY) {
            if (screenX > 0 && screenY > 0 && mAmbientIndicationContainer != null 
                && mAmbientIndicationContainer.getVisibility() == View.VISIBLE) {
                mAmbientIndicationContainer.getLocationOnScreen(mTmpInt2);
                float viewX = screenX - mTmpInt2[0];
                float viewY = screenY - mTmpInt2[1];
                if (0 <= viewX && viewX <= mAmbientIndicationContainer.getWidth()
                        && 0 <= viewY && viewY <= mAmbientIndicationContainer.getHeight()) {
                    dispatchDoubleTap(viewX, viewY);
                }
            }
        }

        @Override
        public void setDozeScreenBrightness(int value) {
            mStatusBarWindowManager.setDozeScreenBrightness(value);
        }

        @Override
        public void setAodDimmingScrim(float scrimOpacity) {
            mDozeScrimController.setAodDimmingScrim(scrimOpacity);
        }

        public void dispatchDoubleTap(float viewX, float viewY) {
            dispatchTap(mAmbientIndicationContainer, viewX, viewY);
            dispatchTap(mAmbientIndicationContainer, viewX, viewY);
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
    }

    public boolean shouldIgnoreTouch() {
        return isDozing() && mDozeServiceHost.mIgnoreTouchWhilePulsing;
    }

    // Begin Extra BaseStatusBar methods.

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;

    // all notifications
    public static NotificationData mNotificationData;
    protected NotificationStackScrollLayout mStackScroller;

    protected NotificationGroupManager mGroupManager = new NotificationGroupManager();

    protected RemoteInputController mRemoteInputController;

    // for heads up notifications
    protected HeadsUpManager mHeadsUpManager;

    private AboveShelfObserver mAboveShelfObserver;

    // handling reordering
    protected VisualStabilityManager mVisualStabilityManager = new VisualStabilityManager();

    protected int mCurrentUserId = 0;
    final protected SparseArray<UserInfo> mCurrentProfiles = new SparseArray<UserInfo>();

    protected int mLayoutDirection = -1; // invalid
    protected AccessibilityManager mAccessibilityManager;

    protected boolean mDeviceInteractive;

    protected boolean mVisible;
    protected ArraySet<Entry> mHeadsUpEntriesToRemoveOnSwitch = new ArraySet<>();
    protected ArraySet<Entry> mRemoteInputEntriesToRemoveOnCollapse = new ArraySet<>();

    /**
     * Notifications with keys in this set are not actually around anymore. We kept them around
     * when they were canceled in response to a remote input interaction. This allows us to show
     * what you replied and allows you to continue typing into it.
     */
    protected ArraySet<String> mKeysKeptForRemoteInput = new ArraySet<>();

    // mScreenOnFromKeyguard && mVisible.
    private boolean mVisibleToUser;

    private Locale mLocale;

    protected boolean mUseHeadsUp = false;
    protected boolean mHeadsUpTicker = false;
    protected boolean mDisableNotificationAlerts = false;

    protected DevicePolicyManager mDevicePolicyManager;
    protected PowerManager mPowerManager;
    protected StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;

    // public mode, private notifications, etc
    private final SparseBooleanArray mLockscreenPublicMode = new SparseBooleanArray();
    private final SparseBooleanArray mUsersAllowingPrivateNotifications = new SparseBooleanArray();
    private final SparseBooleanArray mUsersAllowingNotifications = new SparseBooleanArray();

    private UserManager mUserManager;

    protected KeyguardManager mKeyguardManager;
    private LockPatternUtils mLockPatternUtils;
    private DeviceProvisionedController mDeviceProvisionedController
            = Dependency.get(DeviceProvisionedController.class);
    protected SystemServicesProxy mSystemServicesProxy;

    // UI-specific methods

    protected WindowManager mWindowManager;
    protected IWindowManager mWindowManagerService;

    protected Display mDisplay;

    protected RecentsComponent mRecents;

    protected RecentController mSlimRecents;

    protected int mZenMode;

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
    protected GestureAnywhereView mGestureAnywhereView;

    // which notification is currently being longpress-examined by the user
    private NotificationGuts mNotificationGutsExposed;
    private MenuItem mGutsMenuItem;

    private KeyboardShortcuts mKeyboardShortcuts;

    protected NotificationShelf mNotificationShelf;
    protected DismissView mDismissView;
    protected EmptyShadeView mEmptyShadeView;

    private NotificationClicker mNotificationClicker = new NotificationClicker();

    protected AssistManager mAssistManager;

    protected boolean mVrMode;

    private static final HashMap<String, Field> fieldCache = new HashMap<String, Field>();

    private Set<String> mNonBlockablePkgs;

    public boolean isDeviceInteractive() {
        return mDeviceInteractive;
    }

    public ArrayList<Pair<StatusBarNotification, Icon>> getNotifications() {
        ArrayList<Pair<StatusBarNotification, Icon>> notifs
                = new ArrayList<Pair<StatusBarNotification, Icon>>();
        for (Entry entry : mNotificationData.getActiveNotifications()) {
            StatusBarNotification sbn = entry.notification;
            Icon icon = entry.notification.getNotification().getSmallIcon();
            notifs.add(new Pair<StatusBarNotification, Icon>(sbn, icon));
        }
        return notifs;
    }

    private final ContentObserver mPieSettingsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
            boolean pieEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.PIE_STATE, 0, UserHandle.USER_CURRENT) == 1;

            updatePieControls(!pieEnabled);
        }
    };

    @Override  // NotificationData.Environment
    public boolean isDeviceProvisioned() {
        return mDeviceProvisionedController.isDeviceProvisioned();
    }

    private final IVrStateCallbacks mVrStateCallbacks = new IVrStateCallbacks.Stub() {
        @Override
        public void onVrStateChanged(boolean enabled) {
            mVrMode = enabled;
        }
    };

    public boolean isDeviceInVrMode() {
        return mVrMode;
    }

    private final DeviceProvisionedListener mDeviceProvisionedListener =
            new DeviceProvisionedListener() {
        @Override
        public void onDeviceProvisionedChanged() {
            updateNotifications();
        }
    };

    protected final ContentObserver mSettingsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
            setZenMode(mode);

            updateLockscreenNotificationSetting();
        }
    };

    private final ContentObserver mLockscreenSettingsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            // We don't know which user changed LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS or
            // LOCK_SCREEN_SHOW_NOTIFICATIONS, so we just dump our cache ...
            mUsersAllowingPrivateNotifications.clear();
            mUsersAllowingNotifications.clear();
            // ... and refresh all the notifications
            updateLockscreenNotificationSetting();
            updateNotifications();
        }
    };

    private RRSettingsObserver mRRSettingsObserver = new RRSettingsObserver(mHandler);
    private class RRSettingsObserver extends ContentObserver {
        RRSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BERRY_GLOBAL_STYLE),
                    true,this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BERRY_FORCE_BLACK),
                    true,this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_TICKER),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HEADS_UP_BLACKLIST_VALUES),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_MEDIA_METADATA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_QUICKBAR_SCROLL_ENABLED),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.QS_TILE_TITLE_VISIBILITY),
                  false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_FOOTER_WARNINGS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_SMART_PULLDOWN),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.LOCK_QS_DISABLED),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN_FP),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.USE_SLIM_RECENTS),
                  false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENTS_ICON_PACK),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.STATUS_BAR_BATTERY_SAVER_COLOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FORCE_AMBIENT_FOR_MEDIA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.FP_SWIPE_TO_DISMISS_NOTIFICATIONS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BLUR_SCALE_PREFERENCE_KEY), 
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BLUR_RADIUS_PREFERENCE_KEY), 
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY), 
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.TRANSLUCENT_NOTIFICATIONS_PREFERENCE_KEY), 
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.TRANSLUCENT_NOTIFICATIONS_PRECENTAGE_PREFERENCE_KEY), 
                    false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.TRANSLUCENT_NOTIFICATIONS_PRECENTAGE_PREFERENCE_KEY), 
                 false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY), 
                 false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.RECENT_APPS_SCALE_PREFERENCE_KEY), 
                 false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.RECENT_APPS_RADIUS_PREFERENCE_KEY), 
                 false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.RECENT_APPS_RADIUS_PREFERENCE_KEY),
                 false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.BLUR_DARK_COLOR_PREFERENCE_KEY), 
                 false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.BLUR_LIGHT_COLOR_PREFERENCE_KEY), 
                 false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                 Settings.System.BLUR_MIXED_COLOR_PREFERENCE_KEY), 
                 false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.NO_SIM_CLUSTER_SWITCH),
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
                    Settings.System.SHOW_BATTERY_PERCENT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.STATUS_BAR_BATTERY_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {         
            if (uri.equals (LineageSettings.System.getUriFor(
                    LineageSettings.System.BERRY_GLOBAL_STYLE)) ||
                    uri.equals (LineageSettings.System.getUriFor(
                    LineageSettings.System.BERRY_FORCE_BLACK))) {
                updateTheme();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.HEADS_UP_BLACKLIST_VALUES))) {
                final String blackString = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.HEADS_UP_BLACKLIST_VALUES);
                splitAndAddToArrayList(mBlacklist, blackString, "\\|");
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_MEDIA_METADATA))) {
                setLockscreenMediaMetadata();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.QS_QUICKBAR_SCROLL_ENABLED))) {
                if (mQuickStatusBarHeader != null) {
                    mQuickStatusBarHeader.updateSettings();
                }
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.QS_TILE_TITLE_VISIBILITY))) {
                updateQsPanelResources();
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.QS_ROWS_PORTRAIT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_ROWS_LANDSCAPE)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_COLUMNS_PORTRAIT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_COLUMNS_LANDSCAPE))) {
                if (mQuickStatusBarHeader != null) {
                    mQuickStatusBarHeader.updateSettings();
                }
                updateQsPanelResources();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.QS_FOOTER_WARNINGS))) {
                setQsPanelOptions();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.QS_SMART_PULLDOWN))) {
                setStatusBarWindowViewOptions();
            } else if (uri.equals(Settings.Secure.getUriFor(
                    Settings.Secure.LOCK_QS_DISABLED))) {
                setStatusBarWindowViewOptions(); 
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.USE_SLIM_RECENTS))) {
                updateRecentsMode();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.RECENTS_ICON_PACK))) {
                updateRecentsIconPack();
            } else if (uri.equals(Settings.Secure.getUriFor(
                    Settings.Secure.STATUS_BAR_BATTERY_SAVER_COLOR))) {
                mBatterySaverColor = Settings.Secure.getIntForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.STATUS_BAR_BATTERY_SAVER_COLOR,
                        0xfff4511e, UserHandle.USER_CURRENT);
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.FORCE_AMBIENT_FOR_MEDIA))) {
                setForceAmbient();
            } else if (uri.equals(Settings.Secure.getUriFor(
                    Settings.Secure.FP_SWIPE_TO_DISMISS_NOTIFICATIONS))) {
                setFpToDismissNotifications();
            }  else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY))) {
                    mBlurredRecents = Settings.System.getIntForUser(
                                        mContext.getContentResolver(),
                                        Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY,
                                        0, UserHandle.USER_CURRENT) == 1;
                    RecentsActivity.startBlurTask();
                    updatePreferences(mContext);
            }  else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.NO_SIM_CLUSTER_SWITCH))) {
                    trytoinflateclusters();
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.QS_ROWS_PORTRAIT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_ROWS_LANDSCAPE)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_COLUMNS_PORTRAIT)) ||
                    uri.equals(Settings.System.getUriFor(Settings.System.QS_COLUMNS_LANDSCAPE))) {
                setQsRowsColumns();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.SHOW_BATTERY_PERCENT))
                    || uri.equals(Settings.Secure.getUriFor(
                    Settings.Secure.STATUS_BAR_BATTERY_STYLE))) {
                updateBatterySettings();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE))) {
                updateTickerAnimation();
            }
        }

        public void update() {
            setStatusBarWindowViewOptions();
            setHeadsUpBlacklist();
            setLockscreenMediaMetadata();
            setQsRowsColumns();
            updateQsPanelResources();
            updateRecentsMode();
            updateRecentsIconPack();
            setQsPanelOptions();
            setLockscreenMaxNotifications();
            updateFPQuickPulldown();
            updateBatterySaverColor();
            setForceAmbient();
            setFpToDismissNotifications();
            updateBlurSettings();
            updateBatterySettings();
            updateClockStyle();
            updateTickerAnimation();
        }
    }

    public void updateBatterySettings() {
        if (mStatusBarView != null) {
            mStatusBarView.updateBatterySettings();
        }
        if (mKeyguardStatusBar != null) {
            mKeyguardStatusBar.updateBatterySettings();
        }
        if (mQuickStatusBarHeader != null) {
            mQuickStatusBarHeader.updateBatterySettings();
        }
    }

    private void updateFPQuickPulldown() {
        mFingerprintQuickPulldown = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN_FP, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private void setStatusBarWindowViewOptions() {
        if (mStatusBarWindow != null) {
            mStatusBarWindow.setStatusBarWindowViewOptions();
        }
    }

    private void setHeadsUpBlacklist() {
        final String blackString = Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.HEADS_UP_BLACKLIST_VALUES);
        splitAndAddToArrayList(mBlacklist, blackString, "\\|");
    }

    private void updateBatterySaverColor() {
        mBatterySaverColor = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.STATUS_BAR_BATTERY_SAVER_COLOR, 0xfff4511e, UserHandle.USER_CURRENT);
    }

    private void updateClockStyle() {
       mClockPos = Settings.System.getInt(mContext.getContentResolver(),
			    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION, CLOCK_DATE_POSITION_DEFAULT);
    }

    private void setForceAmbient() {
        mAmbientMediaPlaying = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FORCE_AMBIENT_FOR_MEDIA, 0,
                UserHandle.USER_CURRENT);
        if (mAmbientMediaPlaying != 0 && mAmbientIndicationContainer != null) {
            ((AmbientIndicationContainer)mAmbientIndicationContainer).setIndication(mMediaMetadata);
        }
    }

    private void setFpToDismissNotifications() {
        mFpDismissNotifications = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FP_SWIPE_TO_DISMISS_NOTIFICATIONS, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private void updateBlurSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            mBlurScale = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.BLUR_SCALE_PREFERENCE_KEY, 10);
            mBlurRadius = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.BLUR_RADIUS_PREFERENCE_KEY, 5);
            mBlurredStatusBarExpandedEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY, 0, UserHandle.USER_CURRENT) == 1;
            mTranslucentNotifications = Settings.System.getIntForUser(resolver,
                    Settings.System.TRANSLUCENT_NOTIFICATIONS_PREFERENCE_KEY, 0, UserHandle.USER_CURRENT) == 1;
            mNotTranslucencyPercentage = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.TRANSLUCENT_NOTIFICATIONS_PRECENTAGE_PREFERENCE_KEY, 70);
           mBlurredRecents = Settings.System.getIntForUser(resolver,
                    Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY, 0, UserHandle.USER_CURRENT) == 1;
            mScaleRecents = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.RECENT_APPS_SCALE_PREFERENCE_KEY, 6);
            mRadiusRecents = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.RECENT_APPS_RADIUS_PREFERENCE_KEY, 3);
            mBlurDarkColorFilter = Settings.System.getInt(mContext.getContentResolver(), 
                    Settings.System.BLUR_DARK_COLOR_PREFERENCE_KEY, Color.LTGRAY);
            mBlurMixedColorFilter = Settings.System.getInt(mContext.getContentResolver(), 
                    Settings.System.BLUR_MIXED_COLOR_PREFERENCE_KEY, Color.GRAY);
            mBlurLightColorFilter = Settings.System.getInt(mContext.getContentResolver(), 
                    Settings.System.BLUR_LIGHT_COLOR_PREFERENCE_KEY, Color.DKGRAY);
            RecentsActivity.updateBlurColors(mBlurDarkColorFilter,mBlurMixedColorFilter,mBlurLightColorFilter);
            RecentsActivity.updateRadiusScale(mScaleRecents,mRadiusRecents);
    }

    protected final ContentObserver mNavbarObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            boolean showing = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.NAVIGATION_BAR_VISIBLE,
                    DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0) != 0;
            if (!showing && mNavigationBar != null && mNavigationBarView != null) {
                removeNavigationBar();
            } else if (showing && mNavigationBar == null && mNavigationBarView == null) {
                createNavigationBar();
            }
            if (!DUActionUtils.hasNavbarByDefault(mContext)) {
                Intent intent = new Intent("com.cyanogenmod.action.UserChanged");
                intent.setPackage("com.android.settings");
                mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
            }
        }
    };

    private void setLockscreenMediaMetadata() {
        mLockscreenMediaMetadata = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MEDIA_METADATA, 0, UserHandle.USER_CURRENT) == 1;
    }

    private void setLockscreenMaxNotifications() {
        mMaxKeyguardNotifConfig = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 5, UserHandle.USER_CURRENT);
    }

    private void updateQsPanelResources() {
        if (mQSPanel != null) {
            mQSPanel.updateResources();
        }
    }

    private void setQsRowsColumns() {
        if (mQSPanel != null) {
            mQSPanel.updateResources();
        }
    }

    private void setQsPanelOptions() {
        if (mQSPanel != null) {
            mQSPanel.updateSettings();
        }
    }

    private void updateRecentsMode() {
        boolean slimRecents = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.USE_SLIM_RECENTS, 0, mCurrentUserId) == 1;
        if (slimRecents) {
            mRecents.evictAllCaches();
            mRecents.removeSbCallbacks();
            mSlimRecents = new RecentController(mContext);
            rebuildRecentsScreen();
            mSlimRecents.addSbCallbacks();
        } else {
            mRecents.addSbCallbacks();
            if (mSlimRecents != null) {
                mSlimRecents.evictAllCaches();
                mSlimRecents.removeSbCallbacks();
                mSlimRecents = null;
            }
        }
    }

    private void rebuildRecentsScreen() {
        if (mSlimRecents != null) {
            mSlimRecents.rebuildRecentsScreen();
        }
    }

    private void updateRecentsIconPack() {
        String currentIconPack = Settings.System.getStringForUser(mContext.getContentResolver(),
            Settings.System.RECENTS_ICON_PACK, mCurrentUserId);
        IconPackHelper.getInstance(mContext).updatePrefs(currentIconPack);
        mRecents.resetIconCache();
    }

    private void updateTickerAnimation() {
        mTickerAnimationMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE, 1, UserHandle.USER_CURRENT);
        if (mTicker != null) {
            mTicker.updateAnimation(mTickerAnimationMode);
        }
    }

    private RemoteViews.OnClickHandler mOnClickHandler = new RemoteViews.OnClickHandler() {

        @Override
        public boolean onClickHandler(
                final View view, final PendingIntent pendingIntent, final Intent fillInIntent) {
            wakeUpIfDozing(SystemClock.uptimeMillis(), view);


            if (handleRemoteInput(view, pendingIntent, fillInIntent)) {
                return true;
            }

            if (DEBUG) {
                Log.v(TAG, "Notification click handler invoked for intent: " + pendingIntent);
            }
            logActionClick(view);
            // The intent we are sending is for the application, which
            // won't have permission to immediately start an activity after
            // the user switches to home.  We know it is safe to do at this
            // point, so make sure new activity switches are now allowed.
            try {
                ActivityManager.getService().resumeAppSwitches();
            } catch (RemoteException e) {
            }
            final boolean isActivity = pendingIntent.isActivity();
            if (isActivity) {
                final boolean keyguardShowing = mStatusBarKeyguardViewManager.isShowing();
                final boolean afterKeyguardGone = PreviewInflater.wouldLaunchResolverActivity(
                        mContext, pendingIntent.getIntent(), mCurrentUserId);
                dismissKeyguardThenExecute(new OnDismissAction() {
                    @Override
                    public boolean onDismiss() {
                        try {
                            ActivityManager.getService().resumeAppSwitches();
                        } catch (RemoteException e) {
                        }

                        boolean handled = superOnClickHandler(view, pendingIntent, fillInIntent);

                        // close the shade if it was open
                        if (handled && !mNotificationPanel.isFullyCollapsed()) {
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                                    true /* force */);
                            visibilityChanged(false);
                            mAssistManager.hideAssist();

                            // Wait for activity start.
                            return true;
                        } else {
                            return false;
                        }

                    }
                }, afterKeyguardGone);
                return true;
            } else {
                return superOnClickHandler(view, pendingIntent, fillInIntent);
            }
        }

        private void logActionClick(View view) {
            ViewParent parent = view.getParent();
            String key = getNotificationKeyForParent(parent);
            if (key == null) {
                Log.w(TAG, "Couldn't determine notification for click.");
                return;
            }
            int index = -1;
            // If this is a default template, determine the index of the button.
            if (view.getId() == com.android.internal.R.id.action0 &&
                    parent != null && parent instanceof ViewGroup) {
                ViewGroup actionGroup = (ViewGroup) parent;
                index = actionGroup.indexOfChild(view);
            }
            try {
                mBarService.onNotificationActionClick(key, index);
            } catch (RemoteException e) {
                // Ignore
            }
        }

        private String getNotificationKeyForParent(ViewParent parent) {
            while (parent != null) {
                if (parent instanceof ExpandableNotificationRow) {
                    return ((ExpandableNotificationRow) parent).getStatusBarNotification().getKey();
                }
                parent = parent.getParent();
            }
            return null;
        }

        private boolean superOnClickHandler(View view, PendingIntent pendingIntent,
                Intent fillInIntent) {
            return super.onClickHandler(view, pendingIntent, fillInIntent,
                    StackId.FULLSCREEN_WORKSPACE_STACK_ID);
        }

        private boolean handleRemoteInput(View view, PendingIntent pendingIntent, Intent fillInIntent) {
            Object tag = view.getTag(com.android.internal.R.id.remote_input_tag);
            RemoteInput[] inputs = null;
            if (tag instanceof RemoteInput[]) {
                inputs = (RemoteInput[]) tag;
            }

            if (inputs == null) {
                return false;
            }

            RemoteInput input = null;

            for (RemoteInput i : inputs) {
                if (i.getAllowFreeFormInput()) {
                    input = i;
                }
            }

            if (input == null) {
                return false;
            }

            ViewParent p = view.getParent();
            RemoteInputView riv = null;
            while (p != null) {
                if (p instanceof View) {
                    View pv = (View) p;
                    if (pv.isRootNamespace()) {
                        riv = findRemoteInputView(pv);
                        break;
                    }
                }
                p = p.getParent();
            }
            ExpandableNotificationRow row = null;
            while (p != null) {
                if (p instanceof ExpandableNotificationRow) {
                    row = (ExpandableNotificationRow) p;
                    break;
                }
                p = p.getParent();
            }

            if (row == null) {
                return false;
            }

            row.setUserExpanded(true);

            if (!mAllowLockscreenRemoteInput) {
                final int userId = pendingIntent.getCreatorUserHandle().getIdentifier();
                if (isLockscreenPublicMode(userId)) {
                    onLockedRemoteInput(row, view);
                    return true;
                }
                if (mUserManager.getUserInfo(userId).isManagedProfile()
                        && mKeyguardManager.isDeviceLocked(userId)) {
                    onLockedWorkRemoteInput(userId, row, view);
                    return true;
                }
            }

            if (riv == null) {
                riv = findRemoteInputView(row.getPrivateLayout().getExpandedChild());
                if (riv == null) {
                    return false;
                }
                if (!row.getPrivateLayout().getExpandedChild().isShown()) {
                    onMakeExpandedVisibleForRemoteInput(row, view);
                    return true;
                }
            }

            int width = view.getWidth();
            if (view instanceof TextView) {
                // Center the reveal on the text which might be off-center from the TextView
                TextView tv = (TextView) view;
                if (tv.getLayout() != null) {
                    int innerWidth = (int) tv.getLayout().getLineWidth(0);
                    innerWidth += tv.getCompoundPaddingLeft() + tv.getCompoundPaddingRight();
                    width = Math.min(width, innerWidth);
                }
            }
            int cx = view.getLeft() + width / 2;
            int cy = view.getTop() + view.getHeight() / 2;
            int w = riv.getWidth();
            int h = riv.getHeight();
            int r = Math.max(
                    Math.max(cx + cy, cx + (h - cy)),
                    Math.max((w - cx) + cy, (w - cx) + (h - cy)));

            riv.setRevealParameters(cx, cy, r);
            riv.setPendingIntent(pendingIntent);
            riv.setRemoteInput(inputs, input);
            riv.focusAnimated();

            return true;
        }

        private RemoteInputView findRemoteInputView(View v) {
            if (v == null) {
                return null;
            }
            return (RemoteInputView) v.findViewWithTag(RemoteInputView.VIEW_TAG);
        }
    };

    private final BroadcastReceiver mBaseBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                mCurrentUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                updateCurrentProfilesCache();
                if (true) Log.v(TAG, "userId " + mCurrentUserId + " is in the house");

                updateLockscreenNotificationSetting();

                userSwitched(mCurrentUserId);
            } else if (Intent.ACTION_USER_ADDED.equals(action)) {
                updateCurrentProfilesCache();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                List<ActivityManager.RecentTaskInfo> recentTask = null;
                try {
                    recentTask = ActivityManager.getService().getRecentTasks(1,
                            ActivityManager.RECENT_WITH_EXCLUDED
                            | ActivityManager.RECENT_INCLUDE_PROFILES,
                            mCurrentUserId).getList();
                } catch (RemoteException e) {
                    // Abandon hope activity manager not running.
                }
                if (recentTask != null && recentTask.size() > 0) {
                    UserInfo user = mUserManager.getUserInfo(recentTask.get(0).userId);
                    if (user != null && user.isManagedProfile()) {
                        Toast toast = Toast.makeText(mContext,
                                R.string.managed_profile_foreground_toast,
                                Toast.LENGTH_SHORT);
                        TextView text = (TextView) toast.getView().findViewById(
                                android.R.id.message);
                        text.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                R.drawable.stat_sys_managed_profile_status, 0, 0, 0);
                        int paddingPx = mContext.getResources().getDimensionPixelSize(
                                R.dimen.managed_profile_toast_padding);
                        text.setCompoundDrawablePadding(paddingPx);
                        toast.show();
                    }
                }
            } else if (BANNER_ACTION_CANCEL.equals(action) || BANNER_ACTION_SETUP.equals(action)) {
                mNoMan.cancel(SystemMessage.NOTE_HIDDEN_NOTIFICATIONS);

                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.SHOW_NOTE_ABOUT_NOTIFICATION_HIDING, 0);
                if (BANNER_ACTION_SETUP.equals(action)) {
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                            true /* force */);
                    mContext.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_REDACTION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    );
                }
            } else if (NOTIFICATION_UNLOCKED_BY_WORK_CHALLENGE_ACTION.equals(action)) {
                final IntentSender intentSender = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                final String notificationKey = intent.getStringExtra(Intent.EXTRA_INDEX);
                if (intentSender != null) {
                    try {
                        mContext.startIntentSender(intentSender, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        /* ignore */
                    }
                }
                if (notificationKey != null) {
                    try {
                        mBarService.onNotificationClick(notificationKey);
                    } catch (RemoteException e) {
                        /* ignore */
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mAllUsersReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);

            if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED.equals(action) &&
                    isCurrentProfile(getSendingUserId())) {
                mUsersAllowingPrivateNotifications.clear();
                updateLockscreenNotificationSetting();
                updateNotifications();
            } else if (Intent.ACTION_DEVICE_LOCKED_CHANGED.equals(action)) {
                if (userId != mCurrentUserId && isCurrentProfile(userId)) {
                    onWorkChallengeChanged();
                }
            }
        }
    };

    private final NotificationListenerWithPlugins mNotificationListener =
            new NotificationListenerWithPlugins() {
        @Override
        public void onListenerConnected() {
            if (DEBUG) Log.d(TAG, "onListenerConnected");
            onPluginConnected();
            final StatusBarNotification[] notifications = getActiveNotifications();
            if (notifications == null) {
                Log.w(TAG, "onListenerConnected unable to get active notifications.");
                return;
            }
            final RankingMap currentRanking = getCurrentRanking();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (StatusBarNotification sbn : notifications) {
                        try {
                            addNotification(sbn, currentRanking);
                        } catch (InflationException e) {
                            handleInflationException(sbn, e);
                        }
                    }
                }
            });
        }

        @Override
        public void onNotificationPosted(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onNotificationPosted: " + sbn);
            if (sbn != null && !onPluginNotificationPosted(sbn, rankingMap)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        processForRemoteInput(sbn.getNotification());
                        String key = sbn.getKey();
                        mKeysKeptForRemoteInput.remove(key);
                        boolean isUpdate = mNotificationData.get(key) != null;
                        // In case we don't allow child notifications, we ignore children of
                        // notifications that have a summary, since we're not going to show them
                        // anyway. This is true also when the summary is canceled,
                        // because children are automatically canceled by NoMan in that case.
                        if (!ENABLE_CHILD_NOTIFICATIONS
                            && mGroupManager.isChildInGroupWithSummary(sbn)) {
                            if (DEBUG) {
                                Log.d(TAG, "Ignoring group child due to existing summary: " + sbn);
                            }

                            // Remove existing notification to avoid stale data.
                            if (isUpdate) {
                                removeNotification(key, rankingMap);
                            } else {
                                mNotificationData.updateRanking(rankingMap);
                            }
                            return;
                        }
                        try {
                            if (isUpdate) {
                                updateNotification(sbn, rankingMap);
                            } else {
                                addNotification(sbn, rankingMap);
                            }
                        } catch (InflationException e) {
                            handleInflationException(sbn, e);
                        }
                    }
                });
            }
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onNotificationRemoved: " + sbn);
            if (sbn != null && !onPluginNotificationRemoved(sbn, rankingMap)) {
                final String key = sbn.getKey();
                mHandler.post(() -> removeNotification(key, rankingMap));
            }
        }

        @Override
        public void onNotificationRankingUpdate(final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onRankingUpdate");
            if (rankingMap != null) {
                RankingMap r = onPluginRankingUpdate(rankingMap);
                mHandler.post(() -> updateNotificationRanking(r));
            }
        }

    };

    private void updateCurrentProfilesCache() {
        synchronized (mCurrentProfiles) {
            mCurrentProfiles.clear();
            if (mUserManager != null) {
                for (UserInfo user : mUserManager.getProfiles(mCurrentUserId)) {
                    mCurrentProfiles.put(user.id, user);
                }
            }
        }
    }

    protected void notifyUserAboutHiddenNotifications() {
        if (0 != Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.SHOW_NOTE_ABOUT_NOTIFICATION_HIDING, 1)) {
            Log.d(TAG, "user hasn't seen notification about hidden notifications");
            if (!mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser())) {
                Log.d(TAG, "insecure lockscreen, skipping notification");
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.SHOW_NOTE_ABOUT_NOTIFICATION_HIDING, 0);
                return;
            }
            Log.d(TAG, "disabling lockecreen notifications and alerting the user");
            // disable lockscreen notifications until user acts on the banner.
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0);
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, 0);

            final String packageName = mContext.getPackageName();
            PendingIntent cancelIntent = PendingIntent.getBroadcast(mContext, 0,
                    new Intent(BANNER_ACTION_CANCEL).setPackage(packageName),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            PendingIntent setupIntent = PendingIntent.getBroadcast(mContext, 0,
                    new Intent(BANNER_ACTION_SETUP).setPackage(packageName),
                    PendingIntent.FLAG_CANCEL_CURRENT);

            final int colorRes = com.android.internal.R.color.system_notification_accent_color;
            Notification.Builder note =
                    new Notification.Builder(mContext, NotificationChannels.GENERAL)
                            .setSmallIcon(R.drawable.ic_android)
                            .setContentTitle(mContext.getString(
                                    R.string.hidden_notifications_title))
                            .setContentText(mContext.getString(R.string.hidden_notifications_text))
                            .setOngoing(true)
                            .setColor(mContext.getColor(colorRes))
                            .setContentIntent(setupIntent)
                            .addAction(R.drawable.ic_close,
                                    mContext.getString(R.string.hidden_notifications_cancel),
                                    cancelIntent)
                            .addAction(R.drawable.ic_settings,
                                    mContext.getString(R.string.hidden_notifications_setup),
                                    setupIntent);
            overrideNotificationAppName(mContext, note);

            mNoMan.notify(SystemMessage.NOTE_HIDDEN_NOTIFICATIONS, note.build());
        }
    }

    @Override  // NotificationData.Environment
    public boolean isNotificationForCurrentProfiles(StatusBarNotification n) {
        final int thisUserId = mCurrentUserId;
        final int notificationUserId = n.getUserId();
        if (DEBUG && MULTIUSER_DEBUG) {
            Log.v(TAG, String.format("%s: current userid: %d, notification userid: %d",
                    n, thisUserId, notificationUserId));
        }
        return isCurrentProfile(notificationUserId);
    }

    protected void setNotificationShown(StatusBarNotification n) {
        setNotificationsShown(new String[]{n.getKey()});
    }

    protected void setNotificationsShown(String[] keys) {
        try {
            mNotificationListener.setNotificationsShown(keys);
        } catch (RuntimeException e) {
            Log.d(TAG, "failed setNotificationsShown: ", e);
        }
    }

    protected boolean isCurrentProfile(int userId) {
        synchronized (mCurrentProfiles) {
            return userId == UserHandle.USER_ALL || mCurrentProfiles.get(userId) != null;
        }
    }

    @Override
    public NotificationGroupManager getGroupManager() {
        return mGroupManager;
    }

    public boolean isMediaNotification(NotificationData.Entry entry) {
        // TODO: confirm that there's a valid media key
        return entry.getExpandedContentView() != null &&
               entry.getExpandedContentView()
                       .findViewById(com.android.internal.R.id.media_actions) != null;
    }

    // The button in the guts that links to the system notification settings for that app
    private void startAppNotificationSettingsActivity(String packageName, final int appUid,
            final NotificationChannel channel) {
        final Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        intent.putExtra(Settings.EXTRA_APP_UID, appUid);
        if (channel != null) {
            intent.putExtra(EXTRA_FRAGMENT_ARG_KEY, channel.getId());
        }
        startNotificationGutsIntent(intent, appUid);
    }

    private void startNotificationGutsIntent(final Intent intent, final int appUid) {
        dismissKeyguardThenExecute(new OnDismissAction() {
            @Override
            public boolean onDismiss() {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        TaskStackBuilder.create(mContext)
                                .addNextIntentWithParentStack(intent)
                                .startActivities(getActivityOptions(),
                                        new UserHandle(UserHandle.getUserId(appUid)));
                    }
                });
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL, true /* force */);
                return true;
            }
        }, false /* afterKeyguardGone */);
    }

    public void setNotificationSnoozed(StatusBarNotification sbn, SnoozeOption snoozeOption) {
        if (snoozeOption.getSnoozeCriterion() != null) {
            mNotificationListener.snoozeNotification(sbn.getKey(),
                    snoozeOption.getSnoozeCriterion().getId());
        } else {
            mNotificationListener.snoozeNotification(sbn.getKey(),
                    snoozeOption.getMinutesToSnoozeFor() * 60 * 1000);
        }
    }

    private void bindGuts(final ExpandableNotificationRow row, MenuItem item) {
        row.inflateGuts();
        row.setGutsView(item);
        final StatusBarNotification sbn = row.getStatusBarNotification();
        row.setTag(sbn.getPackageName());
        final NotificationGuts guts = row.getGuts();
        guts.setClosedListener((NotificationGuts g) -> {
            if (!g.willBeRemoved() && !row.isRemoved()) {
                mStackScroller.onHeightChanged(row, !isPanelFullyCollapsed() /* needsAnimation */);
            }
            if (mNotificationGutsExposed == g) {
                mNotificationGutsExposed = null;
                mGutsMenuItem = null;
            }
            String key = sbn.getKey();
            if (key.equals(mKeyToRemoveOnGutsClosed)) {
                mKeyToRemoveOnGutsClosed = null;
                removeNotification(key, mLatestRankingMap);
            }
        });

        View gutsView = item.getGutsView();
        if (gutsView instanceof NotificationSnooze) {
            NotificationSnooze snoozeGuts = (NotificationSnooze) gutsView;
            snoozeGuts.setSnoozeListener(mStackScroller.getSwipeActionHelper());
            snoozeGuts.setStatusBarNotification(sbn);
            snoozeGuts.setSnoozeOptions(row.getEntry().snoozeCriteria);
            guts.setHeightChangedListener((NotificationGuts g) -> {
                mStackScroller.onHeightChanged(row, row.isShown() /* needsAnimation */);
            });
        }

        if (gutsView instanceof NotificationInfo) {
            final UserHandle userHandle = sbn.getUser();
            PackageManager pmUser = getPackageManagerForUser(mContext,
                    userHandle.getIdentifier());
            final INotificationManager iNotificationManager = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
            final String pkg = sbn.getPackageName();
            NotificationInfo info = (NotificationInfo) gutsView;
            // Settings link is only valid for notifications that specify a user, unless this is the
            // system user.
            NotificationInfo.OnSettingsClickListener onSettingsClick = null;
            if (!userHandle.equals(UserHandle.ALL) || mCurrentUserId == UserHandle.USER_SYSTEM) {
                onSettingsClick = (View v, NotificationChannel channel, int appUid) -> {
                    mMetricsLogger.action(MetricsEvent.ACTION_NOTE_INFO);
                    guts.resetFalsingCheck();
                    startAppNotificationSettingsActivity(pkg, appUid, channel);
                };
            }
            final NotificationInfo.OnAppSettingsClickListener onAppSettingsClick = (View v,
                    Intent intent) -> {
                mMetricsLogger.action(MetricsEvent.ACTION_APP_NOTE_SETTINGS);
                guts.resetFalsingCheck();
                startNotificationGutsIntent(intent, sbn.getUid());
            };
            final View.OnClickListener onDoneClick = (View v) -> {
                saveAndCloseNotificationMenu(info, row, guts, v);
            };
            final NotificationInfo.CheckSaveListener checkSaveListener =
                    (Runnable saveImportance) -> {
                // If the user has security enabled, show challenge if the setting is changed.
                if (isLockscreenPublicMode(userHandle.getIdentifier())
                        && (mState == StatusBarState.KEYGUARD
                                || mState == StatusBarState.SHADE_LOCKED)) {
                    onLockedNotificationImportanceChange(() -> {
                        saveImportance.run();
                        return true;
                    });
                } else {
                    saveImportance.run();
                }
            };

            ArraySet<NotificationChannel> channels = new ArraySet<NotificationChannel>();
            channels.add(row.getEntry().channel);
            if (row.isSummaryWithChildren()) {
                // If this is a summary, then add in the children notification channels for the
                // same user and pkg.
                final List<ExpandableNotificationRow> childrenRows = row.getNotificationChildren();
                final int numChildren = childrenRows.size();
                for (int i = 0; i < numChildren; i++) {
                    final ExpandableNotificationRow childRow = childrenRows.get(i);
                    final NotificationChannel childChannel = childRow.getEntry().channel;
                    final StatusBarNotification childSbn = childRow.getStatusBarNotification();
                    if (childSbn.getUser().equals(userHandle) &&
                            childSbn.getPackageName().equals(pkg)) {
                        channels.add(childChannel);
                    }
                }
            }
            try {
                info.bindNotification(pmUser, iNotificationManager, pkg, new ArrayList(channels),
                        row.getEntry().channel.getImportance(), sbn, onSettingsClick,
                        onAppSettingsClick, onDoneClick, checkSaveListener,
                        mNonBlockablePkgs);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void saveAndCloseNotificationMenu(NotificationInfo info,
            ExpandableNotificationRow row, NotificationGuts guts, View done) {
        guts.resetFalsingCheck();
        int[] rowLocation = new int[2];
        int[] doneLocation = new int[2];
        row.getLocationOnScreen(rowLocation);
        done.getLocationOnScreen(doneLocation);

        final int centerX = done.getWidth() / 2;
        final int centerY = done.getHeight() / 2;
        final int x = doneLocation[0] - rowLocation[0] + centerX;
        final int y = doneLocation[1] - rowLocation[1] + centerY;
        closeAndSaveGuts(false /* removeLeavebehind */, false /* force */,
                true /* removeControls */, x, y, true /* resetMenu */);
    }

    protected SwipeHelper.LongPressListener getNotificationLongClicker() {
        return new SwipeHelper.LongPressListener() {
            @Override
            public boolean onLongPress(View v, final int x, final int y,
                    MenuItem item) {
                if (!(v instanceof ExpandableNotificationRow)) {
                    return false;
                }
                if (v.getWindowToken() == null) {
                    Log.e(TAG, "Trying to show notification guts, but not attached to window");
                    return false;
                }

                final ExpandableNotificationRow row = (ExpandableNotificationRow) v;
                if (row.isDark()) {
                    return false;
                }
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (row.areGutsExposed()) {
                    closeAndSaveGuts(false /* removeLeavebehind */, false /* force */,
                            true /* removeControls */, -1 /* x */, -1 /* y */,
                            true /* resetMenu */);
                    return false;
                }
                bindGuts(row, item);
                NotificationGuts guts = row.getGuts();

                // Assume we are a status_bar_notification_row
                if (guts == null) {
                    // This view has no guts. Examples are the more card or the dismiss all view
                    return false;
                }

                mMetricsLogger.action(MetricsEvent.ACTION_NOTE_CONTROLS);

                // ensure that it's laid but not visible until actually laid out
                guts.setVisibility(View.INVISIBLE);
                // Post to ensure the the guts are properly laid out.
                guts.post(new Runnable() {
                    @Override
                    public void run() {
                        if (row.getWindowToken() == null) {
                            Log.e(TAG, "Trying to show notification guts, but not attached to "
                                    + "window");
                            return;
                        }
                        closeAndSaveGuts(true /* removeLeavebehind */, true /* force */,
                                true /* removeControls */, -1 /* x */, -1 /* y */,
                                false /* resetMenu */);
                        guts.setVisibility(View.VISIBLE);
                        final double horz = Math.max(guts.getWidth() - x, x);
                        final double vert = Math.max(guts.getHeight() - y, y);
                        final float r = (float) Math.hypot(horz, vert);
                        final Animator a
                                = ViewAnimationUtils.createCircularReveal(guts, x, y, 0, r);
                        a.setDuration(StackStateAnimator.ANIMATION_DURATION_STANDARD);
                        a.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
                        a.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                // Move the notification view back over the menu
                                row.resetTranslation();
                            }
                        });
                        a.start();
                        final boolean needsFalsingProtection =
                                (mState == StatusBarState.KEYGUARD &&
                                !mAccessibilityManager.isTouchExplorationEnabled());
                        guts.setExposed(true /* exposed */, needsFalsingProtection);
                        row.closeRemoteInput();
                        mStackScroller.onHeightChanged(row, true /* needsAnimation */);
                        mNotificationGutsExposed = guts;
                        mGutsMenuItem = item;
                    }
                });
                return true;
            }
        };
    }

    /**
     * Returns the exposed NotificationGuts or null if none are exposed.
     */
    public NotificationGuts getExposedGuts() {
        return mNotificationGutsExposed;
    }

    /**
     * Closes guts or notification menus that might be visible and saves any changes.
     *
     * @param removeLeavebehinds true if leavebehinds (e.g. snooze) should be closed.
     * @param force true if guts should be closed regardless of state (used for snooze only).
     * @param removeControls true if controls (e.g. info) should be closed.
     * @param x if closed based on touch location, this is the x touch location.
     * @param y if closed based on touch location, this is the y touch location.
     * @param resetMenu if any notification menus that might be revealed should be closed.
     */
    public void closeAndSaveGuts(boolean removeLeavebehinds, boolean force, boolean removeControls,
            int x, int y, boolean resetMenu) {
        if (mNotificationGutsExposed != null) {
            mNotificationGutsExposed.closeControls(removeLeavebehinds, removeControls, x, y, force);
        }
        if (resetMenu) {
            mStackScroller.resetExposedMenuView(false /* animate */, true /* force */);
        }
    }

    @Override
    public void toggleSplitScreen() {
        toggleSplitScreenMode(-1 /* metricsDockAction */, -1 /* metricsUndockAction */);
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
            recomputeDisableFlags(true);
        }
        updateHideIconsForBouncer(true /* animate */);
    }

    protected void sendCloseSystemWindows(String reason) {
        try {
            ActivityManager.getService().closeSystemDialogs(reason);
        } catch (RemoteException e) {
        }
    }

    protected void toggleKeyboardShortcuts(int deviceId) {
        KeyboardShortcuts.toggle(mContext, deviceId);
    }

    protected void dismissKeyboardShortcuts() {
        KeyboardShortcuts.dismiss();
    }

    /**
     * Save the current "public" (locked and secure) state of the lockscreen.
     */
    public void setLockscreenPublicMode(boolean publicMode, int userId) {
        mLockscreenPublicMode.put(userId, publicMode);
    }

    public boolean isLockscreenPublicMode(int userId) {
        if (userId == UserHandle.USER_ALL) {
            return mLockscreenPublicMode.get(mCurrentUserId, false);
        }
        return mLockscreenPublicMode.get(userId, false);
    }

    /**
     * Has the given user chosen to allow notifications to be shown even when the lockscreen is in
     * "public" (secure & locked) mode?
     */
    public boolean userAllowsNotificationsInPublic(int userHandle) {
        if (userHandle == UserHandle.USER_ALL) {
            return true;
        }

        if (mUsersAllowingNotifications.indexOfKey(userHandle) < 0) {
            final boolean allowed = 0 != Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0, userHandle);
            mUsersAllowingNotifications.append(userHandle, allowed);
            return allowed;
        }

        return mUsersAllowingNotifications.get(userHandle);
    }

    /**
     * Has the given user chosen to allow their private (full) notifications to be shown even
     * when the lockscreen is in "public" (secure & locked) mode?
     */
    public boolean userAllowsPrivateNotificationsInPublic(int userHandle) {
        if (userHandle == UserHandle.USER_ALL) {
            return true;
        }

        if (mUsersAllowingPrivateNotifications.indexOfKey(userHandle) < 0) {
            final boolean allowedByUser = 0 != Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, 0, userHandle);
            final boolean allowedByDpm = adminAllowsUnredactedNotifications(userHandle);
            final boolean allowed = allowedByUser && allowedByDpm;
            mUsersAllowingPrivateNotifications.append(userHandle, allowed);
            return allowed;
        }

        return mUsersAllowingPrivateNotifications.get(userHandle);
    }

    private boolean adminAllowsUnredactedNotifications(int userHandle) {
        if (userHandle == UserHandle.USER_ALL) {
            return true;
        }
        final int dpmFlags = mDevicePolicyManager.getKeyguardDisabledFeatures(null /* admin */,
                    userHandle);
        return (dpmFlags & DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS) == 0;
    }

    /**
     * Returns true if we're on a secure lockscreen and the user wants to hide notification data.
     * If so, notifications should be hidden.
     */
    @Override  // NotificationData.Environment
    public boolean shouldHideNotifications(int userId) {
        return isLockscreenPublicMode(userId) && !userAllowsNotificationsInPublic(userId)
                || (userId != mCurrentUserId && shouldHideNotifications(mCurrentUserId));
    }

    /**
     * Returns true if we're on a secure lockscreen and the user wants to hide notifications via
     * package-specific override.
     */
    @Override // NotificationDate.Environment
    public boolean shouldHideNotifications(String key) {
        return isLockscreenPublicMode(mCurrentUserId)
                && mNotificationData.getVisibilityOverride(key) == Notification.VISIBILITY_SECRET;
    }

    /**
     * Returns true if we're on a secure lockscreen.
     */
    @Override  // NotificationData.Environment
    public boolean isSecurelyLocked(int userId) {
        return isLockscreenPublicMode(userId);
    }

    public void onNotificationClear(StatusBarNotification notification) {
        try {
            mBarService.onNotificationClear(
                    notification.getPackageName(),
                    notification.getTag(),
                    notification.getId(),
                    notification.getUserId());
        } catch (android.os.RemoteException ex) {
            // oh well
        }
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
            int maxBefore = getMaxKeyguardNotifications(false /* recompute */);
            int maxNotifications = getMaxKeyguardNotifications(true /* recompute */);
            if (maxBefore != maxNotifications) {
                updateRowStates();
            }
        }
    }

    protected void inflateViews(Entry entry, ViewGroup parent) {
        PackageManager pmUser = getPackageManagerForUser(mContext,
                entry.notification.getUser().getIdentifier());

        final StatusBarNotification sbn = entry.notification;
        if (entry.row != null) {
            entry.reset();
            updateNotification(entry, pmUser, sbn, entry.row);
        } else {
            new RowInflaterTask().inflate(mContext, parent, entry,
                    row -> {
                        bindRow(entry, pmUser, sbn, row);
                        updateNotification(entry, pmUser, sbn, row);
                    });
        }

    }

    private void bindRow(Entry entry, PackageManager pmUser,
            StatusBarNotification sbn, ExpandableNotificationRow row) {
        row.setExpansionLogger(this, entry.notification.getKey());
        row.setGroupManager(mGroupManager);
        row.setHeadsUpManager(mHeadsUpManager);
        row.setAboveShelfChangedListener(mAboveShelfObserver);
        row.setRemoteInputController(mRemoteInputController);
        row.setOnExpandClickListener(this);
        row.setRemoteViewClickHandler(mOnClickHandler);
        row.setInflationCallback(this);
        row.setSecureStateProvider(this::isKeyguardCurrentlySecure);

        // Get the app name.
        // Note that Notification.Builder#bindHeaderAppName has similar logic
        // but since this field is used in the guts, it must be accurate.
        // Therefore we will only show the application label, or, failing that, the
        // package name. No substitutions.
        final String pkg = sbn.getPackageName();
        String appname = pkg;
        try {
            final ApplicationInfo info = pmUser.getApplicationInfo(pkg,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                            | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (info != null) {
                appname = String.valueOf(pmUser.getApplicationLabel(info));
            }
        } catch (NameNotFoundException e) {
            // Do nothing
        }
        row.setAppName(appname);
        row.setOnDismissRunnable(() ->
                performRemoveNotification(row.getStatusBarNotification()));
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (ENABLE_REMOTE_INPUT) {
            row.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        }
    }

    private void updateNotification(Entry entry, PackageManager pmUser,
            StatusBarNotification sbn, ExpandableNotificationRow row) {
        row.setNeedsRedaction(needsRedaction(entry));
        boolean isLowPriority = mNotificationData.isAmbient(sbn.getKey());
        boolean isUpdate = mNotificationData.get(entry.key) != null;
        boolean wasLowPriority = row.isLowPriority();
        row.setIsLowPriority(isLowPriority);
        row.setLowPriorityStateUpdated(isUpdate && (wasLowPriority != isLowPriority));
        // bind the click event to the content area
        mNotificationClicker.register(row, sbn);

        // Extract target SDK version.
        try {
            ApplicationInfo info = pmUser.getApplicationInfo(sbn.getPackageName(), 0);
            entry.targetSdk = info.targetSdkVersion;
        } catch (NameNotFoundException ex) {
            Log.e(TAG, "Failed looking up ApplicationInfo for " + sbn.getPackageName(), ex);
        }
        row.setLegacy(entry.targetSdk >= Build.VERSION_CODES.GINGERBREAD
                && entry.targetSdk < Build.VERSION_CODES.LOLLIPOP);
        entry.setIconTag(R.id.icon_is_pre_L, entry.targetSdk < Build.VERSION_CODES.LOLLIPOP);
        entry.autoRedacted = entry.notification.getNotification().publicVersion == null;

        entry.row = row;
        entry.row.setOnActivatedListener(this);

        boolean useIncreasedCollapsedHeight = mMessagingUtil.isImportantMessaging(sbn,
                mNotificationData.getImportance(sbn.getKey()));
        boolean useIncreasedHeadsUp = useIncreasedCollapsedHeight && mPanelExpanded;
        row.setUseIncreasedCollapsedHeight(useIncreasedCollapsedHeight);
        row.setUseIncreasedHeadsUpHeight(useIncreasedHeadsUp);
        row.updateNotification(entry);
    }

    /**
     * Adds RemoteInput actions from the WearableExtender; to be removed once more apps support this
     * via first-class API.
     *
     * TODO: Remove once enough apps specify remote inputs on their own.
     */
    private void processForRemoteInput(Notification n) {
        if (!ENABLE_REMOTE_INPUT) return;

        if (n.extras != null && n.extras.containsKey("android.wearable.EXTENSIONS") &&
                (n.actions == null || n.actions.length == 0)) {
            Notification.Action viableAction = null;
            Notification.WearableExtender we = new Notification.WearableExtender(n);

            List<Notification.Action> actions = we.getActions();
            final int numActions = actions.size();

            for (int i = 0; i < numActions; i++) {
                Notification.Action action = actions.get(i);
                if (action == null) {
                    continue;
                }
                RemoteInput[] remoteInputs = action.getRemoteInputs();
                if (remoteInputs == null) {
                    continue;
                }
                for (RemoteInput ri : remoteInputs) {
                    if (ri.getAllowFreeFormInput()) {
                        viableAction = action;
                        break;
                    }
                }
                if (viableAction != null) {
                    break;
                }
            }

            if (viableAction != null) {
                Notification.Builder rebuilder = Notification.Builder.recoverBuilder(mContext, n);
                rebuilder.setActions(viableAction);
                rebuilder.build(); // will rewrite n
            }
        }
    }

    public void startPendingIntentDismissingKeyguard(final PendingIntent intent) {
        if (!isDeviceProvisioned()) return;

        final boolean keyguardShowing = mStatusBarKeyguardViewManager.isShowing();
        final boolean afterKeyguardGone = intent.isActivity()
                && PreviewInflater.wouldLaunchResolverActivity(mContext, intent.getIntent(),
                mCurrentUserId);
        dismissKeyguardThenExecute(new OnDismissAction() {
            @Override
            public boolean onDismiss() {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            // The intent we are sending is for the application, which
                            // won't have permission to immediately start an activity after
                            // the user switches to home.  We know it is safe to do at this
                            // point, so make sure new activity switches are now allowed.
                            ActivityManager.getService().resumeAppSwitches();
                        } catch (RemoteException e) {
                        }
                        try {
                            intent.send(null, 0, null, null, null, null, getActivityOptions());
                        } catch (PendingIntent.CanceledException e) {
                            // the stack trace isn't very helpful here.
                            // Just log the exception message.
                            Log.w(TAG, "Sending intent failed: " + e);

                            // TODO: Dismiss Keyguard.
                        }
                        if (intent.isActivity()) {
                            mAssistManager.hideAssist();
                        }
                    }
                }.start();

                if (!mNotificationPanel.isFullyCollapsed()) {
                    // close the shade if it was open
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                            true /* force */, true /* delayed */);
                    visibilityChanged(false);

                    return true;
                } else {
                    return false;
                }
            }
        }, afterKeyguardGone);
    }


    private final class NotificationClicker implements View.OnClickListener {

        @Override
        public void onClick(final View v) {
            if (!(v instanceof ExpandableNotificationRow)) {
                Log.e(TAG, "NotificationClicker called on a view that is not a notification row.");
                return;
            }

            wakeUpIfDozing(SystemClock.uptimeMillis(), v);

            final ExpandableNotificationRow row = (ExpandableNotificationRow) v;
            final StatusBarNotification sbn = row.getStatusBarNotification();
            if (sbn == null) {
                Log.e(TAG, "NotificationClicker called on an unclickable notification,");
                return;
            }

            // Check if the notification is displaying the menu, if so slide notification back
            if (row.getProvider() != null && row.getProvider().isMenuVisible()) {
                row.animateTranslateNotification(0);
                return;
            }

            Notification notification = sbn.getNotification();
            final PendingIntent intent = notification.contentIntent != null
                    ? notification.contentIntent
                    : notification.fullScreenIntent;
            final String notificationKey = sbn.getKey();

            // Mark notification for one frame.
            row.setJustClicked(true);
            DejankUtils.postAfterTraversal(new Runnable() {
                @Override
                public void run() {
                    row.setJustClicked(false);
                }
            });

            final boolean afterKeyguardGone = intent.isActivity()
                    && PreviewInflater.wouldLaunchResolverActivity(mContext, intent.getIntent(),
                            mCurrentUserId);
            dismissKeyguardThenExecute(new OnDismissAction() {
                @Override
                public boolean onDismiss() {
                    if (mHeadsUpManager != null && mHeadsUpManager.isHeadsUp(notificationKey)) {
                        // Release the HUN notification to the shade.

                        if (isPanelFullyCollapsed()) {
                            HeadsUpManager.setIsClickedNotification(row, true);
                        }
                        //
                        // In most cases, when FLAG_AUTO_CANCEL is set, the notification will
                        // become canceled shortly by NoMan, but we can't assume that.
                        mHeadsUpManager.releaseImmediately(notificationKey);
                    }
                    StatusBarNotification parentToCancel = null;
                    if (shouldAutoCancel(sbn) && mGroupManager.isOnlyChildInGroup(sbn)) {
                        StatusBarNotification summarySbn = mGroupManager.getLogicalGroupSummary(sbn)
                                        .getStatusBarNotification();
                        if (shouldAutoCancel(summarySbn)) {
                            parentToCancel = summarySbn;
                        }
                    }
                    final StatusBarNotification parentToCancelFinal = parentToCancel;
                    final Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // The intent we are sending is for the application, which
                                // won't have permission to immediately start an activity after
                                // the user switches to home.  We know it is safe to do at this
                                // point, so make sure new activity switches are now allowed.
                                ActivityManager.getService().resumeAppSwitches();
                            } catch (RemoteException e) {
                            }
                            if (intent != null) {
                                // If we are launching a work activity and require to launch
                                // separate work challenge, we defer the activity action and cancel
                                // notification until work challenge is unlocked.
                                if (intent.isActivity()) {
                                    final int userId = intent.getCreatorUserHandle()
                                            .getIdentifier();
                                    if (mLockPatternUtils.isSeparateProfileChallengeEnabled(userId)
                                            && mKeyguardManager.isDeviceLocked(userId)) {
                                        // TODO(b/28935539): should allow certain activities to
                                        // bypass work challenge
                                        if (startWorkChallengeIfNecessary(userId,
                                                intent.getIntentSender(), notificationKey)) {
                                            // Show work challenge, do not run PendingIntent and
                                            // remove notification
                                            return;
                                        }
                                    }
                                }
                                try {
                                    intent.send(null, 0, null, null, null, null,
                                            getActivityOptions());
                                } catch (PendingIntent.CanceledException e) {
                                    // the stack trace isn't very helpful here.
                                    // Just log the exception message.
                                    Log.w(TAG, "Sending contentIntent failed: " + e);

                                    // TODO: Dismiss Keyguard.
                                }
                                if (intent.isActivity()) {
                                    mAssistManager.hideAssist();
                                }
                            }

                            try {
                                mBarService.onNotificationClick(notificationKey);
                            } catch (RemoteException ex) {
                                // system process is dead if we're here.
                            }
                            if (parentToCancelFinal != null) {
                                // We have to post it to the UI thread for synchronization
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Runnable removeRunnable = new Runnable() {
                                            @Override
                                            public void run() {
                                                performRemoveNotification(parentToCancelFinal);
                                            }
                                        };
                                        if (isCollapsing()) {
                                            // To avoid lags we're only performing the remove
                                            // after the shade was collapsed
                                            addPostCollapseAction(removeRunnable);
                                        } else {
                                            removeRunnable.run();
                                        }
                                    }
                                });
                            }
                        }
                    };

                    if (mStatusBarKeyguardViewManager.isShowing()
                            && mStatusBarKeyguardViewManager.isOccluded()) {
                        mStatusBarKeyguardViewManager.addAfterKeyguardGoneRunnable(runnable);
                    } else {
                        new Thread(runnable).start();
                    }

                    if (!mNotificationPanel.isFullyCollapsed()) {
                        // close the shade if it was open
                        animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                                true /* force */, true /* delayed */);
                        visibilityChanged(false);

                        return true;
                    } else {
                        return false;
                    }
                }
            }, afterKeyguardGone);
        }

        private boolean shouldAutoCancel(StatusBarNotification sbn) {
            int flags = sbn.getNotification().flags;
            if ((flags & Notification.FLAG_AUTO_CANCEL) != Notification.FLAG_AUTO_CANCEL) {
                return false;
            }
            if ((flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                return false;
            }
            return true;
        }

        public void register(ExpandableNotificationRow row, StatusBarNotification sbn) {
            Notification notification = sbn.getNotification();
            if (notification.contentIntent != null || notification.fullScreenIntent != null) {
                row.setOnClickListener(this);
            } else {
                row.setOnClickListener(null);
            }
        }
    }

    public Bundle getActivityOptions() {
        // Anything launched from the notification shade should always go into the
        // fullscreen stack.
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchStackId(StackId.FULLSCREEN_WORKSPACE_STACK_ID);
        return options.toBundle();
    }

    protected void visibilityChanged(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            if (!visible) {
                closeAndSaveGuts(true /* removeLeavebehind */, true /* force */,
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

    /**
     * Cancel this notification and tell the StatusBarManagerService / NotificationManagerService
     * about the failure.
     *
     * WARNING: this will call back into us.  Don't hold any locks.
     */
    void handleNotificationError(StatusBarNotification n, String message) {
        removeNotification(n.getKey(), null);
        try {
            mBarService.onNotificationError(n.getPackageName(), n.getTag(), n.getId(), n.getUid(),
                    n.getInitialPid(), message, n.getUserId());
        } catch (RemoteException ex) {
            // The end is nigh.
        }
    }

    protected StatusBarNotification removeNotificationViews(String key, RankingMap ranking) {
        NotificationData.Entry entry = mNotificationData.remove(key, ranking);
        if (entry == null) {
            Log.w(TAG, "removeNotification for unknown key: " + key);
            return null;
        }
        updateNotifications();
        Dependency.get(LeakDetector.class).trackGarbage(entry);
        return entry.notification;
    }

    protected NotificationData.Entry createNotificationViews(StatusBarNotification sbn)
            throws InflationException {
        if (DEBUG) {
            Log.d(TAG, "createNotificationViews(notification=" + sbn);
        }
        NotificationData.Entry entry = new NotificationData.Entry(sbn);
        Dependency.get(LeakDetector.class).trackInstance(entry);
        entry.createIcons(mContext, sbn);
        // Construct the expanded view.
        inflateViews(entry, mStackScroller);
        return entry;
    }

    protected void addNotificationViews(Entry entry) {
        if (entry == null) {
            return;
        }
        // Add the expanded view and icon.
        mNotificationData.add(entry);
        updateNotifications();
    }

    /**
     * Updates expanded, dimmed and locked states of notification rows.
     */
    protected void updateRowStates() {
        final int N = mStackScroller.getChildCount();

        int visibleNotifications = 0;
        boolean onKeyguard = mState == StatusBarState.KEYGUARD;
        int maxNotifications = -1;
        if (onKeyguard) {
            maxNotifications = getMaxKeyguardNotifications(true /* recompute */);
        }
        mStackScroller.setMaxDisplayedNotifications(maxNotifications);
        Stack<ExpandableNotificationRow> stack = new Stack<>();
        for (int i = N - 1; i >= 0; i--) {
            View child = mStackScroller.getChildAt(i);
            if (!(child instanceof ExpandableNotificationRow)) {
                continue;
            }
            stack.push((ExpandableNotificationRow) child);
        }
        while(!stack.isEmpty()) {
            ExpandableNotificationRow row = stack.pop();
            NotificationData.Entry entry = row.getEntry();
            boolean isChildNotification =
                    mGroupManager.isChildInGroupWithSummary(entry.notification);

            row.setOnKeyguard(onKeyguard);

            if (!onKeyguard) {
                // If mAlwaysExpandNonGroupedNotification is false, then only expand the
                // very first notification and if it's not a child of grouped notifications.
                row.setSystemExpanded(mAlwaysExpandNonGroupedNotification
                        || (visibleNotifications == 0 && !isChildNotification
                        && !row.isLowPriority()));
            }

            entry.row.setShowAmbient(isDozing());
            int userId = entry.notification.getUserId();
            boolean suppressedSummary = mGroupManager.isSummaryOfSuppressedGroup(
                    entry.notification) && !entry.row.isRemoved();
            boolean showOnKeyguard = shouldShowOnKeyguard(entry.notification);
            if (suppressedSummary
                    || (isLockscreenPublicMode(userId) && !mShowLockscreenNotifications)
                    || (onKeyguard && !showOnKeyguard)) {
                entry.row.setVisibility(View.GONE);
            } else {
                boolean wasGone = entry.row.getVisibility() == View.GONE;
                if (wasGone) {
                    entry.row.setVisibility(View.VISIBLE);
                }
                if (!isChildNotification && !entry.row.isRemoved()) {
                    if (wasGone) {
                        // notify the scroller of a child addition
                        mStackScroller.generateAddAnimation(entry.row,
                                !showOnKeyguard /* fromMoreCard */);
                    }
                    visibleNotifications++;
                }
            }
            if (row.isSummaryWithChildren()) {
                List<ExpandableNotificationRow> notificationChildren =
                        row.getNotificationChildren();
                int size = notificationChildren.size();
                for (int i = size - 1; i >= 0; i--) {
                    stack.push(notificationChildren.get(i));
                }
            }
        }
        mNotificationPanel.setNoVisibleNotifications(visibleNotifications == 0);

        // The following views will be moved to the end of mStackScroller. This counter represents
        // the offset from the last child. Initialized to 1 for the very last position. It is post-
        // incremented in the following "changeViewPosition" calls so that its value is correct for
        // subsequent calls.
        int offsetFromEnd = 1;
        if (mDismissView != null) {
            mStackScroller.changeViewPosition(mDismissView,
                    mStackScroller.getChildCount() - offsetFromEnd++);
        }

        mStackScroller.changeViewPosition(mEmptyShadeView,
                mStackScroller.getChildCount() - offsetFromEnd++);

        // No post-increment for this call because it is the last one. Make sure to add one if
        // another "changeViewPosition" call is ever added.
        mStackScroller.changeViewPosition(mNotificationShelf,
                mStackScroller.getChildCount() - offsetFromEnd);

        // Scrim opacity varies based on notification count
        mScrimController.setNotificationCount(mStackScroller.getNotGoneChildCount());

        if (onKeyguard) {
            hideWeatherPanelIfNecessary(visibleNotifications, maxNotifications);
        }
    }

    private void hideWeatherPanelIfNecessary(int visibleNotifications, int maxKeyguardNotifications) {
        final ContentResolver resolver = mContext.getContentResolver();
        int notifications = visibleNotifications;
        if (mNotificationShelf.getChildCount() > 0) {
            notifications += 1;
        }
        maxKeyguardNotifications = getMaxKeyguardNotifications(true);
    }

    public boolean shouldShowOnKeyguard(StatusBarNotification sbn) {
        return mShowLockscreenNotifications && !mNotificationData.isAmbient(sbn.getKey());
    }

    // extended in StatusBar
    protected void setShowLockscreenNotifications(boolean show) {
        mShowLockscreenNotifications = show;
    }

    protected void setLockScreenAllowRemoteInput(boolean allowLockscreenRemoteInput) {
        mAllowLockscreenRemoteInput = allowLockscreenRemoteInput;
    }

    private void updateLockscreenNotificationSetting() {
        final boolean show = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS,
                1,
                mCurrentUserId) != 0;
        final int dpmFlags = mDevicePolicyManager.getKeyguardDisabledFeatures(
                null /* admin */, mCurrentUserId);
        final boolean allowedByDpm = (dpmFlags
                & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS) == 0;

        setShowLockscreenNotifications(show && allowedByDpm);

        if (ENABLE_LOCK_SCREEN_ALLOW_REMOTE_INPUT) {
            final boolean remoteInput = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_ALLOW_REMOTE_INPUT,
                    0,
                    mCurrentUserId) != 0;
            final boolean remoteInputDpm =
                    (dpmFlags & DevicePolicyManager.KEYGUARD_DISABLE_REMOTE_INPUT) == 0;

            setLockScreenAllowRemoteInput(remoteInput && remoteInputDpm);
        } else {
            setLockScreenAllowRemoteInput(false);
        }
    }

    public void updateNotification(StatusBarNotification notification, RankingMap ranking)
            throws InflationException {
        if (DEBUG) Log.d(TAG, "updateNotification(" + notification + ")");

        final String key = notification.getKey();
        abortExistingInflation(key);
        Entry entry = mNotificationData.get(key);
        if (entry == null) {
            return;
        }
        mHeadsUpEntriesToRemoveOnSwitch.remove(entry);
        mRemoteInputEntriesToRemoveOnCollapse.remove(entry);
        if (key.equals(mKeyToRemoveOnGutsClosed)) {
            mKeyToRemoveOnGutsClosed = null;
            Log.w(TAG, "Notification that was kept for guts was updated. " + key);
        }

        Notification n = notification.getNotification();
        mNotificationData.updateRanking(ranking);

        boolean updateTicker = n.tickerText != null
                && !TextUtils.equals(n.tickerText,
                entry.notification.getNotification().tickerText);

        final StatusBarNotification oldNotification = entry.notification;
        entry.notification = notification;
        mGroupManager.onEntryUpdated(entry, oldNotification);

        entry.updateIcons(mContext, notification);
        inflateViews(entry, mStackScroller);

        mForegroundServiceController.updateNotification(notification,
                mNotificationData.getImportance(key));

        boolean shouldPeek = shouldPeek(entry, notification);
        boolean alertAgain = alertAgain(entry, n);

        updateHeadsUp(key, entry, shouldPeek, alertAgain);
        updateNotifications();

        if (!notification.isClearable()) {
            // The user may have performed a dismiss action on the notification, since it's
            // not clearable we should snap it back.
            mStackScroller.snapViewIfNeeded(entry.row);
        }

        boolean isForCurrentUser = isNotificationForCurrentProfiles(notification);
        if (DEBUG) {
            // Is this for you?
            Log.d(TAG, "notification is " + (isForCurrentUser ? "" : "not ") + "for you");
        }
        // Restart the ticker if it's still running
        if (updateTicker && isForCurrentUser) {
            haltTicker();
            if (!shouldPeek) {
                tick(notification, false, false, null);
            }
        }

        setAreThereNotifications();
    }

    protected void notifyHeadsUpGoingToSleep() {
        maybeEscalateHeadsUp();
    }

    private boolean alertAgain(Entry oldEntry, Notification newNotification) {
        return oldEntry == null || !oldEntry.hasInterrupted()
                || (newNotification.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0;
    }

    protected boolean shouldPeek(Entry entry) {
        return shouldPeek(entry, entry.notification);
    }

    protected boolean shouldPeek(Entry entry, StatusBarNotification sbn) {
        if(isPackageInBlacklist(sbn.getPackageName())) {
            return false;
        }

        if ((!mUseHeadsUp || isDeviceInVrMode()) && !isDozing()) {
            if (DEBUG) Log.d(TAG, "No peeking: no huns or vr mode");
            return false;
        }

        if (mNotificationData.shouldFilterOut(sbn)) {
            if (DEBUG) Log.d(TAG, "No peeking: filtered notification: " + sbn.getKey());
            return false;
        }

        boolean inUse = mPowerManager.isScreenOn() && !mSystemServicesProxy.isDreaming();

        if (!inUse && !isDozing()) {
            if (DEBUG) {
                Log.d(TAG, "No peeking: not in use: " + sbn.getKey());
            }
            return false;
        }

        if (!isDozing() && mNotificationData.shouldSuppressScreenOn(sbn.getKey())) {
            if (DEBUG) Log.d(TAG, "No peeking: suppressed by DND: " + sbn.getKey());
            return false;
        }

        if (isDozing() && mNotificationData.shouldSuppressScreenOff(sbn.getKey())) {
            if (DEBUG) Log.d(TAG, "No peeking: suppressed by DND: " + sbn.getKey());
            return false;
        }

        if (entry.hasJustLaunchedFullScreenIntent()) {
            if (DEBUG) Log.d(TAG, "No peeking: recent fullscreen: " + sbn.getKey());
            return false;
        }

        if (isSnoozedPackage(sbn)) {
            if (DEBUG) Log.d(TAG, "No peeking: snoozed package: " + sbn.getKey());
            return false;
        }

        // Allow peeking for DEFAULT notifications only if we're on Ambient Display.
        int importanceLevel = isDozing() ? NotificationManager.IMPORTANCE_DEFAULT
                : NotificationManager.IMPORTANCE_HIGH;
        if (mNotificationData.getImportance(sbn.getKey()) < importanceLevel) {
            if (DEBUG) Log.d(TAG, "No peeking: unimportant notification: " + sbn.getKey());
            return false;
        }

        if (sbn.getNotification().fullScreenIntent != null) {
            if (mAccessibilityManager.isTouchExplorationEnabled()) {
                if (DEBUG) Log.d(TAG, "No peeking: accessible fullscreen: " + sbn.getKey());
                return false;
            } else if (mDozing) {
                // We never want heads up when we are dozing.
                return false;
            } else {
                // we only allow head-up on the lockscreen if it doesn't have a fullscreen intent
                return !mStatusBarKeyguardViewManager.isShowing()
                        || mStatusBarKeyguardViewManager.isOccluded();
            }
        }

        // Don't peek notifications that are suppressed due to group alert behavior
        if (sbn.isGroup() && sbn.getNotification().suppressAlertingDueToGrouping()) {
            if (DEBUG) Log.d(TAG, "No peeking: suppressed due to group alert behavior");
            return false;
        }

        return true;
    }

    private boolean isPackageInBlacklist(String packageName) {
        return mBlacklist.contains(packageName);
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

    /**
     * @return Whether the security bouncer from Keyguard is showing.
     */
    public boolean isBouncerShowing() {
        return mBouncerShowing;
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

    @Override
    public void logNotificationExpansion(String key, boolean userAction, boolean expanded) {
        mUiOffloadThread.submit(() -> {
            try {
                mBarService.onNotificationExpansionChanged(key, userAction, expanded);
            } catch (RemoteException e) {
                // Ignore.
            }
        });
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

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case SCREEN_BRIGHTNESS_MODE:
                mAutomaticBrightness = newValue != null && Integer.parseInt(newValue)
                        == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
                break;
            case STATUS_BAR_BRIGHTNESS_CONTROL:
                mBrightnessControl = newValue != null && Integer.parseInt(newValue) == 1;
                break;
            case LOCKSCREEN_MEDIA_METADATA:
                mShowMediaMetadata = newValue == null || Integer.parseInt(newValue) == 1;
                break;
            default:
                break;
        }
    }
    // End Extra BaseStatusBarMethods.

    private final Runnable mAutoDim = () -> {
        if (mNavigationBar != null) {
            mNavigationBar.getBarTransitions().setAutoDim(true);
        }
    };

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

   protected void addAppCircleSidebar() {
        if (mAppCircleSidebar == null) {
            mAppCircleSidebar = (AppCircleSidebar) View.inflate(mContext, R.layout.app_circle_sidebar, null);
            mWindowManager.addView(mAppCircleSidebar, getAppCircleSidebarLayoutParams());
        }
    }

    protected void removeAppCircleSidebar() {
         if (mAppCircleSidebar != null) {
             mWindowManager.removeView(mAppCircleSidebar);
         }
     }
 
    protected WindowManager.LayoutParams getAppCircleSidebarLayoutParams() {
         int maxWidth =
                 mContext.getResources().getDimensionPixelSize(R.dimen.app_sidebar_trigger_width);
 
         WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                 maxWidth,
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
         lp.gravity = Gravity.TOP | Gravity.RIGHT;
         lp.setTitle("AppCircleSidebar");
 
         return lp;
    }


     public void updatePieControls(boolean reset) {
         ContentResolver resolver = mContext.getContentResolver();
 
         if (reset) {
             Settings.Secure.putIntForUser(resolver,
                     Settings.Secure.PIE_GRAVITY, 0, UserHandle.USER_CURRENT);
             toggleOrientationListener(false);
         } else {
             getOrientationListener();
             toggleOrientationListener(true);
         }
 
         if (mPieController == null) {
             mPieController = PieController.getInstance();
             mPieController.init(mContext, mWindowManager, this);
         }
 
         int gravity = Settings.Secure.getInt(resolver,
                 Settings.Secure.PIE_GRAVITY, 0);
         mPieController.resetPie(!reset, gravity);
     }
 
     public void toggleOrientationListener(boolean enable) {
         if (mOrientationListener == null) {
             if (!enable) {
                 // Do nothing if listener has already dropped
                 return;
             } else {
                 boolean shouldEnable = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                         Settings.Secure.PIE_STATE, 0, UserHandle.USER_CURRENT) == 1;
                 if (shouldEnable) {
                     // Re-init Orientation listener for later action
                     getOrientationListener();
                 } else {
                     return;
                 }
             }
         }
 
         if (enable && mPowerManager.isScreenOn()) {
             mOrientationListener.enable();
         } else {
             mOrientationListener.disable();
             // if it has been disabled, then don't leave it to
             // prevent called from PhoneWindowManager
             mOrientationListener = null;
         }
     }

     private void getOrientationListener() {
         if (mOrientationListener == null)
             mOrientationListener = new OrientationEventListener(mContext,
                     SensorManager.SENSOR_DELAY_NORMAL) {
                 @Override
                 public void onOrientationChanged(int orientation) {
                     int rotation = mDisplay.getRotation();
                     if (rotation != mOrientation) {
                         if (mPieController != null) mPieController.detachPie();
                         mOrientation = rotation;
                     }
                 }
            };
     }

    public static void updatePreferences(Context mContext) {
        RecentsActivity.updatePreferences(mContext);
        if (mNotificationData == null)
            return;

        for (Entry entry : mNotificationData.getActiveNotifications()) {
            NotificationBackgroundView mBackgroundNormal = (NotificationBackgroundView) getObjectField(entry.row, "mBackgroundNormal");
            NotificationBackgroundView mBackgroundDimmed = (NotificationBackgroundView) getObjectField(entry.row, "mBackgroundDimmed");

            mBackgroundNormal.postInvalidate();
            mBackgroundDimmed.postInvalidate();
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    /**
     * Look up a field in a class and set it to accessible. The result is cached.
     * If the field was not found, a {@link NoSuchFieldError} will be thrown.
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        StringBuilder sb = new StringBuilder(clazz.getName());
        sb.append('#');
        sb.append(fieldName);
        String fullFieldName = sb.toString();

        if (fieldCache.containsKey(fullFieldName)) {
            Field field = fieldCache.get(fullFieldName);
            if (field == null)
                throw new NoSuchFieldError(fullFieldName);
            return field;
        }

        try {
            Field field = findFieldRecursiveImpl(clazz, fieldName);
            field.setAccessible(true);
            fieldCache.put(fullFieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            fieldCache.put(fullFieldName, null);
            throw new NoSuchFieldError(fullFieldName);
        }
    }

    private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            while (true) {
                clazz = clazz.getSuperclass();
                if (clazz == null || clazz.equals(Object.class))
                    break;

                try {
                    return clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw e;
        }
    }

   public void trytoinflateclusters() {
        try {
             SignalClusterView signalClusterView = reinflateSignalCluster(mStatusBarView);
             inflateSignalClusters();
        } catch (Exception e) {
        }
   }
}
