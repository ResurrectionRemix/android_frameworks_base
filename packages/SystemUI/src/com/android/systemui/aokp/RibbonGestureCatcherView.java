package com.android.systemui.aokp;

import com.android.systemui.R;
import com.android.systemui.aokp.AokpSwipeRibbon;
import com.android.systemui.aokp.AppWindow;
import com.android.systemui.aokp.AwesomeAction;

import static com.android.internal.util.aokp.AwesomeConstants.*;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class RibbonGestureCatcherView extends LinearLayout{

    private Context mContext;
    private Resources res;
    private ImageView mDragButton;
    long mDowntime;
    int mTimeOut, mLocation, ribbonNumber;
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
    private String mAction, mLongSwipeAction, mLongPressAction;
    private Animation mAnimationIn;
    private Animation mAnimationOut;

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
        mGestureHeight = res.getDimensionPixelSize(R.dimen.ribbon_drag_handle_height);
        updateLayout();
        mAnimationIn = PlayInAnim();
        mAnimationOut = PlayOutAnim();
        ribbonNumber = getRibbonNumber();
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
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    if (!mRibbonSwipeStarted) {
                        if (vib && !mVibLock) {
                            mVibLock = true;
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        }
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
                                mContext.sendBroadcast(showRibbon);
                                mVibLock = false;
                            }
                            if (distance > mScreenWidth * 0.75f) {
                                Intent hideRibbon = new Intent(
                                    AokpSwipeRibbon.RibbonReceiver.ACTION_HIDE_RIBBON);
                                mContext.sendBroadcast(hideRibbon);
                                AwesomeAction.launchAction(mContext, mLongSwipeAction);
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
                AwesomeAction.launchAction(mContext, mLongPressAction);
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

    private int getRibbonNumber() {
        if (mAction.equals("left")) {
            return 0;
        } else if (mAction.equals("right")) {
            return 1;
        } else if (mAction.equals("bottom")) {
            return 2;
        }
        return 0;
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

    public boolean setViewVisibility(boolean visibleIME) {
        if (visibleIME) {
            mDragButton.startAnimation(mAnimationOut);
        } else {
            mDragButton.setVisibility(View.VISIBLE);
            mDragButton.startAnimation(mAnimationIn);
        }
        return false;
    }

    public Animation PlayOutAnim() {
        if (mDragButton != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.slow_fade_out);
            animation.setStartOffset(0);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mDragButton.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            return animation;
        }
        return null;
    }

    public Animation PlayInAnim() {
        if (mDragButton != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.slow_fade_in);
            animation.setStartOffset(0);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            return animation;
        }
        return null;
    }

    private void updateLayout() {
        LinearLayout.LayoutParams dragParams;
        float dragSize = 0;
        float dragHeight = (mGestureHeight * (mButtonHeight * 0.01f));
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
                    Settings.System.RIBBON_DRAG_HANDLE_WEIGHT[ribbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_OPACITY[ribbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_HEIGHT[ribbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_VIBRATE[ribbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_LOCATION[ribbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_LONG_SWIPE[ribbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_LONG_PRESS[ribbonNumber]), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mDragButtonOpacity = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_OPACITY[ribbonNumber], 0);
        mButtonWeight = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_WEIGHT[ribbonNumber], 30);
        mButtonHeight = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_HEIGHT[ribbonNumber], 50);
        mLongSwipeAction = Settings.System.getString(cr, Settings.System.RIBBON_LONG_SWIPE[ribbonNumber]);
        if (TextUtils.isEmpty(mLongSwipeAction)) {
             mLongSwipeAction = AwesomeConstant.ACTION_APP_WINDOW.value();
             Settings.System.putString(cr, Settings.System.RIBBON_LONG_SWIPE[ribbonNumber], AwesomeConstant.ACTION_APP_WINDOW.value());
        }
        mLongPressAction = Settings.System.getString(cr, Settings.System.RIBBON_LONG_PRESS[ribbonNumber]);
        if (TextUtils.isEmpty(mLongPressAction)) {
             mLongPressAction = AwesomeConstant.ACTION_APP_WINDOW.value();
             Settings.System.putString(cr, Settings.System.RIBBON_LONG_PRESS[ribbonNumber], AwesomeConstant.ACTION_APP_WINDOW.value());
        }
        vib = Settings.System.getBoolean(cr, Settings.System.SWIPE_RIBBON_VIBRATE[ribbonNumber], false);
        mLocation = Settings.System.getInt(cr, Settings.System.RIBBON_DRAG_HANDLE_LOCATION[ribbonNumber], 0);
        updateLayout();
    }
}
