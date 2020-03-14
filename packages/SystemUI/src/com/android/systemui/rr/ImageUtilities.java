/*
 * Copyright (C) 2019 Descendant
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

package com.android.systemui.rr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.renderscript.Element;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.RenderScript;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.util.Log;

public class ImageUtilities {

/* screenShot routine */
    public static Bitmap screenshotSurface(Context context) {
        float BITMAP_SCALE = 0.35f;
        Bitmap bitmap;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        defaultDisplay.getRealMetrics(displayMetrics);
        int rotation = defaultDisplay.getRotation();
        bitmap = SurfaceControl.screenshot(new Rect(), Math.round(displayMetrics.widthPixels * BITMAP_SCALE),
                Math.round(displayMetrics.heightPixels * BITMAP_SCALE), false, rotation);
        if (bitmap == null) {
            Log.e("ScreenShotHelper", "screenShotBitmap error bitmap is null");
            return null;
        }
        bitmap.prepareToDraw();
        return bitmap.copy(Bitmap.Config.ARGB_8888, true);
    }

/* blur routine */
    public static Bitmap blurImage(Context context, Bitmap inputBitmap, int intensity) {
        float BLUR_RADIUS = 7.5f;

        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);  
        if (intensity > 0 && intensity <= 100) {
            BLUR_RADIUS = (float) intensity * 0.25f;
        }
        theIntrinsic.setRadius(BLUR_RADIUS);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        return outputBitmap;
   }
}
