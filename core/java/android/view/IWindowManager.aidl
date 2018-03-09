/*
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.view;

import com.android.internal.app.IAssistScreenshotReceiver;
import com.android.internal.os.IResultReceiver;
import com.android.internal.view.IInputContext;
import com.android.internal.view.IInputMethodClient;
import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IShortcutService;

import android.content.Intent;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.GraphicBuffer;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.ParcelFileDescriptor;
import android.view.IApplicationToken;
import android.view.IAppTransitionAnimationSpecsFuture;
import android.view.IDockedStackListener;
import android.view.IOnKeyguardExitResult;
import android.view.IPinnedStackListener;
import android.view.IRotationWatcher;
import android.view.IWallpaperVisibilityListener;
import android.view.IWindowSession;
import android.view.IWindowSessionCallback;
import android.view.KeyEvent;
import android.view.InputEvent;
import android.view.MagnificationSpec;
import android.view.MotionEvent;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.IInputFilter;
import android.view.AppTransitionAnimationSpec;
import android.view.WindowContentFrameStats;
import android.view.WindowManager;

/**
 * System private interface to the window manager.
 *
 * {@hide}
 */
interface IWindowManager
{
    /**
     * ===== NOTICE =====
     * The first three methods must remain the first three methods. Scripts
     * and tools rely on their transaction number to work properly.
     */
    // This is used for debugging
    boolean startViewServer(int port);   // Transaction #1
    boolean stopViewServer();            // Transaction #2
    boolean isViewServerRunning();       // Transaction #3

    IWindowSession openSession(in IWindowSessionCallback callback, in IInputMethodClient client,
            in IInputContext inputContext);
    boolean inputMethodClientHasFocus(IInputMethodClient client);

    void getInitialDisplaySize(int displayId, out Point size);
    void getBaseDisplaySize(int displayId, out Point size);
    void setForcedDisplaySize(int displayId, int width, int height);
    void clearForcedDisplaySize(int displayId);
    int getInitialDisplayDensity(int displayId);
    int getBaseDisplayDensity(int displayId);
    void setForcedDisplayDensityForUser(int displayId, int density, int userId);
    void clearForcedDisplayDensityForUser(int displayId, int userId);
    void setForcedDisplayScalingMode(int displayId, int mode); // 0 = auto, 1 = disable

    void setOverscan(int displayId, int left, int top, int right, int bottom);

    // These can only be called when holding the MANAGE_APP_TOKENS permission.
    void setEventDispatching(boolean enabled);
    void addWindowToken(IBinder token, int type, int displayId);
    void removeWindowToken(IBinder token, int displayId);
    void setFocusedApp(IBinder token, boolean moveFocusNow);
    void prepareAppTransition(int transit, boolean alwaysKeepCurrent);
    int getPendingAppTransition();
    void overridePendingAppTransition(String packageName, int enterAnim, int exitAnim,
            IRemoteCallback startedCallback);
    void overridePendingAppTransitionScaleUp(int startX, int startY, int startWidth,
            int startHeight);
    void overridePendingAppTransitionClipReveal(int startX, int startY,
            int startWidth, int startHeight);
    void overridePendingAppTransitionThumb(in GraphicBuffer srcThumb, int startX, int startY,
            IRemoteCallback startedCallback, boolean scaleUp);
    void overridePendingAppTransitionAspectScaledThumb(in GraphicBuffer srcThumb, int startX,
            int startY, int targetWidth, int targetHeight, IRemoteCallback startedCallback,
            boolean scaleUp);
    /**
     * Overrides animation for app transition that exits from an application to a multi-window
     * environment and allows specifying transition animation parameters for each window.
     *
     * @param specs Array of transition animation descriptions for entering windows.
     *
     * @hide
     */
    void overridePendingAppTransitionMultiThumb(in AppTransitionAnimationSpec[] specs,
            IRemoteCallback startedCallback, IRemoteCallback finishedCallback, boolean scaleUp);
    void overridePendingAppTransitionInPlace(String packageName, int anim);

    /**
     * Like overridePendingAppTransitionMultiThumb, but uses a future to supply the specs. This is
     * used for recents, where generating the thumbnails of the specs takes a non-trivial amount of
     * time, so we want to move that off the critical path for starting the new activity.
     */
    void overridePendingAppTransitionMultiThumbFuture(
            IAppTransitionAnimationSpecsFuture specsFuture, IRemoteCallback startedCallback,
            boolean scaleUp);
    void executeAppTransition();

    /** Used by system ui to report that recents has shown itself. */
    void endProlongedAnimations();

    // Re-evaluate the current orientation from the caller's state.
    // If there is a change, the new Configuration is returned and the
    // caller must call setNewConfiguration() sometime later.
    Configuration updateOrientationFromAppTokens(in Configuration currentConfig,
            IBinder freezeThisOneIfNeeded, int displayId);
    // Notify window manager of the new display override configuration. Returns an array of stack
    // ids that were affected by the update, ActivityManager should resize these stacks.
    int[] setNewDisplayOverrideConfiguration(in Configuration overrideConfig, int displayId);

    void startFreezingScreen(int exitAnim, int enterAnim);
    void stopFreezingScreen();

    // these require DISABLE_KEYGUARD permission
    void disableKeyguard(IBinder token, String tag);
    void reenableKeyguard(IBinder token);
    void exitKeyguardSecurely(IOnKeyguardExitResult callback);
    boolean isKeyguardLocked();
    boolean isKeyguardSecure();
    boolean inKeyguardRestrictedInputMode();
    void dismissKeyguard(IKeyguardDismissCallback callback);

    // Requires INTERACT_ACROSS_USERS_FULL permission
    void setSwitchingUser(boolean switching);

    void closeSystemDialogs(String reason);

    // These can only be called with the SET_ANIMATON_SCALE permission.
    float getAnimationScale(int which);
    float[] getAnimationScales();
    void setAnimationScale(int which, float scale);
    void setAnimationScales(in float[] scales);

    float getCurrentAnimatorScale();

    // For testing
    void setInTouchMode(boolean showFocus);

    // For StrictMode flashing a red border on violations from the UI
    // thread.  The uid/pid is implicit from the Binder call, and the Window
    // Manager uses that to determine whether or not the red border should
    // actually be shown.  (it will be ignored that pid doesn't have windows
    // on screen)
    void showStrictModeViolation(boolean on);

    // Proxy to set the system property for whether the flashing
    // should be enabled.  The 'enabled' value is null or blank for
    // the system default (differs per build variant) or any valid
    // boolean string as parsed by SystemProperties.getBoolean().
    void setStrictModeVisualIndicatorPreference(String enabled);

    /**
     * Set whether screen capture is disabled for all windows of a specific user
     */
    void setScreenCaptureDisabled(int userId, boolean disabled);

    /**
     * Testing and debugging infrastructure for writing surface events
     * to given FD. See RemoteSurfaceTrace.java or Wm.java for format.
     */
    void enableSurfaceTrace(in ParcelFileDescriptor fd);
    void disableSurfaceTrace();

    // These can only be called with the SET_ORIENTATION permission.
    /**
     * Update the current screen rotation based on the current state of
     * the world.
     * @param alwaysSendConfiguration Flag to force a new configuration to
     * be evaluated.  This can be used when there are other parameters in
     * configuration that are changing.
     * @param forceRelayout If true, the window manager will always do a relayout
     * of its windows even if the rotation hasn't changed.
     */
    void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout);

    /**
     * Retrieve the current orientation of the primary screen.
     * @return Constant as per {@link android.view.Surface.Rotation}.
     *
     * @see android.view.Display#DEFAULT_DISPLAY
     */
    int getDefaultDisplayRotation();

    /**
     * Watch the rotation of the specified screen.  Returns the current rotation,
     * calls back when it changes.
     */
    int watchRotation(IRotationWatcher watcher, int displayId);

    /**
     * Remove a rotation watcher set using watchRotation.
     * @hide
     */
    void removeRotationWatcher(IRotationWatcher watcher);

    /**
     * Determine the preferred edge of the screen to pin the compact options menu against.
     * @return a Gravity value for the options menu panel
     * @hide
     */
    int getPreferredOptionsPanelGravity();

    /**
     * Lock the device orientation to the specified rotation, or to the
     * current rotation if -1.  Sensor input will be ignored until
     * thawRotation() is called.
     * @hide
     */
    void freezeRotation(int rotation);

    /**
     * Release the orientation lock imposed by freezeRotation().
     * @hide
     */
    void thawRotation();

    /**
     * Gets whether the rotation is frozen.
     *
     * @return Whether the rotation is frozen.
     */
    boolean isRotationFrozen();

    /**
     * Screenshot the current wallpaper layer, including the whole screen.
     */
    Bitmap screenshotWallpaper();

    /**
     * Registers a wallpaper visibility listener.
     * @return Current visibility.
     */
    boolean registerWallpaperVisibilityListener(IWallpaperVisibilityListener listener,
        int displayId);

    /**
     * Remove a visibility watcher that was added using registerWallpaperVisibilityListener.
     */
    void unregisterWallpaperVisibilityListener(IWallpaperVisibilityListener listener,
        int displayId);

    /**
     * Used only for assist -- request a screenshot of the current application.
     */
    boolean requestAssistScreenshot(IAssistScreenshotReceiver receiver);

    /**
     * Called by the status bar to notify Views of changes to System UI visiblity.
     */
    oneway void statusBarVisibilityChanged(int visibility);

    /**
     * Send some ActionHandler commands to WindowManager.
     */
    void sendCustomAction(in Intent intent);

    /**
     * Called by System UI to notify of changes to the visibility of Recents.
     */
    oneway void setRecentsVisibility(boolean visible);

    /**
     * Called by System UI to notify of changes to the visibility of PIP.
     */
    oneway void setPipVisibility(boolean visible);

    /**
     * Device has a software navigation bar (separate from the status bar).
     */
    boolean hasNavigationBar();

    /**
<<<<<<< HEAD
     * Simulate a hardware menu key
     */
    boolean hasPermanentMenuKey();
=======
     * Device needs a software navigation bar (because it has no hardware keys).
     */
    boolean needsNavigationBar();
>>>>>>> 1891b064a40582e1dad5c1a9eb0e7ed9c5e20017

    /**
     * Lock the device immediately with the specified options (can be null).
     */
    void lockNow(in Bundle options);

    /**
     * Device is in safe mode.
     */
    boolean isSafeModeEnabled();

    /**
     * Enables the screen if all conditions are met.
     */
    void enableScreenIfNeeded();

    /**
     * Clears the frame statistics for a given window.
     *
     * @param token The window token.
     * @return Whether the frame statistics were cleared.
     */
    boolean clearWindowContentFrameStats(IBinder token);

    /**
     * Gets the content frame statistics for a given window.
     *
     * @param token The window token.
     * @return The frame statistics or null if the window does not exist.
     */
    WindowContentFrameStats getWindowContentFrameStats(IBinder token);

    /**
     * @return the dock side the current docked stack is at; must be one of the
     *         WindowManagerGlobal.DOCKED_* values
     */
    int getDockedStackSide();

    /**
     * Sets whether we are currently in a drag resize operation where we are changing the docked
     * stack size.
     */
    void setDockedStackResizing(boolean resizing);

    /**
     * Sets the region the user can touch the divider. This region will be excluded from the region
     * which is used to cause a focus switch when dispatching touch.
     */
    void setDockedStackDividerTouchRegion(in Rect touchableRegion);

    /**
     * Registers a listener that will be called when the dock divider changes its visibility or when
     * the docked stack gets added/removed.
     */
    void registerDockedStackListener(IDockedStackListener listener);

    /**
     * Registers a listener that will be called when the pinned stack state changes.
     */
    void registerPinnedStackListener(int displayId, IPinnedStackListener listener);

    /**
     * Updates the dim layer used while resizing.
     *
     * @param visible Whether the dim layer should be visible.
     * @param targetStackId The id of the task stack the dim layer should be placed on.
     * @param alpha The translucency of the dim layer, between 0 and 1.
     */
    void setResizeDimLayer(boolean visible, int targetStackId, float alpha);

    /**
     * Requests Keyboard Shortcuts from the displayed window.
     *
     * @param receiver The receiver to deliver the results to.
     */
    void requestAppKeyboardShortcuts(IResultReceiver receiver, int deviceId);

    /**
     * Retrieves the current stable insets from the primary display.
     */
    void getStableInsets(int displayId, out Rect outInsets);

    /**
     * Register shortcut key. Shortcut code is packed as:
     * (MetaState << Integer.SIZE) | KeyCode
     * @hide
     */
    void registerShortcutKey(in long shortcutCode, IShortcutService keySubscriber);

    /**
     * Create an input consumer by name.
     */
    void createInputConsumer(String name, out InputChannel inputChannel);

    /**
     * Destroy an input consumer by name.  This method will also dispose the input channels
     * associated with that InputConsumer.
     */
    boolean destroyInputConsumer(String name);

    /**
     * Return the touch region for the current IME window, or an empty region if there is none.
     */
    Region getCurrentImeTouchRegion();

    /**
     * Call screen record from WindowManager.
     */
    void screenRecordAction(int mode);
}
