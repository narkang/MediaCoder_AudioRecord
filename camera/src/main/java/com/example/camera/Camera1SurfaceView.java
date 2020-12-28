package com.example.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Camera1SurfaceView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

    public Camera1SurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        startPreview();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    private volatile boolean isCapture;

    public void startCapture() {
        isCapture = true;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        //bytes为NV21格式
        if (isCapture) {
            portraitData2Raw(bytes);
            isCapture = false;
            captrue(buffer);
        }
        mCamera.addCallbackBuffer(bytes);
    }

    private Camera.Size size;
    private Camera mCamera;
    //注意这个缓冲数据建议放到全局变量，不然定义局部变量，容易报栈内存溢出问题
    byte[] buffer;

    private void startPreview() {
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        Camera.Parameters parameters = mCamera.getParameters();
        size = parameters.getPreviewSize();
        try {
            //相机预览和SurfaceView关联
            mCamera.setPreviewDisplay(getHolder());
//这里关于相机的预览和拍照方向处理，可以看后面的方向处理
            mCamera.setDisplayOrientation(90);
            buffer = new byte[size.width * size.height * 3 / 2];
            //后续onPreviewFrame回调中数据也会渲染到buffer中
            mCamera.addCallbackBuffer(buffer);
            mCamera.setPreviewCallbackWithBuffer(this);
            //开启预览
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //顺时针将YUV数据旋转90度
    private void portraitData2Raw(byte[] data) {
        buffer = YUVUtil.rotateYUV420Degree90(data, size.width, size.height);
    }

    int index = 0;

    public void captrue(byte[] temp) {

        //保存一张照片
        String fileName = "IMG_" + String.valueOf(index++) + ".jpg";  //jpeg文件名定义
        File sdRoot = Environment.getExternalStorageDirectory();    //系统路径

        File pictureFile = new File(sdRoot, fileName);
        if (!pictureFile.exists()) {
            try {
                pictureFile.createNewFile();

                FileOutputStream filecon = new FileOutputStream(pictureFile);
                YuvImage image = new YuvImage(temp, ImageFormat.NV21, size.height, size.width, null);
                //图像压缩
                image.compressToJpeg(
                        new Rect(0, 0, image.getWidth(), image.getHeight()),
                        100, filecon);   // 将NV21格式图片，以质量100压缩成Jpeg，并得到JPEG数据流

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
