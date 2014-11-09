package com.github.aklatt1194.SuperAwesomeOverlay;

import org.apache.log4j.BasicConfigurator;

import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import com.github.aklatt1194.SuperAwesomeOverlay.views.RoutingTableEndpoint;
import com.github.aklatt1194.SuperAwesomeOverlay.views.WebRoutes;

public class Overlay {
    public static void main(String[] args) {
        new WebRoutes();
        new RoutingTableEndpoint(new RoutingTable());

        //BasicConfigurator.configure();
    }
}