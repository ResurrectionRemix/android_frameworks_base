/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import com.android.systemui.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Clock that presents the time in words.
 */
public class TypographicClock extends TextView {

    private static final String ANNOTATION_COLOR = "color";

    private final Resources mResources;
    private final String[] mHours;
    private final String[] mMinutes;
    private int mAccentColor;
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());
    private String mDescFormat;
    private TimeZone mTimeZone;

    public TypographicClock(Context context) {
        this(context, null);
    }

    public TypographicClock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TypographicClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        mHours = mResources.getStringArray(R.array.type_clock_hours);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mAccentColor = mResources.getColor(R.color.typeClockAccentColor, null);
        refreshLockFont();
        refreshclocksize();
    }

    /**
     * Call when the time changes to update the text of the time.
     */
    public void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        final int hour = mTime.get(Calendar.HOUR) % 12;
        final int minute = mTime.get(Calendar.MINUTE) % 60;

        // Get the quantity based on the hour for languages like Portuguese and Czech.
        SpannedString typeTemplate = (SpannedString) mResources.getQuantityText(
                R.plurals.type_clock_header, hour);

        // Find the "color" annotation and set the foreground color to the accent color.
        Annotation[] annotations = typeTemplate.getSpans(0, typeTemplate.length(),
                Annotation.class);
        SpannableString spanType = new SpannableString(typeTemplate);
        for (int i = 0; i < annotations.length; i++) {
            Annotation annotation = annotations[i];
            String key = annotation.getValue();
            if (ANNOTATION_COLOR.equals(key)) {
                spanType.setSpan(new ForegroundColorSpan(mAccentColor),
                        spanType.getSpanStart(annotation), spanType.getSpanEnd(annotation),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        setText(TextUtils.expandTemplate(spanType, mHours[hour], mMinutes[minute]));
        refreshLockFont();
        refreshclocksize();
        refreshclocksize();
    }

    /**
     * Call when the time zone has changed to update clock time.
     *
     * @param timeZone The updated time zone that will be used.
     */
    public void onTimeZoneChanged(TimeZone timeZone) {
        mTimeZone = timeZone;
        mTime.setTimeZone(timeZone);
    }

    /**
     * Sets the accent color used on the clock face.
     */
    public void setClockColor(int color) {
        mAccentColor = color;
        onTimeChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTime.setTimeZone(mTimeZone != null ? mTimeZone : TimeZone.getDefault());
        onTimeChanged();
    }

    /**
     * Overriding hasOverlappingRendering as false to improve performance of crossfading.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.CUSTOM_TEXT_CLOCK_FONTS, 32);
    }

    private int getLockClockSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.CUSTOM_TEXT_CLOCK_FONT_SIZE, 40);
    }

    public void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 32;

        if (lockClockFont == 0) {
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 2) {
            setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 3) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 5) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 6) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockClockFont == 7) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 8) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 9) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 10) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 11) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 12) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockClockFont == 14) {
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockClockFont == 16) {
                setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
                setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (lockClockFont == 18) {
                setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (lockClockFont == 19) {
                setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (lockClockFont == 20) {
                setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (lockClockFont == 21) {
                setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (lockClockFont == 22) {
                setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (lockClockFont == 23) {
                setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (lockClockFont == 24) {
                setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 25) {
            setTypeface(Typeface.create("accuratist", Typeface.NORMAL));
        }
        if (lockClockFont == 26) {
            setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
        }
        if (lockClockFont == 27) {
            setTypeface(Typeface.create("amarante", Typeface.NORMAL));
        }
        if (lockClockFont == 28) {
            setTypeface(Typeface.create("bariol", Typeface.NORMAL));
        }
        if (lockClockFont == 29) {
            setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
        }
        if (lockClockFont == 30) {
            setTypeface(Typeface.create("cocon", Typeface.NORMAL));
        }
        if (lockClockFont == 31) {
            setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
        }

        if (lockClockFont == 32) {
                setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
        }
        if (lockClockFont == 33) {
                setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
        }
        if (lockClockFont == 34) {
                setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
        }
        if (lockClockFont == 35) {
                setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
        }
        if (lockClockFont == 36) {
            setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
        }
        if (lockClockFont == 37) {
            setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
        }
        if (lockClockFont == 38) {
            setTypeface(Typeface.create("lato", Typeface.NORMAL));
        }
        if (lockClockFont == 39) {
            setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
        }
        if (lockClockFont == 40) {
            setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
        }
        if (lockClockFont == 41) {
            setTypeface(Typeface.create("nunito", Typeface.NORMAL));
        }
        if (lockClockFont == 42) {
            setTypeface(Typeface.create("quando", Typeface.NORMAL));
        }

        if (lockClockFont == 43) {
                setTypeface(Typeface.create("redressed", Typeface.NORMAL));
        }
        if (lockClockFont == 44) {
            setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (lockClockFont == 45) {
            setTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
        }
        if (lockClockFont == 46) {
            setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
        }
        if (lockClockFont == 47) {
            setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
        }
        if (lockClockFont == 48) {
            setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
        }
        if (lockClockFont == 49) {
            setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
        }
        if (lockClockFont == 50) {
            setTypeface(Typeface.create("storopia", Typeface.NORMAL));
        }
        if (lockClockFont == 51) {
            setTypeface(Typeface.create("surfer", Typeface.NORMAL));
        }
        if (lockClockFont == 52) {
            setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
        }
    }

    public void refreshclocksize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockSize = isPrimary ? getLockClockSize() : 40;

        if (lockClockSize == 35) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_35));
        } else if (lockClockSize == 36) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_36));
        } else if (lockClockSize == 37) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_37));
        } else if (lockClockSize == 38) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_38));
        } else if (lockClockSize == 39) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_39));
        } else if (lockClockSize == 40) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_40));
        } else if (lockClockSize == 41) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_41));
        } else if (lockClockSize == 42) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_42));
        } else if (lockClockSize == 43) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_43));
        } else if (lockClockSize == 44) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_44));
        } else if (lockClockSize == 45) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_45));
        } else if (lockClockSize == 46) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_46));
        } else if (lockClockSize == 47) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_47));
        } else if (lockClockSize == 48) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_48));
        } else if (lockClockSize == 49) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_49));
        } else if (lockClockSize == 50) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_50));
        } else if (lockClockSize == 51) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51));
        } else if (lockClockSize == 52) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52));
        } else if (lockClockSize == 53) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53));
        } else if (lockClockSize == 54) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54));
        } else if (lockClockSize == 55) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
	        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55));
        }
    }
}
