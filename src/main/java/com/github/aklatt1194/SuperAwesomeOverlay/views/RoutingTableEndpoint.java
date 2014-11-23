package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;
import static spark.Spark.get;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;

public class RoutingTableEndpoint {
    private GeolocateDatabaseProvider db;
    private RoutingTable model;

    public RoutingTableEndpoint(GeolocateDatabaseProvider db, RoutingTable model) {
        this.model = model;
        this.db = db;

        // set known nodes JSON endpoint
        get("/endpoints/known_nodes", (req, res) -> {
            res.type("application/json");
            return lookupKnownNodes();
        }, json());
    }

    private List<GeolocateDatabaseProvider.GeoIPEntry> lookupKnownNodes() {
        List<GeolocateDatabaseProvider.GeoIPEntry> result = new ArrayList<>();
        List<InetAddress> nodes = model.getKnownNeigborAddresses();
        nodes.add(model.getSelfAddress());

        for (InetAddress addr : nodes) {
            result.add(db.lookupNode(addr));
        }

        return result;
    }
}