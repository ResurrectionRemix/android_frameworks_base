/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import android.app.ActivityManager;
import android.animation.ValueAnimator;
import android.content.Context;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.res.ColorUtils;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.database.ContentObserver;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.BitmapShader;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.internal.util.rr.ImageHelperQS;
import com.android.systemui.omni.StatusBarHeaderMachine;
import com.android.systemui.qs.customize.QSCustomizer;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.Dependency;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper view with background which contains {@link QSPanel} and {@link BaseStatusBarHeader}
 */
public class QSContainerImpl extends FrameLayout implements
        StatusBarHeaderMachine.IStatusBarHeaderMachineObserver,
        Tunable {

    private final Point mSizePoint = new Point();

    private int mHeightOverride = -1;
    private QSPanel mQSPanel;
    private View mQSDetail;
    private QuickStatusBarHeader mHeader;
    private float mQsExpansion;
    private QSCustomizer mQSCustomizer;
    private View mQSFooter;

    private ViewGroup mBackground;
    private ImageView mQsBackgroundImage;
    private View mBackgroundGradient;
    private View mStatusBarBackground;

    private int mSideMargins;
    private boolean mQsDisabled;

    // omni additions start
    private Drawable mQsBackGroundNew;
    private boolean mQsBgNewEnabled;
    private Drawable mQsHeaderBackGround;
    private boolean mQsBackgroundBlur;

    private boolean mHeaderImageEnabled;
    private ImageView mBackgroundImage;
    private StatusBarHeaderMachine mStatusBarHeaderMachine;
    private Drawable mCurrentBackground;
    private boolean mLandscape;
    private boolean mForceHideQsStatusBar;
    private boolean mIsAlpha;
    private boolean mStatusBarBgTransparent;
    private static final String QS_STATUS_BAR_BG_TRANSPARENCY =              
          "system:" + Settings.System.QS_STATUS_BAR_BG_TRANSPARENCY;
    private static final String QS_HIDE_GRADIENT =              
          "system:" + Settings.System.QS_HIDE_GRADIENT;
    private boolean mQsBackGroundColorRGB;
    private ValueAnimator mDiscoAnim;

    private Drawable mQsBackGround;
    private int mQsDiscoDuration;
    private boolean mHideGradient;


    private int mHeaderImageHeight;
    private int mQsBackGroundAlpha;
    private boolean mQsBackGroundType;
    private boolean mQsBackgroundAlpha;
    private int mQsBackGroundColor;
    private int mQsBackGroundColorWall;
    private int mCurrentColor;
    private boolean mSetQsFromWall;
    private boolean mSetQsFromAccent;
    private boolean mSetQsFromResources;
    private SysuiColorExtractor mColorExtractor;

    private IOverlayManager mOverlayManager;

    private Context mContext;

    private static final String QS_PANEL_FILE_IMAGE = "custom_file_qs_panel_image";

    public QSContainerImpl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        mStatusBarHeaderMachine = new StatusBarHeaderMachine(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQSPanel = findViewById(R.id.quick_settings_panel);
        mQSDetail = findViewById(R.id.qs_detail);
        mHeader = findViewById(R.id.header);
        mQSCustomizer = findViewById(R.id.qs_customize);
        mQSFooter = findViewById(R.id.qs_footer);
        mBackground = findViewById(R.id.quick_settings_background);
        mQsBackgroundImage = findViewById(R.id.qs_image_view);
        mStatusBarBackground = findViewById(R.id.quick_settings_status_bar_background);
        mBackgroundGradient = findViewById(R.id.quick_settings_gradient_view);
        mSideMargins = getResources().getDimensionPixelSize(R.dimen.notification_side_paddings);
        mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        mQsBackGroundNew = getContext().getDrawable(R.drawable.qs_background_primary_new);
        mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        mBackgroundImage = findViewById(R.id.qs_header_image_view);
        mBackgroundImage.setClipToOutline(true);
        mColorExtractor = Dependency.get(SysuiColorExtractor.class);
        mForceHideQsStatusBar = mContext.getResources().getBoolean(R.bool.qs_status_bar_hidden);
        updateResources();
        updateSettings();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setMargins();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStatusBarHeaderMachine.addObserver(this);
        mStatusBarHeaderMachine.updateEnablement();
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, QS_STATUS_BAR_BG_TRANSPARENCY);
        tunerService.addTunable(this, QS_HIDE_GRADIENT);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mStatusBarHeaderMachine.removeObserver(this);
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.removeTunable(this);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setBackgroundGradientVisibility(newConfig);
        mLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;

        updateResources();
        updateStatusbarVisibility();
        mSizePoint.set(0, 0); // Will be retrieved on next measure pass.
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System
                            .getUriFor(Settings.System.QS_PANEL_BG_ALPHA), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                            .getUriFor(Settings.System.OMNI_STATUS_BAR_CUSTOM_HEADER_SHADOW), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_RGB), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_RGB_DURATION), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_NEW_BG_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_COLOR), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_COLOR_WALL), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_USE_WALL), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_USE_ACCENT), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_USE_FW), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_TYPE_BACKGROUND), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_CUSTOM_IMAGE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_CUSTOM_IMAGE_BLUR), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_IMAGE_BLUR_INTENSITY), false,
                    this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

   private void updateSettings() {
        ContentResolver resolver = getContext().getContentResolver();
        mQsBackGroundColorRGB = Settings.System.getInt(resolver,
                Settings.System.QS_PANEL_BG_RGB, 0) == 1;
        mQsDiscoDuration = Settings.System.getIntForUser(resolver,
                Settings.System.QS_PANEL_BG_RGB_DURATION, 5,
                UserHandle.USER_CURRENT);
        String imageUri = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.QS_PANEL_CUSTOM_IMAGE,
                UserHandle.USER_CURRENT);
        int userQsWallColorSetting = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_PANEL_BG_USE_WALL, 0, UserHandle.USER_CURRENT);
        mSetQsFromWall = userQsWallColorSetting == 1;
        int userQsFwSetting = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_PANEL_BG_USE_FW, 1, UserHandle.USER_CURRENT);
        mSetQsFromResources = userQsFwSetting == 1;
        mSetQsFromAccent = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_PANEL_BG_USE_ACCENT, 0, UserHandle.USER_CURRENT) == 1;
        mQsBackGroundAlpha = Settings.System.getIntForUser(resolver,
                Settings.System.QS_PANEL_BG_ALPHA, 255,
                UserHandle.USER_CURRENT);
        if (mQsBackGroundAlpha < 255 ) {
            mIsAlpha = true;
        } else {
            mIsAlpha = false;

        }
        mQsBgNewEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_NEW_BG_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mQsBackGroundColor = ColorUtils.getValidQsColor(Settings.System.getIntForUser(resolver,
                Settings.System.QS_PANEL_BG_COLOR, ColorUtils.genRandomQsColor(),
                UserHandle.USER_CURRENT));
        mQsBackGroundColorWall = ColorUtils.getValidQsColor(Settings.System.getIntForUser(resolver,
                Settings.System.QS_PANEL_BG_COLOR_WALL, ColorUtils.genRandomQsColor(),
                UserHandle.USER_CURRENT));
        WallpaperColors systemColors = null;
        if (mColorExtractor != null) {
            systemColors = mColorExtractor.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
        }
        mCurrentColor = mSetQsFromAccent
                ? getContext().getResources().getColor(R.color.accent_device_default_light)
                : mSetQsFromWall ? mQsBackGroundColorWall : mQsBackGroundColor;
        post(new Runnable() {
            public void run() {
                setQsBackground();
            }
        });
        if (imageUri != null) {
            saveCustomFileFromString(Uri.parse(imageUri), QS_PANEL_FILE_IMAGE);
        }
        updateHeaderImageHeight();
        updateResources();
        updateStatusbarVisibility();
   }

    public void setQCustomBG() {
        BitmapDrawable currentImage = null;
        if (mQsBackGroundType) {
            currentImage = getCustomImageFromString(QS_PANEL_FILE_IMAGE);
        }
        if (currentImage != null && mQsBackGroundType) {
            int width = mQSPanel.getWidth();
            int height = mQSPanel.getHeight() + mQSFooter.getHeight();

            Bitmap bitmap = mQsBackgroundBlur ? ImageHelperQS.getBlurredImage(mContext, currentImage.getBitmap(),getblurRadius()) : currentImage.getBitmap();
            Bitmap toCenter = ImageHelperQS.scaleCenterCrop(bitmap, width, height);
            BitmapDrawable bDrawable = new BitmapDrawable(mContext.getResources(),
                            ImageHelperQS.getRoundedCornerBitmap(toCenter, 15, width, height, mCurrentColor));

            mQsBackGround = new InsetDrawable(bDrawable, 0, 0, 0, mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.qs_background_inset));

            Drawable background = mContext.getDrawable(R.drawable.qs_header_image_view_outline);
            InsetDrawable clipBackground = new InsetDrawable(background, 0, 0, 0, mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.qs_background_inset));

            mBackground.setBackground(mQsBackGround);
            mBackground.setClipToOutline(true);
        }
    }

    private float getblurRadius() {
        return 0.25f * ((float) Settings.System.getIntForUser(mContext.getContentResolver(),
               Settings.System.QS_IMAGE_BLUR_INTENSITY, 30,
               UserHandle.USER_CURRENT));
    }

    private void setQsBackground() {
        ContentResolver resolver = getContext().getContentResolver();
        mQsBackGroundType = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_PANEL_TYPE_BACKGROUND, 0, UserHandle.USER_CURRENT) == 1;
        mQsBackgroundBlur = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.QS_PANEL_CUSTOM_IMAGE_BLUR, 1, UserHandle.USER_CURRENT) == 1;
       if (mQsBackGroundColorRGB) {
           startDiscoMode();
       } else {
           stopDiscoMode();
           if (mQsBackGroundType) {
              setQCustomBG();
            } else {
              if (mSetQsFromResources) {
                  if (!mQsBgNewEnabled) {
                       mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
                       mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
                  } else {
                       mQsBackGroundNew = getContext().getDrawable(R.drawable.qs_background_primary_new);
                       mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_new);
                  }
                  try {
                       mOverlayManager.setEnabled("com.android.systemui.qstheme.color",
                        false, ActivityManager.getCurrentUser());
                  } catch (RemoteException e) {
                      Log.w("QSContainerImpl", "Can't change qs theme", e);
                  }
             } else {
                if (!mQsBgNewEnabled) {
                     mQsBackGround.setColorFilter(mCurrentColor, PorterDuff.Mode.SRC_ATOP);
                } else {
                     mQsBackGroundNew.setColorFilter(mCurrentColor, PorterDuff.Mode.SRC_ATOP);
                     mQsHeaderBackGround.setColorFilter(mCurrentColor, PorterDuff.Mode.SRC_ATOP);
                }
	            try {
                   mOverlayManager.setEnabled("com.android.systemui.qstheme.color",
                        true, ActivityManager.getCurrentUser());
                 } catch (RemoteException e) {
                   Log.w("QSContainerImpl", "Can't change qs theme", e);
                 }
            }
            if (!mQsBgNewEnabled) {
                mBackground.setBackground(mQsBackGround);
            } else {
                mBackground.setBackground(mQsBackGroundNew);
            }
         }
            mQsHeaderBackGround.setAlpha(mQsBackGroundAlpha);
            if (!mQsBgNewEnabled) {
                mQsBackGround.setAlpha(mQsBackGroundAlpha);
            } else {
                mQsBackGroundNew.setAlpha(mQsBackGroundAlpha);
            }
      }
   }


   private void stopDiscoMode() {
        if (mDiscoAnim != null)
            mDiscoAnim.cancel();
        mDiscoAnim = null;
    }

    private void startDiscoMode() {
        final float from = 0f;
        final float to = 360f;
        stopDiscoMode();
        mDiscoAnim = ValueAnimator.ofFloat(0, 1);
        final float[] hsl = {0f, 1f, 0.5f};
        mDiscoAnim.setDuration(mQsDiscoDuration *1000);
        mDiscoAnim.setRepeatCount(ValueAnimator.INFINITE);
        mDiscoAnim.setRepeatMode(ValueAnimator.RESTART);
        mDiscoAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                hsl[0] = from + (to - from)*animation.getAnimatedFraction();
                mQsBackGround.setColorFilter(com.android.internal.graphics.ColorUtils.HSLToColor(hsl), PorterDuff.Mode.SRC_ATOP);
                if (mQsBackGround != null && mBackground != null) {
                    mBackground.setBackground(mQsBackGround);
                }
            }
        });
        mDiscoAnim.start();
    }


    @Override
    public boolean performClick() {
        // Want to receive clicks so missing QQS tiles doesn't cause collapse, but
        // don't want to do anything with them.
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // QSPanel will show as many rows as it can (up to TileLayout.MAX_ROWS) such that the
        // bottom and footer are inside the screen.
        Configuration config = getResources().getConfiguration();
        boolean navBelow = config.smallestScreenWidthDp >= 600
                || config.orientation != Configuration.ORIENTATION_LANDSCAPE;
        MarginLayoutParams layoutParams = (MarginLayoutParams) mQSPanel.getLayoutParams();

        // The footer is pinned to the bottom of QSPanel (same bottoms), therefore we don't need to
        // subtract its height. We do not care if the collapsed notifications fit in the screen.
        int maxQs = getDisplayHeight() - layoutParams.topMargin - layoutParams.bottomMargin
                - getPaddingBottom();
        if (navBelow) {
            maxQs -= getResources().getDimensionPixelSize(R.dimen.navigation_bar_height);
        }
        // Measure with EXACTLY. That way, PagedTileLayout will only use excess height and will be
        // measured last, after other views and padding is accounted for.
        mQSPanel.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxQs, MeasureSpec.EXACTLY));
        int width = mQSPanel.getMeasuredWidth();
        int height = layoutParams.topMargin + layoutParams.bottomMargin
                + mQSPanel.getMeasuredHeight() + getPaddingBottom();
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        // QSCustomizer will always be the height of the screen, but do this after
        // other measuring to avoid changing the height of the QS.
        mQSCustomizer.measure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(getDisplayHeight(), MeasureSpec.EXACTLY));
    }


    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        // Do not measure QSPanel again when doing super.onMeasure.
        // This prevents the pages in PagedTileLayout to be remeasured with a different (incorrect)
        // size to the one used for determining the number of rows and then the number of pages.
        if (child != mQSPanel) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateExpansion();
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        setBackgroundGradientVisibility(getResources().getConfiguration());
        mBackground.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
    }

    private void updateResources() {
        int topMargin = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height) + (mHeaderImageEnabled ?
                mHeaderImageHeight : 0);

        int statusBarSideMargin = mHeaderImageEnabled ? mContext.getResources().getDimensionPixelSize(
                R.dimen.qs_header_image_side_margin) : 0;

        int gradientTopMargin = !mHeaderImageEnabled ? mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height) : 0;

        ((LayoutParams) mQSPanel.getLayoutParams()).topMargin = topMargin;
        mQSPanel.setLayoutParams(mQSPanel.getLayoutParams());

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mStatusBarBackground.getLayoutParams();
        lp.height = topMargin;
        lp.setMargins(statusBarSideMargin, 0, statusBarSideMargin, 0);
        mStatusBarBackground.setLayoutParams(lp);

        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) mBackgroundGradient.getLayoutParams();
        mlp.setMargins(0, gradientTopMargin, 0, 0);
        mBackgroundGradient.setLayoutParams(mlp);
        updateGradientbackground();
        setBackgroundGradientVisibility(getResources().getConfiguration());

    }

    public void saveCustomFileFromString(Uri fileUri, String fileName) {
        try {
            final InputStream fileStream = mContext.getContentResolver().openInputStream(fileUri);
            File file = new File(mContext.getFilesDir(), fileName);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = fileStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (IOException e) {
        }
    }

    public BitmapDrawable getCustomImageFromString(String fileName) {
        BitmapDrawable mImage = null;
        File file = new File(mContext.getFilesDir(), fileName);
        if (file.exists()) {
            final Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
            mImage = new BitmapDrawable(mContext.getResources(), ImageHelperQS.resizeMaxDeviceSize(mContext, image));
        }
        return mImage;
    }

    /**
     * Overrides the height of this view (post-layout), so that the content is clipped to that
     * height and the background is set to that height.
     *
     * @param heightOverride the overridden height
     */
    public void setHeightOverride(int heightOverride) {
        mHeightOverride = heightOverride;
        updateExpansion();
    }

    public void updateExpansion() {
        int height = calculateContainerHeight();
        setBottom(getTop() + height);
        mQSDetail.setBottom(getTop() + height);
        // Pin QS Footer to the bottom of the panel.
        mQSFooter.setTranslationY(height - mQSFooter.getHeight());
        mBackground.setTop(mQSPanel.getTop());
        mBackground.setBottom(height);
    }

    protected int calculateContainerHeight() {
        int heightOverride = mHeightOverride != -1 ? mHeightOverride : getMeasuredHeight();
        return mQSCustomizer.isCustomizing() ? mQSCustomizer.getHeight()
                : Math.round(mQsExpansion * (heightOverride - mHeader.getHeight()))
                + mHeader.getHeight();
    }

    private void setBackgroundGradientVisibility(Configuration newConfig) {
        if (newConfig.orientation == ORIENTATION_LANDSCAPE) {
            mBackgroundGradient.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundGradient.setVisibility((mQsDisabled || mHideGradient) ? View.INVISIBLE : View.VISIBLE);
        }
        updateStatusbarVisibility();
    }

    public void setExpansion(float expansion) {
        mQsExpansion = expansion;
        updateExpansion();
    }

    private void setMargins() {
        setMargins(mQSDetail);
        setMargins(mBackground);
        setMargins(mQSFooter);
        mQSPanel.setMargins(mSideMargins);
        mHeader.setMargins(mSideMargins);
    }

    private void setMargins(View view) {
        FrameLayout.LayoutParams lp = (LayoutParams) view.getLayoutParams();
        lp.rightMargin = mSideMargins;
        lp.leftMargin = mSideMargins;
    }

    private int getDisplayHeight() {
        if (mSizePoint.y == 0) {
            getDisplay().getRealSize(mSizePoint);
        }
        return mSizePoint.y;
    }

    @Override
    public void updateHeader(final Drawable headerImage, final boolean force) {
        post(new Runnable() {
            public void run() {
                doUpdateStatusBarCustomHeader(headerImage, force);
            }
        });
    }

    @Override
    public void disableHeader() {
        post(new Runnable() {
            public void run() {
                mCurrentBackground = null;
                mBackgroundImage.setVisibility(View.GONE);
                mHeaderImageEnabled = false;
                updateResources();
                updateStatusbarVisibility();
            }
        });
    }

    @Override
    public void refreshHeader() {
        post(new Runnable() {
            public void run() {
                doUpdateStatusBarCustomHeader(mCurrentBackground, true);
            }
        });
    }

    private void doUpdateStatusBarCustomHeader(final Drawable next, final boolean force) {
        if (next != null) {
            mBackgroundImage.setVisibility(View.VISIBLE);
            mCurrentBackground = next;
            setNotificationPanelHeaderBackground(next, force);
            mHeaderImageEnabled = true;
            updateResources();
            updateStatusbarVisibility();
        } else {
            mCurrentBackground = null;
            mBackgroundImage.setVisibility(View.GONE);
            mHeaderImageEnabled = false;
            updateResources();
            updateStatusbarVisibility();
        }
    }

    private void setNotificationPanelHeaderBackground(final Drawable dw, final boolean force) {
        if (mBackgroundImage.getDrawable() != null && !force) {
            Drawable[] arrayDrawable = new Drawable[2];
            arrayDrawable[0] = mBackgroundImage.getDrawable();
            arrayDrawable[1] = dw;

            TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            mBackgroundImage.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(1000);
        } else {
            mBackgroundImage.setImageDrawable(dw);
        }
        applyHeaderBackgroundShadow();
    }

    private void applyHeaderBackgroundShadow() {
        final int headerShadow = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.OMNI_STATUS_BAR_CUSTOM_HEADER_SHADOW, 0,
                UserHandle.USER_CURRENT);

        if (mCurrentBackground != null) {
            float shadow = headerShadow;
            mBackgroundImage.setImageAlpha(255-headerShadow);
        }
    }

    private void updateStatusbarVisibility() {
        boolean shouldHideStatusbar = (mLandscape || mForceHideQsStatusBar) && !mHeaderImageEnabled;
        mStatusBarBackground.setVisibility(shouldHideStatusbar ? View.INVISIBLE : View.VISIBLE);

    }

    public void  updateGradientbackground() {
        if (mHeaderImageEnabled || mStatusBarBgTransparent) {
            mQsBackgroundAlpha = false;
            mStatusBarBackground.setBackgroundColor(Color.TRANSPARENT);
        } else {
            mQsBackgroundAlpha = true;
            mStatusBarBackground.setBackgroundColor(Color.BLACK);
            if(mQsBackGroundType) {
               post(new Runnable() {
                    public void run() {
                      setQsBackground();
                    }
               });
          } 
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (QS_STATUS_BAR_BG_TRANSPARENCY.equals(key)) {
            mStatusBarBgTransparent = newValue != null && Integer.parseInt(newValue) == 1;
            updateResources();
        } else if (QS_HIDE_GRADIENT.equals(key)) {
            mHideGradient = newValue != null && Integer.parseInt(newValue) == 1;
            updateResources();
        }
    }

    private void updateHeaderImageHeight() {
        int mImageHeight = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, 25,
                UserHandle.USER_CURRENT);
        switch (mImageHeight) {
            case 0:
                mHeaderImageHeight = 0;
                break;
            case 1:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1);
                break;
            case 2:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_2);
                break;
            case 3:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_3);
                break;
            case 4:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_4);
                break;
            case 5:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_5);
                break;
            case 6:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_6);
                break;
            case 7:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_7);
                break;
            case 8:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_8);
                break;
            case 9:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9);
                break;
            case 10:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10);
                break;
            case 11:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11);
                break;
            case 12:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12);
                break;
            case 13:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13);
                break;
            case 14:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14);
                break;
            case 15:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15);
                break;
            case 16:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16);
                break;
            case 17:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17);
                break;
            case 18:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18);
                break;
            case 19:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19);
                break;
            case 20:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20);
                break;
            case 21:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21);
                break;
            case 22:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22);
                break;
            case 23:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23);
                break;
            case 24:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24);
                break;
            case 25:
            default:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25);
                break;
            case 26:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26);
                break;
            case 27:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27);
                break;
            case 28:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28);
                break;
            case 29:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29);
                break;
            case 30:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30);
                break;
            case 31:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31);
                break;
            case 32:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32);
                break;
            case 33:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33);
                break;
            case 34:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_34);
                break;
            case 35:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_35);
                break;
            case 36:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_36);
                break;
            case 37:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_37);
                break;
            case 38:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_38);
                break;
            case 39:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_39);
                break;
            case 40:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_40);
                break;
            case 41:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_41);
                break;
            case 42:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_42);
                break;
            case 43:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_43);
                break;
            case 44:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_44);
                break;
            case 45:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_45);
                break;
            case 46:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_46);
                break;
            case 47:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_47);
                break;
            case 48:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_48);
                break;
            case 49:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_49);
                break;
            case 50:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_50);
                break;
            case 51:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51);
                break;
            case 52:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52);
                break;
            case 53:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53);
                break;
            case 54:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54);
                break;
            case 55:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55);
                break;
            case 56:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56);
                break;
            case 57:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57);
                break;
            case 58:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58);
                break;
            case 59:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59);
                break;
            case 60:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60);
                break;
            case 61:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61);
                break;
            case 62:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62);
                break;
            case 63:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63);
                break;
            case 64:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64);
                break;
            case 65:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65);
                break;
            case 66:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66);
                break;
            case 67:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67);
                break;
            case 68:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68);
                break;
            case 69:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69);
                break;
            case 70:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70);
                break;
            case 71:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71);
                break;
            case 72:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72);
                break;
            case 73:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73);
                break;
            case 74:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74);
                break;
            case 75:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75);
                break;
            case 76:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76);
                break;
            case 77:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77);
                break;
            case 78:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78);
                break;
            case 79:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79);
                break;
            case 80:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80);
                break;
            case 81:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81);
                break;
            case 82:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82);
                break;
            case 83:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83);
                break;
            case 84:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84);
                break;
            case 85:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85);
                break;
            case 86:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86);
                break;
            case 87:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87);
                break;
            case 88:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88);
                break;
            case 89:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89);
                break;
            case 90:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90);
                break;
            case 91:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91);
                break;
            case 92:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92);
                break;
            case 93:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93);
                break;
            case 94:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94);
                break;
            case 95:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95);
                break;
            case 96:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96);
                break;
            case 97:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97);
                break;
            case 98:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98);
                break;
            case 99:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99);
                break;
            case 100:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100);
                break;
        }
    }
}
