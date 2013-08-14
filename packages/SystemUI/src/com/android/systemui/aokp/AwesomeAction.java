/*
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.systemui.aokp;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.systemui.statusbar.WidgetView;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.R;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

public class AwesomeAction {

    public final static String TAG = "AwesomeAction";
    private final static String SysUIPackage = "com.android.systemui";

    private AwesomeAction() {
    }

    public static boolean launchAction(Context mContext, String action) {
        if (TextUtils.isEmpty(action) || action.equals(AwesomeConstant.ACTION_NULL)) {
            return false;
        }

            AwesomeConstant AwesomeEnum = fromString(action);
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            switch(AwesomeEnum) {
            case ACTION_RECENTS:
                try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).toggleRecentApps();
                } catch (RemoteException e) {
                    // let it go.
                }
                break;
            case ACTION_ASSIST:
                Intent intent = new Intent(Intent.ACTION_ASSIST);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                break;
            case ACTION_HOME:
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(homeIntent);
                break;
            case ACTION_BACK:
                injectKeyDelayed(KeyEvent.KEYCODE_BACK);
                break;
            case ACTION_MENU:
                injectKeyDelayed(KeyEvent.KEYCODE_MENU);
                break;
            case ACTION_SEARCH:
                injectKeyDelayed(KeyEvent.KEYCODE_SEARCH);
                break;
            case ACTION_RECENTS_GB:
                injectKeyDelayed(KeyEvent.KEYCODE_APP_SWITCH);
                break;
            case ACTION_KILL:
                KillTask mKillTask = new KillTask(mContext);
                mHandler.post(mKillTask);
                break;
            case ACTION_WIDGETS:
                Intent toggleWidgets = new Intent(
                    WidgetView.WidgetReceiver.ACTION_TOGGLE_WIDGETS);
                mContext.sendBroadcast(toggleWidgets);
                break;
            case ACTION_APP_WINDOW:
                Intent appWindow = new Intent();
                appWindow.setAction("com.android.systemui.ACTION_SHOW_APP_WINDOW");
                mContext.sendBroadcast(appWindow);
                break;
            case ACTION_VIB:
                if(am != null){
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case ACTION_SILENT:
                if(am != null){
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case ACTION_SILENT_VIB:
                if(am != null){
                    if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    } else if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case ACTION_POWER:
                injectKeyDelayed(KeyEvent.KEYCODE_POWER);
                break;
            case ACTION_IME:
                mContext.sendBroadcast(new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"));
                break;
            case ACTION_TORCH:
                Intent intentTorch = new Intent("android.intent.action.MAIN");
                intentTorch.setComponent(ComponentName.unflattenFromString("com.aokp.Torch/.TorchActivity"));
                intentTorch.addCategory("android.intent.category.LAUNCHER");
                intentTorch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentTorch);
                break;
            case ACTION_TODAY:
                long startMillis = System.currentTimeMillis();
                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                ContentUris.appendId(builder, startMillis);
                Intent intentToday = new Intent(Intent.ACTION_VIEW)
                    .setData(builder.build());
                intentToday.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentToday);
                break;
            case ACTION_CLOCKOPTIONS:
                Intent intentClock = new Intent(Intent.ACTION_QUICK_CLOCK);
                intentClock.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentClock);
                break;
            case ACTION_EVENT:
                Intent intentEvent = new Intent(Intent.ACTION_INSERT)
                    .setData(Events.CONTENT_URI);
                intentEvent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentEvent);
                break;
            case ACTION_VOICEASSIST:
                Intent intentVoice = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
                intentVoice.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentVoice);
                break;
            case ACTION_ALARM:
                Intent intentAlarm = new Intent(AlarmClock.ACTION_SET_ALARM);
                intentAlarm.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentAlarm);
                break;
            case ACTION_LAST_APP:
                toggleLastApp(mContext);
                break;
            case ACTION_NOTIFICATIONS:
                try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandNotificationsPanel();
                } catch (RemoteException e) {
                    // A RemoteException is like a cold
                    // Let's hope we don't catch one!
                }
                break;
            case ACTION_APP:
                try {
                    Intent intentapp = Intent.parseUri(action, 0);
                    intentapp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intentapp);
                } catch (URISyntaxException e) {
                    Log.e(TAG, "URISyntaxException: [" + action + "]");
                } catch (ActivityNotFoundException e){
                    Log.e(TAG, "ActivityNotFound: [" + action + "]");
                }
                break;
            }
            return true;
    }

    private static void injectKeyDelayed(int keycode) {
        KeyUp onInjectKey_Up = new KeyUp(keycode);
        KeyDown onInjectKey_Down = new KeyDown(keycode);
        mHandler.removeCallbacks(onInjectKey_Down);
        mHandler.removeCallbacks(onInjectKey_Up);
        mHandler.post(onInjectKey_Down);
        mHandler.postDelayed(onInjectKey_Up, 10);
    }

     public static class KeyDown implements Runnable {
        private int mInjectKeyCode;
        public KeyDown(int keycode) {
            this.mInjectKeyCode = keycode;
        }
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                        KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_KEYBOARD);
                    InputManager.getInstance().injectInputEvent(ev,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

     public static class KeyUp implements Runnable {
        private int mInjectKeyCode;
        public KeyUp(int keycode) {
            this.mInjectKeyCode = keycode;
        }
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_KEYBOARD);
                    InputManager.getInstance().injectInputEvent(ev,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    public static class KillTask implements Runnable {
         private Context mContext;
         public KillTask(Context context) {
             this.mContext = context;
         }
         public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Activity.ACTIVITY_SERVICE);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = am.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (SysUIPackage.equals(packageName))
                return; // don't kill SystemUI
            if (!defaultHomePackage.equals(packageName)) {
                am.forceStopPackage(packageName);
                Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
            }
        }
     }

    private static void toggleLastApp(Context mContext) {
        int lastAppId = 0;
        int looper = 1;
        String packageName;
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        List <ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);
        // lets get enough tasks to find something to switch to
        // Note, we'll only get as many as the system currently has - up to 5
        while ((lastAppId == 0) && (looper < tasks.size())) {
            packageName = tasks.get(looper).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage) && !packageName.equals("com.android.systemui")) {
                lastAppId = tasks.get(looper).id;
            }
            looper++;
        }
        if (lastAppId != 0) {
            am.moveTaskToFront(lastAppId, am.MOVE_TASK_NO_USER_ACTION);
        }
    }

    private static Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            }
        }
    };
}
