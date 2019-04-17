/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.ViewClippingUtil;
import com.android.keyguard.clocks.CustomAnalogClock;
import com.android.keyguard.clocks.CustomTextClock;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import com.google.android.collect.Sets;
import java.lang.Math;
import java.util.Locale;

import android.content.ContentResolver;
import com.android.systemui.omni.CurrentWeatherView;
import android.provider.Settings;

import android.text.Html;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener, View.OnLayoutChangeListener {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

	private static final String FONT_FAMILY = "sans-serif-light";

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;
    private final float mSmallClockScale;

    private TextView mLogoutView;
    private CustomAnalogClock mCustomClockView;
    private LinearLayout mTextClock;
    private TextClock mClockView;
    private View mClockSeparator;
    private TextView mOwnerInfo;
    private KeyguardSliceView mKeyguardSlice;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private ArraySet<View> mVisibleInDoze;
    private boolean mPulsing;
    private boolean mWasPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private float mWidgetPadding;
    private int mLastLayoutHeight;
    private CurrentWeatherView mWeatherView;
    private boolean mShowWeather;

    private boolean mShowClock;
    private int mClockSelection;

    private boolean mWasLatestViewSmall;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                updateSettings();
            }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refreshFormat();
            updateOwnerInfo();
            updateLogoutView();
            updateSettings();
        }

        @Override
        public void onLogoutEnabledChanged() {
            updateLogoutView();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIActivityManager = ActivityManager.getService();
        mLockPatternUtils = new LockPatternUtils(getContext());
        mHandler = new Handler(Looper.myLooper());
        mSmallClockScale = getResources().getDimension(R.dimen.widget_small_font_size)
                / getResources().getDimension(R.dimen.widget_big_font_size);
        onDensityOrFontScaleChanged();
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, "Schedule setEnableMarquee: " + (enabled ? "Enable" : "Disable"));
        if (enabled) {
            if (mPendingMarqueeStart == null) {
                mPendingMarqueeStart = () -> {
                    setEnableMarqueeImpl(true);
                    mPendingMarqueeStart = null;
                };
                mHandler.postDelayed(mPendingMarqueeStart, MARQUEE_DELAY_MS);
            }
        } else {
            if (mPendingMarqueeStart != null) {
                mHandler.removeCallbacks(mPendingMarqueeStart);
                mPendingMarqueeStart = null;
            }
            setEnableMarqueeImpl(false);
        }
    }

    private void setEnableMarqueeImpl(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLogoutView = findViewById(R.id.logout);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.clock_view);
        mClockView.setShowCurrentUserTime(true);
        mCustomClockView = findViewById(R.id.custom_clock_view);
        mTextClock = findViewById(R.id.custom_textclock_view);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);
        mClockSeparator = findViewById(R.id.clock_separator);
        mTextColor = mClockView.getCurrentTextColor();

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);

        mVisibleInDoze = Sets.newArraySet();
        if (mWeatherView != null) {
            mVisibleInDoze.add(mWeatherView);
        }
        if (mClockView != null) {
            mVisibleInDoze.add(mClockView);
        }
        if (mKeyguardSlice != null) {
            mVisibleInDoze.add(mKeyguardSlice);
        }
        if (mCustomClockView != null) {
            mVisibleInDoze.add(mCustomClockView);
        }

        int clockStroke = getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke);
        mClockView.getPaint().setStrokeWidth(clockStroke);
        mClockView.addOnLayoutChangeListener(this);
        mClockSeparator.addOnLayoutChangeListener(this);
        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        updateSettings();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
        updateSettings();
    }

    /**
     * Moves clock and separator, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        boolean smallClock = mKeyguardSlice.hasHeader() || mPulsing;
        prepareSmallView(smallClock);
        float clockScale = smallClock ? mSmallClockScale : 1;
		Typeface tf = Typeface.create(FONT_FAMILY, Typeface.NORMAL);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mClockView.getLayoutParams();
        int height = mClockView.getHeight();
        layoutParams.bottomMargin = (int) -(height - (clockScale * height));
        mClockView.setLayoutParams(layoutParams);
		mClockView.setTypeface(tf);

        // Custom analog clock
        RelativeLayout.LayoutParams customlayoutParams =
                (RelativeLayout.LayoutParams) mCustomClockView.getLayoutParams();
        customlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mCustomClockView.setLayoutParams(customlayoutParams);

        //Custom Text clock
        RelativeLayout.LayoutParams textlayoutParams =
                (RelativeLayout.LayoutParams) mTextClock.getLayoutParams();
        textlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mTextClock.setLayoutParams(textlayoutParams);

        layoutParams = (RelativeLayout.LayoutParams) mClockSeparator.getLayoutParams();
        layoutParams.topMargin = smallClock ? (int) mWidgetPadding : 0;
        layoutParams.bottomMargin = layoutParams.topMargin;
        mClockSeparator.setLayoutParams(layoutParams);
    }

    /**
     * Animate clock and its separator when necessary.
     */
    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int heightOffset = mPulsing || mWasPulsing ? 0 : getHeight() - mLastLayoutHeight;
        boolean hasHeader = mKeyguardSlice.hasHeader();
        boolean smallClock = hasHeader || mPulsing;
        prepareSmallView(smallClock);
        long duration = KeyguardSliceView.DEFAULT_ANIM_DURATION;
        long delay = smallClock || mWasPulsing ? 0 : duration / 4;
        mWasPulsing = false;

        boolean shouldAnimate = mKeyguardSlice.getLayoutTransition() != null
                && mKeyguardSlice.getLayoutTransition().isRunning();
        if (view == mClockView) {
            float clockScale = smallClock ? mSmallClockScale : 1;
            Paint.Style style = smallClock ? Paint.Style.FILL_AND_STROKE : Paint.Style.FILL;
            mClockView.animate().cancel();
            if (shouldAnimate) {
                mClockView.setY(oldTop + heightOffset);
                mClockView.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(new ClipChildrenAnimationListener())
                        .setStartDelay(delay)
                        .y(top)
                        .scaleX(clockScale)
                        .scaleY(clockScale)
                        .withEndAction(() -> {
                            mClockView.getPaint().setStyle(style);
                            mClockView.invalidate();
                        })
                        .start();
            } else {
                mClockView.setY(top);
                mClockView.setScaleX(clockScale);
                mClockView.setScaleY(clockScale);
                mClockView.getPaint().setStyle(style);
                mClockView.invalidate();
            }
        } else if (view == mClockSeparator) {
            boolean hasSeparator = hasHeader && !mPulsing;
            float alpha = hasSeparator ? 1 : 0;
            mClockSeparator.animate().cancel();
            if (shouldAnimate) {
                boolean isAwake = mDarkAmount != 0;
                mClockSeparator.setY(oldTop + heightOffset);
                mClockSeparator.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(isAwake ? null : new KeepAwakeAnimationListener(getContext()))
                        .setStartDelay(delay)
                        .y(top)
                        .alpha(alpha)
                        .start();
            } else {
                mClockSeparator.setY(top);
                mClockSeparator.setAlpha(alpha);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mClockView.setPivotX(mClockView.getWidth() / 2);
        mClockView.setPivotY(0);
        mLastLayoutHeight = getHeight();
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        mWidgetPadding = getResources().getDimension(R.dimen.widget_vertical_padding);
        if (mClockView != null) {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
            mClockView.getPaint().setStrokeWidth(
                    getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke));
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        }
        if (mWeatherView != null) {
            mWeatherView.onDensityOrFontScaleChanged();
        }
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
    }

    private void refreshTime() {
        mClockView.refresh();

        if (mClockSelection == 0 || mWasLatestViewSmall) {
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 1) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>mm"));
        } else if (mClockSelection == 4) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        } else if (mClockSelection == 5) {
            mClockView.setFormat12Hour(Html.fromHtml("hh<br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
        } else if (mClockSelection == 6) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color='#454545'>hh</font><br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color='#454545'>kk</font><br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
        } else if (mClockSelection == 7) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.clock_gradient_full_clock_text_color) + ">hh mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.clock_gradient_full_clock_text_color) + ">kk mm</font>"));
        } else if (mClockSelection == 8) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.clock_gradient_full_clock_text_color) + ">hh\nmm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.clock_gradient_full_clock_text_color) + ">kk\nmm</font>"));
        } else {
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        }
    }

    private void refreshFormat() {
        Patterns.update(mContext);
        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    public int getLogoutButtonHeight() {
        if (mLogoutView == null) {
            return 0;
        }
        return mLogoutView.getVisibility() == VISIBLE ? mLogoutView.getHeight() : 0;
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    private void updateLogoutView() {
        if (mLogoutView == null) {
            return;
        }
        mLogoutView.setVisibility(shouldShowLogout() ? VISIBLE : GONE);
        // Logout button will stay in language of user 0 if we don't set that manually.
        mLogoutView.setText(mContext.getResources().getString(
                com.android.internal.R.string.global_action_logout));
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String info = mLockPatternUtils.getDeviceOwnerInfo();
        if (info == null) {
            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        mOwnerInfo.setText(info);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onLocaleListChanged() {
        refreshFormat();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void updateVisibilities() {
        switch (mClockSelection) {
            case 0: // default digital
            default:
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.setLineSpacing(0,1f);
                mCustomClockView.setVisibility(View.GONE);
		        mClockView.setBackgroundResource(0);
                mTextClock.setVisibility(View.GONE);
                break;
            case 1: // digital (bold)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.setLineSpacing(0,1f);
                mCustomClockView.setVisibility(View.GONE);
		        mClockView.setBackgroundResource(0);
                mTextClock.setVisibility(View.GONE);
                break;
            case 2: // custom analog
                mCustomClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.setLineSpacing(0,1f);
                mClockView.setVisibility(View.GONE);
		        mClockView.setBackgroundResource(0);
                mTextClock.setVisibility(View.GONE);
                break;
            case 3: // sammy
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.setLineSpacing(0,1f);
                mCustomClockView.setVisibility(View.GONE);
		        mClockView.setBackgroundResource(0);
                mTextClock.setVisibility(View.GONE);
                break;
            case 4: // sammy (bold)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.setLineSpacing(0,1f);
                mCustomClockView.setVisibility(View.GONE);
                mClockView.setBackgroundResource(0);
                mTextClock.setVisibility(View.GONE);
                break;
            case 5: // sammy accent
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_sammy_accent_font_size));
                mClockView.setLineSpacing(0,1f);
                mCustomClockView.setVisibility(View.GONE);
		        mClockView.setBackgroundResource(0);
                mTextClock.setVisibility(View.GONE);
                break;
            case 6: // sammy accent
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_sammy_accent_font_size));
                mClockView.setLineSpacing(0,1f);
                mCustomClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                break;
            case 7: // Gradient with dark clock
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setBackground(getResources().getDrawable(R.drawable.clock_bootleg_gradient_shadow));
                mClockView.getLayoutParams().width = getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_size);
                mClockView.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_size);
                mClockView.setLineSpacing(0,1f);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_nerves_font_size));
                mClockView.setPadding(0,20,0,20);
                mCustomClockView.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
        getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
        getResources().getDisplayMetrics()),0,0
                );
                mTextClock.setVisibility(View.GONE);
                break;
            case 8: // Monochrome gradient, like the qs tile
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setBackground(getResources().getDrawable(R.drawable.clock_bootleg_qsgradient));
                mClockView.getLayoutParams().width = getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_size);
                mClockView.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_size);
                mClockView.setLineSpacing(0,1f);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_nerves_font_size));
                mClockView.setPadding(0,20,0,20);
                mCustomClockView.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
        getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
        getResources().getDisplayMetrics()),0,0
                );
                mTextClock.setVisibility(View.GONE);
                break;
            case 9: // custom text clock
                mTextClock.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setVisibility(View.GONE);
                mCustomClockView.setVisibility(View.GONE);
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();

        mShowClock = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK, 1, UserHandle.USER_CURRENT) == 1;
        mClockSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);

        setStyle();

        final Resources res = getContext().getResources();
        mShowWeather = Settings.System.getIntForUser(resolver,
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        if (mWeatherView != null) {
            if (mShowWeather) {
                mWeatherView.setVisibility(View.VISIBLE);
                mWeatherView.enableUpdates();
            }
            if (!mShowWeather) {
                mWeatherView.setVisibility(View.GONE);
                mWeatherView.disableUpdates();
            }
	}

    }

    private void setStyle() {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                mKeyguardSlice.getLayoutParams();
        switch (mClockSelection) {
            case 0: // default digital
            default:
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 1: // digital (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 2: // custom analog
                params.addRule(RelativeLayout.BELOW, R.id.custom_clock_view);
                break;
            case 3: // sammy
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 4: // sammy (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 5: // sammy accent
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 6: // sammy accent
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 7: // Gradient with dark clock
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 8: // Monochrome gradient, like the qs tile
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 9: // custom text clock
                params.addRule(RelativeLayout.BELOW, R.id.custom_textclock_view);
                break;
        }
    }

    private void prepareSmallView(boolean small) {
        if (mWasLatestViewSmall == small) return;
        mWasLatestViewSmall = small;
        if (small) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                    mKeyguardSlice.getLayoutParams();
            params.addRule(RelativeLayout.BELOW, R.id.clock_view);
            mClockView.setSingleLine(true);
            mClockView.setGravity(Gravity.CENTER);
            mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                    View.GONE) : View.VISIBLE);
            mCustomClockView.setVisibility(View.GONE);
            mTextClock.setVisibility(View.GONE);
        } else {
            setStyle();
            refreshTime();
        }
    }

    public void updateAll() {
        mKeyguardSlice.refresh();
        mKeyguardSlice.updateSettings();
        updateVisibilities();
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        updateDark();
    }

    private void updateDark() {
        boolean dark = mDarkAmount == 1;
        if (mLogoutView != null) {
            mLogoutView.setAlpha(dark ? 0 : 1);
        }

        if (mOwnerInfo != null) {
            boolean hasText = !TextUtils.isEmpty(mOwnerInfo.getText());
            mOwnerInfo.setVisibility(hasText ? VISIBLE : GONE);
            layoutOwnerInfo();
        }

        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        updateDozeVisibleViews();
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        mClockSeparator.setBackgroundColor(blendedTextColor);
        mCustomClockView.setDark(dark);
        updateVisibilities();
    }

    private void layoutOwnerInfo() {
        if (mOwnerInfo != null && mOwnerInfo.getVisibility() != GONE) {
            // Animate owner info during wake-up transition
            mOwnerInfo.setAlpha(1f - mDarkAmount);

            float ratio = mDarkAmount;
            // Calculate how much of it we should crop in order to have a smooth transition
            int collapsed = mOwnerInfo.getTop() - mOwnerInfo.getPaddingTop();
            int expanded = mOwnerInfo.getBottom() + mOwnerInfo.getPaddingBottom();
            int toRemove = (int) ((expanded - collapsed) * ratio);
            setBottom(getMeasuredHeight() - toRemove);
        }
    }

    public void setPulsing(boolean pulsing, boolean animate) {
        if (mPulsing == pulsing) {
            return;
        }
        if (mPulsing) {
            mWasPulsing = true;
        }
        mPulsing = pulsing;
        // Animation can look really weird when the slice has a header, let's hide the views
        // immediately instead of fading them away.
        if (mKeyguardSlice.hasHeader()) {
            animate = false;
        }
        mKeyguardSlice.setPulsing(pulsing, animate);
        if (mWeatherView != null) {
            mWeatherView.setVisibility((mShowWeather && !mPulsing) ? View.VISIBLE : View.GONE);
        }
        updateDozeVisibleViews();
    }

    private void updateDozeVisibleViews() {
        for (View child : mVisibleInDoze) {
            child.setAlpha(mDarkAmount == 1 && mPulsing ? 0.8f : 1);
        }
    }

    private boolean shouldShowLogout() {
        return KeyguardUpdateMonitor.getInstance(mContext).isLogoutEnabled()
                && KeyguardUpdateMonitor.getCurrentUser() != UserHandle.USER_SYSTEM;
    }

    private void onLogoutClicked(View view) {
        int currentUserId = KeyguardUpdateMonitor.getCurrentUser();
        try {
            mIActivityManager.switchUser(UserHandle.USER_SYSTEM);
            mIActivityManager.stopUser(currentUserId, true /*force*/, null);
        } catch (RemoteException re) {
            Log.e(TAG, "Failed to logout user", re);
        }
    }

//    private void updateSettings() {
//        final ContentResolver resolver = getContext().getContentResolver();
//        final Resources res = getContext().getResources();
//        mShowWeather = Settings.System.getIntForUser(resolver,
//                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
//                UserHandle.USER_CURRENT) == 1;

//        if (mWeatherView != null) {
//            if (mShowWeather) {
//                mWeatherView.setVisibility(View.VISIBLE);
//                mWeatherView.enableUpdates();
//            }
//            if (!mShowWeather) {
//                mWeatherView.setVisibility(View.GONE);
//                mWeatherView.disableUpdates();
//            }
//        }
//    }

    private class ClipChildrenAnimationListener extends AnimatorListenerAdapter implements
            ViewClippingUtil.ClippingParameters {

        ClipChildrenAnimationListener() {
            ViewClippingUtil.setClippingDeactivated(mClockView, true /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            ViewClippingUtil.setClippingDeactivated(mClockView, false /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public boolean shouldFinish(View view) {
            return view == getParent();
        }
    }
}
