
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.view.View;

import com.android.systemui.R;

public class UsbTetherToggle extends StatefulToggle {

    ConnectivityManager connManager;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        connManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                scheduleViewUpdate();
            }
        }, new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED));
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

    @Override
    protected void updateView() {
        boolean enabled = isUsbTetheringEnabled();
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        setIcon(enabled
                ? R.drawable.ic_qs_usb_tether_on
                : R.drawable.ic_qs_usb_tether_off);
        setLabel(enabled
                ? R.string.quick_settings_usb_tether_on_label
                : R.string.quick_settings_usb_tether_off_label);
        super.updateView();
    }

    public boolean isUsbTetheringEnabled() {
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        String[] mUsbRegexs = connManager.getTetherableUsbRegexs();
        String[] tethered = connManager.getTetheredIfaces();
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    protected void doEnable() {
        if (connManager.setUsbTethering(true) == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            scheduleViewUpdate();
        }
    }

    @Override
    protected void doDisable() {
        if (connManager.setUsbTethering(false) == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            scheduleViewUpdate();
        }
    }
}
