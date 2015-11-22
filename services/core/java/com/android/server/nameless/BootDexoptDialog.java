/*
 * Copyright (C) 2015 The NamelessRom Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.nameless;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * @hide
 */
public class BootDexoptDialog extends Dialog {
    /** For low ram devices */
    private static final String PROP_DEXOPT_NO_ICON = "persist.sys.dexopt.no_icon";

    final Context mContext;
    final PackageManager mPackageManager;

    ImageView mBootDexoptIcon;
    TextView mBootDexoptMsg;
    TextView mBootDexoptMsgDetail;
    ProgressBar mBootDexoptProgress;

    boolean mShouldShowIcon;

    public BootDexoptDialog(Context context, int themeResId, int total) {
        super(context, themeResId);
        mContext = context;
        mPackageManager = mContext.getPackageManager();

        final boolean useFanceEffects = (!ActivityManager.isLowRamDeviceStatic()
                                         || ActivityManager.isForcedHighEndGfx());
        mShouldShowIcon = !SystemProperties.getBoolean(PROP_DEXOPT_NO_ICON, !useFanceEffects);

        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View bootMsgLayout = inflater.inflate(
                com.android.internal.R.layout.boot_dexopt_layout, null, false);
        mBootDexoptMsg = (TextView) bootMsgLayout.findViewById(
                com.android.internal.R.id.dexopt_message);
        mBootDexoptMsgDetail = (TextView) bootMsgLayout.findViewById(
                com.android.internal.R.id.dexopt_message_detail);
        mBootDexoptIcon = (ImageView) bootMsgLayout.findViewById(
                com.android.internal.R.id.dexopt_icon);
        mBootDexoptProgress = (ProgressBar) bootMsgLayout.findViewById(
                com.android.internal.R.id.dexopt_progress);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(bootMsgLayout);
        getWindow().setType(WindowManager.LayoutParams.TYPE_BOOT_PROGRESS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        getWindow().setAttributes(lp);
        setCancelable(false);
        show();

        mBootDexoptIcon.setImageDrawable(null);
        mBootDexoptProgress.setMax(total);
    }

    public void setProgress(final ApplicationInfo info, final int current, final int total) {
        boolean isApk = false;
        String msg = "";

        if (info == null) {
            if (current == Integer.MIN_VALUE) {
                msg = mContext.getResources().getString(
                        com.android.internal.R.string.android_upgrading_starting_apps);
            } else if (current == (Integer.MIN_VALUE + 1)) {
                msg = mContext.getResources().getString(
                        com.android.internal.R.string.android_upgrading_fstrim);
            } else if (current == (Integer.MIN_VALUE + 3)) {
                msg = mContext.getResources().getString(
                        com.android.internal.R.string.android_upgrading_complete);
            }
        } else if (current == (Integer.MIN_VALUE + 2)) {
            final CharSequence label = info.loadLabel(mContext.getPackageManager());
            msg = mContext.getResources().getString(
                    com.android.internal.R.string.android_preparing_apk, label);
        } else {
            isApk = true;
            msg = mContext.getResources().getString(
                    com.android.internal.R.string.android_upgrading_apk, current, total);
            mBootDexoptProgress.setProgress(current);
            // just make it look pretty :P
            if ((current + 1) <= total) {
                mBootDexoptProgress.setSecondaryProgress(current + 1);
            }
        }

        // if we are processing an apk, load its icon and set the message details
        if (isApk) {
            if (mShouldShowIcon) {
                mBootDexoptIcon.setImageDrawable(info.loadIcon(mPackageManager));
            }
            mBootDexoptMsgDetail.setVisibility(View.VISIBLE);
            mBootDexoptMsgDetail.setText(String.format("(%s)", info.packageName));
        } else {
            mBootDexoptIcon.setImageDrawable(null);
            mBootDexoptMsgDetail.setVisibility(View.GONE);
        }
        mBootDexoptMsg.setText(msg);
    }

    // This dialog will consume all events coming in to
    // it, to avoid it trying to do things too early in boot.

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return true;
    }

    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override public boolean dispatchTrackballEvent(MotionEvent ev) {
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        return true;
    }

    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return true;
    }
}
