/*
 *  Copyright (C) 2018 The OmniROM Project
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
import android.content.Intent;
import android.content.pm.PackageManager;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

public class GoogleVoiceAssistTile extends QSTileImpl<BooleanState>  {
    private final ActivityStarter mActivityStarter;

    public GoogleVoiceAssistTile(QSHost host) {
        super(host);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    private Intent getVoiceAssistIntent() {
        final Intent assistIntent = new Intent(Intent.ACTION_VOICE_ASSIST);
        assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                // that will always trigger the voice search page
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return assistIntent;
    }

    private boolean isAsistInstalled() {
        return getVoiceAssistIntent().resolveActivityInfo(mContext.getPackageManager(), 0) != null;
    }

    @Override
    public boolean isAvailable() {
        // we cannot use the isAsistInstalled check here cause
        // on boot its to early and the check will fail
        return true;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        if (isAsistInstalled()) {
            mActivityStarter.postStartActivityDismissingKeyguard(getVoiceAssistIntent(), 0);
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = true;
        state.label = mContext.getString(R.string.quick_settings_google_voice_assist_label);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_google_voice_assist);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_google_voice_assist_label);
    }
}