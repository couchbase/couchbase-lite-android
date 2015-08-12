#include <jni.h>
#include <cpu-features.h>


#define DEBUG 0

#if DEBUG
#include <android/log.h>
#  define  D(x...)  __android_log_print(ANDROID_LOG_INFO, "TDCollateJSON_isARMv7", x)
#else
#  define  D(...)  do {} while (0)
#endif

jboolean Java_com_couchbase_touchdb_TDCollateJSON_isARMv7(JNIEnv *env, jclass class) {
    uint64_t features = android_getCpuFeatures();
    if (android_getCpuFamily() == ANDROID_CPU_FAMILY_ARM &&
        (features & ANDROID_CPU_ARM_FEATURE_ARMv7) == ANDROID_CPU_ARM_FEATURE_ARMv7) {
        return JNI_TRUE;
    }
    else {
        return JNI_FALSE;
    }
}