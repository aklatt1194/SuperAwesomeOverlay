package com.github.aklatt1194.SuperAwesomeOverlay.network;

public class BaseLayerSocket extends SimpleSocket {
    @Override
    public void send(SimpleDatagramPacket packet) {
        NetworkInterface.getInstance().send(packet);        
    }
}