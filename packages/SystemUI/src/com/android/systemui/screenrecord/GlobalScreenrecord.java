/*
 * Copyright (C) 2011 The Android Open Source Project
 * Modifications Copyright (C) The OmniROM Project
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
 *
 * Per article 5 of the Apache 2.0 License, some modifications to this code
 * were made by the OmniROM Project.
 *
 * Modifications Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.android.systemui.screenrecord;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.util.NotificationChannels;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class GlobalScreenrecord {
    private static final String TAG = "GlobalScreenrecord";

    private static final int SCREENRECORD_NOTIFICATION_ID = 42;
    private static final int MSG_TASK_ENDED = 1;
    private static final int MSG_TASK_ERROR = 2;

    private static final String TMP_PATH = Environment.getExternalStorageDirectory()
            + File.separator + "__tmp_screenrecord.mp4";

    private static final String SCREENRECORD_SHARE_SUBJECT_TEMPLATE = "Screenrecord (%s)";
    private static final String SCREENRECORD_URI_ID = "android:screenrecord_uri_id";
    private static final String SHARING_INTENT = "android:screenrecord_sharing_intent";

    private Context mContext;
    private Handler mHandler;
    private NotificationManager mNotificationManager;

    private CaptureThread mCaptureThread;

    private Runnable mFinisher;

    private FrameLayout mFrameLayout;
    private LayoutInflater mInflater;
    private WindowManager mWindowManager;
    private boolean mIsHintAtRight;

    private String mNotifContent = null;
    private boolean mHintShowing = false;

    private long mRecordingStartTime = 0;
    private long mRecordingTotalTime = 0;
    private long mFileSize = 0;

    private void setFinisher(Runnable finisher) {
        mFinisher = finisher;
    }

    private class CaptureThread extends Thread {
        private Runnable mFInisher;
        private int mMode;

        public void setMode(int mode) {
            mMode = mode;
        }

        public void run() {
            Runtime rt = Runtime.getRuntime();

            // additional arguments to pass to screenrecord bin
            final String[] cmds = new String[6];
            cmds[0] = "/system/bin/screenrecord";
            cmds[1] = TMP_PATH;
            switch (mMode) {
                case WindowManager.SCREEN_RECORD_LOW_QUALITY:
                    // low resolution and 1.5Mbps
                    cmds[2] = "--size";
                    cmds[3] = "480x800";
                    cmds[4] = "--bit-rate";
                    cmds[5] = "1500000";
                    break;
                case WindowManager.SCREEN_RECORD_MID_QUALITY:
                    // default resolution (720p) and 4Mbps
                    cmds[2] = "--size";
                    cmds[3] = "720x1280";
                    cmds[4] = "--bit-rate";
                    cmds[5] = "4000000";
                    break;
                case WindowManager.SCREEN_RECORD_HIGH_QUALITY:
                    // default resolution (720p) and 8Mbps
                    cmds[2] = "--size";
                    cmds[3] = "720x1280";
                    cmds[4] = "--bit-rate";
                    cmds[5] = "8000000";
                    break;
            }

            try {
                Process proc = rt.exec(cmds);
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));

                while (!isInterrupted()) {
                    if (br.ready()) {
                        String log = br.readLine();
                        Log.d(TAG, log);
                    }

                    try {
                        int code = proc.exitValue();

                        // If the recording is still running, we won't reach here,
                        // but will land in the catch block below.
                        mRecordingTotalTime = System.currentTimeMillis() - mRecordingStartTime;
                        Message msg = Message.obtain(mHandler, MSG_TASK_ENDED, code, 0, null);
                        mHandler.sendMessage(msg);

                        // No need to stop the process, so we can exit this method early
                        return;
                    } catch (IllegalThreadStateException ignore) {
                        // ignored
                    }
                }

                // Terminate the recording process
                // HACK: There is no way to send SIGINT to a process, so we... hack
                rt.exec(new String[]{"killall", "-2", "screenrecord"});
                mRecordingTotalTime = System.currentTimeMillis() - mRecordingStartTime;
            } catch (IOException e) {
                // Notify something went wrong
                Message msg = Message.obtain(mHandler, MSG_TASK_ERROR);
                mHandler.sendMessage(msg);

                // Log the error as well
                Log.e(TAG, "Error while starting the screenrecord process", e);
            }
        }
    }


    /**
     * @param context everything needs a context :(
     */
    public GlobalScreenrecord(Context context) {
        mContext = context;
        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == MSG_TASK_ENDED) {
                    // The screenrecord process stopped, act as if user
                    // requested the record to stop.
                    stopScreenrecord();
                } else if (msg.what == MSG_TASK_ERROR) {
                    mCaptureThread = null;
                    mFinisher.run();
                    // TODO: Notify the error
                }
            }
        };
        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mWindowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public boolean isRecording() {
        return (mCaptureThread != null);
    }

    /**
     * Starts recording the screen.
     */
    void takeScreenrecord(Runnable finisher, int mode) {
        if (mCaptureThread != null) {
            Log.e(TAG, "Capture Thread is already running, ignoring screenrecord start request");
            return;
        }

        setFinisher(finisher);
        mCaptureThread = new CaptureThread();
        mCaptureThread.setMode(mode);
        mCaptureThread.start();

        showHint();
        updateNotification(mode);
    }

    public void updateNotification(int mode) {
        final Resources r = mContext.getResources();
        final String base = r.getString(R.string.screenrecord_notif_title);
        switch (mode) {
            case WindowManager.SCREEN_RECORD_LOW_QUALITY:
                mNotifContent = base + " - 480x800 @1.5Mbps";
                mRecordingStartTime = System.currentTimeMillis();
                break;
            case WindowManager.SCREEN_RECORD_MID_QUALITY:
                mNotifContent = base + " - 720x1280 @4Mbps";
                mRecordingStartTime = System.currentTimeMillis();
                break;
            case WindowManager.SCREEN_RECORD_HIGH_QUALITY:
                mNotifContent = base + " - 720x1280 @8Mbps";
                mRecordingStartTime = System.currentTimeMillis();
                break;
            case -1:
                // updating current notification
                mNotifContent = mNotifContent;
        }
        // Display a notification
        Notification.Builder builder = new Notification.Builder(mContext, NotificationChannels.SCREENRECORDS)
            .setTicker(r.getString(R.string.screenrecord_notif_ticker))
            .setContentTitle(mNotifContent)
            .setSmallIcon(R.drawable.ic_capture_video)
            .setWhen(mRecordingStartTime)
            .setOngoing(true)
            .setUsesChronometer(true);

        Intent stopIntent = new Intent(mContext, TakeScreenrecordService.class)
            .setAction(TakeScreenrecordService.ACTION_STOP);
        PendingIntent stopPendIntent = PendingIntent.getService(mContext, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pointerIntent = new Intent(mContext, TakeScreenrecordService.class)
            .setAction(TakeScreenrecordService.ACTION_TOGGLE_POINTER);
        PendingIntent pointerPendIntent = PendingIntent.getService(mContext, 0, pointerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT);

        Intent hintIntent = new Intent(mContext, TakeScreenrecordService.class)
            .setAction(TakeScreenrecordService.ACTION_TOGGLE_HINT);
        PendingIntent hintPendIntent = PendingIntent.getService(mContext, 0, hintIntent,
            PendingIntent.FLAG_UPDATE_CURRENT);

        boolean showTouches = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SHOW_TOUCHES, 0, UserHandle.USER_CURRENT) != 0;
        int togglePointerIconId = showTouches ?
                R.drawable.ic_pointer_off :
                R.drawable.ic_pointer_on;
        int togglePointerStringId = showTouches ?
                R.string.screenrecord_notif_pointer_off :
                R.string.screenrecord_notif_pointer_on;
        int hideHintStringId = mHintShowing ?
                R.string.screenrecord_hide_hint :
                R.string.screenrecord_show_hint;
        builder
            .addAction(com.android.internal.R.drawable.ic_media_stop,
                r.getString(R.string.screenrecord_notif_stop), stopPendIntent)
            .addAction(togglePointerIconId,
                r.getString(togglePointerStringId), pointerPendIntent)
            .addAction(R.drawable.ic_hide_hint,
                r.getString(hideHintStringId), hintPendIntent);

        Notification notif = builder.build();
        mNotificationManager.notify(SCREENRECORD_NOTIFICATION_ID, notif);
    }

    private void showHint() {
        mHintShowing = true;
        mIsHintAtRight = true;
        final int size = (int) (mContext.getResources()
                .getDimensionPixelSize(R.dimen.screenrecord_hint_size));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // don't get softkey inputs
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // allow outside inputs
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        params.width = size;
        params.height = size;

        mFrameLayout = new FrameLayout(mContext);

        mWindowManager.addView(mFrameLayout, params);
        mInflater.inflate(R.layout.screenrecord_hint, mFrameLayout);

        final ImageView hint = (ImageView) mFrameLayout.findViewById(R.id.hint);
        hint.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Turn off pointer in all cases
                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SHOW_TOUCHES, 0, UserHandle.USER_CURRENT);
                Message msg = Message.obtain(mHandler, MSG_TASK_ENDED);
                mHandler.sendMessage(msg);
            }
        });

        hint.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                hint.setAnimation(null);
                WindowManager.LayoutParams params =
                        (WindowManager.LayoutParams) mFrameLayout.getLayoutParams();
                params.gravity = Gravity.BOTTOM | (mIsHintAtRight? Gravity.LEFT : Gravity.RIGHT);
                mIsHintAtRight = !mIsHintAtRight;
                mWindowManager.updateViewLayout(mFrameLayout, params);
                hint.startAnimation(getHintAnimation());
                return true;
            }
        });

        hint.startAnimation(getHintAnimation());
    }

    public void toggleHint() {
        mHintShowing = !mHintShowing;
        final ImageView hint = (ImageView) mFrameLayout.findViewById(R.id.hint);
        if (mHintShowing) {
            hint.setImageAlpha(255);
            hint.startAnimation(getHintAnimation());
        } else  {
            hint.setImageAlpha(0);
            hint.setAnimation(null);
        }
        updateNotification(-1);
    }

    private Animation getHintAnimation() {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500);
        anim.setStartOffset(100);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        return anim;
    }

    /**
     * Stops recording the screen.
     */
    void stopScreenrecord() {
        if (mCaptureThread == null) {
            Log.e(TAG, "No capture thread, cannot stop screen recording!");
            mFinisher.run();
            return;
        }

        mNotificationManager.cancel(SCREENRECORD_NOTIFICATION_ID);

        try {
            mCaptureThread.interrupt();
        } catch (Exception e) { /* ignore */ }

        // Wait a bit and copy the output file to a safe place
        while (mCaptureThread.isAlive()) {
            // wait...
        }

        final ImageView hint = (ImageView) mFrameLayout.findViewById(R.id.hint);
        hint.setAnimation(null);
        mWindowManager.removeView(mFrameLayout);

        // Give a second to screenrecord to finish the file
        mHandler.postDelayed(new Runnable() { public void run() {
            mCaptureThread = null;

            final String date = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            final String fileName = "ScreenRec_"
                    + date + ".mp4";
            final File pictures = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
            final File screenrecords = new File(pictures, "Screenrecords");

            if (!screenrecords.exists()) {
                if (!screenrecords.mkdir()) {
                    Log.e(TAG, "Cannot create screenrecords directory");
                    mFinisher.run();
                    return;
                }
            }

            final File input = new File(TMP_PATH);
            final File output = new File(screenrecords, fileName);

            Log.d(TAG, "Copying file to " + output.getAbsolutePath());

            try {
                copyFileUsingStream(input, output);
            } catch (IOException e) {
                Log.e(TAG, "Unable to copy output file", e);
                final Message msg = Message.obtain(mHandler, MSG_TASK_ERROR);
                mHandler.sendMessage(msg);
                return;
            } finally {
                input.delete();
            }

            mFileSize = output.length();

            // Make it appear in gallery, run MediaScanner
            // also make sure to tell media scanner that the tmp file got deleted
            MediaScannerConnectionClient client =
                    new MediaScanner(mContext);
            ((MediaScanner)client).connectAndScan(input.getAbsolutePath(), null);
            ((MediaScanner)client).connectAndScan(output.getAbsolutePath(), date);
        } }, 2000);
    }

    private final class MediaScanner implements MediaScannerConnectionClient {

        private String mFileName;
        private String mDate;
        private MediaScannerConnection mConnection;

        public MediaScanner(Context ctx) {
            mConnection = new MediaScannerConnection(ctx, this);
        }

        @Override
        public void onMediaScannerConnected() {
            mConnection.scanFile(mFileName, null);
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            Log.i(TAG, "MediaScanner done scanning " + path);
            if (mDate != null) {
                showFinalNotification(uri, mDate);
            }
            // disconnect the service to avoid leaks
            mConnection.disconnect();
        }

        public void connectAndScan(String fileName, String date) {
            this.mFileName = fileName;
            this.mDate = date;
            mConnection.connect();
        }
    }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }

    private void showFinalNotification(Uri uri, String date) {
        mNotificationManager.cancel(SCREENRECORD_NOTIFICATION_ID);

        // Create a share intent
        //String subjectDate = DateFormat.getDateTimeInstance().format(new Date(mImageTime));
        String subject = String.format(SCREENRECORD_SHARE_SUBJECT_TEMPLATE, date);
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("video/mp4");
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        PendingIntent shareAction = PendingIntent.getBroadcast(mContext, 0,
                new Intent(mContext, GlobalScreenrecord.ShareReceiver.class)
                        .putExtra(SHARING_INTENT, sharingIntent),
                PendingIntent.FLAG_CANCEL_CURRENT);

        // Create a delete action for the notification
        PendingIntent deleteAction = PendingIntent.getBroadcast(mContext, 0,
                new Intent(mContext, GlobalScreenrecord.DeleteScreenrecordReceiver.class)
                        .putExtra(GlobalScreenrecord.SCREENRECORD_URI_ID, uri.toString()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        final Resources r = mContext.getResources();

        final String totalTime = String.format("%02d:%02dm",
                TimeUnit.MILLISECONDS.toMinutes(mRecordingTotalTime),
                TimeUnit.MILLISECONDS.toSeconds(mRecordingTotalTime) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(mRecordingTotalTime))
        );
        final long size = mFileSize / 1000000;

        Notification.Builder builder = new Notification.Builder(mContext, NotificationChannels.SCREENRECORDS)
            .setTicker(r.getString(R.string.screenrecord_notif_final_ticker))
            .setContentTitle(r.getString(R.string.screenrecord_notif_completed) + " ("
                    + totalTime + ", " + size + "MB" + ")")
            .setSmallIcon(R.drawable.ic_capture_video)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true);
        builder
            .addAction(R.drawable.ic_screenshot_share,
                r.getString(com.android.internal.R.string.share), shareAction)
            .addAction(R.drawable.ic_screenshot_delete,
                r.getString(com.android.internal.R.string.delete), deleteAction);

        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
        launchIntent.setDataAndType(uri, "video/mp4");
        launchIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        builder.setContentIntent(PendingIntent.getActivity(mContext, 0, launchIntent, 0));

        Notification notif = builder.build();
        mNotificationManager.notify(SCREENRECORD_NOTIFICATION_ID, notif);

        mFinisher.run();
    }

    /**
     * Receiver to proxy the share intent.
     */
    public static class ShareReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                ActivityManager.getService().closeSystemDialogs("screenrecord");
            } catch (RemoteException e) {
            }

            Intent sharingIntent = intent.getParcelableExtra(SHARING_INTENT);
            PendingIntent chooseAction = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, GlobalScreenrecord.TargetChosenReceiver.class),
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            Intent chooserIntent = Intent.createChooser(sharingIntent, null,
                    chooseAction.getIntentSender())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setDisallowEnterPictureInPictureWhileLaunching(true);
            context.startActivityAsUser(chooserIntent, opts.toBundle(), UserHandle.CURRENT);
        }
    }

    /**
     * Removes the notification for a screenrecord after a share target is chosen.
     */
    public static class TargetChosenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Clear the notification
            final NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(SCREENRECORD_NOTIFICATION_ID);
        }
    }

    /**
     * Removes the last screenrecord.
     */
    public static class DeleteScreenrecordReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.hasExtra(SCREENRECORD_URI_ID)) {
                return;
            }

            final Uri uri = Uri.parse(intent.getStringExtra(SCREENRECORD_URI_ID));
            // Clear the notification
            final NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(SCREENRECORD_NOTIFICATION_ID);
            // And delete the image from the media store
            new DeleteVideoInBackgroundTask(context).execute(uri);
        }
    }

    /**
    * An AsyncTask that deletes a video from the media store in the background.
    */
    private static class DeleteVideoInBackgroundTask extends AsyncTask<Uri, Void, Void> {
        private Context mContext;

        DeleteVideoInBackgroundTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Uri... params) {
            if (params.length != 1) return null;

            Uri uri = params[0];
            ContentResolver resolver = mContext.getContentResolver();
            resolver.delete(uri, null, null);
            return null;
        }
    }
}
