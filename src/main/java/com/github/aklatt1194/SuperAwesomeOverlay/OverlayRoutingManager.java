package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
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

public class OverlayRoutingManager implements Runnable, OverlayRoutingModelListener {
    public static final int ROUTING_UPDATE_SIZE = 8192;
    public static final int PORT = 55555;
    public static final long LINK_STATE_PERIOD = 1000 * 60; // 60 sec
    public static final long METRIC_AVERAGE_PERIOD = 60 * 1000 * 5; // 5 min
    public static final long LS_TIMEOUT = 4 * 1000;
    public static final long BOOTUP_TIME = 2 * 1000;

    private BaseLayerSocket socket;
    private MetricsDatabaseManager db;
    private OverlayRoutingModel model;
    private Set<InetAddress> expected;

    private volatile long end;

    private Thread managerThread;

    public OverlayRoutingManager(OverlayRoutingModel model, MetricsDatabaseManager db) {
        this.db = db;
        this.model = model;
        this.socket = new BaseLayerSocket();
        this.socket.bind(PORT);

        expected = Collections.synchronizedSet(new HashSet<InetAddress>());

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

        while (true) {
            TopologyUpdate upd = null;
            SimpleDatagramPacket packet = null;
            expected.clear();
            
            // wait until we receive a LS packet
            packet = socket.receive(LINK_STATE_PERIOD);

            List<InetAddress> neighbors = model.getKnownNeighbors();
            expected.addAll(neighbors);
            sendLinkStateUpdate(neighbors);

            if (packet != null) {
                upd = TopologyUpdate.deserialize(packet.getPayload());
                model.recordLinkStateInformation(upd);
                expected.remove(packet.getSource());
            }

            end = System.currentTimeMillis() + LS_TIMEOUT;

            while (true) {
                packet = socket.receive(LS_TIMEOUT);
                synchronized (this) {
                    if (packet == null && System.currentTimeMillis() > end) {
                        break;
                    }
                }
                
                if (packet == null) {
                    continue;
                }

                upd = TopologyUpdate.deserialize(packet.getPayload());
                model.recordLinkStateInformation(upd);
                expected.remove(packet.getSource());
            }

            model.triggerFullUpdate();

            for (InetAddress addr : expected) {
                System.out.println("DEBUG: Didn't receive a LS packet from: " + addr.toString());
                NetworkInterface.getInstance().disconnectFromNode(addr);
            }
        }
    }
    
    @Override
    public void nodeAddCallback(InetAddress addr) {
        sendLinkStateUpdate(Arrays.asList(addr));
        expected.add(addr);
        synchronized (this) {
            end = System.currentTimeMillis() + LS_TIMEOUT;
        }
    }

    @Override
    public void nodeDeleteCallback(InetAddress addr) {
        // TODO Do we need to do anything if a node is deleted?
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
            SimpleDatagramPacket packet = new SimpleDatagramPacket(upd.src, dst, PORT, PORT,
                    upd.serialize());
            try {
                socket.send(packet);
            } catch (IOException e) {
                // TODO -- not connected to this host
                System.out.println("DEBUG: manager trying to send to closed socket");
            }
        }
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

            Map<Long, Double> latencies = db.getLatencyData(node, time - METRIC_AVERAGE_PERIOD,
                    time);
            Map<Long, Double> throughputs = db.getThroughputData(node,
                    time - METRIC_AVERAGE_PERIOD, time);

            // If we don't have any recent data, get the most recent data that
            // we do have
            if (latencies.isEmpty()) {
                long lastTime = db.getLastLatencyRecordTime(node);
                latencies = db.getLatencyData(node, lastTime - METRIC_AVERAGE_PERIOD, lastTime + 1);
            }
            if (throughputs.isEmpty()) {
                long lastTime = db.getLastThroughputRecordTime(node);
                throughputs = db.getThroughputData(node, lastTime - METRIC_AVERAGE_PERIOD,
                        lastTime + 1);
            }

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
        // If we have no data, return a default
        if (stats.isEmpty())
            return OverlayRoutingModel.DEFAULT_METRIC;

        double avg = 0;

        for (Long l : stats.keySet())
            avg += stats.get(l);

        avg /= stats.size();

        return avg;
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
