package com.android.systemui.statusbar.screen_gestures;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.internal.util.gesture.EdgeGesturePosition;

/**
 * Created by arasthel on 15/02/18.
 */

public class ScreenGesturesView extends View {

    public static final boolean DEBUG = false;

    public static final int SIGNIFICANT_MOVE = 10;

    private static final String TAG = "ScreenGesturesView";

    public static class GestureType {
        public static final int NONE = 0;
        public static final int HOME = 1;
        public static final int BACK = 1 << 1;
        public static final int RECENTS = 1 << 2;
    }

    private OnGestureCompletedListener onGestureCompletedListener;

    private int initialX = -1;
    private int initialY = -1;

    private int lastX = -1;
    private int lastY = -1;

    private int possibleGestures = GestureType.NONE;

    private Vibrator vibrator;

    private Handler handler = new Handler(Looper.getMainLooper());

    public ScreenGesturesView(Context context) {
        super(context);
    }

    public ScreenGesturesView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScreenGesturesView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ScreenGesturesView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setVisibility(View.GONE);

        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void startGesture(int initialX, int initialY, EdgeGesturePosition position) {
        if (DEBUG) Log.d(TAG, "startGesture: Gesture started");

        this.initialX = initialX;
        this.initialY = initialY;

        this.lastX = initialX;
        this.lastY = initialY;

        String backSettingsId = getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ?
                Settings.Secure.EDGE_GESTURES_BACK_EDGES :
                Settings.Secure.EDGE_GESTURES_LANDSCAPE_BACK_EDGES;
        int backGestureEdgesFlag = Settings.Secure.getIntForUser(getContext().getContentResolver(),
                backSettingsId,
                0,
                UserHandle.USER_CURRENT);;

        if ((position.FLAG & backGestureEdgesFlag) != 0) {
            possibleGestures = GestureType.BACK;
        } else if ((position.FLAG & EdgeGesturePosition.BOTTOM.FLAG) != 0) {
            possibleGestures = GestureType.HOME | GestureType.RECENTS;
        } else {
            if (onGestureCompletedListener != null) {
                onGestureCompletedListener.onGestureCompleted(GestureType.NONE);
            }
            return;
        }

        setVisibility(View.VISIBLE);
    }

    private int getFeedbackStrength() {
        try {
            return Settings.Secure.getInt(getContext().getContentResolver(), Settings.Secure.EDGE_GESTURES_FEEDBACK_DURATION);
        } catch (Settings.SettingNotFoundException exception) {
            return 100;
        }
    }

    private int getLongpressDuration() {
        try {
            return Settings.Secure.getInt(getContext().getContentResolver(), Settings.Secure.EDGE_GESTURES_LONG_PRESS_DURATION);
        } catch (Settings.SettingNotFoundException exception) {
            return 500;
        }
    }

    private void stopGesture(int posX, int posY) {
        if (DEBUG) Log.d(TAG, "stopGesture: Gesture stopped");

        stopLongPress();

        if (onGestureCompletedListener == null) return;

        if (DEBUG) Log.d(TAG, "stopGesture: Initial x: " + String.valueOf(initialX) + ", final x: " + String.valueOf(posX));
        if (DEBUG) Log.d(TAG, "stopGesture: Initial y: " + String.valueOf(initialY) + ", final y: " + String.valueOf(posY));

        final int threshold = 20;
        boolean canSendHome = (possibleGestures & GestureType.HOME) != 0;
        if (canSendHome && (posY - initialY < -threshold)) {
            if (DEBUG) Log.d(TAG, "stopGesture: Home");
            vibrator.vibrate(getFeedbackStrength());
            onGestureCompletedListener.onGestureCompleted(GestureType.HOME);
            return;
        }

        boolean canSendBack = (possibleGestures & GestureType.BACK) != 0;
        if (canSendBack && (Math.abs(posX - initialX) > threshold)) {
            if (DEBUG) Log.d(TAG, "stopGesture: Back");
            vibrator.vibrate(getFeedbackStrength());
            onGestureCompletedListener.onGestureCompleted(GestureType.BACK);
            return;
        }

        if (DEBUG) Log.d(TAG, "stopGesture: None");
        onGestureCompletedListener.onGestureCompleted(GestureType.NONE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (DEBUG) Log.d(TAG, "onTouchEvent: DOWN");
                return true;
            case MotionEvent.ACTION_MOVE:
                if (DEBUG) Log.d(TAG, "onTouchEvent: MOVE");
                int x = (int) event.getX();
                int y = (int) event.getY();
                if (Math.abs(x - lastX) > SIGNIFICANT_MOVE || Math.abs(y - lastY) > SIGNIFICANT_MOVE) {
                    stopLongPress();
                    startLongPress();
                }

                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
                if (DEBUG) Log.d(TAG, "onTouchEvent: UP");
                if (possibleGestures != GestureType.NONE) {
                    stopGesture((int) event.getX(), (int) event.getY());
                    setVisibility(View.GONE);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (DEBUG) Log.d(TAG, "onTouchEvent: DOWN");
                stopGesture((int) event.getX(), (int) event.getY());
                setVisibility(View.GONE);
                return true;
        }
        return false;
    }

    private void startLongPress() {
        if (DEBUG) Log.d(TAG, "startLongPress: scheduling long press");
        handler.postDelayed(longPressRunnable, getLongpressDuration());
    }

    private void stopLongPress() {
        if (DEBUG) Log.d(TAG, "stopLongPress: cancellling long press");
        handler.removeCallbacks(longPressRunnable);
    }

    private Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            boolean canSendRecents = (possibleGestures & GestureType.RECENTS) != 0;
            if (canSendRecents) {
                possibleGestures = GestureType.NONE;
                setVisibility(View.GONE);
                if (onGestureCompletedListener != null) {
                    onGestureCompletedListener.onGestureCompleted(GestureType.RECENTS);
                }
                vibrator.vibrate(getFeedbackStrength());
            }
        }
    };

    public OnGestureCompletedListener getOnGestureCompletedListener() {
        return onGestureCompletedListener;
    }

    public void setOnGestureCompletedListener(OnGestureCompletedListener onGestureCompletedListener) {
        this.onGestureCompletedListener = onGestureCompletedListener;
    }

    interface OnGestureCompletedListener {
        void onGestureCompleted(int gestureType);
    }
}
