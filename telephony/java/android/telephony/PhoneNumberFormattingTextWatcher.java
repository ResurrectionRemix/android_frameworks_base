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

package android.telephony;

import com.android.i18n.phonenumbers.AsYouTypeFormatter;
import com.android.i18n.phonenumbers.PhoneNumberUtil;

import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;

import java.util.Locale;

/**
 * Watches a {@link android.widget.TextView} and if a phone number is entered
 * will format it.
 * <p>
 * Stop formatting when the user
 * <ul>
 * <li>Inputs non-dialable characters</li>
 * <li>Removes the separator in the middle of string.</li>
 * </ul>
 * <p>
 * The formatting will be restarted once the text is cleared.
 */
public class PhoneNumberFormattingTextWatcher implements TextWatcher {

    /**
     * Indicates the change was caused by ourselves.
     */
    private boolean mSelfChange = false;

    /**
     * Indicates the formatting has been stopped.
     */
    private boolean mStopFormatting;

    private int mLastNumberStart = 0;

    private final int mSeparatorLength = 2; // ". " or "; "

    private AsYouTypeFormatter mFormatter;

    /**
     * The formatting is based on the current system locale and future locale changes
     * may not take effect on this instance.
     */
    public PhoneNumberFormattingTextWatcher() {
        this(Locale.getDefault().getCountry());
    }

    /**
     * The formatting is based on the given <code>countryCode</code>.
     *
     * @param countryCode the ISO 3166-1 two-letter country code that indicates the country/region
     * where the phone number is being entered.
     */
    public PhoneNumberFormattingTextWatcher(String countryCode) {
        if (countryCode == null) throw new IllegalArgumentException();
        mFormatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(countryCode);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
        if (mSelfChange || mStopFormatting) {
            return;
        }
        // If the user manually deleted any non-dialable characters, stop formatting
        if (count > 0 && hasSeparator(s, start, count)) {
            stopFormatting();
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mSelfChange || mStopFormatting) {
            return;
        }
        // If the user inserted any non-dialable characters, stop formatting
        if (count > 0 && hasSeparator(s, start, count)) {
            stopFormatting();
        }
    }

    @Override
    public synchronized void afterTextChanged(Editable s) {
        mLastNumberStart = findLastNumberStart(s);
        if (mStopFormatting) {
            // Restart the formatting when all texts were clear
            // or a new recipent will be added.
            mStopFormatting = hasSeparator(s, mLastNumberStart, s.length() - mLastNumberStart);
        }
        if (mSelfChange || mStopFormatting) {
            // Ignore the change caused by s.replace().
            return;
        }
        int sLength = Selection.getSelectionEnd(s) - mLastNumberStart;
        String lastNumber, formatted;
        if (sLength > 0) {
            char[] cLastNumber = new char[sLength];
            s.getChars(mLastNumberStart, sLength + mLastNumberStart, cLastNumber, 0);
            lastNumber = new String(cLastNumber);
            formatted = reformat(lastNumber, sLength);
        } else {
            lastNumber = null;
            formatted = null;
        }
        if (formatted != null) {
            int rememberedPos = mFormatter.getRememberedPosition();
            mSelfChange = true;
            s.replace(mLastNumberStart, mLastNumberStart + sLength,
                  formatted, 0, formatted.length());
            // The text could be changed by other TextWatcher after we changed it. If we found the
            // text is not the one we were expecting, just give up calling setSelection().
            if (formatted.equals(lastNumber.toString())) {
                Selection.setSelection(s, mLastNumberStart + rememberedPos);
            }
            mSelfChange = false;
        }
        PhoneNumberUtils.ttsSpanAsPhoneNumber(s, 0, s.length());
    }

    /**
     * Generate the formatted number by ignoring all non-dialable chars and stick the cursor to the
     * nearest dialable char to the left. For instance, if the number is  (650) 123-45678 and '4' is
     * removed then the cursor should be behind '3' instead of '-'.
     */
    private String reformat(CharSequence s, int cursor) {
        // The index of char to the leftward of the cursor.
        int curIndex = cursor - 1;
        String formatted = null;
        mFormatter.clear();
        char lastNonSeparator = 0;
        boolean hasCursor = false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (PhoneNumberUtils.isNonSeparator(c)) {
                if (lastNonSeparator != 0) {
                    formatted = getFormattedNumber(lastNonSeparator, hasCursor);
                    hasCursor = false;
                }
                lastNonSeparator = c;
            }
            if (i == curIndex) {
                hasCursor = true;
            }
        }
        if (lastNonSeparator != 0) {
            formatted = getFormattedNumber(lastNonSeparator, hasCursor);
        }
        return formatted;
    }

    private String getFormattedNumber(char lastNonSeparator, boolean hasCursor) {
        return hasCursor ? mFormatter.inputDigitAndRememberPosition(lastNonSeparator)
                : mFormatter.inputDigit(lastNonSeparator);
    }

    private void stopFormatting() {
        mStopFormatting = true;
        mFormatter.clear();
    }

    private boolean hasSeparator(final CharSequence s, final int start, final int count) {
        for (int i = start; i < start + count; i++) {
            if (i < start + count - 1  && CheckSeparator(s, i)) {
                continue;
            }
            char c = s.charAt(i);
            if (!PhoneNumberUtils.isNonSeparator(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean CheckSeparator(final CharSequence s, final int index) {
        if (index < 0 || index >= s.length() - 1) {
            return false;
        }
        if ((s.charAt(index) == ',' || s.charAt(index) == ';')  && s.charAt(index + 1) == ' ') {
            return true;
        }
        return false;
    }

    private int findLastNumberStart(final CharSequence s) {
        int index = s.length() - mSeparatorLength;
        while (index >= 0) {
            if (CheckSeparator(s, index)) {
                break;
            }
            index--;
        }
        // If the Separator is at the beginning of the number, consider it as part of number
        if (index < 1) {
            return 0;
        } else {
            return index + mSeparatorLength;
        }
    }
}
