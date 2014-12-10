/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.keyguard;

import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

/**
 * A Pin based Keyguard input view
 */
public abstract class KeyguardPinBasedInputView extends KeyguardAbsKeyInputView
        implements View.OnKeyListener, PasswordTextView.OnTextChangedListener {

    protected PasswordTextView mPasswordEntry;
    private View mOkButton;
    private View mDeleteButton;
    private View[] mButton = new NumPadKey[10];
    private int[] mButtonResId = new int[] {
            R.id.key0,
            R.id.key1,
            R.id.key2,
            R.id.key3,
            R.id.key4,
            R.id.key5,
            R.id.key6,
            R.id.key7,
            R.id.key8,
            R.id.key9
        };

    private boolean mQuickUnlock;

    public KeyguardPinBasedInputView(Context context) {
        this(context, null);
    }

    public KeyguardPinBasedInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void reset() {
        mPasswordEntry.requestFocus();
        super.reset();
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // send focus to the password field
        return mPasswordEntry.requestFocus(direction, previouslyFocusedRect);
    }

    protected void resetState() {
        mPasswordEntry.setEnabled(true);
    }

    @Override
    protected void setPasswordEntryEnabled(boolean enabled) {
        mPasswordEntry.setEnabled(enabled);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.isConfirmKey(keyCode)) {
            performClick(mOkButton);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            performClick(mDeleteButton);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int number = keyCode - KeyEvent.KEYCODE_0 ;
            performClick(mButton[number]);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void performClick(View view) {
        view.performClick();
    }

    @Override
    protected void resetPasswordText(boolean animate) {
        mPasswordEntry.reset(animate);
    }

    @Override
    protected String getPasswordText() {
        return mPasswordEntry.getText();
    }

    // Listener callback.
    @Override
    public void onTextChanged() {
        if (mQuickUnlock) {
            if (getPasswordText().length() > MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT
                    && mLockPatternUtils.checkPassword(getPasswordText())) {
                mCallback.reportUnlockAttempt(true);
                mCallback.dismiss(true);
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        mPasswordEntry = (PasswordTextView) findViewById(getPasswordTextViewId());
        mPasswordEntry.setOnKeyListener(this);
        mPasswordEntry.setOnTextChangedListener(this);

        // Set selected property on so the view can send accessibility events.
        mPasswordEntry.setSelected(true);

        // Poke the wakelock any time the text is selected or modified
        mPasswordEntry.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mCallback.userActivity();
            }
        });


        mQuickUnlock = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_QUICK_UNLOCK_CONTROL, 1, UserHandle.USER_CURRENT) == 1;

        mOkButton = findViewById(R.id.key_enter);
        if (mOkButton != null) {
            if (mQuickUnlock) {
                mOkButton.setVisibility(View.INVISIBLE);
            } else {
                mOkButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doHapticKeyClick();
                        if (mPasswordEntry.isEnabled()) {
                            verifyPasswordAndUnlock();
                        }
                    }
                });
                mOkButton.setOnHoverListener(new LiftToActivateListener(getContext()));
            }
        }
        
        mDeleteButton = findViewById(R.id.delete_button);
        mDeleteButton.setVisibility(View.VISIBLE);
        mDeleteButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // check for time-based lockouts
                if (mPasswordEntry.isEnabled()) {
                    mPasswordEntry.deleteLastChar();
                }
                doHapticKeyClick();
            }
        });
        mDeleteButton.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                // check for time-based lockouts
                if (mPasswordEntry.isEnabled()) {
                    resetPasswordText(true /* animate */);
                }
                doHapticKeyClick();
                return true;
            }
        });

        for (int i = 0; i < 10; i++) {
            mButton[i] = findViewById(mButtonResId[i]);
        }

        final int randomDigitMode = Settings.Secure.getIntForUser(
                mContext.getContentResolver(), Settings.Secure.LOCK_NUMPAD_RANDOM,
                1, UserHandle.USER_CURRENT);

        if (randomDigitMode > 0) {
            final View randomButton = findViewById(R.id.key_random);
            if (randomDigitMode == 1) {
                buildRandomNumPadKey();
            }
            if (randomButton != null) {
                randomButton.setVisibility(View.VISIBLE);
                randomButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doHapticKeyClick();
                        buildRandomNumPadKey();
                    }
                });
                randomButton.setOnHoverListener(new LiftToActivateListener(getContext()));
            }
        }

        mPasswordEntry.requestFocus();
        super.onFinishInflate();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            onKeyDown(keyCode, event);
            return true;
        }
        return false;
    }

    private void buildRandomNumPadKey() {
        for (int i = 0; i < 10; i++) {
            if (mButton[i] != null) {
                if (i == 0) {
                    ((NumPadKey) mButton[i]).initNumKeyPad();
                }
                ((NumPadKey) mButton[i]).createNumKeyPad(true);
            }
        }
    }
}
