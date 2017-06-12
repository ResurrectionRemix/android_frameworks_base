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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.database.ContentObserver;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.BatteryMeterView;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcher;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.tuner.TunerService;

import cyanogenmod.providers.CMSettings;

import java.text.NumberFormat;

/**
 * The header group on Keyguard.
 */
public class KeyguardStatusBarView extends RelativeLayout
        implements BatteryController.BatteryStateChangeCallback, TunerService.Tunable {

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
            "cmsystem:" + CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT;
    private static final String TEXT_CHARGING_SYMBOL =
            Settings.Secure.TEXT_CHARGING_SYMBOL;

    private boolean mBatteryCharging;
    private boolean mKeyguardUserSwitcherShowing;
    private boolean mBatteryListening;

    private View mSystemIconsSuperContainer;
    private MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private TextView mBatteryLevel;

    private TextView mKeyguardClock;
    private int mShowKeyguardClock;

    private TextView mCarrierLabel;
    private int mShowCarrierLabel;

    public static final int FONT_NORMAL = 0;
    public static final int FONT_ITALIC = 1;
    public static final int FONT_BOLD = 2;
    public static final int FONT_BOLD_ITALIC = 3;
    public static final int FONT_LIGHT = 4;
    public static final int FONT_LIGHT_ITALIC = 5;
    public static final int FONT_THIN = 6;
    public static final int FONT_THIN_ITALIC = 7;
    public static final int FONT_CONDENSED = 8;
    public static final int FONT_CONDENSED_ITALIC = 9;
    public static final int FONT_CONDENSED_LIGHT = 10;
    public static final int FONT_CONDENSED_LIGHT_ITALIC = 11;
    public static final int FONT_CONDENSED_BOLD = 12;
    public static final int FONT_CONDENSED_BOLD_ITALIC = 13;
    public static final int FONT_MEDIUM = 14;
    public static final int FONT_MEDIUM_ITALIC = 15;
    public static final int FONT_BLACK = 16;
    public static final int FONT_BLACK_ITALIC = 17;
    public static final int FONT_DANCINGSCRIPT = 18;
    public static final int FONT_DANCINGSCRIPT_BOLD = 19;
    public static final int FONT_COMINGSOON = 20;
    public static final int FONT_NOTOSERIF = 21;
    public static final int FONT_NOTOSERIF_ITALIC = 22;
    public static final int FONT_NOTOSERIF_BOLD = 23;
    public static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;
    private int mCarrierLabelFontStyle = FONT_NORMAL;
    private int mCarrierFontSize = 12;

    private BatteryController mBatteryController;
    private KeyguardUserSwitcher mKeyguardUserSwitcher;
    private UserSwitcherController mUserSwitcherController;

    private int mSystemIconsSwitcherHiddenExpandedMargin;
    private int mSystemIconsBaseMargin;
    private View mSystemIconsContainer;

    private boolean mShowBatteryText;
    private boolean mForceBatteryText;
    private boolean mForceChargeBatteryText;
    private int mTextChargingSymbol;
    private int currentLevel;
    private boolean isPlugged;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            showKeyguardClock();
            showStatusBarCarrier();
            updateVisibilities();
        }
    };
    private boolean mHideContents;
    private boolean mTouchStarted;

    public KeyguardStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        showKeyguardClock();
        showStatusBarCarrier();
    }

    private void showStatusBarCarrier() {
        mShowCarrierLabel = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_SHOW_CARRIER, 0, UserHandle.USER_CURRENT);
        mCarrierLabelFontStyle = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);
    }

    private void showKeyguardClock() {
        mShowKeyguardClock = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.KEYGUARD_SHOW_CLOCK, 1, UserHandle.USER_CURRENT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSystemIconsSuperContainer = findViewById(R.id.system_icons_super_container);
        mSystemIconsContainer = findViewById(R.id.system_icons_container);
        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = (ImageView) findViewById(R.id.multi_user_avatar);
        mBatteryLevel = (TextView) findViewById(R.id.battery_level);
        mCarrierLabel = (TextView) findViewById(R.id.keyguard_carrier_text);
        mKeyguardClock = (TextView) findViewById(R.id.keyguard_clock);
        loadDimens();
        updateUserSwitcher();
        updateVisibilities();
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    mTouchStarted = true;
                } else if (action == MotionEvent.ACTION_UP) {
                    if (mTouchStarted) {
                        toggleContents(!mHideContents);
                    }
                    mTouchStarted = false;
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    mTouchStarted = false;
                }
                return true;
            }
        });
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        MarginLayoutParams lp = (MarginLayoutParams) mMultiUserAvatar.getLayoutParams();
        lp.width = lp.height = getResources().getDimensionPixelSize(
                R.dimen.multi_user_avatar_keyguard_size);
        mMultiUserAvatar.setLayoutParams(lp);

        lp = (MarginLayoutParams) mMultiUserSwitch.getLayoutParams();
        lp.width = getResources().getDimensionPixelSize(
                R.dimen.multi_user_switch_width_keyguard);
        lp.setMarginEnd(getResources().getDimensionPixelSize(
                R.dimen.multi_user_switch_keyguard_margin));
        mMultiUserSwitch.setLayoutParams(lp);

        lp = (MarginLayoutParams) mSystemIconsSuperContainer.getLayoutParams();
        lp.height = getResources().getDimensionPixelSize(
                R.dimen.status_bar_header_height);
        lp.setMarginStart(getResources().getDimensionPixelSize(
                R.dimen.system_icons_super_container_margin_start));
        mSystemIconsSuperContainer.setLayoutParams(lp);
        mSystemIconsSuperContainer.setPaddingRelative(mSystemIconsSuperContainer.getPaddingStart(),
                mSystemIconsSuperContainer.getPaddingTop(),
                getResources().getDimensionPixelSize(R.dimen.system_icons_keyguard_padding_end),
                mSystemIconsSuperContainer.getPaddingBottom());

        // Respect font size setting.
        mCarrierLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.text_size_small_material));

        lp = (MarginLayoutParams) mSystemIconsContainer.getLayoutParams();
        lp.height = getResources().getDimensionPixelSize(
                R.dimen.status_bar_height);
        mSystemIconsContainer.setLayoutParams(lp);

        lp = (MarginLayoutParams) mBatteryLevel.getLayoutParams();
        lp.setMarginStart(
                getResources().getDimensionPixelSize(R.dimen.header_battery_margin_keyguard));
        mBatteryLevel.setLayoutParams(lp);
        mBatteryLevel.setPaddingRelative(mBatteryLevel.getPaddingStart(),
                mBatteryLevel.getPaddingTop(),
                getResources().getDimensionPixelSize(R.dimen.battery_level_padding_end),
                mBatteryLevel.getPaddingBottom());
        mBatteryLevel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.battery_level_text_size));
        mKeyguardClock.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.text_size_small_material));

        updateSize();
        lp = (MarginLayoutParams) mCarrierLabel.getLayoutParams();
        lp.setMarginStart(
                getResources().getDimensionPixelSize(R.dimen.keyguard_carrier_text_margin));
        mCarrierLabel.setLayoutParams(lp);

        lp = (MarginLayoutParams) getLayoutParams();
        lp.height =  getResources().getDimensionPixelSize(
                R.dimen.status_bar_header_height_keyguard);
        setLayoutParams(lp);
    }

    private void updateSize() {
        mCarrierFontSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_SIZE, 10,
                UserHandle.USER_CURRENT);

        mCarrierLabel.setTextSize(mCarrierFontSize);
    }

    private void loadDimens() {
        Resources res = getResources();
        mSystemIconsSwitcherHiddenExpandedMargin = res.getDimensionPixelSize(
                R.dimen.system_icons_switcher_hidden_expanded_margin);
        mSystemIconsBaseMargin = res.getDimensionPixelSize(
                R.dimen.system_icons_super_container_avatarless_margin_end);
    }

    private void updateVisibilities() {
        if (mMultiUserSwitch.getParent() != this && !mKeyguardUserSwitcherShowing) {
            if (mMultiUserSwitch.getParent() != null) {
                getOverlay().remove(mMultiUserSwitch);
            }
            addView(mMultiUserSwitch, 0);
        } else if (mMultiUserSwitch.getParent() == this && mKeyguardUserSwitcherShowing) {
            removeView(mMultiUserSwitch);
        }
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.KEYGUARD_SHOW_CLOCK, 0) == 1) {
            mKeyguardClock.setVisibility(View.VISIBLE);
        } else {
            mKeyguardClock.setVisibility(View.GONE);
        }
        if (mKeyguardUserSwitcher == null) {
            // If we have no keyguard switcher, the screen width is under 600dp. In this case,
            // we don't show the multi-user avatar unless there is more than 1 user on the device.
            if (mUserSwitcherController != null
                    && mUserSwitcherController.getSwitchableUserCount() > 1) {
                mMultiUserSwitch.setVisibility(mHideContents ? View.INVISIBLE : View.VISIBLE);
            } else {
                mMultiUserSwitch.setVisibility(View.GONE);
            }
        }
        mBatteryLevel.setVisibility(
                (mBatteryCharging && mForceChargeBatteryText) || mShowBatteryText || mForceBatteryText ? View.VISIBLE : View.GONE);

        if (mCarrierLabel != null) {
            if (mShowCarrierLabel == 1) {
                mCarrierLabel.setVisibility(View.VISIBLE);
            } else if (mShowCarrierLabel == 3) {
                mCarrierLabel.setVisibility(View.VISIBLE);
            } else {
                mCarrierLabel.setVisibility(View.GONE);
            }
         }

       getFontStyle(mCarrierLabelFontStyle);
    }

    private void updateSystemIconsLayoutParams() {
        RelativeLayout.LayoutParams lp =
                (LayoutParams) mSystemIconsSuperContainer.getLayoutParams();
        // If the avatar icon is gone, we need to have some end margin to display the system icons
        // correctly.
        int baseMarginEnd = mMultiUserSwitch.getVisibility() == View.GONE
                ? mSystemIconsBaseMargin
                : 0;
        int marginEnd = mKeyguardUserSwitcherShowing ? mSystemIconsSwitcherHiddenExpandedMargin :
                baseMarginEnd;
        if (marginEnd != lp.getMarginEnd()) {
            lp.setMarginEnd(marginEnd);
            mSystemIconsSuperContainer.setLayoutParams(lp);
        }
    }

    public void setListening(boolean listening) {
        if (listening == mBatteryListening) {
            return;
        }
        mBatteryListening = listening;
        if (mBatteryListening) {
            TunerService.get(getContext()).addTunable(this, STATUS_BAR_SHOW_BATTERY_PERCENT, TEXT_CHARGING_SYMBOL);
            mBatteryController.addStateChangedCallback(this);
        } else {
            mBatteryController.removeStateChangedCallback(this);
            TunerService.get(getContext()).removeTunable(this);
        }
    }

    private void updateUserSwitcher() {
        boolean keyguardSwitcherAvailable = mKeyguardUserSwitcher != null;
        mMultiUserSwitch.setClickable(keyguardSwitcherAvailable);
        mMultiUserSwitch.setFocusable(keyguardSwitcherAvailable);
        mMultiUserSwitch.setKeyguardMode(keyguardSwitcherAvailable);
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        ((BatteryMeterView) findViewById(R.id.battery)).setBatteryController(batteryController);
    }

    public void setUserSwitcherController(UserSwitcherController controller) {
        mUserSwitcherController = controller;
        mMultiUserSwitch.setUserSwitcherController(controller);
    }

    public void setUserInfoController(UserInfoController userInfoController) {
        userInfoController.addListener(new UserInfoController.OnUserInfoChangedListener() {
            @Override
            public void onUserInfoChanged(String name, Drawable picture) {
                mMultiUserAvatar.setImageDrawable(picture);
            }
        });
    }

    public void setQSPanel(QSPanel qsp) {
        mMultiUserSwitch.setQsPanel(qsp);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        currentLevel = level;
        isPlugged = pluggedIn;
        updateChargingSymbol(currentLevel, isPlugged);
        boolean changed = mBatteryCharging != charging;
        mBatteryCharging = charging;
        if (changed) {
            updateVisibilities();
        }
    }

    private void updateChargingSymbol(int level, boolean pluggedIn) {
        mTextChargingSymbol = Settings.Secure.getInt(getContext().getContentResolver(),
                TEXT_CHARGING_SYMBOL, 0);
        if (pluggedIn) {
            switch (mTextChargingSymbol) {
                case 1:
                    mBatteryLevel.setText("⚡️" + NumberFormat.getPercentInstance().format((double) level / 100.0));
                    break;
                case 2:
                    mBatteryLevel.setText("~" + NumberFormat.getPercentInstance().format((double) level / 100.0));
                    break;
                default:
                    mBatteryLevel.setText(NumberFormat.getPercentInstance().format((double) level / 100.0));
                    break;
            }
        } else {
            mBatteryLevel.setText(NumberFormat.getPercentInstance().format((double) level / 100.0));
        }
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        // could not care less
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
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .start();
                if (userSwitcherHiding) {
                    getOverlay().add(mMultiUserSwitch);
                    mMultiUserSwitch.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .setStartDelay(0)
                            .setInterpolator(Interpolators.ALPHA_OUT)
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
                            .setInterpolator(Interpolators.ALPHA_IN);
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
            mSystemIconsSuperContainer.setTranslationX(0);
            mMultiUserSwitch.animate().cancel();
            mMultiUserSwitch.setAlpha(1f);
        } else {
            updateVisibilities();
            updateSystemIconsLayoutParams();
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
                "keyguard_show_clock"), false, mObserver);
        getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                "status_bar_show_carrier"), false, mObserver);
        getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                "status_bar_carrier_font_style"), false, mObserver);
    }

    public void getFontStyle(int font) {
         switch (font) {
             case FONT_NORMAL:
             default:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif",
                     Typeface.NORMAL));
                 break;
             case FONT_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif",
                     Typeface.ITALIC));
                 break;
             case FONT_BOLD:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif",
                     Typeface.BOLD));
                 break;
             case FONT_BOLD_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif",
                     Typeface.BOLD_ITALIC));
                 break;
             case FONT_LIGHT:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-light",
                     Typeface.NORMAL));
                 break;
             case FONT_LIGHT_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-light",
                     Typeface.ITALIC));
                 break;
             case FONT_THIN:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-thin",
                     Typeface.NORMAL));
                 break;
             case FONT_THIN_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-thin",
                     Typeface.ITALIC));
                 break;
             case FONT_CONDENSED:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-condensed",
                     Typeface.NORMAL));
                 break;
             case FONT_CONDENSED_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-condensed",
                     Typeface.ITALIC));
                 break;
             case FONT_CONDENSED_LIGHT:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-condensed-light",
                     Typeface.NORMAL));
                 break;
             case FONT_CONDENSED_LIGHT_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-condensed-light",
                     Typeface.ITALIC));
                 break;
             case FONT_CONDENSED_BOLD:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-condensed",
                     Typeface.BOLD));
                 break;
             case FONT_CONDENSED_BOLD_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-condensed",
                     Typeface.BOLD_ITALIC));
                 break;
             case FONT_MEDIUM:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-medium",
                     Typeface.NORMAL));
                 break;
             case FONT_MEDIUM_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-medium",
                     Typeface.ITALIC));
                 break;
             case FONT_BLACK:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-black",
                     Typeface.NORMAL));
                 break;
             case FONT_BLACK_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("sans-serif-black",
                     Typeface.ITALIC));
                 break;
             case FONT_DANCINGSCRIPT:
                 mCarrierLabel.setTypeface(Typeface.create("cursive",
                     Typeface.NORMAL));
                 break;
             case FONT_DANCINGSCRIPT_BOLD:
                 mCarrierLabel.setTypeface(Typeface.create("cursive",
                     Typeface.BOLD));
                 break;
             case FONT_COMINGSOON:
                 mCarrierLabel.setTypeface(Typeface.create("casual",
                     Typeface.NORMAL));
                 break;
             case FONT_NOTOSERIF:
                 mCarrierLabel.setTypeface(Typeface.create("serif",
                     Typeface.NORMAL));
                 break;
             case FONT_NOTOSERIF_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("serif",
                     Typeface.ITALIC));
                 break;
             case FONT_NOTOSERIF_BOLD:
                 mCarrierLabel.setTypeface(Typeface.create("serif",
                     Typeface.BOLD));
                 break;
             case FONT_NOTOSERIF_BOLD_ITALIC:
                 mCarrierLabel.setTypeface(Typeface.create("serif",
                     Typeface.BOLD_ITALIC));
                 break;
         }
     }

    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_SHOW_BATTERY_PERCENT:
                mShowBatteryText = newValue == null ? false : Integer.parseInt(newValue) == 2;
                mForceBatteryText = CMSettings.System.getInt(getContext().getContentResolver(),
                        CMSettings.System.STATUS_BAR_BATTERY_STYLE, 0) == 5 ? true : false;
                mForceChargeBatteryText = Settings.Secure.getInt(getContext().getContentResolver(),
                        Settings.Secure.FORCE_CHARGE_BATTERY_TEXT, 1) == 1 ? true : false;
                updateVisibilities();
            case TEXT_CHARGING_SYMBOL:
                updateChargingSymbol(currentLevel, isPlugged);
                break;
            default:
                break;
        }
    }

    public void toggleContents(boolean hideContents) {
        boolean shouldHideContents = Settings.Secure.getIntForUser(
                getContext().getContentResolver(), Settings.Secure.LOCK_HIDE_STATUS_BAR, 0,
                UserHandle.USER_CURRENT) == 1;
        if (!shouldHideContents) {
            hideContents = false;
        }
        if (mHideContents == hideContents) {
            return;
        }

        mHideContents = hideContents;
        if (mHideContents) {
            Animator fadeAnimator1 = null;
            if (mMultiUserSwitch.getVisibility() != View.GONE) {
                fadeAnimator1 = ObjectAnimator.ofFloat(mMultiUserSwitch, "alpha", 1f, 0f);
                fadeAnimator1.setDuration(500);
                fadeAnimator1.setInterpolator(Interpolators.ALPHA_OUT);
                fadeAnimator1.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mMultiUserSwitch.setVisibility(View.INVISIBLE);
                    }
                });
            }
            Animator fadeAnimator2 = ObjectAnimator.ofFloat(mSystemIconsSuperContainer, "alpha", 1f, 0f);
            fadeAnimator2.setDuration(500);
            fadeAnimator2.setInterpolator(Interpolators.ALPHA_OUT);
            fadeAnimator2.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSystemIconsSuperContainer.setVisibility(View.INVISIBLE);
                }
            });
            Animator fadeAnimator3 = ObjectAnimator.ofFloat(mCarrierLabel, "alpha", 1f, 0f);
            fadeAnimator3.setDuration(500);
            fadeAnimator3.setInterpolator(Interpolators.ALPHA_OUT);
            fadeAnimator3.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCarrierLabel.setVisibility(View.INVISIBLE);
                }
            });
            AnimatorSet set = new AnimatorSet();
            set.playTogether(fadeAnimator2, fadeAnimator3);
            if (fadeAnimator1 != null) {
                set.playTogether(fadeAnimator1);
            }
            set.start();
        } else {
            Animator fadeAnimator1 = null;
            if (mMultiUserSwitch.getVisibility() != View.GONE) {
                mMultiUserSwitch.setAlpha(0f);
                mMultiUserSwitch.setVisibility(View.VISIBLE);
                fadeAnimator1 = ObjectAnimator.ofFloat(mMultiUserSwitch, "alpha", 0f, 1f);
                fadeAnimator1.setDuration(500);
                fadeAnimator1.setInterpolator(Interpolators.ALPHA_IN);
            }

            mSystemIconsSuperContainer.setAlpha(0f);
            mSystemIconsSuperContainer.setVisibility(View.VISIBLE);
            Animator fadeAnimator2 = ObjectAnimator.ofFloat(mSystemIconsSuperContainer, "alpha", 0f, 1f);
            fadeAnimator2.setDuration(500);
            fadeAnimator2.setInterpolator(Interpolators.ALPHA_IN);

            mCarrierLabel.setAlpha(0f);
            mCarrierLabel.setVisibility(View.VISIBLE);
            Animator fadeAnimator3 = ObjectAnimator.ofFloat(mCarrierLabel, "alpha", 0f, 1f);
            fadeAnimator3.setDuration(500);
            fadeAnimator3.setInterpolator(Interpolators.ALPHA_IN);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(fadeAnimator2, fadeAnimator3);
            if (fadeAnimator1 != null) {
                set.playTogether(fadeAnimator1);
            }
            set.start();
        }
    }
}
