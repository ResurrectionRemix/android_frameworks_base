/*
 * Copyright (C) 2018 Projekt Substratum
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

package com.android.server.substratum;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.util.Arrays;

public class SoundUtils {
    private static final String TAG = "SubstratumService";
    private static final String SYSTEM_MEDIA_PATH = "/system/media/audio";
    private static final String SYSTEM_ALARMS_PATH =
            SYSTEM_MEDIA_PATH + File.separator + "alarms";
    private static final String SYSTEM_RINGTONES_PATH =
            SYSTEM_MEDIA_PATH + File.separator + "ringtones";
    private static final String SYSTEM_NOTIFICATIONS_PATH =
            SYSTEM_MEDIA_PATH + File.separator + "notifications";
    private static final String MEDIA_CONTENT_URI = "content://media/internal/audio/media";

    private static void updateGlobalSettings(ContentResolver resolver, String uri, String val) {
        Settings.Global.putStringForUser(resolver, uri, val, UserHandle.USER_SYSTEM);
    }

    public static boolean setUISounds(ContentResolver resolver, String sound_name,
            String location) {
        if (allowedUISound(sound_name)) {
            updateGlobalSettings(resolver, sound_name, location);
            return true;
        }

        return false;
    }

    public static void setDefaultUISounds(ContentResolver resolver, String sound_name,
                                          String sound_file) {
        updateGlobalSettings(resolver, sound_name, "/system/media/audio/ui/" + sound_file);
    }

    // This string array contains all the SystemUI acceptable sound files
    private static Boolean allowedUISound(String targetValue) {
        String[] allowed_themable = {
                "lock_sound",
                "unlock_sound"
        };

        return Arrays.asList(allowed_themable).contains(targetValue);
    }

    private static String getDefaultAudiblePath(int type) {
        final String name;
        final String path;

        switch (type) {
            case RingtoneManager.TYPE_ALARM:
                name = SystemProperties.get("ro.config.alarm_alert");
                path = name != null ? SYSTEM_ALARMS_PATH + File.separator + name : null;
                break;
            case RingtoneManager.TYPE_NOTIFICATION:
                name = SystemProperties.get("ro.config.notification_sound");
                path = name != null ? SYSTEM_NOTIFICATIONS_PATH + File.separator + name : null;
                break;
            case RingtoneManager.TYPE_RINGTONE:
                name = SystemProperties.get("ro.config.ringtone");
                path = name != null ? SYSTEM_RINGTONES_PATH + File.separator + name : null;
                break;
            default:
                path = null;
                break;
        }

        return path;
    }

    public static boolean setAudible(Context context, File ringtone, int type, String name) {
        final String path = ringtone.getAbsolutePath();
        final String mimeType = name.endsWith(".ogg") ? "application/ogg" : "application/mp3";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, path);
        values.put(MediaStore.MediaColumns.TITLE, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.SIZE, ringtone.length());
        values.put(MediaStore.Audio.Media.IS_RINGTONE, type == RingtoneManager.TYPE_RINGTONE);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION,
                type == RingtoneManager.TYPE_NOTIFICATION);
        values.put(MediaStore.Audio.Media.IS_ALARM, type == RingtoneManager.TYPE_ALARM);
        values.put(MediaStore.Audio.Media.IS_MUSIC, false);

        Uri uri = MediaStore.Audio.Media.getContentUriForPath(path);
        Uri newUri = null;
        Cursor c = context.getContentResolver().query(uri,
                new String[]{ MediaStore.MediaColumns._ID },
                MediaStore.MediaColumns.DATA + "='" + path + "'",
                null, null);

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            String id = String.valueOf(c.getLong(0));
            c.close();

            newUri = Uri.withAppendedPath(Uri.parse(MEDIA_CONTENT_URI), id);
            try {
                context.getContentResolver().update(uri, values,
                        MediaStore.MediaColumns._ID + "=" + id, null);
            } catch (SQLiteConstraintException e) {
                // intentionally left empty
            }
        }

        if (newUri == null) {
            newUri = context.getContentResolver().insert(uri, values);
        }

        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, newUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply audible", e);
            return false;
        }

        return true;
    }

    public static boolean setUIAudible(Context context, File ringtone_file, int type, String name) {
        final String path = ringtone_file.getAbsolutePath();
        final String path_clone = "/system/media/audio/ui/" + name + ".ogg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, path);
        values.put(MediaStore.MediaColumns.TITLE, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/ogg");
        values.put(MediaStore.MediaColumns.SIZE, ringtone_file.length());
        values.put(MediaStore.Audio.Media.IS_RINGTONE, false);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
        values.put(MediaStore.Audio.Media.IS_ALARM, false);
        values.put(MediaStore.Audio.Media.IS_MUSIC, true);

        Uri uri = MediaStore.Audio.Media.getContentUriForPath(path);
        Uri newUri = null;
        Cursor c = context.getContentResolver().query(uri,
                new String[]{ MediaStore.MediaColumns._ID },
                MediaStore.MediaColumns.DATA + "='" + path_clone + "'",
                null, null);

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            String id = String.valueOf(c.getLong(0));
            c.close();

            newUri = Uri.withAppendedPath(Uri.parse(MEDIA_CONTENT_URI), id);
            try {
                context.getContentResolver().update(uri, values,
                        MediaStore.MediaColumns._ID + "=" + id, null);
            } catch (SQLiteConstraintException e) {
                // intentionally left empty
            }
        }

        if (newUri == null) {
            newUri = context.getContentResolver().insert(uri, values);
        }

        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, newUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply ui audible", e);
            return false;
        }

        return true;
    }

    public static boolean setDefaultAudible(Context context, int type) {
        final String audiblePath = getDefaultAudiblePath(type);
        if (audiblePath == null) {
            return false;
        }

        Uri uri = MediaStore.Audio.Media.getContentUriForPath(audiblePath);
        Cursor c = context.getContentResolver().query(uri,
                new String[]{ MediaStore.MediaColumns._ID },
                MediaStore.MediaColumns.DATA + "='" + audiblePath + "'",
                null, null);

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            String id = String.valueOf(c.getLong(0));
            c.close();

            uri = Uri.withAppendedPath(Uri.parse(MEDIA_CONTENT_URI), id);
        }

        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, uri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply default audible", e);
            return false;
        }

        return true;
    }
}

