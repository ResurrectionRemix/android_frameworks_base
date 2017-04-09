/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.policy;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.messages.SystemMessageProto.SystemMessage;
import com.android.internal.util.UserIcons;
import com.android.settingslib.RestrictedLockUtils;
import com.android.systemui.GuestResumeSessionReceiver;
import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.SystemUISecondaryUserService;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.tiles.UserDetailView;
import com.android.systemui.statusbar.phone.ActivityStarter;
import com.android.systemui.statusbar.phone.SystemUIDialog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

/**
 * Keeps a list of all users on the device for user switching.
 */
public class UserSwitcherController {

    private static final String TAG = "UserSwitcherController";
    private static final boolean DEBUG = false;
    private static final String SIMPLE_USER_SWITCHER_GLOBAL_SETTING =
            "lockscreenSimpleUserSwitcher";
    private static final String ACTION_REMOVE_GUEST = "com.android.systemui.REMOVE_GUEST";
    private static final String ACTION_LOGOUT_USER = "com.android.systemui.LOGOUT_USER";
    private static final int PAUSE_REFRESH_USERS_TIMEOUT_MS = 3000;

    private static final String TAG_REMOVE_GUEST = "remove_guest";
    private static final String TAG_LOGOUT_USER = "logout_user";

    private static final String PERMISSION_SELF = "com.android.systemui.permission.SELF";

    private final Context mContext;
    private final UserManager mUserManager;
    private final ArrayList<WeakReference<BaseUserAdapter>> mAdapters = new ArrayList<>();
    private final GuestResumeSessionReceiver mGuestResumeSessionReceiver
            = new GuestResumeSessionReceiver();
    private final KeyguardMonitor mKeyguardMonitor;
    private final Handler mHandler;
    private final ActivityStarter mActivityStarter;

    private ArrayList<UserRecord> mUsers = new ArrayList<>();
    private Dialog mExitGuestDialog;
    private Dialog mAddUserDialog;
    private int mLastNonGuestUser = UserHandle.USER_SYSTEM;
    private boolean mSimpleUserSwitcher;
    private boolean mAddUsersWhenLocked;
    private boolean mPauseRefreshUsers;
    private int mSecondaryUser = UserHandle.USER_NULL;
    private Intent mSecondaryUserServiceIntent;
    private SparseBooleanArray mForcePictureLoadForUserId = new SparseBooleanArray(2);

    public UserSwitcherController(Context context, KeyguardMonitor keyguardMonitor,
            Handler handler, ActivityStarter activityStarter) {
        mContext = context;
        mGuestResumeSessionReceiver.register(context);
        mKeyguardMonitor = keyguardMonitor;
        mHandler = handler;
        mActivityStarter = activityStarter;
        mUserManager = UserManager.get(context);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiverAsUser(mReceiver, UserHandle.SYSTEM, filter,
                null /* permission */, null /* scheduler */);

        mSecondaryUserServiceIntent = new Intent(context, SystemUISecondaryUserService.class);

        filter = new IntentFilter();
        filter.addAction(ACTION_REMOVE_GUEST);
        filter.addAction(ACTION_LOGOUT_USER);
        mContext.registerReceiverAsUser(mReceiver, UserHandle.SYSTEM, filter,
                PERMISSION_SELF, null /* scheduler */);

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SIMPLE_USER_SWITCHER_GLOBAL_SETTING), true,
                mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADD_USERS_WHEN_LOCKED), true,
                mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(
                        Settings.Global.ALLOW_USER_SWITCHING_WHEN_SYSTEM_USER_LOCKED),
                true, mSettingsObserver);
        // Fetch initial values.
        mSettingsObserver.onChange(false);

        keyguardMonitor.addCallback(mCallback);
        listenForCallState();

        refreshUsers(UserHandle.USER_NULL);
    }

    /**
     * Refreshes users from UserManager.
     *
     * The pictures are only loaded if they have not been loaded yet.
     *
     * @param forcePictureLoadForId forces the picture of the given user to be reloaded.
     */
    @SuppressWarnings("unchecked")
    private void refreshUsers(int forcePictureLoadForId) {
        if (DEBUG) Log.d(TAG, "refreshUsers(forcePictureLoadForId=" + forcePictureLoadForId+")");
        if (forcePictureLoadForId != UserHandle.USER_NULL) {
            mForcePictureLoadForUserId.put(forcePictureLoadForId, true);
        }

        if (mPauseRefreshUsers) {
            return;
        }

        boolean forceAllUsers = mForcePictureLoadForUserId.get(UserHandle.USER_ALL);
        SparseArray<Bitmap> bitmaps = new SparseArray<>(mUsers.size());
        final int N = mUsers.size();
        for (int i = 0; i < N; i++) {
            UserRecord r = mUsers.get(i);
            if (r == null || r.picture == null || r.info == null || forceAllUsers
                    || mForcePictureLoadForUserId.get(r.info.id)) {
                continue;
            }
            bitmaps.put(r.info.id, r.picture);
        }
        mForcePictureLoadForUserId.clear();

        final boolean addUsersWhenLocked = mAddUsersWhenLocked;
        new AsyncTask<SparseArray<Bitmap>, Void, ArrayList<UserRecord>>() {
            @SuppressWarnings("unchecked")
            @Override
            protected ArrayList<UserRecord> doInBackground(SparseArray<Bitmap>... params) {
                final SparseArray<Bitmap> bitmaps = params[0];
                List<UserInfo> infos = mUserManager.getUsers(true);
                if (infos == null) {
                    return null;
                }
                ArrayList<UserRecord> records = new ArrayList<>(infos.size());
                int currentId = ActivityManager.getCurrentUser();
                boolean canSwitchUsers = mUserManager.canSwitchUsers();
                UserInfo currentUserInfo = null;
                UserRecord guestRecord = null;

                for (UserInfo info : infos) {
                    boolean isCurrent = currentId == info.id;
                    if (isCurrent) {
                        currentUserInfo = info;
                    }
                    boolean switchToEnabled = canSwitchUsers || isCurrent;
                    if (info.isEnabled()) {
                        if (info.isGuest()) {
                            // Tapping guest icon triggers remove and a user switch therefore
                            // the icon shouldn't be enabled even if the user is current
                            guestRecord = new UserRecord(info, null /* picture */,
                                    true /* isGuest */, isCurrent, false /* isAddUser */,
                                    false /* isRestricted */, canSwitchUsers);
                        } else if (info.supportsSwitchToByUser()) {
                            Bitmap picture = bitmaps.get(info.id);
                            if (picture == null) {
                                picture = mUserManager.getUserIcon(info.id);

                                if (picture != null) {
                                    int avatarSize = mContext.getResources()
                                            .getDimensionPixelSize(R.dimen.max_avatar_size);
                                    picture = Bitmap.createScaledBitmap(
                                            picture, avatarSize, avatarSize, true);
                                }
                            }
                            int index = isCurrent ? 0 : records.size();
                            records.add(index, new UserRecord(info, picture, false /* isGuest */,
                                    isCurrent, false /* isAddUser */, false /* isRestricted */,
                                    switchToEnabled));
                        }
                    }
                }

                boolean systemCanCreateUsers = !mUserManager.hasBaseUserRestriction(
                                UserManager.DISALLOW_ADD_USER, UserHandle.SYSTEM);
                boolean currentUserCanCreateUsers = currentUserInfo != null
                        && (currentUserInfo.isAdmin()
                                || currentUserInfo.id == UserHandle.USER_SYSTEM)
                        && systemCanCreateUsers;
                boolean anyoneCanCreateUsers = systemCanCreateUsers && addUsersWhenLocked;
                boolean canCreateGuest = (currentUserCanCreateUsers || anyoneCanCreateUsers)
                        && guestRecord == null;
                boolean canCreateUser = (currentUserCanCreateUsers || anyoneCanCreateUsers)
                        && mUserManager.canAddMoreUsers();
                boolean createIsRestricted = !addUsersWhenLocked;

                if (!mSimpleUserSwitcher) {
                    if (guestRecord == null) {
                        if (canCreateGuest) {
                            guestRecord = new UserRecord(null /* info */, null /* picture */,
                                    true /* isGuest */, false /* isCurrent */,
                                    false /* isAddUser */, createIsRestricted, canSwitchUsers);
                            checkIfAddUserDisallowedByAdminOnly(guestRecord);
                            records.add(guestRecord);
                        }
                    } else {
                        int index = guestRecord.isCurrent ? 0 : records.size();
                        records.add(index, guestRecord);
                    }
                }

                if (!mSimpleUserSwitcher && canCreateUser) {
                    UserRecord addUserRecord = new UserRecord(null /* info */, null /* picture */,
                            false /* isGuest */, false /* isCurrent */, true /* isAddUser */,
                            createIsRestricted, canSwitchUsers);
                    checkIfAddUserDisallowedByAdminOnly(addUserRecord);
                    records.add(addUserRecord);
                }

                return records;
            }

            @Override
            protected void onPostExecute(ArrayList<UserRecord> userRecords) {
                if (userRecords != null) {
                    mUsers = userRecords;
                    notifyAdapters();
                }
            }
        }.execute((SparseArray) bitmaps);
    }

    private void pauseRefreshUsers() {
        if (!mPauseRefreshUsers) {
            mHandler.postDelayed(mUnpauseRefreshUsers, PAUSE_REFRESH_USERS_TIMEOUT_MS);
            mPauseRefreshUsers = true;
        }
    }

    private void notifyAdapters() {
        for (int i = mAdapters.size() - 1; i >= 0; i--) {
            BaseUserAdapter adapter = mAdapters.get(i).get();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            } else {
                mAdapters.remove(i);
            }
        }
    }

    public boolean isSimpleUserSwitcher() {
        return mSimpleUserSwitcher;
    }

    public boolean useFullscreenUserSwitcher() {
        // Use adb to override:
        // adb shell settings put system enable_fullscreen_user_switcher 0  # Turn it off.
        // adb shell settings put system enable_fullscreen_user_switcher 1  # Turn it on.
        // Restart SystemUI or adb reboot.
        final int DEFAULT = -1;
        final int overrideUseFullscreenUserSwitcher =
                Settings.System.getInt(mContext.getContentResolver(),
                        "enable_fullscreen_user_switcher", DEFAULT);
        if (overrideUseFullscreenUserSwitcher != DEFAULT) {
            return overrideUseFullscreenUserSwitcher != 0;
        }
        // Otherwise default to the build setting.
        return mContext.getResources().getBoolean(R.bool.config_enableFullscreenUserSwitcher);
    }

    public void logoutCurrentUser() {
        int currentUser = ActivityManager.getCurrentUser();
        if (currentUser != UserHandle.USER_SYSTEM) {
            pauseRefreshUsers();
            ActivityManager.logoutCurrentUser();
        }
    }

    public void removeUserId(int userId) {
        if (userId == UserHandle.USER_SYSTEM) {
            Log.w(TAG, "User " + userId + " could not removed.");
            return;
        }
        if (ActivityManager.getCurrentUser() == userId) {
            switchToUserId(UserHandle.USER_SYSTEM);
        }
        if (mUserManager.removeUser(userId)) {
            refreshUsers(UserHandle.USER_NULL);
        }
    }

    public void switchTo(UserRecord record) {
        int id;
        if (record.isGuest && record.info == null) {
            // No guest user. Create one.
            UserInfo guest = mUserManager.createGuest(
                    mContext, mContext.getString(R.string.guest_nickname));
            if (guest == null) {
                // Couldn't create guest, most likely because there already exists one, we just
                // haven't reloaded the user list yet.
                return;
            }
            id = guest.id;
        } else if (record.isAddUser) {
            showAddUserDialog();
            return;
        } else {
            id = record.info.id;
        }

        if (ActivityManager.getCurrentUser() == id) {
            if (record.isGuest) {
                showExitGuestDialog(id);
            }
            return;
        }

        switchToUserId(id);
    }

    public void switchTo(int userId) {
        final int count = mUsers.size();
        for (int i = 0; i < count; ++i) {
            UserRecord record = mUsers.get(i);
            if (record.info != null && record.info.id == userId) {
                switchTo(record);
                return;
            }
        }

        Log.e(TAG, "Couldn't switch to user, id=" + userId);
    }

    public int getSwitchableUserCount() {
        int count = 0;
        final int N = mUsers.size();
        for (int i = 0; i < N; ++i) {
            UserRecord record = mUsers.get(i);
            if (record.info != null && record.info.supportsSwitchTo()) {
                count++;
            }
        }
        return count;
    }

    private void switchToUserId(int id) {
        try {
            pauseRefreshUsers();
            ActivityManagerNative.getDefault().switchUser(id);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't switch user.", e);
        }
    }

    private void showExitGuestDialog(int id) {
        if (mExitGuestDialog != null && mExitGuestDialog.isShowing()) {
            mExitGuestDialog.cancel();
        }
        mExitGuestDialog = new ExitGuestDialog(mContext, id);
        mExitGuestDialog.show();
    }

    private void showAddUserDialog() {
        if (mAddUserDialog != null && mAddUserDialog.isShowing()) {
            mAddUserDialog.cancel();
        }
        mAddUserDialog = new AddUserDialog(mContext);
        mAddUserDialog.show();
    }

    private void exitGuest(int id) {
        int newId = UserHandle.USER_SYSTEM;
        if (mLastNonGuestUser != UserHandle.USER_SYSTEM) {
            UserInfo info = mUserManager.getUserInfo(mLastNonGuestUser);
            if (info != null && info.isEnabled() && info.supportsSwitchToByUser()) {
                newId = info.id;
            }
        }
        switchToUserId(newId);
        mUserManager.removeUser(id);
    }

    private void listenForCallState() {
        TelephonyManager.from(mContext).listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        private int mCallState;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (mCallState == state) return;
            if (DEBUG) Log.v(TAG, "Call state changed: " + state);
            mCallState = state;
            int currentUserId = ActivityManager.getCurrentUser();
            UserInfo userInfo = mUserManager.getUserInfo(currentUserId);
            if (userInfo != null && userInfo.isGuest()) {
                showGuestNotification(currentUserId);
            }
            refreshUsers(UserHandle.USER_NULL);
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Log.v(TAG, "Broadcast: a=" + intent.getAction()
                       + " user=" + intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1));
            }

            boolean unpauseRefreshUsers = false;
            int forcePictureLoadForId = UserHandle.USER_NULL;

            if (ACTION_REMOVE_GUEST.equals(intent.getAction())) {
                int currentUser = ActivityManager.getCurrentUser();
                UserInfo userInfo = mUserManager.getUserInfo(currentUser);
                if (userInfo != null && userInfo.isGuest()) {
                    showExitGuestDialog(currentUser);
                }
                return;
            } else if (ACTION_LOGOUT_USER.equals(intent.getAction())) {
                logoutCurrentUser();
            } else if (Intent.ACTION_USER_SWITCHED.equals(intent.getAction())) {
                if (mExitGuestDialog != null && mExitGuestDialog.isShowing()) {
                    mExitGuestDialog.cancel();
                    mExitGuestDialog = null;
                }

                final int currentId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                final UserInfo userInfo = mUserManager.getUserInfo(currentId);
                final int N = mUsers.size();
                for (int i = 0; i < N; i++) {
                    UserRecord record = mUsers.get(i);
                    if (record.info == null) continue;
                    boolean shouldBeCurrent = record.info.id == currentId;
                    if (record.isCurrent != shouldBeCurrent) {
                        mUsers.set(i, record.copyWithIsCurrent(shouldBeCurrent));
                    }
                    if (shouldBeCurrent && !record.isGuest) {
                        mLastNonGuestUser = record.info.id;
                    }
                    if ((userInfo == null || !userInfo.isAdmin()) && record.isRestricted) {
                        // Immediately remove restricted records in case the AsyncTask is too slow.
                        mUsers.remove(i);
                        i--;
                    }
                }
                notifyAdapters();

                // Disconnect from the old secondary user's service
                if (mSecondaryUser != UserHandle.USER_NULL) {
                    context.stopServiceAsUser(mSecondaryUserServiceIntent,
                            UserHandle.of(mSecondaryUser));
                    mSecondaryUser = UserHandle.USER_NULL;
                }
                // Connect to the new secondary user's service (purely to ensure that a persistent
                // SystemUI application is created for that user)
                if (userInfo != null && !userInfo.isPrimary()) {
                    context.startServiceAsUser(mSecondaryUserServiceIntent,
                            UserHandle.of(userInfo.id));
                    mSecondaryUser = userInfo.id;
                }

                if (UserManager.isSplitSystemUser() && userInfo != null && !userInfo.isGuest()
                        && userInfo.id != UserHandle.USER_SYSTEM) {
                    showLogoutNotification(currentId);
                }
                if (userInfo != null && userInfo.isGuest()) {
                    showGuestNotification(currentId);
                }
                unpauseRefreshUsers = true;
            } else if (Intent.ACTION_USER_INFO_CHANGED.equals(intent.getAction())) {
                forcePictureLoadForId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,
                        UserHandle.USER_NULL);
            } else if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                // Unlocking the system user may require a refresh
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
                if (userId != UserHandle.USER_SYSTEM) {
                    return;
                }
            }
            refreshUsers(forcePictureLoadForId);
            if (unpauseRefreshUsers) {
                mUnpauseRefreshUsers.run();
            }
        }

        private void showLogoutNotification(int userId) {
            PendingIntent logoutPI = PendingIntent.getBroadcastAsUser(mContext,
                    0, new Intent(ACTION_LOGOUT_USER), 0, UserHandle.SYSTEM);
            Notification.Builder builder = new Notification.Builder(mContext)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setPriority(Notification.PRIORITY_MIN)
                    .setSmallIcon(R.drawable.ic_person)
                    .setContentTitle(mContext.getString(R.string.user_logout_notification_title))
                    .setContentText(mContext.getString(R.string.user_logout_notification_text))
                    .setContentIntent(logoutPI)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .addAction(R.drawable.ic_delete,
                            mContext.getString(R.string.user_logout_notification_action),
                            logoutPI);
            SystemUI.overrideNotificationAppName(mContext, builder);
            NotificationManager.from(mContext).notifyAsUser(TAG_LOGOUT_USER,
                    SystemMessage.NOTE_LOGOUT_USER, builder.build(), new UserHandle(userId));
        }
    };

    private void showGuestNotification(int guestUserId) {
        boolean canSwitchUsers = mUserManager.canSwitchUsers();
        // Disable 'Remove guest' action if cannot switch users right now
        PendingIntent removeGuestPI = canSwitchUsers ? PendingIntent.getBroadcastAsUser(mContext,
                0, new Intent(ACTION_REMOVE_GUEST), 0, UserHandle.SYSTEM) : null;

        Notification.Builder builder = new Notification.Builder(mContext)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setPriority(Notification.PRIORITY_MIN)
                .setSmallIcon(R.drawable.ic_person)
                .setContentTitle(mContext.getString(R.string.guest_notification_title))
                .setContentText(mContext.getString(R.string.guest_notification_text))
                .setContentIntent(removeGuestPI)
                .setShowWhen(false)
                .addAction(R.drawable.ic_delete,
                        mContext.getString(R.string.guest_notification_remove_action),
                        removeGuestPI);
        SystemUI.overrideNotificationAppName(mContext, builder);
        NotificationManager.from(mContext).notifyAsUser(TAG_REMOVE_GUEST,
                SystemMessage.NOTE_REMOVE_GUEST, builder.build(), new UserHandle(guestUserId));
    }

    private final Runnable mUnpauseRefreshUsers = new Runnable() {
        @Override
        public void run() {
            mHandler.removeCallbacks(this);
            mPauseRefreshUsers = false;
            refreshUsers(UserHandle.USER_NULL);
        }
    };

    private final ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            mSimpleUserSwitcher = Settings.Global.getInt(mContext.getContentResolver(),
                    SIMPLE_USER_SWITCHER_GLOBAL_SETTING, 0) != 0;
            mAddUsersWhenLocked = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.ADD_USERS_WHEN_LOCKED, 0) != 0;
            refreshUsers(UserHandle.USER_NULL);
        };
    };

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UserSwitcherController state:");
        pw.println("  mLastNonGuestUser=" + mLastNonGuestUser);
        pw.print("  mUsers.size="); pw.println(mUsers.size());
        for (int i = 0; i < mUsers.size(); i++) {
            final UserRecord u = mUsers.get(i);
            pw.print("    "); pw.println(u.toString());
        }
    }

    public String getCurrentUserName(Context context) {
        if (mUsers.isEmpty()) return null;
        UserRecord item = mUsers.get(0);
        if (item == null || item.info == null) return null;
        if (item.isGuest) return context.getString(R.string.guest_nickname);
        return item.info.name;
    }

    public void onDensityOrFontScaleChanged() {
        refreshUsers(UserHandle.USER_ALL);
    }

    public static abstract class BaseUserAdapter extends BaseAdapter {

        final UserSwitcherController mController;

        protected BaseUserAdapter(UserSwitcherController controller) {
            mController = controller;
            controller.mAdapters.add(new WeakReference<>(this));
        }

        @Override
        public int getCount() {
            boolean secureKeyguardShowing = mController.mKeyguardMonitor.isShowing()
                    && mController.mKeyguardMonitor.isSecure()
                    && !mController.mKeyguardMonitor.canSkipBouncer();
            if (!secureKeyguardShowing) {
                return mController.mUsers.size();
            }
            // The lock screen is secure and showing. Filter out restricted records.
            final int N = mController.mUsers.size();
            int count = 0;
            for (int i = 0; i < N; i++) {
                if (mController.mUsers.get(i).isRestricted) {
                    break;
                } else {
                    count++;
                }
            }
            return count;
        }

        @Override
        public UserRecord getItem(int position) {
            return mController.mUsers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void switchTo(UserRecord record) {
            mController.switchTo(record);
        }

        public String getName(Context context, UserRecord item) {
            if (item.isGuest) {
                if (item.isCurrent) {
                    return context.getString(R.string.guest_exit_guest);
                } else {
                    return context.getString(
                            item.info == null ? R.string.guest_new_guest : R.string.guest_nickname);
                }
            } else if (item.isAddUser) {
                return context.getString(R.string.user_add_user);
            } else {
                return item.info.name;
            }
        }

        public Drawable getDrawable(Context context, UserRecord item) {
            if (item.isAddUser) {
                return context.getDrawable(R.drawable.ic_add_circle_qs);
            }
            return UserIcons.getDefaultUserIcon(item.resolveId(), /* light= */ true);
        }

        public void refresh() {
            mController.refreshUsers(UserHandle.USER_NULL);
        }
    }

    private void checkIfAddUserDisallowedByAdminOnly(UserRecord record) {
        EnforcedAdmin admin = RestrictedLockUtils.checkIfRestrictionEnforced(mContext,
                UserManager.DISALLOW_ADD_USER, ActivityManager.getCurrentUser());
        if (admin != null && !RestrictedLockUtils.hasBaseUserRestriction(mContext,
                UserManager.DISALLOW_ADD_USER, ActivityManager.getCurrentUser())) {
            record.isDisabledByAdmin = true;
            record.enforcedAdmin = admin;
        } else {
            record.isDisabledByAdmin = false;
            record.enforcedAdmin = null;
        }
    }

    public void startActivity(Intent intent) {
        mActivityStarter.startActivity(intent, true);
    }

    public static final class UserRecord {
        public final UserInfo info;
        public final Bitmap picture;
        public final boolean isGuest;
        public final boolean isCurrent;
        public final boolean isAddUser;
        /** If true, the record is only visible to the owner and only when unlocked. */
        public final boolean isRestricted;
        public boolean isDisabledByAdmin;
        public EnforcedAdmin enforcedAdmin;
        public boolean isSwitchToEnabled;

        public UserRecord(UserInfo info, Bitmap picture, boolean isGuest, boolean isCurrent,
                boolean isAddUser, boolean isRestricted, boolean isSwitchToEnabled) {
            this.info = info;
            this.picture = picture;
            this.isGuest = isGuest;
            this.isCurrent = isCurrent;
            this.isAddUser = isAddUser;
            this.isRestricted = isRestricted;
            this.isSwitchToEnabled = isSwitchToEnabled;
        }

        public UserRecord copyWithIsCurrent(boolean _isCurrent) {
            return new UserRecord(info, picture, isGuest, _isCurrent, isAddUser, isRestricted,
                    isSwitchToEnabled);
        }

        public int resolveId() {
            if (isGuest || info == null) {
                return UserHandle.USER_NULL;
            }
            return info.id;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("UserRecord(");
            if (info != null) {
                sb.append("name=\"").append(info.name).append("\" id=").append(info.id);
            } else {
                if (isGuest) {
                    sb.append("<add guest placeholder>");
                } else if (isAddUser) {
                    sb.append("<add user placeholder>");
                }
            }
            if (isGuest) sb.append(" <isGuest>");
            if (isAddUser) sb.append(" <isAddUser>");
            if (isCurrent) sb.append(" <isCurrent>");
            if (picture != null) sb.append(" <hasPicture>");
            if (isRestricted) sb.append(" <isRestricted>");
            if (isDisabledByAdmin) {
                sb.append(" <isDisabledByAdmin>");
                sb.append(" enforcedAdmin=").append(enforcedAdmin);
            }
            if (isSwitchToEnabled) {
                sb.append(" <isSwitchToEnabled>");
            }
            sb.append(')');
            return sb.toString();
        }
    }

    public final QSTile.DetailAdapter userDetailAdapter = new QSTile.DetailAdapter() {
        private final Intent USER_SETTINGS_INTENT = new Intent(Settings.ACTION_USER_SETTINGS);

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_user_title);
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            UserDetailView v;
            if (!(convertView instanceof UserDetailView)) {
                v = UserDetailView.inflate(context, parent, false);
                v.createAndSetAdapter(UserSwitcherController.this);
            } else {
                v = (UserDetailView) convertView;
            }
            v.refreshAdapter();
            return v;
        }

        @Override
        public Intent getSettingsIntent() {
            return USER_SETTINGS_INTENT;
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public void setToggleState(boolean state) {
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QS_USERDETAIL;
        }
    };

    private final KeyguardMonitor.Callback mCallback = new KeyguardMonitor.Callback() {
        @Override
        public void onKeyguardChanged() {
            notifyAdapters();
        }
    };

    private final class ExitGuestDialog extends SystemUIDialog implements
            DialogInterface.OnClickListener {

        private final int mGuestId;

        public ExitGuestDialog(Context context, int guestId) {
            super(context);
            setTitle(R.string.guest_exit_guest_dialog_title);
            setMessage(context.getString(R.string.guest_exit_guest_dialog_message));
            setButton(DialogInterface.BUTTON_NEGATIVE,
                    context.getString(android.R.string.cancel), this);
            setButton(DialogInterface.BUTTON_POSITIVE,
                    context.getString(R.string.guest_exit_guest_dialog_remove), this);
            setCanceledOnTouchOutside(false);
            mGuestId = guestId;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == BUTTON_NEGATIVE) {
                cancel();
            } else {
                dismiss();
                exitGuest(mGuestId);
            }
        }
    }

    private final class AddUserDialog extends SystemUIDialog implements
            DialogInterface.OnClickListener {

        public AddUserDialog(Context context) {
            super(context);
            setTitle(R.string.user_add_user_title);
            setMessage(context.getString(R.string.user_add_user_message_short));
            setButton(DialogInterface.BUTTON_NEGATIVE,
                    context.getString(android.R.string.cancel), this);
            setButton(DialogInterface.BUTTON_POSITIVE,
                    context.getString(android.R.string.ok), this);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == BUTTON_NEGATIVE) {
                cancel();
            } else {
                dismiss();
                if (ActivityManager.isUserAMonkey()) {
                    return;
                }
                UserInfo user = mUserManager.createUser(
                        mContext.getString(R.string.user_new_user_name), 0 /* flags */);
                if (user == null) {
                    // Couldn't create user, most likely because there are too many, but we haven't
                    // been able to reload the list yet.
                    return;
                }
                int id = user.id;
                Bitmap icon = UserIcons.convertToBitmap(UserIcons.getDefaultUserIcon(
                        id, /* light= */ false));
                mUserManager.setUserIcon(id, icon);
                switchToUserId(id);
            }
        }
    }
}
