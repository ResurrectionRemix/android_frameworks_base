
package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.FileObserver;
import android.view.View;

import com.android.systemui.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FastChargeToggle extends StatefulToggle {

    private String mFastChargePath;
    private FileObserver mObserver;

    private boolean mFastChargeEnabled = false;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mFastChargePath = c.getString(com.android.internal.R.string.config_fastChargePath);
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                setEnabledState(mFastChargeEnabled = isFastChargeOn());
                return null;
            }

            protected void onPostExecute(Void result) {
                scheduleViewUpdate();
            };
        }.execute();
        mObserver = new FileObserver(mFastChargePath, FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String file) {
                log("fast charge file modified, event:" + event + ", file: " + file);
                setEnabledState(mFastChargeEnabled = isFastChargeOn());
            }
        };
        mObserver.startWatching();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mObserver.stopWatching();
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        setFastCharge(true);
    }

    @Override
    protected void doDisable() {
        setFastCharge(false);
    }

    @Override
    protected void updateView() {
        setEnabledState(mFastChargeEnabled);
        setLabel(mFastChargeEnabled
                ? R.string.quick_settings_fcharge_on_label
                : R.string.quick_settings_fcharge_off_label);
        setIcon(mFastChargeEnabled
                ? R.drawable.ic_qs_fcharge_on
                : R.drawable.ic_qs_fcharge_off);
        super.updateView();
    }

    private void setFastCharge(final boolean on) {
        Intent fastChargeIntent = new Intent("com.aokp.romcontrol.ACTION_CHANGE_FCHARGE_STATE");
        fastChargeIntent.setPackage("com.aokp.romcontrol");
        fastChargeIntent.putExtra("newState", on);
        mContext.sendBroadcast(fastChargeIntent);
        scheduleViewUpdate();
    }

    private boolean isFastChargeOn() {
        if (mFastChargePath == null || mFastChargePath.isEmpty()) {
            return false;
        }
        File file = new File(mFastChargePath);
        if (!file.exists()) {
            return false;
        }
        String content = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            content = reader.readLine();
            log("isFastChargeOn(): content: " + content);
            return "1".equals(content) || "Y".equalsIgnoreCase(content)
                    || "on".equalsIgnoreCase(content);
        } catch (Exception e) {
            log("exception reading fast charge file", e);
            return false;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setComponent(ComponentName
                .unflattenFromString("com.brewcrewfoo.performance/.activities.MainActivity"));
        intent.addCategory("android.intent.category.LAUNCHER");

        startActivity(intent);

        return super.onLongClick(v);
    }

}
