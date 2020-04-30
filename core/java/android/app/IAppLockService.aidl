/**
 * Copyright (C) 2017-2020 Paranoid Android
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

package android.app;

import android.app.IAppLockCallback;

/** @hide */
interface IAppLockService {

    void addAppToList(in String packageName);

    void removeAppFromList(in String packageName);

    boolean isAppLocked(in String packageName);

    boolean isAppOpen(in String packageName);

    int getLockedAppsCount();

    List<String> getLockedPackages();

    void addAppLockCallback(IAppLockCallback callback);

    void removeAppLockCallback(IAppLockCallback callback);
}
