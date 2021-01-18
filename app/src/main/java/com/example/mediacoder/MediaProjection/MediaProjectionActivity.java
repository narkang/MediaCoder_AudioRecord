package com.example.mediacoder.MediaProjection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediacoder.R;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaProjectionActivity extends AppCompatActivity {

    private static final String TAG = "MediaProjectionActivity";

    private MediaProjectionManager mediaProjectionManager;

    private MediaProjection mediaProjection;

    private MediaCodec mediaCodec;

    private boolean isStart = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_projection);

        checkPermission();

        //开启录频
        this.mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    //开始录频
    public void startClick(View view){
        isStart = true;
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 100);
        Log.e(TAG, "开始录频");
    }

    //结束录频
    public void endClick(View view){
        isStart = false;
        Log.e(TAG, "结束录频");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {

            mediaProjection = mediaProjectionManager.getMediaProjection
                    (resultCode, data);

            //同意了回调这里
            initMediaCodec();

        }
    }

    private void initMediaCodec() {
        try {
            //此处是编码 h264
//            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            //type -> h265
            mediaCodec = MediaCodec.createEncoderByType("video/hevc");
            //h264编码器
//            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
//                    540, 960);
            //h265编码器
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC,
                    540, 960);

            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            //帧率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            //码率
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1200_000);
            //2秒一个I帧
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);//2s一个I帧
            //不需要传surface
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //这是MediaCodec 提供的surface
            final Surface surface = mediaCodec.createInputSurface();
            new Thread() {
                @Override
                public void run() {
                    //开始编码
                    mediaCodec.start();
                    //提供的surface  与MediaProjection关联
                    mediaProjection.createVirtualDisplay("screen-codec",
                            540, 960, 1,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            surface, null, null);
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    while (isStart) {
//                      录频数据直接会传给dsp编码，源源不断的插叙编码好的数据
                        int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 100000);

                        Log.e(TAG, "run: " + index);
                        if (index >= 0) {
//                            dsp芯片提供的ByteBuffer，不能够直接使用操作
                            ByteBuffer buffer = mediaCodec.getOutputBuffer(index);

                            byte[] outData = new byte[bufferInfo.size];
                            buffer.get(outData);

                            //以字符串的方式写入
                            writeContent(outData);
                            //写成 文件  我们就能够播放起来
                            writeBytes(outData);
                            mediaCodec.releaseOutputBuffer(index, false);
                        }

                    }

                    mediaCodec.stop();
                    mediaCodec.release();

                    mediaProjection.stop();

                }
            }.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void writeBytes(byte[] array) {
        FileOutputStream writer = null;
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
//            writer = new FileOutputStream(Environment.getExternalStorageDirectory() + "/codec.h264", true);
            writer = new FileOutputStream(Environment.getExternalStorageDirectory() + "/codec_265.h265", true);
            writer.write(array);
            writer.write('\n');


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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


    public String writeContent(byte[] array) {
        char[] HEX_CHAR_TABLE = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        //转成16进程的字符串存储
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(HEX_CHAR_TABLE[(b & 0xf0) >> 4]);
            sb.append(HEX_CHAR_TABLE[b & 0x0f]);
        }
        Log.e(TAG, "writeContent: " + sb.toString());
        FileWriter writer = null;
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
//            writer = new FileWriter(Environment.getExternalStorageDirectory() + "/codec.txt", true);
            writer = new FileWriter(Environment.getExternalStorageDirectory() + "/codec_H265.txt", true);
            writer.write(sb.toString());
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}