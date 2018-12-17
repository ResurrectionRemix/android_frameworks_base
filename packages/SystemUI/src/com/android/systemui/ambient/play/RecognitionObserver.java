/*
 * Copyright (C) 2018 CypherOS
 * Copyright (C) 2018 PixelExperience
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package com.android.systemui.ambient.play;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/**
 * Class helping audio fingerprinting for recognition
 */
public class RecognitionObserver implements AmbientIndicationManagerCallback {

    private static final String TAG = "RecognitionObserver";

    private static final int SAMPLE_RATE = 11025;
    private static final short BIT_DEPTH = 16;
    private static final short CHANNELS = 1;
    private static final int bufferSize = SAMPLE_RATE * 11 * 2;
    private static final int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);

    private byte[] mBuffer;
    private AudioRecord mRecorder;

    private RecorderThread mRecThread;
    private boolean isRecording = false;
    private AmbientIndicationManager mManager;
    private boolean isRecognitionEnabled;

    RecognitionObserver(Context context, AmbientIndicationManager manager) {
        this.mManager = manager;
        manager.registerCallback(this);
    }

    @Override
    public void onRecognitionResult(Observable observed) {

    }

    @Override
    public void onRecognitionNoResult() {

    }

    @Override
    public void onRecognitionError() {

    }

    @Override
    public void onSettingsChanged(String key, boolean newValue) {
        if (key.equals(Settings.System.AMBIENT_RECOGNITION)) {
            isRecognitionEnabled = newValue;
            if (!isRecognitionEnabled) {
                if (mManager.DEBUG)
                    Log.d(TAG, "Recognition disabled, stopping all and triggering dispatchRecognitionNoResult");
                stopRecording();
                mManager.dispatchRecognitionNoResult();
            }
        }
    }

    /**
     * Class storing fingerprinting results
     */
    public static class Observable {

        public String Artist;
        public String Song;

        @Override
        public String toString() {
            return Song + " by " + Artist;
        }
    }

    /**
     * Helper thread class to record the data to send
     */
    private class RecorderThread extends Thread {
        private boolean mResultGiven = false;
        private byte[] buffCopy = new byte[SAMPLE_RATE * 10 * 2];

        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (mManager.DEBUG) Log.d(TAG, "Started reading recorder...");

            while (isRecording && mBuffer != null) {
                int read = 0;
                synchronized (this) {
                    if (!isRecognitionEnabled) {
                        break;
                    }
                    if (mRecorder != null) {
                        read = mRecorder.read(mBuffer, 0, mBuffer.length);
                        if (read == AudioRecord.ERROR_BAD_VALUE) {
                            if (mManager.DEBUG) Log.d(TAG, "BAD_VALUE while reading recorder");
                            break;
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                            if (mManager.DEBUG)
                                Log.d(TAG, "INVALID_OPERATION while reading recorder");
                            break;
                        } else if (read >= 0) {
                            // Copy recording to a new array before StopRecording is called, because we are clearing the mBuffer there.
                            System.arraycopy(mBuffer, 0, buffCopy, 0, buffCopy.length);
                        }
                    }
                    if (!isRecognitionEnabled) {
                        break;
                    }
                }
            }

            tryMatchCurrentBuffer();

            if (mManager.DEBUG)
                Log.d(TAG, "Broke out of recording loop, mResultGiven=" + mResultGiven);
        }

        private void tryMatchCurrentBuffer() {
            if (!isRecognitionEnabled) {
                stopRecording();
                return;
            }
            if (!isRecording) {
                new Thread() {
                    public void run() {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        String requestResult;
                        try {
                            ByteArrayOutputStream finalBuffer = new ByteArrayOutputStream();
                            WaveFormat header = new WaveFormat(WaveFormat.FORMAT_PCM, CHANNELS, SAMPLE_RATE, BIT_DEPTH, buffCopy.length);
                            header.write(finalBuffer);
                            finalBuffer.write(buffCopy);
                            finalBuffer.close();
                            requestResult = AuddApi.sendRequest(mManager, finalBuffer.toByteArray());
                        } catch (Exception e) {
                            if (mManager.DEBUG) e.printStackTrace();
                            requestResult = null;
                        }
                        parseResult(requestResult);
                    }
                }.start();
            } else {
                if (mManager.DEBUG) Log.e(TAG, "0 bytes recorded!?");
            }
        }

        private void parseResult(String result) {
            if (!isRecognitionEnabled || result == null) {
                reportResult(null);
                return;
            }
            // Return result
            if (mManager.DEBUG) Log.d(TAG, "Parsing result: " + result);
            Observable observed = new Observable();
            try {
                JSONObject jsonResult = new JSONObject(result);
                if (!jsonResult.isNull("status") && jsonResult.getString("status").equals("success") && !jsonResult.isNull("result")) {
                    observed.Artist = jsonResult.getJSONObject("result").getString("artist");
                    observed.Song = jsonResult.getJSONObject("result").getString("title");
                    if (mManager.DEBUG) Log.d(TAG, "Got a match: " + observed);
                } else {
                    if (mManager.DEBUG) Log.d(TAG, "No match (Maybe we could not hear the song?)");
                }
            } catch (Exception e) {
                if (mManager.DEBUG) e.printStackTrace();
            }
            reportResult(observed);
        }

        private boolean isNullResult(Observable observed) {
            return observed == null || observed.Artist == null || observed.Song == null;
        }

        private void reportResult(Observable observed) {
            stopRecording();
            // If the recording is still active and we have no match, don't do anything. Otherwise,
            // report the result.
            if (!isRecognitionEnabled || isNullResult(observed)) {
                if (mManager.DEBUG) Log.d(TAG, "Reporting onNoMatch");
                mManager.dispatchRecognitionNoResult();
            } else {
                if (mManager.DEBUG) Log.d(TAG, "Reporting result");
                mResultGiven = true;
                mManager.dispatchRecognitionResult(observed);
            }
        }
    }

    void startRecording() {
        if (!isRecognitionEnabled || isRecording) {
            return;
        }
        isRecording = true;
        // Only start recording audio if we have internet connectivity.
        if (mManager.getNetworkStatus() != -1) {
            new Thread() {
                @Override
                public void run() {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    try {
                        try {
                            // Make sure buffer is cleared before recording starts.
                            mBuffer = new byte[bufferSize];
                            mRecorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufSize);
                            mRecThread = new RecorderThread();
                            mRecorder.startRecording();
                            mRecThread.start();
                        } catch (Exception e) {
                            if (mManager.DEBUG)
                                Log.d(TAG, "Cannot start recording for recognition", e);
                            mManager.dispatchRecognitionError();
                        }
                        Thread.currentThread().sleep(mManager.getRecordingMaxTime());
                    } catch (Exception e2) {
                        mManager.dispatchRecognitionError();
                    }
                    // Stop recording, process audio and post result.
                    stopRecording();
                }
            }.start();
        } else {
            if (mManager.DEBUG) Log.d(TAG, "No connectivity, triggering dispatchRecognitionError");
            stopRecording();
            mManager.dispatchRecognitionError();
        }
    }

    private void stopRecording() {
        isRecording = false;
        if (mRecorder != null && mRecorder.getState() == AudioRecord.STATE_INITIALIZED && mRecThread != null && mRecThread.isAlive()) {
            try {
                if (mManager.DEBUG) Log.d(TAG, "Stopping recorder");
                mRecorder.stop();
                // Don't forget to release the native resources.
                mRecorder.release();
                mRecorder = null;
                mRecThread = null;
            } catch (Exception e) {
                if (mManager.DEBUG) Log.e(TAG, "Exception occured ", e);
            }
        }
    }
}
