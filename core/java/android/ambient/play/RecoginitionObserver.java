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
package android.ambient.play;

import android.ambient.AmbientIndicationManager;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.provider.Settings;

import static android.provider.Settings.Secure.AMBIENT_RECOGNITION;

/**
 * Class helping audio fingerprinting for recognition
 */
public class RecoginitionObserver {

    protected static final String TAG = "RecoginitionObserver";
    protected static final String USER_AGENT = "User-Agent: AppNumber=48000 APIVersion=2.1.0.0 DEV=Android UID=dkl109sas19s";
    protected static final String MIME_TYPE = "audio/wav";

    protected static final int SAMPLE_RATE = 11025;
    protected static final short BIT_DEPTH = 16;
    protected static final short CHANNELS = 1;

    protected static final int MATCH_INTERVAL = 10000;

    protected byte[] mBuffer;
    protected int mBufferIndex;
    protected AudioRecord mRecorder;
    protected AmbientIndicationManager mManager;

    protected boolean mRecognitionEnabled;

    public RecoginitionObserver(Context context) {
        // We limit to 20 seconds of recording. We size our buffer to store 10 seconds of
        // audio at 11025 Hz, 16 bits (2 bytes) ; total of ~215KB uploaded max
        int bufferSize = SAMPLE_RATE * 11 * 2;
        mBuffer = new byte[bufferSize];
        final int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize);

        mManager = AmbientIndicationManager.getInstance(context);
        mRecognitionEnabled = Settings.Secure.getInt(context.getContentResolver(),
                AMBIENT_RECOGNITION, 0) != 0;
    }

    /**
     * Class storing fingerprinting results
     */
    public static class Observable {

        public String Artist;
        public String Album;
        public String Song;
        public String ArtworkUrl;

        @Override
        public String toString() {
            return Artist + " - " + Song + " (" + Album + "); " + ArtworkUrl;
        }
    }
}
