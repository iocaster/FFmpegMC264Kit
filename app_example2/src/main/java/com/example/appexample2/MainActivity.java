/*
 * This source code is based on these pages :
 *  https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/media/projection/MediaProjectionDemo.java
 *  https://android.googlesource.com/platform/development/+/master/samples/ApiDemos
 */
package com.example.appexample2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import kim.yt.ffmpegmc264.MC264Encoder;

public class MainActivity extends AppCompatActivity
        implements MC264Encoder.YUVFrameListener
{
    private static final String TAG = "MediaProjectionDemo";
    private static final int PERMISSION_CODE = 1;
    private static final List<Resolution> RESOLUTIONS = new ArrayList<Resolution>() {{
        add(new Resolution(1366,1024));
        add(new Resolution(1600,1200));
        //add(new Resolution(1920,1440));
        add(new Resolution(960,720));
        add(new Resolution(720,540));
        add(new Resolution(320,240));
        add(new Resolution(1366,768));
        add(new Resolution(1600,900));
        //add(new Resolution(1920,1080));
        add(new Resolution(960,540));
        add(new Resolution(720,405));
    }};
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private boolean mScreenSharing;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
//    private Surface mSurface;
//    private SurfaceView mSurfaceView;
//    private ToggleButton mToggle;

    private Button btnStart, btnStop, btnHide;
    private CheckBox chkVideoMode;
    private EditText capDst;

    private MyTask ffmpeg_task = null;
    private static MC264Encoder mMC264Encoder;
    private static int ffmpeg_retcode = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "---> onCreate() ..." );
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_projection);

        /*
         * request an android permission : WRITE_EXTERNAL_STORAGE
         * It is required when to save the ffmpeg output into a file.
         */
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1337);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
//        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
//        mSurface = mSurfaceView.getHolder().getSurface();
        mProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        ArrayAdapter<Resolution> arrayAdapter = new ArrayAdapter<Resolution>(
                this, android.R.layout.simple_list_item_1, RESOLUTIONS);
        Spinner s = (Spinner) findViewById(R.id.spinner);
        s.setAdapter(arrayAdapter);
        s.setOnItemSelectedListener(new ResolutionSelector());
        s.setSelection(0);
//        mToggle = (ToggleButton) findViewById(R.id.screen_sharing_toggle);
//        mToggle.setSaveEnabled(false);

        btnStart = findViewById(R.id.button_start);
        btnStop = findViewById(R.id.button_stop);
        btnHide = findViewById(R.id.button_hide);
        chkVideoMode = findViewById(R.id.checkBox_VideoMode);
        capDst = findViewById(R.id.editText_capDst);

        chkVideoMode.setOnClickListener(new CheckBox.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Spinner s = (Spinner) findViewById(R.id.spinner);
//                s.setSelection(0);
                int w = mDisplayWidth;
                mDisplayWidth = mDisplayHeight;
                mDisplayHeight = w;
            }
        }) ;

        String defaultDst = getResources().getString(R.string.cap_dst_ussage2);
        String savedDst = PreferenceManager.getDefaultSharedPreferences(this).getString("dst", defaultDst);
        capDst.setText( savedDst );

        mMC264Encoder = new MC264Encoder();
        //mMC264Encoder.setYUVFrameListener(this, true);
        mMC264Encoder.enableScreenGrabber(true);
    }
//    @Override
//    protected void onStop() {
//        Log.d(TAG, "---> onStop() ..." );
////        stopScreenSharing();  //moved into onDestroy()
//        super.onStop();
//    }
    @Override
    public void onDestroy() {
        Log.d(TAG, "---> onDestroy() ..." );
        stopCapture();
        //stopScreenSharing(); //moved here from onStop() but blocked because it is called at mMediaProjection's callback onStop()
        super.onDestroy();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void saveDst( String dstStr ) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("dst", dstStr ).apply();
    }

    private void stopCapture() {
        mMC264Encoder.ffmpegStop();
    }

    private void startCapture() {
        //String fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vcodec mc264 -b:v 2.0M -s 1366x768 -an -f mpegts udp://192.168.123.123:5555?pkt_size=1316&buffer_size=655360" );
//        String fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vcodec mc264 -b:v 2.0M -s "
//                + mDisplayWidth + "x" + mDisplayHeight + " -an -f mpegts udp://192.168.123.123:5555?pkt_size=1316&buffer_size=655360" );

        String capDstStr = new String("") + capDst.getText();

        /*
        Rotate 90 clockwise:
            ffmpeg -i in.mov -vf "transpose=1" out.mov
            0 = 90CounterCLockwise and Vertical Flip (default)
            1 = 90Clockwise
            2 = 90CounterClockwise
            3 = 90Clockwise and Vertical Flip
         */
        String fullUrl;
        if( chkVideoMode.isChecked() ) {
            fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -an " + capDstStr );
        } else { //rotate for HomeScreen Mode
            fullUrl = new String("ffmpeg -f lavfi -i testsrc -pix_fmt yuv420p -vf transpose=1 -vcodec mc264 -b:v 2.0M -s "
                    + mDisplayWidth + "x" + mDisplayHeight + " -an " + capDstStr);
        }

        //Log.d( TAG, "URL = " + fullUrl );

        String[] sArrays = fullUrl.split("\\s+");   //+ : to remove duplicate whitespace

        ffmpeg_task = new MyTask(MainActivity.this);
        ffmpeg_task.execute( sArrays );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "---> onActivityResult() ..." );
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
        mVirtualDisplay = createVirtualDisplay();
    }

    public void onToggleScreenShare(View view) {
        Log.d(TAG, "---> onToggleScreenShare() ... : ((ToggleButton) view).isChecked() = " + ((ToggleButton) view).isChecked() );
        if (((ToggleButton) view).isChecked()) {
            shareScreen();
        } else {
            stopScreenSharing();
        }
    }
    private void shareScreen() {
        Log.d(TAG, "---> shareScreen() ..." );
        mScreenSharing = true;
//        if (mSurface == null) {
//            return;
//        }
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(),
                    PERMISSION_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
    }
    private void stopScreenSharing() {
        Log.d(TAG, "---> stopScreenSharing() ..." );
//        if (mToggle.isChecked()) {
//            mToggle.setChecked(false);
//        }
        mScreenSharing = false;
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    @Override
    public void onYUVFrame(byte[] frameData, int width, int height) {
        Log.d(TAG, "---> onYUVFrame() ..." );

//        if( !mScreenSharing ) return;
//        if( mMediaProjection == null ) return;
//
//        if( mVirtualDisplay == null )
//            mVirtualDisplay = createVirtualDisplay();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenSharingDemo",
                mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                /*mSurface*/ mMC264Encoder.getEncoderSurface(), null /*Callbacks*/, null /*Handler*/);
    }
    private void resizeVirtualDisplay() {
        Log.d(TAG, "---> resizeVirtualDisplay() ..." );
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.resize(mDisplayWidth, mDisplayHeight, mScreenDensity);
    }
    private class ResolutionSelector implements Spinner.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
            Resolution r = (Resolution) parent.getItemAtPosition(pos);
//            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
//            if (getResources().getConfiguration().orientation
//                    == Configuration.ORIENTATION_LANDSCAPE)
            if( chkVideoMode.isChecked() )  //wide mode
            {
                mDisplayHeight = r.y;
                mDisplayWidth = r.x;
                Log.d( TAG, "---> onItemSelected() : chkVideoMode.isChecked() == true, WxH = " + mDisplayWidth + "x" + mDisplayHeight);
            } else {                        //narrow mode & ffmpeg should rotate the screen
                mDisplayHeight = r.x;
                mDisplayWidth = r.y;
                Log.d( TAG, "---> onItemSelected() : chkVideoMode.isChecked() == false , WxH = " + mDisplayWidth + "x" + mDisplayHeight);
            }
//            lp.height = mDisplayHeight;
//            lp.width = mDisplayWidth;
//            mSurfaceView.setLayoutParams(lp);
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) { /* Ignore */ }
    }
    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.d(TAG, "---> MediaProjectionCallback :: onStop() ..." );
            mMediaProjection = null;
            stopScreenSharing();
        }
    }
//    private class SurfaceCallbacks implements SurfaceHolder.Callback {
//        @Override
//        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//            Log.d(TAG, "---> surfaceChanged() : width x height = " + width + " x " + height );
//            mDisplayWidth = width;
//            mDisplayHeight = height;
//            resizeVirtualDisplay();
//        }
//        @Override
//        public void surfaceCreated(SurfaceHolder holder) {
//            Log.d(TAG, "---> surfaceCreated() ..." );
//            mSurface = holder.getSurface();
//            if (mScreenSharing) {
//                shareScreen();
//            }
//        }
//        @Override
//        public void surfaceDestroyed(SurfaceHolder holder) {
//            Log.d(TAG, "---> surfaceDestroyed() ..." );
//            if (!mScreenSharing) {
//                stopScreenSharing();
//            }
//        }
//    }
    private static class Resolution {
        int x;
        int y;
        public Resolution(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override
        public String toString() {
            return x + "x" + y;
        }
    }

    void finished() {
        Toast.makeText(this, "ffmpeg finished !!!  (retcode = " + ffmpeg_retcode + ")", Toast.LENGTH_SHORT).show();

        ffmpeg_task = null;
        ffmpeg_retcode = 0;
//        stopCnt = 0;

        if( mScreenSharing ) {
            mVirtualDisplay = null;     //will be set by onYUVFrame()
            startCapture();
        }
    }

    private static class MyTask extends AsyncTask<String, Void, Void> {
        private final WeakReference<MainActivity> activityWeakReference;

        MyTask(MainActivity activity) {
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
            final MainActivity activity = activityWeakReference.get();
            if (activity != null) {
                activity.finished();
            }
        }
    }

    public void onBtnStart(View view) {
        String capDstStr = new String("") + capDst.getText();
        saveDst(capDstStr);

        startCapture();     //start ffmpeg
        shareScreen();
    }

    public void onBtnStop(View view) {
        stopScreenSharing();
        stopCapture();      //stop ffmpeg
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    public void onBtnHide(View view) {
        //send this app to background
        Intent i = new Intent();
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        this.startActivity(i);
    }
}
