package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.util.Map;

public interface DatabaseManager {

    /**
     * Add the given latency value for the connection to the given node at the
     * given time to the db
     * 
     * @param nodeName
     *            The name of the node we are connected to
     * @param time
     *            The time the data was collected
     * @param value
     *            The data
     */
    public void addLatencyData(String nodeName, long time, double value);

    /**
     * Add the given throughput value for the connection to the given node at
     * the given time to the db
     * 
     * @param nodeName
     *            The name of the node we are connected to
     * @param time
     *            The time the data was collected
     * @param value
     *            The data
     */
    public void addThroughputData(String nodeName, long time, double value);

    /**
     * Get all latency data for the connection to the given node between
     * startTime and endTime
     * 
     * @param node
     *            The node we are connected to
     * @param startTime
     *            The beginning of the interval we are interested in (inclusive)
     *            in UNIX ms since the epoch format.
     * @param endTime
     *            The end of the interval we are interested in (exclusive) in
     *            UNIX ms since the epoch format.
     * @return A map from time to latency for the interval.
     */
    public Map<Long, Double> getLatencyData(String node, long startTime,
            long endTime);

    /**
     * Get all throughput data for the connection to the given node between
     * startTime and endTime
     * 
     * @param node
     *            The node we are connected to
     * @param startTime
     *            The beginning of the interval we are interested in (inclusive)
     *            in UNIX ms since the epoch format.
     * @param endTime
     *            The end of the interval we are interested in (exclusive) in
     *            UNIX ms since the epoch format.
     * @return A map from time to throughput for the interval.
     */
    public Map<Long, Double> getThroughputData(String node, long startTime,
            long endTime);
}
