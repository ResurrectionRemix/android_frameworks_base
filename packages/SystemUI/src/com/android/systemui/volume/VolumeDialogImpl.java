/*
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

package com.android.systemui.volume;

import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.media.AudioManager.STREAM_ACCESSIBILITY;
import static android.media.AudioManager.STREAM_ALARM;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;
import static android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.systemui.volume.Events.DISMISS_REASON_SETTINGS_CLICKED;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.AppTrackData;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.text.InputFilter;
import android.util.Log;
import android.util.Slog;
import android.util.SparseBooleanArray;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settingslib.Utils;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.plugins.VolumeDialogController;
import com.android.systemui.plugins.VolumeDialogController.State;
import com.android.systemui.plugins.VolumeDialogController.StreamState;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.phone.ExpandableIndicator;
import com.android.systemui.statusbar.policy.AccessibilityManagerWrapper;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.tuner.TunerService;

import lineageos.providers.LineageSettings;
import com.android.systemui.rr.RRMusic;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual presentation of the volume dialog.
 *
 * A client of VolumeDialogControllerImpl and its state model.
 *
 * Methods ending in "H" must be called on the (ui) handler.
 */
public class VolumeDialogImpl implements VolumeDialog,
        ConfigurationController.ConfigurationListener, TunerService.Tunable,
        LocalMediaManager.DeviceCallback {
    private static final String TAG = Util.logTag(VolumeDialogImpl.class);

    private static final long USER_ATTEMPT_GRACE_PERIOD = 1000;
    private static final int UPDATE_ANIMATION_DURATION = 80;

    public static final String VOLUME_PANEL_ON_LEFT =
            "lineagesecure:" + LineageSettings.Secure.VOLUME_PANEL_ON_LEFT;
    public static final String AUDIO_PANEL_VIEW_MEDIA =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_MEDIA;
    public static final String AUDIO_PANEL_VIEW_RINGER =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_RINGER;
    public static final String AUDIO_PANEL_VIEW_NOTIFICATION =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_NOTIFICATION;
    public static final String AUDIO_PANEL_VIEW_ALARM =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_ALARM;
    public static final String AUDIO_PANEL_VIEW_VOICE =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_VOICE;
    public static final String AUDIO_PANEL_VIEW_BT_SCO =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_BT_SCO;
    public static final String AUDIO_PANEL_VIEW_TIMEOUT =
            "system:" + Settings.System.AUDIO_PANEL_VIEW_TIMEOUT;
    public static final String RINGER_VOLUME_PANEL =
            "system:" + Settings.System.SHOW_RINGER_VOLUME_PANEL;
    public static final String APP_VOLUME =
            "system:" + Settings.System.SHOW_APP_VOLUME;
    public static final String TINT_INACTIVE_SLIDER =
            "system:" + Settings.System.TINT_INACTIVE_SLIDER;
    public static final String VOLUME_ANIMATIONS =
            "system:" + Settings.System.VOLUME_PANEL_ANIMATION;

    static final int DIALOG_TIMEOUT_MILLIS = 3000;
    static final int DIALOG_SAFETYWARNING_TIMEOUT_MILLIS = 5000;
    static final int DIALOG_ODI_CAPTIONS_TOOLTIP_TIMEOUT_MILLIS = 5000;
    static final int DIALOG_HOVERING_TIMEOUT_MILLIS = 16000;
    static final int DIALOG_SHOW_ANIMATION_DURATION = 300;
    static final int DIALOG_HIDE_ANIMATION_DURATION = 250;

    private static final int SLIDER_PROGRESS_ALPHA_ACTIVE = 100;
    private static final int SLIDER_PROGRESS_ALPHA_ACTIVE_DARK = 90;
    private static final int SLIDER_PROGRESS_ALPHA = 50;
    private static final int SLIDER_PROGRESS_ALPHA_DARK = 40;

    private final Context mContext;
    private final H mHandler = new H();
    private final VolumeDialogController mController;
    private final DeviceProvisionedController mDeviceProvisionedController;

    private Window mWindow;
    private CustomDialog mDialog;
    private ViewGroup mDialogView;
    private ViewGroup mDialogRowsView;
    private ViewGroup mRinger;
    private ViewGroup mMediaOutputView;
    private ViewGroup mMediaOutputScrollView;
    private ViewGroup mMediaButtonView;
    private TextView mMediaTitleText;
    private ImageButton mMediaButton;
    private ImageButton mRingerIcon;
    private ViewGroup mODICaptionsView;
    private CaptionsToggleImageButton mODICaptionsIcon;
    private View mExpandRowsView;
    private ExpandableIndicator mExpandRows;
    private FrameLayout mZenIcon;
    private final List<VolumeRow> mRows = new ArrayList<>();
    private final List<VolumeRow> mAppRows = new ArrayList<>();
    private ConfigurableTexts mConfigurableTexts;
    private final SparseBooleanArray mDynamic = new SparseBooleanArray();
    private final KeyguardManager mKeyguard;
    private final ActivityManager mActivityManager;
    private final AccessibilityManagerWrapper mAccessibilityMgr;
    private final Object mSafetyWarningLock = new Object();
    private final Accessibility mAccessibility = new Accessibility();

    private final ColorFilter mAppIconMuteColorFilter;

    private boolean mShowing;
    private boolean mShowA11yStream;

    private int mActiveStream;
    private int mPrevActiveStream;
    private boolean mAutomute = VolumePrefs.DEFAULT_ENABLE_AUTOMUTE;
    private boolean mSilentMode = VolumePrefs.DEFAULT_ENABLE_SILENT_MODE;
    private State mState;
    private SafetyWarningDialog mSafetyWarning;
    private boolean mHovering = false;
    private boolean mShowActiveStreamOnly;
    private boolean mConfigChanged = false;
    private boolean mODIServiceComponentEnabled;
    private boolean mPendingOdiCaptionsTooltip;
    private boolean mHasSeenODICaptionsTooltip;
    private ViewStub mODICaptionsTooltipViewStub;
    private View mODICaptionsTooltipView = null;
    private LocalMediaManager mLocalMediaManager;
    private Animator mCurrAnimator;

    // Volume panel placement left or right
    private boolean mVolumePanelOnLeft;
    private RRMusic mMusicText;
    private NotificationMediaManager mMediaManager;

    private boolean mMediaShowing;
    private boolean mRingerShowing;
    private boolean mNotificationShowing;
    private boolean mAlarmShowing;
    private boolean mVoiceShowing;
    private boolean mBTSCOShowing;
    private int mTimeOutDesired, mTimeOut;
    private int mVolumeAlpha;
    private boolean mShowRinger;
    private boolean mTintInActive;
    private boolean mAppVolume;
    private int mVolumeAnimations;

    private boolean mHasAlertSlider;
    private boolean mDarkMode;
    private boolean mVibrateOnSlider;

    private boolean mExpanded;
    private boolean mShowingMediaDevices;

    private float mElevation;
    private float mHeight, mWidth, mSpacer;

    private final List<MediaOutputRow> mMediaOutputRows = new ArrayList<>();
    private final List<MediaDevice> mMediaDevices = new ArrayList<>();

    public VolumeDialogImpl(Context context) {
        mContext =
                new ContextThemeWrapper(context, R.style.qs_theme);
        mController = Dependency.get(VolumeDialogController.class);
        mKeyguard = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mAccessibilityMgr = Dependency.get(AccessibilityManagerWrapper.class);
        mDeviceProvisionedController = Dependency.get(DeviceProvisionedController.class);
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mShowActiveStreamOnly = showActiveStreamOnly();
        mHasSeenODICaptionsTooltip =
                Prefs.getBoolean(context, Prefs.Key.HAS_SEEN_ODI_CAPTIONS_TOOLTIP, false);
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, VOLUME_PANEL_ON_LEFT);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_MEDIA);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_RINGER);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_NOTIFICATION);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_ALARM);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_VOICE);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_BT_SCO);
        tunerService.addTunable(this, AUDIO_PANEL_VIEW_TIMEOUT);
        tunerService.addTunable(this, RINGER_VOLUME_PANEL);
        tunerService.addTunable(this, APP_VOLUME);
        tunerService.addTunable(this, TINT_INACTIVE_SLIDER);
        tunerService.addTunable(this, VOLUME_ANIMATIONS);
        mHasAlertSlider = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_hasAlertSlider);
        mVibrateOnSlider = mContext.getResources().getBoolean(R.bool.config_vibrateOnIconAnimation);
        mElevation = mContext.getResources().getDimension(R.dimen.volume_dialog_elevation);
        mSpacer = mContext.getResources().getDimension(R.dimen.volume_dialog_row_spacer);

        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        mAppIconMuteColorFilter = new ColorMatrixColorFilter(colorMatrix);

        setDarkMode();

        mHandler.postDelayed(() -> {
            if (mLocalMediaManager == null) {
                mLocalMediaManager = new LocalMediaManager(mContext, TAG, null);
                mLocalMediaManager.registerCallback(VolumeDialogImpl.this);
            }
        }, 3000);

    }

    @Override
    public void onUiModeChanged() {
        mContext.getTheme().applyStyle(mContext.getThemeResId(), true);
        removeAllMediaOutputRows();
        setDarkMode();
    }

    private void setDarkMode() {
        final int nightModeFlag = mContext.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK;

        switch (nightModeFlag) {
            case Configuration.UI_MODE_NIGHT_YES:
                mDarkMode = true;
                break;
            case Configuration.UI_MODE_NIGHT_NO:
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                mDarkMode = false;
                break;
        }
    }

    public void init(int windowType, Callback callback) {
        initDialog();

        mAccessibility.init();

        mController.addCallback(mControllerCallbackH, mHandler);
        mController.getState();

        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    public void initDependencies(NotificationMediaManager mediaManager) {
        mMediaManager = mediaManager;
    }

    @Override
    public void destroy() {
        mController.removeCallback(mControllerCallbackH);
        mHandler.removeCallbacksAndMessages(null);
        Dependency.get(ConfigurationController.class).removeCallback(this);
        if (mLocalMediaManager != null) {
            mLocalMediaManager.unregisterCallback(this);
        }
    } 

    private void initDialog() {
        mDialog = new CustomDialog(mContext);

        // Gravitate various views left/right depending on panel placement setting.
        final int panelGravity = mVolumePanelOnLeft ? Gravity.LEFT : Gravity.RIGHT;
        mConfigurableTexts = new ConfigurableTexts(mContext);
        mHovering = false;
        mShowing = false;
        mExpanded = false;
        mWindow = mDialog.getWindow();
        mWindow.requestFeature(Window.FEATURE_NO_TITLE);
        mWindow.getDecorView();
        mWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        mWindow.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        mWindow.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        updateAnimations();
        final WindowManager.LayoutParams lp = mWindow.getAttributes();

        lp.format = PixelFormat.TRANSLUCENT;
        lp.setTitle(VolumeDialogImpl.class.getSimpleName());
        lp.width = MATCH_PARENT;
        lp.height = WRAP_CONTENT;
        if(!isAudioPanelOnLeftSide()){
            lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        } else {
            lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        }
        mWindow.setAttributes(lp);

        mDialog.setContentView(R.layout.volume_dialog);
        mMusicText = mDialog.findViewById(R.id.music_main);
        if (mMediaManager == null) {
            mMediaManager = Dependency.get(NotificationMediaManager.class);
        } else {
            mMusicText.initDependencies(mMediaManager, mContext);
        }
        mDialogView = mDialog.findViewById(R.id.volume_dialog);
        mDialogView.setLayoutDirection(
                isAudioPanelOnLeftSide() ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        mMusicText.setLayoutDirection(
                isAudioPanelOnLeftSide() ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_LTR);
        mDialogView.setAlpha(0);
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnShowListener(dialog -> {
            if (!isLandscape()) {
                mDialogView.setTranslationX(
                        (mDialogView.getWidth() / 2.0f) * (!isAudioPanelOnLeftSide() ? 1 : -1));
            }
            mDialogView.setAlpha(0);
            mDialogView.animate()
                    .alpha(1)
                    .translationX(0)
                    .setDuration(DIALOG_SHOW_ANIMATION_DURATION)
                    .setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator())
                    .withEndAction(() -> {
                        if (!Prefs.getBoolean(mContext, Prefs.Key.TOUCHED_RINGER_TOGGLE, false)) {
                            if (mRingerIcon != null) {
                                mRingerIcon.postOnAnimationDelayed(
                                        getSinglePressFor(mRingerIcon), 1500);
                            }
                        }
                    })
                    .start();
            if (!isLandscape()) {
                mMusicText.setTranslationX((mMusicText.getWidth() / 2.0f)*(isAudioPanelOnLeftSide() ? -1 : 1));
            }
            mMusicText.setAlpha(0);
            mMusicText.animate()
                    .alpha(1)
                    .translationX(0)
                    .setDuration(DIALOG_SHOW_ANIMATION_DURATION)
                    .setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator())
                    .start();
            mMusicText.update();
            if (mLocalMediaManager != null) {
                mLocalMediaManager.startScan();
            }
        });

        mDialogView.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            mHovering = (action == MotionEvent.ACTION_HOVER_ENTER)
                    || (action == MotionEvent.ACTION_HOVER_MOVE);
            rescheduleTimeoutH();
            return true;
        });

        mDialogRowsView = mDialog.findViewById(R.id.volume_dialog_rows);
        mRinger = mDialog.findViewById(R.id.ringer);
        if (mRinger != null) {
            mRingerIcon = mRinger.findViewById(R.id.ringer_icon);
            mZenIcon = mRinger.findViewById(R.id.dnd_icon);
            // Apply ringer layout gravity based on panel left/right setting
            // Layout type is different between landscape/portrait.
            setLayoutGravity(mRinger.getLayoutParams(), panelGravity);
            Util.setVisOrGone(mRinger, mShowRinger);
        }

        mODICaptionsView = mDialog.findViewById(R.id.odi_captions);
        if (mODICaptionsView != null) {
            mODICaptionsIcon = mODICaptionsView.findViewById(R.id.odi_captions_icon);
            setLayoutGravity(mODICaptionsView.getLayoutParams(), panelGravity);
        }
        mODICaptionsTooltipViewStub = mDialog.findViewById(R.id.odi_captions_tooltip_stub);
        if (mHasSeenODICaptionsTooltip && mODICaptionsTooltipViewStub != null) {
            mDialogView.removeView(mODICaptionsTooltipViewStub);
            mODICaptionsTooltipViewStub = null;
        }

        mExpandRowsView = mDialog.findViewById(R.id.expandable_indicator_container);
        mExpandRows = mExpandRowsView.findViewById(R.id.expandable_indicator);
        mExpandRows.setScaleY(isAudioPanelOnLeftSide() ? -1f : 1f);

        mMediaOutputView = mDialog.findViewById(R.id.media_output_container);
        mMediaOutputScrollView = mDialog.findViewById(R.id.media_output_scroller);
        mMediaButtonView = mDialog.findViewById(R.id.media_button_view);
        mMediaButton = mDialog.findViewById(R.id.media_button);
        mMediaTitleText = mDialog.findViewById(R.id.media_output_title);

        if (mRows.isEmpty()) {
            if (!AudioSystem.isSingleVolume(mContext)) {
                addRow(STREAM_ACCESSIBILITY, R.drawable.ic_volume_accessibility,
                        R.drawable.ic_volume_accessibility, true, false);
            }
            addRow(AudioManager.STREAM_MUSIC,
                    R.drawable.ic_volume_media, R.drawable.ic_volume_media_mute, true, true);
            if (!AudioSystem.isSingleVolume(mContext)) {
                if (Util.isVoiceCapable(mContext)) {
                    addRow(AudioManager.STREAM_RING, R.drawable.ic_volume_ringer,
                            R.drawable.ic_volume_ringer_mute, true, false);
                } else {
                    addRow(AudioManager.STREAM_RING, R.drawable.ic_volume_notification,
                            R.drawable.ic_volume_notification_mute, true, false);
                }
                addRow(STREAM_ALARM,
                        R.drawable.ic_alarm, R.drawable.ic_volume_alarm_mute, true, false);
                addRow(AudioManager.STREAM_VOICE_CALL,
                        com.android.internal.R.drawable.ic_phone,
                        com.android.internal.R.drawable.ic_phone, false, false);
                addRow(AudioManager.STREAM_BLUETOOTH_SCO,
                        R.drawable.ic_volume_bt_sco, R.drawable.ic_volume_bt_sco, false, false);
                addRow(AudioManager.STREAM_SYSTEM, R.drawable.ic_volume_system,
                        R.drawable.ic_volume_system_mute, false, false);
            }
        } else {
            addExistingRows();
        }

        if (Util.isVoiceCapable(mContext) && mState != null) {
            updateNotificationRowH();
        }

        updateRowsH(getActiveRow());
        initRingerH();
        initSettingsH();
        initODICaptionsH();
    }

    private void updateAnimations() {
        switch (mVolumeAnimations) {
           case 0:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationEnter);
           break;
           case 1:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimation);
           break;
           case 2:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationTop);
           break;
           case 3:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationFly);
           break;
           case 4:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationTn);
           break;
           case 5:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationTranslucent);
           break;
           case 6:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationXylon);
           break;
           case 7:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationCard);
           break;
           case 8:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationTranslucent);
           break;
           case 9:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationTranslucent);
           break;
           case 10:
              mWindow.setWindowAnimations(com.android.internal.R.style.GlobalActionsAnimationRotate);
           default:
           case 11:
             mWindow.setWindowAnimations(com.android.internal.R.style.Animation_Toast);
           break;
        }
    }

    // Helper to set layout gravity.
    // Particular useful when the ViewGroup in question
    // is different for portait vs landscape.
    private void setLayoutGravity(Object obj, int gravity) {
        if (obj instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) obj).gravity = gravity;
        } else if (obj instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) obj).gravity = gravity;
        }
    }

    private float getAnimatorX() {
        final float x = mDialogView.getWidth() / 2.0f;
        return mVolumePanelOnLeft ? -x : x;
    }

    private class VolumeDialogRunnable implements Runnable {
        @Override
        public void run() {
            // Trigger panel rebuild on next show
            mConfigChanged = true;
        }
    }

    private final VolumeDialogRunnable mVolumeDialogRunnable = new VolumeDialogRunnable();

    @Override
    public void onTuningChanged(String key, String newValue) {
        boolean triggerChange = false;
        switch (key) {
            case VOLUME_PANEL_ON_LEFT:
                final boolean volumePanelOnLeft = TunerService.parseIntegerSwitch(newValue, isAudioPanelOnLeftSide());
                if (mVolumePanelOnLeft != volumePanelOnLeft) {
                    mVolumePanelOnLeft = volumePanelOnLeft;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_MEDIA:
                boolean mediaShowing = TunerService.parseIntegerSwitch(newValue, false);
                if (mMediaShowing != mediaShowing) {
                    mMediaShowing = mediaShowing;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_RINGER:
                boolean ringerShowing = TunerService.parseIntegerSwitch(newValue, false);
                if (mRingerShowing != ringerShowing) {
                    mRingerShowing = ringerShowing;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_NOTIFICATION:
                boolean notificationShowing = TunerService.parseIntegerSwitch(newValue, false);
                if (mNotificationShowing != notificationShowing) {
                    mNotificationShowing = notificationShowing;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_ALARM:
                boolean alarmShowing = TunerService.parseIntegerSwitch(newValue, false);
                if (mAlarmShowing != alarmShowing) {
                    mAlarmShowing = alarmShowing;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_VOICE:
                boolean voiceShowing = TunerService.parseIntegerSwitch(newValue, false);
                if (mVoiceShowing != voiceShowing) {
                    mVoiceShowing = voiceShowing;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_BT_SCO:
                boolean BTSCOShowing = TunerService.parseIntegerSwitch(newValue, false);
                if (mBTSCOShowing != BTSCOShowing) {
                    mBTSCOShowing = BTSCOShowing;
                    triggerChange = true;
                }
                break;
            case AUDIO_PANEL_VIEW_TIMEOUT:
                mTimeOutDesired = TunerService.parseInteger(newValue, 3);
                int timeOut = mTimeOutDesired * 1000;
                if (mTimeOut != timeOut) {
                    mTimeOut = timeOut;
                    triggerChange = true;
                }
                break;
            case RINGER_VOLUME_PANEL:
                boolean ShowRinger = TunerService.parseIntegerSwitch(newValue, true);
                if (mShowRinger != ShowRinger) {
                    mShowRinger = ShowRinger;
                    triggerChange = true;
                }
                break;
            case APP_VOLUME:
                boolean appVolume = TunerService.parseIntegerSwitch(newValue, false);
                if (mAppVolume != appVolume) {
                    mAppVolume = appVolume;
                    triggerChange = true;
                }
                break;
            case TINT_INACTIVE_SLIDER:
                mTintInActive = TunerService.parseIntegerSwitch(newValue, false);
                break;
            case VOLUME_ANIMATIONS:
                int animations = TunerService.parseInteger(newValue, 11);
                if (mVolumeAnimations != animations) {
                    mVolumeAnimations = animations;
                    triggerChange = true;
                }
                break;
            default:
                break;
        }
        if (triggerChange) {
            mHandler.removeCallbacks(mVolumeDialogRunnable);
            mHandler.post(mVolumeDialogRunnable);
        }
    }

    public void initText (NotificationMediaManager mediaManager) {
        mMediaManager = mediaManager;
    }

    protected ViewGroup getDialogView() {
        return mDialogView;
    }

    private int getAlphaAttr(int attr) {
        TypedArray ta = mContext.obtainStyledAttributes(new int[]{attr});
        float alpha = ta.getFloat(0, 0);
        ta.recycle();
        return (int) (alpha * 255);
    }

    private boolean isLandscape() {
        return mContext.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
    }

    public void setStreamImportant(int stream, boolean important) {
        mHandler.obtainMessage(H.SET_STREAM_IMPORTANT, stream, important ? 1 : 0).sendToTarget();
    }

    public void setAutomute(boolean automute) {
        if (mAutomute == automute) return;
        mAutomute = automute;
        mHandler.sendEmptyMessage(H.RECHECK_ALL);
    }

    public void setSilentMode(boolean silentMode) {
        if (mSilentMode == silentMode) return;
        mSilentMode = silentMode;
        mHandler.sendEmptyMessage(H.RECHECK_ALL);
    }

    private void addRow(int stream, int iconRes, int iconMuteRes, boolean important,
            boolean defaultStream) {
        addRow(stream, iconRes, iconMuteRes, important, defaultStream, false);
    }

    private void addRow(int stream, int iconRes, int iconMuteRes, boolean important,
            boolean defaultStream, boolean dynamic) {
        if (D.BUG) Slog.d(TAG, "Adding row for stream " + stream);
        VolumeRow row = new VolumeRow();
        initRow(row, stream, iconRes, iconMuteRes, important, defaultStream);
        mDialogRowsView.addView(row.view, 0);
        mRows.add(row);
    }

    private void addAppRow(AppTrackData data) {
        VolumeRow row = new VolumeRow();
        initAppRow(row, data);
        mDialogRowsView.addView(row.view);
        mAppRows.add(row);
    }

    @SuppressLint("InflateParams")
    private void initAppRow(final VolumeRow row, final AppTrackData data) {
        row.view = mDialog.getLayoutInflater().inflate(R.layout.volume_dialog_row,
                mDialogRowsView, false);

        row.packageName = data.getPackageName();
        row.isAppVolumeRow = true;

        row.view.setTag(row);
        row.slider = row.view.findViewById(R.id.volume_row_slider);
        row.slider.setOnSeekBarChangeListener(new VolumeSeekBarChangeListener(row));

        row.appMuted = data.isMuted();
        row.slider.setProgress((int) (data.getVolume() * 100));

        row.dndIcon = row.view.findViewById(R.id.dnd_icon);
        row.dndIcon.setVisibility(View.GONE);

        row.icon = row.view.findViewById(R.id.volume_row_app_icon);
        row.icon.setVisibility(View.VISIBLE);
        PackageManager pm = mContext.getPackageManager();
        try {
            row.icon.setImageDrawable(pm.getApplicationIcon(row.packageName));
        } catch (PackageManager.NameNotFoundException e) {
            row.icon.setImageDrawable(pm.getDefaultActivityIcon());
            Log.e(TAG, "Failed to get icon of " + row.packageName, e);
        }
    }

    private void addExistingRows() {
        for (VolumeRow row : mRows) {
            initRow(row, row.stream, row.iconRes, row.iconMuteRes, row.important,
                    row.defaultStream);
            mDialogRowsView.addView(row.view, 0);
            updateVolumeRowH(row);
        }
    }

    private void cleanExpandedRows() {
        VolumeRow ring = findRow(STREAM_RING);
        VolumeRow alarm = findRow(STREAM_ALARM);
        VolumeRow media = findRow(STREAM_MUSIC);
        VolumeRow notification = findRow(STREAM_NOTIFICATION);
        VolumeRow active = getActiveRow();

        float width = mWidth + mSpacer;
        float z = mElevation;

        boolean isMediaButtonVisible = mMediaButtonView.getVisibility() == VISIBLE;

        if (isMediaButtonVisible && !mODIServiceComponentEnabled) {
            animateViewOut(mMediaButtonView, false, width, z);
        } else if (mODIServiceComponentEnabled) {
            float widthMedia = width;
            if (isMediaButtonVisible) {
                animateViewOut(mMediaButtonView, false, widthMedia, z/2);
                widthMedia += widthMedia;
            }
            animateViewOut(mODICaptionsView, false, widthMedia, z);
            hideCaptionsTooltip();
        }

        boolean isNotificationEnabled = notification != null && !mState.linkedNotification;
        if (isNotificationEnabled) {
            final boolean isNotifVisible = active == notification;
            animateViewOut(notification.view, isNotifVisible, 0, z);
            z /= 2;
            width = isNotifVisible ? width : width * 2;
        }
        if (alarm != null) {
            final boolean isAlarmVisible = active == alarm;
            animateViewOut(alarm.view, isAlarmVisible, isNotificationEnabled ? width/2 : 0, z);
            z /= 2;
            width = isAlarmVisible ? width / 2 : width;
        }
        if (ring != null) {
            final boolean isRingVisible = active == ring;
            animateViewOut(ring.view, isRingVisible, width, z);
            z /= 2;
            width += isRingVisible ? 0 : (mWidth + mSpacer);
        }
        if (media != null) {
            Util.setVisOrGone(media.view, true);
            animateViewOut(media.view, true, width, z);
        }
        if (mShowingMediaDevices) {
            mDialogRowsView.setAlpha(1f);
            final ColorStateList tint = Utils.getColorAttr(mContext,
                android.R.attr.colorControlNormal);
            mMediaButton.setImageTintList(tint);
            mMediaTitleText.setSelected(false);
            mShowingMediaDevices = false;
            if (mExpanded) {
                if (mCurrAnimator != null && mCurrAnimator.isRunning()) {
                    mCurrAnimator.cancel();
                }
                int x = (int) (isAudioPanelOnLeftSide() ? 0 : (3 * mWidth + 2 * mSpacer));
                int endRadius = (int) Math.hypot(3 * mWidth + 2 * mSpacer, mHeight);
                mCurrAnimator = circularExit(mMediaOutputScrollView, x, endRadius);
                mCurrAnimator.start();
            } else {
                Util.setVisOrGone(mMediaOutputScrollView, false);
                Util.setVisOrGone(mDialogRowsView, true);
            }
        }
    }

    private VolumeRow getActiveRow() {
        for (VolumeRow row : mRows) {
            if (row.stream == mActiveStream) {
                return row;
            }
        }
        for (VolumeRow row : mRows) {
            if (row.stream == STREAM_MUSIC) {
                return row;
            }
        }
        return mRows.get(0);
    }

    private VolumeRow findRow(int stream) {
        for (VolumeRow row : mRows) {
            if (row.stream == stream) return row;
        }
        return null;
    }

    public void dump(PrintWriter writer) {
        writer.println(VolumeDialogImpl.class.getSimpleName() + " state:");
        writer.print("  mShowing: "); writer.println(mShowing);
        writer.print("  mActiveStream: "); writer.println(mActiveStream);
        writer.print("  mDynamic: "); writer.println(mDynamic);
        writer.print("  mAutomute: "); writer.println(mAutomute);
        writer.print("  mSilentMode: "); writer.println(mSilentMode);
    }

    private static int getImpliedLevel(SeekBar seekBar, int progress) {
        final int m = seekBar.getMax();
        final int n = m / 100 - 1;
        final int level = progress == 0 ? 0
                : progress == m ? (m / 100) : (1 + (int)((progress / (float) m) * n));
        return level;
    }

    @SuppressLint("InflateParams")
    private void initRow(final VolumeRow row, final int stream, int iconRes, int iconMuteRes,
            boolean important, boolean defaultStream) {
        row.stream = stream;
        row.iconRes = iconRes;
        row.iconMuteRes = iconMuteRes;
        row.important = important;
        row.defaultStream = defaultStream;
        row.view = mDialog.getLayoutInflater().inflate(R.layout.volume_dialog_row,
                mDialogRowsView, false);
        row.view.setId(row.stream);
        row.view.setTag(row);
        row.header = row.view.findViewById(R.id.volume_row_header);
        row.header.setId(20 * row.stream);
        if (stream == STREAM_ACCESSIBILITY) {
            row.header.setFilters(new InputFilter[] {new InputFilter.LengthFilter(13)});
        }
        row.dndIcon = row.view.findViewById(R.id.dnd_icon);
        row.slider = row.view.findViewById(R.id.volume_row_slider);
        row.slider.setOnSeekBarChangeListener(new VolumeSeekBarChangeListener(row));

        row.anim = null;

        row.icon = row.view.findViewById(R.id.volume_row_icon);
        row.icon.setImageResource(iconRes);
        row.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.icon.setVisibility(View.VISIBLE);
    }

    public void initSettingsH() {
        if (mExpandRowsView != null) {
            mExpandRowsView.setVisibility(
                    mDeviceProvisionedController.isCurrentUserSetup() &&
                            mActivityManager.getLockTaskModeState() == LOCK_TASK_MODE_NONE ?
                            VISIBLE : GONE);
        }
        if (mExpandRows != null) {
            mMediaButton.setOnClickListener(v -> {
                int x = (int) (isLandscape() ? (isAudioPanelOnLeftSide() ? (
                        (mWidth + mSpacer) * (mHasAlertSlider ? 1 : 2) + mWidth / 2)
                        : (mHasAlertSlider ? mWidth * 1.5 + mSpacer : mWidth / 2))
                        : (1.5 * mWidth + mSpacer));
                int endRadius = (int) Math.hypot((isLandscape() ? 2.2 : 1.1) * (1.5 * mWidth +
                        mSpacer), mHeight);
                if (mShowingMediaDevices) {
                    mShowingMediaDevices = false;
                    if (mCurrAnimator != null && mCurrAnimator.isRunning()) {
                        mCurrAnimator.cancel();
                    }
                    VolumeRow media = findRow(STREAM_MUSIC);
                    if (media != null) {
                        animateViewIn(media.view, false, mWidth + mSpacer, mElevation);
                    }
                    mCurrAnimator = circularExit(mMediaOutputScrollView, x, endRadius);
                } else {
                    mShowingMediaDevices = true;
                    if (mCurrAnimator != null && mCurrAnimator.isRunning()) {
                        mCurrAnimator.cancel();
                    }
                    VolumeRow media = findRow(STREAM_MUSIC);
                    if (media != null) {
                        animateViewOut(media.view, true, mWidth + mSpacer, mElevation);
                    }
                    mCurrAnimator = circularReveal(mMediaOutputScrollView, x, endRadius);
                }
                mCurrAnimator.start();
                final ColorStateList tint = mShowingMediaDevices
                    ? Utils.getColorAccent(mContext)
                    : Utils.getColorAttr(mContext, android.R.attr.colorControlNormal);
                mMediaButton.setImageTintList(tint);

                provideTouchHapticH(VibrationEffect.get(VibrationEffect.EFFECT_TICK));
            });
            mExpandRows.setOnClickListener(v -> {
                if (!mExpanded) {
                    VolumeRow ring = findRow(STREAM_RING);
                    VolumeRow alarm = findRow(STREAM_ALARM);
                    VolumeRow media = findRow(STREAM_MUSIC);
                    VolumeRow notification = findRow(STREAM_NOTIFICATION);
                    VolumeRow active = getActiveRow();

                    if (mHeight == 0 || mWidth == 0) {
                        mHeight = (float) active.view.getHeight();
                        mWidth = (float) active.view.getWidth();
                    }

                    float width = mWidth + mSpacer;
                    float z = mElevation;

                    boolean showMediaOutput = !Utils.isAudioModeOngoingCall(mContext) &&
                            mMediaOutputView.getChildCount() > 0;

                    if (showMediaOutput && !mODIServiceComponentEnabled) {
                        animateViewIn(mMediaButtonView, false, width, z);
                    } else if (mODIServiceComponentEnabled) {
                        float widthMedia = width;
                        if (showMediaOutput) {
                            animateViewIn(mMediaButtonView, false, widthMedia, z / 2);
                            widthMedia += widthMedia;
                        }
                        animateViewIn(mODICaptionsView, false, widthMedia, z);
                        if (mPendingOdiCaptionsTooltip && mODICaptionsView != null) {
                            showCaptionsTooltip();
                            mPendingOdiCaptionsTooltip = false;
                        }
                    }

                    boolean isNotificationEnabled = notification != null && !mState.linkedNotification;
                    if (isNotificationEnabled) {
                        final boolean isNotifVisible = active == notification;
                        animateViewIn(notification.view, isNotifVisible, 0, z);
                        z /= 2;
                    }
                    if (alarm != null) {
                        final boolean isAlarmVisible = active == alarm;
                        animateViewIn(alarm.view, isAlarmVisible, isNotificationEnabled ? width : 0, z);
                        z /= 2;
                        width = isAlarmVisible ? width/2 : width;
                    }
                    if (ring != null) {
                        final boolean isRingVisible = active == ring;
                        animateViewIn(ring.view, isRingVisible, width, z);
                        z /= 2;
                        width += isRingVisible ? 0 : (mWidth + mSpacer);
                    }
                    if (media != null) {
                        animateViewIn(media.view, true, width, z);
                    }
                    provideTouchHapticH(VibrationEffect.get(VibrationEffect.EFFECT_TICK));
                    mExpanded = true;
                } else {
                    cleanExpandedRows();
                    provideTouchHapticH(VibrationEffect.get(VibrationEffect.EFFECT_TICK));
                    mExpanded = false;
                }
                mExpandRows.setExpanded(mExpanded);
            });
        }
        if (mAppVolume) {
            updateAppRows();
        }
    }

    private void updateAppRows() {
        for (int i = mAppRows.size() - 1; i >= 0; i--) {
            final VolumeRow row = mAppRows.get(i);
            removeAppRow(row);
        }
        List<AppTrackData> trackDatas = mController.getAudioManager().listAppTrackDatas();
        for (AppTrackData data : trackDatas) {
            if (data.isActive()) {
                addAppRow(data);
            }
        }
    }

    private Animator circularReveal(View view, int x, int endRadius) {
        if (view == null) return null;

        Animator anim = ViewAnimationUtils.createCircularReveal(view, x,
            isLandscape() ? 0 : (int) mHeight, 0, endRadius);

        anim.setDuration(DIALOG_SHOW_ANIMATION_DURATION);
        anim.setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator());
        anim.addListener(new Animator.AnimatorListener() {
            private boolean mIsCancelled;
            private ViewPropertyAnimator mRowsAnimator;

            @Override
            public void onAnimationStart(Animator animation) {
                mIsCancelled = false;
                Util.setVisOrGone(view, true);
                mRowsAnimator = mDialogRowsView.animate()
                    .alpha(0f)
                    .setDuration(DIALOG_SHOW_ANIMATION_DURATION)
                    .setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator());
                mRowsAnimator.start();
            }

            @Override
            public void onAnimationEnd (Animator animation) {
                Util.setVisOrGone(mDialogRowsView, mIsCancelled);
                mHandler.postDelayed(() -> mMediaTitleText.setSelected(true), 100);
            }

            @Override
            public void onAnimationRepeat (Animator animation) { }

            @Override
            public void onAnimationCancel (Animator animation) {
                mIsCancelled = true;
                mRowsAnimator.cancel();
            }
        });
        return anim;
    }

    private Animator circularExit(View view, int x, int endRadius) {
        if (view == null) return null;

        Animator anim = ViewAnimationUtils.createCircularReveal(view, x,
            isLandscape() ? 0 : (int) mHeight, endRadius, 0);

        anim.setDuration(DIALOG_SHOW_ANIMATION_DURATION);
        anim.setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator());
        anim.addListener(new Animator.AnimatorListener() {
            private boolean mIsCancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                mIsCancelled = false;
                Util.setVisOrGone(mDialogRowsView, true);
                mDialogRowsView.setAlpha(1f);
            }

            @Override
            public void onAnimationEnd (Animator animation) {
                Util.setVisOrGone(view, mIsCancelled);
                mMediaTitleText.setSelected(false);
            }

            @Override
            public void onAnimationRepeat (Animator animation) { }

            @Override
            public void onAnimationCancel (Animator animation) {
                mIsCancelled = true;
            }
        });
        return anim;
    }

    private void animateViewIn(View view, boolean wasVisible, float startX, float startZ) {
        if (view == null) return;

        float startAlpha = 0f;
        if (wasVisible) {
            startZ = 0;
            startAlpha = 1f;
        } else {
            startZ = -startZ;
        }

        view.setTranslationX(isAudioPanelOnLeftSide() ? -startX : startX);
        view.setTranslationZ(startZ);
        view.setAlpha(startAlpha);
        Util.setVisOrGone(view, true);
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .translationZ(0f)
            .withLayer()
            .setDuration(DIALOG_SHOW_ANIMATION_DURATION)
            .setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator())
            .withEndAction(() -> {
                view.setTranslationX(0f);
                view.setTranslationZ(0f);
                Util.setVisOrGone(view, true);
            })
            .start();
    }

    private void animateViewOut(View view, boolean stayVisible, float endX, float endZ) {
        if (view == null) return;

        float endAlpha = 0f;
        if (stayVisible) {
            endZ = 0;
            endAlpha = 1f;
        } else {
            endZ = -endZ;
        }

        view.animate()
            .alpha(endAlpha)
            .translationX(isAudioPanelOnLeftSide() ? -endX : endX)
            .translationZ(endZ)
            .withLayer()
            .setDuration(DIALOG_SHOW_ANIMATION_DURATION)
            .setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator())
            .withEndAction(() -> {
                view.setTranslationX(0);
                view.setTranslationZ(0);
                Util.setVisOrGone(view, stayVisible);
                view.setAlpha(1);
            })
            .start();
    }

    @Override
    public void onDeviceListUpdate(List<MediaDevice> devices) {
        mMediaDevices.clear();
        mMediaDevices.addAll(devices);
        if (!mHandler.hasMessages(H.UPDATE_MEDIA_OUTPUT_VIEW)) {
            mHandler.sendEmptyMessageDelayed(H.UPDATE_MEDIA_OUTPUT_VIEW, 30);
        }
    }

    @Override
    public void onSelectedDeviceStateChanged(MediaDevice device, int state) {
        // Do nothing
    }

    private void updateMediaOutputViewH() {
        // update/add/remove existing views
        final String activeText = mContext
            .getString(com.android.settingslib.R.string.bluetooth_active_no_battery_level);
        for (MediaOutputRow row : mMediaOutputRows) {
            if (mMediaDevices.contains(row.device)) {
                if (row.device.isConnected()) {
                    row.name.setText(row.device.getName());
                    if (row.device.getSummary() != null) {
                        Util.setVisOrGone(row.summary, !row.device.getSummary().equals(""));
                        row.summary.setText(row.device.getSummary());
                        Util.setVisOrGone(row.selected,
                                row.device.getSummary().contains(activeText));
                    } else {
                        Util.setVisOrGone(row.summary, false);
                        Util.setVisOrGone(row.selected, false);
                    }
                    if (!row.addedToGroup) {
                        mMediaOutputView.addView(row.view);
                        row.addedToGroup = true;
                    }
                } else {
                    mMediaOutputView.removeView(row.view);
                    row.addedToGroup = false;
                }
                // remove the device that has been handled
                mMediaDevices.remove(row.device);
            } else {
                mMediaOutputView.removeView(row.view);
                row.addedToGroup = false;
            }
        }


        // handle the remaining devices
        for (MediaDevice device : mMediaDevices) {
            if (device.isConnected()) {
                // This device does not have a corresponding row yet, make one.
                MediaOutputRow row = new MediaOutputRow();
                row.device = device;
                row.view = mDialog.getLayoutInflater().inflate(R.layout.volume_dialog_media_output,
                        mMediaOutputView, false);
                row.view.setOnClickListener(v -> {
                        provideTouchHapticH(VibrationEffect.get(VibrationEffect.EFFECT_CLICK));
                        mLocalMediaManager.connectDevice(device);
                });
                row.name = row.view.findViewById(R.id.media_output_text);
                row.summary = row.view.findViewById(R.id.media_output_summary);
                row.selected = row.view.findViewById(R.id.media_output_selected);
                row.icon = row.view.findViewById(R.id.media_output_icon);
                Drawable drawable = null;
                try {
                    drawable = device.getIcon();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get icon of bluetooth headset");
                    
                }
                if (drawable == null) {
                    drawable = mContext.getDrawable(
                            com.android.internal.R.drawable.ic_bt_headphones_a2dp);
                }
                row.icon.setImageDrawable(drawable);

                row.name.setText(device.getName());
                if (device.getSummary() != null) {
                    Util.setVisOrGone(row.summary, !device.getSummary().equals(""));
                    row.summary.setText(device.getSummary());
                    Util.setVisOrGone(row.selected, row.device.getSummary().contains(activeText));
                } else {
                    Util.setVisOrGone(row.summary, false);
                    Util.setVisOrGone(row.selected, false);
                }
                row.name.setSelected(true);
                row.summary.setSelected(true);

                row.addedToGroup = true;
                mMediaOutputView.addView(row.view);
                mMediaOutputRows.add(row);
            }
        }
        if (mMediaOutputView.getChildCount() == 1) {
            // This means there are no external devices connected
            removeAllMediaOutputRows();
        }
    }

    private void removeAllMediaOutputRows() {
        mMediaOutputView.removeAllViews();
        mMediaOutputRows.clear();
    }

    public void initRingerH() {
        if (mRingerIcon != null) {
            mRingerIcon.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            mRingerIcon.setOnClickListener(v -> {
                Prefs.putBoolean(mContext, Prefs.Key.TOUCHED_RINGER_TOGGLE, true);
                final StreamState ss = mState.states.get(AudioManager.STREAM_RING);
                if (ss == null) {
                    return;
                }
                // normal -> vibrate -> silent -> normal (skip vibrate if device doesn't have
                // a vibrator.
                int newRingerMode;
                final boolean hasVibrator = mController.hasVibrator();
                if (mState.ringerModeInternal == AudioManager.RINGER_MODE_NORMAL) {
                    if (hasVibrator) {
                        newRingerMode = AudioManager.RINGER_MODE_VIBRATE;
                    } else {
                        newRingerMode = AudioManager.RINGER_MODE_SILENT;
                    }
                } else if (mState.ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE) {
                    newRingerMode = AudioManager.RINGER_MODE_SILENT;
                } else {
                    newRingerMode = AudioManager.RINGER_MODE_NORMAL;
                    if (ss.level == 0) {
                        mController.setStreamVolume(AudioManager.STREAM_RING, 1);
                    }
                }
                Events.writeEvent(mContext, Events.EVENT_RINGER_TOGGLE, newRingerMode);
                incrementManualToggleCount();
                updateRingerH();
                provideTouchFeedbackH(newRingerMode);
                mController.setRingerMode(newRingerMode, false);
                maybeShowToastH(newRingerMode);
            });
        }
        updateRingerH();
    }

    private void initODICaptionsH() {
        if (mODICaptionsIcon != null) {
            mODICaptionsIcon.setOnConfirmedTapListener(() -> {
                onCaptionIconClicked();
                Events.writeEvent(mContext, Events.EVENT_ODI_CAPTIONS_CLICK);
            }, mHandler);
        }

        mController.getCaptionsComponentState(false);
    }

    private void checkODICaptionsTooltip(boolean fromDismiss) {
        if (!mHasSeenODICaptionsTooltip && !fromDismiss && mODICaptionsTooltipViewStub != null) {
            mController.getCaptionsComponentState(true);
        } else {
            if (mHasSeenODICaptionsTooltip && fromDismiss && mODICaptionsTooltipView != null) {
                hideCaptionsTooltip();
            }
        }
    }

    protected void showCaptionsTooltip() {
        if (!mHasSeenODICaptionsTooltip && mODICaptionsTooltipViewStub != null) {
            mODICaptionsTooltipView = mODICaptionsTooltipViewStub.inflate();
            mODICaptionsTooltipView.findViewById(R.id.dismiss).setOnClickListener(v -> {
                hideCaptionsTooltip();
                Events.writeEvent(mContext, Events.EVENT_ODI_CAPTIONS_TOOLTIP_CLICK);
            });
            mODICaptionsTooltipViewStub = null;
            rescheduleTimeoutH();
        }

        if (mODICaptionsTooltipView != null) {
            mODICaptionsTooltipView.setAlpha(0.f);
            mODICaptionsTooltipView.animate()
                .alpha(1.f)
                .setStartDelay(DIALOG_SHOW_ANIMATION_DURATION)
                .withEndAction(() -> {
                    if (D.BUG) Log.d(TAG, "tool:checkODICaptionsTooltip() putBoolean true");
                    Prefs.putBoolean(mContext,
                            Prefs.Key.HAS_SEEN_ODI_CAPTIONS_TOOLTIP, true);
                    mHasSeenODICaptionsTooltip = true;
                    if (mODICaptionsIcon != null) {
                        mODICaptionsIcon
                                .postOnAnimation(getSinglePressFor(mODICaptionsIcon));
                    }
                })
                .start();
        }
    }

    private void hideCaptionsTooltip() {
        if (mODICaptionsTooltipView != null && mODICaptionsTooltipView.getVisibility() == VISIBLE) {
            mODICaptionsTooltipView.animate().cancel();
            mODICaptionsTooltipView.setAlpha(1.f);
            mODICaptionsTooltipView.animate()
                    .alpha(0.f)
                    .setStartDelay(0)
                    .setDuration(DIALOG_HIDE_ANIMATION_DURATION)
                    .withEndAction(() -> mODICaptionsTooltipView.setVisibility(GONE))
                    .start();
        }
    }

    protected void tryToRemoveCaptionsTooltip() {
        if (mHasSeenODICaptionsTooltip && mODICaptionsTooltipView != null) {
            mDialogView.removeView(mODICaptionsTooltipView);
            mODICaptionsTooltipView = null;
        }
    }

    private void updateODICaptionsH(boolean isServiceComponentEnabled, boolean fromTooltip) {
        mODIServiceComponentEnabled = isServiceComponentEnabled;

        if (!mODIServiceComponentEnabled) return;

        updateCaptionsIcon();
        if (fromTooltip) mPendingOdiCaptionsTooltip = true;
    }

    private void updateCaptionsIcon() {
        boolean captionsEnabled = mController.areCaptionsEnabled();
        if (mODICaptionsIcon.getCaptionsEnabled() != captionsEnabled) {
            mHandler.post(mODICaptionsIcon.setCaptionsEnabled(captionsEnabled));
        }

        boolean isOptedOut = mController.isCaptionStreamOptedOut();
        if (mODICaptionsIcon.getOptedOut() != isOptedOut) {
            mHandler.post(() -> mODICaptionsIcon.setOptedOut(isOptedOut));
        }
    }

    private void onCaptionIconClicked() {
        boolean isEnabled = mController.areCaptionsEnabled();
        mController.setCaptionsEnabled(!isEnabled);
        updateCaptionsIcon();
    }

    private void incrementManualToggleCount() {
        ContentResolver cr = mContext.getContentResolver();
        int ringerCount = Settings.Secure.getInt(cr, Settings.Secure.MANUAL_RINGER_TOGGLE_COUNT, 0);
        Settings.Secure.putInt(cr, Settings.Secure.MANUAL_RINGER_TOGGLE_COUNT, ringerCount + 1);
    }

    private void provideTouchFeedbackH(int newRingerMode) {
        VibrationEffect effect = null;
        switch (newRingerMode) {
            case RINGER_MODE_NORMAL:
                mController.scheduleTouchFeedback();
                break;
            case RINGER_MODE_SILENT:
                effect = VibrationEffect.get(VibrationEffect.EFFECT_CLICK);
                break;
            case RINGER_MODE_VIBRATE:
                effect = VibrationEffect.get(VibrationEffect.EFFECT_THUD);
                break;
            default:
                effect = VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK);
        }
        if (effect != null) {
            mController.vibrate(effect);
        }
    }

    private void provideTouchHapticH(VibrationEffect effect) {
        mController.vibrate(effect);
    }

    private void provideSliderHapticFeedbackH() {
        VibrationEffect effect = VibrationEffect.get(VibrationEffect.EFFECT_TEXTURE_TICK);
        mController.vibrate(effect);
    }

    private void maybeShowToastH(int newRingerMode) {
        int seenToastCount = Prefs.getInt(mContext, Prefs.Key.SEEN_RINGER_GUIDANCE_COUNT, 0);

        if (seenToastCount > VolumePrefs.SHOW_RINGER_TOAST_COUNT) {
            return;
        }
        CharSequence toastText = null;
        switch (newRingerMode) {
            case RINGER_MODE_NORMAL:
                final StreamState ss = mState.states.get(AudioManager.STREAM_RING);
                if (ss != null) {
                    toastText = mContext.getString(
                            R.string.volume_dialog_ringer_guidance_ring,
                            Utils.formatPercentage(ss.level, ss.levelMax));
                }
                break;
            case RINGER_MODE_SILENT:
                toastText = mContext.getString(
                        com.android.internal.R.string.volume_dialog_ringer_guidance_silent);
                break;
            case RINGER_MODE_VIBRATE:
            default:
                toastText = mContext.getString(
                        com.android.internal.R.string.volume_dialog_ringer_guidance_vibrate);
        }

        Toast.makeText(mContext, toastText, Toast.LENGTH_SHORT).show();
        seenToastCount++;
        Prefs.putInt(mContext, Prefs.Key.SEEN_RINGER_GUIDANCE_COUNT, seenToastCount);
    }

    public void show(int reason) {
        mHandler.obtainMessage(H.SHOW, reason, 0).sendToTarget();
    }

    public void dismiss(int reason) {
        mHandler.obtainMessage(H.DISMISS, reason, 0).sendToTarget();
    }

    private void showH(int reason) {
        if (D.BUG) Log.d(TAG, "showH r=" + Events.SHOW_REASONS[reason]);
        mHandler.removeMessages(H.SHOW);
        mHandler.removeMessages(H.DISMISS);
        rescheduleTimeoutH();

        if (mConfigChanged) {
            removeAllMediaOutputRows();
            initDialog(); // resets mShowing to false
            mConfigurableTexts.update();
            mShowingMediaDevices = false;
            mConfigChanged = false;
        }
        initSettingsH();
        mShowing = true;
        mDialog.show();
        Events.writeEvent(mContext, Events.EVENT_SHOW_DIALOG, reason, mKeyguard.isKeyguardLocked());
        mController.notifyVisible(true);
        mController.getCaptionsComponentState(false);
        checkODICaptionsTooltip(false);
    }

    protected void rescheduleTimeoutH() {
        mHandler.removeMessages(H.DISMISS);
        final int timeout = computeTimeoutH();
        mHandler.sendMessageDelayed(mHandler
                .obtainMessage(H.DISMISS, Events.DISMISS_REASON_TIMEOUT, 0), timeout);
        if (D.BUG) Log.d(TAG, "rescheduleTimeout " + timeout + " " + Debug.getCaller());
        mController.userActivity();
    }

    private int computeTimeoutH() {
        if (mHovering) {
            return mAccessibilityMgr.getRecommendedTimeoutMillis(DIALOG_HOVERING_TIMEOUT_MILLIS,
                    AccessibilityManager.FLAG_CONTENT_CONTROLS);
        }
        if (mSafetyWarning != null) {
            return mAccessibilityMgr.getRecommendedTimeoutMillis(
                    DIALOG_SAFETYWARNING_TIMEOUT_MILLIS,
                    AccessibilityManager.FLAG_CONTENT_TEXT
                            | AccessibilityManager.FLAG_CONTENT_CONTROLS);
        }
        if (!mHasSeenODICaptionsTooltip && mODICaptionsTooltipView != null) {
            return mAccessibilityMgr.getRecommendedTimeoutMillis(
                    DIALOG_ODI_CAPTIONS_TOOLTIP_TIMEOUT_MILLIS,
                    AccessibilityManager.FLAG_CONTENT_TEXT
                            | AccessibilityManager.FLAG_CONTENT_CONTROLS);
        }
        return mTimeOut;
    }

    protected void dismissH(int reason) {
        if (D.BUG) {
            Log.d(TAG, "mDialog.dismiss() reason: " + Events.DISMISS_REASONS[reason]
                    + " from: " + Debug.getCaller());
        }
        if (mLocalMediaManager != null) {
            mLocalMediaManager.stopScan();
        }
        if (!mShowing) {
            // This may happen when dismissing an expanded panel, don't animate again
            return;
        }
        mHandler.removeMessages(H.DISMISS);
        mHandler.removeMessages(H.SHOW);
        mDialogView.animate().cancel();
        if (mShowing) {
            mShowing = false;
            // Only logs when the volume dialog visibility is changed.
            Events.writeEvent(mContext, Events.EVENT_DISMISS_DIALOG, reason);
        }
        mDialogView.setTranslationX(0);
        mDialogView.setAlpha(1);
        mMusicText.setTranslationX(0);
        mMusicText.setAlpha(1);
        ViewPropertyAnimator musicAnimator = mMusicText.animate()
                .alpha(0)
                .setDuration(DIALOG_HIDE_ANIMATION_DURATION)
                .setInterpolator(new SystemUIInterpolators.LogAccelerateInterpolator());
        ViewPropertyAnimator animator = mDialogView.animate()
                .alpha(0)
                .setDuration(DIALOG_HIDE_ANIMATION_DURATION)
                .setInterpolator(new SystemUIInterpolators.LogAccelerateInterpolator())
                .withEndAction(() -> mHandler.postDelayed(() -> {
                    mDialog.dismiss();
                    tryToRemoveCaptionsTooltip();
                    mExpanded = false;
                    cleanExpandedRows();
                    mExpandRows.setExpanded(mExpanded);
                }, 50));
        if (!isLandscape()) {
            animator.translationX(
                    (mDialogView.getWidth() / 2.0f) * (!isAudioPanelOnLeftSide() ? 1 : -1));
        }
        if (!isLandscape()) {
            musicAnimator.translationX((mMusicText.getWidth() / 2.0f) * (isAudioPanelOnLeftSide() ? -1 : 1));
        }
        animator.start();
        musicAnimator.start();
        checkODICaptionsTooltip(true);
        mController.notifyVisible(false);
        synchronized (mSafetyWarningLock) {
            if (mSafetyWarning != null) {
                if (D.BUG) Log.d(TAG, "SafetyWarning dismissed");
                mSafetyWarning.dismiss();
            }
        }
    }

    private boolean showActiveStreamOnly() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION);
    }

    private boolean shouldBeVisibleH(VolumeRow row, VolumeRow activeRow) {
        if (row.stream == AudioManager.STREAM_MUSIC && mMediaShowing) {
            return true;
        }
        if (row.stream == AudioManager.STREAM_RING && mRingerShowing) {
            return true;
        }
        if (row.stream == AudioManager.STREAM_NOTIFICATION && mNotificationShowing) {
            return true;
        }
        if (row.stream == AudioManager.STREAM_ALARM && mAlarmShowing) {
            return true;
        }
        if (row.stream == AudioManager.STREAM_VOICE_CALL && mVoiceShowing) {
            return true;
        }
        if (row.stream == AudioManager.STREAM_BLUETOOTH_SCO && mBTSCOShowing) {
            return true;
        }
        if (row.stream == activeRow.stream) {
            return true;
        }

        if (!mShowActiveStreamOnly) {
            if (row.stream == STREAM_ACCESSIBILITY) {
                return mShowA11yStream;
            }

            if (row.stream == mPrevActiveStream) {
                return true;
            }

            if (row.stream == STREAM_MUSIC) {
                return true;
            }

            if (row.defaultStream) {
                return activeRow.stream == STREAM_RING
                        || activeRow.stream == STREAM_ALARM
                        || activeRow.stream == STREAM_VOICE_CALL
                        || activeRow.stream == STREAM_ACCESSIBILITY
                        || activeRow.stream == STREAM_NOTIFICATION
                        || mDynamic.get(activeRow.stream);
            }
        }

        return false;
    }

    private void updateRowsH(final VolumeRow activeRow) {
        if (D.BUG) Log.d(TAG, "updateRowsH");
        if (!mShowing) {
            trimObsoleteH();
        }

        // apply changes to all rows
        for (final VolumeRow row : mRows) {
            final boolean isActive = row == activeRow;
            final boolean shouldBeVisible = shouldBeVisibleH(row, activeRow);
            if(!mExpanded) {
                Util.setVisOrGone(row.view, shouldBeVisible);
            }
            if (row.view.isShown()) {
                updateVolumeRowTintH(row, isActive);
            }
        }
    }

    protected void updateRingerH() {
        updateRingerH(false);
    }

    private void updateRingerH(boolean ringerChanged) {
        if (mState != null) {
            final StreamState ss = mState.states.get(AudioManager.STREAM_RING);
            if (ss == null) {
                return;
            }

            boolean isZenMuted = mState.zenMode == Global.ZEN_MODE_ALARMS
                    || mState.zenMode == Global.ZEN_MODE_NO_INTERRUPTIONS
                    || (mState.zenMode == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
                        && mState.disallowRinger);
            enableRingerViewsH(!isZenMuted);
            switch (mState.ringerModeInternal) {
                case AudioManager.RINGER_MODE_VIBRATE:
                    mRingerIcon.setImageResource(R.drawable.ic_volume_ringer_vibrate);
                    addAccessibilityDescription(mRingerIcon, RINGER_MODE_VIBRATE,
                            mContext.getString(R.string.volume_ringer_hint_mute));
                    mRingerIcon.setTag(Events.ICON_STATE_VIBRATE);
                    if (ringerChanged) {
                        pinNotifAndRingerToMin();
                    }
                    break;
                case AudioManager.RINGER_MODE_SILENT:
                    mRingerIcon.setImageResource(R.drawable.ic_volume_ringer_mute);
                    mRingerIcon.setTag(Events.ICON_STATE_MUTE);
                    addAccessibilityDescription(mRingerIcon, RINGER_MODE_SILENT,
                            mContext.getString(R.string.volume_ringer_hint_unmute));
                    if (ringerChanged) {
                        pinNotifAndRingerToMin();
                    }
                    break;
                case AudioManager.RINGER_MODE_NORMAL:
                default:
                    boolean muted = (mAutomute && ss.level == 0) || ss.muted;
                    if (!isZenMuted && muted) {
                        mRingerIcon.setImageResource(R.drawable.ic_volume_ringer_mute);
                        addAccessibilityDescription(mRingerIcon, RINGER_MODE_NORMAL,
                                mContext.getString(R.string.volume_ringer_hint_unmute));
                        mRingerIcon.setTag(Events.ICON_STATE_MUTE);
                        if (ringerChanged) {
                            pinNotifAndRingerToMin();
                        }
                    } else {
                        mRingerIcon.setImageResource(R.drawable.ic_volume_ringer);
                        if (mController.hasVibrator()) {
                            addAccessibilityDescription(mRingerIcon, RINGER_MODE_NORMAL,
                                    mContext.getString(R.string.volume_ringer_hint_vibrate));
                        } else {
                            addAccessibilityDescription(mRingerIcon, RINGER_MODE_NORMAL,
                                    mContext.getString(R.string.volume_ringer_hint_mute));
                        }
                        mRingerIcon.setTag(Events.ICON_STATE_UNMUTE);
                    }
                    break;
            }
        }
    }

    private void pinNotifAndRingerToMin() {
        final VolumeRow ringer = findRow(STREAM_RING);
        final VolumeRow notif = findRow(STREAM_NOTIFICATION);

        if (ringer != null) {
            final int ringerLevel = ringer.ss.levelMin * 100;
            if (ringer.slider.getProgress() != ringerLevel) {
                ringer.slider.setProgress(ringerLevel);
            }
            Util.setText(ringer.header, Utils.formatPercentage(ringer.ss.levelMin,
                    ringer.ss.levelMax));
        }
        if (notif != null) {
            final int notifLevel = notif.ss.levelMin * 100;
            if (notif.slider.getProgress() != notifLevel) {
                notif.slider.setProgress(notifLevel);
            }
            Util.setText(notif.header, Utils.formatPercentage(notif.ss.levelMin,
                    notif.ss.levelMax));
        }
    }

    private void addAccessibilityDescription(View view, int currState, String hintLabel) {
        int currStateResId;
        switch (currState) {
            case RINGER_MODE_SILENT:
                currStateResId = R.string.volume_ringer_status_silent;
                break;
            case RINGER_MODE_VIBRATE:
                currStateResId = R.string.volume_ringer_status_vibrate;
                break;
            case RINGER_MODE_NORMAL:
            default:
                currStateResId = R.string.volume_ringer_status_normal;
        }

        view.setContentDescription(mContext.getString(currStateResId));
        view.setAccessibilityDelegate(new AccessibilityDelegate() {
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                                AccessibilityNodeInfo.ACTION_CLICK, hintLabel));
            }
        });
    }

    /**
     * Toggles enable state of views in a VolumeRow (not including seekbar or icon)
     * Hides/shows zen icon
     * @param enable whether to enable volume row views and hide dnd icon
     */
    private void enableVolumeRowViewsH(VolumeRow row, boolean enable) {
        boolean showDndIcon = !enable;
        row.dndIcon.setVisibility(showDndIcon ? VISIBLE : GONE);
    }

    /**
     * Toggles enable state of footer/ringer views
     * Hides/shows zen icon
     * @param enable whether to enable ringer views and hide dnd icon
     */
    private void enableRingerViewsH(boolean enable) {
        if (mRingerIcon != null) {
            mRingerIcon.setEnabled(enable);
        }
        if (mZenIcon != null) {
            mZenIcon.setVisibility(enable ? GONE : VISIBLE);
        }
    }

    private void trimObsoleteH() {
        if (D.BUG) Log.d(TAG, "trimObsoleteH");
        for (int i = mRows.size() - 1; i >= 0; i--) {
            final VolumeRow row = mRows.get(i);
            if (row.ss == null || !row.ss.dynamic) continue;
            if (!mDynamic.get(row.stream)) {
                removeRow(row);
            }
        }
    }

    private void removeRow(VolumeRow volumeRow) {
        mRows.remove(volumeRow);
        mDialogRowsView.removeView(volumeRow.view);
    }

    private void removeAppRow(VolumeRow volumeRow) {
        mAppRows.remove(volumeRow);
        mDialogRowsView.removeView(volumeRow.view);
    }

    protected void onStateChangedH(State state) {
        if (D.BUG) Log.d(TAG, "onStateChangedH() state: " + state.toString());
        if (mState != null && state != null
                && mState.ringerModeInternal != state.ringerModeInternal
                && state.ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE) {
            mController.vibrate(VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK));
        }

        boolean ringerChanged = false;
        if (mState != null && state != null
                && mState.ringerModeInternal != state.ringerModeInternal) {
            ringerChanged = true;
        }

        mState = state;
        mDynamic.clear();
        // add any new dynamic rows
        for (int i = 0; i < state.states.size(); i++) {
            final int stream = state.states.keyAt(i);
            final StreamState ss = state.states.valueAt(i);
            if (!ss.dynamic) continue;
            mDynamic.put(stream, true);
            if (findRow(stream) == null) {
                addRow(stream, R.drawable.ic_volume_remote, R.drawable.ic_volume_remote_mute, true,
                        false, true);
            }
        }

        if (Util.isVoiceCapable(mContext)) {
            updateNotificationRowH();
        }

        if (mActiveStream != state.activeStream) {
            mPrevActiveStream = mActiveStream;
            mActiveStream = state.activeStream;
            VolumeRow activeRow = getActiveRow();
            updateRowsH(activeRow);
            if (mShowing) rescheduleTimeoutH();
        }
        for (VolumeRow row : mRows) {
            updateVolumeRowH(row);
        }
        updateRingerH(ringerChanged);
        mWindow.setTitle(composeWindowTitle());
    }

    CharSequence composeWindowTitle() {
        return mContext.getString(R.string.volume_dialog_title, getStreamLabelH(getActiveRow().ss));
    }

    private void updateNotificationRowH() {
        VolumeRow notificationRow = findRow(STREAM_NOTIFICATION);
        VolumeRow alarm = findRow(STREAM_ALARM);
        if (notificationRow != null) {
            if (mState.linkedNotification) {
                removeRow(notificationRow);
            } else {
                final int alarmIndex = mDialogRowsView.indexOfChild(alarm.view);
                final int notifIndex = mDialogRowsView.indexOfChild(notificationRow.view);
                if (notifIndex == -1) {
                    mDialogRowsView.addView(notificationRow.view, alarmIndex);
                } else if ((alarmIndex - 1) != notifIndex) {
                    mDialogRowsView.removeView(notificationRow.view);
                    mDialogRowsView.addView(notificationRow.view, alarmIndex - 1);
                }
            }
        } else if (notificationRow == null && !mState.linkedNotification
                && !AudioSystem.isSingleVolume(mContext)) {
            notificationRow = new VolumeRow();
            initRow(notificationRow, STREAM_NOTIFICATION, R.drawable.ic_volume_notification,
                    R.drawable.ic_volume_notification_mute, true, false);
            mDialogRowsView.addView(notificationRow.view, mDialogRowsView.indexOfChild(alarm.view));
            mRows.add(notificationRow);
        }
    }

    private void updateVolumeRowH(VolumeRow row) {
        if (D.BUG) Log.i(TAG, "updateVolumeRowH s=" + row.stream);
        if (mState == null) return;
        final StreamState ss = mState.states.get(row.stream);
        if (ss == null) return;
        row.ss = ss;
        if (ss.level == row.requestedLevel) {
            row.requestedLevel = -1;
        }
        final boolean isA11yStream = row.stream == STREAM_ACCESSIBILITY;
        final boolean isRingStream = row.stream == AudioManager.STREAM_RING;
        final boolean isSystemStream = row.stream == AudioManager.STREAM_SYSTEM;
        final boolean isAlarmStream = row.stream == STREAM_ALARM;
        final boolean isMusicStream = row.stream == AudioManager.STREAM_MUSIC;
        final boolean isNotificationStream = row.stream == STREAM_NOTIFICATION;
        final boolean isVibrate = mState.ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE;
        final boolean isRingVibrate = isRingStream && isVibrate;
        final boolean isRingSilent = isRingStream
                && mState.ringerModeInternal == AudioManager.RINGER_MODE_SILENT;
        final boolean isZenPriorityOnly = mState.zenMode == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
        final boolean isZenAlarms = mState.zenMode == Global.ZEN_MODE_ALARMS;
        final boolean isZenNone = mState.zenMode == Global.ZEN_MODE_NO_INTERRUPTIONS;
        final boolean zenMuted =
                isZenAlarms ? (isRingStream || isSystemStream || isNotificationStream)
                : isZenNone ? (isRingStream || isSystemStream || isAlarmStream || isMusicStream || isNotificationStream)
                : isZenPriorityOnly ? ((isAlarmStream && mState.disallowAlarms) ||
                        (isMusicStream && mState.disallowMedia) ||
                        (isRingStream && mState.disallowRinger) ||
                        (isSystemStream && mState.disallowSystem))
                : isVibrate ? isNotificationStream
                : false;
        final boolean routedToSubmixAndEarphone = isMusicStream && mState.routedToSubmixAndEarphone;

        // update slider max
        final int max = ss.levelMax * 100;
        final boolean maxChanged = max != row.slider.getMax();
        if (maxChanged) {
            row.slider.setMax(max);
        }

        row.slider.setContentDescription(getStreamLabelH(ss));

        // update icon
        final boolean iconEnabled = (mAutomute || ss.muteSupported) && !zenMuted && !routedToSubmixAndEarphone;
        row.icon.setEnabled(iconEnabled);
        final int iconRes =
                isRingVibrate ? R.drawable.ic_volume_ringer_vibrate
                : isRingSilent || zenMuted || routedToSubmixAndEarphone ? row.iconMuteRes
                : ss.routedToBluetooth ?
                        (ss.muted ? R.drawable.ic_volume_media_bt_mute
                                : R.drawable.ic_volume_media_bt)
                : mAutomute && ss.level == 0 ? row.iconMuteRes
                : (ss.muted ? row.iconMuteRes : row.iconRes);
        row.icon.setImageResource(iconRes);
        if (iconEnabled) {
            if (isRingStream) {
                if (isRingVibrate) {
                    row.icon.setContentDescription(mContext.getString(
                            R.string.volume_stream_content_description_unmute,
                            getStreamLabelH(ss)));
                } else {
                    if (mController.hasVibrator()) {
                        row.icon.setContentDescription(mContext.getString(
                                mShowA11yStream
                                        ? R.string.volume_stream_content_description_vibrate_a11y
                                        : R.string.volume_stream_content_description_vibrate,
                                getStreamLabelH(ss)));
                    } else {
                        row.icon.setContentDescription(mContext.getString(
                                mShowA11yStream
                                        ? R.string.volume_stream_content_description_mute_a11y
                                        : R.string.volume_stream_content_description_mute,
                                getStreamLabelH(ss)));
                    }
                }
            } else if (isA11yStream) {
                row.icon.setContentDescription(getStreamLabelH(ss));
            } else {
                if (ss.muted || mAutomute && ss.level == 0) {
                   row.icon.setContentDescription(mContext.getString(
                           R.string.volume_stream_content_description_unmute,
                           getStreamLabelH(ss)));
                } else {
                    row.icon.setContentDescription(mContext.getString(
                            mShowA11yStream
                                    ? R.string.volume_stream_content_description_mute_a11y
                                    : R.string.volume_stream_content_description_mute,
                            getStreamLabelH(ss)));
                }
            }
        } else {
            row.icon.setContentDescription(getStreamLabelH(ss));
        }

        // ensure tracking is disabled if zenMuted or routedToSubmixAndEarphone
        if (zenMuted || routedToSubmixAndEarphone) {
            row.tracking = false;
        }
        enableVolumeRowViewsH(row, !zenMuted);

        // update slider
        final boolean enableSlider = !zenMuted && !routedToSubmixAndEarphone;
        final int vlevel = routedToSubmixAndEarphone ? max : row.ss.muted && (!isRingStream && !zenMuted) ? 0
                : row.ss.level;
        updateVolumeRowSliderH(row, enableSlider, vlevel, maxChanged);
    }

    private void updateVolumeRowTintH(VolumeRow row, boolean isActive) {
        if (isActive) {
            row.slider.requestFocus();
        }
        mVolumeAlpha = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.VOLUME_TINT_ALPHA, 255,
                UserHandle.USER_CURRENT);
        boolean useActiveColoring = (isActive && row.slider.isEnabled()) || mTintInActive;
        final ColorStateList tint = useActiveColoring
                ? Utils.getColorAccent(mContext).withAlpha(mDarkMode ?
                        SLIDER_PROGRESS_ALPHA_ACTIVE_DARK : SLIDER_PROGRESS_ALPHA_ACTIVE)
                : Utils.getColorAccent(mContext).withAlpha(mDarkMode ?
                        SLIDER_PROGRESS_ALPHA_DARK : SLIDER_PROGRESS_ALPHA);
        final int alpha = useActiveColoring
                ? Color.alpha(tint.getDefaultColor())
                : getAlphaAttr(android.R.attr.secondaryContentAlpha);
        final ColorStateList progressTint = useActiveColoring ? null : tint;
        if (tint == row.cachedTint) return;
        row.slider.setProgressTintList(progressTint);
        row.slider.setThumbTintList(tint);
        row.slider.setAlpha(((float) alpha)/ mVolumeAlpha);
        row.cachedTint = tint;
    }

    private void updateVolumeRowSliderH(VolumeRow row, boolean enable, int vlevel, boolean maxChanged) {
        row.slider.setEnabled(enable);
        updateVolumeRowTintH(row, row.stream == mActiveStream);
        if (row.tracking) {
            return;  // don't update if user is sliding
        }
        final int progress = row.slider.getProgress();
        final int level = getImpliedLevel(row.slider, progress);
        final boolean rowVisible = row.view.getVisibility() == VISIBLE;
        final boolean inGracePeriod = (SystemClock.uptimeMillis() - row.userAttempt)
                < USER_ATTEMPT_GRACE_PERIOD;
        mHandler.removeMessages(H.RECHECK, row);
        if (mShowing && rowVisible && inGracePeriod) {
            if (D.BUG) Log.d(TAG, "inGracePeriod");
            mHandler.sendMessageAtTime(mHandler.obtainMessage(H.RECHECK, row),
                    row.userAttempt + USER_ATTEMPT_GRACE_PERIOD);
            return;  // don't update if visible and in grace period
        }
        if (vlevel == level) {
            if (mShowing && rowVisible) {
                return;  // don't clamp if visible
            }
        }
        final int newProgress = vlevel * 100;
        if (progress != newProgress && !row.ss.muted) {
            if (mShowing && rowVisible) {
                // animate!
                if (row.anim != null && row.anim.isRunning()
                        && row.animTargetProgress == newProgress) {
                    return;  // already animating to the target progress
                }
                // start/update animation
                if (row.anim == null) {
                    row.anim = ObjectAnimator.ofInt(row.slider, "progress", progress, newProgress);
                    row.anim.setInterpolator(new DecelerateInterpolator());
                } else {
                    row.anim.cancel();
                    row.anim.setIntValues(progress, newProgress);
                }
                row.animTargetProgress = newProgress;
                row.anim.setDuration(UPDATE_ANIMATION_DURATION);
                row.anim.start();
            } else {
                // update slider directly to clamped value
                if (row.anim != null) {
                    row.anim.cancel();
                }
                row.slider.setProgress(newProgress, true);
            }
        }

        // update header text
        Util.setText(row.header, Utils.formatPercentage((enable && !row.ss.muted)
                        ? vlevel : 0, row.ss.levelMax));
    }

    private void recheckH(VolumeRow row) {
        if (row == null) {
            if (D.BUG) Log.d(TAG, "recheckH ALL");
            trimObsoleteH();
            for (VolumeRow r : mRows) {
                updateVolumeRowH(r);
            }
        } else {
            if (D.BUG) Log.d(TAG, "recheckH " + row.stream);
            updateVolumeRowH(row);
        }
    }

    private void setStreamImportantH(int stream, boolean important) {
        for (VolumeRow row : mRows) {
            if (row.stream == stream) {
                row.important = important;
                return;
            }
        }
    }

    private void showSafetyWarningH(int flags) {
        if ((flags & (AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_SHOW_UI_WARNINGS)) != 0
                || mShowing) {
            synchronized (mSafetyWarningLock) {
                if (mSafetyWarning != null) {
                    return;
                }
                mSafetyWarning = new SafetyWarningDialog(mContext, mController.getAudioManager()) {
                    @Override
                    protected void cleanUp() {
                        synchronized (mSafetyWarningLock) {
                            mSafetyWarning = null;
                        }
                        recheckH(null);
                    }
                };
                mSafetyWarning.show();
            }
            recheckH(null);
        }
        rescheduleTimeoutH();
    }

    private String getStreamLabelH(StreamState ss) {
        if (ss == null) {
            return "";
        }
        if (ss.remoteLabel != null) {
            return ss.remoteLabel;
        }
        try {
            return mContext.getResources().getString(ss.name);
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "Can't find translation for stream " + ss);
            return "";
        }
    }

    private Runnable getSinglePressFor(ImageButton button) {
        return () -> {
            if (button != null) {
                button.setPressed(true);
                button.postOnAnimationDelayed(getSingleUnpressFor(button), 200);
            }
        };
    }

    private Runnable getSingleUnpressFor(ImageButton button) {
        return () -> {
            if (button != null) {
                button.setPressed(false);
            }
        };
    }

    private final VolumeDialogController.Callbacks mControllerCallbackH
            = new VolumeDialogController.Callbacks() {
        @Override
        public void onShowRequested(int reason) {
            showH(reason);
        }

        @Override
        public void onDismissRequested(int reason) {
            dismissH(reason);
        }

        @Override
        public void onScreenOff() {
            dismissH(Events.DISMISS_REASON_SCREEN_OFF);
        }

        @Override
        public void onStateChanged(State state) {
            onStateChangedH(state);
        }

        @Override
        public void onLayoutDirectionChanged(int layoutDirection) {
            mDialogView.setLayoutDirection(layoutDirection);
        }

        @Override
        public void onConfigurationChanged() {
            mDialog.dismiss();
            mConfigChanged = true;
        }

        @Override
        public void onShowVibrateHint() {
            if (mSilentMode) {
                mController.setRingerMode(AudioManager.RINGER_MODE_SILENT, false);
            }
        }

        @Override
        public void onShowSilentHint() {
            if (mSilentMode) {
                mController.setRingerMode(AudioManager.RINGER_MODE_NORMAL, false);
            }
        }

        @Override
        public void onShowSafetyWarning(int flags) {
            showSafetyWarningH(flags);
        }

        @Override
        public void onAccessibilityModeChanged(Boolean showA11yStream) {
            mShowA11yStream = showA11yStream == null ? false : showA11yStream;
            VolumeRow activeRow = getActiveRow();
            if (!mShowA11yStream && STREAM_ACCESSIBILITY == activeRow.stream) {
                dismissH(Events.DISMISS_STREAM_GONE);
            } else {
                updateRowsH(activeRow);
            }

        }

        @Override
        public void onCaptionComponentStateChanged(
                Boolean isComponentEnabled, Boolean fromTooltip) {
            updateODICaptionsH(isComponentEnabled, fromTooltip);
        }
    };

    private final class H extends Handler {
        private static final int SHOW = 1;
        private static final int DISMISS = 2;
        private static final int RECHECK = 3;
        private static final int RECHECK_ALL = 4;
        private static final int SET_STREAM_IMPORTANT = 5;
        private static final int RESCHEDULE_TIMEOUT = 6;
        private static final int STATE_CHANGED = 7;
        private static final int PERFORM_HAPTIC_FEEDBACK = 8;
        private static final int UPDATE_MEDIA_OUTPUT_VIEW = 9;

        public H(Looper l) {
            super(l);
        }

        public H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW: showH(msg.arg1); break;
                case DISMISS: dismissH(msg.arg1); break;
                case RECHECK: recheckH((VolumeRow) msg.obj); break;
                case RECHECK_ALL: recheckH(null); break;
                case SET_STREAM_IMPORTANT: setStreamImportantH(msg.arg1, msg.arg2 != 0); break;
                case RESCHEDULE_TIMEOUT: rescheduleTimeoutH(); break;
                case STATE_CHANGED: onStateChangedH(mState); break;
                case PERFORM_HAPTIC_FEEDBACK: provideSliderHapticFeedbackH(); break;
                case UPDATE_MEDIA_OUTPUT_VIEW: updateMediaOutputViewH(); break;
            }
        }
    }

    private final class CustomDialog extends Dialog implements DialogInterface {
        public CustomDialog(Context context) {
            super(context, R.style.qs_theme);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            rescheduleTimeoutH();
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onStart() {
            super.setCanceledOnTouchOutside(true);
            super.onStart();
        }

        @Override
        protected void onStop() {
            super.onStop();
            mHandler.sendEmptyMessage(H.RECHECK_ALL);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (mShowing) {
                dismissH(Events.DISMISS_REASON_TOUCH_OUTSIDE);
                return true;
            }
            return false;
        }
    }

    private final class VolumeSeekBarChangeListener implements OnSeekBarChangeListener {
        private final VolumeRow mRow;

        private VolumeSeekBarChangeListener(VolumeRow row) {
            mRow = row;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (D.BUG) Log.d(TAG, AudioSystem.streamToString(mRow.stream)
                    + " onProgressChanged " + progress + " fromUser=" + fromUser);
            if (!fromUser) return;
            if (mRow.isAppVolumeRow) {
                mController.getAudioManager().setAppVolume(mRow.packageName, progress * 0.01f);
                return;
            }
            if (mRow.ss == null) return;
            if ((mRow.stream == STREAM_RING || mRow.stream == STREAM_NOTIFICATION) && mHasAlertSlider) {
                if (mRow.ss.muted) {
                    seekBar.setProgress(0);
                    return;
                }
            }
            if (mRow.ss.levelMin > 0) {
                final int minProgress = mRow.ss.levelMin * 100;
                if (progress < minProgress) {
                    seekBar.setProgress(minProgress);
                    progress = minProgress;
                }
            }
            final int userLevel = getImpliedLevel(seekBar, progress);

            if ((mRow.stream == STREAM_RING || mRow.stream == STREAM_NOTIFICATION) && mHasAlertSlider) {
                if (mRow.ss.level > mRow.ss.levelMin && userLevel == 0) {
                    seekBar.setProgress((mRow.ss.levelMin + 1) * 100);
                    Util.setText(mRow.header,
                            Utils.formatPercentage(mRow.ss.levelMin + 1, mRow.ss.levelMax));
                    return;
                }
            }

            Util.setText(mRow.header, Utils.formatPercentage(userLevel, mRow.ss.levelMax));
            if (mRow.ss.level != userLevel || mRow.ss.muted && userLevel > 0) {
                mRow.userAttempt = SystemClock.uptimeMillis();
                if (mRow.requestedLevel != userLevel) {
                    mController.setActiveStream(mRow.stream);
                    mController.setStreamVolume(mRow.stream, userLevel);
                    mRow.requestedLevel = userLevel;
                    Events.writeEvent(mContext, Events.EVENT_TOUCH_LEVEL_CHANGED, mRow.stream,
                            userLevel);

                    if (mVibrateOnSlider && !mHandler.hasMessages(H.PERFORM_HAPTIC_FEEDBACK)) {
                        mHandler.sendEmptyMessageDelayed(H.PERFORM_HAPTIC_FEEDBACK, 20);
                    }
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mRow.tracking = true;
            if (mRow.isAppVolumeRow) return;
            if (D.BUG) Log.d(TAG, "onStartTrackingTouch"+ " " + mRow.stream);
            mController.setActiveStream(mRow.stream);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (D.BUG) Log.d(TAG, "onStopTrackingTouch"+ " " + mRow.stream);
            mRow.tracking = false;
            if (mRow.isAppVolumeRow) return;
            mRow.userAttempt = SystemClock.uptimeMillis();
            final int userLevel = getImpliedLevel(seekBar, seekBar.getProgress());
            Events.writeEvent(mContext, Events.EVENT_TOUCH_LEVEL_DONE, mRow.stream, userLevel);
            if (mRow.ss.level != userLevel) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(H.RECHECK, mRow),
                        USER_ATTEMPT_GRACE_PERIOD);
            }
        }
    }

    private final class Accessibility extends AccessibilityDelegate {
        public void init() {
            mDialogView.setAccessibilityDelegate(this);
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            // Activities populate their title here. Follow that example.
            event.getText().add(composeWindowTitle());
            return true;
        }

        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                AccessibilityEvent event) {
            rescheduleTimeoutH();
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    }

    private boolean isAudioPanelOnLeftSide() {
        return mVolumePanelOnLeft;
    }

    private static class VolumeRow {
        private View view;
        private TextView header;
        private ImageView icon;
        private SeekBar slider;
        private int stream;
        private StreamState ss;
        private long userAttempt;  // last user-driven slider change
        private boolean tracking;  // tracking slider touch
        private int requestedLevel = -1;  // pending user-requested level via progress changed
        private int iconRes;
        private int iconMuteRes;
        private boolean important;
        private boolean defaultStream;
        private ColorStateList cachedTint;
        private ObjectAnimator anim;  // slider progress animation for non-touch-related updates
        private int animTargetProgress;
        private FrameLayout dndIcon;
        /* for change app's volume */
        private String packageName;
        private boolean isAppVolumeRow = false;
        private boolean appMuted;
    }

    private static class MediaOutputRow {
        private View view;
        private TextView name;
        private TextView summary;
        private ImageView icon;
        private ImageView selected;
        private MediaDevice device;
        private boolean addedToGroup;
    }
}

