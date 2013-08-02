
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.android.systemui.R;

public class MusicToggle extends StatefulToggle {

    private AudioManager mAM = null;
    private IAudioService mAS = null;

    private static final int MEDIA_STATE_UNKNOWN  = -1;
    private static final int MEDIA_STATE_INACTIVE =  0;
    private static final int MEDIA_STATE_ACTIVE   =  1;

    private int mCurrentState = MEDIA_STATE_UNKNOWN;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    protected void doEnable() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        updateCurrentState(State.ENABLED);
    }

    @Override
    protected void doDisable() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        updateCurrentState(State.DISABLED);
    }

    @Override
    public boolean onLongClick(View v) {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
        return super.onLongClick(v);
    }

    @Override
    protected void updateView() {
        setEnabledState(isMusicActive());
        setLabel(isMusicActive() ? R.string.quick_settings_music_pause
                : R.string.quick_settings_music_play);

        if (isMusicActive()) {
            setIcon(com.android.internal.R.drawable.ic_media_pause);
        } else {
            setIcon(com.android.internal.R.drawable.ic_media_play);
        }

        super.updateView();
    }

    private boolean isMusicActive() {
        if (mCurrentState == MEDIA_STATE_UNKNOWN) {
            mCurrentState = MEDIA_STATE_INACTIVE;
            AudioManager am = getAudioManager();
            if (am != null) {
                mCurrentState = (am.isMusicActive() ? MEDIA_STATE_ACTIVE : MEDIA_STATE_INACTIVE);
            }
            return (mCurrentState == MEDIA_STATE_ACTIVE);
        } else {
            boolean active = (mCurrentState == MEDIA_STATE_ACTIVE);
            mCurrentState = MEDIA_STATE_UNKNOWN;
            return active;
        }
    }

    protected void sendMediaKeyEvent(int code) {
        long eventTime = SystemClock.uptimeMillis();
        KeyEvent key = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, code, 0);
        dispatchMediaKeyWithWakeLockToAudioService(key);
        dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.changeAction(key, KeyEvent.ACTION_UP));
        mCurrentState = (isMusicActive() ? MEDIA_STATE_INACTIVE : MEDIA_STATE_ACTIVE);
    }

    void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        IAudioService audioService = getAudioService();
        if (audioService != null) {
            try {
                audioService.dispatchMediaKeyEventUnderWakelock(event);
            } catch (RemoteException e) {
                Log.e(TAG, "dispatchMediaKeyEvent threw exception " + e);
            }
        }
    }

    IAudioService getAudioService() {
        if (mAS == null) {
            mAS = IAudioService.Stub.asInterface(
                    ServiceManager.checkService(Context.AUDIO_SERVICE));
            if (mAS == null) {
                Log.w(TAG, "Unable to find IAudioService interface.");
            }
        }
        return mAS;
    }

    protected AudioManager getAudioManager() {
        if (mAM == null) {
            mAM = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
        return mAM;
    }
}
