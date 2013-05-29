LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := com_couchbase_touchdb_TDCollateJSON
LOCAL_SRC_FILES := com_couchbase_touchdb_TDCollateJSON.cpp
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH)

include $(BUILD_SHARED_LIBRARY)