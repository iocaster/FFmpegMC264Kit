/*
 * Copyright (C) 2019 The FFmpegMC264encoder library By YongTae Kim.
 * This library is based on The Android Open Source Project & FFmpeg.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kim.yt.ffmpegmc264;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MC264Encoder {
//    static {
//        System.loadLibrary("ffmpeg_jni");
//        System.loadLibrary("avutil-56");
//        System.loadLibrary("swresample-3");
//        System.loadLibrary("avcodec-58");
//        System.loadLibrary("avformat-58");
//        System.loadLibrary("swscale-5");
//        System.loadLibrary("avfilter-7");
//        //System.loadLibrary("avdevice");
//        //System.loadLibrary("ffmpeg_jni"); //moved up
//    }
//    public native int ffmpegRun(String[] args);
//    public native int ffmpegStop();
//    public native int ffmpegForceStop();
    public native void H264MediaCodecReady();
    public native void onH264MediaCodecEncodedFrame(byte[] data, long presentationTimeUs, int b_keyframe);
    public native void onH264MediaCodecEncodedFrame2(ByteBuffer data, int size, long presentationTimeUs, int b_keyframe);


    private static final String TAG = "MC264Encoder";
    private final boolean VERBOSE = false;
    private final boolean VERBOSE_DUMP = false;

    //for MediaProjection
    private boolean mEnableScreenGrabber = false;
    private Surface mEncoderSurface;
    private long firstInfopresentationTimeUs = 0;


//    private static final boolean DEBUG_SAVE_FILE = false;   // save copy of encoded movie
//    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/testencode.h264stream.raw.";
//    private FileOutputStream outputStream = null;

//    public interface FrameEncodedListener {
//        void onFrameEncoded(byte[] data, int length, long presentationTimeUs, int b_keyframe);
//    }
//    private FrameEncodedListener listener;


    public interface YUVFrameListener {
        void onYUVFrame(byte[] frameData, int width, int height);
    }
    private YUVFrameListener mYUVFrameListener;
    private boolean mYUVFrameListenerKeyFrameOnly = true;
    public void setYUVFrameListener( YUVFrameListener listener, boolean keyFrameOnly ) {
        mYUVFrameListener = listener;
        mYUVFrameListenerKeyFrameOnly = keyFrameOnly;
    }

    private MediaCodec encoder;
    private boolean encoderDone = false;
    private boolean released = false;

    ByteBuffer[] encoderInputBuffers; // = encoder.getInputBuffers();
    ByteBuffer[] encoderOutputBuffers; // = encoder.getOutputBuffers();

    // size of a frame, in pixels
    private int mStride = -1;
    private int mWidth = -1;
    private int mHeight = -1;
    private int mBitRate = -1;
    private float mFrameRate = -1;
    private int mGOP = -1;
    private int mEncoderColorFormat = -1;
    private long mFrameIndex = 0;
    private long mEncodedIndex = 1;

    private ByteBuffer mH264Keyframe;
    private int mH264MetaSize = 0;                   // Size of SPS + PPS data

    public MC264Encoder() {
        try {
            encoder = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
//moved all into setParameter(...)
//        mWidth = 640;
//        mHeight = 480;
//        mBitRate = 1200000;
//        mFrameRate = 25; //23.98f;
//        mGOP = 12;
//
////        mEncoderColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
////        Log.d( TAG, "mEncoderColorFormat - COLOR_FormatYUV420Planar = " + mEncoderColorFormat );    //19 on LG Q6 (Died)
//
//        MediaCodecInfo codecInfo = selectCodec("video/avc");
//        mEncoderColorFormat = selectColorFormat(codecInfo, "video/avc");
//        Log.d( TAG, "mEncoderColorFormat - after selectColorFormat() = " + mEncoderColorFormat );   //21 on LG Q6 (OK)
//
////moved into setParameter();
//        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
//        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight * 3 / 2 );
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
//        mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, mFrameRate);
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mEncoderColorFormat);
//        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mGOP);
//        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        encoder.start();
//
////moved into setParameter();
//        encoderInputBuffers = encoder.getInputBuffers();
//        encoderOutputBuffers = encoder.getOutputBuffers();

//        if (DEBUG_SAVE_FILE) {
//            //String fileName = DEBUG_FILE_NAME_BASE + mWidth + "x" + mHeight + ".mp4";
//            String fileName = DEBUG_FILE_NAME_BASE + "mp4";
//            try {
//                outputStream = new FileOutputStream(fileName);
//                Log.d(TAG, "encoded output will be saved as " + fileName);
//            } catch (IOException ioe) {
//                Log.w(TAG, "Unable to create debug output file " + fileName);
//                throw new RuntimeException(ioe);
//            }
//        }

        //H264MediaCodecReady();    //moved into MainActivity.java
    }

//    public void start() {
//        if (encoder != null) {
//            encoder.start();
//        }
//    }
//
//    public void stop() {
//        Log.d(TAG, "stop() : Enter...");
//        if (encoder != null) {
//            encoder.stop();
//        }
//    }

    public void reset() {
        Log.d(TAG, "reset() : Enter...");

        if (encoder != null) {
            encoder.stop();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                encoder.reset();
            }
            mStride = -1;
        }

//        if (outputStream != null) {
//            try {
//                outputStream.close();
//                outputStream = null;
//            } catch (IOException ioe) {
//                Log.w(TAG, "failed closing debug file");
//                throw new RuntimeException(ioe);
//            }
//        }
    }

    public void release() {
        Log.d(TAG, "release() : Enter...");

        if( released ) return;

        if (encoder != null) {
            encoder.stop();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                encoder.reset();
            }
            //encoder.release();    //blocked to reuse multiple times
        }

//        if (outputStream != null) {
//            try {
//                outputStream.close();
//                outputStream = null;
//            } catch (IOException ioe) {
//                Log.w(TAG, "failed closing debug file");
//                throw new RuntimeException(ioe);
//            }
//        }
        mStride = -1;
        released = true;
    }

//    public void encodeTest() {
//        setParameter(320, 240, 1200000, 15.0f, 7, -1);
//
//        int generateIndex = 0;
//        byte[] frameData = new byte[mWidth * mHeight * 3 / 2];
//
//        while (true) {
//            Log.d(TAG, "generateIndex = " + generateIndex);
//            generateFrame(generateIndex, mEncoderColorFormat, frameData);
//
//            putYUVFrameData(frameData, mWidth, generateIndex,false);
//
//            generateIndex++;
//            if (generateIndex >= mFrameRate * 2) break; //2-sec
//        }
//
//        putYUVFrameData(frameData, mWidth, generateIndex,true);
//
//        while (!isEncodeDone()) {
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        release();
//    }

    public void setParameter(int width, int height, int bitrate, float framerate, int gop, int colorformat) {
        Log.d(TAG, "setParameter() : Enter ... "
                + "w=" + width
                + ", h=" + height
                + ", bitrate=" + bitrate
                + ", framerate=" + framerate
                + ", gop=" + gop
                + ", colorformat=" + colorformat);

        released = false;

        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mFrameRate = framerate;
        mGOP = gop;
        //mEncoderColorFormat = colorformat;

//        try {
//            encoder = MediaCodec.createEncoderByType("video/avc");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
////        mEncoderColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
////        Log.d( TAG, "mEncoderColorFormat - COLOR_FormatYUV420Planar = " + mEncoderColorFormat );    //19 on LG Q6 (Died)

        MediaCodecInfo codecInfo = selectCodec("video/avc");
        mEncoderColorFormat = selectColorFormat(codecInfo, "video/avc");
        Log.d(TAG, "mEncoderColorFormat - after selectColorFormat() = " + mEncoderColorFormat);   //21 on LG Q6 (OK)
        boolean deviceIsSemiPlanar = isSemiPlanarYUV(mEncoderColorFormat);
        Log.d(TAG, "mEncoderColorFormat - after selectColorFormat() : device isSemiPlanarYUV = " + deviceIsSemiPlanar );

        if( mEnableScreenGrabber )
            mEncoderColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface; //to test MediaProjection

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight * 3 / 2);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mediaFormat.setFloat  (MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mEncoderColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1 /*sec*/);    //KEY_I_FRAME_INTERVAL is not for GOP
        mediaFormat.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, mGOP /*frames*/); //for low-delay and good error-resilience : Maybe this is for GOP
        mediaFormat.setInteger(MediaFormat.KEY_LATENCY, mGOP /*frames*/);               //What is this for?
        mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0);                    //0- real time, 1 - non-reaal time (beast effort)
        if( mStride > 0 ) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                mediaFormat.setInteger(MediaFormat.KEY_STRIDE, mStride );   //No effect on LG-Q6 although KEY_STRIDE requires API 23 and LG-Q6 is API 25.

            //and do more for my LG-Q6
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, mStride );
        }
        //Log.d(TAG, "video sample rate = " + mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) );

//        mediaFormat.setInteger(MediaCodec.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
//        mediaFormat.setInteger(MediaCodec.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel5);
        printCodecProfileLevel(codecInfo, "video/avc");

        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if( mEnableScreenGrabber )
            mEncoderSurface = encoder.createInputSurface();
        encoder.start();  //moved into setParameter();

        encoderInputBuffers = encoder.getInputBuffers();
        encoderOutputBuffers = encoder.getOutputBuffers();
    }

    private void setStride( int stride ) {
        mStride = stride;
        Log.d(TAG, "set encoder stride = " + stride );
        encoder.stop();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            encoder.reset();
        setParameter( mWidth, mHeight, mBitRate, mFrameRate, mGOP, -1);
    }

    public void putYUVFrameData(byte[] frameData, int stride, long pts, boolean flushOnly) {

        if( mStride <= 0 )
            setStride( stride );

        final int TIMEOUT_USEC = 8000;
        final int TIMEOUT_USEC2 = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        /*
         * feed frame data
         */
        if( !mEnableScreenGrabber ) {
            int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufIndex >= 0) {
                //long ptsUsec = computePresentationTime(mFrameIndex++);
                long ptsUsec = computePresentationTime(pts);

                if (flushOnly) {
                    encoder.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    return;
                }

                ByteBuffer inputBuf;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    /*ByteBuffer*/ inputBuf = encoder.getInputBuffer(inputBufIndex);
                } else {
                    /*ByteBuffer*/ inputBuf = encoderInputBuffers[inputBufIndex];
                }

                inputBuf.clear();
                inputBuf.put(frameData);
                encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);
                if (VERBOSE) Log.d(TAG, "submitted frame to enc (length=" + frameData.length + ")");
            } else {
                // either all in use, or we timed out during initial setup
                //if (VERBOSE)
                Log.d(TAG, "input buffer not available. drop input frame...");
            }
        }

        /*
         * Check encoder output. Take out encoded data if it is.
         */
        if (!encoderDone) {
            int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC2);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                //if (VERBOSE)
                    Log.d(TAG, "no output from encoder available");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                //encoderOutputBuffers = encoder.getOutputBuffers();
                //if (VERBOSE)
                    Log.d(TAG, "encoder output buffers changed");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = encoder.getOutputFormat();
                //if (VERBOSE)
                    Log.d(TAG, "encoder output format changed: " + newFormat);
//                Log.i(TAG,"PPS: "+getParameterSetAsString(encoder, "csd-0"));
//                Log.i(TAG,"SPS: "+getParameterSetAsString(encoder, "csd-1"));
            } else if (encoderStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else { // encoderStatus >= 0

//                int loopCnt = 0;
//                do {
//                    Log.d(TAG, "loopCnt = " + loopCnt);
//                    loopCnt++;

                ByteBuffer encodedData;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    /*ByteBuffer*/ encodedData = encoder.getOutputBuffer(encoderStatus);
                } else {
                    /*ByteBuffer*/ encodedData = encoderOutputBuffers[encoderStatus];
                }
                if (encodedData == null) {
                    Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                }
                // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);

//                if (outputStream != null) {
//                    byte[] data = new byte[info.size];
//                    encodedData.get(data);
//                    encodedData.position(info.offset);
//                    try {
//                        outputStream.write(data);
//                        Log.d(TAG, "write length = " + data.length);
//                    } catch (IOException ioe) {
//                        Log.w(TAG, "failed writing debug data to file");
//                        throw new RuntimeException(ioe);
//                    }
//                }

                if ( /*listener*/true) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        //if (VERBOSE)
                            Log.d(TAG, "info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 1 ...");
                        //save this metadata and use it in passing every key_frame
                        captureH264MetaData(encodedData, info);

                    } else if ((info.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0) {
                        //if (VERBOSE)
                            Log.d(TAG, "info.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME == 1 ...");
                    } else if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        //if (VERBOSE)
                            Log.d(TAG, "info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM == 1 ...");
                    } else {
                        int b_keyframe = info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME;

                         if (VERBOSE)
                            Log.d(TAG, "info.presentationTimeUs = " + info.presentationTimeUs + " b_keyframe = " + b_keyframe);

                         //for MediaProjection
                        long curPTS = 0;
                        if( mEnableScreenGrabber ) {
                            if (firstInfopresentationTimeUs == 0) {
                                firstInfopresentationTimeUs = info.presentationTimeUs;
                            }
                            /*long*/ curPTS = ((info.presentationTimeUs - firstInfopresentationTimeUs));
                        }

                        if (b_keyframe == 1) {
                            packageH264Keyframe(encodedData, info);
//                            byte[] data = new byte[mH264MetaSize + info.size];
//                            mH264Keyframe.position(0);
//                            mH264Keyframe.get(data);
//                            dump(data, "keyframe");
//                            onH264MediaCodecEncodedFrame(data, /*info.presentationTimeUs*/mEncodedIndex++, /*b_keyframe*/1);

                            if (mEnableScreenGrabber) {
                                onH264MediaCodecEncodedFrame2(mH264Keyframe, mH264MetaSize + info.size, curPTS /*mEncodedIndex++*/, /*b_keyframe*/1);
                            } else {
                                //onH264MediaCodecEncodedFrame2(mH264Keyframe, mH264MetaSize + info.size, /*info.presentationTimeUs*/mEncodedIndex++, /*b_keyframe*/1);
                                onH264MediaCodecEncodedFrame2(mH264Keyframe, mH264MetaSize + info.size, info.presentationTimeUs /*mEncodedIndex++*/, /*b_keyframe*/1);
                            }

                            if( mYUVFrameListener != null ) {
                                mYUVFrameListener.onYUVFrame( frameData, (mStride > 0) ? mStride : mWidth, mHeight );
                            }
                        } else {
//                            byte[] data = new byte[info.size];
//                            encodedData.get(data);
//                            encodedData.position(info.offset);
//                            dump(data, "non keyframe");
//                            onH264MediaCodecEncodedFrame(data, /*info.presentationTimeUs*/mEncodedIndex++, /*b_keyframe*/0);

                            if (mEnableScreenGrabber) {
                                onH264MediaCodecEncodedFrame2(encodedData, info.size, curPTS /*mEncodedIndex++*/, /*b_keyframe*/0);
                            } else {
                                //onH264MediaCodecEncodedFrame2(encodedData, info.size, /*info.presentationTimeUs*/mEncodedIndex++, /*b_keyframe*/0);
                                onH264MediaCodecEncodedFrame2(encodedData, info.size, info.presentationTimeUs /*mEncodedIndex++*/, /*b_keyframe*/0);
                            }

                            if( (mYUVFrameListener != null) && !mYUVFrameListenerKeyFrameOnly ) {
                                mYUVFrameListener.onYUVFrame( frameData, (mStride > 0) ? mStride : mWidth, mHeight );
                            }
                        }
                    }
                }

                encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                encoder.releaseOutputBuffer(encoderStatus, false);

//                    encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
//                } while( !encoderDone && (encoderStatus >= 0) ); //while
            } //last else
        } //if (!encoderDone)
    }

//    private String getParameterSetAsString(MediaCodec encoder, String csd)
//    {
//        MediaFormat format = encoder.getOutputFormat();
//        ByteBuffer ps = format.getByteBuffer(csd);
//
//        // The actual SPS and PPS byte codes
//        byte[] new_ps = new byte[ps.capacity()-4];
//        ps.position(4);
//        ps.get(new_ps,0,new_ps.length);
//
//        Log.d(TAG, "new_ps = " + new_ps);
//
//        //Covert to String
//        return Base64.encodeToString(new_ps, 0, new_ps.length, Base64.NO_WRAP);
//    }

    private void captureH264MetaData(ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        mH264MetaSize = bufferInfo.size;
        mH264Keyframe = ByteBuffer.allocateDirect(encodedData.capacity());  //Don't use ByteBuffer.allocate(...) to access in JNI using (*genv)->GetDirectBufferAddress(genv, data);
        mH264Keyframe.put(encodedData);

//        //if (VERBOSE)
//        {
//            Log.d(TAG, "captureH264MetaData() : mH264MetaSize = " + mH264MetaSize + ", encodedData.capacity() = " + encodedData.capacity());
//            Log.d(TAG, "captureH264MetaData() : videoConfig[] = "
//                    + videoConfig[0] + " " + videoConfig[1] + " " + videoConfig[2] + " " + videoConfig[3] + " " + videoConfig[4]);
//        }
//        dump(videoConfig, "captureH264MetaData() : videoConfig");
    }

    private void packageH264Keyframe(ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        // BufferOverflow : --> startcode missing <--
        mH264Keyframe.position(mH264MetaSize);
        mH264Keyframe.put(encodedData);
    }

    private void dump(byte[] data, String tag) {
        if (VERBOSE_DUMP) {
            String str = "";
            for (int i = 0; i < data.length; i++) {
                Integer intObject = Integer.valueOf(data[i]);
                str = str.concat(String.format("0x%02x ", intObject));
            }
            Log.d(TAG, tag + " : data[" + data.length + "] = " + str);
        }
    }

    public void enableScreenGrabber( boolean enable ) {
        mEnableScreenGrabber = enable;
    }

    public Surface getEncoderSurface() {
        return mEncoderSurface;
    }

    public boolean isEncodeDone() {
        final int TIMEOUT_USEC = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        /*
         * Check encoder output. Take out encoded data if it is.
         */
        if (!encoderDone) {
            int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE) Log.d(TAG, "isEncodeDone() : no output from encoder available");
                return false;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                //encoderOutputBuffers = encoder.getOutputBuffers();
                if (VERBOSE) Log.d(TAG, "isEncodeDone() : encoder output buffers changed");
                return false;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = encoder.getOutputFormat();
                if (VERBOSE)
                    Log.d(TAG, "isEncodeDone() : encoder output format changed: " + newFormat);
                return false;
            } else if (encoderStatus < 0) {
                Log.e(TAG, "isEncodeDone() : unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                return false;
            } else { // encoderStatus >= 0
                ByteBuffer encodedData;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    /*ByteBuffer*/ encodedData = encoder.getOutputBuffer(encoderStatus);
                } else {
                    /*ByteBuffer*/ encodedData = encoderOutputBuffers[encoderStatus];
                }
                if (encodedData == null) {
                    Log.e(TAG, "isEncodeDone() : encoderOutputBuffer " + encoderStatus + " was null");
                }
                // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);

//                if (outputStream != null) {
//                    byte[] data = new byte[info.size];
//                    encodedData.get(data);
//                    encodedData.position(info.offset);
//                    try {
//                        outputStream.write(data);
//                        Log.d(TAG, "isEncodeDone() : write length = " + data.length);
//                    } catch (IOException ioe) {
//                        Log.w(TAG, "isEncodeDone() : failed writing debug data to file");
//                        throw new RuntimeException(ioe);
//                    }
//                }

                encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                if (VERBOSE) Log.d(TAG, "isEncodeDone() : encoded " + info.size + " bytes"
                        + (encoderDone ? " (EOS)" : ""));

                encoder.releaseOutputBuffer(encoderStatus, false);

                if (encoderDone) return true;
                else return false;
            }
        }
        return true;
    }

    /**
     * Generates data for frame N into the supplied buffer.  We have an 8-frame animation
     * sequence that wraps around.  It looks like this:
     * <pre>
     *   0 1 2 3
     *   7 6 5 4
     * </pre>
     * We draw one of the eight rectangles and leave the rest set to the zero-fill color.
     */
    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;

    private void generateFrame(int frameIndex, int colorFormat, byte[] frameData) {
        final int HALF_WIDTH = mWidth / 2;
        boolean semiPlanar = isSemiPlanarYUV(colorFormat);
        // Set to zero.  In YUV this is a dull green.
        Arrays.fill(frameData, (byte) 0);
        int startX, startY, countX, countY;
        frameIndex %= 8;
        //frameIndex = (frameIndex / 8) % 8;    // use this instead for debug -- easier to see
        if (frameIndex < 4) {
            startX = frameIndex * (mWidth / 4);
            startY = 0;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = mHeight / 2;
        }
        for (int y = startY + (mHeight / 2) - 1; y >= startY; --y) {
            for (int x = startX + (mWidth / 4) - 1; x >= startX; --x) {
                if (semiPlanar) {
                    // full-size Y, followed by UV pairs at half resolution
                    // e.g. Nexus 4 OMX.qcom.video.encoder.avc COLOR_FormatYUV420SemiPlanar
                    // e.g. Galaxy Nexus OMX.TI.DUCATI1.VIDEO.H264E
                    //        OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x] = (byte) TEST_U;
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x + 1] = (byte) TEST_V;
                    }
                } else {
                    // full-size Y, followed by quarter-size U and quarter-size V
                    // e.g. Nexus 10 OMX.Exynos.AVC.Encoder COLOR_FormatYUV420Planar
                    // e.g. Nexus 7 OMX.Nvidia.h264.encoder COLOR_FormatYUV420Planar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_U;
                        frameData[mWidth * mHeight + HALF_WIDTH * (mHeight / 2) +
                                (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_V;
                    }
                }
            }
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    private static void printCodecProfileLevel(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        MediaCodecInfo.CodecProfileLevel profileLevels[] = capabilities.profileLevels;
        for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
            int profile = profileLevel.profile;
            int level = profileLevel.level;
            Log.e(TAG, "printCodecProfileLevel() : profile = " + profile + " level = " + level);
        }
    }

    // refer to : https://www.programcreek.com/java-api-examples/index.php?api=android.media.MediaCodecInfo.CodecProfileLevel
    public static String getProfileName(int profile) {
        switch (profile) {
            case MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline:
                return "Baseline";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileMain:
                return "Main";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileExtended:
                return "Extends";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh:
                return "High";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10:
                return "High10";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422:
                return "High422";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444:
                return "High444";
            default:
                return "Unknown";
        }
    }

    /**
     * Returns true if the specified color format is semi-planar YUV.  Throws an exception
     * if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / (long) mFrameRate;    //for old style sync mc264.c : pkt->pts = (int64_t)( (double)presentationTimeUs / (double)(1000000/(double)myCtx->fps) );
        //return  frameIndex * 1000000 / (long) mFrameRate;          //for new style sync mc264.c : pkt->pts = av_rescale_q( presentationTimeUs, bq, cq ) / (90000/(int)myCtx->fps);
    }

}
