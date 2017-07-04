/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import com.android.systemui.statusbar.policy.BatteryController;

import cyanogenmod.providers.CMSettings;

import org.cyanogenmod.graphics.drawable.StopMotionVectorDrawable;

public class BatteryMeterDrawable extends Drawable implements
        BatteryController.BatteryStateChangeCallback {

    private static final float ASPECT_RATIO = 9.5f / 14.3f;
    public static final String TAG = BatteryMeterDrawable.class.getSimpleName();
    public static final String SHOW_PERCENT_SETTING = "status_bar_show_battery_percent";
    private static final String STATUS_BAR_CHARGE_COLOR =
            Settings.Secure.STATUS_BAR_CHARGE_COLOR;

    private static final boolean SINGLE_DIGIT_PERCENT = false;

    private static final int FULL = 96;

    private static final float BOLT_LEVEL_THRESHOLD = 0.3f;  // opaque bolt below this fraction

    // Values for the different battery styles
    public static final int BATTERY_STYLE_PORTRAIT  = 0;
    public static final int BATTERY_STYLE_SOLID     = 1;
    public static final int BATTERY_STYLE_CIRCLE    = 2;
    public static final int BATTERY_STYLE_HIDDEN    = 3;
    public static final int BATTERY_STYLE_LANDSCAPE = 4;
    public static final int BATTERY_STYLE_TEXT      = 5;

    private final int[] mColors;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;

    private int mShowPercent;
    private float mButtonHeightFraction;
    private float mSubpixelSmoothingLeft;
    private float mSubpixelSmoothingRight;
    private float mTextHeight, mWarningTextHeight;
    private int mIconTint;
    private float mOldDarkIntensity = 0f;

    private int mHeight;
    private int mWidth;
    private String mWarningString;
    private final int mCriticalLevel;
    private int mChargeColor;
    private boolean mBoltOverlay;
    private final Path mBoltPath = new Path();
    private final Path mPlusPath = new Path();

    private final RectF mFrame = new RectF();
    private final RectF mButtonFrame = new RectF();
    private final RectF mBoltFrame = new RectF();
    private final RectF mPlusFrame = new RectF();

    private final Path mShapePath = new Path();
    private final Path mClipPath = new Path();
    private final Path mTextPath = new Path();

    private BatteryController mBatteryController;
    private boolean mPowerSaveEnabled;

    private int mDarkModeBatteryMeterFrameColor;
    private int mDarkModeFillColor;

    private int mLightModeBatteryMeterFrameColor;
    private int mLightModeFillColor;

    private final SettingObserver mSettingObserver = new SettingObserver();

    private final Context mContext;
    private final Handler mHandler;

    private int mLevel = -1;
    private boolean mPluggedIn;
    private boolean mListening;

    private boolean mIsAnimating; // stores charge-animation status to remove callbacks

    private float mTextX, mTextY; // precalculated position for drawText() to appear centered

    private boolean mInitialized;

    private Paint mTextAndBoltPaint;
    private Paint mWarningTextPaint;
    private Paint mClearPaint;

    private LayerDrawable mBatteryDrawable;
    private Drawable mFrameDrawable;
    private StopMotionVectorDrawable mLevelDrawable;
    private Drawable mBoltDrawable;

    private int mTextGravity;

    private int mCurrentBackgroundColor = 0;
    private int mCurrentFillColor = 0;
    private int mStyle;
    private boolean mForceChargeBatteryText;
    private int mTextChargingSymbol;

    private ValueAnimator mAnimator;

    public BatteryMeterDrawable(Context context, Handler handler, int frameColor) {
        // Portrait is the default drawable style
        this(context, handler, frameColor, BATTERY_STYLE_PORTRAIT,false);
    }


    public BatteryMeterDrawable(Context context, Handler handler, int frameColor, int style) {
        this(context, handler, frameColor, style, false);
    }

    public BatteryMeterDrawable(Context context, Handler handler, int frameColor, int style , boolean boltOverlay) {
        mContext = context;
        mHandler = handler;
        mStyle = style;
        mBoltOverlay = boltOverlay;
        final Resources res = context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            mColors[2*i+1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();
        updateShowPercent();
        updateForceChargeBatteryText();
        updateCustomChargingSymbol();
        mWarningString = context.getString(R.string.battery_meter_very_low_overlay_symbol);
        mCriticalLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        mButtonHeightFraction = context.getResources().getFraction(
                R.fraction.battery_button_height_fraction, 1, 1);
        mSubpixelSmoothingLeft = context.getResources().getFraction(
                R.fraction.battery_subpixel_smoothing_left, 1, 1);
        mSubpixelSmoothingRight = context.getResources().getFraction(
                R.fraction.battery_subpixel_smoothing_right, 1, 1);
        mChargeColor = mContext.getResources().getColor(R.color.batterymeter_charge_color);

        loadBatteryDrawables(res, style);

        // Load text gravity and blend mode
        final int[] attrs = new int[] { android.R.attr.gravity, R.attr.blendMode };
        final int resId = getBatteryDrawableStyleResourceForStyle(style);
        PorterDuff.Mode xferMode = PorterDuff.Mode.XOR;
        if (resId != 0) {
            TypedArray a = mContext.obtainStyledAttributes(resId, attrs);
            mTextGravity = a.getInt(0, Gravity.CENTER);
            if (mBoltOverlay) {
                xferMode = PorterDuff.Mode.OVERLAY;
            } else {
                xferMode = PorterDuff.intToMode(a.getInt(1, PorterDuff.modeToInt(PorterDuff.Mode.XOR)));
            }
            a.recycle();
        } else {
            mTextGravity = Gravity.CENTER;
        }
        mIconTint = mContext.getResources().getColor(R.color.battery_charge_level_on_normal_statusbar);

        mTextAndBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        mTextAndBoltPaint.setTypeface(font);
        mTextAndBoltPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));
        mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(xferMode));
        mTextAndBoltPaint.setColor(mBoltOverlay || mCurrentFillColor == 0 ? getBoltColor() : mCurrentFillColor);
        mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWarningTextPaint.setColor(mColors[1]);
        font = Typeface.create("sans-serif", Typeface.BOLD);
        mWarningTextPaint.setTypeface(font);
        mWarningTextPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));

        mClearPaint = new Paint();
        mClearPaint.setColor(0);

        mDarkModeBatteryMeterFrameColor =
                context.getColor(R.color.batterymeter_frame_color_darkintensity);
        mDarkModeFillColor = context.getColor(R.color.battery_charge_level_on_light_statusbar);
        mLightModeBatteryMeterFrameColor =
                context.getColor(R.color.batterymeter_frame_color);
        mLightModeFillColor = context.getColor(R.color.battery_charge_level_on_normal_statusbar);

        mDarkModeBatteryMeterFrameColor =
                context.getColor(R.color.batterymeter_frame_color_darkintensity);
        mLightModeBatteryMeterFrameColor =
                context.getColor(R.color.batterymeter_frame_color);

        mIntrinsicWidth = context.getResources().getDimensionPixelSize(R.dimen.battery_width);
        mIntrinsicHeight = context.getResources().getDimensionPixelSize(R.dimen.battery_height);
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    public void startListening() {
        mListening = true;
        mContext.getContentResolver().registerContentObserver(
                CMSettings.System.getUriFor(CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT),
                false, mSettingObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.STATUS_BAR_CHARGE_COLOR),
                false, mSettingObserver);
        mContext.getContentResolver().registerContentObserver(
                CMSettings.System.getUriFor(CMSettings.System.STATUS_BAR_BATTERY_STYLE),
                false, mSettingObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.FORCE_CHARGE_BATTERY_TEXT),
                false, mSettingObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.TEXT_CHARGING_SYMBOL),
                false, mSettingObserver);
        updateShowPercent();
        updateChargeColor();
        updateForceChargeBatteryText();
        updateCustomChargingSymbol();
        mBatteryController.addStateChangedCallback(this);
    }

    public void stopListening() {
        mListening = false;
        mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
        mBatteryController.removeStateChangedCallback(this);
    }

    public void disableShowPercent() {
        mShowPercent = 0;
        postInvalidate();
    }

    private void postInvalidate() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidateSelf();
            }
        });
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mPowerSaveEnabled = mBatteryController.isPowerSave();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        mLevel = level;
        mPluggedIn = pluggedIn;
       if (Settings.Secure.getInt(mContext.getContentResolver(),
            Settings.Secure.STATUS_BAR_PULSE_CHARGING_BATTERY, 0) == 1) {
            animateBattery(level, pluggedIn, charging);
       }
       postInvalidate();
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        mPowerSaveEnabled = isPowerSave;
        invalidateSelf();
    }

    private static float[] loadBoltPoints(Resources res) {
        final int[] pts = res.getIntArray(R.array.batterymeter_bolt_points);
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float)pts[i] / maxX;
            ptsF[i + 1] = (float)pts[i + 1] / maxY;
        }
        return ptsF;
    }

    private static float[] loadPlusPoints(Resources res) {
        final int[] pts = res.getIntArray(R.array.batterymeter_plus_points);
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float) pts[i] / maxX;
            ptsF[i + 1] = (float) pts[i + 1] / maxY;
        }
        return ptsF;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        mHeight = bottom - top;
        mWidth = right - left;
        mWarningTextPaint.setTextSize(mHeight * 0.75f);
        mWarningTextHeight = -mWarningTextPaint.getFontMetrics().ascent;
    }

    private void updateShowPercent() {
        mShowPercent = CMSettings.System.getInt(mContext.getContentResolver(),
                CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0);
    }

    private void updateChargeColor() {
        mChargeColor = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.STATUS_BAR_CHARGE_COLOR,
                        mContext.getResources().getColor(R.color.batterymeter_charge_color));
    }

    private void updateForceChargeBatteryText() {
        mForceChargeBatteryText = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.FORCE_CHARGE_BATTERY_TEXT, 0) == 1;
    }

    private void updateCustomChargingSymbol() {
        mTextChargingSymbol = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TEXT_CHARGING_SYMBOL, 0);
    }

    private int updateDarkDensityChargeColor() {
        updateChargeColor();
        return mChargeColor;
    }

    private int getColorForLevel(int percent) {
        return getColorForLevel(percent, false);
    }


    private int getColorForLevel(int percent, boolean isChargeLevel) {
         if (mPluggedIn) {
            int chargeColor = mChargeColor;
            return chargeColor;
        } else {
            if (mBoltOverlay) {
                if (mPowerSaveEnabled || percent > mColors[0]) {
                    if (isChargeLevel) {
                        return mColors[mColors.length-1];
                    } else {
                        return getBoltColor();
                    }
                } else {
                    if ((mStyle == BATTERY_STYLE_SOLID || mStyle == BATTERY_STYLE_CIRCLE)
                         && !mPluggedIn) {
                         return mColors[1];
                    } else if (!isChargeLevel) {
                         return getBoltColor();
                    }
                }
            }
           // If we are in power save mode, always use the normal color.
           if (mPowerSaveEnabled) {
               return mColors[mColors.length - 1];
           }
           int thresh = 0;
           int color = 0;
           for (int i = 0; i < mColors.length; i += 2) {
                thresh = mColors[i];
                color = mColors[i + 1];
                if (percent <= thresh) {

                   // Respect tinting for "normal" level
                    if (i == mColors.length - 2) {
                        return mIconTint;
                    } else {
                       return color;
                    }
                }
           }
           return color;
       }
    }

    public void animateBattery(int level, boolean pluggedIn, boolean charging) {
        if (charging) {
            if (mAnimator != null) mAnimator.cancel();

            final int defaultAlpha = mLevelDrawable.getAlpha();
            mAnimator = ValueAnimator.ofInt(defaultAlpha, 0, defaultAlpha);
            mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mLevelDrawable.setAlpha((int) animation.getAnimatedValue());
                    invalidateSelf();
                }
            });
            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mLevelDrawable.setAlpha(defaultAlpha);
                    mAnimator = null;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mLevelDrawable.setAlpha(defaultAlpha);
                    mAnimator = null;
                }
            });
            mAnimator.setDuration(2000);
            mAnimator.start();
        }
    }

    public void setDarkIntensity(float darkIntensity) {
        if (darkIntensity == mOldDarkIntensity) {
            return;
        }
        mCurrentBackgroundColor = getBackgroundColor(darkIntensity);
        mCurrentFillColor = getFillColor(darkIntensity);
        mIconTint = mCurrentFillColor;
        if (darkIntensity == 0f) {
            updateChargeColor();
            if (mBoltDrawable !=null) {
            mBoltDrawable.setTint(0xff000000 | mChargeColor);
            }
        } else {
            mChargeColor = mCurrentFillColor;
            if (mBoltDrawable !=null) {
            mBoltDrawable.setTint(0xff000000 | mCurrentFillColor);
            }
        }
        // Make bolt fully opaque for increased visibility
        mFrameDrawable.setTint(mCurrentBackgroundColor);
		if (mBoltDrawable != null) {
            mBoltDrawable.setTint(0xff000000 | mCurrentFillColor);
            updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
        }
        invalidateSelf();
        mOldDarkIntensity = darkIntensity;
    }

    private int getBackgroundColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeBatteryMeterFrameColor, mDarkModeBatteryMeterFrameColor);
    }

    private int getFillColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeFillColor, mDarkModeFillColor);
    }

    private int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, lightColor, darkColor);
    }

    @Override
    public void draw(Canvas c) {
        if (!mInitialized) {
            init();
        }

        drawBattery(c);
    }

    // Some stuff required by Drawable.
    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    private final class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateShowPercent();
            updateChargeColor();
            updateForceChargeBatteryText();
            updateCustomChargingSymbol();
            postInvalidate();
        }
    }

    private void loadBatteryDrawables(Resources res, int style) {
        if (isThemeApplied()) {
            try {
                checkBatteryMeterDrawableValid(res, style);
            } catch (BatteryMeterDrawableException e) {
                Log.w(TAG, "Invalid themed battery meter drawable, falling back to system", e);
/*              Disable until the theme engine is brought up
                PackageManager pm = mContext.getPackageManager();
                try {
                    res = pm.getThemedResourcesForApplication(mContext.getPackageName(),
                            ThemeConfig.SYSTEM_DEFAULT);
                } catch (PackageManager.NameNotFoundException nnfe) {
                    // Ignore; this should not happen
                }
*/
            }
        }

        final int drawableResId = getBatteryDrawableResourceForStyle(style);
        mBatteryDrawable = (LayerDrawable) mContext.getDrawable(drawableResId);
        mFrameDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_frame);
        mFrameDrawable.setTint(mCurrentBackgroundColor != 0
                ? mCurrentBackgroundColor : res.getColor(R.color.batterymeter_frame_color));
        // Set the animated vector drawable we will be stop-animating
        final Drawable levelDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_fill);
        mLevelDrawable = new StopMotionVectorDrawable(levelDrawable);
        mBoltDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
    }

    private boolean isThemeApplied() {
        final ThemeConfig themeConfig = ThemeConfig.getBootTheme(mContext.getContentResolver());
        return themeConfig != null &&
                !ThemeConfig.SYSTEM_DEFAULT.equals(themeConfig.getOverlayForStatusBar());
    }

    private void checkBatteryMeterDrawableValid(Resources res, int style) {
        final int resId = getBatteryDrawableResourceForStyle(style);
        final Drawable batteryDrawable;
        try {
            batteryDrawable = mContext.getDrawable(resId);
        } catch (Resources.NotFoundException e) {
            throw new BatteryMeterDrawableException(res.getResourceName(resId) + " is an " +
                    "invalid drawable", e);
        }

        // Check that the drawable is a LayerDrawable
        if (!(batteryDrawable instanceof LayerDrawable)) {
            throw new BatteryMeterDrawableException("Expected a LayerDrawable but received a " +
                    batteryDrawable.getClass().getSimpleName());
        }

        final LayerDrawable layerDrawable = (LayerDrawable) batteryDrawable;
        final Drawable frame = layerDrawable.findDrawableByLayerId(R.id.battery_frame);
        final Drawable level = layerDrawable.findDrawableByLayerId(R.id.battery_fill);
        // Now, check that the required layers exist and are of the correct type
        if (frame == null) {
            throw new BatteryMeterDrawableException("Missing battery_frame drawble");
        }
        if (level != null) {
            // Check that the level drawable is an AnimatedVectorDrawable
            if (!(level instanceof AnimatedVectorDrawable)) {
                throw new BatteryMeterDrawableException("Expected a AnimatedVectorDrawable " +
                        "but received a " + level.getClass().getSimpleName());
            }
            // Make sure we can stop-motion animate the level drawable
            try {
                StopMotionVectorDrawable smvd = new StopMotionVectorDrawable(level);
                smvd.setCurrentFraction(0.5f);
            } catch (Exception e) {
                throw new BatteryMeterDrawableException("Unable to perform stop motion on " +
                        "battery_fill drawable", e);
            }
        } else {
            throw new BatteryMeterDrawableException("Missing battery_fill drawable");
        }
    }

    private int getBatteryDrawableResourceForStyle(final int style) {
        switch (style) {
            case BATTERY_STYLE_LANDSCAPE:
                return R.drawable.ic_battery_landscape;
            case BATTERY_STYLE_CIRCLE:
                return R.drawable.ic_battery_circle;
            case BATTERY_STYLE_SOLID:
                return R.drawable.ic_battery_solid;
            case BATTERY_STYLE_PORTRAIT:
                return R.drawable.ic_battery_portrait;
            default:
                return 0;
        }
    }

    private int getBatteryDrawableStyleResourceForStyle(final int style) {
        switch (style) {
            case BATTERY_STYLE_LANDSCAPE:
                return R.style.BatteryMeterViewDrawable_Landscape;
            case BATTERY_STYLE_CIRCLE:
                return R.style.BatteryMeterViewDrawable_Circle;
			case BATTERY_STYLE_SOLID:
                return R.style.BatteryMeterViewDrawable_Solid;
            case BATTERY_STYLE_PORTRAIT:
                return R.style.BatteryMeterViewDrawable_Portrait;
            default:
                return R.style.BatteryMeterViewDrawable;
        }
    }


    private int getBoltColor() {
        if (mBoltOverlay) {
            updateChargeColor();
            return mContext.getResources().getColor((mStyle == BATTERY_STYLE_SOLID ||
                mStyle == BATTERY_STYLE_CIRCLE) ? R.color.batterymeter_bolt_color : R.color.system_primary_color);
        }
        return mContext.getResources().getColor(R.color.batterymeter_bolt_color);
    }

    /**
     * Initializes all size dependent variables
     */
    private void init() {
        // Not much we can do with zero width or height, we'll get another pass later
        if (mWidth <= 0 || mHeight <= 0) return;

        final float widthDiv2 = mWidth / 2f;
        // text size is width / 2 - 2dp for wiggle room

        if ((Settings.System.getInt(mContext.getContentResolver(), Settings.System.BATTERY_LARGE_TEXT, 0) == 1)) {
        final float textSize;
        switch(mStyle) {
            case BATTERY_STYLE_CIRCLE:
                textSize = widthDiv2 - mContext.getResources().getDisplayMetrics().density / 1.3f;
                break;
            case BATTERY_STYLE_LANDSCAPE:
                textSize = widthDiv2 * 1.3f;
                break;
            default:
                textSize = widthDiv2;
                break;
                }
        mTextAndBoltPaint.setTextSize(textSize);
        mWarningTextPaint.setTextSize(textSize);
        } else {
        final float textSize = widthDiv2 - mContext.getResources().getDisplayMetrics().density * 2;
        mTextAndBoltPaint.setTextSize(textSize);
        mWarningTextPaint.setTextSize(textSize);
        }        

        Rect iconBounds = new Rect(0, 0, mWidth, mHeight);
        mBatteryDrawable.setBounds(iconBounds);

        // Calculate text position
        Rect bounds = new Rect();
        mTextAndBoltPaint.getTextBounds("99", 0, "99".length(), bounds);
        final boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        // Compute mTextX based on text gravity
        if ((mTextGravity & Gravity.START) == Gravity.START) {
            mTextX = isRtl ? mWidth : 0;
        } else if ((mTextGravity & Gravity.END) == Gravity.END) {
            mTextX = isRtl ? 0 : mWidth;
        } else if ((mTextGravity & Gravity.LEFT) == Gravity.LEFT) {
            mTextX = 0;
        } else if ((mTextGravity & Gravity.RIGHT) == Gravity.RIGHT) {
            mTextX = mWidth;
        } else {
            mTextX = widthDiv2;
        }

        // Compute mTextY based on text gravity
        if ((mTextGravity & Gravity.TOP) == Gravity.TOP) {
            mTextY = bounds.height();
        } else if ((mTextGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            mTextY = mHeight;
        } else {
            mTextY = widthDiv2 + bounds.height() / 2.0f;
        }

		if (mBoltDrawable != null) {
            updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
        }

        mInitialized = true;
    }

    // Creates a BitmapDrawable of the bolt so we can make use of
    // the XOR xfer mode with vector-based drawables
    private void updateBoltDrawableLayer(LayerDrawable batteryDrawable, Drawable boltDrawable) {
        BitmapDrawable newBoltDrawable;
        if (boltDrawable instanceof BitmapDrawable) {
            newBoltDrawable = (BitmapDrawable) boltDrawable.mutate();
        } else {
            Bitmap boltBitmap = createBoltBitmap(boltDrawable);
            if (boltBitmap == null) {
                // Not much to do with a null bitmap so keep original bolt for now
                return;
            }
            Rect bounds = boltDrawable.getBounds();
            newBoltDrawable = new BitmapDrawable(mContext.getResources(), boltBitmap);
            newBoltDrawable.setBounds(bounds);
        }
        newBoltDrawable.getPaint().set(mTextAndBoltPaint);
        if (mBoltOverlay && mBoltDrawable != null) {
            newBoltDrawable.setTint(getBoltColor());
        }
        batteryDrawable.setDrawableByLayerId(R.id.battery_charge_indicator, newBoltDrawable);
    }

    private Bitmap createBoltBitmap(Drawable boltDrawable) {
        // Not much we can do with zero width or height, we'll get another pass later
        if (mWidth <= 0 || mHeight <= 0) return null;

        Bitmap bolt;
        if (!(boltDrawable instanceof BitmapDrawable)) {
            Rect iconBounds = new Rect(0, 0, mWidth, mHeight);
            bolt = Bitmap.createBitmap(iconBounds.width(), iconBounds.height(),
                    Bitmap.Config.ARGB_8888);
            if (bolt != null) {
                Canvas c = new Canvas(bolt);
                c.drawColor(-1, PorterDuff.Mode.CLEAR);
                boltDrawable.draw(c);
            }
        } else {
            bolt = ((BitmapDrawable) boltDrawable).getBitmap();
        }

        return bolt;
    }

    private void drawBattery(Canvas canvas) {
        final int level = mLevel;
        updatePortDuffMode();
        handleBoltVisibility();
        // Now draw the level indicator
        // Set the level and tint color of the fill drawable
        mLevelDrawable.setCurrentFraction(level / 100f);
        mLevelDrawable.setTint(getColorForLevel(level, true));
        mBatteryDrawable.draw(canvas);

        // If chosen by options, draw percentage text in the middle
        // Always skip percentage when 100, so layout doesnt break
        if (!mPluggedIn || (mPluggedIn && !mForceChargeBatteryText)) {
            drawPercentageText(canvas);
        }
    }

    private void updatePortDuffMode() {
        final int level = mLevel;
        if (level >15 && level <31 && mStyle != BATTERY_STYLE_CIRCLE) {
            mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
            mTextAndBoltPaint.setColor(mBoltOverlay ? Color.WHITE : mIconTint); //mIconTint so when darkintensity enabled the pct is dark and more visible
        } else if (level <=15 && mStyle != BATTERY_STYLE_CIRCLE){
            mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
            mTextAndBoltPaint.setColor(getColorForLevel(level));
        } else {
            if (mBoltOverlay) {
                mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
            } else  {
                //have to recreate the typedarray here otherwise the XOR mode doesn't apply well
                final int[] attrs = new int[] { android.R.attr.gravity, R.attr.blendMode };
                final int resId = getBatteryDrawableStyleResourceForStyle(mStyle);
                if (resId != 0) {
                    TypedArray a = mContext.obtainStyledAttributes(resId, attrs);
                    mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.intToMode(a.getInt(1, PorterDuff.modeToInt(PorterDuff.Mode.XOR)))));
                    a.recycle();
                }
            }
            mTextAndBoltPaint.setColor(getColorForLevel(level));
        }
    }

    private void handleBoltVisibility() {
        final Drawable d = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
        if (d != null) {
            if (d instanceof BitmapDrawable) {
                // In case we are using a BitmapDrawable, which we should be unless something bad
                // happened, we need to change the paint rather than the alpha in case the blendMode
                // has been set to clear.  Clear always clears regardless of alpha level ;)
                final BitmapDrawable bd = (BitmapDrawable) d;
                bd.getPaint().set(!mPluggedIn || (mPluggedIn && mShowPercent == 1 && (!mForceChargeBatteryText
                        || (mForceChargeBatteryText && mTextChargingSymbol != 0)))
                        || (mPluggedIn && mShowPercent == 2 && mTextChargingSymbol != 0)
                        || (mPluggedIn && mShowPercent == 0 && (mForceChargeBatteryText && mTextChargingSymbol != 0))
                        ? mClearPaint : mTextAndBoltPaint);
                if (mBoltOverlay) {
                    mBoltDrawable.setTint(getBoltColor());
                }
            } else {
                d.setAlpha(!mPluggedIn || (mPluggedIn && mShowPercent == 1 && (!mForceChargeBatteryText
                        || (mForceChargeBatteryText && mTextChargingSymbol != 0)))
                        || (mPluggedIn && mShowPercent == 2 && mTextChargingSymbol != 0)
                        || (mPluggedIn && mShowPercent == 0 && (mForceChargeBatteryText && mTextChargingSymbol != 0)) ? 0 : 255);
            }
        }
    }

    private void drawPercentageText(Canvas canvas) {
        final int level = mLevel;
        if (level > mCriticalLevel && mShowPercent == 1 && level != 100) {
            // Draw the percentage text
            String pctText = String.valueOf(SINGLE_DIGIT_PERCENT ? (level / 10) : level);
            mTextAndBoltPaint.setColor(getColorForLevel(level));
            canvas.drawText(pctText, mTextX, mTextY, mTextAndBoltPaint);
            if (mBoltDrawable != null) {
                mBoltDrawable.setTint(getBoltColor());
            }
        } else if (level <= mCriticalLevel) {
            // Draw the warning text
            canvas.drawText(mWarningString, mTextX, mTextY, mWarningTextPaint);
        }
    }

    private Paint.Align getPaintAlignmentFromGravity(int gravity) {
        final boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if ((gravity & Gravity.START) == Gravity.START) {
            return isRtl ? Paint.Align.RIGHT : Paint.Align.LEFT;
        }
        if ((gravity & Gravity.END) == Gravity.END) {
            return isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT;
        }
        if ((gravity & Gravity.LEFT) == Gravity.LEFT) return Paint.Align.LEFT;
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) return Paint.Align.RIGHT;

        // Default to center
        return Paint.Align.CENTER;
    }

    private class BatteryMeterDrawableException extends RuntimeException {
        public BatteryMeterDrawableException(String detailMessage) {
            super(detailMessage);
        }

        public BatteryMeterDrawableException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}
