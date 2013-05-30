
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.R;

import java.io.File;
import java.io.IOException;

public class CustomToggle extends BaseToggle {

    public String[] mClickActions = new String[5];
    public String[] mLongActions = new String[5];
    public String[] mToggleIcons = new String[5];

    private int mDoubleClick;
    private int mNumberOfActions;
    private int mCollapseShade;
    private int mCustomState;
    private int mMatchState = 0;
    private int doubleClickCounter = 0;
    private boolean mActionRevert;
    private boolean mMatchAction;

    public static final int NO_ACTION = 0;
    public static final int REVERSE_ONE = 1;
    public static final int STATE_ONE = 2;
    public static final int SKIP_BACK = 3;
    public static final int SKIP_FORWARD = 4;

    public static final int NO_COLLAPSE = 10;
    public static final int ON_CLICK = 11;
    public static final int ON_LONG = 12;
    public static final int ON_BOTH = 13;

    private static final String KEY_TOGGLE_STATE = "toggle_state";

    public final static String[] StockClickActions = {
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value() };

    private SettingsObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
        startMagicTricks();
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (mActionRevert) {
                    mHandler.postDelayed(delayBootAction, 25000);
                }
            }
        }, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    final Runnable delayBootAction = new Runnable () {
        public void run() {
            mCustomState = 0;
            commitState();
            launchLongOrShort();
            startMagicTricks();
        }
    };

    final Runnable DelayShortPress = new Runnable () {
        public void run() {
            doubleClickCounter = 0;
            startCounting();
        }
    };

    final Runnable ResetDoubleClickCounter = new Runnable () {
        public void run() {
            doubleClickCounter = 0;
        }
    };

    private void checkForDoubleClick() {
        if (doubleClickCounter > 0) {
            mHandler.removeCallbacks(DelayShortPress);
            switch (mDoubleClick) {
                case REVERSE_ONE:
                    startReverse();
                    break;
                case STATE_ONE:
                    mCustomState = 0;
                    commitState();
                    launchLongOrShort();
                    shouldCollapse();
                    startMagicTricks();
                    break;
                case SKIP_BACK:
                    if (mCustomState > 0) {
                        mCustomState--;
                        commitState();
                    } else {
                        mCustomState = mNumberOfActions-1;
                        commitState();
                    }
                    startMagicTricks();
                    break;
                case SKIP_FORWARD:
                    if (mCustomState < mNumberOfActions-1) {
                        mCustomState += 1;
                        commitState();
                    } else {
                        mCustomState = 0;
                        commitState();
                    }
                    startMagicTricks();
                    break;
            }
            mHandler.postDelayed(ResetDoubleClickCounter, 20);
        } else {
            doubleClickCounter = doubleClickCounter + 1;
            mHandler.postDelayed(DelayShortPress, 300);
        }
    }

    private void startCounting() {
        if (mCustomState < mNumberOfActions-1) {
            mCustomState += 1;
            commitState();
            mMatchState = mCustomState-1;
        } else {
            mCustomState = 0;
            commitState();
            mMatchState = mNumberOfActions-1;
        }
        startActions();
    }

    private void startReverse() {
        if (mCustomState > 0) {
            mCustomState--;
            commitState();
        } else {
            mCustomState = mNumberOfActions-1;
            commitState();
        }
        launchLongOrShort();
        shouldCollapse();
        startMagicTricks();
    }

    private void startActions() {
        if (mMatchAction) {
            AwesomeAction.launchAction(mContext, mClickActions[mMatchState]);
        } else {
            AwesomeAction.launchAction(mContext, mClickActions[mCustomState]);
        }
        shouldCollapse();
        startMagicTricks();
    }

    final void launchLongOrShort() {
        AwesomeAction.launchAction(mContext, "**null**".equals(mClickActions[mCustomState])
                ? mLongActions[mCustomState] : mClickActions[mCustomState]);
    }

    private void shouldCollapse() {
        switch (mCollapseShade) {
            case NO_COLLAPSE:
            case ON_LONG:
                break;
            case ON_CLICK:
            case ON_BOTH:
                collapseStatusBar();
                break;
        }
    }

    private void commitState() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.CUSTOM_TOGGLE_STATE, mCustomState);
        updateSettings();
    }

    private void startMagicTricks() {
        String iconUri = "";
        Drawable myIcon = null;
        String toggleText = NavBarHelpers.getProperSummary(mContext,
                "**null**".equals(mClickActions[mCustomState])
                ? mLongActions[mCustomState] : mClickActions[mCustomState]);
        iconUri = mToggleIcons[mCustomState];
        if (iconUri != null && iconUri.length() > 0) {
            File f = new File(Uri.parse(iconUri).getPath());
            if (f.exists()) {
                myIcon = new BitmapDrawable(mContext.getResources(), f.getAbsolutePath());
            }
        } else {
            myIcon = NavBarHelpers.getIconImage(mContext, "**null**".equals(mClickActions[mCustomState])
                    ? mLongActions[mCustomState] : mClickActions[mCustomState]);
        }
        setLabel(toggleText);
        setIcon(myIcon);
        scheduleViewUpdate();
    };

    @Override
    public void onClick(View v) {
        switch (mDoubleClick) {
            case NO_ACTION:
                startCounting();
                break;
            case REVERSE_ONE:
            case STATE_ONE:
            case SKIP_BACK:
            case SKIP_FORWARD:
                checkForDoubleClick();
                break;
        }
    }
    @Override
    public boolean onLongClick(View v) {
        AwesomeAction.launchAction(mContext, mLongActions[mCustomState]);
        switch (mCollapseShade) {
            case NO_COLLAPSE:
            case ON_CLICK:
                break;
            case ON_LONG:
            case ON_BOTH:
                collapseStatusBar();
                break;
        }
        return true;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mActionRevert = Settings.System.getBoolean(resolver,
                Settings.System.CUSTOM_TOGGLE_REVERT, false);

        mCustomState = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_STATE, 0);

        mMatchAction = Settings.System.getBoolean(resolver,
                Settings.System.MATCH_ACTION_ICON, false);

        mCollapseShade = Settings.System.getInt(resolver,
                Settings.System.COLLAPSE_SHADE, 10);

        mNumberOfActions = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_TOGGLE_QTY, 3);

        mDoubleClick = Settings.System.getInt(resolver,
                Settings.System.DCLICK_TOGGLE_REVERT, 0);

        for (int j = 0; j < 5; j++) {
            mClickActions[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_PRESS_TOGGLE[j]);
            if (mClickActions[j] == null) {
                mClickActions[j] = StockClickActions[j];
                Settings.System.putString(resolver,
                        Settings.System.CUSTOM_PRESS_TOGGLE[j], mClickActions[j]);
            }

            mLongActions[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_LONGPRESS_TOGGLE[j]);
            if (mLongActions[j] == null) {
                mLongActions[j] = StockClickActions[j];
                Settings.System.putString(resolver,
                        Settings.System.CUSTOM_LONGPRESS_TOGGLE[j], mLongActions[j]);
            }

            mToggleIcons[j] = Settings.System.getString(resolver,
                    Settings.System.CUSTOM_TOGGLE_ICONS[j]);
        }
        startMagicTricks();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_REVERT),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_STATE),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.MATCH_ACTION_ICON),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.COLLAPSE_SHADE),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.CUSTOM_TOGGLE_QTY),
                    false, this);

            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.DCLICK_TOGGLE_REVERT),
                    false, this);

            for (int j = 0; j < 5; j++) {
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.CUSTOM_PRESS_TOGGLE[j]),
                        false,
                        this);
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.CUSTOM_TOGGLE_ICONS[j]),
                        false,
                        this);
                resolver.registerContentObserver(
                        Settings.System
                                .getUriFor(Settings.System.CUSTOM_LONGPRESS_TOGGLE[j]),
                        false,
                        this);
            }
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
}
