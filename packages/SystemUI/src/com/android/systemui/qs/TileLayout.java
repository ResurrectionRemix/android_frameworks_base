package com.android.systemui.qs;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.qs.QSPanel.QSTileLayout;
import com.android.systemui.qs.QSPanel.TileRecord;

import java.util.ArrayList;

public class TileLayout extends ViewGroup implements QSTileLayout {

    private static final float TILE_ASPECT = 1.2f;

    private static final String TAG = "TileLayout";

    protected int mColumns;
    protected int mCellWidth;
    protected int mCellHeight;
    protected int mCellMarginHorizontal;
    protected int mCellMarginVertical;
    protected int mSidePadding;
    protected int mRows = 1;

    protected final ArrayList<TileRecord> mRecords = new ArrayList<>();
    private int mCellMarginTop;
    private boolean mListening;
    protected int mMaxAllowedRows = 3;
    protected boolean mShowTitles = true;
    private boolean mLayoutChanged = false;

    public TileLayout(Context context) {
        this(context, null);
    }

    public TileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusableInTouchMode(true);
        updateResources();
    }

    @Override
    public int getOffsetTop(TileRecord tile) {
        return getTop();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        for (TileRecord record : mRecords) {
            record.tile.setListening(this, mListening);
        }
    }

    public void addTile(TileRecord tile) {
        mRecords.add(tile);
        tile.tile.setListening(this, mListening);
        addTileView(tile);
    }

    protected void addTileView(TileRecord tile) {
        addView(tile.tileView);
        tile.tileView.textVisibility();
    }

    @Override
    public void removeTile(TileRecord tile) {
        mRecords.remove(tile);
        tile.tile.setListening(this, false);
        removeView(tile.tileView);
    }

    public void removeAllViews() {
        for (TileRecord record : mRecords) {
            record.tile.setListening(this, false);
        }
        mRecords.clear();
        super.removeAllViews();
    }

    public boolean updateResources() {
        final Resources res = mContext.getResources();
        mCellHeight = mContext.getResources().getDimensionPixelSize(R.dimen.qs_tile_height);
        mCellMarginHorizontal = res.getDimensionPixelSize(R.dimen.qs_tile_margin_horizontal);
        mCellMarginVertical= res.getDimensionPixelSize(R.dimen.qs_tile_margin_vertical);
        mCellMarginTop = res.getDimensionPixelSize(R.dimen.qs_tile_margin_top);
        mSidePadding = res.getDimensionPixelOffset(R.dimen.qs_tile_layout_margin_side);
        mMaxAllowedRows = Math.max(1, getResources().getInteger(R.integer.quick_settings_max_rows));
        updateSettings();
        return mLayoutChanged;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If called with AT_MOST, it will limit the number of rows. If called with UNSPECIFIED
        // it will show all its tiles. In this case, the tiles have to be entered before the
        // container is measured. Any change in the tiles, should trigger a remeasure.
        final int numTiles = mRecords.size();
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int availableWidth = width - getPaddingStart() - getPaddingEnd();
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            mRows = (numTiles + mColumns - 1) / mColumns;
        }
        mCellWidth =
                (availableWidth - mSidePadding * 2 - (mCellMarginHorizontal * mColumns)) / mColumns;

        // Measure each QS tile.
        View previousView = this;
        for (TileRecord record : mRecords) {
            if (record.tileView.getVisibility() == GONE) continue;
            record.tileView.measure(exactly(mCellWidth), exactly(mCellHeight));
            previousView = record.tileView.updateAccessibilityOrder(previousView);
        }

        // Only include the top margin in our measurement if we have more than 1 row to show.
        // Otherwise, don't add the extra margin buffer at top.
        int height = (mCellHeight + mCellMarginVertical) * mRows +
                (mRows != 0 ? (mCellMarginTop - mCellMarginVertical) : 0);
        if (height < 0) height = 0;

        setMeasuredDimension(width, height);
    }

    /**
     * Determines the maximum number of rows that can be shown based on height. Clips at a minimum
     * of 1 and a maximum of mMaxAllowedRows.
     *
     * @param heightMeasureSpec Available height.
     * @param tilesCount Upper limit on the number of tiles to show. to prevent empty rows.
     */
    public boolean updateMaxRows(int heightMeasureSpec, int tilesCount) {
        final Resources res = getContext().getResources();
        final ContentResolver resolver = mContext.getContentResolver();
        final int availableHeight = MeasureSpec.getSize(heightMeasureSpec) - mCellMarginTop
                + mCellMarginVertical;
        final int previousRows = mRows;
        // we aren't introducing any delay due to the Settings provider call here, because PagedTileLayout.onMeasure
        // calls updateMaxRows only if the panel height is changed or if updateResources has been triggered
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mRows = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_ROWS_PORTRAIT, 3,
                    UserHandle.USER_CURRENT);
        } else {
            mRows = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.QS_ROWS_LANDSCAPE, 1,
                        UserHandle.USER_CURRENT);
        }
        if (mRows < 1) {
            mRows = 1;
        }
        if (mRows > (tilesCount + mColumns - 1) / mColumns) {
            mRows = (tilesCount + mColumns - 1) / mColumns;
        }
        return previousRows != mRows;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    protected static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }


    protected void layoutTileRecords(int numRecords) {
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int row = 0;
        int column = 0;

        // Layout each QS tile.
        final int tilesToLayout = Math.min(numRecords, mRows * mColumns);
        for (int i = 0; i < tilesToLayout; i++, column++) {
            // If we reached the last column available to layout a tile, wrap back to the next row.
            if (column == mColumns) {
                column = 0;
                row++;
            }

            final TileRecord record = mRecords.get(i);
            final int top = getRowTop(row);
            final int left = getColumnStart(isRtl ? mColumns - column - 1 : column);
            final int right = left + mCellWidth;
            record.tileView.layout(left, top, right, top + record.tileView.getMeasuredHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutTileRecords(mRecords.size());
    }

    private int getRowTop(int row) {
        return row * (mCellHeight + mCellMarginVertical) + mCellMarginTop;
    }

    protected int getColumnStart(int column) {
        return getPaddingStart() + mSidePadding + mCellMarginHorizontal / 2 +
                column *  (mCellWidth + mCellMarginHorizontal);
    }


    public void updateSettings() {
        final Resources res = mContext.getResources();
        int defaultColumns = Math.max(1, res.getInteger(R.integer.quick_settings_num_columns));
        int defaultColumnsLand = Math.max(1, res.getInteger(R.integer.quick_settings_num_columns_land));
        int defaultRows = Math.max(1, res.getInteger(R.integer.quick_settings_num_columns));
        int defaultRowsLandscape = Math.min(2, res.getInteger(R.integer.quick_settings_max_rows));
        boolean isPortrait = res.getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        int columns = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QS_COLUMNS_PORTRAIT, defaultColumns,
                UserHandle.USER_CURRENT);
        int columnsLandscape = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QS_COLUMNS_LANDSCAPE, defaultColumnsLand,
                UserHandle.USER_CURRENT);
        int rows = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QS_ROWS_PORTRAIT, defaultRows,
                UserHandle.USER_CURRENT);
        int rowsLandscape = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QS_ROWS_LANDSCAPE, defaultRowsLandscape,
                UserHandle.USER_CURRENT);
        boolean showTitles = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QS_TILE_TITLE_VISIBILITY, 1,
                UserHandle.USER_CURRENT) == 1;
        if (showTitles) {
            mCellHeight = mContext.getResources().getDimensionPixelSize(R.dimen.qs_tile_height);
        } else {
            mCellHeight = mContext.getResources().getDimensionPixelSize(R.dimen.qs_tile_height_wo_label);
        }
        if (mColumns != (isPortrait ? columns : columnsLandscape) || mShowTitles != showTitles) {
            mColumns = isPortrait ? columns : columnsLandscape;
            mShowTitles = showTitles;
            mLayoutChanged = true;
            requestLayout();
        }
        if (mRows != (isPortrait ? rows : rowsLandscape) || mShowTitles != showTitles) {
            mRows = isPortrait ? rows : rowsLandscape;
            mShowTitles = showTitles;
            mLayoutChanged = true;
            requestLayout();
        }
    }

    @Override
    public int getNumColumns() {
        return mColumns;
    }

    @Override
    public int getNumRows() {
        return mRows;
    }

    @Override
    public int getNumVisibleTiles() {
        return mRecords.size();
    }

    @Override
    public boolean isShowTitles() {
        return mShowTitles;
    }
}
