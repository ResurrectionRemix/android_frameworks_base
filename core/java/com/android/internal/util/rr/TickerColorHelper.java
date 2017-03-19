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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.provider.Settings;

public class TickerColorHelper {

    private static final int WHITE = 0xffffffff;

    public static int getTickerTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_TEXT_COLOR, WHITE);
    }

    public static ColorStateList getTickerIconColorList(Context context, int defaultColor) {
        return ColorStateList.valueOf(getTickerIconColor(context, defaultColor));
    }

    public static int getTickerIconColor(Context context, int defaultColor) {
        int color = Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_ICON_COLOR,
                WHITE);
        if (color == WHITE) {
            return defaultColor;
        } else {
            return color;
        }
    }
}
