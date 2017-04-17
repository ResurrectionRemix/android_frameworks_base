/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.PorterDuff.Mode;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.View;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.rr.FontHelper;
import com.android.internal.util.rr.TickerColorHelper;
import com.android.internal.util.NotificationColorUtil;
import com.android.systemui.R;
import com.android.systemui.statusbar.StatusBarIconView;

import java.util.ArrayList;

public abstract class Ticker {
    private static final int TICKER_SEGMENT_DELAY = 3000;

    private Context mContext;
    private Handler mHandler = new Handler();
    private ArrayList<Segment> mSegments = new ArrayList();
    private TextPaint mPaint;
    private View mTickerView;
    private ImageSwitcher mIconSwitcher;
    private TextSwitcher mTextSwitcher;
    private float mIconScale;
    private int mTickerTextColor;
    private int mTickerFontSize = 14;
    private Typeface mFontStyle;

    // The ticker color as requested by the statusbar
    private int mDefaultColor = 0xffffffff;


    public static boolean isGraphicOrEmoji(char c) {
        int gc = Character.getType(c);
        return     gc != Character.CONTROL
                && gc != Character.FORMAT
                && gc != Character.UNASSIGNED
                && gc != Character.LINE_SEPARATOR
                && gc != Character.PARAGRAPH_SEPARATOR
                && gc != Character.SPACE_SEPARATOR;
    }

    private final class Segment {
        StatusBarNotification notification;
        Drawable icon;
        CharSequence text;
        int current;
        int next;
        boolean first;

        StaticLayout getLayout(CharSequence substr) {
            int w = mTextSwitcher.getWidth() - mTextSwitcher.getPaddingLeft()
                    - mTextSwitcher.getPaddingRight();
            return new StaticLayout(substr, mPaint, w, Alignment.ALIGN_NORMAL, 1, 0, true);
        }

        CharSequence rtrim(CharSequence substr, int start, int end) {
            while (end > start && !isGraphicOrEmoji(substr.charAt(end-1))) {
                end--;
            }
            if (end > start) {
                return substr.subSequence(start, end);
            }
            return null;
        }

        /** returns null if there is no more text */
        CharSequence getText() {
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

        /** returns null if there is no more text */
        CharSequence advance() {
            this.first = false;
            int index = this.next;
            final int len = this.text.length();
            while (index < len && !isGraphicOrEmoji(this.text.charAt(index))) {
                index++;
            }
            if (index >= len) {
                return null;
            }

            CharSequence substr = this.text.subSequence(index, this.text.length());
            StaticLayout l = getLayout(substr);
            final int lineCount = l.getLineCount();
            int i;
            for (i=0; i<lineCount; i++) {
                int start = l.getLineStart(i);
                int end = l.getLineEnd(i);
                if (i == lineCount-1) {
                    this.next = len;
                } else {
                    this.next = index + l.getLineStart(i+1);
                }
                CharSequence result = rtrim(substr, start, end);
                if (result != null) {
                    this.current = index + start;
                    return result;
                }
            }
            this.current = len;
            return null;
        }

        Segment(StatusBarNotification n, Drawable icon, CharSequence text) {
            this.notification = n;
            this.icon = icon;
            this.text = text;
            int index = 0;
            final int len = text.length();
            while (index < len && !isGraphicOrEmoji(text.charAt(index))) {
                index++;
            }
            this.current = index;
            this.next = index;
            this.first = true;
        }
    };

    public Ticker(Context context, View sb) {
        mContext = context;
        final Resources res = context.getResources();
        final int outerBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_size);
        final int imageBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_drawing_size);
        mIconScale = (float)imageBounds / (float)outerBounds;


        AlphaAnimation animationIn = new AlphaAnimation(0.0f, 1.0f);
        Interpolator interpolatorIn = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.decelerate_quad);
        animationIn.setInterpolator(interpolatorIn);
        animationIn.setDuration(350);

        AlphaAnimation animationOut = new AlphaAnimation(1.0f, 0.0f);
        Interpolator interpolatorOut = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.accelerate_quad);
        animationIn.setInterpolator(interpolatorOut);
        animationOut.setDuration(350);

        mIconSwitcher = (ImageSwitcher) sb.findViewById(R.id.tickerIcon);
        mIconSwitcher.setInAnimation(animationIn);
        mIconSwitcher.setOutAnimation(animationOut);
        mIconSwitcher.setScaleX(mIconScale);
        mIconSwitcher.setScaleY(mIconScale);

        mTextSwitcher = (TextSwitcher) sb.findViewById(R.id.tickerText);
        mTextSwitcher.setInAnimation(animationIn);
        mTextSwitcher.setOutAnimation(animationOut);

        // Copy the paint style of one of the TextSwitchers children to use later for measuring
        TextView text = (TextView)mTextSwitcher.getChildAt(0);
        mPaint = text.getPaint();
        updateTextColor();
        updateTickerSize();
        updateTickerFontStyle();
    }


    public void addEntry(StatusBarNotification n, boolean isMusic, MediaMetadata mediaMetaData) {
        int initialCount = mSegments.size();
        ContentResolver resolver = mContext.getContentResolver();

        if (isMusic) {
            CharSequence artist = mediaMetaData.getText(MediaMetadata.METADATA_KEY_ARTIST);
            CharSequence album = mediaMetaData.getText(MediaMetadata.METADATA_KEY_ALBUM);
            CharSequence title = mediaMetaData.getText(MediaMetadata.METADATA_KEY_TITLE);
            if (artist != null && album != null && title != null) {
                n.getNotification().tickerText = artist.toString() + " - " + album.toString() + " - " + title.toString();
            } else {
                return;
            }
        }

        // If what's being displayed has the same text and icon, just drop it
        // (which will let the current one finish, this happens when apps do
        // a notification storm).
        if (initialCount > 0) {
            final Segment seg = mSegments.get(0);
            if (n.getPackageName().equals(seg.notification.getPackageName())
                    && n.getNotification().icon == seg.notification.getNotification().icon
                    && n.getNotification().iconLevel == seg.notification.getNotification().iconLevel
                    && charSequencesEqual(seg.notification.getNotification().tickerText,
                        n.getNotification().tickerText)) {
                return;
            }
        }

        final Drawable icon = StatusBarIconView.getIcon(mContext,
                new StatusBarIcon(n.getPackageName(), n.getUser(), n.getNotification().icon, n.getNotification().iconLevel, 0,
                        n.getNotification().tickerText));
        final CharSequence text = n.getNotification().tickerText;
        final Segment newSegment = new Segment(n, icon, text);
        final ColorStateList tickerIconColor = TickerColorHelper.getTickerIconColorList(mContext, mDefaultColor);

        // If there's already a notification schedule for this package and id, remove it.
        for (int i=0; i<mSegments.size(); i++) {
            Segment seg = mSegments.get(i);
            if (n.getId() == seg.notification.getId() && n.getPackageName().equals(seg.notification.getPackageName())) {
                // just update that one to use this new data instead
                mSegments.remove(i--); // restart iteration here
            }
        }

        mSegments.add(newSegment);

        if (initialCount == 0 && mSegments.size() > 0) {
            Segment seg = mSegments.get(0);
            seg.first = false;

            mIconSwitcher.setAnimateFirstView(false);
            mIconSwitcher.reset();
            mIconSwitcher.setColoredImageDrawable(seg.icon, tickerIconColor);

            mTextSwitcher.setAnimateFirstView(false);
            mTextSwitcher.reset();
            mTextSwitcher.setText(seg.getText());
            updateTickerSize();
            updateTextColor();
            updateTickerFontStyle();
            setTextSwitcherColor();
            mTextSwitcher.setTextSize(mTickerFontSize);
            mTextSwitcher.setTypeface(mFontStyle);

            tickerStarting();
            scheduleAdvance();
        }
    }

    private static boolean charSequencesEqual(CharSequence a, CharSequence b) {
        if (a.length() != b.length()) {
            return false;
        }

        int length = a.length();
        for (int i = 0; i < length; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public void removeEntry(StatusBarNotification n) {
        for (int i=mSegments.size()-1; i>=0; i--) {
            Segment seg = mSegments.get(i);
            if (n.getId() == seg.notification.getId() && n.getPackageName().equals(seg.notification.getPackageName())) {
                mSegments.remove(i);
            }
        }
    }

    public void halt() {
        mHandler.removeCallbacks(mAdvanceTicker);
        mSegments.clear();
        tickerHalting();
    }

    public void reflowText() {
        if (mSegments.size() > 0) {
            Segment seg = mSegments.get(0);
            CharSequence text = seg.getText();
            mTextSwitcher.setCurrentText(text);
            updateTickerSize();
            updateTextColor();
            setTextSwitcherColor();
            mTextSwitcher.setTextSize(mTickerFontSize);
            mTextSwitcher.setTypeface(mFontStyle);
        }
    }

    private Runnable mAdvanceTicker = new Runnable() {
        public void run() {
            while (mSegments.size() > 0) {
                Segment seg = mSegments.get(0);

                final ColorStateList tickerIconColor =
                        TickerColorHelper.getTickerIconColorList(mContext, mDefaultColor);
                if (seg.first) {
                    // this makes the icon slide in for the first one for a given
                    // notification even if there are two notifications with the
                    // same icon in a row
                    mIconSwitcher.setColoredImageDrawable(seg.icon, tickerIconColor);
                }
                CharSequence text = seg.advance();
                if (text == null) {
                    mSegments.remove(0);
                    continue;
                }
                mTextSwitcher.setText(text);
                updateTickerSize();
                updateTextColor();
                setTextSwitcherColor();
                mTextSwitcher.setTextSize(mTickerFontSize);
                mTextSwitcher.setTypeface(mFontStyle);

                scheduleAdvance();
                break;
            }
            if (mSegments.size() == 0) {
                tickerDone();
            }
        }
    };

    private void scheduleAdvance() {
        mHandler.postDelayed(mAdvanceTicker, TICKER_SEGMENT_DELAY);
    }

    public abstract void tickerStarting();
    public abstract void tickerDone();
    public abstract void tickerHalting();

    private void updateTickerFontStyle() {
        final int mTickerFontStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_FONT_STYLE, FontHelper.FONT_NORMAL);

        getFontStyle(mTickerFontStyle);
    }

    public void getFontStyle(int font) {
        switch (font) {
            case FontHelper.FONT_NORMAL:
            default:
                mFontStyle = FontHelper.NORMAL;
                break;
            case FontHelper.FONT_ITALIC:
                mFontStyle = FontHelper.ITALIC;
                break;
            case FontHelper.FONT_BOLD:
                mFontStyle = FontHelper.BOLD;
                break;
            case FontHelper.FONT_BOLD_ITALIC:
                mFontStyle = FontHelper.BOLD_ITALIC;
                break;
            case FontHelper.FONT_LIGHT:
                mFontStyle = FontHelper.LIGHT;
                break;
            case FontHelper.FONT_LIGHT_ITALIC:
                mFontStyle = FontHelper.LIGHT_ITALIC;
                break;
            case FontHelper.FONT_THIN:
                mFontStyle = FontHelper.THIN;
                break;
            case FontHelper.FONT_THIN_ITALIC:
                mFontStyle = FontHelper.THIN_ITALIC;
                break;
            case FontHelper.FONT_CONDENSED:
                mFontStyle = FontHelper.CONDENSED;
                break;
            case FontHelper.FONT_CONDENSED_ITALIC:
                mFontStyle = FontHelper.CONDENSED_ITALIC;
                break;
            case FontHelper.FONT_CONDENSED_LIGHT:
                mFontStyle = FontHelper.CONDENSED_LIGHT;
                break;
            case FontHelper.FONT_CONDENSED_LIGHT_ITALIC:
                mFontStyle = FontHelper.CONDENSED_LIGHT_ITALIC;
                break;
            case FontHelper.FONT_CONDENSED_BOLD:
                mFontStyle = FontHelper.CONDENSED_BOLD;
                break;
            case FontHelper.FONT_CONDENSED_BOLD_ITALIC:
                mFontStyle = FontHelper.CONDENSED_BOLD_ITALIC;
                break;
            case FontHelper.FONT_MEDIUM:
                mFontStyle = FontHelper.MEDIUM;
                break;
            case FontHelper.FONT_MEDIUM_ITALIC:
                mFontStyle = FontHelper.MEDIUM_ITALIC;
                break;
            case FontHelper.FONT_BLACK:
                mFontStyle = FontHelper.BLACK;
                break;
            case FontHelper.FONT_BLACK_ITALIC:
                mFontStyle = FontHelper.BLACK_ITALIC;
                break;
            case FontHelper.FONT_DANCINGSCRIPT:
                mFontStyle = FontHelper.DANCINGSCRIPT;
                break;
            case FontHelper.FONT_DANCINGSCRIPT_BOLD:
                mFontStyle = FontHelper.DANCINGSCRIPT_BOLD;
                break;
            case FontHelper.FONT_COMINGSOON:
                mFontStyle = FontHelper.COMINGSOON;
                break;
            case FontHelper.FONT_NOTOSERIF:
                mFontStyle = FontHelper.NOTOSERIF;
                break;
            case FontHelper.FONT_NOTOSERIF_ITALIC:
                mFontStyle = FontHelper.NOTOSERIF_ITALIC;
                break;
            case FontHelper.FONT_NOTOSERIF_BOLD:
                mFontStyle = FontHelper.NOTOSERIF_BOLD;
                break;
            case FontHelper.FONT_NOTOSERIF_BOLD_ITALIC:
                mFontStyle = FontHelper.NOTOSERIF_BOLD_ITALIC;
                break;
        }
    }

    private void updateTickerSize() {
        mTickerFontSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_FONT_SIZE, 14,
                UserHandle.USER_CURRENT);

    }

    public void updateTextColor() {
        ContentResolver resolver = mContext.getContentResolver();

        mTickerTextColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_TICKER_TEXT_COLOR,
                0xffffffff);
    }

    public void setDefaultColor(int color) {
        mDefaultColor = color;

        // Update text color
        setTextSwitcherColor();
        // Update currently displayed icon
        ImageView currentIcon = (ImageView) mIconSwitcher.getCurrentView();
        if (currentIcon != null) {
            final ColorStateList tickerIconColor =
                    TickerColorHelper.getTickerIconColorList(mContext, mDefaultColor);
            currentIcon.setImageTintList(tickerIconColor);
        }
    }

    private void setTextSwitcherColor() {
        if (mTickerTextColor == 0xffffffff) {
            mTextSwitcher.setTextColor(mDefaultColor);
        } else {
            mTextSwitcher.setTextColor(mTickerTextColor);
        }
    }
}
