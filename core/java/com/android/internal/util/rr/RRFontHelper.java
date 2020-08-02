/*
* Copyright (C) 2017-2018 The OmniROM Project
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
package com.android.internal.util.rr;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.format.Time;
import android.provider.Settings;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.statusbar.IStatusBarService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RRFontHelper {


    public static void setFontType(TextView view, int font) {
        if (font == 0) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (font == 1) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (font == 2) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (font == 3) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (font == 4) {
            view.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (font == 5) {
            view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (font == 6) {
            view.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (font == 7) {
            view.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (font == 8) {
            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (font == 9) {
            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (font == 10) {
            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (font == 11) {
            view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (font == 12) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (font == 13) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (font == 14) {
            view.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (font == 15) {
            view.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (font == 16) {
            view.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (font == 17) {
            view.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (font == 18) {
            view.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (font == 19) {
            view.setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (font == 20) {
            view.setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (font == 21) {
            view.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (font == 22) {
            view.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (font == 23) {
            view.setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (font == 24) {
            view.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (font == 25) {
            view.setTypeface(Typeface.create("accuratist", Typeface.NORMAL));
        }
        if (font == 26) {
            view.setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
        }
        if (font == 27) {
            view.setTypeface(Typeface.create("amarante", Typeface.NORMAL));
        }
        if (font == 28) {
            view.setTypeface(Typeface.create("bariol", Typeface.NORMAL));
        }
        if (font == 29) {
            view.setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
        }
        if (font == 30) {
            view.setTypeface(Typeface.create("cocon", Typeface.NORMAL));
        }
        if (font == 31) {
            view.setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
        }

        if (font == 32) {
            view.setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
        }
        if (font == 33) {
            view.setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
        }
        if (font == 34) {
            view.setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
        }
        if (font == 35) {
            view.setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
        }
        if (font == 36) {
            view.setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
        }
        if (font == 37) {
            view.setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
        }
        if (font == 38) {
            view.setTypeface(Typeface.create("lato", Typeface.NORMAL));
        }
        if (font == 39) {
            view.setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
        }
        if (font == 40) {
            view.setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
        }
        if (font == 41) {
            view.setTypeface(Typeface.create("nunito", Typeface.NORMAL));
        }
        if (font == 42) {
            view.setTypeface(Typeface.create("quando", Typeface.NORMAL));
        }

        if (font == 43) {
            view.setTypeface(Typeface.create("redressed", Typeface.NORMAL));
        }
        if (font == 44) {
            view.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (font == 45) {
            view.setTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
        }
        if (font == 46) {
            view.setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
        }
        if (font == 47) {
            view.setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
        }
        if (font == 48) {
            view.setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
        }
        if (font == 49) {
            view.setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
        }
        if (font == 50) {
            view.setTypeface(Typeface.create("storopia", Typeface.NORMAL));
        }
        if (font == 51) {
            view.setTypeface(Typeface.create("surfer", Typeface.NORMAL));
        }
        if (font == 52) {
            view.setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
        }
        if (font == 53) {
            view.setTypeface(Typeface.create("antipastopro", Typeface.NORMAL));
        }
        if (font == 54) {
            view.setTypeface(Typeface.create("evolvesans", Typeface.NORMAL));
        }
        if (font == 55) {
            view.setTypeface(Typeface.create("fucek", Typeface.NORMAL));
        }
        if (font == 56) {
            view.setTypeface(Typeface.create("lemonmilk", Typeface.NORMAL));
        }
        if (font == 57) {
            view.setTypeface(Typeface.create("oduda", Typeface.NORMAL));
        }
        if (font == 58) {
            view.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
        }
        if (font == 59) {
            view.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
        }
        if (font == 60) {
            view.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        }
        if (font == 61) {
            view.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
        }
        if (font == 62) {
            view.setTypeface(Typeface.create("simpleday", Typeface.NORMAL));
        }
        if (font == 63) {
            view.setTypeface(Typeface.create("gobold-sys", Typeface.NORMAL));
        }
        if (font == 64) {
            view.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (font == 65) {
            view.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
        }
        if (font == 66) {
            view.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (font == 67) {
            view.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
        }
        if (font == 68) {
            view.setTypeface(Typeface.create("mexcellent", Typeface.NORMAL));
        }
        if (font == 69) {
            view.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
        }
        if (font == 70) {
            view.setTypeface(Typeface.create("linotte", Typeface.NORMAL));
        }
    }
}
