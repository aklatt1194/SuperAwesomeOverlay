package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.io.IOException;

public class BaseLayerSocket extends SimpleSocket {
    private static final int DEFAULT_TTL = 1;
    
    @Override
    public void send(SimpleDatagramPacket packet) throws IOException {
        packet.flags |= SimpleDatagramPacket.BASELAYER;
        packet.ttl = DEFAULT_TTL;
        NetworkInterface.getInstance().send(packet);        
    }
}