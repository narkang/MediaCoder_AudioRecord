package com.example.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;

public class MainActivity extends Activity {

    Camera2TextureView_YUV_420 surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface);

        //这个是为了测试ExifInterface，别的TextureView不适用
//        surfaceView.setTakePhotoListener(new Camera2TextureView_JPEG.TakePhotoListener() {
//            @Override
//            public void takePhotoSuccess(String filePath) {
//                Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
//                intent.putExtra("filePath", filePath);
//                startActivity(intent);
//            }
//        });

        checkPermission();
    }

    public void capture(View view) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            new MediaActionSound().play(MediaActionSound.SHUTTER_CLICK);
        }
        surfaceView.startCapture();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(checkPermission()){
            surfaceView.openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        surfaceView.closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        surfaceView.onDestroy();
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1){
            surfaceView.openCamera();
        }
    }
}