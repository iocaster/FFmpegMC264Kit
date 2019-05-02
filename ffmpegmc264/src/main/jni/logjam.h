#ifndef LOGJAM_H
#define LOGJAM_H

#include <android/log.h>

#define LOGTAG "ffmpeg_jni"

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOGTAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , LOGTAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO   , LOGTAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN   , LOGTAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOGTAG, __VA_ARGS__)

//int __android_log_vprint(int prio, const char *tag, const char *fmt, va_list ap);
#define myLOGI(...) __android_log_vprint(ANDROID_LOG_INFO   , LOGTAG, __VA_ARGS__)

int start_logger(const char *app_name);

#endif
