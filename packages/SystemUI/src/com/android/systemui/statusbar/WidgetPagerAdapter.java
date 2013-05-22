
package com.android.systemui.statusbar;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;

public class WidgetPagerAdapter extends PagerAdapter {

    private static final String TAG = "Widget";

    int[] widgetIds = new int[1];
    public AppWidgetHost mAppWidgetHost;
    AppWidgetHostView[] hostViews;
    Context mContext;
    AppWidgetManager mAppWidgetManager;

    public WidgetPagerAdapter(Context c, int[] ids) {
    	if (ids != null) {
    		widgetIds = ids;
    	} else { // got passed null id set .. create a fake one
    		widgetIds[0] = -1;
    	}
        mContext = c;
        hostViews = new AppWidgetHostView[widgetIds.length];
        mAppWidgetManager = AppWidgetManager.getInstance(c);
        mAppWidgetHost = new AppWidgetHost(c, 2112);
    }

    @Override
    public int getCount() {
        return widgetIds.length;
    }

    public int getHeight(int pos) {
        int height = getSavedHeight(pos);
        if (height == -1) {
            if (hostViews[pos] != null && hostViews[pos].getAppWidgetInfo() != null) {
                height = hostViews[pos].getAppWidgetInfo().minHeight;
            } else {
                height = 100;  // default size
            }
            setSavedHeight(pos, height);    
        }
        return height;
    }

    public String getLabel(int pos) {
    	if (hostViews[pos] != null && hostViews[pos].getAppWidgetInfo() != null) {
    		return hostViews[pos].getAppWidgetInfo().label;
    	} else 
    		return "Widget";
    	
    }
    private int getSavedHeight(int pos) {
        SharedPreferences prefs = mContext.getSharedPreferences("widget_adapter",
                Context.MODE_WORLD_WRITEABLE);
        return prefs.getInt("widget_id_" + widgetIds[pos], -1);
    }

    public void setSavedHeight(int pos, int height) {
        SharedPreferences prefs = mContext.getSharedPreferences("widget_adapter",
                Context.MODE_WORLD_WRITEABLE);
        prefs.edit().putInt("widget_id_" + widgetIds[pos], height).commit();
    }

    /**
     * Create the page for the given position. The adapter is responsible for
     * adding the view to the container given here, although it only must ensure
     * this is done by the time it returns from {@link #finishUpdate()}.
     * 
     * @param container The containing View in which the page will be shown.
     * @param position The page position to be instantiated.
     * @return Returns an Object representing the new page. This does not need
     *         to be a View, but can be some other container of the page.
     */
    @Override
    public Object instantiateItem(View collection, int position) {
        int widgetId = widgetIds[position];

        AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(widgetId);
        hostViews[position] = mAppWidgetHost.createView(mContext, widgetId, appWidgetInfo);
        hostViews[position].setAppWidget(widgetId, appWidgetInfo);

        ((ViewPager) collection).addView(hostViews[position], 0);

        return hostViews[position];
    }

    /**
     * Remove a page for the given position. The adapter is responsible for
     * removing the view from its container, although it only must ensure this
     * is done by the time it returns from {@link #finishUpdate()}.
     * 
     * @param container The containing View from which the page will be removed.
     * @param position The page position to be removed.
     * @param object The same object that was returned by
     *            {@link #instantiateItem(View, int)}.
     */
    @Override
    public void destroyItem(View collection, int position, Object view) {
        ((ViewPager) collection).removeView((AppWidgetHostView) view);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == ((AppWidgetHostView) object);
    }

    /**
     * Called when the a change in the shown pages has been completed. At this
     * point you must ensure that all of the pages have actually been added or
     * removed from the container as appropriate.
     * 
     * @param container The containing View which is displaying this adapter's
     *            page views.
     */
    @Override
    public void finishUpdate(View arg0) {
    }

    @Override
    public void restoreState(Parcelable arg0, ClassLoader arg1) {
    }

    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void startUpdate(View arg0) {
    }

    public void onShow() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    public void onHide() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.stopListening();
        }
    }

}
