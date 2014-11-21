/*
 * Copyright (C) 2014 Slimroms
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
package com.android.keyguard;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.util.slim.AppHelper;
import com.android.internal.util.slim.ActionHelper;
import com.android.internal.util.slim.ActionConfig;
import com.android.internal.util.slim.Action;
import com.android.internal.widget.LockPatternUtils;

import com.android.keyguard.R;

import java.util.ArrayList;

public class KeyguardShortcuts extends LinearLayout {

    private Handler mHandler = new Handler();
    private LockPatternUtils mLockPatternUtils;
    private SettingsObserver mSettingsObserver;
    private PackageManager mPackageManager;
    private Context mContext;

    public KeyguardShortcuts(Context context) {
        this(context, null);
    }

    public KeyguardShortcuts(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mSettingsObserver = new SettingsObserver(mHandler);
        createShortcuts();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @Override
    public void onAttachedToWindow() {
        mSettingsObserver.observe();

    }

    @Override
    public void onDetachedFromWindow() {
        mSettingsObserver.unobserve();
    }

    private void createShortcuts() {
        ArrayList<ActionConfig> actionConfigs = ActionHelper.getLockscreenShortcutConfig(mContext);
        if (actionConfigs.size() == 0) {
            setVisibility(View.GONE);
            return;
        }
        setVisibility(View.VISIBLE);

        boolean longpress = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_SHORTCUTS_LONGPRESS, 1, UserHandle.USER_CURRENT) == 1;

        ActionConfig actionConfig;

        for (int j = 0; j < actionConfigs.size(); j++) {
            actionConfig = actionConfigs.get(j);

            final String action = actionConfig.getClickAction();
            ImageView i = new ImageView(mContext);
            int dimens = Math.round(mContext.getResources().getDimensionPixelSize(
                    com.android.internal.R.dimen.app_icon_size));
            LinearLayout.LayoutParams vp =
                    new LinearLayout.LayoutParams(dimens, dimens);
            i.setLayoutParams(vp);

            Drawable d = ActionHelper.getActionIconImage(
                    mContext, actionConfig.getClickAction(), actionConfig.getIcon());
            i.setImageDrawable(d);
            i.setBackground(mContext.getDrawable(R.drawable.ripple_drawable));
            i.setContentDescription(AppHelper.getFriendlyNameForUri(
                    mContext, mPackageManager, actionConfig.getClickAction()));
            i.setClickable(true);

            if (longpress) {
                i.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        doHapticKeyClick(HapticFeedbackConstants.LONG_PRESS);
                        Action.processAction(mContext, action, true);
                        return true;
                    }
                });
            } else {
                i.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doHapticKeyClick(HapticFeedbackConstants.VIRTUAL_KEY);
                        Action.processAction(mContext, action, false);
                    }
                });
            }
            addView(i);
            if (j+1 < actionConfigs.size()) {
                addSeparator();
            }
        }
    }

    public void doHapticKeyClick(int type) {
        if (mLockPatternUtils.isTactileFeedbackEnabled()) {
            performHapticFeedback(type,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    private void addSeparator() {
        View v = new View(mContext);
        LinearLayout.LayoutParams vp =
                new LinearLayout.LayoutParams(mContext.getResources()
                .getDimensionPixelSize(R.dimen.shortcut_seperater_width), 0);
        v.setLayoutParams(vp);
        addView(v);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_SHORTCUTS),
                    false, this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_SHORTCUTS_LONGPRESS),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        public void update() {
            removeAllViews();
            createShortcuts();
        }
    }
}
