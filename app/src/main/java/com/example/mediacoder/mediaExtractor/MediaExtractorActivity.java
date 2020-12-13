package com.example.mediacoder.mediaExtractor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.example.mediacoder.R;
import com.example.mediacoder.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

//https://www.cnblogs.com/renhui/p/7474096.html
//https://blog.csdn.net/u010126792/article/details/86510903
public class MediaExtractorActivity extends AppCompatActivity {

    private static final String TAG = MediaExtractorActivity.class.getSimpleName();

    private MediaExtractor mMediaExtractor;
    private MediaMuxer mMediaMuxer;

    private MediaExtractor mAudioMediaExtractor;
    private MediaMuxer mAudioMediaMuxer;

    private MediaExtractor mMergeAudioMediaExtractor;
    private MediaExtractor mMergeVideoMediaExtractor;
    private MediaMuxer mMergeMediaMuxer;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_extractor);

        checkPermissions();

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            processVideo();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });

        findViewById(R.id.btn2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            processAudio();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });

        findViewById(R.id.btn3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mergeVideoAndAudio();
                    }
                }).start();
            }
        });
    }

    private void mergeVideoAndAudio() {
        String desPath = FileUtil.getExternalStorageDirectory() + "/mergeOutputVideo.mp4";

        String fPath1 = FileUtil.getExternalStorageDirectory() + "/videoOutput.mp4";
        String fPath2 = FileUtil.getExternalStorageDirectory() + "/audioOutput.mp4";

        File filedes = new File(desPath);

        try {
            if (filedes.exists()) {
                filedes.delete();
            }
            filedes.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            mMergeMediaMuxer = new MediaMuxer(desPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int mVideoTrackIndex = 0;
        int mAudioTrackIndex = 0;
        long frameRate1 = 0;
        long frameRate2 = 0;

        MediaFormat format1;
        MediaFormat format2;
        try {
            mMergeVideoMediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mMergeVideoMediaExtractor.setDataSource(fPath1);//媒体文件的位置
            for (int i = 0; i < mMergeVideoMediaExtractor.getTrackCount(); i++) {
                format1 = mMergeVideoMediaExtractor.getTrackFormat(i);
                String mime = format1.getString(MediaFormat.KEY_MIME);

                if (mime.startsWith("video")) {
                    mMergeVideoMediaExtractor.selectTrack(i);//选择此视频轨道
                    frameRate1 = format1.getInteger(MediaFormat.KEY_FRAME_RATE);
                    mVideoTrackIndex = mMergeMediaMuxer.addTrack(format1);

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            mMergeAudioMediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mMergeAudioMediaExtractor.setDataSource(fPath2);//媒体文件的位置
            for (int i = 0; i < mMergeAudioMediaExtractor.getTrackCount(); i++) {
                format2 = mMergeAudioMediaExtractor.getTrackFormat(i);
                String mime = format2.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio")) {//获取音频轨道
                    ByteBuffer buffer = ByteBuffer.allocate(100 * 1024);
                    {
                        mMergeAudioMediaExtractor.selectTrack(i);//选择此音频轨道
                        mMergeAudioMediaExtractor.readSampleData(buffer, 0);
                        long first_sampletime = mMergeAudioMediaExtractor.getSampleTime();
                        mMergeAudioMediaExtractor.advance();
                        long second_sampletime = mMergeAudioMediaExtractor.getSampleTime();
                        frameRate2 = Math.abs(second_sampletime - first_sampletime);//时间戳
                        mMergeAudioMediaExtractor.unselectTrack(i);
                    }
                    mMergeAudioMediaExtractor.selectTrack(i);
                    mAudioTrackIndex = mMergeMediaMuxer.addTrack(format2);

                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        mMergeMediaMuxer.start();
        MediaCodec.BufferInfo info1 = new MediaCodec.BufferInfo();
        info1.presentationTimeUs = 0;
        ByteBuffer buffer = ByteBuffer.allocate(100 * 1024);
        int sampleSize1 = 0;
        while ((sampleSize1 = mMergeVideoMediaExtractor.readSampleData(buffer, 0)) > 0) {
            info1.offset = 0;
            info1.size = sampleSize1;
            info1.flags = mMergeVideoMediaExtractor.getSampleFlags();
            info1.presentationTimeUs += 1000 * 1000 / frameRate1;
            mMergeMediaMuxer.writeSampleData(mVideoTrackIndex, buffer, info1);
            mMergeVideoMediaExtractor.advance();
        }


        MediaCodec.BufferInfo info2 = new MediaCodec.BufferInfo();
        info2.presentationTimeUs = 0;

        int sampleSize2 = 0;
        while ((sampleSize2 = mMergeAudioMediaExtractor.readSampleData(buffer, 0)) > 0) {
            info2.offset = 0;
            info2.size = sampleSize2;
            info2.flags = mMergeAudioMediaExtractor.getSampleFlags();
            info2.presentationTimeUs += frameRate2;
            mMergeMediaMuxer.writeSampleData(mAudioTrackIndex, buffer, info2);
            mMergeAudioMediaExtractor.advance();
        }

        try {
            mMergeVideoMediaExtractor.release();
            mMergeVideoMediaExtractor = null;
            mMergeAudioMediaExtractor.release();
            mMergeAudioMediaExtractor = null;
            mMergeMediaMuxer.stop();
            mMergeMediaMuxer.release();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean processAudio() throws IOException {
        mAudioMediaExtractor = new MediaExtractor();
        mAudioMediaExtractor.setDataSource(FileUtil.getExternalStorageDirectory() + "/demo.mp4");

        int mAudioTrackIndex = -1;
        long frameRate = 0;

        ByteBuffer buffer = ByteBuffer.allocate(500 * 1024);

        for (int i = 0; i < mAudioMediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mAudioMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("audio/")) {
                continue;
            }

            {
                mAudioMediaExtractor.selectTrack(i);//选择此音频轨道
                mAudioMediaExtractor.readSampleData(buffer, 0);
                long first_sampletime = mAudioMediaExtractor.getSampleTime();
                //移动到下一帧
                mAudioMediaExtractor.advance();
                long second_sampletime = mAudioMediaExtractor.getSampleTime();
                frameRate = Math.abs(second_sampletime - first_sampletime);//时间戳
                Log.e(TAG, "fps audio:" + frameRate);
                mAudioMediaExtractor.unselectTrack(i);
            }

            mAudioMediaExtractor.selectTrack(i);
            File videoFile = new File(FileUtil.getExternalStorageDirectory() + "/audioOutput.mp4");
            if(videoFile.exists()){
                videoFile.delete();
            }
            videoFile.createNewFile();
            mAudioMediaMuxer = new MediaMuxer(FileUtil.getExternalStorageDirectory() + "/audioOutput.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mAudioTrackIndex = mAudioMediaMuxer.addTrack(format);
            mAudioMediaMuxer.start();
        }

        if (mAudioMediaMuxer == null) {
            return false;
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.presentationTimeUs = 0;
        int sampleSize = 0;
        while ((sampleSize = mAudioMediaExtractor.readSampleData(buffer, 0)) > 0) {

            info.offset = 0;
            info.size = sampleSize;
            info.flags = mAudioMediaExtractor.getSampleFlags();
            info.presentationTimeUs += frameRate;
            mAudioMediaMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
            mAudioMediaExtractor.advance();
        }

        mAudioMediaExtractor.release();

        mAudioMediaMuxer.stop();
        mAudioMediaMuxer.release();

        return true;
    }

    private boolean processVideo() throws IOException {
        mMediaExtractor = new MediaExtractor();
        mMediaExtractor.setDataSource(FileUtil.getExternalStorageDirectory() + "/demo.mp4");

        int mVideoTrackIndex = -1;
        int framerate = 0;
        for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("video/")) {
                continue;
            }
            framerate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
            Log.e(TAG, "fps video:" + (1000 * 1000 / framerate));

            mMediaExtractor.selectTrack(i);

            File videoFile = new File(FileUtil.getExternalStorageDirectory() + "/videoOutput.mp4");
            if(videoFile.exists()){
                videoFile.delete();
            }
            videoFile.createNewFile();

            mMediaMuxer = new MediaMuxer(FileUtil.getExternalStorageDirectory() + "/videoOutput.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mVideoTrackIndex = mMediaMuxer.addTrack(format);
            mMediaMuxer.start();
        }

        if (mMediaMuxer == null) {
            return false;
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.presentationTimeUs = 0;
        ByteBuffer buffer = ByteBuffer.allocate(500 * 1024);
        int sampleSize = 0;
        while ((sampleSize = mMediaExtractor.readSampleData(buffer, 0)) > 0) {

            info.offset = 0;
            info.size = sampleSize;
            info.flags = mMediaExtractor.getSampleFlags();
            info.presentationTimeUs += 1000 * 1000 / framerate;
            mMediaMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
            mMediaExtractor.advance();
        }

        mMediaExtractor.release();

        mMediaMuxer.stop();
        mMediaMuxer.release();

        return true;
    }

    private static final int MY_PERMISSIONS_REQUEST = 1001;

    /**
     * 被用户拒绝的权限列表
     */
    private List<String> mPermissionList = new ArrayList<>();

    /**
     * 需要申请的运行时权限
     */
    private String[] permissions = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private void checkPermissions() {
        // Marshmallow开始才用申请运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < permissions.length; i++) {
                if (ContextCompat.checkSelfPermission(this, permissions[i]) !=
                        PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(permissions[i]);
                }
            }
            if (!mPermissionList.isEmpty()) {
                String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
                ActivityCompat.requestPermissions(this, permissions, MY_PERMISSIONS_REQUEST);
            }
        }
    }
}