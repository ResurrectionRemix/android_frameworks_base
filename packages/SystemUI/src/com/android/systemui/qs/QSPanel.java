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
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile.DetailAdapter;
import com.android.systemui.qs.QSTile.Host.Callback;
import com.android.systemui.qs.customize.QSCustomizer;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;
import java.util.Collection;

import cyanogenmod.providers.CMSettings;

/** View that represents the quick settings tile panel. **/
public class QSPanel extends LinearLayout implements Tunable, Callback {

    public static final String QS_SHOW_BRIGHTNESS = "qs_show_brightness";

    protected final Context mContext;
    protected final ArrayList<TileRecord> mRecords = new ArrayList<TileRecord>();
    protected final View mBrightnessView;

    protected final ImageView mBrightnessIcon;
    private final H mHandler = new H();

    private int mPanelPaddingBottom;
    private int mBrightnessPaddingTop;
    protected boolean mExpanded;
    protected boolean mListening;

    private Callback mCallback;
    private BrightnessController mBrightnessController;
    protected QSTileHost mHost;

    protected QSFooter mFooter;
    private boolean mGridContentVisible = true;

    protected QSTileLayout mTileLayout;

    private QSCustomizer mCustomizePanel;
    private Record mDetailRecord;

    private BrightnessMirrorController mBrightnessMirrorController;

    protected Vibrator mVibrator;

    public QSPanel(Context context) {
        this(context, null);
    }

    public QSPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        setOrientation(VERTICAL);

        mBrightnessView = LayoutInflater.from(context).inflate(
                R.layout.quick_settings_brightness_dialog, this, false);
        addView(mBrightnessView);

        mBrightnessIcon = (ImageView) mBrightnessView.findViewById(R.id.brightness_icon);

        setupTileLayout();

        mFooter = new QSFooter(this, context);
        addView(mFooter.getView());
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        updateResources();

        mBrightnessController = new BrightnessController(getContext(),
                mBrightnessIcon,
                (ToggleSlider) findViewById(R.id.brightness_slider),
                (CheckBox) findViewById(R.id.brightness_auto));

    }

    protected void setupTileLayout() {
        mTileLayout = (QSTileLayout) LayoutInflater.from(mContext).inflate(
                R.layout.qs_paged_tile_layout, this, false);
        mTileLayout.setListening(mListening);
        addView((View) mTileLayout);
        if (getResources().getBoolean(R.bool.config_show_auto_brightness)) {
            ((CheckBox) findViewById(R.id.brightness_auto)).setVisibility(View.VISIBLE);
        }
    }

    public boolean isShowingCustomize() {
        return mCustomizePanel != null && mCustomizePanel.isCustomizing();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TunerService.get(mContext).addTunable(this, QS_SHOW_BRIGHTNESS);
        if (mHost != null) {
            setTiles(mHost.getTiles());
        }
    }

    public boolean isVibrationEnabled() {
        return (Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.QUICK_SETTINGS_TILES_VIBRATE, 0, UserHandle.USER_CURRENT) == 1);
    }

    public void setenabled() {
      mCustomizePanel.setenabled();
    }

    public void vibrateTile(int duration) {
        if (!isVibrationEnabled()) { return; }
        if (mVibrator != null) {
            if (mVibrator.hasVibrator()) { mVibrator.vibrate(duration); }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        TunerService.get(mContext).removeTunable(this);
        mHost.removeCallback(this);
        for (TileRecord record : mRecords) {
            record.tile.removeCallbacks();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onTilesChanged() {
        setTiles(mHost.getTiles());
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (QS_SHOW_BRIGHTNESS.equals(key)) {
            mBrightnessView.setVisibility(newValue == null || Integer.parseInt(newValue) != 0
                    ? VISIBLE : GONE);
        }
    }

    public void openDetails(String subPanel) {
        QSTile<?> tile = getTile(subPanel);
        showDetailAdapter(true, tile.getDetailAdapter(), new int[] {getWidth() / 2, 0});
    }

    private QSTile<?> getTile(String subPanel) {
        for (int i = 0; i < mRecords.size(); i++) {
            if (subPanel.equals(mRecords.get(i).tile.getTileSpec())) {
                return mRecords.get(i).tile;
            }
        }
        return mHost.createTile(subPanel);
    }

    private void setBrightnessIcon() {
        boolean brightnessIconEnabled = Settings.System.getIntForUser(
            mContext.getContentResolver(), Settings.System.QS_SHOW_BRIGHTNESS_ICON,
                0, UserHandle.USER_CURRENT) == 1;
        mBrightnessIcon.setVisibility(brightnessIconEnabled ? View.VISIBLE : View.GONE);
        updateResources();
    }

    public void setBrightnessMirror(BrightnessMirrorController c) {
        mBrightnessMirrorController = c;
        ToggleSlider brightnessSlider = (ToggleSlider) findViewById(R.id.brightness_slider);
        ToggleSlider mirror = (ToggleSlider) c.getMirror().findViewById(R.id.brightness_slider);
        brightnessSlider.setMirror(mirror);
        brightnessSlider.setMirrorController(c);
    }

    View getBrightnessView() {
        return mBrightnessView;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setHost(QSTileHost host, QSCustomizer customizer) {
        mHost = host;
        mHost.addCallback(this);
        setTiles(mHost.getTiles());
        mFooter.setHost(host);
        mCustomizePanel = customizer;
        if (mCustomizePanel != null) {
            mCustomizePanel.setHost(mHost);
        }
        mBrightnessController.setBackgroundLooper(host.getLooper());
    }

    public QSTileHost getHost() {
        return mHost;
    }

    public void updateResources() {
        final Resources res = mContext.getResources();
        mPanelPaddingBottom = res.getDimensionPixelSize(R.dimen.qs_panel_padding_bottom);
        mBrightnessPaddingTop = res.getDimensionPixelSize(R.dimen.qs_brightness_padding_top);
        setPadding(0, mBrightnessPaddingTop, 0, mPanelPaddingBottom);
        for (TileRecord r : mRecords) {
            r.tile.clearState();
        }
        if (mListening) {
            refreshAllTiles();
        }
        if (mTileLayout != null) {
            mTileLayout.updateResources();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mFooter.onConfigurationChanged();

        if (mBrightnessMirrorController != null) {
            // Reload the mirror in case it got reinflated but we didn't.
            setBrightnessMirror(mBrightnessMirrorController);
        }
    }

    public void onCollapse() {
        if (mCustomizePanel != null && mCustomizePanel.isShown()) {
            mCustomizePanel.hide(mCustomizePanel.getWidth() / 2, mCustomizePanel.getHeight() / 2);
        }
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        if (!mExpanded && mTileLayout instanceof PagedTileLayout) {
            ((PagedTileLayout) mTileLayout).setCurrentItem(0, false);
        }
        MetricsLogger.visibility(mContext, MetricsEvent.QS_PANEL, mExpanded);
        if (!mExpanded) {
            closeDetail();
        } else {
            logTiles();
        }
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mTileLayout != null) {
            mTileLayout.setListening(listening);
        }
        mFooter.setListening(mListening);
        if (mListening) {
            refreshAllTiles();
        }
        if (mBrightnessView.getVisibility() == View.VISIBLE) {
            if (listening) {
                mBrightnessController.registerCallbacks();
            } else {
                mBrightnessController.unregisterCallbacks();
            }
        }
        setBrightnessIcon();
    }

    public void refreshAllTiles() {
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

    public void setTiles(Collection<QSTile<?>> tiles) {
        setTiles(tiles, false);
    }

    public void setTiles(Collection<QSTile<?>> tiles, boolean collapsedView) {
        for (TileRecord record : mRecords) {
            mTileLayout.removeTile(record);
            record.tile.removeCallback(record.callback);
        }
        mRecords.clear();
        for (QSTile<?> tile : tiles) {
            addTile(tile, collapsedView);
        }
    }

    protected void drawTile(TileRecord r, QSTile.State state) {
        r.tileView.onStateChanged(state);
    }

    protected QSTileBaseView createTileView(QSTile<?> tile, boolean collapsedView) {
        return new QSTileView(mContext, tile.createTileView(mContext), collapsedView);
    }

    protected boolean shouldShowDetail() {
        return mExpanded;
    }

    private void setAnimationTile(TileRecord r) {
        ObjectAnimator animTile = null;
        int animStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ANIM_TILE_STYLE, 0, UserHandle.USER_CURRENT);
        int animDuration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ANIM_TILE_DURATION, 2000, UserHandle.USER_CURRENT);
        int interpolatorType = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ANIM_TILE_INTERPOLATOR, 0, UserHandle.USER_CURRENT);
        if (animStyle == 0) {
            //No animation
        }
        if (animStyle == 1) {
            animTile = ObjectAnimator.ofFloat(r.tileView, "rotationY", 0f, 360f);
        }
        if (animStyle == 2) {
            animTile = ObjectAnimator.ofFloat(r.tileView, "rotation", 0f, 360f);
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

    protected void addTile(final QSTile<?> tile, boolean collapsedView) {
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
        final View.OnClickListener click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTileClick(r.tile);
                setAnimationTile(r);
                vibrateTile(45);
            }
        };
        final View.OnLongClickListener longClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                r.tile.longClick();
                setAnimationTile(r);
                vibrateTile(45);
                return true;
            }
        };
        r.tileView.init(click, longClick);
        r.tile.refreshState();
        mRecords.add(r);

        if (mTileLayout != null) {
            mTileLayout.addTile(r);
        }
    }


    public void showEdit(final View v) {
        v.post(new Runnable() {
            @Override
            public void run() {
                if (mCustomizePanel != null) {
                    if (!mCustomizePanel.isCustomizing()) {
                        int[] loc = new int[2];
                        v.getLocationInWindow(loc);
                        int x = loc[0] + v.getWidth() / 2;
                        int y = loc[1] + v.getHeight() / 2;
                        mCustomizePanel.show(x, y);
                    }
                }

            }
        });
    }

    protected void onTileClick(QSTile<?> tile) {
        tile.click();
    }

    public void closeDetail() {
        if (mCustomizePanel != null && mCustomizePanel.isShown()) {
            // Treat this as a detail panel for now, to make things easy.
            mCustomizePanel.hide(mCustomizePanel.getWidth() / 2, mCustomizePanel.getHeight() / 2);
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
        int y = r.tileView.getTop() + mTileLayout.getOffsetTop(r) + r.tileView.getHeight() / 2
                + getTop();
        handleShowDetailImpl(r, show, x, y);
    }

    private void handleShowDetailImpl(Record r, boolean show, int x, int y) {
        setDetailRecord(show ? r : null);
        fireShowingDetail(show ? r.detailAdapter : null, x, y);
    }

    private void setDetailRecord(Record r) {
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
            MetricsLogger.visibility(mContext, MetricsEvent.QS_PANEL, newVis);
        }
        mGridContentVisible = visible;
    }

    private void logTiles() {
        for (int i = 0; i < mRecords.size(); i++) {
            TileRecord tileRecord = mRecords.get(i);
            MetricsLogger.visible(mContext, tileRecord.tile.getMetricsCategory());
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

    QSTileBaseView getTileView(QSTile<?> tile) {
        for (TileRecord r : mRecords) {
            if (r.tile == tile) {
                return r.tileView;
            }
        }
        return null;
    }

    public QSFooter getFooter() {
        return mFooter;
    }

    public void showDeviceMonitoringDialog() {
        mFooter.showDeviceMonitoringDialog();
    }

    private class H extends Handler {
        private static final int SHOW_DETAIL = 1;
        private static final int SET_TILE_VISIBILITY = 2;
        private static final int ANNOUNCE_FOR_ACCESSIBILITY = 3;
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SHOW_DETAIL) {
                handleShowDetail((Record)msg.obj, msg.arg1 != 0);
            } else if (msg.what == ANNOUNCE_FOR_ACCESSIBILITY) {
                announceForAccessibility((CharSequence)msg.obj);
            }
        }
    }

    protected static class Record {
        DetailAdapter detailAdapter;
        int x;
        int y;
    }

    public static final class TileRecord extends Record {
        public QSTile<?> tile;
        public QSTileBaseView tileView;
        public boolean scanState;
        public QSTile.Callback callback;
    }

    public interface Callback {
        void onShowingDetail(DetailAdapter detail, int x, int y);
        void onToggleStateChanged(boolean state);
        void onScanStateChanged(boolean state);
    }

    public interface QSTileLayout {
        void addTile(TileRecord tile);
        void removeTile(TileRecord tile);
        int getOffsetTop(TileRecord tile);
        boolean updateResources();
        void updateSettings();

        void setListening(boolean listening);
    }

    public void updateSettings() {
        if (mTileLayout != null) {
            mTileLayout.updateSettings();
        }
        if (mCustomizePanel != null) {
            mCustomizePanel.updateSettings();
        }
        if (mFooter != null) {
            mFooter.updateSettings();
        }
    }
}
