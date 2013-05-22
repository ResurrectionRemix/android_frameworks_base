
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.view.View;

import com.android.internal.view.RotationPolicy;
import com.android.internal.view.RotationPolicy.RotationPolicyListener;
import com.android.systemui.R;

public class RotateToggle extends StatefulToggle {

    private RotationPolicyListener mListener = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        RotationPolicy.registerRotationPolicyListener(mContext,
                mListener = new RotationPolicyListener() {

                    @Override
                    public void onChange() {
                        scheduleViewUpdate();
                    }
                }, UserHandle.USER_ALL);
    }

    @Override
    protected void cleanup() {
        if (mListener != null) {
            RotationPolicy.unregisterRotationPolicyListener(mContext, mListener);
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        RotationPolicy.setRotationLock(mContext, true);
        updateCurrentState(State.ENABLED);
    }

    @Override
    protected void doDisable() {
        RotationPolicy.setRotationLock(mContext, false);
        updateCurrentState(State.DISABLED);
    }

    @Override
    protected void updateView() {
        boolean lock = RotationPolicy.isRotationLocked(mContext);
        setIcon(lock ? R.drawable.ic_qs_rotation_locked : R.drawable.ic_qs_auto_rotate);
        setLabel(lock ? R.string.quick_settings_rotation_locked_label
                : R.string.quick_settings_rotation_unlocked_label);
        updateCurrentState(lock ? State.ENABLED : State.DISABLED);
        super.updateView();
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
        startActivity(intent);
        return super.onLongClick(v);
    }

}
