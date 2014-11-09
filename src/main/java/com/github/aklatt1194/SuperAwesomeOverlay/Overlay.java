package com.github.aklatt1194.SuperAwesomeOverlay;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.server.Server;

import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import com.github.aklatt1194.SuperAwesomeOverlay.views.ChatEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.RoutingTableEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.WebRoutes;

public class Overlay {
    public static void main(String[] args) throws DeploymentException {
        new WebRoutes();
        new RoutingTableEndpoint(new RoutingTable());

        Server server = new Server("localhost", 8025, "/endpoints", null,
                ChatEndpoint.class);
        server.start();

        // BasicConfigurator.configure();
    }
}