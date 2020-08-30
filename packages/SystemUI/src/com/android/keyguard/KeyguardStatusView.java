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

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.android.internal.util.rr.RRFontHelper;
import android.view.Gravity;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.omni.CurrentWeatherView;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.tuner.TunerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.TimeZone;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener, TunerService.Tunable {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;

    private LinearLayout mStatusViewContainer;
    private TextView mLogoutView;
    private KeyguardClockSwitch mClockView;
    private TextView mOwnerInfo;
    private KeyguardSliceView mKeyguardSlice;
    private View mNotificationIcons;
    private View mKeyguardSliceView;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private boolean mPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private CurrentWeatherView mWeatherView;
    private boolean mShowWeather;
    private boolean mPulsingAllowed = false;
    private boolean mPulseWeather;
    private boolean mPixelStyle;
    private int mLockClockFontSize;
    private int mDateSelection;
    private boolean mShowClock = true;
    private boolean mShowDate = true;
    private int mWeatherBgSelection;
    private int mWeatherViewAlignment;
    private int mOwnerInfoAlignment;
    private int mItemPadding;
    private int mWeatherItemPadding;
 
    // Date styles paddings
    private int mDateVerPadding;
    private int mDateHorPadding;

    private static final String LOCKSCREEN_WEATHER_ENABLED =
            "system:" + Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED;
    private static final String LOCKSCREEN_WEATHER_STYLE =
            "system:" + Settings.System.LOCKSCREEN_WEATHER_STYLE;
    private static final String LS_WEATHER_PULSING =
            "system:" + Settings.System.LS_WEATHER_PULSING;

    private static final String LOCKSCREEN_DATE_SELECTION =
            "system:" + Settings.System.LOCKSCREEN_DATE_SELECTION;
    private static final String LOCKSCREEN_CLOCK =
            "system:" + Settings.System.LOCKSCREEN_CLOCK;
    private static final String LOCKSCREEN_DATE =
            "system:" + Settings.System.LOCKSCREEN_DATE;
    private static final String LOCKSCREEN_WEATHER_SELECTION =
            "system:" + Settings.System.LOCKSCREEN_WEATHER_SELECTION;
    private static final String LOCKSCREEN_WEATHER_ALIGNMENT =
            "system:" + Settings.System.LOCKSCREEN_WEATHER_ALIGNMENT;
    private static final String LOCK_OWNERINFO_ALIGNMENT =
            "system:" + Settings.System.LOCK_OWNERINFO_ALIGNMENT;
    private static final String LOCKSCREEN_ITEM_PADDING =
            "system:" + Settings.System.LOCKSCREEN_ITEM_PADDING;
    private static final String LOCKSCREEN_WEATHER_PADDING =
            "system:" + Settings.System.LOCKSCREEN_WEATHER_PADDING;
    /**
     * Bottom margin that defines the margin between bottom of smart space and top of notification
     * icons on AOD.
     */
    private int mIconTopMargin;
    private int mIconTopMarginWithHeader;
    private boolean mShowingHeader;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
        }

        @Override
        public void onTimeZoneChanged(TimeZone timeZone) {
            updateTimeZone(timeZone);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                updateItemPadding();
                updateDateStyles();
                updateWeatherPadding();
                updateWeatherView();
                mClockView.refreshLockFont();
		        refreshLockDateFont();
		        mClockView.refreshclocksize();
		        mKeyguardSlice.refreshdatesize();
		        mKeyguardSlice.updateDateposition();
                mClockView.updateClockColor();
                updateClockDateColor();
                updateOwnerInfoColor();
                refreshOwnerInfoSize();
                refreshOwnerInfoFont();
                updateOwnerInfoAlignment();
                updateDateVisbility();
                updateClockVisbility();
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
            updateItemPadding();
            updateWeatherPadding();
            updateWeatherView();
            updateDateStyles();         
            mClockView.refreshLockFont();
            refreshLockDateFont();
	        refreshLockDateFont();
	        mClockView.refreshclocksize();
		    mKeyguardSlice.updateDateposition();
	        mKeyguardSlice.refreshdatesize();
            mClockView.updateClockColor();
            updateOwnerInfoAlignment();
            updateClockDateColor();
            updateOwnerInfoColor();
            refreshOwnerInfoSize();
            refreshOwnerInfoFont();
            updateDateVisbility();
            updateClockVisbility();
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
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, LOCKSCREEN_WEATHER_ENABLED);
        tunerService.addTunable(this, LOCKSCREEN_WEATHER_STYLE);
        tunerService.addTunable(this, LOCKSCREEN_DATE_SELECTION);
        tunerService.addTunable(this, LOCKSCREEN_DATE);
        tunerService.addTunable(this, LOCKSCREEN_CLOCK);
        tunerService.addTunable(this, LOCKSCREEN_WEATHER_SELECTION);
        tunerService.addTunable(this, LOCKSCREEN_WEATHER_ALIGNMENT);
        tunerService.addTunable(this, LOCK_OWNERINFO_ALIGNMENT);
        tunerService.addTunable(this, LOCKSCREEN_ITEM_PADDING);
        tunerService.addTunable(this, LOCKSCREEN_WEATHER_PADDING);
        onDensityOrFontScaleChanged();
    }

    /**
     * If we're presenting a custom clock of just the default one.
     */
    public boolean hasCustomClock() {
        return mClockView.hasCustomClock();
    }

    public boolean hasCustomClockInBigContainer() {
        return mClockView.hasCustomClockInBigContainer();
    }

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        mClockView.setHasVisibleNotifications(hasVisibleNotifications);
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
        mStatusViewContainer = findViewById(R.id.status_view_container);
        mLogoutView = findViewById(R.id.logout);
        mNotificationIcons = findViewById(R.id.clock_notification_icon_container);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.keyguard_clock_container);
        mClockView.setShowCurrentUserTime(true);
        if (KeyguardClockAccessibilityDelegate.isNeeded(mContext)) {
            mClockView.setAccessibilityDelegate(new KeyguardClockAccessibilityDelegate(mContext));
        }
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);
        mKeyguardSliceView = findViewById(R.id.keyguard_status_area);

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);
        mClockView.refreshLockFont();
	    refreshLockDateFont();
        mClockView.refreshclocksize();
		mKeyguardSlice.updateDateposition();
	    mKeyguardSlice.refreshdatesize();
	    mClockView.updateClockColor();
	    updateClockDateColor();
	    updateOwnerInfoColor();
	    refreshOwnerInfoSize();
	    refreshOwnerInfoFont();
        updateOwnerInfoAlignment();
        updateItemPadding();
        mTextColor = mClockView.getCurrentTextColor();

        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
        updateDateStyles();
        updateDateVisbility();
        updateWeatherPadding();
        updateWeatherView();
    }

    /**
     * Moves clock, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        final boolean hasHeader = mKeyguardSlice.hasHeader();
        mClockView.setKeyguardShowingHeader(hasHeader);
        if (mShowingHeader == hasHeader) {
            return;
        }
        mShowingHeader = hasHeader;
        if (mNotificationIcons != null) {
            // Update top margin since header has appeared/disappeared.
            MarginLayoutParams params = (MarginLayoutParams) mNotificationIcons.getLayoutParams();
            params.setMargins(params.leftMargin,
                    hasHeader ? mIconTopMarginWithHeader : mIconTopMargin,
                    params.rightMargin,
                    params.bottomMargin);
            mNotificationIcons.setLayoutParams(params);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        if (mClockView != null) {
            mClockView.refreshclocksize();
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        }
        if (mWeatherView != null) {
            mWeatherView.onDensityOrFontScaleChanged();
        }
        loadBottomMargin();
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
    }

    private void refreshTime() {
        mClockView.refresh();
    }

    private void updateTimeZone(TimeZone timeZone) {
        mClockView.onTimeZoneChanged(timeZone);
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 32);
    }

    private int getLockClockSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 78);
    }


    private int getOwnerInfoFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_OWNERINFO_FONTS, 0);
    }

    private int getOwnerInfoSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKOWNER_FONT_SIZE, 21);
    }

    private void updateClockDateColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CLOCK_DATE_COLOR, 0xFFFFFFFF);

        if (mKeyguardSlice != null) {
            mKeyguardSlice.setTextColor(color);
       	}
    }

    private void updateOwnerInfoColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_OWNER_INFO_COLOR, 0xFFFFFFFF);

        if (mOwnerInfo != null) {
            mOwnerInfo.setTextColor(color);
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

    private void refreshLockDateFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockDateFont = isPrimary ? getLockDateFont() : 32;
        if (lockDateFont == 0) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockDateFont == 1) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockDateFont == 2) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockDateFont == 3) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 4) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockDateFont == 5) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockDateFont == 6) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockDateFont == 7) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockDateFont == 8) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockDateFont == 9) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockDateFont == 10) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockDateFont == 11) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 12) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockDateFont == 13) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockDateFont == 14) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockDateFont == 15) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockDateFont == 16) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockDateFont == 17) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (lockDateFont == 18) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (lockDateFont == 19) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (lockDateFont == 20) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (lockDateFont == 21) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (lockDateFont == 22) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (lockDateFont == 23) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (lockDateFont == 24) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 25) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("accuratist", Typeface.NORMAL));
        }
        if (lockDateFont == 26) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("aclonica", Typeface.NORMAL));
        }
        if (lockDateFont == 27) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("amarante", Typeface.NORMAL));
        }
        if (lockDateFont == 28) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("bariol", Typeface.NORMAL));
        }
        if (lockDateFont == 29) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
        }
        if (lockDateFont == 30) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("cocon", Typeface.NORMAL));
        }
        if (lockDateFont == 31) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
        }

        if (lockDateFont == 32) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("comicsans", Typeface.NORMAL));
        }
        if (lockDateFont == 33) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("coolstory", Typeface.NORMAL));
        }
        if (lockDateFont == 34) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("exotwo", Typeface.NORMAL));
        }
        if (lockDateFont == 35) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
        }
        if (lockDateFont == 36) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("googlesans", Typeface.NORMAL));
        }
        if (lockDateFont == 37) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
        }
        if (lockDateFont == 38) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("lato", Typeface.NORMAL));
        }
        if (lockDateFont == 39) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
        }
        if (lockDateFont == 40) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
        }
        if (lockDateFont == 41) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("nunito", Typeface.NORMAL));
        }
        if (lockDateFont == 42) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("quando", Typeface.NORMAL));
        }

        if (lockDateFont == 43) {
                mKeyguardSlice.setViewsTypeface(Typeface.create("redressed", Typeface.NORMAL));
        }
        if (lockDateFont == 44) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (lockDateFont == 45) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
        }
        if (lockDateFont == 46) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("rosemary", Typeface.NORMAL));
        }
        if (lockDateFont == 47) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("samsungone", Typeface.NORMAL));
        }
        if (lockDateFont == 48) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
        }
        if (lockDateFont == 49) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
        }
        if (lockDateFont == 50) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("storopia", Typeface.NORMAL));
        }
        if (lockDateFont == 51) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("surfer", Typeface.NORMAL));
        }
        if (lockDateFont == 52) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
        }
        if (lockDateFont == 53) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("antipastopro", Typeface.NORMAL));
        }
        if (lockDateFont == 54) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("evolvesans", Typeface.NORMAL));
        }
        if (lockDateFont == 55) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("fucek", Typeface.NORMAL));
        }
        if (lockDateFont == 56) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("lemonmilk", Typeface.NORMAL));
        }
        if (lockDateFont == 57) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("oduda", Typeface.NORMAL));
        }
        if (lockDateFont == 58) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 59) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 60) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("monospace", Typeface.NORMAL));
        }
        if (lockDateFont == 61) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 62) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("simpleday", Typeface.NORMAL));
        }
        if (lockDateFont == 63) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("gobold-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 64) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 65) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 66) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 67) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 68) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 69) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 70) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("linotte", Typeface.NORMAL));
        }
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    /**
     * Returns the preferred Y position of the clock.
     *
     * @param totalHeight The height available to position the clock.
     * @return Y position of clock.
     */
    public int getClockPreferredY(int totalHeight) {
        return mClockView.getPreferredY(totalHeight);
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
	updateOwnerInfoColor();
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

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardStatusView:");
        pw.println("  mOwnerInfo: " + (mOwnerInfo == null
                ? "null" : mOwnerInfo.getVisibility() == VISIBLE));
        pw.println("  mPulsing: " + mPulsing);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        if (mLogoutView != null) {
            pw.println("  logout visible: " + (mLogoutView.getVisibility() == VISIBLE));
        }
        if (mClockView != null) {
            mClockView.dump(fd, pw, args);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.dump(fd, pw, args);
        }
    }

    private void loadBottomMargin() {
        mIconTopMargin = getResources().getDimensionPixelSize(R.dimen.widget_vertical_padding);
        mIconTopMarginWithHeader = getResources().getDimensionPixelSize(
                R.dimen.widget_vertical_padding_with_header);
    }

    public void refreshOwnerInfoSize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int ownerInfoSize = isPrimary ? getOwnerInfoSize() : 21;

        if (ownerInfoSize == 10) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (ownerInfoSize == 11) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (ownerInfoSize == 12) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (ownerInfoSize == 13) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (ownerInfoSize == 14) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
        }  else if (ownerInfoSize == 15) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (ownerInfoSize == 16) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (ownerInfoSize == 17) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (ownerInfoSize == 18) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (ownerInfoSize == 19) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (ownerInfoSize == 20) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (ownerInfoSize == 21) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (ownerInfoSize == 22) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (ownerInfoSize == 23) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (ownerInfoSize == 24) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (ownerInfoSize == 25) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
        }
    }


    private void refreshOwnerInfoFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int ownerinfoFont = isPrimary ? getOwnerInfoFont() : 0;
        RRFontHelper.setFontType(mOwnerInfo ,ownerinfoFont);
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

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        mClockView.setDarkAmount(darkAmount);
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
            if (mNotificationIcons != null) {
                // We're using scrolling in order not to overload the translation which is used
                // when appearing the icons
                mNotificationIcons.setScrollY(toRemove);
            }
        } else if (mNotificationIcons != null){
            mNotificationIcons.setScrollY(0);
        }
    }

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
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

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_WEATHER_ENABLED:
                mShowWeather =
                        TunerService.parseIntegerSwitch(newValue, false);
                updateWeatherView();
                break;
            case LOCKSCREEN_WEATHER_STYLE:
                mPixelStyle =
                        TunerService.parseIntegerSwitch(newValue, true);
                updateWeatherView();
                break;
            case LOCKSCREEN_DATE_SELECTION:
                mDateSelection =
                        TunerService.parseInteger(newValue, 0);
                updateDateStyles();
                break;
            case LOCKSCREEN_DATE:
                mShowDate =
                        TunerService.parseIntegerSwitch(newValue, true);
                updateDateVisbility();
                break;
            case LOCKSCREEN_CLOCK:
                mShowClock =
                        TunerService.parseIntegerSwitch(newValue, true);
                updateClockVisbility();
                break;
            case LOCKSCREEN_WEATHER_SELECTION:
                mWeatherBgSelection =
                        TunerService.parseInteger(newValue, 0);
                updateWeatherView();
                break;
            case LOCKSCREEN_WEATHER_ALIGNMENT:
                mWeatherViewAlignment =
                        TunerService.parseInteger(newValue, 1);
                updateWeatherAlignment();
                break;
            case LOCK_OWNERINFO_ALIGNMENT:
                mOwnerInfoAlignment =
                        TunerService.parseInteger(newValue, 1);
                updateOwnerInfoAlignment();
                break;
            case LOCKSCREEN_ITEM_PADDING:
                mItemPadding =
                        TunerService.parseInteger(newValue, 35);
                updateItemPadding();
                break;
            case LOCKSCREEN_WEATHER_PADDING:
                mWeatherItemPadding =
                        TunerService.parseInteger(newValue, 35);
                updateWeatherPadding();
                updateWeatherAlignment();
                break;
            default:
                break;
        }
    }

    private int updateWeatherPadding() {
        switch (mWeatherItemPadding) {
            case 0:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_0);
            case 1:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_1);
            case 2:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_2);
            case 3:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_3);
            case 4:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_4);
            case 5:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_5);
            case 6:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_6);
            case 7:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_7);
            case 8:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_8);
            case 9:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_9);
            case 10:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_10);
            case 11:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_11);
            case 12:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_12);
            case 13:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_13);
            case 14:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_14);
            case 15:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_15);
            case 16:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_16);
            case 17:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_17);
            case 18:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_18);
            case 19:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_19);
            case 20:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_20);
            case 21:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_21);
            case 22:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_22);
            case 23:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_23);
            case 24:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_24);
            case 25:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_25);
            case 26:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_26);
            case 27:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_27);
            case 28:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_28);
            case 29:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_29);
            case 30:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_30);
            case 31:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_31);
            case 32:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_32);
            case 33:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_33);
            case 34:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_34);
            case 35:
            default:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_35);
            case 36:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_36);
            case 37:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_37);
            case 38:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_38);
            case 39:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_39);
            case 40:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_40);
            case 41:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_41);
            case 42:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_42);
            case 43:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_43);
            case 44:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_44);
            case 45:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_45);
            case 46:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_46);
            case 47:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_47);
            case 48:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_48);
            case 49:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_49);
            case 50:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_50);
            case 51:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_51);
            case 52:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_52);
            case 53:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_53);
            case 54:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_54);
            case 55:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_55);
            case 56:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_56);
            case 57:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_57);
            case 58:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_58);
            case 59:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_59);
            case 60:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_60);
            case 61:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_61);
            case 62:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_62);
            case 63:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_63);
            case 64:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_64);
            case 65:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_65);
            case 66:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_66);
            case 67:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_67);
            case 68:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_68);
            case 69:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_69);
            case 70:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_70);
            case 71:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_71);
            case 72:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_72);
            case 73:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_73);
            case 74:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_74);
            case 75:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_75);
            case 76:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_76);
            case 77:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_77);
            case 78:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_78);
            case 79:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_79);
            case 80:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_80);
            case 81:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_81);
            case 82:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_82);
            case 83:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_83);
            case 84:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_84);
            case 85:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_85);
            case 86:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_86);
            case 87:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_87);
            case 88:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_88);
            case 89:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_89);
            case 90:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_90);
            case 91:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_91);
            case 92:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_92);
            case 93:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_93);
            case 94:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_94);
            case 95:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_95);
            case 96:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_96);
            case 97:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_97);
            case 98:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_98);
            case 99:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_99);
            case 100:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_100);
        }
    }

    private int updateItemPadding() {
        switch (mItemPadding) {
            case 0:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_0);
            case 1:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_1);
            case 2:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_2);
            case 3:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_3);
            case 4:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_4);
            case 5:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_5);
            case 6:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_6);
            case 7:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_7);
            case 8:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_8);
            case 9:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_9);
            case 10:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_10);
            case 11:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_11);
            case 12:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_12);
            case 13:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_13);
            case 14:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_14);
            case 15:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_15);
            case 16:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_16);
            case 17:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_17);
            case 18:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_18);
            case 19:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_19);
            case 20:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_20);
            case 21:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_21);
            case 22:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_22);
            case 23:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_23);
            case 24:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_24);
            case 25:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_25);
            case 26:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_26);
            case 27:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_27);
            case 28:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_28);
            case 29:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_29);
            case 30:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_30);
            case 31:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_31);
            case 32:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_32);
            case 33:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_33);
            case 34:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_34);
            case 35:
            default:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_35);
            case 36:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_36);
            case 37:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_37);
            case 38:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_38);
            case 39:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_39);
            case 40:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_40);
            case 41:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_41);
            case 42:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_42);
            case 43:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_43);
            case 44:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_44);
            case 45:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_45);
            case 46:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_46);
            case 47:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_47);
            case 48:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_48);
            case 49:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_49);
            case 50:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_50);
            case 51:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_51);
            case 52:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_52);
            case 53:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_53);
            case 54:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_54);
            case 55:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_55);
            case 56:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_56);
            case 57:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_57);
            case 58:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_58);
            case 59:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_59);
            case 60:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_60);
            case 61:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_61);
            case 62:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_62);
            case 63:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_63);
            case 64:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_64);
            case 65:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_65);
            case 66:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_66);
            case 67:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_67);
            case 68:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_68);
            case 69:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_69);
            case 70:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_70);
            case 71:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_71);
            case 72:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_72);
            case 73:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_73);
            case 74:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_74);
            case 75:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_75);
            case 76:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_76);
            case 77:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_77);
            case 78:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_78);
            case 79:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_79);
            case 80:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_80);
            case 81:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_81);
            case 82:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_82);
            case 83:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_83);
            case 84:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_84);
            case 85:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_85);
            case 86:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_86);
            case 87:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_87);
            case 88:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_88);
            case 89:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_89);
            case 90:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_90);
            case 91:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_91);
            case 92:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_92);
            case 93:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_93);
            case 94:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_94);
            case 95:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_95);
            case 96:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_96);
            case 97:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_97);
            case 98:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_98);
            case 99:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_99);
            case 100:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_100);
        }
    }

   private void updateOwnerInfoAlignment() {
        if (mOwnerInfo != null) {
            switch (mOwnerInfoAlignment) {
                case 0:
                    mOwnerInfo.setPaddingRelative(updateItemPadding() + 8, 0, 0, 0);
                    mOwnerInfo.setGravity(Gravity.START);
                    break;
                case 1:
                default:
                    mOwnerInfo.setPaddingRelative(0, 0, 0, 0);
                    mOwnerInfo.setGravity(Gravity.CENTER);
                    break;
                case 2:
                    mOwnerInfo.setPaddingRelative(0, 0, updateItemPadding() + 8, 0);
                    mOwnerInfo.setGravity(Gravity.END);
                    break;
            }
       }
   }

   private void updateClockVisbility() {
        if (mClockView != null) {
           if (mShowClock)
               mClockView.setVisibility(View.VISIBLE);
           else
               mClockView.setVisibility(View.GONE);
        }
   }

   private void updateDateVisbility() {
         if (mKeyguardSlice != null) {
            // Dont hide slice view in doze
            mKeyguardSlice.setVisibility(mDarkAmount != 1 ? 
             (mShowDate ? View.VISIBLE : View.GONE) : View.VISIBLE);
         }
   }

   private void updateWeatherAlignment() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                          LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (mWeatherView != null && !mPixelStyle) {
            switch (mWeatherViewAlignment) {
                case 0:
                    params.gravity = Gravity.LEFT;
                    mWeatherView.setPaddingRelative(updateWeatherPadding() + 8, 0, 0, 0);
                    mWeatherView.setLayoutParams(params);
                    break;
                case 1:
                default:
                    params.gravity = Gravity.CENTER;
                    mWeatherView.setPaddingRelative(0, 0, 0, 0);
                    mWeatherView.setLayoutParams(params);
                    break;
                case 2:
                    params.gravity = Gravity.RIGHT;
                    mWeatherView.setPaddingRelative(0, 0, updateWeatherPadding() + 8, 0);
                    mWeatherView.setLayoutParams(params);
                    break;
            }
        }
   }

   private void updateDateStyles() {
         if (mKeyguardSlice == null) return;
         switch (mDateSelection) {
            case 0: // default
            default:
                mKeyguardSlice.setViewBackgroundResource(0);
                mDateVerPadding = 0;
                mDateHorPadding = 0;
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 1: // semi-transparent box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 2: // semi-transparent box (round)
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_border));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 3: // Q-Now Playing background
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.ambient_indication_pill_background));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 4: // accent box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 5: // accent box but just the day
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 6: // accent box transparent
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 7: // accent box transparent but just the day
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 8: // gradient box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 9: // Dark Accent border
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 10: // Dark Gradient border
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 11: // gradient color box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient_color));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 12: // gradient color box 2
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient_color_2));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
        }
    }

    public void updateWeatherBG() {
        if (mWeatherView != null) {
            switch (mWeatherBgSelection) {
                case 0: // default
                default:
                    mWeatherView.setViewBackgroundResource(0);
                    break;
                case 1: // semi-transparent box
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                    break;
                case 2: // semi-transparent box (round)
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_border));
                    break;
                case 3: // Q-Now Playing background
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.ambient_indication_pill_background));
                    break;
                case 4: // accent box
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                    break;
                case 5: // accent box transparent
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                    break;
                case 6: // gradient box
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient));
                    break;
                case 7: // Dark Accent border
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                    break;
                case 8: // Dark Gradient border
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                    break;
                case 9: // Dark Accent border
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                    break;
                case 10: // Dark Gradient border
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                    break;
                case 11: // gradient color box
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient_color));
                    break;
                case 12: // gradient color box 2
                    mWeatherView.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient_color_2));
                    break;
            }
        }
   }

    public void updateWeatherView() {
        if (mWeatherView != null) {
            if (mShowWeather && (!mPixelStyle || mKeyguardSlice.getVisibility() != View.VISIBLE)) {
                mWeatherView.enableUpdates();
                updateWeatherAlignment();
                updateWeatherBG();
            } else if (!mShowWeather || mPixelStyle) {
                mWeatherView.disableUpdates();
            }
        }
    }
}
