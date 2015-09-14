LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := com_couchbase_touchdb_TDCollateJSON
LOCAL_SRC_FILES := JsonCollator/com_couchbase_touchdb_TDCollateJSON.cpp
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/JsonCollator)
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := com_couchbase_touchdb_RevCollator
LOCAL_SRC_FILES := RevCollator/com_couchbase_touchdb_RevCollator.cpp
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/RevCollator)
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := com_couchbase_touchdb_TDCollateJSON_util
LOCAL_SRC_FILES := util/com_couchbase_touchdb_TDCollateJSON_util.c
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/util)
LOCAL_STATIC_LIBRARIES := cpufeatures
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := com_couchbase_touchdb_RevCollator_util
LOCAL_SRC_FILES := util/com_couchbase_touchdb_RevCollator_util.c
LOCAL_LDLIBS    := -llog -ldl -L$(LOCAL_PATH/util)
LOCAL_STATIC_LIBRARIES := cpufeatures
include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)