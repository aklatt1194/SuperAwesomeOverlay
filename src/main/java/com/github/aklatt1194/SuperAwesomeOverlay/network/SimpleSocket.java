package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    public abstract void send(SimpleDatagramPacket packet) throws IOException;

    public SimpleDatagramPacket receive() {
        SimpleDatagramPacket packet;
        while (true) {
            try {
                packet = readQueue.take();
            } catch (InterruptedException e) {
                continue;
            }
            return packet;
        }
    }

    public SimpleDatagramPacket receive(long timeout) {
        SimpleDatagramPacket packet = null;
        long last = System.currentTimeMillis();
        while (timeout > 0) {
            try {
                packet = readQueue.poll(timeout, TimeUnit.MILLISECONDS);
                break;
            } catch (InterruptedException e) {
                timeout -= System.currentTimeMillis() - last;
                last = System.currentTimeMillis();
            }
        }
        return packet;
    }
    
    public SimpleDatagramPacket interruptibleReceive(long timeout) throws InterruptedException {
        return readQueue.poll(timeout, TimeUnit.MILLISECONDS);
    }

    public void close() {
        NetworkInterface.getInstance().closeSocket(this);
    }

    public int getPort() {
        return port;
    }
}
