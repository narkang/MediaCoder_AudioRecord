package com.example.audiohandle.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class MusicProcess {

    private static final String TAG = "ruby";
    private static  int TIMEOUT = 1000;

    private float normalizeVolume(int volume) {
        return volume / 100f * 1;
    }

    //     vol1  vol2  0-100  0静音  120
    public void mixPcm(String pcm1Path, String pcm2Path, String toPath
            , int volume1, int volume2) throws IOException {
        float vol1 = normalizeVolume(volume1);
        float vol2 = normalizeVolume(volume2);
        //一次读取  2k
        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
        // 待输出数据
        byte[] buffer3 = new byte[2048];

        FileInputStream is1 = new FileInputStream(pcm1Path);
        FileInputStream is2 = new FileInputStream(pcm2Path);

        //输出PCM 的
        FileOutputStream fileOutputStream = new FileOutputStream(toPath);
        short temp2, temp1;//   两个short变量相加 会大于short   声音
        int temp;
        boolean end1 = false, end2 = false;
        while (!end1 || !end2) {

            if (!end1) {
//
                end1 = (is1.read(buffer1) == -1);
//            音乐的pcm数据  写入到 buffer3
                System.arraycopy(buffer1, 0, buffer3, 0, buffer1.length);

            }

            if (!end2) {
                end2 = (is2.read(buffer2) == -1);
                int voice = 0;//声音的值  跳过下一个声音的值    一个声音 2 个字节
                for (int i = 0; i < buffer2.length; i += 2) {
//                    或运算
                    temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                    temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);
                    temp = (int) (temp1 * vol1 + temp2 * vol2);//音乐和 视频声音 各占一半
                    if (temp > 32767) {
                        temp = 32767;
                    } else if (temp < -32768) {
                        temp = -32768;
                    }
                    buffer3[i] = (byte) (temp & 0xFF);
                    buffer3[i + 1] = (byte) ((temp >>> 8) & 0xFF);
                }
                fileOutputStream.write(buffer3);
            }
        }
        is1.close();
        is2.close();
        fileOutputStream.close();
    }

    public void mixVideoAndAudioTrack(Context context,
                                      final String videoInput,
                                      final String audioInput,
                                      final String output,
                                      final Integer startTimeUs, final Integer endTimeUs,
                                      int videoVolume,//视频声音大小
                                      int aacVolume//音频声音大小
    ) throws Exception {
        File cacheDir = Environment.getExternalStorageDirectory();
//        下载下来的音乐转换城pcm
        File aacPcmFile = new File(cacheDir, "audio" + ".pcm");
//        视频自带的音乐转换城pcm
        final File videoPcmFile = new File(cacheDir, "video" + ".pcm");

//        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
//        mediaMetadataRetriever.setDataSource(audioInput);
//        读取音乐时间
//        final int aacDurationMs = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
//        mediaMetadataRetriever.release();

        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(audioInput);

        decodeToPCM(videoInput, videoPcmFile.getAbsolutePath(),
                startTimeUs, endTimeUs);

//        final int videoDurationMs = (endTimeUs - startTimeUs) / 1000;

        decodeToPCM(audioInput, aacPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);

        File adjustedPcm = new File(cacheDir, "混合后的" + ".pcm");
        mixPcm(videoPcmFile.getAbsolutePath(), aacPcmFile.getAbsolutePath(),
                adjustedPcm.getAbsolutePath()
                , videoVolume, aacVolume);
        File wavFile = new File(cacheDir, adjustedPcm.getName() + ".wav");
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(adjustedPcm.getAbsolutePath()
                , wavFile.getAbsolutePath());

        //混音的wav文件   + 视频文件   ---》  生成
        mixVideoAndAudio(videoInput, output, startTimeUs, endTimeUs, wavFile);
    }

    private void mixVideoAndAudio(String videoInput, String output, Integer startTimeUs, Integer endTimeUs, File wavFile) throws IOException {

        //        初始化一个视频封装容器
        MediaMuxer mediaMuxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

//            一个轨道    既可以装音频 又视频   是 1 不是2
//            取音频轨道  wav文件取配置信息
//            先取视频
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(videoInput);
//            拿到视频轨道的索引
        int videoIndex = selectTrack(mediaExtractor, false);

        int audioIndex = selectTrack(mediaExtractor, true);

//            视频配置 文件
        MediaFormat videoFormat = mediaExtractor.getTrackFormat(videoIndex);
        //开辟了一个 轨道空的轨道   写数据     真实
        mediaMuxer.addTrack(videoFormat);

//        ------------音频的数据已准备好----------------------------
        //视频中音频轨道   应该取自于原视频的音频参数
        MediaFormat audioFormat = mediaExtractor.getTrackFormat(audioIndex);
        int audioBitrate = audioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        audioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        //添加一个空的轨道  轨道格式取自 视频文件，跟视频所有信息一样
        int muxerAudioIndex = mediaMuxer.addTrack(audioFormat);

        //音频轨道开辟好了  输出开始工作
        mediaMuxer.start();

        //音频的wav
        MediaExtractor pcmExtrator = new MediaExtractor();
        pcmExtrator.setDataSource(wavFile.getAbsolutePath());
        int audioTrack = selectTrack(pcmExtrator, true);
        pcmExtrator.selectTrack(audioTrack);
        MediaFormat pcmTrackFormat = pcmExtrator.getTrackFormat(audioTrack);

        //最大一帧的 大小
        int maxBufferSize = 0;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = pcmTrackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }

        MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                44100, 2);//参数对应-> mime type、采样率、声道数
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);//比特率
//            音质等级
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
//            解码  那段
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);
//             解码 那
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
//            配置AAC 参数  编码 pcm   重新编码     视频文件变得更小
        encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        encoder.start();
//            容器
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean encodeDone = false;
        while (!encodeDone) {
            int inputBufferIndex = encoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                long sampleTime = pcmExtrator.getSampleTime();

                if (sampleTime < 0) {
//                        pts小于0  来到了文件末尾 通知编码器  不用编码了
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int flags = pcmExtrator.getSampleFlags();
//
                    int size = pcmExtrator.readSampleData(buffer, 0);
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    inputBuffer.put(buffer);
                    inputBuffer.position(0);

                    encoder.queueInputBuffer(inputBufferIndex, 0, size, sampleTime, flags);
                    pcmExtrator.advance();
                }
            }
//                获取编码完的数据
            int outputBufferIndex = encoder.dequeueOutputBuffer(info, TIMEOUT);
            while (outputBufferIndex >= 0) {
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    encodeDone = true;
                    break;
                }
                ByteBuffer encodeOutputBuffer = encoder.getOutputBuffer(outputBufferIndex);
//                    将编码好的数据  压缩 1     aac
                mediaMuxer.writeSampleData(muxerAudioIndex, encodeOutputBuffer, info);
                encodeOutputBuffer.clear();
                encoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = encoder.dequeueOutputBuffer(info, TIMEOUT);
            }
        }
        //把音频添加好了
        if (audioTrack >= 0) {
            mediaExtractor.unselectTrack(audioTrack);
        }

        //视频
        mediaExtractor.selectTrack(videoIndex);

        mediaExtractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        buffer = ByteBuffer.allocateDirect(maxBufferSize);
        //封装容器添加视频轨道信息
        while (true) {
            long sampleTimeUs = mediaExtractor.getSampleTime();
            if (sampleTimeUs == -1) {
                break;
            }
            if (sampleTimeUs < startTimeUs) {
                mediaExtractor.advance();
                continue;
            }
            if (endTimeUs != null && sampleTimeUs > endTimeUs) {
                break;
            }
            info.presentationTimeUs = sampleTimeUs - startTimeUs + 600;
            info.flags = mediaExtractor.getSampleFlags();
            info.size = mediaExtractor.readSampleData(buffer, 0);
            if (info.size < 0) {
                break;
            }
            mediaMuxer.writeSampleData(videoIndex, buffer, info);
            mediaExtractor.advance();
        }

        try {
            pcmExtrator.release();
            mediaExtractor.release();
            encoder.stop();
            encoder.release();
            mediaMuxer.release();
        } catch (Exception e) {
        }

    }

    public void mixAudioTrack(Context context,
                              final String videoInput,
                              final String audioInput,
                              final String output,
                              final Integer startTimeUs, final Integer endTimeUs,
                              int videoVolume,//视频声音大小
                              int aacVolume//音频声音大小
    ) throws Exception {

        final File videoPcmFile = new File(Environment.getExternalStorageDirectory(), "video.pcm");
        final File musicPcmFile = new File(Environment.getExternalStorageDirectory(), "music.pcm");
        decodeToPCM(videoInput, videoPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);

        decodeToPCM(audioInput, musicPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);
        final File mixPcmFile = new File(Environment.getExternalStorageDirectory(), "mix.pcm");
        mixPcm(videoPcmFile.getAbsolutePath(), musicPcmFile.getAbsolutePath(), mixPcmFile.getAbsolutePath(), videoVolume, aacVolume);
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(mixPcmFile.getAbsolutePath()
                , output);
        Log.e(TAG, "转换完成");
    }

    //    MP3 截取并且输出  pcm
    @SuppressLint("WrongConstant")
    public void decodeToPCM(String musicPath, String outPath, int startTime, int endTime) throws Exception {
        if (endTime < startTime) {
            return;
        }
        MediaExtractor mediaExtractor = new MediaExtractor();

        mediaExtractor.setDataSource(musicPath);
        int audioTrack = selectTrack(mediaExtractor, true);

        mediaExtractor.selectTrack(audioTrack);
        mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        MediaFormat oriAudioFormat = mediaExtractor.getTrackFormat(audioTrack);
        int maxBufferSize = 100 * 1000;
        //音频输入最大buffer
        if (oriAudioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = oriAudioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec mediaCodec = MediaCodec.createDecoderByType(oriAudioFormat.getString((MediaFormat.KEY_MIME)));
        mediaCodec.configure(oriAudioFormat, null, null, 0);
        File pcmFile = new File(outPath);
        FileChannel writeChannel = new FileOutputStream(pcmFile).getChannel();
        mediaCodec.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputBufferIndex = -1;
        while (true) {
            int decodeInputIndex = mediaCodec.dequeueInputBuffer(100000);
            if (decodeInputIndex >= 0) {
                long sampleTimeUs = mediaExtractor.getSampleTime();

                if (sampleTimeUs == -1) {
                    break;
                } else if (sampleTimeUs < startTime) {
                    mediaExtractor.advance();
                    continue;
                } else if (sampleTimeUs > endTime) {
                    break;
                }
                info.size = mediaExtractor.readSampleData(buffer, 0);
                info.presentationTimeUs = sampleTimeUs;
                info.flags = mediaExtractor.getSampleFlags();

                byte[] content = new byte[buffer.remaining()];
                buffer.get(content);
//                输出文件  方便查看
//                FileUtils.writeContent(content);
//                解码
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(decodeInputIndex);
                inputBuffer.put(content);
                mediaCodec.queueInputBuffer(decodeInputIndex, 0, info.size, info.presentationTimeUs, info.flags);
//                释放上一帧的压缩数据
                mediaExtractor.advance();
            }

            outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100_000);
            while (outputBufferIndex >= 0) {
                ByteBuffer decodeOutputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                writeChannel.write(decodeOutputBuffer);//MP3  1   pcm2
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100_000);
            }
        }
        writeChannel.close();
        mediaExtractor.release();
        mediaCodec.stop();
        mediaCodec.release();

    }

    // pcm数据转换成WAV
    public void convertPcmToWav(String inputPath, String outputPath) {
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(inputPath, outputPath);
        Log.i(TAG, "mixAudioTrack: 转换完毕");
    }

    public static int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }
}
