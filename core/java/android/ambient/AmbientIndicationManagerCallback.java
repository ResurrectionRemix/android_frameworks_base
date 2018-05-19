/*
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
package android.ambient;

import android.ambient.play.RecoginitionObserver;
import android.ambient.play.RecoginitionObserver.Observable;

/**
 * Callback for general information relevant to Ambient Indication.
 */
public class AmbientIndicationManagerCallback {

    /**
     * Called when {@link RecoginitionObserver} returns valid track results.
     * @param observed prints the relevant track information
     */
    public void onRecognitionResult(Observable observed) { }

    /**
     * Called when {@link RecoginitionObserver} returns no valid track results.
     */
    public void onRecognitionNoResult() { }

    /**
     * Called when {@link RecoginitionObserver} returns the current audio level.
     * @param level used to get the rate of the recorded audio
     */
    public void onRecognitionAudio(float level) { }

    /**
     * Called when {@link RecoginitionObserver} returns an unexpected error.
     */
    public void onRecognitionError() { }
}
