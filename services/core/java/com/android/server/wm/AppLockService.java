/**
 * Copyright (C) 2017-2020 Paranoid Android
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

import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_APPLOCK;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.POSTFIX_APPLOCK;

import android.app.admin.DevicePolicyManager;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IAppLockService;
import android.app.IAppLockCallback;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.hardware.biometrics.BiometricConstants;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricPrompt.AuthenticationResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.os.BackgroundThread;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.R;
import com.android.server.DisplayThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public class AppLockService extends SystemService {

    private static final String TAG = "AppLockService";
    private static final String TAG_APPLOCK = TAG + POSTFIX_APPLOCK;

    private static final String FILE_NAME = "locked-apps.xml";
    private static final String TAG_LOCKED_APPS = "locked-apps";
    private static final String TAG_PACKAGE = "package";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_OP_MODE = "opMode";

    private final int APPLOCK_TIMEOUT = 15000;

    private AtomicBoolean mEnabled;
    private AppLockContainer mCurrent;
    private PackageManager mPackageManager;
    private AppOpsManager mAppOpsManager;
    private CancellationSignal mCancellationSignal;
    private BiometricPrompt mBiometricPrompt;

    private UserHandle mUserHandle;
    private int mUserId;
    private UserManager mUserManager;
    private boolean mShowOnlyOnWake;
    private boolean mIsSecure;
    private boolean mStartingFromRecents;
    private boolean mKeyguardShown;
    private boolean mLaunchAfterKeyguard;
    private boolean mBiometricRunning;
    private String mForegroundApp;
    private SettingsObserver mSettingsObserver;
    
    private final LockPatternUtils mLockPatternUtils;
    private Context mContext;

    private AtomicFile mFile;
    private final AppLockHandler mHandler;
    private final Object mLock = new Object();

    private final ArrayMap<String, AppLockContainer> mAppsList = new ArrayMap<>();
    private final ArraySet<String> mOpenedApplicationsIndex = new ArraySet<>();
    private final ArraySet<IAppLockCallback> mCallbacks= new ArraySet<>();

    private final BiometricPrompt.AuthenticationCallback mBiometricCallback =
            new BiometricPrompt.AuthenticationCallback() {
        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            if (DEBUG_APPLOCK && mCurrent != null) Slog.v(TAG, "onAuthenticationError() pkg:" + mCurrent.packageName
                    + " Id=" + errMsgId + " Name=" + errString);
            if (errMsgId == BiometricConstants.BIOMETRIC_ERROR_USER_CANCELED && mStartingFromRecents) {
                fallbackToHomeActivity();
            }
            mStartingFromRecents = false;
            mBiometricRunning = false;
            mCurrent = null;
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            if (DEBUG_APPLOCK) Slog.v(TAG, "onAuthenticationHelp");
            if (DEBUG_APPLOCK) Slog.v(TAG, "Help: Id=" + helpMsgId + " Name=" + helpString);
        }

        @Override
        public void onAuthenticationFailed() {
            if (DEBUG_APPLOCK) Slog.v(TAG, "onAuthenticationFailed");
            mStartingFromRecents = false;
            mBiometricRunning = false;
        }

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            if (DEBUG_APPLOCK) Slog.v(TAG, "onAuthenticationSucceeded result=" + result);
            if (mCurrent != null) {
                mCurrent.onUnlockSucceed();
            }
            mStartingFromRecents = false;
            mBiometricRunning = false;
            mCurrent = null;
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Package removed intent received");
                final Uri data = intent.getData();
                if (data == null) {
                    if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK,
                            "Cannot handle package broadcast with null data");
                    return;
                }

                final String packageName = data.getSchemeSpecificPart();
                removeAppFromList(packageName);
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "ACTION_SCREEN_OFF");
                clearOpenedAppsList();
                stopBiometricPrompt();
                fallbackToHomeActivity();
            }
        }
    };

    public AppLockService(Context context) {
        super(context);

        mContext = context;
        mHandler = new AppLockHandler(BackgroundThread.getHandler().getLooper());
        mUserId = ActivityManager.getCurrentUser();
        mUserManager = UserManager.get(context);
        mEnabled = new AtomicBoolean(!mUserManager.isManagedProfile(mUserId)
                && mUserManager.isUserUnlockingOrUnlocked(mUserId));
        mLockPatternUtils = new LockPatternUtils(context);

        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        context.registerReceiver(mReceiver, packageFilter);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mReceiver, intentFilter);

        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();

        mHandler.sendEmptyMessage(AppLockHandler.MSG_READ_STATE);
    }

    @Override
    public void onStart() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Starting AppLockService");
        publishBinderService(Context.APPLOCK_SERVICE, new AppLockImpl());
        publishLocalService(AppLockService.class, this);
    }

    @Override
    public void onUnlockUser(int userHandle) {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "onUnlockUser()");
        mUserId = userHandle;
        mPackageManager = mContext.getPackageManager();
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mHandler.sendEmptyMessage(AppLockHandler.MSG_INIT_APPS);
    }

    @Override
    public void onSwitchUser(int userHandle) {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "onSwitchUser()");
        mUserId = userHandle;
        mHandler.sendEmptyMessage(AppLockHandler.MSG_INIT_APPS);
    }

    @Override
    public void onStopUser(int userHandle) {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "onStopUser()");
        mEnabled.set(false);
    }

    private void initLockedApps() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "initLockedApps(" + mUserId + ")");
        mUserHandle = new UserHandle(mUserId);
        if (mUserManager.isManagedProfile(mUserId)) {
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Disabled");
            mEnabled.set(false);
        } else {
            mFile = new AtomicFile(getFile());
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Enabled");
            mEnabled.set(true);
            readState();
            clearOpenedAppsList();
        }
        mIsSecure = isSecure();
    }

    private File getFile() {
        File file = new File(Environment.getDataSystemCeDirectory(mUserId), FILE_NAME);
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "getFile(): " + file.getAbsolutePath());
        return file;
    }

    private void readState() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "readState()");
        if (!mEnabled.get()) {
            return;
        }
        try (FileInputStream in = mFile.openRead()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            parseXml(parser);
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Read locked-apps.xml successfully");
        } catch (FileNotFoundException e) {
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "locked-apps.xml not found");
            Slog.i(TAG, "locked-apps.xml not found");
        } catch (XmlPullParserException | IOException e) {
            throw new IllegalStateException("Failed to parse locked-apps.xml: " + mFile, e);
        }
    }

    private void parseXml(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (depth > innerDepth || type != XmlPullParser.START_TAG) {
                continue;
            }
            if (parser.getName().equals(TAG_LOCKED_APPS)) {
                parsePackages(parser);
                return;
            }
        }
        Slog.w(TAG, "Missing <" + TAG_LOCKED_APPS + "> in locked-apps.xml");
    }

    private void parsePackages(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        mAppsList.clear();
        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        boolean writeAfter = false;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (depth > innerDepth || type != XmlPullParser.START_TAG) {
                continue;
            }
            if (parser.getName().equals(TAG_PACKAGE)) {
                String pkgName = parser.getAttributeValue(null, ATTRIBUTE_NAME);
                String appOpMode = parser.getAttributeValue(null, ATTRIBUTE_OP_MODE);
                AppLockContainer cont = new AppLockContainer(pkgName, (appOpMode == null)
                        ? -1 : Integer.parseInt(appOpMode));
                writeAfter = (appOpMode == null) || (Integer.parseInt(appOpMode) == -1);
                mAppsList.put(pkgName, cont);
                if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "parsePackages(): pkgName=" + pkgName
                        + " appOpMode=" + appOpMode);
            }
        }
        if (writeAfter) {
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "parsePackages(): writeAfter");
            mHandler.sendEmptyMessage(AppLockHandler.MSG_WRITE_STATE);
        }
    }

    private void writeState() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "writeState()");
        if (!mEnabled.get()) {
            return;
        }

        FileOutputStream out = null;
        try {
            out = mFile.startWrite();
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, StandardCharsets.UTF_8.name());
            serializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument(null, true);
            serializeLockedApps(serializer);
            serializer.endDocument();
            mFile.finishWrite(out);
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Wrote locked-apps.xml successfully");
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            Slog.wtf(TAG, "Failed to write locked-apps.xml, restoring backup", e);
            if (out != null) {
                mFile.failWrite(out);
            }
        } finally {
            IoUtils.closeQuietly(out);
        }
    }

    private void serializeLockedApps(XmlSerializer serializer) throws IOException {
        serializer.startTag(null, TAG_LOCKED_APPS);
        for (int i = 0; i < mAppsList.size(); ++i) {
            AppLockContainer cont = mAppsList.valueAt(i);
            serializer.startTag(null, TAG_PACKAGE);
            serializer.attribute(null, ATTRIBUTE_NAME, cont.packageName);
            serializer.attribute(null, ATTRIBUTE_OP_MODE, String.valueOf(cont.appOpMode));
            serializer.endTag(null, TAG_PACKAGE);
        }
        serializer.endTag(null, TAG_LOCKED_APPS);
    }

    private void addAppToList(String packageName) {
        if (!mEnabled.get()) {
            return;
        }
        if (DEBUG_APPLOCK) Slog.v(TAG, "addAppToList packageName:" + packageName);
        if (!mAppsList.containsKey(packageName)) {
            AppLockContainer cont = new AppLockContainer(packageName, -1);
            mAppsList.put(packageName, cont);
            mHandler.sendEmptyMessage(AppLockHandler.MSG_WRITE_STATE);
            dispatchCallbacks(packageName, false);
        }
    }

    private void removeAppFromList(String packageName) {
        if (!mEnabled.get()) {
            return;
        }
        if (mAppsList.containsKey(packageName)) {
            AppLockContainer cont = getAppLockContainer(packageName);
            cont.appRemovedFromList();
            mAppsList.remove(packageName);
            mHandler.sendEmptyMessage(AppLockHandler.MSG_WRITE_STATE);
            dispatchCallbacks(packageName, true);
        }
    }

    public void reportPasswordChanged(int userId) {
        if (mUserId == userId) {
            mIsSecure = isSecure();
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "reportPasswordChanged() mIsSecure:" + mIsSecure);
        }
    }

    public boolean isAppLocked(String packageName) {
        if (!mEnabled.get() || !mIsSecure) {
            return false;
        }
        return mAppsList.containsKey(packageName);
    }

    private AppLockContainer getAppLockContainer(String packageName) {
        if (!mEnabled.get()) {
            return null;
        }
        return mAppsList.get(packageName);
    }

    private void clearOpenedAppsList() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "clearOpenedAppsList()");
        for (String p : mOpenedApplicationsIndex) {
            dispatchCallbacks(p, false);
        }
        mOpenedApplicationsIndex.clear();
    }

    public boolean isAppOpen(String packageName) {
        return mOpenedApplicationsIndex.contains(packageName);
    }

    private List<String> getLockedPackages() {
        return new ArrayList<String>(mAppsList.keySet());
    }

    public boolean isAlarmOrCallIntent(Intent intent) {
        if (intent == null) return false;
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "isAlarmOrCallIntent() intent:" + intent);

        String intentClassName = intent.getComponent().getClassName().toLowerCase();
        return (intentClassName.contains("call") || intentClassName.contains("voip")
                || intentClassName.contains("alarm"))
                && intentClassName.contains("activity");
    }

    void removeOpenedApp(String packageName) {
        if (isAppOpen(packageName)) {
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "removeOpenedApp(" + packageName + ")");
            mOpenedApplicationsIndex.remove(packageName);
            dispatchCallbacks(packageName, false);
        }
    }

    void addOpenedApp(String packageName) {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "addOpenedApp(" + packageName + ")");
        mOpenedApplicationsIndex.add(packageName);
    }

    public void launchBeforeActivity(String packageName) {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "launchBeforeActivity(" + packageName + ")");
        AppLockContainer cont = getAppLockContainer(packageName);
        if (cont != null) {
            DisplayThread.getHandler().post(() -> {
                mCurrent = cont;
                if (mKeyguardShown) {
                    mLaunchAfterKeyguard = true;
                    return;
                }
                mBiometricPrompt = new BiometricPrompt.Builder(mContext)
                    .setTitle(cont.appLabel)
                    .setApplockPackage(cont.packageName)
                    .setDescription(cont.appLabel)
                    .setDeviceCredentialAllowed(true)
                    .setConfirmationRequired(false)
                    .build();
                startBiometricPrompt();
            });
        }
    }

    public void activityStopped(String packageName, Intent removed) {
        AppLockContainer cont = getAppLockContainer(packageName);
        if (cont != null) {
            if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "activityStopped() pkg:" + packageName);
            if (isAppOpen(packageName)) {
                mHandler.removeMessages(AppLockHandler.MSG_REMOVE_OPENED_APP, cont.packageName);
                if (cont.intent.equals(removed)) {
                    if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "activityStopped() send: MSG_REMOVE_OPENED_APP");
                    final Message msgRemove = mHandler.obtainMessage(AppLockHandler.MSG_REMOVE_OPENED_APP,
                            cont.packageName);
                    mHandler.sendMessageDelayed(msgRemove, APPLOCK_TIMEOUT);
                }
            }
        }
    }

    public void setAppIntent(String packageName, Intent intent) {
        if (DEBUG_APPLOCK) Slog.v(TAG, "setAppIntent(" + packageName + ") intent:" + intent);
        AppLockContainer cont = getAppLockContainer(packageName);
        if (cont != null) {
            cont.intent = intent;
            mHandler.removeMessages(AppLockHandler.MSG_REMOVE_OPENED_APP, cont.packageName);
        }
    }

    public void setStartingFromRecents() {
        if (DEBUG_APPLOCK) Slog.v(TAG, "setStartingFromRecents()");
        mStartingFromRecents = true;
    }

    public void setForegroundApp(String packageName) {
        if (DEBUG_APPLOCK) Slog.v(TAG, "setForegroundApp(" + packageName + ")");
        mForegroundApp = packageName;
    }

    private void startBiometricPrompt() {
        if (DEBUG_APPLOCK) Slog.v(TAG, "startBiometricPrompt()");
        if (mBiometricRunning) return;
        if (mCancellationSignal == null || mCancellationSignal.isCanceled()) {
            mCancellationSignal = new CancellationSignal();
        }
        mBiometricPrompt.authenticate(mCancellationSignal, mContext.getMainExecutor(), mBiometricCallback);
        mBiometricRunning = true;
    }

    private void stopBiometricPrompt() {
        if (DEBUG_APPLOCK) Slog.v(TAG, "stopBiometricPrompt()");
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
        }
        mBiometricRunning = false;
    }

    public void setKeyguardShown(boolean shown) {
        mKeyguardShown = shown;
        if (mLaunchAfterKeyguard && !shown) {
            mLaunchAfterKeyguard = false;
            mHandler.postDelayed(() -> {
                if (mCurrent != null) {
                    launchBeforeActivity(mCurrent.packageName);
                }
            }, 150);
        }
    }

    private boolean isSecure() {
        int storedQuality = mLockPatternUtils.getKeyguardStoredPasswordQuality(mUserId);
        switch (storedQuality) {
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_MANAGED:
                return true;
            default:
                return false;
        }
    }

    private void fallbackToHomeActivity() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "fallbackToHomeActivity()");
        if (mAppsList.containsKey(mForegroundApp) || mStartingFromRecents) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(intent, mUserHandle);
        }
    }

    private int getLockedAppsCount() {
        if (DEBUG_APPLOCK) Slog.v(TAG_APPLOCK, "Number of locked apps: " + mAppsList.size());
        return mIsSecure ? mAppsList.size() : 0;
    }

    private void dispatchCallbacks(String packageName, boolean opened) {
        mHandler.post(() -> {
            synchronized (mCallbacks) {
                final int N = mCallbacks.size();
                boolean cleanup = false;
                for (int i = 0; i < N; i++) {
                    final IAppLockCallback callback = mCallbacks.valueAt(i);
                    try {
                        if (callback != null) {
                            callback.onAppStateChanged(packageName, opened);
                        } else {
                            cleanup = true;
                        }
                    } catch (RemoteException e) {
                        cleanup = true;
                    }
                }
                if (cleanup) {
                    cleanUpCallbacksLocked(null);
                }
            }
        });
    }

    private void cleanUpCallbacksLocked(IAppLockCallback callback) {
        mHandler.post(() -> {
            synchronized (mCallbacks) {
                for (int i = mCallbacks.size() - 1; i >= 0; i--) {
                    IAppLockCallback found = mCallbacks.valueAt(i);
                    if (found == null || found == callback) {
                        mCallbacks.remove(i);
                    }
                }
            }
        });
    }

    private void addAppLockCallback(IAppLockCallback callback) {
        mHandler.post(() -> {
            synchronized(mCallbacks) {
                if (!mCallbacks.contains(callback)) {
                    mCallbacks.add(callback);
                }
            }
        });
    }

    private void removeAppLockCallback(IAppLockCallback callback) {
        mHandler.post(() -> {
            synchronized(mCallbacks) {
                if (mCallbacks.contains(callback)) {
                    mCallbacks.remove(callback);
                }
            }
        });
    }

    private class SettingsObserver extends ContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_LOCK_SHOW_ONLY_ON_WAKE), false, this,
                    UserHandle.USER_ALL);
            mShowOnlyOnWake = Settings.System.getIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.APP_LOCK_SHOW_ONLY_ON_WAKE, 0,
                    mUserId) != 0;
        }

        @Override
        public void onChange(boolean selfChange) {
            mShowOnlyOnWake = Settings.System.getIntForUser(mContext
                    .getContentResolver(),
                    Settings.System.APP_LOCK_SHOW_ONLY_ON_WAKE, 0,
                    mUserId) != 0;
            clearOpenedAppsList();
        }
    }

    private class AppLockImpl extends IAppLockService.Stub {
        @Override
        public void addAppToList(String packageName) {
            AppLockService.this.addAppToList(packageName);
        }

        @Override
        public void removeAppFromList(String packageName) {
            AppLockService.this.removeAppFromList(packageName);
        }

        @Override
        public boolean isAppLocked(String packageName) {
            return AppLockService.this.isAppLocked(packageName);
        }

        @Override
        public boolean isAppOpen(String packageName) {
            return AppLockService.this.isAppOpen(packageName);
        }

        @Override
        public int getLockedAppsCount() {
            return AppLockService.this.getLockedAppsCount();
        }

        @Override
        public List<String> getLockedPackages() {
            return AppLockService.this.getLockedPackages();
        }

        @Override
        public void addAppLockCallback(IAppLockCallback callback) {
            AppLockService.this.addAppLockCallback(callback);
        }

        @Override
        public void removeAppLockCallback(IAppLockCallback callback) {
            AppLockService.this.removeAppLockCallback(callback);
        }
    };

    private class AppLockHandler extends Handler {

        public static final int MSG_INIT_APPS = 0;
        public static final int MSG_READ_STATE = 1;
        public static final int MSG_WRITE_STATE = 2;
        public static final int MSG_REMOVE_OPENED_APP = 3;

        public AppLockHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_INIT_APPS:
                    initLockedApps();
                    break;
                case MSG_READ_STATE:
                    readState();
                    break;
                case MSG_WRITE_STATE:
                    writeState();
                    break;
                case MSG_REMOVE_OPENED_APP:
                    if (!mShowOnlyOnWake) {
                        removeOpenedApp((String) msg.obj);
                    }
                    break;
                default:
                    Slog.w(TAG, "Unknown message:" + msg.what);
            }
        }
    }

    private class AppLockContainer {
        private final String packageName;
        private ApplicationInfo aInfo;
        private CharSequence appLabel;
        private int appOpMode = -1;
        private Intent intent;

        public AppLockContainer(String pkg, int opMode) {
            packageName = pkg;
            try {
                aInfo = mPackageManager.getApplicationInfo(packageName, 0);
            } catch(PackageManager.NameNotFoundException e) {
                Slog.e(TAG, "Failed to find package " + packageName, e);
                removeAppFromList(packageName);
                return;
            }
            appLabel = mPackageManager.getApplicationLabel(aInfo);

            if (opMode == -1) {
                appOpMode = mAppOpsManager.checkOpNoThrow(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                    aInfo.uid, packageName);
            } else {
                appOpMode = opMode;
            }

            if (appOpMode == AppOpsManager.MODE_ALLOWED
                    || appOpMode == AppOpsManager.MODE_DEFAULT) {
                mAppOpsManager.setMode(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                        aInfo.uid, packageName, AppOpsManager.MODE_ERRORED);
            }
        }

        private void startActivityAfterUnlock() {
            if (DEBUG_APPLOCK) Slog.v(TAG, "startActivityAfterUnlock() intent:" + intent);
            Intent fallBackIntent = new Intent(Intent.ACTION_MAIN);
            fallBackIntent.setPackage(packageName);
            List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(fallBackIntent, 0);
            if(resolveInfos.size() > 0) {
                ResolveInfo launchable = resolveInfos.get(0);
                ActivityInfo activity = launchable.activityInfo;
                ComponentName name = new ComponentName(activity.applicationInfo.packageName,
                        activity.name);
                fallBackIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                fallBackIntent.setComponent(name);
            }
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, mUserHandle);
            } else if (fallBackIntent != null) {
                mContext.startActivityAsUser(fallBackIntent, mUserHandle);
            }
            if (fallBackIntent != null) {
                intent = fallBackIntent;
            }
        }

        private void onUnlockSucceed() {
            addOpenedApp(packageName);
            startActivityAfterUnlock();
            dispatchCallbacks(packageName, true);
        }

        private void appRemovedFromList() {
            Slog.d(TAG, "appRemovedFromList() appOpMode: " + appOpMode);
            mAppOpsManager.setMode(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                    aInfo.uid, packageName, appOpMode);
        }
    }
}
