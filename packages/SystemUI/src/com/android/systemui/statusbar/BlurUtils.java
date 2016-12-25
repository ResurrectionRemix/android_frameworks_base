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
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

@SuppressLint("NewApi")
public class BlurUtils {

    private Context mContext;
    private RenderScript mRenderScript;
    private ScriptIntrinsicBlur mScriptIntrinsicBlur;

    public enum BlurEngine {
        RenderScriptBlur,
        StackBlur,
        FastBlur
    }

    public abstract interface BlurTaskCallback {
        public abstract void blurTaskDone(Bitmap blurredBitmap);
        public abstract void dominantColor(int color);
    }

    public BlurUtils(Context context) {
        mContext = context;
        mRenderScript = RenderScript.create(mContext);
        mScriptIntrinsicBlur = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4 (mRenderScript));
    }

    public Context getContext() {
        return mContext;
    }

    public Bitmap renderScriptBlur(Bitmap bitmap, int radius) {
        if (mRenderScript == null)
            return null;

        Allocation input = Allocation.createFromBitmap(mRenderScript, bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation output = Allocation.createTyped(mRenderScript, input.getType());
        mScriptIntrinsicBlur.setRadius(radius);
        mScriptIntrinsicBlur.setInput(input);
        mScriptIntrinsicBlur.forEach(output);
        output.copyTo(bitmap);

        return bitmap;
    }

    public Bitmap stackBlur(Bitmap bitmap, int radius) {
        if (radius < 1)
            return null;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];

        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }

            stackpointer = radius;

            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }

                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }

        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {

                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
                if (i < hm) {
                    yp += w;
                }
            }

            yi = x;
            stackpointer = radius;

            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }

                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return bitmap;

    }

    public void fastBlur(Bitmap bitmap, int radius){

        if (radius < 1)
            return;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;
        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, p1, p2, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];
        int vmax[] = new int[Math.max(w, h)];
        int[] pix = new  int[w * h];

        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int dv[]=new int[256*div];

        for (i = 0; i < 256 * div; i++) {
            dv[i] = (i / div);
        }

        yw = yi = 0;

        for (y = 0; y < h; y++) {
            rsum = gsum = bsum = 0;
            for(i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                rsum += (p & 0xff0000) >> 16;
                gsum += (p & 0x00ff00) >> 8;
                bsum += p & 0x0000ff;
            }

            for (x = 0; x < w; x++) {
                r[yi]=dv[rsum];
                g[yi]=dv[gsum];
                b[yi]=dv[bsum];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1,wm);
                    vmax[x] = Math.max(x - radius, 0);
                }

                p1 = pix[yw + vmin[x]];
                p2 = pix[yw + vmax[x]];

                rsum += ((p1 & 0xff0000) - (p2 & 0xff0000)) >> 16;
                gsum += ((p1 & 0x00ff00) - (p2 & 0x00ff00)) >> 8;
                bsum += (p1 & 0x0000ff) - (p2 & 0x0000ff);
                yi++;
            }

            yw += w;
        }

        for (x = 0; x < w; x++) {
            rsum = gsum = bsum = 0;
            yp =- radius * w;
            for (i =- radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                rsum += r[yi];
                gsum += g[yi];
                bsum +=b [yi];
                yp += w;
            }

            yi=x;
            for (y = 0; y < h; y++) {
                pix[yi] = 0xff000000 | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                if (x == 0){
                    vmin[y] = Math.min(y + radius + 1,hm) * w;
                    vmax[y] = Math.max(y - radius, 0) * w;
                }

                p1 = x + vmin[y];
                p2 = x + vmax[y];

                rsum += r[p1] -r[p2];
                gsum += g[p1] -g[p2];
                bsum += b[p1] -b[p2];

                yi+=w;
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
    }
}
