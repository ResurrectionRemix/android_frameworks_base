/*
 * Copyright (C) 2018 Projekt Development
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

package com.android.server.substratum;

import android.annotation.NonNull;
import android.app.AppGlobals;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageInstallObserver2;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.ServiceConnection;
import android.content.substratum.ISubstratumService;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.substratum.ISubstratumHelperService;
import com.android.server.SystemService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.Throwable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SubstratumService extends SystemService {

    private static final String TAG = "SubstratumService";
    private static final String SUBSTRATUM_PACKAGE = "projekt.substratum";
    private static final boolean DEBUG = false;

    private static final Signature SUBSTRATUM_SIGNATURE = new Signature(""
            + "308202eb308201d3a003020102020411c02f2f300d06092a864886f70d01010b050030263124302206"
            + "03550403131b5375627374726174756d20446576656c6f706d656e74205465616d301e170d31363037"
            + "30333032333335385a170d3431303632373032333335385a3026312430220603550403131b53756273"
            + "74726174756d20446576656c6f706d656e74205465616d30820122300d06092a864886f70d01010105"
            + "000382010f003082010a02820101008855626336f645a335aa5d40938f15db911556385f72f72b5f8b"
            + "ad01339aaf82ae2d30302d3f2bba26126e8da8e76a834e9da200cdf66d1d5977c90a4e4172ce455704"
            + "a22bbe4a01b08478673b37d23c34c8ade3ec040a704da8570d0a17fce3c7397ea63ebcde3a2a3c7c5f"
            + "983a163e4cd5a1fc80c735808d014df54120e2e5708874739e22e5a22d50e1c454b2ae310b480825ab"
            + "3d877f675d6ac1293222602a53080f94e4a7f0692b627905f69d4f0bb1dfd647e281cc0695e0733fa3"
            + "efc57d88706d4426c4969aff7a177ac2d9634401913bb20a93b6efe60e790e06dad3493776c2c0878c"
            + "e82caababa183b494120edde3d823333efd464c8aea1f51f330203010001a321301f301d0603551d0e"
            + "04160414203ec8b075d1c9eb9d600100281c3924a831a46c300d06092a864886f70d01010b05000382"
            + "01010042d4bd26d535ce2bf0375446615ef5bf25973f61ecf955bdb543e4b6e6b5d026fdcab09fec09"
            + "c747fb26633c221df8e3d3d0fe39ce30ca0a31547e9ec693a0f2d83e26d231386ff45f8e4fd5c06095"
            + "8681f9d3bd6db5e940b1e4a0b424f5c463c79c5748a14a3a38da4dd7a5499dcc14a70ba82a50be5fe0"
            + "82890c89a27e56067d2eae952e0bcba4d6beb5359520845f1fdb7df99868786055555187ba46c69ee6"
            + "7fa2d2c79e74a364a8b3544997dc29cc625395e2f45bf8bdb2c9d8df0d5af1a59a58ad08b32cdbec38"
            + "19fa49201bb5b5aadeee8f2f096ac029055713b77054e8af07cd61fe97f7365d0aa92d570be98acb89"
            + "41b8a2b0053b54f18bfde092eb");

    private static final Signature SUBSTRATUM_CI_SIGNATURE = new Signature(""
            + "308201dd30820146020101300d06092a864886f70d010105050030373116301406035504030c0d416e"
            + "64726f69642044656275673110300e060355040a0c07416e64726f6964310b30090603550406130255"
            + "53301e170d3137303232333036303730325a170d3437303231363036303730325a3037311630140603"
            + "5504030c0d416e64726f69642044656275673110300e060355040a0c07416e64726f6964310b300906"
            + "035504061302555330819f300d06092a864886f70d010101050003818d00308189028181008aa6cf56"
            + "e3ba4d0921da3baf527529205efbe440e1f351c40603afa5e6966e6a6ef2def780c8be80d189dc6101"
            + "935e6f8340e61dc699cfd34d50e37d69bf66fbb58619d0ebf66f22db5dbe240b6087719aa3ceb1c68f"
            + "3fa277b8846f1326763634687cc286b0760e51d1b791689fa2d948ae5f31cb8e807e00bd1eb72788b2"
            + "330203010001300d06092a864886f70d0101050500038181007b2b7e432bff612367fbb6fdf8ed0ad1"
            + "a19b969e4c4ddd8837d71ae2ec0c35f52fe7c8129ccdcdc41325f0bcbc90c38a0ad6fc0c604a737209"
            + "17d37421955c47f9104ea56ad05031b90c748b94831969a266fa7c55bc083e20899a13089402be49a5"
            + "edc769811adc2b0496a8a066924af9eeb33f8d57d625a5fa150f7bc18e55");

    private static final File SYSTEM_THEME_DIR =
            new File(Environment.getDataSystemDirectory(), "theme");
    private static final File SYSTEM_THEME_CACHE_DIR = new File(SYSTEM_THEME_DIR, "cache");
    private static final File SYSTEM_THEME_FONT_DIR = new File(SYSTEM_THEME_DIR, "fonts");
    private static final File SYSTEM_THEME_AUDIO_DIR = new File(SYSTEM_THEME_DIR, "audio");
    private static final File SYSTEM_THEME_RINGTONE_DIR =
            new File(SYSTEM_THEME_AUDIO_DIR, "ringtones");
    private static final File SYSTEM_THEME_NOTIFICATION_DIR =
            new File(SYSTEM_THEME_AUDIO_DIR, "notifications");
    private static final File SYSTEM_THEME_ALARM_DIR = new File(SYSTEM_THEME_AUDIO_DIR, "alarms");
    private static final File SYSTEM_THEME_UI_SOUNDS_DIR = new File(SYSTEM_THEME_AUDIO_DIR, "ui");
    private static final File SYSTEM_THEME_BOOTANIMATION_DIR =
            new File(SYSTEM_THEME_DIR, "bootanimation.zip");
    private static final File SYSTEM_THEME_SHUTDOWNANIMATION_DIR =
            new File(SYSTEM_THEME_DIR, "shutdownanimation.zip");

    private static final Signature[] AUTHORIZED_SIGNATURES = new Signature[]{
            SUBSTRATUM_SIGNATURE,
            SUBSTRATUM_CI_SIGNATURE,
    };

    private static final List<Sound> SOUNDS = Arrays.asList(
        new Sound(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath(), "/SoundsCache/ui/", "Effect_Tick",
                "Effect_Tick", RingtoneManager.TYPE_RINGTONE),
        new Sound(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath(), "/SoundsCache/ui/", "lock_sound",
                "Lock"),
        new Sound(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath(), "/SoundsCache/ui/", "unlock_sound",
                "Unlock"),
        new Sound(SYSTEM_THEME_ALARM_DIR.getAbsolutePath(), "/SoundsCache/alarms/", "alarm",
                "alarm", RingtoneManager.TYPE_ALARM),
        new Sound(SYSTEM_THEME_NOTIFICATION_DIR.getAbsolutePath(), "/SoundsCache/notifications/",
                "notification", "notification", RingtoneManager.TYPE_NOTIFICATION),
        new Sound(SYSTEM_THEME_RINGTONE_DIR.getAbsolutePath(), "/SoundsCache/ringtones/",
                "ringtone", "ringtone", RingtoneManager.TYPE_RINGTONE)
    );

    private IOverlayManager mOm;
    private IPackageManager mPm;
    private boolean mIsWaiting;
    private String mInstalledPackageName;

    private Context mContext;
    private final Object mLock = new Object();
    private boolean mSigOverride = false;
    private SettingsObserver mObserver = new SettingsObserver();

    private ISubstratumHelperService mHelperService;
    private final ServiceConnection mHelperConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mHelperService = ISubstratumHelperService.Stub.asInterface(service);
            log("Helper service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mHelperService = null;
            log("Helper service disconnected");
        }
    };

    public SubstratumService(@NonNull final Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            if (makeDir(SYSTEM_THEME_DIR)) {
                restoreconThemeDir();
            }

            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FORCE_AUTHORIZE_SUBSTRATUM_PACKAGES),
                    false, mObserver);
            updateSettings();

            mOm = IOverlayManager.Stub.asInterface(ServiceManager.getService("overlay"));
            mPm = AppGlobals.getPackageManager();
            publishBinderService("substratum", mService);

            log("published substratum service");
        }
    }

    @Override
    public void onStart() {
        // Intentionally left empty
    }

    @Override
    public void onSwitchUser(final int newUserId) {
        // Intentionally left empty
    }

    private void waitForHelperConnection() {
        if (mHelperService == null) {
            Intent intent = new Intent("android.substratum.service.SubstratumHelperService");
            intent.setPackage("android.substratum.service");
            mContext.bindServiceAsUser(intent, mHelperConnection,
                    Context.BIND_AUTO_CREATE, UserHandle.SYSTEM);
        }
    }

    private boolean doSignaturesMatch(String packageName, Signature signature) {
        if (packageName != null) {
              try {
                  PackageInfo pi = mPm.getPackageInfo(packageName,
                        PackageManager.GET_SIGNATURES, UserHandle.USER_SYSTEM);
                  if (pi.signatures != null
                           && pi.signatures.length == 1
                           && signature.equals(pi.signatures[0])) {
                        return true;
                  }
              } catch (RemoteException ignored) {
                  return false;
              }
       }
       return false;
    }

    private void checkCallerAuthorization(int uid) {
        String callingPackage;
        try {
            callingPackage = mPm.getPackagesForUid(uid)[0];
        } catch (RemoteException ignored) {
            throw new SecurityException("Cannot check caller authorization");
        }

        if (TextUtils.equals(callingPackage, SUBSTRATUM_PACKAGE)) {
            for (Signature sig : AUTHORIZED_SIGNATURES) {
                if (doSignaturesMatch(callingPackage, sig)) {
                    return;
                }
            }
        }

       if (mSigOverride) {
            log("\'" + callingPackage + "\' is not an authorized calling package, but the user " +
                    "has explicitly allowed all calling packages, " +
                    "validating calling package permissions...");
            return;
        }

        throw new SecurityException("Caller is not authorized");
    }

    private final IBinder mService = new ISubstratumService.Stub() {
        @Override
        public void installOverlay(List<String> paths) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            final int packageVerifierEnable = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.PACKAGE_VERIFIER_ENABLE, 1);
            try {
                synchronized (mLock) {
                    PackageInstallObserver installObserver = new PackageInstallObserver();
                    PackageDeleteObserver deleteObserver = new PackageDeleteObserver();
                    for (String path : paths) {
                        mInstalledPackageName = null;
                        log("Installer - installing package from path \'" + path + "\'");
                        mIsWaiting = true;
                        Settings.Global.putInt(mContext.getContentResolver(),
                                Settings.Global.PACKAGE_VERIFIER_ENABLE, 0);
                        try {
                            mPm.installPackageAsUser(
                                    path,
                                    installObserver,
                                    PackageManager.INSTALL_REPLACE_EXISTING,
                                    null,
                                    UserHandle.USER_SYSTEM);

                            while (mIsWaiting) {
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    // Someone interrupted my sleep, ugh!
                                }
                            }
                        } catch (RemoteException e) {
                            logE("There is an exception when trying to install " + path, e);
                            continue;
                        }

                        if (mInstalledPackageName != null) {
                            try {
                                PackageInfo pi = mPm.getPackageInfo(mInstalledPackageName,
                                        0, UserHandle.USER_SYSTEM);
                                if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0 ||
                                        pi.overlayTarget == null) {
                                    mIsWaiting = true;
                                    int versionCode = mPm.getPackageInfo(
                                            mInstalledPackageName, 0, UserHandle.USER_SYSTEM)
                                            .versionCode;
                                    mPm.deletePackageAsUser(
                                            mInstalledPackageName,
                                            versionCode,
                                            deleteObserver,
                                            0,
                                            UserHandle.USER_SYSTEM);

                                    while (mIsWaiting) {
                                        try {
                                            Thread.sleep(1);
                                        } catch (InterruptedException e) {
                                            // Someone interrupted my sleep, ugh!
                                        }
                                    }
                                }
                            } catch (RemoteException e) {
                                // Probably won't happen but we need to keep quiet here
                            }
                        }
                    }
                }
            } finally {
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.PACKAGE_VERIFIER_ENABLE, packageVerifierEnable);
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void uninstallOverlay(List<String> packages, boolean restartUi) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    PackageDeleteObserver observer = new PackageDeleteObserver();
                    for (String p : packages) {
                        if (isOverlayEnabled(p)) {
                            log("Remover - disabling overlay for \'" + p + "\'...");
                            switchOverlayState(p, false);
                        }

                        try {
                            PackageInfo pi = mPm.getPackageInfo(p, 0, UserHandle.USER_SYSTEM);
                            if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0 &&
                                    pi.overlayTarget != null) {
                                log("Remover - uninstalling \'" + p + "\'...");
                                mIsWaiting = true;
                                mPm.deletePackageAsUser(
                                        p,
                                        pi.versionCode,
                                        observer,
                                        0,
                                        UserHandle.USER_SYSTEM);

                                while (mIsWaiting) {
                                    try {
                                        Thread.sleep(1);
                                    } catch (InterruptedException e) {
                                        // Someone interrupted my sleep, ugh!
                                    }
                                }
                            }
                        } catch (RemoteException e) {
                            logE("There is an exception when trying to uninstall package", e);
                        }
                    }
                    if (restartUi) {
                        restartUi();
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void switchOverlay(List<String> packages, boolean enable, boolean restartUi) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    for (String p : packages) {
                        log(enable ? "Enabling" : "Disabling" + " overlay " + p);
                        switchOverlayState(p, enable);
                    }
                    if (restartUi) {
                        restartUi();
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void setPriority(List<String> packages, boolean restartUi) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    log("PriorityJob - processing priority changes...");
                    for (int i = 0; i < packages.size() - 1; i++) {
                        String parentName = packages.get(i);
                        String packageName = packages.get(i + 1);

                        mOm.setPriority(packageName, parentName,
                                UserHandle.USER_SYSTEM);
                    }
                    if (restartUi) {
                        restartUi();
                    }
                }
            } catch (RemoteException e) {
                logE("There is an exception when trying to adjust overlay priority", e);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void restartSystemUI() {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                log("Restarting SystemUI...");
                restartUi();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void copy(String source, String destination) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                log("CopyJob - copying \'" + source + "\' to \'" + destination + "\'...");
                File sourceFile = new File(source);
                if (sourceFile.exists()) {
                    if (sourceFile.isFile()) {
                        FileUtils.copyFile(sourceFile, new File(destination));
                    } else {
                        copyDir(source, destination);
                    }
                } else {
                    logE("CopyJob - \'" + source + "\' does not exist, aborting...");
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void move(String source, String destination) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                log("MoveJob - moving \'" + source + "\' to \'" + destination + "\'...");
                File sourceFile = new File(source);
                if (sourceFile.exists()) {
                    if (sourceFile.isFile()) {
                        FileUtils.copyFile(sourceFile, new File(destination));
                    } else {
                        copyDir(source, destination);
                    }
                    FileUtils.deleteContentsAndDir(sourceFile);
                } else {
                    logE("MoveJob - \'" + source + "\' does not exist, aborting...");
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void mkdir(String destination) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                log("MkdirJob - creating \'" + destination + "\'...");
                makeDir(new File(destination));
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void deleteDirectory(String directory, boolean withParent) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                if (withParent) {
                    FileUtils.deleteContentsAndDir(new File(directory));
                } else {
                    FileUtils.deleteContents(new File(directory));
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyBootanimation(String name) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                if (name == null) {
                    log("Restoring system boot animation...");
                    clearBootAnimation();
                } else {
                    log("Configuring themed boot animation...");
                    copyBootAnimation(name);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyFonts(String pid, String fileName) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                if (pid == null) {
                    log("Restoring system font...");
                    clearFonts();
                } else {
                    log("Configuring theme font...");
                    copyFonts(pid, fileName);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applySounds(String pid, String fileName) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                if (pid == null) {
                    log("Restoring system sounds...");
                    clearSounds();
                } else {
                    log("Configuring theme sounds...");
                    applyThemedSounds(pid, fileName);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyProfile(List<String> enable, List<String> disable, String name,
                boolean restartUi) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                log("ProfileJob - Applying profile: " + name);
                waitForHelperConnection();
                boolean mRestartUi = restartUi;

                boolean oldFontsExists = SYSTEM_THEME_FONT_DIR.exists();
                boolean oldSoundsExists = SYSTEM_THEME_AUDIO_DIR.exists();

                mHelperService.applyProfile(name);

                boolean newFontsExists = SYSTEM_THEME_FONT_DIR.exists();
                boolean newSoundsExists = SYSTEM_THEME_AUDIO_DIR.exists();

                if (oldFontsExists || newFontsExists) {
                    refreshFonts();
                }

                if (oldSoundsExists || newSoundsExists) {
                    refreshSounds();
                    mRestartUi = true;
                }

                for (String overlay : disable) {
                    switchOverlayState(overlay, false);
                }

                for (String overlay : enable) {
                    switchOverlayState(overlay, true);
                }

                if (mRestartUi) {
                    restartUi();
                }

                log("ProfileJob - " + name + " successfully applied.");
            } catch (RemoteException e) {
                logE("Failed to apply profile", e);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void applyShutdownAnimation(String name) {
            checkCallerAuthorization(Binder.getCallingUid());
            final long ident = Binder.clearCallingIdentity();
            try {
                if (name == null) {
                    log("Restoring system shutdown animation...");
                    clearShutdownAnimation();
                } else {
                    log("Configuring themed shutdown animation...");
                    copyShutdownAnimation(name);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public Map getAllOverlays(int uid) {
            checkCallerAuthorization(Binder.getCallingUid());
            try {
                return mOm.getAllOverlays(uid);
            } catch (RemoteException e) {
                logE("There is an exception when trying to get all overlays", e);
                return null;
            }
        }
    };

    private Context getAppContext(String packageName) {
        Context ctx = null;
        try {
            ctx = mContext.createPackageContext(packageName,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            logE("Failed to get " + packageName + " context");
        }
        return ctx;
    }

    private void switchOverlayState(String packageName, boolean enable) {
        try {
            mOm.setEnabled(packageName, enable, UserHandle.USER_SYSTEM);
        } catch (RemoteException e) {
            logE("There is an exception when trying to switch overlay state", e);
        }
    }

    private boolean isOverlayEnabled(String packageName) {
        boolean enabled = false;
        try {
            OverlayInfo info = mOm.getOverlayInfo(packageName, UserHandle.USER_SYSTEM);
            if (info != null) {
                enabled = info.isEnabled();
            } else {
                logE("Can't find OverlayInfo for " + packageName);
            }
        } catch (RemoteException e) {
            logE("There is an exception when trying to check overlay state", e);
        }
        return enabled;
    }

    private void restartUi() {
        Intent i = new Intent("com.android.systemui.action.RESTART_THEME");
        i.setPackage("com.android.systemui");
        mContext.sendBroadcastAsUser(i, UserHandle.SYSTEM);
    }

    private void copyBootAnimation(String fileName) {
        try {
            waitForHelperConnection();
            mHelperService.applyBootAnimation();
        } catch (RemoteException e) {
            logE("There is an exception when trying to apply boot animation", e);
        }
    }

    private void clearBootAnimation() {
        if (SYSTEM_THEME_BOOTANIMATION_DIR.exists()) {
            boolean deleted = SYSTEM_THEME_BOOTANIMATION_DIR.delete();
            if (!deleted) {
                logE("Could not delete themed boot animation");
            }
        }
    }

    private void copyShutdownAnimation(String fileName) {
        try {
            waitForHelperConnection();
            mHelperService.applyShutdownAnimation();
        } catch (RemoteException e) {
            logE("There is an exception when trying to apply shutdown animation", e);
        }
    }

    private void clearShutdownAnimation() {
        if (SYSTEM_THEME_SHUTDOWNANIMATION_DIR.exists()) {
            boolean deleted = SYSTEM_THEME_SHUTDOWNANIMATION_DIR.delete();
            if (!deleted) {
                logE("Could not delete themed shutdown animation");
            }
        }
    }

    private void copyFonts(String pid, String zipFileName) {
        // Prepare local cache dir for font package assembly
        log("Copy Fonts - Package ID = " + pid + " filename = " + zipFileName);

        File cacheDir = new File(SYSTEM_THEME_CACHE_DIR, "FontCache");
        if (cacheDir.exists()) {
            FileUtils.deleteContentsAndDir(cacheDir);
        }

        boolean created = cacheDir.mkdirs();
        if (!created) {
            Log.e(TAG, "Could not create cache directory...");
            logE("Could not create cache directory");
        }

        // Copy system fonts into our cache dir
        copyDir("/system/fonts", cacheDir.getAbsolutePath());

        // Append zip to filename since it is probably removed
        // for list presentation
        if (!zipFileName.endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        // Copy target themed fonts zip to our cache dir
        Context themeContext = getAppContext(pid);
        AssetManager am = themeContext.getAssets();
        File fontZip = new File(cacheDir, zipFileName);
        try (InputStream inputStream = am.open("fonts/" + zipFileName)) {
            FileUtils.copyToFile(inputStream, fontZip);
        } catch (IOException e) {
            logE("There is an exception when trying to copy themed fonts", e);
        }

        // Unzip new fonts and delete zip file, overwriting any system fonts
        unzip(fontZip.getAbsolutePath(), cacheDir.getAbsolutePath());

        boolean deleted = fontZip.delete();
        if (!deleted) {
            logE("Could not delete ZIP file");
        }

        // Check if theme zip included a fonts.xml. If not, get from existing file in /system
        File srcConfig = new File("/system/etc/fonts.xml");
        File dstConfig = new File(cacheDir, "fonts.xml");
        if (!dstConfig.exists()) {
            FileUtils.copyFile(srcConfig, dstConfig);
        }

        // Prepare system theme fonts folder and copy new fonts folder from our cache
        FileUtils.deleteContentsAndDir(SYSTEM_THEME_FONT_DIR);
        makeDir(SYSTEM_THEME_FONT_DIR);
        copyDir(cacheDir.getAbsolutePath(), SYSTEM_THEME_FONT_DIR.getAbsolutePath());

        // Let system know it's time for a font change
        FileUtils.deleteContentsAndDir(cacheDir);
        refreshFonts();
    }

    private void clearFonts() {
        FileUtils.deleteContentsAndDir(SYSTEM_THEME_FONT_DIR);
        refreshFonts();
    }

    private void refreshFonts() {
        // Set permissions on font files and config xml
        if (SYSTEM_THEME_FONT_DIR.exists()) {
            // Set permissions
            setPermissionsRecursive(SYSTEM_THEME_FONT_DIR,
                    FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IRWXO,
                    FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH);
            restoreconThemeDir();
        }

        // Let system know it's time for a font change
        SystemProperties.set("sys.refresh_font", "true");
        float fontSize = Settings.System.getFloatForUser(mContext.getContentResolver(),
                Settings.System.FONT_SCALE, 1.0f, UserHandle.USER_CURRENT);
        Settings.System.putFloatForUser(mContext.getContentResolver(),
                Settings.System.FONT_SCALE, (fontSize + 0.0000001f), UserHandle.USER_CURRENT);
        restartUi();
    }

    private void applyThemedSounds(String pid, String zipFileName) {
        // Prepare local cache dir for font package assembly
        log("CopySounds - Package ID = \'" + pid + "\'");
        log("CopySounds - File name = \'" + zipFileName + "\'");

        File cacheDir = new File(SYSTEM_THEME_CACHE_DIR, "SoundsCache");
        if (cacheDir.exists()) {
            FileUtils.deleteContentsAndDir(cacheDir);
        }

        boolean created = cacheDir.mkdirs();
        if (!created) {
            logE("Could not create cache directory");
        }

        // Append zip to filename since it is probably removed
        // for list presentation
        if (!zipFileName.endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        // Copy target themed sounds zip to our cache dir
        Context themeContext = getAppContext(pid);
        AssetManager am = themeContext.getAssets();
        File soundsZip = new File(cacheDir, zipFileName);
        try (InputStream inputStream = am.open("audio/" + zipFileName)) {
            FileUtils.copyToFile(inputStream, soundsZip);
        } catch (IOException e) {
            logE("There is an exception when trying to copy themed sounds", e);
        }

        // Unzip new sounds and delete zip file
        unzip(soundsZip.getAbsolutePath(), cacheDir.getAbsolutePath());

        boolean deleted = soundsZip.delete();
        if (!deleted) {
            logE("Could not delete ZIP file");
        }

        clearSounds();
        makeDir(SYSTEM_THEME_AUDIO_DIR);

        for (Sound sound : SOUNDS) {
            File soundsCache = new File(SYSTEM_THEME_CACHE_DIR, sound.cachePath);

            if (!(soundsCache.exists() && soundsCache.isDirectory())) {
                continue;
            }

            makeDir(new File(sound.themePath));

            File mp3 = new File(SYSTEM_THEME_CACHE_DIR, sound.cachePath + sound.soundPath + ".mp3");
            File ogg = new File(SYSTEM_THEME_CACHE_DIR, sound.cachePath + sound.soundPath + ".ogg");
            if (ogg.exists()) {
                FileUtils.copyFile(ogg,
                        new File(sound.themePath + File.separator + sound.soundPath + ".ogg"));
            } else if (mp3.exists()) {
                FileUtils.copyFile(mp3,
                        new File(sound.themePath + File.separator + sound.soundPath + ".mp3"));
            }
        }

        // Let system know it's time for a sound change
        FileUtils.deleteContentsAndDir(cacheDir);
        refreshSounds();
    }

    private void clearSounds() {
        FileUtils.deleteContentsAndDir(SYSTEM_THEME_AUDIO_DIR);
        refreshSounds();
    }

    private void refreshSounds() {
        if (!SYSTEM_THEME_AUDIO_DIR.exists()) {
            // reset to default sounds
            SoundUtils.setDefaultAudible(mContext, RingtoneManager.TYPE_ALARM);
            SoundUtils.setDefaultAudible(mContext, RingtoneManager.TYPE_NOTIFICATION);
            SoundUtils.setDefaultAudible(mContext, RingtoneManager.TYPE_RINGTONE);
            SoundUtils.setDefaultUISounds(mContext.getContentResolver(), "lock_sound", "Lock.ogg");
            SoundUtils.setDefaultUISounds(mContext.getContentResolver(), "unlock_sound", "Unlock.ogg");
        } else {
            // Set permissions
            setPermissionsRecursive(SYSTEM_THEME_AUDIO_DIR,
                    FileUtils.S_IRWXU | FileUtils.S_IRGRP | FileUtils.S_IRWXO,
                    FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH);

            for (Sound sound : SOUNDS) {
                File themePath = new File(sound.themePath);

                if (!(themePath.exists() && themePath.isDirectory())) {
                    continue;
                }

                String metadataName;
                switch (sound.type) {
                    case RingtoneManager.TYPE_RINGTONE:
                        metadataName = "Theme Ringtone";
                        break;
                    case RingtoneManager.TYPE_NOTIFICATION:
                        metadataName = "Theme Notification";
                        break;
                    case RingtoneManager.TYPE_ALARM:
                        metadataName = "Theme Alarm";
                        break;
                    default:
                        metadataName = "Theme";
                }

                File mp3 = new File(themePath, sound.soundPath + ".mp3");
                File ogg = new File(themePath, sound.soundPath + ".ogg");

                if (ogg.exists()) {
                    if (sound.themePath.equals(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath())
                            && sound.type != 0) { // Effect_Tick
                        SoundUtils.setUIAudible(mContext, ogg, sound.type, sound.soundName);
                    } else if (sound.themePath.equals(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath())) {
                        SoundUtils.setUISounds(mContext.getContentResolver(), sound.soundName, ogg
                                .getAbsolutePath());
                    } else {
                        SoundUtils.setAudible(mContext, ogg, sound.type, metadataName);
                    }
                } else if (mp3.exists()) {
                    if (sound.themePath.equals(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath())
                            && sound.type != 0) { // Effect_Tick
                        SoundUtils.setUIAudible(mContext, mp3, sound.type, sound.soundName);
                    } else if (sound.themePath.equals(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath())) {
                        SoundUtils.setUISounds(mContext.getContentResolver(), sound.soundName,
                                mp3.getAbsolutePath());
                    } else {
                        SoundUtils.setAudible(mContext, mp3, sound.type, metadataName);
                    }
                } else {
                    if (sound.themePath.equals(SYSTEM_THEME_UI_SOUNDS_DIR.getAbsolutePath())) {
                        SoundUtils.setDefaultUISounds(mContext.getContentResolver(),
                                sound.soundName, sound.soundPath + ".ogg");
                    } else {
                        SoundUtils.setDefaultAudible(mContext, sound.type);
                    }
                }
            }
        }

        // Refresh sounds
        Intent i = new Intent("com.android.systemui.action.REFRESH_SOUND");
        i.setPackage("com.android.systemui");
        mContext.sendBroadcastAsUser(i, UserHandle.SYSTEM);

        final boolean soundEffectEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1;
        if (soundEffectEnabled) {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            am.unloadSoundEffects();
            am.loadSoundEffects();
        }
    }

    private void unzip(String source, String destination) {
        try (ZipInputStream inputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(source)))) {
            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];

            while ((zipEntry = inputStream.getNextEntry()) != null) {
                File file = new File(destination, zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();

                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                }

                if (zipEntry.isDirectory()) {
                    continue;
                }

                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    while ((count = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, count);
                    }
                }
            }
        } catch (IOException e) {
            logE("There is an exception when trying to unzip", e);
        }
    }

    private boolean makeDir(File dir) {

        if (dir.exists()) {
            return dir.isDirectory();
        }

        if (dir.mkdirs()) {
            int permissions = FileUtils.S_IRWXU | FileUtils.S_IRWXG |
                FileUtils.S_IRWXO;
            SELinux.restorecon(dir);
            return FileUtils.setPermissions(dir, permissions, -1, -1) == 0;
        }

        return false;
    }

    private boolean copyDir(String src, String dst) {
        File[] files = new File(src).listFiles();
        boolean success = true;

        if (files != null) {
            for (File file : files) {
                File newFile = new File(dst + File.separator +
                        file.getName());
                if (file.isDirectory()) {
                    success &= copyDir(file.getAbsolutePath(),
                            newFile.getAbsolutePath());
                } else {
                    success &= FileUtils.copyFile(file, newFile);
                }
            }
        } else {
            // not a directory
            success = false;
        }
        return success;
    }

    void setPermissions(File path, int permissions) {
        FileUtils.setPermissions(path, permissions, -1, -1);
    }

    void setPermissionsRecursive(File dir, int file, int folder) {
        if (!dir.isDirectory()) {
            setPermissions(dir, file);
            return;
        }

        for (File child : dir.listFiles()) {
            if (child.isDirectory()) {
                setPermissionsRecursive(child, file, folder);
                setPermissions(child, folder);
            } else {
                setPermissions(child, file);
            }
        }

        setPermissions(dir, folder);
    }

    private boolean restoreconThemeDir() {
        return SELinux.restoreconRecursive(SYSTEM_THEME_DIR);
    }

    private void log(String msg) {
        if (DEBUG) {
            Log.e(TAG, msg);
        }
    }

    private void logE(String msg, Throwable tr) {
        if (tr != null) {
            Log.e(TAG, msg, tr);
        } else {
            Log.e(TAG, msg);
        }
    }

    private void logE(String msg) {
        logE(msg, null);
    }

    private void updateSettings() {
        synchronized (mLock) {
            mSigOverride = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.FORCE_AUTHORIZE_SUBSTRATUM_PACKAGES, 0,
                    UserHandle.USER_CURRENT) == 1;
        }
    }

    private static class Sound {
        String themePath;
        String cachePath;
        String soundName;
        String soundPath;
        int type;

        Sound(String themePath, String cachePath, String soundName, String soundPath) {
            this.themePath = themePath;
            this.cachePath = cachePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
        }

        Sound(String themePath, String cachePath, String soundName, String soundPath, int type) {
            this.themePath = themePath;
            this.cachePath = cachePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
            this.type = type;
        }
    }

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }
    };

    private class PackageInstallObserver extends IPackageInstallObserver2.Stub {
        @Override
        public void onUserActionRequired(Intent intent) throws RemoteException {
            log("Installer - user action required callback");
            mIsWaiting = false;
        }

        @Override
        public void onPackageInstalled(String packageName, int returnCode,
                                       String msg, Bundle extras) {
            log("Installer - successfully installed \'" + packageName + "\'!");
            mInstalledPackageName = packageName;
            mIsWaiting = false;
        }
    }

    private class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        @Override
        public void packageDeleted(String packageName, int returnCode) {
            log("Remover - successfully removed \'" + packageName + "\'");
            mIsWaiting = false;
        }
    }
}

