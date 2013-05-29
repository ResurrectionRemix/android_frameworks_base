/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.internal.util.aokp;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

public class NavRingHelpers {

    // These items will be subtracted from NavRing Actions when RC requests list of
    // Available Actions
    private static final AwesomeConstant[] EXCLUDED_FROM_NAVRING = {
            AwesomeConstant.ACTION_UNLOCK,
            AwesomeConstant.ACTION_CAMERA,
            AwesomeConstant.ACTION_CLOCKOPTIONS,
            AwesomeConstant.ACTION_EVENT,
            AwesomeConstant.ACTION_TODAY,
            AwesomeConstant.ACTION_ALARM
    };

    private NavRingHelpers() {
    }

    public static String[] getNavRingActions() {
        boolean itemFound;
        String[] mActions;
        ArrayList<String> mActionList = new ArrayList<String>();
        String[] mActionStart = AwesomeConstants.AwesomeActions();
        int startLength = mActionStart.length;
        int excludeLength = EXCLUDED_FROM_NAVRING.length;
        for (int i = 0; i < startLength; i++) {
            itemFound = false;
            for (int j = 0; j < excludeLength; j++) {
                if (mActionStart[i].equals(EXCLUDED_FROM_NAVRING[j].value())) {
                    itemFound = true;
                }
            }
            if (!itemFound) {
                mActionList.add(mActionStart[i]);
            }
        }
        int actionSize = mActionList.size();
        mActions = new String[actionSize];
        for (int i = 0; i < actionSize; i++) {
            mActions[i] = mActionList.get(i);
        }
        return mActions;
    }

    public static TargetDrawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;
        final Resources res = context.getResources();
        Drawable activityIcon;
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);

        if (TextUtils.isEmpty(action)) {
            TargetDrawable drawable = new TargetDrawable(res,
                    com.android.internal.R.drawable.ic_action_empty);
            drawable.setEnabled(false);
            return drawable;
        }

        AwesomeConstant IconEnum = fromString(action);
        if (IconEnum.equals(AwesomeConstant.ACTION_NULL)) {
            TargetDrawable drawable = new TargetDrawable(res,
                    com.android.internal.R.drawable.ic_action_empty);
            drawable.setEnabled(false);
            return drawable;
        } else if (IconEnum.equals(AwesomeConstant.ACTION_ASSIST)) {
            TargetDrawable drawable = new TargetDrawable(res,
                    com.android.internal.R.drawable.ic_action_assist_generic);
            return drawable;
        } else if (IconEnum.equals(AwesomeConstant.ACTION_APP)) {
            // no pre-defined action, try to resolve URI
            try {
                Intent intent = Intent.parseUri(action, 0);
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                if (info == null) {
                    TargetDrawable drawable = new TargetDrawable(res,
                            com.android.internal.R.drawable.ic_action_empty);
                    drawable.setEnabled(false);
                    return drawable;
                }

                activityIcon = info.loadIcon(pm);

                int desiredSize = (int) (48 * metrics.density);
                int width = activityIcon.getIntrinsicWidth();

                if (width > desiredSize)
                {
                    Bitmap bm = ((BitmapDrawable) activityIcon).getBitmap();
                    if (bm != null) {
                        Bitmap bitmapOrig = Bitmap.createScaledBitmap(bm, desiredSize, desiredSize,
                                false);
                        activityIcon = new BitmapDrawable(res, bitmapOrig);
                    }
                }

            } catch (URISyntaxException e) {
                TargetDrawable drawable = new TargetDrawable(res,
                        com.android.internal.R.drawable.ic_action_empty);
                drawable.setEnabled(false);
                return drawable;
            }
        } else {
            activityIcon = AwesomeConstants.getActionIcon(context, action);
        }

        Drawable iconBg = res.getDrawable(com.android.internal.R.drawable.ic_navbar_blank);
        Drawable iconBgActivated = res
                .getDrawable(com.android.internal.R.drawable.ic_navbar_blank_activated);
        int margin = (int) (iconBg.getIntrinsicHeight() / 3);
        LayerDrawable icon = new LayerDrawable(new Drawable[] {
                iconBg, activityIcon
        });
        LayerDrawable iconActivated = new LayerDrawable(new Drawable[] {
                iconBgActivated, activityIcon
        });

        icon.setLayerInset(1, margin, margin, margin, margin);
        iconActivated.setLayerInset(1, margin, margin, margin, margin);

        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                -android.R.attr.state_active,
                -android.R.attr.state_focused
        }, icon);
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                android.R.attr.state_active,
                -android.R.attr.state_focused
        }, iconActivated);
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                -android.R.attr.state_active,
                android.R.attr.state_focused
        }, iconActivated);
        return new TargetDrawable(res, selector);
    }

    public static TargetDrawable getCustomDrawable(Context context, String action) {
        final Resources res = context.getResources();

        File f = new File(Uri.parse(action).getPath());
        Drawable activityIcon = new BitmapDrawable(res,
                getRoundedCornerBitmap(BitmapFactory.decodeFile(f.getAbsolutePath())));

        Drawable iconBg = res.getDrawable(
                com.android.internal.R.drawable.ic_navbar_blank);
        Drawable iconBgActivated = res.getDrawable(
                com.android.internal.R.drawable.ic_navbar_blank_activated);

        int margin = (int) (iconBg.getIntrinsicHeight() / 3);
        LayerDrawable icon = new LayerDrawable(new Drawable[] {
                iconBg, activityIcon
        });
        LayerDrawable iconActivated = new LayerDrawable(new Drawable[] {
                iconBgActivated, activityIcon
        });

        icon.setLayerInset(1, margin, margin, margin, margin);
        iconActivated.setLayerInset(1, margin, margin, margin, margin);

        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                -android.R.attr.state_active,
                -android.R.attr.state_focused
        }, icon);
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                android.R.attr.state_active,
                -android.R.attr.state_focused
        }, iconActivated);
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                -android.R.attr.state_active,
                android.R.attr.state_focused
        }, iconActivated);
        return new TargetDrawable(res, selector);
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 24;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }
}
