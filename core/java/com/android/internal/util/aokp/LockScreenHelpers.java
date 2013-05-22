/*
 * Copyright (C) 2013 Android Open Kang Project
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
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;
import java.net.URISyntaxException;

public class LockScreenHelpers {

    private LockScreenHelpers() {
    }

    public static TargetDrawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;
        final Resources res = context.getResources();

        if (TextUtils.isEmpty(action) || action.equals(AwesomeConstant.ACTION_NULL.value())) {
            TargetDrawable drawable = new TargetDrawable(res, stateDrawable(res.getDrawable(com.android.internal.R.drawable.ic_empty), context));
            drawable.setEnabled(false);
            return drawable;
        }

        AwesomeConstant IconEnum = fromString(action);
            switch (IconEnum) {
            case ACTION_UNLOCK:
                resourceId = com.android.internal.R.drawable.ic_lockscreen_unlock;
                break;
            case ACTION_ASSIST:
                resourceId = com.android.internal.R.drawable.ic_action_assist_generic;
                break;
            case ACTION_CAMERA:
                resourceId = com.android.internal.R.drawable.ic_lockscreen_camera;
                break;
            case ACTION_APP:
                // no pre-defined action, try to resolve URI
                try {
                    Intent intent = Intent.parseUri(action, 0);
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
                    if (info == null) {
                        TargetDrawable drawable = new TargetDrawable(res, stateDrawable(res.getDrawable(com.android.internal.R.drawable.ic_empty), context));
                        drawable.setEnabled(false);
                        return drawable;
                    }
                    Drawable front = info.loadIcon(pm);
                    return new TargetDrawable(res, stateDrawable(front, context));
                } catch (URISyntaxException e) {
                    resourceId = com.android.internal.R.drawable.ic_empty;
                }
                break;
            }
        TargetDrawable drawable = new TargetDrawable(res, resourceId);
        if (resourceId == com.android.internal.R.drawable.ic_empty) {
            drawable.setEnabled(false);
        }
        return drawable;
    }

    public static StateListDrawable stateDrawable(Drawable front, Context context) {
        final Resources res = context.getResources();
        Drawable iconBg = res.getDrawable(
            com.android.internal.R.drawable.ic_navbar_blank_activated);
        int inset = (int)(iconBg.getIntrinsicHeight() / 3);
        final Drawable blankActiveDrawable = res.getDrawable(
            com.android.internal.R.drawable.ic_lockscreen_target_activated);
        final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);
        Drawable back = activeBack;
        InsetDrawable[] inactivelayer = new InsetDrawable[2];
        InsetDrawable[] activelayer = new InsetDrawable[2];
        inactivelayer[0] = new InsetDrawable(
            res.getDrawable(com.android.internal.R.drawable.ic_lockscreen_lock_pressed), 0, 0,0, 0);
        inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
        activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
        activelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
        StateListDrawable states = new StateListDrawable();
        LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
        inactiveLayerDrawable.setId(0, 0);
        inactiveLayerDrawable.setId(1, 1);
        LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
        activeLayerDrawable.setId(0, 0);
        activeLayerDrawable.setId(1, 1);
        states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
        states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
        states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);
        return states;
    }

    public static TargetDrawable getCustomDrawable(Context context, String action) {
        final Resources res = context.getResources();

        File f = new File(Uri.parse(action).getPath());
        Drawable front = new BitmapDrawable(res,
                         getRoundedCornerBitmap(BitmapFactory.decodeFile(f.getAbsolutePath())));
        final Drawable blankActiveDrawable = res.getDrawable(
            com.android.internal.R.drawable.ic_lockscreen_target_activated);
        final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);
        Drawable back = activeBack;
        Drawable iconBg = res.getDrawable(
            com.android.internal.R.drawable.ic_navbar_blank_activated);
        int inset = (int)(iconBg.getIntrinsicHeight() / 3);
        InsetDrawable[] inactivelayer = new InsetDrawable[2];
        InsetDrawable[] activelayer = new InsetDrawable[2];
        inactivelayer[0] = new InsetDrawable(
            res.getDrawable(com.android.internal.R.drawable.ic_lockscreen_lock_pressed), 0, 0,0, 0);
        inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
        activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
        activelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
        StateListDrawable states = new StateListDrawable();
        LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
        inactiveLayerDrawable.setId(0, 0);
        inactiveLayerDrawable.setId(1, 1);
        LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
        activeLayerDrawable.setId(0, 0);
        activeLayerDrawable.setId(1, 1);
        states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
        states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
        states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);
        return new TargetDrawable(res, states);
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
