package com.github.aklatt1194.SuperAwesomeOverlay;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Date;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.network.BaseLayerSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;

public class PingTester {
    public PingTester(OverlayRoutingModel model,
            MetricsDatabaseManager dbManager) {
        Thread sender = new Thread(new PingTestSender(model, dbManager));
        Thread receiver = new Thread(new PingTestReplier(model));
        sender.start();
        receiver.start();
    }

    static class PingTestSender implements Runnable {
        BaseLayerSocket socket;
        OverlayRoutingModel model;

        PingTestSender(OverlayRoutingModel model,
                MetricsDatabaseManager dbManager) {
            this.model = model;
            socket = new BaseLayerSocket();
            socket.bind(9876);

            Thread receiver = new Thread(new PingTestReceiver(dbManager));
            receiver.start();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                }
                for (InetAddress node : model.getKnownNeighbors()) {
                    ByteBuffer buf = ByteBuffer.allocate(8);
                    buf.putLong(new Date().getTime());

                    SimpleDatagramPacket packet = new SimpleDatagramPacket(
                            model.getSelfAddress(), node, 9876, 6789,
                            buf.array());
                    socket.send(packet);
                }
            }
        }

        class PingTestReceiver implements Runnable {
            private MetricsDatabaseManager dbManager;

            public PingTestReceiver(MetricsDatabaseManager dbManager) {
                this.dbManager = dbManager;
            }

            @Override
            public void run() {
                while (true) {
                    SimpleDatagramPacket response = socket.receive();
                    long timestamp = ByteBuffer.wrap(response.getPayload())
                            .getLong();
                    // Collect the result in the DB
                    dbManager.addLatencyData(response.getSource()
                            .getHostAddress(), System.currentTimeMillis(),
                            new Date().getTime() - timestamp);

                    /*
                     * Uncomment for debug System.out.println("response from " +
                     * response.getSource().getHostAddress() + " " + ((new
                     * Date().getTime()) - timestamp));
                     */
                }
            }
        }
    }

    static class PingTestReplier implements Runnable {
        BaseLayerSocket socket;
        OverlayRoutingModel model;

        PingTestReplier(OverlayRoutingModel model) {
            this.model = model;
            socket = new BaseLayerSocket();
            socket.bind(6789);
        }

        @Override
        public void run() {
            while (true) {
                SimpleDatagramPacket packet = socket.receive();
                socket.send(new SimpleDatagramPacket(model.getSelfAddress(),
                        packet.getSource(), packet.getDestinationPort(), packet
                                .getSourcePort(), packet.getPayload()));
            }
        }
    }
}
