
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.ExtendedPropertiesUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class HybridToggle extends BaseToggle {

    private static final String PARANOID_PREFERENCES_PKG = "com.paranoid.preferences";

    private String mAppLabel;
    private String mPackageName;
    private String mSourceDir;
    private PackageManager mPm; 

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_hybrid);
        setLabel(R.string.quick_settings_hybrid);
        mAppLabel = c.getString(R.string.quick_settings_hybrid);
        mPm = c.getPackageManager(); 
    }

    @Override
    public void onClick(View v) {
        mPackageName = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.FOREGROUND_APP);
        try {
            PackageInfo foregroundAppPackageInfo = mPm.getPackageInfo(mPackageName, 0);
            mAppLabel = foregroundAppPackageInfo.applicationInfo.loadLabel(mPm).toString();
            ApplicationInfo appInfo = ExtendedPropertiesUtils.getAppInfoFromPackageName(mPackageName);
            mSourceDir = appInfo.sourceDir;
        } catch(NameNotFoundException Exception) {} 
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.putExtra("package", mPackageName);
            intent.putExtra("appname", mAppLabel);
            intent.putExtra("filename", mSourceDir);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK); 
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(new ComponentName(PARANOID_PREFERENCES_PKG,
                        PARANOID_PREFERENCES_PKG + ".hybrid.ViewPagerActivity"));
            startActivity(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        try {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setComponent(new ComponentName(PARANOID_PREFERENCES_PKG, 
                    PARANOID_PREFERENCES_PKG + ".MainActivity"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch(NullPointerException e) {
            // No intent found for activity component
        }
        collapseStatusBar();
        return super.onLongClick(v);
    }

} 


