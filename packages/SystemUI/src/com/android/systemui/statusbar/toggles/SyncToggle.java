
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.view.View;

import com.android.systemui.R;

public class SyncToggle extends StatefulToggle implements SyncStatusObserver {

    Object mHandle;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);
    }

    @Override
    protected void cleanup() {
        if (mHandle != null) {
            ContentResolver.removeStatusChangeListener(mHandle);
            mHandle = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        ContentResolver.setMasterSyncAutomatically(true);
    }

    @Override
    protected void doDisable() {
        ContentResolver.setMasterSyncAutomatically(false);
    }

    @Override
    protected void updateView() {
        boolean enabled = ContentResolver.getMasterSyncAutomatically();
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        setIcon(enabled
                ? R.drawable.ic_qs_sync_on
                : R.drawable.ic_qs_sync_off);
        setLabel(enabled
                ? mContext.getString(R.string.quick_settings_sync_on_label)
                : mContext.getString(R.string.quick_settings_sync_off_label));
        super.updateView();
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(new Intent(android.provider.Settings.ACTION_SYNC_SETTINGS));
        return super.onLongClick(v);
    }

    @Override
    public void onStatusChanged(int which) {
        scheduleViewUpdate();
    }

}
