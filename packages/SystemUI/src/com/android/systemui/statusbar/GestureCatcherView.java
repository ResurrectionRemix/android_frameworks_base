package com.android.systemui.statusbar;

import com.android.systemui.R;
import com.android.systemui.aokp.AokpSwipeRibbon;

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

public class GestureCatcherView extends LinearLayout{

    private Context mContext;
    private Resources res;
    private ImageView mDragButton;
    long mDowntime;
    int mTimeOut;
    private float mButtonWeight;
    private int mGestureHeight;
    private int mDragButtonOpacity;
    private boolean mPhoneMode;

    private int mTriggerThreshhold = 20;
    private float[] mDownPoint = new float[2];
    private boolean mSwapXY = false;
    private boolean mNavBarSwipeStarted = false;
    private int mScreenWidth, mScreenHeight;

    private BaseStatusBar mBar;
    private SettingsObserver mSettingsObserver;

    final static String TAG = "PopUpNav";

    public GestureCatcherView(Context context, AttributeSet attrs, BaseStatusBar sb) {
        super(context, attrs);

        mContext = context;
        mBar = sb;
        mDragButton = new ImageView(mContext);
        res = mContext.getResources();
        mGestureHeight = res.getDimensionPixelSize(R.dimen.drag_handle_height);
        updateLayout();
        Point size = new Point();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(size);
        mScreenHeight = size.x;
        mScreenWidth = size.y;

        mSettingsObserver = new SettingsObserver(new Handler());
        updateSettings();

        mDragButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    if (!mNavBarSwipeStarted) {
                        mDownPoint[0] = event.getX();
                        mDownPoint[1] = event.getY();
                        mNavBarSwipeStarted = true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL :
                    mNavBarSwipeStarted = false;
                    break;
                case MotionEvent.ACTION_MOVE :
                    if (mNavBarSwipeStarted) {
                        final int historySize = event.getHistorySize();
                        for (int k = 0; k < historySize + 1; k++) {
                            float x = k < historySize ? event.getHistoricalX(k) : event.getX();
                            float y = k < historySize ? event.getHistoricalY(k) : event.getY();
                            float distance = 0f;
                            distance = mSwapXY ? (mDownPoint[0] - x) : (mDownPoint[1] - y);
                            if (distance > mTriggerThreshhold) {
                                mNavBarSwipeStarted = false;
                                mBar.showBar(false);
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
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
                Intent toggleRibbon = new Intent(
                        AokpSwipeRibbon.RibbonReceiver.ACTION_TOGGLE_RIBBON);
                toggleRibbon.putExtra("action", "bottom");
                mContext.sendBroadcast(toggleRibbon);
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

    public void setSwapXY(boolean swap) {
        mSwapXY = swap;
        updateLayout();
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        WindowManager.LayoutParams lp  = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (mSwapXY ? Gravity.CENTER_VERTICAL | Gravity.RIGHT : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lp.setTitle("GesturePanel");
        return lp;
    }

    private void updateLayout () {
        LinearLayout.LayoutParams dragParams;
        float dragSize = 0;
        removeAllViews();
        if (mSwapXY && mPhoneMode) { // Landscape Mode **we should always multiply if the result would be te same as / easier for the alu
            dragSize = ((mScreenWidth) * (mButtonWeight*0.01f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button_land));
            dragParams = new LinearLayout.LayoutParams(mGestureHeight,(int) dragSize);
            setOrientation(VERTICAL);
        } else if (mSwapXY && !mPhoneMode) {
            dragSize = ((mScreenHeight) * (mButtonWeight*0.01f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
            dragParams = new LinearLayout.LayoutParams((int) dragSize, mGestureHeight);
            setOrientation(HORIZONTAL);
        } else {
            dragSize = ((mScreenWidth) * (mButtonWeight*0.01f)) / getResources().getDisplayMetrics().density;
            mDragButton.setImageDrawable(mContext.getResources().getDrawable(R.drawable.navbar_drag_button));
            dragParams = new LinearLayout.LayoutParams((int) dragSize, mGestureHeight);
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
                    Settings.System.DRAG_HANDLE_WEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DRAG_HANDLE_OPACITY), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mDragButtonOpacity = Settings.System.getInt(cr, Settings.System.DRAG_HANDLE_OPACITY, 50);
        mPhoneMode = Settings.System.getInt(cr,Settings.System.CURRENT_UI_MODE, 0) == 0;

        mButtonWeight = Settings.System.getInt(cr, Settings.System.DRAG_HANDLE_WEIGHT, 5);
        updateLayout();
    }
}
