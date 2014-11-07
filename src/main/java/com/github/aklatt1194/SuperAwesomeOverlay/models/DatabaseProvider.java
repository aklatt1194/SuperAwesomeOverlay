package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.sql.*;
import java.util.Map;
import java.util.TreeMap;

public class DatabaseProvider implements DatabaseManager {
    
    private static final String DEFAULT_NAME = "sqlite-test.db";
    private static final String LATENCY_TABLE = "latency";
    private static final String THROUGHPUT_TABLE = "throughput";
    private static final String PATH = "/home/ubuntu/";
    
    private Connection c;
    
    /**
     * Setup a connection and construct a default test database if necessary
     */
    public DatabaseProvider() {
    	this("");
    }
    
    /**
     * Setup the connection and construct the db and tables if necessary
     */
    public DatabaseProvider(String name) {
        String path = name.length() == 0 ? DEFAULT_NAME : PATH + name;
    	try {
            // Open a connection to the DB (creates the DB if it does not exist)
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + path);
            System.out.println("Opened database successfully");
            
            // Create the throughput table if it does not exist
            if (!hasTable(c, THROUGHPUT_TABLE)) {
              createNetworkDataTable(c, THROUGHPUT_TABLE);
            }
            
            // Create the latency table if it does not exist
            if (!hasTable(c, LATENCY_TABLE)) {
              createNetworkDataTable(c, LATENCY_TABLE);
            }   
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
    }
    
    public void addLatencyData(String nodeName, long time, double value) {
        addNetworkData(nodeName, time, value, LATENCY_TABLE);
    }
    
    public void addThroughputData(String nodeName, long time, double value) {
        addNetworkData(nodeName, time, value, THROUGHPUT_TABLE);
    }
    
    public Map<Long, Double> getLatencyData(String node, long startTime, long endTime) {
        return getConnectionData(node, startTime, endTime, LATENCY_TABLE);
    }

    public Map<Long, Double> getThroughputData(String node, long startTime, long endTime) {
        return getConnectionData(node, startTime, endTime, THROUGHPUT_TABLE);
    }
    
    
    /* Private helper methods */
    
    /**
     * Get all of the network data from the given table for the given node that
     * was recorded between startTime and endTime
     * 
     * @param node
     * @param startTime
     * @param endTime
     * @param table
     * @throws IllegalArgumentException If startTime or endTime is invalid
     * @return
     */
    private Map<Long, Double> getConnectionData(String node, long startTime, long endTime, String table) {
        if (startTime < 0 || startTime > endTime)
            throw new IllegalArgumentException();
        
        Map<Long, Double> result = new TreeMap<>();
        
        String select = "SELECT Time, " + table + " FROM " + table + 
                        " WHERE Node='" + node + "' AND Time>=" + startTime +
                        " AND Time<" + endTime + ";";
        
        try {
            // Execute the statement
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(select);
            
            // Populate the map with the result set
            while (rs.next()) {
                long time = rs.getLong("Time");
                double value = rs.getDouble(table);
                result.put(time, value);
            }
            
            return result;
        } catch (SQLException e) {
            System.err.println("Error getting the data from table " + table + " !");
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Add the given information to the given table
     * 
     * @param nodeName The node representing the connection
     * @param time The time
     * @param value The network value
     * @param table The table
     */
    private void addNetworkData(String nodeName, long time, double value, String table) {
        String insert = "INSERT INTO " + table + " (Time, Node, " + table + ") " +
                        "VALUES (" + time + ", '" + nodeName + "', " + value + " );";
        
        try {
            Statement stmt = c.createStatement();
            stmt.executeUpdate(insert);
        } catch (SQLException e) {
            System.err.println("Insert into table: " + table + " failed!");
            e.printStackTrace();
        }
    }
    
    /**
     * Returns true iff the database with the given connection has a table with the
     * given name
     * 
     * @param c The connection
     * @param name The name of the table
     * @return True iff the table with the given name exists
     * @throws SQLException 
     */
    private static boolean hasTable(Connection c, String name) throws SQLException {
        DatabaseMetaData metaData = c.getMetaData();
        ResultSet tables = metaData.getTables(null, null, name, null);
        return tables.next();
    }
    
    /**
     * Create a table for storing network data (e.g. latency/throughput for a
     * connection to a given node at a given time) and add an index to the table
     * on the Time field
     * 
     * @param c The database connection
     * @param name The name of the table to create
     * @throws SQLException
     */
    private static void createNetworkDataTable(Connection c, String name) throws SQLException {
       // Create the table
        String createTable = "CREATE TABLE " + name +
                     "(Time         BIGINT          NOT NULL," +
                     " Node         VARCHAR(100)    NOT NULL," +
                     " " + name + " DOUBLE          NOT NULL)";
        
        Statement stmt = c.createStatement();
        stmt.executeUpdate(createTable);
        stmt.close();
        
        // Add an index to the time
        String createIndex = "CREATE INDEX " + name + "_time_idx ON " + name + " (Time)";
        stmt = c.createStatement();
        stmt.executeUpdate(createIndex);
        
    }
    
}