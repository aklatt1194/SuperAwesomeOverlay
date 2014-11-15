package com.github.aklatt1194.SuperAwesomeOverlay.Network;

import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class SimpleSocket {
    private BlockingQueue<ByteBuffer> readQueue;
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

    public abstract void send();

    public ByteBuffer receive() {
        ByteBuffer bb;
        while (true) {
            try {
                bb = readQueue.take();
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }
        return bb;
    }

    public void close() {
        NetworkInterface.getInstance().closeSocket(this);
    }
}
