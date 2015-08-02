LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 guava

LOCAL_PACKAGE_NAME := DocumentsUI
LOCAL_CERTIFICATE := platform
LOCAL_AAPT_INCLUDE_ALL_RESOURCES := true

include $(BUILD_PACKAGE)
