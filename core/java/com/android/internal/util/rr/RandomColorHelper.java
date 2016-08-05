/*
* Copyright (C) 2016 Cyanide Android (rogersb11)
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
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import com.android.internal.util.NotificationColorUtil;

public class RandomColorHelper {

    private static final int WHITE = 0xffffffff;
    private static final int BLACK = 0xff000000;
    private static final int TRANSLUCENT_BLACK = 0x7a000000;

    public static ColorStateList getToastIconColorList(Context context) {
        return ColorStateList.valueOf(getToastIconColor(context));
    }

    public static int getToastIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TOAST_ICON_COLOR, WHITE);
    }

    public static ColorStateList getToastTextColorList(Context context) {
        return ColorStateList.valueOf(getToastTextColor(context));
    }

    public static int getToastTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TOAST_TEXT_COLOR, WHITE);
    }
}
