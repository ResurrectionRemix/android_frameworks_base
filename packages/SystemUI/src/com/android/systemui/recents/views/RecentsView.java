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
 * limitations under the License.
 */

package com.android.systemui.recents.views;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityOptions;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.TaskStackBuilder;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.ViewAnimationUtils;
import android.view.Gravity;
import android.util.EventLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.ImageButton;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.recents.Constants;
import com.android.systemui.recents.RecentsAppWidgetHostView;
import com.android.systemui.recents.RecentsConfiguration;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.model.RecentsPackageMonitor;
import com.android.systemui.recents.model.RecentsTaskLoader;
import com.android.systemui.recents.model.Task;
import com.android.systemui.recents.model.TaskStack;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;

import com.android.systemui.EventLogTags;

import java.util.ArrayList;
import java.util.List;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.Callbacks;


/**
 * This view is the the top level layout that contains TaskStacks (which are laid out according
 * to their SpaceNode bounds.
 */
public  class RecentsView extends FrameLayout implements TaskStackView.TaskStackViewCallbacks ,
        RecentsPackageMonitor.PackageCallbacks  {

	   static final String TAG = "RecentsView";
    /** The RecentsView callbacks */
    public interface RecentsViewCallbacks {
        public void onTaskViewClicked();
        public void onTaskLaunchFailed();
        public void onAllTaskViewsDismissed();
        public void onExitToHomeAnimationTriggered();
        public void onScreenPinningRequest();
        public void onTaskResize(Task t);
        public void runAfterPause(Runnable r);
    }

    RecentsConfiguration mConfig;
    LayoutInflater mInflater;
    DebugOverlayView mDebugOverlay;
    RecentsViewLayoutAlgorithm mLayoutAlgorithm;

    ArrayList<TaskStack> mStacks;
    List<TaskStackView> mTaskStackViews = new ArrayList<>();
    RecentsAppWidgetHostView mSearchBar;
    RecentsViewCallbacks mCb;
    View mClearRecents;
    View mFloatingButton;
    TextView mMemText;
    ProgressBar mMemBar;

    private ActivityManager mAm;
    private int mTotalMem;

    public int mClearStyle;
    public boolean mClearStyleSwitch = false;
    private int mfabcolor ;
    private ImageButton button;
    private boolean mButtonsRotation;
    private boolean mClearallRotation;
    private boolean ClearallTasks;

    TextClock mClock;
    TextView mDate;

    public RecentsView(Context context) {
        super(context);
    }

    public RecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mConfig = RecentsConfiguration.getInstance();
        mInflater = LayoutInflater.from(context);
        mLayoutAlgorithm = new RecentsViewLayoutAlgorithm(mConfig);
        mAm = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    /** Sets the callbacks */
    public void setCallbacks(RecentsViewCallbacks cb) {
        mCb = cb;
    }

    /** Sets the debug overlay */
    public void setDebugOverlay(DebugOverlayView overlay) {
        mDebugOverlay = overlay;
    }

    /** Set/get the bsp root node */
    public void setTaskStacks(ArrayList<TaskStack> stacks) {
        int numStacks = stacks.size();

        // Remove all/extra stack views
        int numTaskStacksToKeep = 0; // Keep no tasks if we are recreating the layout
        if (mConfig.launchedReuseTaskStackViews) {
            numTaskStacksToKeep = Math.min(mTaskStackViews.size(), numStacks);
        }
        for (int i = mTaskStackViews.size() - 1; i >= numTaskStacksToKeep; i--) {
            removeView(mTaskStackViews.remove(i));
        }

        // Update the stack views that we are keeping
        for (int i = 0; i < numTaskStacksToKeep; i++) {
            TaskStackView tsv = mTaskStackViews.get(i);
            // If onRecentsHidden is not triggered, we need to the stack view again here
            tsv.reset();
            tsv.setStack(stacks.get(i));
        }

        // Add remaining/recreate stack views
        mStacks = stacks;
        for (int i = mTaskStackViews.size(); i < numStacks; i++) {
            TaskStack stack = stacks.get(i);
            TaskStackView stackView = new TaskStackView(getContext(), stack);
            stackView.setCallbacks(this);
            addView(stackView);
            mTaskStackViews.add(stackView);
        }

        // Enable debug mode drawing on all the stacks if necessary
        if (mConfig.debugModeEnabled) {
            for (int i = mTaskStackViews.size() - 1; i >= 0; i--) {
                TaskStackView stackView = mTaskStackViews.get(i);
                stackView.setDebugOverlay(mDebugOverlay);
            }
        }

        // Trigger a new layout
        requestLayout();
    }

    /** Gets the list of task views */
    List<TaskStackView> getTaskStackViews() {
        return mTaskStackViews;
    }

    /** Gets the next task in the stack - or if the last - the top task */
    public Task getNextTaskOrTopTask(Task taskToSearch) {
        Task returnTask = null;
        boolean found = false;
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = stackCount - 1; i >= 0; --i) {
            TaskStack stack = stackViews.get(i).getStack();
            ArrayList<Task> taskList = stack.getTasks();
            // Iterate the stack views and try and find the focused task
            for (int j = taskList.size() - 1; j >= 0; --j) {
                Task task = taskList.get(j);
                // Return the next task in the line.
                if (found)
                    return task;
                // Remember the first possible task as the top task.
                if (returnTask == null)
                    returnTask = task;
                if (task == taskToSearch)
                    found = true;
            }
        }
        return returnTask;
    }

    public void dismissAllTasksAnimated() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child != mSearchBar) {
                TaskStackView stackView = (TaskStackView) child;
                stackView.dismissAllTasks();
            }
        }
    }

    /** Launches the focused task from the first stack if possible */
    public boolean launchFocusedTask() {
        // Get the first stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            TaskStack stack = stackView.getStack();
            // Iterate the stack views and try and find the focused task
            List<TaskView> taskViews = stackView.getTaskViews();
            int taskViewCount = taskViews.size();
            for (int j = 0; j < taskViewCount; j++) {
                TaskView tv = taskViews.get(j);
                Task task = tv.getTask();
                if (tv.isFocusedTask()) {
                    onTaskViewClicked(stackView, tv, stack, task, false);
                    return true;
                }
            }
        }
        return false;
    }

    /** Launches a given task. */
    public boolean launchTask(Task task) {
        // Get the first stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            TaskStack stack = stackView.getStack();
            // Iterate the stack views and try and find the given task.
            List<TaskView> taskViews = stackView.getTaskViews();
            int taskViewCount = taskViews.size();
            for (int j = 0; j < taskViewCount; j++) {
                TaskView tv = taskViews.get(j);
                if (tv.getTask() == task) {
                    onTaskViewClicked(stackView, tv, stack, task, false);
                    return true;
                }
            }
        }
        return false;
    }

    /** Launches the task that Recents was launched from, if possible */
    public boolean launchPreviousTask() {
        // Get the first stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            TaskStack stack = stackView.getStack();
            ArrayList<Task> tasks = stack.getTasks();

            // Find the launch task in the stack
            if (!tasks.isEmpty()) {
                int taskCount = tasks.size();
                for (int j = 0; j < taskCount; j++) {
                    if (tasks.get(j).isLaunchTarget) {
                        Task task = tasks.get(j);
                        TaskView tv = stackView.getChildViewForTask(task);
                        onTaskViewClicked(stackView, tv, stack, task, false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Requests all task stacks to start their enter-recents animation */
    public void startEnterRecentsAnimation(ViewAnimation.TaskViewEnterContext ctx) {
        // We have to increment/decrement the post animation trigger in case there are no children
        // to ensure that it runs
        ctx.postAnimationTrigger.increment();

        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            stackView.startEnterRecentsAnimation(ctx);
        }
        ctx.postAnimationTrigger.decrement();

        EventLog.writeEvent(EventLogTags.SYSUI_RECENTS_EVENT, 1 /* opened */);
    }

    /** Requests all task stacks to start their exit-recents animation */
    public void startExitToHomeAnimation(ViewAnimation.TaskViewExitContext ctx) {
        // We have to increment/decrement the post animation trigger in case there are no children
        // to ensure that it runs
        ctx.postAnimationTrigger.increment();
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            stackView.startExitToHomeAnimation(ctx);
        }
        ctx.postAnimationTrigger.decrement();

        // Notify of the exit animation
        mCb.onExitToHomeAnimationTriggered();
    }

    /** Adds the search bar */
    public void setSearchBar(RecentsAppWidgetHostView searchBar) {
        // Remove the previous search bar if one exists
        if (mSearchBar != null && indexOfChild(mSearchBar) > -1) {
            removeView(mSearchBar);
        }
        // Add the new search bar
        if (searchBar != null) {
            mSearchBar = searchBar;
            addView(mSearchBar);
        }
    }

    /** Returns whether there is currently a search bar */
    public boolean hasValidSearchBar() {
        return mSearchBar != null && !mSearchBar.isReinflateRequired();
    }

    /** Sets the visibility of the search bar */
    public void setSearchBarVisibility(int visibility) {
        if (mSearchBar != null) {
            mSearchBar.setVisibility(visibility);
            // Always bring the search bar to the top
            mSearchBar.bringToFront();
        }
    }

    /**
     * This is called with the full size of the window since we are handling our own insets.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final ContentResolver resolver = mContext.getContentResolver();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        Rect searchBarSpaceBounds = new Rect();

        int paddingStatusBar = mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_height) / 2;


        boolean enableMemDisplay = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SYSTEMUI_RECENTS_MEM_DISPLAY, 1) == 1;

	mClearStyle = Settings.System.getIntForUser(
                    resolver, Settings.System.CLEAR_RECENTS_STYLE, 0,
                    UserHandle.USER_CURRENT);
	checkstyle(mClearStyle); 	
        
        // Get the search bar bounds and measure the search bar layout
        if (mSearchBar != null && mConfig.searchBarEnabled) {
            mConfig.getSearchBarBounds(width, height, mConfig.systemInsets.top, searchBarSpaceBounds);
            mSearchBar.measure(
                    MeasureSpec.makeMeasureSpec(searchBarSpaceBounds.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(searchBarSpaceBounds.height(), MeasureSpec.EXACTLY));
            boolean isLandscape1 = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

            int paddingSearchBar = searchBarSpaceBounds.height() + 25;

            if (enableMemDisplay) {
                if (!isLandscape1) {
                    mMemBar.setPadding(0, paddingSearchBar, 0, 0);
                } else {
                    mMemBar.setPadding(0, paddingStatusBar, 0, 0);
                }
            }
        } else {
            if (enableMemDisplay) {
                mMemBar.setPadding(0, paddingStatusBar, 0, 0);
            }
        }
        showMemDisplay();


        updateTimeVisibility();

        boolean showClearAllRecents = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SHOW_CLEAR_ALL_RECENTS, 0, UserHandle.USER_CURRENT) != 0;

        Rect taskStackBounds = new Rect();
        mConfig.getAvailableTaskStackBounds(width, height, mConfig.systemInsets.top,
                mConfig.systemInsets.right, searchBarSpaceBounds, taskStackBounds);

        if (mFloatingButton != null && showClearAllRecents) {
            int clearRecentsLocation = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.RECENTS_CLEAR_ALL_LOCATION,
            Constants.DebugFlags.App.RECENTS_CLEAR_ALL_BOTTOM_RIGHT, UserHandle.USER_CURRENT);

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                    mFloatingButton.getLayoutParams();
            boolean isLandscape = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
            if (mSearchBar == null || isLandscape) {
                params.topMargin = mContext.getResources().
                    getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
            } else {
                params.topMargin = mContext.getResources().
                    getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height)
                        + searchBarSpaceBounds.height();
            }

            switch (clearRecentsLocation) {
                case Constants.DebugFlags.App.RECENTS_CLEAR_ALL_TOP_LEFT:
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    break;
                case Constants.DebugFlags.App.RECENTS_CLEAR_ALL_TOP_RIGHT:
                    params.gravity = Gravity.TOP | Gravity.RIGHT;
                    break;
                case Constants.DebugFlags.App.RECENTS_CLEAR_ALL_TOP_CENTER:
                    params.gravity = Gravity.TOP | Gravity.CENTER;
                    break;
                case Constants.DebugFlags.App.RECENTS_CLEAR_ALL_BOTTOM_LEFT:
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    break;
                case Constants.DebugFlags.App.RECENTS_CLEAR_ALL_BOTTOM_RIGHT:
                default:
                    params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    break;
                case Constants.DebugFlags.App.RECENTS_CLEAR_ALL_BOTTOM_CENTER:
                    params.gravity = Gravity.BOTTOM | Gravity.CENTER;
                    break;
            }
            mFloatingButton.setLayoutParams(params);
        } else {
            mFloatingButton.setVisibility(View.GONE);
        }

        // Measure each TaskStackView with the full width and height of the window since the
        // transition view is a child of that stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        List<Rect> stackViewsBounds = mLayoutAlgorithm.computeStackRects(stackViews,
                taskStackBounds);
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            if (stackView.getVisibility() != GONE) {
                // We are going to measure the TaskStackView with the whole RecentsView dimensions,
                // but the actual stack is going to be inset to the bounds calculated by the layout
                // algorithm
                stackView.setStackInsetRect(stackViewsBounds.get(i));
                stackView.measure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        setMeasuredDimension(width, height);
    }

    private boolean showMemDisplay() {
        boolean enableMemDisplay = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SYSTEMUI_RECENTS_MEM_DISPLAY, 0) == 1;

        if (!enableMemDisplay) {
            mMemText.setVisibility(View.GONE);
            mMemBar.setVisibility(View.GONE);
            return false;
        }
        mMemText.setVisibility(View.VISIBLE);
        mMemBar.setVisibility(View.VISIBLE);

        updateMemoryStatus();
        return true;
    }

    private void updateMemoryStatus() {
        if (mMemText.getVisibility() == View.GONE
                || mMemBar.getVisibility() == View.GONE) return;

        MemoryInfo memInfo = new MemoryInfo();
        mAm.getMemoryInfo(memInfo);
            int available = (int)(memInfo.availMem / 1048576L);
            int max = (int)(getTotalMemory() / 1048576L);
            mMemText.setText("Free RAM: " + String.valueOf(available) + "MB");
            mMemBar.setMax(max);
            mMemBar.setProgress(available);
    }

    public long getTotalMemory() {
        MemoryInfo memInfo = new MemoryInfo();
        mAm.getMemoryInfo(memInfo);
        long totalMem = memInfo.totalMem;
        return totalMem;
    }

    public void noUserInteraction() {
        if (mClearRecents != null) {
            mClearRecents.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
	final ContentResolver resolver = mContext.getContentResolver();
        mFloatingButton = ((View)getParent()).findViewById(R.id.floating_action_button);
	mClearStyle = Settings.System.getIntForUser(
                    resolver, Settings.System.CLEAR_RECENTS_STYLE, 0,
                    UserHandle.USER_CURRENT);
        mClearStyleSwitch  = Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.CLEAR_RECENTS_STYLE_ENABLE, 0) == 1;
	mfabcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FAB_BUTTON_COLOR, 0xffDC4C3C);			
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents);
	if (mClearStyleSwitch) {
	checkstyle(mClearStyle); 
	} else {
		mClearRecents.setVisibility(View.VISIBLE);
		mClearRecents.setOnClickListener(new View.OnClickListener() {
          	public void onClick(View v) {
                	dismissAllTasksAnimated();
                	updateMemoryStatus();
            		}
        	});
	}	
        mMemText = (TextView) ((View)getParent()).findViewById(R.id.recents_memory_text);
        mMemBar = (ProgressBar) ((View)getParent()).findViewById(R.id.recents_memory_bar);

        updateMemoryStatus();

        mClock = (TextClock) ((View)getParent()).findViewById(R.id.recents_clock);
        mDate = (TextView) ((View)getParent()).findViewById(R.id.recents_date);
        updateTimeVisibility();
	
    }

    public void checkstyle(int style) {
	final ContentResolver resolver = mContext.getContentResolver();
	mButtonsRotation =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.RECENTS_ROTATE_FAB, 0) == 1;	
	mClearStyle = Settings.System.getIntForUser(
                    resolver, Settings.System.CLEAR_RECENTS_STYLE, 0,
                    UserHandle.USER_CURRENT);	
        final Resources res = getContext().getResources();
        mClearStyleSwitch  = Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.CLEAR_RECENTS_STYLE_ENABLE, 0) == 1;	
	mfabcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FAB_BUTTON_COLOR, 0xffDC4C3C);	
	int mbarcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.MEM_BAR_COLOR, 0xff009688);	
	int mtextcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.MEM_TEXT_COLOR, 0xFFFFFFFF);
	int mclearallcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.CLEAR_BUTTON_COLOR, 0xFFFFFFFF);
	int mClockcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.RECENTS_CLOCK_COLOR, 0xFFFFFFFF);
	int mDatecolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.RECENTS_DATE_COLOR, 0xFFFFFFFF);
        ClearallTasks =   Settings.System.getInt(mContext.getContentResolver(),
		     Settings.System.RECENTS_CLEAR_ALL_DISMISS_ALL, 1) == 1;	
	int mDefaultcolor = res.getColor(R.color.recents_membar_text_color);
	int mSetfabcolor = res.getColor(R.color.fab_color);

	mClearStyle = style;
	if (mClearStyleSwitch) {
	mFloatingButton = ((View)getParent()).findViewById(R.id.floating_action_button);
	mMemBar = (ProgressBar) ((View)getParent()).findViewById(R.id.recents_memory_bar);
	mMemText = (TextView) ((View)getParent()).findViewById(R.id.recents_memory_text);
	mClock = (TextClock) ((View)getParent()).findViewById(R.id.recents_clock);
        mDate = (TextView) ((View)getParent()).findViewById(R.id.recents_date);
	mFloatingButton.getBackground().setColorFilter(mSetfabcolor, Mode.CLEAR); 
	mFloatingButton.getBackground().setColorFilter(mfabcolor, Mode.SRC_IN); 
	MemoryInfo memInfo = new MemoryInfo();
        mAm.getMemoryInfo(memInfo);
	updateMemoryStatus();
	mMemBar.getProgressDrawable().setColorFilter(mbarcolor, Mode.MULTIPLY); 
	mMemText.setTextColor(mtextcolor);
	if (mClock !=null) {
	mClock.setTextColor(mClockcolor); }
        if(mDate !=null) {
	mDate.setTextColor(mDatecolor); }
	
	if (style == 0) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 1) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents1);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents1);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 2) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents2);
        button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents2);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 3) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents3);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents3);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 4) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents4);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents4);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 5) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents5);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents5);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 6) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents6);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents6);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 7) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents7);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents7);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 8) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents8);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents8);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 9) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents9);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents9);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 10) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents10);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents10);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
		} 
	if (style == 11) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents11);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents11);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 12) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents12);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents12);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 13) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents13);
        button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents13);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 14) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents14);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents14);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 15) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents15);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents15);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 16) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents16);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents16);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 17) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents17);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents17);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 18) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents18);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents18);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 19) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents19);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents19);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 20) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents20);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents20);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 21) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents21);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents21);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
		} 
	if (style == 22) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents22);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents22);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 23) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents23);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents23);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 24) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents24);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents24);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	}
	if (style == 25) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents25);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents25);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 26) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents26);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents26);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} if (style == 27) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents27);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents27);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	if (style == 28) {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents28);
	button = (ImageButton) ((View)getParent()).findViewById(R.id.clear_recents28);      
	button.setColorFilter(mclearallcolor, Mode.SRC_IN);
	mClearRecents.setVisibility(View.VISIBLE);
	} 
	mClearRecents.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
               if (mButtonsRotation) {
		dismissAllTasksAnimated();
		checkrotation();
                updateMemoryStatus();
		} else {
		dismissAllTasksAnimated();
                updateMemoryStatus();
		}
            }
        });		
	} else {
	mClearRecents.setVisibility(View.GONE);	
	mClearRecents = ((View)getParent()).findViewById(R.id.clear_recents);
	mClearRecents.setVisibility(View.VISIBLE); 
	mFloatingButton = ((View)getParent()).findViewById(R.id.floating_action_button);
	mMemBar = (ProgressBar) ((View)getParent()).findViewById(R.id.recents_memory_bar);
	mMemText = (TextView) ((View)getParent()).findViewById(R.id.recents_memory_text);
	mClock = (TextClock) ((View)getParent()).findViewById(R.id.recents_clock);
        mDate = (TextView) ((View)getParent()).findViewById(R.id.recents_date);
	mFloatingButton.getBackground().setColorFilter(null);
	mMemBar.getProgressDrawable().setColorFilter(null);
	mMemText.setTextColor(mDefaultcolor);
	mClock.setTextColor(mDefaultcolor);
	mDate.setTextColor(mDefaultcolor);
	mClearRecents.getBackground().setColorFilter(null);
	mClearRecents.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {               
		dismissAllTasksAnimated();
                updateMemoryStatus();
            }
        });
	}
     }

    public void checkrotation() {
		final ContentResolver resolver = mContext.getContentResolver();
		Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.rotate_around_center);
		Animation animation1 = AnimationUtils.loadAnimation(mContext, R.anim.recent_exit);
		Animation animation2 = AnimationUtils.loadAnimation(mContext, R.anim.translucent_exit);
		Animation animation3 = AnimationUtils.loadAnimation(mContext, R.anim.translucent_exit_ribbon);
		Animation animation4 = AnimationUtils.loadAnimation(mContext, R.anim.tn_toast_exit);
		Animation animation5 = AnimationUtils.loadAnimation(mContext, R.anim.slide_out_down);
		Animation animation6 = AnimationUtils.loadAnimation(mContext, R.anim.xylon_toast_exit);
		Animation animation7 = AnimationUtils.loadAnimation(mContext, R.anim.honami_toast_exit);
		Animation animation8 = AnimationUtils.loadAnimation(mContext, R.anim.slide_out_right);
		Animation animation9 = AnimationUtils.loadAnimation(mContext, R.anim.tn_toast_exit);
		Animation animation10 = AnimationUtils.loadAnimation(mContext, R.anim.slow_fade_out);
		Animation animation11 = AnimationUtils.loadAnimation(mContext, R.anim.slide_out_left);
		Animation animation12 = AnimationUtils.loadAnimation(mContext, R.anim.fade_out);
		Animation animation13 = AnimationUtils.loadAnimation(mContext, R.anim.fast_fade_out);
		Animation animation14 = AnimationUtils.loadAnimation(mContext, R.anim.slide_out_up);
		Animation animation15 = AnimationUtils.loadAnimation(mContext, R.anim.rotate_super_fast);
		Animation animation16 = AnimationUtils.loadAnimation(mContext, R.anim.rotate_super_slow);
	        Animation animationdefault = AnimationUtils.loadAnimation(mContext, R.anim.fab_deault);
		mButtonsRotation =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.RECENTS_ROTATE_FAB, 0) == 1;	
		int mAnimStyle =  Settings.System.getIntForUser(
                    resolver, Settings.System.FAB_ANIMATION_STYLE, 0,
                    UserHandle.USER_CURRENT);
                boolean ClearallTasks =   Settings.System.getInt(mContext.getContentResolver(),
		     Settings.System.RECENTS_CLEAR_ALL_DISMISS_ALL, 0) == 1;	
		if(mButtonsRotation) {	
			if (mAnimStyle ==0) {	
			mFloatingButton.startAnimation(animation);
			mClearRecents.startAnimation(animation);  
			} 	
			if (mAnimStyle ==1) {	
	        	mFloatingButton.startAnimation(animation1);
			mClearRecents.startAnimation(animation1);  
			}
			if (mAnimStyle ==2) {	 
	        	mFloatingButton.startAnimation(animation2); 
			mClearRecents.startAnimation(animation2); 
			}
			if (mAnimStyle ==3) {		
	        	mFloatingButton.startAnimation(animation3); 
			mClearRecents.startAnimation(animation3); 
			}
			if (mAnimStyle ==4) {		
	        	mFloatingButton.startAnimation(animation4);
			mClearRecents.startAnimation(animation4); 
			} 
			if (mAnimStyle ==5) {		
	        	mFloatingButton.startAnimation(animation5); 
			mClearRecents.startAnimation(animation5); 
			}
			if (mAnimStyle ==6) {		
	        	mFloatingButton.startAnimation(animation6); 
			mClearRecents.startAnimation(animation6); 
			}
			if (mAnimStyle ==7) {		
	        	mFloatingButton.startAnimation(animation7); 
			mClearRecents.startAnimation(animation7); 
			}
			if (mAnimStyle ==8) {		 
	        	mFloatingButton.startAnimation(animation8); 
			mClearRecents.startAnimation(animation8); 
			}
			if (mAnimStyle ==9) {		
	        	mFloatingButton.startAnimation(animation9);
			mClearRecents.startAnimation(animation9); 
			} 
			if (mAnimStyle ==10) {		
	        	mFloatingButton.startAnimation(animation10); 
			mClearRecents.startAnimation(animation10); 
			}
			if (mAnimStyle ==11) {		
	        	mFloatingButton.startAnimation(animation11); 
			mClearRecents.startAnimation(animation11); 
			}
			if (mAnimStyle ==12) {		
	        	mFloatingButton.startAnimation(animation12); 
			mClearRecents.startAnimation(animation12); 
			}
			if (mAnimStyle ==13) {		 
	        	mFloatingButton.startAnimation(animation13); 
			mClearRecents.startAnimation(animation13); 
			}
			if (mAnimStyle ==14) {		 
	        	mFloatingButton.startAnimation(animation14); 
			mClearRecents.startAnimation(animation14);
			}
			if (mAnimStyle ==15) {		 
	        	mFloatingButton.startAnimation(animation15); 
			mClearRecents.startAnimation(animation15); 
			}
			if (mAnimStyle ==16) {		 
	        	mFloatingButton.startAnimation(animation16); 
			mClearRecents.startAnimation(animation16); 
			}
		} else {
		mFloatingButton.startAnimation(animationdefault);
		mClearRecents.startAnimation(animationdefault); 
		}	
		if (ClearallTasks) {
		mFloatingButton.setVisibility(View.GONE);
		mClearRecents.setVisibility(View.GONE);	
		} 	  
   }	
   

    public void updateTimeVisibility() {
        boolean showClock = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.RECENTS_FULL_SCREEN_CLOCK, 0, UserHandle.USER_CURRENT) != 0;
        boolean showDate = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.RECENTS_FULL_SCREEN_DATE, 0, UserHandle.USER_CURRENT) != 0;
        boolean fullscreenEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.IMMERSIVE_RECENTS, 0, UserHandle.USER_CURRENT) != 0;

        if (fullscreenEnabled) {
            if (showClock) {
                mClock.setVisibility(View.VISIBLE);
            } else {
                mClock.setVisibility(View.GONE);
            }
            if (showDate) {
                long dateStamp = System.currentTimeMillis();
                DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(mContext);
                String currentDateString =  dateFormat.format(dateStamp);
                mDate.setText(currentDateString);
                mDate.setVisibility(View.VISIBLE);
            } else {
                mDate.setVisibility(View.GONE);
            }
        } else {
            mClock.setVisibility(View.GONE);
            mDate.setVisibility(View.GONE);
        }
    }

    /**
     * This is called with the full size of the window since we are handling our own insets.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Get the search bar bounds so that we lay it out
        if (mSearchBar != null && mConfig.searchBarEnabled) {
            Rect searchBarSpaceBounds = new Rect();
            mConfig.getSearchBarBounds(getMeasuredWidth(), getMeasuredHeight(),
                    mConfig.systemInsets.top, searchBarSpaceBounds);
            mSearchBar.layout(searchBarSpaceBounds.left, searchBarSpaceBounds.top,
                    searchBarSpaceBounds.right, searchBarSpaceBounds.bottom);
        }

        // Layout each TaskStackView with the full width and height of the window since the 
        // transition view is a child of that stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            if (stackView.getVisibility() != GONE) {
                stackView.layout(left, top, left + stackView.getMeasuredWidth(),
                        top + stackView.getMeasuredHeight());
            }
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Update the configuration with the latest system insets and trigger a relayout
        mConfig.updateSystemInsets(insets.getSystemWindowInsets());
        requestLayout();
        return insets.consumeSystemWindowInsets();
    }

    /** Notifies each task view of the user interaction. */
    public void onUserInteraction() {
        // Get the first stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            stackView.onUserInteraction();
        }
    }

    /** Focuses the next task in the first stack view */
    public void focusNextTask(boolean forward) {
        // Get the first stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        if (!stackViews.isEmpty()) {
            stackViews.get(0).focusNextTask(forward, true);
        }
    }

    /** Dismisses the focused task. */
    public void dismissFocusedTask() {
        // Get the first stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        if (!stackViews.isEmpty()) {
            stackViews.get(0).dismissFocusedTask();
        }
    }

    /** Unfilters any filtered stacks */
    public boolean unfilterFilteredStacks() {
        if (mStacks != null) {
            // Check if there are any filtered stacks and unfilter them before we back out of Recents
            boolean stacksUnfiltered = false;
            int numStacks = mStacks.size();
            for (int i = 0; i < numStacks; i++) {
                TaskStack stack = mStacks.get(i);
                if (stack.hasFilteredTasks()) {
                    stack.unfilterTasks();
                    stacksUnfiltered = true;
                }
            }
            return stacksUnfiltered;
        }
        return false;
    }

    public void disableLayersForOneFrame() {
        List<TaskStackView> stackViews = getTaskStackViews();
        for (int i = 0; i < stackViews.size(); i++) {
            stackViews.get(i).disableLayersForOneFrame();
        }
    }

    private void postDrawHeaderThumbnailTransitionRunnable(final TaskView tv, final int offsetX,
            final int offsetY, final TaskViewTransform transform,
            final ActivityOptions.OnAnimationStartedListener animStartedListener) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                // Disable any focused state before we draw the header
                if (tv.isFocusedTask()) {
                    tv.unsetFocusedTask();
                }

                float scale = tv.getScaleX();
                int fromHeaderWidth = (int) (tv.mHeaderView.getMeasuredWidth() * scale);
                int fromHeaderHeight = (int) (tv.mHeaderView.getMeasuredHeight() * scale);

                Bitmap b = Bitmap.createBitmap(fromHeaderWidth, fromHeaderHeight,
                        Bitmap.Config.ARGB_8888);
                if (Constants.DebugFlags.App.EnableTransitionThumbnailDebugMode) {
                    b.eraseColor(0xFFff0000);
                } else {
                    Canvas c = new Canvas(b);
                    c.scale(tv.getScaleX(), tv.getScaleY());
                    tv.mHeaderView.draw(c);
                    c.setBitmap(null);
                }
                b = b.createAshmemBitmap();
                int[] pts = new int[2];
                tv.getLocationOnScreen(pts);
                try {
                    WindowManagerGlobal.getWindowManagerService()
                            .overridePendingAppTransitionAspectScaledThumb(b,
                                    pts[0] + offsetX,
                                    pts[1] + offsetY,
                                    transform.rect.width(),
                                    transform.rect.height(),
                                    new IRemoteCallback.Stub() {
                                        @Override
                                        public void sendResult(Bundle data)
                                                throws RemoteException {
                                            post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (animStartedListener != null) {
                                                        animStartedListener.onAnimationStarted();
                                                    }
                                                }
                                            });
                                        }
                                    }, true);
                } catch (RemoteException e) {
                    Log.w(TAG, "Error overriding app transition", e);
                }
            }
        };
        mCb.runAfterPause(r);
    }
    /**** TaskStackView.TaskStackCallbacks Implementation ****/

    @Override
    public void onTaskViewClicked(final TaskStackView stackView, final TaskView tv,
                                  final TaskStack stack, final Task task, final boolean lockToTask) {

        // Notify any callbacks of the launching of a new task
        if (mCb != null) {
            mCb.onTaskViewClicked();
        }

        // Upfront the processing of the thumbnail
        TaskViewTransform transform = new TaskViewTransform();
        View sourceView;
        int offsetX = 0;
        int offsetY = 0;
        float stackScroll = stackView.getScroller().getStackScroll();
        if (tv == null) {
            // If there is no actual task view, then use the stack view as the source view
            // and then offset to the expected transform rect, but bound this to just
            // outside the display rect (to ensure we don't animate from too far away)
            sourceView = stackView;
            transform = stackView.getStackAlgorithm().getStackTransform(task, stackScroll, transform, null);
            offsetX = transform.rect.left;
            offsetY = mConfig.displayRect.height();
        } else {
            sourceView = tv.mThumbnailView;
            transform = stackView.getStackAlgorithm().getStackTransform(task, stackScroll, transform, null);
        }

        // Compute the thumbnail to scale up from
        final SystemServicesProxy ssp =
                RecentsTaskLoader.getInstance().getSystemServicesProxy();
        ActivityOptions opts = null;
        if (task.thumbnail != null && task.thumbnail.getWidth() > 0 &&
                task.thumbnail.getHeight() > 0) {
            ActivityOptions.OnAnimationStartedListener animStartedListener = null;
            if (lockToTask) {
                animStartedListener = new ActivityOptions.OnAnimationStartedListener() {
                    boolean mTriggered = false;
                    @Override
                    public void onAnimationStarted() {
                        if (!mTriggered) {
                            postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mCb.onScreenPinningRequest();
                                }
                            }, 350);
                            mTriggered = true;
                        }
                    }
                };
            }
            if (tv != null) {
                postDrawHeaderThumbnailTransitionRunnable(tv, offsetX, offsetY, transform,
                        animStartedListener);
            }
            if (mConfig.multiStackEnabled) {
                opts = ActivityOptions.makeCustomAnimation(sourceView.getContext(),
                        R.anim.recents_from_unknown_enter,
                        R.anim.recents_from_unknown_exit,
                        sourceView.getHandler(), animStartedListener);
            } else {
                opts = ActivityOptions.makeThumbnailAspectScaleUpAnimation(sourceView,
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8).createAshmemBitmap(),
                        offsetX, offsetY, transform.rect.width(), transform.rect.height(),
                        sourceView.getHandler(), animStartedListener);
            }
        }

        final ActivityOptions launchOpts = opts;
        final Runnable launchRunnable = new Runnable() {
            @Override
            public void run() {
                if (task.isActive) {
                    // Bring an active task to the foreground
                    ssp.moveTaskToFront(task.key.id, launchOpts);
                } else {
                    if (ssp.startActivityFromRecents(getContext(), task.key.id,
                            task.activityLabel, launchOpts)) {
                        if (launchOpts == null && lockToTask) {
                            mCb.onScreenPinningRequest();
                        }
                    } else {
                        // Dismiss the task and return the user to home if we fail to
                        // launch the task
                        onTaskViewDismissed(task);
                        if (mCb != null) {
                            mCb.onTaskLaunchFailed();
                        }

                        // Keep track of failed launches
                        MetricsLogger.count(getContext(), "overview_task_launch_failed", 1);
                    }
                }
            }
        };

        // Keep track of the index of the task launch
        int taskIndexFromFront = 0;
        int taskIndex = stack.indexOfTask(task);
        if (taskIndex > -1) {
            taskIndexFromFront = stack.getTaskCount() - taskIndex - 1;
        }
        MetricsLogger.histogram(getContext(), "overview_task_launch_index", taskIndexFromFront);

        // Launch the app right away if there is no task view, otherwise, animate the icon out first
        if (tv == null) {
            launchRunnable.run();
        } else {
            if (task.group != null && !task.group.isFrontMostTask(task)) {
                // For affiliated tasks that are behind other tasks, we must animate the front cards
                // out of view before starting the task transition
                stackView.startLaunchTaskAnimation(tv, launchRunnable, lockToTask);
            } else {
                // Otherwise, we can start the task transition immediately
                stackView.startLaunchTaskAnimation(tv, null, lockToTask);
                launchRunnable.run();
            }
        }

        EventLog.writeEvent(EventLogTags.SYSUI_RECENTS_EVENT, 3 /* chose task */);
    }

    @Override
    public void onTaskViewAppInfoClicked(Task t) {
        // Create a new task stack with the application info details activity
        Intent baseIntent = t.key.baseIntent;
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", baseIntent.getComponent().getPackageName(), null));
        intent.setComponent(intent.resolveActivity(getContext().getPackageManager()));
        TaskStackBuilder.create(getContext())
                .addNextIntentWithParentStack(intent).startActivities(null,
                new UserHandle(t.key.userId));
    }

    @Override
    public void onTaskFloatClicked(Task t) {
        Intent baseIntent = t.key.baseIntent;
        // Hide and go home
        onRecentsHidden();
        mCb.onTaskLaunchFailed();
        // Launch task in floating mode
        baseIntent.setFlags(Intent.FLAG_FLOATING_WINDOW
                  | Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(baseIntent);
    }

    @Override
    public void onTaskViewDismissed(Task t) {
        // Remove any stored data from the loader.  We currently don't bother notifying the views
        // that the data has been unloaded because at the point we call onTaskViewDismissed(), the views
        // either don't need to be updated, or have already been removed.
        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        loader.deleteTaskData(t, false);

        // Remove the old task from activity manager
        loader.getSystemServicesProxy().removeTask(t.key.id);

        updateMemoryStatus();
    }

    @Override
    public void onAllTaskViewsDismissed(ArrayList<Task> removedTasks) {
        if (removedTasks != null) {
            int taskCount = removedTasks.size();
            for (int i = 0; i < taskCount; i++) {
                onTaskViewDismissed(removedTasks.get(i));
            }
        }

        mCb.onAllTaskViewsDismissed();

        // Keep track of all-deletions
        MetricsLogger.count(getContext(), "overview_task_all_dismissed", 1);
        EventLog.writeEvent(EventLogTags.SYSUI_RECENTS_EVENT, 4 /* closed all */);
    }

    /** Final callback after Recents is finally hidden. */
    public void onRecentsHidden() {
        // Notify each task stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            stackView.onRecentsHidden();
        }
        EventLog.writeEvent(EventLogTags.SYSUI_RECENTS_EVENT, 2 /* closed */);
    }

    @Override
    public void onTaskStackFilterTriggered() {
        // Hide the search bar
        if (mSearchBar != null) {
            mSearchBar.animate()
                    .alpha(0f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutSlowInInterpolator)
                    .setDuration(mConfig.filteringCurrentViewsAnimDuration)
                    .withLayer()
                    .start();
        }
    }

    @Override
    public void onTaskStackUnfilterTriggered() {
        // Show the search bar
        if (mSearchBar != null) {
            mSearchBar.animate()
                    .alpha(1f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutSlowInInterpolator)
                    .setDuration(mConfig.filteringNewViewsAnimDuration)
                    .withLayer()
                    .start();
        }
    }

    @Override
    public void onTaskResize(Task t) {
        if (mCb != null) {
            mCb.onTaskResize(t);
        }
    }

    /**** RecentsPackageMonitor.PackageCallbacks Implementation ****/

    @Override
    public void onPackagesChanged(RecentsPackageMonitor monitor, String packageName, int userId) {
        // Propagate this event down to each task stack view
        List<TaskStackView> stackViews = getTaskStackViews();
        int stackCount = stackViews.size();
        for (int i = 0; i < stackCount; i++) {
            TaskStackView stackView = stackViews.get(i);
            stackView.onPackagesChanged(monitor, packageName, userId);
        }
    }
}
