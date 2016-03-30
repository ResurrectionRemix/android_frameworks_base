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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
  
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile.DetailAdapter;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;

import java.util.ArrayList;
import java.util.Collection;
import cyanogenmod.providers.CMSettings;

/** View that represents the quick settings tile panel. **/
public class QSPanel extends ViewGroup {
    private static final float TILE_ASPECT = 1.2f;

    private final Context mContext;
    protected final ArrayList<TileRecord> mRecords = new ArrayList<TileRecord>();
    private final View mDetail;
    private final ViewGroup mDetailContent;
    private final TextView mDetailSettingsButton;
    private final TextView mDetailDoneButton;
    protected final View mBrightnessView;
    private final QSDetailClipper mClipper;
    private final H mHandler = new H();

    private int mColumns;
    private int mNumberOfColumns;
    private int mCellWidth;
    private int mCellHeight;
    private int mLargeCellWidth;
    private int mLargeCellHeight;
    private int mPanelPaddingBottom;
    private int mDualTileUnderlap;
    private int mBrightnessPaddingTop;
    private int mGridHeight;
    private boolean mExpanded;
    private boolean mListening;
    private boolean mClosingDetail;
    private boolean mQsColorSwitch = false;
    private int mQsIconColor;
    private int mLabelColor;

    private boolean mVibrationEnabled;

    private Record mDetailRecord;
    private Callback mCallback;
    private BrightnessController mBrightnessController;
    private QSTileHost mHost;

    private QSFooter mFooter;
    private boolean mGridContentVisible = true;

    protected Vibrator mVibrator;

    private SettingsObserver mSettingsObserver;

    private boolean mUseMainTiles = false;

    public QSPanel(Context context) {
        this(context, null);
    }

    public QSPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mDetail = LayoutInflater.from(context).inflate(R.layout.qs_detail, this, false);
        mDetailContent = (ViewGroup) mDetail.findViewById(android.R.id.content);
        mDetailSettingsButton = (TextView) mDetail.findViewById(android.R.id.button2);
        mDetailDoneButton = (TextView) mDetail.findViewById(android.R.id.button1);
        updateDetailText();
	mBrightnessView = LayoutInflater.from(mContext).inflate(
                R.layout.quick_settings_brightness_dialog, this, false);
        mDetail.setVisibility(GONE);
        mDetail.setClickable(true);
        mFooter = new QSFooter(this, context);
        addView(mDetail);
        addView(mBrightnessView);
        addView(mFooter.getView());
        mClipper = new QSDetailClipper(mDetail);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mSettingsObserver = new SettingsObserver(mHandler);
        updateResources();
        updateResources();
        mQsColorSwitch = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_COLOR_SWITCH, 0) == 1;
	mLabelColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_TEXT_COLOR, 0xFFFFFFFF);
	mQsIconColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_ICON_COLOR, 0xFFFFFFFF);
	 if (mQsColorSwitch) {
            mDetailDoneButton.setTextColor(mLabelColor);
            mDetailSettingsButton.setTextColor(mLabelColor);
        }


        boolean brightnessIconEnabled = Settings.System.getIntForUser(
            mContext.getContentResolver(), Settings.System.BRIGHTNESS_ICON,
                0, UserHandle.USER_CURRENT) == 1;

        mBrightnessController = new BrightnessController(getContext(),
                (ImageView) findViewById(R.id.brightness_icon),
                (ToggleSlider) findViewById(R.id.brightness_slider));

        mDetailDoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                announceForAccessibility(
                        mContext.getString(R.string.accessibility_desc_quick_settings));
                closeDetail();
                vibrateTile(20);
            }
        });
    }

    /**
     * Enable/disable brightness slider.
     */
    private boolean showBrightnessSlider() {
        boolean brightnessSliderEnabled = CMSettings.System.getIntForUser(
            mContext.getContentResolver(), CMSettings.System.QS_SHOW_BRIGHTNESS_SLIDER,
                1, UserHandle.USER_CURRENT) == 1;
        boolean brightnessIconEnabled = Settings.System.getIntForUser(
            mContext.getContentResolver(), Settings.System.BRIGHTNESS_ICON,
                0, UserHandle.USER_CURRENT) == 1;
        ToggleSlider brightnessSlider = (ToggleSlider) findViewById(R.id.brightness_slider);
        ImageView brightnessIcon = (ImageView) findViewById(R.id.brightness_icon);
        if (brightnessSliderEnabled) {
            mBrightnessView.setVisibility(View.VISIBLE);
            brightnessSlider.setVisibility(View.VISIBLE);
            if (brightnessIconEnabled) {
                brightnessIcon.setVisibility(View.VISIBLE);
            } else {
                brightnessIcon.setVisibility(View.GONE);
            }
        } else {
            mBrightnessView.setVisibility(View.GONE);
            brightnessSlider.setVisibility(View.GONE);
            brightnessIcon.setVisibility(View.GONE);
        }
        updateResources();
        updatecolors();
        return brightnessSliderEnabled;
    }
    
     public void updatecolors() {
	ImageView brightnessIcon = (ImageView) findViewById(R.id.brightness_icon);
	mQsColorSwitch = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.QS_COLOR_SWITCH, 0) == 1;
	int mIconColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_BRIGHTNESS_ICON_COLOR, 0xFFFFFFFF);
	if (mQsColorSwitch) {	
	 brightnessIcon.setColorFilter(mIconColor, Mode.SRC_IN);
		}
     }
     
        
  public void setDetailBackgroundColor(int color) {
	final Resources res = getContext().getResources();
	int mStockBg = res.getColor(R.color.quick_settings_panel_background);
        mQsColorSwitch = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.QS_COLOR_SWITCH, 0) == 1;
        if (mQsColorSwitch) {
            if (mDetail != null) {
                    mDetail.getBackground().setColorFilter(
                            color, Mode.SRC_OVER);
                } 		
            } else {
	if (mDetail != null) {
                    mDetail.getBackground().setColorFilter(
                           mStockBg, Mode.SRC_OVER);
                }
	 }    
	}
	
  public void setQSShadeAlphaValue(int alpha) {
        if (mDetail != null) {
            mDetail.getBackground().setAlpha(alpha);
        }
    }
    
    public void updateicons() {
	mLabelColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_TEXT_COLOR, 0xFFFFFFFF);
	mQsIconColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_ICON_COLOR, 0xFFFFFFFF);
	}

    public void vibrateTile(int duration) {
        if (!mVibrationEnabled) { return; }
        if (mVibrator != null) {
            if (mVibrator.hasVibrator()) { mVibrator.vibrate(duration); }
        }
    }

    /**
     * Use three or four columns.
     */
    private int useFourColumns() {
        final Resources res = mContext.getResources();
        boolean shouldUseFourColumns = Settings.System.getInt(
            mContext.getContentResolver(), Settings.System.QS_USE_FOUR_COLUMNS,
                0) == 1;
        if (shouldUseFourColumns) {
            mNumberOfColumns = 4;
        } else {
            mNumberOfColumns = res.getInteger(R.integer.quick_settings_num_columns);
        }
        return mNumberOfColumns;
    }

    private void updateDetailText() {
        mDetailDoneButton.setText(R.string.quick_settings_done);
        mDetailSettingsButton.setText(R.string.quick_settings_more_settings);
    }

    public void setBrightnessMirror(BrightnessMirrorController c) {
        super.onFinishInflate();
        ToggleSlider brightnessSlider = (ToggleSlider) findViewById(R.id.brightness_slider);
        ToggleSlider mirror = (ToggleSlider) c.getMirror().findViewById(R.id.brightness_slider);
        brightnessSlider.setMirror(mirror);
        brightnessSlider.setMirrorController(c);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setHost(QSTileHost host) {
        mHost = host;
        mFooter.setHost(host);
    }

    public QSTileHost getHost() {
        return mHost;
    }

    public void updateResources() {
        final Resources res = mContext.getResources();
        mLargeCellHeight = res.getDimensionPixelSize(R.dimen.qs_dual_tile_height);
        mLargeCellWidth = (int)(mLargeCellHeight * TILE_ASPECT);
        mPanelPaddingBottom = res.getDimensionPixelSize(R.dimen.qs_panel_padding_bottom);
        mDualTileUnderlap = res.getDimensionPixelSize(R.dimen.qs_dual_tile_padding_vertical);
        mBrightnessPaddingTop = res.getDimensionPixelSize(R.dimen.qs_brightness_padding_top);
        updateNumColumns();
        if (mListening) {
            refreshAllTiles();
            mSettingsObserver.observe();
        }
        updateDetailText();
    }

    public void updateNumColumns() {
        final Resources res = mContext.getResources();
        final ContentResolver resolver = mContext.getContentResolver();
        int defColumns = Math.max(1, res.getInteger(R.integer.quick_settings_num_columns));
        int columns = Settings.System.getIntForUser(resolver,
                Settings.System.QS_NUM_TILE_COLUMNS, defColumns,
                UserHandle.USER_CURRENT);
        if (mColumns != columns) {
            mColumns = columns;
        }
        float aspect = getAspectForColumnCount(columns, res);
        mCellHeight = Math.round(res.getDimensionPixelSize(
                R.dimen.qs_tile_height) * aspect);
        mCellWidth = Math.round(mCellHeight * (TILE_ASPECT * aspect));
        for (TileRecord record : mRecords) {
            record.tileView.updateDimens(res, aspect);
            record.tileView.recreateLabel();
            if (record.tileView.getVisibility() != GONE) {
                record.tileView.requestLayout();
            }
        }
        postInvalidate();
    }

    private float getAspectForColumnCount(int numColumns, Resources res) {
        TypedValue tileScaleFactor = new TypedValue();
        int dimen;
        switch (numColumns) {
            case 3:
                dimen = R.dimen.qs_tile_three_column_scale;
                break;
            case 4:
                dimen = R.dimen.qs_tile_four_column_scale;
                break;
            case 5:
                dimen = R.dimen.qs_tile_five_column_scale;
                break;
            default:
                dimen = R.dimen.qs_tile_three_column_scale;
        }
        res.getValue(dimen, tileScaleFactor, true);
        return tileScaleFactor.getFloat();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mDetailDoneButton, R.dimen.qs_detail_button_text_size);
        FontSizeUtils.updateFontSize(mDetailSettingsButton, R.dimen.qs_detail_button_text_size);

        // We need to poke the detail views as well as they might not be attached to the view
        // hierarchy but reused at a later point.
        int count = mRecords.size();
        for (int i = 0; i < count; i++) {
            View detailView = mRecords.get(i).detailView;
            if (detailView != null) {
                detailView.dispatchConfigurationChanged(newConfig);
            }
        }
        mFooter.onConfigurationChanged();
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        MetricsLogger.visibility(mContext, MetricsLogger.QS_PANEL, mExpanded);
        if (!mExpanded) {
            closeDetail();
        } else {
            logTiles();
        }
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        for (TileRecord r : mRecords) {
            r.tile.setListening(mListening);
        }
        mFooter.setListening(mListening);
        if (mListening) {
            refreshAllTiles();
            mSettingsObserver.observe();
        } else {
            mSettingsObserver.unobserve();
        }
        if (listening && showBrightnessSlider()) {
            mBrightnessController.registerCallbacks();
        } else {
            mBrightnessController.unregisterCallbacks();
        }
    }

    public void refreshAllTiles() {
        mUseMainTiles = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_USE_MAIN_TILES, 1, UserHandle.USER_CURRENT) == 1;
        for (int i = 0; i < mRecords.size(); i++) {
            TileRecord r = mRecords.get(i);
            r.tileView.setDual(mUseMainTiles && i < 2);
            r.tile.refreshState();
        }
        mFooter.refreshState();
    }

    public void showDetailAdapter(boolean show, DetailAdapter adapter, int[] locationInWindow) {
        int xInWindow = locationInWindow[0];
        int yInWindow = locationInWindow[1];
        mDetail.getLocationInWindow(locationInWindow);

        Record r = new Record();
        r.detailAdapter = adapter;
        r.x = xInWindow - locationInWindow[0];
        r.y = yInWindow - locationInWindow[1];

        locationInWindow[0] = xInWindow;
        locationInWindow[1] = yInWindow;

        showDetail(show, r);
    }

    private void showDetail(boolean show, Record r) {
        mHandler.obtainMessage(H.SHOW_DETAIL, show ? 1 : 0, 0, r).sendToTarget();
    }

    private void setTileVisibility(View v, int visibility) {
        mHandler.obtainMessage(H.SET_TILE_VISIBILITY, visibility, 0, v).sendToTarget();
    }

    private void handleSetTileVisibility(View v, int visibility) {
        if (visibility == VISIBLE && !mGridContentVisible) {
            visibility = INVISIBLE;
        }
        if (visibility == v.getVisibility()) return;
        v.setVisibility(visibility);
    }

    public void setTiles(Collection<QSTile<?>> tiles) {
        for (TileRecord record : mRecords) {
            removeView(record.tileView);
        }
        mRecords.clear();
        for (QSTile<?> tile : tiles) {
            addTile(tile);
        }
        if (isShowingDetail()) {
            mDetail.bringToFront();
        }
    }

    private void drawTile(TileRecord r, QSTile.State state) {
        final int visibility = state.visible ? VISIBLE : GONE;
        setTileVisibility(r.tileView, visibility);
        r.tileView.onStateChanged(state);
    }

    private void addTile(final QSTile<?> tile) {
        final TileRecord r = new TileRecord();
        r.tile = tile;
        r.tileView = tile.createTileView(mContext);
        r.tileView.setVisibility(View.GONE);
        final QSTile.Callback callback = new QSTile.Callback() {
            @Override
            public void onStateChanged(QSTile.State state) {
                if (!r.openingDetail) {
                    drawTile(r, state);
                }
            }
            @Override
            public void onShowDetail(boolean show) {
                QSPanel.this.showDetail(show, r);
            }
            @Override
            public void onToggleStateChanged(boolean state) {
                if (mDetailRecord == r) {
                    fireToggleStateChanged(state);
                    vibrateTile(20);
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
                announceForAccessibility(announcement);
            }
        };
        r.tile.setCallback(callback);
        final View.OnClickListener click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.tile.click();
                vibrateTile(20);
            }
        };
        final View.OnClickListener clickSecondary = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.tile.secondaryClick();
                vibrateTile(20);
            }
        };
        final View.OnLongClickListener longClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                r.tile.longClick();
                vibrateTile(20);
                return true;
            }
        };
        r.tileView.init(click, clickSecondary, longClick);
        r.tile.setListening(mListening);
        callback.onStateChanged(r.tile.getState());
        r.tile.refreshState();
        mRecords.add(r);

        addView(r.tileView);
    }

    public boolean isShowingDetail() {
        return mDetailRecord != null;
    }

    public void closeDetail() {
        showDetail(false, mDetailRecord);
    }

    public boolean isClosingDetail() {
        return mClosingDetail;
    }

    public int getGridHeight() {
        return mGridHeight;
    }

    private void handleShowDetail(Record r, boolean show) {
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
        int y = r.tileView.getTop() + r.tileView.getHeight() / 2;
        handleShowDetailImpl(r, show, x, y);
    }

    private void handleShowDetailImpl(Record r, boolean show, int x, int y) {
        boolean visibleDiff = (mDetailRecord != null) != show;
        if (!visibleDiff && mDetailRecord == r) return;  // already in right state
        DetailAdapter detailAdapter = null;
        AnimatorListener listener = null;
        if (show) {
            detailAdapter = r.detailAdapter;
            r.detailView = detailAdapter.createDetailView(mContext, r.detailView, mDetailContent);
            if (r.detailView == null) throw new IllegalStateException("Must return detail view");

            final Intent settingsIntent = detailAdapter.getSettingsIntent();
            mDetailSettingsButton.setVisibility(settingsIntent != null ? VISIBLE : GONE);
            mDetailSettingsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mHost.startActivityDismissingKeyguard(settingsIntent);
                    vibrateTile(20);
                }
            });

            mDetailContent.removeAllViews();
            mDetail.bringToFront();
            mDetailContent.addView(r.detailView);
            MetricsLogger.visible(mContext, detailAdapter.getMetricsCategory());
            announceForAccessibility(mContext.getString(
                    R.string.accessibility_quick_settings_detail,
                    mContext.getString(detailAdapter.getTitle())));
            setDetailRecord(r);
            listener = mHideGridContentWhenDone;
            if (r instanceof TileRecord && visibleDiff) {
                ((TileRecord) r).openingDetail = true;
            }
        } else {
            if (mDetailRecord != null) {
                MetricsLogger.hidden(mContext, mDetailRecord.detailAdapter.getMetricsCategory());
            }
            mClosingDetail = true;
            setGridContentVisibility(true);
            listener = mTeardownDetailWhenDone;
            fireScanStateChanged(false);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        fireShowingDetail(show ? detailAdapter : null);
        if (visibleDiff) {
            mClipper.animateCircularClip(x, y, show, listener);
        }
    }

    private void setGridContentVisibility(boolean visible) {
        int newVis = visible ? VISIBLE : INVISIBLE;
        for (int i = 0; i < mRecords.size(); i++) {
            TileRecord tileRecord = mRecords.get(i);
            if (tileRecord.tileView.getVisibility() != GONE) {
                tileRecord.tileView.setVisibility(newVis);
            }
        }
        mBrightnessView.setVisibility(showBrightnessSlider() ? newVis : GONE);
        if (mGridContentVisible != visible) {
            MetricsLogger.visibility(mContext, MetricsLogger.QS_PANEL, newVis);
        }
        mGridContentVisible = visible;
    }

    private void logTiles() {
        for (int i = 0; i < mRecords.size(); i++) {
            TileRecord tileRecord = mRecords.get(i);
            if (tileRecord.tile.getState().visible) {
                MetricsLogger.visible(mContext, tileRecord.tile.getMetricsCategory());
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        mBrightnessView.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        final int brightnessHeight = mBrightnessView.getMeasuredHeight() + mBrightnessPaddingTop;
        mFooter.getView().measure(exactly(width), MeasureSpec.UNSPECIFIED);
        int r = -1;
        int c = -1;
        int rows = 0;
        for (TileRecord record : mRecords) {
            if (record.tileView.getVisibility() == GONE) continue;
            // wrap to next column if we've reached the max # of columns
            if (mUseMainTiles && r == 0 && c == 1) {
                r = 1;
                c = 0;
            } else if (r == -1 || c == (mColumns - 1)) {
                r++;
                c = 0;
            } else {
                c++;
            }
            record.row = r;
            record.col = c;
            rows = r + 1;
        }

        View previousView = mBrightnessView;
        for (TileRecord record : mRecords) {
            if (record.tileView.getVisibility() == GONE) continue;
            final int cw = (mUseMainTiles && record.row == 0) ? mLargeCellWidth : mCellWidth;
            final int ch = (mUseMainTiles && record.row == 0) ? mLargeCellHeight : mCellHeight;
            record.tileView.measure(exactly(cw), exactly(ch));
            previousView = record.tileView.updateAccessibilityOrder(previousView);
        }
        int h = rows == 0 ? brightnessHeight : (getRowTop(rows) + mPanelPaddingBottom);
        if (mFooter.hasFooter()) {
            h += mFooter.getView().getMeasuredHeight();
        }
        mDetail.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        if (mDetail.getMeasuredHeight() < h) {
            mDetail.measure(exactly(width), exactly(h));
        }
        mGridHeight = h;
        setMeasuredDimension(width, Math.max(h, mDetail.getMeasuredHeight()));
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int w = getWidth();
        mBrightnessView.layout(0, mBrightnessPaddingTop,
                mBrightnessView.getMeasuredWidth(),
                mBrightnessPaddingTop + mBrightnessView.getMeasuredHeight());
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (TileRecord record : mRecords) {
            if (record.tileView.getVisibility() == GONE) continue;
            final int cols = getColumnCount(record.row);
            final int cw = (mUseMainTiles && record.row == 0) ? mLargeCellWidth : mCellWidth;
            final int extra = (w - cw * cols) / (cols + 1);
            int left = record.col * cw + (record.col + 1) * extra;
            final int top = getRowTop(record.row);
            int right;
            int tileWith = record.tileView.getMeasuredWidth();
            if (isRtl) {
                right = w - left;
                left = right - tileWith;
            } else {
                right = left + tileWith;
            }
            record.tileView.layout(left, top, right, top + record.tileView.getMeasuredHeight());
        }
        final int dh = Math.max(mDetail.getMeasuredHeight(), getMeasuredHeight());
        mDetail.layout(0, 0, mDetail.getMeasuredWidth(), dh);
        if (mFooter.hasFooter()) {
            View footer = mFooter.getView();
            footer.layout(0, getMeasuredHeight() - footer.getMeasuredHeight(),
                    footer.getMeasuredWidth(), getMeasuredHeight());
        }
    }

    private int getRowTop(int row) {
        if (row <= 0) return mBrightnessView.getMeasuredHeight() + mBrightnessPaddingTop;
        return mBrightnessView.getMeasuredHeight() + mBrightnessPaddingTop
                + (mUseMainTiles ? mLargeCellHeight - mDualTileUnderlap : mCellHeight)
                + (row - 1) * mCellHeight;
    }

    private int getColumnCount(int row) {
        int cols = 0;
        for (TileRecord record : mRecords) {
            if (record.tileView.getVisibility() == GONE) continue;
            if (record.row == row) cols++;
        }
        return cols;
    }

    private void fireShowingDetail(QSTile.DetailAdapter detail) {
        if (mCallback != null) {
            mCallback.onShowingDetail(detail);
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

    private void setDetailRecord(Record r) {
        if (r == mDetailRecord) return;
        mDetailRecord = r;
        final boolean scanState = mDetailRecord instanceof TileRecord
                && ((TileRecord) mDetailRecord).scanState;
        fireScanStateChanged(scanState);
    }

    private class H extends Handler {
        private static final int SHOW_DETAIL = 1;
        private static final int SET_TILE_VISIBILITY = 2;
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SHOW_DETAIL) {
                handleShowDetail((Record)msg.obj, msg.arg1 != 0);
            } else if (msg.what == SET_TILE_VISIBILITY) {
                handleSetTileVisibility((View)msg.obj, msg.arg1);
            }
        }
    }

    private static class Record {
        View detailView;
        DetailAdapter detailAdapter;
        int x;
        int y;
    }

    protected static final class TileRecord extends Record {
        public QSTile<?> tile;
        public QSTileView tileView;
        public int row;
        public int col;
        public boolean scanState;
        public boolean openingDetail;
    }

    private final AnimatorListenerAdapter mTeardownDetailWhenDone = new AnimatorListenerAdapter() {
        public void onAnimationEnd(Animator animation) {
            mDetailContent.removeAllViews();
            setDetailRecord(null);
            mClosingDetail = false;
        };
    };

    private final AnimatorListenerAdapter mHideGridContentWhenDone = new AnimatorListenerAdapter() {
        public void onAnimationCancel(Animator animation) {
            // If we have been cancelled, remove the listener so that onAnimationEnd doesn't get
            // called, this will avoid accidentally turning off the grid when we don't want to.
            animation.removeListener(this);
            redrawTile();
        };

        @Override
        public void onAnimationEnd(Animator animation) {
            // Only hide content if still in detail state.
            if (mDetailRecord != null) {
                setGridContentVisibility(true);
                redrawTile();
            }
        }

        private void redrawTile() {
            if (mDetailRecord instanceof TileRecord) {
                final TileRecord tileRecord = (TileRecord) mDetailRecord;
                tileRecord.openingDetail = false;
                drawTile(tileRecord, tileRecord.tile.getState());
            }
        }
    };

    public interface Callback {
        void onShowingDetail(QSTile.DetailAdapter detail);
        void onToggleStateChanged(boolean state);
        void onScanStateChanged(boolean state);
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUICK_SETTINGS_TILES_VIBRATE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_USE_MAIN_TILES),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            mVibrationEnabled = Settings.System.getIntForUser(
            mContext.getContentResolver(), Settings.System.QUICK_SETTINGS_TILES_VIBRATE,
                0, UserHandle.USER_CURRENT) == 1;
           mUseMainTiles = Settings.System.getIntForUser(
            mContext.getContentResolver(), Settings.System.QS_USE_MAIN_TILES,
                1, UserHandle.USER_CURRENT) == 1;
        }
    }
}

