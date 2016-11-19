package com.android.systemui.statusbar;
/*
 * Copyright (C) serajr
 * Copyright (C) Xperia Open Source Project
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
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;

public class DisplayUtils {

    public static int getPxFromDp(Resources res, int size) {
        return (int) (size * res.getDisplayMetrics().density + 0.5f);
    }

    public static int getDominantColorByPixelsSampling(Bitmap bitmap, int rows, int cols) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int xPortion = width / cols;
        int yPortion = height / rows;
        int maxBin = -1;
        float[] hsv = new float[3];
        int[] colorBins = new int[36];
        float[] sumHue = new float[36];
        float[] sumSat = new float[36];
        float[] sumVal = new float[36];

        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col <= cols; col++) {
                int pixel = bitmap.getPixel(
                        col > 0 ? (xPortion * col) - 1 : 0,
                        row > 0 ? (yPortion * row) - 1 : 0);

                Color.colorToHSV(pixel, hsv);

                int bin = (int) Math.floor(hsv[0] / 10.0f);

                sumHue[bin] = sumHue[bin] + hsv[0];
                sumSat[bin] = sumSat[bin] + hsv[1];
                sumVal[bin] = sumVal[bin] + hsv[2];

                colorBins[bin]++;

                if (maxBin < 0 || colorBins[bin] > colorBins[maxBin])
                    maxBin = bin;
            }
        }

        if (maxBin < 0)
            return Color.argb(255, 255, 255, 255);

        hsv[0] = sumHue[maxBin] / colorBins[maxBin];
        hsv[1] = sumSat[maxBin] / colorBins[maxBin];
        hsv[2] = sumVal[maxBin] / colorBins[maxBin];

        return Color.HSVToColor(hsv);

    }

    public static double getColorLightness(int color) {
        return 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
    }

    public static int[] getRealScreenDimensions(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        return new int[] { metrics.widthPixels, metrics.heightPixels };
    }

    public static Bitmap takeSurfaceScreenshot(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        Matrix displayMatrix = new Matrix();

        Bitmap screenBitmap = null;

        display.getRealMetrics(metrics);
        float[] dims = { metrics.widthPixels, metrics.heightPixels };
        float degrees = getDegreesForRotation(display.getRotation());
        boolean requiresRotation = (degrees > 0);

        if (requiresRotation) {

            displayMatrix.reset();
            displayMatrix.preRotate(-degrees);
            displayMatrix.mapPoints(dims);
            dims[0] = Math.abs(dims[0]);
            dims[1] = Math.abs(dims[1]);

        }

        screenBitmap = SurfaceControl.screenshot((int) dims[0], (int) dims[1]);

        if (screenBitmap == null) {
            Log.i("xosp_blur_settings","Cannot take surface screenshot! Skipping blur feature!!");
            return null;
        }

        if (requiresRotation) {
            Bitmap bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.translate(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            canvas.rotate(360f - degrees);
            canvas.translate(-dims[0] / 2, -dims[1] / 2);
            canvas.drawBitmap(screenBitmap, 0, 0, null);
            canvas.setBitmap(null);
            screenBitmap = bitmap;
        }

        Bitmap mutable = screenBitmap.copy(Bitmap.Config.ARGB_8888, true);

        mutable.setHasAlpha(false);
        mutable.prepareToDraw();

        return mutable;
    }

    public static Bitmap takeSurfaceScreenshot(Context context, int downScale) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        Matrix displayMatrix = new Matrix();

        Bitmap screenBitmap = null;

        display.getRealMetrics(metrics);
        float[] dims = { metrics.widthPixels / downScale, metrics.heightPixels / downScale };
        float degrees = getDegreesForRotation(display.getRotation());
        boolean requiresRotation = (degrees > 0);

        if (requiresRotation) {
            displayMatrix.reset();
            displayMatrix.preRotate(-degrees);
            displayMatrix.mapPoints(dims);
            dims[0] = Math.abs(dims[0]);
            dims[1] = Math.abs(dims[1]);
        }

        screenBitmap = SurfaceControl.screenshot((int) dims[0], (int) dims[1]);

        if (screenBitmap == null) {
            Log.i("xosp_blur_settings","Cannot take surface screenshot! Skipping blur feature!!");
            return null;
        }

        if (requiresRotation) {
            Bitmap bitmap = Bitmap.createBitmap(metrics.widthPixels / downScale, metrics.heightPixels / downScale, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.translate(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            canvas.rotate(360f - degrees);
            canvas.translate(-dims[0] / 2, -dims[1] / 2);
            canvas.drawBitmap(screenBitmap, 0, 0, null);
            canvas.setBitmap(null);
            screenBitmap = bitmap;
        }

        Bitmap mutable = screenBitmap.copy(Bitmap.Config.ARGB_8888, true);

        mutable.setHasAlpha(false);
        mutable.prepareToDraw();

        return mutable;
    }

    private static float getDegreesForRotation(int value) {

        switch (value) {
            case Surface.ROTATION_90:
                return 90f;
            case Surface.ROTATION_180:
                return 180f;
            case Surface.ROTATION_270:
                return 270f;
        }
        return 0f;
    }
}
