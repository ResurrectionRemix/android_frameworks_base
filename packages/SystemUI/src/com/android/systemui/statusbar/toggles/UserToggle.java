
package com.android.systemui.statusbar.toggles;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.WindowManager;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class UserToggle extends BaseToggle {

    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;

    private static Drawable sAvatarDrawable = null;
    private static int sAvatarBaseSize = 125;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        reloadUserInfo();
        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                reloadUserInfo();
            }
        }, new IntentFilter(Intent.ACTION_USER_SWITCHED));
    }

    @Override
    public void onClick(View v) {
        dismissKeyguard();
        vibrateOnTouch();
        collapseStatusBar();
        final UserManager um =
                (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        if (um.getUsers(true).size() > 1) {
            try {
                WindowManagerGlobal.getWindowManagerService().lockNow(null);
            } catch (RemoteException e) {
                log("Couldn't show user switcher", e);
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Profile.CONTENT_URI);
            mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        }
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
        queryForUserInformation();
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
           log("Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
           log("Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);

                // Fall back to the UserManager nickname if we can't read the
                // name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    DisplayMetrics dm = new DisplayMetrics();
                    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    wm.getDefaultDisplay().getMetrics(dm);
                    int desiredSize = (int) (sAvatarBaseSize * dm.density);
                    int width = rawAvatar.getWidth();
                    if (width > desiredSize) {
                        rawAvatar = Bitmap.createScaledBitmap(rawAvatar, desiredSize, desiredSize, false);
                    }
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                }

                // If it's a single-user device, get the profile name, since the
                // nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {
                                    Phone._ID, Phone.DISPLAY_NAME
                            },
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
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
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
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

}
