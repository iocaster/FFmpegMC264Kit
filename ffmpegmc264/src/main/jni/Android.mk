LOCAL_PATH := $(call my-dir)

LOCAL_MODULE := libavformat
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libavformat-58.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

LOCAL_MODULE := libavcodec
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libavcodec-58.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

LOCAL_MODULE := libavutil
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libavutil-56.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

LOCAL_MODULE := libavfilter
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libavfilter-7.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

#LOCAL_MODULE := libavdevice
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libavdevice-58.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
#include $(PREBUILT_SHARED_LIBRARY)

LOCAL_MODULE := libswscale
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libswscale-5.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

LOCAL_MODULE := libswresample
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libswresample-3.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

LOCAL_MODULE := libavdevice
LOCAL_SRC_FILES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/libavdevice-58.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../jniLibs/$(APP_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg_jni
LOCAL_LDLIBS := -llog -ljnigraphics -lz -landroid -L$(LOCAL_PATH)/../jniLibs/$(APP_ABI) -lavutil-56 -lavfilter-7 -lswscale-5 -lavformat-58 -lavcodec-58 -lswresample-3 -lavdevice-58
ANDROID_LIB := -landroid
# LOCAL_CFLAGS := -I$(NDK)/sources/ffmpeg
LOCAL_SRC_FILES :=  ffmpeg_jni.c ffmpeg.c ffmpeg_filter.c ffmpeg_opt.c cmdutils.c ffmpeg_cuvid.c ffmpeg_hw.c logjam.c
LOCAL_SHARED_LIBRARIES := libavformat libavcodec libswscale libavutil libswresample libavfilter libavdevice
include $(BUILD_SHARED_LIBRARY)
# $(call import-module, ffmpeg/android/$(ARCH))
# $(call import-module,android/native_app_glue)
