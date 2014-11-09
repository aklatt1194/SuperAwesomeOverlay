package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static spark.Spark.get;

import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;

public class RoutingTableEndpoint {    
    public RoutingTableEndpoint(RoutingTable model) {
        get("/endpoints/known_nodes", (req, res) -> model.getKnownNodes(), json());
    }
}