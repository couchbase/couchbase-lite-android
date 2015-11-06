LOCAL_PATH := $(call my-dir)


include $(CLEAR_VARS)
LOCAL_MODULE    := libicuuc
LOCAL_CFLAGS 	:= -DUCONFIG_ONLY_COLLATION
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libicuuc_static.a
include $(PREBUILT_STATIC_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE    := libicui18n
LOCAL_CFLAGS 	:= -DUCONFIG_ONLY_COLLATION
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libicui18n_static.a
include $(PREBUILT_STATIC_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE    := SQLiteJsonCollator
LOCAL_SRC_FILES := JsonCollator/com_couchbase_lite_android_SQLiteJsonCollator.cpp
LOCAL_C_INCLUDES := include/icu4c/common \
					include/icu4c/i18n
LOCAL_CFLAGS 	:= -DUCONFIG_ONLY_COLLATION
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/JsonCollator)
LOCAL_STATIC_LIBRARIES := libicui18n libicuuc
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE    := SQLiteRevCollator
LOCAL_SRC_FILES := RevCollator/com_couchbase_lite_android_SQLiteRevCollator.cpp
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/RevCollator)
include $(BUILD_SHARED_LIBRARY)