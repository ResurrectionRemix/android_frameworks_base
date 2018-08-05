/*
 * Copyright (C) 2017-2018 Google Inc.
 * Copyright (C) 2018 CypherOS
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
package com.android.systemui.ambient;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.ambient.AmbientIndicationInflateListener;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.statusbar.phone.StatusBar;

import static android.provider.Settings.Secure.AMBIENT_RECOGNITION;
import static android.provider.Settings.Secure.AMBIENT_RECOGNITION_KEYGUARD;

public class AmbientIndicationContainer extends AutoReinflateContainer implements DozeReceiver {
    private View mAmbientIndication;
    private boolean mDozing;
    private ImageView mIcon;
    private CharSequence mIndication;
    private StatusBar mStatusBar;
    private TextView mText;
    private Context mContext;

    private String mSong;
    private String mArtist;

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
    }

    public void hideIndication() {
        setIndication(null, null);
    }

    public void initializeView(StatusBar statusBar) {
        mStatusBar = statusBar;
        addInflateListener(new AmbientIndicationInflateListener(this));
    }

    public void updateAmbientIndicationView(View view) {
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mText = (TextView)findViewById(R.id.ambient_indication_text);
        mIcon = (ImageView)findViewById(R.id.ambient_indication_icon);
        setIndication(mSong, mArtist);
    }

    private void updateAmbientIndicationForKeyguard() {
        boolean recognitionEnabled = Settings.Secure.getInt(
            mContext.getContentResolver(), AMBIENT_RECOGNITION, 0) != 0;
        int recognitionKeyguard = Settings.Secure.getIntForUser(
            mContext.getContentResolver(), AMBIENT_RECOGNITION_KEYGUARD, 1, 
            UserHandle.USER_CURRENT);
        if (!recognitionEnabled) return;
        if (recognitionKeyguard == 1) {
            if (mSong != null && mArtist != null) {
                mAmbientIndication.setVisibility(View.VISIBLE);
            } else {
                mAmbientIndication.setVisibility(View.INVISIBLE);
            }
            return;
        }
        mAmbientIndication.setVisibility(View.INVISIBLE);
    }

    @Override
    public void setDozing(boolean dozing) {
        mDozing = dozing;
    }

    public void setIndication(String song, String artist) {
        mText.setText(String.format(mContext.getResources().getString(
            R.string.ambient_recognition_information), song, artist));
        mSong = song;
        mArtist = artist;
        mAmbientIndication.setClickable(false);
        updateAmbientIndicationForKeyguard();
    }
}
