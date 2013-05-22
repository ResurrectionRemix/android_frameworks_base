
package com.android.systemui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;

import com.android.internal.util.aokp.BackgroundAlphaColorDrawable;
import com.android.systemui.statusbar.NavigationBarView;
import com.android.systemui.statusbar.phone.PanelBar;

import java.util.List;

public class TransparencyManager {

    public static final float KEYGUARD_ALPHA = 0.44f;

    private static final String TAG = TransparencyManager.class.getSimpleName();

    NavigationBarView mNavbar;
    PanelBar mStatusbar;

    SomeInfo mNavbarInfo = new SomeInfo();
    SomeInfo mStatusbarInfo = new SomeInfo();

    final Context mContext;

    Handler mHandler = new Handler();

    boolean mIsHomeShowing;
    boolean mIsKeyguardShowing;

    KeyguardManager km;
    ActivityManager am;

    private static class SomeInfo {
        ValueAnimator anim;
        int color;
        float keyguardAlpha;
        float homeAlpha;
        boolean tempDisable;
    }

    private final Runnable updateTransparencyRunnable = new Runnable() {
        @Override
        public void run() {
            doTransparentUpdate();
        }
    };

    public TransparencyManager(Context context) {
        mContext = context;

        km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                update();
            }
        }, intentFilter);

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    public void update() {
        mHandler.removeCallbacks(updateTransparencyRunnable);
        mHandler.postDelayed(updateTransparencyRunnable, 100);
    }

    public void setNavbar(NavigationBarView n) {
        mNavbar = n;
    }

    public void setStatusbar(PanelBar s) {
        mStatusbar = s;
    }

    public void setTempDisableStatusbarState(boolean state) {
        mStatusbarInfo.tempDisable = state;
    }

    public void setTempNavbarState(boolean state) {
        mNavbarInfo.tempDisable = state;
    }

    private ValueAnimator createAnimation(final SomeInfo info, View v) {
        if (info.anim != null) {
            info.anim.cancel();
            info.anim = null;
        }

        float a = 1;

        if (info.tempDisable) {
            info.tempDisable = false;
        } else if (mIsKeyguardShowing) {
            a = info.keyguardAlpha;
        } else if (mIsHomeShowing) {
            a = info.homeAlpha;
        }

        final float alpha = a;
        ValueAnimator anim = null;
        if (v.getBackground() instanceof BackgroundAlphaColorDrawable) {
            final BackgroundAlphaColorDrawable bg = (BackgroundAlphaColorDrawable) v
                    .getBackground();
            anim = ValueAnimator.ofObject(new ArgbEvaluator(), info.color,
                    BackgroundAlphaColorDrawable.applyAlphaToColor(bg.getBgColor(), alpha));
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    info.color = (Integer) animation.getAnimatedValue();
                    bg.setColor(info.color);
                }
            });
        } else {
            // custom image is set by the theme, let's just apply the alpha if we can.
            v.getBackground().setAlpha(BackgroundAlphaColorDrawable.floatAlphaToInt(alpha));
            return null;
        }
        anim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }
            @Override
            public void onAnimationRepeat(Animator animation) {
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                info.anim = null;
            }
            @Override
            public void onAnimationCancel(Animator animation) {
                info.anim = null;
            }
        });
        info.anim = anim;
        return anim;
    }

    private void doTransparentUpdate() {
        mIsKeyguardShowing = isKeyguardShowing();
        mIsHomeShowing = isLauncherShowing();

        ValueAnimator navAnim = null, sbAnim = null;
        if (mNavbar != null) {
            navAnim = createAnimation(mNavbarInfo, mNavbar);
        }
        if (mStatusbar != null) {
            sbAnim = createAnimation(mStatusbarInfo, mStatusbar);
        }
        if (navAnim != null && sbAnim != null) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(navAnim, sbAnim);
            set.start();
        } else {
            if(navAnim != null) {
                navAnim.start();
            } else if(sbAnim != null) {
                sbAnim.start();
            }
        }
    }

    private boolean isLauncherShowing() {
        try {
            final List<ActivityManager.RecentTaskInfo> recentTasks = am
                    .getRecentTasksForUser(
                            1, ActivityManager.RECENT_WITH_EXCLUDED,
                            UserHandle.CURRENT.getIdentifier());
            if (recentTasks.size() > 0) {
                ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(0);
                Intent intent = new Intent(recentInfo.baseIntent);
                if (recentInfo.origActivity != null) {
                    intent.setComponent(recentInfo.origActivity);
                }
                if (isCurrentHomeActivity(intent.getComponent(), null)) {
                    return true;
                }
            }
        } catch(Exception ignore) {
        }
        return false;
    }

    private boolean isKeyguardShowing() {
        if (km == null)
            return false;
        return km.isKeyguardLocked();
    }

    private boolean isCurrentHomeActivity(ComponentName component, ActivityInfo homeInfo) {
        if (homeInfo == null) {
            homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .resolveActivityInfo(mContext.getPackageManager(), 0);
        }
        return homeInfo != null
                && homeInfo.packageName.equals(component.getPackageName())
                && homeInfo.name.equals(component.getClassName());
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_ALPHA_CONFIG), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_ALPHA_CONFIG), false,
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

        final float defaultAlpha = new Float(mContext.getResources().getInteger(
                R.integer.navigation_bar_transparency) / 255);
        String alphas[];
        String settingValue = Settings.System.getString(resolver,
                Settings.System.NAVIGATION_BAR_ALPHA_CONFIG);
        Log.e(TAG, "nav bar config: " + settingValue);
        if (settingValue == null) {
            mNavbarInfo.homeAlpha = defaultAlpha;
            mNavbarInfo.keyguardAlpha = KEYGUARD_ALPHA;
        } else {
            alphas = settingValue.split(";");
            if (alphas != null && alphas.length == 2) {
                mNavbarInfo.homeAlpha = Float.parseFloat(alphas[0]) / 255;
                mNavbarInfo.keyguardAlpha = Float.parseFloat(alphas[1]) / 255;
            }
        }

        settingValue = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_ALPHA_CONFIG);
        Log.e(TAG, "status bar config: " + settingValue);
        if (settingValue == null) {
            mStatusbarInfo.homeAlpha = defaultAlpha;
            mStatusbarInfo.keyguardAlpha = KEYGUARD_ALPHA;
        } else {
            alphas = settingValue.split(";");
            if (alphas != null && alphas.length == 2) {
                mStatusbarInfo.homeAlpha = Float.parseFloat(alphas[0]) / 255;
                mStatusbarInfo.keyguardAlpha = Float.parseFloat(alphas[1]) / 255;
            }
        }

        update();
    }
}
