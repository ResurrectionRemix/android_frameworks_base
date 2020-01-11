/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.qs.customize;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toolbar;
import android.widget.Toolbar.OnMenuItemClickListener;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.systemui.R;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.plugins.qs.QS;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.QSDetailClipper;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.NotificationsQuickSettingsContainer;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.KeyguardMonitor.Callback;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Allows full-screen customization of QS, through show() and hide().
 *
 * This adds itself to the status bar window, so it can appear on top of quick settings and
 * *someday* do fancy animations to get into/out of it.
 */
public class QSCustomizer extends LinearLayout implements OnMenuItemClickListener {

    private static final int MENU_RESET = Menu.FIRST;
    private static final String EXTRA_QS_CUSTOMIZING = "qs_customizing";
    private static final String TAG = "QSCustomizer";

    private final QSDetailClipper mClipper;
    private final LightBarController mLightBarController;
    private KeyguardMonitor mKeyguardMonitor;
    private final ScreenLifecycle mScreenLifecycle;
    private final TileQueryHelper mTileQueryHelper;
    private final View mTransparentView;

    private boolean isShown;
    private QSTileHost mHost;
    private RecyclerView mRecyclerView;
    private TileAdapter mTileAdapter;
    private Toolbar mToolbar;
    private boolean mCustomizing;
    private NotificationsQuickSettingsContainer mNotifQsContainer;
    private QS mQs;
    private int mX;
    private int mY;
    private boolean mOpening;
    private boolean mIsShowingNavBackdrop;
    private GridLayoutManager mGlm;
    private boolean mHeaderImageEnabled;
    private int mHeaderImageHeight;

    @Inject
    public QSCustomizer(Context context, AttributeSet attrs,
            LightBarController lightBarController,
            KeyguardMonitor keyguardMonitor,
            ScreenLifecycle screenLifecycle) {
        super(new ContextThemeWrapper(context, R.style.edit_theme), attrs);

        LayoutInflater.from(getContext()).inflate(R.layout.qs_customize_panel_content, this);
        mClipper = new QSDetailClipper(findViewById(R.id.customize_container));
        mToolbar = findViewById(com.android.internal.R.id.action_bar);
        TypedValue value = new TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.homeAsUpIndicator, value, true);
        mToolbar.setNavigationIcon(
                getResources().getDrawable(value.resourceId, mContext.getTheme()));
        mToolbar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        mToolbar.setOnMenuItemClickListener(this);
        mToolbar.getMenu().add(Menu.NONE, MENU_RESET, 0,
                mContext.getString(com.android.internal.R.string.reset));
        mToolbar.setTitle(R.string.qs_edit);
        mRecyclerView = findViewById(android.R.id.list);
        mTransparentView = findViewById(R.id.customizer_transparent_view);
        mTileAdapter = new TileAdapter(getContext());
        mTileQueryHelper = new TileQueryHelper(context, mTileAdapter);
        mRecyclerView.setAdapter(mTileAdapter);
        mTileAdapter.getItemTouchHelper().attachToRecyclerView(mRecyclerView);
        mGlm = new GridLayoutManager(getContext(), 5);
        mGlm.setSpanSizeLookup(mTileAdapter.getSizeLookup());
        mRecyclerView.setLayoutManager(mGlm);
        mRecyclerView.addItemDecoration(mTileAdapter.getItemDecoration());
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setMoveDuration(TileAdapter.MOVE_DURATION);
        mRecyclerView.setItemAnimator(animator);
        mLightBarController = lightBarController;
        mKeyguardMonitor = keyguardMonitor;
        mScreenLifecycle = screenLifecycle;
        updateNavBackDrop(getResources().getConfiguration());

        updateSettings();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateNavBackDrop(newConfig);
        updateResources();
    }

    public void updateResources() {
        LayoutParams lp = (LayoutParams) mTransparentView.getLayoutParams();
        lp.height = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height);
        if (mHeaderImageEnabled) {
            lp.height += mHeaderImageHeight;
        }
        mTransparentView.setLayoutParams(lp);
        int columns;
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            columns = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_COLUMNS_PORTRAIT, 4,
                    UserHandle.USER_CURRENT);
        } else {
            columns = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_COLUMNS_LANDSCAPE, 4,
                    UserHandle.USER_CURRENT);
        }
        if (columns < 1) {
            columns = 1;
        }
        mTileAdapter.setColumns(columns);
        mGlm.setSpanCount(columns);

        updateSettings();
    }

    private void updateNavBackDrop(Configuration newConfig) {
        View navBackdrop = findViewById(R.id.nav_bar_background);
        mIsShowingNavBackdrop = newConfig.smallestScreenWidthDp >= 600
                || newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE;
        if (navBackdrop != null) {
            navBackdrop.setVisibility(mIsShowingNavBackdrop ? View.VISIBLE : View.GONE);
        }
        updateNavColors();
    }

    private void updateNavColors() {
        mLightBarController.setQsCustomizing(mIsShowingNavBackdrop && isShown);
    }

    public void setHost(QSTileHost host) {
        mHost = host;
        mTileAdapter.setHost(host);
    }

    public void setContainer(NotificationsQuickSettingsContainer notificationsQsContainer) {
        mNotifQsContainer = notificationsQsContainer;
    }

    public void setQs(QS qs) {
        mQs = qs;
    }

    /** Animate and show QSCustomizer panel.
     * @param x,y Location on screen of {@code edit} button to determine center of animation.
     */
    public void show(int x, int y) {
        if (!isShown) {
            int containerLocation[] = findViewById(R.id.customize_container).getLocationOnScreen();
            mX = x - containerLocation[0];
            mY = y - containerLocation[1];
            MetricsLogger.visible(getContext(), MetricsProto.MetricsEvent.QS_EDIT);
            isShown = true;
            mOpening = true;
            setTileSpecs();
            setVisibility(View.VISIBLE);
            mClipper.animateCircularClip(mX, mY, true, mExpandAnimationListener);
            queryTiles();
            mNotifQsContainer.setCustomizerAnimating(true);
            mNotifQsContainer.setCustomizerShowing(true);
            mKeyguardMonitor.addCallback(mKeyguardCallback);
            updateNavColors();
        }
    }


    public void showImmediately() {
        if (!isShown) {
            setVisibility(VISIBLE);
            mClipper.showBackground();
            isShown = true;
            setTileSpecs();
            setCustomizing(true);
            queryTiles();
            mNotifQsContainer.setCustomizerAnimating(false);
            mNotifQsContainer.setCustomizerShowing(true);
            mKeyguardMonitor.addCallback(mKeyguardCallback);
            updateNavColors();
        }
    }

    private void queryTiles() {
        mTileQueryHelper.queryTiles(mHost);
    }

    public void hide() {
        final boolean animate = mScreenLifecycle.getScreenState() != ScreenLifecycle.SCREEN_OFF;
        if (isShown) {
            MetricsLogger.hidden(getContext(), MetricsProto.MetricsEvent.QS_EDIT);
            isShown = false;
            mToolbar.dismissPopupMenus();
            setCustomizing(false);
            save();
            if (animate) {
                mClipper.animateCircularClip(mX, mY, false, mCollapseAnimationListener);
            } else {
                setVisibility(View.GONE);
            }
            mNotifQsContainer.setCustomizerAnimating(animate);
            mNotifQsContainer.setCustomizerShowing(false);
            mKeyguardMonitor.removeCallback(mKeyguardCallback);
            updateNavColors();
        }
    }

    public boolean isShown() {
        return isShown;
    }

    private void setCustomizing(boolean customizing) {
        mCustomizing = customizing;
        mQs.notifyCustomizeChanged();
    }

    public boolean isCustomizing() {
        return mCustomizing || mOpening;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                MetricsLogger.action(getContext(), MetricsProto.MetricsEvent.ACTION_QS_EDIT_RESET);
                reset();
                break;
        }
        return false;
    }

    private void reset() {
        ArrayList<String> tiles = new ArrayList<>();
        String defTiles = mContext.getString(R.string.quick_settings_tiles_default);
        for (String tile : defTiles.split(",")) {
            tiles.add(tile);
        }
        mTileAdapter.resetTileSpecs(mHost, tiles);
    }

    private void setTileSpecs() {
        List<String> specs = new ArrayList<>();
        for (QSTile tile : mHost.getTiles()) {
            specs.add(tile.getTileSpec());
        }
        mTileAdapter.setTileSpecs(specs);
        mRecyclerView.setAdapter(mTileAdapter);
    }

    private void save() {
        if (mTileQueryHelper.isFinished()) {
            mTileAdapter.saveSpecs(mHost);
        }
    }


    public void saveInstanceState(Bundle outState) {
        if (isShown) {
            mKeyguardMonitor.removeCallback(mKeyguardCallback);
        }
        outState.putBoolean(EXTRA_QS_CUSTOMIZING, mCustomizing);
    }

    public void restoreInstanceState(Bundle savedInstanceState) {
        boolean customizing = savedInstanceState.getBoolean(EXTRA_QS_CUSTOMIZING);
        if (customizing) {
            setVisibility(VISIBLE);
            addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft,
                        int oldTop, int oldRight, int oldBottom) {
                    removeOnLayoutChangeListener(this);
                    showImmediately();
                }
            });
        }
    }
    /** @param x,y Location on screen of animation center.
     */
    public void setEditLocation(int x, int y) {
        int containerLocation[] = findViewById(R.id.customize_container).getLocationOnScreen();
        mX = x - containerLocation[0];
        mY = y - containerLocation[1];
    }

    private final Callback mKeyguardCallback = new Callback() {
        @Override
        public void onKeyguardShowingChanged() {
            if (!isAttachedToWindow()) return;
            if (mKeyguardMonitor.isShowing() && !mOpening) {
                hide();
            }
        }
    };

    private final AnimatorListener mExpandAnimationListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (isShown) {
                setCustomizing(true);
            }
            mOpening = false;
            mNotifQsContainer.setCustomizerAnimating(false);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mOpening = false;
            mNotifQsContainer.setCustomizerAnimating(false);
        }
    };

    private final AnimatorListener mCollapseAnimationListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (!isShown) {
                setVisibility(View.GONE);
            }
            mNotifQsContainer.setCustomizerAnimating(false);
            mRecyclerView.setAdapter(mTileAdapter);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            if (!isShown) {
                setVisibility(View.GONE);
            }
            mNotifQsContainer.setCustomizerAnimating(false);
        }
    };

    private void updateSettings() {
        final Resources res = mContext.getResources();
        updateHeaderImage();
    }

    private void updateHeaderImage() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.OMNI_STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        int mImageHeight = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, 25,
                UserHandle.USER_CURRENT);
        switch (mImageHeight) {
            case 0:
                mHeaderImageHeight = 0;
                break;
            case 1:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1);
                break;
            case 2:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_2);
                break;
            case 3:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_3);
                break;
            case 4:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_4);
                break;
            case 5:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_5);
                break;
            case 6:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_6);
                break;
            case 7:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_7);
                break;
            case 8:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_8);
                break;
            case 9:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9);
                break;
            case 10:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10);
                break;
            case 11:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11);
                break;
            case 12:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12);
                break;
            case 13:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13);
                break;
            case 14:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14);
                break;
            case 15:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15);
                break;
            case 16:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16);
                break;
            case 17:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17);
                break;
            case 18:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18);
                break;
            case 19:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19);
                break;
            case 20:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20);
                break;
            case 21:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21);
                break;
            case 22:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22);
                break;
            case 23:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23);
                break;
            case 24:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24);
                break;
            case 25:
            default:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25);
                break;
            case 26:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26);
                break;
            case 27:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27);
                break;
            case 28:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28);
                break;
            case 29:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29);
                break;
            case 30:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30);
                break;
            case 31:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31);
                break;
            case 32:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32);
                break;
            case 33:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33);
                break;
            case 34:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_34);
                break;
            case 35:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_35);
                break;
            case 36:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_36);
                break;
            case 37:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_37);
                break;
            case 38:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_38);
                break;
            case 39:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_39);
                break;
            case 40:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_40);
                break;
            case 41:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_41);
                break;
            case 42:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_42);
                break;
            case 43:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_43);
                break;
            case 44:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_44);
                break;
            case 45:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_45);
                break;
            case 46:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_46);
                break;
            case 47:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_47);
                break;
            case 48:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_48);
                break;
            case 49:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_49);
                break;
            case 50:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_50);
                break;
            case 51:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51);
                break;
            case 52:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52);
                break;
            case 53:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53);
                break;
            case 54:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54);
                break;
            case 55:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55);
                break;
            case 56:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56);
                break;
            case 57:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57);
                break;
            case 58:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58);
                break;
            case 59:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59);
                break;
            case 60:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60);
                break;
            case 61:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61);
                break;
            case 62:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62);
                break;
            case 63:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63);
                break;
            case 64:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64);
                break;
            case 65:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65);
                break;
            case 66:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66);
                break;
            case 67:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67);
                break;
            case 68:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68);
                break;
            case 69:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69);
                break;
            case 70:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70);
                break;
            case 71:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71);
                break;
            case 72:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72);
                break;
            case 73:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73);
                break;
            case 74:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74);
                break;
            case 75:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75);
                break;
            case 76:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76);
                break;
            case 77:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77);
                break;
            case 78:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78);
                break;
            case 79:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79);
                break;
            case 80:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80);
                break;
            case 81:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81);
                break;
            case 82:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82);
                break;
            case 83:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83);
                break;
            case 84:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84);
                break;
            case 85:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85);
                break;
            case 86:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86);
                break;
            case 87:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87);
                break;
            case 88:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88);
                break;
            case 89:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89);
                break;
            case 90:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90);
                break;
            case 91:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91);
                break;
            case 92:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92);
                break;
            case 93:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93);
                break;
            case 94:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94);
                break;
            case 95:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95);
                break;
            case 96:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96);
                break;
            case 97:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97);
                break;
            case 98:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98);
                break;
            case 99:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99);
                break;
            case 100:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100);
                break;
        }
    }
}
