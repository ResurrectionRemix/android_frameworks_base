package com.android.systemui.statusbar.toggles;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;

public class RebootToggle extends BaseToggle {

    private PowerManager pm;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_reboot);
        setLabel(R.string.quick_settings_reboot);

        pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void onClick(View v) {
        collapseStatusBar();
        dismissKeyguard();
        Intent intent = new Intent(Intent.ACTION_REBOOTMENU);
        mContext.sendBroadcast(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        collapseStatusBar();
        rebootToggleDialog(mContext);
        mHandler.postDelayed(mRunnable, 2000); // allow time to show dialog

        return super.onLongClick(v);
    }

    private static void rebootToggleDialog(Context context) {
        ProgressDialog pd = new ProgressDialog(context);
        pd.setMessage(context.getText(R.string.quick_settings_reboot_message));
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);

        pd.show();
    }

    private Runnable mRunnable = new Runnable() {
        public void run() {
            pm.reboot(null);
        }
    };

}
