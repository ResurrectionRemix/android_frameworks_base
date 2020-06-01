package com.android.keyguard;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Build;
import android.transition.Fade;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;

import androidx.annotation.VisibleForTesting;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.OnColorsChangedListener;
import com.android.keyguard.clock.ClockManager;
import com.android.keyguard.KeyguardSliceView;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Switch to show plugin clock when plugin is connected, otherwise it will show default clock.
 */
public class KeyguardClockSwitch extends RelativeLayout implements TunerService.Tunable {

    private static final String TAG = "KeyguardClockSwitch";
    private static final boolean CUSTOM_CLOCKS_ENABLED = true;
    private static final String KEYGUARD_TRANSISITION_ANIMATIONS = "sysui_keyguard_transition_animations";

    /**
     * Animation fraction when text is transitioned to/from bold.
     */
    private static final float TO_BOLD_TRANSITION_FRACTION = 0.7f;

    /**
     * Controller used to track StatusBar state to know when to show the big_clock_container.
     */
    private final StatusBarStateController mStatusBarStateController;

    /**
     * Color extractor used to apply colors from wallpaper to custom clock faces.
     */
    private final SysuiColorExtractor mSysuiColorExtractor;

    /**
     * Manager used to know when to show a custom clock face.
     */
    private final ClockManager mClockManager;

    /**
     * Layout transition that scales the default clock face.
     */
    private final Transition mTransition;

    private final ClockVisibilityTransition mClockTransition;
    private final ClockVisibilityTransition mBoldClockTransition;

    /**
     * Optional/alternative clock injected via plugin.
     */
    private ClockPlugin mClockPlugin;

    /**
     * Default clock.
     */
    private TextClock mClockView;

    /**
     * Default clock, bold version.
     * Used to transition to bold when shrinking the default clock.
     */
    private TextClock mClockViewBold;

    /**
     * Frame for default and custom clock.
     */
    private FrameLayout mSmallClockFrame;

    /**
     * Container for big custom clock.
     */
    private ViewGroup mBigClockContainer;

    /**
     * Status area (date and other stuff) shown below the clock. Plugin can decide whether or not to
     * show it below the alternate clock.
     */
    private View mKeyguardStatusArea;

    /**
     * Maintain state so that a newly connected plugin can be initialized.
     */
    private float mDarkAmount;

    /**
     * Boolean value indicating if notifications are visible on lock screen.
     */
    private boolean mHasVisibleNotifications;

    /**
     * If the Keyguard Slice has a header (big center-aligned text.)
     */
    private boolean mShowingHeader;
    private boolean mSupportsDarkText;
    private int[] mColorPalette;
    private boolean mShowCurrentUserTime;

    /**
     * Track the state of the status bar to know when to hide the big_clock_container.
     */
    private int mStatusBarState;

    private final StatusBarStateController.StateListener mStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onStateChanged(int newState) {
                    mStatusBarState = newState;
                    updateBigClockVisibility();
                }
            };

    private ClockManager.ClockChangedListener mClockChangedListener = this::setClockPlugin;

    /**
     * Listener for changes to the color palette.
     *
     * The color palette changes when the wallpaper is changed.
     */
    private final OnColorsChangedListener mColorsListener = (extractor, which) -> {
        if ((which & WallpaperManager.FLAG_LOCK) != 0) {
            updateColors();
	    updateClockColor();
        }
    };

    private boolean mKeyguardTransitionAnimations = true;

    @Inject
    public KeyguardClockSwitch(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            StatusBarStateController statusBarStateController, SysuiColorExtractor colorExtractor,
            ClockManager clockManager) {
        super(context, attrs);
        mStatusBarStateController = statusBarStateController;
        mStatusBarState = mStatusBarStateController.getState();
        mSysuiColorExtractor = colorExtractor;
        mClockManager = clockManager;

        mClockTransition = new ClockVisibilityTransition().setCutoff(
                1 - TO_BOLD_TRANSITION_FRACTION);
        mClockTransition.addTarget(R.id.default_clock_view);
        mBoldClockTransition = new ClockVisibilityTransition().setCutoff(
                TO_BOLD_TRANSITION_FRACTION);
        mBoldClockTransition.addTarget(R.id.default_clock_view_bold);
        mTransition = new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(mClockTransition)
                .addTransition(mBoldClockTransition)
                .setDuration(KeyguardSliceView.DEFAULT_ANIM_DURATION / 2)
                .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
    }

    /**
     * Returns if this view is presenting a custom clock, or the default implementation.
     */
    public boolean hasCustomClock() {
        return mClockPlugin != null;
    }

    public boolean hasCustomClockInBigContainer() {
        return hasCustomClock() && mClockPlugin.shouldShowInBigContainer();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mClockView = findViewById(R.id.default_clock_view);
        mClockViewBold = findViewById(R.id.default_clock_view_bold);
        mSmallClockFrame = findViewById(R.id.clock_view);
        mKeyguardStatusArea = findViewById(R.id.keyguard_status_area);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mClockManager.addOnClockChangedListener(mClockChangedListener);

        Dependency.get(TunerService.class).addTunable(this, KEYGUARD_TRANSISITION_ANIMATIONS);

        if (CUSTOM_CLOCKS_ENABLED) {
            mClockManager.addOnClockChangedListener(mClockChangedListener);
        }
        mStatusBarStateController.addCallback(mStateListener);
        mSysuiColorExtractor.addOnColorsChangedListener(mColorsListener);
        updateColors();
	updateClockColor();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mClockManager.removeOnClockChangedListener(mClockChangedListener);

        Dependency.get(TunerService.class).removeTunable(this);

        if (CUSTOM_CLOCKS_ENABLED) {
            mClockManager.removeOnClockChangedListener(mClockChangedListener);
        }
        mStatusBarStateController.removeCallback(mStateListener);
        mSysuiColorExtractor.removeOnColorsChangedListener(mColorsListener);
        setClockPlugin(null);
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 34);
    }

    private int getLockClockSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 78);
    }

    public void updateClockColor() {
        ContentResolver resolver = getContext().getContentResolver();
        int color = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_CLOCK_COLOR, 0xFFFFFFFF);

        if (mClockView != null) {
            mClockView.setTextColor(color);
        }
        if (mClockViewBold != null) {
            mClockViewBold.setTextColor(color);
        }
        if (mClockPlugin != null) {
            mClockPlugin.setTextColor(color);
        }
    }

    private void setClockPlugin(ClockPlugin plugin) {
        // Disconnect from existing plugin.
        if (mClockPlugin != null) {
            View smallClockView = mClockPlugin.getView();
            if (smallClockView != null && smallClockView.getParent() == mSmallClockFrame) {
                mSmallClockFrame.removeView(smallClockView);
            }
            View bigClockView = mClockPlugin.getBigClockView();
            if (bigClockView != null && bigClockView.getParent() == mSmallClockFrame) {
                mSmallClockFrame.removeView(bigClockView);
            }
            if (mBigClockContainer != null) {
                mBigClockContainer.removeAllViews();
                updateBigClockVisibility();
            }
            mClockPlugin.onDestroyView();
            mClockPlugin = null;
        }
        if (plugin == null) {
            if (mShowingHeader) {
                mClockView.setVisibility(View.GONE);
                mClockViewBold.setVisibility(View.VISIBLE);
            } else {
                mClockView.setVisibility(View.VISIBLE);
                mClockViewBold.setVisibility(View.INVISIBLE);
            }
            mKeyguardStatusArea.setVisibility(View.VISIBLE);
            return;
        }


        // Attach small and big clock views to hierarchy.
        View smallClockView = plugin.getView();
        View bigClockView = plugin.getBigClockView();

        if (plugin.shouldShowInBigContainer()) {
            if (smallClockView != null) {
                mSmallClockFrame.addView(smallClockView, -1,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                mClockView.setVisibility(View.GONE);
                mClockViewBold.setVisibility(View.GONE);
            }
            if (bigClockView != null && mBigClockContainer != null) {
                mBigClockContainer.addView(bigClockView);
                updateBigClockVisibility();
            }
        } else {
            mClockView.setVisibility(View.GONE);
            mClockViewBold.setVisibility(View.GONE);

            if (bigClockView != null ) {
                mSmallClockFrame.addView(bigClockView, -1,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }



        // Hide default clock.
        if (!plugin.shouldShowStatusArea()) {
            mKeyguardStatusArea.setVisibility(View.GONE);
        }
        // Initialize plugin parameters.
        mClockPlugin = plugin;
        mClockPlugin.setStyle(getPaint().getStyle());
        mClockPlugin.setTextColor(getCurrentTextColor());
        mClockPlugin.setDarkAmount(mDarkAmount);
        if (mColorPalette != null) {
            mClockPlugin.setColorPalette(mSupportsDarkText, mColorPalette);
        }
    }

    /**
     * Set container for big clock face appearing behind NSSL and KeyguardStatusView.
     */
    public void setBigClockContainer(ViewGroup container) {
        if (mClockPlugin != null && container != null) {
            if (mClockPlugin.shouldShowInBigContainer()) {
                View bigClockView = mClockPlugin.getBigClockView();
                if (bigClockView != null) {
                    container.addView(bigClockView);
                }
                mBigClockContainer = container;
            } else {
                mBigClockContainer = null;
            }
        }
        updateBigClockVisibility();
    }

    /**
     * It will also update plugin setStyle if plugin is connected.
     */
    public void setStyle(Style style) {
        mClockView.getPaint().setStyle(style);
        mClockViewBold.getPaint().setStyle(style);
        if (mClockPlugin != null) {
            mClockPlugin.setStyle(style);
        }
    }

    /**
     * It will also update plugin setTextColor if plugin is connected.
     */
    public void setTextColor(int color) {
        updateClockColor();
    }

    public void setShowCurrentUserTime(boolean showCurrentUserTime) {
        mClockView.setShowCurrentUserTime(showCurrentUserTime);
        mClockViewBold.setShowCurrentUserTime(showCurrentUserTime);
        mShowCurrentUserTime = showCurrentUserTime;
    }

    public void setTextSize(int unit, float size) {
        mClockView.setTextSize(unit, size);
    }

    public void setFormat12Hour(CharSequence format) {
        mClockView.setFormat12Hour(format);
        mClockViewBold.setFormat12Hour(format);
    }

    public void setFormat24Hour(CharSequence format) {
        mClockView.setFormat24Hour(format);
        mClockViewBold.setFormat24Hour(format);
    }

    /**
     * Set the amount (ratio) that the device has transitioned to doze.
     *
     * @param darkAmount Amount of transition to doze: 1f for doze and 0f for awake.
     */
    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        if (mClockPlugin != null) {
            mClockPlugin.setDarkAmount(darkAmount);
        }
        updateBigClockAlpha();
    }

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        if (hasVisibleNotifications == mHasVisibleNotifications) {
            return;
        }
        mHasVisibleNotifications = hasVisibleNotifications;
        if (mDarkAmount == 0f && mBigClockContainer != null) {
            // Starting a fade transition since the visibility of the big clock will change.
            TransitionManager.beginDelayedTransition(mBigClockContainer,
                    new Fade().setDuration(KeyguardSliceView.DEFAULT_ANIM_DURATION / 2).addTarget(
                            mBigClockContainer));
        }
        updateBigClockAlpha();
    }

    public Paint getPaint() {
        return mClockView.getPaint();
    }

    public int getCurrentTextColor() {
        return mClockView.getCurrentTextColor();
    }

    public float getTextSize() {
        return mClockView.getTextSize();
    }

    /**
     * Returns the preferred Y position of the clock.
     *
     * @param totalHeight Height of the parent container.
     * @return preferred Y position.
     */
    int getPreferredY(int totalHeight) {
        if (mClockPlugin != null) {
            return mClockPlugin.getPreferredY(totalHeight);
        } else {
            return totalHeight / 2;
        }
    }

    /**
     * Refresh the time of the clock, due to either time tick broadcast or doze time tick alarm.
     */
    public void refresh() {
        mClockView.refresh();
        mClockViewBold.refresh();
        if (mClockPlugin != null) {
            mClockPlugin.onTimeTick();
        }
        if (Build.IS_DEBUGGABLE) {
            // Log for debugging b/130888082 (sysui waking up, but clock not updating)
            Log.d(TAG, "Updating clock: " + mClockView.getText().toString()
                    .replaceAll("[^\\x00-\\x7F]", ":"));
        }
    }

    /**
     * Notifies that the time zone has changed.
     */
    public void onTimeZoneChanged(TimeZone timeZone) {
        if (mClockPlugin != null) {
            mClockPlugin.onTimeZoneChanged(timeZone);
        }
    }

    private void updateColors() {
        ColorExtractor.GradientColors colors = mSysuiColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        mSupportsDarkText = colors.supportsDarkText();
        mColorPalette = colors.getColorPalette();
        if (mClockPlugin != null) {
            mClockPlugin.setColorPalette(mSupportsDarkText, mColorPalette);
        }
    }

    private void updateBigClockVisibility() {
        if (mBigClockContainer == null) {
            return;
        }
        final boolean inDisplayState = mStatusBarState == StatusBarState.KEYGUARD
                || mStatusBarState == StatusBarState.SHADE_LOCKED;
        final int visibility =
                inDisplayState && mBigClockContainer.getChildCount() != 0 ? View.VISIBLE
                        : View.GONE;
        if (mBigClockContainer.getVisibility() != visibility) {
            mBigClockContainer.setVisibility(visibility);
        }
    }

    private void updateBigClockAlpha() {
        if (mBigClockContainer != null) {
            final float alpha = mHasVisibleNotifications ? mDarkAmount : 1f;
            mBigClockContainer.setAlpha(alpha);
            if (alpha == 0f) {
                mBigClockContainer.setVisibility(INVISIBLE);
            } else if (mBigClockContainer.getVisibility() == INVISIBLE) {
                mBigClockContainer.setVisibility(VISIBLE);
            }
        }
    }

    public void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 34;
        if (lockClockFont == 0) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
	        mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 1) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            }
        }
        if (lockClockFont == 2) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 3) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            }
        }
        if (lockClockFont == 4) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 5) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 6) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 7) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 8) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 9) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 10) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            }
        }
        if (lockClockFont == 11) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            }
        }
        if (lockClockFont == 12) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 13) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 14) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 15) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 16) {
            mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 17) {
            mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 18) {
            mClockView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 19) {
            mClockView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
            mClockViewBold.setTypeface(Typeface.create("cursive", Typeface.BOLD));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("cursive", Typeface.BOLD));
            }
        }
        if (lockClockFont == 20) {
            mClockView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("casual", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("casual", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 21) {
            mClockView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 22) {
            mClockView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            mClockViewBold.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            }
        }
        if (lockClockFont == 23) {
            mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD));
            mClockViewBold.setTypeface(Typeface.create("serif", Typeface.BOLD));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("serif", Typeface.BOLD));
            }
        }
        if (lockClockFont == 24) {
            mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            mClockViewBold.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            }
        }
        if (lockClockFont == 25) {
            mClockView.setTypeface(Typeface.create("accuratist", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("accuratist", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("accuratist", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 26) {
            mClockView.setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 27) {
            mClockView.setTypeface(Typeface.create("amarante", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("amarante", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("amarante", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 28) {
            mClockView.setTypeface(Typeface.create("bariol", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("bariol", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("bariol", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 29) {
            mClockView.setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 30) {
            mClockView.setTypeface(Typeface.create("cocon", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("cocon", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("cocon", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 31) {
            mClockView.setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 32) {
            mClockView.setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 33) {
            mClockView.setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 34) {
            mClockView.setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 35) {
            mClockView.setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 36) {
            mClockView.setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 37) {
            mClockView.setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 38) {
            mClockView.setTypeface(Typeface.create("lato", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("lato", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("lato", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 39) {
            mClockView.setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 40) {
            mClockView.setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 41) {
            mClockView.setTypeface(Typeface.create("nunito", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("nunito", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("nunito", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 42) {
            mClockView.setTypeface(Typeface.create("quando", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("quando", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("quando", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 43) {
            mClockView.setTypeface(Typeface.create("redressed", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("redressed", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("redressed", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 44) {
            mClockView.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 45) {
            mClockView.setTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("robotocondensed", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 46) {
            mClockView.setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 47) {
            mClockView.setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 48) {
            mClockView.setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 49) {
            mClockView.setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 50) {
            mClockView.setTypeface(Typeface.create("storopia", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("storopia", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("storopia", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 51) {
            mClockView.setTypeface(Typeface.create("surfer", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("surfer", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("surfer", Typeface.NORMAL));
            }
        }
        if (lockClockFont == 52) {
            mClockView.setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
            mClockViewBold.setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
            if (mClockPlugin != null) {
                mClockPlugin.setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
            }
        }
    }

    public void refreshclocksize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockSize = isPrimary ? getLockClockSize() : 78;

        if (lockClockSize == 65) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
        } else if (lockClockSize == 66) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
        } else if (lockClockSize == 66) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
        } else if (lockClockSize == 68) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
        } else if (lockClockSize == 69) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
        } else if (lockClockSize == 70) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
        } else if (lockClockSize == 71) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
        } else if (lockClockSize == 72) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
        } else if (lockClockSize == 73) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
        } else if (lockClockSize == 74) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
        } else if (lockClockSize == 75) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
        } else if (lockClockSize == 76) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
        } else if (lockClockSize == 77) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
        } else if (lockClockSize == 78) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78));
        } else if (lockClockSize == 79) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
        } else if (lockClockSize == 80) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
        } else if (lockClockSize == 81) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
        } else if (lockClockSize == 82) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
        } else if (lockClockSize == 83) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
        } else if (lockClockSize == 84) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
        }  else if (lockClockSize == 85) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
        } else if (lockClockSize == 86) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
        } else if (lockClockSize == 87) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
        } else if (lockClockSize == 88) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
        } else if (lockClockSize == 89) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
        } else if (lockClockSize == 90) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
        } else if (lockClockSize == 91) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
        } else if (lockClockSize == 92) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
        }  else if (lockClockSize == 93) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
        } else if (lockClockSize == 94) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
        } else if (lockClockSize == 95) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
        } else if (lockClockSize == 96) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
        } else if (lockClockSize == 97) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
        } else if (lockClockSize == 98) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
        } else if (lockClockSize == 99) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
        } else if (lockClockSize == 100) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
        } else if (lockClockSize == 101) {
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
        mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
        }
    }

    /**
     * Sets if the keyguard slice is showing a center-aligned header. We need a smaller clock in
     * these cases.
     */
    void setKeyguardShowingHeader(boolean hasHeader) {
        if (mShowingHeader == hasHeader) {
            return;
        }
        mShowingHeader = hasHeader;
        if (hasCustomClock() || !mKeyguardTransitionAnimations) {
            return;
        }

        float smallFontSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.widget_small_font_size);
        float bigFontSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.widget_big_font_size);
        mClockTransition.setScale(smallFontSize / bigFontSize);
        mBoldClockTransition.setScale(bigFontSize / smallFontSize);

        // End any current transitions before starting a new transition so that the new transition
        // starts from a good state instead of a potentially bad intermediate state arrived at
        // during a transition animation.
        TransitionManager.endTransitions((ViewGroup) mClockView.getParent());

        if (hasHeader) {
            // After the transition, make the default clock GONE so that it doesn't make the
            // KeyguardStatusView appear taller in KeyguardClockPositionAlgorithm and elsewhere.
            mTransition.addListener(new TransitionListenerAdapter() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    super.onTransitionEnd(transition);
                    // Check that header is actually showing. I saw issues where this event was
                    // fired after the big clock transitioned back to visible, which causes the time
                    // to completely disappear.
                    if (mShowingHeader) {
                        mClockView.setVisibility(View.GONE);
                    }
                    transition.removeListener(this);
                }
            });
        }

        TransitionManager.beginDelayedTransition((ViewGroup) mClockView.getParent(), mTransition);
        mClockView.setVisibility(hasHeader ? View.INVISIBLE : View.VISIBLE);
        mClockViewBold.setVisibility(hasHeader ? View.VISIBLE : View.INVISIBLE);
        int paddingBottom = mContext.getResources().getDimensionPixelSize(hasHeader
                ? R.dimen.widget_vertical_padding_clock : R.dimen.title_clock_padding);
        mClockView.setPadding(mClockView.getPaddingLeft(), mClockView.getPaddingTop(),
                mClockView.getPaddingRight(), paddingBottom);
        mClockViewBold.setPadding(mClockViewBold.getPaddingLeft(), mClockViewBold.getPaddingTop(),
                mClockViewBold.getPaddingRight(), paddingBottom);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    ClockManager.ClockChangedListener getClockChangedListener() {
        return mClockChangedListener;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    StatusBarStateController.StateListener getStateListener() {
        return mStateListener;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardClockSwitch:");
        pw.println("  mClockPlugin: " + mClockPlugin);
        pw.println("  mClockView: " + mClockView);
        pw.println("  mClockViewBold: " + mClockViewBold);
        pw.println("  mSmallClockFrame: " + mSmallClockFrame);
        pw.println("  mBigClockContainer: " + mBigClockContainer);
        pw.println("  mKeyguardStatusArea: " + mKeyguardStatusArea);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mShowingHeader: " + mShowingHeader);
        pw.println("  mSupportsDarkText: " + mSupportsDarkText);
        pw.println("  mColorPalette: " + Arrays.toString(mColorPalette));
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (key.equals(KEYGUARD_TRANSISITION_ANIMATIONS)) {
            mKeyguardTransitionAnimations = newValue == null || newValue.equals("1");
            if (!mKeyguardTransitionAnimations) {
                // reset to default before we disable transitions
                if (mClockPlugin == null) {
                    mClockView.setVisibility(View.VISIBLE);
                    mClockViewBold.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    /**
     * {@link Visibility} transformation that scales the view while it is disappearing/appearing and
     * transitions suddenly at a cutoff fraction during the animation.
     */
    private class ClockVisibilityTransition extends android.transition.Visibility {

        private static final String PROPNAME_VISIBILITY = "systemui:keyguard:visibility";

        private float mCutoff;
        private float mScale;

        /**
         * Constructs a transition that switches between visible/invisible at a cutoff and scales in
         * size while appearing/disappearing.
         */
        ClockVisibilityTransition() {
            setCutoff(1f);
            setScale(1f);
        }

        /**
         * Sets the transition point between visible/invisible.
         *
         * @param cutoff The fraction in [0, 1] when the view switches between visible/invisible.
         * @return This transition object
         */
        public ClockVisibilityTransition setCutoff(float cutoff) {
            mCutoff = cutoff;
            return this;
        }

        /**
         * Sets the scale factor applied while appearing/disappearing.
         *
         * @param scale Scale factor applied while appearing/disappearing. When factor is less than
         *              one, the view will shrink while disappearing. When it is greater than one,
         *              the view will expand while disappearing.
         * @return This transition object
         */
        public ClockVisibilityTransition setScale(float scale) {
            mScale = scale;
            return this;
        }

        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            captureVisibility(transitionValues);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            captureVisibility(transitionValues);
        }

        private void captureVisibility(TransitionValues transitionValues) {
            transitionValues.values.put(PROPNAME_VISIBILITY,
                    transitionValues.view.getVisibility());
        }

        @Override
        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            if (!sceneRoot.isShown()) {
                return null;
            }
            final float cutoff = mCutoff;
            final int startVisibility = View.INVISIBLE;
            final int endVisibility = (int) endValues.values.get(PROPNAME_VISIBILITY);
            final float startScale = mScale;
            final float endScale = 1f;
            return createAnimator(view, cutoff, startVisibility, endVisibility, startScale,
                    endScale);
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            if (!sceneRoot.isShown()) {
                return null;
            }
            final float cutoff = 1f - mCutoff;
            final int startVisibility = View.VISIBLE;
            final int endVisibility = (int) endValues.values.get(PROPNAME_VISIBILITY);
            final float startScale = 1f;
            final float endScale = mScale;
            return createAnimator(view, cutoff, startVisibility, endVisibility, startScale,
                    endScale);
        }

        private Animator createAnimator(View view, float cutoff, int startVisibility,
                int endVisibility, float startScale, float endScale) {
            view.setPivotY(view.getHeight() - view.getPaddingBottom());
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(animation -> {
                final float fraction = animation.getAnimatedFraction();
                if (fraction > cutoff) {
                    view.setVisibility(endVisibility);
                }
                final float scale = MathUtils.lerp(startScale, endScale, fraction);
                view.setScaleX(scale);
                view.setScaleY(scale);
            });
            animator.addListener(new KeepAwakeAnimationListener(getContext()) {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    view.setVisibility(startVisibility);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    animation.removeListener(this);
                }
            });
            addListener(new TransitionListenerAdapter() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    view.setVisibility(endVisibility);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    transition.removeListener(this);
                }
            });
            return animator;
        }
    }
}
