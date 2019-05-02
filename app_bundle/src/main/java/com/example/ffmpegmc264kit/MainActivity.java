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

package com.example.ffmpegmc264kit;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.example.ffmpegmc264kit.example1.Example1MainActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
         * request an android permission : WRITE_EXTERNAL_STORAGE
         * It is required when to save the ffmpeg output into a file.
         */
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1337);

        Button btn_app1 = findViewById(R.id.btn_app1);
        Button btn_app2 = findViewById(R.id.btn_app2);

        btn_app1.setOnClickListener(this::app1Clicked);
        btn_app2.setOnClickListener(this::app2Clicked);
    }

    private void app1Clicked(View ignored) {
        //Toast.makeText(this, "App1 Clicked !!!", Toast.LENGTH_SHORT).show();

        //Launch internal Activity - OK
        Intent intent = new Intent(this, Example1MainActivity.class);
        startActivity(intent);

        //Launch internal Activity - Failed
//        Intent intent = new Intent();
//        intent.setComponent(new ComponentName("com.example.ffmpegmc264kit.example1", "com.example.ffmpegmc264kit.example1.Example1MainActivity"));
//        startActivity(intent);

        //Launch outside app - OK
//        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.example.appexample1");
//        if (launchIntent != null) {
//            startActivity(launchIntent);//null pointer check in case package name was not found
//        }
    }

    private void app2Clicked(View ignored) {

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.example.appexample2", "com.example.appexample2.MainActivity"));
        startActivity(intent);

//        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.example.appexample2");
//        if (launchIntent != null) {
//            startActivity(launchIntent);//null pointer check in case package name was not found
//        }
    }
}
