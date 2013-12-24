package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class WirelessAdbToggle extends StatefulToggle {

    private static final String PORT = "5555";

    private boolean enabled;
    private int mAdbEnabled;

    private ConnectivityManager mConnMgr;
    private WifiManager mWifiMgr;

    private Handler mHandler = new Handler();

    SettingsObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mConnMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiMgr = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        toggleADB(true);
    }

    @Override
    protected void doDisable() {
        toggleADB(false);
    }

    @Override
    public void updateView() {
        mAdbEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                              Settings.Global.ADB_ENABLED, 0);

        if (System.getProperty("service.adb.tcp.port") == PORT) {
            if (mWifiMgr.isWifiEnabled()) { // Check if port is open then check isWifiEnabled()
                enabled = true;
            }
        } else {
            enabled = false;
        }
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_qs_adb_on : R.drawable.ic_qs_adb_off);
        setLabel(enabled ? mContext.getString(R.string.quick_settings_adb_on, getWifiIp())
                 : mContext.getString(R.string.quick_settings_adb_off));
        super.updateView();
    }

    private void toggleADB(boolean state) {
        mHandler.postDelayed(new ToggleRunnable(state), 1000);
    }

    protected String getWifiIp() {
        int ip = mWifiMgr.getConnectionInfo().getIpAddress();
        String wifiIp = (ip & 0xFF) + "." +
                        ((ip >> 8) & 0xff) + "." +
                        ((ip >> 16) & 0xff) + "." +
                        ((ip >> 24) & 0xff);
        return wifiIp;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.Global.ADB_ENABLED), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    class ToggleRunnable implements Runnable {
        private boolean state;

        public ToggleRunnable(boolean state) {
            this.state = state;
        }

        public void run() {
            if (state) {
                System.setProperty("service.adb.tcp.port", PORT);
            } else {
                System.setProperty("service.adb.tcp.port", "-1");
            }

            try {
                /** Cycle ADB */
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.ADB_ENABLED, 0);
                Thread.sleep(500);
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.ADB_ENABLED, 1);
            } catch (InterruptedException iex) {
                /** Swallow it */
            }
        }
    }
}

