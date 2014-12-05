package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;

public class OverlayDatagramSocket extends SimpleSocket {
    
    private OverlayRoutingModel model;
    
    public OverlayDatagramSocket(OverlayRoutingModel model) {
        super();
        this.model = model;
    }
    
    /**
     * Receive an Overlay 'broadcast' packet, and forward it along if necessary
     */
    @Override 
    public SimpleDatagramPacket receive() {
        // We block here
        SimpleDatagramPacket packet = super.receive();
        
        int ttl = packet.getTTL();
        int type = packet.getType();
        
        // If this is a broadcast packet and it still has time to live, forward it along
        if (ttl > 0 && type == SimpleDatagramPacket.BROADCAST_TYPE) {
            doBroadcastForward(packet);
        }
        
        return packet;
    }
    
    /**
     * Send out a broadcast message
     */
    @Override
    public void send(SimpleDatagramPacket packet) {
        SimpleDatagramPacket out = new SimpleDatagramPacket(model.getSelfAddress(), 
                null, SimpleDatagramPacket.BROADCAST_TYPE, -1, 
                packet.getSourcePort(), packet.getDestinationPort(), 
                packet.getPayload());
        doBroadcastForward(out);
        
    }
    
    /**
     * A helper method for sending out/forwarding along a broadcast packet
     */
    private void doBroadcastForward(SimpleDatagramPacket packet) {
        Map<InetAddress, InetAddress> fTable = model.getForwardingTable();
        
        List<InetAddress> outInterfaces = new ArrayList<InetAddress>();
        List<InetAddress> knownNeighbors = model.getKnownNeighbors();
        
        // If the packet has a non-negative ttl, then it came from someone else,
        // so we should just decrement it. Otherwise, we are starting a broadcast,
        // so we set the ttl to a default value (the number of known nodes - 1 seems
        // reasonable).
        int ttl = (packet.getTTL() > 0) ? packet.getTTL() - 1: knownNeighbors.size();
        
        
        // Go through the list of known nodes and figure out which outInterfaces
        // we need to be sending out on.
        for (InetAddress node : knownNeighbors) {
            InetAddress portForNode = fTable.get(node);
            if (!portForNode.equals(packet.getSource())) {
                outInterfaces.add(portForNode);
            }
        }
        
        // Construct and send a packet to each of these outInterfaces
        for (InetAddress outInterface : outInterfaces) {
            SimpleDatagramPacket outPacket = new SimpleDatagramPacket(model.getSelfAddress(), 
                    outInterface, packet.getType(), ttl, 
                    packet.getSourcePort(), packet.getDestinationPort(), 
                    packet.getPayload());
            
            try {
                NetworkInterface.getInstance().send(outPacket);
            } catch (IOException e) {
                System.err.println("ERROR: Unable to forward broadcast packet");
                e.printStackTrace();
            }
        }
    }
}