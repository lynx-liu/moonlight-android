package com.limelight.nvstream.av.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.util.Log;

import com.limelight.nvstream.AudioTcpClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class AudioRecordHelper {
    private static final String TAG = "AudioRecordHelper";

    private ExecutorService mExecutorService;
    private AudioRecord mAudioRecorder;
    private AcousticEchoCanceler mAEC;
    private int mAudioSessionId;
    private MediaCodec mAudioEncoder;//音频编码器
    private ArrayBlockingQueue<byte[]> queue;
    private MediaCodec.BufferInfo mAudioEncodeBufferInfo;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;
    private volatile boolean mIsRecording;
    private volatile boolean mIsPaused = false;
    private int mSampleRate = 48000;
    private String mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS;
    private int mAdtsSize = 0;
    private static final int BUFF_SIZE = 2048;

    private AudioTcpClient audioTcpClient;
    private String host;
    private int audioUpPortTcp = 48031;

    public AudioRecordHelper(String host) {
        this.host = host;
    }

    public boolean isRecording(){
        return mIsRecording;
    }

    public void start() {
        Log.d(TAG, "start recorder");
        if (mIsRecording) {
            Log.d(TAG, "current is still recording");
            return;
        }
        mIsRecording = true;

        audioTcpClient = new AudioTcpClient(host,audioUpPortTcp);
        audioTcpClient.start();

        queue = new ArrayBlockingQueue<byte[]>(10);
        try{
            initAudioEncoder();
            initAudioRecord();
        }catch (Exception e){
           Log.e(TAG,e.toString());
           return;
        }

        mExecutorService = Executors.newFixedThreadPool(2);
        // 开启录音线程
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                startRecorder();
            }
        });
        // 开启编码线程
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                encodePCM();
            }
        });
    }

    private void startRecorder() {
        try {
            Log.d(TAG, "startRecord");
            mAudioRecorder.startRecording();
            byte[] mBuffer = new byte[BUFF_SIZE];

            while (mIsRecording) {
                int read = mAudioRecorder.read(mBuffer, 0, mBuffer.length);
                if (read > 0) {
                    byte[] audio = new byte[read];
                    System.arraycopy(mBuffer, 0, audio, 0, read);
                    putPCMData(audio); // PCM数据放入队列，等待编码
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.toString());
        } finally {
            if (mAudioRecorder != null) {
                mAudioRecorder.release();
                mAudioRecorder = null;
            }
        }
    }

    private void encodePCM() {
        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;
        byte[] chunkAudio;
        int outBitSize;
        byte[] chunkPCM;

        try{
            while (mIsRecording || !queue.isEmpty()) {
                chunkPCM = getPCMData();//获取解码器所在线程输出的数据
                if (chunkPCM == null) {
                    continue;
                }
                inputIndex = mAudioEncoder.dequeueInputBuffer(-1);//
                if (inputIndex >= 0) {
                    inputBuffer = encodeInputBuffers[inputIndex];
                    inputBuffer.clear();
                    inputBuffer.limit(chunkPCM.length);
                    inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
                    mAudioEncoder.queueInputBuffer(inputIndex, 0, chunkPCM.length, 0, 0);//通知编码器 编码
                }

                outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
                while (outputIndex >= 0) {
                    outBitSize = mAudioEncodeBufferInfo.size;
                    outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
                    outputBuffer.position(mAudioEncodeBufferInfo.offset);
                    outputBuffer.limit(mAudioEncodeBufferInfo.offset + outBitSize);

                    int outPacketSize = outBitSize + mAdtsSize;
                    chunkAudio = new byte[outPacketSize];
                    if(mAdtsSize>0) addADTStoPacket(chunkAudio, outPacketSize);//添加ADTS
                    outputBuffer.get(chunkAudio, mAdtsSize, outBitSize);//将编码数据 取出到byte[]中

                    outputBuffer.position(mAudioEncodeBufferInfo.offset);

                    if(audioTcpClient!=null) audioTcpClient.sendAudioData(chunkAudio,System.currentTimeMillis(), (byte) 0x10, (byte) 0x04, (byte) 0x01, (byte) 0x00);

                    mAudioEncoder.releaseOutputBuffer(outputIndex, false);
                    outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
                }
            }
        }catch (Exception e){
            Log.e(TAG,e.toString());
        }
    }

    /**
     * 在Container中队列取出PCM数据
     *
     * @return PCM数据块
     */
    private byte[] getPCMData() {
        try {
            if (queue.isEmpty()) {
                return null;
            }
            return queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void putPCMData(byte[] pcmChunk) {
        try {
            queue.put(pcmChunk);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 添加adts信息
     *
     * @param packet
     * @param packetLen
     */
    public void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int chanCfg = 1; // CPE
//        int freqIdx = 3; // 48000Hz
//        int freqIdx = 4; // 44100Hz
        int freqIdx = (mSampleRate == 48000) ? 3 : 4;


        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    private void initAudioEncoder() {
        mAdtsSize = mimeType.endsWith(MediaFormat.MIMETYPE_AUDIO_OPUS)?0:7;//7为ADTS头部的大小

        try {
            mAudioEncoder = MediaCodec.createEncoderByType(mimeType);
            MediaFormat format = MediaFormat.createAudioFormat(mimeType, mSampleRate, 2);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
            mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAudioEncoder == null) {
            Log.e(TAG, "initAudioEncoder failed");
            return;
        }

        mAudioEncoder.start();
        encodeInputBuffers = mAudioEncoder.getInputBuffers();
        encodeOutputBuffers = mAudioEncoder.getOutputBuffers();
        mAudioEncodeBufferInfo = new MediaCodec.BufferInfo();
    }

    private void initAudioRecord() {
        int audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, channelConfig, audioFormat);
        mAudioRecorder = new AudioRecord(audioSource, mSampleRate, channelConfig, audioFormat, Math.max(minBufferSize, BUFF_SIZE));
        mAudioSessionId = mAudioRecorder.getAudioSessionId();
        if(AcousticEchoCanceler.isAvailable()){
            Log.d(TAG, "support aec");
            initAEC();
        }else {
            Log.d(TAG, "unSupport aec");
        }
    }

    public void initAEC(){
        mAEC = AcousticEchoCanceler.create(mAudioSessionId);
        mAEC.setEnabled(true);
    }

    public void resume(){
        Log.d(TAG, "resume");
        if(mIsPaused){
           start();
        }
        mIsPaused = false;
    }
    public void pause(){
        Log.d(TAG, "pause");
        if(isRecording()){
            stop();
            mIsPaused = true;
        }
    }

    public boolean stop() {
        mIsPaused = false;
        Log.d(TAG, "stopRecord");
        mIsRecording = false;
        if (mAudioRecorder != null) {
            Log.d(TAG, "stop recorder");
            try {
                mAudioRecorder.stop();
                mAudioRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            } finally {
                mAudioRecorder = null;
            }

        }

        if (mAudioEncoder != null) {
            try {
                mAudioEncoder.stop();
                mAudioEncoder.release();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            } finally {
                mAudioEncoder = null;
            }

        }

        if(audioTcpClient!=null) {
            audioTcpClient.interrupt();
            try {
                audioTcpClient.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioTcpClient = null;
        }
        return true;
    }
}
