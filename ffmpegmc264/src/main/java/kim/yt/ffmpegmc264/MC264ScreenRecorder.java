/*
 * This source code is based on these pages :
 *  https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/media/projection/MediaProjectionDemo.java
 *  https://android.googlesource.com/platform/development/+/master/samples/ApiDemos
 */
package kim.yt.ffmpegmc264;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.util.Log;

import java.lang.ref.WeakReference;


import static android.app.Activity.RESULT_OK;

public class MC264ScreenRecorder
        //extends AppCompatActivity
        //implements MC264Encoder.YUVFrameListener
{
    private static final String TAG = "MC264ScreenRecorder";
    private static final int PERMISSION_CODE = 1;

    private static boolean ENABLE_MIC_AUDIO = false;

    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private int mDisplayWidth0, mDisplayWidth;
    private int mDisplayHeight0, mDisplayHeight;
    private boolean mScreenSharing;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private MyTask ffmpeg_task = null;
    private static MC264Encoder mMC264Encoder;
    private static MCAACEncoder mMCAACEncoder;
    private static LibFFmpegMC264 mLibFFmpeg;
    private static int ffmpeg_retcode = 0;

    private Activity mActivity;
    private String mDstUrl;
    private boolean mLandscapeMode = false;


    public static abstract class Callback {
        public void onStart() {}
        public void onStop( int retcode ) {}
    }
    private Callback mCallback;
    public void registerCallback( Callback cb ) {
        mCallback = cb;
    }

//    public void setActivity( Activity activity  ) {
//        if( activity == null ) {
//            Log.e(TAG, "---> MC264ScreenRecorder() : Error, ctx is null !!!" );
//            return;
//        }
//        mActivity = activity;
//    }

    public void init( Activity activity ) {
        if( activity == null ) {
            Log.e(TAG, "---> MC264ScreenRecorder() : Error, ctx is null !!!" );
            return;
        }
        mActivity = activity;

        Log.d(TAG, "---> MC264ScreenRecorder() ..." );

//        /*
//         * request an android permission : WRITE_EXTERNAL_STORAGE
//         * It is required when to save the ffmpeg output into a file.
//         */
//        ActivityCompat.requestPermissions(activity,
//                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
//                1337);

        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mProjectionManager =
                (MediaProjectionManager) mActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

//        mMC264Encoder = new MC264Encoder();
//        //mMC264Encoder.setYUVFrameListener(this, true);
//        mMC264Encoder.enableScreenGrabber(true);
//
//        mMCAACEncoder = new MCAACEncoder();
//        //mMCAACEncoder.enableScreenGrabber(true);  //moved into Task because too heavy MIC recording thread

        mLibFFmpeg = new LibFFmpegMC264();
        mLibFFmpeg.enableScreenCapture(true);

//moved into includeMICCapture()
//        if(ENABLE_MIC_AUDIO)
//            mLibFFmpeg.enableMICCapture(true);

    }

    public void release() {
        Log.d(TAG, "---> release() ..." );
        stopCapture();
        //stopScreenSharing(); //blocked because it is called at mMediaProjection's callback onStop()
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void saveDst( String dstStr ) {
        PreferenceManager.getDefaultSharedPreferences(mActivity).edit().putString("dst", dstStr ).apply();
    }

    private void stopCapture() {
//        mMC264Encoder.ffmpegStop();
        mLibFFmpeg.Stop();
    }

    private void startCapture() {
        //String fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vcodec mc264 -b:v 2.0M -s 1366x768 -an -f mpegts udp://192.168.123.123:5555?pkt_size=1316&buffer_size=655360" );

        String capDstStr = mDstUrl;

        /*
        Rotate 90 clockwise:
            ffmpeg -i in.mov -vf "transpose=1" out.mov
            0 = 90CounterCLockwise and Vertical Flip (default)
            1 = 90Clockwise
            2 = 90CounterClockwise
            3 = 90Clockwise and Vertical Flip
         */
        String fullUrl;
        if( mLandscapeMode ) {
            if( !ENABLE_MIC_AUDIO )
                fullUrl = new String("ffmpeg -probesize 32 -f lavfi -re -i rgbtestsrc=size=160x120:rate=30 -pix_fmt yuv420p -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -an " + capDstStr );
            else
                fullUrl = new String("ffmpeg -probesize 32 -filter_complex_threads 2 -f lavfi -re -i sine=frequency=1000:sample_rate=44100 -f lavfi -re -i rgbtestsrc=size=160x120:rate=15 -pix_fmt yuv420p -map 1:v -map 0:a -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -acodec mcaac " + capDstStr );
        } else { //rotate for HomeScreen Mode
            if( !ENABLE_MIC_AUDIO )
                fullUrl = new String("ffmpeg -probesize 32 -filter_complex_threads 2 -f lavfi -re -i rgbtestsrc=size=160x120:rate=30 -pix_fmt yuv420p -vf transpose=1 -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -an " + capDstStr);
            else
                fullUrl = new String("ffmpeg -probesize 32 -filter_complex_threads 3 -f lavfi -re -i sine=frequency=1:sample_rate=44100 -f lavfi -re -i rgbtestsrc=size=160x120:rate=15 -pix_fmt yuv420p -vf transpose=1 -map 1:v -map 0:a -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -acodec mcaac " + capDstStr);
        }
        Log.d( TAG, "URL = " + fullUrl );

        String[] sArrays = fullUrl.split("\\s+");   //+ : to remove duplicate whitespace

        ffmpeg_task = new MyTask(mActivity, this);
        ffmpeg_task.execute( sArrays );
    }

    private void callCallbackStart() {
        if( this.mCallback != null )
            this.mCallback.onStart();
    }
    private void callCallbackStop( int retcode ) {
        if( this.mCallback != null )
            this.mCallback.onStop(retcode);
    }

    //client should call me instead of Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "---> onActivityResult() ..." );
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "onActivityResult() : Unknown request code: " + requestCode);
            //callCallbackStop(-1);
            stopCapture();      //stop ffmpeg
            return;
        }
        if (resultCode != RESULT_OK) {
//            Toast.makeText(mActivity,
//                    "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
            Log.e(TAG,"onActivityResult() : User denied screen sharing permission");
            //callCallbackStop(-1);
            stopCapture();      //stop ffmpeg
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
        mVirtualDisplay = createVirtualDisplay();
        callCallbackStart();
    }

    private void shareScreen() {
        Log.d(TAG, "---> shareScreen() ..." );
        mScreenSharing = true;
        if (mMediaProjection == null) {
            mActivity.startActivityForResult(mProjectionManager.createScreenCaptureIntent(),
                    PERMISSION_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
    }

    private void stopScreenSharing() {
        Log.d(TAG, "---> stopScreenSharing() ..." );
        mScreenSharing = false;
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private VirtualDisplay createVirtualDisplay() {
//        return mMediaProjection.createVirtualDisplay("MC264ScreenRecorder",
//                mDisplayWidth, mDisplayHeight, mScreenDensity,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
//                /*mSurface*/ mMC264Encoder.getEncoderSurface(), null /*Callbacks*/, null /*Handler*/);

        return mMediaProjection.createVirtualDisplay("MC264ScreenRecorder",
                mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                /*mSurface*/ mLibFFmpeg.getMC264Encoder().getEncoderSurface(), null /*Callbacks*/, null /*Handler*/);
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.d(TAG, "---> MediaProjectionCallback :: onStop() ..." );
            mMediaProjection = null;
            stopScreenSharing();
        }
    }

    void finished() {
        //Toast.makeText(this, "ffmpeg finished !!!  (retcode = " + ffmpeg_retcode + ")", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "ffmpeg finished !!!  (retcode = " + ffmpeg_retcode + ")" );

        ffmpeg_task = null;
        ffmpeg_retcode = 0;
    }

    private static class MyTask extends AsyncTask<String, Void, Void> {
        private final WeakReference<Activity> activityWeakReference;
        MC264ScreenRecorder mc264recorder;

        MyTask(Activity activity, MC264ScreenRecorder recorder) {
            activityWeakReference = new WeakReference<>(activity);
            mc264recorder = recorder;
        }

        @Override
        protected Void doInBackground(String... strings) {
//            mMC264Encoder.H264MediaCodecReady();
//            if( ENABLE_MIC_AUDIO ) {
//                mMCAACEncoder.enableScreenGrabber(true);    //moved here from init() <- onCreate()
//                mMCAACEncoder.AACMediaCodecReady();
//            }
//            ffmpeg_retcode = mMC264Encoder.ffmpegRun(strings);

            int tid=android.os.Process.myTid();
            Log.d(TAG,"--1> priority before change = " + android.os.Process.getThreadPriority(tid));
            Log.d(TAG,"--1> priority before change = "+Thread.currentThread().getPriority());

            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);

            Log.d(TAG,"--2> priority after change = " + android.os.Process.getThreadPriority(tid));
            Log.d(TAG,"--2> priority after change = " + Thread.currentThread().getPriority());

            mLibFFmpeg.Ready();
            ffmpeg_retcode = mLibFFmpeg.Run(strings);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
//            mMC264Encoder.reset();
//            if( ENABLE_MIC_AUDIO )
//                mMCAACEncoder.reset();
            mLibFFmpeg.Reset();

//            final Activity activity = activityWeakReference.get();
//            if (activity != null) {
//                activity.finished();
//            }

            //finished();
            if( mc264recorder != null ) {
                if( mc264recorder.mCallback != null )
                    mc264recorder.mCallback.onStop(ffmpeg_retcode);
            }
        }
    }

    public void setCaptureSize( int width, int height ) {
        mDisplayWidth0 = mDisplayWidth = width;
        mDisplayHeight0 = mDisplayHeight = height;
    }

    public void setLandscapeMode( boolean value ) {
        mLandscapeMode = value;

        if( mLandscapeMode )    //wide mode
        {
            mDisplayHeight = mDisplayHeight0;
            mDisplayWidth = mDisplayWidth0;
            Log.d( TAG, "---> setLandscapeMode() : mLandscapeMode == true, WxH = " + mDisplayWidth + "x" + mDisplayHeight);
        } else {                //narrow mode & ffmpeg should rotate the screen
            mDisplayHeight = mDisplayWidth0;
            mDisplayWidth = mDisplayHeight0;
            Log.d( TAG, "---> setLandscapeMode() : mLandscapeMode == false , WxH = " + mDisplayWidth + "x" + mDisplayHeight);
        }
    }

    public void includeMICCapture( boolean value ) {
        if( value ) {
            ENABLE_MIC_AUDIO = true;
            mLibFFmpeg.enableMICCapture(true);
        } else {
            ENABLE_MIC_AUDIO = false;
            mLibFFmpeg.enableMICCapture(false);
        }
    }

    public void setDst( String dstUrl ) {
        mDstUrl = dstUrl;
    }

    public void start() {
        if( mDisplayWidth <= 0 || mDisplayHeight <= 0 ) {
            Log.e(TAG, "Error, Capture size isn't set yet !!!");
            callCallbackStop(-1);
            return;
        }
        if( mDstUrl == null ) {
            Log.e(TAG, "Error, Capture DST isn't set yet, where record or stream to !!!");
            callCallbackStop(-1);
            return;
        }

        startCapture();     //start ffmpeg : should be called before shareScreen();
        shareScreen();
    }

    public void start( int width, int height, String dstUrl ) {
        setCaptureSize( width, height );
        setDst(dstUrl);
        start();
    }

    public void stop() {
        stopScreenSharing();
        stopCapture();      //stop ffmpeg
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

}
