/*
 * Copyright (C) 2018 CypherOS
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
package com.android.systemui.ambient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import com.android.systemui.R;
import com.android.systemui.ambient.AmbientIndicationContainer;

public class AmbientIndicationNotification {

    private Context mContext;

    public AmbientIndicationNotification(Context context) {
        mContext = context;
    }

    public void show(String song, String artist) {
        Notification.Builder mBuilder =
                new Notification.Builder(mContext, "music_recognized_channel");
        final Bundle extras = Bundle.forPair(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                mContext.getResources().getString(R.string.ambient_recognition_notification));
        mBuilder.setSmallIcon(R.drawable.ic_music_note_24dp);
        mBuilder.setContentText(String.format(mContext.getResources().getString(
                 R.string.ambient_recognition_information), song, artist));
        mBuilder.setColor(mContext.getResources().getColor(com.android.internal.R.color.system_notification_accent_color));
        mBuilder.setAutoCancel(false);
        mBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        mBuilder.setLocalOnly(true);
        mBuilder.setShowWhen(true);
        mBuilder.setWhen(System.currentTimeMillis());
        mBuilder.setTicker(String.format(mContext.getResources().getString(
                 R.string.ambient_recognition_information), song, artist));
        mBuilder.setExtras(extras);

        NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel("music_recognized_channel",
                mContext.getResources().getString(R.string.ambient_recognition_notification_channel),
                NotificationManager.IMPORTANCE_MIN);
        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.notify(122306791, mBuilder.build());
    }
}