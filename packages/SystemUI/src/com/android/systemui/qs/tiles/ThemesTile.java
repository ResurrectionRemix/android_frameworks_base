/*
 * Copyright (C) 2015 CyanideL
 * Copyright (C) 2016 Dirty Unicorns
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

package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ThemeConfig;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSTile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cyanogenmod.app.StatusBarPanelCustomTile;
import cyanogenmod.providers.ThemesContract;
import cyanogenmod.themes.ThemeChangeRequest;
import cyanogenmod.themes.ThemeManager;

import org.cyanogenmod.internal.logging.CMMetricsLogger;

/**
 * Quick settings tile: Themes mode
 **/
public class ThemesTile extends QSTile<QSTile.BooleanState> implements ThemeManager.ThemeChangeListener {

    //HAX: CMTE doesn't call "handleDestroy()" so we must find a way to remove old tile
    private static int i = 0;
    private final int localI;

    private enum Mode {ALL_THEMES, ICON_PACK, APP_THEME}

    private static final String CATEGORY_THEME_CHOOSER = "cyanogenmod.intent.category.APP_THEMES";
    private final ThemesDetailAdapter mDetailAdapter;
    private ThemeManager mService;
    private Mode mode;

    public ThemesTile(Host host) {
        super(host);
        mDetailAdapter = new ThemesDetailAdapter();
        mService = ThemeManager.getInstance(getHost().getContext());
        mState.value = true;
        mService.registerThemeChangeListener(this);
        // Log.d("ThemesTile", "new");
        i++;
        localI = i;
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected void handleDestroy() {
        // Log.d("ThemesTile", "destroy");
        super.handleDestroy();
        mService.unregisterThemeChangeListener(this);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected void handleClick() {
        mode = Mode.ALL_THEMES;
        showDetail(true);
    }

    @Override
    protected void handleLongClick() {
        if (isTopActivityLauncher()) {
            mode = Mode.ICON_PACK;
        } else {
            mode = Mode.APP_THEME;
        }

        showDetail(true);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_themes);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_themes_on);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.DISPLAY;
    }

    @Override
    public void onProgress(int progress) {

    }

    @Override
    public void onFinish(boolean isSuccess) {

        if (localI != i) {
            mService.unregisterProcessingListener(this);
            return;
        }

        // Log.d("ThemesTile", "onFinish");

        if (mode == Mode.APP_THEME) {
            showDetail(false);
        }
    }

    private final class ThemesDetailAdapter implements DetailAdapter, QSDetailItems.Callback {

        private QSDetailItems mItems;
        private Item[] items;
        private int firstItemId;

        private final int mMaxItems = getHost().getContext().getResources().getInteger(
                R.integer.quick_settings_detail_max_item_count);

        @Override
        public int getTitle() {
            switch (mode) {
                case ALL_THEMES:
                    return R.string.quick_settings_themes_system_theme;
                case ICON_PACK:
                    return R.string.quick_settings_themes_icon_packs;
                case APP_THEME:
                    return R.string.quick_settings_themes_app_theme;
                default:
                    return -1;
            }
        }

        @Override
        public void onDetailItemClick(QSDetailItems.Item item) {

            String pkg = (String) item.tag;

            // Log.d("ThemesTile applying", pkg);

            ThemeChangeRequest.Builder builder = new ThemeChangeRequest.Builder();

            if (mode == Mode.ALL_THEMES) {
                builder.setStatusBar(pkg);
                builder.setOverlay(pkg);
                builder.setNavBar(pkg);
            } else if (mode == Mode.ICON_PACK) {
                builder.setIcons(pkg);
            } else if (mode == Mode.APP_THEME) {
                //When we set to "default" we have to reapply global theme for some reason
                builder.setOverlay(getCurrentTheme());
                builder.setAppOverlay(getTopApp(), pkg);
            }

            mService.requestThemeChange(builder.build(), false);

        }

        @Override
        public void onDetailItemDisconnect(QSDetailItems.Item item) {

        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItems = QSDetailItems.convertOrInflate(context, convertView, parent);
            mItems.setTagSuffix("Themes");
            mItems.setCallback(this);
            mItems.setMinHeightInItems(0);
            updateItems();
            setItemsVisible();
            return mItems;
        }

        public void setItemsVisible() {
            if (mItems == null) return;
            mItems.setItemsVisible(true);
        }

        private void updateItems() {

            if (mItems == null) return;

            if (isTopActivityLauncher()) {
                // Log.d("ThemesTile", "This is launcher");
            }

            switch (mode) {
                case ALL_THEMES:
                    // Log.d("ThemesTile", "Showing themes");
                    items = getAllThemes();
                    break;
                case APP_THEME:
                    // Log.d("ThemesTile", "Showing themes for " + getTopApp());
                    items = getAllThemesForApp(getTopApp());
                    break;
                case ICON_PACK:
                    // Log.d("ThemesTile", "Showing icon packs");
                    items = getAllIconPacks();
                    break;
                default:
                    throw new RuntimeException();
            }

            mItems.setItems(items);

            firstItemId = 0;

        }

        @Override
        public Intent getSettingsIntent() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(CATEGORY_THEME_CHOOSER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return intent;
        }


        @Override
        public void setToggleState(boolean state) {

            if (firstItemId + mMaxItems >= items.length) {
                //We've show all items - move to beginning
                firstItemId = 0;
                mItems.setItems(items);
                return;
            }

            //Aka new first item
            int newFirstItemId = firstItemId + mMaxItems;
            int newLastItemId = Math.min(items.length, newFirstItemId + mMaxItems);

            firstItemId = newFirstItemId;

            mItems.setItems(Arrays.copyOfRange(items, newFirstItemId, newLastItemId));
        }

        @Override
        public int getMetricsCategory() {
            return MetricsLogger.DISPLAY;
        }

        private String getCurrentIconPack() {

            Cursor c = getHost().getContext().getContentResolver()
                    .query(ThemesContract.MixnMatchColumns.CONTENT_URI, null, null, null, null);

            String theme = null;

            while (c.moveToNext()) {

                String mixnmatchkey = c.getString(c.getColumnIndex(ThemesContract.MixnMatchColumns.COL_KEY));
                String pkg = c.getString(c.getColumnIndex(ThemesContract.MixnMatchColumns.COL_VALUE));

                if (mixnmatchkey.equals(ThemesContract.MixnMatchColumns.KEY_ICONS)) {
                    theme = pkg;
                    break;
                }

            }
            c.close();

            assert theme != null;

            return theme;

        }

        private String getCurrentTheme(String app) {
            ThemeConfig themeConfig = getHost().getContext().getResources().getConfiguration().themeConfig;

            if (themeConfig != null && themeConfig.getAppThemes().get(app) != null) {
                return themeConfig.getAppThemes().get(app).getOverlayPkgName();
            } else {
                return "default";
            }
        }

        private String getCurrentTheme() {

            Cursor c = getHost().getContext().getContentResolver()
                    .query(ThemesContract.MixnMatchColumns.CONTENT_URI, null, null, null, null);

            String theme = null;

            while (c.moveToNext()) {

                String mixnmatchkey = c.getString(c.getColumnIndex(ThemesContract.MixnMatchColumns.COL_KEY));
                String pkg = c.getString(c.getColumnIndex(ThemesContract.MixnMatchColumns.COL_VALUE));

                if (mixnmatchkey.equals(ThemesContract.MixnMatchColumns.KEY_OVERLAYS)) {
                    theme = pkg;
                    break;
                }

            }
            c.close();

            assert theme != null;

            return theme;

        }

        private Item[] getAllThemesForApp(String app) {

            String currentThemePkg = getCurrentTheme(app);

            String filter = ThemesContract.ThemesColumns.MODIFIES_OVERLAYS + "=1";

            List<Item> itemList = getAllFilteredPackages(filter);

            Item defaultItem = new Item();
            defaultItem.line1 = getHost().getContext().getString(R.string.quick_settings_themes_default_theme);
            defaultItem.line2 = getHost().getContext().getString(R.string.quick_settings_themes_match_system);
            defaultItem.tag = "default";
            defaultItem.icon = R.drawable.ic_qs_themes_off;

            itemList.add(0, defaultItem);

            for (int i1 = 0; i1 < itemList.size(); i1++) {
                Item item = itemList.get(i1);

                if (item.tag.equals(currentThemePkg)) {
                    item.icon = R.drawable.ic_qs_themes_on;
                    itemList.remove(i1);
                    itemList.add(0, item);
                    break;
                }

            }

            return itemList.toArray(new Item[itemList.size()]);

        }

        private Item[] getAllThemes() {

            String currentThemePkg = getCurrentTheme();

            String filter = ThemesContract.ThemesColumns.MODIFIES_OVERLAYS + "=1 AND "
                    + ThemesContract.ThemesColumns.MODIFIES_STATUS_BAR + "=1 AND "
                    + ThemesContract.ThemesColumns.MODIFIES_NAVIGATION_BAR + "=1";

            List<Item> itemList = getAllFilteredPackages(filter);

            for (int i1 = 0; i1 < itemList.size(); i1++) {
                Item item = itemList.get(i1);

                if (item.tag.equals(currentThemePkg)) {
                    item.icon = R.drawable.ic_qs_themes_on;
                    itemList.remove(i1);
                    itemList.add(0, item);
                    break;
                }

            }

            return itemList.toArray(new Item[itemList.size()]);

        }

        private Item[] getAllIconPacks() {

            String currentThemePkg = getCurrentIconPack();

            String filter = ThemesContract.ThemesColumns.MODIFIES_ICONS + "=1 OR "
                    + ThemesContract.ThemesColumns.IS_LEGACY_ICONPACK + "=1";

            List<Item> itemList = getAllFilteredPackages(filter);

            for (int i1 = 0; i1 < itemList.size(); i1++) {
                Item item = itemList.get(i1);

                if (item.tag.equals(currentThemePkg)) {
                    item.icon = R.drawable.ic_qs_themes_on;
                    itemList.remove(i1);
                    itemList.add(0, item);
                    break;
                }

            }

            return itemList.toArray(new Item[itemList.size()]);

        }

        private List<Item> getAllFilteredPackages(String filter) {

            List<Item> itemList = new ArrayList<>();

            // sort in ascending order but make sure the "default" theme is always first
            String sortOrder = "(" + ThemesContract.ThemesColumns.IS_DEFAULT_THEME + "=1) DESC, "
                    + ThemesContract.ThemesColumns.TITLE + " ASC";

            Cursor c = getHost().getContext().getContentResolver()
                    .query(ThemesContract.ThemesColumns.CONTENT_URI, null, filter, null, sortOrder);

            while (c.moveToNext()) {

                // Log.d("ThemesTile adding", c.getString(c.getColumnIndex(ThemesContract.ThemesColumns.TITLE)));

                Item item = new Item();
                item.line1 = c.getString(c.getColumnIndex(ThemesContract.ThemesColumns.TITLE));
                item.line2 = c.getString(c.getColumnIndex(ThemesContract.ThemesColumns.AUTHOR));
                item.tag = c.getString(c.getColumnIndex(ThemesContract.ThemesColumns.PKG_NAME));
                item.icon = R.drawable.ic_qs_themes_off;

                itemList.add(item);

            }
            c.close();

            return itemList;

        }

    }


    private boolean isTopActivityLauncher() {
        return isActivityLauncher(getTopActivity());
    }

    private ComponentName getTopActivity() {
        Context context = getHost().getContext();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        return tasks.get(0).topActivity;
    }

    private String getTopApp() {
        return getTopActivity().getPackageName();
    }

    private boolean isActivityLauncher(ComponentName componentName) {
        Context context = getHost().getContext();

        final PackageManager pm = context.getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_HOME);

        List<ResolveInfo> appList = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo app : appList) {
            // Log.d("Activity checking:", componentName.getClassName() + " vs " + app.activityInfo.name);
            if (componentName.getClassName().equals(app.activityInfo.name)) {
                return true;
            }
        }

        return false;
    }

}
