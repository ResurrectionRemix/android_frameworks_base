/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2015-2018 The OmniROM Project
 *
 * Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.android.internal.os;

import android.content.Intent;
import android.hardware.SensorEvent;
import android.view.KeyEvent;

public interface AlternativeDeviceKeyHandler {

    /**
     * Invoked when an unknown key was detected by the system, letting the device handle
     * this special keys prior to pass the key to the active app.
     *
     * @param event The key event to be handled
     * @return If the event is consume
     */
    public boolean handleKeyEvent(KeyEvent event);

    /**
     * Invoked when an unknown key was detected by the system,
     * this should NOT handle the key just return if it WOULD be handled
     *
     * @param event The key event to be handled
     * @return If the event will be consumed
     */
    public boolean canHandleKeyEvent(KeyEvent event);

    /**
     * Special key event that should be treated as
     * a camera launch event
     *
     * @param event The key event to be handled
     * @return If the event is a camera launch event
     */
    public boolean isCameraLaunchEvent(KeyEvent event);

    /**
     * Special key event that should be treated as
     * a wake event
     *
     * @param event The key event to be handled
     * @return If the event is a wake event
     */
    public boolean isWakeEvent(KeyEvent event);

    /**
     * Return false if this event should be ignored
     *
     * @param event The key event to be handled
     * @return If the event should be ignored
     */
    public boolean isDisabledKeyEvent(KeyEvent event);

    /**
     * Return an Intent that should be launched for that KeyEvent
     *
     * @param event The key event to be handled
     * @return an Intent or null
     */
    public Intent isActivityLaunchEvent(KeyEvent event);
}
