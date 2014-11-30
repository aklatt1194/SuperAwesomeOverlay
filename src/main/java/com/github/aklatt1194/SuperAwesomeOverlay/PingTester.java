package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Date;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.network.BaseLayerSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;

public class PingTester {
    public static final byte REQUEST = 0x1;
    public static final byte RESPONSE = 0x2;
    public static final int PORT = 9876;
    BaseLayerSocket socket;
    OverlayRoutingModel model;
    MetricsDatabaseManager db;

    public PingTester(OverlayRoutingModel model, MetricsDatabaseManager db) {
        this.model = model;
        this.db = db;

        socket = new BaseLayerSocket();
        socket.bind(PORT);

        Thread sender = new Thread(new PingTestSender());
        Thread receiver = new Thread(new PingTestReceiver());
        sender.start();
        receiver.start();
    }

    class PingTestSender implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                }
                for (InetAddress node : model.getKnownNeighbors()) {
                    ByteBuffer buf = ByteBuffer.allocate(9);
                    buf.put(REQUEST);
                    buf.putLong(new Date().getTime());

                    SimpleDatagramPacket packet = new SimpleDatagramPacket(
                            model.getSelfAddress(), node, PORT, PORT,
                            buf.array());
                    
                    try {
                        socket.send(packet);
                    } catch (IOException e) {
                        // TODO -- This node is no longer connected
                    }
                }
            }
        }
    }

    class PingTestReceiver implements Runnable {
        @Override
        public void run() {
            while (true) {
                SimpleDatagramPacket response = socket.receive();
                ByteBuffer buf = ByteBuffer.wrap(response.getPayload());
                byte flags = buf.get();

                if (flags == REQUEST) {
                    byte[] payload = buf.array();
                    payload[0] = RESPONSE;
                    try {
                        socket.send(new SimpleDatagramPacket(model.getSelfAddress(), response.getSource(), PORT, PORT, payload));
                    } catch (IOException e) {
                        // TODO -- This node is no longer connected
                    }
                } else {
                    long timestamp = buf.getLong();
                    db.addLatencyData(response.getSource().getHostAddress(),
                            System.currentTimeMillis(), new Date().getTime()
                                    - timestamp);
                }
            }
        }
    }
}