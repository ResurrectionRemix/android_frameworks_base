
package com.android.systemui.statusbar.toggles;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class ImeToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setLabel(R.string.quick_settings_ime_label);
        setIcon(R.drawable.ic_qs_ime);
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS);
        return super.onLongClick(v);
    }

    @Override
    public void onClick(View v) {
        vibrateOnTouch();
        collapseStatusBar();
        Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        try {
            pendingIntent.send();
        } catch (CanceledException e) {
        }
    }

}
