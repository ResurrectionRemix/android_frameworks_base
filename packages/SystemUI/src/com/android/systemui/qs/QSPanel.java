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
 * limitations under the License.
 */

package com.android.systemui.qs;

import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.systemui.qs.tileimpl.QSTileImpl.getColorForState;
import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.metrics.LogMaker;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.DumpController;
import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.QSHost.Callback;
import com.android.systemui.qs.customize.QSCustomizer;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.ToggleSliderView;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import com.android.systemui.statusbar.policy.BrightnessMirrorController.BrightnessMirrorListener;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import lineageos.providers.LineageSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;

/** View that represents the quick settings tile panel (when expanded/pulled down). **/
public class QSPanel extends LinearLayout implements Tunable, Callback, BrightnessMirrorListener,
        Dumpable {

    private static final String QS_SHOW_AUTO_BRIGHTNESS =
            "lineagesecure:" + LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS;
    public static final String QS_SHOW_BRIGHTNESS_SLIDER =
            "lineagesecure:" + LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER;
    public static final String QS_SHOW_HEADER = "qs_show_header";
    public static final String ANIM_TILE_STYLE =
            "system:" + Settings.System.ANIM_TILE_STYLE;
    public static final String ANIM_TILE_DURATION =
            "system:" + Settings.System.ANIM_TILE_DURATION;
    public static final String ANIM_TILE_INTERPOLATOR =
            "system:" + Settings.System.ANIM_TILE_INTERPOLATOR;
    public static final String QS_SHOW_BRIGHTNESS_SIDE_BUTTONS = "qs_show_brightness_side_buttons";
    public static final String QS_SHOW_SECURITY = "qs_show_secure";
    public static final String QS_LONG_PRESS_ACTION = "qs_long_press_action";
    public static final String QS_AUTO_BRIGHTNESS_POS =
            "system:" + Settings.System.QS_AUTO_BRIGHTNESS_POS;

    private static final String TAG = "QSPanel";

    protected final Context mContext;
    protected final ArrayList<TileRecord> mRecords = new ArrayList<>();
    protected final View mBrightnessView;
    protected final ImageView mAutoBrightnessView;
    protected final ImageView mLeftAutoBrightnessView;
    private final H mHandler = new H();
    private final MetricsLogger mMetricsLogger = Dependency.get(MetricsLogger.class);
    private final QSTileRevealController mQsTileRevealController;

    private ImageView mMaxBrightness;
    private ImageView mMinBrightness;

    protected boolean mExpanded;
    protected boolean mListening;
    private boolean mIsAutomaticBrightnessAvailable = false;

    private QSDetail.Callback mCallback;
    private BrightnessController mBrightnessController;
    private DumpController mDumpController;
    protected QSTileHost mHost;

    protected QSSecurityFooter mFooter;
    private PageIndicator mFooterPageIndicator;
    private boolean mGridContentVisible = true;

    protected QSTileLayout mTileLayout;

    private QSCustomizer mCustomizePanel;
    private Record mDetailRecord;

    private BrightnessMirrorController mBrightnessMirrorController;
    private ImageView mMirrorAutoBrightnessView;
    private View mDivider;
    private int animStyle, animDuration, interpolatorType, mPosition;
    private boolean mDualTargetSecondary;

    // omni
    private boolean mBrightnessBottom;
    private boolean mBrightnessVisible;
    private View mBrightnessPlaceholder;
    private boolean mShowAutoBrightnessButton = false;
    private boolean mShowBrightnessSideButtons = false;

    private int mBrightnessSlider;

    public QSPanel(Context context) {
        this(context, null);
    }

    public QSPanel(Context context, AttributeSet attrs) {
        this(context, attrs, null);
    }

    @Inject
    public QSPanel(@Named(VIEW_CONTEXT) final Context context, AttributeSet attrs,
            DumpController dumpController) {
        super(context, attrs);
        mContext = context;

        final ContentResolver resolver = context.getContentResolver();

        setOrientation(VERTICAL);

        mBrightnessView = LayoutInflater.from(mContext).inflate(
            R.layout.quick_settings_brightness_dialog, this, false);
        //addView(mBrightnessView);

        mBrightnessPlaceholder = LayoutInflater.from(mContext).inflate(
            R.layout.quick_settings_brightness_placeholder, this, false);

        mTileLayout = (QSTileLayout) LayoutInflater.from(mContext).inflate(
                R.layout.qs_paged_tile_layout, this, false);
        mTileLayout.setListening(mListening);
       // addView((View) mTileLayout);
        updateSettings();

        mQsTileRevealController = new QSTileRevealController(mContext, this,
                (PagedTileLayout) mTileLayout);

        mFooter = new QSSecurityFooter(this, context);

        addQSPanel();

        mBrightnessController = new BrightnessController(getContext(),
                findViewById(R.id.brightness_icon_left),
                findViewById(R.id.brightness_icon),
                findViewById(R.id.brightness_slider));

        mMinBrightness = mBrightnessView.findViewById(R.id.brightness_left);
        mMinBrightness.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentValue = Settings.System.getIntForUser(resolver,
                        Settings.System.SCREEN_BRIGHTNESS, 0, UserHandle.USER_CURRENT);
                int brightness = currentValue - 10;
                if (currentValue != 0) {
                    int math = Math.max(0, brightness);
                    Settings.System.putIntForUser(resolver,
                            Settings.System.SCREEN_BRIGHTNESS, math, UserHandle.USER_CURRENT);
                }
            }
        });
        mMinBrightness.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setBrightnessMinMax(true);
                return true;
            }
        });

        mMaxBrightness = mBrightnessView.findViewById(R.id.brightness_right);
        mMaxBrightness.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentValue = Settings.System.getIntForUser(resolver,
                        Settings.System.SCREEN_BRIGHTNESS, 0, UserHandle.USER_CURRENT);
                int brightness = currentValue + 10;
                if (currentValue != 255) {
                    int math = Math.min(255, brightness);
                    Settings.System.putIntForUser(resolver,
                            Settings.System.SCREEN_BRIGHTNESS, math, UserHandle.USER_CURRENT);
                }
            }
        });
        mMaxBrightness.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setBrightnessMinMax(false);
                return true;
            }
        });

        mDumpController = dumpController;

        mAutoBrightnessView = findViewById(R.id.brightness_icon);
        mLeftAutoBrightnessView = findViewById(R.id.brightness_icon_left);

        mIsAutomaticBrightnessAvailable = getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);
    }


    private void addQSPanel() {
        switch (mBrightnessSlider) {
            case 1:
            default:
                addView(mBrightnessView);
                addView((View) mTileLayout);
                break;
            case 2:
                addView((View) mTileLayout);
                addView(mBrightnessView);
                break;
            case 3:
                addView(mBrightnessView);
                addView((View) mTileLayout);
                break;
            case 4:
                addView(mBrightnessPlaceholder);
                addView((View) mTileLayout);
                addView(mBrightnessView);
                break;
        }

        addDivider();
        addView(mFooter.getView());
        updateResources();
    }

    private void restartQSPanel() {
        if (mFooter.getView() != null) removeView(mFooter.getView());
        if (mDivider != null) removeView(mDivider);
        if ((View) mTileLayout != null) removeView((View) mTileLayout);
        if (mBrightnessView != null) removeView(mBrightnessView);
        if (mBrightnessPlaceholder != null) removeView(mBrightnessPlaceholder);

        addQSPanel();
    }

    protected void addDivider() {
        mDivider = LayoutInflater.from(mContext).inflate(R.layout.qs_divider, this, false);
        mDivider.setBackgroundColor(Color.TRANSPARENT);
        addView(mDivider);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We want all the logic of LinearLayout#onMeasure, and for it to assign the excess space
        // not used by the other children to PagedTileLayout. However, in this case, LinearLayout
        // assumes that PagedTileLayout would use all the excess space. This is not the case as
        // PagedTileLayout height is quantized (because it shows a certain number of rows).
        // Therefore, after everything is measured, we need to make sure that we add up the correct
        // total height
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = getPaddingBottom() + getPaddingTop();
        int numChildren = getChildCount();
        for (int i = 0; i < numChildren; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) height += child.getMeasuredHeight();
        }
        setMeasuredDimension(getMeasuredWidth(), height);
    }

    public View getDivider() {
        return mDivider;
    }

    public QSTileRevealController getQsTileRevealController() {
        return mQsTileRevealController;
    }

    public boolean isShowingCustomize() {
        return mCustomizePanel != null && mCustomizePanel.isCustomizing();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, QS_SHOW_BRIGHTNESS_SLIDER);
        tunerService.addTunable(this, ANIM_TILE_STYLE);
        tunerService.addTunable(this, ANIM_TILE_DURATION);
        tunerService.addTunable(this, ANIM_TILE_INTERPOLATOR);
        tunerService.addTunable(this, QS_SHOW_BRIGHTNESS_SIDE_BUTTONS);
        tunerService.addTunable(this, QS_SHOW_SECURITY);
        tunerService.addTunable(this, QS_LONG_PRESS_ACTION);
        tunerService.addTunable(this, QS_AUTO_BRIGHTNESS_POS);
        if (mHost != null) {
            setTiles(mHost.getTiles());
        }
        if (mBrightnessMirrorController != null) {
            mBrightnessMirrorController.addCallback(this);
        }
        if (mDumpController != null) mDumpController.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        Dependency.get(TunerService.class).removeTunable(this);
        if (mHost != null) {
            mHost.removeCallback(this);
        }
        for (TileRecord record : mRecords) {
            record.tile.removeCallbacks();
        }
        if (mBrightnessMirrorController != null) {
            mBrightnessMirrorController.removeCallback(this);
        }
        if (mDumpController != null) mDumpController.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onTilesChanged() {
        setTiles(mHost.getTiles());
    }

    private void updateAutoViewVisibilityForTuningValue(View view,View leftview, int pos) {
       try {
            if (pos == 0) {
                view.setVisibility(View.GONE);
                leftview.setVisibility(View.GONE);
            } else if (pos == 1) {
                view.setVisibility(View.GONE);
                leftview.setVisibility(View.VISIBLE);
            } else if (pos == 2) {
                view.setVisibility(View.VISIBLE);
                leftview.setVisibility(View.GONE);
            }
        } catch (Exception e) {}
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
         if (QS_AUTO_BRIGHTNESS_POS.equals(key) && mIsAutomaticBrightnessAvailable) {
            mPosition = TunerService.parseInteger(newValue, 0);
            updateAutoViewVisibilityForTuningValue(mAutoBrightnessView, mLeftAutoBrightnessView, mPosition);
        } else if (QS_SHOW_BRIGHTNESS_SLIDER.equals(key)) {
            mBrightnessSlider = TunerService.parseInteger(newValue, 1);
            mBrightnessView.setVisibility(mBrightnessSlider != 0 ? VISIBLE : GONE);
            restartQSPanel();
        } else if (ANIM_TILE_STYLE.equals(key)) {
            animStyle = TunerService.parseInteger(newValue, 0);
            if (mHost != null) {
                setTiles(mHost.getTiles());
            }
        } else if (ANIM_TILE_DURATION.equals(key)) {
            animDuration = TunerService.parseInteger(newValue, 0);
            if (mHost != null) {
                setTiles(mHost.getTiles());
            }
        } else if (ANIM_TILE_INTERPOLATOR.equals(key)) {
            interpolatorType = TunerService.parseInteger(newValue, 0);
            if (mHost != null) {
                setTiles(mHost.getTiles());
            }
        } else  if (QS_SHOW_BRIGHTNESS_SIDE_BUTTONS.equals(key)) {
            mShowBrightnessSideButtons = (newValue == null || Integer.parseInt(newValue) == 0)
                    ? false : true;
            mMaxBrightness.setVisibility(mShowBrightnessSideButtons ? View.VISIBLE : View.GONE);
            mMinBrightness.setVisibility(mShowBrightnessSideButtons ? View.VISIBLE : View.GONE);
        } else if (QS_SHOW_SECURITY.equals(key)) {
            mFooter.setForceHide(newValue != null && Integer.parseInt(newValue) == 0);
        } else if (QS_LONG_PRESS_ACTION.equals(key)) {
            mDualTargetSecondary = newValue != null && Integer.parseInt(newValue) == 1;
            updateSettings();
        }
    }

    private int getBrightnessViewPositionBottom() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == mDivider) {
                return i;
            }
        }
        return 0;
    }

    private void updateViewVisibilityForBrightnessMirrorIcon(@Nullable String newValue) {
        if (mMirrorAutoBrightnessView != null) {
            mMirrorAutoBrightnessView.setVisibility(
                    TunerService.parseIntegerSwitch(newValue, true) ? INVISIBLE : GONE);
        } else if (mBrightnessMirrorController != null) {
            mMirrorAutoBrightnessView = mBrightnessMirrorController.getMirror()
                    .findViewById(R.id.brightness_icon);
            mMirrorAutoBrightnessView.setVisibility(mAutoBrightnessView.getVisibility()
                    == VISIBLE ? INVISIBLE : GONE);
        }
    }

    public void openDetails(String subPanel) {
        QSTile tile = getTile(subPanel);
        // If there's no tile with that name (as defined in QSFactoryImpl or other QSFactory),
        // QSFactory will not be able to create a tile and getTile will return null
        if (tile != null) {
            showDetailAdapter(true, tile.getDetailAdapter(), new int[]{getWidth() / 2, 0});
        }
    }

    private QSTile getTile(String subPanel) {
        for (int i = 0; i < mRecords.size(); i++) {
            if (subPanel.equals(mRecords.get(i).tile.getTileSpec())) {
                return mRecords.get(i).tile;
            }
        }
        return mHost.createTile(subPanel);
    }

    public void setBrightnessMirror(BrightnessMirrorController c) {
        if (mBrightnessMirrorController != null) {
            mBrightnessMirrorController.removeCallback(this);
        }
        mBrightnessMirrorController = c;
        if (mBrightnessMirrorController != null) {
            mBrightnessMirrorController.addCallback(this);
        }
        updateBrightnessMirror();
        updateViewVisibilityForBrightnessMirrorIcon(null);
    }

    @Override
    public void onBrightnessMirrorReinflated(View brightnessMirror) {
        updateBrightnessMirror();
    }

    View getBrightnessView() {
        return mBrightnessView;
    }

    View getBrightnessPlaceholder() {
        return mBrightnessPlaceholder;
    }

    public void setCallback(QSDetail.Callback callback) {
        mCallback = callback;
    }

    public void setHost(QSTileHost host, QSCustomizer customizer) {
        mHost = host;
        mHost.addCallback(this);
        setTiles(mHost.getTiles());
        mFooter.setHostEnvironment(host);
        mCustomizePanel = customizer;
        if (mCustomizePanel != null) {
            mCustomizePanel.setHost(mHost);
        }
    }

    /**
     * Links the footer's page indicator, which is used in landscape orientation to save space.
     *
     * @param pageIndicator indicator to use for page scrolling
     */
    public void setFooterPageIndicator(PageIndicator pageIndicator) {
        if (mTileLayout instanceof PagedTileLayout) {
            mFooterPageIndicator = pageIndicator;
            updatePageIndicator();
        }
    }

    private void updatePageIndicator() {
        if (mTileLayout instanceof PagedTileLayout) {
            if (mFooterPageIndicator != null) {
                mFooterPageIndicator.setVisibility(View.GONE);

                ((PagedTileLayout) mTileLayout).setPageIndicator(mFooterPageIndicator);
            }
        }
    }

    public QSTileHost getHost() {
        return mHost;
    }

    public void updateResources() {
        final Resources res = mContext.getResources();
        setPadding(0, res.getDimensionPixelSize(R.dimen.qs_panel_padding_top), 0, res.getDimensionPixelSize(R.dimen.qs_panel_padding_bottom));

        updatePageIndicator();

        if (mListening) {
            refreshAllTiles();
        }
        if (mTileLayout != null) {
            mTileLayout.updateResources();
        }
        if (mCustomizePanel != null) {
            mCustomizePanel.updateResources();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mFooter.onConfigurationChanged();
        updateResources();

        updateBrightnessMirror();
    }

    public void updateBrightnessMirror() {
        if (mBrightnessMirrorController != null) {
            ToggleSliderView brightnessSlider = findViewById(R.id.brightness_slider);
            ToggleSliderView mirrorSlider = mBrightnessMirrorController.getMirror()
                    .findViewById(R.id.brightness_slider);
            brightnessSlider.setMirror(mirrorSlider);
            brightnessSlider.setMirrorController(mBrightnessMirrorController);
        }
    }

    public void onCollapse() {
        if (mCustomizePanel != null && mCustomizePanel.isShown()) {
            mCustomizePanel.hide();
        }
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        if (!mExpanded && mTileLayout instanceof PagedTileLayout) {
            ((PagedTileLayout) mTileLayout).setCurrentItem(0, false);
        }
        mMetricsLogger.visibility(MetricsEvent.QS_PANEL, mExpanded);
        if (!mExpanded) {
            closeDetail();
        } else {
            logTiles();
        }
    }

    public void setPageListener(final PagedTileLayout.PageListener pageListener) {
        if (mTileLayout instanceof PagedTileLayout) {
            ((PagedTileLayout) mTileLayout).setPageListener(pageListener);
        }
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mTileLayout != null) {
            mTileLayout.setListening(listening);
        }
        if (mListening) {
            refreshAllTiles();
        }
    }

    public void setListening(boolean listening, boolean expanded) {
        setListening(listening && expanded);
        getFooter().setListening(listening);
        // Set the listening as soon as the QS fragment starts listening regardless of the expansion,
        // so it will update the current brightness before the slider is visible.
        setBrightnessListening(listening);
    }

    public void setBrightnessListening(boolean listening) {
        if (listening) {
            mBrightnessController.registerCallbacks();
        } else {
            mBrightnessController.unregisterCallbacks();
        }
    }

    public void refreshAllTiles() {
        mBrightnessController.checkRestrictionAndSetEnabled();
        for (TileRecord r : mRecords) {
            r.tile.refreshState();
        }
        mFooter.refreshState();
    }

    public void showDetailAdapter(boolean show, DetailAdapter adapter, int[] locationInWindow) {
        int xInWindow = locationInWindow[0];
        int yInWindow = locationInWindow[1];
        ((View) getParent()).getLocationInWindow(locationInWindow);

        Record r = new Record();
        r.detailAdapter = adapter;
        r.x = xInWindow - locationInWindow[0];
        r.y = yInWindow - locationInWindow[1];

        locationInWindow[0] = xInWindow;
        locationInWindow[1] = yInWindow;

        showDetail(show, r);
    }

    protected void showDetail(boolean show, Record r) {
        mHandler.obtainMessage(H.SHOW_DETAIL, show ? 1 : 0, 0, r).sendToTarget();
    }

    public void setTiles(Collection<QSTile> tiles) {
        setTiles(tiles, false);
    }

    public void setTiles(Collection<QSTile> tiles, boolean collapsedView) {
        if (!collapsedView) {
            mQsTileRevealController.updateRevealedTiles(tiles);
        }
        for (TileRecord record : mRecords) {
            mTileLayout.removeTile(record);
            record.tile.removeCallback(record.callback);
        }
        mRecords.clear();
        for (QSTile tile : tiles) {
            addTile(tile, collapsedView);
        }
    }

    protected void drawTile(TileRecord r, QSTile.State state) {
        r.tileView.onStateChanged(state);
    }

    protected QSTileView createTileView(QSTile tile, boolean collapsedView) {
        return mHost.createTileView(tile, collapsedView);
    }

    protected boolean shouldShowDetail() {
        return mExpanded;
    }

    protected TileRecord addTile(final QSTile tile, boolean collapsedView) {
        final TileRecord r = new TileRecord();
        r.tile = tile;
        r.tileView = createTileView(tile, collapsedView);
        final QSTile.Callback callback = new QSTile.Callback() {
            @Override
            public void onStateChanged(QSTile.State state) {
                drawTile(r, state);
            }

            @Override
            public void onShowDetail(boolean show) {
                // Both the collapsed and full QS panels get this callback, this check determines
                // which one should handle showing the detail.
                if (shouldShowDetail()) {
                    QSPanel.this.showDetail(show, r);
                }
            }

            @Override
            public void onToggleStateChanged(boolean state) {
                if (mDetailRecord == r) {
                    fireToggleStateChanged(state);
                }
            }

            @Override
            public void onScanStateChanged(boolean state) {
                r.scanState = state;
                if (mDetailRecord == r) {
                    fireScanStateChanged(r.scanState);
                }
            }

            @Override
            public void onAnnouncementRequested(CharSequence announcement) {
                if (announcement != null) {
                    mHandler.obtainMessage(H.ANNOUNCE_FOR_ACCESSIBILITY, announcement)
                            .sendToTarget();
                }
            }
        };
        r.tile.addCallback(callback);
        r.callback = callback;
        r.tileView.init(r.tile);
        r.tile.refreshState();
        mRecords.add(r);

        if (mTileLayout != null) {
            mTileLayout.addTile(r);
            configureTile(r.tile, r.tileView);
        }

        return r;
    }


    public void showEdit(final View v) {
        v.post(new Runnable() {
            @Override
            public void run() {
                if (mCustomizePanel != null) {
                    if (!mCustomizePanel.isCustomizing()) {
                        int[] loc = v.getLocationOnScreen();
                        int x = loc[0] + v.getWidth() / 2;
                        // we subtract getTop, because Pie clipper starts after black area
                        int y = loc[1] + v.getHeight() / 2 - getTop();
                        mCustomizePanel.show(x, y);
                    }
                }

            }
        });
    }

    public void closeDetail() {
        if (mCustomizePanel != null && mCustomizePanel.isShown()) {
            // Treat this as a detail panel for now, to make things easy.
            mCustomizePanel.hide();
            return;
        }
        showDetail(false, mDetailRecord);
    }

    public int getGridHeight() {
        return getMeasuredHeight();
    }

    protected void handleShowDetail(Record r, boolean show) {
        if (r instanceof TileRecord) {
            handleShowDetailTile((TileRecord) r, show);
        } else {
            int x = 0;
            int y = 0;
            if (r != null) {
                x = r.x;
                y = r.y;
            }
            handleShowDetailImpl(r, show, x, y);
        }
    }

    private void handleShowDetailTile(TileRecord r, boolean show) {
        if ((mDetailRecord != null) == show && mDetailRecord == r) return;

        if (show) {
            r.detailAdapter = r.tile.getDetailAdapter();
            if (r.detailAdapter == null) return;
        }
        r.tile.setDetailListening(show);
        int x = r.tileView.getLeft() + r.tileView.getWidth() / 2;
        int y = r.tileView.getDetailY() + mTileLayout.getOffsetTop(r);
        handleShowDetailImpl(r, show, x, y);
    }

    private void handleShowDetailImpl(Record r, boolean show, int x, int y) {
        setDetailRecord(show ? r : null);
        fireShowingDetail(show ? r.detailAdapter : null, x, y);
    }

    protected void setDetailRecord(Record r) {
        if (r == mDetailRecord) return;
        mDetailRecord = r;
        final boolean scanState = mDetailRecord instanceof TileRecord
                && ((TileRecord) mDetailRecord).scanState;
        fireScanStateChanged(scanState);
    }

    void setGridContentVisibility(boolean visible) {
        int newVis = visible ? VISIBLE : INVISIBLE;
        setVisibility(newVis);
        if (mGridContentVisible != visible) {
            mMetricsLogger.visibility(MetricsEvent.QS_PANEL, newVis);
        }
        mGridContentVisible = visible;
    }

    private void logTiles() {
        for (int i = 0; i < mRecords.size(); i++) {
            QSTile tile = mRecords.get(i).tile;
            mMetricsLogger.write(tile.populate(new LogMaker(tile.getMetricsCategory())
                    .setType(MetricsEvent.TYPE_OPEN)));
        }
    }

    private void fireShowingDetail(DetailAdapter detail, int x, int y) {
        if (mCallback != null) {
            mCallback.onShowingDetail(detail, x, y);
        }
    }

    private void fireToggleStateChanged(boolean state) {
        if (mCallback != null) {
            mCallback.onToggleStateChanged(state);
        }
    }

    private void fireScanStateChanged(boolean state) {
        if (mCallback != null) {
            mCallback.onScanStateChanged(state);
        }
    }

    public void clickTile(ComponentName tile) {
        final String spec = CustomTile.toSpec(tile);
        final int N = mRecords.size();
        for (int i = 0; i < N; i++) {
            if (mRecords.get(i).tile.getTileSpec().equals(spec)) {
                mRecords.get(i).tile.click();
                break;
            }
        }
    }

    QSTileLayout getTileLayout() {
        return mTileLayout;
    }

    QSTileView getTileView(QSTile tile) {
        for (TileRecord r : mRecords) {
            if (r.tile == tile) {
                return r.tileView;
            }
        }
        return null;
    }

    public QSSecurityFooter getFooter() {
        return mFooter;
    }

    public void showDeviceMonitoringDialog() {
        mFooter.showDeviceMonitoringDialog();
    }

    public void setMargins(int sideMargins) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view != mTileLayout) {
                LayoutParams lp = (LayoutParams) view.getLayoutParams();
                lp.leftMargin = sideMargins;
                lp.rightMargin = sideMargins;
            }
        }
    }

    private class H extends Handler {
        private static final int SHOW_DETAIL = 1;
        private static final int SET_TILE_VISIBILITY = 2;
        private static final int ANNOUNCE_FOR_ACCESSIBILITY = 3;

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SHOW_DETAIL) {
                handleShowDetail((Record) msg.obj, msg.arg1 != 0);
            } else if (msg.what == ANNOUNCE_FOR_ACCESSIBILITY) {
                announceForAccessibility((CharSequence) msg.obj);
            }
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println(getClass().getSimpleName() + ":");
        pw.println("  Tile records:");
        for (TileRecord record : mRecords) {
            if (record.tile instanceof Dumpable) {
                pw.print("    "); ((Dumpable) record.tile).dump(fd, pw, args);
                pw.print("    "); pw.println(record.tileView.toString());
            }
        }
    }

    protected static class Record {
        DetailAdapter detailAdapter;
        int x;
        int y;
    }

    public static final class TileRecord extends Record {
        public QSTile tile;
        public com.android.systemui.plugins.qs.QSTileView tileView;
        public boolean scanState;
        public QSTile.Callback callback;
    }

    public interface QSTileLayout {

        default void saveInstanceState(Bundle outState) {}

        default void restoreInstanceState(Bundle savedInstanceState) {}

        void addTile(TileRecord tile);

        void removeTile(TileRecord tile);

        int getOffsetTop(TileRecord tile);

        boolean updateResources();

        void setListening(boolean listening);

        default void setExpansion(float expansion) {}

        int getNumVisibleTiles();
        void updateSettings();
        int getNumColumns();
        int getNumRows();
        boolean isShowTitles();
    }

    private void setAnimationTile(QSTileView v) {
        ObjectAnimator animTile = null;
        if (animStyle == 0) {
            //No animation
        }
        if (animStyle == 1) {
            animTile = ObjectAnimator.ofFloat(v, "rotationY", 0f, 360f);
        }
        if (animStyle == 2) {
            animTile = ObjectAnimator.ofFloat(v, "rotation", 0f, 360f);
        }
        if (animTile != null) {
            switch (interpolatorType) {
                    case 0:
                        animTile.setInterpolator(new LinearInterpolator());
                        break;
                    case 1:
                        animTile.setInterpolator(new AccelerateInterpolator());
                        break;
                    case 2:
                        animTile.setInterpolator(new DecelerateInterpolator());
                        break;
                    case 3:
                        animTile.setInterpolator(new AccelerateDecelerateInterpolator());
                        break;
                    case 4:
                        animTile.setInterpolator(new BounceInterpolator());
                        break;
                    case 5:
                        animTile.setInterpolator(new OvershootInterpolator());
                        break;
                    case 6:
                        animTile.setInterpolator(new AnticipateInterpolator());
                        break;
                    case 7:
                        animTile.setInterpolator(new AnticipateOvershootInterpolator());
                        break;
                    default:
                        break;
            }
            animTile.setDuration(animDuration);
            animTile.start();
        }
    }


    private void configureTile(QSTile t, QSTileView v) {
        if (mTileLayout != null) {
            v.setOnClickListener(view -> {
                    t.click();
                    setAnimationTile(v);
            });
            v.setHideLabel(!mTileLayout.isShowTitles());
            if (t.isDualTarget()) {
                if (mDualTargetSecondary && !mTileLayout.isShowTitles()) {
                    v.setOnLongClickListener(view -> {
                        t.secondaryClick();
                        return true;
                    });
                } else {
                    v.setOnLongClickListener(view -> {
                        t.longClick();
                        return true;
                    });
                }
            }
        }
    }

    public void updateSettings() {
        if (mTileLayout != null) {
            mTileLayout.updateSettings();
        }
    }

    private void setBrightnessMinMax(boolean min) {
        mBrightnessController.setBrightnessFromSliderButtons(min ? 0 : GAMMA_SPACE_MAX);
    }

    public boolean isBrightnessViewBottom() {
        return mBrightnessBottom;
    }
}
