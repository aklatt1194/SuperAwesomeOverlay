package com.github.aklatt1194.SuperAwesomeOverlay;

import java.util.Date;
import java.util.Map;
import java.util.Random;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import com.github.aklatt1194.SuperAwesomeOverlay.models.*;

public class SqliteTest extends TestCase {
    /**
     * Boiler plate junk
     * 
     * @param testname
     */
    public SqliteTest(String testname) {
        super(testname);
    }

    /**
     * More boiler plate
     * 
     * @return
     */
    public static Test suite() {
        return new TestSuite(SqliteTest.class);
    }

    /**
     * Put some random stuff in the db and then read it back out
     */
    public void testDb() {
        System.out.println("\n\n\nDB TEST RESULTS:\n");

        Random rand = new Random();
        String node1 = "Node1";
        String node2 = "Node2";

        MetricsDatabaseManager dbManager = new MetricsDatabaseProvider();
        dbManager.addThroughputData(node1, System.currentTimeMillis(),
                rand.nextDouble() * 100);
        dbManager.addLatencyData(node2, System.currentTimeMillis(),
                rand.nextDouble() * 100);

        /* Print out throughput data for node 1 */
        System.out.println(node1 + " throughput data:");
        Map<Long, Double> data2 = dbManager.getThroughputData(node1, 0,
                System.currentTimeMillis());

        for (Long l : data2.keySet()) {
            System.out.println("Time: " + new Date(l).toGMTString()
                    + " , Throughput: " + data2.get(l));
        }

        /* Print out latency data for node 2 */
        System.out.println("\n" + node2 + " latency data:");
        Map<Long, Double> data = dbManager.getLatencyData(node2, 0,
                System.currentTimeMillis());

        for (Long l : data.keySet()) {
            System.out.println("Time: " + new Date(l).toGMTString()
                    + " , Latency: " + data.get(l));
        }

        System.out.println("\nEND OF DB TEST\n\n\n");
    }
}
