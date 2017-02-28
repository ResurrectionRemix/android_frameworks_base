/*
 * Copyright (C) 2014-2017 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.slimrecent;

import static com.android.systemui.slimrecent.RecentPanelView.DEBUG;
import static com.android.systemui.slimrecent.RecentPanelView.PLAYSTORE_REFERENCE;
import static com.android.systemui.slimrecent.RecentPanelView.AMAZON_REFERENCE;
import static com.android.systemui.slimrecent.RecentPanelView.PLAYSTORE_APP_URI_QUERY;
import static com.android.systemui.slimrecent.RecentPanelView.AMAZON_APP_URI_QUERY;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.android.cards.internal.Card;
import com.android.cards.internal.CardHeader;
import com.android.cards.view.CardView;

import com.android.systemui.R;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

/**
 * This class handles our base card view.
 */
public class RecentCard extends Card {

    private static final boolean DEBUG = false;

    private static final String PLAYSTORE_REFERENCE = "com.android.vending";
    private static final String AMAZON_REFERENCE    = "com.amazon.venezia";

    private static final String PLAYSTORE_APP_URI_QUERY = "market://details?id=";
    private static final String AMAZON_APP_URI_QUERY    = "amzn://apps/android?p=";

    private RecentHeader mHeader;
    private RecentAppIcon mRecentIcon;
    private RecentExpandedCard mExpandedCard;

    private int mPersistentTaskId;

    private int mCardColor;

    private RecentController mSlimRecents;

    private TaskDescription mTaskDescription;

    private int defaultCardBg = mContext.getResources().getColor(
                R.color.recents_task_bar_default_background_color);
    private int cardColor = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.RECENT_CARD_BG_COLOR,
                0x00ffffff, UserHandle.USER_CURRENT);

    public RecentCard(Context context, TaskDescription td, float scaleFactor) {
        this(context, R.layout.inner_base_main, td, scaleFactor);
    }

    public RecentCard(Context context, int innerLayout, TaskDescription td, float scaleFactor) {
        super(context, innerLayout);

        constructBaseCard(context, td, scaleFactor);
    }

    // Construct our card.
    private void constructBaseCard(Context context,
            final TaskDescription td, float scaleFactor) {

        mTaskDescription = td;

        // Construct card header view.
        mHeader = new RecentHeader(mContext, td, scaleFactor);

        // Construct app icon view.
        mRecentIcon = new RecentAppIcon(
                context, td.resolveInfo, td.identifier, scaleFactor, td.getIsFavorite());
        mRecentIcon.setExternalUsage(true);

        // Construct expanded area view.
        mExpandedCard = new RecentExpandedCard(context, td, scaleFactor);
        initExpandedState(td);

        // set custom background
        if (cardColor != 0x00ffffff) {
            mCardColor = cardColor;
        } else {
            mCardColor = getDefaultCardColorBg(td);
        }
        this.setBackgroundResource(new ColorDrawable(mCardColor));

        // Finally add header, icon and expanded area to our card.
        addCardHeader(mHeader);
        addCardThumbnail(mRecentIcon);
        addCardExpand(mExpandedCard);

        mPersistentTaskId = td.persistentTaskId;
    }

    /** Returns the activity's primary color. */
    public int getDefaultCardColorBg(TaskDescription td) {
        if (td != null && td.cardColor != 0) {
            return td.cardColor;
        }
        return defaultCardBg;
    }

    // Update content of our card.
    public void updateCardContent(final TaskDescription td, float scaleFactor) {
        mTaskDescription = td;

        if (mHeader != null) {
            // Set or update the header title.
            mHeader.updateHeader(td, scaleFactor);
        }
        if (mRecentIcon != null) {
            mRecentIcon.updateIcon(
                    td.resolveInfo, td.identifier, scaleFactor, td.getIsFavorite());
        }
        if (mExpandedCard != null) {
            // Set expanded state.
            initExpandedState(td);
            // Update app screenshot.
            mExpandedCard.updateExpandedContent(td, scaleFactor);
        }
        mPersistentTaskId = td.persistentTaskId;

        // set custom background
        if (cardColor != 0x00ffffff) {
            mCardColor = cardColor;
        } else {
            mCardColor = getDefaultCardColorBg(td);
        }
        this.setBackgroundResource(new ColorDrawable(getDefaultCardColorBg(td)));
    }

    // Set initial expanded state of our card.
    private void initExpandedState(TaskDescription td) {
        // Read flags and set accordingly initial expanded state.
        final boolean isTopTask =
                (td.getExpandedState() & RecentPanelView.EXPANDED_STATE_TOPTASK) != 0;

        final boolean isSystemExpanded =
                (td.getExpandedState() & RecentPanelView.EXPANDED_STATE_BY_SYSTEM) != 0;

        final boolean isUserExpanded =
                (td.getExpandedState() & RecentPanelView.EXPANDED_STATE_EXPANDED) != 0;

        final boolean isUserCollapsed =
                (td.getExpandedState() & RecentPanelView.EXPANDED_STATE_COLLAPSED) != 0;

        final boolean isExpanded =
                ((isSystemExpanded && !isUserCollapsed) || isUserExpanded) && !isTopTask;

        if (mHeader != null) {
            // Set visible the expand/collapse button.
            mHeader.setButtonExpandVisible(!isTopTask);
            mHeader.setOtherButtonDrawable(R.drawable.recents_lock_to_app_pin);
            mHeader.setOtherButtonClickListener(new CardHeader.OnClickCardHeaderOtherButtonListener() {
                @Override
                public void onButtonItemClick(Card card, View view) {
                    Context appContext = mContext.getApplicationContext();
                    if (appContext == null) appContext = mContext;
                    if (appContext instanceof SystemUIApplication) {
                        SystemUIApplication app = (SystemUIApplication) appContext;
                        PhoneStatusBar statusBar = app.getComponent(PhoneStatusBar.class);
                        if (statusBar != null) {
                            statusBar.showScreenPinningRequest(mPersistentTaskId, false);
                        }
                    }
                }
            });
            mHeader.setOtherButtonVisible(isTopTask && screenPinningEnabled());
        }

        setExpanded(isExpanded);
    }

    private boolean screenPinningEnabled() {
        return Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.LOCK_TO_APP_ENABLED, 0) != 0;
    }

    @Override
    public void setupInnerViewElements(ViewGroup parent, View view) {
        // Nothing to do here.
        return;
    }

    public TaskDescription getTaskDescription() {
        return mTaskDescription;
    }

    public int getPersistentTaskId() {
        return mPersistentTaskId;
    }

    @Override
    public void setupOptionsItems(final CardView cv) {
        View options = cv.findViewById(R.id.card_options);
        // set custom background
        if (ColorUtils.isDarkColor(mCardColor)) {
            options.setBackgroundColor(ColorUtils.lightenColor(mCardColor));
        } else {
            options.setBackgroundColor(ColorUtils.darkenColor(mCardColor));
        }

        if (!checkAppInstaller(mTaskDescription.packageName, AMAZON_REFERENCE)
                && !checkAppInstaller(mTaskDescription.packageName, PLAYSTORE_REFERENCE)) {
            options.findViewById(R.id.market).setVisibility(View.GONE);
        }

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                Intent intent = null;
                if (id == R.id.app_info) {
                    intent = getAppInfoIntent();
                } else if (id == R.id.market) {
                    intent = getStoreIntent();
                }
                if (id == R.id.multiwindow) {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setDockCreateMode(0);
                    options.setLaunchStackId(ActivityManager.StackId.DOCKED_STACK_ID);
                    mSlimRecents = new RecentController(mContext, getContext().getResources()
                            .getConfiguration().getLayoutDirection());
                    try {
                        ActivityManagerNative.getDefault()
                                .startActivityFromRecents(mPersistentTaskId, options.toBundle());
                        mSlimRecents.openLastApptoBottom();
                    } catch (RemoteException e) {}
                    return; 
                }
                if (intent != null) {
                    RecentController.sendCloseSystemWindows("close_recents");
                    intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
                    TaskStackBuilder.create(mContext)
                            .addNextIntentWithParentStack(intent).startActivities(
                                    RecentPanelView.getAnimation(getContext(), getRecentGravity()));
                    return;
                }

                int[] location = new int[2];
                v.getLocationOnScreen(location);
                cv.hideOptions(location[0], location[1]);
            }
        };

        options.findViewById(R.id.app_info).setOnClickListener(listener);
        options.findViewById(R.id.market).setOnClickListener(listener);
        options.findViewById(R.id.close).setOnClickListener(listener);
        options.findViewById(R.id.multiwindow).setOnClickListener(listener);
    }

    private Intent getAppInfoIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", mTaskDescription.packageName, null));
    }

    private Intent getStoreIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String reference;
        if (checkAppInstaller(mTaskDescription.packageName, AMAZON_REFERENCE)) {
            reference = AMAZON_REFERENCE;
            intent.setData(Uri.parse(AMAZON_APP_URI_QUERY + mTaskDescription.packageName));
        } else {
            reference = PLAYSTORE_REFERENCE;
            intent.setData(Uri.parse(PLAYSTORE_APP_URI_QUERY + mTaskDescription.packageName));
        }
        // Exclude from recents if the store is not in our task list.
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        return intent;
    }

    /**
     * Check if the requested app was installed by the reference store.
     */
    private boolean checkAppInstaller(String packageName, String reference) {
        if (packageName == null) {
            return false;
        }
        PackageManager pm = mContext.getPackageManager();
        if (!isReferenceInstalled(reference, pm)) {
            return false;
        }

        String installer = pm.getInstallerPackageName(packageName);
        if (DEBUG) Log.d(TAG, "Package was installed by: " + installer);
        if (reference.equals(installer)) {
            return true;
        }
        return false;
    }

    /**
     * Check is store reference is installed.
     */
    private boolean isReferenceInstalled(String packagename, PackageManager pm) {
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (NameNotFoundException e) {
            if (DEBUG) Log.e(TAG, "Store is not installed: " + packagename, e);
            return false;
        }
    }

    private int getRecentGravity() {
        // Get user gravity.
        int userGravity = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.RECENT_PANEL_GRAVITY, Gravity.RIGHT,
                UserHandle.USER_CURRENT);
        if (getContext().getResources()
                .getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (userGravity == Gravity.LEFT) {
                return Gravity.RIGHT;
            } else {
                return Gravity.LEFT;
            }
        } else {
            return userGravity;
        }
    }
}
