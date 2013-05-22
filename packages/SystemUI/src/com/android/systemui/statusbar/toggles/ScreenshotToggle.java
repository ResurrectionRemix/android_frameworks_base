
package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.systemui.R;

public class ScreenshotToggle extends BaseToggle {

    private Handler mHandler = new Handler();

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_screenshot);
        setLabel(R.string.quick_settings_screenshot);
    }

    @Override
    public void onClick(View v) {
        collapseStatusBar();
        // just enough delay for statusbar to collapse
        mHandler.postDelayed(mRunnable, 500);
    }

    @Override
    public boolean onLongClick(View v) {
        collapseStatusBar();
        int delay = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_TOGGLE_DELAY, 5000);
        final Toast toast = Toast.makeText(mContext,
                String.format(mContext.getResources().getString(R.string.screenshot_toast),
                        delay / 1000), Toast.LENGTH_SHORT);
        toast.show();
        // toast duration is not customizable, hack to show it only for 1 sec
        mHandler.postDelayed(new Runnable() {
            public void run() {
                toast.cancel();
            }
        }, 1000);
        mHandler.postDelayed(mRunnable, delay);
        return super.onLongClick(v);
    }

    private Runnable mRunnable = new Runnable() {
        public void run() {
            Intent intent = new Intent(Intent.ACTION_SCREENSHOT);
            mContext.sendBroadcast(intent);
        }
    };
}
