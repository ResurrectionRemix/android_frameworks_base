/*
 * Copyright (C) 2015 SlimRoms
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

package android.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class SlimSeekBarPreference extends Preference
        implements OnSeekBarChangeListener {

    public static int maximum = 100;
    public int interval = 5;

    private TextView monitorBox;
    private SeekBar bar;

    int defaultValue = 60;
    int mSetDefault = -1;
    int mMultiply = -1;
    int mMinimum = -1;
    boolean mDisableText = false;
    boolean mDisablePercentageValue = false;
    boolean mIsMilliSeconds = false;

    private OnPreferenceChangeListener mChanger;

    public SlimSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {

        View layout = View.inflate(getContext(),
                com.android.internal.R.layout.slider_preference, null);

        monitorBox = (TextView)
                layout.findViewById(com.android.internal.R.id.monitor_box);
        bar = (SeekBar) layout.findViewById(com.android.internal.R.id.seek_bar);
        bar.setOnSeekBarChangeListener(this);
        bar.setProgress(defaultValue);

        return layout;
    }

    public void setInitValue(int progress) {
        defaultValue = progress;
        if (bar != null) {
            bar.setProgress(defaultValue);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // TODO Auto-generated method stub
        return super.onGetDefaultValue(a, index);
    }

    @Override
    public void setOnPreferenceChangeListener(
                OnPreferenceChangeListener onPreferenceChangeListener) {
        mChanger = onPreferenceChangeListener;
        super.setOnPreferenceChangeListener(onPreferenceChangeListener);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        progress = Math.round(((float) progress) / interval) * interval;
        seekBar.setProgress(progress);

        if (mMultiply != -1) {
            progress = progress * mMultiply;
        }

        if (mMinimum != -1) {
            progress += mMinimum;
        }

        if (progress == mSetDefault) {
            monitorBox.setText(com.android.internal.R.string.default_string);
        } else if (!mDisableText) {
            if (mIsMilliSeconds) {
                monitorBox.setText(progress + " ms");
            } else if (!mDisablePercentageValue) {
                monitorBox.setText(progress + "%");
            } else {
                monitorBox.setText(progress);
            }
        }
        if (mChanger != null) {
            mChanger.onPreferenceChange(this, Integer.toString(progress));
        }
    }

    public void disablePercentageValue(boolean disable) {
        mDisablePercentageValue = disable;
    }

    public void disableText(boolean disable) {
        mDisableText = disable;
    }

    public void setInterval(int inter) {
        interval = inter;
    }

    public void setDefault(int defaultVal) {
        mSetDefault = defaultVal;
    }

    public void multiplyValue(int val) {
        mMultiply = val;
    }

    public void minimumValue(int val) {
        mMinimum = val;
    }

    public void isMilliseconds(boolean millis) {
        mIsMilliSeconds = millis;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

}
