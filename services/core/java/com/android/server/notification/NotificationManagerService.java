/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

package com.android.server.notification;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.Notification.CATEGORY_CALL;
import static android.app.Notification.FLAG_AUTOGROUP_SUMMARY;
import static android.app.Notification.FLAG_BUBBLE;
import static android.app.Notification.FLAG_FOREGROUND_SERVICE;
import static android.app.Notification.FLAG_NO_CLEAR;
import static android.app.Notification.FLAG_ONGOING_EVENT;
import static android.app.Notification.FLAG_ONLY_ALERT_ONCE;
import static android.app.NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED;
import static android.app.NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED;
import static android.app.NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED;
import static android.app.NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_MIN;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECTS_UNSET;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR;
import static android.content.Context.BIND_ALLOW_WHITELIST_MANAGEMENT;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_FOREGROUND_SERVICE;
import static android.content.Context.BIND_NOT_PERCEPTIBLE;
import static android.content.pm.ActivityInfo.DOCUMENT_LAUNCH_ALWAYS;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_TELEVISION;
import static android.content.pm.PackageManager.MATCH_ALL;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_CRITICAL;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_NORMAL;
import static android.os.UserHandle.USER_NULL;
import static android.os.UserHandle.USER_SYSTEM;
import static android.service.notification.NotificationListenerService.HINT_HOST_DISABLE_CALL_EFFECTS;
import static android.service.notification.NotificationListenerService.HINT_HOST_DISABLE_EFFECTS;
import static android.service.notification.NotificationListenerService.HINT_HOST_DISABLE_NOTIFICATION_EFFECTS;
import static android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_ADDED;
import static android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_DELETED;
import static android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_UPDATED;
import static android.service.notification.NotificationListenerService.REASON_APP_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_APP_CANCEL_ALL;
import static android.service.notification.NotificationListenerService.REASON_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_CANCEL_ALL;
import static android.service.notification.NotificationListenerService.REASON_CHANNEL_BANNED;
import static android.service.notification.NotificationListenerService.REASON_CLICK;
import static android.service.notification.NotificationListenerService.REASON_ERROR;
import static android.service.notification.NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED;
import static android.service.notification.NotificationListenerService.REASON_LISTENER_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_LISTENER_CANCEL_ALL;
import static android.service.notification.NotificationListenerService.REASON_PACKAGE_BANNED;
import static android.service.notification.NotificationListenerService.REASON_PACKAGE_CHANGED;
import static android.service.notification.NotificationListenerService.REASON_PACKAGE_SUSPENDED;
import static android.service.notification.NotificationListenerService.REASON_PROFILE_TURNED_OFF;
import static android.service.notification.NotificationListenerService.REASON_SNOOZED;
import static android.service.notification.NotificationListenerService.REASON_TIMEOUT;
import static android.service.notification.NotificationListenerService.REASON_UNAUTOBUNDLED;
import static android.service.notification.NotificationListenerService.REASON_USER_STOPPED;
import static android.service.notification.NotificationListenerService.TRIM_FULL;
import static android.service.notification.NotificationListenerService.TRIM_LIGHT;
import static android.util.StatsLogInternal.BUBBLE_DEVELOPER_ERROR_REPORTED__ERROR__ACTIVITY_INFO_MISSING;
import static android.util.StatsLogInternal.BUBBLE_DEVELOPER_ERROR_REPORTED__ERROR__ACTIVITY_INFO_NOT_RESIZABLE;
import static android.util.StatsLogInternal.BUBBLE_DEVELOPER_ERROR_REPORTED__ERROR__DOCUMENT_LAUNCH_NOT_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;

import static com.android.server.am.PendingIntentRecord.FLAG_ACTIVITY_SENDER;
import static com.android.server.am.PendingIntentRecord.FLAG_BROADCAST_SENDER;
import static com.android.server.am.PendingIntentRecord.FLAG_SERVICE_SENDER;
import static com.android.server.notification.PreferencesHelper.DEFAULT_ALLOW_BUBBLE;
import static com.android.server.utils.PriorityDump.PRIORITY_ARG;
import static com.android.server.utils.PriorityDump.PRIORITY_ARG_CRITICAL;
import static com.android.server.utils.PriorityDump.PRIORITY_ARG_NORMAL;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.AutomaticZenRule;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.ITransientNotification;
import android.app.IUriGrantsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.NotificationManager.Policy;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.app.StatusBarManager;
import android.app.UriGrantsManager;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.backup.BackupManager;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManagerInternal;
import android.companion.ICompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioManagerInternal;
import android.media.IRingtonePlayer;
import android.metrics.LogMaker;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.notification.Adjustment;
import android.service.notification.Condition;
import android.service.notification.IConditionProvider;
import android.service.notification.INotificationListener;
import android.service.notification.IStatusBarNotificationHolder;
import android.service.notification.ListenersDisablingEffectsProto;
import android.service.notification.NotificationAssistantService;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationRankingUpdate;
import android.service.notification.NotificationRecordProto;
import android.service.notification.NotificationServiceDumpProto;
import android.service.notification.NotificationStats;
import android.service.notification.SnoozeCriterion;
import android.service.notification.StatusBarNotification;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeProto;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.IntArray;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.StatsLog;
import android.util.TimeUtils;
import android.util.Xml;
import android.util.proto.ProtoOutputStream;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.os.BackgroundThread;
import com.android.internal.os.SomeArgs;
import com.android.internal.statusbar.NotificationVisibility;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.CollectionUtils;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.Preconditions;
import com.android.internal.util.XmlUtils;
import com.android.internal.util.function.TriPredicate;
import com.android.server.DeviceIdleController;
import com.android.server.EventLogTags;
import com.android.server.IoThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.lights.Light;
import com.android.server.lights.LightsManager;
import com.android.server.notification.ManagedServices.ManagedServiceInfo;
import com.android.server.notification.ManagedServices.UserProfiles;
import com.android.server.pm.PackageManagerService;
import com.android.server.policy.PhoneWindowManager;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.lineageos.internal.notification.LedValues;
import org.lineageos.internal.notification.LineageNotificationLights;

import libcore.io.IoUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** {@hide} */
public class NotificationManagerService extends SystemService {
    static final String TAG = "NotificationService";
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean ENABLE_CHILD_NOTIFICATIONS
            = SystemProperties.getBoolean("debug.child_notifs", true);

    // pullStats report request: undecorated remote view stats
    public static final int REPORT_REMOTE_VIEWS = 0x01;

    static final boolean DEBUG_INTERRUPTIVENESS = SystemProperties.getBoolean(
            "debug.notification.interruptiveness", false);

    static final int MAX_PACKAGE_NOTIFICATIONS = 25;
    static final float DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE = 5f;

    // message codes
    static final int MESSAGE_DURATION_REACHED = 2;
    // 3: removed to a different handler
    static final int MESSAGE_SEND_RANKING_UPDATE = 4;
    static final int MESSAGE_LISTENER_HINTS_CHANGED = 5;
    static final int MESSAGE_LISTENER_NOTIFICATION_FILTER_CHANGED = 6;
    static final int MESSAGE_FINISH_TOKEN_TIMEOUT = 7;
    static final int MESSAGE_ON_PACKAGE_CHANGED = 8;

    // ranking thread messages
    private static final int MESSAGE_RECONSIDER_RANKING = 1000;
    private static final int MESSAGE_RANKING_SORT = 1001;

    static final int LONG_DELAY = PhoneWindowManager.TOAST_WINDOW_TIMEOUT;
    static final int SHORT_DELAY = 2000; // 2 seconds

    // 1 second past the ANR timeout.
    static final int FINISH_TOKEN_TIMEOUT = 11 * 1000;

    static final long[] DEFAULT_VIBRATE_PATTERN = {0, 250, 250, 250};

    static final long SNOOZE_UNTIL_UNSPECIFIED = -1;

    static final int VIBRATE_PATTERN_MAXLEN = 8 * 2 + 1; // up to eight bumps

    static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_NOTIFICATION;

    static final boolean ENABLE_BLOCKED_TOASTS = true;

    static final String[] DEFAULT_ALLOWED_ADJUSTMENTS = new String[] {
            Adjustment.KEY_CONTEXTUAL_ACTIONS,
            Adjustment.KEY_TEXT_REPLIES};

    static final String[] NON_BLOCKABLE_DEFAULT_ROLES = new String[] {
            RoleManager.ROLE_DIALER,
            RoleManager.ROLE_EMERGENCY
    };

    // When #matchesCallFilter is called from the ringer, wait at most
    // 3s to resolve the contacts. This timeout is required since
    // ContactsProvider might take a long time to start up.
    //
    // Return STARRED_CONTACT when the timeout is hit in order to avoid
    // missed calls in ZEN mode "Important".
    static final int MATCHES_CALL_FILTER_CONTACTS_TIMEOUT_MS = 3000;
    static final float MATCHES_CALL_FILTER_TIMEOUT_AFFINITY =
            ValidateNotificationPeople.STARRED_CONTACT;

    /** notification_enqueue status value for a newly enqueued notification. */
    private static final int EVENTLOG_ENQUEUE_STATUS_NEW = 0;

    /** notification_enqueue status value for an existing notification. */
    private static final int EVENTLOG_ENQUEUE_STATUS_UPDATE = 1;

    /** notification_enqueue status value for an ignored notification. */
    private static final int EVENTLOG_ENQUEUE_STATUS_IGNORED = 2;
    private static final long MIN_PACKAGE_OVERRATE_LOG_INTERVAL = 5000; // milliseconds

    private static final long DELAY_FOR_ASSISTANT_TIME = 100;

    private static final String ACTION_NOTIFICATION_TIMEOUT =
            NotificationManagerService.class.getSimpleName() + ".TIMEOUT";
    private static final int REQUEST_CODE_TIMEOUT = 1;
    private static final String SCHEME_TIMEOUT = "timeout";
    private static final String EXTRA_KEY = "key";

    private IActivityManager mAm;
    private ActivityManager mActivityManager;
    private ActivityManagerInternal mAmi;
    private IPackageManager mPackageManager;
    private PackageManager mPackageManagerClient;
    AudioManager mAudioManager;
    AudioManagerInternal mAudioManagerInternal;
    @Nullable StatusBarManagerInternal mStatusBar;
    Vibrator mVibrator;
    private WindowManagerInternal mWindowManagerInternal;
    private AlarmManager mAlarmManager;
    private ICompanionDeviceManager mCompanionManager;
    private AccessibilityManager mAccessibilityManager;
    private IDeviceIdleController mDeviceIdleController;
    private IUriGrantsManager mUgm;
    private UriGrantsManagerInternal mUgmInternal;
    private RoleObserver mRoleObserver;
    private UserManager mUm;

    final IBinder mForegroundToken = new Binder();
    private WorkerHandler mHandler;
    private final HandlerThread mRankingThread = new HandlerThread("ranker",
            Process.THREAD_PRIORITY_BACKGROUND);

    private Light mNotificationLight;
    Light mAttentionLight;

    private long[] mFallbackVibrationPattern;
    private boolean mUseAttentionLight;
    boolean mHasLight = true;
    boolean mLightEnabled;
    boolean mSystemReady;

    private boolean mDisableNotificationEffects;
    private int mCallState;
    private String mSoundNotificationKey;
    private String mVibrateNotificationKey;

    private final SparseArray<ArraySet<ComponentName>> mListenersDisablingEffects =
            new SparseArray<>();
    private List<ComponentName> mEffectsSuppressors = new ArrayList<>();
    private int mListenerHints;  // right now, all hints are global
    private int mInterruptionFilter = NotificationListenerService.INTERRUPTION_FILTER_UNKNOWN;

    // for enabling and disabling notification pulse behavior
    boolean mScreenOn = true;
    protected boolean mInCallStateOffHook = false;
    boolean mNotificationPulseEnabled;

    private boolean mSoundVibScreenOn;
    private boolean mVibrateOnNotifications;

    // Gaming Mode
    private boolean mGamingMode;
    private boolean mSoundVibGamingMode;

    private Uri mInCallNotificationUri;
    private AudioAttributes mInCallNotificationAudioAttributes;
    private float mInCallNotificationVolume;
    private Binder mCallNotificationToken = null;

    // used as a mutex for access to all active notifications & listeners
    final Object mNotificationLock = new Object();
    @GuardedBy("mNotificationLock")
    final ArrayList<NotificationRecord> mNotificationList = new ArrayList<>();
    @GuardedBy("mNotificationLock")
    final ArrayMap<String, NotificationRecord> mNotificationsByKey = new ArrayMap<>();
    @GuardedBy("mNotificationLock")
    final ArrayList<NotificationRecord> mEnqueuedNotifications = new ArrayList<>();
    @GuardedBy("mNotificationLock")
    final ArrayMap<Integer, ArrayMap<String, String>> mAutobundledSummaries = new ArrayMap<>();
    final ArrayList<ToastRecord> mToastQueue = new ArrayList<>();
    final ArrayMap<String, NotificationRecord> mSummaryByGroupKey = new ArrayMap<>();
    final ArrayMap<String, Long> mLastSoundTimestamps = new ArrayMap<>();

    // The last key in this list owns the hardware.
    ArrayList<String> mLights = new ArrayList<>();

    private AppOpsManager mAppOps;
    private UsageStatsManagerInternal mAppUsageStats;
    private DevicePolicyManagerInternal mDpm;

    private Archive mArchive;

    // Persistent storage for notification policy
    private AtomicFile mPolicyFile;

    private static final int DB_VERSION = 1;

    private static final String TAG_NOTIFICATION_POLICY = "notification-policy";
    private static final String ATTR_VERSION = "version";

    private static final String LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_TAG =
            "allow-secure-notifications-on-lockscreen";
    private static final String LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_VALUE = "value";

    private RankingHelper mRankingHelper;
    private PreferencesHelper mPreferencesHelper;

    private final UserProfiles mUserProfiles = new UserProfiles();
    private NotificationListeners mListeners;
    private NotificationAssistants mAssistants;
    private ConditionProviders mConditionProviders;
    private NotificationUsageStats mUsageStats;
    private boolean mLockScreenAllowSecureNotifications = true;

    private static final int MY_UID = Process.myUid();
    private static final int MY_PID = Process.myPid();
    private static final IBinder WHITELIST_TOKEN = new Binder();
    private RankingHandler mRankingHandler;
    private long mLastOverRateLogTime;
    private float mMaxPackageEnqueueRate = DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE;

    private SnoozeHelper mSnoozeHelper;
    private GroupHelper mGroupHelper;
    private int mAutoGroupAtCount;
    private boolean mIsTelevision;
    private boolean mIsAutomotive;
    private boolean mNotificationEffectsEnabledForAutomotive;

    private MetricsLogger mMetricsLogger;
    private TriPredicate<String, Integer, String> mAllowedManagedServicePackages;

    private final SavePolicyFileRunnable mSavePolicyFile = new SavePolicyFileRunnable();

    private LineageNotificationLights mLineageNotificationLights;

    private static class Archive {
        final int mBufferSize;
        final ArrayDeque<StatusBarNotification> mBuffer;

        public Archive(int size) {
            mBufferSize = size;
            mBuffer = new ArrayDeque<StatusBarNotification>(mBufferSize);
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            final int N = mBuffer.size();
            sb.append("Archive (");
            sb.append(N);
            sb.append(" notification");
            sb.append((N==1)?")":"s)");
            return sb.toString();
        }

        public void record(StatusBarNotification nr) {
            if (mBuffer.size() == mBufferSize) {
                mBuffer.removeFirst();
            }

            // We don't want to store the heavy bits of the notification in the archive,
            // but other clients in the system process might be using the object, so we
            // store a (lightened) copy.
            mBuffer.addLast(nr.cloneLight());
        }

        public Iterator<StatusBarNotification> descendingIterator() {
            return mBuffer.descendingIterator();
        }

        public StatusBarNotification[] getArray(int count) {
            if (count == 0) count = mBufferSize;
            final StatusBarNotification[] a
                    = new StatusBarNotification[Math.min(count, mBuffer.size())];
            Iterator<StatusBarNotification> iter = descendingIterator();
            int i=0;
            while (iter.hasNext() && i < count) {
                a[i++] = iter.next();
            }
            return a;
        }

    }

    protected void readDefaultApprovedServices(int userId) {
        String defaultListenerAccess = getContext().getResources().getString(
                com.android.internal.R.string.config_defaultListenerAccessPackages);
        if (defaultListenerAccess != null) {
            for (String whitelisted :
                    defaultListenerAccess.split(ManagedServices.ENABLED_SERVICES_SEPARATOR)) {
                // Gather all notification listener components for candidate pkgs.
                Set<ComponentName> approvedListeners =
                        mListeners.queryPackageForServices(whitelisted,
                                MATCH_DIRECT_BOOT_AWARE
                                        | MATCH_DIRECT_BOOT_UNAWARE, userId);
                for (ComponentName cn : approvedListeners) {
                    try {
                        getBinderService().setNotificationListenerAccessGrantedForUser(cn,
                                    userId, true);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        String defaultDndAccess = getContext().getResources().getString(
                com.android.internal.R.string.config_defaultDndAccessPackages);
        if (defaultDndAccess != null) {
            for (String whitelisted :
                    defaultDndAccess.split(ManagedServices.ENABLED_SERVICES_SEPARATOR)) {
                try {
                    getBinderService().setNotificationPolicyAccessGranted(whitelisted, true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        setDefaultAssistantForUser(userId);
    }

    protected void setDefaultAssistantForUser(int userId) {
        List<ComponentName> validAssistants = new ArrayList<>(
                mAssistants.queryPackageForServices(
                        null, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE, userId));

        List<String> candidateStrs = new ArrayList<>();
        candidateStrs.add(DeviceConfig.getProperty(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                SystemUiDeviceConfigFlags.NAS_DEFAULT_SERVICE));
        candidateStrs.add(getContext().getResources().getString(
                com.android.internal.R.string.config_defaultAssistantAccessComponent));

        for (String candidateStr : candidateStrs) {
            if (TextUtils.isEmpty(candidateStr)) {
                continue;
            }
            ComponentName candidate = ComponentName.unflattenFromString(candidateStr);
            if (candidate != null && validAssistants.contains(candidate)) {
                setNotificationAssistantAccessGrantedForUserInternal(candidate, userId, true);
                Slog.d(TAG, String.format("Set default NAS to be %s in %d", candidateStr, userId));
                return;
            } else {
                Slog.w(TAG, "Invalid default NAS config is found: " + candidateStr);
            }
        }
    }

    void readPolicyXml(InputStream stream, boolean forRestore, int userId)
            throws XmlPullParserException, NumberFormatException, IOException {
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(stream, StandardCharsets.UTF_8.name());
        XmlUtils.beginDocument(parser, TAG_NOTIFICATION_POLICY);
        boolean migratedManagedServices = false;
        boolean ineligibleForManagedServices = forRestore && mUm.isManagedProfile(userId);
        int outerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            if (ZenModeConfig.ZEN_TAG.equals(parser.getName())) {
                mZenModeHelper.readXml(parser, forRestore, userId);
            } else if (PreferencesHelper.TAG_RANKING.equals(parser.getName())){
                mPreferencesHelper.readXml(parser, forRestore, userId);
            }
            if (mListeners.getConfig().xmlTag.equals(parser.getName())) {
                if (ineligibleForManagedServices) {
                    continue;
                }
                mListeners.readXml(parser, mAllowedManagedServicePackages, forRestore, userId);
                migratedManagedServices = true;
            } else if (mAssistants.getConfig().xmlTag.equals(parser.getName())) {
                if (ineligibleForManagedServices) {
                    continue;
                }
                mAssistants.readXml(parser, mAllowedManagedServicePackages, forRestore, userId);
                migratedManagedServices = true;
            } else if (mConditionProviders.getConfig().xmlTag.equals(parser.getName())) {
                if (ineligibleForManagedServices) {
                    continue;
                }
                mConditionProviders.readXml(
                        parser, mAllowedManagedServicePackages, forRestore, userId);
                migratedManagedServices = true;
            }
            if (LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_TAG.equals(parser.getName())) {
                if (forRestore && userId != UserHandle.USER_SYSTEM) {
                    continue;
                }
                mLockScreenAllowSecureNotifications =
                        safeBoolean(parser.getAttributeValue(null,
                                        LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_VALUE), true);
            }
        }

        if (!migratedManagedServices) {
            mListeners.migrateToXml();
            mAssistants.migrateToXml();
            mConditionProviders.migrateToXml();
            handleSavePolicyFile();
        }

        mAssistants.resetDefaultAssistantsIfNecessary();
    }

    @VisibleForTesting
    protected void loadPolicyFile() {
        if (DBG) Slog.d(TAG, "loadPolicyFile");
        synchronized (mPolicyFile) {
            InputStream infile = null;
            try {
                infile = mPolicyFile.openRead();
                readPolicyXml(infile, false /*forRestore*/, UserHandle.USER_ALL);
            } catch (FileNotFoundException e) {
                // No data yet
                // Load default managed services approvals
                readDefaultApprovedServices(USER_SYSTEM);
            } catch (IOException e) {
                Log.wtf(TAG, "Unable to read notification policy", e);
            } catch (NumberFormatException e) {
                Log.wtf(TAG, "Unable to parse notification policy", e);
            } catch (XmlPullParserException e) {
                Log.wtf(TAG, "Unable to parse notification policy", e);
            } finally {
                IoUtils.closeQuietly(infile);
            }
        }
    }

    @VisibleForTesting
    protected void handleSavePolicyFile() {
        if (!IoThread.getHandler().hasCallbacks(mSavePolicyFile)) {
            IoThread.getHandler().post(mSavePolicyFile);
        }
    }

    private final class SavePolicyFileRunnable implements Runnable {
        @Override
        public void run() {
            if (DBG) Slog.d(TAG, "handleSavePolicyFile");
            synchronized (mPolicyFile) {
                final FileOutputStream stream;
                try {
                    stream = mPolicyFile.startWrite();
                } catch (IOException e) {
                    Slog.w(TAG, "Failed to save policy file", e);
                    return;
                }

                try {
                    writePolicyXml(stream, false /*forBackup*/, UserHandle.USER_ALL);
                    mPolicyFile.finishWrite(stream);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed to save policy file, restoring backup", e);
                    mPolicyFile.failWrite(stream);
                }
            }
            BackupManager.dataChanged(getContext().getPackageName());
        }
    }

    private void writePolicyXml(OutputStream stream, boolean forBackup, int userId)
            throws IOException {
        final XmlSerializer out = new FastXmlSerializer();
        out.setOutput(stream, StandardCharsets.UTF_8.name());
        out.startDocument(null, true);
        out.startTag(null, TAG_NOTIFICATION_POLICY);
        out.attribute(null, ATTR_VERSION, Integer.toString(DB_VERSION));
        mZenModeHelper.writeXml(out, forBackup, null, userId);
        mPreferencesHelper.writeXml(out, forBackup, userId);
        mListeners.writeXml(out, forBackup, userId);
        mAssistants.writeXml(out, forBackup, userId);
        mConditionProviders.writeXml(out, forBackup, userId);
        if (!forBackup || userId == UserHandle.USER_SYSTEM) {
            writeSecureNotificationsPolicy(out);
        }
        out.endTag(null, TAG_NOTIFICATION_POLICY);
        out.endDocument();
    }

    private static final class ToastRecord
    {
        final int pid;
        final String pkg;
        final ITransientNotification callback;
        int duration;
        int displayId;
        Binder token;

        ToastRecord(int pid, String pkg, ITransientNotification callback, int duration,
                Binder token, int displayId) {
            this.pid = pid;
            this.pkg = pkg;
            this.callback = callback;
            this.duration = duration;
            this.token = token;
            this.displayId = displayId;
        }

        void update(int duration) {
            this.duration = duration;
        }

        void dump(PrintWriter pw, String prefix, DumpFilter filter) {
            if (filter != null && !filter.matches(pkg)) return;
            pw.println(prefix + this);
        }

        @Override
        public final String toString()
        {
            return "ToastRecord{"
                + Integer.toHexString(System.identityHashCode(this))
                + " pkg=" + pkg
                + " callback=" + callback
                + " duration=" + duration;
        }
    }

    @VisibleForTesting
    final NotificationDelegate mNotificationDelegate = new NotificationDelegate() {

        @Override
        public void onSetDisabled(int status) {
            synchronized (mNotificationLock) {
                mDisableNotificationEffects =
                        (status & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0;
                if (disableNotificationEffects(null) != null) {
                    // cancel whatever's going on
                    long identity = Binder.clearCallingIdentity();
                    try {
                        final IRingtonePlayer player = mAudioManager.getRingtonePlayer();
                        if (player != null) {
                            player.stopAsync();
                        }
                    } catch (RemoteException e) {
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }

                    identity = Binder.clearCallingIdentity();
                    try {
                        mVibrator.cancel();
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            }
        }

        @Override
        public void onClearAll(int callingUid, int callingPid, int userId) {
            synchronized (mNotificationLock) {
                cancelAllLocked(callingUid, callingPid, userId, REASON_CANCEL_ALL, null,
                        /*includeCurrentProfiles*/ true);
            }
        }

        @Override
        public void onNotificationClick(int callingUid, int callingPid, String key,
                NotificationVisibility nv) {
            exitIdle();
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r == null) {
                    Slog.w(TAG, "No notification with key: " + key);
                    return;
                }
                final long now = System.currentTimeMillis();
                MetricsLogger.action(r.getItemLogMaker()
                        .setType(MetricsEvent.TYPE_ACTION)
                        .addTaggedData(MetricsEvent.NOTIFICATION_SHADE_INDEX, nv.rank)
                        .addTaggedData(MetricsEvent.NOTIFICATION_SHADE_COUNT, nv.count));
                EventLogTags.writeNotificationClicked(key,
                        r.getLifespanMs(now), r.getFreshnessMs(now), r.getExposureMs(now),
                        nv.rank, nv.count);

                StatusBarNotification sbn = r.sbn;
                cancelNotification(callingUid, callingPid, sbn.getPackageName(), sbn.getTag(),
                        sbn.getId(), Notification.FLAG_AUTO_CANCEL,
                        FLAG_FOREGROUND_SERVICE, false, r.getUserId(),
                        REASON_CLICK, nv.rank, nv.count, null);
                nv.recycle();
                reportUserInteraction(r);
            }
        }

        @Override
        public void onNotificationActionClick(int callingUid, int callingPid, String key,
                int actionIndex, Notification.Action action, NotificationVisibility nv,
                boolean generatedByAssistant) {
            exitIdle();
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r == null) {
                    Slog.w(TAG, "No notification with key: " + key);
                    return;
                }
                final long now = System.currentTimeMillis();
                MetricsLogger.action(r.getLogMaker(now)
                        .setCategory(MetricsEvent.NOTIFICATION_ITEM_ACTION)
                        .setType(MetricsEvent.TYPE_ACTION)
                        .setSubtype(actionIndex)
                        .addTaggedData(MetricsEvent.NOTIFICATION_SHADE_INDEX, nv.rank)
                        .addTaggedData(MetricsEvent.NOTIFICATION_SHADE_COUNT, nv.count)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ACTION_IS_SMART,
                                action.isContextual() ? 1 : 0)
                        .addTaggedData(
                                MetricsEvent.NOTIFICATION_SMART_SUGGESTION_ASSISTANT_GENERATED,
                                generatedByAssistant ? 1 : 0)
                        .addTaggedData(MetricsEvent.NOTIFICATION_LOCATION,
                                nv.location.toMetricsEventEnum()));

                EventLogTags.writeNotificationActionClicked(key, actionIndex,
                        r.getLifespanMs(now), r.getFreshnessMs(now), r.getExposureMs(now),
                        nv.rank, nv.count);
                nv.recycle();
                reportUserInteraction(r);
                mAssistants.notifyAssistantActionClicked(
                        r.sbn, actionIndex, action, generatedByAssistant);
            }
        }

        @Override
        public void onNotificationClear(int callingUid, int callingPid,
                String pkg, String tag, int id, int userId, String key,
                @NotificationStats.DismissalSurface int dismissalSurface,
                @NotificationStats.DismissalSentiment int dismissalSentiment,
                NotificationVisibility nv) {
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    r.recordDismissalSurface(dismissalSurface);
                    r.recordDismissalSentiment(dismissalSentiment);
                }
            }
            cancelNotification(callingUid, callingPid, pkg, tag, id, 0,
                    FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE,
                    true, userId, REASON_CANCEL, nv.rank, nv.count,null);
            nv.recycle();
        }

        @Override
        public void onPanelRevealed(boolean clearEffects, int items) {
            MetricsLogger.visible(getContext(), MetricsEvent.NOTIFICATION_PANEL);
            MetricsLogger.histogram(getContext(), "note_load", items);
            EventLogTags.writeNotificationPanelRevealed(items);
            if (clearEffects) {
                clearEffects();
            }
        }

        @Override
        public void onPanelHidden() {
            MetricsLogger.hidden(getContext(), MetricsEvent.NOTIFICATION_PANEL);
            EventLogTags.writeNotificationPanelHidden();
        }

        @Override
        public void clearEffects() {
            synchronized (mNotificationLock) {
                if (DBG) Slog.d(TAG, "clearEffects");
                clearSoundLocked();
                clearVibrateLocked();
                clearLightsLocked();
            }
        }

        @Override
        public void onNotificationError(int callingUid, int callingPid, String pkg, String tag,
                int id, int uid, int initialPid, String message, int userId) {
            final boolean fgService;
            synchronized (mNotificationLock) {
                NotificationRecord r = findNotificationLocked(pkg, tag, id, userId);
                fgService = r != null && (r.getNotification().flags & FLAG_FOREGROUND_SERVICE) != 0;
            }
            cancelNotification(callingUid, callingPid, pkg, tag, id, 0, 0, false, userId,
                    REASON_ERROR, null);
            if (fgService) {
                // Still crash for foreground services, preventing the not-crash behaviour abused
                // by apps to give us a garbage notification and silently start a fg service.
                Binder.withCleanCallingIdentity(
                        () -> mAm.crashApplication(uid, initialPid, pkg, -1,
                            "Bad notification(tag=" + tag + ", id=" + id + ") posted from package "
                                + pkg + ", crashing app(uid=" + uid + ", pid=" + initialPid + "): "
                                + message, true /* force */));
            }
        }

        @Override
        public void onNotificationVisibilityChanged(NotificationVisibility[] newlyVisibleKeys,
                NotificationVisibility[] noLongerVisibleKeys) {
            synchronized (mNotificationLock) {
                for (NotificationVisibility nv : newlyVisibleKeys) {
                    NotificationRecord r = mNotificationsByKey.get(nv.key);
                    if (r == null) continue;
                    if (!r.isSeen()) {
                        // Report to usage stats that notification was made visible
                        if (DBG) Slog.d(TAG, "Marking notification as visible " + nv.key);
                        reportSeen(r);
                    }
                    r.setVisibility(true, nv.rank, nv.count);
                    boolean isHun = (nv.location
                            == NotificationVisibility.NotificationLocation.LOCATION_FIRST_HEADS_UP);
                    // hasBeenVisiblyExpanded must be called after updating the expansion state of
                    // the NotificationRecord to ensure the expansion state is up-to-date.
                    if (isHun || r.hasBeenVisiblyExpanded()) {
                        logSmartSuggestionsVisible(r, nv.location.toMetricsEventEnum());
                    }
                    maybeRecordInterruptionLocked(r);
                    nv.recycle();
                }
                // Note that we might receive this event after notifications
                // have already left the system, e.g. after dismissing from the
                // shade. Hence not finding notifications in
                // mNotificationsByKey is not an exceptional condition.
                for (NotificationVisibility nv : noLongerVisibleKeys) {
                    NotificationRecord r = mNotificationsByKey.get(nv.key);
                    if (r == null) continue;
                    r.setVisibility(false, nv.rank, nv.count);
                    nv.recycle();
                }
            }
        }

        @Override
        public void onNotificationExpansionChanged(String key,
                boolean userAction, boolean expanded, int notificationLocation) {
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    r.stats.onExpansionChanged(userAction, expanded);
                    // hasBeenVisiblyExpanded must be called after updating the expansion state of
                    // the NotificationRecord to ensure the expansion state is up-to-date.
                    if (r.hasBeenVisiblyExpanded()) {
                        logSmartSuggestionsVisible(r, notificationLocation);
                    }
                    if (userAction) {
                        MetricsLogger.action(r.getItemLogMaker()
                                .setType(expanded ? MetricsEvent.TYPE_DETAIL
                                        : MetricsEvent.TYPE_COLLAPSE));
                    }
                    if (expanded && userAction) {
                        r.recordExpanded();
                        reportUserInteraction(r);
                    }
                    mAssistants.notifyAssistantExpansionChangedLocked(r.sbn, userAction, expanded);
                }
            }
        }

        @Override
        public void onNotificationDirectReplied(String key) {
            exitIdle();
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    r.recordDirectReplied();
                    mMetricsLogger.write(r.getLogMaker()
                            .setCategory(MetricsEvent.NOTIFICATION_DIRECT_REPLY_ACTION)
                            .setType(MetricsEvent.TYPE_ACTION));
                    reportUserInteraction(r);
                    mAssistants.notifyAssistantNotificationDirectReplyLocked(r.sbn);
                }
            }
        }

        @Override
        public void onNotificationSmartSuggestionsAdded(String key, int smartReplyCount,
                int smartActionCount, boolean generatedByAssistant, boolean editBeforeSending) {
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    r.setNumSmartRepliesAdded(smartReplyCount);
                    r.setNumSmartActionsAdded(smartActionCount);
                    r.setSuggestionsGeneratedByAssistant(generatedByAssistant);
                    r.setEditChoicesBeforeSending(editBeforeSending);
                }
            }
        }

        @Override
        public void onNotificationSmartReplySent(String key, int replyIndex, CharSequence reply,
                int notificationLocation, boolean modifiedBeforeSending) {

            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    LogMaker logMaker = r.getLogMaker()
                            .setCategory(MetricsEvent.SMART_REPLY_ACTION)
                            .setSubtype(replyIndex)
                            .addTaggedData(
                                    MetricsEvent.NOTIFICATION_SMART_SUGGESTION_ASSISTANT_GENERATED,
                                    r.getSuggestionsGeneratedByAssistant() ? 1 : 0)
                            .addTaggedData(MetricsEvent.NOTIFICATION_LOCATION,
                                    notificationLocation)
                            .addTaggedData(
                                    MetricsEvent.NOTIFICATION_SMART_REPLY_EDIT_BEFORE_SENDING,
                                    r.getEditChoicesBeforeSending() ? 1 : 0)
                            .addTaggedData(
                                    MetricsEvent.NOTIFICATION_SMART_REPLY_MODIFIED_BEFORE_SENDING,
                                    modifiedBeforeSending ? 1 : 0);
                    mMetricsLogger.write(logMaker);
                    // Treat clicking on a smart reply as a user interaction.
                    reportUserInteraction(r);
                    mAssistants.notifyAssistantSuggestedReplySent(
                            r.sbn, reply, r.getSuggestionsGeneratedByAssistant());
                }
            }
        }

        @Override
        public void onNotificationSettingsViewed(String key) {
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    r.recordViewedSettings();
                }
            }
        }

        @Override
        public void onNotificationBubbleChanged(String key, boolean isBubble) {
            synchronized (mNotificationLock) {
                NotificationRecord r = mNotificationsByKey.get(key);
                if (r != null) {
                    final StatusBarNotification n = r.sbn;
                    final int callingUid = n.getUid();
                    final String pkg = n.getPackageName();
                    if (isBubble && isNotificationAppropriateToBubble(r, pkg, callingUid,
                            null /* oldEntry */)) {
                        r.getNotification().flags |= FLAG_BUBBLE;
                    } else {
                        r.getNotification().flags &= ~FLAG_BUBBLE;
                    }
                }
            }
        }
    };

    @VisibleForTesting
    void logSmartSuggestionsVisible(NotificationRecord r, int notificationLocation) {
        // If the newly visible notification has smart suggestions
        // then log that the user has seen them.
        if ((r.getNumSmartRepliesAdded() > 0 || r.getNumSmartActionsAdded() > 0)
                && !r.hasSeenSmartReplies()) {
            r.setSeenSmartReplies(true);
            LogMaker logMaker = r.getLogMaker()
                    .setCategory(MetricsEvent.SMART_REPLY_VISIBLE)
                    .addTaggedData(MetricsEvent.NOTIFICATION_SMART_REPLY_COUNT,
                            r.getNumSmartRepliesAdded())
                    .addTaggedData(MetricsEvent.NOTIFICATION_SMART_ACTION_COUNT,
                            r.getNumSmartActionsAdded())
                    .addTaggedData(
                            MetricsEvent.NOTIFICATION_SMART_SUGGESTION_ASSISTANT_GENERATED,
                            r.getSuggestionsGeneratedByAssistant() ? 1 : 0)
                    // The fields in the NotificationVisibility.NotificationLocation enum map
                    // directly to the fields in the MetricsEvent.NotificationLocation enum.
                    .addTaggedData(MetricsEvent.NOTIFICATION_LOCATION, notificationLocation)
                    .addTaggedData(
                            MetricsEvent.NOTIFICATION_SMART_REPLY_EDIT_BEFORE_SENDING,
                            r.getEditChoicesBeforeSending() ? 1 : 0);
            mMetricsLogger.write(logMaker);
        }
    }

    @GuardedBy("mNotificationLock")
    private void clearSoundLocked() {
        mSoundNotificationKey = null;
        long identity = Binder.clearCallingIdentity();
        try {
            final IRingtonePlayer player = mAudioManager.getRingtonePlayer();
            if (player != null) {
                player.stopAsync();
            }
        } catch (RemoteException e) {
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @GuardedBy("mNotificationLock")
    private void clearVibrateLocked() {
        mVibrateNotificationKey = null;
        long identity = Binder.clearCallingIdentity();
        try {
            mVibrator.cancel();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @GuardedBy("mNotificationLock")
    private void clearLightsLocked() {
        // light
        // clear only if lockscreen is not active
        if (!mLineageNotificationLights.isKeyguardLocked()) {
            mLights.clear();
            updateLightsLocked();
        }
    }

    protected final BroadcastReceiver mLocaleChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
                // update system notification channels
                SystemNotificationChannels.createAll(context);
                mZenModeHelper.updateDefaultZenRules();
                mPreferencesHelper.onLocaleChanged(context, ActivityManager.getCurrentUser());
            }
        }
    };

    private final BroadcastReceiver mRestoreReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SETTING_RESTORED.equals(intent.getAction())) {
                try {
                    String element = intent.getStringExtra(Intent.EXTRA_SETTING_NAME);
                    String newValue = intent.getStringExtra(Intent.EXTRA_SETTING_NEW_VALUE);
                    int restoredFromSdkInt = intent.getIntExtra(
                            Intent.EXTRA_SETTING_RESTORED_FROM_SDK_INT, 0);
                    mListeners.onSettingRestored(
                            element, newValue, restoredFromSdkInt, getSendingUserId());
                    mConditionProviders.onSettingRestored(
                            element, newValue, restoredFromSdkInt, getSendingUserId());
                } catch (Exception e) {
                    Slog.wtf(TAG, "Cannot restore managed services from settings", e);
                }
            }
        }
    };

    private final BroadcastReceiver mNotificationTimeoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            if (ACTION_NOTIFICATION_TIMEOUT.equals(action)) {
                final NotificationRecord record;
                synchronized (mNotificationLock) {
                    record = findNotificationByKeyLocked(intent.getStringExtra(EXTRA_KEY));
                }
                if (record != null) {
                    cancelNotification(record.sbn.getUid(), record.sbn.getInitialPid(),
                            record.sbn.getPackageName(), record.sbn.getTag(),
                            record.sbn.getId(), 0,
                            FLAG_FOREGROUND_SERVICE, true, record.getUserId(),
                            REASON_TIMEOUT, null);
                }
            }
        }
    };

    private final BroadcastReceiver mPackageIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            boolean queryRestart = false;
            boolean queryRemove = false;
            boolean packageChanged = false;
            boolean cancelNotifications = true;
            boolean hideNotifications = false;
            boolean unhideNotifications = false;
            int reason = REASON_PACKAGE_CHANGED;

            if (action.equals(Intent.ACTION_PACKAGE_ADDED)
                    || (queryRemove=action.equals(Intent.ACTION_PACKAGE_REMOVED))
                    || action.equals(Intent.ACTION_PACKAGE_RESTARTED)
                    || (packageChanged=action.equals(Intent.ACTION_PACKAGE_CHANGED))
                    || (queryRestart=action.equals(Intent.ACTION_QUERY_PACKAGE_RESTART))
                    || action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
                    || action.equals(Intent.ACTION_PACKAGES_SUSPENDED)
                    || action.equals(Intent.ACTION_PACKAGES_UNSUSPENDED)
                    || action.equals(Intent.ACTION_DISTRACTING_PACKAGES_CHANGED)) {
                int changeUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,
                        UserHandle.USER_ALL);
                String pkgList[] = null;
                int uidList[] = null;
                boolean removingPackage = queryRemove &&
                        !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (DBG) Slog.i(TAG, "action=" + action + " removing=" + removingPackage);
                if (action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                    uidList = intent.getIntArrayExtra(Intent.EXTRA_CHANGED_UID_LIST);
                } else if (action.equals(Intent.ACTION_PACKAGES_SUSPENDED)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                    uidList = intent.getIntArrayExtra(Intent.EXTRA_CHANGED_UID_LIST);
                    cancelNotifications = false;
                    hideNotifications = true;
                } else if (action.equals(Intent.ACTION_PACKAGES_UNSUSPENDED)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                    uidList = intent.getIntArrayExtra(Intent.EXTRA_CHANGED_UID_LIST);
                    cancelNotifications = false;
                    unhideNotifications = true;
                } else if (action.equals(Intent.ACTION_DISTRACTING_PACKAGES_CHANGED)) {
                    final int distractionRestrictions =
                            intent.getIntExtra(Intent.EXTRA_DISTRACTION_RESTRICTIONS,
                                    PackageManager.RESTRICTION_NONE);
                    if ((distractionRestrictions
                            & PackageManager.RESTRICTION_HIDE_NOTIFICATIONS) != 0) {
                        pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                        uidList = intent.getIntArrayExtra(Intent.EXTRA_CHANGED_UID_LIST);
                        cancelNotifications = false;
                        hideNotifications = true;
                    } else {
                        pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                        uidList = intent.getIntArrayExtra(Intent.EXTRA_CHANGED_UID_LIST);
                        cancelNotifications = false;
                        unhideNotifications = true;
                    }

                } else if (queryRestart) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                    uidList = new int[] {intent.getIntExtra(Intent.EXTRA_UID, -1)};
                } else {
                    Uri uri = intent.getData();
                    if (uri == null) {
                        return;
                    }
                    String pkgName = uri.getSchemeSpecificPart();
                    if (pkgName == null) {
                        return;
                    }
                    if (packageChanged) {
                        // We cancel notifications for packages which have just been disabled
                        try {
                            final int enabled = mPackageManager.getApplicationEnabledSetting(
                                    pkgName,
                                    changeUserId != UserHandle.USER_ALL ? changeUserId :
                                            USER_SYSTEM);
                            if (enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                    || enabled == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                                cancelNotifications = false;
                            }
                        } catch (IllegalArgumentException e) {
                            // Package doesn't exist; probably racing with uninstall.
                            // cancelNotifications is already true, so nothing to do here.
                            if (DBG) {
                                Slog.i(TAG, "Exception trying to look up app enabled setting", e);
                            }
                        } catch (RemoteException e) {
                            // Failed to talk to PackageManagerService Should never happen!
                        }
                    }
                    pkgList = new String[]{pkgName};
                    uidList = new int[] {intent.getIntExtra(Intent.EXTRA_UID, -1)};
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    if (cancelNotifications) {
                        for (String pkgName : pkgList) {
                            cancelAllNotificationsInt(MY_UID, MY_PID, pkgName, null, 0, 0,
                                    !queryRestart, changeUserId, reason, null);
                        }
                    } else if (hideNotifications) {
                        hideNotificationsForPackages(pkgList);
                    } else if (unhideNotifications) {
                        unhideNotificationsForPackages(pkgList);
                    }
                }

                mHandler.scheduleOnPackageChanged(removingPackage, changeUserId, pkgList, uidList);
            }
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                // Keep track of screen on/off state, but do not turn off the notification light
                // until user passes through the lock screen or views the notification.
                mScreenOn = true;
                updateNotificationPulse();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                updateNotificationPulse();
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                mInCallStateOffHook = TelephonyManager.EXTRA_STATE_OFFHOOK
                        .equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE));
                updateNotificationPulse();
            } else if (action.equals(Intent.ACTION_USER_STOPPED)) {
                int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                if (userHandle >= 0) {
                    cancelAllNotificationsInt(MY_UID, MY_PID, null, null, 0, 0, true, userHandle,
                            REASON_USER_STOPPED, null);
                }
            } else if (action.equals(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)) {
                int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                if (userHandle >= 0) {
                    cancelAllNotificationsInt(MY_UID, MY_PID, null, null, 0, 0, true, userHandle,
                            REASON_PROFILE_TURNED_OFF, null);
                }
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                // turn off LED when user passes through lock screen
                // if lights with screen on is disabled.
                if (!mLineageNotificationLights.showLightsScreenOn()) {
                    mNotificationLight.turnOff();
                }
            } else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, USER_NULL);
                mUserProfiles.updateCache(context);
                if (!mUserProfiles.isManagedProfile(userId)) {
                    // reload per-user settings
                    mSettingsObserver.update(null);
                    // Refresh managed services
                    mConditionProviders.onUserSwitched(userId);
                    mListeners.onUserSwitched(userId);
                    mZenModeHelper.onUserSwitched(userId);
                    mPreferencesHelper.onUserSwitched(userId);
                }
                // assistant is the only thing that cares about managed profiles specifically
                mAssistants.onUserSwitched(userId);
            } else if (action.equals(Intent.ACTION_USER_ADDED)) {
                final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, USER_NULL);
                if (userId != USER_NULL) {
                    mUserProfiles.updateCache(context);
                    if (!mUserProfiles.isManagedProfile(userId)) {
                        readDefaultApprovedServices(userId);
                    }
                }
            } else if (action.equals(Intent.ACTION_USER_REMOVED)) {
                final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, USER_NULL);
                mUserProfiles.updateCache(context);
                mZenModeHelper.onUserRemoved(userId);
                mPreferencesHelper.onUserRemoved(userId);
                mListeners.onUserRemoved(userId);
                mConditionProviders.onUserRemoved(userId);
                mAssistants.onUserRemoved(userId);
                handleSavePolicyFile();
            } else if (action.equals(Intent.ACTION_USER_UNLOCKED)) {
                final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, USER_NULL);
                mUserProfiles.updateCache(context);
                mAssistants.onUserUnlocked(userId);
                if (!mUserProfiles.isManagedProfile(userId)) {
                    mConditionProviders.onUserUnlocked(userId);
                    mListeners.onUserUnlocked(userId);
                    mZenModeHelper.onUserUnlocked(userId);
                    mPreferencesHelper.onUserUnlocked(userId);
                }
            }
        }
    };

    private final class SettingsObserver extends ContentObserver {
        private final Uri NOTIFICATION_BADGING_URI
                = Settings.Secure.getUriFor(Settings.Secure.NOTIFICATION_BADGING);
        private final Uri NOTIFICATION_BUBBLES_URI_GLOBAL
                = Settings.Global.getUriFor(Settings.Global.NOTIFICATION_BUBBLES);
        private final Uri NOTIFICATION_BUBBLES_URI_SECURE
                = Settings.Secure.getUriFor(Settings.Secure.NOTIFICATION_BUBBLES);
        private final Uri NOTIFICATION_LIGHT_PULSE_URI
                = Settings.System.getUriFor(Settings.System.NOTIFICATION_LIGHT_PULSE);
        private final Uri NOTIFICATION_RATE_LIMIT_URI
                = Settings.Global.getUriFor(Settings.Global.MAX_NOTIFICATION_ENQUEUE_RATE);
        private final Uri NOTIFICATION_SOUND_VIB_SCREEN_ON
                = Settings.System.getUriFor(Settings.System.NOTIFICATION_SOUND_VIB_SCREEN_ON);
        private final Uri VIBRATE_ON_NOTIFICATIONS
                = Settings.System.getUriFor(Settings.System.VIBRATE_ON_NOTIFICATIONS);
        private final Uri GAMING_MODE_ACTIVE
                = Settings.System.getUriFor(Settings.System.GAMING_MODE_ACTIVE);
        private final Uri GAMING_MODE_NOTIFICATIONS_FEEDBACK
                = Settings.System.getUriFor(Settings.System.GAMING_MODE_NOTIFICATIONS_FEEDBACK);

        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(NOTIFICATION_BADGING_URI,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(NOTIFICATION_LIGHT_PULSE_URI,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(NOTIFICATION_RATE_LIMIT_URI,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(NOTIFICATION_BUBBLES_URI_GLOBAL,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(NOTIFICATION_BUBBLES_URI_SECURE,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(NOTIFICATION_SOUND_VIB_SCREEN_ON,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(VIBRATE_ON_NOTIFICATIONS,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(GAMING_MODE_ACTIVE,
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(GAMING_MODE_NOTIFICATIONS_FEEDBACK,
                    false, this, UserHandle.USER_ALL);
            update(null);
        }

        @Override public void onChange(boolean selfChange, Uri uri) {
            update(uri);
        }

        public void update(Uri uri) {
            ContentResolver resolver = getContext().getContentResolver();
            if (uri == null || NOTIFICATION_LIGHT_PULSE_URI.equals(uri)) {
                boolean pulseEnabled = Settings.System.getIntForUser(resolver,
                            Settings.System.NOTIFICATION_LIGHT_PULSE, 0, UserHandle.USER_CURRENT)
                        != 0;
                if (mNotificationPulseEnabled != pulseEnabled) {
                    mNotificationPulseEnabled = pulseEnabled;
                    updateNotificationPulse();
                }
            }
            if (uri == null || NOTIFICATION_RATE_LIMIT_URI.equals(uri)) {
                mMaxPackageEnqueueRate = Settings.Global.getFloat(resolver,
                            Settings.Global.MAX_NOTIFICATION_ENQUEUE_RATE, mMaxPackageEnqueueRate);
            }
            if (uri == null || NOTIFICATION_BADGING_URI.equals(uri)) {
                mPreferencesHelper.updateBadgingEnabled();
            }
            // In QPR we moved the setting to Global rather than Secure so that the setting
            // applied to work profiles. Unfortunately we need to maintain both to pass CTS without
            // a change to CTS outside of a normal letter release.
            if (uri == null || NOTIFICATION_BUBBLES_URI_GLOBAL.equals(uri)) {
                syncBubbleSettings(resolver, NOTIFICATION_BUBBLES_URI_GLOBAL);
                mPreferencesHelper.updateBubblesEnabled();
            }
            if (NOTIFICATION_BUBBLES_URI_SECURE.equals(uri)) {
                syncBubbleSettings(resolver, NOTIFICATION_BUBBLES_URI_SECURE);
            }
            if (uri == null || NOTIFICATION_SOUND_VIB_SCREEN_ON.equals(uri)) {
                mSoundVibScreenOn = Settings.System.getIntForUser(resolver,
                        Settings.System.NOTIFICATION_SOUND_VIB_SCREEN_ON, 1,
                        UserHandle.USER_CURRENT) == 1;
            }
            if (uri == null || VIBRATE_ON_NOTIFICATIONS.equals(uri)) {
                mVibrateOnNotifications = Settings.System.getIntForUser(resolver,
                        Settings.System.VIBRATE_ON_NOTIFICATIONS, 1,
                        UserHandle.USER_CURRENT) == 1;
            }
            if (uri == null || GAMING_MODE_ACTIVE.equals(uri)) {
                mGamingMode = Settings.System.getIntForUser(resolver,
                        Settings.System.GAMING_MODE_ACTIVE, 0,
                        UserHandle.USER_CURRENT) == 1;
            }
            if (uri == null || GAMING_MODE_NOTIFICATIONS_FEEDBACK.equals(uri)) {
                mSoundVibGamingMode = Settings.System.getIntForUser(resolver,
                        Settings.System.GAMING_MODE_NOTIFICATIONS_FEEDBACK, 1,
                        UserHandle.USER_CURRENT) == 1;
            }
        }

        private void syncBubbleSettings(ContentResolver resolver, Uri settingToFollow) {
            boolean followSecureSetting = settingToFollow.equals(NOTIFICATION_BUBBLES_URI_SECURE);

            int secureSettingValue = Settings.Secure.getInt(resolver,
                    Settings.Secure.NOTIFICATION_BUBBLES, DEFAULT_ALLOW_BUBBLE ? 1 : 0);
            int globalSettingValue = Settings.Global.getInt(resolver,
                    Settings.Global.NOTIFICATION_BUBBLES, DEFAULT_ALLOW_BUBBLE ? 1 : 0);

            if (globalSettingValue == secureSettingValue) {
                return;
            }

            if (followSecureSetting) {
                // Global => secure
                Settings.Global.putInt(resolver,
                        Settings.Global.NOTIFICATION_BUBBLES,
                        secureSettingValue);
            } else {
                // Secure => Global
                Settings.Secure.putInt(resolver,
                        Settings.Secure.NOTIFICATION_BUBBLES,
                        globalSettingValue);
            }
        }
    }

    private SettingsObserver mSettingsObserver;
    protected ZenModeHelper mZenModeHelper;

    static long[] getLongArray(Resources r, int resid, int maxlen, long[] def) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return def;
        }
        final int len = ar.length > maxlen ? maxlen : ar.length;
        long[] out = new long[len];
        for (int i=0; i<len; i++) {
            out[i] = ar[i];
        }
        return out;
    }

    public NotificationManagerService(Context context) {
        super(context);
        Notification.processWhitelistToken = WHITELIST_TOKEN;
    }

    // TODO - replace these methods with a single VisibleForTesting constructor
    @VisibleForTesting
    void setAudioManager(AudioManager audioMananger) {
        mAudioManager = audioMananger;
    }

    @VisibleForTesting
    void setHints(int hints) {
        mListenerHints = hints;
    }

    @VisibleForTesting
    void setVibrator(Vibrator vibrator) {
        mVibrator = vibrator;
    }

    @VisibleForTesting
    void setLights(Light light) {
        mNotificationLight = light;
        mAttentionLight = light;
        mNotificationPulseEnabled = true;
    }

    @VisibleForTesting
    void setScreenOn(boolean on) {
        mScreenOn = on;
    }

    @VisibleForTesting
    int getNotificationRecordCount() {
        synchronized (mNotificationLock) {
            int count = mNotificationList.size() + mNotificationsByKey.size()
                    + mSummaryByGroupKey.size() + mEnqueuedNotifications.size();
            // subtract duplicates
            for (NotificationRecord posted : mNotificationList) {
                if (mNotificationsByKey.containsKey(posted.getKey())) {
                    count--;
                }
                if (posted.sbn.isGroup() && posted.getNotification().isGroupSummary()) {
                    count--;
                }
            }

            return count;
        }
    }

    @VisibleForTesting
    void clearNotifications() {
        mEnqueuedNotifications.clear();
        mNotificationList.clear();
        mNotificationsByKey.clear();
        mSummaryByGroupKey.clear();
    }

    @VisibleForTesting
    void addNotification(NotificationRecord r) {
        mNotificationList.add(r);
        mNotificationsByKey.put(r.sbn.getKey(), r);
        if (r.sbn.isGroup()) {
            mSummaryByGroupKey.put(r.getGroupKey(), r);
        }
    }

    @VisibleForTesting
    void addEnqueuedNotification(NotificationRecord r) {
        mEnqueuedNotifications.add(r);
    }

    @VisibleForTesting
    NotificationRecord getNotificationRecord(String key) {
        return mNotificationsByKey.get(key);
    }


    @VisibleForTesting
    void setSystemReady(boolean systemReady) {
        mSystemReady = systemReady;
    }

    @VisibleForTesting
    void setHandler(WorkerHandler handler) {
        mHandler = handler;
    }

    @VisibleForTesting
    void setFallbackVibrationPattern(long[] vibrationPattern) {
        mFallbackVibrationPattern = vibrationPattern;
    }

    @VisibleForTesting
    void setPackageManager(IPackageManager packageManager) {
        mPackageManager = packageManager;
    }

    @VisibleForTesting
    void setRankingHelper(RankingHelper rankingHelper) {
        mRankingHelper = rankingHelper;
    }

    @VisibleForTesting
    void setPreferencesHelper(PreferencesHelper prefHelper) { mPreferencesHelper = prefHelper; }

    @VisibleForTesting
    void setRankingHandler(RankingHandler rankingHandler) {
        mRankingHandler = rankingHandler;
    }

    @VisibleForTesting
    void setZenHelper(ZenModeHelper zenHelper) {
        mZenModeHelper = zenHelper;
    }

    @VisibleForTesting
    void setIsAutomotive(boolean isAutomotive) {
        mIsAutomotive = isAutomotive;
    }

    @VisibleForTesting
    void setNotificationEffectsEnabledForAutomotive(boolean isEnabled) {
        mNotificationEffectsEnabledForAutomotive = isEnabled;
    }

    @VisibleForTesting
    void setIsTelevision(boolean isTelevision) {
        mIsTelevision = isTelevision;
    }

    @VisibleForTesting
    void setUsageStats(NotificationUsageStats us) {
        mUsageStats = us;
    }

    @VisibleForTesting
    void setAccessibilityManager(AccessibilityManager am) {
        mAccessibilityManager = am;
    }

    // TODO: All tests should use this init instead of the one-off setters above.
    @VisibleForTesting
    void init(Looper looper, IPackageManager packageManager,
            PackageManager packageManagerClient,
            LightsManager lightsManager, NotificationListeners notificationListeners,
            NotificationAssistants notificationAssistants, ConditionProviders conditionProviders,
            ICompanionDeviceManager companionManager, SnoozeHelper snoozeHelper,
            NotificationUsageStats usageStats, AtomicFile policyFile,
            ActivityManager activityManager, GroupHelper groupHelper, IActivityManager am,
            UsageStatsManagerInternal appUsageStats, DevicePolicyManagerInternal dpm,
            IUriGrantsManager ugm, UriGrantsManagerInternal ugmInternal, AppOpsManager appOps,
            UserManager userManager, ActivityManagerInternal ami) {
        Resources resources = getContext().getResources();
        mMaxPackageEnqueueRate = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.MAX_NOTIFICATION_ENQUEUE_RATE,
                DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE);

        mAccessibilityManager =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAm = am;
        mUgm = ugm;
        mUgmInternal = ugmInternal;
        mPackageManager = packageManager;
        mPackageManagerClient = packageManagerClient;
        mAppOps = appOps;
        mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        mAppUsageStats = appUsageStats;
        mAlarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        mCompanionManager = companionManager;
        mActivityManager = activityManager;
        mAmi = ami;
        mDeviceIdleController = IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
        mDpm = dpm;
        mUm = userManager;

        mHandler = new WorkerHandler(looper);
        mRankingThread.start();
        String[] extractorNames;
        try {
            extractorNames = resources.getStringArray(R.array.config_notificationSignalExtractors);
        } catch (Resources.NotFoundException e) {
            extractorNames = new String[0];
        }
        mUsageStats = usageStats;
        mMetricsLogger = new MetricsLogger();
        mRankingHandler = new RankingHandlerWorker(mRankingThread.getLooper());
        mConditionProviders = conditionProviders;
        mZenModeHelper = new ZenModeHelper(getContext(), mHandler.getLooper(), mConditionProviders);
        mZenModeHelper.addCallback(new ZenModeHelper.Callback() {
            @Override
            public void onConfigChanged() {
                handleSavePolicyFile();
            }

            @Override
            void onZenModeChanged() {
                sendRegisteredOnlyBroadcast(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
                getContext().sendBroadcastAsUser(
                        new Intent(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED_INTERNAL)
                                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT),
                        UserHandle.ALL, android.Manifest.permission.MANAGE_NOTIFICATIONS);
                mLineageNotificationLights.setZenMode(mZenModeHelper.getZenMode());
                synchronized (mNotificationLock) {
                    updateInterruptionFilterLocked();
                }
                mRankingHandler.requestSort();
            }

            @Override
            void onPolicyChanged() {
                sendRegisteredOnlyBroadcast(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED);
                mRankingHandler.requestSort();
            }
        });
        mPreferencesHelper = new PreferencesHelper(getContext(),
                mPackageManagerClient,
                mRankingHandler,
                mZenModeHelper);
        mRankingHelper = new RankingHelper(getContext(),
                mRankingHandler,
                mPreferencesHelper,
                mZenModeHelper,
                mUsageStats,
                extractorNames);
        mSnoozeHelper = snoozeHelper;
        mGroupHelper = groupHelper;

        // This is a ManagedServices object that keeps track of the listeners.
        mListeners = notificationListeners;

        // This is a MangedServices object that keeps track of the assistant.
        mAssistants = notificationAssistants;

        // Needs to be set before loadPolicyFile
        mAllowedManagedServicePackages = this::canUseManagedServices;

        mPolicyFile = policyFile;
        loadPolicyFile();

        mStatusBar = getLocalService(StatusBarManagerInternal.class);
        if (mStatusBar != null) {
            mStatusBar.setNotificationDelegate(mNotificationDelegate);
        }

        mNotificationLight = lightsManager.getLight(LightsManager.LIGHT_ID_NOTIFICATIONS);
        mAttentionLight = lightsManager.getLight(LightsManager.LIGHT_ID_ATTENTION);

        mFallbackVibrationPattern = getLongArray(resources,
                R.array.config_notificationFallbackVibePattern,
                VIBRATE_PATTERN_MAXLEN,
                DEFAULT_VIBRATE_PATTERN);
        mInCallNotificationUri = Uri.parse("file://" +
                resources.getString(R.string.config_inCallNotificationSound));
        mInCallNotificationAudioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build();
        mInCallNotificationVolume = resources.getFloat(R.dimen.config_inCallNotificationVolume);

        mUseAttentionLight = resources.getBoolean(R.bool.config_useAttentionLight);
        mHasLight =
                resources.getBoolean(com.android.internal.R.bool.config_intrusiveNotificationLed);

        // Don't start allowing notifications until the setup wizard has run once.
        // After that, including subsequent boots, init with notifications turned on.
        // This works on the first boot because the setup wizard will toggle this
        // flag at least once and we'll go back to 0 after that.
        if (0 == Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.DEVICE_PROVISIONED, 0)) {
            mDisableNotificationEffects = true;
        }
        mZenModeHelper.initZenMode();
        mInterruptionFilter = mZenModeHelper.getZenModeListenerInterruptionFilter();

        mUserProfiles.updateCache(getContext());
        listenForCallState();

        mSettingsObserver = new SettingsObserver(mHandler);

        mArchive = new Archive(resources.getInteger(
                R.integer.config_notificationServiceArchiveSize));

        mIsTelevision = mPackageManagerClient.hasSystemFeature(FEATURE_LEANBACK)
                || mPackageManagerClient.hasSystemFeature(FEATURE_TELEVISION);

        mIsAutomotive =
                mPackageManagerClient.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, 0);
        mNotificationEffectsEnabledForAutomotive =
                resources.getBoolean(R.bool.config_enableServerNotificationEffectsForAutomotive);

        mPreferencesHelper.lockChannelsForOEM(getContext().getResources().getStringArray(
                com.android.internal.R.array.config_nonBlockableNotificationPackages));

        mZenModeHelper.setPriorityOnlyDndExemptPackages(getContext().getResources().getStringArray(
                com.android.internal.R.array.config_priorityOnlyDndExemptPackages));
    }

    @Override
    public void onStart() {
        SnoozeHelper snoozeHelper = new SnoozeHelper(getContext(), new SnoozeHelper.Callback() {
            @Override
            public void repost(int userId, NotificationRecord r) {
                try {
                    if (DBG) {
                        Slog.d(TAG, "Reposting " + r.getKey());
                    }
                    enqueueNotificationInternal(r.sbn.getPackageName(), r.sbn.getOpPkg(),
                            r.sbn.getUid(), r.sbn.getInitialPid(), r.sbn.getTag(), r.sbn.getId(),
                            r.sbn.getNotification(), userId);
                } catch (Exception e) {
                    Slog.e(TAG, "Cannot un-snooze notification", e);
                }
            }
        }, mUserProfiles);

        final File systemDir = new File(Environment.getDataDirectory(), "system");

        init(Looper.myLooper(),
                AppGlobals.getPackageManager(), getContext().getPackageManager(),
                getLocalService(LightsManager.class),
                new NotificationListeners(AppGlobals.getPackageManager()),
                new NotificationAssistants(getContext(), mNotificationLock, mUserProfiles,
                        AppGlobals.getPackageManager()),
                new ConditionProviders(getContext(), mUserProfiles, AppGlobals.getPackageManager()),
                null, snoozeHelper, new NotificationUsageStats(getContext()),
                new AtomicFile(new File(
                        systemDir, "notification_policy.xml"), "notification-policy"),
                (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE),
                getGroupHelper(), ActivityManager.getService(),
                LocalServices.getService(UsageStatsManagerInternal.class),
                LocalServices.getService(DevicePolicyManagerInternal.class),
                UriGrantsManager.getService(),
                LocalServices.getService(UriGrantsManagerInternal.class),
                (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE),
                getContext().getSystemService(UserManager.class),
                LocalServices.getService(ActivityManagerInternal.class));

        // register for various Intents
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        getContext().registerReceiverAsUser(mIntentReceiver, UserHandle.ALL, filter, null, null);

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        pkgFilter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
        pkgFilter.addDataScheme("package");
        getContext().registerReceiverAsUser(mPackageIntentReceiver, UserHandle.ALL, pkgFilter, null,
                null);

        IntentFilter suspendedPkgFilter = new IntentFilter();
        suspendedPkgFilter.addAction(Intent.ACTION_PACKAGES_SUSPENDED);
        suspendedPkgFilter.addAction(Intent.ACTION_PACKAGES_UNSUSPENDED);
        suspendedPkgFilter.addAction(Intent.ACTION_DISTRACTING_PACKAGES_CHANGED);
        getContext().registerReceiverAsUser(mPackageIntentReceiver, UserHandle.ALL,
                suspendedPkgFilter, null, null);

        IntentFilter sdFilter = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        getContext().registerReceiverAsUser(mPackageIntentReceiver, UserHandle.ALL, sdFilter, null,
                null);

        IntentFilter timeoutFilter = new IntentFilter(ACTION_NOTIFICATION_TIMEOUT);
        timeoutFilter.addDataScheme(SCHEME_TIMEOUT);
        getContext().registerReceiver(mNotificationTimeoutReceiver, timeoutFilter);

        IntentFilter settingsRestoredFilter = new IntentFilter(Intent.ACTION_SETTING_RESTORED);
        getContext().registerReceiver(mRestoreReceiver, settingsRestoredFilter);

        IntentFilter localeChangedFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        getContext().registerReceiver(mLocaleChangeReceiver, localeChangedFilter);

        mLineageNotificationLights = new LineageNotificationLights(getContext(),
                 new LineageNotificationLights.LedUpdater() {
            public void update() {
                updateNotificationPulse();
            }
        });

        publishBinderService(Context.NOTIFICATION_SERVICE, mService, /* allowIsolated= */ false,
                DUMP_FLAG_PRIORITY_CRITICAL | DUMP_FLAG_PRIORITY_NORMAL);
        publishBinderService(Context.NOTIFICATION_SERVICE, mService);
        publishLocalService(NotificationManagerInternal.class, mInternalService);
    }

    private void registerDeviceConfigChange() {
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                getContext().getMainExecutor(),
                (properties) -> {
                    if (!DeviceConfig.NAMESPACE_SYSTEMUI.equals(properties.getNamespace())) {
                        return;
                    }
                    if (properties.getKeyset()
                            .contains(SystemUiDeviceConfigFlags.NAS_DEFAULT_SERVICE)) {
                        mAssistants.allowAdjustmentType(Adjustment.KEY_IMPORTANCE);
                        mAssistants.resetDefaultAssistantsIfNecessary();
                    }
                });
    }


    private GroupHelper getGroupHelper() {
        mAutoGroupAtCount =
                getContext().getResources().getInteger(R.integer.config_autoGroupAtCount);
        return new GroupHelper(mAutoGroupAtCount, new GroupHelper.Callback() {
            @Override
            public void addAutoGroup(String key) {
                synchronized (mNotificationLock) {
                    addAutogroupKeyLocked(key);
                }
            }

            @Override
            public void removeAutoGroup(String key) {
                synchronized (mNotificationLock) {
                    removeAutogroupKeyLocked(key);
                }
            }

            @Override
            public void addAutoGroupSummary(int userId, String pkg, String triggeringKey) {
                createAutoGroupSummary(userId, pkg, triggeringKey);
            }

            @Override
            public void removeAutoGroupSummary(int userId, String pkg) {
                synchronized (mNotificationLock) {
                    clearAutogroupSummaryLocked(userId, pkg);
                }
            }
        });
    }

    private void sendRegisteredOnlyBroadcast(String action) {
        Intent intent = new Intent(action);
        getContext().sendBroadcastAsUser(intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
                UserHandle.ALL, null);
        // explicitly send the broadcast to all DND packages, even if they aren't currently running
        intent.setFlags(0);
        final Set<String> dndApprovedPackages = mConditionProviders.getAllowedPackages();
        for (String pkg : dndApprovedPackages) {
            intent.setPackage(pkg);
            getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            // no beeping until we're basically done booting
            mSystemReady = true;

            // Grab our optional AudioService
            mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            mAudioManagerInternal = getLocalService(AudioManagerInternal.class);
            mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
            mZenModeHelper.onSystemReady();
            mRoleObserver = new RoleObserver(getContext().getSystemService(RoleManager.class),
                    mPackageManager, getContext().getMainExecutor());
            mRoleObserver.init();
        } else if (phase == SystemService.PHASE_THIRD_PARTY_APPS_CAN_START) {
            // This observer will force an update when observe is called, causing us to
            // bind to listener services.
            mSettingsObserver.observe();
            mListeners.onBootPhaseAppsCanStart();
            mAssistants.onBootPhaseAppsCanStart();
            mConditionProviders.onBootPhaseAppsCanStart();
            registerDeviceConfigChange();
        }
    }

    @GuardedBy("mNotificationLock")
    private void updateListenerHintsLocked() {
        final int hints = calculateHints();
        if (hints == mListenerHints) return;
        ZenLog.traceListenerHintsChanged(mListenerHints, hints, mEffectsSuppressors.size());
        mListenerHints = hints;
        scheduleListenerHintsChanged(hints);
    }

    @GuardedBy("mNotificationLock")
    private void updateEffectsSuppressorLocked() {
        final long updatedSuppressedEffects = calculateSuppressedEffects();
        if (updatedSuppressedEffects == mZenModeHelper.getSuppressedEffects()) return;
        final List<ComponentName> suppressors = getSuppressors();
        ZenLog.traceEffectsSuppressorChanged(mEffectsSuppressors, suppressors, updatedSuppressedEffects);
        mEffectsSuppressors = suppressors;
        mZenModeHelper.setSuppressedEffects(updatedSuppressedEffects);
        sendRegisteredOnlyBroadcast(NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED);
    }

    private void exitIdle() {
        try {
            if (mDeviceIdleController != null) {
                mDeviceIdleController.exitIdle("notification interaction");
            }
        } catch (RemoteException e) {
        }
    }

    private void updateNotificationChannelInt(String pkg, int uid, NotificationChannel channel,
            boolean fromListener) {
        if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            // cancel
            cancelAllNotificationsInt(MY_UID, MY_PID, pkg, channel.getId(), 0, 0, true,
                    UserHandle.getUserId(uid), REASON_CHANNEL_BANNED,
                    null);
            if (isUidSystemOrPhone(uid)) {
                IntArray profileIds = mUserProfiles.getCurrentProfileIds();
                int N = profileIds.size();
                for (int i = 0; i < N; i++) {
                    int profileId = profileIds.get(i);
                    cancelAllNotificationsInt(MY_UID, MY_PID, pkg, channel.getId(), 0, 0, true,
                            profileId, REASON_CHANNEL_BANNED,
                            null);
                }
            }
        }
        final NotificationChannel preUpdate =
                mPreferencesHelper.getNotificationChannel(pkg, uid, channel.getId(), true);

        mPreferencesHelper.updateNotificationChannel(pkg, uid, channel, true);
        maybeNotifyChannelOwner(pkg, uid, preUpdate, channel);

        if (!fromListener) {
            final NotificationChannel modifiedChannel =
                    mPreferencesHelper.getNotificationChannel(pkg, uid, channel.getId(), false);
            mListeners.notifyNotificationChannelChanged(
                    pkg, UserHandle.getUserHandleForUid(uid),
                    modifiedChannel, NOTIFICATION_CHANNEL_OR_GROUP_UPDATED);
        }

        handleSavePolicyFile();
    }

    private void maybeNotifyChannelOwner(String pkg, int uid, NotificationChannel preUpdate,
            NotificationChannel update) {
        try {
            if ((preUpdate.getImportance() == IMPORTANCE_NONE
                    && update.getImportance() != IMPORTANCE_NONE)
                    || (preUpdate.getImportance() != IMPORTANCE_NONE
                    && update.getImportance() == IMPORTANCE_NONE)) {
                getContext().sendBroadcastAsUser(
                        new Intent(ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                                .putExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID,
                                        update.getId())
                                .putExtra(NotificationManager.EXTRA_BLOCKED_STATE,
                                        update.getImportance() == IMPORTANCE_NONE)
                                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                .setPackage(pkg),
                        UserHandle.of(UserHandle.getUserId(uid)), null);
            }
        } catch (SecurityException e) {
            Slog.w(TAG, "Can't notify app about channel change", e);
        }
    }

    private void createNotificationChannelGroup(String pkg, int uid, NotificationChannelGroup group,
            boolean fromApp, boolean fromListener) {
        Preconditions.checkNotNull(group);
        Preconditions.checkNotNull(pkg);

        final NotificationChannelGroup preUpdate =
                mPreferencesHelper.getNotificationChannelGroup(group.getId(), pkg, uid);
        mPreferencesHelper.createNotificationChannelGroup(pkg, uid, group,
                fromApp);
        if (!fromApp) {
            maybeNotifyChannelGroupOwner(pkg, uid, preUpdate, group);
        }
        if (!fromListener) {
            mListeners.notifyNotificationChannelGroupChanged(pkg,
                    UserHandle.of(UserHandle.getCallingUserId()), group,
                    NOTIFICATION_CHANNEL_OR_GROUP_ADDED);
        }
    }

    private void maybeNotifyChannelGroupOwner(String pkg, int uid,
            NotificationChannelGroup preUpdate, NotificationChannelGroup update) {
        try {
            if (preUpdate.isBlocked() != update.isBlocked()) {
                getContext().sendBroadcastAsUser(
                        new Intent(ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED)
                                .putExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_GROUP_ID,
                                        update.getId())
                                .putExtra(NotificationManager.EXTRA_BLOCKED_STATE,
                                        update.isBlocked())
                                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                .setPackage(pkg),
                        UserHandle.of(UserHandle.getUserId(uid)), null);
            }
        } catch (SecurityException e) {
            Slog.w(TAG, "Can't notify app about group change", e);
        }
    }

    private ArrayList<ComponentName> getSuppressors() {
        ArrayList<ComponentName> names = new ArrayList<ComponentName>();
        for (int i = mListenersDisablingEffects.size() - 1; i >= 0; --i) {
            ArraySet<ComponentName> serviceInfoList = mListenersDisablingEffects.valueAt(i);

            for (ComponentName info : serviceInfoList) {
                names.add(info);
            }
        }

        return names;
    }

    private boolean removeDisabledHints(ManagedServiceInfo info) {
        return removeDisabledHints(info, 0);
    }

    private boolean removeDisabledHints(ManagedServiceInfo info, int hints) {
        boolean removed = false;

        for (int i = mListenersDisablingEffects.size() - 1; i >= 0; --i) {
            final int hint = mListenersDisablingEffects.keyAt(i);
            final ArraySet<ComponentName> listeners = mListenersDisablingEffects.valueAt(i);

            if (hints == 0 || (hint & hints) == hint) {
                removed |= listeners.remove(info.component);
            }
        }

        return removed;
    }

    private void addDisabledHints(ManagedServiceInfo info, int hints) {
        if ((hints & HINT_HOST_DISABLE_EFFECTS) != 0) {
            addDisabledHint(info, HINT_HOST_DISABLE_EFFECTS);
        }

        if ((hints & HINT_HOST_DISABLE_NOTIFICATION_EFFECTS) != 0) {
            addDisabledHint(info, HINT_HOST_DISABLE_NOTIFICATION_EFFECTS);
        }

        if ((hints & HINT_HOST_DISABLE_CALL_EFFECTS) != 0) {
            addDisabledHint(info, HINT_HOST_DISABLE_CALL_EFFECTS);
        }
    }

    private void addDisabledHint(ManagedServiceInfo info, int hint) {
        if (mListenersDisablingEffects.indexOfKey(hint) < 0) {
            mListenersDisablingEffects.put(hint, new ArraySet<>());
        }

        ArraySet<ComponentName> hintListeners = mListenersDisablingEffects.get(hint);
        hintListeners.add(info.component);
    }

    private int calculateHints() {
        int hints = 0;
        for (int i = mListenersDisablingEffects.size() - 1; i >= 0; --i) {
            int hint = mListenersDisablingEffects.keyAt(i);
            ArraySet<ComponentName> serviceInfoList = mListenersDisablingEffects.valueAt(i);

            if (!serviceInfoList.isEmpty()) {
                hints |= hint;
            }
        }

        return hints;
    }

    private long calculateSuppressedEffects() {
        int hints = calculateHints();
        long suppressedEffects = 0;

        if ((hints & HINT_HOST_DISABLE_EFFECTS) != 0) {
            suppressedEffects |= ZenModeHelper.SUPPRESSED_EFFECT_ALL;
        }

        if ((hints & HINT_HOST_DISABLE_NOTIFICATION_EFFECTS) != 0) {
            suppressedEffects |= ZenModeHelper.SUPPRESSED_EFFECT_NOTIFICATIONS;
        }

        if ((hints & HINT_HOST_DISABLE_CALL_EFFECTS) != 0) {
            suppressedEffects |= ZenModeHelper.SUPPRESSED_EFFECT_CALLS;
        }

        return suppressedEffects;
    }

    @GuardedBy("mNotificationLock")
    private void updateInterruptionFilterLocked() {
        int interruptionFilter = mZenModeHelper.getZenModeListenerInterruptionFilter();
        if (interruptionFilter == mInterruptionFilter) return;
        mInterruptionFilter = interruptionFilter;
        scheduleInterruptionFilterChanged(interruptionFilter);
    }

    @VisibleForTesting
    INotificationManager getBinderService() {
        return INotificationManager.Stub.asInterface(mService);
    }

    /**
     * Report to usage stats that the notification was seen.
     * @param r notification record
     */
    @GuardedBy("mNotificationLock")
    protected void reportSeen(NotificationRecord r) {
        if (!r.isProxied()) {
            mAppUsageStats.reportEvent(r.sbn.getPackageName(),
                    getRealUserId(r.sbn.getUserId()),
                    UsageEvents.Event.NOTIFICATION_SEEN);
        }
    }

    protected int calculateSuppressedVisualEffects(Policy incomingPolicy, Policy currPolicy,
            int targetSdkVersion) {
        if (incomingPolicy.suppressedVisualEffects == SUPPRESSED_EFFECTS_UNSET) {
            return incomingPolicy.suppressedVisualEffects;
        }
        final int[] effectsIntroducedInP = {
                SUPPRESSED_EFFECT_FULL_SCREEN_INTENT,
                SUPPRESSED_EFFECT_LIGHTS,
                SUPPRESSED_EFFECT_PEEK,
                SUPPRESSED_EFFECT_STATUS_BAR,
                SUPPRESSED_EFFECT_BADGE,
                SUPPRESSED_EFFECT_AMBIENT,
                SUPPRESSED_EFFECT_NOTIFICATION_LIST
        };

        int newSuppressedVisualEffects = incomingPolicy.suppressedVisualEffects;
        if (targetSdkVersion < Build.VERSION_CODES.P) {
            // unset higher order bits introduced in P, maintain the user's higher order bits
            for (int i = 0; i < effectsIntroducedInP.length ; i++) {
                newSuppressedVisualEffects &= ~effectsIntroducedInP[i];
                newSuppressedVisualEffects |=
                        (currPolicy.suppressedVisualEffects & effectsIntroducedInP[i]);
            }
            // set higher order bits according to lower order bits
            if ((newSuppressedVisualEffects & SUPPRESSED_EFFECT_SCREEN_OFF) != 0) {
                newSuppressedVisualEffects |= SUPPRESSED_EFFECT_LIGHTS;
                newSuppressedVisualEffects |= SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
            }
            if ((newSuppressedVisualEffects & SUPPRESSED_EFFECT_SCREEN_ON) != 0) {
                newSuppressedVisualEffects |= SUPPRESSED_EFFECT_PEEK;
            }
        } else {
            boolean hasNewEffects = (newSuppressedVisualEffects
                    - SUPPRESSED_EFFECT_SCREEN_ON - SUPPRESSED_EFFECT_SCREEN_OFF) > 0;
            // if any of the new effects introduced in P are set
            if (hasNewEffects) {
                // clear out the deprecated effects
                newSuppressedVisualEffects &= ~ (SUPPRESSED_EFFECT_SCREEN_ON
                        | SUPPRESSED_EFFECT_SCREEN_OFF);

                // set the deprecated effects according to the new more specific effects
                if ((newSuppressedVisualEffects & Policy.SUPPRESSED_EFFECT_PEEK) != 0) {
                    newSuppressedVisualEffects |= SUPPRESSED_EFFECT_SCREEN_ON;
                }
                if ((newSuppressedVisualEffects & Policy.SUPPRESSED_EFFECT_LIGHTS) != 0
                        && (newSuppressedVisualEffects
                        & Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT) != 0
                        && (newSuppressedVisualEffects
                        & Policy.SUPPRESSED_EFFECT_AMBIENT) != 0) {
                    newSuppressedVisualEffects |= SUPPRESSED_EFFECT_SCREEN_OFF;
                }
            } else {
                // set higher order bits according to lower order bits
                if ((newSuppressedVisualEffects & SUPPRESSED_EFFECT_SCREEN_OFF) != 0) {
                    newSuppressedVisualEffects |= SUPPRESSED_EFFECT_LIGHTS;
                    newSuppressedVisualEffects |= SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
                    newSuppressedVisualEffects |= SUPPRESSED_EFFECT_AMBIENT;
                }
                if ((newSuppressedVisualEffects & SUPPRESSED_EFFECT_SCREEN_ON) != 0) {
                    newSuppressedVisualEffects |= SUPPRESSED_EFFECT_PEEK;
                }
            }
        }

        return newSuppressedVisualEffects;
    }

    @GuardedBy("mNotificationLock")
    protected void maybeRecordInterruptionLocked(NotificationRecord r) {
        if (r.isInterruptive() && !r.hasRecordedInterruption()) {
            mAppUsageStats.reportInterruptiveNotification(r.sbn.getPackageName(),
                    r.getChannel().getId(),
                    getRealUserId(r.sbn.getUserId()));
            r.setRecordedInterruption(true);
        }
    }

    /**
     * Report to usage stats that the user interacted with the notification.
     * @param r notification record
     */
    protected void reportUserInteraction(NotificationRecord r) {
        mAppUsageStats.reportEvent(r.sbn.getPackageName(),
                getRealUserId(r.sbn.getUserId()),
                UsageEvents.Event.USER_INTERACTION);
    }

    private int getRealUserId(int userId) {
        return userId == UserHandle.USER_ALL ? UserHandle.USER_SYSTEM : userId;
    }

    @VisibleForTesting
    NotificationManagerInternal getInternalService() {
        return mInternalService;
    }

    @VisibleForTesting
    final IBinder mService = new INotificationManager.Stub() {
        // Toasts
        // ============================================================================

        @Override
        public void enqueueToast(String pkg, ITransientNotification callback, int duration,
                int displayId)
        {
            if (DBG) {
                Slog.i(TAG, "enqueueToast pkg=" + pkg + " callback=" + callback
                        + " duration=" + duration + " displayId=" + displayId);
            }

            if (pkg == null || callback == null) {
                Slog.e(TAG, "Not enqueuing toast. pkg=" + pkg + " callback=" + callback);
                return ;
            }

            final int callingUid = Binder.getCallingUid();
            final boolean isSystemToast = isCallerSystemOrPhone()
                    || PackageManagerService.PLATFORM_PACKAGE_NAME.equals(pkg);
            final boolean isPackageSuspended = isPackagePaused(pkg);
            final boolean notificationsDisabledForPackage = !areNotificationsEnabledForPackage(pkg,
                    callingUid);

            long callingIdentity = Binder.clearCallingIdentity();
            try {
                final boolean appIsForeground = mActivityManager.getUidImportance(callingUid)
                        == IMPORTANCE_FOREGROUND;
                if (ENABLE_BLOCKED_TOASTS && !isSystemToast && ((notificationsDisabledForPackage
                        && !appIsForeground) || isPackageSuspended)) {
                    Slog.e(TAG, "Suppressing toast from package " + pkg
                            + (isPackageSuspended ? " due to package suspended."
                            : " by user request."));
                    return;
                }
            } finally {
                Binder.restoreCallingIdentity(callingIdentity);
            }

            synchronized (mToastQueue) {
                int callingPid = Binder.getCallingPid();
                long callingId = Binder.clearCallingIdentity();
                try {
                    ToastRecord record;
                    int index = indexOfToastLocked(pkg, callback);
                    // If it's already in the queue, we update it in place, we don't
                    // move it to the end of the queue.
                    if (index >= 0) {
                        record = mToastQueue.get(index);
                        record.update(duration);
                    } else {
                        // Limit the number of toasts that any given package except the android
                        // package can enqueue.  Prevents DOS attacks and deals with leaks.
                        if (!isSystemToast) {
                            int count = 0;
                            final int N = mToastQueue.size();
                            for (int i=0; i<N; i++) {
                                 final ToastRecord r = mToastQueue.get(i);
                                 if (r.pkg.equals(pkg)) {
                                     count++;
                                     if (count >= MAX_PACKAGE_NOTIFICATIONS) {
                                         Slog.e(TAG, "Package has already posted " + count
                                                + " toasts. Not showing more. Package=" + pkg);
                                         return;
                                     }
                                 }
                            }
                        }

                        Binder token = new Binder();
                        mWindowManagerInternal.addWindowToken(token, TYPE_TOAST, displayId);
                        record = new ToastRecord(callingPid, pkg, callback, duration, token,
                                displayId);
                        mToastQueue.add(record);
                        index = mToastQueue.size() - 1;
                        keepProcessAliveIfNeededLocked(callingPid);
                    }
                    // If it's at index 0, it's the current toast.  It doesn't matter if it's
                    // new or just been updated.  Call back and tell it to show itself.
                    // If the callback fails, this will remove it from the list, so don't
                    // assume that it's valid after this.
                    if (index == 0) {
                        showNextToastLocked();
                    }
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }

        @Override
        public void cancelToast(String pkg, ITransientNotification callback) {
            Slog.i(TAG, "cancelToast pkg=" + pkg + " callback=" + callback);

            if (pkg == null || callback == null) {
                Slog.e(TAG, "Not cancelling notification. pkg=" + pkg + " callback=" + callback);
                return ;
            }

            synchronized (mToastQueue) {
                long callingId = Binder.clearCallingIdentity();
                try {
                    int index = indexOfToastLocked(pkg, callback);
                    if (index >= 0) {
                        cancelToastLocked(index);
                    } else {
                        Slog.w(TAG, "Toast already cancelled. pkg=" + pkg
                                + " callback=" + callback);
                    }
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }

        @Override
        public void finishToken(String pkg, ITransientNotification callback) {
            synchronized (mToastQueue) {
                long callingId = Binder.clearCallingIdentity();
                try {
                    int index = indexOfToastLocked(pkg, callback);
                    if (index >= 0) {
                        ToastRecord record = mToastQueue.get(index);
                        finishTokenLocked(record.token, record.displayId);
                    } else {
                        Slog.w(TAG, "Toast already killed. pkg=" + pkg
                                + " callback=" + callback);
                    }
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }

        @Override
        public void enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id,
                Notification notification, int userId) throws RemoteException {
            enqueueNotificationInternal(pkg, opPkg, Binder.getCallingUid(),
                    Binder.getCallingPid(), tag, id, notification, userId);
        }

        @Override
        public void cancelNotificationWithTag(String pkg, String tag, int id, int userId) {
            checkCallerIsSystemOrSameApp(pkg);
            userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                    Binder.getCallingUid(), userId, true, false, "cancelNotificationWithTag", pkg);
            // Don't allow client applications to cancel foreground service notis or autobundled
            // summaries.
            final int mustNotHaveFlags = isCallingUidSystem() ? 0 :
                    (FLAG_FOREGROUND_SERVICE | FLAG_AUTOGROUP_SUMMARY);
            cancelNotification(Binder.getCallingUid(), Binder.getCallingPid(), pkg, tag, id, 0,
                    mustNotHaveFlags, false, userId, REASON_APP_CANCEL, null);
        }

        @Override
        public void cancelAllNotifications(String pkg, int userId) {
            checkCallerIsSystemOrSameApp(pkg);

            userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                    Binder.getCallingUid(), userId, true, false, "cancelAllNotifications", pkg);

            // Calling from user space, don't allow the canceling of actively
            // running foreground services.
            cancelAllNotificationsInt(Binder.getCallingUid(), Binder.getCallingPid(),
                    pkg, null, 0, FLAG_FOREGROUND_SERVICE, true, userId,
                    REASON_APP_CANCEL_ALL, null);
        }

        @Override
        public void silenceNotificationSound() {
            checkCallerIsSystem();

            mNotificationDelegate.clearEffects();
        }

        @Override
        public void setNotificationsEnabledForPackage(String pkg, int uid, boolean enabled) {
            enforceSystemOrSystemUI("setNotificationsEnabledForPackage");

            synchronized (mNotificationLock) {
                boolean wasEnabled = mPreferencesHelper.getImportance(pkg, uid)
                        != NotificationManager.IMPORTANCE_NONE;

                if (wasEnabled == enabled) {
                    return;
                }
            }

            mPreferencesHelper.setEnabled(pkg, uid, enabled);
            mMetricsLogger.write(new LogMaker(MetricsEvent.ACTION_BAN_APP_NOTES)
                    .setType(MetricsEvent.TYPE_ACTION)
                    .setPackageName(pkg)
                    .setSubtype(enabled ? 1 : 0));
            // Now, cancel any outstanding notifications that are part of a just-disabled app
            if (!enabled) {
                cancelAllNotificationsInt(MY_UID, MY_PID, pkg, null, 0, 0, true,
                        UserHandle.getUserId(uid), REASON_PACKAGE_BANNED, null);
            }

            try {
                getContext().sendBroadcastAsUser(
                        new Intent(ACTION_APP_BLOCK_STATE_CHANGED)
                                .putExtra(NotificationManager.EXTRA_BLOCKED_STATE, !enabled)
                                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                .setPackage(pkg),
                        UserHandle.of(UserHandle.getUserId(uid)), null);
            } catch (SecurityException e) {
                Slog.w(TAG, "Can't notify app about app block change", e);
            }

            handleSavePolicyFile();
        }

        @Override
        public void setNotificationSoundTimeout(String pkg, int uid, long timeout) {
            checkCallerIsSystem();
            mPreferencesHelper.setNotificationSoundTimeout(pkg, uid, timeout);
            handleSavePolicyFile();
        }

        @Override
        public long getNotificationSoundTimeout(String pkg, int uid) {
            checkCallerIsSystem();
            return mPreferencesHelper.getNotificationSoundTimeout(pkg, uid);
        }

        /**
         * Updates the enabled state for notifications for the given package (and uid).
         * Additionally, this method marks the app importance as locked by the user, which means
         * that notifications from the app will <b>not</b> be considered for showing a
         * blocking helper.
         *
         * @param pkg package that owns the notifications to update
         * @param uid uid of the app providing notifications
         * @param enabled whether notifications should be enabled for the app
         *
         * @see #setNotificationsEnabledForPackage(String, int, boolean)
         */
        @Override
        public void setNotificationsEnabledWithImportanceLockForPackage(
                String pkg, int uid, boolean enabled) {
            setNotificationsEnabledForPackage(pkg, uid, enabled);

            mPreferencesHelper.setAppImportanceLocked(pkg, uid);
        }

        /**
         * Use this when you just want to know if notifications are OK for this package.
         */
        @Override
        public boolean areNotificationsEnabled(String pkg) {
            return areNotificationsEnabledForPackage(pkg, Binder.getCallingUid());
        }

        /**
         * Use this when you just want to know if notifications are OK for this package.
         */
        @Override
        public boolean areNotificationsEnabledForPackage(String pkg, int uid) {
            enforceSystemOrSystemUIOrSamePackage(pkg,
                    "Caller not system or systemui or same package");
            if (UserHandle.getCallingUserId() != UserHandle.getUserId(uid)) {
                getContext().enforceCallingPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS,
                        "canNotifyAsPackage for uid " + uid);
            }

            return mPreferencesHelper.getImportance(pkg, uid) != IMPORTANCE_NONE;
        }

        @Override
        public boolean areBubblesAllowed(String pkg) {
            return areBubblesAllowedForPackage(pkg, Binder.getCallingUid());
        }

        @Override
        public boolean areBubblesAllowedForPackage(String pkg, int uid) {
            enforceSystemOrSystemUIOrSamePackage(pkg,
                    "Caller not system or systemui or same package");

            if (UserHandle.getCallingUserId() != UserHandle.getUserId(uid)) {
                getContext().enforceCallingPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS,
                        "canNotifyAsPackage for uid " + uid);
            }

            return mPreferencesHelper.areBubblesAllowed(pkg, uid);
        }

        @Override
        public void setBubblesAllowed(String pkg, int uid, boolean allowed) {
            enforceSystemOrSystemUI("Caller not system or systemui");
            mPreferencesHelper.setBubblesAllowed(pkg, uid, allowed);
            handleSavePolicyFile();
        }

        @Override
        public boolean hasUserApprovedBubblesForPackage(String pkg, int uid) {
            enforceSystemOrSystemUI("Caller not system or systemui");
            int lockedFields = mPreferencesHelper.getAppLockedFields(pkg, uid);
            return (lockedFields & PreferencesHelper.LockableAppFields.USER_LOCKED_BUBBLE) != 0;
        }

        @Override
        public boolean shouldHideSilentStatusIcons(String callingPkg) {
            checkCallerIsSameApp(callingPkg);

            if (isCallerSystemOrPhone()
                    || mListeners.isListenerPackage(callingPkg)) {
                return mPreferencesHelper.shouldHideSilentStatusIcons();
            } else {
                throw new SecurityException("Only available for notification listeners");
            }
        }

        @Override
        public void setHideSilentStatusIcons(boolean hide) {
            checkCallerIsSystem();

            mPreferencesHelper.setHideSilentStatusIcons(hide);
            handleSavePolicyFile();

            mListeners.onStatusBarIconsBehaviorChanged(hide);
        }

        @Override
        public int getPackageImportance(String pkg) {
            checkCallerIsSystemOrSameApp(pkg);
            return mPreferencesHelper.getImportance(pkg, Binder.getCallingUid());
        }

        @Override
        public boolean canShowBadge(String pkg, int uid) {
            checkCallerIsSystem();
            return mPreferencesHelper.canShowBadge(pkg, uid);
        }

        @Override
        public void setShowBadge(String pkg, int uid, boolean showBadge) {
            checkCallerIsSystem();
            mPreferencesHelper.setShowBadge(pkg, uid, showBadge);
            handleSavePolicyFile();
        }

        @Override
        public void setNotificationDelegate(String callingPkg, String delegate) {
            checkCallerIsSameApp(callingPkg);
            final int callingUid = Binder.getCallingUid();
            UserHandle user = UserHandle.getUserHandleForUid(callingUid);
            if (delegate == null) {
                mPreferencesHelper.revokeNotificationDelegate(callingPkg, Binder.getCallingUid());
                handleSavePolicyFile();
            } else {
                try {
                    ApplicationInfo info =
                            mPackageManager.getApplicationInfo(delegate,
                                    MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                                    user.getIdentifier());
                    if (info != null) {
                        mPreferencesHelper.setNotificationDelegate(
                                callingPkg, callingUid, delegate, info.uid);
                        handleSavePolicyFile();
                    }
                } catch (RemoteException e) {
                    e.rethrowFromSystemServer();
                }
            }
        }

        @Override
        public String getNotificationDelegate(String callingPkg) {
            // callable by Settings also
            checkCallerIsSystemOrSameApp(callingPkg);
            return mPreferencesHelper.getNotificationDelegate(callingPkg, Binder.getCallingUid());
        }

        @Override
        public boolean canNotifyAsPackage(String callingPkg, String targetPkg, int userId) {
            checkCallerIsSameApp(callingPkg);
            final int callingUid = Binder.getCallingUid();
            UserHandle user = UserHandle.getUserHandleForUid(callingUid);
            if (user.getIdentifier() != userId) {
                getContext().enforceCallingPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS,
                        "canNotifyAsPackage for user " + userId);
            }
            if (callingPkg.equals(targetPkg)) {
                return true;
            }
            try {
                ApplicationInfo info =
                        mPackageManager.getApplicationInfo(targetPkg,
                                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                                userId);
                if (info != null) {
                    return mPreferencesHelper.isDelegateAllowed(
                            targetPkg, info.uid, callingPkg, callingUid);
                }
            } catch (RemoteException e) {
                // :(
            }
            return false;
        }

        @Override
        public void updateNotificationChannelGroupForPackage(String pkg, int uid,
                NotificationChannelGroup group) throws RemoteException {
            enforceSystemOrSystemUI("Caller not system or systemui");
            createNotificationChannelGroup(pkg, uid, group, false, false);
            handleSavePolicyFile();
        }

        @Override
        public void createNotificationChannelGroups(String pkg,
                ParceledListSlice channelGroupList) throws RemoteException {
            checkCallerIsSystemOrSameApp(pkg);
            List<NotificationChannelGroup> groups = channelGroupList.getList();
            final int groupSize = groups.size();
            for (int i = 0; i < groupSize; i++) {
                final NotificationChannelGroup group = groups.get(i);
                createNotificationChannelGroup(pkg, Binder.getCallingUid(), group, true, false);
            }
            handleSavePolicyFile();
        }

        private void createNotificationChannelsImpl(String pkg, int uid,
                ParceledListSlice channelsList) {
            List<NotificationChannel> channels = channelsList.getList();
            final int channelsSize = channels.size();
            boolean needsPolicyFileChange = false;
            for (int i = 0; i < channelsSize; i++) {
                final NotificationChannel channel = channels.get(i);
                Preconditions.checkNotNull(channel, "channel in list is null");
                needsPolicyFileChange = mPreferencesHelper.createNotificationChannel(pkg, uid,
                        channel, true /* fromTargetApp */,
                        mConditionProviders.isPackageOrComponentAllowed(
                                pkg, UserHandle.getUserId(uid)));
                if (needsPolicyFileChange) {
                    mListeners.notifyNotificationChannelChanged(pkg,
                            UserHandle.getUserHandleForUid(uid),
                            mPreferencesHelper.getNotificationChannel(pkg, uid, channel.getId(),
                                    false),
                            NOTIFICATION_CHANNEL_OR_GROUP_ADDED);
                }
            }
            if (needsPolicyFileChange) {
                handleSavePolicyFile();
            }
        }

        @Override
        public void createNotificationChannels(String pkg,
                ParceledListSlice channelsList) throws RemoteException {
            checkCallerIsSystemOrSameApp(pkg);
            createNotificationChannelsImpl(pkg, Binder.getCallingUid(), channelsList);
        }

        @Override
        public void createNotificationChannelsForPackage(String pkg, int uid,
                ParceledListSlice channelsList) throws RemoteException {
            checkCallerIsSystem();
            createNotificationChannelsImpl(pkg, uid, channelsList);
        }

        @Override
        public NotificationChannel getNotificationChannel(String callingPkg, int userId,
                String targetPkg, String channelId) {
            if (canNotifyAsPackage(callingPkg, targetPkg, userId)
                    || isCallingUidSystem()) {
                int targetUid = -1;
                try {
                    targetUid = mPackageManagerClient.getPackageUidAsUser(targetPkg, userId);
                } catch (NameNotFoundException e) {
                    /* ignore */
                }
                return mPreferencesHelper.getNotificationChannel(
                        targetPkg, targetUid, channelId, false /* includeDeleted */);
            }
            throw new SecurityException("Pkg " + callingPkg
                    + " cannot read channels for " + targetPkg + " in " + userId);
        }

        @Override
        public NotificationChannel getNotificationChannelForPackage(String pkg, int uid,
                String channelId, boolean includeDeleted) {
            checkCallerIsSystem();
            return mPreferencesHelper.getNotificationChannel(pkg, uid, channelId, includeDeleted);
        }

        // Returns 'true' if the given channel has a notification associated
        // with an active foreground service.
        private void enforceDeletingChannelHasNoFgService(String pkg, int userId,
                String channelId) {
            if (mAmi.hasForegroundServiceNotification(pkg, userId, channelId)) {
                // Would be a behavioral change to introduce a throw here, so
                // we simply return without affecting the channel.
                Slog.w(TAG, "Package u" + userId + "/" + pkg
                        + " may not delete notification channel '"
                        + channelId + "' with fg service");
                throw new SecurityException("Not allowed to delete channel " + channelId
                        + " with a foreground service");
            }
        }

        @Override
        public void deleteNotificationChannel(String pkg, String channelId) {
            checkCallerIsSystemOrSameApp(pkg);
            final int callingUid = Binder.getCallingUid();
            final int callingUser = UserHandle.getUserId(callingUid);
            if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(channelId)) {
                throw new IllegalArgumentException("Cannot delete default channel");
            }
            enforceDeletingChannelHasNoFgService(pkg, callingUser, channelId);
            cancelAllNotificationsInt(MY_UID, MY_PID, pkg, channelId, 0, 0, true,
                    callingUser, REASON_CHANNEL_BANNED, null);
            mPreferencesHelper.deleteNotificationChannel(pkg, callingUid, channelId);
            mListeners.notifyNotificationChannelChanged(pkg,
                    UserHandle.getUserHandleForUid(callingUid),
                    mPreferencesHelper.getNotificationChannel(pkg, callingUid, channelId, true),
                    NOTIFICATION_CHANNEL_OR_GROUP_DELETED);
            handleSavePolicyFile();
        }

        @Override
        public NotificationChannelGroup getNotificationChannelGroup(String pkg, String groupId) {
            checkCallerIsSystemOrSameApp(pkg);
            return mPreferencesHelper.getNotificationChannelGroupWithChannels(
                    pkg, Binder.getCallingUid(), groupId, false);
        }

        @Override
        public ParceledListSlice<NotificationChannelGroup> getNotificationChannelGroups(
                String pkg) {
            checkCallerIsSystemOrSameApp(pkg);
            return mPreferencesHelper.getNotificationChannelGroups(
                    pkg, Binder.getCallingUid(), false, false, true);
        }

        @Override
        public void deleteNotificationChannelGroup(String pkg, String groupId) {
            checkCallerIsSystemOrSameApp(pkg);

            final int callingUid = Binder.getCallingUid();
            NotificationChannelGroup groupToDelete =
                    mPreferencesHelper.getNotificationChannelGroup(groupId, pkg, callingUid);
            if (groupToDelete != null) {
                // Preflight for allowability
                final int userId = UserHandle.getUserId(callingUid);
                List<NotificationChannel> groupChannels = groupToDelete.getChannels();
                for (int i = 0; i < groupChannels.size(); i++) {
                    enforceDeletingChannelHasNoFgService(pkg, userId,
                            groupChannels.get(i).getId());
                }
                List<NotificationChannel> deletedChannels =
                        mPreferencesHelper.deleteNotificationChannelGroup(pkg, callingUid, groupId);
                for (int i = 0; i < deletedChannels.size(); i++) {
                    final NotificationChannel deletedChannel = deletedChannels.get(i);
                    cancelAllNotificationsInt(MY_UID, MY_PID, pkg, deletedChannel.getId(), 0, 0,
                            true,
                            userId, REASON_CHANNEL_BANNED,
                            null);
                    mListeners.notifyNotificationChannelChanged(pkg,
                            UserHandle.getUserHandleForUid(callingUid),
                            deletedChannel,
                            NOTIFICATION_CHANNEL_OR_GROUP_DELETED);
                }
                mListeners.notifyNotificationChannelGroupChanged(
                        pkg, UserHandle.getUserHandleForUid(callingUid), groupToDelete,
                        NOTIFICATION_CHANNEL_OR_GROUP_DELETED);
                handleSavePolicyFile();
            }
        }

        @Override
        public void updateNotificationChannelForPackage(String pkg, int uid,
                NotificationChannel channel) {
            enforceSystemOrSystemUI("Caller not system or systemui");
            Preconditions.checkNotNull(channel);
            updateNotificationChannelInt(pkg, uid, channel, false);
        }

        @Override
        public ParceledListSlice<NotificationChannel> getNotificationChannelsForPackage(String pkg,
                int uid, boolean includeDeleted) {
            enforceSystemOrSystemUI("getNotificationChannelsForPackage");
            return mPreferencesHelper.getNotificationChannels(pkg, uid, includeDeleted);
        }

        @Override
        public int getNumNotificationChannelsForPackage(String pkg, int uid,
                boolean includeDeleted) {
            enforceSystemOrSystemUI("getNumNotificationChannelsForPackage");
            return mPreferencesHelper.getNotificationChannels(pkg, uid, includeDeleted)
                    .getList().size();
        }

        @Override
        public boolean onlyHasDefaultChannel(String pkg, int uid) {
            enforceSystemOrSystemUI("onlyHasDefaultChannel");
            return mPreferencesHelper.onlyHasDefaultChannel(pkg, uid);
        }

        @Override
        public int getDeletedChannelCount(String pkg, int uid) {
            enforceSystemOrSystemUI("getDeletedChannelCount");
            return mPreferencesHelper.getDeletedChannelCount(pkg, uid);
        }

        @Override
        public int getBlockedChannelCount(String pkg, int uid) {
            enforceSystemOrSystemUI("getBlockedChannelCount");
            return mPreferencesHelper.getBlockedChannelCount(pkg, uid);
        }

        @Override
        public ParceledListSlice<NotificationChannelGroup> getNotificationChannelGroupsForPackage(
                String pkg, int uid, boolean includeDeleted) {
            enforceSystemOrSystemUI("getNotificationChannelGroupsForPackage");
            return mPreferencesHelper.getNotificationChannelGroups(
                    pkg, uid, includeDeleted, true, false);
        }

        @Override
        public NotificationChannelGroup getPopulatedNotificationChannelGroupForPackage(
                String pkg, int uid, String groupId, boolean includeDeleted) {
            enforceSystemOrSystemUI("getPopulatedNotificationChannelGroupForPackage");
            return mPreferencesHelper.getNotificationChannelGroupWithChannels(
                    pkg, uid, groupId, includeDeleted);
        }

        @Override
        public NotificationChannelGroup getNotificationChannelGroupForPackage(
                String groupId, String pkg, int uid) {
            enforceSystemOrSystemUI("getNotificationChannelGroupForPackage");
            return mPreferencesHelper.getNotificationChannelGroup(groupId, pkg, uid);
        }

        @Override
        public ParceledListSlice<NotificationChannel> getNotificationChannels(
                String callingPkg, String targetPkg, int userId) {
            if (canNotifyAsPackage(callingPkg, targetPkg, userId)
                || isCallingUidSystem()) {
                int targetUid = -1;
                try {
                    targetUid = mPackageManagerClient.getPackageUidAsUser(targetPkg, userId);
                } catch (NameNotFoundException e) {
                    /* ignore */
                }
                return mPreferencesHelper.getNotificationChannels(
                        targetPkg, targetUid, false /* includeDeleted */);
            }
            throw new SecurityException("Pkg " + callingPkg
                    + " cannot read channels for " + targetPkg + " in " + userId);
        }

        @Override
        public int getBlockedAppCount(int userId) {
            checkCallerIsSystem();
            return mPreferencesHelper.getBlockedAppCount(userId);
        }

        @Override
        public int getAppsBypassingDndCount(int userId) {
            checkCallerIsSystem();
            return mPreferencesHelper.getAppsBypassingDndCount(userId);
        }

        @Override
        public ParceledListSlice<NotificationChannel> getNotificationChannelsBypassingDnd(
                String pkg, int userId) {
            checkCallerIsSystem();
            return mPreferencesHelper.getNotificationChannelsBypassingDnd(pkg, userId);
        }

        @Override
        public boolean areChannelsBypassingDnd() {
            return mPreferencesHelper.areChannelsBypassingDnd();
        }

        @Override
        public void clearData(String packageName, int uid, boolean fromApp) throws RemoteException {
            checkCallerIsSystem();

            // Cancel posted notifications
            cancelAllNotificationsInt(MY_UID, MY_PID, packageName, null, 0, 0, true,
                    UserHandle.getUserId(Binder.getCallingUid()), REASON_CHANNEL_BANNED, null);

            final String[] packages = new String[] {packageName};
            final int[] uids = new int[] {uid};

            // Listener & assistant
            mListeners.onPackagesChanged(true, packages, uids);
            mAssistants.onPackagesChanged(true, packages, uids);

            // Zen
            mConditionProviders.onPackagesChanged(true, packages, uids);

            // Snoozing
            mSnoozeHelper.clearData(UserHandle.getUserId(uid), packageName);

            // Reset notification preferences
            if (!fromApp) {
                mPreferencesHelper.clearData(packageName, uid);
            }

            handleSavePolicyFile();
        }

        @Override
        public List<String> getAllowedAssistantAdjustments(String pkg) {
            checkCallerIsSystemOrSameApp(pkg);

            if (!isCallerSystemOrPhone()
                    && !mAssistants.isPackageAllowed(pkg, UserHandle.getCallingUserId())) {
                    throw new SecurityException("Not currently an assistant");
            }

            return mAssistants.getAllowedAssistantAdjustments();
        }

        @Override
        public void allowAssistantAdjustment(String adjustmentType) {
            checkCallerIsSystemOrSystemUiOrShell();
            mAssistants.allowAdjustmentType(adjustmentType);

            handleSavePolicyFile();
        }

        @Override
        public void disallowAssistantAdjustment(String adjustmentType) {
            checkCallerIsSystemOrSystemUiOrShell();
            mAssistants.disallowAdjustmentType(adjustmentType);

            handleSavePolicyFile();
        }

        /**
         * System-only API for getting a list of current (i.e. not cleared) notifications.
         *
         * Requires ACCESS_NOTIFICATIONS which is signature|system.
         * @returns A list of all the notifications, in natural order.
         */
        @Override
        public StatusBarNotification[] getActiveNotifications(String callingPkg) {
            // enforce() will ensure the calling uid has the correct permission
            getContext().enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_NOTIFICATIONS,
                    "NotificationManagerService.getActiveNotifications");

            StatusBarNotification[] tmp = null;
            int uid = Binder.getCallingUid();

            // noteOp will check to make sure the callingPkg matches the uid
            if (mAppOps.noteOpNoThrow(AppOpsManager.OP_ACCESS_NOTIFICATIONS, uid, callingPkg)
                    == AppOpsManager.MODE_ALLOWED) {
                synchronized (mNotificationLock) {
                    tmp = new StatusBarNotification[mNotificationList.size()];
                    final int N = mNotificationList.size();
                    for (int i=0; i<N; i++) {
                        tmp[i] = mNotificationList.get(i).sbn;
                    }
                }
            }
            return tmp;
        }

        /**
         * Public API for getting a list of current notifications for the calling package/uid.
         *
         * Note that since notification posting is done asynchronously, this will not return
         * notifications that are in the process of being posted.
         *
         * From {@link Build.VERSION_CODES#Q}, will also return notifications you've posted as
         * an app's notification delegate via
         * {@link NotificationManager#notifyAsPackage(String, String, int, Notification)}.
         *
         * @returns A list of all the package's notifications, in natural order.
         */
        @Override
        public ParceledListSlice<StatusBarNotification> getAppActiveNotifications(String pkg,
                int incomingUserId) {
            checkCallerIsSystemOrSameApp(pkg);
            int userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                    Binder.getCallingUid(), incomingUserId, true, false,
                    "getAppActiveNotifications", pkg);
            synchronized (mNotificationLock) {
                final ArrayMap<String, StatusBarNotification> map
                        = new ArrayMap<>(mNotificationList.size() + mEnqueuedNotifications.size());
                final int N = mNotificationList.size();
                for (int i = 0; i < N; i++) {
                    StatusBarNotification sbn = sanitizeSbn(pkg, userId,
                            mNotificationList.get(i).sbn);
                    if (sbn != null) {
                        map.put(sbn.getKey(), sbn);
                    }
                }
                for(NotificationRecord snoozed: mSnoozeHelper.getSnoozed(userId, pkg)) {
                    StatusBarNotification sbn = sanitizeSbn(pkg, userId, snoozed.sbn);
                    if (sbn != null) {
                        map.put(sbn.getKey(), sbn);
                    }
                }
                final int M = mEnqueuedNotifications.size();
                for (int i = 0; i < M; i++) {
                    StatusBarNotification sbn = sanitizeSbn(pkg, userId,
                            mEnqueuedNotifications.get(i).sbn);
                    if (sbn != null) {
                        map.put(sbn.getKey(), sbn); // pending update overwrites existing post here
                    }
                }
                final ArrayList<StatusBarNotification> list = new ArrayList<>(map.size());
                list.addAll(map.values());
                return new ParceledListSlice<StatusBarNotification>(list);
            }
        }

        private StatusBarNotification sanitizeSbn(String pkg, int userId,
                StatusBarNotification sbn) {
            if (sbn.getUserId() == userId) {
                if (sbn.getPackageName().equals(pkg) || sbn.getOpPkg().equals(pkg)) {
                    // We could pass back a cloneLight() but clients might get confused and
                    // try to send this thing back to notify() again, which would not work
                    // very well.
                    return new StatusBarNotification(
                            sbn.getPackageName(),
                            sbn.getOpPkg(),
                            sbn.getId(), sbn.getTag(), sbn.getUid(), sbn.getInitialPid(),
                            sbn.getNotification().clone(),
                            sbn.getUser(), sbn.getOverrideGroupKey(), sbn.getPostTime());
                }
            }
            return null;
        }

        /**
         * System-only API for getting a list of recent (cleared, no longer shown) notifications.
         *
         * Requires ACCESS_NOTIFICATIONS which is signature|system.
         */
        @Override
        public StatusBarNotification[] getHistoricalNotifications(String callingPkg, int count) {
            // enforce() will ensure the calling uid has the correct permission
            getContext().enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_NOTIFICATIONS,
                    "NotificationManagerService.getHistoricalNotifications");

            StatusBarNotification[] tmp = null;
            int uid = Binder.getCallingUid();

            // noteOp will check to make sure the callingPkg matches the uid
            if (mAppOps.noteOpNoThrow(AppOpsManager.OP_ACCESS_NOTIFICATIONS, uid, callingPkg)
                    == AppOpsManager.MODE_ALLOWED) {
                synchronized (mArchive) {
                    tmp = mArchive.getArray(count);
                }
            }
            return tmp;
        }

        /**
         * Register a listener binder directly with the notification manager.
         *
         * Only works with system callers. Apps should extend
         * {@link android.service.notification.NotificationListenerService}.
         */
        @Override
        public void registerListener(final INotificationListener listener,
                final ComponentName component, final int userid) {
            enforceSystemOrSystemUI("INotificationManager.registerListener");
            mListeners.registerService(listener, component, userid);
        }

        /**
         * Remove a listener binder directly
         */
        @Override
        public void unregisterListener(INotificationListener token, int userid) {
            mListeners.unregisterService(token, userid);
        }

        /**
         * Allow an INotificationListener to simulate a "clear all" operation.
         *
         * {@see com.android.server.StatusBarManagerService.NotificationCallbacks#onClearAllNotifications}
         *
         * @param token The binder for the listener, to check that the caller is allowed
         */
        @Override
        public void cancelNotificationsFromListener(INotificationListener token, String[] keys) {
            final int callingUid = Binder.getCallingUid();
            final int callingPid = Binder.getCallingPid();
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);

                    if (keys != null) {
                        final int N = keys.length;
                        for (int i = 0; i < N; i++) {
                            NotificationRecord r = mNotificationsByKey.get(keys[i]);
                            if (r == null) continue;
                            final int userId = r.sbn.getUserId();
                            if (userId != info.userid && userId != UserHandle.USER_ALL &&
                                    !mUserProfiles.isCurrentProfile(userId)) {
                                throw new SecurityException("Disallowed call from listener: "
                                        + info.service);
                            }
                            cancelNotificationFromListenerLocked(info, callingUid, callingPid,
                                    r.sbn.getPackageName(), r.sbn.getTag(), r.sbn.getId(),
                                    userId);
                        }
                    } else {
                        cancelAllLocked(callingUid, callingPid, info.userid,
                                REASON_LISTENER_CANCEL_ALL, info, info.supportsProfiles());
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Handle request from an approved listener to re-enable itself.
         *
         * @param component The componenet to be re-enabled, caller must match package.
         */
        @Override
        public void requestBindListener(ComponentName component) {
            checkCallerIsSystemOrSameApp(component.getPackageName());
            long identity = Binder.clearCallingIdentity();
            try {
                ManagedServices manager =
                        mAssistants.isComponentEnabledForCurrentProfiles(component)
                        ? mAssistants
                        : mListeners;
                manager.setComponentState(component, true);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void requestUnbindListener(INotificationListener token) {
            long identity = Binder.clearCallingIdentity();
            try {
                // allow bound services to disable themselves
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    info.getOwner().setComponentState(info.component, false);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void setNotificationsShownFromListener(INotificationListener token, String[] keys) {
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    if (keys == null) {
                        return;
                    }
                    ArrayList<NotificationRecord> seen = new ArrayList<>();
                    final int n = keys.length;
                    for (int i = 0; i < n; i++) {
                        NotificationRecord r = mNotificationsByKey.get(keys[i]);
                        if (r == null) continue;
                        final int userId = r.sbn.getUserId();
                        if (userId != info.userid && userId != UserHandle.USER_ALL
                                && !mUserProfiles.isCurrentProfile(userId)) {
                            throw new SecurityException("Disallowed call from listener: "
                                    + info.service);
                        }
                        seen.add(r);
                        if (!r.isSeen()) {
                            if (DBG) Slog.d(TAG, "Marking notification as seen " + keys[i]);
                            reportSeen(r);
                            r.setSeen();
                            maybeRecordInterruptionLocked(r);
                        }
                    }
                    if (!seen.isEmpty()) {
                        mAssistants.onNotificationsSeenLocked(seen);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Allow an INotificationListener to simulate clearing (dismissing) a single notification.
         *
         * {@see com.android.server.StatusBarManagerService.NotificationCallbacks#onNotificationClear}
         *
         * @param info The binder for the listener, to check that the caller is allowed
         */
        @GuardedBy("mNotificationLock")
        private void cancelNotificationFromListenerLocked(ManagedServiceInfo info,
                int callingUid, int callingPid, String pkg, String tag, int id, int userId) {
            cancelNotification(callingUid, callingPid, pkg, tag, id, 0,
                    FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE | FLAG_BUBBLE,
                    true,
                    userId, REASON_LISTENER_CANCEL, info);
        }

        /**
         * Allow an INotificationListener to snooze a single notification until a context.
         *
         * @param token The binder for the listener, to check that the caller is allowed
         */
        @Override
        public void snoozeNotificationUntilContextFromListener(INotificationListener token,
                String key, String snoozeCriterionId) {
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    snoozeNotificationInt(key, SNOOZE_UNTIL_UNSPECIFIED, snoozeCriterionId, info);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Allow an INotificationListener to snooze a single notification until a time.
         *
         * @param token The binder for the listener, to check that the caller is allowed
         */
        @Override
        public void snoozeNotificationUntilFromListener(INotificationListener token, String key,
                long duration) {
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    snoozeNotificationInt(key, duration, null, info);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Allows the notification assistant to un-snooze a single notification.
         *
         * @param token The binder for the assistant, to check that the caller is allowed
         */
        @Override
        public void unsnoozeNotificationFromAssistant(INotificationListener token, String key) {
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info =
                            mAssistants.checkServiceTokenLocked(token);
                    unsnoozeNotificationInt(key, info);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Allow an INotificationListener to simulate clearing (dismissing) a single notification.
         *
         * {@see com.android.server.StatusBarManagerService.NotificationCallbacks#onNotificationClear}
         *
         * @param token The binder for the listener, to check that the caller is allowed
         */
        @Override
        public void cancelNotificationFromListener(INotificationListener token, String pkg,
                String tag, int id) {
            final int callingUid = Binder.getCallingUid();
            final int callingPid = Binder.getCallingPid();
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    if (info.supportsProfiles()) {
                        Slog.e(TAG, "Ignoring deprecated cancelNotification(pkg, tag, id) "
                                + "from " + info.component
                                + " use cancelNotification(key) instead.");
                    } else {
                        cancelNotificationFromListenerLocked(info, callingUid, callingPid,
                                pkg, tag, id, info.userid);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Allow an INotificationListener to request the list of outstanding notifications seen by
         * the current user. Useful when starting up, after which point the listener callbacks
         * should be used.
         *
         * @param token The binder for the listener, to check that the caller is allowed
         * @param keys An array of notification keys to fetch, or null to fetch everything
         * @returns The return value will contain the notifications specified in keys, in that
         *      order, or if keys is null, all the notifications, in natural order.
         */
        @Override
        public ParceledListSlice<StatusBarNotification> getActiveNotificationsFromListener(
                INotificationListener token, String[] keys, int trim) {
            synchronized (mNotificationLock) {
                final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                final boolean getKeys = keys != null;
                final int N = getKeys ? keys.length : mNotificationList.size();
                final ArrayList<StatusBarNotification> list
                        = new ArrayList<StatusBarNotification>(N);
                for (int i=0; i<N; i++) {
                    final NotificationRecord r = getKeys
                            ? mNotificationsByKey.get(keys[i])
                            : mNotificationList.get(i);
                    if (r == null) continue;
                    StatusBarNotification sbn = r.sbn;
                    if (!isVisibleToListener(sbn, info)) continue;
                    StatusBarNotification sbnToSend =
                            (trim == TRIM_FULL) ? sbn : sbn.cloneLight();
                    list.add(sbnToSend);
                }
                return new ParceledListSlice<StatusBarNotification>(list);
            }
        }

        /**
         * Allow an INotificationListener to request the list of outstanding snoozed notifications
         * seen by the current user. Useful when starting up, after which point the listener
         * callbacks should be used.
         *
         * @param token The binder for the listener, to check that the caller is allowed
         * @returns The return value will contain the notifications specified in keys, in that
         *      order, or if keys is null, all the notifications, in natural order.
         */
        @Override
        public ParceledListSlice<StatusBarNotification> getSnoozedNotificationsFromListener(
                INotificationListener token, int trim) {
            synchronized (mNotificationLock) {
                final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                List<NotificationRecord> snoozedRecords = mSnoozeHelper.getSnoozed();
                final int N = snoozedRecords.size();
                final ArrayList<StatusBarNotification> list = new ArrayList<>(N);
                for (int i=0; i < N; i++) {
                    final NotificationRecord r = snoozedRecords.get(i);
                    if (r == null) continue;
                    StatusBarNotification sbn = r.sbn;
                    if (!isVisibleToListener(sbn, info)) continue;
                    StatusBarNotification sbnToSend =
                            (trim == TRIM_FULL) ? sbn : sbn.cloneLight();
                    list.add(sbnToSend);
                }
                return new ParceledListSlice<>(list);
            }
        }

        @Override
        public void clearRequestedListenerHints(INotificationListener token) {
            final long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    removeDisabledHints(info);
                    updateListenerHintsLocked();
                    updateEffectsSuppressorLocked();
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void requestHintsFromListener(INotificationListener token, int hints) {
            final long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    final int disableEffectsMask = HINT_HOST_DISABLE_EFFECTS
                            | HINT_HOST_DISABLE_NOTIFICATION_EFFECTS
                            | HINT_HOST_DISABLE_CALL_EFFECTS;
                    final boolean disableEffects = (hints & disableEffectsMask) != 0;
                    if (disableEffects) {
                        addDisabledHints(info, hints);
                    } else {
                        removeDisabledHints(info, hints);
                    }
                    updateListenerHintsLocked();
                    updateEffectsSuppressorLocked();
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public int getHintsFromListener(INotificationListener token) {
            synchronized (mNotificationLock) {
                return mListenerHints;
            }
        }

        @Override
        public void requestInterruptionFilterFromListener(INotificationListener token,
                int interruptionFilter) throws RemoteException {
            final long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                    mZenModeHelper.requestFromListener(info.component, interruptionFilter);
                    updateInterruptionFilterLocked();
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public int getInterruptionFilterFromListener(INotificationListener token)
                throws RemoteException {
            synchronized (mNotificationLight) {
                return mInterruptionFilter;
            }
        }

        @Override
        public void setOnNotificationPostedTrimFromListener(INotificationListener token, int trim)
                throws RemoteException {
            synchronized (mNotificationLock) {
                final ManagedServiceInfo info = mListeners.checkServiceTokenLocked(token);
                if (info == null) return;
                mListeners.setOnNotificationPostedTrimLocked(info, trim);
            }
        }

        @Override
        public int getZenMode() {
            return mZenModeHelper.getZenMode();
        }

        @Override
        public ZenModeConfig getZenModeConfig() {
            enforceSystemOrSystemUI("INotificationManager.getZenModeConfig");
            return mZenModeHelper.getConfig();
        }

        @Override
        public void setZenMode(int mode, Uri conditionId, String reason) throws RemoteException {
            enforceSystemOrSystemUI("INotificationManager.setZenMode");
            final long identity = Binder.clearCallingIdentity();
            try {
                mZenModeHelper.setManualZenMode(mode, conditionId, null, reason);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public List<ZenModeConfig.ZenRule> getZenRules() throws RemoteException {
            enforcePolicyAccess(Binder.getCallingUid(), "getAutomaticZenRules");
            return mZenModeHelper.getZenRules();
        }

        @Override
        public AutomaticZenRule getAutomaticZenRule(String id) throws RemoteException {
            Preconditions.checkNotNull(id, "Id is null");
            enforcePolicyAccess(Binder.getCallingUid(), "getAutomaticZenRule");
            return mZenModeHelper.getAutomaticZenRule(id);
        }

        @Override
        public String addAutomaticZenRule(AutomaticZenRule automaticZenRule) {
            Preconditions.checkNotNull(automaticZenRule, "automaticZenRule is null");
            Preconditions.checkNotNull(automaticZenRule.getName(), "Name is null");
            if (automaticZenRule.getOwner() == null
                    && automaticZenRule.getConfigurationActivity() == null) {
                throw new NullPointerException(
                        "Rule must have a conditionproviderservice and/or configuration activity");
            }
            Preconditions.checkNotNull(automaticZenRule.getConditionId(), "ConditionId is null");
            if (automaticZenRule.getZenPolicy() != null
                    && automaticZenRule.getInterruptionFilter() != INTERRUPTION_FILTER_PRIORITY) {
                throw new IllegalArgumentException("ZenPolicy is only applicable to "
                        + "INTERRUPTION_FILTER_PRIORITY filters");
            }
            enforcePolicyAccess(Binder.getCallingUid(), "addAutomaticZenRule");

            return mZenModeHelper.addAutomaticZenRule(automaticZenRule,
                    "addAutomaticZenRule");
        }

        @Override
        public boolean updateAutomaticZenRule(String id, AutomaticZenRule automaticZenRule)
                throws RemoteException {
            Preconditions.checkNotNull(automaticZenRule, "automaticZenRule is null");
            Preconditions.checkNotNull(automaticZenRule.getName(), "Name is null");
            if (automaticZenRule.getOwner() == null
                    && automaticZenRule.getConfigurationActivity() == null) {
                throw new NullPointerException(
                        "Rule must have a conditionproviderservice and/or configuration activity");
            }
            Preconditions.checkNotNull(automaticZenRule.getConditionId(), "ConditionId is null");
            enforcePolicyAccess(Binder.getCallingUid(), "updateAutomaticZenRule");

            return mZenModeHelper.updateAutomaticZenRule(id, automaticZenRule,
                    "updateAutomaticZenRule");
        }

        @Override
        public boolean removeAutomaticZenRule(String id) throws RemoteException {
            Preconditions.checkNotNull(id, "Id is null");
            // Verify that they can modify zen rules.
            enforcePolicyAccess(Binder.getCallingUid(), "removeAutomaticZenRule");

            return mZenModeHelper.removeAutomaticZenRule(id, "removeAutomaticZenRule");
        }

        @Override
        public boolean removeAutomaticZenRules(String packageName) throws RemoteException {
            Preconditions.checkNotNull(packageName, "Package name is null");
            enforceSystemOrSystemUI("removeAutomaticZenRules");

            return mZenModeHelper.removeAutomaticZenRules(packageName, "removeAutomaticZenRules");
        }

        @Override
        public int getRuleInstanceCount(ComponentName owner) throws RemoteException {
            Preconditions.checkNotNull(owner, "Owner is null");
            enforceSystemOrSystemUI("getRuleInstanceCount");

            return mZenModeHelper.getCurrentInstanceCount(owner);
        }

        @Override
        public void setAutomaticZenRuleState(String id, Condition condition) {
            Preconditions.checkNotNull(id, "id is null");
            Preconditions.checkNotNull(condition, "Condition is null");

            enforcePolicyAccess(Binder.getCallingUid(), "setAutomaticZenRuleState");

            mZenModeHelper.setAutomaticZenRuleState(id, condition);
        }

        @Override
        public void setInterruptionFilter(String pkg, int filter) throws RemoteException {
            enforcePolicyAccess(pkg, "setInterruptionFilter");
            final int zen = NotificationManager.zenModeFromInterruptionFilter(filter, -1);
            if (zen == -1) throw new IllegalArgumentException("Invalid filter: " + filter);
            final long identity = Binder.clearCallingIdentity();
            try {
                mZenModeHelper.setManualZenMode(zen, null, pkg, "setInterruptionFilter");
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void notifyConditions(final String pkg, IConditionProvider provider,
                final Condition[] conditions) {
            final ManagedServiceInfo info = mConditionProviders.checkServiceToken(provider);
            checkCallerIsSystemOrSameApp(pkg);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mConditionProviders.notifyConditions(pkg, info, conditions);
                }
            });
        }

        @Override
        public void requestUnbindProvider(IConditionProvider provider) {
            long identity = Binder.clearCallingIdentity();
            try {
                // allow bound services to disable themselves
                final ManagedServiceInfo info = mConditionProviders.checkServiceToken(provider);
                info.getOwner().setComponentState(info.component, false);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void requestBindProvider(ComponentName component) {
            checkCallerIsSystemOrSameApp(component.getPackageName());
            long identity = Binder.clearCallingIdentity();
            try {
                mConditionProviders.setComponentState(component, true);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        private void enforceSystemOrSystemUI(String message) {
            if (isCallerSystemOrPhone()) return;
            getContext().enforceCallingPermission(android.Manifest.permission.STATUS_BAR_SERVICE,
                    message);
        }

        private void enforceSystemOrSystemUIOrSamePackage(String pkg, String message) {
            try {
                checkCallerIsSystemOrSameApp(pkg);
            } catch (SecurityException e) {
                getContext().enforceCallingPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        message);
            }
        }

        private void enforcePolicyAccess(int uid, String method) {
            if (PackageManager.PERMISSION_GRANTED == getContext().checkCallingPermission(
                    android.Manifest.permission.MANAGE_NOTIFICATIONS)) {
                return;
            }
            boolean accessAllowed = false;
            String[] packages = mPackageManagerClient.getPackagesForUid(uid);
            final int packageCount = packages.length;
            for (int i = 0; i < packageCount; i++) {
                if (mConditionProviders.isPackageOrComponentAllowed(
                        packages[i], UserHandle.getUserId(uid))) {
                    accessAllowed = true;
                }
            }
            if (!accessAllowed) {
                Slog.w(TAG, "Notification policy access denied calling " + method);
                throw new SecurityException("Notification policy access denied");
            }
        }

        private void enforcePolicyAccess(String pkg, String method) {
            if (PackageManager.PERMISSION_GRANTED == getContext().checkCallingPermission(
                    android.Manifest.permission.MANAGE_NOTIFICATIONS)) {
                return;
            }
            checkCallerIsSameApp(pkg);
            if (!checkPolicyAccess(pkg)) {
                Slog.w(TAG, "Notification policy access denied calling " + method);
                throw new SecurityException("Notification policy access denied");
            }
        }

        private boolean checkPackagePolicyAccess(String pkg) {
            return mConditionProviders.isPackageOrComponentAllowed(
                    pkg, getCallingUserHandle().getIdentifier());
        }

        private boolean checkPolicyAccess(String pkg) {
            try {
                int uid = getContext().getPackageManager().getPackageUidAsUser(pkg,
                        UserHandle.getCallingUserId());
                if (PackageManager.PERMISSION_GRANTED == ActivityManager.checkComponentPermission(
                        android.Manifest.permission.MANAGE_NOTIFICATIONS, uid,
                        -1, true)) {
                    return true;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
            return checkPackagePolicyAccess(pkg)
                    || mListeners.isComponentEnabledForPackage(pkg)
                    || (mDpm != null &&
                            mDpm.isActiveAdminWithPolicy(Binder.getCallingUid(),
                                    DeviceAdminInfo.USES_POLICY_PROFILE_OWNER));
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpAndUsageStatsPermission(getContext(), TAG, pw)) return;
            final DumpFilter filter = DumpFilter.parseFromArguments(args);
            final long token = Binder.clearCallingIdentity();
            try {
                if (filter.stats) {
                    dumpJson(pw, filter);
                } else if (filter.rvStats) {
                    dumpRemoteViewStats(pw, filter);
                } else if (filter.proto) {
                    dumpProto(fd, filter);
                } else if (filter.criticalPriority) {
                    dumpNotificationRecords(pw, filter);
                } else {
                    dumpImpl(pw, filter);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public ComponentName getEffectsSuppressor() {
            return !mEffectsSuppressors.isEmpty() ? mEffectsSuppressors.get(0) : null;
        }

        @Override
        public boolean matchesCallFilter(Bundle extras) {
            enforceSystemOrSystemUI("INotificationManager.matchesCallFilter");
            return mZenModeHelper.matchesCallFilter(
                    Binder.getCallingUserHandle(),
                    extras,
                    mRankingHelper.findExtractor(ValidateNotificationPeople.class),
                    MATCHES_CALL_FILTER_CONTACTS_TIMEOUT_MS,
                    MATCHES_CALL_FILTER_TIMEOUT_AFFINITY);
        }

        @Override
        public boolean isSystemConditionProviderEnabled(String path) {
            enforceSystemOrSystemUI("INotificationManager.isSystemConditionProviderEnabled");
            return mConditionProviders.isSystemProviderEnabled(path);
        }

        // Backup/restore interface
        @Override
        public byte[] getBackupPayload(int user) {
            checkCallerIsSystem();
            if (DBG) Slog.d(TAG, "getBackupPayload u=" + user);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                writePolicyXml(baos, true /*forBackup*/, user);
                return baos.toByteArray();
            } catch (IOException e) {
                Slog.w(TAG, "getBackupPayload: error writing payload for user " + user, e);
            }
            return null;
        }

        @Override
        public void applyRestore(byte[] payload, int user) {
            checkCallerIsSystem();
            if (DBG) Slog.d(TAG, "applyRestore u=" + user + " payload="
                    + (payload != null ? new String(payload, StandardCharsets.UTF_8) : null));
            if (payload == null) {
                Slog.w(TAG, "applyRestore: no payload to restore for user " + user);
                return;
            }
            final ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            try {
                readPolicyXml(bais, true /*forRestore*/, user);
                handleSavePolicyFile();
            } catch (NumberFormatException | XmlPullParserException | IOException e) {
                Slog.w(TAG, "applyRestore: error reading payload", e);
            }
        }

        @Override
        public boolean isNotificationPolicyAccessGranted(String pkg) {
            return checkPolicyAccess(pkg);
        }

        @Override
        public boolean isNotificationPolicyAccessGrantedForPackage(String pkg) {;
            enforceSystemOrSystemUIOrSamePackage(pkg,
                    "request policy access status for another package");
            return checkPolicyAccess(pkg);
        }

        @Override
        public void setNotificationPolicyAccessGranted(String pkg, boolean granted)
                throws RemoteException {
            setNotificationPolicyAccessGrantedForUser(
                    pkg, getCallingUserHandle().getIdentifier(), granted);
        }

        @Override
        public void setNotificationPolicyAccessGrantedForUser(
                String pkg, int userId, boolean granted) {
            checkCallerIsSystemOrShell();
            final long identity = Binder.clearCallingIdentity();
            try {
                if (mAllowedManagedServicePackages.test(
                        pkg, userId, mConditionProviders.getRequiredPermission())) {
                    mConditionProviders.setPackageOrComponentEnabled(
                            pkg, userId, true, granted);

                    getContext().sendBroadcastAsUser(new Intent(
                            ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
                                    .setPackage(pkg)
                                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT),
                            UserHandle.of(userId), null);
                    handleSavePolicyFile();
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public Policy getNotificationPolicy(String pkg) {
            final long identity = Binder.clearCallingIdentity();
            try {
                return mZenModeHelper.getNotificationPolicy();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public Policy getConsolidatedNotificationPolicy() {
            final long identity = Binder.clearCallingIdentity();
            try {
                return mZenModeHelper.getConsolidatedNotificationPolicy();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Sets the notification policy.  Apps that target API levels below
         * {@link android.os.Build.VERSION_CODES#P} cannot change user-designated values to
         * allow or disallow {@link Policy#PRIORITY_CATEGORY_ALARMS},
         * {@link Policy#PRIORITY_CATEGORY_SYSTEM} and
         * {@link Policy#PRIORITY_CATEGORY_MEDIA} from bypassing dnd
         */
        @Override
        public void setNotificationPolicy(String pkg, Policy policy) {
            enforcePolicyAccess(pkg, "setNotificationPolicy");
            final long identity = Binder.clearCallingIdentity();
            try {
                final ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(pkg,
                        0, UserHandle.getUserId(MY_UID));
                Policy currPolicy = mZenModeHelper.getNotificationPolicy();

                if (applicationInfo.targetSdkVersion < Build.VERSION_CODES.P) {
                    int priorityCategories = policy.priorityCategories;
                    // ignore alarm and media values from new policy
                    priorityCategories &= ~Policy.PRIORITY_CATEGORY_ALARMS;
                    priorityCategories &= ~Policy.PRIORITY_CATEGORY_MEDIA;
                    priorityCategories &= ~Policy.PRIORITY_CATEGORY_SYSTEM;
                    // use user-designated values
                    priorityCategories |= currPolicy.priorityCategories
                            & Policy.PRIORITY_CATEGORY_ALARMS;
                    priorityCategories |= currPolicy.priorityCategories
                            & Policy.PRIORITY_CATEGORY_MEDIA;
                    priorityCategories |= currPolicy.priorityCategories
                            & Policy.PRIORITY_CATEGORY_SYSTEM;

                    policy = new Policy(priorityCategories,
                            policy.priorityCallSenders, policy.priorityMessageSenders,
                            policy.suppressedVisualEffects);
                }
                int newVisualEffects = calculateSuppressedVisualEffects(
                            policy, currPolicy, applicationInfo.targetSdkVersion);
                policy = new Policy(policy.priorityCategories,
                        policy.priorityCallSenders, policy.priorityMessageSenders,
                        newVisualEffects);
                ZenLog.traceSetNotificationPolicy(pkg, applicationInfo.targetSdkVersion, policy);
                mZenModeHelper.setNotificationPolicy(policy);
            } catch (RemoteException e) {
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public List<String> getEnabledNotificationListenerPackages() {
            checkCallerIsSystem();
            return mListeners.getAllowedPackages(getCallingUserHandle().getIdentifier());
        }

        @Override
        public List<ComponentName> getEnabledNotificationListeners(int userId) {
            checkCallerIsSystem();
            return mListeners.getAllowedComponents(userId);
        }

        @Override
        public ComponentName getAllowedNotificationAssistantForUser(int userId) {
            checkCallerIsSystemOrSystemUiOrShell();
            List<ComponentName> allowedComponents = mAssistants.getAllowedComponents(userId);
            if (allowedComponents.size() > 1) {
                throw new IllegalStateException(
                        "At most one NotificationAssistant: " + allowedComponents.size());
            }
            return CollectionUtils.firstOrNull(allowedComponents);
        }

        @Override
        public ComponentName getAllowedNotificationAssistant() {
            return getAllowedNotificationAssistantForUser(getCallingUserHandle().getIdentifier());
        }

        @Override
        public boolean isNotificationListenerAccessGranted(ComponentName listener) {
            Preconditions.checkNotNull(listener);
            checkCallerIsSystemOrSameApp(listener.getPackageName());
            return mListeners.isPackageOrComponentAllowed(listener.flattenToString(),
                    getCallingUserHandle().getIdentifier());
        }

        @Override
        public boolean isNotificationListenerAccessGrantedForUser(ComponentName listener,
                int userId) {
            Preconditions.checkNotNull(listener);
            checkCallerIsSystem();
            return mListeners.isPackageOrComponentAllowed(listener.flattenToString(),
                    userId);
        }

        @Override
        public boolean isNotificationAssistantAccessGranted(ComponentName assistant) {
            Preconditions.checkNotNull(assistant);
            checkCallerIsSystemOrSameApp(assistant.getPackageName());
            return mAssistants.isPackageOrComponentAllowed(assistant.flattenToString(),
                    getCallingUserHandle().getIdentifier());
        }

        @Override
        public void setNotificationListenerAccessGranted(ComponentName listener,
                boolean granted) throws RemoteException {
            setNotificationListenerAccessGrantedForUser(
                    listener, getCallingUserHandle().getIdentifier(), granted);
        }

        @Override
        public void setNotificationAssistantAccessGranted(ComponentName assistant,
                boolean granted) {
            setNotificationAssistantAccessGrantedForUser(
                    assistant, getCallingUserHandle().getIdentifier(), granted);
        }

        @Override
        public void setNotificationListenerAccessGrantedForUser(ComponentName listener, int userId,
                boolean granted) {
            Preconditions.checkNotNull(listener);
            checkCallerIsSystemOrShell();
            final long identity = Binder.clearCallingIdentity();
            try {
                if (mAllowedManagedServicePackages.test(
                        listener.getPackageName(), userId, mListeners.getRequiredPermission())) {
                    mConditionProviders.setPackageOrComponentEnabled(listener.flattenToString(),
                            userId, false, granted);
                    mListeners.setPackageOrComponentEnabled(listener.flattenToString(),
                            userId, true, granted);

                    getContext().sendBroadcastAsUser(new Intent(
                            ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
                                    .setPackage(listener.getPackageName())
                                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
                            UserHandle.of(userId), null);

                    handleSavePolicyFile();
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void setNotificationAssistantAccessGrantedForUser(ComponentName assistant,
                int userId, boolean granted) {
            checkCallerIsSystemOrSystemUiOrShell();
            for (UserInfo ui : mUm.getEnabledProfiles(userId)) {
                mAssistants.setUserSet(ui.id, true);
            }
            final long identity = Binder.clearCallingIdentity();
            try {
                setNotificationAssistantAccessGrantedForUserInternal(assistant, userId, granted);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void applyEnqueuedAdjustmentFromAssistant(INotificationListener token,
                Adjustment adjustment) {
            boolean foundEnqueued = false;
            final long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    mAssistants.checkServiceTokenLocked(token);
                    int N = mEnqueuedNotifications.size();
                    for (int i = 0; i < N; i++) {
                        final NotificationRecord r = mEnqueuedNotifications.get(i);
                        if (Objects.equals(adjustment.getKey(), r.getKey())
                                && Objects.equals(adjustment.getUser(), r.getUserId())
                                && mAssistants.isSameUser(token, r.getUserId())) {
                            applyAdjustment(r, adjustment);
                            r.applyAdjustments();
                            // importance is checked at the beginning of the
                            // PostNotificationRunnable, before the signal extractors are run, so
                            // calculate the final importance here
                            r.calculateImportance();
                            foundEnqueued = true;
                            break;
                        }
                    }
                    if (!foundEnqueued) {
                        applyAdjustmentFromAssistant(token, adjustment);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void applyAdjustmentFromAssistant(INotificationListener token,
                Adjustment adjustment) {
            List<Adjustment> adjustments = new ArrayList<>();
            adjustments.add(adjustment);
            applyAdjustmentsFromAssistant(token, adjustments);
        }

        @Override
        public void applyAdjustmentsFromAssistant(INotificationListener token,
                List<Adjustment> adjustments) {

            boolean needsSort = false;
            final long identity = Binder.clearCallingIdentity();
            try {
                synchronized (mNotificationLock) {
                    mAssistants.checkServiceTokenLocked(token);
                    for (Adjustment adjustment : adjustments) {
                        NotificationRecord r = mNotificationsByKey.get(adjustment.getKey());
                        if (r != null && mAssistants.isSameUser(token, r.getUserId())) {
                            applyAdjustment(r, adjustment);
                            // If the assistant has blocked the notification, cancel it
                            // This will trigger a sort, so we don't have to explicitly ask for
                            // one here.
                            if (adjustment.getSignals().containsKey(Adjustment.KEY_IMPORTANCE)
                                    && adjustment.getSignals().getInt(Adjustment.KEY_IMPORTANCE)
                                    == IMPORTANCE_NONE) {
                                cancelNotificationsFromListener(token, new String[]{r.getKey()});
                            } else {
                                needsSort = true;
                            }
                        }
                    }
                }
                if (needsSort) {
                    mRankingHandler.requestSort();
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public void updateNotificationChannelGroupFromPrivilegedListener(
                INotificationListener token, String pkg, UserHandle user,
                NotificationChannelGroup group) throws RemoteException {
            Preconditions.checkNotNull(user);
            verifyPrivilegedListener(token, user, false);
            createNotificationChannelGroup(
                    pkg, getUidForPackageAndUser(pkg, user), group, false, true);
            handleSavePolicyFile();
        }

        @Override
        public void updateNotificationChannelFromPrivilegedListener(INotificationListener token,
                String pkg, UserHandle user, NotificationChannel channel) throws RemoteException {
            Preconditions.checkNotNull(channel);
            Preconditions.checkNotNull(pkg);
            Preconditions.checkNotNull(user);

            verifyPrivilegedListener(token, user, false);
            updateNotificationChannelInt(pkg, getUidForPackageAndUser(pkg, user), channel, true);
        }

        @Override
        public ParceledListSlice<NotificationChannel> getNotificationChannelsFromPrivilegedListener(
                INotificationListener token, String pkg, UserHandle user) throws RemoteException {
            Preconditions.checkNotNull(pkg);
            Preconditions.checkNotNull(user);
            verifyPrivilegedListener(token, user, true);

            return mPreferencesHelper.getNotificationChannels(pkg, getUidForPackageAndUser(pkg, user),
                    false /* includeDeleted */);
        }

        @Override
        public ParceledListSlice<NotificationChannelGroup>
                getNotificationChannelGroupsFromPrivilegedListener(
                INotificationListener token, String pkg, UserHandle user) throws RemoteException {
            Preconditions.checkNotNull(pkg);
            Preconditions.checkNotNull(user);
            verifyPrivilegedListener(token, user, true);

            List<NotificationChannelGroup> groups = new ArrayList<>();
            groups.addAll(mPreferencesHelper.getNotificationChannelGroups(
                    pkg, getUidForPackageAndUser(pkg, user)));
            return new ParceledListSlice<>(groups);
        }

        @Override
        public void setPrivateNotificationsAllowed(boolean allow) {
            if (PackageManager.PERMISSION_GRANTED
                    != getContext().checkCallingPermission(
                            permission.CONTROL_KEYGUARD_SECURE_NOTIFICATIONS)) {
                throw new SecurityException(
                        "Requires CONTROL_KEYGUARD_SECURE_NOTIFICATIONS permission");
            }
            if (allow != mLockScreenAllowSecureNotifications) {
                mLockScreenAllowSecureNotifications = allow;
                handleSavePolicyFile();
            }
        }

        @Override
        public boolean getPrivateNotificationsAllowed() {
            if (PackageManager.PERMISSION_GRANTED
                    != getContext().checkCallingPermission(
                            permission.CONTROL_KEYGUARD_SECURE_NOTIFICATIONS)) {
                throw new SecurityException(
                        "Requires CONTROL_KEYGUARD_SECURE_NOTIFICATIONS permission");
            }
            return mLockScreenAllowSecureNotifications;
        }

        @Override
        public boolean isPackagePaused(String pkg) {
            Preconditions.checkNotNull(pkg);
            checkCallerIsSameApp(pkg);

            return isPackagePausedOrSuspended(pkg, Binder.getCallingUid());
        }

        private void verifyPrivilegedListener(INotificationListener token, UserHandle user,
                boolean assistantAllowed) {
            ManagedServiceInfo info;
            synchronized (mNotificationLock) {
                info = mListeners.checkServiceTokenLocked(token);
            }
            if (!hasCompanionDevice(info)) {
                synchronized (mNotificationLock) {
                    if (!assistantAllowed || !mAssistants.isServiceTokenValidLocked(info.service)) {
                        throw new SecurityException(info + " does not have access");
                    }
                }
            }
            if (!info.enabledAndUserMatches(user.getIdentifier())) {
                throw new SecurityException(info + " does not have access");
            }
        }

        private int getUidForPackageAndUser(String pkg, UserHandle user) throws RemoteException {
            int uid = 0;
            long identity = Binder.clearCallingIdentity();
            try {
                uid = mPackageManager.getPackageUid(pkg, 0, user.getIdentifier());
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            return uid;
        }

        @Override
        public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
                String[] args, ShellCallback callback, ResultReceiver resultReceiver)
                throws RemoteException {
            new NotificationShellCmd(NotificationManagerService.this)
                    .exec(this, in, out, err, args, callback, resultReceiver);
        }

        /**
         * Get stats committed after startNs
         *
         * @param startNs Report stats committed after this time in nanoseconds.
         * @param report  Indicatess which section to include in the stats.
         * @param doAgg   Whether to aggregate the stats or keep them separated.
         * @param out   List of protos of individual commits or one representing the
         *                aggregate.
         * @return the report time in nanoseconds, or 0 on error.
         */
        @Override
        public long pullStats(long startNs, int report, boolean doAgg,
                List<ParcelFileDescriptor> out) {
            checkCallerIsSystemOrShell();
            long startMs = TimeUnit.MILLISECONDS.convert(startNs, TimeUnit.NANOSECONDS);

            final long identity = Binder.clearCallingIdentity();
            try {
                switch (report) {
                    case REPORT_REMOTE_VIEWS:
                        Slog.e(TAG, "pullStats REPORT_REMOTE_VIEWS from: "
                                + startMs + "  wtih " + doAgg);
                        PulledStats stats = mUsageStats.remoteViewStats(startMs, doAgg);
                        if (stats != null) {
                            out.add(stats.toParcelFileDescriptor(report));
                            Slog.e(TAG, "exiting pullStats with: " + out.size());
                            long endNs = TimeUnit.NANOSECONDS
                                    .convert(stats.endTimeMs(), TimeUnit.MILLISECONDS);
                            return endNs;
                        }
                        Slog.e(TAG, "null stats for: " + report);
                }
            } catch (IOException e) {

                Slog.e(TAG, "exiting pullStats: on error", e);
                return 0;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            Slog.e(TAG, "exiting pullStats: bad request");
            return 0;
        }
    };

    @VisibleForTesting
    protected void setNotificationAssistantAccessGrantedForUserInternal(
            ComponentName assistant, int baseUserId, boolean granted) {
        List<UserInfo> users = mUm.getEnabledProfiles(baseUserId);
        if (users != null) {
            for (UserInfo user : users) {
                int userId = user.id;
                if (assistant == null) {
                    ComponentName allowedAssistant = CollectionUtils.firstOrNull(
                            mAssistants.getAllowedComponents(userId));
                    if (allowedAssistant != null) {
                        setNotificationAssistantAccessGrantedForUserInternal(
                                allowedAssistant, userId, false);
                    }
                    continue;
                }
                if (!granted || mAllowedManagedServicePackages.test(assistant.getPackageName(),
                        userId, mAssistants.getRequiredPermission())) {
                    mConditionProviders.setPackageOrComponentEnabled(assistant.flattenToString(),
                            userId, false, granted);
                    mAssistants.setPackageOrComponentEnabled(assistant.flattenToString(),
                            userId, true, granted);

                    getContext().sendBroadcastAsUser(
                            new Intent(ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
                                    .setPackage(assistant.getPackageName())
                                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
                            UserHandle.of(userId), null);

                    handleSavePolicyFile();
                }
            }
        }
    }

    private void applyAdjustment(NotificationRecord r, Adjustment adjustment) {
        if (r == null) {
            return;
        }
        if (adjustment.getSignals() != null) {
            final Bundle adjustments = adjustment.getSignals();
            Bundle.setDefusable(adjustments, true);
            List<String> toRemove = new ArrayList<>();
            for (String potentialKey : adjustments.keySet()) {
                if (!mAssistants.isAdjustmentAllowed(potentialKey)) {
                    toRemove.add(potentialKey);
                }
            }
            for (String removeKey : toRemove) {
                adjustments.remove(removeKey);
            }
            r.addAdjustment(adjustment);
        }
    }

    @GuardedBy("mNotificationLock")
    void addAutogroupKeyLocked(String key) {
        NotificationRecord r = mNotificationsByKey.get(key);
        if (r == null) {
            return;
        }
        if (r.sbn.getOverrideGroupKey() == null) {
            addAutoGroupAdjustment(r, GroupHelper.AUTOGROUP_KEY);
            EventLogTags.writeNotificationAutogrouped(key);
            mRankingHandler.requestSort();
        }
    }

    @GuardedBy("mNotificationLock")
    void removeAutogroupKeyLocked(String key) {
        NotificationRecord r = mNotificationsByKey.get(key);
        if (r == null) {
            return;
        }
        if (r.sbn.getOverrideGroupKey() != null) {
            addAutoGroupAdjustment(r, null);
            EventLogTags.writeNotificationUnautogrouped(key);
            mRankingHandler.requestSort();
        }
    }

    private void addAutoGroupAdjustment(NotificationRecord r, String overrideGroupKey) {
        Bundle signals = new Bundle();
        signals.putString(Adjustment.KEY_GROUP_KEY, overrideGroupKey);
        Adjustment adjustment =
                new Adjustment(r.sbn.getPackageName(), r.getKey(), signals, "", r.sbn.getUserId());
        r.addAdjustment(adjustment);
    }

    // Clears the 'fake' auto-group summary.
    @GuardedBy("mNotificationLock")
    private void clearAutogroupSummaryLocked(int userId, String pkg) {
        ArrayMap<String, String> summaries = mAutobundledSummaries.get(userId);
        if (summaries != null && summaries.containsKey(pkg)) {
            // Clear summary.
            final NotificationRecord removed = findNotificationByKeyLocked(summaries.remove(pkg));
            if (removed != null) {
                boolean wasPosted = removeFromNotificationListsLocked(removed);
                cancelNotificationLocked(removed, false, REASON_UNAUTOBUNDLED, wasPosted, null);
            }
        }
    }

    @GuardedBy("mNotificationLock")
    private boolean hasAutoGroupSummaryLocked(StatusBarNotification sbn) {
        ArrayMap<String, String> summaries = mAutobundledSummaries.get(sbn.getUserId());
        return summaries != null && summaries.containsKey(sbn.getPackageName());
    }

    // Posts a 'fake' summary for a package that has exceeded the solo-notification limit.
    private void createAutoGroupSummary(int userId, String pkg, String triggeringKey) {
        NotificationRecord summaryRecord = null;
        synchronized (mNotificationLock) {
            NotificationRecord notificationRecord = mNotificationsByKey.get(triggeringKey);
            if (notificationRecord == null) {
                // The notification could have been cancelled again already. A successive
                // adjustment will post a summary if needed.
                return;
            }
            final StatusBarNotification adjustedSbn = notificationRecord.sbn;
            userId = adjustedSbn.getUser().getIdentifier();
            ArrayMap<String, String> summaries = mAutobundledSummaries.get(userId);
            if (summaries == null) {
                summaries = new ArrayMap<>();
            }
            mAutobundledSummaries.put(userId, summaries);
            if (!summaries.containsKey(pkg)) {
                // Add summary
                final ApplicationInfo appInfo =
                       adjustedSbn.getNotification().extras.getParcelable(
                               Notification.EXTRA_BUILDER_APPLICATION_INFO);
                final Bundle extras = new Bundle();
                extras.putParcelable(Notification.EXTRA_BUILDER_APPLICATION_INFO, appInfo);
                final String channelId = notificationRecord.getChannel().getId();
                final Notification summaryNotification =
                        new Notification.Builder(getContext(), channelId)
                                .setSmallIcon(adjustedSbn.getNotification().getSmallIcon())
                                .setGroupSummary(true)
                                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                                .setGroup(GroupHelper.AUTOGROUP_KEY)
                                .setFlag(FLAG_AUTOGROUP_SUMMARY, true)
                                .setFlag(Notification.FLAG_GROUP_SUMMARY, true)
                                .setColor(adjustedSbn.getNotification().color)
                                .setLocalOnly(true)
                                .build();
                summaryNotification.extras.putAll(extras);
                Intent appIntent = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
                if (appIntent != null) {
                    summaryNotification.contentIntent = PendingIntent.getActivityAsUser(
                            getContext(), 0, appIntent, 0, null, UserHandle.of(userId));
                }
                final StatusBarNotification summarySbn =
                        new StatusBarNotification(adjustedSbn.getPackageName(),
                                adjustedSbn.getOpPkg(),
                                Integer.MAX_VALUE,
                                GroupHelper.AUTOGROUP_KEY, adjustedSbn.getUid(),
                                adjustedSbn.getInitialPid(), summaryNotification,
                                adjustedSbn.getUser(), GroupHelper.AUTOGROUP_KEY,
                                System.currentTimeMillis());
                summaryRecord = new NotificationRecord(getContext(), summarySbn,
                        notificationRecord.getChannel());
                summaryRecord.setIsAppImportanceLocked(
                        notificationRecord.getIsAppImportanceLocked());
                summaries.put(pkg, summarySbn.getKey());
            }
        }
        if (summaryRecord != null && checkDisqualifyingFeatures(userId, MY_UID,
                summaryRecord.sbn.getId(), summaryRecord.sbn.getTag(), summaryRecord, true)) {
            mHandler.post(new EnqueueNotificationRunnable(userId, summaryRecord));
        }
    }

    private String disableNotificationEffects(NotificationRecord record) {
        if (mDisableNotificationEffects) {
            return "booleanState";
        }
        if ((mListenerHints & HINT_HOST_DISABLE_EFFECTS) != 0) {
            return "listenerHints";
        }
        if (record != null && record.getAudioAttributes() != null) {
            if ((mListenerHints & HINT_HOST_DISABLE_NOTIFICATION_EFFECTS) != 0) {
                if (record.getAudioAttributes().getUsage()
                        != AudioAttributes.USAGE_VOICE_COMMUNICATION) {
                    return "listenerNoti";
                }
            }
            if ((mListenerHints & HINT_HOST_DISABLE_CALL_EFFECTS) != 0) {
                if (record.getAudioAttributes().getUsage()
                        == AudioAttributes.USAGE_VOICE_COMMUNICATION) {
                    return "listenerCall";
                }
            }
        }
        if (mCallState != TelephonyManager.CALL_STATE_IDLE && !mZenModeHelper.isCall(record)) {
            return "callState";
        }
        return null;
    };

    private void dumpJson(PrintWriter pw, @NonNull DumpFilter filter) {
        JSONObject dump = new JSONObject();
        try {
            dump.put("service", "Notification Manager");
            dump.put("bans", mPreferencesHelper.dumpBansJson(filter));
            dump.put("ranking", mPreferencesHelper.dumpJson(filter));
            dump.put("stats", mUsageStats.dumpJson(filter));
            dump.put("channels", mPreferencesHelper.dumpChannelsJson(filter));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        pw.println(dump);
    }

    private void dumpRemoteViewStats(PrintWriter pw, @NonNull DumpFilter filter) {
        PulledStats stats = mUsageStats.remoteViewStats(filter.since, true);
        if (stats == null) {
            pw.println("no remote view stats reported.");
            return;
        }
        stats.dump(REPORT_REMOTE_VIEWS, pw, filter);
    }

    private void dumpProto(FileDescriptor fd, @NonNull DumpFilter filter) {
        final ProtoOutputStream proto = new ProtoOutputStream(fd);
        synchronized (mNotificationLock) {
            int N = mNotificationList.size();
            for (int i = 0; i < N; i++) {
                final NotificationRecord nr = mNotificationList.get(i);
                if (filter.filtered && !filter.matches(nr.sbn)) continue;
                nr.dump(proto, NotificationServiceDumpProto.RECORDS, filter.redact,
                        NotificationRecordProto.POSTED);
            }
            N = mEnqueuedNotifications.size();
            for (int i = 0; i < N; i++) {
                final NotificationRecord nr = mEnqueuedNotifications.get(i);
                if (filter.filtered && !filter.matches(nr.sbn)) continue;
                nr.dump(proto, NotificationServiceDumpProto.RECORDS, filter.redact,
                        NotificationRecordProto.ENQUEUED);
            }
            List<NotificationRecord> snoozed = mSnoozeHelper.getSnoozed();
            N = snoozed.size();
            for (int i = 0; i < N; i++) {
                final NotificationRecord nr = snoozed.get(i);
                if (filter.filtered && !filter.matches(nr.sbn)) continue;
                nr.dump(proto, NotificationServiceDumpProto.RECORDS, filter.redact,
                        NotificationRecordProto.SNOOZED);
            }

            long zenLog = proto.start(NotificationServiceDumpProto.ZEN);
            mZenModeHelper.dump(proto);
            for (ComponentName suppressor : mEffectsSuppressors) {
                suppressor.writeToProto(proto, ZenModeProto.SUPPRESSORS);
            }
            proto.end(zenLog);

            long listenersToken = proto.start(NotificationServiceDumpProto.NOTIFICATION_LISTENERS);
            mListeners.dump(proto, filter);
            proto.end(listenersToken);

            proto.write(NotificationServiceDumpProto.LISTENER_HINTS, mListenerHints);

            for (int i = 0; i < mListenersDisablingEffects.size(); ++i) {
                long effectsToken = proto.start(
                    NotificationServiceDumpProto.LISTENERS_DISABLING_EFFECTS);

                proto.write(
                    ListenersDisablingEffectsProto.HINT, mListenersDisablingEffects.keyAt(i));
                final ArraySet<ComponentName> listeners =
                    mListenersDisablingEffects.valueAt(i);
                for (int j = 0; j < listeners.size(); j++) {
                    final ComponentName componentName = listeners.valueAt(j);
                    componentName.writeToProto(proto,
                            ListenersDisablingEffectsProto.LISTENER_COMPONENTS);
                }

                proto.end(effectsToken);
            }

            long assistantsToken = proto.start(
                NotificationServiceDumpProto.NOTIFICATION_ASSISTANTS);
            mAssistants.dump(proto, filter);
            proto.end(assistantsToken);

            long conditionsToken = proto.start(NotificationServiceDumpProto.CONDITION_PROVIDERS);
            mConditionProviders.dump(proto, filter);
            proto.end(conditionsToken);

            long rankingToken = proto.start(NotificationServiceDumpProto.RANKING_CONFIG);
            mRankingHelper.dump(proto, filter);
            mPreferencesHelper.dump(proto, filter);
            proto.end(rankingToken);
        }

        proto.flush();
    }

    private void dumpNotificationRecords(PrintWriter pw, @NonNull DumpFilter filter) {
        synchronized (mNotificationLock) {
            int N;
            N = mNotificationList.size();
            if (N > 0) {
                pw.println("  Notification List:");
                for (int i = 0; i < N; i++) {
                    final NotificationRecord nr = mNotificationList.get(i);
                    if (filter.filtered && !filter.matches(nr.sbn)) continue;
                    nr.dump(pw, "    ", getContext(), filter.redact);
                }
                pw.println("  ");
            }
        }
    }

    void dumpImpl(PrintWriter pw, @NonNull DumpFilter filter) {
        pw.print("Current Notification Manager state");
        if (filter.filtered) {
            pw.print(" (filtered to "); pw.print(filter); pw.print(")");
        }
        pw.println(':');
        int N;
        final boolean zenOnly = filter.filtered && filter.zen;

        if (!zenOnly) {
            synchronized (mToastQueue) {
                N = mToastQueue.size();
                if (N > 0) {
                    pw.println("  Toast Queue:");
                    for (int i=0; i<N; i++) {
                        mToastQueue.get(i).dump(pw, "    ", filter);
                    }
                    pw.println("  ");
                }
            }
        }

        synchronized (mNotificationLock) {
            if (!zenOnly) {
                // Priority filters are only set when called via bugreport. If set
                // skip sections that are part of the critical section.
                if (!filter.normalPriority) {
                    dumpNotificationRecords(pw, filter);
                }
                if (!filter.filtered) {
                    N = mLights.size();
                    if (N > 0) {
                        pw.println("  Lights List:");
                        for (int i=0; i<N; i++) {
                            if (i == N - 1) {
                                pw.print("  > ");
                            } else {
                                pw.print("    ");
                            }
                            pw.println(mLights.get(i));
                        }
                        pw.println("  ");
                    }
                    pw.println("  mUseAttentionLight=" + mUseAttentionLight);
                    pw.println("  mHasLight=" + mHasLight);
                    pw.println("  mNotificationPulseEnabled=" + mNotificationPulseEnabled);
                    pw.println("  mSoundNotificationKey=" + mSoundNotificationKey);
                    pw.println("  mVibrateNotificationKey=" + mVibrateNotificationKey);
                    pw.println("  mDisableNotificationEffects=" + mDisableNotificationEffects);
                    pw.println("  mCallState=" + callStateToString(mCallState));
                    pw.println("  mSystemReady=" + mSystemReady);
                    pw.println("  mMaxPackageEnqueueRate=" + mMaxPackageEnqueueRate);
                }
                pw.println("  mArchive=" + mArchive.toString());
                Iterator<StatusBarNotification> iter = mArchive.descendingIterator();
                int j=0;
                while (iter.hasNext()) {
                    final StatusBarNotification sbn = iter.next();
                    if (filter != null && !filter.matches(sbn)) continue;
                    pw.println("    " + sbn);
                    if (++j >= 5) {
                        if (iter.hasNext()) pw.println("    ...");
                        break;
                    }
                }

                if (!zenOnly) {
                    N = mEnqueuedNotifications.size();
                    if (N > 0) {
                        pw.println("  Enqueued Notification List:");
                        for (int i = 0; i < N; i++) {
                            final NotificationRecord nr = mEnqueuedNotifications.get(i);
                            if (filter.filtered && !filter.matches(nr.sbn)) continue;
                            nr.dump(pw, "    ", getContext(), filter.redact);
                        }
                        pw.println("  ");
                    }

                    mSnoozeHelper.dump(pw, filter);
                }
            }

            if (!zenOnly) {
                pw.println("\n  Ranking Config:");
                mRankingHelper.dump(pw, "    ", filter);

                pw.println("\n Notification Preferences:");
                mPreferencesHelper.dump(pw, "    ", filter);

                pw.println("\n  Notification listeners:");
                mListeners.dump(pw, filter);
                pw.print("    mListenerHints: "); pw.println(mListenerHints);
                pw.print("    mListenersDisablingEffects: (");
                N = mListenersDisablingEffects.size();
                for (int i = 0; i < N; i++) {
                    final int hint = mListenersDisablingEffects.keyAt(i);
                    if (i > 0) pw.print(';');
                    pw.print("hint[" + hint + "]:");

                    final ArraySet<ComponentName> listeners = mListenersDisablingEffects.valueAt(i);
                    final int listenerSize = listeners.size();

                    for (int j = 0; j < listenerSize; j++) {
                        if (j > 0) pw.print(',');
                        final ComponentName listener = listeners.valueAt(j);
                        if (listener != null) {
                            pw.print(listener);
                        }
                    }
                }
                pw.println(')');
                pw.println("\n  Notification assistant services:");
                mAssistants.dump(pw, filter);
            }

            if (!filter.filtered || zenOnly) {
                pw.println("\n  Zen Mode:");
                pw.print("    mInterruptionFilter="); pw.println(mInterruptionFilter);
                mZenModeHelper.dump(pw, "    ");

                pw.println("\n  Zen Log:");
                ZenLog.dump(pw, "    ");
            }

            pw.println("\n  Condition providers:");
            mConditionProviders.dump(pw, filter);

            pw.println("\n  Group summaries:");
            for (Entry<String, NotificationRecord> entry : mSummaryByGroupKey.entrySet()) {
                NotificationRecord r = entry.getValue();
                pw.println("    " + entry.getKey() + " -> " + r.getKey());
                if (mNotificationsByKey.get(r.getKey()) != r) {
                    pw.println("!!!!!!LEAK: Record not found in mNotificationsByKey.");
                    r.dump(pw, "      ", getContext(), filter.redact);
                }
            }

            if (!zenOnly) {
                pw.println("\n  Usage Stats:");
                mUsageStats.dump(pw, "    ", filter);
            }

            long now = SystemClock.elapsedRealtime();
            pw.println("\n  Last notification sound timestamps:");
            for (Map.Entry<String, Long> entry : mLastSoundTimestamps.entrySet()) {
                pw.print("    " + entry.getKey() + " -> ");
                TimeUtils.formatDuration(entry.getValue(), now, pw);
                pw.println(" ago");
            }
        }
    }

    /**
     * The private API only accessible to the system process.
     */
    private final NotificationManagerInternal mInternalService = new NotificationManagerInternal() {
        @Override
        public NotificationChannel getNotificationChannel(String pkg, int uid, String
                channelId) {
            return mPreferencesHelper.getNotificationChannel(pkg, uid, channelId, false);
        }

        @Override
        public void enqueueNotification(String pkg, String opPkg, int callingUid, int callingPid,
                String tag, int id, Notification notification, int userId) {
            enqueueNotificationInternal(pkg, opPkg, callingUid, callingPid, tag, id, notification,
                    userId);
        }

        @Override
        public void removeForegroundServiceFlagFromNotification(String pkg, int notificationId,
                int userId) {
            checkCallerIsSystem();
            mHandler.post(() -> {
                synchronized (mNotificationLock) {
                    // strip flag from all enqueued notifications. listeners will be informed
                    // in post runnable.
                    List<NotificationRecord> enqueued = findNotificationsByListLocked(
                            mEnqueuedNotifications, pkg, null, notificationId, userId);
                    for (int i = 0; i < enqueued.size(); i++) {
                        removeForegroundServiceFlagLocked(enqueued.get(i));
                    }

                    // if posted notification exists, strip its flag and tell listeners
                    NotificationRecord r = findNotificationByListLocked(
                            mNotificationList, pkg, null, notificationId, userId);
                    if (r != null) {
                        removeForegroundServiceFlagLocked(r);
                        mRankingHelper.sort(mNotificationList);
                        mListeners.notifyPostedLocked(r, r);
                    }
                }
            });
        }

        @GuardedBy("mNotificationLock")
        private void removeForegroundServiceFlagLocked(NotificationRecord r) {
            if (r == null) {
                return;
            }
            StatusBarNotification sbn = r.sbn;
            // NoMan adds flags FLAG_NO_CLEAR and FLAG_ONGOING_EVENT when it sees
            // FLAG_FOREGROUND_SERVICE. Hence it's not enough to remove
            // FLAG_FOREGROUND_SERVICE, we have to revert to the flags we received
            // initially *and* force remove FLAG_FOREGROUND_SERVICE.
            sbn.getNotification().flags =
                    (r.mOriginalFlags & ~FLAG_FOREGROUND_SERVICE);
        }
    };

    void enqueueNotificationInternal(final String pkg, final String opPkg, final int callingUid,
            final int callingPid, final String tag, final int id, final Notification notification,
            int incomingUserId) {
        if (DBG) {
            Slog.v(TAG, "enqueueNotificationInternal: pkg=" + pkg + " id=" + id
                    + " notification=" + notification);
        }

        if (pkg == null || notification == null) {
            throw new IllegalArgumentException("null not allowed: pkg=" + pkg
                    + " id=" + id + " notification=" + notification);
        }

        final int userId = ActivityManager.handleIncomingUser(callingPid,
                callingUid, incomingUserId, true, false, "enqueueNotification", pkg);
        final UserHandle user = UserHandle.of(userId);

        // Can throw a SecurityException if the calling uid doesn't have permission to post
        // as "pkg"
        final int notificationUid = resolveNotificationUid(opPkg, pkg, callingUid, userId);

        checkRestrictedCategories(notification);

        // Fix the notification as best we can.
        try {
            fixNotification(notification, pkg, userId);

        } catch (NameNotFoundException e) {
            Slog.e(TAG, "Cannot create a context for sending app", e);
            return;
        }

        mUsageStats.registerEnqueuedByApp(pkg);

        // setup local book-keeping
        String channelId = notification.getChannelId();
        if (mIsTelevision && (new Notification.TvExtender(notification)).getChannelId() != null) {
            channelId = (new Notification.TvExtender(notification)).getChannelId();
        }
        final NotificationChannel channel = mPreferencesHelper.getNotificationChannel(pkg,
                notificationUid, channelId, false /* includeDeleted */);
        if (channel == null) {
            boolean appNotificationsOff = mPreferencesHelper.getImportance(pkg, notificationUid)
                    == NotificationManager.IMPORTANCE_NONE;

            if (!appNotificationsOff) {
                doChannelWarningToast("Developer warning for package \"" + pkg + "\"\n" +
                        "Failed to post notification on channel \"" + channelId + "\"\n" +
                        "See log for more details");
            }
            return;
        }

        final StatusBarNotification n = new StatusBarNotification(
                pkg, opPkg, id, tag, notificationUid, callingPid, notification,
                user, null, System.currentTimeMillis());
        final NotificationRecord r = new NotificationRecord(getContext(), n, channel);
        r.setIsAppImportanceLocked(mPreferencesHelper.getIsAppImportanceLocked(pkg, callingUid));

        if ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            final boolean fgServiceShown = channel.isFgServiceShown();
            if (((channel.getUserLockedFields() & NotificationChannel.USER_LOCKED_IMPORTANCE) == 0
                        || !fgServiceShown)
                    && (r.getImportance() == IMPORTANCE_MIN
                            || r.getImportance() == IMPORTANCE_NONE)) {
                // Increase the importance of foreground service notifications unless the user had
                // an opinion otherwise (and the channel hasn't yet shown a fg service).
                if (TextUtils.isEmpty(channelId)
                        || NotificationChannel.DEFAULT_CHANNEL_ID.equals(channelId)) {
                    r.setSystemImportance(IMPORTANCE_LOW);
                } else {
                    channel.setImportance(IMPORTANCE_LOW);
                    r.setSystemImportance(IMPORTANCE_LOW);
                    if (!fgServiceShown) {
                        channel.unlockFields(NotificationChannel.USER_LOCKED_IMPORTANCE);
                        channel.setFgServiceShown(true);
                    }
                    mPreferencesHelper.updateNotificationChannel(
                            pkg, notificationUid, channel, false);
                    r.updateNotificationChannel(channel);
                }
            } else if (!fgServiceShown && !TextUtils.isEmpty(channelId)
                    && !NotificationChannel.DEFAULT_CHANNEL_ID.equals(channelId)) {
                channel.setFgServiceShown(true);
                r.updateNotificationChannel(channel);
            }
        }

        if (!checkDisqualifyingFeatures(userId, notificationUid, id, tag, r,
                r.sbn.getOverrideGroupKey() != null)) {
            return;
        }

        // Whitelist pending intents.
        if (notification.allPendingIntents != null) {
            final int intentCount = notification.allPendingIntents.size();
            if (intentCount > 0) {
                final ActivityManagerInternal am = LocalServices
                        .getService(ActivityManagerInternal.class);
                final long duration = LocalServices.getService(
                        DeviceIdleController.LocalService.class).getNotificationWhitelistDuration();
                for (int i = 0; i < intentCount; i++) {
                    PendingIntent pendingIntent = notification.allPendingIntents.valueAt(i);
                    if (pendingIntent != null) {
                        am.setPendingIntentWhitelistDuration(pendingIntent.getTarget(),
                                WHITELIST_TOKEN, duration);
                        am.setPendingIntentAllowBgActivityStarts(pendingIntent.getTarget(),
                                WHITELIST_TOKEN, (FLAG_ACTIVITY_SENDER | FLAG_BROADCAST_SENDER
                                        | FLAG_SERVICE_SENDER));
                    }
                }
            }
        }

        mHandler.post(new EnqueueNotificationRunnable(userId, r));
    }

    @VisibleForTesting
    protected void fixNotification(Notification notification, String pkg, int userId)
            throws NameNotFoundException {
        final ApplicationInfo ai = mPackageManagerClient.getApplicationInfoAsUser(
                pkg, PackageManager.MATCH_DEBUG_TRIAGED_MISSING,
                (userId == UserHandle.USER_ALL) ? USER_SYSTEM : userId);
        Notification.addFieldsFromContext(ai, notification);

        int canColorize = mPackageManagerClient.checkPermission(
                android.Manifest.permission.USE_COLORIZED_NOTIFICATIONS, pkg);
        if (canColorize == PERMISSION_GRANTED) {
            notification.flags |= Notification.FLAG_CAN_COLORIZE;
        } else {
            notification.flags &= ~Notification.FLAG_CAN_COLORIZE;
        }

        if (notification.fullScreenIntent != null && ai.targetSdkVersion >= Build.VERSION_CODES.Q) {
            int fullscreenIntentPermission = mPackageManagerClient.checkPermission(
                    android.Manifest.permission.USE_FULL_SCREEN_INTENT, pkg);
            if (fullscreenIntentPermission != PERMISSION_GRANTED) {
                notification.fullScreenIntent = null;
                Slog.w(TAG, "Package " + pkg +
                        ": Use of fullScreenIntent requires the USE_FULL_SCREEN_INTENT permission");
            }
        }
    }

    /**
     * Updates the flags for this notification to reflect whether it is a bubble or not.
     */
    private void flagNotificationForBubbles(NotificationRecord r, String pkg, int userId,
            NotificationRecord oldRecord) {
        Notification notification = r.getNotification();
        if (isNotificationAppropriateToBubble(r, pkg, userId, oldRecord)) {
            notification.flags |= FLAG_BUBBLE;
        } else {
            notification.flags &= ~FLAG_BUBBLE;
        }
        // Is the app in the foreground?
        final boolean appIsForeground =
                mActivityManager.getPackageImportance(pkg) == IMPORTANCE_FOREGROUND;
        Notification.BubbleMetadata metadata = notification.getBubbleMetadata();
        if (!appIsForeground && metadata != null) {
            // Remove any flags that only work when foregrounded
            int flags = metadata.getFlags();
            flags &= ~Notification.BubbleMetadata.FLAG_AUTO_EXPAND_BUBBLE;
            flags &= ~Notification.BubbleMetadata.FLAG_SUPPRESS_NOTIFICATION;
            metadata.setFlags(flags);
        }
    }

    /**
     * @return whether the provided notification record is allowed to be represented as a bubble,
     * accounting for user choice & policy.
     */
    private boolean isNotificationAppropriateToBubble(NotificationRecord r, String pkg, int userId,
            NotificationRecord oldRecord) {
        Notification notification = r.getNotification();
        if (!canBubble(r, pkg, userId)) {
            // no log: canBubble has its own
            return false;
        }

        if (mActivityManager.isLowRamDevice()) {
            logBubbleError(r.getKey(), "low ram device");
            return false;
        }

        if (mActivityManager.getPackageImportance(pkg) == IMPORTANCE_FOREGROUND) {
            // If the app is foreground it always gets to bubble
            return true;
        }

        if (oldRecord != null && (oldRecord.getNotification().flags & FLAG_BUBBLE) != 0) {
            // This is an update to an active bubble
            return true;
        }

        // At this point the bubble must fulfill communication policy

        // Communication always needs a person
        ArrayList<Person> peopleList = notification.extras != null
                ? notification.extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST)
                : null;
        // Message style requires a person & it's not included in the list
        boolean isMessageStyle = Notification.MessagingStyle.class.equals(
                notification.getNotificationStyle());
        if (!isMessageStyle && (peopleList == null || peopleList.isEmpty())) {
            logBubbleError(r.getKey(), "if not foreground, must have a person and be "
                    + "Notification.MessageStyle or Notification.CATEGORY_CALL");
            return false;
        }

        // Communication is a message or a call
        boolean isCall = CATEGORY_CALL.equals(notification.category);
        boolean hasForegroundService = (notification.flags & FLAG_FOREGROUND_SERVICE) != 0;
        if (isMessageStyle) {
            if (hasValidRemoteInput(notification)) {
                return true;
            }
            logBubbleError(r.getKey(), "messages require valid remote input");
            return false;
        } else if (isCall) {
            if (hasForegroundService) {
                return true;
            }
            logBubbleError(r.getKey(), "calls require foreground service");
            return false;
        }
        logBubbleError(r.getKey(), "if not foreground, must be "
                + "Notification.MessageStyle or Notification.CATEGORY_CALL");
        return false;
    }

    /**
     * @return whether the user has enabled the provided notification to bubble, does not account
     * for policy.
     */
    private boolean canBubble(NotificationRecord r, String pkg, int userId) {
        Notification notification = r.getNotification();
        Notification.BubbleMetadata metadata = notification.getBubbleMetadata();
        if (metadata == null) {
            // no log: no need to inform dev if they didn't attach bubble metadata
            return false;
        }
        if (!canLaunchInActivityView(getContext(), metadata.getIntent(), pkg)) {
            // no log: method has the failure log
            return false;
        }
        if (!mPreferencesHelper.bubblesEnabled()) {
            logBubbleError(r.getKey(), "bubbles disabled for user: " + userId);
            return false;
        }
        if (!mPreferencesHelper.areBubblesAllowed(pkg, userId)) {
            logBubbleError(r.getKey(),
                    "bubbles for package: " + pkg + " disabled for user: " + userId);
            return false;
        }
        if (!r.getChannel().canBubble()) {
            logBubbleError(r.getKey(),
                    "bubbles for channel " + r.getChannel().getId() + " disabled");
            return false;
        }
        return true;
    }

    private boolean hasValidRemoteInput(Notification n) {
        // Also check for inline reply
        Notification.Action[] actions = n.actions;
        if (actions != null) {
            // Get the remote inputs
            for (int i = 0; i < actions.length; i++) {
                Notification.Action action = actions[i];
                RemoteInput[] inputs = action.getRemoteInputs();
                if (inputs != null && inputs.length > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void logBubbleError(String key, String failureMessage) {
        if (DBG) {
            Log.w(TAG, "Bubble notification: " + key + " failed: " + failureMessage);
        }
    }
    /**
     * Whether an intent is properly configured to display in an {@link android.app.ActivityView}.
     *
     * @param context       the context to use.
     * @param pendingIntent the pending intent of the bubble.
     * @param packageName   the notification package name for this bubble.
     */
    // Keep checks in sync with BubbleController#canLaunchInActivityView.
    @VisibleForTesting
    protected boolean canLaunchInActivityView(Context context, PendingIntent pendingIntent,
            String packageName) {
        if (pendingIntent == null) {
            Log.w(TAG, "Unable to create bubble -- no intent");
            return false;
        }

        // Need escalated privileges to get the intent.
        final long token = Binder.clearCallingIdentity();
        Intent intent;
        try {
            intent = pendingIntent.getIntent();
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        ActivityInfo info = intent != null
                ? intent.resolveActivityInfo(context.getPackageManager(), 0)
                : null;
        if (info == null) {
            StatsLog.write(StatsLog.BUBBLE_DEVELOPER_ERROR_REPORTED, packageName,
                    BUBBLE_DEVELOPER_ERROR_REPORTED__ERROR__ACTIVITY_INFO_MISSING);
            Log.w(TAG, "Unable to send as bubble -- couldn't find activity info for intent: "
                    + intent);
            return false;
        }
        if (!ActivityInfo.isResizeableMode(info.resizeMode)) {
            StatsLog.write(StatsLog.BUBBLE_DEVELOPER_ERROR_REPORTED, packageName,
                    BUBBLE_DEVELOPER_ERROR_REPORTED__ERROR__ACTIVITY_INFO_NOT_RESIZABLE);
            Log.w(TAG, "Unable to send as bubble -- activity is not resizable for intent: "
                    + intent);
            return false;
        }
        if (info.documentLaunchMode != DOCUMENT_LAUNCH_ALWAYS) {
            StatsLog.write(StatsLog.BUBBLE_DEVELOPER_ERROR_REPORTED, packageName,
                    BUBBLE_DEVELOPER_ERROR_REPORTED__ERROR__DOCUMENT_LAUNCH_NOT_ALWAYS);
            Log.w(TAG, "Unable to send as bubble -- activity is not documentLaunchMode=always "
                    + "for intent: " + intent);
            return false;
        }
        if ((info.flags & ActivityInfo.FLAG_ALLOW_EMBEDDED) == 0) {
            Log.w(TAG, "Unable to send as bubble -- activity is not embeddable for intent: "
                    + intent);
            return false;
        }
        return true;
    }

    private void doChannelWarningToast(CharSequence toastText) {
        Binder.withCleanCallingIdentity(() -> {
            final int defaultWarningEnabled = Build.IS_ENG ? 1 : 0;
            final boolean warningEnabled = Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.SHOW_NOTIFICATION_CHANNEL_WARNINGS, defaultWarningEnabled) != 0;
            if (warningEnabled) {
                Toast toast = Toast.makeText(getContext(), mHandler.getLooper(), toastText,
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    @VisibleForTesting
    int resolveNotificationUid(String callingPkg, String targetPkg,
            int callingUid, int userId) {
        if (userId == UserHandle.USER_ALL) {
            userId = USER_SYSTEM;
        }
        // posted from app A on behalf of app A
        if (isCallerSameApp(targetPkg, callingUid, userId)
                && (TextUtils.equals(callingPkg, targetPkg)
                || isCallerSameApp(callingPkg, callingUid, userId))) {
            return callingUid;
        }

        int targetUid = -1;
        try {
            targetUid = mPackageManagerClient.getPackageUidAsUser(targetPkg, userId);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        // posted from app A on behalf of app B
        if (targetUid != -1 && (isCallerAndroid(callingPkg, callingUid)
                || mPreferencesHelper.isDelegateAllowed(
                        targetPkg, targetUid, callingPkg, callingUid))) {
            return targetUid;
        }

        throw new SecurityException("Caller " + callingPkg + ":" + callingUid
                + " cannot post for pkg " + targetPkg + " in user " + userId);
    }

    /**
     * Checks if a notification can be posted. checks rate limiter, snooze helper, and blocking.
     *
     * Has side effects.
     */
    private boolean checkDisqualifyingFeatures(int userId, int uid, int id, String tag,
            NotificationRecord r, boolean isAutogroup) {
        final String pkg = r.sbn.getPackageName();
        final boolean isSystemNotification =
                isUidSystemOrPhone(uid) || ("android".equals(pkg));
        final boolean isNotificationFromListener = mListeners.isListenerPackage(pkg);

        // Limit the number of notifications that any given package except the android
        // package or a registered listener can enqueue.  Prevents DOS attacks and deals with leaks.
        if (!isSystemNotification && !isNotificationFromListener) {
            synchronized (mNotificationLock) {
                final int callingUid = Binder.getCallingUid();
                if (mNotificationsByKey.get(r.sbn.getKey()) == null
                        && isCallerInstantApp(callingUid, userId)) {
                    // Ephemeral apps have some special constraints for notifications.
                    // They are not allowed to create new notifications however they are allowed to
                    // update notifications created by the system (e.g. a foreground service
                    // notification).
                    throw new SecurityException("Instant app " + pkg
                            + " cannot create notifications");
                }

                // rate limit updates that aren't completed progress notifications
                if (mNotificationsByKey.get(r.sbn.getKey()) != null
                        && !r.getNotification().hasCompletedProgress()
                        && !isAutogroup) {

                    final float appEnqueueRate = mUsageStats.getAppEnqueueRate(pkg);
                    if (appEnqueueRate > mMaxPackageEnqueueRate) {
                        mUsageStats.registerOverRateQuota(pkg);
                        final long now = SystemClock.elapsedRealtime();
                        if ((now - mLastOverRateLogTime) > MIN_PACKAGE_OVERRATE_LOG_INTERVAL) {
                            Slog.e(TAG, "Package enqueue rate is " + appEnqueueRate
                                    + ". Shedding " + r.sbn.getKey() + ". package=" + pkg);
                            mLastOverRateLogTime = now;
                        }
                        return false;
                    }
                }

                // limit the number of outstanding notificationrecords an app can have
                int count = getNotificationCountLocked(pkg, userId, id, tag);
                if (count >= MAX_PACKAGE_NOTIFICATIONS) {
                    mUsageStats.registerOverCountQuota(pkg);
                    Slog.e(TAG, "Package has already posted or enqueued " + count
                            + " notifications.  Not showing more.  package=" + pkg);
                    return false;
                }
            }
        }

        // snoozed apps
        if (mSnoozeHelper.isSnoozed(userId, pkg, r.getKey())) {
            MetricsLogger.action(r.getLogMaker()
                    .setType(MetricsProto.MetricsEvent.TYPE_UPDATE)
                    .setCategory(MetricsProto.MetricsEvent.NOTIFICATION_SNOOZED));
            if (DBG) {
                Slog.d(TAG, "Ignored enqueue for snoozed notification " + r.getKey());
            }
            mSnoozeHelper.update(userId, r);
            handleSavePolicyFile();
            return false;
        }


        // blocked apps
        if (isBlocked(r, mUsageStats)) {
            return false;
        }

        return true;
    }

    @GuardedBy("mNotificationLock")
    protected int getNotificationCountLocked(String pkg, int userId, int excludedId,
            String excludedTag) {
        int count = 0;
        final int N = mNotificationList.size();
        for (int i = 0; i < N; i++) {
            final NotificationRecord existing = mNotificationList.get(i);
            if (existing.sbn.getPackageName().equals(pkg)
                    && existing.sbn.getUserId() == userId) {
                if (existing.sbn.getId() == excludedId
                        && TextUtils.equals(existing.sbn.getTag(), excludedTag)) {
                    continue;
                }
                count++;
            }
        }
        final int M = mEnqueuedNotifications.size();
        for (int i = 0; i < M; i++) {
            final NotificationRecord existing = mEnqueuedNotifications.get(i);
            if (existing.sbn.getPackageName().equals(pkg)
                    && existing.sbn.getUserId() == userId) {
                count++;
            }
        }
        return count;
    }

    protected boolean isBlocked(NotificationRecord r, NotificationUsageStats usageStats) {
        if (isBlocked(r)) {
            Slog.e(TAG, "Suppressing notification from package by user request.");
            usageStats.registerBlocked(r);
            return true;
        }
        return false;
    }

    private boolean isBlocked(NotificationRecord r) {
        final String pkg = r.sbn.getPackageName();
        final int callingUid = r.sbn.getUid();
        return mPreferencesHelper.isGroupBlocked(pkg, callingUid, r.getChannel().getGroup())
                || mPreferencesHelper.getImportance(pkg, callingUid)
                == NotificationManager.IMPORTANCE_NONE
                || r.getImportance() == NotificationManager.IMPORTANCE_NONE;
    }

    protected class SnoozeNotificationRunnable implements Runnable {
        private final String mKey;
        private final long mDuration;
        private final String mSnoozeCriterionId;

        SnoozeNotificationRunnable(String key, long duration, String snoozeCriterionId) {
            mKey = key;
            mDuration = duration;
            mSnoozeCriterionId = snoozeCriterionId;
        }

        @Override
        public void run() {
            synchronized (mNotificationLock) {
                final NotificationRecord r = findNotificationByKeyLocked(mKey);
                if (r != null) {
                    snoozeLocked(r);
                }
            }
        }

        @GuardedBy("mNotificationLock")
        void snoozeLocked(NotificationRecord r) {
            if (r.sbn.isGroup()) {
                final List<NotificationRecord> groupNotifications = findGroupNotificationsLocked(
                        r.sbn.getPackageName(), r.sbn.getGroupKey(), r.sbn.getUserId());
                if (r.getNotification().isGroupSummary()) {
                    // snooze summary and all children
                    for (int i = 0; i < groupNotifications.size(); i++) {
                        snoozeNotificationLocked(groupNotifications.get(i));
                    }
                } else {
                    // if there is a valid summary for this group, and we are snoozing the only
                    // child, also snooze the summary
                    if (mSummaryByGroupKey.containsKey(r.sbn.getGroupKey())) {
                        if (groupNotifications.size() != 2) {
                            snoozeNotificationLocked(r);
                        } else {
                            // snooze summary and the one child
                            for (int i = 0; i < groupNotifications.size(); i++) {
                                snoozeNotificationLocked(groupNotifications.get(i));
                            }
                        }
                    } else {
                        snoozeNotificationLocked(r);
                    }
                }
            } else {
                // just snooze the one notification
                snoozeNotificationLocked(r);
            }
        }

        @GuardedBy("mNotificationLock")
        void snoozeNotificationLocked(NotificationRecord r) {
            MetricsLogger.action(r.getLogMaker()
                    .setCategory(MetricsEvent.NOTIFICATION_SNOOZED)
                    .setType(MetricsEvent.TYPE_CLOSE)
                    .addTaggedData(MetricsEvent.FIELD_NOTIFICATION_SNOOZE_DURATION_MS,
                            mDuration)
                    .addTaggedData(MetricsEvent.NOTIFICATION_SNOOZED_CRITERIA,
                            mSnoozeCriterionId == null ? 0 : 1));
            reportUserInteraction(r);
            boolean wasPosted = removeFromNotificationListsLocked(r);
            cancelNotificationLocked(r, false, REASON_SNOOZED, wasPosted, null);
            updateLightsLocked();
            if (mSnoozeCriterionId != null) {
                mAssistants.notifyAssistantSnoozedLocked(r.sbn, mSnoozeCriterionId);
                mSnoozeHelper.snooze(r);
            } else {
                mSnoozeHelper.snooze(r, mDuration);
            }
            r.recordSnoozed();
            handleSavePolicyFile();
        }
    }

    protected class CancelNotificationRunnable implements Runnable {
        private final int mCallingUid;
        private final int mCallingPid;
        private final String mPkg;
        private final String mTag;
        private final int mId;
        private final int mMustHaveFlags;
        private final int mMustNotHaveFlags;
        private final boolean mSendDelete;
        private final int mUserId;
        private final int mReason;
        private final int mRank;
        private final int mCount;
        private final ManagedServiceInfo mListener;

        CancelNotificationRunnable(final int callingUid, final int callingPid,
                final String pkg, final String tag, final int id,
                final int mustHaveFlags, final int mustNotHaveFlags, final boolean sendDelete,
                final int userId, final int reason, int rank, int count,
                final ManagedServiceInfo listener) {
            this.mCallingUid = callingUid;
            this.mCallingPid = callingPid;
            this.mPkg = pkg;
            this.mTag = tag;
            this.mId = id;
            this.mMustHaveFlags = mustHaveFlags;
            this.mMustNotHaveFlags = mustNotHaveFlags;
            this.mSendDelete = sendDelete;
            this.mUserId = userId;
            this.mReason = reason;
            this.mRank = rank;
            this.mCount = count;
            this.mListener = listener;
        }

        @Override
        public void run() {
            String listenerName = mListener == null ? null : mListener.component.toShortString();
            if (DBG) {
                EventLogTags.writeNotificationCancel(mCallingUid, mCallingPid, mPkg, mId, mTag,
                        mUserId, mMustHaveFlags, mMustNotHaveFlags, mReason, listenerName);
            }

            synchronized (mNotificationLock) {
                // If the notification is currently enqueued, repost this runnable so it has a
                // chance to notify listeners
                if ((findNotificationByListLocked(mEnqueuedNotifications, mPkg, mTag, mId, mUserId))
                        != null) {
                    mHandler.post(this);
                    return;
                }
                // Look for the notification in the posted list, since we already checked enqueued.
                NotificationRecord r =
                        findNotificationByListLocked(mNotificationList, mPkg, mTag, mId, mUserId);
                if (r != null) {
                    // The notification was found, check if it should be removed.

                    // Ideally we'd do this in the caller of this method. However, that would
                    // require the caller to also find the notification.
                    if (mReason == REASON_CLICK) {
                        mUsageStats.registerClickedByUser(r);
                    }

                    if ((r.getNotification().flags & mMustHaveFlags) != mMustHaveFlags) {
                        return;
                    }
                    if ((r.getNotification().flags & mMustNotHaveFlags) != 0) {
                        return;
                    }

                    // Bubbled children get to stick around if the summary was manually cancelled
                    // (user removed) from systemui.
                    FlagChecker childrenFlagChecker = null;
                    if (mReason == REASON_CANCEL
                            || mReason == REASON_CLICK
                            || mReason == REASON_CANCEL_ALL) {
                        childrenFlagChecker = (flags) -> {
                            if ((flags & FLAG_BUBBLE) != 0) {
                                return false;
                            }
                            return true;
                        };
                    }

                    // Cancel the notification.
                    boolean wasPosted = removeFromNotificationListsLocked(r);
                    cancelNotificationLocked(
                            r, mSendDelete, mReason, mRank, mCount, wasPosted, listenerName);
                    cancelGroupChildrenLocked(r, mCallingUid, mCallingPid, listenerName,
                            mSendDelete, childrenFlagChecker);
                    updateLightsLocked();
                } else {
                    // No notification was found, assume that it is snoozed and cancel it.
                    if (mReason != REASON_SNOOZED) {
                        final boolean wasSnoozed = mSnoozeHelper.cancel(mUserId, mPkg, mTag, mId);
                        if (wasSnoozed) {
                            handleSavePolicyFile();
                        }
                    }
                }
            }
        }
    }

    protected class EnqueueNotificationRunnable implements Runnable {
        private final NotificationRecord r;
        private final int userId;

        EnqueueNotificationRunnable(int userId, NotificationRecord r) {
            this.userId = userId;
            this.r = r;
        };

        @Override
        public void run() {
            synchronized (mNotificationLock) {
                mEnqueuedNotifications.add(r);
                scheduleTimeoutLocked(r);

                final StatusBarNotification n = r.sbn;
                if (DBG) Slog.d(TAG, "EnqueueNotificationRunnable.run for: " + n.getKey());
                NotificationRecord old = mNotificationsByKey.get(n.getKey());
                if (old != null) {
                    // Retain ranking information from previous record
                    r.copyRankingInformation(old);
                }

                final int callingUid = n.getUid();
                final int callingPid = n.getInitialPid();
                final Notification notification = n.getNotification();
                final String pkg = n.getPackageName();
                final int id = n.getId();
                final String tag = n.getTag();

                // We need to fix the notification up a little for bubbles
                flagNotificationForBubbles(r, pkg, callingUid, old);

                // Handle grouped notifications and bail out early if we
                // can to avoid extracting signals.
                handleGroupedNotificationLocked(r, old, callingUid, callingPid);

                // if this is a group child, unsnooze parent summary
                if (n.isGroup() && notification.isGroupChild()) {
                    mSnoozeHelper.repostGroupSummary(pkg, r.getUserId(), n.getGroupKey());
                }

                // This conditional is a dirty hack to limit the logging done on
                //     behalf of the download manager without affecting other apps.
                if (!pkg.equals("com.android.providers.downloads")
                        || Log.isLoggable("DownloadManager", Log.VERBOSE)) {
                    int enqueueStatus = EVENTLOG_ENQUEUE_STATUS_NEW;
                    if (old != null) {
                        enqueueStatus = EVENTLOG_ENQUEUE_STATUS_UPDATE;
                    }
                    EventLogTags.writeNotificationEnqueue(callingUid, callingPid,
                            pkg, id, tag, userId, notification.toString(),
                            enqueueStatus);
                }

                // tell the assistant service about the notification
                if (mAssistants.isEnabled()) {
                    mAssistants.onNotificationEnqueuedLocked(r);
                    mHandler.postDelayed(new PostNotificationRunnable(r.getKey()),
                            DELAY_FOR_ASSISTANT_TIME);
                } else {
                    mHandler.post(new PostNotificationRunnable(r.getKey()));
                }
            }
        }
    }

    @GuardedBy("mNotificationLock")
    boolean isPackagePausedOrSuspended(String pkg, int uid) {
        boolean isPaused;

        final PackageManagerInternal pmi = LocalServices.getService(
                PackageManagerInternal.class);
        int flags = pmi.getDistractingPackageRestrictions(
                pkg, Binder.getCallingUserHandle().getIdentifier());
        isPaused = ((flags & PackageManager.RESTRICTION_HIDE_NOTIFICATIONS) != 0);

        isPaused |= isPackageSuspendedForUser(pkg, uid);

        return isPaused;
    }

    protected class PostNotificationRunnable implements Runnable {
        private final String key;

        PostNotificationRunnable(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            synchronized (mNotificationLock) {
                try {
                    NotificationRecord r = null;
                    int N = mEnqueuedNotifications.size();
                    for (int i = 0; i < N; i++) {
                        final NotificationRecord enqueued = mEnqueuedNotifications.get(i);
                        if (Objects.equals(key, enqueued.getKey())) {
                            r = enqueued;
                            break;
                        }
                    }
                    if (r == null) {
                        Slog.i(TAG, "Cannot find enqueued record for key: " + key);
                        return;
                    }

                    if (isBlocked(r)) {
                        Slog.i(TAG, "notification blocked by assistant request");
                        return;
                    }

                    final boolean isPackageSuspended =
                            isPackagePausedOrSuspended(r.sbn.getPackageName(), r.getUid());
                    r.setHidden(isPackageSuspended);
                    if (isPackageSuspended) {
                        mUsageStats.registerSuspendedByAdmin(r);
                    }
                    NotificationRecord old = mNotificationsByKey.get(key);
                    final StatusBarNotification n = r.sbn;
                    final Notification notification = n.getNotification();
                    int index = indexOfNotificationLocked(n.getKey());
                    if (index < 0) {
                        mNotificationList.add(r);
                        mUsageStats.registerPostedByApp(r);
                        r.setInterruptive(isVisuallyInterruptive(null, r));
                    } else {
                        old = mNotificationList.get(index);
                        mNotificationList.set(index, r);
                        mUsageStats.registerUpdatedByApp(r, old);
                        // Make sure we don't lose the foreground service state.
                        notification.flags |=
                                old.getNotification().flags & FLAG_FOREGROUND_SERVICE;
                        r.isUpdate = true;
                        final boolean isInterruptive = isVisuallyInterruptive(old, r);
                        r.setTextChanged(isInterruptive);
                        r.setInterruptive(isInterruptive);
                    }

                    mNotificationsByKey.put(n.getKey(), r);

                    // Ensure if this is a foreground service that the proper additional
                    // flags are set.
                    if ((notification.flags & FLAG_FOREGROUND_SERVICE) != 0) {
                        notification.flags |= FLAG_ONGOING_EVENT
                                | FLAG_NO_CLEAR;
                    }

                    mRankingHelper.extractSignals(r);
                    mRankingHelper.sort(mNotificationList);

                    if (!r.isHidden()) {
                        buzzBeepBlinkLocked(r);
                    }

                    if (notification.getSmallIcon() != null) {
                        StatusBarNotification oldSbn = (old != null) ? old.sbn : null;
                        mListeners.notifyPostedLocked(r, old);
                        if ((oldSbn == null || !Objects.equals(oldSbn.getGroup(), n.getGroup()))
                                && !isCritical(r)) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mGroupHelper.onNotificationPosted(
                                            n, hasAutoGroupSummaryLocked(n));
                                }
                            });
                        }
                    } else {
                        Slog.e(TAG, "Not posting notification without small icon: " + notification);
                        if (old != null && !old.isCanceled) {
                            mListeners.notifyRemovedLocked(r,
                                    NotificationListenerService.REASON_ERROR, r.getStats());
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mGroupHelper.onNotificationRemoved(n);
                                }
                            });
                        }
                        // ATTENTION: in a future release we will bail out here
                        // so that we do not play sounds, show lights, etc. for invalid
                        // notifications
                        Slog.e(TAG, "WARNING: In a future release this will crash the app: "
                                + n.getPackageName());
                    }

                    maybeRecordInterruptionLocked(r);
                } finally {
                    int N = mEnqueuedNotifications.size();
                    for (int i = 0; i < N; i++) {
                        final NotificationRecord enqueued = mEnqueuedNotifications.get(i);
                        if (Objects.equals(key, enqueued.getKey())) {
                            mEnqueuedNotifications.remove(i);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * If the notification differs enough visually, consider it a new interruptive notification.
     */
    @GuardedBy("mNotificationLock")
    @VisibleForTesting
    protected boolean isVisuallyInterruptive(NotificationRecord old, NotificationRecord r) {
        // Ignore summary updates because we don't display most of the information.
        if (r.sbn.isGroup() && r.sbn.getNotification().isGroupSummary()) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is not interruptive: summary");
            }
            return false;
        }

        if (old == null) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is interruptive: new notification");
            }
            return true;
        }

        if (r == null) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is not interruptive: null");
            }
            return false;
        }

        Notification oldN = old.sbn.getNotification();
        Notification newN = r.sbn.getNotification();
        if (oldN.extras == null || newN.extras == null) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is not interruptive: no extras");
            }
            return false;
        }

        // Ignore visual interruptions from foreground services because users
        // consider them one 'session'. Count them for everything else.
        if ((r.sbn.getNotification().flags & FLAG_FOREGROUND_SERVICE) != 0) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is not interruptive: foreground service");
            }
            return false;
        }

        final String oldTitle = String.valueOf(oldN.extras.get(Notification.EXTRA_TITLE));
        final String newTitle = String.valueOf(newN.extras.get(Notification.EXTRA_TITLE));
        if (!Objects.equals(oldTitle, newTitle)) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is interruptive: changed title");
                Slog.v(TAG, "INTERRUPTIVENESS: " + String.format("   old title: %s (%s@0x%08x)",
                        oldTitle, oldTitle.getClass(), oldTitle.hashCode()));
                Slog.v(TAG, "INTERRUPTIVENESS: " + String.format("   new title: %s (%s@0x%08x)",
                        newTitle, newTitle.getClass(), newTitle.hashCode()));
            }
            return true;
        }

        // Do not compare Spannables (will always return false); compare unstyled Strings
        final String oldText = String.valueOf(oldN.extras.get(Notification.EXTRA_TEXT));
        final String newText = String.valueOf(newN.extras.get(Notification.EXTRA_TEXT));
        if (!Objects.equals(oldText, newText)) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        + r.getKey() + " is interruptive: changed text");
                Slog.v(TAG, "INTERRUPTIVENESS: " + String.format("   old text: %s (%s@0x%08x)",
                        oldText, oldText.getClass(), oldText.hashCode()));
                Slog.v(TAG, "INTERRUPTIVENESS: " + String.format("   new text: %s (%s@0x%08x)",
                        newText, newText.getClass(), newText.hashCode()));
            }
            return true;
        }

        if (oldN.hasCompletedProgress() != newN.hasCompletedProgress()) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                    +  r.getKey() + " is interruptive: completed progress");
            }
            return true;
        }

        // Fields below are invisible to bubbles.
        if (r.canBubble()) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is not interruptive: bubble");
            }
            return false;
        }

        // Actions
        if (Notification.areActionsVisiblyDifferent(oldN, newN)) {
            if (DEBUG_INTERRUPTIVENESS) {
                Slog.v(TAG, "INTERRUPTIVENESS: "
                        +  r.getKey() + " is interruptive: changed actions");
            }
            return true;
        }

        try {
            Notification.Builder oldB = Notification.Builder.recoverBuilder(getContext(), oldN);
            Notification.Builder newB = Notification.Builder.recoverBuilder(getContext(), newN);

            // Style based comparisons
            if (Notification.areStyledNotificationsVisiblyDifferent(oldB, newB)) {
                if (DEBUG_INTERRUPTIVENESS) {
                    Slog.v(TAG, "INTERRUPTIVENESS: "
                            +  r.getKey() + " is interruptive: styles differ");
                }
                return true;
            }

            // Remote views
            if (Notification.areRemoteViewsChanged(oldB, newB)) {
                if (DEBUG_INTERRUPTIVENESS) {
                    Slog.v(TAG, "INTERRUPTIVENESS: "
                            +  r.getKey() + " is interruptive: remoteviews differ");
                }
                return true;
            }
        } catch (Exception e) {
            Slog.w(TAG, "error recovering builder", e);
        }
        return false;
    }

    /**
     * Check if the notification is classified as critical.
     *
     * @param record the record to test for criticality
     * @return {@code true} if notification is considered critical
     *
     * @see CriticalNotificationExtractor for criteria
     */
    private boolean isCritical(NotificationRecord record) {
        // 0 is the most critical
        return record.getCriticality() < CriticalNotificationExtractor.NORMAL;
    }

    /**
     * Ensures that grouped notification receive their special treatment.
     *
     * <p>Cancels group children if the new notification causes a group to lose
     * its summary.</p>
     *
     * <p>Updates mSummaryByGroupKey.</p>
     */
    @GuardedBy("mNotificationLock")
    private void handleGroupedNotificationLocked(NotificationRecord r, NotificationRecord old,
            int callingUid, int callingPid) {
        StatusBarNotification sbn = r.sbn;
        Notification n = sbn.getNotification();
        if (n.isGroupSummary() && !sbn.isAppGroup())  {
            // notifications without a group shouldn't be a summary, otherwise autobundling can
            // lead to bugs
            n.flags &= ~Notification.FLAG_GROUP_SUMMARY;
        }

        String group = sbn.getGroupKey();
        boolean isSummary = n.isGroupSummary();

        Notification oldN = old != null ? old.sbn.getNotification() : null;
        String oldGroup = old != null ? old.sbn.getGroupKey() : null;
        boolean oldIsSummary = old != null && oldN.isGroupSummary();

        if (oldIsSummary) {
            NotificationRecord removedSummary = mSummaryByGroupKey.remove(oldGroup);
            if (removedSummary != old) {
                String removedKey =
                        removedSummary != null ? removedSummary.getKey() : "<null>";
                Slog.w(TAG, "Removed summary didn't match old notification: old=" + old.getKey() +
                        ", removed=" + removedKey);
            }
        }
        if (isSummary) {
            mSummaryByGroupKey.put(group, r);
        }

        // Clear out group children of the old notification if the update
        // causes the group summary to go away. This happens when the old
        // notification was a summary and the new one isn't, or when the old
        // notification was a summary and its group key changed.
        if (oldIsSummary && (!isSummary || !oldGroup.equals(group))) {
            cancelGroupChildrenLocked(old, callingUid, callingPid, null, false /* sendDelete */,
                    null);
        }
    }

    @VisibleForTesting
    @GuardedBy("mNotificationLock")
    void scheduleTimeoutLocked(NotificationRecord record) {
        if (record.getNotification().getTimeoutAfter() > 0) {
            final PendingIntent pi = PendingIntent.getBroadcast(getContext(),
                    REQUEST_CODE_TIMEOUT,
                    new Intent(ACTION_NOTIFICATION_TIMEOUT)
                            .setPackage(PackageManagerService.PLATFORM_PACKAGE_NAME)
                            .setData(new Uri.Builder().scheme(SCHEME_TIMEOUT)
                                    .appendPath(record.getKey()).build())
                            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                            .putExtra(EXTRA_KEY, record.getKey()),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + record.getNotification().getTimeoutAfter(), pi);
        }
    }

    @VisibleForTesting
    @GuardedBy("mNotificationLock")
    void buzzBeepBlinkLocked(NotificationRecord record) {
        if (mIsAutomotive && !mNotificationEffectsEnabledForAutomotive) {
            return;
        }
        boolean buzz = false;
        boolean beep = false;
        boolean blink = false;

        final Notification notification = record.sbn.getNotification();
        final String key = record.getKey();

        // Should this notification make noise, vibe, or use the LED?
        final boolean aboveThreshold =
                mIsAutomotive
                        ? record.getImportance() > NotificationManager.IMPORTANCE_DEFAULT
                        : record.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT;

        // Remember if this notification already owns the notification channels.
        boolean wasBeep = key != null && key.equals(mSoundNotificationKey);
        boolean wasBuzz = key != null && key.equals(mVibrateNotificationKey);
        // These are set inside the conditional if the notification is allowed to make noise.
        boolean hasValidVibrate = false;
        boolean hasValidSound = false;
        boolean sentAccessibilityEvent = false;
        // If the notification will appear in the status bar, it should send an accessibility
        // event
        if (!record.isUpdate && record.getImportance() > IMPORTANCE_MIN) {
            sendAccessibilityEvent(notification, record.sbn.getPackageName());
            sentAccessibilityEvent = true;
        }

        if (aboveThreshold && isNotificationForCurrentUser(record)) {
            boolean skipSound = mScreenOn && !mSoundVibScreenOn;
            boolean noNoiseOnGamingMode = (mScreenOn && mGamingMode && mSoundVibGamingMode);
            if (mSystemReady && mAudioManager != null && !skipSound && !noNoiseOnGamingMode) {
                Uri soundUri = record.getSound();
                hasValidSound = soundUri != null && !Uri.EMPTY.equals(soundUri);
                long[] vibration = record.getVibration();
                // Demote sound to vibration if vibration missing & phone in vibration mode.
                if (vibration == null
                        && hasValidSound
                        && (mAudioManager.getRingerModeInternal()
                        == AudioManager.RINGER_MODE_VIBRATE)
                        && mAudioManager.getStreamVolume(
                        AudioAttributes.toLegacyStreamType(record.getAudioAttributes())) == 0) {
                    vibration = mFallbackVibrationPattern;
                }
                hasValidVibrate = vibration != null
                        && (mVibrateOnNotifications
                        || (mAudioManager.getRingerModeInternal()
                        == AudioManager.RINGER_MODE_VIBRATE));

                boolean hasAudibleAlert = hasValidSound || hasValidVibrate;
                if (hasAudibleAlert && !shouldMuteNotificationLocked(record)
                        && !isInSoundTimeoutPeriod(record)) {
                    if (!sentAccessibilityEvent) {
                        sendAccessibilityEvent(notification, record.sbn.getPackageName());
                        sentAccessibilityEvent = true;
                    }
                    if (DBG) Slog.v(TAG, "Interrupting!");
                    if (hasValidSound) {
                        if (isInCall()) {
                            playInCallNotification();
                            beep = true;
                        } else {
                            beep = playSound(record, soundUri);
                        }
                        if(beep) {
                            mSoundNotificationKey = key;
                        }
                    }

                    final boolean ringerModeSilent =
                            mAudioManager.getRingerModeInternal()
                                    == AudioManager.RINGER_MODE_SILENT;
                    if (!isInCall() && hasValidVibrate && !ringerModeSilent) {
                        buzz = playVibration(record, vibration, hasValidSound);
                        if(buzz) {
                            mVibrateNotificationKey = key;
                        }
                    }
                } else if ((record.getFlags() & Notification.FLAG_INSISTENT) != 0) {
                    hasValidSound = false;
                }
            }
        }
        // If a notification is updated to remove the actively playing sound or vibrate,
        // cancel that feedback now
        if (wasBeep && !hasValidSound) {
            clearSoundLocked();
        }
        if (wasBuzz && !hasValidVibrate) {
            clearVibrateLocked();
        }

        // light
        // release the light
        boolean wasShowLights = mLights.remove(key);
        if (canShowLightsLocked(record, aboveThreshold)) {
            mLights.add(key);
            updateLightsLocked();
            if (mUseAttentionLight) {
                mAttentionLight.pulse();
            }
            blink = true;
        } else if (wasShowLights) {
            updateLightsLocked();
        }

        if (buzz || beep) {
            mLastSoundTimestamps.put(generateLastSoundTimeoutKey(record),
                    SystemClock.elapsedRealtime());
        }

        if (buzz || beep || blink) {
            // Ignore summary updates because we don't display most of the information.
            if (record.sbn.isGroup() && record.sbn.getNotification().isGroupSummary()) {
                if (DEBUG_INTERRUPTIVENESS) {
                    Slog.v(TAG, "INTERRUPTIVENESS: "
                            + record.getKey() + " is not interruptive: summary");
                }
            } else if (record.canBubble()) {
                if (DEBUG_INTERRUPTIVENESS) {
                    Slog.v(TAG, "INTERRUPTIVENESS: "
                            + record.getKey() + " is not interruptive: bubble");
                }
            } else {
                record.setInterruptive(true);
                if (DEBUG_INTERRUPTIVENESS) {
                    Slog.v(TAG, "INTERRUPTIVENESS: "
                            + record.getKey() + " is interruptive: alerted");
                }
            }
            MetricsLogger.action(record.getLogMaker()
                    .setCategory(MetricsEvent.NOTIFICATION_ALERT)
                    .setType(MetricsEvent.TYPE_OPEN)
                    .setSubtype((buzz ? 1 : 0) | (beep ? 2 : 0) | (blink ? 4 : 0)));
            EventLogTags.writeNotificationAlert(key, buzz ? 1 : 0, beep ? 1 : 0, blink ? 1 : 0);
        }
        record.setAudiblyAlerted(buzz || beep);
    }

    private boolean isInSoundTimeoutPeriod(NotificationRecord record) {
        long timeoutMillis = mPreferencesHelper.getNotificationSoundTimeout(
                record.sbn.getPackageName(), record.sbn.getUid());
        if (timeoutMillis == 0) {
            return false;
        }

        Long value = mLastSoundTimestamps.get(generateLastSoundTimeoutKey(record));
        if (value == null) {
            return false;
        }
        return SystemClock.elapsedRealtime() - value < timeoutMillis;
    }

    private String generateLastSoundTimeoutKey(NotificationRecord record) {
        return record.sbn.getPackageName() + "|" + record.sbn.getUid();
    }

    @GuardedBy("mNotificationLock")
    boolean canShowLightsLocked(final NotificationRecord record, boolean aboveThreshold) {
        // device lacks light
        if (!mHasLight) {
            return false;
        }
        // Forced on
        // Used by LineageParts light picker
        // eg to allow selecting battery light color when notification led is turned off.
        if (isLedForcedOn(record)) {
            return true;
        }
        // user turned lights off globally
        if (!mNotificationPulseEnabled) {
            return false;
        }
        // the notification/channel has no light
        if (record.getLight() == null) {
            return false;
        }
        // unimportant notification
        if (!aboveThreshold) {
            return false;
        }
        // Suppressed because it's a silent update
        final Notification notification = record.getNotification();
        if (record.isUpdate && (notification.flags & FLAG_ONLY_ALERT_ONCE) != 0) {
            return false;
        }
        // Suppressed because another notification in its group handles alerting
        if (record.sbn.isGroup() && record.getNotification().suppressAlertingDueToGrouping()) {
            return false;
        }
        // check current user
        if (!isNotificationForCurrentUser(record)) {
            return false;
        }

        return true;
    }

    @GuardedBy("mNotificationLock")
    boolean shouldMuteNotificationLocked(final NotificationRecord record) {
        // Suppressed because it's a silent update
        final Notification notification = record.getNotification();
        if (record.isUpdate && (notification.flags & FLAG_ONLY_ALERT_ONCE) != 0) {
            return true;
        }

        // muted by listener
        final String disableEffects = disableNotificationEffects(record);
        if (disableEffects != null) {
            ZenLog.traceDisableEffects(record, disableEffects);
            return true;
        }

        // suppressed due to DND
        if (record.isIntercepted()) {
            return true;
        }

        // Suppressed because another notification in its group handles alerting
        if (record.sbn.isGroup()) {
            if (notification.suppressAlertingDueToGrouping()) {
                return true;
            }
        }

        // Suppressed for being too recently noisy
        final String pkg = record.sbn.getPackageName();
        if (mUsageStats.isAlertRateLimited(pkg)) {
            Slog.e(TAG, "Muting recently noisy " + record.getKey());
            return true;
        }

        return false;
    }

    private boolean playSound(final NotificationRecord record, Uri soundUri) {
        boolean looping = (record.getNotification().flags & Notification.FLAG_INSISTENT) != 0;
        // play notifications if there is no user of exclusive audio focus
        // and the stream volume is not 0 (non-zero volume implies not silenced by SILENT or
        //   VIBRATE ringer mode)
        if (!mAudioManager.isAudioFocusExclusive()
                && (mAudioManager.getStreamVolume(
                        AudioAttributes.toLegacyStreamType(record.getAudioAttributes())) != 0)) {
            final long identity = Binder.clearCallingIdentity();
            try {
                final IRingtonePlayer player = mAudioManager.getRingtonePlayer();
                if (player != null) {
                    if (DBG) Slog.v(TAG, "Playing sound " + soundUri
                            + " with attributes " + record.getAudioAttributes());
                    player.playAsync(soundUri, record.sbn.getUser(), looping,
                            record.getAudioAttributes());
                    return true;
                }
            } catch (RemoteException e) {
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        return false;
    }

    private boolean playVibration(final NotificationRecord record, long[] vibration,
            boolean delayVibForSound) {
        // Escalate privileges so we can use the vibrator even if the
        // notifying app does not have the VIBRATE permission.
        long identity = Binder.clearCallingIdentity();
        try {
            final VibrationEffect effect;
            try {
                final boolean insistent =
                        (record.getNotification().flags & Notification.FLAG_INSISTENT) != 0;
                effect = VibrationEffect.createWaveform(
                        vibration, insistent ? 0 : -1 /*repeatIndex*/);
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Error creating vibration waveform with pattern: " +
                        Arrays.toString(vibration));
                return false;
            }
            if (delayVibForSound) {
                new Thread(() -> {
                    // delay the vibration by the same amount as the notification sound
                    final int waitMs = mAudioManager.getFocusRampTimeMs(
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                            record.getAudioAttributes());
                    if (DBG) Slog.v(TAG, "Delaying vibration by " + waitMs + "ms");
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) { }

                    // Notifications might be canceled before it actually vibrates due to waitMs,
                    // so need to check the notification still valide for vibrate.
                    synchronized (mNotificationLock) {
                        if (mNotificationsByKey.get(record.getKey()) != null) {
                            mVibrator.vibrate(record.sbn.getUid(), record.sbn.getOpPkg(),
                                    effect, "Notification (delayed)", record.getAudioAttributes());
                        } else {
                            Slog.e(TAG, "No vibration for canceled notification : " + record.getKey());
                        }
                    }
                }).start();
            } else {
                mVibrator.vibrate(record.sbn.getUid(), record.sbn.getPackageName(),
                        effect, "Notification", record.getAudioAttributes());
            }
            return true;
        } finally{
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isNotificationForCurrentUser(NotificationRecord record) {
        final int currentUser;
        final long token = Binder.clearCallingIdentity();
        try {
            currentUser = ActivityManager.getCurrentUser();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return (record.getUserId() == UserHandle.USER_ALL ||
                record.getUserId() == currentUser ||
                mUserProfiles.isCurrentProfile(record.getUserId()));
    }

    protected void playInCallNotification() {
        if (mAudioManager.getRingerModeInternal() == AudioManager.RINGER_MODE_NORMAL
                && Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.IN_CALL_NOTIFICATION_ENABLED, 1) != 0) {
            new Thread() {
                @Override
                public void run() {
                    final long identity = Binder.clearCallingIdentity();
                    try {
                        final IRingtonePlayer player = mAudioManager.getRingtonePlayer();
                        if (player != null) {
                            if (mCallNotificationToken != null) {
                                player.stop(mCallNotificationToken);
                            }
                            mCallNotificationToken = new Binder();
                            player.play(mCallNotificationToken, mInCallNotificationUri,
                                    mInCallNotificationAudioAttributes,
                                    mInCallNotificationVolume, false);
                        }
                    } catch (RemoteException e) {
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            }.start();
        }
    }

    @GuardedBy("mToastQueue")
    void showNextToastLocked() {
        ToastRecord record = mToastQueue.get(0);
        while (record != null) {
            if (DBG) Slog.d(TAG, "Show pkg=" + record.pkg + " callback=" + record.callback);
            try {
                record.callback.show(record.token);
                scheduleDurationReachedLocked(record);
                return;
            } catch (RemoteException e) {
                Slog.w(TAG, "Object died trying to show notification " + record.callback
                        + " in package " + record.pkg);
                // remove it from the list and let the process die
                int index = mToastQueue.indexOf(record);
                if (index >= 0) {
                    mToastQueue.remove(index);
                }
                keepProcessAliveIfNeededLocked(record.pid);
                if (mToastQueue.size() > 0) {
                    record = mToastQueue.get(0);
                } else {
                    record = null;
                }
            }
        }
    }

    @GuardedBy("mToastQueue")
    void cancelToastLocked(int index) {
        ToastRecord record = mToastQueue.get(index);
        try {
            record.callback.hide();
        } catch (RemoteException e) {
            Slog.w(TAG, "Object died trying to hide notification " + record.callback
                    + " in package " + record.pkg);
            // don't worry about this, we're about to remove it from
            // the list anyway
        }

        ToastRecord lastToast = mToastQueue.remove(index);

        mWindowManagerInternal.removeWindowToken(lastToast.token, false /* removeWindows */,
                lastToast.displayId);
        // We passed 'false' for 'removeWindows' so that the client has time to stop
        // rendering (as hide above is a one-way message), otherwise we could crash
        // a client which was actively using a surface made from the token. However
        // we need to schedule a timeout to make sure the token is eventually killed
        // one way or another.
        scheduleKillTokenTimeout(lastToast);

        keepProcessAliveIfNeededLocked(record.pid);
        if (mToastQueue.size() > 0) {
            // Show the next one. If the callback fails, this will remove
            // it from the list, so don't assume that the list hasn't changed
            // after this point.
            showNextToastLocked();
        }
    }

    void finishTokenLocked(IBinder t, int displayId) {
        mHandler.removeCallbacksAndMessages(t);
        // We pass 'true' for 'removeWindows' to let the WindowManager destroy any
        // remaining surfaces as either the client has called finishToken indicating
        // it has successfully removed the views, or the client has timed out
        // at which point anything goes.
        mWindowManagerInternal.removeWindowToken(t, true /* removeWindows */, displayId);
    }

    @GuardedBy("mToastQueue")
    private void scheduleDurationReachedLocked(ToastRecord r)
    {
        mHandler.removeCallbacksAndMessages(r);
        Message m = Message.obtain(mHandler, MESSAGE_DURATION_REACHED, r);
        int delay = r.duration == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY;
        // Accessibility users may need longer timeout duration. This api compares original delay
        // with user's preference and return longer one. It returns original delay if there's no
        // preference.
        delay = mAccessibilityManager.getRecommendedTimeoutMillis(delay,
                AccessibilityManager.FLAG_CONTENT_TEXT);
        mHandler.sendMessageDelayed(m, delay);
    }

    private void handleDurationReached(ToastRecord record)
    {
        if (DBG) Slog.d(TAG, "Timeout pkg=" + record.pkg + " callback=" + record.callback);
        synchronized (mToastQueue) {
            int index = indexOfToastLocked(record.pkg, record.callback);
            if (index >= 0) {
                cancelToastLocked(index);
            }
        }
    }

    @GuardedBy("mToastQueue")
    private void scheduleKillTokenTimeout(ToastRecord r)
    {
        mHandler.removeCallbacksAndMessages(r);
        Message m = Message.obtain(mHandler, MESSAGE_FINISH_TOKEN_TIMEOUT, r);
        mHandler.sendMessageDelayed(m, FINISH_TOKEN_TIMEOUT);
    }

    private void handleKillTokenTimeout(ToastRecord record)
    {
        if (DBG) Slog.d(TAG, "Kill Token Timeout token=" + record.token);
        synchronized (mToastQueue) {
            finishTokenLocked(record.token, record.displayId);
        }
    }

    @GuardedBy("mToastQueue")
    int indexOfToastLocked(String pkg, ITransientNotification callback)
    {
        IBinder cbak = callback.asBinder();
        ArrayList<ToastRecord> list = mToastQueue;
        int len = list.size();
        for (int i=0; i<len; i++) {
            ToastRecord r = list.get(i);
            if (r.pkg.equals(pkg) && r.callback.asBinder() == cbak) {
                return i;
            }
        }
        return -1;
    }

    @GuardedBy("mToastQueue")
    void keepProcessAliveIfNeededLocked(int pid)
    {
        int toastCount = 0; // toasts from this pid
        ArrayList<ToastRecord> list = mToastQueue;
        int N = list.size();
        for (int i=0; i<N; i++) {
            ToastRecord r = list.get(i);
            if (r.pid == pid) {
                toastCount++;
            }
        }
        try {
            mAm.setProcessImportant(mForegroundToken, pid, toastCount > 0, "toast");
        } catch (RemoteException e) {
            // Shouldn't happen.
        }
    }

    private void handleRankingReconsideration(Message message) {
        if (!(message.obj instanceof RankingReconsideration)) return;
        RankingReconsideration recon = (RankingReconsideration) message.obj;
        recon.run();
        boolean changed;
        synchronized (mNotificationLock) {
            final NotificationRecord record = mNotificationsByKey.get(recon.getKey());
            if (record == null) {
                return;
            }
            int indexBefore = findNotificationRecordIndexLocked(record);
            boolean interceptBefore = record.isIntercepted();
            int visibilityBefore = record.getPackageVisibilityOverride();
            boolean interruptiveBefore = record.isInterruptive();

            recon.applyChangesLocked(record);
            applyZenModeLocked(record);
            mRankingHelper.sort(mNotificationList);
            boolean indexChanged = indexBefore != findNotificationRecordIndexLocked(record);
            boolean interceptChanged = interceptBefore != record.isIntercepted();
            boolean visibilityChanged = visibilityBefore != record.getPackageVisibilityOverride();

            // Broadcast isInterruptive changes for bubbles.
            boolean interruptiveChanged =
                    record.canBubble() && (interruptiveBefore != record.isInterruptive());

            changed = indexChanged || interceptChanged || visibilityChanged || interruptiveChanged;
            if (interceptBefore && !record.isIntercepted()
                    && record.isNewEnoughForAlerting(System.currentTimeMillis())) {
                buzzBeepBlinkLocked(record);
            }
        }
        if (changed) {
            mHandler.scheduleSendRankingUpdate();
        }
    }

    void handleRankingSort() {
        if (mRankingHelper == null) return;
        synchronized (mNotificationLock) {
            final int N = mNotificationList.size();
            // Any field that can change via one of the extractors needs to be added here.
            ArrayList<String> orderBefore = new ArrayList<>(N);
            int[] visibilities = new int[N];
            boolean[] showBadges = new boolean[N];
            boolean[] allowBubbles = new boolean[N];
            ArrayList<NotificationChannel> channelBefore = new ArrayList<>(N);
            ArrayList<String> groupKeyBefore = new ArrayList<>(N);
            ArrayList<ArrayList<String>> overridePeopleBefore = new ArrayList<>(N);
            ArrayList<ArrayList<SnoozeCriterion>> snoozeCriteriaBefore = new ArrayList<>(N);
            ArrayList<Integer> userSentimentBefore = new ArrayList<>(N);
            ArrayList<Integer> suppressVisuallyBefore = new ArrayList<>(N);
            ArrayList<ArrayList<Notification.Action>> systemSmartActionsBefore = new ArrayList<>(N);
            ArrayList<ArrayList<CharSequence>> smartRepliesBefore = new ArrayList<>(N);
            int[] importancesBefore = new int[N];
            for (int i = 0; i < N; i++) {
                final NotificationRecord r = mNotificationList.get(i);
                orderBefore.add(r.getKey());
                visibilities[i] = r.getPackageVisibilityOverride();
                showBadges[i] = r.canShowBadge();
                allowBubbles[i] = r.canBubble();
                channelBefore.add(r.getChannel());
                groupKeyBefore.add(r.getGroupKey());
                overridePeopleBefore.add(r.getPeopleOverride());
                snoozeCriteriaBefore.add(r.getSnoozeCriteria());
                userSentimentBefore.add(r.getUserSentiment());
                suppressVisuallyBefore.add(r.getSuppressedVisualEffects());
                systemSmartActionsBefore.add(r.getSystemGeneratedSmartActions());
                smartRepliesBefore.add(r.getSmartReplies());
                importancesBefore[i] = r.getImportance();
                mRankingHelper.extractSignals(r);
            }
            mRankingHelper.sort(mNotificationList);
            for (int i = 0; i < N; i++) {
                final NotificationRecord r = mNotificationList.get(i);
                if (!orderBefore.get(i).equals(r.getKey())
                        || visibilities[i] != r.getPackageVisibilityOverride()
                        || showBadges[i] != r.canShowBadge()
                        || allowBubbles[i] != r.canBubble()
                        || !Objects.equals(channelBefore.get(i), r.getChannel())
                        || !Objects.equals(groupKeyBefore.get(i), r.getGroupKey())
                        || !Objects.equals(overridePeopleBefore.get(i), r.getPeopleOverride())
                        || !Objects.equals(snoozeCriteriaBefore.get(i), r.getSnoozeCriteria())
                        || !Objects.equals(userSentimentBefore.get(i), r.getUserSentiment())
                        || !Objects.equals(suppressVisuallyBefore.get(i),
                        r.getSuppressedVisualEffects())
                        || !Objects.equals(systemSmartActionsBefore.get(i),
                                r.getSystemGeneratedSmartActions())
                        || !Objects.equals(smartRepliesBefore.get(i), r.getSmartReplies())
                        || importancesBefore[i] != r.getImportance()) {
                    mHandler.scheduleSendRankingUpdate();
                    return;
                }
            }
        }
    }

    @GuardedBy("mNotificationLock")
    private void recordCallerLocked(NotificationRecord record) {
        if (mZenModeHelper.isCall(record)) {
            mZenModeHelper.recordCaller(record);
        }
    }

    // let zen mode evaluate this record
    @GuardedBy("mNotificationLock")
    private void applyZenModeLocked(NotificationRecord record) {
        record.setIntercepted(mZenModeHelper.shouldIntercept(record));
        if (record.isIntercepted()) {
            record.setSuppressedVisualEffects(
                    mZenModeHelper.getConsolidatedNotificationPolicy().suppressedVisualEffects);
        } else {
            record.setSuppressedVisualEffects(0);
        }
    }

    @GuardedBy("mNotificationLock")
    private int findNotificationRecordIndexLocked(NotificationRecord target) {
        return mRankingHelper.indexOf(mNotificationList, target);
    }

    private void handleSendRankingUpdate() {
        synchronized (mNotificationLock) {
            mListeners.notifyRankingUpdateLocked(null);
        }
    }

    private void scheduleListenerHintsChanged(int state) {
        mHandler.removeMessages(MESSAGE_LISTENER_HINTS_CHANGED);
        mHandler.obtainMessage(MESSAGE_LISTENER_HINTS_CHANGED, state, 0).sendToTarget();
    }

    private void scheduleInterruptionFilterChanged(int listenerInterruptionFilter) {
        mHandler.removeMessages(MESSAGE_LISTENER_NOTIFICATION_FILTER_CHANGED);
        mHandler.obtainMessage(
                MESSAGE_LISTENER_NOTIFICATION_FILTER_CHANGED,
                listenerInterruptionFilter,
                0).sendToTarget();
    }

    private void handleListenerHintsChanged(int hints) {
        synchronized (mNotificationLock) {
            mListeners.notifyListenerHintsChangedLocked(hints);
        }
    }

    private void handleListenerInterruptionFilterChanged(int interruptionFilter) {
        synchronized (mNotificationLock) {
            mListeners.notifyInterruptionFilterChanged(interruptionFilter);
        }
    }

    private void handleOnPackageChanged(boolean removingPackage, int changeUserId,
            String[] pkgList, int[] uidList) {
        boolean preferencesChanged = removingPackage;
        mListeners.onPackagesChanged(removingPackage, pkgList, uidList);
        mAssistants.onPackagesChanged(removingPackage, pkgList, uidList);
        mConditionProviders.onPackagesChanged(removingPackage, pkgList, uidList);
        preferencesChanged |= mPreferencesHelper.onPackagesChanged(
                removingPackage, changeUserId, pkgList, uidList);
        if (preferencesChanged) {
            handleSavePolicyFile();
        }
    }

    protected class WorkerHandler extends Handler
    {
        public WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case MESSAGE_DURATION_REACHED:
                    handleDurationReached((ToastRecord) msg.obj);
                    break;
                case MESSAGE_FINISH_TOKEN_TIMEOUT:
                    handleKillTokenTimeout((ToastRecord) msg.obj);
                    break;
                case MESSAGE_SEND_RANKING_UPDATE:
                    handleSendRankingUpdate();
                    break;
                case MESSAGE_LISTENER_HINTS_CHANGED:
                    handleListenerHintsChanged(msg.arg1);
                    break;
                case MESSAGE_LISTENER_NOTIFICATION_FILTER_CHANGED:
                    handleListenerInterruptionFilterChanged(msg.arg1);
                    break;
                case MESSAGE_ON_PACKAGE_CHANGED:
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleOnPackageChanged((boolean) args.arg1, args.argi1, (String[]) args.arg2,
                            (int[]) args.arg3);
                    args.recycle();
                    break;
            }
        }

        protected void scheduleSendRankingUpdate() {
            if (!hasMessages(MESSAGE_SEND_RANKING_UPDATE)) {
                Message m = Message.obtain(this, MESSAGE_SEND_RANKING_UPDATE);
                sendMessage(m);
            }
        }

        protected void scheduleCancelNotification(CancelNotificationRunnable cancelRunnable) {
            if (!hasCallbacks(cancelRunnable)) {
                sendMessage(Message.obtain(this, cancelRunnable));
            }
        }

        protected void scheduleOnPackageChanged(boolean removingPackage, int changeUserId,
                String[] pkgList, int[] uidList) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = removingPackage;
            args.argi1 = changeUserId;
            args.arg2 = pkgList;
            args.arg3 = uidList;
            sendMessage(Message.obtain(this, MESSAGE_ON_PACKAGE_CHANGED, args));
        }
    }

    private final class RankingHandlerWorker extends Handler implements RankingHandler
    {
        public RankingHandlerWorker(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_RECONSIDER_RANKING:
                    handleRankingReconsideration(msg);
                    break;
                case MESSAGE_RANKING_SORT:
                    handleRankingSort();
                    break;
            }
        }

        public void requestSort() {
            removeMessages(MESSAGE_RANKING_SORT);
            Message msg = Message.obtain();
            msg.what = MESSAGE_RANKING_SORT;
            sendMessage(msg);
        }

        public void requestReconsideration(RankingReconsideration recon) {
            Message m = Message.obtain(this,
                    NotificationManagerService.MESSAGE_RECONSIDER_RANKING, recon);
            long delay = recon.getDelay(TimeUnit.MILLISECONDS);
            sendMessageDelayed(m, delay);
        }
    }

    // Notifications
    // ============================================================================
    static int clamp(int x, int low, int high) {
        return (x < low) ? low : ((x > high) ? high : x);
    }

    void sendAccessibilityEvent(Notification notification, CharSequence packageName) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }

        AccessibilityEvent event =
            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setPackageName(packageName);
        event.setClassName(Notification.class.getName());
        event.setParcelableData(notification);
        CharSequence tickerText = notification.tickerText;
        if (!TextUtils.isEmpty(tickerText)) {
            event.getText().add(tickerText);
        }

        mAccessibilityManager.sendAccessibilityEvent(event);
    }

    /**
     * Removes all NotificationsRecords with the same key as the given notification record
     * from both lists. Do not call this method while iterating over either list.
     */
    @GuardedBy("mNotificationLock")
    private boolean removeFromNotificationListsLocked(NotificationRecord r) {
        // Remove from both lists, either list could have a separate Record for what is
        // effectively the same notification.
        boolean wasPosted = false;
        NotificationRecord recordInList = null;
        if ((recordInList = findNotificationByListLocked(mNotificationList, r.getKey()))
                != null) {
            mNotificationList.remove(recordInList);
            mNotificationsByKey.remove(recordInList.sbn.getKey());
            wasPosted = true;
        }
        while ((recordInList = findNotificationByListLocked(mEnqueuedNotifications, r.getKey()))
                != null) {
            mEnqueuedNotifications.remove(recordInList);
        }
        return wasPosted;
    }

    @GuardedBy("mNotificationLock")
    private void cancelNotificationLocked(NotificationRecord r, boolean sendDelete, int reason,
            boolean wasPosted, String listenerName) {
        cancelNotificationLocked(r, sendDelete, reason, -1, -1, wasPosted, listenerName);
    }

    @GuardedBy("mNotificationLock")
    private void cancelNotificationLocked(NotificationRecord r, boolean sendDelete, int reason,
            int rank, int count, boolean wasPosted, String listenerName) {
        final String canceledKey = r.getKey();

        // Record caller.
        recordCallerLocked(r);

        if (r.getStats().getDismissalSurface() == NotificationStats.DISMISSAL_NOT_DISMISSED) {
            r.recordDismissalSurface(NotificationStats.DISMISSAL_OTHER);
        }

        // tell the app
        if (sendDelete) {
            final PendingIntent deleteIntent = r.getNotification().deleteIntent;
            if (deleteIntent != null) {
                try {
                    // make sure deleteIntent cannot be used to start activities from background
                    LocalServices.getService(ActivityManagerInternal.class)
                            .clearPendingIntentAllowBgActivityStarts(deleteIntent.getTarget(),
                            WHITELIST_TOKEN);
                    deleteIntent.send();
                } catch (PendingIntent.CanceledException ex) {
                    // do nothing - there's no relevant way to recover, and
                    //     no reason to let this propagate
                    Slog.w(TAG, "canceled PendingIntent for " + r.sbn.getPackageName(), ex);
                }
            }
        }

        // Only cancel these if this notification actually got to be posted.
        if (wasPosted) {
            // status bar
            if (r.getNotification().getSmallIcon() != null) {
                if (reason != REASON_SNOOZED) {
                    r.isCanceled = true;
                }
                mListeners.notifyRemovedLocked(r, reason, r.getStats());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mGroupHelper.onNotificationRemoved(r.sbn);
                    }
                });
            }

            // sound
            if (canceledKey.equals(mSoundNotificationKey)) {
                mSoundNotificationKey = null;
                final long identity = Binder.clearCallingIdentity();
                try {
                    final IRingtonePlayer player = mAudioManager.getRingtonePlayer();
                    if (player != null) {
                        player.stopAsync();
                    }
                } catch (RemoteException e) {
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }

            // vibrate
            if (canceledKey.equals(mVibrateNotificationKey)) {
                mVibrateNotificationKey = null;
                long identity = Binder.clearCallingIdentity();
                try {
                    mVibrator.cancel();
                }
                finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }

            // light
            mLights.remove(canceledKey);
        }

        // Record usage stats
        // TODO: add unbundling stats?
        switch (reason) {
            case REASON_CANCEL:
            case REASON_CANCEL_ALL:
            case REASON_LISTENER_CANCEL:
            case REASON_LISTENER_CANCEL_ALL:
                mUsageStats.registerDismissedByUser(r);
                break;
            case REASON_APP_CANCEL:
            case REASON_APP_CANCEL_ALL:
                mUsageStats.registerRemovedByApp(r);
                break;
        }

        String groupKey = r.getGroupKey();
        NotificationRecord groupSummary = mSummaryByGroupKey.get(groupKey);
        if (groupSummary != null && groupSummary.getKey().equals(canceledKey)) {
            mSummaryByGroupKey.remove(groupKey);
        }
        final ArrayMap<String, String> summaries = mAutobundledSummaries.get(r.sbn.getUserId());
        if (summaries != null && r.sbn.getKey().equals(summaries.get(r.sbn.getPackageName()))) {
            summaries.remove(r.sbn.getPackageName());
        }

        // Save it for users of getHistoricalNotifications()
        mArchive.record(r.sbn);

        final long now = System.currentTimeMillis();
        final LogMaker logMaker = r.getItemLogMaker()
                .setType(MetricsEvent.TYPE_DISMISS)
                .setSubtype(reason);
        if (rank != -1 && count != -1) {
            logMaker.addTaggedData(MetricsEvent.NOTIFICATION_SHADE_INDEX, rank)
                    .addTaggedData(MetricsEvent.NOTIFICATION_SHADE_COUNT, count);
        }
        MetricsLogger.action(logMaker);
        EventLogTags.writeNotificationCanceled(canceledKey, reason,
                r.getLifespanMs(now), r.getFreshnessMs(now), r.getExposureMs(now),
                rank, count, listenerName);
    }

    @VisibleForTesting
    void updateUriPermissions(@Nullable NotificationRecord newRecord,
            @Nullable NotificationRecord oldRecord, String targetPkg, int targetUserId) {
        final String key = (newRecord != null) ? newRecord.getKey() : oldRecord.getKey();
        if (DBG) Slog.d(TAG, key + ": updating permissions");

        final ArraySet<Uri> newUris = (newRecord != null) ? newRecord.getGrantableUris() : null;
        final ArraySet<Uri> oldUris = (oldRecord != null) ? oldRecord.getGrantableUris() : null;

        // Shortcut when no Uris involved
        if (newUris == null && oldUris == null) {
            return;
        }

        // Inherit any existing owner
        IBinder permissionOwner = null;
        if (newRecord != null && permissionOwner == null) {
            permissionOwner = newRecord.permissionOwner;
        }
        if (oldRecord != null && permissionOwner == null) {
            permissionOwner = oldRecord.permissionOwner;
        }

        // If we have Uris to grant, but no owner yet, go create one
        if (newUris != null && permissionOwner == null) {
            if (DBG) Slog.d(TAG, key + ": creating owner");
            permissionOwner = mUgmInternal.newUriPermissionOwner("NOTIF:" + key);
        }

        // If we have no Uris to grant, but an existing owner, go destroy it
        if (newUris == null && permissionOwner != null) {
            final long ident = Binder.clearCallingIdentity();
            try {
                if (DBG) Slog.d(TAG, key + ": destroying owner");
                mUgmInternal.revokeUriPermissionFromOwner(permissionOwner, null, ~0,
                        UserHandle.getUserId(oldRecord.getUid()));
                permissionOwner = null;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        // Grant access to new Uris
        if (newUris != null && permissionOwner != null) {
            for (int i = 0; i < newUris.size(); i++) {
                final Uri uri = newUris.valueAt(i);
                if (oldUris == null || !oldUris.contains(uri)) {
                    if (DBG) Slog.d(TAG, key + ": granting " + uri);
                    grantUriPermission(permissionOwner, uri, newRecord.getUid(), targetPkg,
                            targetUserId);
                }
            }
        }

        // Revoke access to old Uris
        if (oldUris != null && permissionOwner != null) {
            for (int i = 0; i < oldUris.size(); i++) {
                final Uri uri = oldUris.valueAt(i);
                if (newUris == null || !newUris.contains(uri)) {
                    if (DBG) Slog.d(TAG, key + ": revoking " + uri);
                    revokeUriPermission(permissionOwner, uri, oldRecord.getUid());
                }
            }
        }

        if (newRecord != null) {
            newRecord.permissionOwner = permissionOwner;
        }
    }

    private void grantUriPermission(IBinder owner, Uri uri, int sourceUid, String targetPkg,
            int targetUserId) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return;

        final long ident = Binder.clearCallingIdentity();
        try {
            mUgm.grantUriPermissionFromOwner(owner, sourceUid, targetPkg,
                    ContentProvider.getUriWithoutUserId(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    ContentProvider.getUserIdFromUri(uri, UserHandle.getUserId(sourceUid)),
                    targetUserId);
        } catch (RemoteException ignored) {
            // Ignored because we're in same process
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void revokeUriPermission(IBinder owner, Uri uri, int sourceUid) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return;

        final long ident = Binder.clearCallingIdentity();
        try {
            mUgmInternal.revokeUriPermissionFromOwner(
                    owner,
                    ContentProvider.getUriWithoutUserId(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    ContentProvider.getUserIdFromUri(uri, UserHandle.getUserId(sourceUid)));
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * Cancels a notification ONLY if it has all of the {@code mustHaveFlags}
     * and none of the {@code mustNotHaveFlags}.
     */
    void cancelNotification(final int callingUid, final int callingPid,
            final String pkg, final String tag, final int id,
            final int mustHaveFlags, final int mustNotHaveFlags, final boolean sendDelete,
            final int userId, final int reason, final ManagedServiceInfo listener) {
        cancelNotification(callingUid, callingPid, pkg, tag, id, mustHaveFlags, mustNotHaveFlags,
                sendDelete, userId, reason, -1 /* rank */, -1 /* count */, listener);
    }

    /**
     * Cancels a notification ONLY if it has all of the {@code mustHaveFlags}
     * and none of the {@code mustNotHaveFlags}.
     */
    void cancelNotification(final int callingUid, final int callingPid,
            final String pkg, final String tag, final int id,
            final int mustHaveFlags, final int mustNotHaveFlags, final boolean sendDelete,
            final int userId, final int reason, int rank, int count,
            final ManagedServiceInfo listener) {
        // In enqueueNotificationInternal notifications are added by scheduling the
        // work on the worker handler. Hence, we also schedule the cancel on this
        // handler to avoid a scenario where an add notification call followed by a
        // remove notification call ends up in not removing the notification.
        mHandler.scheduleCancelNotification(new CancelNotificationRunnable(callingUid, callingPid,
                pkg, tag, id, mustHaveFlags, mustNotHaveFlags, sendDelete, userId, reason, rank,
                count, listener));
    }

    /**
     * Determine whether the userId applies to the notification in question, either because
     * they match exactly, or one of them is USER_ALL (which is treated as a wildcard).
     */
    private boolean notificationMatchesUserId(NotificationRecord r, int userId) {
        return
                // looking for USER_ALL notifications? match everything
                   userId == UserHandle.USER_ALL
                // a notification sent to USER_ALL matches any query
                || r.getUserId() == UserHandle.USER_ALL
                // an exact user match
                || r.getUserId() == userId;
    }

    /**
     * Determine whether the userId applies to the notification in question, either because
     * they match exactly, or one of them is USER_ALL (which is treated as a wildcard) or
     * because it matches one of the users profiles.
     */
    private boolean notificationMatchesCurrentProfiles(NotificationRecord r, int userId) {
        return notificationMatchesUserId(r, userId)
                || mUserProfiles.isCurrentProfile(r.getUserId());
    }

    /**
     * Cancels all notifications from a given package that have all of the
     * {@code mustHaveFlags}.
     */
    void cancelAllNotificationsInt(int callingUid, int callingPid, String pkg, String channelId,
            int mustHaveFlags, int mustNotHaveFlags, boolean doit, int userId, int reason,
            ManagedServiceInfo listener) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String listenerName = listener == null ? null : listener.component.toShortString();
                EventLogTags.writeNotificationCancelAll(callingUid, callingPid,
                        pkg, userId, mustHaveFlags, mustNotHaveFlags, reason,
                        listenerName);

                // Why does this parameter exist? Do we actually want to execute the above if doit
                // is false?
                if (!doit) {
                    return;
                }

                synchronized (mNotificationLock) {
                    FlagChecker flagChecker = (int flags) -> {
                        if ((flags & mustHaveFlags) != mustHaveFlags) {
                            return false;
                        }
                        if ((flags & mustNotHaveFlags) != 0) {
                            return false;
                        }
                        return true;
                    };
                    cancelAllNotificationsByListLocked(mNotificationList, callingUid, callingPid,
                            pkg, true /*nullPkgIndicatesUserSwitch*/, channelId, flagChecker,
                            false /*includeCurrentProfiles*/, userId, false /*sendDelete*/, reason,
                            listenerName, true /* wasPosted */);
                    cancelAllNotificationsByListLocked(mEnqueuedNotifications, callingUid,
                            callingPid, pkg, true /*nullPkgIndicatesUserSwitch*/, channelId,
                            flagChecker, false /*includeCurrentProfiles*/, userId,
                            false /*sendDelete*/, reason, listenerName, false /* wasPosted */);
                    mSnoozeHelper.cancel(userId, pkg);
                }
            }
        });
    }

    private interface FlagChecker {
        // Returns false if these flags do not pass the defined flag test.
        public boolean apply(int flags);
    }

    @GuardedBy("mNotificationLock")
    private void cancelAllNotificationsByListLocked(ArrayList<NotificationRecord> notificationList,
            int callingUid, int callingPid, String pkg, boolean nullPkgIndicatesUserSwitch,
            String channelId, FlagChecker flagChecker, boolean includeCurrentProfiles, int userId,
            boolean sendDelete, int reason, String listenerName, boolean wasPosted) {
        ArrayList<NotificationRecord> canceledNotifications = null;
        for (int i = notificationList.size() - 1; i >= 0; --i) {
            NotificationRecord r = notificationList.get(i);
            if (includeCurrentProfiles) {
                if (!notificationMatchesCurrentProfiles(r, userId)) {
                    continue;
                }
            } else if (!notificationMatchesUserId(r, userId)) {
                continue;
            }
            // Don't remove notifications to all, if there's no package name specified
            if (nullPkgIndicatesUserSwitch && pkg == null && r.getUserId() == UserHandle.USER_ALL) {
                continue;
            }
            if (!flagChecker.apply(r.getFlags())) {
                continue;
            }
            if (pkg != null && !r.sbn.getPackageName().equals(pkg)) {
                continue;
            }
            if (channelId != null && !channelId.equals(r.getChannel().getId())) {
                continue;
            }
            if (canceledNotifications == null) {
                canceledNotifications = new ArrayList<>();
            }
            notificationList.remove(i);
            mNotificationsByKey.remove(r.getKey());
            r.recordDismissalSentiment(NotificationStats.DISMISS_SENTIMENT_NEUTRAL);
            canceledNotifications.add(r);
            cancelNotificationLocked(r, sendDelete, reason, wasPosted, listenerName);
        }
        if (canceledNotifications != null) {
            final int M = canceledNotifications.size();
            for (int i = 0; i < M; i++) {
                cancelGroupChildrenLocked(canceledNotifications.get(i), callingUid, callingPid,
                        listenerName, false /* sendDelete */, flagChecker);
            }
            updateLightsLocked();
        }
    }

    void snoozeNotificationInt(String key, long duration, String snoozeCriterionId,
            ManagedServiceInfo listener) {
        String listenerName = listener == null ? null : listener.component.toShortString();
        if (duration <= 0 && snoozeCriterionId == null || key == null) {
            return;
        }

        if (DBG) {
            Slog.d(TAG, String.format("snooze event(%s, %d, %s, %s)", key, duration,
                    snoozeCriterionId, listenerName));
        }
        // Needs to post so that it can cancel notifications not yet enqueued.
        mHandler.post(new SnoozeNotificationRunnable(key, duration, snoozeCriterionId));
    }

    void unsnoozeNotificationInt(String key, ManagedServiceInfo listener) {
        String listenerName = listener == null ? null : listener.component.toShortString();
        if (DBG) {
            Slog.d(TAG, String.format("unsnooze event(%s, %s)", key, listenerName));
        }
        mSnoozeHelper.repost(key);
        handleSavePolicyFile();
    }

    @GuardedBy("mNotificationLock")
    void cancelAllLocked(int callingUid, int callingPid, int userId, int reason,
            ManagedServiceInfo listener, boolean includeCurrentProfiles) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mNotificationLock) {
                    String listenerName =
                            listener == null ? null : listener.component.toShortString();
                    EventLogTags.writeNotificationCancelAll(callingUid, callingPid,
                            null, userId, 0, 0, reason, listenerName);

                    FlagChecker flagChecker = (int flags) -> {
                        int flagsToCheck = FLAG_ONGOING_EVENT | FLAG_NO_CLEAR;
                        if (REASON_LISTENER_CANCEL_ALL == reason) {
                            flagsToCheck |= FLAG_BUBBLE;
                        }
                        if ((flags & flagsToCheck) != 0) {
                            return false;
                        }
                        return true;
                    };

                    cancelAllNotificationsByListLocked(mNotificationList, callingUid, callingPid,
                            null, false /*nullPkgIndicatesUserSwitch*/, null, flagChecker,
                            includeCurrentProfiles, userId, true /*sendDelete*/, reason,
                            listenerName, true);
                    cancelAllNotificationsByListLocked(mEnqueuedNotifications, callingUid,
                            callingPid, null, false /*nullPkgIndicatesUserSwitch*/, null,
                            flagChecker, includeCurrentProfiles, userId, true /*sendDelete*/,
                            reason, listenerName, false);
                    mSnoozeHelper.cancel(userId, includeCurrentProfiles);
                }
            }
        });
    }

    // Warning: The caller is responsible for invoking updateLightsLocked().
    @GuardedBy("mNotificationLock")
    private void cancelGroupChildrenLocked(NotificationRecord r, int callingUid, int callingPid,
            String listenerName, boolean sendDelete, FlagChecker flagChecker) {
        Notification n = r.getNotification();
        if (!n.isGroupSummary()) {
            return;
        }

        String pkg = r.sbn.getPackageName();

        if (pkg == null) {
            if (DBG) Slog.e(TAG, "No package for group summary: " + r.getKey());
            return;
        }

        cancelGroupChildrenByListLocked(mNotificationList, r, callingUid, callingPid, listenerName,
                sendDelete, true, flagChecker);
        cancelGroupChildrenByListLocked(mEnqueuedNotifications, r, callingUid, callingPid,
                listenerName, sendDelete, false, flagChecker);
    }

    @GuardedBy("mNotificationLock")
    private void cancelGroupChildrenByListLocked(ArrayList<NotificationRecord> notificationList,
            NotificationRecord parentNotification, int callingUid, int callingPid,
            String listenerName, boolean sendDelete, boolean wasPosted, FlagChecker flagChecker) {
        final String pkg = parentNotification.sbn.getPackageName();
        final int userId = parentNotification.getUserId();
        final int reason = REASON_GROUP_SUMMARY_CANCELED;
        for (int i = notificationList.size() - 1; i >= 0; i--) {
            final NotificationRecord childR = notificationList.get(i);
            final StatusBarNotification childSbn = childR.sbn;
            if ((childSbn.isGroup() && !childSbn.getNotification().isGroupSummary()) &&
                    childR.getGroupKey().equals(parentNotification.getGroupKey())
                    && (childR.getFlags() & FLAG_FOREGROUND_SERVICE) == 0
                    && (flagChecker == null || flagChecker.apply(childR.getFlags()))) {
                EventLogTags.writeNotificationCancel(callingUid, callingPid, pkg, childSbn.getId(),
                        childSbn.getTag(), userId, 0, 0, reason, listenerName);
                notificationList.remove(i);
                mNotificationsByKey.remove(childR.getKey());
                cancelNotificationLocked(childR, sendDelete, reason, wasPosted, listenerName);
            }
        }
    }

    @GuardedBy("mNotificationLock")
    void updateLightsLocked()
    {
        // handle notification lights
        NotificationRecord ledNotification = null;
        while (ledNotification == null && !mLights.isEmpty()) {
            final String owner = mLights.get(mLights.size() - 1);
            ledNotification = mNotificationsByKey.get(owner);
            if (ledNotification == null) {
                Slog.wtfStack(TAG, "LED Notification does not exist: " + owner);
                mLights.remove(owner);
            }
        }

        NotificationRecord.Light light = ledNotification != null ?
                ledNotification.getLight() : null;
        if (ledNotification == null || mLineageNotificationLights == null || light == null) {
            mNotificationLight.turnOff();
            return;
        }

        int ledColor = light.color;
        if (isLedForcedOn(ledNotification) && ledColor == 0) {
            // User has requested color 0.  However, lineage-sdk interprets
            // color 0 as "supply a default" therefore adjust alpha to make
            // the color still black but non-zero.
            ledColor = 0x01000000;
        }

        LedValues ledValues = new LedValues(ledColor, light.onMs, light.offMs);
        mLineageNotificationLights.calcLights(ledValues, ledNotification.sbn.getPackageName(),
                ledNotification.sbn.getNotification(), mScreenOn || isInCall(),
                ledNotification.getSuppressedVisualEffects());

        if (!ledValues.isEnabled()) {
            mNotificationLight.turnOff();
        } else {
            mNotificationLight.setModes(ledValues.getBrightness());

            // we are using 1:0 to indicate LED should stay always on
            if (ledValues.getOnMs() == 1 && ledValues.getOffMs() == 0) {
                mNotificationLight.setColor(ledValues.getColor());
            } else {
                mNotificationLight.setFlashing(ledValues.getColor(), Light.LIGHT_FLASH_TIMED,
                        ledValues.getOnMs(), ledValues.getOffMs());
            }
        }
    }

    private boolean isLedForcedOn(NotificationRecord nr) {
        return nr != null ?
                mLineageNotificationLights.isForcedOn(nr.sbn.getNotification()) : false;
    }

    @GuardedBy("mNotificationLock")
    @NonNull List<NotificationRecord> findGroupNotificationsLocked(String pkg,
            String groupKey, int userId) {
        List<NotificationRecord> records = new ArrayList<>();
        records.addAll(findGroupNotificationByListLocked(mNotificationList, pkg, groupKey, userId));
        records.addAll(
                findGroupNotificationByListLocked(mEnqueuedNotifications, pkg, groupKey, userId));
        return records;
    }


    @GuardedBy("mNotificationLock")
    private @NonNull List<NotificationRecord> findGroupNotificationByListLocked(
            ArrayList<NotificationRecord> list, String pkg, String groupKey, int userId) {
        List<NotificationRecord> records = new ArrayList<>();
        final int len = list.size();
        for (int i = 0; i < len; i++) {
            NotificationRecord r = list.get(i);
            if (notificationMatchesUserId(r, userId) && r.getGroupKey().equals(groupKey)
                    && r.sbn.getPackageName().equals(pkg)) {
                records.add(r);
            }
        }
        return records;
    }

    // Searches both enqueued and posted notifications by key.
    // TODO: need to combine a bunch of these getters with slightly different behavior.
    // TODO: Should enqueuing just add to mNotificationsByKey instead?
    @GuardedBy("mNotificationLock")
    private NotificationRecord findNotificationByKeyLocked(String key) {
        NotificationRecord r;
        if ((r = findNotificationByListLocked(mNotificationList, key)) != null) {
            return r;
        }
        if ((r = findNotificationByListLocked(mEnqueuedNotifications, key)) != null) {
            return r;
        }
        return null;
    }

    @GuardedBy("mNotificationLock")
    NotificationRecord findNotificationLocked(String pkg, String tag, int id, int userId) {
        NotificationRecord r;
        if ((r = findNotificationByListLocked(mNotificationList, pkg, tag, id, userId)) != null) {
            return r;
        }
        if ((r = findNotificationByListLocked(mEnqueuedNotifications, pkg, tag, id, userId))
                != null) {
            return r;
        }
        return null;
    }

    @GuardedBy("mNotificationLock")
    private NotificationRecord findNotificationByListLocked(ArrayList<NotificationRecord> list,
            String pkg, String tag, int id, int userId) {
        final int len = list.size();
        for (int i = 0; i < len; i++) {
            NotificationRecord r = list.get(i);
            if (notificationMatchesUserId(r, userId) && r.sbn.getId() == id &&
                    TextUtils.equals(r.sbn.getTag(), tag) && r.sbn.getPackageName().equals(pkg)) {
                return r;
            }
        }
        return null;
    }

    @GuardedBy("mNotificationLock")
    private List<NotificationRecord> findNotificationsByListLocked(
            ArrayList<NotificationRecord> list, String pkg, String tag, int id, int userId) {
        List<NotificationRecord> matching = new ArrayList<>();
        final int len = list.size();
        for (int i = 0; i < len; i++) {
            NotificationRecord r = list.get(i);
            if (notificationMatchesUserId(r, userId) && r.sbn.getId() == id &&
                    TextUtils.equals(r.sbn.getTag(), tag) && r.sbn.getPackageName().equals(pkg)) {
                matching.add(r);
            }
        }
        return matching;
    }

    @GuardedBy("mNotificationLock")
    private NotificationRecord findNotificationByListLocked(ArrayList<NotificationRecord> list,
            String key) {
        final int N = list.size();
        for (int i = 0; i < N; i++) {
            if (key.equals(list.get(i).getKey())) {
                return list.get(i);
            }
        }
        return null;
    }

    @GuardedBy("mNotificationLock")
    int indexOfNotificationLocked(String key) {
        final int N = mNotificationList.size();
        for (int i = 0; i < N; i++) {
            if (key.equals(mNotificationList.get(i).getKey())) {
                return i;
            }
        }
        return -1;
    }

    @VisibleForTesting
    protected void hideNotificationsForPackages(String[] pkgs) {
        synchronized (mNotificationLock) {
            List<String> pkgList = Arrays.asList(pkgs);
            List<NotificationRecord> changedNotifications = new ArrayList<>();
            int numNotifications = mNotificationList.size();
            for (int i = 0; i < numNotifications; i++) {
                NotificationRecord rec = mNotificationList.get(i);
                if (pkgList.contains(rec.sbn.getPackageName())) {
                    rec.setHidden(true);
                    changedNotifications.add(rec);
                }
            }

            mListeners.notifyHiddenLocked(changedNotifications);
        }
    }

    @VisibleForTesting
    protected void unhideNotificationsForPackages(String[] pkgs) {
        synchronized (mNotificationLock) {
            List<String> pkgList = Arrays.asList(pkgs);
            List<NotificationRecord> changedNotifications = new ArrayList<>();
            int numNotifications = mNotificationList.size();
            for (int i = 0; i < numNotifications; i++) {
                NotificationRecord rec = mNotificationList.get(i);
                if (pkgList.contains(rec.sbn.getPackageName())) {
                    rec.setHidden(false);
                    changedNotifications.add(rec);
                }
            }

            mListeners.notifyUnhiddenLocked(changedNotifications);
        }
    }

    private void updateNotificationPulse() {
        synchronized (mNotificationLock) {
            updateLightsLocked();
        }
    }

    protected boolean isCallingUidSystem() {
        final int uid = Binder.getCallingUid();
        return uid == Process.SYSTEM_UID;
    }

    protected boolean isUidSystemOrPhone(int uid) {
        final int appid = UserHandle.getAppId(uid);
        return (appid == Process.SYSTEM_UID || appid == Process.PHONE_UID || uid == 0);
    }

    // TODO: Most calls should probably move to isCallerSystem.
    protected boolean isCallerSystemOrPhone() {
        return isUidSystemOrPhone(Binder.getCallingUid());
    }

    private void checkCallerIsSystemOrShell() {
        if (Binder.getCallingUid() == Process.SHELL_UID) {
            return;
        }
        checkCallerIsSystem();
    }

    private void checkCallerIsSystem() {
        if (isCallerSystemOrPhone()) {
            return;
        }
        throw new SecurityException("Disallowed call for uid " + Binder.getCallingUid());
    }

    private void checkCallerIsSystemOrSystemUiOrShell() {
        if (Binder.getCallingUid() == Process.SHELL_UID) {
            return;
        }
        if (isCallerSystemOrPhone()) {
            return;
        }
        getContext().enforceCallingPermission(android.Manifest.permission.STATUS_BAR_SERVICE, null);
    }

    private void checkCallerIsSystemOrSameApp(String pkg) {
        if (isCallerSystemOrPhone()) {
            return;
        }
        checkCallerIsSameApp(pkg);
    }

    private boolean isCallerAndroid(String callingPkg, int uid) {
        return isUidSystemOrPhone(uid) && callingPkg != null
                && PackageManagerService.PLATFORM_PACKAGE_NAME.equals(callingPkg);
    }

    /**
     * Check if the notification is of a category type that is restricted to system use only,
     * if so throw SecurityException
     */
    private void checkRestrictedCategories(final Notification notification) {
        try {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, 0)) {
                return;
            }
        } catch (RemoteException re) {
            if (DBG) Slog.e(TAG, "Unable to confirm if it's safe to skip category "
                    + "restrictions check thus the check will be done anyway");
        }
        if (Notification.CATEGORY_CAR_EMERGENCY.equals(notification.category)
                || Notification.CATEGORY_CAR_WARNING.equals(notification.category)
                || Notification.CATEGORY_CAR_INFORMATION.equals(notification.category)) {
                    checkCallerIsSystem();
        }
    }

    @VisibleForTesting
    boolean isCallerInstantApp(int callingUid, int userId) {
        // System is always allowed to act for ephemeral apps.
        if (isUidSystemOrPhone(callingUid)) {
            return false;
        }

        if (userId == UserHandle.USER_ALL) {
            userId = USER_SYSTEM;
        }

        try {
            final String[] pkgs = mPackageManager.getPackagesForUid(callingUid);
            if (pkgs == null) {
                throw new SecurityException("Unknown uid " + callingUid);
            }
            final String pkg = pkgs[0];
            mAppOps.checkPackage(callingUid, pkg);

            ApplicationInfo ai = mPackageManager.getApplicationInfo(pkg, 0, userId);
            if (ai == null) {
                throw new SecurityException("Unknown package " + pkg);
            }
            return ai.isInstantApp();
        } catch (RemoteException re) {
            throw new SecurityException("Unknown uid " + callingUid, re);
        }
    }

    private void checkCallerIsSameApp(String pkg) {
        checkCallerIsSameApp(pkg, Binder.getCallingUid(), UserHandle.getCallingUserId());
    }

    private void checkCallerIsSameApp(String pkg, int uid, int userId) {
        try {
            ApplicationInfo ai = mPackageManager.getApplicationInfo(
                    pkg, 0, userId);
            if (ai == null) {
                throw new SecurityException("Unknown package " + pkg);
            }
            if (!UserHandle.isSameApp(ai.uid, uid)) {
                throw new SecurityException("Calling uid " + uid + " gave package "
                        + pkg + " which is owned by uid " + ai.uid);
            }
        } catch (RemoteException re) {
            throw new SecurityException("Unknown package " + pkg + "\n" + re);
        }
    }

    private boolean isCallerSameApp(String pkg) {
        try {
            checkCallerIsSameApp(pkg);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean isCallerSameApp(String pkg, int uid, int userId) {
        try {
            checkCallerIsSameApp(pkg, uid, userId);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String callStateToString(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE: return "CALL_STATE_IDLE";
            case TelephonyManager.CALL_STATE_RINGING: return "CALL_STATE_RINGING";
            case TelephonyManager.CALL_STATE_OFFHOOK: return "CALL_STATE_OFFHOOK";
            default: return "CALL_STATE_UNKNOWN_" + state;
        }
    }

    private void listenForCallState() {
        TelephonyManager.from(getContext()).listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (mCallState == state) return;
                if (DBG) Slog.d(TAG, "Call state changed: " + callStateToString(state));
                mCallState = state;
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * Generates a NotificationRankingUpdate from 'sbns', considering only
     * notifications visible to the given listener.
     */
    @GuardedBy("mNotificationLock")
    private NotificationRankingUpdate makeRankingUpdateLocked(ManagedServiceInfo info) {
        final int N = mNotificationList.size();
        final ArrayList<NotificationListenerService.Ranking> rankings = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            NotificationRecord record = mNotificationList.get(i);
            if (!isVisibleToListener(record.sbn, info)) {
                continue;
            }
            final String key = record.sbn.getKey();
            final NotificationListenerService.Ranking ranking =
                    new NotificationListenerService.Ranking();
            ranking.populate(
                    key,
                    rankings.size(),
                    !record.isIntercepted(),
                    record.getPackageVisibilityOverride(),
                    record.getSuppressedVisualEffects(),
                    record.getImportance(),
                    record.getImportanceExplanation(),
                    record.sbn.getOverrideGroupKey(),
                    record.getChannel(),
                    record.getPeopleOverride(),
                    record.getSnoozeCriteria(),
                    record.canShowBadge(),
                    record.getUserSentiment(),
                    record.isHidden(),
                    record.getLastAudiblyAlertedMs(),
                    record.getSound() != null || record.getVibration() != null,
                    record.getSystemGeneratedSmartActions(),
                    record.getSmartReplies(),
                    record.canBubble(),
                    record.isInterruptive()
            );
            rankings.add(ranking);
        }

        return new NotificationRankingUpdate(
                rankings.toArray(new NotificationListenerService.Ranking[0]));
    }

    boolean hasCompanionDevice(ManagedServiceInfo info) {
        if (mCompanionManager == null) {
            mCompanionManager = getCompanionManager();
        }
        // Companion mgr doesn't exist on all device types
        if (mCompanionManager == null) {
            return false;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            List<String> associations = mCompanionManager.getAssociations(
                    info.component.getPackageName(), info.userid);
            if (!ArrayUtils.isEmpty(associations)) {
                return true;
            }
        } catch (SecurityException se) {
            // Not a privileged listener
        } catch (RemoteException re) {
            Slog.e(TAG, "Cannot reach companion device service", re);
        } catch (Exception e) {
            Slog.e(TAG, "Cannot verify listener " + info, e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return false;
    }

    protected ICompanionDeviceManager getCompanionManager() {
        return ICompanionDeviceManager.Stub.asInterface(
                ServiceManager.getService(Context.COMPANION_DEVICE_SERVICE));
    }

    private boolean isVisibleToListener(StatusBarNotification sbn, ManagedServiceInfo listener) {
        if (!listener.enabledAndUserMatches(sbn.getUserId())) {
            return false;
        }
        // TODO: remove this for older listeners.
        return true;
    }

    private boolean isPackageSuspendedForUser(String pkg, int uid) {
        final long identity = Binder.clearCallingIdentity();
        int userId = UserHandle.getUserId(uid);
        try {
            return mPackageManager.isPackageSuspendedForUser(pkg, userId);
        } catch (RemoteException re) {
            throw new SecurityException("Could not talk to package manager service");
        } catch (IllegalArgumentException ex) {
            // Package not found.
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @VisibleForTesting
    boolean canUseManagedServices(String pkg, Integer userId, String requiredPermission) {
        boolean canUseManagedServices = !mActivityManager.isLowRamDevice()
                || mPackageManagerClient.hasSystemFeature(PackageManager.FEATURE_WATCH);

        for (String whitelisted : getContext().getResources().getStringArray(
                R.array.config_allowedManagedServicesOnLowRamDevices)) {
            if (whitelisted.equals(pkg)) {
                canUseManagedServices = true;
            }
        }

        if (requiredPermission != null) {
            try {
                if (mPackageManager.checkPermission(requiredPermission, pkg, userId)
                    != PackageManager.PERMISSION_GRANTED) {
                    canUseManagedServices = false;
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "can't talk to pm", e);
            }
        }

        return canUseManagedServices;
    }

    private class TrimCache {
        StatusBarNotification heavy;
        StatusBarNotification sbnClone;
        StatusBarNotification sbnCloneLight;

        TrimCache(StatusBarNotification sbn) {
            heavy = sbn;
        }

        StatusBarNotification ForListener(ManagedServiceInfo info) {
            if (mListeners.getOnNotificationPostedTrim(info) == TRIM_LIGHT) {
                if (sbnCloneLight == null) {
                    sbnCloneLight = heavy.cloneLight();
                }
                return sbnCloneLight;
            } else {
                if (sbnClone == null) {
                    sbnClone = heavy.clone();
                }
                return sbnClone;
            }
        }
    }

    private boolean isInCall() {
        if (mInCallStateOffHook) {
            return true;
        }
        int audioMode = mAudioManager.getMode();
        if (audioMode == AudioManager.MODE_IN_CALL
                || audioMode == AudioManager.MODE_IN_COMMUNICATION) {
            return true;
        }
        return false;
    }

    public class NotificationAssistants extends ManagedServices {
        static final String TAG_ENABLED_NOTIFICATION_ASSISTANTS = "enabled_assistants";

        private static final String ATT_USER_SET = "user_set";
        private static final String TAG_ALLOWED_ADJUSTMENT_TYPES = "q_allowed_adjustments";
        private static final String ATT_TYPES = "types";

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private ArrayMap<Integer, Boolean> mUserSetMap = new ArrayMap<>();
        private Set<String> mAllowedAdjustments = new ArraySet<>();

        public NotificationAssistants(Context context, Object lock, UserProfiles up,
                IPackageManager pm) {
            super(context, lock, up, pm);

            // Add all default allowed adjustment types. Will be overwritten by values in xml,
            // if they exist
            for (int i = 0; i < DEFAULT_ALLOWED_ADJUSTMENTS.length; i++) {
                mAllowedAdjustments.add(DEFAULT_ALLOWED_ADJUSTMENTS[i]);
            }
        }

        @Override
        protected Config getConfig() {
            Config c = new Config();
            c.caption = "notification assistant";
            c.serviceInterface = NotificationAssistantService.SERVICE_INTERFACE;
            c.xmlTag = TAG_ENABLED_NOTIFICATION_ASSISTANTS;
            c.secureSettingName = Settings.Secure.ENABLED_NOTIFICATION_ASSISTANT;
            c.bindPermission = Manifest.permission.BIND_NOTIFICATION_ASSISTANT_SERVICE;
            c.settingsAction = Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS;
            c.clientLabel = R.string.notification_ranker_binding_label;
            return c;
        }

        @Override
        protected IInterface asInterface(IBinder binder) {
            return INotificationListener.Stub.asInterface(binder);
        }

        @Override
        protected boolean checkType(IInterface service) {
            return service instanceof INotificationListener;
        }

        @Override
        protected void onServiceAdded(ManagedServiceInfo info) {
            mListeners.registerGuestService(info);
        }

        @Override
        @GuardedBy("mNotificationLock")
        protected void onServiceRemovedLocked(ManagedServiceInfo removed) {
            mListeners.unregisterService(removed.service, removed.userid);
        }

        @Override
        public void onUserUnlocked(int user) {
            if (DEBUG) Slog.d(TAG, "onUserUnlocked u=" + user);
            // force rebind the assistant, as it might be keeping its own state in user locked
            // storage
            rebindServices(true, user);
        }

        @Override
        protected String getRequiredPermission() {
            // only signature/privileged apps can be bound.
            return android.Manifest.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE;
        }

        @Override
        protected void writeExtraXmlTags(XmlSerializer out) throws IOException {
            synchronized (mLock) {
                out.startTag(null, TAG_ALLOWED_ADJUSTMENT_TYPES);
                out.attribute(null, ATT_TYPES, TextUtils.join(",", mAllowedAdjustments));
                out.endTag(null, TAG_ALLOWED_ADJUSTMENT_TYPES);
            }
        }

        @Override
        protected void readExtraTag(String tag, XmlPullParser parser) throws IOException {
            if (TAG_ALLOWED_ADJUSTMENT_TYPES.equals(tag)) {
                final String types = XmlUtils.readStringAttribute(parser, ATT_TYPES);
                synchronized (mLock) {
                    mAllowedAdjustments.clear();
                    if (!TextUtils.isEmpty(types)) {
                        mAllowedAdjustments.addAll(Arrays.asList(types.split(",")));
                    }
                }
            }
        }

        protected void allowAdjustmentType(String type) {
            synchronized (mLock) {
                mAllowedAdjustments.add(type);
            }
            for (final ManagedServiceInfo info : NotificationAssistants.this.getServices()) {
                mHandler.post(() -> notifyCapabilitiesChanged(info));
            }
        }

        protected void disallowAdjustmentType(String type) {
            synchronized (mLock) {
                mAllowedAdjustments.remove(type);
            }
            for (final ManagedServiceInfo info : NotificationAssistants.this.getServices()) {
                    mHandler.post(() -> notifyCapabilitiesChanged(info));
            }
        }

        protected List<String> getAllowedAssistantAdjustments() {
            synchronized (mLock) {
                List<String> types = new ArrayList<>();
                types.addAll(mAllowedAdjustments);
                return types;
            }
        }

        protected boolean isAdjustmentAllowed(String type) {
            synchronized (mLock) {
                return mAllowedAdjustments.contains(type);
            }
        }

        protected void onNotificationsSeenLocked(ArrayList<NotificationRecord> records) {
            // There should be only one, but it's a list, so while we enforce
            // singularity elsewhere, we keep it general here, to avoid surprises.
            for (final ManagedServiceInfo info : NotificationAssistants.this.getServices()) {
                ArrayList<String> keys = new ArrayList<>(records.size());
                for (NotificationRecord r : records) {
                    boolean sbnVisible = isVisibleToListener(r.sbn, info)
                            && info.isSameUser(r.getUserId());
                    if (sbnVisible) {
                        keys.add(r.getKey());
                    }
                }

                if (!keys.isEmpty()) {
                    mHandler.post(() -> notifySeen(info, keys));
                }
            }
        }

        boolean hasUserSet(int userId) {
            synchronized (mLock) {
                return mUserSetMap.getOrDefault(userId, false);
            }
        }

        void setUserSet(int userId, boolean set) {
            synchronized (mLock) {
                mUserSetMap.put(userId, set);
            }
        }

        @Override
        protected void writeExtraAttributes(XmlSerializer out, int userId) throws IOException {
            out.attribute(null, ATT_USER_SET, Boolean.toString(hasUserSet(userId)));
        }

        @Override
        protected void readExtraAttributes(String tag, XmlPullParser parser, int userId)
                throws IOException {
            boolean userSet = XmlUtils.readBooleanAttribute(parser, ATT_USER_SET, false);
            setUserSet(userId, userSet);
        }

        private void notifyCapabilitiesChanged(final ManagedServiceInfo info) {
            final INotificationListener assistant = (INotificationListener) info.service;
            try {
                assistant.onAllowedAdjustmentsChanged();
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify assistant (capabilities): " + assistant, ex);
            }
        }

        private void notifySeen(final ManagedServiceInfo info,
                final ArrayList<String> keys) {
            final INotificationListener assistant = (INotificationListener) info.service;
            try {
                assistant.onNotificationsSeen(keys);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify assistant (seen): " + assistant, ex);
            }
        }

        @GuardedBy("mNotificationLock")
        private void onNotificationEnqueuedLocked(final NotificationRecord r) {
            final boolean debug = isVerboseLogEnabled();
            if (debug) {
                Slog.v(TAG, "onNotificationEnqueuedLocked() called with: r = [" + r + "]");
            }
            final StatusBarNotification sbn = r.sbn;
            notifyAssistantLocked(
                    sbn,
                    true /* sameUserOnly */,
                    (assistant, sbnHolder) -> {
                        try {
                            if (debug) {
                                Slog.v(TAG,
                                        "calling onNotificationEnqueuedWithChannel " + sbnHolder);
                            }
                            assistant.onNotificationEnqueuedWithChannel(sbnHolder, r.getChannel());
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "unable to notify assistant (enqueued): " + assistant, ex);
                        }
                    });
        }

        @GuardedBy("mNotificationLock")
        void notifyAssistantExpansionChangedLocked(
                final StatusBarNotification sbn,
                final boolean isUserAction,
                final boolean isExpanded) {
            final String key = sbn.getKey();
            notifyAssistantLocked(
                    sbn,
                    false /* sameUserOnly */,
                    (assistant, sbnHolder) -> {
                        try {
                            assistant.onNotificationExpansionChanged(key, isUserAction, isExpanded);
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "unable to notify assistant (expanded): " + assistant, ex);
                        }
                    });
        }

        @GuardedBy("mNotificationLock")
        void notifyAssistantNotificationDirectReplyLocked(
                final StatusBarNotification sbn) {
            final String key = sbn.getKey();
            notifyAssistantLocked(
                    sbn,
                    false /* sameUserOnly */,
                    (assistant, sbnHolder) -> {
                        try {
                            assistant.onNotificationDirectReply(key);
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "unable to notify assistant (expanded): " + assistant, ex);
                        }
                    });
        }

        @GuardedBy("mNotificationLock")
        void notifyAssistantSuggestedReplySent(
                final StatusBarNotification sbn, CharSequence reply, boolean generatedByAssistant) {
            final String key = sbn.getKey();
            notifyAssistantLocked(
                    sbn,
                    false /* sameUserOnly */,
                    (assistant, sbnHolder) -> {
                        try {
                            assistant.onSuggestedReplySent(
                                    key,
                                    reply,
                                    generatedByAssistant
                                            ? NotificationAssistantService.SOURCE_FROM_ASSISTANT
                                            : NotificationAssistantService.SOURCE_FROM_APP);
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "unable to notify assistant (snoozed): " + assistant, ex);
                        }
                    });
        }

        @GuardedBy("mNotificationLock")
        void notifyAssistantActionClicked(
                final StatusBarNotification sbn, int actionIndex, Notification.Action action,
                boolean generatedByAssistant) {
            final String key = sbn.getKey();
            notifyAssistantLocked(
                    sbn,
                    false /* sameUserOnly */,
                    (assistant, sbnHolder) -> {
                        try {
                            assistant.onActionClicked(
                                    key,
                                    action,
                                    generatedByAssistant
                                            ? NotificationAssistantService.SOURCE_FROM_ASSISTANT
                                            : NotificationAssistantService.SOURCE_FROM_APP);
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "unable to notify assistant (snoozed): " + assistant, ex);
                        }
                    });
        }

        /**
         * asynchronously notify the assistant that a notification has been snoozed until a
         * context
         */
        @GuardedBy("mNotificationLock")
        private void notifyAssistantSnoozedLocked(
                final StatusBarNotification sbn, final String snoozeCriterionId) {
            notifyAssistantLocked(
                    sbn,
                    false /* sameUserOnly */,
                    (assistant, sbnHolder) -> {
                        try {
                            assistant.onNotificationSnoozedUntilContext(
                                    sbnHolder, snoozeCriterionId);
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "unable to notify assistant (snoozed): " + assistant, ex);
                        }
                    });
        }

        /**
         * Notifies the assistant something about the specified notification, only assistant
         * that is visible to the notification will be notified.
         *
         * @param sbn          the notification object that the update is about.
         * @param sameUserOnly should the update  be sent to the assistant in the same user only.
         * @param callback     the callback that provides the assistant to be notified, executed
         *                     in WorkerHandler.
         */
        @GuardedBy("mNotificationLock")
        private void notifyAssistantLocked(
                final StatusBarNotification sbn,
                boolean sameUserOnly,
                BiConsumer<INotificationListener, StatusBarNotificationHolder> callback) {
            TrimCache trimCache = new TrimCache(sbn);
            // There should be only one, but it's a list, so while we enforce
            // singularity elsewhere, we keep it general here, to avoid surprises.

            final boolean debug = isVerboseLogEnabled();
            if (debug) {
                Slog.v(TAG,
                        "notifyAssistantLocked() called with: sbn = [" + sbn + "], sameUserOnly = ["
                                + sameUserOnly + "], callback = [" + callback + "]");
            }
            for (final ManagedServiceInfo info : NotificationAssistants.this.getServices()) {
                boolean sbnVisible = isVisibleToListener(sbn, info)
                        && (!sameUserOnly || info.isSameUser(sbn.getUserId()));
                if (debug) {
                    Slog.v(TAG, "notifyAssistantLocked info=" + info + " snbVisible=" + sbnVisible);
                }
                if (!sbnVisible) {
                    continue;
                }
                final INotificationListener assistant = (INotificationListener) info.service;
                final StatusBarNotification sbnToPost = trimCache.ForListener(info);
                final StatusBarNotificationHolder sbnHolder =
                        new StatusBarNotificationHolder(sbnToPost);
                mHandler.post(() -> callback.accept(assistant, sbnHolder));
            }
        }

        public boolean isEnabled() {
            return !getServices().isEmpty();
        }

        protected void resetDefaultAssistantsIfNecessary() {
            final List<UserInfo> activeUsers = mUm.getUsers(true);
            for (UserInfo userInfo : activeUsers) {
                int userId = userInfo.getUserHandle().getIdentifier();
                if (!hasUserSet(userId)) {
                    Slog.d(TAG, "Approving default notification assistant for user " + userId);
                    setDefaultAssistantForUser(userId);
                }
            }
        }

        @Override
        protected void setPackageOrComponentEnabled(String pkgOrComponent, int userId,
                boolean isPrimary, boolean enabled) {
            // Ensures that only one component is enabled at a time
            if (enabled) {
                List<ComponentName> allowedComponents = getAllowedComponents(userId);
                if (!allowedComponents.isEmpty()) {
                    ComponentName currentComponent = CollectionUtils.firstOrNull(allowedComponents);
                    if (currentComponent.flattenToString().equals(pkgOrComponent)) return;
                    setNotificationAssistantAccessGrantedForUserInternal(
                            currentComponent, userId, false);
                }
            }
            super.setPackageOrComponentEnabled(pkgOrComponent, userId, isPrimary, enabled);
        }

        @Override
        public void dump(PrintWriter pw, DumpFilter filter) {
            super.dump(pw, filter);
            pw.println("    Has user set:");
            synchronized (mLock) {
                Set<Integer> userIds = mUserSetMap.keySet();
                for (int userId : userIds) {
                    pw.println("      userId=" + userId + " value=" + mUserSetMap.get(userId));
                }
            }
        }

        private boolean isVerboseLogEnabled() {
            return Log.isLoggable("notification_assistant", Log.VERBOSE);
        }
    }

    public class NotificationListeners extends ManagedServices {
        static final String TAG_ENABLED_NOTIFICATION_LISTENERS = "enabled_listeners";

        private final ArraySet<ManagedServiceInfo> mLightTrimListeners = new ArraySet<>();

        public NotificationListeners(IPackageManager pm) {
            super(getContext(), mNotificationLock, mUserProfiles, pm);

        }

        @Override
        protected int getBindFlags() {
            // Most of the same flags as the base, but also add BIND_NOT_PERCEPTIBLE
            // because too many 3P apps could be kept in memory as notification listeners and
            // cause extreme memory pressure.
            // TODO: Change the binding lifecycle of NotificationListeners to avoid this situation.
            return BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE
                    | BIND_NOT_PERCEPTIBLE | BIND_ALLOW_WHITELIST_MANAGEMENT;
        }

        @Override
        protected Config getConfig() {
            Config c = new Config();
            c.caption = "notification listener";
            c.serviceInterface = NotificationListenerService.SERVICE_INTERFACE;
            c.xmlTag = TAG_ENABLED_NOTIFICATION_LISTENERS;
            c.secureSettingName = Settings.Secure.ENABLED_NOTIFICATION_LISTENERS;
            c.bindPermission = android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE;
            c.settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS;
            c.clientLabel = R.string.notification_listener_binding_label;
            return c;
        }

        @Override
        protected IInterface asInterface(IBinder binder) {
            return INotificationListener.Stub.asInterface(binder);
        }

        @Override
        protected boolean checkType(IInterface service) {
            return service instanceof INotificationListener;
        }

        @Override
        public void onServiceAdded(ManagedServiceInfo info) {
            final INotificationListener listener = (INotificationListener) info.service;
            final NotificationRankingUpdate update;
            synchronized (mNotificationLock) {
                update = makeRankingUpdateLocked(info);
            }
            try {
                listener.onListenerConnected(update);
            } catch (RemoteException e) {
                // we tried
            }
        }

        @Override
        @GuardedBy("mNotificationLock")
        protected void onServiceRemovedLocked(ManagedServiceInfo removed) {
            if (removeDisabledHints(removed)) {
                updateListenerHintsLocked();
                updateEffectsSuppressorLocked();
            }
            mLightTrimListeners.remove(removed);
        }

        @Override
        protected String getRequiredPermission() {
            return null;
        }

        @GuardedBy("mNotificationLock")
        public void setOnNotificationPostedTrimLocked(ManagedServiceInfo info, int trim) {
            if (trim == TRIM_LIGHT) {
                mLightTrimListeners.add(info);
            } else {
                mLightTrimListeners.remove(info);
            }
        }

        public int getOnNotificationPostedTrim(ManagedServiceInfo info) {
            return mLightTrimListeners.contains(info) ? TRIM_LIGHT : TRIM_FULL;
        }

        public void onStatusBarIconsBehaviorChanged(boolean hideSilentStatusIcons) {
            for (final ManagedServiceInfo info : getServices()) {
                mHandler.post(() -> {
                    final INotificationListener listener = (INotificationListener) info.service;
                     try {
                        listener.onStatusBarIconsBehaviorChanged(hideSilentStatusIcons);
                    } catch (RemoteException ex) {
                        Slog.e(TAG, "unable to notify listener "
                                + "(hideSilentStatusIcons): " + listener, ex);
                    }
                });
            }
        }

        /**
         * asynchronously notify all listeners about a new notification
         *
         * <p>
         * Also takes care of removing a notification that has been visible to a listener before,
         * but isn't anymore.
         */
        @GuardedBy("mNotificationLock")
        public void notifyPostedLocked(NotificationRecord r, NotificationRecord old) {
            notifyPostedLocked(r, old, true);
        }

        /**
         * @param notifyAllListeners notifies all listeners if true, else only notifies listeners
         *                           targetting <= O_MR1
         */
        @GuardedBy("mNotificationLock")
        private void notifyPostedLocked(NotificationRecord r, NotificationRecord old,
                boolean notifyAllListeners) {
            // Lazily initialized snapshots of the notification.
            StatusBarNotification sbn = r.sbn;
            StatusBarNotification oldSbn = (old != null) ? old.sbn : null;
            TrimCache trimCache = new TrimCache(sbn);

            for (final ManagedServiceInfo info : getServices()) {
                boolean sbnVisible = isVisibleToListener(sbn, info);
                boolean oldSbnVisible = oldSbn != null ? isVisibleToListener(oldSbn, info) : false;
                // This notification hasn't been and still isn't visible -> ignore.
                if (!oldSbnVisible && !sbnVisible) {
                    continue;
                }
                // If the notification is hidden, don't notifyPosted listeners targeting < P.
                // Instead, those listeners will receive notifyPosted when the notification is
                // unhidden.
                if (r.isHidden() && info.targetSdkVersion < Build.VERSION_CODES.P) {
                    continue;
                }

                // If we shouldn't notify all listeners, this means the hidden state of
                // a notification was changed.  Don't notifyPosted listeners targeting >= P.
                // Instead, those listeners will receive notifyRankingUpdate.
                if (!notifyAllListeners && info.targetSdkVersion >= Build.VERSION_CODES.P) {
                    continue;
                }

                final NotificationRankingUpdate update = makeRankingUpdateLocked(info);

                // This notification became invisible -> remove the old one.
                if (oldSbnVisible && !sbnVisible) {
                    final StatusBarNotification oldSbnLightClone = oldSbn.cloneLight();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyRemoved(
                                    info, oldSbnLightClone, update, null, REASON_USER_STOPPED);
                        }
                    });
                    continue;
                }

                // Grant access before listener is notified
                final int targetUserId = (info.userid == UserHandle.USER_ALL)
                        ? UserHandle.USER_SYSTEM : info.userid;
                updateUriPermissions(r, old, info.component.getPackageName(), targetUserId);

                final StatusBarNotification sbnToPost = trimCache.ForListener(info);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyPosted(info, sbnToPost, update);
                    }
                });
            }
        }

        /**
         * asynchronously notify all listeners about a removed notification
         */
        @GuardedBy("mNotificationLock")
        public void notifyRemovedLocked(NotificationRecord r, int reason,
                NotificationStats notificationStats) {
            final StatusBarNotification sbn = r.sbn;

            // make a copy in case changes are made to the underlying Notification object
            // NOTE: this copy is lightweight: it doesn't include heavyweight parts of the
            // notification
            final StatusBarNotification sbnLight = sbn.cloneLight();
            for (final ManagedServiceInfo info : getServices()) {
                if (!isVisibleToListener(sbn, info)) {
                    continue;
                }

                // don't notifyRemoved for listeners targeting < P
                // if not for reason package suspended
                if (r.isHidden() && reason != REASON_PACKAGE_SUSPENDED
                        && info.targetSdkVersion < Build.VERSION_CODES.P) {
                    continue;
                }

                // don't notifyRemoved for listeners targeting >= P
                // if the reason is package suspended
                if (reason == REASON_PACKAGE_SUSPENDED
                        && info.targetSdkVersion >= Build.VERSION_CODES.P) {
                    continue;
                }

                // Only assistants can get stats
                final NotificationStats stats = mAssistants.isServiceTokenValidLocked(info.service)
                        ? notificationStats : null;
                final NotificationRankingUpdate update = makeRankingUpdateLocked(info);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyRemoved(info, sbnLight, update, stats, reason);
                    }
                });
            }

            // Revoke access after all listeners have been updated
            mHandler.post(() -> {
                updateUriPermissions(null, r, null, UserHandle.USER_SYSTEM);
            });
        }

        /**
         * Asynchronously notify all listeners about a reordering of notifications
         * unless changedHiddenNotifications is populated.
         * If changedHiddenNotifications is populated, there was a change in the hidden state
         * of the notifications.  In this case, we only send updates to listeners that
         * target >= P.
         */
        @GuardedBy("mNotificationLock")
        public void notifyRankingUpdateLocked(List<NotificationRecord> changedHiddenNotifications) {
            boolean isHiddenRankingUpdate = changedHiddenNotifications != null
                    && changedHiddenNotifications.size() > 0;

            for (final ManagedServiceInfo serviceInfo : getServices()) {
                if (!serviceInfo.isEnabledForCurrentProfiles()) {
                    continue;
                }

                boolean notifyThisListener = false;
                if (isHiddenRankingUpdate && serviceInfo.targetSdkVersion >=
                        Build.VERSION_CODES.P) {
                    for (NotificationRecord rec : changedHiddenNotifications) {
                        if (isVisibleToListener(rec.sbn, serviceInfo)) {
                            notifyThisListener = true;
                            break;
                        }
                    }
                }

                if (notifyThisListener || !isHiddenRankingUpdate) {
                    final NotificationRankingUpdate update = makeRankingUpdateLocked(
                            serviceInfo);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyRankingUpdate(serviceInfo, update);
                        }
                    });
                }
            }
        }

        @GuardedBy("mNotificationLock")
        public void notifyListenerHintsChangedLocked(final int hints) {
            for (final ManagedServiceInfo serviceInfo : getServices()) {
                if (!serviceInfo.isEnabledForCurrentProfiles()) {
                    continue;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyListenerHintsChanged(serviceInfo, hints);
                    }
                });
            }
        }

        /**
         * asynchronously notify relevant listeners their notification is hidden
         * NotificationListenerServices that target P+:
         *      NotificationListenerService#notifyRankingUpdateLocked()
         * NotificationListenerServices that target <= P:
         *      NotificationListenerService#notifyRemovedLocked() with REASON_PACKAGE_SUSPENDED.
         */
        @GuardedBy("mNotificationLock")
        public void notifyHiddenLocked(List<NotificationRecord> changedNotifications) {
            if (changedNotifications == null || changedNotifications.size() == 0) {
                return;
            }

            notifyRankingUpdateLocked(changedNotifications);

            // for listeners that target < P, notifyRemoveLocked
            int numChangedNotifications = changedNotifications.size();
            for (int i = 0; i < numChangedNotifications; i++) {
                NotificationRecord rec = changedNotifications.get(i);
                mListeners.notifyRemovedLocked(rec, REASON_PACKAGE_SUSPENDED, rec.getStats());
            }
        }

        /**
         * asynchronously notify relevant listeners their notification is unhidden
         * NotificationListenerServices that target P+:
         *      NotificationListenerService#notifyRankingUpdateLocked()
         * NotificationListenerServices that target <= P:
         *      NotificationListeners#notifyPostedLocked()
         */
        @GuardedBy("mNotificationLock")
        public void notifyUnhiddenLocked(List<NotificationRecord> changedNotifications) {
            if (changedNotifications == null || changedNotifications.size() == 0) {
                return;
            }

            notifyRankingUpdateLocked(changedNotifications);

            // for listeners that target < P, notifyPostedLocked
            int numChangedNotifications = changedNotifications.size();
            for (int i = 0; i < numChangedNotifications; i++) {
                NotificationRecord rec = changedNotifications.get(i);
                mListeners.notifyPostedLocked(rec, rec, false);
            }
        }

        public void notifyInterruptionFilterChanged(final int interruptionFilter) {
            for (final ManagedServiceInfo serviceInfo : getServices()) {
                if (!serviceInfo.isEnabledForCurrentProfiles()) {
                    continue;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyInterruptionFilterChanged(serviceInfo, interruptionFilter);
                    }
                });
            }
        }

        protected void notifyNotificationChannelChanged(final String pkg, final UserHandle user,
                final NotificationChannel channel, final int modificationType) {
            if (channel == null) {
                return;
            }
            for (final ManagedServiceInfo serviceInfo : getServices()) {
                if (!serviceInfo.enabledAndUserMatches(UserHandle.getCallingUserId())) {
                    continue;
                }

                BackgroundThread.getHandler().post(() -> {
                    if (hasCompanionDevice(serviceInfo)) {
                        notifyNotificationChannelChanged(
                                serviceInfo, pkg, user, channel, modificationType);
                    }
                });
            }
        }

        protected void notifyNotificationChannelGroupChanged(
                final String pkg, final UserHandle user, final NotificationChannelGroup group,
                final int modificationType) {
            if (group == null) {
                return;
            }
            for (final ManagedServiceInfo serviceInfo : getServices()) {
                if (!serviceInfo.enabledAndUserMatches(UserHandle.getCallingUserId())) {
                    continue;
                }

                BackgroundThread.getHandler().post(() -> {
                    if (hasCompanionDevice(serviceInfo)) {
                        notifyNotificationChannelGroupChanged(
                                serviceInfo, pkg, user, group, modificationType);
                    }
                });
            }
        }

        private void notifyPosted(final ManagedServiceInfo info,
                final StatusBarNotification sbn, NotificationRankingUpdate rankingUpdate) {
            final INotificationListener listener = (INotificationListener) info.service;
            StatusBarNotificationHolder sbnHolder = new StatusBarNotificationHolder(sbn);
            try {
                listener.onNotificationPosted(sbnHolder, rankingUpdate);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (posted): " + listener, ex);
            }
        }

        private void notifyRemoved(ManagedServiceInfo info, StatusBarNotification sbn,
                NotificationRankingUpdate rankingUpdate, NotificationStats stats, int reason) {
            if (!info.enabledAndUserMatches(sbn.getUserId())) {
                return;
            }
            final INotificationListener listener = (INotificationListener) info.service;
            StatusBarNotificationHolder sbnHolder = new StatusBarNotificationHolder(sbn);
            try {
                listener.onNotificationRemoved(sbnHolder, rankingUpdate, stats, reason);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (removed): " + listener, ex);
            }
        }

        private void notifyRankingUpdate(ManagedServiceInfo info,
                                         NotificationRankingUpdate rankingUpdate) {
            final INotificationListener listener = (INotificationListener) info.service;
            try {
                listener.onNotificationRankingUpdate(rankingUpdate);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (ranking update): " + listener, ex);
            }
        }

        private void notifyListenerHintsChanged(ManagedServiceInfo info, int hints) {
            final INotificationListener listener = (INotificationListener) info.service;
            try {
                listener.onListenerHintsChanged(hints);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (listener hints): " + listener, ex);
            }
        }

        private void notifyInterruptionFilterChanged(ManagedServiceInfo info,
                int interruptionFilter) {
            final INotificationListener listener = (INotificationListener) info.service;
            try {
                listener.onInterruptionFilterChanged(interruptionFilter);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (interruption filter): " + listener, ex);
            }
        }

        void notifyNotificationChannelChanged(ManagedServiceInfo info,
                final String pkg, final UserHandle user, final NotificationChannel channel,
                final int modificationType) {
            final INotificationListener listener = (INotificationListener) info.service;
            try {
                listener.onNotificationChannelModification(pkg, user, channel, modificationType);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (channel changed): " + listener, ex);
            }
        }

        private void notifyNotificationChannelGroupChanged(ManagedServiceInfo info,
                final String pkg, final UserHandle user, final NotificationChannelGroup group,
                final int modificationType) {
            final INotificationListener listener = (INotificationListener) info.service;
            try {
                listener.onNotificationChannelGroupModification(pkg, user, group, modificationType);
            } catch (RemoteException ex) {
                Slog.e(TAG, "unable to notify listener (channel group changed): " + listener, ex);
            }
        }

        public boolean isListenerPackage(String packageName) {
            if (packageName == null) {
                return false;
            }
            // TODO: clean up locking object later
            synchronized (mNotificationLock) {
                for (final ManagedServiceInfo serviceInfo : getServices()) {
                    if (packageName.equals(serviceInfo.component.getPackageName())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    class RoleObserver implements OnRoleHoldersChangedListener {
        // Role name : user id : list of approved packages
        private ArrayMap<String, ArrayMap<Integer, ArraySet<String>>> mNonBlockableDefaultApps;

        private final RoleManager mRm;
        private final IPackageManager mPm;
        private final Executor mExecutor;

        RoleObserver(@NonNull RoleManager roleManager,
                @NonNull IPackageManager pkgMgr,
                @NonNull @CallbackExecutor Executor executor) {
            mRm = roleManager;
            mPm = pkgMgr;
            mExecutor = executor;
        }

        public void init() {
            List<UserInfo> users = mUm.getUsers();
            mNonBlockableDefaultApps = new ArrayMap<>();
            for (int i = 0; i < NON_BLOCKABLE_DEFAULT_ROLES.length; i++) {
                final ArrayMap<Integer, ArraySet<String>> userToApprovedList = new ArrayMap<>();
                mNonBlockableDefaultApps.put(NON_BLOCKABLE_DEFAULT_ROLES[i], userToApprovedList);
                for (int j = 0; j < users.size(); j++) {
                    Integer userId = users.get(j).getUserHandle().getIdentifier();
                    ArraySet<String> approvedForUserId = new ArraySet<>(mRm.getRoleHoldersAsUser(
                            NON_BLOCKABLE_DEFAULT_ROLES[i], UserHandle.of(userId)));
                    ArraySet<Pair<String, Integer>> approvedAppUids = new ArraySet<>();
                    for (String pkg : approvedForUserId) {
                        approvedAppUids.add(new Pair(pkg, getUidForPackage(pkg, userId)));
                    }
                    userToApprovedList.put(userId, approvedForUserId);
                    mPreferencesHelper.updateDefaultApps(userId, null, approvedAppUids);
                }
            }

            mRm.addOnRoleHoldersChangedListenerAsUser(mExecutor, this, UserHandle.ALL);
        }

        @VisibleForTesting
        public boolean isApprovedPackageForRoleForUser(String role, String pkg, int userId) {
            return mNonBlockableDefaultApps.get(role).get(userId).contains(pkg);
        }

        /**
         * Convert the assistant-role holder into settings. The rest of the system uses the
         * settings.
         *
         * @param roleName the name of the role whose holders are changed
         * @param user the user for this role holder change
         */
        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            // we only care about a couple of the roles they'll tell us about
            boolean relevantChange = false;
            for (int i = 0; i < NON_BLOCKABLE_DEFAULT_ROLES.length; i++) {
                if (NON_BLOCKABLE_DEFAULT_ROLES[i].equals(roleName)) {
                    relevantChange = true;
                    break;
                }
            }

            if (!relevantChange) {
                return;
            }

            ArraySet<String> roleHolders = new ArraySet<>(mRm.getRoleHoldersAsUser(roleName, user));

            // find the diff
            ArrayMap<Integer, ArraySet<String>> prevApprovedForRole =
                    mNonBlockableDefaultApps.getOrDefault(roleName, new ArrayMap<>());
            ArraySet<String> previouslyApproved =
                    prevApprovedForRole.getOrDefault(user.getIdentifier(), new ArraySet<>());

            ArraySet<String> toRemove = new ArraySet<>();
            ArraySet<Pair<String, Integer>> toAdd = new ArraySet<>();

            for (String previous : previouslyApproved) {
                if (!roleHolders.contains(previous)) {
                    toRemove.add(previous);
                }
            }
            for (String nowApproved : roleHolders) {
                if (!previouslyApproved.contains(nowApproved)) {
                    toAdd.add(new Pair(nowApproved,
                            getUidForPackage(nowApproved, user.getIdentifier())));
                }
            }

            // store newly approved apps
            prevApprovedForRole.put(user.getIdentifier(), roleHolders);
            mNonBlockableDefaultApps.put(roleName, prevApprovedForRole);

            // update what apps can be blocked
            mPreferencesHelper.updateDefaultApps(user.getIdentifier(), toRemove, toAdd);

            // RoleManager is the source of truth for this data so we don't need to trigger a
            // write of the notification policy xml for this change
        }

        private int getUidForPackage(String pkg, int userId) {
            try {
                return mPm.getPackageUid(pkg, MATCH_ALL, userId);
            } catch (RemoteException e) {
                Slog.e(TAG, "role manager has bad default " + pkg + " " + userId);
            }
            return -1;
        }
    }

    public static final class DumpFilter {
        public boolean filtered = false;
        public String pkgFilter;
        public boolean zen;
        public long since;
        public boolean stats;
        public boolean rvStats;
        public boolean redact = true;
        public boolean proto = false;
        public boolean criticalPriority = false;
        public boolean normalPriority = false;

        @NonNull
        public static DumpFilter parseFromArguments(String[] args) {
            final DumpFilter filter = new DumpFilter();
            for (int ai = 0; ai < args.length; ai++) {
                final String a = args[ai];
                if ("--proto".equals(a)) {
                    filter.proto = true;
                } else if ("--noredact".equals(a) || "--reveal".equals(a)) {
                    filter.redact = false;
                } else if ("p".equals(a) || "pkg".equals(a) || "--package".equals(a)) {
                    if (ai < args.length-1) {
                        ai++;
                        filter.pkgFilter = args[ai].trim().toLowerCase();
                        if (filter.pkgFilter.isEmpty()) {
                            filter.pkgFilter = null;
                        } else {
                            filter.filtered = true;
                        }
                    }
                } else if ("--zen".equals(a) || "zen".equals(a)) {
                    filter.filtered = true;
                    filter.zen = true;
                } else if ("--stats".equals(a)) {
                    filter.stats = true;
                    if (ai < args.length-1) {
                        ai++;
                        filter.since = Long.parseLong(args[ai]);
                    } else {
                        filter.since = 0;
                    }
                } else if ("--remote-view-stats".equals(a)) {
                    filter.rvStats = true;
                    if (ai < args.length-1) {
                        ai++;
                        filter.since = Long.parseLong(args[ai]);
                    } else {
                        filter.since = 0;
                    }
                } else if (PRIORITY_ARG.equals(a)) {
                    // Bugreport will call the service twice with priority arguments, first to dump
                    // critical sections and then non critical ones. Set approriate filters
                    // to generate the desired data.
                    if (ai < args.length - 1) {
                        ai++;
                        switch (args[ai]) {
                            case PRIORITY_ARG_CRITICAL:
                                filter.criticalPriority = true;
                                break;
                            case PRIORITY_ARG_NORMAL:
                                filter.normalPriority = true;
                                break;
                        }
                    }
                }
            }
            return filter;
        }

        public boolean matches(StatusBarNotification sbn) {
            if (!filtered) return true;
            return zen ? true : sbn != null
                    && (matches(sbn.getPackageName()) || matches(sbn.getOpPkg()));
        }

        public boolean matches(ComponentName component) {
            if (!filtered) return true;
            return zen ? true : component != null && matches(component.getPackageName());
        }

        public boolean matches(String pkg) {
            if (!filtered) return true;
            return zen ? true : pkg != null && pkg.toLowerCase().contains(pkgFilter);
        }

        @Override
        public String toString() {
            return stats ? "stats" : zen ? "zen" : ('\'' + pkgFilter + '\'');
        }
    }

    @VisibleForTesting
    void resetAssistantUserSet(int userId) {
        checkCallerIsSystemOrShell();
        mAssistants.setUserSet(userId, false);
        handleSavePolicyFile();
    }

    @VisibleForTesting
    @Nullable
    ComponentName getApprovedAssistant(int userId) {
        checkCallerIsSystemOrShell();
        List<ComponentName> allowedComponents = mAssistants.getAllowedComponents(userId);
        return CollectionUtils.firstOrNull(allowedComponents);
    }

    @VisibleForTesting
    protected void simulatePackageSuspendBroadcast(boolean suspend, String pkg) {
        checkCallerIsSystemOrShell();
        // only use for testing: mimic receive broadcast that package is (un)suspended
        // but does not actually (un)suspend the package
        final Bundle extras = new Bundle();
        extras.putStringArray(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                new String[]{pkg});

        final String action = suspend ? Intent.ACTION_PACKAGES_SUSPENDED
                : Intent.ACTION_PACKAGES_UNSUSPENDED;
        final Intent intent = new Intent(action);
        intent.putExtras(extras);

        mPackageIntentReceiver.onReceive(getContext(), intent);
    }

    @VisibleForTesting
    protected void simulatePackageDistractionBroadcast(int flag, String[] pkgs) {
        checkCallerIsSystemOrShell();
        // only use for testing: mimic receive broadcast that package is (un)distracting
        // but does not actually register that info with packagemanager
        final Bundle extras = new Bundle();
        extras.putStringArray(Intent.EXTRA_CHANGED_PACKAGE_LIST, pkgs);
        extras.putInt(Intent.EXTRA_DISTRACTION_RESTRICTIONS, flag);

        final Intent intent = new Intent(Intent.ACTION_DISTRACTING_PACKAGES_CHANGED);
        intent.putExtras(extras);

        mPackageIntentReceiver.onReceive(getContext(), intent);
    }

    /**
     * Wrapper for a StatusBarNotification object that allows transfer across a oneway
     * binder without sending large amounts of data over a oneway transaction.
     */
    private static final class StatusBarNotificationHolder
            extends IStatusBarNotificationHolder.Stub {
        private StatusBarNotification mValue;

        public StatusBarNotificationHolder(StatusBarNotification value) {
            mValue = value;
        }

        /** Get the held value and clear it. This function should only be called once per holder */
        @Override
        public StatusBarNotification get() {
            StatusBarNotification value = mValue;
            mValue = null;
            return value;
        }
    }

    private void writeSecureNotificationsPolicy(XmlSerializer out) throws IOException {
        out.startTag(null, LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_TAG);
        out.attribute(null, LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_VALUE,
                Boolean.toString(mLockScreenAllowSecureNotifications));
        out.endTag(null, LOCKSCREEN_ALLOW_SECURE_NOTIFICATIONS_TAG);
    }

    private static boolean safeBoolean(String val, boolean defValue) {
        if (TextUtils.isEmpty(val)) return defValue;
        return Boolean.parseBoolean(val);
    }
}
