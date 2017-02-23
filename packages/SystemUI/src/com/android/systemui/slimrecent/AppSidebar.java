/*
 * Copyright (C) 2013 The ChameleonOS Project
 * Copyright (C) 2015-2017 Android Ice Cold Project
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

package com.android.systemui.slimrecent;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.android.internal.util.rr.Action;
import com.android.internal.util.rr.ActionConfig;
import com.android.internal.util.rr.ActionConstants;
import com.android.internal.util.rr.ActionHelper;
import com.android.systemui.R;

import java.util.ArrayList;

public class AppSidebar extends FrameLayout {
    private static final String TAG = "SlimRecentAppSidebar";

    private static final LinearLayout.LayoutParams SCROLLVIEW_LAYOUT_PARAMS =
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.0f
            );

    private static LinearLayout.LayoutParams ITEM_LAYOUT_PARAMS =
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f
            );

    public static float DEFAULT_SCALE_FACTOR = 1.0f;
    private static final float SWIPE_THRESHOLD_FACTOR = 0.5f;
    private static final float SWIPE_ANIMATION_JUMP_FACTOR = 0.2f;
    private static final int SWIPE_ANIMATION_DURATION = 300;

    private LinearLayout mAppContainer;
    private SnappingScrollView mScrollView;
    private Rect mScaledIconBounds;
    private int mIconSize;
    private int mScaledIconSize;
    private int mItemTextSize;
    private int mScaledItemTextSize;
    private int mBackgroundColor;
    private int mLabelColor;
    private boolean mHideTextLabels = false;
    private float mSwipeThreshold;
    private boolean mSwipedLeft;
    private String mSwipeAction = null;
    private Toast mSwipeToast;

    private float mScaleFactor = DEFAULT_SCALE_FACTOR;

    private Context mContext;
    private SettingsObserver mSettingsObserver;

    private RecentController mSlimRecent;

    public AppSidebar(Context context) {
        this(context, null);
    }

    public AppSidebar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppSidebar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mItemSwipeGestureDetector = new GestureDetector(mContext, mItemSwipeListener);
        Resources resources = context.getResources();
        mItemTextSize = resources
                .getDimensionPixelSize(R.dimen.recent_app_sidebar_item_title_text_size);
        mIconSize = resources
                .getDimensionPixelSize(R.dimen.recent_app_sidebar_item_size) - mItemTextSize;
        setScaledSizes();
        setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                setupAppContainer();
            }
        });
    }

    private void setScaledSizes() {
        mScaledItemTextSize = Math.round(mItemTextSize * mScaleFactor);
        mScaledIconSize = Math.round(mIconSize * mScaleFactor);
        mScaledIconBounds = new Rect(0, 0, mScaledIconSize, mScaledIconSize);
        mSwipeThreshold = mScaledIconSize * SWIPE_THRESHOLD_FACTOR;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setupAppContainer();
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupAppContainer();
    }

    public void setSlimRecent(RecentController slimRecent){
        mSlimRecent = slimRecent;
    }

    private void setupAppContainer() {
        post(new Runnable() {
            public void run() {
                setupSidebarContent();
            }
        });
    }


    private int getBackgroundColor(){
        return mBackgroundColor == 0x00ffffff ?
                mContext.getResources().getColor(R.color.recent_background) : mBackgroundColor;
    }
    private int getLabelColor(){
        return mLabelColor == 0x00ffffff ?
                mContext.getResources().getColor(R.color.recents_task_bar_light_text_color) :
                mLabelColor;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BACK && event.getAction() == ACTION_DOWN &&
                mSlimRecent != null)
            mSlimRecent.onExit();
        return super.dispatchKeyEventPreIme(event);
    }

    private OnClickListener mItemClickedListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Action.processAction(mContext, ((ActionConfig)view.getTag()).getClickAction(), false);
            cancelPendingSwipeAction();
            hideSlimRecent();
        }
    };

    private OnLongClickListener mItemLongClickedListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            String action = ((ActionConfig)view.getTag()).getLongpressAction();
            if (!ActionConstants.ACTION_NULL.equals(action)) {
                Action.processAction(mContext, action, false);
                cancelPendingSwipeAction();
                hideSlimRecent();
                return true;
            }
            return false;
        }
    };

    private OnTouchListener mItemTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (mItemSwipeGestureDetector.onTouchEvent(event)) {
                handleSwipe(view);
                return true;
            }
            return false;
        }
    };

    private GestureDetector mItemSwipeGestureDetector;

    private GestureDetector.SimpleOnGestureListener mItemSwipeListener =
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                            float velocityY) {
                        float diffY = Math.abs(e1.getY() - e2.getY());
                        float diffX = Math.abs(e1.getX() - e2.getX());
                        if (diffX > diffY && diffX >= mSwipeThreshold) {
                            mSwipedLeft = e1.getX() > e2.getX();
                            return true;
                        } else {
                            return false;
                        }
                    }
               };

    private void handleSwipe(View view) {
        // Swipe animation
        int jumpX = (int) (mScaledIconSize * SWIPE_ANIMATION_JUMP_FACTOR);
        if (mSwipedLeft) {
            jumpX *= -1;
        }
        AnimatorSet animatorSet =  new AnimatorSet();
        animatorSet.play(ObjectAnimator.ofFloat(view, "translationX", 0, jumpX, 0));
        animatorSet.setDuration(SWIPE_ANIMATION_DURATION);
        animatorSet.start();
        // Swipe action
        ActionConfig config = (ActionConfig)view.getTag();
        String clickAction = config.getClickAction();
        String longPressAction = config.getLongpressAction();
        if (mSwipeAction != null) {
            if (mSwipeAction.equals(clickAction)) {
                if (!ActionConstants.ACTION_NULL.equals(longPressAction)) {
                    mSwipeAction = longPressAction;
                } else {
                    mSwipeAction = null;
                }
            } else if (mSwipeAction.equals(longPressAction)) {
                mSwipeAction = null;
            } else {
                mSwipeAction = clickAction;
            }
        } else {
            mSwipeAction = clickAction;
        }
        // Toast notification
        if (mSwipeToast != null) {
            mSwipeToast.cancel();
        }
        String toastText;
        if (mSwipeAction != null && mSwipeAction.equals(clickAction)) {
            toastText = mContext.getString(R.string.toast_recents_queue_app,
                            config.getClickActionDescription());
        } else if (mSwipeAction != null && mSwipeAction.equals(longPressAction)) {
            toastText = mContext.getString(R.string.toast_recents_queue_app,
                            config.getLongpressActionDescription());
        } else {
            toastText = mContext.getString(R.string.toast_recents_cancel_queued_app);
        }
        mSwipeToast = Toast.makeText(mContext, toastText , Toast.LENGTH_SHORT);
        mSwipeToast.show();
    }

    public void launchPendingSwipeAction() {
        if (mSwipeAction != null) {
            Action.processAction(mContext, mSwipeAction, false);
            cancelPendingSwipeAction();
        }
    }

    public void cancelPendingSwipeAction() {
        mSwipeAction = null;
        if (mSwipeToast != null) {
            mSwipeToast.cancel();
        }
    }

    class SnappingScrollView extends ScrollView {

        private boolean mSnapTrigger = false;
        private float mActionDownY = -1f;

        public SnappingScrollView(Context context) {
            super(context);
        }

        Runnable mSnapRunnable = new Runnable(){
            @Override
            public void run() {
                int mSelectedItem = ((getScrollY() + (ITEM_LAYOUT_PARAMS.height / 2)) /
                        ITEM_LAYOUT_PARAMS.height);
                int scrollTo = mSelectedItem * ITEM_LAYOUT_PARAMS.height;
                smoothScrollTo(0, scrollTo);
                mSnapTrigger = false;
            }
        };

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (Math.abs(oldt - t) <= 1 && mSnapTrigger) {
                removeCallbacks(mSnapRunnable);
                postDelayed(mSnapRunnable, 100);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getAction();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mSnapTrigger = true;
                // Allow swipe actions
                if (mActionDownY >= 0f) {
                    float diffY = Math.abs(ev.getY() - mActionDownY);
                    mActionDownY = -1f;
                    if (diffY < mScaledIconSize) {
                        return false;
                    }
                }
            } else if (action == MotionEvent.ACTION_DOWN) {
                mSnapTrigger = false;
                mActionDownY = ev.getY();
            }
            return super.onTouchEvent(ev);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_DISABLE_LABELS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_BG_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_TEXT_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RECENT_APP_SIDEBAR_SCALE_FACTOR), false, this);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();

            boolean requireNewSetup = false;

            boolean hideLabels = Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_APP_SIDEBAR_DISABLE_LABELS, 0,
                    UserHandle.USER_CURRENT) == 1;

            int labelColor = Settings.System.getIntForUser(resolver,
                    Settings.System.RECENT_APP_SIDEBAR_TEXT_COLOR, 0x00ffffff,
                    UserHandle.USER_CURRENT);

            if (hideLabels != mHideTextLabels || labelColor != mLabelColor) {
                mHideTextLabels = hideLabels;
                mLabelColor = labelColor;
                if (mScrollView != null) {
                    requireNewSetup = true;
                }
            }

            int backgroundColor = Settings.System.getIntForUser(resolver,
                    Settings.System.RECENT_APP_SIDEBAR_BG_COLOR, 0x00ffffff,
                    UserHandle.USER_CURRENT);

            if (mBackgroundColor != backgroundColor) {
                mBackgroundColor = backgroundColor;
                setBackgroundColor(getBackgroundColor());
            }

            float scaleFactor = Settings.System.getIntForUser(
                    resolver, Settings.System.RECENT_APP_SIDEBAR_SCALE_FACTOR, 100,
                    UserHandle.USER_CURRENT) / 100.0f;
            if (scaleFactor != mScaleFactor) {
                mScaleFactor = scaleFactor;
                setScaledSizes();
                requireNewSetup = true;
            }
            if (requireNewSetup) {
                setupAppContainer();
            }
        }
    }

    private void setupSidebarContent(){
        // Load content
        ArrayList<ActionConfig> contentConfig = ActionHelper.getRecentAppSidebarConfig(mContext);
        ArrayList<View> mContainerItems = new ArrayList<View>();

        for (ActionConfig config: contentConfig){
            mContainerItems.add(createAppItem(config));
        }

        // Layout items
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        int windowHeight = r.bottom - r.top;;
        int statusBarHeight = r.top;
        if (mScrollView != null)
            removeView(mScrollView);

        // create a LinearLayout to hold our items
        if (mAppContainer == null) {
            mAppContainer = new LinearLayout(mContext);
            mAppContainer.setOrientation(LinearLayout.VERTICAL);
            mAppContainer.setGravity(Gravity.CENTER);
        }
        mAppContainer.removeAllViews();

        // set the layout height based on the item height we would like and the
        // number of items that would fit at on screen at once given the height
        // of the app sidebar
        int padding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.recent_app_sidebar_item_padding);
        int desiredItemSize = mScaledIconSize + padding * 2;
        if (!mHideTextLabels) {
            // add size twice to make sure that the text won't get cut
            // (e.g. "y" being displayed as "v")
            desiredItemSize += mScaledItemTextSize * 2;
        }
        int numItems = (int)Math.floor(windowHeight / desiredItemSize);
        ITEM_LAYOUT_PARAMS.height = windowHeight / numItems;
        ITEM_LAYOUT_PARAMS.width = desiredItemSize;
        LinearLayout.LayoutParams firstItemLayoutParams = new LinearLayout.LayoutParams(
                ITEM_LAYOUT_PARAMS.width, ITEM_LAYOUT_PARAMS.height);
        firstItemLayoutParams.topMargin += statusBarHeight;

        boolean firstIcon = true;
        for (View icon : mContainerItems) {
            icon.setOnClickListener(mItemClickedListener);
            icon.setOnLongClickListener(mItemLongClickedListener);
            icon.setOnTouchListener(mItemTouchListener);
            if (mHideTextLabels) {
                ((TextView)icon).setTextSize(0);
            }
            icon.setClickable(true);
            icon.setPadding(0, padding, 0, padding);
            if (firstIcon) {
                // First icon should not hide behind the status bar
                mAppContainer.addView(icon, firstItemLayoutParams);
                firstIcon = false;
            } else {
                mAppContainer.addView(icon, ITEM_LAYOUT_PARAMS);
            }
        }

        // we need our horizontal scroll view to wrap the linear layout
        if (mScrollView == null) {
            mScrollView = new SnappingScrollView(mContext);
            // make the fading edge the size of a button (makes it more noticible that we can scroll
            mScrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        mScrollView.removeAllViews();
        mScrollView.addView(mAppContainer, SCROLLVIEW_LAYOUT_PARAMS);
        addView(mScrollView, SCROLLVIEW_LAYOUT_PARAMS);
        mAppContainer.setFocusable(true);
    }

    private TextView createAppItem(ActionConfig config) {
        TextView tv = new TextView(mContext);
        Drawable icon = ActionHelper.getActionIconImage(mContext, config.getClickAction(),
                config.getIcon());
        if (icon != null) {
            icon.setBounds(mScaledIconBounds);
            tv.setCompoundDrawables(null, icon, null, null);
        }
        tv.setTag(config);
        tv.setText(config.getClickActionDescription());
        tv.setSingleLine(true);
        tv.setEllipsize(TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mScaledItemTextSize);
        tv.setTextColor(getLabelColor());

        return tv;
    }

    private void hideSlimRecent(){
        if (mSlimRecent != null)
            mSlimRecent.hideRecents(false);
    }
}
