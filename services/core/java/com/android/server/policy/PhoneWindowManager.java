/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.Manifest.permission.INTERNAL_SYSTEM_WINDOW;
import static android.Manifest.permission.SYSTEM_ALERT_WINDOW;
import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.app.AppOpsManager.OP_SYSTEM_ALERT_WINDOW;
import static android.app.AppOpsManager.OP_TOAST_WINDOW;
import static android.content.Context.CONTEXT_RESTRICTED;
import static android.content.Context.DISPLAY_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.content.res.Configuration.EMPTY;
import static android.content.res.Configuration.UI_MODE_TYPE_CAR;
import static android.content.res.Configuration.UI_MODE_TYPE_MASK;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.O;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.STATE_OFF;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.DOCKED_TOP;
import static android.view.WindowManager.INPUT_CONSUMER_NAVIGATION;
import static android.view.WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.FIRST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW;
import static android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_ATTACHED_IN_DECOR;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.LAST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.LAST_SYSTEM_WINDOW;
import static android.view.WindowManager.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_ACQUIRES_SLEEP_TOKEN;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_STATUS_BAR_VISIBLE_TRANSPARENT;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NAV_HIDE_FORCED;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_STATUS_HIDE_FORCED;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_WAS_NOT_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;
import static android.view.WindowManager.LayoutParams.TYPE_BOOT_PROGRESS;
import static android.view.WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.LayoutParams.TYPE_DREAM;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_CONSUMER;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_POINTER;
import static android.view.WindowManager.LayoutParams.TYPE_PRESENTATION;
import static android.view.WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION;
import static android.view.WindowManager.LayoutParams.TYPE_QS_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SCREENSHOT;
import static android.view.WindowManager.LayoutParams.TYPE_SEARCH_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_SLIM_RECENTS;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION;
import static android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION_STARTING;
import static android.view.WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static android.view.WindowManager.LayoutParams.isSystemAlertWindowType;
import static android.view.WindowManager.SCREEN_RECORD_LOW_QUALITY;
import static android.view.WindowManager.SCREEN_RECORD_MID_QUALITY;
import static android.view.WindowManager.SCREEN_RECORD_HIGH_QUALITY;
import static android.view.WindowManager.TAKE_SCREENSHOT_FULLSCREEN;
import static android.view.WindowManager.TAKE_SCREENSHOT_SELECTED_REGION;
import static android.view.WindowManagerGlobal.ADD_OKAY;
import static android.view.WindowManagerGlobal.ADD_PERMISSION_DENIED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVER_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_UNCOVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_CLOSED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_OPEN;

import android.annotation.Nullable;
import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IUiModeManager;
import android.app.PendingIntent;
<<<<<<< HEAD
import android.app.KeyguardManager;
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiPlaybackClient.OneTouchPlayCallback;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerInternal;
import android.hardware.power.V1_0.PowerHint;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IAudioService;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.pocket.IPocketCallback;
import android.pocket.PocketManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.dreams.DreamManagerInternal;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.service.vr.IPersistentVrStateCallbacks;
import android.service.gesture.EdgeGestureManager;
import android.speech.RecognizerIntent;
import android.telecom.TelecomManager;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableBoolean;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerInternal;
import android.view.WindowManagerInternal.AppTransitionListener;
import android.view.WindowManagerPolicy;
import android.view.WindowManagerPolicyControl;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.autofill.AutofillManagerInternal;
import android.view.inputmethod.InputMethodManagerInternal;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IShortcutService;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.gesture.EdgeGesturePosition;
import com.android.internal.util.gesture.EdgeServiceConstants;
import com.android.internal.util.ScreenShapeHelper;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.widget.PointerLocationView;
import com.android.server.GestureLauncherService;
import com.android.server.LocalServices;
import com.android.server.SystemServiceManager;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.policy.keyguard.KeyguardServiceDelegate.DrawnListener;
import com.android.server.policy.keyguard.KeyguardStateMonitor.StateCallback;
import com.android.server.policy.pocket.PocketLock;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.vr.VrManagerInternal;
import com.android.server.wm.AppTransition;

<<<<<<< HEAD
import lineageos.providers.LineageSettings;
=======
import lineageos.hardware.LineageHardwareManager;
import lineageos.providers.LineageSettings;

import org.lineageos.internal.util.ActionUtils;
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
import static org.lineageos.internal.util.DeviceKeysConstants.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.lineageos.internal.buttons.LineageButtons;

import dalvik.system.PathClassLoader;

/**
 * WindowManagerPolicy implementation for the Android phone UI.  This
 * introduces a new method suffix, Lp, for an internal lock of the
 * PhoneWindowManager.  This is used to protect some internal state, and
 * can be acquired with either the Lw and Li lock held, so has the restrictions
 * of both of those when held.
 */
public class PhoneWindowManager implements WindowManagerPolicy {
    static final String TAG = "WindowManager";
    static final boolean DEBUG = false;
    static final boolean localLOGV = false;
    static final boolean DEBUG_INPUT = false;
    static final boolean DEBUG_KEYGUARD = false;
    static final boolean DEBUG_LAYOUT = false;
    static final boolean DEBUG_SPLASH_SCREEN = false;
    static final boolean DEBUG_WAKEUP = false;
    static final boolean SHOW_SPLASH_SCREENS = true;

    // Whether to allow dock apps with METADATA_DOCK_HOME to temporarily take over the Home key.
    // No longer recommended for desk docks;
    static final boolean ENABLE_DESK_DOCK_HOME_CAPTURE = false;

    // Whether to allow devices placed in vr headset viewers to have an alternative Home intent.
    static final boolean ENABLE_VR_HEADSET_HOME_CAPTURE = true;

    static final boolean ALTERNATE_CAR_MODE_NAV_SIZE = false;

    static final int SHORT_PRESS_POWER_NOTHING = 0;
    static final int SHORT_PRESS_POWER_GO_TO_SLEEP = 1;
    static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP = 2;
    static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP_AND_GO_HOME = 3;
    static final int SHORT_PRESS_POWER_GO_HOME = 4;
    static final int SHORT_PRESS_POWER_CLOSE_IME_OR_GO_HOME = 5;

    static final int LONG_PRESS_POWER_NOTHING = 0;
    static final int LONG_PRESS_POWER_GLOBAL_ACTIONS = 1;
    static final int LONG_PRESS_POWER_SHUT_OFF = 2;
    static final int LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM = 3;
    static final int LONG_PRESS_POWER_TORCH = 4;
<<<<<<< HEAD
    static final int LONG_PRESS_POWER_HIDE_POCKET_LOCK = 5;
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017

    static final int LONG_PRESS_BACK_NOTHING = 0;
    static final int LONG_PRESS_BACK_GO_TO_VOICE_ASSIST = 1;

    static final int MULTI_PRESS_POWER_NOTHING = 0;
    static final int MULTI_PRESS_POWER_THEATER_MODE = 1;
    static final int MULTI_PRESS_POWER_BRIGHTNESS_BOOST = 2;

    // Number of presses needed before we induce panic press behavior on the back button
    static final int PANIC_PRESS_BACK_COUNT = 4;
    static final int PANIC_PRESS_BACK_NOTHING = 0;
    static final int PANIC_PRESS_BACK_HOME = 1;

    // These need to match the documentation/constant in
    // core/res/res/values/config.xml
    static final int LONG_PRESS_HOME_NOTHING = 0;
    static final int LONG_PRESS_HOME_ALL_APPS = 1;
    static final int LONG_PRESS_HOME_ASSIST = 2;
    static final int LAST_LONG_PRESS_HOME_BEHAVIOR = LONG_PRESS_HOME_ASSIST;

    static final int DOUBLE_TAP_HOME_NOTHING = 0;
    static final int DOUBLE_TAP_HOME_RECENT_SYSTEM_UI = 1;

    static final int SHORT_PRESS_WINDOW_NOTHING = 0;
    static final int SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE = 1;

    static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP = 0;
    static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME = 1;

    static final int PENDING_KEY_NULL = -1;

    // Controls navigation bar opacity depending on which workspace stacks are currently
    // visible.
    // Nav bar is always opaque when either the freeform stack or docked stack is visible.
    static final int NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED = 0;
    // Nav bar is always translucent when the freeform stack is visible, otherwise always opaque.
    static final int NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE = 1;

    static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
    static public final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    static public final String SYSTEM_DIALOG_REASON_ASSIST = "assist";
    static public final String SYSTEM_DIALOG_REASON_SCREENSHOT = "screenshot";

    /**
     * These are the system UI flags that, when changing, can cause the layout
     * of the screen to change.
     */
    static final int SYSTEM_UI_CHANGING_LAYOUT =
              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.STATUS_BAR_TRANSLUCENT
            | View.NAVIGATION_BAR_TRANSLUCENT
            | View.STATUS_BAR_TRANSPARENT
            | View.NAVIGATION_BAR_TRANSPARENT;

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    // The panic gesture may become active only after the keyguard is dismissed and the immersive
    // app shows again. If that doesn't happen for 30s we drop the gesture.
    private static final long PANIC_GESTURE_EXPIRATION = 30000;

    private static final String SYSUI_PACKAGE = "com.android.systemui";
    private static final String SYSUI_SCREENSHOT_SERVICE =
            "com.android.systemui.screenshot.TakeScreenshotService";
    private static final String SYSUI_SCREENSHOT_ERROR_RECEIVER =
            "com.android.systemui.screenshot.ScreenshotServiceErrorReceiver";

    private static final String ACTION_TORCH_OFF =
            "com.android.server.policy.PhoneWindowManager.ACTION_TORCH_OFF";

    /**
     * Keyguard stuff
     */
    private boolean mKeyguardDrawnOnce;

    /* Table of Application Launch keys.  Maps from key codes to intent categories.
     *
     * These are special keys that are used to launch particular kinds of applications,
     * such as a web browser.  HID defines nearly a hundred of them in the Consumer (0x0C)
     * usage page.  We don't support quite that many yet...
     */
    static SparseArray<String> sApplicationLaunchKeyCategories;
    static {
        sApplicationLaunchKeyCategories = new SparseArray<String>();
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_EXPLORER, Intent.CATEGORY_APP_BROWSER);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_ENVELOPE, Intent.CATEGORY_APP_EMAIL);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CONTACTS, Intent.CATEGORY_APP_CONTACTS);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CALENDAR, Intent.CATEGORY_APP_CALENDAR);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_MUSIC, Intent.CATEGORY_APP_MUSIC);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CALCULATOR, Intent.CATEGORY_APP_CALCULATOR);
    }

    /** Amount of time (in milliseconds) to wait for windows drawn before powering on. */
    static final int WAITING_FOR_DRAWN_TIMEOUT = 1000;

    /** Amount of time (in milliseconds) a toast window can be shown. */
    public static final int TOAST_WINDOW_TIMEOUT = 3500; // 3.5 seconds

    /**
     * Lock protecting internal state.  Must not call out into window
     * manager with lock held.  (This lock will be acquired in places
     * where the window manager is calling in with its own lock held.)
     */
    private final Object mLock = new Object();

    Context mContext;
    IWindowManager mWindowManager;
    WindowManagerFuncs mWindowManagerFuncs;
    WindowManagerInternal mWindowManagerInternal;
    PowerManager mPowerManager;
    ActivityManagerInternal mActivityManagerInternal;
    AutofillManagerInternal mAutofillManagerInternal;
    InputManagerInternal mInputManagerInternal;
    InputMethodManagerInternal mInputMethodManagerInternal;
    DreamManagerInternal mDreamManagerInternal;
    PowerManagerInternal mPowerManagerInternal;
    IStatusBarService mStatusBarService;
    StatusBarManagerInternal mStatusBarManagerInternal;
    boolean mPreloadedRecentApps;
    final Object mServiceAquireLock = new Object();
    Vibrator mVibrator; // Vibrator for giving feedback of orientation changes
    SearchManager mSearchManager;
    AccessibilityManager mAccessibilityManager;
    BurnInProtectionHelper mBurnInProtectionHelper;
    AppOpsManager mAppOpsManager;
    AlarmManager mAlarmManager;
<<<<<<< HEAD
    ANBIHandler mANBIHandler;

    private boolean mANBIEnabled;

=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    private boolean mHasFeatureWatch;
    private boolean mHasFeatureLeanback;

    // Assigned on main thread, accessed on UI thread
    volatile VrManagerInternal mVrManagerInternal;

    // Vibrator pattern for haptic feedback of a long press.
    long[] mLongPressVibePattern;

    // Vibrator pattern for a short vibration when tapping on a day/month/year date of a Calendar.
    long[] mCalendarDateVibePattern;

    // Vibrator pattern for haptic feedback during boot when safe mode is enabled.
    long[] mSafeModeEnabledVibePattern;

    /** If true, hitting shift & menu will broadcast Intent.ACTION_BUG_REPORT */
    boolean mEnableShiftMenuBugReports = false;

    /** Controller that supports enabling an AccessibilityService by holding down the volume keys */
    private AccessibilityShortcutController mAccessibilityShortcutController;

    boolean mSafeMode;
    WindowState mStatusBar = null;
    int mStatusBarHeight;
    WindowState mNavigationBar = null;
    // User defined bar visibility, regardless of factory configuration
    boolean mNavbarVisible = false;
    boolean mNavigationBarCanMove = false; // can the navigation bar ever move to the side?
    int mNavigationBarPosition = NAV_BAR_BOTTOM;
    int[] mNavigationBarHeightForRotationDefault = new int[4];
    int[] mNavigationBarWidthForRotationDefault = new int[4];
    int[] mNavigationBarHeightForRotationInCarMode = new int[4];
    int[] mNavigationBarWidthForRotationInCarMode = new int[4];

    private LongSparseArray<IShortcutService> mShortcutKeyServices = new LongSparseArray<>();

    // Whether to allow dock apps with METADATA_DOCK_HOME to temporarily take over the Home key.
    // This is for car dock and this is updated from resource.
    private boolean mEnableCarDockHomeCapture = true;

    boolean mBootMessageNeedsHiding;
    KeyguardServiceDelegate mKeyguardDelegate;
    private boolean mKeyguardBound;
    final Runnable mWindowManagerDrawCallback = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_WAKEUP) Slog.i(TAG, "All windows ready for display!");
            mHandler.sendEmptyMessage(MSG_WINDOW_MANAGER_DRAWN_COMPLETE);
        }
    };
    final DrawnListener mKeyguardDrawnCallback = new DrawnListener() {
        @Override
        public void onDrawn() {
            if (DEBUG_WAKEUP) Slog.d(TAG, "mKeyguardDelegate.ShowListener.onDrawn.");
            mHandler.sendEmptyMessage(MSG_KEYGUARD_DRAWN_COMPLETE);
        }
    };

    GlobalActions mGlobalActions;
    Handler mHandler;
    WindowState mLastInputMethodWindow = null;
    WindowState mLastInputMethodTargetWindow = null;

    // FIXME This state is shared between the input reader and handler thread.
    // Technically it's broken and buggy but it has been like this for many years
    // and we have not yet seen any problems.  Someday we'll rewrite this logic
    // so that only one thread is involved in handling input policy.  Unfortunately
    // it's on a critical path for power management so we can't just post the work to the
    // handler thread.  We'll need to resolve this someday by teaching the input dispatcher
    // to hold wakelocks during dispatch and eliminating the critical path.
    volatile boolean mPowerKeyHandled;
    volatile boolean mBackKeyHandled;
    volatile boolean mBeganFromNonInteractive;
    volatile int mPowerKeyPressCounter;
    volatile int mBackKeyPressCounter;
    volatile boolean mEndCallKeyHandled;
    volatile boolean mCameraGestureTriggeredDuringGoingToSleep;
    volatile boolean mGoingToSleep;
    volatile boolean mRequestedOrGoingToSleep;
    volatile boolean mRecentsVisible;
    volatile boolean mPictureInPictureVisible;
    // Written by vr manager thread, only read in this class.
    volatile private boolean mPersistentVrModeEnabled;
    volatile private boolean mDismissImeOnBackKeyPressed;

    // Used to hold the last user key used to wake the device.  This helps us prevent up events
    // from being passed to the foregrounded app without a corresponding down event
    volatile int mPendingWakeKey = PENDING_KEY_NULL;

    int mRecentAppsHeldModifiers;
    boolean mLanguageSwitchKeyPressed;

    int mLidState = LID_ABSENT;
    int mCameraLensCoverState = CAMERA_LENS_COVER_ABSENT;
    boolean mHaveBuiltInKeyboard;

    boolean mSystemReady;
    boolean mSystemBooted;
    boolean mHdmiPlugged;
    HdmiControl mHdmiControl;
    IUiModeManager mUiModeManager;
    int mUiMode;
    int mDockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    int mLidOpenRotation;
    int mCarDockRotation;
    int mDeskDockRotation;
    int mUndockedHdmiRotation;
    int mDemoHdmiRotation;
    boolean mDemoHdmiRotationLock;
    int mDemoRotation;
    boolean mDemoRotationLock;

    boolean mWakeGestureEnabledSetting;
    MyWakeGestureListener mWakeGestureListener;

    // Default display does not rotate, apps that require non-default orientation will have to
    // have the orientation emulated.
    private boolean mForceDefaultOrientation = false;

    int mUserRotationMode = WindowManagerPolicy.USER_ROTATION_FREE;
    int mUserRotation = Surface.ROTATION_0;
    int mUserRotationAngles = -1;
    boolean mAccelerometerDefault;

    boolean mSupportAutoRotation;
    int mAllowAllRotations = -1;
    boolean mCarDockEnablesAccelerometer;
    boolean mDeskDockEnablesAccelerometer;
    int mLidKeyboardAccessibility;
    int mLidNavigationAccessibility;
    boolean mLidControlsScreenLock;
    boolean mLidControlsSleep;
    int mShortPressOnPowerBehavior;
    int mLongPressOnPowerBehavior;
    int mDoublePressOnPowerBehavior;
    int mTriplePressOnPowerBehavior;
    int mLongPressOnBackBehavior;
    int mPanicPressOnBackBehavior;
    int mShortPressOnSleepBehavior;
    int mShortPressWindowBehavior;
    volatile boolean mAwake;
    boolean mScreenOnEarly;
    boolean mScreenOnFully;
    ScreenOnListener mScreenOnListener;
    boolean mKeyguardDrawComplete;
    boolean mWindowManagerDrawComplete;
    boolean mOrientationSensorEnabled = false;
    int mCurrentAppOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    boolean mHasSoftInput = false;
    boolean mTranslucentDecorEnabled = true;
    boolean mUseTvRouting;

    int mDeviceHardwareKeys;

    private boolean mHandleVolumeKeysInWM;

<<<<<<< HEAD
=======
    int mDeviceHardwareKeys;

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    // Button wake control flags
    boolean mHomeWakeScreen;
    boolean mBackWakeScreen;
    boolean mMenuWakeScreen;
    boolean mAssistWakeScreen;
    boolean mAppSwitchWakeScreen;
    boolean mCameraWakeScreen;
    boolean mVolumeWakeScreen;

    // Camera button control flags and actions
    boolean mCameraLaunch;
    boolean mCameraSleepOnRelease;
    boolean mIsFocusPressed;
    boolean mIsLongPress;

    // During wakeup by volume keys, we still need to capture subsequent events
    // until the key is released. This is required since the beep sound is produced
    // post keypressed.
    boolean mVolumeDownWakeTriggered;
    boolean mVolumeUpWakeTriggered;
    boolean mVolumeMuteWakeTriggered;

    boolean mVolumeAnswerCall;

<<<<<<< HEAD
=======
    boolean mKillAppLongpressBack;
    int mBackKillTimeout;

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    int mPointerLocationMode = 0; // guarded by mLock

    // The last window we were told about in focusChanged.
    WindowState mFocusedWindow;
    IApplicationToken mFocusedApp;

    PointerLocationView mPointerLocationView;

    // The current size of the screen; really; extends into the overscan area of
    // the screen and doesn't account for any system elements like the status bar.
    int mOverscanScreenLeft, mOverscanScreenTop;
    int mOverscanScreenWidth, mOverscanScreenHeight;
    // The current visible size of the screen; really; (ir)regardless of whether the status
    // bar can be hidden but not extending into the overscan area.
    int mUnrestrictedScreenLeft, mUnrestrictedScreenTop;
    int mUnrestrictedScreenWidth, mUnrestrictedScreenHeight;
    // Like mOverscanScreen*, but allowed to move into the overscan region where appropriate.
    int mRestrictedOverscanScreenLeft, mRestrictedOverscanScreenTop;
    int mRestrictedOverscanScreenWidth, mRestrictedOverscanScreenHeight;
    // The current size of the screen; these may be different than (0,0)-(dw,dh)
    // if the status bar can't be hidden; in that case it effectively carves out
    // that area of the display from all other windows.
    int mRestrictedScreenLeft, mRestrictedScreenTop;
    int mRestrictedScreenWidth, mRestrictedScreenHeight;
    // During layout, the current screen borders accounting for any currently
    // visible system UI elements.
    int mSystemLeft, mSystemTop, mSystemRight, mSystemBottom;
    // For applications requesting stable content insets, these are them.
    int mStableLeft, mStableTop, mStableRight, mStableBottom;
    // For applications requesting stable content insets but have also set the
    // fullscreen window flag, these are the stable dimensions without the status bar.
    int mStableFullscreenLeft, mStableFullscreenTop;
    int mStableFullscreenRight, mStableFullscreenBottom;
    // For force immersive mode
    int mForceImmersiveLeft, mForceImmersiveTop;
    int mForceImmersiveRight, mForceImmersiveBottom;
    // During layout, the current screen borders with all outer decoration
    // (status bar, input method dock) accounted for.
    int mCurLeft, mCurTop, mCurRight, mCurBottom;
    // During layout, the frame in which content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.  This is usually
    // the same as mCur*, but may be larger if the screen decor has supplied
    // content insets.
    int mContentLeft, mContentTop, mContentRight, mContentBottom;
    // During layout, the frame in which voice content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.
    int mVoiceContentLeft, mVoiceContentTop, mVoiceContentRight, mVoiceContentBottom;
    // During layout, the current screen borders along which input method
    // windows are placed.
    int mDockLeft, mDockTop, mDockRight, mDockBottom;
    // During layout, the layer at which the doc window is placed.
    int mDockLayer;
    // During layout, this is the layer of the status bar.
    int mStatusBarLayer;
    int mLastSystemUiFlags;
    // Bits that we are in the process of clearing, so we want to prevent
    // them from being set by applications until everything has been updated
    // to have them clear.
    int mResettingSystemUiFlags = 0;
    // Bits that we are currently always keeping cleared.
    int mForceClearedSystemUiFlags = 0;
    int mLastFullscreenStackSysUiFlags;
    int mLastDockedStackSysUiFlags;
    final Rect mNonDockedStackBounds = new Rect();
    final Rect mDockedStackBounds = new Rect();
    final Rect mLastNonDockedStackBounds = new Rect();
    final Rect mLastDockedStackBounds = new Rect();

    // What we last reported to system UI about whether the compatibility
    // menu needs to be displayed.
    boolean mLastFocusNeedsMenu = false;
    // If nonzero, a panic gesture was performed at that time in uptime millis and is still pending.
    private long mPendingPanicGestureUptime;

    InputConsumer mInputConsumer = null;

    static final Rect mTmpParentFrame = new Rect();
    static final Rect mTmpDisplayFrame = new Rect();
    static final Rect mTmpOverscanFrame = new Rect();
    static final Rect mTmpContentFrame = new Rect();
    static final Rect mTmpVisibleFrame = new Rect();
    static final Rect mTmpDecorFrame = new Rect();
    static final Rect mTmpStableFrame = new Rect();
    static final Rect mTmpNavigationFrame = new Rect();
    static final Rect mTmpOutsetFrame = new Rect();
    private static final Rect mTmpRect = new Rect();

    WindowState mTopFullscreenOpaqueWindowState;
    WindowState mTopFullscreenOpaqueOrDimmingWindowState;
    WindowState mTopDockedOpaqueWindowState;
    WindowState mTopDockedOpaqueOrDimmingWindowState;
    boolean mTopIsFullscreen;
    boolean mForceStatusBar;
    boolean mForceStatusBarFromKeyguard;
    private boolean mForceStatusBarTransparent;
    int mNavBarOpacityMode = NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED;
    boolean mForcingShowNavBar;
    int mForcingShowNavBarLayer;

    private boolean mPendingKeyguardOccluded;
    private boolean mKeyguardOccludedChanged;

    // Pie
    boolean mPieState;

    boolean mShowingDream;
    private boolean mLastShowingDream;
    boolean mDreamingLockscreen;
    boolean mDreamingSleepTokenNeeded;
    private boolean mWindowSleepTokenNeeded;
    private boolean mLastWindowSleepTokenNeeded;

    @GuardedBy("mHandler")
    private SleepToken mWindowSleepToken;

    SleepToken mDreamingSleepToken;
    SleepToken mScreenOffSleepToken;
    volatile boolean mKeyguardOccluded;
    boolean mHomePressed;
    boolean mHomeConsumed;
    boolean mHomeDoubleTapPending;
    boolean mMenuPressed;
    boolean mAppSwitchLongPressed;
    Intent mHomeIntent;
    Intent mCarDockIntent;
    Intent mDeskDockIntent;
    Intent mVrHeadsetHomeIntent;
    boolean mSearchKeyShortcutPending;
    boolean mConsumeSearchKeyUp;
    boolean mAssistKeyLongPressed;
    boolean mPendingMetaAction;
    boolean mPendingCapsLockToggle;
    int mMetaState;
    int mInitialMetaState;
    boolean mForceShowSystemBars;

    int mDevForceNavbar = -1;

    // Tracks user-customisable behavior for certain key events
    private Action mHomeLongPressAction;
    private Action mHomeDoubleTapAction;
    private Action mMenuPressAction;
    private Action mMenuLongPressAction;
    private Action mAssistPressAction;
    private Action mAssistLongPressAction;
    private Action mAppSwitchPressAction;
    private Action mAppSwitchLongPressAction;

    // support for activating the lock screen while the screen is on
    boolean mAllowLockscreenWhenOn;
    int mLockScreenTimeout;
    boolean mLockScreenTimerActive;

    // Behavior of ENDCALL Button.  (See Settings.System.END_BUTTON_BEHAVIOR.)
    int mEndcallBehavior;

    // Behavior of POWER button while in-call and screen on.
    // (See Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR.)
    int mIncallPowerBehavior;

    // Behavior of Back button while in-call and screen on
    int mIncallBackBehavior;

    // Behavior of HOME button during an incoming call.
    // (See LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR.)
    int mRingHomeBehavior;

    // whether System Navigation Keys are enabled
    boolean mSystemNavigationKeysEnabled;

    Display mDisplay;

    private int mDisplayRotation;

    int mLandscapeRotation = 0;  // default landscape rotation
    int mSeascapeRotation = 0;   // "other" landscape rotation, 180 degrees from mLandscapeRotation
    int mPortraitRotation = 0;   // default portrait rotation
    int mUpsideDownRotation = 0; // "other" portrait rotation

    int mOverscanLeft = 0;
    int mOverscanTop = 0;
    int mOverscanRight = 0;
    int mOverscanBottom = 0;

    // Allowed theater mode wake actions
    private boolean mAllowTheaterModeWakeFromKey;
    private boolean mAllowTheaterModeWakeFromPowerKey;
    private boolean mAllowTheaterModeWakeFromMotion;
    private boolean mAllowTheaterModeWakeFromMotionWhenNotDreaming;
    private boolean mAllowTheaterModeWakeFromCameraLens;
    private boolean mAllowTheaterModeWakeFromLidSwitch;
    private boolean mAllowTheaterModeWakeFromWakeGesture;

    // Whether to support long press from power button in non-interactive mode
    private boolean mSupportLongPressPowerWhenNonInteractive;

    // Power long press action saved on key down that should happen on key up
    private int mResolvedLongPressOnPowerBehavior;

    // Whether to go to sleep entering theater mode from power button
    private boolean mGoToSleepOnButtonPressTheaterMode;

    // Screenshot trigger states
    // Time to volume and power must be pressed within this interval of each other.
    private static final long SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS = 150;
    // Increase the chord delay when taking a screenshot from the keyguard
    private static final float KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER = 2.5f;
    private boolean mScreenshotChordEnabled;
    private boolean mScreenshotChordVolumeDownKeyTriggered;
    private long mScreenshotChordVolumeDownKeyTime;
    private boolean mScreenshotChordVolumeDownKeyConsumed;
    // Omni screenrecord
    private boolean mScreenrecordChordEnabled;
    private boolean mScreenrecordChordVolumeUpKeyTriggered;
    private long mScreenrecordChordVolumeUpKeyTime;
    private boolean mScreenrecordChordVolumeUpKeyConsumed;
    // Omni end
    private boolean mA11yShortcutChordVolumeUpKeyTriggered;
    private long mA11yShortcutChordVolumeUpKeyTime;
    private boolean mA11yShortcutChordVolumeUpKeyConsumed;

    private boolean mScreenshotChordPowerKeyTriggered;
    private long mScreenshotChordPowerKeyTime;

    private static final long BUGREPORT_TV_GESTURE_TIMEOUT_MILLIS = 1000;

    private boolean mBugreportTvKey1Pressed;
    private boolean mBugreportTvKey2Pressed;
    private boolean mBugreportTvScheduled;

    private boolean mAccessibilityTvKey1Pressed;
    private boolean mAccessibilityTvKey2Pressed;
    private boolean mAccessibilityTvScheduled;

    /* The number of steps between min and max brightness */
    private static final int BRIGHTNESS_STEPS = 10;

    SettingsObserver mSettingsObserver;
    ShortcutManager mShortcutManager;
    PowerManager.WakeLock mBroadcastWakeLock;
    PowerManager.WakeLock mPowerKeyWakeLock;
    boolean mHavePendingMediaKeyRepeatWithWakeLock;

    private int mCurrentUserId;
    private boolean haveEnableGesture = false;

    private int mScreenshotDelay;

    // Maps global key codes to the components that will handle them.
    private GlobalKeyManager mGlobalKeyManager;

    // Fallback actions by key code.
    private final SparseArray<KeyCharacterMap.FallbackAction> mFallbackActions =
            new SparseArray<KeyCharacterMap.FallbackAction>();

    private final LogDecelerateInterpolator mLogDecelerateInterpolator
            = new LogDecelerateInterpolator(100, 0);

    private final MutableBoolean mTmpBoolean = new MutableBoolean(false);

    private final List<DeviceKeyHandler> mDeviceKeyHandlers = new ArrayList<>();
    private LineageButtons mLineageButtons;

<<<<<<< HEAD
    private PocketManager mPocketManager;
    private PocketLock mPocketLock;
    private boolean mPocketLockShowing;
    private boolean mIsDeviceInPocket;
    private final IPocketCallback mPocketCallback = new IPocketCallback.Stub() {

        @Override
        public void onStateChanged(boolean isDeviceInPocket, int reason) {
            boolean wasDeviceInPocket = mIsDeviceInPocket;
            if (reason == PocketManager.REASON_SENSOR) {
                mIsDeviceInPocket = isDeviceInPocket;
            } else {
                mIsDeviceInPocket = false;
            }
            if (wasDeviceInPocket != mIsDeviceInPocket) {
                handleDevicePocketStateChanged();
            }
        }

    };

=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    private static final int MSG_ENABLE_POINTER_LOCATION = 1;
    private static final int MSG_DISABLE_POINTER_LOCATION = 2;
    private static final int MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK = 3;
    private static final int MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK = 4;
    private static final int MSG_KEYGUARD_DRAWN_COMPLETE = 5;
    private static final int MSG_KEYGUARD_DRAWN_TIMEOUT = 6;
    private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 7;
    private static final int MSG_DISPATCH_SHOW_RECENTS = 9;
    private static final int MSG_DISPATCH_SHOW_GLOBAL_ACTIONS = 10;
    private static final int MSG_HIDE_BOOT_MESSAGE = 11;
    private static final int MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK = 12;
    private static final int MSG_POWER_DELAYED_PRESS = 13;
    private static final int MSG_POWER_LONG_PRESS = 14;
    private static final int MSG_UPDATE_DREAMING_SLEEP_TOKEN = 15;
    private static final int MSG_REQUEST_TRANSIENT_BARS = 16;
    private static final int MSG_SHOW_PICTURE_IN_PICTURE_MENU = 17;
    private static final int MSG_BACK_LONG_PRESS = 18;
    private static final int MSG_DISPOSE_INPUT_CONSUMER = 19;
    private static final int MSG_BACK_DELAYED_PRESS = 20;
    private static final int MSG_ACCESSIBILITY_SHORTCUT = 21;
    private static final int MSG_BUGREPORT_TV = 22;
    private static final int MSG_ACCESSIBILITY_TV = 23;
    private static final int MSG_DISPATCH_BACK_KEY_TO_AUTOFILL = 24;
    private static final int MSG_SYSTEM_KEY_PRESS = 25;
    private static final int MSG_HANDLE_ALL_APPS = 26;
    private static final int MSG_TOGGLE_TORCH = 27;
    private static final int MSG_CAMERA_LONG_PRESS = 28;

    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS = 0;
    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_NAVIGATION = 1;

<<<<<<< HEAD
=======
    private LineageHardwareManager mLineageHardware;
    private boolean mClearedBecauseOfForceShow;
    private boolean mTopWindowIsKeyguard;

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    private CameraManager mCameraManager;
    private String mRearFlashCameraId;
    private boolean mTorchLongPressPowerEnabled;
    private boolean mTorchEnabled;
    private int mTorchTimeout;
    private PendingIntent mTorchOffPendingIntent;

<<<<<<< HEAD
    private HardkeyActionHandler mKeyHandler;

    private boolean mHasPermanentMenuKey;

    private boolean mClearedBecauseOfForceShow;
    private boolean mTopWindowIsKeyguard;

=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    private class PolicyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_POINTER_LOCATION:
                    enablePointerLocation();
                    break;
                case MSG_DISABLE_POINTER_LOCATION:
                    disablePointerLocation();
                    break;
                case MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK:
                    dispatchMediaKeyWithWakeLock((KeyEvent)msg.obj);
                    break;
                case MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK:
                    dispatchMediaKeyRepeatWithWakeLock((KeyEvent)msg.obj);
                    break;
                case MSG_DISPATCH_SHOW_RECENTS:
                    showRecentApps(false, msg.arg1 != 0);
                    break;
                case MSG_DISPATCH_SHOW_GLOBAL_ACTIONS:
                    showGlobalActionsInternal();
                    break;
                case MSG_KEYGUARD_DRAWN_COMPLETE:
                    if (DEBUG_WAKEUP) Slog.w(TAG, "Setting mKeyguardDrawComplete");
                    finishKeyguardDrawn();
                    break;
                case MSG_KEYGUARD_DRAWN_TIMEOUT:
                    Slog.w(TAG, "Keyguard drawn timeout. Setting mKeyguardDrawComplete");
                    finishKeyguardDrawn();
                    break;
                case MSG_WINDOW_MANAGER_DRAWN_COMPLETE:
                    if (DEBUG_WAKEUP) Slog.w(TAG, "Setting mWindowManagerDrawComplete");
                    finishWindowsDrawn();
                    break;
                case MSG_HIDE_BOOT_MESSAGE:
                    handleHideBootMessage();
                    break;
                case MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK:
                    launchVoiceAssistWithWakeLock(msg.arg1 != 0);
                    break;
                case MSG_POWER_DELAYED_PRESS:
                    powerPress((Long)msg.obj, msg.arg1 != 0, msg.arg2);
                    finishPowerKeyPress();
                    break;
                case MSG_POWER_LONG_PRESS:
                    powerLongPress();
                    break;
                case MSG_UPDATE_DREAMING_SLEEP_TOKEN:
                    updateDreamingSleepToken(msg.arg1 != 0);
                    break;
                case MSG_REQUEST_TRANSIENT_BARS:
                    WindowState targetBar = (msg.arg1 == MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS) ?
                            mStatusBar : mNavigationBar;
                    if (targetBar != null) {
                        requestTransientBars(targetBar);
                    }
                    break;
                case MSG_SHOW_PICTURE_IN_PICTURE_MENU:
                    showPictureInPictureMenuInternal();
                    break;
                case MSG_BACK_LONG_PRESS:
                    backLongPress();
                    finishBackKeyPress();
                    break;
                case MSG_DISPOSE_INPUT_CONSUMER:
                    disposeInputConsumer((InputConsumer) msg.obj);
                    break;
               /* case MSG_BACK_DELAYED_PRESS:
                    backMultiPressAction((Long) msg.obj, msg.arg1);
                    finishBackKeyPress();
                    break;*/
                case MSG_ACCESSIBILITY_SHORTCUT:
                    accessibilityShortcutActivated();
                    break;
                case MSG_BUGREPORT_TV:
                    takeBugreport();
                    break;
                case MSG_ACCESSIBILITY_TV:
                    if (mAccessibilityShortcutController.isAccessibilityShortcutAvailable(false)) {
                        accessibilityShortcutActivated();
                    }
                    break;
                case MSG_DISPATCH_BACK_KEY_TO_AUTOFILL:
                    mAutofillManagerInternal.onBackKeyPressed();
                    break;
                case MSG_SYSTEM_KEY_PRESS:
                    sendSystemKeyToStatusBar(msg.arg1);
                    break;
                case MSG_HANDLE_ALL_APPS:
                    launchAllAppsAction();
                    break;
                case MSG_TOGGLE_TORCH:
                    toggleTorch();
                    break;
                case MSG_CAMERA_LONG_PRESS:
                    KeyEvent event = (KeyEvent) msg.obj;
                    mIsLongPress = true;
                    break;
<<<<<<< HEAD
                case HardkeyActionHandler.MSG_FIRE_HOME:
                    launchHomeFromHotKey();
                    break;
                case HardkeyActionHandler.MSG_UPDATE_MENU_KEY:
                    synchronized (mLock) {
                        mHasPermanentMenuKey = msg.arg1 == 1;
                    }
                    break;
                case HardkeyActionHandler.MSG_DO_HAPTIC_FB:
                    performHapticFeedbackLw(null,
                            HapticFeedbackConstants.LONG_PRESS, false);
                    break;
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
            }
        }
    }

    private UEventObserver mHDMIObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            setHdmiPlugged("1".equals(event.get("SWITCH_STATE")));
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            // Observe all users' changes
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.END_BUTTON_BEHAVIOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.INCALL_BACK_BUTTON_BEHAVIOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.WAKE_GESTURE_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACCELEROMETER_ROTATION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.USER_ROTATION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_OFF_TIMEOUT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.POINTER_LOCATION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.DEFAULT_INPUT_METHOD), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.POLICY_CONTROL), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.ACCELEROMETER_ROTATION_ANGLES), false, this,
                    UserHandle.USER_ALL);
<<<<<<< HEAD
=======
            resolver.registerContentObserver(LineageSettings.Global.getUriFor(
                    LineageSettings.Global.DEV_FORCE_SHOW_NAVBAR), false, this,
                    UserHandle.USER_ALL);
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.TORCH_LONG_PRESS_POWER_GESTURE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.TORCH_LONG_PRESS_POWER_TIMEOUT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
<<<<<<< HEAD
=======
                    LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_MENU_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_ASSIST_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_APP_SWITCH_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                    LineageSettings.System.HOME_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BACK_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.MENU_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.ASSIST_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.APP_SWITCH_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.VOLUME_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.VOLUME_ANSWER_CALL), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.CAMERA_WAKE_SCREEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.CAMERA_SLEEP_ON_RELEASE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.CAMERA_LAUNCH), false, this,
                    UserHandle.USER_ALL);
<<<<<<< HEAD
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.NAVIGATION_BAR_VISIBLE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.NAVIGATION_BAR_HEIGHT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.NAVIGATION_BAR_WIDTH), false, this,
                    UserHandle.USER_ALL);
	    resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.THREE_FINGER_GESTURE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.PIE_STATE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.USE_EDGE_SERVICE_FOR_GESTURES), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ANBI_ENABLED), false, this,
=======
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.KILL_APP_LONGPRESS_BACK), false, this,
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Secure.SYSTEM_NAVIGATION_KEYS_ENABLED), false, this,
                    UserHandle.USER_ALL);
            updateSettings();
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
            updateRotation(false);
        }
    }

    class MyWakeGestureListener extends WakeGestureListener {
        MyWakeGestureListener(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        public void onWakeUp() {
            synchronized (mLock) {
                if (shouldEnableWakeGestureLp()) {
                    performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
                    wakeUp(SystemClock.uptimeMillis(), mAllowTheaterModeWakeFromWakeGesture,
                            "android.policy:GESTURE");
                }
            }
        }
    }

    class MyOrientationListener extends WindowOrientationListener {
        private final Runnable mUpdateRotationRunnable = new Runnable() {
            @Override
            public void run() {
                // send interaction hint to improve redraw performance
                mPowerManagerInternal.powerHint(PowerHint.INTERACTION, 0);
                updateRotation(false);
            }
        };

        MyOrientationListener(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        public void onProposedRotationChanged(int rotation) {
            if (localLOGV) Slog.v(TAG, "onProposedRotationChanged, rotation=" + rotation);
            mHandler.post(mUpdateRotationRunnable);
        }
    }
    MyOrientationListener mOrientationListener;

    final IPersistentVrStateCallbacks mPersistentVrModeListener =
            new IPersistentVrStateCallbacks.Stub() {
        @Override
        public void onPersistentVrStateChanged(boolean enabled) {
            mPersistentVrModeEnabled = enabled;
        }
    };

    private final StatusBarController mStatusBarController = new StatusBarController();

    private final BarController mNavigationBarController = new BarController("NavigationBar",
            View.NAVIGATION_BAR_TRANSIENT,
            View.NAVIGATION_BAR_UNHIDE,
            View.NAVIGATION_BAR_TRANSLUCENT,
            StatusBarManager.WINDOW_NAVIGATION_BAR,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            View.NAVIGATION_BAR_TRANSPARENT);

    private final BarController.OnBarVisibilityChangedListener mNavBarVisibilityListener =
            new BarController.OnBarVisibilityChangedListener() {
        @Override
        public void onBarVisibilityChanged(boolean visible) {
            mAccessibilityManager.notifyAccessibilityButtonVisibilityChanged(visible);
        }
    };

    private final Runnable mAcquireSleepTokenRunnable = () -> {
        if (mWindowSleepToken != null) {
            return;
        }
        mWindowSleepToken = mActivityManagerInternal.acquireSleepToken("WindowSleepToken",
                DEFAULT_DISPLAY);
    };

    private final Runnable mReleaseSleepTokenRunnable = () -> {
        if (mWindowSleepToken == null) {
            return;
        }
        mWindowSleepToken.release();
        mWindowSleepToken = null;
    };

    private ImmersiveModeConfirmation mImmersiveModeConfirmation;

    private SystemGesturesPointerEventListener mSystemGestures;
    private OPGesturesListener mOPGestures;

    private EdgeGestureManager.EdgeGestureActivationListener mEdgeGestureActivationListener
            = new EdgeGestureManager.EdgeGestureActivationListener() {

        @Override
        public void onEdgeGestureActivation(int touchX, int touchY,
                EdgeGesturePosition position, int flags) {
            WindowState target = null;

            if (position == EdgeGesturePosition.TOP) {
                target = mStatusBar;
            } else if ((position == EdgeGesturePosition.BOTTOM) && (mNavigationBarPosition == NAV_BAR_BOTTOM)) {
                target = mNavigationBar;
            } else if ((position == EdgeGesturePosition.RIGHT) && (mNavigationBarPosition == NAV_BAR_RIGHT)) {
                target = mNavigationBar;
            } else if ((position == EdgeGesturePosition.LEFT) && (mNavigationBarPosition == NAV_BAR_LEFT)) {
                target = mNavigationBar;
            }

            if (target != null) {
                requestTransientBars(target);
                dropEventsUntilLift();
                mEdgeListenerActivated = true;
            } else {
                restoreListenerState();
            }
        }
    };
    private EdgeGestureManager mEdgeGestureManager = null;
    private int mLastEdgePositions = 0;
    private boolean mEdgeListenerActivated = false;
    private boolean mUsingEdgeGestureServiceForGestures = false;

    private void updateEdgeGestureListenerState() {
        int flags = 0;
        if (mUsingEdgeGestureServiceForGestures) {
            flags = EdgeServiceConstants.LONG_LIVING | EdgeServiceConstants.UNRESTRICTED;
            if (mStatusBar != null && !mStatusBar.isVisibleLw()) {
                flags |= EdgeGesturePosition.TOP.FLAG;
            }
            if (mNavigationBar != null && !mNavigationBar.isVisibleLw()
                    && !immersiveModeImplementsPie() && !isStatusBarKeyguard()) {
                if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                    flags |= EdgeGesturePosition.BOTTOM.FLAG;
                } else if (mNavigationBarPosition == NAV_BAR_RIGHT){
                    flags |= EdgeGesturePosition.RIGHT.FLAG;
                } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                    flags |= EdgeGesturePosition.LEFT.FLAG;
                }
            }
        }
        if (mEdgeListenerActivated) {
            mEdgeGestureActivationListener.restoreListenerState();
            mEdgeListenerActivated = false;
        }
        if (flags != mLastEdgePositions) {
            mEdgeGestureManager.updateEdgeGestureActivationListener(mEdgeGestureActivationListener,
                    flags);
            mLastEdgePositions = flags;
        }
    }

    IStatusBarService getStatusBarService() {
        synchronized (mServiceAquireLock) {
            if (mStatusBarService == null) {
                mStatusBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
            }
            return mStatusBarService;
        }
    }

    StatusBarManagerInternal getStatusBarManagerInternal() {
        synchronized (mServiceAquireLock) {
            if (mStatusBarManagerInternal == null) {
                mStatusBarManagerInternal =
                        LocalServices.getService(StatusBarManagerInternal.class);
            }
            return mStatusBarManagerInternal;
        }
    }

    /*
     * We always let the sensor be switched on by default except when
     * the user has explicitly disabled sensor based rotation or when the
     * screen is switched off.
     */
    boolean needSensorRunningLp() {
        if (mSupportAutoRotation) {
            if (mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                // If the application has explicitly requested to follow the
                // orientation, then we need to turn the sensor on.
                return true;
            }
        }
        if ((mCarDockEnablesAccelerometer && mDockMode == Intent.EXTRA_DOCK_STATE_CAR) ||
                (mDeskDockEnablesAccelerometer && (mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK))) {
            // enable accelerometer if we are docked in a dock that enables accelerometer
            // orientation management,
            return true;
        }
        if (mUserRotationMode == USER_ROTATION_LOCKED) {
            // If the setting for using the sensor by default is enabled, then
            // we will always leave it on.  Note that the user could go to
            // a window that forces an orientation that does not use the
            // sensor and in theory we could turn it off... however, when next
            // turning it on we won't have a good value for the current
            // orientation for a little bit, which can cause orientation
            // changes to lag, so we'd like to keep it always on.  (It will
            // still be turned off when the screen is off.)
            return false;
        }
        return mSupportAutoRotation;
    }

    /*
     * Various use cases for invoking this function
     * screen turning off, should always disable listeners if already enabled
     * screen turned on and current app has sensor based orientation, enable listeners
     * if not already enabled
     * screen turned on and current app does not have sensor orientation, disable listeners if
     * already enabled
     * screen turning on and current app has sensor based orientation, enable listeners if needed
     * screen turning on and current app has nosensor based orientation, do nothing
     */
    void updateOrientationListenerLp() {
        if (!mOrientationListener.canDetectOrientation()) {
            // If sensor is turned off or nonexistent for some reason
            return;
        }
        // Could have been invoked due to screen turning on or off or
        // change of the currently visible window's orientation.
        if (localLOGV) Slog.v(TAG, "mScreenOnEarly=" + mScreenOnEarly
                + ", mAwake=" + mAwake + ", mCurrentAppOrientation=" + mCurrentAppOrientation
                + ", mOrientationSensorEnabled=" + mOrientationSensorEnabled
                + ", mKeyguardDrawComplete=" + mKeyguardDrawComplete
                + ", mWindowManagerDrawComplete=" + mWindowManagerDrawComplete);

        boolean disable = true;
        // Note: We postpone the rotating of the screen until the keyguard as well as the
        // window manager have reported a draw complete or the keyguard is going away in dismiss
        // mode.
        if (mScreenOnEarly && mAwake && ((mKeyguardDrawComplete && mWindowManagerDrawComplete))) {
            if (needSensorRunningLp()) {
                disable = false;
                //enable listener if not already enabled
                if (!mOrientationSensorEnabled) {
                    // Don't clear the current sensor orientation if the keyguard is going away in
                    // dismiss mode. This allows window manager to use the last sensor reading to
                    // determine the orientation vs. falling back to the last known orientation if
                    // the sensor reading was cleared which can cause it to relaunch the app that
                    // will show in the wrong orientation first before correcting leading to app
                    // launch delays.
                    mOrientationListener.enable(true /* clearCurrentRotation */);
                    if(localLOGV) Slog.v(TAG, "Enabling listeners");
                    mOrientationSensorEnabled = true;
                }
            }
        }
        //check if sensors need to be disabled
        if (disable && mOrientationSensorEnabled) {
            mOrientationListener.disable();
            if(localLOGV) Slog.v(TAG, "Disabling listeners");
            mOrientationSensorEnabled = false;
        }
    }

    private void interceptBackKeyDown() {
        MetricsLogger.count(mContext, "key_back_down", 1);
        // Reset back key state for long press
        mBackKeyHandled = false;

        // Cancel multi-press detection timeout.
        if (hasPanicPressOnBackBehavior()) {
            if (mBackKeyPressCounter != 0
                    && mBackKeyPressCounter < PANIC_PRESS_BACK_COUNT) {
                mHandler.removeMessages(MSG_BACK_DELAYED_PRESS);
            }
        }

        if (hasLongPressOnBackBehavior()) {
            Message msg = mHandler.obtainMessage(MSG_BACK_LONG_PRESS);
            msg.setAsynchronous(true);
            mHandler.sendMessageDelayed(msg,
                    ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
        }
    }

    // returns true if the key was handled and should not be passed to the user
    private boolean interceptBackKeyUp(KeyEvent event) {
        // Cache handled state
        boolean handled = mBackKeyHandled;

        if (hasPanicPressOnBackBehavior()) {
            // Check for back key panic press
            ++mBackKeyPressCounter;

            final long eventTime = event.getDownTime();

            if (mBackKeyPressCounter <= PANIC_PRESS_BACK_COUNT) {
                // This could be a multi-press.  Wait a little bit longer to confirm.
                Message msg = mHandler.obtainMessage(MSG_BACK_DELAYED_PRESS,
                    mBackKeyPressCounter, 0, eventTime);
                msg.setAsynchronous(true);
                mHandler.sendMessageDelayed(msg, ViewConfiguration.getMultiPressTimeout());
            }
        }

        // Reset back long press state
        cancelPendingBackKeyAction();

        if (mHasFeatureWatch) {
            TelecomManager telecomManager = getTelecommService();

            if (telecomManager != null) {
                if (telecomManager.isRinging()) {
                    // Pressing back while there's a ringing incoming
                    // call should silence the ringer.
                    telecomManager.silenceRinger();

                    // It should not prevent navigating away
                    return false;
                } else if (
                    (mIncallBackBehavior & Settings.Secure.INCALL_BACK_BUTTON_BEHAVIOR_HANGUP) != 0
                        && telecomManager.isInCall()) {
                    // Otherwise, if "Back button ends call" is enabled,
                    // the Back button will hang up any current active call.
                    return telecomManager.endCall();
                }
            }
        }

        if (mAutofillManagerInternal != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_DISPATCH_BACK_KEY_TO_AUTOFILL));
        }

        return handled;
    }

    private void interceptPowerKeyDown(KeyEvent event, boolean interactive) {
        // Hold a wake lock until the power key is released.
        if (!mPowerKeyWakeLock.isHeld()) {
            mPowerKeyWakeLock.acquire();
        }

        // Still allow muting call with power button press.
        boolean blockInputs = mIsDeviceInPocket && (!interactive || mPocketLockShowing);

        // Cancel multi-press detection timeout.
        if (mPowerKeyPressCounter != 0) {
            mHandler.removeMessages(MSG_POWER_DELAYED_PRESS);
        }

        // Detect user pressing the power button in panic when an application has
        // taken over the whole screen.
        boolean panic = mImmersiveModeConfirmation.onPowerKeyDown(interactive,
                SystemClock.elapsedRealtime(), isImmersiveMode(mLastSystemUiFlags),
                isNavBarEmpty(mLastSystemUiFlags));
        if (panic) {
            mHandler.post(mHiddenNavPanic);
        }

        // Latch power key state to detect screenshot chord.
        if (interactive && !mScreenshotChordPowerKeyTriggered
                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
            mScreenshotChordPowerKeyTriggered = true;
            mScreenshotChordPowerKeyTime = event.getDownTime();
            checkSettings();
            interceptScreenshotChord();
            interceptScreenrecordChord();
        }

        // Stop ringing or end call if configured to do so when power is pressed.
        TelecomManager telecomManager = getTelecommService();
        boolean hungUp = false;
        if (telecomManager != null) {
            if (telecomManager.isRinging()) {
                // Pressing Power while there's a ringing incoming
                // call should silence the ringer.
                telecomManager.silenceRinger();
            } else if (!blockInputs && (mIncallPowerBehavior
                    & Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP) != 0
                    && telecomManager.isInCall() && interactive) {
                // Otherwise, if "Power button ends call" is enabled,
                // the Power button will hang up any current active call.
                hungUp = telecomManager.endCall();
            }
        }


        boolean gesturedServiceIntercepted = false;
        if (!blockInputs) {
            GestureLauncherService gestureService = LocalServices.getService(
                    GestureLauncherService.class);
            if (gestureService != null) {
                gesturedServiceIntercepted = gestureService.interceptPowerKeyDown(event, interactive,
                        mTmpBoolean);
                if (mTmpBoolean.value && mGoingToSleep) {
                    mCameraGestureTriggeredDuringGoingToSleep = true;
                }
            }
        }

        // Inform the StatusBar; but do not allow it to consume the event.
        sendSystemKeyToStatusBarAsync(event.getKeyCode());

        // If the power key has still not yet been handled, then detect short
        // press, long press, or multi press and decide what to do.
        mPowerKeyHandled = hungUp || mScreenshotChordVolumeDownKeyTriggered
                || mScreenrecordChordVolumeUpKeyTriggered
                || mA11yShortcutChordVolumeUpKeyTriggered || gesturedServiceIntercepted;
        if (!mPowerKeyHandled) {
            if (interactive) {
                // When interactive, we're already awake.
                // Wait for a long press or for the button to be released to decide what to do.
                if (hasLongPressOnPowerBehavior()) {
                    mResolvedLongPressOnPowerBehavior = getResolvedLongPressOnPowerBehavior();
                    Message msg = mHandler.obtainMessage(MSG_POWER_LONG_PRESS);
                    msg.setAsynchronous(true);
                    mHandler.sendMessageDelayed(msg,
                            ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                }
            } else {
                if (mSupportLongPressPowerWhenNonInteractive && hasLongPressOnPowerBehavior()) {
                    mResolvedLongPressOnPowerBehavior = getResolvedLongPressOnPowerBehavior();
                    if (mResolvedLongPressOnPowerBehavior != LONG_PRESS_POWER_TORCH) {
                        wakeUpFromPowerKey(event.getDownTime());
                    }
                    Message msg = mHandler.obtainMessage(MSG_POWER_LONG_PRESS);
                    msg.setAsynchronous(true);
                    mHandler.sendMessageDelayed(msg,
                            ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                    mBeganFromNonInteractive = true;
                } else {
                    wakeUpFromPowerKey(event.getDownTime());

                    final int maxCount = getMaxMultiPressPowerCount();

                    if (maxCount <= 1) {
                        mPowerKeyHandled = true;
                    } else {
                        mBeganFromNonInteractive = true;
                    }
                }
            }
        }
    }

    private void interceptPowerKeyUp(KeyEvent event, boolean interactive, boolean canceled) {
        final boolean handled = canceled || mPowerKeyHandled;
        mScreenshotChordPowerKeyTriggered = false;
        cancelPendingScreenshotChordAction();
        cancelPendingPowerKeyAction();

        if (!handled) {
            // Figure out how to handle the key now that it has been released.
            mPowerKeyPressCounter += 1;

            final int maxCount = getMaxMultiPressPowerCount();
            final long eventTime = event.getDownTime();
            if (mPowerKeyPressCounter < maxCount) {
                // This could be a multi-press.  Wait a little bit longer to confirm.
                // Continue holding the wake lock.
                Message msg = mHandler.obtainMessage(MSG_POWER_DELAYED_PRESS,
                        interactive ? 1 : 0, mPowerKeyPressCounter, eventTime);
                msg.setAsynchronous(true);
                mHandler.sendMessageDelayed(msg, ViewConfiguration.getMultiPressTimeout());
                return;
            }

            // No other actions.  Handle it immediately.
            powerPress(eventTime, interactive, mPowerKeyPressCounter);
        }

        // Done.  Reset our state.
        finishPowerKeyPress();
    }

    private void finishPowerKeyPress() {
        mBeganFromNonInteractive = false;
        mPowerKeyPressCounter = 0;
        if (mPowerKeyWakeLock.isHeld()) {
            mPowerKeyWakeLock.release();
        }
    }

    private void finishBackKeyPress() {
        mBackKeyPressCounter = 0;
    }

    private void cancelPendingPowerKeyAction() {
        if (!mPowerKeyHandled) {
            mPowerKeyHandled = true;
            mHandler.removeMessages(MSG_POWER_LONG_PRESS);
            // See if we deferred screen wake because long press power for torch is enabled
            if (mResolvedLongPressOnPowerBehavior == LONG_PRESS_POWER_TORCH && !isScreenOn()) {
                wakeUpFromPowerKey(SystemClock.uptimeMillis());
            }
        }
    }

    private void cancelPendingBackKeyAction() {
        if (!mBackKeyHandled) {
            mBackKeyHandled = true;
            mHandler.removeMessages(MSG_BACK_LONG_PRESS);
        }
    }

    private void backMultiPressAction(long eventTime, int count) {
        if (count >= PANIC_PRESS_BACK_COUNT) {
            switch (mPanicPressOnBackBehavior) {
                case PANIC_PRESS_BACK_NOTHING:
                    break;
                case PANIC_PRESS_BACK_HOME:
                    launchHomeFromHotKey();
                    break;
            }
        }
    }

    private void powerPress(long eventTime, boolean interactive, int count) {
        if (mScreenOnEarly && !mScreenOnFully) {
            Slog.i(TAG, "Suppressed redundant power key press while "
                    + "already in the process of turning the screen on.");
            return;
        }

        if (count == 2) {
            powerMultiPressAction(eventTime, interactive, mDoublePressOnPowerBehavior);
        } else if (count == 3) {
            powerMultiPressAction(eventTime, interactive, mTriplePressOnPowerBehavior);
        } else if (interactive && !mBeganFromNonInteractive) {
            switch (mShortPressOnPowerBehavior) {
                case SHORT_PRESS_POWER_NOTHING:
                    break;
                case SHORT_PRESS_POWER_GO_TO_SLEEP:
                    goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                    break;
                case SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP:
                    goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
                    break;
                case SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP_AND_GO_HOME:
                    goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
                    launchHomeFromHotKey();
                    break;
                case SHORT_PRESS_POWER_GO_HOME:
                    shortPressPowerGoHome();
                    break;
                case SHORT_PRESS_POWER_CLOSE_IME_OR_GO_HOME: {
                    if (mDismissImeOnBackKeyPressed) {
                        if (mInputMethodManagerInternal == null) {
                            mInputMethodManagerInternal =
                                    LocalServices.getService(InputMethodManagerInternal.class);
                        }
                        if (mInputMethodManagerInternal != null) {
                            mInputMethodManagerInternal.hideCurrentInputMethod();
                        }
                    } else {
                        shortPressPowerGoHome();
                    }
                    break;
                }
            }
        }
    }

    private void goToSleep(long eventTime, int reason, int flags) {
        mRequestedOrGoingToSleep = true;
        mPowerManager.goToSleep(eventTime, reason, flags);
    }

    private void shortPressPowerGoHome() {
        launchHomeFromHotKey(true /* awakenFromDreams */, false /*respectKeyguard*/);
        if (isKeyguardShowingAndNotOccluded()) {
            // Notify keyguard so it can do any special handling for the power button since the
            // device will not power off and only launch home.
            mKeyguardDelegate.onShortPowerPressedGoHome();
        }
    }

    private void powerMultiPressAction(long eventTime, boolean interactive, int behavior) {
        switch (behavior) {
            case MULTI_PRESS_POWER_NOTHING:
                break;
            case MULTI_PRESS_POWER_THEATER_MODE:
                if (!isUserSetupComplete()) {
                    Slog.i(TAG, "Ignoring toggling theater mode - device not setup.");
                    break;
                }

                if (isTheaterModeEnabled()) {
                    Slog.i(TAG, "Toggling theater mode off.");
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.THEATER_MODE_ON, 0);
                    if (!interactive) {
                        wakeUpFromPowerKey(eventTime);
                    }
                } else {
                    Slog.i(TAG, "Toggling theater mode on.");
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.THEATER_MODE_ON, 1);

                    if (mGoToSleepOnButtonPressTheaterMode && interactive) {
                        goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                    }
                }
                break;
            case MULTI_PRESS_POWER_BRIGHTNESS_BOOST:
                Slog.i(TAG, "Starting brightness boost.");
                if (!interactive) {
                    wakeUpFromPowerKey(eventTime);
                }
                mPowerManager.boostScreenBrightness(eventTime);
                break;
        }
    }

    private int getMaxMultiPressPowerCount() {
        if (mTriplePressOnPowerBehavior != MULTI_PRESS_POWER_NOTHING) {
            return 3;
        }
        if (mDoublePressOnPowerBehavior != MULTI_PRESS_POWER_NOTHING) {
            return 2;
        }
        return 1;
    }

    private void powerLongPress() {
<<<<<<< HEAD
        int behavior = mResolvedLongPressOnPowerBehavior;
=======
        final int behavior = mResolvedLongPressOnPowerBehavior;
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
        switch (behavior) {
        case LONG_PRESS_POWER_NOTHING:
            break;
        case LONG_PRESS_POWER_GLOBAL_ACTIONS:
            mPowerKeyHandled = true;
            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
            KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            boolean globalActionsOnLockScreen = Settings.System.getInt(
                    mContext.getContentResolver(), Settings.System.POWERMENU_LOCKSCREEN, 1) == 1;
            if (locked && !globalActionsOnLockScreen) {
                behavior = LONG_PRESS_POWER_NOTHING;
            } else {
                showGlobalActionsInternal();
            }
            break;
        case LONG_PRESS_POWER_SHUT_OFF:
        case LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM:
            mPowerKeyHandled = true;
            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
            mWindowManagerFuncs.shutdown(behavior == LONG_PRESS_POWER_SHUT_OFF);
            break;
        case LONG_PRESS_POWER_TORCH:
            mPowerKeyHandled = true;
            // Toggle torch state asynchronously to help protect against
            // a misbehaving cameraservice from blocking systemui.
            mHandler.removeMessages(MSG_TOGGLE_TORCH);
            Message msg = mHandler.obtainMessage(MSG_TOGGLE_TORCH);
            msg.setAsynchronous(true);
            msg.sendToTarget();
            break;
<<<<<<< HEAD
        case LONG_PRESS_POWER_HIDE_POCKET_LOCK:
            mPowerKeyHandled = true;
            if (!performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false)) {
                mAccessibilityShortcutController.performAccessibilityShortcut();
            }
            hidePocketLock(true);
            mPocketManager.setListeningExternal(false);
            break;
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
        }
    }

    private void backLongPress() {
        mBackKeyHandled = true;

        switch (mLongPressOnBackBehavior) {
            case LONG_PRESS_BACK_NOTHING:
                break;
            case LONG_PRESS_BACK_GO_TO_VOICE_ASSIST:
                final boolean keyguardActive = mKeyguardDelegate == null
                        ? false
                        : mKeyguardDelegate.isShowing();
                if (!keyguardActive) {
                    Intent intent = new Intent(Intent.ACTION_VOICE_ASSIST);
                    startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);
                }
                break;
        }
    }

    private void accessibilityShortcutActivated() {
        mAccessibilityShortcutController.performAccessibilityShortcut();
    }

    private void disposeInputConsumer(InputConsumer inputConsumer) {
        if (inputConsumer != null) {
            inputConsumer.dismiss();
        }
    }

    private void sleepPress(long eventTime) {
        if (mShortPressOnSleepBehavior == SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME) {
            launchHomeFromHotKey(false /* awakenDreams */, true /*respectKeyguard*/);
        }
    }

    private void sleepRelease(long eventTime) {
        switch (mShortPressOnSleepBehavior) {
            case SHORT_PRESS_SLEEP_GO_TO_SLEEP:
            case SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME:
                Slog.i(TAG, "sleepRelease() calling goToSleep(GO_TO_SLEEP_REASON_SLEEP_BUTTON)");
                goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON, 0);
                break;
        }
    }

    private int getResolvedLongPressOnPowerBehavior() {
        if (FactoryTest.isLongPressOnPowerOffEnabled()) {
            return LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM;
        }
        if (mTorchLongPressPowerEnabled && !isScreenOn()) {
            return LONG_PRESS_POWER_TORCH;
        }
<<<<<<< HEAD
        if (mPocketLockShowing) {
            return LONG_PRESS_POWER_HIDE_POCKET_LOCK;
        }
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
        return mLongPressOnPowerBehavior;
    }

    private boolean hasLongPressOnPowerBehavior() {
        return getResolvedLongPressOnPowerBehavior() != LONG_PRESS_POWER_NOTHING;
    }

    private boolean hasLongPressOnBackBehavior() {
        return mLongPressOnBackBehavior != LONG_PRESS_BACK_NOTHING;
    }

    private boolean hasPanicPressOnBackBehavior() {
        return mPanicPressOnBackBehavior != PANIC_PRESS_BACK_NOTHING;
    }

    private void interceptScreenshotChord() {
        if (mScreenshotChordEnabled
                && mScreenshotChordVolumeDownKeyTriggered && mScreenshotChordPowerKeyTriggered
                && !mA11yShortcutChordVolumeUpKeyTriggered) {
            final long now = SystemClock.uptimeMillis();
            if (now <= mScreenshotChordVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS
                    && now <= mScreenshotChordPowerKeyTime
                            + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
                mScreenshotChordVolumeDownKeyConsumed = true;
                cancelPendingPowerKeyAction();
                if (Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREENSHOT_TYPE, 0, UserHandle.USER_CURRENT) == 1) {
                    mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_SELECTED_REGION);
                } else {
                    mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_FULLSCREEN);
                }
                mHandler.postDelayed(mScreenshotRunnable, getScreenshotChordLongPressDelay());
            }
        }
    }

    private void interceptScreenrecordChord() {
        if (mScreenrecordChordEnabled && mScreenrecordChordVolumeUpKeyTriggered
                && mScreenshotChordPowerKeyTriggered) {
            final long now = SystemClock.uptimeMillis();
            if (now <= mScreenrecordChordVolumeUpKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS
                    && now <= mScreenshotChordPowerKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
                mScreenrecordChordVolumeUpKeyConsumed = true;
                cancelPendingPowerKeyAction();

                mHandler.postDelayed(mScreenrecordRunnable, getScreenshotChordLongPressDelay());
            }
        }
    }

    private void interceptAccessibilityShortcutChord() {
        if (mAccessibilityShortcutController.isAccessibilityShortcutAvailable(isKeyguardLocked())
                && mScreenshotChordVolumeDownKeyTriggered && mA11yShortcutChordVolumeUpKeyTriggered
                && !mScreenshotChordPowerKeyTriggered) {
            final long now = SystemClock.uptimeMillis();
            if (now <= mScreenshotChordVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS
                    && now <= mA11yShortcutChordVolumeUpKeyTime
                    + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
                mScreenshotChordVolumeDownKeyConsumed = true;
                mA11yShortcutChordVolumeUpKeyConsumed = true;
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_ACCESSIBILITY_SHORTCUT),
                        ViewConfiguration.get(mContext).getAccessibilityShortcutKeyTimeout());
            }
        }
    }

    private long getScreenshotChordLongPressDelay() {
        if (mKeyguardDelegate.isShowing()) {
            // Double the time it takes to take a screenshot from the keyguard
            return (long) (KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER *
                    ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
        }
        return ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout();
    }

    private void cancelPendingScreenshotChordAction() {
        mHandler.removeCallbacks(mScreenshotRunnable);
    }

    private void cancelPendingScreenrecordChordAction() {
        mHandler.removeCallbacks(mScreenrecordRunnable);
    }

    private void cancelPendingAccessibilityShortcutAction() {
        mHandler.removeMessages(MSG_ACCESSIBILITY_SHORTCUT);
    }

    private final Runnable mEndCallLongPress = new Runnable() {
        @Override
        public void run() {
            mEndCallKeyHandled = true;
            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
            showGlobalActionsInternal();
        }
    };

    private class ScreenshotRunnable implements Runnable {
        private int mScreenshotType = TAKE_SCREENSHOT_FULLSCREEN;

        public void setScreenshotType(int screenshotType) {
            mScreenshotType = screenshotType;
        }

        @Override
        public void run() {
            takeScreenshot(mScreenshotType);
        }
    }

    private final ScreenshotRunnable mScreenshotRunnable = new ScreenshotRunnable();

<<<<<<< HEAD
    private class ScreenrecordRunnable implements Runnable {
        private int mMode = SCREEN_RECORD_LOW_QUALITY;

        public void setMode(int mode) {
            mMode = mode;
        }

        @Override
        public void run() {
            takeScreenrecord(mMode);
        }
    }

    private final ScreenrecordRunnable mScreenrecordRunnable = new ScreenrecordRunnable();

    @Override
    public void screenRecordAction(int mode) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_SURFACE_FLINGER,
                TAG + "screenRecordAction permission denied");
        mHandler.removeCallbacks(mScreenrecordRunnable);
        mScreenrecordRunnable.setMode(mode);
        mHandler.post(mScreenrecordRunnable);
    }
=======
    Runnable mBackLongPress = new Runnable() {
        public void run() {
            if (unpinActivity(false)) {
                return;
            }

            if (ActionUtils.killForegroundApp(mContext, mCurrentUserId)) {
                performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                Toast.makeText(mContext,
                        org.lineageos.platform.internal.R.string.app_killed_message,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017

    @Override
    public void showGlobalActions() {
        mHandler.removeMessages(MSG_DISPATCH_SHOW_GLOBAL_ACTIONS);
        mHandler.sendEmptyMessage(MSG_DISPATCH_SHOW_GLOBAL_ACTIONS);
    }

    void showGlobalActionsInternal() {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
        if (mGlobalActions == null) {
            mGlobalActions = new GlobalActions(mContext, mWindowManagerFuncs);
        }
        final boolean keyguardShowing = isKeyguardShowingAndNotOccluded();
        mGlobalActions.showDialog(keyguardShowing, isDeviceProvisioned());
        if (keyguardShowing) {
            // since it took two seconds of long press to bring this up,
            // poke the wake lock so they have some time to see the dialog.
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    boolean isDeviceProvisioned() {
        return Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    boolean isUserSetupComplete() {
        boolean isSetupComplete = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) != 0;
        if (mHasFeatureLeanback) {
            isSetupComplete &= isTvUserSetupComplete();
        }
        return isSetupComplete;
    }

    private boolean isTvUserSetupComplete() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.TV_USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) != 0;
    }

    private void handleShortPressOnHome() {
        // Turn on the connected TV and switch HDMI input if we're a HDMI playback device.
        final HdmiControl hdmiControl = getHdmiControl();
        if (hdmiControl != null) {
            hdmiControl.turnOnTv();
        }

        // If there's a dream running then use home to escape the dream
        // but don't actually go home.
        if (mDreamManagerInternal != null && mDreamManagerInternal.isDreaming()) {
            mDreamManagerInternal.stopDream(false /*immediate*/);
            return;
        }

        // Go home!
        launchHomeFromHotKey();
    }

    /**
     * Creates an accessor to HDMI control service that performs the operation of
     * turning on TV (optional) and switching input to us. If HDMI control service
     * is not available or we're not a HDMI playback device, the operation is no-op.
     * @return {@link HdmiControl} instance if available, null otherwise.
     */
    private HdmiControl getHdmiControl() {
        if (null == mHdmiControl) {
            if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_HDMI_CEC)) {
                return null;
            }
            HdmiControlManager manager = (HdmiControlManager) mContext.getSystemService(
                        Context.HDMI_CONTROL_SERVICE);
            HdmiPlaybackClient client = null;
            if (manager != null) {
                client = manager.getPlaybackClient();
            }
            mHdmiControl = new HdmiControl(client);
        }
        return mHdmiControl;
    }

    private static class HdmiControl {
        private final HdmiPlaybackClient mClient;

        private HdmiControl(HdmiPlaybackClient client) {
            mClient = client;
        }

        public void turnOnTv() {
            if (mClient == null) {
                return;
            }
            mClient.oneTouchPlay(new OneTouchPlayCallback() {
                @Override
                public void onComplete(int result) {
                    if (result != HdmiControlManager.RESULT_SUCCESS) {
                        Log.w(TAG, "One touch play failed: " + result);
                    }
                }
            });
        }
    }

    private void triggerVirtualKeypress(final int keyCode) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();
        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        final KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);

        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private void launchAllAppsAction() {
        Intent intent = new Intent(Intent.ACTION_ALL_APPS);
        if (mHasFeatureLeanback) {
            final PackageManager pm = mContext.getPackageManager();
            Intent intentLauncher = new Intent(Intent.ACTION_MAIN);
            intentLauncher.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = pm.resolveActivityAsUser(intentLauncher,
                    PackageManager.MATCH_SYSTEM_ONLY,
                    mCurrentUserId);
            if (resolveInfo != null) {
                intent.setPackage(resolveInfo.activityInfo.packageName);
            }
        }
        startActivityAsUser(intent, UserHandle.CURRENT);
    }

    private void launchCameraAction() {
        sendCloseSystemWindows();
        Intent intent = new Intent(lineageos.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
        mContext.sendBroadcast(intent, android.Manifest.permission.STATUS_BAR_SERVICE);
    }

    private void showPictureInPictureMenu(KeyEvent event) {
        if (DEBUG_INPUT) Log.d(TAG, "showPictureInPictureMenu event=" + event);
        mHandler.removeMessages(MSG_SHOW_PICTURE_IN_PICTURE_MENU);
        Message msg = mHandler.obtainMessage(MSG_SHOW_PICTURE_IN_PICTURE_MENU);
        msg.setAsynchronous(true);
        msg.sendToTarget();
    }

    private void showPictureInPictureMenuInternal() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.showPictureInPictureMenu();
        }
    }

    private void performKeyAction(Action action, KeyEvent event) {
        switch (action) {
            case NOTHING:
                break;
            case MENU:
                triggerVirtualKeypress(KeyEvent.KEYCODE_MENU);
                break;
            case APP_SWITCH:
                toggleRecentApps();
                break;
            case SEARCH:
                launchAssistAction(null, event.getDeviceId());
                break;
            case VOICE_SEARCH:
                launchAssistLongPressAction();
              break;
            case IN_APP_SEARCH:
                triggerVirtualKeypress(KeyEvent.KEYCODE_SEARCH);
                break;
            case LAUNCH_CAMERA:
                launchCameraAction();
                break;
            case SLEEP:
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
                break;
            case LAST_APP:
                ActionUtils.switchToLastApp(mContext, mCurrentUserId);
                break;
            case SPLIT_SCREEN:
                toggleSplitScreen();
                break;
            default:
                break;
        }
    }

    private final Runnable mHomeDoubleTapTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mHomeDoubleTapPending) {
                mHomeDoubleTapPending = false;
                handleShortPressOnHome();
            }
        }
    };

    private boolean isRoundWindow() {
        return mContext.getResources().getConfiguration().isScreenRound();
    }

    /** {@inheritDoc} */
    @Override
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowManager = windowManager;
        mWindowManagerFuncs = windowManagerFuncs;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mDreamManagerInternal = LocalServices.getService(DreamManagerInternal.class);
        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mHasFeatureWatch = mContext.getPackageManager().hasSystemFeature(FEATURE_WATCH);
        mHasFeatureLeanback = mContext.getPackageManager().hasSystemFeature(FEATURE_LEANBACK);
        mAccessibilityShortcutController =
                new AccessibilityShortcutController(mContext, new Handler(), mCurrentUserId);
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        // Init display burn-in protection
        boolean burnInProtectionEnabled = context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableBurnInProtection);
        // Allow a system property to override this. Used by developer settings.
        boolean burnInProtectionDevMode =
                SystemProperties.getBoolean("persist.debug.force_burn_in", false);
        if (burnInProtectionEnabled || burnInProtectionDevMode) {
            final int minHorizontal;
            final int maxHorizontal;
            final int minVertical;
            final int maxVertical;
            final int maxRadius;
            if (burnInProtectionDevMode) {
                minHorizontal = -8;
                maxHorizontal = 8;
                minVertical = -8;
                maxVertical = -4;
                maxRadius = (isRoundWindow()) ? 6 : -1;
            } else {
                Resources resources = context.getResources();
                minHorizontal = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMinHorizontalOffset);
                maxHorizontal = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMaxHorizontalOffset);
                minVertical = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMinVerticalOffset);
                maxVertical = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMaxVerticalOffset);
                maxRadius = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMaxRadius);
            }
            mBurnInProtectionHelper = new BurnInProtectionHelper(
                    context, minHorizontal, maxHorizontal, minVertical, maxVertical, maxRadius);
        }

        mOPGestures = new OPGesturesListener(context, new OPGesturesListener.Callbacks() {
                    @Override
                    public void onSwipeThreeFinger() {
                        mHandler.post(mScreenshotRunnable);
                    }
                });

        mHandler = new PolicyHandler();
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerTorchCallback(new TorchModeCallback(), mHandler);
        mWakeGestureListener = new MyWakeGestureListener(mContext, mHandler);
        mOrientationListener = new MyOrientationListener(mContext, mHandler);
        try {
            mOrientationListener.setCurrentRotation(windowManager.getDefaultDisplayRotation());
        } catch (RemoteException ex) { }

        // only for hwkey devices
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar)) {
            mKeyHandler = new HardkeyActionHandler(mContext, mHandler);
        }

        mSettingsObserver = new SettingsObserver(mHandler);
<<<<<<< HEAD
        //mSettingsObserver.observe();
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
        mShortcutManager = new ShortcutManager(context);
        mUiMode = context.getResources().getInteger(
                com.android.internal.R.integer.config_defaultUiModeType);
        mHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mEnableCarDockHomeCapture = context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableCarDockHomeLaunch);
        mCarDockIntent =  new Intent(Intent.ACTION_MAIN, null);
        mCarDockIntent.addCategory(Intent.CATEGORY_CAR_DOCK);
        mCarDockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mDeskDockIntent =  new Intent(Intent.ACTION_MAIN, null);
        mDeskDockIntent.addCategory(Intent.CATEGORY_DESK_DOCK);
        mDeskDockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mVrHeadsetHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mVrHeadsetHomeIntent.addCategory(Intent.CATEGORY_VR_HOME);
        mVrHeadsetHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        mPowerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mBroadcastWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneWindowManager.mBroadcastWakeLock");
        mPowerKeyWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneWindowManager.mPowerKeyWakeLock");
        mEnableShiftMenuBugReports = "1".equals(SystemProperties.get("ro.debuggable"));
        mSupportAutoRotation = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportAutoRotation);
        mLidOpenRotation = readRotation(
                com.android.internal.R.integer.config_lidOpenRotation);
        mCarDockRotation = readRotation(
                com.android.internal.R.integer.config_carDockRotation);
        mDeskDockRotation = readRotation(
                com.android.internal.R.integer.config_deskDockRotation);
        mUndockedHdmiRotation = readRotation(
                com.android.internal.R.integer.config_undockedHdmiRotation);
        mCarDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_carDockEnablesAccelerometer);
        mDeskDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_deskDockEnablesAccelerometer);
        mLidKeyboardAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidKeyboardAccessibility);
        mLidNavigationAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidNavigationAccessibility);
        mLidControlsScreenLock = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_lidControlsScreenLock);
        mLidControlsSleep = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_lidControlsSleep);
        mTranslucentDecorEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableTranslucentDecor);

        mAllowTheaterModeWakeFromKey = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromKey);
        mAllowTheaterModeWakeFromPowerKey = mAllowTheaterModeWakeFromKey
                || mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_allowTheaterModeWakeFromPowerKey);
        mAllowTheaterModeWakeFromMotion = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromMotion);
        mAllowTheaterModeWakeFromMotionWhenNotDreaming = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromMotionWhenNotDreaming);
        mAllowTheaterModeWakeFromCameraLens = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromCameraLens);
        mAllowTheaterModeWakeFromLidSwitch = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromLidSwitch);
        mAllowTheaterModeWakeFromWakeGesture = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromGesture);

        mGoToSleepOnButtonPressTheaterMode = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_goToSleepOnButtonPressTheaterMode);

        mSupportLongPressPowerWhenNonInteractive = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportLongPressPowerWhenNonInteractive);

        mLongPressOnBackBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_longPressOnBackBehavior);
        mPanicPressOnBackBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_backPanicBehavior);

        mShortPressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_shortPressOnPowerBehavior);
        mLongPressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_longPressOnPowerBehavior);
        mDoublePressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_doublePressOnPowerBehavior);
        mTriplePressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_triplePressOnPowerBehavior);
        mShortPressOnSleepBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_shortPressOnSleepBehavior);

        mUseTvRouting = AudioSystem.getPlatformType(mContext) == AudioSystem.PLATFORM_TELEVISION;

        mDeviceHardwareKeys = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);

        mHandleVolumeKeysInWM = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_handleVolumeKeysInWindowManager);

        mDeviceHardwareKeys = mContext.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceHardwareKeys);
        mBackKillTimeout = mContext.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_backKillTimeout);

        updateKeyAssignments();

        mAccessibilityManager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);

        // register for dock events
        IntentFilter filter = new IntentFilter();
        filter.addAction(UiModeManager.ACTION_ENTER_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_ENTER_DESK_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_DESK_MODE);
        filter.addAction(Intent.ACTION_DOCK_EVENT);
        Intent intent = context.registerReceiver(mDockReceiver, filter);
        if (intent != null) {
            // Retrieve current sticky dock event broadcast.
            mDockMode = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                    Intent.EXTRA_DOCK_STATE_UNDOCKED);
        }

        // register for dream-related broadcasts
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        context.registerReceiver(mDreamReceiver, filter);

        // register for multiuser-relevant broadcasts
        filter = new IntentFilter(Intent.ACTION_USER_SWITCHED);
        context.registerReceiver(mMultiuserReceiver, filter);

        // register for screenshot
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREENSHOT);
        context.registerReceiver(mPowerMenuReceiver, filter);

        // monitor for system gestures
        mSystemGestures = new SystemGesturesPointerEventListener(context,
                new SystemGesturesPointerEventListener.Callbacks() {
                    @Override
                    public void onSwipeFromTop() {
                        if (mStatusBar != null) {
                            requestTransientBars(mStatusBar);
                        }
                    }
                    @Override
                    public void onSwipeFromBottom() {
                        if (mNavigationBar != null && mNavigationBarPosition == NAV_BAR_BOTTOM
                                && !immersiveModeImplementsPie()) {
                            requestTransientBars(mNavigationBar);
                        }
                    }
                    @Override
                    public void onSwipeFromRight() {
                        if (mNavigationBar != null && mNavigationBarPosition == NAV_BAR_RIGHT
                                && !immersiveModeImplementsPie()) {
                            requestTransientBars(mNavigationBar);
                        }
                    }
                    @Override
                    public void onSwipeFromLeft() {
                        if (mNavigationBar != null && mNavigationBarPosition == NAV_BAR_LEFT
                                && !immersiveModeImplementsPie()) {
                            requestTransientBars(mNavigationBar);
                        }
                    }
                    @Override
                    public void onFling(int duration) {
                        if (mPowerManagerInternal != null) {
                            mPowerManagerInternal.powerHint(
                                    PowerHint.INTERACTION, duration);
                        }
                    }
                    @Override
                    public void onDebug() {
                        // no-op
                    }
                    @Override
                    public void onDown() {
                        mOrientationListener.onTouchStart();
                    }
                    @Override
                    public void onUpOrCancel() {
                        mOrientationListener.onTouchEnd();
                    }
                    @Override
                    public void onMouseHoverAtTop() {
                        mHandler.removeMessages(MSG_REQUEST_TRANSIENT_BARS);
                        Message msg = mHandler.obtainMessage(MSG_REQUEST_TRANSIENT_BARS);
                        msg.arg1 = MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS;
                        mHandler.sendMessageDelayed(msg, 500);
                    }
                    @Override
                    public void onMouseHoverAtBottom() {
                        mHandler.removeMessages(MSG_REQUEST_TRANSIENT_BARS);
                        Message msg = mHandler.obtainMessage(MSG_REQUEST_TRANSIENT_BARS);
                        msg.arg1 = MSG_REQUEST_TRANSIENT_BARS_ARG_NAVIGATION;
                        mHandler.sendMessageDelayed(msg, 500);
                    }
                    @Override
                    public void onMouseLeaveFromEdge() {
                        mHandler.removeMessages(MSG_REQUEST_TRANSIENT_BARS);
                    }
                });
        mImmersiveModeConfirmation = new ImmersiveModeConfirmation(mContext);
        mWindowManagerFuncs.registerPointerEventListener(mSystemGestures);

        mVibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        mLongPressVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_longPressVibePattern);
        mCalendarDateVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_calendarDateVibePattern);
        mSafeModeEnabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeEnabledVibePattern);

        mScreenshotChordEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableScreenshotChord);

        mScreenrecordChordEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableScreenrecordChord);

        mGlobalKeyManager = new GlobalKeyManager(mContext);

        // Controls rotation and the like.
        initializeHdmiState();

        // Match current screen state.
        if (!mPowerManager.isInteractive()) {
            startedGoingToSleep(WindowManagerPolicy.OFF_BECAUSE_OF_USER);
            finishedGoingToSleep(WindowManagerPolicy.OFF_BECAUSE_OF_USER);
        }

        mWindowManagerInternal.registerAppTransitionListener(
                mStatusBarController.getAppTransitionListener());
        mWindowManagerInternal.registerAppTransitionListener(new AppTransitionListener() {
            @Override
            public int onAppTransitionStartingLocked(int transit, IBinder openToken,
                    IBinder closeToken,
                    Animation openAnimation, Animation closeAnimation) {
                return handleStartTransitionForKeyguardLw(transit, openAnimation);
            }

            @Override
            public void onAppTransitionCancelledLocked(int transit) {
                handleStartTransitionForKeyguardLw(transit, null /* transit */);
            }
        });
        mKeyguardDelegate = new KeyguardServiceDelegate(mContext,
                new StateCallback() {
                    @Override
                    public void onTrustedChanged() {
                        mWindowManagerFuncs.notifyKeyguardTrustedChanged();
                    }
                });
<<<<<<< HEAD

        final Resources res = mContext.getResources();
        final String[] deviceKeyHandlerLibs = res.getStringArray(
                org.lineageos.platform.internal.R.array.config_deviceKeyHandlerLibs);
        final String[] deviceKeyHandlerClasses = res.getStringArray(
                org.lineageos.platform.internal.R.array.config_deviceKeyHandlerClasses);

        for (int i = 0;
                i < deviceKeyHandlerLibs.length && i < deviceKeyHandlerClasses.length; i++) {
            try {
                PathClassLoader loader = new PathClassLoader(
                        deviceKeyHandlerLibs[i], getClass().getClassLoader());
                Class<?> klass = loader.loadClass(deviceKeyHandlerClasses[i]);
                Constructor<?> constructor = klass.getConstructor(Context.class);
                mDeviceKeyHandlers.add((DeviceKeyHandler) constructor.newInstance(mContext));
            } catch (Exception e) {
                Slog.w(TAG, "Could not instantiate device key handler "
                        + deviceKeyHandlerLibs[i] + " from class "
                        + deviceKeyHandlerClasses[i], e);
            }
        }
        if (DEBUG) Slog.d(TAG, "" + mDeviceKeyHandlers.size() + " device key handlers loaded");

        // Register for torch off events
        BroadcastReceiver torchReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTorchOffPendingIntent = null;
                if (mTorchEnabled) {
                    mHandler.removeMessages(MSG_TOGGLE_TORCH);
                    Message msg = mHandler.obtainMessage(MSG_TOGGLE_TORCH);
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
                }
            }
        };
        filter = new IntentFilter();
        filter.addAction(ACTION_TORCH_OFF);
        context.registerReceiver(torchReceiver, filter);
    }
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017

        final Resources res = mContext.getResources();
        final String[] deviceKeyHandlerLibs = res.getStringArray(
                org.lineageos.platform.internal.R.array.config_deviceKeyHandlerLibs);
        final String[] deviceKeyHandlerClasses = res.getStringArray(
                org.lineageos.platform.internal.R.array.config_deviceKeyHandlerClasses);

        for (int i = 0;
                i < deviceKeyHandlerLibs.length && i < deviceKeyHandlerClasses.length; i++) {
            try {
                PathClassLoader loader = new PathClassLoader(
                        deviceKeyHandlerLibs[i], getClass().getClassLoader());
                Class<?> klass = loader.loadClass(deviceKeyHandlerClasses[i]);
                Constructor<?> constructor = klass.getConstructor(Context.class);
                mDeviceKeyHandlers.add((DeviceKeyHandler) constructor.newInstance(mContext));
            } catch (Exception e) {
                Slog.w(TAG, "Could not instantiate device key handler "
                        + deviceKeyHandlerLibs[i] + " from class "
                        + deviceKeyHandlerClasses[i], e);
            }
        }
        if (DEBUG) Slog.d(TAG, "" + mDeviceKeyHandlers.size() + " device key handlers loaded");

        // Register for torch off events
        BroadcastReceiver torchReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTorchOffPendingIntent = null;
                if (mTorchEnabled) {
                    mHandler.removeMessages(MSG_TOGGLE_TORCH);
                    Message msg = mHandler.obtainMessage(MSG_TOGGLE_TORCH);
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
                }
            }
        };
        filter = new IntentFilter();
        filter.addAction(ACTION_TORCH_OFF);
        context.registerReceiver(torchReceiver, filter);
    }

    private void updateKeyAssignments() {
        int activeHardwareKeys = mDeviceHardwareKeys;

        if (mDevForceNavbar == 1) {
            activeHardwareKeys = 0;
        }

        final boolean hasMenu = (activeHardwareKeys & KEY_MASK_MENU) != 0;
        final boolean hasAssist = (activeHardwareKeys & KEY_MASK_ASSIST) != 0;
        final boolean hasAppSwitch = (activeHardwareKeys & KEY_MASK_APP_SWITCH) != 0;

        final ContentResolver resolver = mContext.getContentResolver();
        final Resources res = mContext.getResources();

        // Initialize all assignments to sane defaults.
        mMenuPressAction = Action.MENU;

        mMenuLongPressAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_longPressOnMenuBehavior));

        if (mMenuLongPressAction == Action.NOTHING &&
               (hasMenu && !hasAssist)) {
            mMenuLongPressAction = Action.SEARCH;
        }
        mAssistPressAction = Action.SEARCH;
        mAssistLongPressAction = Action.VOICE_SEARCH;
        mAppSwitchPressAction = Action.APP_SWITCH;
        mAppSwitchLongPressAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_longPressOnAppSwitchBehavior));

        mHomeLongPressAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_longPressOnHomeBehavior));
        if (mHomeLongPressAction.ordinal() > Action.SLEEP.ordinal()) {
            mHomeLongPressAction = Action.NOTHING;
        }

        mHomeDoubleTapAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_doubleTapOnHomeBehavior));
        if (mHomeDoubleTapAction.ordinal() > Action.SLEEP.ordinal()) {
            mHomeDoubleTapAction = Action.NOTHING;
        }

        mHomeLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                mHomeLongPressAction);
        mHomeDoubleTapAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                mHomeDoubleTapAction);

        if (hasMenu) {
            mMenuPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_MENU_ACTION,
                    mMenuPressAction);
            mMenuLongPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION,
                    mMenuLongPressAction);
        }
        if (hasAssist) {
            mAssistPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_ASSIST_ACTION,
                    mAssistPressAction);
            mAssistLongPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION,
                    mAssistLongPressAction);
        }
        if (hasAppSwitch) {
            mAppSwitchPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_APP_SWITCH_ACTION,
                    mAppSwitchPressAction);
        }
        mAppSwitchLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
                mAppSwitchLongPressAction);

        mShortPressWindowBehavior = SHORT_PRESS_WINDOW_NOTHING;
        if (mContext.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            mShortPressWindowBehavior = SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE;
        }

        mNavBarOpacityMode = res.getInteger(
                com.android.internal.R.integer.config_navBarOpacityMode);
    }

     private void enableSwipeThreeFingerGesture(boolean enable){
        if (enable) {
            if (haveEnableGesture) return;
            haveEnableGesture = true;
            mWindowManagerFuncs.registerPointerEventListener(mOPGestures);
        } else {
            if (!haveEnableGesture) return;
            haveEnableGesture = false;
            mWindowManagerFuncs.unregisterPointerEventListener(mOPGestures);
        }
    }

    @Override
    public void setInitialDisplaySize(Display display, int width, int height, int density) {
        // This method might be called before the policy has been fully initialized
        // or for other displays we don't care about.
        // TODO(multi-display): Define policy for secondary displays.
        if (mContext == null || display.getDisplayId() != DEFAULT_DISPLAY) {
            return;
        }
        mDisplay = display;

        final Resources res = mContext.getResources();
        int shortSize, longSize;
        if (width > height) {
            shortSize = height;
            longSize = width;
            mLandscapeRotation = Surface.ROTATION_0;
            mSeascapeRotation = Surface.ROTATION_180;
            if (res.getBoolean(com.android.internal.R.bool.config_reverseDefaultRotation)) {
                mPortraitRotation = Surface.ROTATION_90;
                mUpsideDownRotation = Surface.ROTATION_270;
            } else {
                mPortraitRotation = Surface.ROTATION_270;
                mUpsideDownRotation = Surface.ROTATION_90;
            }
        } else {
            shortSize = width;
            longSize = height;
            mPortraitRotation = Surface.ROTATION_0;
            mUpsideDownRotation = Surface.ROTATION_180;
            if (res.getBoolean(com.android.internal.R.bool.config_reverseDefaultRotation)) {
                mLandscapeRotation = Surface.ROTATION_270;
                mSeascapeRotation = Surface.ROTATION_90;
            } else {
                mLandscapeRotation = Surface.ROTATION_90;
                mSeascapeRotation = Surface.ROTATION_270;
            }
        }

        updateNavigationBarSize();

        // SystemUI (status bar) layout policy
        int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / density;
        int longSizeDp = longSize * DisplayMetrics.DENSITY_DEFAULT / density;

        // Allow the navigation bar to move on non-square small devices (phones).
        mNavigationBarCanMove = width != height && shortSizeDp < 600;

        // For demo purposes, allow the rotation of the HDMI display to be controlled.
        // By default, HDMI locks rotation to landscape.
        if ("portrait".equals(SystemProperties.get("persist.demo.hdmirotation"))) {
            mDemoHdmiRotation = mPortraitRotation;
        } else {
            mDemoHdmiRotation = mLandscapeRotation;
        }
        mDemoHdmiRotationLock = SystemProperties.getBoolean("persist.demo.hdmirotationlock", false);

        // For demo purposes, allow the rotation of the remote display to be controlled.
        // By default, remote display locks rotation to landscape.
        if ("portrait".equals(SystemProperties.get("persist.demo.remoterotation"))) {
            mDemoRotation = mPortraitRotation;
        } else {
            mDemoRotation = mLandscapeRotation;
        }
        mDemoRotationLock = SystemProperties.getBoolean(
                "persist.demo.rotationlock", false);

        // Only force the default orientation if the screen is xlarge, at least 960dp x 720dp, per
        // http://developer.android.com/guide/practices/screens_support.html#range
        mForceDefaultOrientation = longSizeDp >= 960 && shortSizeDp >= 720 &&
                res.getBoolean(com.android.internal.R.bool.config_forceDefaultOrientation) &&
                // For debug purposes the next line turns this feature off with:
                // $ adb shell setprop config.override_forced_orient true
                // $ adb shell wm size reset
                !"true".equals(SystemProperties.get("config.override_forced_orient"));
    }

    /**
     * @return whether the navigation bar can be hidden, e.g. the device has a
     *         navigation bar and touch exploration is not enabled
     */
    private boolean canHideNavigationBar() {
<<<<<<< HEAD
        return hasNavigationBar()
                && !mAccessibilityManager.isTouchExplorationEnabled();
=======
        return hasNavigationBar();
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    }

    @Override
    public boolean isDefaultOrientationForced() {
        return mForceDefaultOrientation;
    }

    @Override
    public void setDisplayOverscan(Display display, int left, int top, int right, int bottom) {
        // TODO(multi-display): Define policy for secondary displays.
        if (display.getDisplayId() == DEFAULT_DISPLAY) {
            mOverscanLeft = left;
            mOverscanTop = top;
            mOverscanRight = right;
            mOverscanBottom = bottom;
        }
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        boolean updateRotation = false;
        int mDeviceHardwareWakeKeys = mContext.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceHardwareWakeKeys);
        synchronized (mLock) {
            mEndcallBehavior = Settings.System.getIntForUser(resolver,
                    Settings.System.END_BUTTON_BEHAVIOR,
                    Settings.System.END_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.USER_CURRENT);
            mIncallPowerBehavior = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.USER_CURRENT);
            mIncallBackBehavior = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.INCALL_BACK_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_BACK_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.USER_CURRENT);
            mRingHomeBehavior = LineageSettings.Secure.getIntForUser(resolver,
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.USER_CURRENT);
           mSystemNavigationKeysEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.SYSTEM_NAVIGATION_KEYS_ENABLED, 0,
                    UserHandle.USER_CURRENT) == 1;
            mTorchLongPressPowerEnabled = LineageSettings.System.getIntForUser(
                    resolver, LineageSettings.System.TORCH_LONG_PRESS_POWER_GESTURE, 0,
                    UserHandle.USER_CURRENT) == 1;
            mTorchTimeout = LineageSettings.System.getIntForUser(
                    resolver, LineageSettings.System.TORCH_LONG_PRESS_POWER_TIMEOUT, 0,
                    UserHandle.USER_CURRENT);
            mHomeWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.HOME_WAKE_SCREEN, 1, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_HOME) != 0);
            mBackWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BACK_WAKE_SCREEN, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_BACK) != 0);
            mMenuWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.MENU_WAKE_SCREEN, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_MENU) != 0);
            mAssistWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.ASSIST_WAKE_SCREEN, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_ASSIST) != 0);
            mAppSwitchWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.APP_SWITCH_WAKE_SCREEN, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_APP_SWITCH) != 0);
            mVolumeWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.VOLUME_WAKE_SCREEN, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_VOLUME) != 0);
            mVolumeAnswerCall = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.VOLUME_ANSWER_CALL, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_VOLUME) != 0);
            mCameraWakeScreen = (LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.CAMERA_WAKE_SCREEN, 0, UserHandle.USER_CURRENT) == 1)
                    && ((mDeviceHardwareWakeKeys & KEY_MASK_CAMERA) != 0);
            mCameraSleepOnRelease = LineageSettings.System.getIntForUser(
                    resolver, LineageSettings.System.CAMERA_SLEEP_ON_RELEASE, 0,
                    UserHandle.USER_CURRENT) == 1;
            mCameraLaunch = LineageSettings.System.getIntForUser(
                    resolver, LineageSettings.System.CAMERA_LAUNCH, 0,
                    UserHandle.USER_CURRENT) == 1;
<<<<<<< HEAD

            mPieState = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.PIE_STATE, 0, UserHandle.USER_CURRENT) == 1;
=======
            mKillAppLongpressBack = LineageSettings.Secure.getInt(
                    resolver, LineageSettings.Secure.KILL_APP_LONGPRESS_BACK, 0) == 1;
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017

            // Configure wake gesture.
            boolean wakeGestureEnabledSetting = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.WAKE_GESTURE_ENABLED, 0,
                    UserHandle.USER_CURRENT) != 0;
            if (mWakeGestureEnabledSetting != wakeGestureEnabledSetting) {
                mWakeGestureEnabledSetting = wakeGestureEnabledSetting;
                updateWakeGestureListenerLp();
            }

<<<<<<< HEAD
	          //Three Finger Gesture
            boolean threeFingerGesture = Settings.System.getIntForUser(resolver,
                    Settings.System.THREE_FINGER_GESTURE, 0, UserHandle.USER_CURRENT) == 1;
            enableSwipeThreeFingerGesture(threeFingerGesture);
=======
            int devForceNavbar = LineageSettings.Global.getIntForUser(resolver,
                    LineageSettings.Global.DEV_FORCE_SHOW_NAVBAR, 0, UserHandle.USER_CURRENT);
            if (devForceNavbar != mDevForceNavbar) {
                mDevForceNavbar = devForceNavbar;
                if (mLineageHardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE)) {
                    mLineageHardware.set(LineageHardwareManager.FEATURE_KEY_DISABLE,
                            mDevForceNavbar == 1);
                }
            }

            updateKeyAssignments();
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017

            // Configure rotation lock.
            int userRotation = Settings.System.getIntForUser(resolver,
                    Settings.System.USER_ROTATION, Surface.ROTATION_0,
                    UserHandle.USER_CURRENT);
            if (mUserRotation != userRotation) {
                mUserRotation = userRotation;
                updateRotation = true;
            }
            int userRotationMode = Settings.System.getIntForUser(resolver,
                    Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT) != 0 ?
                            WindowManagerPolicy.USER_ROTATION_FREE :
                                    WindowManagerPolicy.USER_ROTATION_LOCKED;
            if (mUserRotationMode != userRotationMode) {
                mUserRotationMode = userRotationMode;
                updateRotation = true;
                updateOrientationListenerLp();
            }

            mUserRotationAngles = LineageSettings.System.getInt(resolver,
                    LineageSettings.System.ACCELEROMETER_ROTATION_ANGLES, -1);

<<<<<<< HEAD
            final boolean useEdgeService = Settings.System.getIntForUser(resolver,
                    Settings.System.USE_EDGE_SERVICE_FOR_GESTURES, 1, UserHandle.USER_CURRENT) == 1;
            if (useEdgeService ^ mUsingEdgeGestureServiceForGestures && mSystemReady) {
                if (!mUsingEdgeGestureServiceForGestures && useEdgeService) {
                    mUsingEdgeGestureServiceForGestures = true;
                    mWindowManagerFuncs.unregisterPointerEventListener(mSystemGestures);
                } else if (mUsingEdgeGestureServiceForGestures && !useEdgeService) {
                    mUsingEdgeGestureServiceForGestures = false;
                    mWindowManagerFuncs.registerPointerEventListener(mSystemGestures);
                }
                updateEdgeGestureListenerState();
            }

=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
            if (mSystemReady) {
                int pointerLocation = Settings.System.getIntForUser(resolver,
                        Settings.System.POINTER_LOCATION, 0, UserHandle.USER_CURRENT);
                if (mPointerLocationMode != pointerLocation) {
                    mPointerLocationMode = pointerLocation;
                    mHandler.sendEmptyMessage(pointerLocation != 0 ?
                            MSG_ENABLE_POINTER_LOCATION : MSG_DISABLE_POINTER_LOCATION);
                }
            }

            final boolean ANBIEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.ANBI_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
            if (mANBIHandler != null) {
                if (mANBIEnabled != ANBIEnabled) {
                    mANBIEnabled = ANBIEnabled;
                    if (mANBIEnabled) {
                        mWindowManagerFuncs.registerPointerEventListener(mANBIHandler);
                    } else {
                        mWindowManagerFuncs.unregisterPointerEventListener(mANBIHandler);
                    }
                }
            }

            // use screen off timeout setting as the timeout for the lockscreen
            mLockScreenTimeout = Settings.System.getIntForUser(resolver,
                    Settings.System.SCREEN_OFF_TIMEOUT, 0, UserHandle.USER_CURRENT);
            String imId = Settings.Secure.getStringForUser(resolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD, UserHandle.USER_CURRENT);
            boolean hasSoftInput = imId != null && imId.length() > 0;
            if (mHasSoftInput != hasSoftInput) {
                mHasSoftInput = hasSoftInput;
                updateRotation = true;
            }
            if (mImmersiveModeConfirmation != null) {
                mImmersiveModeConfirmation.loadSetting(mCurrentUserId);
            }
            boolean doShowNavbar = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_VISIBLE,
                    DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0,
                    UserHandle.USER_CURRENT) == 1;
            if (doShowNavbar != mNavbarVisible) {
                mNavbarVisible = doShowNavbar;
            }
            updateNavigationBarSize();
        }

        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
            WindowManagerPolicyControl.reloadFromSetting(mContext);
        }
        if (updateRotation) {
            updateRotation(true);
        }
        
    }

    private void updateNavigationBarSize() {
        final Resources res = mContext.getResources();
        int navbarHeightDef = res
                .getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height);
        int navbarHeightScale = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_HEIGHT, 100, UserHandle.USER_CURRENT);
        int navbarHeightVal = getScaledNavbarSize(navbarHeightScale, navbarHeightDef);
        mNavigationBarHeightForRotationDefault[mPortraitRotation] = mNavigationBarHeightForRotationDefault[mUpsideDownRotation] = navbarHeightVal;

        int navbarHeightLandDef = res.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height_landscape);
        int navbarHeightLandScale = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, 100, UserHandle.USER_CURRENT);
        int navbarHeightLandVal = getScaledNavbarSize(navbarHeightLandScale, navbarHeightLandDef);
        mNavigationBarHeightForRotationDefault[mLandscapeRotation] = mNavigationBarHeightForRotationDefault[mSeascapeRotation] = navbarHeightLandVal;

        // Width of the navigation bar when presented vertically along one
        // side
        int navbarWidthDef = res
                .getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_width);
        int navbarWidthScale = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_WIDTH, 100, UserHandle.USER_CURRENT);
        int navbarWidthVal = getScaledNavbarSize(navbarWidthScale, navbarWidthDef);
        mNavigationBarWidthForRotationDefault[mPortraitRotation] = mNavigationBarWidthForRotationDefault[mUpsideDownRotation] = mNavigationBarWidthForRotationDefault[mLandscapeRotation] = mNavigationBarWidthForRotationDefault[mSeascapeRotation] = navbarWidthVal;
    }

    private int getScaledNavbarSize(int percentage, int defaultDimen) {
        return Math.round(defaultDimen * (percentage * 0.01f));
    }

    private void updateWakeGestureListenerLp() {
        if (shouldEnableWakeGestureLp()) {
            mWakeGestureListener.requestWakeUpTrigger();
        } else {
            mWakeGestureListener.cancelWakeUpTrigger();
        }
    }

    private boolean shouldEnableWakeGestureLp() {
        return mWakeGestureEnabledSetting && !mAwake
                && (!mLidControlsSleep || mLidState != LID_CLOSED)
                && mWakeGestureListener.isSupported();
    }

    private void enablePointerLocation() {
        if (mPointerLocationView == null) {
            mPointerLocationView = new PointerLocationView(mContext);
            mPointerLocationView.setPrintCoords(false);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            lp.type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
            lp.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            if (ActivityManager.isHighEndGfx()) {
                lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                lp.privateFlags |=
                        WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
            }
            lp.format = PixelFormat.TRANSLUCENT;
            lp.setTitle("PointerLocation");
            WindowManager wm = (WindowManager) mContext.getSystemService(WINDOW_SERVICE);
            lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
            wm.addView(mPointerLocationView, lp);
            mWindowManagerFuncs.registerPointerEventListener(mPointerLocationView);
        }
    }

    private void disablePointerLocation() {
        if (mPointerLocationView != null) {
            mWindowManagerFuncs.unregisterPointerEventListener(mPointerLocationView);
            WindowManager wm = (WindowManager) mContext.getSystemService(WINDOW_SERVICE);
            wm.removeView(mPointerLocationView);
            mPointerLocationView = null;
        }
    }

    private int readRotation(int resID) {
        try {
            int rotation = mContext.getResources().getInteger(resID);
            switch (rotation) {
                case 0:
                    return Surface.ROTATION_0;
                case 90:
                    return Surface.ROTATION_90;
                case 180:
                    return Surface.ROTATION_180;
                case 270:
                    return Surface.ROTATION_270;
            }
        } catch (Resources.NotFoundException e) {
            // fall through
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public int checkAddPermission(WindowManager.LayoutParams attrs, int[] outAppOp) {
        int type = attrs.type;

        outAppOp[0] = AppOpsManager.OP_NONE;

        if (!((type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW)
                || (type >= FIRST_SUB_WINDOW && type <= LAST_SUB_WINDOW)
                || (type >= FIRST_SYSTEM_WINDOW && type <= LAST_SYSTEM_WINDOW))) {
            return WindowManagerGlobal.ADD_INVALID_TYPE;
        }

        if (type < FIRST_SYSTEM_WINDOW || type > LAST_SYSTEM_WINDOW) {
            // Window manager will make sure these are okay.
            return ADD_OKAY;
        }

        if (!isSystemAlertWindowType(type)) {
            switch (type) {
                case TYPE_TOAST:
                    // Only apps that target older than O SDK can add window without a token, after
                    // that we require a token so apps cannot add toasts directly as the token is
                    // added by the notification system.
                    // Window manager does the checking for this.
                    outAppOp[0] = OP_TOAST_WINDOW;
                    return ADD_OKAY;
                case TYPE_DREAM:
                case TYPE_INPUT_METHOD:
                case TYPE_WALLPAPER:
                case TYPE_PRESENTATION:
                case TYPE_PRIVATE_PRESENTATION:
                case TYPE_VOICE_INTERACTION:
                case TYPE_ACCESSIBILITY_OVERLAY:
                case TYPE_QS_DIALOG:
                    // The window manager will check these.
                    return ADD_OKAY;
            }
            return mContext.checkCallingOrSelfPermission(INTERNAL_SYSTEM_WINDOW)
                    == PERMISSION_GRANTED ? ADD_OKAY : ADD_PERMISSION_DENIED;
        }

        // Things get a little more interesting for alert windows...
        outAppOp[0] = OP_SYSTEM_ALERT_WINDOW;

        final int callingUid = Binder.getCallingUid();
        // system processes will be automatically granted privilege to draw
        if (UserHandle.getAppId(callingUid) == Process.SYSTEM_UID) {
            return ADD_OKAY;
        }

        ApplicationInfo appInfo;
        try {
            appInfo = mContext.getPackageManager().getApplicationInfoAsUser(
                            attrs.packageName,
                            0 /* flags */,
                            UserHandle.getUserId(callingUid));
        } catch (PackageManager.NameNotFoundException e) {
            appInfo = null;
        }

        if (appInfo == null || (type != TYPE_APPLICATION_OVERLAY && appInfo.targetSdkVersion >= O)) {
            /**
             * Apps targeting >= {@link Build.VERSION_CODES#O} are required to hold
             * {@link android.Manifest.permission#INTERNAL_SYSTEM_WINDOW} (system signature apps)
             * permission to add alert windows that aren't
             * {@link android.view.WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY}.
             */
            return (mContext.checkCallingOrSelfPermission(INTERNAL_SYSTEM_WINDOW)
                    == PERMISSION_GRANTED) ? ADD_OKAY : ADD_PERMISSION_DENIED;
        }

        // check if user has enabled this operation. SecurityException will be thrown if this app
        // has not been allowed by the user
        final int mode = mAppOpsManager.checkOpNoThrow(outAppOp[0], callingUid, attrs.packageName);
        switch (mode) {
            case AppOpsManager.MODE_ALLOWED:
            case AppOpsManager.MODE_IGNORED:
                // although we return ADD_OKAY for MODE_IGNORED, the added window will
                // actually be hidden in WindowManagerService
                return ADD_OKAY;
            case AppOpsManager.MODE_ERRORED:
                // Don't crash legacy apps
                if (appInfo.targetSdkVersion < M) {
                    return ADD_OKAY;
                }
                return ADD_PERMISSION_DENIED;
            default:
                // in the default mode, we will make a decision here based on
                // checkCallingPermission()
                return (mContext.checkCallingOrSelfPermission(SYSTEM_ALERT_WINDOW)
                        == PERMISSION_GRANTED) ? ADD_OKAY : ADD_PERMISSION_DENIED;
        }
    }

    @Override
    public boolean checkShowToOwnerOnly(WindowManager.LayoutParams attrs) {

        // If this switch statement is modified, modify the comment in the declarations of
        // the type in {@link WindowManager.LayoutParams} as well.
        switch (attrs.type) {
            default:
                // These are the windows that by default are shown only to the user that created
                // them. If this needs to be overridden, set
                // {@link WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS} in
                // {@link WindowManager.LayoutParams}. Note that permission
                // {@link android.Manifest.permission.INTERNAL_SYSTEM_WINDOW} is required as well.
                if ((attrs.privateFlags & PRIVATE_FLAG_SHOW_FOR_ALL_USERS) == 0) {
                    return true;
                }
                break;

            // These are the windows that by default are shown to all users. However, to
            // protect against spoofing, check permissions below.
            case TYPE_APPLICATION_STARTING:
            case TYPE_BOOT_PROGRESS:
            case TYPE_DISPLAY_OVERLAY:
            case TYPE_INPUT_CONSUMER:
            case TYPE_KEYGUARD_DIALOG:
            case TYPE_MAGNIFICATION_OVERLAY:
            case TYPE_NAVIGATION_BAR:
            case TYPE_NAVIGATION_BAR_PANEL:
            case TYPE_PHONE:
            case TYPE_POINTER:
            case TYPE_PRIORITY_PHONE:
            case TYPE_SEARCH_BAR:
            case TYPE_STATUS_BAR:
            case TYPE_STATUS_BAR_PANEL:
            case TYPE_STATUS_BAR_SUB_PANEL:
            case TYPE_SYSTEM_DIALOG:
            case TYPE_SLIM_RECENTS:
            case TYPE_VOLUME_OVERLAY:
            case TYPE_PRESENTATION:
            case TYPE_PRIVATE_PRESENTATION:
            case TYPE_DOCK_DIVIDER:
                break;
        }

        // Check if third party app has set window to system window type.
        return mContext.checkCallingOrSelfPermission(INTERNAL_SYSTEM_WINDOW) != PERMISSION_GRANTED;
    }

    @Override
    public void adjustWindowParamsLw(WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_SYSTEM_OVERLAY:
            case TYPE_SECURE_SYSTEM_OVERLAY:
                // These types of windows can't receive input events.
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                break;
            case TYPE_STATUS_BAR:

                // If the Keyguard is in a hidden state (occluded by another window), we force to
                // remove the wallpaper and keyguard flag so that any change in-flight after setting
                // the keyguard as occluded wouldn't set these flags again.
                // See {@link #processKeyguardSetHiddenResultLw}.
                if (mKeyguardOccluded) {
                    attrs.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                    attrs.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
                }
                break;

            case TYPE_SCREENSHOT:
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                break;

            case TYPE_TOAST:
                // While apps should use the dedicated toast APIs to add such windows
                // it possible legacy apps to add the window directly. Therefore, we
                // make windows added directly by the app behave as a toast as much
                // as possible in terms of timeout and animation.
                if (attrs.hideTimeoutMilliseconds < 0
                        || attrs.hideTimeoutMilliseconds > TOAST_WINDOW_TIMEOUT) {
                    attrs.hideTimeoutMilliseconds = TOAST_WINDOW_TIMEOUT;
                }

                switch(Settings.System.getInt(mContext.getContentResolver(), Settings.System.TOAST_ANIMATION, 1)) {
                case 0:
                        attrs.windowAnimations = -1;
                        break;
                case 1:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast;
                        break;
                case 2:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_Fade;
                        break;
                case 3:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_SlideRight;
                        break;
                case 4:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_SlideLeft;
                        break;
                case 5:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_Xylon;
                        break;
                case 6:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_Toko;
                        break;
                case 7:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_Tn;
                        break;
                case 8:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_Honami;
                        break;
                case 9:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_FastFade;
                        break;
                case 10:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_GrowFade;
                        break;
                case 11:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_GrowFadeCenter;
                        break;
                case 12:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_GrowFadeBottom;
                        break;
                case 13:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_Translucent;
                        break;
                case 14:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_SlideLeftRight;
                        break;
                case 15:
                        attrs.windowAnimations = com.android.internal.R.style.Animation_Toast_SlideRightLeft;
                        break;
        }
    }

        if (attrs.type != TYPE_STATUS_BAR) {
            // The status bar is the only window allowed to exhibit keyguard behavior.
            attrs.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
        }

        if ((attrs.flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0) {
            attrs.subtreeSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        final boolean forceWindowDrawsStatusBarBackground =
                (attrs.privateFlags & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) != 0;
        if ((attrs.flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0
                || forceWindowDrawsStatusBarBackground
                        && attrs.height == MATCH_PARENT && attrs.width == MATCH_PARENT) {
            attrs.subtreeSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
    }

    void readLidState() {
        mLidState = mWindowManagerFuncs.getLidState();
    }

    private void readCameraLensCoverState() {
        mCameraLensCoverState = mWindowManagerFuncs.getCameraLensCoverState();
    }

    private boolean isHidden(int accessibilityMode) {
        switch (accessibilityMode) {
            case 1:
                return mLidState == LID_CLOSED;
            case 2:
                return mLidState == LID_OPEN;
            default:
                return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void adjustConfigurationLw(Configuration config, int keyboardPresence,
            int navigationPresence) {
        mHaveBuiltInKeyboard = (keyboardPresence & PRESENCE_INTERNAL) != 0;

        readLidState();

        if (config.keyboard == Configuration.KEYBOARD_NOKEYS
                || (keyboardPresence == PRESENCE_INTERNAL
                        && isHidden(mLidKeyboardAccessibility))) {
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;
            if (!mHasSoftInput) {
                config.keyboardHidden = Configuration.KEYBOARDHIDDEN_YES;
            }
        }

        if (config.navigation == Configuration.NAVIGATION_NONAV
                || (navigationPresence == PRESENCE_INTERNAL
                        && isHidden(mLidNavigationAccessibility))) {
            config.navigationHidden = Configuration.NAVIGATIONHIDDEN_YES;
        }
    }

    @Override
    public void onConfigurationChanged() {
        // TODO(multi-display): Define policy for secondary displays.
        Context uiContext = ActivityThread.currentActivityThread().getSystemUiContext();
        final Resources res = uiContext.getResources();

        mStatusBarHeight =
                res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);

        updateNavigationBarSize();

        if (ALTERNATE_CAR_MODE_NAV_SIZE) {
            // Height of the navigation bar when presented horizontally at bottom
            mNavigationBarHeightForRotationInCarMode[mPortraitRotation] =
            mNavigationBarHeightForRotationInCarMode[mUpsideDownRotation] =
                    res.getDimensionPixelSize(
                            com.android.internal.R.dimen.navigation_bar_height_car_mode);
            mNavigationBarHeightForRotationInCarMode[mLandscapeRotation] =
            mNavigationBarHeightForRotationInCarMode[mSeascapeRotation] = res.getDimensionPixelSize(
                    com.android.internal.R.dimen.navigation_bar_height_landscape_car_mode);

            // Width of the navigation bar when presented vertically along one side
            mNavigationBarWidthForRotationInCarMode[mPortraitRotation] =
            mNavigationBarWidthForRotationInCarMode[mUpsideDownRotation] =
            mNavigationBarWidthForRotationInCarMode[mLandscapeRotation] =
            mNavigationBarWidthForRotationInCarMode[mSeascapeRotation] =
                    res.getDimensionPixelSize(
                            com.android.internal.R.dimen.navigation_bar_width_car_mode);
        }
    }

    @Override
    public int getMaxWallpaperLayer() {
        return getWindowLayerFromTypeLw(TYPE_STATUS_BAR);
    }

    private int getNavigationBarWidth(int rotation, int uiMode) {
        if (ALTERNATE_CAR_MODE_NAV_SIZE && (uiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_CAR) {
            return mNavigationBarWidthForRotationInCarMode[rotation];
        } else {
            return mNavigationBarWidthForRotationDefault[rotation];
        }
    }

    @Override
    public int getNonDecorDisplayWidth(int fullWidth, int fullHeight, int rotation, int uiMode,
            int displayId) {
        // TODO(multi-display): Support navigation bar on secondary displays.
        if (displayId == DEFAULT_DISPLAY && hasNavigationBar()) {
            // For a basic navigation bar, when we are in landscape mode we place
            // the navigation bar to the side.
            if (mNavigationBarCanMove && fullWidth > fullHeight) {
                return fullWidth - getNavigationBarWidth(rotation, uiMode);
            }
        }
        return fullWidth;
    }

    private int getNavigationBarHeight(int rotation, int uiMode) {
        if (ALTERNATE_CAR_MODE_NAV_SIZE && (uiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_CAR) {
            return mNavigationBarHeightForRotationInCarMode[rotation];
        } else {
            return mNavigationBarHeightForRotationDefault[rotation];
        }
    }

    @Override
    public int getNonDecorDisplayHeight(int fullWidth, int fullHeight, int rotation, int uiMode,
            int displayId) {
        // TODO(multi-display): Support navigation bar on secondary displays.
        if (displayId == DEFAULT_DISPLAY && hasNavigationBar()) {
            // For a basic navigation bar, when we are in portrait mode we place
            // the navigation bar to the bottom.
            if (!mNavigationBarCanMove || fullWidth < fullHeight) {
                return fullHeight - getNavigationBarHeight(rotation, uiMode);
            }
        }
        return fullHeight;
    }

    @Override
    public int getConfigDisplayWidth(int fullWidth, int fullHeight, int rotation, int uiMode,
            int displayId) {
        return getNonDecorDisplayWidth(fullWidth, fullHeight, rotation, uiMode, displayId);
    }

    @Override
    public int getConfigDisplayHeight(int fullWidth, int fullHeight, int rotation, int uiMode,
            int displayId) {
        // There is a separate status bar at the top of the display.  We don't count that as part
        // of the fixed decor, since it can hide; however, for purposes of configurations,
        // we do want to exclude it since applications can't generally use that part
        // of the screen.
        // TODO(multi-display): Support status bars on secondary displays.
        if (displayId == DEFAULT_DISPLAY) {
            return getNonDecorDisplayHeight(fullWidth, fullHeight, rotation, uiMode, displayId)
                    - mStatusBarHeight;
        }
        return fullHeight;
    }

    @Override
    public boolean isKeyguardHostWindow(WindowManager.LayoutParams attrs) {
        return attrs.type == TYPE_STATUS_BAR;
    }

    @Override
    public boolean canBeHiddenByKeyguardLw(WindowState win) {
        switch (win.getAttrs().type) {
            case TYPE_STATUS_BAR:
            case TYPE_NAVIGATION_BAR:
            case TYPE_WALLPAPER:
            case TYPE_DREAM:
                return false;
            default:
                // Hide only windows below the keyguard host window.
                return getWindowLayerLw(win) < getWindowLayerFromTypeLw(TYPE_STATUS_BAR);
        }
    }

    private boolean shouldBeHiddenByKeyguard(WindowState win, WindowState imeTarget) {

        // Keyguard visibility of window from activities are determined over activity visibility.
        if (win.getAppToken() != null) {
            return false;
        }

        final LayoutParams attrs = win.getAttrs();
        final boolean showImeOverKeyguard = imeTarget != null && imeTarget.isVisibleLw() &&
                ((imeTarget.getAttrs().flags & FLAG_SHOW_WHEN_LOCKED) != 0
                        || !canBeHiddenByKeyguardLw(imeTarget));

        // Show IME over the keyguard if the target allows it
        boolean allowWhenLocked = (win.isInputMethodWindow() || imeTarget == this)
                && showImeOverKeyguard;;

        if (isKeyguardLocked() && isKeyguardOccluded()) {
            // Show SHOW_WHEN_LOCKED windows if Keyguard is occluded.
            allowWhenLocked |= (attrs.flags & FLAG_SHOW_WHEN_LOCKED) != 0
                    // Show error dialogs over apps that are shown on lockscreen
                    || (attrs.privateFlags & PRIVATE_FLAG_SYSTEM_ERROR) != 0;
        }

        boolean keyguardLocked = isKeyguardLocked();
        boolean hideDockDivider = attrs.type == TYPE_DOCK_DIVIDER
                && !mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID);
        return (keyguardLocked && !allowWhenLocked && win.getDisplayId() == DEFAULT_DISPLAY)
                || hideDockDivider;
    }

    /** {@inheritDoc} */
    @Override
    public StartingSurface addSplashScreen(IBinder appToken, String packageName, int theme,
            CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes, int icon,
            int logo, int windowFlags, Configuration overrideConfig, int displayId) {
        if (!SHOW_SPLASH_SCREENS) {
            return null;
        }
        if (packageName == null) {
            return null;
        }

        WindowManager wm = null;
        View view = null;

        try {
            Context context = mContext;
            if (DEBUG_SPLASH_SCREEN) Slog.d(TAG, "addSplashScreen " + packageName
                    + ": nonLocalizedLabel=" + nonLocalizedLabel + " theme="
                    + Integer.toHexString(theme));

            // Obtain proper context to launch on the right display.
            final Context displayContext = getDisplayContext(context, displayId);
            if (displayContext == null) {
                // Can't show splash screen on requested display, so skip showing at all.
                return null;
            }
            context = displayContext;

            if (theme != context.getThemeResId() || labelRes != 0) {
                try {
                    context = context.createPackageContext(packageName, CONTEXT_RESTRICTED);
                    context.setTheme(theme);
                } catch (PackageManager.NameNotFoundException e) {
                    // Ignore
                }
            }

            if (overrideConfig != null && !overrideConfig.equals(EMPTY)) {
                if (DEBUG_SPLASH_SCREEN) Slog.d(TAG, "addSplashScreen: creating context based"
                        + " on overrideConfig" + overrideConfig + " for splash screen");
                final Context overrideContext = context.createConfigurationContext(overrideConfig);
                overrideContext.setTheme(theme);
                final TypedArray typedArray = overrideContext.obtainStyledAttributes(
                        com.android.internal.R.styleable.Window);
                final int resId = typedArray.getResourceId(R.styleable.Window_windowBackground, 0);
                if (resId != 0 && overrideContext.getDrawable(resId) != null) {
                    // We want to use the windowBackground for the override context if it is
                    // available, otherwise we use the default one to make sure a themed starting
                    // window is displayed for the app.
                    if (DEBUG_SPLASH_SCREEN) Slog.d(TAG, "addSplashScreen: apply overrideConfig"
                            + overrideConfig + " to starting window resId=" + resId);
                    context = overrideContext;
                }
                typedArray.recycle();
            }

            final PhoneWindow win = new PhoneWindow(context);
            win.setIsStartingWindow(true);

            CharSequence label = context.getResources().getText(labelRes, null);
            // Only change the accessibility title if the label is localized
            if (label != null) {
                win.setTitle(label, true);
            } else {
                win.setTitle(nonLocalizedLabel, false);
            }

            win.setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING);

            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                // Assumes it's safe to show starting windows of launched apps while
                // the keyguard is being hidden. This is okay because starting windows never show
                // secret information.
                if (mKeyguardOccluded) {
                    windowFlags |= FLAG_SHOW_WHEN_LOCKED;
                }
            }

            // Force the window flags: this is a fake window, so it is not really
            // touchable or focusable by the user.  We also add in the ALT_FOCUSABLE_IM
            // flag because we do know that the next window will take input
            // focus, so we want to get the IME window up on top of us right away.
            win.setFlags(
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            win.setDefaultIcon(icon);
            win.setDefaultLogo(logo);

            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            final WindowManager.LayoutParams params = win.getAttributes();
            params.token = appToken;
            params.packageName = packageName;
            params.windowAnimations = win.getWindowStyle().getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
            params.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_FAKE_HARDWARE_ACCELERATED;
            params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

            if (!compatInfo.supportsScreen()) {
                params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_COMPATIBLE_WINDOW;
            }

            params.setTitle("Splash Screen " + packageName);
            addSplashscreenContent(win, context);

            wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
            view = win.getDecorView();

            if (DEBUG_SPLASH_SCREEN) Slog.d(TAG, "Adding splash screen window for "
                + packageName + " / " + appToken + ": " + (view.getParent() != null ? view : null));

            wm.addView(view, params);

            // Only return the view if it was successfully added to the
            // window manager... which we can tell by it having a parent.
            return view.getParent() != null ? new SplashScreenSurface(view, appToken) : null;
        } catch (WindowManager.BadTokenException e) {
            // ignore
            Log.w(TAG, appToken + " already running, starting window not displayed. " +
                    e.getMessage());
        } catch (RuntimeException e) {
            // don't crash if something else bad happens, for example a
            // failure loading resources because we are loading from an app
            // on external storage that has been unmounted.
            Log.w(TAG, appToken + " failed creating starting window", e);
        } finally {
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                wm.removeViewImmediate(view);
            }
        }

        return null;
    }

    private void addSplashscreenContent(PhoneWindow win, Context ctx) {
        final TypedArray a = ctx.obtainStyledAttributes(R.styleable.Window);
        final int resId = a.getResourceId(R.styleable.Window_windowSplashscreenContent, 0);
        a.recycle();
        if (resId == 0) {
            return;
        }
        final Drawable drawable = ctx.getDrawable(resId);
        if (drawable == null) {
            return;
        }

        // We wrap this into a view so the system insets get applied to the drawable.
        final View v = new View(ctx);
        v.setBackground(drawable);
        win.setContentView(v);
    }

    /** Obtain proper context for showing splash screen on the provided display. */
    private Context getDisplayContext(Context context, int displayId) {
        if (displayId == DEFAULT_DISPLAY) {
            // The default context fits.
            return context;
        }

        final DisplayManager dm = (DisplayManager) context.getSystemService(DISPLAY_SERVICE);
        final Display targetDisplay = dm.getDisplay(displayId);
        if (targetDisplay == null) {
            // Failed to obtain the non-default display where splash screen should be shown,
            // lets not show at all.
            return null;
        }

        return context.createDisplayContext(targetDisplay);
    }

    /**
     * Preflight adding a window to the system.
     *
     * Currently enforces that three window types are singletons:
     * <ul>
     * <li>STATUS_BAR_TYPE</li>
     * <li>KEYGUARD_TYPE</li>
     * </ul>
     *
     * @param win The window to be added
     * @param attrs Information about the window to be added
     *
     * @return If ok, WindowManagerImpl.ADD_OKAY.  If too many singletons,
     * WindowManagerImpl.ADD_MULTIPLE_SINGLETON
     */
    @Override
    public int prepareAddWindowLw(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                if (mStatusBar != null) {
                    if (mStatusBar.isAlive()) {
                        return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mStatusBar = win;
                mStatusBarController.setWindow(win);
                setKeyguardOccludedLw(mKeyguardOccluded, true /* force */);
                break;
            case TYPE_NAVIGATION_BAR:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                if (mNavigationBar != null) {
                    if (mNavigationBar.isAlive()) {
                        return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mNavigationBar = win;
                mNavigationBarController.setWindow(win);
                mNavigationBarController.setOnBarVisibilityChangedListener(
                        mNavBarVisibilityListener, true);
                if (DEBUG_LAYOUT) Slog.i(TAG, "NAVIGATION BAR: " + mNavigationBar);
                break;
            case TYPE_NAVIGATION_BAR_PANEL:
            case TYPE_STATUS_BAR_PANEL:
            case TYPE_STATUS_BAR_SUB_PANEL:
            case TYPE_VOICE_INTERACTION_STARTING:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                break;
        }
        return ADD_OKAY;
    }

    /** {@inheritDoc} */
    @Override
    public void removeWindowLw(WindowState win) {
        if (mStatusBar == win) {
            mStatusBar = null;
            mStatusBarController.setWindow(null);
        } else if (mNavigationBar == win) {
            mNavigationBar = null;
            mNavigationBarController.setWindow(null);
        }
    }

    static final boolean PRINT_ANIM = false;

    /** {@inheritDoc} */
    @Override
    public int selectAnimationLw(WindowState win, int transit) {
        if (PRINT_ANIM) Log.i(TAG, "selectAnimation in " + win
              + ": transit=" + transit);
        if (win == mStatusBar) {
            final boolean isKeyguard = (win.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0;
            final boolean expanded = win.getAttrs().height == MATCH_PARENT
                    && win.getAttrs().width == MATCH_PARENT;
            if (isKeyguard || expanded) {
                return -1;
            }
            if (transit == TRANSIT_EXIT
                    || transit == TRANSIT_HIDE) {
                return R.anim.dock_top_exit;
            } else if (transit == TRANSIT_ENTER
                    || transit == TRANSIT_SHOW) {
                return R.anim.dock_top_enter;
            }
        } else if (win == mNavigationBar) {
            if (win.getAttrs().windowAnimations != 0) {
                return 0;
            }
            // This can be on either the bottom or the right or the left.
            if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                if (transit == TRANSIT_EXIT
                        || transit == TRANSIT_HIDE) {
                    if (isKeyguardShowingAndNotOccluded()) {
                        return R.anim.dock_bottom_exit_keyguard;
                    } else {
                        return R.anim.dock_bottom_exit;
                    }
                } else if (transit == TRANSIT_ENTER
                        || transit == TRANSIT_SHOW) {
                    return R.anim.dock_bottom_enter;
                }
            } else if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                if (transit == TRANSIT_EXIT
                        || transit == TRANSIT_HIDE) {
                    return R.anim.dock_right_exit;
                } else if (transit == TRANSIT_ENTER
                        || transit == TRANSIT_SHOW) {
                    return R.anim.dock_right_enter;
                }
            } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                if (transit == TRANSIT_EXIT
                        || transit == TRANSIT_HIDE) {
                    return R.anim.dock_left_exit;
                } else if (transit == TRANSIT_ENTER
                        || transit == TRANSIT_SHOW) {
                    return R.anim.dock_left_enter;
                }
            }
        } else if (win.getAttrs().type == TYPE_DOCK_DIVIDER) {
            return selectDockedDividerAnimationLw(win, transit);
        }

        if (transit == TRANSIT_PREVIEW_DONE) {
            if (win.hasAppShownWindows()) {
                if (PRINT_ANIM) Log.i(TAG, "**** STARTING EXIT");
                return com.android.internal.R.anim.app_starting_exit;
            }
        } else if (win.getAttrs().type == TYPE_DREAM && mDreamingLockscreen
                && transit == TRANSIT_ENTER) {
            // Special case: we are animating in a dream, while the keyguard
            // is shown.  We don't want an animation on the dream, because
            // we need it shown immediately with the keyguard animating away
            // to reveal it.
            return -1;
        }

        return 0;
    }

    private int selectDockedDividerAnimationLw(WindowState win, int transit) {
        int insets = mWindowManagerFuncs.getDockedDividerInsetsLw();

        // If the divider is behind the navigation bar, don't animate.
        final Rect frame = win.getFrameLw();
        final boolean behindNavBar = mNavigationBar != null
                && ((mNavigationBarPosition == NAV_BAR_BOTTOM
                        && frame.top + insets >= mNavigationBar.getFrameLw().top)
                || (mNavigationBarPosition == NAV_BAR_RIGHT
                        && frame.left + insets >= mNavigationBar.getFrameLw().left)
                || (mNavigationBarPosition == NAV_BAR_LEFT
                        && frame.right - insets <= mNavigationBar.getFrameLw().right));
        final boolean landscape = frame.height() > frame.width();
        final boolean offscreenLandscape = landscape && (frame.right - insets <= 0
                || frame.left + insets >= win.getDisplayFrameLw().right);
        final boolean offscreenPortrait = !landscape && (frame.top - insets <= 0
                || frame.bottom + insets >= win.getDisplayFrameLw().bottom);
        final boolean offscreen = offscreenLandscape || offscreenPortrait;
        if (behindNavBar || offscreen) {
            return 0;
        }
        if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
            return R.anim.fade_in;
        } else if (transit == TRANSIT_EXIT) {
            return R.anim.fade_out;
        } else {
            return 0;
        }
    }

    @Override
    public void selectRotationAnimationLw(int anim[]) {
        // If the screen is off or non-interactive, force a jumpcut.
        final boolean forceJumpcut = !mScreenOnFully || !okToAnimate();
        if (PRINT_ANIM) Slog.i(TAG, "selectRotationAnimation mTopFullscreen="
                + mTopFullscreenOpaqueWindowState + " rotationAnimation="
                + (mTopFullscreenOpaqueWindowState == null ?
                        "0" : mTopFullscreenOpaqueWindowState.getAttrs().rotationAnimation)
                + " forceJumpcut=" + forceJumpcut);
        if (forceJumpcut) {
            anim[0] = R.anim.rotation_animation_jump_exit;
            anim[1] = R.anim.rotation_animation_enter;
            return;
        }
        if (mTopFullscreenOpaqueWindowState != null) {
            int animationHint = mTopFullscreenOpaqueWindowState.getRotationAnimationHint();
            if (animationHint < 0 && mTopIsFullscreen) {
                animationHint = mTopFullscreenOpaqueWindowState.getAttrs().rotationAnimation;
            }
            switch (animationHint) {
                case ROTATION_ANIMATION_CROSSFADE:
                case ROTATION_ANIMATION_SEAMLESS: // Crossfade is fallback for seamless.
                    anim[0] = R.anim.rotation_animation_xfade_exit;
                    anim[1] = R.anim.rotation_animation_enter;
                    break;
                case ROTATION_ANIMATION_JUMPCUT:
                    anim[0] = R.anim.rotation_animation_jump_exit;
                    anim[1] = R.anim.rotation_animation_enter;
                    break;
                case ROTATION_ANIMATION_ROTATE:
                default:
                    anim[0] = anim[1] = 0;
                    break;
            }
        } else {
            anim[0] = anim[1] = 0;
        }
    }

    @Override
    public boolean validateRotationAnimationLw(int exitAnimId, int enterAnimId,
            boolean forceDefault) {
        switch (exitAnimId) {
            case R.anim.rotation_animation_xfade_exit:
            case R.anim.rotation_animation_jump_exit:
                // These are the only cases that matter.
                if (forceDefault) {
                    return false;
                }
                int anim[] = new int[2];
                selectRotationAnimationLw(anim);
                return (exitAnimId == anim[0] && enterAnimId == anim[1]);
            default:
                return true;
        }
    }

    @Override
    public Animation createHiddenByKeyguardExit(boolean onWallpaper,
            boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_behind_enter_fade_in);
        }

        AnimationSet set = (AnimationSet) AnimationUtils.loadAnimation(mContext, onWallpaper ?
                    R.anim.lock_screen_behind_enter_wallpaper :
                    R.anim.lock_screen_behind_enter);

        // TODO: Use XML interpolators when we have log interpolators available in XML.
        final List<Animation> animations = set.getAnimations();
        for (int i = animations.size() - 1; i >= 0; --i) {
            animations.get(i).setInterpolator(mLogDecelerateInterpolator);
        }

        return set;
    }


    @Override
    public Animation createKeyguardWallpaperExit(boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return null;
        } else {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_wallpaper_exit);
        }
    }

    private static void awakenDreams() {
        IDreamManager dreamManager = getDreamManager();
        if (dreamManager != null) {
            try {
                dreamManager.awaken();
            } catch (RemoteException e) {
                // fine, stay asleep then
            }
        }
    }

    static IDreamManager getDreamManager() {
        return IDreamManager.Stub.asInterface(
                ServiceManager.checkService(DreamService.DREAM_SERVICE));
    }

    TelecomManager getTelecommService() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    static IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub.asInterface(
                ServiceManager.checkService(Context.AUDIO_SERVICE));
        if (audioService == null) {
            Log.w(TAG, "Unable to find IAudioService interface.");
        }
        return audioService;
    }

    boolean keyguardOn() {
        return isKeyguardShowingAndNotOccluded() || inKeyguardRestrictedKeyInputMode();
    }

    private static final int[] WINDOW_TYPES_WHERE_HOME_DOESNT_WORK = {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
        };

    /** {@inheritDoc} */
    @Override
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        final boolean keyguardOn = keyguardOn();
        final int keyCode = event.getKeyCode();
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final boolean longPress = (flags & KeyEvent.FLAG_LONG_PRESS) != 0;
        final boolean virtualKey = event.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD;

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTi keyCode=" + keyCode + " down=" + down + " repeatCount="
                    + repeatCount + " keyguardOn=" + keyguardOn + " mHomePressed=" + mHomePressed
                    + " canceled=" + canceled);
        }

        // we only handle events from hardware key devices that originate from
        // real button
        // pushes. We ignore virtual key events as well since it didn't come
        // from a hard key or
        // it's the key handler synthesizing a back or menu key event for
        // dispatch
        // if keyguard is showing and secure, don't intercept and let aosp keycode
        // implementation handle event
        if (mKeyHandler != null && !keyguardOn && !virtualKey) {
            boolean handled = mKeyHandler.handleKeyEvent(win, keyCode, repeatCount, down, canceled,
                    longPress, keyguardOn);
            if (handled)
                return -1;
        }

        // If we think we might have a volume down & power key chord on the way
        // but we're not sure, then tell the dispatcher to wait a little while and
        // try again later before dispatching.
        if (mScreenshotChordEnabled && (flags & KeyEvent.FLAG_FALLBACK) == 0) {
            if (mScreenshotChordVolumeDownKeyTriggered && !mScreenshotChordPowerKeyTriggered) {
                final long now = SystemClock.uptimeMillis();
                final long timeoutTime = mScreenshotChordVolumeDownKeyTime
                        + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    && mScreenshotChordVolumeDownKeyConsumed) {
                if (!down) {
                    mScreenshotChordVolumeDownKeyConsumed = false;
                }
                return -1;
            }
        }

        // If we think we might have a volume up & power key chord on the way
        // but we're not sure, then tell the dispatcher to wait a little while and
        // try again later before dispatching.
        if (mScreenrecordChordEnabled && (flags & KeyEvent.FLAG_FALLBACK) == 0) {
            if (mScreenrecordChordVolumeUpKeyTriggered && !mScreenshotChordPowerKeyTriggered) {
                final long now = SystemClock.uptimeMillis();
                final long timeoutTime = mScreenrecordChordVolumeUpKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    && mScreenrecordChordVolumeUpKeyConsumed) {
                if (!down) {
                    mScreenrecordChordVolumeUpKeyConsumed = false;
                }
                return -1;
            }
        }

        // If an accessibility shortcut might be partially complete, hold off dispatching until we
        // know if it is complete or not
        if (mAccessibilityShortcutController.isAccessibilityShortcutAvailable(false)
                && (flags & KeyEvent.FLAG_FALLBACK) == 0) {
            if (mScreenshotChordVolumeDownKeyTriggered ^ mA11yShortcutChordVolumeUpKeyTriggered) {
                final long now = SystemClock.uptimeMillis();
                final long timeoutTime = (mScreenshotChordVolumeDownKeyTriggered
                        ? mScreenshotChordVolumeDownKeyTime : mA11yShortcutChordVolumeUpKeyTime)
                        + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && mScreenshotChordVolumeDownKeyConsumed) {
                if (!down) {
                    mScreenshotChordVolumeDownKeyConsumed = false;
                }
                return -1;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && mA11yShortcutChordVolumeUpKeyConsumed) {
                if (!down) {
                    mA11yShortcutChordVolumeUpKeyConsumed = false;
                }
                return -1;
            }
        }

        // Cancel any pending meta actions if we see any other keys being pressed between the down
        // of the meta key and its corresponding up.
        if (mPendingMetaAction && !KeyEvent.isMetaKey(keyCode)) {
            mPendingMetaAction = false;
        }
        // Any key that is not Alt or Meta cancels Caps Lock combo tracking.
        if (mPendingCapsLockToggle && !KeyEvent.isMetaKey(keyCode) && !KeyEvent.isAltKey(keyCode)) {
            mPendingCapsLockToggle = false;
        }
<<<<<<< HEAD
=======

        if (keyCode == KeyEvent.KEYCODE_BACK && !down) {
            mHandler.removeCallbacks(mBackLongPress);
        }

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
        // First we always handle the home key here, so applications
        // can never break it, although if keyguard is on, we do let
        // it handle it, because that gives us the correct 5 second
        // timeout.
        if (keyCode == KeyEvent.KEYCODE_HOME) {

            // If we have released the home key, and didn't do anything else
            // while it was pressed, then it is time to go home!
            if (!down) {
                if (mHomeDoubleTapAction != Action.APP_SWITCH) {
                    cancelPreloadRecentApps();
                }

                mHomePressed = false;
                if (mHomeConsumed) {
                    mHomeConsumed = false;
                    return -1;
                }

                if (canceled) {
                    Log.i(TAG, "Ignoring HOME; event canceled.");
                    return -1;
                }

                if ((mRingHomeBehavior
                        & LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER) != 0) {
                    final TelecomManager telecomManager = getTelecommService();
                    if (telecomManager != null && telecomManager.isRinging()) {
                        telecomManager.acceptRingingCall();
                        return -1;
                    }
                }

                // Delay handling home if a double-tap is possible.
                if (mHomeDoubleTapAction != Action.NOTHING) {
                    mHandler.removeCallbacks(mHomeDoubleTapTimeoutRunnable); // just in case
                    mHomeDoubleTapPending = true;
                    mHandler.postDelayed(mHomeDoubleTapTimeoutRunnable,
                            ViewConfiguration.getDoubleTapTimeout());
                    return -1;
                }

                handleShortPressOnHome();
                return -1;
            }

            // If a system window has focus, then it doesn't make sense
            // right now to interact with applications.
            WindowManager.LayoutParams attrs = win != null ? win.getAttrs() : null;
            if (attrs != null) {
                final int type = attrs.type;
                if (type == TYPE_KEYGUARD_DIALOG
                        || (attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                    // the "app" is keyguard, so give it the key
                    return 0;
                }
                final int typeCount = WINDOW_TYPES_WHERE_HOME_DOESNT_WORK.length;
                for (int i=0; i<typeCount; i++) {
                    if (type == WINDOW_TYPES_WHERE_HOME_DOESNT_WORK[i]) {
                        // don't do anything, but also don't pass it to the app
                        return -1;
                    }
                }
            }

            // Remember that home is pressed and handle special actions.
            if (repeatCount == 0) {
                mHomePressed = true;
                if (mHomeDoubleTapPending) {
                    mHomeDoubleTapPending = false;
                    mHandler.removeCallbacks(mHomeDoubleTapTimeoutRunnable);
                    performKeyAction(mHomeDoubleTapAction, event);
                    if (mHomeDoubleTapAction != Action.SLEEP) {
                        mHomeConsumed = true;
                    }
                } else if (mHomeLongPressAction == Action.APP_SWITCH
                        || mHomeDoubleTapAction == Action.APP_SWITCH) {
                    preloadRecentApps();
                }
            } else if (longPress) {
                if (!keyguardOn && !mHomeConsumed &&
                        mHomeLongPressAction != Action.NOTHING) {
                    if (mHomeLongPressAction != Action.APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    mHomePressed = true;
                    performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                    performKeyAction(mHomeLongPressAction, event);
                    if (mHomeLongPressAction != Action.SLEEP) {
                        mHomeConsumed = true;
                    }
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Hijack modified menu keys for debugging features
            final int chordBug = KeyEvent.META_SHIFT_ON;
            if (virtualKey || keyguardOn) {
                // Let the app handle the key
                return 0;
            }

            if (down) {
                if (mMenuPressAction == Action.APP_SWITCH
                        || mMenuLongPressAction == Action.APP_SWITCH) {
                    preloadRecentApps();
                }
                if (repeatCount == 0) {
                    mMenuPressed = true;
                    if (mEnableShiftMenuBugReports && (metaState & chordBug) == chordBug) {
                        Intent intent = new Intent(Intent.ACTION_BUG_REPORT);
                        mContext.sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT,
                                null, null, null, 0, null, null);
                        return -1;
                     }
                } else if (longPress) {
                    if (!keyguardOn && mMenuLongPressAction != Action.NOTHING) {
                        if (mMenuLongPressAction != Action.APP_SWITCH) {
                            cancelPreloadRecentApps();
                        }
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        performKeyAction(mMenuLongPressAction, event);
                        mMenuPressed = false;
                        return -1;
                    }
                }
            }

            if (!down && mMenuPressed) {
                if (mMenuPressAction != Action.APP_SWITCH) {
                    cancelPreloadRecentApps();
                }
                mMenuPressed = false;
                if (!canceled) {
                    performKeyAction(mMenuPressAction, event);
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (down) {
                if (repeatCount == 0) {
                    mSearchKeyShortcutPending = true;
                    mConsumeSearchKeyUp = false;
                }
            } else {
                mSearchKeyShortcutPending = false;
                if (mConsumeSearchKeyUp) {
                    mConsumeSearchKeyUp = false;
                    return -1;
                }
            }
            return 0;
        } else if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            if (!keyguardOn) {
                if (down) {
                    if (mAppSwitchPressAction == Action.APP_SWITCH
                            || mAppSwitchLongPressAction == Action.APP_SWITCH) {
                        preloadRecentApps();
                    }
                    if (repeatCount == 0) {
                        mAppSwitchLongPressed = false;
                    } else if (longPress) {
                        if (!keyguardOn && mAppSwitchLongPressAction != Action.NOTHING) {
                            if (mAppSwitchLongPressAction != Action.APP_SWITCH) {
                                cancelPreloadRecentApps();
                            }
                            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                            performKeyAction(mAppSwitchLongPressAction, event);
                            mAppSwitchLongPressed = true;
                       }
                    }
                } else {
                    if (mAppSwitchLongPressed) {
                        mAppSwitchLongPressed = false;
                    } else {
                        if (mAppSwitchPressAction != Action.APP_SWITCH) {
                            cancelPreloadRecentApps();
                        }
                        if (!canceled) {
                            performKeyAction(mAppSwitchPressAction, event);
                        }
                    }
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_N && event.isMetaPressed()) {
            if (down) {
                IStatusBarService service = getStatusBarService();
                if (service != null) {
                    try {
                        service.expandNotificationsPanel();
                    } catch (RemoteException e) {
                        // do nothing.
                    }
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_S && event.isMetaPressed()
                && event.isCtrlPressed()) {
            if (down && repeatCount == 0) {
                int type = event.isShiftPressed() ? TAKE_SCREENSHOT_SELECTED_REGION
                        : TAKE_SCREENSHOT_FULLSCREEN;
                mScreenshotRunnable.setScreenshotType(type);
                mHandler.post(mScreenshotRunnable);
                return -1;
            }
        } else if (keyCode == KeyEvent.KEYCODE_SLASH && event.isMetaPressed()) {
            if (down && repeatCount == 0 && !isKeyguardLocked()) {
                toggleKeyboardShortcutsMenu(event.getDeviceId());
            }
        } else if (keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (down) {
                if (mAssistPressAction == Action.APP_SWITCH
                        || mAssistLongPressAction == Action.APP_SWITCH) {
                    preloadRecentApps();
                }
                if (repeatCount == 0) {
                    mAssistKeyLongPressed = false;
                } else if (longPress) {
                    if (!keyguardOn && mAssistLongPressAction != Action.NOTHING) {
                        if (mAssistLongPressAction != Action.APP_SWITCH) {
                            cancelPreloadRecentApps();
                        }
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        performKeyAction(mAssistLongPressAction, event);
                        mAssistKeyLongPressed = true;
                    }
                }
            } else {
                if (mAssistKeyLongPressed) {
                    mAssistKeyLongPressed = false;
                } else {
                    if (mAssistPressAction != Action.APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    if (!canceled) {
                        performKeyAction(mAssistPressAction, event);
                    }
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (!down) {
                Intent voiceIntent;
                if (!keyguardOn) {
                    voiceIntent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
                } else {
                    IDeviceIdleController dic = IDeviceIdleController.Stub.asInterface(
                            ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
                    if (dic != null) {
                        try {
                            dic.exitIdle("voice-search");
                        } catch (RemoteException e) {
                        }
                    }
                    voiceIntent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
                    voiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, true);
                }
                startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF);
            }
        } else if (keyCode == KeyEvent.KEYCODE_SYSRQ) {
            if (down && repeatCount == 0) {
                mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_FULLSCREEN);
                mHandler.post(mScreenshotRunnable);
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_BRIGHTNESS_UP
                || keyCode == KeyEvent.KEYCODE_BRIGHTNESS_DOWN) {
            if (down) {
                int direction = keyCode == KeyEvent.KEYCODE_BRIGHTNESS_UP ? 1 : -1;

                // Disable autobrightness if it's on
                int auto = Settings.System.getIntForUser(
                        mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        UserHandle.USER_CURRENT_OR_SELF);
                if (auto != 0) {
                    Settings.System.putIntForUser(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                            UserHandle.USER_CURRENT_OR_SELF);
                }

                int min = mPowerManager.getMinimumScreenBrightnessSetting();
                int max = mPowerManager.getMaximumScreenBrightnessSetting();
                int step = (max - min + BRIGHTNESS_STEPS - 1) / BRIGHTNESS_STEPS * direction;
                int brightness = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS,
                        mPowerManager.getDefaultScreenBrightnessSetting(),
                        UserHandle.USER_CURRENT_OR_SELF);
                brightness += step;
                // Make sure we don't go beyond the limits.
                brightness = Math.min(max, brightness);
                brightness = Math.max(min, brightness);

                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, brightness,
                        UserHandle.USER_CURRENT_OR_SELF);
                startActivityAsUser(new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG),
                        UserHandle.CURRENT_OR_SELF);
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (mUseTvRouting || mHandleVolumeKeysInWM) {
                // On TVs or when the configuration is enabled, volume keys never
                // go to the foreground app.
                dispatchDirectAudioEvent(event);
                return -1;
            }

            // If the device is in Vr mode, drop the volume keys and don't
            // forward it to the application/dispatch the audio event.
            if (mPersistentVrModeEnabled) {
                return -1;
            }
        } else if (keyCode == KeyEvent.KEYCODE_TAB && event.isMetaPressed()) {
            // Pass through keyboard navigation keys.
            return 0;
        } else if (mHasFeatureLeanback && interceptBugreportGestureTv(keyCode, down)) {
            return -1;
        } else if (mHasFeatureLeanback && interceptAccessibilityGestureTv(keyCode, down)) {
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            if (!down) {
                mHandler.removeMessages(MSG_HANDLE_ALL_APPS);
                Message msg = mHandler.obtainMessage(MSG_HANDLE_ALL_APPS);
                msg.setAsynchronous(true);
                msg.sendToTarget();
            }
            return -1;
        }

        // Toggle Caps Lock on META-ALT.
        boolean actionTriggered = false;
        if (KeyEvent.isModifierKey(keyCode)) {
            if (!mPendingCapsLockToggle) {
                // Start tracking meta state for combo.
                mInitialMetaState = mMetaState;
                mPendingCapsLockToggle = true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                int altOnMask = mMetaState & KeyEvent.META_ALT_MASK;
                int metaOnMask = mMetaState & KeyEvent.META_META_MASK;

                // Check for Caps Lock toggle
                if ((metaOnMask != 0) && (altOnMask != 0)) {
                    // Check if nothing else is pressed
                    if (mInitialMetaState == (mMetaState ^ (altOnMask | metaOnMask))) {
                        // Handle Caps Lock Toggle
                        mInputManagerInternal.toggleCapsLock(event.getDeviceId());
                        actionTriggered = true;
                    }
                }

                // Always stop tracking when key goes up.
                mPendingCapsLockToggle = false;
            }
        }
        // Store current meta state to be able to evaluate it later.
        mMetaState = metaState;

        if (actionTriggered) {
            return -1;
        }

        if (KeyEvent.isMetaKey(keyCode)) {
            if (down) {
                mPendingMetaAction = true;
            } else if (mPendingMetaAction) {
                launchAssistAction(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, event.getDeviceId());
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mKillAppLongpressBack || unpinActivity(true)) {
                if (down && repeatCount == 0) {
                    mHandler.postDelayed(mBackLongPress, mBackKillTimeout);
                }
            }
        }

        // Shortcuts are invoked through Search+key, so intercept those here
        // Any printing key that is chorded with Search should be consumed
        // even if no shortcut was invoked.  This prevents text from being
        // inadvertently inserted when using a keyboard that has built-in macro
        // shortcut keys (that emit Search+x) and some of them are not registered.
        if (mSearchKeyShortcutPending) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            if (kcm.isPrintingKey(keyCode)) {
                mConsumeSearchKeyUp = true;
                mSearchKeyShortcutPending = false;
                if (down && repeatCount == 0 && !keyguardOn) {
                    Intent shortcutIntent = mShortcutManager.getIntent(kcm, keyCode, metaState);
                    if (shortcutIntent != null) {
                        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivityAsUser(shortcutIntent, UserHandle.CURRENT);
                            dismissKeyboardShortcutsMenu();
                        } catch (ActivityNotFoundException ex) {
                            Slog.w(TAG, "Dropping shortcut key combination because "
                                    + "the activity to which it is registered was not found: "
                                    + "SEARCH+" + KeyEvent.keyCodeToString(keyCode), ex);
                        }
                    } else {
                        Slog.i(TAG, "Dropping unregistered shortcut key combination: "
                                + "SEARCH+" + KeyEvent.keyCodeToString(keyCode));
                    }
                }
                return -1;
            }
        }

        // Invoke shortcuts using Meta.
        if (down && repeatCount == 0 && !keyguardOn
                && (metaState & KeyEvent.META_META_ON) != 0) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            if (kcm.isPrintingKey(keyCode)) {
                Intent shortcutIntent = mShortcutManager.getIntent(kcm, keyCode,
                        metaState & ~(KeyEvent.META_META_ON
                                | KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON));
                if (shortcutIntent != null) {
                    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivityAsUser(shortcutIntent, UserHandle.CURRENT);
                        dismissKeyboardShortcutsMenu();
                    } catch (ActivityNotFoundException ex) {
                        Slog.w(TAG, "Dropping shortcut key combination because "
                                + "the activity to which it is registered was not found: "
                                + "META+" + KeyEvent.keyCodeToString(keyCode), ex);
                    }
                    return -1;
                }
            }
        }

        // Handle application launch keys.
        if (down && repeatCount == 0 && !keyguardOn) {
            String category = sApplicationLaunchKeyCategories.get(keyCode);
            if (category != null) {
                Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivityAsUser(intent, UserHandle.CURRENT);
                    dismissKeyboardShortcutsMenu();
                } catch (ActivityNotFoundException ex) {
                    Slog.w(TAG, "Dropping application launch key because "
                            + "the activity to which it is registered was not found: "
                            + "keyCode=" + keyCode + ", category=" + category, ex);
                }
                return -1;
            }
        }

        // Display task switcher for ALT-TAB.
        if (down && repeatCount == 0 && keyCode == KeyEvent.KEYCODE_TAB) {
            if (mRecentAppsHeldModifiers == 0 && !keyguardOn && isUserSetupComplete()) {
                final int shiftlessModifiers = event.getModifiers() & ~KeyEvent.META_SHIFT_MASK;
                if (KeyEvent.metaStateHasModifiers(shiftlessModifiers, KeyEvent.META_ALT_ON)) {
                    mRecentAppsHeldModifiers = shiftlessModifiers;
                    showRecentApps(true, false);
                    return -1;
                }
            }
        } else if (!down && mRecentAppsHeldModifiers != 0
                && (metaState & mRecentAppsHeldModifiers) == 0) {
            mRecentAppsHeldModifiers = 0;
            hideRecentApps(true, false);
        }

        // Handle input method switching.
        if (down && repeatCount == 0
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
                        || (keyCode == KeyEvent.KEYCODE_SPACE
                                && (metaState & KeyEvent.META_META_MASK) != 0))) {
            final boolean forwardDirection = (metaState & KeyEvent.META_SHIFT_MASK) == 0;
            mWindowManagerFuncs.switchInputMethod(forwardDirection);
            return -1;
        }
        if (mLanguageSwitchKeyPressed && !down
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
                        || keyCode == KeyEvent.KEYCODE_SPACE)) {
            mLanguageSwitchKeyPressed = false;
            return -1;
        }

        if (isValidGlobalKey(keyCode)
                && mGlobalKeyManager.handleGlobalKey(mContext, keyCode, event)) {
            return -1;
        }

        // Specific device key handling
        if (dispatchKeyToKeyHandlers(event)) {
            return -1;
        }

        if (down) {
            long shortcutCode = keyCode;
            if (event.isCtrlPressed()) {
                shortcutCode |= ((long) KeyEvent.META_CTRL_ON) << Integer.SIZE;
            }

            if (event.isAltPressed()) {
                shortcutCode |= ((long) KeyEvent.META_ALT_ON) << Integer.SIZE;
            }

            if (event.isShiftPressed()) {
                shortcutCode |= ((long) KeyEvent.META_SHIFT_ON) << Integer.SIZE;
            }

            if (event.isMetaPressed()) {
                shortcutCode |= ((long) KeyEvent.META_META_ON) << Integer.SIZE;
            }

            IShortcutService shortcutService = mShortcutKeyServices.get(shortcutCode);
            if (shortcutService != null) {
                try {
                    if (isUserSetupComplete()) {
                        shortcutService.notifyShortcutKeyPressed(shortcutCode);
                    }
                } catch (RemoteException e) {
                    mShortcutKeyServices.delete(shortcutCode);
                }
                return -1;
            }
        }

        // Reserve all the META modifier combos for system behavior
        if ((metaState & KeyEvent.META_META_ON) != 0) {
            return -1;
        }

        // Let the application handle the key.
        return 0;
    }

    /**
     * TV only: recognizes a remote control gesture for capturing a bug report.
     */
    private boolean interceptBugreportGestureTv(int keyCode, boolean down) {
        // The bugreport capture chord is a long press on DPAD CENTER and BACK simultaneously.
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            mBugreportTvKey1Pressed = down;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            mBugreportTvKey2Pressed = down;
        }

        if (mBugreportTvKey1Pressed && mBugreportTvKey2Pressed) {
            if (!mBugreportTvScheduled) {
                mBugreportTvScheduled = true;
                Message msg = Message.obtain(mHandler, MSG_BUGREPORT_TV);
                msg.setAsynchronous(true);
                mHandler.sendMessageDelayed(msg, BUGREPORT_TV_GESTURE_TIMEOUT_MILLIS);
            }
        } else if (mBugreportTvScheduled) {
            mHandler.removeMessages(MSG_BUGREPORT_TV);
            mBugreportTvScheduled = false;
        }

        return mBugreportTvScheduled;
    }

    /**
     * TV only: recognizes a remote control gesture as Accessibility shortcut.
     * Shortcut: Long press (BACK + DPAD_DOWN)
     */
    private boolean interceptAccessibilityGestureTv(int keyCode, boolean down) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mAccessibilityTvKey1Pressed = down;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            mAccessibilityTvKey2Pressed = down;
        }

        if (mAccessibilityTvKey1Pressed && mAccessibilityTvKey2Pressed) {
            if (!mAccessibilityTvScheduled) {
                mAccessibilityTvScheduled = true;
                Message msg = Message.obtain(mHandler, MSG_ACCESSIBILITY_TV);
                msg.setAsynchronous(true);
                mHandler.sendMessageDelayed(msg,
                        ViewConfiguration.get(mContext).getAccessibilityShortcutKeyTimeout());
            }
        } else if (mAccessibilityTvScheduled) {
            mHandler.removeMessages(MSG_ACCESSIBILITY_TV);
            mAccessibilityTvScheduled = false;
        }

        return mAccessibilityTvScheduled;
    }

    private void takeBugreport() {
        if ("1".equals(SystemProperties.get("ro.debuggable"))
                || Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
            try {
                ActivityManager.getService()
                        .requestBugReport(ActivityManager.BUGREPORT_OPTION_INTERACTIVE);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error taking bugreport", e);
            }
        }
    }

    private boolean dispatchKeyToKeyHandlers(KeyEvent event) {
        for (DeviceKeyHandler handler : mDeviceKeyHandlers) {
            try {
                if (DEBUG_INPUT) {
                    Log.d(TAG, "Dispatching key event " + event + " to handler " + handler);
                }
                event = handler.handleKeyEvent(event);
                if (event == null) {
                    return true;
                }
            } catch (Exception e) {
                Slog.w(TAG, "Could not dispatch event to device key handler", e);
            }
        }
        return false;
    }

    private boolean unpinActivity(boolean checkOnly) {
        if (!hasNavigationBar()) {
            try {
                if (ActivityManagerNative.getDefault().isInLockTaskMode()) {
                    if (!checkOnly) {
                        ActivityManagerNative.getDefault().stopSystemLockTaskMode();
                    }
                    return true;
                }
            } catch (RemoteException e) {
                // ignore
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public KeyEvent dispatchUnhandledKey(WindowState win, KeyEvent event, int policyFlags) {
        // Note: This method is only called if the initial down was unhandled.
        if (DEBUG_INPUT) {
            Slog.d(TAG, "Unhandled key: win=" + win + ", action=" + event.getAction()
                    + ", flags=" + event.getFlags()
                    + ", keyCode=" + event.getKeyCode()
                    + ", scanCode=" + event.getScanCode()
                    + ", metaState=" + event.getMetaState()
                    + ", repeatCount=" + event.getRepeatCount()
                    + ", policyFlags=" + policyFlags);
        }

        KeyEvent fallbackEvent = null;
        if ((event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            final int keyCode = event.getKeyCode();
            final int metaState = event.getMetaState();
            final boolean initialDown = event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0;

            // Check for fallback actions specified by the key character map.
            final FallbackAction fallbackAction;
            if (initialDown) {
                fallbackAction = kcm.getFallbackAction(keyCode, metaState);
            } else {
                fallbackAction = mFallbackActions.get(keyCode);
            }

            if (fallbackAction != null) {
                if (DEBUG_INPUT) {
                    Slog.d(TAG, "Fallback: keyCode=" + fallbackAction.keyCode
                            + " metaState=" + Integer.toHexString(fallbackAction.metaState));
                }

                final int flags = event.getFlags() | KeyEvent.FLAG_FALLBACK;
                fallbackEvent = KeyEvent.obtain(
                        event.getDownTime(), event.getEventTime(),
                        event.getAction(), fallbackAction.keyCode,
                        event.getRepeatCount(), fallbackAction.metaState,
                        event.getDeviceId(), event.getScanCode(),
                        flags, event.getSource(), null);

                if (!interceptFallback(win, fallbackEvent, policyFlags)) {
                    fallbackEvent.recycle();
                    fallbackEvent = null;
                }

                if (initialDown) {
                    mFallbackActions.put(keyCode, fallbackAction);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    mFallbackActions.remove(keyCode);
                    fallbackAction.recycle();
                }
            }
        }

        if (DEBUG_INPUT) {
            if (fallbackEvent == null) {
                Slog.d(TAG, "No fallback.");
            } else {
                Slog.d(TAG, "Performing fallback: " + fallbackEvent);
            }
        }
        return fallbackEvent;
    }

    private boolean interceptFallback(WindowState win, KeyEvent fallbackEvent, int policyFlags) {
        int actions = interceptKeyBeforeQueueing(fallbackEvent, policyFlags);
        if ((actions & ACTION_PASS_TO_USER) != 0) {
            long delayMillis = interceptKeyBeforeDispatching(
                    win, fallbackEvent, policyFlags);
            if (delayMillis == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void registerShortcutKey(long shortcutCode, IShortcutService shortcutService)
            throws RemoteException {
        synchronized (mLock) {
            IShortcutService service = mShortcutKeyServices.get(shortcutCode);
            if (service != null && service.asBinder().pingBinder()) {
                throw new RemoteException("Key already exists.");
            }

            mShortcutKeyServices.put(shortcutCode, shortcutService);
        }
    }

    @Override
    public void onKeyguardOccludedChangedLw(boolean occluded) {
        if (mKeyguardDelegate != null && mKeyguardDelegate.isShowing()) {
            mPendingKeyguardOccluded = occluded;
            mKeyguardOccludedChanged = true;
        } else {
            setKeyguardOccludedLw(occluded, false /* force */);
        }
    }

    private int handleStartTransitionForKeyguardLw(int transit, @Nullable Animation anim) {
        if (mKeyguardOccludedChanged) {
            if (DEBUG_KEYGUARD) Slog.d(TAG, "transition/occluded changed occluded="
                    + mPendingKeyguardOccluded);
            mKeyguardOccludedChanged = false;
            if (setKeyguardOccludedLw(mPendingKeyguardOccluded, false /* force */)) {
                return FINISH_LAYOUT_REDO_LAYOUT | FINISH_LAYOUT_REDO_WALLPAPER;
            }
        }
        if (AppTransition.isKeyguardGoingAwayTransit(transit)) {
            if (DEBUG_KEYGUARD) Slog.d(TAG, "Starting keyguard exit animation");
            final long startTime = anim != null
                    ? SystemClock.uptimeMillis() + anim.getStartOffset()
                    : SystemClock.uptimeMillis();
            final long duration = anim != null
                    ? anim.getDuration()
                    : 0;
            startKeyguardExitAnimation(startTime, duration);
        }
        return 0;
    }

    private void launchAssistLongPressAction() {
        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);

        // launch the search activity
        Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // TODO: This only stops the factory-installed search manager.
            // Need to formalize an API to handle others
            SearchManager searchManager = getSearchManager();
            if (searchManager != null) {
                searchManager.stopSearch();
            }
            startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (ActivityNotFoundException e) {
            Slog.w(TAG, "No activity to handle assist long press action.", e);
        }
    }

    private void launchAssistAction(String hint, int deviceId) {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);
        if (!isUserSetupComplete()) {
            // Disable opening assist window during setup
            return;
        }
        Bundle args = null;
        if (deviceId > Integer.MIN_VALUE) {
            args = new Bundle();
            args.putInt(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID, deviceId);
        }
        if ((mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            // On TV, use legacy handling until assistants are implemented in the proper way.
            ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .launchLegacyAssist(hint, UserHandle.myUserId(), args);
        } else {
            if (hint != null) {
                if (args == null) {
                    args = new Bundle();
                }
                args.putBoolean(hint, true);
            }
            StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.startAssist(args);
            }
        }
    }

    private void startActivityAsUser(Intent intent, UserHandle handle) {
        if (isUserSetupComplete()) {
            mContext.startActivityAsUser(intent, handle);
        } else {
            Slog.i(TAG, "Not starting activity because user setup is in progress: " + intent);
        }
    }

    private SearchManager getSearchManager() {
        if (mSearchManager == null) {
            mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        }
        return mSearchManager;
    }

    private void preloadRecentApps() {
        mPreloadedRecentApps = true;
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.preloadRecentApps();
        }
    }

    private void cancelPreloadRecentApps() {
        if (mPreloadedRecentApps) {
            mPreloadedRecentApps = false;
            StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.cancelPreloadRecentApps();
            }
        }
    }

    private void toggleRecentApps() {
        mPreloadedRecentApps = false; // preloading no longer needs to be canceled
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleRecentApps();
        }
    }

    private void toggleSplitScreen() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleSplitScreen();
        }
    }

    @Override
    public void showRecentApps(boolean fromHome) {
        mHandler.removeMessages(MSG_DISPATCH_SHOW_RECENTS);
        mHandler.obtainMessage(MSG_DISPATCH_SHOW_RECENTS, fromHome ? 1 : 0, 0).sendToTarget();
    }

    private void showRecentApps(boolean triggeredFromAltTab, boolean fromHome) {
        mPreloadedRecentApps = false; // preloading no longer needs to be canceled
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.showRecentApps(triggeredFromAltTab, fromHome);
        }
    }

    private void toggleKeyboardShortcutsMenu(int deviceId) {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleKeyboardShortcutsMenu(deviceId);
        }
    }

    private void dismissKeyboardShortcutsMenu() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.dismissKeyboardShortcutsMenu();
        }
    }

    private void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHome) {
        mPreloadedRecentApps = false; // preloading no longer needs to be canceled
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.hideRecentApps(triggeredFromAltTab, triggeredFromHome);
        }
    }

    void launchHomeFromHotKey() {
        launchHomeFromHotKey(true /* awakenFromDreams */, true /*respectKeyguard*/);
    }

    /**
     * A home key -> launch home action was detected.  Take the appropriate action
     * given the situation with the keyguard.
     */
    void launchHomeFromHotKey(final boolean awakenFromDreams, final boolean respectKeyguard) {
        if (respectKeyguard) {
            if (isKeyguardShowingAndNotOccluded()) {
                // don't launch home if keyguard showing
                return;
            }

            if (!mKeyguardOccluded && mKeyguardDelegate.isInputRestricted()) {
                // when in keyguard restricted mode, must first verify unlock
                // before launching home
                mKeyguardDelegate.verifyUnlock(new OnKeyguardExitResult() {
                    @Override
                    public void onKeyguardExitResult(boolean success) {
                        if (success) {
                            try {
                                ActivityManager.getService().stopAppSwitches();
                            } catch (RemoteException e) {
                            }
                            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
                            startDockOrHome(true /*fromHomeKey*/, awakenFromDreams);
                        }
                    }
                });
                return;
            }
        }

        // no keyguard stuff to worry about, just launch home!
        try {
            ActivityManager.getService().stopAppSwitches();
        } catch (RemoteException e) {
        }
        if (mRecentsVisible) {
            // Hide Recents and notify it to launch Home
            if (awakenFromDreams) {
                awakenDreams();
            }
            hideRecentApps(false, true);
        } else if (mScreenOnFully) {
            // Otherwise, just launch Home
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
            startDockOrHome(true /*fromHomeKey*/, awakenFromDreams);
        }
    }

    private final Runnable mClearHideNavigationFlag = new Runnable() {
        @Override
        public void run() {
            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                // Clear flags.
                mForceClearedSystemUiFlags &=
                        ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    };

    /**
     * Input handler used while nav bar is hidden.  Captures any touch on the screen,
     * to determine when the nav bar should be shown and prevent applications from
     * receiving those touches.
     */
    final class HideNavInputEventReceiver extends InputEventReceiver {
        public HideNavInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event, int displayId) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent
                        && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    final MotionEvent motionEvent = (MotionEvent)event;
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        // When the user taps down, we re-show the nav bar.
                        boolean changed = false;
                        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                            if (mInputConsumer == null) {
                                return;
                            }
                            // Any user activity always causes us to show the
                            // navigation controls, if they had been hidden.
                            // We also clear the low profile and only content
                            // flags so that tapping on the screen will atomically
                            // restore all currently hidden screen decorations.
                            int newVal = mResettingSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                    View.SYSTEM_UI_FLAG_LOW_PROFILE |
                                    View.SYSTEM_UI_FLAG_FULLSCREEN;
                            if (mResettingSystemUiFlags != newVal) {
                                mResettingSystemUiFlags = newVal;
                                changed = true;
                            }
                            // We don't allow the system's nav bar to be hidden
                            // again for 1 second, to prevent applications from
                            // spamming us and keeping it from being shown.
                            newVal = mForceClearedSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            if (mForceClearedSystemUiFlags != newVal) {
                                mForceClearedSystemUiFlags = newVal;
                                changed = true;
                                mHandler.postDelayed(mClearHideNavigationFlag, 1000);
                            }
                        }
                        if (changed) {
                            mWindowManagerFuncs.reevaluateStatusBarVisibility();
                        }
                    }
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }

    @Override
    public void setRecentsVisibilityLw(boolean visible) {
        mRecentsVisible = visible;
    }

    @Override
    public void setPipVisibilityLw(boolean visible) {
        mPictureInPictureVisible = visible;
    }

    @Override
    public int adjustSystemUiVisibilityLw(int visibility) {
        mStatusBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);
        mNavigationBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);

        updateEdgeGestureListenerState();

        // Reset any bits in mForceClearingStatusBarVisibility that
        // are now clear.
        mResettingSystemUiFlags &= visibility;
        // Clear any bits in the new visibility that are currently being
        // force cleared, before reporting it.
        return visibility & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;
    }

    @Override
    public boolean getInsetHintLw(WindowManager.LayoutParams attrs, Rect taskBounds,
            int displayRotation, int displayWidth, int displayHeight, Rect outContentInsets,
            Rect outStableInsets, Rect outOutsets) {
        final int fl = WindowManagerPolicyControl.getWindowFlags(null, attrs);
        final int sysuiVis = WindowManagerPolicyControl.getSystemUiVisibility(null, attrs);
        final int systemUiVisibility = (sysuiVis | attrs.subtreeSystemUiVisibility);

        final boolean useOutsets = outOutsets != null && shouldUseOutsets(attrs, fl);
        if (useOutsets) {
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(mContext.getResources());
            if (outset > 0) {
                if (displayRotation == Surface.ROTATION_0) {
                    outOutsets.bottom += outset;
                } else if (displayRotation == Surface.ROTATION_90) {
                    outOutsets.right += outset;
                } else if (displayRotation == Surface.ROTATION_180) {
                    outOutsets.top += outset;
                } else if (displayRotation == Surface.ROTATION_270) {
                    outOutsets.left += outset;
                }
            }
        }

        if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
            int availRight, availBottom;
            if (canHideNavigationBar() &&
                    (systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0) {
                availRight = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                availBottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            } else {
                availRight = mRestrictedScreenLeft + mRestrictedScreenWidth;
                availBottom = mRestrictedScreenTop + mRestrictedScreenHeight;
            }
            if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
                if ((fl & FLAG_FULLSCREEN) != 0) {
                    outContentInsets.set(mStableFullscreenLeft, mStableFullscreenTop,
                            availRight - mStableFullscreenRight,
                            availBottom - mStableFullscreenBottom);
                } else {
                    outContentInsets.set(mStableLeft, mStableTop,
                            availRight - mStableRight, availBottom - mStableBottom);
                }
            } else if ((fl & FLAG_FULLSCREEN) != 0 || (fl & FLAG_LAYOUT_IN_OVERSCAN) != 0) {
                outContentInsets.setEmpty();
            } else if ((systemUiVisibility & (View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) == 0) {
                outContentInsets.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            } else {
                outContentInsets.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            }

            outStableInsets.set(mStableLeft, mStableTop,
                    availRight - mStableRight, availBottom - mStableBottom);
            if (taskBounds != null) {
                calculateRelevantTaskInsets(taskBounds, outContentInsets,
                        displayWidth, displayHeight);
                calculateRelevantTaskInsets(taskBounds, outStableInsets,
                        displayWidth, displayHeight);
            }
            return mForceShowSystemBars;
        }
        outContentInsets.setEmpty();
        outStableInsets.setEmpty();
        return mForceShowSystemBars;
    }

    /**
     * For any given task bounds, the insets relevant for these bounds given the insets relevant
     * for the entire display.
     */
    private void calculateRelevantTaskInsets(Rect taskBounds, Rect inOutInsets, int displayWidth,
            int displayHeight) {
        mTmpRect.set(0, 0, displayWidth, displayHeight);
        mTmpRect.inset(inOutInsets);
        mTmpRect.intersect(taskBounds);
        int leftInset = mTmpRect.left - taskBounds.left;
        int topInset = mTmpRect.top - taskBounds.top;
        int rightInset = taskBounds.right - mTmpRect.right;
        int bottomInset = taskBounds.bottom - mTmpRect.bottom;
        inOutInsets.set(leftInset, topInset, rightInset, bottomInset);
    }

    private boolean shouldUseOutsets(WindowManager.LayoutParams attrs, int fl) {
        return attrs.type == TYPE_WALLPAPER || (fl & (WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN)) != 0;
    }

    /** {@inheritDoc} */
    @Override
    public void beginLayoutLw(boolean isDefaultDisplay, int displayWidth, int displayHeight,
                              int displayRotation, int uiMode) {
        mDisplayRotation = displayRotation;
        final int overscanLeft, overscanTop, overscanRight, overscanBottom;
        if (isDefaultDisplay) {
            switch (displayRotation) {
                case Surface.ROTATION_90:
                    overscanLeft = mOverscanTop;
                    overscanTop = mOverscanRight;
                    overscanRight = mOverscanBottom;
                    overscanBottom = mOverscanLeft;
                    break;
                case Surface.ROTATION_180:
                    overscanLeft = mOverscanRight;
                    overscanTop = mOverscanBottom;
                    overscanRight = mOverscanLeft;
                    overscanBottom = mOverscanTop;
                    break;
                case Surface.ROTATION_270:
                    overscanLeft = mOverscanBottom;
                    overscanTop = mOverscanLeft;
                    overscanRight = mOverscanTop;
                    overscanBottom = mOverscanRight;
                    break;
                default:
                    overscanLeft = mOverscanLeft;
                    overscanTop = mOverscanTop;
                    overscanRight = mOverscanRight;
                    overscanBottom = mOverscanBottom;
                    break;
            }
        } else {
            overscanLeft = 0;
            overscanTop = 0;
            overscanRight = 0;
            overscanBottom = 0;
        }
        mOverscanScreenLeft = mRestrictedOverscanScreenLeft = 0;
        mOverscanScreenTop = mRestrictedOverscanScreenTop = 0;
        mOverscanScreenWidth = mRestrictedOverscanScreenWidth = displayWidth;
        mOverscanScreenHeight = mRestrictedOverscanScreenHeight = displayHeight;
        mSystemLeft = 0;
        mSystemTop = 0;
        mSystemRight = displayWidth;
        mSystemBottom = displayHeight;
        mUnrestrictedScreenLeft = overscanLeft;
        mUnrestrictedScreenTop = overscanTop;
        mUnrestrictedScreenWidth = displayWidth - overscanLeft - overscanRight;
        mUnrestrictedScreenHeight = displayHeight - overscanTop - overscanBottom;
        mRestrictedScreenLeft = mUnrestrictedScreenLeft;
        mRestrictedScreenTop = mUnrestrictedScreenTop;
        mRestrictedScreenWidth = mSystemGestures.screenWidth = mUnrestrictedScreenWidth;
        mRestrictedScreenHeight = mSystemGestures.screenHeight = mUnrestrictedScreenHeight;
        mDockLeft = mContentLeft = mVoiceContentLeft = mStableLeft = mStableFullscreenLeft
                = mCurLeft = mForceImmersiveLeft = mUnrestrictedScreenLeft;
        mDockTop = mContentTop = mVoiceContentTop = mStableTop = mStableFullscreenTop
                = mCurTop = mForceImmersiveTop = mUnrestrictedScreenTop;
        mDockRight = mContentRight = mVoiceContentRight = mStableRight = mStableFullscreenRight
                = mCurRight = mForceImmersiveRight = displayWidth - overscanRight;
        mDockBottom = mContentBottom = mVoiceContentBottom = mStableBottom = mStableFullscreenBottom
                = mCurBottom = mForceImmersiveBottom = displayHeight - overscanBottom;
        mDockLayer = 0x10000000;
        mStatusBarLayer = -1;

        // start with the current dock rect, which will be (0,0,displayWidth,displayHeight)
        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect of = mTmpOverscanFrame;
        final Rect vf = mTmpVisibleFrame;
        final Rect dcf = mTmpDecorFrame;
        pf.left = df.left = of.left = vf.left = mDockLeft;
        pf.top = df.top = of.top = vf.top = mDockTop;
        pf.right = df.right = of.right = vf.right = mDockRight;
        pf.bottom = df.bottom = of.bottom = vf.bottom = mDockBottom;
        dcf.setEmpty();  // Decor frame N/A for system bars.

        if (isDefaultDisplay) {
            // For purposes of putting out fake window up to steal focus, we will
            // drive nav being hidden only by whether it is requested.
            final int sysui = mLastSystemUiFlags;
            boolean navVisible = (sysui & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            boolean navTranslucent = (sysui
                    & (View.NAVIGATION_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSPARENT)) != 0;
            boolean immersive = (sysui & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
            boolean immersiveSticky = (sysui & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
            boolean navAllowedHidden = immersive || immersiveSticky;
            navTranslucent &= !immersiveSticky;  // transient trumps translucent
            boolean isKeyguardShowing = isStatusBarKeyguard() && !mKeyguardOccluded;
            if (!isKeyguardShowing) {
                navTranslucent &= areTranslucentBarsAllowed();
            }
            boolean statusBarExpandedNotKeyguard = !isKeyguardShowing && mStatusBar != null
                    && mStatusBar.getAttrs().height == MATCH_PARENT
                    && mStatusBar.getAttrs().width == MATCH_PARENT;

            // When the navigation bar isn't visible, we put up a fake
            // input window to catch all touch events.  This way we can
            // detect when the user presses anywhere to bring back the nav
            // bar and ensure the application doesn't see the event.
            if (navVisible || navAllowedHidden) {
                if (mInputConsumer != null) {
                    mHandler.sendMessage(
                            mHandler.obtainMessage(MSG_DISPOSE_INPUT_CONSUMER, mInputConsumer));
                    mInputConsumer = null;
                }
            } else if (mInputConsumer == null) {
                mInputConsumer = mWindowManagerFuncs.createInputConsumer(mHandler.getLooper(),
                        INPUT_CONSUMER_NAVIGATION,
                        (channel, looper) -> new HideNavInputEventReceiver(channel, looper));
                // As long as mInputConsumer is active, hover events are not dispatched to the app
                // and the pointer icon is likely to become stale. Hide it to avoid confusion.
                InputManager.getInstance().setPointerIconType(PointerIcon.TYPE_NULL);
            }

            // For purposes of positioning and showing the nav bar, if we have
            // decided that it can't be hidden (because of the screen aspect ratio),
            // then take that into account.
            navVisible |= !canHideNavigationBar();

            boolean updateSysUiVisibility = layoutNavigationBar(displayWidth, displayHeight,
                    displayRotation, uiMode, overscanLeft, overscanRight, overscanBottom, dcf, navVisible, navTranslucent,
                    navAllowedHidden, statusBarExpandedNotKeyguard);
            if (DEBUG_LAYOUT) Slog.i(TAG, String.format("mDock rect: (%d,%d - %d,%d)",
                    mDockLeft, mDockTop, mDockRight, mDockBottom));
            updateSysUiVisibility |= layoutStatusBar(pf, df, of, vf, dcf, sysui, isKeyguardShowing);
            if (updateSysUiVisibility) {
                updateSystemUiVisibilityLw();
            }
        }
    }

    private boolean layoutStatusBar(Rect pf, Rect df, Rect of, Rect vf, Rect dcf, int sysui,
            boolean isKeyguardShowing) {
        // decide where the status bar goes ahead of time
        if (mStatusBar != null) {
            // apply any navigation bar insets
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenWidth + mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenHeight
                    + mUnrestrictedScreenTop;
            vf.left = mStableLeft;
            vf.top = mStableTop;
            vf.right = mStableRight;
            vf.bottom = mStableBottom;

            mStatusBarLayer = mStatusBar.getSurfaceLayer();

            // Let the status bar determine its size.
            mStatusBar.computeFrameLw(pf /* parentFrame */, df /* displayFrame */,
                    vf /* overlayFrame */, vf /* contentFrame */, vf /* visibleFrame */,
                    dcf /* decorFrame */, vf /* stableFrame */, vf /* outsetFrame */);

            // For layout, the status bar is always at the top with our fixed height.
            mStableTop = mUnrestrictedScreenTop + mStatusBarHeight;

            boolean statusBarTransient = (sysui & View.STATUS_BAR_TRANSIENT) != 0;
            boolean statusBarTranslucent = (sysui
                    & (View.STATUS_BAR_TRANSLUCENT | View.STATUS_BAR_TRANSPARENT)) != 0;
            if (!isKeyguardShowing) {
                statusBarTranslucent &= areTranslucentBarsAllowed();
            }

            // If the status bar is hidden, we don't want to cause
            // windows behind it to scroll.
            if (mStatusBar.isVisibleLw() && !statusBarTransient) {
                // Status bar may go away, so the screen area it occupies
                // is available to apps but just covering them when the
                // status bar is visible.
                mDockTop = mUnrestrictedScreenTop + mStatusBarHeight;

                mContentTop = mVoiceContentTop = mCurTop = mDockTop;
                mContentBottom = mVoiceContentBottom = mCurBottom = mDockBottom;
                mContentLeft = mVoiceContentLeft = mCurLeft = mDockLeft;
                mContentRight = mVoiceContentRight = mCurRight = mDockRight;

                if (DEBUG_LAYOUT) Slog.v(TAG, "Status bar: " +
                        String.format(
                                "dock=[%d,%d][%d,%d] content=[%d,%d][%d,%d] cur=[%d,%d][%d,%d]",
                                mDockLeft, mDockTop, mDockRight, mDockBottom,
                                mContentLeft, mContentTop, mContentRight, mContentBottom,
                                mCurLeft, mCurTop, mCurRight, mCurBottom));
            }
            if (mStatusBar.isVisibleLw() && !mStatusBar.isAnimatingLw()
                    && !statusBarTransient && !statusBarTranslucent
                    && !mStatusBarController.wasRecentlyTranslucent()) {
                // If the opaque status bar is currently requested to be visible,
                // and not in the process of animating on or off, then
                // we can tell the app that it is covered by it.
                mSystemTop = mUnrestrictedScreenTop + mStatusBarHeight;
            }
            if (mStatusBarController.checkHiddenLw()) {
                return true;
            }
        }
        return false;
    }

    private void notifyLeftInLandscapeChanged(boolean isOnLeft) {
        IStatusBarService sbar = getStatusBarService();
        if (sbar != null) {
            try {
                sbar.leftInLandscapeChanged(isOnLeft);
            } catch (RemoteException e1) {
                // oops, no statusbar. Ignore event.
            }
        }
    }

    private boolean layoutNavigationBar(int displayWidth, int displayHeight, int displayRotation,
            int uiMode, int overscanLeft, int overscanRight, int overscanBottom, Rect dcf,
            boolean navVisible, boolean navTranslucent, boolean navAllowedHidden,
            boolean statusBarExpandedNotKeyguard) {
        if (mNavigationBar != null) {
            boolean transientNavBarShowing = mNavigationBarController.isTransientShowing();
            // Force the navigation bar to its appropriate place and
            // size.  We need to do this directly, instead of relying on
            // it to bubble up from the nav bar, because this needs to
            // change atomically with screen rotations.
            int lastNavbarPosition = mNavigationBarPosition;
            mNavigationBarPosition = navigationBarPosition(displayWidth, displayHeight,
                    displayRotation);
            // broadcast left in landscape changes to listeners
            if (lastNavbarPosition == NAV_BAR_LEFT && mNavigationBarPosition != NAV_BAR_LEFT) {
                notifyLeftInLandscapeChanged(false);
            } else if (lastNavbarPosition != NAV_BAR_LEFT
                    && mNavigationBarPosition == NAV_BAR_LEFT) {
                notifyLeftInLandscapeChanged(true);
            }
            if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                // It's a system nav bar or a portrait screen; nav bar goes on bottom.
                int top = displayHeight - overscanBottom
                        - getNavigationBarHeight(displayRotation, uiMode);
                mTmpNavigationFrame.set(0, top, displayWidth, displayHeight - overscanBottom);
                mStableBottom = mStableFullscreenBottom = mTmpNavigationFrame.top;
                if (transientNavBarShowing) {
                    mNavigationBarController.setBarShowingLw(true);
                } else if (navVisible) {
                    mNavigationBarController.setBarShowingLw(true);
                    mDockBottom = mTmpNavigationFrame.top;
                    mRestrictedScreenHeight = mDockBottom - mRestrictedScreenTop;
                    mRestrictedOverscanScreenHeight = mDockBottom - mRestrictedOverscanScreenTop;
                } else {
                    // We currently want to hide the navigation UI - unless we expanded the status
                    // bar.
                    mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard
                            && !immersiveModeImplementsPie());
                }
                if (navVisible && !navTranslucent && !navAllowedHidden
                        && !mNavigationBar.isAnimatingLw()
                        && !mNavigationBarController.wasRecentlyTranslucent()) {
                    // If the opaque nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemBottom = mTmpNavigationFrame.top;
                }
            } else if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                // Landscape screen; nav bar goes to the right.
                int left = displayWidth - overscanRight
                        - getNavigationBarWidth(displayRotation, uiMode);
                mTmpNavigationFrame.set(left, 0, displayWidth - overscanRight, displayHeight);
                mStableRight = mStableFullscreenRight = mTmpNavigationFrame.left;
                if (transientNavBarShowing) {
                    mNavigationBarController.setBarShowingLw(true);
                } else if (navVisible) {
                    mNavigationBarController.setBarShowingLw(true);
                    mDockRight = mTmpNavigationFrame.left;
                    mRestrictedScreenWidth = mDockRight - mRestrictedScreenLeft;
                    mRestrictedOverscanScreenWidth = mDockRight - mRestrictedOverscanScreenLeft;
                } else {
                    // We currently want to hide the navigation UI - unless we expanded the status
                    // bar.
                    mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard
                            && !immersiveModeImplementsPie());
                }
                if (navVisible && !navTranslucent && !navAllowedHidden
                        && !mNavigationBar.isAnimatingLw()
                        && !mNavigationBarController.wasRecentlyTranslucent()) {
                    // If the nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemRight = mTmpNavigationFrame.left;
                }
            } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                // Seascape screen; nav bar goes to the left.
                int right = overscanLeft + getNavigationBarWidth(displayRotation, uiMode);
                mTmpNavigationFrame.set(overscanLeft, 0, right, displayHeight);
                mStableLeft = mStableFullscreenLeft = mTmpNavigationFrame.right;
                if (transientNavBarShowing) {
                    mNavigationBarController.setBarShowingLw(true);
                } else if (navVisible) {
                    mNavigationBarController.setBarShowingLw(true);
                    mDockLeft = mTmpNavigationFrame.right;
                    // TODO: not so sure about those:
                    mRestrictedScreenLeft = mRestrictedOverscanScreenLeft = mDockLeft;
                    mRestrictedScreenWidth = mDockRight - mRestrictedScreenLeft;
                    mRestrictedOverscanScreenWidth = mDockRight - mRestrictedOverscanScreenLeft;
                } else {
                    // We currently want to hide the navigation UI - unless we expanded the status
                    // bar.
                    mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard
                            && !immersiveModeImplementsPie());
                }
                if (navVisible && !navTranslucent && !navAllowedHidden
                        && !mNavigationBar.isAnimatingLw()
                        && !mNavigationBarController.wasRecentlyTranslucent()) {
                    // If the nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemLeft = mTmpNavigationFrame.right;
                }
            }
            // Make sure the content and current rectangles are updated to
            // account for the restrictions from the navigation bar.
            mContentTop = mVoiceContentTop = mCurTop = mDockTop;
            mContentBottom = mVoiceContentBottom = mCurBottom = mDockBottom;
            mContentLeft = mVoiceContentLeft = mCurLeft = mDockLeft;
            mContentRight = mVoiceContentRight = mCurRight = mDockRight;
            mStatusBarLayer = mNavigationBar.getSurfaceLayer();
            // And compute the final frame.
            mNavigationBar.computeFrameLw(mTmpNavigationFrame, mTmpNavigationFrame,
                    mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, dcf,
                    mTmpNavigationFrame, mTmpNavigationFrame);
            if (DEBUG_LAYOUT) Slog.i(TAG, "mNavigationBar frame: " + mTmpNavigationFrame);
            if (mNavigationBarController.checkHiddenLw()) {
                return true;
            }
        }
        return false;
    }

    private int navigationBarPosition(int displayWidth, int displayHeight, int displayRotation) {
        if (mNavigationBarCanMove && displayWidth > displayHeight) {
            if (displayRotation == Surface.ROTATION_270) {
                return NAV_BAR_LEFT;
            } else {
                return NAV_BAR_RIGHT;
            }
        }
        return NAV_BAR_BOTTOM;
    }

    /** {@inheritDoc} */
    @Override
    public int getSystemDecorLayerLw() {
        if (mStatusBar != null && mStatusBar.isVisibleLw()) {
            return mStatusBar.getSurfaceLayer();
        }

        if (mNavigationBar != null && mNavigationBar.isVisibleLw()) {
            return mNavigationBar.getSurfaceLayer();
        }

        return 0;
    }

    @Override
    public void getContentRectLw(Rect r) {
        r.set(mContentLeft, mContentTop, mContentRight, mContentBottom);
    }

    void setAttachedWindowFrames(WindowState win, int fl, int adjust, WindowState attached,
            boolean insetDecors, Rect pf, Rect df, Rect of, Rect cf, Rect vf) {
        if (win.getSurfaceLayer() > mDockLayer && attached.getSurfaceLayer() < mDockLayer) {
            // Here's a special case: if this attached window is a panel that is
            // above the dock window, and the window it is attached to is below
            // the dock window, then the frames we computed for the window it is
            // attached to can not be used because the dock is effectively part
            // of the underlying window and the attached window is floating on top
            // of the whole thing.  So, we ignore the attached window and explicitly
            // compute the frames that would be appropriate without the dock.
            df.left = of.left = cf.left = vf.left = mDockLeft;
            df.top = of.top = cf.top = vf.top = mDockTop;
            df.right = of.right = cf.right = vf.right = mDockRight;
            df.bottom = of.bottom = cf.bottom = vf.bottom = mDockBottom;
        } else {
            // The effective display frame of the attached window depends on
            // whether it is taking care of insetting its content.  If not,
            // we need to use the parent's content frame so that the entire
            // window is positioned within that content.  Otherwise we can use
            // the overscan frame and let the attached window take care of
            // positioning its content appropriately.
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                // Set the content frame of the attached window to the parent's decor frame
                // (same as content frame when IME isn't present) if specifically requested by
                // setting {@link WindowManager.LayoutParams#FLAG_LAYOUT_ATTACHED_IN_DECOR} flag.
                // Otherwise, use the overscan frame.
                cf.set((fl & FLAG_LAYOUT_ATTACHED_IN_DECOR) != 0
                        ? attached.getContentFrameLw() : attached.getOverscanFrameLw());
            } else {
                // If the window is resizing, then we want to base the content
                // frame on our attached content frame to resize...  however,
                // things can be tricky if the attached window is NOT in resize
                // mode, in which case its content frame will be larger.
                // Ungh.  So to deal with that, make sure the content frame
                // we end up using is not covering the IM dock.
                cf.set(attached.getContentFrameLw());
                if (attached.isVoiceInteraction()) {
                    if (cf.left < mVoiceContentLeft) cf.left = mVoiceContentLeft;
                    if (cf.top < mVoiceContentTop) cf.top = mVoiceContentTop;
                    if (cf.right > mVoiceContentRight) cf.right = mVoiceContentRight;
                    if (cf.bottom > mVoiceContentBottom) cf.bottom = mVoiceContentBottom;
                } else if (attached.getSurfaceLayer() < mDockLayer) {
                    if (cf.left < mContentLeft) cf.left = mContentLeft;
                    if (cf.top < mContentTop) cf.top = mContentTop;
                    if (cf.right > mContentRight) cf.right = mContentRight;
                    if (cf.bottom > mContentBottom) cf.bottom = mContentBottom;
                }
            }
            df.set(insetDecors ? attached.getDisplayFrameLw() : cf);
            of.set(insetDecors ? attached.getOverscanFrameLw() : cf);
            vf.set(attached.getVisibleFrameLw());
        }
        // The LAYOUT_IN_SCREEN flag is used to determine whether the attached
        // window should be positioned relative to its parent or the entire
        // screen.
        pf.set((fl & FLAG_LAYOUT_IN_SCREEN) == 0
                ? attached.getFrameLw() : df);
    }

    private void applyForceImmersiveMode(int pfl, Rect r) {
<<<<<<< HEAD
        if ((pfl & WindowManager.LayoutParams.PRIVATE_FLAG_STATUS_HIDE_FORCED) != 0) {
            r.top = mForceImmersiveTop;
        }
        if ((pfl & WindowManager.LayoutParams.PRIVATE_FLAG_NAV_HIDE_FORCED) != 0) {
=======
        if ((pfl & PRIVATE_FLAG_STATUS_HIDE_FORCED) != 0) {
            r.top = mForceImmersiveTop;
        }
        if ((pfl & PRIVATE_FLAG_NAV_HIDE_FORCED) != 0) {
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
            if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                r.bottom = mForceImmersiveBottom;
            } else {
                r.right = mForceImmersiveRight;
            }
        }
    }

    private void applyStableConstraints(int sysui, int fl, Rect r) {
        if ((sysui & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
            // If app is requesting a stable layout, don't let the
            // content insets go below the stable values.
            if ((fl & FLAG_FULLSCREEN) != 0) {
                if (r.left < mStableFullscreenLeft) r.left = mStableFullscreenLeft;
                if (r.top < mStableFullscreenTop) r.top = mStableFullscreenTop;
                if (r.right > mStableFullscreenRight) r.right = mStableFullscreenRight;
                if (r.bottom > mStableFullscreenBottom) r.bottom = mStableFullscreenBottom;
            } else {
                if (r.left < mStableLeft) r.left = mStableLeft;
                if (r.top < mStableTop) r.top = mStableTop;
                if (r.right > mStableRight) r.right = mStableRight;
                if (r.bottom > mStableBottom) r.bottom = mStableBottom;
            }
        }
    }

    private boolean canReceiveInput(WindowState win) {
        boolean notFocusable =
                (win.getAttrs().flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0;
        boolean altFocusableIm =
                (win.getAttrs().flags & WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) != 0;
        boolean notFocusableForIm = notFocusable ^ altFocusableIm;
        return !notFocusableForIm;
    }

    /** {@inheritDoc} */
    @Override
    public void layoutWindowLw(WindowState win, WindowState attached) {
        // We've already done the navigation bar and status bar. If the status bar can receive
        // input, we need to layout it again to accomodate for the IME window.
        if ((win == mStatusBar && !canReceiveInput(win)) || win == mNavigationBar) {
            return;
        }
        final WindowManager.LayoutParams attrs = win.getAttrs();
        final boolean isDefaultDisplay = win.isDefaultDisplay();
        final boolean needsToOffsetInputMethodTarget = isDefaultDisplay &&
                (win == mLastInputMethodTargetWindow && mLastInputMethodWindow != null);
        if (needsToOffsetInputMethodTarget) {
            if (DEBUG_LAYOUT) Slog.i(TAG, "Offset ime target window by the last ime window state");
            offsetInputMethodWindowLw(mLastInputMethodWindow);
        }

        final int fl = WindowManagerPolicyControl.getWindowFlags(win, attrs);
        final int pfl = WindowManagerPolicyControl.getPrivateWindowFlags(win, attrs);
        final int sim = attrs.softInputMode;
        final int sysUiFl = WindowManagerPolicyControl.getSystemUiVisibility(win, null);

        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect of = mTmpOverscanFrame;
        final Rect cf = mTmpContentFrame;
        final Rect vf = mTmpVisibleFrame;
        final Rect dcf = mTmpDecorFrame;
        final Rect sf = mTmpStableFrame;
        Rect osf = null;
        dcf.setEmpty();

        final boolean hasNavBar = (isDefaultDisplay && hasNavigationBar()
                && mNavigationBar != null && mNavigationBar.isVisibleLw());

        final int adjust = sim & SOFT_INPUT_MASK_ADJUST;

        if (isDefaultDisplay) {
            sf.set(mStableLeft, mStableTop, mStableRight, mStableBottom);
        } else {
            sf.set(mOverscanLeft, mOverscanTop, mOverscanRight, mOverscanBottom);
        }

        if (!isDefaultDisplay) {
            if (attached != null) {
                // If this window is attached to another, our display
                // frame is the same as the one we are attached to.
                setAttachedWindowFrames(win, fl, adjust, attached, true, pf, df, of, cf, vf);
            } else {
                // Give the window full screen.
                pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                pf.right = df.right = of.right = cf.right
                        = mOverscanScreenLeft + mOverscanScreenWidth;
                pf.bottom = df.bottom = of.bottom = cf.bottom
                        = mOverscanScreenTop + mOverscanScreenHeight;
            }
        } else if (attrs.type == TYPE_INPUT_METHOD) {
            pf.left = df.left = of.left = cf.left = vf.left = mDockLeft;
            pf.top = df.top = of.top = cf.top = vf.top = mDockTop;
            pf.right = df.right = of.right = cf.right = vf.right = mDockRight;
            // IM dock windows layout below the nav bar...
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            // ...with content insets above the nav bar
            cf.bottom = vf.bottom = mStableBottom;
            if (mStatusBar != null && mFocusedWindow == mStatusBar && canReceiveInput(mStatusBar)) {
                // The status bar forces the navigation bar while it's visible. Make sure the IME
                // avoids the navigation bar in that case.
                if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                    pf.right = df.right = of.right = cf.right = vf.right = mStableRight;
                } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                    pf.left = df.left = of.left = cf.left = vf.left = mStableLeft;
                }
            }
            // IM dock windows always go to the bottom of the screen.
            attrs.gravity = Gravity.BOTTOM;
            mDockLayer = win.getSurfaceLayer();
        } else if (attrs.type == TYPE_VOICE_INTERACTION) {
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                cf.left = mDockLeft;
                cf.top = mDockTop;
                cf.right = mDockRight;
                cf.bottom = mDockBottom;
            } else {
                cf.left = mContentLeft;
                cf.top = mContentTop;
                cf.right = mContentRight;
                cf.bottom = mContentBottom;
            }
            if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                vf.left = mCurLeft;
                vf.top = mCurTop;
                vf.right = mCurRight;
                vf.bottom = mCurBottom;
            } else {
                vf.set(cf);
            }
        } else if (attrs.type == TYPE_WALLPAPER) {
           layoutWallpaper(win, pf, df, of, cf);
        } else if (win == mStatusBar) {
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenWidth + mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenHeight + mUnrestrictedScreenTop;
            cf.left = vf.left = mStableLeft;
            cf.top = vf.top = mStableTop;
            cf.right = vf.right = mStableRight;
            vf.bottom = mStableBottom;

            if (adjust == SOFT_INPUT_ADJUST_RESIZE) {
                cf.bottom = mContentBottom;
            } else {
                cf.bottom = mDockBottom;
                vf.bottom = mContentBottom;
            }
        } else {

            // Default policy decor for the default display
            dcf.left = mSystemLeft;
            dcf.top = mSystemTop;
            dcf.right = mSystemRight;
            dcf.bottom = mSystemBottom;
            final boolean inheritTranslucentDecor = (attrs.privateFlags
                    & WindowManager.LayoutParams.PRIVATE_FLAG_INHERIT_TRANSLUCENT_DECOR) != 0;
            final boolean isAppWindow =
                    attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW &&
                    attrs.type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            final boolean topAtRest =
                    win == mTopFullscreenOpaqueWindowState && !win.isAnimatingLw();
            if (isAppWindow && !inheritTranslucentDecor && !topAtRest) {
                if ((sysUiFl & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
                        && (fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0
                        && (fl & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) == 0
                        && (fl & WindowManager.LayoutParams.
                                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0
                        && (pfl & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) == 0) {
                    // Ensure policy decor includes status bar
                    dcf.top = mStableTop;
                }
                if ((fl & WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION) == 0
                        && (sysUiFl & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                        && (fl & WindowManager.LayoutParams.
                                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0) {
                    // Ensure policy decor includes navigation bar
                    dcf.bottom = mStableBottom;
                    dcf.right = mStableRight;
                }
            }

            if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                    == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle()
                            + "): IN_SCREEN, INSET_DECOR");
                // This is the case for a normal activity window: we want it
                // to cover all of the screen space, and it can take care of
                // moving its contents to account for screen decorations that
                // intrude into that space.
                if (attached != null) {
                    // If this window is attached to another, our display
                    // frame is the same as the one we are attached to.
                    setAttachedWindowFrames(win, fl, adjust, attached, true, pf, df, of, cf, vf);
                } else {
                    if (attrs.type == TYPE_STATUS_BAR_PANEL
                            || attrs.type == TYPE_STATUS_BAR_SUB_PANEL) {
                        // Status bar panels are the only windows who can go on top of
                        // the status bar.  They are protected by the STATUS_BAR_SERVICE
                        // permission, so they have the same privileges as the status
                        // bar itself.
                        //
                        // However, they should still dodge the navigation bar if it exists.

                        pf.left = df.left = of.left = hasNavBar
                                ? mDockLeft : mUnrestrictedScreenLeft;
                        pf.top = df.top = of.top = mUnrestrictedScreenTop;
                        pf.right = df.right = of.right = hasNavBar
                                ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                : mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        pf.bottom = df.bottom = of.bottom = hasNavBar
                                ? mRestrictedScreenTop+mRestrictedScreenHeight
                                : mUnrestrictedScreenTop + mUnrestrictedScreenHeight;

                        if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                                        "Laying out status bar window: (%d,%d - %d,%d)",
                                        pf.left, pf.top, pf.right, pf.bottom));
                    } else if ((fl & FLAG_LAYOUT_IN_OVERSCAN) != 0
                            && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                        // Asking to layout into the overscan region, so give it that pure
                        // unrestricted area.
                        pf.left = df.left = of.left = mOverscanScreenLeft;
                        pf.top = df.top = of.top = mOverscanScreenTop;
                        pf.right = df.right = of.right = mOverscanScreenLeft + mOverscanScreenWidth;
                        pf.bottom = df.bottom = of.bottom = mOverscanScreenTop
                                + mOverscanScreenHeight;
                    } else if (canHideNavigationBar()
                            && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                            && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                        // Asking for layout as if the nav bar is hidden, lets the
                        // application extend into the unrestricted overscan screen area.  We
                        // only do this for application windows to ensure no window that
                        // can be above the nav bar can do this.
                        pf.left = df.left = mOverscanScreenLeft;
                        pf.top = df.top = mOverscanScreenTop;
                        pf.right = df.right = mOverscanScreenLeft + mOverscanScreenWidth;
                        pf.bottom = df.bottom = mOverscanScreenTop + mOverscanScreenHeight;
                        // We need to tell the app about where the frame inside the overscan
                        // is, so it can inset its content by that amount -- it didn't ask
                        // to actually extend itself into the overscan region.
                        of.left = mUnrestrictedScreenLeft;
                        of.top = mUnrestrictedScreenTop;
                        of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    } else {
                        pf.left = df.left = mRestrictedOverscanScreenLeft;
                        pf.top = df.top = mRestrictedOverscanScreenTop;
                        pf.right = df.right = mRestrictedOverscanScreenLeft
                                + mRestrictedOverscanScreenWidth;
                        pf.bottom = df.bottom = mRestrictedOverscanScreenTop
                                + mRestrictedOverscanScreenHeight;
                        // We need to tell the app about where the frame inside the overscan
                        // is, so it can inset its content by that amount -- it didn't ask
                        // to actually extend itself into the overscan region.
                        of.left = mUnrestrictedScreenLeft;
                        of.top = mUnrestrictedScreenTop;
                        of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    }

                    if ((fl & FLAG_FULLSCREEN) == 0
<<<<<<< HEAD
                            || (pfl & WindowManager.LayoutParams.PRIVATE_FLAG_WAS_NOT_FULLSCREEN) != 0) {
=======
                            || (pfl & PRIVATE_FLAG_WAS_NOT_FULLSCREEN) != 0) {
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                        if (win.isVoiceInteraction()) {
                            cf.left = mVoiceContentLeft;
                            cf.top = mVoiceContentTop;
                            cf.right = mVoiceContentRight;
                            cf.bottom = mVoiceContentBottom;
                        } else {
                            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                                cf.left = mDockLeft;
                                cf.top = mDockTop;
                                cf.right = mDockRight;
                                cf.bottom = mDockBottom;
                            } else {
                                cf.left = mContentLeft;
                                cf.top = mContentTop;
                                cf.right = mContentRight;
                                cf.bottom = mContentBottom;
                            }
<<<<<<< HEAD
=======

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                            applyForceImmersiveMode(pfl, cf);
                        }
                    } else {
                        // Full screen windows are always given a layout that is as if the
                        // status bar and other transient decors are gone.  This is to avoid
                        // bad states when moving from a window that is not hding the
                        // status bar to one that is.
                        cf.left = mRestrictedScreenLeft;
                        cf.top = mRestrictedScreenTop;
                        cf.right = mRestrictedScreenLeft + mRestrictedScreenWidth;
                        cf.bottom = mRestrictedScreenTop + mRestrictedScreenHeight;
                    }
                    applyStableConstraints(sysUiFl, fl, cf);
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
<<<<<<< HEAD
=======

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                    applyForceImmersiveMode(pfl, vf);
                }
            } else if ((fl & FLAG_LAYOUT_IN_SCREEN) != 0 || (sysUiFl
                    & (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)) != 0) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle() +
                        "): IN_SCREEN");
                // A window that has requested to fill the entire screen just
                // gets everything, period.
                if (attrs.type == TYPE_STATUS_BAR_PANEL
                        || attrs.type == TYPE_STATUS_BAR_SUB_PANEL) {
                    pf.left = df.left = of.left = cf.left = hasNavBar
                            ? mDockLeft : mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = hasNavBar
                            ? mRestrictedScreenLeft + mRestrictedScreenWidth
                            : mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = hasNavBar
                            ? mRestrictedScreenTop + mRestrictedScreenHeight
                            : mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                            "Laying out IN_SCREEN status bar window: (%d,%d - %d,%d)",
                            pf.left, pf.top, pf.right, pf.bottom));
                } else if (attrs.type == TYPE_VOLUME_OVERLAY) {
                    // Volume overlay covers everything, including the status and navbar
                    pf.left = df.left = of.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right =
                            mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom =
                            mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                                    "Laying out IN_SCREEN status bar window: (%d,%d - %d,%d)",
                                    pf.left, pf.top, pf.right, pf.bottom));
                } else if (attrs.type == TYPE_NAVIGATION_BAR
                        || attrs.type == TYPE_NAVIGATION_BAR_PANEL) {
                    // The navigation bar has Real Ultimate Power.
                    pf.left = df.left = of.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                                    "Laying out navigation bar window: (%d,%d - %d,%d)",
                                    pf.left, pf.top, pf.right, pf.bottom));
                } else if ((attrs.type == TYPE_SECURE_SYSTEM_OVERLAY
                                || attrs.type == TYPE_BOOT_PROGRESS
                                || attrs.type == TYPE_SCREENSHOT)
                        && ((fl & FLAG_FULLSCREEN) != 0)) {
                    // Fullscreen secure system overlays get what they ask for. Screenshot region
                    // selection overlay should also expand to full screen.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right = mOverscanScreenLeft
                            + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mOverscanScreenTop
                            + mOverscanScreenHeight;
                } else if (attrs.type == TYPE_BOOT_PROGRESS) {
                    // Boot progress screen always covers entire display.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right = mOverscanScreenLeft
                            + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mOverscanScreenTop
                            + mOverscanScreenHeight;
                } else if ((fl & FLAG_LAYOUT_IN_OVERSCAN) != 0
                        && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                        && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                    // Asking to layout into the overscan region, so give it that pure
                    // unrestricted area.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right
                            = mOverscanScreenLeft + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom
                            = mOverscanScreenTop + mOverscanScreenHeight;
                } else if (canHideNavigationBar()
                        && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                        && (attrs.type == TYPE_STATUS_BAR
                            || attrs.type == TYPE_TOAST
                            || attrs.type == TYPE_DOCK_DIVIDER
                            || attrs.type == TYPE_VOICE_INTERACTION_STARTING
                            || (attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW))) {
                    // Asking for layout as if the nav bar is hidden, lets the
                    // application extend into the unrestricted screen area.  We
                    // only do this for application windows (or toasts) to ensure no window that
                    // can be above the nav bar can do this.
                    // XXX This assumes that an app asking for this will also
                    // ask for layout in only content.  We can't currently figure out
                    // what the screen would be if only laying out to hide the nav bar.
                    pf.left = df.left = of.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                } else if ((sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0) {
                    pf.left = df.left = of.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top  = mRestrictedScreenTop;
                    pf.right = df.right = of.right = mRestrictedScreenLeft + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                    if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                        cf.left = mDockLeft;
                        cf.top = mDockTop;
                        cf.right = mDockRight;
                        cf.bottom = mDockBottom;
                    } else {
                        cf.left = mContentLeft;
                        cf.top = mContentTop;
                        cf.right = mContentRight;
                        cf.bottom = mContentBottom;
                    }
                } else {
                    pf.left = df.left = of.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mRestrictedScreenLeft
                            + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                }

                applyStableConstraints(sysUiFl, fl, cf);

                if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                    vf.left = mCurLeft;
                    vf.top = mCurTop;
                    vf.right = mCurRight;
                    vf.bottom = mCurBottom;
                } else {
                    vf.set(cf);
                }
<<<<<<< HEAD
=======

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                applyForceImmersiveMode(pfl, vf);
            } else if (attached != null) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle() +
                        "): attached to " + attached);
                // A child window should be placed inside of the same visible
                // frame that its parent had.
                setAttachedWindowFrames(win, fl, adjust, attached, false, pf, df, of, cf, vf);
            } else {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle() +
                        "): normal window");
                // Otherwise, a normal window must be placed inside the content
                // of all screen decorations.
                if (attrs.type == TYPE_STATUS_BAR_PANEL || attrs.type == TYPE_VOLUME_OVERLAY) {
                    // Status bar panels and the volume dialog are the only windows who can go on
                    // top of the status bar.  They are protected by the STATUS_BAR_SERVICE
                    // permission, so they have the same privileges as the status
                    // bar itself.
                    pf.left = df.left = of.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mRestrictedScreenLeft
                            + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                } else if (attrs.type == TYPE_TOAST || attrs.type == TYPE_SYSTEM_ALERT) {
                    // These dialogs are stable to interim decor changes.
                    pf.left = df.left = of.left = cf.left = mStableLeft;
                    pf.top = df.top = of.top = cf.top = mStableTop;
                    pf.right = df.right = of.right = cf.right = mStableRight;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mStableBottom;
                } else {
                    pf.left = mContentLeft;
                    pf.top = mContentTop;
                    pf.right = mContentRight;
                    pf.bottom = mContentBottom;
                    if (win.isVoiceInteraction()) {
                        df.left = of.left = cf.left = mVoiceContentLeft;
                        df.top = of.top = cf.top = mVoiceContentTop;
                        df.right = of.right = cf.right = mVoiceContentRight;
                        df.bottom = of.bottom = cf.bottom = mVoiceContentBottom;
                    } else if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                        df.left = of.left = cf.left = mDockLeft;
                        df.top = of.top = cf.top = mDockTop;
                        df.right = of.right = cf.right = mDockRight;
                        df.bottom = of.bottom = cf.bottom = mDockBottom;
                    } else {
                        df.left = of.left = cf.left = mContentLeft;
                        df.top = of.top = cf.top = mContentTop;
                        df.right = of.right = cf.right = mContentRight;
                        df.bottom = of.bottom = cf.bottom = mContentBottom;
                    }
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
<<<<<<< HEAD
=======

>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
                    applyForceImmersiveMode(pfl, vf);
                }
            }
        }

        // TYPE_SYSTEM_ERROR is above the NavigationBar so it can't be allowed to extend over it.
        // Also, we don't allow windows in multi-window mode to extend out of the screen.
        if ((fl & FLAG_LAYOUT_NO_LIMITS) != 0 && attrs.type != TYPE_SYSTEM_ERROR
                && !win.isInMultiWindowMode()) {
            df.left = df.top = -10000;
            df.right = df.bottom = 10000;
            if (attrs.type != TYPE_WALLPAPER) {
                of.left = of.top = cf.left = cf.top = vf.left = vf.top = -10000;
                of.right = of.bottom = cf.right = cf.bottom = vf.right = vf.bottom = 10000;
            }
        }

        // If the device has a chin (e.g. some watches), a dead area at the bottom of the screen we
        // need to provide information to the clients that want to pretend that you can draw there.
        // We only want to apply outsets to certain types of windows. For example, we never want to
        // apply the outsets to floating dialogs, because they wouldn't make sense there.
        final boolean useOutsets = shouldUseOutsets(attrs, fl);
        if (isDefaultDisplay && useOutsets) {
            osf = mTmpOutsetFrame;
            osf.set(cf.left, cf.top, cf.right, cf.bottom);
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(mContext.getResources());
            if (outset > 0) {
                int rotation = mDisplayRotation;
                if (rotation == Surface.ROTATION_0) {
                    osf.bottom += outset;
                } else if (rotation == Surface.ROTATION_90) {
                    osf.right += outset;
                } else if (rotation == Surface.ROTATION_180) {
                    osf.top -= outset;
                } else if (rotation == Surface.ROTATION_270) {
                    osf.left -= outset;
                }
                if (DEBUG_LAYOUT) Slog.v(TAG, "applying bottom outset of " + outset
                        + " with rotation " + rotation + ", result: " + osf);
            }
        }

        if (DEBUG_LAYOUT) Slog.v(TAG, "Compute frame " + attrs.getTitle()
                + ": sim=#" + Integer.toHexString(sim)
                + " attach=" + attached + " type=" + attrs.type
                + String.format(" flags=0x%08x", fl)
                + " pf=" + pf.toShortString() + " df=" + df.toShortString()
                + " of=" + of.toShortString()
                + " cf=" + cf.toShortString() + " vf=" + vf.toShortString()
                + " dcf=" + dcf.toShortString()
                + " sf=" + sf.toShortString()
                + " osf=" + (osf == null ? "null" : osf.toShortString()));

        win.computeFrameLw(pf, df, of, cf, vf, dcf, sf, osf);

        // Dock windows carve out the bottom of the screen, so normal windows
        // can't appear underneath them.
        if (attrs.type == TYPE_INPUT_METHOD && win.isVisibleLw()
                && !win.getGivenInsetsPendingLw()) {
            setLastInputMethodWindowLw(null, null);
            offsetInputMethodWindowLw(win);
        }
        if (attrs.type == TYPE_VOICE_INTERACTION && win.isVisibleLw()
                && !win.getGivenInsetsPendingLw()) {
            offsetVoiceInputWindowLw(win);
        }
    }

    private void layoutWallpaper(WindowState win, Rect pf, Rect df, Rect of, Rect cf) {

        // The wallpaper also has Real Ultimate Power, but we want to tell
        // it about the overscan area.
        pf.left = df.left = mOverscanScreenLeft;
        pf.top = df.top = mOverscanScreenTop;
        pf.right = df.right = mOverscanScreenLeft + mOverscanScreenWidth;
        pf.bottom = df.bottom = mOverscanScreenTop + mOverscanScreenHeight;
        of.left = cf.left = mUnrestrictedScreenLeft;
        of.top = cf.top = mUnrestrictedScreenTop;
        of.right = cf.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
        of.bottom = cf.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
    }

    private boolean immersiveModeImplementsPie() {
        return mPieState;
    }

    private void offsetInputMethodWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top);
        top += win.getGivenContentInsetsLw().top;
        if (mContentBottom > top) {
            mContentBottom = top;
        }
        if (mForceImmersiveBottom > top) {
            mForceImmersiveBottom = top;
        }
        if (mVoiceContentBottom > top) {
            mVoiceContentBottom = top;
        }
        top = win.getVisibleFrameLw().top;
        top += win.getGivenVisibleInsetsLw().top;
        if (mCurBottom > top) {
            mCurBottom = top;
        }
        if (DEBUG_LAYOUT) Slog.v(TAG, "Input method: mDockBottom="
                + mDockBottom + " mContentBottom="
                + mContentBottom + " mCurBottom=" + mCurBottom);
    }

    private void offsetVoiceInputWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top);
        top += win.getGivenContentInsetsLw().top;
        if (mVoiceContentBottom > top) {
            mVoiceContentBottom = top;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void finishLayoutLw() {
        return;
    }

    /** {@inheritDoc} */
    @Override
    public void beginPostLayoutPolicyLw(int displayWidth, int displayHeight) {
        mTopFullscreenOpaqueWindowState = null;
        mTopFullscreenOpaqueOrDimmingWindowState = null;
        mTopDockedOpaqueWindowState = null;
        mTopDockedOpaqueOrDimmingWindowState = null;
        mForceStatusBar = false;
        mForceStatusBarFromKeyguard = false;
        mForceStatusBarTransparent = false;
        mForcingShowNavBar = false;
        mForcingShowNavBarLayer = -1;

        mAllowLockscreenWhenOn = false;
        mShowingDream = false;
        mWindowSleepTokenNeeded = false;
    }

    /** {@inheritDoc} */
    @Override
    public void applyPostLayoutPolicyLw(WindowState win, WindowManager.LayoutParams attrs,
            WindowState attached, WindowState imeTarget) {
        final boolean affectsSystemUi = win.canAffectSystemUiFlags();
        if (DEBUG_LAYOUT) Slog.i(TAG, "Win " + win + ": affectsSystemUi=" + affectsSystemUi);
        applyKeyguardPolicyLw(win, imeTarget);
        final int fl = WindowManagerPolicyControl.getWindowFlags(win, attrs);
        if (mTopFullscreenOpaqueWindowState == null && affectsSystemUi
                && attrs.type == TYPE_INPUT_METHOD && !immersiveModeImplementsPie()) {
            mForcingShowNavBar = true;
            mForcingShowNavBarLayer = win.getSurfaceLayer();
        }
        if (attrs.type == TYPE_STATUS_BAR) {
            if ((attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                mForceStatusBarFromKeyguard = true;
            }
            if ((attrs.privateFlags & PRIVATE_FLAG_FORCE_STATUS_BAR_VISIBLE_TRANSPARENT) != 0) {
                mForceStatusBarTransparent = true;
            }
        }

        boolean appWindow = attrs.type >= FIRST_APPLICATION_WINDOW
                && attrs.type < FIRST_SYSTEM_WINDOW;
        final int stackId = win.getStackId();
        if (mTopFullscreenOpaqueWindowState == null && affectsSystemUi) {
            if ((fl & FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                mForceStatusBar = true;
            }
            if (attrs.type == TYPE_DREAM) {
                // If the lockscreen was showing when the dream started then wait
                // for the dream to draw before hiding the lockscreen.
                if (!mDreamingLockscreen
                        || (win.isVisibleLw() && win.hasDrawnLw())) {
                    mShowingDream = true;
                    appWindow = true;
                }
            }

            // For app windows that are not attached, we decide if all windows in the app they
            // represent should be hidden or if we should hide the lockscreen. For attached app
            // windows we defer the decision to the window it is attached to.
            if (appWindow && attached == null) {
                if (attrs.isFullscreen() && StackId.normallyFullscreenWindows(stackId)) {
                    if (DEBUG_LAYOUT) Slog.v(TAG, "Fullscreen window: " + win);
                    mTopFullscreenOpaqueWindowState = win;
                    if (mTopFullscreenOpaqueOrDimmingWindowState == null) {
                        mTopFullscreenOpaqueOrDimmingWindowState = win;
                    }
                    if ((fl & FLAG_ALLOW_LOCK_WHILE_SCREEN_ON) != 0) {
                        mAllowLockscreenWhenOn = true;
                    }
                }
            }
        }

        // Voice interaction overrides both top fullscreen and top docked.
        if (affectsSystemUi && win.getAttrs().type == TYPE_VOICE_INTERACTION) {
            if (mTopFullscreenOpaqueWindowState == null) {
                mTopFullscreenOpaqueWindowState = win;
                if (mTopFullscreenOpaqueOrDimmingWindowState == null) {
                    mTopFullscreenOpaqueOrDimmingWindowState = win;
                }
            }
            if (mTopDockedOpaqueWindowState == null) {
                mTopDockedOpaqueWindowState = win;
                if (mTopDockedOpaqueOrDimmingWindowState == null) {
                    mTopDockedOpaqueOrDimmingWindowState = win;
                }
            }
        }

        // Keep track of the window if it's dimming but not necessarily fullscreen.
        if (mTopFullscreenOpaqueOrDimmingWindowState == null && affectsSystemUi
                && win.isDimming() && StackId.normallyFullscreenWindows(stackId)) {
            mTopFullscreenOpaqueOrDimmingWindowState = win;
        }

        // We need to keep track of the top "fullscreen" opaque window for the docked stack
        // separately, because both the "real fullscreen" opaque window and the one for the docked
        // stack can control View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.
        if (mTopDockedOpaqueWindowState == null && affectsSystemUi && appWindow && attached == null
                && attrs.isFullscreen() && stackId == DOCKED_STACK_ID) {
            mTopDockedOpaqueWindowState = win;
            if (mTopDockedOpaqueOrDimmingWindowState == null) {
                mTopDockedOpaqueOrDimmingWindowState = win;
            }
        }

        // Also keep track of any windows that are dimming but not necessarily fullscreen in the
        // docked stack.
        if (mTopDockedOpaqueOrDimmingWindowState == null && affectsSystemUi && win.isDimming()
                && stackId == DOCKED_STACK_ID) {
            mTopDockedOpaqueOrDimmingWindowState = win;
        }

        // Take note if a window wants to acquire a sleep token.
        if (win.isVisibleLw() && (attrs.privateFlags & PRIVATE_FLAG_ACQUIRES_SLEEP_TOKEN) != 0
                && win.canAcquireSleepToken()) {
            mWindowSleepTokenNeeded = true;
        }
    }

    private void applyKeyguardPolicyLw(WindowState win, WindowState imeTarget) {
        if (canBeHiddenByKeyguardLw(win)) {
            if (shouldBeHiddenByKeyguard(win, imeTarget)) {
                win.hideLw(false /* doAnimation */);
            } else {
                win.showLw(false /* doAnimation */);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public int finishPostLayoutPolicyLw() {
        int changes = 0;
        boolean topIsFullscreen = false;

        final WindowManager.LayoutParams lp = (mTopFullscreenOpaqueWindowState != null)
                ? mTopFullscreenOpaqueWindowState.getAttrs()
                : null;

        // If we are not currently showing a dream then remember the current
        // lockscreen state.  We will use this to determine whether the dream
        // started while the lockscreen was showing and remember this state
        // while the dream is showing.
        if (!mShowingDream) {
            mDreamingLockscreen = isKeyguardShowingAndNotOccluded();
            if (mDreamingSleepTokenNeeded) {
                mDreamingSleepTokenNeeded = false;
                mHandler.obtainMessage(MSG_UPDATE_DREAMING_SLEEP_TOKEN, 0, 1).sendToTarget();
            }
        } else {
            if (!mDreamingSleepTokenNeeded) {
                mDreamingSleepTokenNeeded = true;
                mHandler.obtainMessage(MSG_UPDATE_DREAMING_SLEEP_TOKEN, 1, 1).sendToTarget();
            }
        }

        if (mStatusBar != null) {
            if (DEBUG_LAYOUT) Slog.i(TAG, "force=" + mForceStatusBar
                    + " forcefkg=" + mForceStatusBarFromKeyguard
                    + " top=" + mTopFullscreenOpaqueWindowState);
            boolean shouldBeTransparent = mForceStatusBarTransparent
                    && !mForceStatusBar
                    && !mForceStatusBarFromKeyguard;
            if (!shouldBeTransparent) {
                mStatusBarController.setShowTransparent(false /* transparent */);
            } else if (!mStatusBar.isVisibleLw()) {
                mStatusBarController.setShowTransparent(true /* transparent */);
            }

            WindowManager.LayoutParams statusBarAttrs = mStatusBar.getAttrs();
            boolean statusBarExpanded = statusBarAttrs.height == MATCH_PARENT
                    && statusBarAttrs.width == MATCH_PARENT;
            boolean topAppHidesStatusBar = topAppHidesStatusBar();
            if (mForceStatusBar || mForceStatusBarFromKeyguard || mForceStatusBarTransparent
                    || statusBarExpanded) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "Showing status bar: forced");
                if (mStatusBarController.setBarShowingLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT;
                }
                // Maintain fullscreen layout until incoming animation is complete.
                topIsFullscreen = mTopIsFullscreen && mStatusBar.isAnimatingLw();
                // Transient status bar on the lockscreen is not allowed
                if ((mForceStatusBarFromKeyguard || statusBarExpanded)
                        && mStatusBarController.isTransientShowing()) {
                    mStatusBarController.updateVisibilityLw(false /*transientAllowed*/,
                            mLastSystemUiFlags, mLastSystemUiFlags);
                }
                if (statusBarExpanded && !immersiveModeImplementsPie()) {
                    if (mNavigationBar != null) {
                        if (mNavigationBarController.setBarShowingLw(true)) {
                            changes |= FINISH_LAYOUT_REDO_LAYOUT;
                        }
                   }
                }
            } else if (mTopFullscreenOpaqueWindowState != null) {
                topIsFullscreen = topAppHidesStatusBar;
                // The subtle difference between the window for mTopFullscreenOpaqueWindowState
                // and mTopIsFullscreen is that mTopIsFullscreen is set only if the window
                // has the FLAG_FULLSCREEN set.  Not sure if there is another way that to be the
                // case though.
                if (mStatusBarController.isTransientShowing()) {
                    if (mStatusBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                } else if (topIsFullscreen
                        && !mWindowManagerInternal.isStackVisible(FREEFORM_WORKSPACE_STACK_ID)
                        && !mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID)) {
                    if (DEBUG_LAYOUT) Slog.v(TAG, "** HIDING status bar");
                    if (mStatusBarController.setBarShowingLw(false)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    } else {
                        if (DEBUG_LAYOUT) Slog.v(TAG, "Status bar already hiding");
                    }
                } else {
                    if (DEBUG_LAYOUT) Slog.v(TAG, "** SHOWING status bar: top is not fullscreen");
                    if (mStatusBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                    topAppHidesStatusBar = false;
                }
            }
            mStatusBarController.setTopAppHidesStatusBar(topAppHidesStatusBar);
        }

        if (mTopIsFullscreen != topIsFullscreen) {
            if (!topIsFullscreen) {
                // Force another layout when status bar becomes fully shown.
                changes |= FINISH_LAYOUT_REDO_LAYOUT;
            }
            mTopIsFullscreen = topIsFullscreen;
        }

        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            changes |= FINISH_LAYOUT_REDO_LAYOUT;
        }

        if (mShowingDream != mLastShowingDream) {
            mLastShowingDream = mShowingDream;
            mWindowManagerFuncs.notifyShowingDreamChanged();
        }

        updateWindowSleepToken();

        // update since mAllowLockscreenWhenOn might have changed
        updateLockScreenTimeout();
        updateEdgeGestureListenerState();
        return changes;
    }

    private void updateWindowSleepToken() {
        if (mWindowSleepTokenNeeded && !mLastWindowSleepTokenNeeded) {
            mHandler.removeCallbacks(mReleaseSleepTokenRunnable);
            mHandler.post(mAcquireSleepTokenRunnable);
        } else if (!mWindowSleepTokenNeeded && mLastWindowSleepTokenNeeded) {
            mHandler.removeCallbacks(mAcquireSleepTokenRunnable);
            mHandler.post(mReleaseSleepTokenRunnable);
        }
        mLastWindowSleepTokenNeeded = mWindowSleepTokenNeeded;
    }

    /**
     * @return Whether the top app should hide the statusbar based on the top fullscreen opaque
     *         window.
     */
    private boolean topAppHidesStatusBar() {
        if (mTopFullscreenOpaqueWindowState == null) {
            return false;
        }
        final int fl = WindowManagerPolicyControl.getWindowFlags(null,
                mTopFullscreenOpaqueWindowState.getAttrs());
        if (localLOGV) {
            Slog.d(TAG, "frame: " + mTopFullscreenOpaqueWindowState.getFrameLw()
                    + " shown position: "
                    + mTopFullscreenOpaqueWindowState.getShownPositionLw());
            Slog.d(TAG, "attr: " + mTopFullscreenOpaqueWindowState.getAttrs()
                    + " lp.flags=0x" + Integer.toHexString(fl));
        }
        return (fl & LayoutParams.FLAG_FULLSCREEN) != 0
                || (mLastSystemUiFlags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
    }

    /**
     * Updates the occluded state of the Keyguard.
     *
     * @return Whether the flags have changed and we have to redo the layout.
     */
    private boolean setKeyguardOccludedLw(boolean isOccluded, boolean force) {
        if (DEBUG_KEYGUARD) Slog.d(TAG, "setKeyguardOccluded occluded=" + isOccluded);
        final boolean wasOccluded = mKeyguardOccluded;
        final boolean showing = mKeyguardDelegate.isShowing();
        final boolean changed = wasOccluded != isOccluded || force;
        if (!isOccluded && changed && showing) {
            mKeyguardOccluded = false;
            mKeyguardDelegate.setOccluded(false, true /* animate */);
            if (mStatusBar != null) {
                mStatusBar.getAttrs().privateFlags |= PRIVATE_FLAG_KEYGUARD;
                if (!mKeyguardDelegate.hasLockscreenWallpaper()) {
                    mStatusBar.getAttrs().flags |= FLAG_SHOW_WALLPAPER;
                }
            }
            return true;
        } else if (isOccluded && changed && showing) {
            mKeyguardOccluded = true;
            mKeyguardDelegate.setOccluded(true, false /* animate */);
            if (mStatusBar != null) {
                mStatusBar.getAttrs().privateFlags &= ~PRIVATE_FLAG_KEYGUARD;
                mStatusBar.getAttrs().flags &= ~FLAG_SHOW_WALLPAPER;
            }
            return true;
        } else if (changed) {
            mKeyguardOccluded = isOccluded;
            mKeyguardDelegate.setOccluded(isOccluded, false /* animate */);
            return false;
        } else {
            return false;
        }
    }

    private boolean isStatusBarKeyguard() {
        return mStatusBar != null
                && (mStatusBar.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0;
    }

    @Override
    public boolean allowAppAnimationsLw() {
        if (mShowingDream) {
            // If keyguard or dreams is currently visible, no reason to animate behind it.
            return false;
        }
        return true;
    }

    @Override
    public int focusChangedLw(WindowState lastFocus, WindowState newFocus) {
        mFocusedWindow = newFocus;
        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            return FINISH_LAYOUT_REDO_LAYOUT;
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
        // lid changed state
        final int newLidState = lidOpen ? LID_OPEN : LID_CLOSED;
        if (newLidState == mLidState) {
            return;
        }

        mLidState = newLidState;
        applyLidSwitchState();
        updateRotation(true);

        if (lidOpen) {
            wakeUp(SystemClock.uptimeMillis(), mAllowTheaterModeWakeFromLidSwitch,
                    "android.policy:LID");
        } else if (!mLidControlsSleep) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    @Override
    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {
        int lensCoverState = lensCovered ? CAMERA_LENS_COVERED : CAMERA_LENS_UNCOVERED;
        if (mCameraLensCoverState == lensCoverState) {
            return;
        }
        if (mCameraLensCoverState == CAMERA_LENS_COVERED &&
                lensCoverState == CAMERA_LENS_UNCOVERED) {
            Intent intent;
            final boolean keyguardActive = mKeyguardDelegate == null ? false :
                    mKeyguardDelegate.isShowing();
            if (keyguardActive) {
                intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
            } else {
                intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            }
            wakeUp(whenNanos / 1000000, mAllowTheaterModeWakeFromCameraLens,
                    "android.policy:CAMERA_COVER");
            startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);
        }
        mCameraLensCoverState = lensCoverState;
    }

    void setHdmiPlugged(boolean plugged) {
        if (mHdmiPlugged != plugged) {
            mHdmiPlugged = plugged;
            updateRotation(true, true);
            Intent intent = new Intent(ACTION_HDMI_PLUGGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(EXTRA_HDMI_PLUGGED_STATE, plugged);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    void initializeHdmiState() {
        boolean plugged = false;
        // watch for HDMI plug messages if the hdmi switch exists
        if (new File("/sys/devices/virtual/switch/hdmi/state").exists()) {
            mHDMIObserver.startObserving("DEVPATH=/devices/virtual/switch/hdmi");

            final String filename = "/sys/class/switch/hdmi/state";
            FileReader reader = null;
            try {
                reader = new FileReader(filename);
                char[] buf = new char[15];
                int n = reader.read(buf);
                if (n > 1) {
                    plugged = 0 != Integer.parseInt(new String(buf, 0, n-1));
                }
            } catch (IOException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } catch (NumberFormatException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }
        // This dance forces the code in setHdmiPlugged to run.
        // Always do this so the sticky intent is stuck (to false) if there is no hdmi.
        mHdmiPlugged = !plugged;
        setHdmiPlugged(!mHdmiPlugged);
    }

    final Object mScreenshotLock = new Object();
    final Object mScreenrecordLock = new Object();
    ServiceConnection mScreenshotConnection = null;
    ServiceConnection mScreenrecordConnection = null;

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                    notifyScreenshotError();
                }
            }
        }
    };

    final Runnable mScreenrecordTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenrecordLock) {
                if (mScreenrecordConnection != null) {
                    mContext.unbindService(mScreenrecordConnection);
                    mScreenrecordConnection = null;
                }
            }
        }
    };

    // Assume this is called from the Handler thread.
    private void takeScreenshot(final int screenshotType) {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null || mPocketLockShowing) {
                return;
            }
            final ComponentName serviceComponent = new ComponentName(SYSUI_PACKAGE,
                    SYSUI_SCREENSHOT_SERVICE);
            final Intent serviceIntent = new Intent();
            serviceIntent.setComponent(serviceComponent);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, screenshotType);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHandler.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        // Needs delay or else we'll be taking a screenshot of the dialog each time
                        try {
                            Thread.sleep(mScreenshotDelay);
                        } catch (InterruptedException ie) {
                            // Do nothing
                        }

                        if (mStatusBar != null && mStatusBar.isVisibleLw())
                            msg.arg1 = 1;
                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
                            msg.arg2 = 1;
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != null) {
                            mContext.unbindService(mScreenshotConnection);
                            mScreenshotConnection = null;
                            mHandler.removeCallbacks(mScreenshotTimeout);
                            notifyScreenshotError();
                        }
                    }
                }
            };
            if (mContext.bindServiceAsUser(serviceIntent, conn,
                    Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                    UserHandle.CURRENT)) {
                mScreenshotConnection = conn;
                if (screenshotType != WindowManager.TAKE_SCREENSHOT_SELECTED_REGION) {
                    mHandler.postDelayed(mScreenshotTimeout, 10000);
                }
            }
        }
    }

    /**
     * Notifies the screenshot service to show an error.
     */
    private void notifyScreenshotError() {
        // If the service process is killed, then ask it to clean up after itself
        final ComponentName errorComponent = new ComponentName(SYSUI_PACKAGE,
                SYSUI_SCREENSHOT_ERROR_RECEIVER);
        Intent errorIntent = new Intent(Intent.ACTION_USER_PRESENT);
        errorIntent.setComponent(errorComponent);
        errorIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT |
                Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcastAsUser(errorIntent, UserHandle.CURRENT);
    }

    private void setVolumeWakeTriggered(final int keyCode, boolean triggered) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mVolumeDownWakeTriggered = triggered;
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                mVolumeUpWakeTriggered = triggered;
                break;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                mVolumeMuteWakeTriggered = triggered;
                break;
            default:
                Log.w(TAG, "setVolumeWakeTriggered: unexpected keyCode=" + keyCode);
        }
    }

    private boolean getVolumeWakeTriggered(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return mVolumeDownWakeTriggered;
            case KeyEvent.KEYCODE_VOLUME_UP:
                return mVolumeUpWakeTriggered;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return mVolumeMuteWakeTriggered;
            default:
                Log.w(TAG, "getVolumeWakeTriggered: unexpected keyCode=" + keyCode);
                return false;
        }
    }

<<<<<<< HEAD
    private boolean isHwKeysDisabled() {
        return mKeyHandler != null ? mKeyHandler.isHwKeysDisabled() : false;
    }

    // Assume this is called from the Handler thread.
    private void takeScreenrecord(final int mode) {
        synchronized (mScreenrecordLock) {
            if (mScreenrecordConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.omni.screenrecord.TakeScreenrecordService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenrecordLock) {
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, mode);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenrecordLock) {
                                    if (mScreenrecordConnection == myConn) {
                                        mContext.unbindService(mScreenrecordConnection);
                                        mScreenrecordConnection = null;
                                        mHandler.removeCallbacks(mScreenrecordTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized (mScreenrecordLock) {
                        if (mScreenrecordConnection != null) {
                            mContext.unbindService(mScreenrecordConnection);
                            mScreenrecordConnection = null;
                            mHandler.removeCallbacks(mScreenrecordTimeout);
                        }
                    }
                }
            };
            if (mContext.bindServiceAsUser(
                    intent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                mScreenrecordConnection = conn;
                // Screenrecord max duration is 30 minutes. Allow 32 minutes before killing
                // the service.
                mHandler.postDelayed(mScreenrecordTimeout, 32 * 60 * 1000);
            }
        }
    }

=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    /** {@inheritDoc} */
    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        if (!mSystemBooted) {
            // If we have not yet booted, don't let key events do anything.
            return 0;
        }

        final boolean interactive = (policyFlags & FLAG_INTERACTIVE) != 0;
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final int keyCode = event.getKeyCode();

        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        final boolean appSwitchKey = keyCode == KeyEvent.KEYCODE_APP_SWITCH;
        final boolean homeKey = keyCode == KeyEvent.KEYCODE_HOME;
        final boolean menuKey = keyCode == KeyEvent.KEYCODE_MENU;
        final boolean backKey = keyCode == KeyEvent.KEYCODE_BACK;
//        final boolean navBarKey = event.getSource() == InputDevice.SOURCE_NAVIGATION_BAR;

        // If screen is off then we treat the case where the keyguard is open but hidden
        // the same as if it were open and in front.
        // This will prevent any keys other than the power button from waking the screen
        // when the keyguard is hidden by another activity.
        final boolean keyguardActive = (mKeyguardDelegate == null ? false :
                                            (interactive ?
                                                isKeyguardShowingAndNotOccluded() :
                                                mKeyguardDelegate.isShowing()));

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                    + " interactive=" + interactive + " keyguardActive=" + keyguardActive
                    + " policyFlags=" + Integer.toHexString(policyFlags));
		}

        // Pre-basic policy based on interactive and pocket lock state.
        if (mIsDeviceInPocket && (!interactive || mPocketLockShowing)) {
            if (keyCode != KeyEvent.KEYCODE_POWER &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_MEDIA_PLAY &&
                keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE &&
                keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                keyCode != KeyEvent.KEYCODE_HEADSETHOOK &&
                keyCode != KeyEvent.KEYCODE_MEDIA_STOP &&
                keyCode != KeyEvent.KEYCODE_MEDIA_NEXT &&
                keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) {
                return 0;
            }
        }

        if (mANBIHandler != null && mANBIEnabled && mANBIHandler.isScreenTouched()
/*                && !navBarKey */
                && (appSwitchKey || homeKey || menuKey || backKey)) {
            return 0;
        }

        // Basic policy based on interactive state.
        int result;
        boolean isWakeKey = (policyFlags & WindowManagerPolicy.FLAG_WAKE) != 0
                || event.isWakeKey();
        if (interactive || (isInjected && !isWakeKey)) {
            // When the device is interactive or the key is injected pass the
            // key to the application.
            result = ACTION_PASS_TO_USER;
            isWakeKey = false;

            if (interactive) {
                // If the screen is awake, but the button pressed was the one that woke the device
                // then don't pass it to the application
                if (keyCode == mPendingWakeKey && !down) {
                    result = 0;
                }
                // Reset the pending key
                mPendingWakeKey = PENDING_KEY_NULL;
            }
        } else if (!interactive && shouldDispatchInputWhenNonInteractive(event)) {
            // If we're currently dozing with the screen on and the keyguard showing, pass the key
            // to the application but preserve its wake key status to make sure we still move
            // from dozing to fully interactive if we would normally go from off to fully
            // interactive, unless the user has explicitly disabled this wake key.
            result = ACTION_PASS_TO_USER;
            isWakeKey = isWakeKey && isWakeKeyEnabled(keyCode);
            // Since we're dispatching the input, reset the pending key
            mPendingWakeKey = PENDING_KEY_NULL;
        } else {
            // When the screen is off and the key is not injected, determine whether
            // to wake the device but don't pass the key to the application.
            result = 0;
            if (isWakeKey && (!down || !isWakeKeyWhenScreenOff(keyCode))) {
                isWakeKey = false;
            }
            // Cache the wake key on down event so we can also avoid sending the up event to the app
            if (isWakeKey && down) {
                mPendingWakeKey = keyCode;
            }
        }

        // If the key would be handled globally, just return the result, don't worry about special
        // key processing.
        if (isValidGlobalKey(keyCode)
                && mGlobalKeyManager.shouldHandleGlobalKey(keyCode, event)) {
            if (isWakeKey) {
                wakeUp(event.getEventTime(), mAllowTheaterModeWakeFromKey,
                       "android.policy:KEY", true);
            }
            return result;
        }

        boolean useHapticFeedback = down
                && (policyFlags & WindowManagerPolicy.FLAG_VIRTUAL) != 0
                && event.getRepeatCount() == 0
                && !isHwKeysDisabled();

        // Specific device key handling
        if (dispatchKeyToKeyHandlers(event)) {
            return 0;
        }

        // Specific device key handling
        if (dispatchKeyToKeyHandlers(event)) {
            return 0;
        }

        // Handle special keys.
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (down) {
                    interceptBackKeyDown();
                } else {
                    boolean handled = interceptBackKeyUp(event);

                    // Don't pass back press to app if we've already handled it via long press
                    if (handled) {
                        result &= ~ACTION_PASS_TO_USER;
                    }
                }
                break;
            }

            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE: {
                // Eat all down & up keys when using volume wake.
                // This disables volume control, music control, and "beep" on key up.
                if (isWakeKey && mVolumeWakeScreen) {
                    setVolumeWakeTriggered(keyCode, true);
                    break;
                } else if (getVolumeWakeTriggered(keyCode) && !down) {
                    result &= ~ACTION_PASS_TO_USER;
                    setVolumeWakeTriggered(keyCode, false);
                    break;
                }

                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (down) {
                        if (interactive && !mScreenshotChordVolumeDownKeyTriggered
                                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                            mScreenshotChordVolumeDownKeyTriggered = true;
                            mScreenshotChordVolumeDownKeyTime = event.getDownTime();
                            mScreenshotChordVolumeDownKeyConsumed = false;
                            cancelPendingPowerKeyAction();
                            cancelPendingScreenrecordChordAction();
                            checkSettings();
                            interceptScreenshotChord();
                            interceptAccessibilityShortcutChord();
                        }
                    } else {
                        mScreenshotChordVolumeDownKeyTriggered = false;
                        cancelPendingScreenshotChordAction();
                        cancelPendingAccessibilityShortcutAction();
                        cancelPendingScreenrecordChordAction();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (down) {
                        if (interactive && (!mA11yShortcutChordVolumeUpKeyTriggered
                                || !mScreenrecordChordVolumeUpKeyTriggered)
                                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                            mA11yShortcutChordVolumeUpKeyTriggered = true;
                            mA11yShortcutChordVolumeUpKeyTime = event.getDownTime();
                            mA11yShortcutChordVolumeUpKeyConsumed = false;
                            mScreenrecordChordVolumeUpKeyTriggered = true;
                            mScreenrecordChordVolumeUpKeyTime = event.getDownTime();
                            mScreenrecordChordVolumeUpKeyConsumed = false;
                            cancelPendingPowerKeyAction();
                            interceptScreenshotChord();
                            cancelPendingScreenshotChordAction();
                            interceptAccessibilityShortcutChord();
                        }
                    } else {
                        mA11yShortcutChordVolumeUpKeyTriggered = false;
                        mScreenrecordChordVolumeUpKeyTriggered = false;
                        cancelPendingScreenshotChordAction();
                        cancelPendingAccessibilityShortcutAction();
                        cancelPendingScreenrecordChordAction();
                    }
                }
                if (down) {
                    sendSystemKeyToStatusBarAsync(event.getKeyCode());

                    TelecomManager telecomManager = getTelecommService();
                    if (telecomManager != null) {
                        if (telecomManager.isRinging()) {
                            if (mVolumeAnswerCall) {
                                telecomManager.acceptRingingCall();
                            }

                            // If an incoming call is ringing, either VOLUME key means
                            // "silence ringer".  We handle these keys here, rather than
                            // in the InCallScreen, to make sure we'll respond to them
                            // even if the InCallScreen hasn't come to the foreground yet.
                            // Look for the DOWN event here, to agree with the "fallback"
                            // behavior in the InCallScreen.
                            Log.i(TAG, "interceptKeyBeforeQueueing:"
                                  + " VOLUME key-down while ringing: Silence ringer!");

                            // Silence the ringer.  (It's safe to call this
                            // even if the ringer has already been silenced.)
                            telecomManager.silenceRinger();

                            // And *don't* pass this key thru to the current activity
                            // (which is probably the InCallScreen.)
                            result &= ~ACTION_PASS_TO_USER;
                            break;
                        }
                    }
                    int audioMode = AudioManager.MODE_NORMAL;
                    try {
                        audioMode = getAudioService().getMode();
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting AudioService in interceptKeyBeforeQueueing.", e);
                    }
                    boolean isInCall = (telecomManager != null && telecomManager.isInCall()) ||
                            audioMode == AudioManager.MODE_IN_COMMUNICATION;
                    if (isInCall && (result & ACTION_PASS_TO_USER) == 0) {
                        // If we are in call but we decided not to pass the key to
                        // the application, just pass it to the session service.
                        MediaSessionLegacyHelper.getHelper(mContext).sendVolumeKeyEvent(
                                event, AudioManager.USE_DEFAULT_STREAM_TYPE, false);
                        break;
                    }
                }
                if (mUseTvRouting || mHandleVolumeKeysInWM) {
                    // Defer special key handlings to
                    // {@link interceptKeyBeforeDispatching()}.
                    result |= ACTION_PASS_TO_USER;
                } else if ((result & ACTION_PASS_TO_USER) == 0 && !mVolumeWakeScreen) {
                    if (mLineageButtons.handleVolumeKey(event, interactive)) {
                        break;
                    }

                    // If we aren't passing to the user and no one else
                    // handled it send it to the session manager to
                    // figure out.
                    MediaSessionLegacyHelper.getHelper(mContext).sendVolumeKeyEvent(
                            event, AudioManager.USE_DEFAULT_STREAM_TYPE, true);
                }
                break;
            }

            case KeyEvent.KEYCODE_HOME:
                if (down && !interactive && mHomeWakeScreen) {
                    isWakeKey = true;
                }
                break;

            case KeyEvent.KEYCODE_FOCUS:
                if (down && !interactive && mCameraSleepOnRelease) {
                    mIsFocusPressed = true;
                } else if ((event.getAction() == KeyEvent.ACTION_UP)
                        && mScreenOnFully && mIsFocusPressed) {
                    // Check if screen is fully on before letting the device go to sleep
                    mPowerManager.goToSleep(SystemClock.uptimeMillis());
                    mIsFocusPressed = false;
                }
                break;

            case KeyEvent.KEYCODE_CAMERA:
                if (down && mIsFocusPressed) {
                    mIsFocusPressed = false;
                }
                if (down) {
                    mIsLongPress = false;

                    KeyEvent newEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                            event.getAction(), keyCode, 0);
                    Message msg = mHandler.obtainMessage(MSG_CAMERA_LONG_PRESS, newEvent);
                    msg.setAsynchronous(true);
                    mHandler.sendMessageDelayed(msg, ViewConfiguration.getLongPressTimeout());
                    // Consume key down events of all presses.
                    break;
                } else {
                    mHandler.removeMessages(MSG_CAMERA_LONG_PRESS);
                    // Consume key up events of long presses only.
                    if (mIsLongPress && mCameraLaunch) {
                        Intent intent;
                        if (keyguardActive) {
                            intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                        } else {
                            intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                        }
                        isWakeKey = true;
                        startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);
                    }
                }
                break;

            case KeyEvent.KEYCODE_ENDCALL: {
                result &= ~ACTION_PASS_TO_USER;
                if (down) {
                    TelecomManager telecomManager = getTelecommService();
                    boolean hungUp = false;
                    if (telecomManager != null) {
                        hungUp = telecomManager.endCall();
                    }
                    if (interactive && !hungUp) {
                        mEndCallKeyHandled = false;
                        mHandler.postDelayed(mEndCallLongPress,
                                ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                    } else {
                        mEndCallKeyHandled = true;
                    }
                } else {
                    if (!mEndCallKeyHandled) {
                        mHandler.removeCallbacks(mEndCallLongPress);
                        if (!canceled) {
                            if ((mEndcallBehavior
                                    & Settings.System.END_BUTTON_BEHAVIOR_HOME) != 0) {
                                if (goHome()) {
                                    break;
                                }
                            }
                            if ((mEndcallBehavior
                                    & Settings.System.END_BUTTON_BEHAVIOR_SLEEP) != 0) {
                                goToSleep(event.getEventTime(),
                                        PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                                isWakeKey = false;
                            }
                        }
                    }
                }
                break;
            }

            case KeyEvent.KEYCODE_POWER: {
                if (mTopFullscreenOpaqueWindowState != null
                        && (mTopFullscreenOpaqueWindowState.getAttrs().privateFlags
                                & WindowManager.LayoutParams.PRIVATE_FLAG_PREVENT_POWER_KEY) != 0
                        && mScreenOnFully) {
                    return result;
                }
                // Any activity on the power button stops the accessibility shortcut
                cancelPendingAccessibilityShortcutAction();
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false; // wake-up will be handled separately
                if (down) {
                    interceptPowerKeyDown(event, interactive);
                } else {
                    interceptPowerKeyUp(event, interactive, canceled);
                }
                break;
            }

            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN:
                // fall through
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP:
                // fall through
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT:
                // fall through
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT: {
                result &= ~ACTION_PASS_TO_USER;
                interceptSystemNavigationKey(event);
                break;
            }

            case KeyEvent.KEYCODE_SLEEP: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false;
                if (!mPowerManager.isInteractive()) {
                    useHapticFeedback = false; // suppress feedback if already non-interactive
                }
                if (down) {
                    sleepPress(event.getEventTime());
                } else {
                    sleepRelease(event.getEventTime());
                }
                break;
            }

            case KeyEvent.KEYCODE_SOFT_SLEEP: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false;
                if (!down) {
                    mPowerManagerInternal.setUserInactiveOverrideFromWindowManager();
                }
                break;
            }

            case KeyEvent.KEYCODE_WAKEUP: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = true;
                break;
            }

            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK: {
                if (MediaSessionLegacyHelper.getHelper(mContext).isGlobalPriorityActive()) {
                    // If the global session is active pass all media keys to it
                    // instead of the active window.
                    result &= ~ACTION_PASS_TO_USER;
                }
                if ((result & ACTION_PASS_TO_USER) == 0) {
                    // Only do this if we would otherwise not pass it to the user. In that
                    // case, the PhoneWindow class will do the same thing, except it will
                    // only do it if the showing app doesn't process the key on its own.
                    // Note that we need to make a copy of the key event here because the
                    // original key event will be recycled when we return.
                    mBroadcastWakeLock.acquire();
                    Message msg = mHandler.obtainMessage(MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK,
                            new KeyEvent(event));
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
                }
                break;
            }

            case KeyEvent.KEYCODE_CALL: {
                if (down) {
                    TelecomManager telecomManager = getTelecommService();
                    if (telecomManager != null) {
                        if (telecomManager.isRinging()) {
                            Log.i(TAG, "interceptKeyBeforeQueueing:"
                                  + " CALL key-down while ringing: Answer the call!");
                            telecomManager.acceptRingingCall();

                            // And *don't* pass this key thru to the current activity
                            // (which is presumably the InCallScreen.)
                            result &= ~ACTION_PASS_TO_USER;
                        }
                    }
                }
                break;
            }
            case KeyEvent.KEYCODE_VOICE_ASSIST: {
                // Only do this if we would otherwise not pass it to the user. In that case,
                // interceptKeyBeforeDispatching would apply a similar but different policy in
                // order to invoke voice assist actions. Note that we need to make a copy of the
                // key event here because the original key event will be recycled when we return.
                if ((result & ACTION_PASS_TO_USER) == 0 && !down) {
                    mBroadcastWakeLock.acquire();
                    Message msg = mHandler.obtainMessage(MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK,
                            keyguardActive ? 1 : 0, 0);
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
                }
                break;
            }
            case KeyEvent.KEYCODE_WINDOW: {
                if (mShortPressWindowBehavior == SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE) {
                    if (mPictureInPictureVisible) {
                        // Consumes the key only if picture-in-picture is visible to show
                        // picture-in-picture control menu. This gives a chance to the foreground
                        // activity to customize PIP key behavior.
                        if (!down) {
                            showPictureInPictureMenu(event);
                        }
                        result &= ~ACTION_PASS_TO_USER;
                    }
                }
                break;
            }
        }

        if (useHapticFeedback) {
            performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
        }

        if (isWakeKey) {
            wakeUp(event.getEventTime(), mAllowTheaterModeWakeFromKey, "android.policy:KEY",
                    event.getKeyCode() == KeyEvent.KEYCODE_WAKEUP); // Check prox only on wake key
        }

        return result;
    }

    /**
     * Handle statusbar expansion events.
     * @param event
     */
    private void interceptSystemNavigationKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (!mAccessibilityManager.isEnabled()
                    || !mAccessibilityManager.sendFingerprintGesture(event.getKeyCode())) {
                if (mSystemNavigationKeysEnabled) {
                    sendSystemKeyToStatusBarAsync(event.getKeyCode());
                }
            }
        }
    }

    /**
     * Notify the StatusBar that a system key was pressed.
     */
    private void sendSystemKeyToStatusBar(int keyCode) {
        IStatusBarService statusBar = getStatusBarService();
        if (statusBar != null) {
            try {
                statusBar.handleSystemKey(keyCode);
            } catch (RemoteException e) {
                // Oh well.
            }
        }
    }

    /**
     * Notify the StatusBar that a system key was pressed without blocking the current thread.
     */
    private void sendSystemKeyToStatusBarAsync(int keyCode) {
        Message message = mHandler.obtainMessage(MSG_SYSTEM_KEY_PRESS, keyCode, 0);
        message.setAsynchronous(true);
        mHandler.sendMessage(message);
    }

    /**
     * Returns true if the key can have global actions attached to it.
     * We reserve all power management keys for the system since they require
     * very careful handling.
     */
    private static boolean isValidGlobalKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_SLEEP:
                return false;
            default:
                return true;
        }
    }

    /**
     * Check if the given keyCode represents a key that is considered a wake key
     * and is currently enabled by the user in Settings or for another reason.
     */
    private boolean isWakeKeyEnabled(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                // Volume keys are still wake keys if the device is docked.
                return mVolumeWakeScreen || mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED;
            case KeyEvent.KEYCODE_BACK:
                return mBackWakeScreen;
            case KeyEvent.KEYCODE_MENU:
                return mMenuWakeScreen;
            case KeyEvent.KEYCODE_ASSIST:
                return mAssistWakeScreen;
            case KeyEvent.KEYCODE_APP_SWITCH:
                return mAppSwitchWakeScreen;
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                return mCameraWakeScreen;
        }
        return true;
    }

    /**
     * When the screen is off we ignore some keys that might otherwise typically
     * be considered wake keys.  We filter them out here.
     *
     * {@link KeyEvent#KEYCODE_POWER} is notably absent from this list because it
     * is always considered a wake key.
     */
    private boolean isWakeKeyWhenScreenOff(int keyCode) {
        switch (keyCode) {
            // ignore volume keys unless docked
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return mVolumeWakeScreen || mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED;

            // ignore media keys
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                return false;

            case KeyEvent.KEYCODE_BACK:
                return mBackWakeScreen;
            case KeyEvent.KEYCODE_MENU:
                return mMenuWakeScreen;
            case KeyEvent.KEYCODE_ASSIST:
                return mAssistWakeScreen;
            case KeyEvent.KEYCODE_APP_SWITCH:
                return mAppSwitchWakeScreen;
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                return mCameraWakeScreen;
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public int interceptMotionBeforeQueueingNonInteractive(long whenNanos, int policyFlags) {
        if ((policyFlags & FLAG_WAKE) != 0) {
            if (wakeUp(whenNanos / 1000000, mAllowTheaterModeWakeFromMotion,
                    "android.policy:MOTION")) {
                return 0;
            }
        }

        if (shouldDispatchInputWhenNonInteractive(null)) {
            return ACTION_PASS_TO_USER;
        }

        // If we have not passed the action up and we are in theater mode without dreaming,
        // there will be no dream to intercept the touch and wake into ambient.  The device should
        // wake up in this case.
        if (isTheaterModeEnabled() && (policyFlags & FLAG_WAKE) != 0) {
            wakeUp(whenNanos / 1000000, mAllowTheaterModeWakeFromMotionWhenNotDreaming,
                    "android.policy:MOTION");
        }

        return 0;
    }

    private boolean shouldDispatchInputWhenNonInteractive(KeyEvent event) {
        final boolean displayOff = (mDisplay == null || mDisplay.getState() == STATE_OFF);

        if (displayOff && !mHasFeatureWatch) {
            return false;
        }

        IDreamManager dreamManager = getDreamManager();
        try {
            if (dreamManager != null && dreamManager.isDozing()) {
                if (event != null && isVolumeKey(event)) {
                    return false;
                }
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException when checking if dreaming", e);
        }

        // Send events to keyguard while the screen is on and it's showing.
        if (isKeyguardShowingAndNotOccluded() && !displayOff) {
            return true;
        }

        // Watches handle BACK specially
        if (mHasFeatureWatch
                && event != null
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                        || event.getKeyCode() == KeyEvent.KEYCODE_STEM_PRIMARY)) {
            return false;
        }

        // Send events to a dozing dream even if the screen is off since the dream
        // is in control of the state of the screen.
        try {
            if (dreamManager != null && dreamManager.isDreaming()) {
                return true;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException when checking if dreaming", e);
        }

        // Otherwise, consume events since the user can't see what is being
        // interacted with.
        return false;
    }

    private boolean isVolumeKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP;
    }

    private void dispatchDirectAudioEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        int keyCode = event.getKeyCode();
        int flags = AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND
                | AudioManager.FLAG_FROM_KEY;
        String pkgName = mContext.getOpPackageName();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                try {
                    getAudioService().adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, flags, pkgName, TAG);
                } catch (Exception e) {
                    Log.e(TAG, "Error dispatching volume up in dispatchTvAudioEvent.", e);
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                try {
                    getAudioService().adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, flags, pkgName, TAG);
                } catch (Exception e) {
                    Log.e(TAG, "Error dispatching volume down in dispatchTvAudioEvent.", e);
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                try {
                    if (event.getRepeatCount() == 0) {
                        getAudioService().adjustSuggestedStreamVolume(
                                AudioManager.ADJUST_TOGGLE_MUTE,
                                AudioManager.USE_DEFAULT_STREAM_TYPE, flags, pkgName, TAG);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error dispatching mute in dispatchTvAudioEvent.", e);
                }
                break;
        }
    }

    void dispatchMediaKeyWithWakeLock(KeyEvent event) {
        if (DEBUG_INPUT) {
            Slog.d(TAG, "dispatchMediaKeyWithWakeLock: " + event);
        }

        if (mHavePendingMediaKeyRepeatWithWakeLock) {
            if (DEBUG_INPUT) {
                Slog.d(TAG, "dispatchMediaKeyWithWakeLock: canceled repeat");
            }

            mHandler.removeMessages(MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK);
            mHavePendingMediaKeyRepeatWithWakeLock = false;
            mBroadcastWakeLock.release(); // pending repeat was holding onto the wake lock
        }

        dispatchMediaKeyWithWakeLockToAudioService(event);

        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            mHavePendingMediaKeyRepeatWithWakeLock = true;

            Message msg = mHandler.obtainMessage(
                    MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK, event);
            msg.setAsynchronous(true);
            mHandler.sendMessageDelayed(msg, ViewConfiguration.getKeyRepeatTimeout());
        } else {
            mBroadcastWakeLock.release();
        }
    }

    void dispatchMediaKeyRepeatWithWakeLock(KeyEvent event) {
        mHavePendingMediaKeyRepeatWithWakeLock = false;

        KeyEvent repeatEvent = KeyEvent.changeTimeRepeat(event,
                SystemClock.uptimeMillis(), 1, event.getFlags() | KeyEvent.FLAG_LONG_PRESS);
        if (DEBUG_INPUT) {
            Slog.d(TAG, "dispatchMediaKeyRepeatWithWakeLock: " + repeatEvent);
        }

        dispatchMediaKeyWithWakeLockToAudioService(repeatEvent);
        mBroadcastWakeLock.release();
    }

    void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        if (mActivityManagerInternal.isSystemReady()) {
            MediaSessionLegacyHelper.getHelper(mContext).sendMediaButtonEvent(event, true);
        }
    }

    void launchVoiceAssistWithWakeLock(boolean keyguardActive) {
        IDeviceIdleController dic = IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
        if (dic != null) {
            try {
                dic.exitIdle("voice-search");
            } catch (RemoteException e) {
            }
        }
        Intent voiceIntent =
            new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        voiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, keyguardActive);
        startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF);
        mBroadcastWakeLock.release();
    }

    BroadcastReceiver mDockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DOCK_EVENT.equals(intent.getAction())) {
                mDockMode = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
            } else {
                try {
                    IUiModeManager uiModeService = IUiModeManager.Stub.asInterface(
                            ServiceManager.getService(Context.UI_MODE_SERVICE));
                    mUiMode = uiModeService.getCurrentModeType();
                } catch (RemoteException e) {
                }
            }
            updateRotation(true);
            synchronized (mLock) {
                updateOrientationListenerLp();
            }
        }
    };

    BroadcastReceiver mDreamReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DREAMING_STARTED.equals(intent.getAction())) {
                if (mKeyguardDelegate != null) {
                    mKeyguardDelegate.onDreamingStarted();
                }
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(intent.getAction())) {
                if (mKeyguardDelegate != null) {
                    mKeyguardDelegate.onDreamingStopped();
                }
            }
        }
    };

    BroadcastReceiver mMultiuserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_SWITCHED.equals(intent.getAction())) {
                // tickle the settings observer: this first ensures that we're
                // observing the relevant settings for the newly-active user,
                // and then updates our own bookkeeping based on the now-
                // current user.
                mSettingsObserver.onChange(false);

                // force a re-application of focused window sysui visibility.
                // the window may never have been shown for this user
                // e.g. the keyguard when going through the new-user setup flow
                synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                    mLastSystemUiFlags = 0;
                    updateSystemUiVisibilityLw();
                }
            }
        }
    };

    BroadcastReceiver mPowerMenuReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREENSHOT.equals(intent.getAction())) {
                mHandler.removeCallbacks(mScreenshotRunnable);
                mHandler.post(mScreenshotRunnable);
            }
        }
    };

    private final Runnable mHiddenNavPanic = new Runnable() {
        @Override
        public void run() {
            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                if (!isUserSetupComplete()) {
                    // Swipe-up for navigation bar is disabled during setup
                    return;
                }
                mPendingPanicGestureUptime = SystemClock.uptimeMillis();
                if (!isNavBarEmpty(mLastSystemUiFlags)) {
                    mNavigationBarController.showTransient();
                }
            }
        }
    };

    private void requestTransientBars(WindowState swipeTarget) {
        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
            if (!isUserSetupComplete()) {
                // Swipe-up for navigation bar is disabled during setup
                return;
            }
            boolean sb = mStatusBarController.checkShowTransientBarLw();
            boolean nb = mNavigationBarController.checkShowTransientBarLw()
                    && !isNavBarEmpty(mLastSystemUiFlags);
            if (sb || nb) {
                // Don't show status bar when swiping on already visible navigation bar
                if (!nb && swipeTarget == mNavigationBar) {
                    if (DEBUG) Slog.d(TAG, "Not showing transient bar, wrong swipe target");
                    return;
                }
                if (sb) mStatusBarController.showTransient();
                if (nb && !immersiveModeImplementsPie()) {
                    mNavigationBarController.showTransient();
                }
                mImmersiveModeConfirmation.confirmCurrentPrompt();
                updateSystemUiVisibilityLw();
            }
        }
    }

    private void toggleOrientationListener(boolean enable) {
        IStatusBarService statusbar = getStatusBarService();
        if (statusbar != null) {
           try {
               statusbar.toggleOrientationListener(enable);
           } catch (RemoteException e) {
               Slog.e(TAG, "RemoteException when controlling orientation sensor", e);
               // re-acquire status bar service next time it is needed.
               mStatusBarService = null;
           }
        }
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void startedGoingToSleep(int why) {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Started going to sleep... (why=" + why + ")");

        mGoingToSleep = true;
        mRequestedOrGoingToSleep = true;

        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onStartedGoingToSleep(why);
        }
        if (mPocketManager != null) {
            mPocketManager.onInteractiveChanged(false);
        }
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void finishedGoingToSleep(int why) {
        EventLog.writeEvent(70000, 0);
        if (DEBUG_WAKEUP) Slog.i(TAG, "Finished going to sleep... (why=" + why + ")");
        MetricsLogger.histogram(mContext, "screen_timeout", mLockScreenTimeout / 1000);

        mGoingToSleep = false;
        mRequestedOrGoingToSleep = false;

        // We must get this work done here because the power manager will drop
        // the wake lock and let the system suspend once this function returns.
        synchronized (mLock) {
            mAwake = false;
            updateWakeGestureListenerLp();
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onFinishedGoingToSleep(why,
                    mCameraGestureTriggeredDuringGoingToSleep);
        }
        mCameraGestureTriggeredDuringGoingToSleep = false;
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void startedWakingUp() {
        EventLog.writeEvent(70000, 1);
        if (DEBUG_WAKEUP) Slog.i(TAG, "Started waking up...");

        // Since goToSleep performs these functions synchronously, we must
        // do the same here.  We cannot post this work to a handler because
        // that might cause it to become reordered with respect to what
        // may happen in a future call to goToSleep.
        synchronized (mLock) {
            mAwake = true;

            updateWakeGestureListenerLp();
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }

        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onStartedWakingUp();
        }

        if (mPocketManager != null) {
            mPocketManager.onInteractiveChanged(true);
        }
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void finishedWakingUp() {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Finished waking up...");

        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onFinishedWakingUp();
        }
    }

    private void wakeUpFromPowerKey(long eventTime) {
        wakeUp(eventTime, mAllowTheaterModeWakeFromPowerKey, "android.policy:POWER");
    }

    private boolean wakeUp(long wakeTime, boolean wakeInTheaterMode, String reason) {
        return wakeUp(wakeTime, wakeInTheaterMode, reason, false);
    }

    private boolean wakeUp(long wakeTime, boolean wakeInTheaterMode, String reason,
            final boolean withProximityCheck) {
        final boolean theaterModeEnabled = isTheaterModeEnabled();
        if (!wakeInTheaterMode && theaterModeEnabled) {
            return false;
        }

        if (theaterModeEnabled) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.THEATER_MODE_ON, 0);
        }

        if (withProximityCheck) {
            mPowerManager.wakeUpWithProximityCheck(wakeTime, reason);
        } else {
            mPowerManager.wakeUp(wakeTime, reason);
        }
        return true;
    }

    private void finishKeyguardDrawn() {
        synchronized (mLock) {
            if (!mScreenOnEarly || mKeyguardDrawComplete) {
                return; // We are not awake yet or we have already informed of this event.
            }

            mKeyguardDrawComplete = true;
            if (mKeyguardDelegate != null) {
                mHandler.removeMessages(MSG_KEYGUARD_DRAWN_TIMEOUT);
            }
            mWindowManagerDrawComplete = false;
        }

        // ... eventually calls finishWindowsDrawn which will finalize our screen turn on
        // as well as enabling the orientation change logic/sensor.
        mWindowManagerInternal.waitForAllWindowsDrawn(mWindowManagerDrawCallback,
                WAITING_FOR_DRAWN_TIMEOUT);
    }

    // Called on the DisplayManager's DisplayPowerController thread.
    @Override
    public void screenTurnedOff() {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Screen turned off...");

        updateScreenOffSleepToken(true);
        synchronized (mLock) {
            mScreenOnEarly = false;
            mScreenOnFully = false;
            mKeyguardDrawComplete = false;
            mWindowManagerDrawComplete = false;
            mScreenOnListener = null;
            updateOrientationListenerLp();

            if (mKeyguardDelegate != null) {
                mKeyguardDelegate.onScreenTurnedOff();
            }
        }
        reportScreenStateToVrManager(false);
    }

    private long getKeyguardDrawnTimeout() {
        final boolean bootCompleted =
                LocalServices.getService(SystemServiceManager.class).isBootCompleted();
        // Set longer timeout if it has not booted yet to prevent showing empty window.
        return bootCompleted ? 1000 : 5000;
    }

    // Called on the DisplayManager's DisplayPowerController thread.
    @Override
    public void screenTurningOn(final ScreenOnListener screenOnListener) {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Screen turning on...");

        updateScreenOffSleepToken(false);
        synchronized (mLock) {
            mScreenOnEarly = true;
            mScreenOnFully = false;
            mKeyguardDrawComplete = false;
            mWindowManagerDrawComplete = false;
            mScreenOnListener = screenOnListener;

            if (mKeyguardDelegate != null) {
                mHandler.removeMessages(MSG_KEYGUARD_DRAWN_TIMEOUT);
                mHandler.sendEmptyMessageDelayed(MSG_KEYGUARD_DRAWN_TIMEOUT,
                        getKeyguardDrawnTimeout());
                mKeyguardDelegate.onScreenTurningOn(mKeyguardDrawnCallback);
            } else {
                if (DEBUG_WAKEUP) Slog.d(TAG,
                        "null mKeyguardDelegate: setting mKeyguardDrawComplete.");
                finishKeyguardDrawn();
            }
        }
    }

    // Called on the DisplayManager's DisplayPowerController thread.
    @Override
    public void screenTurnedOn() {
        synchronized (mLock) {
            if (mKeyguardDelegate != null) {
                mKeyguardDelegate.onScreenTurnedOn();
            }
        }
        reportScreenStateToVrManager(true);
    }

    @Override
    public void screenTurningOff(ScreenOffListener screenOffListener) {
        mWindowManagerFuncs.screenTurningOff(screenOffListener);
        synchronized (mLock) {
            if (mKeyguardDelegate != null) {
                mKeyguardDelegate.onScreenTurningOff();
            }
        }
    }

    private void reportScreenStateToVrManager(boolean isScreenOn) {
        if (mVrManagerInternal == null) {
            return;
        }
        mVrManagerInternal.onScreenStateChanged(isScreenOn);
    }

    private void finishWindowsDrawn() {
        synchronized (mLock) {
            if (!mScreenOnEarly || mWindowManagerDrawComplete) {
                return; // Screen is not turned on or we did already handle this case earlier.
            }

            mWindowManagerDrawComplete = true;
        }

        finishScreenTurningOn();
    }

    private void finishScreenTurningOn() {
        synchronized (mLock) {
            // We have just finished drawing screen content. Since the orientation listener
            // gets only installed when all windows are drawn, we try to install it again.
            updateOrientationListenerLp();
        }
        final ScreenOnListener listener;
        final boolean enableScreen;
        synchronized (mLock) {
            if (DEBUG_WAKEUP) Slog.d(TAG,
                    "finishScreenTurningOn: mAwake=" + mAwake
                            + ", mScreenOnEarly=" + mScreenOnEarly
                            + ", mScreenOnFully=" + mScreenOnFully
                            + ", mKeyguardDrawComplete=" + mKeyguardDrawComplete
                            + ", mWindowManagerDrawComplete=" + mWindowManagerDrawComplete);

            if (mScreenOnFully || !mScreenOnEarly || !mWindowManagerDrawComplete
                    || (mAwake && !mKeyguardDrawComplete)) {
                return; // spurious or not ready yet
            }

            if (DEBUG_WAKEUP) Slog.i(TAG, "Finished screen turning on...");
            listener = mScreenOnListener;
            mScreenOnListener = null;
            mScreenOnFully = true;

            // Remember the first time we draw the keyguard so we know when we're done with
            // the main part of booting and can enable the screen and hide boot messages.
            if (!mKeyguardDrawnOnce && mAwake) {
                mKeyguardDrawnOnce = true;
                enableScreen = true;
                if (mBootMessageNeedsHiding) {
                    mBootMessageNeedsHiding = false;
                    hideBootMessages();
                }
            } else {
                enableScreen = false;
            }
        }

        if (listener != null) {
            listener.onScreenOn();
        }

        if (enableScreen) {
            try {
                mWindowManager.enableScreenIfNeeded();
            } catch (RemoteException unhandled) {
            }
        }

        toggleOrientationListener(true);
    }

    /**
     * Perform operations if needed on pocket mode state changed.
     * @see com.android.server.pocket.PocketService
     * @see PocketLock
     * @see this.mPocketCallback;
     * @author Carlo Savignano
     */
    private void handleDevicePocketStateChanged() {
        final boolean interactive = mPowerManager.isInteractive();
        if (mIsDeviceInPocket) {
            showPocketLock(interactive);
        } else {
            hidePocketLock(interactive);
        }
    }

    /**
     * Check if we can show pocket lock once requested.
     * @see com.android.server.pocket.PocketService
     * @see PocketLock
     * @see this.mPocketCallback;
     * @author Carlo Savignano
     */
    private void showPocketLock(boolean animate) {
        if (!mSystemReady || !mSystemBooted || !mKeyguardDrawnOnce
                || mPocketLock == null || mPocketLockShowing) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "showPocketLock, animate=" + animate);
        }

        mPocketLock.show(animate);
        mPocketLockShowing = true;
    }

    /**
     * Check if we can hide pocket lock once requested.
     * @see com.android.server.pocket.PocketService
     * @see PocketLock
     * @see this.mPocketCallback;
     * @author Carlo Savignano
     */
    private void hidePocketLock(boolean animate) {
        if (!mSystemReady || !mSystemBooted || !mKeyguardDrawnOnce
                || mPocketLock == null || !mPocketLockShowing) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "hidePocketLock, animate=" + animate);
        }

        mPocketLock.hide(animate);
        mPocketLockShowing = false;
    }

    private void handleHideBootMessage() {
        synchronized (mLock) {
            if (!mKeyguardDrawnOnce) {
                mBootMessageNeedsHiding = true;
                return; // keyguard hasn't drawn the first time yet, not done booting
            }
        }

        if (mBootMsgDialog != null) {
            if (DEBUG_WAKEUP) Slog.d(TAG, "handleHideBootMessage: dismissing");
            mBootMsgDialog.dismiss();
            mBootMsgDialog = null;
        }
    }

    @Override
    public boolean isScreenOn() {
        synchronized (mLock) {
            return mScreenOnEarly;
        }
    }

    @Override
    public boolean okToAnimate() {
        return mAwake && !mGoingToSleep;
    }

    /** {@inheritDoc} */
    @Override
    public void enableKeyguard(boolean enabled) {
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.setKeyguardEnabled(enabled);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.verifyUnlock(callback);
        }
    }

    @Override
    public boolean isKeyguardShowingAndNotOccluded() {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isShowing() && !mKeyguardOccluded;
    }

    @Override
    public boolean isKeyguardTrustedLw() {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isTrusted();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardLocked() {
        return keyguardOn();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardSecure(int userId) {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isSecure(userId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardOccluded() {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardOccluded;
    }

    /** {@inheritDoc} */
    @Override
    public boolean inKeyguardRestrictedKeyInputMode() {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isInputRestricted();
    }

    @Override
    public void dismissKeyguardLw(IKeyguardDismissCallback callback) {
        if (mKeyguardDelegate != null && mKeyguardDelegate.isShowing()) {
            if (DEBUG_KEYGUARD) Slog.d(TAG, "PWM.dismissKeyguardLw");

            // ask the keyguard to prompt the user to authenticate if necessary
            mKeyguardDelegate.dismiss(callback);
        } else if (callback != null) {
            try {
                callback.onDismissError();
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call callback", e);
            }
        }
    }

    @Override
    public boolean isKeyguardDrawnLw() {
        synchronized (mLock) {
            return mKeyguardDrawnOnce;
        }
    }

    @Override
    public boolean isShowingDreamLw() {
        return mShowingDream;
    }

    @Override
    public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        if (mKeyguardDelegate != null) {
            if (DEBUG_KEYGUARD) Slog.d(TAG, "PWM.startKeyguardExitAnimation");
            mKeyguardDelegate.startKeyguardExitAnimation(startTime, fadeoutDuration);
        }
    }

    @Override
    public void getStableInsetsLw(int displayRotation, int displayWidth, int displayHeight,
            Rect outInsets) {
        outInsets.setEmpty();

        // Navigation bar and status bar.
        getNonDecorInsetsLw(displayRotation, displayWidth, displayHeight, outInsets);
        outInsets.top = mStatusBarHeight;
    }

    @Override
    public void getNonDecorInsetsLw(int displayRotation, int displayWidth, int displayHeight,
            Rect outInsets) {
        outInsets.setEmpty();

        // Only navigation bar
        if (hasNavigationBar()) {
            int position = navigationBarPosition(displayWidth, displayHeight, displayRotation);
            if (position == NAV_BAR_BOTTOM) {
                outInsets.bottom = getNavigationBarHeight(displayRotation, mUiMode);
            } else if (position == NAV_BAR_RIGHT) {
                outInsets.right = getNavigationBarWidth(displayRotation, mUiMode);
            } else if (position == NAV_BAR_LEFT) {
                outInsets.left = getNavigationBarWidth(displayRotation, mUiMode);
            }
        }
    }

    @Override
    public boolean isNavBarForcedShownLw(WindowState windowState) {
        return mForceShowSystemBars;
    }

    @Override
    public int getNavBarPosition() {
        // TODO(multi-display): Support system decor on secondary displays.
        return mNavigationBarPosition;
    }

    @Override
    public boolean isDockSideAllowed(int dockSide) {

        // We do not allow all dock sides at which the navigation bar touches the docked stack.
        if (!mNavigationBarCanMove) {
            return dockSide == DOCKED_TOP || dockSide == DOCKED_LEFT || dockSide == DOCKED_RIGHT;
        } else {
            return dockSide == DOCKED_TOP || dockSide == DOCKED_LEFT;
        }
    }

    void sendCloseSystemWindows() {
        PhoneWindow.sendCloseSystemWindows(mContext, null);
    }

    void sendCloseSystemWindows(String reason) {
        PhoneWindow.sendCloseSystemWindows(mContext, reason);
    }

    @Override
    public int rotationForOrientationLw(int orientation, int lastRotation) {
        if (false) {
            Slog.v(TAG, "rotationForOrientationLw(orient="
                        + orientation + ", last=" + lastRotation
                        + "); user=" + mUserRotation + " "
                        + ((mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED)
                            ? "USER_ROTATION_LOCKED" : "")
                        );
        }

        if (mForceDefaultOrientation) {
            return Surface.ROTATION_0;
        }

        synchronized (mLock) {
            int sensorRotation = mOrientationListener.getProposedRotation(); // may be -1
            if (sensorRotation < 0) {
                sensorRotation = lastRotation;
            }

            final int preferredRotation;
            if (mLidState == LID_OPEN && mLidOpenRotation >= 0) {
                // Ignore sensor when lid switch is open and rotation is forced.
                preferredRotation = mLidOpenRotation;
            } else if (mDockMode == Intent.EXTRA_DOCK_STATE_CAR
                    && (mCarDockEnablesAccelerometer || mCarDockRotation >= 0)) {
                // Ignore sensor when in car dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mCarDockEnablesAccelerometer
                        ? sensorRotation : mCarDockRotation;
            } else if ((mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                    || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK
                    || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK)
                    && (mDeskDockEnablesAccelerometer || mDeskDockRotation >= 0)) {
                // Ignore sensor when in desk dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mDeskDockEnablesAccelerometer
                        ? sensorRotation : mDeskDockRotation;
            } else if (mHdmiPlugged && mDemoHdmiRotationLock) {
                // Ignore sensor when plugged into HDMI when demo HDMI rotation lock enabled.
                // Note that the dock orientation overrides the HDMI orientation.
                preferredRotation = mDemoHdmiRotation;
            } else if (mHdmiPlugged && mDockMode == Intent.EXTRA_DOCK_STATE_UNDOCKED
                    && mUndockedHdmiRotation >= 0) {
                // Ignore sensor when plugged into HDMI and an undocked orientation has
                // been specified in the configuration (only for legacy devices without
                // full multi-display support).
                // Note that the dock orientation overrides the HDMI orientation.
                preferredRotation = mUndockedHdmiRotation;
            } else if (mDemoRotationLock) {
                // Ignore sensor when demo rotation lock is enabled.
                // Note that the dock orientation and HDMI rotation lock override this.
                preferredRotation = mDemoRotation;
            } else if (mPersistentVrModeEnabled) {
                // While in VR, apps always prefer a portrait rotation. This does not change
                // any apps that explicitly set landscape, but does cause sensors be ignored,
                // and ignored any orientation lock that the user has set (this conditional
                // should remain above the ORIENTATION_LOCKED conditional below).
                preferredRotation = mPortraitRotation;
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
                // Application just wants to remain locked in the last rotation.
                preferredRotation = lastRotation;
            } else if (!mSupportAutoRotation) {
                // If we don't support auto-rotation then bail out here and ignore
                // the sensor and any rotation lock settings.
                preferredRotation = -1;
            } else if ((mUserRotationMode == WindowManagerPolicy.USER_ROTATION_FREE
                            && (orientation == ActivityInfo.SCREEN_ORIENTATION_USER
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_USER))
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
                // Otherwise, use sensor only if requested by the application or enabled
                // by default for USER or UNSPECIFIED modes.  Does not apply to NOSENSOR.
                if (mAllowAllRotations < 0) {
                    // Can't read this during init() because the context doesn't
                    // have display metrics at that time so we cannot determine
                    // tablet vs. phone then.
                    mAllowAllRotations = mContext.getResources().getBoolean(
                            com.android.internal.R.bool.config_allowAllRotations) ? 1 : 0;
                }
                // Rotation setting bitmask
                // 1=0 2=90 4=180 8=270
                boolean allowed = true;
                if (mUserRotationAngles < 0) {
                    // Not set by user so use these defaults
                    mUserRotationAngles = mAllowAllRotations == 1 ?
                            (1 | 2 | 4 | 8) : // All angles
                                (1 | 2 | 8); // All except 180
                }
                switch (sensorRotation) {
                    case Surface.ROTATION_0:
                        allowed = (mUserRotationAngles & 1) != 0;
                        break;
                    case Surface.ROTATION_90:
                        allowed = (mUserRotationAngles & 2) != 0;
                        break;
                    case Surface.ROTATION_180:
                        allowed = (mUserRotationAngles & 4) != 0;
                        break;
                    case Surface.ROTATION_270:
                        allowed = (mUserRotationAngles & 8) != 0;
                        break;
                }
                if (allowed) {
                    preferredRotation = sensorRotation;
                } else {
                    preferredRotation = lastRotation;
                }
            } else if (mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED
                    && orientation != ActivityInfo.SCREEN_ORIENTATION_NOSENSOR) {
                // Apply rotation lock.  Does not apply to NOSENSOR.
                // The idea is that the user rotation expresses a weak preference for the direction
                // of gravity and as NOSENSOR is never affected by gravity, then neither should
                // NOSENSOR be affected by rotation lock (although it will be affected by docks).
                preferredRotation = mUserRotation;
            } else {
                // No overriding preference.
                // We will do exactly what the application asked us to do.
                preferredRotation = -1;
            }

            switch (orientation) {
                case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                    // Return portrait unless overridden.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mPortraitRotation;

                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    // Return landscape unless overridden.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mLandscapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    // Return reverse portrait unless overridden.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mUpsideDownRotation;

                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    // Return seascape unless overridden.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mSeascapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE:
                    // Return either landscape rotation.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    if (isLandscapeOrSeascape(lastRotation)) {
                        return lastRotation;
                    }
                    return mLandscapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT:
                    // Return either portrait rotation.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    if (isAnyPortrait(lastRotation)) {
                        return lastRotation;
                    }
                    return mPortraitRotation;

                default:
                    // For USER, UNSPECIFIED, NOSENSOR, SENSOR and FULL_SENSOR,
                    // just return the preferred orientation we already calculated.
                    if (preferredRotation >= 0) {
                        return preferredRotation;
                    }
                    return Surface.ROTATION_0;
            }
        }
    }

    @Override
    public boolean rotationHasCompatibleMetricsLw(int orientation, int rotation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return isAnyPortrait(rotation);

            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return isLandscapeOrSeascape(rotation);

            default:
                return true;
        }
    }

    @Override
    public void setRotationLw(int rotation) {
        mOrientationListener.setCurrentRotation(rotation);
    }

    private boolean isLandscapeOrSeascape(int rotation) {
        return rotation == mLandscapeRotation || rotation == mSeascapeRotation;
    }

    private boolean isAnyPortrait(int rotation) {
        return rotation == mPortraitRotation || rotation == mUpsideDownRotation;
    }

    @Override
    public int getUserRotationMode() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT) != 0 ?
                        WindowManagerPolicy.USER_ROTATION_FREE :
                                WindowManagerPolicy.USER_ROTATION_LOCKED;
    }

    // User rotation: to be used when all else fails in assigning an orientation to the device
    @Override
    public void setUserRotationMode(int mode, int rot) {
        ContentResolver res = mContext.getContentResolver();

        // mUserRotationMode and mUserRotation will be assigned by the content observer
        if (mode == WindowManagerPolicy.USER_ROTATION_LOCKED) {
            Settings.System.putIntForUser(res,
                    Settings.System.USER_ROTATION,
                    rot,
                    UserHandle.USER_CURRENT);
            Settings.System.putIntForUser(res,
                    Settings.System.ACCELEROMETER_ROTATION,
                    0,
                    UserHandle.USER_CURRENT);
        } else {
            Settings.System.putIntForUser(res,
                    Settings.System.ACCELEROMETER_ROTATION,
                    1,
                    UserHandle.USER_CURRENT);
        }
    }

    @Override
    public void setSafeMode(boolean safeMode) {
        mSafeMode = safeMode;
        if (safeMode) {
            performHapticFeedbackLw(null, HapticFeedbackConstants.SAFE_MODE_ENABLED, true);
        }
    }

    static long[] getLongIntArray(Resources r, int resid) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return null;
        }
        long[] out = new long[ar.length];
        for (int i=0; i<ar.length; i++) {
            out[i] = ar[i];
        }
        return out;
    }

    private void bindKeyguard() {
        synchronized (mLock) {
            if (mKeyguardBound) {
                return;
            }
            mKeyguardBound = true;
        }
        mKeyguardDelegate.bindService(mContext);
    }

    @Override
    public void onSystemUiStarted() {
        bindKeyguard();
    }

    /** {@inheritDoc} */
    @Override
    public void systemReady() {
        // In normal flow, systemReady is called before other system services are ready.
        // So it is better not to bind keyguard here.
        mPocketManager = (PocketManager) mContext.getSystemService(Context.POCKET_SERVICE);
        mPocketManager.addCallback(mPocketCallback);
        mPocketLock = new PocketLock(mContext);

        mKeyguardDelegate.onSystemReady();

        mVrManagerInternal = LocalServices.getService(VrManagerInternal.class);
        if (mVrManagerInternal != null) {
            mVrManagerInternal.addPersistentVrModeStateListener(mPersistentVrModeListener);
        }
        mEdgeGestureManager = EdgeGestureManager.getInstance();
        mEdgeGestureManager.setEdgeGestureActivationListener(mEdgeGestureActivationListener);
        mSettingsObserver.observe();

        if (mDeviceHardwareKeys > 0) {
            mANBIHandler = new ANBIHandler(mContext);
        }

        mLineageHardware = LineageHardwareManager.getInstance(mContext);
        // Ensure observe happens in systemReady() since we need
        // LineageHardwareService to be up and running
        mSettingsObserver.observe();

        readCameraLensCoverState();
        updateUiMode();
        synchronized (mLock) {
            updateOrientationListenerLp();
            mSystemReady = true;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateSettings();
                }
            });
            // If this happens, for whatever reason, systemReady came later than systemBooted.
            // And keyguard should be already bound from systemBooted
            if (mSystemBooted) {
                mKeyguardDelegate.onBootCompleted();
            }
        }

        mLineageButtons = new LineageButtons(mContext);
        mSystemGestures.systemReady();
        mImmersiveModeConfirmation.systemReady();

        mAutofillManagerInternal = LocalServices.getService(AutofillManagerInternal.class);
    }

    /** {@inheritDoc} */
    @Override
    public void systemBooted() {
        bindKeyguard();
        synchronized (mLock) {
            mSystemBooted = true;
            if (mSystemReady) {
                mKeyguardDelegate.onBootCompleted();
            }
        }
        startedWakingUp();
        screenTurningOn(null);
        screenTurnedOn();
    }

    @Override
    public boolean canDismissBootAnimation() {
        synchronized (mLock) {
            return mKeyguardDrawComplete;
        }
    }

    ProgressDialog mBootMsgDialog = null;

    /** {@inheritDoc} */
    @Override
    public void showBootMessage(final CharSequence msg, final boolean always) {
        mHandler.post(new Runnable() {
            @Override public void run() {
                if (mBootMsgDialog == null) {
                    int theme;
                    if (mContext.getPackageManager().hasSystemFeature(FEATURE_LEANBACK)) {
                        theme = com.android.internal.R.style.Theme_Leanback_Dialog_Alert;
                    } else {
                        theme = 0;
                    }

                    mBootMsgDialog = new ProgressDialog(mContext, theme) {
                        // This dialog will consume all events coming in to
                        // it, to avoid it trying to do things too early in boot.
                        @Override public boolean dispatchKeyEvent(KeyEvent event) {
                            return true;
                        }
                        @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
                            return true;
                        }
                        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchTrackballEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchPopulateAccessibilityEvent(
                                AccessibilityEvent event) {
                            return true;
                        }
                    };
                    if (mContext.getPackageManager().isUpgrade()) {
                        mBootMsgDialog.setTitle(R.string.android_upgrading_title);
                    } else {
                        mBootMsgDialog.setTitle(R.string.android_start_title);
                    }
                    mBootMsgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mBootMsgDialog.setIndeterminate(true);
                    mBootMsgDialog.getWindow().setType(
                            WindowManager.LayoutParams.TYPE_BOOT_PROGRESS);
                    mBootMsgDialog.getWindow().addFlags(
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                    mBootMsgDialog.getWindow().setDimAmount(1);
                    WindowManager.LayoutParams lp = mBootMsgDialog.getWindow().getAttributes();
                    lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
                    mBootMsgDialog.getWindow().setAttributes(lp);
                    mBootMsgDialog.setCancelable(false);
                    mBootMsgDialog.show();
                }
                mBootMsgDialog.setMessage(msg);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void hideBootMessages() {
        mHandler.sendEmptyMessage(MSG_HIDE_BOOT_MESSAGE);
    }

    /** {@inheritDoc} */
    @Override
    public void userActivity() {
        // ***************************************
        // NOTE NOTE NOTE NOTE NOTE NOTE NOTE NOTE
        // ***************************************
        // THIS IS CALLED FROM DEEP IN THE POWER MANAGER
        // WITH ITS LOCKS HELD.
        //
        // This code must be VERY careful about the locks
        // it acquires.
        // In fact, the current code acquires way too many,
        // and probably has lurking deadlocks.

        synchronized (mScreenLockTimeout) {
            if (mLockScreenTimerActive) {
                // reset the timer
                mHandler.removeCallbacks(mScreenLockTimeout);
                mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
            }
        }
    }

    class ScreenLockTimeout implements Runnable {
        Bundle options;

        @Override
        public void run() {
            synchronized (this) {
                if (localLOGV) Log.v(TAG, "mScreenLockTimeout activating keyguard");
                if (mKeyguardDelegate != null) {
                    mKeyguardDelegate.doKeyguardTimeout(options);
                }
                mLockScreenTimerActive = false;
                options = null;
            }
        }

        public void setLockOptions(Bundle options) {
            this.options = options;
        }
    }

    ScreenLockTimeout mScreenLockTimeout = new ScreenLockTimeout();

    @Override
    public void lockNow(Bundle options) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER, null);
        mHandler.removeCallbacks(mScreenLockTimeout);
        if (options != null) {
            // In case multiple calls are made to lockNow, we don't wipe out the options
            // until the runnable actually executes.
            mScreenLockTimeout.setLockOptions(options);
        }
        mHandler.post(mScreenLockTimeout);
    }

    private void updateLockScreenTimeout() {
        synchronized (mScreenLockTimeout) {
            boolean enable = (mAllowLockscreenWhenOn && mAwake &&
                    mKeyguardDelegate != null && mKeyguardDelegate.isSecure(mCurrentUserId));
            if (mLockScreenTimerActive != enable) {
                if (enable) {
                    if (localLOGV) Log.v(TAG, "setting lockscreen timer");
                    mHandler.removeCallbacks(mScreenLockTimeout); // remove any pending requests
                    mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
                } else {
                    if (localLOGV) Log.v(TAG, "clearing lockscreen timer");
                    mHandler.removeCallbacks(mScreenLockTimeout);
                }
                mLockScreenTimerActive = enable;
            }
        }
    }

    // TODO (multidisplay): Support multiple displays in WindowManagerPolicy.
    private void updateDreamingSleepToken(boolean acquire) {
        if (acquire) {
            if (mDreamingSleepToken == null) {
                mDreamingSleepToken = mActivityManagerInternal.acquireSleepToken(
                        "Dream", DEFAULT_DISPLAY);
            }
        } else {
            if (mDreamingSleepToken != null) {
                mDreamingSleepToken.release();
                mDreamingSleepToken = null;
            }
        }
    }

    // TODO (multidisplay): Support multiple displays in WindowManagerPolicy.
    private void updateScreenOffSleepToken(boolean acquire) {
        if (acquire) {
            if (mScreenOffSleepToken == null) {
                mScreenOffSleepToken = mActivityManagerInternal.acquireSleepToken(
                        "ScreenOff", DEFAULT_DISPLAY);
            }
        } else {
            if (mScreenOffSleepToken != null) {
                mScreenOffSleepToken.release();
                mScreenOffSleepToken = null;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void enableScreenAfterBoot() {
        readLidState();
        applyLidSwitchState();
        updateRotation(true);
    }

    private void applyLidSwitchState() {
        if (mLidState == LID_CLOSED && mLidControlsSleep) {
            goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH,
                    PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
        } else if (mLidState == LID_CLOSED && mLidControlsScreenLock) {
            mWindowManagerFuncs.lockDeviceNow();
        }

        synchronized (mLock) {
            updateWakeGestureListenerLp();
        }
        sendLidChangeBroadcast();
    }

    private void sendLidChangeBroadcast() {
        Log.d(TAG, "Sending cover change broadcast, mLidState=" + mLidState);
        Intent intent = new Intent(lineageos.content.Intent.ACTION_LID_STATE_CHANGED);
        intent.putExtra(lineageos.content.Intent.EXTRA_LID_STATE, mLidState);
        intent.setFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        mContext.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }

    void updateUiMode() {
        if (mUiModeManager == null) {
            mUiModeManager = IUiModeManager.Stub.asInterface(
                    ServiceManager.getService(Context.UI_MODE_SERVICE));
        }
        try {
            mUiMode = mUiModeManager.getCurrentModeType();
        } catch (RemoteException e) {
        }
    }

    void updateRotation(boolean alwaysSendConfiguration) {
        try {
            //set orientation on WindowManager
            mWindowManager.updateRotation(alwaysSendConfiguration, false);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout) {
        try {
            //set orientation on WindowManager
            mWindowManager.updateRotation(alwaysSendConfiguration, forceRelayout);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    /**
     * Return an Intent to launch the currently active dock app as home.  Returns
     * null if the standard home should be launched, which is the case if any of the following is
     * true:
     * <ul>
     *  <li>The device is not in either car mode or desk mode
     *  <li>The device is in car mode but mEnableCarDockHomeCapture is false
     *  <li>The device is in desk mode but ENABLE_DESK_DOCK_HOME_CAPTURE is false
     *  <li>The device is in car mode but there's no CAR_DOCK app with METADATA_DOCK_HOME
     *  <li>The device is in desk mode but there's no DESK_DOCK app with METADATA_DOCK_HOME
     * </ul>
     * @return A dock intent.
     */
    Intent createHomeDockIntent() {
        Intent intent = null;

        // What home does is based on the mode, not the dock state.  That
        // is, when in car mode you should be taken to car home regardless
        // of whether we are actually in a car dock.
        if (mUiMode == Configuration.UI_MODE_TYPE_CAR) {
            if (mEnableCarDockHomeCapture) {
                intent = mCarDockIntent;
            }
        } else if (mUiMode == Configuration.UI_MODE_TYPE_DESK) {
            if (ENABLE_DESK_DOCK_HOME_CAPTURE) {
                intent = mDeskDockIntent;
            }
        } else if (mUiMode == Configuration.UI_MODE_TYPE_WATCH
                && (mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK)) {
            // Always launch dock home from home when watch is docked, if it exists.
            intent = mDeskDockIntent;
        } else if (mUiMode == Configuration.UI_MODE_TYPE_VR_HEADSET) {
            if (ENABLE_VR_HEADSET_HOME_CAPTURE) {
                intent = mVrHeadsetHomeIntent;
            }
        }

        if (intent == null) {
            return null;
        }

        ActivityInfo ai = null;
        ResolveInfo info = mContext.getPackageManager().resolveActivityAsUser(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA,
                mCurrentUserId);
        if (info != null) {
            ai = info.activityInfo;
        }
        if (ai != null
                && ai.metaData != null
                && ai.metaData.getBoolean(Intent.METADATA_DOCK_HOME)) {
            intent = new Intent(intent);
            intent.setClassName(ai.packageName, ai.name);
            return intent;
        }

        return null;
    }

    void startDockOrHome(boolean fromHomeKey, boolean awakenFromDreams) {
        if (awakenFromDreams) {
            awakenDreams();
        }

        Intent dock = createHomeDockIntent();
        if (dock != null) {
            try {
                if (fromHomeKey) {
                    dock.putExtra(WindowManagerPolicy.EXTRA_FROM_HOME_KEY, fromHomeKey);
                }
                startActivityAsUser(dock, UserHandle.CURRENT);
                return;
            } catch (ActivityNotFoundException e) {
            }
        }

        Intent intent;

        if (fromHomeKey) {
            intent = new Intent(mHomeIntent);
            intent.putExtra(WindowManagerPolicy.EXTRA_FROM_HOME_KEY, fromHomeKey);
        } else {
            intent = mHomeIntent;
        }

        startActivityAsUser(intent, UserHandle.CURRENT);
    }

    /**
     * goes to the home screen
     * @return whether it did anything
     */
    boolean goHome() {
        if (!isUserSetupComplete()) {
            Slog.i(TAG, "Not going home because user setup is in progress.");
            return false;
        }
        if (false) {
            // This code always brings home to the front.
            try {
                ActivityManager.getService().stopAppSwitches();
            } catch (RemoteException e) {
            }
            sendCloseSystemWindows();
            startDockOrHome(false /*fromHomeKey*/, true /* awakenFromDreams */);
        } else {
            // This code brings home to the front or, if it is already
            // at the front, puts the device to sleep.
            try {
                if (SystemProperties.getInt("persist.sys.uts-test-mode", 0) == 1) {
                    /// Roll back EndcallBehavior as the cupcake design to pass P1 lab entry.
                    Log.d(TAG, "UTS-TEST-MODE");
                } else {
                    ActivityManager.getService().stopAppSwitches();
                    sendCloseSystemWindows();
                    Intent dock = createHomeDockIntent();
                    if (dock != null) {
                        int result = ActivityManager.getService()
                                .startActivityAsUser(null, null, dock,
                                        dock.resolveTypeIfNeeded(mContext.getContentResolver()),
                                        null, null, 0,
                                        ActivityManager.START_FLAG_ONLY_IF_NEEDED,
                                        null, null, UserHandle.USER_CURRENT);
                        if (result == ActivityManager.START_RETURN_INTENT_TO_CALLER) {
                            return false;
                        }
                    }
                }
                int result = ActivityManager.getService()
                        .startActivityAsUser(null, null, mHomeIntent,
                                mHomeIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                                null, null, 0,
                                ActivityManager.START_FLAG_ONLY_IF_NEEDED,
                                null, null, UserHandle.USER_CURRENT);
                if (result == ActivityManager.START_RETURN_INTENT_TO_CALLER) {
                    return false;
                }
            } catch (RemoteException ex) {
                // bummer, the activity manager, which is in this process, is dead
            }
        }
        return true;
    }

    @Override
    public void setCurrentOrientationLw(int newOrientation) {
        synchronized (mLock) {
            if (newOrientation != mCurrentAppOrientation) {
                mCurrentAppOrientation = newOrientation;
                updateOrientationListenerLp();
            }
        }
    }

    private boolean isTheaterModeEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.THEATER_MODE_ON, 0) == 1;
    }

    @Override
    public boolean performHapticFeedbackLw(WindowState win, int effectId, boolean always) {
        if (!mVibrator.hasVibrator()) {
            return false;
        }
        final boolean hapticsDisabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0, UserHandle.USER_CURRENT) == 0;
        if (hapticsDisabled && !always) {
            return false;
        }

        VibrationEffect effect = getVibrationEffect(effectId);
        if (effect == null) {
            return false;
        }

        int owningUid;
        String owningPackage;
        if (win != null) {
            owningUid = win.getOwningUid();
            owningPackage = win.getOwningPackage();
        } else {
            owningUid = android.os.Process.myUid();
            owningPackage = mContext.getOpPackageName();
        }
        mVibrator.vibrate(owningUid, owningPackage, effect, VIBRATION_ATTRIBUTES);
        return true;
    }

    private VibrationEffect getVibrationEffect(int effectId) {
        long[] pattern;
        switch (effectId) {
            case HapticFeedbackConstants.LONG_PRESS:
                pattern = mLongPressVibePattern;
                break;
            case HapticFeedbackConstants.CLOCK_TICK:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK);
            case HapticFeedbackConstants.CALENDAR_DATE:
                pattern = mCalendarDateVibePattern;
                break;
            case HapticFeedbackConstants.SAFE_MODE_ENABLED:
                pattern = mSafeModeEnabledVibePattern;
                break;
            case HapticFeedbackConstants.CONTEXT_CLICK:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK);
            case HapticFeedbackConstants.VIRTUAL_KEY:
                return VibrationEffect.get(VibrationEffect.EFFECT_CLICK);
            case HapticFeedbackConstants.VIRTUAL_KEY_RELEASE:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK, false);
            case HapticFeedbackConstants.KEYBOARD_PRESS:  // == HapticFeedbackConstants.KEYBOARD_TAP
                return VibrationEffect.get(VibrationEffect.EFFECT_CLICK);
            case HapticFeedbackConstants.KEYBOARD_RELEASE:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK, false);
            case HapticFeedbackConstants.TEXT_HANDLE_MOVE:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK, false);
            default:
                return null;
        }
        if (pattern.length == 0) {
            // No vibration
            return null;
        } else if (pattern.length == 1) {
            // One-shot vibration
            return VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE);
        } else {
            // Pattern vibration
            return VibrationEffect.createWaveform(pattern, -1);
        }
    }

    @Override
    public void keepScreenOnStartedLw() {
    }

    @Override
    public void keepScreenOnStoppedLw() {
        if (isKeyguardShowingAndNotOccluded()) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    private int updateSystemUiVisibilityLw() {
        // If there is no window focused, there will be nobody to handle the events
        // anyway, so just hang on in whatever state we're in until things settle down.
        WindowState winCandidate = mFocusedWindow != null ? mFocusedWindow
                : mTopFullscreenOpaqueWindowState;
        if (winCandidate == null) {
            return 0;
        }
        if (winCandidate.getAttrs().token == mImmersiveModeConfirmation.getWindowToken()) {
            // The immersive mode confirmation should never affect the system bar visibility,
            // otherwise it will unhide the navigation bar and hide itself.
            winCandidate = isStatusBarKeyguard() ? mStatusBar : mTopFullscreenOpaqueWindowState;
            if (winCandidate == null) {
                return 0;
            }
        }
        final WindowState win = winCandidate;
        if ((win.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0 && mKeyguardOccluded) {
            // We are updating at a point where the keyguard has gotten
            // focus, but we were last in a state where the top window is
            // hiding it.  This is probably because the keyguard as been
            // shown while the top window was displayed, so we want to ignore
            // it here because this is just a very transient change and it
            // will quickly lose focus once it correctly gets hidden.
            return 0;
        }

        int tmpVisibility = WindowManagerPolicyControl.getSystemUiVisibility(win, null)
                & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;
        boolean wasCleared = mClearedBecauseOfForceShow;
        if (mForcingShowNavBar && win.getSurfaceLayer() < mForcingShowNavBarLayer) {
            tmpVisibility &=
<<<<<<< HEAD
                    ~WindowManagerPolicyControl.adjustClearableFlags(win,
                            View.SYSTEM_UI_CLEARABLE_FLAGS);
=======
                    ~WindowManagerPolicyControl.adjustClearableFlags(win, View.SYSTEM_UI_CLEARABLE_FLAGS);
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
            mClearedBecauseOfForceShow = true;
        } else {
            mClearedBecauseOfForceShow = false;
        }

        // The window who requested navbar force showing disappeared and next window wants
        // to hide navbar. Instead of hiding we will make it transient. SystemUI will take care
        // about hiding after timeout. This should not happen if next window is keyguard because
        // transient state have more priority than translucent (why?) and cause bad UX
        if (wasCleared && !mClearedBecauseOfForceShow
                && (tmpVisibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0) {
            mNavigationBarController.showTransient();
            tmpVisibility |= View.NAVIGATION_BAR_TRANSIENT;
            mWindowManagerFuncs.addSystemUIVisibilityFlag(View.NAVIGATION_BAR_TRANSIENT);
        }

        boolean topWindowWasKeyguard = mTopWindowIsKeyguard;
        mTopWindowIsKeyguard = (win.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0;
        if (topWindowWasKeyguard && !mTopWindowIsKeyguard
                && (tmpVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
            mStatusBarController.showTransient();
            tmpVisibility |= View.STATUS_BAR_TRANSIENT;
            mWindowManagerFuncs.addSystemUIVisibilityFlag(View.STATUS_BAR_TRANSIENT);
        }

        final int fullscreenVisibility = updateLightStatusBarLw(0 /* vis */,
                mTopFullscreenOpaqueWindowState, mTopFullscreenOpaqueOrDimmingWindowState);
        final int dockedVisibility = updateLightStatusBarLw(0 /* vis */,
                mTopDockedOpaqueWindowState, mTopDockedOpaqueOrDimmingWindowState);
        mWindowManagerFuncs.getStackBounds(HOME_STACK_ID, mNonDockedStackBounds);
        mWindowManagerFuncs.getStackBounds(DOCKED_STACK_ID, mDockedStackBounds);
        final int visibility = updateSystemBarsLw(win, mLastSystemUiFlags, tmpVisibility);
        final int diff = visibility ^ mLastSystemUiFlags;
        final int fullscreenDiff = fullscreenVisibility ^ mLastFullscreenStackSysUiFlags;
        final int dockedDiff = dockedVisibility ^ mLastDockedStackSysUiFlags;
        final boolean needsMenu = win.getNeedsMenuLw(mTopFullscreenOpaqueWindowState);
        if (diff == 0 && fullscreenDiff == 0 && dockedDiff == 0 && mLastFocusNeedsMenu == needsMenu
                && mFocusedApp == win.getAppToken()
                && mLastNonDockedStackBounds.equals(mNonDockedStackBounds)
                && mLastDockedStackBounds.equals(mDockedStackBounds)) {
            return 0;
        }
        mLastSystemUiFlags = visibility;
        mLastFullscreenStackSysUiFlags = fullscreenVisibility;
        mLastDockedStackSysUiFlags = dockedVisibility;
        mLastFocusNeedsMenu = needsMenu;
        mFocusedApp = win.getAppToken();
        final Rect fullscreenStackBounds = new Rect(mNonDockedStackBounds);
        final Rect dockedStackBounds = new Rect(mDockedStackBounds);
        mHandler.post(new Runnable() {
                @Override
                public void run() {
                    StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
                    if (statusbar != null) {
                        statusbar.setSystemUiVisibility(visibility, fullscreenVisibility,
                                dockedVisibility, 0xffffffff, fullscreenStackBounds,
                                dockedStackBounds, win.toString());
                        statusbar.topAppWindowChanged(needsMenu);
                    }
                }
            });
        return diff;
    }

    private int updateLightStatusBarLw(int vis, WindowState opaque, WindowState opaqueOrDimming) {
        final boolean onKeyguard = isStatusBarKeyguard() && !mKeyguardOccluded;
        final WindowState statusColorWin = onKeyguard ? mStatusBar : opaqueOrDimming;
        if (statusColorWin != null && (statusColorWin == opaque || onKeyguard)) {
            // If the top fullscreen-or-dimming window is also the top fullscreen, respect
            // its light flag.
            vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            vis |= WindowManagerPolicyControl.getSystemUiVisibility(statusColorWin, null)
                    & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else if (statusColorWin != null && statusColorWin.isDimming()) {
            // Otherwise if it's dimming, clear the light flag.
            vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        return vis;
    }

    private int updateLightNavigationBarLw(int vis, WindowState opaque,
            WindowState opaqueOrDimming) {
        final WindowState imeWin = mWindowManagerFuncs.getInputMethodWindowLw();

        final WindowState navColorWin;
        if (imeWin != null && imeWin.isVisibleLw() && mNavigationBarPosition == NAV_BAR_BOTTOM) {
            navColorWin = imeWin;
        } else {
            navColorWin = opaqueOrDimming;
        }

        if (navColorWin != null) {
            if (navColorWin == opaque) {
                // If the top fullscreen-or-dimming window is also the top fullscreen, respect
                // its light flag.
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                vis |= WindowManagerPolicyControl.getSystemUiVisibility(navColorWin, null)
                        & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else if (navColorWin.isDimming() || navColorWin == imeWin) {
                // Otherwise if it's dimming or it's the IME window, clear the light flag.
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        return vis;
    }

    private boolean drawsSystemBarBackground(WindowState win) {
        return win == null || (win.getAttrs().flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;
    }

    private boolean forcesDrawStatusBarBackground(WindowState win) {
        return win == null || (win.getAttrs().privateFlags
                & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) != 0;
    }

    private int updateSystemBarsLw(WindowState win, int oldVis, int vis) {
        final boolean dockedStackVisible = mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID);
        final boolean freeformStackVisible =
                mWindowManagerInternal.isStackVisible(FREEFORM_WORKSPACE_STACK_ID);
        final boolean resizing = mWindowManagerInternal.isDockedDividerResizing();

        // We need to force system bars when the docked stack is visible, when the freeform stack
        // is visible but also when we are resizing for the transitions when docked stack
        // visibility changes.
        mForceShowSystemBars = dockedStackVisible || freeformStackVisible || resizing;
        final boolean forceOpaqueStatusBar = mForceShowSystemBars && !mForceStatusBarFromKeyguard;

        // apply translucent bar vis flags
        WindowState fullscreenTransWin = isStatusBarKeyguard() && !mKeyguardOccluded
                ? mStatusBar
                : mTopFullscreenOpaqueWindowState;
        vis = mStatusBarController.applyTranslucentFlagLw(fullscreenTransWin, vis, oldVis);
        vis = mNavigationBarController.applyTranslucentFlagLw(fullscreenTransWin, vis, oldVis);
        final int dockedVis = mStatusBarController.applyTranslucentFlagLw(
                mTopDockedOpaqueWindowState, 0, 0);

        final boolean fullscreenDrawsStatusBarBackground =
                (drawsSystemBarBackground(mTopFullscreenOpaqueWindowState)
                        && (vis & View.STATUS_BAR_TRANSLUCENT) == 0)
                || forcesDrawStatusBarBackground(mTopFullscreenOpaqueWindowState);
        final boolean dockedDrawsStatusBarBackground =
                (drawsSystemBarBackground(mTopDockedOpaqueWindowState)
                        && (dockedVis & View.STATUS_BAR_TRANSLUCENT) == 0)
                || forcesDrawStatusBarBackground(mTopDockedOpaqueWindowState);

        // prevent status bar interaction from clearing certain flags
        int type = win.getAttrs().type;
        boolean statusBarHasFocus = type == TYPE_STATUS_BAR;
        if (statusBarHasFocus && !isStatusBarKeyguard()) {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (mKeyguardOccluded) {
                flags |= View.STATUS_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSLUCENT;
            }
            vis = (vis & ~flags) | (oldVis & flags);
        }

        if (fullscreenDrawsStatusBarBackground && dockedDrawsStatusBarBackground) {
            vis |= View.STATUS_BAR_TRANSPARENT;
            vis &= ~View.STATUS_BAR_TRANSLUCENT;
        } else if ((!areTranslucentBarsAllowed() && fullscreenTransWin != mStatusBar)
                || forceOpaqueStatusBar) {
            vis &= ~(View.STATUS_BAR_TRANSLUCENT | View.STATUS_BAR_TRANSPARENT);
        }

        vis = configureNavBarOpacity(vis, dockedStackVisible, freeformStackVisible, resizing);

        // update status bar
        boolean immersiveSticky =
                (vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
        final boolean hideStatusBarWM =
                mTopFullscreenOpaqueWindowState != null
                && (WindowManagerPolicyControl.getWindowFlags(mTopFullscreenOpaqueWindowState, null)
                        & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
        final boolean hideStatusBarSysui =
                (vis & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
        final boolean hideNavBarSysui =
                (vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0;

        final boolean transientStatusBarAllowed = mStatusBar != null
                && (statusBarHasFocus || (!mForceShowSystemBars
                        && (hideStatusBarWM || (hideStatusBarSysui && immersiveSticky))));

        final boolean transientNavBarAllowed = mNavigationBar != null
                && !mForceShowSystemBars && hideNavBarSysui && immersiveSticky;

        final long now = SystemClock.uptimeMillis();
        final boolean pendingPanic = mPendingPanicGestureUptime != 0
                && now - mPendingPanicGestureUptime <= PANIC_GESTURE_EXPIRATION;
        if (pendingPanic && hideNavBarSysui && !isStatusBarKeyguard() && mKeyguardDrawComplete) {
            // The user performed the panic gesture recently, we're about to hide the bars,
            // we're no longer on the Keyguard and the screen is ready. We can now request the bars.
            mPendingPanicGestureUptime = 0;
            mStatusBarController.showTransient();
            if (!isNavBarEmpty(vis) && !immersiveModeImplementsPie()) {
                mNavigationBarController.showTransient();
            }
        }

        final boolean denyTransientStatus = mStatusBarController.isTransientShowRequested()
                && !transientStatusBarAllowed && hideStatusBarSysui;
        final boolean denyTransientNav = mNavigationBarController.isTransientShowRequested()
                && !transientNavBarAllowed;
        if (denyTransientStatus || denyTransientNav || mForceShowSystemBars) {
            // clear the clearable flags instead
            clearClearableFlagsLw();
            vis &= ~View.SYSTEM_UI_CLEARABLE_FLAGS;
        }

        final boolean immersive = (vis & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
        immersiveSticky = (vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
        final boolean navAllowedHidden = immersive || immersiveSticky;

        if (hideNavBarSysui && !navAllowedHidden
                && getWindowLayerLw(win) > getWindowLayerFromTypeLw(TYPE_INPUT_CONSUMER)) {
            // We can't hide the navbar from this window otherwise the input consumer would not get
            // the input events.
            vis = (vis & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        vis = mStatusBarController.updateVisibilityLw(transientStatusBarAllowed, oldVis, vis);

        // update navigation bar
        boolean oldImmersiveMode = isImmersiveMode(oldVis);
        boolean newImmersiveMode = isImmersiveMode(vis);
        if (win != null && oldImmersiveMode != newImmersiveMode) {
            final String pkg = win.getOwningPackage();
            mImmersiveModeConfirmation.immersiveModeChangedLw(pkg, newImmersiveMode,
                    isUserSetupComplete(), isNavBarEmpty(win.getSystemUiVisibility()));
        }

        vis = mNavigationBarController.updateVisibilityLw(transientNavBarAllowed, oldVis, vis);

        vis = updateLightNavigationBarLw(vis, mTopFullscreenOpaqueWindowState,
                mTopFullscreenOpaqueOrDimmingWindowState);

        return vis;
    }

    /**
     * @return the current visibility flags with the nav-bar opacity related flags toggled based
     *         on the nav bar opacity rules chosen by {@link #mNavBarOpacityMode}.
     */
    private int configureNavBarOpacity(int visibility, boolean dockedStackVisible,
            boolean freeformStackVisible, boolean isDockedDividerResizing) {
        if (mNavBarOpacityMode == NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED) {
            if (dockedStackVisible || freeformStackVisible || isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        } else if (mNavBarOpacityMode == NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE) {
            if (isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            } else if (freeformStackVisible) {
                visibility = setNavBarTranslucentFlag(visibility);
            } else {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        }

        if (!areTranslucentBarsAllowed()) {
            visibility &= ~View.NAVIGATION_BAR_TRANSLUCENT;
        }
        return visibility;
    }

    private int setNavBarOpaqueFlag(int visibility) {
        return visibility &= ~(View.NAVIGATION_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSPARENT);
    }

    private int setNavBarTranslucentFlag(int visibility) {
        visibility &= ~View.NAVIGATION_BAR_TRANSPARENT;
        return visibility |= View.NAVIGATION_BAR_TRANSLUCENT;
    }

    private void clearClearableFlagsLw() {
        int newVal = mResettingSystemUiFlags | View.SYSTEM_UI_CLEARABLE_FLAGS;
        if (newVal != mResettingSystemUiFlags) {
            mResettingSystemUiFlags = newVal;
            mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    }

    private boolean isImmersiveMode(int vis) {
        final int flags = View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return mNavigationBar != null
                && (vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                && (vis & flags) != 0
                && canHideNavigationBar();
    }

    private static boolean isNavBarEmpty(int systemUiFlags) {
        final int disableNavigationBar = (View.STATUS_BAR_DISABLE_HOME
                | View.STATUS_BAR_DISABLE_BACK
                | View.STATUS_BAR_DISABLE_RECENT);

        return (systemUiFlags & disableNavigationBar) == disableNavigationBar;
    }

    /**
     * @return whether the navigation or status bar can be made translucent
     *
     * This should return true unless touch exploration is not enabled or
     * R.boolean.config_enableTranslucentDecor is false.
     */
    private boolean areTranslucentBarsAllowed() {
        return mTranslucentDecorEnabled;
    }

    // Use this instead of checking config_showNavigationBar so that it can be consistently
    // overridden by qemu.hw.mainkeys in the emulator.
    @Override
    public boolean hasNavigationBar() {
<<<<<<< HEAD
        return mNavbarVisible;
    }

    /*
     * TODO: provide implementation later in bringup
     */
    @Override
    public void sendCustomAction(Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            if (ActionHandler.INTENT_SHOW_POWER_MENU.equals(action)) {
                showGlobalActions();
            } else if (ActionHandler.INTENT_SCREENSHOT.equals(action)) {
                mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_SURFACE_FLINGER, TAG + "sendCustomAction permission denied");
                mHandler.removeCallbacks(mScreenshotRunnable);
                mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_FULLSCREEN);
                mHandler.post(mScreenshotRunnable);
            } else if (ActionHandler.INTENT_REGION_SCREENSHOT.equals(action)) {
                mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_SURFACE_FLINGER, TAG + "sendCustomAction permission denied");
                mHandler.removeCallbacks(mScreenshotRunnable);
                mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_SELECTED_REGION);
                mHandler.post(mScreenshotRunnable);
            }/* else if (ActionHandler.INTENT_TOGGLE_SCREENRECORD.equals(action)) {
                mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_SURFACE_FLINGER, TAG + "sendCustomAction permission denied");
                mHandler.removeCallbacks(mScreenrecordRunnable);
                mHandler.post(mScreenrecordRunnable);
            }*/
        }
    }

    @Override
    public boolean hasPermanentMenuKey() {
        return !hasNavigationBar() && mHasPermanentMenuKey;
=======
        return mHasNavigationBar || mDevForceNavbar == 1;
    }

    public boolean needsNavigationBar() {
        return mHasNavigationBar;
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
    }

    @Override
    public void setLastInputMethodWindowLw(WindowState ime, WindowState target) {
        mLastInputMethodWindow = ime;
        mLastInputMethodTargetWindow = target;
    }

    @Override
    public void setDismissImeOnBackKeyPressed(boolean newValue) {
        mDismissImeOnBackKeyPressed = newValue;
    }

    @Override
    public int getInputMethodWindowVisibleHeightLw() {
        return mDockBottom - mCurBottom;
    }

    @Override
    public void setCurrentUserLw(int newUserId) {
        mCurrentUserId = newUserId;
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.setCurrentUser(newUserId);
        }
        if (mAccessibilityShortcutController != null) {
            mAccessibilityShortcutController.setCurrentUser(newUserId);
        }
        StatusBarManagerInternal statusBar = getStatusBarManagerInternal();
        if (statusBar != null) {
            statusBar.setCurrentUser(newUserId);
        }
        setLastInputMethodWindowLw(null, null);
    }

    @Override
    public void setSwitchingUser(boolean switching) {
        mKeyguardDelegate.setSwitchingUser(switching);
    }

    @Override
    public boolean canMagnifyWindow(int windowType) {
        switch (windowType) {
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD:
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG:
            case WindowManager.LayoutParams.TYPE_NAVIGATION_BAR:
            case WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY: {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTopLevelWindow(int windowType) {
        if (windowType >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
                && windowType <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            return (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        }
        return true;
    }

    @Override
    public boolean shouldRotateSeamlessly(int oldRotation, int newRotation) {
        // For the upside down rotation we don't rotate seamlessly as the navigation
        // bar moves position.
        // Note most apps (using orientation:sensor or user as opposed to fullSensor)
        // will not enter the reverse portrait orientation, so actually the
        // orientation won't change at all.
        if (oldRotation == mUpsideDownRotation || newRotation == mUpsideDownRotation) {
            return false;
        }
        // If the navigation bar can't change sides, then it will
        // jump when we change orientations and we don't rotate
        // seamlessly.
        if (!mNavigationBarCanMove) {
            return false;
        }
        int delta = newRotation - oldRotation;
        if (delta < 0) delta += 4;
        // Likewise we don't rotate seamlessly for 180 degree rotations
        // in this case the surfaces never resize, and our logic to
        // revert the transformations on size change will fail. We could
        // fix this in the future with the "tagged" frames idea.
        if (delta == Surface.ROTATION_180) {
            return false;
        }

        final WindowState w = mTopFullscreenOpaqueWindowState;
        if (w != mFocusedWindow) {
            return false;
        }

        // We only enable seamless rotation if the top window has requested
        // it and is in the fullscreen opaque state. Seamless rotation
        // requires freezing various Surface states and won't work well
        // with animations, so we disable it in the animation case for now.
        if (w != null && !w.isAnimatingLw() &&
                ((w.getAttrs().rotationAnimation == ROTATION_ANIMATION_JUMPCUT) ||
                        (w.getAttrs().rotationAnimation == ROTATION_ANIMATION_SEAMLESS))) {
            return true;
        }
        return false;
    }

    @Override
    public void dump(String prefix, PrintWriter pw, String[] args) {
        pw.print(prefix); pw.print("mSafeMode="); pw.print(mSafeMode);
                pw.print(" mSystemReady="); pw.print(mSystemReady);
                pw.print(" mSystemBooted="); pw.println(mSystemBooted);
        pw.print(prefix); pw.print("mLidState="); pw.print(mLidState);
                pw.print(" mLidOpenRotation="); pw.print(mLidOpenRotation);
                pw.print(" mCameraLensCoverState="); pw.print(mCameraLensCoverState);
                pw.print(" mHdmiPlugged="); pw.println(mHdmiPlugged);
        if (mLastSystemUiFlags != 0 || mResettingSystemUiFlags != 0
                || mForceClearedSystemUiFlags != 0) {
            pw.print(prefix); pw.print("mLastSystemUiFlags=0x");
                    pw.print(Integer.toHexString(mLastSystemUiFlags));
                    pw.print(" mResettingSystemUiFlags=0x");
                    pw.print(Integer.toHexString(mResettingSystemUiFlags));
                    pw.print(" mForceClearedSystemUiFlags=0x");
                    pw.println(Integer.toHexString(mForceClearedSystemUiFlags));
        }
        if (mLastFocusNeedsMenu) {
            pw.print(prefix); pw.print("mLastFocusNeedsMenu=");
                    pw.println(mLastFocusNeedsMenu);
        }
        pw.print(prefix); pw.print("mWakeGestureEnabledSetting=");
                pw.println(mWakeGestureEnabledSetting);

        pw.print(prefix); pw.print("mSupportAutoRotation="); pw.println(mSupportAutoRotation);
        pw.print(prefix); pw.print("mUiMode="); pw.print(mUiMode);
                pw.print(" mDockMode="); pw.print(mDockMode);
                pw.print(" mEnableCarDockHomeCapture="); pw.print(mEnableCarDockHomeCapture);
                pw.print(" mCarDockRotation="); pw.print(mCarDockRotation);
                pw.print(" mDeskDockRotation="); pw.println(mDeskDockRotation);
        pw.print(prefix); pw.print("mUserRotationMode="); pw.print(mUserRotationMode);
                pw.print(" mUserRotation="); pw.print(mUserRotation);
                pw.print(" mAllowAllRotations="); pw.println(mAllowAllRotations);
        pw.print(prefix); pw.print("mCurrentAppOrientation="); pw.println(mCurrentAppOrientation);
        pw.print(prefix); pw.print("mCarDockEnablesAccelerometer=");
                pw.print(mCarDockEnablesAccelerometer);
                pw.print(" mDeskDockEnablesAccelerometer=");
                pw.println(mDeskDockEnablesAccelerometer);
        pw.print(prefix); pw.print("mLidKeyboardAccessibility=");
                pw.print(mLidKeyboardAccessibility);
                pw.print(" mLidNavigationAccessibility="); pw.print(mLidNavigationAccessibility);
                pw.print(" mLidControlsScreenLock="); pw.println(mLidControlsScreenLock);
                pw.print(" mLidControlsSleep="); pw.println(mLidControlsSleep);
        pw.print(prefix);
                pw.print(" mLongPressOnBackBehavior="); pw.println(mLongPressOnBackBehavior);
        pw.print(prefix);
                pw.print("mShortPressOnPowerBehavior="); pw.print(mShortPressOnPowerBehavior);
                pw.print(" mLongPressOnPowerBehavior="); pw.println(mLongPressOnPowerBehavior);
        pw.print(prefix);
                pw.print("mDoublePressOnPowerBehavior="); pw.print(mDoublePressOnPowerBehavior);
                pw.print(" mTriplePressOnPowerBehavior="); pw.println(mTriplePressOnPowerBehavior);
        pw.print(prefix); pw.print("mHasSoftInput="); pw.println(mHasSoftInput);
        pw.print(prefix); pw.print("mAwake="); pw.println(mAwake);
        pw.print(prefix); pw.print("mScreenOnEarly="); pw.print(mScreenOnEarly);
                pw.print(" mScreenOnFully="); pw.println(mScreenOnFully);
        pw.print(prefix); pw.print("mKeyguardDrawComplete="); pw.print(mKeyguardDrawComplete);
                pw.print(" mWindowManagerDrawComplete="); pw.println(mWindowManagerDrawComplete);
        pw.print(prefix); pw.print("mOrientationSensorEnabled=");
                pw.println(mOrientationSensorEnabled);
        pw.print(prefix); pw.print("mOverscanScreen=("); pw.print(mOverscanScreenLeft);
                pw.print(","); pw.print(mOverscanScreenTop);
                pw.print(") "); pw.print(mOverscanScreenWidth);
                pw.print("x"); pw.println(mOverscanScreenHeight);
        if (mOverscanLeft != 0 || mOverscanTop != 0
                || mOverscanRight != 0 || mOverscanBottom != 0) {
            pw.print(prefix); pw.print("mOverscan left="); pw.print(mOverscanLeft);
                    pw.print(" top="); pw.print(mOverscanTop);
                    pw.print(" right="); pw.print(mOverscanRight);
                    pw.print(" bottom="); pw.println(mOverscanBottom);
        }
        pw.print(prefix); pw.print("mRestrictedOverscanScreen=(");
                pw.print(mRestrictedOverscanScreenLeft);
                pw.print(","); pw.print(mRestrictedOverscanScreenTop);
                pw.print(") "); pw.print(mRestrictedOverscanScreenWidth);
                pw.print("x"); pw.println(mRestrictedOverscanScreenHeight);
        pw.print(prefix); pw.print("mUnrestrictedScreen=("); pw.print(mUnrestrictedScreenLeft);
                pw.print(","); pw.print(mUnrestrictedScreenTop);
                pw.print(") "); pw.print(mUnrestrictedScreenWidth);
                pw.print("x"); pw.println(mUnrestrictedScreenHeight);
        pw.print(prefix); pw.print("mRestrictedScreen=("); pw.print(mRestrictedScreenLeft);
                pw.print(","); pw.print(mRestrictedScreenTop);
                pw.print(") "); pw.print(mRestrictedScreenWidth);
                pw.print("x"); pw.println(mRestrictedScreenHeight);
        pw.print(prefix); pw.print("mStableFullscreen=("); pw.print(mStableFullscreenLeft);
                pw.print(","); pw.print(mStableFullscreenTop);
                pw.print(")-("); pw.print(mStableFullscreenRight);
                pw.print(","); pw.print(mStableFullscreenBottom); pw.println(")");
        pw.print(prefix); pw.print("mStable=("); pw.print(mStableLeft);
                pw.print(","); pw.print(mStableTop);
                pw.print(")-("); pw.print(mStableRight);
                pw.print(","); pw.print(mStableBottom); pw.println(")");
        pw.print(prefix); pw.print("mSystem=("); pw.print(mSystemLeft);
                pw.print(","); pw.print(mSystemTop);
                pw.print(")-("); pw.print(mSystemRight);
                pw.print(","); pw.print(mSystemBottom); pw.println(")");
        pw.print(prefix); pw.print("mCur=("); pw.print(mCurLeft);
                pw.print(","); pw.print(mCurTop);
                pw.print(")-("); pw.print(mCurRight);
                pw.print(","); pw.print(mCurBottom); pw.println(")");
        pw.print(prefix); pw.print("mContent=("); pw.print(mContentLeft);
                pw.print(","); pw.print(mContentTop);
                pw.print(")-("); pw.print(mContentRight);
                pw.print(","); pw.print(mContentBottom); pw.println(")");
        pw.print(prefix); pw.print("mVoiceContent=("); pw.print(mVoiceContentLeft);
                pw.print(","); pw.print(mVoiceContentTop);
                pw.print(")-("); pw.print(mVoiceContentRight);
                pw.print(","); pw.print(mVoiceContentBottom); pw.println(")");
        pw.print(prefix); pw.print("mDock=("); pw.print(mDockLeft);
                pw.print(","); pw.print(mDockTop);
                pw.print(")-("); pw.print(mDockRight);
                pw.print(","); pw.print(mDockBottom); pw.println(")");
        pw.print(prefix); pw.print("mDockLayer="); pw.print(mDockLayer);
                pw.print(" mStatusBarLayer="); pw.println(mStatusBarLayer);
        pw.print(prefix); pw.print("mShowingDream="); pw.print(mShowingDream);
                pw.print(" mDreamingLockscreen="); pw.print(mDreamingLockscreen);
                pw.print(" mDreamingSleepToken="); pw.println(mDreamingSleepToken);
        if (mLastInputMethodWindow != null) {
            pw.print(prefix); pw.print("mLastInputMethodWindow=");
                    pw.println(mLastInputMethodWindow);
        }
        if (mLastInputMethodTargetWindow != null) {
            pw.print(prefix); pw.print("mLastInputMethodTargetWindow=");
                    pw.println(mLastInputMethodTargetWindow);
        }
        pw.print(prefix); pw.print("mDismissImeOnBackKeyPressed=");
                pw.println(mDismissImeOnBackKeyPressed);
        if (mStatusBar != null) {
            pw.print(prefix); pw.print("mStatusBar=");
                    pw.print(mStatusBar); pw.print(" isStatusBarKeyguard=");
                    pw.println(isStatusBarKeyguard());
        }
        if (mNavigationBar != null) {
            pw.print(prefix); pw.print("mNavigationBar=");
                    pw.println(mNavigationBar);
        }
        if (mFocusedWindow != null) {
            pw.print(prefix); pw.print("mFocusedWindow=");
                    pw.println(mFocusedWindow);
        }
        if (mFocusedApp != null) {
            pw.print(prefix); pw.print("mFocusedApp=");
                    pw.println(mFocusedApp);
        }
        if (mTopFullscreenOpaqueWindowState != null) {
            pw.print(prefix); pw.print("mTopFullscreenOpaqueWindowState=");
                    pw.println(mTopFullscreenOpaqueWindowState);
        }
        if (mTopFullscreenOpaqueOrDimmingWindowState != null) {
            pw.print(prefix); pw.print("mTopFullscreenOpaqueOrDimmingWindowState=");
                    pw.println(mTopFullscreenOpaqueOrDimmingWindowState);
        }
        if (mForcingShowNavBar) {
            pw.print(prefix); pw.print("mForcingShowNavBar=");
                    pw.println(mForcingShowNavBar); pw.print( "mForcingShowNavBarLayer=");
                    pw.println(mForcingShowNavBarLayer);
        }
        pw.print(prefix); pw.print("mTopIsFullscreen="); pw.print(mTopIsFullscreen);
                pw.print(" mKeyguardOccluded="); pw.println(mKeyguardOccluded);
                pw.print(" mKeyguardOccludedChanged="); pw.println(mKeyguardOccludedChanged);
                pw.print(" mPendingKeyguardOccluded="); pw.println(mPendingKeyguardOccluded);
        pw.print(prefix); pw.print("mForceStatusBar="); pw.print(mForceStatusBar);
                pw.print(" mForceStatusBarFromKeyguard=");
                pw.println(mForceStatusBarFromKeyguard);
        pw.print(prefix); pw.print("mHomePressed="); pw.println(mHomePressed);
        pw.print(prefix); pw.print("mAllowLockscreenWhenOn="); pw.print(mAllowLockscreenWhenOn);
                pw.print(" mLockScreenTimeout="); pw.print(mLockScreenTimeout);
                pw.print(" mLockScreenTimerActive="); pw.println(mLockScreenTimerActive);
        pw.print(prefix); pw.print("mEndcallBehavior="); pw.print(mEndcallBehavior);
                pw.print(" mIncallPowerBehavior="); pw.print(mIncallPowerBehavior);
                pw.print(" mIncallBackBehavior="); pw.print(mIncallBackBehavior);
        pw.print(prefix); pw.print("mLandscapeRotation="); pw.print(mLandscapeRotation);
                pw.print(" mSeascapeRotation="); pw.println(mSeascapeRotation);
        pw.print(prefix); pw.print("mPortraitRotation="); pw.print(mPortraitRotation);
                pw.print(" mUpsideDownRotation="); pw.println(mUpsideDownRotation);
        pw.print(prefix); pw.print("mDemoHdmiRotation="); pw.print(mDemoHdmiRotation);
                pw.print(" mDemoHdmiRotationLock="); pw.println(mDemoHdmiRotationLock);
        pw.print(prefix); pw.print("mUndockedHdmiRotation="); pw.println(mUndockedHdmiRotation);
        if (mHasFeatureLeanback) {
            pw.print(prefix);
            pw.print("mAccessibilityTvKey1Pressed="); pw.println(mAccessibilityTvKey1Pressed);
            pw.print(prefix);
            pw.print("mAccessibilityTvKey2Pressed="); pw.println(mAccessibilityTvKey2Pressed);
            pw.print(prefix);
            pw.print("mAccessibilityTvScheduled="); pw.println(mAccessibilityTvScheduled);
        }

        mGlobalKeyManager.dump(prefix, pw);
        mStatusBarController.dump(pw, prefix);
        mNavigationBarController.dump(pw, prefix);
        WindowManagerPolicyControl.dump(prefix, pw);

        if (mWakeGestureListener != null) {
            mWakeGestureListener.dump(pw, prefix);
        }
        if (mOrientationListener != null) {
            mOrientationListener.dump(pw, prefix);
        }
        if (mBurnInProtectionHelper != null) {
            mBurnInProtectionHelper.dump(prefix, pw);
        }
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.dump(prefix, pw);
        }
    }

    private void cancelTorchOff() {
        if (mTorchOffPendingIntent != null) {
            mAlarmManager.cancel(mTorchOffPendingIntent);
            mTorchOffPendingIntent = null;
        }
    }

    private void toggleTorch() {
        cancelTorchOff();
        final boolean origEnabled = mTorchEnabled;
        try {
            final String rearFlashCameraId = getRearFlashCameraId();
            if (rearFlashCameraId != null) {
                mCameraManager.setTorchMode(rearFlashCameraId, !mTorchEnabled);
                mTorchEnabled = !mTorchEnabled;
            }
        } catch (CameraAccessException e) {
            // Ignore
        }
        // Setup torch off alarm
        if (mTorchEnabled && !origEnabled && mTorchTimeout > 0) {
            Intent torchOff = new Intent(ACTION_TORCH_OFF);
            torchOff.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                    | Intent.FLAG_RECEIVER_FOREGROUND);
            mTorchOffPendingIntent = PendingIntent.getBroadcast(mContext, 0, torchOff, 0);
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + mTorchTimeout * 1000, mTorchOffPendingIntent);
        }
    }

    private String getRearFlashCameraId() throws CameraAccessException {
        if (mRearFlashCameraId != null) return mRearFlashCameraId;
        for (final String id : mCameraManager.getCameraIdList()) {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(id);
            boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            int lensDirection = c.get(CameraCharacteristics.LENS_FACING);
            if (flashAvailable && lensDirection == CameraCharacteristics.LENS_FACING_BACK) {
                mRearFlashCameraId = id;
            }
        }
        return mRearFlashCameraId;
    }

    private class TorchModeCallback extends CameraManager.TorchCallback {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearFlashCameraId)) return;
            mTorchEnabled = enabled;
            if (!mTorchEnabled) {
                cancelTorchOff();
            }
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearFlashCameraId)) return;
            mTorchEnabled = false;
            cancelTorchOff();
        }
    }
<<<<<<< HEAD

    private void checkSettings() {
        mScreenshotDelay = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_DELAY, 100);
    }
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
}
