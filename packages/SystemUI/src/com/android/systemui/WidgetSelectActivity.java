
package com.android.systemui;

import java.util.ArrayList;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.android.systemui.statusbar.WidgetView;

public class WidgetSelectActivity extends Activity {

    static final String TAG = "WidgetSelectActivity";

    int widgetId;

    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    public static final int APP_WIDGET_HOST_ID = 2112;

    public static final String ACTION_SEND_ID = "com.android.systemui.ACTION_SEND_ID";

    AppWidgetManager mAppWidgetManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        widgetId = getIntent().getIntExtra("selected_widget_id", -1);

        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        addEmptyData(pickIntent);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                Log.w(TAG, "configure widget");
                configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                Log.w(TAG, "save widget");
                saveWidget(data);
            }
        } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
            Log.w(TAG, "cancelled");
            int appWidgetId =
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) {
                // remove reference in the widget host
                Intent deleteId = new Intent();
                deleteId.setAction(WidgetView.WidgetReceiver.ACTION_DEALLOCATE_ID);
                deleteId.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                sendBroadcast(deleteId);

                // let ROM Control know the preference is gone
                Intent send = new Intent();
                send.setAction(ACTION_SEND_ID);
                send.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                send.putExtra("summary", "None");
                sendBroadcast(send);
            }
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void addEmptyData(Intent pickIntent) {
        ArrayList<AppWidgetProviderInfo> customInfo =
                new ArrayList<AppWidgetProviderInfo>();
        pickIntent.putParcelableArrayListExtra(
                AppWidgetManager.EXTRA_CUSTOM_INFO, customInfo);
        ArrayList<Bundle> customExtras = new ArrayList<Bundle>();
        pickIntent.putParcelableArrayListExtra(
                AppWidgetManager.EXTRA_CUSTOM_EXTRAS, customExtras);
    }

    private void configureWidget(Intent data) {
        Bundle extras = data.getExtras();
        int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        AppWidgetProviderInfo appWidgetInfo =
                mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo.configure != null) {
            Intent intent =
                    new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            saveWidget(data);
        }
    }

    private void saveWidget(Intent data) {
        Bundle extras = data.getExtras();
        int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

        Intent send = new Intent();
        send.setAction(ACTION_SEND_ID);
        send.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        send.putExtra("summary", getWidgetSummary(appWidgetId));
        sendBroadcast(send);
        Log.i(TAG, "ACTION_SEND_ID sent ID:"+ appWidgetId + " Label:"+getWidgetSummary(appWidgetId) );
        finish();

    }

    private String getWidgetSummary(int appWidgetId) {
        if (appWidgetId == -1) {
            return "None";
        } else {
            AppWidgetProviderInfo appWPI = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
            if (appWPI == null) {
                return "Problem:" + appWidgetId;
            } else {
                return appWPI.label;
            }
        }

    }

}
