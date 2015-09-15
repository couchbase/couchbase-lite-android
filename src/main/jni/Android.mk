LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := SQLiteJsonCollator
LOCAL_SRC_FILES := JsonCollator/com_couchbase_lite_android_SQLiteJsonCollator.cpp
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/JsonCollator)
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := SQLiteRevCollator
LOCAL_SRC_FILES := RevCollator/com_couchbase_lite_android_SQLiteRevCollator.cpp
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/RevCollator)
include $(BUILD_SHARED_LIBRARY)