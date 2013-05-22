
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public class AirplaneModeToggle extends StatefulToggle implements NetworkSignalChangedCallback {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        boolean enabled = Settings.Global.getInt(c.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        setLabel(R.string.quick_settings_airplane_mode_label);
        setIcon(enabled ? R.drawable.ic_qs_airplane_on : R.drawable.ic_qs_airplane_off);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
    }

    @Override
    protected void doEnable() {
        setAirplaneModeState(true);
    }

    @Override
    protected void doDisable() {
        setAirplaneModeState(false);
    }

    private void setAirplaneModeState(boolean enabled) {
        // TODO: Sets the view to be "awaiting" if not already awaiting

        // Change the system setting
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                enabled ? 1 : 0);

        updateCurrentState(State.ENABLED);
        scheduleViewUpdate();

        // Post the intent
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabled);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            String wifitSignalContentDescriptionId, String description) {
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
            String mobileSignalContentDescriptionId, int dataTypeIconId,
            String dataTypeContentDescriptionId, String description) {
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        setLabel(R.string.quick_settings_airplane_mode_label);
        setIcon(enabled ? R.drawable.ic_qs_airplane_on : R.drawable.ic_qs_airplane_off);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        scheduleViewUpdate();
    }
}
