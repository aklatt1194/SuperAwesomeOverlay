package com.github.aklatt1194.SuperAwesomeOverlay.models;

public class RoutingTable {
    public static final String[] NODES = {
        "ec2-54-171-49-149.eu-west-1.compute.amazonaws.com",
        "ec2-54-65-17-109.ap-northeast-1.compute.amazonaws.com",
        "ec2-54-173-133-146.compute-1.amazonaws.com" };
    
    public String[] getKnownNodes() {
        return NODES;
    }
}