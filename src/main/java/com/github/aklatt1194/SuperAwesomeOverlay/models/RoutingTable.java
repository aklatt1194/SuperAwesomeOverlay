package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class RoutingTable {
    public static final String[] NODES_BOOTSTRAP = {
        "sidney.duckdns.org",  // for testing purposes
        "ec2-54-72-49-50.eu-west-1.compute.amazonaws.com",
        "ec2-54-64-177-145.ap-northeast-1.compute.amazonaws.com",
        "ec2-54-172-69-181.compute-1.amazonaws.com"};

    private List<InetAddress> nodes;
    
    public RoutingTable() {
        nodes = new ArrayList<>();
        for (String node: NODES_BOOTSTRAP) {
            try {
                nodes.add(InetAddress.getByName(node));
            } catch (UnknownHostException e) {
                // pass
            }
        }
    }
    
    public List<InetAddress> getKnownNodes() {
        return nodes;
    }
}