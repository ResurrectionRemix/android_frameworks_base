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
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import android.widget.RemoteViews;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.tiles.AdbOverNetworkTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.AmbientDisplayTile;
import com.android.systemui.qs.tiles.AppPickerTile;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.BrightnessTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.CustomQSTile;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.EditTile;
import com.android.systemui.qs.tiles.ExpandedDesktopTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HeadsUpTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LiveDisplayTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.AppCircleBarTile;
import com.android.systemui.qs.tiles.AppsidebarTile;
import com.android.systemui.qs.tiles.KernelAdiutorTile;
import com.android.systemui.qs.tiles.NavBarTile;
import com.android.systemui.qs.tiles.SystemUIRestartTile;
import com.android.systemui.qs.tiles.PieTile;
import com.android.systemui.qs.tiles.GestureAnyWhereTile;
import com.android.systemui.qs.tiles.ScreenrecordTile;
import com.android.systemui.qs.tiles.LockscreenToggleTile;
import com.android.systemui.qs.tiles.MusicTile;
import com.android.systemui.qs.tiles.RRTile;
import com.android.systemui.qs.tiles.SElinuxTile;
import com.android.systemui.qs.tiles.KillAppTile;
import com.android.systemui.qs.tiles.LteTile;
import com.android.systemui.qs.tiles.ThemesTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.PerfProfileTile;
import com.android.systemui.qs.tiles.ProfilesTile;
import com.android.systemui.qs.tiles.RebootTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.ScreenOffTile;
import com.android.systemui.qs.tiles.PowerMenuTile;
import com.android.systemui.qs.tiles.ScreenshotTile;
import com.android.systemui.qs.tiles.ScreenTimeoutTile;
import com.android.systemui.qs.tiles.SyncTile;
import com.android.systemui.qs.tiles.UsbTetherTile;
import com.android.systemui.qs.tiles.VolumeTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.statusbar.CustomTileData;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import android.telephony.TelephonyManager;

import cyanogenmod.app.CustomTileListenerService;
import cyanogenmod.app.StatusBarPanelCustomTile;
import cyanogenmod.providers.CMSettings;
import com.android.internal.telephony.PhoneConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Platform implementation of the quick settings tile host **/
public class QSTileHost implements QSTile.Host, Tunable {
    private static final String TAG = "QSTileHost";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final int TILES_PER_PAGE = 8;

    private final Context mContext;
    private final PhoneStatusBar mStatusBar;
    private final LinkedHashMap<String, QSTile<?>> mTiles = new LinkedHashMap<>();
    protected final ArrayList<String> mTileSpecs = new ArrayList<>();
    private final BluetoothController mBluetooth;
    private final LocationController mLocation;
    private final RotationLockController mRotation;
    private final NetworkController mNetwork;
    private final ZenModeController mZen;
    private final HotspotController mHotspot;
    private final CastController mCast;
    private final Looper mLooper;
    private final FlashlightController mFlashlight;
    private final UserSwitcherController mUserSwitcherController;
    private final KeyguardMonitor mKeyguard;
    private final SecurityController mSecurity;

    private CustomTileData mCustomTileData;
    private CustomTileListenerService mCustomTileListenerService;

    private Callback mCallback;

    public QSTileHost(Context context, PhoneStatusBar statusBar,
            BluetoothController bluetooth, LocationController location,
            RotationLockController rotation, NetworkController network,
            ZenModeController zen, HotspotController hotspot,
            CastController cast, FlashlightController flashlight,
            UserSwitcherController userSwitcher, KeyguardMonitor keyguard,
            SecurityController security) {
        mContext = context;
        mStatusBar = statusBar;
        mBluetooth = bluetooth;
        mLocation = location;
        mRotation = rotation;
        mNetwork = network;
        mZen = zen;
        mHotspot = hotspot;
        mCast = cast;
        mFlashlight = flashlight;
        mUserSwitcherController = userSwitcher;
        mKeyguard = keyguard;
        mSecurity = security;
        mCustomTileData = new CustomTileData();

        final HandlerThread ht = new HandlerThread(QSTileHost.class.getSimpleName(),
                Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        mLooper = ht.getLooper();

        TunerService.get(mContext).addTunableByProvider(this, CMSettings.Secure.QS_TILES, true);
    }

    public void destroy() {
        TunerService.get(mContext).removeTunable(this);
    }

    public boolean isEditing() {
        if (mCallback != null) {
            return mCallback.isEditing();
        }
        return false;
    }

    public void setEditing(boolean editing) {
        mCallback.setEditing(editing);
    }

    void setCustomTileListenerService(CustomTileListenerService customTileListenerService) {
        mCustomTileListenerService = customTileListenerService;
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public Collection<QSTile<?>> getTiles() {
        return mTiles.values();
    }

    public List<String> getTileSpecs() {
        return mTileSpecs;
    }

    public String getSpec(QSTile<?> tile) {
        for (Map.Entry<String, QSTile<?>> entry : mTiles.entrySet()) {
            if (entry.getValue() == tile) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void startActivityDismissingKeyguard(final Intent intent) {
        mStatusBar.postStartActivityDismissingKeyguard(intent, 0);
    }

   public static boolean deviceSupportsLte(Context ctx) {
        final TelephonyManager tm = (TelephonyManager)
                ctx.getSystemService(Context.TELEPHONY_SERVICE);
        return (tm.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE)
                || tm.getLteOnGsmMode() != 0;
    }
 public static boolean deviceSupportsDdsSupported(Context context) {
        TelephonyManager tm = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.isMultiSimEnabled()
                && tm.getMultiSimConfiguration() == TelephonyManager.MultiSimVariants.DSDA;
    }

    @Override
    public void removeCustomTile(StatusBarPanelCustomTile customTile) {
        if (mCustomTileListenerService != null) {
            mCustomTileListenerService.removeCustomTile(customTile.getPackage(),
                    customTile.getTag(), customTile.getId());
        }
    }

    @Override
    public void startActivityDismissingKeyguard(PendingIntent intent) {
        mStatusBar.postStartActivityDismissingKeyguard(intent);
    }

    @Override
    public void warn(String message, Throwable t) {
        // already logged
    }

    @Override
    public void collapsePanels() {
        mStatusBar.postAnimateCollapsePanels();
    }

    @Override
    public RemoteViews.OnClickHandler getOnClickHandler() {
        return mStatusBar.getOnClickHandler();
    }

    @Override
    public Looper getLooper() {
        return mLooper;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public BluetoothController getBluetoothController() {
        return mBluetooth;
    }

    @Override
    public LocationController getLocationController() {
        return mLocation;
    }

    @Override
    public RotationLockController getRotationLockController() {
        return mRotation;
    }

    @Override
    public NetworkController getNetworkController() {
        return mNetwork;
    }

    @Override
    public ZenModeController getZenModeController() {
        return mZen;
    }

    @Override
    public HotspotController getHotspotController() {
        return mHotspot;
    }

    @Override
    public CastController getCastController() {
        return mCast;
    }

    @Override
    public FlashlightController getFlashlightController() {
        return mFlashlight;
    }

    @Override
    public KeyguardMonitor getKeyguardMonitor() {
        return mKeyguard;
    }

    public UserSwitcherController getUserSwitcherController() {
        return mUserSwitcherController;
    }

    public SecurityController getSecurityController() {
        return mSecurity;
    }
    
    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!CMSettings.Secure.QS_TILES.equals(key)) {
            return;
        }
        if (DEBUG) Log.d(TAG, "Recreating tiles");
        final List<String> tileSpecs = loadTileSpecs(newValue);
        if (tileSpecs.equals(mTileSpecs)) return;
        for (Map.Entry<String, QSTile<?>> tile : mTiles.entrySet()) {
            if (!tileSpecs.contains(tile.getKey()) && mCustomTileData.get(tile.getKey()) == null) {
                if (DEBUG) Log.d(TAG, "Destroying tile: " + tile.getKey());
                tile.getValue().destroy();
            }
        }
        final LinkedHashMap<String, QSTile<?>> newTiles = new LinkedHashMap<>();
        for (String tileSpec : tileSpecs) {
            if (mTiles.containsKey(tileSpec)) {
                newTiles.put(tileSpec, mTiles.get(tileSpec));
            } else {
                if (DEBUG) Log.d(TAG, "Creating tile: " + tileSpec);
                try {
                    if (mCustomTileData.get(tileSpec) != null) {
                        newTiles.put(tileSpec, new CustomQSTile(this,
                                mCustomTileData.get(tileSpec).sbc));
                    } else {
                        newTiles.put(tileSpec, createTile(tileSpec));
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Error creating tile for spec: " + tileSpec, t);
                }
            }
        }
        mTileSpecs.clear();
        mTileSpecs.addAll(tileSpecs);
        mTiles.clear();
        mTiles.putAll(newTiles);
        if (mCallback != null) {
            mCallback.onTilesChanged();
        }
    }

    @Override
    public void goToSettingsPage() {
        if (mCallback != null) {
            mCallback.goToSettingsPage();
        }
    }

    public QSTile<?> createTile(String tileSpec) {
        if (tileSpec.equals("wifi")) return new WifiTile(this);
        else if (tileSpec.equals("bt")) return new BluetoothTile(this);
        else if (tileSpec.equals("inversion")) return new ColorInversionTile(this);
        else if (tileSpec.equals("cell")) return new CellularTile(this);
        else if (tileSpec.equals("airplane")) return new AirplaneModeTile(this);
        else if (tileSpec.equals("dnd")) return new DndTile(this);
        else if (tileSpec.equals("rotation")) return new RotationLockTile(this);
        else if (tileSpec.equals("flashlight")) return new FlashlightTile(this);
        else if (tileSpec.equals("location")) return new LocationTile(this);
        else if (tileSpec.equals("cast")) return new CastTile(this);
        else if (tileSpec.equals("hotspot")) return new HotspotTile(this);
        else if (tileSpec.equals("edit")) return new EditTile(this);
        else if (tileSpec.equals("adb_network")) return new AdbOverNetworkTile(this);
        else if (tileSpec.equals("compass")) return new CompassTile(this);
        else if (tileSpec.equals("nfc")) return new NfcTile(this);
        else if (tileSpec.equals("profiles")) return new ProfilesTile(this);
        else if (tileSpec.equals("sync")) return new SyncTile(this);
        else if (tileSpec.equals("volume_panel")) return new VolumeTile(this);
        else if (tileSpec.equals("usb_tether")) return new UsbTetherTile(this);
        else if (tileSpec.equals("screen_timeout")) return new ScreenTimeoutTile(this);
        else if (tileSpec.equals("performance")) return new PerfProfileTile(this);
	else if (tileSpec.equals("lockscreen")) return  new LockscreenToggleTile(this);
        else if (tileSpec.equals("ambient_display")) return new AmbientDisplayTile(this);
        else if (tileSpec.equals("live_display")) return new LiveDisplayTile(this);
        else if (tileSpec.equals("brightness")) return new BrightnessTile(this);
        else if (tileSpec.equals("screen_off")) return new ScreenOffTile(this);
        else if (tileSpec.equals("screenshot")) return new ScreenshotTile(this);
        else if (tileSpec.equals("expanded_desktop")) return new ExpandedDesktopTile(this);
        else if (tileSpec.equals("music")) return new MusicTile(this);
        else if (tileSpec.equals("reboot")) return new RebootTile(this);
	else if (tileSpec.equals("configurations")) return new RRTile(this);
        else if (tileSpec.equals("heads_up")) return new HeadsUpTile(this);
	else if (tileSpec.equals("lte")) return new LteTile(this);
	else if (tileSpec.equals("themes")) return new ThemesTile(this);
	else if (tileSpec.equals("navbar")) return new NavBarTile(this);
	else if (tileSpec.equals("appcirclebar")) return new AppCircleBarTile(this);
	else if (tileSpec.equals("kernel_adiutor")) return new KernelAdiutorTile(this);
	else if (tileSpec.equals("screenrecord")) return new ScreenrecordTile(this);
	else if (tileSpec.equals("pie")) return new PieTile(this);
	else if (tileSpec.equals("appsidebar")) return new AppsidebarTile(this);
	else if (tileSpec.equals("restartui")) return new SystemUIRestartTile(this);
	else if (tileSpec.equals("gesture_anywhere")) return new GestureAnyWhereTile(this);
        else if (tileSpec.equals("battery_saver")) return new BatterySaverTile(this);
        else if (tileSpec.equals("power_menu")) return new PowerMenuTile(this);
	else if (tileSpec.equals("selinux")) return new SElinuxTile(this);
        else if (tileSpec.equals("app_picker")) return new AppPickerTile(this);
	else if (tileSpec.equals("kill_app")) return new KillAppTile(this);
        else if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(this,tileSpec);
        else throw new IllegalArgumentException("Bad tile spec: " + tileSpec);
    }

    protected List<String> loadTileSpecs(String tileList) {
        final Resources res = mContext.getResources();
        final String defaultTileList = res.getString(org.cyanogenmod.platform.internal.
                R.string.config_defaultQuickSettingsTiles);
        if (tileList == null) {
            tileList = res.getString(R.string.quick_settings_tiles);
            if (DEBUG) Log.d(TAG, "Loaded tile specs from config: " + tileList);
        } else {
            if (DEBUG) Log.d(TAG, "Loaded tile specs from setting: " + tileList);
        }
        final ArrayList<String> tiles = new ArrayList<String>();
        boolean addedDefault = false;
        for (String tile : tileList.split(",")) {
            tile = tile.trim();
            if (tile.isEmpty()) continue;
            if (tile.equals("default")) {
                if (!addedDefault) {
                    tiles.addAll(Arrays.asList(defaultTileList.split(",")));
                    addedDefault = true;
                }
            } else {
                tiles.add(tile);
            }
        }
        // ensure edit tile is present
        if (tiles.size() < TILES_PER_PAGE && !tiles.contains("edit")) {
            tiles.add("edit");
        } else if (tiles.size() > TILES_PER_PAGE && !tiles.contains("edit")) {
            tiles.add((TILES_PER_PAGE - 1), "edit");
        }
        return tiles;
    }

    public void remove(String tile) {
        MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_REMOVE, tile);
        List<String> tiles = new ArrayList<>(mTileSpecs);
        tiles.remove(tile);
        setTiles(tiles);
    }

    public void setTiles(List<String> tiles) {
        CMSettings.Secure.putStringForUser(getContext().getContentResolver(),
                CMSettings.Secure.QS_TILES,
                TextUtils.join(",", tiles), ActivityManager.getCurrentUser());
    }

    public void initiateReset() {
        if (mCallback != null) {
            mCallback.resetTiles();
        }
    }

    @Override
    public void resetTiles() {
        CMSettings.Secure.putStringForUser(getContext().getContentResolver(),
                CMSettings.Secure.QS_TILES, "default", ActivityManager.getCurrentUser());
    }

    public QSTile<?> getTile(String spec) {
        return mTiles.get(spec);
    }

    public static int getLabelResource(String spec) {
        if (spec.equals("wifi")) return R.string.quick_settings_wifi_label;
        else if (spec.equals("bt")) return R.string.quick_settings_bluetooth_label;
        else if (spec.equals("inversion")) return R.string.quick_settings_inversion_label;
        else if (spec.equals("cell")) return R.string.quick_settings_cellular_detail_title;
        else if (spec.equals("airplane")) return R.string.airplane_mode;
        else if (spec.equals("dnd")) return R.string.quick_settings_dnd_label;
        else if (spec.equals("rotation")) return R.string.quick_settings_rotation_locked_label;
        else if (spec.equals("flashlight")) return R.string.quick_settings_flashlight_label;
        else if (spec.equals("location")) return R.string.quick_settings_location_label;
        else if (spec.equals("cast")) return R.string.quick_settings_cast_title;
        else if (spec.equals("hotspot")) return R.string.quick_settings_hotspot_label;
        else if (spec.equals("edit")) return R.string.quick_settings_edit_label;
        else if (spec.equals("adb_network")) return R.string.quick_settings_network_adb_label;
        else if (spec.equals("compass")) return R.string.quick_settings_compass_label;
        else if (spec.equals("nfc")) return R.string.quick_settings_nfc_label;
        else if (spec.equals("profiles")) return R.string.quick_settings_profiles;
        else if (spec.equals("sync")) return R.string.quick_settings_sync_label;
        else if (spec.equals("volume_panel")) return R.string.quick_settings_volume_panel_label;
        else if (spec.equals("usb_tether")) return R.string.quick_settings_usb_tether_label;
        else if (spec.equals("screen_timeout")) return R.string.quick_settings_screen_timeout_detail_title;
        else if (spec.equals("performance")) return R.string.qs_tile_performance;
        else if (spec.equals("lockscreen")) return R.string.quick_settings_lockscreen_label;
        else if (spec.equals("ambient_display")) return R.string.quick_settings_ambient_display_label;
	else if (spec.equals("live_display")) return R.string.live_display_title;
        else if (spec.equals("music")) return R.string.quick_settings_music_label;
        else if (spec.equals("brightness")) return R.string.quick_settings_brightness_label;
        else if (spec.equals("screen_off")) return R.string.quick_settings_screen_off_label;
        else if (spec.equals("screenshot")) return R.string.quick_settings_screenshot_label;
        else if (spec.equals("expanded_desktop")) return R.string.quick_settings_expanded_desktop_label;
        else if (spec.equals("reboot")) return R.string.quick_settings_reboot_label;
        else if (spec.equals("configurations")) return R.string.quick_settings_rrtools;
	else if (spec.equals("heads_up")) return R.string.quick_settings_heads_up_label;
	else if (spec.equals("lte")) return R.string.qs_lte_label;
	else if (spec.equals("themes")) return R.string.quick_settings_themes;
	else if (spec.equals("navbar")) return R.string.quick_settings_navbar_title;
	else if (spec.equals("appcirclebar")) return R.string.quick_settings_appcirclebar_title;
	else if (spec.equals("kernel_adiutor")) return R.string.quick_settings_kernel_title;
	else if (spec.equals("screenrecord")) return R.string.quick_settings_screenrecord;
	else if (spec.equals("pie")) return R.string.quick_settings_pie;
	else if (spec.equals("appsidebar")) return R.string.quick_settings_app_sidebar;
	else if (spec.equals("restartui")) return R.string.quick_settings_systemui_restart_label;
        else if (spec.equals("gesture_anywhere")) return R.string.quick_settings_gesture_anywhere_label;
        else if (spec.equals("battery_saver")) return R.string.quick_settings_battery_saver_label;
        else if (spec.equals("power_menu")) return R.string.quick_settings_power_menu_label;
        else if (spec.equals("selinux")) return R.string.quick_settings_selinux_label;
        else if (spec.equals("app_picker")) return R.string.navbar_app_picker;
	else if (spec.equals("kill_app")) return R.string.qs_kill_app;
        return 0;
    }

    public static int getIconResource(String spec) {
        if (spec.equals("wifi")) return R.drawable.ic_qs_wifi_full_4;
        else if (spec.equals("bt")) return R.drawable.ic_qs_bluetooth_on;
        else if (spec.equals("inversion")) return R.drawable.ic_invert_colors_enable_animation;
        else if (spec.equals("cell")) return R.drawable.ic_qs_signal_full_4;
        else if (spec.equals("airplane")) return R.drawable.ic_signal_airplane_enable;
        else if (spec.equals("dnd")) return R.drawable.ic_dnd;
        else if (spec.equals("rotation")) return R.drawable.ic_portrait_from_auto_rotate;
        else if (spec.equals("flashlight")) return R.drawable.ic_signal_flashlight_enable;
        else if (spec.equals("location")) return R.drawable.ic_signal_location_enable;
        else if (spec.equals("cast")) return R.drawable.ic_qs_cast_on;
        else if (spec.equals("hotspot")) return R.drawable.ic_hotspot_enable;
        else if (spec.equals("edit")) return R.drawable.ic_qs_edit_tiles;
        else if (spec.equals("adb_network")) return R.drawable.ic_qs_network_adb_on;
        else if (spec.equals("compass")) return R.drawable.ic_qs_compass_on;
        else if (spec.equals("nfc")) return R.drawable.ic_qs_nfc_on;
        else if (spec.equals("profiles")) return R.drawable.ic_qs_profiles_on;
        else if (spec.equals("sync")) return R.drawable.ic_qs_sync_on;
        else if (spec.equals("volume_panel")) return R.drawable.ic_qs_volume_panel;
        else if (spec.equals("usb_tether")) return R.drawable.ic_qs_usb_tether_on;
        else if (spec.equals("screen_timeout")) return R.drawable.ic_qs_screen_timeout_short_avd;
        else if (spec.equals("performance")) return R.drawable.ic_qs_perf_profile;
        else if (spec.equals("lockscreen")) return R.drawable.ic_qs_lock_screen_on;
        else if (spec.equals("ambient_display")) return R.drawable.ic_qs_ambientdisplay_on;
        else if (spec.equals("live_display")) return R.drawable.ic_livedisplay_auto;
        else if (spec.equals("music")) return R.drawable.ic_qs_media_play;
        else if (spec.equals("brightness")) return R.drawable.ic_qs_brightness_auto_on;
        else if (spec.equals("screen_off")) return R.drawable.ic_qs_power;
        else if (spec.equals("screenshot")) return R.drawable.ic_qs_screenshot;
        else if (spec.equals("expanded_desktop")) return R.drawable.ic_qs_expanded_desktop;
	else if (spec.equals("reboot")) return R.drawable.ic_qs_reboot;
	else if (spec.equals("configurations")) return R.drawable.ic_qs_rrtools;
	else if (spec.equals("heads_up")) return R.drawable.ic_qs_heads_up_on;
	else if (spec.equals("lte")) return R.drawable.ic_qs_lte_on;
	else if (spec.equals("themes")) return R.drawable.ic_qs_themes;
	else if (spec.equals("navbar")) return R.drawable.ic_qs_navbar_on;
	else if (spec.equals("appcirclebar")) return R.drawable.ic_qs_appcirclebar_on;
	else if (spec.equals("kernel_adiutor")) return R.drawable.ic_qs_kernel_adiutor;	
	else if (spec.equals("screenrecord")) return R.drawable.ic_qs_screenrecord;	
	else if (spec.equals("pie")) return R.drawable.ic_qs_pie_on;	
	else if (spec.equals("appsidebar")) return R.drawable.ic_qs_appsidebar_on;
	else if (spec.equals("restartui")) return R.drawable.ic_qs_systemui_restart;
	else if (spec.equals("gesture_anywhere")) return R.drawable.ic_qs_gestures_on;
        else if (spec.equals("battery_saver")) return R.drawable.ic_qs_battery_saver_on;
        else if (spec.equals("power_menu")) return R.drawable.ic_qs_power_menu;
	else if (spec.equals("selinux")) return R.drawable.ic_qs_selinux_enforcing;
        else if (spec.equals("app_picker")) return R.drawable.ic_sysbar_app_picker;
 	else if (spec.equals("kill_app")) return R.drawable.ic_app_kill;
        return 0;
    }

    void updateCustomTile(StatusBarPanelCustomTile sbc) {
        synchronized (mTiles) {
            if (mTiles.containsKey(sbc.getKey())) {
                QSTile<?> tile = mTiles.get(sbc.getKey());
                if (tile instanceof CustomQSTile) {
                    CustomQSTile qsTile = (CustomQSTile) tile;
                    qsTile.update(sbc);
                }
            }
        }
    }

    void addCustomTile(StatusBarPanelCustomTile sbc) {
        synchronized (mTiles) {
            mCustomTileData.add(new CustomTileData.Entry(sbc));
            mTileSpecs.add(sbc.getKey());
            mTiles.put(sbc.getKey(), new CustomQSTile(this, sbc));
            if (mCallback != null) {
                mCallback.onTilesChanged();
            }
        }
    }

    void removeCustomTileSysUi(String key) {
        synchronized (mTiles) {
            if (mTiles.containsKey(key)) {
                mTileSpecs.remove(key);
                mTiles.remove(key);
                mCustomTileData.remove(key);
                if (mCallback != null) {
                    mCallback.onTilesChanged();
                }
            }
        }
    }

    public CustomTileData getCustomTileData() {
        return mCustomTileData;
    }
}
