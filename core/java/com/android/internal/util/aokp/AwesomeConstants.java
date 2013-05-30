/*
 * Copyright (C) 2013 AOKP by Mike Wilson - Zaphod-Beeblebrox && Steve Spear - Stevespear426
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

public class AwesomeConstants {

    public static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";

    public final static int SWIPE_LEFT = 0;
    public final static int SWIPE_RIGHT = 1;
    public final static int SWIPE_DOWN = 2;
    public final static int SWIPE_UP = 3;
    public final static int TAP_DOUBLE = 4;
    public final static int PRESS_LONG = 5;
    public final static int SPEN_REMOVE = 6;
    public final static int SPEN_INSERT = 7;

    /* Adding Actions here will automatically add them to NavBar actions in ROMControl.
     * **app** must remain the last action.  Add other actions before that final action.
     * For clarity, **null** should probably also be just before APP.  New actions
     * should be added prior to **null**
     */
    public static enum AwesomeConstant {
        ACTION_HOME          { @Override public String value() { return "**home**";}},
        ACTION_BACK          { @Override public String value() { return "**back**";}},
        ACTION_MENU          { @Override public String value() { return "**menu**";}},
        ACTION_SEARCH        { @Override public String value() { return "**search**";}},
        ACTION_RECENTS       { @Override public String value() { return "**recents**";}},
        ACTION_ASSIST        { @Override public String value() { return "**assist**";}},
        ACTION_POWER         { @Override public String value() { return "**power**";}},
        ACTION_WIDGETS       { @Override public String value() { return "**widgets**";}},
        ACTION_APP_WINDOW    { @Override public String value() { return "**app_window**";}},
        ACTION_NOTIFICATIONS { @Override public String value() { return "**notifications**";}},
        ACTION_CLOCKOPTIONS  { @Override public String value() { return "**clockoptions**";}},
        ACTION_VOICEASSIST   { @Override public String value() { return "**voiceassist**";}},
        ACTION_LAST_APP      { @Override public String value() { return "**lastapp**";}},
        ACTION_RECENTS_GB    { @Override public String value() { return "**recentsgb**";}},
        ACTION_TORCH         { @Override public String value() { return "**torch**";}},
        ACTION_IME           { @Override public String value() { return "**ime**";}},
        ACTION_KILL          { @Override public String value() { return "**kill**";}},
        ACTION_SILENT        { @Override public String value() { return "**ring_silent**";}},
        ACTION_VIB           { @Override public String value() { return "**ring_vib**";}},
        ACTION_SILENT_VIB    { @Override public String value() { return "**ring_vib_silent**";}},
        ACTION_EVENT         { @Override public String value() { return "**event**";}},
        ACTION_TODAY         { @Override public String value() { return "**today**";}},
        ACTION_ALARM         { @Override public String value() { return "**alarm**";}},
        ACTION_UNLOCK        { @Override public String value() { return "**unlock**";}},
        ACTION_CAMERA        { @Override public String value() { return "**camera**";}},
        ACTION_NULL          { @Override public String value() { return "**null**";}},
        ACTION_APP           { @Override public String value() { return "**app**";}};
        public String value() { return this.value(); }
    }

    public static AwesomeConstant fromString(String string) {
        if (!TextUtils.isEmpty(string)) {
            AwesomeConstant[] allTargs = AwesomeConstant.values();
            for (int i=0; i < allTargs.length; i++) {
                if (string.equals(allTargs[i].value())) {
                    return allTargs[i];
                }
            }
        }
        // not in ENUM must be custom
        return AwesomeConstant.ACTION_APP;
    }

    public static String[] AwesomeActions() {
        return fromAwesomeActionArray(AwesomeConstant.values());
    }

    public static String[] fromAwesomeActionArray(AwesomeConstant[] allTargs) {
        int actions = allTargs.length;
        String[] values = new String [actions];
        for (int i = 0; i < actions; i++) {
            values [i] = allTargs[i].value();
        }
        return values;
    }

    public static Drawable getSystemUIDrawable(Context mContext, String DrawableID) {
        Resources res = mContext.getResources();
        PackageManager pm = mContext.getPackageManager();
        int resId = 0;
        Drawable d = res.getDrawable(com.android.internal.R.drawable.ic_action_empty);
        if (pm != null) {
            Resources mSystemUiResources = null;
            try {
                mSystemUiResources = pm.getResourcesForApplication("com.android.systemui");
            } catch (Exception e) {
            }

            if (mSystemUiResources != null && DrawableID != null) {
                resId = mSystemUiResources.getIdentifier(DrawableID, null, null);
            }
            if (resId > 0) {
                try {
                    d = mSystemUiResources.getDrawable(resId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return d;
    }

    public static String getProperName(Context context, String actionstring) {
        // Will return a string for the associated action, but will need the caller's context to get resources.
        Resources res = context.getResources();
        String value = "";
        if (TextUtils.isEmpty(actionstring)) {
            actionstring = AwesomeConstant.ACTION_NULL.value();
        }
        AwesomeConstant action = fromString(actionstring);
        switch (action) {
            case ACTION_HOME :
                value = res.getString(com.android.internal.R.string.action_home);
                break;
            case ACTION_BACK:
                value = res.getString(com.android.internal.R.string.action_back);
                break;
            case ACTION_RECENTS:
                value = res.getString(com.android.internal.R.string.action_recents);
                break;
            case ACTION_RECENTS_GB:
                value = res.getString(com.android.internal.R.string.action_recents_gb);
                break;
            case ACTION_SEARCH:
                value = res.getString(com.android.internal.R.string.action_search);
                break;
            /*case ACTION_SCREENSHOT:
                value = res.getString(com.android.internal.R.string.action_screenshot);
                break;*/
            case ACTION_MENU:
                value = res.getString(com.android.internal.R.string.action_menu);
                break;
            case ACTION_IME:
                value = res.getString(com.android.internal.R.string.action_ime);
                break;
            case ACTION_KILL:
                value = res.getString(com.android.internal.R.string.action_kill);
                break;
            case ACTION_LAST_APP:
                value = res.getString(com.android.internal.R.string.action_lastapp);
                break;
            case ACTION_POWER:
                value = res.getString(com.android.internal.R.string.action_power);
                break;
            case ACTION_WIDGETS:
                value = res.getString(com.android.internal.R.string.action_widgets);
                break;
            case ACTION_APP_WINDOW:
                value = res.getString(com.android.internal.R.string.action_app_window);
                break;
            case ACTION_NOTIFICATIONS:
                value = res.getString(com.android.internal.R.string.action_notifications);
                break;
            case ACTION_ASSIST:
                value = res.getString(com.android.internal.R.string.action_assist);
                break;
            case ACTION_CLOCKOPTIONS:
                value = res.getString(com.android.internal.R.string.action_clockoptions);
                break;
            case ACTION_VOICEASSIST:
                value = res.getString(com.android.internal.R.string.action_voiceassist);
                break;
            case ACTION_TORCH:
                value = res.getString(com.android.internal.R.string.action_torch);
                break;
            case ACTION_SILENT:
                value = res.getString(com.android.internal.R.string.action_silent);
                break;
            case ACTION_VIB:
                value = res.getString(com.android.internal.R.string.action_vib);
                break;
            case ACTION_SILENT_VIB:
                value = res.getString(com.android.internal.R.string.action_silent_vib);
                break;
            case ACTION_EVENT:
                value = res.getString(com.android.internal.R.string.action_event);
                break;
            case ACTION_TODAY:
                value = res.getString(com.android.internal.R.string.action_today);
                break;
            case ACTION_ALARM:
                value = res.getString(com.android.internal.R.string.action_alarm);
                break;
            case ACTION_UNLOCK:
                value = res.getString(com.android.internal.R.string.action_unlock);
                break;
            case ACTION_CAMERA:
                value = res.getString(com.android.internal.R.string.action_camera);
                break;
            case ACTION_APP:
                value = res.getString(com.android.internal.R.string.action_app);
                break;
            case ACTION_NULL:
            default:
                value = res.getString(com.android.internal.R.string.action_null);
                break;

        }
        return value;
    }
    public static Drawable getActionIcon(Context context,String actionstring) {
        // Will return a Drawable for the associated action, but will need the caller's context to get resources.
        Resources res = context.getResources();
        Drawable value = null;
        AwesomeConstant action = fromString(actionstring);
        switch (action) {
            case ACTION_HOME :
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_home");
                break;
            case ACTION_BACK:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_back");
                break;
            case ACTION_RECENTS:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_recent");
                break;
            case ACTION_RECENTS_GB:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_recent_gb");
                break;
            case ACTION_SEARCH:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_search");
                break;
            /*case ACTION_SCREENSHOT:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_screenshot");
                break;*/
            case ACTION_MENU:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_menu_big");
                break;
            case ACTION_IME:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_ime_switcher");
                break;
            case ACTION_KILL:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_killtask");
                break;
            case ACTION_LAST_APP:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_lastapp");
                break;
            case ACTION_POWER:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_power");
                break;
            case ACTION_WIDGETS:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_widget");
                break;
            case ACTION_APP_WINDOW:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_widget");
                break;
            case ACTION_NOTIFICATIONS:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_notifications");
                break;
            case ACTION_ASSIST:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_assist");
                break;
            case ACTION_CLOCKOPTIONS:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_clockoptions");
                break;
            case ACTION_VOICEASSIST:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_voiceassist");
                break;
            case ACTION_TORCH:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_torch");
                break;
            case ACTION_SILENT:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_silent");
                break;
            case ACTION_VIB:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_vib");
                break;
            case ACTION_SILENT_VIB:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_silent_vib");
                break;
            case ACTION_EVENT:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_event");
                break;
            case ACTION_TODAY:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_today");
                break;
            case ACTION_ALARM:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_alarm");
                break;
            case ACTION_UNLOCK:
                value = res.getDrawable(com.android.internal.R.drawable.ic_lockscreen_unlock);
                break;
            case ACTION_CAMERA:
                value = res.getDrawable(com.android.internal.R.drawable.ic_lockscreen_camera);
                break;
            case ACTION_APP: // APP doesn't really have an icon - it should look up
                        //the package icon - we'll return the 'null' on just in case
            case ACTION_NULL:
            default:
                value = getSystemUIDrawable(context, "com.android.systemui:drawable/ic_sysbar_null");
                break;

        }
        return value;
    }
}
