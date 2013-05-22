
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;

public class GpsToggle extends StatefulToggle implements LocationGpsStateChangeCallback {

    private boolean mGpsFix;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                LocationManager.GPS_PROVIDER, true);
        updateCurrentState(State.ENABLED);
    }

    @Override
    protected void doDisable() {
        Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                LocationManager.GPS_PROVIDER, false);
        updateCurrentState(State.DISABLED);
    }

    @Override
    protected void updateView() {
        boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
        setEnabledState(gpsEnabled);
        setLabel(gpsEnabled ? R.string.quick_settings_gps_on_label
                : R.string.quick_settings_gps_off_label);

       if (gpsEnabled && mGpsFix) {
            setIcon(R.drawable.ic_qs_gps_locked);
        } else if (gpsEnabled) {
            setIcon(R.drawable.ic_qs_gps_on);
        } else {
            setIcon(R.drawable.ic_qs_gps_off);
        }
        super.updateView();
    }

    @Override
    public void onLocationGpsStateChanged(boolean inUse, boolean hasFix, String description) {
        setEnabledState(inUse);
        mGpsFix = hasFix;
        scheduleViewUpdate();
    }
}
