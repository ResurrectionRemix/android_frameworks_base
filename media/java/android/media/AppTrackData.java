/*
 * Copyright (C) 2020 The exTHmUI Open Source Project
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

package android.media;

public class AppTrackData {
    private final String mPackageName;
    private final float mVolume;
    private final boolean mMute;
    private final boolean mActive;

    AppTrackData(String packageName, boolean mute, float volume, boolean active) {
        mPackageName = packageName;
        mMute = mute;
        mVolume = volume;
        mActive = active;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public float getVolume() {
        return mVolume;
    }

    public boolean isMuted() {
        return mMute;
    }

    public boolean isActive() {
        return mActive;
    }
}
