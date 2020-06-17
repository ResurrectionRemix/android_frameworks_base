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
import android.database.ContentObserver;
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

import com.android.systemui.R;

import java.text.DecimalFormat;
import java.util.HashMap;

public class NetworkTraffic extends TextView {
    private static final String TAG = "NetworkTraffic";

    private static final boolean DEBUG = false;

    private static final int MODE_UPSTREAM_AND_DOWNSTREAM = 0;
    private static final int MODE_UPSTREAM_ONLY = 1;
    private static final int MODE_DOWNSTREAM_ONLY = 2;

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int MESSAGE_TYPE_UPDATE_VIEW = 1;

    private static final int Kilo = 1000;
    private static final int Mega = Kilo * Kilo;
    private static final int Giga = Mega * Kilo;

    protected int mLocation = 0;
    private int mMode = MODE_UPSTREAM_AND_DOWNSTREAM;
    private int mSubMode = MODE_UPSTREAM_AND_DOWNSTREAM;
    private boolean mConnectionAvailable;
    protected boolean mIsActive;
    private long mTxBytes;
    private long mRxBytes;
    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;
    private boolean mAutoHide;
    private long mAutoHideThreshold;
    private int mUnits;
    protected int mIconTint = 0;
    protected int newTint = Color.WHITE;

    private SettingsObserver mObserver;
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
        mObserver = new SettingsObserver(mTrafficHandler);
        mObserver.observe();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
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
            mAttached = false;
        }
    }

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final long now = SystemClock.elapsedRealtime();
            final long timeDelta = now - mLastUpdateTime; /* ms */

            if (msg.what == MESSAGE_TYPE_PERIODIC_REFRESH
                    && timeDelta >= mRefreshInterval * 1000 * 0.95f) {
                // Sum tx and rx bytes from all sources of interest
                long txBytes = 0;
                long rxBytes = 0;

                // Add stats
                final long newTxBytes = TrafficStats.getTotalTxBytes();
                final long newRxBytes = TrafficStats.getTotalRxBytes();

                txBytes += newTxBytes;
                rxBytes += newRxBytes;

                // Add tether hw offload counters since these are
                // not included in netd interface stats.
                final TetheringStats tetheringStats = getOffloadTetheringStats();
                txBytes += tetheringStats.txBytes;
                rxBytes += tetheringStats.rxBytes;

                if (DEBUG) {
                    Log.d(TAG, "tether hw offload txBytes: " + tetheringStats.txBytes
                            + " rxBytes: " + tetheringStats.rxBytes);
                }

                final long txBytesDelta = txBytes - mLastTxBytes;
                final long rxBytesDelta = rxBytes - mLastRxBytes;

                if (timeDelta > 0 && txBytesDelta >= 0 && rxBytesDelta >= 0) {
                    mTxBytes = (long) (txBytesDelta / (timeDelta / 1000f));
                    mRxBytes = (long) (rxBytesDelta / (timeDelta / 1000f));
                }
                mLastTxBytes = txBytes;
                mLastRxBytes = rxBytes;
                mLastUpdateTime = now;
            }

            mConnectionAvailable = isConnectionAvailable();
            final boolean enabled = mLocation != 0 && mScreenOn;
            final boolean showUpstream =
                    mMode == MODE_UPSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean showDownstream =
                    mMode == MODE_DOWNSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean aboveThreshold = (showUpstream && mTxBytes > mAutoHideThreshold)
                    || (showDownstream && mRxBytes > mAutoHideThreshold);
            mIsActive = enabled && mAttached && (!mAutoHide || (mConnectionAvailable && aboveThreshold));
            int submode = MODE_UPSTREAM_AND_DOWNSTREAM;

            clearHandlerCallbacks();

            if (mIsActive) {
                CharSequence output = "";
                if (showUpstream && showDownstream) {
                    if (mTxBytes > mRxBytes) {
                        output = formatOutput(mTxBytes);
                        submode = MODE_UPSTREAM_ONLY;
                    } else if (mTxBytes < mRxBytes) {
                        output = formatOutput(mRxBytes);
                        submode = MODE_DOWNSTREAM_ONLY;
                    } else {
                        output = formatOutput(mRxBytes);
                        submode = MODE_UPSTREAM_AND_DOWNSTREAM;
                    }
                } else if (showDownstream) {
                    output = formatOutput(mRxBytes);
                } else if (showUpstream) {
                    output = formatOutput(mTxBytes);
                }

                // Update view if there's anything new to show
                if (output != getText()) {
                    setText(output);
                }
            }

            updateVisibility();

            if (mVisible && mSubMode != submode) {
                mSubMode = submode;
                setTrafficDrawable();
            }

            // Schedule periodic refresh
            if (enabled && mAttached) {
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
                decimalFormat = new DecimalFormat("0.00");
                formatSpeed =  decimalFormat.format(speed / (float)Giga);
            } else if (speed >= 100 * Mega) {
                decimalFormat = new DecimalFormat("###0");
                unit = munit;
                formatSpeed =  decimalFormat.format(speed / (float)Mega);
            } else if (speed >= 10 * Mega) {
                decimalFormat = new DecimalFormat("#0.0");
                unit = munit;
                formatSpeed =  decimalFormat.format(speed / (float)Mega);
            } else if (speed >= Mega) {
                decimalFormat = new DecimalFormat("0.00");
                unit = munit;
                formatSpeed =  decimalFormat.format(speed / (float)Mega);
            } else if (speed >= 100 * Kilo) {
                decimalFormat = new DecimalFormat("##0");
                unit = kunit;
                formatSpeed =  decimalFormat.format(speed / (float)Kilo);
            } else if (speed >= 10 * Kilo) {
                decimalFormat = new DecimalFormat("#0.0");
                unit = kunit;
                formatSpeed =  decimalFormat.format(speed / (float)Kilo);
            } else {
                decimalFormat = new DecimalFormat("0.00");
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
    };

    protected void updateVisibility() {
        boolean enabled = mIsActive && (mLocation == 2) && mScreenOn && getText() != "";
        if (enabled != mVisible) {
            mVisible = enabled;
            setVisibility(mVisible ? VISIBLE : GONE);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) && mScreenOn) {
                updateViews();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
                updateViews();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                updateViews();
            }
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_LOCATION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_UNITS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_REFRESH_INTERVAL),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_HIDEARROW),
                    false, this, UserHandle.USER_ALL);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private boolean isConnectionAvailable() {
        return mConnectivityManager.getActiveNetworkInfo() != null;
    }

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
            if (DEBUG) {
                Log.d(TAG, "tethering stats entry: " + entry);
            }
            // hw offload tether stats are reported under UID_ALL.
            if (entry.uid == NetworkStats.UID_ALL) {
                tetheringStats.txBytes += entry.txBytes;
                tetheringStats.rxBytes += entry.rxBytes;
            }
        }
        return tetheringStats;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mLocation = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_LOCATION, 0, UserHandle.USER_CURRENT);
        mMode = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0, UserHandle.USER_CURRENT);
        mAutoHide = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT) != 0;
        mAutoHideThreshold = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 0, UserHandle.USER_CURRENT);
        mUnits = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, /* Bytes */ 1,
                UserHandle.USER_CURRENT);

        mRefreshInterval = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_REFRESH_INTERVAL, 2, UserHandle.USER_CURRENT);
        mHideArrows = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_HIDEARROW, 0, UserHandle.USER_CURRENT) == 1;

        if (!mHideArrows) {
            setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        } else {
            setGravity(Gravity.CENTER);
        }
        setLines(2);
        String txtFont = getResources().getString(com.android.internal.R.string.config_bodyFontFamily);
        setTypeface(Typeface.create(txtFont, Typeface.BOLD));
        setLineSpacing(0.80f, 0.80f);
        setStaticHeight(true);

        setTrafficDrawable();
        updateViews();
    }

    protected void updateViews() {
        if (mLocation == 2 && mScreenOn) {
            updateViewState();
        } else {
            clearHandlerCallbacks();
            updateVisibility();
        }
    }

    protected void updateViewState() {
        mTrafficHandler.removeMessages(MESSAGE_TYPE_UPDATE_VIEW);
        mTrafficHandler.sendEmptyMessageDelayed(MESSAGE_TYPE_UPDATE_VIEW, 1000);
    }

    protected void clearHandlerCallbacks() {
        mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
        mTrafficHandler.removeMessages(MESSAGE_TYPE_UPDATE_VIEW);
    }

    protected void setTrafficDrawable() {
        final int drawableResId;
        final Resources resources = getResources();
        final Drawable drawable;

        if (!mHideArrows && mMode == MODE_UPSTREAM_AND_DOWNSTREAM) {
            if (mSubMode == MODE_DOWNSTREAM_ONLY) {
                drawableResId = R.drawable.stat_sys_network_traffic_down;
            } else if (mSubMode == MODE_UPSTREAM_ONLY) {
                drawableResId = R.drawable.stat_sys_network_traffic_up;
            } else {
                drawableResId = R.drawable.stat_sys_network_traffic_updown;
            }
        } else if (!mHideArrows && mMode == MODE_UPSTREAM_ONLY) {
            drawableResId = R.drawable.stat_sys_network_traffic_up;
        } else if (!mHideArrows && mMode == MODE_DOWNSTREAM_ONLY) {
            drawableResId = R.drawable.stat_sys_network_traffic_down;
        } else {
            drawableResId = 0;
        }
        drawable = drawableResId != 0 ? resources.getDrawable(drawableResId) : null;
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
