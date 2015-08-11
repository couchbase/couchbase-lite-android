#include <jni.h>
#include <cpu-features.h>

jboolean
Java_com_couchbase_touchdb_RevCollator_isARMv7
(JNIEnv *env, jclass class) {
  uint64_t features = android_getCpuFeatures();
  if ((android_getCpuFamily() != ANDROID_CPU_FAMILY_ARM) ||
      ((features & ANDROID_CPU_ARM_FEATURE_ARMv7) == 0) ||
      ((features & ANDROID_CPU_ARM_FEATURE_NEON) == 0)) {
    return JNI_FALSE;
  }
  else {
    return JNI_TRUE;
  }
}
