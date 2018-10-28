/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 * Copyright (C) 2018 CypherOS
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

import android.ambient.AmbientIndicationManager;
import android.ambient.play.RecoginitionObserver;
import android.ambient.play.WaveFormat;
import android.content.Context;
import android.media.AudioRecord;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class helping audio fingerprinting for recognition
 */
public class RecoginitionObserverFactory extends RecoginitionObserver {

    private RecorderThread mRecThread;
    boolean isRecording = false;

    public RecoginitionObserverFactory(Context context) {
        super(context);
    }

    /**
     * Helper thread class to record the data to send
     */
    private class RecorderThread extends Thread {
        private boolean mResultGiven = false;
        private byte[] buffCopy = new byte[SAMPLE_RATE * 10 * 2];

        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            Log.d(TAG, "Started reading recorder...");

            while (isRecording && mBuffer != null) {
                int read = 0;
                synchronized (this) {
                    if (mRecorder != null) {
                        read = mRecorder.read(mBuffer, 0, mBuffer.length);
                        if (read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.d(TAG, "BAD_VALUE while reading recorder");
                            break;
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.d(TAG, "INVALID_OPERATION while reading recorder");
                            break;
                        } else if (read >= 0) {
                            // Copy recording to a new array before StopRecording is called, because we are clearing the mBuffer there.
                            System.arraycopy(mBuffer, 0, buffCopy, 0, buffCopy.length);
                        }
                    }
                }

                // if (read >= 0) {
                //     if (mBufferIndex > 10) {
                //         mManager.dispatchRecognitionAudio((float) computeAverageAmplitude(mBuffer, mBufferIndex - 10, 4));
                //     }
                //     tryMatchCurrentBuffer();
                // }
            }

            tryMatchCurrentBuffer();

            Log.d(TAG, "Broke out of recording loop, mResultGiven=" + mResultGiven);
        }

        private double computeAverageAmplitude(byte[] buffer, int startSample, int numSamples) {
            // Assuming 16 bits depth
            double sum = 0.0f;
            for (int i = 0; i < numSamples; ++i) {
                sum += computeAmplitude(buffer, startSample + i * 2);
            }

            return sum / ((double) numSamples);
        }

        private double computeAmplitude(byte[] buffer, int sample) {
            // Assuming 16 bits depth
            int amplitude = (buffer[sample] & 0xff) << 8 | buffer[sample + 1];
            // decibel: return 20.0 * Math.log10((double)Math.abs(amplitude) / 32768.0);
            return amplitude / 65536.0;
        }

        public void tryMatchCurrentBuffer() {
            if (!mRecognitionEnabled) return;
            if (!isRecording) {
                new Thread() {
                    public void run() {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                        // Allow only one upload call at a time
                        String output_xml = sendAudioData(buffCopy, buffCopy.length);
                        parseXmlResult(output_xml);
                    }
                }.start();
            } else {
                Log.e(TAG, "0 bytes recorded!?");
            }
        }

        private String sendAudioData(byte[] inputBuffer, int length) {
            Log.d(TAG, "Preparing to send audio data: " + length + " bytes");
            try {
                URL url = new URL("http://search.midomi.com:443/v2/?method=search&type=identify");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.addRequestProperty("User-Agent", USER_AGENT);
                conn.addRequestProperty("Content-Type", MIME_TYPE);
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(10000);

                // Write the WAVE audio header, then the PCM data
                Log.d(TAG, "Sending mic data, " + length + " bytes...");
                WaveFormat header = new WaveFormat(WaveFormat.FORMAT_PCM, CHANNELS,
                        SAMPLE_RATE, BIT_DEPTH, length);
                header.write(conn.getOutputStream());
                conn.getOutputStream().write(inputBuffer, 0, length);

                InputStream is = conn.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                StringBuilder sb = new StringBuilder();
                while ((read = is.read(buffer)) > 0) {
                    sb.append(new String(buffer, 0, read));
                }

                return sb.toString();
            } catch (IOException e) {
                Log.d(TAG, "Error while sending audio data", e);
            }

            return "";
        }

        private void parseXmlResult(String xml) {
            if (xml.contains("did not hear any music") || xml.contains("no close matches")) {
                // No result
                Log.d(TAG, "No match (Maybe we could not hear the song?)");
                reportResult(null);
            } else {
                // Return result where everything is fine
                Observable observed = new Observable();

                Pattern data_re = Pattern.compile("<track .*?artist_name=\"(.*?)\".*?album_name=\"(.*?)\".*?track_name=\"(.*?)\".*?album_primary_image=\"(.*?)\".*?>",
                        Pattern.DOTALL | Pattern.MULTILINE);
                Matcher match = data_re.matcher(xml.replaceAll("\n", ""));

                if (match.find()) {
                    observed.Artist = match.group(1);
                    observed.Album = match.group(2);
                    observed.Song = match.group(3);
                    observed.ArtworkUrl = match.group(4);
                    Log.d(TAG, "Got a match! " + observed);
                } else {
                    Log.d(TAG, "Regular expression didn't match!");
                    reportResult(null);
                }
                reportResult(observed);
            }
        }

        private void reportResult(Observable observed) {
            // If the recording is still active and we have no match, don't do anything. Otherwise,
            // report the result.
            if (mRecorder == null && observed == null) {
                Log.d(TAG, "Reporting onNoMatch");
                mManager.dispatchRecognitionNoResult();
            } else if (observed != null) {
                Log.d(TAG, "Reporting result");
                mResultGiven = true;
                mManager.dispatchRecognitionResult(observed);
            }
        }
    }

    /**
     * Starts the recording and the recorder thread. Results will be posted in the Callback
     * that was given to the class constructor.
     */
    public void startRecording() {
        mBufferIndex = 0;
        if (!mRecognitionEnabled) return;
        try {
            // Make sure buffer is cleared before recording starts.
            int bufferSize = SAMPLE_RATE * 11 * 2;
            mBuffer = new byte[bufferSize];
            mRecThread = new RecorderThread();
            mRecorder.startRecording();
            isRecording = true;
            mRecThread.start();
        } catch (IllegalStateException e) {
            Log.d(TAG, "Cannot start recording for recognition", e);
            mManager.dispatchRecognitionError();
        }
    }

    /**
     * If startRecording was called and still active, the recording system will be stopped and
     * pending data, if any, will be sent to the API to get a match.
     */
    public void stopRecording() {
        if (mRecorder != null && mRecorder.getState() == AudioRecord.STATE_INITIALIZED && mRecThread != null && mRecThread.isAlive()) {
            try {
                Log.d(TAG, "Stopping recorder");
                isRecording = false;
                mRecorder.stop();

                // Don't forget to release the native resources.
                mRecorder.release();
                mRecorder = null;
                mRecThread = null;
            } catch (Exception e) {
                Log.e(TAG, "Exception occured ", e);
            }
        }
    }
}
