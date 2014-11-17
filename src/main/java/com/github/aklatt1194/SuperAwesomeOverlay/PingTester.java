package com.github.aklatt1194.SuperAwesomeOverlay;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Date;

import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import com.github.aklatt1194.SuperAwesomeOverlay.network.BaseLayerSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;

public class PingTester {
    public PingTester(RoutingTable routingTable) {
        Thread sender = new Thread(new PingTestSender(routingTable));
        Thread receiver = new Thread(new PingTestReceiver(routingTable));
        sender.start();
        receiver.start();
    }

    static class PingTestSender implements Runnable {
        BaseLayerSocket socket;
        RoutingTable routingTable;

        PingTestSender(RoutingTable routingTable) {
            this.routingTable = routingTable;
            socket = new BaseLayerSocket();
            socket.bind(9876);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
                for (InetAddress node : routingTable.getKnownNeigborAddresses()) {
                    ByteBuffer buf = ByteBuffer.allocate(8);
                    buf.putLong(new Date().getTime());

                    SimpleDatagramPacket packet = new SimpleDatagramPacket(
                            routingTable.getSelfAddress(), node, 9876, 6789,
                            buf.array());
                    socket.send(packet);

                    SimpleDatagramPacket response = socket.receive();
                    long timestamp = ByteBuffer.wrap(response.getPayload())
                            .getLong();
                    System.out.println("response from "
                            + response.getSource().getHostAddress() + " "
                            + (timestamp - (new Date().getTime())));
                }
            }
        }
    }

    static class PingTestReceiver implements Runnable {
        BaseLayerSocket socket;
        RoutingTable routingTable;

        PingTestReceiver(RoutingTable routingTable) {
            this.routingTable = routingTable;
            socket = new BaseLayerSocket();
            socket.bind(6789);
        }

        @Override
        public void run() {
            while (true) {
                SimpleDatagramPacket packet = socket.receive();
                socket.send(new SimpleDatagramPacket(packet.getSource(),
                        routingTable.getSelfAddress(), packet
                                .getDestinationPort(), packet.getSourcePort(),
                        packet.getPayload()));
            }
        }
    }
}
