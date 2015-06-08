/*
 * Copyright (C) 2015 Slimroms
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

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.net.Uri;
import android.provider.Settings;
import com.android.systemui.qs.QSTile;

import com.android.internal.util.slim.Action;
import com.android.internal.util.slim.ActionConfig;
import com.android.internal.util.slim.ActionConstants;
import com.android.internal.util.slim.ActionHelper;

import java.util.ArrayList;

public class SlimActionTile extends QSTile<QSTile.BooleanState> {

    private ArrayList<ActionConfig> mActionConfigs;
    private int mActionConfigIndex;
    private ActionConfig mCurrentActionConfig;

    public SlimActionTile(Host host) {
        super(host);
        populateActionConfigs();
    }

    private void populateActionConfigs() {
        mActionConfigs = ActionHelper.getQuickTileConfigWithDescription(
                mContext, "shortcut_action_tile_values",
                "shortcut_action_tile_entries");
        if (mActionConfigs.size() > 0 && mCurrentActionConfig == null) {
            mCurrentActionConfig = mActionConfigs.get(0);
            mActionConfigIndex = 0;
        }
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        mActionConfigIndex++;
        if (mActionConfigIndex >= mActionConfigs.size()) {
            mActionConfigIndex = 0;
        }
        mCurrentActionConfig = mActionConfigs.get(mActionConfigIndex);
        if (mActionConfigs.size() > 1) {
            refreshState();
        } else {
            processAction();
        }
    }

    @Override
    protected void handleLongClick() {
        if (mCurrentActionConfig == null) {
            return;
        }
        processAction();
    }

    private void processAction() {
        mHost.collapsePanels();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
        }
        Action.processAction(mContext, mCurrentActionConfig.getClickAction(), false);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        if (mCurrentActionConfig == null) {
            return;
        }
        state.label = mCurrentActionConfig.getClickActionDescription();
        state.icon = ResourceIcon.get(ActionHelper.getActionIconUri(mContext,
                mCurrentActionConfig.getClickAction(), mCurrentActionConfig.getIcon()));
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            populateActionConfigs();
            refreshState();
        }
    }

}
