/*
 * Copyright (C) 2018 Projekt Substratum
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.systemui.SystemUIApplication;
import com.android.systemui.keyguard.KeyguardViewMediator;

public class SoundRefreshReceiver extends BroadcastReceiver {
    public static String ACTION = "com.android.systemui.action.REFRESH_SOUND";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            ((SystemUIApplication) context.getApplicationContext())
                    .getComponent(KeyguardViewMediator.class).refreshSounds();
        }
    }
}
