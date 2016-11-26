package com.google.android.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.RenderNodeAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.ButtonDispatcher;
import com.android.systemui.statusbar.policy.KeyButtonView;

public class OpaLayout extends FrameLayout implements ButtonDispatcher.ButtonInterface{

    private static final int ANIMATION_STATE_NONE = 0;
    private static final int ANIMATION_STATE_DIAMOND = 1;
    private static final int ANIMATION_STATE_RETRACT = 2;
    private static final int ANIMATION_STATE_OTHER = 3;

    private static final int MIN_DIAMOND_DURATION = 100;
    private static final int COLLAPSE_ANIMATION_DURATION_RY = 83;
    private static final int COLLAPSE_ANIMATION_DURATION_BG = 100;
    private static final int LINE_ANIMATION_DURATION_Y = 275;
    private static final int LINE_ANIMATION_DURATION_X = 133;
    private static final int RETRACT_ANIMATION_DURATION = 300;
    private static final int DIAMOND_ANIMATION_DURATION = 200;
    private static final int HALO_ANIMATION_DURATION = 100;

    private static final int DOTS_RESIZE_DURATION = 200;
    private static final int HOME_RESIZE_DURATION = 83;

    private static final int HOME_REAPPEAR_ANIMATION_OFFSET = 33;
    private static final int HOME_REAPPEAR_DURATION = 150;

    private static final float DIAMOND_DOTS_SCALE_FACTOR = 0.8f;
    private static final float DIAMOND_HOME_SCALE_FACTOR = 0.625f;
    private static final float HALO_SCALE_FACTOR = 0.47619048f;

    private KeyButtonView mHome;

    private int mAnimationState;
    private final ArraySet<Animator> mCurrentAnimators;

    private boolean mIsLandscape;
    private boolean mIsPressed;
    private boolean mLongClicked;
    private boolean mOpaEnabled;
    private long mStartTime;

    private View mRed;
    private View mBlue;
    private View mGreen;
    private View mYellow;
    private View mWhite;
    private View mHalo;

    private View mTop;
    private View mRight;
    private View mLeft;
    private View mBottom;

    private final Runnable mCheckLongPress;
    private final Runnable mRetract;

    private final Interpolator mRetractInterpolator;
    private final Interpolator mCollapseInterpolator;
    private final Interpolator mDiamondInterpolator;
    private final Interpolator mDotsFullSizeInterpolator;
    private final Interpolator mFastOutSlowInInterpolator;
    private final Interpolator mHomeDisappearInterpolator;

    public OpaLayout(Context context) {
        super(context);
        this.mFastOutSlowInInterpolator = Interpolators.FAST_OUT_SLOW_IN;
        this.mHomeDisappearInterpolator = new PathInterpolator(0.8f, 0f, 1f, 1f);
        this.mCollapseInterpolator = Interpolators.FAST_OUT_LINEAR_IN;
        this.mDotsFullSizeInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mRetractInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mDiamondInterpolator = new PathInterpolator(0.2f, 0f, 0.2f, 1f);
        this.mCheckLongPress = new Runnable() {
            @Override
            public void run() {
                if (OpaLayout.this.mIsPressed) {
                    OpaLayout.this.mLongClicked = true;
                }
            }
        };
        this.mRetract = new Runnable() {
            @Override
            public void run() {
                OpaLayout.this.cancelCurrentAnimation();
                OpaLayout.this.startRetractAnimation();
            }
        };
        this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
        this.mCurrentAnimators = new ArraySet<Animator>();
    }

    public OpaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mFastOutSlowInInterpolator = Interpolators.FAST_OUT_SLOW_IN;
        this.mHomeDisappearInterpolator = new PathInterpolator(0.8f, 0f, 1f, 1f);
        this.mCollapseInterpolator = Interpolators.FAST_OUT_LINEAR_IN;
        this.mDotsFullSizeInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mRetractInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mDiamondInterpolator = new PathInterpolator(0.2f, 0f, 0.2f, 1f);
        this.mCheckLongPress = new Runnable() {
            @Override
            public void run() {
                if (OpaLayout.this.mIsPressed) {
                    OpaLayout.this.mLongClicked = true;
                }
            }
        };
        this.mRetract = new Runnable() {
            @Override
            public void run() {
                OpaLayout.this.cancelCurrentAnimation();
                OpaLayout.this.startRetractAnimation();
            }
        };
        this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
        this.mCurrentAnimators = new ArraySet<Animator>();
    }

    public OpaLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mFastOutSlowInInterpolator = Interpolators.FAST_OUT_SLOW_IN;
        this.mHomeDisappearInterpolator = new PathInterpolator(0.8f, 0f, 1f, 1f);
        this.mCollapseInterpolator = Interpolators.FAST_OUT_LINEAR_IN;
        this.mDotsFullSizeInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mRetractInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mDiamondInterpolator = new PathInterpolator(0.2f, 0f, 0.2f, 1f);
        this.mCheckLongPress = new Runnable() {
            @Override
            public void run() {
                if (OpaLayout.this.mIsPressed) {
                    OpaLayout.this.mLongClicked = true;
                }
            }
        };
        this.mRetract = new Runnable() {
            @Override
            public void run() {
                OpaLayout.this.cancelCurrentAnimation();
                OpaLayout.this.startRetractAnimation();
            }
        };
        this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
        this.mCurrentAnimators = new ArraySet<Animator>();
    }

    public OpaLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mFastOutSlowInInterpolator = Interpolators.FAST_OUT_SLOW_IN;
        this.mHomeDisappearInterpolator = new PathInterpolator(0.8f, 0f, 1f, 1f);
        this.mCollapseInterpolator = Interpolators.FAST_OUT_LINEAR_IN;
        this.mDotsFullSizeInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mRetractInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        this.mDiamondInterpolator = new PathInterpolator(0.2f, 0f, 0.2f, 1f);
        this.mCheckLongPress = new Runnable() {
            @Override
            public void run() {
                if (OpaLayout.this.mIsPressed) {
                    OpaLayout.this.mLongClicked = true;
                }
            }
        };
        this.mRetract = new Runnable() {
            @Override
            public void run() {
                OpaLayout.this.cancelCurrentAnimation();
                OpaLayout.this.startRetractAnimation();
            }
        };
        this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
        this.mCurrentAnimators = new ArraySet<Animator>();
    }

    private void startAll(ArraySet<Animator> animators) {
        for(int i=0; i < animators.size(); i++) {
            Animator curAnim = (Animator) this.mCurrentAnimators.valueAt(i);
            curAnim.start();
        }
    }

    private void startCollapseAnimation() {
        this.mCurrentAnimators.clear();
        this.mCurrentAnimators.addAll(this.getCollapseAnimatorSet());
        this.mAnimationState = OpaLayout.ANIMATION_STATE_OTHER;
        this.startAll(this.mCurrentAnimators);
    }

    private void startDiamondAnimation() {
        this.mCurrentAnimators.clear();
        this.mCurrentAnimators.addAll(this.getDiamondAnimatorSet());
        this.mAnimationState = OpaLayout.ANIMATION_STATE_DIAMOND;
        this.startAll(this.mCurrentAnimators);
    }

    private void startLineAnimation() {
        this.mCurrentAnimators.clear();
        this.mCurrentAnimators.addAll(this.getLineAnimatorSet());
        this.mAnimationState = OpaLayout.ANIMATION_STATE_OTHER;
        this.startAll(this.mCurrentAnimators);
    }

    private void startRetractAnimation() {
        this.mCurrentAnimators.clear();
        this.mCurrentAnimators.addAll(this.getRetractAnimatorSet());
        this.mAnimationState = OpaLayout.ANIMATION_STATE_RETRACT;
        this.startAll(this.mCurrentAnimators);
    }

    private void cancelCurrentAnimation() {
        if(this.mCurrentAnimators.isEmpty())
            return;
        for(int i=0; i < this.mCurrentAnimators.size(); i++) {
            Animator curAnim = (Animator) this.mCurrentAnimators.valueAt(i);
            curAnim.removeAllListeners();
            curAnim.cancel();
        }
        this.mCurrentAnimators.clear();
        this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
    }

    private void endCurrentAnimation() {
        if(this.mCurrentAnimators.isEmpty())
            return;
        for(int i=0; i < this.mCurrentAnimators.size(); i++) {
            Animator curAnim = (Animator) this.mCurrentAnimators.valueAt(i);
            curAnim.removeAllListeners();
            curAnim.end();
        }
        this.mCurrentAnimators.clear();
        this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
    }

    private ArraySet<Animator> getCollapseAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        Animator animator;
        if (this.mIsLandscape) {
            animator = this.getDeltaAnimatorY(this.mRed, this.mCollapseInterpolator, -this.getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.COLLAPSE_ANIMATION_DURATION_RY);
        }
        else {
            animator = this.getDeltaAnimatorX(this.mRed, this.mCollapseInterpolator, this.getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.COLLAPSE_ANIMATION_DURATION_RY);
        }
        set.add(animator);
        set.add(this.getScaleAnimatorX(this.mRed, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        set.add(this.getScaleAnimatorY(this.mRed, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        Animator animator2;
        if (this.mIsLandscape) {
            animator2 = this.getDeltaAnimatorY(this.mBlue, this.mCollapseInterpolator, -this.getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.COLLAPSE_ANIMATION_DURATION_BG);
        }
        else {
            animator2 = this.getDeltaAnimatorX(this.mBlue, this.mCollapseInterpolator, this.getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.COLLAPSE_ANIMATION_DURATION_BG);
        }
        set.add(animator2);
        set.add(this.getScaleAnimatorX(this.mBlue, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        set.add(this.getScaleAnimatorY(this.mBlue, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        Animator animator3;
        if (this.mIsLandscape) {
            animator3 = this.getDeltaAnimatorY(this.mYellow, this.mCollapseInterpolator, this.getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.COLLAPSE_ANIMATION_DURATION_RY);
        }
        else {
            animator3 = this.getDeltaAnimatorX(this.mYellow, this.mCollapseInterpolator, -this.getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.COLLAPSE_ANIMATION_DURATION_RY);
        }
        set.add(animator3);
        set.add(this.getScaleAnimatorX(this.mYellow, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        set.add(this.getScaleAnimatorY(this.mYellow, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        Animator animator4;
        if (this.mIsLandscape) {
            animator4 = this.getDeltaAnimatorY(this.mGreen, this.mCollapseInterpolator, this.getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.COLLAPSE_ANIMATION_DURATION_BG);
        }
        else {
            animator4 = this.getDeltaAnimatorX(this.mGreen, this.mCollapseInterpolator, -this.getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.COLLAPSE_ANIMATION_DURATION_BG);
        }
        set.add(animator4);
        set.add(this.getScaleAnimatorX(this.mGreen, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        set.add(this.getScaleAnimatorY(this.mGreen, 1.0f, OpaLayout.DOTS_RESIZE_DURATION, this.mDotsFullSizeInterpolator));
        final Animator scaleAnimatorX = this.getScaleAnimatorX(this.mWhite, 1.0f, OpaLayout.HOME_REAPPEAR_DURATION, this.mFastOutSlowInInterpolator);
        final Animator scaleAnimatorY = this.getScaleAnimatorY(this.mWhite, 1.0f, OpaLayout.HOME_REAPPEAR_DURATION, this.mFastOutSlowInInterpolator);
        final Animator scaleAnimatorX2 = this.getScaleAnimatorX(this.mHalo, 1.0f, OpaLayout.HOME_REAPPEAR_DURATION, this.mFastOutSlowInInterpolator);
        final Animator scaleAnimatorY2 = this.getScaleAnimatorY(this.mHalo, 1.0f, OpaLayout.HOME_REAPPEAR_DURATION, this.mFastOutSlowInInterpolator);
        scaleAnimatorX.setStartDelay(OpaLayout.HOME_REAPPEAR_ANIMATION_OFFSET);
        scaleAnimatorY.setStartDelay(OpaLayout.HOME_REAPPEAR_ANIMATION_OFFSET);
        scaleAnimatorX2.setStartDelay(OpaLayout.HOME_REAPPEAR_ANIMATION_OFFSET);
        scaleAnimatorY2.setStartDelay(OpaLayout.HOME_REAPPEAR_ANIMATION_OFFSET);
        set.add(scaleAnimatorX);
        set.add(scaleAnimatorY);
        set.add(scaleAnimatorX2);
        set.add(scaleAnimatorY2);
        this.getLongestAnim((set)).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationEnd(final Animator animator) {
                OpaLayout.this.mCurrentAnimators.clear();
                OpaLayout.this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
            }
        });
        return set;
    }

    private ArraySet<Animator> getDiamondAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        set.add(this.getDeltaAnimatorY(this.mTop, this.mDiamondInterpolator, -this.getPxVal(R.dimen.opa_diamond_translation), OpaLayout.DIAMOND_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mTop, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mTop, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getDeltaAnimatorY(this.mBottom, this.mDiamondInterpolator, this.getPxVal(R.dimen.opa_diamond_translation), OpaLayout.DIAMOND_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mBottom, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mBottom, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getDeltaAnimatorX(this.mLeft, this.mDiamondInterpolator, -this.getPxVal(R.dimen.opa_diamond_translation), OpaLayout.DIAMOND_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mLeft, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mLeft, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getDeltaAnimatorX(this.mRight, this.mDiamondInterpolator, this.getPxVal(R.dimen.opa_diamond_translation), OpaLayout.DIAMOND_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mRight, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mRight, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorX(this.mWhite, OpaLayout.DIAMOND_HOME_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mWhite, OpaLayout.DIAMOND_HOME_SCALE_FACTOR, OpaLayout.DIAMOND_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorX(this.mHalo, OpaLayout.HALO_SCALE_FACTOR, OpaLayout.MIN_DIAMOND_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mHalo, OpaLayout.HALO_SCALE_FACTOR, OpaLayout.MIN_DIAMOND_DURATION, this.mFastOutSlowInInterpolator));
        this.getLongestAnim(set).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationCancel(final Animator animator) {
                OpaLayout.this.mCurrentAnimators.clear();
            }

            public void onAnimationEnd(final Animator animator) {
                OpaLayout.this.startLineAnimation();
            }
        });
        return set;
    }

    private ArraySet<Animator> getLineAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        if (this.mIsLandscape) {
            set.add(this.getDeltaAnimatorY(this.mRed, this.mFastOutSlowInInterpolator, this.getPxVal(R.dimen.opa_line_y_translation), OpaLayout.LINE_ANIMATION_DURATION_Y));
            set.add(this.getDeltaAnimatorX(this.mRed, this.mFastOutSlowInInterpolator, this.getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.LINE_ANIMATION_DURATION_X));
            set.add(this.getDeltaAnimatorY(this.mBlue, this.mFastOutSlowInInterpolator, this.getPxVal(R.dimen.opa_line_y_translation), OpaLayout.LINE_ANIMATION_DURATION_Y));
            set.add(this.getDeltaAnimatorY(this.mYellow, this.mFastOutSlowInInterpolator, -this.getPxVal(R.dimen.opa_line_y_translation), OpaLayout.LINE_ANIMATION_DURATION_Y));
            set.add(this.getDeltaAnimatorX(this.mYellow, this.mFastOutSlowInInterpolator, -this.getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.LINE_ANIMATION_DURATION_X));
            set.add(this.getDeltaAnimatorY(this.mGreen, this.mFastOutSlowInInterpolator, -this.getPxVal(R.dimen.opa_line_y_translation), OpaLayout.LINE_ANIMATION_DURATION_Y));
        }
        else {
            set.add(this.getDeltaAnimatorX(this.mRed, this.mFastOutSlowInInterpolator, -this.getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.LINE_ANIMATION_DURATION_Y));
            set.add(this.getDeltaAnimatorY(this.mRed, this.mFastOutSlowInInterpolator, this.getPxVal(R.dimen.opa_line_y_translation), OpaLayout.LINE_ANIMATION_DURATION_X));
            set.add(this.getDeltaAnimatorX(this.mBlue, this.mFastOutSlowInInterpolator, -this.getPxVal(R.dimen.opa_line_x_trans_bg), OpaLayout.LINE_ANIMATION_DURATION_Y));
            set.add(this.getDeltaAnimatorX(this.mYellow, this.mFastOutSlowInInterpolator, this.getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.LINE_ANIMATION_DURATION_Y));
            set.add(this.getDeltaAnimatorY(this.mYellow, this.mFastOutSlowInInterpolator, -this.getPxVal(R.dimen.opa_line_y_translation), OpaLayout.LINE_ANIMATION_DURATION_X));
            set.add(this.getDeltaAnimatorX(this.mGreen, this.mFastOutSlowInInterpolator, this.getPxVal(R.dimen.opa_line_x_trans_bg), OpaLayout.LINE_ANIMATION_DURATION_Y));
        }
        set.add(this.getScaleAnimatorX(this.mWhite, 0.0f, OpaLayout.HOME_RESIZE_DURATION, this.mHomeDisappearInterpolator));
        set.add(this.getScaleAnimatorY(this.mWhite, 0.0f, OpaLayout.HOME_RESIZE_DURATION, this.mHomeDisappearInterpolator));
        set.add(this.getScaleAnimatorX(this.mHalo, 0.0f, OpaLayout.HOME_RESIZE_DURATION, this.mHomeDisappearInterpolator));
        set.add(this.getScaleAnimatorY(this.mHalo, 0.0f, OpaLayout.HOME_RESIZE_DURATION, this.mHomeDisappearInterpolator));
        this.getLongestAnim(set).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationCancel(final Animator animator) {
                OpaLayout.this.mCurrentAnimators.clear();
            }

            public void onAnimationEnd(final Animator animator) {
                OpaLayout.this.startCollapseAnimation();
            }
        });
        return set;
    }

    private ArraySet<Animator> getRetractAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        set.add(this.getTranslationAnimatorX(this.mRed, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getTranslationAnimatorY(this.mRed, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mRed, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorY(this.mRed, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getTranslationAnimatorX(this.mBlue, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getTranslationAnimatorY(this.mBlue, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mBlue, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorY(this.mBlue, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getTranslationAnimatorX(this.mGreen, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getTranslationAnimatorY(this.mGreen, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mGreen, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorY(this.mGreen, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getTranslationAnimatorX(this.mYellow, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getTranslationAnimatorY(this.mYellow, this.mRetractInterpolator, OpaLayout.RETRACT_ANIMATION_DURATION));
        set.add(this.getScaleAnimatorX(this.mYellow, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorY(this.mYellow, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorX(this.mWhite, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorY(this.mWhite, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mRetractInterpolator));
        set.add(this.getScaleAnimatorX(this.mHalo, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        set.add(this.getScaleAnimatorY(this.mHalo, 1.0f, OpaLayout.RETRACT_ANIMATION_DURATION, this.mFastOutSlowInInterpolator));
        this.getLongestAnim(set).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationEnd(final Animator animator) {
                OpaLayout.this.mCurrentAnimators.clear();
                OpaLayout.this.mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
            }
        });
        return set;
    }

    private float getPxVal(int id) {
        return this.getResources().getDimensionPixelOffset(id);
    }

    private Animator getDeltaAnimatorX(View v, Interpolator interpolator, float deltaX, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(8, (int) (v.getX() + deltaX));
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getDeltaAnimatorY(View v, Interpolator interpolator, float deltaY, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(9, (int) (v.getY() + deltaY));
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getScaleAnimatorX(View v, float factor, int duration, Interpolator interpolator) {
        RenderNodeAnimator anim = new RenderNodeAnimator(3, factor);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getScaleAnimatorY(View v, float factor, int duration, Interpolator interpolator) {
        RenderNodeAnimator anim = new RenderNodeAnimator(4, factor);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getTranslationAnimatorX(View v, Interpolator interpolator, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(0, 0);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getTranslationAnimatorY(View v, Interpolator interpolator, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(1, 0);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getLongestAnim(ArraySet<Animator> animators) {
        long longestDuration = -1;
        Animator longestAnim = null;

        for(int i=0; i < animators.size(); i++) {
            Animator a = (Animator) animators.valueAt(i);
            if(a.getTotalDuration() > longestDuration) {
                longestDuration = a.getTotalDuration();
                longestAnim = a;
            }
        }
        return longestAnim;
    }

    public void abortCurrentGesture() {
        this.mHome.abortCurrentGesture();
    }

    protected void onFinishInflate() {
        super.onFinishInflate();

        mRed = this.findViewById(R.id.red);
        mBlue = this.findViewById(R.id.blue);
        mYellow = this.findViewById(R.id.yellow);
        mGreen = this.findViewById(R.id.green);
        mWhite = this.findViewById(R.id.white);
        mHalo = this.findViewById(R.id.halo);
        mHome = (KeyButtonView) this.findViewById(R.id.home_button);

        this.setOpaEnabled(true);
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!this.mOpaEnabled) {
            return false;
        }
        switch (ev.getAction()) {
            case 0: {
                if (!this.mCurrentAnimators.isEmpty()) {
                    if (this.mAnimationState != OpaLayout.ANIMATION_STATE_RETRACT) {
                        return false;
                    }
                    this.endCurrentAnimation();
                }
                this.mStartTime = SystemClock.elapsedRealtime();
                this.mLongClicked = false;
                this.mIsPressed = true;
                this.startDiamondAnimation();
                this.removeCallbacks(this.mCheckLongPress);
                this.postDelayed(this.mCheckLongPress, (long)ViewConfiguration.getLongPressTimeout());
                return false;
            }
            case 1:
            case 3: {
                if (this.mAnimationState == OpaLayout.ANIMATION_STATE_DIAMOND) {
                    final long elapsedRealtime = SystemClock.elapsedRealtime();
                    final long mStartTime = this.mStartTime;
                    this.removeCallbacks(this.mRetract);
                    this.postDelayed(this.mRetract, 100L - (elapsedRealtime - mStartTime));
                    this.removeCallbacks(this.mCheckLongPress);
                    return false;
                }
                int n;
                if (!this.mIsPressed || this.mLongClicked) {
                    n = 0;
                }
                else {
                    n = 1;
                }
                this.mIsPressed = false;
                if (n != 0) {
                    this.mRetract.run();
                    return false;
                }
                break;
            }
        }
        return false;
    }

    public void setCarMode(boolean carMode) {
        this.setOpaEnabled(!carMode);
    }

    public void setImageDrawable(Drawable drawable) {
        ((ImageView) mWhite).setImageDrawable(drawable);
    }

    public void setImageResource(int resId) {
        ((ImageView) mWhite).setImageResource(resId);
    }

    public void setLandscape(boolean landscape) {
        this.mIsLandscape = mIsLandscape;
        if (this.mIsLandscape) {
            this.mTop = this.mGreen;
            this.mBottom = this.mBlue;
            this.mRight = this.mYellow;
            this.mLeft = this.mRed;
            return;
        }
        this.mTop = this.mRed;
        this.mBottom = this.mYellow;
        this.mLeft = this.mBlue;
        this.mRight = this.mGreen;
    }

    public void setOnLongClickListener(View.OnLongClickListener l) {
        mHome.setOnLongClickListener(l);
    }

    public void setOnTouchListener(View.OnTouchListener l) {
        mHome.setOnTouchListener(l);
    }

    public void setOpaEnabled(boolean enabled) {
        final boolean b2 = enabled || UserManager.isDeviceInDemoMode(this.getContext());
        this.mOpaEnabled = true;
        int visibility;
        if (b2) {
            visibility = View.VISIBLE;
        }
        else {
            visibility = View.INVISIBLE;
        }
        this.mBlue.setVisibility(visibility);
        this.mRed.setVisibility(visibility);
        this.mYellow.setVisibility(visibility);
        this.mGreen.setVisibility(visibility);
        this.mHalo.setVisibility(visibility);
    }

}
