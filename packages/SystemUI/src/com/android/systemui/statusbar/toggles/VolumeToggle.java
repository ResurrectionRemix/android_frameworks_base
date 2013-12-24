
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class VolumeToggle extends BaseToggle {

    SettingsObserver mObserver;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_volume);
        setLabel(R.string.quick_settings_volume);
    }

    @Override
    public void onClick(View v) {
        vibrateOnTouch();
        collapseStatusBar();

        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    @Override
    public boolean onLongClick(View v) {
        dismissKeyguard();
        collapseStatusBar();
        startActivity(new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));
        return super.onLongClick(v);
    }

}
