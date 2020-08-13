/**
 * Copyright (C) 2019-2020 crDroid Android Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkStats;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import lineageos.providers.LineageSettings;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.tuner.TunerService;

import java.text.DecimalFormat;
import java.util.HashMap;

import android.provider.Settings;

import com.android.internal.util.rr.RRFontHelper;

public class NetworkTraffic extends TextView implements TunerService.Tunable {
    private static final String TAG = "NetworkTraffic";

    private static final int MODE_UPSTREAM_AND_DOWNSTREAM = 0;
    private static final int MODE_UPSTREAM_ONLY = 1;
    private static final int MODE_DOWNSTREAM_ONLY = 2;

    protected static final int LOCATION_DISABLED = 0;
    protected static final int LOCATION_STATUSBAR = 1;
    protected static final int LOCATION_QUICK_STATUSBAR = 2;

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int MESSAGE_TYPE_UPDATE_VIEW = 1;

    private static final int Kilo = 1000;
    private static final int Mega = Kilo * Kilo;
    private static final int Giga = Mega * Kilo;

    private static final String NETWORK_TRAFFIC_LOCATION =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_LOCATION;
    private static final String NETWORK_TRAFFIC_MODE =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_MODE;
    private static final String NETWORK_TRAFFIC_AUTOHIDE =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE;
    private static final String NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD;
    private static final String NETWORK_TRAFFIC_UNITS =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_UNITS;
    private static final String NETWORK_TRAFFIC_REFRESH_INTERVAL =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_REFRESH_INTERVAL;
    private static final String NETWORK_TRAFFIC_HIDEARROW =
            "lineagesecure:" + LineageSettings.Secure.NETWORK_TRAFFIC_HIDEARROW;
    private static final String NETWORK_TRAFFIC_FONT =
            "system:" + Settings.System.TRAFFIC_FONT_STYLE;

    protected int mLocation = LOCATION_DISABLED;
    private int mMode = MODE_UPSTREAM_AND_DOWNSTREAM;
    private int mSubMode = MODE_UPSTREAM_AND_DOWNSTREAM;
    protected boolean mIsActive;
    private boolean mTrafficActive;
    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;
    private boolean mAutoHide;
    private long mAutoHideThreshold;
    private int mUnits;
    protected int mIconTint = 0;
    protected int newTint = Color.WHITE;

    private Drawable mDrawable;

    private int mRefreshInterval = 2;

    protected boolean mAttached;
    private boolean mHideArrows;

    private INetworkManagementService mNetworkManagementService;

    protected boolean mVisible = true;
    protected boolean mScreenOn = true;

    private ConnectivityManager mConnectivityManager;

    private RelativeSizeSpan mSpeedRelativeSizeSpan = new RelativeSizeSpan(0.70f);
    private RelativeSizeSpan mUnitRelativeSizeSpan = new RelativeSizeSpan(0.65f);

    protected boolean mEnabled = false;
    protected boolean mConnectionAvailable = true;
    private int mFontStyle;

    public NetworkTraffic(Context context) {
        this(context, null);
    }

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mConnectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            final TunerService tunerService = Dependency.get(TunerService.class);
            tunerService.addTunable(this, NETWORK_TRAFFIC_LOCATION);
            tunerService.addTunable(this, NETWORK_TRAFFIC_MODE);
            tunerService.addTunable(this, NETWORK_TRAFFIC_AUTOHIDE);
            tunerService.addTunable(this, NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD);
            tunerService.addTunable(this, NETWORK_TRAFFIC_UNITS);
            tunerService.addTunable(this, NETWORK_TRAFFIC_REFRESH_INTERVAL);
            tunerService.addTunable(this, NETWORK_TRAFFIC_HIDEARROW);
            tunerService.addTunable(this, NETWORK_TRAFFIC_FONT);
            mConnectionAvailable = mConnectivityManager.getActiveNetworkInfo() != null;

            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mIntentReceiver, filter, null, mTrafficHandler);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            Dependency.get(TunerService.class).removeTunable(this);
            mAttached = false;
        }
    }

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long rxBytes = 0;
            long txBytes = 0;

            if (msg.what == MESSAGE_TYPE_PERIODIC_REFRESH) {
                final long now = SystemClock.elapsedRealtime();
                long timeDelta = now - mLastUpdateTime; /* ms */

                if (timeDelta >= mRefreshInterval * 1000 * 0.95f) {
                    long[] newTotalRxTxBytes = getTotalRxTxBytes();

                    final long rxBytesDelta = newTotalRxTxBytes[0] - mLastRxBytes;
                    final long txBytesDelta = newTotalRxTxBytes[1] - mLastTxBytes;

                    rxBytes = (long) (rxBytesDelta / (timeDelta / 1000f));
                    txBytes = (long) (txBytesDelta / (timeDelta / 1000f));

                    mLastRxBytes = newTotalRxTxBytes[0];
                    mLastTxBytes = newTotalRxTxBytes[1];
                    mLastUpdateTime = now;
                }
            }

            final boolean showUpstream =
                    mMode == MODE_UPSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean showDownstream =
                    mMode == MODE_DOWNSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean aboveThreshold = (showUpstream && txBytes > mAutoHideThreshold)
                    || (showDownstream && rxBytes > mAutoHideThreshold);
            mIsActive = mAttached && mConnectionAvailable && (!mAutoHide || aboveThreshold);
            int submode = MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean trafficactive = (txBytes > 0 || rxBytes > 0);

            clearHandlerCallbacks();

            if (mEnabled && mIsActive) {
                CharSequence output = "";
                if (showUpstream && showDownstream) {
                    if (txBytes > rxBytes) {
                        output = formatOutput(txBytes);
                        submode = MODE_UPSTREAM_ONLY;
                    } else if (txBytes < rxBytes) {
                        output = formatOutput(rxBytes);
                        submode = MODE_DOWNSTREAM_ONLY;
                    } else {
                        output = formatOutput(rxBytes);
                        submode = MODE_UPSTREAM_AND_DOWNSTREAM;
                    }
                } else if (showDownstream) {
                    output = formatOutput(rxBytes);
                } else if (showUpstream) {
                    output = formatOutput(txBytes);
                }

                // Update view if there's anything new to show
                if (output != getText()) {
                    setText(output);
                }
            }

            updateVisibility();

            if (mVisible && (mSubMode != submode ||
                    mTrafficActive != trafficactive)) {
                mSubMode = submode;
                mTrafficActive = trafficactive;
                setTrafficDrawable();
            }

            // Schedule periodic refresh
            if (mEnabled && mScreenOn && mAttached) {
                mTrafficHandler.sendEmptyMessageDelayed(MESSAGE_TYPE_PERIODIC_REFRESH,
                        mRefreshInterval * 1000);
            }
        }

        private CharSequence formatOutput(long speed) {
            DecimalFormat decimalFormat;
            String unit;
            String formatSpeed;
            SpannableString spanUnitString;
            SpannableString spanSpeedString;
            String gunit, munit, kunit;

            if (mUnits == 0) {
                // speed is in bytes, convert to bits
                speed = speed * 8;
                gunit = mContext.getString(R.string.gigabitspersecond_short);
                munit = mContext.getString(R.string.megabitspersecond_short);
                kunit = mContext.getString(R.string.kilobitspersecond_short);
            } else {
                gunit = mContext.getString(R.string.gigabytespersecond_short);
                munit = mContext.getString(R.string.megabytespersecond_short);
                kunit = mContext.getString(R.string.kilobytespersecond_short);
            }

            if (speed >= Giga) {
                unit = gunit;
                decimalFormat = new DecimalFormat("0.##");
                formatSpeed = decimalFormat.format(speed / (float)Giga);
            } else if (speed >= 100 * Mega) {
                decimalFormat = new DecimalFormat("##0");
                unit = munit;
                formatSpeed = decimalFormat.format(speed / (float)Mega);
            } else if (speed >= 10 * Mega) {
                decimalFormat = new DecimalFormat("#0.#");
                unit = munit;
                formatSpeed = decimalFormat.format(speed / (float)Mega);
            } else if (speed >= Mega) {
                decimalFormat = new DecimalFormat("0.##");
                unit = munit;
                formatSpeed = decimalFormat.format(speed / (float)Mega);
            } else if (speed >= 100 * Kilo) {
                decimalFormat = new DecimalFormat("##0");
                unit = kunit;
                formatSpeed = decimalFormat.format(speed / (float)Kilo);
            } else if (speed >= 10 * Kilo) {
                decimalFormat = new DecimalFormat("#0.#");
                unit = kunit;
                formatSpeed = decimalFormat.format(speed / (float)Kilo);
            } else {
                decimalFormat = new DecimalFormat("0.##");
                unit = kunit;
                formatSpeed = decimalFormat.format(speed / (float)Kilo);
            }
            spanSpeedString = new SpannableString(formatSpeed);
            spanSpeedString.setSpan(mSpeedRelativeSizeSpan, 0, (formatSpeed).length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            spanUnitString = new SpannableString(unit);
            spanUnitString.setSpan(mUnitRelativeSizeSpan, 0, (unit).length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return TextUtils.concat(spanSpeedString, "\n", spanUnitString);
        }

        private long[] getTotalRxTxBytes() {
            long[] bytes = new long[] { 0, 0 };

            // Sum tx and rx bytes from all sources of interest
            // Add stats
            bytes[0] = TrafficStats.getTotalRxBytes();
            bytes[1] = TrafficStats.getTotalTxBytes();

            // Add tether hw offload counters since these are
            // not included in netd interface stats.
            final TetheringStats tetheringStats = getOffloadTetheringStats();
            bytes[0] += tetheringStats.rxBytes;
            bytes[1] += tetheringStats.txBytes;

            return bytes;
        }
    };

    protected void setEnabled() {
        mEnabled = mLocation == LOCATION_QUICK_STATUSBAR;
    }

    protected void updateVisibility() {
        boolean visible = mEnabled && mIsActive && getText() != "";
        if (visible != mVisible) {
            mVisible = visible;
            setVisibility(mVisible ? VISIBLE : GONE);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                mConnectionAvailable = mConnectivityManager.getActiveNetworkInfo() != null;
                if (mScreenOn) updateViews();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
                updateViews();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                clearHandlerCallbacks();
            }
        }
    };

    private class TetheringStats {
        long txBytes;
        long rxBytes;
    }

    private TetheringStats getOffloadTetheringStats() {
        TetheringStats tetheringStats = new TetheringStats();

        NetworkStats stats = null;

        if (mNetworkManagementService == null) {
            mNetworkManagementService = INetworkManagementService.Stub.asInterface(
                    ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
        }

        try {
            // STATS_PER_UID returns hw offload and netd stats combined (as entry UID_TETHERING)
            // STATS_PER_IFACE returns only hw offload stats (as entry UID_ALL)
            stats = mNetworkManagementService.getNetworkStatsTethering(
                    NetworkStats.STATS_PER_IFACE);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to call getNetworkStatsTethering: " + e);
        }
        if (stats == null) {
            // nothing we can do except return zero stats
            return tetheringStats;
        }

        NetworkStats.Entry entry = null;
        // Entries here are per tethered interface.
        // Counters persist even after tethering has been disabled.
        for (int i = 0; i < stats.size(); i++) {
            entry = stats.getValues(i, entry);
            // hw offload tether stats are reported under UID_ALL.
            if (entry.uid == NetworkStats.UID_ALL) {
                tetheringStats.txBytes += entry.txBytes;
                tetheringStats.rxBytes += entry.rxBytes;
            }
        }
        return tetheringStats;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case NETWORK_TRAFFIC_LOCATION:
                mLocation =
                        TunerService.parseInteger(newValue, 0);
                setEnabled();
                if (mEnabled) {
                    setLines(2);
                    String txtFont = getResources().getString(com.android.internal.R.string.config_bodyFontFamily);
                    setTypeface(Typeface.create(txtFont, Typeface.BOLD));
                    setLineSpacing(0.75f, 0.75f);
                }
                updateViews();
                break;
            case NETWORK_TRAFFIC_MODE:
                mMode =
                        TunerService.parseInteger(newValue, 0);
                updateViews();
                setTrafficDrawable();
                break;
            case NETWORK_TRAFFIC_AUTOHIDE:
                mAutoHide =
                        TunerService.parseIntegerSwitch(newValue, false);
                updateViews();
                break;
            case NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD:
                int autohidethreshold =
                        TunerService.parseInteger(newValue, 0);
                mAutoHideThreshold = autohidethreshold * Kilo; /* Convert kB to Bytes */
                updateViews();
                break;
            case NETWORK_TRAFFIC_UNITS:
                mUnits =
                        TunerService.parseInteger(newValue, 1);
                updateViews();
                break;
            case NETWORK_TRAFFIC_REFRESH_INTERVAL:
                mRefreshInterval =
                        TunerService.parseInteger(newValue, 2);
                updateViews();
                break;
            case NETWORK_TRAFFIC_FONT:
                mFontStyle =
                        TunerService.parseInteger(newValue, 0);
                RRFontHelper.setFontType(this, mFontStyle);
                updateViews();
                break;
            case NETWORK_TRAFFIC_HIDEARROW:
                mHideArrows =
                        TunerService.parseIntegerSwitch(newValue, false);
                if (!mHideArrows) {
                    setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
                } else {
                    setGravity(Gravity.CENTER);
                }
                setTrafficDrawable();
                break;
            default:
                break;
        }
    }

    protected void updateViews() {
        if (mEnabled) {
            updateViewState();
        }
    }

    private void updateViewState() {
        mTrafficHandler.removeMessages(MESSAGE_TYPE_UPDATE_VIEW);
        mTrafficHandler.sendEmptyMessageDelayed(MESSAGE_TYPE_UPDATE_VIEW, 1000);
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
        mTrafficHandler.removeMessages(MESSAGE_TYPE_UPDATE_VIEW);
    }

    private void setTrafficDrawable() {
        final int drawableResId;
        final Drawable drawable;
        if (!mVisible) return;
        if (mHideArrows) {
            drawableResId = 0;
        } else if (!mTrafficActive) {
            drawableResId = R.drawable.stat_sys_network_traffic;
        } else if (mMode == MODE_UPSTREAM_ONLY || mSubMode == MODE_UPSTREAM_ONLY) {
            drawableResId = R.drawable.stat_sys_network_traffic_up;
        } else if (mMode == MODE_DOWNSTREAM_ONLY || mSubMode == MODE_DOWNSTREAM_ONLY) {
            drawableResId = R.drawable.stat_sys_network_traffic_down;
        } else if (mMode == MODE_UPSTREAM_AND_DOWNSTREAM) {
            drawableResId = R.drawable.stat_sys_network_traffic_updown;
        } else {
            drawableResId = 0;
        }
        drawable = drawableResId != 0 ? getResources().getDrawable(drawableResId) : null;
        if (mDrawable != drawable || mIconTint != newTint) {
            mDrawable = drawable;
            mIconTint = newTint;
            setCompoundDrawablesWithIntrinsicBounds(null, null, mDrawable, null);
            updateTrafficDrawable();
        }
    }

    protected void updateTrafficDrawable() {
        if (mDrawable != null) {
            mDrawable.setColorFilter(mIconTint, PorterDuff.Mode.MULTIPLY);
        }
        setTextColor(mIconTint);
    }
}

