/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server.wm;

import static android.Manifest.permission.MANAGE_APP_TOKENS;
import static android.Manifest.permission.READ_FRAME_BUFFER;
import static android.Manifest.permission.REGISTER_WINDOW_MANAGER_LISTENERS;
import static android.Manifest.permission.RESTRICTED_VR_ACCESS;
import static android.app.ActivityManager.DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT;
import static android.app.AppOpsManager.OP_SYSTEM_ALERT_WINDOW;
import static android.app.StatusBarManager.DISABLE_MASK;
import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED;
import static android.content.Intent.ACTION_USER_REMOVED;
import static android.content.Intent.EXTRA_USER_HANDLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Process.ROOT_UID;
import static android.os.Process.SHELL_UID;
import static android.os.Process.SYSTEM_UID;
import static android.os.Process.myPid;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.os.UserHandle.USER_NULL;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.DOCKED_INVALID;
import static android.view.WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.FIRST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
import static android.view.WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.LAST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_COMPATIBLE_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.LayoutParams.TYPE_DRAG;
import static android.view.WindowManager.LayoutParams.TYPE_DREAM;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION;
import static android.view.WindowManager.LayoutParams.TYPE_QS_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SLIM_RECENTS;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static android.view.WindowManagerGlobal.RELAYOUT_DEFER_SURFACE_DESTROY;
import static android.view.WindowManagerGlobal.RELAYOUT_RES_SURFACE_CHANGED;
import static android.view.WindowManagerPolicy.FINISH_LAYOUT_REDO_LAYOUT;
import static android.view.WindowManagerPolicy.FINISH_LAYOUT_REDO_WALLPAPER;
import static com.android.server.LockGuard.INDEX_WINDOW;
import static com.android.server.LockGuard.installLock;
import static com.android.server.wm.AppTransition.TRANSIT_UNSET;
import static com.android.server.wm.AppWindowAnimator.PROLONG_ANIMATION_AT_END;
import static com.android.server.wm.AppWindowAnimator.PROLONG_ANIMATION_AT_START;
import static com.android.server.wm.KeyguardDisableHandler.KEYGUARD_POLICY_CHANGED;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_ADD_REMOVE;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_ANIM;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_APP_TRANSITIONS;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_BOOT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_CONFIGURATION;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_DISPLAY;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_DRAG;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_FOCUS;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_FOCUS_LIGHT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_INPUT_METHOD;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_KEEP_SCREEN_ON;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_LAYOUT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_ORIENTATION;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_SCREENSHOT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_SCREEN_ON;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_STARTING_WINDOW;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_TASK_POSITIONING;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_VISIBILITY;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_WALLPAPER_LIGHT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_WINDOW_MOVEMENT;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_WINDOW_TRACE;
import static com.android.server.wm.WindowManagerDebugConfig.SHOW_LIGHT_TRANSACTIONS;
import static com.android.server.wm.WindowManagerDebugConfig.SHOW_STACK_CRAWLS;
import static com.android.server.wm.WindowManagerDebugConfig.SHOW_TRANSACTIONS;
import static com.android.server.wm.WindowManagerDebugConfig.SHOW_VERBOSE_TRANSACTIONS;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_KEEP_SCREEN_ON;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.Manifest;
import android.Manifest.permission;
import android.animation.AnimationHandler;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.TaskSnapshot;
import android.app.ActivityManagerInternal;
import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.GraphicBuffer;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.hardware.configstore.V1_0.ISurfaceFlingerConfigs;
import android.hardware.configstore.V1_0.OptionalBool;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.SystemService;
import android.os.Trace;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.ArraySet;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.MergedConfiguration;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TimeUtils;
import android.util.TypedValue;
import android.view.AppTransitionAnimationSpec;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Gravity;
import android.view.IAppTransitionAnimationSpecsFuture;
import android.view.IDockedStackListener;
import android.view.IInputFilter;
import android.view.IOnKeyguardExitResult;
import android.view.IPinnedStackListener;
import android.view.IRotationWatcher;
import android.view.IWallpaperVisibilityListener;
import android.view.IWindow;
import android.view.IWindowId;
import android.view.IWindowManager;
import android.view.IWindowSession;
import android.view.IWindowSessionCallback;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyEvent;
import android.view.MagnificationSpec;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.Surface.OutOfResourcesException;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.view.View;
import android.view.WindowContentFrameStats;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerInternal;
import android.view.WindowManagerPolicy;
import android.view.WindowManagerPolicy.PointerEventListener;
import android.view.WindowManagerPolicy.ScreenOffListener;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManagerInternal;

import com.android.internal.R;
import com.android.internal.app.IAssistScreenshotReceiver;
import com.android.internal.graphics.SfVsyncFrameCallbackProvider;
import com.android.internal.os.IResultReceiver;
import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IShortcutService;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FastPrintWriter;
import com.android.internal.view.IInputContext;
import com.android.internal.view.IInputMethodClient;
import com.android.internal.view.IInputMethodManager;
import com.android.internal.view.WindowManagerPolicyThread;
import com.android.server.AnimationThread;
import com.android.server.DisplayThread;
import com.android.server.EventLogTags;
import com.android.server.FgThread;
import com.android.server.LocalServices;
import com.android.server.UiThread;
import com.android.server.Watchdog;
import com.android.server.input.InputManagerService;
import com.android.server.power.BatterySaverPolicy.ServiceType;
import com.android.server.power.ShutdownThread;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
/** {@hide} */
public class WindowManagerService extends IWindowManager.Stub
        implements Watchdog.Monitor, WindowManagerPolicy.WindowManagerFuncs {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "WindowManagerService" : TAG_WM;

    static final int LAYOUT_REPEAT_THRESHOLD = 4;

    static final boolean PROFILE_ORIENTATION = false;
    static final boolean localLOGV = DEBUG;

    /** How much to multiply the policy's type layer, to reserve room
     * for multiple windows of the same type and Z-ordering adjustment
     * with TYPE_LAYER_OFFSET. */
    static final int TYPE_LAYER_MULTIPLIER = 10000;

    /** Offset from TYPE_LAYER_MULTIPLIER for moving a group of windows above
     * or below others in the same layer. */
    static final int TYPE_LAYER_OFFSET = 1000;

    /** How much to increment the layer for each window, to reserve room
     * for effect surfaces between them.
     */
    static final int WINDOW_LAYER_MULTIPLIER = 5;

    /**
     * Dim surface layer is immediately below target window.
     */
    static final int LAYER_OFFSET_DIM = 1;

    /**
     * Animation thumbnail is as far as possible below the window above
     * the thumbnail (or in other words as far as possible above the window
     * below it).
     */
    static final int LAYER_OFFSET_THUMBNAIL = WINDOW_LAYER_MULTIPLIER - 1;

    /** The maximum length we will accept for a loaded animation duration:
     * this is 10 seconds.
     */
    static final int MAX_ANIMATION_DURATION = 10 * 1000;

    /** Amount of time (in milliseconds) to delay before declaring a window freeze timeout. */
    static final int WINDOW_FREEZE_TIMEOUT_DURATION = 2000;

    /** Amount of time (in milliseconds) to delay before declaring a seamless rotation timeout. */
    static final int SEAMLESS_ROTATION_TIMEOUT_DURATION = 2000;

    /** Amount of time (in milliseconds) to delay before declaring a window replacement timeout. */
    static final int WINDOW_REPLACEMENT_TIMEOUT_DURATION = 2000;

    /** Amount of time to allow a last ANR message to exist before freeing the memory. */
    static final int LAST_ANR_LIFETIME_DURATION_MSECS = 2 * 60 * 60 * 1000; // Two hours
    /**
     * If true, the window manager will do its own custom freezing and general
     * management of the screen during rotation.
     */
    static final boolean CUSTOM_SCREEN_ROTATION = true;

    // Maximum number of milliseconds to wait for input devices to be enumerated before
    // proceding with safe mode detection.
    private static final int INPUT_DEVICES_READY_FOR_SAFE_MODE_DETECTION_TIMEOUT_MILLIS = 1000;

    // Default input dispatching timeout in nanoseconds.
    static final long DEFAULT_INPUT_DISPATCHING_TIMEOUT_NANOS = 5000 * 1000000L;

    // Poll interval in milliseconds for watching boot animation finished.
    private static final int BOOT_ANIMATION_POLL_INTERVAL = 200;

    // The name of the boot animation service in init.rc.
    private static final String BOOT_ANIMATION_SERVICE = "bootanim";

    static final int UPDATE_FOCUS_NORMAL = 0;
    static final int UPDATE_FOCUS_WILL_ASSIGN_LAYERS = 1;
    static final int UPDATE_FOCUS_PLACING_SURFACES = 2;
    static final int UPDATE_FOCUS_WILL_PLACE_SURFACES = 3;

    private static final String SYSTEM_SECURE = "ro.secure";
    private static final String SYSTEM_DEBUGGABLE = "ro.debuggable";

    private static final String DENSITY_OVERRIDE = "ro.config.density_override";
    private static final String SIZE_OVERRIDE = "ro.config.size_override";

    private static final int MAX_SCREENSHOT_RETRIES = 3;

    private static final String PROPERTY_EMULATOR_CIRCULAR = "ro.emulator.circular";

    // Used to indicate that if there is already a transition set, it should be preserved when
    // trying to apply a new one.
    private static final boolean ALWAYS_KEEP_CURRENT = true;

    private static final float DRAG_SHADOW_ALPHA_TRANSPARENT = .7071f;

    // Enums for animation scale update types.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WINDOW_ANIMATION_SCALE, TRANSITION_ANIMATION_SCALE, ANIMATION_DURATION_SCALE})
    private @interface UpdateAnimationScaleMode {};
    private static final int WINDOW_ANIMATION_SCALE = 0;
    private static final int TRANSITION_ANIMATION_SCALE = 1;
    private static final int ANIMATION_DURATION_SCALE = 2;

    final private KeyguardDisableHandler mKeyguardDisableHandler;
    boolean mKeyguardGoingAway;
    // VR Vr2d Display Id.
    int mVr2dDisplayId = INVALID_DISPLAY;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED:
                    mKeyguardDisableHandler.sendEmptyMessage(KEYGUARD_POLICY_CHANGED);
                    break;
                case ACTION_USER_REMOVED:
                    final int userId = intent.getIntExtra(EXTRA_USER_HANDLE, USER_NULL);
                    if (userId != USER_NULL) {
                        synchronized (mWindowMap) {
                            mScreenCaptureDisabled.remove(userId);
                        }
                    }
                    break;
            }
        }
    };
    final WindowSurfacePlacer mWindowPlacerLocked;

    /**
     * Current user when multi-user is enabled. Don't show windows of
     * non-current user. Also see mCurrentProfileIds.
     */
    int mCurrentUserId;
    /**
     * Users that are profiles of the current user. These are also allowed to show windows
     * on the current user.
     */
    int[] mCurrentProfileIds = new int[] {};

    final Context mContext;

    final boolean mHaveInputMethods;

    final boolean mHasPermanentDpad;
    final long mDrawLockTimeoutMillis;
    final boolean mAllowAnimationsInLowPowerMode;

    final boolean mAllowBootMessages;

    final boolean mLimitedAlphaCompositing;
    final int mMaxUiWidth;

    final WindowManagerPolicy mPolicy;

    final IActivityManager mActivityManager;
    final ActivityManagerInternal mAmInternal;

    final AppOpsManager mAppOps;

    final DisplaySettings mDisplaySettings;

    /** If the system should display notifications for apps displaying an alert window. */
    boolean mShowAlertWindowNotifications = true;

    /**
     * All currently active sessions with clients.
     */
    final ArraySet<Session> mSessions = new ArraySet<>();

    /**
     * Mapping from an IWindow IBinder to the server's Window object.
     * This is also used as the lock for all of our state.
     * NOTE: Never call into methods that lock ActivityManagerService while holding this object.
     */
    final WindowHashMap mWindowMap = new WindowHashMap();

    /**
     * List of window tokens that have finished starting their application,
     * and now need to have the policy remove their windows.
     */
    final ArrayList<AppWindowToken> mFinishedStarting = new ArrayList<>();

    /**
     * List of window tokens that have finished drawing their own windows and
     * no longer need to show any saved surfaces. Windows that's still showing
     * saved surfaces will be cleaned up after next animation pass.
     */
    final ArrayList<AppWindowToken> mFinishedEarlyAnim = new ArrayList<>();

    /**
     * List of app window tokens that are waiting for replacing windows. If the
     * replacement doesn't come in time the stale windows needs to be disposed of.
     */
    final ArrayList<AppWindowToken> mWindowReplacementTimeouts = new ArrayList<>();

    /**
     * Windows that are being resized.  Used so we can tell the client about
     * the resize after closing the transaction in which we resized the
     * underlying surface.
     */
    final ArrayList<WindowState> mResizingWindows = new ArrayList<>();

    /**
     * Windows whose animations have ended and now must be removed.
     */
    final ArrayList<WindowState> mPendingRemove = new ArrayList<>();

    /**
     * Used when processing mPendingRemove to avoid working on the original array.
     */
    WindowState[] mPendingRemoveTmp = new WindowState[20];

    /**
     * Windows whose surface should be destroyed.
     */
    final ArrayList<WindowState> mDestroySurface = new ArrayList<>();

    /**
     * Windows with a preserved surface waiting to be destroyed. These windows
     * are going through a surface change. We keep the old surface around until
     * the first frame on the new surface finishes drawing.
     */
    final ArrayList<WindowState> mDestroyPreservedSurface = new ArrayList<>();

    /**
     * Windows that have lost input focus and are waiting for the new
     * focus window to be displayed before they are told about this.
     */
    ArrayList<WindowState> mLosingFocus = new ArrayList<>();

    /**
     * This is set when we have run out of memory, and will either be an empty
     * list or contain windows that need to be force removed.
     */
    final ArrayList<WindowState> mForceRemoves = new ArrayList<>();

    /**
     * Windows that clients are waiting to have drawn.
     */
    ArrayList<WindowState> mWaitingForDrawn = new ArrayList<>();
    /**
     * And the callback to make when they've all been drawn.
     */
    Runnable mWaitingForDrawnCallback;

    /** List of window currently causing non-system overlay windows to be hidden. */
    private ArrayList<WindowState> mHidingNonSystemOverlayWindows = new ArrayList<>();

    /**
     * Stores for each user whether screencapture is disabled
     * This array is essentially a cache for all userId for
     * {@link android.app.admin.DevicePolicyManager#getScreenCaptureDisabled}
     */
    private SparseArray<Boolean> mScreenCaptureDisabled = new SparseArray<>();

    IInputMethodManager mInputMethodManager;

    AccessibilityController mAccessibilityController;

    final SurfaceSession mFxSession;
    Watermark mWatermark;
    StrictModeFlash mStrictModeFlash;
    CircularDisplayMask mCircularDisplayMask;
    EmulatorDisplayOverlay mEmulatorDisplayOverlay;

    final float[] mTmpFloats = new float[9];
    final Rect mTmpRect = new Rect();
    final Rect mTmpRect2 = new Rect();
    final Rect mTmpRect3 = new Rect();
    final RectF mTmpRectF = new RectF();

    final Matrix mTmpTransform = new Matrix();

    boolean mDisplayReady;
    boolean mSafeMode;
    boolean mDisplayEnabled = false;
    boolean mSystemBooted = false;
    boolean mForceDisplayEnabled = false;
    boolean mShowingBootMessages = false;
    boolean mBootAnimationStopped = false;

    // Following variables are for debugging screen wakelock only.
    WindowState mLastWakeLockHoldingWindow = null;
    WindowState mLastWakeLockObscuringWindow = null;

    /** Dump of the windows and app tokens at the time of the last ANR. Cleared after
     * LAST_ANR_LIFETIME_DURATION_MSECS */
    String mLastANRState;

    // The root of the device window hierarchy.
    RootWindowContainer mRoot;

    int mDockedStackCreateMode = DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT;
    Rect mDockedStackCreateBounds;

    private final SparseIntArray mTmpTaskIds = new SparseIntArray();

    boolean mForceResizableTasks = false;
    boolean mSupportsPictureInPicture = false;

    int getDragLayerLocked() {
        return mPolicy.getWindowLayerFromTypeLw(TYPE_DRAG) * TYPE_LAYER_MULTIPLIER + TYPE_LAYER_OFFSET;
    }

    class RotationWatcher {
        final IRotationWatcher mWatcher;
        final IBinder.DeathRecipient mDeathRecipient;
        final int mDisplayId;
        RotationWatcher(IRotationWatcher watcher, IBinder.DeathRecipient deathRecipient,
                int displayId) {
            mWatcher = watcher;
            mDeathRecipient = deathRecipient;
            mDisplayId = displayId;
        }
    }

    ArrayList<RotationWatcher> mRotationWatchers = new ArrayList<>();
    int mDeferredRotationPauseCount;
    final WallpaperVisibilityListeners mWallpaperVisibilityListeners =
            new WallpaperVisibilityListeners();

    int mSystemDecorLayer = 0;
    final Rect mScreenRect = new Rect();

    boolean mDisplayFrozen = false;
    long mDisplayFreezeTime = 0;
    int mLastDisplayFreezeDuration = 0;
    Object mLastFinishedFreezeSource = null;
    boolean mWaitingForConfig = false;
    boolean mSwitchingUser = false;

    final static int WINDOWS_FREEZING_SCREENS_NONE = 0;
    final static int WINDOWS_FREEZING_SCREENS_ACTIVE = 1;
    final static int WINDOWS_FREEZING_SCREENS_TIMEOUT = 2;
    int mWindowsFreezingScreen = WINDOWS_FREEZING_SCREENS_NONE;

    boolean mClientFreezingScreen = false;
    int mAppsFreezingScreen = 0;

    int mLayoutSeq = 0;

    // Last systemUiVisibility we received from status bar.
    int mLastStatusBarVisibility = 0;
    // Last systemUiVisibility we dispatched to windows.
    int mLastDispatchedSystemUiVisibility = 0;

    // State while inside of layoutAndPlaceSurfacesLocked().
    boolean mFocusMayChange;

    // This is held as long as we have the screen frozen, to give us time to
    // perform a rotation animation when turning off shows the lock screen which
    // changes the orientation.
    private final PowerManager.WakeLock mScreenFrozenLock;

    final AppTransition mAppTransition;
    boolean mSkipAppTransitionAnimation = false;

    final ArraySet<AppWindowToken> mOpeningApps = new ArraySet<>();
    final ArraySet<AppWindowToken> mClosingApps = new ArraySet<>();

    final UnknownAppVisibilityController mUnknownAppVisibilityController =
            new UnknownAppVisibilityController(this);
    final TaskSnapshotController mTaskSnapshotController;

    boolean mIsTouchDevice;

    final H mH = new H();

    /**
     * Handler for things to run that have direct impact on an animation, i.e. animation tick,
     * layout, starting window creation, whereas {@link H} runs things that are still important, but
     * not as critical.
     */
    final Handler mAnimationHandler = new Handler(AnimationThread.getHandler().getLooper());

    WindowState mCurrentFocus = null;
    WindowState mLastFocus = null;

    /** Windows added since {@link #mCurrentFocus} was set to null. Used for ANR blaming. */
    private final ArrayList<WindowState> mWinAddedSinceNullFocus = new ArrayList<>();
    /** Windows removed since {@link #mCurrentFocus} was set to null. Used for ANR blaming. */
    private final ArrayList<WindowState> mWinRemovedSinceNullFocus = new ArrayList<>();

    /** This just indicates the window the input method is on top of, not
     * necessarily the window its input is going to. */
    WindowState mInputMethodTarget = null;

    /** If true hold off on modifying the animation layer of mInputMethodTarget */
    boolean mInputMethodTargetWaitingAnim;

    WindowState mInputMethodWindow = null;

    boolean mHardKeyboardAvailable;
    WindowManagerInternal.OnHardKeyboardStatusChangeListener mHardKeyboardStatusChangeListener;
    SettingsObserver mSettingsObserver;

    // A count of the windows which are 'seamlessly rotated', e.g. a surface
    // at an old orientation is being transformed. We freeze orientation updates
    // while any windows are seamlessly rotated, so we need to track when this
    // hits zero so we can apply deferred orientation updates.
    int mSeamlessRotationCount = 0;

    private final class SettingsObserver extends ContentObserver {
        private final Uri mDisplayInversionEnabledUri =
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED);
        private final Uri mWindowAnimationScaleUri =
                Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE);
        private final Uri mTransitionAnimationScaleUri =
                Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE);
        private final Uri mAnimationDurationScaleUri =
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE);

        public SettingsObserver() {
            super(new Handler());
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(mDisplayInversionEnabledUri, false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(mWindowAnimationScaleUri, false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(mTransitionAnimationScaleUri, false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(mAnimationDurationScaleUri, false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri == null) {
                return;
            }

            if (mDisplayInversionEnabledUri.equals(uri)) {
                updateCircularDisplayMaskIfNeeded();
            } else {
                @UpdateAnimationScaleMode
                final int mode;
                if (mWindowAnimationScaleUri.equals(uri)) {
                    mode = WINDOW_ANIMATION_SCALE;
                } else if (mTransitionAnimationScaleUri.equals(uri)) {
                    mode = TRANSITION_ANIMATION_SCALE;
                } else if (mAnimationDurationScaleUri.equals(uri)) {
                    mode = ANIMATION_DURATION_SCALE;
                } else {
                    // Ignoring unrecognized content changes
                    return;
                }
                Message m = mH.obtainMessage(H.UPDATE_ANIMATION_SCALE, mode, 0);
                mH.sendMessage(m);
            }
        }
    }

    boolean mAnimateWallpaperWithTarget;

    // TODO: Move to RootWindowContainer
    AppWindowToken mFocusedApp = null;

    PowerManager mPowerManager;
    PowerManagerInternal mPowerManagerInternal;

    private float mWindowAnimationScaleSetting = 1.0f;
    private float mTransitionAnimationScaleSetting = 1.0f;
    private float mAnimatorDurationScaleSetting = 1.0f;
    private boolean mAnimationsDisabled = false;

    final InputManagerService mInputManager;
    final DisplayManagerInternal mDisplayManagerInternal;
    final DisplayManager mDisplayManager;
    private final Display[] mDisplays;

    // Indicates whether this device supports wide color gamut rendering
    private boolean mHasWideColorGamutSupport;

    // Who is holding the screen on.
    private Session mHoldingScreenOn;
    private PowerManager.WakeLock mHoldingScreenWakeLock;

    boolean mTurnOnScreen;

    // Whether or not a layout can cause a wake up when theater mode is enabled.
    boolean mAllowTheaterModeWakeFromLayout;

    TaskPositioner mTaskPositioner;
    DragState mDragState = null;

    // For frozen screen animations.
    private int mExitAnimId, mEnterAnimId;

    // The display that the rotation animation is applying to.
    private int mFrozenDisplayId;

    /** Skip repeated AppWindowTokens initialization. Note that AppWindowsToken's version of this
     * is a long initialized to Long.MIN_VALUE so that it doesn't match this value on startup. */
    int mTransactionSequence;

    final WindowAnimator mAnimator;

    final BoundsAnimationController mBoundsAnimationController;

    private final PointerEventDispatcher mPointerEventDispatcher;

    private WindowContentFrameStats mTempWindowRenderStats;

    final class DragInputEventReceiver extends InputEventReceiver {
        // Set, if stylus button was down at the start of the drag.
        private boolean mStylusButtonDownAtStart;
        // Indicates the first event to check for button state.
        private boolean mIsStartEvent = true;
        // Set to true to ignore input events after the drag gesture is complete but the drag events
        // are still being dispatched.
        private boolean mMuteInput = false;

        public DragInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event, int displayId) {
            boolean handled = false;
            try {
                if (mDragState == null) {
                    // The drag has ended but the clean-up message has not been processed by
                    // window manager. Drop events that occur after this until window manager
                    // has a chance to clean-up the input handle.
                    handled = true;
                    return;
                }
                if (event instanceof MotionEvent
                        && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0
                        && !mMuteInput) {
                    final MotionEvent motionEvent = (MotionEvent)event;
                    boolean endDrag = false;
                    final float newX = motionEvent.getRawX();
                    final float newY = motionEvent.getRawY();
                    final boolean isStylusButtonDown =
                            (motionEvent.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;

                    if (mIsStartEvent) {
                        if (isStylusButtonDown) {
                            // First event and the button was down, check for the button being
                            // lifted in the future, if that happens we'll drop the item.
                            mStylusButtonDownAtStart = true;
                        }
                        mIsStartEvent = false;
                    }

                    switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        if (DEBUG_DRAG) {
                            Slog.w(TAG_WM, "Unexpected ACTION_DOWN in drag layer");
                        }
                    } break;

                    case MotionEvent.ACTION_MOVE: {
                        if (mStylusButtonDownAtStart && !isStylusButtonDown) {
                            if (DEBUG_DRAG) Slog.d(TAG_WM, "Button no longer pressed; dropping at "
                                    + newX + "," + newY);
                            mMuteInput = true;
                            synchronized (mWindowMap) {
                                endDrag = mDragState.notifyDropLw(newX, newY);
                            }
                        } else {
                            synchronized (mWindowMap) {
                                // move the surface and tell the involved window(s) where we are
                                mDragState.notifyMoveLw(newX, newY);
                            }
                        }
                    } break;

                    case MotionEvent.ACTION_UP: {
                        if (DEBUG_DRAG) Slog.d(TAG_WM, "Got UP on move channel; dropping at "
                                + newX + "," + newY);
                        mMuteInput = true;
                        synchronized (mWindowMap) {
                            endDrag = mDragState.notifyDropLw(newX, newY);
                        }
                    } break;

                    case MotionEvent.ACTION_CANCEL: {
                        if (DEBUG_DRAG) Slog.d(TAG_WM, "Drag cancelled!");
                        mMuteInput = true;
                        endDrag = true;
                    } break;
                    }

                    if (endDrag) {
                        if (DEBUG_DRAG) Slog.d(TAG_WM, "Drag ended; tearing down state");
                        // tell all the windows that the drag has ended
                        synchronized (mWindowMap) {
                            // endDragLw will post back to looper to dispose the receiver
                            // since we still need the receiver for the last finishInputEvent.
                            mDragState.endDragLw();
                        }
                        mStylusButtonDownAtStart = false;
                        mIsStartEvent = true;
                    }

                    handled = true;
                }
            } catch (Exception e) {
                Slog.e(TAG_WM, "Exception caught by drag handleMotion", e);
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }

    /**
     * Whether the UI is currently running in touch mode (not showing
     * navigational focus because the user is directly pressing the screen).
     */
    boolean mInTouchMode;

    private ViewServer mViewServer;
    final ArrayList<WindowChangeListener> mWindowChangeListeners = new ArrayList<>();
    boolean mWindowsChanged = false;

    public interface WindowChangeListener {
        public void windowsChanged();
        public void focusChanged();
    }

    final Configuration mTempConfiguration = new Configuration();

    // If true, only the core apps and services are being launched because the device
    // is in a special boot mode, such as being encrypted or waiting for a decryption password.
    // For example, when this flag is true, there will be no wallpaper service.
    final boolean mOnlyCore;

    // List of clients without a transtiton animation that we notify once we are done transitioning
    // since they won't be notified through the app window animator.
    final List<IBinder> mNoAnimationNotifyOnTransitionFinished = new ArrayList<>();

    static WindowManagerThreadPriorityBooster sThreadPriorityBooster =
            new WindowManagerThreadPriorityBooster();

    static void boostPriorityForLockedSection() {
        sThreadPriorityBooster.boost();
    }

    static void resetPriorityAfterLockedSection() {
        sThreadPriorityBooster.reset();
    }

    void openSurfaceTransaction() {
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "openSurfaceTransaction");
            synchronized (mWindowMap) {
                if (mRoot.mSurfaceTraceEnabled) {
                    mRoot.mRemoteEventTrace.openSurfaceTransaction();
                }
                SurfaceControl.openTransaction();
            }
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Closes a surface transaction.
     */
    void closeSurfaceTransaction() {
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "closeSurfaceTransaction");
            synchronized (mWindowMap) {
                if (mRoot.mSurfaceTraceEnabled) {
                    mRoot.mRemoteEventTrace.closeSurfaceTransaction();
                }
                SurfaceControl.closeTransaction();
            }
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Executes an empty animation transaction without holding the WM lock to simulate
     * back-pressure. See {@link WindowAnimator#animate} why this is needed.
     */
    void executeEmptyAnimationTransaction() {
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "openSurfaceTransaction");
            synchronized (mWindowMap) {
                if (mRoot.mSurfaceTraceEnabled) {
                    mRoot.mRemoteEventTrace.openSurfaceTransaction();
                }
                SurfaceControl.openTransaction();
                SurfaceControl.setAnimationTransaction();
                if (mRoot.mSurfaceTraceEnabled) {
                    mRoot.mRemoteEventTrace.closeSurfaceTransaction();
                }
            }
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "closeSurfaceTransaction");
            SurfaceControl.closeTransaction();
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /** Listener to notify activity manager about app transitions. */
    final WindowManagerInternal.AppTransitionListener mActivityManagerAppTransitionNotifier
            = new WindowManagerInternal.AppTransitionListener() {

        @Override
        public void onAppTransitionCancelledLocked(int transit) {
            mH.sendEmptyMessage(H.NOTIFY_APP_TRANSITION_CANCELLED);
        }

        @Override
        public void onAppTransitionFinishedLocked(IBinder token) {
            mH.sendEmptyMessage(H.NOTIFY_APP_TRANSITION_FINISHED);
            final AppWindowToken atoken = mRoot.getAppWindowToken(token);
            if (atoken == null) {
                return;
            }
            if (atoken.mLaunchTaskBehind) {
                try {
                    mActivityManager.notifyLaunchTaskBehindComplete(atoken.token);
                } catch (RemoteException e) {
                }
                atoken.mLaunchTaskBehind = false;
            } else {
                atoken.updateReportedVisibilityLocked();
                if (atoken.mEnteringAnimation) {
                    atoken.mEnteringAnimation = false;
                    try {
                        mActivityManager.notifyEnterAnimationComplete(atoken.token);
                    } catch (RemoteException e) {
                    }
                }
            }
        }
    };

    final ArrayList<AppFreezeListener> mAppFreezeListeners = new ArrayList<>();

    interface AppFreezeListener {
        void onAppFreezeTimeout();
    }

    private static WindowManagerService sInstance;
    static WindowManagerService getInstance() {
        return sInstance;
    }

    public static WindowManagerService main(final Context context, final InputManagerService im,
            final boolean haveInputMethods, final boolean showBootMsgs, final boolean onlyCore,
            WindowManagerPolicy policy) {
        DisplayThread.getHandler().runWithScissors(() ->
                sInstance = new WindowManagerService(context, im, haveInputMethods, showBootMsgs,
                        onlyCore, policy), 0);
        return sInstance;
    }

    private void initPolicy() {
        UiThread.getHandler().runWithScissors(new Runnable() {
            @Override
            public void run() {
                WindowManagerPolicyThread.set(Thread.currentThread(), Looper.myLooper());

                mPolicy.init(mContext, WindowManagerService.this, WindowManagerService.this);
            }
        }, 0);
    }

    private WindowManagerService(Context context, InputManagerService inputManager,
            boolean haveInputMethods, boolean showBootMsgs, boolean onlyCore,
            WindowManagerPolicy policy) {
        installLock(this, INDEX_WINDOW);
        mRoot = new RootWindowContainer(this);
        mContext = context;
        mHaveInputMethods = haveInputMethods;
        mAllowBootMessages = showBootMsgs;
        mOnlyCore = onlyCore;
        mLimitedAlphaCompositing = context.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_limitedAlpha);
        mHasPermanentDpad = context.getResources().getBoolean(
                com.android.internal.R.bool.config_hasPermanentDpad);
        mInTouchMode = context.getResources().getBoolean(
                com.android.internal.R.bool.config_defaultInTouchMode);
        mDrawLockTimeoutMillis = context.getResources().getInteger(
                com.android.internal.R.integer.config_drawLockTimeoutMillis);
        mAllowAnimationsInLowPowerMode = context.getResources().getBoolean(
                com.android.internal.R.bool.config_allowAnimationsInLowPowerMode);
        mMaxUiWidth = context.getResources().getInteger(
                com.android.internal.R.integer.config_maxUiWidth);
        mInputManager = inputManager; // Must be before createDisplayContentLocked.
        mDisplayManagerInternal = LocalServices.getService(DisplayManagerInternal.class);
        mDisplaySettings = new DisplaySettings();
        mDisplaySettings.readSettingsLocked();

        mWindowPlacerLocked = new WindowSurfacePlacer(this);
        mPolicy = policy;
        mTaskSnapshotController = new TaskSnapshotController(this);

        LocalServices.addService(WindowManagerPolicy.class, mPolicy);

        if(mInputManager != null) {
            final InputChannel inputChannel = mInputManager.monitorInput(TAG_WM);
            mPointerEventDispatcher = inputChannel != null
                    ? new PointerEventDispatcher(inputChannel) : null;
        } else {
            mPointerEventDispatcher = null;
        }

        mFxSession = new SurfaceSession();
        mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        mDisplays = mDisplayManager.getDisplays();
        for (Display display : mDisplays) {
            createDisplayContentLocked(display);
        }

        mKeyguardDisableHandler = new KeyguardDisableHandler(mContext, mPolicy);

        mPowerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);

        if (mPowerManagerInternal != null) {
            mPowerManagerInternal.registerLowPowerModeObserver(
                    new PowerManagerInternal.LowPowerModeListener() {
                @Override
                public int getServiceType() {
                    return ServiceType.ANIMATION;
                }

                @Override
                public void onLowPowerModeChanged(PowerSaveState result) {
                    synchronized (mWindowMap) {
                        final boolean enabled = result.batterySaverEnabled;
                        if (mAnimationsDisabled != enabled && !mAllowAnimationsInLowPowerMode) {
                            mAnimationsDisabled = enabled;
                            dispatchNewAnimatorScaleLocked(null);
                        }
                    }
                }
            });
            mAnimationsDisabled = mPowerManagerInternal
                    .getLowPowerState(ServiceType.ANIMATION).batterySaverEnabled;
        }
        mScreenFrozenLock = mPowerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "SCREEN_FROZEN");
        mScreenFrozenLock.setReferenceCounted(false);

        mAppTransition = new AppTransition(context, this);
        mAppTransition.registerListenerLocked(mActivityManagerAppTransitionNotifier);

        final AnimationHandler animationHandler = new AnimationHandler();
        animationHandler.setProvider(new SfVsyncFrameCallbackProvider());
        mBoundsAnimationController = new BoundsAnimationController(context, mAppTransition,
                AnimationThread.getHandler(), animationHandler);

        mActivityManager = ActivityManager.getService();
        mAmInternal = LocalServices.getService(ActivityManagerInternal.class);
        mAppOps = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
        AppOpsManager.OnOpChangedInternalListener opListener =
                new AppOpsManager.OnOpChangedInternalListener() {
                    @Override public void onOpChanged(int op, String packageName) {
                        updateAppOpsState();
                    }
                };
        mAppOps.startWatchingMode(OP_SYSTEM_ALERT_WINDOW, null, opListener);
        mAppOps.startWatchingMode(AppOpsManager.OP_TOAST_WINDOW, null, opListener);

        // Get persisted window scale setting
        mWindowAnimationScaleSetting = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.WINDOW_ANIMATION_SCALE, mWindowAnimationScaleSetting);
        mTransitionAnimationScaleSetting = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                context.getResources().getFloat(
                        R.dimen.config_appTransitionAnimationDurationScaleDefault));

        setAnimatorDurationScale(Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, mAnimatorDurationScaleSetting));

        IntentFilter filter = new IntentFilter();
        // Track changes to DevicePolicyManager state so we can enable/disable keyguard.
        filter.addAction(ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        // Listen to user removal broadcasts so that we can remove the user-specific data.
        filter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        mSettingsObserver = new SettingsObserver();

        mHoldingScreenWakeLock = mPowerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG_WM);
        mHoldingScreenWakeLock.setReferenceCounted(false);

        mAnimator = new WindowAnimator(this);

        mAllowTheaterModeWakeFromLayout = context.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromWindowLayout);


        LocalServices.addService(WindowManagerInternal.class, new LocalService());
        initPolicy();

        // Add ourself to the Watchdog monitors.
        Watchdog.getInstance().addMonitor(this);

        openSurfaceTransaction();
        try {
            createWatermarkInTransaction();
        } finally {
            closeSurfaceTransaction();
        }

        showEmulatorDisplayOverlayIfNeeded();
    }

    public InputMonitor getInputMonitor() {
        return mInputMonitor;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            // The window manager only throws security exceptions, so let's
            // log all others.
            if (!(e instanceof SecurityException)) {
                Slog.wtf(TAG_WM, "Window Manager Crash", e);
            }
            throw e;
        }
    }

    static boolean excludeWindowTypeFromTapOutTask(int windowType) {
        switch (windowType) {
            case TYPE_STATUS_BAR:
            case TYPE_NAVIGATION_BAR:
            case TYPE_INPUT_METHOD_DIALOG:
            case TYPE_SLIM_RECENTS:
                return true;
        }
        return false;
    }

    public int addWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int viewVisibility, int displayId,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            InputChannel outInputChannel) {
        int[] appOp = new int[1];
        int res = mPolicy.checkAddPermission(attrs, appOp);
        if (res != WindowManagerGlobal.ADD_OKAY) {
            return res;
        }

        boolean reportNewConfig = false;
        WindowState parentWindow = null;
        long origId;
        final int callingUid = Binder.getCallingUid();
        final int type = attrs.type;

        synchronized(mWindowMap) {
            if (!mDisplayReady) {
                throw new IllegalStateException("Display has not been initialialized");
            }

            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent == null) {
                Slog.w(TAG_WM, "Attempted to add window to a display that does not exist: "
                        + displayId + ".  Aborting.");
                return WindowManagerGlobal.ADD_INVALID_DISPLAY;
            }
            if (!displayContent.hasAccess(session.mUid)
                    && !mDisplayManagerInternal.isUidPresentOnDisplay(session.mUid, displayId)) {
                Slog.w(TAG_WM, "Attempted to add window to a display for which the application "
                        + "does not have access: " + displayId + ".  Aborting.");
                return WindowManagerGlobal.ADD_INVALID_DISPLAY;
            }

            if (mWindowMap.containsKey(client.asBinder())) {
                Slog.w(TAG_WM, "Window " + client + " is already added");
                return WindowManagerGlobal.ADD_DUPLICATE_ADD;
            }

            if (type >= FIRST_SUB_WINDOW && type <= LAST_SUB_WINDOW) {
                parentWindow = windowForClientLocked(null, attrs.token, false);
                if (parentWindow == null) {
                    Slog.w(TAG_WM, "Attempted to add window with token that is not a window: "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN;
                }
                if (parentWindow.mAttrs.type >= FIRST_SUB_WINDOW
                        && parentWindow.mAttrs.type <= LAST_SUB_WINDOW) {
                    Slog.w(TAG_WM, "Attempted to add window with token that is a sub-window: "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN;
                }
            }

            if (type == TYPE_PRIVATE_PRESENTATION && !displayContent.isPrivate()) {
                Slog.w(TAG_WM, "Attempted to add private presentation window to a non-private display.  Aborting.");
                return WindowManagerGlobal.ADD_PERMISSION_DENIED;
            }

            AppWindowToken atoken = null;
            final boolean hasParent = parentWindow != null;
            // Use existing parent window token for child windows since they go in the same token
            // as there parent window so we can apply the same policy on them.
            WindowToken token = displayContent.getWindowToken(
                    hasParent ? parentWindow.mAttrs.token : attrs.token);
            // If this is a child window, we want to apply the same type checking rules as the
            // parent window type.
            final int rootType = hasParent ? parentWindow.mAttrs.type : type;

            boolean addToastWindowRequiresToken = false;

            if (token == null) {
                if (rootType >= FIRST_APPLICATION_WINDOW && rootType <= LAST_APPLICATION_WINDOW) {
                    Slog.w(TAG_WM, "Attempted to add application window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_INPUT_METHOD) {
                    Slog.w(TAG_WM, "Attempted to add input method window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_VOICE_INTERACTION) {
                    Slog.w(TAG_WM, "Attempted to add voice interaction window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_WALLPAPER) {
                    Slog.w(TAG_WM, "Attempted to add wallpaper window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_DREAM) {
                    Slog.w(TAG_WM, "Attempted to add Dream window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_QS_DIALOG) {
                    Slog.w(TAG_WM, "Attempted to add QS dialog window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_ACCESSIBILITY_OVERLAY) {
                    Slog.w(TAG_WM, "Attempted to add Accessibility overlay window with unknown token "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (type == TYPE_TOAST) {
                    // Apps targeting SDK above N MR1 cannot arbitrary add toast windows.
                    if (doesAddToastWindowRequireToken(attrs.packageName, callingUid,
                            parentWindow)) {
                        Slog.w(TAG_WM, "Attempted to add a toast window with unknown token "
                                + attrs.token + ".  Aborting.");
                        return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                    }
                }
                final IBinder binder = attrs.token != null ? attrs.token : client.asBinder();
                token = new WindowToken(this, binder, type, false, displayContent,
                        session.mCanAddInternalSystemWindow);
            } else if (rootType >= FIRST_APPLICATION_WINDOW && rootType <= LAST_APPLICATION_WINDOW) {
                atoken = token.asAppWindowToken();
                if (atoken == null) {
                    Slog.w(TAG_WM, "Attempted to add window with non-application token "
                          + token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_NOT_APP_TOKEN;
                } else if (atoken.removed) {
                    Slog.w(TAG_WM, "Attempted to add window with exiting application token "
                          + token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_APP_EXITING;
                }
            } else if (rootType == TYPE_INPUT_METHOD) {
                if (token.windowType != TYPE_INPUT_METHOD) {
                    Slog.w(TAG_WM, "Attempted to add input method window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (rootType == TYPE_VOICE_INTERACTION) {
                if (token.windowType != TYPE_VOICE_INTERACTION) {
                    Slog.w(TAG_WM, "Attempted to add voice interaction window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (rootType == TYPE_WALLPAPER) {
                if (token.windowType != TYPE_WALLPAPER) {
                    Slog.w(TAG_WM, "Attempted to add wallpaper window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (rootType == TYPE_DREAM) {
                if (token.windowType != TYPE_DREAM) {
                    Slog.w(TAG_WM, "Attempted to add Dream window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (rootType == TYPE_ACCESSIBILITY_OVERLAY) {
                if (token.windowType != TYPE_ACCESSIBILITY_OVERLAY) {
                    Slog.w(TAG_WM, "Attempted to add Accessibility overlay window with bad token "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (type == TYPE_TOAST) {
                // Apps targeting SDK above N MR1 cannot arbitrary add toast windows.
                addToastWindowRequiresToken = doesAddToastWindowRequireToken(attrs.packageName,
                        callingUid, parentWindow);
                if (addToastWindowRequiresToken && token.windowType != TYPE_TOAST) {
                    Slog.w(TAG_WM, "Attempted to add a toast window with bad token "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (type == TYPE_QS_DIALOG) {
                if (token.windowType != TYPE_QS_DIALOG) {
                    Slog.w(TAG_WM, "Attempted to add QS dialog window with bad token "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (token.asAppWindowToken() != null) {
                Slog.w(TAG_WM, "Non-null appWindowToken for system window of rootType=" + rootType);
                // It is not valid to use an app token with other system types; we will
                // instead make a new token for it (as if null had been passed in for the token).
                attrs.token = null;
                token = new WindowToken(this, client.asBinder(), type, false, displayContent,
                        session.mCanAddInternalSystemWindow);
            }

            final WindowState win = new WindowState(this, session, client, token, parentWindow,
                    appOp[0], seq, attrs, viewVisibility, session.mUid,
                    session.mCanAddInternalSystemWindow);
            if (win.mDeathRecipient == null) {
                // Client has apparently died, so there is no reason to
                // continue.
                Slog.w(TAG_WM, "Adding window client " + client.asBinder()
                        + " that is dead, aborting.");
                return WindowManagerGlobal.ADD_APP_EXITING;
            }

            if (win.getDisplayContent() == null) {
                Slog.w(TAG_WM, "Adding window to Display that has been removed.");
                return WindowManagerGlobal.ADD_INVALID_DISPLAY;
            }

            mPolicy.adjustWindowParamsLw(win.mAttrs);
            win.setShowToOwnerOnlyLocked(mPolicy.checkShowToOwnerOnly(attrs));

            res = mPolicy.prepareAddWindowLw(win, attrs);
            if (res != WindowManagerGlobal.ADD_OKAY) {
                return res;
            }

            final boolean openInputChannels = (outInputChannel != null
                    && (attrs.inputFeatures & INPUT_FEATURE_NO_INPUT_CHANNEL) == 0);
            if  (openInputChannels) {
                win.openInputChannel(outInputChannel);
            }

            // If adding a toast requires a token for this app we always schedule hiding
            // toast windows to make sure they don't stick around longer then necessary.
            // We hide instead of remove such windows as apps aren't prepared to handle
            // windows being removed under them.
            //
            // If the app is older it can add toasts without a token and hence overlay
            // other apps. To be maximally compatible with these apps we will hide the
            // window after the toast timeout only if the focused window is from another
            // UID, otherwise we allow unlimited duration. When a UID looses focus we
            // schedule hiding all of its toast windows.
            if (type == TYPE_TOAST) {
                if (!getDefaultDisplayContentLocked().canAddToastWindowForUid(callingUid)) {
                    Slog.w(TAG_WM, "Adding more than one toast window for UID at a time.");
                    return WindowManagerGlobal.ADD_DUPLICATE_ADD;
                }
                // Make sure this happens before we moved focus as one can make the
                // toast focusable to force it not being hidden after the timeout.
                // Focusable toasts are always timed out to prevent a focused app to
                // show a focusable toasts while it has focus which will be kept on
                // the screen after the activity goes away.
                if (addToastWindowRequiresToken
                        || (attrs.flags & LayoutParams.FLAG_NOT_FOCUSABLE) == 0
                        || mCurrentFocus == null
                        || mCurrentFocus.mOwnerUid != callingUid) {
                    mH.sendMessageDelayed(
                            mH.obtainMessage(H.WINDOW_HIDE_TIMEOUT, win),
                            win.mAttrs.hideTimeoutMilliseconds);
                }
            }

            // From now on, no exceptions or errors allowed!

            res = WindowManagerGlobal.ADD_OKAY;
            if (mCurrentFocus == null) {
                mWinAddedSinceNullFocus.add(win);
            }

            if (excludeWindowTypeFromTapOutTask(type)) {
                displayContent.mTapExcludedWindows.add(win);
            }

            origId = Binder.clearCallingIdentity();

            win.attach();
            mWindowMap.put(client.asBinder(), win);
            if (win.mAppOp != AppOpsManager.OP_NONE) {
                int startOpResult = mAppOps.startOpNoThrow(win.mAppOp, win.getOwningUid(),
                        win.getOwningPackage());
                if ((startOpResult != AppOpsManager.MODE_ALLOWED) &&
                        (startOpResult != AppOpsManager.MODE_DEFAULT)) {
                    win.setAppOpVisibilityLw(false);
                }
            }

            final boolean hideSystemAlertWindows = !mHidingNonSystemOverlayWindows.isEmpty();
            win.setForceHideNonSystemOverlayWindowIfNeeded(hideSystemAlertWindows);

            final AppWindowToken aToken = token.asAppWindowToken();
            if (type == TYPE_APPLICATION_STARTING && aToken != null) {
                aToken.startingWindow = win;
                if (DEBUG_STARTING_WINDOW) Slog.v (TAG_WM, "addWindow: " + aToken
                        + " startingWindow=" + win);
            }

            boolean imMayMove = true;

            win.mToken.addWindow(win);
            if (type == TYPE_INPUT_METHOD) {
                win.mGivenInsetsPending = true;
                setInputMethodWindowLocked(win);
                imMayMove = false;
            } else if (type == TYPE_INPUT_METHOD_DIALOG) {
                displayContent.computeImeTarget(true /* updateImeTarget */);
                imMayMove = false;
            } else {
                if (type == TYPE_WALLPAPER) {
                    displayContent.mWallpaperController.clearLastWallpaperTimeoutTime();
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                } else if ((attrs.flags&FLAG_SHOW_WALLPAPER) != 0) {
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                } else if (displayContent.mWallpaperController.isBelowWallpaperTarget(win)) {
                    // If there is currently a wallpaper being shown, and
                    // the base layer of the new window is below the current
                    // layer of the target window, then adjust the wallpaper.
                    // This is to avoid a new window being placed between the
                    // wallpaper and its target.
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                }
            }

            // If the window is being added to a stack that's currently adjusted for IME,
            // make sure to apply the same adjust to this new window.
            win.applyAdjustForImeIfNeeded();

            if (type == TYPE_DOCK_DIVIDER) {
                mRoot.getDisplayContent(displayId).getDockedDividerController().setWindow(win);
            }

            final WindowStateAnimator winAnimator = win.mWinAnimator;
            winAnimator.mEnterAnimationPending = true;
            winAnimator.mEnteringAnimation = true;
            // Check if we need to prepare a transition for replacing window first.
            if (atoken != null && atoken.isVisible()
                    && !prepareWindowReplacementTransition(atoken)) {
                // If not, check if need to set up a dummy transition during display freeze
                // so that the unfreeze wait for the apps to draw. This might be needed if
                // the app is relaunching.
                prepareNoneTransitionForRelaunching(atoken);
            }

            if (displayContent.isDefaultDisplay) {
                final DisplayInfo displayInfo = displayContent.getDisplayInfo();
                final Rect taskBounds;
                if (atoken != null && atoken.getTask() != null) {
                    taskBounds = mTmpRect;
                    atoken.getTask().getBounds(mTmpRect);
                } else {
                    taskBounds = null;
                }
                if (mPolicy.getInsetHintLw(win.mAttrs, taskBounds, displayInfo.rotation,
                        displayInfo.logicalWidth, displayInfo.logicalHeight, outContentInsets,
                        outStableInsets, outOutsets)) {
                    res |= WindowManagerGlobal.ADD_FLAG_ALWAYS_CONSUME_NAV_BAR;
                }
            } else {
                outContentInsets.setEmpty();
                outStableInsets.setEmpty();
            }

            if (mInTouchMode) {
                res |= WindowManagerGlobal.ADD_FLAG_IN_TOUCH_MODE;
            }
            if (win.mAppToken == null || !win.mAppToken.isClientHidden()) {
                res |= WindowManagerGlobal.ADD_FLAG_APP_VISIBLE;
            }

            mInputMonitor.setUpdateInputWindowsNeededLw();

            boolean focusChanged = false;
            if (win.canReceiveKeys()) {
                focusChanged = updateFocusedWindowLocked(UPDATE_FOCUS_WILL_ASSIGN_LAYERS,
                        false /*updateInputWindows*/);
                if (focusChanged) {
                    imMayMove = false;
                }
            }

            if (imMayMove) {
                displayContent.computeImeTarget(true /* updateImeTarget */);
            }

            // Don't do layout here, the window must call
            // relayout to be displayed, so we'll do it there.
            displayContent.assignWindowLayers(false /* setLayoutNeeded */);

            if (focusChanged) {
                mInputMonitor.setInputFocusLw(mCurrentFocus, false /*updateInputWindows*/);
            }
            mInputMonitor.updateInputWindowsLw(false /*force*/);

            if (localLOGV || DEBUG_ADD_REMOVE) Slog.v(TAG_WM, "addWindow: New client "
                    + client.asBinder() + ": window=" + win + " Callers=" + Debug.getCallers(5));

            if (win.isVisibleOrAdding() && updateOrientationFromAppTokensLocked(false, displayId)) {
                reportNewConfig = true;
            }
        }

        if (reportNewConfig) {
            sendNewConfiguration(displayId);
        }

        Binder.restoreCallingIdentity(origId);

        return res;
    }

    private boolean doesAddToastWindowRequireToken(String packageName, int callingUid,
            WindowState attachedWindow) {
        // Try using the target SDK of the root window
        if (attachedWindow != null) {
            return attachedWindow.mAppToken != null
                    && attachedWindow.mAppToken.mTargetSdk >= Build.VERSION_CODES.O;
        } else {
            // Otherwise, look at the package
            try {
                ApplicationInfo appInfo = mContext.getPackageManager()
                        .getApplicationInfoAsUser(packageName, 0,
                                UserHandle.getUserId(callingUid));
                if (appInfo.uid != callingUid) {
                    throw new SecurityException("Package " + packageName + " not in UID "
                            + callingUid);
                }
                if (appInfo.targetSdkVersion >= Build.VERSION_CODES.O) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                /* ignore */
            }
        }
        return false;
    }

    /**
     * Returns true if we're done setting up any transitions.
     */
    private boolean prepareWindowReplacementTransition(AppWindowToken atoken) {
        atoken.clearAllDrawn();
        final WindowState replacedWindow = atoken.getReplacingWindow();
        if (replacedWindow == null) {
            // We expect to already receive a request to remove the old window. If it did not
            // happen, let's just simply add a window.
            return false;
        }
        // We use the visible frame, because we want the animation to morph the window from what
        // was visible to the user to the final destination of the new window.
        Rect frame = replacedWindow.mVisibleFrame;
        // We treat this as if this activity was opening, so we can trigger the app transition
        // animation and piggy-back on existing transition animation infrastructure.
        mOpeningApps.add(atoken);
        prepareAppTransition(AppTransition.TRANSIT_ACTIVITY_RELAUNCH, ALWAYS_KEEP_CURRENT);
        mAppTransition.overridePendingAppTransitionClipReveal(frame.left, frame.top,
                frame.width(), frame.height());
        executeAppTransition();
        return true;
    }

    private void prepareNoneTransitionForRelaunching(AppWindowToken atoken) {
        // Set up a none-transition and add the app to opening apps, so that the display
        // unfreeze wait for the apps to be drawn.
        // Note that if the display unfroze already because app unfreeze timed out,
        // we don't set up the transition anymore and just let it go.
        if (mDisplayFrozen && !mOpeningApps.contains(atoken) && atoken.isRelaunching()) {
            mOpeningApps.add(atoken);
            prepareAppTransition(AppTransition.TRANSIT_NONE, !ALWAYS_KEEP_CURRENT);
            executeAppTransition();
        }
    }

    /**
     * Returns whether screen capture is disabled for all windows of a specific user.
     */
    boolean isScreenCaptureDisabledLocked(int userId) {
        Boolean disabled = mScreenCaptureDisabled.get(userId);
        if (disabled == null) {
            return false;
        }
        return disabled;
    }

    boolean isSecureLocked(WindowState w) {
        if ((w.mAttrs.flags&WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            return true;
        }
        if (isScreenCaptureDisabledLocked(UserHandle.getUserId(w.mOwnerUid))) {
            return true;
        }
        return false;
    }

    @Override
    public void enableSurfaceTrace(ParcelFileDescriptor pfd) {
        final int callingUid = Binder.getCallingUid();
        if (callingUid != SHELL_UID && callingUid != ROOT_UID) {
            throw new SecurityException("Only shell can call enableSurfaceTrace");
        }

        synchronized (mWindowMap) {
            mRoot.enableSurfaceTrace(pfd);
        }
    }

    @Override
    public void disableSurfaceTrace() {
        final int callingUid = Binder.getCallingUid();
        if (callingUid != SHELL_UID && callingUid != ROOT_UID &&
            callingUid != SYSTEM_UID) {
            throw new SecurityException("Only shell can call disableSurfaceTrace");
        }
        synchronized (mWindowMap) {
            mRoot.disableSurfaceTrace();
        }
    }

    /**
     * Set mScreenCaptureDisabled for specific user
     */
    @Override
    public void setScreenCaptureDisabled(int userId, boolean disabled) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != SYSTEM_UID) {
            throw new SecurityException("Only system can call setScreenCaptureDisabled.");
        }

        synchronized(mWindowMap) {
            mScreenCaptureDisabled.put(userId, disabled);
            // Update secure surface for all windows belonging to this user.
            mRoot.setSecureSurfaceState(userId, disabled);
        }
    }

    void removeWindow(Session session, IWindow client) {
        synchronized(mWindowMap) {
            WindowState win = windowForClientLocked(session, client, false);
            if (win == null) {
                return;
            }
            win.removeIfPossible();
        }
    }

    /**
     * Performs some centralized bookkeeping clean-up on the window that is being removed.
     * NOTE: Should only be called from {@link WindowState#removeImmediately()}
     * TODO: Maybe better handled with a method {@link WindowContainer#removeChild} if we can
     * figure-out a good way to have all parents of a WindowState doing the same thing without
     * forgetting to add the wiring when a new parent of WindowState is added.
     */
    void postWindowRemoveCleanupLocked(WindowState win) {
        if (DEBUG_ADD_REMOVE) Slog.v(TAG_WM, "postWindowRemoveCleanupLocked: " + win);
        mWindowMap.remove(win.mClient.asBinder());
        if (win.mAppOp != AppOpsManager.OP_NONE) {
            mAppOps.finishOp(win.mAppOp, win.getOwningUid(), win.getOwningPackage());
        }

        if (mCurrentFocus == null) {
            mWinRemovedSinceNullFocus.add(win);
        }
        mPendingRemove.remove(win);
        mResizingWindows.remove(win);
        updateNonSystemOverlayWindowsVisibilityIfNeeded(win, false /* surfaceShown */);
        mWindowsChanged = true;
        if (DEBUG_WINDOW_MOVEMENT) Slog.v(TAG_WM, "Final remove of window: " + win);

        if (mInputMethodWindow == win) {
            setInputMethodWindowLocked(null);
        }

        final WindowToken token = win.mToken;
        final AppWindowToken atoken = win.mAppToken;
        if (DEBUG_ADD_REMOVE) Slog.v(TAG_WM, "Removing " + win + " from " + token);
        // Window will already be removed from token before this post clean-up method is called.
        if (token.isEmpty()) {
            if (!token.mPersistOnEmpty) {
                token.removeImmediately();
            } else if (atoken != null) {
                // TODO: Should this be moved into AppWindowToken.removeWindow? Might go away after
                // re-factor.
                atoken.firstWindowDrawn = false;
                atoken.clearAllDrawn();
                final TaskStack stack = atoken.getStack();
                if (stack != null) {
                    stack.mExitingAppTokens.remove(atoken);
                }
            }
        }

        if (atoken != null) {
            atoken.postWindowRemoveStartingWindowCleanup(win);
        }

        final DisplayContent dc = win.getDisplayContent();
        if (win.mAttrs.type == TYPE_WALLPAPER) {
            dc.mWallpaperController.clearLastWallpaperTimeoutTime();
            dc.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
        } else if ((win.mAttrs.flags & FLAG_SHOW_WALLPAPER) != 0) {
            dc.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
        }

        if (dc != null && !mWindowPlacerLocked.isInLayout()) {
            dc.assignWindowLayers(true /* setLayoutNeeded */);
            mWindowPlacerLocked.performSurfacePlacement();
            if (win.mAppToken != null) {
                win.mAppToken.updateReportedVisibilityLocked();
            }
        }

        mInputMonitor.updateInputWindowsLw(true /*force*/);
    }

    void setInputMethodWindowLocked(WindowState win) {
        mInputMethodWindow = win;
        final DisplayContent dc = win != null
                ? win.getDisplayContent() : getDefaultDisplayContentLocked();
        dc.computeImeTarget(true /* updateImeTarget */);
    }

    private void updateAppOpsState() {
        synchronized(mWindowMap) {
            mRoot.updateAppOpsState();
        }
    }

    static void logSurface(WindowState w, String msg, boolean withStackTrace) {
        String str = "  SURFACE " + msg + ": " + w;
        if (withStackTrace) {
            logWithStack(TAG, str);
        } else {
            Slog.i(TAG_WM, str);
        }
    }

    static void logSurface(SurfaceControl s, String title, String msg) {
        String str = "  SURFACE " + s + ": " + msg + " / " + title;
        Slog.i(TAG_WM, str);
    }

    static void logWithStack(String tag, String s) {
        RuntimeException e = null;
        if (SHOW_STACK_CRAWLS) {
            e = new RuntimeException();
            e.fillInStackTrace();
        }
        Slog.i(tag, s, e);
    }

    void setTransparentRegionWindow(Session session, IWindow client, Region region) {
        long origId = Binder.clearCallingIdentity();
        try {
            synchronized (mWindowMap) {
                WindowState w = windowForClientLocked(session, client, false);
                if (SHOW_TRANSACTIONS) WindowManagerService.logSurface(w,
                        "transparentRegionHint=" + region, false);

                if ((w != null) && w.mHasSurface) {
                    w.mWinAnimator.setTransparentRegionHintLocked(region);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    void setInsetsWindow(Session session, IWindow client, int touchableInsets, Rect contentInsets,
            Rect visibleInsets, Region touchableRegion) {
        long origId = Binder.clearCallingIdentity();
        try {
            synchronized (mWindowMap) {
                WindowState w = windowForClientLocked(session, client, false);
                if (DEBUG_LAYOUT) Slog.d(TAG, "setInsetsWindow " + w
                        + ", contentInsets=" + w.mGivenContentInsets + " -> " + contentInsets
                        + ", visibleInsets=" + w.mGivenVisibleInsets + " -> " + visibleInsets
                        + ", touchableRegion=" + w.mGivenTouchableRegion + " -> " + touchableRegion
                        + ", touchableInsets " + w.mTouchableInsets + " -> " + touchableInsets);
                if (w != null) {
                    w.mGivenInsetsPending = false;
                    w.mGivenContentInsets.set(contentInsets);
                    w.mGivenVisibleInsets.set(visibleInsets);
                    w.mGivenTouchableRegion.set(touchableRegion);
                    w.mTouchableInsets = touchableInsets;
                    if (w.mGlobalScale != 1) {
                        w.mGivenContentInsets.scale(w.mGlobalScale);
                        w.mGivenVisibleInsets.scale(w.mGlobalScale);
                        w.mGivenTouchableRegion.scale(w.mGlobalScale);
                    }
                    w.setDisplayLayoutNeeded();
                    mWindowPlacerLocked.performSurfacePlacement();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    public void getWindowDisplayFrame(Session session, IWindow client,
            Rect outDisplayFrame) {
        synchronized(mWindowMap) {
            WindowState win = windowForClientLocked(session, client, false);
            if (win == null) {
                outDisplayFrame.setEmpty();
                return;
            }
            outDisplayFrame.set(win.mDisplayFrame);
        }
    }

    public void onRectangleOnScreenRequested(IBinder token, Rect rectangle) {
        synchronized (mWindowMap) {
            if (mAccessibilityController != null) {
                WindowState window = mWindowMap.get(token);
                //TODO (multidisplay): Magnification is supported only for the default display.
                if (window != null && window.getDisplayId() == DEFAULT_DISPLAY) {
                    mAccessibilityController.onRectangleOnScreenRequestedLocked(rectangle);
                }
            }
        }
    }

    public IWindowId getWindowId(IBinder token) {
        synchronized (mWindowMap) {
            WindowState window = mWindowMap.get(token);
            return window != null ? window.mWindowId : null;
        }
    }

    public void pokeDrawLock(Session session, IBinder token) {
        synchronized (mWindowMap) {
            WindowState window = windowForClientLocked(session, token, false);
            if (window != null) {
                window.pokeDrawLockLw(mDrawLockTimeoutMillis);
            }
        }
    }

    public int relayoutWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int requestedWidth,
            int requestedHeight, int viewVisibility, int flags,
            Rect outFrame, Rect outOverscanInsets, Rect outContentInsets,
            Rect outVisibleInsets, Rect outStableInsets, Rect outOutsets, Rect outBackdropFrame,
            MergedConfiguration mergedConfiguration, Surface outSurface) {
        int result = 0;
        boolean configChanged;
        boolean hasStatusBarPermission =
                mContext.checkCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR)
                        == PackageManager.PERMISSION_GRANTED;

        long origId = Binder.clearCallingIdentity();
        final int displayId;
        synchronized(mWindowMap) {
            WindowState win = windowForClientLocked(session, client, false);
            if (win == null) {
                return 0;
            }
            displayId = win.getDisplayId();

            WindowStateAnimator winAnimator = win.mWinAnimator;
            if (viewVisibility != View.GONE) {
                win.setRequestedSize(requestedWidth, requestedHeight);
            }

            int attrChanges = 0;
            int flagChanges = 0;
            if (attrs != null) {
                mPolicy.adjustWindowParamsLw(attrs);
                // if they don't have the permission, mask out the status bar bits
                if (seq == win.mSeq) {
                    int systemUiVisibility = attrs.systemUiVisibility
                            | attrs.subtreeSystemUiVisibility;
                    if ((systemUiVisibility & DISABLE_MASK) != 0) {
                        if (!hasStatusBarPermission) {
                            systemUiVisibility &= ~DISABLE_MASK;
                        }
                    }
                    win.mSystemUiVisibility = systemUiVisibility;
                }
                if (win.mAttrs.type != attrs.type) {
                    throw new IllegalArgumentException(
                            "Window type can not be changed after the window is added.");
                }

                // Odd choice but less odd than embedding in copyFrom()
                if ((attrs.privateFlags & WindowManager.LayoutParams.PRIVATE_FLAG_PRESERVE_GEOMETRY)
                        != 0) {
                    attrs.x = win.mAttrs.x;
                    attrs.y = win.mAttrs.y;
                    attrs.width = win.mAttrs.width;
                    attrs.height = win.mAttrs.height;
                }

                flagChanges = win.mAttrs.flags ^= attrs.flags;
                attrChanges = win.mAttrs.copyFrom(attrs);
                if ((attrChanges & (WindowManager.LayoutParams.LAYOUT_CHANGED
                        | WindowManager.LayoutParams.SYSTEM_UI_VISIBILITY_CHANGED)) != 0) {
                    win.mLayoutNeeded = true;
                }
                if (win.mAppToken != null && ((flagChanges & FLAG_SHOW_WHEN_LOCKED) != 0
                        || (flagChanges & FLAG_DISMISS_KEYGUARD) != 0)) {
                    win.mAppToken.checkKeyguardFlagsChanged();
                }
                if (((attrChanges & LayoutParams.ACCESSIBILITY_TITLE_CHANGED) != 0)
                        && (mAccessibilityController != null)
                        && (win.getDisplayId() == DEFAULT_DISPLAY)) {
                    // No move or resize, but the controller checks for title changes as well
                    mAccessibilityController.onSomeWindowResizedOrMovedLocked();
                }
            }

            if (DEBUG_LAYOUT) Slog.v(TAG_WM, "Relayout " + win + ": viewVisibility=" + viewVisibility
                    + " req=" + requestedWidth + "x" + requestedHeight + " " + win.mAttrs);
            winAnimator.mSurfaceDestroyDeferred = (flags & RELAYOUT_DEFER_SURFACE_DESTROY) != 0;
            win.mEnforceSizeCompat =
                    (win.mAttrs.privateFlags & PRIVATE_FLAG_COMPATIBLE_WINDOW) != 0;
            if ((attrChanges & WindowManager.LayoutParams.ALPHA_CHANGED) != 0) {
                winAnimator.mAlpha = attrs.alpha;
            }
            win.setWindowScale(win.mRequestedWidth, win.mRequestedHeight);

            if (win.mAttrs.surfaceInsets.left != 0
                    || win.mAttrs.surfaceInsets.top != 0
                    || win.mAttrs.surfaceInsets.right != 0
                    || win.mAttrs.surfaceInsets.bottom != 0) {
                winAnimator.setOpaqueLocked(false);
            }

            boolean imMayMove = (flagChanges & (FLAG_ALT_FOCUSABLE_IM | FLAG_NOT_FOCUSABLE)) != 0;
            final boolean isDefaultDisplay = win.isDefaultDisplay();
            boolean focusMayChange = isDefaultDisplay && (win.mViewVisibility != viewVisibility
                    || ((flagChanges & FLAG_NOT_FOCUSABLE) != 0)
                    || (!win.mRelayoutCalled));

            boolean wallpaperMayMove = win.mViewVisibility != viewVisibility
                    && (win.mAttrs.flags & FLAG_SHOW_WALLPAPER) != 0;
            wallpaperMayMove |= (flagChanges & FLAG_SHOW_WALLPAPER) != 0;
            if ((flagChanges & FLAG_SECURE) != 0 && winAnimator.mSurfaceController != null) {
                winAnimator.mSurfaceController.setSecure(isSecureLocked(win));
            }

            win.mRelayoutCalled = true;
            win.mInRelayout = true;

            final int oldVisibility = win.mViewVisibility;
            win.mViewVisibility = viewVisibility;
            if (DEBUG_SCREEN_ON) {
                RuntimeException stack = new RuntimeException();
                stack.fillInStackTrace();
                Slog.i(TAG_WM, "Relayout " + win + ": oldVis=" + oldVisibility
                        + " newVis=" + viewVisibility, stack);
            }

            // We should only relayout if the view is visible, it is a starting window, or the
            // associated appToken is not hidden.
            final boolean shouldRelayout = viewVisibility == View.VISIBLE &&
                (win.mAppToken == null || win.mAttrs.type == TYPE_APPLICATION_STARTING
                    || !win.mAppToken.isClientHidden());

            if (shouldRelayout) {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "relayoutWindow: viewVisibility_1");

                // We are about to create a surface, but we didn't run a layout yet. So better run
                // a layout now that we already know the right size, as a resize call will make the
                // surface transaction blocking until next vsync and slow us down.
                // TODO: Ideally we'd create the surface after running layout a bit further down,
                // but moving this seems to be too risky at this point in the release.
                if (win.mLayoutSeq == -1) {
                    win.setDisplayLayoutNeeded();
                    mWindowPlacerLocked.performSurfacePlacement(true);
                }
                result = win.relayoutVisibleWindow(result, attrChanges, oldVisibility);

                try {
                    result = createSurfaceControl(outSurface, result, win, winAnimator);
                } catch (Exception e) {
                    mInputMonitor.updateInputWindowsLw(true /*force*/);

                    Slog.w(TAG_WM, "Exception thrown when creating surface for client "
                             + client + " (" + win.mAttrs.getTitle() + ")",
                             e);
                    Binder.restoreCallingIdentity(origId);
                    return 0;
                }
                if ((result & WindowManagerGlobal.RELAYOUT_RES_FIRST_TIME) != 0) {
                    focusMayChange = isDefaultDisplay;
                }
                if (win.mAttrs.type == TYPE_INPUT_METHOD && mInputMethodWindow == null) {
                    setInputMethodWindowLocked(win);
                    imMayMove = true;
                }
                win.adjustStartingWindowFlags();
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            } else {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "relayoutWindow: viewVisibility_2");

                winAnimator.mEnterAnimationPending = false;
                winAnimator.mEnteringAnimation = false;
                final boolean usingSavedSurfaceBeforeVisible =
                        oldVisibility != View.VISIBLE && win.isAnimatingWithSavedSurface();
                if (DEBUG_APP_TRANSITIONS || DEBUG_ANIM) {
                    if (winAnimator.hasSurface() && !win.mAnimatingExit
                            && usingSavedSurfaceBeforeVisible) {
                        Slog.d(TAG, "Ignoring layout to invisible when using saved surface " + win);
                    }
                }

                if (winAnimator.hasSurface() && !win.mAnimatingExit
                        && !usingSavedSurfaceBeforeVisible) {
                    if (DEBUG_VISIBILITY) Slog.i(TAG_WM, "Relayout invis " + win
                            + ": mAnimatingExit=" + win.mAnimatingExit);
                    // If we are not currently running the exit animation, we
                    // need to see about starting one.
                    // We don't want to animate visibility of windows which are pending
                    // replacement. In the case of activity relaunch child windows
                    // could request visibility changes as they are detached from the main
                    // application window during the tear down process. If we satisfied
                    // these visibility changes though, we would cause a visual glitch
                    // hiding the window before it's replacement was available.
                    // So we just do nothing on our side.
                    if (!win.mWillReplaceWindow) {
                        focusMayChange = tryStartExitingAnimation(
                                win, winAnimator, isDefaultDisplay, focusMayChange);
                    }
                    result |= RELAYOUT_RES_SURFACE_CHANGED;
                }
                if (viewVisibility == View.VISIBLE && winAnimator.hasSurface()) {
                    // We already told the client to go invisible, but the message may not be
                    // handled yet, or it might want to draw a last frame. If we already have a
                    // surface, let the client use that, but don't create new surface at this point.
                    Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "relayoutWindow: getSurface");
                    winAnimator.mSurfaceController.getSurface(outSurface);
                    Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                } else {
                    if (DEBUG_VISIBILITY) Slog.i(TAG_WM, "Releasing surface in: " + win);

                    try {
                        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "wmReleaseOutSurface_"
                                + win.mAttrs.getTitle());
                        outSurface.release();
                    } finally {
                        Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                    }
                }

                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            }

            if (focusMayChange) {
                //System.out.println("Focus may change: " + win.mAttrs.getTitle());
                if (updateFocusedWindowLocked(UPDATE_FOCUS_WILL_PLACE_SURFACES,
                        false /*updateInputWindows*/)) {
                    imMayMove = false;
                }
                //System.out.println("Relayout " + win + ": focus=" + mCurrentFocus);
            }

            // updateFocusedWindowLocked() already assigned layers so we only need to
            // reassign them at this point if the IM window state gets shuffled
            boolean toBeDisplayed = (result & WindowManagerGlobal.RELAYOUT_RES_FIRST_TIME) != 0;
            final DisplayContent dc = win.getDisplayContent();
            if (imMayMove) {
                dc.computeImeTarget(true /* updateImeTarget */);
                if (toBeDisplayed) {
                    // Little hack here -- we -should- be able to rely on the function to return
                    // true if the IME has moved and needs its layer recomputed. However, if the IME
                    // was hidden and isn't actually moved in the list, its layer may be out of data
                    // so we make sure to recompute it.
                    dc.assignWindowLayers(false /* setLayoutNeeded */);
                }
            }

            if (wallpaperMayMove) {
                win.getDisplayContent().pendingLayoutChanges |=
                        WindowManagerPolicy.FINISH_LAYOUT_REDO_WALLPAPER;
            }

            if (win.mAppToken != null) {
                mUnknownAppVisibilityController.notifyRelayouted(win.mAppToken);
            }

            win.setDisplayLayoutNeeded();
            win.mGivenInsetsPending = (flags & WindowManagerGlobal.RELAYOUT_INSETS_PENDING) != 0;
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER,
                    "relayoutWindow: updateOrientationFromAppTokens");
            configChanged = updateOrientationFromAppTokensLocked(false, displayId);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);

            // We may be deferring layout passes at the moment, but since the client is interested
            // in the new out values right now we need to force a layout.
            mWindowPlacerLocked.performSurfacePlacement(true /* force */);
            if (toBeDisplayed && win.mIsWallpaper) {
                DisplayInfo displayInfo = win.getDisplayContent().getDisplayInfo();
                dc.mWallpaperController.updateWallpaperOffset(
                        win, displayInfo.logicalWidth, displayInfo.logicalHeight, false);
            }
            if (win.mAppToken != null) {
                win.mAppToken.updateReportedVisibilityLocked();
            }
            if (winAnimator.mReportSurfaceResized) {
                winAnimator.mReportSurfaceResized = false;
                result |= WindowManagerGlobal.RELAYOUT_RES_SURFACE_RESIZED;
            }
            if (mPolicy.isNavBarForcedShownLw(win)) {
                result |= WindowManagerGlobal.RELAYOUT_RES_CONSUME_ALWAYS_NAV_BAR;
            }
            if (!win.isGoneForLayoutLw()) {
                win.mResizedWhileGone = false;
            }

            // We must always send the latest {@link MergedConfiguration}, regardless of whether we
            // have already reported it. The client might not have processed the previous value yet
            // and needs process it before handling the corresponding window frame. the variable
            // {@code mergedConfiguration} is an out parameter that will be passed back to the
            // client over IPC and checked there.
            // Note: in the cases where the window is tied to an activity, we should not send a
            // configuration update when the window has requested to be hidden. Doing so can lead
            // to the client erroneously accepting a configuration that would have otherwise caused
            // an activity restart. We instead hand back the last reported
            // {@link MergedConfiguration}.
            if (shouldRelayout) {
                win.getMergedConfiguration(mergedConfiguration);
            } else {
                win.getLastReportedMergedConfiguration(mergedConfiguration);
            }

            win.setLastReportedMergedConfiguration(mergedConfiguration);

            outFrame.set(win.mCompatFrame);
            outOverscanInsets.set(win.mOverscanInsets);
            outContentInsets.set(win.mContentInsets);
            win.mLastRelayoutContentInsets.set(win.mContentInsets);
            outVisibleInsets.set(win.mVisibleInsets);
            outStableInsets.set(win.mStableInsets);
            outOutsets.set(win.mOutsets);
            outBackdropFrame.set(win.getBackdropFrame(win.mFrame));
            if (localLOGV) Slog.v(
                TAG_WM, "Relayout given client " + client.asBinder()
                + ", requestedWidth=" + requestedWidth
                + ", requestedHeight=" + requestedHeight
                + ", viewVisibility=" + viewVisibility
                + "\nRelayout returning frame=" + outFrame
                + ", surface=" + outSurface);

            if (localLOGV || DEBUG_FOCUS) Slog.v(
                TAG_WM, "Relayout of " + win + ": focusMayChange=" + focusMayChange);

            result |= mInTouchMode ? WindowManagerGlobal.RELAYOUT_RES_IN_TOUCH_MODE : 0;

            mInputMonitor.updateInputWindowsLw(true /*force*/);

            if (DEBUG_LAYOUT) {
                Slog.v(TAG_WM, "Relayout complete " + win + ": outFrame=" + outFrame.toShortString());
            }
            win.mInRelayout = false;
        }

        if (configChanged) {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "relayoutWindow: sendNewConfiguration");
            sendNewConfiguration(displayId);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
        Binder.restoreCallingIdentity(origId);
        return result;
    }

    private boolean tryStartExitingAnimation(WindowState win, WindowStateAnimator winAnimator,
            boolean isDefaultDisplay, boolean focusMayChange) {
        // Try starting an animation; if there isn't one, we
        // can destroy the surface right away.
        int transit = WindowManagerPolicy.TRANSIT_EXIT;
        if (win.mAttrs.type == TYPE_APPLICATION_STARTING) {
            transit = WindowManagerPolicy.TRANSIT_PREVIEW_DONE;
        }
        if (win.isWinVisibleLw() && winAnimator.applyAnimationLocked(transit, false)) {
            focusMayChange = isDefaultDisplay;
            win.mAnimatingExit = true;
            win.mWinAnimator.mAnimating = true;
        } else if (win.mWinAnimator.isAnimationSet()) {
            // Currently in a hide animation... turn this into
            // an exit.
            win.mAnimatingExit = true;
            win.mWinAnimator.mAnimating = true;
        } else if (win.getDisplayContent().mWallpaperController.isWallpaperTarget(win)) {
            // If the wallpaper is currently behind this
            // window, we need to change both of them inside
            // of a transaction to avoid artifacts.
            win.mAnimatingExit = true;
            win.mWinAnimator.mAnimating = true;
        } else {
            if (mInputMethodWindow == win) {
                setInputMethodWindowLocked(null);
            }
            boolean stopped = win.mAppToken != null ? win.mAppToken.mAppStopped : false;
            // We set mDestroying=true so AppWindowToken#notifyAppStopped in-to destroy surfaces
            // will later actually destroy the surface if we do not do so here. Normally we leave
            // this to the exit animation.
            win.mDestroying = true;
            win.destroySurface(false, stopped);
        }
        // TODO(multidisplay): Magnification is supported only for the default display.
        if (mAccessibilityController != null && win.getDisplayId() == DEFAULT_DISPLAY) {
            mAccessibilityController.onWindowTransitionLocked(win, transit);
        }

        // When we start the exit animation we take the Surface from the client
        // so it will stop perturbing it. We need to likewise takeaway the SurfaceFlinger
        // side child surfaces, so they will remain preserved in their current state
        // (rather than be cleaned up immediately by the app code).
        SurfaceControl.openTransaction();
        winAnimator.detachChildren();
        SurfaceControl.closeTransaction();

        return focusMayChange;
    }

    private int createSurfaceControl(Surface outSurface, int result, WindowState win,
            WindowStateAnimator winAnimator) {
        if (!win.mHasSurface) {
            result |= RELAYOUT_RES_SURFACE_CHANGED;
        }

        WindowSurfaceController surfaceController;
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "createSurfaceControl");
            surfaceController = winAnimator.createSurfaceLocked(win.mAttrs.type, win.mOwnerUid);
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
        if (surfaceController != null) {
            surfaceController.getSurface(outSurface);
            if (SHOW_TRANSACTIONS) Slog.i(TAG_WM, "  OUT SURFACE " + outSurface + ": copied");
        } else {
            // For some reason there isn't a surface.  Clear the
            // caller's object so they see the same state.
            Slog.w(TAG_WM, "Failed to create surface control for " + win);
            outSurface.release();
        }

        return result;
    }

    public boolean outOfMemoryWindow(Session session, IWindow client) {
        final long origId = Binder.clearCallingIdentity();

        try {
            synchronized (mWindowMap) {
                WindowState win = windowForClientLocked(session, client, false);
                if (win == null) {
                    return false;
                }
                return mRoot.reclaimSomeSurfaceMemory(win.mWinAnimator, "from-client", false);
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    void finishDrawingWindow(Session session, IWindow client) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (mWindowMap) {
                WindowState win = windowForClientLocked(session, client, false);
                if (DEBUG_ADD_REMOVE) Slog.d(TAG_WM, "finishDrawingWindow: " + win + " mDrawState="
                        + (win != null ? win.mWinAnimator.drawStateToString() : "null"));
                if (win != null && win.mWinAnimator.finishDrawingLocked()) {
                    if ((win.mAttrs.flags & FLAG_SHOW_WALLPAPER) != 0) {
                        win.getDisplayContent().pendingLayoutChanges |=
                                WindowManagerPolicy.FINISH_LAYOUT_REDO_WALLPAPER;
                    }
                    win.setDisplayLayoutNeeded();
                    mWindowPlacerLocked.requestTraversal();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    boolean applyAnimationLocked(AppWindowToken atoken, WindowManager.LayoutParams lp,
            int transit, boolean enter, boolean isVoiceInteraction) {
        // Only apply an animation if the display isn't frozen.  If it is
        // frozen, there is no reason to animate and it can cause strange
        // artifacts when we unfreeze the display if some different animation
        // is running.
        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "WM#applyAnimationLocked");
        if (atoken.okToAnimate()) {
            final DisplayContent displayContent = atoken.getTask().getDisplayContent();
            final DisplayInfo displayInfo = displayContent.getDisplayInfo();
            final int width = displayInfo.appWidth;
            final int height = displayInfo.appHeight;
            if (DEBUG_APP_TRANSITIONS || DEBUG_ANIM) Slog.v(TAG_WM,
                    "applyAnimation: atoken=" + atoken);

            // Determine the visible rect to calculate the thumbnail clip
            final WindowState win = atoken.findMainWindow();
            final Rect frame = new Rect(0, 0, width, height);
            final Rect displayFrame = new Rect(0, 0,
                    displayInfo.logicalWidth, displayInfo.logicalHeight);
            final Rect insets = new Rect();
            final Rect stableInsets = new Rect();
            Rect surfaceInsets = null;
            final boolean freeform = win != null && win.inFreeformWorkspace();
            if (win != null) {
                // Containing frame will usually cover the whole screen, including dialog windows.
                // For freeform workspace windows it will not cover the whole screen and it also
                // won't exactly match the final freeform window frame (e.g. when overlapping with
                // the status bar). In that case we need to use the final frame.
                if (freeform) {
                    frame.set(win.mFrame);
                } else {
                    frame.set(win.mContainingFrame);
                }
                surfaceInsets = win.getAttrs().surfaceInsets;
                insets.set(win.mContentInsets);
                stableInsets.set(win.mStableInsets);
            }

            if (atoken.mLaunchTaskBehind) {
                // Differentiate the two animations. This one which is briefly on the screen
                // gets the !enter animation, and the other activity which remains on the
                // screen gets the enter animation. Both appear in the mOpeningApps set.
                enter = false;
            }
            if (DEBUG_APP_TRANSITIONS) Slog.d(TAG_WM, "Loading animation for app transition."
                    + " transit=" + AppTransition.appTransitionToString(transit) + " enter=" + enter
                    + " frame=" + frame + " insets=" + insets + " surfaceInsets=" + surfaceInsets);
            final Configuration displayConfig = displayContent.getConfiguration();
            Animation a = mAppTransition.loadAnimation(lp, transit, enter, displayConfig.uiMode,
                    displayConfig.orientation, frame, displayFrame, insets, surfaceInsets,
                    stableInsets, isVoiceInteraction, freeform, atoken.getTask().mTaskId);
            if (a != null) {
                if (DEBUG_ANIM) logWithStack(TAG, "Loaded animation " + a + " for " + atoken);
                final int containingWidth = frame.width();
                final int containingHeight = frame.height();
                atoken.mAppAnimator.setAnimation(a, containingWidth, containingHeight, width,
                        height, mAppTransition.canSkipFirstFrame(),
                        mAppTransition.getAppStackClipMode(),
                        transit, mAppTransition.getTransitFlags());
            }
        } else {
            atoken.mAppAnimator.clearAnimation();
        }
        Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);

        return atoken.mAppAnimator.animation != null;
    }

    boolean checkCallingPermission(String permission, String func) {
        // Quick check: if the calling permission is me, it's all okay.
        if (Binder.getCallingPid() == myPid()) {
            return true;
        }

        if (mContext.checkCallingPermission(permission)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        final String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid()
                + ", uid=" + Binder.getCallingUid() + " requires " + permission;
        Slog.w(TAG_WM, msg);
        return false;
    }

    @Override
    public void addWindowToken(IBinder binder, int type, int displayId) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "addWindowToken()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        synchronized(mWindowMap) {
            final DisplayContent dc = mRoot.getDisplayContentOrCreate(displayId);
            WindowToken token = dc.getWindowToken(binder);
            if (token != null) {
                Slog.w(TAG_WM, "addWindowToken: Attempted to add binder token: " + binder
                        + " for already created window token: " + token
                        + " displayId=" + displayId);
                return;
            }
            if (type == TYPE_WALLPAPER) {
                new WallpaperWindowToken(this, binder, true, dc,
                        true /* ownerCanManageAppTokens */);
            } else {
                new WindowToken(this, binder, type, true, dc, true /* ownerCanManageAppTokens */);
            }
        }
    }

    @Override
    public void removeWindowToken(IBinder binder, int displayId) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "removeWindowToken()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (mWindowMap) {
                final DisplayContent dc = mRoot.getDisplayContent(displayId);
                if (dc == null) {
                    Slog.w(TAG_WM, "removeWindowToken: Attempted to remove token: " + binder
                            + " for non-exiting displayId=" + displayId);
                    return;
                }

                final WindowToken token = dc.removeWindowToken(binder);
                if (token == null) {
                    Slog.w(TAG_WM,
                            "removeWindowToken: Attempted to remove non-existing token: " + binder);
                    return;
                }

                mInputMonitor.updateInputWindowsLw(true /*force*/);
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public Configuration updateOrientationFromAppTokens(Configuration currentConfig,
            IBinder freezeThisOneIfNeeded, int displayId) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "updateOrientationFromAppTokens()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        final Configuration config;
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                config = updateOrientationFromAppTokensLocked(currentConfig, freezeThisOneIfNeeded,
                        displayId);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return config;
    }

    private Configuration updateOrientationFromAppTokensLocked(Configuration currentConfig,
            IBinder freezeThisOneIfNeeded, int displayId) {
        if (!mDisplayReady) {
            return null;
        }
        Configuration config = null;

        if (updateOrientationFromAppTokensLocked(false, displayId)) {
            // If we changed the orientation but mOrientationChangeComplete is already true,
            // we used seamless rotation, and we don't need to freeze the screen.
            if (freezeThisOneIfNeeded != null && !mRoot.mOrientationChangeComplete) {
                final AppWindowToken atoken = mRoot.getAppWindowToken(freezeThisOneIfNeeded);
                if (atoken != null) {
                    atoken.startFreezingScreen();
                }
            }
            config = computeNewConfigurationLocked(displayId);

        } else if (currentConfig != null) {
            // No obvious action we need to take, but if our current state mismatches the activity
            // manager's, update it, disregarding font scale, which should remain set to the value
            // of the previous configuration.
            // Here we're calling Configuration#unset() instead of setToDefaults() because we need
            // to keep override configs clear of non-empty values (e.g. fontSize).
            mTempConfiguration.unset();
            mTempConfiguration.updateFrom(currentConfig);
            final DisplayContent displayContent = mRoot.getDisplayContent(displayId);
            displayContent.computeScreenConfiguration(mTempConfiguration);
            if (currentConfig.diff(mTempConfiguration) != 0) {
                mWaitingForConfig = true;
                displayContent.setLayoutNeeded();
                int anim[] = new int[2];
                if (displayContent.isDimming()) {
                    anim[0] = anim[1] = 0;
                } else {
                    mPolicy.selectRotationAnimationLw(anim);
                }
                startFreezingDisplayLocked(false, anim[0], anim[1], displayContent);
                config = new Configuration(mTempConfiguration);
            }
        }

        return config;
    }

    /**
     * Determine the new desired orientation of the display, returning a non-null new Configuration
     * if it has changed from the current orientation.  IF TRUE IS RETURNED SOMEONE MUST CALL
     * {@link #setNewDisplayOverrideConfiguration(Configuration, int)} TO TELL THE WINDOW MANAGER IT
     * CAN UNFREEZE THE SCREEN.  This will typically be done for you if you call
     * {@link #sendNewConfiguration(int)}.
     *
     * The orientation is computed from non-application windows first. If none of the
     * non-application windows specify orientation, the orientation is computed from application
     * tokens.
     * @see android.view.IWindowManager#updateOrientationFromAppTokens(Configuration, IBinder, int)
     */
    boolean updateOrientationFromAppTokensLocked(boolean inTransaction, int displayId) {
        long ident = Binder.clearCallingIdentity();
        try {
            final DisplayContent dc = mRoot.getDisplayContent(displayId);
            final int req = dc.getOrientation();
            if (req != dc.getLastOrientation()) {
                dc.setLastOrientation(req);
                //send a message to Policy indicating orientation change to take
                //action like disabling/enabling sensors etc.,
                // TODO(multi-display): Implement policy for secondary displays.
                if (dc.isDefaultDisplay) {
                    mPolicy.setCurrentOrientationLw(req);
                }
                if (dc.updateRotationUnchecked(inTransaction)) {
                    // changed
                    return true;
                }
            }

            return false;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    // If this is true we have updated our desired orientation, but not yet
    // changed the real orientation our applied our screen rotation animation.
    // For example, because a previous screen rotation was in progress.
    boolean rotationNeedsUpdateLocked() {
        // TODO(multi-display): Check for updates on all displays. Need to have per-display policy
        // to implement WindowManagerPolicy#rotationForOrientationLw() correctly.
        final DisplayContent defaultDisplayContent = getDefaultDisplayContentLocked();
        final int lastOrientation = defaultDisplayContent.getLastOrientation();
        final int oldRotation = defaultDisplayContent.getRotation();
        final boolean oldAltOrientation = defaultDisplayContent.getAltOrientation();

        final int rotation = mPolicy.rotationForOrientationLw(lastOrientation,
                oldRotation);
        boolean altOrientation = !mPolicy.rotationHasCompatibleMetricsLw(
                lastOrientation, rotation);
        if (oldRotation == rotation && oldAltOrientation == altOrientation) {
            return false;
        }
        return true;
    }

    @Override
    public int[] setNewDisplayOverrideConfiguration(Configuration overrideConfig, int displayId) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "setNewDisplayOverrideConfiguration()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        synchronized(mWindowMap) {
            if (mWaitingForConfig) {
                mWaitingForConfig = false;
                mLastFinishedFreezeSource = "new-config";
            }
            return mRoot.setDisplayOverrideConfigurationIfNeeded(overrideConfig, displayId);
        }
    }

    void setFocusTaskRegionLocked(AppWindowToken previousFocus) {
        final Task focusedTask = mFocusedApp != null ? mFocusedApp.getTask() : null;
        final Task previousTask = previousFocus != null ? previousFocus.getTask() : null;
        final DisplayContent focusedDisplayContent =
                focusedTask != null ? focusedTask.getDisplayContent() : null;
        final DisplayContent previousDisplayContent =
                previousTask != null ? previousTask.getDisplayContent() : null;
        if (previousDisplayContent != null && previousDisplayContent != focusedDisplayContent) {
            previousDisplayContent.setTouchExcludeRegion(null);
        }
        if (focusedDisplayContent != null) {
            focusedDisplayContent.setTouchExcludeRegion(focusedTask);
        }
    }

    @Override
    public void setFocusedApp(IBinder token, boolean moveFocusNow) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "setFocusedApp()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        synchronized(mWindowMap) {
            final AppWindowToken newFocus;
            if (token == null) {
                if (DEBUG_FOCUS_LIGHT) Slog.v(TAG_WM, "Clearing focused app, was " + mFocusedApp);
                newFocus = null;
            } else {
                newFocus = mRoot.getAppWindowToken(token);
                if (newFocus == null) {
                    Slog.w(TAG_WM, "Attempted to set focus to non-existing app token: " + token);
                }
                if (DEBUG_FOCUS_LIGHT) Slog.v(TAG_WM, "Set focused app to: " + newFocus
                        + " old focus=" + mFocusedApp + " moveFocusNow=" + moveFocusNow);
            }

            final boolean changed = mFocusedApp != newFocus;
            if (changed) {
                AppWindowToken prev = mFocusedApp;
                mFocusedApp = newFocus;
                mInputMonitor.setFocusedAppLw(newFocus);
                setFocusTaskRegionLocked(prev);
            }

            if (moveFocusNow && changed) {
                final long origId = Binder.clearCallingIdentity();
                updateFocusedWindowLocked(UPDATE_FOCUS_NORMAL, true /*updateInputWindows*/);
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    @Override
    public void prepareAppTransition(int transit, boolean alwaysKeepCurrent) {
        prepareAppTransition(transit, alwaysKeepCurrent, 0 /* flags */, false /* forceOverride */);
    }

    /**
     * @param transit What kind of transition is happening. Use one of the constants
     *                AppTransition.TRANSIT_*.
     * @param alwaysKeepCurrent If true and a transition is already set, new transition will NOT
     *                          be set.
     * @param flags Additional flags for the app transition, Use a combination of the constants
     *              AppTransition.TRANSIT_FLAG_*.
     * @param forceOverride Always override the transit, not matter what was set previously.
     */
    public void prepareAppTransition(int transit, boolean alwaysKeepCurrent, int flags,
            boolean forceOverride) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "prepareAppTransition()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }
        synchronized(mWindowMap) {
            boolean prepared = mAppTransition.prepareAppTransitionLocked(transit, alwaysKeepCurrent,
                    flags, forceOverride);
            // TODO (multidisplay): associate app transitions with displays
            final DisplayContent dc = mRoot.getDisplayContent(DEFAULT_DISPLAY);
            if (prepared && dc != null && dc.okToAnimate()) {
                mSkipAppTransitionAnimation = false;
            }
        }
    }

    @Override
    public int getPendingAppTransition() {
        return mAppTransition.getAppTransition();
    }

    @Override
    public void overridePendingAppTransition(String packageName,
            int enterAnim, int exitAnim, IRemoteCallback startedCallback) {
        synchronized(mWindowMap) {
            mAppTransition.overridePendingAppTransition(packageName, enterAnim, exitAnim,
                    startedCallback);
        }
    }

    @Override
    public void overridePendingAppTransitionScaleUp(int startX, int startY, int startWidth,
            int startHeight) {
        synchronized(mWindowMap) {
            mAppTransition.overridePendingAppTransitionScaleUp(startX, startY, startWidth,
                    startHeight);
        }
    }

    @Override
    public void overridePendingAppTransitionClipReveal(int startX, int startY,
            int startWidth, int startHeight) {
        synchronized(mWindowMap) {
            mAppTransition.overridePendingAppTransitionClipReveal(startX, startY, startWidth,
                    startHeight);
        }
    }

    @Override
    public void overridePendingAppTransitionThumb(GraphicBuffer srcThumb, int startX,
            int startY, IRemoteCallback startedCallback, boolean scaleUp) {
        synchronized(mWindowMap) {
            mAppTransition.overridePendingAppTransitionThumb(srcThumb, startX, startY,
                    startedCallback, scaleUp);
        }
    }

    @Override
    public void overridePendingAppTransitionAspectScaledThumb(GraphicBuffer srcThumb, int startX,
            int startY, int targetWidth, int targetHeight, IRemoteCallback startedCallback,
            boolean scaleUp) {
        synchronized(mWindowMap) {
            mAppTransition.overridePendingAppTransitionAspectScaledThumb(srcThumb, startX, startY,
                    targetWidth, targetHeight, startedCallback, scaleUp);
        }
    }

    @Override
    public void overridePendingAppTransitionMultiThumb(AppTransitionAnimationSpec[] specs,
            IRemoteCallback onAnimationStartedCallback, IRemoteCallback onAnimationFinishedCallback,
            boolean scaleUp) {
        synchronized (mWindowMap) {
            mAppTransition.overridePendingAppTransitionMultiThumb(specs, onAnimationStartedCallback,
                    onAnimationFinishedCallback, scaleUp);
            prolongAnimationsFromSpecs(specs, scaleUp);

        }
    }

    void prolongAnimationsFromSpecs(@NonNull AppTransitionAnimationSpec[] specs, boolean scaleUp) {
        // This is used by freeform <-> recents windows transition. We need to synchronize
        // the animation with the appearance of the content of recents, so we will make
        // animation stay on the first or last frame a little longer.
        mTmpTaskIds.clear();
        for (int i = specs.length - 1; i >= 0; i--) {
            mTmpTaskIds.put(specs[i].taskId, 0);
        }
        for (final WindowState win : mWindowMap.values()) {
            final Task task = win.getTask();
            if (task != null && mTmpTaskIds.get(task.mTaskId, -1) != -1
                    && task.inFreeformWorkspace()) {
                final AppWindowToken appToken = win.mAppToken;
                if (appToken != null && appToken.mAppAnimator != null) {
                    appToken.mAppAnimator.startProlongAnimation(scaleUp ?
                            PROLONG_ANIMATION_AT_START : PROLONG_ANIMATION_AT_END);
                }
            }
        }
    }

    @Override
    public void overridePendingAppTransitionInPlace(String packageName, int anim) {
        synchronized(mWindowMap) {
            mAppTransition.overrideInPlaceAppTransition(packageName, anim);
        }
    }

    @Override
    public void overridePendingAppTransitionMultiThumbFuture(
            IAppTransitionAnimationSpecsFuture specsFuture, IRemoteCallback callback,
            boolean scaleUp) {
        synchronized(mWindowMap) {
            mAppTransition.overridePendingAppTransitionMultiThumbFuture(specsFuture, callback,
                    scaleUp);
        }
    }

    @Override
    public void endProlongedAnimations() {
        synchronized (mWindowMap) {
            for (final WindowState win : mWindowMap.values()) {
                final AppWindowToken appToken = win.mAppToken;
                if (appToken != null && appToken.mAppAnimator != null) {
                    appToken.mAppAnimator.endProlongedAnimation();
                }
            }
            mAppTransition.notifyProlongedAnimationsEnded();
        }
    }

    @Override
    public void executeAppTransition() {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "executeAppTransition()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        synchronized(mWindowMap) {
            if (DEBUG_APP_TRANSITIONS) Slog.w(TAG_WM, "Execute app transition: " + mAppTransition
                    + " Callers=" + Debug.getCallers(5));
            if (mAppTransition.isTransitionSet()) {
                mAppTransition.setReady();
                final long origId = Binder.clearCallingIdentity();
                try {
                    mWindowPlacerLocked.performSurfacePlacement();
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
    }

    public void setAppFullscreen(IBinder token, boolean toOpaque) {
        synchronized (mWindowMap) {
            final AppWindowToken atoken = mRoot.getAppWindowToken(token);
            if (atoken != null) {
                atoken.setFillsParent(toOpaque);
                setWindowOpaqueLocked(token, toOpaque);
                mWindowPlacerLocked.requestTraversal();
            }
        }
    }

    public void setWindowOpaque(IBinder token, boolean isOpaque) {
        synchronized (mWindowMap) {
            setWindowOpaqueLocked(token, isOpaque);
        }
    }

    private void setWindowOpaqueLocked(IBinder token, boolean isOpaque) {
        final AppWindowToken wtoken = mRoot.getAppWindowToken(token);
        if (wtoken != null) {
            final WindowState win = wtoken.findMainWindow();
            if (win != null) {
                win.mWinAnimator.setOpaqueLocked(isOpaque);
            }
        }
    }

    void updateTokenInPlaceLocked(AppWindowToken wtoken, int transit) {
        if (transit != TRANSIT_UNSET) {
            if (wtoken.mAppAnimator.animation == AppWindowAnimator.sDummyAnimation) {
                wtoken.mAppAnimator.setNullAnimation();
            }
            applyAnimationLocked(wtoken, null, transit, false, false);
        }
    }

    public void setDockedStackCreateState(int mode, Rect bounds) {
        synchronized (mWindowMap) {
            setDockedStackCreateStateLocked(mode, bounds);
        }
    }

    void setDockedStackCreateStateLocked(int mode, Rect bounds) {
        mDockedStackCreateMode = mode;
        mDockedStackCreateBounds = bounds;
    }

    public boolean isValidPictureInPictureAspectRatio(int displayId, float aspectRatio) {
        final DisplayContent displayContent = mRoot.getDisplayContent(displayId);
        return displayContent.getPinnedStackController().isValidPictureInPictureAspectRatio(
                aspectRatio);
    }

    @Override
    public void getStackBounds(int stackId, Rect bounds) {
        synchronized (mWindowMap) {
            final TaskStack stack = mRoot.getStackById(stackId);
            if (stack != null) {
                stack.getBounds(bounds);
                return;
            }
            bounds.setEmpty();
        }
    }

    @Override
    public void notifyShowingDreamChanged() {
        notifyKeyguardFlagsChanged(null /* callback */);
    }

    @Override
    public WindowManagerPolicy.WindowState getInputMethodWindowLw() {
        return mInputMethodWindow;
    }

    @Override
    public void notifyKeyguardTrustedChanged() {
        mH.sendEmptyMessage(H.NOTIFY_KEYGUARD_TRUSTED_CHANGED);
    }

    @Override
    public void screenTurningOff(ScreenOffListener listener) {
        mTaskSnapshotController.screenTurningOff(listener);
    }

    /**
     * Starts deferring layout passes. Useful when doing multiple changes but to optimize
     * performance, only one layout pass should be done. This can be called multiple times, and
     * layouting will be resumed once the last caller has called {@link #continueSurfaceLayout}
     */
    public void deferSurfaceLayout() {
        synchronized (mWindowMap) {
            mWindowPlacerLocked.deferLayout();
        }
    }

    /**
     * Resumes layout passes after deferring them. See {@link #deferSurfaceLayout()}
     */
    public void continueSurfaceLayout() {
        synchronized (mWindowMap) {
            mWindowPlacerLocked.continueLayout();
        }
    }

    /**
     * @return true if the activity contains windows that have
     *         {@link LayoutParams#FLAG_SHOW_WHEN_LOCKED} set
     */
    public boolean containsShowWhenLockedWindow(IBinder token) {
        synchronized (mWindowMap) {
            final AppWindowToken wtoken = mRoot.getAppWindowToken(token);
            return wtoken != null && wtoken.containsShowWhenLockedWindow();
        }
    }

    /**
     * @return true if the activity contains windows that have
     *         {@link LayoutParams#FLAG_DISMISS_KEYGUARD} set
     */
    public boolean containsDismissKeyguardWindow(IBinder token) {
        synchronized (mWindowMap) {
            final AppWindowToken wtoken = mRoot.getAppWindowToken(token);
            return wtoken != null && wtoken.containsDismissKeyguardWindow();
        }
    }

    /**
     * Notifies activity manager that some Keyguard flags have changed and that it needs to
     * reevaluate the visibilities of the activities.
     * @param callback Runnable to be called when activity manager is done reevaluating visibilities
     */
    void notifyKeyguardFlagsChanged(@Nullable Runnable callback) {
        final Runnable wrappedCallback = callback != null
                ? () -> { synchronized (mWindowMap) { callback.run(); } }
                : null;
        mH.obtainMessage(H.NOTIFY_KEYGUARD_FLAGS_CHANGED, wrappedCallback).sendToTarget();
    }

    public boolean isKeyguardTrusted() {
        synchronized (mWindowMap) {
            return mPolicy.isKeyguardTrustedLw();
        }
    }

    public void setKeyguardGoingAway(boolean keyguardGoingAway) {
        synchronized (mWindowMap) {
            mKeyguardGoingAway = keyguardGoingAway;
        }
    }

    // -------------------------------------------------------------
    // Misc IWindowSession methods
    // -------------------------------------------------------------

    @Override
    public void startFreezingScreen(int exitAnim, int enterAnim) {
        if (!checkCallingPermission(android.Manifest.permission.FREEZE_SCREEN,
                "startFreezingScreen()")) {
            throw new SecurityException("Requires FREEZE_SCREEN permission");
        }

        synchronized(mWindowMap) {
            if (!mClientFreezingScreen) {
                mClientFreezingScreen = true;
                final long origId = Binder.clearCallingIdentity();
                try {
                    startFreezingDisplayLocked(false, exitAnim, enterAnim);
                    mH.removeMessages(H.CLIENT_FREEZE_TIMEOUT);
                    mH.sendEmptyMessageDelayed(H.CLIENT_FREEZE_TIMEOUT, 5000);
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
    }

    @Override
    public void stopFreezingScreen() {
        if (!checkCallingPermission(android.Manifest.permission.FREEZE_SCREEN,
                "stopFreezingScreen()")) {
            throw new SecurityException("Requires FREEZE_SCREEN permission");
        }

        synchronized(mWindowMap) {
            if (mClientFreezingScreen) {
                mClientFreezingScreen = false;
                mLastFinishedFreezeSource = "client";
                final long origId = Binder.clearCallingIdentity();
                try {
                    stopFreezingDisplayLocked();
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
    }

    @Override
    public void disableKeyguard(IBinder token, String tag) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DISABLE_KEYGUARD)
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires DISABLE_KEYGUARD permission");
        }
        // If this isn't coming from the system then don't allow disabling the lockscreen
        // to bypass security.
        if (Binder.getCallingUid() != SYSTEM_UID && isKeyguardSecure()) {
            Log.d(TAG_WM, "current mode is SecurityMode, ignore disableKeyguard");
            return;
        }

        // If this isn't coming from the current profiles, ignore it.
        if (!isCurrentProfileLocked(UserHandle.getCallingUserId())) {
            Log.d(TAG_WM, "non-current profiles, ignore disableKeyguard");
            return;
        }

        if (token == null) {
            throw new IllegalArgumentException("token == null");
        }

        mKeyguardDisableHandler.sendMessage(mKeyguardDisableHandler.obtainMessage(
                KeyguardDisableHandler.KEYGUARD_DISABLE, new Pair<IBinder, String>(token, tag)));
    }

    @Override
    public void reenableKeyguard(IBinder token) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DISABLE_KEYGUARD)
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires DISABLE_KEYGUARD permission");
        }

        if (token == null) {
            throw new IllegalArgumentException("token == null");
        }

        mKeyguardDisableHandler.sendMessage(mKeyguardDisableHandler.obtainMessage(
                KeyguardDisableHandler.KEYGUARD_REENABLE, token));
    }

    /**
     * @see android.app.KeyguardManager#exitKeyguardSecurely
     */
    @Override
    public void exitKeyguardSecurely(final IOnKeyguardExitResult callback) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DISABLE_KEYGUARD)
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires DISABLE_KEYGUARD permission");
        }

        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }

        mPolicy.exitKeyguardSecurely(new WindowManagerPolicy.OnKeyguardExitResult() {
            @Override
            public void onKeyguardExitResult(boolean success) {
                try {
                    callback.onKeyguardExitResult(success);
                } catch (RemoteException e) {
                    // Client has died, we don't care.
                }
            }
        });
    }

    @Override
    public boolean inKeyguardRestrictedInputMode() {
        return mPolicy.inKeyguardRestrictedKeyInputMode();
    }

    @Override
    public boolean isKeyguardLocked() {
        return mPolicy.isKeyguardLocked();
    }

    public boolean isKeyguardShowingAndNotOccluded() {
        return mPolicy.isKeyguardShowingAndNotOccluded();
    }

    @Override
    public boolean isKeyguardSecure() {
        int userId = UserHandle.getCallingUserId();
        long origId = Binder.clearCallingIdentity();
        try {
            return mPolicy.isKeyguardSecure(userId);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    public boolean isShowingDream() {
        synchronized (mWindowMap) {
            return mPolicy.isShowingDreamLw();
        }
    }

    @Override
    public void dismissKeyguard(IKeyguardDismissCallback callback) {
        checkCallingPermission(permission.CONTROL_KEYGUARD, "dismissKeyguard");
        synchronized(mWindowMap) {
            mPolicy.dismissKeyguardLw(callback);
        }
    }

    public void onKeyguardOccludedChanged(boolean occluded) {
        synchronized (mWindowMap) {
            mPolicy.onKeyguardOccludedChangedLw(occluded);
        }
    }

    @Override
    public void setSwitchingUser(boolean switching) {
        if (!checkCallingPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                "setSwitchingUser()")) {
            throw new SecurityException("Requires INTERACT_ACROSS_USERS_FULL permission");
        }
        mPolicy.setSwitchingUser(switching);
        synchronized (mWindowMap) {
            mSwitchingUser = switching;
        }
    }

    void showGlobalActions() {
        mPolicy.showGlobalActions();
    }

    @Override
    public void closeSystemDialogs(String reason) {
        synchronized(mWindowMap) {
            mRoot.closeSystemDialogs(reason);
        }
    }

    static float fixScale(float scale) {
        if (scale < 0) scale = 0;
        else if (scale > 20) scale = 20;
        return Math.abs(scale);
    }

    @Override
    public void setAnimationScale(int which, float scale) {
        if (!checkCallingPermission(android.Manifest.permission.SET_ANIMATION_SCALE,
                "setAnimationScale()")) {
            throw new SecurityException("Requires SET_ANIMATION_SCALE permission");
        }

        scale = fixScale(scale);
        switch (which) {
            case 0: mWindowAnimationScaleSetting = scale; break;
            case 1: mTransitionAnimationScaleSetting = scale; break;
            case 2: mAnimatorDurationScaleSetting = scale; break;
        }

        // Persist setting
        mH.sendEmptyMessage(H.PERSIST_ANIMATION_SCALE);
    }

    @Override
    public void setAnimationScales(float[] scales) {
        if (!checkCallingPermission(android.Manifest.permission.SET_ANIMATION_SCALE,
                "setAnimationScale()")) {
            throw new SecurityException("Requires SET_ANIMATION_SCALE permission");
        }

        if (scales != null) {
            if (scales.length >= 1) {
                mWindowAnimationScaleSetting = fixScale(scales[0]);
            }
            if (scales.length >= 2) {
                mTransitionAnimationScaleSetting = fixScale(scales[1]);
            }
            if (scales.length >= 3) {
                mAnimatorDurationScaleSetting = fixScale(scales[2]);
                dispatchNewAnimatorScaleLocked(null);
            }
        }

        // Persist setting
        mH.sendEmptyMessage(H.PERSIST_ANIMATION_SCALE);
    }

    private void setAnimatorDurationScale(float scale) {
        mAnimatorDurationScaleSetting = scale;
        ValueAnimator.setDurationScale(scale);
    }

    public float getWindowAnimationScaleLocked() {
        return mAnimationsDisabled ? 0 : mWindowAnimationScaleSetting;
    }

    public float getTransitionAnimationScaleLocked() {
        return mAnimationsDisabled ? 0 : mTransitionAnimationScaleSetting;
    }

    @Override
    public float getAnimationScale(int which) {
        switch (which) {
            case 0: return mWindowAnimationScaleSetting;
            case 1: return mTransitionAnimationScaleSetting;
            case 2: return mAnimatorDurationScaleSetting;
        }
        return 0;
    }

    @Override
    public float[] getAnimationScales() {
        return new float[] { mWindowAnimationScaleSetting, mTransitionAnimationScaleSetting,
                mAnimatorDurationScaleSetting };
    }

    @Override
    public float getCurrentAnimatorScale() {
        synchronized(mWindowMap) {
            return mAnimationsDisabled ? 0 : mAnimatorDurationScaleSetting;
        }
    }

    void dispatchNewAnimatorScaleLocked(Session session) {
        mH.obtainMessage(H.NEW_ANIMATOR_SCALE, session).sendToTarget();
    }

    @Override
    public void registerPointerEventListener(PointerEventListener listener) {
        mPointerEventDispatcher.registerInputEventListener(listener);
    }

    @Override
    public void unregisterPointerEventListener(PointerEventListener listener) {
        mPointerEventDispatcher.unregisterInputEventListener(listener);
    }

    /** Check if the service is set to dispatch pointer events. */
    boolean canDispatchPointerEvents() {
        return mPointerEventDispatcher != null;
    }

    @Override
    public void addSystemUIVisibilityFlag(int flags) {
        mLastStatusBarVisibility |= flags;
    }

    // Called by window manager policy. Not exposed externally.
    @Override
    public int getLidState() {
        int sw = mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY,
                InputManagerService.SW_LID);
        if (sw > 0) {
            // Switch state: AKEY_STATE_DOWN or AKEY_STATE_VIRTUAL.
            return LID_CLOSED;
        } else if (sw == 0) {
            // Switch state: AKEY_STATE_UP.
            return LID_OPEN;
        } else {
            // Switch state: AKEY_STATE_UNKNOWN.
            return LID_ABSENT;
        }
    }

    // Called by window manager policy. Not exposed externally.
    @Override
    public void lockDeviceNow() {
        lockNow(null);
    }

    // Called by window manager policy. Not exposed externally.
    @Override
    public int getCameraLensCoverState() {
        int sw = mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY,
                InputManagerService.SW_CAMERA_LENS_COVER);
        if (sw > 0) {
            // Switch state: AKEY_STATE_DOWN or AKEY_STATE_VIRTUAL.
            return CAMERA_LENS_COVERED;
        } else if (sw == 0) {
            // Switch state: AKEY_STATE_UP.
            return CAMERA_LENS_UNCOVERED;
        } else {
            // Switch state: AKEY_STATE_UNKNOWN.
            return CAMERA_LENS_COVER_ABSENT;
        }
    }

    // Called by window manager policy.  Not exposed externally.
    @Override
    public void switchInputMethod(boolean forwardDirection) {
        final InputMethodManagerInternal inputMethodManagerInternal =
                LocalServices.getService(InputMethodManagerInternal.class);
        if (inputMethodManagerInternal != null) {
            inputMethodManagerInternal.switchInputMethod(forwardDirection);
        }
    }

    // Called by window manager policy.  Not exposed externally.
    @Override
    public void shutdown(boolean confirm) {
        // Pass in the UI context, since ShutdownThread requires it (to show UI).
        ShutdownThread.shutdown(ActivityThread.currentActivityThread().getSystemUiContext(),
                PowerManager.SHUTDOWN_USER_REQUESTED, confirm);
    }

    // Called by window manager policy.  Not exposed externally.
    @Override
    public void reboot(boolean confirm) {
        // Pass in the UI context, since ShutdownThread requires it (to show UI).
        ShutdownThread.reboot(ActivityThread.currentActivityThread().getSystemUiContext(),
                PowerManager.SHUTDOWN_USER_REQUESTED, confirm);
    }

    // Called by window manager policy.  Not exposed externally.
    @Override
    public void rebootSafeMode(boolean confirm) {
        // Pass in the UI context, since ShutdownThread requires it (to show UI).
        ShutdownThread.rebootSafeMode(ActivityThread.currentActivityThread().getSystemUiContext(),
                confirm);
    }

    // Called by window manager policy.  Not exposed externally.
    @Override
    public void reboot(boolean confirm, String reason) {
        // Pass in the UI context, since ShutdownThread requires it (to show UI).
        ShutdownThread.rebootCustom(ActivityThread.currentActivityThread().getSystemUiContext(),
                reason, confirm);
    }

    public void setCurrentProfileIds(final int[] currentProfileIds) {
        synchronized (mWindowMap) {
            mCurrentProfileIds = currentProfileIds;
        }
    }

    public void setCurrentUser(final int newUserId, final int[] currentProfileIds) {
        synchronized (mWindowMap) {
            mCurrentUserId = newUserId;
            mCurrentProfileIds = currentProfileIds;
            mAppTransition.setCurrentUser(newUserId);
            mPolicy.setCurrentUserLw(newUserId);

            // If keyguard was disabled, re-enable it
            // TODO: Keep track of keyguardEnabled state per user and use here...
            // e.g. enabled = mKeyguardDisableHandler.getEnabledStateForUser(newUserId);
            mPolicy.enableKeyguard(true);

            // Hide windows that should not be seen by the new user.
            mRoot.switchUser();
            mWindowPlacerLocked.performSurfacePlacement();

            // Notify whether the docked stack exists for the current user
            final DisplayContent displayContent = getDefaultDisplayContentLocked();
            final TaskStack stack = displayContent.getDockedStackIgnoringVisibility();
            displayContent.mDividerControllerLocked.notifyDockedStackExistsChanged(
                    stack != null && stack.hasTaskForUser(newUserId));

            // If the display is already prepared, update the density.
            // Otherwise, we'll update it when it's prepared.
            if (mDisplayReady) {
                final int forcedDensity = getForcedDisplayDensityForUserLocked(newUserId);
                final int targetDensity = forcedDensity != 0 ? forcedDensity
                        : displayContent.mInitialDisplayDensity;
                setForcedDisplayDensityLocked(displayContent, targetDensity);
            }
        }
    }

    /* Called by WindowState */
    boolean isCurrentProfileLocked(int userId) {
        if (userId == mCurrentUserId) return true;
        for (int i = 0; i < mCurrentProfileIds.length; i++) {
            if (mCurrentProfileIds[i] == userId) return true;
        }
        return false;
    }

    public void enableScreenAfterBoot() {
        synchronized(mWindowMap) {
            if (DEBUG_BOOT) {
                RuntimeException here = new RuntimeException("here");
                here.fillInStackTrace();
                Slog.i(TAG_WM, "enableScreenAfterBoot: mDisplayEnabled=" + mDisplayEnabled
                        + " mForceDisplayEnabled=" + mForceDisplayEnabled
                        + " mShowingBootMessages=" + mShowingBootMessages
                        + " mSystemBooted=" + mSystemBooted, here);
            }
            if (mSystemBooted) {
                return;
            }
            mSystemBooted = true;
            hideBootMessagesLocked();
            // If the screen still doesn't come up after 30 seconds, give
            // up and turn it on.
            mH.sendEmptyMessageDelayed(H.BOOT_TIMEOUT, 30 * 1000);
        }

        mPolicy.systemBooted();

        performEnableScreen();
    }

    @Override
    public void enableScreenIfNeeded() {
        synchronized (mWindowMap) {
            enableScreenIfNeededLocked();
        }
    }

    void enableScreenIfNeededLocked() {
        if (DEBUG_BOOT) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Slog.i(TAG_WM, "enableScreenIfNeededLocked: mDisplayEnabled=" + mDisplayEnabled
                    + " mForceDisplayEnabled=" + mForceDisplayEnabled
                    + " mShowingBootMessages=" + mShowingBootMessages
                    + " mSystemBooted=" + mSystemBooted, here);
        }
        if (mDisplayEnabled) {
            return;
        }
        if (!mSystemBooted && !mShowingBootMessages) {
            return;
        }
        mH.sendEmptyMessage(H.ENABLE_SCREEN);
    }

    public void performBootTimeout() {
        synchronized(mWindowMap) {
            if (mDisplayEnabled) {
                return;
            }
            Slog.w(TAG_WM, "***** BOOT TIMEOUT: forcing display enabled");
            mForceDisplayEnabled = true;
        }
        performEnableScreen();
    }

    /**
     * Called when System UI has been started.
     */
    public void onSystemUiStarted() {
        mPolicy.onSystemUiStarted();
    }

    private void performEnableScreen() {
        synchronized(mWindowMap) {
            if (DEBUG_BOOT) Slog.i(TAG_WM, "performEnableScreen: mDisplayEnabled=" + mDisplayEnabled
                    + " mForceDisplayEnabled=" + mForceDisplayEnabled
                    + " mShowingBootMessages=" + mShowingBootMessages
                    + " mSystemBooted=" + mSystemBooted
                    + " mOnlyCore=" + mOnlyCore,
                    new RuntimeException("here").fillInStackTrace());
            if (mDisplayEnabled) {
                return;
            }
            if (!mSystemBooted && !mShowingBootMessages) {
                return;
            }

            if (!mShowingBootMessages && !mPolicy.canDismissBootAnimation()) {
                return;
            }

            // Don't enable the screen until all existing windows have been drawn.
            if (!mForceDisplayEnabled
                    // TODO(multidisplay): Expand to all displays?
                    && getDefaultDisplayContentLocked().checkWaitingForWindows()) {
                return;
            }

            if (!mBootAnimationStopped) {
                Trace.asyncTraceBegin(TRACE_TAG_WINDOW_MANAGER, "Stop bootanim", 0);
                // stop boot animation
                // formerly we would just kill the process, but we now ask it to exit so it
                // can choose where to stop the animation.
                SystemProperties.set("service.bootanim.exit", "1");
                mBootAnimationStopped = true;
            }

            if (!mForceDisplayEnabled && !checkBootAnimationCompleteLocked()) {
                if (DEBUG_BOOT) Slog.i(TAG_WM, "performEnableScreen: Waiting for anim complete");
                return;
            }

            try {
                IBinder surfaceFlinger = ServiceManager.getService("SurfaceFlinger");
                if (surfaceFlinger != null) {
                    Slog.i(TAG_WM, "******* TELLING SURFACE FLINGER WE ARE BOOTED!");
                    Parcel data = Parcel.obtain();
                    data.writeInterfaceToken("android.ui.ISurfaceComposer");
                    surfaceFlinger.transact(IBinder.FIRST_CALL_TRANSACTION, // BOOT_FINISHED
                            data, null, 0);
                    data.recycle();
                }
            } catch (RemoteException ex) {
                Slog.e(TAG_WM, "Boot completed: SurfaceFlinger is dead!");
            }

            EventLog.writeEvent(EventLogTags.WM_BOOT_ANIMATION_DONE, SystemClock.uptimeMillis());
            Trace.asyncTraceEnd(TRACE_TAG_WINDOW_MANAGER, "Stop bootanim", 0);
            mDisplayEnabled = true;
            if (DEBUG_SCREEN_ON || DEBUG_BOOT) Slog.i(TAG_WM, "******************** ENABLING SCREEN!");

            // Enable input dispatch.
            mInputMonitor.setEventDispatchingLw(mEventDispatchingEnabled);
        }

        try {
            mActivityManager.bootAnimationComplete();
        } catch (RemoteException e) {
        }

        mPolicy.enableScreenAfterBoot();

        // Make sure the last requested orientation has been applied.
        updateRotationUnchecked(false, false);
    }

    private boolean checkBootAnimationCompleteLocked() {
        if (SystemService.isRunning(BOOT_ANIMATION_SERVICE)) {
            mH.removeMessages(H.CHECK_IF_BOOT_ANIMATION_FINISHED);
            mH.sendEmptyMessageDelayed(H.CHECK_IF_BOOT_ANIMATION_FINISHED,
                    BOOT_ANIMATION_POLL_INTERVAL);
            if (DEBUG_BOOT) Slog.i(TAG_WM, "checkBootAnimationComplete: Waiting for anim complete");
            return false;
        }
        if (DEBUG_BOOT) Slog.i(TAG_WM, "checkBootAnimationComplete: Animation complete!");
        return true;
    }

    public void showBootMessage(final CharSequence msg, final boolean always) {
        boolean first = false;
        synchronized(mWindowMap) {
            if (DEBUG_BOOT) {
                RuntimeException here = new RuntimeException("here");
                here.fillInStackTrace();
                Slog.i(TAG_WM, "showBootMessage: msg=" + msg + " always=" + always
                        + " mAllowBootMessages=" + mAllowBootMessages
                        + " mShowingBootMessages=" + mShowingBootMessages
                        + " mSystemBooted=" + mSystemBooted, here);
            }
            if (!mAllowBootMessages) {
                return;
            }
            if (!mShowingBootMessages) {
                if (!always) {
                    return;
                }
                first = true;
            }
            if (mSystemBooted) {
                return;
            }
            mShowingBootMessages = true;
            mPolicy.showBootMessage(msg, always);
        }
        if (first) {
            performEnableScreen();
        }
    }

    public void hideBootMessagesLocked() {
        if (DEBUG_BOOT) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Slog.i(TAG_WM, "hideBootMessagesLocked: mDisplayEnabled=" + mDisplayEnabled
                    + " mForceDisplayEnabled=" + mForceDisplayEnabled
                    + " mShowingBootMessages=" + mShowingBootMessages
                    + " mSystemBooted=" + mSystemBooted, here);
        }
        if (mShowingBootMessages) {
            mShowingBootMessages = false;
            mPolicy.hideBootMessages();
        }
    }

    @Override
    public void setInTouchMode(boolean mode) {
        synchronized(mWindowMap) {
            mInTouchMode = mode;
        }
    }

    private void updateCircularDisplayMaskIfNeeded() {
        if (mContext.getResources().getConfiguration().isScreenRound()
                && mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_windowShowCircularMask)) {
            final int currentUserId;
            synchronized(mWindowMap) {
                currentUserId = mCurrentUserId;
            }
            // Device configuration calls for a circular display mask, but we only enable the mask
            // if the accessibility color inversion feature is disabled, as the inverted mask
            // causes artifacts.
            int inversionState = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, 0, currentUserId);
            int showMask = (inversionState == 1) ? 0 : 1;
            Message m = mH.obtainMessage(H.SHOW_CIRCULAR_DISPLAY_MASK);
            m.arg1 = showMask;
            mH.sendMessage(m);
        }
    }

    public void showEmulatorDisplayOverlayIfNeeded() {
        if (mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_windowEnableCircularEmulatorDisplayOverlay)
                && SystemProperties.getBoolean(PROPERTY_EMULATOR_CIRCULAR, false)
                && Build.IS_EMULATOR) {
            mH.sendMessage(mH.obtainMessage(H.SHOW_EMULATOR_DISPLAY_OVERLAY));
        }
    }

    public void showCircularMask(boolean visible) {
        synchronized(mWindowMap) {

            if (SHOW_LIGHT_TRANSACTIONS) Slog.i(TAG_WM,
                    ">>> OPEN TRANSACTION showCircularMask(visible=" + visible + ")");
            openSurfaceTransaction();
            try {
                if (visible) {
                    // TODO(multi-display): support multiple displays
                    if (mCircularDisplayMask == null) {
                        int screenOffset = mContext.getResources().getInteger(
                                com.android.internal.R.integer.config_windowOutsetBottom);
                        int maskThickness = mContext.getResources().getDimensionPixelSize(
                                com.android.internal.R.dimen.circular_display_mask_thickness);

                        mCircularDisplayMask = new CircularDisplayMask(
                                getDefaultDisplayContentLocked().getDisplay(),
                                mFxSession,
                                mPolicy.getWindowLayerFromTypeLw(
                                        WindowManager.LayoutParams.TYPE_POINTER)
                                        * TYPE_LAYER_MULTIPLIER + 10, screenOffset, maskThickness);
                    }
                    mCircularDisplayMask.setVisibility(true);
                } else if (mCircularDisplayMask != null) {
                    mCircularDisplayMask.setVisibility(false);
                    mCircularDisplayMask = null;
                }
            } finally {
                closeSurfaceTransaction();
                if (SHOW_LIGHT_TRANSACTIONS) Slog.i(TAG_WM,
                        "<<< CLOSE TRANSACTION showCircularMask(visible=" + visible + ")");
            }
        }
    }

    public void showEmulatorDisplayOverlay() {
        synchronized(mWindowMap) {

            if (SHOW_LIGHT_TRANSACTIONS) Slog.i(TAG_WM,
                    ">>> OPEN TRANSACTION showEmulatorDisplayOverlay");
            openSurfaceTransaction();
            try {
                if (mEmulatorDisplayOverlay == null) {
                    mEmulatorDisplayOverlay = new EmulatorDisplayOverlay(
                            mContext,
                            getDefaultDisplayContentLocked().getDisplay(),
                            mFxSession,
                            mPolicy.getWindowLayerFromTypeLw(
                                    WindowManager.LayoutParams.TYPE_POINTER)
                                    * TYPE_LAYER_MULTIPLIER + 10);
                }
                mEmulatorDisplayOverlay.setVisibility(true);
            } finally {
                closeSurfaceTransaction();
                if (SHOW_LIGHT_TRANSACTIONS) Slog.i(TAG_WM,
                        "<<< CLOSE TRANSACTION showEmulatorDisplayOverlay");
            }
        }
    }

    // TODO: more accounting of which pid(s) turned it on, keep count,
    // only allow disables from pids which have count on, etc.
    @Override
    public void showStrictModeViolation(boolean on) {
        final int pid = Binder.getCallingPid();
        if (on) {
            // Show the visualization, and enqueue a second message to tear it
            // down if we don't hear back from the app.
            mH.sendMessage(mH.obtainMessage(H.SHOW_STRICT_MODE_VIOLATION, 1, pid));
            mH.sendMessageDelayed(mH.obtainMessage(H.SHOW_STRICT_MODE_VIOLATION, 0, pid),
                    DateUtils.SECOND_IN_MILLIS);
        } else {
            mH.sendMessage(mH.obtainMessage(H.SHOW_STRICT_MODE_VIOLATION, 0, pid));
        }
    }

    private void showStrictModeViolation(int arg, int pid) {
        final boolean on = arg != 0;
        synchronized(mWindowMap) {
            // Ignoring requests to enable the red border from clients which aren't on screen.
            // (e.g. Broadcast Receivers in the background..)
            if (on && !mRoot.canShowStrictModeViolation(pid)) {
                return;
            }

            if (SHOW_VERBOSE_TRANSACTIONS) Slog.i(TAG_WM,
                    ">>> OPEN TRANSACTION showStrictModeViolation");
            // TODO: Modify this to use the surface trace once it is not going crazy.
            // b/31532461
            SurfaceControl.openTransaction();
            try {
                // TODO(multi-display): support multiple displays
                if (mStrictModeFlash == null) {
                    mStrictModeFlash = new StrictModeFlash(
                            getDefaultDisplayContentLocked().getDisplay(), mFxSession);
                }
                mStrictModeFlash.setVisibility(on);
            } finally {
                SurfaceControl.closeTransaction();
                if (SHOW_VERBOSE_TRANSACTIONS) Slog.i(TAG_WM,
                        "<<< CLOSE TRANSACTION showStrictModeViolation");
            }
        }
    }

    @Override
    public void setStrictModeVisualIndicatorPreference(String value) {
        SystemProperties.set(StrictMode.VISUAL_PROPERTY, value);
    }

    @Override
    public Bitmap screenshotWallpaper() {
        if (!checkCallingPermission(READ_FRAME_BUFFER,
                "screenshotWallpaper()")) {
            throw new SecurityException("Requires READ_FRAME_BUFFER permission");
        }
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "screenshotWallpaper");
            return screenshotApplications(null /* appToken */, DEFAULT_DISPLAY, -1 /* width */,
                    -1 /* height */, true /* includeFullDisplay */, 1f /* frameScale */,
                    Bitmap.Config.ARGB_8888, true /* wallpaperOnly */, false /* includeDecor */);
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Takes a snapshot of the screen.  In landscape mode this grabs the whole screen.
     * In portrait mode, it grabs the upper region of the screen based on the vertical dimension
     * of the target image.
     */
    @Override
    public boolean requestAssistScreenshot(final IAssistScreenshotReceiver receiver) {
        if (!checkCallingPermission(READ_FRAME_BUFFER,
                "requestAssistScreenshot()")) {
            throw new SecurityException("Requires READ_FRAME_BUFFER permission");
        }

        FgThread.getHandler().post(() -> {
            Bitmap bm = screenshotApplications(null /* appToken */, DEFAULT_DISPLAY,
                    -1 /* width */, -1 /* height */, true /* includeFullDisplay */,
                    1f /* frameScale */, Bitmap.Config.ARGB_8888, false /* wallpaperOnly */,
                    false /* includeDecor */);
            try {
                receiver.send(bm);
            } catch (RemoteException e) {
            }
        });

        return true;
    }

    public TaskSnapshot getTaskSnapshot(int taskId, int userId, boolean reducedResolution) {
        return mTaskSnapshotController.getSnapshot(taskId, userId, true /* restoreFromDisk */,
                reducedResolution);
    }

    /**
     * In case a task write/delete operation was lost because the system crashed, this makes sure to
     * clean up the directory to remove obsolete files.
     *
     * @param persistentTaskIds A set of task ids that exist in our in-memory model.
     * @param runningUserIds The ids of the list of users that have tasks loaded in our in-memory
     *                       model.
     */
    public void removeObsoleteTaskFiles(ArraySet<Integer> persistentTaskIds, int[] runningUserIds) {
        synchronized (mWindowMap) {
            mTaskSnapshotController.removeObsoleteTaskFiles(persistentTaskIds, runningUserIds);
        }
    }

    /**
     * Takes a snapshot of the screen.  In landscape mode this grabs the whole screen.
     * In portrait mode, it grabs the full screenshot.
     *
     * @param displayId the Display to take a screenshot of.
     * @param width the width of the target bitmap
     * @param height the height of the target bitmap
     * @param includeFullDisplay true if the screen should not be cropped before capture
     * @param frameScale the scale to apply to the frame, only used when width = -1 and height = -1
     * @param config of the output bitmap
     * @param wallpaperOnly true if only the wallpaper layer should be included in the screenshot
     * @param includeDecor whether to include window decors, like the status or navigation bar
     *                     background of the window
     */
    private Bitmap screenshotApplications(IBinder appToken, int displayId, int width,
            int height, boolean includeFullDisplay, float frameScale, Bitmap.Config config,
            boolean wallpaperOnly, boolean includeDecor) {
        final DisplayContent displayContent;
        synchronized(mWindowMap) {
            displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent == null) {
                if (DEBUG_SCREENSHOT) Slog.i(TAG_WM, "Screenshot of " + appToken
                        + ": returning null. No Display for displayId=" + displayId);
                return null;
            }
        }
        return displayContent.screenshotApplications(appToken, width, height,
                includeFullDisplay, frameScale, config, wallpaperOnly, includeDecor);
    }

    /**
     * Freeze rotation changes.  (Enable "rotation lock".)
     * Persists across reboots.
     * @param rotation The desired rotation to freeze to, or -1 to use the
     * current rotation.
     */
    @Override
    public void freezeRotation(int rotation) {
        // TODO(multi-display): Track which display is rotated.
        if (!checkCallingPermission(android.Manifest.permission.SET_ORIENTATION,
                "freezeRotation()")) {
            throw new SecurityException("Requires SET_ORIENTATION permission");
        }
        if (rotation < -1 || rotation > Surface.ROTATION_270) {
            throw new IllegalArgumentException("Rotation argument must be -1 or a valid "
                    + "rotation constant.");
        }

        final int defaultDisplayRotation = getDefaultDisplayRotation();
        if (DEBUG_ORIENTATION) Slog.v(TAG_WM, "freezeRotation: mRotation="
                + defaultDisplayRotation);

        long origId = Binder.clearCallingIdentity();
        try {
            mPolicy.setUserRotationMode(WindowManagerPolicy.USER_ROTATION_LOCKED,
                    rotation == -1 ? defaultDisplayRotation : rotation);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }

        updateRotationUnchecked(false, false);
    }

    /**
     * Thaw rotation changes.  (Disable "rotation lock".)
     * Persists across reboots.
     */
    @Override
    public void thawRotation() {
        if (!checkCallingPermission(android.Manifest.permission.SET_ORIENTATION,
                "thawRotation()")) {
            throw new SecurityException("Requires SET_ORIENTATION permission");
        }

        if (DEBUG_ORIENTATION) Slog.v(TAG_WM, "thawRotation: mRotation="
                + getDefaultDisplayRotation());

        long origId = Binder.clearCallingIdentity();
        try {
            mPolicy.setUserRotationMode(WindowManagerPolicy.USER_ROTATION_FREE,
                    777); // rot not used
        } finally {
            Binder.restoreCallingIdentity(origId);
        }

        updateRotationUnchecked(false, false);
    }

    /**
     * Recalculate the current rotation.
     *
     * Called by the window manager policy whenever the state of the system changes
     * such that the current rotation might need to be updated, such as when the
     * device is docked or rotated into a new posture.
     */
    @Override
    public void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout) {
        updateRotationUnchecked(alwaysSendConfiguration, forceRelayout);
    }

    /**
     * Temporarily pauses rotation changes until resumed.
     *
     * This can be used to prevent rotation changes from occurring while the user is
     * performing certain operations, such as drag and drop.
     *
     * This call nests and must be matched by an equal number of calls to
     * {@link #resumeRotationLocked}.
     */
    void pauseRotationLocked() {
        mDeferredRotationPauseCount += 1;
    }

    /**
     * Resumes normal rotation changes after being paused.
     */
    void resumeRotationLocked() {
        if (mDeferredRotationPauseCount > 0) {
            mDeferredRotationPauseCount -= 1;
            if (mDeferredRotationPauseCount == 0) {
                // TODO(multi-display): Update rotation for different displays separately.
                final DisplayContent displayContent = getDefaultDisplayContentLocked();
                final boolean changed = displayContent.updateRotationUnchecked(
                        false /* inTransaction */);
                if (changed) {
                    mH.obtainMessage(H.SEND_NEW_CONFIGURATION, displayContent.getDisplayId())
                            .sendToTarget();
                }
            }
        }
    }

    private void updateRotationUnchecked(boolean alwaysSendConfiguration, boolean forceRelayout) {
        if(DEBUG_ORIENTATION) Slog.v(TAG_WM, "updateRotationUnchecked:"
                + " alwaysSendConfiguration=" + alwaysSendConfiguration
                + " forceRelayout=" + forceRelayout);

        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "updateRotation");

        long origId = Binder.clearCallingIdentity();

        try {
            // TODO(multi-display): Update rotation for different displays separately.
            final boolean rotationChanged;
            final int displayId;
            synchronized (mWindowMap) {
                final DisplayContent displayContent = getDefaultDisplayContentLocked();
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "updateRotation: display");
                rotationChanged = displayContent.updateRotationUnchecked(
                        false /* inTransaction */);
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                if (!rotationChanged || forceRelayout) {
                    displayContent.setLayoutNeeded();
                    Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER,
                            "updateRotation: performSurfacePlacement");
                    mWindowPlacerLocked.performSurfacePlacement();
                    Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                }
                displayId = displayContent.getDisplayId();
            }

            if (rotationChanged || alwaysSendConfiguration) {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "updateRotation: sendNewConfiguration");
                sendNewConfiguration(displayId);
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    @Override
    public int getDefaultDisplayRotation() {
        synchronized (mWindowMap) {
            return getDefaultDisplayContentLocked().getRotation();
        }
    }

    @Override
    public boolean isRotationFrozen() {
        return mPolicy.getUserRotationMode() == WindowManagerPolicy.USER_ROTATION_LOCKED;
    }

    @Override
    public int watchRotation(IRotationWatcher watcher, int displayId) {
        final IBinder watcherBinder = watcher.asBinder();
        IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                synchronized (mWindowMap) {
                    for (int i=0; i<mRotationWatchers.size(); i++) {
                        if (watcherBinder == mRotationWatchers.get(i).mWatcher.asBinder()) {
                            RotationWatcher removed = mRotationWatchers.remove(i);
                            IBinder binder = removed.mWatcher.asBinder();
                            if (binder != null) {
                                binder.unlinkToDeath(this, 0);
                            }
                            i--;
                        }
                    }
                }
            }
        };

        synchronized (mWindowMap) {
            try {
                watcher.asBinder().linkToDeath(dr, 0);
                mRotationWatchers.add(new RotationWatcher(watcher, dr, displayId));
            } catch (RemoteException e) {
                // Client died, no cleanup needed.
            }

            return getDefaultDisplayRotation();
        }
    }

    @Override
    public void removeRotationWatcher(IRotationWatcher watcher) {
        final IBinder watcherBinder = watcher.asBinder();
        synchronized (mWindowMap) {
            for (int i=0; i<mRotationWatchers.size(); i++) {
                RotationWatcher rotationWatcher = mRotationWatchers.get(i);
                if (watcherBinder == rotationWatcher.mWatcher.asBinder()) {
                    RotationWatcher removed = mRotationWatchers.remove(i);
                    IBinder binder = removed.mWatcher.asBinder();
                    if (binder != null) {
                        binder.unlinkToDeath(removed.mDeathRecipient, 0);
                    }
                    i--;
                }
            }
        }
    }

    @Override
    public boolean registerWallpaperVisibilityListener(IWallpaperVisibilityListener listener,
            int displayId) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent == null) {
                throw new IllegalArgumentException("Trying to register visibility event "
                        + "for invalid display: " + displayId);
            }
            mWallpaperVisibilityListeners.registerWallpaperVisibilityListener(listener, displayId);
            return displayContent.mWallpaperController.isWallpaperVisible();
        }
    }

    @Override
    public void unregisterWallpaperVisibilityListener(IWallpaperVisibilityListener listener,
            int displayId) {
        synchronized (mWindowMap) {
            mWallpaperVisibilityListeners
                    .unregisterWallpaperVisibilityListener(listener, displayId);
        }
    }

    /**
     * Apps that use the compact menu panel (as controlled by the panelMenuIsCompact
     * theme attribute) on devices that feature a physical options menu key attempt to position
     * their menu panel window along the edge of the screen nearest the physical menu key.
     * This lowers the travel distance between invoking the menu panel and selecting
     * a menu option.
     *
     * This method helps control where that menu is placed. Its current implementation makes
     * assumptions about the menu key and its relationship to the screen based on whether
     * the device's natural orientation is portrait (width < height) or landscape.
     *
     * The menu key is assumed to be located along the bottom edge of natural-portrait
     * devices and along the right edge of natural-landscape devices. If these assumptions
     * do not hold for the target device, this method should be changed to reflect that.
     *
     * @return A {@link Gravity} value for placing the options menu window
     */
    @Override
    public int getPreferredOptionsPanelGravity() {
        synchronized (mWindowMap) {
            // TODO(multidisplay): Assume that such devices physical keys are on the main screen.
            final DisplayContent displayContent = getDefaultDisplayContentLocked();
            final int rotation = displayContent.getRotation();
            if (displayContent.mInitialDisplayWidth < displayContent.mInitialDisplayHeight) {
                // On devices with a natural orientation of portrait
                switch (rotation) {
                    default:
                    case Surface.ROTATION_0:
                        return Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    case Surface.ROTATION_90:
                        return Gravity.RIGHT | Gravity.BOTTOM;
                    case Surface.ROTATION_180:
                        return Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    case Surface.ROTATION_270:
                        return Gravity.START | Gravity.BOTTOM;
                }
            }

            // On devices with a natural orientation of landscape
            switch (rotation) {
                default:
                case Surface.ROTATION_0:
                    return Gravity.RIGHT | Gravity.BOTTOM;
                case Surface.ROTATION_90:
                    return Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                case Surface.ROTATION_180:
                    return Gravity.START | Gravity.BOTTOM;
                case Surface.ROTATION_270:
                    return Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            }
        }
    }

    /**
     * Starts the view server on the specified port.
     *
     * @param port The port to listener to.
     *
     * @return True if the server was successfully started, false otherwise.
     *
     * @see com.android.server.wm.ViewServer
     * @see com.android.server.wm.ViewServer#VIEW_SERVER_DEFAULT_PORT
     */
    @Override
    public boolean startViewServer(int port) {
        if (isSystemSecure()) {
            return false;
        }

        if (!checkCallingPermission(Manifest.permission.DUMP, "startViewServer")) {
            return false;
        }

        if (port < 1024) {
            return false;
        }

        if (mViewServer != null) {
            if (!mViewServer.isRunning()) {
                try {
                    return mViewServer.start();
                } catch (IOException e) {
                    Slog.w(TAG_WM, "View server did not start");
                }
            }
            return false;
        }

        try {
            mViewServer = new ViewServer(this, port);
            return mViewServer.start();
        } catch (IOException e) {
            Slog.w(TAG_WM, "View server did not start");
        }
        return false;
    }

    private boolean isSystemSecure() {
        return "1".equals(SystemProperties.get(SYSTEM_SECURE, "1")) &&
                "0".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
    }

    /**
     * Stops the view server if it exists.
     *
     * @return True if the server stopped, false if it wasn't started or
     *         couldn't be stopped.
     *
     * @see com.android.server.wm.ViewServer
     */
    @Override
    public boolean stopViewServer() {
        if (isSystemSecure()) {
            return false;
        }

        if (!checkCallingPermission(Manifest.permission.DUMP, "stopViewServer")) {
            return false;
        }

        if (mViewServer != null) {
            return mViewServer.stop();
        }
        return false;
    }

    /**
     * Indicates whether the view server is running.
     *
     * @return True if the server is running, false otherwise.
     *
     * @see com.android.server.wm.ViewServer
     */
    @Override
    public boolean isViewServerRunning() {
        if (isSystemSecure()) {
            return false;
        }

        if (!checkCallingPermission(Manifest.permission.DUMP, "isViewServerRunning")) {
            return false;
        }

        return mViewServer != null && mViewServer.isRunning();
    }

    /**
     * Lists all available windows in the system. The listing is written in the specified Socket's
     * output stream with the following syntax: windowHashCodeInHexadecimal windowName
     * Each line of the output represents a different window.
     *
     * @param client The remote client to send the listing to.
     * @return false if an error occurred, true otherwise.
     */
    boolean viewServerListWindows(Socket client) {
        if (isSystemSecure()) {
            return false;
        }

        boolean result = true;

        final ArrayList<WindowState> windows = new ArrayList();
        synchronized (mWindowMap) {
            mRoot.forAllWindows(w -> {
                windows.add(w);
            }, false /* traverseTopToBottom */);
        }

        BufferedWriter out = null;

        // Any uncaught exception will crash the system process
        try {
            OutputStream clientStream = client.getOutputStream();
            out = new BufferedWriter(new OutputStreamWriter(clientStream), 8 * 1024);

            final int count = windows.size();
            for (int i = 0; i < count; i++) {
                final WindowState w = windows.get(i);
                out.write(Integer.toHexString(System.identityHashCode(w)));
                out.write(' ');
                out.append(w.mAttrs.getTitle());
                out.write('\n');
            }

            out.write("DONE.\n");
            out.flush();
        } catch (Exception e) {
            result = false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    result = false;
                }
            }
        }

        return result;
    }

    // TODO(multidisplay): Extend to multiple displays.
    /**
     * Returns the focused window in the following format:
     * windowHashCodeInHexadecimal windowName
     *
     * @param client The remote client to send the listing to.
     * @return False if an error occurred, true otherwise.
     */
    boolean viewServerGetFocusedWindow(Socket client) {
        if (isSystemSecure()) {
            return false;
        }

        boolean result = true;

        WindowState focusedWindow = getFocusedWindow();

        BufferedWriter out = null;

        // Any uncaught exception will crash the system process
        try {
            OutputStream clientStream = client.getOutputStream();
            out = new BufferedWriter(new OutputStreamWriter(clientStream), 8 * 1024);

            if(focusedWindow != null) {
                out.write(Integer.toHexString(System.identityHashCode(focusedWindow)));
                out.write(' ');
                out.append(focusedWindow.mAttrs.getTitle());
            }
            out.write('\n');
            out.flush();
        } catch (Exception e) {
            result = false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    result = false;
                }
            }
        }

        return result;
    }

    /**
     * Sends a command to a target window. The result of the command, if any, will be
     * written in the output stream of the specified socket.
     *
     * The parameters must follow this syntax:
     * windowHashcode extra
     *
     * Where XX is the length in characeters of the windowTitle.
     *
     * The first parameter is the target window. The window with the specified hashcode
     * will be the target. If no target can be found, nothing happens. The extra parameters
     * will be delivered to the target window and as parameters to the command itself.
     *
     * @param client The remote client to sent the result, if any, to.
     * @param command The command to execute.
     * @param parameters The command parameters.
     *
     * @return True if the command was successfully delivered, false otherwise. This does
     *         not indicate whether the command itself was successful.
     */
    boolean viewServerWindowCommand(Socket client, String command, String parameters) {
        if (isSystemSecure()) {
            return false;
        }

        boolean success = true;
        Parcel data = null;
        Parcel reply = null;

        BufferedWriter out = null;

        // Any uncaught exception will crash the system process
        try {
            // Find the hashcode of the window
            int index = parameters.indexOf(' ');
            if (index == -1) {
                index = parameters.length();
            }
            final String code = parameters.substring(0, index);
            int hashCode = (int) Long.parseLong(code, 16);

            // Extract the command's parameter after the window description
            if (index < parameters.length()) {
                parameters = parameters.substring(index + 1);
            } else {
                parameters = "";
            }

            final WindowState window = findWindow(hashCode);
            if (window == null) {
                return false;
            }

            data = Parcel.obtain();
            data.writeInterfaceToken("android.view.IWindow");
            data.writeString(command);
            data.writeString(parameters);
            data.writeInt(1);
            ParcelFileDescriptor.fromSocket(client).writeToParcel(data, 0);

            reply = Parcel.obtain();

            final IBinder binder = window.mClient.asBinder();
            // TODO: GET THE TRANSACTION CODE IN A SAFER MANNER
            binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);

            reply.readException();

            if (!client.isOutputShutdown()) {
                out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
                out.write("DONE\n");
                out.flush();
            }

        } catch (Exception e) {
            Slog.w(TAG_WM, "Could not send command " + command + " with parameters " + parameters, e);
            success = false;
        } finally {
            if (data != null) {
                data.recycle();
            }
            if (reply != null) {
                reply.recycle();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {

                }
            }
        }

        return success;
    }

    public void addWindowChangeListener(WindowChangeListener listener) {
        synchronized(mWindowMap) {
            mWindowChangeListeners.add(listener);
        }
    }

    public void removeWindowChangeListener(WindowChangeListener listener) {
        synchronized(mWindowMap) {
            mWindowChangeListeners.remove(listener);
        }
    }

    private void notifyWindowsChanged() {
        WindowChangeListener[] windowChangeListeners;
        synchronized(mWindowMap) {
            if(mWindowChangeListeners.isEmpty()) {
                return;
            }
            windowChangeListeners = new WindowChangeListener[mWindowChangeListeners.size()];
            windowChangeListeners = mWindowChangeListeners.toArray(windowChangeListeners);
        }
        int N = windowChangeListeners.length;
        for(int i = 0; i < N; i++) {
            windowChangeListeners[i].windowsChanged();
        }
    }

    private void notifyFocusChanged() {
        WindowChangeListener[] windowChangeListeners;
        synchronized(mWindowMap) {
            if(mWindowChangeListeners.isEmpty()) {
                return;
            }
            windowChangeListeners = new WindowChangeListener[mWindowChangeListeners.size()];
            windowChangeListeners = mWindowChangeListeners.toArray(windowChangeListeners);
        }
        int N = windowChangeListeners.length;
        for(int i = 0; i < N; i++) {
            windowChangeListeners[i].focusChanged();
        }
    }

    private WindowState findWindow(int hashCode) {
        if (hashCode == -1) {
            // TODO(multidisplay): Extend to multiple displays.
            return getFocusedWindow();
        }

        synchronized (mWindowMap) {
            return mRoot.getWindow((w) -> System.identityHashCode(w) == hashCode);
        }
    }

    /**
     * Instruct the Activity Manager to fetch and update the current display's configuration and
     * broadcast them to config-changed listeners if appropriate.
     * NOTE: Can't be called with the window manager lock held since it call into activity manager.
     */
    void sendNewConfiguration(int displayId) {
        try {
            final boolean configUpdated = mActivityManager.updateDisplayOverrideConfiguration(
                    null /* values */, displayId);
            if (!configUpdated) {
                // Something changed (E.g. device rotation), but no configuration update is needed.
                // E.g. changing device rotation by 180 degrees. Go ahead and perform surface
                // placement to unfreeze the display since we froze it when the rotation was updated
                // in DisplayContent#updateRotationUnchecked.
                synchronized (mWindowMap) {
                    if (mWaitingForConfig) {
                        mWaitingForConfig = false;
                        mLastFinishedFreezeSource = "config-unchanged";
                        final DisplayContent dc = mRoot.getDisplayContent(displayId);
                        if (dc != null) {
                            dc.setLayoutNeeded();
                        }
                        mWindowPlacerLocked.performSurfacePlacement();
                    }
                }
            }
        } catch (RemoteException e) {
        }
    }

    public Configuration computeNewConfiguration(int displayId) {
        synchronized (mWindowMap) {
            return computeNewConfigurationLocked(displayId);
        }
    }

    private Configuration computeNewConfigurationLocked(int displayId) {
        if (!mDisplayReady) {
            return null;
        }
        final Configuration config = new Configuration();
        final DisplayContent displayContent = mRoot.getDisplayContent(displayId);
        displayContent.computeScreenConfiguration(config);
        return config;
    }

    void notifyHardKeyboardStatusChange() {
        final boolean available;
        final WindowManagerInternal.OnHardKeyboardStatusChangeListener listener;
        synchronized (mWindowMap) {
            listener = mHardKeyboardStatusChangeListener;
            available = mHardKeyboardAvailable;
        }
        if (listener != null) {
            listener.onHardKeyboardStatusChange(available);
        }
    }

    boolean startMovingTask(IWindow window, float startX, float startY) {
        WindowState win = null;
        synchronized (mWindowMap) {
            win = windowForClientLocked(null, window, false);
            // win shouldn't be null here, pass it down to startPositioningLocked
            // to get warning if it's null.
            if (!startPositioningLocked(
                        win, false /*resize*/, false /*preserveOrientation*/, startX, startY)) {
                return false;
            }
        }
        try {
            mActivityManager.setFocusedTask(win.getTask().mTaskId);
        } catch(RemoteException e) {}
        return true;
    }

    private void handleTapOutsideTask(DisplayContent displayContent, int x, int y) {
        int taskId = -1;
        synchronized (mWindowMap) {
            final Task task = displayContent.findTaskForResizePoint(x, y);
            if (task != null) {
                if (!startPositioningLocked(task.getTopVisibleAppMainWindow(), true /*resize*/,
                            task.preserveOrientationOnResize(), x, y)) {
                    return;
                }
                taskId = task.mTaskId;
            } else {
                taskId = displayContent.taskIdFromPoint(x, y);
            }
        }
        if (taskId >= 0) {
            try {
                mActivityManager.setFocusedTask(taskId);
            } catch(RemoteException e) {}
        }
    }

    private boolean startPositioningLocked(WindowState win, boolean resize,
            boolean preserveOrientation, float startX, float startY) {
        if (DEBUG_TASK_POSITIONING)
            Slog.d(TAG_WM, "startPositioningLocked: "
                            + "win=" + win + ", resize=" + resize + ", preserveOrientation="
                            + preserveOrientation + ", {" + startX + ", " + startY + "}");

        if (win == null || win.getAppToken() == null) {
            Slog.w(TAG_WM, "startPositioningLocked: Bad window " + win);
            return false;
        }
        if (win.mInputChannel == null) {
            Slog.wtf(TAG_WM, "startPositioningLocked: " + win + " has no input channel, "
                    + " probably being removed");
            return false;
        }

        final DisplayContent displayContent = win.getDisplayContent();
        if (displayContent == null) {
            Slog.w(TAG_WM, "startPositioningLocked: Invalid display content " + win);
            return false;
        }

        Display display = displayContent.getDisplay();
        mTaskPositioner = new TaskPositioner(this);
        mTaskPositioner.register(display);
        mInputMonitor.updateInputWindowsLw(true /*force*/);

        // We need to grab the touch focus so that the touch events during the
        // resizing/scrolling are not sent to the app. 'win' is the main window
        // of the app, it may not have focus since there might be other windows
        // on top (eg. a dialog window).
        WindowState transferFocusFromWin = win;
        if (mCurrentFocus != null && mCurrentFocus != win
                && mCurrentFocus.mAppToken == win.mAppToken) {
            transferFocusFromWin = mCurrentFocus;
        }
        if (!mInputManager.transferTouchFocus(
                transferFocusFromWin.mInputChannel, mTaskPositioner.mServerChannel)) {
            Slog.e(TAG_WM, "startPositioningLocked: Unable to transfer touch focus");
            mTaskPositioner.unregister();
            mTaskPositioner = null;
            mInputMonitor.updateInputWindowsLw(true /*force*/);
            return false;
        }

        mTaskPositioner.startDrag(win, resize, preserveOrientation, startX, startY);
        return true;
    }

    private void finishPositioning() {
        if (DEBUG_TASK_POSITIONING) {
            Slog.d(TAG_WM, "finishPositioning");
        }
        synchronized (mWindowMap) {
            if (mTaskPositioner != null) {
                mTaskPositioner.unregister();
                mTaskPositioner = null;
                mInputMonitor.updateInputWindowsLw(true /*force*/);
            }
        }
    }

    // -------------------------------------------------------------
    // Drag and drop
    // -------------------------------------------------------------

    IBinder prepareDragSurface(IWindow window, SurfaceSession session,
            int flags, int width, int height, Surface outSurface) {
        if (DEBUG_DRAG) {
            Slog.d(TAG_WM, "prepare drag surface: w=" + width + " h=" + height
                    + " flags=" + Integer.toHexString(flags) + " win=" + window
                    + " asbinder=" + window.asBinder());
        }

        final int callerPid = Binder.getCallingPid();
        final int callerUid = Binder.getCallingUid();
        final long origId = Binder.clearCallingIdentity();
        IBinder token = null;

        try {
            synchronized (mWindowMap) {
                try {
                    if (mDragState == null) {
                        // TODO(multi-display): support other displays
                        final DisplayContent displayContent = getDefaultDisplayContentLocked();
                        final Display display = displayContent.getDisplay();

                        SurfaceControl surface = new SurfaceControl(session, "drag surface",
                                width, height, PixelFormat.TRANSLUCENT, SurfaceControl.HIDDEN);
                        surface.setLayerStack(display.getLayerStack());
                        float alpha = 1;
                        if ((flags & View.DRAG_FLAG_OPAQUE) == 0) {
                            alpha = DRAG_SHADOW_ALPHA_TRANSPARENT;
                        }
                        surface.setAlpha(alpha);

                        if (SHOW_TRANSACTIONS) Slog.i(TAG_WM, "  DRAG "
                                + surface + ": CREATE");
                        outSurface.copyFrom(surface);
                        final IBinder winBinder = window.asBinder();
                        token = new Binder();
                        mDragState = new DragState(this, token, surface, flags, winBinder);
                        mDragState.mPid = callerPid;
                        mDragState.mUid = callerUid;
                        mDragState.mOriginalAlpha = alpha;
                        token = mDragState.mToken = new Binder();

                        // 5 second timeout for this window to actually begin the drag
                        mH.removeMessages(H.DRAG_START_TIMEOUT, winBinder);
                        Message msg = mH.obtainMessage(H.DRAG_START_TIMEOUT, winBinder);
                        mH.sendMessageDelayed(msg, 5000);
                    } else {
                        Slog.w(TAG_WM, "Drag already in progress");
                    }
                } catch (OutOfResourcesException e) {
                    Slog.e(TAG_WM, "Can't allocate drag surface w=" + width + " h=" + height, e);
                    if (mDragState != null) {
                        mDragState.reset();
                        mDragState = null;
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }

        return token;
    }

    // -------------------------------------------------------------
    // Input Events and Focus Management
    // -------------------------------------------------------------

    final InputMonitor mInputMonitor = new InputMonitor(this);
    private boolean mEventDispatchingEnabled;

    @Override
    public void setEventDispatching(boolean enabled) {
        if (!checkCallingPermission(MANAGE_APP_TOKENS, "setEventDispatching()")) {
            throw new SecurityException("Requires MANAGE_APP_TOKENS permission");
        }

        synchronized (mWindowMap) {
            mEventDispatchingEnabled = enabled;
            if (mDisplayEnabled) {
                mInputMonitor.setEventDispatchingLw(enabled);
            }
        }
    }

    private WindowState getFocusedWindow() {
        synchronized (mWindowMap) {
            return getFocusedWindowLocked();
        }
    }

    private WindowState getFocusedWindowLocked() {
        return mCurrentFocus;
    }

    TaskStack getImeFocusStackLocked() {
        // Don't use mCurrentFocus.getStack() because it returns home stack for system windows.
        // Also don't use mInputMethodTarget's stack, because some window with FLAG_NOT_FOCUSABLE
        // and FLAG_ALT_FOCUSABLE_IM flags both set might be set to IME target so they're moved
        // to make room for IME, but the window is not the focused window that's taking input.
        return (mFocusedApp != null && mFocusedApp.getTask() != null) ?
                mFocusedApp.getTask().mStack : null;
    }

    public boolean detectSafeMode() {
        if (!mInputMonitor.waitForInputDevicesReady(
                INPUT_DEVICES_READY_FOR_SAFE_MODE_DETECTION_TIMEOUT_MILLIS)) {
            Slog.w(TAG_WM, "Devices still not ready after waiting "
                   + INPUT_DEVICES_READY_FOR_SAFE_MODE_DETECTION_TIMEOUT_MILLIS
                   + " milliseconds before attempting to detect safe mode.");
        }

        if (Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.SAFE_BOOT_DISALLOWED, 0) != 0) {
            return false;
        }

        int menuState = mInputManager.getKeyCodeState(-1, InputDevice.SOURCE_ANY,
                KeyEvent.KEYCODE_MENU);
        int sState = mInputManager.getKeyCodeState(-1, InputDevice.SOURCE_ANY, KeyEvent.KEYCODE_S);
        int dpadState = mInputManager.getKeyCodeState(-1, InputDevice.SOURCE_DPAD,
                KeyEvent.KEYCODE_DPAD_CENTER);
        int trackballState = mInputManager.getScanCodeState(-1, InputDevice.SOURCE_TRACKBALL,
                InputManagerService.BTN_MOUSE);
        int volumeDownState = mInputManager.getKeyCodeState(-1, InputDevice.SOURCE_ANY,
                KeyEvent.KEYCODE_VOLUME_DOWN);
        mSafeMode = menuState > 0 || sState > 0 || dpadState > 0 || trackballState > 0
                || volumeDownState > 0;
        try {
            if (SystemProperties.getInt(ShutdownThread.REBOOT_SAFEMODE_PROPERTY, 0) != 0
                    || SystemProperties.getInt(ShutdownThread.RO_SAFEMODE_PROPERTY, 0) != 0) {
                mSafeMode = true;
                SystemProperties.set(ShutdownThread.REBOOT_SAFEMODE_PROPERTY, "");
            }
        } catch (IllegalArgumentException e) {
        }
        if (mSafeMode) {
            Log.i(TAG_WM, "SAFE MODE ENABLED (menu=" + menuState + " s=" + sState
                    + " dpad=" + dpadState + " trackball=" + trackballState + ")");
            SystemProperties.set(ShutdownThread.RO_SAFEMODE_PROPERTY, "1");
        } else {
            Log.i(TAG_WM, "SAFE MODE not enabled");
        }
        mPolicy.setSafeMode(mSafeMode);
        return mSafeMode;
    }

    public void displayReady() {
        for (Display display : mDisplays) {
            displayReady(display.getDisplayId());
        }

        synchronized(mWindowMap) {
            final DisplayContent displayContent = getDefaultDisplayContentLocked();
            if (mMaxUiWidth > 0) {
                displayContent.setMaxUiWidth(mMaxUiWidth);
            }
            readForcedDisplayPropertiesLocked(displayContent);
            mDisplayReady = true;
        }

        try {
            mActivityManager.updateConfiguration(null);
        } catch (RemoteException e) {
        }

        synchronized(mWindowMap) {
            mIsTouchDevice = mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TOUCHSCREEN);
            configureDisplayPolicyLocked(getDefaultDisplayContentLocked());
        }

        try {
            mActivityManager.updateConfiguration(null);
        } catch (RemoteException e) {
        }

        updateCircularDisplayMaskIfNeeded();
    }

    private void displayReady(int displayId) {
        synchronized(mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null) {
                mAnimator.addDisplayLocked(displayId);
                displayContent.initializeDisplayBaseInfo();
            }
        }
    }

    public void systemReady() {
        mPolicy.systemReady();
        mTaskSnapshotController.systemReady();
        mHasWideColorGamutSupport = queryWideColorGamutSupport();
    }

    private static boolean queryWideColorGamutSupport() {
        try {
            ISurfaceFlingerConfigs surfaceFlinger = ISurfaceFlingerConfigs.getService();
            OptionalBool hasWideColor = surfaceFlinger.hasWideColorDisplay();
            if (hasWideColor != null) {
                return hasWideColor.value;
            }
        } catch (RemoteException e) {
            // Ignore, we're in big trouble if we can't talk to SurfaceFlinger's config store
        }
        return false;
    }

    // -------------------------------------------------------------
    // Async Handler
    // -------------------------------------------------------------

    final class H extends android.os.Handler {
        public static final int REPORT_FOCUS_CHANGE = 2;
        public static final int REPORT_LOSING_FOCUS = 3;
        public static final int WINDOW_FREEZE_TIMEOUT = 11;

        public static final int APP_TRANSITION_TIMEOUT = 13;
        public static final int PERSIST_ANIMATION_SCALE = 14;
        public static final int FORCE_GC = 15;
        public static final int ENABLE_SCREEN = 16;
        public static final int APP_FREEZE_TIMEOUT = 17;
        public static final int SEND_NEW_CONFIGURATION = 18;
        public static final int REPORT_WINDOWS_CHANGE = 19;
        public static final int DRAG_START_TIMEOUT = 20;
        public static final int DRAG_END_TIMEOUT = 21;
        public static final int REPORT_HARD_KEYBOARD_STATUS_CHANGE = 22;
        public static final int BOOT_TIMEOUT = 23;
        public static final int WAITING_FOR_DRAWN_TIMEOUT = 24;
        public static final int SHOW_STRICT_MODE_VIOLATION = 25;
        public static final int DO_ANIMATION_CALLBACK = 26;

        public static final int CLIENT_FREEZE_TIMEOUT = 30;
        public static final int TAP_OUTSIDE_TASK = 31;
        public static final int NOTIFY_ACTIVITY_DRAWN = 32;

        public static final int ALL_WINDOWS_DRAWN = 33;

        public static final int NEW_ANIMATOR_SCALE = 34;

        public static final int SHOW_CIRCULAR_DISPLAY_MASK = 35;
        public static final int SHOW_EMULATOR_DISPLAY_OVERLAY = 36;

        public static final int CHECK_IF_BOOT_ANIMATION_FINISHED = 37;
        public static final int RESET_ANR_MESSAGE = 38;
        public static final int WALLPAPER_DRAW_PENDING_TIMEOUT = 39;

        public static final int FINISH_TASK_POSITIONING = 40;

        public static final int UPDATE_DOCKED_STACK_DIVIDER = 41;

        public static final int TEAR_DOWN_DRAG_AND_DROP_INPUT = 44;

        public static final int WINDOW_REPLACEMENT_TIMEOUT = 46;

        public static final int NOTIFY_APP_TRANSITION_STARTING = 47;
        public static final int NOTIFY_APP_TRANSITION_CANCELLED = 48;
        public static final int NOTIFY_APP_TRANSITION_FINISHED = 49;
        public static final int UPDATE_ANIMATION_SCALE = 51;
        public static final int WINDOW_HIDE_TIMEOUT = 52;
        public static final int NOTIFY_DOCKED_STACK_MINIMIZED_CHANGED = 53;
        public static final int SEAMLESS_ROTATION_TIMEOUT = 54;
        public static final int RESTORE_POINTER_ICON = 55;
        public static final int NOTIFY_KEYGUARD_FLAGS_CHANGED = 56;
        public static final int NOTIFY_KEYGUARD_TRUSTED_CHANGED = 57;
        public static final int SET_HAS_OVERLAY_UI = 58;

        /**
         * Used to denote that an integer field in a message will not be used.
         */
        public static final int UNUSED = 0;

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_WINDOW_TRACE) {
                Slog.v(TAG_WM, "handleMessage: entry what=" + msg.what);
            }
            switch (msg.what) {
                case REPORT_FOCUS_CHANGE: {
                    WindowState lastFocus;
                    WindowState newFocus;

                    AccessibilityController accessibilityController = null;

                    synchronized(mWindowMap) {
                        // TODO(multidisplay): Accessibility supported only of default desiplay.
                        if (mAccessibilityController != null && getDefaultDisplayContentLocked()
                                .getDisplayId() == DEFAULT_DISPLAY) {
                            accessibilityController = mAccessibilityController;
                        }

                        lastFocus = mLastFocus;
                        newFocus = mCurrentFocus;
                        if (lastFocus == newFocus) {
                            // Focus is not changing, so nothing to do.
                            return;
                        }
                        mLastFocus = newFocus;
                        if (DEBUG_FOCUS_LIGHT) Slog.i(TAG_WM, "Focus moving from " + lastFocus +
                                " to " + newFocus);
                        if (newFocus != null && lastFocus != null
                                && !newFocus.isDisplayedLw()) {
                            //Slog.i(TAG_WM, "Delaying loss of focus...");
                            mLosingFocus.add(lastFocus);
                            lastFocus = null;
                        }
                    }

                    // First notify the accessibility manager for the change so it has
                    // the windows before the newly focused one starts firing eventgs.
                    if (accessibilityController != null) {
                        accessibilityController.onWindowFocusChangedNotLocked();
                    }

                    //System.out.println("Changing focus from " + lastFocus
                    //                   + " to " + newFocus);
                    if (newFocus != null) {
                        if (DEBUG_FOCUS_LIGHT) Slog.i(TAG_WM, "Gaining focus: " + newFocus);
                        newFocus.reportFocusChangedSerialized(true, mInTouchMode);
                        notifyFocusChanged();
                    }

                    if (lastFocus != null) {
                        if (DEBUG_FOCUS_LIGHT) Slog.i(TAG_WM, "Losing focus: " + lastFocus);
                        lastFocus.reportFocusChangedSerialized(false, mInTouchMode);
                    }
                } break;

                case REPORT_LOSING_FOCUS: {
                    ArrayList<WindowState> losers;

                    synchronized(mWindowMap) {
                        losers = mLosingFocus;
                        mLosingFocus = new ArrayList<WindowState>();
                    }

                    final int N = losers.size();
                    for (int i=0; i<N; i++) {
                        if (DEBUG_FOCUS_LIGHT) Slog.i(TAG_WM, "Losing delayed focus: " +
                                losers.get(i));
                        losers.get(i).reportFocusChangedSerialized(false, mInTouchMode);
                    }
                } break;

                case WINDOW_FREEZE_TIMEOUT: {
                    // TODO(multidisplay): Can non-default displays rotate?
                    synchronized (mWindowMap) {
                        getDefaultDisplayContentLocked().onWindowFreezeTimeout();
                    }
                    break;
                }

                case APP_TRANSITION_TIMEOUT: {
                    synchronized (mWindowMap) {
                        if (mAppTransition.isTransitionSet() || !mOpeningApps.isEmpty()
                                    || !mClosingApps.isEmpty()) {
                            if (DEBUG_APP_TRANSITIONS) Slog.v(TAG_WM, "*** APP TRANSITION TIMEOUT."
                                    + " isTransitionSet()=" + mAppTransition.isTransitionSet()
                                    + " mOpeningApps.size()=" + mOpeningApps.size()
                                    + " mClosingApps.size()=" + mClosingApps.size());
                            mAppTransition.setTimeout();
                            mWindowPlacerLocked.performSurfacePlacement();
                        }
                    }
                    break;
                }

                case PERSIST_ANIMATION_SCALE: {
                    Settings.Global.putFloat(mContext.getContentResolver(),
                            Settings.Global.WINDOW_ANIMATION_SCALE, mWindowAnimationScaleSetting);
                    Settings.Global.putFloat(mContext.getContentResolver(),
                            Settings.Global.TRANSITION_ANIMATION_SCALE,
                            mTransitionAnimationScaleSetting);
                    Settings.Global.putFloat(mContext.getContentResolver(),
                            Settings.Global.ANIMATOR_DURATION_SCALE, mAnimatorDurationScaleSetting);
                    break;
                }

                case UPDATE_ANIMATION_SCALE: {
                    @UpdateAnimationScaleMode
                    final int mode = msg.arg1;
                    switch (mode) {
                        case WINDOW_ANIMATION_SCALE: {
                            mWindowAnimationScaleSetting = Settings.Global.getFloat(
                                    mContext.getContentResolver(),
                                    Settings.Global.WINDOW_ANIMATION_SCALE,
                                    mWindowAnimationScaleSetting);
                            break;
                        }
                        case TRANSITION_ANIMATION_SCALE: {
                            mTransitionAnimationScaleSetting = Settings.Global.getFloat(
                                    mContext.getContentResolver(),
                                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                                    mTransitionAnimationScaleSetting);
                            break;
                        }
                        case ANIMATION_DURATION_SCALE: {
                            mAnimatorDurationScaleSetting = Settings.Global.getFloat(
                                    mContext.getContentResolver(),
                                    Settings.Global.ANIMATOR_DURATION_SCALE,
                                    mAnimatorDurationScaleSetting);
                            dispatchNewAnimatorScaleLocked(null);
                            break;
                        }
                    }
                    break;
                }

                case FORCE_GC: {
                    synchronized (mWindowMap) {
                        // Since we're holding both mWindowMap and mAnimator we don't need to
                        // hold mAnimator.mLayoutToAnim.
                        if (mAnimator.isAnimating() || mAnimator.isAnimationScheduled()) {
                            // If we are animating, don't do the gc now but
                            // delay a bit so we don't interrupt the animation.
                            sendEmptyMessageDelayed(H.FORCE_GC, 2000);
                            return;
                        }
                        // If we are currently rotating the display, it will
                        // schedule a new message when done.
                        if (mDisplayFrozen) {
                            return;
                        }
                    }
                    Runtime.getRuntime().gc();
                    break;
                }

                case ENABLE_SCREEN: {
                    performEnableScreen();
                    break;
                }

                case APP_FREEZE_TIMEOUT: {
                    synchronized (mWindowMap) {
                        Slog.w(TAG_WM, "App freeze timeout expired.");
                        mWindowsFreezingScreen = WINDOWS_FREEZING_SCREENS_TIMEOUT;
                        for (int i = mAppFreezeListeners.size() - 1; i >=0 ; --i) {
                            mAppFreezeListeners.get(i).onAppFreezeTimeout();
                        }
                    }
                    break;
                }

                case CLIENT_FREEZE_TIMEOUT: {
                    synchronized (mWindowMap) {
                        if (mClientFreezingScreen) {
                            mClientFreezingScreen = false;
                            mLastFinishedFreezeSource = "client-timeout";
                            stopFreezingDisplayLocked();
                        }
                    }
                    break;
                }

                case SEND_NEW_CONFIGURATION: {
                    removeMessages(SEND_NEW_CONFIGURATION, msg.obj);
                    final int displayId = (Integer) msg.obj;
                    if (mRoot.getDisplayContent(displayId) != null) {
                        sendNewConfiguration(displayId);
                    } else {
                        // Message could come after display has already been removed.
                        if (DEBUG_CONFIGURATION) {
                            Slog.w(TAG, "Trying to send configuration to non-existing displayId="
                                    + displayId);
                        }
                    }
                    break;
                }

                case REPORT_WINDOWS_CHANGE: {
                    if (mWindowsChanged) {
                        synchronized (mWindowMap) {
                            mWindowsChanged = false;
                        }
                        notifyWindowsChanged();
                    }
                    break;
                }

                case DRAG_START_TIMEOUT: {
                    IBinder win = (IBinder)msg.obj;
                    if (DEBUG_DRAG) {
                        Slog.w(TAG_WM, "Timeout starting drag by win " + win);
                    }
                    synchronized (mWindowMap) {
                        // !!! TODO: ANR the app that has failed to start the drag in time
                        if (mDragState != null) {
                            mDragState.unregister();
                            mDragState.reset();
                            mDragState = null;
                        }
                    }
                    break;
                }

                case DRAG_END_TIMEOUT: {
                    IBinder win = (IBinder)msg.obj;
                    if (DEBUG_DRAG) {
                        Slog.w(TAG_WM, "Timeout ending drag to win " + win);
                    }
                    synchronized (mWindowMap) {
                        // !!! TODO: ANR the drag-receiving app
                        if (mDragState != null) {
                            mDragState.mDragResult = false;
                            mDragState.endDragLw();
                        }
                    }
                    break;
                }

                case TEAR_DOWN_DRAG_AND_DROP_INPUT: {
                    if (DEBUG_DRAG) Slog.d(TAG_WM, "Drag ending; tearing down input channel");
                    DragState.InputInterceptor interceptor = (DragState.InputInterceptor) msg.obj;
                    if (interceptor != null) {
                        synchronized (mWindowMap) {
                            interceptor.tearDown();
                        }
                    }
                }
                break;

                case REPORT_HARD_KEYBOARD_STATUS_CHANGE: {
                    notifyHardKeyboardStatusChange();
                    break;
                }

                case BOOT_TIMEOUT: {
                    performBootTimeout();
                    break;
                }

                case WAITING_FOR_DRAWN_TIMEOUT: {
                    Runnable callback = null;
                    synchronized (mWindowMap) {
                        Slog.w(TAG_WM, "Timeout waiting for drawn: undrawn=" + mWaitingForDrawn);
                        mWaitingForDrawn.clear();
                        callback = mWaitingForDrawnCallback;
                        mWaitingForDrawnCallback = null;
                    }
                    if (callback != null) {
                        callback.run();
                    }
                    break;
                }

                case SHOW_STRICT_MODE_VIOLATION: {
                    showStrictModeViolation(msg.arg1, msg.arg2);
                    break;
                }

                case SHOW_CIRCULAR_DISPLAY_MASK: {
                    showCircularMask(msg.arg1 == 1);
                    break;
                }

                case SHOW_EMULATOR_DISPLAY_OVERLAY: {
                    showEmulatorDisplayOverlay();
                    break;
                }

                case DO_ANIMATION_CALLBACK: {
                    try {
                        ((IRemoteCallback)msg.obj).sendResult(null);
                    } catch (RemoteException e) {
                    }
                    break;
                }

                case TAP_OUTSIDE_TASK: {
                    handleTapOutsideTask((DisplayContent)msg.obj, msg.arg1, msg.arg2);
                }
                break;

                case FINISH_TASK_POSITIONING: {
                    finishPositioning();
                }
                break;

                case NOTIFY_ACTIVITY_DRAWN:
                    try {
                        mActivityManager.notifyActivityDrawn((IBinder) msg.obj);
                    } catch (RemoteException e) {
                    }
                    break;
                case ALL_WINDOWS_DRAWN: {
                    Runnable callback;
                    synchronized (mWindowMap) {
                        callback = mWaitingForDrawnCallback;
                        mWaitingForDrawnCallback = null;
                    }
                    if (callback != null) {
                        callback.run();
                    }
                    break;
                }
                case NEW_ANIMATOR_SCALE: {
                    float scale = getCurrentAnimatorScale();
                    ValueAnimator.setDurationScale(scale);
                    Session session = (Session)msg.obj;
                    if (session != null) {
                        try {
                            session.mCallback.onAnimatorScaleChanged(scale);
                        } catch (RemoteException e) {
                        }
                    } else {
                        ArrayList<IWindowSessionCallback> callbacks
                                = new ArrayList<IWindowSessionCallback>();
                        synchronized (mWindowMap) {
                            for (int i=0; i<mSessions.size(); i++) {
                                callbacks.add(mSessions.valueAt(i).mCallback);
                            }

                        }
                        for (int i=0; i<callbacks.size(); i++) {
                            try {
                                callbacks.get(i).onAnimatorScaleChanged(scale);
                            } catch (RemoteException e) {
                            }
                        }
                    }
                }
                break;
                case CHECK_IF_BOOT_ANIMATION_FINISHED: {
                    final boolean bootAnimationComplete;
                    synchronized (mWindowMap) {
                        if (DEBUG_BOOT) Slog.i(TAG_WM, "CHECK_IF_BOOT_ANIMATION_FINISHED:");
                        bootAnimationComplete = checkBootAnimationCompleteLocked();
                    }
                    if (bootAnimationComplete) {
                        performEnableScreen();
                    }
                }
                break;
                case RESET_ANR_MESSAGE: {
                    synchronized (mWindowMap) {
                        mLastANRState = null;
                    }
                    mAmInternal.clearSavedANRState();
                }
                break;
                case WALLPAPER_DRAW_PENDING_TIMEOUT: {
                    synchronized (mWindowMap) {
                        if (mRoot.mWallpaperController.processWallpaperDrawPendingTimeout()) {
                            mWindowPlacerLocked.performSurfacePlacement();
                        }
                    }
                }
                break;
                case UPDATE_DOCKED_STACK_DIVIDER: {
                    synchronized (mWindowMap) {
                        final DisplayContent displayContent = getDefaultDisplayContentLocked();
                        displayContent.getDockedDividerController().reevaluateVisibility(false);
                        displayContent.adjustForImeIfNeeded();
                    }
                }
                break;
                case WINDOW_REPLACEMENT_TIMEOUT: {
                    synchronized (mWindowMap) {
                        for (int i = mWindowReplacementTimeouts.size() - 1; i >= 0; i--) {
                            final AppWindowToken token = mWindowReplacementTimeouts.get(i);
                            token.onWindowReplacementTimeout();
                        }
                        mWindowReplacementTimeouts.clear();
                    }
                }
                break;
                case NOTIFY_APP_TRANSITION_STARTING: {
                    mAmInternal.notifyAppTransitionStarting((SparseIntArray) msg.obj,
                            msg.getWhen());
                }
                break;
                case NOTIFY_APP_TRANSITION_CANCELLED: {
                    mAmInternal.notifyAppTransitionCancelled();
                }
                break;
                case NOTIFY_APP_TRANSITION_FINISHED: {
                    mAmInternal.notifyAppTransitionFinished();
                }
                break;
                case WINDOW_HIDE_TIMEOUT: {
                    final WindowState window = (WindowState) msg.obj;
                    synchronized(mWindowMap) {
                        // TODO: This is all about fixing b/21693547
                        // where partially initialized Toasts get stuck
                        // around and keep the screen on. We'd like
                        // to just remove the toast...but this can cause clients
                        // who miss the timeout due to normal circumstances (e.g.
                        // running under debugger) to crash (b/29105388). The windows will
                        // eventually be removed when the client process finishes.
                        // The best we can do for now is remove the FLAG_KEEP_SCREEN_ON
                        // and prevent the symptoms of b/21693547. Since apps don't
                        // support windows being removed under them we hide the window
                        // and it will be removed when the app dies.
                        window.mAttrs.flags &= ~FLAG_KEEP_SCREEN_ON;
                        window.hidePermanentlyLw();
                        window.setDisplayLayoutNeeded();
                        mWindowPlacerLocked.performSurfacePlacement();
                    }
                }
                break;
                case NOTIFY_DOCKED_STACK_MINIMIZED_CHANGED: {
                    mAmInternal.notifyDockedStackMinimizedChanged(msg.arg1 == 1);
                }
                break;
                case RESTORE_POINTER_ICON: {
                    synchronized (mWindowMap) {
                        restorePointerIconLocked((DisplayContent)msg.obj, msg.arg1, msg.arg2);
                    }
                }
                break;
                case SEAMLESS_ROTATION_TIMEOUT: {
                    // Rotation only supported on primary display.
                    // TODO(multi-display)
                    synchronized(mWindowMap) {
                        final DisplayContent dc = getDefaultDisplayContentLocked();
                        dc.onSeamlessRotationTimeout();
                    }
                }
                break;
                case NOTIFY_KEYGUARD_FLAGS_CHANGED: {
                    mAmInternal.notifyKeyguardFlagsChanged((Runnable) msg.obj);
                }
                break;
                case NOTIFY_KEYGUARD_TRUSTED_CHANGED: {
                    mAmInternal.notifyKeyguardTrustedChanged();
                }
                break;
                case SET_HAS_OVERLAY_UI: {
                    mAmInternal.setHasOverlayUi(msg.arg1, msg.arg2 == 1);
                }
                break;
            }
            if (DEBUG_WINDOW_TRACE) {
                Slog.v(TAG_WM, "handleMessage: exit");
            }
        }
    }

    void destroyPreservedSurfaceLocked() {
        for (int i = mDestroyPreservedSurface.size() - 1; i >= 0 ; i--) {
            final WindowState w = mDestroyPreservedSurface.get(i);
            w.mWinAnimator.destroyPreservedSurfaceLocked();
        }
        mDestroyPreservedSurface.clear();
    }

    void stopUsingSavedSurfaceLocked() {
        for (int i = mFinishedEarlyAnim.size() - 1; i >= 0 ; i--) {
            final AppWindowToken wtoken = mFinishedEarlyAnim.get(i);
            wtoken.stopUsingSavedSurfaceLocked();
        }
        mFinishedEarlyAnim.clear();
    }

    // -------------------------------------------------------------
    // IWindowManager API
    // -------------------------------------------------------------

    @Override
    public IWindowSession openSession(IWindowSessionCallback callback, IInputMethodClient client,
            IInputContext inputContext) {
        if (client == null) throw new IllegalArgumentException("null client");
        if (inputContext == null) throw new IllegalArgumentException("null inputContext");
        Session session = new Session(this, callback, client, inputContext);
        return session;
    }

    @Override
    public boolean inputMethodClientHasFocus(IInputMethodClient client) {
        synchronized (mWindowMap) {
            // TODO: multi-display
            if (getDefaultDisplayContentLocked().inputMethodClientHasFocus(client)) {
                return true;
            }

            // Okay, how about this...  what is the current focus?
            // It seems in some cases we may not have moved the IM
            // target window, such as when it was in a pop-up window,
            // so let's also look at the current focus.  (An example:
            // go to Gmail, start searching so the keyboard goes up,
            // press home.  Sometimes the IME won't go down.)
            // Would be nice to fix this more correctly, but it's
            // way at the end of a release, and this should be good enough.
            if (mCurrentFocus != null && mCurrentFocus.mSession.mClient != null
                    && mCurrentFocus.mSession.mClient.asBinder() == client.asBinder()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void getInitialDisplaySize(int displayId, Point size) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null && displayContent.hasAccess(Binder.getCallingUid())) {
                size.x = displayContent.mInitialDisplayWidth;
                size.y = displayContent.mInitialDisplayHeight;
            }
        }
    }

    @Override
    public void getBaseDisplaySize(int displayId, Point size) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null && displayContent.hasAccess(Binder.getCallingUid())) {
                size.x = displayContent.mBaseDisplayWidth;
                size.y = displayContent.mBaseDisplayHeight;
            }
        }
    }

    @Override
    public void setForcedDisplaySize(int displayId, int width, int height) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        if (displayId != DEFAULT_DISPLAY) {
            throw new IllegalArgumentException("Can only set the default display");
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                // Set some sort of reasonable bounds on the size of the display that we
                // will try to emulate.
                final int MIN_WIDTH = 200;
                final int MIN_HEIGHT = 200;
                final int MAX_SCALE = 2;
                final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
                if (displayContent != null) {
                    width = Math.min(Math.max(width, MIN_WIDTH),
                            displayContent.mInitialDisplayWidth * MAX_SCALE);
                    height = Math.min(Math.max(height, MIN_HEIGHT),
                            displayContent.mInitialDisplayHeight * MAX_SCALE);
                    setForcedDisplaySizeLocked(displayContent, width, height);
                    Settings.Global.putString(mContext.getContentResolver(),
                            Settings.Global.DISPLAY_SIZE_FORCED, width + "," + height);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void setForcedDisplayScalingMode(int displayId, int mode) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        if (displayId != DEFAULT_DISPLAY) {
            throw new IllegalArgumentException("Can only set the default display");
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
                if (displayContent != null) {
                    if (mode < 0 || mode > 1) {
                        mode = 0;
                    }
                    setForcedDisplayScalingModeLocked(displayContent, mode);
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.DISPLAY_SCALING_FORCE, mode);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void setForcedDisplayScalingModeLocked(DisplayContent displayContent, int mode) {
        Slog.i(TAG_WM, "Using display scaling mode: " + (mode == 0 ? "auto" : "off"));
        displayContent.mDisplayScalingDisabled = (mode != 0);
        reconfigureDisplayLocked(displayContent);
    }

    private void readForcedDisplayPropertiesLocked(final DisplayContent displayContent) {
        // Display size.
        String sizeStr = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.DISPLAY_SIZE_FORCED);
        if (sizeStr == null || sizeStr.length() == 0) {
            sizeStr = SystemProperties.get(SIZE_OVERRIDE, null);
        }
        if (sizeStr != null && sizeStr.length() > 0) {
            final int pos = sizeStr.indexOf(',');
            if (pos > 0 && sizeStr.lastIndexOf(',') == pos) {
                int width, height;
                try {
                    width = Integer.parseInt(sizeStr.substring(0, pos));
                    height = Integer.parseInt(sizeStr.substring(pos+1));
                    if (displayContent.mBaseDisplayWidth != width
                            || displayContent.mBaseDisplayHeight != height) {
                        Slog.i(TAG_WM, "FORCED DISPLAY SIZE: " + width + "x" + height);
                        displayContent.updateBaseDisplayMetrics(width, height,
                                displayContent.mBaseDisplayDensity);
                    }
                } catch (NumberFormatException ex) {
                }
            }
        }

        // Display density.
        final int density = getForcedDisplayDensityForUserLocked(mCurrentUserId);
        if (density != 0) {
            displayContent.mBaseDisplayDensity = density;
        }

        // Display scaling mode.
        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DISPLAY_SCALING_FORCE, 0);
        if (mode != 0) {
            Slog.i(TAG_WM, "FORCED DISPLAY SCALING DISABLED");
            displayContent.mDisplayScalingDisabled = true;
        }
    }

    // displayContent must not be null
    private void setForcedDisplaySizeLocked(DisplayContent displayContent, int width, int height) {
        Slog.i(TAG_WM, "Using new display size: " + width + "x" + height);
        displayContent.updateBaseDisplayMetrics(width, height, displayContent.mBaseDisplayDensity);
        reconfigureDisplayLocked(displayContent);
    }

    @Override
    public void clearForcedDisplaySize(int displayId) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        if (displayId != DEFAULT_DISPLAY) {
            throw new IllegalArgumentException("Can only set the default display");
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
                if (displayContent != null) {
                    setForcedDisplaySizeLocked(displayContent, displayContent.mInitialDisplayWidth,
                            displayContent.mInitialDisplayHeight);
                    Settings.Global.putString(mContext.getContentResolver(),
                            Settings.Global.DISPLAY_SIZE_FORCED, "");
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public int getInitialDisplayDensity(int displayId) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null && displayContent.hasAccess(Binder.getCallingUid())) {
                return displayContent.mInitialDisplayDensity;
            }
        }
        return -1;
    }

    @Override
    public int getBaseDisplayDensity(int displayId) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null && displayContent.hasAccess(Binder.getCallingUid())) {
                return displayContent.mBaseDisplayDensity;
            }
        }
        return -1;
    }

    @Override
    public void setForcedDisplayDensityForUser(int displayId, int density, int userId) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        if (displayId != DEFAULT_DISPLAY) {
            throw new IllegalArgumentException("Can only set the default display");
        }

        final int targetUserId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), userId, false, true, "setForcedDisplayDensityForUser",
                null);
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
                if (displayContent != null && mCurrentUserId == targetUserId) {
                    setForcedDisplayDensityLocked(displayContent, density);
                }
                Settings.Secure.putStringForUser(mContext.getContentResolver(),
                        Settings.Secure.DISPLAY_DENSITY_FORCED,
                        Integer.toString(density), targetUserId);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void clearForcedDisplayDensityForUser(int displayId, int userId) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        if (displayId != DEFAULT_DISPLAY) {
            throw new IllegalArgumentException("Can only set the default display");
        }

        final int callingUserId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), userId, false, true, "clearForcedDisplayDensityForUser",
                null);
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
                if (displayContent != null && mCurrentUserId == callingUserId) {
                    setForcedDisplayDensityLocked(displayContent,
                            displayContent.mInitialDisplayDensity);
                }
                Settings.Secure.putStringForUser(mContext.getContentResolver(),
                        Settings.Secure.DISPLAY_DENSITY_FORCED, "", callingUserId);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * @param userId the ID of the user
     * @return the forced display density for the specified user, if set, or
     *         {@code 0} if not set
     */
    private int getForcedDisplayDensityForUserLocked(int userId) {
        String densityStr = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.DISPLAY_DENSITY_FORCED, userId);
        if (densityStr == null || densityStr.length() == 0) {
            densityStr = SystemProperties.get(DENSITY_OVERRIDE, null);
        }
        if (densityStr != null && densityStr.length() > 0) {
            try {
                return Integer.parseInt(densityStr);
            } catch (NumberFormatException ex) {
            }
        }
        return 0;
    }

    /**
     * Forces the given display to the use the specified density.
     *
     * @param displayContent the display to modify
     * @param density the density in DPI to use
     */
    private void setForcedDisplayDensityLocked(@NonNull DisplayContent displayContent,
            int density) {
        displayContent.mBaseDisplayDensity = density;
        reconfigureDisplayLocked(displayContent);
    }

    void reconfigureDisplayLocked(@NonNull DisplayContent displayContent) {
        if (!mDisplayReady) {
            return;
        }
        configureDisplayPolicyLocked(displayContent);
        displayContent.setLayoutNeeded();

        final int displayId = displayContent.getDisplayId();
        boolean configChanged = updateOrientationFromAppTokensLocked(false /* inTransaction */,
                displayId);
        final Configuration currentDisplayConfig = displayContent.getConfiguration();
        mTempConfiguration.setTo(currentDisplayConfig);
        displayContent.computeScreenConfiguration(mTempConfiguration);
        configChanged |= currentDisplayConfig.diff(mTempConfiguration) != 0;

        if (configChanged) {
            mWaitingForConfig = true;
            startFreezingDisplayLocked(false /* inTransaction */, 0 /* exitAnim */,
                    0 /* enterAnim */, displayContent);
            mH.obtainMessage(H.SEND_NEW_CONFIGURATION, displayId).sendToTarget();
        }

        mWindowPlacerLocked.performSurfacePlacement();
    }

    void configureDisplayPolicyLocked(DisplayContent displayContent) {
        mPolicy.setInitialDisplaySize(displayContent.getDisplay(),
                displayContent.mBaseDisplayWidth,
                displayContent.mBaseDisplayHeight,
                displayContent.mBaseDisplayDensity);

        DisplayInfo displayInfo = displayContent.getDisplayInfo();
        mPolicy.setDisplayOverscan(displayContent.getDisplay(),
                displayInfo.overscanLeft, displayInfo.overscanTop,
                displayInfo.overscanRight, displayInfo.overscanBottom);
    }

    /**
     * Get an array with display ids ordered by focus priority - last items should be given
     * focus first. Sparse array just maps position to displayId.
     */
    // TODO: Maintain display list in focus order in ActivityManager and remove this call.
    public void getDisplaysInFocusOrder(SparseIntArray displaysInFocusOrder) {
        synchronized(mWindowMap) {
            mRoot.getDisplaysInFocusOrder(displaysInFocusOrder);
        }
    }

    @Override
    public void setOverscan(int displayId, int left, int top, int right, int bottom) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Must hold permission " +
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized(mWindowMap) {
                DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
                if (displayContent != null) {
                    setOverscanLocked(displayContent, left, top, right, bottom);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void setOverscanLocked(DisplayContent displayContent,
            int left, int top, int right, int bottom) {
        final DisplayInfo displayInfo = displayContent.getDisplayInfo();
        displayInfo.overscanLeft = left;
        displayInfo.overscanTop = top;
        displayInfo.overscanRight = right;
        displayInfo.overscanBottom = bottom;

        mDisplaySettings.setOverscanLocked(displayInfo.uniqueId, displayInfo.name, left, top,
                right, bottom);
        mDisplaySettings.writeSettingsLocked();

        reconfigureDisplayLocked(displayContent);
    }

    // -------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------

    final WindowState windowForClientLocked(Session session, IWindow client, boolean throwOnError) {
        return windowForClientLocked(session, client.asBinder(), throwOnError);
    }

    final WindowState windowForClientLocked(Session session, IBinder client, boolean throwOnError) {
        WindowState win = mWindowMap.get(client);
        if (localLOGV) Slog.v(TAG_WM, "Looking up client " + client + ": " + win);
        if (win == null) {
            if (throwOnError) {
                throw new IllegalArgumentException(
                        "Requested window " + client + " does not exist");
            }
            Slog.w(TAG_WM, "Failed looking up window callers=" + Debug.getCallers(3));
            return null;
        }
        if (session != null && win.mSession != session) {
            if (throwOnError) {
                throw new IllegalArgumentException("Requested window " + client + " is in session "
                        + win.mSession + ", not " + session);
            }
            Slog.w(TAG_WM, "Failed looking up window callers=" + Debug.getCallers(3));
            return null;
        }

        return win;
    }

    void makeWindowFreezingScreenIfNeededLocked(WindowState w) {
        // If the screen is currently frozen or off, then keep
        // it frozen/off until this window draws at its new
        // orientation.
        if (!w.mToken.okToDisplay() && mWindowsFreezingScreen != WINDOWS_FREEZING_SCREENS_TIMEOUT) {
            if (DEBUG_ORIENTATION) Slog.v(TAG_WM, "Changing surface while display frozen: " + w);
            w.setOrientationChanging(true);
            w.mLastFreezeDuration = 0;
            mRoot.mOrientationChangeComplete = false;
            if (mWindowsFreezingScreen == WINDOWS_FREEZING_SCREENS_NONE) {
                mWindowsFreezingScreen = WINDOWS_FREEZING_SCREENS_ACTIVE;
                // XXX should probably keep timeout from
                // when we first froze the display.
                mH.removeMessages(H.WINDOW_FREEZE_TIMEOUT);
                mH.sendEmptyMessageDelayed(H.WINDOW_FREEZE_TIMEOUT,
                        WINDOW_FREEZE_TIMEOUT_DURATION);
            }
        }
    }

    /**
     * @return bitmap indicating if another pass through layout must be made.
     */
    int handleAnimatingStoppedAndTransitionLocked() {
        int changes = 0;

        mAppTransition.setIdle();

        for (int i = mNoAnimationNotifyOnTransitionFinished.size() - 1; i >= 0; i--) {
            final IBinder token = mNoAnimationNotifyOnTransitionFinished.get(i);
            mAppTransition.notifyAppTransitionFinishedLocked(token);
        }
        mNoAnimationNotifyOnTransitionFinished.clear();

        // TODO: multi-display.
        final DisplayContent dc = getDefaultDisplayContentLocked();

        dc.mWallpaperController.hideDeferredWallpapersIfNeeded();

        dc.onAppTransitionDone();

        changes |= FINISH_LAYOUT_REDO_LAYOUT;
        if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG_WM,
                "Wallpaper layer changed: assigning layers + relayout");
        dc.computeImeTarget(true /* updateImeTarget */);
        mRoot.mWallpaperMayChange = true;
        // Since the window list has been rebuilt, focus might have to be recomputed since the
        // actual order of windows might have changed again.
        mFocusMayChange = true;

        return changes;
    }

    void checkDrawnWindowsLocked() {
        if (mWaitingForDrawn.isEmpty() || mWaitingForDrawnCallback == null) {
            return;
        }
        for (int j = mWaitingForDrawn.size() - 1; j >= 0; j--) {
            WindowState win = mWaitingForDrawn.get(j);
            if (DEBUG_SCREEN_ON) Slog.i(TAG_WM, "Waiting for drawn " + win +
                    ": removed=" + win.mRemoved + " visible=" + win.isVisibleLw() +
                    " mHasSurface=" + win.mHasSurface +
                    " drawState=" + win.mWinAnimator.mDrawState);
            if (win.mRemoved || !win.mHasSurface || !win.mPolicyVisibility) {
                // Window has been removed or hidden; no draw will now happen, so stop waiting.
                if (DEBUG_SCREEN_ON) Slog.w(TAG_WM, "Aborted waiting for drawn: " + win);
                mWaitingForDrawn.remove(win);
            } else if (win.hasDrawnLw()) {
                // Window is now drawn (and shown).
                if (DEBUG_SCREEN_ON) Slog.d(TAG_WM, "Window drawn win=" + win);
                mWaitingForDrawn.remove(win);
            }
        }
        if (mWaitingForDrawn.isEmpty()) {
            if (DEBUG_SCREEN_ON) Slog.d(TAG_WM, "All windows drawn!");
            mH.removeMessages(H.WAITING_FOR_DRAWN_TIMEOUT);
            mH.sendEmptyMessage(H.ALL_WINDOWS_DRAWN);
        }
    }

    void setHoldScreenLocked(final Session newHoldScreen) {
        final boolean hold = newHoldScreen != null;

        if (hold && mHoldingScreenOn != newHoldScreen) {
            mHoldingScreenWakeLock.setWorkSource(new WorkSource(newHoldScreen.mUid));
        }
        mHoldingScreenOn = newHoldScreen;

        final boolean state = mHoldingScreenWakeLock.isHeld();
        if (hold != state) {
            if (hold) {
                if (DEBUG_KEEP_SCREEN_ON) {
                    Slog.d(TAG_KEEP_SCREEN_ON, "Acquiring screen wakelock due to "
                            + mRoot.mHoldScreenWindow);
                }
                mLastWakeLockHoldingWindow = mRoot.mHoldScreenWindow;
                mLastWakeLockObscuringWindow = null;
                mHoldingScreenWakeLock.acquire();
                mPolicy.keepScreenOnStartedLw();
            } else {
                if (DEBUG_KEEP_SCREEN_ON) {
                    Slog.d(TAG_KEEP_SCREEN_ON, "Releasing screen wakelock, obscured by "
                            + mRoot.mObscuringWindow);
                }
                mLastWakeLockHoldingWindow = null;
                mLastWakeLockObscuringWindow = mRoot.mObscuringWindow;
                mPolicy.keepScreenOnStoppedLw();
                mHoldingScreenWakeLock.release();
            }
        }
    }

    void requestTraversal() {
        synchronized (mWindowMap) {
            mWindowPlacerLocked.requestTraversal();
        }
    }

    /** Note that Locked in this case is on mLayoutToAnim */
    void scheduleAnimationLocked() {
        mAnimator.scheduleAnimation();
    }

    // TODO: Move to DisplayContent
    boolean updateFocusedWindowLocked(int mode, boolean updateInputWindows) {
        WindowState newFocus = mRoot.computeFocusedWindow();
        if (mCurrentFocus != newFocus) {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "wmUpdateFocus");
            // This check makes sure that we don't already have the focus
            // change message pending.
            mH.removeMessages(H.REPORT_FOCUS_CHANGE);
            mH.sendEmptyMessage(H.REPORT_FOCUS_CHANGE);
            // TODO(multidisplay): Focused windows on default display only.
            final DisplayContent displayContent = getDefaultDisplayContentLocked();
            boolean imWindowChanged = false;
            if (mInputMethodWindow != null) {
                final WindowState prevTarget = mInputMethodTarget;
                final WindowState newTarget =
                        displayContent.computeImeTarget(true /* updateImeTarget*/);

                imWindowChanged = prevTarget != newTarget;

                if (mode != UPDATE_FOCUS_WILL_ASSIGN_LAYERS
                        && mode != UPDATE_FOCUS_WILL_PLACE_SURFACES) {
                    final int prevImeAnimLayer = mInputMethodWindow.mWinAnimator.mAnimLayer;
                    displayContent.assignWindowLayers(false /* setLayoutNeeded */);
                    imWindowChanged |=
                            prevImeAnimLayer != mInputMethodWindow.mWinAnimator.mAnimLayer;
                }
            }

            if (imWindowChanged) {
                mWindowsChanged = true;
                displayContent.setLayoutNeeded();
                newFocus = mRoot.computeFocusedWindow();
            }

            if (DEBUG_FOCUS_LIGHT || localLOGV) Slog.v(TAG_WM, "Changing focus from " +
                    mCurrentFocus + " to " + newFocus + " Callers=" + Debug.getCallers(4));
            final WindowState oldFocus = mCurrentFocus;
            mCurrentFocus = newFocus;
            mLosingFocus.remove(newFocus);

            if (mCurrentFocus != null) {
                mWinAddedSinceNullFocus.clear();
                mWinRemovedSinceNullFocus.clear();
            }

            int focusChanged = mPolicy.focusChangedLw(oldFocus, newFocus);

            if (imWindowChanged && oldFocus != mInputMethodWindow) {
                // Focus of the input method window changed. Perform layout if needed.
                if (mode == UPDATE_FOCUS_PLACING_SURFACES) {
                    displayContent.performLayout(true /*initial*/,  updateInputWindows);
                    focusChanged &= ~FINISH_LAYOUT_REDO_LAYOUT;
                } else if (mode == UPDATE_FOCUS_WILL_PLACE_SURFACES) {
                    // Client will do the layout, but we need to assign layers
                    // for handleNewWindowLocked() below.
                    displayContent.assignWindowLayers(false /* setLayoutNeeded */);
                }
            }

            if ((focusChanged & FINISH_LAYOUT_REDO_LAYOUT) != 0) {
                // The change in focus caused us to need to do a layout.  Okay.
                displayContent.setLayoutNeeded();
                if (mode == UPDATE_FOCUS_PLACING_SURFACES) {
                    displayContent.performLayout(true /*initial*/, updateInputWindows);
                }
            }

            if (mode != UPDATE_FOCUS_WILL_ASSIGN_LAYERS) {
                // If we defer assigning layers, then the caller is responsible for
                // doing this part.
                mInputMonitor.setInputFocusLw(mCurrentFocus, updateInputWindows);
            }

            displayContent.adjustForImeIfNeeded();

            // We may need to schedule some toast windows to be removed. The toasts for an app that
            // does not have input focus are removed within a timeout to prevent apps to redress
            // other apps' UI.
            displayContent.scheduleToastWindowsTimeoutIfNeededLocked(oldFocus, newFocus);

            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            return true;
        }
        return false;
    }

    void startFreezingDisplayLocked(boolean inTransaction, int exitAnim, int enterAnim) {
        startFreezingDisplayLocked(inTransaction, exitAnim, enterAnim,
                getDefaultDisplayContentLocked());
    }

    void startFreezingDisplayLocked(boolean inTransaction, int exitAnim, int enterAnim,
            DisplayContent displayContent) {
        if (mDisplayFrozen) {
            return;
        }

        if (!displayContent.isReady() || !mPolicy.isScreenOn() || !displayContent.okToAnimate()) {
            // No need to freeze the screen before the display is ready,  if the screen is off,
            // or we can't currently animate.
            return;
        }

        if (DEBUG_ORIENTATION) Slog.d(TAG_WM,
                "startFreezingDisplayLocked: inTransaction=" + inTransaction
                + " exitAnim=" + exitAnim + " enterAnim=" + enterAnim
                + " called by " + Debug.getCallers(8));
        mScreenFrozenLock.acquire();

        mDisplayFrozen = true;
        mDisplayFreezeTime = SystemClock.elapsedRealtime();
        mLastFinishedFreezeSource = null;

        // {@link mDisplayFrozen} prevents us from freezing on multiple displays at the same time.
        // As a result, we only track the display that has initially froze the screen.
        mFrozenDisplayId = displayContent.getDisplayId();

        mInputMonitor.freezeInputDispatchingLw();

        // Clear the last input window -- that is just used for
        // clean transitions between IMEs, and if we are freezing
        // the screen then the whole world is changing behind the scenes.
        mPolicy.setLastInputMethodWindowLw(null, null);

        if (mAppTransition.isTransitionSet()) {
            mAppTransition.freeze();
        }

        if (PROFILE_ORIENTATION) {
            File file = new File("/data/system/frozen");
            Debug.startMethodTracing(file.toString(), 8 * 1024 * 1024);
        }

        // TODO(multidisplay): rotation on non-default displays
        if (CUSTOM_SCREEN_ROTATION && displayContent.isDefaultDisplay) {
            mExitAnimId = exitAnim;
            mEnterAnimId = enterAnim;
            ScreenRotationAnimation screenRotationAnimation =
                    mAnimator.getScreenRotationAnimationLocked(mFrozenDisplayId);
            if (screenRotationAnimation != null) {
                screenRotationAnimation.kill();
            }

            // Check whether the current screen contains any secure content.
            boolean isSecure = displayContent.hasSecureWindowOnScreen();

            displayContent.updateDisplayInfo();
            screenRotationAnimation = new ScreenRotationAnimation(mContext, displayContent,
                    mFxSession, inTransaction, mPolicy.isDefaultOrientationForced(), isSecure,
                    this);
            mAnimator.setScreenRotationAnimationLocked(mFrozenDisplayId,
                    screenRotationAnimation);
        }
    }

    void stopFreezingDisplayLocked() {
        if (!mDisplayFrozen) {
            return;
        }

        if (mWaitingForConfig || mAppsFreezingScreen > 0
                || mWindowsFreezingScreen == WINDOWS_FREEZING_SCREENS_ACTIVE
                || mClientFreezingScreen || !mOpeningApps.isEmpty()) {
            if (DEBUG_ORIENTATION) Slog.d(TAG_WM,
                "stopFreezingDisplayLocked: Returning mWaitingForConfig=" + mWaitingForConfig
                + ", mAppsFreezingScreen=" + mAppsFreezingScreen
                + ", mWindowsFreezingScreen=" + mWindowsFreezingScreen
                + ", mClientFreezingScreen=" + mClientFreezingScreen
                + ", mOpeningApps.size()=" + mOpeningApps.size());
            return;
        }

        if (DEBUG_ORIENTATION) Slog.d(TAG_WM,
                "stopFreezingDisplayLocked: Unfreezing now");

        final DisplayContent displayContent = mRoot.getDisplayContent(mFrozenDisplayId);

        // We must make a local copy of the displayId as it can be potentially overwritten later on
        // in this method. For example, {@link startFreezingDisplayLocked} may be called as a result
        // of update rotation, but we reference the frozen display after that call in this method.
        final int displayId = mFrozenDisplayId;
        mFrozenDisplayId = INVALID_DISPLAY;
        mDisplayFrozen = false;
        mLastDisplayFreezeDuration = (int)(SystemClock.elapsedRealtime() - mDisplayFreezeTime);
        StringBuilder sb = new StringBuilder(128);
        sb.append("Screen frozen for ");
        TimeUtils.formatDuration(mLastDisplayFreezeDuration, sb);
        if (mLastFinishedFreezeSource != null) {
            sb.append(" due to ");
            sb.append(mLastFinishedFreezeSource);
        }
        Slog.i(TAG_WM, sb.toString());
        mH.removeMessages(H.APP_FREEZE_TIMEOUT);
        mH.removeMessages(H.CLIENT_FREEZE_TIMEOUT);
        if (PROFILE_ORIENTATION) {
            Debug.stopMethodTracing();
        }

        boolean updateRotation = false;

        ScreenRotationAnimation screenRotationAnimation =
                mAnimator.getScreenRotationAnimationLocked(displayId);
        if (CUSTOM_SCREEN_ROTATION && screenRotationAnimation != null
                && screenRotationAnimation.hasScreenshot()) {
            if (DEBUG_ORIENTATION) Slog.i(TAG_WM, "**** Dismissing screen rotation animation");
            // TODO(multidisplay): rotation on main screen only.
            DisplayInfo displayInfo = displayContent.getDisplayInfo();
            // Get rotation animation again, with new top window
            boolean isDimming = displayContent.isDimming();
            if (!mPolicy.validateRotationAnimationLw(mExitAnimId, mEnterAnimId, isDimming)) {
                mExitAnimId = mEnterAnimId = 0;
            }
            if (screenRotationAnimation.dismiss(mFxSession, MAX_ANIMATION_DURATION,
                    getTransitionAnimationScaleLocked(), displayInfo.logicalWidth,
                        displayInfo.logicalHeight, mExitAnimId, mEnterAnimId)) {
                scheduleAnimationLocked();
            } else {
                screenRotationAnimation.kill();
                mAnimator.setScreenRotationAnimationLocked(displayId, null);
                updateRotation = true;
            }
        } else {
            if (screenRotationAnimation != null) {
                screenRotationAnimation.kill();
                mAnimator.setScreenRotationAnimationLocked(displayId, null);
            }
            updateRotation = true;
        }

        mInputMonitor.thawInputDispatchingLw();

        boolean configChanged;

        // While the display is frozen we don't re-compute the orientation
        // to avoid inconsistent states.  However, something interesting
        // could have actually changed during that time so re-evaluate it
        // now to catch that.
        configChanged = updateOrientationFromAppTokensLocked(false, displayId);

        // A little kludge: a lot could have happened while the
        // display was frozen, so now that we are coming back we
        // do a gc so that any remote references the system
        // processes holds on others can be released if they are
        // no longer needed.
        mH.removeMessages(H.FORCE_GC);
        mH.sendEmptyMessageDelayed(H.FORCE_GC, 2000);

        mScreenFrozenLock.release();

        if (updateRotation) {
            if (DEBUG_ORIENTATION) Slog.d(TAG_WM, "Performing post-rotate rotation");
            configChanged |= displayContent.updateRotationUnchecked(
                    false /* inTransaction */);
        }

        if (configChanged) {
            mH.obtainMessage(H.SEND_NEW_CONFIGURATION, displayId).sendToTarget();
        }
    }

    static int getPropertyInt(String[] tokens, int index, int defUnits, int defDps,
            DisplayMetrics dm) {
        if (index < tokens.length) {
            String str = tokens[index];
            if (str != null && str.length() > 0) {
                try {
                    int val = Integer.parseInt(str);
                    return val;
                } catch (Exception e) {
                }
            }
        }
        if (defUnits == TypedValue.COMPLEX_UNIT_PX) {
            return defDps;
        }
        int val = (int)TypedValue.applyDimension(defUnits, defDps, dm);
        return val;
    }

    void createWatermarkInTransaction() {
        if (mWatermark != null) {
            return;
        }

        File file = new File("/system/etc/setup.conf");
        FileInputStream in = null;
        DataInputStream ind = null;
        try {
            in = new FileInputStream(file);
            ind = new DataInputStream(in);
            String line = ind.readLine();
            if (line != null) {
                String[] toks = line.split("%");
                if (toks != null && toks.length > 0) {
                    // TODO(multi-display): Show watermarks on secondary displays.
                    final DisplayContent displayContent = getDefaultDisplayContentLocked();
                    mWatermark = new Watermark(displayContent.getDisplay(),
                            displayContent.mRealDisplayMetrics, mFxSession, toks);
                }
            }
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        } finally {
            if (ind != null) {
                try {
                    ind.close();
                } catch (IOException e) {
                }
            } else if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public void setRecentsVisibility(boolean visible) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold permission "
                    + android.Manifest.permission.STATUS_BAR);
        }

        synchronized (mWindowMap) {
            mPolicy.setRecentsVisibilityLw(visible);
        }
    }

    @Override
    public void setPipVisibility(boolean visible) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold permission "
                    + android.Manifest.permission.STATUS_BAR);
        }

        synchronized (mWindowMap) {
            mPolicy.setPipVisibilityLw(visible);
        }
    }

    @Override
    public void statusBarVisibilityChanged(int visibility) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold permission "
                    + android.Manifest.permission.STATUS_BAR);
        }

        synchronized (mWindowMap) {
            mLastStatusBarVisibility = visibility;
            visibility = mPolicy.adjustSystemUiVisibilityLw(visibility);
            updateStatusBarVisibilityLocked(visibility);
        }
    }

    // TODO(multidisplay): StatusBar on multiple screens?
    private boolean updateStatusBarVisibilityLocked(int visibility) {
        if (mLastDispatchedSystemUiVisibility == visibility) {
            return false;
        }
        final int globalDiff = (visibility ^ mLastDispatchedSystemUiVisibility)
                // We are only interested in differences of one of the
                // clearable flags...
                & View.SYSTEM_UI_CLEARABLE_FLAGS
                // ...if it has actually been cleared.
                & ~visibility;

        mLastDispatchedSystemUiVisibility = visibility;
        mInputManager.setSystemUiVisibility(visibility);
        getDefaultDisplayContentLocked().updateSystemUiVisibility(visibility, globalDiff);
        return true;
    }

    @Override
    public void reevaluateStatusBarVisibility() {
        synchronized (mWindowMap) {
            int visibility = mPolicy.adjustSystemUiVisibilityLw(mLastStatusBarVisibility);
            if (updateStatusBarVisibilityLocked(visibility)) {
                mWindowPlacerLocked.requestTraversal();
            }
        }
    }

    /**
     * Used by ActivityManager to determine where to position an app with aspect ratio shorter then
     * the screen is.
     * @see WindowManagerPolicy#getNavBarPosition()
     */
    public int getNavBarPosition() {
        synchronized (mWindowMap) {
            // Perform layout if it was scheduled before to make sure that we get correct nav bar
            // position when doing rotations.
            final DisplayContent defaultDisplayContent = getDefaultDisplayContentLocked();
            defaultDisplayContent.performLayout(false /* initial */,
                    false /* updateInputWindows */);
            return mPolicy.getNavBarPosition();
        }
    }

    @Override
    public WindowManagerPolicy.InputConsumer createInputConsumer(Looper looper, String name,
            InputEventReceiver.Factory inputEventReceiverFactory) {
        synchronized (mWindowMap) {
            return mInputMonitor.createInputConsumer(looper, name, inputEventReceiverFactory);
        }
    }

    @Override
    public void createInputConsumer(String name, InputChannel inputChannel) {
        synchronized (mWindowMap) {
            mInputMonitor.createInputConsumer(name, inputChannel);
        }
    }

    @Override
    public boolean destroyInputConsumer(String name) {
        synchronized (mWindowMap) {
            return mInputMonitor.destroyInputConsumer(name);
        }
    }

    @Override
    public Region getCurrentImeTouchRegion() {
        if (mContext.checkCallingOrSelfPermission(RESTRICTED_VR_ACCESS) != PERMISSION_GRANTED) {
            throw new SecurityException("getCurrentImeTouchRegion is restricted to VR services");
        }
        synchronized (mWindowMap) {
            final Region r = new Region();
            if (mInputMethodWindow != null) {
                mInputMethodWindow.getTouchableRegion(r);
            }
            return r;
        }
    }

    @Override
    public boolean hasNavigationBar() {
        return mPolicy.hasNavigationBar();
    }

    public void sendCustomAction(Intent intent) {
        mPolicy.sendCustomAction(intent);
    }

    @Override
    public boolean hasPermanentMenuKey() {
        return mPolicy.hasPermanentMenuKey();
    }

    @Override
    public boolean needsNavigationBar() {
        return mPolicy.needsNavigationBar();
    }

    @Override
    public void lockNow(Bundle options) {
        mPolicy.lockNow(options);
    }

    public void showRecentApps(boolean fromHome) {
        mPolicy.showRecentApps(fromHome);
    }

    @Override
    public boolean isSafeModeEnabled() {
        return mSafeMode;
    }

    @Override
    public boolean clearWindowContentFrameStats(IBinder token) {
        if (!checkCallingPermission(Manifest.permission.FRAME_STATS,
                "clearWindowContentFrameStats()")) {
            throw new SecurityException("Requires FRAME_STATS permission");
        }
        synchronized (mWindowMap) {
            WindowState windowState = mWindowMap.get(token);
            if (windowState == null) {
                return false;
            }
            WindowSurfaceController surfaceController = windowState.mWinAnimator.mSurfaceController;
            if (surfaceController == null) {
                return false;
            }
            return surfaceController.clearWindowContentFrameStats();
        }
    }

    @Override
    public WindowContentFrameStats getWindowContentFrameStats(IBinder token) {
        if (!checkCallingPermission(Manifest.permission.FRAME_STATS,
                "getWindowContentFrameStats()")) {
            throw new SecurityException("Requires FRAME_STATS permission");
        }
        synchronized (mWindowMap) {
            WindowState windowState = mWindowMap.get(token);
            if (windowState == null) {
                return null;
            }
            WindowSurfaceController surfaceController = windowState.mWinAnimator.mSurfaceController;
            if (surfaceController == null) {
                return null;
            }
            if (mTempWindowRenderStats == null) {
                mTempWindowRenderStats = new WindowContentFrameStats();
            }
            WindowContentFrameStats stats = mTempWindowRenderStats;
            if (!surfaceController.getWindowContentFrameStats(stats)) {
                return null;
            }
            return stats;
        }
    }

    public void notifyAppRelaunching(IBinder token) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindow = mRoot.getAppWindowToken(token);
            if (appWindow != null) {
                appWindow.startRelaunching();
            }
        }
    }

    public void notifyAppRelaunchingFinished(IBinder token) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindow = mRoot.getAppWindowToken(token);
            if (appWindow != null) {
                appWindow.finishRelaunching();
            }
        }
    }

    public void notifyAppRelaunchesCleared(IBinder token) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindow = mRoot.getAppWindowToken(token);
            if (appWindow != null) {
                appWindow.clearRelaunching();
            }
        }
    }

    public void notifyAppResumedFinished(IBinder token) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindow = mRoot.getAppWindowToken(token);
            if (appWindow != null) {
                mUnknownAppVisibilityController.notifyAppResumedFinished(appWindow);
            }
        }
    }

    /**
     * Called when a task has been removed from the recent tasks list.
     * <p>
     * Note: This doesn't go through {@link TaskWindowContainerController} yet as the window
     * container may not exist when this happens.
     */
    public void notifyTaskRemovedFromRecents(int taskId, int userId) {
        synchronized (mWindowMap) {
            mTaskSnapshotController.notifyTaskRemovedFromRecents(taskId, userId);
        }
    }

    @Override
    public int getDockedDividerInsetsLw() {
        return getDefaultDisplayContentLocked().getDockedDividerController().getContentInsets();
    }

    private void dumpPolicyLocked(PrintWriter pw, String[] args, boolean dumpAll) {
        pw.println("WINDOW MANAGER POLICY STATE (dumpsys window policy)");
        mPolicy.dump("    ", pw, args);
    }

    private void dumpAnimatorLocked(PrintWriter pw, String[] args, boolean dumpAll) {
        pw.println("WINDOW MANAGER ANIMATOR STATE (dumpsys window animator)");
        mAnimator.dumpLocked(pw, "    ", dumpAll);
    }

    private void dumpTokensLocked(PrintWriter pw, boolean dumpAll) {
        pw.println("WINDOW MANAGER TOKENS (dumpsys window tokens)");
        mRoot.dumpTokens(pw, dumpAll);
        if (!mOpeningApps.isEmpty() || !mClosingApps.isEmpty()) {
            pw.println();
            if (mOpeningApps.size() > 0) {
                pw.print("  mOpeningApps="); pw.println(mOpeningApps);
            }
            if (mClosingApps.size() > 0) {
                pw.print("  mClosingApps="); pw.println(mClosingApps);
            }
        }
    }

    private void dumpSessionsLocked(PrintWriter pw, boolean dumpAll) {
        pw.println("WINDOW MANAGER SESSIONS (dumpsys window sessions)");
        for (int i=0; i<mSessions.size(); i++) {
            Session s = mSessions.valueAt(i);
            pw.print("  Session "); pw.print(s); pw.println(':');
            s.dump(pw, "    ");
        }
    }

    private void dumpWindowsLocked(PrintWriter pw, boolean dumpAll,
            ArrayList<WindowState> windows) {
        pw.println("WINDOW MANAGER WINDOWS (dumpsys window windows)");
        dumpWindowsNoHeaderLocked(pw, dumpAll, windows);
    }

    private void dumpWindowsNoHeaderLocked(PrintWriter pw, boolean dumpAll,
            ArrayList<WindowState> windows) {
        mRoot.dumpWindowsNoHeader(pw, dumpAll, windows);

        if (!mHidingNonSystemOverlayWindows.isEmpty()) {
            pw.println();
            pw.println("  Hiding System Alert Windows:");
            for (int i = mHidingNonSystemOverlayWindows.size() - 1; i >= 0; i--) {
                final WindowState w = mHidingNonSystemOverlayWindows.get(i);
                pw.print("  #"); pw.print(i); pw.print(' ');
                pw.print(w);
                if (dumpAll) {
                    pw.println(":");
                    w.dump(pw, "    ", true);
                } else {
                    pw.println();
                }
            }
        }
        if (mPendingRemove.size() > 0) {
            pw.println();
            pw.println("  Remove pending for:");
            for (int i=mPendingRemove.size()-1; i>=0; i--) {
                WindowState w = mPendingRemove.get(i);
                if (windows == null || windows.contains(w)) {
                    pw.print("  Remove #"); pw.print(i); pw.print(' ');
                            pw.print(w);
                    if (dumpAll) {
                        pw.println(":");
                        w.dump(pw, "    ", true);
                    } else {
                        pw.println();
                    }
                }
            }
        }
        if (mForceRemoves != null && mForceRemoves.size() > 0) {
            pw.println();
            pw.println("  Windows force removing:");
            for (int i=mForceRemoves.size()-1; i>=0; i--) {
                WindowState w = mForceRemoves.get(i);
                pw.print("  Removing #"); pw.print(i); pw.print(' ');
                        pw.print(w);
                if (dumpAll) {
                    pw.println(":");
                    w.dump(pw, "    ", true);
                } else {
                    pw.println();
                }
            }
        }
        if (mDestroySurface.size() > 0) {
            pw.println();
            pw.println("  Windows waiting to destroy their surface:");
            for (int i=mDestroySurface.size()-1; i>=0; i--) {
                WindowState w = mDestroySurface.get(i);
                if (windows == null || windows.contains(w)) {
                    pw.print("  Destroy #"); pw.print(i); pw.print(' ');
                            pw.print(w);
                    if (dumpAll) {
                        pw.println(":");
                        w.dump(pw, "    ", true);
                    } else {
                        pw.println();
                    }
                }
            }
        }
        if (mLosingFocus.size() > 0) {
            pw.println();
            pw.println("  Windows losing focus:");
            for (int i=mLosingFocus.size()-1; i>=0; i--) {
                WindowState w = mLosingFocus.get(i);
                if (windows == null || windows.contains(w)) {
                    pw.print("  Losing #"); pw.print(i); pw.print(' ');
                            pw.print(w);
                    if (dumpAll) {
                        pw.println(":");
                        w.dump(pw, "    ", true);
                    } else {
                        pw.println();
                    }
                }
            }
        }
        if (mResizingWindows.size() > 0) {
            pw.println();
            pw.println("  Windows waiting to resize:");
            for (int i=mResizingWindows.size()-1; i>=0; i--) {
                WindowState w = mResizingWindows.get(i);
                if (windows == null || windows.contains(w)) {
                    pw.print("  Resizing #"); pw.print(i); pw.print(' ');
                            pw.print(w);
                    if (dumpAll) {
                        pw.println(":");
                        w.dump(pw, "    ", true);
                    } else {
                        pw.println();
                    }
                }
            }
        }
        if (mWaitingForDrawn.size() > 0) {
            pw.println();
            pw.println("  Clients waiting for these windows to be drawn:");
            for (int i=mWaitingForDrawn.size()-1; i>=0; i--) {
                WindowState win = mWaitingForDrawn.get(i);
                pw.print("  Waiting #"); pw.print(i); pw.print(' '); pw.print(win);
            }
        }
        pw.println();
        pw.print("  mGlobalConfiguration="); pw.println(mRoot.getConfiguration());
        pw.print("  mHasPermanentDpad="); pw.println(mHasPermanentDpad);
        pw.print("  mCurrentFocus="); pw.println(mCurrentFocus);
        if (mLastFocus != mCurrentFocus) {
            pw.print("  mLastFocus="); pw.println(mLastFocus);
        }
        pw.print("  mFocusedApp="); pw.println(mFocusedApp);
        if (mInputMethodTarget != null) {
            pw.print("  mInputMethodTarget="); pw.println(mInputMethodTarget);
        }
        pw.print("  mInTouchMode="); pw.print(mInTouchMode);
                pw.print(" mLayoutSeq="); pw.println(mLayoutSeq);
        pw.print("  mLastDisplayFreezeDuration=");
                TimeUtils.formatDuration(mLastDisplayFreezeDuration, pw);
                if ( mLastFinishedFreezeSource != null) {
                    pw.print(" due to ");
                    pw.print(mLastFinishedFreezeSource);
                }
                pw.println();
        pw.print("  mLastWakeLockHoldingWindow=");pw.print(mLastWakeLockHoldingWindow);
                pw.print(" mLastWakeLockObscuringWindow="); pw.print(mLastWakeLockObscuringWindow);
                pw.println();

        mInputMonitor.dump(pw, "  ");
        mUnknownAppVisibilityController.dump(pw, "  ");
        mTaskSnapshotController.dump(pw, "  ");

        if (dumpAll) {
            pw.print("  mSystemDecorLayer="); pw.print(mSystemDecorLayer);
                    pw.print(" mScreenRect="); pw.println(mScreenRect.toShortString());
            if (mLastStatusBarVisibility != 0) {
                pw.print("  mLastStatusBarVisibility=0x");
                        pw.println(Integer.toHexString(mLastStatusBarVisibility));
            }
            if (mInputMethodWindow != null) {
                pw.print("  mInputMethodWindow="); pw.println(mInputMethodWindow);
            }
            mWindowPlacerLocked.dump(pw, "  ");
            mRoot.mWallpaperController.dump(pw, "  ");
            pw.print("  mSystemBooted="); pw.print(mSystemBooted);
                    pw.print(" mDisplayEnabled="); pw.println(mDisplayEnabled);

            mRoot.dumpLayoutNeededDisplayIds(pw);

            pw.print("  mTransactionSequence="); pw.println(mTransactionSequence);
            pw.print("  mDisplayFrozen="); pw.print(mDisplayFrozen);
                    pw.print(" windows="); pw.print(mWindowsFreezingScreen);
                    pw.print(" client="); pw.print(mClientFreezingScreen);
                    pw.print(" apps="); pw.print(mAppsFreezingScreen);
                    pw.print(" waitingForConfig="); pw.println(mWaitingForConfig);
            final DisplayContent defaultDisplayContent = getDefaultDisplayContentLocked();
            pw.print("  mRotation="); pw.print(defaultDisplayContent.getRotation());
                    pw.print(" mAltOrientation=");
                            pw.println(defaultDisplayContent.getAltOrientation());
            pw.print("  mLastWindowForcedOrientation=");
                    pw.print(defaultDisplayContent.getLastWindowForcedOrientation());
                    pw.print(" mLastOrientation=");
                            pw.println(defaultDisplayContent.getLastOrientation());
            pw.print("  mDeferredRotationPauseCount="); pw.println(mDeferredRotationPauseCount);
            pw.print("  Animation settings: disabled="); pw.print(mAnimationsDisabled);
                    pw.print(" window="); pw.print(mWindowAnimationScaleSetting);
                    pw.print(" transition="); pw.print(mTransitionAnimationScaleSetting);
                    pw.print(" animator="); pw.println(mAnimatorDurationScaleSetting);
            pw.print("  mSkipAppTransitionAnimation=");pw.println(mSkipAppTransitionAnimation);
            pw.println("  mLayoutToAnim:");
            mAppTransition.dump(pw, "    ");
        }
    }

    private boolean dumpWindows(PrintWriter pw, String name, String[] args, int opti,
            boolean dumpAll) {
        final ArrayList<WindowState> windows = new ArrayList();
        if ("apps".equals(name) || "visible".equals(name) || "visible-apps".equals(name)) {
            final boolean appsOnly = name.contains("apps");
            final boolean visibleOnly = name.contains("visible");
            synchronized(mWindowMap) {
                if (appsOnly) {
                    mRoot.dumpDisplayContents(pw);
                }

                mRoot.forAllWindows((w) -> {
                    if ((!visibleOnly || w.mWinAnimator.getShown())
                            && (!appsOnly || w.mAppToken != null)) {
                        windows.add(w);
                    }
                }, true /* traverseTopToBottom */);
            }
        } else {
            synchronized(mWindowMap) {
                mRoot.getWindowsByName(windows, name);
            }
        }

        if (windows.size() <= 0) {
            return false;
        }

        synchronized(mWindowMap) {
            dumpWindowsLocked(pw, dumpAll, windows);
        }
        return true;
    }

    private void dumpLastANRLocked(PrintWriter pw) {
        pw.println("WINDOW MANAGER LAST ANR (dumpsys window lastanr)");
        if (mLastANRState == null) {
            pw.println("  <no ANR has occurred since boot>");
        } else {
            pw.println(mLastANRState);
        }
    }

    /**
     * Saves information about the state of the window manager at
     * the time an ANR occurred before anything else in the system changes
     * in response.
     *
     * @param appWindowToken The application that ANR'd, may be null.
     * @param windowState The window that ANR'd, may be null.
     * @param reason The reason for the ANR, may be null.
     */
    void saveANRStateLocked(AppWindowToken appWindowToken, WindowState windowState, String reason) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new FastPrintWriter(sw, false, 1024);
        pw.println("  ANR time: " + DateFormat.getDateTimeInstance().format(new Date()));
        if (appWindowToken != null) {
            pw.println("  Application at fault: " + appWindowToken.stringName);
        }
        if (windowState != null) {
            pw.println("  Window at fault: " + windowState.mAttrs.getTitle());
        }
        if (reason != null) {
            pw.println("  Reason: " + reason);
        }
        if (!mWinAddedSinceNullFocus.isEmpty()) {
            pw.println("  Windows added since null focus: " + mWinAddedSinceNullFocus);
        }
        if (!mWinRemovedSinceNullFocus.isEmpty()) {
            pw.println("  Windows removed since null focus: " + mWinRemovedSinceNullFocus);
        }
        pw.println();
        dumpWindowsNoHeaderLocked(pw, true, null);
        pw.println();
        pw.println("Last ANR continued");
        mRoot.dumpDisplayContents(pw);
        pw.close();
        mLastANRState = sw.toString();

        mH.removeMessages(H.RESET_ANR_MESSAGE);
        mH.sendEmptyMessageDelayed(H.RESET_ANR_MESSAGE, LAST_ANR_LIFETIME_DURATION_MSECS);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) return;

        boolean dumpAll = false;

        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;
            if ("-a".equals(opt)) {
                dumpAll = true;
            } else if ("-h".equals(opt)) {
                pw.println("Window manager dump options:");
                pw.println("  [-a] [-h] [cmd] ...");
                pw.println("  cmd may be one of:");
                pw.println("    l[astanr]: last ANR information");
                pw.println("    p[policy]: policy state");
                pw.println("    a[animator]: animator state");
                pw.println("    s[essions]: active sessions");
                pw.println("    surfaces: active surfaces (debugging enabled only)");
                pw.println("    d[isplays]: active display contents");
                pw.println("    t[okens]: token list");
                pw.println("    w[indows]: window list");
                pw.println("  cmd may also be a NAME to dump windows.  NAME may");
                pw.println("    be a partial substring in a window name, a");
                pw.println("    Window hex object identifier, or");
                pw.println("    \"all\" for all windows, or");
                pw.println("    \"visible\" for the visible windows.");
                pw.println("    \"visible-apps\" for the visible app windows.");
                pw.println("  -a: include all available server state.");
                return;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }

        // Is the caller requesting to dump a particular piece of data?
        if (opti < args.length) {
            String cmd = args[opti];
            opti++;
            if ("lastanr".equals(cmd) || "l".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpLastANRLocked(pw);
                }
                return;
            } else if ("policy".equals(cmd) || "p".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpPolicyLocked(pw, args, true);
                }
                return;
            } else if ("animator".equals(cmd) || "a".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpAnimatorLocked(pw, args, true);
                }
                return;
            } else if ("sessions".equals(cmd) || "s".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpSessionsLocked(pw, true);
                }
                return;
            } else if ("surfaces".equals(cmd)) {
                synchronized(mWindowMap) {
                    WindowSurfaceController.SurfaceTrace.dumpAllSurfaces(pw, null);
                }
                return;
            } else if ("displays".equals(cmd) || "d".equals(cmd)) {
                synchronized(mWindowMap) {
                    mRoot.dumpDisplayContents(pw);
                }
                return;
            } else if ("tokens".equals(cmd) || "t".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpTokensLocked(pw, true);
                }
                return;
            } else if ("windows".equals(cmd) || "w".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpWindowsLocked(pw, true, null);
                }
                return;
            } else if ("all".equals(cmd) || "a".equals(cmd)) {
                synchronized(mWindowMap) {
                    dumpWindowsLocked(pw, true, null);
                }
                return;
            } else if ("containers".equals(cmd)) {
                synchronized(mWindowMap) {
                    StringBuilder output = new StringBuilder();
                    mRoot.dumpChildrenNames(output, " ");
                    pw.println(output.toString());
                    pw.println(" ");
                    mRoot.forAllWindows(w -> {pw.println(w);}, true /* traverseTopToBottom */);
                }
                return;
            } else {
                // Dumping a single name?
                if (!dumpWindows(pw, cmd, args, opti, dumpAll)) {
                    pw.println("Bad window command, or no windows match: " + cmd);
                    pw.println("Use -h for help.");
                }
                return;
            }
        }

        synchronized(mWindowMap) {
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            dumpLastANRLocked(pw);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            dumpPolicyLocked(pw, args, dumpAll);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            dumpAnimatorLocked(pw, args, dumpAll);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            dumpSessionsLocked(pw, dumpAll);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            WindowSurfaceController.SurfaceTrace.dumpAllSurfaces(pw, dumpAll ?
                    "-------------------------------------------------------------------------------"
                    : null);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            mRoot.dumpDisplayContents(pw);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            dumpTokensLocked(pw, dumpAll);
            pw.println();
            if (dumpAll) {
                pw.println("-------------------------------------------------------------------------------");
            }
            dumpWindowsLocked(pw, dumpAll, null);
        }
    }

    // Called by the heartbeat to ensure locks are not held indefnitely (for deadlock detection).
    @Override
    public void monitor() {
        synchronized (mWindowMap) { }
    }

    // TODO: All the display method below should probably be moved into the RootWindowContainer...
    private void createDisplayContentLocked(final Display display) {
        if (display == null) {
            throw new IllegalArgumentException("getDisplayContent: display must not be null");
        }
        mRoot.getDisplayContentOrCreate(display.getDisplayId());
    }

    // There is an inherent assumption that this will never return null.
    // TODO(multi-display): Inspect all the call-points of this method to see if they make sense to
    // support non-default display.
    DisplayContent getDefaultDisplayContentLocked() {
        return mRoot.getDisplayContentOrCreate(DEFAULT_DISPLAY);
    }

    public void onDisplayAdded(int displayId) {
        synchronized (mWindowMap) {
            final Display display = mDisplayManager.getDisplay(displayId);
            if (display != null) {
                createDisplayContentLocked(display);
                displayReady(displayId);
            }
            mWindowPlacerLocked.requestTraversal();
        }
    }

    public void onDisplayRemoved(int displayId) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null) {
                displayContent.removeIfPossible();
            }
            mAnimator.removeDisplayLocked(displayId);
            mWindowPlacerLocked.requestTraversal();
        }
    }

    public void onDisplayChanged(int displayId) {
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);
            if (displayContent != null) {
                displayContent.updateDisplayInfo();
            }
            mWindowPlacerLocked.requestTraversal();
        }
    }

    @Override
    public Object getWindowManagerLock() {
        return mWindowMap;
    }

    /**
     * Hint to a token that its activity will relaunch, which will trigger removal and addition of
     * a window.
     * @param token Application token for which the activity will be relaunched.
     */
    public void setWillReplaceWindow(IBinder token, boolean animate) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindowToken = mRoot.getAppWindowToken(token);
            if (appWindowToken == null || !appWindowToken.hasContentToDisplay()) {
                Slog.w(TAG_WM, "Attempted to set replacing window on non-existing app token "
                        + token);
                return;
            }
            appWindowToken.setWillReplaceWindows(animate);
        }
    }

    /**
     * Hint to a token that its windows will be replaced across activity relaunch.
     * The windows would otherwise be removed  shortly following this as the
     * activity is torn down.
     * @param token Application token for which the activity will be relaunched.
     * @param childrenOnly Whether to mark only child windows for replacement
     *                     (for the case where main windows are being preserved/
     *                     reused rather than replaced).
     *
     */
    // TODO: The s at the end of the method name is the only difference with the name of the method
    // above. We should combine them or find better names.
    void setWillReplaceWindows(IBinder token, boolean childrenOnly) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindowToken = mRoot.getAppWindowToken(token);
            if (appWindowToken == null || !appWindowToken.hasContentToDisplay()) {
                Slog.w(TAG_WM, "Attempted to set replacing window on non-existing app token "
                        + token);
                return;
            }

            if (childrenOnly) {
                appWindowToken.setWillReplaceChildWindows();
            } else {
                appWindowToken.setWillReplaceWindows(false /* animate */);
            }

            scheduleClearWillReplaceWindows(token, true /* replacing */);
        }
    }

    /**
     * If we're replacing the window, schedule a timer to clear the replaced window
     * after a timeout, in case the replacing window is not coming.
     *
     * If we're not replacing the window, clear the replace window settings of the app.
     *
     * @param token Application token for the activity whose window might be replaced.
     * @param replacing Whether the window is being replaced or not.
     */
    public void scheduleClearWillReplaceWindows(IBinder token, boolean replacing) {
        synchronized (mWindowMap) {
            final AppWindowToken appWindowToken = mRoot.getAppWindowToken(token);
            if (appWindowToken == null) {
                Slog.w(TAG_WM, "Attempted to reset replacing window on non-existing app token "
                        + token);
                return;
            }
            if (replacing) {
                scheduleWindowReplacementTimeouts(appWindowToken);
            } else {
                appWindowToken.clearWillReplaceWindows();
            }
        }
    }

    void scheduleWindowReplacementTimeouts(AppWindowToken appWindowToken) {
        if (!mWindowReplacementTimeouts.contains(appWindowToken)) {
            mWindowReplacementTimeouts.add(appWindowToken);
        }
        mH.removeMessages(H.WINDOW_REPLACEMENT_TIMEOUT);
        mH.sendEmptyMessageDelayed(
                H.WINDOW_REPLACEMENT_TIMEOUT, WINDOW_REPLACEMENT_TIMEOUT_DURATION);
    }

    @Override
    public int getDockedStackSide() {
        synchronized (mWindowMap) {
            final TaskStack dockedStack = getDefaultDisplayContentLocked()
                    .getDockedStackIgnoringVisibility();
            return dockedStack == null ? DOCKED_INVALID : dockedStack.getDockSide();
        }
    }

    @Override
    public void setDockedStackResizing(boolean resizing) {
        synchronized (mWindowMap) {
            getDefaultDisplayContentLocked().getDockedDividerController().setResizing(resizing);
            requestTraversal();
        }
    }

    @Override
    public void setDockedStackDividerTouchRegion(Rect touchRegion) {
        synchronized (mWindowMap) {
            getDefaultDisplayContentLocked().getDockedDividerController()
                    .setTouchRegion(touchRegion);
            setFocusTaskRegionLocked(null);
        }
    }

    @Override
    public void setResizeDimLayer(boolean visible, int targetStackId, float alpha) {
        synchronized (mWindowMap) {
            getDefaultDisplayContentLocked().getDockedDividerController().setResizeDimLayer(
                    visible, targetStackId, alpha);
        }
    }

    public void setForceResizableTasks(boolean forceResizableTasks) {
        synchronized (mWindowMap) {
            mForceResizableTasks = forceResizableTasks;
        }
    }

    public void setSupportsPictureInPicture(boolean supportsPictureInPicture) {
        synchronized (mWindowMap) {
            mSupportsPictureInPicture = supportsPictureInPicture;
        }
    }

    static int dipToPixel(int dip, DisplayMetrics displayMetrics) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, displayMetrics);
    }

    @Override
    public void registerDockedStackListener(IDockedStackListener listener) {
        if (!checkCallingPermission(REGISTER_WINDOW_MANAGER_LISTENERS,
                "registerDockedStackListener()")) {
            return;
        }
        synchronized (mWindowMap) {
            // TODO(multi-display): The listener is registered on the default display only.
            getDefaultDisplayContentLocked().mDividerControllerLocked.registerDockedStackListener(
                    listener);
        }
    }

    @Override
    public void registerPinnedStackListener(int displayId, IPinnedStackListener listener) {
        if (!checkCallingPermission(REGISTER_WINDOW_MANAGER_LISTENERS,
                "registerPinnedStackListener()")) {
            return;
        }
        if (!mSupportsPictureInPicture) {
            return;
        }
        synchronized (mWindowMap) {
            final DisplayContent displayContent = mRoot.getDisplayContent(displayId);
            displayContent.getPinnedStackController().registerPinnedStackListener(listener);
        }
    }

    @Override
    public void requestAppKeyboardShortcuts(IResultReceiver receiver, int deviceId) {
        try {
            WindowState focusedWindow = getFocusedWindow();
            if (focusedWindow != null && focusedWindow.mClient != null) {
                getFocusedWindow().mClient.requestAppKeyboardShortcuts(receiver, deviceId);
            }
        } catch (RemoteException e) {
        }
    }

    @Override
    public void getStableInsets(int displayId, Rect outInsets) throws RemoteException {
        synchronized (mWindowMap) {
            getStableInsetsLocked(displayId, outInsets);
        }
    }

    void getStableInsetsLocked(int displayId, Rect outInsets) {
        outInsets.setEmpty();
        final DisplayContent dc = mRoot.getDisplayContent(displayId);
        if (dc != null) {
            final DisplayInfo di = dc.getDisplayInfo();
            mPolicy.getStableInsetsLw(di.rotation, di.logicalWidth, di.logicalHeight, outInsets);
        }
    }

    void intersectDisplayInsetBounds(Rect display, Rect insets, Rect inOutBounds) {
        mTmpRect3.set(display);
        mTmpRect3.inset(insets);
        inOutBounds.intersect(mTmpRect3);
    }

    MousePositionTracker mMousePositionTracker = new MousePositionTracker();

    private static class MousePositionTracker implements PointerEventListener {
        private boolean mLatestEventWasMouse;
        private float mLatestMouseX;
        private float mLatestMouseY;

        void updatePosition(float x, float y) {
            synchronized (this) {
                mLatestEventWasMouse = true;
                mLatestMouseX = x;
                mLatestMouseY = y;
            }
        }

        @Override
        public void onPointerEvent(MotionEvent motionEvent) {
            if (motionEvent.isFromSource(InputDevice.SOURCE_MOUSE)) {
                updatePosition(motionEvent.getRawX(), motionEvent.getRawY());
            } else {
                synchronized (this) {
                    mLatestEventWasMouse = false;
                }
            }
        }
    };

    void updatePointerIcon(IWindow client) {
        float mouseX, mouseY;

        synchronized(mMousePositionTracker) {
            if (!mMousePositionTracker.mLatestEventWasMouse) {
                return;
            }
            mouseX = mMousePositionTracker.mLatestMouseX;
            mouseY = mMousePositionTracker.mLatestMouseY;
        }

        synchronized (mWindowMap) {
            if (mDragState != null) {
                // Drag cursor overrides the app cursor.
                return;
            }
            WindowState callingWin = windowForClientLocked(null, client, false);
            if (callingWin == null) {
                Slog.w(TAG_WM, "Bad requesting window " + client);
                return;
            }
            final DisplayContent displayContent = callingWin.getDisplayContent();
            if (displayContent == null) {
                return;
            }
            WindowState windowUnderPointer =
                    displayContent.getTouchableWinAtPointLocked(mouseX, mouseY);
            if (windowUnderPointer != callingWin) {
                return;
            }
            try {
                windowUnderPointer.mClient.updatePointerIcon(
                        windowUnderPointer.translateToWindowX(mouseX),
                        windowUnderPointer.translateToWindowY(mouseY));
            } catch (RemoteException e) {
                Slog.w(TAG_WM, "unable to update pointer icon");
            }
        }
    }

    void restorePointerIconLocked(DisplayContent displayContent, float latestX, float latestY) {
        // Mouse position tracker has not been getting updates while dragging, update it now.
        mMousePositionTracker.updatePosition(latestX, latestY);

        WindowState windowUnderPointer =
                displayContent.getTouchableWinAtPointLocked(latestX, latestY);
        if (windowUnderPointer != null) {
            try {
                windowUnderPointer.mClient.updatePointerIcon(
                        windowUnderPointer.translateToWindowX(latestX),
                        windowUnderPointer.translateToWindowY(latestY));
            } catch (RemoteException e) {
                Slog.w(TAG_WM, "unable to restore pointer icon");
            }
        } else {
            InputManager.getInstance().setPointerIconType(PointerIcon.TYPE_DEFAULT);
        }
    }

    @Override
    public void registerShortcutKey(long shortcutCode, IShortcutService shortcutKeyReceiver)
            throws RemoteException {
        if (!checkCallingPermission(REGISTER_WINDOW_MANAGER_LISTENERS, "registerShortcutKey")) {
            throw new SecurityException(
                    "Requires REGISTER_WINDOW_MANAGER_LISTENERS permission");
        }
        mPolicy.registerShortcutKey(shortcutCode, shortcutKeyReceiver);
    }

    void markForSeamlessRotation(WindowState w, boolean seamlesslyRotated) {
        if (seamlesslyRotated == w.mSeamlesslyRotated) {
            return;
        }
        w.mSeamlesslyRotated = seamlesslyRotated;
        if (seamlesslyRotated) {
            mSeamlessRotationCount++;
        } else {
            mSeamlessRotationCount--;
        }
        if (mSeamlessRotationCount == 0) {
            if (DEBUG_ORIENTATION) {
                Slog.i(TAG, "Performing post-rotate rotation after seamless rotation");
            }
            final DisplayContent displayContent = w.getDisplayContent();
            if (displayContent.updateRotationUnchecked(false /* inTransaction */)) {
                mH.obtainMessage(H.SEND_NEW_CONFIGURATION, displayContent.getDisplayId())
                        .sendToTarget();
            }
        }
    }

    private final class LocalService extends WindowManagerInternal {
        @Override
        public void requestTraversalFromDisplayManager() {
            requestTraversal();
        }

        @Override
        public void setMagnificationSpec(MagnificationSpec spec) {
            synchronized (mWindowMap) {
                if (mAccessibilityController != null) {
                    mAccessibilityController.setMagnificationSpecLocked(spec);
                } else {
                    throw new IllegalStateException("Magnification callbacks not set!");
                }
            }
            if (Binder.getCallingPid() != myPid()) {
                spec.recycle();
            }
        }

        @Override
        public void setForceShowMagnifiableBounds(boolean show) {
            synchronized (mWindowMap) {
                if (mAccessibilityController != null) {
                    mAccessibilityController.setForceShowMagnifiableBoundsLocked(show);
                } else {
                    throw new IllegalStateException("Magnification callbacks not set!");
                }
            }
        }

        @Override
        public void getMagnificationRegion(@NonNull Region magnificationRegion) {
            synchronized (mWindowMap) {
                if (mAccessibilityController != null) {
                    mAccessibilityController.getMagnificationRegionLocked(magnificationRegion);
                } else {
                    throw new IllegalStateException("Magnification callbacks not set!");
                }
            }
        }

        @Override
        public MagnificationSpec getCompatibleMagnificationSpecForWindow(IBinder windowToken) {
            synchronized (mWindowMap) {
                WindowState windowState = mWindowMap.get(windowToken);
                if (windowState == null) {
                    return null;
                }
                MagnificationSpec spec = null;
                if (mAccessibilityController != null) {
                    spec = mAccessibilityController.getMagnificationSpecForWindowLocked(windowState);
                }
                if ((spec == null || spec.isNop()) && windowState.mGlobalScale == 1.0f) {
                    return null;
                }
                spec = (spec == null) ? MagnificationSpec.obtain() : MagnificationSpec.obtain(spec);
                spec.scale *= windowState.mGlobalScale;
                return spec;
            }
        }

        @Override
        public void setMagnificationCallbacks(@Nullable MagnificationCallbacks callbacks) {
            synchronized (mWindowMap) {
                if (mAccessibilityController == null) {
                    mAccessibilityController = new AccessibilityController(
                            WindowManagerService.this);
                }
                mAccessibilityController.setMagnificationCallbacksLocked(callbacks);
                if (!mAccessibilityController.hasCallbacksLocked()) {
                    mAccessibilityController = null;
                }
            }
        }

        @Override
        public void setWindowsForAccessibilityCallback(WindowsForAccessibilityCallback callback) {
            synchronized (mWindowMap) {
                if (mAccessibilityController == null) {
                    mAccessibilityController = new AccessibilityController(
                            WindowManagerService.this);
                }
                mAccessibilityController.setWindowsForAccessibilityCallback(callback);
                if (!mAccessibilityController.hasCallbacksLocked()) {
                    mAccessibilityController = null;
                }
            }
        }

        @Override
        public void setInputFilter(IInputFilter filter) {
            mInputManager.setInputFilter(filter);
        }

        @Override
        public IBinder getFocusedWindowToken() {
            synchronized (mWindowMap) {
                WindowState windowState = getFocusedWindowLocked();
                if (windowState != null) {
                    return windowState.mClient.asBinder();
                }
                return null;
            }
        }

        @Override
        public boolean isKeyguardLocked() {
            return WindowManagerService.this.isKeyguardLocked();
        }

        @Override
        public boolean isKeyguardShowingAndNotOccluded() {
            return WindowManagerService.this.isKeyguardShowingAndNotOccluded();
        }

        @Override
        public void showGlobalActions() {
            WindowManagerService.this.showGlobalActions();
        }

        @Override
        public void getWindowFrame(IBinder token, Rect outBounds) {
            synchronized (mWindowMap) {
                WindowState windowState = mWindowMap.get(token);
                if (windowState != null) {
                    outBounds.set(windowState.mFrame);
                } else {
                    outBounds.setEmpty();
                }
            }
        }

        @Override
        public void waitForAllWindowsDrawn(Runnable callback, long timeout) {
            boolean allWindowsDrawn = false;
            synchronized (mWindowMap) {
                mWaitingForDrawnCallback = callback;
                getDefaultDisplayContentLocked().waitForAllWindowsDrawn();
                mWindowPlacerLocked.requestTraversal();
                mH.removeMessages(H.WAITING_FOR_DRAWN_TIMEOUT);
                if (mWaitingForDrawn.isEmpty()) {
                    allWindowsDrawn = true;
                } else {
                    mH.sendEmptyMessageDelayed(H.WAITING_FOR_DRAWN_TIMEOUT, timeout);
                    checkDrawnWindowsLocked();
                }
            }
            if (allWindowsDrawn) {
                callback.run();
            }
        }

        @Override
        public void addWindowToken(IBinder token, int type, int displayId) {
            WindowManagerService.this.addWindowToken(token, type, displayId);
        }

        @Override
        public void removeWindowToken(IBinder binder, boolean removeWindows, int displayId) {
            synchronized(mWindowMap) {
                if (removeWindows) {
                    final DisplayContent dc = mRoot.getDisplayContent(displayId);
                    if (dc == null) {
                        Slog.w(TAG_WM, "removeWindowToken: Attempted to remove token: " + binder
                                + " for non-exiting displayId=" + displayId);
                        return;
                    }

                    final WindowToken token = dc.removeWindowToken(binder);
                    if (token == null) {
                        Slog.w(TAG_WM, "removeWindowToken: Attempted to remove non-existing token: "
                                + binder);
                        return;
                    }

                    token.removeAllWindowsIfPossible();
                }
                WindowManagerService.this.removeWindowToken(binder, displayId);
            }
        }

        @Override
        public void registerAppTransitionListener(AppTransitionListener listener) {
            synchronized (mWindowMap) {
                mAppTransition.registerListenerLocked(listener);
            }
        }

        @Override
        public int getInputMethodWindowVisibleHeight() {
            synchronized (mWindowMap) {
                return mPolicy.getInputMethodWindowVisibleHeightLw();
            }
        }

        @Override
        public void saveLastInputMethodWindowForTransition() {
            synchronized (mWindowMap) {
                if (mInputMethodWindow != null) {
                    mPolicy.setLastInputMethodWindowLw(mInputMethodWindow, mInputMethodTarget);
                }
            }
        }

        @Override
        public void clearLastInputMethodWindowForTransition() {
            synchronized (mWindowMap) {
                mPolicy.setLastInputMethodWindowLw(null, null);
            }
        }

        @Override
        public void updateInputMethodWindowStatus(@NonNull IBinder imeToken,
                boolean imeWindowVisible, boolean dismissImeOnBackKeyPressed,
                @Nullable IBinder targetWindowToken) {
            // TODO (b/34628091): Use this method to address the window animation issue.
            if (DEBUG_INPUT_METHOD) {
                Slog.w(TAG_WM, "updateInputMethodWindowStatus: imeToken=" + imeToken
                        + " dismissImeOnBackKeyPressed=" + dismissImeOnBackKeyPressed
                        + " imeWindowVisible=" + imeWindowVisible
                        + " targetWindowToken=" + targetWindowToken);
            }
            mPolicy.setDismissImeOnBackKeyPressed(dismissImeOnBackKeyPressed);
        }

        @Override
        public boolean isHardKeyboardAvailable() {
            synchronized (mWindowMap) {
                return mHardKeyboardAvailable;
            }
        }

        @Override
        public void setOnHardKeyboardStatusChangeListener(
                OnHardKeyboardStatusChangeListener listener) {
            synchronized (mWindowMap) {
                mHardKeyboardStatusChangeListener = listener;
            }
        }

        @Override
        public boolean isStackVisible(int stackId) {
            synchronized (mWindowMap) {
                final DisplayContent dc = getDefaultDisplayContentLocked();
                return dc.isStackVisible(stackId);
            }
        }

        @Override
        public boolean isDockedDividerResizing() {
            synchronized (mWindowMap) {
                return getDefaultDisplayContentLocked().getDockedDividerController().isResizing();
            }
        }

        @Override
        public void computeWindowsForAccessibility() {
            final AccessibilityController accessibilityController;
            synchronized (mWindowMap) {
                accessibilityController = mAccessibilityController;
            }
            if (accessibilityController != null) {
                accessibilityController.performComputeChangedWindowsNotLocked();
            }
        }

        @Override
        public void setVr2dDisplayId(int vr2dDisplayId) {
            if (DEBUG_DISPLAY) {
                Slog.d(TAG, "setVr2dDisplayId called for: " + vr2dDisplayId);
            }
            synchronized (WindowManagerService.this) {
                mVr2dDisplayId = vr2dDisplayId;
            }
        }
    }

    void registerAppFreezeListener(AppFreezeListener listener) {
        if (!mAppFreezeListeners.contains(listener)) {
            mAppFreezeListeners.add(listener);
        }
    }

    void unregisterAppFreezeListener(AppFreezeListener listener) {
        mAppFreezeListeners.remove(listener);
    }

    /**
     * WARNING: This interrupts surface updates, be careful! Don't
     * execute within the transaction for longer than you would
     * execute on an animation thread.
     * WARNING: This holds the WindowManager lock, so if exec will acquire
     * the ActivityManager lock, you should hold it BEFORE calling this
     * otherwise there is a risk of deadlock if another thread holding the AM
     * lock waits on the WM lock.
     * WARNING: This method contains locks known to the State of California
     * to cause Deadlocks and other conditions.
     *
     * Begins a surface transaction with which the AM can batch operations.
     * All Surface updates performed by the WindowManager following this
     * will not appear on screen until after the call to
     * closeSurfaceTransaction.
     *
     * ActivityManager can use this to ensure multiple 'commands' will all
     * be reflected in a single frame. For example when reparenting a window
     * which was previously hidden due to it's parent properties, we may
     * need to ensure it is hidden in the same frame that the properties
     * from the new parent are inherited, otherwise it could be revealed
     * mistakenly.
     *
     * TODO(b/36393204): We can investigate totally replacing #deferSurfaceLayout
     * with something like this but it seems that some existing cases of
     * deferSurfaceLayout may be a little too broad, in particular the total
     * enclosure of startActivityUnchecked which could run for quite some time.
     */
    public void inSurfaceTransaction(Runnable exec) {
        // We hold the WindowManger lock to ensure relayoutWindow
        // does not return while a Surface transaction is opening.
        // The client depends on us to have resized the surface
        // by that point (b/36462635)

        synchronized (mWindowMap) {
            SurfaceControl.openTransaction();
            try {
                exec.run();
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    /** Called to inform window manager if non-Vr UI shoul be disabled or not. */
    public void disableNonVrUi(boolean disable) {
        synchronized (mWindowMap) {
            // Allow alert window notifications to be shown if non-vr UI is enabled.
            final boolean showAlertWindowNotifications = !disable;
            if (showAlertWindowNotifications == mShowAlertWindowNotifications) {
                return;
            }
            mShowAlertWindowNotifications = showAlertWindowNotifications;

            for (int i = mSessions.size() - 1; i >= 0; --i) {
                final Session s = mSessions.valueAt(i);
                s.setShowingAlertWindowNotificationAllowed(mShowAlertWindowNotifications);
            }
        }
    }

    boolean hasWideColorGamutSupport() {
        return mHasWideColorGamutSupport &&
                !SystemProperties.getBoolean("persist.sys.sf.native_mode", false);
    }

    void updateNonSystemOverlayWindowsVisibilityIfNeeded(WindowState win, boolean surfaceShown) {
        if (!win.hideNonSystemOverlayWindowsWhenVisible()) {
            return;
        }
        final boolean systemAlertWindowsHidden = !mHidingNonSystemOverlayWindows.isEmpty();
        if (surfaceShown) {
            if (!mHidingNonSystemOverlayWindows.contains(win)) {
                mHidingNonSystemOverlayWindows.add(win);
            }
        } else {
            mHidingNonSystemOverlayWindows.remove(win);
        }

        final boolean hideSystemAlertWindows = !mHidingNonSystemOverlayWindows.isEmpty();

        if (systemAlertWindowsHidden == hideSystemAlertWindows) {
            return;
        }

        mRoot.forAllWindows((w) -> {
            w.setForceHideNonSystemOverlayWindowIfNeeded(hideSystemAlertWindows);
        }, false /* traverseTopToBottom */);
    }

    @Override
    public void screenRecordAction(int mode) {
        mPolicy.screenRecordAction(mode);
    }
}
