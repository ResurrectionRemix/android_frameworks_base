package com.android.systemui.aokp;

import com.android.systemui.R;
import com.android.systemui.aokp.AokpSwipeRibbon;
import com.android.systemui.aokp.AppWindow;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class RibbonGestureCatcherView extends LinearLayout{

    private Context mContext;
    private Resources res;
    private ImageView mDragButton;
    long mDowntime;
    int mTimeOut, mLocation;
    private int mButtonWeight = 30;
    private int mButtonHeight = 0;
    private int mGestureHeight;
    private int mDragButtonOpacity;
    private boolean mRightSide, vib;
    private boolean mVibLock = false;

    private int mTriggerThreshhold = 20;
    private float[] mDownPoint = new float[2];
    private boolean mVerticalLayout = true;
    private boolean mRibbonSwipeStarted = false;
    private boolean mRibbonShortSwiped = false;
    private int mScreenWidth, mScreenHeight;
    private String mAction;

    private SettingsObserver mSettingsObserver;

    final static String TAG = "PopUpRibbon";

    public RibbonGestureCatcherView(Context context, AttributeSet attrs, String action) {
        super(context, attrs);

        mContext = context;
        mAction = action;
        mVerticalLayout = !mAction.equals("bottom");
        mRightSide = mAction.equals("right");
        mDragButton = new ImageView(mContext);
        res = mContext.getResources();
        mGestureHeight = res.getDimensionPixelSize(R.dimen.drag_handle_height);
        updateLayout();
        Point size = new Point();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(size);
        mScreenHeight = size.y;
        mScreenWidth = size.x;

        mSettingsObserver = new SettingsObserver(new Handler());
        updateSettings();

        mDragButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (vib && !mVibLock) {
                    mVibLock = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    if (!mRibbonSwipeStarted) {
                        mDownPoint[0] = event.getX();
                        mDownPoint[1] = event.getY();
                        mRibbonSwipeStarted = true;
                        mRibbonShortSwiped = false;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL :
                    mRibbonSwipeStarted = false;
                    mVibLock = false;
                    break;
                case MotionEvent.ACTION_MOVE :
                    if (mRibbonSwipeStarted) {
                        final int historySize = event.getHistorySize();
                        for (int k = 0; k < historySize + 1; k++) {
                            float x = k < historySize ? event.getHistoricalX(k) : event.getX();
                            float y = k < historySize ? event.getHistoricalY(k) : event.getY();
                            float distance = 0f;
                            if (mVerticalLayout) {
                                distance = mRightSide ? (mDownPoint[0] - x) : (x - mDownPoint[0]);
                            } else {
                                distance = mDownPoint[1] - y;
                            }
                            if (distance > mTriggerThreshhold && distance < mScreenWidth * 0.75f && !mRibbonShortSwiped) {
                                mRibbonShortSwiped = true;
                                Intent showRibbon = new Intent(
                                    AokpSwipeRibbon.RibbonReceiver.ACTION_SHOW_RIBBON);
                                showRibbon.putExtra("action", mAction);
                                Log.d(TAG, "Sending broadcast for" + mAction);
                                mContext.sendBroadcast(showRibbon);
                                mVibLock = false;
                            }
                            if (distance > mScreenWidth * 0.75f) {
                                Intent hideRibbon = new Intent(
                                    AokpSwipeRibbon.RibbonReceiver.ACTION_HIDE_RIBBON);
                                mContext.sendBroadcast(hideRibbon);
                                Intent showAppWindow = new Intent(
                                    AppWindow.WindowReceiver.ACTION_SHOW_APP_WINDOW);
                                mContext.sendBroadcast(showAppWindow);
                                mVibLock = false;
                                mRibbonSwipeStarted = false;
                                mRibbonShortSwiped = false;
                                return true;
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mRibbonSwipeStarted = false;
                    mRibbonShortSwiped = false;
                    mVibLock = false;
                    break;
                }
                return false;
            }
        });

        mDragButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                Log.d(TAG, "Long pressed sending broadcast");
           /*     Intent showRibbon = new Intent(
                        AokpSwipeRibbon.RibbonReceiver.ACTION_SHOW_RIBBON);
                showRibbon.putExtra("action", mAction);
                mContext.sendBroadcast(showRibbon); */
                                Intent showAppWindow = new Intent(
                                    AppWindow.WindowReceiver.ACTION_SHOW_APP_WINDOW);
                                mContext.sendBroadcast(showAppWindow);
                return true;
                }
            });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        super.onDetachedFromWindow();
    }

    private int getGravity() {
        int gravity = 0;
        if (mAction.equals("bottom")) {
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        } else if (mAction.equals("left")) {
            switch (mLocation)  {
            case 0:
                gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                break;
            case 1:
                gravity = Gravity.TOP | Gravity.LEFT;
                break;
            case 2:
                gravity = Gravity.BOTTOM | Gravity.LEFT;
                break;
            }
        } else {
            switch (mLocation)  {
            case 0:
                gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
                break;
            case 1:
                gravity = Gravity.TOP | Gravity.RIGHT;
                break;
            case 2:
                gravity = Gravity.BOTTOM | Gravity.RIGHT;
                break;
            }
        }
        return gravity;
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        WindowManager.LayoutParams lp  = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = getGravity();
        lp.setTitle("RibbonGesturePanel" + mAction);
        return lp;
    }

    private void updateLayout() {
        LinearLayout.LayoutParams dragParams;
        float dragSize = 0;
        float dragHeight = mGestureHeight + (mGestureHeight * (mButtonHeight * 0.01f));
        removeAllViews();
        if (mVerticalLayout) {
            dragSize = ((mScreenHeight) * (mButtonWeight*0.02f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(mRightSide
                ? R.drawable.navbar_drag_button_land : R.drawable.navbar_drag_button_left));
            dragParams = new LinearLayout.LayoutParams((int) dragHeight, (int) dragSize);
            setOrientation(VERTICAL);
        } else {
            dragSize = ((mScreenWidth) * (mButtonWeight*0.02f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
            dragParams = new LinearLayout.LayoutParams((int) dragSize, (int) dragHeight);
            setOrientation(HORIZONTAL);
        }
        mDragButton.setScaleType(ImageView.ScaleType.FIT_XY);
        float opacity = (255f * (mDragButtonOpacity/ 100f));
        mDragButton.setImageAlpha((int) opacity);
        addView(mDragButton,dragParams);
        invalidate();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_WEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_OPACITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_VIBRATE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_LOCATION), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mDragButtonOpacity = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_OPACITY, 0);
        mButtonWeight = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_WEIGHT, 30);
        mButtonHeight = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_HEIGHT, 0);
        vib = Settings.System.getBoolean(cr, Settings.System.SWIPE_RIBBON_VIBRATE, false);
        mLocation = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_LOCATION, 0);
        updateLayout();
    }
}
