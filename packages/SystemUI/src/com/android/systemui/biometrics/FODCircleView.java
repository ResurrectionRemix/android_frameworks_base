/**
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.biometrics;

import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.hardware.biometrics.BiometricSourceType;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.palette.graphics.Palette;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.utils.ThreadUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.tuner.TunerService;

import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreen;
import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreenCallback;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

public class FODCircleView extends ImageView implements ConfigurationListener,
            Handler.Callback, TunerService.Tunable   {
    private final String SCREEN_BRIGHTNESS = "system:" + Settings.System.SCREEN_BRIGHTNESS;
    private final int[][] BRIGHTNESS_ALPHA_ARRAY = {
        new int[]{0, 255},
        new int[]{1, 224},
        new int[]{2, 213},
        new int[]{3, 211},
        new int[]{4, 208},
        new int[]{5, 206},
        new int[]{6, 203},
        new int[]{8, 200},
        new int[]{10, 196},
        new int[]{15, 186},
        new int[]{20, 176},
        new int[]{30, 160},
        new int[]{45, 139},
        new int[]{70, 114},
        new int[]{100, 90},
        new int[]{150, 56},
        new int[]{227, 14},
        new int[]{255, 0}
    };
    private final int MSG_HBM_OFF = 1001;
    private final int MSG_HBM_ON = 1002;
    private static final int FADE_ANIM_DURATION = 250;

    private final int mPositionX;
    private final int mPositionY;
    private final int mSize;
    private final int mDreamingMaxOffset;
    private final int mNavigationBarSize;
    private final boolean mShouldBoostBrightness;
    private final Paint mPaintFingerprint = new Paint();
    private final Paint mPaintIcon = new Paint();
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private final WindowManager.LayoutParams mParamsPressed = new WindowManager.LayoutParams();
    private final WindowManager mWindowManager;

    private IFingerprintInscreen mFingerprintInscreenDaemon;

    private Bitmap mIconBitmap;

    private int mCurDim;
    private int mDreamingOffsetY;
    private int mCurrentBrightness;
    private int mHbmOffDelay;
    private int mHbmOnDelay;
    private boolean mSupportsAlwaysOnHbm;

    private boolean mNoDim;

    private boolean mViewPressedDisplayed = false;
    private final ImageView mViewPressed;

    private boolean mFading;
    private boolean mIsBouncer;
    private boolean mIsDreaming;
    private boolean mIsPulsing;
    private boolean mIsKeyguard;
    private boolean mIsShowing;
    private boolean mIsCircleShowing;
    private boolean mIsAuthenticated;

    private float mCurrentDimAmount = 0.0f;

    private Handler mHandler;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private LockPatternUtils mLockPatternUtils;

    private Timer mBurnInProtectionTimer;
    private WallpaperManager mWallManager;
    private int iconcolor = 0xFF3980FF;

    private FODAnimation mFODAnimation;
    private boolean mIsRecognizingAnimEnabled;


    private int mSelectedIcon;
    private TypedArray mIconStyles;
    private boolean mBrightIcon;

    private IFingerprintInscreenCallback mFingerprintInscreenCallback =
            new IFingerprintInscreenCallback.Stub() {
        @Override
        public void onFingerDown() {
            mHandler.post(() -> showCircle());
        }

        @Override
        public void onFingerUp() {
            mHandler.post(() -> hideCircle());
        }
    };

    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            mIsDreaming = dreaming;
            updateAlpha();

            if (dreaming) {
                mBurnInProtectionTimer = new Timer();
                mBurnInProtectionTimer.schedule(new BurnInProtectionTask(), 0, 60 * 1000);
            } else if (mBurnInProtectionTimer != null) {
                mBurnInProtectionTimer.cancel();
                updatePosition();
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            mIsKeyguard = showing;
            updateStyle();
            updatePosition();
            if (mFODAnimation != null) {
                mFODAnimation.setAnimationKeyguard(mIsKeyguard);
            }
        }

        @Override
        public void onKeyguardBouncerChanged(boolean isBouncer) {
            mIsBouncer = isBouncer;
            if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                if (isPinOrPattern(mUpdateMonitor.getCurrentUser()) || !isBouncer) {
                    show();
                } else {
                    hide();
                }
            } else {
                hide();
            }
        }

        @Override
        public void onBiometricAuthenticated(int userId, BiometricSourceType biometricSourceType) {
            super.onBiometricAuthenticated(userId, biometricSourceType);
            mIsAuthenticated = true;
        }

        @Override
        public void onPulsing(boolean pulsing) {
            super.onPulsing(pulsing);
            mIsPulsing = pulsing;
	        if (mIsPulsing) {
                mIsDreaming = false;
	        }
        } 

        @Override
        public void onScreenTurnedOff() {
            hide();
        }

        @Override
        public void onScreenTurnedOn() {
            if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
        }

        @Override
        public void onBiometricHelp(int msgId, String helpString,
                BiometricSourceType biometricSourceType) {
            if (biometricSourceType == BiometricSourceType.FINGERPRINT &&
                    msgId == -1) { // Auth error
                hideCircle();
                mHandler.post(() -> mFODAnimation.hideFODanimation());
            }
        }
    };

    private boolean mCutoutMasked;
    private int mStatusbarHeight;

    public FODCircleView(Context context) {
        super(context);

        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon == null) {
            throw new RuntimeException("Unable to get IFingerprintInscreen");
        }

        vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen daemon_v1_1 =
                getFingerprintInScreenDaemonV1_1(daemon);

        try {
            mShouldBoostBrightness = daemon.shouldBoostBrightness();
            mPositionX = daemon.getPositionX();
            mPositionY = daemon.getPositionY();
            mSize = daemon.getSize();
            if (daemon_v1_1 != null) {
                mSupportsAlwaysOnHbm = daemon_v1_1.supportsAlwaysOnHBM();
                mNoDim = daemon_v1_1.noDim();
                mHbmOnDelay = daemon_v1_1.getHbmOnDelay();
                mHbmOffDelay = daemon_v1_1.getHbmOffDelay();
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to retrieve FOD circle position or size");
        }

        mViewPressed = new ImageView(context);

        Resources res = context.getResources();

        mPaintFingerprint.setAntiAlias(true);
        mPaintFingerprint.setColor(res.getColor(R.color.config_fodColor));

        mWindowManager = context.getSystemService(WindowManager.class);

        mNavigationBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);

        mDreamingMaxOffset = (int) (mSize * 0.1f);

        mHandler = new Handler(Looper.getMainLooper(), this);

        mParams.height = mSize;
        mParams.width = mSize;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.setTitle(res.getString(R.string.fod_view_title));
        mParams.packageName = "android";
        mParams.type = WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mWindowManager.addView(this, mParams);

        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();
        updatePosition();
        hide();

        mLockPatternUtils = new LockPatternUtils(mContext);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
        mUpdateMonitor.registerCallback(mMonitorCallback);

        updateCutoutFlags();

        Dependency.get(ConfigurationController.class).addCallback(this);
        mPowerManager = context.getSystemService(PowerManager.class);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                FODCircleView.class.getSimpleName());

        mFODAnimation = new FODAnimation(context, mPositionX, mPositionY);
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            float drawingDimAmount = mParams.dimAmount;
            if (!mSupportsAlwaysOnHbm) {
                if (mCurrentDimAmount == 0.0f && drawingDimAmount > 0.0f) {
                    ThreadUtils.postOnBackgroundThread(() -> {
                      dispatchPress();
                    });
                    mCurrentDimAmount = drawingDimAmount;
                } else if (mCurrentDimAmount > 0.0f && drawingDimAmount == 0.0f) {
                    mCurrentDimAmount = drawingDimAmount;
                }
            }
        });
    }

    private CustomSettingsObserver mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
    private class CustomSettingsObserver extends ContentObserver {

        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FOD_ICON),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FOD_ANIM),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FOD_BRIGHT_ICON),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FOD_ANIM_KEYGUARD),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.FOD_ICON))) {
                updateStyle();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.FOD_ANIM))) {
                updateStyle();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.FOD_BRIGHT_ICON))) {
                updateStyle();
            }
        }

        public void update() {
            updateStyle();
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        mCurrentBrightness = newValue != null ? Integer.parseInt(newValue) : 0;
        setDim(true);
    }

    private int interpolate(int i, int i2, int i3, int i4, int i5) {
        int i6 = i5 - i4;
        int i7 = i - i2;
        int i8 = ((i6 * 2) * i7) / (i3 - i2);
        int i9 = i8 / 2;
        int i10 = i2 - i3;
        return i4 + i9 + (i8 % 2) + ((i10 == 0 || i6 == 0) ? 0 : (((i7 * 2) * (i - i3)) / i6) / i10);
    }

    private int getDimAlpha() {
        int length = BRIGHTNESS_ALPHA_ARRAY.length;
        int i = 0;
        while (i < length && BRIGHTNESS_ALPHA_ARRAY[i][0] < mCurrentBrightness) {
            i++;
        }
        if (i == 0) {
            return BRIGHTNESS_ALPHA_ARRAY[0][1];
        }
        if (i == length) {
            return BRIGHTNESS_ALPHA_ARRAY[length - 1][1];
        }
        int[][] iArr = BRIGHTNESS_ALPHA_ARRAY;
        int i2 = i - 1;
        return interpolate(mCurrentBrightness, iArr[i2][0], iArr[i][0], iArr[i2][1], iArr[i][1]);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mIsCircleShowing) {
            Resources res = mContext.getResources();
            mParamsPressed.height = mSize;
            mParamsPressed.width = mSize;
            mParamsPressed.format = PixelFormat.TRANSLUCENT;

            mParamsPressed.setTitle(res.getString(R.string.fod_view_pressed_title));
            mParamsPressed.packageName = "android";
            mParamsPressed.type = WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
            mParamsPressed.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
	            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mParamsPressed.gravity = Gravity.TOP | Gravity.LEFT;

            if (getFODPressedState() == 0) {
                //canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprint);
                setImageResource(R.drawable.fod_icon_pressed);
            } else if (getFODPressedState() == 1) {
                //canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprint);
                setImageResource(R.drawable.fod_icon_pressed_white);
            } else if (getFODPressedState() == 2) {
                canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprint);
            }

            if (!mViewPressedDisplayed && mIsShowing) {
                mViewPressedDisplayed = true;
                mWindowManager.addView(mViewPressed, mParamsPressed);
                updateCirclePosition();
            }
        } else {
            if (mViewPressedDisplayed) {
                mViewPressedDisplayed = false;
                mWindowManager.removeView(mViewPressed);
            }
        }
    }

    private int getFODPressedState() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_PRESSED_STATE, 2);
    }

    private void setFODPressedState() {
        int fodpressed = getFODPressedState();

        if (fodpressed == 0) {
            setImageResource(R.drawable.fod_icon_pressed);
        } else if (fodpressed == 1) {
            setImageResource(R.drawable.fod_icon_pressed_white);
        } else if (fodpressed == 2) {
            setImageDrawable(null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newIsInside = (x > 0 && x < mSize) && (y > 0 && y < mSize);

        if (event.getAction() == MotionEvent.ACTION_DOWN && newIsInside) {
            showCircle();
            if (mIsRecognizingAnimEnabled && (!mIsDreaming || mIsPulsing)) {
                mHandler.post(() -> mFODAnimation.showFODanimation());
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            hideCircle();
            mHandler.post(() -> mFODAnimation.hideFODanimation());
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            return true;
        }

        mHandler.post(() -> mFODAnimation.hideFODanimation());
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateStyle();
        updatePosition();
    }

    public IFingerprintInscreen getFingerprintInScreenDaemon() {
        if (mFingerprintInscreenDaemon == null) {
            try {
                mFingerprintInscreenDaemon = IFingerprintInscreen.getService();
                if (mFingerprintInscreenDaemon != null) {
                    mFingerprintInscreenDaemon.setCallback(mFingerprintInscreenCallback);
                    mFingerprintInscreenDaemon.asBinder().linkToDeath((cookie) -> {
                        mFingerprintInscreenDaemon = null;
                    }, 0);
                }
            } catch (NoSuchElementException | RemoteException e) {
                // do nothing
            }
        }
        return mFingerprintInscreenDaemon;
    }

    public vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen
            getFingerprintInScreenDaemonV1_1() {
        return getFingerprintInScreenDaemonV1_1(getFingerprintInScreenDaemon());
    }

    public vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen
            getFingerprintInScreenDaemonV1_1(IFingerprintInscreen daemon) {
        return vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen.castFrom(
                   daemon);
    }

    public void dispatchPress() {
        if (mFading) return;
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onPress();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchRelease() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onRelease();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchShow() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onShowFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchHide() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onHideFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void switchHbm(boolean enable) {
        if (mShouldBoostBrightness) {
            if (enable) {
                mParams.screenBrightness = 1.0f;
            } else {
                mParams.screenBrightness = 0.0f;
            }
            mWindowManager.updateViewLayout(this, mParams);
        }

        vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen daemon_v1_1 =
                getFingerprintInScreenDaemonV1_1();

        try {
            if (daemon_v1_1 != null) {
                daemon_v1_1.switchHbm(enable);
            }
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void showCircle() {
        if (mIsAuthenticated || mFading) {
            return;
        }
        mIsCircleShowing = true;

        setKeepScreenOn(true);

        if (!mSupportsAlwaysOnHbm) {
            setDim(true);
        } else {
            ThreadUtils.postOnBackgroundThread(() -> {
              dispatchPress();
            });
            setColorFilter(Color.argb(0, 0, 0, 0), 
                     PorterDuff.Mode.SRC_ATOP);
        }
        updateAlpha();

        setFODPressedState();
        invalidate();
    }

    public void hideCircle() {
        mIsCircleShowing = false;
        setFODIcon();
        invalidate();

        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchRelease();
        });

        if (!mSupportsAlwaysOnHbm) {
            setDim(false);
        } else {
            if (mBrightIcon) {
                setColorFilter(Color.argb(0, 0, 0, 0),
                   PorterDuff.Mode.SRC_ATOP); 
            } else {
                setColorFilter(Color.argb(mCurDim, 0, 0, 0),
                   PorterDuff.Mode.SRC_ATOP); 
            }
            invalidate();
        }
        updateAlpha();

        setKeepScreenOn(false);
    }

    private boolean useWallpaperColor() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON_WALLPAPER_COLOR, 0) != 0;
    }

    private void setFODIcon() {
        if (useWallpaperColor()) {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                if (bitmap != null) {
                    Palette p = Palette.from(bitmap).generate();
                    int wallColor = p.getDominantColor(iconcolor);
                    if (iconcolor != wallColor) {
                        iconcolor = wallColor;
                    }
                    mIconStyles = mContext.getResources().obtainTypedArray(R.array.fod_icon_resources);
                    mIconBitmap = BitmapFactory.decodeResource(getResources(),
                            mIconStyles.getResourceId(mSelectedIcon, -1)).copy(Bitmap.Config.ARGB_8888, true);
                    mPaintIcon.setColorFilter(new PorterDuffColorFilter(lighter(iconcolor, 3),
                            PorterDuff.Mode.SRC_IN));
                    Canvas canvas = new Canvas(mIconBitmap);
                    canvas.drawBitmap(mIconBitmap, 0, 0, mPaintIcon);
                    setImageBitmap(mIconBitmap);
                    this.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else {
            mIconStyles = mContext.getResources().obtainTypedArray(R.array.fod_icon_resources);
            setImageResource(mIconStyles.getResourceId(mSelectedIcon, -1));
            this.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
    }

    private static int lighter(int color, int factor) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        blue = blue * factor;
        green = green * factor;
        blue = blue * factor;

        blue = blue > 255 ? 255 : blue;
        green = green > 255 ? 255 : green;
        red = red > 255 ? 255 : red;

        return Color.argb(Color.alpha(color), red, green, blue);
    }

    public void show() {
        if (!mUpdateMonitor.isScreenOn()) {
            // Keyguard is shown just after screen turning off
            return;
        }

        if (mIsBouncer && !isPinOrPattern(mUpdateMonitor.getCurrentUser())) {
            // Ignore show calls when Keyguard password screen is being shown
            return;
        }

        mIsShowing = true;
        mIsAuthenticated = false;

        updatePosition();

        setVisibility(View.VISIBLE);
        animate().withStartAction(() -> mFading = true)
                .alpha(mIsDreaming || mIsPulsing ? 0.75f : 1.0f)
                .setDuration(FADE_ANIM_DURATION)
                .withEndAction(() -> mFading = false)
                .start();
        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchShow();
        });
        if (mSupportsAlwaysOnHbm) {
            Dependency.get(TunerService.class).addTunable(this, SCREEN_BRIGHTNESS);
            setDim(true);
            mHandler.sendEmptyMessageDelayed(MSG_HBM_ON, mHbmOnDelay);
        }
    }

    public void hide() {
        mIsShowing = false;

        if (mViewPressedDisplayed) {
            mViewPressedDisplayed = false;
            mWindowManager.removeView(mViewPressed);
        }

        if (mSupportsAlwaysOnHbm) {
            mHandler.sendEmptyMessageDelayed(MSG_HBM_OFF, mHbmOffDelay);
            if (mHandler.hasMessages(MSG_HBM_ON)) {
                mHandler.removeMessages(MSG_HBM_ON);
            }
            setDim(false);
            Dependency.get(TunerService.class).removeTunable(this);
        }
        animate().withStartAction(() -> mFading = true)
                .alpha(0)
                .setDuration(FADE_ANIM_DURATION)
                .withEndAction(() -> {
                    setVisibility(View.GONE);
                    mFading = false;
                })
                .start();

        hideCircle();
        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchHide();
        });
    }

    private void updateAlpha() {
        if (mIsCircleShowing) {
            setAlpha(1.0f);
        } else {
            setAlpha(mIsDreaming || mIsPulsing ? 0.75f : 1.0f);
        }
    }

    private void updateStyle() {
        mIsRecognizingAnimEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_RECOGNIZING_ANIMATION, 0) != 0;
        mSelectedIcon = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON, 3);
        mBrightIcon = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_BRIGHT_ICON, 0) == 1;
        if (mFODAnimation != null) {
            mFODAnimation.update();
        }
    }

    private void updatePosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        int cutoutMaskedExtra = mCutoutMasked ? mStatusbarHeight : 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                mParams.x = mPositionX;
                mParams.y = mPositionY - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_90:
                mParams.x = mPositionY;
                mParams.y = mPositionX - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_180:
                mParams.x = mPositionX;
                mParams.y = size.y - mPositionY - mSize - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_270:
                mParams.x = size.x - mPositionY - mSize - mNavigationBarSize - cutoutMaskedExtra;
                mParams.y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }
        if (mIsDreaming) {
            mParams.y += mDreamingOffsetY;
            mFODAnimation.updateParams(mParams.y);
        }

        mWindowManager.updateViewLayout(this, mParams);
    }

    private void updateCirclePosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        int cutoutMaskedExtra = mCutoutMasked ? mStatusbarHeight : 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                mParamsPressed.x = mPositionX;
                mParamsPressed.y = mPositionY - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_90:
                mParamsPressed.x = mPositionY;
                mParamsPressed.y = mPositionX - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_180:
                mParamsPressed.x = mPositionX;
                mParamsPressed.y = size.y - mPositionY - mSize - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_270:
                mParamsPressed.x = size.x - mPositionY - mSize - mNavigationBarSize - cutoutMaskedExtra;
                mParamsPressed.y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }

        if (mIsDreaming) {
            mParamsPressed.y += mDreamingOffsetY;
        }

        mWindowManager.updateViewLayout(mViewPressed, mParamsPressed);
    }

    private void setDim(boolean dim) {
        if (dim) {
            int dimAmount = 0;

            if (!mSupportsAlwaysOnHbm) {
                mCurrentBrightness = Settings.System.getInt(getContext().getContentResolver(),
                       Settings.System.SCREEN_BRIGHTNESS, 100);
            }

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            try {
                dimAmount = daemon.getDimAmount(mCurrentBrightness);
            } catch (RemoteException e) {
                // do nothing
            }

            if (!mNoDim) {
                mParams.dimAmount = dimAmount / 255.0f;
            }
            if (mSupportsAlwaysOnHbm) {
                mCurDim = getDimAlpha();
                if (mBrightIcon) {
                    setColorFilter(Color.argb(0, 0, 0, 0), 
                        PorterDuff.Mode.SRC_ATOP);
                } else {
                    setColorFilter(Color.argb(getDimAlpha(), 0, 0, 0), 
                        PorterDuff.Mode.SRC_ATOP);
                }
            }
        } else {
            mParams.dimAmount = 0.0f;
        }

        mWindowManager.updateViewLayout(this, mParams);
    }

    private boolean isPinOrPattern(int userId) {
        int passwordQuality = mLockPatternUtils.getActivePasswordQuality(userId);
        switch (passwordQuality) {
            // PIN
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            // Pattern
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return true;
        }

        return false;
    }

    private class BurnInProtectionTask extends TimerTask {
        @Override
        public void run() {
            long now = System.currentTimeMillis() / 1000 / 60;
            // Let y to be not synchronized with x, so that we get maximum movement
            mDreamingOffsetY = (int) ((now + mDreamingMaxOffset / 3) % (mDreamingMaxOffset * 2));
            mDreamingOffsetY -= mDreamingMaxOffset;

            mHandler.post(() -> updatePosition());
        }
    };

    @Override
    public void onOverlayChanged() {
        updateCutoutFlags();
    }

    private void updateCutoutFlags() {
        mStatusbarHeight = getContext().getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height_portrait);
        boolean cutoutMasked = getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_maskMainBuiltInDisplayCutout);
        if (mCutoutMasked != cutoutMasked) {
            mCutoutMasked = cutoutMasked;
            updatePosition();
        }
    }

    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_HBM_OFF: {
                switchHbm(false);
            } break;
            case MSG_HBM_ON: {
                switchHbm(true);
            } break;

        }
        return true;
    }
}

