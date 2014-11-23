package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.server.Server;

import com.github.aklatt1194.SuperAwesomeOverlay.models.DatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import com.github.aklatt1194.SuperAwesomeOverlay.network.NetworkInterface;
import com.github.aklatt1194.SuperAwesomeOverlay.views.ChatEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.MetricsEndpoints;
import com.github.aklatt1194.SuperAwesomeOverlay.views.RoutingTableEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.WebRoutes;

public class Overlay {
    public static void main(String[] args) throws DeploymentException {
        // create routing table and initialize the network interface
        RoutingTable routingTable = new RoutingTable();
        
        MetricsDatabaseManager metricsdb = new DatabaseProvider("Metrics");
        GeolocateDatabaseProvider geodb = new GeolocateDatabaseProvider(); 

        try {
            NetworkInterface.getInstance().initialize(routingTable);
        } catch (IOException e) {
            System.err.println("Unable to initialize network interface");
            System.exit(1);
        }

        // web routes and endpoints
        new WebRoutes();
        new RoutingTableEndpoint(geodb, routingTable);
        new MetricsEndpoints(metricsdb, routingTable);

        // websockets
        Server server = new Server("localhost", 8025, "/endpoints", null,
                ChatEndpoint.class);
        server.start();

        // uncomment for extended logging
        // BasicConfigurator.configure();
        
        new PingTester(routingTable, metricsdb);
    }
}