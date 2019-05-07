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

    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private int mDisplayWidth0, mDisplayWidth;
    private int mDisplayHeight0, mDisplayHeight;
    private boolean mScreenSharing;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private MyTask ffmpeg_task = null;
    private static MC264Encoder mMC264Encoder;
    private static int ffmpeg_retcode = 0;

    private Activity mActivity;
    private String mDstUrl;
    private boolean mLandscapeMode = false;

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

        mMC264Encoder = new MC264Encoder();
        //mMC264Encoder.setYUVFrameListener(this, true);
        mMC264Encoder.enableScreenGrabber(true);
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
        mMC264Encoder.ffmpegStop();
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
            fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -an " + capDstStr );
        } else { //rotate for HomeScreen Mode
            fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vf transpose=1 -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -an " + capDstStr);
        }
        //Log.d( TAG, "URL = " + fullUrl );

        String[] sArrays = fullUrl.split("\\s+");   //+ : to remove duplicate whitespace

        ffmpeg_task = new MyTask(mActivity);
        ffmpeg_task.execute( sArrays );
    }

    //@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "---> onActivityResult() ..." );
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "onActivityResult() : Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
//            Toast.makeText(mActivity,
//                    "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
            Log.e(TAG,"onActivityResult() : User denied screen sharing permission");
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
        mVirtualDisplay = createVirtualDisplay();
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
        return mMediaProjection.createVirtualDisplay("MC264ScreenRecorder",
                mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                /*mSurface*/ mMC264Encoder.getEncoderSurface(), null /*Callbacks*/, null /*Handler*/);
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

        MyTask(Activity activity) {
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        protected Void doInBackground(String... strings) {
            mMC264Encoder.H264MediaCodecReady();
            ffmpeg_retcode = mMC264Encoder.ffmpegRun(strings);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mMC264Encoder.reset();
//            final Activity activity = activityWeakReference.get();
//            if (activity != null) {
//                activity.finished();
//            }

            //finished();
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

    public void setDst( String dstUrl ) {
        mDstUrl = dstUrl;
    }

    public void start() {
        if( mDisplayWidth <= 0 || mDisplayHeight <= 0 ) {
            Log.e(TAG, "Error, Capture size isn't set yet !!!");
            return;
        }
        if( mDstUrl == null ) {
            Log.e(TAG, "Error, Capture DST isn't set yet, where record or stream to !!!");
            return;
        }

        startCapture();     //start ffmpeg
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
