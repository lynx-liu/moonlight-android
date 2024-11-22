package com.limelight.nvstream;

import android.util.Log;

import com.limelight.utils.TcpClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class AudioTcpClient extends TcpClient {
    private static final String TAG = AudioTcpClient.class.getSimpleName();
    private static final int InputData = 0x0206;
    private static final int InputAudioData = 0x0208;
    private static final int PingData = 0x0601;
    private static final int Login = 0x0700;
    private static final int NotifyType = 0x0801;
    private static final int PACKET_TYPE_CAPTURE_AUDIO=0x01;
    private static final String audioSessionId = "AudioSession";

    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;

    public AudioTcpClient(final String ip, final int port) {
        super(ip,port);
        setName(getClass().getSimpleName());
    }

    @Override
    public void onConnected(Socket client) {
        try {
            dataInputStream = new DataInputStream(client.getInputStream());
            dataOutputStream = new DataOutputStream(client.getOutputStream());

            sendLogin(audioSessionId);

            byte[] header = new byte[4];
            while (!isInterrupted()){
                dataInputStream.readFully(header);
                int type=((header[1]&0xFF)<<8)|(header[0]&0xFF);
                int dataLen=((header[3]&0xFF)<<8)|(header[2]&0xFF);

                byte[] buffer = new byte[dataLen];
                dataInputStream.readFully(buffer,0,dataLen);

                switch (type) {
                    case PingData:
                    case InputData:
                    case NotifyType:
                        break;
                }
            }
        } catch (Exception e) {
            Log.d(TAG,e.toString());
        } finally {
            try {
                dataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataOutputStream = null;

            try {
                dataInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataInputStream = null;
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();

        if(dataInputStream!=null) {
            try {
                dataInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dataInputStream = null;
        }
    }

    public void sendLogin(String sessionId) {
        if(dataOutputStream==null)
            return;

        byte[] sessionID = sessionId.getBytes(StandardCharsets.UTF_8);
        int payloadByteSize = sessionID.length;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = Login&0xFF;
        buf[1] = (Login>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        System.arraycopy(sessionID, 0, buf, 4, sessionID.length);
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG,"sendLogin end threadID:"+Thread.currentThread().getId());
    }

    public void sendAudioData(byte[] audio_data, long ts, byte sound_format, byte sound_rate, byte sound_size, byte sound_type) {
        if(dataOutputStream==null)
            return;

        int payloadByteSize2 = 16+audio_data.length;
        int payloadByteSize = payloadByteSize2 + 4;
        byte[] buf = new byte[4+payloadByteSize];
        buf[0] = InputAudioData&0xFF;
        buf[1] = (InputAudioData>>8)&0xFF;
        buf[2] = (byte) (payloadByteSize&0xFF);
        buf[3] = (byte) ((payloadByteSize>>8)&0xFF);
        buf[4] = (byte) (payloadByteSize2&0xFF);
        buf[5] = (byte) ((payloadByteSize2>>8)&0xFF);
        buf[6] = (byte) ((payloadByteSize2>>16)&0xFF);
        buf[7] = (byte) ((payloadByteSize2>>24)&0xFF);
        buf[8] = (PACKET_TYPE_CAPTURE_AUDIO>>24)&0xFF;
        buf[9] = (PACKET_TYPE_CAPTURE_AUDIO>>16)&0xFF;
        buf[10] = (PACKET_TYPE_CAPTURE_AUDIO>>8)&0xFF;
        buf[11] = PACKET_TYPE_CAPTURE_AUDIO&0xFF;
        buf[12] = (byte) ((ts>>56)&0xFF);
        buf[13] = (byte) ((ts>>48)&0xFF);
        buf[14] = (byte) ((ts>>40)&0xFF);
        buf[15] = (byte) ((ts>>32)&0xFF);
        buf[16] = (byte) ((ts>>24)&0xFF);
        buf[17] = (byte) ((ts>>16)&0xFF);
        buf[18] = (byte) ((ts>>8)&0xFF);
        buf[19] = (byte) (ts&0xFF);
        buf[20] = sound_format;
        buf[21] = sound_rate;
        buf[22] = sound_size;
        buf[23] = sound_type;
        System.arraycopy(audio_data, 0, buf, 24, audio_data.length);
        try {
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}