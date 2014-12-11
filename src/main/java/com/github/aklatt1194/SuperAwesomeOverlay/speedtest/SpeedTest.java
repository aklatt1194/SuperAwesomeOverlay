package com.github.aklatt1194.SuperAwesomeOverlay.speedtest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModelListener;

public class SpeedTest {
    private final int SERVER_PORT = 4545;
    private final int NUM_PACKETS = 1000;
    private final int TEST_INTERVAL = 10 * 60 * 1000; // connect to a remote
                                                      // every 10 minutes to do
                                                      // a test

    private OverlayRoutingModel model;
    private MetricsDatabaseManager db;

    public SpeedTest(OverlayRoutingModel model, MetricsDatabaseManager db) {
        this.model = model;
        this.db = db;

        new Thread(new Sender()).start();

        // add some jitter so that a bunch of nodes starting at the same time
        // don't all try to connect to the same host
        try {
            Thread.sleep(new Random().nextInt(TEST_INTERVAL));
        } catch (InterruptedException e) {
        }
        new Thread(new Receiver()).start();
    }

    class Sender implements Runnable {
        private ServerSocket ss;

        public Sender() {
            try {
                ss = new ServerSocket(SERVER_PORT);
            } catch (IOException e) {
                System.err.println("Unable to open speedtest server socket");
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (true) {
                try (Socket s = ss.accept();
                        OutputStream os = s.getOutputStream();
                        DataOutputStream dos = new DataOutputStream(os)) {
                    Random r = new Random();

                    for (int i = 0; i < NUM_PACKETS; i++) {
                        final int packetSize = r.nextInt(8192) + 2048;
                        final byte[] packet = new byte[packetSize];
                        r.nextBytes(packet);

                        dos.writeInt(packetSize);
                        dos.write(packet);
                        dos.flush();
                    }
                } catch (IOException e) {
                }
            }
        }
    }

    class Receiver implements Runnable, OverlayRoutingModelListener {
        private BlockingQueue<InetAddress> hosts;

        public Receiver() {
            hosts = new LinkedBlockingQueue<>();
            model.addListener(this);
        }

        @Override
        public void run() {
            while (true) {
                if (hosts.isEmpty()) {
                    hosts.addAll(model.getKnownNeighbors());
                }

                InetAddress host;
                try {
                    host = hosts.take();
                } catch (InterruptedException e) {
                    continue;
                }

                try (Socket s = new Socket(host, SERVER_PORT);
                        InputStream is = s.getInputStream();
                        DataInputStream dis = new DataInputStream(is)) {

                    int totalBytes = 0;

                    int packetSize = dis.readInt();
                    byte[] packet = new byte[packetSize];
                    long start = System.currentTimeMillis();
                    dis.readFully(packet);
                    totalBytes += packetSize;

                    for (int i = 0; i < NUM_PACKETS - 1; i++) {
                        packetSize = dis.readInt();
                        packet = new byte[packetSize];
                        dis.readFully(packet);
                        totalBytes += packetSize;
                    }

                    long totalTime = System.currentTimeMillis() - start;

                    double downstreamBytesPerSecond = totalBytes / (totalTime / 1000.0);
                    db.addThroughputData(host.getHostAddress(), System.currentTimeMillis(),
                            downstreamBytesPerSecond);
                } catch (IOException e) {
                    // this test failed for some reason, stick this host on the
                    // end of the queue
                    hosts.add(host);
                    continue;
                }

                try {
                    Thread.sleep(TEST_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void nodeAddCallback(InetAddress addr) {
            hosts.add(addr);
        }

        @Override
        public void nodeDeleteCallback(InetAddress addr) {
            hosts.remove(addr);
            if (hosts.isEmpty()) {
                hosts.addAll(model.getKnownNeighbors());
            }
        }
        
        @Override
        public void topologyChangeCallback() {
        }
    }
}
