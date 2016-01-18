/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.NotificationColorUtil;
import com.android.internal.util.rr.NotificationColorHelper;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.IconMerger;

/**
 * A view to display all the overflowing icons on Keyguard.
 */
public class NotificationOverflowIconsView extends IconMerger {

    private TextView mMoreText;
    private int mIconSize;
    private int mTintColor;	
    private NotificationColorUtil mNotificationColorUtil;
    private boolean MColorSwitch = false;
    public Context mContext;	

    public NotificationOverflowIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
	mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNotificationColorUtil = NotificationColorUtil.getInstance(getContext());
        mIconSize = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_size);
	mTintColor = getContext().getColor(R.color.keyguard_overflow_content_color);
    }

    public void setMoreText(TextView moreText) {
        mMoreText = moreText;
    }

    public void addNotification(NotificationData.Entry notification) {
        StatusBarIconView v = new StatusBarIconView(getContext(), "",
                notification.notification.getNotification());
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(v, mIconSize, mIconSize);
        v.set(notification.icon.getStatusBarIcon());
        applyColor(notification.notification.getNotification(), v);
        updateMoreText();
    }

    private void applyColor(Notification notification, StatusBarIconView view) {
		
	MColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.NOTIF_COLOR_SWITCH, 0) == 1;
	if(MColorSwitch){
        StatusBarIcon sbi = view.getStatusBarIcon();
        Drawable icon = StatusBarIconView.getIcon(getContext(), sbi);
        final int tintColor = NotificationColorHelper.getIconColor(getContext(), icon);
        	if (tintColor != 0) {
            view.setColorFilter(tintColor, PorterDuff.Mode.MULTIPLY);
        	} else 	{
            	view.setColorFilter(null);
        	}
	} else {
		view.setColorFilter(mTintColor, PorterDuff.Mode.MULTIPLY);
		}
    }

    private void updateMoreText() {	
        final int textColor = NotificationColorHelper.getCustomIconColor(getContext());
        final int bgColor = NotificationColorHelper.getLegacyBgColor(getContext(), 0);
        final int bgAlpha = NotificationColorHelper.getLegacyBgAlpha(getContext(), 0);
	MColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.NOTIF_COLOR_SWITCH, 0) == 1;
	if(MColorSwitch){
	        mMoreText.setText(
                getResources().getString(R.string.keyguard_more_overflow_text, getChildCount()));
        mMoreText.setTextColor(textColor);
        if (mMoreText.getBackground() != null) {
            if (bgColor == Notification.COLOR_DEFAULT) {
                mMoreText.getBackground().setColorFilter(null);
            } else {
                mMoreText.getBackground().setColorFilter(bgColor, PorterDuff.Mode.SRC_ATOP);

            }
            mMoreText.getBackground().setAlpha(bgAlpha);
        	}
	} else	{  mMoreText.setText(
                getResources().getString(R.string.keyguard_more_overflow_text, getChildCount())); }
	
    }
}
