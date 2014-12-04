package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModelListener;
import com.github.aklatt1194.SuperAwesomeOverlay.network.BaseLayerSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.NetworkInterface;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;
import com.github.aklatt1194.SuperAwesomeOverlay.utils.IPUtils;

public class OverlayRoutingManager implements Runnable,
        OverlayRoutingModelListener {
    public static final int ROUTING_UPDATE_SIZE = 8192;
    public static final int PORT = 55555;
    public static final long LINK_STATE_PERIOD = 1000 * 60; // 60 sec
    public static final long METRIC_AVERAGE_PERIOD = 60 * 1000 * 5; // 5 min
    public static final long LS_TIMEOUT = 2 * 1000;
    public static final long BOOTUP_TIME = 2 * 1000;

    private volatile boolean forceLinkState;
    private BaseLayerSocket socket;
    private MetricsDatabaseManager db;
    private OverlayRoutingModel model;
    private long lastLinkState;

    private Thread managerThread;

    public OverlayRoutingManager(OverlayRoutingModel model,
            MetricsDatabaseManager db) {
        this.forceLinkState = true;
        this.lastLinkState = 0;
        this.db = db;
        this.model = model;
        this.socket = new BaseLayerSocket();
        this.socket.bind(PORT);

        model.addListener(this);

        managerThread = new Thread(this);
        managerThread.start();
    }

    @Override
    public void run() {
        // We may want to try sleeping for a little bit to give the
        // NetworkInterface
        // time to establish all of its connections before we start sending out
        // link state updates.
        try {
            Thread.sleep(BOOTUP_TIME);
        } catch (InterruptedException e) {
        }

        Set<InetAddress> expected = null;
        while (true) {
            // Should we be initiating a link state update
            if (forceLinkState || (System.currentTimeMillis() - lastLinkState) > LINK_STATE_PERIOD) {
             // Nodes we are expecting to receive ls updates from
                expected = new HashSet<InetAddress>(model.getKnownNeighbors()); 
                
                /**
                // DEBUG
                System.out.println(System.currentTimeMillis() + ": Start of new ls update");
                System.out.print(System.currentTimeMillis() + ": Sending ls packets to:");
                for (InetAddress addr: expected)
                    System.out.print(" " + addr);
                System.out.println();
                */
                
                sendLinkStateUpdate(new ArrayList<InetAddress>(expected));
                forceLinkState = false;
            } else {
                // If we received a link state packet, initiate an update
                SimpleDatagramPacket initPacket;
                try {
                    initPacket = socket.interruptibleReceive(LINK_STATE_PERIOD - (System.currentTimeMillis() - lastLinkState));
                    if (initPacket == null) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    continue;
                }

                // Receive the packet and then send out a LS update of our own
                if (initPacket != null) {
                    // Receive the packet
                    TopologyUpdate initUpd = TopologyUpdate
                            .deserialize(initPacket.getPayload());
                    model.recordLinkStateInformation(initUpd);
                    
                    /**
                    // DEBUG
                    System.out.println(System.currentTimeMillis() + ": Start of new ls update");
                    System.out.println(System.currentTimeMillis() + ": Recieved ls from " + initUpd.src);
                    */
                    
                    // We are expecting to hear from all of our neighbors
                    expected = new HashSet<InetAddress>(model.getKnownNeighbors());
                    
                    /**
                    // DEBUG
                    System.out.print(System.currentTimeMillis() + ": Sending ls packets to:");
                    for (InetAddress addr: expected)
                        System.out.print(" " + addr);
                    System.out.println();
                    */
                    
                    // Send out our own ls update
                    sendLinkStateUpdate(new ArrayList<InetAddress>(expected));
                    
                    expected.remove(initUpd.src);
                }
            }

            // While we are expecting more packets, keep receiving
            while (!expected.isEmpty()) {
                SimpleDatagramPacket packet = socket.receive(LS_TIMEOUT);

                // We have not received any more packets, the remaining nodes
                // must be down. Explicitly disconnect and remove them.
                if (packet == null) {
                    // Disconnect from the remaining nodes
                    for (InetAddress node : expected) {
                        NetworkInterface.getInstance().disconnectFromNode(node);
                    }

                    break;
                } else {
                    TopologyUpdate upd = TopologyUpdate.deserialize(packet
                            .getPayload());
                    model.recordLinkStateInformation(upd);
                    
                    /**
                    // DEBUG
                    System.out.println(System.currentTimeMillis() + ": Recieved ls from " + upd.src);
                    */
                   
                    
                    expected.remove(upd.src);
                }
            }
            // Rebuild the model with the new information
            model.triggerFullUpdate();
        }
    }

    /**
     * Send out a link state update to all of our neighbors and apply it to
     * ourself
     */
    private void sendLinkStateUpdate(List<InetAddress> dests) {
        // Get the update from the db
        TopologyUpdate upd = getMetricsFromDB();
        // Apply it to ourselves
        model.recordLinkStateInformation(upd);

        // Send it to all of our neighbors
        for (InetAddress dst : dests) {
            SimpleDatagramPacket packet = new SimpleDatagramPacket(upd.src,
                    dst, PORT, PORT, upd.serialize());
            try {
                socket.send(packet);
            } catch (IOException e) {
                // TODO -- not connected to this host
            }
        }

        // Update when we sent the last link state update (i.e. just now)
        lastLinkState = System.currentTimeMillis();
    }

    /**
     * Query the database for metrics and return the result in the form of a
     * topology update
     */
    private TopologyUpdate getMetricsFromDB() {
        TopologyUpdate upd = new TopologyUpdate();

        // Add the src
        upd.src = model.getSelfAddress();
        upd.metrics.put(upd.src, -1.);

        long time = System.currentTimeMillis();

        for (InetAddress addr : model.getKnownNeighbors()) {
            String node = addr.getHostAddress();

            Map<Long, Double> latencies = db.getLatencyData(node, time
                    - METRIC_AVERAGE_PERIOD, time);
            Map<Long, Double> throughputs = db.getThroughputData(node, time
                    - METRIC_AVERAGE_PERIOD, time);

            double avgLat = getAvg(latencies);
            double avgThrough = getAvg(throughputs);

            double metric = computeMetric(avgLat, avgThrough);

            upd.metrics.put(addr, metric);
        }

        return upd;
    }

    private double computeMetric(double latency, double throughput) {
        // TODO if we want a more sophisticated metric, here is the place to
        // compute it.
        return latency;
    }

    private double getAvg(Map<Long, Double> stats) {
        if (stats.isEmpty())
            return OverlayRoutingModel.DEFAULT_METRIC;

        double avg = 0;

        for (Long l : stats.keySet())
            avg += stats.get(l);

        avg /= stats.size();

        return avg;
    }

    @Override
    public void nodeChangeCallback() {
        // Set force link state to true and interrupt the block receive in the
        // main loop
        forceLinkState = true;
        managerThread.interrupt();
    }

    public static class TopologyUpdate {
        public InetAddress src;
        public Map<InetAddress, Double> metrics;

        public TopologyUpdate() {
            this.metrics = new HashMap<InetAddress, Double>();
        }

        public byte[] serialize() {
            ByteBuffer buf = ByteBuffer.allocate(ROUTING_UPDATE_SIZE);
            IPUtils.serializeIPAddr(src, buf);
            for (InetAddress addr : metrics.keySet()) {
                IPUtils.serializeIPAddr(addr, buf);
                buf.putDouble(metrics.get(addr));
            }
            byte[] result = new byte[buf.position()];

            buf.flip();
            buf.get(result);
            return result;
        }

        public static TopologyUpdate deserialize(byte[] in) {
            TopologyUpdate result = new TopologyUpdate();

            ByteBuffer buf = ByteBuffer.wrap(in);
            InetAddress src = IPUtils.deserializeIPAddr(buf);
            result.src = src;
            while (buf.hasRemaining()) {
                InetAddress addr = IPUtils.deserializeIPAddr(buf);

                if (addr == null)
                    return null;

                Double d = buf.getDouble();
                result.metrics.put(addr, d);
            }
            return result;
        }
    }
}
