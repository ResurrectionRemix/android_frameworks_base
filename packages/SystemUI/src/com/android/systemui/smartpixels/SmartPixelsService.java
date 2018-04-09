package screen.dimmer.pixelfilter;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;


public class FilterService extends Service implements SensorEventListener {
    public static final String LOG = "Pixel Filter"; //NON-NLS

    private WindowManager windowManager;
    private ImageView view = null;
    private Bitmap bmp;

    private boolean destroyed = false;
    private boolean intentProcessed = false;
    public static boolean running = false;
    public static MainActivity gui = null;

    private SensorManager sensors = null;
    private Sensor lightSensor = null;
    private ScreenOffReceiver screenOffReceiver = null;

    private int samsungBackLightValue = 0;
    private String SAMSUNG_BACK_LIGHT_SETTING = "button_key_light"; //NON-NLS

    private int startCounter = 0;

    @Override
    public IBinder onBind(Intent intent) {
    return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        MainActivity guiCopy = gui;
        if (guiCopy != null) {
            guiCopy.updateCheckbox();
        }

        Log.d(LOG, "Service started"); //NON-NLS
        Cfg.Init(this);

        if (Cfg.UseLightSensor) {
            sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            lightSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (lightSensor != null) {
                StartSensor.get().registerListener(sensors, this, lightSensor, 1200000, 1000000);
            }

            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            screenOffReceiver = new ScreenOffReceiver();
            registerReceiver(screenOffReceiver, filter);
        } else {
            startFilter();
        }
        Cfg.WasEnabled = true;
        Cfg.Save(this);
    }

    public void startFilter() {
        if (view != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        view = new ImageView(this);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        bmp = Bitmap.createBitmap(Grids.GridSideSize, Grids.GridSideSize, Bitmap.Config.ARGB_4444);

        updatePattern();
        BitmapDrawable draw = new BitmapDrawable(bmp);
        draw.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        draw.setFilterBitmap(false);
        draw.setAntiAlias(false);
        draw.setTargetDensity(metrics.densityDpi);

        view.setBackground(draw);

        WindowManager.LayoutParams params = getLayoutParams();
        try {
            windowManager.addView(view, params);
        } catch (Exception e) {
            running = false;
            view = null;
            Log.d(LOG, "Permission " + Manifest.permission.SYSTEM_ALERT_WINDOW + " not granted - launching permission activity"); //NON-NLS
            Log.d(LOG, e.toString()); //NON-NLS
            Intent intent = new Intent(this, PermissionActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            stopSelf();
            return;
        }


        startCounter++;
        final int handlerStartCounter = startCounter;
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (view == null || destroyed || handlerStartCounter != startCounter) {
                    return;
                }
                updatePattern();
                view.invalidate();
                if (!destroyed) {
                    handler.postDelayed(this, Grids.ShiftTimeouts[Cfg.ShiftTimeoutIdx]);
                }
            }
        }, Grids.ShiftTimeouts[Cfg.ShiftTimeoutIdx]);

        if (Cfg.SamsungBacklight) {
            try {
                samsungBackLightValue = android.provider.Settings.System.getInt(getContentResolver(), SAMSUNG_BACK_LIGHT_SETTING);
                android.provider.Settings.System.putInt(getContentResolver(), SAMSUNG_BACK_LIGHT_SETTING, 0);
            } catch (Exception e) {
            }
        }
    }

    public void stopFilter() {
        if (view == null) {
            return;
        }

        if (Cfg.SamsungBacklight) {
            try {
                android.provider.Settings.System.putInt(getContentResolver(), SAMSUNG_BACK_LIGHT_SETTING, samsungBackLightValue);
            } catch (Exception e) {
            }
        }

        startCounter++;

        windowManager.removeView(view);
        view = null;
    }

    private WindowManager.LayoutParams getLayoutParams()
    {
        //DisplayMetrics metrics = new DisplayMetrics();
        //windowManager.getDefaultDisplay().getRealMetrics(metrics);
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getRealSize(displaySize);
        Point windowSize = new Point();
        windowManager.getDefaultDisplay().getSize(windowSize);
        displaySize.x += displaySize.x - windowSize.x;
        displaySize.y += displaySize.y - windowSize.y;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                displaySize.x, //metrics.widthPixels, // + getNavigationBarWidth(), //WindowManager.LayoutParams.MATCH_PARENT,
                displaySize.y, //metrics.heightPixels, // + getNavigationBarHeight(), //WindowManager.LayoutParams.MATCH_PARENT,
                0,
                0,
                //Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                //WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        //WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ?
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN : 0) |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        //WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        //WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSPARENT
        );

        params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        params.dimAmount = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE; // View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        //params.gravity = Gravity.DISPLAY_CLIP_HORIZONTAL | Gravity.DISPLAY_CLIP_VERTICAL;
        return params;
    }

    private int getNavigationBarHeight() {
        Resources resources = getResources();
        int id = resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android"); //NON-NLS
        if (id > 0) {
            return resources.getDimensionPixelSize(id) * 2;
        }
        return 0;
    }

    private int getNavigationBarWidth() {
        Resources resources = getResources();
        int id = resources.getIdentifier("navigation_bar_height", "dimen", "android"); //NON-NLS
        if (id > 0) {
            return resources.getDimensionPixelSize(id) * 2;
        }
        return 0;
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        if (Intent.ACTION_DELETE.equals(intent.getAction()) ||
                (intentProcessed && Intent.ACTION_INSERT.equals(intent.getAction()))) {
            Log.d(LOG, "Service got shutdown intent"); //NON-NLS
            Ntf.show(this, false);
            intentProcessed = true;
            Cfg.WasEnabled = false;
            Cfg.Save(this);
            if (!Cfg.PersistentNotification) {
                stopSelf();
                return START_NOT_STICKY;
            } else {
                stopFilter();
                return START_STICKY;
            }
        }
        if (Cfg.PersistentNotification && Intent.ACTION_RUN.equals(intent.getAction())) {
            startFilter();
            Cfg.WasEnabled = true;
            Cfg.Save(this);
        }

        intentProcessed = true;
        Log.d(LOG, "Service got intent " + intent.getAction()); //NON-NLS
        if (running && intent.hasExtra(TaskerActivity.BUNDLE_PATTERN)) {
            Cfg.Pattern = intent.getIntExtra(TaskerActivity.BUNDLE_PATTERN, Cfg.Pattern);
            if (view != null) {
                updatePattern();
                view.invalidate();
            }
        }
        if (running && getString(R.string.intent_brighter).equals(intent.getAction())) {
            if (Cfg.Pattern > 0 && Cfg.Pattern != Grids.PatternIdCustom) {
                Cfg.Pattern--;
            }
            Cfg.Save(this);
            if (view != null) {
                updatePattern();
                view.invalidate();
            }
        }
        if (running && getString(R.string.intent_darker).equals(intent.getAction())) {
            if (Cfg.Pattern + 1 < Grids.Patterns.length && Cfg.Pattern + 1 != Grids.PatternIdCustom) {
                Cfg.Pattern++;
            }
            Cfg.Save(this);
            if (view != null) {
                updatePattern();
                view.invalidate();
            }
        }
        Ntf.show(this, true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyed = true;
        stopFilter();

        if (lightSensor != null) {
            unregisterReceiver(screenOffReceiver);
            sensors.unregisterListener(this, lightSensor);
        }
        Ntf.show(this, false);

        Log.d(LOG, "Service stopped"); //NON-NLS
        running = false;

        MainActivity guiCopy = gui;
        if (guiCopy != null)
            guiCopy.updateCheckbox();
    }

    int getShift() {
        long shift = (System.currentTimeMillis() / Grids.ShiftTimeouts[Cfg.ShiftTimeoutIdx]) % Grids.GridSize;
        return Grids.GridShift[(int)shift];
    }

    void updatePattern() {
        //Log.d(LOG, "Filter pattern " + Cfg.Pattern);
        int shift = getShift();
        int shiftX = shift % Grids.GridSideSize;
        int shiftY = shift / Grids.GridSideSize;
        for (int i = 0; i < Grids.GridSize; i++) {
            int x = (i + shiftX) % Grids.GridSideSize;
            int y = ((i / Grids.GridSideSize) + shiftY) % Grids.GridSideSize;
            int color = (Grids.Patterns[Cfg.Pattern][i] == 0) ? Color.TRANSPARENT : Color.BLACK;
            bmp.setPixel(x, y, color);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] > Cfg.LightSensorValue) {
            stopFilter();
        }
        if (event.values[0] < Cfg.LightSensorValue * 0.6f) {
            startFilter();
        }
    }

    class ScreenOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (lightSensor != null) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    //Log.d(LOG, "Service received screen off, disabling light sensor");
                    sensors.unregisterListener(FilterService.this, lightSensor);
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    //Log.d(LOG, "Service received screen on, enabling light sensor");
                    StartSensor.get().registerListener(sensors, FilterService.this, lightSensor, 1200000, 1000000);
                }
            }
        }
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(LOG, "Screen orientation changed, updating window layout"); //NON-NLS
        WindowManager.LayoutParams params = getLayoutParams();
        windowManager.updateViewLayout(view, params);
    }
}
