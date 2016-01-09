/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2012-2015 The CyanogenMod Project
 * Copyright (C) 2014-2015 The Euphoria-OS Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.omni.screenrecord.TakeScreenrecordService;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.internal.logging.MetricsLogger;

/** Quick settings tile: Screenrecord **/
public class ScreenrecordTile extends QSTile<QSTile.BooleanState> {

	private static final Intent APP_RECORD = new Intent().setComponent(new ComponentName(
            "com.android.gallery3d", "com.android.gallery3d.app.GalleryActivity"));

    private boolean mListening;
    private boolean mRecording;
    private final Object mScreenrecordLock = new Object();
    private ServiceConnection mScreenrecordConnection = null;

    public ScreenrecordTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

   @Override
    public int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }

    @Override
    public void handleClick() {
        mHost.collapsePanels();
        /* wait for the panel to close */
        try {
             Thread.sleep(2000);
        } catch (InterruptedException ie) {
             // Do nothing
        }
        takeScreenRecord();
    }

    @Override
    protected void handleSecondaryClick() {
       mHost.startActivityDismissingKeyguard(APP_RECORD);
    }

    @Override
    public void handleLongClick() {
       mHost.startActivityDismissingKeyguard(APP_RECORD);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
	state.value = mRecording;
        if (mRecording) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_screenrecord);
            state.label = mContext.getString(R.string.quick_settings_screenrecord_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_screenrecord);
            state.label = mContext.getString(R.string.quick_settings_screenrecord);
        }
    }

    final Runnable mScreenrecordTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenrecordLock) {
                if (mScreenrecordConnection != null) {
                    mContext.unbindService(mScreenrecordConnection);
                    mScreenrecordConnection = null;
                    mRecording = false;
                }
            }
        }
    };

    private void takeScreenRecord() {
        synchronized (mScreenrecordLock) {
            if (mScreenrecordConnection != null) {
                return;
            }
            Intent intent = new Intent(mContext, TakeScreenrecordService.class);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenrecordLock) {
                        if (mScreenrecordConnection != this) {
                            return;
                        }

                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenrecordLock) {
                                    if (mScreenrecordConnection == myConn) {
                                        mContext.unbindService(mScreenrecordConnection);
                                        mScreenrecordConnection = null;
                                        mRecording = false;
                                        mHandler.removeCallbacks(mScreenrecordTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        // Take the screenrecord
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                            // Do nothing here
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // Do nothing here
                }
            };

            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenrecordConnection = conn;
                mRecording = true;
                mHandler.postDelayed(mScreenrecordTimeout, 100000);
            }
        }
    }
}
