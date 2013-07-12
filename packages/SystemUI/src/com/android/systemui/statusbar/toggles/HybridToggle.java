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
import android.graphics.PorterDuff;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.ExtendedPropertiesUtils;
import android.util.ColorUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

import java.math.BigInteger;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class HybridToggle extends BaseToggle {

    private static final String PARANOID_PREFERENCES_PKG = "com.paranoid.preferences";
    private static final String STOCK_COLORS = "NULL|NULL|NULL|NULL|NULL";

    private String mDefaultLabel;
    private String mLabel;
    private String mPackageName;
    private String mSourceDir;
    private PackageManager mPm;
    private String mStatus;
    private String mColor = STOCK_COLORS;
    private SettingsObserver mObserver = null;
    private QuickSettingsTileView mQuick = null;

    @Override
    public void init(Context context, int style) {
        super.init(context, style);
        mDefaultLabel = context.getString(R.string.quick_settings_hybrid);
        mLabel = mDefaultLabel;
        mPm = context.getPackageManager();
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
    public void onClick(View v) {
        if(!mLabel.equals(mDefaultLabel)) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.putExtra("package", mPackageName);
            intent.putExtra("appname", mLabel);
            intent.putExtra("filename", mSourceDir);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);  
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(new ComponentName(PARANOID_PREFERENCES_PKG,
                    PARANOID_PREFERENCES_PKG + ".hybrid.ViewPagerActivity"));
            startActivity(intent);
        }
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

    @Override
    public QuickSettingsTileView createTileView() {
	QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.quick_settings_tile_hybrid, null);
        mQuick = quick;
        mQuick.setOnClickListener(this);
        mQuick.setOnLongClickListener(this);
        return mQuick;
    }

    @Override
    public View createTraditionalView() {
        View v = super.createTraditionalView();
        return v;
    }

    @Override
    protected void updateView() {
        mPackageName = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.FOREGROUND_APP);
        try {
            PackageInfo foregroundAppPackageInfo = mPm.
                    getPackageInfo(mPackageName, 0);
            mLabel = foregroundAppPackageInfo.applicationInfo.
                    loadLabel(mPm).toString();

            ExtendedPropertiesUtils.refreshProperties();
            ApplicationInfo appInfo = ExtendedPropertiesUtils.
                    getAppInfoFromPackageName(mPackageName);
            mSourceDir = appInfo.sourceDir;

            mStatus = String.valueOf(ExtendedPropertiesUtils.getActualProperty(mPackageName +
                    ExtendedPropertiesUtils.PARANOID_DPI_SUFFIX)) + " DPI / " +
                    String.valueOf(ExtendedPropertiesUtils.getActualProperty(mPackageName +
                    ExtendedPropertiesUtils.PARANOID_LAYOUT_SUFFIX)) + "P";

            mColor = ExtendedPropertiesUtils.getProperty(mPackageName +
                    ExtendedPropertiesUtils.PARANOID_COLORS_SUFFIX, STOCK_COLORS);

            TextView status = (TextView) mQuick.findViewById(R.id.hybrid_status);
            status.setText(mStatus);
            status.setTextSize(1, super.getTextSize());
            status.setTextColor(super.getTextColor());
            TextView app = (TextView) mQuick.findViewById(R.id.hybrid_app);
            app.setText(mLabel);
            app.setTextSize(1, super.getTextSize());
            app.setTextColor(super.getTextColor());

            // Color changes
            View[] swatches = new View[5];
            swatches[0] = mQuick.findViewById(R.id.hybrid_swatch1);
            swatches[1] = mQuick.findViewById(R.id.hybrid_swatch2);
            swatches[2] = mQuick.findViewById(R.id.hybrid_swatch3);
            swatches[3] = mQuick.findViewById(R.id.hybrid_swatch4);
            swatches[4] = mQuick.findViewById(R.id.hybrid_swatch5);

            String[] colors = mColor.split(ExtendedPropertiesUtils.PARANOID_STRING_DELIMITER);
            if (colors.length == ExtendedPropertiesUtils.PARANOID_COLORS_COUNT) {
                for(int colorIndex = 0; colorIndex < ExtendedPropertiesUtils.PARANOID_COLORS_COUNT; colorIndex++) {
                    swatches[colorIndex].setBackgroundDrawable(mContext.getResources().getDrawable(
                            R.drawable.color_picker).mutate());
                    swatches[colorIndex].getBackground().setColorFilter(colors[colorIndex]
                            .toUpperCase().equals("NULL") ? ExtendedPropertiesUtils.PARANOID_COLORCODES_DEFAULTS[
                    colorIndex] : ColorUtils.hexToInt(colors[colorIndex]),
                            PorterDuff.Mode.SRC_ATOP);
                }
            }
        } catch(Exception e) {
            mLabel = mDefaultLabel; // No app found with package name
        }
        super.updateView();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.FOREGROUND_APP),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateView();
        }
    }
}
