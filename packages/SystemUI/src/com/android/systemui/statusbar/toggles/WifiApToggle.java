
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.view.View;

import com.android.systemui.R;

public class WifiApToggle extends StatefulToggle {

    private WifiManager wifiManager;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        onWifiTetherChanged();
        IntentFilter wifiFilter = new IntentFilter(
                WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                onWifiTetherChanged();
            }
        }, wifiFilter);
    }

    private void changeWifiApState(final boolean desiredState) {
        if (wifiManager == null) {
            return;
        }

        AsyncTask.execute(new Runnable() {
            public void run() {
                int wifiState = wifiManager.getWifiState();
                if (desiredState
                        && ((wifiState == WifiManager.WIFI_STATE_ENABLING)
                        || (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
                    wifiManager.setWifiEnabled(false);
                }

                wifiManager.setWifiApEnabled(null, desiredState);
                return;
            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
        collapseStatusBar();
        dismissKeyguard();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.settings",
                "com.android.settings.Settings$TetherSettingsActivity"));
        startActivity(intent);
        return super.onLongClick(v);
    }

    public void onWifiTetherChanged() {
        wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        int mWifiApState = wifiManager.getWifiApState();
        boolean enabled = mWifiApState == WifiManager.WIFI_AP_STATE_ENABLED
                || mWifiApState == WifiManager.WIFI_AP_STATE_ENABLING;
        setEnabledState(enabled);
        setIcon(enabled
                ? R.drawable.ic_qs_wifi_tether_on
                : R.drawable.ic_qs_wifi_tether_off);
        setLabel(enabled
                ? mContext.getString(R.string.quick_settings_wifi_tether_on_label)
                : mContext.getString(R.string.quick_settings_wifi_tether_off_label));

    }

    @Override
    protected void doEnable() {
        changeWifiApState(true);
    }

    @Override
    protected void doDisable() {
        changeWifiApState(false);
    }
}
