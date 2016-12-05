/*
* Copyright (C) 2016 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/

package com.android.systemui.screenshot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.systemui.R;

public class PenSizeArrayAdapter extends ArrayAdapter<String> {
    private String[] mPenSizes;

    public PenSizeArrayAdapter(Context context, int resourceId, String[] penSizes) {
        super(context, resourceId, penSizes);
        this.mPenSizes = penSizes;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createPenSizeImae(position);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createPenSizeImae(position);
    }

    private ImageView createPenSizeImae(int position){
        ImageView imageView = new ImageView(getContext());
        final int width = getContext().getResources().getDimensionPixelSize(R.dimen.crop_buttons);
        final int contentWidth = getContext().getResources().getDimensionPixelSize(R.dimen.crop_buttons_inlet);
        final int penSizeValue = Integer.valueOf(mPenSizes[position]);
        final float density = getContext().getResources().getDisplayMetrics().density;
        final int penSize = Math.round(penSizeValue * density);

        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmp = Bitmap.createBitmap(contentWidth, contentWidth, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(penSize);
        canvas.drawLine(0, contentWidth / 2, contentWidth, contentWidth / 2, paint);

        imageView.setImageDrawable(new BitmapDrawable(getContext().getResources(), bmp));
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(width, width);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ScaleType.CENTER);
        return imageView;
    }
}
