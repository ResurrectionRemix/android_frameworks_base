package com.android.systemui.screenshot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
        final int width = getContext().getResources().getDimensionPixelSize(R.dimen.crop_buttons);
        final int contentWidth = getContext().getResources().getDimensionPixelSize(R.dimen.crop_buttons_inlet);
        final int color = colors[position];

        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmp = Bitmap.createBitmap(contentWidth, contentWidth, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(0, 0, contentWidth, contentWidth, paint);

        imageView.setImageDrawable(new BitmapDrawable(getContext().getResources(), bmp));
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(width, width);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ScaleType.CENTER);
        return imageView;
    }
}
