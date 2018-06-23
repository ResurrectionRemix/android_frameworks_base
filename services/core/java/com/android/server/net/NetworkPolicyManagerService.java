/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.net;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.Manifest.permission.MANAGE_NETWORK_POLICY;
import static android.Manifest.permission.MANAGE_SUBSCRIPTION_PLANS;
import static android.Manifest.permission.READ_NETWORK_USAGE_HISTORY;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_UID_REMOVED;
import static android.content.Intent.ACTION_USER_ADDED;
import static android.content.Intent.ACTION_USER_REMOVED;
import static android.content.Intent.EXTRA_UID;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkPolicy.LIMIT_DISABLED;
import static android.net.NetworkPolicy.SNOOZE_NEVER;
import static android.net.NetworkPolicy.WARNING_DISABLED;
import static android.net.NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE;
import static android.net.NetworkPolicyManager.FIREWALL_CHAIN_DOZABLE;
import static android.net.NetworkPolicyManager.FIREWALL_CHAIN_POWERSAVE;
import static android.net.NetworkPolicyManager.FIREWALL_CHAIN_STANDBY;
import static android.net.NetworkPolicyManager.FIREWALL_RULE_ALLOW;
import static android.net.NetworkPolicyManager.FIREWALL_RULE_DEFAULT;
import static android.net.NetworkPolicyManager.FIREWALL_RULE_DENY;
import static android.net.NetworkPolicyManager.MASK_ALL_NETWORKS;
import static android.net.NetworkPolicyManager.MASK_METERED_NETWORKS;
import static android.net.NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND;
import static android.net.NetworkPolicyManager.POLICY_NONE;
import static android.net.NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND;
import static android.net.NetworkPolicyManager.POLICY_REJECT_ON_DATA;
import static android.net.NetworkPolicyManager.POLICY_REJECT_ON_WLAN;
import static android.net.NetworkPolicyManager.RULE_ALLOW_ALL;
import static android.net.NetworkPolicyManager.RULE_ALLOW_METERED;
import static android.net.NetworkPolicyManager.RULE_NONE;
import static android.net.NetworkPolicyManager.RULE_REJECT_ALL;
import static android.net.NetworkPolicyManager.RULE_REJECT_METERED;
import static android.net.NetworkPolicyManager.RULE_TEMPORARY_ALLOW_METERED;
import static android.net.NetworkPolicyManager.isProcStateAllowedWhileIdleOrPowerSaveMode;
import static android.net.NetworkPolicyManager.isProcStateAllowedWhileOnRestrictBackground;
import static android.net.NetworkPolicyManager.resolveNetworkId;
import static android.net.NetworkPolicyManager.uidPoliciesToString;
import static android.net.NetworkPolicyManager.uidRulesToString;
import static android.net.NetworkTemplate.MATCH_MOBILE_3G_LOWER;
import static android.net.NetworkTemplate.MATCH_MOBILE_4G;
import static android.net.NetworkTemplate.MATCH_MOBILE_ALL;
import static android.net.NetworkTemplate.MATCH_WIFI;
import static android.net.NetworkTemplate.buildTemplateMobileAll;
import static android.net.TrafficStats.MB_IN_BYTES;
import static android.telephony.CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED;
import static android.telephony.CarrierConfigManager.DATA_CYCLE_THRESHOLD_DISABLED;
import static android.telephony.CarrierConfigManager.DATA_CYCLE_USE_PLATFORM_DEFAULT;
import static android.text.format.DateUtils.DAY_IN_MILLIS;

import static com.android.internal.util.ArrayUtils.appendInt;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.internal.util.XmlUtils.readBooleanAttribute;
import static com.android.internal.util.XmlUtils.readIntAttribute;
import static com.android.internal.util.XmlUtils.readLongAttribute;
import static com.android.internal.util.XmlUtils.readStringAttribute;
import static com.android.internal.util.XmlUtils.writeBooleanAttribute;
import static com.android.internal.util.XmlUtils.writeIntAttribute;
import static com.android.internal.util.XmlUtils.writeLongAttribute;
import static com.android.internal.util.XmlUtils.writeStringAttribute;
import static com.android.server.NetworkManagementService.LIMIT_GLOBAL_ALERT;
import static com.android.server.net.NetworkStatsService.ACTION_NETWORK_STATS_UPDATED;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.IUidObserver;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManagerInternal;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.IConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.INetworkPolicyListener;
import android.net.INetworkPolicyManager;
import android.net.INetworkStatsService;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkIdentity;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkQuotaInfo;
import android.net.NetworkRequest;
import android.net.NetworkState;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IDeviceIdleController;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.MessageQueue.IdleHandler;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Log;
import android.util.NtpTrustedTime;
import android.util.Pair;
import android.util.RecurrenceRule;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.TrustedTime;
import android.util.Xml;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;
import com.android.server.DeviceIdleController;
import com.android.server.EventLogTags;
import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.SystemConfig;
import com.android.server.power.BatterySaverPolicy.ServiceType;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Service that maintains low-level network policy rules, using
 * {@link NetworkStatsService} statistics to drive those rules.
 * <p>
 * Derives active rules by combining a given policy with other system status,
 * and delivers to listeners, such as {@link ConnectivityManager}, for
 * enforcement.
 *
 * <p>
 * This class uses 2-3 locks to synchronize state:
 * <ul>
 * <li>{@code mUidRulesFirstLock}: used to guard state related to individual UIDs (such as firewall
 * rules).
 * <li>{@code mNetworkPoliciesSecondLock}: used to guard state related to network interfaces (such
 * as network policies).
 * <li>{@code allLocks}: not a "real" lock, but an indication (through @GuardedBy) that all locks
 * must be held.
 * </ul>
 *
 * <p>
 * As such, methods that require synchronization have the following prefixes:
 * <ul>
 * <li>{@code UL()}: require the "UID" lock ({@code mUidRulesFirstLock}).
 * <li>{@code NL()}: require the "Network" lock ({@code mNetworkPoliciesSecondLock}).
 * <li>{@code AL()}: require all locks, which must be obtained in order ({@code mUidRulesFirstLock}
 * first, then {@code mNetworkPoliciesSecondLock}, then {@code mYetAnotherGuardThirdLock}, etc..
 * </ul>
 */
public class NetworkPolicyManagerService extends INetworkPolicyManager.Stub {
    static final String TAG = "NetworkPolicy";
    private static final boolean LOGD = false;
    private static final boolean LOGV = false;

    private static final int VERSION_INIT = 1;
    private static final int VERSION_ADDED_SNOOZE = 2;
    private static final int VERSION_ADDED_RESTRICT_BACKGROUND = 3;
    private static final int VERSION_ADDED_METERED = 4;
    private static final int VERSION_SPLIT_SNOOZE = 5;
    private static final int VERSION_ADDED_TIMEZONE = 6;
    private static final int VERSION_ADDED_INFERRED = 7;
    private static final int VERSION_SWITCH_APP_ID = 8;
    private static final int VERSION_ADDED_NETWORK_ID = 9;
    private static final int VERSION_SWITCH_UID = 10;
    private static final int VERSION_ADDED_CYCLE = 11;
    private static final int VERSION_LATEST = VERSION_ADDED_CYCLE;

    /**
     * Max items written to {@link #ProcStateSeqHistory}.
     */
    @VisibleForTesting
    public static final int MAX_PROC_STATE_SEQ_HISTORY =
            ActivityManager.isLowRamDeviceStatic() ? 50 : 200;

    @VisibleForTesting
    public static final int TYPE_WARNING = SystemMessage.NOTE_NET_WARNING;
    @VisibleForTesting
    public static final int TYPE_LIMIT = SystemMessage.NOTE_NET_LIMIT;
    @VisibleForTesting
    public static final int TYPE_LIMIT_SNOOZED = SystemMessage.NOTE_NET_LIMIT_SNOOZED;

    private static final String TAG_POLICY_LIST = "policy-list";
    private static final String TAG_NETWORK_POLICY = "network-policy";
    private static final String TAG_SUBSCRIPTION_PLAN = "subscription-plan";
    private static final String TAG_UID_POLICY = "uid-policy";
    private static final String TAG_APP_POLICY = "app-policy";
    private static final String TAG_WHITELIST = "whitelist";
    private static final String TAG_RESTRICT_BACKGROUND = "restrict-background";
    private static final String TAG_REVOKED_RESTRICT_BACKGROUND = "revoked-restrict-background";

    private static final String ATTR_VERSION = "version";
    private static final String ATTR_RESTRICT_BACKGROUND = "restrictBackground";
    private static final String ATTR_NETWORK_TEMPLATE = "networkTemplate";
    private static final String ATTR_SUBSCRIBER_ID = "subscriberId";
    private static final String ATTR_NETWORK_ID = "networkId";
    @Deprecated private static final String ATTR_CYCLE_DAY = "cycleDay";
    @Deprecated private static final String ATTR_CYCLE_TIMEZONE = "cycleTimezone";
    private static final String ATTR_CYCLE_START = "cycleStart";
    private static final String ATTR_CYCLE_END = "cycleEnd";
    private static final String ATTR_CYCLE_PERIOD = "cyclePeriod";
    private static final String ATTR_WARNING_BYTES = "warningBytes";
    private static final String ATTR_LIMIT_BYTES = "limitBytes";
    private static final String ATTR_LAST_SNOOZE = "lastSnooze";
    private static final String ATTR_LAST_WARNING_SNOOZE = "lastWarningSnooze";
    private static final String ATTR_LAST_LIMIT_SNOOZE = "lastLimitSnooze";
    private static final String ATTR_METERED = "metered";
    private static final String ATTR_INFERRED = "inferred";
    private static final String ATTR_UID = "uid";
    private static final String ATTR_APP_ID = "appId";
    private static final String ATTR_POLICY = "policy";
    private static final String ATTR_SUB_ID = "subId";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_SUMMARY = "summary";
    private static final String ATTR_LIMIT_BEHAVIOR = "limitBehavior";
    private static final String ATTR_USAGE_BYTES = "usageBytes";
    private static final String ATTR_USAGE_TIME = "usageTime";
    private static final String ATTR_OWNER_PACKAGE = "ownerPackage";

    private static final String ACTION_ALLOW_BACKGROUND =
            "com.android.server.net.action.ALLOW_BACKGROUND";
    private static final String ACTION_SNOOZE_WARNING =
            "com.android.server.net.action.SNOOZE_WARNING";

    private static final long TIME_CACHE_MAX_AGE = DAY_IN_MILLIS;

    private static final int MSG_RULES_CHANGED = 1;
    private static final int MSG_METERED_IFACES_CHANGED = 2;
    private static final int MSG_LIMIT_REACHED = 5;
    private static final int MSG_RESTRICT_BACKGROUND_CHANGED = 6;
    private static final int MSG_ADVISE_PERSIST_THRESHOLD = 7;
    private static final int MSG_UPDATE_INTERFACE_QUOTA = 10;
    private static final int MSG_REMOVE_INTERFACE_QUOTA = 11;
    private static final int MSG_POLICIES_CHANGED = 13;
    private static final int MSG_RESET_FIREWALL_RULES_BY_UID = 15;

    private static final int UID_MSG_STATE_CHANGED = 100;
    private static final int UID_MSG_GONE = 101;

    private final Context mContext;
    private final IActivityManager mActivityManager;
    private final INetworkStatsService mNetworkStats;
    private final INetworkManagementService mNetworkManager;
    private UsageStatsManagerInternal mUsageStats;
    private final TrustedTime mTime;
    private final UserManager mUserManager;
    private final CarrierConfigManager mCarrierConfigManager;

    private IConnectivityManager mConnManager;
    private INotificationManager mNotifManager;
    private PowerManagerInternal mPowerManagerInternal;
    private IDeviceIdleController mDeviceIdleController;
    @GuardedBy("mUidRulesFirstLock")
    private PowerSaveState mRestrictBackgroundPowerState;

    // Store the status of restrict background before turning on battery saver.
    // Used to restore mRestrictBackground when battery saver is turned off.
    private boolean mRestrictBackgroundBeforeBsm;

    // See main javadoc for instructions on how to use these locks.
    final Object mUidRulesFirstLock = new Object();
    final Object mNetworkPoliciesSecondLock = new Object();

    @GuardedBy("allLocks") volatile boolean mSystemReady;

    @GuardedBy("mUidRulesFirstLock") volatile boolean mRestrictBackground;
    @GuardedBy("mUidRulesFirstLock") volatile boolean mRestrictPower;
    @GuardedBy("mUidRulesFirstLock") volatile boolean mDeviceIdleMode;
    // Store whether user flipped restrict background in battery saver mode
    @GuardedBy("mUidRulesFirstLock") volatile boolean mRestrictBackgroundChangedInBsm;

    private final boolean mSuppressDefaultPolicy;

    /** Defined network policies. */
    @GuardedBy("mNetworkPoliciesSecondLock")
    final ArrayMap<NetworkTemplate, NetworkPolicy> mNetworkPolicy = new ArrayMap<>();

    /** Map from subId to subscription plans. */
    @GuardedBy("mNetworkPoliciesSecondLock")
    final SparseArray<SubscriptionPlan[]> mSubscriptionPlans = new SparseArray<>();
    /** Map from subId to package name that owns subscription plans. */
    @GuardedBy("mNetworkPoliciesSecondLock")
    final SparseArray<String> mSubscriptionPlansOwner = new SparseArray<>();

    /** Defined UID policies. */
    @GuardedBy("mUidRulesFirstLock") final SparseIntArray mUidPolicy = new SparseIntArray();
    /** Currently derived rules for each UID. */
    @GuardedBy("mUidRulesFirstLock") final SparseIntArray mUidRules = new SparseIntArray();

    @GuardedBy("mUidRulesFirstLock")
    final SparseIntArray mUidFirewallStandbyRules = new SparseIntArray();
    @GuardedBy("mUidRulesFirstLock")
    final SparseIntArray mUidFirewallDozableRules = new SparseIntArray();
    @GuardedBy("mUidRulesFirstLock")
    final SparseIntArray mUidFirewallPowerSaveRules = new SparseIntArray();

    /** Set of states for the child firewall chains. True if the chain is active. */
    @GuardedBy("mUidRulesFirstLock")
    final SparseBooleanArray mFirewallChainStates = new SparseBooleanArray();

    /**
     * UIDs that have been white-listed to always be able to have network access
     * in power save mode, except device idle (doze) still applies.
     * TODO: An int array might be sufficient
     */
    @GuardedBy("mUidRulesFirstLock")
    private final SparseBooleanArray mPowerSaveWhitelistExceptIdleAppIds = new SparseBooleanArray();

    /**
     * UIDs that have been white-listed to always be able to have network access
     * in power save mode.
     * TODO: An int array might be sufficient
     */
    @GuardedBy("mUidRulesFirstLock")
    private final SparseBooleanArray mPowerSaveWhitelistAppIds = new SparseBooleanArray();

    @GuardedBy("mUidRulesFirstLock")
    private final SparseBooleanArray mPowerSaveTempWhitelistAppIds = new SparseBooleanArray();

    /**
     * UIDs that have been initially white-listed by system to avoid restricted background.
     */
    @GuardedBy("mUidRulesFirstLock")
    private final SparseBooleanArray mDefaultRestrictBackgroundWhitelistUids =
            new SparseBooleanArray();

    /**
     * UIDs that have been initially white-listed by system to avoid restricted background,
     * but later revoked by user.
     */
    @GuardedBy("mUidRulesFirstLock")
    private final SparseBooleanArray mRestrictBackgroundWhitelistRevokedUids =
            new SparseBooleanArray();

    /** Set of ifaces that are metered. */
    @GuardedBy("mNetworkPoliciesSecondLock")
    private ArraySet<String> mMeteredIfaces = new ArraySet<>();
    /** Set of over-limit templates that have been notified. */
    @GuardedBy("mNetworkPoliciesSecondLock")
    private final ArraySet<NetworkTemplate> mOverLimitNotified = new ArraySet<>();

    /** Set of currently active {@link Notification} tags. */
    @GuardedBy("mNetworkPoliciesSecondLock")
    private final ArraySet<NotificationId> mActiveNotifs = new ArraySet<>();

    /** Foreground at UID granularity. */
    @GuardedBy("mUidRulesFirstLock")
    final SparseIntArray mUidState = new SparseIntArray();

    /** Map from network ID to last observed meteredness state */
    @GuardedBy("mNetworkPoliciesSecondLock")
    private final SparseBooleanArray mNetworkMetered = new SparseBooleanArray();

    private final RemoteCallbackList<INetworkPolicyListener>
            mListeners = new RemoteCallbackList<>();

    final Handler mHandler;
    @VisibleForTesting
    public final Handler mUidEventHandler;

    private final ServiceThread mUidEventThread;

    @GuardedBy("allLocks")
    private final AtomicFile mPolicyFile;

    private final AppOpsManager mAppOps;

    private final IPackageManager mIPm;

    private ActivityManagerInternal mActivityManagerInternal;

    /**
     * This is used for debugging purposes. Whenever the IUidObserver.onUidStateChanged is called,
     * the uid and procStateSeq will be written to this and will be printed as part of dump.
     */
    @VisibleForTesting
    public ProcStateSeqHistory mObservedHistory
            = new ProcStateSeqHistory(MAX_PROC_STATE_SEQ_HISTORY);

    // TODO: keep whitelist of system-critical services that should never have
    // rules enforced, such as system, phone, and radio UIDs.

    // TODO: migrate notifications to SystemUI

    public NetworkPolicyManagerService(Context context, IActivityManager activityManager,
            INetworkStatsService networkStats, INetworkManagementService networkManagement) {
        this(context, activityManager, networkStats, networkManagement,
                AppGlobals.getPackageManager(), NtpTrustedTime.getInstance(context), getSystemDir(),
                false);
    }

    private static File getSystemDir() {
        return new File(Environment.getDataDirectory(), "system");
    }

    public NetworkPolicyManagerService(Context context, IActivityManager activityManager,
            INetworkStatsService networkStats, INetworkManagementService networkManagement,
            IPackageManager pm, TrustedTime time, File systemDir, boolean suppressDefaultPolicy) {
        mContext = checkNotNull(context, "missing context");
        mActivityManager = checkNotNull(activityManager, "missing activityManager");
        mNetworkStats = checkNotNull(networkStats, "missing networkStats");
        mNetworkManager = checkNotNull(networkManagement, "missing networkManagement");
        mDeviceIdleController = IDeviceIdleController.Stub.asInterface(ServiceManager.getService(
                Context.DEVICE_IDLE_CONTROLLER));
        mTime = checkNotNull(time, "missing TrustedTime");
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mIPm = pm;

        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new Handler(thread.getLooper(), mHandlerCallback);

        // We create another thread for the UID events, which are more time-critical.
        mUidEventThread = new ServiceThread(TAG + ".uid", Process.THREAD_PRIORITY_FOREGROUND,
                /*allowIo=*/ false);
        mUidEventThread.start();
        mUidEventHandler = new Handler(mUidEventThread.getLooper(), mUidEventHandlerCallback);

        mSuppressDefaultPolicy = suppressDefaultPolicy;

        mPolicyFile = new AtomicFile(new File(systemDir, "netpolicy.xml"));

        mAppOps = context.getSystemService(AppOpsManager.class);

        // Expose private service for system components to use.
        LocalServices.addService(NetworkPolicyManagerInternal.class,
                new NetworkPolicyManagerInternalImpl());
    }

    public void bindConnectivityManager(IConnectivityManager connManager) {
        mConnManager = checkNotNull(connManager, "missing IConnectivityManager");
    }

    public void bindNotificationManager(INotificationManager notifManager) {
        mNotifManager = checkNotNull(notifManager, "missing INotificationManager");
    }

    void updatePowerSaveWhitelistUL() {
        try {
            int[] whitelist = mDeviceIdleController.getAppIdWhitelistExceptIdle();
            mPowerSaveWhitelistExceptIdleAppIds.clear();
            if (whitelist != null) {
                for (int uid : whitelist) {
                    mPowerSaveWhitelistExceptIdleAppIds.put(uid, true);
                }
            }
            whitelist = mDeviceIdleController.getAppIdWhitelist();
            mPowerSaveWhitelistAppIds.clear();
            if (whitelist != null) {
                for (int uid : whitelist) {
                    mPowerSaveWhitelistAppIds.put(uid, true);
                }
            }
        } catch (RemoteException e) {
        }
    }

    /**
     * Whitelists pre-defined apps for restrict background, but only if the user didn't already
     * revoke the whitelist.
     *
     * @return whether any uid has been whitelisted.
     */
    boolean addDefaultRestrictBackgroundWhitelistUidsUL() {
        final List<UserInfo> users = mUserManager.getUsers();
        final int numberUsers = users.size();

        boolean changed = false;
        for (int i = 0; i < numberUsers; i++) {
            final UserInfo user = users.get(i);
            changed = addDefaultRestrictBackgroundWhitelistUidsUL(user.id) || changed;
        }
        return changed;
    }

    private boolean addDefaultRestrictBackgroundWhitelistUidsUL(int userId) {
        final SystemConfig sysConfig = SystemConfig.getInstance();
        final PackageManager pm = mContext.getPackageManager();
        final ArraySet<String> allowDataUsage = sysConfig.getAllowInDataUsageSave();
        boolean changed = false;
        for (int i = 0; i < allowDataUsage.size(); i++) {
            final String pkg = allowDataUsage.valueAt(i);
            if (LOGD)
                Slog.d(TAG, "checking restricted background whitelisting for package " + pkg
                        + " and user " + userId);
            final ApplicationInfo app;
            try {
                app = pm.getApplicationInfoAsUser(pkg, PackageManager.MATCH_SYSTEM_ONLY, userId);
            } catch (PackageManager.NameNotFoundException e) {
                if (LOGD) Slog.d(TAG, "No ApplicationInfo for package " + pkg);
                // Ignore it - some apps on allow-in-data-usage-save are optional.
                continue;
            }
            if (!app.isPrivilegedApp()) {
                Slog.e(TAG, "addDefaultRestrictBackgroundWhitelistUidsUL(): "
                        + "skipping non-privileged app  " + pkg);
                continue;
            }
            final int uid = UserHandle.getUid(userId, app.uid);
            mDefaultRestrictBackgroundWhitelistUids.append(uid, true);
            if (LOGD)
                Slog.d(TAG, "Adding uid " + uid + " (user " + userId + ") to default restricted "
                        + "background whitelist. Revoked status: "
                        + mRestrictBackgroundWhitelistRevokedUids.get(uid));
            if (!mRestrictBackgroundWhitelistRevokedUids.get(uid)) {
                if (LOGD)
                    Slog.d(TAG, "adding default package " + pkg + " (uid " + uid + " for user "
                            + userId + ") to restrict background whitelist");
                setUidPolicyUncheckedUL(uid, POLICY_ALLOW_METERED_BACKGROUND, false);
                changed = true;
            }
        }
        return changed;
    }

    void updatePowerSaveTempWhitelistUL() {
        try {
            // Clear the states of the current whitelist
            final int N = mPowerSaveTempWhitelistAppIds.size();
            for (int i = 0; i < N; i++) {
                mPowerSaveTempWhitelistAppIds.setValueAt(i, false);
            }
            // Update the states with the new whitelist
            final int[] whitelist = mDeviceIdleController.getAppIdTempWhitelist();
            if (whitelist != null) {
                for (int uid : whitelist) {
                    mPowerSaveTempWhitelistAppIds.put(uid, true);
                }
            }
        } catch (RemoteException e) {
        }
    }

    /**
     * Remove unnecessary entries in the temp whitelist
     */
    void purgePowerSaveTempWhitelistUL() {
        final int N = mPowerSaveTempWhitelistAppIds.size();
        for (int i = N - 1; i >= 0; i--) {
            if (mPowerSaveTempWhitelistAppIds.valueAt(i) == false) {
                mPowerSaveTempWhitelistAppIds.removeAt(i);
            }
        }
    }

    private void initService(CountDownLatch initCompleteSignal) {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "systemReady");
        final int oldPriority = Process.getThreadPriority(Process.myTid());
        try {
            // Boost thread's priority during system server init
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
            if (!isBandwidthControlEnabled()) {
                Slog.w(TAG, "bandwidth controls disabled, unable to enforce policy");
                return;
            }

            mUsageStats = LocalServices.getService(UsageStatsManagerInternal.class);

            synchronized (mUidRulesFirstLock) {
                synchronized (mNetworkPoliciesSecondLock) {
                    updatePowerSaveWhitelistUL();
                    mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
                    mPowerManagerInternal.registerLowPowerModeObserver(
                            new PowerManagerInternal.LowPowerModeListener() {
                                @Override
                                public int getServiceType() {
                                    return ServiceType.NETWORK_FIREWALL;
                                }

                                @Override
                                public void onLowPowerModeChanged(PowerSaveState result) {
                                    final boolean enabled = result.batterySaverEnabled;
                                    if (LOGD) {
                                        Slog.d(TAG, "onLowPowerModeChanged(" + enabled + ")");
                                    }
                                    synchronized (mUidRulesFirstLock) {
                                        if (mRestrictPower != enabled) {
                                            mRestrictPower = enabled;
                                            updateRulesForRestrictPowerUL();
                                        }
                                    }
                                }
                            });
                    mRestrictPower = mPowerManagerInternal.getLowPowerState(
                            ServiceType.NETWORK_FIREWALL).batterySaverEnabled;

                    mSystemReady = true;

                    // read policy from disk
                    readPolicyAL();

                    // Update the restrictBackground if battery saver is turned on
                    mRestrictBackgroundBeforeBsm = mRestrictBackground;
                    mRestrictBackgroundPowerState = mPowerManagerInternal
                            .getLowPowerState(ServiceType.DATA_SAVER);
                    final boolean localRestrictBackground =
                            mRestrictBackgroundPowerState.batterySaverEnabled;
                    if (localRestrictBackground && localRestrictBackground != mRestrictBackground) {
                        mRestrictBackground = localRestrictBackground;
                        mHandler.obtainMessage(MSG_RESTRICT_BACKGROUND_CHANGED,
                                mRestrictBackground ? 1 : 0, 0).sendToTarget();
                    }
                    mPowerManagerInternal.registerLowPowerModeObserver(
                            new PowerManagerInternal.LowPowerModeListener() {
                                @Override
                                public int getServiceType() {
                                    return ServiceType.DATA_SAVER;
                                }

                                @Override
                                public void onLowPowerModeChanged(PowerSaveState result) {
                                    synchronized (mUidRulesFirstLock) {
                                        updateRestrictBackgroundByLowPowerModeUL(result);
                                    }
                                }
                            });

                    if (addDefaultRestrictBackgroundWhitelistUidsUL()) {
                        writePolicyAL();
                    }

                    setRestrictBackgroundUL(mRestrictBackground);
                    updateRulesForGlobalChangeAL(false);
                    updateNotificationsNL();
                }
            }

            mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
            try {
                mActivityManager.registerUidObserver(mUidObserver,
                        ActivityManager.UID_OBSERVER_PROCSTATE|ActivityManager.UID_OBSERVER_GONE,
                        ActivityManager.PROCESS_STATE_UNKNOWN, null);
                mNetworkManager.registerObserver(mAlertObserver);
            } catch (RemoteException e) {
                // ignored; both services live in system_server
            }

            // listen for changes to power save whitelist
            final IntentFilter whitelistFilter = new IntentFilter(
                    PowerManager.ACTION_POWER_SAVE_WHITELIST_CHANGED);
            mContext.registerReceiver(mPowerSaveWhitelistReceiver, whitelistFilter, null, mHandler);

            DeviceIdleController.LocalService deviceIdleService
                    = LocalServices.getService(DeviceIdleController.LocalService.class);
            deviceIdleService.setNetworkPolicyTempWhitelistCallback(mTempPowerSaveChangedCallback);

            // watch for network interfaces to be claimed
            final IntentFilter connFilter = new IntentFilter(CONNECTIVITY_ACTION);
            mContext.registerReceiver(mConnReceiver, connFilter, CONNECTIVITY_INTERNAL, mHandler);

            // listen for package changes to update policy
            final IntentFilter packageFilter = new IntentFilter();
            packageFilter.addAction(ACTION_PACKAGE_ADDED);
            packageFilter.addDataScheme("package");
            mContext.registerReceiver(mPackageReceiver, packageFilter, null, mHandler);

            // listen for UID changes to update policy
            mContext.registerReceiver(
                    mUidRemovedReceiver, new IntentFilter(ACTION_UID_REMOVED), null, mHandler);

            // listen for user changes to update policy
            final IntentFilter userFilter = new IntentFilter();
            userFilter.addAction(ACTION_USER_ADDED);
            userFilter.addAction(ACTION_USER_REMOVED);
            mContext.registerReceiver(mUserReceiver, userFilter, null, mHandler);

            // listen for stats update events
            final IntentFilter statsFilter = new IntentFilter(ACTION_NETWORK_STATS_UPDATED);
            mContext.registerReceiver(
                    mStatsReceiver, statsFilter, READ_NETWORK_USAGE_HISTORY, mHandler);

            // listen for restrict background changes from notifications
            final IntentFilter allowFilter = new IntentFilter(ACTION_ALLOW_BACKGROUND);
            mContext.registerReceiver(mAllowReceiver, allowFilter, MANAGE_NETWORK_POLICY, mHandler);

            // listen for snooze warning from notifications
            final IntentFilter snoozeWarningFilter = new IntentFilter(ACTION_SNOOZE_WARNING);
            mContext.registerReceiver(mSnoozeWarningReceiver, snoozeWarningFilter,
                    MANAGE_NETWORK_POLICY, mHandler);

            // listen for configured wifi networks to be loaded
            final IntentFilter wifiFilter =
                    new IntentFilter(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
            mContext.registerReceiver(mWifiReceiver, wifiFilter, null, mHandler);

            // listen for carrier config changes to update data cycle information
            final IntentFilter carrierConfigFilter = new IntentFilter(
                    ACTION_CARRIER_CONFIG_CHANGED);
            mContext.registerReceiver(mCarrierConfigReceiver, carrierConfigFilter, null, mHandler);

            // listen for meteredness changes
            mContext.getSystemService(ConnectivityManager.class).registerNetworkCallback(
                    new NetworkRequest.Builder().build(), mNetworkCallback);

            mUsageStats.addAppIdleStateChangeListener(new AppIdleStateChangeListener());
            // tell systemReady() that the service has been initialized
            initCompleteSignal.countDown();
        } finally {
            // Restore the default priority after init is done
            Process.setThreadPriority(oldPriority);
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    public CountDownLatch networkScoreAndNetworkManagementServiceReady() {
        final CountDownLatch initCompleteSignal = new CountDownLatch(1);
        mHandler.post(() -> initService(initCompleteSignal));
        return initCompleteSignal;
    }

    public void systemReady(CountDownLatch initCompleteSignal) {
        // wait for initService to complete
        try {
            if (!initCompleteSignal.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Service " + TAG +" init timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Service " + TAG + " init interrupted", e);
        }
    }

    final private IUidObserver mUidObserver = new IUidObserver.Stub() {
        @Override public void onUidStateChanged(int uid, int procState, long procStateSeq) {
            mUidEventHandler.obtainMessage(UID_MSG_STATE_CHANGED,
                    uid, procState, procStateSeq).sendToTarget();
        }

        @Override public void onUidGone(int uid, boolean disabled) {
            mUidEventHandler.obtainMessage(UID_MSG_GONE, uid, 0).sendToTarget();
        }

        @Override public void onUidActive(int uid) {
        }

        @Override public void onUidIdle(int uid, boolean disabled) {
        }

        @Override public void onUidCachedChanged(int uid, boolean cached) {
        }
    };

    final private BroadcastReceiver mPowerSaveWhitelistReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and POWER_SAVE_WHITELIST_CHANGED is protected
            synchronized (mUidRulesFirstLock) {
                updatePowerSaveWhitelistUL();
                updateRulesForRestrictPowerUL();
                updateRulesForAppIdleUL();
            }
        }
    };

    final private Runnable mTempPowerSaveChangedCallback = new Runnable() {
        @Override
        public void run() {
            synchronized (mUidRulesFirstLock) {
                updatePowerSaveTempWhitelistUL();
                updateRulesForTempWhitelistChangeUL();
                purgePowerSaveTempWhitelistUL();
            }
        }
    };

    final private BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and PACKAGE_ADDED is protected

            final String action = intent.getAction();
            final int uid = intent.getIntExtra(EXTRA_UID, -1);
            if (uid == -1) return;

            if (ACTION_PACKAGE_ADDED.equals(action)) {
                // update rules for UID, since it might be subject to
                // global background data policy
                if (LOGV) Slog.v(TAG, "ACTION_PACKAGE_ADDED for uid=" + uid);
                synchronized (mUidRulesFirstLock) {
                    updateRestrictionRulesForUidUL(uid);
                }
            }
        }
    };

    final private BroadcastReceiver mUidRemovedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and UID_REMOVED is protected

            final int uid = intent.getIntExtra(EXTRA_UID, -1);
            if (uid == -1) return;

            // remove any policy and update rules to clean up
            if (LOGV) Slog.v(TAG, "ACTION_UID_REMOVED for uid=" + uid);
            synchronized (mUidRulesFirstLock) {
                onUidDeletedUL(uid);
                synchronized (mNetworkPoliciesSecondLock) {
                    writePolicyAL();
                }
            }
        }
    };

    final private BroadcastReceiver mUserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and USER_ADDED and USER_REMOVED
            // broadcasts are protected

            final String action = intent.getAction();
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
            if (userId == -1) return;

            switch (action) {
                case ACTION_USER_REMOVED:
                case ACTION_USER_ADDED:
                    synchronized (mUidRulesFirstLock) {
                        // Remove any persistable state for the given user; both cleaning up after a
                        // USER_REMOVED, and one last sanity check during USER_ADDED
                        removeUserStateUL(userId, true);
                        if (action == ACTION_USER_ADDED) {
                            // Add apps that are whitelisted by default.
                            addDefaultRestrictBackgroundWhitelistUidsUL(userId);
                        }
                        // Update global restrict for that user
                        synchronized (mNetworkPoliciesSecondLock) {
                            updateRulesForGlobalChangeAL(true);
                        }
                    }
                    break;
            }
        }
    };

    /**
     * Receiver that watches for {@link INetworkStatsService} updates, which we
     * use to check against {@link NetworkPolicy#warningBytes}.
     */
    final private BroadcastReceiver mStatsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and verified
            // READ_NETWORK_USAGE_HISTORY permission above.

            maybeRefreshTrustedTime();
            synchronized (mNetworkPoliciesSecondLock) {
                updateNetworkEnabledNL();
                updateNotificationsNL();
            }
        }
    };

    /**
     * Receiver that watches for {@link Notification} control of
     * {@link #mRestrictBackground}.
     */
    final private BroadcastReceiver mAllowReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and verified MANAGE_NETWORK_POLICY
            // permission above.

            setRestrictBackground(false);
        }
    };

    /**
     * Receiver that watches for {@link Notification} control of
     * {@link NetworkPolicy#lastWarningSnooze}.
     */
    final private BroadcastReceiver mSnoozeWarningReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and verified MANAGE_NETWORK_POLICY
            // permission above.

            final NetworkTemplate template = intent.getParcelableExtra(EXTRA_NETWORK_TEMPLATE);
            performSnooze(template, TYPE_WARNING);
        }
    };

    /**
     * Receiver that watches for {@link WifiConfiguration} to be loaded so that
     * we can perform upgrade logic. After initial upgrade logic, it updates
     * {@link #mMeteredIfaces} based on configuration changes.
     */
    final private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mUidRulesFirstLock) {
                synchronized (mNetworkPoliciesSecondLock) {
                    upgradeWifiMeteredOverrideAL();
                }
            }
            // Only need to perform upgrade logic once
            mContext.unregisterReceiver(this);
        }
    };

    private final NetworkCallback mNetworkCallback = new NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            if (network == null || networkCapabilities == null) return;

            synchronized (mNetworkPoliciesSecondLock) {
                final boolean oldMetered = mNetworkMetered.get(network.netId, false);
                final boolean newMetered = !networkCapabilities
                        .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

                if ((oldMetered != newMetered) || mNetworkMetered.indexOfKey(network.netId) < 0) {
                    mNetworkMetered.put(network.netId, newMetered);
                    updateNetworkRulesNL();
                }
            }
        }
    };

    /**
     * Observer that watches for {@link INetworkManagementService} alerts.
     */
    final private INetworkManagementEventObserver mAlertObserver
            = new BaseNetworkObserver() {
        @Override
        public void limitReached(String limitName, String iface) {
            // only someone like NMS should be calling us
            mContext.enforceCallingOrSelfPermission(CONNECTIVITY_INTERNAL, TAG);

            if (!LIMIT_GLOBAL_ALERT.equals(limitName)) {
                mHandler.obtainMessage(MSG_LIMIT_REACHED, iface).sendToTarget();
            }
        }
    };

    /**
     * Check {@link NetworkPolicy} against current {@link INetworkStatsService}
     * to show visible notifications as needed.
     */
    void updateNotificationsNL() {
        if (LOGV) Slog.v(TAG, "updateNotificationsNL()");

        // keep track of previously active notifications
        final ArraySet<NotificationId> beforeNotifs = new ArraySet<NotificationId>(mActiveNotifs);
        mActiveNotifs.clear();

        // TODO: when switching to kernel notifications, compute next future
        // cycle boundary to recompute notifications.

        // examine stats for each active policy
        for (int i = mNetworkPolicy.size()-1; i >= 0; i--) {
            final NetworkPolicy policy = mNetworkPolicy.valueAt(i);
            // ignore policies that aren't relevant to user
            if (!isTemplateRelevant(policy.template)) continue;
            if (!policy.hasCycle()) continue;

            final Pair<ZonedDateTime, ZonedDateTime> cycle = NetworkPolicyManager
                    .cycleIterator(policy).next();
            final long start = cycle.first.toInstant().toEpochMilli();
            final long end = cycle.second.toInstant().toEpochMilli();
            final long totalBytes = getTotalBytes(policy.template, start, end);

            if (policy.isOverLimit(totalBytes)) {
                if (policy.lastLimitSnooze >= start) {
                    enqueueNotification(policy, TYPE_LIMIT_SNOOZED, totalBytes);
                } else {
                    enqueueNotification(policy, TYPE_LIMIT, totalBytes);
                    notifyOverLimitNL(policy.template);
                }

            } else {
                notifyUnderLimitNL(policy.template);

                if (policy.isOverWarning(totalBytes) && policy.lastWarningSnooze < start) {
                    enqueueNotification(policy, TYPE_WARNING, totalBytes);
                }
            }
        }

        // cancel stale notifications that we didn't renew above
        for (int i = beforeNotifs.size()-1; i >= 0; i--) {
            final NotificationId notificationId = beforeNotifs.valueAt(i);
            if (!mActiveNotifs.contains(notificationId)) {
                cancelNotification(notificationId);
            }
        }
    }

    /**
     * Test if given {@link NetworkTemplate} is relevant to user based on
     * current device state, such as when
     * {@link TelephonyManager#getSubscriberId()} matches. This is regardless of
     * data connection status.
     */
    private boolean isTemplateRelevant(NetworkTemplate template) {
        if (template.isMatchRuleMobile()) {
            final TelephonyManager tele = TelephonyManager.from(mContext);
            final SubscriptionManager sub = SubscriptionManager.from(mContext);

            // Mobile template is relevant when any active subscriber matches
            final int[] subIds = sub.getActiveSubscriptionIdList();
            for (int subId : subIds) {
                final String subscriberId = tele.getSubscriberId(subId);
                final NetworkIdentity probeIdent = new NetworkIdentity(TYPE_MOBILE,
                        TelephonyManager.NETWORK_TYPE_UNKNOWN, subscriberId, null, false, true);
                if (template.matches(probeIdent)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Notify that given {@link NetworkTemplate} is over
     * {@link NetworkPolicy#limitBytes}, potentially showing dialog to user.
     */
    private void notifyOverLimitNL(NetworkTemplate template) {
        if (!mOverLimitNotified.contains(template)) {
            mContext.startActivity(buildNetworkOverLimitIntent(mContext.getResources(), template));
            mOverLimitNotified.add(template);
        }
    }

    private void notifyUnderLimitNL(NetworkTemplate template) {
        mOverLimitNotified.remove(template);
    }

    /**
     * Show notification for combined {@link NetworkPolicy} and specific type,
     * like {@link #TYPE_LIMIT}. Okay to call multiple times.
     */
    private void enqueueNotification(NetworkPolicy policy, int type, long totalBytes) {
        final NotificationId notificationId = new NotificationId(policy, type);
        final Notification.Builder builder =
                new Notification.Builder(mContext, SystemNotificationChannels.NETWORK_STATUS);
        builder.setOnlyAlertOnce(true);
        builder.setWhen(0L);
        builder.setColor(mContext.getColor(
                com.android.internal.R.color.system_notification_accent_color));

        final Resources res = mContext.getResources();
        CharSequence body = null;
        switch (type) {
            case TYPE_WARNING: {
                final CharSequence title = res.getText(R.string.data_usage_warning_title);
                body = res.getString(R.string.data_usage_warning_body);

                builder.setSmallIcon(R.drawable.stat_notify_error);
                builder.setTicker(title);
                builder.setContentTitle(title);
                builder.setContentText(body);
                builder.setDefaults(Notification.DEFAULT_ALL);
                builder.setChannelId(SystemNotificationChannels.NETWORK_ALERTS);

                final Intent snoozeIntent = buildSnoozeWarningIntent(policy.template);
                builder.setDeleteIntent(PendingIntent.getBroadcast(
                        mContext, 0, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                final Intent viewIntent = buildViewDataUsageIntent(res, policy.template);
                builder.setContentIntent(PendingIntent.getActivity(
                        mContext, 0, viewIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                break;
            }
            case TYPE_LIMIT: {
                body = res.getText(R.string.data_usage_limit_body);

                final CharSequence title;
                int icon = R.drawable.stat_notify_disabled_data;
                switch (policy.template.getMatchRule()) {
                    case MATCH_MOBILE_3G_LOWER:
                        title = res.getText(R.string.data_usage_3g_limit_title);
                        break;
                    case MATCH_MOBILE_4G:
                        title = res.getText(R.string.data_usage_4g_limit_title);
                        break;
                    case MATCH_MOBILE_ALL:
                        title = res.getText(R.string.data_usage_mobile_limit_title);
                        break;
                    case MATCH_WIFI:
                        title = res.getText(R.string.data_usage_wifi_limit_title);
                        icon = R.drawable.stat_notify_error;
                        break;
                    default:
                        title = null;
                        break;
                }

                builder.setOngoing(true);
                builder.setSmallIcon(icon);
                builder.setTicker(title);
                builder.setContentTitle(title);
                builder.setContentText(body);

                final Intent intent = buildNetworkOverLimitIntent(res, policy.template);
                builder.setContentIntent(PendingIntent.getActivity(
                        mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            }
            case TYPE_LIMIT_SNOOZED: {
                final long overBytes = totalBytes - policy.limitBytes;
                body = res.getString(R.string.data_usage_limit_snoozed_body,
                        Formatter.formatFileSize(mContext, overBytes));

                final CharSequence title;
                switch (policy.template.getMatchRule()) {
                    case MATCH_MOBILE_3G_LOWER:
                        title = res.getText(R.string.data_usage_3g_limit_snoozed_title);
                        break;
                    case MATCH_MOBILE_4G:
                        title = res.getText(R.string.data_usage_4g_limit_snoozed_title);
                        break;
                    case MATCH_MOBILE_ALL:
                        title = res.getText(R.string.data_usage_mobile_limit_snoozed_title);
                        break;
                    case MATCH_WIFI:
                        title = res.getText(R.string.data_usage_wifi_limit_snoozed_title);
                        break;
                    default:
                        title = null;
                        break;
                }

                builder.setOngoing(true);
                builder.setSmallIcon(R.drawable.stat_notify_error);
                builder.setTicker(title);
                builder.setContentTitle(title);
                builder.setContentText(body);

                final Intent intent = buildViewDataUsageIntent(res, policy.template);
                builder.setContentIntent(PendingIntent.getActivity(
                        mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            }
        }

        // TODO: move to NotificationManager once we can mock it
        try {
            final String packageName = mContext.getPackageName();
            if (!TextUtils.isEmpty(body)) {
                builder.setStyle(new Notification.BigTextStyle()
                        .bigText(body));
            }
            mNotifManager.enqueueNotificationWithTag(
                    packageName, packageName, notificationId.getTag(), notificationId.getId(),
                    builder.build(), UserHandle.USER_ALL);
            mActiveNotifs.add(notificationId);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private void cancelNotification(NotificationId notificationId) {
        // TODO: move to NotificationManager once we can mock it
        try {
            final String packageName = mContext.getPackageName();
            mNotifManager.cancelNotificationWithTag(
                    packageName, notificationId.getTag(), notificationId.getId(),
                    UserHandle.USER_ALL);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    /**
     * Receiver that watches for {@link IConnectivityManager} to claim network
     * interfaces. Used to apply {@link NetworkPolicy} to matching networks.
     */
    private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // on background handler thread, and verified CONNECTIVITY_INTERNAL
            // permission above.

            maybeRefreshTrustedTime();
            synchronized (mUidRulesFirstLock) {
                synchronized (mNetworkPoliciesSecondLock) {
                    ensureActiveMobilePolicyAL();
                    normalizePoliciesNL();
                    updateNetworkEnabledNL();
                    updateNetworkRulesNL();
                    updateNotificationsNL();
                }
            }
        }
    };

    /**
     * Update mobile policies with data cycle information from {@link CarrierConfigManager}
     * if necessary.
     *
     * @param subId that has its associated NetworkPolicy updated if necessary
     * @return if any policies were updated
     */
    private boolean maybeUpdateMobilePolicyCycleAL(int subId) {
        if (LOGV) Slog.v(TAG, "maybeUpdateMobilePolicyCycleAL()");

        boolean policyUpdated = false;
        final String subscriberId = TelephonyManager.from(mContext).getSubscriberId(subId);

        // find and update the mobile NetworkPolicy for this subscriber id
        final NetworkIdentity probeIdent = new NetworkIdentity(TYPE_MOBILE,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, subscriberId, null, false, true);
        for (int i = mNetworkPolicy.size() - 1; i >= 0; i--) {
            final NetworkTemplate template = mNetworkPolicy.keyAt(i);
            if (template.matches(probeIdent)) {
                final NetworkPolicy policy = mNetworkPolicy.valueAt(i);
                policyUpdated |= updateDefaultMobilePolicyAL(subId, policy);
            }
        }
        return policyUpdated;
    }

    /**
     * Returns the cycle day that should be used for a mobile NetworkPolicy.
     *
     * It attempts to get an appropriate cycle day from the passed in CarrierConfig. If it's unable
     * to do so, it returns the fallback value.
     *
     * @param config The CarrierConfig to read the value from.
     * @param fallbackCycleDay to return if the CarrierConfig can't be read.
     * @return cycleDay to use in the mobile NetworkPolicy.
     */
    @VisibleForTesting
    public int getCycleDayFromCarrierConfig(@Nullable PersistableBundle config,
            int fallbackCycleDay) {
        if (config == null) {
            return fallbackCycleDay;
        }
        int cycleDay =
                config.getInt(CarrierConfigManager.KEY_MONTHLY_DATA_CYCLE_DAY_INT);
        if (cycleDay == DATA_CYCLE_USE_PLATFORM_DEFAULT) {
            return fallbackCycleDay;
        }
        // validate cycleDay value
        final Calendar cal = Calendar.getInstance();
        if (cycleDay < cal.getMinimum(Calendar.DAY_OF_MONTH) ||
                cycleDay > cal.getMaximum(Calendar.DAY_OF_MONTH)) {
            Slog.e(TAG, "Invalid date in "
                    + "CarrierConfigManager.KEY_MONTHLY_DATA_CYCLE_DAY_INT: " + cycleDay);
            return fallbackCycleDay;
        }
        return cycleDay;
    }

    /**
     * Returns the warning bytes that should be used for a mobile NetworkPolicy.
     *
     * It attempts to get an appropriate value from the passed in CarrierConfig. If it's unable
     * to do so, it returns the fallback value.
     *
     * @param config The CarrierConfig to read the value from.
     * @param fallbackWarningBytes to return if the CarrierConfig can't be read.
     * @return warningBytes to use in the mobile NetworkPolicy.
     */
    @VisibleForTesting
    public long getWarningBytesFromCarrierConfig(@Nullable PersistableBundle config,
            long fallbackWarningBytes) {
        if (config == null) {
            return fallbackWarningBytes;
        }
        long warningBytes =
                config.getLong(CarrierConfigManager.KEY_DATA_WARNING_THRESHOLD_BYTES_LONG);

        if (warningBytes == DATA_CYCLE_THRESHOLD_DISABLED) {
            return WARNING_DISABLED;
        } else if (warningBytes == DATA_CYCLE_USE_PLATFORM_DEFAULT) {
            return getPlatformDefaultWarningBytes();
        } else if (warningBytes < 0) {
            Slog.e(TAG, "Invalid value in "
                    + "CarrierConfigManager.KEY_DATA_WARNING_THRESHOLD_BYTES_LONG; expected a "
                    + "non-negative value but got: " + warningBytes);
            return fallbackWarningBytes;
        }

        return warningBytes;
    }

    /**
     * Returns the limit bytes that should be used for a mobile NetworkPolicy.
     *
     * It attempts to get an appropriate value from the passed in CarrierConfig. If it's unable
     * to do so, it returns the fallback value.
     *
     * @param config The CarrierConfig to read the value from.
     * @param fallbackLimitBytes to return if the CarrierConfig can't be read.
     * @return limitBytes to use in the mobile NetworkPolicy.
     */
    @VisibleForTesting
    public long getLimitBytesFromCarrierConfig(@Nullable PersistableBundle config,
            long fallbackLimitBytes) {
        if (config == null) {
            return fallbackLimitBytes;
        }
        long limitBytes =
                config.getLong(CarrierConfigManager.KEY_DATA_LIMIT_THRESHOLD_BYTES_LONG);

        if (limitBytes == DATA_CYCLE_THRESHOLD_DISABLED) {
            return LIMIT_DISABLED;
        } else if (limitBytes == DATA_CYCLE_USE_PLATFORM_DEFAULT) {
            return getPlatformDefaultLimitBytes();
        } else if (limitBytes < 0) {
            Slog.e(TAG, "Invalid value in "
                    + "CarrierConfigManager.KEY_DATA_LIMIT_THRESHOLD_BYTES_LONG; expected a "
                    + "non-negative value but got: " + limitBytes);
            return fallbackLimitBytes;
        }
        return limitBytes;
    }

    /**
     * Receiver that watches for {@link CarrierConfigManager} to be changed.
     */
    private BroadcastReceiver mCarrierConfigReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // No need to do a permission check, because the ACTION_CARRIER_CONFIG_CHANGED
            // broadcast is protected and can't be spoofed. Runs on a background handler thread.

            if (!intent.hasExtra(PhoneConstants.SUBSCRIPTION_KEY)) {
                return;
            }
            final int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
            final TelephonyManager tele = TelephonyManager.from(mContext);
            final String subscriberId = tele.getSubscriberId(subId);

            maybeRefreshTrustedTime();
            synchronized (mUidRulesFirstLock) {
                synchronized (mNetworkPoliciesSecondLock) {
                    final boolean added = ensureActiveMobilePolicyAL(subId, subscriberId);
                    if (added) return;
                    final boolean updated = maybeUpdateMobilePolicyCycleAL(subId);
                    if (!updated) return;
                    // update network and notification rules, as the data cycle changed and it's
                    // possible that we should be triggering warnings/limits now
                    handleNetworkPoliciesUpdateAL(true);
                }
            }
        }
    };

    /**
     * Handles all tasks that need to be run after a new network policy has been set, or an existing
     * one has been updated.
     *
     * @param shouldNormalizePolicies true iff network policies need to be normalized after the
     *                                update.
     */
    void handleNetworkPoliciesUpdateAL(boolean shouldNormalizePolicies) {
        if (shouldNormalizePolicies) {
            normalizePoliciesNL();
        }
        updateNetworkEnabledNL();
        updateNetworkRulesNL();
        updateNotificationsNL();
        writePolicyAL();
    }

    /**
     * Proactively control network data connections when they exceed
     * {@link NetworkPolicy#limitBytes}.
     */
    void updateNetworkEnabledNL() {
        if (LOGV) Slog.v(TAG, "updateNetworkEnabledNL()");

        // TODO: reset any policy-disabled networks when any policy is removed
        // completely, which is currently rare case.

        for (int i = mNetworkPolicy.size()-1; i >= 0; i--) {
            final NetworkPolicy policy = mNetworkPolicy.valueAt(i);
            // shortcut when policy has no limit
            if (policy.limitBytes == LIMIT_DISABLED || !policy.hasCycle()) {
                setNetworkTemplateEnabled(policy.template, true);
                continue;
            }

            final Pair<ZonedDateTime, ZonedDateTime> cycle = NetworkPolicyManager
                    .cycleIterator(policy).next();
            final long start = cycle.first.toInstant().toEpochMilli();
            final long end = cycle.second.toInstant().toEpochMilli();
            final long totalBytes = getTotalBytes(policy.template, start, end);

            // disable data connection when over limit and not snoozed
            final boolean overLimitWithoutSnooze = policy.isOverLimit(totalBytes)
                    && policy.lastLimitSnooze < start;
            final boolean networkEnabled = !overLimitWithoutSnooze;

            setNetworkTemplateEnabled(policy.template, networkEnabled);
        }
    }

    /**
     * Proactively disable networks that match the given
     * {@link NetworkTemplate}.
     */
    private void setNetworkTemplateEnabled(NetworkTemplate template, boolean enabled) {
        // TODO: reach into ConnectivityManager to proactively disable bringing
        // up this network, since we know that traffic will be blocked.

        if (template.getMatchRule() == MATCH_MOBILE_ALL) {
            // If mobile data usage hits the limit or if the user resumes the data, we need to
            // notify telephony.
            final SubscriptionManager sm = SubscriptionManager.from(mContext);
            final TelephonyManager tm = TelephonyManager.from(mContext);

            final int[] subIds = sm.getActiveSubscriptionIdList();
            for (int subId : subIds) {
                final String subscriberId = tm.getSubscriberId(subId);
                final NetworkIdentity probeIdent = new NetworkIdentity(TYPE_MOBILE,
                        TelephonyManager.NETWORK_TYPE_UNKNOWN, subscriberId, null, false, true);
                // Template is matched when subscriber id matches.
                if (template.matches(probeIdent)) {
                    tm.setPolicyDataEnabled(enabled, subId);
                }
            }
        }
    }

    /**
     * Collect all ifaces from a {@link NetworkState} into the given set.
     */
    private static void collectIfaces(ArraySet<String> ifaces, NetworkState state) {
        final String baseIface = state.linkProperties.getInterfaceName();
        if (baseIface != null) {
            ifaces.add(baseIface);
        }
        for (LinkProperties stackedLink : state.linkProperties.getStackedLinks()) {
            final String stackedIface = stackedLink.getInterfaceName();
            if (stackedIface != null) {
                ifaces.add(stackedIface);
            }
        }
    }

    /**
     * Examine all connected {@link NetworkState}, looking for
     * {@link NetworkPolicy} that need to be enforced. When matches found, set
     * remaining quota based on usage cycle and historical stats.
     */
    void updateNetworkRulesNL() {
        if (LOGV) Slog.v(TAG, "updateNetworkRulesNL()");

        final NetworkState[] states;
        try {
            states = mConnManager.getAllNetworkState();
        } catch (RemoteException e) {
            // ignored; service lives in system_server
            return;
        }

        // First, generate identities of all connected networks so we can
        // quickly compare them against all defined policies below.
        final ArrayMap<NetworkState, NetworkIdentity> identified = new ArrayMap<>();
        for (NetworkState state : states) {
            if (state.networkInfo != null && state.networkInfo.isConnected()) {
                final NetworkIdentity ident = NetworkIdentity.buildNetworkIdentity(mContext, state);
                identified.put(state, ident);
            }
        }

        final ArraySet<String> newMeteredIfaces = new ArraySet<>();
        long lowestRule = Long.MAX_VALUE;

        // For every well-defined policy, compute remaining data based on
        // current cycle and historical stats, and push to kernel.
        final ArraySet<String> matchingIfaces = new ArraySet<>();
        for (int i = mNetworkPolicy.size() - 1; i >= 0; i--) {
           final NetworkPolicy policy = mNetworkPolicy.valueAt(i);

            // Collect all ifaces that match this policy
            matchingIfaces.clear();
            for (int j = identified.size() - 1; j >= 0; j--) {
                if (policy.template.matches(identified.valueAt(j))) {
                    collectIfaces(matchingIfaces, identified.keyAt(j));
                }
            }

            if (LOGD) {
                Slog.d(TAG, "Applying " + policy + " to ifaces " + matchingIfaces);
            }

            final boolean hasWarning = policy.warningBytes != LIMIT_DISABLED;
            final boolean hasLimit = policy.limitBytes != LIMIT_DISABLED;
            if (hasLimit || policy.metered) {
                final long quotaBytes;
                if (hasLimit && policy.hasCycle()) {
                    final Pair<ZonedDateTime, ZonedDateTime> cycle = NetworkPolicyManager
                            .cycleIterator(policy).next();
                    final long start = cycle.first.toInstant().toEpochMilli();
                    final long end = cycle.second.toInstant().toEpochMilli();
                    final long totalBytes = getTotalBytes(policy.template, start, end);

                    if (policy.lastLimitSnooze >= start) {
                        // snoozing past quota, but we still need to restrict apps,
                        // so push really high quota.
                        quotaBytes = Long.MAX_VALUE;
                    } else {
                        // remaining "quota" bytes are based on total usage in
                        // current cycle. kernel doesn't like 0-byte rules, so we
                        // set 1-byte quota and disable the radio later.
                        quotaBytes = Math.max(1, policy.limitBytes - totalBytes);
                    }
                } else {
                    // metered network, but no policy limit; we still need to
                    // restrict apps, so push really high quota.
                    quotaBytes = Long.MAX_VALUE;
                }

                if (matchingIfaces.size() > 1) {
                    // TODO: switch to shared quota once NMS supports
                    Slog.w(TAG, "shared quota unsupported; generating rule for each iface");
                }

                for (int j = matchingIfaces.size() - 1; j >= 0; j--) {
                    final String iface = matchingIfaces.valueAt(j);
                    setInterfaceQuotaAsync(iface, quotaBytes);
                    newMeteredIfaces.add(iface);
                }
            }

            // keep track of lowest warning or limit of active policies
            if (hasWarning && policy.warningBytes < lowestRule) {
                lowestRule = policy.warningBytes;
            }
            if (hasLimit && policy.limitBytes < lowestRule) {
                lowestRule = policy.limitBytes;
            }
        }

        // One final pass to catch any metered ifaces that don't have explicitly
        // defined policies; typically Wi-Fi networks.
        for (NetworkState state : states) {
            if (state.networkInfo != null && state.networkInfo.isConnected()
                    && !state.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_METERED)) {
                matchingIfaces.clear();
                collectIfaces(matchingIfaces, state);
                for (int j = matchingIfaces.size() - 1; j >= 0; j--) {
                    final String iface = matchingIfaces.valueAt(j);
                    if (!newMeteredIfaces.contains(iface)) {
                        setInterfaceQuotaAsync(iface, Long.MAX_VALUE);
                        newMeteredIfaces.add(iface);
                    }
                }
            }
        }

        // Remove quota from any interfaces that are no longer metered.
        for (int i = mMeteredIfaces.size() - 1; i >= 0; i--) {
            final String iface = mMeteredIfaces.valueAt(i);
            if (!newMeteredIfaces.contains(iface)) {
                removeInterfaceQuotaAsync(iface);
            }
        }
        mMeteredIfaces = newMeteredIfaces;

        final String[] meteredIfaces = mMeteredIfaces.toArray(new String[mMeteredIfaces.size()]);
        mHandler.obtainMessage(MSG_METERED_IFACES_CHANGED, meteredIfaces).sendToTarget();

        mHandler.obtainMessage(MSG_ADVISE_PERSIST_THRESHOLD, lowestRule).sendToTarget();
    }

    /**
     * Once any {@link #mNetworkPolicy} are loaded from disk, ensure that we
     * have at least a default mobile policy defined.
     */
    private void ensureActiveMobilePolicyAL() {
        if (LOGV) Slog.v(TAG, "ensureActiveMobilePolicyAL()");
        if (mSuppressDefaultPolicy) return;

        final TelephonyManager tele = TelephonyManager.from(mContext);
        final SubscriptionManager sub = SubscriptionManager.from(mContext);

        final int[] subIds = sub.getActiveSubscriptionIdList();
        for (int subId : subIds) {
            final String subscriberId = tele.getSubscriberId(subId);
            ensureActiveMobilePolicyAL(subId, subscriberId);
        }
    }

    /**
     * Once any {@link #mNetworkPolicy} are loaded from disk, ensure that we
     * have at least a default mobile policy defined.
     *
     * @param subId to build a default policy for
     * @param subscriberId that we check for an existing policy
     * @return true if a mobile network policy was added, or false one already existed.
     */
    private boolean ensureActiveMobilePolicyAL(int subId, String subscriberId) {
        // Poke around to see if we already have a policy
        final NetworkIdentity probeIdent = new NetworkIdentity(TYPE_MOBILE,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, subscriberId, null, false, true);
        for (int i = mNetworkPolicy.size() - 1; i >= 0; i--) {
            final NetworkTemplate template = mNetworkPolicy.keyAt(i);
            if (template.matches(probeIdent)) {
                if (LOGD) {
                    Slog.d(TAG, "Found template " + template + " which matches subscriber "
                            + NetworkIdentity.scrubSubscriberId(subscriberId));
                }
                return false;
            }
        }

        Slog.i(TAG, "No policy for subscriber " + NetworkIdentity.scrubSubscriberId(subscriberId)
                + "; generating default policy");
        final NetworkPolicy policy = buildDefaultMobilePolicy(subId, subscriberId);
        addNetworkPolicyAL(policy);
        return true;
    }

    private long getPlatformDefaultWarningBytes() {
        final int dataWarningConfig = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_networkPolicyDefaultWarning);
        if (dataWarningConfig == WARNING_DISABLED) {
            return WARNING_DISABLED;
        } else {
            return dataWarningConfig * MB_IN_BYTES;
        }
    }

    private long getPlatformDefaultLimitBytes() {
        return LIMIT_DISABLED;
    }

    @VisibleForTesting
    public NetworkPolicy buildDefaultMobilePolicy(int subId, String subscriberId) {
        final NetworkTemplate template = buildTemplateMobileAll(subscriberId);
        final RecurrenceRule cycleRule = NetworkPolicy
                .buildRule(ZonedDateTime.now().getDayOfMonth(), ZoneId.systemDefault());
        final NetworkPolicy policy = new NetworkPolicy(template, cycleRule,
                getPlatformDefaultWarningBytes(), getPlatformDefaultLimitBytes(),
                SNOOZE_NEVER, SNOOZE_NEVER, true, true);
        synchronized (mUidRulesFirstLock) {
            synchronized (mNetworkPoliciesSecondLock) {
                updateDefaultMobilePolicyAL(subId, policy);
            }
        }
        return policy;
    }

    /**
     * Update the given {@link NetworkPolicy} based on any carrier-provided
     * defaults via {@link SubscriptionPlan} or {@link CarrierConfigManager}.
     * Leaves policy untouched if the user has modified it.
     *
     * @return if the policy was modified
     */
    private boolean updateDefaultMobilePolicyAL(int subId, NetworkPolicy policy) {
        if (!policy.inferred) {
            if (LOGD) Slog.d(TAG, "Ignoring user-defined policy " + policy);
            return false;
        }

        final NetworkPolicy original = new NetworkPolicy(policy.template, policy.cycleRule,
                policy.warningBytes, policy.limitBytes, policy.lastWarningSnooze,
                policy.lastLimitSnooze, policy.metered, policy.inferred);

        final SubscriptionPlan[] plans = mSubscriptionPlans.get(subId);
        if (!ArrayUtils.isEmpty(plans)) {
            final SubscriptionPlan plan = plans[0];
            policy.cycleRule = plan.getCycleRule();
            final long planLimitBytes = plan.getDataLimitBytes();
            if (planLimitBytes == SubscriptionPlan.BYTES_UNKNOWN) {
                policy.warningBytes = getPlatformDefaultWarningBytes();
                policy.limitBytes = getPlatformDefaultLimitBytes();
            } else if (planLimitBytes == SubscriptionPlan.BYTES_UNLIMITED) {
                policy.warningBytes = NetworkPolicy.WARNING_DISABLED;
                policy.limitBytes = NetworkPolicy.LIMIT_DISABLED;
            } else {
                policy.warningBytes = (planLimitBytes * 9) / 10;
                switch (plan.getDataLimitBehavior()) {
                    case SubscriptionPlan.LIMIT_BEHAVIOR_BILLED:
                    case SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED:
                        policy.limitBytes = planLimitBytes;
                        break;
                    default:
                        policy.limitBytes = NetworkPolicy.LIMIT_DISABLED;
                        break;
                }
            }
        } else {
            final PersistableBundle config = mCarrierConfigManager.getConfigForSubId(subId);
            final int currentCycleDay;
            if (policy.cycleRule.isMonthly()) {
                currentCycleDay = policy.cycleRule.start.getDayOfMonth();
            } else {
                currentCycleDay = NetworkPolicy.CYCLE_NONE;
            }
            final int cycleDay = getCycleDayFromCarrierConfig(config, currentCycleDay);
            policy.cycleRule = NetworkPolicy.buildRule(cycleDay, ZoneId.systemDefault());
            policy.warningBytes = getWarningBytesFromCarrierConfig(config, policy.warningBytes);
            policy.limitBytes = getLimitBytesFromCarrierConfig(config, policy.limitBytes);
        }

        if (policy.equals(original)) {
            return false;
        } else {
            Slog.d(TAG, "Updated " + original + " to " + policy);
            return true;
        }
    }

    private void readPolicyAL() {
        if (LOGV) Slog.v(TAG, "readPolicyAL()");

        // clear any existing policy and read from disk
        mNetworkPolicy.clear();
        mSubscriptionPlans.clear();
        mSubscriptionPlansOwner.clear();
        mUidPolicy.clear();

        FileInputStream fis = null;
        try {
            fis = mPolicyFile.openRead();
            final XmlPullParser in = Xml.newPullParser();
            in.setInput(fis, StandardCharsets.UTF_8.name());

             // Must save the <restrict-background> tags and convert them to <uid-policy> later,
             // to skip UIDs that were explicitly blacklisted.
            final SparseBooleanArray whitelistedRestrictBackground = new SparseBooleanArray();

            int type;
            int version = VERSION_INIT;
            boolean insideWhitelist = false;
            while ((type = in.next()) != END_DOCUMENT) {
                final String tag = in.getName();
                if (type == START_TAG) {
                    if (TAG_POLICY_LIST.equals(tag)) {
                        final boolean oldValue = mRestrictBackground;
                        version = readIntAttribute(in, ATTR_VERSION);
                        if (version >= VERSION_ADDED_RESTRICT_BACKGROUND) {
                            mRestrictBackground = readBooleanAttribute(
                                    in, ATTR_RESTRICT_BACKGROUND);
                        } else {
                            mRestrictBackground = false;
                        }
                        if (mRestrictBackground != oldValue) {
                            // Some early services may have read the default value,
                            // so notify them that it's changed
                            mHandler.obtainMessage(MSG_RESTRICT_BACKGROUND_CHANGED,
                                    mRestrictBackground ? 1 : 0, 0).sendToTarget();
                        }

                    } else if (TAG_NETWORK_POLICY.equals(tag)) {
                        final int networkTemplate = readIntAttribute(in, ATTR_NETWORK_TEMPLATE);
                        final String subscriberId = in.getAttributeValue(null, ATTR_SUBSCRIBER_ID);
                        final String networkId;
                        if (version >= VERSION_ADDED_NETWORK_ID) {
                            networkId = in.getAttributeValue(null, ATTR_NETWORK_ID);
                        } else {
                            networkId = null;
                        }
                        final RecurrenceRule cycleRule;
                        if (version >= VERSION_ADDED_CYCLE) {
                            final String start = readStringAttribute(in, ATTR_CYCLE_START);
                            final String end = readStringAttribute(in, ATTR_CYCLE_END);
                            final String period = readStringAttribute(in, ATTR_CYCLE_PERIOD);
                            cycleRule = new RecurrenceRule(
                                    RecurrenceRule.convertZonedDateTime(start),
                                    RecurrenceRule.convertZonedDateTime(end),
                                    RecurrenceRule.convertPeriod(period));
                        } else {
                            final int cycleDay = readIntAttribute(in, ATTR_CYCLE_DAY);
                            final String cycleTimezone;
                            if (version >= VERSION_ADDED_TIMEZONE) {
                                cycleTimezone = in.getAttributeValue(null, ATTR_CYCLE_TIMEZONE);
                            } else {
                                cycleTimezone = "UTC";
                            }
                            cycleRule = NetworkPolicy.buildRule(cycleDay, ZoneId.of(cycleTimezone));
                        }
                        final long warningBytes = readLongAttribute(in, ATTR_WARNING_BYTES);
                        final long limitBytes = readLongAttribute(in, ATTR_LIMIT_BYTES);
                        final long lastLimitSnooze;
                        if (version >= VERSION_SPLIT_SNOOZE) {
                            lastLimitSnooze = readLongAttribute(in, ATTR_LAST_LIMIT_SNOOZE);
                        } else if (version >= VERSION_ADDED_SNOOZE) {
                            lastLimitSnooze = readLongAttribute(in, ATTR_LAST_SNOOZE);
                        } else {
                            lastLimitSnooze = SNOOZE_NEVER;
                        }
                        final boolean metered;
                        if (version >= VERSION_ADDED_METERED) {
                            metered = readBooleanAttribute(in, ATTR_METERED);
                        } else {
                            switch (networkTemplate) {
                                case MATCH_MOBILE_3G_LOWER:
                                case MATCH_MOBILE_4G:
                                case MATCH_MOBILE_ALL:
                                    metered = true;
                                    break;
                                default:
                                    metered = false;
                            }
                        }
                        final long lastWarningSnooze;
                        if (version >= VERSION_SPLIT_SNOOZE) {
                            lastWarningSnooze = readLongAttribute(in, ATTR_LAST_WARNING_SNOOZE);
                        } else {
                            lastWarningSnooze = SNOOZE_NEVER;
                        }
                        final boolean inferred;
                        if (version >= VERSION_ADDED_INFERRED) {
                            inferred = readBooleanAttribute(in, ATTR_INFERRED);
                        } else {
                            inferred = false;
                        }

                        final NetworkTemplate template = new NetworkTemplate(networkTemplate,
                                subscriberId, networkId);
                        if (template.isPersistable()) {
                            mNetworkPolicy.put(template, new NetworkPolicy(template, cycleRule,
                                    warningBytes, limitBytes, lastWarningSnooze,
                                    lastLimitSnooze, metered, inferred));
                        }

                    } else if (TAG_SUBSCRIPTION_PLAN.equals(tag)) {
                        final String start = readStringAttribute(in, ATTR_CYCLE_START);
                        final String end = readStringAttribute(in, ATTR_CYCLE_END);
                        final String period = readStringAttribute(in, ATTR_CYCLE_PERIOD);
                        final SubscriptionPlan.Builder builder = new SubscriptionPlan.Builder(
                                RecurrenceRule.convertZonedDateTime(start),
                                RecurrenceRule.convertZonedDateTime(end),
                                RecurrenceRule.convertPeriod(period));
                        builder.setTitle(readStringAttribute(in, ATTR_TITLE));
                        builder.setSummary(readStringAttribute(in, ATTR_SUMMARY));

                        final long limitBytes = readLongAttribute(in, ATTR_LIMIT_BYTES,
                                SubscriptionPlan.BYTES_UNKNOWN);
                        final int limitBehavior = readIntAttribute(in, ATTR_LIMIT_BEHAVIOR,
                                SubscriptionPlan.LIMIT_BEHAVIOR_UNKNOWN);
                        if (limitBytes != SubscriptionPlan.BYTES_UNKNOWN
                                && limitBehavior != SubscriptionPlan.LIMIT_BEHAVIOR_UNKNOWN) {
                            builder.setDataLimit(limitBytes, limitBehavior);
                        }

                        final long usageBytes = readLongAttribute(in, ATTR_USAGE_BYTES,
                                SubscriptionPlan.BYTES_UNKNOWN);
                        final long usageTime = readLongAttribute(in, ATTR_USAGE_TIME,
                                SubscriptionPlan.TIME_UNKNOWN);
                        if (usageBytes != SubscriptionPlan.BYTES_UNKNOWN
                                && usageTime != SubscriptionPlan.TIME_UNKNOWN) {
                            builder.setDataUsage(usageBytes, usageTime);
                        }

                        final int subId = readIntAttribute(in, ATTR_SUB_ID);
                        final SubscriptionPlan plan = builder.build();
                        mSubscriptionPlans.put(subId, ArrayUtils.appendElement(
                                SubscriptionPlan.class, mSubscriptionPlans.get(subId), plan));

                        final String ownerPackage = readStringAttribute(in, ATTR_OWNER_PACKAGE);
                        mSubscriptionPlansOwner.put(subId, ownerPackage);

                    } else if (TAG_UID_POLICY.equals(tag)) {
                        final int uid = readIntAttribute(in, ATTR_UID);
                        final int policy = readIntAttribute(in, ATTR_POLICY);

                        if (UserHandle.isApp(uid)) {
                            setUidPolicyUncheckedUL(uid, policy, false);
                        } else {
                            Slog.w(TAG, "unable to apply policy to UID " + uid + "; ignoring");
                        }
                    } else if (TAG_APP_POLICY.equals(tag)) {
                        final int appId = readIntAttribute(in, ATTR_APP_ID);
                        final int policy = readIntAttribute(in, ATTR_POLICY);

                        // TODO: set for other users during upgrade
                        // app policy is deprecated so this is only used in pre system user split.
                        final int uid = UserHandle.getUid(UserHandle.USER_SYSTEM, appId);
                        if (UserHandle.isApp(uid)) {
                            setUidPolicyUncheckedUL(uid, policy, false);
                        } else {
                            Slog.w(TAG, "unable to apply policy to UID " + uid + "; ignoring");
                        }
                    } else if (TAG_WHITELIST.equals(tag)) {
                        insideWhitelist = true;
                    } else if (TAG_RESTRICT_BACKGROUND.equals(tag) && insideWhitelist) {
                        final int uid = readIntAttribute(in, ATTR_UID);
                        whitelistedRestrictBackground.append(uid, true);
                    } else if (TAG_REVOKED_RESTRICT_BACKGROUND.equals(tag) && insideWhitelist) {
                        final int uid = readIntAttribute(in, ATTR_UID);
                        mRestrictBackgroundWhitelistRevokedUids.put(uid, true);
                    }
                } else if (type == END_TAG) {
                    if (TAG_WHITELIST.equals(tag)) {
                        insideWhitelist = false;
                    }

                }
            }

            final int size = whitelistedRestrictBackground.size();
            for (int i = 0; i < size; i++) {
                final int uid = whitelistedRestrictBackground.keyAt(i);
                final int policy = mUidPolicy.get(uid, POLICY_NONE);
                if ((policy & POLICY_REJECT_METERED_BACKGROUND) != 0) {
                    Slog.w(TAG, "ignoring restrict-background-whitelist for " + uid
                            + " because its policy is " + uidPoliciesToString(policy));
                    continue;
                }
                if (UserHandle.isApp(uid)) {
                    final int newPolicy = policy | POLICY_ALLOW_METERED_BACKGROUND;
                    if (LOGV)
                        Log.v(TAG, "new policy for " + uid + ": " + uidPoliciesToString(newPolicy));
                    setUidPolicyUncheckedUL(uid, newPolicy, false);
                } else {
                    Slog.w(TAG, "unable to update policy on UID " + uid);
                }
            }

        } catch (FileNotFoundException e) {
            // missing policy is okay, probably first boot
            upgradeDefaultBackgroundDataUL();
        } catch (Exception e) {
            Log.wtf(TAG, "problem reading network policy", e);
        } finally {
            IoUtils.closeQuietly(fis);
        }
    }

    /**
     * Upgrade legacy background data flags, notifying listeners of one last
     * change to always-true.
     */
    private void upgradeDefaultBackgroundDataUL() {
        // This method is only called when we're unable to find the network policy flag, which
        // usually happens on first boot of a new device and not one that has received an OTA.

        // Seed from the default value configured for this device.
        mRestrictBackground = Settings.Global.getInt(
                mContext.getContentResolver(), Global.DEFAULT_RESTRICT_BACKGROUND_DATA, 0) == 1;

        // NOTE: We used to read the legacy setting here :
        //
        // final int legacyFlagValue = Settings.Secure.getInt(
        //        mContext.getContentResolver(), Settings.Secure.BACKGROUND_DATA, ..);
        //
        // This is no longer necessary because we will never upgrade directly from Gingerbread
        // to O+. Devices upgrading from ICS onwards to O will have a netpolicy.xml file that
        // contains the correct value that we will continue to use.
    }

    /**
     * Perform upgrade step of moving any user-defined meterness overrides over
     * into {@link WifiConfiguration}.
     */
    private void upgradeWifiMeteredOverrideAL() {
        boolean modified = false;
        final WifiManager wm = mContext.getSystemService(WifiManager.class);
        final List<WifiConfiguration> configs = wm.getConfiguredNetworks();
        for (int i = 0; i < mNetworkPolicy.size(); ) {
            final NetworkPolicy policy = mNetworkPolicy.valueAt(i);
            if (policy.template.getMatchRule() == NetworkTemplate.MATCH_WIFI
                    && !policy.inferred) {
                mNetworkPolicy.removeAt(i);
                modified = true;

                final String networkId = resolveNetworkId(policy.template.getNetworkId());
                for (WifiConfiguration config : configs) {
                    if (Objects.equals(resolveNetworkId(config), networkId)) {
                        Slog.d(TAG, "Found network " + networkId + "; upgrading metered hint");
                        config.meteredOverride = policy.metered
                                ? WifiConfiguration.METERED_OVERRIDE_METERED
                                : WifiConfiguration.METERED_OVERRIDE_NOT_METERED;
                        wm.updateNetwork(config);
                    }
                }
            } else {
                i++;
            }
        }
        if (modified) {
            writePolicyAL();
        }
    }

    void writePolicyAL() {
        if (LOGV) Slog.v(TAG, "writePolicyAL()");

        FileOutputStream fos = null;
        try {
            fos = mPolicyFile.startWrite();

            XmlSerializer out = new FastXmlSerializer();
            out.setOutput(fos, StandardCharsets.UTF_8.name());
            out.startDocument(null, true);

            out.startTag(null, TAG_POLICY_LIST);
            writeIntAttribute(out, ATTR_VERSION, VERSION_LATEST);
            writeBooleanAttribute(out, ATTR_RESTRICT_BACKGROUND, mRestrictBackground);

            // write all known network policies
            for (int i = 0; i < mNetworkPolicy.size(); i++) {
                final NetworkPolicy policy = mNetworkPolicy.valueAt(i);
                final NetworkTemplate template = policy.template;
                if (!template.isPersistable()) continue;

                out.startTag(null, TAG_NETWORK_POLICY);
                writeIntAttribute(out, ATTR_NETWORK_TEMPLATE, template.getMatchRule());
                final String subscriberId = template.getSubscriberId();
                if (subscriberId != null) {
                    out.attribute(null, ATTR_SUBSCRIBER_ID, subscriberId);
                }
                final String networkId = template.getNetworkId();
                if (networkId != null) {
                    out.attribute(null, ATTR_NETWORK_ID, networkId);
                }
                writeStringAttribute(out, ATTR_CYCLE_START,
                        RecurrenceRule.convertZonedDateTime(policy.cycleRule.start));
                writeStringAttribute(out, ATTR_CYCLE_END,
                        RecurrenceRule.convertZonedDateTime(policy.cycleRule.end));
                writeStringAttribute(out, ATTR_CYCLE_PERIOD,
                        RecurrenceRule.convertPeriod(policy.cycleRule.period));
                writeLongAttribute(out, ATTR_WARNING_BYTES, policy.warningBytes);
                writeLongAttribute(out, ATTR_LIMIT_BYTES, policy.limitBytes);
                writeLongAttribute(out, ATTR_LAST_WARNING_SNOOZE, policy.lastWarningSnooze);
                writeLongAttribute(out, ATTR_LAST_LIMIT_SNOOZE, policy.lastLimitSnooze);
                writeBooleanAttribute(out, ATTR_METERED, policy.metered);
                writeBooleanAttribute(out, ATTR_INFERRED, policy.inferred);
                out.endTag(null, TAG_NETWORK_POLICY);
            }

            // write all known subscription plans
            for (int i = 0; i < mSubscriptionPlans.size(); i++) {
                final int subId = mSubscriptionPlans.keyAt(i);
                final String ownerPackage = mSubscriptionPlansOwner.get(subId);
                final SubscriptionPlan[] plans = mSubscriptionPlans.valueAt(i);
                if (ArrayUtils.isEmpty(plans)) continue;

                for (SubscriptionPlan plan : plans) {
                    out.startTag(null, TAG_SUBSCRIPTION_PLAN);
                    writeIntAttribute(out, ATTR_SUB_ID, subId);
                    writeStringAttribute(out, ATTR_OWNER_PACKAGE, ownerPackage);
                    final RecurrenceRule cycleRule = plan.getCycleRule();
                    writeStringAttribute(out, ATTR_CYCLE_START,
                            RecurrenceRule.convertZonedDateTime(cycleRule.start));
                    writeStringAttribute(out, ATTR_CYCLE_END,
                            RecurrenceRule.convertZonedDateTime(cycleRule.end));
                    writeStringAttribute(out, ATTR_CYCLE_PERIOD,
                            RecurrenceRule.convertPeriod(cycleRule.period));
                    writeStringAttribute(out, ATTR_TITLE, plan.getTitle());
                    writeStringAttribute(out, ATTR_SUMMARY, plan.getSummary());
                    writeLongAttribute(out, ATTR_LIMIT_BYTES, plan.getDataLimitBytes());
                    writeIntAttribute(out, ATTR_LIMIT_BEHAVIOR, plan.getDataLimitBehavior());
                    writeLongAttribute(out, ATTR_USAGE_BYTES, plan.getDataUsageBytes());
                    writeLongAttribute(out, ATTR_USAGE_TIME, plan.getDataUsageTime());
                    out.endTag(null, TAG_SUBSCRIPTION_PLAN);
                }
            }

            // write all known uid policies
            for (int i = 0; i < mUidPolicy.size(); i++) {
                final int uid = mUidPolicy.keyAt(i);
                final int policy = mUidPolicy.valueAt(i);

                // skip writing empty policies
                if (policy == POLICY_NONE) continue;

                out.startTag(null, TAG_UID_POLICY);
                writeIntAttribute(out, ATTR_UID, uid);
                writeIntAttribute(out, ATTR_POLICY, policy);
                out.endTag(null, TAG_UID_POLICY);
            }

            out.endTag(null, TAG_POLICY_LIST);

            // write all whitelists
            out.startTag(null, TAG_WHITELIST);

            // revoked restrict background whitelist
            int size = mRestrictBackgroundWhitelistRevokedUids.size();
            for (int i = 0; i < size; i++) {
                final int uid = mRestrictBackgroundWhitelistRevokedUids.keyAt(i);
                out.startTag(null, TAG_REVOKED_RESTRICT_BACKGROUND);
                writeIntAttribute(out, ATTR_UID, uid);
                out.endTag(null, TAG_REVOKED_RESTRICT_BACKGROUND);
            }

            out.endTag(null, TAG_WHITELIST);

            out.endDocument();

            mPolicyFile.finishWrite(fos);
        } catch (IOException e) {
            if (fos != null) {
                mPolicyFile.failWrite(fos);
            }
        }
    }

    @Override
    public void setUidPolicy(int uid, int policy) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        if (!UserHandle.isApp(uid)) {
            throw new IllegalArgumentException("cannot apply policy to UID " + uid);
        }
        synchronized (mUidRulesFirstLock) {
            final long token = Binder.clearCallingIdentity();
            try {
                final int oldPolicy = mUidPolicy.get(uid, POLICY_NONE);
                if (oldPolicy != policy) {
                    setUidPolicyUncheckedUL(uid, oldPolicy, policy, true);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    @Override
    public void addUidPolicy(int uid, int policy) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        if (!UserHandle.isApp(uid)) {
            throw new IllegalArgumentException("cannot apply policy to UID " + uid);
        }

        synchronized (mUidRulesFirstLock) {
            final int oldPolicy = mUidPolicy.get(uid, POLICY_NONE);
            policy |= oldPolicy;
            if (oldPolicy != policy) {
                setUidPolicyUncheckedUL(uid, oldPolicy, policy, true);
            }
        }
    }

    @Override
    public void removeUidPolicy(int uid, int policy) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        if (!UserHandle.isApp(uid)) {
            throw new IllegalArgumentException("cannot apply policy to UID " + uid);
        }

        synchronized (mUidRulesFirstLock) {
            final int oldPolicy = mUidPolicy.get(uid, POLICY_NONE);
            policy = oldPolicy & ~policy;
            if (oldPolicy != policy) {
                setUidPolicyUncheckedUL(uid, oldPolicy, policy, true);
            }
        }
    }

    private void setUidPolicyUncheckedUL(int uid, int oldPolicy, int policy, boolean persist) {
        setUidPolicyUncheckedUL(uid, policy, persist);

        final boolean notifyApp;
        if (!isUidValidForWhitelistRules(uid)) {
            notifyApp = false;
        } else {
            final boolean wasBlacklisted = oldPolicy == POLICY_REJECT_METERED_BACKGROUND;
            final boolean isBlacklisted = policy == POLICY_REJECT_METERED_BACKGROUND;
            final boolean wasWhitelisted = oldPolicy == POLICY_ALLOW_METERED_BACKGROUND;
            final boolean isWhitelisted = policy == POLICY_ALLOW_METERED_BACKGROUND;
            final boolean wasBlocked = wasBlacklisted || (mRestrictBackground && !wasWhitelisted);
            final boolean isBlocked = isBlacklisted || (mRestrictBackground && !isWhitelisted);
            if ((wasWhitelisted && (!isWhitelisted || isBlacklisted))
                    && mDefaultRestrictBackgroundWhitelistUids.get(uid)
                    && !mRestrictBackgroundWhitelistRevokedUids.get(uid)) {
                if (LOGD)
                    Slog.d(TAG, "Adding uid " + uid + " to revoked restrict background whitelist");
                mRestrictBackgroundWhitelistRevokedUids.append(uid, true);
            }
            notifyApp = wasBlocked != isBlocked;
        }
        mHandler.obtainMessage(MSG_POLICIES_CHANGED, uid, policy, Boolean.valueOf(notifyApp))
                .sendToTarget();
    }

    private void setUidPolicyUncheckedUL(int uid, int policy, boolean persist) {
        if (policy == POLICY_NONE) {
            mUidPolicy.delete(uid);
        } else {
            mUidPolicy.put(uid, policy);
        }

        // uid policy changed, recompute rules and persist policy.
        updateRulesForDataUsageRestrictionsUL(uid);
        if (persist) {
            synchronized (mNetworkPoliciesSecondLock) {
                writePolicyAL();
            }
        }
    }

    @Override
    public int getUidPolicy(int uid) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        synchronized (mUidRulesFirstLock) {
            return mUidPolicy.get(uid, POLICY_NONE);
        }
    }

    @Override
    public int[] getUidsWithPolicy(int policy) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        int[] uids = new int[0];
        synchronized (mUidRulesFirstLock) {
            for (int i = 0; i < mUidPolicy.size(); i++) {
                final int uid = mUidPolicy.keyAt(i);
                final int uidPolicy = mUidPolicy.valueAt(i);
                if ((policy == POLICY_NONE && uidPolicy == POLICY_NONE) ||
                        (uidPolicy & policy) != 0) {
                    uids = appendInt(uids, uid);
                }
            }
        }
        return uids;
    }

    /**
     * Removes any persistable state associated with given {@link UserHandle}, persisting
     * if any changes that are made.
     */
    boolean removeUserStateUL(int userId, boolean writePolicy) {

        if (LOGV) Slog.v(TAG, "removeUserStateUL()");
        boolean changed = false;

        // Remove entries from revoked default restricted background UID whitelist
        for (int i = mRestrictBackgroundWhitelistRevokedUids.size() - 1; i >= 0; i--) {
            final int uid = mRestrictBackgroundWhitelistRevokedUids.keyAt(i);
            if (UserHandle.getUserId(uid) == userId) {
                mRestrictBackgroundWhitelistRevokedUids.removeAt(i);
                changed = true;
            }
        }

        // Remove associated UID policies
        int[] uids = new int[0];
        for (int i = 0; i < mUidPolicy.size(); i++) {
            final int uid = mUidPolicy.keyAt(i);
            if (UserHandle.getUserId(uid) == userId) {
                uids = appendInt(uids, uid);
            }
        }

        if (uids.length > 0) {
            for (int uid : uids) {
                mUidPolicy.delete(uid);
            }
            changed = true;
        }
        synchronized (mNetworkPoliciesSecondLock) {
            updateRulesForGlobalChangeAL(true);
            if (writePolicy && changed) {
                writePolicyAL();
            }
        }
        return changed;
    }

    @Override
    public void registerListener(INetworkPolicyListener listener) {
        // TODO: create permission for observing network policy
        mContext.enforceCallingOrSelfPermission(CONNECTIVITY_INTERNAL, TAG);
        mListeners.register(listener);
    }

    @Override
    public void unregisterListener(INetworkPolicyListener listener) {
        // TODO: create permission for observing network policy
        mContext.enforceCallingOrSelfPermission(CONNECTIVITY_INTERNAL, TAG);
        mListeners.unregister(listener);
    }

    @Override
    public void setNetworkPolicies(NetworkPolicy[] policies) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        final long token = Binder.clearCallingIdentity();
        try {
            maybeRefreshTrustedTime();
            synchronized (mUidRulesFirstLock) {
                synchronized (mNetworkPoliciesSecondLock) {
                    normalizePoliciesNL(policies);
                    handleNetworkPoliciesUpdateAL(false);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    void addNetworkPolicyAL(NetworkPolicy policy) {
        NetworkPolicy[] policies = getNetworkPolicies(mContext.getOpPackageName());
        policies = ArrayUtils.appendElement(NetworkPolicy.class, policies, policy);
        setNetworkPolicies(policies);
    }

    @Override
    public NetworkPolicy[] getNetworkPolicies(String callingPackage) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);
        try {
            mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, TAG);
            // SKIP checking run-time OP_READ_PHONE_STATE since caller or self has PRIVILEGED
            // permission
        } catch (SecurityException e) {
            mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, TAG);

            if (mAppOps.noteOp(AppOpsManager.OP_READ_PHONE_STATE, Binder.getCallingUid(),
                    callingPackage) != AppOpsManager.MODE_ALLOWED) {
                return new NetworkPolicy[0];
            }
        }

        synchronized (mNetworkPoliciesSecondLock) {
            final int size = mNetworkPolicy.size();
            final NetworkPolicy[] policies = new NetworkPolicy[size];
            for (int i = 0; i < size; i++) {
                policies[i] = mNetworkPolicy.valueAt(i);
            }
            return policies;
        }
    }

    private void normalizePoliciesNL() {
        normalizePoliciesNL(getNetworkPolicies(mContext.getOpPackageName()));
    }

    private void normalizePoliciesNL(NetworkPolicy[] policies) {
        final TelephonyManager tele = TelephonyManager.from(mContext);
        final String[] merged = tele.getMergedSubscriberIds();

        mNetworkPolicy.clear();
        for (NetworkPolicy policy : policies) {
            // When two normalized templates conflict, prefer the most
            // restrictive policy
            policy.template = NetworkTemplate.normalize(policy.template, merged);
            final NetworkPolicy existing = mNetworkPolicy.get(policy.template);
            if (existing == null || existing.compareTo(policy) > 0) {
                if (existing != null) {
                    Slog.d(TAG, "Normalization replaced " + existing + " with " + policy);
                }
                mNetworkPolicy.put(policy.template, policy);
            }
        }
    }

    @Override
    public void snoozeLimit(NetworkTemplate template) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        final long token = Binder.clearCallingIdentity();
        try {
            performSnooze(template, TYPE_LIMIT);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    void performSnooze(NetworkTemplate template, int type) {
        maybeRefreshTrustedTime();
        final long currentTime = currentTimeMillis();
        synchronized (mUidRulesFirstLock) {
            synchronized (mNetworkPoliciesSecondLock) {
                // find and snooze local policy that matches
                final NetworkPolicy policy = mNetworkPolicy.get(template);
                if (policy == null) {
                    throw new IllegalArgumentException("unable to find policy for " + template);
                }

                switch (type) {
                    case TYPE_WARNING:
                        policy.lastWarningSnooze = currentTime;
                        break;
                    case TYPE_LIMIT:
                        policy.lastLimitSnooze = currentTime;
                        break;
                    default:
                        throw new IllegalArgumentException("unexpected type");
                }

                handleNetworkPoliciesUpdateAL(true);
            }
        }
    }

    @Override
    public void onTetheringChanged(String iface, boolean tethering) {
        // No need to enforce permission because setRestrictBackground() will do it.
        if (LOGD) Log.d(TAG, "onTetherStateChanged(" + iface + ", " + tethering + ")");
        synchronized (mUidRulesFirstLock) {
            if (mRestrictBackground && tethering) {
                Log.d(TAG, "Tethering on (" + iface +"); disable Data Saver");
                setRestrictBackground(false);
            }
        }
    }

    @Override
    public void setRestrictBackground(boolean restrictBackground) {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "setRestrictBackground");
        try {
            mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);
            final long token = Binder.clearCallingIdentity();
            try {
                maybeRefreshTrustedTime();
                synchronized (mUidRulesFirstLock) {
                    if (restrictBackground == mRestrictBackground) {
                        // Ideally, UI should never allow this scenario...
                        Slog.w(TAG, "setRestrictBackground: already " + restrictBackground);
                        return;
                    }
                    setRestrictBackgroundUL(restrictBackground);
                }

            } finally {
                Binder.restoreCallingIdentity(token);
            }

            mHandler.obtainMessage(MSG_RESTRICT_BACKGROUND_CHANGED, restrictBackground ? 1 : 0, 0)
                    .sendToTarget();
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void setRestrictBackgroundUL(boolean restrictBackground) {
        Slog.d(TAG, "setRestrictBackgroundUL(): " + restrictBackground);
        final boolean oldRestrictBackground = mRestrictBackground;
        mRestrictBackground = restrictBackground;
        // Must whitelist foreground apps before turning data saver mode on.
        // TODO: there is no need to iterate through all apps here, just those in the foreground,
        // so it could call AM to get the UIDs of such apps, and iterate through them instead.
        updateRulesForRestrictBackgroundUL();
        try {
            if (!mNetworkManager.setDataSaverModeEnabled(mRestrictBackground)) {
                Slog.e(TAG, "Could not change Data Saver Mode on NMS to " + mRestrictBackground);
                mRestrictBackground = oldRestrictBackground;
                // TODO: if it knew the foreground apps (see TODO above), it could call
                // updateRulesForRestrictBackgroundUL() again to restore state.
                return;
            }
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }

        if (mRestrictBackgroundPowerState.globalBatterySaverEnabled) {
            mRestrictBackgroundChangedInBsm = true;
        }
        synchronized (mNetworkPoliciesSecondLock) {
            updateNotificationsNL();
            writePolicyAL();
        }
    }

    @Override
    public int getRestrictBackgroundByCaller() {
        mContext.enforceCallingOrSelfPermission(ACCESS_NETWORK_STATE, TAG);
        final int uid = Binder.getCallingUid();

        synchronized (mUidRulesFirstLock) {
            // Must clear identity because getUidPolicy() is restricted to system.
            final long token = Binder.clearCallingIdentity();
            final int policy;
            try {
                policy = getUidPolicy(uid);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            if (policy == POLICY_REJECT_METERED_BACKGROUND) {
                // App is blacklisted.
                return RESTRICT_BACKGROUND_STATUS_ENABLED;
            }
            if (!mRestrictBackground) {
                return RESTRICT_BACKGROUND_STATUS_DISABLED;
            }
            return (mUidPolicy.get(uid) & POLICY_ALLOW_METERED_BACKGROUND) != 0
                    ? RESTRICT_BACKGROUND_STATUS_WHITELISTED
                    : RESTRICT_BACKGROUND_STATUS_ENABLED;
        }
    }

    @Override
    public boolean getRestrictBackground() {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        synchronized (mUidRulesFirstLock) {
            return mRestrictBackground;
        }
    }

    @Override
    public void setDeviceIdleMode(boolean enabled) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "setDeviceIdleMode");
        try {
            synchronized (mUidRulesFirstLock) {
                if (mDeviceIdleMode == enabled) {
                    return;
                }
                mDeviceIdleMode = enabled;
                if (mSystemReady) {
                    // Device idle change means we need to rebuild rules for all
                    // known apps, so do a global refresh.
                    updateRulesForRestrictPowerUL();
                }
            }
            if (enabled) {
                EventLogTags.writeDeviceIdleOnPhase("net");
            } else {
                EventLogTags.writeDeviceIdleOffPhase("net");
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    @Override
    public void setWifiMeteredOverride(String networkId, int meteredOverride) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);
        final long token = Binder.clearCallingIdentity();
        try {
            final WifiManager wm = mContext.getSystemService(WifiManager.class);
            final List<WifiConfiguration> configs = wm.getConfiguredNetworks();
            for (WifiConfiguration config : configs) {
                if (Objects.equals(resolveNetworkId(config), networkId)) {
                    config.meteredOverride = meteredOverride;
                    wm.updateNetwork(config);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    @Deprecated
    public NetworkQuotaInfo getNetworkQuotaInfo(NetworkState state) {
        Log.w(TAG, "Shame on UID " + Binder.getCallingUid()
                + " for calling the hidden API getNetworkQuotaInfo(). Shame!");
        return new NetworkQuotaInfo();
    }

    private void enforceSubscriptionPlanAccess(int subId, int callingUid, String callingPackage) {
        // Verify they're not lying about package name
        mAppOps.checkPackage(callingUid, callingPackage);

        final SubscriptionInfo si;
        final PersistableBundle config;
        final long token = Binder.clearCallingIdentity();
        try {
            si = mContext.getSystemService(SubscriptionManager.class)
                    .getActiveSubscriptionInfo(subId);
            config = mCarrierConfigManager.getConfigForSubId(subId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        // First check: is caller the CarrierService?
        if (si != null) {
            if (si.isEmbedded() && si.canManageSubscription(mContext, callingPackage)) {
                return;
            }
        }

        // Second check: has the CarrierService delegated access?
        if (config != null) {
            final String overridePackage = config
                    .getString(CarrierConfigManager.KEY_CONFIG_PLANS_PACKAGE_OVERRIDE_STRING, null);
            if (!TextUtils.isEmpty(overridePackage)
                    && Objects.equals(overridePackage, callingPackage)) {
                return;
            }
        }

        // Third check: is caller the fallback/default CarrierService?
        final String defaultPackage = mCarrierConfigManager.getDefaultCarrierServicePackageName();
        if (!TextUtils.isEmpty(defaultPackage)
                && Objects.equals(defaultPackage, callingPackage)) {
            return;
        }

        // Final check: does the caller hold a permission?
        mContext.enforceCallingOrSelfPermission(MANAGE_SUBSCRIPTION_PLANS, TAG);
    }

    @Override
    public SubscriptionPlan[] getSubscriptionPlans(int subId, String callingPackage) {
        enforceSubscriptionPlanAccess(subId, Binder.getCallingUid(), callingPackage);

        final String fake = SystemProperties.get("fw.fake_plan");
        if (!TextUtils.isEmpty(fake)) {
            final List<SubscriptionPlan> plans = new ArrayList<>();
            if ("month_hard".equals(fake)) {
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile")
                        .setDataLimit(5 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_BILLED)
                        .setDataUsage(1 * TrafficStats.GB_IN_BYTES,
                                ZonedDateTime.now().minusHours(36).toInstant().toEpochMilli())
                        .build());
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2017-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile Happy")
                        .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                                SubscriptionPlan.LIMIT_BEHAVIOR_BILLED)
                        .setDataUsage(5 * TrafficStats.GB_IN_BYTES,
                                ZonedDateTime.now().minusHours(36).toInstant().toEpochMilli())
                        .build());
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2017-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile, Charged after limit")
                        .setDataLimit(5 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_BILLED)
                        .setDataUsage(5 * TrafficStats.GB_IN_BYTES,
                                ZonedDateTime.now().minusHours(36).toInstant().toEpochMilli())
                        .build());
            } else if ("month_soft".equals(fake)) {
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile is the carriers name who this plan belongs to")
                        .setSummary("Crazy unlimited bandwidth plan with incredibly long title "
                                + "that should be cut off to prevent UI from looking terrible")
                        .setDataLimit(5 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                        .setDataUsage(1 * TrafficStats.GB_IN_BYTES,
                                ZonedDateTime.now().minusHours(1).toInstant().toEpochMilli())
                        .build());
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2017-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile, Throttled after limit")
                        .setDataLimit(5 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                        .setDataUsage(5 * TrafficStats.GB_IN_BYTES,
                                ZonedDateTime.now().minusHours(1).toInstant().toEpochMilli())
                        .build());
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2017-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile, No data connection after limit")
                        .setDataLimit(5 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                        .setDataUsage(5 * TrafficStats.GB_IN_BYTES,
                                ZonedDateTime.now().minusHours(1).toInstant().toEpochMilli())
                        .build());

            } else if ("month_none".equals(fake)) {
                plans.add(SubscriptionPlan.Builder
                        .createRecurringMonthly(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"))
                        .setTitle("G-Mobile")
                        .build());
            } else if ("prepaid".equals(fake)) {
                plans.add(SubscriptionPlan.Builder
                        .createNonrecurring(ZonedDateTime.now().minusDays(20),
                                ZonedDateTime.now().plusDays(10))
                        .setTitle("G-Mobile")
                        .setDataLimit(512 * TrafficStats.MB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                        .setDataUsage(100 * TrafficStats.MB_IN_BYTES,
                                ZonedDateTime.now().minusHours(3).toInstant().toEpochMilli())
                        .build());
            } else if ("prepaid_crazy".equals(fake)) {
                plans.add(SubscriptionPlan.Builder
                        .createNonrecurring(ZonedDateTime.now().minusDays(20),
                                ZonedDateTime.now().plusDays(10))
                        .setTitle("G-Mobile Anytime")
                        .setDataLimit(512 * TrafficStats.MB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                        .setDataUsage(100 * TrafficStats.MB_IN_BYTES,
                                ZonedDateTime.now().minusHours(3).toInstant().toEpochMilli())
                        .build());
                plans.add(SubscriptionPlan.Builder
                        .createNonrecurring(ZonedDateTime.now().minusDays(10),
                                ZonedDateTime.now().plusDays(20))
                        .setTitle("G-Mobile Nickel Nights")
                        .setSummary("5¢/GB between 1-5AM")
                        .setDataLimit(5 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                        .setDataUsage(15 * TrafficStats.MB_IN_BYTES,
                                ZonedDateTime.now().minusHours(30).toInstant().toEpochMilli())
                        .build());
                plans.add(SubscriptionPlan.Builder
                        .createNonrecurring(ZonedDateTime.now().minusDays(10),
                                ZonedDateTime.now().plusDays(20))
                        .setTitle("G-Mobile Bonus 3G")
                        .setSummary("Unlimited 3G data")
                        .setDataLimit(1 * TrafficStats.GB_IN_BYTES,
                                SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                        .setDataUsage(300 * TrafficStats.MB_IN_BYTES,
                                ZonedDateTime.now().minusHours(1).toInstant().toEpochMilli())
                        .build());
            } else if ("unlimited".equals(fake)) {
                plans.add(SubscriptionPlan.Builder
                        .createNonrecurring(ZonedDateTime.now().minusDays(20),
                                ZonedDateTime.now().plusDays(10))
                        .setTitle("G-Mobile Awesome")
                        .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                                SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                        .setDataUsage(50 * TrafficStats.MB_IN_BYTES,
                                ZonedDateTime.now().minusHours(3).toInstant().toEpochMilli())
                        .build());
            }
            return plans.toArray(new SubscriptionPlan[plans.size()]);
        }

        synchronized (mNetworkPoliciesSecondLock) {
            // Only give out plan details to the package that defined them,
            // so that we don't risk leaking plans between apps. We always
            // let in core system components (like the Settings app).
            final String ownerPackage = mSubscriptionPlansOwner.get(subId);
            if (Objects.equals(ownerPackage, callingPackage)
                    || (UserHandle.getCallingAppId() == android.os.Process.SYSTEM_UID)) {
                return mSubscriptionPlans.get(subId);
            } else {
                Log.w(TAG, "Not returning plans because caller " + callingPackage
                        + " doesn't match owner " + ownerPackage);
                return null;
            }
        }
    }

    @Override
    public void setSubscriptionPlans(int subId, SubscriptionPlan[] plans, String callingPackage) {
        enforceSubscriptionPlanAccess(subId, Binder.getCallingUid(), callingPackage);

        for (SubscriptionPlan plan : plans) {
            Preconditions.checkNotNull(plan);
        }

        final long token = Binder.clearCallingIdentity();
        try {
            maybeRefreshTrustedTime();
            synchronized (mUidRulesFirstLock) {
                synchronized (mNetworkPoliciesSecondLock) {
                    mSubscriptionPlans.put(subId, plans);
                    mSubscriptionPlansOwner.put(subId, callingPackage);

                    final String subscriberId = mContext.getSystemService(TelephonyManager.class)
                            .getSubscriberId(subId);
                    ensureActiveMobilePolicyAL(subId, subscriberId);
                    maybeUpdateMobilePolicyCycleAL(subId);
                    handleNetworkPoliciesUpdateAL(true);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, writer)) return;

        final IndentingPrintWriter fout = new IndentingPrintWriter(writer, "  ");

        final ArraySet<String> argSet = new ArraySet<String>(args.length);
        for (String arg : args) {
            argSet.add(arg);
        }

        synchronized (mUidRulesFirstLock) {
            synchronized (mNetworkPoliciesSecondLock) {
                if (argSet.contains("--unsnooze")) {
                    for (int i = mNetworkPolicy.size()-1; i >= 0; i--) {
                        mNetworkPolicy.valueAt(i).clearSnooze();
                    }

                    handleNetworkPoliciesUpdateAL(true);

                    fout.println("Cleared snooze timestamps");
                    return;
                }

                fout.print("System ready: "); fout.println(mSystemReady);
                fout.print("Restrict background: "); fout.println(mRestrictBackground);
                fout.print("Restrict power: "); fout.println(mRestrictPower);
                fout.print("Device idle: "); fout.println(mDeviceIdleMode);
                fout.print("Metered ifaces: "); fout.println(String.valueOf(mMeteredIfaces));

                fout.println();
                fout.println("Network policies:");
                fout.increaseIndent();
                for (int i = 0; i < mNetworkPolicy.size(); i++) {
                    fout.println(mNetworkPolicy.valueAt(i).toString());
                }
                fout.decreaseIndent();

                fout.println();
                fout.println("Subscription plans:");
                fout.increaseIndent();
                for (int i = 0; i < mSubscriptionPlans.size(); i++) {
                    final int subId = mSubscriptionPlans.keyAt(i);
                    fout.println("Subscriber ID " + subId + ":");
                    fout.increaseIndent();
                    final SubscriptionPlan[] plans = mSubscriptionPlans.valueAt(i);
                    if (!ArrayUtils.isEmpty(plans)) {
                        for (SubscriptionPlan plan : plans) {
                            fout.println(plan);
                        }
                    }
                    fout.decreaseIndent();
                }
                fout.decreaseIndent();

                fout.println();
                fout.println("Policy for UIDs:");
                fout.increaseIndent();
                int size = mUidPolicy.size();
                for (int i = 0; i < size; i++) {
                    final int uid = mUidPolicy.keyAt(i);
                    final int policy = mUidPolicy.valueAt(i);
                    fout.print("UID=");
                    fout.print(uid);
                    fout.print(" policy=");
                    fout.print(uidPoliciesToString(policy));
                    fout.println();
                }
                fout.decreaseIndent();

                size = mPowerSaveWhitelistExceptIdleAppIds.size();
                if (size > 0) {
                    fout.println("Power save whitelist (except idle) app ids:");
                    fout.increaseIndent();
                    for (int i = 0; i < size; i++) {
                        fout.print("UID=");
                        fout.print(mPowerSaveWhitelistExceptIdleAppIds.keyAt(i));
                        fout.print(": ");
                        fout.print(mPowerSaveWhitelistExceptIdleAppIds.valueAt(i));
                        fout.println();
                    }
                    fout.decreaseIndent();
                }

                size = mPowerSaveWhitelistAppIds.size();
                if (size > 0) {
                    fout.println("Power save whitelist app ids:");
                    fout.increaseIndent();
                    for (int i = 0; i < size; i++) {
                        fout.print("UID=");
                        fout.print(mPowerSaveWhitelistAppIds.keyAt(i));
                        fout.print(": ");
                        fout.print(mPowerSaveWhitelistAppIds.valueAt(i));
                        fout.println();
                    }
                    fout.decreaseIndent();
                }

                size = mDefaultRestrictBackgroundWhitelistUids.size();
                if (size > 0) {
                    fout.println("Default restrict background whitelist uids:");
                    fout.increaseIndent();
                    for (int i = 0; i < size; i++) {
                        fout.print("UID=");
                        fout.print(mDefaultRestrictBackgroundWhitelistUids.keyAt(i));
                        fout.println();
                    }
                    fout.decreaseIndent();
                }

                size = mRestrictBackgroundWhitelistRevokedUids.size();
                if (size > 0) {
                    fout.println("Default restrict background whitelist uids revoked by users:");
                    fout.increaseIndent();
                    for (int i = 0; i < size; i++) {
                        fout.print("UID=");
                        fout.print(mRestrictBackgroundWhitelistRevokedUids.keyAt(i));
                        fout.println();
                    }
                    fout.decreaseIndent();
                }

                final SparseBooleanArray knownUids = new SparseBooleanArray();
                collectKeys(mUidState, knownUids);
                collectKeys(mUidRules, knownUids);

                fout.println("Status for all known UIDs:");
                fout.increaseIndent();
                size = knownUids.size();
                for (int i = 0; i < size; i++) {
                    final int uid = knownUids.keyAt(i);
                    fout.print("UID=");
                    fout.print(uid);

                    final int state = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
                    fout.print(" state=");
                    fout.print(state);
                    if (state <= ActivityManager.PROCESS_STATE_TOP) {
                        fout.print(" (fg)");
                    } else {
                        fout.print(state <= ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
                                ? " (fg svc)" : " (bg)");
                    }

                    final int uidRules = mUidRules.get(uid, RULE_NONE);
                    fout.print(" rules=");
                    fout.print(uidRulesToString(uidRules));
                    fout.println();
                }
                fout.decreaseIndent();

                fout.println("Status for just UIDs with rules:");
                fout.increaseIndent();
                size = mUidRules.size();
                for (int i = 0; i < size; i++) {
                    final int uid = mUidRules.keyAt(i);
                    fout.print("UID=");
                    fout.print(uid);
                    final int uidRules = mUidRules.get(uid, RULE_NONE);
                    fout.print(" rules=");
                    fout.print(uidRulesToString(uidRules));
                    fout.println();
                }
                fout.decreaseIndent();

                fout.println("Observed uid state changes:");
                fout.increaseIndent();
                mObservedHistory.dumpUL(fout);
                fout.decreaseIndent();
            }
        }
    }

    @Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        (new NetworkPolicyManagerShellCommand(mContext, this)).exec(
                this, in, out, err, args, callback, resultReceiver);
    }

    @Override
    public boolean isUidForeground(int uid) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);

        synchronized (mUidRulesFirstLock) {
            return isUidForegroundUL(uid);
        }
    }

    private boolean isUidForegroundUL(int uid) {
        return isUidStateForegroundUL(
                mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY));
    }

    private boolean isUidForegroundOnRestrictBackgroundUL(int uid) {
        final int procState = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
        return isProcStateAllowedWhileOnRestrictBackground(procState);
    }

    private boolean isUidForegroundOnRestrictPowerUL(int uid) {
        final int procState = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
        return isProcStateAllowedWhileIdleOrPowerSaveMode(procState);
    }

    private boolean isUidStateForegroundUL(int state) {
        // only really in foreground when screen is also on
        return state <= ActivityManager.PROCESS_STATE_TOP;
    }

    /**
     * Process state of UID changed; if needed, will trigger
     * {@link #updateRulesForDataUsageRestrictionsUL(int)} and
     * {@link #updateRulesForPowerRestrictionsUL(int)}
     */
    private void updateUidStateUL(int uid, int uidState) {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateUidStateUL");
        try {
            final int oldUidState = mUidState.get(uid, ActivityManager.PROCESS_STATE_CACHED_EMPTY);
            if (oldUidState != uidState) {
                // state changed, push updated rules
                mUidState.put(uid, uidState);
                updateRestrictBackgroundRulesOnUidStatusChangedUL(uid, oldUidState, uidState);
                if (isProcStateAllowedWhileIdleOrPowerSaveMode(oldUidState)
                        != isProcStateAllowedWhileIdleOrPowerSaveMode(uidState) ) {
                    updateRuleForAppIdleUL(uid);
                    if (mDeviceIdleMode) {
                        updateRuleForDeviceIdleUL(uid);
                    }
                    if (mRestrictPower) {
                        updateRuleForRestrictPowerUL(uid);
                    }
                    updateRulesForPowerRestrictionsUL(uid);
                }
                updateNetworkStats(uid, isUidStateForegroundUL(uidState));
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void removeUidStateUL(int uid) {
        final int index = mUidState.indexOfKey(uid);
        if (index >= 0) {
            final int oldUidState = mUidState.valueAt(index);
            mUidState.removeAt(index);
            if (oldUidState != ActivityManager.PROCESS_STATE_CACHED_EMPTY) {
                updateRestrictBackgroundRulesOnUidStatusChangedUL(uid, oldUidState,
                        ActivityManager.PROCESS_STATE_CACHED_EMPTY);
                if (mDeviceIdleMode) {
                    updateRuleForDeviceIdleUL(uid);
                }
                if (mRestrictPower) {
                    updateRuleForRestrictPowerUL(uid);
                }
                updateRulesForPowerRestrictionsUL(uid);
                updateNetworkStats(uid, false);
            }
        }
    }

    // adjust stats accounting based on foreground status
    private void updateNetworkStats(int uid, boolean uidForeground) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK,
                    "updateNetworkStats: " + uid + "/" + (uidForeground ? "F" : "B"));
        }
        try {
            mNetworkStats.setUidForeground(uid, uidForeground);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void updateRestrictBackgroundRulesOnUidStatusChangedUL(int uid, int oldUidState,
            int newUidState) {
        final boolean oldForeground =
                isProcStateAllowedWhileOnRestrictBackground(oldUidState);
        final boolean newForeground =
                isProcStateAllowedWhileOnRestrictBackground(newUidState);
        if (oldForeground != newForeground) {
            updateRulesForDataUsageRestrictionsUL(uid);
        }
    }

    void updateRulesForPowerSaveUL() {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRulesForPowerSaveUL");
        try {
            updateRulesForWhitelistedPowerSaveUL(mRestrictPower, FIREWALL_CHAIN_POWERSAVE,
                    mUidFirewallPowerSaveRules);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    void updateRuleForRestrictPowerUL(int uid) {
        updateRulesForWhitelistedPowerSaveUL(uid, mRestrictPower, FIREWALL_CHAIN_POWERSAVE);
    }

    void updateRulesForDeviceIdleUL() {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRulesForDeviceIdleUL");
        try {
            updateRulesForWhitelistedPowerSaveUL(mDeviceIdleMode, FIREWALL_CHAIN_DOZABLE,
                    mUidFirewallDozableRules);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    void updateRuleForDeviceIdleUL(int uid) {
        updateRulesForWhitelistedPowerSaveUL(uid, mDeviceIdleMode, FIREWALL_CHAIN_DOZABLE);
    }

    // NOTE: since both fw_dozable and fw_powersave uses the same map
    // (mPowerSaveTempWhitelistAppIds) for whitelisting, we can reuse their logic in this method.
    private void updateRulesForWhitelistedPowerSaveUL(boolean enabled, int chain,
            SparseIntArray rules) {
        if (enabled) {
            // Sync the whitelists before enabling the chain.  We don't care about the rules if
            // we are disabling the chain.
            final SparseIntArray uidRules = rules;
            uidRules.clear();
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                updateRulesForWhitelistedAppIds(uidRules, mPowerSaveTempWhitelistAppIds, user.id);
                updateRulesForWhitelistedAppIds(uidRules, mPowerSaveWhitelistAppIds, user.id);
                if (chain == FIREWALL_CHAIN_POWERSAVE) {
                    updateRulesForWhitelistedAppIds(uidRules,
                            mPowerSaveWhitelistExceptIdleAppIds, user.id);
                }
            }
            for (int i = mUidState.size() - 1; i >= 0; i--) {
                if (isProcStateAllowedWhileIdleOrPowerSaveMode(mUidState.valueAt(i))) {
                    uidRules.put(mUidState.keyAt(i), FIREWALL_RULE_ALLOW);
                }
            }
            setUidFirewallRulesUL(chain, uidRules, CHAIN_TOGGLE_ENABLE);
        } else {
            setUidFirewallRulesUL(chain, null, CHAIN_TOGGLE_DISABLE);
        }
    }

    private void updateRulesForWhitelistedAppIds(final SparseIntArray uidRules,
            final SparseBooleanArray whitelistedAppIds, int userId) {
        for (int i = whitelistedAppIds.size() - 1; i >= 0; --i) {
            if (whitelistedAppIds.valueAt(i)) {
                final int appId = whitelistedAppIds.keyAt(i);
                final int uid = UserHandle.getUid(userId, appId);
                uidRules.put(uid, FIREWALL_RULE_ALLOW);
            }
        }
    }

    /**
     * @param deviceIdleMode if true then we don't consider
     *        {@link #mPowerSaveWhitelistExceptIdleAppIds} for checking if the {@param uid} is
     *        whitelisted.
     */
    private boolean isWhitelistedBatterySaverUL(int uid, boolean deviceIdleMode) {
        final int appId = UserHandle.getAppId(uid);
        boolean isWhitelisted = mPowerSaveTempWhitelistAppIds.get(appId)
                || mPowerSaveWhitelistAppIds.get(appId);
        if (!deviceIdleMode) {
            isWhitelisted = isWhitelisted || mPowerSaveWhitelistExceptIdleAppIds.get(appId);
        }
        return isWhitelisted;
    }

    // NOTE: since both fw_dozable and fw_powersave uses the same map
    // (mPowerSaveTempWhitelistAppIds) for whitelisting, we can reuse their logic in this method.
    private void updateRulesForWhitelistedPowerSaveUL(int uid, boolean enabled, int chain) {
        if (enabled) {
            final boolean isWhitelisted = isWhitelistedBatterySaverUL(uid,
                    chain == FIREWALL_CHAIN_DOZABLE);
            if (isWhitelisted || isUidForegroundOnRestrictPowerUL(uid)) {
                setUidFirewallRule(chain, uid, FIREWALL_RULE_ALLOW);
            } else {
                setUidFirewallRule(chain, uid, FIREWALL_RULE_DEFAULT);
            }
        }
    }

    void updateRulesForAppIdleUL() {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRulesForAppIdleUL");
        try {
            final SparseIntArray uidRules = mUidFirewallStandbyRules;
            uidRules.clear();

            // Fully update the app idle firewall chain.
            final List<UserInfo> users = mUserManager.getUsers();
            for (int ui = users.size() - 1; ui >= 0; ui--) {
                UserInfo user = users.get(ui);
                int[] idleUids = mUsageStats.getIdleUidsForUser(user.id);
                for (int uid : idleUids) {
                    if (!mPowerSaveTempWhitelistAppIds.get(UserHandle.getAppId(uid), false)) {
                        // quick check: if this uid doesn't have INTERNET permission, it
                        // doesn't have network access anyway, so it is a waste to mess
                        // with it here.
                        if (hasInternetPermissions(uid)) {
                            uidRules.put(uid, FIREWALL_RULE_DENY);
                        }
                    }
                }
            }

            setUidFirewallRulesUL(FIREWALL_CHAIN_STANDBY, uidRules, CHAIN_TOGGLE_NONE);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    void updateRuleForAppIdleUL(int uid) {
        if (!isUidValidForBlacklistRules(uid)) return;

        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRuleForAppIdleUL: " + uid );
        }
        try {
            int appId = UserHandle.getAppId(uid);
            if (!mPowerSaveTempWhitelistAppIds.get(appId) && isUidIdle(uid)
                    && !isUidForegroundOnRestrictPowerUL(uid)) {
                setUidFirewallRule(FIREWALL_CHAIN_STANDBY, uid, FIREWALL_RULE_DENY);
            } else {
                setUidFirewallRule(FIREWALL_CHAIN_STANDBY, uid, FIREWALL_RULE_DEFAULT);
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    /**
     * Toggle the firewall standby chain and inform listeners if the uid rules have effectively
     * changed.
     */
    void updateRulesForAppIdleParoleUL() {
        boolean paroled = mUsageStats.isAppIdleParoleOn();
        boolean enableChain = !paroled;
        enableFirewallChainUL(FIREWALL_CHAIN_STANDBY, enableChain);

        int ruleCount = mUidFirewallStandbyRules.size();
        for (int i = 0; i < ruleCount; i++) {
            int uid = mUidFirewallStandbyRules.keyAt(i);
            int oldRules = mUidRules.get(uid);
            if (enableChain) {
                // Chain wasn't enabled before and the other power-related
                // chains are whitelists, so we can clear the
                // MASK_ALL_NETWORKS part of the rules and re-inform listeners if
                // the effective rules result in blocking network access.
                oldRules &= MASK_METERED_NETWORKS;
            } else {
                // Skip if it had no restrictions to begin with
                if ((oldRules & MASK_ALL_NETWORKS) == 0) continue;
            }
            final int newUidRules = updateRulesForPowerRestrictionsUL(uid, oldRules, paroled);
            if (newUidRules == RULE_NONE) {
                mUidRules.delete(uid);
            } else {
                mUidRules.put(uid, newUidRules);
            }
        }
    }

    /**
     * Update rules that might be changed by {@link #mRestrictBackground},
     * {@link #mRestrictPower}, or {@link #mDeviceIdleMode} value.
     */
    private void updateRulesForGlobalChangeAL(boolean restrictedNetworksChanged) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK,
                    "updateRulesForGlobalChangeAL: " + (restrictedNetworksChanged ? "R" : "-"));
        }
        try {
            updateRulesForAppIdleUL();
            updateRulesForRestrictPowerUL();
            updateRulesForRestrictBackgroundUL();

            // If the set of restricted networks may have changed, re-evaluate those.
            if (restrictedNetworksChanged) {
                normalizePoliciesNL();
                updateNetworkRulesNL();
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    // TODO: rename / document to make it clear these are global (not app-specific) rules
    private void updateRulesForRestrictPowerUL() {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRulesForRestrictPowerUL");
        try {
            updateRulesForDeviceIdleUL();
            updateRulesForPowerSaveUL();
            updateRulesForAllAppsUL(TYPE_RESTRICT_POWER);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void updateRulesForRestrictBackgroundUL() {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRulesForRestrictBackgroundUL");
        try {
            updateRulesForAllAppsUL(TYPE_RESTRICT_BACKGROUND);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private static final int TYPE_RESTRICT_BACKGROUND = 1;
    private static final int TYPE_RESTRICT_POWER = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = false, value = {
            TYPE_RESTRICT_BACKGROUND,
            TYPE_RESTRICT_POWER,
    })
    public @interface RestrictType {
    }

    // TODO: refactor / consolidate all those updateXyz methods, there are way too many of them...
    private void updateRulesForAllAppsUL(@RestrictType int type) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "updateRulesForRestrictPowerUL-" + type);
        }
        try {
            // update rules for all installed applications

            final PackageManager pm = mContext.getPackageManager();
            final List<UserInfo> users;
            final List<ApplicationInfo> apps;

            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "list-users");
            try {
                users = mUserManager.getUsers();
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
            }
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "list-uids");
            try {
                apps = pm.getInstalledApplications(
                        PackageManager.MATCH_ANY_USER | PackageManager.MATCH_DISABLED_COMPONENTS
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
            }

            final int usersSize = users.size();
            final int appsSize = apps.size();
            for (int i = 0; i < usersSize; i++) {
                final UserInfo user = users.get(i);
                for (int j = 0; j < appsSize; j++) {
                    final ApplicationInfo app = apps.get(j);
                    final int uid = UserHandle.getUid(user.id, app.uid);
                    switch (type) {
                        case TYPE_RESTRICT_BACKGROUND:
                            updateRulesForDataUsageRestrictionsUL(uid);
                            break;
                        case TYPE_RESTRICT_POWER:
                            updateRulesForPowerRestrictionsUL(uid);
                            break;
                        default:
                            Slog.w(TAG, "Invalid type for updateRulesForAllApps: " + type);
                    }
                }
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void updateRulesForTempWhitelistChangeUL() {
        final List<UserInfo> users = mUserManager.getUsers();
        for (int i = 0; i < users.size(); i++) {
            final UserInfo user = users.get(i);
            for (int j = mPowerSaveTempWhitelistAppIds.size() - 1; j >= 0; j--) {
                int appId = mPowerSaveTempWhitelistAppIds.keyAt(j);
                int uid = UserHandle.getUid(user.id, appId);
                // Update external firewall rules.
                updateRuleForAppIdleUL(uid);
                updateRuleForDeviceIdleUL(uid);
                updateRuleForRestrictPowerUL(uid);
                // Update internal rules.
                updateRulesForPowerRestrictionsUL(uid);
            }
        }
    }

    // TODO: the MEDIA / DRM restriction might not be needed anymore, in which case both
    // methods below could be merged into a isUidValidForRules() method.
    private boolean isUidValidForBlacklistRules(int uid) {
        // allow rules on specific system services, and any apps
        if (uid == android.os.Process.MEDIA_UID || uid == android.os.Process.DRM_UID
            || (UserHandle.isApp(uid) && hasInternetPermissions(uid))) {
            return true;
        }

        return false;
    }

    private boolean isUidValidForWhitelistRules(int uid) {
        return UserHandle.isApp(uid) && hasInternetPermissions(uid);
    }

    private boolean isUidIdle(int uid) {
        final String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        final int userId = UserHandle.getUserId(uid);

        if (packages != null) {
            for (String packageName : packages) {
                if (!mUsageStats.isAppIdle(packageName, uid, userId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks if an uid has INTERNET permissions.
     * <p>
     * Useful for the cases where the lack of network access can simplify the rules.
     */
    private boolean hasInternetPermissions(int uid) {
        try {
            if (mIPm.checkUidPermission(Manifest.permission.INTERNET, uid)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        } catch (RemoteException e) {
        }
        return true;
    }

    /**
     * Clears all state - internal and external - associated with an UID.
     */
    private void onUidDeletedUL(int uid) {
        // First cleanup in-memory state synchronously...
        mUidRules.delete(uid);
        mUidPolicy.delete(uid);
        mUidFirewallStandbyRules.delete(uid);
        mUidFirewallDozableRules.delete(uid);
        mUidFirewallPowerSaveRules.delete(uid);
        mPowerSaveWhitelistExceptIdleAppIds.delete(uid);
        mPowerSaveWhitelistAppIds.delete(uid);
        mPowerSaveTempWhitelistAppIds.delete(uid);

        // ...then update iptables asynchronously.
        mHandler.obtainMessage(MSG_RESET_FIREWALL_RULES_BY_UID, uid, 0).sendToTarget();
    }

    /**
     * Applies network rules to bandwidth and firewall controllers based on uid policy.
     *
     * <p>There are currently 4 types of restriction rules:
     * <ul>
     * <li>Doze mode
     * <li>App idle mode
     * <li>Battery Saver Mode (also referred as power save).
     * <li>Data Saver Mode (The Feature Formerly Known As 'Restrict Background Data').
     * </ul>
     *
     * <p>This method changes both the external firewall rules and the internal state.
     */
    private void updateRestrictionRulesForUidUL(int uid) {
        // Methods below only changes the firewall rules for the power-related modes.
        updateRuleForDeviceIdleUL(uid);
        updateRuleForAppIdleUL(uid);
        updateRuleForRestrictPowerUL(uid);

        // Update internal state for power-related modes.
        updateRulesForPowerRestrictionsUL(uid);

        // Update firewall and internal rules for Data Saver Mode.
        updateRulesForDataUsageRestrictionsUL(uid);
    }

    /**
     * Applies network rules to bandwidth controllers based on process state and user-defined
     * restrictions (blacklist / whitelist).
     *
     * <p>
     * {@code netd} defines 3 firewall chains that govern whether an app has access to metered
     * networks:
     * <ul>
     * <li>@{code bw_penalty_box}: UIDs added to this chain do not have access (blacklist).
     * <li>@{code bw_happy_box}: UIDs added to this chain have access (whitelist), unless they're
     *     also blacklisted.
     * <li>@{code bw_data_saver}: when enabled (through {@link #setRestrictBackground(boolean)}),
     *     no UIDs other those whitelisted will have access.
     * <ul>
     *
     * <p>The @{code bw_penalty_box} and @{code bw_happy_box} are primarily managed through the
     * {@link #setUidPolicy(int, int)} and {@link #addRestrictBackgroundWhitelistedUid(int)} /
     * {@link #removeRestrictBackgroundWhitelistedUid(int)} methods (for blacklist and whitelist
     * respectively): these methods set the proper internal state (blacklist / whitelist), then call
     * this ({@link #updateRulesForDataUsageRestrictionsUL(int)}) to propagate the rules to
     * {@link INetworkManagementService}, but this method should also be called in events (like
     * Data Saver Mode flips or UID state changes) that might affect the foreground app, since the
     * following rules should also be applied:
     *
     * <ul>
     * <li>When Data Saver mode is on, the foreground app should be temporarily added to
     *     {@code bw_happy_box} before the @{code bw_data_saver} chain is enabled.
     * <li>If the foreground app is blacklisted by the user, it should be temporarily removed from
     *     {@code bw_penalty_box}.
     * <li>When the app leaves foreground state, the temporary changes above should be reverted.
     * </ul>
     *
     * <p>For optimization, the rules are only applied on user apps that have internet access
     * permission, since there is no need to change the {@code iptables} rule if the app does not
     * have permission to use the internet.
     *
     * <p>The {@link #mUidRules} map is used to define the transtion of states of an UID.
     *
     */
    private void updateRulesForDataUsageRestrictionsUL(int uid) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK,
                    "updateRulesForDataUsageRestrictionsUL: " + uid);
        }
        try {
            updateRulesForDataUsageRestrictionsULInner(uid);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void updateRulesForDataUsageRestrictionsULInner(int uid) {
        if (!isUidValidForWhitelistRules(uid)) {
            if (LOGD) Slog.d(TAG, "no need to update restrict data rules for uid " + uid);
            return;
        }

        final int uidPolicy = mUidPolicy.get(uid, POLICY_NONE);
        final int oldUidRules = mUidRules.get(uid, RULE_NONE);
        final boolean isForeground = isUidForegroundOnRestrictBackgroundUL(uid);

        final boolean isBlacklisted = (uidPolicy & POLICY_REJECT_METERED_BACKGROUND) != 0;
        final boolean isWhitelisted = (uidPolicy & POLICY_ALLOW_METERED_BACKGROUND) != 0;
        final int oldRule = oldUidRules & MASK_METERED_NETWORKS;
        int newRule = RULE_NONE;

        try {
            mNetworkManager.restrictAppOnData(uid, (uidPolicy & POLICY_REJECT_ON_DATA) != 0);
            mNetworkManager.restrictAppOnWlan(uid, (uidPolicy & POLICY_REJECT_ON_WLAN) != 0);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }

        // First step: define the new rule based on user restrictions and foreground state.
        if (isForeground) {
            if (isBlacklisted || (mRestrictBackground && !isWhitelisted)) {
                newRule = RULE_TEMPORARY_ALLOW_METERED;
            } else if (isWhitelisted) {
                newRule = RULE_ALLOW_METERED;
            }
        } else {
            if (isBlacklisted) {
                newRule = RULE_REJECT_METERED;
            } else if (mRestrictBackground && isWhitelisted) {
                newRule = RULE_ALLOW_METERED;
            }
        }
        final int newUidRules = newRule | (oldUidRules & MASK_ALL_NETWORKS);

        if (LOGV) {
            Log.v(TAG, "updateRuleForRestrictBackgroundUL(" + uid + ")"
                    + ": isForeground=" +isForeground
                    + ", isBlacklisted=" + isBlacklisted
                    + ", isWhitelisted=" + isWhitelisted
                    + ", oldRule=" + uidRulesToString(oldRule)
                    + ", newRule=" + uidRulesToString(newRule)
                    + ", newUidRules=" + uidRulesToString(newUidRules)
                    + ", oldUidRules=" + uidRulesToString(oldUidRules));
        }

        if (newUidRules == RULE_NONE) {
            mUidRules.delete(uid);
        } else {
            mUidRules.put(uid, newUidRules);
        }

        // Second step: apply bw changes based on change of state.
        if (newRule != oldRule) {
            if (hasRule(newRule, RULE_TEMPORARY_ALLOW_METERED)) {
                // Temporarily whitelist foreground app, removing from blacklist if necessary
                // (since bw_penalty_box prevails over bw_happy_box).

                setMeteredNetworkWhitelist(uid, true);
                // TODO: if statement below is used to avoid an unnecessary call to netd / iptables,
                // but ideally it should be just:
                //    setMeteredNetworkBlacklist(uid, isBlacklisted);
                if (isBlacklisted) {
                    setMeteredNetworkBlacklist(uid, false);
                }
            } else if (hasRule(oldRule, RULE_TEMPORARY_ALLOW_METERED)) {
                // Remove temporary whitelist from app that is not on foreground anymore.

                // TODO: if statements below are used to avoid unnecessary calls to netd / iptables,
                // but ideally they should be just:
                //    setMeteredNetworkWhitelist(uid, isWhitelisted);
                //    setMeteredNetworkBlacklist(uid, isBlacklisted);
                if (!isWhitelisted) {
                    setMeteredNetworkWhitelist(uid, false);
                }
                if (isBlacklisted) {
                    setMeteredNetworkBlacklist(uid, true);
                }
            } else if (hasRule(newRule, RULE_REJECT_METERED)
                    || hasRule(oldRule, RULE_REJECT_METERED)) {
                // Flip state because app was explicitly added or removed to blacklist.
                setMeteredNetworkBlacklist(uid, isBlacklisted);
                if (hasRule(oldRule, RULE_REJECT_METERED) && isWhitelisted) {
                    // Since blacklist prevails over whitelist, we need to handle the special case
                    // where app is whitelisted and blacklisted at the same time (although such
                    // scenario should be blocked by the UI), then blacklist is removed.
                    setMeteredNetworkWhitelist(uid, isWhitelisted);
                }
            } else if (hasRule(newRule, RULE_ALLOW_METERED)
                    || hasRule(oldRule, RULE_ALLOW_METERED)) {
                // Flip state because app was explicitly added or removed to whitelist.
                setMeteredNetworkWhitelist(uid, isWhitelisted);
            } else {
                // All scenarios should have been covered above.
                Log.wtf(TAG, "Unexpected change of metered UID state for " + uid
                        + ": foreground=" + isForeground
                        + ", whitelisted=" + isWhitelisted
                        + ", blacklisted=" + isBlacklisted
                        + ", newRule=" + uidRulesToString(newUidRules)
                        + ", oldRule=" + uidRulesToString(oldUidRules));
            }

            // Dispatch changed rule to existing listeners.
            mHandler.obtainMessage(MSG_RULES_CHANGED, uid, newUidRules).sendToTarget();
        }
    }

    /**
     * Updates the power-related part of the {@link #mUidRules} for a given map, and notify external
     * listeners in case of change.
     * <p>
     * There are 3 power-related rules that affects whether an app has background access on
     * non-metered networks, and when the condition applies and the UID is not whitelisted for power
     * restriction, it's added to the equivalent firewall chain:
     * <ul>
     * <li>App is idle: {@code fw_standby} firewall chain.
     * <li>Device is idle: {@code fw_dozable} firewall chain.
     * <li>Battery Saver Mode is on: {@code fw_powersave} firewall chain.
     * </ul>
     * <p>
     * This method updates the power-related part of the {@link #mUidRules} for a given uid based on
     * these modes, the UID process state (foreground or not), and the UIDwhitelist state.
     * <p>
     * <strong>NOTE: </strong>This method does not update the firewall rules on {@code netd}.
     */
    private void updateRulesForPowerRestrictionsUL(int uid) {
        final int oldUidRules = mUidRules.get(uid, RULE_NONE);

        final int newUidRules = updateRulesForPowerRestrictionsUL(uid, oldUidRules, false);

        if (newUidRules == RULE_NONE) {
            mUidRules.delete(uid);
        } else {
            mUidRules.put(uid, newUidRules);
        }
    }

    /**
     * Similar to above but ignores idle state if app standby is currently disabled by parole.
     *
     * @param uid the uid of the app to update rules for
     * @param oldUidRules the current rules for the uid, in order to determine if there's a change
     * @param paroled whether to ignore idle state of apps and only look at other restrictions.
     *
     * @return the new computed rules for the uid
     */
    private int updateRulesForPowerRestrictionsUL(int uid, int oldUidRules, boolean paroled) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK,
                    "updateRulesForPowerRestrictionsUL: " + uid + "/" + oldUidRules + "/"
                    + (paroled ? "P" : "-"));
        }
        try {
            return updateRulesForPowerRestrictionsULInner(uid, oldUidRules, paroled);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private int updateRulesForPowerRestrictionsULInner(int uid, int oldUidRules, boolean paroled) {
        if (!isUidValidForBlacklistRules(uid)) {
            if (LOGD) Slog.d(TAG, "no need to update restrict power rules for uid " + uid);
            return RULE_NONE;
        }

        final boolean isIdle = !paroled && isUidIdle(uid);
        final boolean restrictMode = isIdle || mRestrictPower || mDeviceIdleMode;
        final boolean isForeground = isUidForegroundOnRestrictPowerUL(uid);

        final boolean isWhitelisted = isWhitelistedBatterySaverUL(uid, mDeviceIdleMode);
        final int oldRule = oldUidRules & MASK_ALL_NETWORKS;
        int newRule = RULE_NONE;

        // First step: define the new rule based on user restrictions and foreground state.

        // NOTE: if statements below could be inlined, but it's easier to understand the logic
        // by considering the foreground and non-foreground states.
        if (isForeground) {
            if (restrictMode) {
                newRule = RULE_ALLOW_ALL;
            }
        } else if (restrictMode) {
            newRule = isWhitelisted ? RULE_ALLOW_ALL : RULE_REJECT_ALL;
        }

        final int newUidRules = (oldUidRules & MASK_METERED_NETWORKS) | newRule;

        if (LOGV) {
            Log.v(TAG, "updateRulesForPowerRestrictionsUL(" + uid + ")"
                    + ", isIdle: " + isIdle
                    + ", mRestrictPower: " + mRestrictPower
                    + ", mDeviceIdleMode: " + mDeviceIdleMode
                    + ", isForeground=" + isForeground
                    + ", isWhitelisted=" + isWhitelisted
                    + ", oldRule=" + uidRulesToString(oldRule)
                    + ", newRule=" + uidRulesToString(newRule)
                    + ", newUidRules=" + uidRulesToString(newUidRules)
                    + ", oldUidRules=" + uidRulesToString(oldUidRules));
        }

        // Second step: notify listeners if state changed.
        if (newRule != oldRule) {
            if (newRule == RULE_NONE || hasRule(newRule, RULE_ALLOW_ALL)) {
                if (LOGV) Log.v(TAG, "Allowing non-metered access for UID " + uid);
            } else if (hasRule(newRule, RULE_REJECT_ALL)) {
                if (LOGV) Log.v(TAG, "Rejecting non-metered access for UID " + uid);
            } else {
                // All scenarios should have been covered above
                Log.wtf(TAG, "Unexpected change of non-metered UID state for " + uid
                        + ": foreground=" + isForeground
                        + ", whitelisted=" + isWhitelisted
                        + ", newRule=" + uidRulesToString(newUidRules)
                        + ", oldRule=" + uidRulesToString(oldUidRules));
            }
            mHandler.obtainMessage(MSG_RULES_CHANGED, uid, newUidRules).sendToTarget();
        }

        return newUidRules;
    }

    private class AppIdleStateChangeListener
            extends UsageStatsManagerInternal.AppIdleStateChangeListener {

        @Override
        public void onAppIdleStateChanged(String packageName, int userId, boolean idle) {
            try {
                final int uid = mContext.getPackageManager().getPackageUidAsUser(packageName,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES, userId);
                if (LOGV) Log.v(TAG, "onAppIdleStateChanged(): uid=" + uid + ", idle=" + idle);
                synchronized (mUidRulesFirstLock) {
                    updateRuleForAppIdleUL(uid);
                    updateRulesForPowerRestrictionsUL(uid);
                }
            } catch (NameNotFoundException nnfe) {
            }
        }

        @Override
        public void onParoleStateChanged(boolean isParoleOn) {
            synchronized (mUidRulesFirstLock) {
                updateRulesForAppIdleParoleUL();
            }
        }
    }

    private void dispatchUidRulesChanged(INetworkPolicyListener listener, int uid, int uidRules) {
        if (listener != null) {
            try {
                listener.onUidRulesChanged(uid, uidRules);
            } catch (RemoteException ignored) {
            }
        }
    }

    private void dispatchMeteredIfacesChanged(INetworkPolicyListener listener,
            String[] meteredIfaces) {
        if (listener != null) {
            try {
                listener.onMeteredIfacesChanged(meteredIfaces);
            } catch (RemoteException ignored) {
            }
        }
    }

    private void dispatchRestrictBackgroundChanged(INetworkPolicyListener listener,
            boolean restrictBackground) {
        if (listener != null) {
            try {
                listener.onRestrictBackgroundChanged(restrictBackground);
            } catch (RemoteException ignored) {
            }
        }
    }

    private void dispatchUidPoliciesChanged(INetworkPolicyListener listener, int uid,
            int uidPolicies) {
        if (listener != null) {
            try {
                listener.onUidPoliciesChanged(uid, uidPolicies);
            } catch (RemoteException ignored) {
            }
        }
    }

    private final Handler.Callback mHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RULES_CHANGED: {
                    final int uid = msg.arg1;
                    final int uidRules = msg.arg2;
                    final int length = mListeners.beginBroadcast();
                    for (int i = 0; i < length; i++) {
                        final INetworkPolicyListener listener = mListeners.getBroadcastItem(i);
                        dispatchUidRulesChanged(listener, uid, uidRules);
                    }
                    mListeners.finishBroadcast();
                    return true;
                }
                case MSG_METERED_IFACES_CHANGED: {
                    final String[] meteredIfaces = (String[]) msg.obj;
                    final int length = mListeners.beginBroadcast();
                    for (int i = 0; i < length; i++) {
                        final INetworkPolicyListener listener = mListeners.getBroadcastItem(i);
                        dispatchMeteredIfacesChanged(listener, meteredIfaces);
                    }
                    mListeners.finishBroadcast();
                    return true;
                }
                case MSG_LIMIT_REACHED: {
                    final String iface = (String) msg.obj;

                    maybeRefreshTrustedTime();
                    synchronized (mNetworkPoliciesSecondLock) {
                        if (mMeteredIfaces.contains(iface)) {
                            try {
                                // force stats update to make sure we have
                                // numbers that caused alert to trigger.
                                mNetworkStats.forceUpdate();
                            } catch (RemoteException e) {
                                // ignored; service lives in system_server
                            }

                            updateNetworkEnabledNL();
                            updateNotificationsNL();
                        }
                    }
                    return true;
                }
                case MSG_RESTRICT_BACKGROUND_CHANGED: {
                    final boolean restrictBackground = msg.arg1 != 0;
                    final int length = mListeners.beginBroadcast();
                    for (int i = 0; i < length; i++) {
                        final INetworkPolicyListener listener = mListeners.getBroadcastItem(i);
                        dispatchRestrictBackgroundChanged(listener, restrictBackground);
                    }
                    mListeners.finishBroadcast();
                    final Intent intent =
                            new Intent(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
                    intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                    return true;
                }
                case MSG_POLICIES_CHANGED: {
                    final int uid = msg.arg1;
                    final int policy = msg.arg2;
                    final Boolean notifyApp = (Boolean) msg.obj;
                    // First notify internal listeners...
                    final int length = mListeners.beginBroadcast();
                    for (int i = 0; i < length; i++) {
                        final INetworkPolicyListener listener = mListeners.getBroadcastItem(i);
                        dispatchUidPoliciesChanged(listener, uid, policy);
                    }
                    mListeners.finishBroadcast();
                    // ...then apps listening to ACTION_RESTRICT_BACKGROUND_CHANGED
                    if (notifyApp.booleanValue()) {
                        broadcastRestrictBackgroundChanged(uid, notifyApp);
                    }
                    return true;
                }
                case MSG_ADVISE_PERSIST_THRESHOLD: {
                    final long lowestRule = (Long) msg.obj;
                    try {
                        // make sure stats are recorded frequently enough; we aim
                        // for 2MB threshold for 2GB/month rules.
                        final long persistThreshold = lowestRule / 1000;
                        mNetworkStats.advisePersistThreshold(persistThreshold);
                    } catch (RemoteException e) {
                        // ignored; service lives in system_server
                    }
                    return true;
                }
                case MSG_UPDATE_INTERFACE_QUOTA: {
                    removeInterfaceQuota((String) msg.obj);
                    // int params need to be stitched back into a long
                    setInterfaceQuota((String) msg.obj,
                            ((long) msg.arg1 << 32) | (msg.arg2 & 0xFFFFFFFFL));
                    return true;
                }
                case MSG_REMOVE_INTERFACE_QUOTA: {
                    removeInterfaceQuota((String) msg.obj);
                    return true;
                }
                case MSG_RESET_FIREWALL_RULES_BY_UID: {
                    resetUidFirewallRules(msg.arg1);
                    return true;
                }
                default: {
                    return false;
                }
            }
        }
    };

    private final Handler.Callback mUidEventHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case UID_MSG_STATE_CHANGED: {
                    final int uid = msg.arg1;
                    final int procState = msg.arg2;
                    final long procStateSeq = (Long) msg.obj;

                    handleUidChanged(uid, procState, procStateSeq);
                    return true;
                }
                case UID_MSG_GONE: {
                    final int uid = msg.arg1;
                    handleUidGone(uid);
                    return true;
                }
                default: {
                    return false;
                }
            }
        }

    };

    void handleUidChanged(int uid, int procState, long procStateSeq) {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "onUidStateChanged");
        try {
            synchronized (mUidRulesFirstLock) {
                // We received a uid state change callback, add it to the history so that it
                // will be useful for debugging.
                mObservedHistory.addProcStateSeqUL(uid, procStateSeq);
                // Now update the network policy rules as per the updated uid state.
                updateUidStateUL(uid, procState);
                // Updating the network rules is done, so notify AMS about this.
                mActivityManagerInternal.notifyNetworkPolicyRulesUpdated(uid, procStateSeq);
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    void handleUidGone(int uid) {
        Trace.traceBegin(Trace.TRACE_TAG_NETWORK, "onUidGone");
        try {
            synchronized (mUidRulesFirstLock) {
                removeUidStateUL(uid);
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    private void broadcastRestrictBackgroundChanged(int uid, Boolean changed) {
        final PackageManager pm = mContext.getPackageManager();
        final String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) {
            final int userId = UserHandle.getUserId(uid);
            for (String packageName : packages) {
                final Intent intent =
                        new Intent(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
                intent.setPackage(packageName);
                intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
            }
        }
    }

    private void setInterfaceQuotaAsync(String iface, long quotaBytes) {
        // long quotaBytes split up into two ints to fit in message
        mHandler.obtainMessage(MSG_UPDATE_INTERFACE_QUOTA, (int) (quotaBytes >> 32),
                (int) (quotaBytes & 0xFFFFFFFF), iface).sendToTarget();
    }

    private void setInterfaceQuota(String iface, long quotaBytes) {
        try {
            mNetworkManager.setInterfaceQuota(iface, quotaBytes);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem setting interface quota", e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private void removeInterfaceQuotaAsync(String iface) {
        mHandler.obtainMessage(MSG_REMOVE_INTERFACE_QUOTA, iface).sendToTarget();
    }

    private void removeInterfaceQuota(String iface) {
        try {
            mNetworkManager.removeInterfaceQuota(iface);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem removing interface quota", e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private void setMeteredNetworkBlacklist(int uid, boolean enable) {
        if (LOGV) Slog.v(TAG, "setMeteredNetworkBlacklist " + uid + ": " + enable);
        try {
            mNetworkManager.setUidMeteredNetworkBlacklist(uid, enable);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem setting blacklist (" + enable + ") rules for " + uid, e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private void setMeteredNetworkWhitelist(int uid, boolean enable) {
        if (LOGV) Slog.v(TAG, "setMeteredNetworkWhitelist " + uid + ": " + enable);
        try {
            mNetworkManager.setUidMeteredNetworkWhitelist(uid, enable);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem setting whitelist (" + enable + ") rules for " + uid, e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private static final int CHAIN_TOGGLE_NONE = 0;
    private static final int CHAIN_TOGGLE_ENABLE = 1;
    private static final int CHAIN_TOGGLE_DISABLE = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = false, value = {
            CHAIN_TOGGLE_NONE,
            CHAIN_TOGGLE_ENABLE,
            CHAIN_TOGGLE_DISABLE
    })
    public @interface ChainToggleType {
    }

    /**
     * Calls {@link #setUidFirewallRules(int, SparseIntArray)} and
     * {@link #enableFirewallChainUL(int, boolean)} synchronously.
     *
     * @param chain firewall chain.
     * @param uidRules new UID rules; if {@code null}, only toggles chain state.
     * @param toggle whether the chain should be enabled, disabled, or not changed.
     */
    private void setUidFirewallRulesUL(int chain, @Nullable SparseIntArray uidRules,
            @ChainToggleType int toggle) {
        if (uidRules != null) {
            setUidFirewallRulesUL(chain, uidRules);
        }
        if (toggle != CHAIN_TOGGLE_NONE) {
            enableFirewallChainUL(chain, toggle == CHAIN_TOGGLE_ENABLE);
        }
    }

    /**
     * Set uid rules on a particular firewall chain. This is going to synchronize the rules given
     * here to netd.  It will clean up dead rules and make sure the target chain only contains rules
     * specified here.
     */
    private void setUidFirewallRulesUL(int chain, SparseIntArray uidRules) {
        try {
            int size = uidRules.size();
            int[] uids = new int[size];
            int[] rules = new int[size];
            for(int index = size - 1; index >= 0; --index) {
                uids[index] = uidRules.keyAt(index);
                rules[index] = uidRules.valueAt(index);
            }
            mNetworkManager.setFirewallUidRules(chain, uids, rules);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem setting firewall uid rules", e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    /**
     * Add or remove a uid to the firewall blacklist for all network ifaces.
     */
    private void setUidFirewallRule(int chain, int uid, int rule) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_NETWORK)) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK,
                    "setUidFirewallRule: " + chain + "/" + uid + "/" + rule);
        }
        try {
            if (chain == FIREWALL_CHAIN_DOZABLE) {
                mUidFirewallDozableRules.put(uid, rule);
            } else if (chain == FIREWALL_CHAIN_STANDBY) {
                mUidFirewallStandbyRules.put(uid, rule);
            } else if (chain == FIREWALL_CHAIN_POWERSAVE) {
                mUidFirewallPowerSaveRules.put(uid, rule);
            }

            try {
                mNetworkManager.setFirewallUidRule(chain, uid, rule);
            } catch (IllegalStateException e) {
                Log.wtf(TAG, "problem setting firewall uid rules", e);
            } catch (RemoteException e) {
                // ignored; service lives in system_server
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
    }

    /**
     * Add or remove a uid to the firewall blacklist for all network ifaces.
     */
    private void enableFirewallChainUL(int chain, boolean enable) {
        if (mFirewallChainStates.indexOfKey(chain) >= 0 &&
                mFirewallChainStates.get(chain) == enable) {
            // All is the same, nothing to do.
            return;
        }
        mFirewallChainStates.put(chain, enable);
        try {
            mNetworkManager.setFirewallChainEnabled(chain, enable);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem enable firewall chain", e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    /**
     * Resets all firewall rules associated with an UID.
     */
    private void resetUidFirewallRules(int uid) {
        try {
            mNetworkManager.setFirewallUidRule(FIREWALL_CHAIN_DOZABLE, uid, FIREWALL_RULE_DEFAULT);
            mNetworkManager.setFirewallUidRule(FIREWALL_CHAIN_STANDBY, uid, FIREWALL_RULE_DEFAULT);
            mNetworkManager
                    .setFirewallUidRule(FIREWALL_CHAIN_POWERSAVE, uid, FIREWALL_RULE_DEFAULT);
            mNetworkManager.setUidMeteredNetworkWhitelist(uid, false);
            mNetworkManager.setUidMeteredNetworkBlacklist(uid, false);
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "problem resetting firewall uid rules for " + uid, e);
        } catch (RemoteException e) {
            // ignored; service lives in system_server
        }
    }

    private long getTotalBytes(NetworkTemplate template, long start, long end) {
        try {
            return mNetworkStats.getNetworkTotalBytes(template, start, end);
        } catch (RuntimeException e) {
            Slog.w(TAG, "problem reading network stats: " + e);
            return 0;
        } catch (RemoteException e) {
            // ignored; service lives in system_server
            return 0;
        }
    }

    private boolean isBandwidthControlEnabled() {
        final long token = Binder.clearCallingIdentity();
        try {
            return mNetworkManager.isBandwidthControlEnabled();
        } catch (RemoteException e) {
            // ignored; service lives in system_server
            return false;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Try refreshing {@link #mTime} when stale.
     */
    void maybeRefreshTrustedTime() {
        if (mTime.getCacheAge() > TIME_CACHE_MAX_AGE) {
            mTime.forceRefresh();
        }
    }

    private long currentTimeMillis() {
        return mTime.hasCache() ? mTime.currentTimeMillis() : System.currentTimeMillis();
    }

    private static Intent buildAllowBackgroundDataIntent() {
        return new Intent(ACTION_ALLOW_BACKGROUND);
    }

    private static Intent buildSnoozeWarningIntent(NetworkTemplate template) {
        final Intent intent = new Intent(ACTION_SNOOZE_WARNING);
        intent.putExtra(EXTRA_NETWORK_TEMPLATE, template);
        return intent;
    }

    private static Intent buildNetworkOverLimitIntent(Resources res, NetworkTemplate template) {
        final Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(
                res.getString(R.string.config_networkOverLimitComponent)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_NETWORK_TEMPLATE, template);
        return intent;
    }

    private static Intent buildViewDataUsageIntent(Resources res, NetworkTemplate template) {
        final Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(
                res.getString(R.string.config_dataUsageSummaryComponent)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_NETWORK_TEMPLATE, template);
        return intent;
    }

    @VisibleForTesting
    public void addIdleHandler(IdleHandler handler) {
        mHandler.getLooper().getQueue().addIdleHandler(handler);
    }

    @VisibleForTesting
    public void updateRestrictBackgroundByLowPowerModeUL(final PowerSaveState result) {
        mRestrictBackgroundPowerState = result;

        boolean restrictBackground = result.batterySaverEnabled;
        boolean shouldInvokeRestrictBackground;
        // store the temporary mRestrictBackgroundChangedInBsm and update it at last
        boolean localRestrictBgChangedInBsm = mRestrictBackgroundChangedInBsm;

        if (result.globalBatterySaverEnabled) {
            // Try to turn on restrictBackground if (1) it is off and (2) batter saver need to
            // turn it on.
            shouldInvokeRestrictBackground = !mRestrictBackground && result.batterySaverEnabled;
            mRestrictBackgroundBeforeBsm = mRestrictBackground;
            localRestrictBgChangedInBsm = false;
        } else {
            // Try to restore the restrictBackground if it doesn't change in bsm
            shouldInvokeRestrictBackground = !mRestrictBackgroundChangedInBsm;
            restrictBackground = mRestrictBackgroundBeforeBsm;
        }

        if (shouldInvokeRestrictBackground) {
            setRestrictBackground(restrictBackground);
        }

        // Change it at last so setRestrictBackground() won't affect this variable
        mRestrictBackgroundChangedInBsm = localRestrictBgChangedInBsm;
    }

    private static void collectKeys(SparseIntArray source, SparseBooleanArray target) {
        final int size = source.size();
        for (int i = 0; i < size; i++) {
            target.put(source.keyAt(i), true);
        }
    }

    @Override
    public void factoryReset(String subscriber) {
        mContext.enforceCallingOrSelfPermission(CONNECTIVITY_INTERNAL, TAG);

        if (mUserManager.hasUserRestriction(UserManager.DISALLOW_NETWORK_RESET)) {
            return;
        }

        // Turn mobile data limit off
        NetworkPolicy[] policies = getNetworkPolicies(mContext.getOpPackageName());
        NetworkTemplate template = NetworkTemplate.buildTemplateMobileAll(subscriber);
        for (NetworkPolicy policy : policies) {
            if (policy.template.equals(template)) {
                policy.limitBytes = NetworkPolicy.LIMIT_DISABLED;
                policy.inferred = false;
                policy.clearSnooze();
            }
        }
        setNetworkPolicies(policies);

        // Turn restrict background data off
        setRestrictBackground(false);

        if (!mUserManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL)) {
            // Remove app's "restrict background data" flag
            for (int uid : getUidsWithPolicy(POLICY_REJECT_METERED_BACKGROUND)) {
                setUidPolicy(uid, POLICY_NONE);
            }
        }
    }

    @Override
    public boolean isUidNetworkingBlocked(int uid, boolean isNetworkMetered) {
        mContext.enforceCallingOrSelfPermission(MANAGE_NETWORK_POLICY, TAG);
        return isUidNetworkingBlockedInternal(uid, isNetworkMetered);
    }

    private boolean isUidNetworkingBlockedInternal(int uid, boolean isNetworkMetered) {
        final int uidRules;
        final boolean isBackgroundRestricted;
        synchronized (mUidRulesFirstLock) {
            uidRules = mUidRules.get(uid, RULE_NONE);
            isBackgroundRestricted = mRestrictBackground;
        }
        if (hasRule(uidRules, RULE_REJECT_ALL)) {
            if (LOGV) logUidStatus(uid, "blocked by power restrictions");
            return true;
        }
        if (!isNetworkMetered) {
            if (LOGV) logUidStatus(uid, "allowed on unmetered network");
            return false;
        }
        if (hasRule(uidRules, RULE_REJECT_METERED)) {
            if (LOGV) logUidStatus(uid, "blacklisted on metered network");
            return true;
        }
        if (hasRule(uidRules, RULE_ALLOW_METERED)) {
            if (LOGV) logUidStatus(uid, "whitelisted on metered network");
            return false;
        }
        if (hasRule(uidRules, RULE_TEMPORARY_ALLOW_METERED)) {
            if (LOGV) logUidStatus(uid, "temporary whitelisted on metered network");
            return false;
        }
        if (isBackgroundRestricted) {
            if (LOGV) logUidStatus(uid, "blocked when background is restricted");
            return true;
        }
        if (LOGV) logUidStatus(uid, "allowed by default");
        return false;
    }

    private class NetworkPolicyManagerInternalImpl extends NetworkPolicyManagerInternal {

        @Override
        public void resetUserState(int userId) {
            synchronized (mUidRulesFirstLock) {
                boolean changed = removeUserStateUL(userId, false);
                changed = addDefaultRestrictBackgroundWhitelistUidsUL(userId) || changed;
                if (changed) {
                    synchronized (mNetworkPoliciesSecondLock) {
                        writePolicyAL();
                    }
                }
            }
        }

        /**
         * @return true if the given uid is restricted from doing networking on metered networks.
         */
        @Override
        public boolean isUidRestrictedOnMeteredNetworks(int uid) {
            final int uidRules;
            final boolean isBackgroundRestricted;
            synchronized (mUidRulesFirstLock) {
                uidRules = mUidRules.get(uid, RULE_ALLOW_ALL);
                isBackgroundRestricted = mRestrictBackground;
            }
            return isBackgroundRestricted
                    && !hasRule(uidRules, RULE_ALLOW_METERED)
                    && !hasRule(uidRules, RULE_TEMPORARY_ALLOW_METERED);
        }

        /**
         * @return true if networking is blocked on the given interface for the given uid according
         * to current networking policies.
         */
        @Override
        public boolean isUidNetworkingBlocked(int uid, String ifname) {
            final boolean isNetworkMetered;
            synchronized (mNetworkPoliciesSecondLock) {
                isNetworkMetered = mMeteredIfaces.contains(ifname);
            }
            return isUidNetworkingBlockedInternal(uid, isNetworkMetered);
        }
    }

    private static boolean hasRule(int uidRules, int rule) {
        return (uidRules & rule) != 0;
    }

    private static void logUidStatus(int uid, String descr) {
        Slog.d(TAG, String.format("uid %d is %s", uid, descr));
    }

    /**
     * This class is used for storing and dumping the last {@link #MAX_PROC_STATE_SEQ_HISTORY}
     * (uid, procStateSeq) pairs.
     */
    @VisibleForTesting
    public static final class ProcStateSeqHistory {
        private static final int INVALID_UID = -1;

        /**
         * Denotes maximum number of items this history can hold.
         */
        private final int mMaxCapacity;
        /**
         * Used for storing the uid information.
         */
        private final int[] mUids;
        /**
         * Used for storing the sequence numbers associated with {@link #mUids}.
         */
        private final long[] mProcStateSeqs;
        /**
         * Points to the next available slot for writing (uid, procStateSeq) pair.
         */
        private int mHistoryNext;

        public ProcStateSeqHistory(int maxCapacity) {
            mMaxCapacity = maxCapacity;
            mUids = new int[mMaxCapacity];
            Arrays.fill(mUids, INVALID_UID);
            mProcStateSeqs = new long[mMaxCapacity];
        }

        @GuardedBy("mUidRulesFirstLock")
        public void addProcStateSeqUL(int uid, long procStateSeq) {
            mUids[mHistoryNext] = uid;
            mProcStateSeqs[mHistoryNext] = procStateSeq;
            mHistoryNext = increaseNext(mHistoryNext, 1);
        }

        @GuardedBy("mUidRulesFirstLock")
        public void dumpUL(IndentingPrintWriter fout) {
            if (mUids[0] == INVALID_UID) {
                fout.println("NONE");
                return;
            }
            int index = mHistoryNext;
            do {
                index = increaseNext(index, -1);
                if (mUids[index] == INVALID_UID) {
                    break;
                }
                fout.println(getString(mUids[index], mProcStateSeqs[index]));
            } while (index != mHistoryNext);
        }

        public static String getString(int uid, long procStateSeq) {
            return "UID=" + uid + " Seq=" + procStateSeq;
        }

        private int increaseNext(int next, int increment) {
            next += increment;
            if (next >= mMaxCapacity) {
                next = 0;
            } else if (next < 0) {
                next = mMaxCapacity - 1;
            }
            return next;
        }
    }

    private class NotificationId {
        private final String mTag;
        private final int mId;

        NotificationId(NetworkPolicy policy, int type) {
            mTag = buildNotificationTag(policy, type);
            mId = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NotificationId)) return false;
            NotificationId that = (NotificationId) o;
            return Objects.equals(mTag, that.mTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTag);
        }

        /**
         * Build unique tag that identifies an active {@link NetworkPolicy}
         * notification of a specific type, like {@link #TYPE_LIMIT}.
         */
        private String buildNotificationTag(NetworkPolicy policy, int type) {
            return TAG + ":" + policy.template.hashCode() + ":" + type;
        }

        public String getTag() {
            return mTag;
        }

        public int getId() {
            return mId;
        }
    }
}
