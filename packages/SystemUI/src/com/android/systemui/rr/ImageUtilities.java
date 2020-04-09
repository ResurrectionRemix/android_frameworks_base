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
import android.graphics.drawable.GradientDrawable.Orientation;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
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
        WindowManager mWindowManager;
        Display mDisplay;
        DisplayMetrics mDisplayMetrics;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mDisplayMetrics = new DisplayMetrics();
        mDisplay.getRealMetrics(mDisplayMetrics);
        int displayHeight = mDisplayMetrics.heightPixels;
        int displayWidth = mDisplayMetrics.widthPixels;
        Rect displayRect = new Rect(0, 0, displayWidth, displayHeight);
        int rot = mDisplay.getRotation();
        Bitmap mScreenBitmap;
        try {
            mScreenBitmap = SurfaceControl.screenshot(displayRect, displayWidth, displayHeight, rot);
            // Crop the screenshot to selected region
            Bitmap swBitmap = mScreenBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Bitmap cropped = Bitmap.createBitmap(swBitmap, Math.max(0, displayRect.left), Math.max(0, displayRect.top),
                    displayRect.width(), displayRect.height());
            swBitmap.recycle();
            mScreenBitmap.recycle();
            mScreenBitmap = cropped;
            } catch (Exception e) {
                Log.d ("ImageUtilities", "Screenshot service: FB is protected, falling back to an empty Bitmap");
                mScreenBitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888);
        }
        return mScreenBitmap;
    }

/* blur routine */
    public static Bitmap blurImage(Context context, Bitmap image) {
        float BITMAP_SCALE = 0.4f;
        float BLUR_RADIUS = 7.5f;

        int width = Math.round(image.getWidth() * BITMAP_SCALE);       
        int height = Math.round(image.getHeight() * BITMAP_SCALE);

        Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);  
        theIntrinsic.setRadius(BLUR_RADIUS);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);
        return outputBitmap;
   }
}
