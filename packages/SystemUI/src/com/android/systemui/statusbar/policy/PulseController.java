/**
 * Copyright (C) 2020 The DirtyUnicorns Project
 *
 * @author: Randall Rushing <randall.rushing@gmail.com>
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
 * limitations under the License. *
 */
package com.android.systemui.statusbar.policy;

import android.widget.FrameLayout;

import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.navigation.pulse.VisualizerView;
import com.android.systemui.statusbar.phone.NavigationBarView;

public interface PulseController extends NotificationMediaManager.MediaListener {
    public interface PulseStateListener {
        public void onPulseStateChanged(boolean isRunning);
    }

    public void attachPulseTo(FrameLayout parent);
    public void detachPulseFrom(FrameLayout parent, boolean keepLinked);
    public void addCallback(PulseStateListener listener);
    public void removeCallback(PulseStateListener listener);
    public void setDozing(boolean dozing);
    public void notifyKeyguardGoingAway();
    public void setKeyguardShowing(boolean showing);
}
