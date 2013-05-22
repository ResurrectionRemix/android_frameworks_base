
package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.os.PowerManager;
import android.os.SystemClock;

import com.android.systemui.R;

public class SleepToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_sleep);
        setLabel(R.string.quick_settings_sleep);
    }

    @Override
    public void onClick(View v) {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }

}
