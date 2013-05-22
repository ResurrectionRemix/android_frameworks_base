
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import com.android.systemui.R;

public class NavbarHideToggle extends StatefulToggle {

    SettingsObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mObserver = new SettingsObserver(mHandler);
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
    protected void doEnable() {
        Settings.System.putBoolean(mContext.getContentResolver(),
                Settings.System.NAV_HIDE_ENABLE, true);
        Settings.System.putBoolean(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW_NOW, false);
    }

    @Override
    protected void doDisable() {
        Settings.System.putBoolean(mContext.getContentResolver(),
                Settings.System.NAV_HIDE_ENABLE, false);
        Settings.System.putBoolean(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW_NOW, true);
    }

    @Override
    protected void updateView() {
        final boolean enabled = Settings.System.getBoolean(mContext.getContentResolver(),
                Settings.System.NAV_HIDE_ENABLE, false);
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_qs_navbar_hide_on : R.drawable.ic_qs_navbar_hide_off);
        setLabel(enabled ? R.string.quick_settings_navbar_hide_on_label
                : R.string.quick_settings_navbar_hide_off_label);
        super.updateView();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.System.NAV_HIDE_ENABLE), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

}
