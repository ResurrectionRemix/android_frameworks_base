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

#define LOG_TAG "MtpDatabaseJNI"
#include "utils/Log.h"

#include "android_media_Utils.h"
#include "mtp.h"
#include "MtpDatabase.h"
#include "MtpDataPacket.h"
#include "MtpObjectInfo.h"
#include "MtpProperty.h"
#include "MtpStringBuffer.h"
#include "MtpUtils.h"

#include "src/piex_types.h"
#include "src/piex.h"

extern "C" {
#include "libexif/exif-content.h"
#include "libexif/exif-data.h"
#include "libexif/exif-tag.h"
#include "libexif/exif-utils.h"
}

#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/Log.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>

#include <assert.h>
#include <fcntl.h>
#include <inttypes.h>
#include <limits.h>
#include <stdio.h>
#include <unistd.h>

// for mtp query database enhancement
#include <sqlite3.h>
#include <sys/stat.h>

using namespace android;

// ----------------------------------------------------------------------------

static jmethodID method_beginSendObject;
static jmethodID method_endSendObject;
static jmethodID method_getObjectList;
static jmethodID method_getNumObjects;
static jmethodID method_getSupportedPlaybackFormats;
static jmethodID method_getSupportedCaptureFormats;
static jmethodID method_getSupportedObjectProperties;
static jmethodID method_getSupportedDeviceProperties;
static jmethodID method_setObjectProperty;
static jmethodID method_getDeviceProperty;
static jmethodID method_setDeviceProperty;
static jmethodID method_getObjectPropertyList;
static jmethodID method_getObjectInfo;
static jmethodID method_getObjectFilePath;
static jmethodID method_deleteFile;
static jmethodID method_getObjectReferences;
static jmethodID method_setObjectReferences;
static jmethodID method_sessionStarted;
static jmethodID method_sessionEnded;

static jfieldID field_context;
static jfieldID field_batteryLevel;
static jfieldID field_batteryScale;
static jfieldID field_deviceType;

// MtpPropertyList fields
static jfieldID field_mCount;
static jfieldID field_mResult;
static jfieldID field_mObjectHandles;
static jfieldID field_mPropertyCodes;
static jfieldID field_mDataTypes;
static jfieldID field_mLongValues;
static jfieldID field_mStringValues;

// ----------------------------------------------------------------------------

// for mtp query database enhancement
const int QUERY_BUSY_TIMEOUT_MS = 200;// 200ms
const char *mtpDbPath = "/data/data/com.android.providers.media/databases/external.db";
// mtp extension response code
#define MTP_RESPONSE_LOCAL_CALL_FAIL     (MTP_RESPONSE_UNDEFINED-1)

// add column sql string
#define MTP_COLUMN_STORAGE_ID         "storage_id"
#define MTP_COLUMN_OBJECT_FORMAT      "format"
#define MTP_COLUMN_OBJECT_SIZE        "_size"
#define MTP_COLUMN_OBJECT_FILE_NAME   "_data"
#define MTP_COLUMN_DATE_MODIFIED      "date_modified"
#define MTP_COLUMN_PARENT_OBJECT      "parent"
#define MTP_COLUMN_NAME               "title"
#define MTP_COLUMN_DISPLAY_NAME       "_display_name"
#define MTP_COLUMN_DATE_ADDED         "date_added"
#define MTP_COLUMN_ARTIST             "artist"
#define MTP_COLUMN_ARTIST_KEY         "artist_key"
#define MTP_COLUMN_ALBUM_ARTIST       "album_artist"
#define MTP_COLUMN_ALBUM              "album"
#define MTP_COLUMN_TRACK              "track"
#define MTP_COLUMN_ORIGINAL_RELEASE_DATE "year"
#define MTP_COLUMN_COMPOSER           "composer"
#define MTP_COLUMN_DURATION           "duration"
#define MTP_COLUMN_DESCRIPTION        "description"
#define MTP_COLUMN_GENRES_NAME        "name"

// mtp media file type
#define MTP_MEDIA_TYPE_NONE  0
#define MTP_MEDIA_TYPE_IMAGE  1
#define MTP_MEDIA_TYPE_AUDIO  2
#define MTP_MEDIA_TYPE_VIDEO  3
#define MTP_MEDIA_TYPE_PLAYLIST  4

// mtp file property group
static int FILE_PROPERTIES[] = {
        // NOTE must match beginning of AUDIO_PROPERTIES, VIDEO_PROPERTIES
        // and IMAGE_PROPERTIES below
        MTP_PROPERTY_STORAGE_ID,
        MTP_PROPERTY_OBJECT_FORMAT,
        MTP_PROPERTY_PROTECTION_STATUS,
        MTP_PROPERTY_OBJECT_SIZE,
        MTP_PROPERTY_OBJECT_FILE_NAME,
        MTP_PROPERTY_DATE_MODIFIED,
        MTP_PROPERTY_PARENT_OBJECT,
        MTP_PROPERTY_PERSISTENT_UID,
        MTP_PROPERTY_NAME,
        MTP_PROPERTY_DISPLAY_NAME,
        MTP_PROPERTY_DATE_ADDED,
};

static int AUDIO_PROPERTIES[] = {
        // NOTE must match FILE_PROPERTIES above
        MTP_PROPERTY_STORAGE_ID,
        MTP_PROPERTY_OBJECT_FORMAT,
        MTP_PROPERTY_PROTECTION_STATUS,
        MTP_PROPERTY_OBJECT_SIZE,
        MTP_PROPERTY_OBJECT_FILE_NAME,
        MTP_PROPERTY_DATE_MODIFIED,
        MTP_PROPERTY_PARENT_OBJECT,
        MTP_PROPERTY_PERSISTENT_UID,
        MTP_PROPERTY_NAME,
        MTP_PROPERTY_DISPLAY_NAME,
        MTP_PROPERTY_DATE_ADDED,

        // audio specific properties
        MTP_PROPERTY_ARTIST,
        MTP_PROPERTY_ALBUM_NAME,
        MTP_PROPERTY_ALBUM_ARTIST,
        MTP_PROPERTY_TRACK,
        MTP_PROPERTY_ORIGINAL_RELEASE_DATE,
        MTP_PROPERTY_DURATION,
        MTP_PROPERTY_GENRE,
        MTP_PROPERTY_COMPOSER,
        MTP_PROPERTY_AUDIO_WAVE_CODEC,
        MTP_PROPERTY_BITRATE_TYPE,
        MTP_PROPERTY_AUDIO_BITRATE,
        MTP_PROPERTY_NUMBER_OF_CHANNELS,
        MTP_PROPERTY_SAMPLE_RATE,
};

static int VIDEO_PROPERTIES[] = {
        // NOTE must match FILE_PROPERTIES above
        MTP_PROPERTY_STORAGE_ID,
        MTP_PROPERTY_OBJECT_FORMAT,
        MTP_PROPERTY_PROTECTION_STATUS,
        MTP_PROPERTY_OBJECT_SIZE,
        MTP_PROPERTY_OBJECT_FILE_NAME,
        MTP_PROPERTY_DATE_MODIFIED,
        MTP_PROPERTY_PARENT_OBJECT,
        MTP_PROPERTY_PERSISTENT_UID,
        MTP_PROPERTY_NAME,
        MTP_PROPERTY_DISPLAY_NAME,
        MTP_PROPERTY_DATE_ADDED,

        // video specific properties
        MTP_PROPERTY_ARTIST,
        MTP_PROPERTY_ALBUM_NAME,
        MTP_PROPERTY_DURATION,
        MTP_PROPERTY_DESCRIPTION,
};

static int IMAGE_PROPERTIES[] = {
        // NOTE must match FILE_PROPERTIES above
        MTP_PROPERTY_STORAGE_ID,
        MTP_PROPERTY_OBJECT_FORMAT,
        MTP_PROPERTY_PROTECTION_STATUS,
        MTP_PROPERTY_OBJECT_SIZE,
        MTP_PROPERTY_OBJECT_FILE_NAME,
        MTP_PROPERTY_DATE_MODIFIED,
        MTP_PROPERTY_PARENT_OBJECT,
        MTP_PROPERTY_PERSISTENT_UID,
        MTP_PROPERTY_NAME,
        MTP_PROPERTY_DISPLAY_NAME,
        MTP_PROPERTY_DATE_ADDED,

        // image specific properties
        MTP_PROPERTY_DESCRIPTION,
};

// ----------------------------------------------------------------------------

MtpDatabase* getMtpDatabase(JNIEnv *env, jobject database) {
    return (MtpDatabase *)env->GetLongField(database, field_context);
}

// ----------------------------------------------------------------------------

class MyMtpDatabase : public MtpDatabase {
private:
    jobject         mDatabase;
    jintArray       mIntBuffer;
    jlongArray      mLongBuffer;
    jcharArray      mStringBuffer;

public:
                                    MyMtpDatabase(JNIEnv *env, jobject client);
    virtual                         ~MyMtpDatabase();
    void                            cleanup(JNIEnv *env);

    virtual MtpObjectHandle         beginSendObject(const char* path,
                                            MtpObjectFormat format,
                                            MtpObjectHandle parent,
                                            MtpStorageID storage,
                                            uint64_t size,
                                            time_t modified);

    virtual void                    endSendObject(const char* path,
                                            MtpObjectHandle handle,
                                            MtpObjectFormat format,
                                            bool succeeded);

    virtual MtpObjectHandleList*    getObjectList(MtpStorageID storageID,
                                    MtpObjectFormat format,
                                    MtpObjectHandle parent);

    virtual int                     getNumObjects(MtpStorageID storageID,
                                            MtpObjectFormat format,
                                            MtpObjectHandle parent);

    // callee should delete[] the results from these
    // results can be NULL
    virtual MtpObjectFormatList*    getSupportedPlaybackFormats();
    virtual MtpObjectFormatList*    getSupportedCaptureFormats();
    virtual MtpObjectPropertyList*  getSupportedObjectProperties(MtpObjectFormat format);
    virtual MtpDevicePropertyList*  getSupportedDeviceProperties();

    virtual MtpResponseCode         getObjectPropertyValue(MtpObjectHandle handle,
                                            MtpObjectProperty property,
                                            MtpDataPacket& packet);

    virtual MtpResponseCode         setObjectPropertyValue(MtpObjectHandle handle,
                                            MtpObjectProperty property,
                                            MtpDataPacket& packet);

    virtual MtpResponseCode         getDevicePropertyValue(MtpDeviceProperty property,
                                            MtpDataPacket& packet);

    virtual MtpResponseCode         setDevicePropertyValue(MtpDeviceProperty property,
                                            MtpDataPacket& packet);

    virtual MtpResponseCode         resetDeviceProperty(MtpDeviceProperty property);

    virtual MtpResponseCode         getObjectPropertyList(MtpObjectHandle handle,
                                            uint32_t format, uint32_t property,
                                            int groupCode, int depth,
                                            MtpDataPacket& packet);

    virtual MtpResponseCode         getObjectInfo(MtpObjectHandle handle,
                                            MtpObjectInfo& info);

    virtual void*                   getThumbnail(MtpObjectHandle handle, size_t& outThumbSize);

    virtual MtpResponseCode         getObjectFilePath(MtpObjectHandle handle,
                                            MtpString& outFilePath,
                                            int64_t& outFileLength,
                                            MtpObjectFormat& outFormat);
    virtual MtpResponseCode         deleteFile(MtpObjectHandle handle);

    bool                            getObjectPropertyInfo(MtpObjectProperty property, int& type);
    bool                            getDevicePropertyInfo(MtpDeviceProperty property, int& type);

    virtual MtpObjectHandleList*    getObjectReferences(MtpObjectHandle handle);

    virtual MtpResponseCode         setObjectReferences(MtpObjectHandle handle,
                                            MtpObjectHandleList* references);

    virtual MtpProperty*            getObjectPropertyDesc(MtpObjectProperty property,
                                            MtpObjectFormat format);

    virtual MtpProperty*            getDevicePropertyDesc(MtpDeviceProperty property);

    virtual void                    sessionStarted();

    virtual void                    sessionEnded();

// ----------------------------------------------------------------------------

// sqlite directly query add for mtp enhancement
private:
    sqlite3 *mMydb;
    MtpObjectPropertyList propCodeList;
    Int16List columnIndex;

private:
    // sqlite simple query method,  add to MyMtpDatabase  class
    void openDataBase(const char *path);

    void closeDataBase();

    int getObjectFormat(int handle);

    void getSupportedObjectPropertiesFast(int format);

    MtpResponseCode         getObjectFilePathFast(MtpObjectHandle handle,
                                            MtpString& outFilePath,
                                            int64_t& outFileLength,
                                            MtpObjectFormat& outFormat);

    MtpObjectHandleList*    getObjectReferencesFast(MtpObjectHandle handle, MtpResponseCode& result);

    MtpResponseCode         getObjectPropertyListFast(MtpObjectHandle handle,
                                    uint32_t format, uint32_t property,
                                    int groupCode, int depth,
                                    MtpDataPacket& packet);

    MtpResponseCode         getObjectInfoFast(MtpObjectHandle handle,
                                    MtpObjectInfo& info);
};

// ----------------------------------------------------------------------------

static void checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName) {
    if (env->ExceptionCheck()) {
        ALOGE("An exception was thrown by callback '%s'.", methodName);
        LOGE_EX(env);
        env->ExceptionClear();
    }
}

// ----------------------------------------------------------------------------

MyMtpDatabase::MyMtpDatabase(JNIEnv *env, jobject client)
    :   mDatabase(env->NewGlobalRef(client)),
        mIntBuffer(NULL),
        mLongBuffer(NULL),
        mStringBuffer(NULL)
{
    // create buffers for out arguments
    // we don't need to be thread-safe so this is OK
    jintArray intArray = env->NewIntArray(3);
    if (!intArray) {
        return; // Already threw.
    }
    mIntBuffer = (jintArray)env->NewGlobalRef(intArray);
    jlongArray longArray = env->NewLongArray(2);
    if (!longArray) {
        return; // Already threw.
    }
    mLongBuffer = (jlongArray)env->NewGlobalRef(longArray);
    // Needs to be long enough to hold a file path for getObjectFilePath()
    jcharArray charArray = env->NewCharArray(PATH_MAX + 1);
    if (!charArray) {
        return; // Already threw.
    }
    mStringBuffer = (jcharArray)env->NewGlobalRef(charArray);

    // for mtp query enhancement
    openDataBase(mtpDbPath);
}

void MyMtpDatabase::cleanup(JNIEnv *env) {
    env->DeleteGlobalRef(mDatabase);
    env->DeleteGlobalRef(mIntBuffer);
    env->DeleteGlobalRef(mLongBuffer);
    env->DeleteGlobalRef(mStringBuffer);

    // for mtp query enhancement
    closeDataBase();
}

MyMtpDatabase::~MyMtpDatabase() {
}

MtpObjectHandle MyMtpDatabase::beginSendObject(const char* path,
                                               MtpObjectFormat format,
                                               MtpObjectHandle parent,
                                               MtpStorageID storage,
                                               uint64_t size,
                                               time_t modified) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jstring pathStr = env->NewStringUTF(path);
    MtpObjectHandle result = env->CallIntMethod(mDatabase, method_beginSendObject,
            pathStr, (jint)format, (jint)parent, (jint)storage,
            (jlong)size, (jlong)modified);

    if (pathStr)
        env->DeleteLocalRef(pathStr);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

void MyMtpDatabase::endSendObject(const char* path, MtpObjectHandle handle,
                                  MtpObjectFormat format, bool succeeded) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jstring pathStr = env->NewStringUTF(path);
    env->CallVoidMethod(mDatabase, method_endSendObject, pathStr,
                        (jint)handle, (jint)format, (jboolean)succeeded);

    if (pathStr)
        env->DeleteLocalRef(pathStr);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

MtpObjectHandleList* MyMtpDatabase::getObjectList(MtpStorageID storageID,
                                                  MtpObjectFormat format,
                                                  MtpObjectHandle parent) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jintArray array = (jintArray)env->CallObjectMethod(mDatabase, method_getObjectList,
                (jint)storageID, (jint)format, (jint)parent);
    if (!array)
        return NULL;
    MtpObjectHandleList* list = new MtpObjectHandleList();
    jint* handles = env->GetIntArrayElements(array, 0);
    jsize length = env->GetArrayLength(array);
    for (int i = 0; i < length; i++)
        list->push(handles[i]);
    env->ReleaseIntArrayElements(array, handles, 0);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return list;
}

int MyMtpDatabase::getNumObjects(MtpStorageID storageID,
                                 MtpObjectFormat format,
                                 MtpObjectHandle parent) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    int result = env->CallIntMethod(mDatabase, method_getNumObjects,
                (jint)storageID, (jint)format, (jint)parent);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

MtpObjectFormatList* MyMtpDatabase::getSupportedPlaybackFormats() {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jintArray array = (jintArray)env->CallObjectMethod(mDatabase,
            method_getSupportedPlaybackFormats);
    if (!array)
        return NULL;
    MtpObjectFormatList* list = new MtpObjectFormatList();
    jint* formats = env->GetIntArrayElements(array, 0);
    jsize length = env->GetArrayLength(array);
    for (int i = 0; i < length; i++)
        list->push(formats[i]);
    env->ReleaseIntArrayElements(array, formats, 0);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return list;
}

MtpObjectFormatList* MyMtpDatabase::getSupportedCaptureFormats() {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jintArray array = (jintArray)env->CallObjectMethod(mDatabase,
            method_getSupportedCaptureFormats);
    if (!array)
        return NULL;
    MtpObjectFormatList* list = new MtpObjectFormatList();
    jint* formats = env->GetIntArrayElements(array, 0);
    jsize length = env->GetArrayLength(array);
    for (int i = 0; i < length; i++)
        list->push(formats[i]);
    env->ReleaseIntArrayElements(array, formats, 0);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return list;
}

MtpObjectPropertyList* MyMtpDatabase::getSupportedObjectProperties(MtpObjectFormat format) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jintArray array = (jintArray)env->CallObjectMethod(mDatabase,
            method_getSupportedObjectProperties, (jint)format);
    if (!array)
        return NULL;
    MtpObjectPropertyList* list = new MtpObjectPropertyList();
    jint* properties = env->GetIntArrayElements(array, 0);
    jsize length = env->GetArrayLength(array);
    for (int i = 0; i < length; i++)
        list->push(properties[i]);
    env->ReleaseIntArrayElements(array, properties, 0);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return list;
}

MtpDevicePropertyList* MyMtpDatabase::getSupportedDeviceProperties() {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jintArray array = (jintArray)env->CallObjectMethod(mDatabase,
            method_getSupportedDeviceProperties);
    if (!array)
        return NULL;
    MtpDevicePropertyList* list = new MtpDevicePropertyList();
    jint* properties = env->GetIntArrayElements(array, 0);
    jsize length = env->GetArrayLength(array);
    for (int i = 0; i < length; i++)
        list->push(properties[i]);
    env->ReleaseIntArrayElements(array, properties, 0);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return list;
}

MtpResponseCode MyMtpDatabase::getObjectPropertyValue(MtpObjectHandle handle,
                                                      MtpObjectProperty property,
                                                      MtpDataPacket& packet) {
    static_assert(sizeof(jint) >= sizeof(MtpObjectHandle),
                  "Casting MtpObjectHandle to jint loses a value");
    static_assert(sizeof(jint) >= sizeof(MtpObjectProperty),
                  "Casting MtpObjectProperty to jint loses a value");
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jobject list = env->CallObjectMethod(
            mDatabase,
            method_getObjectPropertyList,
            static_cast<jint>(handle),
            0,
            static_cast<jint>(property),
            0,
            0);
    MtpResponseCode result = env->GetIntField(list, field_mResult);
    int count = env->GetIntField(list, field_mCount);
    if (result == MTP_RESPONSE_OK && count != 1)
        result = MTP_RESPONSE_GENERAL_ERROR;

    if (result == MTP_RESPONSE_OK) {
        jintArray objectHandlesArray = (jintArray)env->GetObjectField(list, field_mObjectHandles);
        jintArray propertyCodesArray = (jintArray)env->GetObjectField(list, field_mPropertyCodes);
        jintArray dataTypesArray = (jintArray)env->GetObjectField(list, field_mDataTypes);
        jlongArray longValuesArray = (jlongArray)env->GetObjectField(list, field_mLongValues);
        jobjectArray stringValuesArray = (jobjectArray)env->GetObjectField(list, field_mStringValues);

        jint* objectHandles = env->GetIntArrayElements(objectHandlesArray, 0);
        jint* propertyCodes = env->GetIntArrayElements(propertyCodesArray, 0);
        jint* dataTypes = env->GetIntArrayElements(dataTypesArray, 0);
        jlong* longValues = (longValuesArray ? env->GetLongArrayElements(longValuesArray, 0) : NULL);

        int type = dataTypes[0];
        jlong longValue = (longValues ? longValues[0] : 0);

        // special case date properties, which are strings to MTP
        // but stored internally as a uint64
        if (property == MTP_PROPERTY_DATE_MODIFIED || property == MTP_PROPERTY_DATE_ADDED) {
            char    date[20];
            formatDateTime(longValue, date, sizeof(date));
            packet.putString(date);
            goto out;
        }
        // release date is stored internally as just the year
        if (property == MTP_PROPERTY_ORIGINAL_RELEASE_DATE) {
            char    date[20];
            snprintf(date, sizeof(date), "%04" PRId64 "0101T000000", longValue);
            packet.putString(date);
            goto out;
        }

        switch (type) {
            case MTP_TYPE_INT8:
                packet.putInt8(longValue);
                break;
            case MTP_TYPE_UINT8:
                packet.putUInt8(longValue);
                break;
            case MTP_TYPE_INT16:
                packet.putInt16(longValue);
                break;
            case MTP_TYPE_UINT16:
                packet.putUInt16(longValue);
                break;
            case MTP_TYPE_INT32:
                packet.putInt32(longValue);
                break;
            case MTP_TYPE_UINT32:
                packet.putUInt32(longValue);
                break;
            case MTP_TYPE_INT64:
                packet.putInt64(longValue);
                break;
            case MTP_TYPE_UINT64:
                packet.putUInt64(longValue);
                break;
            case MTP_TYPE_INT128:
                packet.putInt128(longValue);
                break;
            case MTP_TYPE_UINT128:
                packet.putUInt128(longValue);
                break;
            case MTP_TYPE_STR:
            {
                jstring stringValue = (jstring)env->GetObjectArrayElement(stringValuesArray, 0);
                const char* str = (stringValue ? env->GetStringUTFChars(stringValue, NULL) : NULL);
                if (stringValue) {
                    packet.putString(str);
                    env->ReleaseStringUTFChars(stringValue, str);
                } else {
                    packet.putEmptyString();
                }
                env->DeleteLocalRef(stringValue);
                break;
             }
            default:
                ALOGE("unsupported type in getObjectPropertyValue\n");
                result = MTP_RESPONSE_INVALID_OBJECT_PROP_FORMAT;
        }
out:
        env->ReleaseIntArrayElements(objectHandlesArray, objectHandles, 0);
        env->ReleaseIntArrayElements(propertyCodesArray, propertyCodes, 0);
        env->ReleaseIntArrayElements(dataTypesArray, dataTypes, 0);
        if (longValues)
            env->ReleaseLongArrayElements(longValuesArray, longValues, 0);

        env->DeleteLocalRef(objectHandlesArray);
        env->DeleteLocalRef(propertyCodesArray);
        env->DeleteLocalRef(dataTypesArray);
        if (longValuesArray)
            env->DeleteLocalRef(longValuesArray);
        if (stringValuesArray)
            env->DeleteLocalRef(stringValuesArray);
    }

    env->DeleteLocalRef(list);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

static bool readLongValue(int type, MtpDataPacket& packet, jlong& longValue) {
    switch (type) {
        case MTP_TYPE_INT8: {
            int8_t temp;
            if (!packet.getInt8(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_UINT8: {
            uint8_t temp;
            if (!packet.getUInt8(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_INT16: {
            int16_t temp;
            if (!packet.getInt16(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_UINT16: {
            uint16_t temp;
            if (!packet.getUInt16(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_INT32: {
            int32_t temp;
            if (!packet.getInt32(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_UINT32: {
            uint32_t temp;
            if (!packet.getUInt32(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_INT64: {
            int64_t temp;
            if (!packet.getInt64(temp)) return false;
            longValue = temp;
            break;
        }
        case MTP_TYPE_UINT64: {
            uint64_t temp;
            if (!packet.getUInt64(temp)) return false;
            longValue = temp;
            break;
        }
        default:
            ALOGE("unsupported type in readLongValue");
            return false;
    }
    return true;
}

MtpResponseCode MyMtpDatabase::setObjectPropertyValue(MtpObjectHandle handle,
                                                      MtpObjectProperty property,
                                                      MtpDataPacket& packet) {
    int         type;

    if (!getObjectPropertyInfo(property, type))
        return MTP_RESPONSE_OBJECT_PROP_NOT_SUPPORTED;

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jlong longValue = 0;
    jstring stringValue = NULL;
    MtpResponseCode result = MTP_RESPONSE_INVALID_OBJECT_PROP_FORMAT;

    if (type == MTP_TYPE_STR) {
        MtpStringBuffer buffer;
        if (!packet.getString(buffer)) goto fail;
        stringValue = env->NewStringUTF((const char *)buffer);
    } else {
        if (!readLongValue(type, packet, longValue)) goto fail;
    }

    result = env->CallIntMethod(mDatabase, method_setObjectProperty,
                (jint)handle, (jint)property, longValue, stringValue);
    if (stringValue)
        env->DeleteLocalRef(stringValue);

fail:
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

MtpResponseCode MyMtpDatabase::getDevicePropertyValue(MtpDeviceProperty property,
                                                      MtpDataPacket& packet) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();

    if (property == MTP_DEVICE_PROPERTY_BATTERY_LEVEL) {
        // special case - implemented here instead of Java
        packet.putUInt8((uint8_t)env->GetIntField(mDatabase, field_batteryLevel));
        return MTP_RESPONSE_OK;
    } else {
        int type;

        if (!getDevicePropertyInfo(property, type))
            return MTP_RESPONSE_DEVICE_PROP_NOT_SUPPORTED;

        jint result = env->CallIntMethod(mDatabase, method_getDeviceProperty,
                    (jint)property, mLongBuffer, mStringBuffer);
        if (result != MTP_RESPONSE_OK) {
            checkAndClearExceptionFromCallback(env, __FUNCTION__);
            return result;
        }

        jlong* longValues = env->GetLongArrayElements(mLongBuffer, 0);
        jlong longValue = longValues[0];
        env->ReleaseLongArrayElements(mLongBuffer, longValues, 0);

        switch (type) {
            case MTP_TYPE_INT8:
                packet.putInt8(longValue);
                break;
            case MTP_TYPE_UINT8:
                packet.putUInt8(longValue);
                break;
            case MTP_TYPE_INT16:
                packet.putInt16(longValue);
                break;
            case MTP_TYPE_UINT16:
                packet.putUInt16(longValue);
                break;
            case MTP_TYPE_INT32:
                packet.putInt32(longValue);
                break;
            case MTP_TYPE_UINT32:
                packet.putUInt32(longValue);
                break;
            case MTP_TYPE_INT64:
                packet.putInt64(longValue);
                break;
            case MTP_TYPE_UINT64:
                packet.putUInt64(longValue);
                break;
            case MTP_TYPE_INT128:
                packet.putInt128(longValue);
                break;
            case MTP_TYPE_UINT128:
                packet.putInt128(longValue);
                break;
            case MTP_TYPE_STR:
            {
                jchar* str = env->GetCharArrayElements(mStringBuffer, 0);
                packet.putString(str);
                env->ReleaseCharArrayElements(mStringBuffer, str, 0);
                break;
             }
            default:
                ALOGE("unsupported type in getDevicePropertyValue\n");
                return MTP_RESPONSE_INVALID_DEVICE_PROP_FORMAT;
        }

        checkAndClearExceptionFromCallback(env, __FUNCTION__);
        return MTP_RESPONSE_OK;
    }
}

MtpResponseCode MyMtpDatabase::setDevicePropertyValue(MtpDeviceProperty property,
                                                      MtpDataPacket& packet) {
    int         type;

    if (!getDevicePropertyInfo(property, type))
        return MTP_RESPONSE_DEVICE_PROP_NOT_SUPPORTED;

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jlong longValue = 0;
    jstring stringValue = NULL;
    MtpResponseCode result = MTP_RESPONSE_INVALID_DEVICE_PROP_FORMAT;

    if (type == MTP_TYPE_STR) {
        MtpStringBuffer buffer;
        if (!packet.getString(buffer)) goto fail;
        stringValue = env->NewStringUTF((const char *)buffer);
    } else {
        if (!readLongValue(type, packet, longValue)) goto fail;
    }

    result = env->CallIntMethod(mDatabase, method_setDeviceProperty,
                (jint)property, longValue, stringValue);
    if (stringValue)
        env->DeleteLocalRef(stringValue);

fail:
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

MtpResponseCode MyMtpDatabase::resetDeviceProperty(MtpDeviceProperty /*property*/) {
    return -1;
}

MtpResponseCode MyMtpDatabase::getObjectPropertyList(MtpObjectHandle handle,
                                                     uint32_t format, uint32_t property,
                                                     int groupCode, int depth,
                                                     MtpDataPacket& packet) {
    // try to use sqlite directly query, if fail go to original flow
    MtpResponseCode fast_result;
    fast_result = getObjectPropertyListFast(handle, format, property,
            groupCode, depth, packet);
    if (fast_result != MTP_RESPONSE_LOCAL_CALL_FAIL)
        return fast_result;

    static_assert(sizeof(jint) >= sizeof(MtpObjectHandle),
                  "Casting MtpObjectHandle to jint loses a value");
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jobject list = env->CallObjectMethod(
            mDatabase,
            method_getObjectPropertyList,
            static_cast<jint>(handle),
            static_cast<jint>(format),
            static_cast<jint>(property),
            static_cast<jint>(groupCode),
            static_cast<jint>(depth));
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    if (!list)
        return MTP_RESPONSE_GENERAL_ERROR;
    int count = env->GetIntField(list, field_mCount);
    MtpResponseCode result = env->GetIntField(list, field_mResult);

    packet.putUInt32(count);
    if (count > 0) {
        jintArray objectHandlesArray = (jintArray)env->GetObjectField(list, field_mObjectHandles);
        jintArray propertyCodesArray = (jintArray)env->GetObjectField(list, field_mPropertyCodes);
        jintArray dataTypesArray = (jintArray)env->GetObjectField(list, field_mDataTypes);
        jlongArray longValuesArray = (jlongArray)env->GetObjectField(list, field_mLongValues);
        jobjectArray stringValuesArray = (jobjectArray)env->GetObjectField(list, field_mStringValues);

        jint* objectHandles = env->GetIntArrayElements(objectHandlesArray, 0);
        jint* propertyCodes = env->GetIntArrayElements(propertyCodesArray, 0);
        jint* dataTypes = env->GetIntArrayElements(dataTypesArray, 0);
        jlong* longValues = (longValuesArray ? env->GetLongArrayElements(longValuesArray, 0) : NULL);

        for (int i = 0; i < count; i++) {
            packet.putUInt32(objectHandles[i]);
            packet.putUInt16(propertyCodes[i]);
            int type = dataTypes[i];
            packet.putUInt16(type);

            switch (type) {
                case MTP_TYPE_INT8:
                    packet.putInt8(longValues[i]);
                    break;
                case MTP_TYPE_UINT8:
                    packet.putUInt8(longValues[i]);
                    break;
                case MTP_TYPE_INT16:
                    packet.putInt16(longValues[i]);
                    break;
                case MTP_TYPE_UINT16:
                    packet.putUInt16(longValues[i]);
                    break;
                case MTP_TYPE_INT32:
                    packet.putInt32(longValues[i]);
                    break;
                case MTP_TYPE_UINT32:
                    packet.putUInt32(longValues[i]);
                    break;
                case MTP_TYPE_INT64:
                    packet.putInt64(longValues[i]);
                    break;
                case MTP_TYPE_UINT64:
                    packet.putUInt64(longValues[i]);
                    break;
                case MTP_TYPE_INT128:
                    packet.putInt128(longValues[i]);
                    break;
                case MTP_TYPE_UINT128:
                    packet.putUInt128(longValues[i]);
                    break;
                case MTP_TYPE_STR: {
                    jstring value = (jstring)env->GetObjectArrayElement(stringValuesArray, i);
                    const char *valueStr = (value ? env->GetStringUTFChars(value, NULL) : NULL);
                    if (valueStr) {
                        packet.putString(valueStr);
                        env->ReleaseStringUTFChars(value, valueStr);
                    } else {
                        packet.putEmptyString();
                    }
                    env->DeleteLocalRef(value);
                    break;
                }
                default:
                    ALOGE("bad or unsupported data type in MyMtpDatabase::getObjectPropertyList");
                    break;
            }
        }

        env->ReleaseIntArrayElements(objectHandlesArray, objectHandles, 0);
        env->ReleaseIntArrayElements(propertyCodesArray, propertyCodes, 0);
        env->ReleaseIntArrayElements(dataTypesArray, dataTypes, 0);
        if (longValues)
            env->ReleaseLongArrayElements(longValuesArray, longValues, 0);

        env->DeleteLocalRef(objectHandlesArray);
        env->DeleteLocalRef(propertyCodesArray);
        env->DeleteLocalRef(dataTypesArray);
        if (longValuesArray)
            env->DeleteLocalRef(longValuesArray);
        if (stringValuesArray)
            env->DeleteLocalRef(stringValuesArray);
    }

    env->DeleteLocalRef(list);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

static void foreachentry(ExifEntry *entry, void* /* user */) {
    char buf[1024];
    ALOGI("entry %x, format %d, size %d: %s",
            entry->tag, entry->format, entry->size, exif_entry_get_value(entry, buf, sizeof(buf)));
}

static void foreachcontent(ExifContent *content, void *user) {
    ALOGI("content %d", exif_content_get_ifd(content));
    exif_content_foreach_entry(content, foreachentry, user);
}

static long getLongFromExifEntry(ExifEntry *e) {
    ExifByteOrder o = exif_data_get_byte_order(e->parent->parent);
    return exif_get_long(e->data, o);
}

MtpResponseCode MyMtpDatabase::getObjectInfo(MtpObjectHandle handle,
                                             MtpObjectInfo& info) {
    // try to use sqlite directly query, if fail go to original flow
    if (getObjectInfoFast(handle, info) == MTP_RESPONSE_OK) {
        return MTP_RESPONSE_OK;
    }

    MtpString       path;
    int64_t         length;
    MtpObjectFormat format;

    MtpResponseCode result = getObjectFilePath(handle, path, length, format);
    if (result != MTP_RESPONSE_OK) {
        return result;
    }
    info.mCompressedSize = (length > 0xFFFFFFFFLL ? 0xFFFFFFFF : (uint32_t)length);

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (!env->CallBooleanMethod(mDatabase, method_getObjectInfo,
                (jint)handle, mIntBuffer, mStringBuffer, mLongBuffer)) {
        return MTP_RESPONSE_INVALID_OBJECT_HANDLE;
    }

    jint* intValues = env->GetIntArrayElements(mIntBuffer, 0);
    info.mStorageID = intValues[0];
    info.mFormat = intValues[1];
    info.mParent = intValues[2];
    env->ReleaseIntArrayElements(mIntBuffer, intValues, 0);

    jlong* longValues = env->GetLongArrayElements(mLongBuffer, 0);
    info.mDateCreated = longValues[0];
    info.mDateModified = longValues[1];
    env->ReleaseLongArrayElements(mLongBuffer, longValues, 0);

    if ((false)) {
        info.mAssociationType = (format == MTP_FORMAT_ASSOCIATION ?
                                MTP_ASSOCIATION_TYPE_GENERIC_FOLDER :
                                MTP_ASSOCIATION_TYPE_UNDEFINED);
    }
    info.mAssociationType = MTP_ASSOCIATION_TYPE_UNDEFINED;

    jchar* str = env->GetCharArrayElements(mStringBuffer, 0);
    MtpString temp(reinterpret_cast<char16_t*>(str));
    info.mName = strdup((const char *)temp);
    env->ReleaseCharArrayElements(mStringBuffer, str, 0);

    // read EXIF data for thumbnail information
    switch (info.mFormat) {
        case MTP_FORMAT_EXIF_JPEG:
        case MTP_FORMAT_HEIF:
        case MTP_FORMAT_JFIF: {
            ExifData *exifdata = exif_data_new_from_file(path);
            if (exifdata) {
                if ((false)) {
                    exif_data_foreach_content(exifdata, foreachcontent, NULL);
                }

                ExifEntry *w = exif_content_get_entry(
                        exifdata->ifd[EXIF_IFD_EXIF], EXIF_TAG_PIXEL_X_DIMENSION);
                ExifEntry *h = exif_content_get_entry(
                        exifdata->ifd[EXIF_IFD_EXIF], EXIF_TAG_PIXEL_Y_DIMENSION);
                info.mThumbCompressedSize = exifdata->data ? exifdata->size : 0;
                info.mThumbFormat = MTP_FORMAT_EXIF_JPEG;
                info.mImagePixWidth = w ? getLongFromExifEntry(w) : 0;
                info.mImagePixHeight = h ? getLongFromExifEntry(h) : 0;
                exif_data_unref(exifdata);
            }
            break;
        }

        // Except DNG, all supported RAW image formats are not defined in PTP 1.2 specification.
        // Most of RAW image formats are based on TIFF or TIFF/EP. To render Fuji's RAF format,
        // it checks MTP_FORMAT_DEFINED case since it's designed as a custom format.
        case MTP_FORMAT_DNG:
        case MTP_FORMAT_TIFF:
        case MTP_FORMAT_TIFF_EP:
        case MTP_FORMAT_DEFINED: {
            std::unique_ptr<FileStream> stream(new FileStream(path));
            piex::PreviewImageData image_data;
            if (!GetExifFromRawImage(stream.get(), path, image_data)) {
                // Couldn't parse EXIF data from a image file via piex.
                break;
            }

            info.mThumbCompressedSize = image_data.thumbnail.length;
            info.mThumbFormat = MTP_FORMAT_EXIF_JPEG;
            info.mImagePixWidth = image_data.full_width;
            info.mImagePixHeight = image_data.full_height;

            break;
        }
    }

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return MTP_RESPONSE_OK;
}

void* MyMtpDatabase::getThumbnail(MtpObjectHandle handle, size_t& outThumbSize) {
    MtpString path;
    int64_t length;
    MtpObjectFormat format;
    void* result = NULL;
    outThumbSize = 0;

    if (getObjectFilePath(handle, path, length, format) == MTP_RESPONSE_OK) {
        switch (format) {
            case MTP_FORMAT_EXIF_JPEG:
            case MTP_FORMAT_HEIF:
            case MTP_FORMAT_JFIF: {
                ExifData *exifdata = exif_data_new_from_file(path);
                if (exifdata) {
                    if (exifdata->data) {
                        result = malloc(exifdata->size);
                        if (result) {
                            memcpy(result, exifdata->data, exifdata->size);
                            outThumbSize = exifdata->size;
                        }
                    }
                    exif_data_unref(exifdata);
                }
                break;
            }

            // See the above comment on getObjectInfo() method.
            case MTP_FORMAT_DNG:
            case MTP_FORMAT_TIFF:
            case MTP_FORMAT_TIFF_EP:
            case MTP_FORMAT_DEFINED: {
                std::unique_ptr<FileStream> stream(new FileStream(path));
                piex::PreviewImageData image_data;
                if (!GetExifFromRawImage(stream.get(), path, image_data)) {
                    // Couldn't parse EXIF data from a image file via piex.
                    break;
                }

                if (image_data.thumbnail.length == 0
                        || image_data.thumbnail.format != ::piex::Image::kJpegCompressed) {
                    // No thumbnail or non jpeg thumbnail.
                    break;
                }

                result = malloc(image_data.thumbnail.length);
                if (result) {
                    piex::Error err = stream.get()->GetData(
                            image_data.thumbnail.offset,
                            image_data.thumbnail.length,
                            (std::uint8_t *)result);
                    if (err == piex::Error::kOk) {
                        outThumbSize = image_data.thumbnail.length;
                    } else {
                        free(result);
                        result = NULL;
                    }
                }
                break;
            }
        }
    }

    return result;
}

MtpResponseCode MyMtpDatabase::getObjectFilePath(MtpObjectHandle handle,
                                                 MtpString& outFilePath,
                                                 int64_t& outFileLength,
                                                 MtpObjectFormat& outFormat) {
    // try to use sqlite directly query, if fail go to original flow
    if (getObjectFilePathFast(handle, outFilePath,
            outFileLength, outFormat) == MTP_RESPONSE_OK) {
        return MTP_RESPONSE_OK;
    }

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jint result = env->CallIntMethod(mDatabase, method_getObjectFilePath,
                (jint)handle, mStringBuffer, mLongBuffer);
    if (result != MTP_RESPONSE_OK) {
        checkAndClearExceptionFromCallback(env, __FUNCTION__);
        return result;
    }

    jchar* str = env->GetCharArrayElements(mStringBuffer, 0);
    outFilePath.setTo(reinterpret_cast<char16_t*>(str),
                      strlen16(reinterpret_cast<char16_t*>(str)));
    env->ReleaseCharArrayElements(mStringBuffer, str, 0);

    jlong* longValues = env->GetLongArrayElements(mLongBuffer, 0);
    outFileLength = longValues[0];
    outFormat = longValues[1];
    env->ReleaseLongArrayElements(mLongBuffer, longValues, 0);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

MtpResponseCode MyMtpDatabase::deleteFile(MtpObjectHandle handle) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    MtpResponseCode result = env->CallIntMethod(mDatabase, method_deleteFile, (jint)handle);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

struct PropertyTableEntry {
    MtpObjectProperty   property;
    int                 type;
};

static const PropertyTableEntry   kObjectPropertyTable[] = {
    {   MTP_PROPERTY_STORAGE_ID,        MTP_TYPE_UINT32     },
    {   MTP_PROPERTY_OBJECT_FORMAT,     MTP_TYPE_UINT16     },
    {   MTP_PROPERTY_PROTECTION_STATUS, MTP_TYPE_UINT16     },
    {   MTP_PROPERTY_OBJECT_SIZE,       MTP_TYPE_UINT64     },
    {   MTP_PROPERTY_OBJECT_FILE_NAME,  MTP_TYPE_STR        },
    {   MTP_PROPERTY_DATE_MODIFIED,     MTP_TYPE_STR        },
    {   MTP_PROPERTY_PARENT_OBJECT,     MTP_TYPE_UINT32     },
    {   MTP_PROPERTY_PERSISTENT_UID,    MTP_TYPE_UINT128    },
    {   MTP_PROPERTY_NAME,              MTP_TYPE_STR        },
    {   MTP_PROPERTY_DISPLAY_NAME,      MTP_TYPE_STR        },
    {   MTP_PROPERTY_DATE_ADDED,        MTP_TYPE_STR        },
    {   MTP_PROPERTY_ARTIST,            MTP_TYPE_STR        },
    {   MTP_PROPERTY_ALBUM_NAME,        MTP_TYPE_STR        },
    {   MTP_PROPERTY_ALBUM_ARTIST,      MTP_TYPE_STR        },
    {   MTP_PROPERTY_TRACK,             MTP_TYPE_UINT16     },
    {   MTP_PROPERTY_ORIGINAL_RELEASE_DATE, MTP_TYPE_STR    },
    {   MTP_PROPERTY_GENRE,             MTP_TYPE_STR        },
    {   MTP_PROPERTY_COMPOSER,          MTP_TYPE_STR        },
    {   MTP_PROPERTY_DURATION,          MTP_TYPE_UINT32     },
    {   MTP_PROPERTY_DESCRIPTION,       MTP_TYPE_STR        },
    {   MTP_PROPERTY_AUDIO_WAVE_CODEC,  MTP_TYPE_UINT32     },
    {   MTP_PROPERTY_BITRATE_TYPE,      MTP_TYPE_UINT16     },
    {   MTP_PROPERTY_AUDIO_BITRATE,     MTP_TYPE_UINT32     },
    {   MTP_PROPERTY_NUMBER_OF_CHANNELS,MTP_TYPE_UINT16     },
    {   MTP_PROPERTY_SAMPLE_RATE,       MTP_TYPE_UINT32     },
};

static const PropertyTableEntry   kDevicePropertyTable[] = {
    {   MTP_DEVICE_PROPERTY_SYNCHRONIZATION_PARTNER,    MTP_TYPE_STR },
    {   MTP_DEVICE_PROPERTY_DEVICE_FRIENDLY_NAME,       MTP_TYPE_STR },
    {   MTP_DEVICE_PROPERTY_IMAGE_SIZE,                 MTP_TYPE_STR },
    {   MTP_DEVICE_PROPERTY_BATTERY_LEVEL,              MTP_TYPE_UINT8 },
    {   MTP_DEVICE_PROPERTY_PERCEIVED_DEVICE_TYPE,      MTP_TYPE_UINT32 },
};

bool MyMtpDatabase::getObjectPropertyInfo(MtpObjectProperty property, int& type) {
    int count = sizeof(kObjectPropertyTable) / sizeof(kObjectPropertyTable[0]);
    const PropertyTableEntry* entry = kObjectPropertyTable;
    for (int i = 0; i < count; i++, entry++) {
        if (entry->property == property) {
            type = entry->type;
            return true;
        }
    }
    return false;
}

bool MyMtpDatabase::getDevicePropertyInfo(MtpDeviceProperty property, int& type) {
    int count = sizeof(kDevicePropertyTable) / sizeof(kDevicePropertyTable[0]);
    const PropertyTableEntry* entry = kDevicePropertyTable;
    for (int i = 0; i < count; i++, entry++) {
        if (entry->property == property) {
            type = entry->type;
            return true;
        }
    }
    return false;
}

MtpObjectHandleList* MyMtpDatabase::getObjectReferences(MtpObjectHandle handle) {
    // try to use sqlite directly query, if fail go to original flow
    MtpObjectHandleList* mylist = NULL;
    MtpResponseCode result = MTP_RESPONSE_OK;
    mylist = getObjectReferencesFast(handle, result);
    if (result == MTP_RESPONSE_OK)
        return mylist;

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jintArray array = (jintArray)env->CallObjectMethod(mDatabase, method_getObjectReferences,
                (jint)handle);
    if (!array)
        return NULL;
    MtpObjectHandleList* list = new MtpObjectHandleList();
    jint* handles = env->GetIntArrayElements(array, 0);
    jsize length = env->GetArrayLength(array);
    for (int i = 0; i < length; i++)
        list->push(handles[i]);
    env->ReleaseIntArrayElements(array, handles, 0);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return list;
}

MtpResponseCode MyMtpDatabase::setObjectReferences(MtpObjectHandle handle,
                                                   MtpObjectHandleList* references) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    int count = references->size();
    jintArray array = env->NewIntArray(count);
    if (!array) {
        ALOGE("out of memory in setObjectReferences");
        return false;
    }
    jint* handles = env->GetIntArrayElements(array, 0);
     for (int i = 0; i < count; i++)
        handles[i] = (*references)[i];
    env->ReleaseIntArrayElements(array, handles, 0);
    MtpResponseCode result = env->CallIntMethod(mDatabase, method_setObjectReferences,
                (jint)handle, array);
    env->DeleteLocalRef(array);

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

MtpProperty* MyMtpDatabase::getObjectPropertyDesc(MtpObjectProperty property,
                                                  MtpObjectFormat format) {
    static const int channelEnum[] = {
                                        1,  // mono
                                        2,  // stereo
                                        3,  // 2.1
                                        4,  // 3
                                        5,  // 3.1
                                        6,  // 4
                                        7,  // 4.1
                                        8,  // 5
                                        9,  // 5.1
                                    };
    static const int bitrateEnum[] = {
                                        1,  // fixed rate
                                        2,  // variable rate
                                     };

    MtpProperty* result = NULL;
    switch (property) {
        case MTP_PROPERTY_OBJECT_FORMAT:
            // use format as default value
            result = new MtpProperty(property, MTP_TYPE_UINT16, false, format);
            break;
        case MTP_PROPERTY_PROTECTION_STATUS:
        case MTP_PROPERTY_TRACK:
            result = new MtpProperty(property, MTP_TYPE_UINT16);
            break;
        case MTP_PROPERTY_STORAGE_ID:
        case MTP_PROPERTY_PARENT_OBJECT:
        case MTP_PROPERTY_DURATION:
        case MTP_PROPERTY_AUDIO_WAVE_CODEC:
            result = new MtpProperty(property, MTP_TYPE_UINT32);
            break;
        case MTP_PROPERTY_OBJECT_SIZE:
            result = new MtpProperty(property, MTP_TYPE_UINT64);
            break;
        case MTP_PROPERTY_PERSISTENT_UID:
            result = new MtpProperty(property, MTP_TYPE_UINT128);
            break;
        case MTP_PROPERTY_NAME:
        case MTP_PROPERTY_DISPLAY_NAME:
        case MTP_PROPERTY_ARTIST:
        case MTP_PROPERTY_ALBUM_NAME:
        case MTP_PROPERTY_ALBUM_ARTIST:
        case MTP_PROPERTY_GENRE:
        case MTP_PROPERTY_COMPOSER:
        case MTP_PROPERTY_DESCRIPTION:
            result = new MtpProperty(property, MTP_TYPE_STR);
            break;
        case MTP_PROPERTY_DATE_MODIFIED:
        case MTP_PROPERTY_DATE_ADDED:
        case MTP_PROPERTY_ORIGINAL_RELEASE_DATE:
            result = new MtpProperty(property, MTP_TYPE_STR);
            result->setFormDateTime();
            break;
        case MTP_PROPERTY_OBJECT_FILE_NAME:
            // We allow renaming files and folders
            result = new MtpProperty(property, MTP_TYPE_STR, true);
            break;
        case MTP_PROPERTY_BITRATE_TYPE:
             result = new MtpProperty(property, MTP_TYPE_UINT16);
            result->setFormEnum(bitrateEnum, sizeof(bitrateEnum)/sizeof(bitrateEnum[0]));
            break;
        case MTP_PROPERTY_AUDIO_BITRATE:
            result = new MtpProperty(property, MTP_TYPE_UINT32);
            result->setFormRange(1, 1536000, 1);
            break;
        case MTP_PROPERTY_NUMBER_OF_CHANNELS:
            result = new MtpProperty(property, MTP_TYPE_UINT16);
            result->setFormEnum(channelEnum, sizeof(channelEnum)/sizeof(channelEnum[0]));
            break;
        case MTP_PROPERTY_SAMPLE_RATE:
            result = new MtpProperty(property, MTP_TYPE_UINT32);
            result->setFormRange(8000, 48000, 1);
            break;
    }

    return result;
}

MtpProperty* MyMtpDatabase::getDevicePropertyDesc(MtpDeviceProperty property) {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    MtpProperty* result = NULL;
    bool writable = false;

    switch (property) {
        case MTP_DEVICE_PROPERTY_SYNCHRONIZATION_PARTNER:
        case MTP_DEVICE_PROPERTY_DEVICE_FRIENDLY_NAME:
            writable = true;
            // fall through
        case MTP_DEVICE_PROPERTY_IMAGE_SIZE: {
            result = new MtpProperty(property, MTP_TYPE_STR, writable);

            // get current value
            jint ret = env->CallIntMethod(mDatabase, method_getDeviceProperty,
                        (jint)property, mLongBuffer, mStringBuffer);
            if (ret == MTP_RESPONSE_OK) {
                jchar* str = env->GetCharArrayElements(mStringBuffer, 0);
                result->setCurrentValue(str);
                // for read-only properties it is safe to assume current value is default value
                if (!writable)
                    result->setDefaultValue(str);
                env->ReleaseCharArrayElements(mStringBuffer, str, 0);
            } else {
                ALOGE("unable to read device property, response: %04X", ret);
            }
            break;
        }
        case MTP_DEVICE_PROPERTY_BATTERY_LEVEL:
            result = new MtpProperty(property, MTP_TYPE_UINT8);
            result->setFormRange(0, env->GetIntField(mDatabase, field_batteryScale), 1);
            result->mCurrentValue.u.u8 = (uint8_t)env->GetIntField(mDatabase, field_batteryLevel);
            break;
        case MTP_DEVICE_PROPERTY_PERCEIVED_DEVICE_TYPE:
            result = new MtpProperty(property, MTP_TYPE_UINT32);
            result->mCurrentValue.u.u32 = (uint32_t)env->GetIntField(mDatabase, field_deviceType);
            break;
    }

    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return result;
}

void MyMtpDatabase::sessionStarted() {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mDatabase, method_sessionStarted);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

void MyMtpDatabase::sessionEnded() {
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mDatabase, method_sessionEnded);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}


// ----------------------------------------------------------------------------

// for mtp enhancement
// sqlite fast query method, add as MyMtpDatabase internal function
const char* getSqlColumn(int code) {
     switch (code) {
        case MTP_PROPERTY_STORAGE_ID:
            return (MTP_COLUMN_STORAGE_ID);
         case MTP_PROPERTY_OBJECT_FORMAT:
            return (MTP_COLUMN_OBJECT_FORMAT);
        case MTP_PROPERTY_PROTECTION_STATUS:
            // protection status is always 0
            break;
        case MTP_PROPERTY_OBJECT_SIZE:
            return (MTP_COLUMN_OBJECT_SIZE);
        case MTP_PROPERTY_OBJECT_FILE_NAME:
            return (MTP_COLUMN_OBJECT_FILE_NAME);
        case MTP_PROPERTY_NAME:
            return (MTP_COLUMN_NAME);
        case MTP_PROPERTY_DATE_MODIFIED:
            return (MTP_COLUMN_DATE_MODIFIED);
        case MTP_PROPERTY_DATE_ADDED:
            return (MTP_COLUMN_DATE_ADDED);
        case MTP_PROPERTY_ORIGINAL_RELEASE_DATE:
            return (MTP_COLUMN_ORIGINAL_RELEASE_DATE);
        case MTP_PROPERTY_PARENT_OBJECT:
            return (MTP_COLUMN_PARENT_OBJECT);
        case MTP_PROPERTY_PERSISTENT_UID:
            // PUID is concatenation of storageID and object handle
            return (MTP_COLUMN_STORAGE_ID);
        case MTP_PROPERTY_DURATION:
            return (MTP_COLUMN_DURATION);
        case MTP_PROPERTY_TRACK:
            return (MTP_COLUMN_TRACK);
        case MTP_PROPERTY_DISPLAY_NAME:
            return (MTP_COLUMN_DISPLAY_NAME);
        case MTP_PROPERTY_ARTIST:
            return (MTP_COLUMN_ARTIST);
        case MTP_PROPERTY_ALBUM_NAME:
            return (MTP_COLUMN_ALBUM);
        case MTP_PROPERTY_ALBUM_ARTIST:
            return (MTP_COLUMN_ALBUM_ARTIST);
        case MTP_PROPERTY_GENRE:
            // genre requires a special query
            // to be fixed, not implemented now
            // just use name
            return (MTP_COLUMN_NAME);
        case MTP_PROPERTY_COMPOSER:
            return (MTP_COLUMN_COMPOSER);
        case MTP_PROPERTY_DESCRIPTION:
            return (MTP_COLUMN_DESCRIPTION);
        case MTP_PROPERTY_AUDIO_WAVE_CODEC:
        case MTP_PROPERTY_AUDIO_BITRATE:
        case MTP_PROPERTY_SAMPLE_RATE:
            // these are special cased
        case MTP_PROPERTY_BITRATE_TYPE:
        case MTP_PROPERTY_NUMBER_OF_CHANNELS:
            // these are special cased
        default:
            ALOGE("unsupported property 0x%x", code);
            break;
    }
    return NULL;
}

void MyMtpDatabase::openDataBase(const char *path) {
    // open database
    int rv;
    rv = sqlite3_open(path, &mMydb);
    if (rv != SQLITE_OK) {
        ALOGE("Cannot open database: %s\n", sqlite3_errmsg(mMydb));
        sqlite3_close(mMydb);
        mMydb = NULL;
        return;
    }

    // Set the default busy handler to retry automatically before returning SQLITE_BUSY.
    rv = sqlite3_busy_timeout(mMydb, QUERY_BUSY_TIMEOUT_MS);
    if (rv != SQLITE_OK) {
        ALOGE("Could not set busy timeout");
        sqlite3_close(mMydb);
        mMydb = NULL;
        return;
    }
    ALOGV("openDataBase success");
}

void MyMtpDatabase::closeDataBase() {
    if (mMydb != NULL) {
        sqlite3_close(mMydb);
        mMydb = NULL;
        ALOGV("sqlite3_close");
    }
    propCodeList.clear();
    columnIndex.clear();
}

MtpResponseCode MyMtpDatabase::getObjectFilePathFast(MtpObjectHandle handle,
                                                        MtpString& outFilePath,
                                                        int64_t& outFileLength,
                                                        MtpObjectFormat& outFormat) {
    MtpResponseCode result = MTP_RESPONSE_OK;
    String8 sqlcmd;
    sqlite3_stmt * stmt = NULL;
    int rc = SQLITE_OK;

    // check mMydb is available or not
    if (mMydb == NULL)
        return MTP_RESPONSE_LOCAL_CALL_FAIL;

    // set sql cmd
    sqlcmd.clear();
    sqlcmd.appendFormat("SELECT _id, _data, format FROM files WHERE (_id=%d)", handle);
    //-------------------------0-----1-----2------------------------------------

    // query and get data
    rc = sqlite3_prepare_v2(mMydb, sqlcmd.string(), -1, &stmt, 0);
    if (rc == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            const char *temppath = (const char *)sqlite3_column_text(stmt,1);
            int tempformat = sqlite3_column_int(stmt, 2);
            struct stat statbuf;
            stat(temppath,&statbuf);
            int size=statbuf.st_size;

            outFilePath.setTo(temppath);;
            outFormat = tempformat;
            outFileLength = size;
        }
        sqlite3_finalize(stmt);
    } else {
        ALOGE("%s: %s\n", sqlcmd.string(), sqlite3_errmsg(mMydb));
        sqlite3_close(mMydb);
        mMydb = NULL;
        result = MTP_RESPONSE_LOCAL_CALL_FAIL;
    }

    return result;
}

MtpObjectHandleList* MyMtpDatabase::getObjectReferencesFast(MtpObjectHandle handle, MtpResponseCode& result) {
    String8 sqlcmd;
    sqlite3_stmt * stmt = NULL;
    int rc = SQLITE_OK;
    int playlistId = 0;
    int mediatype = -1;

    // check mMydb is available or not
    if (mMydb == NULL) {
        result = MTP_RESPONSE_LOCAL_CALL_FAIL;
        return NULL;
    }

    // set sql cmd to get media type
    sqlcmd.clear();
    sqlcmd.appendFormat("SELECT _id,media_type FROM files WHERE (_id=%d)", handle);
    //-------------------------0-----1--------------------------------------

    // query and get data
    rc = sqlite3_prepare_v2(mMydb, sqlcmd.string(), -1, &stmt, 0);
    if (rc == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            playlistId = sqlite3_column_int(stmt, 0);
            mediatype = sqlite3_column_int(stmt, 1);
        }
        sqlite3_finalize(stmt);
    } else {
        ALOGE("%s: %s\n", sqlcmd.string(), sqlite3_errmsg(mMydb));
        sqlite3_close(mMydb);
        mMydb = NULL;
        result = MTP_RESPONSE_LOCAL_CALL_FAIL;
        return NULL;
    }

    if (mediatype != MTP_MEDIA_TYPE_PLAYLIST) {
        // we only support object references for playlist objects
        // return reponse ok in this case
        ALOGV("mediatype != playlist return null");
        result = MTP_RESPONSE_OK;
        return NULL;
    } else {
        MtpObjectHandleList* list = new MtpObjectHandleList();
        String8 OBJECT_REFERENCES_QUERY;
        OBJECT_REFERENCES_QUERY.clear();
        OBJECT_REFERENCES_QUERY.appendFormat("SELECT audio_id FROM audio_playlists_map \
            WHERE playlist_id=%d ORDER BY play_order", playlistId);

        // query audio_id and get data
        rc = sqlite3_prepare_v2(mMydb, OBJECT_REFERENCES_QUERY.string(), -1, &stmt, 0);
        if (rc == SQLITE_OK) {
            while (sqlite3_step(stmt) == SQLITE_ROW) {
                int audioId = sqlite3_column_int(stmt, 0);
                list->push(audioId);
            }
            sqlite3_finalize(stmt);
        } else {
            ALOGE("%s: %s\n", sqlcmd.string(), sqlite3_errmsg(mMydb));
            sqlite3_close(mMydb);
            mMydb = NULL;
            if(list != NULL)
                delete list;
            result = MTP_RESPONSE_LOCAL_CALL_FAIL;
            return NULL;
        }
        // result ok
        result = MTP_RESPONSE_OK;
        return list;
    }
}

void MyMtpDatabase::getSupportedObjectPropertiesFast(int format) {
    int count = 0, i = 0;
    switch (format) {
        case MTP_FORMAT_MP3:
        case MTP_FORMAT_WAV:
        case MTP_FORMAT_WMA:
        case MTP_FORMAT_OGG:
        case MTP_FORMAT_AAC:
            count = sizeof(AUDIO_PROPERTIES)/sizeof(int);
            for (i = 0; i < count; i++) {
                propCodeList.push(AUDIO_PROPERTIES[i]);
            }
            break;
        case MTP_FORMAT_MPEG:
        case MTP_FORMAT_3GP_CONTAINER:
        case MTP_FORMAT_WMV:
            count = sizeof(VIDEO_PROPERTIES)/sizeof(int);
            for (i = 0; i < count; i++) {
                propCodeList.push(VIDEO_PROPERTIES[i]);
            }
            break;
        case MTP_FORMAT_EXIF_JPEG:
        case MTP_FORMAT_GIF:
        case MTP_FORMAT_PNG:
        case MTP_FORMAT_BMP:
        case MTP_FORMAT_DNG:
        case MTP_FORMAT_HEIF:
            count = sizeof(IMAGE_PROPERTIES)/sizeof(int);
            for (i = 0; i < count; i++) {
                propCodeList.push(IMAGE_PROPERTIES[i]);
            }

            break;
        default:
            count = sizeof(FILE_PROPERTIES)/sizeof(int);
            for (i = 0; i < count; i++) {
                propCodeList.push(FILE_PROPERTIES[i]);
            }

    }
    return;
}

int MyMtpDatabase::getObjectFormat(int handle) {
    MtpResponseCode result = MTP_RESPONSE_OK;
    String8 sqlcmd;
    sqlite3_stmt * stmt = NULL;
    int rc = SQLITE_OK;
    int format = -1;

    // check mMydb is available or not
    if (mMydb == NULL)
        return -1;

    // set sql cmd
    sqlcmd.clear();
    sqlcmd.appendFormat("SELECT format FROM files WHERE (_id=%d)", handle);
    //---------------------------0------------------------------------

    // query and get data
    rc = sqlite3_prepare_v2(mMydb, sqlcmd.string(), -1, &stmt, 0);
    if (rc == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            format = sqlite3_column_int(stmt, 0);
        }
        sqlite3_finalize(stmt);
    } else {
        ALOGE("sqlite3_prepare_v2 fail sqlcmd:%s, error:%s\n", sqlcmd.string(), sqlite3_errmsg(mMydb));
        sqlite3_close(mMydb);
        mMydb = NULL;
        result = MTP_RESPONSE_LOCAL_CALL_FAIL;
    }
    return format;
}

// reference to mtpdatabase.java
MtpResponseCode MyMtpDatabase::getObjectPropertyListFast(MtpObjectHandle handle,
                                                            uint32_t format, uint32_t property,
                                                            int groupCode, int depth,
                                                            MtpDataPacket& packet) {
    MtpResponseCode result = MTP_RESPONSE_OK;
    String8 sqlcmd;
    String8 columns;
    sqlite3_stmt * stmt = NULL;
    int rc;
    int proplistcnt = 0;

    // check group code
    if (groupCode != 0) {
        packet.putUInt32(0);
        ALOGE("groupCode != 0 not support");
        return MTP_RESPONSE_GROUP_NOT_SUPPORTED;
    }

    // check depth
    if (depth > 1) {
        ALOGE("depth > 1 not support");
        // to be fiexed
        packet.putUInt32(0);
        return MTP_RESPONSE_SPECIFICATION_BY_GROUP_UNSUPPORTED;
    }

    // check mMydb is available or not
    if (mMydb == NULL)
        return MTP_RESPONSE_LOCAL_CALL_FAIL;

    columns.clear();
    sqlcmd.clear();
    propCodeList.clear();
    columnIndex.clear();

    if (property == 0xffffffff) {
        if (format == 0 && handle != 0 && handle != 0xffffffff) {
            // return properties based on the object's format
            format = getObjectFormat(handle);
        }
        getSupportedObjectPropertiesFast(format);
    } else {
        propCodeList.push(property);
    }

    proplistcnt = propCodeList.size();

    // create columns
    int realColumnIndex = 0;
    for (size_t i = 0; i< propCodeList.size();i++) {
        const char *temp = getSqlColumn(propCodeList[i]);
        if (temp != NULL) {
            if(i > 0)
                columns.append(",");
            columns.append(temp);
            columnIndex.push(realColumnIndex);
            realColumnIndex++;
        } else {
            // mark -1 as column none
            columnIndex.push(-1);
        }
    }

    // create sqlcmd
    if (format == 0) {
        if (handle == 0xFFFFFFFF) {
            // select all objects
            sqlcmd.appendFormat("SELECT %s FROM files", columns.string());
        } else {
            if (depth == 1) {
                sqlcmd.appendFormat("SELECT %s FROM files WHERE (parent=%d)", columns.string(), handle);
            } else {
                sqlcmd.appendFormat("SELECT %s FROM files WHERE (_id=%d)", columns.string(), handle);
            }
        }
    } else {
        if (handle == 0xFFFFFFFF) {
            // select all objects with given format
            sqlcmd.appendFormat("SELECT %s FROM files WHERE (format=%d)", columns.string(), format);
        } else {
            if (depth == 1) {
                sqlcmd.appendFormat("SELECT %s FROM files WHERE parent=%d AND format=%d",
                    columns.string(), handle, format);
            } else {
                sqlcmd.appendFormat("SELECT %s FROM files WHERE _id=%d AND format=%d",
                    columns.string(), handle, format);
            }
        }
    }
    ALOGV("sqlcmd = %s", sqlcmd.string());

    rc = sqlite3_prepare_v2(mMydb, sqlcmd.string(), -1, &stmt, 0);
    if (rc == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            int64_t value = 0;
            int type = 0;
            char    date[20];
            char *str = NULL;
            String8 entryName;
            String8 leaf;

            packet.putUInt32(proplistcnt);
            for (int j = 0; j < proplistcnt; j++) {
                packet.putUInt32(handle);
                packet.putUInt16(propCodeList[j]);
                switch(propCodeList[j]) {
                    case MTP_PROPERTY_STORAGE_ID:
                    case MTP_PROPERTY_DURATION:
                    case MTP_PROPERTY_PARENT_OBJECT:
                        value = sqlite3_column_int(stmt, columnIndex[j]);
                        type = MTP_TYPE_UINT32;
                        break;
                    case MTP_PROPERTY_TRACK:
                        value = sqlite3_column_int(stmt, columnIndex[j]) % 1000;
                        type = MTP_TYPE_UINT16;
                        break;
                    case MTP_PROPERTY_OBJECT_FORMAT:
                        value = sqlite3_column_int(stmt, columnIndex[j]);
                        type = MTP_TYPE_UINT16;
                        break;
                    case MTP_PROPERTY_PROTECTION_STATUS:
                        // protection status is always 0
                        value = 0;
                        type = MTP_TYPE_UINT16;
                        break;
                    case MTP_PROPERTY_OBJECT_SIZE:
                        value = sqlite3_column_int(stmt, columnIndex[j]);
                        type = MTP_TYPE_UINT64;
                        break;
                    case MTP_PROPERTY_DATE_MODIFIED:
                    case MTP_PROPERTY_DATE_ADDED:
                        value = sqlite3_column_int(stmt, columnIndex[j]);
                        formatDateTime(value, date, sizeof(date));
                        type = MTP_TYPE_STR;
                        str = date;
                        break;
                    case MTP_PROPERTY_OBJECT_FILE_NAME:
                        str = (char *)sqlite3_column_text(stmt, columnIndex[j]);
                        entryName.setPathName((const char*)str);
                        leaf = entryName.getPathLeaf();
                        str = (char *)leaf.string();
                        type = MTP_TYPE_STR;
                        break;
                    case MTP_PROPERTY_PERSISTENT_UID:
                        value = sqlite3_column_int(stmt, columnIndex[j]);
                        value <<= 32;
                        value += handle;
                        type = MTP_TYPE_UINT128;
                        break;
                    case MTP_PROPERTY_NAME:
                        str = (char *)sqlite3_column_text(stmt, columnIndex[j]);
                        type = MTP_TYPE_STR;
                        if (str == NULL)
                            str = (char *)leaf.string();
                        break;
                    case MTP_PROPERTY_ARTIST:
                    case MTP_PROPERTY_ALBUM_NAME:
                    case MTP_PROPERTY_DISPLAY_NAME:
                    case MTP_PROPERTY_COMPOSER:
                    case MTP_PROPERTY_DESCRIPTION:
                    case MTP_PROPERTY_GENRE:
                        str = (char *)sqlite3_column_text(stmt, columnIndex[j]);
                        type = MTP_TYPE_STR;
                        break;
                    case MTP_PROPERTY_ORIGINAL_RELEASE_DATE:
                        value = sqlite3_column_int(stmt, columnIndex[j]);
                        entryName.clear();
                        entryName.appendFormat("%d0101T000000", (int)value);
                        str = (char *)entryName.string();
                        type = MTP_TYPE_STR;
                        break;
                    case MTP_PROPERTY_AUDIO_WAVE_CODEC:
                    case MTP_PROPERTY_AUDIO_BITRATE:
                    case MTP_PROPERTY_SAMPLE_RATE:
                        // we don't have these in our database, so return 0
                        type = MTP_TYPE_UINT32;
                        value = 0;
                        break;
                    case MTP_PROPERTY_BITRATE_TYPE:
                    case MTP_PROPERTY_NUMBER_OF_CHANNELS:
                        // we don't have these in our database, so return 0
                        type = MTP_TYPE_UINT16;
                        value = 0;
                        break;

                }
                // type
                packet.putUInt16(type);
                switch (type) {
                    case MTP_TYPE_INT8:
                        packet.putInt8(value);
                        break;
                    case MTP_TYPE_UINT8:
                        packet.putUInt8(value);
                        break;
                    case MTP_TYPE_INT16:
                        packet.putInt16(value);
                        break;
                    case MTP_TYPE_UINT16:
                        packet.putUInt16(value);
                        break;
                    case MTP_TYPE_INT32:
                        packet.putInt32(value);
                        break;
                    case MTP_TYPE_UINT32:
                        packet.putUInt32(value);
                        break;
                    case MTP_TYPE_INT64:
                        packet.putInt64(value);
                        break;
                    case MTP_TYPE_UINT64:
                        packet.putUInt64(value);
                        break;
                    case MTP_TYPE_INT128:
                        packet.putInt128(value);
                        break;
                    case MTP_TYPE_UINT128:
                        packet.putUInt128(value);
                        break;
                    case MTP_TYPE_STR: {
                        if (str) {
                            packet.putString(str);
                        } else {
                            packet.putEmptyString();
                        }
                        break;
                    }
                    default:
                        ALOGE("bad or unsupported data type in MyMtpDatabase::getObjectPropertyListFast");
                        result = MTP_RESPONSE_LOCAL_CALL_FAIL;
                        packet.reset();
                        break;
                }
            }
        }

        sqlite3_finalize(stmt);

    } else  {
        ALOGE("sqlite3_prepare_v2 fail sqlcmd:%s, error:%s\n", sqlcmd.string(), sqlite3_errmsg(mMydb));
        sqlite3_close(mMydb);
        mMydb = NULL;
        result = MTP_RESPONSE_LOCAL_CALL_FAIL;
    }
    return result;
}

MtpResponseCode MyMtpDatabase::getObjectInfoFast(MtpObjectHandle handle,
                                                    MtpObjectInfo& info) {
    MtpString       path;
    int64_t         length = 0;
    MtpObjectFormat format;
    MtpResponseCode result = MTP_RESPONSE_OK;
    String8 sqlcmd;
    sqlite3_stmt * stmt = NULL;
    int rc = SQLITE_OK;

    // check mMydb is available or not
    if (mMydb == NULL)
        return MTP_RESPONSE_LOCAL_CALL_FAIL;

    result = getObjectFilePathFast(handle, path, length, format);
    if (result != MTP_RESPONSE_OK) {
        return result;
    }

    sqlcmd.clear();
    sqlcmd.appendFormat(
        "SELECT _id, storage_id, format, parent, _data, \
        date_added, date_modified FROM files WHERE (_id=%d)", handle);

    // query object info from external db
    rc = sqlite3_prepare_v2(mMydb, sqlcmd.string(), -1, &stmt, 0);
    if (rc == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            int temp_storage_id = sqlite3_column_int(stmt, 1);
            int temp_format = sqlite3_column_int(stmt, 2);
            int temp_parent = sqlite3_column_int(stmt, 3);
            const char *temp_data = (const char *)sqlite3_column_text(stmt,4);
            String8 entryName;
            entryName.setPathName(temp_data);
            const String8 leaf(entryName.getPathLeaf());
            time_t temp_createtime = (long)sqlite3_column_int(stmt, 5);
            time_t temp_modifytime = (long)sqlite3_column_int(stmt, 6);
            // use modification date as creation date if date added is not set
            if (temp_createtime == 0) {
                temp_createtime = temp_modifytime;
            }

            info.mStorageID = temp_storage_id;
            info.mFormat = temp_format;
            info.mParent = temp_parent;
            info.mDateCreated = temp_createtime;
            info.mDateModified = temp_modifytime;
            info.mName = strdup((const char *)leaf);
        }
        sqlite3_finalize(stmt);

    } else {
        ALOGE("sqlite3_prepare_v2 fail sqlcmd %s error %s\n", sqlcmd.string(), sqlite3_errmsg(mMydb));
        sqlite3_close(mMydb);
        mMydb = NULL;
        return MTP_RESPONSE_LOCAL_CALL_FAIL;
    }

    info.mCompressedSize = (length > 0xFFFFFFFFLL ? 0xFFFFFFFF : (uint32_t)length);
    if ((false)) {
        info.mAssociationType = (format == MTP_FORMAT_ASSOCIATION ?
                                MTP_ASSOCIATION_TYPE_GENERIC_FOLDER :
                                MTP_ASSOCIATION_TYPE_UNDEFINED);
    }
    info.mAssociationType = MTP_ASSOCIATION_TYPE_UNDEFINED;

    // read EXIF data for thumbnail information
    switch (info.mFormat) {
        case MTP_FORMAT_EXIF_JPEG:
        case MTP_FORMAT_HEIF:
        case MTP_FORMAT_JFIF: {
            ExifData *exifdata = exif_data_new_from_file(path);
            if (exifdata) {
                if ((false)) {
                    exif_data_foreach_content(exifdata, foreachcontent, NULL);
                }

                ExifEntry *w = exif_content_get_entry(
                        exifdata->ifd[EXIF_IFD_EXIF], EXIF_TAG_PIXEL_X_DIMENSION);
                ExifEntry *h = exif_content_get_entry(
                        exifdata->ifd[EXIF_IFD_EXIF], EXIF_TAG_PIXEL_Y_DIMENSION);
                info.mThumbCompressedSize = exifdata->data ? exifdata->size : 0;
                info.mThumbFormat = MTP_FORMAT_EXIF_JPEG;
                info.mImagePixWidth = w ? getLongFromExifEntry(w) : 0;
                info.mImagePixHeight = h ? getLongFromExifEntry(h) : 0;
                exif_data_unref(exifdata);
            }
            break;
        }

        // Except DNG, all supported RAW image formats are not defined in PTP 1.2 specification.
        // Most of RAW image formats are based on TIFF or TIFF/EP. To render Fuji's RAF format,
        // it checks MTP_FORMAT_DEFINED case since it's designed as a custom format.
        case MTP_FORMAT_DNG:
        case MTP_FORMAT_TIFF:
        case MTP_FORMAT_TIFF_EP:
        case MTP_FORMAT_DEFINED: {
            std::unique_ptr<FileStream> stream(new FileStream(path));
            piex::PreviewImageData image_data;
            if (!GetExifFromRawImage(stream.get(), path, image_data)) {
                // Couldn't parse EXIF data from a image file via piex.
                break;
            }

            info.mThumbCompressedSize = image_data.thumbnail.length;
            info.mThumbFormat = MTP_FORMAT_EXIF_JPEG;
            info.mImagePixWidth = image_data.full_width;
            info.mImagePixHeight = image_data.full_height;

            break;
        }
    }

    return MTP_RESPONSE_OK;
}

// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------

static void
android_mtp_MtpDatabase_setup(JNIEnv *env, jobject thiz)
{
    MyMtpDatabase* database = new MyMtpDatabase(env, thiz);
    env->SetLongField(thiz, field_context, (jlong)database);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void
android_mtp_MtpDatabase_finalize(JNIEnv *env, jobject thiz)
{
    MyMtpDatabase* database = (MyMtpDatabase *)env->GetLongField(thiz, field_context);
    database->cleanup(env);
    delete database;
    env->SetLongField(thiz, field_context, 0);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static jstring
android_mtp_MtpPropertyGroup_format_date_time(JNIEnv *env, jobject /*thiz*/, jlong seconds)
{
    char    date[20];
    formatDateTime(seconds, date, sizeof(date));
    return env->NewStringUTF(date);
}

// ----------------------------------------------------------------------------

static const JNINativeMethod gMtpDatabaseMethods[] = {
    {"native_setup",            "()V",  (void *)android_mtp_MtpDatabase_setup},
    {"native_finalize",         "()V",  (void *)android_mtp_MtpDatabase_finalize},
};

static const JNINativeMethod gMtpPropertyGroupMethods[] = {
    {"format_date_time",        "(J)Ljava/lang/String;",
                                        (void *)android_mtp_MtpPropertyGroup_format_date_time},
};

int register_android_mtp_MtpDatabase(JNIEnv *env)
{
    jclass clazz;

    clazz = env->FindClass("android/mtp/MtpDatabase");
    if (clazz == NULL) {
        ALOGE("Can't find android/mtp/MtpDatabase");
        return -1;
    }
    method_beginSendObject = env->GetMethodID(clazz, "beginSendObject", "(Ljava/lang/String;IIIJJ)I");
    if (method_beginSendObject == NULL) {
        ALOGE("Can't find beginSendObject");
        return -1;
    }
    method_endSendObject = env->GetMethodID(clazz, "endSendObject", "(Ljava/lang/String;IIZ)V");
    if (method_endSendObject == NULL) {
        ALOGE("Can't find endSendObject");
        return -1;
    }
    method_getObjectList = env->GetMethodID(clazz, "getObjectList", "(III)[I");
    if (method_getObjectList == NULL) {
        ALOGE("Can't find getObjectList");
        return -1;
    }
    method_getNumObjects = env->GetMethodID(clazz, "getNumObjects", "(III)I");
    if (method_getNumObjects == NULL) {
        ALOGE("Can't find getNumObjects");
        return -1;
    }
    method_getSupportedPlaybackFormats = env->GetMethodID(clazz, "getSupportedPlaybackFormats", "()[I");
    if (method_getSupportedPlaybackFormats == NULL) {
        ALOGE("Can't find getSupportedPlaybackFormats");
        return -1;
    }
    method_getSupportedCaptureFormats = env->GetMethodID(clazz, "getSupportedCaptureFormats", "()[I");
    if (method_getSupportedCaptureFormats == NULL) {
        ALOGE("Can't find getSupportedCaptureFormats");
        return -1;
    }
    method_getSupportedObjectProperties = env->GetMethodID(clazz, "getSupportedObjectProperties", "(I)[I");
    if (method_getSupportedObjectProperties == NULL) {
        ALOGE("Can't find getSupportedObjectProperties");
        return -1;
    }
    method_getSupportedDeviceProperties = env->GetMethodID(clazz, "getSupportedDeviceProperties", "()[I");
    if (method_getSupportedDeviceProperties == NULL) {
        ALOGE("Can't find getSupportedDeviceProperties");
        return -1;
    }
    method_setObjectProperty = env->GetMethodID(clazz, "setObjectProperty", "(IIJLjava/lang/String;)I");
    if (method_setObjectProperty == NULL) {
        ALOGE("Can't find setObjectProperty");
        return -1;
    }
    method_getDeviceProperty = env->GetMethodID(clazz, "getDeviceProperty", "(I[J[C)I");
    if (method_getDeviceProperty == NULL) {
        ALOGE("Can't find getDeviceProperty");
        return -1;
    }
    method_setDeviceProperty = env->GetMethodID(clazz, "setDeviceProperty", "(IJLjava/lang/String;)I");
    if (method_setDeviceProperty == NULL) {
        ALOGE("Can't find setDeviceProperty");
        return -1;
    }
    method_getObjectPropertyList = env->GetMethodID(clazz, "getObjectPropertyList",
            "(IIIII)Landroid/mtp/MtpPropertyList;");
    if (method_getObjectPropertyList == NULL) {
        ALOGE("Can't find getObjectPropertyList");
        return -1;
    }
    method_getObjectInfo = env->GetMethodID(clazz, "getObjectInfo", "(I[I[C[J)Z");
    if (method_getObjectInfo == NULL) {
        ALOGE("Can't find getObjectInfo");
        return -1;
    }
    method_getObjectFilePath = env->GetMethodID(clazz, "getObjectFilePath", "(I[C[J)I");
    if (method_getObjectFilePath == NULL) {
        ALOGE("Can't find getObjectFilePath");
        return -1;
    }
    method_deleteFile = env->GetMethodID(clazz, "deleteFile", "(I)I");
    if (method_deleteFile == NULL) {
        ALOGE("Can't find deleteFile");
        return -1;
    }
    method_getObjectReferences = env->GetMethodID(clazz, "getObjectReferences", "(I)[I");
    if (method_getObjectReferences == NULL) {
        ALOGE("Can't find getObjectReferences");
        return -1;
    }
    method_setObjectReferences = env->GetMethodID(clazz, "setObjectReferences", "(I[I)I");
    if (method_setObjectReferences == NULL) {
        ALOGE("Can't find setObjectReferences");
        return -1;
    }
    method_sessionStarted = env->GetMethodID(clazz, "sessionStarted", "()V");
    if (method_sessionStarted == NULL) {
        ALOGE("Can't find sessionStarted");
        return -1;
    }
    method_sessionEnded = env->GetMethodID(clazz, "sessionEnded", "()V");
    if (method_sessionEnded == NULL) {
        ALOGE("Can't find sessionEnded");
        return -1;
    }

    field_context = env->GetFieldID(clazz, "mNativeContext", "J");
    if (field_context == NULL) {
        ALOGE("Can't find MtpDatabase.mNativeContext");
        return -1;
    }
    field_batteryLevel = env->GetFieldID(clazz, "mBatteryLevel", "I");
    if (field_batteryLevel == NULL) {
        ALOGE("Can't find MtpDatabase.mBatteryLevel");
        return -1;
    }
    field_batteryScale = env->GetFieldID(clazz, "mBatteryScale", "I");
    if (field_batteryScale == NULL) {
        ALOGE("Can't find MtpDatabase.mBatteryScale");
        return -1;
    }
    field_deviceType = env->GetFieldID(clazz, "mDeviceType", "I");
    if (field_deviceType == NULL) {
        ALOGE("Can't find MtpDatabase.mDeviceType");
        return -1;
    }

    // now set up fields for MtpPropertyList class
    clazz = env->FindClass("android/mtp/MtpPropertyList");
    if (clazz == NULL) {
        ALOGE("Can't find android/mtp/MtpPropertyList");
        return -1;
    }
    field_mCount = env->GetFieldID(clazz, "mCount", "I");
    if (field_mCount == NULL) {
        ALOGE("Can't find MtpPropertyList.mCount");
        return -1;
    }
    field_mResult = env->GetFieldID(clazz, "mResult", "I");
    if (field_mResult == NULL) {
        ALOGE("Can't find MtpPropertyList.mResult");
        return -1;
    }
    field_mObjectHandles = env->GetFieldID(clazz, "mObjectHandles", "[I");
    if (field_mObjectHandles == NULL) {
        ALOGE("Can't find MtpPropertyList.mObjectHandles");
        return -1;
    }
    field_mPropertyCodes = env->GetFieldID(clazz, "mPropertyCodes", "[I");
    if (field_mPropertyCodes == NULL) {
        ALOGE("Can't find MtpPropertyList.mPropertyCodes");
        return -1;
    }
    field_mDataTypes = env->GetFieldID(clazz, "mDataTypes", "[I");
    if (field_mDataTypes == NULL) {
        ALOGE("Can't find MtpPropertyList.mDataTypes");
        return -1;
    }
    field_mLongValues = env->GetFieldID(clazz, "mLongValues", "[J");
    if (field_mLongValues == NULL) {
        ALOGE("Can't find MtpPropertyList.mLongValues");
        return -1;
    }
    field_mStringValues = env->GetFieldID(clazz, "mStringValues", "[Ljava/lang/String;");
    if (field_mStringValues == NULL) {
        ALOGE("Can't find MtpPropertyList.mStringValues");
        return -1;
    }

    if (AndroidRuntime::registerNativeMethods(env,
                "android/mtp/MtpDatabase", gMtpDatabaseMethods, NELEM(gMtpDatabaseMethods)))
        return -1;

    return AndroidRuntime::registerNativeMethods(env,
                "android/mtp/MtpPropertyGroup", gMtpPropertyGroupMethods, NELEM(gMtpPropertyGroupMethods));
}
