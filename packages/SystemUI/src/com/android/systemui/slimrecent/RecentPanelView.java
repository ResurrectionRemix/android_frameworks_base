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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.slimrecent.ExpandableCardAdapter.ExpandableCard;
import com.android.systemui.slimrecent.ExpandableCardAdapter.OptionsItem;
import com.android.systemui.stackdivider.WindowManagerProxy;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import java.io.IOException;
import java.lang.ref.WeakReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Our main view controller which handles and construct most of the view
 * related tasks.
 *
 * Constructing the actual cards, add the listeners, loading or updating the tasks
 * and inform all relevant classes with the listeners is done here.
 *
 * As well the actual click, longpress or swipe action methods are holded here.
 */
public class RecentPanelView {

    private static final String TAG = "RecentPanelView";
    public static final boolean DEBUG = true;

    public static final String TASK_PACKAGE_IDENTIFIER = "#ident:";

    private static final int EXPANDED_STATE_UNKNOWN  = 0;
    public static final int EXPANDED_STATE_EXPANDED  = 1;
    public static final int EXPANDED_STATE_COLLAPSED = 2;
    public static final int EXPANDED_STATE_BY_SYSTEM = 4;
    public static final int EXPANDED_STATE_TOPTASK   = 8;

    public static final int EXPANDED_MODE_AUTO    = 0;
    private static final int EXPANDED_MODE_ALWAYS = 1;
    private static final int EXPANDED_MODE_NEVER  = 2;

    private static final int MENU_APP_DETAILS_ID   = 0;
    private static final int MENU_APP_PLAYSTORE_ID = 1;
    private static final int MENU_APP_AMAZON_ID    = 2;

    public static final String PLAYSTORE_REFERENCE = "com.android.vending";
    public static final String AMAZON_REFERENCE    = "com.amazon.venezia";

    public static final String PLAYSTORE_APP_URI_QUERY = "market://details?id=";
    public static final String AMAZON_APP_URI_QUERY    = "amzn://apps/android?p=";

    private final Context mContext;
    private final ImageView mEmptyRecentView;

    private final RecyclerView mCardRecyclerView;
    private ExpandableCardAdapter mCardAdapter;

    private final RecentController mController;

    // Our first task which is not displayed but needed for internal references.
    protected TaskDescription mFirstTask;
    // Array list of all expanded states of apps accessed during the session
    private final ArrayList<TaskExpandedStates> mExpandedTaskStates =
            new ArrayList<TaskExpandedStates>();

    private boolean mCancelledByUser;
    private boolean mTasksLoaded;
    private boolean mIsLoading;

    private int mMainGravity;
    private float mScaleFactor;
    private int mExpandedMode = EXPANDED_MODE_AUTO;
    private boolean mShowTopTask;
    private boolean mOnlyShowRunningTasks;
    private static int mCardColor = 0x0ffffff;

    final static BitmapFactory.Options sBitmapOptions;

    static {
        sBitmapOptions = new BitmapFactory.Options();
        sBitmapOptions.inMutable = true;
    }

    private static final int OPTION_INFO = 1001;
    private static final int OPTION_MARKET = 1002;
    private static final int OPTION_MULTIWINDOW = 1003;
    private static final int OPTION_CLOSE = 1004;

    private class RecentCard extends ExpandableCard {
        TaskDescription task;
        int position;

        private RecentCard(TaskDescription task) {
            super(task.getLabel(), null);
            setTask(task);
        }

        private void setTask(TaskDescription task) {
            this.task = task;
            this.appName = task.getLabel();
            updateExpandState();

            this.favorite = task.getIsFavorite();
            this.appIconLongClickListener = new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    favorite = !favorite;
                    handleFavoriteEntry(task);
                    mCardAdapter.notifyItemChanged(position);
                    return true;
                }
            };

            this.expandListener = new ExpandableCardAdapter.ExpandListener() {
                @Override
                public void onExpanded(boolean expanded) {
                    final int oldState = task.getExpandedState();
                    int state;
                    if (expanded) {
                        state = EXPANDED_STATE_EXPANDED;
                    } else {
                        state = EXPANDED_STATE_COLLAPSED;
                    }
                    if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                        state |= EXPANDED_STATE_BY_SYSTEM;
                    }
                    task.setExpandedState(state);
                }
            };

            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int id = v.getId();
                    Intent intent = null;
                    if (id == OPTION_INFO) {
                        intent = getAppInfoIntent();
                    } else if (id == OPTION_MARKET) {
                        intent = getStoreIntent();
                    } else if (id == OPTION_MULTIWINDOW) {
                        boolean wasDocked = false;
                        int dockSide = WindowManagerProxy.getInstance().getDockSide();
                        if (dockSide != WindowManager.DOCKED_INVALID) {
                            try {
                            // resize the docked stack to fullscreen to disable current multiwindow mode
                                ActivityManagerNative.getDefault().resizeStack(
                                                    ActivityManager.StackId.DOCKED_STACK_ID,
                                                    null, true, true, false, -1);
                            } catch (RemoteException e) {}
                            wasDocked = true;
                        }
                        ActivityOptions options = ActivityOptions.makeBasic();
                        options.setDockCreateMode(0);
                        options.setLaunchStackId(ActivityManager.StackId.DOCKED_STACK_ID);
                        Handler mHandler = new Handler();
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                try {
                                    ActivityManagerNative.getDefault()
                                            .startActivityFromRecents(task.persistentTaskId,
                                             options.toBundle());
                                    mController.openLastApptoBottom();
                                    clearOptions();
                                } catch (RemoteException e) {}
                            }
                        // if we disabled a running multiwindow mode, just wait a little bit before
                        // docking the new apps
                        }, wasDocked ? 100 : 0);
                        return;
                    }
                    if (intent != null) {
                        RecentController.sendCloseSystemWindows("close_recents");
                        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
                        TaskStackBuilder.create(mContext)
                                .addNextIntentWithParentStack(intent).startActivities(
                                    getAnimation(mContext, mMainGravity));
                    }
                }
            };

            clearOptions();
            addOption(new OptionsItem(
                    mContext.getDrawable(R.drawable.ic_info), OPTION_INFO, listener));
            if (checkAppInstaller(task.packageName, AMAZON_REFERENCE)
                    || checkAppInstaller(task.packageName, PLAYSTORE_REFERENCE)) {
                addOption(new OptionsItem(
                        mContext.getDrawable(R.drawable.ic_shop), OPTION_MARKET, listener));
            }
            addOption(new OptionsItem(
                    mContext.getDrawable(R.drawable.ic_multiwindow), OPTION_MULTIWINDOW, listener));
            addOption(new OptionsItem(
                    mContext.getDrawable(R.drawable.ic_done), OPTION_CLOSE, true));
        }

        private void updateExpandState() {
            // Read flags and set accordingly initial expanded state.
            final boolean isTopTask =
                    (task.getExpandedState() & EXPANDED_STATE_TOPTASK) != 0;

            final boolean isSystemExpanded =
                    (task.getExpandedState() & EXPANDED_STATE_BY_SYSTEM) != 0;

            final boolean isUserExpanded =
                    (task.getExpandedState() & EXPANDED_STATE_EXPANDED) != 0;

            final boolean isUserCollapsed =
                    (task.getExpandedState() & EXPANDED_STATE_COLLAPSED) != 0;

            final boolean isExpanded =
                    ((isSystemExpanded && !isUserCollapsed) || isUserExpanded) && !isTopTask;

            boolean screenPinningEnabled = screenPinningEnabled();
            expanded = isExpanded;
            expandVisible = !isTopTask;
            customIcon = isTopTask && screenPinningEnabled;
            custom = mContext.getDrawable(R.drawable.recents_lock_to_app_pin);
            customClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context appContext = mContext.getApplicationContext();
                    if (appContext == null) appContext = mContext;
                    if (appContext instanceof SystemUIApplication) {
                        SystemUIApplication app = (SystemUIApplication) appContext;
                        PhoneStatusBar statusBar = app.getComponent(PhoneStatusBar.class);
                        if (statusBar != null) {
                            statusBar.showScreenPinningRequest(task.persistentTaskId, false);
                        }
                    }
                }
            };
        }

        private boolean screenPinningEnabled() {
            return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_TO_APP_ENABLED, 0) != 0;
        }

        private Intent getAppInfoIntent() {
            return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", task.packageName, null));
        }

        private Intent getStoreIntent() {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String reference;
            if (checkAppInstaller(task.packageName, AMAZON_REFERENCE)) {
                reference = AMAZON_REFERENCE;
                intent.setData(Uri.parse(AMAZON_APP_URI_QUERY + task.packageName));
            } else {
                reference = PLAYSTORE_REFERENCE;
                intent.setData(Uri.parse(PLAYSTORE_APP_URI_QUERY + task.packageName));
            }
            // Exclude from recents if the store is not in our task list.
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            return intent;
        }
    }

    public interface OnExitListener {
        void onExit();
    }
    private OnExitListener mOnExitListener = null;

    public void setOnExitListener(OnExitListener onExitListener) {
        mOnExitListener = onExitListener;
    }

    public interface OnTasksLoadedListener {
        void onTasksLoaded();
    }
    private OnTasksLoadedListener mOnTasksLoadedListener = null;

    public void setOnTasksLoadedListener(OnTasksLoadedListener onTasksLoadedListener) {
        mOnTasksLoadedListener = onTasksLoadedListener;
    }

    public RecentPanelView(Context context, RecentController controller,
            RecyclerView recyclerView, ImageView emptyRecentView) {
        mContext = context;
        mCardRecyclerView = recyclerView;
        mEmptyRecentView = emptyRecentView;
        mController = controller;

        buildCardListAndAdapter();

        setupItemTouchHelper();
    }

    /**
     * Build card list and arrayadapter we need to fill with tasks
     */
    protected void buildCardListAndAdapter() {
        mCardAdapter = new ExpandableCardAdapter(mContext);
        if (mCardRecyclerView != null) {
            mCardRecyclerView.setAdapter(mCardAdapter);
        }
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

            RecentCard card;
            int taskid;
            int initPos;
            int finalPos;
            boolean isSwipe;
            boolean unwantedDrag = true;

            @Override
            public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder,
                    ViewHolder target) {

                /* We'll start multiwindow action in the clearView void, when the drag action
                and all animations are completed. Otherwise we'd do a loop action
                till the drag is completed for each onMove (wasting resources and making
                the drag not smooth).*/

                ExpandableCardAdapter.ViewHolder vh = (ExpandableCardAdapter.ViewHolder) viewHolder;
                vh.hideOptions(-1, -1);

                initPos = viewHolder.getAdapterPosition();
                card = (RecentCard) mCardAdapter.getCard(initPos);
                taskid = card.task.persistentTaskId;

                unwantedDrag = false;
                return true;
            }

            @Override
            public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                    int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
                finalPos = toPos;
                isSwipe = false;
            }

            @Override
            public float getMoveThreshold(RecyclerView.ViewHolder viewHolder) {
                // if less then this we consider it as unwanted drag
                return 0.2f;
            }

            @Override
            public void clearView (RecyclerView recyclerView,
                RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if (isSwipe) {
                    //don't start multiwindow on swipe
                    return;
                }

                if (unwantedDrag) {
                    /*this means MoveThreshold is less than needed, so onMove
                    has not been considered, so we don't consider the action as wanted drag*/
                    return;
                }

                unwantedDrag = true; //restore the drag check

                boolean wasDocked = false;
                int dockSide = WindowManagerProxy.getInstance().getDockSide();
                if (dockSide != WindowManager.DOCKED_INVALID) {
                    try {
                        //resize the docked stack to fullscreen to disable current multiwindow mode
                        ActivityManagerNative.getDefault().resizeStack(
                                            ActivityManager.StackId.DOCKED_STACK_ID,
                                            null, true, true, false, -1);
                    } catch (RemoteException e) {}
                    wasDocked = true;
                }

                ActivityOptions options = ActivityOptions.makeBasic();
                /* Activity Manager let's dock the app to top or bottom dinamically,
                with the setDockCreateMode DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT is 0,
                DOCKED_STACK_CREATE_MODE_BOTTOM_OR_RIGHT is 1. Thus if we drag down,
                dock app to bottom, if we drag up dock app to top*/
                options.setDockCreateMode(finalPos > initPos ? 0 : 1);
                options.setLaunchStackId(ActivityManager.StackId.DOCKED_STACK_ID);
                Handler mHandler = new Handler();
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        try {
                            ActivityManagerNative.getDefault()
                                    .startActivityFromRecents(taskid, options.toBundle());
                            card = (RecentCard) mCardAdapter.getCard(finalPos);
                            int newTaskid = card.task.persistentTaskId;
                            /*after we docked our main app, on the other side of the screen we
                            open the app we dragged the main app over*/
                            mController.openOnDraggedApptoOtherSide(newTaskid);
                        } catch (RemoteException e) {}
                    }
                //if we disabled a running multiwindow mode, just wait a little bit before docking the new apps
                }, wasDocked ? 100 : 0);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public void onSwiped(ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                RecentCard card = (RecentCard) mCardAdapter.getCard(pos);
                mCardAdapter.removeCard(pos);
                removeApplication(card.task);
                isSwipe = true;
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView,
                    RecyclerView.ViewHolder viewHolder) {
                // Set movement flags based on the layout manager
                final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                final int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                return makeMovementFlags(dragFlags, swipeFlags);
            }
        });
        touchHelper.attachToRecyclerView(mCardRecyclerView);
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
            if (DEBUG) Log.e(TAG, "Store is not installed: " + packagename);
            return false;
        }
    }

    /**
     * Handle favorite task entry (add or remove) if user longpressed on app icon.
     */
    private void handleFavoriteEntry(TaskDescription td) {
        ContentResolver resolver = mContext.getContentResolver();
        final String favorites = Settings.System.getStringForUser(
                    resolver, Settings.System.RECENT_PANEL_FAVORITES,
                    UserHandle.USER_CURRENT);
        String entryToSave = "";

        if (!td.getIsFavorite()) {
            if (favorites != null && !favorites.isEmpty()) {
                entryToSave += favorites + "|";
            }
            entryToSave += td.identifier;
        } else {
            if (favorites == null) {
                return;
            }
            for (String favorite : favorites.split("\\|")) {
                if (favorite.equals(td.identifier)) {
                    continue;
                }
                entryToSave += favorite + "|";
            }
            if (!entryToSave.isEmpty()) {
                entryToSave = entryToSave.substring(0, entryToSave.length() - 1);
            }
        }

        td.setIsFavorite(!td.getIsFavorite());

        Settings.System.putStringForUser(
                resolver, Settings.System.RECENT_PANEL_FAVORITES,
                entryToSave,
                UserHandle.USER_CURRENT);
    }

    /**
     * Get application launcher label of installed references.
     */
    private String getApplicationLabel(String packageName) {
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent = pm.getLaunchIntentForPackage(packageName);
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        if (resolveInfo != null) {
            return resolveInfo.activityInfo.loadLabel(pm).toString();
        }
        return null;
    }

    /**
     * Remove requested application.
     */
    private void removeApplication(TaskDescription td) {
        if (DEBUG) Log.v(TAG, "Jettison " + td.getLabel());

        // Kill the actual app and send accessibility event.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.removeTask(td.persistentTaskId);

            // Accessibility feedback
            mCardRecyclerView.setContentDescription(
                    mContext.getString(R.string.accessibility_recents_item_dismissed,
                            td.getLabel()));
            mCardRecyclerView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            mCardRecyclerView.setContentDescription(null);

            // Remove app from task and expanded state list.
            removeExpandedTaskState(td.identifier);
        }

        // All apps were removed? Close recents panel.
        if (mCardAdapter.getItemCount() == 0) {
            if (!(mEmptyRecentView.getDrawable() instanceof AnimatedVectorDrawable)) {
                setVisibility();
            }
            exit();
        }
        mController.updateMemoryStatus();
    }

    /**
     * Remove all applications. Call from controller class
     */
    protected boolean removeAllApplications() {
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        boolean hasFavorite = false;
        int size = mCardAdapter.getItemCount() - 1;
        for (int i = size; i >= 0; i--) {
            RecentCard card = (RecentCard) mCardAdapter.getCard(i);
            TaskDescription td = card.task;
            // User favorites are not removed.
            if (td.getIsFavorite()) {
                hasFavorite = true;
                continue;
            }
            // Remove from task stack.
            if (am != null) {
                am.removeTask(td.persistentTaskId);
            }
            // Remove the card.
            removeRecentCard(card);
            // Remove expanded state.
            removeExpandedTaskState(td.identifier);
        }
        return !hasFavorite;
    }

    private void removeRecentCard(RecentCard card) {
        mCardAdapter.removeCard(card);
    }

    /**
     * Start application or move to forground if still active.
     */
    protected void startApplication(TaskDescription td) {
        // Starting app is requested by the user.
        // Move it to foreground or start it with custom animation.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (td.taskId >= 0) {
            // This is an active task; it should just go to the foreground.
            am.moveTaskToFront(td.taskId, ActivityManager.MOVE_TASK_WITH_HOME,
                    getAnimation(mContext, mMainGravity));
        } else {
            final Intent intent = td.intent;
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                    | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (DEBUG) Log.v(TAG, "Starting activity " + intent);
            try {
                mContext.startActivityAsUser(intent, getAnimation(mContext, mMainGravity),
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (SecurityException e) {
                Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Error launching activity " + intent, e);
            }
        }
        mController.onLaunchApplication();
        exit();
    }

    /**
     * Get custom animation for app starting.
     * @return Bundle
     */
    public static Bundle getAnimation(Context context, int gravity) {
        return ActivityOptions.makeCustomAnimation(context,
                gravity == Gravity.RIGHT ?
                com.android.internal.R.anim.recent_screen_enter
                : com.android.internal.R.anim.recent_screen_enter_left,
                com.android.internal.R.anim.recent_screen_fade_out).toBundle();
    }

    /**
     * Check if the requested store is in the task list to prevent it gets excluded.
     */
    private boolean storeIsInTaskList(String uriReference) {
        if (mFirstTask != null && uriReference.equals(mFirstTask.packageName)) {
            return true;
        }
        int count = mCardAdapter.getItemCount();
        for (int i = 0;  i < count; i++) {
            RecentCard c = (RecentCard) mCardAdapter.getCard(i);
            if (uriReference.equals(c.task.packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a TaskDescription, returning null if the title or icon is null.
     */
    private TaskDescription createTaskDescription(int taskId, int persistentTaskId,
            Intent baseIntent, ComponentName origActivity,
            CharSequence description, boolean isFavorite, int expandedState,
            ActivityManager.TaskDescription td) {

        final Intent intent = new Intent(baseIntent);
        if (origActivity != null) {
            intent.setComponent(origActivity);
        }
        final PackageManager pm = mContext.getPackageManager();
        intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        if (resolveInfo != null) {
            final ActivityInfo info = resolveInfo.activityInfo;
            String title = td.getLabel();
            if (title == null) {
                title = info.loadLabel(pm).toString();
            }

            String identifier = TASK_PACKAGE_IDENTIFIER;
            final ComponentName component = intent.getComponent();
            if (component != null) {
                identifier += component.flattenToString();
            } else {
                identifier += info.packageName;
            }

            if (title != null && title.length() > 0) {
                if (DEBUG) Log.v(TAG, "creating activity desc for id="
                        + persistentTaskId + ", label=" + title);
                int color = td.getPrimaryColor();

                final TaskDescription item = new TaskDescription(taskId,
                        persistentTaskId, resolveInfo, baseIntent, info.packageName,
                        identifier, description, isFavorite, expandedState, color);
                item.setLabel(title);
                return item;
            } else {
                if (DEBUG) Log.v(TAG, "SKIPPING item " + persistentTaskId);
            }
        }
        return null;
    }

    /**
     * Load all tasks we want.
     */
    protected void loadTasks() {
        if (isTasksLoaded() || mIsLoading) {
            return;
        }
        if (DEBUG) Log.v(TAG, "loading tasks");
        mIsLoading = true;
        updateExpandedTaskStates();

        // We have all needed tasks now.
        // Let us load the cards for it in background.
        final CardLoader cardLoader = new CardLoader();
        cardLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        mController.updateMemoryStatus();
    }

    /**
     * Set correct visibility states for the listview and the empty recent icon.
     */
    private void setVisibility() {
        mEmptyRecentView.setVisibility((
                mCardAdapter.getItemCount() == 0) ? View.VISIBLE : View.GONE);
        mCardRecyclerView.setVisibility((
                mCardAdapter.getItemCount() == 0) ? View.GONE : View.VISIBLE);

        if (mEmptyRecentView.getDrawable() instanceof AnimatedVectorDrawable) {
            AnimatedVectorDrawable vd = (AnimatedVectorDrawable) mEmptyRecentView.getDrawable();
            if (mCardAdapter.getItemCount() == 0) {
                vd.start();
            } else {
                vd.stop();
            }
        }
        mController.updateMemoryStatus();
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Update the List for actual apps.
     */
    private void updateExpandedTaskStates() {
        int count = mCardAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            RecentCard card = (RecentCard) mCardAdapter.getCard(i);
            boolean updated = false;
            for (TaskExpandedStates expandedState : mExpandedTaskStates) {
                if (card.task.identifier.equals(expandedState.getIdentifier())) {
                    updated = true;
                    expandedState.setExpandedState(card.task.getExpandedState());
                }
            }
            if (!updated) {
                mExpandedTaskStates.add(
                        new TaskExpandedStates(
                                card.task.identifier, card.task.getExpandedState()));
            }
        }
        mController.updateMemoryStatus();
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Get expanded state of the app.
     */
    private int getExpandedState(TaskDescription item) {
        for (TaskExpandedStates oldTask : mExpandedTaskStates) {
            if (DEBUG) Log.v(TAG, "old task launch uri = "+ oldTask.getIdentifier()
                    + " new task launch uri = " + item.identifier);
            if (item.identifier.equals(oldTask.getIdentifier())) {
                    return oldTask.getExpandedState();
            }
        }
        return EXPANDED_STATE_UNKNOWN;
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Remove expanded state entry due that app was removed by the user.
     */
    private void removeExpandedTaskState(String identifier) {
        TaskExpandedStates expandedStateToDelete = null;
        for (TaskExpandedStates expandedState : mExpandedTaskStates) {
            if (expandedState.getIdentifier().equals(identifier)) {
                expandedStateToDelete = expandedState;
            }
        }
        if (expandedStateToDelete != null) {
            mExpandedTaskStates.remove(expandedStateToDelete);
        }
    }

    protected void notifyDataSetChanged(boolean forceupdate) {
        if (forceupdate || !mController.isShowing()) {
            mCardAdapter.notifyDataSetChanged();
        }
    }

    protected void setCancelledByUser(boolean cancelled) {
        mCancelledByUser = cancelled;
        if (cancelled) {
            setTasksLoaded(false);
        }
    }

    protected void setTasksLoaded(boolean loaded) {
        mTasksLoaded = loaded;
    }

    protected boolean isCancelledByUser() {
        return mCancelledByUser;
    }

    protected boolean isTasksLoaded() {
        return mTasksLoaded;
    }

    protected void setMainGravity(int gravity) {
        mMainGravity = gravity;
    }

    protected void setScaleFactor(float factor) {
        mScaleFactor = factor;
    }

    protected void setExpandedMode(int mode) {
        mExpandedMode = mode;
    }

    protected void setShowTopTask(boolean enabled) {
        mShowTopTask = enabled;
    }

    protected void setShowOnlyRunningTasks(boolean enabled) {
        mOnlyShowRunningTasks = enabled;
    }

    protected boolean hasFavorite() {
        int count = mCardAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            RecentCard card = (RecentCard) mCardAdapter.getCard(i);
            if (card.task.getIsFavorite()) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasClearableTasks() {
        int count = mCardAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            RecentCard card = (RecentCard) mCardAdapter.getCard(i);
            if (!card.task.getIsFavorite()) {
                return true;
            }
        }
        return false;
    }

    protected void setCardColor(int color) {
        mCardColor = color;
    }

    /**
     * Notify listener that tasks are loaded.
     */
    private void tasksLoaded() {
        if (mOnTasksLoadedListener != null) {
            mIsLoading = false;
            if (!isCancelledByUser()) {
                setTasksLoaded(true);
                mOnTasksLoadedListener.onTasksLoaded();
            }
        }
    }

    /**
     * Notify listener that we exit recents panel now.
     */
    private void exit() {
        setTasksLoaded(false);
        if (mOnExitListener != null) {
            mOnExitListener.onExit();
        }
    }

    protected void scrollToFirst() {
        LinearLayoutManager lm = (LinearLayoutManager) mCardRecyclerView.getLayoutManager();
        lm.scrollToPositionWithOffset(0, 0);
    }

    /**
     * AsyncTask cardloader to load all cards in background. Preloading
     * forces as well a card load or update. So if the user cancelled the preload
     * or does not even open the recent panel we want to reduce the system
     * load as much as possible. So we do it in background.
     *
     * Note: App icons as well the app screenshots are loaded in other
     *       async tasks.
     *       See #link:RecentCard, #link:RecentExpandedCard
     *       #link:RecentAppIcon and #link AppIconLoader
     */
    private class CardLoader extends AsyncTask<Void, ExpandableCard, Boolean> {

        //private int mOrigPri;
        private int mCounter;

        public CardLoader() {
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mCardAdapter.clearCards();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // Save current thread priority and set it during the loading
            // to background priority.
            //mOrigPri = Process.getThreadPriority(Process.myTid());
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

            final int oldSize = mCardAdapter.getItemCount();
            mCounter = 0;

            // Check and get user favorites.
            final String favorites = Settings.System.getStringForUser(
                    mContext.getContentResolver(), Settings.System.RECENT_PANEL_FAVORITES,
                    UserHandle.USER_CURRENT);
            final ArrayList<String> favList = new ArrayList<>();
            final ArrayList<TaskDescription> nonFavoriteTasks = new ArrayList<>();
            if (favorites != null && !favorites.isEmpty()) {
                for (String favorite : favorites.split("\\|")) {
                    favList.add(favorite);
                }
            }

            final PackageManager pm = mContext.getPackageManager();
            final ActivityManager am = (ActivityManager)
                    mContext.getSystemService(Context.ACTIVITY_SERVICE);

            int maxNumTasksToLoad = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.RECENTS_MAX_APPS, ActivityManager.getMaxRecentTasksStatic(),
                    UserHandle.USER_CURRENT);

            final List<ActivityManager.RecentTaskInfo> recentTasks =
                    am.getRecentTasksForUser(ActivityManager.getMaxRecentTasksStatic(),
                    ActivityManager.RECENT_IGNORE_HOME_STACK_TASKS
                            | ActivityManager.RECENT_INGORE_PINNED_STACK_TASKS
                            | ActivityManager.RECENT_IGNORE_UNAVAILABLE
                            | ActivityManager.RECENT_INCLUDE_PROFILES,
                            UserHandle.CURRENT.getIdentifier());

            final List<ActivityManager.RunningTaskInfo> runningTasks =
                   am.getRunningTasks(Integer.MAX_VALUE);
            final int numTasks = recentTasks.size();
            int newSize = numTasks;
            ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(pm, 0);

            int firstItems = 0;
            final int firstExpandedItems =
                    mContext.getResources().getInteger(R.integer.expanded_items_default);

            // Get current task list. We do not need to do it in background. We only load MAX_TASKS.
            for (int i = 0; i < numTasks; i++) {
                if (isCancelled() || mCancelledByUser) {
                    if (DEBUG) Log.v(TAG, "loading tasks cancelled");
                    mIsLoading = false;
                    return false;
                }

                final ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(i);

                final Intent intent = new Intent(recentInfo.baseIntent);
                if (recentInfo.origActivity != null) {
                    intent.setComponent(recentInfo.origActivity);
                }

                boolean topTask = i == 0;
                if (topTask) {
                    ActivityManager.RunningTaskInfo rTask = getRunningTask(am);
                    if (rTask != null) {
                        if (!rTask.baseActivity.getPackageName().equals(
                                recentInfo.baseIntent.getComponent().getPackageName())) {
                            topTask = false;
                        }
                    }
                }

                if (mOnlyShowRunningTasks) {
                    boolean isRunning = false;
                    for (ActivityManager.RunningTaskInfo task : runningTasks) {
                        if (recentInfo.baseIntent.getComponent().getPackageName().equals(
                                task.baseActivity.getPackageName())) {
                            isRunning = true;
                        }
                    }
                    if (!isRunning) {
                        newSize--;
                        continue;
                    }
                 }

                TaskDescription item = createTaskDescription(recentInfo.id,
                        recentInfo.persistentId, recentInfo.baseIntent,
                        recentInfo.origActivity, recentInfo.description,
                        false, EXPANDED_STATE_UNKNOWN, recentInfo.taskDescription);

                if (item != null) {
                    // Remove any tasks after our max task limit to keep good ux
                    if (i >= maxNumTasksToLoad) {
                        am.removeTask(item.persistentTaskId);
                        continue;
                    }
                    for (String fav : favList) {
                        if (fav.equals(item.identifier)) {
                            item.setIsFavorite(true);
                            break;
                        }
                    }

                    if (topTask) {
                        if (mShowTopTask || screenPinningEnabled()) {
                            // User want to see actual running task. Set it here
                            int oldState = getExpandedState(item);
                            if ((oldState & EXPANDED_STATE_TOPTASK) == 0) {
                                oldState |= EXPANDED_STATE_TOPTASK;
                            }
                            item.setExpandedState(oldState);
                            addCard(item, oldSize, true);
                            mFirstTask = item;
                        } else {
                            // Skip the first task for our list but save it for later use.
                            mFirstTask = item;
                            newSize--;
                        }
                    } else {
                        // FirstExpandedItems value forces to show always the app screenshot
                        // if the old state is not known and the user has set expanded mode to auto.
                        // On all other items we check if they were expanded from the user
                        // in last known recent app list and restore the state. This counts as well
                        // if expanded mode is always or never.
                        int oldState = getExpandedState(item);
                        if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                            oldState &= ~EXPANDED_STATE_BY_SYSTEM;
                        }
                        if ((oldState & EXPANDED_STATE_TOPTASK) != 0) {
                            oldState &= ~EXPANDED_STATE_TOPTASK;
                        }
                        if (DEBUG) Log.v(TAG, "old expanded state = " + oldState);
                        if (firstItems < firstExpandedItems) {
                            if (mExpandedMode != EXPANDED_MODE_NEVER) {
                                oldState |= EXPANDED_STATE_BY_SYSTEM;
                            }
                            item.setExpandedState(oldState);
                            // The first tasks are always added to the task list.
                            addCard(item, oldSize, false);
                        } else {
                            if (mExpandedMode == EXPANDED_MODE_ALWAYS) {
                                oldState |= EXPANDED_STATE_BY_SYSTEM;
                            }
                            item.setExpandedState(oldState);
                            // Favorite tasks are added next. Non favorite
                            // we hold for a short time in an extra list.
                            if (item.getIsFavorite()) {
                                addCard(item, oldSize, false);
                            } else {
                                nonFavoriteTasks.add(item);
                            }
                        }
                        firstItems++;
                    }
                }
            }

            // Add now the non favorite tasks to the final task list.
            for (TaskDescription item : nonFavoriteTasks) {
                addCard(item, oldSize, false);
            }

            // We may have unused cards left. Eg app was uninstalled but present
            // in the old task list. Let us remove them as well.
            if (newSize < oldSize) {
                for (int i = oldSize - 1; i >= newSize; i--) {
                    if (DEBUG) Log.v(TAG,
                            "loading tasks - remove not needed old card - position=" + i);
                    mCardAdapter.removeCard(i);
                }
            }

            return true;
        }

        private void addCard(final TaskDescription task, int oldSize, boolean topTask) {
            RecentCard card = null;

            final int index = mCounter;
            // We may have already constructed and inflated card.
            // Let us reuse them and just update the content.
            if (mCounter < oldSize) {
                card = (RecentCard) mCardAdapter.getCard(mCounter);
                if (card != null) {
                    if (DEBUG) Log.v(TAG, "loading tasks - update old card");
                    card.setTask(task);
                }
            }

            // No old card was present to update....so add a new one.
            if (card == null) {
                if (DEBUG) Log.v(TAG, "loading tasks - create new card");
                card = new RecentCard(task);
                card.position = index;
            }

            // Set card color
            card.cardBackgroundColor = getCardBackgroundColor(task);

            final ExpandableCard ec = card;
            AppIconLoader.getInstance(mContext).loadAppIcon(task.resolveInfo,
                            task.identifier, new AppIconLoader.IconCallback() {
                        @Override
                        public void onDrawableLoaded(Drawable drawable) {
                            ec.appIcon = drawable;
                            mCardAdapter.notifyItemChanged(index);
                        }
                    }, mScaleFactor);
            new BitmapDownloaderTask(mContext, mScaleFactor, new DownloaderCallback() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap) {
                            ec.screenshot = bitmap;
                            mCardAdapter.notifyItemChanged(index);
                        }
                    }).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, task.persistentTaskId);
            card.cardClickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startApplication(task);
                    }
            };
            mCounter++;
            publishProgress(card);
        }

        @Override
        protected void onProgressUpdate(ExpandableCard... card) {
            mCardAdapter.addCard(card[0]);
            if (!isTasksLoaded()) {
                //we have at least one task and card, so can show the panel while we
                //load more tasks and cards
               setVisibility();
               tasksLoaded();
            }
        }

        @Override
        protected void onPostExecute(Boolean loaded) {
            // If cancelled by system, log it and set task size
            // to the only visible tasks we have till now to keep task
            // removing alive. This should never happen. Just in case.
            if (!loaded) {
                Log.v(TAG, "card constructing was cancelled by system or user");
            }

            // Notify arrayadapter that data set has changed
            if (DEBUG) Log.v(TAG, "notifiy arrayadapter that data has changed");
            notifyDataSetChanged(true);
            // Notfiy controller that tasks are completly loaded.
            if (!isTasksLoaded()) {
                setVisibility();
                tasksLoaded();
            }
        }
    }

    private int getCardBackgroundColor(TaskDescription task) {
        if (mCardColor != 0x0ffffff) {
            return mCardColor;
        } else if (task != null && task.cardColor != 0) {
            return task.cardColor;
        } else {
            return mContext.getResources()
                    .getColor(R.color.recents_task_bar_default_background_color);
        }
    }

    private ActivityManager.RunningTaskInfo getRunningTask(ActivityManager am) {
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            return tasks.get(0);
        }
        return null;
    }

    private boolean screenPinningEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_TO_APP_ENABLED, 0) != 0;
    }

    /**
     * We are holding a list of user expanded states of apps.
     * This class describes one expanded state object.
     */
    private static final class TaskExpandedStates {
        private String mIdentifier;
        private int mExpandedState;

        public TaskExpandedStates(String identifier, int expandedState) {
            mIdentifier = identifier;
            mExpandedState = expandedState;
        }

        public String getIdentifier() {
            return mIdentifier;
        }

        public int getExpandedState() {
            return mExpandedState;
        }

        public void setExpandedState(int expandedState) {
            mExpandedState = expandedState;
        }
    }

    // Loads the actual task bitmap.
    private static Bitmap loadThumbnail(int persistentTaskId, Context context, float scaleFactor) {
        if (context == null) {
            return null;
        }
        final ActivityManager am = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        return getResizedBitmap(getThumbnail(am, persistentTaskId), context, scaleFactor);
    }

    /**
     * Returns a task thumbnail from the activity manager
     */
    public static Bitmap getThumbnail(ActivityManager activityManager, int taskId) {
        ActivityManager.TaskThumbnail taskThumbnail = activityManager.getTaskThumbnail(taskId);
        if (taskThumbnail == null) return null;

        Bitmap thumbnail = taskThumbnail.mainThumbnail;
        ParcelFileDescriptor descriptor = taskThumbnail.thumbnailFileDescriptor;
        if (thumbnail == null && descriptor != null) {
            thumbnail = BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(),
                    null, sBitmapOptions);
        }
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException e) {
            }
        }
        return thumbnail;
    }

    // Resize and crop the task bitmap to the overlay values.
    private static Bitmap getResizedBitmap(Bitmap source, Context context, float scaleFactor) {
        if (source == null || source.isRecycled()) {
            return null;
        }

        final Resources res = context.getResources();
        final int thumbnailWidth =
                (int) (res.getDimensionPixelSize(
                        R.dimen.recent_thumbnail_width) * scaleFactor);
        final int thumbnailHeight =
                (int) (res.getDimensionPixelSize(
                        R.dimen.recent_thumbnail_height) * scaleFactor);

        final int sourceWidth = source.getWidth();
        final int sourceHeight = source.getHeight();

        // Compute the scaling factors to fit the new height and width, respectively.
        // To cover the final image, the final scaling will be the bigger
        // of these two.
        final float xScale = (float) thumbnailWidth / sourceWidth;
        final float yScale = (float) thumbnailHeight / sourceHeight;
        final float scale = Math.max(xScale, yScale);

        // Now get the size of the source bitmap when scaled
        final float scaledWidth = scale * sourceWidth;
        final float scaledHeight = scale * sourceHeight;

        // Let's find out the left coordinates if the scaled bitmap
        // should be centered in the new size given by the parameters
        final float left = (thumbnailWidth - scaledWidth) / 2;

        // The target rectangle for the new, scaled version of the source bitmap
        final RectF targetRect = new RectF(left, 0.0f, left + scaledWidth, scaledHeight);

        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);

        // Finally, we create a new bitmap of the specified size and draw our new,
        // scaled bitmap onto it.
        final Bitmap dest = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Config.ARGB_8888);
        final Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, null, targetRect, paint);

        return dest;
    }

    interface DownloaderCallback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    // AsyncTask loader for the task bitmap.
    private static class BitmapDownloaderTask extends AsyncTask<Integer, Void, Bitmap> {

        private boolean mLoaded;

        private final WeakReference<Context> rContext;

        private float mScaleFactor;

        private DownloaderCallback mCallback;

        public BitmapDownloaderTask(Context context, float scaleFactor,
                                    DownloaderCallback callback) {
            rContext = new WeakReference<Context>(context);
            mScaleFactor = scaleFactor;
            mCallback = callback;
        }

        @Override
        protected Bitmap doInBackground(Integer... params) {
            mLoaded = false;
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
            if (isCancelled() || rContext == null) {
                return null;
            }
            // Load and return bitmap
            return loadThumbnail(params[0], rContext.get(), mScaleFactor);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            // Assign image to the view.
            mLoaded = true;
            if (mCallback != null) {
                mCallback.onBitmapLoaded(bitmap);
            }
        }

        public boolean isLoaded() {
            return mLoaded;
        }
    }
}
