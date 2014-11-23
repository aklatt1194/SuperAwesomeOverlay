package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;
import static spark.Spark.get;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;

public class MetricsEndpoints {
    private MetricsDatabaseManager db;
    private OverlayRoutingModel model;

    public MetricsEndpoints(MetricsDatabaseManager db, OverlayRoutingModel model) {
        this.db = db;
        this.model = model;

        get("/endpoints/latency/:start/:end",
                (req, res) -> {
                    res.type("application/json");
                    return lookupLatencies(Long.parseLong(req.params("start")),
                            Long.parseLong(req.params("end")));
                }, json());
    }

    // Get all latencies during the specified time series.
    private List<NodeMetrics> lookupLatencies(long startTime, long endTime) {
        List<NodeMetrics> response = new ArrayList<>();

        for (InetAddress node : model.getKnownNeighbors()) {
            NodeMetrics nodeMetrics = new NodeMetrics(node.getHostAddress());
            Map<Long, Double> latencies = db.getLatencyData(nodeMetrics.name,
                    startTime, endTime);

            for (Entry<Long, Double> entry : latencies.entrySet()) {
                List<Object> entryList = new ArrayList<>();
                entryList.add(entry.getKey());
                entryList.add(entry.getValue());
                nodeMetrics.data.add(entryList);
            }

            response.add(nodeMetrics);
        }
        return response;
    }

    class NodeMetrics {
        String name;
        List<List<Object>> data;

        public NodeMetrics(String name) {
            this.name = name;
            data = new ArrayList<>();
        }
    }
}
