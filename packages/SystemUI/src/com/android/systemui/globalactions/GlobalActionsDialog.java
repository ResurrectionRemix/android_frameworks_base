/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.globalactions;

import com.android.internal.R;
import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.EmergencyAffordanceManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.Dependency;
import com.android.systemui.HardwareUiLayout;
import com.android.systemui.Interpolators;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.GlobalActions.GlobalActionsManager;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.volume.VolumeDialogMotion.LogAccelerateInterpolator;
import com.android.systemui.volume.VolumeDialogMotion.LogDecelerateInterpolator;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.IActivityManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.MathUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.colorextraction.drawable.GradientDrawable;
import com.android.systemui.statusbar.policy.FlashlightController;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.android.systemui.Dependency;
import com.android.internal.util.rr.OnTheGoActions;

/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class GlobalActionsDialog implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener {

    static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";

    private static final String TAG = "GlobalActionsDialog";

    private static final boolean SHOW_SILENT_TOGGLE = true;

    /* Valid settings for global actions keys.
     * see config.xml config_globalActionList */
    private static final String GLOBAL_ACTION_KEY_POWER = "power";
    private static final String GLOBAL_ACTION_KEY_AIRPLANE = "airplane";
    private static final String GLOBAL_ACTION_KEY_BUGREPORT = "bugreport";
    private static final String GLOBAL_ACTION_KEY_SILENT = "silent";
    private static final String GLOBAL_ACTION_KEY_USERS = "users";
    private static final String GLOBAL_ACTION_KEY_SETTINGS = "settings";
    private static final String GLOBAL_ACTION_KEY_LOCKDOWN = "lockdown";
    private static final String GLOBAL_ACTION_KEY_VOICEASSIST = "voiceassist";
    private static final String GLOBAL_ACTION_KEY_ASSIST = "assist";
    private static final String GLOBAL_ACTION_KEY_RESTART = "restart";
    private static final String GLOBAL_ACTION_KEY_ADVANCED = "advanced";
    private static final String GLOBAL_ACTION_KEY_SCREENSHOT = "screenshot";
    private static final String GLOBAL_ACTION_KEY_SCREENRECORD = "screenrecord";
    private static final String GLOBAL_ACTION_KEY_ON_THE_GO = "on_the_go";
    private static final String GLOBAL_ACTION_KEY_FLASHLIGHT = "flashlight";

    private static final int SHOW_TOGGLES_BUTTON = 1;
    private static final int RESTART_HOT_BUTTON = 2;
    private static final int RESTART_RECOVERY_BUTTON = 3;
    private static final int RESTART_BOOTLOADER_BUTTON = 4;
    private static final int RESTART_UI_BUTTON = 5;

    private final Context mContext;
    private final GlobalActionsManager mWindowManagerFuncs;
    private final AudioManager mAudioManager;
    private final IDreamManager mDreamManager;

    private ArrayList<Action> mItems;
    private ActionsDialog mDialog;

    private Action mSilentModeAction;
    private ToggleAction mAirplaneModeOn;
    private ToggleAction.State mAirplaneState = ToggleAction.State.Off;

    private AdvancedAction mShowAdvancedToggles;
    private AdvancedAction mRestartHot;
    private AdvancedAction mRestartRecovery;
    private AdvancedAction mRestartBootloader;
    private AdvancedAction mRestartSystemUI;

    private MyAdapter mAdapter;

    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;
    private boolean mIsWaitingForEcmExit = false;
    private boolean mHasTelephony;
    private boolean mHasVibrator;
    private final boolean mShowSilentToggle;
    private final EmergencyAffordanceManager mEmergencyAffordanceManager;

    private BitSet mAirplaneModeBits;
    private final List<PhoneStateListener> mPhoneStateListeners = new ArrayList<>();

    private static final String SYSUI_PACKAGE = "com.android.systemui";
    private static final String SYSUI_SCREENSHOT_SERVICE =
            "com.android.systemui.screenshot.TakeScreenshotService";
    private static final String SYSUI_SCREENRECORD_SERVICE =
            "com.android.systemui.omni.screenrecord.TakeScreenrecordService";

    private FlashlightController mFlashlightController;
    private int mScreenshotDelay;

    /**
     * @param context everything needs a context :(
     */
    public GlobalActionsDialog(Context context, GlobalActionsManager windowManagerFuncs) {
        mContext = new ContextThemeWrapper(context, com.android.systemui.R.style.qs_theme);
        mWindowManagerFuncs = windowManagerFuncs;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.getService(DreamService.DREAM_SERVICE));

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);

        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mHasTelephony = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);

        // get notified of phone state changes
        SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override
            public void onSubscriptionsChanged() {
                super.onSubscriptionsChanged();
                setupAirplaneModeListeners();
            }
        });
        setupAirplaneModeListeners();
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator != null && vibrator.hasVibrator();

        mShowSilentToggle = SHOW_SILENT_TOGGLE && !mContext.getResources().getBoolean(
                R.bool.config_useFixedVolume);

        mEmergencyAffordanceManager = new EmergencyAffordanceManager(context);

        // Set the initial status of airplane mode toggle
        mAirplaneState = getUpdatedAirplaneToggleState();
        mFlashlightController = Dependency.get(FlashlightController.class);
    }

    /**
     * Since there are two ways of handling airplane mode (with telephony, we depend on the internal
     * device telephony state), and MSIM devices do not report phone state for missing SIMs, we
     * need to dynamically setup listeners based on subscription changes.
     *
     * So if there is _any_ active SIM in the device, we can depend on the phone state,
     * otherwise fall back to {@link Settings.Global#AIRPLANE_MODE_ON}.
     */
    private void setupAirplaneModeListeners() {
        TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        for (PhoneStateListener listener : mPhoneStateListeners) {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
        mPhoneStateListeners.clear();

        final List<SubscriptionInfo> subInfoList = SubscriptionManager.from(mContext)
                .getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            mHasTelephony = true;
            mAirplaneModeBits = new BitSet(subInfoList.size());
            for (int i = 0; i < subInfoList.size(); i++) {
                final int finalI = i;
                PhoneStateListener subListener = new PhoneStateListener(subInfoList.get(finalI)
                        .getSubscriptionId()) {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        final boolean inAirplaneMode = serviceState.getState()
                                == ServiceState.STATE_POWER_OFF;
                        mAirplaneModeBits.set(finalI, inAirplaneMode);

                        // we're in airplane mode if _any_ of the subscriptions say we are
                        mAirplaneState = mAirplaneModeBits.cardinality() > 0
                                ? ToggleAction.State.On : ToggleAction.State.Off;

                        mAirplaneModeOn.updateState(mAirplaneState);
                        if (mAdapter != null) {
                            mAdapter.notifyDataSetChanged();
                        }
                    }
                };
                mPhoneStateListeners.add(subListener);
                telephonyManager.listen(subListener, PhoneStateListener.LISTEN_SERVICE_STATE);
            }
        } else {
            mHasTelephony = false;
        }

        // Set the initial status of airplane mode toggle
        mAirplaneState = getUpdatedAirplaneToggleState();
    }

    /**
     * Show the global actions dialog (creating if necessary)
     *
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog != null) {
            mDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            mDialog.dismiss();
            mDialog = null;
            // Show delayed, so that the dismiss of the previous dialog completes
            mHandler.sendEmptyMessage(MESSAGE_SHOW);
        } else {
            handleShow();
        }
    }

    private void awakenIfNecessary() {
        if (mDreamManager != null) {
            try {
                if (mDreamManager.isDreaming()) {
                    mDreamManager.awaken();
                }
            } catch (RemoteException e) {
                // we tried
            }
        }
    }

    private void handleShow() {
        awakenIfNecessary();
        mDialog = createDialog();
        checkSettings();
        prepareDialog();

        // If we only have 1 item and it's a simple press action, just do this action.
        if (mAdapter.getCount() == 1
                && mAdapter.getItem(0) instanceof SinglePressAction
                && !(mAdapter.getItem(0) instanceof LongPressAction)) {
            ((SinglePressAction) mAdapter.getItem(0)).onPress();
        } else {
            WindowManager.LayoutParams attrs = mDialog.getWindow().getAttributes();
            attrs.setTitle("ActionsDialog");
            attrs.alpha = setPowerMenuAlpha();
            mDialog.getWindow().setAttributes(attrs);
            mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            mDialog.getWindow().setDimAmount(setPowerMenuDialogDim());
            mDialog.show();
            mWindowManagerFuncs.onGlobalActionsShown();
        }
    }

    private float setPowerMenuAlpha() {
        int mPowerMenuAlpha = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TRANSPARENT_POWER_MENU, 100);
        double dAlpha = mPowerMenuAlpha / 100.0;
        float alpha = (float) dAlpha;
        return alpha;
    }

    private float setPowerMenuDialogDim() {
        int mPowerMenuDialogDim = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TRANSPARENT_POWER_DIALOG_DIM, 50);
        double dDim = mPowerMenuDialogDim / 100.0;
        float dim = (float) dDim;
        return dim;
    }

    /**
     * Create the global actions dialog.
     *
     * @return A new dialog.
     */
    private ActionsDialog createDialog() {
        // Simple toggle style if there's no vibrator, otherwise use a tri-state
        if (!mHasVibrator) {
            mSilentModeAction = new SilentModeToggleAction();
        } else {
            mSilentModeAction = new SilentModeTriStateAction(mContext, mAudioManager, mHandler);
        }
        mAirplaneModeOn = new ToggleAction(
                R.drawable.ic_lock_airplane_mode,
                R.drawable.ic_lock_airplane_mode_off,
                R.string.global_actions_toggle_airplane_mode) {

            void onToggle(boolean on) {
                if (mHasTelephony && Boolean.parseBoolean(
                        SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
                    mIsWaitingForEcmExit = true;
                    // Launch ECM exit dialog
                    Intent ecmDialogIntent =
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null);
                    ecmDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(ecmDialogIntent);
                } else {
                    changeAirplaneModeSystemSetting(on);
                }
            }

            @Override
            protected void changeStateFromPress(boolean buttonOn) {
                if (!mHasTelephony) return;

                // In ECM mode airplane state cannot be changed
                if (!(Boolean.parseBoolean(
                        SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE)))) {
                    mState = buttonOn ? State.TurningOn : State.TurningOff;
                    mAirplaneState = mState;
                }
            }

            public boolean showDuringKeyguard() {
                boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_LS_AIRPLANE, 0) == 1;
                return showlocked;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onAirplaneModeChanged();

        mShowAdvancedToggles = new AdvancedAction(
                SHOW_TOGGLES_BUTTON,
                com.android.systemui.R.drawable.ic_restart_advanced,
                com.android.systemui.R.string.global_action_restart_advanced,
                mWindowManagerFuncs, mHandler) {

            public boolean showDuringKeyguard() {
                boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_LS_ADVANCED_REBOOT, 0) == 1;
                return showlocked;
            }

            public boolean showBeforeProvisioning() {
                return true;
            }
        };

        mRestartHot = new AdvancedAction(
                RESTART_HOT_BUTTON,
                com.android.systemui.R.drawable.ic_restart_hot,
                com.android.systemui.R.string.global_action_restart_hot,
                mWindowManagerFuncs, mHandler) {

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return true;
            }
        };

        mRestartRecovery = new AdvancedAction(
                RESTART_RECOVERY_BUTTON,
                com.android.systemui.R.drawable.ic_restart_recovery,
                com.android.systemui.R.string.global_action_restart_recovery,
                mWindowManagerFuncs, mHandler) {

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return true;
            }
        };

        mRestartBootloader = new AdvancedAction(
                RESTART_BOOTLOADER_BUTTON,
                com.android.systemui.R.drawable.ic_restart_bootloader,
                com.android.systemui.R.string.global_action_restart_bootloader,
                mWindowManagerFuncs, mHandler) {

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return true;
            }
        };

        mRestartSystemUI = new AdvancedAction(
                RESTART_UI_BUTTON,
                com.android.systemui.R.drawable.ic_restart_ui,
                com.android.systemui.R.string.global_action_restart_ui,
                mWindowManagerFuncs, mHandler) {

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return true;
            }
        };

        mItems = new ArrayList<Action>();
        updateOnTheGoActions();
        String[] defaultActions = mContext.getResources().getStringArray(
                R.array.config_custom_globalActionsList);

        ArraySet<String> addedKeys = new ArraySet<String>();
        for (int i = 0; i < defaultActions.length; i++) {
            String actionKey = defaultActions[i];
            if (addedKeys.contains(actionKey)) {
                // If we already have added this, don't add it again.
                continue;
            }
            if (GLOBAL_ACTION_KEY_POWER.equals(actionKey)) {
                mItems.add(new PowerAction());
            } else if (GLOBAL_ACTION_KEY_SCREENRECORD.equals(actionKey)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_SCREENRECORD, 0) == 1) {
                mItems.add(new ScreenrecordAction());
                }
            } else if (GLOBAL_ACTION_KEY_FLASHLIGHT.equals(actionKey)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_FLASHLIGHT, 0) == 1) {
                mItems.add(new FlashLightAction());
                }
            } else if (GLOBAL_ACTION_KEY_AIRPLANE.equals(actionKey)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_AIRPLANE, 0) != 0) {
                    mItems.add(mAirplaneModeOn);
                }
            } else if (GLOBAL_ACTION_KEY_BUGREPORT.equals(actionKey)) {
                //if (Settings.Global.getInt(mContext.getContentResolver(),
                //        Settings.Global.BUGREPORT_IN_POWER_MENU, 0) != 0 && isCurrentUserOwner()) {
                //    mItems.add(new BugReportAction());
                //}
            } else if (GLOBAL_ACTION_KEY_SILENT.equals(actionKey)) {
                //if (mShowSilentToggle) {
                //    mItems.add(mSilentModeAction);
                //}
            } else if (GLOBAL_ACTION_KEY_USERS.equals(actionKey)) {
                //if (SystemProperties.getBoolean("fw.power_user_switcher", false)) {
                //    addUsersToMenu(mItems);
                //}
            } else if (GLOBAL_ACTION_KEY_SETTINGS.equals(actionKey)) {
                //mItems.add(getSettingsAction());
            } else if (GLOBAL_ACTION_KEY_LOCKDOWN.equals(actionKey)) {
                //mItems.add(getLockdownAction());
            } else if (GLOBAL_ACTION_KEY_VOICEASSIST.equals(actionKey)) {
                //mItems.add(getVoiceAssistAction());
            } else if (GLOBAL_ACTION_KEY_ASSIST.equals(actionKey)) {
                //mItems.add(getAssistAction());
            } else if (GLOBAL_ACTION_KEY_RESTART.equals(actionKey)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_REBOOT, 1) == 1) {
                    mItems.add(new RestartAction());
                }
            } else if (GLOBAL_ACTION_KEY_ADVANCED.equals(actionKey)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_REBOOT, 1) == 1 && Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_ADVANCED_REBOOT, 0) != 0) {
                    mItems.add(mShowAdvancedToggles);
                }
            } else if (GLOBAL_ACTION_KEY_SCREENSHOT.equals(actionKey)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_SCREENSHOT, 0) != 0) {
                    mItems.add(getScreenshotAction());
                }
            } else {
                Log.e(TAG, "Invalid global action key " + actionKey);
            }
            // Add here so we don't add more than one.
            addedKeys.add(actionKey);
        }

        /*if (mEmergencyAffordanceManager.needsEmergencyAffordance()) {
            mItems.add(getEmergencyAction());
        }*/

        mAdapter = new MyAdapter();

        OnItemLongClickListener onItemLongClickListener = new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                    long id) {
                final Action action = mAdapter.getItem(position);
                if (action instanceof LongPressAction) {
                    mDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    mDialog.dismiss();
                    return ((LongPressAction) action).onLongPress();
                }
                return false;
            }
        };
        ActionsDialog dialog = new ActionsDialog(mContext, this, mAdapter, onItemLongClickListener);
        dialog.setCanceledOnTouchOutside(false); // Handled by the custom class.
        dialog.setKeyguardShowing(mKeyguardShowing);

        dialog.setOnDismissListener(this);

        return dialog;
    }

    private final class PowerAction extends SinglePressAction implements LongPressAction {
        private PowerAction() {
            super(R.drawable.ic_lock_power_off,
                    R.string.global_action_power_off);
        }

        @Override
        public boolean onLongPress() {
            //UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            //if (!um.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT)) {
            //    mWindowManagerFuncs.reboot(true);
            //    return true;
            //}
            return false;
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return true;
        }

        @Override
        public void onPress() {
            // shutdown by making sure radio and power are handled accordingly.
            mWindowManagerFuncs.shutdown();
        }
    }

    private final class RestartAction extends SinglePressAction implements LongPressAction {
        private RestartAction() {
            super(R.drawable.ic_restart, com.android.internal.R.string.global_action_restart);
        }

        @Override
        public boolean onLongPress() {
            UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            if (!um.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT)) {
                mWindowManagerFuncs.reboot(true);
                return true;
            }
            return false;
        }

        @Override
        public boolean showDuringKeyguard() {
            boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.POWERMENU_LS_REBOOT, 1) == 1;
            return showlocked;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return true;
        }

        @Override
        public void onPress() {
            mWindowManagerFuncs.reboot(false);
        }
    }

    private Action getScreenshotAction() {
        return new SinglePressAction(com.android.systemui.R.drawable.ic_screenshot,
                com.android.systemui.R.string.global_action_screenshot) {

            @Override
            public void onPress() {
               mHandler.postDelayed(new Runnable() {
                   @Override
                    public void run() {
                        Intent intent = new Intent(Intent.ACTION_SCREENSHOT);
                        mContext.sendBroadcast(intent);
                    }
                }, mScreenshotDelay);
            }

            @Override
            public boolean showDuringKeyguard() {
                boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.POWERMENU_LS_SCREENSHOT, 0) == 1;
                return showlocked;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private final class ScreenrecordAction extends SinglePressAction implements LongPressAction {

        private ScreenrecordAction() {
            super(R.drawable.ic_lock_screenrecord, R.string.global_action_screenrecord);
        }

        @Override
        public void onPress() {
            takeScreenrecord();
        }

        @Override
        public boolean onLongPress() {
            return true;
        }

        @Override
        public boolean showDuringKeyguard() {
            boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.POWERMENU_LS_SCREENRECORD, 0) == 1;
            return showlocked;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return true;
        }
    }

    private final class FlashLightAction extends SinglePressAction implements LongPressAction {

        private FlashLightAction() {
            super(R.drawable.ic_lock_torch, R.string.global_action_flashlight);
        }

        @Override
        public void onPress() {
            toggleFlashlight();
        }

        @Override
        public boolean onLongPress() {
            return true;
        }

        @Override
        public boolean showDuringKeyguard() {
            boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.POWERMENU_LS_FLASHLIGHT, 0) == 1;
            return showlocked;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return true;
        }
    }

    private class BugReportAction extends SinglePressAction implements LongPressAction {

        public BugReportAction() {
            super(R.drawable.ic_lock_bugreport, R.string.bugreport_title);
        }

        @Override
        public void onPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            if (ActivityManager.isUserAMonkey()) {
                return;
            }
            // Add a little delay before executing, to give the
            // dialog a chance to go away before it takes a
            // screenshot.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Take an "interactive" bugreport.
                        MetricsLogger.action(mContext,
                                MetricsEvent.ACTION_BUGREPORT_FROM_POWER_MENU_INTERACTIVE);
                        ActivityManager.getService().requestBugReport(
                                ActivityManager.BUGREPORT_OPTION_INTERACTIVE);
                    } catch (RemoteException e) {
                    }
                }
            }, 500);
        }

        @Override
        public boolean onLongPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            if (ActivityManager.isUserAMonkey()) {
                return false;
            }
            try {
                // Take a "full" bugreport.
                MetricsLogger.action(mContext, MetricsEvent.ACTION_BUGREPORT_FROM_POWER_MENU_FULL);
                ActivityManager.getService().requestBugReport(
                        ActivityManager.BUGREPORT_OPTION_FULL);
            } catch (RemoteException e) {
            }
            return false;
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }

        @Override
        public String getStatus() {
            return mContext.getString(
                    R.string.bugreport_status,
                    Build.VERSION.RELEASE,
                    Build.ID);
        }
    }

    private Action getSettingsAction() {
        return new SinglePressAction(R.drawable.ic_settings,
                R.string.global_action_settings) {

            @Override
            public void onPress() {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getEmergencyAction() {
        return new SinglePressAction(R.drawable.emergency_icon,
                R.string.global_action_emergency) {
            @Override
            public void onPress() {
                mEmergencyAffordanceManager.performEmergencyCall();
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getAssistAction() {
        return new SinglePressAction(R.drawable.ic_action_assist_focused,
                R.string.global_action_assist) {
            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_ASSIST);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getVoiceAssistAction() {
        return new SinglePressAction(R.drawable.ic_voice_search,
                R.string.global_action_voice_assist) {
            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_VOICE_ASSIST);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getLockdownAction() {
        return new SinglePressAction(R.drawable.ic_lock_lock,
                R.string.global_action_lockdown) {

            @Override
            public void onPress() {
                new LockPatternUtils(mContext).requireCredentialEntry(UserHandle.USER_ALL);
                try {
                    WindowManagerGlobal.getWindowManagerService().lockNow(null);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error while trying to lock device.", e);
                }
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return false;
            }
        };
    }

    private UserInfo getCurrentUser() {
        try {
            return ActivityManager.getService().getCurrentUser();
        } catch (RemoteException re) {
            return null;
        }
    }

    private boolean isCurrentUserOwner() {
        UserInfo currentUser = getCurrentUser();
        return currentUser == null || currentUser.isPrimary();
    }

    private void addUsersToMenu(ArrayList<Action> items) {
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        if (um.isUserSwitcherEnabled()) {
            List<UserInfo> users = um.getUsers();
            UserInfo currentUser = getCurrentUser();
            for (final UserInfo user : users) {
                if (user.supportsSwitchToByUser()) {
                    boolean isCurrentUser = currentUser == null
                            ? user.id == 0 : (currentUser.id == user.id);
                    Drawable icon = user.iconPath != null ? Drawable.createFromPath(user.iconPath)
                            : null;
                    SinglePressAction switchToUser = new SinglePressAction(
                            R.drawable.ic_menu_cc, icon,
                            (user.name != null ? user.name : "Primary")
                                    + (isCurrentUser ? " \u2714" : "")) {
                        public void onPress() {
                            try {
                                ActivityManager.getService().switchUser(user.id);
                            } catch (RemoteException re) {
                                Log.e(TAG, "Couldn't switch user " + re);
                            }
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        public boolean showBeforeProvisioning() {
                            return false;
                        }
                    };
                    items.add(switchToUser);
                }
            }
        }
    }

    /**
     * functions needed for taking screen record.
     */
    final Object mScreenrecordLock = new Object();
    ServiceConnection mScreenrecordConnection = null;

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
    private void takeScreenrecord() {
        synchronized (mScreenrecordLock) {
            if (mScreenrecordConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName(SYSUI_PACKAGE,
                    SYSUI_SCREENRECORD_SERVICE);
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenrecordLock) {
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
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
                public void onServiceDisconnected(ComponentName name) {}
            };
            if (mContext.bindServiceAsUser(
                    intent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                mScreenrecordConnection = conn;
                // Screenrecord max duration is 30 minutes. Allow 31 minutes before killing
                // the service.
                mHandler.postDelayed(mScreenrecordTimeout, 31 * 60 * 1000);
            }
        }
    }

    private void prepareDialog() {
        refreshSilentMode();
        mAirplaneModeOn.updateState(mAirplaneState);
        mAdapter.notifyDataSetChanged();
        if (mShowSilentToggle) {
            IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mContext.registerReceiver(mRingerModeReceiver, filter);
        }
    }

    private void refreshSilentMode() {
        if (!mHasVibrator) {
            final boolean silentModeOn =
                    mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
            ((ToggleAction) mSilentModeAction).updateState(
                    silentModeOn ? ToggleAction.State.On : ToggleAction.State.Off);
        }
    }

    /** {@inheritDoc} */
    public void onDismiss(DialogInterface dialog) {
        mWindowManagerFuncs.onGlobalActionsHidden();
        if (mShowSilentToggle) {
            try {
                mContext.unregisterReceiver(mRingerModeReceiver);
            } catch (IllegalArgumentException ie) {
                // ignore this
                Log.w(TAG, ie);
            }
        }
    }

    /** {@inheritDoc} */
    public void onClick(DialogInterface dialog, int which) {
        Action item = mAdapter.getItem(which);
        if (!(item instanceof SilentModeTriStateAction)
                && !(item instanceof AdvancedAction)) {
            dialog.dismiss();
        }
        item.onPress();
    }

    /**
     * The adapter used for the list within the global actions dialog, taking
     * into account whether the keyguard is showing via
     * {@link com.android.systemui.globalactions.GlobalActionsDialog#mKeyguardShowing} and whether
     * the device is provisioned
     * via {@link com.android.systemui.globalactions.GlobalActionsDialog#mDeviceProvisioned}.
     */
    private class MyAdapter extends BaseAdapter {

        public int getCount() {
            int count = 0;

            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);

                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                count++;
            }
            return count;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        public Action getItem(int position) {

            int filteredPos = 0;
            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);
                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                if (filteredPos == position) {
                    return action;
                }
                filteredPos++;
            }

            throw new IllegalArgumentException("position " + position
                    + " out of range of showable actions"
                    + ", filtered count=" + getCount()
                    + ", keyguardshowing=" + mKeyguardShowing
                    + ", provisioned=" + mDeviceProvisioned);
        }


        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent, false);
        }

        public View getView(int position, View convertView, ViewGroup parent, boolean noDivider) {
            Action action = getItem(position);
            View view = action.create(mContext, convertView, parent, LayoutInflater.from(mContext));
            if (!noDivider && position == 99) {
                HardwareUiLayout.get(parent).setDivisionView(view);
            }
            return view;
        }
    }

    // note: the scheme below made more sense when we were planning on having
    // 8 different things in the global actions dialog.  seems overkill with
    // only 3 items now, but may as well keep this flexible approach so it will
    // be easy should someone decide at the last minute to include something
    // else, such as 'enable wifi', or 'enable bluetooth'

    /**
     * What each item in the global actions dialog must be able to support.
     */
    private interface Action {
        /**
         * @return Text that will be announced when dialog is created.  null
         * for none.
         */
        CharSequence getLabelForAccessibility(Context context);

        View create(Context context, View convertView, ViewGroup parent, LayoutInflater inflater);

        void onPress();

        /**
         * @return whether this action should appear in the dialog when the keygaurd
         * is showing.
         */
        boolean showDuringKeyguard();

        /**
         * @return whether this action should appear in the dialog before the
         * device is provisioned.
         */
        boolean showBeforeProvisioning();

        boolean isEnabled();
    }

    /**
     * An action that also supports long press.
     */
    private interface LongPressAction extends Action {
        boolean onLongPress();
    }

    /**
     * A single press action maintains no state, just responds to a press
     * and takes an action.
     */
    private static abstract class SinglePressAction implements Action {
        private final int mIconResId;
        private final Drawable mIcon;
        private final int mMessageResId;
        private final CharSequence mMessage;

        protected SinglePressAction(int iconResId, int messageResId) {
            mIconResId = iconResId;
            mMessageResId = messageResId;
            mMessage = null;
            mIcon = null;
        }

        protected SinglePressAction(int iconResId, Drawable icon, CharSequence message) {
            mIconResId = iconResId;
            mMessageResId = 0;
            mMessage = message;
            mIcon = icon;
        }

        public boolean isEnabled() {
            return true;
        }

        public String getStatus() {
            return null;
        }

        abstract public void onPress();

        public CharSequence getLabelForAccessibility(Context context) {
            if (mMessage != null) {
                return mMessage;
            } else {
                return context.getString(mMessageResId);
            }
        }

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            View v = inflater.inflate(com.android.systemui.R.layout.global_actions_item, parent,
                    false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);

            TextView statusView = (TextView) v.findViewById(R.id.status);
            final String status = getStatus();
            if (!TextUtils.isEmpty(status)) {
                statusView.setText(status);
            } else {
                statusView.setVisibility(View.GONE);
            }
            if (mIcon != null) {
                icon.setImageDrawable(mIcon);
                icon.setScaleType(ScaleType.CENTER_CROP);
            } else if (mIconResId != 0) {
                icon.setImageDrawable(context.getDrawable(mIconResId));
            }
            if (mMessage != null) {
                messageView.setText(mMessage);
            } else {
                messageView.setText(mMessageResId);
            }

            return v;
        }
    }

    /**
     * A toggle action knows whether it is on or off, and displays an icon
     * and status message accordingly.
     */
    private static abstract class ToggleAction implements Action {

        enum State {
            Off(false),
            TurningOn(true),
            TurningOff(true),
            On(false);

            private final boolean inTransition;

            State(boolean intermediate) {
                inTransition = intermediate;
            }

            public boolean inTransition() {
                return inTransition;
            }
        }

        public String getStatus() {
            return null;
        }

        protected State mState = State.Off;

        // prefs
        protected int mEnabledIconResId;
        protected int mDisabledIconResid;
        protected int mMessageResId;

        /**
         * @param enabledIconResId           The icon for when this action is on.
         * @param disabledIconResid          The icon for when this action is off.
         * @param message                    The general information message, e.g 'Silent Mode'
         */

        public ToggleAction(int enabledIconResId, int disabledIconResid, int message) {
            mEnabledIconResId = enabledIconResId;
            mDisabledIconResid = disabledIconResid;
            mMessageResId = message;
        }

        /**
         * Override to make changes to resource IDs just before creating the
         * View.
         */
        void willCreate() {

        }

        @Override
        public CharSequence getLabelForAccessibility(Context context) {
            return context.getString(mMessageResId);
        }

        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            willCreate();

            View v = inflater.inflate(R
                    .layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);
            TextView statusView = (TextView) v.findViewById(R.id.status);
            final boolean enabled = isEnabled();

            if (messageView != null) {
                messageView.setText(mMessageResId);
                messageView.setEnabled(enabled);
            }

            boolean on = ((mState == State.On) || (mState == State.TurningOn));
            if (icon != null) {
                icon.setImageDrawable(context.getDrawable(
                        (on ? mEnabledIconResId : mDisabledIconResid)));
                icon.setEnabled(enabled);
            }

            final String status = getStatus();
            if (!TextUtils.isEmpty(status)) {
                statusView.setText(status);
            } else {
                statusView.setVisibility(View.GONE);
            v.setEnabled(enabled);
            }

            return v;
        }

        public final void onPress() {
            if (mState.inTransition()) {
                Log.w(TAG, "shouldn't be able to toggle when in transition");
                return;
            }

            final boolean nowOn = !(mState == State.On);
            onToggle(nowOn);
            changeStateFromPress(nowOn);
        }

        public boolean isEnabled() {
            return !mState.inTransition();
        }

        /**
         * Implementations may override this if their state can be in on of the intermediate
         * states until some notification is received (e.g airplane mode is 'turning off' until
         * we know the wireless connections are back online
         *
         * @param buttonOn Whether the button was turned on or off
         */
        protected void changeStateFromPress(boolean buttonOn) {
            mState = buttonOn ? State.On : State.Off;
        }

        abstract void onToggle(boolean on);

        public void updateState(State state) {
            mState = state;
        }
    }

    /**
     * A toggle action knows whether it is on or off, and displays an icon
     * and status message accordingly.
     */
    private static abstract class AdvancedAction implements Action, LongPressAction {

        protected int mActionType;
        protected int mIconResid;
        protected int mMessageResid;
        protected Handler mRefresh;
        protected GlobalActionsManager mWmFuncs;
        private Context mContext;

        public AdvancedAction(
                int actionType,
                int iconResid,
                int messageResid,
                GlobalActionsManager funcs,
                Handler handler) {
            mActionType = actionType;
            mIconResid = iconResid;
            mMessageResid = messageResid;
            mRefresh = handler;
            mWmFuncs = funcs;
        }

        @Override
        public CharSequence getLabelForAccessibility(Context context) {
            return context.getString(mMessageResid);
        }

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            mContext = context;
            View v = inflater.inflate(com.android.systemui.R.layout.global_actions_item, parent,
                    false);

            TextView statusView = (TextView) v.findViewById(R.id.status);
            final String status = getStatus();
            if (!TextUtils.isEmpty(status)) {
                statusView.setText(status);
            } else {
                statusView.setVisibility(View.GONE);
            }

            TextView messageView = (TextView) v.findViewById(R.id.message);
            if (messageView != null) {
                messageView.setText(mMessageResid);
            }
            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            if (icon != null) {
                icon.setImageDrawable(mContext.getDrawable((mIconResid)));
            }

            return v;
        }

        @Override
        public final void onPress() {
            if (mActionType == SHOW_TOGGLES_BUTTON) {
                mRefresh.sendEmptyMessage(MESSAGE_SHOW_ADVANCED_TOGGLES);
            } else {
                triggerAction(mActionType, mRefresh, mWmFuncs, mContext);
            }
        }

        @Override
        public boolean onLongPress() {
            return true;
        }

        public boolean isEnabled() {
            return true;
        }

        public String getStatus() {
            return null;
        }
    }

    private static void triggerAction(int type, Handler h, GlobalActionsManager funcs, Context ctx) {
        switch (type) {
            case RESTART_HOT_BUTTON:
                h.sendEmptyMessage(MESSAGE_DISMISS);
                doHotReboot();
                break;
            case RESTART_RECOVERY_BUTTON:
                h.sendEmptyMessage(MESSAGE_DISMISS);
                funcs.advancedReboot(PowerManager.REBOOT_RECOVERY);
                break;
            case RESTART_BOOTLOADER_BUTTON:
                h.sendEmptyMessage(MESSAGE_DISMISS);
                funcs.advancedReboot(PowerManager.REBOOT_BOOTLOADER);
                break;
            case RESTART_UI_BUTTON:
                /* no time and need to dismiss the dialog here, just kill systemui straight after telling to
                policy/GlobalActions that we hid the dialog within the kill action itself so its onStatusBarConnectedChanged
                won't show the LegacyGlobalActions after systemui restart
                */
                funcs.onGlobalActionsHidden();
                restartSystemUI(ctx);
                break;
            default:
                break;
        }
    }

    private class SilentModeToggleAction extends ToggleAction {
        public SilentModeToggleAction() {
            super(R.drawable.ic_audio_vol_mute,
                    R.drawable.ic_audio_vol,
                    R.string.global_action_toggle_silent_mode);
        }

        void onToggle(boolean on) {
            if (on) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private static class SilentModeTriStateAction implements Action, View.OnClickListener {

        private final int[] ITEM_IDS = {R.id.option1, R.id.option2, R.id.option3};

        private final AudioManager mAudioManager;
        private final Handler mHandler;
        private final Context mContext;

        SilentModeTriStateAction(Context context, AudioManager audioManager, Handler handler) {
            mAudioManager = audioManager;
            mHandler = handler;
            mContext = context;
        }

        private int ringerModeToIndex(int ringerMode) {
            // They just happen to coincide
            return ringerMode;
        }

        private int indexToRingerMode(int index) {
            // They just happen to coincide
            return index;
        }

        @Override
        public CharSequence getLabelForAccessibility(Context context) {
            return null;
        }

        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            View v = inflater.inflate(R.layout.global_actions_silent_mode, parent, false);

            int selectedIndex = ringerModeToIndex(mAudioManager.getRingerMode());
            for (int i = 0; i < 3; i++) {
                View itemView = v.findViewById(ITEM_IDS[i]);
                itemView.setSelected(selectedIndex == i);
                // Set up click handler
                itemView.setTag(i);
                itemView.setOnClickListener(this);
            }
            return v;
        }

        public void onPress() {
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        public boolean showBeforeProvisioning() {
            return false;
        }

        public boolean isEnabled() {
            return true;
        }

        void willCreate() {
        }

        public void onClick(View v) {
            if (!(v.getTag() instanceof Integer)) return;

            int index = (Integer) v.getTag();
            mAudioManager.setRingerMode(indexToRingerMode(index));
            mHandler.sendEmptyMessageDelayed(MESSAGE_DISMISS, DIALOG_DISMISS_DELAY);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (!SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
                    mHandler.sendEmptyMessage(MESSAGE_DISMISS);
                }
            } else if (TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED.equals(action)) {
                // Airplane mode can be changed after ECM exits if airplane toggle button
                // is pressed during ECM mode
                if (!(intent.getBooleanExtra("PHONE_IN_ECM_STATE", false)) &&
                        mIsWaitingForEcmExit) {
                    mIsWaitingForEcmExit = false;
                    changeAirplaneModeSystemSetting(true);
                }
            }
        }
    };

    private BroadcastReceiver mRingerModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                mHandler.sendEmptyMessage(MESSAGE_REFRESH);
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onAirplaneModeChanged();
        }
    };

    private static final int MESSAGE_DISMISS = 0;
    private static final int MESSAGE_REFRESH = 1;
    private static final int MESSAGE_SHOW = 2;
    private static final int MESSAGE_SHOW_ADVANCED_TOGGLES = 3;
    private static final int DIALOG_DISMISS_DELAY = 300; // ms

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_DISMISS:
                    if (mDialog != null) {
                        mDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                        mDialog.dismiss();
                        mDialog = null;
                    }
                    break;
                case MESSAGE_REFRESH:
                    refreshSilentMode();
                    mAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_SHOW:
                    handleShow();
                    break;
                case MESSAGE_SHOW_ADVANCED_TOGGLES:
                    mAdapter.notifyDataSetChanged();
                    addNewItems();
                    mDialog.refreshList();
                    break;
            }
        }
    };

    private void addNewItems() {
        mItems.clear();
        mItems.add(mRestartHot);
        mItems.add(mRestartRecovery);
        mItems.add(mRestartBootloader);
        mItems.add(mRestartSystemUI);
    }

    private ToggleAction.State getUpdatedAirplaneToggleState() {
        return (Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1) ?
                ToggleAction.State.On : ToggleAction.State.Off;
    }

    private void onAirplaneModeChanged() {
        // Let the service state callbacks handle the state.
        if (mHasTelephony) return;

        mAirplaneState = getUpdatedAirplaneToggleState();
        mAirplaneModeOn.updateState(mAirplaneState);
    }

    /**
     * Change the airplane mode system setting
     */
    private void changeAirplaneModeSystemSetting(boolean on) {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                on ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        if (!mHasTelephony) {
            mAirplaneState = on ? ToggleAction.State.On : ToggleAction.State.Off;
        }
    }

    private static final class ActionsDialog extends Dialog implements DialogInterface,
            ColorExtractor.OnColorsChangedListener {

        private final Context mContext;
        private final MyAdapter mAdapter;
        private final LinearLayout mListView;
        private final HardwareUiLayout mHardwareLayout;
        private final OnClickListener mClickListener;
        private final OnItemLongClickListener mLongClickListener;
        private final GradientDrawable mGradientDrawable;
        private final ColorExtractor mColorExtractor;
        private boolean mKeyguardShowing;

        public ActionsDialog(Context context, OnClickListener clickListener, MyAdapter adapter,
                OnItemLongClickListener longClickListener) {
            super(context, com.android.systemui.R.style.Theme_SystemUI_Dialog_GlobalActions);
            mContext = context;
            mAdapter = adapter;
            mClickListener = clickListener;
            mLongClickListener = longClickListener;
            mGradientDrawable = new GradientDrawable(mContext);
            mColorExtractor = Dependency.get(SysuiColorExtractor.class);

            // Window initialization
            Window window = getWindow();
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.setBackgroundDrawable(mGradientDrawable);
            window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);

            setContentView(com.android.systemui.R.layout.global_actions_wrapped);
            mListView = findViewById(android.R.id.list);
            mHardwareLayout = HardwareUiLayout.get(mListView);
            mHardwareLayout.setOutsideTouchListener(view -> dismiss());
        }

        private void updateList(boolean noDivider) {
            mListView.removeAllViews();
            for (int i = 0; i < mAdapter.getCount(); i++) {
                View v = mAdapter.getView(i, null, mListView, noDivider);
                final int pos = i;
                v.setOnClickListener(view -> mClickListener.onClick(this, pos));
                v.setOnLongClickListener(view ->
                        mLongClickListener.onItemLongClick(null, v, pos, 0));
                mListView.addView(v);
            }
        }

        public void refreshList() {
            updateList(true);
            // we need to recreate the HardwareBgDrawable
            HardwareUiLayout.get(mListView).updateSettings();
        }

        @Override
        protected void onStart() {
            super.setCanceledOnTouchOutside(true);
            super.onStart();
            updateList(false);

            Point displaySize = new Point();
            mContext.getDisplay().getRealSize(displaySize);
            mColorExtractor.addOnColorsChangedListener(this);
            mGradientDrawable.setScreenSize(displaySize.x, displaySize.y);
            GradientColors colors = mColorExtractor.getColors(mKeyguardShowing ?
                    WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM);
            mGradientDrawable.setColors(colors, false);
        }

        @Override
        protected void onStop() {
            super.onStop();
            mColorExtractor.removeOnColorsChangedListener(this);
        }

        @Override
        public void show() {
            super.show();
            mGradientDrawable.setAlpha(0);
            mHardwareLayout.setTranslationX(getAnimTranslation());
            mHardwareLayout.setAlpha(0);
            mHardwareLayout.animate()
                    .alpha(1)
                    .translationX(0)
                    .setDuration(300)
                    .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                    .setUpdateListener(animation -> {
                        int alpha = (int) ((Float) animation.getAnimatedValue()
                                * ScrimController.GRADIENT_SCRIM_ALPHA * 255);
                        mGradientDrawable.setAlpha(alpha);
                    })
                    .withEndAction(() -> getWindow().getDecorView().requestAccessibilityFocus())
                    .start();
        }

        @Override
        public void dismiss() {
            mHardwareLayout.setTranslationX(0);
            mHardwareLayout.setAlpha(1);
            mHardwareLayout.animate()
                    .alpha(0)
                    .translationX(getAnimTranslation())
                    .setDuration(300)
                    .withEndAction(() -> super.dismiss())
                    .setInterpolator(new LogAccelerateInterpolator())
                    .setUpdateListener(animation -> {
                        int alpha = (int) ((1f - (Float) animation.getAnimatedValue())
                                * ScrimController.GRADIENT_SCRIM_ALPHA * 255);
                        mGradientDrawable.setAlpha(alpha);
                    })
                    .start();
        }

        private float getAnimTranslation() {
            return getContext().getResources().getDimension(
                    com.android.systemui.R.dimen.global_actions_panel_width) / 2;
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                for (int i = 0; i < mAdapter.getCount(); ++i) {
                    CharSequence label =
                            mAdapter.getItem(i).getLabelForAccessibility(getContext());
                    if (label != null) {
                        event.getText().add(label);
                    }
                }
            }
            return super.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public void onColorsChanged(ColorExtractor extractor, int which) {
            if (mKeyguardShowing) {
                if ((WallpaperManager.FLAG_LOCK & which) != 0) {
                    mGradientDrawable.setColors(extractor.getColors(WallpaperManager.FLAG_LOCK));
                }
            } else {
                if ((WallpaperManager.FLAG_SYSTEM & which) != 0) {
                    mGradientDrawable.setColors(extractor.getColors(WallpaperManager.FLAG_SYSTEM));
                }
            }
        }

        public void setKeyguardShowing(boolean keyguardShowing) {
            mKeyguardShowing = keyguardShowing;
        }
    }

    public static void restartSystemUI(Context ctx) {
        Process.killProcess(Process.myPid());
    }

    private static void doHotReboot() {
        try {
            final IActivityManager am =
                  ActivityManagerNative.asInterface(ServiceManager.checkService("activity"));
            if (am != null) {
                am.restart();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "failure trying to perform hot reboot", e);
        }
    }

    public void updateOnTheGoActions() {
 	     ContentResolver resolver = mContext.getContentResolver();
         boolean showOnTheGo = Settings.System.getInt(
                 resolver, Settings.System.POWER_MENU_ONTHEGO_ENABLED, 0) == 1;
         if (showOnTheGo) {
             mItems.add(
                 new SinglePressAction(com.android.internal.R.drawable.ic_lock_onthego,
                         R.string.global_action_onthego) {
 
                         public void onPress() {
                             OnTheGoActions.processAction(mContext,
                                     OnTheGoActions.ACTION_ONTHEGO_TOGGLE);
                         }
 
                         public boolean onLongPress() {
                             return false;
                         }
 
                         public boolean showDuringKeyguard() {
                              boolean showlocked = Settings.System.getInt(mContext.getContentResolver(),
                                      Settings.System.POWERMENU_LS_ONTHEGO, 0) == 1;
                             return showlocked;
                         }
 
                         public boolean showBeforeProvisioning() {
                             return true;
                         }
                     }
             );
         }
    }

 
    private void startOnTheGo() {
        final ComponentName cn = new ComponentName("com.android.systemui",
                "com.android.systemui.rr.onthego.OnTheGoService");
        final Intent startIntent = new Intent();
        startIntent.setComponent(cn);
        startIntent.setAction("start");
        mContext.startService(startIntent);
    }

    public void toggleFlashlight() {
        if (mFlashlightController != null) {
            mFlashlightController.initFlashLight();
            if (mFlashlightController.hasFlashlight() && mFlashlightController.isAvailable()) {
                mFlashlightController.setFlashlight(!mFlashlightController.isEnabled());
            }
        }
    }

   private void checkSettings() {
        mScreenshotDelay = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_DELAY, 100);
    }

}
