package com.android.systemui.statusbar.info;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.os.AsyncTask;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.widget.TextView;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.settingslib.net.DataUsageController;

public class DataUsageView extends TextView {

    private Context mContext;
    private NetworkController mNetworkController;
    private static boolean shouldUpdateData;
    private static boolean shouldUpdateDataTextView;
    private String formatedinfo;

    public DataUsageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mNetworkController = Dependency.get(NetworkController.class);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isDataUsageEnabled() && this.getText().toString() != "") {
            setText("");
        }
        if (isDataUsageEnabled()) {
            if(shouldUpdateData) {
                shouldUpdateData = false;
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        updateUsageData();
                    }
                });
            }
            if (shouldUpdateDataTextView) {
                shouldUpdateDataTextView = false;
                setText(formatedinfo);
            }
        }
    }

    private void updateUsageData() {
        DataUsageController mobileDataController = new DataUsageController(mContext);
        mobileDataController.setSubscriptionId(
            SubscriptionManager.getDefaultDataSubscriptionId());
        final DataUsageController.DataUsageInfo info = mobileDataController.getDataUsageInfo();
        
        formatedinfo = formatDataUsage(info.usageLevel) + " " + mContext.getResources().getString(R.string.usage_data);
        shouldUpdateDataTextView = true;
    }

    private boolean isDataUsageEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_DATAUSAGE, 0) != 0;
    }

    public static void updateUsage() {
        shouldUpdateData = true;
    }

    private CharSequence formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(mContext.getString(
                com.android.internal.R.string.fileSizeSuffix, res.value, res.units));
    }
}