package com.android.systemui.statusbar;

import com.android.systemui.R;
import com.android.systemui.WidgetSelectActivity;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WidgetView extends LinearLayout {

    private Context mContext;
    private Handler mHandler;
    public FrameLayout mPopupView;
    public WindowManager mWindowManager;
    int originalHeight = 0;
    TextView mWidgetLabel;
    ViewPager mWidgetPager;
    View widgetView;
    WidgetPagerAdapter mAdapter;
    int widgetIds[];
    float mFirstMoveY;
    int mCurrentWidgetPage = 0;
    long mDowntime;
    boolean mMoving = false;
    boolean showing = false;
    boolean animating = false;

    final static String TAG = "Widget";

    public WidgetView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(WidgetReceiver.ACTION_ALLOCATE_ID);
        filter.addAction(WidgetReceiver.ACTION_DEALLOCATE_ID);
        filter.addAction(WidgetReceiver.ACTION_TOGGLE_WIDGETS);
        filter.addAction(WidgetReceiver.ACTION_DELETE_WIDGETS);
        mContext.registerReceiver(new WidgetReceiver(), filter);
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    public void toggleWidgetView() {
        if (showing) {
            if (mPopupView != null && !animating) {
                animating = true;
                PlayOutAnim();
                mHandler.postDelayed(removePopup, 490);
            }
        } else {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM;
            params.setTitle("Widgets");
            if (mWindowManager != null && mAdapter !=null){
                showing = true;
                mWindowManager.addView(mPopupView, params);
                mAdapter.onShow();
                PlayInAnim();
            } else {
                Log.e(TAG,"WTF - ToggleWidget when no pager or window manager exist?");
            }
        }
    }

    private Runnable removePopup = new Runnable() {
        public void run() {
            mAdapter.onHide();
            mWindowManager.removeView(mPopupView);
            showing = false;
            animating = false;
        }
    };

    public Animation PlayInAnim() {
        if (widgetView != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.slide_in_up);
            animation.setStartOffset(0);
            widgetView.startAnimation(animation);
            return animation;
        }
        return null;
    }

    public Animation PlayOutAnim() {
        if (widgetView != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.slide_out_down);
            animation.setStartOffset(0);
            widgetView.startAnimation(animation);
            return animation;
        }
        return null;
    }

    public void createWidgetView() {
        mPopupView = new FrameLayout(mContext);
        widgetView = View.inflate(mContext, R.layout.navigation_bar_expanded, null);
        mPopupView.addView(widgetView);
        mWidgetLabel = (TextView) mPopupView.findViewById(R.id.widgetlabel);
        mWidgetPager = (ViewPager) widgetView.findViewById(R.id.pager);
        mWidgetPager.setAdapter(mAdapter = new WidgetPagerAdapter(mContext, widgetIds));
        mWidgetPager.setOnPageChangeListener(mNewPageListener);

        int dp = mAdapter.getHeight(mWidgetPager.getCurrentItem());
        float px = dp * getResources().getDisplayMetrics().density;
        mWidgetPager.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,(int) px));

        mPopupView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    toggleWidgetView();
                    return true;
                }
                return false;
            }
        });

        final Runnable SetMoving = new Runnable () {
            public void run() {
                mMoving = true;
                mDowntime = System.currentTimeMillis();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        };

        mWidgetLabel.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mHandler.postDelayed(SetMoving, ViewConfiguration.getLongPressTimeout());
                    mFirstMoveY = event.getY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (mMoving) {
                        float diff = event.getY() - mFirstMoveY;
                        int oldheight = mWidgetPager.getHeight();
                        int newheight = oldheight + (int) - diff; // this is pixels
                        if (System.currentTimeMillis() - mDowntime > 150) { // slow down the move/updates
                            mWidgetPager.setLayoutParams(
                                    new LayoutParams(LayoutParams.MATCH_PARENT, newheight));
                            newheight = (int) (newheight / getResources().getDisplayMetrics().density);
                            mAdapter.setSavedHeight(mCurrentWidgetPage, newheight);
                            //mFirstMoveY = event.getY(); // reset the diff
                            mDowntime = System.currentTimeMillis();
                        }
                        return true;
                    } else { // we are moving without waiting for longpress
                        if (Math.abs(mFirstMoveY - event.getY()) > 20) {
                            // allow a little slop in the movement before cancelling longpress
                            mHandler.removeCallbacks(SetMoving);
                        }
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    mMoving = false;
                    return true;
                }
                return false;
            }
        });

    }

    public OnPageChangeListener mNewPageListener = new OnPageChangeListener() {

        @Override
        public void onPageSelected(int page) {
            int dp = mAdapter.getHeight(page);
            mCurrentWidgetPage = page;
            float px = dp * getResources().getDisplayMetrics().density;
            mWidgetPager.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, (int) px));
            if (mWidgetLabel != null) {
                mWidgetLabel.setText(mAdapter.getLabel(page));
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {

        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_WIDGETS),
                false,
                this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        String settingWidgets = Settings.System.getString(resolver,
                Settings.System.NAVIGATION_BAR_WIDGETS);
        if (settingWidgets != null && settingWidgets.length() > 0) {
            String[] split = settingWidgets.split("\\|");
            widgetIds = new int[split.length];
            for (int i = 0; i < widgetIds.length; i++) {
                widgetIds[i] = Integer.parseInt(split[i]);
            }
        }
        createWidgetView();
    }

    public class WidgetReceiver extends BroadcastReceiver {

        public static final String ACTION_ALLOCATE_ID = "com.android.systemui.ACTION_ALLOCATE_ID";
        public static final String ACTION_DEALLOCATE_ID = "com.android.systemui.ACTION_DEALLOCATE_ID";
        public static final String ACTION_TOGGLE_WIDGETS = "com.android.systemui.ACTION_TOGGLE_WIDGETS";
        public static final String ACTION_DELETE_WIDGETS = "com.android.systemui.ACTION_DELETE_WIDGETS";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_ALLOCATE_ID.equals(action)) {
                int appWidgetId = mAdapter.mAppWidgetHost.allocateAppWidgetId();

                Intent select = new Intent(context, WidgetSelectActivity.class);
                select.putExtra("selected_widget_id", appWidgetId);
                select.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(select);

            } else if (ACTION_DEALLOCATE_ID.equals(action)) {
                int appWidgetId =
                        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                if (appWidgetId != -1) {
                    mAdapter.mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    SharedPreferences prefs = mContext.getSharedPreferences("widget_adapter",
                            Context.MODE_WORLD_WRITEABLE);
                    prefs.edit().remove("widget_id_" + appWidgetId);
                }
            } else if (ACTION_TOGGLE_WIDGETS.equals(action)) {
                toggleWidgetView();
            } else if (ACTION_DELETE_WIDGETS.equals(action)) {
                SharedPreferences prefs = mContext.getSharedPreferences("widget_adapter",
                        Context.MODE_WORLD_WRITEABLE);
                int widgetqty = (widgetIds != null) ? widgetIds.length : 0;
                for (int i = 0; i < widgetqty; i++) {
                    prefs.edit().remove("widget_id_" + widgetIds[i]);
                    mAdapter.mAppWidgetHost.deleteAppWidgetId(widgetIds[i]);
                }
                mAdapter.mAppWidgetHost.deleteHost();
            }
        }
    }

}
