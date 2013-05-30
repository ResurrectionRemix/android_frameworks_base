/*
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.internal.util.aokp;

import com.android.internal.R;

import android.content.Context;
import android.util.TypedValue;

public class StatusBarHelpers {

    private StatusBarHelpers() {
    }

    public static int pixelsToSp(Context c, Float px) {
        float scaledDensity = c.getResources().getDisplayMetrics().scaledDensity;
        return (int) (px/scaledDensity);
    }

    public static int getIconWidth(Context c, int fontsize) {

        int toppadding = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_top_padding);
        int bottompadding = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_bottom_padding);
        int padding = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_padding);
        float fontSizepx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontsize,
                c.getResources().getDisplayMetrics());
        int naturalBarHeight = (int) (fontSizepx + padding);

        int newIconSize = naturalBarHeight - (toppadding + bottompadding);
        return newIconSize;
    }
}