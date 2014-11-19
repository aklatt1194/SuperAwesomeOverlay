package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class SimpleSocket {
    protected BlockingQueue<SimpleDatagramPacket> readQueue;
    private int port;

    public SimpleSocket() {
        readQueue = new LinkedBlockingQueue<>();
        port = 0;
    }

    public void bind(int port) {
        this.port = port;
        try {
            NetworkInterface.getInstance().bindSocket(this, port);
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public abstract void send(SimpleDatagramPacket packet);

    public SimpleDatagramPacket receive() {
        SimpleDatagramPacket packet;
        while (true) {
            try {
                packet = readQueue.take();
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }
        return packet;
    }

    public void close() {
        NetworkInterface.getInstance().closeSocket(this);
    }
    
    public int getPort() {
        return port;
    }
}
