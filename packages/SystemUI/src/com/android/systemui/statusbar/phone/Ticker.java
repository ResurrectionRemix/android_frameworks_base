/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2016, ParanoidAndroid Project.
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
package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.StaticLayout;
import android.text.Layout.Alignment;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.TextSwitcher;

import java.util.ArrayList;

import com.android.internal.statusbar.StatusBarIcon;
import android.service.notification.StatusBarNotification;

import com.android.systemui.R;
import com.android.systemui.statusbar.StatusBarIconView;

public class Ticker {
    private static final int TICKER_SEGMENT_DELAY = 3000;

    private Context mContext;
    private TextPaint mPaint;
    private ArrayList<Segment> mSegments = new ArrayList();
    private TextSwitcher mTextSwitcher;
    private TickerCallback mEvent;

    public interface TickerCallback {

        public void updateTicker(StatusBarNotification notification, String text);
    }

    public void setUpdateEvent(TickerCallback event) {
        mEvent = event;
    }


    public final class Segment {
        public StatusBarNotification notification;
        public Drawable icon;
        public CharSequence text;
        public int current;
        public int next;
        public boolean first;

        public StaticLayout getLayout(CharSequence substr) {
            int w = mTextSwitcher.getWidth() - mTextSwitcher.getPaddingLeft()
                    - mTextSwitcher.getPaddingRight();
            return new StaticLayout(substr, mPaint, w, Alignment.ALIGN_NORMAL, 1, 0, true);
        }

        /** returns null if there is no more text */
        public CharSequence getText() {
            if (this.current > this.text.length()) {
                return null;
            }
            CharSequence substr = this.text.subSequence(this.current, this.text.length());
            StaticLayout l = getLayout(substr);
            int lineCount = l.getLineCount();
            if (lineCount > 0) {
                int start = l.getLineStart(0);
                int end = l.getLineEnd(0);
                this.next = this.current + end;
                return rtrim(substr, start, end);
            } else {
                throw new RuntimeException("lineCount=" + lineCount + " current=" + current +
                        " text=" + text);
            }
        }

        public CharSequence rtrim(CharSequence substr, int start, int end) {
            while (end > start) {
                end--;
            }
            if (end > start) {
                return substr.subSequence(start, end);
            }
            return null;
        }

        public Segment(StatusBarNotification n, Drawable icon, CharSequence text) {
            this.notification = n;
            this.icon = icon;
            this.text = text;
            int index = 0;
            final int len = text.length();
            while (index < len) {
                index++;
            }
            this.current = index;
            this.next = index;
            this.first = true;
        }
    };

    public void addEntry(StatusBarNotification n) {
        final Drawable icon = StatusBarIconView.getIcon(mContext,
                new StatusBarIcon(n.pkg, n.user, n.notification.icon, n.notification.iconLevel, 0,
                        n.notification.tickerText));
        final CharSequence text = n.notification.tickerText;

        int initialCount = mSegments.size();

        if (initialCount > 0) {
            final Segment seg = mSegments.get(0);
        }

        final Segment newSegment = new Segment(n, icon, text);

        mSegments.add(newSegment);
        if (mEvent != null) {
            if (newSegment != null) mEvent.updateTicker(newSegment.notification, text.toString());
        }
    }
}
