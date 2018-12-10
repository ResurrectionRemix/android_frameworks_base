/*
 * Copyright 2014-2017 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pie;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.Shader;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;
import android.view.animation.DecelerateInterpolator;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;

import com.android.internal.app.AssistUtils;
import com.android.internal.statusbar.StatusBarIcon;

import com.android.systemui.R;
import com.android.systemui.statusbar.AnimatedImageView;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pie menu
 * Handles creating, drawing, animations and touch eventing for pie.
 */
public class PieMenu extends RelativeLayout {
    private static final String TAG = PieMenu.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String FONT_FAMILY_LIGHT = "sans-serif-light";
    private static final String FONT_FAMILY_MEDIUM = "sans-serif-medium";

    private static final String SETTINGS_FRAGMENT_INTENT = ":settings:show_fragment_as_subsetting";

    private final Handler mHandler = new Handler();
    private final Point mCenter = new Point(0, 0);
    private final ConnectivityManager mConnectivityManager;
    private final TelephonyManager mTelephonyManager;
    private final PieSignalCallback mSignalCallback = new PieSignalCallback();
    private final WindowManager mWindowManager;
    private final WifiManager mWifiManager;

    // paints
    private final Paint mToggleBackground;
    private final Paint mToggleOuterBackground;
    private final Paint mClockPaint;
    private final Paint mStatusPaint;
    private final Paint mLinePaint;
    private final Paint mCirclePaint;
    private final Paint mBackgroundCirclePaint;
    private final Paint mBatteryPaint;
    private final Paint mShaderPaint;
    private final Paint mToggleShaderPaint;

    private final AssistUtils mAssistUtils;
    private final StatusBar mBar;
    private final Context mContext;
    private final List<PieItem> mItems = new ArrayList<>();
    private final PieController mPanel;
    private final Resources mResources;
    private final TogglePoint[] mTogglePoint = new TogglePoint[5];
    private final Vibrator mVibrator;

    // Colors
    private int mForegroundColor;
    private int mBackgroundColor;
    private int mIconColor;
    private int mLineColor;
    private int mSelectedColor;
    private int mBackgroundCircleColor;
    private int mBatteryColor;
    private int mBatteryMedColor;
    private int mBatteryLowColor;
    private int mShaderStartColor;
    private int mShaderEndColor;

    // Dark colors;
    private int mDarkBackgroundColor;
    private int mDarkSnappointColor;

    // Dimensions
    private int mPanelOrientation;
    private int mOuterCircleRadius;
    private int mOuterCircleThickness;
    private int mInnerCircleRadius;
    private int mBatteryCircleRadius;
    private float mShadeThreshold;
    private float mCenterDistance = 0;

    // Info texts
    private String mClockText;
    private String mBatteryText;
    private String mDateText;
    private String mSsidText;
    private String mNetworkText;

    // Animators
    private ValueAnimator mPieBackgroundAnimator;
    private ValueAnimator mPieFadeAnimator;
    private ValueAnimator mPieGrowAnimator;
    private ValueAnimator mPieMoveAnimator;
    private ValueAnimator mToggleAnimator;
    private ValueAnimator mToggleGrowAnimator;
    private ValueAnimator mToggleOuterGrowAnimator;
    private ValueAnimator mRippleAnimator;
    private ValueAnimator mSettingsToggleAnimator;

    // Animator fractions
    private float mBackgroundFraction;
    private float mSettingsToggleFraction;

    private int mOverallSpeed;

    // Offsets
    private float mClockOffsetX;
    private float mClockOffsetY;
    private float mDateOffsetX;
    private float mDateOffsetY;
    private float mBatteryOffsetX;
    private float mBatteryOffsetY;
    private float mBatteryOffsetYSide;
    private float mLandOffsetX;
    private float mLandOffsetY;

    private float mSweep;

    private int mLineLength;
    private int mLineOffset;
    private int mLineOffsetLand;
    private int mLineOffsetSide;
    private int mWidth;
    private int mHeight;

    // Settings
    private final ImageView mSettingsLogo;
    private final Intent mSettingsIntent;
    private final Intent mCloseDialogsIntent;
    private boolean mSettingsAttached;
    private int mSettingsOffset;
    private int mSettingsOffsetLand;

    // Now on tap
    private final ImageView mNOTLogo;
    private boolean mNotAttached = false;
    private int mNOTSize;
    private int mNOTRadius;
    private int mNOTOffsetY;
    private int mNOTOffsetX;

    // Notifications
    private List<AnimatedImageView> mIconViews = new ArrayList<>();
    private List<Pair<StatusBarNotification,Icon>> mNotifications;
    private int mIconSize;
    private int mIconPadding;
    private int mMaxIcons;
    private float mIconOffsetY;
    private float mIconOffsetYside;

    // Pie theme
    private boolean mDarkThemeEnabled;

    private boolean mHasShown;
    private boolean mReversing;
    private boolean mSnappointsShowing;

    private int mNumberOfSnapPoints;

    private int mSnapRadius;
    private int mSnapOffset;

    private int mBatteryLevel;

    private boolean mSnappointLaunched;
    private boolean mOpen;
    private boolean mPieBottom;
    private boolean mRegistered;

    private int mBatteryMode;
    private int mStatusIndicator;

    // network
    private ImageView mNetworkIcon;
    private ImageView mWifiIcon;
    private NetworkController mNetworkController;
    private boolean mNetworkIconsAttached;
    private boolean mSignalRegistered;
    private int mSubId;
    private int mStatusTextTopMargin;
    private int mStatusTextTopMarginLand;
    private int mStatusOffset;
    private int mStatusIconSize;
    private int mWifiIconResId;
    private int mNetworkIconResId;

    /**
     * Creates a new pie outline view
     *
     * @param context the current context
     * @param panel instance of piecontroller
     * @param bar instance of basestatusbar
     */
    public PieMenu(Context context, PieController panel, StatusBar bar) {
        super(context);

        mContext = context;
        mResources = mContext.getResources();
        mBar = bar;
        mPanel = panel;

        mNetworkController = mBar.getNetworkController();

        setWillNotDraw(false);
        setDrawingCacheEnabled(false);
        setElevation(mResources.getDimensionPixelSize(R.dimen.pie_elevation));

        // create system services
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        // create assist manager
        mAssistUtils = new AssistUtils(mContext);

        // register broadcast receivers
        registerReceivers();

        // settings intent
        mSettingsIntent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        mSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mSettingsIntent.putExtra(SETTINGS_FRAGMENT_INTENT, true);
        mCloseDialogsIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        // snappoint background
        mToggleBackground = new Paint();
        mToggleBackground.setAntiAlias(true);

        // outer snap point animation paint
        mToggleOuterBackground = new Paint();
        mToggleOuterBackground.setAntiAlias(true);

        // clock
        mClockPaint = new Paint();
        mClockPaint.setAntiAlias(true);
        mClockPaint.setTypeface(Typeface.create(FONT_FAMILY_LIGHT, Typeface.NORMAL));

        // status (date and battery)
        mStatusPaint = new Paint();
        mStatusPaint.setAntiAlias(true);
        mStatusPaint.setTypeface(Typeface.create(FONT_FAMILY_MEDIUM, Typeface.NORMAL));

        // line
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);

        // pie circles
        mCirclePaint = new Paint();
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);

        // background circle
        mBackgroundCirclePaint = new Paint();
        mBackgroundCirclePaint.setAntiAlias(true);

        // battery circle
        mBatteryPaint = new Paint();
        mBatteryPaint.setAntiAlias(true);
        mBatteryPaint.setStyle(Paint.Style.STROKE);

        // shader
        mShaderPaint = new Paint();
        mShaderPaint.setAntiAlias(true);
        mShaderPaint.setStyle(Paint.Style.STROKE);

        // toggle shader
        mToggleShaderPaint = new Paint();
        mToggleShaderPaint.setAntiAlias(true);
        mToggleShaderPaint.setStyle(Paint.Style.STROKE);

        // Settings
        mSettingsLogo = new ImageView(mContext);

        // now on tap
        mNOTLogo = new ImageView(mContext);

        // Pie animation speed
        mOverallSpeed = mResources.getInteger(R.integer.pie_animation_speed);

        // network icons
        mWifiIcon = new ImageView(mContext);
        mNetworkIcon = new ImageView(mContext);
        mWifiIcon.setAlpha(0); // default alpha to 0, animator will animate to 255
        mNetworkIcon.setAlpha(0);

        // Get all dimensions
        getDimensions();
    }

    /**
     * Initializes current dimensions
     */
    private void getDimensions() {
        // get theme status
        int themeMode = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.PIE_THEME_MODE, 0);
        if (themeMode == 0) {
            int primaryTheme = mContext.getResources().getColor(R.color.system_primary_color);
            mDarkThemeEnabled = primaryTheme == 1 || primaryTheme == 3;
        } else {
            mDarkThemeEnabled = themeMode == 2;
        }

        // Battery circle status
        mBatteryMode = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.PIE_BATTERY_MODE, 0);

        // status indicator
        mStatusIndicator = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.PIE_STATUS_INDICATOR, 0);

        // Settings
        mSettingsOffset = mResources.getDimensionPixelSize(R.dimen.pie_settings_offset);
        mSettingsOffsetLand = mResources.getDimensionPixelSize(R.dimen.pie_settings_offset_land);

        // now on tap
        mNOTSize = mResources.getDimensionPixelSize(R.dimen.pie_not_size);
        mNOTRadius = mResources.getDimensionPixelSize(R.dimen.pie_not_radius);
        mNOTOffsetY = mResources.getDimensionPixelSize(R.dimen.pie_not_offset);
        mNOTOffsetX = mResources.getDimensionPixelSize(R.dimen.pie_not_offsetx);

        // snap
        mSnappointLaunched = false;
        mReversing = false;
        mSnapRadius = mResources.getDimensionPixelSize(R.dimen.pie_snap_radius);
        mSnapOffset = mResources.getDimensionPixelSize(R.dimen.pie_snap_offset);

        // fetch colors
        mBackgroundColor = mResources.getColor(R.color.pie_foreground);
        mForegroundColor = mResources.getColor(R.color.pie_background);
        mIconColor = mResources.getColor(R.color.pie_icon);
        mLineColor = mResources.getColor(R.color.pie_line);
        mSelectedColor = mResources.getColor(R.color.pie_selected);
        mBackgroundCircleColor = mResources.getColor(R.color.pie_background_circle);
        mBatteryColor = mResources.getColor(R.color.pie_battery);
        mBatteryMedColor = mResources.getColor(R.color.pie_battery_med);
        mBatteryLowColor = mResources.getColor(R.color.pie_battery_low);
        mShaderStartColor = mResources.getColor(R.color.pie_shader_start);
        mShaderEndColor = mResources.getColor(R.color.pie_shader_end);
        mDarkBackgroundColor = mResources.getColor(R.color.pie_dark_background);
        mDarkSnappointColor = mResources.getColor(R.color.pie_dark_snap_point);

        mBackgroundFraction = 0.0f;
        mSettingsToggleFraction = 0.0f;

        // fetch orientation
        mPanelOrientation = mPanel.getOrientation();
        mPieBottom = mPanelOrientation == Gravity.BOTTOM || isLandScape();

        Point outSize = new Point(0, 0);
        mWindowManager.getDefaultDisplay().getRealSize(outSize);
        mWidth = outSize.x;
        mHeight = outSize.y;

        // Create snap points
        mNumberOfSnapPoints = 0;
        if (isSnapPossible(Gravity.LEFT) && !isLandScape()) {
            mTogglePoint[mNumberOfSnapPoints++] = new SnapPoint(
                    0 - mSnapOffset, mHeight / 2, mSnapRadius, Gravity.LEFT);
        }

        if (isSnapPossible(Gravity.RIGHT) && !isLandScape()) {
            mTogglePoint[mNumberOfSnapPoints++] = new SnapPoint(
                    mWidth + mSnapOffset, mHeight / 2, mSnapRadius, Gravity.RIGHT);
        }

        if ((!isLandScape() || isTablet()) && isSnapPossible(Gravity.BOTTOM)) {
            mTogglePoint[mNumberOfSnapPoints++] = new SnapPoint(
                    mWidth / 2, mHeight + mSnapOffset, mSnapRadius, Gravity.BOTTOM);
        }

        final boolean pieRight = mPanelOrientation == Gravity.RIGHT;
        if (!mSettingsAttached) {
            mSettingsLogo.setImageResource(R.drawable.ic_settings);
            addView(mSettingsLogo);
            mSettingsAttached = true;
        }
        setColor(mSettingsLogo, mDarkThemeEnabled ? mForegroundColor : mBackgroundColor);
        mTogglePoint[mNumberOfSnapPoints++] = new SettingsPoint(mWidth / 2
                + (mPieBottom ? 0 : (pieRight ? -mSettingsOffset : mSettingsOffset))
                + (mPanelOrientation == Gravity.BOTTOM ? 0 :
                (isLandScape() ? -mSettingsOffsetLand : (pieRight ? -mNOTOffsetX : mNOTOffsetX))),
                mHeight / (mPanelOrientation == Gravity.BOTTOM ? 6 : 2), mNOTRadius, mSettingsLogo,
                mNOTSize);

        if (isAssistantAvailable()) {
            if (!mNotAttached) {
                mNOTLogo.setImageResource(R.drawable.ic_google_logo);
                addView(mNOTLogo);
                mNotAttached = true;
            }
            setColor(mNOTLogo, mDarkThemeEnabled ? mForegroundColor : mBackgroundColor);
            mTogglePoint[mNumberOfSnapPoints++] = new NowOnTapPoint(mWidth / 2 +
                    (mPanelOrientation == Gravity.BOTTOM ? 0 : (isLandScape() ? mWidth / 7 :
                    (pieRight ? -mNOTOffsetX : mNOTOffsetX))), mHeight / 2 +
                    (mPanelOrientation == Gravity.BOTTOM ? mNOTOffsetY : 0),
                    mNOTRadius, mNOTLogo, mNOTSize);
        }

        // shader
        mShaderPaint.setStrokeWidth(mResources.getDimensionPixelSize(R.dimen.pie_shader_width));
        mToggleShaderPaint.setStrokeWidth(mResources.getDimensionPixelSize(R.dimen.pie_shader_width));

        // create pie
        mOuterCircleRadius = mResources
                .getDimensionPixelSize(R.dimen.pie_outer_circle_radius);
        mOuterCircleThickness = mResources
                .getDimensionPixelSize(R.dimen.pie_outer_circle_thickness);
        mInnerCircleRadius = mResources
                .getDimensionPixelSize(R.dimen.pie_inner_circle_radius);
        mShadeThreshold = mOuterCircleRadius + mOuterCircleThickness;

        // clock
        mClockPaint.setTextSize(mResources
                .getDimensionPixelSize(R.dimen.pie_clock_size));
        mClockPaint.setLetterSpacing(mResources.getFloat(R.integer.pie_clock_letter_spacing));
        measureClock(getSimpleTime());
        mClockOffsetY = mResources.getDimensionPixelSize(R.dimen.pie_clock_offset);

        // status (date and battery)
        mStatusPaint.setTextSize(mResources
                .getDimensionPixelSize(R.dimen.pie_status_size));
        mStatusPaint.setLetterSpacing(mResources.getFloat(R.integer.pie_status_letter_spacing));

        // landscape ofset
        mLandOffsetX = mResources.getDimensionPixelSize(R.dimen.pie_land_offset);
        mLandOffsetY = mResources.getDimensionPixelSize(R.dimen.pie_land_offset_y);

        // date
        mDateText = getSimpleDate();
        mDateOffsetX = mStatusPaint.measureText(mDateText) / 2;
        mDateOffsetY = mResources.getDimensionPixelSize(R.dimen.pie_date_offset);

        // battery
        mBatteryText = mResources.getString(R.string.pie_battery_level)
                + mBatteryLevel + "%";
        mBatteryOffsetX = mStatusPaint.measureText(mBatteryText) / 2;
        mBatteryOffsetY = mResources.getDimensionPixelSize(R.dimen.pie_battery_offset);
        mBatteryOffsetYSide = mResources.getDimensionPixelSize(R.dimen.pie_battery_offset_side);
        mBatteryCircleRadius = mResources.getDimensionPixelSize(R.dimen.pie_battery_circle_radius);
        mBatteryPaint.setStrokeWidth(mResources.getDimensionPixelSize(R.dimen.pie_battery_width));

        // line
        mLinePaint.setStrokeWidth(mResources.getDimensionPixelSize(R.dimen.pie_line_width));
        mLineLength = mResources.getDimensionPixelSize(R.dimen.pie_line_length);
        mLineOffset = mResources.getDimensionPixelSize(R.dimen.pie_line_offset);
        mLineOffsetLand = mResources.getDimensionPixelSize(R.dimen.pie_line_offset_land);
        mLineOffsetSide = mResources.getDimensionPixelSize(R.dimen.pie_line_offset_side);

        mSweep = 0;

        // notifications
        mIconSize = mResources.getDimensionPixelSize(R.dimen.pie_icon_size);
        mIconPadding = mResources.getDimensionPixelSize(R.dimen.pie_icon_padding);
        mMaxIcons = mContext.getResources().getInteger(R.integer.pie_maximum_notifications);
        mIconOffsetY = mResources.getDimensionPixelSize(R.dimen.pie_icon_offset);
        mIconOffsetYside = mResources.getDimensionPixelSize(R.dimen.pie_icon_offset_side);

        updateNotifications();

        // status icons
        mStatusOffset = mResources.getDimensionPixelSize(R.dimen.pie_status_offset);
        mStatusTextTopMargin = mResources.getDimensionPixelSize(R.dimen.pie_status_text_top_margin);
        mStatusTextTopMarginLand = mResources.getDimensionPixelSize(
                R.dimen.pie_status_text_top_margin_land);
        mStatusIconSize = mResources.getDimensionPixelSize(R.dimen.pie_status_item_size);
        mNetworkText = getNetworkText();
        mSsidText = getWifiSsid();
        createSignalIcons();

        // Set colors
        mToggleBackground.setColor(mDarkThemeEnabled ? mDarkSnappointColor : mForegroundColor);
        mToggleOuterBackground.setColor(mForegroundColor);
        mStatusPaint.setColor(mForegroundColor);
        mClockPaint.setColor(mForegroundColor);
        mLinePaint.setColor(mLineColor);
        mCirclePaint.setColor(mDarkThemeEnabled ? mDarkBackgroundColor : mForegroundColor);
        mBackgroundCirclePaint.setColor(mDarkThemeEnabled ? mBackgroundCircleColor
                : mForegroundColor);
        mBatteryPaint.setColor(mBatteryLevel >= 50 ? mBatteryColor : (mBatteryLevel <= 15 ?
                mBatteryLowColor : mBatteryMedColor));

        // background animator
        mPieBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f);
        mPieBackgroundAnimator.setDuration(500);
        mPieBackgroundAnimator.addUpdateListener(new AnimatorUpdateListener(mPieBackgroundAnimator));

        // snappoint fade-in animator
        mToggleAnimator = ValueAnimator.ofInt(0, 1);
        mToggleAnimator.setDuration((int) (mOverallSpeed + 150));
        mToggleAnimator.addUpdateListener(new AnimatorUpdateListener(mToggleAnimator));

        // snappoint grow animator
        mToggleGrowAnimator = ValueAnimator.ofInt(0, 1);
        mToggleGrowAnimator.setDuration((int) (mOverallSpeed + 185));
        mToggleGrowAnimator.setInterpolator(new DecelerateInterpolator());
        mToggleGrowAnimator.setRepeatCount(1);
        mToggleGrowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mToggleGrowAnimator.addUpdateListener(new AnimatorUpdateListener(mToggleGrowAnimator));

        // outer snappoint grow animator
        mToggleOuterGrowAnimator = ValueAnimator.ofInt(0, 1);
        mToggleOuterGrowAnimator.setDuration((int) (mOverallSpeed - 10));
        mToggleOuterGrowAnimator.setRepeatCount(1);
        mToggleOuterGrowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mToggleOuterGrowAnimator.addUpdateListener(new AnimatorUpdateListener(mToggleOuterGrowAnimator));
        mToggleOuterGrowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                // Remove all listeners to prevent onAnimationEnd from being called.
                // This is a limitation from the API, so this is all we can do.
                animation.removeAllListeners();
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                switchSnapPoints();
                // Don't keep any listeners alive
                animation.removeAllListeners();
            }
        });

        // circle move animator
        mPieMoveAnimator = ValueAnimator.ofInt(0, 1);
        mPieMoveAnimator.setDuration((int) (mOverallSpeed * 1.5));
        mPieMoveAnimator.setInterpolator(new DecelerateInterpolator());
        mPieMoveAnimator.addUpdateListener(new AnimatorUpdateListener(mPieMoveAnimator));

        // outer circle animator
        mPieGrowAnimator = ValueAnimator.ofInt(0, 1);
        mPieGrowAnimator.setDuration((int) (mOverallSpeed * 1.5));
        mPieGrowAnimator.setInterpolator(new DecelerateInterpolator());
        mPieGrowAnimator.addUpdateListener(new AnimatorUpdateListener(mPieGrowAnimator));

        // Buttons fade-in animator
        mPieFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        mPieFadeAnimator.setDuration(500);
        mPieFadeAnimator.addUpdateListener(new AnimatorUpdateListener(mPieFadeAnimator));

        // Ripple alpha animator
        mRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        mRippleAnimator.setDuration(450);
        mRippleAnimator.setInterpolator(new DecelerateInterpolator());
        mRippleAnimator.addUpdateListener(new AnimatorUpdateListener(mRippleAnimator));

        // settings snappoint
        mSettingsToggleAnimator = ValueAnimator.ofFloat(0f, 1f);
        mSettingsToggleAnimator.setDuration(500);
        mSettingsToggleAnimator.addUpdateListener(new AnimatorUpdateListener(mSettingsToggleAnimator));
    }

    /**
     * Create and layout the wifi and mobile signal icons
     */
    private void createSignalIcons() {
        mNetworkText = getNetworkText();
        mSsidText = getWifiSsid();

        if (mNetworkText != null) {
            mNetworkIcon.setImageResource(mNetworkIconResId);
        }
        if (mSsidText != null) {
            mWifiIcon.setImageResource(mWifiIconResId);
        }

        // network icon layoutparams
        RelativeLayout.LayoutParams lp = new
                RelativeLayout.LayoutParams(mStatusIconSize, mStatusIconSize);
        lp.leftMargin = isLandScape() ? mStatusOffset : 0;
        lp.rightMargin = isLandScape() ? 0 : mStatusOffset;
        lp.topMargin = isLandScape() ? mHeight - mStatusOffset * 2 : mStatusOffset;
        if (!isLandScape()) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        mNetworkIcon.setLayoutParams(lp);

        // wifi icon layoutparams
        RelativeLayout.LayoutParams params = new
                RelativeLayout.LayoutParams(mStatusIconSize, mStatusIconSize);
        params.leftMargin = mStatusOffset;
        params.topMargin = mStatusOffset;
        mWifiIcon.setLayoutParams(params);

        if (!mNetworkIconsAttached) {
            addView(mNetworkIcon);
            addView(mWifiIcon);
            mNetworkIconsAttached = true;
        }

        mWifiIcon.setVisibility(mStatusIndicator == 2 || mStatusIndicator == 3
                ? View.GONE : View.VISIBLE);
        mNetworkIcon.setVisibility(mStatusIndicator == 1 || mStatusIndicator == 3
                || mNetworkText == null ? View.GONE : View.VISIBLE);
        setColor(mNetworkIcon, mForegroundColor);
        setColor(mWifiIcon, mForegroundColor);
    }

    /**
     * Checks whether snappoint should be created.
     */
    private boolean isSnapPossible(int gravity) {
        return mPanelOrientation != gravity && mPanel.isGravityPossible(gravity);
    }

    /**
     * Adds a new pie item to the item list
     */
    protected void addItem(PieItem item) {
        mItems.add(item);
    }

    /**
     * Switches the snap points
     */
    private void switchSnapPoints() {
        if (mSnappointLaunched) return;
        mSnappointLaunched = true;
        for (int i = 0; i < mNumberOfSnapPoints; i++) {
            TogglePoint toggle = mTogglePoint[i];
            if (toggle != null && toggle.active && toggle.isCurrentlyPossible(true)) {
                mVibrator.vibrate(2);
                animateOut(toggle);
            }
        }
    }

    /**
     * Register time and battery receivers
     */
    private void registerReceivers() {
        if (mRegistered) {
            if (DEBUG) {
                Log.d(TAG, "Cannot registering receivers, already registered.");
            }
            return;
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mReceiver, filter);
        mRegistered = true;
    }

    /**
     * Unregister time and battery receivers
     */
    private void unregisterReceivers() {
        if (!mRegistered) {
            if (DEBUG) {
                Log.d(TAG, "Cannot unregistering receivers, they've never been registered.");
            }
            return;
        }
        mContext.unregisterReceiver(mReceiver);
        mRegistered = false;
    }

    /**
     * Starts assist activity
     */
    private void startAssist() {
        if (isAssistantAvailable()) {
            mBar.startAssist(new Bundle());
        }
    }

    /**
     * Measures clock text
     */
    private void measureClock(String text) {
        mClockText = text;
        mClockOffsetX = mClockPaint.measureText(mClockText) / 2;
    }

    /**
     * Checks whether the PIE is currently showing
     */
    protected boolean isShowing() {
        return mOpen;
    }

    /**
     * Checks whether the current configuration is specified as for a tablet.
     */
    private boolean isTablet() {
        return mResources.getBoolean(R.bool.config_isTablet);
    }

    /**
     * Checks whether the current rotation is landscape or not.
     */
    private boolean isLandScape() {
        return mPanel.isLandScape();
    }

    /**
     * create notification icons
     */
    protected void updateNotifications() {
        mNotifications = mBar.getNotifications();
        if (mNotifications.size() > mMaxIcons) {
            mNotifications.subList(mMaxIcons, mNotifications.size()).clear();
        }
        float iconOffsetX = ((mNotifications.size() * mIconSize) + ((mNotifications.size() - 1)
                * mIconPadding)) / 2;
        if (isAllowedToDraw()) {
            if (mIconViews != null) {
                for (View view : mIconViews) {
                    removeView(view);
                }
                mIconViews.clear();
            }
            for (Pair<StatusBarNotification, Icon> icon : mNotifications) {
                AnimatedImageView view = new AnimatedImageView(mContext);
                try {
                    view.setImageDrawable(icon.second.loadDrawable(
                            mContext.createPackageContext(icon.first.getPackageName(),
                                    Context.CONTEXT_IGNORE_SECURITY)));
                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(TAG, "Could not load notification drawable", e);
                }
                setColor(view, mIconColor);
                view.setMinimumWidth(mIconSize);
                view.setMinimumHeight(mIconSize);
                view.setScaleType(ScaleType.FIT_XY);
                RelativeLayout.LayoutParams lp = new
                        RelativeLayout.LayoutParams(mIconSize, mIconSize);
                if (mPieBottom) {
                    lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                }
                lp.topMargin = mHeight / (mPieBottom ? 2 : 4) + (int)
                        (mPieBottom ? mIconOffsetY : mIconOffsetYside) + (int)
                        (isLandScape() ? mLandOffsetY : 0);
                lp.leftMargin = mWidth / 2 - (int) iconOffsetX -
                        (isLandScape() ? (int) mLandOffsetX : 0);
                view.setLayoutParams(lp);
                addView(view);
                mIconViews.add(view);
                iconOffsetX -= (float) mIconSize + mIconPadding;
                invalidate();
            }
        }
    }

    /**
     * Shows the PIE
     */
    protected void show(boolean show) {
        mOpen = show;

        setVisibility(show ? View.VISIBLE : View.GONE);

        if (mOpen) {
            centerPie();
            registerReceivers();
            getDimensions();
            layoutPie();
            if (!mSignalRegistered) {
                mNetworkController.addCallback(mSignalCallback);
                mSignalRegistered = true;
            }
            if (isAllowedToDraw()) {
                mHandler.postDelayed(mAnimateRunnable, 2000);
            }
        } else {
            unregisterReceivers();
            if (mSignalRegistered) {
                mNetworkController.removeCallback(mSignalCallback);
                mSignalRegistered = false;
            }
        }

        invalidate();
    }

    /**
     * Animate Pie shade after a fixed amount of time.
     */
    private final Runnable mAnimateRunnable = new Runnable() {
        @Override
        public void run() {
            animateInRest(false);
        }
    };

    /**
     * Sets PIE center dimensions
     */
    private void centerPie() {
        switch (mPanel.getOrientation()) {
            case Gravity.LEFT:
                mPanel.setCenter(0, mHeight / 2);
                break;
            case Gravity.RIGHT:
                mPanel.setCenter(mWidth, mHeight / 2);
                break;
            default:
                mPanel.setCenter(mWidth / 2, mHeight);
                break;
        }
    }


    /**
     * Centers the PIE x and y coordinates
     */
    protected void setCoordinates(int x, int y) {
        mCenter.y = y;
        mCenter.x = x;
    }

    /**
     * Sets the color of the imageviews
     */
    private void setColor(ImageView view, int color) {
        view.setColorFilter(color);
    }

    /**
     * Layout the pie
     */
    private void layoutPie() {
        int itemCount = mItems.size();

        float angle = 0;
        float total = 0;

        for (PieItem item : mItems) {
            mSweep = ((float) Math.PI / itemCount) * (item.isLesser() ? 0.65f : 1);
            angle = (mSweep - (float) Math.PI) / 2;
            View view = item.getView();

            if (view != null) {
                view.measure(view.getLayoutParams().width, view.getLayoutParams().height);
                int w = view.getMeasuredWidth();
                int h = view.getMeasuredHeight();
                int r = mOuterCircleRadius;
                int x = (int) (r * Math.sin(total + angle));
                int y = (int) (r * Math.cos(total + angle));

                switch (mPanelOrientation) {
                    case Gravity.LEFT:
                        y = mCenter.y - (int) (r * Math.sin(total + angle)) - h / 2;
                        x = (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.RIGHT:
                        y = mCenter.y - (int) (Math.PI / 2 - r * Math.sin(total + angle)) - h / 2;
                        x = mCenter.x - (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.BOTTOM:
                        y = mCenter.y - y - h / 2;
                        x = mCenter.x - x - w / 2;
                        break;
                }
                view.layout(x, y, x + w, y + h);
            }
            float itemStart = total + angle - mSweep / 2;
            item.setStartAngle(itemStart);
            total += mSweep;
        }
    }

    /**
     * Cancels all animations
     */
    private void cancelAnimation() {
        mPieBackgroundAnimator.cancel();
        mPieFadeAnimator.cancel();
        mToggleGrowAnimator.cancel();
        mToggleOuterGrowAnimator.cancel();
        mPieGrowAnimator.cancel();
        mRippleAnimator.cancel();
        mSettingsToggleAnimator.cancel();
        invalidate();
    }

    /**
     * Start the first animations
     */
    private void animateInStartup() {
        // cancel & start startup animations
        cancelAnimation();
        mPieMoveAnimator.start();
        mPieGrowAnimator.start();
        mPieFadeAnimator.setStartDelay(50);
        mPieFadeAnimator.start();
    }

    /**
     * Starts the rest of the animations
     */
    private void animateInRest(boolean fromTouch) {
        // start missing animations
        if (!mHasShown) {
            mHasShown = true;
            if (fromTouch) mPieBackgroundAnimator.setStartDelay(250);
            mPieBackgroundAnimator.start();
            mSettingsToggleAnimator.setStartDelay(fromTouch ? 2500 : 4500);
            mSettingsToggleAnimator.start();
        }
    }

    /**
     * Animates the PIE out of the view
     */
    private void animateOut(final TogglePoint toggle) {
        deselect();
        deselectNotifications();

        // Hook the listener up onto the main pie grow animator
        // since  this one is always available
        mPieGrowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mHasShown = false;
                mSnappointsShowing = false;
                mHandler.removeCallbacks(mAnimateRunnable);
                show(false);
                cancelAnimation();
                mReversing = false;
                if (toggle != null) {
                    if (toggle instanceof NowOnTapPoint) {
                        startAssist();
                    } else if (toggle instanceof SettingsPoint) {
                        mContext.sendBroadcast(mCloseDialogsIntent);
                        mContext.startActivity(mSettingsIntent);
                    } else if (toggle instanceof SnapPoint) {
                        mPanel.reorient(((SnapPoint)toggle).gravity);
                    }
                }
            }
        });

        // Cancel pie toggle animators
        mToggleGrowAnimator.cancel();
        mToggleOuterGrowAnimator.cancel();
        mSettingsToggleAnimator.cancel();

        // Remove start delay because we set it when animating in
        mPieFadeAnimator.setStartDelay(0);
        if (mHasShown && isAllowedToDraw()) {
            mPieBackgroundAnimator.setStartDelay(0);
            mPieBackgroundAnimator.reverse();
            if (mSettingsToggleFraction != 0f) {
                mSettingsToggleAnimator.setStartDelay(0);
                mSettingsToggleAnimator.reverse();
            }
        }

        // Reverse the animators
        mPieMoveAnimator.reverse();
        mPieGrowAnimator.reverse();
        mPieFadeAnimator.reverse();
        if (toggle != null) {
             mReversing = true;
        }
    }

    /**
     * Draws the PIE items
     */
    private void drawItem(Canvas canvas, PieItem item, float fraction) {
        if (item.getView() == null) return;
        final ImageView view = (ImageView) item.getView();
        final int itemOffset = item.getSize() / 2;
        final Point start = new Point(mCenter.x - itemOffset, mCenter.y + itemOffset);
        final int state = canvas.save();
        final float rippleFraction = mRippleAnimator.getAnimatedFraction();
        final float x = start.x + (fraction * (view.getX() - start.x));
        final float y = start.y + (fraction * (view.getY() - start.y));
        setColor(view, mDarkThemeEnabled ? mForegroundColor : item.getColor());
        canvas.translate(x, y);
        view.setImageAlpha((int) (mPieFadeAnimator.getAnimatedFraction() * 0xff));
        view.getBackground().setAlpha(view.isSelected()
                ? (int) (rippleFraction * (mDarkThemeEnabled ? 0x59 : 0x33)) : 0);
        view.draw(canvas);
        canvas.restoreToCount(state);
        if (DEBUG) {
            Log.d(TAG, "Drawing item: " + view.getTag());
        }
    }

    /**
     * Checks whether the PIE detail is allowed to show
     */
    private boolean isAllowedToDraw() {
        return !mPanel.isKeyguardLocked();
    }

    /**
     * Deselects current pie item
     */
    private void deselect() {
        for (PieItem item : mItems) {
            if (item == null) return;
            item.setSelected(false);
        }
    }

    /**
     * Deselects all notification icons
     */
    private void deselectNotifications() {
        for (AnimatedImageView view : mIconViews) {
            view.setSelected(false);
        }
    }

    /**
     * Calculates the polar
     */
    private float getPolar(float x, float y) {
        float deltaY = mCenter.y - y;
        float deltaX = mCenter.x - x;
        float adjustAngle = 0;
        switch (mPanelOrientation) {
            case Gravity.LEFT:
                adjustAngle = 90;
                break;
            case Gravity.RIGHT:
                adjustAngle = -90;
                break;
        }
        return (adjustAngle + (float) Math.atan2(deltaX,
                deltaY) * 180 / (float) Math.PI) * (float) Math.PI / 180;
    }

    /**
     * Broadcast receiver for battery and time
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)) {
                measureClock(getSimpleTime());
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra("level", 0);
                mBatteryPaint.setColor(mBatteryLevel >= 50 ? mBatteryColor
                        : (mBatteryLevel <= 15 ? mBatteryLowColor : mBatteryMedColor));
            }

            invalidate();
        }
    };

    /**
     * Get the current date in a simple format
     */
    private String getSimpleDate() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                mContext.getString(R.string.pie_date_format), Locale.getDefault());
        String date = sdf.format(new Date());
        return date.toUpperCase();
    }

    /**
     * Get the current time in a simple format
     */
    private String getSimpleTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                mContext.getString(DateFormat.is24HourFormat(mContext)
                        ? R.string.pie_hour_format_24
                        : R.string.pie_hour_format_12), Locale.getDefault());
        String time = sdf.format(new Date());
        return time.toUpperCase();
    }

    /**
     * Get the connect wifi network SSID.
     */
    private String getWifiSsid() {
        String ssid = null;
        NetworkInfo info = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (info.isConnected()) {
            final WifiInfo connectionInfo = mWifiManager.getConnectionInfo();
            String name = connectionInfo.getSSID();
            if (name != null) {
                ssid = name;
            }
            if (ssid == null) {
                List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
                for (WifiConfiguration net : networks) {
                    if (net.networkId == connectionInfo.getNetworkId()) {
                        ssid = net.SSID;
                    }
                }
            }
        }
        if (ssid != null) {
            ssid = ssid.replaceAll("^\"|\"$", "");
        }
        return ssid != null ? ssid.toUpperCase() : null;
    }

    /**
     * Get the current provider name and the mobile data type, if enabled
     */
    private String getNetworkText() {
        boolean mobileDataEnabled = TelephonyManager.getDefault().getDataEnabled(mSubId);
        String type = TelephonyManager.getDefault().getNetworkTypeName();
        String operatorName = mTelephonyManager.getNetworkOperatorName();
        if (operatorName == null) {
            operatorName = mTelephonyManager.getSimOperatorName();
        }
        if (operatorName != null && operatorName.equals("")) {
            operatorName = null;
        }
        if (!mobileDataEnabled) {
            return operatorName == null ? null : operatorName.toUpperCase();
        }
        return operatorName == null || type == null ? null :
                operatorName.toUpperCase() + "  " + type.toUpperCase();
    }

    /**
     * @return whether assistant is currently available
     */
    private boolean isAssistantAvailable() {
        return mAssistUtils.getAssistComponentForUser(UserHandle.USER_CURRENT) != null;
    }

    /**
     * Draws PIE stuff
     */
    @Override
    protected void onDraw(final Canvas canvas) {
        if (!mOpen) return;
        int state;

        if (isAllowedToDraw()) {
            // draw background
            canvas.drawARGB((int) (mBackgroundFraction * 0xcc), 0, 0, 0);

            // draw clock, date, battery level, line, wifi and network
            mClockPaint.setAlpha((int) (mBackgroundFraction * 0xff));
            mStatusPaint.setAlpha((int) (mBackgroundFraction * 0xff));
            mLinePaint.setAlpha((int) (mBackgroundFraction * 0x1f));
            if (mStatusIndicator != 2 && mStatusIndicator != 3) {
                mWifiIcon.setAlpha((int) (mBackgroundFraction * 0xff));
                if (mSsidText != null) {
                    canvas.drawText(mSsidText, mWidth / (isLandScape() ? 12 : 8), mStatusOffset
                            + mStatusTextTopMargin, mStatusPaint);
                }
            }
            if (mStatusIndicator != 1 && mStatusIndicator != 3) {
                mNetworkIcon.setAlpha((int) (mBackgroundFraction * 0xff));
                if (mNetworkText != null) {
                    if (!isLandScape()) {
                        mStatusPaint.setTextAlign(Paint.Align.RIGHT);
                    }
                    canvas.drawText(mNetworkText, isLandScape() ? mWidth / 12 : mWidth -
                              (mWidth / 8), isLandScape() ? (mHeight - (mStatusOffset
                              + mStatusTextTopMarginLand)) : (mStatusOffset
                              + mStatusTextTopMargin), mStatusPaint);
                    // restore to default alignment
                    mStatusPaint.setTextAlign(Paint.Align.LEFT);
                }
            }
            canvas.drawText(mClockText, mWidth / 2 - mClockOffsetX -
                    (isLandScape() ? mLandOffsetX : 0), mHeight / (mPieBottom ? 2 : 4)
                    - mClockOffsetY + (isLandScape() ? mLandOffsetY : 0), mClockPaint);
            canvas.drawText(getSimpleDate(), mWidth / 2 - mDateOffsetX -
                    (isLandScape() ? mLandOffsetX : 0), mHeight / (mPieBottom ? 2 : 4)
                    - mDateOffsetY + (isLandScape() ? mLandOffsetY : 0), mStatusPaint);
            // Don't draw battery text if disabled
            if (mBatteryMode != 1) {
                canvas.drawText(mBatteryText, mWidth / 2 - mBatteryOffsetX -
                        (isLandScape() ? mLandOffsetX : 0), mHeight / (mPieBottom ? 2 : 4) -
                        (mPieBottom ? mBatteryOffsetY : mBatteryOffsetYSide) +
                        (isLandScape() ? mLandOffsetY : 0), mStatusPaint);
            }
            // Hide line when there are no notifications
            if (mNotifications.size() > 0) {
                canvas.drawLine(mWidth / 2 - mLineLength / 2 - (isLandScape() ? mLineOffsetLand : 0),
                        (mPieBottom ? mHeight / 2 - mLineOffset : mHeight / 4 + mLineOffsetSide)
                        + (isLandScape() ? mLandOffsetY : 0), mWidth / 2 + mLineLength / 2 -
                        (isLandScape() ? mLineOffsetLand : 0),
                        (mPieBottom ? mHeight / 2 - mLineOffset : mHeight / 4 + mLineOffsetSide)
                        + (isLandScape() ? mLandOffsetY : 0), mLinePaint);
            }

            // draw notification icons
            for (AnimatedImageView view : mIconViews) {
                view.setAlpha((int) (mBackgroundFraction * 0xff));
                setColor(view, view.isSelected() ? mIconColor : mBackgroundColor);
            }
        }
        // draw snap points and now on tap
        for (int i = 0; i < mNumberOfSnapPoints; i++) {
            TogglePoint toggle = mTogglePoint[i];
            if (!toggle.isCurrentlyPossible(mCenterDistance > mShadeThreshold)
                    && !mToggleAnimator.isRunning()) {
                continue;
            }
            boolean isTogglePoint = toggle instanceof NowOnTapPoint
                    || toggle instanceof SettingsPoint;
            float toggleAnimatorFraction = mToggleAnimator.getAnimatedFraction();
            float toggleOuterAnimatorFraction = mToggleOuterGrowAnimator.getAnimatedFraction();
            float fraction = 1f + (toggle.active ?
                    mToggleGrowAnimator.getAnimatedFraction() * (isTogglePoint ? 0.5f : 0.7f) : 0f);
            float toggleOuterfraction = (toggle.active ? 1f + toggleOuterAnimatorFraction
                    * (isTogglePoint ? 0.9f : 1f) : 0);
            if (mDarkThemeEnabled) {
                if (isTogglePoint) {
                    mToggleBackground.setColor(mDarkBackgroundColor);
                } else {
                    mToggleBackground.setColor(mDarkSnappointColor);
                }
                mToggleShaderPaint.setShader(new LinearGradient(toggle.x - toggle.radius, toggle.y
                        - toggle.radius, toggle.x + toggle.radius, toggle.y + toggle.radius,
                        mShaderStartColor, mShaderEndColor, Shader.TileMode.CLAMP));
                mToggleShaderPaint.setAlpha((int) ((!isTogglePoint && mBackgroundFraction == 1f ?
                        toggleAnimatorFraction : (toggle instanceof SettingsPoint ?
                        mSettingsToggleFraction : mBackgroundFraction)) * 0xff));
                state = canvas.save();
                canvas.drawCircle(toggle.x, toggle.y, toggle.radius, mToggleShaderPaint);
                canvas.restoreToCount(state);
            } else {
                mToggleBackground.setColor(mForegroundColor);
            }
            toggle.draw(canvas, mToggleBackground, fraction, (!isTogglePoint && mBackgroundFraction
                    == 1f ? toggleAnimatorFraction : (toggle instanceof SettingsPoint
                            ? mSettingsToggleFraction : mBackgroundFraction)));
            // Only draw when outside animator is running.
            if (mToggleOuterGrowAnimator.isStarted()) {
                toggle.draw(canvas, mToggleOuterBackground, toggleOuterfraction,
                        toggleOuterAnimatorFraction / 2);
            }
        }

        final float pieMoveFraction = mPieMoveAnimator.getAnimatedFraction();
        final float pieGrowFraction = mPieGrowAnimator.getAnimatedFraction();
        final float circleCenterY = mCenter.y + mInnerCircleRadius -
                (pieMoveFraction * mInnerCircleRadius);
        final float circleThickness = pieGrowFraction * mOuterCircleThickness;
        final float circleRadius = pieMoveFraction * mOuterCircleRadius;

        if (mBatteryMode == 1 || mBatteryMode == 2) {
            // Draw battery circle
            final boolean pieLeft = mPanelOrientation == Gravity.LEFT;
            final boolean pieBottom = mPanelOrientation == Gravity.BOTTOM;
            final float startAngle = pieBottom ? 180 : (isLandScape() ? 90 : (pieLeft ? 270 : 90));
            final float sweepAngle = (float) (mBatteryLevel / 100.0 * 180.0);
            state = canvas.save();
            canvas.drawArc(mCenter.x - circleRadius + mBatteryCircleRadius,
                    mCenter.y - circleRadius + mBatteryCircleRadius, mCenter.x +
                    circleRadius - mBatteryCircleRadius, mCenter.y + circleRadius -
                    mBatteryCircleRadius, startAngle, sweepAngle, false, mBatteryPaint);
            canvas.restoreToCount(state);
        }

        // draw background circle
        mBackgroundCirclePaint.setAlpha(mDarkThemeEnabled ? 0 : 100);
        state = canvas.save();
        canvas.drawCircle(mCenter.x, circleCenterY, circleRadius, mBackgroundCirclePaint);
        canvas.restoreToCount(state);

        // draw outer circle
        state = canvas.save();
        final Path outerCirclePath = new Path();
        outerCirclePath.addCircle(mCenter.x, circleCenterY,
                circleRadius + circleThickness, Direction.CW);
        outerCirclePath.close();
        outerCirclePath.addCircle(mCenter.x, circleCenterY,
                circleRadius - circleThickness, Direction.CW);
        outerCirclePath.close();
        outerCirclePath.setFillType(Path.FillType.EVEN_ODD);
        outerCirclePath.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(outerCirclePath, mCirclePaint);
        canvas.restoreToCount(state);

        // draw inner circle
        state = canvas.save();
        canvas.drawCircle(mCenter.x, circleCenterY, mInnerCircleRadius, mCirclePaint);
        canvas.restoreToCount(state);

        // draw shade around PIE if dark theme is enabled
        if (mDarkThemeEnabled) {
            mShaderPaint.setShader(new LinearGradient(0, 0, 0, circleCenterY + circleRadius,
                    mShaderStartColor, mShaderEndColor, Shader.TileMode.CLAMP));
            state = canvas.save();
            canvas.drawCircle(mCenter.x, circleCenterY, circleRadius + circleThickness,
                    mShaderPaint);
            canvas.restoreToCount(state);
            mShaderPaint.setShader(new LinearGradient(mCenter.x - mInnerCircleRadius,
                    circleCenterY - mInnerCircleRadius, mCenter.x + mInnerCircleRadius,
                    circleCenterY + mInnerCircleRadius, mShaderStartColor, mShaderEndColor,
                    Shader.TileMode.CLAMP));
            state = canvas.save();
            canvas.drawCircle(mCenter.x, circleCenterY, mInnerCircleRadius, mShaderPaint);
            canvas.restoreToCount(state);
        }

        // draw buttons
        for (PieItem item : mItems) {
            drawItem(canvas, item, mPieMoveAnimator.getAnimatedFraction());
        }

        invalidateOutline();
    }

    /**
     * Touch handling for pie
     */
    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        if (evt.getPointerCount() > 1 || mReversing) return true;
        final float mX = evt.getRawX();
        final float mY = evt.getRawY();
        final float distanceX = mCenter.x - mX;
        final float distanceY = mCenter.y - mY;
        final float polar = getPolar(mX, mY);
        boolean snappointActive = false;
        PieItem item = null;
        for (PieItem it : mItems) {
            if ((it.getStartAngle() < polar) && (it.getStartAngle() + mSweep > polar)) {
                item = it;
            }
        }
        mCenterDistance = (float) Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

        int action = evt.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // open panel
                animateInStartup();
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < mNumberOfSnapPoints; i++) {
                    TogglePoint toggle = mTogglePoint[i];
                    if (!toggle.isCurrentlyPossible(true)) continue;

                    boolean settingsPoint = toggle instanceof SettingsPoint;
                    float toggleDistanceX = toggle.x - mX;
                    float toggleDistanceY = toggle.y - mY;
                    float toggleDistance = (float) Math.sqrt(Math.pow(toggleDistanceX, 2)
                            + Math.pow(toggleDistanceY, 2));

                    if (toggleDistance < toggle.radius && isAllowedToDraw()) {
                        if (settingsPoint && mSettingsToggleFraction == 0f) {
                            mSettingsToggleAnimator.setStartDelay(1);
                            mSettingsToggleAnimator.start();
                        }
                        if (!toggle.active && ((settingsPoint && mSettingsToggleFraction == 1f)
                                || (!settingsPoint))) {
                            mToggleGrowAnimator.cancel();
                            mToggleOuterGrowAnimator.cancel();
                            mToggleGrowAnimator.start();
                            mVibrator.vibrate(2);
                            toggle.active = true;
                        }
                    } else {
                        if (toggle.active) {
                            mToggleGrowAnimator.cancel();
                            mToggleOuterGrowAnimator.cancel();
                            toggle.active = false;
                        }
                    }
                    snappointActive = toggle.active;
                }

                // trigger the shades
                if (mCenterDistance > mShadeThreshold && isAllowedToDraw()) {
                    animateInRest(true);
                    deselect();
                    if (mHasShown && !mSnappointsShowing) {
                        mToggleAnimator.start();
                        mSnappointsShowing = true;
                    }
                    for (AnimatedImageView view : mIconViews) {
                        Rect rect = new Rect(view.getLeft(), view.getTop() - 375,
                                view.getRight(), view.getBottom() + (mPieBottom ? 375 : 0));
                        if (!snappointActive && rect.contains((int) mX, (int) mY)) {
                            view.setSelected(true);
                        } else {
                            view.setSelected(false);
                        }
                    }
                } else {
                    if (mSnappointsShowing) {
                        mToggleAnimator.reverse();
                        mSnappointsShowing = false;
                    }
                }
                if (mCenterDistance < mShadeThreshold && mCenterDistance > mInnerCircleRadius) {
                    if (item != null && item.getView() != null) {
                        if (!item.getView().isSelected()) {
                            deselect();
                            item.setSelected(true);
                            mRippleAnimator.start();
                        }
                    }
                } else {
                    deselect();
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (mOpen) {
                    for (int i = 0; i < mNumberOfSnapPoints; i++) {
                        TogglePoint toggle = mTogglePoint[i];
                        if (!toggle.isCurrentlyPossible(true)) continue;
                        if (toggle.active) {
                            mToggleGrowAnimator.end();
                            mToggleOuterGrowAnimator.end();
                            break;
                        }
                    }
                    // check for click actions
                    for (int i = 0; i < mIconViews.size(); i++) { // Use only 1 loop because the size is the same
                        AnimatedImageView view = mIconViews.get(i);
                        if (!view.isSelected()) continue;
                        Pair<StatusBarNotification, Icon> p = mNotifications.get(i);
                        final Notification notif = p.first.getNotification();
                        final PendingIntent pIntent = notif.contentIntent != null
                                ? notif.contentIntent : notif.fullScreenIntent;
                        try {
                            mBar.animateCollapsePanels(
                                    CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL, true);
                            ActivityManagerNative.getDefault().resumeAppSwitches();
                            if (pIntent != null) {
                                pIntent.send(null, 0, null, null, null, null,
                                        mBar.getActivityOptions(null));
                            }
                            mBar.onPerformRemoveNotification(p.first);
                        } catch (PendingIntent.CanceledException | RemoteException e) {
                            Log.e(TAG, "Failed trying to open notification " + e);
                        }
                        break;
                    }
                    if (item != null && item.getView() != null
                            && item.getView().isSelected()) {
                        item.getView().performClick();
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (item.getView().getTag().equals(PieController.RECENT_BUTTON) ||
                                item.getView().getTag().equals(PieController.HOME_BUTTON)) {
                            //dont do shit
                        }
                    }
                }

                // say good bye
                animateOut(null);
                return true;
        }
        // always re-dispatch event
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        setVisibility(View.GONE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        mWidth = w;
        mHeight = h;
        setOutlineProvider(new PieOutline());
    }

    private class AnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        private ValueAnimator animationIndex;

        AnimatorUpdateListener(ValueAnimator index) {
            animationIndex = index;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (animationIndex == mPieBackgroundAnimator) {
                mBackgroundFraction = animation.getAnimatedFraction();
            }
            if (animationIndex == mSettingsToggleAnimator) {
                mSettingsToggleFraction = animation.getAnimatedFraction();
            }
            if (animationIndex == mToggleGrowAnimator
                    && animation.getAnimatedFraction() >= 0.95
                    && !mToggleOuterGrowAnimator.isRunning()) {
                mToggleOuterGrowAnimator.start();
            }
            invalidate();
        }
    }

    private abstract class TogglePoint {
        public boolean active;
        public final int radius;
        public final int x;
        public final int y;

        TogglePoint(int toggleX, int toggleY, int toggleRadius) {
            x = toggleX;
            y = toggleY;
            radius = toggleRadius;
            active = false;
        }

        public void draw(Canvas canvas, Paint paint, float growFraction, float alphaFraction) {
            int growRadius = (int) (radius * growFraction);
            paint.setAlpha((int) (alphaFraction * 0xff));
            canvas.drawCircle(x, y, growRadius, paint);
        }

        public abstract boolean isCurrentlyPossible(boolean trigger);
    }

    private class SnapPoint extends TogglePoint {
        public final int gravity;

        SnapPoint(int snapX, int snapY, int snapRadius, int snapGravity) {
            super(snapX, snapY, snapRadius);
            gravity = snapGravity;
        }

        /**
         * @return whether the gravity of this snap point is usable under the current conditions
         */
        @Override
        public boolean isCurrentlyPossible(boolean trigger) {
            return trigger && mPanel.isGravityPossible(gravity);
        }
    }

    private class NowOnTapPoint extends TogglePoint {
        private final ImageView mLogo;

        NowOnTapPoint(int notX, int notY, int notRadius, ImageView logo, int logoSize) {
            super(notX, notY, notRadius);

            logo.setMinimumWidth(logoSize);
            logo.setMinimumHeight(logoSize);
            logo.setScaleType(ScaleType.FIT_XY);
            RelativeLayout.LayoutParams lp = new
                    RelativeLayout.LayoutParams(logoSize, logoSize);
            if (mPanelOrientation == Gravity.BOTTOM) {
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            }
            lp.leftMargin = notX - logoSize / 2;
            lp.topMargin = notY - logoSize / 2;
            logo.setLayoutParams(lp);
            mLogo = logo;
        }

        @Override
        public void draw(Canvas canvas, Paint paint, float growFraction, float
                alphaFraction) {
            super.draw(canvas, paint, growFraction, alphaFraction);
            // Don't set alpha when outer animator is running
            if (!mToggleOuterGrowAnimator.isRunning()) {
                mLogo.setAlpha(alphaFraction);
            }
        }

        /**
         * @return whether the assist manager is currently available
         */
        @Override
        public boolean isCurrentlyPossible(boolean trigger) {
            return isAssistantAvailable();
        }
    }

    private class SettingsPoint extends TogglePoint {
        private final ImageView mLogo;

        SettingsPoint(int x, int y, int radius, ImageView logo, int size) {
            super(x, y, radius);

            logo.setMinimumWidth(size);
            logo.setMinimumHeight(size);
            logo.setScaleType(ScaleType.FIT_XY);
            RelativeLayout.LayoutParams lp = new
                    RelativeLayout.LayoutParams(size, size);
            if (mPanelOrientation == Gravity.BOTTOM) {
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            }
            lp.leftMargin = x - size / 2;
            lp.topMargin = y - size / 2;
            logo.setLayoutParams(lp);
            mLogo = logo;
        }

        @Override
        public void draw(Canvas canvas, Paint paint, float growFraction, float
                alphaFraction) {
            super.draw(canvas, paint, growFraction, alphaFraction);
            // Don't set alpha when outer animator is running
            if (!mToggleOuterGrowAnimator.isRunning()) {
                mLogo.setAlpha(alphaFraction);
            }
        }

        /**
         * @return whether snappoint is available
         */
        @Override
        public boolean isCurrentlyPossible(boolean trigger) {
            return true;
        }
    }

    private class PieOutline extends ViewOutlineProvider {

        private final float mPadding;

        PieOutline() {
            mPadding = mResources.getDimensionPixelSize(R.dimen.pie_elevation);
        }

        @Override
        public void getOutline(View view, Outline outline) {
            float circleThickness = mPieGrowAnimator.getAnimatedFraction()
                    * mOuterCircleThickness;
            float circleRadius = mPieMoveAnimator.getAnimatedFraction()
                    * mOuterCircleRadius;
            int size = (int) (circleRadius + circleThickness + mPadding);
            final Path outerCirclePath = new Path();
            outerCirclePath.addCircle(0, 0,
                    circleRadius + circleThickness + mPadding, Direction.CW);
            outerCirclePath.close();
            outline.setConvexPath(outerCirclePath);
            outline.setOval(-size, -size, size, size);
            outline.setAlpha(0.6f);
            outline.offset(mCenter.x, mCenter.y);
        }
    }

    private class PieSignalCallback implements SignalCallback {
        public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
                boolean activityIn, boolean activityOut, String description, boolean isTransient) {
            mWifiIconResId = qsIcon == null ? 0 : qsIcon.icon;
            createSignalIcons();
        }

        public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
                int qsType, boolean activityIn, boolean activityOut,
                String typeContentDescription, String description, boolean isWide, int subId,
                boolean roaming, boolean isMobileIms) {
            mSubId = subId;
            mNetworkIconResId = qsIcon == null ? 0 : qsIcon.icon;
            createSignalIcons();
        }

        @Override
        public void setNoSims(boolean show,boolean simDetected) {
        }

        @Override
        public void setIsAirplaneMode(IconState icon) {
        }

        @Override
        public void setMobileDataEnabled(boolean enabled) {
        }
    }
}
