package com.limelight.utils;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class TcpClient extends Thread {
    private final String ip;
    private final int port;
    private Socket client=null;

    public TcpClient(String ip, int port){
        this.ip=ip;
        this.port=port;
        client=new Socket();
    }

    public abstract void onConnected(Socket client);

    @Override
    public void run() {
        super.run();
        while (!isInterrupted()){
            try {
                client.connect(new InetSocketAddress(ip, port));
                onConnected(client);
            } catch (Exception e) {
                Log.d("llx",e.toString());
                try {
                    sleep(500);
                } catch (InterruptedException ex) {
                    break;
                }
            } finally {
                if(client!=null) {
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    client = null;
                }
            }
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        if(client!=null) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            client = null;
        }
    }
}
