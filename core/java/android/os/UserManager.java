/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2016 The CyanogenMod Project
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

package android.os;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.WindowManager.LayoutParams;

import com.android.internal.R;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages users and user details on a multi-user system. There are two major categories of
 * users: fully customizable users with their own login, and managed profiles that share a workspace
 * with a related user.
 * <p>
 * Users are different from accounts, which are managed by
 * {@link AccountManager}. Each user can have their own set of accounts.
 * <p>
 * See {@link DevicePolicyManager#ACTION_PROVISION_MANAGED_PROFILE} for more on managed profiles.
 */
public class UserManager {

    private static String TAG = "UserManager";
    private final IUserManager mService;
    private final Context mContext;

    /**
     * @hide
     * No user restriction.
     */
    @SystemApi
    public static final int RESTRICTION_NOT_SET = 0x0;

    /**
     * @hide
     * User restriction set by system/user.
     */
    @SystemApi
    public static final int RESTRICTION_SOURCE_SYSTEM = 0x1;

    /**
     * @hide
     * User restriction set by a device owner.
     */
    @SystemApi
    public static final int RESTRICTION_SOURCE_DEVICE_OWNER = 0x2;

    /**
     * @hide
     * User restriction set by a profile owner.
     */
    @SystemApi
    public static final int RESTRICTION_SOURCE_PROFILE_OWNER = 0x4;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag=true, value={RESTRICTION_NOT_SET, RESTRICTION_SOURCE_SYSTEM,
            RESTRICTION_SOURCE_DEVICE_OWNER, RESTRICTION_SOURCE_PROFILE_OWNER})
    @SystemApi
    public @interface UserRestrictionSource {}

    /**
     * Specifies if a user is disallowed from adding and removing accounts, unless they are
     * {@link android.accounts.AccountManager#addAccountExplicitly programmatically} added by
     * Authenticator.
     * The default value is <code>false</code>.
     *
     * <p>From {@link android.os.Build.VERSION_CODES#N} a profile or device owner app can still
     * use {@link android.accounts.AccountManager} APIs to add or remove accounts when account
     * management is disallowed.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_MODIFY_ACCOUNTS = "no_modify_accounts";

    /**
     * Specifies if a user is disallowed from changing Wi-Fi
     * access points. The default value is <code>false</code>.
     * <p>This restriction has no effect in a managed profile.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_WIFI = "no_config_wifi";

    /**
     * Specifies if a user is disallowed from installing applications.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_INSTALL_APPS = "no_install_apps";

    /**
     * Specifies if a user is disallowed from uninstalling applications.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_UNINSTALL_APPS = "no_uninstall_apps";

    /**
     * Specifies if a user is disallowed from turning on location sharing.
     * The default value is <code>false</code>.
     * <p>In a managed profile, location sharing always reflects the primary user's setting, but
     * can be overridden and forced off by setting this restriction to true in the managed profile.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_SHARE_LOCATION = "no_share_location";

    /**
     * Specifies if a user is disallowed from enabling the
     * "Unknown Sources" setting, that allows installation of apps from unknown sources.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES = "no_install_unknown_sources";

    /**
     * Specifies if a user is disallowed from configuring bluetooth.
     * This does <em>not</em> restrict the user from turning bluetooth on or off.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect in a managed profile.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_BLUETOOTH = "no_config_bluetooth";

    /**
     * Specifies if a user is disallowed from transferring files over
     * USB. This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_USB_FILE_TRANSFER = "no_usb_file_transfer";

    /**
     * Specifies if a user is disallowed from configuring user
     * credentials. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_CREDENTIALS = "no_config_credentials";

    /**
     * When set on the primary user this specifies if the user can remove other users.
     * When set on a secondary user, this specifies if the user can remove itself.
     * This restriction has no effect on managed profiles.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_REMOVE_USER = "no_remove_user";

    /**
     * Specifies if a user is disallowed from enabling or
     * accessing debugging features. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_DEBUGGING_FEATURES = "no_debugging_features";

    /**
     * Specifies if a user is disallowed from configuring VPN.
     * The default value is <code>false</code>.
     * This restriction has an effect in a managed profile only from
     * {@link android.os.Build.VERSION_CODES#M}
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_VPN = "no_config_vpn";

    /**
     * Specifies if a user is disallowed from configuring Tethering
     * & portable hotspots. This can only be set by device owners and profile owners on the
     * primary user. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_TETHERING = "no_config_tethering";

    /**
     * Specifies if a user is disallowed from resetting network settings
     * from Settings. This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect on secondary users and managed profiles since only the
     * primary user can reset the network settings of the device.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_NETWORK_RESET = "no_network_reset";

    /**
     * Specifies if a user is disallowed from factory resetting
     * from Settings. This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect on secondary users and managed profiles since only the
     * primary user can factory reset the device.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_FACTORY_RESET = "no_factory_reset";

    /**
     * Specifies if a user is disallowed from adding new users and
     * profiles. This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect on secondary users and managed profiles since only the
     * primary user can add other users.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_ADD_USER = "no_add_user";

    /**
     * Specifies if a user is disallowed from disabling application
     * verification. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String ENSURE_VERIFY_APPS = "ensure_verify_apps";

    /**
     * Specifies if a user is disallowed from configuring cell
     * broadcasts. This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect on secondary users and managed profiles since only the
     * primary user can configure cell broadcasts.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_CELL_BROADCASTS = "no_config_cell_broadcasts";

    /**
     * Specifies if a user is disallowed from configuring mobile
     * networks. This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect on secondary users and managed profiles since only the
     * primary user can configure mobile networks.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CONFIG_MOBILE_NETWORKS = "no_config_mobile_networks";

    /**
     * Specifies if a user is disallowed from modifying
     * applications in Settings or launchers. The following actions will not be allowed when this
     * restriction is enabled:
     * <li>uninstalling apps</li>
     * <li>disabling apps</li>
     * <li>clearing app caches</li>
     * <li>clearing app data</li>
     * <li>force stopping apps</li>
     * <li>clearing app defaults</li>
     * <p>
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_APPS_CONTROL = "no_control_apps";

    /**
     * Specifies if a user is disallowed from mounting
     * physical external media. This can only be set by device owners and profile owners on the
     * primary user. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_MOUNT_PHYSICAL_MEDIA = "no_physical_media";

    /**
     * Specifies if a user is disallowed from adjusting microphone
     * volume. If set, the microphone will be muted. This can only be set by device owners
     * and profile owners on the primary user. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_UNMUTE_MICROPHONE = "no_unmute_microphone";

    /**
     * Specifies if a user is disallowed from adjusting the master
     * volume. If set, the master volume will be muted. This can only be set by device owners
     * and profile owners on the primary user. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_ADJUST_VOLUME = "no_adjust_volume";

    /**
     * Specifies that the user is not allowed to make outgoing
     * phone calls. Emergency calls are still permitted.
     * The default value is <code>false</code>.
     * <p>This restriction has no effect on managed profiles.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_OUTGOING_CALLS = "no_outgoing_calls";

    /**
     * Specifies that the user is not allowed to send or receive
     * SMS messages. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_SMS = "no_sms";

    /**
     * Specifies if the user is not allowed to have fun. In some cases, the
     * device owner may wish to prevent the user from experiencing amusement or
     * joy while using the device. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_FUN = "no_fun";

    /**
     * Specifies that windows besides app windows should not be
     * created. This will block the creation of the following types of windows.
     * <li>{@link LayoutParams#TYPE_TOAST}</li>
     * <li>{@link LayoutParams#TYPE_PHONE}</li>
     * <li>{@link LayoutParams#TYPE_PRIORITY_PHONE}</li>
     * <li>{@link LayoutParams#TYPE_SYSTEM_ALERT}</li>
     * <li>{@link LayoutParams#TYPE_SYSTEM_ERROR}</li>
     * <li>{@link LayoutParams#TYPE_SYSTEM_OVERLAY}</li>
     *
     * <p>This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CREATE_WINDOWS = "no_create_windows";

    /**
     * Specifies if what is copied in the clipboard of this profile can
     * be pasted in related profiles. Does not restrict if the clipboard of related profiles can be
     * pasted in this profile.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_CROSS_PROFILE_COPY_PASTE = "no_cross_profile_copy_paste";

    /**
     * Specifies if the user is not allowed to use NFC to beam out data from apps.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_OUTGOING_BEAM = "no_outgoing_beam";

    /**
     * Hidden user restriction to disallow access to wallpaper manager APIs. This restriction
     * generally means that wallpapers are not supported for the particular user. This user
     * restriction is always set for managed profiles, because such profiles don't have wallpapers.
     * @hide
     * @see #DISALLOW_SET_WALLPAPER
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_WALLPAPER = "no_wallpaper";

    /**
     * User restriction to disallow setting a wallpaper. Profile owner and device owner
     * are able to set wallpaper regardless of this restriction.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_SET_WALLPAPER = "no_set_wallpaper";

    /**
     * Specifies if the user is not allowed to reboot the device into safe boot mode.
     * This can only be set by device owners and profile owners on the primary user.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_SAFE_BOOT = "no_safe_boot";

    /**
     * Specifies if a user is not allowed to record audio. This restriction is always enabled for
     * background users. The default value is <code>false</code>.
     *
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     * @hide
     */
    public static final String DISALLOW_RECORD_AUDIO = "no_record_audio";

    /**
     * Specifies if a user is not allowed to run in the background and should be stopped during
     * user switch. The default value is <code>false</code>.
     *
     * <p>This restriction can be set by device owners and profile owners.
     *
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     * @hide
     */
    public static final String DISALLOW_RUN_IN_BACKGROUND = "no_run_in_background";

    /**
     * Specifies if a user is not allowed to use the camera.
     *
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     * @hide
     */
    public static final String DISALLOW_CAMERA = "no_camera";

    /**
     * Specifies if a user is not allowed to unmute the device's master volume.
     *
     * @see DevicePolicyManager#setMasterVolumeMuted(ComponentName, boolean)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     * @hide
     */
    public static final String DISALLLOW_UNMUTE_DEVICE = "disallow_unmute_device";

    /**
     * Specifies if a user is not allowed to use cellular data when roaming. This can only be set by
     * device owners. The default value is <code>false</code>.
     *
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_DATA_ROAMING = "no_data_roaming";

    /**
     * Specifies if a user is not allowed to change their icon. Device owner and profile owner
     * can set this restriction. When it is set by device owner, only the target user will be
     * affected. The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String DISALLOW_SET_USER_ICON = "no_set_user_icon";

    /**
     * Specifies if a user is not allowed to enable the oem unlock setting. The default value is
     * <code>false</code>. Setting this restriction has no effect if the bootloader is already
     * unlocked.
     *
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     * @hide
     */
    public static final String DISALLOW_OEM_UNLOCK = "no_oem_unlock";

    /**
     * Allows apps in the parent profile to handle web links from the managed profile.
     *
     * This user restriction has an effect only in a managed profile.
     * If set:
     * Intent filters of activities in the parent profile with action
     * {@link android.content.Intent#ACTION_VIEW},
     * category {@link android.content.Intent#CATEGORY_BROWSABLE}, scheme http or https, and which
     * define a host can handle intents from the managed profile.
     * The default value is <code>false</code>.
     *
     * <p>Key for user restrictions.
     * <p>Type: Boolean
     * @see DevicePolicyManager#addUserRestriction(ComponentName, String)
     * @see DevicePolicyManager#clearUserRestriction(ComponentName, String)
     * @see #getUserRestrictions()
     */
    public static final String ALLOW_PARENT_PROFILE_APP_LINKING
            = "allow_parent_profile_app_linking";

    /**
     * Application restriction key that is used to indicate the pending arrival
     * of real restrictions for the app.
     *
     * <p>
     * Applications that support restrictions should check for the presence of this key.
     * A <code>true</code> value indicates that restrictions may be applied in the near
     * future but are not available yet. It is the responsibility of any
     * management application that sets this flag to update it when the final
     * restrictions are enforced.
     *
     * <p>Key for application restrictions.
     * <p>Type: Boolean
     * @see android.app.admin.DevicePolicyManager#setApplicationRestrictions(
     *      android.content.ComponentName, String, Bundle)
     * @see android.app.admin.DevicePolicyManager#getApplicationRestrictions(
     *      android.content.ComponentName, String)
     */
    public static final String KEY_RESTRICTIONS_PENDING = "restrictions_pending";

    private static final String ACTION_CREATE_USER = "android.os.action.CREATE_USER";

    /**
     * Extra containing a name for the user being created. Optional parameter passed to
     * ACTION_CREATE_USER activity.
     * @hide
     */
    public static final String EXTRA_USER_NAME = "android.os.extra.USER_NAME";

    /**
     * Extra containing account name for the user being created. Optional parameter passed to
     * ACTION_CREATE_USER activity.
     * @hide
     */
    public static final String EXTRA_USER_ACCOUNT_NAME = "android.os.extra.USER_ACCOUNT_NAME";

    /**
     * Extra containing account type for the user being created. Optional parameter passed to
     * ACTION_CREATE_USER activity.
     * @hide
     */
    public static final String EXTRA_USER_ACCOUNT_TYPE = "android.os.extra.USER_ACCOUNT_TYPE";

    /**
     * Extra containing account-specific data for the user being created. Optional parameter passed
     * to ACTION_CREATE_USER activity.
     * @hide
     */
    public static final String EXTRA_USER_ACCOUNT_OPTIONS
            = "android.os.extra.USER_ACCOUNT_OPTIONS";

    /**
     * Specifies if the user is not allowed to use SU commands.
     * The default value is <code>false</code>.
     *
     * <p/>Key for user restrictions.
     * <p/>Type: Boolean
     * @see #setUserRestrictions(Bundle)
     * @see #getUserRestrictions()
     * @hide
     */
    public static final String DISALLOW_SU = "no_su";

    /** @hide */
    public static final int PIN_VERIFICATION_FAILED_INCORRECT = -3;
    /** @hide */
    public static final int PIN_VERIFICATION_FAILED_NOT_SET = -2;
    /** @hide */
    public static final int PIN_VERIFICATION_SUCCESS = -1;

    /**
     * Error result indicating that this user is not allowed to add other users on this device.
     * This is a result code returned from the activity created by the intent
     * {@link #createUserCreationIntent(String, String, String, PersistableBundle)}.
     */
    public static final int USER_CREATION_FAILED_NOT_PERMITTED = Activity.RESULT_FIRST_USER;

    /**
     * Error result indicating that no more users can be created on this device.
     * This is a result code returned from the activity created by the intent
     * {@link #createUserCreationIntent(String, String, String, PersistableBundle)}.
     */
    public static final int USER_CREATION_FAILED_NO_MORE_USERS = Activity.RESULT_FIRST_USER + 1;

    /** @hide */
    public static UserManager get(Context context) {
        return (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    /** @hide */
    public UserManager(Context context, IUserManager service) {
        mService = service;
        mContext = context;
    }

    /**
     * Returns whether this device supports multiple users with their own login and customizable
     * space.
     * @return whether the device supports multiple users.
     */
    public static boolean supportsMultipleUsers() {
        return getMaxSupportedUsers() > 1
                && SystemProperties.getBoolean("fw.show_multiuserui",
                Resources.getSystem().getBoolean(R.bool.config_enableMultiUserUI));
    }

    /**
     * @hide
     * @return Whether the device is running with split system user. It means the system user and
     * primary user are two separate users. Previously system user and primary user are combined as
     * a single owner user.  see @link {android.os.UserHandle#USER_OWNER}
     */
    public static boolean isSplitSystemUser() {
        return SystemProperties.getBoolean("ro.fw.system_user_split", false);
    }

    /**
     * Returns whether switching users is currently allowed.
     * <p>For instance switching users is not allowed if the current user is in a phone call,
     * or system user hasn't been unlocked yet
     * @hide
     */
    public boolean canSwitchUsers() {
        boolean allowUserSwitchingWhenSystemUserLocked = Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.ALLOW_USER_SWITCHING_WHEN_SYSTEM_USER_LOCKED, 0) != 0;
        boolean isSystemUserUnlocked = isUserUnlocked(UserHandle.SYSTEM);
        boolean inCall = TelephonyManager.getDefault().getCallState()
                != TelephonyManager.CALL_STATE_IDLE;
        return (allowUserSwitchingWhenSystemUserLocked || isSystemUserUnlocked) && !inCall;
    }

    /**
     * Returns the user handle for the user that this process is running under.
     *
     * @return the user handle of this process.
     * @hide
     */
    public @UserIdInt int getUserHandle() {
        return UserHandle.myUserId();
    }

    /**
     * Returns the user name of the user making this call.  This call is only
     * available to applications on the system image; it requires the
     * MANAGE_USERS permission.
     * @return the user name
     */
    public String getUserName() {
        try {
            return mService.getUserInfo(getUserHandle()).name;
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Used to determine whether the user making this call is subject to
     * teleportations.
     *
     * <p>As of {@link android.os.Build.VERSION_CODES#LOLLIPOP}, this method can
     * now automatically identify goats using advanced goat recognition technology.</p>
     *
     * @return Returns true if the user making this call is a goat.
     */
    public boolean isUserAGoat() {
        return mContext.getPackageManager()
                .isPackageAvailable("com.coffeestainstudios.goatsimulator");
    }

    /**
     * Used to check if this process is running under the primary user. The primary user
     * is the first human user on a device.
     *
     * @return whether this process is running under the primary user.
     * @hide
     */
    public boolean isPrimaryUser() {
        UserInfo user = getUserInfo(UserHandle.myUserId());
        return user != null && user.isPrimary();
    }

    /**
     * Used to check if this process is running under the system user. The system user
     * is the initial user that is implicitly created on first boot and hosts most of the
     * system services.
     *
     * @return whether this process is running under the system user.
     */
    public boolean isSystemUser() {
        return UserHandle.myUserId() == UserHandle.USER_SYSTEM;
    }

    /**
     * @hide
     * Returns whether the caller is running as an admin user. There can be more than one admin
     * user.
     */
    public boolean isAdminUser() {
        return isUserAdmin(UserHandle.myUserId());
    }

    /**
     * @hide
     * Returns whether the provided user is an admin user. There can be more than one admin
     * user.
     */
    public boolean isUserAdmin(@UserIdInt int userId) {
        UserInfo user = getUserInfo(userId);
        return user != null && user.isAdmin();
    }

    /**
     * Used to check if the user making this call is linked to another user. Linked users may have
     * a reduced number of available apps, app restrictions and account restrictions.
     * @return whether the user making this call is a linked user
     * @hide
     */
    public boolean isLinkedUser() {
        try {
            return mService.isRestricted();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Checks if specified user can have restricted profile.
     * @hide
     */
    public boolean canHaveRestrictedProfile(@UserIdInt int userId) {
        try {
            return mService.canHaveRestrictedProfile(userId);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Checks if the calling app is running as a guest user.
     * @return whether the caller is a guest user.
     * @hide
     */
    public boolean isGuestUser() {
        UserInfo user = getUserInfo(UserHandle.myUserId());
        return user != null && user.isGuest();
    }

    /**
     * Checks if the calling app is running in a demo user. When running in a demo user,
     * apps can be more helpful to the user, or explain their features in more detail.
     *
     * @return whether the caller is a demo user.
     */
    public boolean isDemoUser() {
        try {
            return mService.isDemoUser(UserHandle.myUserId());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Checks if the calling app is running in a managed profile.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @return whether the caller is in a managed profile.
     * @hide
     */
    @SystemApi
    public boolean isManagedProfile() {
        try {
            return mService.isManagedProfile(UserHandle.myUserId());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Checks if the specified user is a managed profile.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission, otherwise the caller
     * must be in the same profile group of specified user.
     *
     * @return whether the specified user is a managed profile.
     * @hide
     */
    @SystemApi
    public boolean isManagedProfile(@UserIdInt int userId) {
        try {
            return mService.isManagedProfile(userId);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Checks if the calling app is running as an ephemeral user.
     *
     * @return whether the caller is an ephemeral user.
     * @hide
     */
    public boolean isEphemeralUser() {
        return isUserEphemeral(UserHandle.myUserId());
    }

    /**
     * Returns whether the specified user is ephemeral.
     * @hide
     */
    public boolean isUserEphemeral(@UserIdInt int userId) {
        final UserInfo user = getUserInfo(userId);
        return user != null && user.isEphemeral();
    }

    /**
     * Return whether the given user is actively running.  This means that
     * the user is in the "started" state, not "stopped" -- it is currently
     * allowed to run code through scheduled alarms, receiving broadcasts,
     * etc.  A started user may be either the current foreground user or a
     * background user; the result here does not distinguish between the two.
     * @param user The user to retrieve the running state for.
     */
    public boolean isUserRunning(UserHandle user) {
        return isUserRunning(user.getIdentifier());
    }

    /** {@hide} */
    public boolean isUserRunning(int userId) {
        try {
            return mService.isUserRunning(userId);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Return whether the given user is actively running <em>or</em> stopping.
     * This is like {@link #isUserRunning(UserHandle)}, but will also return
     * true if the user had been running but is in the process of being stopped
     * (but is not yet fully stopped, and still running some code).
     * @param user The user to retrieve the running state for.
     */
    public boolean isUserRunningOrStopping(UserHandle user) {
        try {
            // TODO: reconcile stopped vs stopping?
            return ActivityManagerNative.getDefault().isUserRunning(
                    user.getIdentifier(), ActivityManager.FLAG_OR_STOPPED);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /** @removed */
    @Deprecated
    public boolean isUserRunningAndLocked() {
        return isUserRunningAndLocked(Process.myUserHandle());
    }

    /** @removed */
    @Deprecated
    public boolean isUserRunningAndLocked(UserHandle user) {
        try {
            return ActivityManagerNative.getDefault().isUserRunning(
                    user.getIdentifier(), ActivityManager.FLAG_AND_LOCKED);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /** @removed */
    @Deprecated
    public boolean isUserRunningAndUnlocked() {
        return isUserRunningAndUnlocked(Process.myUserHandle());
    }

    /** @removed */
    @Deprecated
    public boolean isUserRunningAndUnlocked(UserHandle user) {
        try {
            return ActivityManagerNative.getDefault().isUserRunning(
                    user.getIdentifier(), ActivityManager.FLAG_AND_UNLOCKED);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Return whether the calling user is running in an "unlocked" state.
     * <p>
     * On devices with direct boot, a user is unlocked only after they've
     * entered their credentials (such as a lock pattern or PIN). On devices
     * without direct boot, a user is unlocked as soon as it starts.
     * <p>
     * When a user is locked, only device-protected data storage is available.
     * When a user is unlocked, both device-protected and credential-protected
     * private app data storage is available.
     *
     * @see Intent#ACTION_USER_UNLOCKED
     * @see Context#createDeviceProtectedStorageContext()
     */
    public boolean isUserUnlocked() {
        return isUserUnlocked(Process.myUserHandle());
    }

    /**
     * Return whether the given user is running in an "unlocked" state.
     * <p>
     * On devices with direct boot, a user is unlocked only after they've
     * entered their credentials (such as a lock pattern or PIN). On devices
     * without direct boot, a user is unlocked as soon as it starts.
     * <p>
     * When a user is locked, only device-protected data storage is available.
     * When a user is unlocked, both device-protected and credential-protected
     * private app data storage is available.
     *
     * @param user to retrieve the unlocked state for.
     * @see Intent#ACTION_USER_UNLOCKED
     * @see Context#createDeviceProtectedStorageContext()
     */
    public boolean isUserUnlocked(UserHandle user) {
        return isUserUnlocked(user.getIdentifier());
    }

    /** {@hide} */
    public boolean isUserUnlocked(@UserIdInt int userId) {
        try {
            return mService.isUserUnlocked(userId);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /** {@hide} */
    public boolean isUserUnlockingOrUnlocked(UserHandle user) {
        return isUserUnlockingOrUnlocked(user.getIdentifier());
    }

    /** {@hide} */
    public boolean isUserUnlockingOrUnlocked(@UserIdInt int userId) {
        try {
            return mService.isUserUnlockingOrUnlocked(userId);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the UserInfo object describing a specific user.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @param userHandle the user handle of the user whose information is being requested.
     * @return the UserInfo object for a specific user.
     * @hide
     */
    public UserInfo getUserInfo(@UserIdInt int userHandle) {
        try {
            return mService.getUserInfo(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     *
     * Returns who set a user restriction on a user.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @param restrictionKey the string key representing the restriction
     * @param userHandle the UserHandle of the user for whom to retrieve the restrictions.
     * @return The source of user restriction. Any combination of {@link #RESTRICTION_NOT_SET},
     *         {@link #RESTRICTION_SOURCE_SYSTEM}, {@link #RESTRICTION_SOURCE_DEVICE_OWNER}
     *         and {@link #RESTRICTION_SOURCE_PROFILE_OWNER}
     */
    @SystemApi
    @UserRestrictionSource
    public int getUserRestrictionSource(String restrictionKey, UserHandle userHandle) {
        try {
            return mService.getUserRestrictionSource(restrictionKey, userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the user-wide restrictions imposed on this user.
     * @return a Bundle containing all the restrictions.
     */
    public Bundle getUserRestrictions() {
        return getUserRestrictions(Process.myUserHandle());
    }

    /**
     * Returns the user-wide restrictions imposed on the user specified by <code>userHandle</code>.
     * @param userHandle the UserHandle of the user for whom to retrieve the restrictions.
     * @return a Bundle containing all the restrictions.
     */
    public Bundle getUserRestrictions(UserHandle userHandle) {
        try {
            return mService.getUserRestrictions(userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

     /**
     * @hide
     * Returns whether the given user has been disallowed from performing certain actions
     * or setting certain settings through UserManager. This method disregards restrictions
     * set by device policy.
     * @param restrictionKey the string key representing the restriction
     * @param userHandle the UserHandle of the user for whom to retrieve the restrictions.
     */
    public boolean hasBaseUserRestriction(String restrictionKey, UserHandle userHandle) {
        try {
            return mService.hasBaseUserRestriction(restrictionKey, userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * This will no longer work.  Device owners and profile owners should use
     * {@link DevicePolicyManager#addUserRestriction(ComponentName, String)} instead.
     */
    // System apps should use UserManager.setUserRestriction() instead.
    @Deprecated
    public void setUserRestrictions(Bundle restrictions) {
        throw new UnsupportedOperationException("This method is no longer supported");
    }

    /**
     * This will no longer work.  Device owners and profile owners should use
     * {@link DevicePolicyManager#addUserRestriction(ComponentName, String)} instead.
     */
    // System apps should use UserManager.setUserRestriction() instead.
    @Deprecated
    public void setUserRestrictions(Bundle restrictions, UserHandle userHandle) {
        throw new UnsupportedOperationException("This method is no longer supported");
    }

    /**
     * Sets the value of a specific restriction.
     * Requires the MANAGE_USERS permission.
     * @param key the key of the restriction
     * @param value the value for the restriction
     * @deprecated use {@link android.app.admin.DevicePolicyManager#addUserRestriction(
     * android.content.ComponentName, String)} or
     * {@link android.app.admin.DevicePolicyManager#clearUserRestriction(
     * android.content.ComponentName, String)} instead.
     */
    @Deprecated
    public void setUserRestriction(String key, boolean value) {
        setUserRestriction(key, value, Process.myUserHandle());
    }

    /**
     * @hide
     * Sets the value of a specific restriction on a specific user.
     * Requires the MANAGE_USERS permission.
     * @param key the key of the restriction
     * @param value the value for the restriction
     * @param userHandle the user whose restriction is to be changed.
     * @deprecated use {@link android.app.admin.DevicePolicyManager#addUserRestriction(
     * android.content.ComponentName, String)} or
     * {@link android.app.admin.DevicePolicyManager#clearUserRestriction(
     * android.content.ComponentName, String)} instead.
     */
    @Deprecated
    public void setUserRestriction(String key, boolean value, UserHandle userHandle) {
        try {
            mService.setUserRestriction(key, value, userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether the current user has been disallowed from performing certain actions
     * or setting certain settings.
     *
     * @param restrictionKey The string key representing the restriction.
     * @return {@code true} if the current user has the given restriction, {@code false} otherwise.
     */
    public boolean hasUserRestriction(String restrictionKey) {
        return hasUserRestriction(restrictionKey, Process.myUserHandle());
    }

    /**
     * @hide
     * Returns whether the given user has been disallowed from performing certain actions
     * or setting certain settings.
     * @param restrictionKey the string key representing the restriction
     * @param userHandle the UserHandle of the user for whom to retrieve the restrictions.
     */
    public boolean hasUserRestriction(String restrictionKey, UserHandle userHandle) {
        try {
            return mService.hasUserRestriction(restrictionKey,
                    userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Return the serial number for a user.  This is a device-unique
     * number assigned to that user; if the user is deleted and then a new
     * user created, the new users will not be given the same serial number.
     * @param user The user whose serial number is to be retrieved.
     * @return The serial number of the given user; returns -1 if the
     * given UserHandle does not exist.
     * @see #getUserForSerialNumber(long)
     */
    public long getSerialNumberForUser(UserHandle user) {
        return getUserSerialNumber(user.getIdentifier());
    }

    /**
     * Return the user associated with a serial number previously
     * returned by {@link #getSerialNumberForUser(UserHandle)}.
     * @param serialNumber The serial number of the user that is being
     * retrieved.
     * @return Return the user associated with the serial number, or null
     * if there is not one.
     * @see #getSerialNumberForUser(UserHandle)
     */
    public UserHandle getUserForSerialNumber(long serialNumber) {
        int ident = getUserHandle((int) serialNumber);
        return ident >= 0 ? new UserHandle(ident) : null;
    }

    /**
     * Creates a user with the specified name and options. For non-admin users, default user
     * restrictions are going to be applied.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @param name the user's name
     * @param flags flags that identify the type of user and other properties.
     * @see UserInfo
     *
     * @return the UserInfo object for the created user, or null if the user could not be created.
     * @hide
     */
    public UserInfo createUser(String name, int flags) {
        UserInfo user = null;
        try {
            user = mService.createUser(name, flags);
            // TODO: Keep this in sync with
            // UserManagerService.LocalService.createUserEvenWhenDisallowed
            if (user != null && !user.isAdmin()) {
                mService.setUserRestriction(DISALLOW_SMS, true, user.id);
                mService.setUserRestriction(DISALLOW_OUTGOING_CALLS, true, user.id);
            }
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
        return user;
    }

    /**
     * Creates a guest user and configures it.
     * @param context an application context
     * @param name the name to set for the user
     * @hide
     */
    public UserInfo createGuest(Context context, String name) {
        UserInfo guest = null;
        try {
            guest = mService.createUser(name, UserInfo.FLAG_GUEST);
            if (guest != null) {
                Settings.Secure.putStringForUser(context.getContentResolver(),
                        Settings.Secure.SKIP_FIRST_USE_HINTS, "1", guest.id);
            }
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
        return guest;
    }

    /**
     * Creates a user with the specified name and options as a profile of another user.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @param name the user's name
     * @param flags flags that identify the type of user and other properties.
     * @see UserInfo
     * @param userHandle new user will be a profile of this use.
     *
     * @return the UserInfo object for the created user, or null if the user could not be created.
     * @hide
     */
    public UserInfo createProfileForUser(String name, int flags, @UserIdInt int userHandle) {
        try {
            return mService.createProfileForUser(name, flags, userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Creates a restricted profile with the specified name. This method also sets necessary
     * restrictions and adds shared accounts.
     *
     * @param name profile's name
     * @return UserInfo object for the created user, or null if the user could not be created.
     * @hide
     */
    public UserInfo createRestrictedProfile(String name) {
        try {
            UserHandle parentUserHandle = Process.myUserHandle();
            UserInfo user = mService.createRestrictedProfile(name,
                    parentUserHandle.getIdentifier());
            if (user != null) {
                AccountManager.get(mContext).addSharedAccountsFromParentUser(parentUserHandle,
                        UserHandle.of(user.id));
            }
            return user;
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns an intent to create a user for the provided name and account name. The name
     * and account name will be used when the setup process for the new user is started.
     * <p>
     * The intent should be launched using startActivityForResult and the return result will
     * indicate if the user consented to adding a new user and if the operation succeeded. Any
     * errors in creating the user will be returned in the result code. If the user cancels the
     * request, the return result will be {@link Activity#RESULT_CANCELED}. On success, the
     * result code will be {@link Activity#RESULT_OK}.
     * <p>
     * Use {@link #supportsMultipleUsers()} to first check if the device supports this operation
     * at all.
     * <p>
     * The new user is created but not initialized. After switching into the user for the first
     * time, the preferred user name and account information are used by the setup process for that
     * user.
     *
     * @param userName Optional name to assign to the user.
     * @param accountName Optional account name that will be used by the setup wizard to initialize
     *                    the user.
     * @param accountType Optional account type for the account to be created. This is required
     *                    if the account name is specified.
     * @param accountOptions Optional bundle of data to be passed in during account creation in the
     *                       new user via {@link AccountManager#addAccount(String, String, String[],
     *                       Bundle, android.app.Activity, android.accounts.AccountManagerCallback,
     *                       Handler)}.
     * @return An Intent that can be launched from an Activity.
     * @see #USER_CREATION_FAILED_NOT_PERMITTED
     * @see #USER_CREATION_FAILED_NO_MORE_USERS
     * @see #supportsMultipleUsers
     */
    public static Intent createUserCreationIntent(@Nullable String userName,
            @Nullable String accountName,
            @Nullable String accountType, @Nullable PersistableBundle accountOptions) {
        Intent intent = new Intent(ACTION_CREATE_USER);
        if (userName != null) {
            intent.putExtra(EXTRA_USER_NAME, userName);
        }
        if (accountName != null && accountType == null) {
            throw new IllegalArgumentException("accountType must be specified if accountName is "
                    + "specified");
        }
        if (accountName != null) {
            intent.putExtra(EXTRA_USER_ACCOUNT_NAME, accountName);
        }
        if (accountType != null) {
            intent.putExtra(EXTRA_USER_ACCOUNT_TYPE, accountType);
        }
        if (accountOptions != null) {
            intent.putExtra(EXTRA_USER_ACCOUNT_OPTIONS, accountOptions);
        }
        return intent;
    }

    /**
     * @hide
     *
     * Returns the preferred account name for user creation. Requires MANAGE_USERS permission.
     */
    @SystemApi
    public String getSeedAccountName() {
        try {
            return mService.getSeedAccountName();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     *
     * Returns the preferred account type for user creation. Requires MANAGE_USERS permission.
     */
    @SystemApi
    public String getSeedAccountType() {
        try {
            return mService.getSeedAccountType();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     *
     * Returns the preferred account's options bundle for user creation. Requires MANAGE_USERS
     * permission.
     * @return Any options set by the requestor that created the user.
     */
    @SystemApi
    public PersistableBundle getSeedAccountOptions() {
        try {
            return mService.getSeedAccountOptions();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     *
     * Called by a system activity to set the seed account information of a user created
     * through the user creation intent.
     * @param userId
     * @param accountName
     * @param accountType
     * @param accountOptions
     * @see #createUserCreationIntent(String, String, String, PersistableBundle)
     */
    public void setSeedAccountData(int userId, String accountName, String accountType,
            PersistableBundle accountOptions) {
        try {
            mService.setSeedAccountData(userId, accountName, accountType, accountOptions,
                    /* persist= */ true);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     * Clears the seed information used to create this user. Requires MANAGE_USERS permission.
     */
    @SystemApi
    public void clearSeedAccountData() {
        try {
            mService.clearSeedAccountData();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     * Marks the guest user for deletion to allow a new guest to be created before deleting
     * the current user who is a guest.
     * @param userHandle
     * @return
     */
    public boolean markGuestForDeletion(@UserIdInt int userHandle) {
        try {
            return mService.markGuestForDeletion(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the user as enabled, if such an user exists.
     *
     * <p>Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * <p>Note that the default is true, it's only that managed profiles might not be enabled.
     * Also ephemeral users can be disabled to indicate that their removal is in progress and they
     * shouldn't be re-entered. Therefore ephemeral users should not be re-enabled once disabled.
     *
     * @param userHandle the id of the profile to enable
     * @hide
     */
    public void setUserEnabled(@UserIdInt int userHandle) {
        try {
            mService.setUserEnabled(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Return the number of users currently created on the device.
     */
    public int getUserCount() {
        List<UserInfo> users = getUsers();
        return users != null ? users.size() : 1;
    }

    /**
     * Returns information for all users on this device, including ones marked for deletion.
     * To retrieve only users that are alive, use {@link #getUsers(boolean)}.
     * <p>
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @return the list of users that exist on the device.
     * @hide
     */
    public List<UserInfo> getUsers() {
        try {
            return mService.getUsers(false);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns serial numbers of all users on this device.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @param excludeDying specify if the list should exclude users being removed.
     * @return the list of serial numbers of users that exist on the device.
     * @hide
     */
    @SystemApi
    public long[] getSerialNumbersOfUsers(boolean excludeDying) {
        try {
            List<UserInfo> users = mService.getUsers(excludeDying);
            long[] result = new long[users.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = users.get(i).serialNumber;
            }
            return result;
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @return the user's account name, null if not found.
     * @hide
     */
    @RequiresPermission( allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.MANAGE_USERS
    })
    public @Nullable String getUserAccount(@UserIdInt int userHandle) {
        try {
            return mService.getUserAccount(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Set account name for the given user.
     * @hide
     */
    @RequiresPermission( allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.MANAGE_USERS
    })
    public void setUserAccount(@UserIdInt int userHandle, @Nullable String accountName) {
        try {
            mService.setUserAccount(userHandle, accountName);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns information for Primary user.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @return the Primary user, null if not found.
     * @hide
     */
    public @Nullable UserInfo getPrimaryUser() {
        try {
            return mService.getPrimaryUser();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Checks whether it's possible to add more users. Caller must hold the MANAGE_USERS
     * permission.
     *
     * @return true if more users can be added, false if limit has been reached.
     * @hide
     */
    public boolean canAddMoreUsers() {
        final List<UserInfo> users = getUsers(true);
        final int totalUserCount = users.size();
        int aliveUserCount = 0;
        for (int i = 0; i < totalUserCount; i++) {
            UserInfo user = users.get(i);
            if (!user.isGuest()) {
                aliveUserCount++;
            }
        }
        return aliveUserCount < getMaxSupportedUsers();
    }

    /**
     * Checks whether it's possible to add more managed profiles. Caller must hold the MANAGE_USERS
     * permission.
     * if allowedToRemoveOne is true and if the user already has a managed profile, then return if
     * we could add a new managed profile to this user after removing the existing one.
     *
     * @return true if more managed profiles can be added, false if limit has been reached.
     * @hide
     */
    public boolean canAddMoreManagedProfiles(@UserIdInt int userId, boolean allowedToRemoveOne) {
        try {
            return mService.canAddMoreManagedProfiles(userId, allowedToRemoveOne);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns list of the profiles of userHandle including
     * userHandle itself.
     * Note that this returns both enabled and not enabled profiles. See
     * {@link #getEnabledProfiles(int)} if you need only the enabled ones.
     *
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @param userHandle profiles of this user will be returned.
     * @return the list of profiles.
     * @hide
     */
    public List<UserInfo> getProfiles(@UserIdInt int userHandle) {
        try {
            return mService.getProfiles(userHandle, false /* enabledOnly */);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @param userId one of the two user ids to check.
     * @param otherUserId one of the two user ids to check.
     * @return true if the two user ids are in the same profile group.
     * @hide
     */
    public boolean isSameProfileGroup(@UserIdInt int userId, int otherUserId) {
        try {
            return mService.isSameProfileGroup(userId, otherUserId);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns list of the profiles of userHandle including
     * userHandle itself.
     * Note that this returns only enabled.
     *
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @param userHandle profiles of this user will be returned.
     * @return the list of profiles.
     * @hide
     */
    public List<UserInfo> getEnabledProfiles(@UserIdInt int userHandle) {
        try {
            return mService.getProfiles(userHandle, true /* enabledOnly */);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a list of UserHandles for profiles associated with the user that the calling process
     * is running on, including the user itself.
     *
     * @return A non-empty list of UserHandles associated with the calling user.
     */
    public List<UserHandle> getUserProfiles() {
        int[] userIds = getProfileIds(UserHandle.myUserId(), true /* enabledOnly */);
        List<UserHandle> result = new ArrayList<>(userIds.length);
        for (int userId : userIds) {
            result.add(UserHandle.of(userId));
        }
        return result;
    }

    /**
     * Returns a list of ids for profiles associated with the specified user including the user
     * itself.
     *
     * @param userId      id of the user to return profiles for
     * @param enabledOnly whether return only {@link UserInfo#isEnabled() enabled} profiles
     * @return A non-empty list of ids of profiles associated with the specified user.
     *
     * @hide
     */
    public int[] getProfileIds(@UserIdInt int userId, boolean enabledOnly) {
        try {
            return mService.getProfileIds(userId, enabledOnly);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @see #getProfileIds(int, boolean)
     * @hide
     */
    public int[] getProfileIdsWithDisabled(@UserIdInt int userId) {
        return getProfileIds(userId, false /* enabledOnly */);
    }

    /**
     * @see #getProfileIds(int, boolean)
     * @hide
     */
    public int[] getEnabledProfileIds(@UserIdInt int userId) {
        return getProfileIds(userId, true /* enabledOnly */);
    }

    /**
     * Returns the device credential owner id of the profile from
     * which this method is called, or userHandle if called from a user that
     * is not a profile.
     *
     * @hide
     */
    public int getCredentialOwnerProfile(@UserIdInt int userHandle) {
        try {
            return mService.getCredentialOwnerProfile(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the parent of the profile which this method is called from
     * or null if called from a user that is not a profile.
     *
     * @hide
     */
    public UserInfo getProfileParent(@UserIdInt int userHandle) {
        try {
            return mService.getProfileParent(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Set quiet mode of a managed profile.
     *
     * @param userHandle The user handle of the profile.
     * @param enableQuietMode Whether quiet mode should be enabled or disabled.
     * @hide
     */
    public void setQuietModeEnabled(@UserIdInt int userHandle, boolean enableQuietMode) {
        try {
            mService.setQuietModeEnabled(userHandle, enableQuietMode);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether the given profile is in quiet mode or not.
     * Notes: Quiet mode is only supported for managed profiles.
     *
     * @param userHandle The user handle of the profile to be queried.
     * @return true if the profile is in quiet mode, false otherwise.
     */
    public boolean isQuietModeEnabled(UserHandle userHandle) {
        try {
            return mService.isQuietModeEnabled(userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Tries disabling quiet mode for a given user. If the user is still locked, we unlock the user
     * first by showing the confirm credentials screen and disable quiet mode upon successful
     * unlocking. If the user is already unlocked, we call through to {@link #setQuietModeEnabled}
     * directly.
     *
     * @return true if the quiet mode was disabled immediately
     * @hide
     */
    public boolean trySetQuietModeDisabled(@UserIdInt int userHandle, IntentSender target) {
        try {
            return mService.trySetQuietModeDisabled(userHandle, target);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * If the target user is a managed profile of the calling user or the caller
     * is itself a managed profile, then this returns a badged copy of the given
     * icon to be able to distinguish it from the original icon. For badging an
     * arbitrary drawable use {@link #getBadgedDrawableForUser(
     * android.graphics.drawable.Drawable, UserHandle, android.graphics.Rect, int)}.
     * <p>
     * If the original drawable is a BitmapDrawable and the backing bitmap is
     * mutable as per {@link android.graphics.Bitmap#isMutable()}, the badging
     * is performed in place and the original drawable is returned.
     * </p>
     *
     * @param icon The icon to badge.
     * @param user The target user.
     * @return A drawable that combines the original icon and a badge as
     *         determined by the system.
     * @removed
     */
    public Drawable getBadgedIconForUser(Drawable icon, UserHandle user) {
        return mContext.getPackageManager().getUserBadgedIcon(icon, user);
    }

    /**
     * If the target user is a managed profile of the calling user or the caller
     * is itself a managed profile, then this returns a badged copy of the given
     * drawable allowing the user to distinguish it from the original drawable.
     * The caller can specify the location in the bounds of the drawable to be
     * badged where the badge should be applied as well as the density of the
     * badge to be used.
     * <p>
     * If the original drawable is a BitmapDrawable and the backing bitmap is
     * mutable as per {@link android.graphics.Bitmap#isMutable()}, the badging
     * is performed in place and the original drawable is returned.
     * </p>
     *
     * @param badgedDrawable The drawable to badge.
     * @param user The target user.
     * @param badgeLocation Where in the bounds of the badged drawable to place
     *         the badge. If it's {@code null}, the badge is applied on top of the entire
     *         drawable being badged.
     * @param badgeDensity The optional desired density for the badge as per
     *         {@link android.util.DisplayMetrics#densityDpi}. If it's not positive,
     *         the density of the display is used.
     * @return A drawable that combines the original drawable and a badge as
     *         determined by the system.
     * @removed
     */
    public Drawable getBadgedDrawableForUser(Drawable badgedDrawable, UserHandle user,
            Rect badgeLocation, int badgeDensity) {
        return mContext.getPackageManager().getUserBadgedDrawableForDensity(badgedDrawable, user,
                badgeLocation, badgeDensity);
    }

    /**
     * If the target user is a managed profile of the calling user or the caller
     * is itself a managed profile, then this returns a copy of the label with
     * badging for accessibility services like talkback. E.g. passing in "Email"
     * and it might return "Work Email" for Email in the work profile.
     *
     * @param label The label to change.
     * @param user The target user.
     * @return A label that combines the original label and a badge as
     *         determined by the system.
     * @removed
     */
    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandle user) {
        return mContext.getPackageManager().getUserBadgedLabel(label, user);
    }

    /**
     * Returns information for all users on this device. Requires
     * {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @param excludeDying specify if the list should exclude users being
     *            removed.
     * @return the list of users that were created.
     * @hide
     */
    public List<UserInfo> getUsers(boolean excludeDying) {
        try {
            return mService.getUsers(excludeDying);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Removes a user and all associated data.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     * @param userHandle the integer handle of the user, where 0 is the primary user.
     * @hide
     */
    public boolean removeUser(@UserIdInt int userHandle) {
        try {
            return mService.removeUser(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Updates the user's name.
     * Requires {@link android.Manifest.permission#MANAGE_USERS} permission.
     *
     * @param userHandle the user's integer handle
     * @param name the new name for the user
     * @hide
     */
    public void setUserName(@UserIdInt int userHandle, String name) {
        try {
            mService.setUserName(userHandle, name);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the user's photo.
     * @param userHandle the user for whom to change the photo.
     * @param icon the bitmap to set as the photo.
     * @hide
     */
    public void setUserIcon(@UserIdInt int userHandle, Bitmap icon) {
        try {
            mService.setUserIcon(userHandle, icon);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a file descriptor for the user's photo. PNG data can be read from this file.
     * @param userHandle the user whose photo we want to read.
     * @return a {@link Bitmap} of the user's photo, or null if there's no photo.
     * @see com.android.internal.util.UserIcons#getDefaultUserIcon for a default.
     * @hide
     */
    public Bitmap getUserIcon(@UserIdInt int userHandle) {
        try {
            ParcelFileDescriptor fd = mService.getUserIcon(userHandle);
            if (fd != null) {
                try {
                    return BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
                } finally {
                    try {
                        fd.close();
                    } catch (IOException e) {
                    }
                }
            }
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
        return null;
    }

    /**
     * Returns the maximum number of users that can be created on this device. A return value
     * of 1 means that it is a single user device.
     * @hide
     * @return a value greater than or equal to 1
     */
    public static int getMaxSupportedUsers() {
        // Don't allow multiple users on certain builds
        if (android.os.Build.ID.startsWith("JVP")) return 1;
        // Svelte devices don't get multi-user.
        if (ActivityManager.isLowRamDeviceStatic()) return 1;
        return SystemProperties.getInt("fw.max_users",
                Resources.getSystem().getInteger(R.integer.config_multiuserMaximumUsers));
    }

    /**
     * Returns true if the user switcher should be shown, this will be if device supports multi-user
     * and there are at least 2 users available that are not managed profiles.
     * @hide
     * @return true if user switcher should be shown.
     */
    public boolean isUserSwitcherEnabled() {
        if (!supportsMultipleUsers()) {
            return false;
        }
        // If Demo Mode is on, don't show user switcher
        if (isDeviceInDemoMode(mContext)) {
            return false;
        }
        List<UserInfo> users = getUsers(true);
        if (users == null) {
           return false;
        }
        int switchableUserCount = 0;
        for (UserInfo user : users) {
            if (user.supportsSwitchToByUser()) {
                ++switchableUserCount;
            }
        }
        final boolean guestEnabled = !mContext.getSystemService(DevicePolicyManager.class)
                .getGuestUserDisabled(null);
        return switchableUserCount > 1 || guestEnabled;
    }

    /**
     * @hide
     */
    public static boolean isDeviceInDemoMode(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVICE_DEMO_MODE, 0) > 0;
    }

    /**
     * Returns a serial number on this device for a given userHandle. User handles can be recycled
     * when deleting and creating users, but serial numbers are not reused until the device is wiped.
     * @param userHandle
     * @return a serial number associated with that user, or -1 if the userHandle is not valid.
     * @hide
     */
    public int getUserSerialNumber(@UserIdInt int userHandle) {
        try {
            return mService.getUserSerialNumber(userHandle);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a userHandle on this device for a given user serial number. User handles can be
     * recycled when deleting and creating users, but serial numbers are not reused until the device
     * is wiped.
     * @param userSerialNumber
     * @return the userHandle associated with that user serial number, or -1 if the serial number
     * is not valid.
     * @hide
     */
    public @UserIdInt int getUserHandle(int userSerialNumber) {
        try {
            return mService.getUserHandle(userSerialNumber);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a {@link Bundle} containing any saved application restrictions for this user, for the
     * given package name. Only an application with this package name can call this method.
     *
     * <p>The returned {@link Bundle} consists of key-value pairs, as defined by the application,
     * where the types of values may be:
     * <ul>
     * <li>{@code boolean}
     * <li>{@code int}
     * <li>{@code String} or {@code String[]}
     * <li>From {@link android.os.Build.VERSION_CODES#M}, {@code Bundle} or {@code Bundle[]}
     * </ul>
     *
     * @param packageName the package name of the calling application
     * @return a {@link Bundle} with the restrictions for that package, or an empty {@link Bundle}
     * if there are no saved restrictions.
     *
     * @see #KEY_RESTRICTIONS_PENDING
     */
    public Bundle getApplicationRestrictions(String packageName) {
        try {
            return mService.getApplicationRestrictions(packageName);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public Bundle getApplicationRestrictions(String packageName, UserHandle user) {
        try {
            return mService.getApplicationRestrictionsForUser(packageName, user.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void setApplicationRestrictions(String packageName, Bundle restrictions,
            UserHandle user) {
        try {
            mService.setApplicationRestrictions(packageName, restrictions, user.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Sets a new challenge PIN for restrictions. This is only for use by pre-installed
     * apps and requires the MANAGE_USERS permission.
     * @param newPin the PIN to use for challenge dialogs.
     * @return Returns true if the challenge PIN was set successfully.
     * @deprecated The restrictions PIN functionality is no longer provided by the system.
     * This method is preserved for backwards compatibility reasons and always returns false.
     */
    public boolean setRestrictionsChallenge(String newPin) {
        return false;
    }

    /**
     * @hide
     * Set restrictions that should apply to any future guest user that's created.
     */
    public void setDefaultGuestRestrictions(Bundle restrictions) {
        try {
            mService.setDefaultGuestRestrictions(restrictions);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     * Gets the default guest restrictions.
     */
    public Bundle getDefaultGuestRestrictions() {
        try {
            return mService.getDefaultGuestRestrictions();
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * Returns creation time of the user or of a managed profile associated with the calling user.
     * @param userHandle user handle of the user or a managed profile associated with the
     *                   calling user.
     * @return creation time in milliseconds since Epoch time.
     */
    public long getUserCreationTime(UserHandle userHandle) {
        try {
            return mService.getUserCreationTime(userHandle.getIdentifier());
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     * Checks if any uninitialized user has the specific seed account name and type.
     *
     * @param mAccountName The account name to check for
     * @param mAccountType The account type of the account to check for
     * @return whether the seed account was found
     */
    public boolean someUserHasSeedAccount(String accountName, String accountType) {
        try {
            return mService.someUserHasSeedAccount(accountName, accountType);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }
}
