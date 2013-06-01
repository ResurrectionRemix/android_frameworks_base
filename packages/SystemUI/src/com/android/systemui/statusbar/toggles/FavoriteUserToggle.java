
package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

import java.io.InputStream;

public class FavoriteUserToggle extends BaseToggle {

    private AsyncTask<Void, Void, Pair<String, Drawable>> mFavContactInfoTask;

    private static Drawable sAvatarDrawable = null;

    private SettingsObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        reloadFavContactInfo();
        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                reloadFavContactInfo();
            }
        }, new IntentFilter(Intent.ACTION_USER_SWITCHED));
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        if (mFavContactInfoTask != null) {
            mFavContactInfoTask.cancel(false);
            mFavContactInfoTask = null;
        }
        sAvatarDrawable = null;
        super.cleanup();
    }

    @Override
    public void onClick(View v) {
        String lookupKey = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.QUICK_TOGGLE_FAV_CONTACT);

        if (lookupKey != null && lookupKey.length() > 0) {
            dismissKeyguard();
            collapseStatusBar();
            Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                    lookupKey);
            Uri res = ContactsContract.Contacts.lookupContact(mContext.getContentResolver(),
                    lookupUri);
            Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                    mContext, v, res,
                    ContactsContract.QuickContact.MODE_LARGE, null);
            mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        }
    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile_user, null);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        mLabel = (TextView) quick.findViewById(R.id.user_textview);
        mIcon = (ImageView) quick.findViewById(R.id.user_imageview);
        return quick;
    }

    @Override
    public View createTraditionalView() {
        View v = super.createTraditionalView();
        return v;
    }

    @Override
    protected void updateView() {
        if (sAvatarDrawable != null) {
            setIcon(sAvatarDrawable);
        }
        super.updateView();
    }

    private void queryForFavContactInformation() {
        mFavContactInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                String name = "";
                Drawable avatar = mContext.getResources()
                        .getDrawable(R.drawable.ic_qs_default_user);
                Bitmap rawAvatar = null;
                String lookupKey = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.QUICK_TOGGLE_FAV_CONTACT);
                if (lookupKey != null && lookupKey.length() > 0) {
                    Uri lookupUri = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                    Uri res = ContactsContract.Contacts.lookupContact(
                            mContext.getContentResolver(), lookupUri);
                    String[] projection = new String[] {
                            ContactsContract.Contacts.DISPLAY_NAME,
                            ContactsContract.Contacts.PHOTO_URI,
                            ContactsContract.Contacts.LOOKUP_KEY
                    };

                    final Cursor cursor = mContext.getContentResolver().query(res, projection,
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor
                                        .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    InputStream input = ContactsContract.Contacts.
                            openContactPhotoInputStream(mContext.getContentResolver(), res, true);
                    if (input != null) {
                        rawAvatar = BitmapFactory.decodeStream(input);
                    }

                    if (rawAvatar != null) {
                        avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                setLabel(result.first);
                sAvatarDrawable = result.second;
                scheduleViewUpdate();
                mFavContactInfoTask = null;
            }
        };
        mFavContactInfoTask.execute();
    }

    void reloadFavContactInfo() {
        if (mFavContactInfoTask != null) {
            mFavContactInfoTask.cancel(false);
            mFavContactInfoTask = null;
        }
        queryForFavContactInformation();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QUICK_TOGGLE_FAV_CONTACT),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            reloadFavContactInfo();
        }
    }
}
