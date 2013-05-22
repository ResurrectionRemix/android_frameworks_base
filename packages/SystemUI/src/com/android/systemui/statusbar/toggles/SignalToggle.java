
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public class SignalToggle extends StatefulToggle implements NetworkSignalChangedCallback {

    private WifiState mWifiState = new WifiState();
    private RSSIState mRSSIState = new RSSIState();

    private ImageView rssiImage;
    private ImageView rssiOverlayImage;

    private ConnectivityManager connManager;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_signal_no_signal);
        setLabel(R.string.quick_settings_rssi_emergency_only);
        connManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void cleanup() {
        connManager = null;
        super.cleanup();
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            String wifiSignalContentDescription, String enabledDesc) {
        boolean wifiConnected = enabled && (wifiSignalIconId > 0) && (enabledDesc != null);
        mWifiState.enabled = enabled;
        mWifiState.connected = wifiConnected;
    }

    @Override
    public void onMobileDataSignalChanged(
            boolean enabled, int mobileSignalIconId, String signalContentDescription,
            int dataTypeIconId, String dataContentDescription, String enabledDesc) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mRSSIState.signalIconId = enabled && (mobileSignalIconId > 0)
                ? mobileSignalIconId
                : R.drawable.ic_qs_signal_no_signal;
        mRSSIState.signalContentDescription = enabled && (mobileSignalIconId > 0)
                ? signalContentDescription
                : r.getString(R.string.accessibility_no_signal);
        mRSSIState.dataTypeIconId = enabled && (dataTypeIconId > 0) && !mWifiState.enabled
                ? dataTypeIconId
                : 0;
        mRSSIState.dataContentDescription = enabled && (dataTypeIconId > 0)
                && !mWifiState.enabled
                ? dataContentDescription
                : r.getString(R.string.accessibility_no_data);
        mRSSIState.label = enabled
                ? removeTrailingPeriod(enabledDesc)
                : r.getString(R.string.quick_settings_rssi_emergency_only);
        setEnabledState(dataTypeIconId > 0 ? true : false);
        scheduleViewUpdate();
    }

    @Override
    protected void updateView() {
        setLabel(mRSSIState.label);
        if (rssiImage != null) {
            rssiImage.setImageResource(mRSSIState.signalIconId);
        }
        if (rssiOverlayImage != null) {
            rssiOverlayImage.setImageResource(mRSSIState.dataTypeIconId);
        }
        super.updateView();
    }

    public static class RSSIState {
        int signalIconId;
        String signalContentDescription;
        int dataTypeIconId;
        String dataContentDescription;
        boolean enabled;
        String label;
    }

    public static class WifiState {
        boolean enabled;
        boolean connected;
    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView root = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile_signal, null);
        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        mLabel = (TextView) root.findViewById(R.id.rssi_textview);
        rssiImage = (ImageView) root.findViewById(R.id.rssi_image);
        rssiOverlayImage = (ImageView) root.findViewById(R.id.rssi_overlay_image);
        setIcon(null);
        return root;
    }

    @Override
    public View createTraditionalView() {
        View root = View.inflate(mContext, R.layout.toggle_traditional_signal, null);
        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        rssiImage = (ImageView) root.findViewById(R.id.rssi_image);
        rssiOverlayImage = (ImageView) root.findViewById(R.id.rssi_overlay_image);
        mLabel = null;
        mIcon = null;
        return root;
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {

    }

    @Override
    protected void doEnable() {
        connManager.setMobileDataEnabled(true);
        // String strData = connManager.getMobileDataEnabled() ?
        // mContext.getString(R.string.quick_settings_data_on_label)
        // : mContext.getString(R.string.quick_settings_data_off_label);
        // Toast.makeText(mContext, strData, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void doDisable() {
        connManager.setMobileDataEnabled(false);
        // String strData = connManager.getMobileDataEnabled() ?
        // mContext.getString(R.string.quick_settings_data_on_label)
        // : mContext.getString(R.string.quick_settings_data_off_label);
        // Toast.makeText(mContext, strData, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        startActivity(intent);
        return super.onLongClick(v);
    }

    private static int getCurrentPreferredNetworkMode(Context context) {
        int network = -1;
        try {
            network = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return network;
    }

}
