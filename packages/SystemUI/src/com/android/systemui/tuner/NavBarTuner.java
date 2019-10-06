/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY_CODE_END;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY_CODE_START;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY_IMAGE_DELIM;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.MENU_IME_ROTATE;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.NAVSPACE;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.NAV_BAR_LEFT;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.NAV_BAR_RIGHT;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.NAV_BAR_VIEWS;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.extractButton;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.extractImage;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.extractKeycode;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;

public class NavBarTuner extends PreferenceFragment {

    private static final String LAYOUT = "layout";

    private final ArrayList<Tunable> mTunables = new ArrayList<>();
    private Handler mHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        mHandler = new Handler();
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.nav_bar_tuner);
        bindLayout((ListPreference) findPreference(LAYOUT));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTunables.forEach(t -> Dependency.get(TunerService.class).removeTunable(t));
    }

    private void addTunable(Tunable tunable, String... keys) {
        mTunables.add(tunable);
        Dependency.get(TunerService.class).addTunable(tunable, keys);
    }

    private void bindLayout(ListPreference preference) {
        addTunable((key, newValue) -> mHandler.post(() -> {
            String val = newValue;
            if (val == null) {
                val = "default";
            }
            preference.setValue(val);
        }), NAV_BAR_VIEWS);
        preference.setOnPreferenceChangeListener((preference1, newValue) -> {
            String val = (String) newValue;
            int valueIndex = preference.findIndexOfValue(val);
            preference.setSummary(preference.getEntries()[valueIndex]);
            if ("default".equals(val)) val = null;
            Dependency.get(TunerService.class).setValue(NAV_BAR_VIEWS, val);
            return true;
        });
    }
}
