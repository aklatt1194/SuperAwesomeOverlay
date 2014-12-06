package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;

public class OverlaySocket extends SimpleSocket {
    private static final int DEFAULT_TTL = 10;
    
    @Override
    public void send(SimpleDatagramPacket packet) throws IOException {
        packet.flags |= SimpleDatagramPacket.OVERLAY;
        packet.ttl = DEFAULT_TTL;
        NetworkInterface.getInstance().send(packet);        
    }
}