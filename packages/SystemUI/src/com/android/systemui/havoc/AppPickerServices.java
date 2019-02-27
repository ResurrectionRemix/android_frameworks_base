package com.android.systemui.havoc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.VendorServices;

import java.util.ArrayList;

public class AppPickerServices extends VendorServices {

    private ArrayList<Object> mServices = new ArrayList();

    private void addService(Object obj) {
        if (obj != null) {
            this.mServices.add(obj);
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_PACKAGE_REMOVED)) {

                String packageName = intent.getData().getSchemeSpecificPart();

                String backLongPress = Settings.System.getString(context.getContentResolver(),
                        Settings.System.KEY_BACK_LONG_PRESS_CUSTOM_APP);
                String backDoubleTap = Settings.System.getString(context.getContentResolver(),
                        Settings.System.KEY_BACK_DOUBLE_TAP_CUSTOM_APP);
                String homeLongPress = Settings.System.getString(context.getContentResolver(),
                        Settings.System.KEY_HOME_LONG_PRESS_CUSTOM_APP);
                String homeDoubleTap = Settings.System.getString(context.getContentResolver(),
                        Settings.System.KEY_HOME_DOUBLE_TAP_CUSTOM_APP);
                String appSwitchLongPress = Settings.System.getString(context.getContentResolver(),
                        Settings.System.KEY_APP_SWITCH_LONG_PRESS_CUSTOM_APP);
                String appSwitchDoubleTap = Settings.System.getString(context.getContentResolver(),
                        Settings.System.KEY_APP_SWITCH_DOUBLE_TAP_CUSTOM_APP);

                if (packageName.equals(backLongPress)) {
                    resetAction(Settings.System.KEY_BACK_LONG_PRESS_ACTION,
                            Settings.System.KEY_BACK_LONG_PRESS_CUSTOM_APP_FR_NAME);
                }
                if (packageName.equals(backDoubleTap)) {
                    resetAction(Settings.System.KEY_BACK_DOUBLE_TAP_ACTION,
                            Settings.System.KEY_BACK_DOUBLE_TAP_CUSTOM_APP_FR_NAME);
                }
                if (packageName.equals(homeLongPress)) {
                    resetAction(Settings.System.KEY_HOME_LONG_PRESS_ACTION,
                            Settings.System.KEY_HOME_LONG_PRESS_CUSTOM_APP_FR_NAME);
                }
                if (packageName.equals(homeDoubleTap)) {
                    resetAction(Settings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                            Settings.System.KEY_HOME_DOUBLE_TAP_CUSTOM_APP_FR_NAME);
                }
                if (packageName.equals(appSwitchLongPress)) {
                    resetAction(Settings.System.KEY_HOME_LONG_PRESS_ACTION,
                            Settings.System.KEY_HOME_LONG_PRESS_CUSTOM_APP_FR_NAME);
                }
                if (packageName.equals(appSwitchDoubleTap)) {
                    resetAction(Settings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION,
                            Settings.System.KEY_APP_SWITCH_DOUBLE_TAP_CUSTOM_APP_FR_NAME);
                }
            }
        }
    };

    private void resetAction(String action, String name) {
        Settings.System.putInt(mContext.getContentResolver(), action, 0);
        Settings.System.putString(mContext.getContentResolver(), name, "");
    }

    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }
}
