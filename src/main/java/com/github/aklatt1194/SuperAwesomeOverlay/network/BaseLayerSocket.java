package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.io.IOException;

public class BaseLayerSocket extends SimpleSocket {
    @Override
    public void send(SimpleDatagramPacket packet) throws IOException {
        NetworkInterface.getInstance().send(packet);        
    }
}