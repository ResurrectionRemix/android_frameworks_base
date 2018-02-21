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

import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import static com.android.systemui.statusbar.phone.StatusBar.reinflateSignalCluster;

import android.annotation.Nullable;
import android.app.Fragment;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.utils.du.UserContentObserver;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.ClockLeft;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.rr.carrierlabel.CarrierLabel;
/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";

    private static final int CLOCK_DATE_POSITION_DEFAULT  = 0;
    private static final int CLOCK_DATE_POSITION_CENTERED = 1;
    private static final int CLOCK_DATE_POSITION_HIDDEN   = 2;
    private static final int CLOCK_DATE_POSITION_LEFT   = 3;

    private PhoneStatusBarView mStatusBar;
    private KeyguardMonitor mKeyguardMonitor;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private View mNotificationIconAreaInner;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private SignalClusterView mSignalClusterView;
    private Clock mClockDefault;
    private Clock mClockCentered;
    private ClockLeft mLeftClock;
    private View mCenterClockLayout;

    private int mClockPosition = CLOCK_DATE_POSITION_DEFAULT;

    private int mTickerEnabled;
    private ContentResolver mContentResolver;
    private View mTickerViewFromStub;

    // Custom Carrier
    private View mCustomCarrierLabel;
    private View mBatteryBar;
    private int mShowCarrierLabel;
    private int mCarrierFontSize;

    private ImageView mRRLogo;
    private ImageView mRRLogoRight;
    private View mWeatherImageView;
    private View mWeatherTextView;

    private int mShowLogo;
    private int mShowWeather;
    // Custom Logos
    private ImageView mCLogo;
    private ImageView mCLogoRight;
    private int mCustomlogoStyle;
    private int mCustomLogoPos;
    private RRSettingsObserver mRRSettingsObserver;


    private class RRSettingsObserver extends UserContentObserver {
        RRSettingsObserver(Handler handler) {
            super(handler);
        }

        protected void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CARRIER),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.CUSTOM_LOGO_STYLE),
                   false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.CUSTOM_LOGO_POSITION),
                   false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.STATUS_BAR_SHOW_TICKER), 
                   false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SHOW_SECONDS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SHOW_DATE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_DATE_SIZE_SMALL),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_AM_PM_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SIZE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_FONT_STYLE),
                    false, this, UserHandle.USER_ALL);
        }

        protected void unobserve() {
            super.unobserve();
            getContext().getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            updateSettings(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings(true);
        }
    }

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mStatusBarComponent.recomputeDisableFlags(true /* animate */);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContext().getContentResolver();
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarComponent = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
        mRRSettingsObserver = new RRSettingsObserver(new Handler());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.go(savedInstanceState.getInt(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons));
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mSignalClusterView = mStatusBar.findViewById(R.id.signal_cluster);
        mClockDefault = (Clock) mStatusBar.findViewById(R.id.clock);
        mLeftClock = mStatusBar.findViewById(R.id.left_clock);
        mClockCentered = (Clock) mStatusBar.findViewById(R.id.center_clock);
        mCenterClockLayout = mStatusBar.findViewById(R.id.center_clock_layout);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mSignalClusterView);
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);
        mRRLogo =  (ImageView) mStatusBar.findViewById(R.id.status_bar_logo);
        mRRLogoRight = (ImageView) mStatusBar.findViewById(R.id.status_bar_logo_right);
        mCLogo = (ImageView) mStatusBar.findViewById(R.id.custom);
        mCLogoRight = (ImageView) mStatusBar.findViewById(R.id.custom_right);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mRRLogo);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mRRLogoRight);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mCLogo);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mCLogoRight);
        mWeatherTextView = mStatusBar.findViewById(R.id.weather_temp);
        mWeatherImageView = mStatusBar.findViewById(R.id.weather_image);
        mBatteryBar = mStatusBar.findViewById(R.id.battery_bar);
        // Default to showing until we know otherwise.
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();

        mRRSettingsObserver.observe();
        updateSettings(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_PANEL_STATE, mStatusBar.getState());
    }

    @Override
    public void onResume() {
        super.onResume();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).addCallbacks(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).removeCallbacks(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mSignalClusterView);
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mRRLogo);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mRRLogoRight);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mCLogo);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mCLogoRight);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
        mRRSettingsObserver.unobserve();
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
        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
            } else {
                showSystemIconArea(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
                hideCarrierName(animate);
                hideLeftClock(animate);
            } else {
                showNotificationIconArea(animate);
                showCarrierName(animate);
                showLeftClock(animate);
            }
        }
    }

    protected int adjustDisableFlags(int state) {
        if (!mStatusBarComponent.isLaunchTransitionFadingAway()
                && !mKeyguardMonitor.isKeyguardFadingAway()
                && shouldHideNotificationIcons()) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
        }
        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
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
        animateHide(mSystemIconArea, animate, true);
        if (mShowLogo == 2) {
            animateHide(mRRLogoRight, animate, false);
        }
        if (mCustomLogoPos == 2) {
            animateHide(mCLogoRight, animate, false);
        }
        animateHide(mBatteryBar, animate, true);
        if (mClockPosition == CLOCK_DATE_POSITION_CENTERED) {
            animateHide(mCenterClockLayout, animate ,false);
        }
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        if (mShowLogo == 2) {
            animateShow(mRRLogoRight, animate);
        }
        if (mCustomLogoPos == 2) {
            animateShow(mCLogoRight, animate);
        }
        animateShow(mBatteryBar, animate);
        if (mClockPosition == CLOCK_DATE_POSITION_CENTERED) {
            animateShow(mCenterClockLayout, animate);
        }
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate, true);
        if (mShowLogo == 1) {
            animateHide(mRRLogo, animate, false);
        }
        if (mCustomLogoPos == 1) {
            animateHide(mCLogo, animate, false);
        }
        animateHide(mBatteryBar, animate, true);
        if (mClockPosition == CLOCK_DATE_POSITION_CENTERED) {
            animateHide(mCenterClockLayout, animate ,false);
        }
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
        if (mShowLogo == 1) {
            animateShow(mRRLogo, animate);
        }
        if (mCustomLogoPos == 1) {
            animateShow(mCLogo, animate);
        }
        animateShow(mBatteryBar, animate);
        if (mClockPosition == CLOCK_DATE_POSITION_CENTERED) {
            animateShow(mCenterClockLayout, animate);
        }
    }

    public void hideCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            animateHide(mCustomCarrierLabel, animate, false);
        }
    }

    public void showCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            setCarrierLabel(animate);
        }
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate, final boolean invisible) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(invisible ? View.INVISIBLE : View.GONE);
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(invisible ? View.INVISIBLE : View.GONE));
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(50)

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

    private void setCarrierLabel(boolean animate) {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            animateShow(mCustomCarrierLabel, animate);
        } else {
            animateHide(mCustomCarrierLabel, animate, false);
        }
    }

    public void updateSettings(boolean animate) {
        try {
        mShowCarrierLabel = Settings.System.getInt(
                mContentResolver, Settings.System.STATUS_BAR_CARRIER, 1);
        mShowLogo = Settings.System.getInt(
                mContentResolver, Settings.System.STATUS_BAR_LOGO, 0);
        mShowWeather = Settings.System.getInt(
                mContentResolver, Settings.System.STATUS_BAR_SHOW_WEATHER_TEMP, 0);
        mCustomlogoStyle = Settings.System.getInt(
                mContentResolver, Settings.System.CUSTOM_LOGO_STYLE, 0);
        mCustomLogoPos = Settings.System.getInt(
                    mContentResolver, Settings.System.CUSTOM_LOGO_POSITION, 0);
        mTickerEnabled = Settings.System.getInt(
                     mContentResolver, Settings.System.STATUS_BAR_SHOW_TICKER, 0);

       } catch (Exception e) {
       }
        setCarrierLabel(animate);
        initTickerView();
        mStatusBarComponent.updateBatterySettings();
	    updateCustomLogo();
        setUpClock();
        updateClockStyle(animate);
        if (mNotificationIconAreaInner != null) {
            if (mShowLogo == 1) {
                if (mNotificationIconAreaInner.getVisibility() == View.VISIBLE) {
                    animateShow(mRRLogo, animate);
                }
            } else if (mShowLogo != 1) {
                animateHide(mRRLogo, animate, false);
            }
        }
        if (mSystemIconArea != null) {
            if (mShowLogo == 2) {
                if (mSystemIconArea.getVisibility() == View.VISIBLE) {
                    animateShow(mRRLogoRight, animate);
                }
            } else if (mShowLogo != 2) {
                animateHide(mRRLogoRight, animate, false);
            }
        }
        if (mNotificationIconAreaInner != null) {
            if (mCustomLogoPos == 1) {
                if (mNotificationIconAreaInner.getVisibility() == View.VISIBLE) {
                    animateShow(mCLogo, animate);
                }
            } else if (mCustomLogoPos != 1) {
                animateHide(mCLogo, animate, false);
            }
        }
        if (mSystemIconArea != null) {
            if (mCustomLogoPos == 2) {
                if (mSystemIconArea.getVisibility() == View.VISIBLE) {
                    animateShow(mCLogoRight, animate);
                }
            } else if (mCustomLogoPos != 2) {
                animateHide(mCLogoRight, animate, false);
            }
        }
    }

    private void setUpClock() {
        updateClockDatePosition();
        updateClockShowSeconds();
        updateClockShowDate();
        updateClockDateFormat();
        updateClockDateStyle();
        updateClockShowDateSizeSmall();
        updateClockAmPmStyle();
        updateClockFont();
        updateClockSize();
    }

    private void updateClockDatePosition() {
        int position =  Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_DATE_POSITION, CLOCK_DATE_POSITION_DEFAULT);

        if (mClockPosition != position) {
            mClockPosition = position;

            switch (mClockPosition) {
                case CLOCK_DATE_POSITION_DEFAULT:
                    mClockDefault.setClockVisibleByUser(true);
                    mCenterClockLayout.setVisibility(View.GONE);
                    mClockCentered.setClockVisibleByUser(false);
                   // mLeftClock.setClockVisibleByUser(false);
                    break;
                case CLOCK_DATE_POSITION_CENTERED:
                    mClockDefault.setClockVisibleByUser(false);
                    mCenterClockLayout.setVisibility(View.VISIBLE);
                    mClockCentered.setClockVisibleByUser(true);
                    //mLeftClock.setClockVisibleByUser(false);
                    break;
                case CLOCK_DATE_POSITION_HIDDEN:
                    mClockDefault.setClockVisibleByUser(false);
                    mCenterClockLayout.setVisibility(View.GONE);
                    mClockCentered.setClockVisibleByUser(false);
                    //mLeftClock.setClockVisibleByUser(false);
                    break;
                case CLOCK_DATE_POSITION_LEFT:
                    mClockDefault.setClockVisibleByUser(false);
                    mCenterClockLayout.setVisibility(View.GONE);
                    mClockCentered.setClockVisibleByUser(false);
                    break;
            }
        }
    }

    public void updateCustomLogo() {
        Drawable d = null;
        int style = mCustomlogoStyle;

        if ( style == 0) {
            d = getContext().getResources().getDrawable(R.drawable.custom);
        } else if ( style == 1) {
            d = getContext().getResources().getDrawable(R.drawable.custom_1);
        } else if ( style == 2) {
            d = getContext().getResources().getDrawable(R.drawable.custom_2);
        } else if ( style == 3) {
            d = getContext().getResources().getDrawable(R.drawable.custom_3);
        } else if ( style == 4) {
            d = getContext().getResources().getDrawable(R.drawable.custom_4);
        } else if ( style == 5) {
            d = getContext().getResources().getDrawable(R.drawable.custom_5);
        } else if ( style == 6) {
            d = getContext().getResources().getDrawable(R.drawable.custom_6);
        } else if ( style == 7) {
            d = getContext().getResources().getDrawable(R.drawable.custom_7);
        } else if ( style == 8) {
            d = getContext().getResources().getDrawable(R.drawable.custom_8);
        } else if ( style == 9) {
            d = getContext().getResources().getDrawable(R.drawable.custom_9);
        } else if ( style == 10) {
            d = getContext().getResources().getDrawable(R.drawable.custom_10);
        }  else if ( style == 11) {
            d = getContext().getResources().getDrawable(R.drawable.custom_11);
        } else if ( style == 12) {
            d = getContext().getResources().getDrawable(R.drawable.custom_12);
        } else if ( style == 13) {
            d = getContext().getResources().getDrawable(R.drawable.custom_13);
        } else if ( style == 14) {
            d = getContext().getResources().getDrawable(R.drawable.custom_14);
        } else if ( style  == 15) {
            d = getContext().getResources().getDrawable(R.drawable.weather_off);
        } else if ( style  == 16) {
            d = getContext().getResources().getDrawable(R.drawable.blender);
        } else if ( style  == 17) {
            d = getContext().getResources().getDrawable(R.drawable.cake_variant);
        } else if ( style  == 18) {
            d = getContext().getResources().getDrawable(R.drawable.guitar_electric);
        } else if ( style  == 19) {
            d = getContext().getResources().getDrawable(R.drawable.tag_faces);
        } else if ( style  == 20) {
            d = getContext().getResources().getDrawable(R.drawable.run);
        } else if ( style  == 21) {
            d = getContext().getResources().getDrawable(R.drawable.radioactive);
        } else if ( style  == 22) {
            d = getContext().getResources().getDrawable(R.drawable.professional_hexagon);
        } else if ( style  == 23) {
            d = getContext().getResources().getDrawable(R.drawable.pokeball);
        } else if ( style  == 24) {
            d = getContext().getResources().getDrawable(R.drawable.package_variant);
        } else if ( style  == 25) {
            d = getContext().getResources().getDrawable(R.drawable.package_variant_closed);
        } else if ( style  == 26) {
            d = getContext().getResources().getDrawable(R.drawable.weather_fog);
        } else if ( style  == 27) {
            d = getContext().getResources().getDrawable(R.drawable.cat);
        } else if ( style == 28) {
            d = getContext().getResources().getDrawable(R.drawable.android1);
        } else if ( style == 29) {
            d = getContext().getResources().getDrawable(R.drawable.bike);
        } else if ( style == 30) {
            d = getContext().getResources().getDrawable(R.drawable.candycane);
        } else if ( style == 31) {
            d = getContext().getResources().getDrawable(R.drawable.shit);
        } else if ( style == 32) {
            d = getContext().getResources().getDrawable(R.drawable.chart_bubble);
        } else if ( style == 33) {
            d = getContext().getResources().getDrawable(R.drawable.google1);
        } else if ( style == 34) {
            d = getContext().getResources().getDrawable(R.drawable.fish);
        } else if ( style == 35) {
            d = getContext().getResources().getDrawable(R.drawable.gender_male);
        } else if ( style == 36) {
            d = getContext().getResources().getDrawable(R.drawable.gender_female);
        } else if ( style == 37) {
            d = getContext().getResources().getDrawable(R.drawable.pb_logo);
        } else if ( style == 38) {
            d = getContext().getResources().getDrawable(R.drawable.rr_original_logo_1);
        } else if ( style == 39) {
            d = getContext().getResources().getDrawable(R.drawable.rr_logo_half);
        } else if ( style == 40) {
            d = getContext().getResources().getDrawable(R.drawable.rr_noring);
        } else if ( style == 41) {
            d = getContext().getResources().getDrawable(R.drawable.spider1);
        } else if ( style == 42) {
            d = getContext().getResources().getDrawable(R.drawable.spider2);
        } else if ( style == 43) {
            d = getContext().getResources().getDrawable(R.drawable.orioles_logo);
        }

        if (mCustomLogoPos == 1) {
            mCLogo.setImageDrawable(null);
            mCLogo.setImageDrawable(d);
        } else if (mCustomLogoPos == 2) {
            mCLogoRight.setImageDrawable(null);
            mCLogoRight.setImageDrawable(d);
        }
    }

    private void updateClockSize() {
        int size = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_SIZE, 14);
        mClockDefault.setClockSize(size);
        mClockCentered.setClockSize(size);
        mLeftClock.setClockSize(size);

    }

    private void updateClockFont() {
        int size = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_FONT_STYLE, 0);
        mClockDefault.setClockStyle(size);
        mClockCentered.setClockStyle(size);
        mLeftClock.setClockStyle(size);

    }


    private void updateClockAmPmStyle() {
        int style = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_AM_PM_STYLE, 0);
        mClockDefault.setAmPmStyle(style);
        mClockCentered.setAmPmStyle(style);
        mLeftClock.setAmPmStyle(style);
    }


    private void updateClockShowSeconds() {
        boolean show = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_SHOW_SECONDS, 0) == 1;
        mClockDefault.setShowSeconds(show);
        mClockCentered.setShowSeconds(show);
        mLeftClock.setShowSeconds(show);
    }

    private void updateClockShowDate() {
        boolean show = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_SHOW_DATE, 0) == 1;
        mClockDefault.setShowDate(show);
        mClockCentered.setShowDate(show);
        mLeftClock.setShowDate(show);
    }

    private void updateClockDateFormat() {
        String format = Settings.System.getString(mContentResolver,
                Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT);
        mClockDefault.setDateFormat(format);
        mClockCentered.setDateFormat(format);
        mLeftClock.setDateFormat(format);
    }

    private void updateClockDateStyle() {
        int style = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_DATE_STYLE, Clock.DATE_STYLE_REGULAR);
        mClockDefault.setDateStyle(style);
        mClockCentered.setDateStyle(style);
        mLeftClock.setDateStyle(style);
    }

    private void updateClockShowDateSizeSmall() {
        boolean small = Settings.System.getInt(mContentResolver,
			    Settings.System.STATUS_BAR_CLOCK_DATE_SIZE_SMALL, 0) == 1;
        mClockDefault.setShowDateSizeSmall(small);
        mClockCentered.setShowDateSizeSmall(small);
        mLeftClock.setShowDateSizeSmall(small);
    }

     public void hideLeftClock(boolean animate) {
         if (mLeftClock != null) {
             animateHide(mLeftClock, animate, false);
         }
     }
 
     public void showLeftClock(boolean animate) {
         updateClockStyle(animate);
     }

 
     private void updateClockStyle(boolean animate) {
        if (mClockPosition == 0 || mClockPosition == 1 || mClockPosition == 2) {
            animateHide(mLeftClock, animate, false);
        } else {
            animateShow(mLeftClock, animate);
        }
    }
 
}
