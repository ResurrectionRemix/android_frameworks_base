/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.internal.util.darkkat.StatusBarColorHelper;
import com.android.keyguard.CarrierText;
import com.android.systemui.BatteryLevelTextView;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.DockBatteryMeterView;
import com.android.systemui.R;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.DockBatteryController;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcher;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserSwitcherController;

import com.android.systemui.statusbar.phone.StatusBarIconController;

/**
 * The header group on Keyguard.
 */
public class KeyguardStatusBarView extends RelativeLayout {

    private boolean mKeyguardUserSwitcherShowing;

    private View mSystemIconsSuperContainer;
    private SignalClusterView mSignalCluster;
    private MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private BatteryLevelTextView mBatteryLevel;
    private BatteryMeterView mBatteryMeterView;

    private TextView mCarrierLabel;
    private int mShowCarrierLabel;

    private BatteryLevelTextView mDockBatteryLevel;

    private KeyguardUserSwitcher mKeyguardUserSwitcher;

    private int mSystemIconsSwitcherHiddenExpandedMargin;
    private Interpolator mFastOutSlowInInterpolator;
   
    public Boolean mColorSwitch = false ;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            showStatusBarCarrier();
            updateVisibilities();
        }
    };

    private UserInfoController mUserInfoController;

    public KeyguardStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        showStatusBarCarrier();
    }

    private void showStatusBarCarrier() {
        mShowCarrierLabel = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_SHOW_CARRIER, 1, UserHandle.USER_CURRENT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSystemIconsSuperContainer = findViewById(R.id.system_icons_super_container);
        mSignalCluster = (SignalClusterView) findViewById(R.id.signal_cluster);
        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = (ImageView) findViewById(R.id.multi_user_avatar);
        mBatteryLevel = (BatteryLevelTextView) findViewById(R.id.battery_level_text);
        mDockBatteryLevel = (BatteryLevelTextView) findViewById(R.id.dock_battery_level_text);
        mCarrierLabel = (TextView) findViewById(R.id.keyguard_carrier_text);
        mBatteryMeterView = new BatteryMeterView(mContext);
        loadDimens();
        mFastOutSlowInInterpolator = AnimationUtils.loadInterpolator(getContext(),
                android.R.interpolator.fast_out_slow_in);
        updateUserSwitcher();
        updateVisibilities();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Respect font size setting.
        mCarrierLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.text_size_small_material));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mUserInfoController != null) {
            mUserInfoController.removeListener(mUserInfoChangedListener);
        }
    }

    private void loadDimens() {
        mSystemIconsSwitcherHiddenExpandedMargin = getResources().getDimensionPixelSize(
                R.dimen.system_icons_switcher_hidden_expanded_margin);
    }

    private void updateVisibilities() {
    	int batterytext = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.BATTERY_TEXT_COLOR, 0xFFFFFFFF);
        if (mMultiUserSwitch.getParent() != this && !mKeyguardUserSwitcherShowing) {
            if (mMultiUserSwitch.getParent() != null) {
                getOverlay().remove(mMultiUserSwitch);
            }
            addView(mMultiUserSwitch, 0);
        } else if (mMultiUserSwitch.getParent() == this && mKeyguardUserSwitcherShowing) {
            removeView(mMultiUserSwitch);
        } if(mColorSwitch) {
        mBatteryLevel.setTextColor(batterytext);
        }
        mBatteryLevel.setVisibility(View.VISIBLE);

        if (mCarrierLabel != null) {
            if (mShowCarrierLabel == 1) {
                mCarrierLabel.setVisibility(View.VISIBLE);
            } else if (mShowCarrierLabel == 3) {
                mCarrierLabel.setVisibility(View.VISIBLE);
            } else {
                mCarrierLabel.setVisibility(View.GONE);
	      }
            }
        if (mDockBatteryLevel != null) {
            mDockBatteryLevel.setVisibility(View.VISIBLE);
	}
        
    }

    private void updateSystemIconsLayoutParams() {
        RelativeLayout.LayoutParams lp =
                (LayoutParams) mSystemIconsSuperContainer.getLayoutParams();
        int marginEnd = mKeyguardUserSwitcherShowing ? mSystemIconsSwitcherHiddenExpandedMargin : 0;
        if (marginEnd != lp.getMarginEnd()) {
            lp.setMarginEnd(marginEnd);
            mSystemIconsSuperContainer.setLayoutParams(lp);
        }
    }

    private void updateUserSwitcher() {
        boolean keyguardSwitcherAvailable = mKeyguardUserSwitcher != null;
        mMultiUserSwitch.setClickable(keyguardSwitcherAvailable);
        mMultiUserSwitch.setFocusable(keyguardSwitcherAvailable);
        mMultiUserSwitch.setKeyguardMode(keyguardSwitcherAvailable);
    }

    public void setBatteryController(BatteryController batteryController) {
        BatteryMeterView v = ((BatteryMeterView) findViewById(R.id.battery));
        v.setBatteryStateRegistar(batteryController);
        v.setBatteryController(batteryController);
        mBatteryLevel.setBatteryStateRegistar(batteryController);
    }

    public void setDockBatteryController(DockBatteryController dockBatteryController) {
        DockBatteryMeterView v = ((DockBatteryMeterView) findViewById(R.id.dock_battery));
        if (dockBatteryController != null) {
            v.setBatteryStateRegistar(dockBatteryController);
            mDockBatteryLevel.setBatteryStateRegistar(dockBatteryController);
        } else {
            if (v != null ) {
                removeView(v);
            }
            if (mDockBatteryLevel != null) {
                removeView(mDockBatteryLevel);
                mDockBatteryLevel = null;
            }
        }
    }

    public void setUserSwitcherController(UserSwitcherController controller) {
        mMultiUserSwitch.setUserSwitcherController(controller);
    }

    private UserInfoController.OnUserInfoChangedListener mUserInfoChangedListener =
            new UserInfoController.OnUserInfoChangedListener() {
        @Override
        public void onUserInfoChanged(String name, Drawable picture) {
            mMultiUserAvatar.setImageDrawable(picture);
        }
    };

    public void setUserInfoController(UserInfoController userInfoController) {
        mUserInfoController = userInfoController;
        userInfoController.addListener(mUserInfoChangedListener);
    }

    public void setKeyguardUserSwitcher(KeyguardUserSwitcher keyguardUserSwitcher) {
        mKeyguardUserSwitcher = keyguardUserSwitcher;
        mMultiUserSwitch.setKeyguardUserSwitcher(keyguardUserSwitcher);
        updateUserSwitcher();
    }

    public void setKeyguardUserSwitcherShowing(boolean showing, boolean animate) {
        mKeyguardUserSwitcherShowing = showing;
        if (animate) {
            animateNextLayoutChange();
        }
        updateVisibilities();
        updateSystemIconsLayoutParams();
    }

    private void animateNextLayoutChange() {
        final int systemIconsCurrentX = mSystemIconsSuperContainer.getLeft();
        final boolean userSwitcherVisible = mMultiUserSwitch.getParent() == this;
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                boolean userSwitcherHiding = userSwitcherVisible
                        && mMultiUserSwitch.getParent() != KeyguardStatusBarView.this;
                mSystemIconsSuperContainer.setX(systemIconsCurrentX);
                mSystemIconsSuperContainer.animate()
                        .translationX(0)
                        .setDuration(400)
                        .setStartDelay(userSwitcherHiding ? 300 : 0)
                        .setInterpolator(mFastOutSlowInInterpolator)
                        .start();
                if (userSwitcherHiding) {
                    getOverlay().add(mMultiUserSwitch);
                    mMultiUserSwitch.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .setStartDelay(0)
                            .setInterpolator(PhoneStatusBar.ALPHA_OUT)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    mMultiUserSwitch.setAlpha(1f);
                                    getOverlay().remove(mMultiUserSwitch);
                                }
                            })
                            .start();

                } else {
                    mMultiUserSwitch.setAlpha(0f);
                    mMultiUserSwitch.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .setStartDelay(200)
                            .setInterpolator(PhoneStatusBar.ALPHA_IN);
                }
                return true;
            }
        });

    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != View.VISIBLE) {
            mSystemIconsSuperContainer.animate().cancel();
            mMultiUserSwitch.animate().cancel();
            mMultiUserSwitch.setAlpha(1f);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                "status_bar_show_carrier"), false, mObserver);
    }

    public void updateNetworkIconColors() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if(mColorSwitch) {
        mSignalCluster.setIconTint(
                StatusBarColorHelper.getNetworkSignalColor(mContext),
                StatusBarColorHelper.getNoSimColor(mContext),
                StatusBarColorHelper.getAirplaneModeColor(mContext), 0f);
	 }
    }

    public void updateNetworkSignalColor() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if(mColorSwitch) {
        mSignalCluster.applyNetworkSignalTint(StatusBarColorHelper.getNetworkSignalColor(getContext()));
	}
    }

    public void updateNoSimColor() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if(mColorSwitch) {
        mSignalCluster.applyNoSimTint(StatusBarColorHelper.getNoSimColor(getContext()));
	}
    }

    public void updateAirplaneModeColor() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if(mColorSwitch) {
        mSignalCluster.applyAirplaneModeTint(StatusBarColorHelper.getAirplaneModeColor(getContext()));
	}
    }   
    
    public void updateBatteryviews() {
	mBatteryMeterView = (BatteryMeterView) findViewById(R.id.battery);
	int mBatteryIconColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.BATTERY_ICON_COLOR, 0xFFFFFFFF);
    	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if(mColorSwitch) {
	mBatteryMeterView.setDarkIntensity(mBatteryIconColor);
	}   
    }
}
