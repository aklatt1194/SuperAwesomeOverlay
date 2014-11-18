package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class OverlayRoutingModel {
    private RoutingTable rTbl;
    
    private Map<InetAddress, Long> lastUpdated;
    private Map<InetAddress, Integer> nodeToIndex;
    private Map<Integer, InetAddress> indexToNode;
    private double[][] metrics;
    
    
    private Tree Mst;
    private Map<InetAddress, InetAddress> forwardingTable;
    
    public OverlayRoutingModel(RoutingTable rTbl) {
        this.rTbl = rTbl;
        
        nodeToIndex = new HashMap<InetAddress, Integer>();
        indexToNode = new TreeMap<Integer, InetAddress>();
        forwardingTable = new HashMap<InetAddress, InetAddress>();
        
        List<InetAddress> neighbors = rTbl.getKnownNeigborAddresses();
        for (int i = 0; i < neighbors.size(); i++)
            nodeToIndex.put(neighbors.get(i), i);
        
        metrics = new double[nodeToIndex.size()][nodeToIndex.size()];
        
        update();  
    }
    
    
    // remove the node with the given address from the known set
    public void removeNodes(List<InetAddress> addrs) {
        for (InetAddress addr : addrs) {
            int index = nodeToIndex.get(addr);
            nodeToIndex.remove(addr);
            indexToNode.remove(index);
        }
        
        
        
        for (int i = 0; i < metrics.length; i++) {
            
        }
        
        
    }
    
    // Update metrics/known nodes
    public void update(InetAddress src, Map<InetAddress, Double> newValues) {
        // Update known nodes
        for (InetAddress addr : newValues.keySet()) {
            if (!nodeToIndex.containsKey(addr)) {
                addNode(addr);
            }
            
            lastUpdated.put(addr, System.currentTimeMillis());
        }
        
        // If number of known nodes has changed
        if (nodeToIndex.size() != metrics.length) {
            double[][] newMetrics = new double[nodeToIndex.size()][nodeToIndex.size()];
            
            for (int i = 0; i < 
            
            
        }
    }
    
    // Adds a node without updating the model
    private void addNode(InetAddress node) {
        nodeToIndex.put(node, nodeToIndex.size());
        indexToNode.put(indexToNode.size(), node);
    }
    
    // Update the model
    private void update() {
        buildMst();
        constructForwardingTable();
    }
    
    private void buildMst() {
        
    }
    
    private void constructForwardingTable() {
        
    }
}
