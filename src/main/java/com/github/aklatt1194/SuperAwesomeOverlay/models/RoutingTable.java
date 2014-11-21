package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.github.aklatt1194.SuperAwesomeOverlay.utils.IPUtils;

public class RoutingTable {
    public static final String[] NODES_BOOTSTRAP = {
        "sidney.duckdns.org",  // for testing purposes
        "ec2-54-72-49-50.eu-west-1.compute.amazonaws.com",
        "ec2-54-64-177-145.ap-northeast-1.compute.amazonaws.com",
        "ec2-54-172-69-181.compute-1.amazonaws.com"};

    private List<InetAddress> nodes;
    private InetAddress selfAddr;
    
    public RoutingTable() {
        nodes = new ArrayList<>();
        
        try {
            selfAddr = IPUtils.getExternalAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        for (String node: NODES_BOOTSTRAP) {
            try {
                InetAddress addr = InetAddress.getByName(node);
                if (!addr.getHostAddress().equals(selfAddr.getHostAddress()))
                    nodes.add(InetAddress.getByName(node));
            } catch (UnknownHostException e) {
                // pass
            }
        }
    }
    
    // Return a copy to avoid concurrency issues
    public synchronized List<InetAddress> getKnownNeigborAddresses() {
        return new ArrayList<InetAddress>(nodes);
    }
    
    public synchronized void addNeighborNode(InetAddress addr) {
        nodes.add(addr);
    }
    
    public synchronized void removeNeighborNode(InetAddress addr) {
        nodes.remove(addr);
    }
    
    public InetAddress getSelfAddress() {
        return selfAddr;
    }
}