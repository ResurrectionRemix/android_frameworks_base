/*
* Copyright (C) 2019 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.rr;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.settingslib.Utils;
import com.android.systemui.R;

import androidx.palette.graphics.Palette;

public class NotificationLightsView extends RelativeLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";
    private View mNotificationAnimView;
    private ValueAnimator mLightAnimator;
    private boolean mPulsing;
    private WallpaperManager mWallManager;
    private int color;

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (DEBUG) Log.d(TAG, "new");
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
    }


    private Runnable mLightUpdate = new Runnable() {
        @Override
        public void run() {
            Log.e("NotificationLightsView", "run");
            animateNotification();
        }
    };

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
    }

    public void stopAnimateNotification() {
        if (mLightAnimator != null) {
            mLightAnimator.end();
            mLightAnimator = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        Log.e("NotificationLightsView", "draw");
    }

    public void animateNotification() {
        animateNotificationWithColor(getNotificationLightsColor());
    }

    public int getNotificationLightsColor() {
        int color = getDefaultNotificationLightsColor();
        int lightColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_COLOR, 0,
                UserHandle.USER_CURRENT);
        int customColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_CUSTOM_COLOR, getDefaultNotificationLightsColor(),
                UserHandle.USER_CURRENT);
        int blend = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_BLEND_COLOR, getDefaultNotificationLightsColor(),
                UserHandle.USER_CURRENT);
        if (lightColor == 1) {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                WallpaperInfo wallpaperInfo = wallpaperManager.getWallpaperInfo();
                if (wallpaperInfo == null) { // if not a live wallpaper
                    Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                    Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                    if (bitmap != null) { // if wallpaper is not blank
                        Palette p = Palette.from(bitmap).generate();
                        int wallColor = p.getDominantColor(color);
                        if (color != wallColor)
                            color = wallColor;
                    }
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else if (lightColor == 2) {
            color = Utils.getColorAccentDefaultColor(getContext());
        } else if (lightColor == 3) {
            color = customColor;
        } else if (lightColor == 4) {
            color = mixColors(customColor, blend);
        }  else {
            color = 0xFFFFFFFF;
        }
        return color;
    }

    public int getDefaultNotificationLightsColor() {
        return getResources().getInteger(com.android.internal.R.integer.config_AmbientPulseLightColor);
    }

    public void endAnimation() {
        mLightAnimator.end();
        mLightAnimator.removeAllUpdateListeners();
    }


    private int mixColors(int color1, int color2) {
        int[] rgb1 = colorToRgb(color1);
        int[] rgb2 = colorToRgb(color2);

        rgb1[0] = mixedValue(rgb1[0], rgb2[0]);
        rgb1[1] = mixedValue(rgb1[1], rgb2[1]);
        rgb1[2] = mixedValue(rgb1[2], rgb2[2]);
        rgb1[3] = mixedValue(rgb1[3], rgb2[3]);

        return rgbToColor(rgb1);
    }

    private int[] colorToRgb(int color) {
        int[] rgb = {(color & 0xFF000000) >> 24, (color & 0xFF0000) >> 16, (color & 0xFF00) >> 8, (color & 0xFF)};
        return rgb;
    }

    private int rgbToColor(int[] rgb) {
        return (rgb[0] << 24) + (rgb[1] << 16) + (rgb[2] << 8) + rgb[3];
    }

    private int mixedValue(int val1, int val2) {
        return (int)Math.min((val1 + val2), 255f);
    }

    public void animateNotificationWithColor(int color) {
        int duration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_DURATION, 2,
                UserHandle.USER_CURRENT) * 1000;
        int repeat = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_REPEAT_COUNT, 0,
                UserHandle.USER_CURRENT);
        boolean directionIsRestart = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_REPEAT_DIRECTION, 0,
                UserHandle.USER_CURRENT) != 1;
        int width = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_WIDTH, 125,
                UserHandle.USER_CURRENT);
        int layout = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_LAYOUT, 0,
                UserHandle.USER_CURRENT);
        ImageView leftViewSolid = (ImageView) findViewById(R.id.notification_animation_left_solid);
        ImageView leftViewFaded = (ImageView) findViewById(R.id.notification_animation_left_faded);
        leftViewSolid.setColorFilter(color);
        leftViewFaded.setColorFilter(color);
        leftViewSolid.getLayoutParams().width = width;
        leftViewFaded.getLayoutParams().width = width;
        leftViewSolid.setVisibility(layout == 0 ? View.VISIBLE : View.GONE);
        leftViewFaded.setVisibility(layout == 1 ? View.VISIBLE : View.GONE);
        ImageView rightViewSolid = (ImageView) findViewById(R.id.notification_animation_right_solid);
        ImageView rightViewFaded = (ImageView) findViewById(R.id.notification_animation_right_faded);
        rightViewSolid.setColorFilter(color);
        rightViewFaded.setColorFilter(color);
        rightViewSolid.getLayoutParams().width = width;
        rightViewFaded.getLayoutParams().width = width;
        rightViewSolid.setVisibility(layout == 0 ? View.VISIBLE : View.GONE);
        rightViewFaded.setVisibility(layout == 1 ? View.VISIBLE : View.GONE);
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(duration);
        mLightAnimator.setDuration(duration);
        if (repeat == 0) {
            mLightAnimator.setRepeatCount(ValueAnimator.INFINITE);
        } else {
            mLightAnimator.setRepeatCount(repeat - 1);
        }
        mLightAnimator.setRepeatMode(directionIsRestart ? ValueAnimator.RESTART : ValueAnimator.REVERSE);
        mLightAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Log.e("NotificationLightsView", "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftViewSolid.setScaleY(progress);
                leftViewFaded.setScaleY(progress);
                rightViewSolid.setScaleY(progress);
                rightViewFaded.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftViewSolid.setAlpha(alpha);
                leftViewFaded.setAlpha(alpha);
                rightViewSolid.setAlpha(alpha);
                rightViewFaded.setAlpha(alpha);
            }
        });
       if (DEBUG) Log.d(TAG, "start");
        mLightAnimator.start();
    }
}
