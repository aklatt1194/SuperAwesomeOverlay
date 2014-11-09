package com.github.aklatt1194.SuperAwesomeOverlay.models;

public class RoutingTable {
    public static final String[] NODES = {
        "sidney.duckdns.org",  // for testing purposes
        "ec2-54-72-49-50.eu-west-1.compute.amazonaws.com",
        "ec2-54-64-177-145.ap-northeast-1.compute.amazonaws.com",
        "ec2-54-172-69-181.compute-1.amazonaws.com"};
    
    public String[] getKnownNodes() {
        return NODES;
    }
}