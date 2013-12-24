package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class StayAwakeToggle extends StatefulToggle {

    private static final String TAG = "AOKPInsomnia";
    private static final String KEY_USER_TIMEOUT = "user_timeout";
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
    private static final int neverSleep = Integer.MAX_VALUE; // MAX_VALUE equates to approx 24 days

    private boolean enabled;
    private int storedUserTimeout;

    SettingsObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        SharedPreferences shared = mContext.getSharedPreferences(KEY_USER_TIMEOUT,
                Context.MODE_PRIVATE);
        storedUserTimeout = shared.getInt("timeout", FALLBACK_SCREEN_TIMEOUT_VALUE);
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
    public boolean onLongClick(View v) {
       startActivity(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
       return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        toggleInsomnia(true);
    }

    @Override
    protected void doDisable() {
        toggleInsomnia(false);
    }

    @Override
    public void updateView() {
        int currentTimeout = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);

        if (currentTimeout == neverSleep) {
            enabled = true;
        } else {
            enabled = false;
        }
        setEnabledState(enabled);
        setLabel(enabled ? R.string.quick_settings_stayawake_on
                : R.string.quick_settings_stayawake_off);
        setIcon(enabled ? R.drawable.ic_qs_stayawake_on : R.drawable.ic_qs_stayawake_off);
        super.updateView();
    }

    protected void toggleInsomnia(boolean state) {
        if (state) {
            saveUserTimeout();
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, neverSleep);
        } else {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, storedUserTimeout);
            saveUserTimeout(); // save here incase of manual change
        }
    }

    private void saveUserTimeout() {
        storedUserTimeout = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
        SharedPreferences shared = mContext.getSharedPreferences(KEY_USER_TIMEOUT,
                Context.MODE_PRIVATE);
        shared.edit().putInt("timeout", storedUserTimeout).commit();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }
}
