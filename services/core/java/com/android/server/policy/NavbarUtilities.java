/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.policy;

import android.view.KeyEvent;

import com.android.server.LocalServices;
import com.android.server.statusbar.StatusBarManagerInternal;

/**
 * Various utilities for the navigation bar.
 */
public class NavbarUtilities {

    // These need to match the documentation/constant in
    // core/res/res/values/config.xml
    public static final int KEY_ACTION_NOTHING = 0;
    public static final int KEY_ACTION_MENU = 1;
    public static final int KEY_ACTION_APP_SWITCH = 2;
    public static final int KEY_ACTION_SEARCH = 3;
    public static final int KEY_ACTION_VOICE_SEARCH = 4;
    public static final int KEY_ACTION_IN_APP_SEARCH = 5;
    public static final int KEY_ACTION_CAMERA = 6;
    public static final int KEY_ACTION_LAST_APP = 7;
    public static final int KEY_ACTION_SPLIT_SCREEN = 8;
    public static final int KEY_ACTION_FLASHLIGHT = 9;
    public static final int KEY_ACTION_CLEAR_NOTIFICATIONS = 10;
    public static final int KEY_ACTION_VOLUME_PANEL = 11;
    public static final int KEY_ACTION_SCREEN_OFF = 12;
    public static final int KEY_ACTION_SCREENSHOT = 13;

    // Special values, used internal only.
    public static final int KEY_ACTION_HOME = 100;
    public static final int KEY_ACTION_BACK = 101;

    // Masks for checking presence of hardware keys.
    // Must match values in core/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_ASSIST = 0x08;
    public static final int KEY_MASK_APP_SWITCH = 0x10;
    public static final int KEY_MASK_CAMERA = 0x20;

    // This controls whether we will intercept key events and
    // handle them with our customized input policy. Enable
    // when navigation bar is fully supporting this and we have
    // screen pinning unlock within long pressing back button.
    static final boolean ENABLE_CUSTOM_INPUT_POLICY = true;

    static StatusBarManagerInternal mStatusBarManagerInternal;
    static final Object mServiceAquireLock = new Object();

    public static StatusBarManagerInternal getCustomStatusBarManagerInternal() {
        synchronized (mServiceAquireLock) {
            if (mStatusBarManagerInternal == null) {
                mStatusBarManagerInternal =
                        LocalServices.getService(StatusBarManagerInternal.class);
            }
            return mStatusBarManagerInternal;
        }
    }

    /**
     * Request camera to be opened.
     * This goes through status bar service which will check if there
     * is an app set as default camera app and launch that or trigger
     * a ResolverActivity to let user chose which camera app to use.
     */
    public static void launchCamera() {
        StatusBarManagerInternal statusbar = getCustomStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.onCameraLaunchGestureDetected(-1);
        }
    }

    /**
     * Request current window to enter multiwindow mode.
     */
    public static void toggleSplitScreen() {
        StatusBarManagerInternal statusbar = getCustomStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleSplitScreen();
        }
    }

    /**
     * List of key codes to intercept with our custom policy.
     */
    public static final int[] SUPPORTED_KEYCODE_LIST = {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_CAMERA
    };

    /**
     * List of key actions available for key code behaviors.
     */
    static final int[] SUPPORTED_KEY_ACTIONS = {
            KEY_ACTION_NOTHING,
            KEY_ACTION_MENU,
            KEY_ACTION_APP_SWITCH,
            KEY_ACTION_SEARCH,
            KEY_ACTION_VOICE_SEARCH,
            KEY_ACTION_IN_APP_SEARCH,
            KEY_ACTION_CAMERA,
            KEY_ACTION_LAST_APP,
            KEY_ACTION_SPLIT_SCREEN,
            KEY_ACTION_FLASHLIGHT,
            KEY_ACTION_CLEAR_NOTIFICATIONS,
            KEY_ACTION_VOLUME_PANEL,
            KEY_ACTION_SCREEN_OFF,
            KEY_ACTION_SCREENSHOT,
    };

    /**
     * @return the default res id for the key double tap default action.
     * @param keyCode the KeyEvent key code.
     */
    public static int getKeyDoubleTapBehaviorResId(int keyCode) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_HOME:
                return com.android.internal.R.integer.config_doubleTapOnHomeKeyBehavior;
            case KeyEvent.KEYCODE_BACK:
                return com.android.internal.R.integer.config_doubleTapOnBackKeyBehavior;
            case KeyEvent.KEYCODE_MENU:
                return com.android.internal.R.integer.config_doubleTapOnMenuKeyBehavior;
            case KeyEvent.KEYCODE_ASSIST:
                return com.android.internal.R.integer.config_doubleTapOnAssistKeyBehavior;
            case KeyEvent.KEYCODE_APP_SWITCH:
                return com.android.internal.R.integer.config_doubleTapOnAppSwitchKeyBehavior;
            case KeyEvent.KEYCODE_CAMERA:
                return com.android.internal.R.integer.config_doubleTapOnCameraKeyBehavior;
        }
        return 0;
    }

    /**
     * @return the default res id for the key long press default action.
     * @param keyCode the KeyEvent key code.
     */
    public static int getKeyLongPressBehaviorResId(int keyCode) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_HOME:
                return com.android.internal.R.integer.config_longPressOnHomeKeyBehavior;
            case KeyEvent.KEYCODE_BACK:
                return com.android.internal.R.integer.config_longPressOnBackKeyBehavior;
            case KeyEvent.KEYCODE_MENU:
                return com.android.internal.R.integer.config_longPressOnMenuKeyBehavior;
            case KeyEvent.KEYCODE_ASSIST:
                return com.android.internal.R.integer.config_longPressOnAssistKeyBehavior;
            case KeyEvent.KEYCODE_APP_SWITCH:
                return com.android.internal.R.integer.config_longPressOnAppSwitchKeyBehavior;
            case KeyEvent.KEYCODE_CAMERA:
                return com.android.internal.R.integer.config_longPressOnCameraKeyBehavior;
        }
        return 0;
    }

    /**
     * @return if key code is supported by custom policy.
     * @param keyCode the KeyEvent key code.
     */
    public static boolean canApplyCustomPolicy(int keyCode) {
        if (!ENABLE_CUSTOM_INPUT_POLICY) {
            return false;
        }
        boolean supported = false;
        int length = NavbarUtilities.SUPPORTED_KEYCODE_LIST.length;
        for (int i = 0; i < length; i++) {
            if (NavbarUtilities.SUPPORTED_KEYCODE_LIST[i] == keyCode) {
                supported = true;
                break;
            }
        }
        return supported/* && mDeviceHardwareKeys > 0*/;
    }

    /**
     * @return key code's long press behavior.
     * @param keyCode the KeyEvent key code.
     */
    public static int getKeyLongPressBehavior(int keyCode) {
        int behavior = -1;
        try {
            behavior = PhoneWindowManager.mKeyLongPressBehavior.get(keyCode);
        } catch (NullPointerException e) {
            // Ops.
        } finally {
            return behavior;
        }
    }

    /**
     * @return if key code's double tap is pending.
     * @param keyCode the KeyEvent key code.
     */
    public static boolean isKeyDoubleTapPending(int keyCode) {
        boolean pending = false;
        try {
            pending = PhoneWindowManager.mKeyDoubleTapPending.get(keyCode);
        } catch (NullPointerException e) {
            // Ops.
        } finally {
            return pending;
        }
    }

    /**
     * @return key code's dpouble tap behavior.
     * @param keyCode the KeyEvent key code.
     */
    public static int getKeyDoubleTapBehavior(int keyCode) {
        int behavior = -1;
        try {
            behavior = PhoneWindowManager.mKeyDoubleTapBehavior.get(keyCode);
        } catch (NullPointerException e) {
            // Ops.
        } finally {
            return behavior;
        }
    }

    /**
     * @return key code's double tap timeout runnable.
     * @param keyCode the KeyEvent key code.
     */
    public static Runnable getDoubleTapTimeoutRunnable(int keyCode) {
        Runnable runnable = null;
        try {
            runnable = PhoneWindowManager.mKeyDoubleTapRunnable.get(keyCode);
        } catch (NullPointerException e) {
            // Ops.
        } finally {
            return runnable;
        }
    }

    /**
     * @return if key code's last event has been consumed.
     * @param keyCode the KeyEvent key code.
     */
    public static boolean isKeyConsumed(int keyCode) {
        boolean consumed = false;
        try {
            consumed = PhoneWindowManager.mKeyConsumed.get(keyCode);
        } catch (NullPointerException e) {
            // Ops.
        } finally {
            return consumed;
        }
    }

    /**
     * Set last key code's event consumed state.
     * @param keyCode the KeyEvent key code.
     */
    public static void setKeyConsumed(int keyCode, boolean consumed) {
        try {
            PhoneWindowManager.mKeyConsumed.put(keyCode, consumed);
        } catch (NullPointerException e) {
            // Ops.
        }
    }

    /**
     * Set last key code's event pressed state.
     * @param keyCode the KeyEvent key code.
     */
    public static void setKeyPressed(int keyCode, boolean pressed) {
        try {
            PhoneWindowManager.mKeyPressed.put(keyCode, pressed);
        } catch (NullPointerException e) {
            // Ops.
        }
    }

    /**
     * Set last key code's event double tap pending state.
     * @param keyCode the KeyEvent key code.
     */
    public static void setKeyDoubleTapPending(int keyCode, boolean pending) {
        try {
            PhoneWindowManager.mKeyDoubleTapPending.put(keyCode, pending);
        } catch (NullPointerException e) {
            // Ops.
        }
    }
}
