/**
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.server.vr;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.vr.IVrListener;
import android.service.vr.IVrManager;
import android.service.vr.IVrStateCallbacks;
import android.service.vr.VrListenerService;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;
import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.SystemConfig;
import com.android.server.SystemService;
import com.android.server.utils.ManagedApplicationService.PendingEvent;
import com.android.server.vr.EnabledComponentsObserver.EnabledComponentChangeListener;
import com.android.server.utils.ManagedApplicationService;
import com.android.server.utils.ManagedApplicationService.BinderChecker;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.StringBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service tracking whether VR mode is active, and notifying listening services of state changes.
 * <p/>
 * Services running in system server may modify the state of VrManagerService via the interface in
 * VrManagerInternal, and may register to receive callbacks when the system VR mode changes via the
 * interface given in VrStateListener.
 * <p/>
 * Device vendors may choose to receive VR state changes by implementing the VR mode HAL, e.g.:
 *  hardware/libhardware/modules/vr
 * <p/>
 * In general applications may enable or disable VR mode by calling
 * {@link android.app.Activity#setVrModeEnabled)}.  An application may also implement a service to
 * be run while in VR mode by implementing {@link android.service.vr.VrListenerService}.
 *
 * @see android.service.vr.VrListenerService
 * @see com.android.server.vr.VrManagerInternal
 * @see com.android.server.vr.VrStateListener
 *
 * @hide
 */
public class VrManagerService extends SystemService implements EnabledComponentChangeListener{

    public static final String TAG = "VrManagerService";

    public static final String VR_MANAGER_BINDER_SERVICE = "vrmanager";

    private static final int PENDING_STATE_DELAY_MS = 300;
    private static final int EVENT_LOG_SIZE = 32;
    private static final int INVALID_APPOPS_MODE = -1;
    /** Null set of sleep sleep flags. */
    private static final int FLAG_NONE = 0;
    /** Flag set when the device is not sleeping. */
    private static final int FLAG_AWAKE = 1;
    /** Flag set when the screen has been turned on. */
    private static final int FLAG_SCREEN_ON = 2;
    /** Flag indicating that all system sleep flags have been set.*/
    private static final int FLAG_ALL = FLAG_AWAKE | FLAG_SCREEN_ON;

    private static native void initializeNative();
    private static native void setVrModeNative(boolean enabled);

    private final Object mLock = new Object();

    private final IBinder mOverlayToken = new Binder();

    // State protected by mLock
    private boolean mVrModeAllowed;
    private boolean mVrModeEnabled;
    private EnabledComponentsObserver mComponentObserver;
    private ManagedApplicationService mCurrentVrService;
    private Context mContext;
    private ComponentName mCurrentVrModeComponent;
    private int mCurrentVrModeUser;
    private boolean mWasDefaultGranted;
    private boolean mGuard;
    private final RemoteCallbackList<IVrStateCallbacks> mRemoteCallbacks =
            new RemoteCallbackList<>();
    private int mPreviousCoarseLocationMode = INVALID_APPOPS_MODE;
    private int mPreviousManageOverlayMode = INVALID_APPOPS_MODE;
    private VrState mPendingState;
    private final ArrayDeque<VrState> mLoggingDeque = new ArrayDeque<>(EVENT_LOG_SIZE);
    private final NotificationAccessManager mNotifAccessManager = new NotificationAccessManager();
    /** Tracks the state of the screen and keyguard UI.*/
    private int mSystemSleepFlags = FLAG_NONE;

    private static final int MSG_VR_STATE_CHANGE = 0;
    private static final int MSG_PENDING_VR_STATE_CHANGE = 1;

    /**
     * Set whether VR mode may be enabled.
     * <p/>
     * If VR mode is not allowed to be enabled, calls to set VR mode will be cached.  When VR mode
     * is again allowed to be enabled, the most recent cached state will be applied.
     *
     * @param allowed {@code true} if calling any of the setVrMode methods may cause the device to
     *   enter VR mode.
     */
    private void setVrModeAllowedLocked(boolean allowed) {
        if (mVrModeAllowed != allowed) {
            mVrModeAllowed = allowed;
            Slog.i(TAG, "VR mode is " + ((allowed) ? "allowed" : "disallowed"));
            if (mVrModeAllowed) {
                consumeAndApplyPendingStateLocked();
            } else {
                // Set pending state to current state.
                mPendingState = (mVrModeEnabled && mCurrentVrService != null)
                    ? new VrState(mVrModeEnabled, mCurrentVrService.getComponent(),
                        mCurrentVrService.getUserId(), mCurrentVrModeComponent)
                    : null;

                // Unbind current VR service and do necessary callbacks.
                updateCurrentVrServiceLocked(false, null, 0, null);
            }
        }
    }

    private void setSleepState(boolean isAsleep) {
        synchronized(mLock) {

            if (!isAsleep) {
                mSystemSleepFlags |= FLAG_AWAKE;
            } else {
                mSystemSleepFlags &= ~FLAG_AWAKE;
            }

            setVrModeAllowedLocked(mSystemSleepFlags == FLAG_ALL);
        }
    }

    private void setScreenOn(boolean isScreenOn) {
        synchronized(mLock) {
            if (isScreenOn) {
                mSystemSleepFlags |= FLAG_SCREEN_ON;
            } else {
                mSystemSleepFlags &= ~FLAG_SCREEN_ON;
            }
            setVrModeAllowedLocked(mSystemSleepFlags == FLAG_ALL);
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_VR_STATE_CHANGE : {
                    boolean state = (msg.arg1 == 1);
                    int i = mRemoteCallbacks.beginBroadcast();
                    while (i > 0) {
                        i--;
                        try {
                            mRemoteCallbacks.getBroadcastItem(i).onVrStateChanged(state);
                        } catch (RemoteException e) {
                            // Noop
                        }
                    }
                    mRemoteCallbacks.finishBroadcast();
                } break;
                case MSG_PENDING_VR_STATE_CHANGE : {
                    synchronized(mLock) {
                        if (mVrModeAllowed) {
                           VrManagerService.this.consumeAndApplyPendingStateLocked();
                        }
                    }
                } break;
                default :
                    throw new IllegalStateException("Unknown message type: " + msg.what);
            }
        }
    };

    private static class VrState {
        final boolean enabled;
        final int userId;
        final ComponentName targetPackageName;
        final ComponentName callingPackage;
        final long timestamp;
        final boolean defaultPermissionsGranted;

        VrState(boolean enabled, ComponentName targetPackageName, int userId,
                ComponentName callingPackage) {
            this.enabled = enabled;
            this.userId = userId;
            this.targetPackageName = targetPackageName;
            this.callingPackage = callingPackage;
            this.defaultPermissionsGranted = false;
            this.timestamp = System.currentTimeMillis();
        }

        VrState(boolean enabled, ComponentName targetPackageName, int userId,
            ComponentName callingPackage, boolean defaultPermissionsGranted) {
            this.enabled = enabled;
            this.userId = userId;
            this.targetPackageName = targetPackageName;
            this.callingPackage = callingPackage;
            this.defaultPermissionsGranted = defaultPermissionsGranted;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final BinderChecker sBinderChecker = new BinderChecker() {
        @Override
        public IInterface asInterface(IBinder binder) {
            return IVrListener.Stub.asInterface(binder);
        }

        @Override
        public boolean checkType(IInterface service) {
            return service instanceof IVrListener;
        }
    };

    private final class NotificationAccessManager {
        private final SparseArray<ArraySet<String>> mAllowedPackages = new SparseArray<>();
        private final ArrayMap<String, Integer> mNotificationAccessPackageToUserId =
                new ArrayMap<>();

        public void update(Collection<String> packageNames) {
            int currentUserId = ActivityManager.getCurrentUser();

            ArraySet<String> allowed = mAllowedPackages.get(currentUserId);
            if (allowed == null) {
                allowed = new ArraySet<>();
            }

            // Make sure we revoke notification access for listeners in other users
            final int listenerCount = mNotificationAccessPackageToUserId.size();
            for (int i = listenerCount - 1; i >= 0; i--) {
                final int grantUserId = mNotificationAccessPackageToUserId.valueAt(i);
                if (grantUserId != currentUserId) {
                    String packageName = mNotificationAccessPackageToUserId.keyAt(i);
                    revokeNotificationListenerAccess(packageName, grantUserId);
                    revokeNotificationPolicyAccess(packageName);
                    revokeCoarseLocationPermissionIfNeeded(packageName, grantUserId);
                    mNotificationAccessPackageToUserId.removeAt(i);
                }
            }

            for (String pkg : allowed) {
                if (!packageNames.contains(pkg)) {
                    revokeNotificationListenerAccess(pkg, currentUserId);
                    revokeNotificationPolicyAccess(pkg);
                    revokeCoarseLocationPermissionIfNeeded(pkg, currentUserId);
                    mNotificationAccessPackageToUserId.remove(pkg);
                }
            }
            for (String pkg : packageNames) {
                if (!allowed.contains(pkg)) {
                    grantNotificationPolicyAccess(pkg);
                    grantNotificationListenerAccess(pkg, currentUserId);
                    grantCoarseLocationPermissionIfNeeded(pkg, currentUserId);
                    mNotificationAccessPackageToUserId.put(pkg, currentUserId);
                }
            }

            allowed.clear();
            allowed.addAll(packageNames);
            mAllowedPackages.put(currentUserId, allowed);
        }
    }

    /**
     * Called when a user, package, or setting changes that could affect whether or not the
     * currently bound VrListenerService is changed.
     */
    @Override
    public void onEnabledComponentChanged() {
        synchronized (mLock) {
            int currentUser = ActivityManager.getCurrentUser();
            // Update listeners
            ArraySet<ComponentName> enabledListeners = mComponentObserver.getEnabled(currentUser);

            ArraySet<String> enabledPackages = new ArraySet<>();
            for (ComponentName n : enabledListeners) {
                String pkg = n.getPackageName();
                if (isDefaultAllowed(pkg)) {
                    enabledPackages.add(n.getPackageName());
                }
            }
            mNotifAccessManager.update(enabledPackages);

            if (!mVrModeAllowed) {
                return; // Don't do anything, we shouldn't be in VR mode.
            }

            // If there is a pending state change, we'd better deal with that first
            consumeAndApplyPendingStateLocked(false);

            if (mCurrentVrService == null) {
                return; // No active services
            }

            // There is an active service, update it if needed
            updateCurrentVrServiceLocked(mVrModeEnabled, mCurrentVrService.getComponent(),
                    mCurrentVrService.getUserId(), null);
        }
    }

    private final IVrManager mVrManager = new IVrManager.Stub() {

        @Override
        public void registerListener(IVrStateCallbacks cb) {
            enforceCallerPermission(Manifest.permission.ACCESS_VR_MANAGER);
            if (cb == null) {
                throw new IllegalArgumentException("Callback binder object is null.");
            }

            VrManagerService.this.addStateCallback(cb);
        }

        @Override
        public void unregisterListener(IVrStateCallbacks cb) {
            enforceCallerPermission(Manifest.permission.ACCESS_VR_MANAGER);
            if (cb == null) {
                throw new IllegalArgumentException("Callback binder object is null.");
            }

            VrManagerService.this.removeStateCallback(cb);
        }

        @Override
        public boolean getVrModeState() {
            return VrManagerService.this.getVrMode();
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("permission denied: can't dump VrManagerService from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
                return;
            }
            pw.println("********* Dump of VrManagerService *********");
            pw.println("VR mode is currently: " + ((mVrModeAllowed) ? "allowed" : "disallowed"));
            pw.println("Previous state transitions:\n");
            String tab = "  ";
            dumpStateTransitions(pw);
            pw.println("\n\nRemote Callbacks:");
            int i=mRemoteCallbacks.beginBroadcast(); // create the broadcast item array
            while(i-->0) {
                pw.print(tab);
                pw.print(mRemoteCallbacks.getBroadcastItem(i));
                if (i>0) pw.println(",");
            }
            mRemoteCallbacks.finishBroadcast();
            pw.println("\n");
            pw.println("Installed VrListenerService components:");
            int userId = mCurrentVrModeUser;
            ArraySet<ComponentName> installed = mComponentObserver.getInstalled(userId);
            if (installed == null || installed.size() == 0) {
                pw.println("None");
            } else {
                for (ComponentName n : installed) {
                    pw.print(tab);
                    pw.println(n.flattenToString());
                }
            }
            pw.println("Enabled VrListenerService components:");
            ArraySet<ComponentName> enabled = mComponentObserver.getEnabled(userId);
            if (enabled == null || enabled.size() == 0) {
                pw.println("None");
            } else {
                for (ComponentName n : enabled) {
                    pw.print(tab);
                    pw.println(n.flattenToString());
                }
            }
            pw.println("\n");
            pw.println("********* End of VrManagerService Dump *********");
        }

    };

    private void enforceCallerPermission(String permission) {
        if (mContext.checkCallingOrSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold the permission " + permission);
        }
    }

    /**
     * Implementation of VrManagerInternal.  Callable only from system services.
     */
    private final class LocalService extends VrManagerInternal {
        @Override
        public void setVrMode(boolean enabled, ComponentName packageName, int userId,
                ComponentName callingPackage) {
            VrManagerService.this.setVrMode(enabled, packageName, userId, callingPackage);
        }

        @Override
        public void onSleepStateChanged(boolean isAsleep) {
            VrManagerService.this.setSleepState(isAsleep);
        }

        @Override
        public void onScreenStateChanged(boolean isScreenOn) {
            VrManagerService.this.setScreenOn(isScreenOn);
        }

        @Override
        public boolean isCurrentVrListener(String packageName, int userId) {
            return VrManagerService.this.isCurrentVrListener(packageName, userId);
        }

        @Override
        public int hasVrPackage(ComponentName packageName, int userId) {
            return VrManagerService.this.hasVrPackage(packageName, userId);
        }
    }

    public VrManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        synchronized(mLock) {
            initializeNative();
            mContext = getContext();
        }

        publishLocalService(VrManagerInternal.class, new LocalService());
        publishBinderService(VR_MANAGER_BINDER_SERVICE, mVrManager.asBinder());
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            synchronized (mLock) {
                Looper looper = Looper.getMainLooper();
                Handler handler = new Handler(looper);
                ArrayList<EnabledComponentChangeListener> listeners = new ArrayList<>();
                listeners.add(this);
                mComponentObserver = EnabledComponentsObserver.build(mContext, handler,
                        Settings.Secure.ENABLED_VR_LISTENERS, looper,
                        android.Manifest.permission.BIND_VR_LISTENER_SERVICE,
                        VrListenerService.SERVICE_INTERFACE, mLock, listeners);

                mComponentObserver.rebuildAll();
            }
        } else if (phase == SystemService.PHASE_THIRD_PARTY_APPS_CAN_START) {
            synchronized (mLock) {
                mVrModeAllowed = true;
            }
        }
    }

    @Override
    public void onStartUser(int userHandle) {
        synchronized (mLock) {
            mComponentObserver.onUsersChanged();
        }
    }

    @Override
    public void onSwitchUser(int userHandle) {
        synchronized (mLock) {
            mComponentObserver.onUsersChanged();
        }

    }

    @Override
    public void onStopUser(int userHandle) {
        synchronized (mLock) {
            mComponentObserver.onUsersChanged();
        }

    }

    @Override
    public void onCleanupUser(int userHandle) {
        synchronized (mLock) {
            mComponentObserver.onUsersChanged();
        }
    }

    private void updateOverlayStateLocked(String exemptedPackage, int newUserId, int oldUserId) {
        AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);

        // If user changed drop restrictions for the old user.
        if (oldUserId != newUserId) {
            appOpsManager.setUserRestrictionForUser(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                    false, mOverlayToken, null, oldUserId);
        }

        if (!mVrModeEnabled) {
            return;
        }

        // Apply the restrictions for the current user based on vr state
        String[] exemptions = (exemptedPackage == null) ? new String[0] :
                new String[] { exemptedPackage };

        appOpsManager.setUserRestrictionForUser(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                true, mOverlayToken, exemptions, newUserId);
    }

    private void updateDependentAppOpsLocked(String newVrServicePackage, int newUserId,
            String oldVrServicePackage, int oldUserId) {
        // If VR state changed and we also have a VR service change.
        if (Objects.equals(newVrServicePackage, oldVrServicePackage)) {
            return;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            // Set overlay exception state based on VR enabled and current service
            updateOverlayStateLocked(newVrServicePackage, newUserId, oldUserId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Send VR mode changes (if the mode state has changed), and update the bound/unbound state of
     * the currently selected VR listener service.  If the component selected for the VR listener
     * service has changed, unbind the previous listener and bind the new listener (if enabled).
     * <p/>
     * Note: Must be called while holding {@code mLock}.
     *
     * @param enabled new state for VR mode.
     * @param component new component to be bound as a VR listener.
     * @param userId user owning the component to be bound.
     * @param calling the component currently using VR mode, or null to leave unchanged.
     *
     * @return {@code true} if the component/user combination specified is valid.
     */
    private boolean updateCurrentVrServiceLocked(boolean enabled, @NonNull ComponentName component,
            int userId, ComponentName calling) {

        boolean sendUpdatedCaller = false;
        final long identity = Binder.clearCallingIdentity();
        try {

            boolean validUserComponent = (mComponentObserver.isValid(component, userId) ==
                    EnabledComponentsObserver.NO_ERROR);
            boolean goingIntoVrMode = validUserComponent && enabled;
            if (!mVrModeEnabled && !goingIntoVrMode) {
                return validUserComponent; // Disabled -> Disabled transition does nothing.
            }

            String oldVrServicePackage = mCurrentVrService != null
                    ? mCurrentVrService.getComponent().getPackageName() : null;
            final int oldUserId = mCurrentVrModeUser;

            // Notify system services and VR HAL of mode change.
            changeVrModeLocked(goingIntoVrMode);

            boolean nothingChanged = false;
            if (!goingIntoVrMode) {
                // Not going into VR mode, unbind whatever is running
                if (mCurrentVrService != null) {
                    Slog.i(TAG, "Leaving VR mode, disconnecting "
                        + mCurrentVrService.getComponent() + " for user "
                        + mCurrentVrService.getUserId());
                    mCurrentVrService.disconnect();
                    mCurrentVrService = null;
                } else {
                    nothingChanged = true;
                }
            } else {
                // Going into VR mode
                if (mCurrentVrService != null) {
                    // Unbind any running service that doesn't match the latest component/user
                    // selection.
                    if (mCurrentVrService.disconnectIfNotMatching(component, userId)) {
                        Slog.i(TAG, "VR mode component changed to " + component
                            + ", disconnecting " + mCurrentVrService.getComponent()
                            + " for user " + mCurrentVrService.getUserId());
                        createAndConnectService(component, userId);
                        sendUpdatedCaller = true;
                    } else {
                        nothingChanged = true;
                    }
                    // The service with the correct component/user is already bound, do nothing.
                } else {
                    // Nothing was previously running, bind a new service for the latest
                    // component/user selection.
                    createAndConnectService(component, userId);
                    sendUpdatedCaller = true;
                }
            }

            if (calling != null && !Objects.equals(calling, mCurrentVrModeComponent)) {
                mCurrentVrModeComponent = calling;
                sendUpdatedCaller = true;
            }

            if (mCurrentVrModeUser != userId) {
                mCurrentVrModeUser = userId;
                sendUpdatedCaller = true;
            }

            String newVrServicePackage = mCurrentVrService != null
                    ? mCurrentVrService.getComponent().getPackageName() : null;
            final int newUserId = mCurrentVrModeUser;

            // Update AppOps settings that change state when entering/exiting VR mode, or changing
            // the current VrListenerService.
            updateDependentAppOpsLocked(newVrServicePackage, newUserId,
                    oldVrServicePackage, oldUserId);

            if (mCurrentVrService != null && sendUpdatedCaller) {
                final ComponentName c = mCurrentVrModeComponent;
                mCurrentVrService.sendEvent(new PendingEvent() {
                    @Override
                    public void runEvent(IInterface service) throws RemoteException {
                        IVrListener l = (IVrListener) service;
                        l.focusedActivityChanged(c);
                    }
                });
            }

            if (!nothingChanged) {
                logStateLocked();
            }

            return validUserComponent;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isDefaultAllowed(String packageName) {
        PackageManager pm = mContext.getPackageManager();

        ApplicationInfo info = null;
        try {
            info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
        }

        if (info == null || !(info.isSystemApp() || info.isUpdatedSystemApp())) {
            return false;
        }
        return true;
    }

    private void grantNotificationPolicyAccess(String pkg) {
        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        nm.setNotificationPolicyAccessGranted(pkg, true);
    }

    private void revokeNotificationPolicyAccess(String pkg) {
        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        // Remove any DND zen rules possibly created by the package.
        nm.removeAutomaticZenRules(pkg);
        // Remove Notification Policy Access.
        nm.setNotificationPolicyAccessGranted(pkg, false);
    }

    private void grantNotificationListenerAccess(String pkg, int userId) {
        PackageManager pm = mContext.getPackageManager();
        ArraySet<ComponentName> possibleServices = EnabledComponentsObserver.loadComponentNames(pm,
                userId, NotificationListenerService.SERVICE_INTERFACE,
                android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE);
        ContentResolver resolver = mContext.getContentResolver();

        ArraySet<String> current = getNotificationListeners(resolver, userId);

        for (ComponentName c : possibleServices) {
            String flatName = c.flattenToString();
            if (Objects.equals(c.getPackageName(), pkg)
                    && !current.contains(flatName)) {
                current.add(flatName);
            }
        }

        if (current.size() > 0) {
            String flatSettings = formatSettings(current);
            Settings.Secure.putStringForUser(resolver,
                    Settings.Secure.ENABLED_NOTIFICATION_LISTENERS,
                    flatSettings, userId);
        }
    }

    private void revokeNotificationListenerAccess(String pkg, int userId) {
        ContentResolver resolver = mContext.getContentResolver();

        ArraySet<String> current = getNotificationListeners(resolver, userId);

        ArrayList<String> toRemove = new ArrayList<>();

        for (String c : current) {
            ComponentName component = ComponentName.unflattenFromString(c);
            if (component != null && component.getPackageName().equals(pkg)) {
                toRemove.add(c);
            }
        }

        current.removeAll(toRemove);

        String flatSettings = formatSettings(current);
        Settings.Secure.putStringForUser(resolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS,
                flatSettings, userId);
    }

    private void grantCoarseLocationPermissionIfNeeded(String pkg, int userId) {
        // Don't clobber the user if permission set in current state explicitly
        if (!isPermissionUserUpdated(Manifest.permission.ACCESS_COARSE_LOCATION, pkg, userId)) {
            try {
                mContext.getPackageManager().grantRuntimePermission(pkg,
                        Manifest.permission.ACCESS_COARSE_LOCATION, new UserHandle(userId));
            } catch (IllegalArgumentException e) {
                // Package was removed during update.
                Slog.w(TAG, "Could not grant coarse location permission, package " + pkg
                    + " was removed.");
            }
        }
    }

    private void revokeCoarseLocationPermissionIfNeeded(String pkg, int userId) {
        // Don't clobber the user if permission set in current state explicitly
        if (!isPermissionUserUpdated(Manifest.permission.ACCESS_COARSE_LOCATION, pkg, userId)) {
            try {
                mContext.getPackageManager().revokeRuntimePermission(pkg,
                        Manifest.permission.ACCESS_COARSE_LOCATION, new UserHandle(userId));
            } catch (IllegalArgumentException e) {
                // Package was removed during update.
                Slog.w(TAG, "Could not revoke coarse location permission, package " + pkg
                    + " was removed.");
            }
        }
    }

    private boolean isPermissionUserUpdated(String permission, String pkg, int userId) {
        final int flags = mContext.getPackageManager().getPermissionFlags(
                permission, pkg, new UserHandle(userId));
        return (flags & (PackageManager.FLAG_PERMISSION_USER_SET
                | PackageManager.FLAG_PERMISSION_USER_FIXED)) != 0;
    }

    private ArraySet<String> getNotificationListeners(ContentResolver resolver, int userId) {
        String flat = Settings.Secure.getStringForUser(resolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS, userId);

        ArraySet<String> current = new ArraySet<>();
        if (flat != null) {
            String[] allowed = flat.split(":");
            for (String s : allowed) {
                if (!TextUtils.isEmpty(s)) {
                    current.add(s);
                }
            }
        }
        return current;
    }

    private static String formatSettings(Collection<String> c) {
        if (c == null || c.isEmpty()) {
            return "";
        }

        StringBuilder b = new StringBuilder();
        boolean start = true;
        for (String s : c) {
            if ("".equals(s)) {
                continue;
            }
            if (!start) {
                b.append(':');
            }
            b.append(s);
            start = false;
        }
        return b.toString();
    }



    private void createAndConnectService(@NonNull ComponentName component, int userId) {
        mCurrentVrService = VrManagerService.create(mContext, component, userId);
        mCurrentVrService.connect();
        Slog.i(TAG, "Connecting " + component + " for user " + userId);
    }

    /**
     * Send VR mode change callbacks to HAL and system services if mode has actually changed.
     * <p/>
     * Note: Must be called while holding {@code mLock}.
     *
     * @param enabled new state of the VR mode.
     */
    private void changeVrModeLocked(boolean enabled) {
        if (mVrModeEnabled != enabled) {
            mVrModeEnabled = enabled;

            // Log mode change event.
            Slog.i(TAG, "VR mode " + ((mVrModeEnabled) ? "enabled" : "disabled"));
            setVrModeNative(mVrModeEnabled);

            onVrModeChangedLocked();
        }
    }

    /**
     * Notify system services of VR mode change.
     * <p/>
     * Note: Must be called while holding {@code mLock}.
     */
    private void onVrModeChangedLocked() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_VR_STATE_CHANGE,
                (mVrModeEnabled) ? 1 : 0, 0));
    }

    /**
     * Helper function for making ManagedApplicationService instances.
     */
    private static ManagedApplicationService create(@NonNull Context context,
            @NonNull ComponentName component, int userId) {
        return ManagedApplicationService.build(context, component, userId,
                R.string.vr_listener_binding_label, Settings.ACTION_VR_LISTENER_SETTINGS,
                sBinderChecker);
    }

    /**
     * Apply the pending VR state. If no state is pending, disconnect any currently bound
     * VR listener service.
     */
    private void consumeAndApplyPendingStateLocked() {
        consumeAndApplyPendingStateLocked(true);
    }

    /**
     * Apply the pending VR state.
     *
     * @param disconnectIfNoPendingState if {@code true}, then any currently bound VR listener
     *     service will be disconnected if no state is pending. If this is {@code false} then the
     *     nothing will be changed when there is no pending state.
     */
    private void consumeAndApplyPendingStateLocked(boolean disconnectIfNoPendingState) {
        if (mPendingState != null) {
            updateCurrentVrServiceLocked(mPendingState.enabled,
                    mPendingState.targetPackageName, mPendingState.userId,
                    mPendingState.callingPackage);
            mPendingState = null;
        } else if (disconnectIfNoPendingState) {
            updateCurrentVrServiceLocked(false, null, 0, null);
        }
    }

    private void logStateLocked() {
        ComponentName currentBoundService = (mCurrentVrService == null) ? null :
            mCurrentVrService.getComponent();
        VrState current = new VrState(mVrModeEnabled, currentBoundService, mCurrentVrModeUser,
            mCurrentVrModeComponent, mWasDefaultGranted);
        if (mLoggingDeque.size() == EVENT_LOG_SIZE) {
            mLoggingDeque.removeFirst();
        }
        mLoggingDeque.add(current);
    }

    private void dumpStateTransitions(PrintWriter pw) {
        SimpleDateFormat d = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        String tab = "  ";
        if (mLoggingDeque.size() == 0) {
            pw.print(tab);
            pw.println("None");
        }
        for (VrState state : mLoggingDeque) {
            pw.print(d.format(new Date(state.timestamp)));
            pw.print(tab);
            pw.print("State changed to:");
            pw.print(tab);
            pw.println((state.enabled) ? "ENABLED" : "DISABLED");
            if (state.enabled) {
                pw.print(tab);
                pw.print("User=");
                pw.println(state.userId);
                pw.print(tab);
                pw.print("Current VR Activity=");
                pw.println((state.callingPackage == null) ?
                    "None" : state.callingPackage.flattenToString());
                pw.print(tab);
                pw.print("Bound VrListenerService=");
                pw.println((state.targetPackageName == null) ?
                    "None" : state.targetPackageName.flattenToString());
                if (state.defaultPermissionsGranted) {
                    pw.print(tab);
                    pw.println("Default permissions granted to the bound VrListenerService.");
                }
            }
        }
    }

    /*
     * Implementation of VrManagerInternal calls.  These are callable from system services.
     */
    private void setVrMode(boolean enabled, @NonNull ComponentName targetPackageName,
            int userId, @NonNull ComponentName callingPackage) {

        synchronized (mLock) {
            VrState pending = new VrState(enabled, targetPackageName, userId, callingPackage);
            if (!mVrModeAllowed) {
                // We're not allowed to be in VR mode.  Make this state pending.  This will be
                // applied the next time we are allowed to enter VR mode unless it is superseded by
                // another call.
                mPendingState = pending;
                return;
            }

            if (!enabled && mCurrentVrService != null) {
                // If we're transitioning out of VR mode, delay briefly to avoid expensive HAL calls
                // and service bind/unbind in case we are immediately switching to another VR app.
                if (mPendingState == null) {
                    mHandler.sendEmptyMessageDelayed(MSG_PENDING_VR_STATE_CHANGE,
                            PENDING_STATE_DELAY_MS);
                }

                mPendingState = pending;
                return;
            } else {
                mHandler.removeMessages(MSG_PENDING_VR_STATE_CHANGE);
                mPendingState = null;
            }

            updateCurrentVrServiceLocked(enabled, targetPackageName, userId, callingPackage);
        }
    }

    private int hasVrPackage(@NonNull ComponentName targetPackageName, int userId) {
        synchronized (mLock) {
            return mComponentObserver.isValid(targetPackageName, userId);
        }
    }

    private boolean isCurrentVrListener(String packageName, int userId) {
        synchronized (mLock) {
            if (mCurrentVrService == null) {
                return false;
            }
            return mCurrentVrService.getComponent().getPackageName().equals(packageName) &&
                    userId == mCurrentVrService.getUserId();
        }
    }

    /*
     * Implementation of IVrManager calls.
     */

    private void addStateCallback(IVrStateCallbacks cb) {
        mRemoteCallbacks.register(cb);
    }

    private void removeStateCallback(IVrStateCallbacks cb) {
        mRemoteCallbacks.unregister(cb);
    }

    private boolean getVrMode() {
        synchronized (mLock) {
            return mVrModeEnabled;
        }
    }
}
