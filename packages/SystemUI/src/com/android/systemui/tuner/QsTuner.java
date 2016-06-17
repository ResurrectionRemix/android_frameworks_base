/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * See the License for the mSpecific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.tuner;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host.Callback;
import com.android.systemui.qs.QSTile.ResourceIcon;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.policy.SecurityController;

import java.util.ArrayList;
import java.util.List;

public class QsTuner extends Fragment implements Callback {

    private static final String TAG = "QsTuner";

    private static final int MENU_RESET = Menu.FIRST;

    private DraggableQsPanel mQsPanel;
    private CustomHost mTileHost;

    private FrameLayout mDropTarget;

    private ScrollView mScrollRoot;

    private FrameLayout mAddTarget;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, com.android.internal.R.string.reset);
    }

    public void onResume() {
        super.onResume();
        MetricsLogger.visibility(getContext(), MetricsLogger.TUNER_QS, true);
    }

    public void onPause() {
        super.onPause();
        MetricsLogger.visibility(getContext(), MetricsLogger.TUNER_QS, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                mTileHost.reset();
                break;
            case android.R.id.home:
                getFragmentManager().popBackStack();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mScrollRoot = (ScrollView) inflater.inflate(R.layout.tuner_qs, container, false);

        mQsPanel = new DraggableQsPanel(getContext());
        mTileHost = new CustomHost(getContext());
        mTileHost.setCallback(this);
        mQsPanel.setTiles(mTileHost.getTiles());
        mQsPanel.setHost(mTileHost);
        mQsPanel.refreshAllTiles();
        ((ViewGroup) mScrollRoot.findViewById(R.id.all_details)).addView(mQsPanel, 0);

        mDropTarget = (FrameLayout) mScrollRoot.findViewById(R.id.remove_target);
        setupDropTarget();
        mAddTarget = (FrameLayout) mScrollRoot.findViewById(R.id.add_target);
        setupAddTarget();
        mQsPanel.updateResources();
        return mScrollRoot;
    }

    @Override
    public void onDestroyView() {
        mTileHost.destroy();
        super.onDestroyView();
    }

    private void setupDropTarget() {
        QSTileView tileView = new QSTileView(getContext());
        QSTile.State state = new QSTile.State();
        state.visible = true;
        state.icon = ResourceIcon.get(R.drawable.ic_delete);
        state.label = getString(com.android.internal.R.string.delete);
        tileView.onStateChanged(state);
        mDropTarget.addView(tileView);
        mDropTarget.setVisibility(View.GONE);
        new DragHelper(tileView, new DropListener() {
            @Override
            public void onDrop(String sourceText) {
                mTileHost.remove(sourceText);
                mQsPanel.refreshAllTiles();
                mQsPanel.updateResources();
            }
        });
    }

    private void setupAddTarget() {
        QSTileView tileView = new QSTileView(getContext());
        QSTile.State state = new QSTile.State();
        state.visible = true;
        state.icon = ResourceIcon.get(R.drawable.ic_add_circle_qs);
        state.label = getString(R.string.add_tile);
        tileView.onStateChanged(state);
        mAddTarget.addView(tileView);
        tileView.setClickable(true);
        tileView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTileHost.showAddDialog();
                mQsPanel.updateResources();
            }
        });
    }

    public void onStartDrag() {
        mDropTarget.post(new Runnable() {
            @Override
            public void run() {
                mDropTarget.setVisibility(View.VISIBLE);
                mAddTarget.setVisibility(View.GONE);
            }
        });
    }

    public void stopDrag() {
        mDropTarget.post(new Runnable() {
            @Override
            public void run() {
                mDropTarget.setVisibility(View.GONE);
                mAddTarget.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onTilesChanged() {
        mQsPanel.setTiles(mTileHost.getTiles());
        mQsPanel.refreshAllTiles();
        mQsPanel.updateResources();
    }

    public static int getLabelResource(String mSpec) {
        if (mSpec.equals("wifi")) return R.string.quick_settings_wifi_label;
        else if (mSpec.equals("bt")) return R.string.quick_settings_bluetooth_label;
        else if (mSpec.equals("inversion")) return R.string.quick_settings_inversion_label;
        else if (mSpec.equals("cell")) return R.string.quick_settings_cellular_detail_title;
        else if (mSpec.equals("airplane")) return R.string.airplane_mode;
        else if (mSpec.equals("dnd")) return R.string.quick_settings_dnd_label;
        else if (mSpec.equals("rotation")) return R.string.quick_settings_rotation_locked_label;
        else if (mSpec.equals("flashlight")) return R.string.quick_settings_flashlight_label;
        else if (mSpec.equals("location")) return R.string.quick_settings_location_label;
        else if (mSpec.equals("cast")) return R.string.quick_settings_cast_title;
        else if (mSpec.equals("hotspot")) return R.string.quick_settings_hotspot_label;
        else if (mSpec.equals("adb_network")) return R.string.quick_settings_network_adb_label;
        else if (mSpec.equals("compass")) return R.string.quick_settings_compass_label;
        else if (mSpec.equals("nfc")) return R.string.quick_settings_nfc_label;
        else if (mSpec.equals("profiles")) return R.string.quick_settings_profiles;
        else if (mSpec.equals("sync")) return R.string.quick_settings_sync_label;
        else if (mSpec.equals("volume_panel")) return R.string.quick_settings_volume_panel_label;
        else if (mSpec.equals("usb_tether")) return R.string.quick_settings_usb_tether_label;
        else if (mSpec.equals("screen_timeout")) return R.string.quick_settings_screen_timeout_detail_title;
        else if (mSpec.equals("performance")) return R.string.qs_tile_performance;
        else if (mSpec.equals("ambient_display")) return R.string.quick_settings_ambient_display_label;
        else if (mSpec.equals("live_display")) return R.string.live_display_title;
        else if (mSpec.equals("music")) return R.string.quick_settings_music_label;
        else if (mSpec.equals("brightness")) return R.string.quick_settings_brightness_label;
        else if (mSpec.equals("screen_off")) return R.string.quick_settings_screen_off_label;
        else if (mSpec.equals("screenshot")) return R.string.quick_settings_screenshot_label;
        else if (mSpec.equals("expanded_desktop")) return R.string.quick_settings_expanded_desktop_label;
        else if (mSpec.equals("reboot")) return R.string.quick_settings_reboot_label;
        else if (mSpec.equals("configurations")) return R.string.quick_settings_rrtools;
        else if (mSpec.equals("heads_up")) return R.string.quick_settings_heads_up_label;
        else if (mSpec.equals("lte")) return R.string.qs_lte_label;
        else if (mSpec.equals("themes")) return R.string.quick_settings_themes;
        else if (mSpec.equals("navbar")) return R.string.quick_settings_navigation_bar;
        else if (mSpec.equals("appcirclebar")) return R.string.quick_settings_appcirclebar_title;
        else if (mSpec.equals("kernel_adiutor")) return R.string.quick_settings_kernel_title;
        else if (mSpec.equals("screenrecord")) return R.string.quick_settings_screenrecord;
        else if (mSpec.equals("gesture_anywhere")) return R.string.quick_settings_gesture_anywhere_label;
        else if (mSpec.equals("battery_saver")) return R.string.quick_settings_battery_saver_label;
        else if (mSpec.equals("power_menu")) return R.string.quick_settings_power_menu_label;
        else if (mSpec.equals("app_picker")) return R.string.navbar_app_picker;
        else if (mSpec.equals("kill_app")) return R.string.qs_kill_app;
        else if (mSpec.equals("caffeine")) return R.string.quick_settings_caffeine_label;
        else if (mSpec.equals("hw_keys")) return R.string.quick_settings_hwkeys_title;
        else if (mSpec.equals("sound")) return R.string.quick_settings_sound_label;
        else if (mSpec.equals("lockscreen")) return R.string.quick_settings_lockscreen_label;
        else if (mSpec.equals("pulse")) return R.string.quick_settings_pulse_label;
        else if (mSpec.equals("pie")) return R.string.quick_settings_pie;
        else if (mSpec.equals("float_mode")) return R.string.recent_float_mode_title;
        else if (mSpec.equals("visualizer")) return R.string.quick_settings_visualizer_label;
        return 0;
    }

    private static class CustomHost extends QSTileHost {

        public CustomHost(Context context) {
            super(context, null, null, null, null, null, null, null, null, null,
                    null, null, new BlankSecurityController(), null);
        }

        @Override
        protected QSTile<?> createTile(String tileSpec) {
            return new DraggableTile(this, tileSpec);
        }

        public void replace(String oldTile, String newTile) {
            if (oldTile.equals(newTile)) {
                return;
            }
            MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_REORDER, oldTile + ","
                    + newTile);
            List<String> order = new ArrayList<>(mTileSpecs);
            int index = order.indexOf(oldTile);
            if (index < 0) {
                Log.e(TAG, "Can't find " + oldTile);
                return;
            }
            order.remove(newTile);
            order.add(index, newTile);
            setTiles(order);
        }

        public void remove(String tile) {
            MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_REMOVE, tile);
            List<String> tiles = new ArrayList<>(mTileSpecs);
            tiles.remove(tile);
            setTiles(tiles);
        }

        public void add(String tile) {
            MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_ADD, tile);
            List<String> tiles = new ArrayList<>(mTileSpecs);
            tiles.add(tile);
            setTiles(tiles);
        }

      public void reset() {
            Secure.putStringForUser(getContext().getContentResolver(), TILES_SETTING,
                    getContext().getString(R.string.quick_settings_tiles_reset),
                    ActivityManager.getCurrentUser());
        }
        

        private void setTiles(List<String> tiles) {
            Secure.putStringForUser(getContext().getContentResolver(), TILES_SETTING,
                    TextUtils.join(",", tiles), ActivityManager.getCurrentUser());
        }

        public void showAddDialog() {
            List<String> tiles = mTileSpecs;
            int numBroadcast = 0;
            for (int i = 0; i < tiles.size(); i++) {
                if (tiles.get(i).startsWith(IntentTile.PREFIX)) {
                    numBroadcast++;
                }
            }
            String[] defaults =
                getContext().getString(R.string.quick_settings_tiles_default).split(",");
            final String[] available = new String[defaults.length + 1
                                                  - (tiles.size() - numBroadcast)];
            final String[] availableTiles = new String[available.length];
            int index = 0;
            for (int i = 0; i < defaults.length; i++) {
                if (tiles.contains(defaults[i])) {
                    continue;
                }
                int resource = getLabelResource(defaults[i]);
                if (resource != 0) {
                    availableTiles[index] = defaults[i];
                    available[index++] = getContext().getString(resource);
                } else {
                    availableTiles[index] = defaults[i];
                    available[index++] = defaults[i];
                }
            }
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.add_tile)
                    .setItems(available, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (which < available.length - 1) {
                                add(availableTiles[which]);
                            }
                        }
                    }).show();
        }

        private boolean isValid(String action) {
            for (int i = 0; i < action.length(); i++) {
                char c = action.charAt(i);
                if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '.') {
                    return false;
                }
            }
            return true;
        }

        private static class BlankSecurityController implements SecurityController {
            @Override
            public boolean hasDeviceOwner() {
                return false;
            }

            @Override
            public boolean hasProfileOwner() {
                return false;
            }

            @Override
            public String getDeviceOwnerName() {
                return null;
            }

            @Override
            public String getProfileOwnerName() {
                return null;
            }

            @Override
            public boolean isVpnEnabled() {
                return false;
            }

            @Override
            public String getPrimaryVpnName() {
                return null;
            }

            @Override
            public String getProfileVpnName() {
                return null;
            }

            @Override
            public void onUserSwitched(int newUserId) {
            }

            @Override
            public void addCallback(SecurityControllerCallback callback) {
            }

            @Override
            public void removeCallback(SecurityControllerCallback callback) {
            }
        }
    }

    private static class DraggableTile extends QSTile<QSTile.State>
            implements DropListener {
        private String mSpec;
        private QSTileView mView;

        protected DraggableTile(QSTile.Host host, String tileSpec) {
            super(host);
            Log.d(TAG, "Creating tile " + tileSpec);
            mSpec = tileSpec;
        }

        @Override
        public QSTileView createTileView(Context context) {
            mView = super.createTileView(context);
            return mView;
        }

        @Override
        public void setListening(boolean listening) {
        }

        @Override
        protected QSTile.State newTileState() {
            return new QSTile.State();
        }

        @Override
        protected void handleClick() {
        }

        @Override
        protected void handleUpdateState(QSTile.State state, Object arg) {
            state.visible = true;
            state.icon = ResourceIcon.get(getIcon());
            state.label = getLabel();
        }

        private String getLabel() {
            int resource = getLabelResource(mSpec);
            if (resource != 0) {
                return mContext.getString(resource);
            }
            if (mSpec.startsWith(IntentTile.PREFIX)) {
                int lastDot = mSpec.lastIndexOf('.');
                if (lastDot >= 0) {
                    return mSpec.substring(lastDot + 1, mSpec.length() - 1);
                } else {
                    return mSpec.substring(IntentTile.PREFIX.length(), mSpec.length() - 1);
                }
            }
            return mSpec;
        }

        private int getIcon() {
            if (mSpec.equals("wifi")) return R.drawable.ic_qs_wifi_full_3;
            else if (mSpec.equals("bt")) return R.drawable.ic_qs_bluetooth_connected;
            else if (mSpec.equals("inversion")) return R.drawable.ic_invert_colors_enable;
            else if (mSpec.equals("cell")) return R.drawable.ic_qs_signal_full_3;
            else if (mSpec.equals("airplane")) return R.drawable.ic_signal_airplane_enable;
            else if (mSpec.equals("dnd")) return R.drawable.ic_qs_dnd_on;
            else if (mSpec.equals("rotation")) return R.drawable.ic_portrait_from_auto_rotate;
            else if (mSpec.equals("flashlight")) return R.drawable.ic_signal_flashlight_enable;
            else if (mSpec.equals("location")) return R.drawable.ic_signal_location_enable;
            else if (mSpec.equals("cast")) return R.drawable.ic_qs_cast_on;
            else if (mSpec.equals("hotspot")) return R.drawable.ic_hotspot_enable;
            else if (mSpec.equals("adb_network")) return R.drawable.ic_qs_network_adb_on;
            else if (mSpec.equals("compass")) return R.drawable.ic_qs_compass_on;
	    else if (mSpec.equals("nfc")) return R.drawable.ic_qs_nfc_on;
	    else if (mSpec.equals("profiles")) return R.drawable.ic_qs_profiles_on;
	    else if (mSpec.equals("sync")) return R.drawable.ic_qs_sync_on;
	    else if (mSpec.equals("volume_panel")) return R.drawable.ic_qs_volume_panel;
	    else if (mSpec.equals("usb_tether")) return R.drawable.ic_qs_usb_tether_on;
	    else if (mSpec.equals("screen_timeout")) return R.drawable.ic_qs_screen_timeout_short_avd;
	    else if (mSpec.equals("performance")) return R.drawable.ic_qs_perf_profile;
	    else if (mSpec.equals("ambient_display")) return R.drawable.ic_qs_ambientdisplay_on;
	    else if (mSpec.equals("live_display")) return R.drawable.ic_livedisplay_auto;
	    else if (mSpec.equals("music")) return R.drawable.ic_qs_media_play;
	    else if (mSpec.equals("brightness")) return R.drawable.ic_qs_brightness_auto_on;
	    else if (mSpec.equals("screen_off")) return R.drawable.ic_qs_power;
	    else if (mSpec.equals("screenshot")) return R.drawable.ic_qs_screenshot;
	    else if (mSpec.equals("expanded_desktop")) return R.drawable.ic_qs_expanded_desktop;
	    else if (mSpec.equals("reboot")) return R.drawable.ic_qs_reboot;
	    else if (mSpec.equals("configurations")) return R.drawable.ic_rr_tools;
	    else if (mSpec.equals("heads_up")) return R.drawable.ic_qs_heads_up_on;
	    else if (mSpec.equals("lte")) return R.drawable.ic_qs_lte_on;
	    else if (mSpec.equals("themes")) return R.drawable.ic_qs_themes;
	    else if (mSpec.equals("navbar")) return R.drawable.ic_qs_navbar_on;
	    else if (mSpec.equals("appcirclebar")) return R.drawable.ic_qs_appcirclebar_on;
	    else if (mSpec.equals("kernel_adiutor")) return R.drawable.ic_qs_kernel_adiutor;	
	    else if (mSpec.equals("screenrecord")) return R.drawable.ic_qs_screenrecord;	
	    else if (mSpec.equals("gesture_anywhere")) return R.drawable.ic_qs_gestures_on;
	    else if (mSpec.equals("battery_saver")) return R.drawable.ic_qs_battery_saver_on;
	    else if (mSpec.equals("power_menu")) return R.drawable.ic_qs_power_menu;
	    else if (mSpec.equals("app_picker")) return R.drawable.ic_qs_app_picker;
	    else if (mSpec.equals("kill_app")) return R.drawable.ic_app_kill;
	    else if (mSpec.equals("caffeine")) return R.drawable.ic_qs_caffeine_on;
	    else if (mSpec.equals("hw_keys")) return R.drawable.ic_qs_hwkeys_on;
	    else if (mSpec.equals("sound")) return R.drawable.ic_qs_ringer_silent;
	    else if (mSpec.equals("lockscreen")) return R.drawable.ic_qs_lock_screen_on;
	    else if (mSpec.equals("pulse")) return R.drawable.ic_qs_pulse;
	    else if (mSpec.equals("pie")) return R.drawable.ic_qs_pie;
	    else if (mSpec.equals("float_mode")) return R.drawable.ic_qs_floating_on;
	    else if (mSpec.equals("visualizer")) return R.drawable.ic_qs_visualizer_static;
            return R.drawable.android;
        }

        @Override
        public int getMetricsCategory() {
            return 20000;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DraggableTile) {
                return mSpec.equals(((DraggableTile) o).mSpec);
            }
            return false;
        }

        @Override
        public void onDrop(String sourceText) {
            ((CustomHost) mHost).replace(mSpec, sourceText);
        }

    }

    private class DragHelper implements OnDragListener {

        private final View mView;
        private final DropListener mListener;

        public DragHelper(View view, DropListener dropListener) {
            mView = view;
            mListener = dropListener;
            mView.setOnDragListener(this);
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    mView.setBackgroundColor(0x77ffffff);
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    stopDrag();
                case DragEvent.ACTION_DRAG_EXITED:
                    mView.setBackgroundColor(0x0);
                    break;
                case DragEvent.ACTION_DROP:
                    stopDrag();
                    String text = event.getClipData().getItemAt(0).getText().toString();
                    mListener.onDrop(text);
                    break;
            }
            return true;
        }

    }

    public interface DropListener {
        void onDrop(String sourceText);
    }

    private class DraggableQsPanel extends QSPanel implements OnTouchListener {
        public DraggableQsPanel(Context context) {
            super(context);
            mBrightnessView.setVisibility(View.GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            for (TileRecord r : mRecords) {
                new DragHelper(r.tileView, (DraggableTile) r.tile);
                r.tileView.setTag(r.tile);
                r.tileView.setOnTouchListener(this);

                for (int i = 0; i < r.tileView.getChildCount(); i++) {
                    r.tileView.getChildAt(i).setClickable(false);
                }
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    String tileSpec = (String) ((DraggableTile) v.getTag()).mSpec;
                    ClipData data = ClipData.newPlainText(tileSpec, tileSpec);
                    v.startDrag(data, new View.DragShadowBuilder(v), null, 0);
                    onStartDrag();
                    return true;
            }
            return false;
        }
    }

}
