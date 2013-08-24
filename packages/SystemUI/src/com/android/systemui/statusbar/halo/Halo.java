/*
 * Copyright (C) 2013 ParanoidAndroid.
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

package com.android.systemui.statusbar.halo;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.INotificationManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.RemoteException;
import android.os.Vibrator;
import android.os.ServiceManager;
import android.provider.Settings;
import android.service.notification.INotificationListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.ExtendedPropertiesUtils;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.animation.TimeInterpolator;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.SoundEffectConstants;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.ImageButton;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import android.service.notification.StatusBarNotification;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.Ticker;
import com.android.systemui.statusbar.tablet.TabletTicker;

public class Halo extends FrameLayout implements Ticker.TickerCallback, TabletTicker.TabletTickerCallback {

    public static final String TAG = "HaloLauncher";

    enum State {
        FIRST_RUN,
        IDLE,
        HIDDEN,
        SILENT,
        DRAG,
        GESTURES
    }

    enum Gesture {
        NONE,
        TASK,
        UP1,
        UP2,
        DOWN1,
        DOWN2
    }

    private Context mContext;
    private PackageManager mPm;

    private Handler mHandler;
    private BaseStatusBar mBar;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private Vibrator mVibrator;
    private LayoutInflater mInflater;
    private INotificationManager mNotificationManager;
    private SettingsObserver mSettingsObserver;
    private GestureDetector mGestureDetector;
    private KeyguardManager mKeyguardManager;
    private BroadcastReceiver mReceiver;

    private HaloEffect mEffect;
    private WindowManager.LayoutParams mTriggerPos;
    private State mState = State.IDLE;
    private Gesture mGesture = Gesture.NONE;

    private View mRoot;
    private View mContent, mHaloContent;
    private INotificationListener mHaloListener;
    private ComponentName mHaloComponent;
    private NotificationData.Entry mLastNotificationEntry = null;
    private NotificationData.Entry mCurrentNotficationEntry = null;
    private NotificationClicker mContentIntent, mTaskIntent;
    private NotificationData mNotificationData;
    private String mNotificationText = "";

    private Paint mPaintHolo = new Paint();
    private Paint mPaintHoloCustom = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintHoloRed = new Paint();

    private boolean isBeingDragged = false;
    private boolean mAttached = false;
    private boolean mHapticFeedback;
    private boolean mHideTicker;
    private boolean mEnableColor;
    private boolean mFirstStart = true;
    private boolean mInitialized = false;
    private boolean mTickerLeft = true;
    private boolean mIsNotificationNew = true;
    private boolean mOverX = false;
    private boolean mInteractionReversed = true;

    private int mIconSize, mIconHalfSize;
    private int mScreenWidth, mScreenHeight;
    private int mKillX, mKillY;
    private int mMarkerIndex = -1;
    private int mDismissDelay = 100;

    private int oldIconIndex = -1;
    private float initialX = 0;
    private float initialY = 0;
    private float mHaloSize = 1.0f;
    private boolean hiddenState = false;

    // Halo dock position
    SharedPreferences preferences;
    private String KEY_HALO_POSITION_Y = "halo_position_y";
    private String KEY_HALO_POSITION_X = "halo_position_x";
    private String KEY_HALO_FIRST_RUN = "halo_first_run";


    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_REVERSED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_HIDE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HAPTIC_FEEDBACK_ENABLED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_COLORS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_EFFECT_COLOR), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver cr = mContext.getContentResolver();

            mEnableColor = Settings.System.getInt(cr,
                   Settings.System.HALO_COLORS, 0) == 1;

            mInteractionReversed = Settings.System.getInt(cr, Settings.System.HALO_REVERSED, 1) == 1;
            mHideTicker = Settings.System.getInt(cr, Settings.System.HALO_HIDE, 0) == 1;

            if (!selfChange) {
                mEffect.wake();
                if (mEnableColor) {
                    mEffect.ping(mPaintHoloCustom, HaloEffect.WAKE_TIME);
                } else {
                    mEffect.ping(mPaintHolo, HaloEffect.WAKE_TIME);
                }
                mEffect.nap(HaloEffect.SNAP_TIME + 1000);
                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
            }
        }
    }

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mNotificationManager = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mDisplay = mWindowManager.getDefaultDisplay();
        mGestureDetector = new GestureDetector(mContext, new GestureListener());
        mHandler = new Handler();
        mRoot = this;
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);

        filter.addAction(Intent.ACTION_USER_PRESENT);

        mReceiver = new ScreenReceiver();
        mContext.registerReceiver(mReceiver, filter);

        // Init variables
        mInteractionReversed =
                Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_REVERSED, 1) == 1;
        mHideTicker =
                Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_HIDE, 0) == 1;
        mHaloSize = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        mIconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * mHaloSize);
        mIconHalfSize = mIconSize / 2;
        mTriggerPos = getWMParams();

        // Init colors
        int color = Settings.System.getInt(mContext.getContentResolver(),
               Settings.System.HALO_EFFECT_COLOR, 0xFF33B5E5);

        mPaintHoloCustom.setAntiAlias(true);
        mPaintHoloCustom.setColor(color);
        mPaintHolo.setAntiAlias(true);
        mPaintHolo.setColor(getResources().getColor(R.color.halo_ping_color));
        mPaintWhite.setAntiAlias(true);
        mPaintWhite.setColor(0xfff0f0f0);
        mPaintHoloRed.setAntiAlias(true);
        mPaintHoloRed.setColor(0xffcc0000);

        // Create effect layer
        mEffect = new HaloEffect(mContext);
        mEffect.setLayerType (View.LAYER_TYPE_HARDWARE, null);
        mEffect.pingMinRadius = mIconHalfSize;
        mEffect.pingMaxRadius = (int)(mIconSize * 1.1f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        mWindowManager.addView(mEffect, lp);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
            mSettingsObserver.onChange(true);
        }
        mHandler.postDelayed(new Runnable() {
            public void run() {
                final int c = getHaloMsgCount()-getHidden() < 0 ? 0 : getHaloMsgCount()-getHidden();
                mEffect.animateHaloBatch(0, c, false, 500, HaloProperties.MessageType.MESSAGE);
            }
        }, 2500);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    }

    private void initControl() {
        if (mInitialized) return;

        mInitialized = true;

        // Get actual screen size
        mScreenWidth = mEffect.getWidth();
        mScreenHeight = mEffect.getHeight();

        // Halo dock position
        preferences = mContext.getSharedPreferences("Halo", 0);
        int msavePositionX = preferences.getInt(KEY_HALO_POSITION_X, 0);
        int msavePositionY = preferences.getInt(KEY_HALO_POSITION_Y, mScreenHeight / 2 - mIconHalfSize);

        if (preferences.getBoolean(KEY_HALO_FIRST_RUN, true)) {
            mState = State.FIRST_RUN;
            preferences.edit().putBoolean(KEY_HALO_FIRST_RUN, false).apply();
        }

        mKillX = mScreenWidth / 2;
        mKillY = mIconHalfSize;

        if (!mFirstStart) {
            if (msavePositionY < 0) mEffect.setHaloY(0);
            float mTmpHaloY = (float) msavePositionY / mScreenWidth * (mScreenHeight);
            if (msavePositionY > mScreenHeight-mIconSize) {
                mEffect.setHaloY((int)mTmpHaloY);
            } else {
                mEffect.setHaloY(isLandscapeMod() ? msavePositionY : (int)mTmpHaloY);
            }

            if (mState == State.HIDDEN || mState == State.SILENT) {
                mEffect.setHaloX((int)(mTickerLeft ? -mIconSize*0.8f : mScreenWidth - mIconSize*0.2f));
                final int triggerWidth = (int)(mTickerLeft ? -mIconSize*0.7f : mScreenWidth - mIconSize*0.3f);
                updateTriggerPosition(triggerWidth, mEffect.mHaloY);
            } else {
                mEffect.nap(500);
                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
            }
        } else {
            // Do the startup animations only once
            mFirstStart = false;
            // Halo dock position
            mTickerLeft = msavePositionX == 0 ? true : false;
            updateTriggerPosition(msavePositionX, msavePositionY);
            mEffect.updateResources(mTickerLeft);
            mEffect.setHaloY(msavePositionY);

            if (mState == State.FIRST_RUN) {
                mEffect.setHaloX(msavePositionX + (mTickerLeft ? -mIconSize : mIconSize));
                mEffect.setHaloOverlay(HaloProperties.Overlay.MESSAGE, 1f);
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mEffect.wake();
                        mEffect.ticker(mContext.getResources().getString(R.string.halo_tutorial1), 0, 3000);
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                mEffect.ticker(mContext.getResources().getString(R.string.halo_tutorial2), 0, 3000);
                                mHandler.postDelayed(new Runnable() {
                                    public void run() {
                                        mEffect.ticker(mContext.getResources().getString(R.string.halo_tutorial3), 0, 3000);
                                        mHandler.postDelayed(new Runnable() {
                                            public void run() {
                                                mState = State.IDLE;
                                                mEffect.nap(0);
                                                mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                                                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME
                                                        + 2500, HaloEffect.SLEEP_TIME, false);
                                            }}, 6000);
                                    }}, 6000);
                            }}, 6000);
                    }}, 1000);
            } else {
                mEffect.setHaloX(msavePositionX);
                mEffect.nap(500);
                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
            }
        }
    }

    private boolean isLandscapeMod() {
        return mScreenWidth < mScreenHeight;
    }

    public void update() {
        if (mEffect != null) mEffect.invalidate();
    }

    private void updateTriggerPosition(int x, int y) {
        try {
            mTriggerPos.x = x;
            mTriggerPos.y = y;
            mWindowManager.updateViewLayout(mRoot, mTriggerPos);
        } catch(Exception e) {
            // Probably some animation still looking to move stuff around
        }
    }

    private void loadLastNotification(boolean includeCurrentDismissable) {
        if (getHaloMsgCount() > 0) {
            mLastNotificationEntry = mNotificationData.get(getHaloMsgIndex(getHaloMsgCount() - 1, false));

            // If the current notification is dismissable we might want to skip it if so desired
            if (!includeCurrentDismissable) {
                if (getHaloMsgCount() > 1 && mLastNotificationEntry != null &&
                        mCurrentNotficationEntry != null &&
                        mLastNotificationEntry.notification == mCurrentNotficationEntry.notification) {
                    if (mLastNotificationEntry.notification.isClearable()) {
                        mLastNotificationEntry = mNotificationData.get(getHaloMsgIndex(getHaloMsgCount() - 2, false));
                    }
                } else if (getHaloMsgCount() == 1) {
                    if (mLastNotificationEntry.notification.isClearable()) {
                        // We have one notification left and it is dismissable, clear it...
                        clearTicker();
                        return;
                    }
                }
            }

            if (mLastNotificationEntry.notification != null
                    && mLastNotificationEntry.notification.getNotification() != null
                    && mLastNotificationEntry.notification.getNotification().tickerText != null) {
                mNotificationText = mLastNotificationEntry.notification.getNotification().tickerText.toString();
            }

            tick(mLastNotificationEntry, 0, 0, false, false);
        } else {
            clearTicker();
        }
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
        mHaloComponent = new ComponentName("HaloComponent", "Halo.java");
        mHaloListener = new HaloReceiver();
        try {
            mNotificationManager.registerListener(mHaloListener, mHaloComponent, 0);
        } catch (android.os.RemoteException ex) {
            // failed to register listener
        }
        if(ExtendedPropertiesUtils.isTablet()) {
            if (mBar.getTabletTicker() != null) mBar.getTabletTicker().setUpdateEvent(this);
        } else {
            if (mBar.getTicker() != null) mBar.getTicker().setUpdateEvent(this);
        }
        mNotificationData = mBar.getNotificationData();
        loadLastNotification(true);
    }

    void launchTask(NotificationClicker intent) {
        // Do not launch tasks in hidden state or protected lock screen
        if (mState == State.HIDDEN || mState == State.SILENT
                || (mKeyguardManager.isKeyguardLocked() && mKeyguardManager.isKeyguardSecure())) return;

        try {
            ActivityManagerNative.getDefault().resumeAppSwitches();
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            // ...
        }
        mDismissDelay = 1500;

        if (intent!= null) {
            intent.onClick(mRoot);
        }
    }

    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp (MotionEvent event) {
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                float velocityX, float velocityY) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            if (mState != State.DRAG) {
                launchTask(mContentIntent);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (!mInteractionReversed) {
                mState = State.GESTURES;
                mEffect.wake();
                mBar.setHaloTaskerActive(true, true);
            } else {
                // Move
                mState = State.DRAG;
                mEffect.intro();
            }
            return true;
        }
    }

    void resetIcons() {
        final float originalAlpha = mContext.getResources().getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            entry.icon.setAlpha(originalAlpha);
        }
    }

    void setIcon(int index) {
        float originalAlpha = mContext.getResources().getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            float alpha = index == i ? 1f : originalAlpha;

            // Persistent notification appear muted
            if (!entry.notification.isClearable() && index != i) alpha /= 2;
            entry.icon.setAlpha(alpha);
        }
    }

    private boolean verticalGesture() {
        return (mGesture == Gesture.UP1
                || mGesture == Gesture.DOWN1
                || mGesture == Gesture.UP2
                || mGesture == Gesture.DOWN2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        mEffect.onTouchEvent(event);

        // Prevent any kind of interaction while HALO explains itself
        if (mState == State.FIRST_RUN) return true;

        mGestureDetector.onTouchEvent(event);

        final int action = event.getAction();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                // Stop HALO from moving around, unschedule sleeping patterns
                if (mState != State.GESTURES) mEffect.unscheduleSleep();

                mMarkerIndex = -1;
                oldIconIndex = -1;

                resetIcons();

                mGesture = Gesture.NONE;
                hiddenState = (mState == State.HIDDEN || mState == State.SILENT);
                if (hiddenState) {
                    mEffect.wake();
                    if (mHideTicker) {
                        mEffect.sleep(2500, HaloEffect.SLEEP_TIME, false);
                    } else {
                        mEffect.nap(2500);
                    }
                    return true;
                }

                initialX = event.getRawX();
                initialY = event.getRawY();
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (hiddenState) break;

                resetIcons();
                mBar.setHaloTaskerActive(false, true);
                mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                updateTriggerPosition(mEffect.getHaloX(), mEffect.getHaloY());

                mEffect.outro();
                mEffect.killTicker();
                mEffect.unscheduleSleep();

                // Do we erase ourselves?
                if (mOverX) {
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.HALO_ACTIVE, 0);
                    try {
                        mNotificationManager.unregisterListener(mHaloListener,0);
                    } catch (android.os.RemoteException ex) {
                        // Failed to un-register listener
                    }
                    return true;
                }

                // Halo dock position
                float mTmpHaloY = (float) mEffect.mHaloY / mScreenHeight * mScreenWidth;
                preferences.edit().putInt(KEY_HALO_POSITION_X, mTickerLeft ?
                        0 : mScreenWidth - mIconSize).putInt(KEY_HALO_POSITION_Y, isLandscapeMod() ?
                        mEffect.mHaloY : (int)mTmpHaloY).apply();

                if (mGesture == Gesture.TASK) {
                    // Launch tasks
                    if (mTaskIntent != null) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        launchTask(mTaskIntent);
                    }
                    mEffect.nap(100);
                    if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 1500, HaloEffect.SLEEP_TIME, false);

                } else if (mGesture == Gesture.DOWN2) {
                    // Hide & silence
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEffect.sleep(0, HaloEffect.NAP_TIME / 2, true);

                } else if (mGesture == Gesture.DOWN1) {
                    // Hide from sight
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEffect.sleep(0, HaloEffect.NAP_TIME / 2, false);

                } else if (mGesture == Gesture.UP2) {
                    // Clear all notifications
                    playSoundEffect(SoundEffectConstants.CLICK);

                    if (getHaloMsgCount()-getHidden() < 1) {
                        mEffect.nap(1500);
                        if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 3000, HaloEffect.SLEEP_TIME, false);
                    }
                    try {
                        mDismissDelay = 0;
                        mBar.getStatusBarService().onClearAllNotifications();
                    } catch (RemoteException ex) {
                        // system process is dead if we're here.
                    }

                } else if (mGesture == Gesture.UP1) {
                    // Dismiss notification
                    playSoundEffect(SoundEffectConstants.CLICK);

                    if (getHaloMsgCount()-getHidden() < 1) {
                        mEffect.nap(1500);
                        if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 3000, HaloEffect.SLEEP_TIME, false);
                    }
                    if (mContentIntent != null) {
                        try {
                            mDismissDelay = 0;
                            mBar.getStatusBarService().onNotificationClear(mContentIntent.mPkg, mContentIntent.mTag, mContentIntent.mId);
                        } catch (RemoteException ex) {
                            // system process is dead if we're here.
                        }
                    }
                } else {
                    // No gesture, just snap HALO
                    mEffect.snap(0);
                    mEffect.nap(HaloEffect.SNAP_TIME + 1000);
                    if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
                }

                mState = State.IDLE;
                mGesture = Gesture.NONE;
                break;

            case MotionEvent.ACTION_MOVE:
                if (hiddenState) break;

                float distanceX = mKillX-event.getRawX();
                float distanceY = mKillY-event.getRawY();
                float distanceToKill = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                distanceX = initialX-event.getRawX();
                distanceY = initialY-event.getRawY();
                float initialDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                if (mState != State.GESTURES) {
                    // Check kill radius
                    if (distanceToKill < mIconSize) {
                        // Magnetize X
                        mEffect.setHaloX((int)mKillX - mIconHalfSize);
                        mEffect.setHaloY((int)(mKillY - mIconHalfSize));

                        if (!mOverX) {
                            if (mHapticFeedback) mVibrator.vibrate(25);
                            mEffect.ping(mPaintHoloRed, 0);
                            mEffect.setHaloOverlay(HaloProperties.Overlay.BLACK_X, 1f);
                            mOverX = true;
                        }

                        return false;
                    } else {
                        if (mOverX) mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                        mOverX = false;
                    }

                    // Drag
                    if (mState != State.DRAG) {
                        if (initialDistance > mIconSize * 0.7f) {
                            if (mInteractionReversed) {
                                mState = State.GESTURES;
                                mEffect.wake();
                                mBar.setHaloTaskerActive(true, true);
                            } else {
                                mState = State.DRAG;
                                mEffect.intro();
                                if (mHapticFeedback) mVibrator.vibrate(25);
                            }
                        }
                    } else {
                        int posX = (int)event.getRawX() - mIconHalfSize;
                        int posY = (int)event.getRawY() - mIconHalfSize;
                        if (posX < 0) posX = 0;
                        if (posY < 0) posY = 0;
                        if (posX > mScreenWidth-mIconSize) posX = mScreenWidth-mIconSize;
                        if (posY > mScreenHeight-mIconSize) posY = mScreenHeight-mIconSize;
                        mEffect.setHaloX(posX);
                        mEffect.setHaloY(posY);

                        // Update resources when the side changes
                        boolean oldTickerPos = mTickerLeft;
                        mTickerLeft = (posX + mIconHalfSize < mScreenWidth / 2);
                        if (oldTickerPos != mTickerLeft) {
                            mEffect.updateResources(mTickerLeft);
                        }
                    }
                } else {
                    // We have three basic gestures, one horizontal for switching through tasks and
                    // two vertical for dismissing tasks or making HALO fall asleep
                    int deltaX = (int)(mTickerLeft ? event.getRawX() : mScreenWidth - event.getRawX());
                    int deltaY = (int)(mEffect.getHaloY() - event.getRawY() + mIconSize);
                    int horizontalThreshold = (int)(mIconSize * 1.5f);
                    int verticalThreshold = (int)(mIconSize * 0.25f);
                    int verticalSteps = (int)(mIconSize * 0.7f);
                    String gestureText = mNotificationText;
                    Gesture oldGesture = Gesture.NONE;

                    // Switch icons
                    if (deltaX > horizontalThreshold) {
                        if (mGesture != Gesture.TASK) mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);

                        oldGesture = mGesture;
                        mGesture = Gesture.TASK;

                        deltaX -= horizontalThreshold;
                        if (mNotificationData != null && getHaloMsgCount() > 0) {
                            int items = getHaloMsgCount();

                            // This will be the length we are going to use
                            int indexLength = ((int)(mScreenWidth * 0.85f) - mIconSize) / items;

                            // Set a standard (max) distance for markers.
                            indexLength = indexLength > 120 ? 120 : indexLength;

                            // Calculate index
                            mMarkerIndex = mTickerLeft ? (items - deltaX / indexLength) - 1 : (deltaX / indexLength);

                            // Watch out for margins!
                            if (mMarkerIndex >= items) mMarkerIndex = items - 1;
                            if (mMarkerIndex < 0) mMarkerIndex = 0;
                        }

                    // Up & down gestures
                    } else if (Math.abs(deltaY) > verticalThreshold * 2) {
                        mMarkerIndex = -1;

                        boolean gestureChanged = false;
                        final int deltaIndex = (Math.abs(deltaY) - verticalThreshold) / verticalSteps;

                        if (deltaIndex < 1 && mGesture != Gesture.NONE) {
                            // Dead zone buffer to prevent accidental notifiction dismissal
                            gestureChanged = true;
                            mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            if (verticalGesture()) gestureText = "";
                            oldGesture = mGesture;
                            mGesture = Gesture.NONE;
                        } else if (deltaY > 0) {
                            if (deltaIndex == 1 && mGesture != Gesture.UP1) {
                                oldGesture = mGesture;
                                mGesture = Gesture.UP1;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.DISMISS, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_dismiss);
                            } else if (deltaIndex > 1 && mGesture != Gesture.UP2) {
                                oldGesture = mGesture;
                                mGesture = Gesture.UP2;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.CLEAR_ALL, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_clear_all);
                            }
                        } else {
                            if (deltaIndex == 1 && mGesture != Gesture.DOWN1) {
                                oldGesture = mGesture;
                                mGesture = Gesture.DOWN1;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(mTickerLeft ? HaloProperties.Overlay.BACK_LEFT
                                        : HaloProperties.Overlay.BACK_RIGHT, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_hide);
                            } else if (deltaIndex > 1 && mGesture != Gesture.DOWN2) {
                                oldGesture = mGesture;
                                mGesture = Gesture.DOWN2;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(mTickerLeft ? HaloProperties.Overlay.SILENCE_LEFT
                                        : HaloProperties.Overlay.SILENCE_RIGHT, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_silence);
                            }
                        }

                        if (gestureChanged) {
                            mMarkerIndex = -1;
                            mEffect.ticker(gestureText, 0, 250);
                            if (mHapticFeedback) mVibrator.vibrate(10);
                            gestureChanged = false;
                        }

                    } else {
                        mMarkerIndex = -1;

                        if (mGesture != Gesture.NONE) {
                            mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            if (verticalGesture()) mEffect.killTicker();
                        }
                        oldGesture = mGesture;
                        mGesture = Gesture.NONE;
                    }

                    // If the marker index changed, tick
                    if (mMarkerIndex != oldIconIndex) {
                        oldIconIndex = mMarkerIndex;

                        // Make a tiny pop if not so many icons are present
                        if (mHapticFeedback && getHaloMsgCount() < 10) mVibrator.vibrate(10);

                        int iconIndex = getHaloMsgIndex(mMarkerIndex, false);
                        try {
                            // Tick the first item only if we were tasking before
                            if (iconIndex == -1 && !verticalGesture() && oldGesture == Gesture.TASK) {
                                mTaskIntent = null;
                                resetIcons();
                                tick(mLastNotificationEntry, 0, -1, false, true);
                            } else {
                                setIcon(iconIndex);
                                NotificationData.Entry entry = mNotificationData.get(iconIndex);
                                tick(entry, 0, -1, false, true);
                                mTaskIntent = entry.getFloatingIntent();
                            }
                        } catch (Exception e) {
                            // IndexOutOfBoundsException
                        }
                    }
                }
                mEffect.invalidate();
                break;
        }
        return false;
    }

    public void cleanUp() {
        // Remove pending tasks, if we can
        mEffect.unscheduleSleep();
        mHandler.removeCallbacksAndMessages(null);
        // Kill callback
        if(ExtendedPropertiesUtils.isTablet()) {
            if (mBar.getTabletTicker() != null) mBar.getTabletTicker().setUpdateEvent(null);
        } else {
             mBar.getTicker().setUpdateEvent(null);
        }
        // Flag tasker
        mBar.setHaloTaskerActive(false, false);
        // Kill the effect layer
        if (mEffect != null) mWindowManager.removeView(mEffect);
        // Remove resolver
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        mContext.unregisterReceiver(mReceiver);
    }

    class HaloEffect extends HaloProperties {

        public static final int WAKE_TIME = 300;
        public static final int SNAP_TIME = 300;
        public static final int NAP_TIME = 1000;
        public static final int SLEEP_TIME = 2000;
        public static final int PING_TIME = 1500;
        public static final int PULSE_TIME = 1500;
        public static final int TICKER_HIDE_TIME = 2500;
        public static final int NAP_DELAY = 4500;
        public static final int SLEEP_DELAY = 6500;

        private Context mContext;
        private Paint mPingPaint;
        private int pingRadius = 0;
        private int mPingX, mPingY;
        protected int pingMinRadius = 0;
        protected int pingMaxRadius = 0;
        private boolean mPingAllowed = true;

        private Bitmap mMarker, mMarkerT, mMarkerB;
        private Bitmap mBigRed;
        private Paint mMarkerPaint = new Paint();
        private Paint xPaint = new Paint();

        CustomObjectAnimator xAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator tickerAnimator = new CustomObjectAnimator(this);

        public HaloEffect(Context context) {
            super(context);

            mContext = context;
            setWillNotDraw(false);
            setDrawingCacheEnabled(false);

            mBigRed = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_bigred);
            mMarker = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker);
            mMarkerT = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_t);
            mMarkerB = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_b);

            if (mHaloSize != 1.0f) {
                mBigRed = Bitmap.createScaledBitmap(mBigRed, (int)(mBigRed.getWidth() * mHaloSize),
                        (int)(mBigRed.getHeight() * mHaloSize), true);
                mMarker = Bitmap.createScaledBitmap(mMarker, (int)(mMarker.getWidth() * mHaloSize),
                        (int)(mMarker.getHeight() * mHaloSize), true);
                mMarkerT = Bitmap.createScaledBitmap(mMarkerT, (int)(mMarkerT.getWidth() * mHaloSize),
                        (int)(mMarkerT.getHeight() * mHaloSize), true);
                mMarkerB = Bitmap.createScaledBitmap(mMarkerB, (int)(mMarkerB.getWidth() * mHaloSize),
                        (int)(mMarkerB.getHeight() * mHaloSize), true);
            }

            mMarkerPaint.setAntiAlias(true);
            mMarkerPaint.setAlpha(0);
            xPaint.setAntiAlias(true);
            xPaint.setAlpha(0);

            updateResources(mTickerLeft);
        }

        void getRawPoint(MotionEvent ev, int index, PointF point){
            final int location[] = { 0, 0 };
            mRoot.getLocationOnScreen(location);

            float x=ev.getX(index);
            float y=ev.getY(index);

            double angle=Math.toDegrees(Math.atan2(y, x));
            angle+=mRoot.getRotation();

            final float length=PointF.length(x,y);

            x=(float)(length*Math.cos(Math.toRadians(angle)))+location[0];
            y=(float)(length*Math.sin(Math.toRadians(angle)))+location[1];

            point.set((int)x,(int)y);
        }

        boolean browseView(PointF loc, Rect parent, View v) {
            int posX = (int)loc.x;
            int posY = (int)loc.y - mIconHalfSize / 2;

            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup)v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View sv = vg.getChildAt(i);
                    if (browseView(loc, parent, sv)) return true;
                }
            } else {
                if (v.isClickable()) {
                    Rect r = new Rect();
                    v.getHitRect(r);

                    int left = tickerX + parent.left + r.left;
                    int top = tickerY + parent.top + r.top;
                    int right = tickerX + parent.left + r.right;
                    int bottom = tickerY + parent.top + r.bottom;

                    if (posX > left && posX < right && posY > top && posY < bottom) {
                        v.performClick();
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (mHapticFeedback) mVibrator.vibrate(25);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP
                    && event.getActionIndex() == 1 ) {

                if (mCurrentNotficationEntry != null && mCurrentNotficationEntry.haloContent != null) {
                    Rect rootRect = new Rect();
                    mHaloTickerContent.getHitRect(rootRect);

                    PointF point = new PointF();
                    getRawPoint(event, 1, point);
                    browseView(point, rootRect, mCurrentNotficationEntry.haloContent);
                }
            }
            return false;
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            onConfigurationChanged(null);
        }

        @Override
        public void onConfigurationChanged(Configuration newConfiguration) {
            // This will reset the initialization flag
            mInitialized = false;
            // Generate a new content bubble
            updateResources(mTickerLeft);
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
            super.onLayout (changed, left, top, right, bottom);
            // We have our effect-layer, now let's kickstart HALO
            initControl();
        }

        public void killTicker() {
            tickerAnimator.animate(ObjectAnimator.ofFloat(this, "haloContentAlpha", 0f).setDuration(250),
                    new DecelerateInterpolator(), null);
        }

        public void ticker(String tickerText, int delay, int startDuration) {
            if (tickerText == null || tickerText.equals("")) {
                killTicker();
                return;
            }

            setHaloContentHeight((int)(mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height) * 0.7f));
            mHaloTickerContent.setVisibility(View.GONE);
            mHaloTextView.setVisibility(View.VISIBLE);
            mHaloTextView.setText(tickerText);
            updateResources(mTickerLeft);

            float total = TICKER_HIDE_TIME + startDuration + 1000;
            PropertyValuesHolder tickerUpFrames = PropertyValuesHolder.ofKeyframe("haloContentAlpha",
                    Keyframe.ofFloat(0f, mHaloTextView.getAlpha()),
                    Keyframe.ofFloat(0.1f, 1f),
                    Keyframe.ofFloat(0.95f, 1f),
                    Keyframe.ofFloat(1f, 0f));
            tickerAnimator.animate(ObjectAnimator.ofPropertyValuesHolder(this, tickerUpFrames).setDuration((int)total),
                    new DecelerateInterpolator(), null, delay, null);
        }

        public void ticker(int delay, int startDuration) {

            setHaloContentHeight(mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height));
            mHaloTickerContent.setVisibility(View.VISIBLE);
            mHaloTextView.setVisibility(View.GONE);
            updateResources(mTickerLeft);

            if (startDuration != -1) {
                // Finite tiker
                float total = TICKER_HIDE_TIME + startDuration + 1000;
                PropertyValuesHolder tickerUpFrames = PropertyValuesHolder.ofKeyframe("haloContentAlpha",
                        Keyframe.ofFloat(0f, mHaloTextView.getAlpha()),
                        Keyframe.ofFloat(0.1f, 1f),
                        Keyframe.ofFloat(0.95f, 1f),
                        Keyframe.ofFloat(1f, 0f));
                tickerAnimator.animate(ObjectAnimator.ofPropertyValuesHolder(this, tickerUpFrames).setDuration((int)total),
                        new DecelerateInterpolator(), null, delay, null);
            } else {
                // Infinite ticker (until killTicker() is called)
                tickerAnimator.animate(ObjectAnimator.ofFloat(this, "haloContentAlpha", 1f).setDuration(250),
                    new DecelerateInterpolator(), null, delay, null);
            }
        }

        public void ping(final Paint paint, final long delay) {
            if ((!mPingAllowed && paint != mPaintHoloRed)
                    && mGesture != Gesture.TASK) return;

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mPingAllowed = false;

                    mPingX = mHaloX + mIconHalfSize;
                    mPingY = mHaloY + mIconHalfSize;

                    mPingPaint = paint;

                    CustomObjectAnimator pingAnimator = new CustomObjectAnimator(mEffect);
                    pingAnimator.animate(ObjectAnimator.ofInt(mPingPaint, "alpha", 200, 0).setDuration(PING_TIME),
                            new DecelerateInterpolator(), new AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    pingRadius = (int)((pingMaxRadius - pingMinRadius) *
                                            animation.getAnimatedFraction()) + pingMinRadius;
                                    invalidate();
                                }});

                    // prevent ping spam
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            mPingAllowed = true;
                        }}, PING_TIME / 2);

                }}, delay);
        }

        public void intro() {
            xAnimator.animate(ObjectAnimator.ofInt(xPaint, "alpha", 255).setDuration(PING_TIME / 3),
                    new DecelerateInterpolator(), null);
        }

        public void outro() {
            xAnimator.animate(ObjectAnimator.ofInt(xPaint, "alpha", 0).setDuration(PING_TIME / 3),
                    new AccelerateInterpolator(), null);
        }

        CustomObjectAnimator snapAnimator = new CustomObjectAnimator(this);

        public void wake() {
            unscheduleSleep();
            if (mState == State.HIDDEN || mState == State.SILENT) mState = State.IDLE;
            int newPos = mTickerLeft ? 0 : mScreenWidth - mIconSize;
            updateTriggerPosition(newPos, mHaloY);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(WAKE_TIME),
                    new DecelerateInterpolator(), null);
        }

        public void snap(long delay) {
            int newPos = mTickerLeft ? 0 : mScreenWidth - mIconSize;
            updateTriggerPosition(newPos, mHaloY);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(SNAP_TIME),
                    new DecelerateInterpolator(), null, delay, null);
        }

        public void nap(long delay) {
            final int newPos = mTickerLeft ? -mIconHalfSize : mScreenWidth - mIconHalfSize;
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(NAP_TIME),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                        public void run() {
                            updateTriggerPosition(newPos, mHaloY);
                        }});
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    if (mState != State.GESTURES && mState != State.DRAG) {
                        final int c = getHaloMsgCount()-getHidden() < 0 ? 0 : getHaloMsgCount()-getHidden();
                        mEffect.animateHaloBatch(0, c, false, 3000, HaloProperties.MessageType.MESSAGE);
                    }
                }
            }, 2000);
        }

        public void sleep(long delay, int speed, final boolean silent) {
            final int newPos = (int)(mTickerLeft ? -mIconSize*0.8f : mScreenWidth - mIconSize*0.2f);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(speed),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                        public void run() {
                            mState = silent ? State.SILENT : State.HIDDEN;
                            final int triggerWidth = (int)(mTickerLeft ? -mIconSize*0.7f : mScreenWidth - mIconSize*0.3f);
                            updateTriggerPosition(triggerWidth, mHaloY);
                        }});
        }

        public void unscheduleSleep() {
            snapAnimator.cancel(true);
        }

        CustomObjectAnimator contentYAnimator = new CustomObjectAnimator(this);

        int tickerX, tickerY;

        @Override
        protected void onDraw(Canvas canvas) {
            int state;

            // Ping
            if (mPingPaint != null) {
                canvas.drawCircle(mPingX, mPingY, pingRadius, mPingPaint);
            }

            // Content
            state = canvas.save();
            final int tickerHeight = mHaloTickerWrapper.getMeasuredHeight();
            int ch = mGesture == Gesture.TASK ? 0 : tickerHeight / 2;
            int cw = mHaloTickerWrapper.getMeasuredWidth();
            int y = mHaloY + mIconHalfSize - ch;

            if (mGesture == Gesture.TASK) {
                if (mHaloY < mIconHalfSize) {
                    y = y + (int)(mIconSize * 0.20f);
                } else {
                    y = y - mIconSize;
                }
            }

            int x = mHaloX + mIconSize + (int)(mIconSize * 0.1f);
            if (!mTickerLeft) {
                x = mHaloX - cw - (int)(mIconSize * 0.1f);
            }

            // X
            float fraction = 1 - ((float)xPaint.getAlpha()) / 255;
            int killyPos = (int)(mKillY - mBigRed.getWidth() / 2 - mIconSize * fraction);
            canvas.drawBitmap(mBigRed, mKillX - mBigRed.getWidth() / 2, killyPos, xPaint);

            // Horizontal Marker
            if (mGesture == Gesture.TASK) {
                if (y > 0 && mNotificationData != null && getHaloMsgCount() > 0) {
                    int pulseY = mHaloY + mIconHalfSize - mMarker.getHeight() / 2;
                    int items = getHaloMsgCount();
                    int indexLength = ((int)(mScreenWidth * 0.85f) - mIconSize) / items;

                    indexLength = indexLength > 120 ? 120 : indexLength;

                    for (int i = 0; i < items; i++) {
                        float pulseX = mTickerLeft ? (mIconSize * 1.3f + indexLength * i)
                                : (mScreenWidth - mIconSize * 1.3f - indexLength * i - mMarker.getWidth());
                        boolean markerState = mTickerLeft ? mMarkerIndex >= 0 && i < items-mMarkerIndex : i <= mMarkerIndex;
                        mMarkerPaint.setAlpha(markerState ? 255 : 100);
                        canvas.drawBitmap(mMarker, pulseX, pulseY, mMarkerPaint);
                    }
                }
            }

            // Vertical Markers
            if (verticalGesture()) {
                int xPos = mHaloX + mIconHalfSize - mMarkerT.getWidth() / 2;

                mMarkerPaint.setAlpha(mGesture == Gesture.UP1 ? 255 : 100);
                int yTop = (int)(mHaloY - (mIconSize * 0.25f) - mMarkerT.getHeight() / 2);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == Gesture.UP2 ? 255 : 100);
                yTop = yTop - (int)(mIconSize * 0.7f);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == Gesture.DOWN1 ? 255 : 100);
                int yButtom = (int)(mHaloY + mIconSize + (mIconSize * 0.25f) - mMarkerT.getHeight() / 2);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == Gesture.DOWN2 ? 255 : 100);
                yButtom = yButtom + (int)(mIconSize * 0.7f);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);
            }

            if (mState == State.DRAG) {
                setHaloContentY(y);
            } else {
                if (y != getHaloContentY() && !contentYAnimator.isRunning()) {
                    setHaloContentBackground(mTickerLeft, mGesture == Gesture.TASK && mHaloY > mIconHalfSize
                            ? HaloProperties.ContentStyle.CONTENT_DOWN : HaloProperties.ContentStyle.CONTENT_UP);
                    contentYAnimator.animate(ObjectAnimator.ofInt(this, "HaloContentY", y).setDuration(300),
                            new DecelerateInterpolator(), null);
                }
            }

            if (getHaloContentAlpha() > 0.0f) {
                state = canvas.save();
                tickerX = x;
                tickerY = getHaloContentY();
                canvas.translate(x, getHaloContentY());
                mHaloContentView.draw(canvas);
                canvas.restoreToCount(state);
            }

            // Bubble
            state = canvas.save();
            canvas.translate(mHaloX, mHaloY);
            mHaloBubble.draw(canvas);
            canvas.restoreToCount(state);

            // Number
            if (mState == State.IDLE || mState == State.GESTURES) {
                state = canvas.save();
                canvas.translate(mTickerLeft ? mHaloX + mIconSize - mHaloNumber.getMeasuredWidth() : mHaloX, mHaloY);
                mHaloNumberView.draw(canvas);
                canvas.restoreToCount(state);
            }
        }
    }

    public WindowManager.LayoutParams getWMParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mIconSize,
                mIconSize,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        return lp;
    }

    void clearTicker() {
        mEffect.mHaloIcon.setImageDrawable(null);
        mEffect.msgNumberAlphaAnimator.cancel(true);
        mEffect.msgNumberFlipAnimator.cancel(true);
        mEffect.tickerAnimator.cancel(true);
        mEffect.mHaloNumber.setAlpha(0f);
        mEffect.mHaloCount.setAlpha(0f);
        mEffect.mHaloPinned.setAlpha(0f);
        mEffect.mHaloSystemIcon.setAlpha(0f);
        mEffect.mHaloNumberIcon.setAlpha(0f);
        mContentIntent = null;
        mCurrentNotficationEntry = null;
        mEffect.killTicker();
        mEffect.updateResources(mTickerLeft);
        mEffect.invalidate();
    }

    void tick(NotificationData.Entry entry, int delay, int duration, boolean alwaysFlip, boolean showContent) {
        if (entry == null) {
            clearTicker();
            return;
        }

        StatusBarNotification notification = entry.notification;
        Notification n = notification.getNotification();

        // Deal with the intent
        mContentIntent = entry.getFloatingIntent();
        mCurrentNotficationEntry = entry;

        // set the avatar
        mEffect.mHaloIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), entry.getRoundIcon()));

        if (showContent && mState != State.SILENT) {
            if (entry.haloContent != null) {
                try {
                    ((ViewGroup)mEffect.mHaloTickerContent).removeAllViews();
                    ((ViewGroup)mEffect.mHaloTickerContent).addView(entry.haloContent);
                    mEffect.ticker(delay, duration);
                } catch(Exception e) {
                    // haloContent had a view already? Let's give it one last chance ...
                    try {
                        mBar.prepareHaloNotification(entry, notification, false);
                        if (entry.haloContent != null) ((ViewGroup)mEffect.mHaloTickerContent).addView(entry.haloContent);
                        mEffect.ticker(delay, duration);
                    } catch(Exception ex) {
                        // Screw it, we're going with a simple text
                        mEffect.ticker(mNotificationText, delay, duration);
                    }
                }
            }
        }

        mEffect.invalidate();

        // Set Number
        HaloProperties.MessageType msgType;
        if (entry.notification.getPackageName().equals("com.paranoid.halo")) {
            msgType = HaloProperties.MessageType.PINNED;
        } else if (!entry.notification.isClearable()) {
            msgType = HaloProperties.MessageType.SYSTEM;
        } else {
            msgType = HaloProperties.MessageType.MESSAGE;
        }
        mEffect.animateHaloBatch(n.number, -1, alwaysFlip, delay, msgType);
    }

    public void updateTicker(StatusBarNotification notification) {
        loadLastNotification(true);
    }

    // This is the android ticker callback
    public void updateTicker(StatusBarNotification notification, String text) {

        boolean allowed = false; // default off
        try {
            allowed = mNotificationManager.isPackageAllowedForHalo(notification.getPackageName());
        } catch (android.os.RemoteException ex) {
            // System is dead
        }
        if (allowed) {
            for (int i = 0; i < mNotificationData.size(); i++) {
                NotificationData.Entry entry = mNotificationData.get(i);

                if (entry.notification == notification) {

                    // No intent, no tick ...
                    if (entry.notification.getNotification().contentIntent == null) return;

                    mIsNotificationNew = true;
                    if (mLastNotificationEntry != null && notification == mLastNotificationEntry.notification) {
                        // Ok, this is the same notification
                        // Let's give it a chance though, if the text has changed we allow it
                        mIsNotificationNew = !mNotificationText.equals(text);
                    }

                    if (mIsNotificationNew) {
                        mNotificationText = text;
                        mLastNotificationEntry = entry;

                        if (mState != State.FIRST_RUN) {
                            if (mState == State.IDLE || mState == State.HIDDEN) {
                                if (mState == State.HIDDEN) clearTicker();
                                mEffect.wake();
                                mEffect.nap(HaloEffect.NAP_DELAY + HaloEffect.WAKE_TIME * 2);
                                if (mHideTicker) mEffect.sleep(HaloEffect.SLEEP_DELAY + HaloEffect.WAKE_TIME * 2, HaloEffect.SLEEP_TIME, false);
                            }

                            tick(entry, HaloEffect.WAKE_TIME * 2, 1000, true, true);

                            // Pop while not tasking, only if notification is certified fresh
                            if (mEnableColor) {
                                if (mGesture != Gesture.TASK && mState != State.SILENT) mEffect.ping(mPaintHoloCustom, HaloEffect.WAKE_TIME * 2);
                            } else {
                                if (mGesture != Gesture.TASK && mState != State.SILENT) mEffect.ping(mPaintHolo, HaloEffect.WAKE_TIME * 2);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    public int getHaloMsgCount() {
        int msgs = 0;
        StatusBarNotification notification;

        for(int i = 0; i < mNotificationData.size(); i++) {
            notification = mNotificationData.get(i).notification;
            try {
                if (!mNotificationManager.isPackageAllowedForHalo(notification.getPackageName())) continue;
            } catch (android.os.RemoteException ex) {
                // System is dead
            }
            msgs += 1;
        }
        return msgs;
    }

    public int getHaloMsgIndex(int index, boolean notifyOnUnlock) {
        int msgIndex = 0;
        StatusBarNotification notification;

        for (int i = 0; i < mNotificationData.size(); i++){
            notification = mNotificationData.get(i).notification;
            try { //ignore blacklisted notifications
                if (!mNotificationManager.isPackageAllowedForHalo(notification.getPackageName())) continue;
            } catch (android.os.RemoteException ex) {
                // System is dead
            }
            //if notifying the user on unlock, ignore persistent notifications
            if (notifyOnUnlock && !notification.isClearable()) continue;

            if (msgIndex == index) return i;

            msgIndex += 1;
        }
        return -1;
    }

    public int getHidden() {
        int ignore = 0;
        boolean allowed = false;
        boolean persistent = false;

        for (int i = 0; i < mNotificationData.size(); i++ ) {
            NotificationData.Entry entry = mNotificationData.get(i);
            StatusBarNotification statusNotify = entry.notification;
            if (statusNotify == null) continue;

            try {
                allowed = mNotificationManager.isPackageAllowedForHalo(mNotificationData.get(i).notification.getPackageName());
            } catch (android.os.RemoteException ex) {
                // System is dead
            }
            persistent = !mNotificationData.get(i).notification.isClearable();
            // persistent notifications that were not blacklisted and pinned apps
            boolean hide = (statusNotify.getPackageName().equals("com.paranoid.halo") || (allowed && persistent));
            if (hide) ignore++;
        }
        return ignore;
    }

    private class HaloReceiver extends INotificationListener.Stub {

        public HaloReceiver() {
        }

        @Override
        public void onNotificationPosted(StatusBarNotification notification) throws RemoteException {
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification notification) throws RemoteException {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    // Find next entry
                    clearTicker();
                    mEffect.clearAnimation();
                    mNotificationText = "";
                    NotificationData.Entry entry = null;
                    if (getHaloMsgCount() > 0) {
                        for (int i = getHaloMsgCount()-1; i >= 0; i--) {
                            NotificationData.Entry item = mNotificationData.get(getHaloMsgIndex(i, false));
                            if (mCurrentNotficationEntry != null
                                    && mCurrentNotficationEntry.notification == item.notification) {
                                continue;
                            }
                            if (item.notification.isClearable()) {
                                entry = item;
                                break;
                            }
                        }
                    }
                    // When no entry was found, take the last one
                    if (entry == null && getHaloMsgCount() > 0) {
                        loadLastNotification(false);
                    } else {
                        tick(entry, 0, 0, false, false);
                    }
                    final int c = getHaloMsgCount()-getHidden() < 0 ? 0 : getHaloMsgCount()-getHidden();
                    mEffect.setHaloMessageNumber(c);
                    if (mState != State.HIDDEN) {
                        mEffect.nap(1500);
                        if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 3000, HaloEffect.SLEEP_TIME, false);
                    }
                }
            }, mDismissDelay);

            mDismissDelay = 100;
        }
    }

    public class ScreenReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // When screen unlocked, HALO active & Expanded desktop mode, ping HALO and load last notification.
            // Because notifications are not readily visible and HALO does not "tick" on protected lock screens
            if(intent.getAction().equals(Intent.ACTION_USER_PRESENT) &&
                    Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_ACTIVE, 0) == 1) {
                if (mKeyguardManager.isKeyguardSecure() ||
                        (Settings.System.getInt(mContext.getContentResolver(), Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1 &&
                                mState == State.HIDDEN)) {
                    mEffect.animateHaloBatch(0, 0, false, 0, HaloProperties.MessageType.MESSAGE);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            int lastMsg = getHaloMsgCount() - getHidden();
                            if (lastMsg > 0) {
                                NotificationData.Entry entry = mNotificationData.get(getHaloMsgIndex(lastMsg - 1, true));
                                mEffect.wake();
                                mEffect.nap(HaloEffect.NAP_DELAY + HaloEffect.WAKE_TIME * 2);
                                if (mHideTicker) mEffect.sleep(HaloEffect.SLEEP_DELAY + HaloEffect.WAKE_TIME * 2, HaloEffect.SLEEP_TIME, false);
                                tick(entry, HaloEffect.WAKE_TIME * 2, 1000, false, true);
                                 if (mEnableColor) {
                                     mEffect.ping(mPaintHoloCustom, HaloEffect.WAKE_TIME * 2);
                                 } else {
                                     mEffect.ping(mPaintHolo, HaloEffect.WAKE_TIME * 2);
                                 }
                            }
                        }
                    }, 400);
                }
            }
        }
    }
}
