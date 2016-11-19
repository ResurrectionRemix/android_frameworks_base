/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.drawable.BitmapDrawable;
import android.app.Fragment;
import android.content.res.Configuration;
import android.database.ContentObserver
import android.graphics.Rect;
import android.graphics.*;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.animation.*;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout.LayoutParams;
import android.widget.HorizontalScrollView;

import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.R.id;
import com.android.systemui.plugins.qs.QS;
import com.android.systemui.qs.customize.QSCustomizer;
import com.android.systemui.statusbar.*;
import com.android.systemui.statusbar.phone.NotificationsQuickSettingsContainer;
import com.android.systemui.statusbar.stack.StackStateAnimator;

import android.provider.Settings;

public class QSFragment extends Fragment implements QS {
    private static final String TAG = "QS";
    private static final boolean DEBUG = false;
    private static final String EXTRA_EXPANDED = "expanded";
    private static final String EXTRA_LISTENING = "listening";

    private final Rect mQsBounds = new Rect();
    private boolean mQsExpanded;
    private boolean mHeaderAnimating;
    public static boolean mKeyguardShowing;
    private boolean mStackScrollerOverscrolling;

    private long mDelay;

    private QSAnimator mQSAnimator;
    private HeightListener mPanelView;
    protected QuickStatusBarHeader mHeader;
    private QSCustomizer mQSCustomizer;
    protected QSPanel mQSPanel;
    private QSDetail mQSDetail;
    private boolean mListening;
    public static QSContainerImpl mContainer;
    private int mLayoutDirection;
    private QSFooter mFooter;

    private HorizontalScrollView mQuickQsPanelScroller;

    // omni additions
    private boolean mSecureExpandDisabled;

    
     public static boolean mBlurredStatusBarExpandedEnabled;
     public static QsFragment mNotificationPanelView;
 
     private static int mBlurScale;
     private static int mBlurRadius;
     private static BlurUtils mBlurUtils;
     private static FrameLayout mBlurredView;
     private static ColorFilter mColorFilter;
     private static int mBlurDarkColorFilter;
     private static int mBlurMixedColorFilter;
     private static int mBlurLightColorFilter;
     private static int mTranslucencyPercentage;
     private static AlphaAnimation mAlphaAnimation;
     private static boolean mTranslucentQuickSettings;
     private Handler mHandler = new Handler();
     private SettingsObserver mSettingsObserver;
     
     private static Animation.AnimationListener mAnimationListener = new Animation.AnimationListener() {
 
         @Override
         public void onAnimationStart(Animation anim) {
             mBlurredView.setVisibility(View.VISIBLE);
         }
 
         @Override
         public void onAnimationEnd(Animation anim) {}
 
         @Override
         public void onAnimationRepeat(Animation anim) {}
 
     };

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        inflater =inflater.cloneInContext(new ContextThemeWrapper(getContext(), R.style.qs_theme));
        return inflater.inflate(R.layout.qs_panel, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceStatef) {
        super.onViewCreated(view, savedInstanceState);
        mQSPanel = view.findViewById(R.id.quick_settings_panel);
        mQSDetail = view.findViewById(R.id.qs_detail);
        mHeader = view.findViewById(R.id.header);
        mFooter = view.findViewById(R.id.qs_footer);
        mContainer = view.findViewById(id.quick_settings_container);

        mQSDetail.setQsPanel(mQSPanel, mHeader, (View) mFooter);
        mQuickQsPanelScroller =
                (HorizontalScrollView) mHeader.findViewById(R.id.quick_qs_panel_scroll);
        mQSAnimator = new QSAnimator(this,
                mHeader.findViewById(R.id.quick_qs_panel),  mQSPanel,mQuickQsPanelScroller);

        mQSCustomizer = view.findViewById(R.id.qs_customize);
        mQSCustomizer.setQs(this);
        if (savedInstanceState != null) {
            setExpanded(savedInstanceState.getBoolean(EXTRA_EXPANDED));
            setListening(savedInstanceState.getBoolean(EXTRA_LISTENING));
            int[] loc = new int[2];
            View edit = view.findViewById(android.R.id.edit);
            edit.getLocationInWindow(loc);
            int x = loc[0] + edit.getWidth() / 2;
            int y = loc[1] + edit.getHeight() / 2;
            mQSCustomizer.setEditLocation(x, y);
            mQSCustomizer.restoreInstanceState(savedInstanceState);
        }
        mSettingsObserver = new SettingsObserver(mHandler);
        inirBlurprefs();
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
    public void onDestroy() {
        super.onDestroy();
        if (mListening) {
            setListening(false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_EXPANDED, mQsExpanded);
        outState.putBoolean(EXTRA_LISTENING, mListening);
        mQSCustomizer.saveInstanceState(outState);
    }

    @VisibleForTesting
    boolean isListening() {
        return mListening;
    }

    @VisibleForTesting
    boolean isExpanded() {
        return mQsExpanded;
    }

    @Override
    public View getHeader() {
        return mHeader;
    }

    public QuickStatusBarHeader getQuickStatusBarHeader() {
        return mHeader;
    }

    @Override
    public void setHasNotifications(boolean hasNotifications) {
    }

    @Override
    public void setPanelView(HeightListener panelView) {
        mPanelView = panelView;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.getLayoutDirection() != mLayoutDirection) {
            mLayoutDirection = newConfig.getLayoutDirection();

            if (mQSAnimator != null) {
                mQSAnimator.onRtlChanged();
            }
        }
    }

    @Override
    public void setContainer(ViewGroup container) {
        if (container instanceof NotificationsQuickSettingsContainer) {
            mQSCustomizer.setContainer((NotificationsQuickSettingsContainer) container);
        }
    }

    @Override
    public boolean isCustomizing() {
        return mQSCustomizer.isCustomizing();
    }

    public void setHost(QSTileHost qsh) {
        mQSPanel.setHost(qsh, mQSCustomizer);
        mHeader.setQSPanel(mQSPanel);
        mFooter.setQSPanel(mQSPanel);
        mQSDetail.setHost(qsh);

        if (mQSAnimator != null) {
            mQSAnimator.setHost(qsh);
        }
    }

    private void updateQsState() {
        final boolean expandVisually = mQsExpanded || mStackScrollerOverscrolling
                || mHeaderAnimating;
        mQSPanel.setExpanded(mQsExpanded);
        mQSDetail.setExpanded(mQsExpanded);
        mHeader.setVisibility((mQsExpanded || !mKeyguardShowing || mHeaderAnimating)
                ? View.VISIBLE
                : View.INVISIBLE);
        mHeader.setExpanded((mKeyguardShowing && !mHeaderAnimating)
                || (mQsExpanded && !mStackScrollerOverscrolling));
        mFooter.setVisibility((mQsExpanded || !mKeyguardShowing || mHeaderAnimating)
                ? View.VISIBLE
                : View.INVISIBLE);
        mFooter.setExpanded((mKeyguardShowing && !mHeaderAnimating)
                || (mQsExpanded && !mStackScrollerOverscrolling));
        mQSPanel.setVisibility(expandVisually ? View.VISIBLE : View.INVISIBLE);
    }

    public QSPanel getQsPanel() {
        return mQSPanel;
    }

    public QSCustomizer getCustomizer() {
        return mQSCustomizer;
    }

    @Override
    public boolean isShowingDetail() {
        return mQSPanel.isShowingCustomize() || mQSDetail.isShowingDetail();
    }

    @Override
    public void setHeaderClickable(boolean clickable) {
        if (DEBUG) Log.d(TAG, "setHeaderClickable " + clickable);

        View expandView = mFooter.getExpandView();
        if (expandView != null) {
            expandView.setClickable(clickable);
        }
    }

    @Override
    public void setExpanded(boolean expanded) {
        if (DEBUG) Log.d(TAG, "setExpanded " + expanded);
        mQsExpanded = expanded;
        mQSPanel.setListening(mListening && mQsExpanded);
        updateQsState();
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        if (DEBUG) Log.d(TAG, "setKeyguardShowing " + keyguardShowing);
        mKeyguardShowing = keyguardShowing;

        if (mQSAnimator != null) {
            mQSAnimator.setOnKeyguard(keyguardShowing);
        }

        mFooter.setKeyguardShowing(keyguardShowing);
        updateQsState();
    }

    @Override
    public void setOverscrolling(boolean stackScrollerOverscrolling) {
        if (DEBUG) Log.d(TAG, "setOverscrolling " + stackScrollerOverscrolling);
        mStackScrollerOverscrolling = stackScrollerOverscrolling;
        updateQsState();
    }

    @Override
    public void setListening(boolean listening) {
        if (DEBUG) Log.d(TAG, "setListening " + listening);
        mListening = listening;
        mHeader.setListening(listening);
        mFooter.setListening(listening);
        mQSPanel.setListening(mListening && mQsExpanded);
    }

    @Override
    public void setHeaderListening(boolean listening) {
        mHeader.setListening(listening);
        mFooter.setListening(listening);
    }

    @Override
    public void setQsExpansion(float expansion, float headerTranslation) {
        if (DEBUG) Log.d(TAG, "setQSExpansion " + expansion + " " + headerTranslation);
        mContainer.setExpansion(expansion);
        final float translationScaleY = expansion - 1;
        if (!mHeaderAnimating) {
            int height = mHeader.getHeight();
            getView().setTranslationY((mKeyguardShowing || mSecureExpandDisabled) ? (translationScaleY * height)
                    : headerTranslation);
        }
        mHeader.setExpansion(mKeyguardShowing ? 1 : expansion);
        mFooter.setExpansion(mKeyguardShowing ? 1 : expansion);
        int heightDiff = mQSPanel.getBottom() - mHeader.getBottom() + mHeader.getPaddingBottom()
                + mFooter.getHeight();
        mQSPanel.setTranslationY(translationScaleY * heightDiff);
        mQSDetail.setFullyExpanded(expansion == 1);

        if (mQSAnimator != null) {
            mQSAnimator.setPosition(expansion);
        }

        // Set bounds on the QS panel so it doesn't run over the header.
        mQsBounds.top = (int) -mQSPanel.getTranslationY();
        mQsBounds.right = mQSPanel.getWidth();
        mQsBounds.bottom = mQSPanel.getHeight();
        mQSPanel.setClipBounds(mQsBounds);
    }

    @Override
    public void animateHeaderSlidingIn(long delay) {
        if (mSecureExpandDisabled) {
            return;
        }
        if (DEBUG) Log.d(TAG, "animateHeaderSlidingIn");
        // If the QS is already expanded we don't need to slide in the header as it's already
        // visible.
        if (!mQsExpanded) {
            mHeaderAnimating = true;
            mDelay = delay;
            getView().getViewTreeObserver().addOnPreDrawListener(mStartHeaderSlidingIn);
        }
    }

    @Override
    public void animateHeaderSlidingOut() {
        if (DEBUG) Log.d(TAG, "animateHeaderSlidingOut");
        mHeaderAnimating = true;
        getView().animate().y(-mHeader.getHeight())
                .setStartDelay(0)
                .setDuration(StackStateAnimator.ANIMATION_DURATION_STANDARD)
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        getView().animate().setListener(null);
                        mHeaderAnimating = false;
                        updateQsState();
                    }
                })
                .start();
    }

    @Override
    public void setExpandClickListener(OnClickListener onClickListener) {
        View expandView = mFooter.getExpandView();

        if (expandView != null) {
            expandView.setOnClickListener(onClickListener);
        }
    }

    @Override
    public void closeDetail() {
        mQSPanel.closeDetail();
    }

    public void notifyCustomizeChanged() {
        // The customize state changed, so our height changed.
        mContainer.updateExpansion();
        mQSPanel.setVisibility(!mQSCustomizer.isCustomizing() ? View.VISIBLE : View.INVISIBLE);
        mHeader.setVisibility(!mQSCustomizer.isCustomizing() ? View.VISIBLE : View.INVISIBLE);
        mFooter.setVisibility(!mQSCustomizer.isCustomizing() ? View.VISIBLE : View.INVISIBLE);
        // Let the panel know the position changed and it needs to update where notifications
        // and whatnot are.
        mPanelView.onQsHeightChanged();

        if (!mQSCustomizer.isCustomizing()) {
            mQSPanel.updateSettings();
        }
    }

    /**
     * The height this view wants to be. This is different from {@link #getMeasuredHeight} such that
     * during closing the detail panel, this already returns the smaller height.
     */
    @Override
    public int getDesiredHeight() {
        if (mQSCustomizer.isCustomizing()) {
            return getView().getHeight();
        }
        if (mQSDetail.isClosingDetail()) {
            LayoutParams layoutParams = (LayoutParams) mQSPanel.getLayoutParams();
            int panelHeight = layoutParams.topMargin + layoutParams.bottomMargin +
                    + mQSPanel.getMeasuredHeight();
            return panelHeight + getView().getPaddingBottom();
        } else {
            return getView().getMeasuredHeight();
        }
    }

    @Override
    public void setHeightOverride(int desiredHeight) {
        mContainer.setHeightOverride(desiredHeight);
    }

    @Override
    public int getQsMinExpansionHeight() {
        return mSecureExpandDisabled ? 0 : mHeader.getHeight();
    }

    public void setSecureExpandDisabled(boolean value) {
        if (DEBUG) Log.d(TAG, "setSecureExpandDisabled " + value);
        mSecureExpandDisabled = value;
      }

    @Override
    public void hideImmediately() {
        getView().animate().cancel();
        getView().setY(-mHeader.getHeight());
    }

    private final ViewTreeObserver.OnPreDrawListener mStartHeaderSlidingIn
            = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            getView().getViewTreeObserver().removeOnPreDrawListener(this);
            getView().animate()
                    .translationY(0f)
                    .setStartDelay(mDelay)
                    .setDuration(StackStateAnimator.ANIMATION_DURATION_GO_TO_FULL_SHADE)
                    .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                    .setListener(mAnimateHeaderSlidingInListener)
                    .start();
            getView().setY(-mHeader.getHeight());
            return true;
        }
    };

    private final Animator.AnimatorListener mAnimateHeaderSlidingInListener
            = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mHeaderAnimating = false;
            updateQsState();
        }
    };


    private static void handleQuickSettingsBackround() {

        if (mContainer == null)
            return;
        if (mKeyguardShowing) {
            mContainer.getBackground().setAlpha(255);
        } else {
            mContainer.getBackground().setAlpha(mTranslucentQuickSettings ? mTranslucencyPercentage : 255);
        }
    }

    public static void startBlurTask() {

        if (!mBlurredStatusBarExpandedEnabled)
            return;
        try {
            if (mBlurredView.getTag().toString().equals("blur_applied"))
                return;
        } catch (Exception e){
        }
        if (mNotificationPanelView == null)
            return;  
        if (mKeyguardShowing)
            return;
       
        BlurTask.setBlurTaskCallback(new BlurUtils.BlurTaskCallback() {

            @Override
            public void blurTaskDone(Bitmap blurredBitmap) {

                if (blurredBitmap != null) {

                    int[] screenDimens = BlurTask.getRealScreenDimensions();
                    mBlurredView.getLayoutParams().width = screenDimens[0];
                    mBlurredView.requestLayout();

                    BitmapDrawable drawable = new BitmapDrawable(blurredBitmap);
                    drawable.setColorFilter(mColorFilter);

                    mBlurredView.setBackground(drawable);

                    mBlurredView.setTag("blur_applied");

                } else {

                    mBlurredView.setBackgroundColor(mBlurLightColorFilter);

                    mBlurredView.setTag("error");

                }
                mBlurredView.startAnimation(mAlphaAnimation);
            }

            @Override
            public void dominantColor(int color) {

                double lightness = DisplayUtils.getColorLightness(color);

                if (lightness >= 0.0 && color <= 1.0) {
                    if (lightness <= 0.33) {
                        mColorFilter = new PorterDuffColorFilter(mBlurLightColorFilter, PorterDuff.Mode.MULTIPLY);

                    } else if (lightness >= 0.34 && lightness <= 0.66) {
                        mColorFilter = new PorterDuffColorFilter(mBlurMixedColorFilter, PorterDuff.Mode.MULTIPLY);

                    } else if (lightness >= 0.67 && lightness <= 1.0) {
                        mColorFilter = new PorterDuffColorFilter(mBlurDarkColorFilter, PorterDuff.Mode.MULTIPLY);
                    }

                } else {
                    mColorFilter = new PorterDuffColorFilter(mBlurMixedColorFilter, PorterDuff.Mode.MULTIPLY);
                }
            }
        });

        BlurTask.setBlurEngine(BlurUtils.BlurEngine.RenderScriptBlur);

        new BlurTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void recycle() {

        if (mBlurredView != null &&
                mBlurredView.getBackground() != null) {
            if (mBlurredView.getBackground() instanceof BitmapDrawable) {

                Bitmap bitmap = ((BitmapDrawable) mBlurredView.getBackground()).getBitmap();
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
            }
            mBlurredView.setBackground(null);
        }

        mBlurredView.setTag("ready_to_blur");

        mBlurredView.setVisibility(View.INVISIBLE);

   }

   public void initBlurprefs() {
            mNotificationPanelView = this;

            mBlurUtils = new BlurUtils(mNotificationPanelView.getContext());

            mAlphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            mAlphaAnimation.setDuration(75);
            mAlphaAnimation.setAnimationListener(mAnimationListener);

            mBlurredView = new FrameLayout(mNotificationPanelView.getContext());

            mNotificationPanelView.addView(mBlurredView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mNotificationPanelView.requestLayout();
            setQSStroke();
            mBlurredView.setTag("ready_to_blur");

            mBlurredView.setVisibility(View.INVISIBLE);

            handleQuickSettingsBackround();
    }

    public static class BlurTask extends AsyncTask<Void, Void, Bitmap> {

        private static int[] mScreenDimens;
        private static BlurUtils.BlurEngine mBlurEngine;
        private static BlurUtils.BlurTaskCallback mCallback;

        private Bitmap mScreenBitmap;

        public static void setBlurEngine(BlurUtils.BlurEngine blurEngine) {
            mBlurEngine = blurEngine;
        }

        public static void setBlurTaskCallback(BlurUtils.BlurTaskCallback callBack) {
            mCallback = callBack;
        }

        public static int[] getRealScreenDimensions() {
            return mScreenDimens;
        }

        @Override
        protected void onPreExecute() {

            Context context = mNotificationPanelView.getContext();
            mScreenDimens = DisplayUtils.getRealScreenDimensions(context);
            
            //We don't want SystemUI to crash for Arithmetic Exception
            if(mBlurScale==0){
                mBlurScale=1;
            }

            mScreenBitmap = DisplayUtils.takeSurfaceScreenshot(context, mBlurScale);
        }

        @Override
        protected Bitmap doInBackground(Void... arg0) {

            try {
                if (mScreenBitmap == null)
                    return null;

                mCallback.dominantColor(DisplayUtils.getDominantColorByPixelsSampling(mScreenBitmap, 20, 20));

                //We don't want SystemUI to crash for Arithmetic Exception
                if(mBlurRadius == 0){
                    mBlurRadius=1;
                }
                
                mScreenBitmap = mBlurUtils.renderScriptBlur(mScreenBitmap, mBlurRadius);
                return mScreenBitmap;

            } catch (OutOfMemoryError e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {

            if (bitmap != null) {
                mCallback.blurTaskDone(bitmap);

            } else {
                mCallback.blurTaskDone(null);
            }
        }
   }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BLUR_SCALE_PREFERENCE_KEY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BLUR_RADIUS_PREFERENCE_KEY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.TRANSLUCENT_QUICK_SETTINGS_PREFERENCE_KEY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.TRANSLUCENT_QUICK_SETTINGS_PRECENTAGE_PREFERENCE_KEY), false, this);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            ContentResolver resolver = getContext().getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        @Override
        public void update() {
            ContentResolver resolver = getContext().getContentResolver();
            mBlurScale = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.BLUR_SCALE_PREFERENCE_KEY, 10);
            mBlurRadius = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.BLUR_RADIUS_PREFERENCE_KEY, 5);
            mTranslucentQuickSettings =  Settings.System.getIntForUser(resolver,
                    Settings.System.TRANSLUCENT_QUICK_SETTINGS_PREFERENCE_KEY, 0, UserHandle.USER_CURRENT) == 1;
            mBlurredStatusBarExpandedEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY, 0, UserHandle.USER_CURRENT) == 1;
            mTranslucencyPercentage = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.TRANSLUCENT_QUICK_SETTINGS_PRECENTAGE_PREFERENCE_KEY, 60);

            mBlurDarkColorFilter = Color.LTGRAY;
            mBlurMixedColorFilter = Color.GRAY;
            mBlurLightColorFilter = Color.DKGRAY;
            mTranslucencyPercentage = 255 - ((mTranslucencyPercentage * 255) / 100);
            handleQuickSettingsBackround();
        }
    }

}
