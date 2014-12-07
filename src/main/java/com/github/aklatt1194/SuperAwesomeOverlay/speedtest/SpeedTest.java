package com.github.aklatt1194.SuperAwesomeOverlay.speedtest;

import java.io.BufferedOutputStream;
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
                                                      // every 30 minutes to do
                                                      // a test

    private OverlayRoutingModel model;
    private MetricsDatabaseManager db;
    private Thread receiver;
    
    public SpeedTest(OverlayRoutingModel model, MetricsDatabaseManager db) {
        this.model = model;
        this.db = db;
        
        receiver = new Thread(new Receiver());
        receiver.start();
        new Thread(new Sender()).start();
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
            Socket s = null;
            OutputStream os = null;
            try {
                s = ss.accept();
                os = s.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(os));
            Random r = new Random();

            for (int i = 0; i < NUM_PACKETS; i++) {
                final int packetSize = r.nextInt(8192) + 2048;
                final byte[] packet = new byte[packetSize];
                r.nextBytes(packet);

                try {
                    dos.writeInt(packetSize);
                    dos.write(packet);
                    dos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                dos.close();
                os.close();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
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

                Socket s;
                InputStream is;
                try {
                    s = new Socket(host, SERVER_PORT);
                    is = s.getInputStream();
                } catch (IOException e1) {
                    continue;
                }

                DataInputStream dis = new DataInputStream(is);

                int totalBytes = 0;
                long totalTime = 0;

                for (int i = 0; i < NUM_PACKETS; i++) {
                    try {
                        int packetSize = dis.readInt();
                        
                        long start = System.currentTimeMillis();
                        final byte[] packet = new byte[packetSize];
                        dis.readFully(packet);
                        totalTime += System.currentTimeMillis() - start;
                        totalBytes += packetSize;
                    } catch (IOException e) {
                        break;
                    }
                }
                
                double downstreamBytesPerSecond = totalBytes / (totalTime / 1000.0);
                
                if (!Double.isNaN(downstreamBytesPerSecond)) {
                    db.addThroughputData(host.getHostAddress(), System.currentTimeMillis(), downstreamBytesPerSecond);
                }

                try {
                    dis.close();
                    is.close();
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
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
            receiver.interrupt();
        }

        @Override
        public void nodeDeleteCallback(InetAddress addr) {
            hosts.remove(addr);
            if (hosts.isEmpty()) {
                hosts.addAll(model.getKnownNeighbors());
            }
        }
    }
}
