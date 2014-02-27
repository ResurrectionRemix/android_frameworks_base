package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;

public class GPSTile extends QuickSettingsTile implements LocationSettingsChangeCallback {

    private QuickSettingsController mQsc;
    private LocationController mLocationController;
    private boolean mLocationEnabled;
    private int mLocationMode;

    public GPSTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mQsc = qsc;
        mLocationController = new LocationController(mContext);
        mLocationController.addSettingsChangedCallback(this);
        mLocationMode = mLocationController.getLocationMode();
        mLocationEnabled = mLocationController.isLocationEnabled();

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLocationController.setLocationEnabled(!mLocationEnabled);
		if (isFlipTilesEnabled()) {
                    flipTile(0);
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mLocationEnabled) {
                    mLocationController.switchLocationMode(mLocationMode);
                }
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        switch (mLocationMode) {
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                mDrawable = R.drawable.ic_qs_location_on_gps;
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                mDrawable = R.drawable.ic_qs_location_on_wifi;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                mDrawable = R.drawable.ic_qs_location_on;
                break;
            case Settings.Secure.LOCATION_MODE_OFF:
                mDrawable = R.drawable.ic_qs_location_off;
                break;
        }
        int textResId = mLocationEnabled ? R.string.quick_settings_location_label
                : R.string.quick_settings_location_off_label;
        mLabel = mContext.getText(textResId).toString();
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled, int locationMode) {
        // collapse all panels in case the confirmation dialog needs to show up
        if ((mLocationMode == Settings.Secure.LOCATION_MODE_SENSORS_ONLY
                        && locationMode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
                || (!mLocationEnabled && locationEnabled
                        && (locationMode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                        || locationMode == Settings.Secure.LOCATION_MODE_BATTERY_SAVING))) {
            mQsc.mBar.collapseAllPanels(true);
        }
        mLocationMode = locationMode;
        mLocationEnabled = locationEnabled;
        updateResources();
    }
}