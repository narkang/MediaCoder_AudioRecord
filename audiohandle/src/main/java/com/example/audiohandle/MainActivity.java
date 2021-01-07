package com.example.audiohandle;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.example.audiohandle.util.MusicProcess;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    MusicProcess musicProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();
        musicProcess = new MusicProcess();
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);

        }
        return false;
    }


    public void musicMix(View view){
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String aacPath = new File(Environment.getExternalStorageDirectory(), "music.mp3").getAbsolutePath();
                final String videoPath  = new File(Environment.getExternalStorageDirectory(), "input2.mp4").getAbsolutePath();

                try {
                    copyAssets("music.mp3", aacPath);
                    copyAssets("input2.mp4", videoPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                final String outPathPcm = new File(Environment.getExternalStorageDirectory(), "outPut.wav").getAbsolutePath();
                try {
                    musicProcess.mixAudioTrack(MainActivity.this, videoPath, aacPath,
                            outPathPcm, 60 * 1000 * 1000, 70 * 1000 * 1000,
                            100,//0 - 100
                            10);//
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void musicClip(View view){
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String aacPath = new File(Environment.getExternalStorageDirectory(), "music.mp3").getAbsolutePath();

                try {
                    copyAssets("music.mp3", aacPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                final String outPathPcm = new File(Environment.getExternalStorageDirectory(), "outPut.pcm").getAbsolutePath();
                final String outPathWav = new File(Environment.getExternalStorageDirectory(), "outPut.wav").getAbsolutePath();
                try {
                    musicProcess.decodeToPCM(aacPath, outPathPcm, 60 * 1000 * 1000, 70 * 1000 * 1000);
                    musicProcess.convertPcmToWav(outPathPcm, outPathWav);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void videoMix(View view){
        startActivity(new Intent(this, VideoMixActivity.class));
    }

    private void copyAssets(String assetsName, String path) throws IOException {
        AssetFileDescriptor assetFileDescriptor = getAssets().openFd(assetsName);
        FileChannel from = new FileInputStream(assetFileDescriptor.getFileDescriptor()).getChannel();
        FileChannel to = new FileOutputStream(path).getChannel();
        from.transferTo(assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength(), to);
    }
}