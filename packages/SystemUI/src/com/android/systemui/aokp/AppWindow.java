/*
 * Copyright (C) 2013 The Android Open Kand Project
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

package com.android.systemui.aokp;

import com.android.systemui.R;

import java.lang.IllegalArgumentException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;

import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.BackgroundAlphaColorDrawable;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.aokp.RibbonGestureCatcherView;

public class AppWindow extends LinearLayout {
    public static final String TAG = "APP WINDOW";

    private Context mContext;
    public FrameLayout mPopupView;
    public WindowManager mWindowManager;
    private SettingsObserver mSettingsObserver;
    private TextView mWindowLabel;
    private ImageView mWindowDividerTop;
    private ImageView mWindowDividerBottom;
    private Button mCloseButton;
    private LinearLayout mWindow;
    private LinearLayout mWindowMain;
    private Button mBackGround;
    private boolean showing = false;
    private boolean animating = false;
    private int mColor, mColumns, mTextColor, mOpacity;
    private ArrayList<String> mApps = new ArrayList<String>();
    private Handler mHandler;
    private int APP_WINDOW = 6;


    private static final LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(
            LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

    private static final LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

    public AppWindow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WindowReceiver.ACTION_TOGGLE_APP_WINDOW);
        filter.addAction(WindowReceiver.ACTION_SHOW_APP_WINDOW);
        filter.addAction(WindowReceiver.ACTION_HIDE_APP_WINDOW);
        mContext.registerReceiver(new WindowReceiver(), filter);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler();
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        updateSettings();
    }


    public void toggleRibbonView() {
        if (showing) {
            hideWindowView();
        } else {
            showWindowView();
        }
    }

    public void showWindowView() {
        if (!showing) {
            showing = true;
            WindowManager.LayoutParams params = getParams();
            params.gravity = Gravity.CENTER;
            params.setTitle("AppWindow");
            if (mWindowManager != null) {
                mWindowManager.addView(mPopupView, params);
                PlayInAnim();
            }
        }
    }

    public void hideWindowView() {
        if (mPopupView != null && showing) {
            showing = false;
            if (!animating) {
                PlayOutAnim();
            }
        }
    }

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        return params;
    }

    public void createWindowView() {
        mPopupView = new FrameLayout(mContext);
        mPopupView.removeAllViews();
        mBackGround = new Button(mContext);
        mBackGround.setClickable(false);
        mBackGround.setBackgroundColor(mColor);
        float opacity = (255f * (mOpacity * 0.01f));
        mBackGround.getBackground().setAlpha((int)opacity);
        View windowView = View.inflate(mContext, R.layout.aokp_app_window, null);
        mWindowMain = (LinearLayout) windowView.findViewById(R.id.window_main);
        mWindowLabel = (TextView) windowView.findViewById(R.id.window_label);
        mWindowDividerTop = (ImageView) windowView.findViewById(R.id.window_divider_top);
        mWindowDividerBottom = (ImageView) windowView.findViewById(R.id.window_divider_bottom);
        mCloseButton = (Button) windowView.findViewById(R.id.close);
        mCloseButton.setBackgroundColor(Color.TRANSPARENT);
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (showing) {
                    hideWindowView();
                }
            }
        });
        mCloseButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    mCloseButton.setBackgroundColor((mTextColor != -1) ? mTextColor : Color.CYAN);
                    break;
                case MotionEvent.ACTION_CANCEL :
                case MotionEvent.ACTION_UP:
                    mCloseButton.setBackgroundColor(Color.TRANSPARENT);
                    break;
                }
                return false;
            }
        });
        if (mTextColor != -1) {
            mWindowLabel.setTextColor(mTextColor);
            mWindowDividerTop.setBackgroundColor(mTextColor);
            mWindowDividerBottom.setBackgroundColor(mTextColor);
            mCloseButton.setTextColor(mTextColor);
        }
        mWindow = (LinearLayout) windowView.findViewById(R.id.window);
        setupWindow();
        mWindow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    if (showing) {
                        hideWindowView();
                    }
                    return true;
                }
                return false;
            }
        });
        mPopupView.addView(mBackGround, backgroundParams);
        mPopupView.addView(windowView, scrollParams);
    }

    public Animation PlayInAnim() {
        if (mWindowMain != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.fade_in);
            animation.setStartOffset(0);
            mWindowMain.startAnimation(animation);
            return animation;
        }
        return null;
    }

    public Animation PlayOutAnim() {
        if (mWindowMain != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.fade_out);
            animation.setStartOffset(0);
            mWindowMain.startAnimation(animation);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mWindowManager.removeView(mPopupView);
                    animating = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            return animation;
        }
        return null;
    }

    private void setupWindow() {
        mApps.clear();
        PackageManager pm = mContext.getPackageManager();
        List<PackageInfo> packs = pm.getInstalledPackages(0);
        for (int i = 0; i < packs.size(); i++) {
            PackageInfo p = packs.get(i);
            Intent intent = new Intent();
            intent = pm.getLaunchIntentForPackage(p.packageName);
            if (intent != null) {
                mApps.add(intent.toUri(0));
            }
        }
        ScrollView gv = new ScrollView(mContext);
        // 4 == Number of columns... make this user changeable...
        gv = AokpRibbonHelper.getGridView(mContext, sortApps(mApps), mTextColor, mColumns);
        mWindow.addView(gv, scrollParams);
    }

    private  ArrayList<String> sortApps(ArrayList<String> apps) {
        ArrayList<String> mGoodName = new ArrayList<String>();
        ArrayList<String> mTemp = new ArrayList<String>();
        for (int i = 0; i < apps.size(); i++) {
            mGoodName.add(NavBarHelpers.getProperSummary(mContext, apps.get(i)));
        }
        for (int i = 0; i < mGoodName.size(); i++) {
            mTemp.add(mGoodName.get(i));
        }
        Collections.sort(mTemp, String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < apps.size(); i++) {
            int j = mGoodName.indexOf(mTemp.get(i));
            mTemp.set(i, apps.get(j));
        }
        return mTemp;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_WINDOW_COLOR_BG), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_WINDOW_COLOR_TEXT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_WINDOW_OPACITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_WINDOW_COLUMNS), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
    protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mTextColor = Settings.System.getInt(cr,
                 Settings.System.APP_WINDOW_COLOR_TEXT, -1);
        mColor = Settings.System.getInt(cr,
                 Settings.System.APP_WINDOW_COLOR_BG, Color.BLACK);
        mColumns = Settings.System.getInt(cr,
                 Settings.System.APP_WINDOW_COLUMNS, 5);
        mOpacity = Settings.System.getInt(cr,
                 Settings.System.APP_WINDOW_OPACITY, 255);
        createWindowView();
    }

    public class WindowReceiver extends BroadcastReceiver {
        public static final String ACTION_TOGGLE_APP_WINDOW = "com.android.systemui.ACTION_TOGGLE_APP_WINDOW";
        public static final String ACTION_SHOW_APP_WINDOW = "com.android.systemui.ACTION_SHOW_APP_WINDOW";
        public static final String ACTION_HIDE_APP_WINDOW = "com.android.systemui.ACTION_HIDE_APP_WINDOW";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_TOGGLE_APP_WINDOW.equals(action)) {
                toggleRibbonView();
            } else if (ACTION_SHOW_APP_WINDOW.equals(action)) {
                if (!showing) {
                    showWindowView();
                }
            } else if (ACTION_HIDE_APP_WINDOW.equals(action)) {
                if (showing) {
                    hideWindowView();
                }
            }
        }
    }
}
