/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.settingslib.Utils;
import com.android.settingslib.drawable.UserIconDrawable;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.R.dimen;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.statusbar.phone.MultiUserSwitch;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import javax.inject.Inject;
import javax.inject.Named;

public class QSFooterImpl extends FrameLayout implements QSFooter,
        OnClickListener,  OnLongClickListener, OnUserInfoChangedListener, Tunable {

    private static final String TAG = "QSFooterImpl";
    public static final String QS_SHOW_DRAG_HANDLE =
            "system:" + Settings.System.QS_DRAG_HANDLE;
    public static final String QS_SHOW_AUTO_BRIGHTNESS_BUTTON =
            "system:" + Settings.System.QS_FOOTER_AUTO_ICON;
    public static final String QS_FOOTER_SHOW_SETTINGS = "qs_footer_show_settings";


    private final ActivityStarter mActivityStarter;
    private final UserInfoController mUserInfoController;
    private final DeviceProvisionedController mDeviceProvisionedController;
    private SettingsButton mSettingsButton;
    protected View mSettingsContainer;
    private PageIndicator mPageIndicator;
    private View mRunningServicesButton;

    private boolean mQsDisabled;
    private QSPanel mQsPanel;

    private boolean mExpanded;

    private boolean mListening;

    private int mQsSettings;

    protected MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;

    protected TouchAnimator mFooterAnimator;
    private float mExpansionAmount;

    protected View mEdit;
    protected View mEditContainer;
    private TouchAnimator mSettingsCogAnimator;

    private View mActionsContainer;
    private View mDragHandle;

    private OnClickListener mExpandClickListener;

    private ImageView mAutoBrightnessIcon;
    protected View mAutoBrightnessContainer;
    private boolean mShowAutoBrightnessButton = false;
    private boolean mAutoBrightOn;



    private final ContentObserver mSettingsObserver = new ContentObserver(
            new Handler(mContext.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            setBuildText();
            updateResources();
        }
    };


    @Inject
    public QSFooterImpl(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            ActivityStarter activityStarter, UserInfoController userInfoController,
            DeviceProvisionedController deviceProvisionedController) {
        super(context, attrs);
        mActivityStarter = activityStarter;
        mUserInfoController = userInfoController;
        mDeviceProvisionedController = deviceProvisionedController;
    }

    @VisibleForTesting
    public QSFooterImpl(Context context, AttributeSet attrs) {
        this(context, attrs,
                Dependency.get(ActivityStarter.class),
                Dependency.get(UserInfoController.class),
                Dependency.get(DeviceProvisionedController.class));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEdit = findViewById(android.R.id.edit);
        mEdit.setOnClickListener(view ->
                mActivityStarter.postQSRunnableDismissingKeyguard(() ->
                        mQsPanel.showEdit(view)));

        mPageIndicator = findViewById(R.id.footer_page_indicator);

        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mSettingsButton.setOnClickListener(this);
        mSettingsButton.setOnLongClickListener(this);

        mRunningServicesButton = findViewById(R.id.running_services_button);
        mRunningServicesButton.setOnClickListener(this);

        mMultiUserSwitch = findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = mMultiUserSwitch.findViewById(R.id.multi_user_avatar);

        mDragHandle = findViewById(R.id.qs_drag_handle_view);
        mActionsContainer = findViewById(R.id.qs_footer_actions_container);
        mEditContainer = findViewById(R.id.qs_footer_actions_edit_container);

        mAutoBrightnessContainer = findViewById(R.id.brightness_icon_container);
        mAutoBrightnessIcon = findViewById(R.id.brightness_icon);
        mAutoBrightnessIcon.setOnClickListener(this);

        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);
        ((RippleDrawable) mRunningServicesButton.getBackground()).setForceSoftware(true);

        updateResources();

        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                oldBottom) -> updateAnimator(right - left));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateEverything();
        setBuildText();
    }

    private void setBuildText() {
        TextView v = findViewById(R.id.build);
        if (v == null) return;
        boolean isShow = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.RR_FOOTER_TEXT_SHOW, 0,
                        UserHandle.USER_CURRENT) == 1;
        String text = Settings.System.getStringForUser(mContext.getContentResolver(),
                        Settings.System.RR_FOOTER_TEXT_STRING,
                        UserHandle.USER_CURRENT);
        if (isShow) {
            if (text == null || text == "") {
                v.setText("Resurrection Remix");
                v.setVisibility(View.VISIBLE);
            } else {
                v.setText(text);
                v.setVisibility(View.VISIBLE);
            }
        } else {
              v.setVisibility(View.GONE);
        }
    }

    private void updateAnimator(int width) {
        mSettingsCogAnimator = new Builder()
                .addFloat(mSettingsButton, "rotation", -120, 0)
                .build();

        setExpansion(mExpansionAmount);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
        updateEverything();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void updateResources() {
        updateFooterAnimator();
    }

    private void updateFooterAnimator() {
        mFooterAnimator = createFooterAnimator();
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        if (isQsSettingsEnabled()) {
            return new TouchAnimator.Builder()
                    .addFloat(mActionsContainer, "alpha", 1, 1) // contains mRunningServicesButton
                    .addFloat(mEditContainer, "alpha", 0, 1)
                    .addFloat(mDragHandle, "alpha", 1, 0, 0)
                    .addFloat(mPageIndicator, "alpha", 0, 1)
                    .setStartDelay(0.15f)
                    .build();
        } else {
            return new TouchAnimator.Builder()
                    .addFloat(mActionsContainer, "alpha", 0, 1) // contains mRunningServicesButton
                    .addFloat(mEditContainer, "alpha", 0, 1)
                    .addFloat(mDragHandle, "alpha", 1, 0, 0)
                    .addFloat(mPageIndicator, "alpha", 0, 1)
                    .setStartDelay(0.15f)
                    .build();
        }
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        setExpansion(mExpansionAmount);
    }

    @Override
    public void setExpandClickListener(OnClickListener onClickListener) {
        mExpandClickListener = onClickListener;
    }

    @Override
    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();
    }

    @Override
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        if (mSettingsCogAnimator != null) mSettingsCogAnimator.setPosition(headerExpansionFraction);

        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        /*mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false,
                mDeveloperSettingsObserver, UserHandle.USER_ALL);*/


        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, QS_SHOW_DRAG_HANDLE);
        tunerService.addTunable(this, QS_SHOW_AUTO_BRIGHTNESS_BUTTON);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.RR_FOOTER_TEXT_SHOW), false,
                mSettingsObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SETTING_BUTTON_TOGGLE), false,
                mSettingsObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.RR_FOOTER_TEXT_STRING), false,
                mSettingsObserver, UserHandle.USER_ALL);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        Dependency.get(TunerService.class).removeTunable(this);
        setListening(false);
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (QS_SHOW_DRAG_HANDLE.equals(key)) {
            boolean showdrag = TunerService.parseIntegerSwitch(newValue, false);
            setHideDragHandle(showdrag);
        }
        if (QS_SHOW_AUTO_BRIGHTNESS_BUTTON.equals(key)) {
            boolean show = TunerService.parseIntegerSwitch(newValue, true);
            setHideAutoBright(show);
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;
        updateListeners();
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_EXPAND) {
            if (mExpandClickListener != null) {
                mExpandClickListener.onClick(null);
                return true;
            }
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        updateEverything();
    }

    public void updateEverything() {
        post(() -> {
            updateVisibilities();
            updateClickabilities();
            setClickable(false);
        });
        if (mShowAutoBrightnessButton) {
            mAutoBrightOn = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    UserHandle.USER_CURRENT) != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
            setAutoBrightnessIcon(mAutoBrightOn);
        }
    }

    private void updateClickabilities() {
        mMultiUserSwitch.setClickable(mMultiUserSwitch.getVisibility() == View.VISIBLE);
        mEdit.setClickable(mEdit.getVisibility() == View.VISIBLE);
        mSettingsButton.setClickable(mSettingsButton.getVisibility() == View.VISIBLE);
    }

    private void updateVisibilities() {
        mSettingsContainer.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        mAutoBrightnessContainer.setVisibility(mShowAutoBrightnessButton ? View.GONE : View.VISIBLE);
        final boolean isDemo = UserManager.isDeviceInDemoMode(mContext);
        mSettingsContainer.setVisibility(isSettingsDisabled() || mQsDisabled ? View.GONE : View.VISIBLE);
        mSettingsButton.setVisibility(isSettingsDisabled() ? View.GONE : (isDemo || !mExpanded ? View.VISIBLE : View.VISIBLE));
        mRunningServicesButton.setVisibility(isRunningServicesEnabled() ? (isDemo || !mExpanded ? View.INVISIBLE : View.VISIBLE) : View.GONE);
        mMultiUserSwitch.setVisibility(isUserEnabled() ? (showUserSwitcher() ? View.VISIBLE : View.INVISIBLE) : View.GONE);
        mEditContainer.setVisibility(isDemo || !mExpanded ? View.INVISIBLE : View.VISIBLE);
        mAutoBrightnessIcon.setVisibility(mShowAutoBrightnessButton
                || !mExpanded ? View.INVISIBLE : View.VISIBLE);
        mEdit.setVisibility(isEditEnabled() ? View.VISIBLE : View.GONE);
    }

    private boolean showUserSwitcher() {
        return mExpanded && mMultiUserSwitch.isMultiUserEnabled();
    }

    private void updateListeners() {
        if (mListening) {
            mUserInfoController.addCallback(this);
        } else {
            mUserInfoController.removeCallback(this);
        }
    }

    @Override
    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        if (mQsPanel != null) {
            mMultiUserSwitch.setQsPanel(qsPanel);
            mQsPanel.setFooterPageIndicator(mPageIndicator);
        }
    }

    public boolean isRunningServicesEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_RUNNING_SERVICES_TOGGLE, 0) == 1;
    }

    public boolean isSettingsDisabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.SETTING_BUTTON_TOGGLE, 1) == 0;
    }

    public boolean isQsSettingsEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.SETTING_BUTTON_TOGGLE, 1) == 2;
    }

    public boolean isEditEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_FOOTER_SHOW_EDIT, 1) == 1;
    }

    public boolean isUserEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_FOOTER_SHOW_USER, 1) == 1;
    }

    @Override
    public void onClick(View v) {
        if (v == mSettingsButton) {
            if (!mDeviceProvisionedController.isCurrentUserSetup()) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                mActivityStarter.postQSRunnableDismissingKeyguard(() -> {
                });
                return;
            }
            MetricsLogger.action(mContext,
                    mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                            : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
            startSettingsActivity();
        } else if (v == mAutoBrightnessIcon) {
            if (mAutoBrightOn) {
                 Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        UserHandle.USER_CURRENT);
                 mAutoBrightOn = false;
            } else {
                 Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                        UserHandle.USER_CURRENT);
                 mAutoBrightOn = true;
            }
            setAutoBrightnessIcon(mAutoBrightOn);
        } else if (v == mRunningServicesButton) {
            MetricsLogger.action(mContext,
                    mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                            : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
            startRunningServicesActivity();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == mSettingsButton) {
            startRRActivity();
        }
        return false;
    }

    private void startRunningServicesActivity() {
        Intent intent = new Intent();
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings$DevRunningServicesActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startRRActivity() {
        Intent intent = new Intent();
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings$MainSettingsLayoutActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        if (picture != null &&
                UserManager.get(mContext).isGuestUser(KeyguardUpdateMonitor.getCurrentUser()) &&
                !(picture instanceof UserIconDrawable)) {
            picture = picture.getConstantState().newDrawable(mContext.getResources()).mutate();
            picture.setColorFilter(
                    Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorForeground),
                    Mode.SRC_IN);
        }
        mMultiUserAvatar.setImageDrawable(picture);
    }

    private void setHideDragHandle(boolean hide) {
        mDragHandle.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private void setHideAutoBright(boolean hide) {
        mAutoBrightnessIcon.setVisibility(hide ? View.GONE : View.VISIBLE);
        mShowAutoBrightnessButton = hide;
    }

    private void setAutoBrightnessIcon(boolean automatic) {
        mAutoBrightnessIcon.setImageResource(automatic ?
                com.android.systemui.R.drawable.ic_qs_brightness_auto_on :
                com.android.systemui.R.drawable.ic_qs_brightness_auto_off);
    }
}
