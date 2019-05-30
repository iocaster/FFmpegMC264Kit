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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.*;

public class MCAACEncoder {
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
    public native void AACMediaCodecReady();
    public native void onAACMediaCodecEncodedFrame2(ByteBuffer data, int size, long presentationTimeUs, int b_keyframe);


    private static final String TAG = "MCAACEncoder";
    private final boolean VERBOSE = false;
    private final boolean VERBOSE_DUMP = false;
    private final boolean SAVETOFILE = false;
    private boolean mEncodeTest = false;

    //for MediaProjection
    private boolean mEnableScreenGrabber = false;
    private Surface mEncoderSurface;
    private long firstInfopresentationTimeUs = 0;
    private AudioRecord mAudioRecorder = null;
    MyMICAudio myMICAudio;


//    private static final boolean DEBUG_SAVE_FILE = false;   // save copy of encoded movie
//    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/testencode.h264stream.raw.";
//    private FileOutputStream outputStream = null;

    public interface FrameEncodedListener {
        void onFrameEncoded(byte[] data, int length, long presentationTimeUs, int b_keyframe);
    }
    private FrameEncodedListener listener;

    private MediaCodec encoder;
    private boolean encoderDone = false;
    private boolean released = false;

    ByteBuffer[] encoderInputBuffers; // = encoder.getInputBuffers();
    ByteBuffer[] encoderOutputBuffers; // = encoder.getOutputBuffers();

    private int mFrameIndex = 0;

    private int mSampleRate;
    private int mBitRate = -1;
    private int mNumChannels;
    private int mFrameSize;

    /*
     * muxer
     */
    class TrackIndex {
        int index = 0;
    }
    private TrackIndex mAudioTrackIndex = new TrackIndex();
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted;
    private int numTracksAdded = 0;

    private ByteBuffer mH264Keyframe;
    private int mH264MetaSize = 0;                   // Size of SPS + PPS data

    public MCAACEncoder() {
        try {
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm"); //AAC audio (note, this is raw AAC packets, not packaged in LATM!)
        } catch (IOException e) {
            e.printStackTrace();
        }
        //AACMediaCodecReady();    //moved into MainActivity.java
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
            //mStride = -1;
            mFrameIndex = 0;
        }

        if( mAudioRecorder != null ) {
            mAudioRecorder.stop();
        }
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

        if( mAudioRecorder != null ) {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }

        if( SAVETOFILE ) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
            mMuxerStarted = false;
        }
        //mStride = -1;
        mFrameIndex = 0;
        released = true;
    }

    public void encodeTest() {
        setParameter(44100, 192000, 1);

        int generateIndex = 0;
        byte[] frameData;

        mEncodeTest = true;
        while (true) {
            Log.d(TAG, "generateIndex = " + generateIndex);

            frameData = generateFrame(generateIndex);

            putPCMFrameData(frameData, generateIndex,false);

            generateIndex++;
            if (generateIndex >= (1000/20) * 10) break; //10-sec
        }
        putPCMFrameData(frameData, generateIndex,true);

        while (!isEncodeDone()) {
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mEncodeTest = false;
        release();
    }

    public void setParameter(int samplerate, int bitrate, int numchannels) {
        Log.d(TAG, "setParameter() : Enter ... "
                + "samplerate=" + samplerate
                + ", bitrate=" + bitrate
                + ", numchannel=" + numchannels);

        released = false;

        mSampleRate = samplerate;
        mBitRate = bitrate;
        mNumChannels = numchannels;

        mFrameSize = ((20 * mSampleRate) / 1000);   //should be equal to the avctx->frame_size of mcaac.c

//        try {
//            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        MediaCodecInfo codecInfo = selectCodec("audio/mp4a-latm");

        MediaFormat mediaFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", mSampleRate, mNumChannels);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);

        printCodecProfileLevel(codecInfo, "audio/mp4a-latm");

        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        if( SAVETOFILE ) {
            //File f = FileUtils.createTempFileInRootAppStorage(c, "test_" + new Date().getTime() + ".m4a");
            final File f = new File(Environment.getExternalStorageDirectory(), "recording_aac.mp4");
            Log.i(TAG, "SAVETOFILE : " + f.getAbsolutePath());

            try {
                mMuxer = new MediaMuxer(f.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException ioe) {
                throw new RuntimeException("MediaMuxer creation failed", ioe);
            }
        }

        encoderInputBuffers = encoder.getInputBuffers();
        encoderOutputBuffers = encoder.getOutputBuffers();
    }

    public void putPCMFrameData(byte[] frameData, long pts, boolean flushOnly) {

        final int TIMEOUT_USEC = 8000;
        final int TIMEOUT_USEC2 = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        /*
         * feed frame data
         */
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                /*ByteBuffer*/
                inputBuf = encoder.getInputBuffer(inputBufIndex);
            } else {
                /*ByteBuffer*/
                inputBuf = encoderInputBuffers[inputBufIndex];
            }

            if( !mEnableScreenGrabber ) {
                inputBuf.clear();
                inputBuf.put(frameData);
            } else {
                inputBuf.clear();
                //inputBuf.put(frameData);  //moved down

                int bufferSize = frameData.length;
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
                int rc = mAudioRecorder.read(byteBuffer,bufferSize);

                if( rc > 0 ) {
                    byte[] bytes = new byte[bufferSize];
                    byteBuffer.get(bytes, 0, bytes.length);
                    //mMCAACEncoder.putPCMFrameData(bytes, frameIdx++, false);
                    inputBuf.put(bytes);
                } else {
                    Log.e(TAG, "Error : read mic audio data rc = " + rc);
                }
            }
            int dataLen = mFrameSize * 2 * mNumChannels;
            encoder.queueInputBuffer(inputBufIndex, 0, (frameData.length > dataLen) ? dataLen : frameData.length, ptsUsec, 0);
            if (VERBOSE)
                Log.d(TAG, "submitted frame to enc (length=" + frameData.length + ") pts=" + pts + " computePresentationTime(pts)=" + ptsUsec);
        } else {
            // either all in use, or we timed out during initial setup
            //if (VERBOSE)
            Log.d(TAG, "input buffer not available. drop input frame...");
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

                /*
                 * prepare muxer to save encoded data into a file
                 */
                if( SAVETOFILE ) {
                    //MediaFormat newFormat = encoder.getOutputFormat();
                    // now that we have the Magic Goodies, start the muxer
                    mAudioTrackIndex.index = mMuxer.addTrack(newFormat);
                    numTracksAdded++;
                    Log.d(TAG, "encoder output format changed: " + newFormat + ". Added track index: " + mAudioTrackIndex.index);
                    if (numTracksAdded == /*TOTAL_NUM_TRACKS*/1) {
                        mMuxer.start();
                        mMuxerStarted = true;
                        Log.i(TAG, "All tracks added. Muxer started");
                    }
                }

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

                if ( /*listener*/true) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        //if (VERBOSE)
                            Log.d(TAG, "info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 1 ...");

                        if( SAVETOFILE ) {
                            mMuxer.writeSampleData(mAudioTrackIndex.index, encodedData, info);
                        }

                        //save this metadata and use it in passing every key_frame
                        //captureH264MetaData(encodedData, info);

                        //insert ADTS header
                        ByteBuffer newEncodedData = ByteBuffer.allocateDirect(7+info.size);
                        byte[] header = new byte[7];
                        addADTStoPacket(header, 7+info.size);
                        newEncodedData.put(header);
                        byte[] encdata = new byte[info.size];
                        encodedData.get(encdata, 0, info.size);
                        newEncodedData.position(7);
                        newEncodedData.put(encdata, 0, info.size);
                        newEncodedData.position(0);

                        onAACMediaCodecEncodedFrame2(newEncodedData, 7+info.size, info.presentationTimeUs /*mEncodedIndex++*/, /*b_keyframe*/-1);

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

                        if( SAVETOFILE ) {
                            mMuxer.writeSampleData(mAudioTrackIndex.index, encodedData, info);
                        }

                        if (b_keyframe == 1) {
                            //packageH264Keyframe(encodedData, info);
                            //onH264MediaCodecEncodedFrame2(mH264Keyframe, mH264MetaSize + info.size, info.presentationTimeUs /*mEncodedIndex++*/, /*b_keyframe*/1);
                            //onAACMediaCodecEncodedFrame(...);
                        } else {
                            //onH264MediaCodecEncodedFrame2(encodedData, info.size, info.presentationTimeUs /*mEncodedIndex++*/, /*b_keyframe*/0);
                            //onAACMediaCodecEncodedFrame(...);
                        }

                        //insert ADTS header - bad
//                        byte[] data = new byte[7 + info.size];  //7 : space for ADTS header
//                        addADTStoPacket(data, info.size);
//                        encodedData.get(data, 7, info.size);
//                        //ByteBuffer newEncodedData = ByteBuffer.wrap(data);    //not safe, use allocateDirect()
//                        ByteBuffer newEncodedData = ByteBuffer.allocateDirect(7+info.size);
//                        newEncodedData.wrap(data);

                        //insert ADTS header - good
                        ByteBuffer newEncodedData = ByteBuffer.allocateDirect(7+info.size);
                        byte[] header = new byte[7];
                        addADTStoPacket(header, 7+info.size);
                        newEncodedData.put(header);
                        byte[] encdata = new byte[info.size];
                        encodedData.get(encdata, 0, info.size);
                        newEncodedData.position(7);
                        newEncodedData.put(encdata, 0, info.size);
                        newEncodedData.position(0);

                        onAACMediaCodecEncodedFrame2(newEncodedData, 7+info.size, info.presentationTimeUs /*mEncodedIndex++*/, /*b_keyframe*/-1);
                    }
                }

                encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                encoder.releaseOutputBuffer(encoderStatus, false);

//                    encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
//                } while( !encoderDone && (encoderStatus >= 0) ); //while
            } //last else
        } //if (!encoderDone)
    }

    /*
     * https://stackoverflow.com/questions/18862715/how-to-generate-the-aac-adts-elementary-stream-with-android-mediacodec
     *  > ADTS spec : https://wiki.multimedia.cx/index.php/MPEG-4_Audio#Sampling_Frequencies
     *
     *  Add ADTS header at the beginning of each and every AAC packet.
     *  This is needed as MediaCodec encoder generates a packet of raw
     *  AAC data.
     *
     *  Note the packetLen must count in the ADTS header itself.
     *
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = getADTSFreqIdx(mSampleRate);  //4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }

    private int getADTSFreqIdx( int sampleRate ) {
        /*
        0: 96000 Hz
        1: 88200 Hz
        2: 64000 Hz
        3: 48000 Hz
        4: 44100 Hz
        5: 32000 Hz
        6: 24000 Hz
        7: 22050 Hz
        8: 16000 Hz
        9: 12000 Hz
        10: 11025 Hz
        11: 8000 Hz
        12: 7350 Hz
        */

        int[] sampleRates = {
                96000, 88200, 64000, 48000, 44100,
                32000, 24000, 22050, 16000, 12000,
                11025, 8000,  7350
        };

        int idx = 4;    //default to 44100 Hz
        for( int i=0; i<sampleRates.length; i++ ) {
            if( sampleRate == sampleRates[i] ) {
                idx = i;
                break;
            }
        }
        return idx;
    }

    private int getADTSChannelConfig( int numChannels ) {
        /*
            0: Defined in AOT Specifc Config
            1: 1 channel: front-center
            2: 2 channels: front-left, front-right
            3: 3 channels: front-center, front-left, front-right
            4: 4 channels: front-center, front-left, front-right, back-center
            5: 5 channels: front-center, front-left, front-right, back-left, back-right
            6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
            7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
            8-15: Reserved
         */
        int idx = 2; //set default to stereo

        return idx;
    }

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

//        int N = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, N*2);
//        mAudioRecorder.startRecording();

        /*MyMICAudio*/ myMICAudio = new MyMICAudio();
        myMICAudio.start();

    }

    class MyMICAudio extends Thread
    {
        private static final String TAG = "MyMICAudio";
        private boolean stopped = false;

        private MyMICAudio()
        {
            //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            int N = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, N*2);

            if( mAudioRecorder.getState() != mAudioRecorder.STATE_INITIALIZED ) {
                Log.e( TAG, "--> AudioRecord not yet initialized !!!");
            }
        }

        @Override
        public void run() {
                mAudioRecorder.startRecording();
        }
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

    private byte[] generateFrame(int frameIndex) {
        return createSinWaveBuffer( 1000, 20 );
    }

    /*
     * generate sine wave :
     *  refer to : https://stackoverflow.com/questions/8632104/sine-wave-sound-generator-in-java
     */
    public byte[] createSinWaveBuffer(double freq, int ms) {
        //int samples = (int) ((double)(ms*1000) / (double)(1000000/mSampleRate));                      //noisy
        //int samples = (int) ((double)((double)ms*1000) / (double)(1000000/(double)mSampleRate));      //clean
        int samples = (int)((ms * mSampleRate) / 1000); //equal to above
        byte[] output = new byte[samples];

        double period = (double)mSampleRate / freq;
        for (int i = 0; i < output.length; i++) {
            double angle = 2.0 * Math.PI * i / period;
            output[i] = (byte)(Math.sin(angle) * 127f);  }

        return output;
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

    private static void printCodecProfileLevel(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        MediaCodecInfo.CodecProfileLevel profileLevels[] = capabilities.profileLevels;
        for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
            int profile = profileLevel.profile;
            int level = profileLevel.level;
            Log.e(TAG, "printCodecProfileLevel() : profile = " + profile + " level = " + level);
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        if( mEncodeTest ) {
            return frameIndex * 20000;  //20msec for encodeTest();
        } else {
            long fidx = (long) ((double)frameIndex / (double)mFrameSize);
            if( VERBOSE ) Log.d(TAG, "---> computePresentationTime() : fidx = " + fidx);
            return fidx * 20000; //20msec
        }
    }
}
