#ifndef ANDROID_LOGGING_H
#define ANDROID_LOGGING_H

#ifdef ANDROID

#include <android/log.h>

#define  LOG_TAG    "LiteCoreJNI"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)

#else
#include <stdio.h>
#define  LOGE(...)  fprintf(stderr,__VA_ARGS__)
#define  LOGW(...)  fprintf(stderr,__VA_ARGS__)
#define  LOGI(...)  fprintf(stderr,__VA_ARGS__)
#define  LOGD(...)  fprintf(stderr,__VA_ARGS__)
#define  LOGV(...)  fprintf(stderr,__VA_ARGS__)

#endif

#endif //ANDROID_LOGGING_H
