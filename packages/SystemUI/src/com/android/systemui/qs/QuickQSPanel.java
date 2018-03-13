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

package com.android.systemui.qs;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Space;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.*;
import com.android.systemui.plugins.qs.QSTile.SignalState;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.customize.QSCustomizer;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Version of QSPanel that only shows N Quick Tiles in the QS Header.
 */
public class QuickQSPanel extends QSPanel {

    public static final String NUM_QUICK_TILES = "sysui_qqs_count";
    public static int NUM_QUICK_TILES_DEFAULT = 6;
    public static final int NUM_QUICK_TILES_ALL = 666;

    private int mMaxTiles = NUM_QUICK_TILES_DEFAULT;
    protected QSPanel mFullPanel;

    public QuickQSPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (mFooter != null) {
            removeView((View) mFooter.getView());
        }
        if (mTileLayout != null) {
            for (int i = 0; i < mRecords.size(); i++) {
                mTileLayout.removeTile(mRecords.get(i));
            }
            removeView((View) mTileLayout);
        }
        mTileLayout = new HeaderTileLayout(context);
        mTileLayout.setListening(mListening);
        addView((View) mTileLayout, 0 /* Between brightness and footer */);
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        // Always have no padding.
    }

    @Override
    protected void addDivider() {
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Dependency.get(TunerService.class).addTunable(mNumTiles, NUM_QUICK_TILES);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Dependency.get(TunerService.class).removeTunable(mNumTiles);
    }

    public void setQSPanelAndHeader(QSPanel fullPanel, View header) {
        mFullPanel = fullPanel;
    }

    @Override
    protected boolean shouldShowDetail() {
        return !mExpanded;
    }

    @Override
    protected void drawTile(TileRecord r, State state) {
        if (state instanceof SignalState) {
            SignalState copy = new SignalState();
            state.copyTo(copy);
            // No activity shown in the quick panel.
            copy.activityIn = false;
            copy.activityOut = false;
            state = copy;
        }
        super.drawTile(r, state);
    }

    @Override
    public void setHost(QSTileHost host, QSCustomizer customizer) {
        super.setHost(host, customizer);
        setTiles(mHost.getTiles());
    }

    public void setMaxTiles(int maxTiles) {
        mMaxTiles = maxTiles;
        if (mHost != null) {
            setTiles(mHost.getTiles());
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        // No tunings for you.
        if (key.equals(QS_SHOW_BRIGHTNESS)) {
            // No Brightness for you.
            super.onTuningChanged(key, "0");
        }
    }

    @Override
    public void setTiles(Collection<QSTile> tiles) {
        ArrayList<QSTile> quickTiles = new ArrayList<>();
        for (QSTile tile : tiles) {
            quickTiles.add(tile);
            if (quickTiles.size() == mMaxTiles) {
                break;
            }
        }
        super.setTiles(quickTiles, true);
        ((HeaderTileLayout) mTileLayout).updateTileGaps();
    }

    private final Tunable mNumTiles = new Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            NUM_QUICK_TILES_DEFAULT = getNumQuickTiles(mContext);
            ((HeaderTileLayout) mTileLayout).updateTileGaps();
            updateSettings();
        }
    };

    public static int getNumQuickTiles(Context context) {
        return Dependency.get(TunerService.class)
                .getValue(NUM_QUICK_TILES, NUM_QUICK_TILES_DEFAULT);
    }

    public int getNumQuickTiles() {
        return mMaxTiles;
    }

    public int getNumVisibleQuickTiles() {
        return NUM_QUICK_TILES_DEFAULT;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ((HeaderTileLayout) mTileLayout).updateTileGaps();
    }

    public void updateSettings() {
        setMaxTiles(Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_QUICKBAR_SCROLL_ENABLED, 0, UserHandle.USER_CURRENT) == 0 ?
                NUM_QUICK_TILES_DEFAULT : NUM_QUICK_TILES_ALL);
    }

    @Override
    public void setVisibility(int visibility) {
        if (getParent() instanceof HorizontalScrollView) {
            // Same visibility for parent views that only wrap around this
            View view = (View) getParent();
            if (view.getParent() instanceof HorizontalClippingLinearLayout) {
                view = (View) view.getParent();
            }
            view.setVisibility(visibility);
        } else {
            super.setVisibility(visibility);
        }
    }

    private static class HeaderTileLayout extends LinearLayout implements QSTileLayout {

        protected final ArrayList<TileRecord> mRecords = new ArrayList<>();
        private boolean mListening;
        private int mTileSize;
        private int mScreenWidth;
        private int mStartMargin;

        public HeaderTileLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setGravity(Gravity.CENTER_VERTICAL);
            setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            mTileSize = mContext.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size);
            mStartMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.qs_scroller_margin);
            mScreenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        }

        @Override
        public void setListening(boolean listening) {
            if (mListening == listening) return;
            mListening = listening;
            for (TileRecord record : mRecords) {
                record.tile.setListening(this, mListening);
            }
        }

        @Override
        public void addTile(TileRecord tile) {
            if (getChildCount() != 0) {
                // Add a spacer.
                addView(new Space(mContext), getChildCount(), generateSpaceParams());
            }
            addView(tile.tileView, getChildCount(), generateLayoutParams());
            mRecords.add(tile);
            tile.tile.setListening(this, mListening);
        }

        private LayoutParams generateSpaceParams() {
            //int size = mContext.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size);
            //LayoutParams lp = new LayoutParams(0, size);
            //lp.weight = 1;
            LayoutParams lp = new LayoutParams(mTileSize, mTileSize);
            lp.gravity = Gravity.CENTER;
            return lp;
        }

        private LayoutParams generateLayoutParams() {
            //int size = mContext.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size);
            //LayoutParams lp = new LayoutParams(size, size);
            LayoutParams lp = new LayoutParams(mTileSize, mTileSize);
            lp.gravity = Gravity.CENTER;
            return lp;
        }

        @Override
        public void removeTile(TileRecord tile) {
            int childIndex = getChildIndex(tile.tileView);
            // Remove the tile.
            removeViewAt(childIndex);
            if (getChildCount() != 0) {
                // Remove its spacer as well.
                removeViewAt(childIndex);
            }
            mRecords.remove(tile);
            tile.tile.setListening(this, false);
        }

        private int getChildIndex(QSTileView tileView) {
            final int N = getChildCount();
            for (int i = 0; i < N; i++) {
                if (getChildAt(i) == tileView) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int getOffsetTop(TileRecord tile) {
            return 0;
        }

        @Override
        public boolean updateResources() {
            // No resources here.
            return false;
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (mRecords != null && mRecords.size() > 0) {
                View previousView = this;
                for (TileRecord record : mRecords) {
                    if (record.tileView.getVisibility() == GONE) continue;
                    previousView = record.tileView.updateAccessibilityOrder(previousView);
                }
                mRecords.get(0).tileView.setAccessibilityTraversalAfter(
                        R.id.alarm_status_collapsed);
                mRecords.get(mRecords.size() - 1).tileView.setAccessibilityTraversalBefore(
                        R.id.expand_indicator);
            }
        }

        public void updateTileGaps() {
            int panelWidth = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.notification_panel_width);
            if (panelWidth == -1) {
                panelWidth = mScreenWidth;
            }
            panelWidth -= 2 * mStartMargin;
            int tileGap = (panelWidth - mTileSize * NUM_QUICK_TILES_DEFAULT) /
                    (NUM_QUICK_TILES_DEFAULT - 1);
            final int N = getChildCount();
            for (int i = 0; i < N; i++) {
                if (getChildAt(i) instanceof Space) {
                    Space s = (Space) getChildAt(i);
                    LayoutParams params = (LayoutParams) s.getLayoutParams();
                    params.width = tileGap;
                }
            }
        }
    }
}
