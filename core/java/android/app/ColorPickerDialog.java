/*
 * Copyright (C) 2010 Daniel Nilsson
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

package android.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.preference.ColorPickerPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ColorPickerView;
import android.view.ColorPickerPanelView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.internal.R;

public class ColorPickerDialog extends Dialog implements
    ColorPickerView.OnColorChangedListener, View.OnClickListener {

    private ColorPickerView mColorPicker;

    private ColorPickerPanelView mOldColor;
    private ColorPickerPanelView mNewColor;

    private EditText mHex;
    private boolean isColorPickerBusy;

    private OnColorChangedListener mListener;

    public interface OnColorChangedListener {
        public void onColorChanged(int color);
    }

    public ColorPickerDialog(Context context, int initialColor) {
        super(context);
        init(initialColor);
    }

    private void init(int color) {
        // To fight color branding.
        getWindow().setFormat(PixelFormat.RGBA_8888);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setUp(color);
    }

    private void setUp(int color) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);

        View layout = inflater.inflate(R.layout.dialog_color_picker, null);

        setContentView(layout);

        setTitle(R.string.color_picker);

        mColorPicker = (ColorPickerView) layout.findViewById(R.id.color_picker_view);
        mOldColor = (ColorPickerPanelView) layout.findViewById(R.id.old_color_panel);
        mNewColor = (ColorPickerPanelView) layout.findViewById(R.id.new_color_panel);
        mHex = (EditText) layout.findViewById(R.id.hex);

        ((LinearLayout) mOldColor.getParent()).setPadding(Math.round(
            mColorPicker.getDrawingOffset()), 0, Math.round(
            mColorPicker.getDrawingOffset()), 0);

        mOldColor.setOnClickListener(this);
        mNewColor.setOnClickListener(this);
        mColorPicker.setOnColorChangedListener(this);
        mOldColor.setColor(color);
        mColorPicker.setColor(color, true);
        mHex.setText(ColorPickerPreference.convertToARGB(color));
        mHex.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String text = mHex.getText().toString();
                int newColor = ColorPickerPreference.convertToColorInt(text);
                isColorPickerBusy = true;
                mColorPicker.setColor(newColor, true);
            }
        });
    }

    public void setColors(int oldColor, int newColor) {
        mOldColor.setColor(oldColor);
        mColorPicker.setColor(newColor, true);
        mHex.setText(ColorPickerPreference.convertToARGB(newColor));
    }

    @Override
    public void onColorChanged(int color) {
        mNewColor.setColor(color);
        if(!isColorPickerBusy) {
            mHex.setText(ColorPickerPreference.convertToARGB(color));
        }
        isColorPickerBusy = false;
    }

    public void setAlphaSliderVisible(boolean visible) {
        mColorPicker.setAlphaSliderVisible(visible);
    }

    public boolean getAlphaSliderVisible() {
        return mColorPicker.getAlphaSliderVisible();
    }

    /**
     * Set a OnColorChangedListener to get notified when the color selected by the user has changed.
     *
     * @param listener
     */
    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    public int getColor() {
        return mColorPicker.getColor();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.new_color_panel) {
            if (mListener != null) {
                mListener.onColorChanged(mNewColor.getColor());
            }
        } else if (v.getId() == R.id.old_color_panel) {
            if (mListener != null) {
                mListener.onColorChanged(mOldColor.getColor());
            }
        }
        dismiss();
    }
}
