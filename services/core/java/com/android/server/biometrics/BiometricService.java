/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.biometrics;

import static android.Manifest.permission.USE_BIOMETRIC;
import static android.Manifest.permission.USE_BIOMETRIC_INTERNAL;
import static android.Manifest.permission.USE_FINGERPRINT;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FACE;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FINGERPRINT;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_IRIS;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_NONE;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.app.IActivityTaskManager;
import android.app.KeyguardManager;
import android.app.TaskStackListener;
import android.app.UserSwitchObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.biometrics.BiometricAuthenticator;
import android.hardware.biometrics.BiometricConstants;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricSourceType;
import android.hardware.biometrics.BiometricsProtoEnums;
import android.hardware.biometrics.IBiometricConfirmDeviceCredentialCallback;
import android.hardware.biometrics.IBiometricEnabledOnKeyguardCallback;
import android.hardware.biometrics.IBiometricService;
import android.hardware.biometrics.IBiometricServiceReceiver;
import android.hardware.biometrics.IBiometricServiceReceiverInternal;
import android.hardware.face.FaceManager;
import android.hardware.face.IFaceService;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.IFingerprintService;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.security.KeyStore;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import android.util.StatsLog;

import com.android.internal.R;
import com.android.internal.os.SomeArgs;
import com.android.internal.statusbar.IStatusBarService;
import com.android.server.SystemService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * System service that arbitrates the modality for BiometricPrompt to use.
 */
public class BiometricService extends SystemService {

    private static final String TAG = "BiometricService";
    private static final boolean DEBUG = true;

    private static final int MSG_ON_TASK_STACK_CHANGED = 1;
    private static final int MSG_ON_AUTHENTICATION_SUCCEEDED = 2;
    private static final int MSG_ON_AUTHENTICATION_FAILED = 3;
    private static final int MSG_ON_ERROR = 4;
    private static final int MSG_ON_ACQUIRED = 5;
    private static final int MSG_ON_DISMISSED = 6;
    private static final int MSG_ON_TRY_AGAIN_PRESSED = 7;
    private static final int MSG_ON_READY_FOR_AUTHENTICATION = 8;
    private static final int MSG_AUTHENTICATE = 9;
    private static final int MSG_CANCEL_AUTHENTICATION = 10;
    private static final int MSG_ON_CONFIRM_DEVICE_CREDENTIAL_SUCCESS = 11;
    private static final int MSG_ON_CONFIRM_DEVICE_CREDENTIAL_ERROR = 12;
    private static final int MSG_REGISTER_CANCELLATION_CALLBACK = 13;

    private static final int[] FEATURE_ID = {
        TYPE_FINGERPRINT,
        TYPE_IRIS,
        TYPE_FACE
    };

    /**
     * Authentication either just called and we have not transitioned to the CALLED state, or
     * authentication terminated (success or error).
     */
    private static final int STATE_AUTH_IDLE = 0;
    /**
     * Authentication was called and we are waiting for the <Biometric>Services to return their
     * cookies before starting the hardware and showing the BiometricPrompt.
     */
    private static final int STATE_AUTH_CALLED = 1;
    /**
     * Authentication started, BiometricPrompt is showing and the hardware is authenticating.
     */
    private static final int STATE_AUTH_STARTED = 2;
    /**
     * Authentication is paused, waiting for the user to press "try again" button. Only
     * passive modalities such as Face or Iris should have this state. Note that for passive
     * modalities, the HAL enters the idle state after onAuthenticated(false) which differs from
     * fingerprint.
     */
    private static final int STATE_AUTH_PAUSED = 3;
    /**
     * Authentication is successful, but we're waiting for the user to press "confirm" button.
     */
    private static final int STATE_AUTH_PENDING_CONFIRM = 5;
    /**
     * Biometric authentication was canceled, but the device is now showing ConfirmDeviceCredential
     */
    private static final int STATE_BIOMETRIC_AUTH_CANCELED_SHOWING_CDC = 6;

    private final class AuthSession implements IBinder.DeathRecipient {
        // Map of Authenticator/Cookie pairs. We expect to receive the cookies back from
        // <Biometric>Services before we can start authenticating. Pairs that have been returned
        // are moved to mModalitiesMatched.
        final HashMap<Integer, Integer> mModalitiesWaiting;
        // Pairs that have been matched.
        final HashMap<Integer, Integer> mModalitiesMatched = new HashMap<>();

        // The following variables are passed to authenticateInternal, which initiates the
        // appropriate <Biometric>Services.
        final IBinder mToken;
        final long mSessionId;
        final int mUserId;
        // Original receiver from BiometricPrompt.
        final IBiometricServiceReceiver mClientReceiver;
        final String mOpPackageName;
        // Info to be shown on BiometricDialog when all cookies are returned.
        final Bundle mBundle;
        final int mCallingUid;
        final int mCallingPid;
        final int mCallingUserId;
        // Continue authentication with the same modality/modalities after "try again" is
        // pressed
        final int mModality;
        final boolean mRequireConfirmation;

        // The current state, which can be either idle, called, or started
        private int mState = STATE_AUTH_IDLE;
        // For explicit confirmation, do not send to keystore until the user has confirmed
        // the authentication.
        byte[] mTokenEscrow;

        // Timestamp when authentication started
        private long mStartTimeMs;
        // Timestamp when hardware authentication occurred
        private long mAuthenticatedTimeMs;

        // TODO(b/123378871): Remove when moved.
        private IBiometricConfirmDeviceCredentialCallback mConfirmDeviceCredentialCallback;

        AuthSession(HashMap<Integer, Integer> modalities, IBinder token, long sessionId,
                int userId, IBiometricServiceReceiver receiver, String opPackageName,
                Bundle bundle, int callingUid, int callingPid, int callingUserId,
                int modality, boolean requireConfirmation,
                IBiometricConfirmDeviceCredentialCallback callback) {
            mModalitiesWaiting = modalities;
            mToken = token;
            mSessionId = sessionId;
            mUserId = userId;
            mClientReceiver = receiver;
            mOpPackageName = opPackageName;
            mBundle = bundle;
            mCallingUid = callingUid;
            mCallingPid = callingPid;
            mCallingUserId = callingUserId;
            mModality = modality;
            mRequireConfirmation = requireConfirmation;
            mConfirmDeviceCredentialCallback = callback;

            if (isFromConfirmDeviceCredential()) {
                try {
                    token.linkToDeath(this, 0 /* flags */);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to link to death", e);
                }
            }
        }

        boolean isCrypto() {
            return mSessionId != 0;
        }

        boolean isFromConfirmDeviceCredential() {
            return mBundle.getBoolean(BiometricPrompt.KEY_FROM_CONFIRM_DEVICE_CREDENTIAL, false);
        }

        boolean containsCookie(int cookie) {
            if (mModalitiesWaiting != null && mModalitiesWaiting.containsValue(cookie)) {
                return true;
            }
            if (mModalitiesMatched != null && mModalitiesMatched.containsValue(cookie)) {
                return true;
            }
            return false;
        }

        // TODO(b/123378871): Remove when moved.
        @Override
        public void binderDied() {
            mHandler.post(() -> {
                Slog.e(TAG, "Binder died, killing ConfirmDeviceCredential");
                if (mConfirmDeviceCredentialCallback == null) {
                    Slog.e(TAG, "Callback is null");
                    return;
                }

                try {
                    mConfirmDeviceCredentialCallback.cancel();
                    mConfirmDeviceCredentialCallback = null;
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to send cancel", e);
                }
            });
        }
    }

    private final class BiometricTaskStackListener extends TaskStackListener {
        @Override
        public void onTaskStackChanged() {
            mHandler.sendEmptyMessage(MSG_ON_TASK_STACK_CHANGED);
        }
    }

    private final AppOpsManager mAppOps;
    private final boolean mHasFeatureFingerprint;
    private final boolean mHasFeatureIris;
    private final boolean mHasFeatureFace;
    private final SettingObserver mSettingObserver;
    private final List<EnabledOnKeyguardCallback> mEnabledOnKeyguardCallbacks;
    private final BiometricTaskStackListener mTaskStackListener = new BiometricTaskStackListener();
    private final Random mRandom = new Random();

    private IFingerprintService mFingerprintService;
    private IFaceService mFaceService;
    private IActivityTaskManager mActivityTaskManager;
    private IStatusBarService mStatusBarService;

    // Get and cache the available authenticator (manager) classes. Used since aidl doesn't support
    // polymorphism :/
    final ArrayList<Authenticator> mAuthenticators = new ArrayList<>();

    // Cache the current service that's being used. This is the service which
    // cancelAuthentication() must be forwarded to. This is just a cache, and the actual
    // check (is caller the current client) is done in the <Biometric>Service.
    // Since Settings/System (not application) is responsible for changing preference, this
    // should be safe.
    private int mCurrentModality;

    // The current authentication session, null if idle/done. We need to track both the current
    // and pending sessions since errors may be sent to either.
    private AuthSession mCurrentAuthSession;
    private AuthSession mPendingAuthSession;

    // TODO(b/123378871): Remove when moved.
    // When BiometricPrompt#setAllowDeviceCredentials is set to true, we need to store the
    // client (app) receiver. BiometricService internally launches CDCA which invokes
    // BiometricService to start authentication (normal path). When auth is success/rejected,
    // CDCA will use an aidl method to poke BiometricService - the result will then be forwarded
    // to this receiver.
    private IBiometricServiceReceiver mConfirmDeviceCredentialReceiver;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_TASK_STACK_CHANGED: {
                    handleTaskStackChanged();
                    break;
                }

                case MSG_ON_AUTHENTICATION_SUCCEEDED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleAuthenticationSucceeded(
                            (boolean) args.arg1 /* requireConfirmation */,
                            (byte[]) args.arg2 /* token */);
                    args.recycle();
                    break;
                }

                case MSG_ON_AUTHENTICATION_FAILED: {
                    handleAuthenticationFailed((String) msg.obj /* failureReason */);
                    break;
                }

                case MSG_ON_ERROR: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleOnError(
                            args.argi1 /* cookie */,
                            args.argi2 /* error */,
                            (String) args.arg1 /* message */);
                    args.recycle();
                    break;
                }

                case MSG_ON_ACQUIRED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleOnAcquired(
                            args.argi1 /* acquiredInfo */,
                            (String) args.arg1 /* message */);
                    args.recycle();
                    break;
                }

                case MSG_ON_DISMISSED: {
                    handleOnDismissed(msg.arg1);
                    break;
                }

                case MSG_ON_TRY_AGAIN_PRESSED: {
                    handleOnTryAgainPressed();
                    break;
                }

                case MSG_ON_READY_FOR_AUTHENTICATION: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleOnReadyForAuthentication(
                            args.argi1 /* cookie */,
                            (boolean) args.arg1 /* requireConfirmation */,
                            args.argi2 /* userId */);
                    args.recycle();
                    break;
                }

                case MSG_AUTHENTICATE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleAuthenticate(
                            (IBinder) args.arg1 /* token */,
                            (long) args.arg2 /* sessionId */,
                            args.argi1 /* userid */,
                            (IBiometricServiceReceiver) args.arg3 /* receiver */,
                            (String) args.arg4 /* opPackageName */,
                            (Bundle) args.arg5 /* bundle */,
                            args.argi2 /* callingUid */,
                            args.argi3 /* callingPid */,
                            args.argi4 /* callingUserId */,
                            (IBiometricConfirmDeviceCredentialCallback) args.arg6 /* callback */);
                    args.recycle();
                    break;
                }

                case MSG_CANCEL_AUTHENTICATION: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleCancelAuthentication(
                            (IBinder) args.arg1 /* token */,
                            (String) args.arg2 /* opPackageName */);
                    args.recycle();
                    break;
                }

                case MSG_ON_CONFIRM_DEVICE_CREDENTIAL_SUCCESS: {
                    handleOnConfirmDeviceCredentialSuccess();
                    break;
                }

                case MSG_ON_CONFIRM_DEVICE_CREDENTIAL_ERROR: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    handleOnConfirmDeviceCredentialError(
                            args.argi1 /* error */,
                            (String) args.arg1 /* errorMsg */);
                    args.recycle();
                    break;
                }

                case MSG_REGISTER_CANCELLATION_CALLBACK: {
                    handleRegisterCancellationCallback(
                            (IBiometricConfirmDeviceCredentialCallback) msg.obj /* callback */);
                    break;
                }

                default:
                    Slog.e(TAG, "Unknown message: " + msg);
                    break;
            }
        }
    };

    private final class Authenticator {
        int mType;
        BiometricAuthenticator mAuthenticator;

        Authenticator(int type, BiometricAuthenticator authenticator) {
            mType = type;
            mAuthenticator = authenticator;
        }

        int getType() {
            return mType;
        }

        BiometricAuthenticator getAuthenticator() {
            return mAuthenticator;
        }
    }

    private final class SettingObserver extends ContentObserver {

        private static final boolean DEFAULT_KEYGUARD_ENABLED = true;
        private static final boolean DEFAULT_APP_ENABLED = true;
        private static final boolean DEFAULT_ALWAYS_REQUIRE_CONFIRMATION = false;

        private final Uri FACE_UNLOCK_KEYGUARD_ENABLED =
                Settings.Secure.getUriFor(Settings.Secure.FACE_UNLOCK_KEYGUARD_ENABLED);
        private final Uri FACE_UNLOCK_APP_ENABLED =
                Settings.Secure.getUriFor(Settings.Secure.FACE_UNLOCK_APP_ENABLED);
        private final Uri FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION =
                Settings.Secure.getUriFor(Settings.Secure.FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION);

        private final ContentResolver mContentResolver;

        private Map<Integer, Boolean> mFaceEnabledOnKeyguard = new HashMap<>();
        private Map<Integer, Boolean> mFaceEnabledForApps = new HashMap<>();
        private Map<Integer, Boolean> mFaceAlwaysRequireConfirmation = new HashMap<>();

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        SettingObserver(Handler handler) {
            super(handler);
            mContentResolver = getContext().getContentResolver();
            updateContentObserver();
        }

        void updateContentObserver() {
            mContentResolver.unregisterContentObserver(this);
            mContentResolver.registerContentObserver(FACE_UNLOCK_KEYGUARD_ENABLED,
                    false /* notifyForDescendents */,
                    this /* observer */,
                    UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(FACE_UNLOCK_APP_ENABLED,
                    false /* notifyForDescendents */,
                    this /* observer */,
                    UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION,
                    false /* notifyForDescendents */,
                    this /* observer */,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, int userId) {
            if (FACE_UNLOCK_KEYGUARD_ENABLED.equals(uri)) {
                mFaceEnabledOnKeyguard.put(userId, Settings.Secure.getIntForUser(
                                mContentResolver,
                                Settings.Secure.FACE_UNLOCK_KEYGUARD_ENABLED,
                                DEFAULT_KEYGUARD_ENABLED ? 1 : 0 /* default */,
                                userId) != 0);

                if (userId == ActivityManager.getCurrentUser() && !selfChange) {
                    notifyEnabledOnKeyguardCallbacks(userId);
                }
            } else if (FACE_UNLOCK_APP_ENABLED.equals(uri)) {
                mFaceEnabledForApps.put(userId, Settings.Secure.getIntForUser(
                                mContentResolver,
                                Settings.Secure.FACE_UNLOCK_APP_ENABLED,
                                DEFAULT_APP_ENABLED ? 1 : 0 /* default */,
                                userId) != 0);
            } else if (FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION.equals(uri)) {
                mFaceAlwaysRequireConfirmation.put(userId, Settings.Secure.getIntForUser(
                                mContentResolver,
                                Settings.Secure.FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION,
                                DEFAULT_ALWAYS_REQUIRE_CONFIRMATION ? 1 : 0 /* default */,
                                userId) != 0);
            }
        }

        boolean getFaceEnabledOnKeyguard() {
            final int user = ActivityManager.getCurrentUser();
            if (!mFaceEnabledOnKeyguard.containsKey(user)) {
                onChange(true /* selfChange */, FACE_UNLOCK_KEYGUARD_ENABLED, user);
            }
            return mFaceEnabledOnKeyguard.get(user);
        }

        boolean getFaceEnabledForApps(int userId) {
            if (!mFaceEnabledForApps.containsKey(userId)) {
                onChange(true /* selfChange */, FACE_UNLOCK_APP_ENABLED, userId);
            }
            return mFaceEnabledForApps.getOrDefault(userId, DEFAULT_APP_ENABLED);
        }

        boolean getFaceAlwaysRequireConfirmation(int userId) {
            if (!mFaceAlwaysRequireConfirmation.containsKey(userId)) {
                onChange(true /* selfChange */, FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION, userId);
            }
            return mFaceAlwaysRequireConfirmation.get(userId);
        }

        void notifyEnabledOnKeyguardCallbacks(int userId) {
            List<EnabledOnKeyguardCallback> callbacks = mEnabledOnKeyguardCallbacks;
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).notify(BiometricSourceType.FACE,
                        mFaceEnabledOnKeyguard.getOrDefault(userId, DEFAULT_KEYGUARD_ENABLED),
                        userId);
            }
        }
    }

    private final class EnabledOnKeyguardCallback implements IBinder.DeathRecipient {

        private final IBiometricEnabledOnKeyguardCallback mCallback;

        EnabledOnKeyguardCallback(IBiometricEnabledOnKeyguardCallback callback) {
            mCallback = callback;
            try {
                mCallback.asBinder().linkToDeath(EnabledOnKeyguardCallback.this, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "Unable to linkToDeath", e);
            }
        }

        void notify(BiometricSourceType sourceType, boolean enabled, int userId) {
            try {
                mCallback.onChanged(sourceType, enabled, userId);
            } catch (DeadObjectException e) {
                Slog.w(TAG, "Death while invoking notify", e);
                mEnabledOnKeyguardCallbacks.remove(this);
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to invoke onChanged", e);
            }
        }

        @Override
        public void binderDied() {
            Slog.e(TAG, "Enabled callback binder died");
            mEnabledOnKeyguardCallbacks.remove(this);
        }
    }

    // Wrap the client's receiver so we can do things with the BiometricDialog first
    private final IBiometricServiceReceiverInternal mInternalReceiver =
            new IBiometricServiceReceiverInternal.Stub() {
        @Override
        public void onAuthenticationSucceeded(boolean requireConfirmation, byte[] token)
                throws RemoteException {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = requireConfirmation;
            args.arg2 = token;
            mHandler.obtainMessage(MSG_ON_AUTHENTICATION_SUCCEEDED, args).sendToTarget();
        }

        @Override
        public void onAuthenticationFailed(int cookie, boolean requireConfirmation)
                throws RemoteException {
            String failureReason = getContext().getString(R.string.biometric_not_recognized);
            mHandler.obtainMessage(MSG_ON_AUTHENTICATION_FAILED, failureReason).sendToTarget();
        }

        @Override
        public void onError(int cookie, int error, String message) throws RemoteException {
            // Determine if error is hard or soft error. Certain errors (such as TIMEOUT) are
            // soft errors and we should allow the user to try authenticating again instead of
            // dismissing BiometricPrompt.
            if (error == BiometricConstants.BIOMETRIC_ERROR_TIMEOUT) {
                mHandler.obtainMessage(MSG_ON_AUTHENTICATION_FAILED, message).sendToTarget();
            } else {
                SomeArgs args = SomeArgs.obtain();
                args.argi1 = cookie;
                args.argi2 = error;
                args.arg1 = message;
                mHandler.obtainMessage(MSG_ON_ERROR, args).sendToTarget();
            }
        }

        @Override
        public void onAcquired(int acquiredInfo, String message) throws RemoteException {
            SomeArgs args = SomeArgs.obtain();
            args.argi1 = acquiredInfo;
            args.arg1 = message;
            mHandler.obtainMessage(MSG_ON_ACQUIRED, args).sendToTarget();
        }

        @Override
        public void onDialogDismissed(int reason) throws RemoteException {
            mHandler.obtainMessage(MSG_ON_DISMISSED, reason, 0 /* arg2 */).sendToTarget();
        }

        @Override
        public void onTryAgainPressed() {
            mHandler.sendEmptyMessage(MSG_ON_TRY_AGAIN_PRESSED);
        }
    };


    /**
     * This is just a pass-through service that wraps Fingerprint, Iris, Face services. This service
     * should not carry any state. The reality is we need to keep a tiny amount of state so that
     * cancelAuthentication() can go to the right place.
     */
    private final class BiometricServiceWrapper extends IBiometricService.Stub {
        @Override // Binder call
        public void onReadyForAuthentication(int cookie, boolean requireConfirmation, int userId) {
            checkInternalPermission();

            SomeArgs args = SomeArgs.obtain();
            args.argi1 = cookie;
            args.arg1 = requireConfirmation;
            args.argi2 = userId;
            mHandler.obtainMessage(MSG_ON_READY_FOR_AUTHENTICATION, args).sendToTarget();
        }

        @Override // Binder call
        public void authenticate(IBinder token, long sessionId, int userId,
                IBiometricServiceReceiver receiver, String opPackageName, Bundle bundle,
                IBiometricConfirmDeviceCredentialCallback callback)
                throws RemoteException {
            final int callingUid = Binder.getCallingUid();
            final int callingPid = Binder.getCallingPid();
            final int callingUserId = UserHandle.getCallingUserId();

            // TODO(b/123378871): Remove when moved.
            if (callback != null) {
                checkInternalPermission();
            }

            // In the BiometricServiceBase, check do the AppOps and foreground check.
            if (userId == callingUserId) {
                // Check the USE_BIOMETRIC permission here.
                checkPermission();
            } else {
                // Only allow internal clients to authenticate with a different userId
                Slog.w(TAG, "User " + callingUserId + " is requesting authentication of userid: "
                        + userId);
                checkInternalPermission();
            }

            if (token == null || receiver == null || opPackageName == null || bundle == null) {
                Slog.e(TAG, "Unable to authenticate, one or more null arguments");
                return;
            }

            final boolean isFromConfirmDeviceCredential =
                    bundle.getBoolean(BiometricPrompt.KEY_FROM_CONFIRM_DEVICE_CREDENTIAL, false);
            if (isFromConfirmDeviceCredential) {
                checkInternalPermission();
            }

            // Check the usage of this in system server. Need to remove this check if it becomes
            // a public API.
            final boolean useDefaultTitle =
                    bundle.getBoolean(BiometricPrompt.KEY_USE_DEFAULT_TITLE, false);
            if (useDefaultTitle) {
                checkInternalPermission();
                // Set the default title if necessary
                if (TextUtils.isEmpty(bundle.getCharSequence(BiometricPrompt.KEY_TITLE))) {
                    bundle.putCharSequence(BiometricPrompt.KEY_TITLE,
                            getContext().getString(R.string.biometric_dialog_default_title));
                }
            }

            // Launch CDC instead if necessary. CDC will return results through an AIDL call, since
            // we can't get activity results. Store the receiver somewhere so we can forward the
            // result back to the client.
            // TODO(b/123378871): Remove when moved.
            if (bundle.getBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL)) {
                mHandler.post(() -> {
                    final KeyguardManager kgm = getContext().getSystemService(
                            KeyguardManager.class);
                    if (!kgm.isDeviceSecure()) {
                        try {
                            receiver.onError(
                                    BiometricConstants.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
                                    getContext().getString(
                                            R.string.biometric_error_device_not_secured));
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Remote exception", e);
                        }
                        return;
                    }
                    mConfirmDeviceCredentialReceiver = receiver;
                    // Use this so we don't need to duplicate logic..
                    final Intent intent = kgm.createConfirmDeviceCredentialIntent(null /* title */,
                            null /* description */, userId);
                    // Then give it the bundle to do magic behavior..
                    intent.putExtra(KeyguardManager.EXTRA_BIOMETRIC_PROMPT_BUNDLE, bundle);
                    // Create a new task with this activity located at the root.
                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    getContext().startActivityAsUser(intent, UserHandle.CURRENT);
                });
                return;
            }

            SomeArgs args = SomeArgs.obtain();
            args.arg1 = token;
            args.arg2 = sessionId;
            args.argi1 = userId;
            args.arg3 = receiver;
            args.arg4 = opPackageName;
            args.arg5 = bundle;
            args.argi2 = callingUid;
            args.argi3 = callingPid;
            args.argi4 = callingUserId;
            args.arg6 = callback;

            mHandler.obtainMessage(MSG_AUTHENTICATE, args).sendToTarget();
        }

        @Override // Binder call
        public void onConfirmDeviceCredentialSuccess() {
            checkInternalPermission();

            mHandler.sendEmptyMessage(MSG_ON_CONFIRM_DEVICE_CREDENTIAL_SUCCESS);
        }

        @Override // Binder call
        public void onConfirmDeviceCredentialError(int error, String message) {
            checkInternalPermission();

            SomeArgs args = SomeArgs.obtain();
            args.argi1 = error;
            args.arg1 = message;
            mHandler.obtainMessage(MSG_ON_CONFIRM_DEVICE_CREDENTIAL_ERROR, args).sendToTarget();
        }

        @Override // Binder call
        public void registerCancellationCallback(
                IBiometricConfirmDeviceCredentialCallback callback) {
            // TODO(b/123378871): Remove when moved.
            // This callback replaces the one stored in the current session. If the session is null
            // we can ignore this, since it means ConfirmDeviceCredential was launched by something
            // else (not BiometricPrompt)
            checkInternalPermission();

            mHandler.obtainMessage(MSG_REGISTER_CANCELLATION_CALLBACK, callback).sendToTarget();
        }

        @Override // Binder call
        public void cancelAuthentication(IBinder token, String opPackageName)
                throws RemoteException {
            checkPermission();

            SomeArgs args = SomeArgs.obtain();
            args.arg1 = token;
            args.arg2 = opPackageName;
            mHandler.obtainMessage(MSG_CANCEL_AUTHENTICATION, args).sendToTarget();
        }

        @Override // Binder call
        public int canAuthenticate(String opPackageName, int userId) {
            Slog.d(TAG, "canAuthenticate: User=" + userId
                    + ", Caller=" + UserHandle.getCallingUserId());

            if (userId != UserHandle.getCallingUserId()) {
                checkInternalPermission();
            } else {
                checkPermission();
            }

            final long ident = Binder.clearCallingIdentity();
            int error;
            try {
                final Pair<Integer, Integer> result = checkAndGetBiometricModality(userId);
                error = result.second;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
            return error;
        }

        @Override
        public boolean hasEnrolledBiometrics(int userId) {
            checkInternalPermission();

            final long ident = Binder.clearCallingIdentity();
            try {
                for (int i = 0; i < mAuthenticators.size(); i++) {
                    if (mAuthenticators.get(i).mAuthenticator.hasEnrolledTemplates(userId)) {
                        return true;
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
            return false;
        }

        @Override // Binder call
        public void registerEnabledOnKeyguardCallback(IBiometricEnabledOnKeyguardCallback callback)
                throws RemoteException {
            checkInternalPermission();
            mEnabledOnKeyguardCallbacks.add(new EnabledOnKeyguardCallback(callback));
            try {
                callback.onChanged(BiometricSourceType.FACE,
                        mSettingObserver.getFaceEnabledOnKeyguard(),
                        UserHandle.getCallingUserId());
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote exception", e);
            }
        }

        @Override // Binder call
        public void setActiveUser(int userId) {
            checkInternalPermission();
            final long ident = Binder.clearCallingIdentity();
            try {
                for (int i = 0; i < mAuthenticators.size(); i++) {
                    mAuthenticators.get(i).getAuthenticator().setActiveUser(userId);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void resetLockout(byte[] token) {
            checkInternalPermission();
            final long ident = Binder.clearCallingIdentity();
            try {
                if (mFingerprintService != null) {
                    mFingerprintService.resetTimeout(token);
                }
                if (mFaceService != null) {
                    mFaceService.resetLockout(token);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Remote exception", e);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    private void checkAppOp(String opPackageName, int callingUid) {
        if (mAppOps.noteOp(AppOpsManager.OP_USE_BIOMETRIC, callingUid,
                opPackageName) != AppOpsManager.MODE_ALLOWED) {
            Slog.w(TAG, "Rejecting " + opPackageName + "; permission denied");
            throw new SecurityException("Permission denied");
        }
    }

    private void checkInternalPermission() {
        getContext().enforceCallingOrSelfPermission(USE_BIOMETRIC_INTERNAL,
                "Must have USE_BIOMETRIC_INTERNAL permission");
    }

    private void checkPermission() {
        if (getContext().checkCallingOrSelfPermission(USE_FINGERPRINT)
                != PackageManager.PERMISSION_GRANTED) {
            getContext().enforceCallingOrSelfPermission(USE_BIOMETRIC,
                    "Must have USE_BIOMETRIC permission");
        }
    }

    /**
     * Initializes the system service.
     * <p>
     * Subclasses must define a single argument constructor that accepts the context
     * and passes it to super.
     * </p>
     *
     * @param context The system server context.
     */
    public BiometricService(Context context) {
        super(context);

        mAppOps = context.getSystemService(AppOpsManager.class);
        mEnabledOnKeyguardCallbacks = new ArrayList<>();
        mSettingObserver = new SettingObserver(mHandler);

        final PackageManager pm = context.getPackageManager();
        mHasFeatureFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        mHasFeatureIris = pm.hasSystemFeature(PackageManager.FEATURE_IRIS);
        mHasFeatureFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);

        try {
            ActivityManager.getService().registerUserSwitchObserver(
                    new UserSwitchObserver() {
                        @Override
                        public void onUserSwitchComplete(int newUserId) {
                            mSettingObserver.updateContentObserver();
                            mSettingObserver.notifyEnabledOnKeyguardCallbacks(newUserId);
                        }
                    }, BiometricService.class.getName()
            );
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register user switch observer", e);
        }
    }

    @Override
    public void onStart() {
        // TODO: maybe get these on-demand
        if (mHasFeatureFingerprint) {
            mFingerprintService = IFingerprintService.Stub.asInterface(
                    ServiceManager.getService(Context.FINGERPRINT_SERVICE));
        }
        if (mHasFeatureFace) {
            mFaceService = IFaceService.Stub.asInterface(
                    ServiceManager.getService(Context.FACE_SERVICE));
        }

        mActivityTaskManager = ActivityTaskManager.getService();
        mStatusBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        // Cache the authenticators
        for (int i = 0; i < FEATURE_ID.length; i++) {
            if (hasFeature(FEATURE_ID[i])) {
                Authenticator authenticator =
                        new Authenticator(FEATURE_ID[i], getAuthenticator(FEATURE_ID[i]));
                mAuthenticators.add(authenticator);
            }
        }

        publishBinderService(Context.BIOMETRIC_SERVICE, new BiometricServiceWrapper());
    }

    /**
     * Checks if there are any available biometrics, and returns the modality. This method also
     * returns errors through the callback (no biometric feature, hardware not detected, no
     * templates enrolled, etc). This service must not start authentication if errors are sent.
     *
     * @Returns A pair [Modality, Error] with Modality being one of
     * {@link BiometricAuthenticator#TYPE_NONE},
     * {@link BiometricAuthenticator#TYPE_FINGERPRINT},
     * {@link BiometricAuthenticator#TYPE_IRIS},
     * {@link BiometricAuthenticator#TYPE_FACE}
     * and the error containing one of the {@link BiometricConstants} errors.
     */
    private Pair<Integer, Integer> checkAndGetBiometricModality(int userId) {
        int modality = TYPE_NONE;

        // No biometric features, send error
        if (mAuthenticators.isEmpty()) {
            return new Pair<>(TYPE_NONE, BiometricConstants.BIOMETRIC_ERROR_HW_NOT_PRESENT);
        }

        // Assuming that authenticators are listed in priority-order, the rest of this function
        // will go through and find the first authenticator that's available, enrolled, and enabled.
        // The tricky part is returning the correct error. Error strings that are modality-specific
        // should also respect the priority-order.

        // Find first authenticator that's detected, enrolled, and enabled.
        boolean isHardwareDetected = false;
        boolean hasTemplatesEnrolled = false;
        boolean enabledForApps = false;

        int firstHwAvailable = TYPE_NONE;
        for (int i = 0; i < mAuthenticators.size(); i++) {
            int type = mAuthenticators.get(i).getType();
            BiometricAuthenticator authenticator = mAuthenticators.get(i).getAuthenticator();
            if (authenticator.isHardwareDetected()) {
                isHardwareDetected = true;
                if (firstHwAvailable == TYPE_NONE) {
                    // Store the first one since we want to return the error in correct priority
                    // order.
                    firstHwAvailable = type;
                }
                if (authenticator.hasEnrolledTemplates(userId)) {
                    hasTemplatesEnrolled = true;
                    if (isEnabledForApp(type, userId)) {
                        // TODO(b/110907543): When face settings (and other settings) have both a
                        // user toggle as well as a work profile settings page, this needs to be
                        // updated to reflect the correct setting.
                        enabledForApps = true;
                        modality |= type;
                    }
                }
            }
        }

        Slog.d(TAG, "checkAndGetBiometricModality: user=" + userId
                + " isHardwareDetected=" + isHardwareDetected
                + " hasTemplatesEnrolled=" + hasTemplatesEnrolled
                + " enabledForApps=" + enabledForApps);

        // Check error conditions
        if (!isHardwareDetected) {
            return new Pair<>(TYPE_NONE, BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE);
        } else if (!hasTemplatesEnrolled) {
            // Return the modality here so the correct error string can be sent. This error is
            // preferred over !enabledForApps
            return new Pair<>(firstHwAvailable, BiometricConstants.BIOMETRIC_ERROR_NO_BIOMETRICS);
        } else if (!enabledForApps) {
            return new Pair<>(TYPE_NONE, BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE);
        }

        return new Pair<>(modality, BiometricConstants.BIOMETRIC_SUCCESS);
    }

    private boolean isEnabledForApp(int modality, int userId) {
        switch(modality) {
            case TYPE_FINGERPRINT:
                return true;
            case TYPE_IRIS:
                return true;
            case TYPE_FACE:
                return mSettingObserver.getFaceEnabledForApps(userId);
            default:
                Slog.w(TAG, "Unsupported modality: " + modality);
                return false;
        }
    }

    private String getErrorString(int type, int error, int vendorCode) {
        switch (type) {
            case TYPE_FINGERPRINT:
                return FingerprintManager.getErrorString(getContext(), error, vendorCode);
            case TYPE_IRIS:
                Slog.w(TAG, "Modality not supported");
                return null; // not supported
            case TYPE_FACE:
                return FaceManager.getErrorString(getContext(), error, vendorCode);
            default:
                Slog.w(TAG, "Unable to get error string for modality: " + type);
                return null;
        }
    }

    private BiometricAuthenticator getAuthenticator(int type) {
        switch (type) {
            case TYPE_FINGERPRINT:
                return (FingerprintManager)
                        getContext().getSystemService(Context.FINGERPRINT_SERVICE);
            case TYPE_IRIS:
                return null;
            case TYPE_FACE:
                return (FaceManager)
                        getContext().getSystemService(Context.FACE_SERVICE);
            default:
                return null;
        }
    }

    private boolean hasFeature(int type) {
        switch (type) {
            case TYPE_FINGERPRINT:
                return mHasFeatureFingerprint;
            case TYPE_IRIS:
                return mHasFeatureIris;
            case TYPE_FACE:
                return mHasFeatureFace;
            default:
                return false;
        }
    }

    private void logDialogDismissed(int reason) {
        if (reason == BiometricPrompt.DISMISSED_REASON_POSITIVE) {
            // Explicit auth, authentication confirmed.
            // Latency in this case is authenticated -> confirmed. <Biometric>Service
            // should have the first half (first acquired -> authenticated).
            final long latency = System.currentTimeMillis()
                    - mCurrentAuthSession.mAuthenticatedTimeMs;

            if (LoggableMonitor.DEBUG) {
                Slog.v(LoggableMonitor.TAG, "Confirmed! Modality: " + statsModality()
                        + ", User: " + mCurrentAuthSession.mUserId
                        + ", IsCrypto: " + mCurrentAuthSession.isCrypto()
                        + ", Client: " + BiometricsProtoEnums.CLIENT_BIOMETRIC_PROMPT
                        + ", RequireConfirmation: "
                        + mCurrentAuthSession.mRequireConfirmation
                        + ", State: " + StatsLog.BIOMETRIC_AUTHENTICATED__STATE__CONFIRMED
                        + ", Latency: " + latency);
            }

            StatsLog.write(StatsLog.BIOMETRIC_AUTHENTICATED,
                    statsModality(),
                    mCurrentAuthSession.mUserId,
                    mCurrentAuthSession.isCrypto(),
                    BiometricsProtoEnums.CLIENT_BIOMETRIC_PROMPT,
                    mCurrentAuthSession.mRequireConfirmation,
                    StatsLog.BIOMETRIC_AUTHENTICATED__STATE__CONFIRMED,
                    latency,
                    Utils.isDebugEnabled(getContext(), mCurrentAuthSession.mUserId));
        } else {

            final long latency = System.currentTimeMillis() - mCurrentAuthSession.mStartTimeMs;

            int error = reason == BiometricPrompt.DISMISSED_REASON_NEGATIVE
                    ? BiometricConstants.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                    : reason == BiometricPrompt.DISMISSED_REASON_USER_CANCEL
                            ? BiometricConstants.BIOMETRIC_ERROR_USER_CANCELED
                            : 0;
            if (LoggableMonitor.DEBUG) {
                Slog.v(LoggableMonitor.TAG, "Dismissed! Modality: " + statsModality()
                        + ", User: " + mCurrentAuthSession.mUserId
                        + ", IsCrypto: " + mCurrentAuthSession.isCrypto()
                        + ", Action: " + BiometricsProtoEnums.ACTION_AUTHENTICATE
                        + ", Client: " + BiometricsProtoEnums.CLIENT_BIOMETRIC_PROMPT
                        + ", Error: " + error
                        + ", Latency: " + latency);
            }
            // Auth canceled
            StatsLog.write(StatsLog.BIOMETRIC_ERROR_OCCURRED,
                    statsModality(),
                    mCurrentAuthSession.mUserId,
                    mCurrentAuthSession.isCrypto(),
                    BiometricsProtoEnums.ACTION_AUTHENTICATE,
                    BiometricsProtoEnums.CLIENT_BIOMETRIC_PROMPT,
                    error,
                    0 /* vendorCode */,
                    Utils.isDebugEnabled(getContext(), mCurrentAuthSession.mUserId),
                    latency);
        }
    }

    private int statsModality() {
        int modality = 0;
        if (mCurrentAuthSession == null) {
            return BiometricsProtoEnums.MODALITY_UNKNOWN;
        }
        if ((mCurrentAuthSession.mModality & TYPE_FINGERPRINT)
                != 0) {
            modality |= BiometricsProtoEnums.MODALITY_FINGERPRINT;
        }
        if ((mCurrentAuthSession.mModality & TYPE_IRIS) != 0) {
            modality |= BiometricsProtoEnums.MODALITY_IRIS;
        }
        if ((mCurrentAuthSession.mModality & TYPE_FACE) != 0) {
            modality |= BiometricsProtoEnums.MODALITY_FACE;
        }
        return modality;
    }

    private void handleTaskStackChanged() {
        try {
            final List<ActivityManager.RunningTaskInfo> runningTasks =
                    mActivityTaskManager.getTasks(1);
            if (!runningTasks.isEmpty()) {
                final String topPackage = runningTasks.get(0).topActivity.getPackageName();
                if (mCurrentAuthSession != null
                        && !topPackage.contentEquals(mCurrentAuthSession.mOpPackageName)) {
                    mStatusBarService.hideBiometricDialog();
                    mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
                    mCurrentAuthSession.mClientReceiver.onError(
                            BiometricConstants.BIOMETRIC_ERROR_CANCELED,
                            getContext().getString(
                                    com.android.internal.R.string.biometric_error_canceled)
                    );
                    mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                    mCurrentAuthSession = null;
                }
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to get running tasks", e);
        }
    }

    private void handleAuthenticationSucceeded(boolean requireConfirmation, byte[] token) {

        try {
            // Should never happen, log this to catch bad HAL behavior (e.g. auth succeeded
            // after user dismissed/canceled dialog).
            if (mCurrentAuthSession == null) {
                Slog.e(TAG, "onAuthenticationSucceeded(): Auth session is null");
                return;
            }

            if (!requireConfirmation) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
                KeyStore.getInstance().addAuthToken(token);
                mCurrentAuthSession.mClientReceiver.onAuthenticationSucceeded();
                mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                mCurrentAuthSession = null;
                cancelInternal(null, null, false);
            } else {
                mCurrentAuthSession.mAuthenticatedTimeMs = System.currentTimeMillis();
                // Store the auth token and submit it to keystore after the confirmation
                // button has been pressed.
                mCurrentAuthSession.mTokenEscrow = token;
                mCurrentAuthSession.mState = STATE_AUTH_PENDING_CONFIRM;
            }

            // Notify SysUI that the biometric has been authenticated. SysUI already knows
            // the implicit/explicit state and will react accordingly.
            mStatusBarService.onBiometricAuthenticated(true, null /* failureReason */, requireConfirmation);
        } catch (RemoteException e) {
            Slog.e(TAG, "Remote exception", e);
        }
    }

    private void handleAuthenticationFailed(String failureReason) {
        try {
            // Should never happen, log this to catch bad HAL behavior (e.g. auth succeeded
            // after user dismissed/canceled dialog).
            if (mCurrentAuthSession == null) {
                Slog.e(TAG, "onAuthenticationFailed(): Auth session is null");
                return;
            }

            mStatusBarService.onBiometricAuthenticated(false, failureReason, false);

            // TODO: This logic will need to be updated if BP is multi-modal
            if ((mCurrentAuthSession.mModality & TYPE_FACE) != 0) {
                // Pause authentication. onBiometricAuthenticated(false) causes the
                // dialog to show a "try again" button for passive modalities.
                mCurrentAuthSession.mState = STATE_AUTH_PAUSED;
            }

            mCurrentAuthSession.mClientReceiver.onAuthenticationFailed();
        } catch (RemoteException e) {
            Slog.e(TAG, "Remote exception", e);
        }
    }

    private void handleOnConfirmDeviceCredentialSuccess() {
        if (mConfirmDeviceCredentialReceiver == null) {
            Slog.w(TAG, "onCDCASuccess null!");
            return;
        }
        try {
            mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
            mConfirmDeviceCredentialReceiver.onAuthenticationSucceeded();
            if (mCurrentAuthSession != null) {
                mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                mCurrentAuthSession = null;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException", e);
        }
        mConfirmDeviceCredentialReceiver = null;
    }

    private void handleOnConfirmDeviceCredentialError(int error, String message) {
        if (mConfirmDeviceCredentialReceiver == null) {
            Slog.w(TAG, "onCDCAError null! Error: " + error + " " + message);
            return;
        }
        try {
            mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
            mConfirmDeviceCredentialReceiver.onError(error, message);
            if (mCurrentAuthSession != null) {
                mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                mCurrentAuthSession = null;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException", e);
        }
        mConfirmDeviceCredentialReceiver = null;
    }

    private void handleRegisterCancellationCallback(
            IBiometricConfirmDeviceCredentialCallback callback) {
        if (mCurrentAuthSession == null) {
            Slog.d(TAG, "Current auth session null");
            return;
        }
        Slog.d(TAG, "Updating cancel callback");
        mCurrentAuthSession.mConfirmDeviceCredentialCallback = callback;
    }

    private void handleOnError(int cookie, int error, String message) {
        Slog.d(TAG, "Error: " + error + " cookie: " + cookie);
        // Errors can either be from the current auth session or the pending auth session.
        // The pending auth session may receive errors such as ERROR_LOCKOUT before
        // it becomes the current auth session. Similarly, the current auth session may
        // receive errors such as ERROR_CANCELED while the pending auth session is preparing
        // to be started. Thus we must match error messages with their cookies to be sure
        // of their intended receivers.
        try {
            if (mCurrentAuthSession != null && mCurrentAuthSession.containsCookie(cookie)) {

                if (mCurrentAuthSession.isFromConfirmDeviceCredential()) {
                    // If we were invoked by ConfirmDeviceCredential, do not delete the current
                    // auth session since we still need to respond to cancel signal while
                    if (DEBUG) Slog.d(TAG, "From CDC, transition to CANCELED_SHOWING_CDC state");

                    // Send the error to ConfirmDeviceCredential so that it goes to Pin/Pattern/Pass
                    // screen
                    mCurrentAuthSession.mClientReceiver.onError(error, message);
                    mCurrentAuthSession.mState = STATE_BIOMETRIC_AUTH_CANCELED_SHOWING_CDC;
                    mStatusBarService.hideBiometricDialog();
                } else if (mCurrentAuthSession.mState == STATE_AUTH_STARTED) {
                    mStatusBarService.onBiometricError(message);
                    if (error == BiometricConstants.BIOMETRIC_ERROR_CANCELED) {
                        mActivityTaskManager.unregisterTaskStackListener(
                                mTaskStackListener);
                        mCurrentAuthSession.mClientReceiver.onError(error, message);
                        mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                        mCurrentAuthSession = null;
                        mStatusBarService.hideBiometricDialog();
                    } else {
                        // Send errors after the dialog is dismissed.
                        mHandler.postDelayed(() -> {
                            try {
                                if (mCurrentAuthSession != null) {
                                    mActivityTaskManager.unregisterTaskStackListener(
                                            mTaskStackListener);
                                    mCurrentAuthSession.mClientReceiver.onError(error,
                                            message);
                                    mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                                    mCurrentAuthSession = null;
                                }
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Remote exception", e);
                            }
                        }, BiometricPrompt.HIDE_DIALOG_DELAY);
                    }
                } else if (mCurrentAuthSession.mState == STATE_AUTH_PAUSED) {
                    // In the "try again" state, we should forward canceled errors to
                    // the client and and clean up.
                    mCurrentAuthSession.mClientReceiver.onError(error, message);
                    mStatusBarService.onBiometricError(message);
                    mActivityTaskManager.unregisterTaskStackListener(
                            mTaskStackListener);
                    mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                    mCurrentAuthSession = null;
                } else {
                    Slog.e(TAG, "Impossible session error state: "
                            + mCurrentAuthSession.mState);
                }
            } else if (mPendingAuthSession != null
                    && mPendingAuthSession.containsCookie(cookie)) {
                if (mPendingAuthSession.mState == STATE_AUTH_CALLED) {
                    mPendingAuthSession.mClientReceiver.onError(error, message);
                    mPendingAuthSession.mState = STATE_AUTH_IDLE;
                    mPendingAuthSession = null;
                } else {
                    Slog.e(TAG, "Impossible pending session error state: "
                            + mPendingAuthSession.mState);
                }
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Remote exception", e);
        }
    }

    private void handleOnAcquired(int acquiredInfo, String message) {
        // Should never happen, log this to catch bad HAL behavior (e.g. auth succeeded
        // after user dismissed/canceled dialog).
        if (mCurrentAuthSession == null) {
            Slog.e(TAG, "onAcquired(): Auth session is null");
            return;
        }

        if (acquiredInfo != BiometricConstants.BIOMETRIC_ACQUIRED_GOOD) {
            if (message == null) {
                Slog.w(TAG, "Ignoring null message: " + acquiredInfo);
                return;
            }
            try {
                mStatusBarService.onBiometricHelp(message);
            } catch (RemoteException e) {
                Slog.e(TAG, "Remote exception", e);
            }
        }
    }

    private void handleOnDismissed(int reason) {
        if (mCurrentAuthSession == null) {
            Slog.e(TAG, "onDialogDismissed: " + reason + ", auth session null");
            return;
        }

        logDialogDismissed(reason);

        try {
            if (reason != BiometricPrompt.DISMISSED_REASON_POSITIVE) {
                // Positive button is used by passive modalities as a "confirm" button,
                // do not send to client
                mCurrentAuthSession.mClientReceiver.onDialogDismissed(reason);
                // Cancel authentication. Skip the token/package check since we are cancelling
                // from system server. The interface is permission protected so this is fine.
                cancelInternal(null /* token */, null /* package */, false /* fromClient */);
            }
            if (reason == BiometricPrompt.DISMISSED_REASON_USER_CANCEL) {
                mCurrentAuthSession.mClientReceiver.onError(
                        BiometricConstants.BIOMETRIC_ERROR_USER_CANCELED,
                        getContext().getString(
                                com.android.internal.R.string.biometric_error_user_canceled));
            } else if (reason == BiometricPrompt.DISMISSED_REASON_POSITIVE) {
                // Have the service send the token to KeyStore, and send onAuthenticated
                // to the application
                KeyStore.getInstance().addAuthToken(mCurrentAuthSession.mTokenEscrow);
                mCurrentAuthSession.mClientReceiver.onAuthenticationSucceeded();
                // If we are using multiple modalities, we cancel the other modality that still
                // might be listening for authentication
                cancelInternal(null /* token */, null /* package */, false /* fromClient */);
            }

            // Do not clean up yet if we are from ConfirmDeviceCredential. We should be in the
            // STATE_BIOMETRIC_AUTH_CANCELED_SHOWING_CDC. The session should only be removed when
            // ConfirmDeviceCredential is confirmed or canceled.
            // TODO(b/123378871): Remove when moved
            if (!mCurrentAuthSession.isFromConfirmDeviceCredential()) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
                mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                mCurrentAuthSession = null;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Remote exception", e);
        }
    }

    private void handleOnTryAgainPressed() {
        Slog.d(TAG, "onTryAgainPressed");
        // No need to check permission, since it can only be invoked by SystemUI
        // (or system server itself).
        authenticateInternal(mCurrentAuthSession.mToken,
                mCurrentAuthSession.mSessionId,
                mCurrentAuthSession.mUserId,
                mCurrentAuthSession.mClientReceiver,
                mCurrentAuthSession.mOpPackageName,
                mCurrentAuthSession.mBundle,
                mCurrentAuthSession.mCallingUid,
                mCurrentAuthSession.mCallingPid,
                mCurrentAuthSession.mCallingUserId,
                mCurrentAuthSession.mModality,
                mCurrentAuthSession.mConfirmDeviceCredentialCallback);
        mCurrentAuthSession = null;
    }

    private void handleOnReadyForAuthentication(int cookie, boolean requireConfirmation,
            int userId) {
        if (mPendingAuthSession == null) {
            return;
        }
        Iterator it = mPendingAuthSession.mModalitiesWaiting.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> pair = (Map.Entry) it.next();
            if (pair.getValue() == cookie) {
                mPendingAuthSession.mModalitiesMatched.put(pair.getKey(), pair.getValue());
                mPendingAuthSession.mModalitiesWaiting.remove(pair.getKey());
                Slog.d(TAG, "Matched cookie: " + cookie + ", "
                        + mPendingAuthSession.mModalitiesWaiting.size() + " remaining");
                break;
            }
        }

        if (mPendingAuthSession.mModalitiesWaiting.isEmpty()) {
            final boolean continuing = mCurrentAuthSession != null
                    && mCurrentAuthSession.mState == STATE_AUTH_PAUSED;

            mCurrentAuthSession = mPendingAuthSession;

            // Time starts when lower layers are ready to start the client.
            mCurrentAuthSession.mStartTimeMs = System.currentTimeMillis();
            mPendingAuthSession = null;

            mCurrentAuthSession.mState = STATE_AUTH_STARTED;
            try {
                int modality = TYPE_NONE;
                it = mCurrentAuthSession.mModalitiesMatched.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Integer> pair = (Map.Entry) it.next();
                    if ((pair.getKey() & TYPE_FINGERPRINT) != 0) {
                        mFingerprintService.startPreparedClient(pair.getValue());
                    }
                    if ((pair.getKey() & TYPE_IRIS) != 0) {
                        Slog.e(TAG, "Iris unsupported");
                    }
                    if ((pair.getKey() & TYPE_FACE) != 0) {
                        mFaceService.startPreparedClient(pair.getValue());
                    }
                    modality |= pair.getKey();
                }

                if (!continuing) {
                    mStatusBarService.showBiometricDialog(mCurrentAuthSession.mBundle,
                            mInternalReceiver, modality, requireConfirmation, userId);
                    mActivityTaskManager.registerTaskStackListener(mTaskStackListener);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Remote exception", e);
            }
        }
    }

    private void handleAuthenticate(IBinder token, long sessionId, int userId,
            IBiometricServiceReceiver receiver, String opPackageName, Bundle bundle,
            int callingUid, int callingPid, int callingUserId,
            IBiometricConfirmDeviceCredentialCallback callback) {

        mHandler.post(() -> {
            final Pair<Integer, Integer> result = checkAndGetBiometricModality(userId);
            final int modality = result.first;
            final int error = result.second;

            // Check for errors, notify callback, and return
            if (error != BiometricConstants.BIOMETRIC_SUCCESS) {
                try {
                    final String hardwareUnavailable =
                            getContext().getString(R.string.biometric_error_hw_unavailable);
                    switch (error) {
                        case BiometricConstants.BIOMETRIC_ERROR_HW_NOT_PRESENT:
                            receiver.onError(error, hardwareUnavailable);
                            break;
                        case BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                            receiver.onError(error, hardwareUnavailable);
                            break;
                        case BiometricConstants.BIOMETRIC_ERROR_NO_BIOMETRICS:
                            receiver.onError(error,
                                    getErrorString(modality, error, 0 /* vendorCode */));
                            break;
                        default:
                            Slog.e(TAG, "Unhandled error");
                            break;
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to send error", e);
                }
                return;
            }

            mCurrentModality = modality;

            // Start preparing for authentication. Authentication starts when
            // all modalities requested have invoked onReadyForAuthentication.
            authenticateInternal(token, sessionId, userId, receiver, opPackageName, bundle,
                    callingUid, callingPid, callingUserId, modality, callback);
        });
    }

    /**
     * authenticate() (above) which is called from BiometricPrompt determines which
     * modality/modalities to start authenticating with. authenticateInternal() should only be
     * used for:
     * 1) Preparing <Biometric>Services for authentication when BiometricPrompt#authenticate is,
     *    invoked, shortly after which BiometricPrompt is shown and authentication starts
     * 2) Preparing <Biometric>Services for authentication when BiometricPrompt is already shown
     *    and the user has pressed "try again"
     */
    private void authenticateInternal(IBinder token, long sessionId, int userId,
            IBiometricServiceReceiver receiver, String opPackageName, Bundle bundle,
            int callingUid, int callingPid, int callingUserId, int modality,
            IBiometricConfirmDeviceCredentialCallback callback) {
        try {
            boolean requireConfirmation = bundle.getBoolean(
                    BiometricPrompt.KEY_REQUIRE_CONFIRMATION, true /* default */);
            if ((modality & TYPE_FACE) != 0) {
                // Check if the user has forced confirmation to be required in Settings.
                requireConfirmation = requireConfirmation
                        || mSettingObserver.getFaceAlwaysRequireConfirmation(userId);
            }
            // Generate random cookies to pass to the services that should prepare to start
            // authenticating. Store the cookie here and wait for all services to "ack"
            // with the cookie. Once all cookies are received, we can show the prompt
            // and let the services start authenticating. The cookie should be non-zero.
            final int cookie = mRandom.nextInt(Integer.MAX_VALUE - 1) + 1;
            Slog.d(TAG, "Creating auth session. Modality: " + modality
                    + ", cookie: " + cookie);
            final HashMap<Integer, Integer> authenticators = new HashMap<>();
            // No polymorphism :(
            if ((modality & TYPE_FINGERPRINT) != 0) {
                authenticators.put(TYPE_FINGERPRINT, cookie);
                mFingerprintService.prepareForAuthentication(token, sessionId, userId,
                        mInternalReceiver, opPackageName, cookie,
                        callingUid, callingPid, callingUserId);
            }
            if ((modality & TYPE_IRIS) != 0) {
                Slog.w(TAG, "Iris unsupported");
            }
            if ((modality & TYPE_FACE) != 0) {
                authenticators.put(TYPE_FACE, cookie);
                mFaceService.prepareForAuthentication(requireConfirmation,
                        token, sessionId, userId, mInternalReceiver, opPackageName,
                        cookie, callingUid, callingPid, callingUserId);
            }
            mPendingAuthSession = new AuthSession(authenticators, token, sessionId, userId,
                    receiver, opPackageName, bundle, callingUid, callingPid, callingUserId,
                    modality, requireConfirmation, callback);
            mPendingAuthSession.mState = STATE_AUTH_CALLED;
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to start authentication", e);
        }
    }

    private void handleCancelAuthentication(IBinder token, String opPackageName) {
        if (token == null || opPackageName == null) {
            Slog.e(TAG, "Unable to cancel, one or more null arguments");
            return;
        }

        if (mCurrentAuthSession != null
                && mCurrentAuthSession.mState == STATE_BIOMETRIC_AUTH_CANCELED_SHOWING_CDC) {
            if (DEBUG) Slog.d(TAG, "Cancel received while ConfirmDeviceCredential showing");
            try {
                mCurrentAuthSession.mConfirmDeviceCredentialCallback.cancel();
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to cancel ConfirmDeviceCredential", e);
            }

            // TODO(b/123378871): Remove when moved. Piggy back on this for now to clean up.
            handleOnConfirmDeviceCredentialError(BiometricConstants.BIOMETRIC_ERROR_CANCELED,
                    getContext().getString(R.string.biometric_error_canceled));
        } else if (mCurrentAuthSession != null
                && mCurrentAuthSession.mState != STATE_AUTH_STARTED) {
            // We need to check the current authenticators state. If we're pending confirm
            // or idle, we need to dismiss the dialog and send an ERROR_CANCELED to the client,
            // since we won't be getting an onError from the driver.
            try {
                // Send error to client
                mCurrentAuthSession.mClientReceiver.onError(
                        BiometricConstants.BIOMETRIC_ERROR_CANCELED,
                        getContext().getString(
                                com.android.internal.R.string.biometric_error_user_canceled)
                );

                mCurrentAuthSession.mState = STATE_AUTH_IDLE;
                mCurrentAuthSession = null;
                mStatusBarService.hideBiometricDialog();
            } catch (RemoteException e) {
                Slog.e(TAG, "Remote exception", e);
            }
        } else {
            boolean fromCDC = false;
            if (mCurrentAuthSession != null) {
                fromCDC = mCurrentAuthSession.mBundle.getBoolean(
                        BiometricPrompt.KEY_FROM_CONFIRM_DEVICE_CREDENTIAL, false);
            }

            if (fromCDC) {
                if (DEBUG) Slog.d(TAG, "Cancelling from CDC");
                cancelInternal(token, opPackageName, false /* fromClient */);
                try {
                    mStatusBarService.hideBiometricDialog();
                } catch (RemoteException e) {
                    Slog.e(TAG, "Remote exception", e);
                }
            } else {
                cancelInternal(token, opPackageName, true /* fromClient */);
            }

        }
    }

    void cancelInternal(IBinder token, String opPackageName, boolean fromClient) {
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        final int callingUserId = UserHandle.getCallingUserId();
        mHandler.post(() -> {
            try {
                // TODO: For multiple modalities, send a single ERROR_CANCELED only when all
                // drivers have canceled authentication.
                if ((mCurrentModality & TYPE_FINGERPRINT) != 0) {
                    mFingerprintService.cancelAuthenticationFromService(token, opPackageName,
                            callingUid, callingPid, callingUserId, fromClient);
                }
                if ((mCurrentModality & TYPE_IRIS) != 0) {
                    Slog.w(TAG, "Iris unsupported");
                }
                if ((mCurrentModality & TYPE_FACE) != 0) {
                    mFaceService.cancelAuthenticationFromService(token, opPackageName,
                            callingUid, callingPid, callingUserId, fromClient);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to cancel authentication");
            }
        });
    }

}
