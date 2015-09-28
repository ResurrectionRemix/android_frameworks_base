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
    public static final String TILE_PIE = "pie";
    public static final String TILE_PROFILES = "profiles";
    public static final String TILE_PERFORMANCE = "performance";
    public static final String TILE_ADB_NETWORK = "adb_network";
    public static final String TILE_NFC = "nfc";
    public static final String TILE_COMPASS = "compass";
    public static final String TILE_LOCKSCREEN = "lockscreen";
    public static final String TILE_LTE = "lte";
    public static final String TILE_VISUALIZER = "visualizer";
    public static final String TILE_VOLUME = "volume_panel";
    public static final String TILE_SCREEN_TIMEOUT = "screen_timeout";
    public static final String TILE_SCREENSHOT = "screenshot";
    public static final String TILE_SCREENRECORD = "screenrecord";
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
    public static final String TILE_TRDS = "trds";
    public static final String TILE_SYSTEMUI_RESTART = "reboot_systemui";
    public static final String TILE_SLIM_FLOATS = "slim_floats";
    public static final String TILE_THEMES = "toggleThemes";
    public static final String TILE_CONFIGURATIONS = "Configurations"; 	 
    //public static final String TILE_ONTHEGO = "toggleOnTheGo";

    public static final String DYNAMIC_TILE_NEXT_ALARM = "next_alarm";
    public static final String DYNAMIC_TILE_IME_SELECTOR = "ime_selector";
    public static final String DYNAMIC_TILE_ADB = "adb";

    protected static final ArrayList<String> STATIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> DYNAMIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> TILES_AVAILABLE = new ArrayList<String>();

    static {
        STATIC_TILES_AVAILABLE.add(TILE_WIFI);
        STATIC_TILES_AVAILABLE.add(TILE_BLUETOOTH);
        STATIC_TILES_AVAILABLE.add(TILE_CELLULAR);
        STATIC_TILES_AVAILABLE.add(TILE_AIRPLANE);
        STATIC_TILES_AVAILABLE.add(TILE_ROTATION);
        STATIC_TILES_AVAILABLE.add(TILE_FLASHLIGHT);
        STATIC_TILES_AVAILABLE.add(TILE_LOCATION);
        STATIC_TILES_AVAILABLE.add(TILE_CAST);
        STATIC_TILES_AVAILABLE.add(TILE_INVERSION);
        STATIC_TILES_AVAILABLE.add(TILE_HOTSPOT);
        STATIC_TILES_AVAILABLE.add(TILE_NOTIFICATIONS);
        STATIC_TILES_AVAILABLE.add(TILE_DATA);
        STATIC_TILES_AVAILABLE.add(TILE_ROAMING);
        STATIC_TILES_AVAILABLE.add(TILE_DDS);
        STATIC_TILES_AVAILABLE.add(TILE_APN);
        STATIC_TILES_AVAILABLE.add(TILE_PIE);
        STATIC_TILES_AVAILABLE.add(TILE_PROFILES);
        STATIC_TILES_AVAILABLE.add(TILE_PERFORMANCE);
        STATIC_TILES_AVAILABLE.add(TILE_ADB_NETWORK);
        STATIC_TILES_AVAILABLE.add(TILE_NFC);
        STATIC_TILES_AVAILABLE.add(TILE_COMPASS);
        STATIC_TILES_AVAILABLE.add(TILE_LOCKSCREEN);
        STATIC_TILES_AVAILABLE.add(TILE_LTE);
        STATIC_TILES_AVAILABLE.add(TILE_VISUALIZER);
        STATIC_TILES_AVAILABLE.add(TILE_VOLUME);
        STATIC_TILES_AVAILABLE.add(TILE_SCREEN_TIMEOUT);
        STATIC_TILES_AVAILABLE.add(TILE_SCREENSHOT);
        STATIC_TILES_AVAILABLE.add(TILE_SYNC);
        STATIC_TILES_AVAILABLE.add(TILE_BRIGHTNESS);
        STATIC_TILES_AVAILABLE.add(TILE_BATTERY_SAVER);
        STATIC_TILES_AVAILABLE.add(TILE_SCREEN_OFF);
        STATIC_TILES_AVAILABLE.add(TILE_EXPANDED_DESKTOP);
        STATIC_TILES_AVAILABLE.add(TILE_NAVBAR);
        STATIC_TILES_AVAILABLE.add(TILE_APPCIRCLEBAR);
        STATIC_TILES_AVAILABLE.add(TILE_AMBIENT_DISPLAY);
        STATIC_TILES_AVAILABLE.add(TILE_LIVE_DISPLAY);
        STATIC_TILES_AVAILABLE.add(TILE_USB_TETHER);
        STATIC_TILES_AVAILABLE.add(TILE_MUSIC);
        STATIC_TILES_AVAILABLE.add(TILE_HEADS_UP);
        STATIC_TILES_AVAILABLE.add(TILE_POWER_MENU);
        STATIC_TILES_AVAILABLE.add(TILE_REBOOT);
        STATIC_TILES_AVAILABLE.add(TILE_SLIMACTION);
        STATIC_TILES_AVAILABLE.add(TILE_TRDS);
        STATIC_TILES_AVAILABLE.add(TILE_SYSTEMUI_RESTART);
        STATIC_TILES_AVAILABLE.add(TILE_SLIM_FLOATS);
 	STATIC_TILES_AVAILABLE.add(TILE_THEMES);
	STATIC_TILES_AVAILABLE.add(TILE_CONFIGURATIONS);

        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_NEXT_ALARM);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_IME_SELECTOR);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_ADB);

        TILES_AVAILABLE.addAll(STATIC_TILES_AVAILABLE);
        TILES_AVAILABLE.addAll(DYNAMIC_TILES_AVAILABLE); 
	TILES_AVAILABLE.add(TILE_SCREENRECORD);       
    }
}


