package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;
import static spark.Spark.get;

import java.util.ArrayList;
import java.util.List;

import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider.GeoIPEntry;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel.TreeNode;

public class NetworkTopologyEndpoint {
    private GeolocateDatabaseProvider db;

    public NetworkTopologyEndpoint(GeolocateDatabaseProvider db,
            OverlayRoutingModel model) {
        this.db = db;

        // set known nodes JSON endpoint
        get("/endpoints/network_topology", (req, res) -> {
            res.type("application/json");
            return buildSpanningTree(model.getMST());
        }, json());
    }

    private ResultNode buildSpanningTree(TreeNode root) {
        ResultNode node = new ResultNode();
        GeoIPEntry geoEntry = db.lookupNode(root.address);
        
        node.hostname = root.address.getHostName();
        node.lat = geoEntry.lat;
        node.lon = geoEntry.lon;
        
        if (!root.children.isEmpty()) {
            node.children = new ArrayList<>();
        }
        
        for (TreeNode child : root.children) {
            node.children.add(buildSpanningTree(child));
        }
        
        return node;
    }

    @SuppressWarnings("unused")
    private static class ResultNode {
        private List<ResultNode> children;
        private String hostname;
        private double lat;
        private double lon;
    }
}
