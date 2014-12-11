package com.github.aklatt1194.SuperAwesomeOverlay.views;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider;
import com.github.aklatt1194.SuperAwesomeOverlay.models.GeolocateDatabaseProvider.GeoIPEntry;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel.TreeNode;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModelListener;
import com.google.gson.Gson;

@ServerEndpoint(value = "/topology")
public class NetworkTopologyEndpoint {
    private static GeolocateDatabaseProvider db;
    private static OverlayRoutingModel model;
    private static List<Session> sessions;

    public static void init(GeolocateDatabaseProvider db, OverlayRoutingModel model) {
        NetworkTopologyEndpoint.db = db;
        NetworkTopologyEndpoint.model = model;

        model.addListener(new OverlayRoutingModelListener() {
            @Override
            public void nodeAddCallback(InetAddress addr) {
            }

            @Override
            public void nodeDeleteCallback(InetAddress addr) {
            }

            @Override
            public void topologyChangeCallback() {
                sendTopology(sessions);
            }
        });

        sessions = new ArrayList<Session>();
    }

    @OnOpen
    public void open(Session session) {
        sessions.add(session);
        sendTopology(Arrays.asList(session));
    }

    @OnClose
    public void closedConnection(Session session) {
        sessions.remove(session);
    }

    public static void sendTopology(List<Session> receivers) {
        TreeNode root = model.getMST();

        if (root != null) {
            String result = new Gson().toJson(buildSpanningTree(root));

            for (Session session : receivers) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }

                try {
                    session.getBasicRemote().sendText(result);
                } catch (IOException e) {
                    System.err.println("Failed to deliver topology to map view");
                    e.printStackTrace();
                }
            }
        }
    }

    private static ResultNode buildSpanningTree(TreeNode root) {
        ResultNode node = new ResultNode();
        GeoIPEntry geoEntry = db.lookupNode(root.address);

        node.hostname = root.address.getHostName();
        node.lat = geoEntry.lat;
        node.lon = geoEntry.lon;
        node.ip = geoEntry.ip;

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
        private String ip;
    }
}
