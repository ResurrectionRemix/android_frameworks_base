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

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.ServiceManager;
import android.provider.Settings;
import android.service.gesture.EdgeGestureManager;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.gesture.EdgeGesturePosition;
import com.android.internal.util.gesture.EdgeServiceConstants;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;

/**
 * Pie Controller
 * Sets and controls the pie menu and associated pie items
 * Singleton must be initialized.
 */
public class PieController extends EdgeGestureManager.EdgeGestureActivationListener {

    static final String BACK_BUTTON = "back";
    static final String HOME_BUTTON = "home";
    static final String RECENT_BUTTON = "recent";

    /* Analogous to NAVBAR_ALWAYS_AT_RIGHT */
    static final boolean PIE_ALWAYS_AT_RIGHT = true;

    private static PieController sInstance;

    private AudioManager mAudioManager;
    private BaseStatusBar mBar;
    private Context mContext;
    private EdgeGestureManager mEdgeGestureManager;
    private Handler mHandler;
    private KeyguardManager mKeyguardManager;
    private PieItem mBack;
    private PieItem mHome;
    private PieItem mRecent;
    private PieMenu mPie;
    private Point mEdgeGestureTouchPos = new Point(0, 0);
    private WindowManager mWindowManager;

    private boolean mForcePieCentered;
    private boolean mPieAttached;
    private boolean mRelocatePieOnRotation;

    private int mOrientation;
    private int mWidth;
    private int mHeight;
    private int mRotation;

    private int mPiePosition;
    private int mInjectKeycode;

    private PieController() {
        super(Looper.getMainLooper());
    }

    public static PieController getInstance() {
        if (sInstance == null) {
            sInstance = new PieController();
        }
        return sInstance;
    }

    /**
     * Creates a new instance of pie
     *
     * @Param context the current Context
     * @Param wm the current Window Manager
     * @Param bar the current BaseStatusBar
     */
    public void init(Context context, WindowManager wm, BaseStatusBar bar) {
        mContext = context;
        mHandler = new Handler();
        mWindowManager = wm;
        mBar = bar;

        mOrientation = Gravity.BOTTOM;
        mRelocatePieOnRotation = mContext.getResources().getBoolean(
                R.bool.config_relocatePieOnRotation);
        mRotation = mWindowManager.getDefaultDisplay().getRotation();

        mEdgeGestureManager = EdgeGestureManager.getInstance();
        mEdgeGestureManager.setEdgeGestureActivationListener(this);
        mForcePieCentered = mContext.getResources().getBoolean(R.bool.config_forcePieCentered);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
    }

    public void detachPie() {
         mBar.updatePieControls(!mPieAttached);
    }

    public void resetPie(boolean enabled, int gravity) {
        mRotation = mWindowManager.getDefaultDisplay().getRotation();

        if (mPieAttached) {
            // Remove the view
            if (mPie != null) {
                mWindowManager.removeView(mPie);
            }
            mPieAttached = false;
        }
        if (enabled) attachPie(gravity);
    }

    private boolean showPie() {
        final boolean pieEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.PIE_STATE, 0, UserHandle.USER_CURRENT) == 1;
        return pieEnabled;
    }

    public void attachPie(int gravity) {
        if(showPie()) {
            // want some slice?
            switch (gravity) {
                // this is just main gravity, the trigger is centered later
                default:
                    addPieInLocation(Gravity.LEFT);
                    break;
                case 1:
                    addPieInLocation(Gravity.RIGHT);
                    break;
                case 2:
                    addPieInLocation(Gravity.BOTTOM);
                    break;
            }
        }
    }

    private void initOrientation(int orientation) {
        mOrientation = orientation;

        // Default to bottom if no pie gravity is set
        if (mOrientation != Gravity.BOTTOM && mOrientation != Gravity.RIGHT
                && mOrientation != Gravity.LEFT) {
            mOrientation = Gravity.BOTTOM;
        }
    }

    protected void reorient(int orientation) {
        mOrientation = convertRelativeToAbsoluteGravity(orientation);
        mPiePosition = getOrientation();
        setupEdgeGesture(mPiePosition);
        mPie.show(mPie.isShowing());
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.PIE_GRAVITY, mOrientation);
    }

    protected boolean isKeyguardLocked() {
        return mKeyguardManager.isKeyguardLocked();
    }

    private void addPieInLocation(int gravity) {
        if (mPieAttached) return;

        // pie menu
        mPie = new PieMenu(mContext, this, mBar);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("PieMenu");
        lp.windowAnimations = android.R.style.Animation;

        // set gravity and touch
        initOrientation(gravity);

        // pie edge gesture
        mPiePosition = getOrientation();
        setupEdgeGesture(mPiePosition);

        // add pie view to windowmanager
        mWindowManager.addView(mPie, lp);
        mPieAttached = true;

        createItems();
    }

    private boolean activateFromListener(int touchX, int touchY) {
        if (!mPie.isShowing()) {
            mEdgeGestureTouchPos.x = touchX;
            mEdgeGestureTouchPos.y = touchY;
            mPie.show(true);
            return true;
        }
        return false;
    }

    private void setupEdgeGesture(int gravity) {
        int triggerSlot = convertToEdgeGesturePosition(gravity);

        int sensitivity = mContext.getResources().getInteger(R.integer.pie_gesture_sensitivity);
        if (sensitivity < EdgeServiceConstants.SENSITIVITY_LOWEST
                || sensitivity > EdgeServiceConstants.SENSITIVITY_HIGHEST) {
            sensitivity = EdgeServiceConstants.SENSITIVITY_HIGHEST;
        }

        mEdgeGestureManager.updateEdgeGestureActivationListener(this,
                sensitivity << EdgeServiceConstants.SENSITIVITY_SHIFT
                        | triggerSlot
                        | EdgeServiceConstants.LONG_LIVING
                        | EdgeServiceConstants.UNRESTRICTED);
    }

    private int convertToEdgeGesturePosition(int gravity) {
        switch (gravity) {
            case Gravity.LEFT:
                return EdgeGesturePosition.LEFT.FLAG;
            case Gravity.RIGHT:
                return EdgeGesturePosition.RIGHT.FLAG;
            case Gravity.BOTTOM:
            default: // fall back
                return EdgeGesturePosition.BOTTOM.FLAG;
        }
    }

    private void createItems() {
        final Resources res = mContext.getResources();
        mBack = makeItem(R.drawable.pie_back, res.getColor(R.color.pie_back_button),
                BACK_BUTTON, false);
        mHome = makeItem(R.drawable.pie_home, res.getColor(R.color.pie_home_button),
                HOME_BUTTON, false);
        mRecent = makeItem(R.drawable.pie_recent, res.getColor(R.color.pie_recent_button),
                RECENT_BUTTON, false);

        mPie.addItem(mRecent);
        mPie.addItem(mHome);
        mPie.addItem(mBack);
    }

    public void setNavigationIconHints(int hints) {
        if (mBack == null) return;

        boolean alt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        mBack.setIcon(alt ? R.drawable.pie_back_keyboard : R.drawable.pie_back);
    }

    private int convertAbsoluteToRelativeGravity(int gravity) {
        // only mess around with Pie in landscape
        if (mRelocatePieOnRotation && isLandScape()) {
            // no questions asked if right is preferred
            if (PIE_ALWAYS_AT_RIGHT) {
                return Gravity.RIGHT;
            } else {
                // bottom is now right/left (depends on the direction of rotation)
                return mRotation == Surface.ROTATION_90 ? Gravity.RIGHT : Gravity.LEFT;
            }
        }
        return gravity;
    }

    protected boolean isLandScape() {
       return mRotation == Surface.ROTATION_90 || mRotation == Surface.ROTATION_270;
    }

    private int convertRelativeToAbsoluteGravity(int gravity) {
        // only mess around with Pie in landscape
        if (mRelocatePieOnRotation && isLandScape()) {
            if (PIE_ALWAYS_AT_RIGHT) {
                // no questions asked if right is preferred
                return Gravity.RIGHT;
            } else {
                // just stick to the edge when possible
                switch (gravity) {
                    case Gravity.LEFT:
                        return mRotation == Surface.ROTATION_90 ? Gravity.NO_GRAVITY : Gravity.BOTTOM;
                    case Gravity.RIGHT:
                        return mRotation == Surface.ROTATION_90 ? Gravity.BOTTOM : Gravity.NO_GRAVITY;
                    case Gravity.BOTTOM:
                        return mRotation == Surface.ROTATION_90 ? Gravity.LEFT : Gravity.RIGHT;
                }
            }
        }
        return gravity;
    }

    protected int getOrientation() {
        return convertAbsoluteToRelativeGravity(mOrientation);
    }

    /**
     * Check whether the requested relative gravity is possible.
     *
     * @param gravity the Gravity value to check
     * @return whether the requested relative Gravity is possible
     * @see #isGravityPossible(int)
     */
    protected boolean isGravityPossible(int gravity) {
        if (mRelocatePieOnRotation && isLandScape() && PIE_ALWAYS_AT_RIGHT) {
            return gravity == Gravity.RIGHT;
        }

        return convertRelativeToAbsoluteGravity(gravity) != Gravity.NO_GRAVITY;
    }

    private PieItem makeItem(int image, int color, String name, boolean lesser) {
        int mItemSize = (int) mContext.getResources().getDimension(R.dimen.pie_item_size);
        ImageView view = new ImageView(mContext);
        Drawable background = mContext.getDrawable(R.drawable.pie_ripple);
        view.setImageResource(image);
        view.setBackground(background);
        view.setScaleType(ScaleType.CENTER);
        view.setTag(name);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        view.setOnClickListener(mOnClickListener);
        return new PieItem(view, color, lesser, mItemSize);
    }

    protected void setCenter(int x, int y) {
        if (!mForcePieCentered) {
            switch (mPiePosition) {
                case Gravity.LEFT:
                case Gravity.RIGHT:
                    y = mEdgeGestureTouchPos.y;
                    break;
                case Gravity.BOTTOM:
                    x = mEdgeGestureTouchPos.x;
                    break;
            }
        }
        mPie.setCoordinates(x, y);
    }

    public void updateNotifications() {
        if (mPie != null) {
            mPie.updateNotifications();
        }
    }

    private void onNavButtonPressed(String buttonName) {
            final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        switch (buttonName) {
            case PieController.BACK_BUTTON:
                injectKey(KeyEvent.KEYCODE_BACK);
                break;
            case PieController.HOME_BUTTON:
                injectKey(KeyEvent.KEYCODE_HOME);
                break;
            case PieController.RECENT_BUTTON:
                 if (isKeyguardLocked()) {
                    return;
                }
                try {
                    barService.toggleRecentApps();
                } catch (Exception e) {
                }
                break;
        }
    }

    private void injectKey(int keycode) {
        mInjectKeycode = keycode;
        mHandler.post(onInjectKey);
    }

    @Override
    public void onEdgeGestureActivation(int touchX, int touchY,
            EdgeGesturePosition position, int flags) {
        if (mPie != null
                && activateFromListener(touchX, touchY)) {
            // give the main thread some time to do the bookkeeping
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!gainTouchFocus(mPie.getWindowToken())) {
                        detachPie();
                    }
                    restoreListenerState();
                }
            });
        } else {
            restoreListenerState();
        }
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onNavButtonPressed((String) v.getTag());
            mAudioManager.playSoundEffect(SoundEffectConstants.CLICK,
                    ActivityManager.getCurrentUser());
        }
    };

    private final Runnable onInjectKey = new Runnable() {
        @Override
        public void run() {
            final InputManager im = InputManager.getInstance();
            final int keyCode = mInjectKeycode;
            long now = SystemClock.uptimeMillis();

            final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_CUSTOM);
            final KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);

            im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };
}
