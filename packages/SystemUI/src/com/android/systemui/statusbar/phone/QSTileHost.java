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
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import android.widget.RemoteViews;
import android.util.Log;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.tiles.AdbOverNetworkTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.AmbientDisplayTile;
import com.android.systemui.qs.tiles.AppPickerTile;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.BrightnessTile;
import com.android.systemui.qs.tiles.CaffeineTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HeadsUpTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LiveDisplayTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.AppCircleBarTile;
import com.android.systemui.qs.tiles.ExpandedDesktopTile;
import com.android.systemui.qs.tiles.KernelAdiutorTile;
import com.android.systemui.qs.tiles.HardwareKeysTile;
import com.android.systemui.qs.tiles.GestureAnyWhereTile;
import com.android.systemui.qs.tiles.ScreenrecordTile;
import com.android.systemui.qs.tiles.MusicTile;
import com.android.systemui.qs.tiles.RRTile;
import com.android.systemui.qs.tiles.KillAppTile;
import com.android.systemui.qs.tiles.LteTile;
import com.android.systemui.qs.tiles.ThemesTile;
import com.android.systemui.qs.tiles.NavigationBarTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.PerfProfileTile;
import com.android.systemui.qs.tiles.ProfilesTile;
import com.android.systemui.qs.tiles.RebootTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.ScreenOffTile;
import com.android.systemui.qs.tiles.PowerMenuTile;
import com.android.systemui.qs.tiles.ScreenshotTile;
import com.android.systemui.qs.tiles.ScreenTimeoutTile;
import com.android.systemui.qs.tiles.SoundTile;
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

    protected static final String TILES_SETTING = "sysui_qs_tiles";

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
    private Handler mHandler;

    private CustomTileData mCustomTileData;

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

        final HandlerThread ht = new HandlerThread(QSTileHost.class.getSimpleName(),
                Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        mLooper = ht.getLooper();

        TunerService.get(mContext).addTunable(this, TILES_SETTING);
    }

    public void destroy() {
        TunerService.get(mContext).removeTunable(this);
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public Collection<QSTile<?>> getTiles() {
        return mTiles.values();
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
        if (!TILES_SETTING.equals(key)) {
            return;
        }
        if (DEBUG) Log.d(TAG, "Recreating tiles");
        final List<String> tileSpecs = loadTileSpecs(newValue);
        if (tileSpecs.equals(mTileSpecs)) return;
        for (Map.Entry<String, QSTile<?>> tile : mTiles.entrySet()) {
            if (!tileSpecs.contains(tile.getKey())) {
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
                    newTiles.put(tileSpec, createTile(tileSpec));
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


    protected QSTile<?> createTile(String tileSpec) {
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
	else if (tileSpec.equals("adb_network")) return new AdbOverNetworkTile(this);
	else if (tileSpec.equals("compass")) return new CompassTile(this);
	else if (tileSpec.equals("nfc")) return new NfcTile(this);
	else if (tileSpec.equals("profiles")) return new ProfilesTile(this);
	else if (tileSpec.equals("sync")) return new SyncTile(this);
	else if (tileSpec.equals("volume_panel")) return new VolumeTile(this);
	else if (tileSpec.equals("usb_tether")) return new UsbTetherTile(this);
	else if (tileSpec.equals("screen_timeout")) return new ScreenTimeoutTile(this);
	else if (tileSpec.equals("performance")) return new PerfProfileTile(this);
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
	else if (tileSpec.equals("navbar")) return new NavigationBarTile(this);
	else if (tileSpec.equals("appcirclebar")) return new AppCircleBarTile(this);
	else if (tileSpec.equals("kernel_adiutor")) return new KernelAdiutorTile(this);
	else if (tileSpec.equals("screenrecord")) return new ScreenrecordTile(this);
	else if (tileSpec.equals("gesture_anywhere")) return new GestureAnyWhereTile(this);
	else if (tileSpec.equals("battery_saver")) return new BatterySaverTile(this);
	else if (tileSpec.equals("power_menu")) return new PowerMenuTile(this);
	else if (tileSpec.equals("app_picker")) return new AppPickerTile(this);
	else if (tileSpec.equals("kill_app")) return new KillAppTile(this);
	else if (tileSpec.equals("caffeine")) return new CaffeineTile(this);
	else if (tileSpec.equals("hw_keys")) return new HardwareKeysTile(this);	
	else if (tileSpec.equals("sound")) return new SoundTile(this);
	else if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(this,tileSpec);
	else throw new IllegalArgumentException("Bad tile spec: " + tileSpec);
    }

    protected List<String> loadTileSpecs(String tileList) {
        final Resources res = mContext.getResources();
        final String defaultTileList = res.getString(R.string.quick_settings_tiles_default);
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
        return tiles;
    }
}
