
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class BrightnessToggle extends BaseToggle {

    SettingsObserver mObserver;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mObserver = new SettingsObserver(new Handler());
        mObserver.observe();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    public void onClick(View v) {
        vibrateOnTouch();
        collapseStatusBar();

        Intent intent = new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT_OR_SELF);

    }

    @Override
    public boolean onLongClick(View v) {
        dismissKeyguard();
        collapseStatusBar();
        startActivity(new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS));
        return super.onLongClick(v);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false,
                    this, UserHandle.USER_ALL);
            scheduleViewUpdate();
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    protected void updateView() {
        int mode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                UserHandle.USER_CURRENT);
        boolean autoBrightness =
                (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        int iconId = autoBrightness
                ? R.drawable.ic_qs_brightness_auto_on
                : R.drawable.ic_qs_brightness_auto_off;
        int label = R.string.quick_settings_brightness_label;

        setIcon(iconId);
        setLabel(label);
        super.updateView();
    }

}
