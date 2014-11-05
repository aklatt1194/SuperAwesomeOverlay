package com.github.aklatt1194.SuperAwesomeOverlay;

import java.util.Date;
import java.util.Map;
import java.util.Random;
import com.github.aklatt1194.SuperAwesomeOverlay.models.*;


public class TestIntegrateSqlite {
    public static void main(String[] args) {
        Random rand = new Random();
        String node1 = "Node1";
        String node2 = "Node2";
        
        DatabaseManager dbManager = new DatabaseProvider();
        dbManager.addLatencyData(node1, System.currentTimeMillis(), rand.nextDouble() * 100);
        dbManager.addLatencyData(node2, System.currentTimeMillis(), rand.nextDouble() * 100);
        
        Map<Long, Double> data = dbManager.getLatencyData(node2, 0, System.currentTimeMillis());
        
        for (Long l : data.keySet()) {
            System.out.println("Time: " + new Date(l).toGMTString() + " , Latency: " + data.get(l));
        }
    }
}
