
package com.android.systemui.statusbar.toggles;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;

import com.android.systemui.R;

public class BluetoothToggle extends StatefulToggle {
    private BluetoothAdapter mBluetoothAdapter;

    public void init(Context c, int style) {
        super.init(c, style);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return;
        }
        onBluetoothChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerBroadcastReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBluetoothChanged();
            }
        }, filter);
    }

    private void onBluetoothChanged() {
        String label = null;
        int iconId = 0;
        State newState = getState();
        switch (mBluetoothAdapter.getState()) {
            case BluetoothAdapter.STATE_ON:
                newState = State.ENABLED;
                switch (mBluetoothAdapter.getConnectionState()) {
                    case BluetoothAdapter.STATE_CONNECTED:
                        iconId = R.drawable.ic_qs_bluetooth_on;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                    case BluetoothAdapter.STATE_DISCONNECTED:
                    case BluetoothAdapter.STATE_DISCONNECTING:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        break;
                }
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                iconId = R.drawable.ic_qs_bluetooth_not_connected;
                label = mContext.getString(R.string.quick_settings_bluetooth_label);
                newState = State.ENABLING;
                break;
            case BluetoothAdapter.STATE_OFF:
                iconId = R.drawable.ic_qs_bluetooth_off;
                label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                newState = State.DISABLED;
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                iconId = R.drawable.ic_qs_bluetooth_off;
                label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                newState = State.DISABLING;
                break;
        }
        setInfo(label, iconId);
        updateCurrentState(newState);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        BluetoothAdapter.getDefaultAdapter().enable();
    }

    @Override
    protected void doDisable() {
        BluetoothAdapter.getDefaultAdapter().disable();
    }
}
