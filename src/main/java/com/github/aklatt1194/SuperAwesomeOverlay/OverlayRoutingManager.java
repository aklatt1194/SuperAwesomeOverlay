package com.github.aklatt1194.SuperAwesomeOverlay;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.DatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import com.github.aklatt1194.SuperAwesomeOverlay.network.BaseLayerSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;

public class OverlayRoutingManager implements Runnable {
    public static final int ROUTING_UPDATE_SIZE = 8192;
    
    private BaseLayerSocket socket;
    private DatabaseManager db;
    private OverlayRoutingModel model;
    
    public OverlayRoutingManager(RoutingTable rTbl, DatabaseManager db) {
        this.db = db;
        this.model = new OverlayRoutingModel(rTbl);
        this.socket = new BaseLayerSocket();
        this.socket.bind(55555);
        new Thread(this).start();
    }

    @Override
    public void run() {
        // TODO, upon receiving an update, we may wish to inform other nodes
        // TODO when do we remove a node
        // TODO periodically send out updates
        // TODO forward these updates along?
        // TODO TTL for these updates
        
        while (true) {
            SimpleDatagramPacket packet = socket.receive();
            TopologyUpdate upd = TopologyUpdate.deserialize(packet.getPayload());
            model.updateTopology(upd.src, upd.metrics);
        }
        
        
    }
    
    private static class TopologyUpdate {
        public InetAddress src;
        public Map<InetAddress, Double> metrics;
        
        public TopologyUpdate() {
            this.metrics = new HashMap<InetAddress, Double>();
        }
        
        public byte[] serialize() {
            ByteBuffer buf = ByteBuffer.allocate(ROUTING_UPDATE_SIZE);
            for (InetAddress address : metrics.keySet()) {
                String addr = address.getHostAddress();
                for (int i = 0; i < addr.length(); i++) {
                    buf.putChar(addr.charAt(i));
                }
                buf.putChar((char) 0); // Null terminate
                buf.putDouble(metrics.get(addr));
            }
            byte[] result = new byte[buf.position() + 1];
            buf.get(result);
            return result;
        }
        
        public static TopologyUpdate deserialize(byte[] in) {
            TopologyUpdate result = new TopologyUpdate();
            
            ByteBuffer buf = ByteBuffer.wrap(in);
            for (int i = 0; i < in.length; i++) {
                // Get the string
                String addr = "";
                char c = buf.getChar();
                while (c != 0) {
                    addr += c;
                }
                
                // Get the double
                Double d = buf.getDouble();
                
                try {
                    result.metrics.put(InetAddress.getByName(addr), d);
                } catch (UnknownHostException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return result;
        }
    }
}
