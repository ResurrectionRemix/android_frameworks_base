package com.android.systemui.screenshot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.android.systemui.R;

/**
 * Created by DanielHuber on 16.04.2016.
 */
public class ImageArrayAdapter extends ArrayAdapter<Integer> {
    private Integer[] images;

    public ImageArrayAdapter(Context context, Integer[] images) {
        super(context, R.layout.list_item, images);
        this.images = images;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getImageForPosition(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getImageForPosition(position);
    }

    private View getImageForPosition(int position) {
        ImageView imageView = new ImageView(getContext());
        imageView.setImageResource(images[position]);
        imageView.setScaleType(ScaleType.CENTER_INSIDE);
        int width = getContext().getResources().getDimensionPixelSize(R.dimen.crop_buttons);
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(width, width);
        imageView.setLayoutParams(params);
        return imageView;
    }
}
