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

import android.app.Notification;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import com.android.internal.util.NotificationColorUtil;
import com.android.internal.util.rr.ColorHelper;

public class NotificationColorHelper {

    public static int getNotificationMediaBgColor(Context context, int bgColor) {
        final int DEFAULT_MEDIA_BG = 0xff424242;
        if (getMediaBgMode(context) == 0) {
            return bgColor;
        } else if (getMediaBgMode(context) == 1) {
            return bgColor != DEFAULT_MEDIA_BG ? bgColor
                    : getCustomNotificationBgColor(context);
        } else {
            return getCustomNotificationBgColor(context);
        }
    }

    public static int getLegacyBgColor(Context context, int notificationColor) {
        if (colorizeIconBackground(context, notificationColor)) {
           return (255 << 24) | (getCustomLegacyBgColor(context) & 0x00ffffff);
        } else if (notificationColor != Notification.COLOR_DEFAULT) {
            return notificationColor;
        } else {
            if (ColorHelper.isColorDark(getCustomNotificationBgColor(context))) {
                return Notification.COLOR_DEFAULT;
            } else {
                return Color.BLACK;
            }
        }
    }

    public static int getLegacyBgAlpha(Context context, int notificationColor) {
        if (colorizeIconBackground(context, notificationColor)) {
            return Color.alpha(getCustomLegacyBgColor(context));
        } else if (notificationColor != Notification.COLOR_DEFAULT) {
            return 255;
        } else {
            return 77;
        }
    }

    public static int getIconColor(Context context, Drawable icon) {
        if (colorizeIcon(context, icon)) {
           return getCustomIconColor(context);
        } else {
            return 0;
        }
    }

    public static int getdividerColor(Context context) {
        if (ColorHelper.isColorDark(getCustomNotificationBgColor(context))) {
            return 0x6f222222;
        } else {
            return 0x6fdddddd;
        }
    }

    private static boolean colorizeIconBackground(Context context, int notificationColor) {
        final int legacyBgMode = getLegacyBgMode(context);
        if (legacyBgMode == 0) {
            return false;
        } else if (legacyBgMode == 1) {
            return notificationColor == Notification.COLOR_DEFAULT;
        } else {
            return true;
        }
    }

    public static boolean colorizeIcon(Context context, Drawable d) {
        if (d == null) {
            return false;
        }

        NotificationColorUtil cu = NotificationColorUtil.getInstance(context);
        final int iconColorMode = getIconColorMode(context);
        final boolean isGreyscale = cu.isGrayscaleIcon(d);

        if (iconColorMode == 0) {
            return false;
        } else if (iconColorMode == 1) {
            return isGreyscale;
        } else {
            return true;
        }
    }

    private static int getMediaBgMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_MEDIA_BG_MODE, 0);
    }

    private static int getLegacyBgMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_APP_ICON_BG_MODE, 0);
    }

    private static int getIconColorMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_APP_ICON_COLOR_MODE, 0);
    }

    public static int getCustomNotificationBgColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_BG_COLOR, 0xffffffff);
    }

    private static int getCustomLegacyBgColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_APP_ICON_BG_COLOR, 0x4dffffff);
    }

    public static int getCustomTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_TEXT_COLOR, 0xff000000);
    }

    public static int getCustomIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_ICON_COLOR, 0xff000000);
    }
}
