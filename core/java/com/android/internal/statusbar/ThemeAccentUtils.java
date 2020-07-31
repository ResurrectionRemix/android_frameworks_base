/*
 * Copyright (C) 2018-2020 crDroid Android Project
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

package com.android.internal.statusbar;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.util.Log;

public class ThemeAccentUtils {

    public static final String TAG = "ThemeAccentUtils";
    public static final String[] STOCK = {
            "com.android.theme.stock.system",
    };

    // Accents
    private static final String[] ACCENTS = {
        "default_accent", // 0
        "com.accents.red", // 1
        "com.accents.pink", // 2
        "com.accents.purple", // 3
        "com.accents.deeppurple", // 4
        "com.accents.indigo", // 5
        "com.accents.blue", // 6
        "com.accents.lightblue", // 7
        "com.accents.cyan", // 8
        "com.accents.teal", // 9
        "com.accents.green", // 10
        "com.accents.lightgreen", // 11
        "com.accents.lime", // 12
        "com.accents.yellow", // 13
        "com.accents.amber", // 14
        "com.accents.orange", // 15
        "com.accents.deeporange", // 16
        "com.accents.brown", // 17
        "com.accents.grey", // 18
        "com.accents.bluegrey", // 19
        "com.accents.black", // 20
        "com.accents.white", // 21
        "com.accents.userone", // 22
        "com.accents.usertwo", // 23
        "com.accents.userthree", // 24
        "com.accents.userfour", // 25
        "com.accents.userfive", // 26
        "com.accents.usersix", // 27
        "com.accents.userseven", // 28
        "com.accents.aospagreen", // 29
        "com.accents.androidonegreen", // 30
        "com.accents.cocacolared", // 31
        "com.accents.discordpurple", // 32
        "com.accents.facebookblue", // 33
        "com.accents.instagramcerise", // 34
        "com.accents.jollibeecrimson", // 35
        "com.accents.monsterenergygreen", // 36
        "com.accents.nextbitmint", // 37
        "com.accents.oneplusred", // 38
        "com.accents.pepsiblue", // 39
        "com.accents.pocophoneyellow", // 40
        "com.accents.razergreen", // 41
        "com.accents.samsungblue", // 42
        "com.accents.spotifygreen", // 43
        "com.accents.starbucksgreen", // 44
        "com.accents.twitchpurple", // 45
        "com.accents.twitterblue", // 46
        "com.accents.xboxgreen", // 47
        "com.accents.xiaomiorange", // 48
    };

    private static final String[] QS_TILE_THEMES = {
        "com.android.systemui.qstile.default", // 0
        "com.android.systemui.qstile.circletrim", // 1
        "com.android.systemui.qstile.dualtonecircletrim", // 2
        "com.android.systemui.qstile.squircletrim", // 3
        "com.android.systemui.qstile.wavey", // 4
        "com.android.systemui.qstile.pokesign", // 5
        "com.android.systemui.qstile.ninja", // 6
        "com.android.systemui.qstile.dottedcircle", // 7
        "com.android.systemui.qstile.attemptmountain", // 8
        "com.android.systemui.qstile.squaremedo", // 9
        "com.android.systemui.qstile.inkdrop", // 10
        "com.android.systemui.qstile.cookie", // 11
        "com.android.systemui.qstile.circleoutline", //12
        "com.android.systemui.qstile.neonlike", // 13
        "com.android.systemui.qstile.oos", // 14
        "com.android.systemui.qstile.triangles", // 15
        "com.android.systemui.qstile.divided", // 16
        "com.android.systemui.qstile.cosmos", // 17
        "com.android.systemui.qstile.squircle", // 18
        "com.android.systemui.qstile.teardrop", // 19
        "com.android.systemui.qstile.square", // 20
        "com.android.systemui.qstile.hexagon", // 21
        "com.android.systemui.qstile.diamond", // 22
        "com.android.systemui.qstile.star", // 23
        "com.android.systemui.qstile.gear", // 24
        "com.android.systemui.qstile.badge", // 25
        "com.android.systemui.qstile.badgetwo", // 26
    };

    // Dark Variants
    public static final String[] PRIMARY_THEMES = {
        "com.android.theme.color.primary.ocean", 
        "com.android.theme.color.primary.nature", 
        "com.android.theme.color.primary.gray", 
        "com.android.theme.color.primary.flame", 
        "com.android.theme.color.primary.charcoal", 
        "com.android.theme.color.primary.omniblack",
        "com.android.theme.color.primary.darkblue",
    }

    public static final String[] DARK_THEMES = {
        "com.android.system.theme.charcoalblack", 
    };

    // Dark Variants
    public static final String[] PRIMARY_THEMES = {
        "com.android.theme.color.primary.ocean", 
        "com.android.theme.color.primary.nature", 
        "com.android.theme.color.primary.gray", 
        "com.android.theme.color.primary.flame", 
        "com.android.theme.color.primary.charcoal", 
        "com.android.theme.color.primary.omniblack",
        "com.android.theme.color.primary.darkblue",
    };

    // Switch themes
    private static final String[] SWITCH_STYLES = {
        "com.android.system.switch.stock", // 0
        "com.android.system.switch.md2", // 1
        "com.android.system.switch.oneplus", // 2
        "com.android.system.switch.narrow", // 3
        "com.android.system.switch.contained", // 4
        "com.android.system.switch.retro", // 5
        "com.android.system.switch.telegram", // 6
    };

    public static final String[] SOLARIZED_DARK = {
            "com.android.theme.solarizeddark.system",
            "com.android.theme.solarizeddark.systemui",
    };

    public static final String[] BAKED_GREEN = {
            "com.android.theme.bakedgreen.system",
            "com.android.theme.bakedgreen.systemui",
    };

    public static final String[] CHOCO_X = {
            "com.android.theme.chocox.system",
            "com.android.theme.chocox.systemui",
    };

    public static final String[] PITCH_BLACK = {
            "com.android.theme.pitchblack.system",
            "com.android.theme.pitchblack.systemui",
    };

    public static final String[] DARK_GREY = {
            "com.android.theme.darkgrey.system",
            "com.android.theme.darkgrey.systemui",
    };
    public static final String[] MATERIAL_OCEAN = {
            "com.android.theme.materialocean.system",
            "com.android.theme.materialocean.systemui",
    };

    public static final String[] XTENDED_CLEAR = {
            "com.android.theme.xtendedclear.system",
            "com.android.theme.xtendedclear.systemui",
    };

    // QS header themes
    private static final String[] QS_HEADER_THEMES = {
        "com.android.systemui.qsheader.black", // 0
        "com.android.systemui.qsheader.grey", // 1
        "com.android.systemui.qsheader.lightgrey", // 2
        "com.android.systemui.qsheader.accent", // 3
        "com.android.systemui.qsheader.transparent", // 4
    };

    // Check for the dark system theme
    public static int getDarkStyle(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;

        for (int darkStyle = 0; darkStyle < DARK_THEMES.length; darkStyle++) {
            String darktheme = DARK_THEMES[darkStyle];
            try {
                themeInfo = om.getOverlayInfo(darktheme, userId);
                if (themeInfo != null && themeInfo.isEnabled())
                    return (darkStyle + 1);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    // Unloads the dark themes
    private static void unloadDarkTheme(IOverlayManager om, int userId) {
        for (String theme : DARK_THEMES) {
            try {
                om.setEnabled(theme, false, userId);
            } catch (RemoteException e) {
            }
        }
    }

    // Switches qs header style to user selected.
    public static void updateQSHeaderStyle(IOverlayManager om, int userId, int qsHeaderStyle) {
        if (qsHeaderStyle == 0) {
            stockQSHeaderStyle(om, userId);
        } else {
            try {
                om.setEnabled(QS_HEADER_THEMES[qsHeaderStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs header theme", e);
            }
        }
    }

    // Switches qs header style back to stock.
    public static void stockQSHeaderStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_HEADER_THEMES.length; i++) {
            String qsheadertheme = QS_HEADER_THEMES[i];
            try {
                om.setEnabled(qsheadertheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Set dark system theme
    public static void setSystemTheme(IOverlayManager om, int userId, boolean useDarkTheme, int darkStyle) {
         unloadDarkTheme(om, userId);

        // Ensure dark/black theme enabled if requested
        if (useDarkTheme && darkStyle > 0) {
            try {
                om.setEnabled(DARK_THEMES[darkStyle - 1], useDarkTheme, userId);
            } catch (RemoteException e) {
            }
        }

        // Check black/white accent proper usage
        checkBlackWhiteAccent(om, userId, useDarkTheme);
    }

    public static void checkBlackWhiteAccent(IOverlayManager om, int userId, boolean useDarkTheme) {
        OverlayInfo themeInfo = null;
        try {
            if (useDarkTheme) {
                themeInfo = om.getOverlayInfo(ACCENTS[20],
                        userId);
                if (themeInfo != null && themeInfo.isEnabled()) {
                    om.setEnabled(ACCENTS[20],
                            false /*disable*/, userId);
                    om.setEnabled(ACCENTS[21],
                            true, userId);
                }
            } else {
                themeInfo = om.getOverlayInfo(ACCENTS[21],
                        userId);
                if (themeInfo != null && themeInfo.isEnabled()) {
                    om.setEnabled(ACCENTS[21],
                            false /*disable*/, userId);
                    om.setEnabled(ACCENTS[20],
                            true, userId);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Unloads the switch styles
    private static void unloadSwitchStyle(IOverlayManager om, int userId) {
        for (String style : SWITCH_STYLES) {
            try {
                om.setEnabled(style, false, userId);
            } catch (RemoteException e) {
            }
        }
    }

    // Switches qs tile style to user selected.
    public static void updateNewTileStyle(IOverlayManager om, int userId, int qsTileStyle) {
        if (qsTileStyle == 0) {
            stockNewTileStyle(om, userId);
        } else {
            try {
                om.setEnabled(QS_TILE_THEMES[qsTileStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs tile style", e);
            }
        }
    }

    // Switches qs tile style back to stock.
    public static void stockNewTileStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_TILE_THEMES.length; i++) {
            String qstiletheme = QS_TILE_THEMES[i];
            try {
                om.setEnabled(qstiletheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Set switch style
    public static void setSwitchStyle(IOverlayManager om, int userId, int switchStyle) {
        // Always unload switch styles
        unloadSwitchStyle(om, userId);

        if (switchStyle == 0) return;

        try {
            om.setEnabled(SWITCH_STYLES[switchStyle], true, userId);
        } catch (RemoteException e) {
        }
    }
}
