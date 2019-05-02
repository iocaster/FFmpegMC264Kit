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

package com.example.appexample1;

import android.Manifest;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import kim.yt.ffmpegmc264.MC264Encoder;


public class MainActivity extends AppCompatActivity
        implements MC264Encoder.YUVFrameListener
{
    EditText urlSrc;
    private View spinnerContainer;
    private boolean spinnerActive = false;

    private MyTask ffmpeg_task = null;
    private static MC264Encoder mMC264Encoder;
    private static int ffmpeg_retcode = 0;

    /*
     * To draw a progress monitor view with YUVFrame
     */
    private static ImageView monitorView;
    private static ArrayList mFrameList = new ArrayList();
    private static int mWidth, mHeight;
    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1){
                if( ! mFrameList.isEmpty() ) {
                    byte[] frameData = (byte[]) mFrameList.get(0);
                    drawYUVFrame( frameData, mWidth, mHeight );
                    mFrameList.remove(0);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        /*
         * request an android permission : WRITE_EXTERNAL_STORAGE
         * It is required when to save the ffmpeg output into a file.
         */
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1337);

        mMC264Encoder = new MC264Encoder();
        mMC264Encoder.setYUVFrameListener(this, false);

        EditText usagelabel = findViewById(R.id.usageLabel);
        //usagelabel.setFocusable(false);     //show normally but not editable
        usagelabel.setEnabled(false);        //show as gray

        EditText usagebody = findViewById(R.id.usageBody);
        //usagebody.setFocusable(false);     //show normally but not editable
        usagebody.setEnabled(false);        //show as gray

        EditText usagebody2 = findViewById(R.id.usageBody2);
        //usagebody2.setFocusable(false);     //show normally but not editable
        usagebody2.setEnabled(false);        //show as gray

        urlSrc = findViewById(R.id.editText4url);
        Button process = findViewById(R.id.btn_process);
        Button btn_stop = findViewById(R.id.btn_stop);
        /*ImageView*/ monitorView = findViewById(R.id.imageView);

        String defaultCmd = getResources().getString(R.string.ffmpeg_cmd_pc1);
        String savedCmd = PreferenceManager.getDefaultSharedPreferences(this).getString("cmd", defaultCmd);
        if( savedCmd != null && savedCmd.length() > 6 ) // 6 == sizeof("ffmpeg");
            urlSrc.setText( savedCmd );
        else {
            urlSrc.setText(defaultCmd);
            saveCmd(defaultCmd);
        }

        /*
         * NOTE :
         * To control spinner in onConfigurationChanged(),
         * you should set this option - android:configChanges - into <activity ...> from AndroidManifest.xml :
         *  <Activity ... android:configChanges="orientation|keyboardHidden|screenSize">
         */
        spinnerContainer = findViewById(R.id.v_spinner_container);
        spinnerContainer.setVisibility(View.VISIBLE);

        process.setOnClickListener(this::processClicked);
        btn_stop.setOnClickListener(this::btnStopClicked);

        spinnerContainer.setVisibility(View.GONE);
    }

    private void saveCmd( String cmdStr ) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("cmd", cmdStr ).apply();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if( !spinnerActive )
            spinnerContainer.setVisibility(View.GONE);
        else
            spinnerContainer.setVisibility(View.VISIBLE);
    }

    private void processClicked(View ignored) {
        Toast.makeText(this, "START Clicked !!!", Toast.LENGTH_SHORT).show();

        spinnerContainer.setVisibility(View.VISIBLE);
        spinnerActive = true;

        String fullUrl = new String("") + urlSrc.getText();
        String[] sArrays = fullUrl.split("\\s+");   //+ : to remove duplicate whitespace
        saveCmd(fullUrl);

        ffmpeg_task = new MyTask(this);
        ffmpeg_task.execute( sArrays );
    }

    private int stopCnt = 0;
    private void btnStopClicked(View ignored) {
        Toast.makeText(this, "STOP Clicked !!!", Toast.LENGTH_SHORT).show();

        String fullUrl = new String("") + urlSrc.getText();
        saveCmd(fullUrl);

        mMC264Encoder.ffmpegStop();
//        mMC264Encoder.reset();     //Please, call mMC264Encoder.reset() inside onPostExecute() not here

        if( ++stopCnt >= 3 )
            mMC264Encoder.ffmpegForceStop();
    }

    void finished() {
        Toast.makeText(this, "ffmpeg finished !!!  (retcode = " + ffmpeg_retcode + ")", Toast.LENGTH_SHORT).show();
        spinnerContainer.setVisibility(View.GONE);
        spinnerActive = false;

        ffmpeg_task = null;
        ffmpeg_retcode = 0;
        stopCnt = 0;
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



    @Override
    public void onYUVFrame(byte[] frameData, int width, int height) {
        mFrameList.add( frameData );
        mWidth = width;
        mHeight = height;

        Message msg = new Message();
        msg.what = 1;
        mHandler.sendMessage( msg );
    }

    public static void drawYUVFrame(byte[] frameData, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(frameData, ImageFormat.NV21, width, height, null);
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        monitorView.setImageBitmap(image);
    }
}
