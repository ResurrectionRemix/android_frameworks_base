
package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.systemui.R;

public class SettingsToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_settings);
        setLabel(R.string.quick_settings_settings_label);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        collapseStatusBar();
        dismissKeyguard();
        startActivity(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setComponent(ComponentName
                .unflattenFromString("com.aokp.romcontrol/.ROMControlActivity"));
        intent.addCategory("android.intent.category.LAUNCHER");

        startActivity(intent);
        return super.onLongClick(v);
    }

}
