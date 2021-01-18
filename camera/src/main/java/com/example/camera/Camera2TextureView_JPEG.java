package com.example.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2TextureView_JPEG extends TextureView implements TextureView.SurfaceTextureListener, ImageReader.OnImageAvailableListener {

    private static final String TAG = Camera2TextureView_JPEG.class.getSimpleName();

    private Context mContext;

    private String mCameraId;
    private CameraCharacteristics mBackCameraCharacteristics;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CaptureRequest mPreviewRequest;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;

    private Surface mWorkingSurface;
    private ImageReader mImageReader;
    private SurfaceTexture mSurfaceTexture;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private Size mPreviewSize;

    public Camera2TextureView_JPEG(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.mContext = context;
        initializeCameraManager();
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Camera2TextureView_JPEG.this.mCameraDevice = cameraDevice;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
        }
    };

    protected void prepareCameraOutputs() {
        //获取相机支持的流的参数的集合
        StreamConfigurationMap map = mBackCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //获取输出格式为ImageFormat.NV21支持的所有尺寸
        Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);

        mPreviewSize = outputSizes[0];

        mImageReader = ImageReader.newInstance(outputSizes[0].getWidth(), outputSizes[0].getHeight(),
                ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(this, mBackgroundHandler);
    }

    public void openCamera() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            prepareCameraOutputs();
            if (isAvailable()) {
                mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } else {
                setSurfaceTextureListener(this);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        closePreviewSession();
        closeCameraDevice();
        closeImageReader();
    }

    public void onDestroy() {
        stopBackgroundThread();
    }

    private void createCaptureSession() {
        try {

            mSurfaceTexture = getSurfaceTexture();

            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            mWorkingSurface = new Surface(mSurfaceTexture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mWorkingSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(mWorkingSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            updatePreview(cameraCaptureSession);
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Fail while starting preview: ");
                        }
                    }, null);

        } catch (Exception e) {
            Log.e(TAG, "Error while preparing surface for preview: ", e);
        }
    }

    private void updatePreview(CameraCaptureSession cameraCaptureSession) {
        if (null == mCameraDevice) {
            return;
        }
        mCaptureSession = cameraCaptureSession;
        //设置自动对焦模式为连续自动对焦
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //创建CaptureRequest
        mPreviewRequest = mPreviewRequestBuilder.build();
        try {
            //开始预览，由于我们现在并不需要对预览的图像数据做处理，所以这里的第二个参数就传null
            mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (Build.VERSION.SDK_INT > 17) {
            mBackgroundThread.quitSafely();
        } else mBackgroundThread.quit();

        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread: ", e);
        } finally {
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    private void initializeCameraManager() {

        startBackgroundThread();

        mCameraManager = (CameraManager) this.mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            //获取可用的相机列表
            String[] cameraIdList = mCameraManager.getCameraIdList();
            for (String cameraId : cameraIdList) {
                //获取该相机的CameraCharacteristics，它保存的相机相关的属性
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                //获取相机的方向
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                //如果是前置摄像头就continue，我们这里只用后置摄像头
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                //保存cameraId
                this.mCameraId = cameraId;
                this.mBackCameraCharacteristics = cameraCharacteristics;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            try {
                mCaptureSession.abortCaptures();
            } catch (Exception ignore) {
            } finally {
                mCaptureSession = null;
            }
        }
    }

    private void releaseTexture() {
        if (null != mSurfaceTexture) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }

    private void closeImageReader() {
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void closeCameraDevice() {
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public void startCapture() {
        CaptureRequest.Builder captureRequestBuilder = null;
        try {
            //用CameraDevice创建一个CaptureRequest.Builder,类型为CameraDevice.TEMPLATE_STILL_CAPTURE，也就是说我们需要请求以个静态的图像。
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            //将imageReader的Surface设置为请求的目标Surface
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            mPreviewRequest = captureRequestBuilder.build();
            //停止预览
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            //开始请求拍照
            mCaptureSession.capture(mPreviewRequest, new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //一个保存图片的Runnable
    int index = 0;
    class SaveImageRunnable implements Runnable {
        private Image image;
        private File imgFile;

        public SaveImageRunnable(Image image) {
            //保存一张照片
            String fileName = "IMG_" + String.valueOf(index++) + ".jpg";  //jpeg文件名定义
            File imgFile = new File(Environment.getExternalStorageDirectory(), fileName);    //系统路径

            this.image = image;
            this.imgFile = imgFile;
        }

        @Override
        public void run() {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] byteBuffer = new byte[buffer.remaining()];
            buffer.get(byteBuffer);
            FileOutputStream fos = null;
            try {

                if(!imgFile.exists()) imgFile.createNewFile();

                fos = new FileOutputStream(imgFile);
                fos.write(byteBuffer);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    assert fos != null;
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if(takePhotoListener != null){
                            takePhotoListener.takePhotoSuccess(imgFile.toString());
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        //发送拍照的请求后，相机将图像数据填充到imageReader的Surface中的时候会回调这里
        //我们想后台线程post一个runnable
        mBackgroundHandler.post(new SaveImageRunnable(reader.acquireNextImage()));
    }

    private TakePhotoListener takePhotoListener;

    public interface TakePhotoListener{

        void takePhotoSuccess(String filePath);

    }

    public void setTakePhotoListener(TakePhotoListener takePhotoListener) {
        this.takePhotoListener = takePhotoListener;
    }
}
