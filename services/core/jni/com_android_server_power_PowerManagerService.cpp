/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "PowerManagerService-JNI"

//#define LOG_NDEBUG 0

#include <android/hardware/power/1.1/IPower.h>
#include <vendor/lineage/power/1.0/ILineagePower.h>
#include "JNIHelp.h"
#include "jni.h"

#include <ScopedUtfChars.h>

#include <limits.h>

#include <android-base/chrono_utils.h>
#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/Log.h>
#include <utils/Timers.h>
#include <utils/misc.h>
#include <utils/String8.h>
#include <utils/Log.h>
#include <hardware/power.h>
#include <hardware_legacy/power.h>
#include <suspend/autosuspend.h>
#include <android/keycodes.h>

#include "com_android_server_power_PowerManagerService.h"

using android::hardware::Return;
using android::hardware::Void;
using android::hardware::power::V1_1::IPower;
using android::hardware::power::V1_0::PowerHint;
using android::hardware::power::V1_0::Feature;
using android::String8;
using vendor::lineage::power::V1_0::LineageFeature;

namespace android {

// ----------------------------------------------------------------------------

static struct {
    jmethodID userActivityFromNative;
} gPowerManagerServiceClassInfo;

// ----------------------------------------------------------------------------

static jobject gPowerManagerServiceObj;
sp<android::hardware::power::V1_0::IPower> gPowerHalV1_0 = nullptr;
sp<android::hardware::power::V1_1::IPower> gPowerHalV1_1 = nullptr;
sp<vendor::lineage::power::V1_0::ILineagePower> gLineagePowerHalV1_0 = nullptr;
bool gPowerHalExists = true;
bool gLineagePowerHalExists = true;
std::mutex gPowerHalMutex;
static nsecs_t gLastEventTime[USER_ACTIVITY_EVENT_LAST + 1];

// Throttling interval for user activity calls.
static const nsecs_t MIN_TIME_BETWEEN_USERACTIVITIES = 100 * 1000000L; // 100ms

// ----------------------------------------------------------------------------

static bool checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName) {
    if (env->ExceptionCheck()) {
        ALOGE("An exception was thrown by callback '%s'.", methodName);
        LOGE_EX(env);
        env->ExceptionClear();
        return true;
    }
    return false;
}

// Check validity of current handle to the power HAL service, and call getService() if necessary.
// The caller must be holding gPowerHalMutex.
bool getPowerHal() {
    if (gPowerHalExists && gPowerHalV1_0 == nullptr) {
        gPowerHalV1_0 = android::hardware::power::V1_0::IPower::getService();
        if (gPowerHalV1_0 != nullptr) {
            gPowerHalV1_1 =  android::hardware::power::V1_1::IPower::castFrom(gPowerHalV1_0);
            ALOGI("Loaded power HAL service");
        } else {
            ALOGI("Couldn't load power HAL service");
            gPowerHalExists = false;
        }
    }
    return gPowerHalV1_0 != nullptr;
}

// Check validity of current handle to the Lineage power HAL service, and call getService() if necessary.
// The caller must be holding gPowerHalMutex.
bool getLineagePowerHal() {
    if (gLineagePowerHalExists && gLineagePowerHalV1_0 == nullptr) {
        gLineagePowerHalV1_0 = vendor::lineage::power::V1_0::ILineagePower::getService();
        if (gLineagePowerHalV1_0 != nullptr) {
            ALOGI("Loaded power HAL service");
        } else {
            ALOGI("Couldn't load power HAL service");
            gLineagePowerHalExists = false;
        }
    }
    return gLineagePowerHalV1_0 != nullptr;
}

// Check if a call to a power HAL function failed; if so, log the failure and invalidate the
// current handle to the power HAL service. The caller must be holding gPowerHalMutex.
static void processReturn(const Return<void> &ret, const char* functionName) {
    if (!ret.isOk()) {
        ALOGE("%s() failed: power HAL service not available.", functionName);
        gPowerHalV1_0 = nullptr;
    }
}

void android_server_PowerManagerService_userActivity(nsecs_t eventTime, int32_t eventType,
        int32_t keyCode) {
    if (gPowerManagerServiceObj) {
        // Throttle calls into user activity by event type.
        // We're a little conservative about argument checking here in case the caller
        // passes in bad data which could corrupt system state.
        if (eventType >= 0 && eventType <= USER_ACTIVITY_EVENT_LAST) {
            nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
            if (eventTime > now) {
                eventTime = now;
            }

            if (gLastEventTime[eventType] + MIN_TIME_BETWEEN_USERACTIVITIES > eventTime) {
                return;
            }
            gLastEventTime[eventType] = eventTime;


            // Tell the power HAL when user activity occurs.
            gPowerHalMutex.lock();
            if (getPowerHal()) {
              Return<void> ret;
              if (gPowerHalV1_1 != nullptr) {
                ret = gPowerHalV1_1->powerHintAsync(PowerHint::INTERACTION, 0);
              } else {
                ret = gPowerHalV1_0->powerHint(PowerHint::INTERACTION, 0);
              }
              processReturn(ret, "powerHint");
            }
            gPowerHalMutex.unlock();

        }

        JNIEnv* env = AndroidRuntime::getJNIEnv();

        int flags = 0;
        if (keyCode == AKEYCODE_VOLUME_UP || keyCode == AKEYCODE_VOLUME_DOWN) {
            flags |= USER_ACTIVITY_FLAG_NO_BUTTON_LIGHTS;
        }

        env->CallVoidMethod(gPowerManagerServiceObj,
                gPowerManagerServiceClassInfo.userActivityFromNative,
                nanoseconds_to_milliseconds(eventTime), eventType, flags);
        checkAndClearExceptionFromCallback(env, "userActivityFromNative");
    }
}

// ----------------------------------------------------------------------------

static void nativeInit(JNIEnv* env, jobject obj) {
    gPowerManagerServiceObj = env->NewGlobalRef(obj);

    gPowerHalMutex.lock();
    getPowerHal();
    gPowerHalMutex.unlock();
}

static void nativeAcquireSuspendBlocker(JNIEnv *env, jclass /* clazz */, jstring nameStr) {
    ScopedUtfChars name(env, nameStr);
    acquire_wake_lock(PARTIAL_WAKE_LOCK, name.c_str());
}

static void nativeReleaseSuspendBlocker(JNIEnv *env, jclass /* clazz */, jstring nameStr) {
    ScopedUtfChars name(env, nameStr);
    release_wake_lock(name.c_str());
}

static void nativeSetInteractive(JNIEnv* /* env */, jclass /* clazz */, jboolean enable) {
    std::lock_guard<std::mutex> lock(gPowerHalMutex);
    if (getPowerHal()) {
        android::base::Timer t;
        Return<void> ret = gPowerHalV1_0->setInteractive(enable);
        processReturn(ret, "setInteractive");
        if (t.duration() > 20ms) {
            ALOGD("Excessive delay in setInteractive(%s) while turning screen %s",
                  enable ? "true" : "false", enable ? "on" : "off");
        }
    }
}

static void nativeSetAutoSuspend(JNIEnv* /* env */, jclass /* clazz */, jboolean enable) {
    if (enable) {
        android::base::Timer t;
        autosuspend_enable();
        if (t.duration() > 100ms) {
            ALOGD("Excessive delay in autosuspend_enable() while turning screen off");
        }
    } else {
        android::base::Timer t;
        autosuspend_disable();
        if (t.duration() > 100ms) {
            ALOGD("Excessive delay in autosuspend_disable() while turning screen on");
        }
    }
}

static void nativeSendPowerHint(JNIEnv *env, jclass clazz, jint hintId, jint data) {
    std::lock_guard<std::mutex> lock(gPowerHalMutex);
    if (getPowerHal()) {
        Return<void> ret;
        if (gPowerHalV1_1 != nullptr) {
            ret =  gPowerHalV1_1->powerHintAsync((PowerHint)hintId, data);
        } else {
            ret =  gPowerHalV1_0->powerHint((PowerHint)hintId, data);
        }
        processReturn(ret, "powerHint");
    }
}

static void nativeSetFeature(JNIEnv *env, jclass clazz, jint featureId, jint data) {
    std::lock_guard<std::mutex> lock(gPowerHalMutex);
    if (getPowerHal()) {
        Return<void> ret = gPowerHalV1_0->setFeature((Feature)featureId, static_cast<bool>(data));
        processReturn(ret, "setFeature");
    }
}

static jint nativeGetFeature(JNIEnv *env, jclass clazz, jint featureId) {
    int value = -1;

    std::lock_guard<std::mutex> lock(gPowerHalMutex);
    if (getLineagePowerHal()) {
        value = gLineagePowerHalV1_0->getFeature((LineageFeature)featureId);
    }

    return (jint)value;
}

// ----------------------------------------------------------------------------

static const JNINativeMethod gPowerManagerServiceMethods[] = {
    /* name, signature, funcPtr */
    { "nativeInit", "()V",
            (void*) nativeInit },
    { "nativeAcquireSuspendBlocker", "(Ljava/lang/String;)V",
            (void*) nativeAcquireSuspendBlocker },
    { "nativeReleaseSuspendBlocker", "(Ljava/lang/String;)V",
            (void*) nativeReleaseSuspendBlocker },
    { "nativeSetInteractive", "(Z)V",
            (void*) nativeSetInteractive },
    { "nativeSetAutoSuspend", "(Z)V",
            (void*) nativeSetAutoSuspend },
    { "nativeSendPowerHint", "(II)V",
            (void*) nativeSendPowerHint },
    { "nativeSetFeature", "(II)V",
            (void*) nativeSetFeature },
    { "nativeGetFeature", "(I)I",
            (void*) nativeGetFeature },
};

#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! (var), "Unable to find class " className);

#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! (var), "Unable to find method " methodName);

#define GET_FIELD_ID(var, clazz, fieldName, fieldDescriptor) \
        var = env->GetFieldID(clazz, fieldName, fieldDescriptor); \
        LOG_FATAL_IF(! (var), "Unable to find field " fieldName);

int register_android_server_PowerManagerService(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "com/android/server/power/PowerManagerService",
            gPowerManagerServiceMethods, NELEM(gPowerManagerServiceMethods));
    (void) res;  // Faked use when LOG_NDEBUG.
    LOG_FATAL_IF(res < 0, "Unable to register native methods.");

    // Callbacks

    jclass clazz;
    FIND_CLASS(clazz, "com/android/server/power/PowerManagerService");

    GET_METHOD_ID(gPowerManagerServiceClassInfo.userActivityFromNative, clazz,
            "userActivityFromNative", "(JII)V");

    // Initialize
    for (int i = 0; i <= USER_ACTIVITY_EVENT_LAST; i++) {
        gLastEventTime[i] = LLONG_MIN;
    }
    gPowerManagerServiceObj = NULL;
    return 0;
}

} /* namespace android */
