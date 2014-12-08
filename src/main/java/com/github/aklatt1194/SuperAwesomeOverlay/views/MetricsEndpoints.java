package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;
import static spark.Spark.get;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider.GeoIPEntry;

public class MetricsEndpoints {
    private MetricsDatabaseManager metricsdb;
    private GeolocateDatabaseProvider geodb;
    private OverlayRoutingModel model;

    public MetricsEndpoints(MetricsDatabaseManager metricsdb,
            GeolocateDatabaseProvider geodb, OverlayRoutingModel model) {
        this.metricsdb = metricsdb;
        this.geodb = geodb;
        this.model = model;

        get("/endpoints/latency/:start/:end/:bucket_size",
                (req, res) -> {
                    res.type("application/json");
                    return lookupLatencies(Long.parseLong(req.params("start")),
                            Long.parseLong(req.params("end")),
                            Long.parseLong(req.params("bucket_size")));
                }, json());
        
        get("/endpoints/throughput/:start/:end/:bucket_size",
                (req, res) -> {
                    res.type("application/json");
                    return lookupThroughput(Long.parseLong(req.params("start")),
                            Long.parseLong(req.params("end")));
                }, json());
    }

    // Get all latencies during the specified time series.
    private List<NodeMetrics> lookupLatencies(long startTime, long endTime,
            long bucketSize) {
        List<NodeMetrics> response = new ArrayList<>();

        for (InetAddress node : model.getKnownNeighbors()) {
            GeoIPEntry geoEntry = geodb.lookupNode(node);
            String nodeLocation = "";
            
            nodeLocation += (geoEntry.city_name != null) ? geoEntry.city_name + ", " : "";
            nodeLocation += (geoEntry.region_name != null) ? geoEntry.region_name + ", " : "";
            nodeLocation += geoEntry.country;
            
            NodeMetrics nodeMetrics = new NodeMetrics(nodeLocation);
            Map<Long, Double> latencies = metricsdb.getLatencyData(
                    node.getHostAddress(), startTime, endTime, bucketSize);

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
    
    // Get all throughput measurements during the specified time series.
    private List<NodeMetrics> lookupThroughput(long startTime, long endTime) {
        List<NodeMetrics> response = new ArrayList<>();

        for (InetAddress node : model.getKnownNeighbors()) {
            GeoIPEntry geoEntry = geodb.lookupNode(node);
            String nodeLocation = "";
            
            nodeLocation += (geoEntry.city_name != null) ? geoEntry.city_name + ", " : "";
            nodeLocation += (geoEntry.region_name != null) ? geoEntry.region_name + ", " : "";
            nodeLocation += geoEntry.country;
            
            NodeMetrics nodeMetrics = new NodeMetrics(nodeLocation);
            Map<Long, Double> throughput = metricsdb.getThroughputData(
                    node.getHostAddress(), startTime, endTime);

            for (Entry<Long, Double> entry : throughput.entrySet()) {
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
