/*
 * Copyright (C) 2013 The Android Open Kang Project
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

package com.android.internal.util.aokp;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.R;

import java.io.File;

public class RibbonTarget {

    private static final String TAG = "Ribbon Target";

    private View mView;
    private LinearLayout mContainer;
    private Context mContext;
    private IWindowManager mWm;
    private ImageButton mIcon;
    private Button mBackground;
    private TextView mText;
    private Vibrator vib;
    private Intent u;
    private Intent b;
    private Intent a;


    /*
     * sClick = short click send the uri for the short click action also this will be the icon used
     * lClick = long click send the uri for the long click action
     * cIcon = custom icon
     * text = a boolean for weither to show the app text label
     * color = text color
     * touchVib = vibrate on touch
     * size = size used to resize icons 0 is default and will not resize the icons at all.
     */

    public RibbonTarget(Context context, final String sClick, final String lClick,
            final String cIcon, final boolean text, final int color, final int size, final boolean touchVib, final boolean colorize) {
        mContext = context;
        u = new Intent();
        u.setAction("com.android.lockscreen.ACTION_UNLOCK_RECEIVER");
        b = new Intent();
        b.setAction("com.android.systemui.ACTION_HIDE_RIBBON");
        a = new Intent();
        a.setAction("com.android.systemui.ACTION_HIDE_APP_WINDOW");
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
	    DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        vib = (Vibrator) mContext.getSystemService(mContext.VIBRATOR_SERVICE);
        mView = View.inflate(mContext, R.layout.target_button, null);
        mContainer = (LinearLayout) mView.findViewById(R.id.container);
        mBackground = (Button) mView.findViewById(R.id.background);
        mBackground.setBackgroundColor(Color.TRANSPARENT);
        mBackground.setClickable(false);
        mText = (TextView) mView.findViewById(R.id.label);
        if (!text) {
            mText.setVisibility(View.GONE);
        }
        mText.setText(NavBarHelpers.getProperSummary(mContext, sClick.equals("**null**") ? lClick : sClick));
        if (color != -1) {
            mText.setTextColor(color);
        }
        mText.setOnClickListener(new OnClickListener() {
            @Override
            public final void onClick(View v) {
                if(vib != null && touchVib) {
                    vib.vibrate(10);
                }
                collapseStatusBar();
                sendIt(sClick.equals("**null**") ? lClick : sClick);
            }
        });
        if (!lClick.equals("**null**")) {
            mText.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    collapseStatusBar();
                    sendIt(lClick);
                    return true;
                }
            });
        }
        mText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    mBackground.setBackgroundColor((color != -1) ? color : Color.CYAN);
                    break;
                case MotionEvent.ACTION_CANCEL :
                case MotionEvent.ACTION_UP:
                    mBackground.setBackgroundColor(Color.TRANSPARENT);
                    break;
                }
                return false;
            }
        });
        mIcon = (ImageButton) mView.findViewById(R.id.icon);
        Drawable newIcon = NavBarHelpers.getIconImage(mContext, "**null**");
        if (!cIcon.equals("**null**")) {
            if (size > 0) {
                newIcon = resize(getCustomDrawable(mContext, cIcon), mapChosenDpToPixels(size));
            } else {
                newIcon = getCustomDrawable(mContext, cIcon);
            }
        } else {
            if (size > 0) {
                newIcon = resize(NavBarHelpers.getIconImage(mContext, sClick.equals("**null**") ? lClick : sClick), mapChosenDpToPixels(size));
            } else {
                newIcon = NavBarHelpers.getIconImage(mContext, sClick.equals("**null**") ? lClick : sClick);
                int desiredSize = (int) (48 * metrics.density);
                int width = newIcon.getIntrinsicWidth();
                if (width > desiredSize) {
                    Bitmap bm = ((BitmapDrawable) newIcon).getBitmap();
                    if (bm != null) {
                        Bitmap bitmapOrig = Bitmap.createScaledBitmap(bm, desiredSize, desiredSize, false);
                        newIcon = new BitmapDrawable(mContext.getResources(), bitmapOrig);
                    }
                }
            }
        }
        if ((sClick.equals("**null**") ? lClick.startsWith("**") : sClick.startsWith("**")) && colorize) {
            mIcon.setColorFilter(color);
        }
        mIcon.setImageDrawable(newIcon);
        if (!sClick.equals("**null**")) {
            mIcon.setOnClickListener(new OnClickListener() {
                @Override
                public final void onClick(View v) {
                    if(vib != null && touchVib) {
                        vib.vibrate(10);
                    }
                    collapseStatusBar();
                    sendIt(sClick);
                }
            });
        }
        if (!lClick.equals("**null**")) {
            mIcon.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    collapseStatusBar();
                    sendIt(lClick);
                    return true;
                }
            });
        }
        mIcon.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                case  MotionEvent.ACTION_DOWN :
                    mBackground.setBackgroundColor((color != -1) ? color : Color.CYAN);
                    break;
                case MotionEvent.ACTION_CANCEL :
                case MotionEvent.ACTION_UP:
                    mBackground.setBackgroundColor(Color.TRANSPARENT);
                    break;
                }
                return false;
            }
        });
    }

    private Drawable resize(Drawable image, int size) {
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size,
                mContext.getResources().getDisplayMetrics());

        Bitmap d = ((BitmapDrawable) image).getBitmap();
        if (d == null) {
            return AwesomeConstants.getSystemUIDrawable(mContext, "**null**");
        } else {
            Bitmap bitmapOrig = Bitmap.createScaledBitmap(d, px, px, false);
            return new BitmapDrawable(mContext.getResources(), bitmapOrig);
        }
    }

    private void sendIt(String action) {
        mContext.sendBroadcastAsUser(a, UserHandle.ALL);
        if (shouldUnlock(action)) {
            mContext.sendBroadcastAsUser(u, UserHandle.ALL);
        }
        Intent i = new Intent();
        i.setAction("com.android.systemui.aokp.LAUNCH_ACTION");
        i.putExtra("action", action);
        mContext.sendBroadcastAsUser(i, UserHandle.ALL);
        mContext.sendBroadcastAsUser(b, UserHandle.ALL);
    }

    private boolean shouldUnlock(String action) {
        if (action.equals(AwesomeConstants.AwesomeConstant.ACTION_TORCH.value()) ||
            action.equals(AwesomeConstants.AwesomeConstant.ACTION_NOTIFICATIONS.value()) ||
            action.equals(AwesomeConstants.AwesomeConstant.ACTION_POWER.value())) {
            return false;
        }

        return true;
    }

    public int mapChosenDpToPixels(int dp) {
        switch (dp) {
            case 0:
                return 0;
            case 20:
                return mContext.getResources().getDimensionPixelSize(R.dimen.icon_size_20);
            case 15:
                return mContext.getResources().getDimensionPixelSize(R.dimen.icon_size_15);
        }
        return -1;
    }

    private void collapseStatusBar() {
        try {
            IStatusBarService sb = IStatusBarService.Stub
                    .asInterface(ServiceManager
                            .getService(Context.STATUS_BAR_SERVICE));
            sb.collapsePanels();
        } catch (RemoteException e) {
        }
    }

    public View getView() {
        return mView;
    }

    public void setVerticalPadding(int pad, int side) {
        mContainer.setPadding(side, 0, side, pad);
    }

    public void setPadding(int pad, int top) {
        mContainer.setPadding(pad, top, pad, top);
    }

    public static Drawable getCustomDrawable(Context context, String action) {
        final Resources res = context.getResources();

        File f = new File(Uri.parse(action).getPath());
        Drawable front = new BitmapDrawable(res,
                         getRoundedCornerBitmap(BitmapFactory.decodeFile(f.getAbsolutePath())));
        return front;
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
            bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 24;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }
}
