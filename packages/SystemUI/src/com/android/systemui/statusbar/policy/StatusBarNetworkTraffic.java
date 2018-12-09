package com.android.systemui.statusbar.policy;

import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;

/** @hide */
public class StatusBarNetworkTraffic extends NetworkTraffic implements StatusIconDisplayable {

    public static final String SLOT = "networktraffic";

    private int mVisibleState = -1;
    private boolean mSystemIconVisible = true;

    public StatusBarNetworkTraffic(Context context) {
        super(context);
    }

    public StatusBarNetworkTraffic(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusBarNetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected String getSystemSettingKey() {
        return Settings.System.NETWORK_TRAFFIC_STATE;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public void setStaticDrawableColor(int color) {
        mTintColor = color;
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public void setDecorColor(int color) {
    }


    @Override
    public String getSlot() {
        return SLOT;
    }

    @Override
    public boolean isIconVisible() {
        return mIsEnabled;
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @Override
    public void setVisibleState(int state) {
        if (state == mVisibleState) {
            return;
        }
        mVisibleState = state;

        switch (state) {
            case STATE_ICON:
                mSystemIconVisible = true;
                break;
            case STATE_DOT:
            case STATE_HIDDEN:
            default:
                mSystemIconVisible = false;
                break;
        }
        updateVisibility();
    }

    @Override
    protected void updateVisibility() {
        if (mIsEnabled && mTrafficVisible && mSystemIconVisible) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

}
