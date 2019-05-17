/*
 * This source code is based on these pages :
 *  https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/media/projection/MediaProjectionDemo.java
 *  https://android.googlesource.com/platform/development/+/master/samples/ApiDemos
 */
package com.example.appexample2;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.appexample2.camerapreview.CameraActivity;

import java.util.ArrayList;
import java.util.List;

import kim.yt.ffmpegmc264.MC264ScreenRecorder;

public class MainActivity2 extends AppCompatActivity
{
    private static final String TAG = "MC264ScreenRecorderDemo";
    //private static final int PERMISSION_CODE = 1;
    private static final List<Resolution> RESOLUTIONS = new ArrayList<Resolution>() {{
        //4:3
        //add(new Resolution(1920,1440));       //too big size to encoder : instead use 1440 x 1080
        add(new Resolution(1440,1080));
        add(new Resolution(1366,1024));
        add(new Resolution(1600,1200));
        add(new Resolution(960,720));
        add(new Resolution(720,540));
        add(new Resolution(320,240));
        //16:9
        add(new Resolution(1920,1080));
        add(new Resolution(1366,768));
        add(new Resolution(1600,900));
        add(new Resolution(960,540));
        add(new Resolution(720,405));
    }};

    private int mDisplayWidth;
    private int mDisplayHeight;

    private Button btnStart, btnStop, btnHide;
    private CheckBox chkCaptureFromCamera;
    private CheckBox chkVideoMode;  //landscape mode
    private EditText capDst;

    private static MC264ScreenRecorder mMC264Recorder;

    private ContextWrapper mCtx;
    private class MyMC264RecorderCallback extends MC264ScreenRecorder.Callback
    {
        @Override
        public void onStart() {
            hideMe();

            /*
             * launch camera preview app if user selects the camera streaming
             */
            if( chkCaptureFromCamera.isChecked() ) {
                Intent intent = new Intent(mCtx, CameraActivity.class);
                if (chkVideoMode.isChecked()) {
                    intent.putExtra("req_landscape", 1);
                } else {
                    intent.putExtra("req_landscape", 0);
                }
                startActivity(intent);
            }
        }

        @Override
        public void onStop( int retcode ) {
            Log.d(TAG, "MC264ScreenRecorder.Callback::onStop() ... retcode = " + retcode );

            if( retcode == 0 ) {
                Toast.makeText(mCtx,
                        "Normal finished : retcode = " + retcode, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mCtx,
                        "Error finished : retcode = " + retcode, Toast.LENGTH_LONG).show();
            }
            btnStart.setEnabled(true);
            ScreenRecorderNotification.cancel(mCtx);
            //showMe();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "---> onCreate() ..." );
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_projection);

        mCtx = this;

        /*
         * request an android permission : WRITE_EXTERNAL_STORAGE
         * It is required when to save the ffmpeg output into a file.
         */
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1337);

        //getDeviceScreenSize();
        ArrayAdapter<Resolution> arrayAdapter = new ArrayAdapter<Resolution>(
                this, android.R.layout.simple_list_item_1, RESOLUTIONS);
        Spinner s = (Spinner) findViewById(R.id.spinner);
        s.setAdapter(arrayAdapter);
        s.setOnItemSelectedListener(new ResolutionSelector());
        s.setSelection(0);

        btnStart = findViewById(R.id.button_start);
        btnStop = findViewById(R.id.button_stop);
        btnHide = findViewById(R.id.button_hide);
        chkCaptureFromCamera = findViewById(R.id.checkBox_CaptureCamera);
        chkVideoMode = findViewById(R.id.checkBox_VideoMode);
        capDst = findViewById(R.id.editText_capDst);

        String defaultDst = getResources().getString(R.string.cap_dst_ussage2);
        String savedDst = PreferenceManager.getDefaultSharedPreferences(this).getString("dst", defaultDst);
        capDst.setText( savedDst );

        mMC264Recorder = new MC264ScreenRecorder();
        mMC264Recorder.registerCallback( new MyMC264RecorderCallback() );
        mMC264Recorder.init( this );
    }

//    @Override
//    protected void onStop() {
//        Log.d(TAG, "---> onStop() ..." );
//        super.onStop();
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "---> onDestroy() ..." );
        mMC264Recorder.release();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Finish or Not");
        builder.setMessage("Do you want to finish? ");

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //MainActivity2.super.onBackPressed();
            }
        });
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                MainActivity2.super.onBackPressed();
            }
        });

        builder.show();
    }

    private void getDeviceScreenSize() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        RESOLUTIONS.add(new Resolution(size.x,size.y));
        RESOLUTIONS.add(new Resolution(size.y,size.x));

        Log.i( TAG, "---> getDeviceScreenSize() : width x height = " + size.x + " x " + size.y );
    }

    private void saveDst( String dstStr ) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("dst", dstStr ).apply();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "---> onActivityResult() ..." );
        mMC264Recorder.onActivityResult(requestCode, resultCode, data);
    }

    private class ResolutionSelector implements Spinner.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
            Resolution r = (Resolution) parent.getItemAtPosition(pos);
            mDisplayHeight = r.y;
            mDisplayWidth = r.x;
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) { /* Ignore */ }
    }

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

    public void onBtnStart(View view) {
        String capDstStr = new String("") + capDst.getText();
        saveDst(capDstStr);

        mMC264Recorder.setCaptureSize( mDisplayWidth, mDisplayHeight );
        mMC264Recorder.setDst(capDstStr);
        if( chkVideoMode.isChecked() )
            mMC264Recorder.setLandscapeMode( true );
        else
            mMC264Recorder.setLandscapeMode( false );
        mMC264Recorder.start();

        String capModeStr;
        if( chkVideoMode.isChecked() )
            capModeStr = new String("Capture as Landscape : " + mDisplayWidth + "x" + mDisplayHeight);
        else
            capModeStr = new String("Capture as Portrait : " + mDisplayHeight + "x" + mDisplayWidth);

        btnStart.setEnabled(false);
        ScreenRecorderNotification.notify(this, capModeStr, 4747);
        //hideMe();     //moved into MC264ScreenRecorder.Callback::onStart();
    }

    public void onBtnStop(View view) {
        mMC264Recorder.stop();
    }

    public void onCheckLandscape(View view) {
        if (chkVideoMode.isChecked()) {
            Toast.makeText(this,
                    "Turn your phone to LANDSCAPE before click START !!!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,
                    "Turn your phone to PORTRAIT before click START !!!", Toast.LENGTH_LONG).show();
        }
    }

    private void hideMe() {
        //send this app to background
        Intent i = new Intent();
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        this.startActivity(i);
    }
    private void showMe() {
        Intent intent = new Intent(this, MainActivity2.class);
        startActivity(intent);
    }
    public void onBtnHide(View view) {
        hideMe();
    }
}
