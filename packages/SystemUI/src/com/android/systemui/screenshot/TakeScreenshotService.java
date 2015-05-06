/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.screenshot;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class TakeScreenshotService extends Service {
    private static final String TAG = "TakeScreenshotService";

    private static GlobalScreenshot mScreenshot;

    @Override
    public IBinder onBind(Intent intent) {
        return new Messenger(new ScreenshotHandler(TakeScreenshotService.this)).getBinder();
    }

    private static class ScreenshotHandler extends Handler {
        private TakeScreenshotService takeScreenshotService;

        public ScreenshotHandler(TakeScreenshotService takeScreenshotService) {
            this.takeScreenshotService = takeScreenshotService;
        }

        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    final Messenger callback = msg.replyTo;
                    if (mScreenshot == null) {
                        mScreenshot = new GlobalScreenshot(takeScreenshotService);
                    }
                    mScreenshot.takeScreenshot(new Runnable() {
                        @Override public void run() {
                            Message reply = Message.obtain(null, 1);
                            try {
                                callback.send(reply);
                            } catch (RemoteException ignored) { }
                        }
                    }, msg.arg1 > 0, msg.arg2 > 0);
            }
        }
    }
}
