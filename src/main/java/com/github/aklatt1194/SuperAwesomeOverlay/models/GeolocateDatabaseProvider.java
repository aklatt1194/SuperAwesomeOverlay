package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class GeolocateDatabaseProvider {
    private static final String PATH = "src/main/resources/geoipdb/ipdb.sqlite";

    private Connection c;
    private Map<String, String> countryCodeTable;

    public GeolocateDatabaseProvider() {
        // connect to the GEOIP db
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + PATH);
        } catch (Exception e) {
            System.err.println("Unable to open GEOIP database");
            System.exit(1);
        }

        // Save all countries in a table since it will be faster than a JOIN for
        // each query
        countryCodeTable = getCountryCodeTable();
    }

    private Map<String, String> getCountryCodeTable() {
        Map<String, String> result = new HashMap<>();

        try {
            Statement stmt = c.createStatement();
            ResultSet rs = stmt
                    .executeQuery("SELECT DISTINCT country_code, country_name FROM country_blocks");

            while (rs.next()) {
                result.put(rs.getString("country_code"),
                        rs.getString("country_name"));
            }
        } catch (SQLException e) {
            System.err.println("Unable to query GEOIP database");
            System.exit(1);
        }

        return result;
    }

    public GeoIPEntry lookupNode(InetAddress addr) {
        byte[] byteAddr = addr.getAddress();
        int intAddr = (byteAddr[0] & 0xFF) << 24 | (byteAddr[1] & 0xFF) << 16
                | (byteAddr[2] & 0xFF) << 8 | (byteAddr[3] & 0xFF);

        GeoIPEntry entry = new GeoIPEntry();
        entry.hostname = addr.getHostName();
        entry.ip = addr.getHostAddress();

        String query = "SELECT x.country_code, region_name, city_name, latitude, longitude "
                + "FROM city_location x, region_names y "
                + "WHERE loc_id = (SELECT loc_id FROM city_blocks WHERE ip_start <= "
                + Integer.toUnsignedLong(intAddr)
                + " ORDER BY ip_start DESC LIMIT 1)"
                + "AND x.region_code = y.region_code AND x.country_code = y.country_code";

        try {
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            if (rs.next()) {
                // there was a result
                entry.country = countryCodeTable.get(rs
                        .getString("country_code"));
                entry.region_name = rs.getString("region_name").equals("") ? null
                        : rs.getString("region_name");
                entry.city_name = rs.getString("city_name").equals("") ? null
                        : rs.getString("city_name");
                entry.lat = rs.getDouble("latitude");
                entry.lon = rs.getDouble("longitude");
            }
        } catch (SQLException e) {
            System.err.println("Unable to query GEOIP database");
            System.exit(1);
        }
        
        return entry;
    }

    public static class GeoIPEntry {
        public String hostname;
        public String ip;
        public String country;
        public String region_name;
        public String city_name;
        public double lat;
        public double lon;
    }
}
