/*
 * Copyright (C) 2017, ParanoidAndroid Project
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

package com.android.server.policy;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

public class ANBIHandler implements PointerEventListener {

    private static final String TAG = ANBIHandler.class.getSimpleName();
    private static final boolean DEBUG = false;

    private boolean mScreenTouched;

    private Context mContext;

    public ANBIHandler(Context context) {
        mContext = context;
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_DOWN:
                mScreenTouched = true;
                break;
            default:
                mScreenTouched = false;
                break;
        }
        if (DEBUG) {
            Log.d(TAG, "Screen touched= " + mScreenTouched);
        }
    }

    public boolean isScreenTouched() {
        if (DEBUG) {
            Log.d(TAG, "isScreenTouched: mScreenTouched= " + mScreenTouched);
        }
        return mScreenTouched;
    }
}
