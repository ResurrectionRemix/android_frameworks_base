package com.android.systemui.screenshot;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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

/**
 * Created by DanielHuber on 16.04.2016.
 */
public class ColorArrayAdapter extends ArrayAdapter<Integer> {
    private Integer[] colors;

    public ColorArrayAdapter(Context context, Integer[] colors) {
        super(context, R.layout.list_item, colors);
        this.colors = colors;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createColorImage(position);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return  createColorImage(position);
    }

    private ImageView createColorImage(int position){
        ImageView imageView = new ImageView(getContext());
        int colorWidth = getContext().getResources().getDimensionPixelSize(R.dimen.crop_color_rect);
        ColorDrawable colorRect = new ColorDrawable(colors[position]);
        imageView.setImageDrawable(colorRect);
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(colorWidth, colorWidth);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ScaleType.CENTER_INSIDE);
        return imageView;
    }
}
