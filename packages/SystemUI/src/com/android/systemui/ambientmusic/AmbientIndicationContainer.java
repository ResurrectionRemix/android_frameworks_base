package com.android.systemui.ambientmusic;

import android.content.Context;
import android.media.MediaMetadata;
import android.os.Handler;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.ambientmusic.AmbientIndicationInflateListener;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.statusbar.phone.StatusBar;

import java.util.concurrent.TimeUnit;

public class AmbientIndicationContainer extends AutoReinflateContainer implements DozeReceiver {
    private View mAmbientIndication;
    private boolean mDozing;
    private ImageView mIcon;
    private CharSequence mIndication;
    private StatusBar mStatusBar;
    private TextView mText;
    private TextView mTrackLenght;
    private Context mContext;
    private MediaMetadata mMediaMetaData;
    private boolean mForcedMediaDoze;
    private Handler mHandler;
    private boolean mScrollingInfo;

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
    }

    public void hideIndication() {
        setIndication(null);
    }

    public void initializeView(StatusBar statusBar, Handler handler) {
        mStatusBar = statusBar;
        addInflateListener(new AmbientIndicationInflateListener(this));
        mHandler = handler;
    }

    public void updateAmbientIndicationView(View view) {
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mText = (TextView)findViewById(R.id.ambient_indication_text);
        mTrackLenght = (TextView)findViewById(R.id.ambient_indication_track_lenght);
        mIcon = (ImageView)findViewById(R.id.ambient_indication_icon);
        setIndication(mMediaMetaData);
    }

    @Override
    public void setDozing(boolean dozing) {
        mDozing = dozing;
        setVisibility(dozing ? View.VISIBLE : View.INVISIBLE);
        updatePosition();
    }

    public void setTickerMarquee(boolean enable) {
        if (enable) {
            setTickerMarquee(false);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mText.setEllipsize(TruncateAt.MARQUEE);
                    mText.setMarqueeRepeatLimit(2);
                    mText.setSelected(true);
                    mScrollingInfo = true;
                }
            }, 1600);
        } else {
            mText.setEllipsize(null);
            mText.setSelected(false);
            mScrollingInfo = false;
        }
    }

    public void setCleanLayout(int reason) {
        mForcedMediaDoze =
                reason == DozeLog.PULSE_REASON_FORCED_MEDIA_NOTIFICATION;
        updatePosition();
    }

    public void updatePosition() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) this.getLayoutParams();
        lp.gravity = mForcedMediaDoze ? Gravity.CENTER : Gravity.BOTTOM;
        this.setLayoutParams(lp);
    }

    public void setIndication(MediaMetadata mediaMetaData) {
        CharSequence charSequence = null;
        CharSequence lenghtInfo = null;
        if (mediaMetaData != null) {
            CharSequence artist = mediaMetaData.getText(MediaMetadata.METADATA_KEY_ARTIST);
            CharSequence album = mediaMetaData.getText(MediaMetadata.METADATA_KEY_ALBUM);
            CharSequence title = mediaMetaData.getText(MediaMetadata.METADATA_KEY_TITLE);
            long duration = mediaMetaData.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (artist != null && album != null && title != null) {
                /* considering we are in Ambient mode here, it's not worth it to show
                    too many infos, so let's skip album name to keep a smaller text */
                charSequence = artist.toString() /*+ " - " + album.toString()*/ + " - " + title.toString();
                if (duration != 0) {
                    lenghtInfo = String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(duration),
                            TimeUnit.MILLISECONDS.toSeconds(duration) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
                    );
                }
            }
        }
        if (mScrollingInfo) {
            // if we are already showing an Ambient Notification with track info,
            // stop the current scrolling and start it delayed again for the next song
            setTickerMarquee(true);
        }
        mText.setText(charSequence);
        mTrackLenght.setText(lenghtInfo);
        mMediaMetaData = mediaMetaData;
        boolean infoAvaillable = TextUtils.isEmpty(charSequence);
        if (infoAvaillable) {
            mAmbientIndication.setVisibility(View.INVISIBLE);
        } else {
            mAmbientIndication.setVisibility(View.VISIBLE);
        }
        if (mStatusBar != null) {
            mStatusBar.triggerAmbientForMedia();
        }
    }
}
