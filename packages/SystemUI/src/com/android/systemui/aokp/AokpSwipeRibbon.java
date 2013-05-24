/*
 * Copyright (C) 2013 The Android Open Kang Project
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

package com.android.systemui.aokp;

import com.android.systemui.R;

import java.lang.IllegalArgumentException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.AwesomeAnimationHelper;
import com.android.internal.util.aokp.BackgroundAlphaColorDrawable;
import com.android.systemui.aokp.RibbonGestureCatcherView;
import static com.android.systemui.statusbar.toggles.ToggleManager.*;
import com.android.systemui.statusbar.toggles.*;

public class AokpSwipeRibbon extends LinearLayout {
    public static final String TAG = "NAVIGATION BAR RIBBON";

    private Context mContext;
    private RibbonGestureCatcherView mGesturePanel;
    public FrameLayout mPopupView;
    public FrameLayout mContainerFrame;
    public WindowManager mWindowManager;
    private SettingsObserver mSettingsObserver;
    private LinearLayout mRibbon;
    private LinearLayout mRibbonMain;
    private ImageButton mTogglesButton;
    private TextView mTogglesText;
    private Button mBackGround;
    private boolean mText, mColorize, hasNavBarByDefault, NavBarEnabled, navAutoHide, mNavBarShowing, mVib, mToggleButtonLoc, mHideIme;
    private int mHideTimeOut = 5000;
    private boolean showing = false;
    private boolean animating = false;
    private int mRibbonNumber, mLocationNumber, mSize, mColor, mTextColor, mOpacity, animationIn,
        animationOut, animTogglesIn, animTogglesOut, mIconLoc, mPad, mAnimDur, mDismiss, mAnim;
    private ArrayList<String> shortTargets = new ArrayList<String>();
    private ArrayList<String> longTargets = new ArrayList<String>();
    private ArrayList<String> customIcons = new ArrayList<String>();
    private String mLocation;
    private Handler mHandler;
    private boolean[] mEnableSides = new boolean[3];
    private boolean flipped = false;
    private Vibrator vib;

    private ArrayList<LinearLayout> mRows = new ArrayList<LinearLayout>();
    private ArrayList<BaseToggle> toggles = new ArrayList<BaseToggle>();
    private ScrollView mRibbonSV;
    private ScrollView mTogglesSV;
    private Animation mAnimationIn;
    private Animation mAnimationOut;
    private int visible = 0;
    private int mDisabledFlags = 0;

    private static final String TOGGLE_DELIMITER = "|";

    private BluetoothController bluetoothController;
    private NetworkController networkController;
    private BatteryController batteryController;
    private LocationController locationController;
    private BrightnessController brightnessController;

    public void setControllers(BluetoothController bt, NetworkController net,
            BatteryController batt, LocationController loc, BrightnessController screen) {
        bluetoothController = bt;
        networkController = net;
        batteryController = batt;
        locationController = loc;
        brightnessController = screen;
        updateSettings();
    }

    private static final LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(
            LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

    public AokpSwipeRibbon(Context context, AttributeSet attrs, String location) {
        super(context, attrs);
        mContext = context;
        mLocation = location;
        setRibbonNumber();
        IntentFilter filter = new IntentFilter();
        filter.addAction(RibbonReceiver.ACTION_TOGGLE_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_SHOW_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_HIDE_RIBBON);
        mContext.registerReceiver(new RibbonReceiver(), filter);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        vib = (Vibrator) mContext.getSystemService(mContext.VIBRATOR_SERVICE);
        mHandler = new Handler();
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        updateSettings();
    }

    private void setRibbonNumber() {
        if (mLocation.equals("bottom")) {
            mRibbonNumber = 5;
            mLocationNumber = 2;
        } else if (mLocation.equals("left")) {
            mRibbonNumber = 2;
            mLocationNumber = 0;
        } else if (mLocation.equals("right")) {
            mRibbonNumber = 4;
            mLocationNumber = 1;
        }
    }

    public void toggleRibbonView() {
        if (showing) {
            hideRibbonView();
        } else {
            showRibbonView();
        }
    }

    public void showRibbonView() {
        if (!showing) {
            showing = true;
            WindowManager.LayoutParams params = getParams();
            params.gravity = getGravity();
            params.setTitle("Ribbon" + mLocation);
            if (mWindowManager != null) {
                mWindowManager.addView(mPopupView, params);
                mContainerFrame.startAnimation(mAnimationIn);
                if (mHideTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mHideTimeOut);
                }
            }
        }
    }

    public void hideRibbonView() {
        if (mPopupView != null && showing) {
            showing = false;
            mContainerFrame.startAnimation(mAnimationOut);
        }
    }

    private Runnable delayHide = new Runnable() {
        public void run() {
            if (showing) {
                hideRibbonView();
            }
        }
    };

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                mLocation.equals("bottom") ? WindowManager.LayoutParams.MATCH_PARENT
                    : WindowManager.LayoutParams.WRAP_CONTENT,
                mLocation.equals("bottom") ? WindowManager.LayoutParams.WRAP_CONTENT
                    : WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        return params;
    }

    private int getGravity() {
        int gravity = 0;
        if (mLocation.equals("bottom")) {
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        } else if (mLocation.equals("left")) {
            gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        } else {
            gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        }
        return gravity;
    }

    private void setAnimation() {
        if (mLocation.equals("bottom")) {
            animationIn = com.android.internal.R.anim.slide_in_up_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_down_ribbon;
        } else if (mLocation.equals("left")) {
            animationIn = com.android.internal.R.anim.slide_in_left_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_left_ribbon;
            animTogglesIn = com.android.internal.R.anim.slide_in_left_ribbon;
            animTogglesOut = com.android.internal.R.anim.slide_out_right_ribbon;
        } else {
            animationIn = com.android.internal.R.anim.slide_in_right_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_right_ribbon;
            animTogglesIn = com.android.internal.R.anim.slide_in_right_ribbon;
            animTogglesOut = com.android.internal.R.anim.slide_out_left_ribbon;
        }
        if (mAnim > 0) {
            int[] animArray = AwesomeAnimationHelper.getAnimations(mAnim);
            animationIn = animArray[1];
            animationOut = animArray[0];
        }
    }

    public void createRibbonView() {
        if (mGesturePanel != null) {
            try {
                mWindowManager.removeView(mGesturePanel);
            } catch (IllegalArgumentException e) {
                //If we try to remove the gesture panel and it's not currently attached.
            }
        }
        if (sideEnabled()) {
            mGesturePanel = new RibbonGestureCatcherView(mContext,null,mLocation);
            mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
        }
        mPopupView = new FrameLayout(mContext);
        mPopupView.removeAllViews();
        mContainerFrame = new FrameLayout(mContext);
        mContainerFrame.removeAllViews();
        if (mNavBarShowing) {
            int adjustment = mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.status_bar_height);
            mPopupView.setPadding(0, adjustment, 0, 0);
        }
        mBackGround = new Button(mContext);
        mBackGround.setClickable(false);
        mBackGround.setBackgroundColor(mColor);
        float opacity = (255f * (mOpacity * 0.01f));
        mBackGround.getBackground().setAlpha((int)opacity);
        View ribbonView = View.inflate(mContext, R.layout.aokp_swipe_ribbon, null);
        mRibbonMain = (LinearLayout) ribbonView.findViewById(R.id.ribbon_main);
        if (mToggleButtonLoc) {
            mTogglesButton = (ImageButton) ribbonView.findViewById(R.id.toggles_bottom);
            mTogglesText = (TextView) ribbonView.findViewById(R.id.label_bottom);
        } else {
            mTogglesButton = (ImageButton) ribbonView.findViewById(R.id.toggles);
            mTogglesText = (TextView) ribbonView.findViewById(R.id.label);
        }
        switch (mIconLoc) {
            case 0:
                mRibbonMain.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
                break;
            case 1:
                mRibbonMain.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                break;
            case 2:
                mRibbonMain.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                break;
        }
        mRibbon = (LinearLayout) ribbonView.findViewById(R.id.ribbon);
        setupRibbon();
        maybeToggleOnly();
        ribbonView.invalidate();
        mContainerFrame.addView(mBackGround, backgroundParams);
        mContainerFrame.addView(ribbonView);
        mContainerFrame.setDrawingCacheEnabled(true);
        mAnimationIn = PlayInAnim();
        mAnimationOut = PlayOutAnim();
        mPopupView.addView(mContainerFrame, backgroundParams);
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    mHandler.removeCallbacks(delayHide);
                    if (showing) {
                        hideRibbonView();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private boolean sideEnabled() {
        if (mLocation.equals("bottom") && mEnableSides[0]) {
            return true;
        } else if (mLocation.equals("left") && mEnableSides[1]) {
            return true;
        } else if (mLocation.equals("right") && mEnableSides[2]) {
            return true;
        }
        return false;
    }

    private boolean maybeToggleOnly() {
        if (shortTargets.size() < 1 && longTargets.size() < 1  && toggles.size() > 0) {
            mTogglesButton.setVisibility(View.GONE);
            mTogglesText.setVisibility(View.GONE);
            mRibbon.removeView(mRibbonSV);
            mRibbon.addView(mTogglesSV);
            return true;
        }
        return false;
    }

    public Animation PlayInAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationIn);
            animation.setStartOffset(0);
            animation.setDuration((int) (animation.getDuration() * (mAnimDur * 0.01f)));
            return animation;
        }
        return null;
    }

    public Animation PlayOutAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationOut);
            animation.setStartOffset(0);
            animation.setDuration((int) (animation.getDuration() * (mAnimDur * 0.01f)));
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mWindowManager.removeView(mPopupView);
                    animating = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            return animation;
        }
        return null;
    }

    private void setupRibbon() {
        mRibbon.removeAllViews();
        if (mLocation.equals("bottom")) {
            HorizontalScrollView hsv = new HorizontalScrollView(mContext);
            hsv = AokpRibbonHelper.getRibbon(mContext,
                shortTargets, longTargets, customIcons,
                mText, mTextColor, mSize, mPad, mVib, mColorize, mDismiss);
            hsv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            mRibbon.addView(hsv);
        } else {
            mRibbonSV = new ScrollView(mContext);
            mRibbonSV = AokpRibbonHelper.getVerticalRibbon(mContext,
                shortTargets, longTargets, customIcons, mText, mTextColor,
                mSize, mPad, mVib, mColorize, mDismiss);
            mRibbonSV.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            mRibbon.addView(mRibbonSV);
            mRibbon.setPadding(0, 0, 0, 0);
            addToggleManagers();
        }
    }

    private HashMap<String, Class<? extends BaseToggle>> toggleMap;

    private HashMap<String, Class<? extends BaseToggle>> getToggleMap() {
        if (toggleMap == null) {
            toggleMap = new HashMap<String, Class<? extends BaseToggle>>();
            toggleMap.put(USER_TOGGLE, UserToggle.class);
            toggleMap.put(BRIGHTNESS_TOGGLE, BrightnessToggle.class);
            toggleMap.put(SETTINGS_TOGGLE, SettingsToggle.class);
            toggleMap.put(WIFI_TOGGLE, WifiToggle.class);
            if (deviceSupportsTelephony()) {
                toggleMap.put(SIGNAL_TOGGLE, SignalToggle.class);
                toggleMap.put(WIFI_TETHER_TOGGLE, WifiApToggle.class);
            }
            toggleMap.put(ROTATE_TOGGLE, RotateToggle.class);
            toggleMap.put(CLOCK_TOGGLE, ClockToggle.class);
            toggleMap.put(GPS_TOGGLE, GpsToggle.class);
            toggleMap.put(IME_TOGGLE, ImeToggle.class);
            toggleMap.put(BATTERY_TOGGLE, BatteryToggle.class);
            toggleMap.put(AIRPLANE_TOGGLE, AirplaneModeToggle.class);
            if (deviceSupportsBluetooth()) {
                toggleMap.put(BLUETOOTH_TOGGLE, BluetoothToggle.class);
            }
            toggleMap.put(SWAGGER_TOGGLE, SwaggerToggle.class);
            if (((Vibrator)mContext.getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                toggleMap.put(VIBRATE_TOGGLE, VibrateToggle.class);
                toggleMap.put(SOUND_STATE_TOGGLE, SoundStateToggle.class);
            }
            toggleMap.put(SILENT_TOGGLE, SilentToggle.class);
            toggleMap.put(FCHARGE_TOGGLE, FastChargeToggle.class);
            toggleMap.put(SYNC_TOGGLE, SyncToggle.class);
            if (mContext.getSystemService(Context.NFC_SERVICE) != null) {
                toggleMap.put(NFC_TOGGLE, NfcToggle.class);
            }
            toggleMap.put(TORCH_TOGGLE, TorchToggle.class);
            toggleMap.put(USB_TETHER_TOGGLE, UsbTetherToggle.class);
            if (((TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE))
                    .getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                toggleMap.put(TWOG_TOGGLE, TwoGToggle.class);
            }
            if (TelephonyManager.getLteOnCdmaModeStatic() == PhoneConstants.LTE_ON_CDMA_TRUE
                    || TelephonyManager.getLteOnGsmModeStatic() != 0) {
                toggleMap.put(LTE_TOGGLE, LteToggle.class);
            }
            toggleMap.put(FAV_CONTACT_TOGGLE, FavoriteUserToggle.class);
            toggleMap.put(NAVBAR_HIDE_TOGGLE, NavbarHideToggle.class);
            toggleMap.put(QUICKRECORD_TOGGLE, QuickRecordToggle.class);
            toggleMap.put(QUIETHOURS_TOGGLE, QuietHoursToggle.class);
            toggleMap.put(SLEEP_TOGGLE, SleepToggle.class);
            toggleMap.put(STATUSBAR_TOGGLE, StatusbarToggle.class);
            toggleMap.put(SCREENSHOT_TOGGLE, ScreenshotToggle.class);
            toggleMap.put(REBOOT_TOGGLE, RebootToggle.class);
            toggleMap.put(CUSTOM_TOGGLE, CustomToggle.class);
            toggleMap.put(STAYAWAKE_TOGGLE, StayAwakeToggle.class);
            toggleMap.put(WIRELESS_ADB_TOGGLE, WirelessAdbToggle.class);
            // toggleMap.put(BT_TETHER_TOGGLE, null);
        }
        return toggleMap;
    }

    private void addToggles(ArrayList<String> userToggles) {
        toggles.clear();
        if (userToggles.size() > 0) {

        HashMap<String, Class<? extends BaseToggle>> map = getToggleMap();
        for (String toggleIdent : userToggles) {
            try {
                Class<? extends BaseToggle> theclass = map.get(toggleIdent);
                BaseToggle toggle = theclass.newInstance();
                //might make this user selectable, 0 == mStyle in togglemanager
                toggle.init(mContext, 0);
                toggles.add(toggle);

                if (networkController != null && toggle instanceof NetworkSignalChangedCallback) {
                    networkController
                            .addNetworkSignalChangedCallback((NetworkSignalChangedCallback) toggle);
                    networkController
                            .notifySignalsChangedCallbacks((NetworkSignalChangedCallback) toggle);
                }

                if (bluetoothController != null && toggle instanceof BluetoothStateChangeCallback) {
                    bluetoothController
                            .addStateChangedCallback((BluetoothStateChangeCallback) toggle);
                }

                if (batteryController != null && toggle instanceof BatteryStateChangeCallback) {
                    batteryController.addStateChangedCallback((BatteryStateChangeCallback) toggle);
                    batteryController.updateCallback((BatteryStateChangeCallback) toggle);
                }

                if (locationController != null && toggle instanceof LocationGpsStateChangeCallback) {
                    locationController
                            .addStateChangedCallback((LocationGpsStateChangeCallback) toggle);
                }

                if (brightnessController != null && toggle instanceof BrightnessStateChangeCallback) {
                    brightnessController.addStateChangedCallback((BrightnessStateChangeCallback)
                            toggle);
                }
            } catch (Exception e) {
            }
        }
        }
    }

    private void addToggleManagers() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        int padding = (int) (mPad * metrics.density);
        int mWidth = (int) (75 * metrics.density);
        int mHeight = (int) (75 * metrics.density);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
            mWidth, mHeight);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, padding);
        mTogglesSV = new ScrollView(mContext);
        int length = toggles.size();
        if (length > 0) {
            LinearLayout targetsLayout = new LinearLayout(mContext);
            targetsLayout.setOrientation(LinearLayout.VERTICAL);
            targetsLayout.setGravity(Gravity.CENTER);
            mTogglesSV.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            for (int i = 0; i < length; i++) {
                View view = toggles.get(i).createTileView();
                view.setBackground(null);
                targetsLayout.addView(view, tileParams);
                if (i < length -1) {
                    View v = new View(mContext);
                    targetsLayout.addView(v, lp);
                }
            }
            mTogglesSV.addView(targetsLayout, AokpRibbonHelper.PARAMS_TARGET_SCROLL);
            mTogglesSV.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            addToogleButton();
        }
    }

    private void addToogleButton() {
        mRibbon.removeView(mRibbonSV);
        mTogglesButton.setVisibility(View.VISIBLE);
        if (mText) {
            mTogglesText.setVisibility(View.VISIBLE);
        }
        mRibbon.addView(mRibbonSV);
        mRibbon.invalidate();
        mTogglesButton.getBackground().setAlpha(0);
        mTogglesButton.setImageDrawable(
            mContext.getResources().getDrawable(R.drawable.ribbon_toggles_icon));
        mTogglesText.setText(mContext.getResources().getString(R.string.toggles));
        if (mTextColor != -1) {
            mTogglesText.setTextColor(mTextColor);
        }
        mTogglesText.setOnClickListener(new OnClickListener() {
            @Override
            public final void onClick(View v) {
                 if (mVib) {
                     vib.vibrate(10);
                 }
                 if (flipped) {
                     PlayAnim(mRibbonSV, mTogglesSV, mContext.getResources().getDrawable(R.drawable.ribbon_toggles_icon),
                         mContext.getResources().getString(R.string.toggles));
                 } else {
                     PlayAnim(mTogglesSV, mRibbonSV, mContext.getResources().getDrawable(R.drawable.ribbon_icon),
                         mContext.getResources().getString(R.string.ribbon));
                 }
                 flipped = !flipped;
                 mHandler.removeCallbacks(delayHide);
                 if (mHideTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mHideTimeOut);
                 }
            }
        });
        mTogglesButton.setOnClickListener(new OnClickListener() {
            @Override
            public final void onClick(View v) {
                 if (mVib) {
                     vib.vibrate(10);
                 }
                 if (flipped) {
                     PlayAnim(mRibbonSV, mTogglesSV, mContext.getResources().getDrawable(R.drawable.ribbon_toggles_icon),
                         mContext.getResources().getString(R.string.toggles));
                 } else {
                     PlayAnim(mTogglesSV, mRibbonSV, mContext.getResources().getDrawable(R.drawable.ribbon_icon),
                         mContext.getResources().getString(R.string.ribbon));
                 }
                 flipped = !flipped;
                 mHandler.removeCallbacks(delayHide);
                 if (mHideTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mHideTimeOut);
                 }
            }
        });
    }

    public void PlayAnim(final ScrollView in, final ScrollView out, final Drawable newIcon, final String text) {
        if (mRibbon != null) {
            Animation outAnimation = AnimationUtils.loadAnimation(mContext, animTogglesOut);
            final Animation inAnimation = AnimationUtils.loadAnimation(mContext, animTogglesIn);
            final Animation inIcon = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.fade_in);
            final Animation outIcon = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.fade_out);
            inIcon.setDuration((int) (250 * (mAnimDur * 0.01f)));
            inIcon.setStartOffset(0);
            outIcon.setStartOffset(0);
            outIcon.setDuration((int) (250 * (mAnimDur * 0.01f)));
            outAnimation.setStartOffset(0);
            outAnimation.setDuration((int) (250 * (mAnimDur * 0.01f)));
            outAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    mTogglesButton.startAnimation(outIcon);
                    mTogglesText.startAnimation(outIcon);
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mRibbon.removeView(out);
                    mRibbon.addView(in);
                    mTogglesButton.setImageDrawable(newIcon);
                    mTogglesText.setText(text);
                    mTogglesButton.startAnimation(inIcon);
                    mTogglesText.startAnimation(inIcon);
                    in.startAnimation(inAnimation);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            inAnimation.setStartOffset(0);
            inAnimation.setDuration((int) (250 * (mAnimDur * 0.01f)));
            inAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    animating = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            out.startAnimation(outAnimation);
        }
    }

    protected void updateSwipeArea() {
        final boolean showingIme = ((visible & InputMethodService.IME_VISIBLE) != 0);
        if (mGesturePanel != null) {
            mGesturePanel.setViewVisibility(showingIme);
        }
    }

    public void setNavigationIconHints(int hints) {
          if (hints == visible) return;

        if (mHideIme) {
             visible = hints;
             updateSwipeArea();
        }
    }

    public void setDisabledFlags(int disabledFlags) {
        if (disabledFlags == mDisabledFlags) return;

        if (mHideIme) {
            mDisabledFlags = disabledFlags;
            updateSwipeArea();
        }
    }

    private boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_SHORT[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_LONG[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_ICONS[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_RIBBON_TEXT[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SIZE[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_ENABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_LOCATION[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TEXT_COLOR[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SPACE[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_VIBRATE[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_COLORIZE[mRibbonNumber]), false, this);
            for (int i = 0; i < 3; i++) {
	            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ENABLE_RIBBON_LOCATION[i]), false, this);
            }
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_SHOW_NOW), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_HEIGHT), false, this);

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_HIDE_TIMEOUT[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_OPACITY[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_COLOR[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_TOGGLES[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DISMISS[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ANIMATION_DURATION[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ANIMATION_TYPE[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TOGGLE_BUTTON_LOCATION[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_HIDE_IME[mLocationNumber]), false, this);

            if (mLocationNumber < 2) {
                resolver.registerContentObserver(Settings.System.getUriFor(
                        Settings.System.RIBBON_ICON_LOCATION[mLocationNumber]), false, this);
            }

        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        shortTargets = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_SHORT[mRibbonNumber]);
        longTargets = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_LONG[mRibbonNumber]);
        customIcons = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_ICONS[mRibbonNumber]);
        mText = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_TEXT[mRibbonNumber], true);
        mTextColor = Settings.System.getInt(cr,
                 Settings.System.RIBBON_TEXT_COLOR[mRibbonNumber], -1);
        mSize = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SIZE[mRibbonNumber], 0);
        mPad = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SPACE[mRibbonNumber], 5);
        mVib = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_ICON_VIBRATE[mRibbonNumber], true);
        mColorize = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_ICON_COLORIZE[mRibbonNumber], false);
        mAnimDur = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ANIMATION_DURATION[mLocationNumber], 50);
        mDismiss = Settings.System.getInt(cr,
                 Settings.System.RIBBON_DISMISS[mLocationNumber], 1);
        mHideTimeOut = Settings.System.getInt(cr,
                 Settings.System.RIBBON_HIDE_TIMEOUT[mLocationNumber], mHideTimeOut);
        mColor = Settings.System.getInt(cr,
                 Settings.System.SWIPE_RIBBON_COLOR[mLocationNumber], Color.BLACK);
        mOpacity = Settings.System.getInt(cr,
                 Settings.System.SWIPE_RIBBON_OPACITY[mLocationNumber], 100);
        mAnim = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ANIMATION_TYPE[mLocationNumber], 0);
        mToggleButtonLoc = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_TOGGLE_BUTTON_LOCATION[mLocationNumber], false);
        mHideIme = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_HIDE_IME[mLocationNumber], false);
        if (mLocationNumber < 2) {
            mIconLoc = Settings.System.getInt(cr,
                     Settings.System.RIBBON_ICON_LOCATION[mLocationNumber], 0);
        }

        for (int i = 0; i < 3; i++) {
            mEnableSides[i] = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_LOCATION[i], false);
        }
        boolean manualNavBarHide = Settings.System.getBoolean(mContext.getContentResolver(), Settings.System.NAVIGATION_BAR_SHOW_NOW, true);
        boolean navHeightZero = Settings.System.getInt(mContext.getContentResolver(), Settings.System.NAVIGATION_BAR_HEIGHT, 10) < 5;
        navAutoHide = Settings.System.getBoolean(cr, Settings.System.NAV_HIDE_ENABLE, false);
        NavBarEnabled = Settings.System.getBoolean(cr, Settings.System.NAVIGATION_BAR_SHOW, false);
        hasNavBarByDefault = mContext.getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar);
        mNavBarShowing = (NavBarEnabled || hasNavBarByDefault) && manualNavBarHide && !navHeightZero && !navAutoHide;
        mEnableSides[0] = mEnableSides[0] && (!(NavBarEnabled || hasNavBarByDefault) || !manualNavBarHide);

        addToggles(Settings.System.getArrayList(cr, Settings.System.SWIPE_RIBBON_TOGGLES[mLocationNumber]));

        setAnimation();
        if (!showing && !animating) {
            createRibbonView();
        }
    }

    public class RibbonReceiver extends BroadcastReceiver {
        public static final String ACTION_TOGGLE_RIBBON = "com.android.systemui.ACTION_TOGGLE_RIBBON";
        public static final String ACTION_SHOW_RIBBON = "com.android.systemui.ACTION_SHOW_RIBBON";
        public static final String ACTION_HIDE_RIBBON = "com.android.systemui.ACTION_HIDE_RIBBON";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String location = intent.getStringExtra("action");
            if (ACTION_TOGGLE_RIBBON.equals(action)) {
                mHandler.removeCallbacks(delayHide);
                if (location.equals(mLocation)) {
                    toggleRibbonView();
                }
            } else if (ACTION_SHOW_RIBBON.equals(action)) {
                if (location.equals(mLocation)) {
                    if (!showing) {
                        showRibbonView();
                    }
                }
            } else if (ACTION_HIDE_RIBBON.equals(action)) {
                mHandler.removeCallbacks(delayHide);
                if (showing) {
                    hideRibbonView();
                }
            }
        }
    }
}
