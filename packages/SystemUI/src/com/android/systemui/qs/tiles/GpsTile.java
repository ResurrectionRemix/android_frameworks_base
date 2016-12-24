/*
 * Copyright (C) 2016 RR
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

package com.android.systemui.qs.tiles;


import android.content.Intent;
import android.os.UserManager;

import android.provider.Settings;
import android.widget.Switch;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.GpsController;
import com.android.systemui.statusbar.policy.GpsController.GpsSettingsChangeCallback;

/** Quick settings tile: Gps **/
public class GpsTile extends QSTile<QSTile.BooleanState> {

    private final AnimationIcon mEnable =
        new AnimationIcon(R.drawable.ic_signal_location_enable_animation,
            R.drawable.ic_signal_location_disable);
    private final AnimationIcon mDisable =
        new AnimationIcon(R.drawable.ic_signal_location_disable_animation,
            R.drawable.ic_signal_location_enable);

    private final GpsController mController;
    private final KeyguardMonitor mKeyguard;
    private final Callback mCallback = new Callback();

    public GpsTile(Host host) {
        super(host);
        mController = host.getGpsController();
        mKeyguard = host.getKeyguardMonitor();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addSettingsChangedCallback(mCallback);
            mKeyguard.addCallback(mCallback);
        } else {
            mController.removeSettingsChangedCallback(mCallback);
            mKeyguard.removeCallback(mCallback);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }

    @Override
    protected void handleClick() {
        if (mKeyguard.isSecure() && mKeyguard.isShowing()) {
            mHost.startRunnableDismissingKeyguard(new Runnable() {
                @Override
                public void run() {
                    mHost.openPanels();
                    mController.setGpsEnabled(!mController.isGpsEnabled());
                }
            });

            return;
        }

        mController.setGpsEnabled(!mController.isGpsEnabled());
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_gps_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean gpsEnabled = mController.isGpsEnabled();

        // Work around for bug 15916487: don't show location tile on top of lock screen. After the
        // bug is fixed, this should be reverted to only hiding it on secure lock screens:
        // state.visible = !(mKeyguard.isSecure() && mKeyguard.isShowing());
        state.value = gpsEnabled;
        checkIfRestrictionEnforcedByAdminOnly(state, UserManager.DISALLOW_SHARE_LOCATION);

        state.label = mContext.getString(R.string.quick_settings_gps_label);

        if (gpsEnabled) {
            state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_gps_on);
            state.icon = mEnable;
        } else {
            state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_gps_off);
            state.icon = mDisable;
        }

        state.minimalAccessibilityClassName = state.expandedAccessibilityClassName
            = Switch.class.getName();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_LOCATION;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_gps_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_gps_changed_off);
        }
    }

    private final class Callback implements GpsSettingsChangeCallback, KeyguardMonitor.Callback {
        @Override
        public void onGpsSettingsChanged(boolean enabled) {
            refreshState();
        }

        @Override
        public void onKeyguardChanged() {
            refreshState();
        }
    }
}
