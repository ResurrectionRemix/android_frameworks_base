
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;

public class LocationToggle extends StatefulToggle implements LocationSettingsChangeCallback {

    private boolean mLocationEnabled;

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
       getLocationController().setLocationEnabled(true);
       updateCurrentState(State.ENABLING);
       collapseStatusBar();
    }

    @Override
    protected void doDisable() {
        getLocationController().setLocationEnabled(false);
        updateCurrentState(State.DISABLED);
    }

    @Override
    protected void updateView() {
        setLabel(mLocationEnabled ? R.string.quick_settings_location_label
                : R.string.quick_settings_location_off_label);
       if (mLocationEnabled) {
            setIcon(R.drawable.ic_qs_location_on);
        } else {
            setIcon(R.drawable.ic_qs_location_off);
        }
        super.updateView();
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        setEnabledState(mLocationEnabled = locationEnabled);
        scheduleViewUpdate();
    }
}
