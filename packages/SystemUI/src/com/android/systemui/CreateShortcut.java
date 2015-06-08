/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.app.AlertDialog;
import android.app.LauncherActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ListView;

import com.android.systemui.R;

public class CreateShortcut extends LauncherActivity {

    private static final int DLG_TOGGLE = 0;

    private int mSettingType = 0;

    private Intent mShortcutIntent;
    private Intent mIntent;

    private CharSequence mName = null;

    @Override
    protected Intent getTargetIntent() {
        Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
        targetIntent.addCategory("com.android.systemui.SHORTCUT");
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return targetIntent;
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        mShortcutIntent = intentForPosition(position);

        String intentClass = mShortcutIntent.getComponent().getClassName();
        String className = intentClass.substring(intentClass.lastIndexOf(".") + 1);
        String intentAction = mShortcutIntent.getAction();

        mShortcutIntent = new Intent();
        mShortcutIntent.setClassName(this, intentClass);
        mShortcutIntent.setAction(intentAction);

        mName = itemForPosition(position).label;

        mIntent = new Intent();
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                BitmapFactory.decodeResource(getResources(), returnIconResId(className)));
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mName);
        if (className.equals("Rotation")) {
            showDialogSetting(DLG_TOGGLE);
        } else {
            finalizeIntent();
        }
    }

    private int returnIconResId(String intentClass) {
        String c = intentClass.substring(intentClass.lastIndexOf(".") + 1);

        if (c.equals("Rotation")) {
            return R.drawable.ic_qs_auto_rotate;
        } else if (c.equals("Reboot")) {
            return R.drawable.ic_qs_reboot;
        } else if (c.equals("Recovery")) {
            return R.drawable.ic_qs_reboot_recovery;
        } else {
            // Oh-Noes, you found a wild derp.
            return R.drawable.ic_sysbar_null;
        }
    }

    private void finalizeIntent() {
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, mShortcutIntent);
        setResult(RESULT_OK, mIntent);
        finish();
    }

    private void showDialogSetting(int id) {
        switch (id) {
            case DLG_TOGGLE:
                final CharSequence[] items = {
                    getResources().getString(R.string.off),
                    getResources().getString(R.string.on),
                    getResources().getString(R.string.toggle),
                };
                AlertDialog.Builder alertToggle = new AlertDialog.Builder(this);
                alertToggle.setTitle(R.string.shortcut_toggle_title)
                .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, final int item) {
                        mShortcutIntent.putExtra("value", item);
                        mIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                                mName + " " + items[item]);
                        finalizeIntent();
                    }
                });
                alertToggle.show();
                break;
        }
    }
}
