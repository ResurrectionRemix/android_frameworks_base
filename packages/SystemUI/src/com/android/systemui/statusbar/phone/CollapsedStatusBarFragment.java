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

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_CLOCK;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import android.annotation.Nullable;
import android.app.Fragment;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserHandle;
import android.net.Uri;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageSwitcher;
import android.widget.LinearLayout;

import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks,
        StatusBarStateController.StateListener {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";
    public static final String STATUS_BAR_ICON_MANAGER_TAG = "status_bar_icon_manager";
    public static final int FADE_IN_DURATION = 320;
    public static final int FADE_IN_DELAY = 50;
    private PhoneStatusBarView mStatusBar;
    private StatusBarStateController mStatusBarStateController;
    private KeyguardMonitor mKeyguardMonitor;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private LinearLayout mCustomIconArea;
    private View mStatusBarLogo, mStatusBarLogoRight;
    private View mNotificationIconAreaInner;
    private View mCenteredIconArea;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private View mOperatorNameFrame;
    private CommandQueue mCommandQueue;
    private View mWeatherImageView;
    private View mWeatherTextView;
    private int mShowWeather;
    private boolean mWeatherInHeaderView;
    private ContentResolver mContentResolver;
    private final Handler mHandler = new Handler();

   private class SettingsObserver extends ContentObserver {
       SettingsObserver(Handler handler) {
           super(handler);
       }

       void observe() {
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_TICKER),
                    false, this, UserHandle.USER_ALL);
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_CARRIER),
                    false, this, UserHandle.USER_ALL);
       }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
              updateSettings(true);
        }
    }

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    // RR additions
    private View mBatteryBars[] = new View[2];

    // custom carrier label
    private View mCustomCarrierLabel;
    private int mShowCarrierLabel;

    // Notification ticker
    private int mTickerEnabled;
    private View mTickerViewFromStub;
    // clock position
    private ClockController mClockController;

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mCommandQueue.recomputeDisableFlags(getContext().getDisplayId(), true /* animate */);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContext().getContentResolver();
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarComponent = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
        mCommandQueue = SysUiServiceProvider.getComponent(getContext(), CommandQueue.class);
        mSettingsObserver.observe();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
         if(isDualStatusbarEnabled()) {
            return inflater.inflate(R.layout.status_bar_dual, container, false);
         } else {
             return inflater.inflate(R.layout.status_bar, container, false);
         }
    }

    private boolean isDualStatusbarEnabled() {
        return Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.STATUSBAR_DUAL, 0) == 1;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.restoreHierarchyState(
                    savedInstanceState.getSparseParcelableArray(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons));
        mDarkIconManager.setShouldLog(true);
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mCustomIconArea = mStatusBar.findViewById(R.id.left_icon_area);
        mClockController = new ClockController(mStatusBar);
        mBatteryBars[0] = mStatusBar.findViewById(R.id.battery_bar);
        mBatteryBars[1] = mStatusBar.findViewById(R.id.battery_bar_1);
        mStatusBarLogo = mStatusBar.findViewById(R.id.statusbar_logo);
        mStatusBarLogoRight = mStatusBar.findViewById(R.id.statusbar_logo_right);
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);
        mWeatherTextView = mStatusBar.findViewById(R.id.weather_temp);
        mWeatherImageView = mStatusBar.findViewById(R.id.weather_image);
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();
        initOperatorName();
        mSettingsObserver.observe();
        updateSettings(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SparseArray<Parcelable> states = new SparseArray<>();
        mStatusBar.saveHierarchyState(states);
        outState.putSparseParcelableArray(EXTRA_PANEL_STATE, states);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCommandQueue.addCallback(this);
        mStatusBarStateController.addCallback(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCommandQueue.removeCallback(this);
        mStatusBarStateController.removeCallback(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
    }

    public void initNotificationIconArea(NotificationIconAreaController
            notificationIconAreaController) {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                notificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);

        ViewGroup statusBarCenteredIconArea = mStatusBar.findViewById(R.id.centered_icon_area);
        mCenteredIconArea = notificationIconAreaController.getCenteredNotificationAreaView();
        if (mCenteredIconArea.getParent() != null) {
            ((ViewGroup) mCenteredIconArea.getParent())
                    .removeView(mCenteredIconArea);
        }
        statusBarCenteredIconArea.addView(mCenteredIconArea);

        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int displayId, int state1, int state2, boolean animate) {
        if (displayId != getContext().getDisplayId()) {
            return;
        }
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
                hideOperatorName(animate);
            } else {
                showSystemIconArea(animate);
                showOperatorName(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
                hideCarrierName(animate);
            } else {
                showNotificationIconArea(animate);
                showCarrierName(animate);
            }
        }
        // The clock may have already been hidden, but we might want to shift its
        // visibility to GONE from INVISIBLE or vice versa
        if ((diff1 & DISABLE_CLOCK) != 0 ||
                mClockController.getClock().getVisibility() != clockHiddenMode()) {
            if ((state1 & DISABLE_CLOCK) != 0) {
                hideClock(animate);
            } else {
                showClock(animate);
            }
        }
    }


    protected int adjustDisableFlags(int state) {
        boolean headsUpVisible = mStatusBarComponent.headsUpShouldBeVisible();
        if (headsUpVisible) {
            state |= DISABLE_CLOCK;
        }

        if (!mKeyguardMonitor.isLaunchTransitionFadingAway()
                && !mKeyguardMonitor.isKeyguardFadingAway()
                && shouldHideNotificationIcons()
                && !(mStatusBarStateController.getState() == StatusBarState.KEYGUARD
                        && headsUpVisible)) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
            state |= DISABLE_CLOCK;
        }


        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }

        // The shelf will be hidden when dozing with a custom clock, we must show notification
        // icons in this occasion.
        if (mStatusBarStateController.isDozing()
                && mStatusBarComponent.getPanel().hasCustomClock()) {
            state |= DISABLE_CLOCK | DISABLE_SYSTEM_INFO;
        }

        return state;
    }

    private boolean shouldHideNotificationIcons() {
        if (!mStatusBar.isClosed() && mStatusBarComponent.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        if (mStatusBarComponent.hideStatusBarIconsForBouncer()) {
            return true;
        }
        return false;
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        for (View batteryBar: mBatteryBars) {
            animateHide(batteryBar, animate);
        }
        animateHide(mStatusBarLogoRight, animate);
        animateHide(mClockController.getClockLayout(), animate);
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        for (View batteryBar: mBatteryBars) {
            animateShow(batteryBar, animate);
        }
        animateShow(mStatusBarLogoRight, animate);
        animateShow(mClockController.getClockLayout(), animate);
    }

    public void hideClock(boolean animate) {
        animateHiddenState(mClockController.getClock(), clockHiddenMode(), animate);
    }

    public void showClock(boolean animate) {
        animateShow(mClockController.getClock(), animate);

    }

    /**
     * If panel is expanded/expanding it usually means QS shade is opening, so
     * don't set the clock GONE otherwise it'll mess up the animation.
     */
    private int clockHiddenMode() {
        if (!mStatusBar.isClosed() && !mKeyguardMonitor.isShowing()
                && !mStatusBarStateController.isDozing()) {
            return View.INVISIBLE;
        }
        return View.GONE;
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate);
        animateHide(mCenteredIconArea, animate);
        animateHide(mCustomIconArea, animate);
        animateHide(mClockController.getClockLayout(), animate);
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
        animateShow(mCenteredIconArea, animate);
        animateShow(mCustomIconArea, animate);
        animateShow(mClockController.getClockLayout(), animate);
    }

    public void hideOperatorName(boolean animate) {
        if (mOperatorNameFrame != null) {
            animateHide(mOperatorNameFrame, animate);
        }
    }

    public void showOperatorName(boolean animate) {
        if (mOperatorNameFrame != null) {
            animateShow(mOperatorNameFrame, animate);
        }
    }

    public void hideCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            animateHiddenState(mCustomCarrierLabel, View.GONE, animate);
        }
    }

    public void showCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            setCarrierLabel(animate);
        }
    }

    /**
     * Animate a view to INVISIBLE or GONE
     */
    private void animateHiddenState(final View v, int state, boolean animate) {
        if (v instanceof Clock && !((Clock)v).isClockVisible()) {
            return;
        }
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(state);
            return;
        }

        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(state));
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate) {
        animateHiddenState(v, View.INVISIBLE, animate);
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        if (v instanceof Clock && !((Clock)v).isClockVisible()) {
            return;
        }
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(FADE_IN_DURATION)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(FADE_IN_DELAY)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardMonitor.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardMonitor.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardMonitor.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

    private void initOperatorName() {
        if (getResources().getBoolean(R.bool.config_showOperatorNameInStatusBar)) {
            ViewStub stub = mStatusBar.findViewById(R.id.operator_name);
            mOperatorNameFrame = stub.inflate();
        }
    }

    @Override
    public void onStateChanged(int newState) {

    }

    public void updateSettings(boolean animate) {
        if (mStatusBar == null) return;

        if (getContext() == null) {
            return;
        }

        try {
        mShowCarrierLabel = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_SHOW_CARRIER, 1,
                UserHandle.USER_CURRENT);
        setCarrierLabel(animate);
        mTickerEnabled = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, 0,
                UserHandle.USER_CURRENT);
       } catch (Exception e) {
       }
        initTickerView();
    }

    @Override
    public void onDozingChanged(boolean isDozing) {
        disable(getContext().getDisplayId(), mDisabled1, mDisabled1, false /* animate */);
    }

    private void setCarrierLabel(boolean animate) {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            animateShow(mCustomCarrierLabel, animate);
        } else {
            animateHiddenState(mCustomCarrierLabel, View.GONE, false);
        }
    }

    private void initTickerView() {
        if (mTickerEnabled != 0) {
            View tickerStub = mStatusBar.findViewById(R.id.ticker_stub);
            if (mTickerViewFromStub == null && tickerStub != null) {
                mTickerViewFromStub = ((ViewStub) tickerStub).inflate();
            }
            TickerView tickerView = (TickerView) mStatusBar.findViewById(R.id.tickerText);
            ImageSwitcher tickerIcon = (ImageSwitcher) mStatusBar.findViewById(R.id.tickerIcon);
            mStatusBarComponent.createTicker(
                    mTickerEnabled, getContext(), mStatusBar, tickerView, tickerIcon, mTickerViewFromStub);
        } else {
            mStatusBarComponent.disableTicker();
        }
    }
}
