
package com.android.systemui.statusbar.toggles;

import android.content.res.Resources;
import android.view.View;

import com.android.systemui.R;

public abstract class StatefulToggle extends BaseToggle {

    public enum State {
        ENABLED,
        DISABLED,
        ENABLING,
        DISABLING;
    }

    private State mState = State.DISABLED;

    protected final void updateCurrentState(final State state) {
        mState = state;
        scheduleViewUpdate();
    }

    @Override
    public final void onClick(View v) {
        State newState = null;

        switch (mState) {
            case DISABLING:
            case ENABLING:
                return;
            case DISABLED:
                newState = State.ENABLING;
                doEnable();
                break;
            case ENABLED:
                newState = State.DISABLING;
                doDisable();
                break;
        }
        updateCurrentState(newState);
        collapseShadePref();
    }

    public State getState() {
        return mState;
    }

    protected final void setEnabledState(boolean s) {
        updateCurrentState(s ? State.ENABLED : State.DISABLED);
    }

    protected abstract void doEnable();

    protected abstract void doDisable();

    @Override
    protected void updateView() {
        super.updateView();
        Resources r = mContext.getResources();

        boolean tweenState = mState == State.DISABLING || mState == State.ENABLING;
        if (mLabel != null) {
            mLabel.setEnabled(!tweenState);
            mLabel.setTextColor(r.getColor(tweenState
                    ? R.color.toggle_text_changing_state_color
                    : R.color.toggle_text));
        }
        if (mIcon != null) {
            mIcon.setColorFilter(null);
            if (tweenState) {
                mIcon.setColorFilter(r.getColor(
                        R.color.toggle_image_changing_state_overlay));
            }
        }

    }

}
