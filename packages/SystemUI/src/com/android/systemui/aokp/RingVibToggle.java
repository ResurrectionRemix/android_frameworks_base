/*
 * Copyright 2011 AOKP by Mike Wilson - Zaphod-Beeblebrox
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

package com.android.systemui.aokp;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;

/*
 * Toggle Ring/Vibrate/Silent
 */

public class RingVibToggle extends Activity  {
  public RingVibToggle() {
    super();
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();

    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    if(am != null){
      if(am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if(vib != null){
          vib.vibrate(50);
        }
      }else{
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
        if(tg != null){
          tg.startTone(ToneGenerator.TONE_PROP_BEEP);
        }
      }
    }
    finish();
  }
}
