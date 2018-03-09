/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use mHost file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tileimpl;

import android.content.Context;
import android.util.Log;
import android.view.ContextThemeWrapper;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.*;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.qs.tiles.AdbOverNetworkTile;
<<<<<<< HEAD
import com.android.systemui.qs.tiles.RRTile;
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.AmbientDisplayTile;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CaffeineTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.DataSaverTile;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.ExpandedDesktopTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HeadsUpTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
<<<<<<< HEAD
import com.android.systemui.qs.tiles.LocaleTile;
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
import com.android.systemui.qs.tiles.LiveDisplayTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.MusicTile;
import com.android.systemui.qs.tiles.NavigationBarTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.NightDisplayTile;
import com.android.systemui.qs.tiles.RebootTile;
import com.android.systemui.qs.tiles.PictureInPictureTile;
import com.android.systemui.qs.tiles.PieTile;
import com.android.systemui.qs.tiles.RotationLockTile;
<<<<<<< HEAD
import com.android.systemui.qs.tiles.ScreenrecordTile;
import com.android.systemui.qs.tiles.SyncTile;
import com.android.systemui.qs.tiles.SoundTile;
import com.android.systemui.qs.tiles.UsbTetherTile;
import com.android.systemui.qs.tiles.ScreenshotTile;
import com.android.systemui.qs.tiles.UserTile;
import com.android.systemui.qs.tiles.VolumeTile;
import com.android.systemui.qs.tiles.WeatherTile;
=======
import com.android.systemui.qs.tiles.SyncTile;
import com.android.systemui.qs.tiles.UsbTetherTile;
import com.android.systemui.qs.tiles.UserTile;
import com.android.systemui.qs.tiles.VolumeTile;
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.qs.tiles.WorkModeTile;
import com.android.systemui.qs.QSTileHost;

public class QSFactoryImpl implements QSFactory {

    private static final String TAG = "QSFactory";
    private final QSTileHost mHost;

    public QSFactoryImpl(QSTileHost host) {
        mHost = host;
    }

    public QSTile createTile(String tileSpec) {
        if (tileSpec.equals("wifi")) return new WifiTile(mHost);
        else if (tileSpec.equals("bt")) return new BluetoothTile(mHost);
        else if (tileSpec.equals("cell")) return new CellularTile(mHost);
        else if (tileSpec.equals("dnd")) return new DndTile(mHost);
        else if (tileSpec.equals("inversion")) return new ColorInversionTile(mHost);
        else if (tileSpec.equals("airplane")) return new AirplaneModeTile(mHost);
        else if (tileSpec.equals("work")) return new WorkModeTile(mHost);
        else if (tileSpec.equals("rotation")) return new RotationLockTile(mHost);
        else if (tileSpec.equals("flashlight")) return new FlashlightTile(mHost);
        else if (tileSpec.equals("location")) return new LocationTile(mHost);
        else if (tileSpec.equals("cast")) return new CastTile(mHost);
        else if (tileSpec.equals("hotspot")) return new HotspotTile(mHost);
        else if (tileSpec.equals("user")) return new UserTile(mHost);
        else if (tileSpec.equals("battery")) return new BatterySaverTile(mHost);
        else if (tileSpec.equals("saver")) return new DataSaverTile(mHost);
        else if (tileSpec.equals("night")) return new NightDisplayTile(mHost);
        else if (tileSpec.equals("nfc")) return new NfcTile(mHost);
        // Custom tiles.
        else if (tileSpec.equals("adb_network")) return new AdbOverNetworkTile(mHost);
        else if (tileSpec.equals("ambient_display")) return new AmbientDisplayTile(mHost);
        else if (tileSpec.equals("caffeine")) return new CaffeineTile(mHost);
        else if (tileSpec.equals("heads_up")) return new HeadsUpTile(mHost);
        else if (tileSpec.equals("livedisplay")) return new LiveDisplayTile(mHost);
        else if (tileSpec.equals("sync")) return new SyncTile(mHost);
        else if (tileSpec.equals("usb_tether")) return new UsbTetherTile(mHost);
        else if (tileSpec.equals("volume_panel")) return new VolumeTile(mHost);
<<<<<<< HEAD
        else if (tileSpec.equals("reboot")) return new RebootTile(mHost);
        else if (tileSpec.equals("screenshot")) return new ScreenshotTile(mHost);
        else if (tileSpec.equals("configurations")) return new RRTile(mHost);
        else if (tileSpec.equals("locale")) return new LocaleTile(mHost);
        else if (tileSpec.equals("compass")) return new CompassTile(mHost);
        else if (tileSpec.equals("music")) return new MusicTile(mHost);
        else if (tileSpec.equals("sound")) return new SoundTile(mHost);
        else if (tileSpec.equals("pip")) return new PictureInPictureTile(mHost);
        else if (tileSpec.equals("screenrecord")) return new ScreenrecordTile(mHost);
        else if (tileSpec.equals("weather")) return new WeatherTile(mHost);
        else if (tileSpec.equals("pie")) return new PieTile(mHost);
        else if (tileSpec.equals("expanded_desktop")) return new ExpandedDesktopTile(mHost);
        else if (tileSpec.equals("navigation")) return new NavigationBarTile(mHost);
=======
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017
        // Intent tiles.
        else if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(mHost, tileSpec);
        else if (tileSpec.startsWith(CustomTile.PREFIX)) return CustomTile.create(mHost, tileSpec);
        else {
            Log.w(TAG, "Bad tile spec: " + tileSpec);
            return null;
        }
    }

    @Override
    public QSTileView createTileView(QSTile tile, boolean collapsedView) {
        Context context = new ContextThemeWrapper(mHost.getContext(), R.style.qs_theme);
        QSIconView icon = tile.createTileView(context);
        if (collapsedView) {
            return new QSTileBaseView(context, icon, collapsedView);
        } else {
            return new com.android.systemui.qs.tileimpl.QSTileView(context, icon);
        }
    }
}
