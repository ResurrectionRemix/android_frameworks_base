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

package com.android.systemui.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.TaskStackBuilder;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.session.MediaController;
import android.hardware.SensorManager;
import android.net.Uri;
import com.android.systemui.recents.RecentsActivity;
import android.media.MediaMetadata;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.service.vr.IVrManager;
import android.service.vr.IVrStateCallbacks;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.messages.SystemMessageProto.SystemMessage;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.omni.OmniSwitchConstants;
import com.android.internal.util.rr.RRUtils;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardHostView.OnDismissAction;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.DejankUtils;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.RecentsComponent;
import com.android.systemui.SwipeHelper;
import com.android.systemui.SystemUI;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.chaos.lab.gestureanywhere.GestureAnywhereView;
import com.android.systemui.navigation.Navigator;
import com.android.systemui.recents.Recents;
import com.android.systemui.statusbar.NotificationData.Entry;
import com.android.systemui.statusbar.NotificationGuts.OnGutsClosedListener;
import com.android.systemui.statusbar.appcirclesidebar.AppCircleSidebar;
import com.android.systemui.statusbar.notification.VisualStabilityManager;
import com.android.systemui.statusbar.phone.NavigationBarView;
import com.android.systemui.statusbar.phone.NotificationGroupManager;
import com.android.systemui.statusbar.phone.NotificationPanelView;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.pie.PieController;
import com.android.systemui.statusbar.policy.PreviewInflater;
import com.android.systemui.statusbar.policy.RemoteInputView;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.stack.StackStateAnimator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

import static android.service.notification.NotificationListenerService.Ranking.IMPORTANCE_HIGH;
import static android.service.notification.NotificationListenerService.Ranking.IMPORTANCE_MIN;
import static android.service.notification.NotificationListenerService.Ranking.IMPORTANCE_VERY_LOW;
import static android.service.notification.NotificationListenerService.Ranking.importanceToLevel;

public abstract class BaseStatusBar extends SystemUI implements
        CommandQueue.Callbacks, ActivatableNotificationView.OnActivatedListener,
        ExpandableNotificationRow.ExpansionLogger, NotificationData.Environment,
        ExpandableNotificationRow.OnExpandClickListener, OnGutsClosedListener {
    public static final String TAG = "StatusBar";
    public static final boolean DEBUG = false;
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
    protected static final int MSG_SHOW_NEXT_AFFILIATED_TASK = 1024;
    protected static final int MSG_SHOW_PREV_AFFILIATED_TASK = 1025;
    protected static final int MSG_TOGGLE_KEYBOARD_SHORTCUTS_MENU = 1026;
    protected static final int MSG_DISMISS_KEYBOARD_SHORTCUTS_MENU = 1027;

    protected static final boolean ENABLE_HEADS_UP = true;
    protected static final String SETTING_HEADS_UP_TICKER = "ticker_gets_heads_up";

    private static final String PERMISSION_SELF = "com.android.systemui.permission.SELF";

    private static final String ACTION_ENABLE_NAVIGATION_KEY =
            "com.android.systemui.action.ENABLE_NAVIGATION_KEY";

    // Should match the values in PhoneWindowManager
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    public static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";

    private static final String BANNER_ACTION_CANCEL =
            "com.android.systemui.statusbar.banner_action_cancel";
    private static final String BANNER_ACTION_SETUP =
            "com.android.systemui.statusbar.banner_action_setup";
    private static final String WORK_CHALLENGE_UNLOCKED_NOTIFICATION_ACTION
            = "com.android.systemui.statusbar.work_challenge_unlocked_notification_action";

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;
    protected H mHandler = createHandler();

    // all notifications
    public static NotificationData mNotificationData;
    protected NotificationStackScrollLayout mStackScroller;

    protected NotificationGroupManager mGroupManager = new NotificationGroupManager();

    protected RemoteInputController mRemoteInputController;

    // for heads up notifications
    protected HeadsUpManager mHeadsUpManager;

    // handling reordering
    protected VisualStabilityManager mVisualStabilityManager = new VisualStabilityManager();

    protected int mCurrentUserId = 0;
    final protected SparseArray<UserInfo> mCurrentProfiles = new SparseArray<UserInfo>();

    // Pie controls
    protected PieController mPieController;
    public int mOrientation = 0;

    private OrientationEventListener mOrientationListener;

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
    private float mFontScale;

    protected boolean mUseHeadsUp = false;
    protected boolean mHeadsUpTicker = false;
    protected boolean mHeadsUpUserEnabled = false;
    protected boolean mDisableNotificationAlerts = false;
    private boolean mIsAlwaysHeadsupDialer;

    private boolean mIsNavNotificationShowing = false;

    protected DevicePolicyManager mDevicePolicyManager;
    protected IDreamManager mDreamManager;
    protected PowerManager mPowerManager;
    protected StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;

    // public mode, private notifications, etc
    private boolean mLockscreenPublicMode = false;
    private final SparseBooleanArray mUsersAllowingPrivateNotifications = new SparseBooleanArray();
    private final SparseBooleanArray mUsersAllowingNotifications = new SparseBooleanArray();

    private UserManager mUserManager;
    private int mDensity;

    private KeyguardManager mKeyguardManager;
    private LockPatternUtils mLockPatternUtils;

    // UI-specific methods

    /**
     * Create all windows necessary for the status bar (including navigation, overlay panels, etc)
     * and add them to the window manager.
     */
    protected abstract void createAndAddWindows();

    protected WindowManager mWindowManager;
    protected IWindowManager mWindowManagerService;

    protected abstract void refreshLayout(int layoutDirection);

    protected Display mDisplay;

    private boolean mDeviceProvisioned = false;

    protected RecentsComponent mRecents;

    protected int mZenMode;

    protected AppCircleSidebar mAppCircleSidebar;

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
    protected GestureAnywhereView mGestureAnywhereView;

    // which notification is currently being longpress-examined by the user
    private NotificationGuts mNotificationGutsExposed;

    private KeyboardShortcuts mKeyboardShortcuts;

    /**
     * The {@link StatusBarState} of the status bar.
     */
    protected int mState;
    protected boolean mBouncerShowing;
    protected boolean mShowLockscreenNotifications;
    protected boolean mAllowLockscreenRemoteInput;

    protected NotificationOverflowContainer mKeyguardIconOverflowContainer;
    protected DismissView mDismissView;
    protected EmptyShadeView mEmptyShadeView;

    private NotificationClicker mNotificationClicker = new NotificationClicker();

    protected AssistManager mAssistManager;

    protected boolean mVrMode;

    private static final HashMap<String, Field> fieldCache = new HashMap<String, Field>();

    private Set<String> mNonBlockablePkgs;

    protected boolean mScreenPinningEnabled;

    protected int mRecentsType;

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

    public abstract NetworkController getNetworkController();

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
        return mDeviceProvisioned;
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

    public int getNotificationCount() {
        return mNotificationData.getActiveNotifications().size();
    }


    private void showNavigationNotification(boolean show) {
        NotificationManager noMan = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (show && !mIsNavNotificationShowing) {
            Intent intent = new Intent(ACTION_ENABLE_NAVIGATION_KEY);
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.setPackage(mContext.getPackageName());

            final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
            final Notification notification = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(
                            R.string.no_navigation_warning_title))
                    .setContentText(mContext.getResources().getString(
                            R.string.no_navigation_warning_text))
                    .setSmallIcon(R.drawable.no_navigation_warning_icon)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .build();
            mIsNavNotificationShowing = true;
            noMan.notify(R.string.no_navigation_warning_title, notification);
        } else if (mIsNavNotificationShowing) {
            mIsNavNotificationShowing = false;
            noMan.cancel(R.string.no_navigation_warning_title);
        }
    }

    protected final ContentObserver mSettingsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean provisioned = 0 != Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0);
            if (provisioned != mDeviceProvisioned) {
                mDeviceProvisioned = provisioned;
                updateNotifications();
            }
            final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
            setZenMode(mode);

            mIsAlwaysHeadsupDialer = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.ALWAYS_HEADSUP_DIALER, 0, UserHandle.USER_CURRENT) == 1;

            updateLockscreenNotificationSetting();

            // Show notification when no navigation method enabled
            boolean navNotificationEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NO_NAVIGATION_NOTIFICATION, 1,
                    UserHandle.USER_CURRENT) != 0;
            boolean hasNavbar = DUActionUtils.hasNavbarByDefault(mContext);
            boolean navbarDisabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.NAVIGATION_BAR_VISIBLE, (hasNavbar ? 1 : 0),
                    UserHandle.USER_CURRENT) == 0;
            boolean hwKeysDisabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.HARDWARE_KEYS_DISABLE, (hasNavbar ? 1 : 0),
                    UserHandle.USER_CURRENT) != 0;
            showNavigationNotification((hasNavbar && navbarDisabled && navNotificationEnabled) ||
                    (!hasNavbar && hwKeysDisabled && navbarDisabled && navNotificationEnabled));
            mRecentsType = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_RECENTS, 0,
                    UserHandle.USER_CURRENT);
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
            updateNotifications();
        }
    };

    private RemoteViews.OnClickHandler mOnClickHandler = new RemoteViews.OnClickHandler() {
        @Override
        public boolean onClickHandler(
                final View view, final PendingIntent pendingIntent, final Intent fillInIntent) {
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
                ActivityManagerNative.getDefault().resumeAppSwitches();
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
                        if (keyguardShowing && !afterKeyguardGone) {
                            try {
                                ActivityManagerNative.getDefault()
                                        .keyguardWaitingForActivityDrawn();
                                ActivityManagerNative.getDefault().resumeAppSwitches();
                            } catch (RemoteException e) {
                            }
                        }

                        boolean handled = superOnClickHandler(view, pendingIntent, fillInIntent);
                        overrideActivityPendingAppTransition(keyguardShowing && !afterKeyguardGone);

                        // close the shade if it was open
                        if (handled) {
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                                    true /* force */);
                            visibilityChanged(false);
                            mAssistManager.hideAssist();
                        }

                        // Wait for activity start.
                        return handled;
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
                        riv = (RemoteInputView) pv.findViewWithTag(RemoteInputView.VIEW_TAG);
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

            if (riv == null || row == null) {
                return false;
            }

            row.setUserExpanded(true);

            if (!mAllowLockscreenRemoteInput) {
                if (isLockscreenPublicMode()) {
                    onLockedRemoteInput(row, view);
                    return true;
                }
                final int userId = pendingIntent.getCreatorUserHandle().getIdentifier();
                if (mUserManager.getUserInfo(userId).isManagedProfile()
                        && mKeyguardManager.isDeviceLocked(userId)) {
                    onLockedWorkRemoteInput(userId, row, view);
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

    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
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
                    recentTask = ActivityManagerNative.getDefault().getRecentTasks(1,
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
                NotificationManager noMan = (NotificationManager)
                        mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                noMan.cancel(SystemMessage.NOTE_HIDDEN_NOTIFICATIONS);

                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.SHOW_NOTE_ABOUT_NOTIFICATION_HIDING, 0);
                if (BANNER_ACTION_SETUP.equals(action)) {
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                            true /* force */);
                    mContext.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_REDACTION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    );
                }
            } else if (WORK_CHALLENGE_UNLOCKED_NOTIFICATION_ACTION.equals(action)) {
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
                onWorkChallengeUnlocked();
            } else if (ACTION_ENABLE_NAVIGATION_KEY.equals(action)) {
                if (DUActionUtils.hasNavbarByDefault(context)) {
                    Settings.Secure.putInt(context.getContentResolver(),
                            Settings.Secure.NAVIGATION_BAR_VISIBLE,
                            1);
                } else {
                    Settings.Secure.putInt(context.getContentResolver(),
                            Settings.Secure.HARDWARE_KEYS_DISABLE,
                            0);
                }
            }
        }
    };

    private final BroadcastReceiver mAllUsersReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED.equals(action) &&
                    isCurrentProfile(getSendingUserId())) {
                mUsersAllowingPrivateNotifications.clear();
                updateLockscreenNotificationSetting();
                updateNotifications();
            }
        }
    };

    private final NotificationListenerService mNotificationListener =
            new NotificationListenerService() {
        @Override
        public void onListenerConnected() {
            if (DEBUG) Log.d(TAG, "onListenerConnected");
            final StatusBarNotification[] notifications = getActiveNotifications();
            final RankingMap currentRanking = getCurrentRanking();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (StatusBarNotification sbn : notifications) {
                        addNotification(sbn, currentRanking, null /* oldEntry */);
                    }
                }
            });
        }

        @Override
        public void onNotificationPosted(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onNotificationPosted: " + sbn);
            if (sbn != null) {
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
                        if (isUpdate) {
                            updateNotification(sbn, rankingMap);
                        } else {
                            addNotification(sbn, rankingMap, null /* oldEntry */);
                        }
                    }
                });
            }
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onNotificationRemoved: " + sbn);
            if (sbn != null) {
                final String key = sbn.getKey();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        removeNotification(key, rankingMap);
                    }
                });
            }
        }

        @Override
        public void onNotificationRankingUpdate(final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onRankingUpdate");
            if (rankingMap != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateNotificationRanking(rankingMap);
                }
            });
        }                            }

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

    public void start() {
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
        mDisplay = mWindowManager.getDefaultDisplay();
        mDevicePolicyManager = (DevicePolicyManager)mContext.getSystemService(
                Context.DEVICE_POLICY_SERVICE);

        mNotificationData = new NotificationData(this);

        mAccessibilityManager = (AccessibilityManager)
                mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.checkService(DreamService.DREAM_SERVICE));
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED), true,
                mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ZEN_MODE), false,
                mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS), false,
                mSettingsObserver,
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
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ALWAYS_HEADSUP_DIALER), false,
                mSettingsObserver,
                UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.NAVIGATION_BAR_VISIBLE), false,
                mSettingsObserver,
                UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.HARDWARE_KEYS_DISABLE), false,
                mSettingsObserver,
                UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.NO_NAVIGATION_NOTIFICATION), false,
                mSettingsObserver,
                UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_RECENTS), false,
                mSettingsObserver,
                UserHandle.USER_ALL);

        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        final Configuration currentConfig = mContext.getResources().getConfiguration();
        mLocale = currentConfig.locale;
        mLayoutDirection = TextUtils.getLayoutDirectionFromLocale(mLocale);
        mFontScale = currentConfig.fontScale;
        mDensity = currentConfig.densityDpi;

        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mLockPatternUtils = new LockPatternUtils(mContext);

        // Connect in to the status bar manager service
        mCommandQueue = new CommandQueue(this);

        int[] switches = new int[9];
        ArrayList<IBinder> binders = new ArrayList<IBinder>();
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
        disable(switches[0], switches[6], false /* animate */);
        setSystemUiVisibility(switches[1], switches[7], switches[8], 0xffffffff,
                fullscreenStackBounds, dockedStackBounds);
        topAppWindowChanged(switches[2] != 0);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        setImeWindowStatus(binders.get(0), switches[3], switches[4], switches[5] != 0);

        // Set up the initial icon state
        int N = iconSlots.size();
        int viewIndex = 0;
        for (int i=0; i < N; i++) {
            setIcon(iconSlots.get(i), icons.get(i));
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
        filter.addAction(ACTION_ENABLE_NAVIGATION_KEY);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        IntentFilter internalFilter = new IntentFilter();
        internalFilter.addAction(WORK_CHALLENGE_UNLOCKED_NOTIFICATION_ACTION);
        internalFilter.addAction(BANNER_ACTION_CANCEL);
        internalFilter.addAction(BANNER_ACTION_SETUP);
        mContext.registerReceiver(mBroadcastReceiver, internalFilter, PERMISSION_SELF, null);

        IntentFilter allUsersFilter = new IntentFilter();
        allUsersFilter.addAction(
                DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        mContext.registerReceiverAsUser(mAllUsersReceiver, UserHandle.ALL, allUsersFilter,
                null, null);
        updateCurrentProfilesCache();

        IVrManager vrManager = IVrManager.Stub.asInterface(ServiceManager.getService("vrmanager"));
        try {
            vrManager.registerListener(mVrStateCallbacks);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register VR mode state listener: " + e);
        }

        mNonBlockablePkgs = new ArraySet<String>();
        Collections.addAll(mNonBlockablePkgs, mContext.getResources().getStringArray(
                com.android.internal.R.array.config_nonBlockableNotificationPackages));

        mPieSettingsObserver.onChange(false);
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.PIE_STATE), false, mPieSettingsObserver);
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.PIE_GRAVITY), false, mPieSettingsObserver);
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
            Notification.Builder note = new Notification.Builder(mContext)
                    .setSmallIcon(R.drawable.ic_android)
                    .setContentTitle(mContext.getString(R.string.hidden_notifications_title))
                    .setContentText(mContext.getString(R.string.hidden_notifications_text))
                    .setPriority(Notification.PRIORITY_HIGH)
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

            NotificationManager noMan =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            noMan.notify(SystemMessage.NOTE_HIDDEN_NOTIFICATIONS, note.build());
        }
    }

    public void userSwitched(int newUserId) {
        setHeadsUpUser(newUserId);
    }

    protected abstract void setHeadsUpUser(int newUserId);

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
    public String getCurrentMediaNotificationKey() {
        return null;
    }

    protected MediaController getCurrentMediaController() {
        return null;
    }

    @Override
    public NotificationGroupManager getGroupManager() {
        return mGroupManager;
    }

    /**
     * Takes the necessary steps to prepare the status bar for starting an activity, then starts it.
     * @param action A dismiss action that is called if it's safe to start the activity.
     * @param afterKeyguardGone Whether the action should be executed after the Keyguard is gone.
     */
    protected void dismissKeyguardThenExecute(OnDismissAction action, boolean afterKeyguardGone) {
        action.onDismiss();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        final Locale locale = mContext.getResources().getConfiguration().locale;
        final int ld = TextUtils.getLayoutDirectionFromLocale(locale);
        final float fontScale = newConfig.fontScale;
        final int density = newConfig.densityDpi;
        if (density != mDensity || mFontScale != fontScale) {
            onDensityOrFontScaleChanged();
            mDensity = density;
            mFontScale = fontScale;
        }
        if (! locale.equals(mLocale) || ld != mLayoutDirection) {
            if (DEBUG) {
                Log.v(TAG, String.format(
                        "config changed locale/LD: %s (%d) -> %s (%d)", mLocale, mLayoutDirection,
                        locale, ld));
            }
            mLocale = locale;
            mLayoutDirection = ld;
            refreshLayout(ld);
        }

        if (mAssistManager != null) {
            mAssistManager.onConfigurationChanged();
        }
        int rotation = mDisplay.getRotation();
        if (rotation != mOrientation) {
            if (mPieController != null) mPieController.detachPie();
            mOrientation = rotation;
        }
    }

    protected void onDensityOrFontScaleChanged() {
        ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        for (int i = 0; i < activeNotifications.size(); i++) {
            Entry entry = activeNotifications.get(i);
            boolean exposedGuts = entry.row.getGuts() == mNotificationGutsExposed;
            entry.row.reInflateViews();
            if (exposedGuts) {
                mNotificationGutsExposed = entry.row.getGuts();
                bindGuts(entry.row);
            }
            inflateViews(entry, mStackScroller);
        }
        ContentResolver resolver = mContext.getContentResolver();
        boolean pieEnabled = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.PIE_STATE, 0, UserHandle.USER_CURRENT) == 1;
        updatePieControls(!pieEnabled);
    }

    protected void bindDismissListener(final ExpandableNotificationRow row) {
        row.setOnDismissListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Accessibility feedback
                v.announceForAccessibility(
                        mContext.getString(R.string.accessibility_notification_dismissed));
                performRemoveNotification(row.getStatusBarNotification());
            }
        });
    }

    public void performRemoveNotification(StatusBarNotification n) {
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
    }


    protected void applyColorsAndBackgrounds(StatusBarNotification sbn,
            NotificationData.Entry entry) {

        if (entry.getContentView().getId()
                != com.android.internal.R.id.status_bar_latest_event_content) {
            // Using custom RemoteViews
            if (entry.targetSdk >= Build.VERSION_CODES.GINGERBREAD
                    && entry.targetSdk < Build.VERSION_CODES.LOLLIPOP) {
                entry.row.setShowingLegacyBackground(true);
                entry.legacy = true;
            }
        }

        if (entry.icon != null) {
            entry.icon.setTag(R.id.icon_is_pre_L, entry.targetSdk < Build.VERSION_CODES.LOLLIPOP);
        }
    }

    public boolean isMediaNotification(NotificationData.Entry entry) {
        // TODO: confirm that there's a valid media key
        return entry.getExpandedContentView() != null &&
               entry.getExpandedContentView()
                       .findViewById(com.android.internal.R.id.media_actions) != null;
    }

    // The (i) button in the guts that links to the system notification settings for that app
    private void startAppNotificationSettingsActivity(String packageName, final int appUid) {
        final Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        intent.putExtra(Settings.EXTRA_APP_UID, appUid);
        startNotificationGutsIntent(intent, appUid);
    }

    private void startNotificationGutsIntent(final Intent intent, final int appUid) {
        final boolean keyguardShowing = mStatusBarKeyguardViewManager.isShowing();
        dismissKeyguardThenExecute(new OnDismissAction() {
            @Override
            public boolean onDismiss() {
                AsyncTask.execute(new Runnable() {
                    public void run() {
                        try {
                            if (keyguardShowing) {
                                ActivityManagerNative.getDefault()
                                        .keyguardWaitingForActivityDrawn();
                            }
                            TaskStackBuilder.create(mContext)
                                    .addNextIntentWithParentStack(intent)
                                    .startActivities(getActivityOptions(),
                                            new UserHandle(UserHandle.getUserId(appUid)));
                            overrideActivityPendingAppTransition(keyguardShowing);
                        } catch (RemoteException e) {
                        }
                    }
                });
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL, true /* force */);
                return true;
            }
        }, false /* afterKeyguardGone */);
    }

    private void bindGuts(final ExpandableNotificationRow row) {
        row.inflateGuts();
        final StatusBarNotification sbn = row.getStatusBarNotification();
        PackageManager pmUser = getPackageManagerForUser(mContext, sbn.getUser().getIdentifier());
        row.setTag(sbn.getPackageName());
        final NotificationGuts guts = row.getGuts();
        guts.setClosedListener(this);
        final String pkg = sbn.getPackageName();
        String appname = pkg;
        Drawable pkgicon = null;
        int appUid = -1;
        try {
            final ApplicationInfo info = pmUser.getApplicationInfo(pkg,
                    PackageManager.GET_UNINSTALLED_PACKAGES
                            | PackageManager.GET_DISABLED_COMPONENTS);
            if (info != null) {
                appname = String.valueOf(pmUser.getApplicationLabel(info));
                pkgicon = pmUser.getApplicationIcon(info);
                appUid = info.uid;
            }
        } catch (NameNotFoundException e) {
            // app is gone, just show package name and generic icon
            pkgicon = pmUser.getDefaultActivityIcon();
        }

        ((ImageView) guts.findViewById(R.id.app_icon)).setImageDrawable(pkgicon);
        ((TextView) guts.findViewById(R.id.pkgname)).setText(appname);

        final TextView settingsButton = (TextView) guts.findViewById(R.id.more_settings);
        ViewGroup buttonParent = (ViewGroup) settingsButton.getParent();
        final TextView killButton = (TextView) LayoutInflater.from(
                settingsButton.getContext()).inflate(
                R.layout.kill_button, buttonParent, false /* attachToRoot */);
        if (buttonParent.findViewById(R.id.notification_inspect_kill) == null) { // only add once
            buttonParent.addView(killButton, buttonParent.indexOfChild(settingsButton)/*index*/);
        }
        if (appUid >= 0) {
            final int appUidF = appUid;
            settingsButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    MetricsLogger.action(mContext, MetricsEvent.ACTION_NOTE_INFO);
                    guts.resetFalsingCheck();
                    startAppNotificationSettingsActivity(pkg, appUidF);
                }
            });
            settingsButton.setText(R.string.notification_more_settings);
            if (isThisASystemPackage(pkg, pmUser)) {
                killButton.setVisibility(View.GONE);
            } else {
                boolean killButtonEnabled = Settings.System.getIntForUser(
                        mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_GUTS_KILL_APP_BUTTON, 0,
                        UserHandle.USER_CURRENT) != 0;
                killButton.setVisibility(killButtonEnabled ? View.VISIBLE : View.GONE);
                killButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (mKeyguardManager.inKeyguardRestrictedInputMode()) {
                            // Don't do anything
                            return;
                        }
                        final SystemUIDialog killDialog = new SystemUIDialog(mContext);
                        killDialog.setTitle(mContext.getText(R.string.force_stop_dlg_title));
                        killDialog.setMessage(mContext.getText(R.string.force_stop_dlg_text));
                        killDialog.setPositiveButton(
                                R.string.dlg_ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // kill pkg
                                ActivityManager actMan =
                                        (ActivityManager) mContext.getSystemService(
                                        Context.ACTIVITY_SERVICE);
                                actMan.forceStopPackage(pkg);
                            }
                        });
                        killDialog.setNegativeButton(R.string.dlg_cancel, null);
                        killDialog.show();
                    }
                });
                killButton.setText(R.string.kill);
            }
        } else {
            settingsButton.setVisibility(View.GONE);
            killButton.setVisibility(View.GONE);
        }

        guts.bindImportance(pmUser, sbn, mNonBlockablePkgs,
                mNotificationData.getImportance(sbn.getKey()));

        final TextView doneButton = (TextView) guts.findViewById(R.id.done);
        doneButton.setText(R.string.notification_done);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If the user has security enabled, show challenge if the setting is changed.
                if (guts.hasImportanceChanged() && isLockscreenPublicMode() &&
                        (mState == StatusBarState.KEYGUARD
                        || mState == StatusBarState.SHADE_LOCKED)) {
                    OnDismissAction dismissAction = new OnDismissAction() {
                        @Override
                        public boolean onDismiss() {
                            saveImportanceCloseControls(sbn, row, guts, v);
                            return true;
                        }
                    };
                    onLockedNotificationImportanceChange(dismissAction);
                } else {
                    saveImportanceCloseControls(sbn, row, guts, v);
                }
            }
        });
    }

    private boolean isThisASystemPackage(String packageName, PackageManager pm) {
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES);
            PackageInfo sys = pm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
            return (packageInfo != null && packageInfo.signatures != null &&
                    sys.signatures[0].equals(packageInfo.signatures[0]));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void saveImportanceCloseControls(StatusBarNotification sbn,
            ExpandableNotificationRow row, NotificationGuts guts, View done) {
        guts.resetFalsingCheck();
        guts.saveImportance(sbn);

        int[] rowLocation = new int[2];
        int[] doneLocation = new int[2];
        row.getLocationOnScreen(rowLocation);
        done.getLocationOnScreen(doneLocation);

        final int centerX = done.getWidth() / 2;
        final int centerY = done.getHeight() / 2;
        final int x = doneLocation[0] - rowLocation[0] + centerX;
        final int y = doneLocation[1] - rowLocation[1] + centerY;
        dismissPopups(x, y);
    }

    protected SwipeHelper.LongPressListener getNotificationLongClicker() {
        return new SwipeHelper.LongPressListener() {
            @Override
            public boolean onLongPress(View v, final int x, final int y) {
                if (!(v instanceof ExpandableNotificationRow)) {
                    return false;
                }
                if (v.getWindowToken() == null) {
                    Log.e(TAG, "Trying to show notification guts, but not attached to window");
                    return false;
                }

                final ExpandableNotificationRow row = (ExpandableNotificationRow) v;
                if (v instanceof MediaExpandableNotificationRow
                        && !((MediaExpandableNotificationRow) v).inflateGuts()) {
                    return false;
                }
                bindGuts(row);

                // Assume we are a status_bar_notification_row
                final NotificationGuts guts = row.getGuts();
                if (guts == null) {
                    // This view has no guts. Examples are the more card or the dismiss all view
                    return false;
                }

                // Already showing?
                if (guts.getVisibility() == View.VISIBLE) {
                    dismissPopups(x, y);
                    return false;
                }

                MetricsLogger.action(mContext, MetricsEvent.ACTION_NOTE_CONTROLS);

                // ensure that it's laid but not visible until actually laid out
                guts.setVisibility(View.INVISIBLE);
                // Post to ensure the the guts are properly laid out.
                guts.post(new Runnable() {
                    public void run() {
                        dismissPopups(-1 /* x */, -1 /* y */, false /* resetGear */,
                                false /* animate */);
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
                                // Move the notification view back over the gear
                                row.resetTranslation();
                            }
                        });
                        a.start();
                        guts.setExposed(true /* exposed */,
                                mState == StatusBarState.KEYGUARD /* needsFalsingProtection */);
                        row.closeRemoteInput();
                        mStackScroller.onHeightChanged(null, true /* needsAnimation */);
                        mNotificationGutsExposed = guts;
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

    public void dismissPopups() {
        dismissPopups(-1 /* x */, -1 /* y */, true /* resetGear */, false /* animate */);
    }

    private void dismissPopups(int x, int y) {
        dismissPopups(x, y, true /* resetGear */, false /* animate */);
    }

    public void dismissPopups(int x, int y, boolean resetGear, boolean animate) {
        if (mNotificationGutsExposed != null) {
            mNotificationGutsExposed.closeControls(x, y, true /* notify */);
        }
        if (resetGear) {
            mStackScroller.resetExposedGearView(animate, true /* force */);
        }
    }

    @Override
    public void onGutsClosed(NotificationGuts guts) {
        mStackScroller.onHeightChanged(null, true /* needsAnimation */);
        mNotificationGutsExposed = null;
    }

    @Override
    public void showRecentApps(boolean triggeredFromAltTab, boolean fromHome) {
        int msg = MSG_SHOW_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.obtainMessage(msg, triggeredFromAltTab ? 1 : 0, fromHome ? 1 : 0).sendToTarget();
    }

    @Override
    public void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        int msg = MSG_HIDE_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.obtainMessage(msg, triggeredFromAltTab ? 1 : 0,
                triggeredFromHomeKey ? 1 : 0).sendToTarget();
    }

    @Override
    public void toggleRecentApps() {
        RecentsActivity.startBlurTask();
        toggleRecents();
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

    /** Jumps to the next affiliated task in the group. */
    public void showNextAffiliatedTask() {
        int msg = MSG_SHOW_NEXT_AFFILIATED_TASK;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    /** Jumps to the previous affiliated task in the group. */
    public void showPreviousAffiliatedTask() {
        int msg = MSG_SHOW_PREV_AFFILIATED_TASK;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void screenPinningStateChanged(boolean enabled) {
        if (mScreenPinningEnabled != enabled) {
            mScreenPinningEnabled = enabled;
        }
        Log.d(TAG, "StatusBar API screenPinningStateChanged = " + enabled);
    }

    @Override
    public void restartUI() {
        Log.d(TAG, "StatusBar API restartUI! Commiting suicide! Goodbye cruel world!");
        Process.killProcess(Process.myPid());
    }

    @Override
    public void leftInLandscapeChanged(boolean isLeft) {
        if (DEBUG)
            Log.d(TAG, "StatusBar API leftInLandscapeChanged = " + isLeft);
    }

    protected H createHandler() {
         return new H();
    }

    protected void sendCloseSystemWindows(String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    protected abstract View getStatusBarView();

    /*this is not needed with DUI navbar because it's not set anymore in PhoneStatusBar and it preloads recents
    if the button has Recent action (SmartButtonView) with the ActionHandler. But i'm keeping it if anyone using stock navbar*/
    protected View.OnTouchListener mRecentsPreloadOnTouchListener = new View.OnTouchListener() {
        // additional optimization when we have software system buttons - start loading the recent
        // tasks on touch down
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                if (mRecentsType == 1) {
                    RRUtils.preloadOmniSwitchRecents(mContext, UserHandle.CURRENT);
                } else {
                    preloadRecents();
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                if (mRecentsType != 1) {
                    cancelPreloadingRecents();
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (!v.isPressed()) {
                    if (mRecentsType != 1) {
                        cancelPreloadingRecents();
                    }
                }

            }
            return false;
        }
    };

    /**
     * Toggle docking the app window
     *
     * @param metricsDockAction the action to log when docking is successful, or -1 to not log
     *                          anything on successful docking
     * @param metricsUndockAction the action to log when undocking, or -1 to not log anything when
     *                            undocking
     */
    protected abstract void toggleSplitScreenMode(int metricsDockAction, int metricsUndockAction);

    /** Proxy for RecentsComponent */

    protected void showRecents(boolean triggeredFromAltTab, boolean fromHome) {
        if (mRecents != null) {
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_RECENT_APPS);
            mRecents.showRecents(triggeredFromAltTab, fromHome);
        }
    }

    protected void hideRecents(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        if (mRecents != null) {
            mRecents.hideRecents(triggeredFromAltTab, triggeredFromHomeKey);
        }
    }

    protected void toggleRecents() {
        if (mRecentsType == 1) {
            RRUtils.toggleOmniSwitchRecents(mContext, UserHandle.CURRENT);
        } else if (mRecents != null) {
            mRecents.toggleRecents(mDisplay);
        }
    }

    protected void preloadRecents() {
        if (mRecentsType != 1 && mRecents != null) {
            mRecents.preloadRecents();
        } else {
            RRUtils.preloadOmniSwitchRecents(mContext, UserHandle.CURRENT);
        }
    }

    protected void toggleKeyboardShortcuts(int deviceId) {
        KeyboardShortcuts.toggle(mContext, deviceId);
    }

    protected void dismissKeyboardShortcuts() {
        KeyboardShortcuts.dismiss();
    }

    protected void cancelPreloadingRecents() {
        if (mRecentsType != 1 && mRecents != null) {
            mRecents.cancelPreloadingRecents();
        }
    }

    protected void showRecentsNextAffiliatedTask() {
        if (mRecentsType != 1 && mRecents != null) {
            mRecents.showNextAffiliatedTask();
        }
    }

    protected void showRecentsPreviousAffiliatedTask() {
        if (mRecentsType != 1 && mRecents != null) {
            mRecents.showPrevAffiliatedTask();
        }
    }

    /**
     * If there is an active heads-up notification and it has a fullscreen intent, fire it now.
     */
    public abstract void maybeEscalateHeadsUp();

    /**
     * Save the current "public" (locked and secure) state of the lockscreen.
     */
    public void setLockscreenPublicMode(boolean publicMode) {
        mLockscreenPublicMode = publicMode;
    }

    public boolean isLockscreenPublicMode() {
        return mLockscreenPublicMode;
    }

    protected void onWorkChallengeUnlocked() {}

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
    public boolean shouldHideNotifications(int userid) {
        return isLockscreenPublicMode() && !userAllowsNotificationsInPublic(userid);
    }

    /**
     * Returns true if we're on a secure lockscreen and the user wants to hide notifications via
     * package-specific override.
     */
    @Override // NotificationDate.Environment
    public boolean shouldHideNotifications(String key) {
        return isLockscreenPublicMode()
                && mNotificationData.getVisibilityOverride(key) == Notification.VISIBILITY_SECRET;
    }

    /**
     * Returns true if we're on a secure lockscreen.
     */
    @Override  // NotificationData.Environment
    public boolean onSecureLockScreen() {
        return isLockscreenPublicMode();
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
    
    public static void updatePreferences() {

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

    /**
     * Called when the notification panel layouts
     */
    public void onPanelLaidOut() {
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

    protected void onLockedNotificationImportanceChange(OnDismissAction dismissAction) {}

    protected void onLockedRemoteInput(ExpandableNotificationRow row, View clickedView) {}

    protected void onLockedWorkRemoteInput(int userId, ExpandableNotificationRow row,
            View clicked) {}

    @Override
    public void onExpandClicked(Entry clickedEntry, boolean nowExpanded) {
    }

    protected class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
             case MSG_SHOW_RECENT_APPS:
                 showRecents(m.arg1 > 0, m.arg2 != 0);
                 break;
             case MSG_HIDE_RECENT_APPS:
                 hideRecents(m.arg1 > 0, m.arg2 > 0);
                 break;
             case MSG_TOGGLE_RECENTS_APPS:
                 toggleRecents();
                 break;
             case MSG_PRELOAD_RECENT_APPS:
                  preloadRecents();
                  break;
             case MSG_CANCEL_PRELOAD_RECENT_APPS:
                  cancelPreloadingRecents();
                  break;
             case MSG_SHOW_NEXT_AFFILIATED_TASK:
                  showRecentsNextAffiliatedTask();
                  break;
             case MSG_SHOW_PREV_AFFILIATED_TASK:
                  showRecentsPreviousAffiliatedTask();
                  break;
             case MSG_TOGGLE_KEYBOARD_SHORTCUTS_MENU:
                  toggleKeyboardShortcuts(m.arg1);
                  break;
             case MSG_DISMISS_KEYBOARD_SHORTCUTS_MENU:
                  dismissKeyboardShortcuts();
                  break;
            }
        }
    }

    protected void workAroundBadLayerDrawableOpacity(View v) {
    }

    protected boolean inflateViews(Entry entry, ViewGroup parent) {
        PackageManager pmUser = getPackageManagerForUser(mContext,
                entry.notification.getUser().getIdentifier());

        final StatusBarNotification sbn = entry.notification;
        try {
            entry.cacheContentViews(mContext, null);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to get notification remote views", e);
            return false;
        }

        final RemoteViews contentView = entry.cachedContentView;
        final RemoteViews bigContentView = entry.cachedBigContentView;
        final RemoteViews headsUpContentView = entry.cachedHeadsUpContentView;
        final RemoteViews publicContentView = entry.cachedPublicContentView;

        if (contentView == null) {
            Log.v(TAG, "no contentView for: " + sbn.getNotification());
            return false;
        }

        if (DEBUG) {
            Log.v(TAG, "publicContentView: " + publicContentView);
        }

        ExpandableNotificationRow row;

        // Stash away previous user expansion state so we can restore it at
        // the end.
        boolean hasUserChangedExpansion = false;
        boolean userExpanded = false;
        boolean userLocked = false;

        if (entry.row != null) {
            row = entry.row;
            hasUserChangedExpansion = row.hasUserChangedExpansion();
            userExpanded = row.isUserExpanded();
            userLocked = row.isUserLocked();
            entry.reset();
            if (hasUserChangedExpansion) {
                row.setUserExpanded(userExpanded);
            }
        } else {
            // create the row view
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);

            // cannot use isMediaNotification()
            if (sbn.getNotification().category != null
                    && sbn.getNotification().category.equals(Notification.CATEGORY_TRANSPORT)) {
                Log.d("ro", "inflating media notification");
                row = (MediaExpandableNotificationRow) inflater.inflate(
                        R.layout.status_bar_notification_row_media, parent, false);
                ((MediaExpandableNotificationRow)row).setMediaController(
                        getCurrentMediaController());
            } else {
                row = (ExpandableNotificationRow) inflater.inflate(
                        R.layout.status_bar_notification_row,
                        parent, false);
            }
            row.setExpansionLogger(this, entry.notification.getKey());
            row.setGroupManager(mGroupManager);
            row.setHeadsUpManager(mHeadsUpManager);
            row.setRemoteInputController(mRemoteInputController);
            row.setOnExpandClickListener(this);

            // Get the app name.
            // Note that Notification.Builder#bindHeaderAppName has similar logic
            // but since this field is used in the guts, it must be accurate.
            // Therefore we will only show the application label, or, failing that, the
            // package name. No substitutions.
            final String pkg = sbn.getPackageName();
            String appname = pkg;
            try {
                final ApplicationInfo info = pmUser.getApplicationInfo(pkg,
                        PackageManager.GET_UNINSTALLED_PACKAGES
                                | PackageManager.GET_DISABLED_COMPONENTS);
                if (info != null) {
                    appname = String.valueOf(pmUser.getApplicationLabel(info));
                }
            } catch (NameNotFoundException e) {
                // Do nothing
            }
            row.setAppName(appname);
        }

        workAroundBadLayerDrawableOpacity(row);
        bindDismissListener(row);

        // NB: the large icon is now handled entirely by the template

        // bind the click event to the content area
        NotificationContentView contentContainer = row.getPrivateLayout();
        NotificationContentView contentContainerPublic = row.getPublicLayout();

        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (ENABLE_REMOTE_INPUT) {
            row.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        }

        mNotificationClicker.register(row, sbn);

        // set up the adaptive layout
        View contentViewLocal = null;
        View bigContentViewLocal = null;
        View headsUpContentViewLocal = null;
        View publicViewLocal = null;
        try {
            contentViewLocal = contentView.apply(
                    sbn.getPackageContext(mContext),
                    contentContainer,
                    mOnClickHandler);
            if (bigContentView != null) {
                bigContentViewLocal = bigContentView.apply(
                        sbn.getPackageContext(mContext),
                        contentContainer,
                        mOnClickHandler);
            }
            if (headsUpContentView != null) {
                headsUpContentViewLocal = headsUpContentView.apply(
                        sbn.getPackageContext(mContext),
                        contentContainer,
                        mOnClickHandler);
            }
            if (publicContentView != null) {
                publicViewLocal = publicContentView.apply(
                        sbn.getPackageContext(mContext),
                        contentContainerPublic, mOnClickHandler);
            }

            if (contentViewLocal != null) {
                contentViewLocal.setIsRootNamespace(true);
                contentContainer.setContractedChild(contentViewLocal);
            }
            if (bigContentViewLocal != null) {
                bigContentViewLocal.setIsRootNamespace(true);
                contentContainer.setExpandedChild(bigContentViewLocal);
            }
            if (headsUpContentViewLocal != null) {
                headsUpContentViewLocal.setIsRootNamespace(true);
                contentContainer.setHeadsUpChild(headsUpContentViewLocal);
            }
            if (publicViewLocal != null) {
                publicViewLocal.setIsRootNamespace(true);
                contentContainerPublic.setContractedChild(publicViewLocal);
            }
        }
        catch (RuntimeException e) {
            final String ident = sbn.getPackageName() + "/0x" + Integer.toHexString(sbn.getId());
            Log.e(TAG, "couldn't inflate view for notification " + ident, e);
            return false;
        }

        // Extract target SDK version.
        try {
            ApplicationInfo info = pmUser.getApplicationInfo(sbn.getPackageName(), 0);
            entry.targetSdk = info.targetSdkVersion;
        } catch (NameNotFoundException ex) {
            Log.e(TAG, "Failed looking up ApplicationInfo for " + sbn.getPackageName(), ex);
        }
        entry.autoRedacted = entry.notification.getNotification().publicVersion == null;

        if (MULTIUSER_DEBUG) {
            TextView debug = (TextView) row.findViewById(R.id.debug_info);
            if (debug != null) {
                debug.setVisibility(View.VISIBLE);
                debug.setText("CU " + mCurrentUserId +" NU " + entry.notification.getUserId());
            }
        }
        entry.row = row;
        entry.row.setOnActivatedListener(this);
        entry.row.setExpandable(bigContentViewLocal != null);

        applyColorsAndBackgrounds(sbn, entry);

        // Restore previous flags.
        if (hasUserChangedExpansion) {
            // Note: setUserExpanded() conveniently ignores calls with
            //       userExpanded=true if !isExpandable().
            row.setUserExpanded(userExpanded);
        }
        row.setUserLocked(userLocked);
        row.onNotificationUpdated(entry);
        return true;
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
            public boolean onDismiss() {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            if (keyguardShowing && !afterKeyguardGone) {
                                ActivityManagerNative.getDefault()
                                        .keyguardWaitingForActivityDrawn();
                            }

                            // The intent we are sending is for the application, which
                            // won't have permission to immediately start an activity after
                            // the user switches to home.  We know it is safe to do at this
                            // point, so make sure new activity switches are now allowed.
                            ActivityManagerNative.getDefault().resumeAppSwitches();
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
                            overrideActivityPendingAppTransition(keyguardShowing
                                    && !afterKeyguardGone);
                        }
                    }
                }.start();

                // close the shade if it was open
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                        true /* force */, true /* delayed */);
                visibilityChanged(false);

                return true;
            }
        }, afterKeyguardGone);
    }

    public void addPostCollapseAction(Runnable r) {
    }

    public boolean isCollapsing() {
        return false;
    }

    private final class NotificationClicker implements View.OnClickListener {
        public void onClick(final View v) {
            if (!(v instanceof ExpandableNotificationRow)) {
                Log.e(TAG, "NotificationClicker called on a view that is not a notification row.");
                return;
            }

            final ExpandableNotificationRow row = (ExpandableNotificationRow) v;
            final StatusBarNotification sbn = row.getStatusBarNotification();
            if (sbn == null) {
                Log.e(TAG, "NotificationClicker called on an unclickable notification,");
                return;
            }

            // Check if the notification is displaying the gear, if so slide notification back
            if (row.getSettingsRow() != null && row.getSettingsRow().isVisible()) {
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

            final boolean keyguardShowing = mStatusBarKeyguardViewManager.isShowing();
            final boolean afterKeyguardGone = intent.isActivity()
                    && PreviewInflater.wouldLaunchResolverActivity(mContext, intent.getIntent(),
                            mCurrentUserId);
            dismissKeyguardThenExecute(new OnDismissAction() {
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
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                if (keyguardShowing && !afterKeyguardGone) {
                                    ActivityManagerNative.getDefault()
                                            .keyguardWaitingForActivityDrawn();
                                }

                                // The intent we are sending is for the application, which
                                // won't have permission to immediately start an activity after
                                // the user switches to home.  We know it is safe to do at this
                                // point, so make sure new activity switches are now allowed.
                                ActivityManagerNative.getDefault().resumeAppSwitches();
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
                                        boolean canBypass = false;
                                        try {
                                            canBypass = ActivityManagerNative.getDefault()
                                                    .canBypassWorkChallenge(intent);
                                        } catch (RemoteException e) {
                                        }
                                        // For direct-boot aware activities, they can be shown when
                                        // the device is still locked without triggering the work
                                        // challenge.
                                        if ((!canBypass) && startWorkChallengeIfNecessary(userId,
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
                                    overrideActivityPendingAppTransition(keyguardShowing
                                            && !afterKeyguardGone);
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
                    }.start();

                    // close the shade if it was open
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL,
                            true /* force */, true /* delayed */,
                            NotificationPanelView.SPEED_UP_FACTOR_CLICKED);
                    visibilityChanged(false);

                    return true;
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

    public void animateCollapsePanels(int flags, boolean force) {
    }

    public void animateCollapsePanels(int flags, boolean force, boolean delayed) {
    }

    public void animateCollapsePanels(int flags, boolean force, boolean delayed,
            float speedUpFactor) {
    }

    public void overrideActivityPendingAppTransition(boolean keyguardShowing) {
        if (keyguardShowing) {
            try {
                mWindowManagerService.overridePendingAppTransition(null, 0, 0, null);
            } catch (RemoteException e) {
                Log.w(TAG, "Error overriding app transition: " + e);
            }
        }
    }

    protected boolean startWorkChallengeIfNecessary(int userId, IntentSender intendSender,
            String notificationKey) {
        final Intent newIntent = mKeyguardManager.createConfirmDeviceCredentialIntent(null,
                null, userId);
        if (newIntent == null) {
            return false;
        }
        final Intent callBackIntent = new Intent(
                WORK_CHALLENGE_UNLOCKED_NOTIFICATION_ACTION);
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
            ActivityManagerNative.getDefault().startConfirmDeviceCredentialIntent(newIntent);
        } catch (RemoteException ex) {
            // ignore
        }
        return true;
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
                dismissPopups();
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
     * The LEDs are turned off when the notification panel is shown, even just a little bit.
     * See also PhoneStatusBar.setPanelExpanded for another place where we attempt to do this.
     */
    protected void handleVisibleToUserChanged(boolean visibleToUser) {
        try {
            if (visibleToUser) {
                boolean pinnedHeadsUp = mHeadsUpManager.hasPinnedHeadsUp();
                boolean clearNotificationEffects =
                        !isPanelFullyCollapsed() &&
                        (mState == StatusBarState.SHADE || mState == StatusBarState.SHADE_LOCKED);
                int notificationLoad = mNotificationData.getActiveNotifications().size();
                if (pinnedHeadsUp && isPanelFullyCollapsed())  {
                    notificationLoad = 1;
                } else {
                    MetricsLogger.histogram(mContext, "note_load", notificationLoad);
                }
                mBarService.onPanelRevealed(clearNotificationEffects, notificationLoad);
            } else {
                mBarService.onPanelHidden();
            }
        } catch (RemoteException ex) {
            // Won't fail unless the world has ended.
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

    public abstract boolean isPanelFullyCollapsed();

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
        return entry.notification;
    }

    protected NotificationData.Entry createNotificationViews(StatusBarNotification sbn) {
        if (DEBUG) {
            Log.d(TAG, "createNotificationViews(notification=" + sbn);
        }
        final StatusBarIconView iconView = createIcon(sbn);
        if (iconView == null) {
            return null;
        }

        // Construct the expanded view.
        NotificationData.Entry entry = new NotificationData.Entry(sbn, iconView);
        if (!inflateViews(entry, mStackScroller)) {
            handleNotificationError(sbn, "Couldn't expand RemoteViews for: " + sbn);
            return null;
        }
        return entry;
    }

    public StatusBarIconView createIcon(StatusBarNotification sbn) {
        // Construct the icon.
        Notification n = sbn.getNotification();
        final StatusBarIconView iconView = new StatusBarIconView(mContext,
                sbn.getPackageName() + "/0x" + Integer.toHexString(sbn.getId()), n);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        final Icon smallIcon = n.getSmallIcon();
        if (smallIcon == null) {
            handleNotificationError(sbn,
                    "No small icon in notification from " + sbn.getPackageName());
            return null;
        }
        final StatusBarIcon ic = new StatusBarIcon(
                sbn.getUser(),
                sbn.getPackageName(),
                smallIcon,
                n.iconLevel,
                n.number,
                StatusBarIconView.contentDescForNotification(mContext, n));
        if (!iconView.set(ic)) {
            handleNotificationError(sbn, "Couldn't create icon: " + ic);
            return null;
        }
        return iconView;
    }

    protected void addNotificationViews(Entry entry, RankingMap ranking) {
        if (entry == null) {
            return;
        }
        // Add the expanded view and icon.
        mNotificationData.add(entry, ranking);
        updateNotifications();
    }

    /**
     * @param recompute wheter the number should be recomputed
     * @return The number of notifications we show on Keyguard.
     */
    protected abstract int getMaxKeyguardNotifications(boolean recompute);

    /**
     * Updates expanded, dimmed and locked states of notification rows.
     */
    protected void updateRowStates() {
        mKeyguardIconOverflowContainer.getIconsView().removeAllViews();
        final int N = mStackScroller.getChildCount();


        int visibleNotifications = 0;
        boolean onKeyguard = mState == StatusBarState.KEYGUARD;
        int maxNotifications = 0;
        if (onKeyguard) {
            maxNotifications = getMaxKeyguardNotifications(true /* recompute */);
        }
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
            boolean childNotification = mGroupManager.isChildInGroupWithSummary(entry.notification);
            if (onKeyguard) {
                row.setOnKeyguard(true);
            } else {
                row.setOnKeyguard(false);
                row.setSystemExpanded(visibleNotifications == 0 && !childNotification);
            }
            boolean suppressedSummary = mGroupManager.isSummaryOfSuppressedGroup(
                    entry.notification) && !entry.row.isRemoved();
            boolean childWithVisibleSummary = childNotification
                    && mGroupManager.getGroupSummary(entry.notification).getVisibility()
                    == View.VISIBLE;
            boolean showOnKeyguard = shouldShowOnKeyguard(entry.notification);
            if (suppressedSummary || (isLockscreenPublicMode() && !mShowLockscreenNotifications) ||
                    (onKeyguard && !childWithVisibleSummary
                            && (visibleNotifications >= maxNotifications || !showOnKeyguard))) {
                entry.row.setVisibility(View.GONE);
                if (onKeyguard && showOnKeyguard && !childNotification && !suppressedSummary) {
                    mKeyguardIconOverflowContainer.getIconsView().addNotification(entry);
                }
            } else {
                boolean wasGone = entry.row.getVisibility() == View.GONE;
                if (wasGone) {
                    entry.row.setVisibility(View.VISIBLE);
                }
                if (!childNotification && !entry.row.isRemoved()) {
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

        mStackScroller.updateOverflowContainerVisibility(onKeyguard
                && mKeyguardIconOverflowContainer.getIconsView().getChildCount() > 0);

        mStackScroller.changeViewPosition(mDismissView, mStackScroller.getChildCount() - 1);
        mStackScroller.changeViewPosition(mEmptyShadeView, mStackScroller.getChildCount() - 2);
        mStackScroller.changeViewPosition(mKeyguardIconOverflowContainer,
                mStackScroller.getChildCount() - 3);

        if (onKeyguard) {
            hideWeatherPanelIfNecessary(visibleNotifications, getMaxKeyguardNotifications(true));
        }
    }

    private void hideWeatherPanelIfNecessary(int visibleNotifications, int maxKeyguardNotifications) {
        final ContentResolver resolver = mContext.getContentResolver();
        int notifications = visibleNotifications;
        if (mKeyguardIconOverflowContainer.getIconsView().getChildCount() > 0) {
            notifications += 1;
        }
        Settings.System.putInt(resolver,
                Settings.System.LOCK_SCREEN_VISIBLE_NOTIFICATIONS, notifications);
        maxKeyguardNotifications = getMaxKeyguardNotifications(true);
    }

    public boolean shouldShowOnKeyguard(StatusBarNotification sbn) {
        return mShowLockscreenNotifications && !mNotificationData.isAmbient(sbn.getKey())
            && importanceToLevel(mNotificationData.getImportance(sbn.getKey()))
               > importanceToLevel(IMPORTANCE_VERY_LOW);
    }

    protected void setZenMode(int mode) {
        if (!isDeviceProvisioned()) return;
        mZenMode = mode;
        updateNotifications();
    }

    // extended in PhoneStatusBar
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

    protected abstract void haltTicker();
    protected abstract void setAreThereNotifications();
    protected abstract void updateNotifications();
    protected abstract void tick(StatusBarNotification n, boolean firstTime, boolean isMusic, MediaMetadata mediaMetaData);
    public abstract boolean shouldDisableNavbarGestures();

    public abstract void addNotification(StatusBarNotification notification,
            RankingMap ranking, Entry oldEntry);
    protected abstract void updateNotificationRanking(RankingMap ranking);
    public abstract void removeNotification(String key, RankingMap ranking);

    public void updateNotification(StatusBarNotification notification, RankingMap ranking) {
        if (DEBUG) Log.d(TAG, "updateNotification(" + notification + ")");

        final String key = notification.getKey();
        Entry entry = mNotificationData.get(key);
        if (entry == null) {
            return;
        } else {
            mHeadsUpEntriesToRemoveOnSwitch.remove(entry);
            mRemoteInputEntriesToRemoveOnCollapse.remove(entry);
        }

        Notification n = notification.getNotification();
        mNotificationData.updateRanking(ranking);

        boolean applyInPlace;
        try {
            applyInPlace = entry.cacheContentViews(mContext, notification.getNotification());
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to get notification remote views", e);
            applyInPlace = false;
        }
        boolean shouldPeek = shouldPeek(entry, notification);
        boolean alertAgain = alertAgain(entry, n);
        if (DEBUG) {
            Log.d(TAG, "applyInPlace=" + applyInPlace
                    + " shouldPeek=" + shouldPeek
                    + " alertAgain=" + alertAgain);
        }
        boolean updateTicker = n.tickerText != null
                && !TextUtils.equals(n.tickerText,
                entry.notification.getNotification().tickerText);

        final StatusBarNotification oldNotification = entry.notification;
        entry.notification = notification;
        mGroupManager.onEntryUpdated(entry, oldNotification);

        boolean updateSuccessful = false;
        if (applyInPlace) {
            if (DEBUG) Log.d(TAG, "reusing notification for key: " + key);
            try {
                if (entry.icon != null) {
                    // Update the icon
                    final StatusBarIcon ic = new StatusBarIcon(
                            notification.getUser(),
                            notification.getPackageName(),
                            n.getSmallIcon(),
                            n.iconLevel,
                            n.number,
                            StatusBarIconView.contentDescForNotification(mContext, n));
                    entry.icon.setNotification(n);
                    if (!entry.icon.set(ic)) {
                        handleNotificationError(notification, "Couldn't update icon: " + ic);
                        return;
                    }
                }
                updateNotificationViews(entry, notification);
                updateSuccessful = true;
            }
            catch (RuntimeException e) {
                // It failed to apply cleanly.
                Log.w(TAG, "Couldn't reapply views for package " +
                        notification.getPackageName(), e);
            }
        }
        if (!updateSuccessful) {
            if (DEBUG) Log.d(TAG, "not reusing notification for key: " + key);
            final StatusBarIcon ic = new StatusBarIcon(
                    notification.getUser(),
                    notification.getPackageName(),
                    n.getSmallIcon(),
                    n.iconLevel,
                    n.number,
                    StatusBarIconView.contentDescForNotification(mContext, n));
            entry.icon.setNotification(n);
            entry.icon.set(ic);
            if (!inflateViews(entry, mStackScroller)) {
                handleNotificationError(notification, "Couldn't update remote views for: "
                        + notification);
            }
        }
        updateHeadsUp(key, entry, shouldPeek, alertAgain);
        updateNotifications();

        if (!notification.isClearable()) {
            // The user may have performed a dismiss action on the notification, since it's
            // not clearable we should snap it back.
            mStackScroller.snapViewIfNeeded(entry.row);
        }

        // Is this for you?
        boolean isForCurrentUser = isNotificationForCurrentProfiles(notification);
        Log.d(TAG, "notification is " + (isForCurrentUser ? "" : "not ") + "for you");

        // Restart the ticker if it's still running
        if (updateTicker && isForCurrentUser) {
            haltTicker();
            tick(notification, false, false, null);
          }

        setAreThereNotifications();
    }

    protected abstract void updateHeadsUp(String key, Entry entry, boolean shouldPeek,
            boolean alertAgain);

    private void updateNotificationViews(Entry entry, StatusBarNotification sbn) {
        final RemoteViews contentView = entry.cachedContentView;
        final RemoteViews bigContentView = entry.cachedBigContentView;
        final RemoteViews headsUpContentView = entry.cachedHeadsUpContentView;
        final RemoteViews publicContentView = entry.cachedPublicContentView;

        // Reapply the RemoteViews
        contentView.reapply(mContext, entry.getContentView(), mOnClickHandler);
        if (bigContentView != null && entry.getExpandedContentView() != null) {
            bigContentView.reapply(sbn.getPackageContext(mContext),
                    entry.getExpandedContentView(),
                    mOnClickHandler);
        }
        View headsUpChild = entry.getHeadsUpContentView();
        if (headsUpContentView != null && headsUpChild != null) {
            headsUpContentView.reapply(sbn.getPackageContext(mContext),
                    headsUpChild, mOnClickHandler);
        }
        if (publicContentView != null && entry.getPublicContentView() != null) {
            publicContentView.reapply(sbn.getPackageContext(mContext),
                    entry.getPublicContentView(), mOnClickHandler);
        }
        // update the contentIntent
        mNotificationClicker.register(entry.row, sbn);

        entry.row.onNotificationUpdated(entry);
        entry.row.resetHeight();
    }

    protected void updatePublicContentView(Entry entry,
            StatusBarNotification sbn) {
        final RemoteViews publicContentView = entry.cachedPublicContentView;
        View inflatedView = entry.getPublicContentView();
        if (entry.autoRedacted && publicContentView != null && inflatedView != null) {
            final boolean disabledByPolicy =
                    !adminAllowsUnredactedNotifications(entry.notification.getUserId());
            String notificationHiddenText = mContext.getString(disabledByPolicy
                    ? com.android.internal.R.string.notification_hidden_by_policy_text
                    : com.android.internal.R.string.notification_hidden_text);
            TextView titleView = (TextView) inflatedView.findViewById(android.R.id.title);
            if (titleView != null
                    && !titleView.getText().toString().equals(notificationHiddenText)) {
                publicContentView.setTextViewText(android.R.id.title, notificationHiddenText);
                publicContentView.reapply(sbn.getPackageContext(mContext),
                        inflatedView, mOnClickHandler);
                entry.row.onNotificationUpdated(entry);
            }
        }
    }

    protected void notifyHeadsUpScreenOff() {
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
        final ActivityManager am = (ActivityManager)
            mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo foregroundApp = null;
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            foregroundApp = tasks.get(0);
        }
        boolean isDialerForegroundApp = foregroundApp != null &&
                foregroundApp.baseActivity.getPackageName().toLowerCase().contains("dialer");
        boolean isNotificationFromDialer = sbn.getPackageName().toLowerCase().contains("dialer");

        boolean alwaysHeadsUpForThis = !isDialerForegroundApp && isNotificationFromDialer && mIsAlwaysHeadsupDialer;
        if ((!mUseHeadsUp && !alwaysHeadsUpForThis) || isDeviceInVrMode()) {
            return false;
        }

        boolean inUse = mPowerManager.isScreenOn();
        try {
            inUse = inUse && !mDreamManager.isDreaming();
        } catch (RemoteException e) {
            Log.d(TAG, "failed to query dream manager", e);
        }

        if (!inUse) {
            if (DEBUG) {
                Log.d(TAG, "No peeking: not in use: " + sbn.getKey());
            }
            return false;
        }

        if (mNotificationData.shouldSuppressScreenOn(sbn.getKey())) {
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

        if (importanceToLevel(mNotificationData.getImportance(sbn.getKey()))
            < importanceToLevel(IMPORTANCE_HIGH)) {
            if (DEBUG) Log.d(TAG, "No peeking: unimportant notification: " + sbn.getKey());
            return false;
        }

        if (sbn.getNotification().fullScreenIntent != null) {
            if (mAccessibilityManager.isTouchExplorationEnabled()) {
                if (DEBUG) Log.d(TAG, "No peeking: accessible fullscreen: " + sbn.getKey());
                return false;
            } else {
                // we only allow head-up on the lockscreen if it doesn't have a fullscreen intent
                return !mStatusBarKeyguardViewManager.isShowing()
                        || mStatusBarKeyguardViewManager.isOccluded();
            }
        }

        return true;
    }

    protected abstract boolean isSnoozedPackage(StatusBarNotification sbn);

    public void setInteracting(int barWindow, boolean interacting) {
        // hook for subclasses
    }

    public void setBouncerShowing(boolean bouncerShowing) {
        mBouncerShowing = bouncerShowing;
    }

    /**
     * @return Whether the security bouncer from Keyguard is showing.
     */
    public boolean isBouncerShowing() {
        return mBouncerShowing;
    }

    public void destroy() {
        mContext.unregisterReceiver(mBroadcastReceiver);
        try {
            mNotificationListener.unregisterAsSystemService();
        } catch (RemoteException e) {
            // Ignore.
        }
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
        try {
            mBarService.onNotificationExpansionChanged(key, userAction, expanded);
        } catch (RemoteException e) {
            // Ignore.
        }
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

    @Override
    public void startAssist(Bundle args) {
        if (mAssistManager != null) {
            mAssistManager.startAssist(args);
        }
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
}
