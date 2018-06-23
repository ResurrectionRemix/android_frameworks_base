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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import static android.provider.Settings.Global.ZEN_MODE_ALARMS;
import static android.provider.Settings.Global.ZEN_MODE_OFF;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeConfig.ZenRule;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.statusbar.policy.ZenModeController.Callback;
import com.android.systemui.volume.ZenModePanel;

/** Quick settings tile: Do not disturb **/
public class DndTile extends QSTileImpl<BooleanState> {

    private static final Intent ZEN_SETTINGS =
            new Intent(Settings.ACTION_ZEN_MODE_SETTINGS);

    private static final Intent ZEN_PRIORITY_SETTINGS =
            new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);

    private static final String ACTION_SET_VISIBLE = "com.android.systemui.dndtile.SET_VISIBLE";
    private static final String EXTRA_VISIBLE = "visible";

    private static final QSTile.Icon TOTAL_SILENCE =
            ResourceIcon.get(R.drawable.ic_qs_dnd_on_total_silence);

    private final ZenModeController mController;
    private final DndDetailAdapter mDetailAdapter;

    private boolean mListening;
    private boolean mShowingDetail;
    private boolean mReceiverRegistered;

    public DndTile(QSHost host) {
        super(host);
        mController = Dependency.get(ZenModeController.class);
        mDetailAdapter = new DndDetailAdapter();
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_SET_VISIBLE));
        mReceiverRegistered = true;
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        if (mReceiverRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
    }

    public static void setVisible(Context context, boolean visible) {
        Prefs.putBoolean(context, Prefs.Key.DND_TILE_VISIBLE, visible);
    }

    public static boolean isVisible(Context context) {
        return Prefs.getBoolean(context, Prefs.Key.DND_TILE_VISIBLE, false /* defaultValue */);
    }

    public static void setCombinedIcon(Context context, boolean combined) {
        Prefs.putBoolean(context, Prefs.Key.DND_TILE_COMBINED_ICON, combined);
    }

    public static boolean isCombinedIcon(Context context) {
        return Prefs.getBoolean(context, Prefs.Key.DND_TILE_COMBINED_ICON,
                false /* defaultValue */);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public boolean isDualTarget() {
        return true;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public Intent getLongClickIntent() {
        return ZEN_SETTINGS;
    }

    @Override
    protected void handleClick() {
        if (mState.value) {
            mController.setZen(ZEN_MODE_OFF, null, TAG);
        } else {
            int zen = Prefs.getInt(mContext, Prefs.Key.DND_FAVORITE_ZEN, Global.ZEN_MODE_ALARMS);
            mController.setZen(zen, null, TAG);
        }
    }

    @Override
    protected void handleSecondaryClick() {
        if (mController.isVolumeRestricted()) {
            // Collapse the panels, so the user can see the toast.
            mHost.collapsePanels();
            SysUIToast.makeText(mContext, mContext.getString(
                    com.android.internal.R.string.error_message_change_not_allowed),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!mState.value) {
            // Because of the complexity of the zen panel, it needs to be shown after
            // we turn on zen below.
            mController.addCallback(new ZenModeController.Callback() {
                @Override
                public void onZenChanged(int zen) {
                    mController.removeCallback(this);
                    showDetail(true);
                }
            });
            int zen = Prefs.getInt(mContext, Prefs.Key.DND_FAVORITE_ZEN,
                    Global.ZEN_MODE_ALARMS);
            mController.setZen(zen, null, TAG);
        } else {
            showDetail(true);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_dnd_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mController == null) {
            return;
        }
        final int zen = arg instanceof Integer ? (Integer) arg : mController.getZen();
        final boolean newValue = zen != ZEN_MODE_OFF;
        final boolean valueChanged = state.value != newValue;
        if (state.slash == null) state.slash = new SlashState();
        state.dualTarget = true;
        state.value = newValue;
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.slash.isSlashed = !state.value;
        checkIfRestrictionEnforcedByAdminOnly(state, UserManager.DISALLOW_ADJUST_VOLUME);
        switch (zen) {
            case Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_dnd_on_priority);
                state.label = mContext.getString(R.string.quick_settings_dnd_priority_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_priority_on);
                break;
            case Global.ZEN_MODE_NO_INTERRUPTIONS:
                state.icon = TOTAL_SILENCE;
                state.label = mContext.getString(R.string.quick_settings_dnd_none_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_none_on);
                break;
            case ZEN_MODE_ALARMS:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_dnd_on);
                state.label = mContext.getString(R.string.quick_settings_dnd_alarms_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_alarms_on);
                break;
            default:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_dnd_on);
                state.label = mContext.getString(R.string.quick_settings_dnd_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd);
                break;
        }
        if (valueChanged) {
            fireToggleStateChanged(state.value);
        }
        state.dualLabelContentDescription = mContext.getResources().getString(
                R.string.accessibility_quick_settings_open_settings, getTileLabel());
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_DND;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_dnd_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_dnd_changed_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mController == null) return;
        if (mListening) {
            mController.addCallback(mZenCallback);
            Prefs.registerListener(mContext, mPrefListener);
        } else {
            mController.removeCallback(mZenCallback);
            Prefs.unregisterListener(mContext, mPrefListener);
        }
    }

    @Override
    public boolean isAvailable() {
        return isVisible(mContext);
    }

    private final OnSharedPreferenceChangeListener mPrefListener
            = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                @Prefs.Key String key) {
            if (Prefs.Key.DND_TILE_COMBINED_ICON.equals(key) ||
                    Prefs.Key.DND_TILE_VISIBLE.equals(key)) {
                refreshState();
            }
        }
    };

    private final ZenModeController.Callback mZenCallback = new ZenModeController.Callback() {
        public void onZenChanged(int zen) {
            refreshState(zen);
            if (isShowingDetail()) {
                mDetailAdapter.updatePanel();
            }
        }

        @Override
        public void onConfigChanged(ZenModeConfig config) {
            if (isShowingDetail()) {
                mDetailAdapter.updatePanel();
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final boolean visible = intent.getBooleanExtra(EXTRA_VISIBLE, false);
            setVisible(mContext, visible);
            refreshState();
        }
    };

    private final class DndDetailAdapter implements DetailAdapter, OnAttachStateChangeListener {

        private ZenModePanel mZenPanel;
        private boolean mAuto;

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_dnd_label);
        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public Intent getSettingsIntent() {
            return ZEN_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            MetricsLogger.action(mContext, MetricsEvent.QS_DND_TOGGLE, state);
            if (!state) {
                mController.setZen(ZEN_MODE_OFF, null, TAG);
                mAuto = false;
            } else {
                int zen = Prefs.getInt(mContext, Prefs.Key.DND_FAVORITE_ZEN,
                        ZEN_MODE_ALARMS);
                mController.setZen(zen, null, TAG);
            }
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QS_DND_DETAILS;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mZenPanel = convertView != null ? (ZenModePanel) convertView
                    : (ZenModePanel) LayoutInflater.from(context).inflate(
                            R.layout.zen_mode_panel, parent, false);
            if (convertView == null) {
                mZenPanel.init(mController);
                mZenPanel.addOnAttachStateChangeListener(this);
                mZenPanel.setCallback(mZenModePanelCallback);
                mZenPanel.setEmptyState(R.drawable.ic_qs_dnd_detail_empty, R.string.dnd_is_off);
            }
            updatePanel();
            return mZenPanel;
        }

        private void updatePanel() {
            if (mZenPanel == null) return;
            mAuto = false;
            if (mController.getZen() == ZEN_MODE_OFF) {
                mZenPanel.setState(ZenModePanel.STATE_OFF);
            } else {
                ZenModeConfig config = mController.getConfig();
                String summary = "";
                if (config.manualRule != null && config.manualRule.enabler != null) {
                    summary = getOwnerCaption(config.manualRule.enabler);
                }
                for (ZenRule automaticRule : config.automaticRules.values()) {
                    if (automaticRule.isAutomaticActive()) {
                        if (summary.isEmpty()) {
                            summary = mContext.getString(R.string.qs_dnd_prompt_auto_rule,
                                    automaticRule.name);
                        } else {
                            summary = mContext.getString(R.string.qs_dnd_prompt_auto_rule_app);
                        }
                    }
                }
                if (summary.isEmpty()) {
                    mZenPanel.setState(ZenModePanel.STATE_MODIFY);
                } else {
                    mAuto = true;
                    mZenPanel.setState(ZenModePanel.STATE_AUTO_RULE);
                    mZenPanel.setAutoText(summary);
                }
            }
        }

        private String getOwnerCaption(String owner) {
            final PackageManager pm = mContext.getPackageManager();
            try {
                final ApplicationInfo info = pm.getApplicationInfo(owner, 0);
                if (info != null) {
                    final CharSequence seq = info.loadLabel(pm);
                    if (seq != null) {
                        final String str = seq.toString().trim();
                        return mContext.getString(R.string.qs_dnd_prompt_app, str);
                    }
                }
            } catch (Throwable e) {
                Slog.w(TAG, "Error loading owner caption", e);
            }
            return "";
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            mShowingDetail = true;
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            mShowingDetail = false;
            mZenPanel = null;
        }
    }

    private final ZenModePanel.Callback mZenModePanelCallback = new ZenModePanel.Callback() {
        @Override
        public void onPrioritySettings() {
            Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(
                    ZEN_PRIORITY_SETTINGS, 0);
        }

        @Override
        public void onInteraction() {
            // noop
        }

        @Override
        public void onExpanded(boolean expanded) {
            // noop
        }
    };

}
