/*
 * Copyright (C) 2014, ParanoidAndroid Project
 * Copyrighy (c) 2015, METALLIUM OS Project
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

package android.util;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GifView;
import android.widget.ImageView;
import android.provider.Settings;

import java.io.InputStream;

import com.android.internal.R;

/**
 * Hide from public API
 * @hide
 */
public class SettingConfirmationHelper {

    private static final int NOT_SET = 0;
    private static final int ENABLED = 1;
    private static final int DISABLED = 2;
    private static final int ASK_LATER = 3;

    /**
     * @hide
     */
    public static interface OnSelectListener {
        void onSelect(boolean enabled);
    }

    /**
     * @hide
     */
    public static void showConfirmationDialogForSetting(final Context context, String title, String msg, Drawable hint,
                                                        final String setting, final OnSelectListener listener) {
        int mCurrentStatus = Settings.System.getInt(context.getContentResolver(), setting, NOT_SET);
        if (mCurrentStatus == ENABLED || mCurrentStatus == DISABLED) return;

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View dialogLayout = layoutInflater.inflate(R.layout.setting_confirmation_dialog, null);
        final ImageView visualHint = (ImageView)
                dialogLayout.findViewById(R.id.setting_confirmation_dialog_visual_hint);
        visualHint.setImageDrawable(hint);
        visualHint.setVisibility(View.VISIBLE);

        AlertDialog dialog = createDialog(context,title,msg,dialogLayout,setting,listener);
        initWindow(dialog).show();
    }

    /**
     * @hide
     */
    public static void showConfirmationDialogForSetting(final Context context, String title, String msg, InputStream gif,
                                                        final String setting, final OnSelectListener listener) {
        int mCurrentStatus = Settings.System.getInt(
                context.getContentResolver(), setting, NOT_SET);
        if (mCurrentStatus == ENABLED || mCurrentStatus == DISABLED) return;

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View dialogLayout = layoutInflater.inflate(R.layout.setting_confirmation_dialog, null);
        final GifView gifView = (GifView)
                dialogLayout.findViewById(R.id.setting_confirmation_dialog_visual_gif);
        gifView.setGifResource(gif);
        gifView.setVisibility(View.VISIBLE);

        AlertDialog dialog = createDialog(context,title,msg,dialogLayout,setting,listener);
        initWindow(dialog).show();
    }

    private static AlertDialog initWindow(AlertDialog dialog) {
        Window dialogWindow = dialog.getWindow();
        dialogWindow.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        return dialog;
    }

    private static AlertDialog createDialog(final Context context, String title, String msg, View view,
                                            final String setting, final OnSelectListener listener) {
        final int currentUserId = ActivityManager.getCurrentUser();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setView(view, 10, 10, 10, 20);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.setting_confirmation_yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.System.putIntForUser(context.getContentResolver(), setting,
                                ENABLED, currentUserId);
                        if (listener == null) return;
                        listener.onSelect(true);
                    }
                }
        );
        builder.setNeutralButton(R.string.setting_confirmation_ask_me_later,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.System.putIntForUser(context.getContentResolver(), setting,
                                ASK_LATER, currentUserId);
                        if (listener == null) return;
                        listener.onSelect(false);
                    }
                }
        );
        builder.setNegativeButton(R.string.setting_confirmation_no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.System.putIntForUser(context.getContentResolver(), setting,
                                DISABLED, currentUserId);
                        if (listener == null) return;
                        listener.onSelect(false);
                    }
                }
        );
        builder.setCancelable(false);

        return builder.create();
    }

}
