package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static com.github.aklatt1194.SuperAwesomeOverlay.utils.JsonUtil.json;
import static spark.Spark.get;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;

public class RoutingTableEndpoint {
    private Connection c;
    private RoutingTable model;
    private Map<String, String> countryCodeTable;

    public RoutingTableEndpoint(RoutingTable model) {
        this.model = model;

        // connect to the GEOIP db
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager
                    .getConnection("jdbc:sqlite:src/main/resources/geoipdb/ipdb.sqlite");
        } catch (Exception e) {
            System.err.println("Unable to open GEOIP database");
            System.exit(1);
        }

        // Save all countries in a table since it will be faster than a JOIN
        countryCodeTable = getCountryCodeTable();

        // set known nodes JSON endpoint
        get("/endpoints/known_nodes", (req, res) -> {
            res.type("application/json");
            return lookupKnownNodes();
        }, json());
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

    private List<Node> lookupKnownNodes() {
        List<Node> result = new ArrayList<>();
        String[] nodes = model.getKnownNodes();

        for (String hostname : nodes) {
            InetAddress addr;
            try {
                addr = InetAddress.getByName(hostname);
            } catch (UnknownHostException e) {
                continue;
            }

            // convert four bytes of ip address to an int
            byte[] byteAddr = addr.getAddress();
            int intAddr = (byteAddr[0] & 0xFF) << 24
                    | (byteAddr[1] & 0xFF) << 16 | (byteAddr[2] & 0xFF) << 8
                    | (byteAddr[3] & 0xFF);

            Node node = new Node();
            node.hostname = hostname;
            node.ip = addr.getHostAddress();

            String query = "SELECT x.country_code, region_name, city_name, latitude, longitude "
                    + "FROM city_location x, region_names y "
                    + "WHERE loc_id = (SELECT loc_id FROM city_blocks WHERE ip_start <= " + Integer.toUnsignedLong(intAddr) + " ORDER BY ip_start DESC LIMIT 1)"
                    + "AND x.region_code = y.region_code AND x.country_code = y.country_code";

            try {
                Statement stmt = c.createStatement();
                ResultSet rs = stmt.executeQuery(query);

                if (rs.next()) {
                    // there was a result
                    node.country = countryCodeTable.get(rs
                            .getString("country_code"));
                    node.region_name = rs.getString("region_name").equals("") ? null : rs.getString("region_name");
                    node.city_name = rs.getString("city_name").equals("") ? null : rs.getString("city_name");
                    node.lat = rs.getDouble("latitude");
                    node.lon = rs.getDouble("longitude");
                }
            } catch (SQLException e) {
                System.err.println("Unable to query GEOIP database");
                System.exit(1);
            } finally {
                result.add(node);
            }
        }

        return result;
    }

    @SuppressWarnings("unused")
    private static class Node {
        private String hostname;
        private String ip;
        private String country;
        private String region_name;
        private String city_name;
        private double lat;
        private double lon;
    }
}