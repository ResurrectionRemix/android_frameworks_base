package com.android.settingslib.graph;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.Op;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.PathParser;
import android.util.TypedValue;

import com.android.settingslib.R;
import com.android.settingslib.Utils;

public class ThemedBatteryDrawable extends BatteryMeterDrawableBase {
    private int backgroundColor = 0xFFFF00FF;
    private final Path boltPath = new Path();
    private boolean charging;
    private int[] colorLevels;
    private final Context context;
    private int criticalLevel;
    private boolean dualTone;
    private int fillColor = 0xFFFF00FF;
    private final Path fillMask = new Path();
    private final RectF fillRect = new RectF();
    private int intrinsicHeight;
    private int intrinsicWidth;
    private boolean invertFillIcon;
    private int levelColor = 0xFFFF00FF;
    private final Path levelPath = new Path();
    private final RectF levelRect = new RectF();
    private final Rect padding = new Rect();
    private final Path perimeterPath = new Path();
    private final Path plusPath = new Path();
    private boolean powerSaveEnabled;
    private final Matrix scaleMatrix = new Matrix();
    private final Path scaledBolt = new Path();
    private final Path scaledFill = new Path();
    private final Path scaledPerimeter = new Path();
    private final Path scaledPlus = new Path();
    private final Path unifiedPath = new Path();
    private final Path textPath = new Path();

    private final Paint dualToneBackgroundFill;
    private final Paint fillColorStrokePaint;
    private final Paint fillColorStrokeProtection;
    private final Paint fillPaint;
    private final Paint textPaint;

    private final float mWidthDp = 12f;
    private final float mHeightDp = 20f;

    private int mMeterStyle;
    private int level;
    private boolean showPercent;

    private final Paint getDualToneBackgroundFill() {
        return this.dualToneBackgroundFill;
    }

    private final Paint getFillColorStrokePaint() {
        return this.fillColorStrokePaint;
    }

    private final Paint getFillColorStrokeProtection() {
        return this.fillColorStrokeProtection;
    }

    private final Paint getFillPaint() {
        return this.fillPaint;
    }

    private final Paint getTextPaint() {
        return this.textPaint;
    }

    public int getOpacity() {
        return -1;
    }

    public void setAlpha(int i) {
    }

    public ThemedBatteryDrawable(Context context, int frameColor) {
        super(context, frameColor);

        this.context = context;
        float f = this.context.getResources().getDisplayMetrics().density;
        this.intrinsicHeight = (int) (mHeightDp * f);
        this.intrinsicWidth = (int) (mWidthDp * f);
        Resources res = this.context.getResources();

        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        colorLevels = new int[2 * N];
        for (int i = 0; i < N; i++) {
            colorLevels[2 * i] = levels.getInt(i, 0);
            if (colors.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                colorLevels[2 * i + 1] = Utils.getColorAttr(context, colors.getThemeAttributeId(i, 0));
            } else {
                colorLevels[2 * i + 1] = colors.getColor(i, 0);
            }
        }
        levels.recycle();
        colors.recycle();
        
        setCriticalLevel(res.getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel));

        dualToneBackgroundFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        dualToneBackgroundFill.setColor(frameColor);
        dualToneBackgroundFill.setAlpha(255);
        dualToneBackgroundFill.setDither(true);
        dualToneBackgroundFill.setStrokeWidth(0f);
        dualToneBackgroundFill.setStyle(Style.FILL_AND_STROKE);

        fillColorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillColorStrokePaint.setColor(frameColor);
        fillColorStrokePaint.setDither(true);
        fillColorStrokePaint.setStrokeWidth(5f);
        fillColorStrokePaint.setStyle(Style.STROKE);
        fillColorStrokePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        fillColorStrokePaint.setStrokeMiter(5f);

        fillColorStrokeProtection = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillColorStrokeProtection.setDither(true);
        fillColorStrokeProtection.setStrokeWidth(5f);
        fillColorStrokeProtection.setStyle(Style.STROKE);
        fillColorStrokeProtection.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        fillColorStrokeProtection.setStrokeMiter(5f);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(frameColor);
        fillPaint.setAlpha(255);
        fillPaint.setDither(true);
        fillPaint.setStrokeWidth(0f);
        fillPaint.setStyle(Style.FILL_AND_STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        textPaint.setTypeface(font);
        textPaint.setTextAlign(Paint.Align.CENTER);

        loadPaths();
    }

    public void setCriticalLevel(int i) {
        this.criticalLevel = i;
    }

    public final void setCharging(boolean charging) {
        this.charging = charging;
        super.setCharging(charging);
    }

    public boolean getCharging() {
        return this.charging;
    }

    public final boolean getPowerSaveEnabled() {
        return this.powerSaveEnabled;
    }

    public final void setPowerSaveEnabled(boolean enabled) {
        this.powerSaveEnabled = enabled;
        super.setPowerSave(enabled);
    }

    public void setShowPercent(boolean show) {
        this.showPercent = show;
        super.setShowPercent(show);
    }

    public void draw(Canvas canvas) {
        if (getMeterStyle() != BATTERY_STYLE_Q) {
            super.draw(canvas);
            return;
        }
        
        boolean drawText;
        float pctX = 0, pctY = 0, textHeight;
        String pctText = null;
        if (!this.charging && !this.powerSaveEnabled && this.showPercent) {
            this.textPaint.setColor(getColorForLevel(level));
            final float full = 0.38f;
            final float nofull = 0.5f;
            final float single = 0.75f;
            this.textPaint.setTextSize(this.fillRect.height() * (this.level == 100 ? full : nofull));
            textHeight = -mTextPaint.getFontMetrics().ascent;
            pctText = String.valueOf(level);
            pctX = this.fillRect.width() * 0.5f + this.fillRect.left;
            pctY = (this.fillRect.height() + textHeight) * 0.47f + this.fillRect.top;
            this.textPath.reset();
            this.textPaint.getTextPath(pctText, 0, pctText.length(), pctX, pctY, this.textPath);
            drawText = true;
        } else {
            drawText = false;
        }

        this.unifiedPath.reset();
        this.levelPath.reset();
        this.levelRect.set(this.fillRect);
        float level = ((float) this.level) / 100.0f;
        float levelTop;
        if (this.level >= 95) {
            levelTop = this.fillRect.top;
        } else {
            RectF rectF = this.fillRect;
            levelTop = (rectF.height() * (((float) 1) - level)) + rectF.top;
        }
        this.levelRect.top = (float) Math.floor((double) levelTop);
        this.levelPath.addRect(this.levelRect, Direction.CCW);
        this.unifiedPath.addPath(this.scaledPerimeter);
        if (!this.dualTone) {
            this.unifiedPath.op(this.levelPath, Op.UNION);
            if (drawText) {
                this.unifiedPath.op(this.textPath, Op.DIFFERENCE);
            }
        }
        getFillPaint().setColor(this.levelColor);
        if (this.charging) {
            this.unifiedPath.op(this.scaledBolt, Op.DIFFERENCE);
            if (!this.invertFillIcon) {
                canvas.drawPath(this.scaledBolt, getFillPaint());
            }
        } else if (this.powerSaveEnabled) {
            this.unifiedPath.op(this.scaledPlus, Op.DIFFERENCE);
            if (!this.invertFillIcon) {
                canvas.drawPath(this.scaledPlus, getFillPaint());
            }
        }
        if (this.dualTone) {
            canvas.drawPath(this.unifiedPath, getDualToneBackgroundFill());
            canvas.save();
            float clipTop = getBounds().bottom - getBounds().height() * level;
            canvas.clipRect(0f, clipTop, (float) getBounds().right, (float) getBounds().bottom);
            canvas.drawPath(this.unifiedPath, getFillPaint());
            canvas.restore();
        } else {
            getFillPaint().setColor(this.fillColor);
            canvas.drawPath(this.unifiedPath, getFillPaint());
            getFillPaint().setColor(this.levelColor);
            if (this.level <= 15 && !this.charging) {
                canvas.save();
                canvas.clipPath(this.scaledFill);
                canvas.drawPath(this.levelPath, getFillPaint());
                canvas.restore();
            }
            if (drawText) {
                this.textPath.op(this.levelPath, Op.DIFFERENCE);
                canvas.drawPath(this.textPath, getFillPaint());
            }
        }
        if (this.charging) {
            canvas.clipOutPath(this.scaledBolt);
            if (this.invertFillIcon) {
                canvas.drawPath(this.scaledBolt, getFillColorStrokePaint());
            } else {
                canvas.drawPath(this.scaledBolt, getFillColorStrokeProtection());
            }
        } else if (this.powerSaveEnabled) {
            canvas.clipOutPath(this.scaledPlus);
            if (this.invertFillIcon) {
                canvas.drawPath(this.scaledPlus, getFillColorStrokePaint());
            } else {
                canvas.drawPath(this.scaledPlus, getFillColorStrokeProtection());
            }
        }
    }

    public int getBatteryLevel() {
        return this.level;
    }

    protected int batteryColorForLevel(int level) {
        return (this.charging || this.powerSaveEnabled)
                ? this.fillColor
                : getColorForLevel(level);
    }

    private final int getColorForLevel(int percent) {
        int thresh, color = 0;
        for (int i = 0; i < colorLevels.length; i += 2) {
            thresh = colorLevels[i];
            color = colorLevels[i + 1];
            if (percent <= thresh) {

                // Respect tinting for "normal" level
                if (i == colorLevels.length - 2) {
                    return this.fillColor;
                } else {
                    return color;
                }
            }
        }
        return color;
    }

    public void setColorFilter(ColorFilter colorFilter) {
        getFillPaint().setColorFilter(colorFilter);
        getFillColorStrokePaint().setColorFilter(colorFilter);
        getDualToneBackgroundFill().setColorFilter(colorFilter);
    }

    public int getIntrinsicHeight() {
        if (getMeterStyle() == BATTERY_STYLE_Q) {
            return this.intrinsicHeight;
        } else {
            return super.getIntrinsicHeight();
        }
    }

    public int getIntrinsicWidth() {
        if (getMeterStyle() == BATTERY_STYLE_Q) {
            return this.intrinsicWidth;
        } else {
            return super.getIntrinsicWidth();
        }
    }

    public void setBatteryLevel(int val) {
        this.level = val;
        this.invertFillIcon = val >= 67 ? true : val <= 33 ? false : this.invertFillIcon;
        this.levelColor = batteryColorForLevel(this.level);
        super.setBatteryLevel(val);
    }

    protected void onBoundsChange(Rect rect) {
        super.onBoundsChange(rect);
        updateSize();
    }

    public void setColors(int fillColor, int backgroundColor, int singleToneColor) {
        this.fillColor = this.dualTone ? fillColor : singleToneColor;
        getFillPaint().setColor(this.fillColor);
        getFillColorStrokePaint().setColor(this.fillColor);
        this.backgroundColor = backgroundColor;
        getDualToneBackgroundFill().setColor(backgroundColor);
        this.levelColor = batteryColorForLevel(this.level);
        super.setColors(fillColor, backgroundColor);
    }

    private final void updateSize() {
        Rect bounds = getBounds();
        String str = "b";
        if (bounds.isEmpty()) {
            this.scaleMatrix.setScale(1.0f, 1.0f);
        } else {
            this.scaleMatrix.setScale(((float) bounds.right) / mWidthDp, ((float) bounds.bottom) / mHeightDp);
        }
        this.perimeterPath.transform(this.scaleMatrix, this.scaledPerimeter);
        this.fillMask.transform(this.scaleMatrix, this.scaledFill);
        this.scaledFill.computeBounds(this.fillRect, true);
        this.boltPath.transform(this.scaleMatrix, this.scaledBolt);
        this.plusPath.transform(this.scaleMatrix, this.scaledPlus);
    }

    private final void loadPaths() {
        this.perimeterPath.set(PathParser.createPathFromPathData("M3.5,2 v0 H1.33 C0.6,2 0,2.6 0,3.33 V13v5.67 C0,19.4 0.6,20 1.33,20 h9.33 C11.4,20 12,19.4 12,18.67 V13V3.33 C12,2.6 11.4,2 10.67,2 H8.5 V0 H3.5 z M2,18v-7V4h8v9v5H2L2,18z"));
        this.perimeterPath.computeBounds(new RectF(), true);
        this.fillMask.set(PathParser.createPathFromPathData("M2,18 v-14 h8 v14 z"));
        this.fillMask.computeBounds(this.fillRect, true);
        this.boltPath.set(PathParser.createPathFromPathData("M5,16.8 V12 H3 L7,5.2 V10 h2 L5,16.8 z"));
        this.plusPath.set(PathParser.createPathFromPathData("M9,10l-2,0l0,-2l-2,0l0,2l-2,0l0,2l2,0l0,2l2,0l0,-2l2,0z"));
        this.dualTone = false;
    }
}