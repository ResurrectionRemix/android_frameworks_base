/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.screenrecord;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Icon;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A service which records the device screen and optionally microphone input.
 */
public class RecordingService extends Service {
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "screen_record";
    private static final String EXTRA_RESULT_CODE = "extra_resultCode";
    private static final String EXTRA_DATA = "extra_data";
    private static final String EXTRA_PATH = "extra_path";
    private static final String EXTRA_AUDIO_SOURCE = "extra_audioSource";
    private static final String EXTRA_SHOW_TAPS = "extra_showTaps";
    private static final String EXTRA_SHOW_DOT = "extra_showDot";
    private static final String EXTRA_VIDEO_BITRATE = "extra_videoBitrate";
    private static final String EXTRA_SCREEN_OFF = "extra_screenoff";
    private static final int REQUEST_CODE = 2;

    private static final String ACTION_START = "com.android.systemui.screenrecord.START";
    private static final String ACTION_STOP = "com.android.systemui.screenrecord.STOP";
    private static final String ACTION_PAUSE = "com.android.systemui.screenrecord.PAUSE";
    private static final String ACTION_RESUME = "com.android.systemui.screenrecord.RESUME";
    private static final String ACTION_CANCEL = "com.android.systemui.screenrecord.CANCEL";
    private static final String ACTION_SHARE = "com.android.systemui.screenrecord.SHARE";
    private static final String ACTION_DELETE = "com.android.systemui.screenrecord.DELETE";

    private static final int AUDIO_BIT_RATE = 128000;
    private static final int SAMPLES_PER_FRAME = 1024;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static int TOTAL_NUM_TRACKS = 1;
    private static int AUDIO_CHANNEL_TYPE = AudioFormat.CHANNEL_IN_MONO;
    private static int VIDEO_FRAME_RATE;
    private static int VIDEO_BIT_RATE;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private Surface mInputSurface;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private MediaMuxer mMuxer;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mVideoBufferInfo;
    private int mAudioBufferBytes;
    private AudioRecord mInternalAudio;
    private boolean mMuxerStarted = false;
    private boolean mPausedRecording = false;
    private boolean mAudioRecording;
    private boolean mAudioEncoding;
    private boolean mVideoEncoding;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private final Object mMuxerLock = new Object();
    private final Object mAudioEncoderLock = new Object();
    private final Object mWriteVideoLock = new Object();
    private final Object mWriteAudioLock = new Object();
    private Notification.Builder mRecordingNotificationBuilder;

    private boolean mIsLowRamEnabled;
    private boolean mShowTaps;
    private boolean mShowDot;
    private boolean mScreenOff;
    private boolean mIsDotAtRight;
    private boolean mDotShowing;
    private int mVideoBitrateOpt;
    private int mAudioSourceOpt;
    private FrameLayout mFrameLayout;
    private LayoutInflater mInflater;
    private WindowManager mWindowManager;
    private File mTempFile;
    private boolean mIsRecording = false;

    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onScreenTurnedOff() {
              if (mIsRecording) {
                  if (mScreenOff)  {
                     try {
                         NotificationManager notificationManager =
                         (NotificationManager) getSystemService(
                         Context.NOTIFICATION_SERVICE);
                         stopRecording();
                         saveRecording(notificationManager);
                      } catch (Exception e) {
                        Log.e(TAG, "Unable to save recording because: "
                         + e.getMessage());
                      }
                  }
              }
         }
    };

    /**
     * Get an intent to start the recording service.
     *
     * @param context    Context from the requesting activity
     * @param resultCode The result code from {@link android.app.Activity#onActivityResult(int, int,
     *                   android.content.Intent)}
     * @param data       The data from {@link android.app.Activity#onActivityResult(int, int,
     *                   android.content.Intent)}
     * @param useAudio   True to enable microphone input while recording
     * @param showTaps   True to make touches visible while recording
     */
    public static Intent getStartIntent(Context context, int resultCode, Intent data,
            int audioSourceOpt, boolean showTaps, boolean showDot, int vidBitrateOpt, boolean screenOff) {
        return new Intent(context, RecordingService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_AUDIO_SOURCE, audioSourceOpt)
                .putExtra(EXTRA_SHOW_TAPS, showTaps)
                .putExtra(EXTRA_SHOW_DOT, showDot)
                .putExtra(EXTRA_VIDEO_BITRATE, vidBitrateOpt)
                .putExtra(EXTRA_SCREEN_OFF, screenOff);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return Service.START_NOT_STICKY;
        }
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand " + action);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        switch (action) {
            case ACTION_START:
                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
                mAudioSourceOpt = intent.getIntExtra(EXTRA_AUDIO_SOURCE, 0 /* disabled*/);
                mShowTaps = intent.getBooleanExtra(EXTRA_SHOW_TAPS, false);
                mShowDot = intent.getBooleanExtra(EXTRA_SHOW_DOT, false);
                mVideoBitrateOpt = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 2);
                mScreenOff = intent.getBooleanExtra(EXTRA_SCREEN_OFF, false);

                Intent data = intent.getParcelableExtra(EXTRA_DATA);
                if (data != null) {
                    mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
                    final Handler h = new Handler(Looper.getMainLooper());
                    h.postDelayed(() -> {
                        startRecording();
                    }, 500);
                }
                break;

            case ACTION_CANCEL:
                stopRecording();

                // Delete temp file
                if (!mTempFile.delete()) {
                    Log.e(TAG, "Error canceling screen recording!");
                    Toast.makeText(this, R.string.screenrecord_delete_error, Toast.LENGTH_LONG)
                            .show();
                } else {
                    Toast.makeText(this, R.string.screenrecord_cancel_success, Toast.LENGTH_LONG)
                            .show();
                }

                // Close quick shade
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                break;

            case ACTION_STOP:
                stopRecording();
                saveRecording(notificationManager);
                break;

            case ACTION_PAUSE:
                if (mAudioSourceOpt != 2) {
                    mMediaRecorder.pause();
                } else {
                    mPausedRecording = true;
                }
                setNotificationActions(true, notificationManager);
                break;

            case ACTION_RESUME:
                if (mAudioSourceOpt != 2) {
                    mMediaRecorder.resume();
                } else {
                    mPausedRecording = false;
                }
                setNotificationActions(false, notificationManager);
                break;

            case ACTION_SHARE:
                Uri shareUri = Uri.parse(intent.getStringExtra(EXTRA_PATH));

                Intent shareIntent = new Intent(Intent.ACTION_SEND)
                        .setType("video/mp4")
                        .putExtra(Intent.EXTRA_STREAM, shareUri);
                String shareLabel = getResources().getString(R.string.screenrecord_share_label);

                // Close quick shade
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

                // Remove notification
                notificationManager.cancel(NOTIFICATION_ID);

                startActivity(Intent.createChooser(shareIntent, shareLabel)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case ACTION_DELETE:
                // Close quick shade
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

                ContentResolver resolver = getContentResolver();
                Uri uri = Uri.parse(intent.getStringExtra(EXTRA_PATH));
                resolver.delete(uri, null, null);

                Toast.makeText(
                        this,
                        R.string.screenrecord_delete_description,
                        Toast.LENGTH_LONG).show();

                // Remove notification
                notificationManager.cancel(NOTIFICATION_ID);
                Log.d(TAG, "Deleted recording " + uri);
                break;
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mInflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mWindowManager =
                (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(getApplicationContext());
        mUpdateMonitor.registerCallback(mMonitorCallback);
    }


    /**
     * Begin the recording session
     */
    private void startRecording() {
        try {
            try {
                mTempFile = File.createTempFile("temp", ".mp4");
                Log.d(TAG, "Writing video output to: " + mTempFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Check if the device allows to use h265 for lighter recordings
            boolean useH265 = getResources().getBoolean(R.bool.config_useNewScreenRecEncoder);

            // Set initial resources
            DisplayMetrics metrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            mIsLowRamEnabled = SystemProperties.get("ro.config.low_ram").equals("true");

            switch (mVideoBitrateOpt) {
                case 1:
                    VIDEO_BIT_RATE = mIsLowRamEnabled ? 8388608 : 15728640;
                    break;
                case 2:
                    VIDEO_BIT_RATE = mIsLowRamEnabled ? 6291456 : 10485760;
                    break;
                case 3:
                    VIDEO_BIT_RATE = mIsLowRamEnabled ? 4194304 : 5242880;
                    break;
                case 4:
                    VIDEO_BIT_RATE = 1048576;
                    break;
                case 0:
                    VIDEO_BIT_RATE = mIsLowRamEnabled ? 10485760 : 20971520;
                    break;
                default:
                    VIDEO_BIT_RATE = 6000000;
                    break;
            }

            if (mVideoBitrateOpt > 2) {
                VIDEO_FRAME_RATE = mIsLowRamEnabled ? 30 : 60;
                if (!mIsLowRamEnabled) {
                    TOTAL_NUM_TRACKS = 2;
                    AUDIO_CHANNEL_TYPE = AudioFormat.CHANNEL_IN_STEREO;
                }
            } else {
                VIDEO_FRAME_RATE = mIsLowRamEnabled ? 25 : 48;
                if (!mIsLowRamEnabled) {
                    TOTAL_NUM_TRACKS = 1;
                    AUDIO_CHANNEL_TYPE = AudioFormat.CHANNEL_IN_MONO;
                }
            }

            setTapsVisible(mShowTaps);
            if (mShowDot) {
                showDot();
            }

            // Reving up those recorders
            boolean useOldEncoder = mIsLowRamEnabled || !useH265;
            switch (mAudioSourceOpt) {
                case 2:
                    mVideoBufferInfo = new MediaCodec.BufferInfo();
                    mAudioBufferBytes =  AudioRecord.getMinBufferSize(
                        AUDIO_SAMPLE_RATE,
                        AUDIO_CHANNEL_TYPE,
                        AudioFormat.ENCODING_PCM_16BIT);
                    // Preparing video encoder
                    MediaFormat videoFormat = MediaFormat.createVideoFormat(useOldEncoder ? "video/avc" : "video/hevc", screenWidth, screenHeight);
                    videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                    videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE);
                    videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE);
                    videoFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, VIDEO_FRAME_RATE);
                    videoFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / VIDEO_FRAME_RATE);
                    videoFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
                    videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                    mVideoEncoder = MediaCodec.createEncoderByType(useOldEncoder ? "video/avc" : "video/hevc");
                    mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    // Preparing audio encoder
                    MediaFormat mAudioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", AUDIO_SAMPLE_RATE, TOTAL_NUM_TRACKS);
                    mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                    mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
                    mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
                    mAudioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
                    mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    int bufferSize = SAMPLES_PER_FRAME * VIDEO_FRAME_RATE;
                    if (bufferSize < mAudioBufferBytes)
                        bufferSize = ((mAudioBufferBytes / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
                    mMuxer = new MediaMuxer(mTempFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    // Preparing internal recorder
                    AudioPlaybackCaptureConfiguration internalAudioConfig = 
                    new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build();
                    mInternalAudio = new AudioRecord.Builder()
                        .setAudioFormat(
                            new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(AUDIO_SAMPLE_RATE)
                                .setChannelMask(AUDIO_CHANNEL_TYPE)
                                .build())
                        .setAudioPlaybackCaptureConfig(internalAudioConfig)
                        .build();
                    mInputSurface = mVideoEncoder.createInputSurface();
                    break;

                default:
                    mMediaRecorder = new MediaRecorder();
                    if (mAudioSourceOpt == 1) mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mMediaRecorder.setVideoEncoder(useOldEncoder ? MediaRecorder.VideoEncoder.H264 : MediaRecorder.VideoEncoder.HEVC);
                    mMediaRecorder.setVideoSize(screenWidth, screenHeight);
                    mMediaRecorder.setVideoFrameRate(VIDEO_FRAME_RATE);
                    mMediaRecorder.setVideoEncodingBitRate(VIDEO_BIT_RATE);
                    if (mAudioSourceOpt == 1) {
                        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        mMediaRecorder.setAudioChannels(TOTAL_NUM_TRACKS);
                        mMediaRecorder.setAudioEncodingBitRate(AUDIO_BIT_RATE);
                        mMediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE);
                    }
                    mMediaRecorder.setOutputFile(mTempFile);
                    mMediaRecorder.prepare();
                    mInputSurface = mMediaRecorder.getSurface();
                    break;
            }

            // Create surface
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "Recording Display",
                    screenWidth,
                    screenHeight,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mInputSurface,
                    null,
                    null);

            // Let's get ready to record now
            switch (mAudioSourceOpt) {
                case 2:
                    // Start the encoders
                    mVideoEncoder.start();
                    new Thread(new VideoEncoderTask(), "VideoEncoderTask").start();
                    mAudioEncoder.start();
                    new Thread(new AudioEncoderTask(), "AudioEncoderTask").start();
                    mInternalAudio.startRecording();
                    mAudioRecording = true;
                    new Thread(new AudioRecorderTask(), "AudioRecorderTask").start();
                    break;

                default:
                    mMediaRecorder.start();
                    break;
            }

            ///mMediaRecorder.start();
        } catch (IOException e) {
            Log.e(TAG, "Error starting screen recording: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        mIsRecording = true;
        createRecordingNotification();
    }

    private void createRecordingNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screenrecord_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.screenrecord_channel_description));
        channel.enableVibration(true);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);

        mRecordingNotificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(getResources().getString(R.string.screenrecord_name))
                .setUsesChronometer(true)
                .setOngoing(true);
        setNotificationActions(false, notificationManager);
        Notification notification = mRecordingNotificationBuilder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void setNotificationActions(boolean isPaused, NotificationManager notificationManager) {
        String pauseString = getResources()
                .getString(isPaused ? R.string.screenrecord_resume_label
                        : R.string.screenrecord_pause_label);
        Intent pauseIntent = isPaused ? getResumeIntent(this) : getPauseIntent(this);

        mRecordingNotificationBuilder.setActions(
                new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_screenrecord),
                        getResources().getString(R.string.screenrecord_stop_label),
                        getStopPendingIntent())
                        .build(),
                new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_screenrecord), pauseString,
                        PendingIntent.getService(this, REQUEST_CODE, pauseIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build(),
                new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_screenrecord),
                        getResources().getString(R.string.screenrecord_cancel_label),
                        PendingIntent
                                .getService(this, REQUEST_CODE, getCancelIntent(this),
                                        PendingIntent.FLAG_UPDATE_CURRENT))
                        .build());
        notificationManager.notify(NOTIFICATION_ID, mRecordingNotificationBuilder.build());
    }

    private Notification createSaveNotification(Uri uri) {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setDataAndType(uri, "video/mp4");

        Notification.Action shareAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_screenrecord),
                getResources().getString(R.string.screenrecord_share_label),
                PendingIntent.getService(
                        this,
                        REQUEST_CODE,
                        getShareIntent(this, uri.toString()),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .build();

        Notification.Action deleteAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_screenrecord),
                getResources().getString(R.string.screenrecord_delete_label),
                PendingIntent.getService(
                        this,
                        REQUEST_CODE,
                        getDeleteIntent(this, uri.toString()),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .build();

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(getResources().getString(R.string.screenrecord_name))
                .setContentText(getResources().getString(R.string.screenrecord_save_message))
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        REQUEST_CODE,
                        viewIntent,
                        PendingIntent.FLAG_IMMUTABLE))
                .addAction(shareAction)
                .addAction(deleteAction)
                .setAutoCancel(true);

        // Add thumbnail if available
        Bitmap thumbnailBitmap = null;
        try {
            ContentResolver resolver = getContentResolver();
            Size size = Point.convert(MediaStore.ThumbnailConstants.MINI_SIZE);
            thumbnailBitmap = resolver.loadThumbnail(uri, size, null);
        } catch (IOException e) {
            Log.e(TAG, "Error creating thumbnail: " + e.getMessage());
            e.printStackTrace();
        }
        if (thumbnailBitmap != null) {
            Notification.BigPictureStyle pictureStyle = new Notification.BigPictureStyle()
                    .bigPicture(thumbnailBitmap)
                    .bigLargeIcon((Bitmap) null);
            builder.setLargeIcon(thumbnailBitmap).setStyle(pictureStyle);
        }
        return builder.build();
    }

    private void stopRecording() {
        setTapsVisible(false);
        if (mDotShowing) {
            stopDot();
        }
        try {
            switch (mAudioSourceOpt) {
                case 2:
                    mAudioRecording = false;
                    mAudioEncoding = false;
                    mVideoEncoding = false;
                    break;

                default:
                    mMediaRecorder.stop();
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                    mMediaProjection.stop();
                    mMediaProjection = null;
                    mInputSurface.release();
                    mVirtualDisplay.release();
                    break;
            }
        } catch (Exception e) {}
        stopSelf();
        mIsRecording = false;
    }

    private void saveRecording(NotificationManager notificationManager) {
        String fileName = new SimpleDateFormat("'ScreenRecord-'yyyyMMdd-HHmmss'.mp4'")
                .format(new Date());

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis());
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());

        ContentResolver resolver = getContentResolver();
        Uri collectionUri = MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = resolver.insert(collectionUri, values);

        try {
            // Add to the mediastore
            OutputStream os = resolver.openOutputStream(itemUri, "w");
            Files.copy(mTempFile.toPath(), os);
            os.close();

            Notification notification = createSaveNotification(itemUri);
            notificationManager.notify(NOTIFICATION_ID, notification);

            mTempFile.delete();
        } catch (Exception e) {
            Log.e(TAG, "Error saving screen recording: " + e.getMessage());
            Toast.makeText(this, R.string.screenrecord_delete_error, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void setTapsVisible(boolean turnOn) {
        int value = turnOn ? 1 : 0;
        Settings.System.putInt(getApplicationContext().getContentResolver(),
                Settings.System.SHOW_TOUCHES, value);
    }

    private void showDot() {
        mDotShowing = true;
        mIsDotAtRight = true;
        final int size = (int) (this.getResources()
                .getDimensionPixelSize(R.dimen.screenrecord_dot_size));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // don't get softkey inputs
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // allow outside inputs
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.width = size;
        params.height = size;

        mFrameLayout = new FrameLayout(this);

        mWindowManager.addView(mFrameLayout, params);
        mInflater.inflate(R.layout.screenrecord_dot, mFrameLayout);

        final ImageView dot = (ImageView) mFrameLayout.findViewById(R.id.dot);
        dot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    getStopPendingIntent().send();
                } catch (PendingIntent.CanceledException e) {}
            }
        });

        dot.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                dot.setAnimation(null);
                WindowManager.LayoutParams params =
                        (WindowManager.LayoutParams) mFrameLayout.getLayoutParams();
                params.gravity = Gravity.TOP | (mIsDotAtRight? Gravity.LEFT : Gravity.RIGHT);
                mIsDotAtRight = !mIsDotAtRight;
                mWindowManager.updateViewLayout(mFrameLayout, params);
                dot.startAnimation(getDotAnimation());
                return true;
            }
        });

        dot.startAnimation(getDotAnimation());
    }

    private void stopDot() {
        mDotShowing = false;
        final ImageView dot = (ImageView) mFrameLayout.findViewById(R.id.dot);
        if (dot != null) {
            dot.setAnimation(null);
            mWindowManager.removeView(mFrameLayout);
        }
    }

    private Animation getDotAnimation() {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500);
        anim.setStartOffset(100);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        return anim;
    }

    private PendingIntent getStopPendingIntent() {
        return PendingIntent.getService(this, REQUEST_CODE, getStopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Intent getStopIntent(Context context) {
        return new Intent(context, RecordingService.class).setAction(ACTION_STOP);
    }

    private static Intent getPauseIntent(Context context) {
        return new Intent(context, RecordingService.class).setAction(ACTION_PAUSE);
    }

    private static Intent getResumeIntent(Context context) {
        return new Intent(context, RecordingService.class).setAction(ACTION_RESUME);
    }

    private static Intent getCancelIntent(Context context) {
        return new Intent(context, RecordingService.class).setAction(ACTION_CANCEL);
    }

    private static Intent getShareIntent(Context context, String path) {
        return new Intent(context, RecordingService.class).setAction(ACTION_SHARE)
                .putExtra(EXTRA_PATH, path);
    }

    private static Intent getDeleteIntent(Context context, String path) {
        return new Intent(context, RecordingService.class).setAction(ACTION_DELETE)
                .putExtra(EXTRA_PATH, path);
    }

    private class AudioRecorderTask implements Runnable {
        ByteBuffer inputBuffer;
        int readResult;

        @Override
        public void run() {
            long audioPresentationTimeNs;
            byte[] mTempBuffer = new byte[SAMPLES_PER_FRAME];
            while (mAudioRecording) {
                if (!mPausedRecording) {
                    audioPresentationTimeNs = System.nanoTime();
                    readResult = mInternalAudio.read(mTempBuffer, 0, SAMPLES_PER_FRAME);
                    if(readResult == AudioRecord.ERROR_BAD_VALUE || readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                        continue;
                    }
                    // send current frame data to encoder
                    try {
                        synchronized (mAudioEncoderLock) {
                            if (mAudioEncoding) {
                                int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
                                if (inputBufferIndex >= 0) {
                                    inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                                    inputBuffer.clear();
                                    inputBuffer.put(mTempBuffer);

                                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, mTempBuffer.length, audioPresentationTimeNs / 1000, 0);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
            // finished recording -> send it to the encoder
            audioPresentationTimeNs = System.nanoTime();
            readResult = mInternalAudio.read(mTempBuffer, 0, SAMPLES_PER_FRAME);
            if (readResult == AudioRecord.ERROR_BAD_VALUE
                || readResult == AudioRecord.ERROR_INVALID_OPERATION)
            // send current frame data to encoder
            try {
                synchronized (mAudioEncoderLock) {
                    if (mAudioEncoding) {
                        int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
                        if (inputBufferIndex >= 0) {
                            inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                            inputBuffer.clear();
                            inputBuffer.put(mTempBuffer);
                            mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, mTempBuffer.length, audioPresentationTimeNs / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            mInternalAudio.stop();
            mInternalAudio.release();
            mInternalAudio = null;
        }
    }

    // Encoders tasks to do both screen capture and audio recording
    private class VideoEncoderTask implements Runnable {
        private MediaCodec.BufferInfo videoBufferInfo;

        @Override
        public void run(){
            mVideoEncoding = true;
            videoTrackIndex = -1;
            videoBufferInfo = new MediaCodec.BufferInfo();
            while(mVideoEncoding){
                if (!mPausedRecording) {
                    int bufferIndex = mVideoEncoder.dequeueOutputBuffer(videoBufferInfo, 10);
                    if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // nothing available yet
                    } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // should happen before receiving buffers, and should only happen once
                        if (videoTrackIndex >= 0) {
                            throw new RuntimeException("format changed twice");
                        }
                        synchronized (mMuxerLock) {
                            videoTrackIndex = mMuxer.addTrack(mVideoEncoder.getOutputFormat());

                            if (!mMuxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                                mMuxer.start();
                                mMuxerStarted = true;
                            }
                        }
                    } else if (bufferIndex < 0) {
                        // not sure what's going on, ignore it
                    } else {
                        ByteBuffer videoData = mVideoEncoder.getOutputBuffer(bufferIndex);
                        if (videoData == null) {
                            throw new RuntimeException("couldn't fetch buffer at index " + bufferIndex);
                        }
                        if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            videoBufferInfo.size = 0;
                        }
                        if (videoBufferInfo.size != 0) {
                            if (mMuxerStarted) {
                                videoData.position(videoBufferInfo.offset);
                                videoData.limit(videoBufferInfo.offset + videoBufferInfo.size);
                                synchronized (mWriteVideoLock) {
                                    if (mMuxerStarted) {
                                        mMuxer.writeSampleData(videoTrackIndex, videoData, videoBufferInfo);
                                    }
                                }
                            } else {
                                // muxer not started
                            }
                        }
                        mVideoEncoder.releaseOutputBuffer(bufferIndex, false);
                        if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            mVideoEncoding = false;
                            break;
                        }
                    }
                }
            }
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;

            if (mInputSurface != null) {
                mInputSurface.release();
                mInputSurface = null;
            }
            if (mMediaProjection != null) {
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            synchronized (mWriteAudioLock) {
                synchronized (mMuxerLock) {
                    if (mMuxer != null) {
                        if (mMuxerStarted) {
                            mMuxer.stop();
                        }
                        mMuxer.release();
                        mMuxer = null;
                        mMuxerStarted = false;
                    }
                }
            }
        }
    }

    private class AudioEncoderTask implements Runnable {
        private MediaCodec.BufferInfo audioBufferInfo;

        @Override
        public void run(){
            mAudioEncoding = true;
            audioTrackIndex = -1;
            audioBufferInfo = new MediaCodec.BufferInfo();
            while(mAudioEncoding){
                if (!mPausedRecording) {
                    int bufferIndex = mAudioEncoder.dequeueOutputBuffer(audioBufferInfo, 10);
                    if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                    } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // should happen before receiving buffers, and should only happen once
                        if (audioTrackIndex >= 0) {
                            throw new RuntimeException("format changed twice");
                        }
                        synchronized (mMuxerLock) {
                            audioTrackIndex = mMuxer.addTrack(mAudioEncoder.getOutputFormat());

                            if (!mMuxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                                mMuxer.start();
                                mMuxerStarted = true;
                            }
                        }
                    } else if (bufferIndex < 0) {
                        // let's ignore it
                    } else {
                        if (mMuxerStarted && audioTrackIndex >= 0) {
                            ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(bufferIndex);
                            if (encodedData == null) {
                                throw new RuntimeException("encoderOutputBuffer " + bufferIndex + " was null");
                            }
                            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                // The codec config data was pulled out and fed to the muxer when we got
                                // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
                                audioBufferInfo.size = 0;
                            }
                            if (audioBufferInfo.size != 0) {
                                if (mMuxerStarted) {
                                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                                    encodedData.position(audioBufferInfo.offset);
                                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size);
                                    synchronized (mWriteAudioLock) {
                                        if (mMuxerStarted) {
                                            mMuxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo);
                                        }
                                    }
                                }
                            }
                            mAudioEncoder.releaseOutputBuffer(bufferIndex, false);
                            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                // reached EOS
                                mAudioEncoding = false;
                                break;
                            }
                        }
                    }
                }
            }

            synchronized (mAudioEncoderLock) {
                mAudioEncoder.stop();
                mAudioEncoder.release();
                mAudioEncoder = null;
            }

            synchronized (mWriteVideoLock) {
                synchronized (mMuxerLock) {
                    if (mMuxer != null) {
                        if (mMuxerStarted) {
                            mMuxer.stop();
                        }
                        mMuxer.release();
                        mMuxer = null;
                        mMuxerStarted = false;
                    }
                }
            }
        }
    }
}

