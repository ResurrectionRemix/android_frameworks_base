/*
 * Copyright (C) 2015 The CyanogenMod Open Source Project
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

package com.android.internal.util.cm;

import java.util.ArrayList;

public class QSConstants {
    private QSConstants() {}

    public static final String TILE_WIFI = "wifi";
    public static final String TILE_BLUETOOTH = "bt";
    public static final String TILE_INVERSION = "inversion";
    public static final String TILE_CELLULAR = "cell";
    public static final String TILE_AIRPLANE = "airplane";
    public static final String TILE_ROTATION = "rotation";
    public static final String TILE_FLASHLIGHT = "flashlight";
    public static final String TILE_LOCATION = "location";
    public static final String TILE_CAST = "cast";
    public static final String TILE_HOTSPOT = "hotspot";
    public static final String TILE_NOTIFICATIONS = "notifications";
    public static final String TILE_DATA = "data";
    public static final String TILE_ROAMING = "roaming";
    public static final String TILE_DDS = "dds";
    public static final String TILE_APN = "apn";
    public static final String TILE_PROFILES = "profiles";
    public static final String TILE_PERFORMANCE = "performance";
    public static final String TILE_ADB_NETWORK = "adb_network";
    public static final String TILE_NFC = "nfc";
    public static final String TILE_COMPASS = "compass";
    public static final String TILE_LOCKSCREEN = "lockscreen";
    public static final String TILE_LTE = "lte";
    public static final String TILE_VISUALIZER = "visualizer";
    public static final String TILE_SCREEN_TIMEOUT = "screen_timeout";
    public static final String TILE_SCREENSHOT = "screenshot";
    public static final String TILE_SYNC = "sync";
    public static final String TILE_BRIGHTNESS = "brightness";
    public static final String TILE_BATTERY_SAVER = "battery_saver";
    public static final String TILE_SCREEN_OFF = "screen_off";
    public static final String TILE_EXPANDED_DESKTOP = "expanded_desktop";
    public static final String TILE_NAVBAR = "toggleNavBar";
    public static final String TILE_APPCIRCLEBAR = "toggleAppCircleBar";
    public static final String TILE_AMBIENT_DISPLAY = "ambient_display";
    public static final String TILE_LIVE_DISPLAY = "live_display";
    public static final String TILE_USB_TETHER = "usb_tether";
    public static final String TILE_MUSIC = "music";
    public static final String TILE_HEADS_UP = "heads_up";
    public static final String TILE_POWER_MENU = "power_menu";
    public static final String TILE_REBOOT = "reboot";
    public static final String TILE_SLIMACTION = "slimaction";
    public static final String TILE_SYSTEMUI_RESTART = "reboot_systemui";

    public static final String DYNAMIC_TILE_NEXT_ALARM = "next_alarm";
    public static final String DYNAMIC_TILE_IME_SELECTOR = "ime_selector";
    public static final String DYNAMIC_TILE_SU = "su";
    public static final String DYNAMIC_TILE_ADB = "adb";

    protected static final ArrayList<String> STATIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> DYNAMIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> TILES_AVAILABLE = new ArrayList<String>();

    static {
        TILES_AVAILABLE.add(TILE_WIFI);
        TILES_AVAILABLE.add(TILE_BLUETOOTH);
        TILES_AVAILABLE.add(TILE_CELLULAR);
        TILES_AVAILABLE.add(TILE_AIRPLANE);
        TILES_AVAILABLE.add(TILE_ROTATION);
        TILES_AVAILABLE.add(TILE_FLASHLIGHT);
        TILES_AVAILABLE.add(TILE_LOCATION);
        TILES_AVAILABLE.add(TILE_CAST);
        TILES_AVAILABLE.add(TILE_INVERSION);
        TILES_AVAILABLE.add(TILE_HOTSPOT);
        TILES_AVAILABLE.add(TILE_NOTIFICATIONS);
        TILES_AVAILABLE.add(TILE_DATA);
        TILES_AVAILABLE.add(TILE_ROAMING);
        TILES_AVAILABLE.add(TILE_DDS);
        TILES_AVAILABLE.add(TILE_APN);
        TILES_AVAILABLE.add(TILE_PROFILES);
        TILES_AVAILABLE.add(TILE_PERFORMANCE);
        TILES_AVAILABLE.add(TILE_ADB_NETWORK);
        TILES_AVAILABLE.add(TILE_NFC);
        TILES_AVAILABLE.add(TILE_COMPASS);
        TILES_AVAILABLE.add(TILE_LOCKSCREEN);
        TILES_AVAILABLE.add(TILE_LTE);
        TILES_AVAILABLE.add(TILE_VISUALIZER);
        TILES_AVAILABLE.add(TILE_SCREEN_TIMEOUT);
        TILES_AVAILABLE.add(TILE_SCREENSHOT);
        TILES_AVAILABLE.add(TILE_SYNC);
        TILES_AVAILABLE.add(TILE_BRIGHTNESS);
        TILES_AVAILABLE.add(TILE_BATTERY_SAVER);
        TILES_AVAILABLE.add(TILE_SCREEN_OFF);
        TILES_AVAILABLE.add(TILE_EXPANDED_DESKTOP);
        TILES_AVAILABLE.add(TILE_NAVBAR);
        TILES_AVAILABLE.add(TILE_APPCIRCLEBAR);
        TILES_AVAILABLE.add(TILE_AMBIENT_DISPLAY);
        TILES_AVAILABLE.add(TILE_LIVE_DISPLAY);
        TILES_AVAILABLE.add(TILE_USB_TETHER);
        TILES_AVAILABLE.add(TILE_MUSIC);
        TILES_AVAILABLE.add(TILE_HEADS_UP);
        TILES_AVAILABLE.add(TILE_POWER_MENU);
        TILES_AVAILABLE.add(TILE_REBOOT);
        TILES_AVAILABLE.add(TILE_SLIMACTION);
        TILES_AVAILABLE.add(TILE_SYSTEMUI_RESTART);
        
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_NEXT_ALARM);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_IME_SELECTOR);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_SU);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_ADB);

        TILES_AVAILABLE.addAll(STATIC_TILES_AVAILABLE);
        TILES_AVAILABLE.addAll(DYNAMIC_TILES_AVAILABLE);        
    }
}
