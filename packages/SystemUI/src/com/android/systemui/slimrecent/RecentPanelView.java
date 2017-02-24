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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import android.widget.ImageButton;
import android.widget.ImageView;

import com.android.cards.recyclerview.internal.BaseRecyclerViewAdapter.CardViewHolder;
import com.android.cards.recyclerview.internal.CardArrayRecyclerViewAdapter;
import com.android.cards.recyclerview.view.CardRecyclerView;
import com.android.cards.internal.Card;
import com.android.systemui.R;

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

    private final CardRecyclerView mCardRecyclerView;
    private CardArrayRecyclerViewAdapter mCardAdapter;

    private final RecentController mController;

    // Array list of all current cards
    private ArrayList<Card> mCards;
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
            CardRecyclerView recyclerView, ImageView emptyRecentView) {
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
        mCards = new ArrayList<>();
        mCardAdapter = new CardArrayRecyclerViewAdapter(mContext, mCards);
        if (mCardRecyclerView != null) {
            mCardRecyclerView.setAdapter(mCardAdapter);
        }
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder, 
                    ViewHolder target) {
                return true;
            }

            @Override
            public void onSwiped(ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                RecentCard card = (RecentCard) mCards.get(pos);
                mCards.remove(pos);
                removeApplication(card.getTaskDescription());
                mCardAdapter.notifyItemRemoved(pos);
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView,
                    RecyclerView.ViewHolder viewHolder) {
                // Set movement flags based on the layout manager
                final int dragFlags = 0;
                final int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                return makeMovementFlags(dragFlags, swipeFlags);
            }
        });
        touchHelper.attachToRecyclerView(mCardRecyclerView);
    }

    /**
     * Assign the listeners to the card.
     */
    private RecentCard assignListeners(final RecentCard card, final TaskDescription td) {
        if (DEBUG) Log.v(TAG, "add listeners to task card");

        // Listen for onClick to start the app with custom animation
        card.setOnClickListener(new Card.OnCardClickListener() {
            @Override
            public void onClick(Card card, View view) {
                startApplication(td);
            }
        });

        // App icon has own onLongClick action. Listen for it and
        // process the favorite action for it.
        card.addPartialOnLongClickListener(Card.CLICK_LISTENER_THUMBNAIL_VIEW,
                new Card.OnLongCardClickListener() {
            @Override
            public boolean onLongClick(Card card, View view) {
                RecentImageView favoriteIcon =
                        (RecentImageView) view.findViewById(R.id.card_thumbnail_favorite);
                favoriteIcon.setVisibility(td.getIsFavorite() ? View.INVISIBLE : View.VISIBLE);
                handleFavoriteEntry(td);
                return true;
            }
        });

        // Listen for card is expanded to save current value for next recent call
        card.setOnExpandAnimatorEndListener(new Card.OnExpandAnimatorEndListener() {
            @Override
            public void onExpandEnd(Card card) {
                if (DEBUG) Log.v(TAG, td.getLabel() + " is expanded");
                final int oldState = td.getExpandedState();
                int state = EXPANDED_STATE_EXPANDED;
                if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                    state |= EXPANDED_STATE_BY_SYSTEM;
                }
                td.setExpandedState(state);
            }
        });
        // Listen for card is collapsed to save current value for next recent call
        card.setOnCollapseAnimatorEndListener(new Card.OnCollapseAnimatorEndListener() {
            @Override
            public void onCollapseEnd(Card card) {
                if (DEBUG) Log.v(TAG, td.getLabel() + " is collapsed");
                final int oldState = td.getExpandedState();
                int state = EXPANDED_STATE_COLLAPSED;
                if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                    state |= EXPANDED_STATE_BY_SYSTEM;
                }
                td.setExpandedState(state);
            }
        });
        return card;
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

            // Remove app from task, cache and expanded state list.
            removeApplicationBitmapCacheAndExpandedState(td);
        }

        // All apps were removed? Close recents panel.
        if (mCards.size() == 0) {
            setVisibility();
            exit();
        }
    }

    /**
     * Remove all applications. Call from controller class
     */
    protected boolean removeAllApplications() {
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        boolean hasFavorite = false;
        int size = mCards.size() - 1;
        for (int i = size; i >= 0; i--) {
            RecentCard card = (RecentCard) mCards.get(i);;
            TaskDescription td = card.getTaskDescription();
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
            // Remove bitmap and expanded state.
            removeApplicationBitmapCacheAndExpandedState(td);
        }
        return !hasFavorite;
    }

    private void removeRecentCard(RecentCard card) {
        int pos = mCards.indexOf(card);
        mCards.remove(pos);
        mCardAdapter.notifyItemRemoved(pos);
    }

    /**
     * Remove application bitmaps from LRU cache and expanded state list.
     */
    private void removeApplicationBitmapCacheAndExpandedState(TaskDescription td) {
        // Remove application thumbnail.
        CacheController.getInstance(mContext)
                .removeBitmapFromMemCache(String.valueOf(td.persistentTaskId));
        // Remove application icon.
        CacheController.getInstance(mContext)
                .removeBitmapFromMemCache(td.identifier);
        // Remove from expanded state list.
        removeExpandedTaskState(td.identifier);
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
        for (Card c : mCards) {
            TaskDescription task = ((RecentCard) c).getTaskDescription();
            if (uriReference.equals(task.packageName)) {
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
        cardLoader.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Set correct visibility states for the listview and the empty recent icon.
     */
    private void setVisibility() {
        mEmptyRecentView.setVisibility(mCards.size() == 0 ? View.VISIBLE : View.GONE);
        mCardRecyclerView.setVisibility(mCards.size() == 0 ? View.GONE : View.VISIBLE);
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Update the List for actual apps.
     */
    private void updateExpandedTaskStates() {
        for (Card card : mCards) {
            TaskDescription item = ((RecentCard) card).getTaskDescription();
            boolean updated = false;
            for (TaskExpandedStates expandedState : mExpandedTaskStates) {
                if (item.identifier.equals(expandedState.getIdentifier())) {
                    updated = true;
                    expandedState.setExpandedState(item.getExpandedState());
                }
            }
            if (!updated) {
                mExpandedTaskStates.add(
                        new TaskExpandedStates(
                                item.identifier, item.getExpandedState()));
            }
        }
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
            // We want to have the list scrolled down before it is visible for the user.
            // Whoever calls notifyDataSetChanged() first (not visible) do it now.
            if (mCardRecyclerView != null) {
               // mCardRecyclerView.setSelection(mCards.size() - 1);
            }
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

    protected void dismissPopup() {
        for (Card card : mCards) {
            card.hideOptions(-1, -1);
        }
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
        for (Card card : mCards) {
            TaskDescription td = ((RecentCard) card).getTaskDescription();
            if (td.getIsFavorite()) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasClearableTasks() {
        for (Card card : mCards) {
            TaskDescription td = ((RecentCard) card).getTaskDescription();
            if (!td.getIsFavorite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notify listener that tasks are loaded.
     */
    private void tasksLoaded() {
        if (mOnTasksLoadedListener != null) {
            setTasksLoaded(true);
            mIsLoading = false;
            mOnTasksLoadedListener.onTasksLoaded();
        }
    }

    /**
     * Notify listener that we exit recents panel now.
     */
    private void exit() {
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
    private class CardLoader extends AsyncTask<Void, Void, Boolean> {

        private int mOrigPri;
        private int mCounter;

        public CardLoader() {
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // Save current thread priority and set it during the loading
            // to background priority.
            mOrigPri = Process.getThreadPriority(Process.myTid());
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            final int oldSize = mCards.size();
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
                    am.getRecentTasksForUser(maxNumTasksToLoad,
                    ActivityManager.RECENT_IGNORE_HOME_STACK_TASKS
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
                    mCards.remove(i);
                }
            }

            return true;
        }

        private void addCard(TaskDescription task, int oldSize, boolean topTask) {
            RecentCard card = null;

            // We may have already constructed and inflated card.
            // Let us reuse them and just update the content.
            if (mCounter < oldSize) {
                card = (RecentCard) mCards.get(mCounter);
                if (card != null) {
                    if (DEBUG) Log.v(TAG, "loading tasks - update old card");
                    card.updateCardContent(task, mScaleFactor);
                    card = assignListeners(card, task);
                }
                mCounter++;
            }

            // No old card was present to update....so add a new one.
            if (card == null) {
                if (DEBUG) Log.v(TAG, "loading tasks - create new card");
                card = new RecentCard(mContext, task, mScaleFactor);
                card = assignListeners(card, task);
                mCards.add(card);
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

            // Restore original thread priority.
            Process.setThreadPriority(mOrigPri);

            // Set correct view visibilitys
            setVisibility();

            // Notify arrayadapter that data set has changed
            if (DEBUG) Log.v(TAG, "notifiy arrayadapter that data has changed");
            notifyDataSetChanged(true);
            // Notfiy controller that tasks are completly loaded.
            tasksLoaded();
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
}
