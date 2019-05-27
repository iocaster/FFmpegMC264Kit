/*
 *  This file is based on videokit.c :
 *      https://github.com/IljaKosynkin/FFmpeg-Development-Kit/blob/master/JNI/app/jni/videokit.c
 *  And I - YongTae Kim - cited, modified and renamed it to ffmpeg_jni.c on 2019.03.11.
 */
#include "logjam.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

JavaVM *sVm = NULL;

static jobject gobj = 0;    //MC264Encoder.java
static jclass gcls = 0;     //MC264Encoder.java
static jmethodID gmid;      //MC264Encoder.java :: void putYUVFrameData( byte[] frameData, long pts, boolean flushOnly )
static jmethodID gmid2;     //MC264Encoder.java :: void setParameter( int width, int height, int bitrate, float framerate, int gop, int colorformat )
static jmethodID gmid3;     //MC264Encoder.java :: public void release()
JNIEnv *genv;

static jobject gobj2 = 0;    //MCAACEncoder.java
static jclass gcls2 = 0;     //MCAACEncoder.java
static jmethodID gamid;      //MCAACEncoder.java :: void putPCMFrameData(byte[] frameData, long pts, boolean flushOnly)
static jmethodID gamid2;     //MCAACEncoder.java :: void setParameter(int samplerate, int bitrate, int numchannels)
static jmethodID gamid3;     //MCAACEncoder.java :: public void release()
JNIEnv *genv2;

int main(int argc, char **argv);
int remuxing(int argc, char **argv);
int transcoding(int argc, char **argv);
int ytkim_ffmpeg_main(int argc, char **argv);
void ytkim_ffmpeg_stop();
void ytkim_ffmpeg_force_stop();

static char app_name[] = "ffmpeg_jni";

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    sVm = vm;
    start_logger( /*"ffmpeg_jni"*/ app_name );
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_kim_yt_ffmpegmc264_MC264Encoder_H264MediaCodecReady(JNIEnv *env, jobject obj)
{
    jclass cls1 = (*env)->GetObjectClass(env, obj);
    if (cls1 == 0) {
        /* error */
    }
    gcls = (*env)->NewGlobalRef(env, cls1);
    if (gcls == 0) {
        /* error */
    }

    gobj = (*env)->NewGlobalRef(env, obj);

    gmid = (*env)->GetMethodID(env, gcls, "putYUVFrameData", "([BIJZ)V");   //void putYUVFrameData( byte[] frameData, int stride, long pts, boolean flushOnly )
    gmid2 = (*env)->GetMethodID(env, gcls, "setParameter", "(IIIFII)V");    //void setParameter( int width, int height, int bitrate, float framerate, int gop, int colorformat )
    gmid3 = (*env)->GetMethodID(env, gcls, "release", "()V");               //public void release()
    if (gmid == 0 || gmid2 == 0 ) {
        /* error */
        return;
    }

    genv = env;
}

/*
 * Local functions called by java & ffmpeg libavcodec
 */
void pushEncodedFrame( unsigned char* pData, int length, int64_t presentationTimeUs, int32_t b_keyframe );
int getEncodedFrame( void **data, int *length, int64_t *presentationTimeUs, int32_t *b_keyframe );

// This function - onH264MediaCodecEncodedFrame(...) - is deprecated. Use instead onH264MediaCodecEncodedFrame2(..).
JNIEXPORT void JNICALL Java_kim_yt_ffmpegmc264_MC264Encoder_onH264MediaCodecEncodedFrame(JNIEnv *env, jobject obj, jbyteArray data, jlong presentationTimeUs, jint b_keyframe)
{
    int isCopy;
    jbyte * pData = (*genv)->GetByteArrayElements(genv, data, /* &isCopy */0);
    jsize length = (*env)->GetArrayLength(env, data);

    int64_t pts = presentationTimeUs;  //jlong is 64-bit
    int32_t is_keyframe = b_keyframe;

    pushEncodedFrame( (unsigned char*) pData, length, pts, is_keyframe );

    (*genv)->ReleaseByteArrayElements(genv, data, pData, JNI_ABORT);
}

// This function is memory copy reduced version than onH264MediaCodecEncodedFrame(...).
JNIEXPORT void JNICALL Java_kim_yt_ffmpegmc264_MC264Encoder_onH264MediaCodecEncodedFrame2(JNIEnv *env, jobject obj, jobject data, jint size, jlong presentationTimeUs, jint b_keyframe)
{
    int isCopy;
    jbyte * pData = (*genv)->GetDirectBufferAddress(genv, data);

    int64_t pts = presentationTimeUs;  //jlong is 64-bit
    int32_t is_keyframe = b_keyframe;

    pushEncodedFrame( (unsigned char*) pData, size, pts, is_keyframe );
}

/*
 * container for both pushEncodedFrame() and getEncodedFrame(). No lock required in use case.
 */
struct encodedPacket {
    unsigned char *pData;
    int length;
    int64_t presentationTimeUs;
    int32_t b_keyframe;
};

#define MAX_ENC_PACKETS  2
struct encodedPacket encodedPackets[MAX_ENC_PACKETS+1];
uint32_t encodedPktIdxHead = 0;
uint32_t encodedPktIdxTail = 0;

void pushEncodedFrame( unsigned char* pData, int length, int64_t presentationTimeUs, int32_t b_keyframe )
{
    encodedPackets[encodedPktIdxHead].pData = (unsigned char *) malloc( length );
    memcpy( encodedPackets[encodedPktIdxHead].pData, pData, length );
    encodedPackets[encodedPktIdxHead].length = length;
    encodedPackets[encodedPktIdxHead].presentationTimeUs = presentationTimeUs;
    encodedPackets[encodedPktIdxHead].b_keyframe = b_keyframe;

    encodedPktIdxHead = ++encodedPktIdxHead % MAX_ENC_PACKETS;
}

int getEncodedFrame( void **data, int *length, int64_t *presentationTimeUs, int32_t *b_keyframe )
{
    if( encodedPktIdxHead != encodedPktIdxTail ) {
        *data = encodedPackets[encodedPktIdxTail].pData;
        *length = encodedPackets[encodedPktIdxTail].length;
        *presentationTimeUs = encodedPackets[encodedPktIdxTail].presentationTimeUs;
        *b_keyframe = encodedPackets[encodedPktIdxTail].b_keyframe;

        encodedPktIdxTail = ++encodedPktIdxTail % MAX_ENC_PACKETS;
        return 0;
    } else {
        *data = NULL;
    }
    return -1;
}

/*
 * refer to : https://m.blog.naver.com/PostView.nhn?blogId=ein0204&logNo=220384865210&proxyReferer=https%3A%2F%2Fwww.google.com%2F
 * NV12 종류 : (LG Q6)
 * COLOR_FormatYUV420SemiPlanar
 * COLOR_FormatYUV420PackedSemiPlanar
 * COLOR_TI_FormatYUV420PackedSemiPlanar
 *
 * I420 종류 : (ffmpeg, 삼성의 최신폰)
 * COLOR_FormatYUV420Planar
 * COLOR_FormatYUV420PackedPlanar
 *
 * For a single I420 pixel : YYYYYYYY UU VV
 * For a single NV12 pixel : YYYYYYYY UVUV
 *
 */
static uint8_t * convI420toNV12( uint8_t *pData )
{
    //TODO
}

int mediacodec_release(void)
{
    LOGD("ffmpeg_jni.c : mediacodec_release() : Enter...\n");

    if( gcls && gobj ) {

        //call public void release()
        (*genv)->CallVoidMethod(genv, gobj, gmid3 );

        return 0;
    } else {
        /* not yet prepared ... */
        LOGE("ffmpeg_jni.c : mediacodec_release() : gcls & gobj are not yet prepared !!!");
        return -1;
    }
}

int mediacodecAAC_release(void)
{
    LOGD("ffmpeg_jni.c : mediacodecAAC_release() : Enter...\n");

    if( gcls2 && gobj2 ) {

        //call public void release()
        (*genv2)->CallVoidMethod(genv2, gobj2, gamid3 );

        return 0;
    } else {
        /* not yet prepared ... */
        LOGE("ffmpeg_jni.c : mediacodecAAC_release() : gcls2 & gobj2 are not yet prepared !!!");
        return -1;
    }
}

int sendParameterToJavaEncoder( int width, int height, int bitrate, float framerate, int gop, int colorformat )
{
    LOGD("sendParameterToJavaEncoder() : w=%d h=%d bitrate=%d framerate=%f gop=%d colorformat=%d\n",
        width, height, bitrate, framerate, gop, colorformat );

    if( gcls && gobj ) {
        //call setParameter( int width, int height, int bitrate, float framerate, int gop, int colorformat )
        (*genv)->CallVoidMethod(genv, gobj, gmid2, width, height, bitrate, framerate, gop, colorformat );
        return 0;
    } else {
        /* not yet prepared ... */
        LOGE("sendParameterToJavaEncoder() : gcls & gobj are not yet prepared !!!");
        return -1;
    }
}

int sendFrameToJavaEncoder( void *data, int length, int stride, uint64_t pts ) {
    if( gcls && gobj ) {
        jbyteArray jb = (*genv)->NewByteArray(genv, length);
        (*genv)->SetByteArrayRegion(genv, jb, 0, length, (jbyte*)data);

        //call void putYUVFrameData( byte[] frameData, int stride, boolean flushOnly )
        (*genv)->CallVoidMethod(genv, gobj, gmid, jb, stride, pts, /*flush*/0 );

        (*genv)->DeleteLocalRef(genv, jb);
        return length;
    } else {
        /* not yet prepared ... */
        LOGE("sendFrameToJavaEncoder() : gcls & gobj are not yet prepared !!!");
        return -1;
    }
    return 0;
}

JNIEXPORT void JNICALL Java_kim_yt_ffmpegmc264_MCAACEncoder_AACMediaCodecReady(JNIEnv *env, jobject obj)
{
    jclass cls1 = (*env)->GetObjectClass(env, obj);
    if (cls1 == 0) {
        /* error */
    }
    gcls2 = (*env)->NewGlobalRef(env, cls1);
    if (gcls2 == 0) {
        /* error */
    }

    gobj2 = (*env)->NewGlobalRef(env, obj);

    gamid = (*env)->GetMethodID(env, gcls2, "putPCMFrameData", "([BJZ)V");   //void putPCMFrameData(byte[] frameData, long pts, boolean flushOnly)
    gamid2 = (*env)->GetMethodID(env, gcls2, "setParameter", "(III)V");      //void setParameter(int samplerate, int bitrate, int numchannels)
    gamid3 = (*env)->GetMethodID(env, gcls2, "release", "()V");               //public void release()
    if (gamid == 0 || gamid2 == 0 ) {
        /* error */
        return;
    }

    genv2 = env;
}

/*
 * Local functions called by java & ffmpeg libavcodec
 */
void pushEncodedFrameAAC( unsigned char* pData, int length, int64_t presentationTimeUs, int32_t b_keyframe );
int getEncodedFrameAAC( void **data, int *length, int64_t *presentationTimeUs, int32_t *b_keyframe );

JNIEXPORT void JNICALL Java_kim_yt_ffmpegmc264_MCAACEncoder_onAACMediaCodecEncodedFrame2(JNIEnv *env, jobject obj, jobject data, jint size, jlong presentationTimeUs, jint b_keyframe)
{
    int isCopy;
    jbyte * pData = (*env)->GetDirectBufferAddress(env, data);

    int64_t pts = presentationTimeUs;  //jlong is 64-bit
    int32_t is_keyframe = b_keyframe;

    pushEncodedFrameAAC( (unsigned char*) pData, size, pts, is_keyframe );
}

/*
 * container for both pushEncodedFrame() and getEncodedFrame(). No lock required in use case.
 */
struct encodedAACPacket {
    unsigned char *pData;
    int length;
    int64_t presentationTimeUs;
    int32_t b_keyframe;
};

#define MAX_ENC_AAC_PACKETS  8
static struct encodedAACPacket encodedAACPackets[MAX_ENC_AAC_PACKETS+1];
static uint32_t encodedPktIdxAACHead = 0;
static uint32_t encodedPktIdxAACTail = 0;

void pushEncodedFrameAAC( unsigned char* pData, int length, int64_t presentationTimeUs, int32_t b_keyframe )
{
    encodedAACPackets[encodedPktIdxAACHead].pData = (unsigned char *) malloc( length );
    memcpy( encodedAACPackets[encodedPktIdxAACHead].pData, pData, length );
    encodedAACPackets[encodedPktIdxAACHead].length = length;
    encodedAACPackets[encodedPktIdxAACHead].presentationTimeUs = presentationTimeUs;
    encodedAACPackets[encodedPktIdxAACHead].b_keyframe = b_keyframe;

    encodedPktIdxAACHead = ++encodedPktIdxAACHead % MAX_ENC_AAC_PACKETS;
}

int getEncodedFrameAAC( void **data, int *length, int64_t *presentationTimeUs, int32_t *b_keyframe )
{
    if( encodedPktIdxAACHead != encodedPktIdxAACTail ) {
        *data = encodedAACPackets[encodedPktIdxAACTail].pData;
        *length = encodedAACPackets[encodedPktIdxAACTail].length;
        *presentationTimeUs = encodedAACPackets[encodedPktIdxAACTail].presentationTimeUs;
        *b_keyframe = encodedAACPackets[encodedPktIdxAACTail].b_keyframe;

        encodedPktIdxAACTail = ++encodedPktIdxAACTail % MAX_ENC_AAC_PACKETS;
        return 0;
    } else {
        *data = NULL;
    }
    return -1;
}

int sendAACParameterToJavaEncoder( int samplerate, int bitrate, int numchannels )
{
    LOGD("sendAACParameterToJavaEncoder() : samplerate=%d bitrate=%d numchannels=%d\n",
         samplerate, bitrate, numchannels );

    if( gcls2 && gobj2 ) {
        //call setParameter(int samplerate, int bitrate, int numchannels)
        (*genv2)->CallVoidMethod(genv2, gobj2, gamid2, samplerate, bitrate, numchannels );
        return 0;
    } else {
        /* not yet prepared ... */
        LOGE("sendAACParameterToJavaEncoder() : gcls2 & gobj2 are not yet prepared !!!");
        return -1;
    }
}

int sendPCMFrameToJavaEncoder( void *data, int length, uint64_t pts ) {
    if( gcls2 && gobj2 ) {
        jbyteArray jb = (*genv2)->NewByteArray(genv2, length);
        (*genv2)->SetByteArrayRegion(genv2, jb, 0, length, (jbyte*)data);

        //call void putPCMFrameData(byte[] frameData, long pts, boolean flushOnly)
        (*genv2)->CallVoidMethod(genv2, gobj2, gamid, jb, pts, /*flush*/0 );

        (*genv2)->DeleteLocalRef(genv2, jb);
        return length;
    } else {
        /* not yet prepared ... */
        LOGE("sendPCMFrameToJavaEncoder() : gcls2 & gobj2 are not yet prepared !!!");
        return -1;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_kim_yt_ffmpegmc264_MC264Encoder_ffmpegStop(JNIEnv *env, jobject instance)
{
    ytkim_ffmpeg_stop();
}

JNIEXPORT jint JNICALL Java_kim_yt_ffmpegmc264_MC264Encoder_ffmpegForceStop(JNIEnv *env, jobject instance)
{
    ytkim_ffmpeg_force_stop();
}

JNIEXPORT jint JNICALL Java_kim_yt_ffmpegmc264_MC264Encoder_ffmpegRun(JNIEnv *env, jobject instance, jobjectArray args)
{
    int i = 0;
    int argc = 0;
    char **argv = NULL;
    jstring *strr = NULL;

    LOGI("Main started");

    if (args != NULL) {
        argc = (*env)->GetArrayLength(env, args);
        argv = (char **) malloc(sizeof(char *) * argc);
        strr = (jstring *) malloc(sizeof(jstring) * argc);
        
        for (i = 0; i < argc; ++i) {
            strr[i] = (jstring)(*env)->GetObjectArrayElement(env, args, i);
            argv[i] = (char *)(*env)->GetStringUTFChars(env, strr[i], 0);
        }
    }
    
    jint retcode = 0;
    //retcode = main(argc, argv);
    retcode = ytkim_ffmpeg_main(argc, argv);

    LOGI("Main ended : retcode = %d", retcode);
    
    for (i = 0; i < argc; ++i) {
        (*env)->ReleaseStringUTFChars(env, strr[i], argv[i]);
    }
    
    free(argv);
    free(strr);
    
    return retcode;
}

// --- just test code ---
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>

static void log_packet(const AVFormatContext *fmt_ctx, const AVPacket *pkt, const char *tag)
{
    AVRational *time_base = &fmt_ctx->streams[pkt->stream_index]->time_base;

    printf("%s: pts:%s pts_time:%s dts:%s dts_time:%s duration:%s duration_time:%s stream_index:%d\n",
           tag,
           av_ts2str(pkt->pts), av_ts2timestr(pkt->pts, time_base),
           av_ts2str(pkt->dts), av_ts2timestr(pkt->dts, time_base),
           av_ts2str(pkt->duration), av_ts2timestr(pkt->duration, time_base),
           pkt->stream_index);
}

int remuxing(int argc, char **argv)
{
    AVOutputFormat *ofmt = NULL;
    AVFormatContext *ifmt_ctx = NULL, *ofmt_ctx = NULL;
    AVPacket pkt;
    const char *in_filename, *out_filename;
    int ret, i;
    int stream_index = 0;
    int *stream_mapping = NULL;
    int stream_mapping_size = 0;

    if (argc < 3) {
        printf("usage: %s input output\n"
                       "API example program to remux a media file with libavformat and libavcodec.\n"
                       "The output format is guessed according to the file extension.\n"
                       "\n", argv[0]);
        return 1;
    }

    in_filename  = argv[1];
    out_filename = argv[2];

    if ((ret = avformat_open_input(&ifmt_ctx, in_filename, 0, 0)) < 0) {
        fprintf(stderr, "Could not open input file '%s'", in_filename);
        goto end;
    }

    if ((ret = avformat_find_stream_info(ifmt_ctx, 0)) < 0) {
        fprintf(stderr, "Failed to retrieve input stream information");
        goto end;
    }

    av_dump_format(ifmt_ctx, 0, in_filename, 0);

    avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, out_filename);
    if (!ofmt_ctx) {
        fprintf(stderr, "Could not create output context\n");
        ret = AVERROR_UNKNOWN;
        goto end;
    }

    stream_mapping_size = ifmt_ctx->nb_streams;
    stream_mapping = av_mallocz_array(stream_mapping_size, sizeof(*stream_mapping));
    if (!stream_mapping) {
        ret = AVERROR(ENOMEM);
        goto end;
    }

    ofmt = ofmt_ctx->oformat;

    for (i = 0; i < ifmt_ctx->nb_streams; i++) {
        AVStream *out_stream;
        AVStream *in_stream = ifmt_ctx->streams[i];
        AVCodecParameters *in_codecpar = in_stream->codecpar;

        if (in_codecpar->codec_type != AVMEDIA_TYPE_AUDIO &&
            in_codecpar->codec_type != AVMEDIA_TYPE_VIDEO &&
            in_codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE) {
            stream_mapping[i] = -1;
            continue;
        }

        stream_mapping[i] = stream_index++;

        out_stream = avformat_new_stream(ofmt_ctx, NULL);
        if (!out_stream) {
            fprintf(stderr, "Failed allocating output stream\n");
            ret = AVERROR_UNKNOWN;
            goto end;
        }

        ret = avcodec_parameters_copy(out_stream->codecpar, in_codecpar);
        if (ret < 0) {
            fprintf(stderr, "Failed to copy codec parameters\n");
            goto end;
        }
        out_stream->codecpar->codec_tag = 0;
    }
    av_dump_format(ofmt_ctx, 0, out_filename, 1);

    if (!(ofmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&ofmt_ctx->pb, out_filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            fprintf(stderr, "Could not open output file '%s'", out_filename);
            goto end;
        }
    }

    ret = avformat_write_header(ofmt_ctx, NULL);
    if (ret < 0) {
        fprintf(stderr, "Error occurred when opening output file\n");
        goto end;
    }

    while (1) {
        AVStream *in_stream, *out_stream;

        ret = av_read_frame(ifmt_ctx, &pkt);
        if (ret < 0)
            break;

        in_stream  = ifmt_ctx->streams[pkt.stream_index];
        if (pkt.stream_index >= stream_mapping_size ||
            stream_mapping[pkt.stream_index] < 0) {
            av_packet_unref(&pkt);
            continue;
        }

        pkt.stream_index = stream_mapping[pkt.stream_index];
        out_stream = ofmt_ctx->streams[pkt.stream_index];
        log_packet(ifmt_ctx, &pkt, "in");

        /* copy packet */
        pkt.pts = av_rescale_q_rnd(pkt.pts, in_stream->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
        pkt.dts = av_rescale_q_rnd(pkt.dts, in_stream->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
        pkt.duration = av_rescale_q(pkt.duration, in_stream->time_base, out_stream->time_base);
        pkt.pos = -1;
        log_packet(ofmt_ctx, &pkt, "out");

        ret = av_interleaved_write_frame(ofmt_ctx, &pkt);
        if (ret < 0) {
            fprintf(stderr, "Error muxing packet\n");
            break;
        }
        av_packet_unref(&pkt);
    }

    av_write_trailer(ofmt_ctx);
    end:

    avformat_close_input(&ifmt_ctx);

    /* close output */
    if (ofmt_ctx && !(ofmt->flags & AVFMT_NOFILE))
        avio_closep(&ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);

    av_freep(&stream_mapping);

    if (ret < 0 && ret != AVERROR_EOF) {
        fprintf(stderr, "Error occurred: %s\n", av_err2str(ret));
        return 1;
    }

    return 0;
}