
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.view.View;

import com.android.systemui.R;

public class NfcToggle extends StatefulToggle {

    NfcAdapter mNfcAdapter = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(c);
        if (mNfcAdapter != null) {
            setEnabledState(mNfcAdapter.isEnabled());
        }

        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final boolean enabled = (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                        NfcAdapter.STATE_OFF) == NfcAdapter.STATE_ON);
                if (mNfcAdapter == null) {
                    mNfcAdapter = NfcAdapter.getDefaultAdapter();
                }
                updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
            }
        }, new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_NFC_SETTINGS);
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        toggleNfc(true);
    }

    @Override
    protected void doDisable() {
        toggleNfc(false);
    }

    @Override
    protected void updateView() {
        switch (getState()) {
            case ENABLED:
                setIcon(R.drawable.ic_qs_nfc_on);
                setLabel(R.string.quick_settings_nfc_on_label);
                break;
            case DISABLED:
                setIcon(R.drawable.ic_qs_nfc_off);
                setLabel(R.string.quick_settings_nfc_off_label);
                break;
        }
        super.updateView();
    }

    private void toggleNfc(boolean state) {
        if (mNfcAdapter == null) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter();
        }
        try {
            if (state) {
                mNfcAdapter.enable();
            } else {
                mNfcAdapter.disable();
            }
        } catch (NullPointerException ex) {
            // swallow
        }
    }
}
