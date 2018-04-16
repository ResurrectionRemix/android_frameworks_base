/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.am;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManager.TaskDescription;
import android.app.ActivityManager.TaskSnapshot;
import android.app.ActivityManager.TaskThumbnail;
import android.app.ActivityManager.TaskThumbnailInfo;
import android.app.ActivityOptions;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Debug;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.voice.IVoiceInteractionSession;
import android.util.DisplayMetrics;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IVoiceInteractor;
import com.android.internal.util.XmlUtils;

import com.android.server.wm.AppWindowContainerController;
import com.android.server.wm.StackWindowController;
import com.android.server.wm.TaskWindowContainerController;
import com.android.server.wm.TaskWindowContainerListener;
import com.android.server.wm.WindowManagerService;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Objects;

import static android.app.ActivityManager.RESIZE_MODE_FORCED;
import static android.app.ActivityManager.RESIZE_MODE_SYSTEM;
import static android.app.ActivityManager.StackId.ASSISTANT_STACK_ID;
import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.FULLSCREEN_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.app.ActivityManager.StackId.INVALID_STACK_ID;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import static android.app.ActivityManager.StackId.RECENTS_STACK_ID;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static android.content.Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS;
import static android.content.pm.ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY;
import static android.content.pm.ActivityInfo.LOCK_TASK_LAUNCH_MODE_ALWAYS;
import static android.content.pm.ActivityInfo.LOCK_TASK_LAUNCH_MODE_DEFAULT;
import static android.content.pm.ActivityInfo.LOCK_TASK_LAUNCH_MODE_IF_WHITELISTED;
import static android.content.pm.ActivityInfo.LOCK_TASK_LAUNCH_MODE_NEVER;
import static android.content.pm.ActivityInfo.RESIZE_MODE_FORCE_RESIZABLE_LANDSCAPE_ONLY;
import static android.content.pm.ActivityInfo.RESIZE_MODE_FORCE_RESIZABLE_PORTRAIT_ONLY;
import static android.content.pm.ActivityInfo.RESIZE_MODE_FORCE_RESIZABLE_PRESERVE_ORIENTATION;
import static android.content.pm.ActivityInfo.RESIZE_MODE_FORCE_RESIZEABLE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE_AND_PIPABLE_DEPRECATED;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
import static android.os.Trace.TRACE_TAG_ACTIVITY_MANAGER;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ADD_REMOVE;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_LOCKTASK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_RECENTS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_TASKS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_ADD_REMOVE;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_LOCKTASK;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_RECENTS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_TASKS;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.am.ActivityRecord.APPLICATION_ACTIVITY_TYPE;
import static com.android.server.am.ActivityRecord.ASSISTANT_ACTIVITY_TYPE;
import static com.android.server.am.ActivityRecord.HOME_ACTIVITY_TYPE;
import static com.android.server.am.ActivityRecord.RECENTS_ACTIVITY_TYPE;
import static com.android.server.am.ActivityRecord.STARTING_WINDOW_SHOWN;
import static com.android.server.am.ActivityStack.REMOVE_TASK_MODE_MOVING;
import static com.android.server.am.ActivityStack.REMOVE_TASK_MODE_MOVING_TO_TOP;
import static com.android.server.am.ActivityStackSupervisor.PAUSE_IMMEDIATELY;
import static com.android.server.am.ActivityStackSupervisor.PRESERVE_WINDOWS;

import static java.lang.Integer.MAX_VALUE;

final class TaskRecord extends ConfigurationContainer implements TaskWindowContainerListener {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "TaskRecord" : TAG_AM;
    private static final String TAG_ADD_REMOVE = TAG + POSTFIX_ADD_REMOVE;
    private static final String TAG_RECENTS = TAG + POSTFIX_RECENTS;
    private static final String TAG_LOCKTASK = TAG + POSTFIX_LOCKTASK;
    private static final String TAG_TASKS = TAG + POSTFIX_TASKS;

    private static final String ATTR_TASKID = "task_id";
    private static final String TAG_INTENT = "intent";
    private static final String TAG_AFFINITYINTENT = "affinity_intent";
    private static final String ATTR_REALACTIVITY = "real_activity";
    private static final String ATTR_REALACTIVITY_SUSPENDED = "real_activity_suspended";
    private static final String ATTR_ORIGACTIVITY = "orig_activity";
    private static final String TAG_ACTIVITY = "activity";
    private static final String ATTR_AFFINITY = "affinity";
    private static final String ATTR_ROOT_AFFINITY = "root_affinity";
    private static final String ATTR_ROOTHASRESET = "root_has_reset";
    private static final String ATTR_AUTOREMOVERECENTS = "auto_remove_recents";
    private static final String ATTR_ASKEDCOMPATMODE = "asked_compat_mode";
    private static final String ATTR_USERID = "user_id";
    private static final String ATTR_USER_SETUP_COMPLETE = "user_setup_complete";
    private static final String ATTR_EFFECTIVE_UID = "effective_uid";
    private static final String ATTR_TASKTYPE = "task_type";
    private static final String ATTR_FIRSTACTIVETIME = "first_active_time";
    private static final String ATTR_LASTACTIVETIME = "last_active_time";
    private static final String ATTR_LASTDESCRIPTION = "last_description";
    private static final String ATTR_LASTTIMEMOVED = "last_time_moved";
    private static final String ATTR_NEVERRELINQUISH = "never_relinquish_identity";
    private static final String ATTR_TASK_AFFILIATION = "task_affiliation";
    private static final String ATTR_PREV_AFFILIATION = "prev_affiliation";
    private static final String ATTR_NEXT_AFFILIATION = "next_affiliation";
    private static final String ATTR_TASK_AFFILIATION_COLOR = "task_affiliation_color";
    private static final String ATTR_CALLING_UID = "calling_uid";
    private static final String ATTR_CALLING_PACKAGE = "calling_package";
    private static final String ATTR_SUPPORTS_PICTURE_IN_PICTURE = "supports_picture_in_picture";
    private static final String ATTR_RESIZE_MODE = "resize_mode";
    private static final String ATTR_PRIVILEGED = "privileged";
    private static final String ATTR_NON_FULLSCREEN_BOUNDS = "non_fullscreen_bounds";
    private static final String ATTR_MIN_WIDTH = "min_width";
    private static final String ATTR_MIN_HEIGHT = "min_height";
    private static final String ATTR_PERSIST_TASK_VERSION = "persist_task_version";

    // Current version of the task record we persist. Used to check if we need to run any upgrade
    // code.
    private static final int PERSIST_TASK_VERSION = 1;
    private static final String TASK_THUMBNAIL_SUFFIX = "_task_thumbnail";

    static final int INVALID_TASK_ID = -1;
    private static final int INVALID_MIN_SIZE = -1;

    /**
     * The modes to control how the stack is moved to the front when calling
     * {@link TaskRecord#reparent}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            REPARENT_MOVE_STACK_TO_FRONT,
            REPARENT_KEEP_STACK_AT_FRONT,
            REPARENT_LEAVE_STACK_IN_PLACE
    })
    public @interface ReparentMoveStackMode {}
    // Moves the stack to the front if it was not at the front
    public static final int REPARENT_MOVE_STACK_TO_FRONT = 0;
    // Only moves the stack to the front if it was focused or front most already
    public static final int REPARENT_KEEP_STACK_AT_FRONT = 1;
    // Do not move the stack as a part of reparenting
    public static final int REPARENT_LEAVE_STACK_IN_PLACE = 2;

    final int taskId;       // Unique identifier for this task.
    String affinity;        // The affinity name for this task, or null; may change identity.
    String rootAffinity;    // Initial base affinity, or null; does not change from initial root.
    final IVoiceInteractionSession voiceSession;    // Voice interaction session driving task
    final IVoiceInteractor voiceInteractor;         // Associated interactor to provide to app
    Intent intent;          // The original intent that started the task.
    Intent affinityIntent;  // Intent of affinity-moved activity that started this task.
    int effectiveUid;       // The current effective uid of the identity of this task.
    ComponentName origActivity; // The non-alias activity component of the intent.
    ComponentName realActivity; // The actual activity component that started the task.
    boolean realActivitySuspended; // True if the actual activity component that started the
                                   // task is suspended.
    long firstActiveTime;   // First time this task was active.
    long lastActiveTime;    // Last time this task was active, including sleep.
    boolean inRecents;      // Actually in the recents list?
    boolean isAvailable;    // Is the activity available to be launched?
    boolean rootWasReset;   // True if the intent at the root of the task had
                            // the FLAG_ACTIVITY_RESET_TASK_IF_NEEDED flag.
    boolean autoRemoveRecents;  // If true, we should automatically remove the task from
                                // recents when activity finishes
    boolean askedCompatMode;// Have asked the user about compat mode for this task.
    boolean hasBeenVisible; // Set if any activities in the task have been visible to the user.

    String stringName;      // caching of toString() result.
    int userId;             // user for which this task was created
    boolean mUserSetupComplete; // The user set-up is complete as of the last time the task activity
                                // was changed.

    int numFullscreen;      // Number of fullscreen activities.

    int mResizeMode;        // The resize mode of this task and its activities.
                            // Based on the {@link ActivityInfo#resizeMode} of the root activity.
    private boolean mSupportsPictureInPicture;  // Whether or not this task and its activities
            // support PiP. Based on the {@link ActivityInfo#FLAG_SUPPORTS_PICTURE_IN_PICTURE} flag
            // of the root activity.
    boolean mTemporarilyUnresizable; // Separate flag from mResizeMode used to suppress resize
                                     // changes on a temporary basis.
    private int mLockTaskMode;  // Which tasklock mode to launch this task in. One of
                                // ActivityManager.LOCK_TASK_LAUNCH_MODE_*
    private boolean mPrivileged;    // The root activity application of this task holds
                                    // privileged permissions.

    /** Can't be put in lockTask mode. */
    final static int LOCK_TASK_AUTH_DONT_LOCK = 0;
    /** Can enter app pinning with user approval. Can never start over existing lockTask task. */
    final static int LOCK_TASK_AUTH_PINNABLE = 1;
    /** Starts in LOCK_TASK_MODE_LOCKED automatically. Can start over existing lockTask task. */
    final static int LOCK_TASK_AUTH_LAUNCHABLE = 2;
    /** Can enter lockTask without user approval. Can start over existing lockTask task. */
    final static int LOCK_TASK_AUTH_WHITELISTED = 3;
    /** Priv-app that starts in LOCK_TASK_MODE_LOCKED automatically. Can start over existing
     * lockTask task. */
    final static int LOCK_TASK_AUTH_LAUNCHABLE_PRIV = 4;
    int mLockTaskAuth = LOCK_TASK_AUTH_PINNABLE;

    int mLockTaskUid = -1;  // The uid of the application that called startLockTask().

    // This represents the last resolved activity values for this task
    // NOTE: This value needs to be persisted with each task
    TaskDescription lastTaskDescription = new TaskDescription();

    /** List of all activities in the task arranged in history order */
    final ArrayList<ActivityRecord> mActivities;

    /** Current stack. Setter must always be used to update the value. */
    private ActivityStack mStack;

    /** Takes on same set of values as ActivityRecord.mActivityType */
    int taskType;

    /** Takes on same value as first root activity */
    boolean isPersistable = false;
    int maxRecents;

    /** Only used for persistable tasks, otherwise 0. The last time this task was moved. Used for
     * determining the order when restoring. Sign indicates whether last task movement was to front
     * (positive) or back (negative). Absolute value indicates time. */
    long mLastTimeMoved = System.currentTimeMillis();

    /** Indication of what to run next when task exits. Use ActivityRecord types.
     * ActivityRecord.APPLICATION_ACTIVITY_TYPE indicates to resume the task below this one in the
     * task stack. */
    private int mTaskToReturnTo = APPLICATION_ACTIVITY_TYPE;

    /** If original intent did not allow relinquishing task identity, save that information */
    private boolean mNeverRelinquishIdentity = true;

    // Used in the unique case where we are clearing the task in order to reuse it. In that case we
    // do not want to delete the stack when the task goes empty.
    private boolean mReuseTask = false;

    private Bitmap mLastThumbnail; // Last thumbnail captured for this item.
    private final File mLastThumbnailFile; // File containing last thumbnail.
    private final String mFilename;
    private TaskThumbnailInfo mLastThumbnailInfo;
    CharSequence lastDescription; // Last description captured for this item.

    int mAffiliatedTaskId; // taskId of parent affiliation or self if no parent.
    int mAffiliatedTaskColor; // color of the parent task affiliation.
    TaskRecord mPrevAffiliate; // previous task in affiliated chain.
    int mPrevAffiliateTaskId = INVALID_TASK_ID; // previous id for persistence.
    TaskRecord mNextAffiliate; // next task in affiliated chain.
    int mNextAffiliateTaskId = INVALID_TASK_ID; // next id for persistence.

    // For relaunching the task from recents as though it was launched by the original launcher.
    int mCallingUid;
    String mCallingPackage;

    final ActivityManagerService mService;

    // Whether or not this task covers the entire screen; by default tasks are fullscreen.
    boolean mFullscreen = true;

    // Bounds of the Task. null for fullscreen tasks.
    Rect mBounds = null;
    private final Rect mTmpStableBounds = new Rect();
    private final Rect mTmpNonDecorBounds = new Rect();
    private final Rect mTmpRect = new Rect();

    // Last non-fullscreen bounds the task was launched in or resized to.
    // The information is persisted and used to determine the appropriate stack to launch the
    // task into on restore.
    Rect mLastNonFullscreenBounds = null;
    // Minimal width and height of this task when it's resizeable. -1 means it should use the
    // default minimal width/height.
    int mMinWidth;
    int mMinHeight;

    // Ranking (from top) of this task among all visible tasks. (-1 means it's not visible)
    // This number will be assigned when we evaluate OOM scores for all visible tasks.
    int mLayerRank = -1;

    /** Helper object used for updating override configuration. */
    private Configuration mTmpConfig = new Configuration();

    private TaskWindowContainerController mWindowContainerController;

    TaskRecord(ActivityManagerService service, int _taskId, ActivityInfo info, Intent _intent,
            IVoiceInteractionSession _voiceSession, IVoiceInteractor _voiceInteractor, int type) {
        mService = service;
        mFilename = String.valueOf(_taskId) + TASK_THUMBNAIL_SUFFIX +
                TaskPersister.IMAGE_EXTENSION;
        userId = UserHandle.getUserId(info.applicationInfo.uid);
        mLastThumbnailFile = new File(TaskPersister.getUserImagesDir(userId), mFilename);
        mLastThumbnailInfo = new TaskThumbnailInfo();
        taskId = _taskId;
        mAffiliatedTaskId = _taskId;
        voiceSession = _voiceSession;
        voiceInteractor = _voiceInteractor;
        isAvailable = true;
        mActivities = new ArrayList<>();
        mCallingUid = info.applicationInfo.uid;
        mCallingPackage = info.packageName;
        taskType = type;
        setIntent(_intent, info);
        setMinDimensions(info);
        touchActiveTime();
        mService.mTaskChangeNotificationController.notifyTaskCreated(_taskId, realActivity);
    }

    TaskRecord(ActivityManagerService service, int _taskId, ActivityInfo info, Intent _intent,
            TaskDescription _taskDescription, TaskThumbnailInfo thumbnailInfo) {
        mService = service;
        mFilename = String.valueOf(_taskId) + TASK_THUMBNAIL_SUFFIX +
                TaskPersister.IMAGE_EXTENSION;
        userId = UserHandle.getUserId(info.applicationInfo.uid);
        mLastThumbnailFile = new File(TaskPersister.getUserImagesDir(userId), mFilename);
        mLastThumbnailInfo = thumbnailInfo;
        taskId = _taskId;
        mAffiliatedTaskId = _taskId;
        voiceSession = null;
        voiceInteractor = null;
        isAvailable = true;
        mActivities = new ArrayList<>();
        mCallingUid = info.applicationInfo.uid;
        mCallingPackage = info.packageName;
        setIntent(_intent, info);
        setMinDimensions(info);

        isPersistable = true;
        // Clamp to [1, max].
        maxRecents = Math.min(Math.max(info.maxRecents, 1),
                ActivityManager.getMaxAppRecentsLimitStatic());

        taskType = APPLICATION_ACTIVITY_TYPE;
        mTaskToReturnTo = HOME_ACTIVITY_TYPE;
        lastTaskDescription = _taskDescription;
        touchActiveTime();
        mService.mTaskChangeNotificationController.notifyTaskCreated(_taskId, realActivity);
    }

    private TaskRecord(ActivityManagerService service, int _taskId, Intent _intent,
            Intent _affinityIntent, String _affinity, String _rootAffinity,
            ComponentName _realActivity, ComponentName _origActivity, boolean _rootWasReset,
            boolean _autoRemoveRecents, boolean _askedCompatMode, int _taskType, int _userId,
            int _effectiveUid, String _lastDescription, ArrayList<ActivityRecord> activities,
            long _firstActiveTime, long _lastActiveTime, long lastTimeMoved,
            boolean neverRelinquishIdentity, TaskDescription _lastTaskDescription,
            TaskThumbnailInfo lastThumbnailInfo, int taskAffiliation, int prevTaskId,
            int nextTaskId, int taskAffiliationColor, int callingUid, String callingPackage,
            int resizeMode, boolean supportsPictureInPicture, boolean privileged,
            boolean _realActivitySuspended, boolean userSetupComplete, int minWidth,
            int minHeight) {
        mService = service;
        mFilename = String.valueOf(_taskId) + TASK_THUMBNAIL_SUFFIX +
                TaskPersister.IMAGE_EXTENSION;
        mLastThumbnailFile = new File(TaskPersister.getUserImagesDir(_userId), mFilename);
        mLastThumbnailInfo = lastThumbnailInfo;
        taskId = _taskId;
        intent = _intent;
        affinityIntent = _affinityIntent;
        affinity = _affinity;
        rootAffinity = _rootAffinity;
        voiceSession = null;
        voiceInteractor = null;
        realActivity = _realActivity;
        realActivitySuspended = _realActivitySuspended;
        origActivity = _origActivity;
        rootWasReset = _rootWasReset;
        isAvailable = true;
        autoRemoveRecents = _autoRemoveRecents;
        askedCompatMode = _askedCompatMode;
        taskType = _taskType;
        mTaskToReturnTo = HOME_ACTIVITY_TYPE;
        userId = _userId;
        mUserSetupComplete = userSetupComplete;
        effectiveUid = _effectiveUid;
        firstActiveTime = _firstActiveTime;
        lastActiveTime = _lastActiveTime;
        lastDescription = _lastDescription;
        mActivities = activities;
        mLastTimeMoved = lastTimeMoved;
        mNeverRelinquishIdentity = neverRelinquishIdentity;
        lastTaskDescription = _lastTaskDescription;
        mAffiliatedTaskId = taskAffiliation;
        mAffiliatedTaskColor = taskAffiliationColor;
        mPrevAffiliateTaskId = prevTaskId;
        mNextAffiliateTaskId = nextTaskId;
        mCallingUid = callingUid;
        mCallingPackage = callingPackage;
        mResizeMode = resizeMode;
        mSupportsPictureInPicture = supportsPictureInPicture;
        mPrivileged = privileged;
        mMinWidth = minWidth;
        mMinHeight = minHeight;
        mService.mTaskChangeNotificationController.notifyTaskCreated(_taskId, realActivity);
    }

    TaskWindowContainerController getWindowContainerController() {
        return mWindowContainerController;
    }

    void createWindowContainer(boolean onTop, boolean showForAllUsers) {
        if (mWindowContainerController != null) {
            throw new IllegalArgumentException("Window container=" + mWindowContainerController
                    + " already created for task=" + this);
        }

        final Rect bounds = updateOverrideConfigurationFromLaunchBounds();
        final Configuration overrideConfig = getOverrideConfiguration();
        setWindowContainerController(new TaskWindowContainerController(taskId, this,
                getStack().getWindowContainerController(), userId, bounds, overrideConfig,
                mResizeMode, mSupportsPictureInPicture, isHomeTask(), onTop, showForAllUsers,
                lastTaskDescription));
    }

    /**
     * Should only be invoked from {@link #createWindowContainer(boolean, boolean)}.
     */
    @VisibleForTesting
    protected void setWindowContainerController(TaskWindowContainerController controller) {
        if (mWindowContainerController != null) {
            throw new IllegalArgumentException("Window container=" + mWindowContainerController
                    + " already created for task=" + this);
        }

        mWindowContainerController = controller;
    }

    void removeWindowContainer() {
        mService.mStackSupervisor.removeLockedTaskLocked(this);
        mWindowContainerController.removeContainer();
        if (!StackId.persistTaskBounds(getStackId())) {
            // Reset current bounds for task whose bounds shouldn't be persisted so it uses
            // default configuration the next time it launches.
            updateOverrideConfiguration(null);
        }
        mService.mTaskChangeNotificationController.notifyTaskRemoved(taskId);
        mWindowContainerController = null;
    }

    @Override
    public void onSnapshotChanged(TaskSnapshot snapshot) {
        mService.mTaskChangeNotificationController.notifyTaskSnapshotChanged(taskId, snapshot);
    }

    void setResizeMode(int resizeMode) {
        if (mResizeMode == resizeMode) {
            return;
        }
        mResizeMode = resizeMode;
        mWindowContainerController.setResizeable(resizeMode);
        mService.mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
        mService.mStackSupervisor.resumeFocusedStackTopActivityLocked();
    }

    void setTaskDockedResizing(boolean resizing) {
        mWindowContainerController.setTaskDockedResizing(resizing);
    }

    // TODO: Consolidate this with the resize() method below.
    @Override
    public void requestResize(Rect bounds, int resizeMode) {
        mService.resizeTask(taskId, bounds, resizeMode);
    }

    boolean resize(Rect bounds, int resizeMode, boolean preserveWindow, boolean deferResume) {
        if (!isResizeable()) {
            Slog.w(TAG, "resizeTask: task " + this + " not resizeable.");
            return true;
        }

        // If this is a forced resize, let it go through even if the bounds is not changing,
        // as we might need a relayout due to surface size change (to/from fullscreen).
        final boolean forced = (resizeMode & RESIZE_MODE_FORCED) != 0;
        if (Objects.equals(mBounds, bounds) && !forced) {
            // Nothing to do here...
            return true;
        }
        bounds = validateBounds(bounds);

        if (mWindowContainerController == null) {
            // Task doesn't exist in window manager yet (e.g. was restored from recents).
            // All we can do for now is update the bounds so it can be used when the task is
            // added to window manager.
            updateOverrideConfiguration(bounds);
            if (getStackId() != FREEFORM_WORKSPACE_STACK_ID) {
                // re-restore the task so it can have the proper stack association.
                mService.mStackSupervisor.restoreRecentTaskLocked(this,
                        FREEFORM_WORKSPACE_STACK_ID);
            }
            return true;
        }

        if (!canResizeToBounds(bounds)) {
            throw new IllegalArgumentException("resizeTask: Can not resize task=" + this
                    + " to bounds=" + bounds + " resizeMode=" + mResizeMode);
        }

        // Do not move the task to another stack here.
        // This method assumes that the task is already placed in the right stack.
        // we do not mess with that decision and we only do the resize!

        Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "am.resizeTask_" + taskId);

        final boolean updatedConfig = updateOverrideConfiguration(bounds);
        // This variable holds information whether the configuration didn't change in a significant
        // way and the activity was kept the way it was. If it's false, it means the activity had
        // to be relaunched due to configuration change.
        boolean kept = true;
        if (updatedConfig) {
            final ActivityRecord r = topRunningActivityLocked();
            if (r != null && !deferResume) {
                kept = r.ensureActivityConfigurationLocked(0 /* globalChanges */, preserveWindow);
                mService.mStackSupervisor.ensureActivitiesVisibleLocked(r, 0, !PRESERVE_WINDOWS);
                if (!kept) {
                    mService.mStackSupervisor.resumeFocusedStackTopActivityLocked();
                }
            }
        }
        mWindowContainerController.resize(mBounds, getOverrideConfiguration(), kept, forced);

        Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
        return kept;
    }

    // TODO: Investigate combining with the resize() method above.
    void resizeWindowContainer() {
        mWindowContainerController.resize(mBounds, getOverrideConfiguration(), false /* relayout */,
                false /* forced */);
    }

    void getWindowContainerBounds(Rect bounds) {
        if (mWindowContainerController != null) {
            mWindowContainerController.getBounds(bounds);
            return;
        }
        Slog.w(TAG, "getWindowContainerBounds: task " + this + " has null window container.");
        bounds.setEmpty();
    }

    /**
     * Convenience method to reparent a task to the top or bottom position of the stack.
     */
    boolean reparent(int preferredStackId, boolean toTop, @ReparentMoveStackMode int moveStackMode,
            boolean animate, boolean deferResume, String reason) {
        return reparent(preferredStackId, toTop ? MAX_VALUE : 0, moveStackMode, animate,
                deferResume, true /* schedulePictureInPictureModeChange */, reason);
    }

    /**
     * Convenience method to reparent a task to the top or bottom position of the stack, with
     * an option to skip scheduling the picture-in-picture mode change.
     */
    boolean reparent(int preferredStackId, boolean toTop, @ReparentMoveStackMode int moveStackMode,
            boolean animate, boolean deferResume, boolean schedulePictureInPictureModeChange,
            String reason) {
        return reparent(preferredStackId, toTop ? MAX_VALUE : 0, moveStackMode, animate,
                deferResume, schedulePictureInPictureModeChange, reason);
    }

    /**
     * Convenience method to reparent a task to a specific position of the stack.
     */
    boolean reparent(int preferredStackId, int position, @ReparentMoveStackMode int moveStackMode,
            boolean animate, boolean deferResume, String reason) {
        return reparent(preferredStackId, position, moveStackMode, animate, deferResume,
                true /* schedulePictureInPictureModeChange */, reason);
    }

    /**
     * Reparents the task into a preferred stack, creating it if necessary.
     *
     * @param preferredStackId the stack id of the target stack to move this task
     * @param position the position to place this task in the new stack
     * @param animate whether or not we should wait for the new window created as a part of the
     *            reparenting to be drawn and animated in
     * @param moveStackMode whether or not to move the stack to the front always, only if it was
     *            previously focused & in front, or never
     * @param deferResume whether or not to update the visibility of other tasks and stacks that may
     *            have changed as a result of this reparenting
     * @param schedulePictureInPictureModeChange specifies whether or not to schedule the PiP mode
     *            change. Callers may set this to false if they are explicitly scheduling PiP mode
     *            changes themselves, like during the PiP animation
     * @param reason the caller of this reparenting
     * @return whether the task was reparented
     */
    boolean reparent(int preferredStackId, int position, @ReparentMoveStackMode int moveStackMode,
            boolean animate, boolean deferResume, boolean schedulePictureInPictureModeChange,
            String reason) {
        final ActivityStackSupervisor supervisor = mService.mStackSupervisor;
        final WindowManagerService windowManager = mService.mWindowManager;
        final ActivityStack sourceStack = getStack();
        final ActivityStack toStack = supervisor.getReparentTargetStack(this, preferredStackId,
                position == MAX_VALUE);
        if (toStack == sourceStack) {
            return false;
        }

        final int sourceStackId = getStackId();
        final int stackId = toStack.getStackId();
        final ActivityRecord topActivity = getTopActivity();

        final boolean mightReplaceWindow = StackId.replaceWindowsOnTaskMove(sourceStackId, stackId)
                && topActivity != null;
        if (mightReplaceWindow) {
            // We are about to relaunch the activity because its configuration changed due to
            // being maximized, i.e. size change. The activity will first remove the old window
            // and then add a new one. This call will tell window manager about this, so it can
            // preserve the old window until the new one is drawn. This prevents having a gap
            // between the removal and addition, in which no window is visible. We also want the
            // entrance of the new window to be properly animated.
            // Note here we always set the replacing window first, as the flags might be needed
            // during the relaunch. If we end up not doing any relaunch, we clear the flags later.
            windowManager.setWillReplaceWindow(topActivity.appToken, animate);
        }

        windowManager.deferSurfaceLayout();
        boolean kept = true;
        try {
            final ActivityRecord r = topRunningActivityLocked();
            final boolean wasFocused = r != null && supervisor.isFocusedStack(sourceStack)
                    && (topRunningActivityLocked() == r);
            final boolean wasResumed = r != null && sourceStack.mResumedActivity == r;
            final boolean wasPaused = r != null && sourceStack.mPausingActivity == r;

            // In some cases the focused stack isn't the front stack. E.g. pinned stack.
            // Whenever we are moving the top activity from the front stack we want to make sure to
            // move the stack to the front.
            final boolean wasFront = r != null && supervisor.isFrontStackOnDisplay(sourceStack)
                    && (sourceStack.topRunningActivityLocked() == r);

            // Adjust the position for the new parent stack as needed.
            position = toStack.getAdjustedPositionForTask(this, position, null /* starting */);

            // Must reparent first in window manager to avoid a situation where AM can delete the
            // we are coming from in WM before we reparent because it became empty.
            mWindowContainerController.reparent(toStack.getWindowContainerController(), position,
                    moveStackMode == REPARENT_MOVE_STACK_TO_FRONT);

            final boolean moveStackToFront = moveStackMode == REPARENT_MOVE_STACK_TO_FRONT
                    || (moveStackMode == REPARENT_KEEP_STACK_AT_FRONT && (wasFocused || wasFront));
            // Move the task
            sourceStack.removeTask(this, reason, moveStackToFront
                    ? REMOVE_TASK_MODE_MOVING_TO_TOP : REMOVE_TASK_MODE_MOVING);
            toStack.addTask(this, position, false /* schedulePictureInPictureModeChange */, reason);

            if (schedulePictureInPictureModeChange) {
                // Notify of picture-in-picture mode changes
                supervisor.scheduleUpdatePictureInPictureModeIfNeeded(this, sourceStack);
            }

            // TODO: Ensure that this is actually necessary here
            // Notify the voice session if required
            if (voiceSession != null) {
                try {
                    voiceSession.taskStarted(intent, taskId);
                } catch (RemoteException e) {
                }
            }

            // If the task had focus before (or we're requested to move focus), move focus to the
            // new stack by moving the stack to the front.
            if (r != null) {
                toStack.moveToFrontAndResumeStateIfNeeded(r, moveStackToFront, wasResumed,
                        wasPaused, reason);
            }
            if (!animate) {
                toStack.mNoAnimActivities.add(topActivity);
            }

            // We might trigger a configuration change. Save the current task bounds for freezing.
            // TODO: Should this call be moved inside the resize method in WM?
            toStack.prepareFreezingTaskBounds();

            // Make sure the task has the appropriate bounds/size for the stack it is in.
            if (stackId == FULLSCREEN_WORKSPACE_STACK_ID
                    && !Objects.equals(mBounds, toStack.mBounds)) {
                kept = resize(toStack.mBounds, RESIZE_MODE_SYSTEM, !mightReplaceWindow,
                        deferResume);
            } else if (stackId == FREEFORM_WORKSPACE_STACK_ID) {
                Rect bounds = getLaunchBounds();
                if (bounds == null) {
                    toStack.layoutTaskInStack(this, null);
                    bounds = mBounds;
                }
                kept = resize(bounds, RESIZE_MODE_FORCED, !mightReplaceWindow, deferResume);
            } else if (stackId == DOCKED_STACK_ID || stackId == PINNED_STACK_ID) {
                if (stackId == DOCKED_STACK_ID && moveStackMode == REPARENT_KEEP_STACK_AT_FRONT) {
                    // Move recents to front so it is not behind home stack when going into docked
                    // mode
                    mService.mStackSupervisor.moveRecentsStackToFront(reason);
                }
                kept = resize(toStack.mBounds, RESIZE_MODE_SYSTEM, !mightReplaceWindow,
                        deferResume);
            }
        } finally {
            windowManager.continueSurfaceLayout();
        }

        if (mightReplaceWindow) {
            // If we didn't actual do a relaunch (indicated by kept==true meaning we kept the old
            // window), we need to clear the replace window settings. Otherwise, we schedule a
            // timeout to remove the old window if the replacing window is not coming in time.
            windowManager.scheduleClearWillReplaceWindows(topActivity.appToken, !kept);
        }

        if (!deferResume) {
            // The task might have already been running and its visibility needs to be synchronized
            // with the visibility of the stack / windows.
            supervisor.ensureActivitiesVisibleLocked(null, 0, !mightReplaceWindow);
            supervisor.resumeFocusedStackTopActivityLocked();
        }

        // TODO: Handle incorrect request to move before the actual move, not after.
        supervisor.handleNonResizableTaskIfNeeded(this, preferredStackId, DEFAULT_DISPLAY, stackId);

        boolean successful = (preferredStackId == stackId);
        if (successful && stackId == DOCKED_STACK_ID) {
            // If task moved to docked stack - show recents if needed.
            mService.mWindowManager.showRecentApps(false /* fromHome */);
        }
        return successful;
    }

    void cancelWindowTransition() {
        mWindowContainerController.cancelWindowTransition();
    }

    void cancelThumbnailTransition() {
        mWindowContainerController.cancelThumbnailTransition();
    }

    /**
     * DO NOT HOLD THE ACTIVITY MANAGER LOCK WHEN CALLING THIS METHOD!
     */
    TaskSnapshot getSnapshot(boolean reducedResolution) {

        // TODO: Move this to {@link TaskWindowContainerController} once recent tasks are more
        // synchronized between AM and WM.
        return mService.mWindowManager.getTaskSnapshot(taskId, userId, reducedResolution);
    }

    void touchActiveTime() {
        lastActiveTime = System.currentTimeMillis();
        if (firstActiveTime == 0) {
            firstActiveTime = lastActiveTime;
        }
    }

    long getInactiveDuration() {
        return System.currentTimeMillis() - lastActiveTime;
    }

    /** Sets the original intent, and the calling uid and package. */
    void setIntent(ActivityRecord r) {
        mCallingUid = r.launchedFromUid;
        mCallingPackage = r.launchedFromPackage;
        setIntent(r.intent, r.info);
    }

    /** Sets the original intent, _without_ updating the calling uid or package. */
    private void setIntent(Intent _intent, ActivityInfo info) {
        if (intent == null) {
            mNeverRelinquishIdentity =
                    (info.flags & FLAG_RELINQUISH_TASK_IDENTITY) == 0;
        } else if (mNeverRelinquishIdentity) {
            return;
        }

        affinity = info.taskAffinity;
        if (intent == null) {
            // If this task already has an intent associated with it, don't set the root
            // affinity -- we don't want it changing after initially set, but the initially
            // set value may be null.
            rootAffinity = affinity;
        }
        effectiveUid = info.applicationInfo.uid;
        stringName = null;

        if (info.targetActivity == null) {
            if (_intent != null) {
                // If this Intent has a selector, we want to clear it for the
                // recent task since it is not relevant if the user later wants
                // to re-launch the app.
                if (_intent.getSelector() != null || _intent.getSourceBounds() != null) {
                    _intent = new Intent(_intent);
                    _intent.setSelector(null);
                    _intent.setSourceBounds(null);
                }
            }
            if (DEBUG_TASKS) Slog.v(TAG_TASKS, "Setting Intent of " + this + " to " + _intent);
            intent = _intent;
            realActivity = _intent != null ? _intent.getComponent() : null;
            origActivity = null;
        } else {
            ComponentName targetComponent = new ComponentName(
                    info.packageName, info.targetActivity);
            if (_intent != null) {
                Intent targetIntent = new Intent(_intent);
                targetIntent.setComponent(targetComponent);
                targetIntent.setSelector(null);
                targetIntent.setSourceBounds(null);
                if (DEBUG_TASKS) Slog.v(TAG_TASKS,
                        "Setting Intent of " + this + " to target " + targetIntent);
                intent = targetIntent;
                realActivity = targetComponent;
                origActivity = _intent.getComponent();
            } else {
                intent = null;
                realActivity = targetComponent;
                origActivity = new ComponentName(info.packageName, info.name);
            }
        }

        final int intentFlags = intent == null ? 0 : intent.getFlags();
        if ((intentFlags & Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0) {
            // Once we are set to an Intent with this flag, we count this
            // task as having a true root activity.
            rootWasReset = true;
        }
        userId = UserHandle.getUserId(info.applicationInfo.uid);
        mUserSetupComplete = Settings.Secure.getIntForUser(mService.mContext.getContentResolver(),
                USER_SETUP_COMPLETE, 0, userId) != 0;
        if ((info.flags & ActivityInfo.FLAG_AUTO_REMOVE_FROM_RECENTS) != 0) {
            // If the activity itself has requested auto-remove, then just always do it.
            autoRemoveRecents = true;
        } else if ((intentFlags & (FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_RETAIN_IN_RECENTS))
                == FLAG_ACTIVITY_NEW_DOCUMENT) {
            // If the caller has not asked for the document to be retained, then we may
            // want to turn on auto-remove, depending on whether the target has set its
            // own document launch mode.
            if (info.documentLaunchMode != ActivityInfo.DOCUMENT_LAUNCH_NONE) {
                autoRemoveRecents = false;
            } else {
                autoRemoveRecents = true;
            }
        } else {
            autoRemoveRecents = false;
        }
        mResizeMode = info.resizeMode;
        mSupportsPictureInPicture = info.supportsPictureInPicture();
        mLockTaskMode = info.lockTaskLaunchMode;
        mPrivileged = (info.applicationInfo.privateFlags & PRIVATE_FLAG_PRIVILEGED) != 0;
        setLockTaskAuth();
    }

    /** Sets the original minimal width and height. */
    private void setMinDimensions(ActivityInfo info) {
        if (info != null && info.windowLayout != null) {
            mMinWidth = info.windowLayout.minWidth;
            mMinHeight = info.windowLayout.minHeight;
        } else {
            mMinWidth = INVALID_MIN_SIZE;
            mMinHeight = INVALID_MIN_SIZE;
        }
    }

    /**
     * Return true if the input activity has the same intent filter as the intent this task
     * record is based on (normally the root activity intent).
     */
    boolean isSameIntentFilter(ActivityRecord r) {
        final Intent intent = new Intent(r.intent);
        // Correct the activity intent for aliasing. The task record intent will always be based on
        // the real activity that will be launched not the alias, so we need to use an intent with
        // the component name pointing to the real activity not the alias in the activity record.
        intent.setComponent(r.realActivity);
        return this.intent.filterEquals(intent);
    }

    void setTaskToReturnTo(int taskToReturnTo) {
        mTaskToReturnTo = (taskToReturnTo == RECENTS_ACTIVITY_TYPE)
                ? HOME_ACTIVITY_TYPE : taskToReturnTo;
    }

    void setTaskToReturnTo(ActivityRecord source) {
        if (source.isRecentsActivity()) {
            setTaskToReturnTo(RECENTS_ACTIVITY_TYPE);
        } else if (source.isAssistantActivity()) {
            setTaskToReturnTo(ASSISTANT_ACTIVITY_TYPE);
        }
    }

    int getTaskToReturnTo() {
        return mTaskToReturnTo;
    }

    void setPrevAffiliate(TaskRecord prevAffiliate) {
        mPrevAffiliate = prevAffiliate;
        mPrevAffiliateTaskId = prevAffiliate == null ? INVALID_TASK_ID : prevAffiliate.taskId;
    }

    void setNextAffiliate(TaskRecord nextAffiliate) {
        mNextAffiliate = nextAffiliate;
        mNextAffiliateTaskId = nextAffiliate == null ? INVALID_TASK_ID : nextAffiliate.taskId;
    }

    ActivityStack getStack() {
        return mStack;
    }

    /**
     * Must be used for setting parent stack because it performs configuration updates.
     * Must be called after adding task as a child to the stack.
     */
    void setStack(ActivityStack stack) {
        if (stack != null && !stack.isInStackLocked(this)) {
            throw new IllegalStateException("Task must be added as a Stack child first.");
        }
        mStack = stack;
        onParentChanged();
    }

    /**
     * @return Id of current stack, {@link INVALID_STACK_ID} if no stack is set.
     */
    int getStackId() {
        return mStack != null ? mStack.mStackId : INVALID_STACK_ID;
    }

    @Override
    protected int getChildCount() {
        return mActivities.size();
    }

    @Override
    protected ConfigurationContainer getChildAt(int index) {
        return mActivities.get(index);
    }

    @Override
    protected ConfigurationContainer getParent() {
        return mStack;
    }

    @Override
    void onParentChanged() {
        super.onParentChanged();
        mService.mStackSupervisor.updateUIDsPresentOnDisplay();
    }

    // Close up recents linked list.
    private void closeRecentsChain() {
        if (mPrevAffiliate != null) {
            mPrevAffiliate.setNextAffiliate(mNextAffiliate);
        }
        if (mNextAffiliate != null) {
            mNextAffiliate.setPrevAffiliate(mPrevAffiliate);
        }
        setPrevAffiliate(null);
        setNextAffiliate(null);
    }

    void removedFromRecents() {
        disposeThumbnail();
        closeRecentsChain();
        if (inRecents) {
            inRecents = false;
            mService.notifyTaskPersisterLocked(this, false);
        }

        // TODO: Use window container controller once tasks are better synced between AM and WM
        mService.mWindowManager.notifyTaskRemovedFromRecents(taskId, userId);
    }

    void setTaskToAffiliateWith(TaskRecord taskToAffiliateWith) {
        closeRecentsChain();
        mAffiliatedTaskId = taskToAffiliateWith.mAffiliatedTaskId;
        mAffiliatedTaskColor = taskToAffiliateWith.mAffiliatedTaskColor;
        // Find the end
        while (taskToAffiliateWith.mNextAffiliate != null) {
            final TaskRecord nextRecents = taskToAffiliateWith.mNextAffiliate;
            if (nextRecents.mAffiliatedTaskId != mAffiliatedTaskId) {
                Slog.e(TAG, "setTaskToAffiliateWith: nextRecents=" + nextRecents + " affilTaskId="
                        + nextRecents.mAffiliatedTaskId + " should be " + mAffiliatedTaskId);
                if (nextRecents.mPrevAffiliate == taskToAffiliateWith) {
                    nextRecents.setPrevAffiliate(null);
                }
                taskToAffiliateWith.setNextAffiliate(null);
                break;
            }
            taskToAffiliateWith = nextRecents;
        }
        taskToAffiliateWith.setNextAffiliate(this);
        setPrevAffiliate(taskToAffiliateWith);
        setNextAffiliate(null);
    }

    /**
     * Sets the last thumbnail with the current task bounds and the system orientation.
     * @return whether the thumbnail was set
     */
    boolean setLastThumbnailLocked(Bitmap thumbnail) {
        int taskWidth = 0;
        int taskHeight = 0;
        if (mBounds != null) {
            // Non-fullscreen tasks
            taskWidth = mBounds.width();
            taskHeight = mBounds.height();
        } else if (mStack != null) {
            // Fullscreen tasks
            final Point displaySize = new Point();
            mStack.getDisplaySize(displaySize);
            taskWidth = displaySize.x;
            taskHeight = displaySize.y;
        } else {
            Slog.e(TAG, "setLastThumbnailLocked() called on Task without stack");
        }
        // We need to provide the current orientation of the display on which this task resides,
        // not the orientation of the task.
        final int orientation = getStack().getDisplay().getConfiguration().orientation;
        return setLastThumbnailLocked(thumbnail, taskWidth, taskHeight, orientation);
    }

    /**
     * Sets the last thumbnail with the current task bounds.
     * @return whether the thumbnail was set
     */
    private boolean setLastThumbnailLocked(Bitmap thumbnail, int taskWidth, int taskHeight,
            int screenOrientation) {
        if (mLastThumbnail != thumbnail) {
            mLastThumbnail = thumbnail;
            mLastThumbnailInfo.taskWidth = taskWidth;
            mLastThumbnailInfo.taskHeight = taskHeight;
            mLastThumbnailInfo.screenOrientation = screenOrientation;
            if (thumbnail == null) {
                if (mLastThumbnailFile != null) {
                    mLastThumbnailFile.delete();
                }
            } else {
                mService.mRecentTasks.saveImage(thumbnail, mLastThumbnailFile.getAbsolutePath());
            }
            return true;
        }
        return false;
    }

    void getLastThumbnail(TaskThumbnail thumbs) {
        thumbs.mainThumbnail = mLastThumbnail;
        thumbs.thumbnailInfo = mLastThumbnailInfo;
        thumbs.thumbnailFileDescriptor = null;
        if (mLastThumbnail == null) {
            thumbs.mainThumbnail = mService.mRecentTasks.getImageFromWriteQueue(
                    mLastThumbnailFile.getAbsolutePath());
        }
        // Only load the thumbnail file if we don't have a thumbnail
        if (thumbs.mainThumbnail == null && mLastThumbnailFile.exists()) {
            try {
                thumbs.thumbnailFileDescriptor = ParcelFileDescriptor.open(mLastThumbnailFile,
                        ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (IOException e) {
            }
        }
    }

    /**
     * Removes in-memory thumbnail data when the max number of in-memory task thumbnails is reached.
     */
    void freeLastThumbnail() {
        mLastThumbnail = null;
    }

    /**
     * Removes all associated thumbnail data when a task is removed or pruned from recents.
     */
    void disposeThumbnail() {
        mLastThumbnailInfo.reset();
        mLastThumbnail = null;
        lastDescription = null;
    }

    /** Returns the intent for the root activity for this task */
    Intent getBaseIntent() {
        return intent != null ? intent : affinityIntent;
    }

    /** Returns the first non-finishing activity from the root. */
    ActivityRecord getRootActivity() {
        for (int i = 0; i < mActivities.size(); i++) {
            final ActivityRecord r = mActivities.get(i);
            if (r.finishing) {
                continue;
            }
            return r;
        }
        return null;
    }

    ActivityRecord getTopActivity() {
        return getTopActivity(true /* includeOverlays */);
    }

    ActivityRecord getTopActivity(boolean includeOverlays) {
        for (int i = mActivities.size() - 1; i >= 0; --i) {
            final ActivityRecord r = mActivities.get(i);
            if (r.finishing || (!includeOverlays && r.mTaskOverlay)) {
                continue;
            }
            return r;
        }
        return null;
    }

    ActivityRecord topRunningActivityLocked() {
        if (mStack != null) {
            for (int activityNdx = mActivities.size() - 1; activityNdx >= 0; --activityNdx) {
                ActivityRecord r = mActivities.get(activityNdx);
                if (!r.finishing && r.okToShowLocked()) {
                    return r;
                }
            }
        }
        return null;
    }

    boolean isVisible() {
        for (int i = mActivities.size() - 1; i >= 0; --i) {
            final ActivityRecord r = mActivities.get(i);
            if (r.visible) {
                return true;
            }
        }
        return false;
    }

    void getAllRunningVisibleActivitiesLocked(ArrayList<ActivityRecord> outActivities) {
        if (mStack != null) {
            for (int activityNdx = mActivities.size() - 1; activityNdx >= 0; --activityNdx) {
                ActivityRecord r = mActivities.get(activityNdx);
                if (!r.finishing && r.okToShowLocked() && r.visibleIgnoringKeyguard) {
                    outActivities.add(r);
                }
            }
        }
    }

    ActivityRecord topRunningActivityWithStartingWindowLocked() {
        if (mStack != null) {
            for (int activityNdx = mActivities.size() - 1; activityNdx >= 0; --activityNdx) {
                ActivityRecord r = mActivities.get(activityNdx);
                if (r.mStartingWindowState != STARTING_WINDOW_SHOWN
                        || r.finishing || !r.okToShowLocked()) {
                    continue;
                }
                return r;
            }
        }
        return null;
    }

    boolean okToShowLocked() {
        // NOTE: If {@link TaskRecord#topRunningActivityLocked} return is not null then it is
        // okay to show the activity when locked.
        return mService.mStackSupervisor.isCurrentProfileLocked(userId)
                || topRunningActivityLocked() != null;
    }

    /** Call after activity movement or finish to make sure that frontOfTask is set correctly */
    final void setFrontOfTask() {
        boolean foundFront = false;
        final int numActivities = mActivities.size();
        for (int activityNdx = 0; activityNdx < numActivities; ++activityNdx) {
            final ActivityRecord r = mActivities.get(activityNdx);
            if (foundFront || r.finishing) {
                r.frontOfTask = false;
            } else {
                r.frontOfTask = true;
                // Set frontOfTask false for every following activity.
                foundFront = true;
            }
        }
        if (!foundFront && numActivities > 0) {
            // All activities of this task are finishing. As we ought to have a frontOfTask
            // activity, make the bottom activity front.
            mActivities.get(0).frontOfTask = true;
        }
    }

    /**
     * Reorder the history stack so that the passed activity is brought to the front.
     */
    final void moveActivityToFrontLocked(ActivityRecord newTop) {
        if (DEBUG_ADD_REMOVE) Slog.i(TAG_ADD_REMOVE,
                "Removing and adding activity " + newTop
                + " to stack at top callers=" + Debug.getCallers(4));

        mActivities.remove(newTop);
        mActivities.add(newTop);
        updateEffectiveIntent();

        setFrontOfTask();
    }

    void addActivityAtBottom(ActivityRecord r) {
        addActivityAtIndex(0, r);
    }

    void addActivityToTop(ActivityRecord r) {
        addActivityAtIndex(mActivities.size(), r);
    }

    /**
     * Adds an activity {@param r} at the given {@param index}. The activity {@param r} must either
     * be in the current task or unparented to any task.
     */
    void addActivityAtIndex(int index, ActivityRecord r) {
        TaskRecord task = r.getTask();
        if (task != null && task != this) {
            throw new IllegalArgumentException("Can not add r=" + " to task=" + this
                    + " current parent=" + task);
        }

        r.setTask(this);

        // Remove r first, and if it wasn't already in the list and it's fullscreen, count it.
        if (!mActivities.remove(r) && r.fullscreen) {
            // Was not previously in list.
            numFullscreen++;
        }
        // Only set this based on the first activity
        if (mActivities.isEmpty()) {
            taskType = r.mActivityType;
            isPersistable = r.isPersistable();
            mCallingUid = r.launchedFromUid;
            mCallingPackage = r.launchedFromPackage;
            // Clamp to [1, max].
            maxRecents = Math.min(Math.max(r.info.maxRecents, 1),
                    ActivityManager.getMaxAppRecentsLimitStatic());
        } else {
            // Otherwise make all added activities match this one.
            r.mActivityType = taskType;
        }

        final int size = mActivities.size();

        if (index == size && size > 0) {
            final ActivityRecord top = mActivities.get(size - 1);
            if (top.mTaskOverlay) {
                // Place below the task overlay activity since the overlay activity should always
                // be on top.
                index--;
            }
        }

        index = Math.min(size, index);
        mActivities.add(index, r);
        updateEffectiveIntent();
        if (r.isPersistable()) {
            mService.notifyTaskPersisterLocked(this, false);
        }

        // Sync. with window manager
        updateOverrideConfigurationFromLaunchBounds();
        final AppWindowContainerController appController = r.getWindowContainerController();
        if (appController != null) {
            // Only attempt to move in WM if the child has a controller. It is possible we haven't
            // created controller for the activity we are starting yet.
            mWindowContainerController.positionChildAt(appController, index);
        }

        // Make sure the list of display UID whitelists is updated
        // now that this record is in a new task.
        mService.mStackSupervisor.updateUIDsPresentOnDisplay();
    }

    /**
     * Removes the specified activity from this task.
     * @param r The {@link ActivityRecord} to remove.
     * @return true if this was the last activity in the task.
     */
    boolean removeActivity(ActivityRecord r) {
        return removeActivity(r, false /*reparenting*/);
    }

    boolean removeActivity(ActivityRecord r, boolean reparenting) {
        if (r.getTask() != this) {
            throw new IllegalArgumentException(
                    "Activity=" + r + " does not belong to task=" + this);
        }

        r.setTask(null /*task*/, reparenting);

        if (mActivities.remove(r) && r.fullscreen) {
            // Was previously in list.
            numFullscreen--;
        }
        if (r.isPersistable()) {
            mService.notifyTaskPersisterLocked(this, false);
        }

        if (getStackId() == PINNED_STACK_ID) {
            // We normally notify listeners of task stack changes on pause, however pinned stack
            // activities are normally in the paused state so no notification will be sent there
            // before the activity is removed. We send it here so instead.
            mService.mTaskChangeNotificationController.notifyTaskStackChanged();
        }

        if (mActivities.isEmpty()) {
            return !mReuseTask;
        }
        updateEffectiveIntent();
        return false;
    }

    /**
     * @return whether or not there are ONLY task overlay activities in the stack.
     *         If {@param excludeFinishing} is set, then ignore finishing activities in the check.
     *         If there are no task overlay activities, this call returns false.
     */
    boolean onlyHasTaskOverlayActivities(boolean excludeFinishing) {
        int count = 0;
        for (int i = mActivities.size() - 1; i >= 0; i--) {
            final ActivityRecord r = mActivities.get(i);
            if (excludeFinishing && r.finishing) {
                continue;
            }
            if (!r.mTaskOverlay) {
                return false;
            }
            count++;
        }
        return count > 0;
    }

    boolean autoRemoveFromRecents() {
        // We will automatically remove the task either if it has explicitly asked for
        // this, or it is empty and has never contained an activity that got shown to
        // the user.
        return autoRemoveRecents || (mActivities.isEmpty() && !hasBeenVisible);
    }

    /**
     * Completely remove all activities associated with an existing
     * task starting at a specified index.
     */
    final void performClearTaskAtIndexLocked(int activityNdx, boolean pauseImmediately) {
        int numActivities = mActivities.size();
        for ( ; activityNdx < numActivities; ++activityNdx) {
            final ActivityRecord r = mActivities.get(activityNdx);
            if (r.finishing) {
                continue;
            }
            if (mStack == null) {
                // Task was restored from persistent storage.
                r.takeFromHistory();
                mActivities.remove(activityNdx);
                --activityNdx;
                --numActivities;
            } else if (mStack.finishActivityLocked(r, Activity.RESULT_CANCELED, null,
                    "clear-task-index", false, pauseImmediately)) {
                --activityNdx;
                --numActivities;
            }
        }
    }

    /**
     * Completely remove all activities associated with an existing task.
     */
    final void performClearTaskLocked() {
        mReuseTask = true;
        performClearTaskAtIndexLocked(0, !PAUSE_IMMEDIATELY);
        mReuseTask = false;
    }

    ActivityRecord performClearTaskForReuseLocked(ActivityRecord newR, int launchFlags) {
        mReuseTask = true;
        final ActivityRecord result = performClearTaskLocked(newR, launchFlags);
        mReuseTask = false;
        return result;
    }

    /**
     * Perform clear operation as requested by
     * {@link Intent#FLAG_ACTIVITY_CLEAR_TOP}: search from the top of the
     * stack to the given task, then look for
     * an instance of that activity in the stack and, if found, finish all
     * activities on top of it and return the instance.
     *
     * @param newR Description of the new activity being started.
     * @return Returns the old activity that should be continued to be used,
     * or null if none was found.
     */
    final ActivityRecord performClearTaskLocked(ActivityRecord newR, int launchFlags) {
        int numActivities = mActivities.size();
        for (int activityNdx = numActivities - 1; activityNdx >= 0; --activityNdx) {
            ActivityRecord r = mActivities.get(activityNdx);
            if (r.finishing) {
                continue;
            }
            if (r.realActivity.equals(newR.realActivity)) {
                // Here it is!  Now finish everything in front...
                final ActivityRecord ret = r;

                for (++activityNdx; activityNdx < numActivities; ++activityNdx) {
                    r = mActivities.get(activityNdx);
                    if (r.finishing) {
                        continue;
                    }
                    ActivityOptions opts = r.takeOptionsLocked();
                    if (opts != null) {
                        ret.updateOptionsLocked(opts);
                    }
                    if (mStack != null && mStack.finishActivityLocked(
                            r, Activity.RESULT_CANCELED, null, "clear-task-stack", false)) {
                        --activityNdx;
                        --numActivities;
                    }
                }

                // Finally, if this is a normal launch mode (that is, not
                // expecting onNewIntent()), then we will finish the current
                // instance of the activity so a new fresh one can be started.
                if (ret.launchMode == ActivityInfo.LAUNCH_MULTIPLE
                        && (launchFlags & Intent.FLAG_ACTIVITY_SINGLE_TOP) == 0
                        && !ActivityStarter.isDocumentLaunchesIntoExisting(launchFlags)) {
                    if (!ret.finishing) {
                        if (mStack != null) {
                            mStack.finishActivityLocked(
                                    ret, Activity.RESULT_CANCELED, null, "clear-task-top", false);
                        }
                        return null;
                    }
                }

                return ret;
            }
        }

        return null;
    }

    TaskThumbnail getTaskThumbnailLocked() {
        if (mStack != null) {
            final ActivityRecord resumedActivity = mStack.mResumedActivity;
            if (resumedActivity != null && resumedActivity.getTask() == this) {
                final Bitmap thumbnail = resumedActivity.screenshotActivityLocked();
                setLastThumbnailLocked(thumbnail);
            }
        }
        final TaskThumbnail taskThumbnail = new TaskThumbnail();
        getLastThumbnail(taskThumbnail);
        return taskThumbnail;
    }

    void removeTaskActivitiesLocked(boolean pauseImmediately) {
        // Just remove the entire task.
        performClearTaskAtIndexLocked(0, pauseImmediately);
    }

    String lockTaskAuthToString() {
        switch (mLockTaskAuth) {
            case LOCK_TASK_AUTH_DONT_LOCK: return "LOCK_TASK_AUTH_DONT_LOCK";
            case LOCK_TASK_AUTH_PINNABLE: return "LOCK_TASK_AUTH_PINNABLE";
            case LOCK_TASK_AUTH_LAUNCHABLE: return "LOCK_TASK_AUTH_LAUNCHABLE";
            case LOCK_TASK_AUTH_WHITELISTED: return "LOCK_TASK_AUTH_WHITELISTED";
            case LOCK_TASK_AUTH_LAUNCHABLE_PRIV: return "LOCK_TASK_AUTH_LAUNCHABLE_PRIV";
            default: return "unknown=" + mLockTaskAuth;
        }
    }

    void setLockTaskAuth() {
        if (!mPrivileged &&
                (mLockTaskMode == LOCK_TASK_LAUNCH_MODE_ALWAYS ||
                        mLockTaskMode == LOCK_TASK_LAUNCH_MODE_NEVER)) {
            // Non-priv apps are not allowed to use always or never, fall back to default
            mLockTaskMode = LOCK_TASK_LAUNCH_MODE_DEFAULT;
        }
        switch (mLockTaskMode) {
            case LOCK_TASK_LAUNCH_MODE_DEFAULT:
                mLockTaskAuth = isLockTaskWhitelistedLocked() ?
                    LOCK_TASK_AUTH_WHITELISTED : LOCK_TASK_AUTH_PINNABLE;
                break;

            case LOCK_TASK_LAUNCH_MODE_NEVER:
                mLockTaskAuth = LOCK_TASK_AUTH_DONT_LOCK;
                break;

            case LOCK_TASK_LAUNCH_MODE_ALWAYS:
                mLockTaskAuth = LOCK_TASK_AUTH_LAUNCHABLE_PRIV;
                break;

            case LOCK_TASK_LAUNCH_MODE_IF_WHITELISTED:
                mLockTaskAuth = isLockTaskWhitelistedLocked() ?
                        LOCK_TASK_AUTH_LAUNCHABLE : LOCK_TASK_AUTH_PINNABLE;
                break;
        }
        if (DEBUG_LOCKTASK) Slog.d(TAG_LOCKTASK, "setLockTaskAuth: task=" + this +
                " mLockTaskAuth=" + lockTaskAuthToString());
    }

    boolean isLockTaskWhitelistedLocked() {
        String pkg = (realActivity != null) ? realActivity.getPackageName() : null;
        if (pkg == null) {
            return false;
        }
        String[] packages = mService.mLockTaskPackages.get(userId);
        if (packages == null) {
            return false;
        }
        for (int i = packages.length - 1; i >= 0; --i) {
            if (pkg.equals(packages[i])) {
                return true;
            }
        }
        return false;
    }

    boolean isHomeTask() {
        return taskType == HOME_ACTIVITY_TYPE;
    }

    boolean isRecentsTask() {
        return taskType == RECENTS_ACTIVITY_TYPE;
    }

    boolean isAssistantTask() {
        return taskType == ASSISTANT_ACTIVITY_TYPE;
    }

    boolean isApplicationTask() {
        return taskType == APPLICATION_ACTIVITY_TYPE;
    }

    boolean isOverHomeStack() {
        return mTaskToReturnTo == HOME_ACTIVITY_TYPE;
    }

    boolean isOverAssistantStack() {
        return mTaskToReturnTo == ASSISTANT_ACTIVITY_TYPE;
    }

    private boolean isResizeable(boolean checkSupportsPip) {
        return (mService.mForceResizableActivities || ActivityInfo.isResizeableMode(mResizeMode)
                || (checkSupportsPip && mSupportsPictureInPicture)) && !mTemporarilyUnresizable;
    }

    boolean isResizeable() {
        return isResizeable(true /* checkSupportsPip */);
    }

    boolean supportsSplitScreen() {
        // A task can not be docked even if it is considered resizeable because it only supports
        // picture-in-picture mode but has a non-resizeable resizeMode
        return mService.mSupportsSplitScreenMultiWindow
                && (mService.mForceResizableActivities
                        || (isResizeable(false /* checkSupportsPip */)
                                && !ActivityInfo.isPreserveOrientationMode(mResizeMode)));
    }

    /**
     * Check whether this task can be launched on the specified display.
     * @param displayId Target display id.
     * @return {@code true} if either it is the default display or this activity is resizeable and
     *         can be put a secondary screen.
     */
    boolean canBeLaunchedOnDisplay(int displayId) {
        return mService.mStackSupervisor.canPlaceEntityOnDisplay(displayId,
                isResizeable(false /* checkSupportsPip */), -1 /* don't check PID */,
                -1 /* don't check UID */, null /* activityInfo */);
    }

    /**
     * Check that a given bounds matches the application requested orientation.
     *
     * @param bounds The bounds to be tested.
     * @return True if the requested bounds are okay for a resizing request.
     */
    private boolean canResizeToBounds(Rect bounds) {
        if (bounds == null || getStackId() != FREEFORM_WORKSPACE_STACK_ID) {
            // Note: If not on the freeform workspace, we ignore the bounds.
            return true;
        }
        final boolean landscape = bounds.width() > bounds.height();
        if (mResizeMode == RESIZE_MODE_FORCE_RESIZABLE_PRESERVE_ORIENTATION) {
            return mBounds == null || landscape == (mBounds.width() > mBounds.height());
        }
        return (mResizeMode != RESIZE_MODE_FORCE_RESIZABLE_PORTRAIT_ONLY || !landscape)
                && (mResizeMode != RESIZE_MODE_FORCE_RESIZABLE_LANDSCAPE_ONLY || landscape);
    }

    /**
     * @return {@code true} if the task is being cleared for the purposes of being reused.
     */
    boolean isClearingToReuseTask() {
        return mReuseTask;
    }

    /**
     * Find the activity in the history stack within the given task.  Returns
     * the index within the history at which it's found, or < 0 if not found.
     */
    final ActivityRecord findActivityInHistoryLocked(ActivityRecord r) {
        final ComponentName realActivity = r.realActivity;
        for (int activityNdx = mActivities.size() - 1; activityNdx >= 0; --activityNdx) {
            ActivityRecord candidate = mActivities.get(activityNdx);
            if (candidate.finishing) {
                continue;
            }
            if (candidate.realActivity.equals(realActivity)) {
                return candidate;
            }
        }
        return null;
    }

    /** Updates the last task description values. */
    void updateTaskDescription() {
        // Traverse upwards looking for any break between main task activities and
        // utility activities.
        int activityNdx;
        final int numActivities = mActivities.size();
        final boolean relinquish = numActivities != 0 &&
                (mActivities.get(0).info.flags & FLAG_RELINQUISH_TASK_IDENTITY) != 0;
        for (activityNdx = Math.min(numActivities, 1); activityNdx < numActivities;
                ++activityNdx) {
            final ActivityRecord r = mActivities.get(activityNdx);
            if (relinquish && (r.info.flags & FLAG_RELINQUISH_TASK_IDENTITY) == 0) {
                // This will be the top activity for determining taskDescription. Pre-inc to
                // overcome initial decrement below.
                ++activityNdx;
                break;
            }
            if (r.intent != null &&
                    (r.intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET) != 0) {
                break;
            }
        }
        if (activityNdx > 0) {
            // Traverse downwards starting below break looking for set label, icon.
            // Note that if there are activities in the task but none of them set the
            // recent activity values, then we do not fall back to the last set
            // values in the TaskRecord.
            String label = null;
            String iconFilename = null;
            int colorPrimary = 0;
            int colorBackground = 0;
            int statusBarColor = 0;
            int navigationBarColor = 0;
            boolean topActivity = true;
            for (--activityNdx; activityNdx >= 0; --activityNdx) {
                final ActivityRecord r = mActivities.get(activityNdx);
                if (r.taskDescription != null) {
                    if (label == null) {
                        label = r.taskDescription.getLabel();
                    }
                    if (iconFilename == null) {
                        iconFilename = r.taskDescription.getIconFilename();
                    }
                    if (colorPrimary == 0) {
                        colorPrimary = r.taskDescription.getPrimaryColor();
                    }
                    if (topActivity) {
                        colorBackground = r.taskDescription.getBackgroundColor();
                        statusBarColor = r.taskDescription.getStatusBarColor();
                        navigationBarColor = r.taskDescription.getNavigationBarColor();
                    }
                }
                topActivity = false;
            }
            lastTaskDescription = new TaskDescription(label, null, iconFilename, colorPrimary,
                    colorBackground, statusBarColor, navigationBarColor);
            if (mWindowContainerController != null) {
                mWindowContainerController.setTaskDescription(lastTaskDescription);
            }
            // Update the task affiliation color if we are the parent of the group
            if (taskId == mAffiliatedTaskId) {
                mAffiliatedTaskColor = lastTaskDescription.getPrimaryColor();
            }
        }
    }

    int findEffectiveRootIndex() {
        int effectiveNdx = 0;
        final int topActivityNdx = mActivities.size() - 1;
        for (int activityNdx = 0; activityNdx <= topActivityNdx; ++activityNdx) {
            final ActivityRecord r = mActivities.get(activityNdx);
            if (r.finishing) {
                continue;
            }
            effectiveNdx = activityNdx;
            if ((r.info.flags & FLAG_RELINQUISH_TASK_IDENTITY) == 0) {
                break;
            }
        }
        return effectiveNdx;
    }

    void updateEffectiveIntent() {
        final int effectiveRootIndex = findEffectiveRootIndex();
        final ActivityRecord r = mActivities.get(effectiveRootIndex);
        setIntent(r);
    }

    void saveToXml(XmlSerializer out) throws IOException, XmlPullParserException {
        if (DEBUG_RECENTS) Slog.i(TAG_RECENTS, "Saving task=" + this);

        out.attribute(null, ATTR_TASKID, String.valueOf(taskId));
        if (realActivity != null) {
            out.attribute(null, ATTR_REALACTIVITY, realActivity.flattenToShortString());
        }
        out.attribute(null, ATTR_REALACTIVITY_SUSPENDED, String.valueOf(realActivitySuspended));
        if (origActivity != null) {
            out.attribute(null, ATTR_ORIGACTIVITY, origActivity.flattenToShortString());
        }
        // Write affinity, and root affinity if it is different from affinity.
        // We use the special string "@" for a null root affinity, so we can identify
        // later whether we were given a root affinity or should just make it the
        // same as the affinity.
        if (affinity != null) {
            out.attribute(null, ATTR_AFFINITY, affinity);
            if (!affinity.equals(rootAffinity)) {
                out.attribute(null, ATTR_ROOT_AFFINITY, rootAffinity != null ? rootAffinity : "@");
            }
        } else if (rootAffinity != null) {
            out.attribute(null, ATTR_ROOT_AFFINITY, rootAffinity != null ? rootAffinity : "@");
        }
        out.attribute(null, ATTR_ROOTHASRESET, String.valueOf(rootWasReset));
        out.attribute(null, ATTR_AUTOREMOVERECENTS, String.valueOf(autoRemoveRecents));
        out.attribute(null, ATTR_ASKEDCOMPATMODE, String.valueOf(askedCompatMode));
        out.attribute(null, ATTR_USERID, String.valueOf(userId));
        out.attribute(null, ATTR_USER_SETUP_COMPLETE, String.valueOf(mUserSetupComplete));
        out.attribute(null, ATTR_EFFECTIVE_UID, String.valueOf(effectiveUid));
        out.attribute(null, ATTR_TASKTYPE, String.valueOf(taskType));
        out.attribute(null, ATTR_FIRSTACTIVETIME, String.valueOf(firstActiveTime));
        out.attribute(null, ATTR_LASTACTIVETIME, String.valueOf(lastActiveTime));
        out.attribute(null, ATTR_LASTTIMEMOVED, String.valueOf(mLastTimeMoved));
        out.attribute(null, ATTR_NEVERRELINQUISH, String.valueOf(mNeverRelinquishIdentity));
        if (lastDescription != null) {
            out.attribute(null, ATTR_LASTDESCRIPTION, lastDescription.toString());
        }
        if (lastTaskDescription != null) {
            lastTaskDescription.saveToXml(out);
        }
        mLastThumbnailInfo.saveToXml(out);
        out.attribute(null, ATTR_TASK_AFFILIATION_COLOR, String.valueOf(mAffiliatedTaskColor));
        out.attribute(null, ATTR_TASK_AFFILIATION, String.valueOf(mAffiliatedTaskId));
        out.attribute(null, ATTR_PREV_AFFILIATION, String.valueOf(mPrevAffiliateTaskId));
        out.attribute(null, ATTR_NEXT_AFFILIATION, String.valueOf(mNextAffiliateTaskId));
        out.attribute(null, ATTR_CALLING_UID, String.valueOf(mCallingUid));
        out.attribute(null, ATTR_CALLING_PACKAGE, mCallingPackage == null ? "" : mCallingPackage);
        out.attribute(null, ATTR_RESIZE_MODE, String.valueOf(mResizeMode));
        out.attribute(null, ATTR_SUPPORTS_PICTURE_IN_PICTURE,
                String.valueOf(mSupportsPictureInPicture));
        out.attribute(null, ATTR_PRIVILEGED, String.valueOf(mPrivileged));
        if (mLastNonFullscreenBounds != null) {
            out.attribute(
                    null, ATTR_NON_FULLSCREEN_BOUNDS, mLastNonFullscreenBounds.flattenToString());
        }
        out.attribute(null, ATTR_MIN_WIDTH, String.valueOf(mMinWidth));
        out.attribute(null, ATTR_MIN_HEIGHT, String.valueOf(mMinHeight));
        out.attribute(null, ATTR_PERSIST_TASK_VERSION, String.valueOf(PERSIST_TASK_VERSION));

        if (affinityIntent != null) {
            out.startTag(null, TAG_AFFINITYINTENT);
            affinityIntent.saveToXml(out);
            out.endTag(null, TAG_AFFINITYINTENT);
        }

        out.startTag(null, TAG_INTENT);
        intent.saveToXml(out);
        out.endTag(null, TAG_INTENT);

        final ArrayList<ActivityRecord> activities = mActivities;
        final int numActivities = activities.size();
        for (int activityNdx = 0; activityNdx < numActivities; ++activityNdx) {
            final ActivityRecord r = activities.get(activityNdx);
            if (r.info.persistableMode == ActivityInfo.PERSIST_ROOT_ONLY || !r.isPersistable() ||
                    ((r.intent.getFlags() & FLAG_ACTIVITY_NEW_DOCUMENT
                            | FLAG_ACTIVITY_RETAIN_IN_RECENTS) == FLAG_ACTIVITY_NEW_DOCUMENT) &&
                            activityNdx > 0) {
                // Stop at first non-persistable or first break in task (CLEAR_WHEN_TASK_RESET).
                break;
            }
            out.startTag(null, TAG_ACTIVITY);
            r.saveToXml(out);
            out.endTag(null, TAG_ACTIVITY);
        }
    }

    static TaskRecord restoreFromXml(XmlPullParser in, ActivityStackSupervisor stackSupervisor)
            throws IOException, XmlPullParserException {
        Intent intent = null;
        Intent affinityIntent = null;
        ArrayList<ActivityRecord> activities = new ArrayList<>();
        ComponentName realActivity = null;
        boolean realActivitySuspended = false;
        ComponentName origActivity = null;
        String affinity = null;
        String rootAffinity = null;
        boolean hasRootAffinity = false;
        boolean rootHasReset = false;
        boolean autoRemoveRecents = false;
        boolean askedCompatMode = false;
        int taskType = ActivityRecord.APPLICATION_ACTIVITY_TYPE;
        int userId = 0;
        boolean userSetupComplete = true;
        int effectiveUid = -1;
        String lastDescription = null;
        long firstActiveTime = -1;
        long lastActiveTime = -1;
        long lastTimeOnTop = 0;
        boolean neverRelinquishIdentity = true;
        int taskId = INVALID_TASK_ID;
        final int outerDepth = in.getDepth();
        TaskDescription taskDescription = new TaskDescription();
        TaskThumbnailInfo thumbnailInfo = new TaskThumbnailInfo();
        int taskAffiliation = INVALID_TASK_ID;
        int taskAffiliationColor = 0;
        int prevTaskId = INVALID_TASK_ID;
        int nextTaskId = INVALID_TASK_ID;
        int callingUid = -1;
        String callingPackage = "";
        int resizeMode = RESIZE_MODE_FORCE_RESIZEABLE;
        boolean supportsPictureInPicture = false;
        boolean privileged = false;
        Rect bounds = null;
        int minWidth = INVALID_MIN_SIZE;
        int minHeight = INVALID_MIN_SIZE;
        int persistTaskVersion = 0;

        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if (TaskPersister.DEBUG) Slog.d(TaskPersister.TAG, "TaskRecord: attribute name=" +
                    attrName + " value=" + attrValue);
            if (ATTR_TASKID.equals(attrName)) {
                if (taskId == INVALID_TASK_ID) taskId = Integer.parseInt(attrValue);
            } else if (ATTR_REALACTIVITY.equals(attrName)) {
                realActivity = ComponentName.unflattenFromString(attrValue);
            } else if (ATTR_REALACTIVITY_SUSPENDED.equals(attrName)) {
                realActivitySuspended = Boolean.valueOf(attrValue);
            } else if (ATTR_ORIGACTIVITY.equals(attrName)) {
                origActivity = ComponentName.unflattenFromString(attrValue);
            } else if (ATTR_AFFINITY.equals(attrName)) {
                affinity = attrValue;
            } else if (ATTR_ROOT_AFFINITY.equals(attrName)) {
                rootAffinity = attrValue;
                hasRootAffinity = true;
            } else if (ATTR_ROOTHASRESET.equals(attrName)) {
                rootHasReset = Boolean.parseBoolean(attrValue);
            } else if (ATTR_AUTOREMOVERECENTS.equals(attrName)) {
                autoRemoveRecents = Boolean.parseBoolean(attrValue);
            } else if (ATTR_ASKEDCOMPATMODE.equals(attrName)) {
                askedCompatMode = Boolean.parseBoolean(attrValue);
            } else if (ATTR_USERID.equals(attrName)) {
                userId = Integer.parseInt(attrValue);
            } else if (ATTR_USER_SETUP_COMPLETE.equals(attrName)) {
                userSetupComplete = Boolean.parseBoolean(attrValue);
            } else if (ATTR_EFFECTIVE_UID.equals(attrName)) {
                effectiveUid = Integer.parseInt(attrValue);
            } else if (ATTR_TASKTYPE.equals(attrName)) {
                taskType = Integer.parseInt(attrValue);
            } else if (ATTR_FIRSTACTIVETIME.equals(attrName)) {
                firstActiveTime = Long.parseLong(attrValue);
            } else if (ATTR_LASTACTIVETIME.equals(attrName)) {
                lastActiveTime = Long.parseLong(attrValue);
            } else if (ATTR_LASTDESCRIPTION.equals(attrName)) {
                lastDescription = attrValue;
            } else if (ATTR_LASTTIMEMOVED.equals(attrName)) {
                lastTimeOnTop = Long.parseLong(attrValue);
            } else if (ATTR_NEVERRELINQUISH.equals(attrName)) {
                neverRelinquishIdentity = Boolean.parseBoolean(attrValue);
            } else if (attrName.startsWith(TaskThumbnailInfo.ATTR_TASK_THUMBNAILINFO_PREFIX)) {
                thumbnailInfo.restoreFromXml(attrName, attrValue);
            } else if (attrName.startsWith(TaskDescription.ATTR_TASKDESCRIPTION_PREFIX)) {
                taskDescription.restoreFromXml(attrName, attrValue);
            } else if (ATTR_TASK_AFFILIATION.equals(attrName)) {
                taskAffiliation = Integer.parseInt(attrValue);
            } else if (ATTR_PREV_AFFILIATION.equals(attrName)) {
                prevTaskId = Integer.parseInt(attrValue);
            } else if (ATTR_NEXT_AFFILIATION.equals(attrName)) {
                nextTaskId = Integer.parseInt(attrValue);
            } else if (ATTR_TASK_AFFILIATION_COLOR.equals(attrName)) {
                taskAffiliationColor = Integer.parseInt(attrValue);
            } else if (ATTR_CALLING_UID.equals(attrName)) {
                callingUid = Integer.parseInt(attrValue);
            } else if (ATTR_CALLING_PACKAGE.equals(attrName)) {
                callingPackage = attrValue;
            } else if (ATTR_RESIZE_MODE.equals(attrName)) {
                resizeMode = Integer.parseInt(attrValue);
            } else if (ATTR_SUPPORTS_PICTURE_IN_PICTURE.equals(attrName)) {
                supportsPictureInPicture = Boolean.parseBoolean(attrValue);
            } else if (ATTR_PRIVILEGED.equals(attrName)) {
                privileged = Boolean.parseBoolean(attrValue);
            } else if (ATTR_NON_FULLSCREEN_BOUNDS.equals(attrName)) {
                bounds = Rect.unflattenFromString(attrValue);
            } else if (ATTR_MIN_WIDTH.equals(attrName)) {
                minWidth = Integer.parseInt(attrValue);
            } else if (ATTR_MIN_HEIGHT.equals(attrName)) {
                minHeight = Integer.parseInt(attrValue);
            } else if (ATTR_PERSIST_TASK_VERSION.equals(attrName)) {
                persistTaskVersion = Integer.parseInt(attrValue);
            } else {
                Slog.w(TAG, "TaskRecord: Unknown attribute=" + attrName);
            }
        }

        int event;
        while (((event = in.next()) != XmlPullParser.END_DOCUMENT) &&
                (event != XmlPullParser.END_TAG || in.getDepth() >= outerDepth)) {
            if (event == XmlPullParser.START_TAG) {
                final String name = in.getName();
                if (TaskPersister.DEBUG) Slog.d(TaskPersister.TAG, "TaskRecord: START_TAG name=" +
                        name);
                if (TAG_AFFINITYINTENT.equals(name)) {
                    affinityIntent = Intent.restoreFromXml(in);
                } else if (TAG_INTENT.equals(name)) {
                    intent = Intent.restoreFromXml(in);
                } else if (TAG_ACTIVITY.equals(name)) {
                    ActivityRecord activity = ActivityRecord.restoreFromXml(in, stackSupervisor);
                    if (TaskPersister.DEBUG) Slog.d(TaskPersister.TAG, "TaskRecord: activity=" +
                            activity);
                    if (activity != null) {
                        activities.add(activity);
                    }
                } else {
                    Slog.e(TAG, "restoreTask: Unexpected name=" + name);
                    XmlUtils.skipCurrentTag(in);
                }
            }
        }
        if (!hasRootAffinity) {
            rootAffinity = affinity;
        } else if ("@".equals(rootAffinity)) {
            rootAffinity = null;
        }
        if (effectiveUid <= 0) {
            Intent checkIntent = intent != null ? intent : affinityIntent;
            effectiveUid = 0;
            if (checkIntent != null) {
                IPackageManager pm = AppGlobals.getPackageManager();
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(
                            checkIntent.getComponent().getPackageName(),
                            PackageManager.MATCH_UNINSTALLED_PACKAGES
                                    | PackageManager.MATCH_DISABLED_COMPONENTS, userId);
                    if (ai != null) {
                        effectiveUid = ai.uid;
                    }
                } catch (RemoteException e) {
                }
            }
            Slog.w(TAG, "Updating task #" + taskId + " for " + checkIntent
                    + ": effectiveUid=" + effectiveUid);
        }

        if (persistTaskVersion < 1) {
            // We need to convert the resize mode of home activities saved before version one if
            // they are marked as RESIZE_MODE_RESIZEABLE to RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION
            // since we didn't have that differentiation before version 1 and the system didn't
            // resize home activities before then.
            if (taskType == HOME_ACTIVITY_TYPE && resizeMode == RESIZE_MODE_RESIZEABLE) {
                resizeMode = RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION;
            }
        } else {
            // This activity has previously marked itself explicitly as both resizeable and
            // supporting picture-in-picture.  Since there is no longer a requirement for
            // picture-in-picture activities to be resizeable, we can mark this simply as
            // resizeable and supporting picture-in-picture separately.
            if (resizeMode == RESIZE_MODE_RESIZEABLE_AND_PIPABLE_DEPRECATED) {
                resizeMode = RESIZE_MODE_RESIZEABLE;
                supportsPictureInPicture = true;
            }
        }

        final TaskRecord task = new TaskRecord(stackSupervisor.mService, taskId, intent,
                affinityIntent, affinity, rootAffinity, realActivity, origActivity, rootHasReset,
                autoRemoveRecents, askedCompatMode, taskType, userId, effectiveUid, lastDescription,
                activities, firstActiveTime, lastActiveTime, lastTimeOnTop, neverRelinquishIdentity,
                taskDescription, thumbnailInfo, taskAffiliation, prevTaskId, nextTaskId,
                taskAffiliationColor, callingUid, callingPackage, resizeMode,
                supportsPictureInPicture, privileged, realActivitySuspended, userSetupComplete,
                minWidth, minHeight);
        task.updateOverrideConfiguration(bounds);

        for (int activityNdx = activities.size() - 1; activityNdx >=0; --activityNdx) {
            activities.get(activityNdx).setTask(task);
        }

        if (DEBUG_RECENTS) Slog.d(TAG_RECENTS, "Restored task=" + task);
        return task;
    }

    private void adjustForMinimalTaskDimensions(Rect bounds) {
        if (bounds == null) {
            return;
        }
        int minWidth = mMinWidth;
        int minHeight = mMinHeight;
        // If the task has no requested minimal size, we'd like to enforce a minimal size
        // so that the user can not render the task too small to manipulate. We don't need
        // to do this for the pinned stack as the bounds are controlled by the system.
        if (getStackId() != PINNED_STACK_ID) {
            if (minWidth == INVALID_MIN_SIZE) {
                minWidth = mService.mStackSupervisor.mDefaultMinSizeOfResizeableTask;
            }
            if (minHeight == INVALID_MIN_SIZE) {
                minHeight = mService.mStackSupervisor.mDefaultMinSizeOfResizeableTask;
            }
        }
        final boolean adjustWidth = minWidth > bounds.width();
        final boolean adjustHeight = minHeight > bounds.height();
        if (!(adjustWidth || adjustHeight)) {
            return;
        }

        if (adjustWidth) {
            if (mBounds != null && bounds.right == mBounds.right) {
                bounds.left = bounds.right - minWidth;
            } else {
                // Either left bounds match, or neither match, or the previous bounds were
                // fullscreen and we default to keeping left.
                bounds.right = bounds.left + minWidth;
            }
        }
        if (adjustHeight) {
            if (mBounds != null && bounds.bottom == mBounds.bottom) {
                bounds.top = bounds.bottom - minHeight;
            } else {
                // Either top bounds match, or neither match, or the previous bounds were
                // fullscreen and we default to keeping top.
                bounds.bottom = bounds.top + minHeight;
            }
        }
    }

    /**
     * @return a new Configuration for this Task, given the provided {@param bounds} and
     *         {@param insetBounds}.
     */
    Configuration computeNewOverrideConfigurationForBounds(Rect bounds, Rect insetBounds) {
        // Compute a new override configuration for the given bounds, if fullscreen bounds
        // (bounds == null), then leave the override config unset
        final Configuration newOverrideConfig = new Configuration();
        if (bounds != null) {
            newOverrideConfig.setTo(getOverrideConfiguration());
            mTmpRect.set(bounds);
            adjustForMinimalTaskDimensions(mTmpRect);
            computeOverrideConfiguration(newOverrideConfig, mTmpRect, insetBounds,
                    mTmpRect.right != bounds.right, mTmpRect.bottom != bounds.bottom);
        }

        return newOverrideConfig;
    }

    /**
     * Update task's override configuration based on the bounds.
     * @param bounds The bounds of the task.
     * @return True if the override configuration was updated.
     */
    boolean updateOverrideConfiguration(Rect bounds) {
        return updateOverrideConfiguration(bounds, null /* insetBounds */);
    }

    /**
     * Update task's override configuration based on the bounds.
     * @param bounds The bounds of the task.
     * @param insetBounds The bounds used to calculate the system insets, which is used here to
     *                    subtract the navigation bar/status bar size from the screen size reported
     *                    to the application. See {@link IActivityManager#resizeDockedStack}.
     * @return True if the override configuration was updated.
     */
    boolean updateOverrideConfiguration(Rect bounds, @Nullable Rect insetBounds) {
        if (Objects.equals(mBounds, bounds)) {
            return false;
        }
        mTmpConfig.setTo(getOverrideConfiguration());
        final boolean oldFullscreen = mFullscreen;
        final Configuration newConfig = getOverrideConfiguration();

        mFullscreen = bounds == null;
        if (mFullscreen) {
            if (mBounds != null && StackId.persistTaskBounds(mStack.mStackId)) {
                mLastNonFullscreenBounds = mBounds;
            }
            mBounds = null;
            newConfig.unset();
        } else {
            mTmpRect.set(bounds);
            adjustForMinimalTaskDimensions(mTmpRect);
            if (mBounds == null) {
                mBounds = new Rect(mTmpRect);
            } else {
                mBounds.set(mTmpRect);
            }
            if (mStack == null || StackId.persistTaskBounds(mStack.mStackId)) {
                mLastNonFullscreenBounds = mBounds;
            }
            computeOverrideConfiguration(newConfig, mTmpRect, insetBounds,
                    mTmpRect.right != bounds.right, mTmpRect.bottom != bounds.bottom);
        }
        onOverrideConfigurationChanged(newConfig);

        if (mFullscreen != oldFullscreen) {
            mService.mStackSupervisor.scheduleUpdateMultiWindowMode(this);
        }

        return !mTmpConfig.equals(newConfig);
    }

    /** Clears passed config and fills it with new override values. */
    // TODO(b/36505427): TaskRecord.computeOverrideConfiguration() is a utility method that doesn't
    // depend on task or stacks, but uses those object to get the display to base the calculation
    // on. Probably best to centralize calculations like this in ConfigurationContainer.
    void computeOverrideConfiguration(Configuration config, Rect bounds, Rect insetBounds,
            boolean overrideWidth, boolean overrideHeight) {
        mTmpNonDecorBounds.set(bounds);
        mTmpStableBounds.set(bounds);

        config.unset();
        final Configuration parentConfig = getParent().getConfiguration();

        final float density = parentConfig.densityDpi * DisplayMetrics.DENSITY_DEFAULT_SCALE;

        if (mStack != null) {
            final StackWindowController stackController = mStack.getWindowContainerController();
            stackController.adjustConfigurationForBounds(bounds, insetBounds,
                    mTmpNonDecorBounds, mTmpStableBounds, overrideWidth, overrideHeight, density,
                    config, parentConfig);
        } else {
            throw new IllegalArgumentException("Expected stack when calculating override config");
        }

        config.orientation = (config.screenWidthDp <= config.screenHeightDp)
                ? Configuration.ORIENTATION_PORTRAIT
                : Configuration.ORIENTATION_LANDSCAPE;

        // For calculating screen layout, we need to use the non-decor inset screen area for the
        // calculation for compatibility reasons, i.e. screen area without system bars that could
        // never go away in Honeycomb.
        final int compatScreenWidthDp = (int) (mTmpNonDecorBounds.width() / density);
        final int compatScreenHeightDp = (int) (mTmpNonDecorBounds.height() / density);
        // We're only overriding LONG, SIZE and COMPAT parts of screenLayout, so we start override
        // calculation with partial default.
        final int sl = Configuration.SCREENLAYOUT_LONG_YES | Configuration.SCREENLAYOUT_SIZE_XLARGE;
        final int longSize = Math.max(compatScreenHeightDp, compatScreenWidthDp);
        final int shortSize = Math.min(compatScreenHeightDp, compatScreenWidthDp);
        config.screenLayout = Configuration.reduceScreenLayout(sl, longSize, shortSize);

    }

    Rect updateOverrideConfigurationFromLaunchBounds() {
        final Rect bounds = validateBounds(getLaunchBounds());
        updateOverrideConfiguration(bounds);
        if (bounds != null) {
            bounds.set(mBounds);
        }
        return bounds;
    }

    static Rect validateBounds(Rect bounds) {
        if (bounds != null && bounds.isEmpty()) {
            Slog.wtf(TAG, "Received strange task bounds: " + bounds, new Throwable());
            return null;
        }
        return bounds;
    }

    /** Updates the task's bounds and override configuration to match what is expected for the
     * input stack. */
    void updateOverrideConfigurationForStack(ActivityStack inStack) {
        if (mStack != null && mStack == inStack) {
            return;
        }

        if (inStack.mStackId == FREEFORM_WORKSPACE_STACK_ID) {
            if (!isResizeable()) {
                throw new IllegalArgumentException("Can not position non-resizeable task="
                        + this + " in stack=" + inStack);
            }
            if (mBounds != null) {
                return;
            }
            if (mLastNonFullscreenBounds != null) {
                updateOverrideConfiguration(mLastNonFullscreenBounds);
            } else {
                inStack.layoutTaskInStack(this, null);
            }
        } else {
            updateOverrideConfiguration(inStack.mBounds);
        }
    }

    /**
     * Returns the correct stack to use based on task type and currently set bounds,
     * regardless of the focused stack and current stack association of the task.
     * The task will be moved (and stack focus changed) later if necessary.
     */
    int getLaunchStackId() {
        if (isRecentsTask()) {
            return RECENTS_STACK_ID;
        }
        if (isHomeTask()) {
            return HOME_STACK_ID;
        }
        if (isAssistantTask()) {
            return ASSISTANT_STACK_ID;
        }
        if (mBounds != null) {
            return FREEFORM_WORKSPACE_STACK_ID;
        }
        return FULLSCREEN_WORKSPACE_STACK_ID;
    }

    /** Returns the bounds that should be used to launch this task. */
    Rect getLaunchBounds() {
        if (mStack == null) {
            return null;
        }

        final int stackId = mStack.mStackId;
        if (stackId == HOME_STACK_ID
                || stackId == RECENTS_STACK_ID
                || stackId == ASSISTANT_STACK_ID
                || stackId == FULLSCREEN_WORKSPACE_STACK_ID
                || (stackId == DOCKED_STACK_ID && !isResizeable())) {
            return isResizeable() ? mStack.mBounds : null;
        } else if (!StackId.persistTaskBounds(stackId)) {
            return mStack.mBounds;
        }
        return mLastNonFullscreenBounds;
    }

    void addStartingWindowsForVisibleActivities(boolean taskSwitch) {
        for (int activityNdx = mActivities.size() - 1; activityNdx >= 0; --activityNdx) {
            final ActivityRecord r = mActivities.get(activityNdx);
            if (r.visible) {
                r.showStartingWindow(null /* prev */, false /* newTask */, taskSwitch);
            }
        }
    }

    void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.print("userId="); pw.print(userId);
                pw.print(" effectiveUid="); UserHandle.formatUid(pw, effectiveUid);
                pw.print(" mCallingUid="); UserHandle.formatUid(pw, mCallingUid);
                pw.print(" mUserSetupComplete="); pw.print(mUserSetupComplete);
                pw.print(" mCallingPackage="); pw.println(mCallingPackage);
        if (affinity != null || rootAffinity != null) {
            pw.print(prefix); pw.print("affinity="); pw.print(affinity);
            if (affinity == null || !affinity.equals(rootAffinity)) {
                pw.print(" root="); pw.println(rootAffinity);
            } else {
                pw.println();
            }
        }
        if (voiceSession != null || voiceInteractor != null) {
            pw.print(prefix); pw.print("VOICE: session=0x");
            pw.print(Integer.toHexString(System.identityHashCode(voiceSession)));
            pw.print(" interactor=0x");
            pw.println(Integer.toHexString(System.identityHashCode(voiceInteractor)));
        }
        if (intent != null) {
            StringBuilder sb = new StringBuilder(128);
            sb.append(prefix); sb.append("intent={");
            intent.toShortString(sb, false, true, false, true);
            sb.append('}');
            pw.println(sb.toString());
        }
        if (affinityIntent != null) {
            StringBuilder sb = new StringBuilder(128);
            sb.append(prefix); sb.append("affinityIntent={");
            affinityIntent.toShortString(sb, false, true, false, true);
            sb.append('}');
            pw.println(sb.toString());
        }
        if (origActivity != null) {
            pw.print(prefix); pw.print("origActivity=");
            pw.println(origActivity.flattenToShortString());
        }
        if (realActivity != null) {
            pw.print(prefix); pw.print("realActivity=");
            pw.println(realActivity.flattenToShortString());
        }
        if (autoRemoveRecents || isPersistable || taskType != 0 || mTaskToReturnTo != 0
                || numFullscreen != 0) {
            pw.print(prefix); pw.print("autoRemoveRecents="); pw.print(autoRemoveRecents);
                    pw.print(" isPersistable="); pw.print(isPersistable);
                    pw.print(" numFullscreen="); pw.print(numFullscreen);
                    pw.print(" taskType="); pw.print(taskType);
                    pw.print(" mTaskToReturnTo="); pw.println(mTaskToReturnTo);
        }
        if (rootWasReset || mNeverRelinquishIdentity || mReuseTask
                || mLockTaskAuth != LOCK_TASK_AUTH_PINNABLE) {
            pw.print(prefix); pw.print("rootWasReset="); pw.print(rootWasReset);
                    pw.print(" mNeverRelinquishIdentity="); pw.print(mNeverRelinquishIdentity);
                    pw.print(" mReuseTask="); pw.print(mReuseTask);
                    pw.print(" mLockTaskAuth="); pw.println(lockTaskAuthToString());
        }
        if (mAffiliatedTaskId != taskId || mPrevAffiliateTaskId != INVALID_TASK_ID
                || mPrevAffiliate != null || mNextAffiliateTaskId != INVALID_TASK_ID
                || mNextAffiliate != null) {
            pw.print(prefix); pw.print("affiliation="); pw.print(mAffiliatedTaskId);
                    pw.print(" prevAffiliation="); pw.print(mPrevAffiliateTaskId);
                    pw.print(" (");
                    if (mPrevAffiliate == null) {
                        pw.print("null");
                    } else {
                        pw.print(Integer.toHexString(System.identityHashCode(mPrevAffiliate)));
                    }
                    pw.print(") nextAffiliation="); pw.print(mNextAffiliateTaskId);
                    pw.print(" (");
                    if (mNextAffiliate == null) {
                        pw.print("null");
                    } else {
                        pw.print(Integer.toHexString(System.identityHashCode(mNextAffiliate)));
                    }
                    pw.println(")");
        }
        pw.print(prefix); pw.print("Activities="); pw.println(mActivities);
        if (!askedCompatMode || !inRecents || !isAvailable) {
            pw.print(prefix); pw.print("askedCompatMode="); pw.print(askedCompatMode);
                    pw.print(" inRecents="); pw.print(inRecents);
                    pw.print(" isAvailable="); pw.println(isAvailable);
        }
        pw.print(prefix); pw.print("lastThumbnail="); pw.print(mLastThumbnail);
                pw.print(" lastThumbnailFile="); pw.println(mLastThumbnailFile);
        if (lastDescription != null) {
            pw.print(prefix); pw.print("lastDescription="); pw.println(lastDescription);
        }
        pw.print(prefix); pw.print("stackId="); pw.println(getStackId());
        pw.print(prefix + "hasBeenVisible=" + hasBeenVisible);
                pw.print(" mResizeMode=" + ActivityInfo.resizeModeToString(mResizeMode));
                pw.print(" mSupportsPictureInPicture=" + mSupportsPictureInPicture);
                pw.print(" isResizeable=" + isResizeable());
                pw.print(" firstActiveTime=" + firstActiveTime);
                pw.print(" lastActiveTime=" + lastActiveTime);
                pw.println(" (inactive for " + (getInactiveDuration() / 1000) + "s)");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        if (stringName != null) {
            sb.append(stringName);
            sb.append(" U=");
            sb.append(userId);
            sb.append(" StackId=");
            sb.append(getStackId());
            sb.append(" sz=");
            sb.append(mActivities.size());
            sb.append('}');
            return sb.toString();
        }
        sb.append("TaskRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" #");
        sb.append(taskId);
        if (affinity != null) {
            sb.append(" A=");
            sb.append(affinity);
        } else if (intent != null) {
            sb.append(" I=");
            sb.append(intent.getComponent().flattenToShortString());
        } else if (affinityIntent != null) {
            sb.append(" aI=");
            sb.append(affinityIntent.getComponent().flattenToShortString());
        } else {
            sb.append(" ??");
        }
        stringName = sb.toString();
        return toString();
    }
}
