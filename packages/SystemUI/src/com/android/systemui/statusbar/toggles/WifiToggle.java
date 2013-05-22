
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public class WifiToggle extends StatefulToggle implements NetworkSignalChangedCallback {

    private WifiManager wifiManager;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setInfo(mContext.getString(R.string.quick_settings_wifi_off_label),
                R.drawable.ic_qs_wifi_no_network);
        updateCurrentState(State.DISABLED);

        wifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
    }

    private void changeWifiState(final boolean desiredState) {
        if (wifiManager == null) {
            return;
        }

        AsyncTask.execute(new Runnable() {
            public void run() {
                int wifiApState = wifiManager.getWifiApState();
                if (desiredState
                        && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING)
                        || (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
                    wifiManager.setWifiApEnabled(null, false);
                }

                wifiManager.setWifiEnabled(desiredState);
                return;
            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
        return super.onLongClick(v);
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            String wifiSignalContentDescription, String enabledDesc) {
        Resources r = mContext.getResources();

        boolean wifiConnected = enabled && (wifiSignalIconId > 0) && (enabledDesc != null);
        boolean wifiNotConnected = (wifiSignalIconId > 0) && (enabledDesc == null);

        String label;
        int iconId;
        State newState = getState();
        if (wifiConnected) {
            iconId = wifiSignalIconId;
            label = enabledDesc;
            newState = State.ENABLED;
        } else if (wifiNotConnected) {
            iconId = R.drawable.ic_qs_wifi_0;
            label = r.getString(R.string.quick_settings_wifi_label);
            newState = State.ENABLED;
        } else {
            iconId = R.drawable.ic_qs_wifi_no_network;
            label = r.getString(R.string.quick_settings_wifi_off_label);
            newState = State.DISABLED;
        }
        updateCurrentState(newState);
        setInfo(removeDoubleQuotes(label), iconId);

    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
            String mobileSignalContentDescriptionId, int dataTypeIconId,
            String dataTypeContentDescriptionId, String description) {
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
    }

    @Override
    protected void doEnable() {
        changeWifiState(true);
    }

    @Override
    protected void doDisable() {
        changeWifiState(false);
    }
}
