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

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.CalendarContract.Events;
import android.provider.Settings;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.util.cm.WeatherController;
import com.android.internal.util.cm.WeatherControllerImpl;
import com.android.internal.logging.MetricsConstants;
import com.android.keyguard.KeyguardStatusView;
import com.android.systemui.BatteryLevelTextView;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.omni.StatusBarHeaderMachine;
import com.android.systemui.qs.QSDragPanel;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.EmergencyListener;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.tuner.TunerService;

import java.text.NumberFormat;

import cyanogenmod.app.StatusBarPanelCustomTile;
import cyanogenmod.providers.CMSettings;
import org.cyanogenmod.internal.logging.CMMetricsLogger;

/**
 * The view to manage the header area in the expanded status bar.
 */
public class StatusBarHeaderView extends RelativeLayout implements View.OnClickListener, View.OnLongClickListener,
        NextAlarmController.NextAlarmChangeCallback, WeatherController.Callback, EmergencyListener,
        StatusBarHeaderMachine.IStatusBarHeaderMachineObserver {
    static final String TAG = "StatusBarHeaderView";

    private boolean mExpanded;
    private boolean mListening;

    private View mHeaderView;
    private ViewGroup mSystemIconsContainer;
    private ViewGroup mWeatherContainer;
    private View mSystemIconsSuperContainer;
    private View mDateGroup;
    private View mClock;
    private TextView mTime;
    private TextView mAmPm;
    private MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private TextView mDateCollapsed;
    private TextView mDateExpanded;
    private LinearLayout mSystemIcons;
    private View mSignalCluster;
    private SettingsButton mSettingsButton;
    private View mSettingsContainer;
    private View mQsDetailHeader;
    private TextView mQsDetailHeaderTitle;
    private Switch mQsDetailHeaderSwitch;
    private ImageView mQsDetailHeaderProgress;
    private TextView mEmergencyCallsOnly;
    private BatteryLevelTextView mBatteryLevel;
    private TextView mAlarmStatus;
    private TextView mWeatherLine1, mWeatherLine2;
    private TextView mEditTileDoneText;

    private boolean mShowEmergencyCallsOnly;
    private boolean mAlarmShowing;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private int mCollapsedHeight;
    private int mExpandedHeight;

    private int mMultiUserExpandedMargin;
    private int mMultiUserCollapsedMargin;

    private int mClockMarginBottomExpanded;
    private int mClockMarginBottomCollapsed;
    private int mMultiUserSwitchWidthCollapsed;
    private int mMultiUserSwitchWidthExpanded;

    private int mClockCollapsedSize;
    private int mClockExpandedSize;

    private int mStatusBarHeaderClockFont = FONT_NORMAL;
    private int mStatusBarHeaderWeatherFont = FONT_NORMAL;
    private int mStatusBarHeaderAlarmFont = FONT_NORMAL;
    private int mStatusBarHeaderDateFont = FONT_NORMAL;	
    private int mStatusBarHeaderDetailFont = FONT_NORMAL;	


    // Task manager
    private boolean mShowTaskManager;
    private View mTaskManagerButton;

    protected Vibrator mVibrator;

    /**
     * In collapsed QS, the clock and avatar are scaled down a bit post-layout to allow for a nice
     * transition. These values determine that factor.
     */
    private float mClockCollapsedScaleFactor;
    private float mAvatarCollapsedScaleFactor;

    private ActivityStarter mActivityStarter;
    private BatteryController mBatteryController;
    private NextAlarmController mNextAlarmController;
    private WeatherController mWeatherController;
    private QSDragPanel mQSPanel;

    private final Rect mClipBounds = new Rect();

    private boolean mCaptureValues;
    private boolean mSignalClusterDetached;
    private final LayoutValues mCollapsedValues = new LayoutValues();
    private final LayoutValues mExpandedValues = new LayoutValues();
    private final LayoutValues mCurrentValues = new LayoutValues();

    private float mCurrentT;
    private boolean mShowingDetail;
    private boolean mDetailTransitioning;
    private SettingsObserver mSettingsObserver;
    private boolean mShowWeather;
    private boolean mShowBatteryTextExpanded;

    private QSTile.DetailAdapter mEditingDetailAdapter;
    private boolean mEditing;


    private ImageView mBackgroundImage;
    private Drawable mCurrentBackground;
    private float mLastHeight;
    private UserInfoController mUserInfoController;


    // QS header alpha
    private int mQSHeaderAlpha;

    // Font style
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
    private int mStatusBarHeaderFontStyle = FONT_NORMAL;

    public StatusBarHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderView = findViewById(R.id.header);
        mSystemIconsSuperContainer = findViewById(R.id.system_icons_super_container);
        mSystemIconsContainer = (ViewGroup) findViewById(R.id.system_icons_container);
        mSystemIconsSuperContainer.setOnClickListener(this);
        mSystemIconsSuperContainer.setOnLongClickListener(this);
        mDateGroup = findViewById(R.id.date_group);
        mDateGroup.setOnClickListener(this);
        mDateGroup.setOnLongClickListener(this);
        mClock = findViewById(R.id.clock);
        mClock.setOnClickListener(this);
        mClock.setOnLongClickListener(this);
        mTime = (TextView) findViewById(R.id.time_view);
        mAmPm = (TextView) findViewById(R.id.am_pm_view);
        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserSwitch.setOnLongClickListener(this);
        mMultiUserAvatar = (ImageView) findViewById(R.id.multi_user_avatar);
        mDateCollapsed = (TextView) findViewById(R.id.date_collapsed);
        mDateExpanded = (TextView) findViewById(R.id.date_expanded);
        mSettingsButton = (SettingsButton) findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mSettingsButton.setOnClickListener(this);
        mTaskManagerButton = findViewById(R.id.task_manager_button);
        mTaskManagerButton.setOnLongClickListener(this);
        mQsDetailHeader = findViewById(R.id.qs_detail_header);
        mQsDetailHeader.setAlpha(0);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mQsDetailHeaderTitle = (TextView) mQsDetailHeader.findViewById(android.R.id.title);
        mQsDetailHeaderSwitch = (Switch) mQsDetailHeader.findViewById(android.R.id.toggle);
        mQsDetailHeaderProgress = (ImageView) findViewById(R.id.qs_detail_header_progress);
        mEmergencyCallsOnly = (TextView) findViewById(R.id.header_emergency_calls_only);
        mBatteryLevel = (BatteryLevelTextView) findViewById(R.id.battery_level_text);
        mAlarmStatus = (TextView) findViewById(R.id.alarm_status);
        mAlarmStatus.setOnClickListener(this);
        mSignalCluster = findViewById(R.id.signal_cluster);
        mSystemIcons = (LinearLayout) findViewById(R.id.system_icons);
        mWeatherContainer = (LinearLayout) findViewById(R.id.weather_container);
        mWeatherContainer.setOnClickListener(this);
        mWeatherContainer.setOnLongClickListener(this);
        mWeatherLine1 = (TextView) findViewById(R.id.weather_line_1);
        mWeatherLine2 = (TextView) findViewById(R.id.weather_line_2);
        mEditTileDoneText = (TextView) findViewById(R.id.done);
        mSettingsObserver = new SettingsObserver(new Handler());
        mBackgroundImage = (ImageView) findViewById(R.id.background_image);
        loadDimens();
        updateVisibilities();
        updateClockScale();
        updateAvatarScale();
	hidepanelItems();
        setQSHeaderAlpha();
        setStatusBarClockFontStyle(mStatusBarHeaderClockFont);
	setStatusBarWeatherFontStyle(mStatusBarHeaderWeatherFont);
	setStatusBarHeaderFontStyle(mStatusBarHeaderFontStyle);
	setStatusBarAlarmFontStyle(mStatusBarHeaderAlarmFont);
	setStatusBarDateFontStyle(mStatusBarHeaderDateFont);
        setStatusBarDetailFontStyle(mStatusBarHeaderDetailFont);
        addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) != (oldRight - oldLeft)) {
                    // width changed, update clipping
                    setClipping(getHeight());
                }
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                mTime.setPivotX(rtl ? mTime.getWidth() : 0);
                mTime.setPivotY(mTime.getBaseline());
                updateAmPmTranslation();
            }
        });
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(mClipBounds);
            }
        });
        requestCaptureValues();

        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        Drawable d = getBackground();
        if (d instanceof RippleDrawable) {
            ((RippleDrawable) d).setForceSoftware(true);
        }
        d = mSettingsButton.getBackground();
        if (d instanceof RippleDrawable) {
            ((RippleDrawable) d).setForceSoftware(true);
        }
        d = mSystemIconsSuperContainer.getBackground();
        if (d instanceof RippleDrawable) {
            ((RippleDrawable) d).setForceSoftware(true);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mCaptureValues) {
            if (mExpanded) {
                captureLayoutValues(mExpandedValues);
            } else {
                captureLayoutValues(mCollapsedValues);
            }
            mCaptureValues = false;
            updateLayoutValues(mCurrentT);
        }
        mAlarmStatus.setX(mDateGroup.getLeft() + mDateCollapsed.getRight());
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mEmergencyCallsOnly,
                R.dimen.qs_emergency_calls_only_text_size);
        FontSizeUtils.updateFontSize(mDateCollapsed, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(mDateExpanded, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(mAlarmStatus, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(this, android.R.id.title, R.dimen.qs_detail_header_text_size);
        FontSizeUtils.updateFontSize(this, android.R.id.toggle, R.dimen.qs_detail_header_text_size);
        FontSizeUtils.updateFontSize(mAmPm, R.dimen.qs_time_collapsed_size);
        FontSizeUtils.updateFontSize(this, R.id.empty_time_view, R.dimen.qs_time_expanded_size);

        mEmergencyCallsOnly.setText(com.android.internal.R.string.emergency_calls_only);

        mClockCollapsedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_collapsed_size);
        mClockExpandedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_expanded_size);
        mClockCollapsedScaleFactor = (float) mClockCollapsedSize / (float) mClockExpandedSize;
        updateClockScale();
        updateClockCollapsedMargin();
    }


    public void vibrateheader(int duration) {
        if (mVibrator != null) {
            if (mVibrator.hasVibrator()) { mVibrator.vibrate(duration); }
        	}
	}

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mUserInfoController != null) {
            mUserInfoController.removeListener(mUserInfoChangedListener);
        }
        setListening(false);
    }

    private void updateClockCollapsedMargin() {
        Resources res = getResources();
        int padding = res.getDimensionPixelSize(R.dimen.clock_collapsed_bottom_margin);
        int largePadding = res.getDimensionPixelSize(
                R.dimen.clock_collapsed_bottom_margin_large_text);
        float largeFactor = (MathUtils.constrain(getResources().getConfiguration().fontScale, 1.0f,
                FontSizeUtils.LARGE_TEXT_SCALE) - 1f) / (FontSizeUtils.LARGE_TEXT_SCALE - 1f);
        mClockMarginBottomCollapsed = Math.round((1 - largeFactor) * padding + largeFactor * largePadding);
        requestLayout();
    }

    private void requestCaptureValues() {
        mCaptureValues = true;
        requestLayout();
    }

    private void loadDimens() {
        mCollapsedHeight = getResources().getDimensionPixelSize(R.dimen.status_bar_header_height);
        mExpandedHeight = getResources().getDimensionPixelSize(
                R.dimen.status_bar_header_height_expanded);
        mMultiUserExpandedMargin =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_expanded_margin);
        mMultiUserCollapsedMargin =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_collapsed_margin);
        mClockMarginBottomExpanded =
                getResources().getDimensionPixelSize(R.dimen.clock_expanded_bottom_margin);
        updateClockCollapsedMargin();
        mMultiUserSwitchWidthCollapsed =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_width_collapsed);
        mMultiUserSwitchWidthExpanded =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_width_expanded);
        mAvatarCollapsedScaleFactor =
                getResources().getDimensionPixelSize(R.dimen.multi_user_avatar_collapsed_size)
                / (float) mMultiUserAvatar.getLayoutParams().width;
        mClockCollapsedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_collapsed_size);
        mClockExpandedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_expanded_size);
        mClockCollapsedScaleFactor = (float) mClockCollapsedSize / (float) mClockExpandedSize;
    }

    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
        if (mMultiUserSwitch != null) {
            mMultiUserSwitch.setActivityStarter(activityStarter);
        }
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        ((BatteryMeterView) findViewById(R.id.battery)).setBatteryController(batteryController);
        mBatteryLevel.setBatteryController(batteryController);
    }

    public void setNextAlarmController(NextAlarmController nextAlarmController) {
        mNextAlarmController = nextAlarmController;
    }

    public void setWeatherController(WeatherController weatherController) {
        mWeatherController = weatherController;
    }

    public int getCollapsedHeight() {
        return mCollapsedHeight;
    }

    public int getExpandedHeight() {
        return mExpandedHeight;
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;
        updateListeners();
    }

    public void setExpanded(boolean expanded) {
        boolean changed = expanded != mExpanded;
        mExpanded = expanded;
        if (changed) {
            if (mShowingDetail && !expanded) {
                mQsPanelCallback.onShowingDetail(null);
            }
            updateEverything();
        }
    }

    public void updateEverything() {
        updateHeights();
        updateVisibilities();
        updateSystemIconsLayoutParams();
        updateClickTargets();
        updateMultiUserSwitch();
        updateClockScale();
        updateAvatarScale();
        updateClockLp();
        requestCaptureValues();
	hidepanelItems();
    }

    void setTaskManagerEnabled(boolean enabled) {
        mShowTaskManager = enabled;
        updateVisibilities();
        updateSystemIconsLayoutParams();
        updateMultiUserSwitch();
        requestCaptureValues();
    }

    private void updateHeights() {
        int height = mExpanded ? mExpandedHeight : mCollapsedHeight;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            setLayoutParams(lp);
        }
    }

    private void updateVisibilities() {
        mDateCollapsed.setVisibility(mExpanded && mAlarmShowing ? View.VISIBLE : View.INVISIBLE);
        mDateExpanded.setVisibility(mExpanded && mAlarmShowing ? View.INVISIBLE : View.VISIBLE);
        mAlarmStatus.setVisibility(mExpanded && mAlarmShowing ? View.VISIBLE : View.INVISIBLE);
        mSettingsContainer.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
        mWeatherContainer.setVisibility(mExpanded && mShowWeather ? View.VISIBLE : View.GONE);
        mTaskManagerButton.setVisibility(mExpanded && mShowTaskManager ? View.VISIBLE : View.GONE);
        mQsDetailHeader.setVisibility(mExpanded && mShowingDetail ? View.VISIBLE : View.GONE);
        if (mSignalCluster != null) {
            updateSignalClusterDetachment();
        }
        mEmergencyCallsOnly.setVisibility(mExpanded && mShowEmergencyCallsOnly ? VISIBLE : GONE);
        mBatteryLevel.setForceShown(mExpanded && mShowBatteryTextExpanded);
        mBatteryLevel.setVisibility(View.VISIBLE);
        mSettingsContainer.findViewById(R.id.tuner_icon).setVisibility(
                TunerService.isTunerEnabled(mContext) ? View.VISIBLE : View.INVISIBLE);
    }

	public void hidepanelItems() {
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HIDE_PANEL_CLOCK, 1) == 1) {
       	    mTime = (TextView) findViewById(R.id.time_view);
            mTime.setVisibility(View.VISIBLE);
	    
        } else {
        mTime = (TextView) findViewById(R.id.time_view);
        mAmPm = (TextView) findViewById(R.id.am_pm_view);
            mTime.setVisibility(View.GONE);
	    mAmPm.setVisibility(View.GONE);
        	}
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HIDE_PANEL_DATE, 1) == 1) {
        mDateGroup = findViewById(R.id.date_group);
	mDateGroup .setVisibility(View.VISIBLE);
        } else {
         mDateGroup = findViewById(R.id.date_group);
	 mDateGroup.setVisibility(View.INVISIBLE);
        }
 	if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HIDE_PANEL_CLOCKVALUE, 1) == 1) {
	mAmPm = (TextView) findViewById(R.id.am_pm_view);
	mAmPm.setVisibility(View.VISIBLE);
	} else {
        mAmPm = (TextView) findViewById(R.id.am_pm_view);
	    mAmPm.setVisibility(View.GONE);
		}
	if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HIDE_PANEL_BATTERY, 1) == 1) {
	mBatteryLevel = (BatteryLevelTextView) findViewById(R.id.battery_level_text);
	mBatteryLevel.setVisibility(View.VISIBLE);
	} else {
       	mBatteryLevel = (BatteryLevelTextView) findViewById(R.id.battery_level_text);
	    mBatteryLevel.setVisibility(View.INVISIBLE);
		}
	if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HIDE_PANEL_ICONS, 1) == 1) {
	mSystemIconsContainer = (ViewGroup) findViewById(R.id.system_icons_container);
	mSystemIconsContainer.setVisibility(View.VISIBLE);
        } else {
       	mSystemIconsContainer = (ViewGroup) findViewById(R.id.system_icons_container);
	mSystemIconsContainer.setVisibility(View.INVISIBLE);
		}
	if (Settings.System.getInt(mContext.getContentResolver(),
               Settings.System.HIDE_SETTINGS_ICON, 1) == 1) {
	mSettingsButton = (SettingsButton) findViewById(R.id.settings_button);
	mSettingsButton.setVisibility(View.VISIBLE);
	} else {	
	mSettingsButton = (SettingsButton) findViewById(R.id.settings_button);
	mSettingsButton.setVisibility(View.INVISIBLE);
        	}
	if (Settings.System.getInt(mContext.getContentResolver(),
              Settings.System.HIDE_USER_ICON, 1) == 1) {
	mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
	mMultiUserSwitch.setVisibility(View.VISIBLE);
	} else {
	mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
	mMultiUserSwitch.setVisibility(View.INVISIBLE);
  	 	}
        if (Settings.System.getInt(mContext.getContentResolver(),
              Settings.System.HIDE_PANEL_ALARM, 1) == 1)  {
	mAlarmStatus = (TextView) findViewById(R.id.alarm_status);
	mAlarmStatus.setVisibility(View.GONE);
  	 	}
	}

    private void updateSignalClusterDetachment() {
        boolean detached = mExpanded;
        if (detached != mSignalClusterDetached) {
            if (detached) {
                getOverlay().add(mSignalCluster);
            } else {
                reattachSignalCluster();
            }
        }
        mSignalClusterDetached = detached;
    }

    private void reattachSignalCluster() {
        getOverlay().remove(mSignalCluster);
        mSystemIcons.addView(mSignalCluster, 1);
    }

    private void updateSystemIconsLayoutParams() {
        RelativeLayout.LayoutParams lp = (LayoutParams) mSystemIconsSuperContainer
                .getLayoutParams();
        int rule = mExpanded
                ? mSettingsContainer.getId()
                : mMultiUserSwitch.getId();
        int taskManager = mShowTaskManager && mExpanded
                ? mTaskManagerButton.getId() : rule;
        if (taskManager != lp.getRules()[RelativeLayout.START_OF]) {
            lp.addRule(RelativeLayout.START_OF, taskManager);
            mSystemIconsSuperContainer.setLayoutParams(lp);
        }
    }

    private void updateListeners() {
        if (mListening) {
            mSettingsObserver.observe();
            mNextAlarmController.addStateChangedCallback(this);
            mWeatherController.addCallback(this);
        } else {
            mNextAlarmController.removeStateChangedCallback(this);
            mWeatherController.removeCallback(this);
            mSettingsObserver.unobserve();
        }
    }

    private void updateAvatarScale() {
        if (mExpanded) {
            mMultiUserAvatar.setScaleX(1f);
            mMultiUserAvatar.setScaleY(1f);
        } else {
            mMultiUserAvatar.setScaleX(mAvatarCollapsedScaleFactor);
            mMultiUserAvatar.setScaleY(mAvatarCollapsedScaleFactor);
        }
    }

    private void updateClockScale() {
        mTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, mExpanded
                ? mClockExpandedSize
                : mClockCollapsedSize);
        mTime.setScaleX(1f);
        mTime.setScaleY(1f);
        updateAmPmTranslation();
    }

    private void updateAmPmTranslation() {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        mAmPm.setTranslationX((rtl ? 1 : -1) * mTime.getWidth() * (1 - mTime.getScaleX()));
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        if (nextAlarm != null) {
            mAlarmStatus.setText(KeyguardStatusView.formatNextAlarm(getContext(), nextAlarm));
        }
        mAlarmShowing = nextAlarm != null;
        updateEverything();
        requestCaptureValues();
    }

    @Override
    public void onWeatherChanged(WeatherController.WeatherInfo info) {
        if (info.temp == null || info.condition == null) {
            mWeatherLine1.setText(mContext.getString(R.string.weather_info_not_available));
            mWeatherLine2.setText(null);
        } else {
            mWeatherLine1.setText(mContext.getString(
                    R.string.status_bar_expanded_header_weather_format,
                    info.temp,
                    info.condition));
            mWeatherLine2.setText(info.city);
        }

    }

    private void updateClickTargets() {
        mMultiUserSwitch.setClickable(mExpanded);
        mMultiUserSwitch.setFocusable(mExpanded);
        mSystemIconsSuperContainer.setClickable(mExpanded);
        mSystemIconsSuperContainer.setFocusable(mExpanded);
        mAlarmStatus.setClickable(mNextAlarm != null && mNextAlarm.getShowIntent() != null);
    }

    private void updateClockLp() {
        int marginBottom = mExpanded
                ? mClockMarginBottomExpanded
                : mClockMarginBottomCollapsed;
        LayoutParams lp = (LayoutParams) mDateGroup.getLayoutParams();
        if (marginBottom != lp.bottomMargin) {
            lp.bottomMargin = marginBottom;
            mDateGroup.setLayoutParams(lp);
        }
    }

    private void updateMultiUserSwitch() {
        int marginEnd;
        int width;
        if (mExpanded) {
            marginEnd = mMultiUserExpandedMargin;
            width = mMultiUserSwitchWidthExpanded;
        } else {
            marginEnd = mMultiUserCollapsedMargin;
            width = mMultiUserSwitchWidthCollapsed;
        }
        MarginLayoutParams lp = (MarginLayoutParams) mMultiUserSwitch.getLayoutParams();
        if (marginEnd != lp.getMarginEnd() || lp.width != width) {
            lp.setMarginEnd(marginEnd);
            lp.width = width;
            mMultiUserSwitch.setLayoutParams(lp);
        }
    }

    public void setExpansion(float t) {
        if (!mExpanded) {
            t = 0f;
        }
        mCurrentT = t;
        float height = mCollapsedHeight + t * (mExpandedHeight - mCollapsedHeight);
        if (height != mLastHeight) {
            if (height < mCollapsedHeight) {
                height = mCollapsedHeight;
            }
            if (height > mExpandedHeight) {
                height = mExpandedHeight;
            }
            final float heightFinal = height;
            setClipping(heightFinal);

            post(new Runnable() {
                 public void run() {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBackgroundImage.getLayoutParams(); 
                    params.height = (int)heightFinal;
                    mBackgroundImage.setLayoutParams(params);
                }
            });

            updateLayoutValues(t);
            mLastHeight = heightFinal;
        }
    }

    private void updateLayoutValues(float t) {
        if (mCaptureValues) {
            return;
        }
        mCurrentValues.interpoloate(mCollapsedValues, mExpandedValues, t);
        applyLayoutValues(mCurrentValues);
    }

    private void setClipping(float height) {
        mClipBounds.set(getPaddingLeft(), 0, getWidth() - getPaddingRight(), (int) height);
        setClipBounds(mClipBounds);
        invalidateOutline();
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
        if (mMultiUserSwitch != null) {
            mMultiUserSwitch.setUserInfoController(mUserInfoController);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mSettingsButton) {
            if (mSettingsButton.isTunerClick()) {
                mSettingsButton.consumeClick();
                mQSPanel.getHost().setEditing(!mQSPanel.getHost().isEditing());
            } else {
		startSettingsActivity();
		}
        } else if (v == mSystemIconsSuperContainer) {
            startBatteryActivity();
        } else if (v == mAlarmStatus && mNextAlarm != null) {
            PendingIntent showIntent = mNextAlarm.getShowIntent();
            if (showIntent != null) {
                mActivityStarter.startPendingIntentDismissingKeyguard(showIntent);
            }
        } else if (v == mClock) {
            startClockActivity();
        } else if (v == mDateGroup) {
            startDateActivity();
        } else if (v == mWeatherContainer) {
            startForecastActivity();
        }
    }

    @Override
    public boolean onLongClick(View v) {
	 if (v == mSystemIconsSuperContainer) {
            startBatteryLongClickActivity();
        } else if (v == mClock) {
            startClockLongClickActivity();
        } else if (v == mDateGroup) {
            startDateLongClickActivity();
        } else if (v == mWeatherContainer) {
            startForecastLongClickActivity();
        } else if (v == mMultiUserSwitch) {
            startUserLongClickActivity();       
        } else if (v == mTaskManagerButton) {
            startTaskManagerLongClickActivity();
        }
	vibrateheader(20);	
        return false;
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    private void startBatteryActivity() {
        mActivityStarter.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                true /* dismissShade */);
    }

    private void startBatteryLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$BatterySaverSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startUserLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$UserSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startClockActivity() {
        mActivityStarter.startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS),
                true /* dismissShade */);
    }

    private void startClockLongClickActivity() {
        mActivityStarter.startActivity(new Intent(AlarmClock.ACTION_SET_ALARM),
                true /* dismissShade */);
    }

    private void startDateActivity() {
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, System.currentTimeMillis());
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startDateLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(Events.CONTENT_URI);
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startForecastActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(WeatherControllerImpl.COMPONENT_WEATHER_FORECAST);
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startForecastLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.cyanogenmod.lockclock",
            "com.cyanogenmod.lockclock.preference.Preferences");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }


    private void startTaskManagerLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$RunningServicesActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

     public void setQSPanel(QSDragPanel qsp) {
        mQSPanel = qsp;
        if (mQSPanel != null) {
            mQSPanel.setCallback(mQsPanelCallback);
        }
        mMultiUserSwitch.setQsPanel(qsp);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public void setEmergencyCallsOnly(boolean show) {
        boolean changed = show != mShowEmergencyCallsOnly;
        if (changed) {
            mShowEmergencyCallsOnly = show;
            if (mExpanded) {
                updateEverything();
                requestCaptureValues();
            }
        }
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // We don't want that everything lights up when we click on the header, so block the request
        // here.
    }

    private void captureLayoutValues(LayoutValues target) {
        target.timeScale = mExpanded ? 1f : mClockCollapsedScaleFactor;
        target.clockY = mClock.getBottom();
        target.dateY = mDateGroup.getTop();
        target.emergencyCallsOnlyAlpha = getAlphaForVisibility(mEmergencyCallsOnly);
        target.alarmStatusAlpha = getAlphaForVisibility(mAlarmStatus);
        target.dateCollapsedAlpha = getAlphaForVisibility(mDateCollapsed);
        target.dateExpandedAlpha = getAlphaForVisibility(mDateExpanded);
        target.avatarScale = mMultiUserAvatar.getScaleX();
        target.avatarX = mMultiUserSwitch.getLeft() + mMultiUserAvatar.getLeft();
        target.avatarY = mMultiUserSwitch.getTop() + mMultiUserAvatar.getTop();
        target.weatherY = mClock.getBottom() - mWeatherLine1.getHeight();
        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            target.batteryX = mSystemIconsSuperContainer.getLeft()
                    + mSystemIconsContainer.getRight();
        } else {
            target.batteryX = mSystemIconsSuperContainer.getLeft()
                    + mSystemIconsContainer.getLeft();
        }
        target.batteryY = mSystemIconsSuperContainer.getTop() + mSystemIconsContainer.getTop();
        target.batteryLevelAlpha = getAlphaForVisibility(mBatteryLevel);
        target.settingsAlpha = getAlphaForVisibility(mSettingsContainer);
        target.settingsTranslation = mExpanded
                ? 0
                : mMultiUserSwitch.getLeft() - mSettingsContainer.getLeft();
        target.taskManagerAlpha = getAlphaForVisibility(mTaskManagerButton);
	target.taskManagerTranslation = mExpanded
                ? 0
                : mSettingsButton.getLeft() - mTaskManagerButton.getLeft();
        target.signalClusterAlpha = mSignalClusterDetached ? 0f : 1f;
        target.settingsRotation = !mExpanded ? 90f : 0f;
    }

    private float getAlphaForVisibility(View v) {
        return v == null || v.getVisibility() == View.VISIBLE ? 1f : 0f;
    }

    private void applyAlpha(View v, float alpha) {
        if (v == null || v.getVisibility() == View.GONE) {
            return;
        }
        if (alpha == 0f) {
            v.setVisibility(View.INVISIBLE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setAlpha(alpha);
        }
    }

    private void applyLayoutValues(LayoutValues values) {
        mTime.setScaleX(values.timeScale);
        mTime.setScaleY(values.timeScale);
        mClock.setY(values.clockY - mClock.getHeight());
        mDateGroup.setY(values.dateY);
        mWeatherContainer.setY(values.weatherY);
        mAlarmStatus.setY(values.dateY - mAlarmStatus.getPaddingTop());
        mMultiUserAvatar.setScaleX(values.avatarScale);
        mMultiUserAvatar.setScaleY(values.avatarScale);
        mMultiUserAvatar.setX(values.avatarX - mMultiUserSwitch.getLeft());
        mMultiUserAvatar.setY(values.avatarY - mMultiUserSwitch.getTop());
        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            mSystemIconsSuperContainer.setX(values.batteryX - mSystemIconsContainer.getRight());
        } else {
            mSystemIconsSuperContainer.setX(values.batteryX - mSystemIconsContainer.getLeft());
        }
        mSystemIconsSuperContainer.setY(values.batteryY - mSystemIconsContainer.getTop());
        if (mSignalCluster != null && mExpanded) {
            if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
                mSignalCluster.setX(mSystemIconsSuperContainer.getX()
                        - mSignalCluster.getWidth());
            } else {
                mSignalCluster.setX(mSystemIconsSuperContainer.getX()
                        + mSystemIconsSuperContainer.getWidth());
            }
            mSignalCluster.setY(
                    mSystemIconsSuperContainer.getY() + mSystemIconsSuperContainer.getHeight()/2
                            - mSignalCluster.getHeight()/2);
        } else if (mSignalCluster != null) {
            mSignalCluster.setTranslationX(0f);
            mSignalCluster.setTranslationY(0f);
        }
        if (!mSettingsButton.isAnimating()) {
            mSettingsContainer.setTranslationY(mSystemIconsSuperContainer.getTranslationY());
            mSettingsContainer.setTranslationX(values.settingsTranslation);
            mSettingsButton.setRotation(values.settingsRotation);
	}
            mTaskManagerButton.setTranslationY(mSystemIconsSuperContainer.getTranslationY());
            mTaskManagerButton.setTranslationX(values.settingsTranslation);
            mTaskManagerButton.setRotation(values.settingsRotation);
        applyAlpha(mEmergencyCallsOnly, values.emergencyCallsOnlyAlpha);
        if (!mShowingDetail && !mDetailTransitioning) {
            // Otherwise it needs to stay invisible
            applyAlpha(mAlarmStatus, values.alarmStatusAlpha);
        }
        applyAlpha(mDateCollapsed, values.dateCollapsedAlpha);
        applyAlpha(mDateExpanded, values.dateExpandedAlpha);
        applyAlpha(mBatteryLevel, values.batteryLevelAlpha);
        applyAlpha(mSettingsContainer, values.settingsAlpha);
        applyAlpha(mTaskManagerButton, values.settingsAlpha);
        applyAlpha(mWeatherLine1, values.settingsAlpha);
        applyAlpha(mWeatherLine2, values.settingsAlpha);
        applyAlpha(mSignalCluster, values.signalClusterAlpha);
        if (!mExpanded) {
            mTime.setScaleX(1f);
            mTime.setScaleY(1f);
        }
        updateAmPmTranslation();
    }

    public void setEditing(boolean editing) {
        mEditing = editing;
        if (editing && mEditingDetailAdapter == null) {
            mEditingDetailAdapter = new QSTile.DetailAdapter() {
                @Override
                public int getTitle() {
                    return R.string.quick_settings_edit_label;
                }

                @Override
                public Boolean getToggleState() {
                    return null;
                }

                @Override
                public View createDetailView(Context context, View convertView, ViewGroup parent) {
                    return null;
                }

                @Override
                public Intent getSettingsIntent() {
                    return null;
                }

                @Override
                public StatusBarPanelCustomTile getCustomTile() {
                    return null;
                }

                @Override
                public void setToggleState(boolean state) {

                }

                @Override
                public int getMetricsCategory() {
                    return CMMetricsLogger.DONT_LOG;
                }
            };
        }
        mQsPanelCallback.onShowingDetail(mEditing ? mEditingDetailAdapter : null);
        updateEverything();
    }

    /**
     * Captures all layout values (position, visibility) for a certain state. This is used for
     * animations.
     */
    private static final class LayoutValues {

        float dateExpandedAlpha;
        float dateCollapsedAlpha;
        float emergencyCallsOnlyAlpha;
        float alarmStatusAlpha;
        float timeScale = 1f;
        float clockY;
        float dateY;
        float avatarScale;
        float avatarX;
        float avatarY;
        float batteryX;
        float batteryY;
        float batteryLevelAlpha;
        float batteryLevelExpandedAlpha;
        float taskManagerAlpha;
        float taskManagerTranslation;
        float settingsAlpha;
        float settingsTranslation;
        float signalClusterAlpha;
        float settingsRotation;
        float weatherY;

        public void interpoloate(LayoutValues v1, LayoutValues v2, float t) {
            timeScale = v1.timeScale * (1 - t) + v2.timeScale * t;
            clockY = v1.clockY * (1 - t) + v2.clockY * t;
            dateY = v1.dateY * (1 - t) + v2.dateY * t;
            avatarScale = v1.avatarScale * (1 - t) + v2.avatarScale * t;
            avatarX = v1.avatarX * (1 - t) + v2.avatarX * t;
            avatarY = v1.avatarY * (1 - t) + v2.avatarY * t;
            batteryX = v1.batteryX * (1 - t) + v2.batteryX * t;
            batteryY = v1.batteryY * (1 - t) + v2.batteryY * t;
            taskManagerTranslation =
                    v1.taskManagerTranslation * (1 - t) + v2.taskManagerTranslation * t;
            settingsTranslation = v1.settingsTranslation * (1 - t) + v2.settingsTranslation * t;
            weatherY = v1.weatherY * (1 - t) + v2.weatherY * t;

            float t1 = Math.max(0, t - 0.5f) * 2;
            settingsRotation = v1.settingsRotation * (1 - t1) + v2.settingsRotation * t1;
            emergencyCallsOnlyAlpha =
                    v1.emergencyCallsOnlyAlpha * (1 - t1) + v2.emergencyCallsOnlyAlpha * t1;

            float t2 = Math.min(1, 2 * t);
            signalClusterAlpha = v1.signalClusterAlpha * (1 - t2) + v2.signalClusterAlpha * t2;

            float t3 = Math.max(0, t - 0.7f) / 0.3f;
            batteryLevelAlpha = v1.batteryLevelAlpha * (1 - t3) + v2.batteryLevelAlpha * t3;
            taskManagerAlpha = v1.taskManagerAlpha * (1 - t3) + v2.taskManagerAlpha * t3;
            settingsAlpha = v1.settingsAlpha * (1 - t3) + v2.settingsAlpha * t3;
            dateExpandedAlpha = v1.dateExpandedAlpha * (1 - t3) + v2.dateExpandedAlpha * t3;
            dateCollapsedAlpha = v1.dateCollapsedAlpha * (1 - t3) + v2.dateCollapsedAlpha * t3;
            alarmStatusAlpha = v1.alarmStatusAlpha * (1 - t3) + v2.alarmStatusAlpha * t3;
            batteryLevelExpandedAlpha =
                    v1.batteryLevelExpandedAlpha * (1 - t3) + v2.batteryLevelExpandedAlpha * t3;
        }
    }

    private final QSPanel.Callback mQsPanelCallback = new QSPanel.Callback() {
        private boolean mScanState;

        @Override
        public void onToggleStateChanged(final boolean state) {
            post(new Runnable() {
                @Override
                public void run() {
                    handleToggleStateChanged(state);
                }
            });
        }

        @Override
        public void onShowingDetail(final QSTile.DetailAdapter detail) {
            mDetailTransitioning = true;
            post(new Runnable() {
                @Override
                public void run() {
                    handleShowingDetail(mEditing && detail == null ? mEditingDetailAdapter : detail);
                }
            });
        }

        @Override
        public void onScanStateChanged(final boolean state) {
            post(new Runnable() {
                @Override
                public void run() {
                    handleScanStateChanged(state);
                }
            });
        }

        private void handleToggleStateChanged(boolean state) {
            mQsDetailHeaderSwitch.setChecked(state);
        }

        private void handleScanStateChanged(boolean state) {
            if (mScanState == state) return;
            mScanState = state;
            final Animatable anim = (Animatable) mQsDetailHeaderProgress.getDrawable();
            if (state) {
                mQsDetailHeaderProgress.animate().alpha(1f);
                anim.start();
            } else {
                mQsDetailHeaderProgress.animate().alpha(0f);
                anim.stop();
            }
        }

        private void handleShowingDetail(final QSTile.DetailAdapter detail) {
            final boolean showingDetail = detail != null;
            transition(mClock, !showingDetail);
            transition(mDateGroup, !showingDetail);
            if (mShowWeather) {
                transition(mWeatherContainer, !showingDetail);
            }
            if (mAlarmShowing) {
                transition(mAlarmStatus, !showingDetail && !mDetailTransitioning);
            }
            transition(mQsDetailHeader, showingDetail);
            mShowingDetail = showingDetail;
            if (showingDetail) {
                mQsDetailHeaderTitle.setText(detail.getTitle());
                final Boolean toggleState = detail.getToggleState();
                if (detail.getTitle() == R.string.quick_settings_edit_label) {
                    mEditTileDoneText.setVisibility(View.VISIBLE);
                    mQsDetailHeaderSwitch.setVisibility(INVISIBLE);
                    mQsDetailHeader.setClickable(true);
                    mQsDetailHeader.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mQSPanel.getHost().setEditing(false);
                        }
                    });
                } else if (toggleState == null) {
                    mQsDetailHeaderSwitch.setVisibility(INVISIBLE);
                    mEditTileDoneText.setVisibility(View.GONE);
                    mQsDetailHeader.setClickable(false);
                } else {
                    mEditTileDoneText.setVisibility(View.GONE);
                    mQsDetailHeaderSwitch.setVisibility(VISIBLE);
                    mQsDetailHeaderSwitch.setChecked(toggleState);
                    mQsDetailHeader.setClickable(true);
                    mQsDetailHeader.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean checked = !mQsDetailHeaderSwitch.isChecked();
                            mQsDetailHeaderSwitch.setChecked(checked);
                            detail.setToggleState(checked);
                        }
                    });
                }
            } else {
                mQsDetailHeader.setClickable(false);
            }
        }

        private void transition(final View v, final boolean in) {
            if (in) {
                v.bringToFront();
                v.setVisibility(VISIBLE);
            }
            if (v.hasOverlappingRendering()) {
                v.animate().withLayer();
            }
            v.animate()
                    .alpha(in ? 1 : 0)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (!in) {
                                v.setVisibility(INVISIBLE);
                            }
                            mDetailTransitioning = false;
                        }
                    })
                    .start();
        }
    };

    class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.STATUS_BAR_SHOW_WEATHER), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.STATUS_BAR_BATTERY_STYLE), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_TRANSPARENT_HEADER), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_HEADER_FONT_STYLE), false, this, UserHandle.USER_ALL);
  resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_PANEL_CLOCK), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_PANEL_DATE), false, this, UserHandle.USER_ALL);
  resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_PANEL_CLOCKVALUE), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_PANEL_BATTERY), false, this, UserHandle.USER_ALL);
  resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_PANEL_ICONS), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_SETTINGS_ICON), false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_USER_ICON), false, this, UserHandle.USER_ALL);
	            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HEADER_CLOCK_FONT_STYLE), false, this, UserHandle.USER_ALL);
  resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_PANEL_ALARM), false, this, UserHandle.USER_ALL);
  resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HEADER_ALARM_FONT_STYLE), false, this, UserHandle.USER_ALL);
  resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HEADER_DETAIL_FONT_STYLE), false, this, UserHandle.USER_ALL);
 resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HEADER_ALARM_FONT_STYLE), false, this, UserHandle.USER_ALL);
	
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void update() {

            ContentResolver resolver = mContext.getContentResolver();
            int currentUserId = ActivityManager.getCurrentUser();
            int batteryStyle = CMSettings.System.getIntForUser(resolver,
                    CMSettings.System.STATUS_BAR_BATTERY_STYLE, 0, currentUserId);
            boolean showExpandedBatteryPercentage = CMSettings.System.getIntForUser(resolver,
                    CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, currentUserId) == 0;

            switch (batteryStyle) {
                case 4: //BATTERY_METER_GONE
                case 6: //BATTERY_METER_TEXT
                    showExpandedBatteryPercentage = false;
                    break;
                default:
                    break;
            }

            mShowBatteryTextExpanded = showExpandedBatteryPercentage;
            mShowWeather = CMSettings.System.getInt(
                    resolver, CMSettings.System.STATUS_BAR_SHOW_WEATHER, 1) == 1;

            mQSHeaderAlpha = Settings.System.getInt(
                    resolver, Settings.System.QS_TRANSPARENT_HEADER, 255);
            setQSHeaderAlpha();

            mStatusBarHeaderFontStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_HEADER_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);
	    mStatusBarHeaderWeatherFont = Settings.System.getIntForUser(resolver,
                Settings.System.HEADER_WEATHER_FONT_STYLE , FONT_NORMAL,
                UserHandle.USER_CURRENT);
	    mStatusBarHeaderClockFont =Settings.System.getIntForUser(resolver,
                Settings.System.HEADER_CLOCK_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);
	    mStatusBarHeaderAlarmFont =Settings.System.getIntForUser(resolver,
                Settings.System.HEADER_ALARM_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);
	    mStatusBarHeaderDateFont =Settings.System.getIntForUser(resolver,
                Settings.System.HEADER_DATE_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);
	    mStatusBarHeaderDetailFont =Settings.System.getIntForUser(resolver,
                Settings.System.HEADER_DETAIL_FONT_STYLE, FONT_NORMAL,
                UserHandle.USER_CURRENT);

	    setStatusBarHeaderFontStyle	(mStatusBarHeaderFontStyle);
            setStatusBarWeatherFontStyle(mStatusBarHeaderWeatherFont);
	    setStatusBarClockFontStyle(mStatusBarHeaderClockFont);
	    setStatusBarAlarmFontStyle(mStatusBarHeaderAlarmFont);
	    setStatusBarDateFontStyle(mStatusBarHeaderDateFont);
            setStatusBarDetailFontStyle(mStatusBarHeaderDetailFont);	

            updateVisibilities();
            requestCaptureValues();
        }
    }

    private void doUpdateStatusBarCustomHeader(final Drawable next, final boolean force) {
        if (next != null) {
            if (next != mCurrentBackground) {
                Log.i(TAG, "Updating status bar header background");
                mBackgroundImage.setVisibility(View.VISIBLE);
                setNotificationPanelHeaderBackground(next, force);
                mCurrentBackground = next;
            }
        } else {
            mCurrentBackground = null;
            mBackgroundImage.setVisibility(View.GONE);
        }
    }

    private void setNotificationPanelHeaderBackground(final Drawable dw, final boolean force) {
        if (mBackgroundImage.getDrawable() != null && !force) {
            Drawable[] arrayDrawable = new Drawable[2];
            arrayDrawable[0] = mBackgroundImage.getDrawable();
            arrayDrawable[1] = dw;

            TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            mBackgroundImage.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(1000);
        } else {
            mBackgroundImage.setImageDrawable(dw);
        }
    }

    @Override
    public void updateHeader(final Drawable headerImage, final boolean force) {
        post(new Runnable() {
             public void run() {
                // TODO we dont need to do this every time but we dont have
                // an other place to know right now when custom header is enabled
                enableTextShadow();
                doUpdateStatusBarCustomHeader(headerImage, force);
            }
        });
    }

    @Override
    public void disableHeader() {
        post(new Runnable() {
             public void run() {
                mCurrentBackground = null;
                mBackgroundImage.setVisibility(View.GONE);
                disableTextShadow();
            }
        });
    }

    /**
     * makes text more readable on light backgrounds
     */
    private void enableTextShadow() {
        mTime.setShadowLayer(5, 0, 0, Color.BLACK);
        mAmPm.setShadowLayer(5, 0, 0, Color.BLACK);
        mDateCollapsed.setShadowLayer(5, 0, 0, Color.BLACK);
        mDateExpanded.setShadowLayer(5, 0, 0, Color.BLACK);
        mBatteryLevel.setShadowLayer(5, 0, 0, Color.BLACK);
        mAlarmStatus.setShadowLayer(5, 0, 0, Color.BLACK);
        mQsDetailHeaderTitle.setShadowLayer(5, 0, 0, Color.BLACK);
        mWeatherLine1.setShadowLayer(5, 0, 0, Color.BLACK);
        mWeatherLine2.setShadowLayer(5, 0, 0, Color.BLACK);
        mEditTileDoneText.setShadowLayer(5, 0, 0, Color.BLACK);
    }

    /**
     * default
     */
    private void disableTextShadow() {
        mTime.setShadowLayer(0, 0, 0, Color.BLACK);
        mAmPm.setShadowLayer(0, 0, 0, Color.BLACK);
        mDateCollapsed.setShadowLayer(0, 0, 0, Color.BLACK);
        mDateExpanded.setShadowLayer(0, 0, 0, Color.BLACK);
        mBatteryLevel.setShadowLayer(0, 0, 0, Color.BLACK);
        mAlarmStatus.setShadowLayer(0, 0, 0, Color.BLACK);
        mQsDetailHeaderTitle.setShadowLayer(0, 0, 0, Color.BLACK);
        mWeatherLine1.setShadowLayer(0, 0, 0, Color.BLACK);
        mWeatherLine2.setShadowLayer(0, 0, 0, Color.BLACK);
        mEditTileDoneText.setShadowLayer(0, 0, 0, Color.BLACK);
    }

    private void setQSHeaderAlpha() {
        if (mHeaderView != null) {
            mHeaderView.getBackground().setAlpha(mQSHeaderAlpha);
        }
        if (mBackgroundImage != null) {
            mBackgroundImage.setAlpha(mQSHeaderAlpha);
        }
    }
    private void setStatusBarDetailFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mAmPm.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                mAmPm.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                mAmPm.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                mAmPm.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                mAmPm.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                mAmPm.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                mAmPm.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                mAmPm.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                mAmPm.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                mAmPm.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                mAmPm.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                mAmPm.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }


    private void setStatusBarDateFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));       
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:  
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                mDateCollapsed.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                mDateCollapsed.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                mDateCollapsed.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }


    private void setStatusBarAlarmFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
               mAlarmStatus.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
               mAlarmStatus.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
               mAlarmStatus.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
               mAlarmStatus.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
              mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
              mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
              mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
              mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void setStatusBarClockFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));   
                break;
            case FONT_THIN:
                mTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                mTime.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));;
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                mTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                mTime.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                mTime.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                mTime.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                mTime.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                mTime.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                mTime.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                mTime.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                mTime.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                mTime.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }


	private void setStatusBarWeatherFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));        
                break;
            case FONT_THIN_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));

                break;
            case FONT_BLACK:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                mWeatherLine1.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                mWeatherLine1.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void setStatusBarHeaderFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                mBatteryLevel.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                mBatteryLevel.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mEditTileDoneText.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                mBatteryLevel.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                mBatteryLevel.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mEditTileDoneText.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                mBatteryLevel.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mEditTileDoneText.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                mBatteryLevel.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mQsDetailHeaderTitle.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mEditTileDoneText.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }
}
