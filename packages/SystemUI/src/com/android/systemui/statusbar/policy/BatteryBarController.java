package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

public class BatteryBarController extends LinearLayout {

    private static final String TAG = "BatteryBarController";

    BatteryBar mainBar;
    BatteryBar alternateStyleBar;
    GlobalSettingsObserver mSettingsObserver;

    public static final int STYLE_REGULAR = 0;
    public static final int STYLE_SYMMETRIC = 1;

    static int mStyle = STYLE_REGULAR;
    static int mLocation = 0;

    protected final static int CURRENT_LOC = 1;
    static int mLocationToLookFor = 0;

    private int mBatteryLevel = 0;
    private boolean mBatteryCharging = false;

    boolean isAttached = false;
    boolean isVertical = false;

    static class GlobalSettingsObserver extends ContentObserver {
        private static GlobalSettingsObserver sInstance;
        private ArrayList<BatteryBarController> mBatteryBarControllers = new ArrayList<BatteryBarController>();
        private Context mContext;

        public GlobalSettingsObserver(Handler handler, Context context) {
            super(handler);
            mContext = context.getApplicationContext();
        }

        static GlobalSettingsObserver getInstance(Context context) {
            if (sInstance == null) {
                sInstance = new GlobalSettingsObserver(new Handler(), context);
            }
            return sInstance;
        }

        void attach(BatteryBarController bbc) {
            if (mBatteryBarControllers.isEmpty()) {
                observe();
            }
            mBatteryBarControllers.add(bbc);
        }

        void detach(BatteryBarController bbc) {
            mBatteryBarControllers.remove(bbc);
            if (mBatteryBarControllers.isEmpty()) {
                unobserve();
            }
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUSBAR_BATTERY_BAR), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUSBAR_BATTERY_BAR_STYLE), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS),
                    false, this);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            this.updateSettings();
        }

        void updateSettings() {
            mStyle = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUSBAR_BATTERY_BAR_STYLE, 0);
            mLocation = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUSBAR_BATTERY_BAR, 0);

            for (BatteryBarController bbc : mBatteryBarControllers) {
                if (mLocationToLookFor == mLocation) {
                    bbc.removeBars();
                    bbc.addBars();
                    bbc.setVisibility(View.VISIBLE);
                } else {
                    bbc.removeBars();
                    bbc.setVisibility(View.GONE);
                }
            }
        }
    }

    public BatteryBarController(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            String ns = "http://schemas.android.com/apk/res/com.android.systemui";
            mLocationToLookFor = attrs.getAttributeIntValue(ns, "viewLocation", 0);
        }
        mSettingsObserver = GlobalSettingsObserver.getInstance(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isAttached) {
            isVertical = (getLayoutParams().height == LayoutParams.MATCH_PARENT);

            isAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            getContext().registerReceiver(mIntentReceiver, filter);

            mSettingsObserver.attach(this);
            updateSettings();
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
                Prefs.setLastBatteryLevel(context, mBatteryLevel);
            }
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        if (isAttached) {
            isAttached = false;
            removeBars();
            mSettingsObserver.detach(this);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isAttached) {
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateSettings();
                }
            }, 500);

        }
    }

    public void addBars() {
        // set heights
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float dp = (float) Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS, 1);
        int pixels = (int) ((metrics.density * dp) + 0.5);

        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();

        if (isVertical)
            params.width = pixels;
        else
            params.height = pixels;
        setLayoutParams(params);

        if (isVertical)
            params.width = pixels;
        else
            params.height = pixels;
        setLayoutParams(params);
        mBatteryLevel = Prefs.getLastBatteryLevel(getContext());
        if (mStyle == STYLE_REGULAR) {
            addView(new BatteryBar(mContext, mBatteryCharging, mBatteryLevel, isVertical),
                    new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT, 1));
        } else if (mStyle == STYLE_SYMMETRIC) {
            BatteryBar bar1 = new BatteryBar(mContext, mBatteryCharging, mBatteryLevel, isVertical);
            BatteryBar bar2 = new BatteryBar(mContext, mBatteryCharging, mBatteryLevel, isVertical);

            if (isVertical) {
                bar2.setRotation(180);
                addView(bar2, (new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT, 1)));
                addView(bar1, (new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT, 1)));
            } else {
                bar1.setRotation(180);
                addView(bar1, (new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT, 1)));
                addView(bar2, (new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT, 1)));
            }

        }
    }

    public void removeBars() {
        removeAllViews();
    }

    public void updateSettings() {
        mStyle = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.STATUSBAR_BATTERY_BAR_STYLE, 0);
        mLocation = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.STATUSBAR_BATTERY_BAR, 0);

        if (isLocationValid(mLocation)) {
            removeBars();
            addBars();
            setVisibility(View.VISIBLE);
        } else {
            removeBars();
            setVisibility(View.GONE);
        }
    }

    protected boolean isLocationValid(int location) {
        return mLocationToLookFor == location;
    }
}
