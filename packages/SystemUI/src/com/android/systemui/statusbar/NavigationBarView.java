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

package com.android.systemui.statusbar;

import java.io.File;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.util.aokp.BackgroundAlphaColorDrawable;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.R;
import com.android.systemui.TransparencyManager;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.policy.key.ExtensibleKeyButtonView;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED= true;

    final static boolean ANIMATE_HIDE_TRANSITION = false; // turned off because it introduces unsightly delay when videos goes to full screen

    protected IStatusBarService mBarService;
    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    float mButtonWidth, mMenuWidth;
    float mGlowScale = 1.8f;
    boolean mVertical;
    boolean mScreenOn;

    private boolean mNavBarAutoHide = false;
    private boolean isRotating = false;

    boolean mHidden, mLowProfile, mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;
    private Drawable mBackIcon, mBackAltIcon;
    private boolean mMenuArrowKeys;
    
    public DelegateViewHelper mDelegateHelper;
    private BaseStatusBar mBar;
    private SettingsObserver mSettingsObserver;
    private Context mContext;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    int mNumberOfButtons = 3;

    // Will determine if NavBar goes to the left side in Landscape Mode
    private boolean mLeftyMode;

    /* 0 = Phone UI
     * 1 = Tablet UI
     * 2 = Phablet UI
     */
    int mCurrentUIMode = 0;

    int mNavigationBarColor = -1;

    private TransparencyManager mTransparencyManager;

    public String[] mClickActions = new String[7];
    public String[] mLongpressActions = new String[7];
    public String[] mPortraitIcons = new String[7];

    public final static int StockButtonsQty = 3;
    public final static String[] StockClickActions = {
        AwesomeConstant.ACTION_BACK.value(),
        AwesomeConstant.ACTION_HOME.value(),
        AwesomeConstant.ACTION_RECENTS.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value() };
    public final static String[] StockLongpress = {
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value(),
        AwesomeConstant.ACTION_NULL.value() };

    FrameLayout rot0;
    FrameLayout rot90;

    //Definitions for NavBar Menu button customization
    public final static int SHOW_LEFT_MENU = 1;
    public final static int SHOW_RIGHT_MENU = 0;
    public final static int SHOW_BOTH_MENU = 2;
    public final static int SHOW_DONT = 4;

    public final static int VISIBILITY_SYSTEM = 0;
    public final static int VISIBILITY_SYSTEM_AND_INVIZ = 3;
    public final static int VISIBILITY_NEVER = 1;
    public final static int VISIBILITY_ALWAYS = 2;

    public static final int KEY_MENU_RIGHT = 2;
    public static final int KEY_MENU_LEFT = 5;
    public static final int KEY_ARROW_LEFT = 21; // pretty cute right
    public static final int KEY_ARROW_RIGHT = 22;
    public static final int KEY_BACK_ALT = 1000;



    private int mMenuVisbility;
    private int mMenuLocation;

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Slog.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
        //mDelegateHelper.setLefty(mLeftyMode);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mDelegateHelper.setBar(phoneStatusBar);
        mBar = phoneStatusBar;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDelegateHelper != null) {
            mDelegateHelper.onInterceptTouchEvent(event);
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mBar != null) {
            mBar.onBarTouchEvent(event);
        }
        return mDelegateHelper.onInterceptTouchEvent(event);
    }

    private H mHandler = new H();

    public View getLeftMenuButton() {
        return mCurrentView.findViewById(R.id.menu_left);
    }

    public View getRightMenuButton() {
        return mCurrentView.findViewById(R.id.menu);
    }

    public View getRecentsButton() {
        return mCurrentView.findViewById(R.id.recent_apps);
    }

    public View getBackButton() {
        return mCurrentView.findViewById(R.id.back);
    }

    public View getHomeButton() {
        return mCurrentView.findViewById(R.id.home);
    }

    // for when home is disabled, but search isn't
    public View getSearchLight() {
        return mCurrentView.findViewById(R.id.search_light);
    }

    public View getLeftArrow() {
        return mCurrentView.findViewById(KEY_ARROW_LEFT);
    }

    public View getRightArrow() {
        return mCurrentView.findViewById(KEY_ARROW_RIGHT);
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mHidden = false;

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        final Resources res = mContext.getResources();
        mVertical = false;
        mShowMenu = false;
        mDelegateHelper = new DelegateViewHelper(this);

        mBackIcon = NavBarHelpers.getIconImage(mContext, AwesomeConstant.ACTION_BACK.value());
        mBackAltIcon = ((KeyButtonView)generateKey(false, KEY_BACK_ALT)).getDrawable();
        mButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_key_width);
        mMenuWidth = res.getDimensionPixelSize(R.dimen.navigation_menu_key_width);
    }

    public void setTransparencyManager(TransparencyManager tm) {
        mTransparencyManager = tm;
    }

    private void makeBar() {

        ((LinearLayout) rot0.findViewById(R.id.nav_buttons)).removeAllViews();
        ((LinearLayout) rot0.findViewById(R.id.lights_out)).removeAllViews();
        ((LinearLayout) rot90.findViewById(R.id.nav_buttons)).removeAllViews();
        ((LinearLayout) rot90.findViewById(R.id.lights_out)).removeAllViews();

        for (int i = 0; i <= 1; i++) {
            boolean landscape = (i == 1);

            LinearLayout navButtonLayout = (LinearLayout) (landscape ? rot90
                    .findViewById(R.id.nav_buttons) : rot0
                    .findViewById(R.id.nav_buttons));

            LinearLayout lightsOut = (LinearLayout) (landscape ? rot90
                    .findViewById(R.id.lights_out) : rot0
                    .findViewById(R.id.lights_out));

            // Add the Main Nav Buttons
            String iconUri = "";
            for (int j = 0; j < mNumberOfButtons; j++) {
                ExtensibleKeyButtonView v = generateKey(landscape, mClickActions[j],
                        mLongpressActions[j],
                        mPortraitIcons[j]);
                v.setTag((landscape ? "key_land_" : "key_") + j);
                iconUri = mPortraitIcons[j];
                if (iconUri != null && iconUri.length() > 0) {
                    // custom icon from the URI here
                    File f = new File(Uri.parse(iconUri).getPath());
                    if (f.exists()) {
                        v.setImageDrawable(new BitmapDrawable(getResources(), f.getAbsolutePath()));
                    }
                    v.setTint(false);
                } else {
                    v.setImageDrawable(NavBarHelpers.getIconImage(mContext, mClickActions[j]));
                    v.setTint(mClickActions[j].startsWith("**"));
                }
                addButton(navButtonLayout, v, landscape && !mLeftyMode);
                // if we are in LeftyMode, then we want to add to end, like Portrait
                addLightsOutButton(lightsOut, v, landscape && !mLeftyMode, false);

                if (v.getId() == R.id.back){
                    mBackIcon = v.getDrawable();
                }
                if (mNumberOfButtons == 3 && j != (mNumberOfButtons - 1) && mCurrentUIMode != 2) {
                    // add separator view here
                    View separator = new View(mContext);
                    separator.setLayoutParams(getSeparatorLayoutParams(landscape));
                    addButton(navButtonLayout, separator, landscape);
                    addLightsOutButton(lightsOut, separator, landscape, true);
                }
            }
            if (mMenuLocation != SHOW_DONT) {
                // add left menu
                View leftMenuKey = generateKey(landscape, KEY_MENU_LEFT);
                    // since we didn't add these at the beginning, we need to insert it now
                    // the behavior is backwards from landscape (ie, insert at beginning
                    // if portrait, add to end if landscape
                addButton(navButtonLayout, leftMenuKey, !landscape || (landscape && mLeftyMode));
                addLightsOutButton(lightsOut, leftMenuKey, !landscape || (landscape && mLeftyMode), true);
             // add right menu
                View rightMenuKey = generateKey(landscape, KEY_MENU_RIGHT);
                addButton(navButtonLayout, rightMenuKey, landscape && !mLeftyMode);
                addLightsOutButton(lightsOut, rightMenuKey, landscape && !mLeftyMode, true);
            } else if(mMenuArrowKeys) {
                    addButton(navButtonLayout, generateKey(landscape, KEY_ARROW_LEFT), !landscape);
                    addButton(navButtonLayout, generateKey(landscape, KEY_ARROW_RIGHT), landscape);
            }
            if (mCurrentUIMode == 1 || mCurrentUIMode == 2) {
                // add spacer views to the outside edges to help with Glowscale cutoff
                // We'll only do this for Tablet UI for now.  It was suffering the worst from
                // glowscale cutoff.  I may add some user adjustable padding at a later date that
                // we'll apply to all styles of NavBar.
                View spacer = new View(mContext);
                spacer.setLayoutParams(getSeparatorLayoutParams(landscape));
                View spacer1 = new View(mContext);
                spacer1.setLayoutParams(getSeparatorLayoutParams(landscape));
                navButtonLayout.addView(spacer, 0);
                navButtonLayout.addView(spacer1);
                View spacer2 = new View(mContext);
                spacer2.setLayoutParams(getSeparatorLayoutParams(landscape));
                View spacer3 = new View(mContext);
                spacer3.setLayoutParams(getSeparatorLayoutParams(landscape));
                lightsOut.addView(spacer2, 0);
                lightsOut.addView(spacer3);
            }
        }
        Drawable bg = mContext.getResources().getDrawable(R.drawable.nav_bar_bg);
        if(bg instanceof ColorDrawable) {
            BackgroundAlphaColorDrawable bacd = new BackgroundAlphaColorDrawable(
                    mNavigationBarColor > 0 ? mNavigationBarColor : ((ColorDrawable) bg).getColor());
            setBackground(bacd);
        }
        if(mTransparencyManager != null) {
            mTransparencyManager.update();
        }
    }

    private void addLightsOutButton(LinearLayout root, View v, boolean landscape, boolean empty) {

        ImageView addMe = new ImageView(mContext);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? R.drawable.ic_sysbar_lights_out_dot_large
                : R.drawable.ic_sysbar_lights_out_dot_small);
        addMe.setScaleType(ImageView.ScaleType.CENTER);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        if (landscape)
            root.addView(addMe, 0);
        else
            root.addView(addMe);
    }

    private void addButton(ViewGroup root, View addMe, boolean landscape) {
        if (landscape)
            root.addView(addMe, 0);
        else
            root.addView(addMe);
    }

    private View generateKey(boolean landscape, int keyId) {
        KeyButtonView v = null;
        Resources r = getResources();

        switch (keyId) {

            case KEY_MENU_RIGHT:
                v = new KeyButtonView(mContext, null);
                v.setLayoutParams(getLayoutParams(landscape, (mCurrentUIMode == 1) ? mButtonWidth : mMenuWidth));

                v.setId(R.id.menu);
                v.setCode(KeyEvent.KEYCODE_MENU);
                if (mCurrentUIMode == 1) {
                    v.setImageResource(R.drawable.ic_sysbar_menu_big);
                    v.setVisibility(View.GONE);
                } else {
                    v.setImageResource(landscape ? R.drawable.ic_sysbar_menu_land
                        : R.drawable.ic_sysbar_menu);
                    v.setVisibility(View.INVISIBLE);
                }
                v.setContentDescription(r.getString(R.string.accessibility_menu));
                v.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                v.setTint(true);
                break;
            case KEY_MENU_LEFT:
                v = new KeyButtonView(mContext, null);
                v.setLayoutParams(getLayoutParams(landscape, (mCurrentUIMode == 1) ? mButtonWidth : mMenuWidth));

                v.setId(R.id.menu_left);
                v.setCode(KeyEvent.KEYCODE_MENU);
                if (mCurrentUIMode == 1) {
                    v.setImageResource(R.drawable.ic_sysbar_menu_big);
                    v.setVisibility(View.GONE);
                } else {
                    v.setImageResource(landscape ? R.drawable.ic_sysbar_menu_land
                        : R.drawable.ic_sysbar_menu);
                    v.setVisibility(View.INVISIBLE);
                }
                v.setContentDescription(r.getString(R.string.accessibility_menu));
                v.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                v.setTint(true);
                break;
            case KEY_ARROW_LEFT:
                v = new KeyButtonView(mContext, null);
                v.setLayoutParams(getLayoutParams(landscape, mButtonWidth));
                v.setId(KEY_ARROW_LEFT);
                v.setCode(KeyEvent.KEYCODE_DPAD_LEFT);
                v.setImageResource(R.drawable.ic_sysbar_ime_left);
                v.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                v.setVisibility(View.GONE);
                v.setSupportsLongPress(true);
                v.setTint(true);
                break;
            case KEY_ARROW_RIGHT:
                v = new KeyButtonView(mContext, null);
                v.setLayoutParams(getLayoutParams(landscape, mButtonWidth));
                v.setId(KEY_ARROW_RIGHT);
                v.setCode(KeyEvent.KEYCODE_DPAD_RIGHT);
                v.setImageResource(R.drawable.ic_sysbar_ime_right);
                v.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                v.setVisibility(View.GONE);
                v.setSupportsLongPress(true);
                v.setTint(true);
                break;
            case KEY_BACK_ALT:
                v = new KeyButtonView(mContext, null);
                v.setLayoutParams(getLayoutParams(landscape, mButtonWidth));
                v.setImageResource(R.drawable.ic_sysbar_back_ime);
                v.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                v.setTint(true);
        }
        v.setCustomGlowScale(mGlowScale);
        return v;
    }

    private ExtensibleKeyButtonView generateKey(boolean landscape, String clickAction,
            String longpress,String iconUri) {

        ExtensibleKeyButtonView v = new ExtensibleKeyButtonView(mContext, null,
                clickAction, longpress);
        v.setLayoutParams(getLayoutParams(landscape, mButtonWidth));
        v.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                : R.drawable.ic_sysbar_highlight);
        v.setCustomGlowScale(mGlowScale);
        return v;
    }

    private LayoutParams getLayoutParams(boolean landscape, float px) {
        return landscape ?
                new LayoutParams(LayoutParams.MATCH_PARENT, (int) px, 1f) :
                new LayoutParams((int) px, LayoutParams.MATCH_PARENT, 1f);
    }

    private LayoutParams getSeparatorLayoutParams(boolean landscape) {
        int width = (int) mMenuWidth /2 ;
        // set the outer separator params to half the width of a menu button
        // to keep from squeezing in on the navbar too much, but still allow
        // some breathing room for NavGlow
        return landscape ?
                new LayoutParams(LayoutParams.MATCH_PARENT, (int) width,1f) :
                new LayoutParams((int) width, LayoutParams.MATCH_PARENT,1f);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                setLowProfile(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;

        if (DEBUG) {
            android.widget.Toast.makeText(mContext,
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;
        // We can't guarantee users will set these buttons as targets
        if (getBackButton() != null) {
            getBackButton().setAlpha((0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_NOP)) ? 0.5f : 1.0f);
            ((ImageView)getBackButton()).setImageDrawable(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT))
                    ? (mBackAltIcon)
                    : (mBackIcon));
        }
        if (getHomeButton()!=null) {
            getHomeButton().setAlpha((0 != (hints & StatusBarManager.NAVIGATION_HINT_HOME_NOP)) ? 0.5f : 1.0f);
        }
        if (getRecentsButton()!=null) {
            getRecentsButton().setAlpha((0 != (hints & StatusBarManager.NAVIGATION_HINT_RECENT_NOP)) ? 0.5f : 1.0f);
        }
        updateMenuArrowKeys();
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    private boolean isKeyguardEnabled() {
        KeyguardManager km = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if(km == null) return false;

        return km.isKeyguardLocked();
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
              && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (mCurrentUIMode != 1 && SLIPPERY_WHEN_DISABLED) { // Tabletmode doesn't deal with slippery
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }
        if (!mScreenOn && mCurrentView != null) {
            ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
            LayoutTransition lt = navButtons == null ? null : navButtons.getLayoutTransition();
            if (lt != null) {
                lt.disableTransitionType(
                        LayoutTransition.CHANGE_APPEARING | LayoutTransition.CHANGE_DISAPPEARING |
                        LayoutTransition.APPEARING | LayoutTransition.DISAPPEARING);
            }
        }
        for (int j = 0; j < mNumberOfButtons; j++) {
            View v = (View) findViewWithTag((mVertical ? "key_land_" : "key_") + j);
            if (v != null) {
                int vid = v.getId();
                if (vid == R.id.back) {
                    v.setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
                } else if (vid == R.id.recent_apps) {
                    v.setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);
                } else { // treat all other buttons as same rule as home
                    v.setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);
                }

            }
        }
     // if Home is to be shown, then we hide the Searchlight.
        getSearchLight().setVisibility((isKeyguardEnabled()&& disableHome) ? View.VISIBLE : View.GONE);
        if (mNavBarAutoHide && !isRotating) {
            if (isKeyguardEnabled())
                mBar.setSearchLightOn(true);
            else
                mBar.setSearchLightOn(false);
        }
        isRotating = false;
        updateMenuArrowKeys();
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            try  {
                WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
                wm.updateViewLayout(this, lp);
            } catch (IllegalArgumentException e) {
                // Let it go.  This should only happen when NavBar is on 'AutoHide' so the NavBar exists, but
                // isn't attached to the window at this time.
            }
        }
    }

    public boolean getMenuVisibility() {
        return mShowMenu;
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {

    	if (!force && mShowMenu == show)
            return;

        if (mMenuLocation == SHOW_DONT) {
            return;
        }

        mShowMenu = show;
        boolean localShow = show;

        ImageView leftButton = (ImageView) getLeftMenuButton();
        ImageView rightButton = (ImageView) getRightMenuButton();

        switch (mMenuVisbility) {
            case VISIBILITY_ALWAYS:
                localShow = true;
            case VISIBILITY_SYSTEM:
                if (mCurrentUIMode == 1) {
                    rightButton.setImageResource(R.drawable.ic_sysbar_menu_big);
                    leftButton.setImageResource(R.drawable.ic_sysbar_menu_big);
                } else {
                    rightButton.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                        : R.drawable.ic_sysbar_menu);
                    leftButton.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                            : R.drawable.ic_sysbar_menu);
                }
                break;
            case VISIBILITY_NEVER:
                leftButton
                        .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                rightButton
                        .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                localShow = true;
                break;
            case VISIBILITY_SYSTEM_AND_INVIZ:
                if (localShow) {
                    if (mCurrentUIMode == 1) {
                        rightButton.setImageResource(R.drawable.ic_sysbar_menu_big);
                        leftButton.setImageResource(R.drawable.ic_sysbar_menu_big);
                    } else {
                        rightButton.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                            : R.drawable.ic_sysbar_menu);
                        leftButton.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                : R.drawable.ic_sysbar_menu);
                    }
                } else {
                    localShow = true;
                    leftButton
                            .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                    rightButton
                            .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                }
                break;
        }

        // do this after just in case show was changed
        // Tablet menu buttons should not take up space when hidden.
        switch (mMenuLocation) {
            case SHOW_BOTH_MENU:
                if (mCurrentUIMode==1) {
                    leftButton.setVisibility(localShow ? View.VISIBLE : View.GONE);
                    rightButton.setVisibility(localShow ? View.VISIBLE : View.GONE);
                } else {
                    leftButton.setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                    rightButton.setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                }
                break;
            case SHOW_LEFT_MENU:
                if (mCurrentUIMode==1) {
                    leftButton.setVisibility(localShow ? View.VISIBLE : View.GONE);
                } else {
                    leftButton.setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                }
                rightButton.setVisibility((mCurrentUIMode == 1) ? View.GONE : View.INVISIBLE);
                break;
            default:
            case SHOW_RIGHT_MENU:
                leftButton.setVisibility((mCurrentUIMode == 1) ? View.GONE : View.INVISIBLE);
                if (mCurrentUIMode==1) {
                    rightButton.setVisibility(localShow ? View.VISIBLE : View.GONE);
                } else {
                    rightButton.setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                }
                break;
        }
    }

    public void setLowProfile(final boolean lightsOut) {
        setLowProfile(lightsOut, true, false);
    }

    public void setLowProfile(final boolean lightsOut, final boolean animate, final boolean force) {
        if (!force && lightsOut == mLowProfile) return;

        mLowProfile = lightsOut;

        if (DEBUG) Slog.d(TAG, "setting lights " + (lightsOut?"out":"on"));

        final View navButtons = mCurrentView.findViewById(R.id.nav_buttons);
        final View lowLights = mCurrentView.findViewById(R.id.lights_out);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        if (!animate) {
            navButtons.setAlpha(lightsOut ? 0f : 1f);

            lowLights.setAlpha(lightsOut ? 1f : 0f);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            navButtons.animate()
                .alpha(lightsOut ? 0f : 1f)
                .setDuration(lightsOut ? 750 : 250)
                .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                .alpha(lightsOut ? 1f : 0f)
                .setDuration(lightsOut ? 750 : 250)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        lowLights.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    public void setHidden(final boolean hide) {
        if (hide == mHidden) return;

        mHidden = hide;
        Slog.d(TAG,
            (hide ? "HIDING" : "SHOWING") + " navigation bar");

        // bring up the lights no matter what
        setLowProfile(false);
    }

    @Override
    public void onFinishInflate() {
    	 rot0 = (FrameLayout) findViewById(R.id.rot0);
         rot90 = (FrameLayout) findViewById(R.id.rot90);

         mRotatedViews[Surface.ROTATION_0] =
                 mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

         mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

         mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                 ? findViewById(R.id.rot90)
                 : findViewById(R.id.rot270);

         for (View v : mRotatedViews) {
             // this helps avoid drawing artifacts with glowing navigation keys
             ViewGroup group = (ViewGroup) v.findViewById(R.id.nav_buttons);
             group.setMotionEventSplittingEnabled(false);
         }
         mCurrentView = mRotatedViews[Surface.ROTATION_0];

         // this takes care of making the buttons
         mSettingsObserver = new SettingsObserver(new Handler());
         updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        if (mCurrentUIMode !=0) { // this is either a tablet of Phablet.  Need to stay at Rot_0
            mCurrentView = mRotatedViews[Surface.ROTATION_0];
        } else {
            mCurrentView = mRotatedViews[rot];
        }
        mCurrentView.setVisibility(View.VISIBLE);

        // force the low profile & disabled states into compliance
        setLowProfile(mLowProfile, false, true /* force */);
        isRotating = true;
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);
        setNavigationIconHints(mNavigationIconHints, true);
        if (DEBUG) {
            Slog.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDelegateHelper.setInitialTouchRegion(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Slog.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Slog.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    protected void updateMenuArrowKeys() {
        if (!mMenuArrowKeys)
            return;

        final KeyButtonView leftMenu = (KeyButtonView) getLeftMenuButton();
        final KeyButtonView rightMenu = (KeyButtonView) getRightMenuButton();

        final KeyButtonView leftArrow = (KeyButtonView) getLeftArrow();
        final KeyButtonView rightArrow = (KeyButtonView) getRightArrow();

        final boolean showingIme = ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0);
        final boolean keygaurdProbablyEnabled = !((mDisabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);

        int state = -1;
        if(keygaurdProbablyEnabled && showingIme) state = 1;
        else if (keygaurdProbablyEnabled && !showingIme) state = 2;
        else if (!keygaurdProbablyEnabled && showingIme) state = 3;
        else if (!keygaurdProbablyEnabled && !showingIme) state = 4;

        switch(state) {
            case 1:
                setMenuVisibility(mShowMenu, true);
                if (leftArrow != null) {
                    leftArrow.setVisibility(View.VISIBLE);
                }
                if (rightArrow != null) {
                    rightArrow.setVisibility(View.VISIBLE);
                }
                if (mMenuLocation != SHOW_DONT) {
                    if (leftMenu != null) {
                        leftMenu.setCode(KeyEvent.KEYCODE_DPAD_LEFT);
                        leftMenu.setImageResource(R.drawable.ic_sysbar_ime_left);
                        leftMenu.setSupportsLongPress(true);
                        leftMenu.setVisibility(View.VISIBLE);
                    }
                    if (rightMenu != null) {
                        rightMenu.setCode(KeyEvent.KEYCODE_DPAD_RIGHT);
                        rightMenu.setImageResource(R.drawable.ic_sysbar_ime_right);
                        rightMenu.setSupportsLongPress(true);
                        rightMenu.setVisibility(View.VISIBLE);
                    }
                }
                break;
            case 2:
                if (leftArrow != null) {
                    leftArrow.setVisibility(View.GONE);
                }
                if (rightArrow != null) {
                    rightArrow.setVisibility(View.GONE);
                }
                if (mMenuLocation != SHOW_DONT) {
                    if (leftMenu != null && leftMenu.getCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                        leftMenu.setCode(KeyEvent.KEYCODE_MENU);
                        leftMenu.setSupportsLongPress(false);
                    }
                    if (rightMenu != null && rightMenu.getCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        rightMenu.setCode(KeyEvent.KEYCODE_MENU);
                        rightMenu.setSupportsLongPress(false);
                    }
                }
                setMenuVisibility(mShowMenu, true);
                break;
            case 3:
                if (leftArrow != null) {
                    leftArrow.setVisibility(View.INVISIBLE);
                }
                if (rightArrow != null) {
                    rightArrow.setVisibility(View.INVISIBLE);
                }
                if (mMenuLocation != SHOW_DONT) {
                    if (leftMenu != null && leftMenu.getCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                        leftMenu.setVisibility(View.INVISIBLE);
                    }
                    if (rightMenu != null && rightMenu.getCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        rightMenu.setVisibility(View.INVISIBLE);
                    }
                }
                break;
            case 4:
            default:
                break;
        }
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Slog.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)", 
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Slog.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_COLOR), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.MENU_LOCATION), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.MENU_VISIBILITY), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTONS_QTY), false,
                    this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_LEFTY_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_ENABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_MENU_ARROW_KEYS), false, this);

            for (int j = 0; j < 7; j++) { // watch all 7 settings for changes.
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.NAVIGATION_CUSTOM_ACTIVITIES[j]),
                        false,
                        this);
                resolver.registerContentObserver(
                        Settings.System
                                .getUriFor(Settings.System.NAVIGATION_LONGPRESS_ACTIVITIES[j]),
                        false,
                        this);
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.NAVIGATION_CUSTOM_APP_ICONS[j]),
                        false,
                        this);
            }
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    /*
     * ]0 < alpha < 1[
     */
    public void setBackgroundAlpha(float alpha) {
        Drawable bg = getBackground();
        if(bg == null) return;

        if(bg instanceof BackgroundAlphaColorDrawable) {
         // if there's a custom color while the lockscreen is on, clear it momentarily, otherwise it won't match.
            if(mNavigationBarColor > 0) {
                if(isKeyguardEnabled()) {
                    ((BackgroundAlphaColorDrawable) bg).setBgColor(-1);
                } else {
                    ((BackgroundAlphaColorDrawable) bg).setBgColor(mNavigationBarColor);
                }
            }
        }
        int a = Math.round(alpha * 255);
        bg.setAlpha(a);
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mMenuLocation = Settings.System.getInt(resolver,
                Settings.System.MENU_LOCATION, SHOW_RIGHT_MENU);
        mNavigationBarColor = Settings.System.getInt(resolver,
                Settings.System.NAVIGATION_BAR_COLOR, -1);
        mMenuVisbility = Settings.System.getInt(resolver,
                Settings.System.MENU_VISIBILITY, VISIBILITY_SYSTEM);
        mMenuArrowKeys = Settings.System.getBoolean(resolver,
                Settings.System.NAVIGATION_BAR_MENU_ARROW_KEYS, true);
        mCurrentUIMode = Settings.System.getInt(resolver,
                Settings.System.CURRENT_UI_MODE,0);
        mLeftyMode = Settings.System.getBoolean(resolver,
                Settings.System.NAVIGATION_BAR_LEFTY_MODE, false);
        mNavBarAutoHide = Settings.System.getBoolean(resolver,
                Settings.System.NAV_HIDE_ENABLE, false);
        mNumberOfButtons = Settings.System.getInt(resolver,
                Settings.System.NAVIGATION_BAR_BUTTONS_QTY, 0);
        if (mNumberOfButtons == 0) {
            mNumberOfButtons = StockButtonsQty;
            Settings.System.putInt(resolver,
                    Settings.System.NAVIGATION_BAR_BUTTONS_QTY, StockButtonsQty);
        }
        // I would like to figure out a way to dynamically adjust the Glowscale based on
        // Width of the NavBar vs the number & width of the buttons, but that is proving to be
        // difficult.  for now, we'll simply adjust the scale based on the number of buttons we have.
        mGlowScale = 2f - (mNumberOfButtons * 0.1f);
        for (int j = 0; j < 7; j++) {
            mClickActions[j] = Settings.System.getString(resolver,
                    Settings.System.NAVIGATION_CUSTOM_ACTIVITIES[j]);
            if (mClickActions[j] == null) {
                mClickActions[j] = StockClickActions[j];
                Settings.System.putString(resolver,
                        Settings.System.NAVIGATION_CUSTOM_ACTIVITIES[j], mClickActions[j]);
            }

            mLongpressActions[j] = Settings.System.getString(resolver,
                    Settings.System.NAVIGATION_LONGPRESS_ACTIVITIES[j]);
            if (mLongpressActions[j] == null) {
                mLongpressActions[j] = StockLongpress[j];
                Settings.System.putString(resolver,
                        Settings.System.NAVIGATION_LONGPRESS_ACTIVITIES[j], mLongpressActions[j]);
            }
            mPortraitIcons[j] = Settings.System.getString(resolver,
                    Settings.System.NAVIGATION_CUSTOM_APP_ICONS[j]);
            if (mPortraitIcons[j] == null) {
                mPortraitIcons[j] = "";
                Settings.System.putString(resolver,
                        Settings.System.NAVIGATION_CUSTOM_APP_ICONS[j], "");
            }
        }
        makeBar();
        setMenuVisibility(mShowMenu);
        updateMenuArrowKeys();
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }
}
