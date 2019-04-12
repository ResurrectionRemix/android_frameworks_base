package com.android.keyguard.clocks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.text.format.DateUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;
import android.provider.Settings;

import java.util.TimeZone;

import com.android.systemui.R;

public class CustomTextClock extends TextView {

    final Resources res = getResources();

    private final String[] TensString  = { "", "", res.getString(R.string.text_clock_tens_string_twenty),
                                           res.getString(R.string.text_clock_tens_string_thirty),
                                           res.getString(R.string.text_clock_tens_string_forty),
                                           res.getString(R.string.text_clock_tens_string_fifty),
                                           res.getString(R.string.text_clock_tens_string_sixty) };

    private final String[] UnitsString = { res.getString(R.string.text_clock_units_string_clock),
                                           res.getString(R.string.text_clock_units_string_one),
                                           res.getString(R.string.text_clock_units_string_two),
                                           res.getString(R.string.text_clock_units_string_three),
                                           res.getString(R.string.text_clock_units_string_four),
                                           res.getString(R.string.text_clock_units_string_five),
                                           res.getString(R.string.text_clock_units_string_six),
                                           res.getString(R.string.text_clock_units_string_seven),
                                           res.getString(R.string.text_clock_units_string_eight),
                                           res.getString(R.string.text_clock_units_string_nine),
                                           res.getString(R.string.text_clock_units_string_ten),
                                           res.getString(R.string.text_clock_units_string_eleven),
                                           res.getString(R.string.text_clock_units_string_twelve),
                                           res.getString(R.string.text_clock_units_string_thirteen),
                                           res.getString(R.string.text_clock_units_string_fourteen),
                                           res.getString(R.string.text_clock_units_string_fifteen),
                                           res.getString(R.string.text_clock_units_string_sixteen),
                                           res.getString(R.string.text_clock_units_string_seventeen),
                                           res.getString(R.string.text_clock_units_string_eighteen),
                                           res.getString(R.string.text_clock_units_string_nineteen) };

    private final String[] TensStringH  = { "", "", res.getString(R.string.text_clock_tens_string_h_twenty),
                                            res.getString(R.string.text_clock_tens_string_h_thirty),
                                            res.getString(R.string.text_clock_tens_string_h_forty),
                                            res.getString(R.string.text_clock_tens_string_h_fifty),
                                            res.getString(R.string.text_clock_tens_string_h_sixty) };

    private final String[] UnitsStringH = { res.getString(R.string.text_clock_units_string_h_twelve_h),
                                            res.getString(R.string.text_clock_units_string_h_one),
                                            res.getString(R.string.text_clock_units_string_h_two),
                                            res.getString(R.string.text_clock_units_string_h_three),
                                            res.getString(R.string.text_clock_units_string_h_four),
                                            res.getString(R.string.text_clock_units_string_h_five),
                                            res.getString(R.string.text_clock_units_string_h_six),
                                            res.getString(R.string.text_clock_units_string_h_seven),
                                            res.getString(R.string.text_clock_units_string_h_eight),
                                            res.getString(R.string.text_clock_units_string_h_nine),
                                            res.getString(R.string.text_clock_units_string_h_ten),
                                            res.getString(R.string.text_clock_units_string_h_eleven),
                                            res.getString(R.string.text_clock_units_string_h_twelve),
                                            res.getString(R.string.text_clock_units_string_h_thirteen),
                                            res.getString(R.string.text_clock_units_string_h_fourteen),
                                            res.getString(R.string.text_clock_units_string_h_fifteen),
                                            res.getString(R.string.text_clock_units_string_h_sixteen),
                                            res.getString(R.string.text_clock_units_string_h_seventeen),
                                            res.getString(R.string.text_clock_units_string_h_eighteen),
                                            res.getString(R.string.text_clock_units_string_h_nineteen) };

    private Time mCalendar;

    private boolean mAttached;

    private int handType;

    private boolean h24;

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomTextClock);

        handType = a.getInteger(R.styleable.CustomTextClock_HandType, 2);

        mCalendar = new Time();


    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

            // OK, this is gross but needed. This class is supported by the
            // remote views machanism and as a part of that the remote views
            // can be inflated by a context for another user without the app
            // having interact users permission - just for loading resources.
            // For exmaple, when adding widgets from a user profile to the
            // home screen. Therefore, we register the receiver as the current
            // user not the one the context is for.
            getContext().registerReceiverAsUser(mIntentReceiver,
                    android.os.Process.myUserHandle(), filter, null, getHandler());
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = new Time();

        // Make sure we update to the current time
        onTimeChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    private void onTimeChanged() {
        mCalendar.setToNow();
        h24 = DateFormat.is24HourFormat(getContext());

        int hour = mCalendar.hour;
        int minute = mCalendar.minute;

        Log.d("CustomTextClock", ""+h24);

        if (!h24) {
            if (hour > 12) {
                hour = hour - 12;
            }
        }

        switch(handType){
            case 0:
                if (hour == 12 && minute == 0) {
                setText(res.getString(R.string.text_clock_high));
                } else {
                setText(getIntStringHour(hour));
                }
                break;
            case 1:
                if (hour == 12 && minute == 0) {
                setText(res.getString(R.string.text_clock_noon));
                } else {
                setText(getIntStringMin(minute));
                }
                break;
            default:
                break;
        }

        updateContentDescription(mCalendar, getContext());
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = new Time(TimeZone.getTimeZone(tz).getID());
            }

            onTimeChanged();

            invalidate();
        }
    };

    private void updateContentDescription(Time time, Context mContext) {
        final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR;
        String contentDescription = DateUtils.formatDateTime(mContext,
                time.toMillis(false), flags);
        setContentDescription(contentDescription);
    }

    private String getIntStringHour (int num) {
        int tens, units;
        String NumString = "";
        if(num >= 20) {
            units = num % 10 ;
            tens =  num / 10;
            if ( units == 0 ) {
                NumString = TensStringH[tens];
            } else {
                NumString = TensStringH[tens]+" "+UnitsStringH[units];
            }
        } else if (num < 20 ) {
            NumString = UnitsStringH[num];
        }

        return NumString;
    }

    private String getIntStringMin (int num) {
        int tens, units;
        String NumString = "";
        if(num >= 20) {
            units = num % 10 ;
            tens =  num / 10;
            if ( units == 0 ) {
                NumString = TensString[tens];
            } else {
                NumString = TensString[tens] + " " + UnitsString[units];
            }
        } else if (num < 10 ) {
            NumString = res.getString(R.string.text_clock_zero_h_min) +
                            " " + UnitsString[num];
        } else if (num >= 10 && num < 20) {
            NumString = UnitsString[num];
        }

        return NumString;
    }

}
