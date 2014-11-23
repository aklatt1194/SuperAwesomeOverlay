package com.github.aklatt1194.SuperAwesomeOverlay;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.network.BaseLayerSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;
import com.github.aklatt1194.SuperAwesomeOverlay.utils.IPUtils;

public class OverlayRoutingManager implements Runnable {
    public static final int ROUTING_UPDATE_SIZE = 8192;
    public static final int PORT = 55555;
    public static final long LINK_STATE_PERIOD = 30000; //30 sec
    public static final long METRIC_AVERAGE_PERIOD = 60000 * 5; // 5 min
    
    private BaseLayerSocket socket;
    private MetricsDatabaseManager db;
    private OverlayRoutingModel model;
    
    public OverlayRoutingManager(OverlayRoutingModel model, MetricsDatabaseManager db) {
        this.db = db;
        this.model = model;
        this.socket = new BaseLayerSocket();
        this.socket.bind(PORT);
        new Thread(this).start();
    }

    @Override
    public void run() {
        // TODO We might want special add node and remove node packets in addition to
        // simple updates
        
        long lastLinkState = 0;
        while (true) {
            // If we have not sent link state messages for a while, do so
            if (System.currentTimeMillis() - lastLinkState > LINK_STATE_PERIOD) {
                // Get the update from the db
                TopologyUpdate upd = getMetricsFromDB();
                // Apply it to ourselves
                model.updateTopology(upd.src, upd.metrics);
                
                // Send it to all of our neighbors
                for (InetAddress dst : model.getKnownNeighbors()) {
                    SimpleDatagramPacket packet = new SimpleDatagramPacket(upd.src, 
                            dst, PORT, PORT, upd.serialize());
                    socket.send(packet);
                }
                // Update when we sent the last link state update (i.e. just now)
                lastLinkState = System.currentTimeMillis();
            }
            
            SimpleDatagramPacket packet = socket.receive(LINK_STATE_PERIOD - 
                    (System.currentTimeMillis() - lastLinkState));
            if (packet != null) {
                TopologyUpdate upd = TopologyUpdate.deserialize(packet.getPayload());
                model.updateTopology(upd.src, upd.metrics);
                // TODO If we want to forward these updates, should do it here?
                // TODO If we do forward, we need to think about TTL for them.
            }
        }  
    }
    
    private TopologyUpdate getMetricsFromDB() {
        TopologyUpdate upd = new TopologyUpdate();
        
        // Add the src
        upd.src = model.getSelfAddress();
        upd.metrics.put(upd.src, -1.);
        
        long time = System.currentTimeMillis();
        for (InetAddress addr : model.getKnownNeighbors()) {
            String node = addr.getHostAddress();
            
            Map<Long, Double> latencies = db.getLatencyData(node, 
                    time - METRIC_AVERAGE_PERIOD, time);
            Map<Long, Double> throughputs = db.getThroughputData(node, 
                    time - METRIC_AVERAGE_PERIOD, time);
            
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
        double avg = 0;
        
        for (Long l : stats.keySet())
            avg += stats.get(l);
        
        avg /= stats.size();
        
        return avg;
    }
    
    private static class TopologyUpdate {
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
