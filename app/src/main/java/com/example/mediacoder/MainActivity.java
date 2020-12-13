package com.example.mediacoder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.mediacoder.AudioRecord.AudioRecordActivity;
import com.example.mediacoder.Camera.CameraActivity;
import com.example.mediacoder.draw_image.DrawImageActivity;
import com.example.mediacoder.mediaExtractor.MediaExtractorActivity;
import com.example.mediacoder.mediaMuxer.MediaMuxerActivity;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    //三种方式绘制图片
    public void drawImageClick(View view){
        startActivity(new Intent(this, DrawImageActivity.class));
    }

    //使用 AudioRecord 采集并播放音频PCM
    public void collectPCMClick(View view){
        startActivity(new Intent(this, AudioRecordActivity.class));
    }

    //使用 Camera API 采集视频数据(TextureView)
    public void cameraClick(View view){
        startActivity(new Intent(this, CameraActivity.class));
    }

    //MediaExtractor Demo
    public void MediaExtractorClick(View view){
        startActivity(new Intent(this, MediaExtractorActivity.class));
    }

    //音视频混合
    public void mediaMuxerClick(View view){
        startActivity(new Intent(this, MediaMuxerActivity.class));
    }
}