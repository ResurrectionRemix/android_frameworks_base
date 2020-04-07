package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";
    private static final String GRADIENT_COLOR_PROP = "persist.sys.theme.gradientcolor";


    static boolean isResourceAccent(String resName) {
        return resName.contains("accent_device_default_light")
                || resName.contains("accent_device_default_dark")
                || resName.contains("material_pixel_blue_dark")
                || resName.contains("material_pixel_blue_bright")
                || resName.contains("colorAccent")
                || resName.contains("omni_color5")
                || resName.contains("omni_color4")
                || resName.contains("dialer_theme_color")
                || resName.contains("dialer_theme_color_dark")
                || resName.contains("dialer_theme_color_20pct")
                || resName.contains("gradient_start")
                || resName.contains("colorAccent");
    }

    static boolean isResourceGradient(String resName) {
        return resName.contains("gradient_end");
    }
    public static int getNewAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_COLOR_PROP);
    }

    public static int getNewGradientColor(int defaultColor) {
        return getAccentColor(defaultColor, GRADIENT_COLOR_PROP);
    }

    private static int getAccentColor(int defaultColor, String property) {
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
