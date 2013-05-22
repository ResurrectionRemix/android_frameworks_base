
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

import java.lang.Object;
import java.util.Calendar;

public class SwaggerToggle extends BaseToggle implements OnTouchListener {

    private Calendar mCalendar;

    boolean youAreATaco = false;
    boolean sundayToggle = false;
    long tacoTime = 0;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                updateClock();
            }
        }, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public QuickSettingsTileView createTileView() {
        View root = super.createTileView();
        root.setOnTouchListener(this);
        root.setOnClickListener(null);
        root.setOnLongClickListener(null);
        return (QuickSettingsTileView) root;
    }

    @Override
    public View createTraditionalView() {
        View root = super.createTraditionalView();
        root.setOnTouchListener(this);
        root.setOnClickListener(null);
        root.setOnLongClickListener(null);
        return root;
    }

    @Override
    protected void updateView() {
        if (sundayToggle) {
            setLabel(R.string.quick_settings_swaggersun);
            setIcon(R.drawable.ic_qs_swaggersun);
        } else {
            setLabel(youAreATaco
                    ? R.string.quick_settings_fbgt
                    : R.string.quick_settings_swagger);
            setIcon(youAreATaco
                    ? R.drawable.ic_qs_fbgt_on
                    : R.drawable.ic_qs_swagger);
        }
        super.updateView();
    }

    final void updateClock() {
        mCalendar = Calendar.getInstance();
        if (mCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            if (!sundayToggle) {
                sundayToggle = true;
                scheduleViewUpdate();
            }
        } else {
            if (sundayToggle) {
                sundayToggle = false;
                scheduleViewUpdate();
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (sundayToggle) {
                    collapseStatusBar();
                    Toast.makeText(mContext,
                            R.string.quick_settings_swaggersuntoast,
                            Toast.LENGTH_LONG).show();
                    tacoTime = event.getEventTime();
                } else {
                    if (youAreATaco) {
                        tacoTime = event.getEventTime();
                        youAreATaco = false;
                    } else {
                        tacoTime = event.getEventTime();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (tacoTime > 0 && (event.getEventTime() - tacoTime) > 2500) {
                    youAreATaco = true;
                }
                break;
        }
        scheduleViewUpdate();
        return true;
    }
}
