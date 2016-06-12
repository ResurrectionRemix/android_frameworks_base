package com.android.systemui.screenshot;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.ListPopupWindow;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.systemui.screenshot.CropImageView;
import com.android.systemui.screenshot.ColorArrayAdapter;
import com.android.systemui.screenshot.ImageArrayAdapter;
import com.android.systemui.R;

import java.io.File;
import java.io.FileOutputStream;

public class ScreenshotEditor extends Service implements View.OnClickListener {

    private Context mContext;

    //Anything the user can see
    private View mainLayout;
    private CropImageView cropView;
    private WindowManager wm;
    private ProgressBar progressBar;
    private ImageButton cropModeButton, cropButton, workModeButton;
    private LinearLayout layoutWorkModeCrop, layoutWorkModeDraw;
    private RelativeLayout buttonBar;

    private String screenshotPath = "";
    static String TAG = "UniversalCropper";
    private int nCropMode = 0;
    private int nOverlayColor;
    private int drawColor;
    private int penSize = 10;
    private int workMode = 0;
    private boolean isShowing = false;
    private boolean isPopUpWorkModeShowing = false;
    private boolean isPopUpColorShowing = false;
    private boolean isPopUpCropModeShowing = false;
    private boolean isPopUpPenSizeShowing = false;
    private boolean receiverRegistered = false;
    Handler mainHandler;
    HandlerThread handlerThread = null;

    CountDownTimer countDownTimer;

    public static final int WORK_MODE_CROP = 0;
    public static String KEY_SERVICE_SWITCH = "service_switch";
    static String KEY_NOTIFICATION_ACCESS_SWITCH = "notification_access_switch";
    static String KEY_CATEGORY_MAIN = "category_main";
    static String KEY_CROP_BEHAVIOR = "crop_behavior";
    static String KEY_DRAW_COLOR = "draw_color";
    static String KEY_CROP_MODE = "crop_mode";
    static String KEY_WORK_MODE = "work_mode";
    static String KEY_CREDITS = "credits";
    static String KEY_PEN_SIZE = "pen_size";

    private SharedPreferences preferences;
    private float mDensity;

    public ScreenshotEditor() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = ScreenshotEditor.this;
        preferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        mDensity = getResources().getDisplayMetrics().density;

        mainHandler = new Handler(getMainLooper());


        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        LinearLayout mLinear = new LinearLayout(getApplicationContext()) {

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    ScreenshotEditor.this.removeView();
                }
                return super.dispatchKeyEvent(event);
            }
        };

        mLinear.setFocusable(true);

        mainLayout = inflater.inflate(R.layout.cropper, mLinear);
        mainLayout.setSystemUiVisibility(getUiOptions());

        buttonBar = (RelativeLayout) mainLayout.findViewById(R.id.buttonBar);
        buttonBar.setVisibility(View.VISIBLE);

        nOverlayColor = getResources().getColor(R.color.crop_action_overlay);

        initButtons();

        progressBar = (ProgressBar) mainLayout.findViewById(R.id.progressBar);

        nCropMode = preferences.getInt(KEY_CROP_MODE, 0);
        if (nCropMode > 2)
            nCropMode = 0;
        setCropMode();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CropImageView.BROADCAST_BUTTON_BAR_VISIBILITY);
        if (!receiverRegistered)
            registerReceiver(receiver, intentFilter);
        receiverRegistered = true;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CropImageView.BROADCAST_BUTTON_BAR_VISIBILITY.equals(intent.getAction()))
                showButtonBar();
        }
    };

    boolean buttonBarVisible = true;

    private void showButtonBar() {
        if (buttonBarVisible)
            buttonBar.setVisibility(View.GONE);
        else buttonBar.setVisibility(View.VISIBLE);
        buttonBarVisible = !buttonBarVisible;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        screenshotPath = intent.getStringExtra("screenshotPath");
        addView();
        return Service.START_STICKY;
    }

    Bitmap screenshot = null;

    private void addView() {
        if (!isShowing)
           initButtons();
        mainHandler.post(new Runnable() {
            public void run() {

                screenshot = BitmapFactory.decodeFile(screenshotPath);
              /*  countDownTimer = new CountDownTimer(10000, 500) {
                    long millis = 0;

                    @Override
                    public void onTick(long millisUntilFinished) {
                        millis = millisUntilFinished;
                        if (screenshot == null)
                            screenshot = BitmapFactory.decodeFile(screenshotPath);
                        else {
                            this.cancel();
                            onFinish();
                        }
                    }

                    @Override
                    public void onFinish() {
                        if (screenshot != null) {
                            cropView.setImageBitmap(screenshot);
                            if (!isShowing)
                                wm.addView(mainLayout, getParams());
                            else
                                wm.updateViewLayout(mainLayout, getParams());
                            isShowing = true;
                        }
                    }
                }.start();*/
		cropView.setImageBitmap(screenshot);
                if (!isShowing)
                    wm.addView(mainLayout, getParams());
                else
                   wm.updateViewLayout(mainLayout, getParams());
                isShowing = true;
            }
        });
    }

    private void removeView() {
        if (isShowing) {
            wm.removeView(mainLayout);
        }
        isShowing = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiverRegistered)
            unregisterReceiver(receiver);
        receiverRegistered = false;
        handlerThread = null;
        mainHandler = null;
        cropView.clearMemory();
        screenshot = null;
        cropView = null;
        System.gc();
    }

    private void initButtons() {
        mainLayout.setSystemUiVisibility(getUiOptions());
        mainLayout.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                mainLayout.setSystemUiVisibility(getUiOptions());
            }
        });
        cropView = (CropImageView) mainLayout.findViewById(R.id.image);
        cropView.setFrameColor(getResources().getColor(R.color.crop_frame));
        cropView.setHandleColor(getResources().getColor(R.color.crop_handle));
        cropView.setGuideColor(getResources().getColor(R.color.crop_guide));
        cropView.setHandleSizeInDp(15);
        cropView.setTouchPaddingInDp(10);
        cropView.setGuideShowMode(CropImageView.ShowMode.SHOW_ON_TOUCH);

        cropModeButton = (ImageButton) mainLayout.findViewById(R.id.cropMode);
        final ListPopupWindow listPopupWindowCropMode = new ListPopupWindow(mContext);
        nCropMode = preferences.getInt(KEY_WORK_MODE, 0);
        ImageArrayAdapter cropModeAdapter = new ImageArrayAdapter(mContext,
                new Integer[]{R.drawable.ic_image_crop_free, R.drawable.ic_image_crop_square, R.drawable.ic_image_crop_circle});
        listPopupWindowCropMode.setAdapter(cropModeAdapter);
        listPopupWindowCropMode.setAnchorView(cropModeButton);
        listPopupWindowCropMode.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                nCropMode = position;
                setCropMode();
                preferences.edit().putInt(KEY_CROP_MODE, nCropMode).apply();
                setCropMode();
                listPopupWindowCropMode.dismiss();
            }
        });
        listPopupWindowCropMode.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                isPopUpCropModeShowing = false;
            }
        });
        cropModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPopUpCropModeShowing) {
                    listPopupWindowCropMode.show();
                    isPopUpCropModeShowing = true;
                }
                else listPopupWindowCropMode.dismiss();
            }
        });
        cropModeButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);
        cropButton = (ImageButton) mainLayout.findViewById(R.id.doneCrop);
        cropButton.setOnClickListener(this);
        cropButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);
        final ImageButton recoverButton = (ImageButton) mainLayout.findViewById(R.id.recover);
        recoverButton.setOnClickListener(this);
        recoverButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);
        final ImageButton deleteButton = (ImageButton) mainLayout.findViewById(R.id.delete);
        deleteButton.setOnClickListener(this);
        deleteButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);
        final ImageButton shareButton = (ImageButton) mainLayout.findViewById(R.id.share);
        shareButton.setOnClickListener(this);
        shareButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);
        final ImageButton saveButton = (ImageButton) mainLayout.findViewById(R.id.save);
        saveButton.setOnClickListener(this);
        saveButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);

        final Integer[] colors = new Integer[]{getResources().getColor(R.color.crop_draw_color_1),
                getResources().getColor(R.color.crop_draw_color_2),
                getResources().getColor(R.color.crop_draw_color_3),
                getResources().getColor(R.color.crop_draw_color_4),
                getResources().getColor(R.color.crop_draw_color_5),
                getResources().getColor(R.color.crop_draw_color_6),
                getResources().getColor(R.color.crop_draw_color_7),
                getResources().getColor(R.color.crop_draw_color_8)};

        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_image_edit, opt);
        float scale = getResources().getDisplayMetrics().density;

        final ListPopupWindow listPopupWindowColorPicker = new ListPopupWindow(mContext);
        listPopupWindowColorPicker.setAdapter(new ColorArrayAdapter(mContext, colors));
        int width = mContext.getResources().getDimensionPixelSize(R.dimen.crop_buttons);
        final ImageButton drawColorButton = (ImageButton) mainLayout.findViewById(R.id.drawColor);
        listPopupWindowColorPicker.setAnchorView(drawColorButton);
        drawColor = preferences.getInt(KEY_DRAW_COLOR, getResources().getColor(R.color.crop_draw_color_1));
        cropView.setDrawColor(drawColor);
        drawColorButton.setImageDrawable(createColorImage(drawColor));

        listPopupWindowColorPicker.setDropDownGravity(Gravity.CENTER);
        drawColorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPopUpColorShowing) {
                    listPopupWindowColorPicker.show();
                    isPopUpColorShowing = true;
                }
            }
        });
        listPopupWindowColorPicker.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                isPopUpColorShowing = false;
            }
        });
        listPopupWindowColorPicker.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                drawColor = colors[position];
                drawColorButton.setImageDrawable(createColorImage(drawColor));
                preferences.edit().putInt(KEY_DRAW_COLOR, drawColor).apply();
                cropView.setDrawColor(drawColor);
                listPopupWindowColorPicker.dismiss();
            }
        });


        final String [] penSizeValues = getResources().getStringArray(R.array.crop_pen_size_entries);
        final ListPopupWindow listPopupPenSizerPicker = new ListPopupWindow(mContext);
        listPopupPenSizerPicker.setAdapter(new PenSizeArrayAdapter(this, android.R.layout.simple_list_item_1, penSizeValues));
        final ImageButton penSizeButton = (ImageButton) mainLayout.findViewById(R.id.penSize);
        listPopupPenSizerPicker.setAnchorView(penSizeButton);
        final int penSizeValue = preferences.getInt(KEY_PEN_SIZE, 5);
        penSize = Math.round(penSizeValue * mDensity);
        cropView.setPenSize(penSize);
        penSizeButton.setImageDrawable(createPenSizeImage(penSize));

        listPopupPenSizerPicker.setDropDownGravity(Gravity.CENTER);
        penSizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPopUpColorShowing) {
                    listPopupPenSizerPicker.show();
                    isPopUpPenSizeShowing = true;
                }
            }
        });
        listPopupPenSizerPicker.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                isPopUpPenSizeShowing = false;
            }
        });
        listPopupPenSizerPicker.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final int penSizeValue = Integer.valueOf(penSizeValues[position]);
                preferences.edit().putInt(KEY_PEN_SIZE, penSizeValue).apply();
                penSize = Math.round(penSizeValue * mDensity);
                penSizeButton.setImageDrawable(createPenSizeImage(penSize));
                cropView.setPenSize(penSize);
                listPopupPenSizerPicker.dismiss();
            }
        });

        layoutWorkModeCrop = (LinearLayout) mainLayout.findViewById(R.id.layout_work_mode_crop);
        layoutWorkModeDraw = (LinearLayout) mainLayout.findViewById(R.id.layout_work_mode_draw);

        final ListPopupWindow listPopupWindow = new ListPopupWindow(mContext);
        workModeButton = (ImageButton) mainLayout.findViewById(R.id.workMode);
        workMode = preferences.getInt(KEY_WORK_MODE, WORK_MODE_CROP);
        ImageArrayAdapter adapter = new ImageArrayAdapter(mContext,
                new Integer[]{R.drawable.ic_image_crop, R.drawable.ic_image_edit, R.drawable.ic_action_visibility});
        listPopupWindow.setAdapter(adapter);
        listPopupWindow.setAnchorView(workModeButton);
        listPopupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                preferences.edit().putInt(KEY_WORK_MODE, position).apply();
                workMode = position;
                setWorkMode();
                listPopupWindow.dismiss();
            }
        });
        listPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                isPopUpWorkModeShowing = false;
            }
        });
        workModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPopUpWorkModeShowing) {
                    listPopupWindow.show();
                    isPopUpWorkModeShowing = true;
                }
                else listPopupWindow.dismiss();
            }
        });
        setWorkMode();
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onClick(View v) {
        String msg = "";
        Bitmap bm = null;
        switch (v.getId()) {
            case R.id.recover:
                cropView.recoverImage();
                break;
            case R.id.delete:
                deleteBitmap(screenshotPath);
                break;
            case R.id.share:
                boolean cropAnytime = Settings.System.getInt(getContentResolver(), Settings.System.SCREENSHOT_CROP_BEHAVIOR, 1) != 0;
                bm = cropAnytime ? cropView.getCroppedBitmap() : cropView.getImageBitmap();
                cropView.setCropEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                shareBitmap(bm);
                progressBar.setVisibility(View.GONE);
                removeView();
                break;
            case R.id.save:
                cropAnytime = Settings.System.getInt(getContentResolver(), Settings.System.SCREENSHOT_CROP_BEHAVIOR, 1) != 0;
                bm = cropAnytime ? cropView.getCroppedBitmap() : cropView.getImageBitmap();
                if (saveBitmap(bm))
                    msg = getString(R.string.action_save_success);
                else msg = getString(R.string.action_save_fault);
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
                removeView();
                break;
            case R.id.doneCrop:
                cropView.cropImage(cropView.getCroppedBitmap());
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mainHandler.post(new Runnable() {
            public void run() {
                if (isShowing)
                    wm.updateViewLayout(mainLayout, getParams());
            }
        });
    }

    private void setWorkMode() {
        switch (workMode) {
            case 0:
                cropView.setCropEnabled(true);
                cropView.setDrawEnabled(false);
                workModeButton.setImageResource(R.drawable.ic_image_crop);
                layoutWorkModeDraw.setVisibility(View.GONE);
                layoutWorkModeCrop.setVisibility(View.VISIBLE);
                break;
            case 1:
                cropView.setCropEnabled(false);
                cropView.setDrawEnabled(true);
                workModeButton.setImageResource(R.drawable.ic_image_edit);
                layoutWorkModeDraw.setVisibility(View.VISIBLE);
                layoutWorkModeCrop.setVisibility(View.GONE);
                break;
            case 2:
                cropView.setCropEnabled(false);
                cropView.setDrawEnabled(false);
                workModeButton.setImageResource(R.drawable.ic_action_visibility);
                layoutWorkModeDraw.setVisibility(View.GONE);
                layoutWorkModeCrop.setVisibility(View.GONE);
                break;
        }
        workModeButton.setColorFilter(nOverlayColor, PorterDuff.Mode.MULTIPLY);
    }

    private void setCropMode() {
        cropView.setCropEnabled(true);
        if (nCropMode > 2)
            nCropMode = 0;
        switch (nCropMode) {
            case 0:
                cropModeButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_image_crop_free));
                cropView.setCropMode(CropImageView.CropMode.RATIO_FREE);
                break;
            case 1:
                cropModeButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_image_crop_square));
                cropView.setCropMode(CropImageView.CropMode.RATIO_1_1);
                break;
            case 2:
                cropModeButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_image_crop_circle));
                cropView.setCropMode(CropImageView.CropMode.CIRCLE);
                break;
        }
    }

    private WindowManager.LayoutParams getParams() {
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);

        params.dimAmount = 0.7f;
        params.windowAnimations = R.style.CropDialogAnimations;
        params.flags += WindowManager.LayoutParams.FLAG_FULLSCREEN;
        return params;
    }

    private WindowManager.LayoutParams getFabParams() {
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        params.dimAmount = 0.2f;
        params.windowAnimations = R.style.CropDialogAnimations;
        params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        return params;
    }

    public boolean saveBitmap(Bitmap bm) {
        try {
            FileOutputStream stream = new FileOutputStream(screenshotPath);
            bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.flush();
            stream.close();
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(screenshotPath))));
            return true;
        } catch (Exception e) {
            Log.e("Could not save", e.toString());
            return false;
        }
    }

    private void shareBitmap(final Bitmap bitmap) {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File file = new File(mContext.getCacheDir(), "croppedScreenshot_" + String.valueOf(System.currentTimeMillis()) + "_.png");
                    File cacheFiles = new File(getCacheDir().getPath());
                    File[] cachedScreenshots = cacheFiles.listFiles();
                    for (File screenshot : cachedScreenshots)
                        screenshot.delete();

                    FileOutputStream fOut = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                    fOut.flush();
                    fOut.close();
                    file.setReadable(true, false);
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
                    final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                    intent.setType("image/png");
                    Intent sender = Intent.createChooser(intent, null);
                    sender.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplicationContext().startActivity(sender);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    private void deleteBitmap(String file) {
        String msg;
        boolean deleted = new File(file).delete();
        if (deleted)
            msg = getString(R.string.action_delete_success);
        else msg = getString(R.string.action_delete_fault);
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(screenshotPath))));
        removeView();
    }



    @TargetApi(Build.VERSION_CODES.M)
    public static boolean canWeDrawOurOverlay(Context mContext) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(mContext);
    }

    private int getUiOptions() {
        if (Build.VERSION.SDK_INT >= 19) {
            return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        } else return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
    }

    private BitmapDrawable createPenSizeImage(int penSize){
        final int width = getResources().getDimensionPixelSize(R.dimen.crop_buttons_inlet);
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmp = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(penSize);
        canvas.drawLine(0, width / 2, width, width / 2, paint);
        return new BitmapDrawable(getResources(), bmp);
    }

    private BitmapDrawable createColorImage(int color){
        final int width = getResources().getDimensionPixelSize(R.dimen.crop_buttons_inlet);
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmp = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        // inside
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(0, 0, width, width, paint);
        // border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, width, width, paint);
        return new BitmapDrawable(getResources(), bmp);
    }
}