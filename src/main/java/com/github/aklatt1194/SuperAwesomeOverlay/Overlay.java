package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.server.Server;

import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.network.NetworkInterface;
import com.github.aklatt1194.SuperAwesomeOverlay.views.ChatEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.KnownNodesEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.MetricsEndpoints;
import com.github.aklatt1194.SuperAwesomeOverlay.views.NetworkTopologyEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.WebRoutes;

public class Overlay {
    public static void main(String[] args) throws DeploymentException {
        MetricsDatabaseManager metricsdb = new MetricsDatabaseProvider("Metrics");
        GeolocateDatabaseProvider geodb = new GeolocateDatabaseProvider(); 
        
        // create routing table and initialize the network interface
        OverlayRoutingModel overlayRoutingModel = new OverlayRoutingModel();
        
        try {
            NetworkInterface.getInstance().initialize(overlayRoutingModel);
        } catch (IOException e) {
            System.err.println("Unable to initialize network interface");
            System.exit(1);
        }
        
        OverlayRoutingManager overlayRoutingManager = new OverlayRoutingManager(overlayRoutingModel, metricsdb);

        // web routes and endpoints
        new WebRoutes();
        new KnownNodesEndpoint(geodb, overlayRoutingModel);
        new NetworkTopologyEndpoint(geodb, overlayRoutingModel);
        new MetricsEndpoints(metricsdb, geodb, overlayRoutingModel);

        // websockets
        Server server = new Server("localhost", 8025, "/endpoints", null,
                ChatEndpoint.class);
        server.start();

        // uncomment for extended logging
        // BasicConfigurator.configure();
        
        //new PingTester(routingTable, metricsdb); TODO: this needs some fixin
    }
}