/*
 * Copyright (c) 2015, Sergii Pylypenko
 *           (c) 2018, Joe Maples
 *           (c) 2018, Adin Kwok
 *           (c) 2018, CarbonROM
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of screen-dimmer-pixel-filter nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.server.smartpixels;

import android.Manifest;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class SmartPixelsService extends Service {
    public static final String LOG = "SmartPixelsService";

    private WindowManager windowManager;
    private ImageView view = null;
    private Bitmap bmp;

    private boolean destroyed = false;
    public static boolean running = false;

    private int startCounter = 0;
    private Context mContext;

    // Pixel Filter Settings
    private int mPattern = 3;
    private int mShiftTimeout = 4;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        mContext = this;
        updateSettings();
        Log.d(LOG, "Service started");
        startFilter();
    }

    public void startFilter() {
        if (view != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        view = new ImageView(this);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        bmp = Bitmap.createBitmap(Grids.GridSideSize, Grids.GridSideSize, Bitmap.Config.ARGB_4444);

        updatePattern();
        BitmapDrawable draw = new BitmapDrawable(bmp);
        draw.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        draw.setFilterBitmap(false);
        draw.setAntiAlias(false);
        draw.setTargetDensity(metrics.densityDpi);

        view.setBackground(draw);

        WindowManager.LayoutParams params = getLayoutParams();
        try {
            windowManager.addView(view, params);
        } catch (Exception e) {
            running = false;
            view = null;
            return;
        }

        startCounter++;
        final int handlerStartCounter = startCounter;
        final Handler handler = new Handler();
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (view == null || destroyed || handlerStartCounter != startCounter) {
                    return;
                } else if (pm.isInteractive()) {
                    updatePattern();
                    view.invalidate();
                }
                if (!destroyed) {
                    handler.postDelayed(this, Grids.ShiftTimeouts[mShiftTimeout]);
                }
            }
        }, Grids.ShiftTimeouts[mShiftTimeout]);
    }

    public void stopFilter() {
        if (view == null) {
            return;
        }

        startCounter++;

        windowManager.removeView(view);
        view = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyed = true;
        stopFilter();
        Log.d(LOG, "Service stopped");
        running = false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(LOG, "Screen orientation changed, updating window layout");
        WindowManager.LayoutParams params = getLayoutParams();
        windowManager.updateViewLayout(view, params);
    }

    private WindowManager.LayoutParams getLayoutParams()
    {
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getRealSize(displaySize);
        Point windowSize = new Point();
        windowManager.getDefaultDisplay().getRealSize(windowSize);
        Resources res = getResources();
        int mStatusBarHeight = res.getDimensionPixelOffset(com.android.internal.R.dimen.status_bar_height);
        displaySize.x += displaySize.x - windowSize.x + (mStatusBarHeight * 2);
        displaySize.y += displaySize.y - windowSize.y + (mStatusBarHeight * 2);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                displaySize.x,
                displaySize.y,
                0,
                0,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSPARENT
        );

        // Use the rounded corners overlay to hide it from screenshots. See 132c9f514.
        params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY;

        params.dimAmount = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        return params;
    }

    private int getShift() {
        long shift = (System.currentTimeMillis() / Grids.ShiftTimeouts[mShiftTimeout]) % Grids.GridSize;
        return Grids.GridShift[(int)shift];
    }

    private void updatePattern() {
        int shift = getShift();
        int shiftX = shift % Grids.GridSideSize;
        int shiftY = shift / Grids.GridSideSize;
        for (int i = 0; i < Grids.GridSize; i++) {
            int x = (i + shiftX) % Grids.GridSideSize;
            int y = ((i / Grids.GridSideSize) + shiftY) % Grids.GridSideSize;
            int color = (Grids.Patterns[mPattern][i] == 0) ? Color.TRANSPARENT : Color.BLACK;
            bmp.setPixel(x, y, color);
        }
    }

    private void updateSettings() {
        mPattern = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.SMART_PIXELS_PATTERN,
                3, UserHandle.USER_CURRENT);
        mShiftTimeout = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.SMART_PIXELS_SHIFT_TIMEOUT,
                4, UserHandle.USER_CURRENT);
    }
}
