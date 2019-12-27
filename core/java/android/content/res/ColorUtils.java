package android.content.res;

import android.os.SystemProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** @hide */
public class ColorUtils {
    private static final String TAG = "ColorUtils";
    private static final float QS_MIN_LIGHTNESS = 0.0f;
    private static final float QS_MAX_LIGHTNESS = 0.6f;

    public static boolean isQsColorValid(int color) {
        float hsl[] = new float[3];
        com.android.internal.graphics.ColorUtils.colorToHSL(color, hsl);
        return hsl[2] >= QS_MIN_LIGHTNESS && hsl[2] <= QS_MAX_LIGHTNESS;
    }

    public static int getValidQsColor(int color) {
        if (isQsColorValid(color)) return color;
        float hsl[] = new float[3];
        com.android.internal.graphics.ColorUtils.colorToHSL(color, hsl);
        // We want to use the lightness at 0.75 of valid range
        hsl[2] = ((QS_MIN_LIGHTNESS * 1) + (QS_MAX_LIGHTNESS * 3)) / 4;
        return com.android.internal.graphics.ColorUtils.HSLToColor(hsl);
    }

    public static int genRandomQsColor(long seed) {
        Random r = new Random(seed);
        float hsl[] = new float[3];
        hsl[0] = r.nextInt(360);
        hsl[1] = r.nextFloat();
        hsl[2] = r.nextFloat() * 0.6f;
        return com.android.internal.graphics.ColorUtils.HSLToColor(hsl);
    }

    public static int genRandomQsColor() {
        return genRandomQsColor((long) getBootTime());
    }

    public static int getBootTime() {
        return SystemProperties.getInt("ro.boottime.init", 0);
    }

    private static int genRandomAccentColor(boolean isThemeDark, Random r) {
        float hsl[] = new float[3];
        hsl[0] = r.nextInt(360);
        hsl[1] = 0.5f + (r.nextFloat() * 0.5f);
        hsl[2] = (isThemeDark ? 0.575f : 0.3f) + (r.nextFloat() * 0.125f);
        return com.android.internal.graphics.ColorUtils.HSLToColor(hsl);
    }

    public static int genRandomAccentColor(boolean isThemeDark, long seed) {
        return genRandomAccentColor(isThemeDark, new Random(seed));
    }

    public static int genRandomAccentColor(boolean isThemeDark) {
        return genRandomAccentColor(isThemeDark, new Random());
    }
}
