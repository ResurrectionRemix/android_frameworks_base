/**
 * Copyright (C) 2016 The ParanoidAndroid Project
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
package android.pocket;

import android.pocket.IPocketCallback;

/** @hide */
interface IPocketService {

    // add callback to get notified about pocket state.
    void addCallback(IPocketCallback callback);

    // remove callback and stop getting notified about pocket state.
    void removeCallback(IPocketCallback callback);

    // notify pocket service about intercative state changed.
    // @see com.android.policy.PhoneWindowManager
    void onInteractiveChanged(boolean interactive);

    // external processes can request changing listening state.
    void setListeningExternal(boolean listen);

    // check if device is in pocket.
    boolean isDeviceInPocket();

    // Custom methods
    void setPocketLockVisible(boolean visible);
    boolean isPocketLockVisible();

}