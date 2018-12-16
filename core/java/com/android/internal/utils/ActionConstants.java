/*
 * Copyright (C) 2015 TeamEos project
 * Author Randall Rushing aka bigrushdog, randall.rushing@gmail.com
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
 *
 * ActionConstants.java: A helper class to assist Config.java with loading
 * and assigning default feature configurations. Nested classes implement
 * the static interface Defaults, which allows Settings and Config.java
 * to handle configurations in a non-implementation specific way, allowing
 * for more generalized code structures.
 *
 * Of strong importance is the ConfigMap pojo class. Current settings use
 * a ActionPreference which sets a single action. Therefore, we must have a
 * way to map individual actions to their associated buttons. ActionPreference
 * key MUST match the tag associated with the target ConfigMap.
 *
 */

package com.android.internal.utils;

import java.util.HashMap;
import java.util.Map;

import com.android.internal.utils.ActionHandler.SystemAction;
import com.android.internal.utils.Config.ActionConfig;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;

public class ActionConstants {
    public static interface Defaults {
        public int getConfigType();
        public String getUri();
        public String getDefaultConfig();
        public int getMaxButtons();
        public Map<String, ConfigMap> getActionMap();
        public Bundle getConfigs(Context context);
    }

    public static final String ACTION_DELIMITER = "|";
    public static final String EMPTY = "empty";
    public static final int SMARTBAR = 1;
    public static final int HWKEYS = 2;
    public static final int FLING = 3;
    public static final int PIE_PRIMARY = 4;
    public static final int PIE_SECONDARY = 5;

    private static final Smartbar smartbar = new Smartbar();
    private static final Hwkeys hwkeys = new Hwkeys();
    private static final Fling fling = new Fling();
    private static final PiePrimary pie_primary = new PiePrimary();
    private static final PieSecond pie_second = new PieSecond();

    public static Defaults getDefaults(int type) {
        if (type == SMARTBAR) {
            return smartbar;
        } else if (type == HWKEYS) {
            return hwkeys;
        } else if (type == FLING) {
            return fling;
        } else if (type == PIE_PRIMARY){
            return pie_primary;
        } else if (type == PIE_SECONDARY) {
            return pie_second;
        } else {
            return null;
        }
    }

    public static String dl(String s) {
        return s + ACTION_DELIMITER;
    }

    public static class Smartbar implements Defaults {
        public static final int SMARTBAR_MAX_BUTTONS = 10;
        public static final String SMARTBAR_DEF_BUTTONS = "3";
        public static final String BUTTON1_TAG = "smartbar_button_1";
        public static final String BUTTON2_TAG = "smartbar_button_2";
        public static final String BUTTON3_TAG = "smartbar_button_3";
        public static final String BUTTON4_TAG = "smartbar_button_4";
        public static final String BUTTON5_TAG = "smartbar_button_5";
        public static final String BUTTON6_TAG = "smartbar_button_6";
        public static final String BUTTON7_TAG = "smartbar_button_7";

        public static final String SMARTBAR_CONFIG_DEFAULT =
                dl(SMARTBAR_DEF_BUTTONS)                                                              // default number of ButtonConfig
              + dl(BUTTON1_TAG)                                                                     // button tag
              + dl(SystemAction.Back.mAction)       + dl(SystemAction.Back.mLabelRes)     + dl(EMPTY)  // single tap (PRIMARY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes)  + dl(EMPTY)  // long press (SECOND)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)  // double tap (THIRD)

              + dl(BUTTON2_TAG)
              + dl(SystemAction.Home.mAction)       + dl(SystemAction.Home.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.GoogleNowOnTap.mAction)   + dl(SystemAction.GoogleNowOnTap.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)

              + dl(BUTTON3_TAG)
              + dl(SystemAction.Overview.mAction)   + dl(SystemAction.Overview.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.SplitScreen.mAction)    + dl(SystemAction.SplitScreen.mLabelRes)  + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + EMPTY;
/*
              + dl(BUTTON4_TAG)
              + dl(SystemAction.ExpandedDesktop.mAction)       + dl(SystemAction.ExpandedDesktop.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.Flashlight.mAction)   + dl(SystemAction.Flashlight.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.PowerMenu.mAction)   + dl(SystemAction.PowerMenu.mLabelRes) + dl(EMPTY)

              + dl(BUTTON5_TAG)
              + dl(SystemAction.Screenrecord.mAction)       + dl(SystemAction.Screenrecord.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.KillApp.mAction)   + dl(SystemAction.KillApp.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.ScreenOff.mAction)   + dl(SystemAction.ScreenOff.mLabelRes) + EMPTY;
*/
        @Override
        public String getUri() {
            return "smartbar_button_config";
        }

        @Override
        public String getDefaultConfig() {
            return SMARTBAR_CONFIG_DEFAULT;
        }

        @Override
        public int getMaxButtons() {
            return SMARTBAR_MAX_BUTTONS;
        }

        @Override
        public int getConfigType() {
            return SMARTBAR;
        }

        @Override
        public Map<String, ConfigMap> getActionMap() {
            return null;
        }

        @Override
        public Bundle getConfigs(Context context) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public static class Hwkeys implements Defaults {
        public static final int HWKEY_MAX_BUTTONS = 7;
        public static final String HWKEY_DEF_BUTTONS = "5";
        public static final String BACK_BUTTON_TAG = "hwkeys_button_back";
        public static final String HOME_BUTTON_TAG = "hwkeys_button_home";
        public static final String OVERVIEW_BUTTON_TAG = "hwkeys_button_overview";
        public static final String MENU_BUTTON_TAG = "hwkeys_button_menu";
        public static final String ASSIST_BUTTON_TAG = "hwkeys_button_assist";
        public static final String EXTRA1_BUTTON_TAG = "hwkeys_button_camera";
        public static final String EXTRA2_BUTTON_TAG = "hwkeys_button_extra";

        public static final String BACK_BUTTON_SINGLE_TAP_TAG = "hwkeys_button_back_single_tap";
        public static final String HOME_BUTTON_SINGLE_TAP_TAG = "hwkeys_button_home_single_tap";
        public static final String OVERVIEW_BUTTON_SINGLE_TAP_TAG = "hwkeys_button_overview_single_tap";
        public static final String MENU_BUTTON_SINGLE_TAP_TAG = "hwkeys_button_menu_single_tap";
        public static final String ASSIST_BUTTON_SINGLE_TAP_TAG = "hwkeys_button_assist_single_tap";

        public static final String BACK_BUTTON_LONG_PRESS_TAG = "hwkeys_button_back_long_press";
        public static final String HOME_BUTTON_LONG_PRESS_TAG = "hwkeys_button_home_long_press";
        public static final String OVERVIEW_BUTTON_LONG_PRESS_TAG = "hwkeys_button_overview_long_press";
        public static final String MENU_BUTTON_LONG_PRESS_TAG = "hwkeys_button_menu_long_press";
        public static final String ASSIST_BUTTON_LONG_PRESS_TAG = "hwkeys_button_assist_long_press";

        public static final String BACK_BUTTON_DOUBLE_TAP_TAG = "hwkeys_button_back_double_tap";
        public static final String HOME_BUTTON_DOUBLE_TAP_TAG = "hwkeys_button_home_double_tap";
        public static final String OVERVIEW_BUTTON_DOUBLE_TAP_TAG = "hwkeys_button_overview_double_tap";
        public static final String MENU_BUTTON_DOUBLE_TAP_TAG = "hwkeys_button_menu_double_tap";
        public static final String ASSIST_BUTTON_DOUBLE_TAP_TAG = "hwkeys_button_assist_double_tap";

        private static final Map<String, ConfigMap> configMap = new HashMap<String, ConfigMap>();

        static {
            configMap.put(BACK_BUTTON_SINGLE_TAP_TAG, new ConfigMap(0, ActionConfig.PRIMARY));
            configMap.put(HOME_BUTTON_SINGLE_TAP_TAG, new ConfigMap(1, ActionConfig.PRIMARY));
            configMap.put(OVERVIEW_BUTTON_SINGLE_TAP_TAG, new ConfigMap(2, ActionConfig.PRIMARY));
            configMap.put(MENU_BUTTON_SINGLE_TAP_TAG, new ConfigMap(3, ActionConfig.PRIMARY));
            configMap.put(ASSIST_BUTTON_SINGLE_TAP_TAG, new ConfigMap(4, ActionConfig.PRIMARY));
            configMap.put(BACK_BUTTON_LONG_PRESS_TAG, new ConfigMap(0, ActionConfig.SECOND));
            configMap.put(HOME_BUTTON_LONG_PRESS_TAG, new ConfigMap(1, ActionConfig.SECOND));
            configMap.put(OVERVIEW_BUTTON_LONG_PRESS_TAG, new ConfigMap(2, ActionConfig.SECOND));
            configMap.put(MENU_BUTTON_LONG_PRESS_TAG, new ConfigMap(3, ActionConfig.SECOND));
            configMap.put(ASSIST_BUTTON_LONG_PRESS_TAG, new ConfigMap(4, ActionConfig.SECOND));
            configMap.put(BACK_BUTTON_DOUBLE_TAP_TAG, new ConfigMap(0, ActionConfig.THIRD));
            configMap.put(HOME_BUTTON_DOUBLE_TAP_TAG, new ConfigMap(1, ActionConfig.THIRD));
            configMap.put(OVERVIEW_BUTTON_DOUBLE_TAP_TAG, new ConfigMap(2, ActionConfig.THIRD));
            configMap.put(MENU_BUTTON_DOUBLE_TAP_TAG, new ConfigMap(3, ActionConfig.THIRD));
            configMap.put(ASSIST_BUTTON_DOUBLE_TAP_TAG, new ConfigMap(4, ActionConfig.THIRD));
        }

        public static final String HWKEYS_CONFIG_DEFAULT =
                dl(HWKEY_DEF_BUTTONS)
              + dl(BACK_BUTTON_TAG)
              + dl(SystemAction.Back.mAction)        + dl(SystemAction.Back.mLabelRes)         + dl(EMPTY)  // single tap (PRIMARY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)  // long press (SECOND)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)  // double tap (THIRD)

              + dl(HOME_BUTTON_TAG)
              + dl(SystemAction.Home.mAction)        + dl(SystemAction.Home.mLabelRes)         + dl(EMPTY)
              + dl(SystemAction.Assistant.mAction)   + dl(SystemAction.Assistant.mLabelRes)    + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)

              + dl(OVERVIEW_BUTTON_TAG)
              + dl(SystemAction.Overview.mAction)    + dl(SystemAction.Overview.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)

              + dl(MENU_BUTTON_TAG)
              + dl(SystemAction.Menu.mAction)        + dl(SystemAction.Menu.mLabelRes)         + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + dl(EMPTY)

              + dl(ASSIST_BUTTON_TAG)
              + dl(SystemAction.Assistant.mAction)   + dl(SystemAction.Assistant.mLabelRes)    + dl(EMPTY)
              + dl(SystemAction.VoiceSearch.mAction) + dl(SystemAction.VoiceSearch.mLabelRes)  + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)    + dl(SystemAction.NoAction.mLabelRes)     + EMPTY;

        @Override
        public int getConfigType() {
            return HWKEYS;
        }

        @Override
        public String getUri() {
            //return Settings.System.HWKEY_BUTTON_ACTIONS;
            return "hwkey_config";
        }

        @Override
        public String getDefaultConfig() {
            return HWKEYS_CONFIG_DEFAULT;
        }

        @Override
        public int getMaxButtons() {
            return HWKEY_MAX_BUTTONS;
        }

        @Override
        public Map<String, ConfigMap> getActionMap() {
            return configMap;
        }

        @Override
        public Bundle getConfigs(Context context) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public static class Fling implements Defaults {
        public static final int FLING_MAX_BUTTONS = 10;
        public static final String FLING_DEF_BUTTONS = "5";

        public static final String RIGHT_TAP_TAG = "fling_right_taps";
        public static final String LEFT_TAP_TAG = "fling_left_taps";
        public static final String RIGHT_FLING_TAG = "fling_right";
        public static final String LEFT_FLING = "fling_left";
        public static final String UP_FLING = "fling_up";

        public static final String SINGLE_LEFT_TAP_TAG = "single_left_tap";
        public static final String SINGLE_RIGHT_TAP_TAG = "single_right_tap";
        public static final String DOUBLE_LEFT_TAP_TAG = "double_left_tap";
        public static final String DOUBLE_RIGHT_TAP_TAG = "double_right_tap";
        public static final String LONG_LEFT_PRESS_TAG = "long_left_press";
        public static final String LONG_RIGHT_PRESS_TAG = "long_right_press";
        public static final String FLING_SHORT_LEFT_TAG = "fling_short_left";
        public static final String FLING_SHORT_RIGHT_TAG = "fling_short_right";
        public static final String FLING_LONG_LEFT_TAG = "fling_long_left";
        public static final String FLING_LONG_RIGHT_TAG = "fling_long_right";
        public static final String FLING_RIGHT_UP_TAG = "fling_right_up";
        public static final String FLING_LEFT_UP_TAG = "fling_left_up";
        public static final String CONFIG_fling_touchslop_increase_factor = "config_fling_touchslop_increase_factor";
        public static final String CONFIG_FlingLongSwipePortraitLeft = "config_FlingLongSwipePortraitLeft";
        public static final String CONFIG_FlingLongSwipePortraitRight = "config_FlingLongSwipePortraitRight";
        public static final String CONFIG_FlingLongSwipeLandscapeLeft = "config_FlingLongSwipeLandscapeLeft";
        public static final String CONFIG_FlingLongSwipeLandscapeRight = "config_FlingLongSwipeLandscapeRight";
        public static final String CONFIG_FlingLongSwipeVerticalUp = "config_FlingLongSwipeVerticalUp";
        public static final String CONFIG_FlingLongSwipeVerticalDown = "config_FlingLongSwipeVerticalDown";
        public static final String CONFIG_pulsePathEffect_1 = "config_pulsePathEffect_1";
        public static final String CONFIG_pulsePathEffect_2 = "config_pulsePathEffect_2";
        public static final String CONFIG_pulsePathStrokeWidth = "config_pulsePathStrokeWidth";
        public static final String CONFIG_pulseFillColor = "config_pulseFillColor";
        public static final String CONFIG_pulseDivisions = "config_pulseDivisions";
        public static final String CONFIG_pulseDbFuzzFactor = "config_pulseDbFuzzFactor";
        public static final String CONFIG_pulseDbFuzz = "config_pulseDbFuzz";

        private static final Map<String, ConfigHolder> defMap = new HashMap<String, ConfigHolder>();

        static {
            defMap.put(CONFIG_fling_touchslop_increase_factor, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_fling_touchslop_increase_factor,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_FlingLongSwipePortraitLeft, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_FlingLongSwipePortraitLeft,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_FlingLongSwipePortraitRight, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_FlingLongSwipePortraitRight,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_FlingLongSwipeLandscapeLeft, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_FlingLongSwipeLandscapeLeft,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_FlingLongSwipeLandscapeRight, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_FlingLongSwipeLandscapeRight,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_FlingLongSwipeVerticalUp, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_FlingLongSwipeVerticalUp,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_FlingLongSwipeVerticalDown, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_FlingLongSwipeVerticalDown,
                    ActionUtils.FORMAT_FLOAT, ActionUtils.DIMEN));
            defMap.put(CONFIG_pulsePathEffect_1, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulsePathEffect_1, ActionUtils.DIMEN_PIXEL));
            defMap.put(CONFIG_pulsePathEffect_2, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulsePathEffect_2, ActionUtils.DIMEN_PIXEL));
            defMap.put(CONFIG_pulsePathStrokeWidth, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulsePathStrokeWidth, ActionUtils.DIMEN_PIXEL));
            defMap.put(CONFIG_pulseFillColor, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulseFillColor, ActionUtils.COLOR));
            defMap.put(CONFIG_pulseDivisions, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulseDivisions, ActionUtils.INT));
            defMap.put(CONFIG_pulseDbFuzzFactor, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulseDbFuzzFactor, ActionUtils.INT));
            defMap.put(CONFIG_pulseDbFuzz, new ConfigHolder(
                    ActionUtils.PACKAGE_SYSTEMUI, CONFIG_pulseDbFuzz, ActionUtils.INT));
        }

        private static final Map<String, ConfigMap> configMap = new HashMap<String, ConfigMap>();

        static {
            configMap.put(SINGLE_LEFT_TAP_TAG, new ConfigMap(1, ActionConfig.PRIMARY));
            configMap.put(SINGLE_RIGHT_TAP_TAG, new ConfigMap(0, ActionConfig.PRIMARY));
            configMap.put(DOUBLE_LEFT_TAP_TAG, new ConfigMap(1, ActionConfig.THIRD));
            configMap.put(DOUBLE_RIGHT_TAP_TAG, new ConfigMap(0, ActionConfig.THIRD));
            configMap.put(LONG_LEFT_PRESS_TAG, new ConfigMap(1, ActionConfig.SECOND));
            configMap.put(LONG_RIGHT_PRESS_TAG, new ConfigMap(0, ActionConfig.SECOND));
            configMap.put(FLING_SHORT_LEFT_TAG, new ConfigMap(3, ActionConfig.PRIMARY));
            configMap.put(FLING_SHORT_RIGHT_TAG, new ConfigMap(2, ActionConfig.PRIMARY));
            configMap.put(FLING_LONG_LEFT_TAG, new ConfigMap(3, ActionConfig.SECOND));
            configMap.put(FLING_LONG_RIGHT_TAG, new ConfigMap(2, ActionConfig.SECOND));
            configMap.put(FLING_RIGHT_UP_TAG, new ConfigMap(4, ActionConfig.PRIMARY));
            configMap.put(FLING_LEFT_UP_TAG, new ConfigMap(4, ActionConfig.SECOND));
        }

        public static final String FLING_CONFIG_DEFAULT =
                dl(FLING_DEF_BUTTONS)
              + dl(RIGHT_TAP_TAG)
              + dl(SystemAction.Home.mAction)         + dl(SystemAction.Home.mLabelRes)        + dl(EMPTY)  // short tap
              + dl(SystemAction.GoogleNowOnTap.mAction)         + dl(SystemAction.GoogleNowOnTap.mLabelRes)        + dl(EMPTY)  // long press
              + dl(SystemAction.LastApp.mAction)     + dl(SystemAction.LastApp.mLabelRes)    + dl(EMPTY)  // double tap

              + dl(LEFT_TAP_TAG)
              + dl(SystemAction.Home.mAction)     + dl(SystemAction.Home.mLabelRes)    + dl(EMPTY)  // short tap
              + dl(SystemAction.GoogleNowOnTap.mAction)     + dl(SystemAction.GoogleNowOnTap.mLabelRes)    + dl(EMPTY)  // long press
              + dl(SystemAction.ScreenOff.mAction)     + dl(SystemAction.ScreenOff.mLabelRes)    + dl(EMPTY)  // double tap

              + dl(RIGHT_FLING_TAG)
              + dl(SystemAction.Overview.mAction)     + dl(SystemAction.Overview.mLabelRes)    + dl(EMPTY)  // short fling
              + dl(SystemAction.Assistant.mAction)    + dl(SystemAction.Assistant.mLabelRes)   + dl(EMPTY)  // long fling
              + dl(SystemAction.NoAction.mAction)     + dl(SystemAction.NoAction.mLabelRes)    + dl(EMPTY)  // super fling?

              + dl(LEFT_FLING)
              + dl(SystemAction.Back.mAction)         + dl(SystemAction.Back.mLabelRes)        + dl(EMPTY)  // short fling
              + dl(SystemAction.NoAction.mAction)     + dl(SystemAction.NoAction.mLabelRes)    + dl(EMPTY)  // long fling
              + dl(SystemAction.NoAction.mAction)     + dl(SystemAction.NoAction.mLabelRes)    + dl(EMPTY)  // super fling?

              + dl(UP_FLING)
              + dl(SystemAction.SplitScreen.mAction)     + dl(SystemAction.SplitScreen.mLabelRes)    + dl(EMPTY)  // right side (short fling only)
              + dl(SystemAction.SplitScreen.mAction)     + dl(SystemAction.SplitScreen.mLabelRes)    + dl(EMPTY)  // left side (short fling only)
              + dl(SystemAction.NoAction.mAction)     + dl(SystemAction.NoAction.mLabelRes)    + EMPTY;

        @Override
        public int getConfigType() {
            return FLING;
        }

        @Override
        public String getUri() {
            return Settings.Secure.FLING_GESTURE_ACTIONS;
        }

        @Override
        public String getDefaultConfig() {
            return FLING_CONFIG_DEFAULT;
        }

        @Override
        public int getMaxButtons() {
            return FLING_MAX_BUTTONS;
        }

        @Override
        public Map<String, ConfigMap> getActionMap() {
            return configMap;
        }

        @Override
        public Bundle getConfigs(Context context) {
            return loadConfigsFromMap(context, defMap);
        }
    }

    public static class PiePrimary implements Defaults {
        public static final int PIE_PRIMARY_MAX_BUTTONS = 3;
        public static final String PIE_PRIMARY_DEF_BUTTONS = "3";
        public static final String PIE_BACK_BUTTON_TAG = "pie_button_back";
        public static final String PIE_HOME_BUTTON_TAG = "pie_button_home";
        public static final String PIE_OVERVIEW_BUTTON_TAG = "pie_button_overview";

        public static final String PIE_BACK_BUTTON_SINGLE_TAP_TAG = "pie_button_back_single_tap";
        public static final String PIE_HOME_BUTTON_SINGLE_TAP_TAG = "pie_button_home_single_tap";
        public static final String PIE_OVERVIEW_BUTTON_SINGLE_TAP_TAG = "pie_button_overview_single_tap";

        public static final String PIE_BACK_BUTTON_LONG_PRESS_TAG = "pie_button_back_long_press";
        public static final String PIE_HOME_BUTTON_LONG_PRESS_TAG = "pie_button_home_long_press";
        public static final String PIE_OVERVIEW_BUTTON_LONG_PRESS_TAG = "pie_button_overview_long_press";

        private static final Map<String, ConfigMap> configMap = new HashMap<String, ConfigMap>();

        static {
            configMap.put(PIE_BACK_BUTTON_SINGLE_TAP_TAG, new ConfigMap(0, ActionConfig.PRIMARY));
            configMap.put(PIE_HOME_BUTTON_SINGLE_TAP_TAG, new ConfigMap(1, ActionConfig.PRIMARY));
            configMap.put(PIE_OVERVIEW_BUTTON_SINGLE_TAP_TAG, new ConfigMap(2, ActionConfig.PRIMARY));
            configMap.put(PIE_BACK_BUTTON_LONG_PRESS_TAG, new ConfigMap(0, ActionConfig.SECOND));
            configMap.put(PIE_HOME_BUTTON_LONG_PRESS_TAG, new ConfigMap(1, ActionConfig.SECOND));
            configMap.put(PIE_OVERVIEW_BUTTON_LONG_PRESS_TAG, new ConfigMap(2, ActionConfig.SECOND));
        }

        public static final String PIE_PRIMARY_CONFIG_DEFAULT =
                dl(PIE_PRIMARY_DEF_BUTTONS)
              + dl(PIE_BACK_BUTTON_TAG)
              + dl(SystemAction.Back.mAction)       + dl(SystemAction.Back.mLabelRes)     + dl(EMPTY)  // single tap (PRIMARY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)  // long press (SECOND)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)  // double tap (NO-OP on Pie)

              + dl(PIE_HOME_BUTTON_TAG)
              + dl(SystemAction.Home.mAction)       + dl(SystemAction.Home.mLabelRes)     + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)

              + dl(PIE_OVERVIEW_BUTTON_TAG)
              + dl(SystemAction.Overview.mAction)   + dl(SystemAction.Overview.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)   + dl(SystemAction.NoAction.mLabelRes) + EMPTY;  // don't delimit final string


        @Override
        public int getConfigType() {
            return PIE_PRIMARY;
        }

        @Override
        public String getUri() {
            //return Settings.System.PIE_BUTTONS_CONFIG;
            return "pie_primary_config";
        }

        @Override
        public String getDefaultConfig() {
            return PIE_PRIMARY_CONFIG_DEFAULT;
        }

        @Override
        public int getMaxButtons() {
            return PIE_PRIMARY_MAX_BUTTONS;
        }

        @Override
        public Map<String, ConfigMap> getActionMap() {
            return configMap;
        }

        @Override
        public Bundle getConfigs(Context context) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public static class PieSecond implements Defaults {
        public static final int PIE_SECOND_MAX_BUTTONS = 7;
        public static final String PIE_SECOND_DEF_BUTTONS = "5";
        public static final String PIE_BUTTON1_TAG = "pie_button_1";
        public static final String PIE_BUTTON2_TAG = "pie_button_2";
        public static final String PIE_BUTTON3_TAG = "pie_button_3";
        public static final String PIE_BUTTON4_TAG = "pie_button_4";
        public static final String PIE_BUTTON5_TAG = "pie_button_5";
        public static final String PIE_BUTTON6_TAG = "pie_button_6";
        public static final String PIE_BUTTON7_TAG = "pie_button_7";

        public static final String PIE_SECOND_BUTTON1_SINGLE_TAP_TAG = "pie_second_button_1_single_tap";
        public static final String PIE_SECOND_BUTTON2_SINGLE_TAP_TAG = "pie_second_button_2_single_tap";
        public static final String PIE_SECOND_BUTTON3_SINGLE_TAP_TAG = "pie_second_button_3_single_tap";
        public static final String PIE_SECOND_BUTTON4_SINGLE_TAP_TAG = "pie_second_button_4_single_tap";
        public static final String PIE_SECOND_BUTTON5_SINGLE_TAP_TAG = "pie_second_button_5_single_tap";
        public static final String PIE_SECOND_BUTTON6_SINGLE_TAP_TAG = "pie_second_button_6_single_tap";
        public static final String PIE_SECOND_BUTTON7_SINGLE_TAP_TAG = "pie_second_button_7_single_tap";

        public static final String PIE_SECOND_BUTTON1_LONG_PRESS_TAG = "pie_second_button_1_long_press";
        public static final String PIE_SECOND_BUTTON2_LONG_PRESS_TAG = "pie_second_button_2_long_press";
        public static final String PIE_SECOND_BUTTON3_LONG_PRESS_TAG = "pie_second_button_3_long_press";
        public static final String PIE_SECOND_BUTTON4_LONG_PRESS_TAG = "pie_second_button_4_long_press";
        public static final String PIE_SECOND_BUTTON5_LONG_PRESS_TAG = "pie_second_button_5_long_press";
        public static final String PIE_SECOND_BUTTON6_LONG_PRESS_TAG = "pie_second_button_6_long_press";
        public static final String PIE_SECOND_BUTTON7_LONG_PRESS_TAG = "pie_second_button_7_long_press";

        private static final Map<String, ConfigMap> configMap = new HashMap<String, ConfigMap>();

        static {
            configMap.put(PIE_SECOND_BUTTON1_SINGLE_TAP_TAG, new ConfigMap(0, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON2_SINGLE_TAP_TAG, new ConfigMap(1, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON3_SINGLE_TAP_TAG, new ConfigMap(2, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON4_SINGLE_TAP_TAG, new ConfigMap(3, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON5_SINGLE_TAP_TAG, new ConfigMap(4, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON6_SINGLE_TAP_TAG, new ConfigMap(5, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON7_SINGLE_TAP_TAG, new ConfigMap(6, ActionConfig.PRIMARY));
            configMap.put(PIE_SECOND_BUTTON1_LONG_PRESS_TAG, new ConfigMap(0, ActionConfig.SECOND));
            configMap.put(PIE_SECOND_BUTTON2_LONG_PRESS_TAG, new ConfigMap(1, ActionConfig.SECOND));
            configMap.put(PIE_SECOND_BUTTON3_LONG_PRESS_TAG, new ConfigMap(2, ActionConfig.SECOND));
            configMap.put(PIE_SECOND_BUTTON4_LONG_PRESS_TAG, new ConfigMap(3, ActionConfig.SECOND));
            configMap.put(PIE_SECOND_BUTTON5_LONG_PRESS_TAG, new ConfigMap(4, ActionConfig.SECOND));
            configMap.put(PIE_SECOND_BUTTON6_LONG_PRESS_TAG, new ConfigMap(5, ActionConfig.SECOND));
            configMap.put(PIE_SECOND_BUTTON7_LONG_PRESS_TAG, new ConfigMap(6, ActionConfig.SECOND));
        }

        public static final String PIE_SECOND_CONFIG_DEFAULT =
                dl(PIE_SECOND_DEF_BUTTONS)
              + dl(PIE_BUTTON1_TAG)
              + dl(SystemAction.PowerMenu.mAction)          + dl(SystemAction.PowerMenu.mLabelRes)           + dl(EMPTY)  // single tap (PRIMARY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)  // long press (SECOND)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)  // double tap (NO-OP on Pie)

              + dl(PIE_BUTTON2_TAG)
              + dl(SystemAction.NotificationPanel.mAction)  + dl(SystemAction.NotificationPanel.mLabelRes)   + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)

              + dl(PIE_BUTTON3_TAG)
              + dl(SystemAction.Assistant.mAction)          + dl(SystemAction.Assistant.mLabelRes)           + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)

              + dl(PIE_BUTTON4_TAG)
              + dl(SystemAction.Screenshot.mAction)         + dl(SystemAction.Screenshot.mLabelRes)          + dl(EMPTY)
              + dl(SystemAction.RegionScreenshot.mAction)  + dl(SystemAction.RegionScreenshot.mLabelRes)  + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)

              + dl(PIE_BUTTON5_TAG)
              + dl(SystemAction.LastApp.mAction)            + dl(SystemAction.LastApp.mLabelRes)             + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + dl(EMPTY)
              + dl(SystemAction.NoAction.mAction)           + dl(SystemAction.NoAction.mLabelRes)            + EMPTY;

        @Override
        public int getConfigType() {
            return PIE_SECONDARY;
        }

        @Override
        public String getUri() {
            //return Settings.System.PIE_BUTTONS_CONFIG_SECOND_LAYER;
            return "pie_second_config";
        }

        @Override
        public String getDefaultConfig() {
            return PIE_SECOND_CONFIG_DEFAULT;
        }

        @Override
        public int getMaxButtons() {
            return PIE_SECOND_MAX_BUTTONS;
        }

        @Override
        public Map<String, ConfigMap> getActionMap() {
            return configMap;
        }

        @Override
        public Bundle getConfigs(Context context) {
            // TODO Auto-generated method stub
            return null;
        }

    }

    private static Bundle loadConfigsFromMap(Context ctx, Map<String, ConfigHolder> configMap) {
        Bundle b = new Bundle();
        for (Map.Entry<String, ConfigHolder> entry : configMap.entrySet()) {
            ConfigHolder holder = entry.getValue();
            Object obj = ActionUtils.getValue(ctx, holder.name, holder.type, holder.format,
                    holder.pkg);
            ActionUtils.putValue(holder.name, obj, holder.type, b);
        }
        return b;
    }

    private static class ConfigHolder {
        public String pkg;
        public String name;
        public String format;
        public String type;

        public ConfigHolder(String pkg, String name, String type) {
            this(pkg, name, null, type);
        }

        public ConfigHolder(String pkg, String name, String format, String type) {
            this.pkg = pkg;
            this.name = name;
            this.format = format;
            this.type = type;
        }
    }

    public static class ConfigMap {
        public int button = -1;
        public int action = -1;

        public ConfigMap() {
        };

        public ConfigMap(int button, int action) {
            this.button = button;
            this.action = action;
        }
    }

    public static final int PIE_PRIMARY_MAX_BUTTONS = 5;
    public static final int PIE_SECONDAY_MAX_BUTTONS = 7;

}
