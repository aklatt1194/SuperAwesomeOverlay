package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;
import static spark.Spark.get;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;

public class KnownNodesEndpoint {
    private GeolocateDatabaseProvider db;
    private OverlayRoutingModel model;

    public KnownNodesEndpoint(GeolocateDatabaseProvider db, OverlayRoutingModel model) {
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

        for (InetAddress addr : model.getKnownNodes()) {
            result.add(db.lookupNode(addr));
        }

        return result;
    }
}