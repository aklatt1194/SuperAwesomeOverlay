package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.net.InetAddress;

public interface OverlayRoutingModelListener {
    public void nodeAddCallback(InetAddress addr);

    public void nodeDeleteCallback(InetAddress addr);
}