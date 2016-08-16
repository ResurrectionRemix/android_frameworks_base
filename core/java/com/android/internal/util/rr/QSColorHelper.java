/*
* Copyright (C) 2015 DarkKat

*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.util.rr;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;

public class QSColorHelper {

    private static int WHITE = 0xffffffff;

   public static int getBrightnessSliderColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.QS_BRIGHTNESS_SLIDER_COLOR, WHITE);
    }

    public static int getRippleColor(Context context) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QS_RIPPLE_COLOR, WHITE);
        int colorToUse =  (74 << 24) | (color & 0x00ffffff);
        return colorToUse;
    }

    public static int getBrightnessSliderEmptyColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.QS_BRIGHTNESS_SLIDER_BG_COLOR, WHITE);
    }

    public static int getQsBgGradientOrientation(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.QS_BACKGROUND_GRADIENT_ORIENTATION, 270);
    }

    private static boolean useBgGradientCenterColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.QS_BACKGROUND_GRADIENT_USE_CENTER_COLOR,
                0) == 1;
    }

    public static int[] getBackgroundColors(Context context) {
        int startColor = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QS_BACKGROUND_COLOR_START, 0xff000000);
        int[] colors;
        //if (getBackgroundType(context) == BACKGROUND_TYPE_GRADIENT) {
            int centerColor = useBgGradientCenterColor(context)
                    ? Settings.System.getInt(context.getContentResolver(),
                            Settings.System.QS_BACKGROUND_COLOR_CENTER, 0xff000000)
                    : 0;
            int endColor = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.QS_BACKGROUND_COLOR_END, 0xff000000);

            colors = new int[useBgGradientCenterColor(context) ? 3 : 2];
            colors[0] = startColor;
            if (useBgGradientCenterColor(context)) {
                colors[1] = centerColor;
            }
            colors[useBgGradientCenterColor(context) ? 2 : 1] = endColor;
        /*} else {
            colors = new int[2];
            colors[0] = startColor;
            colors[1] = startColor;
        }*/
        return colors;
    }
}
