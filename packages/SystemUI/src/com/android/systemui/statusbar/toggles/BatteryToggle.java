
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryToggle extends BaseToggle implements BatteryStateChangeCallback {

    LevelListDrawable mBatteryLevels;
    LevelListDrawable mChargingBatteryLevels;


    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mBatteryLevels = (LevelListDrawable) c.getResources()
                .getDrawable(R.drawable.qs_sys_battery);
        mChargingBatteryLevels =
                (LevelListDrawable) c.getResources()
                        .getDrawable(R.drawable.qs_sys_battery_charging);
    }

    @Override
    public void onClick(View v) {
        startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_DREAM_SETTINGS);
        return super.onLongClick(v);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        Drawable d = null;
        if (pluggedIn) {
            d = mChargingBatteryLevels;
        } else {
            d = mBatteryLevels;
        }
        setIcon(d);
        setIconLevel(level);

        if (level == 100) {
            setLabel(R.string.quick_settings_battery_charged_label);
        } else {
            setLabel(pluggedIn ?
                    mContext.getString(R.string.quick_settings_battery_charging_label,
                            level)
                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                            level));
        }
        scheduleViewUpdate();
    }
}
