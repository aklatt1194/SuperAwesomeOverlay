package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import com.github.aklatt1194.SuperAwesomeOverlay.utils.IPUtils;


public class OverlayRoutingModel {
    private RoutingTable rTbl;
    
    // Used for managing the matrix of metrics
    private Queue<InetAddress> nodesToAdd;
    private Map<InetAddress, Integer> nodeToIndex;
    private InetAddress[] knownNodes;
    private double[][] metrics;
    
    // Models
    private TreeNode root;
    private Map<InetAddress, InetAddress> fTable;
    
    public OverlayRoutingModel(RoutingTable rTbl) {
        this.rTbl = rTbl;
        
        // Get the neighbors and use them to populate the table initially
        List<InetAddress> neighbors = rTbl.getKnownNeigborAddresses();
        
        // Populate the known nodes
        nodeToIndex = new HashMap<InetAddress, Integer>();
        knownNodes = new InetAddress[neighbors.size() + 1];
        knownNodes[0] = rTbl.getSelfAddress(); // Put ourselves in
        nodeToIndex.put(knownNodes[0], 0); // Put ourselves in
        
        // Put our neighbors in
        for (int i = 0; i < neighbors.size(); i++) {
            nodeToIndex.put(neighbors.get(i), i + 1);
            knownNodes[i + 1] = neighbors.get(i);
        }
        
        nodesToAdd = new LinkedList<InetAddress>();
        
        metrics = new double[nodeToIndex.size()][nodeToIndex.size()];
        
        // Fill metrics with non-existent links to start
        for (int i = 0; i < metrics.length; i++) {
            for (int j = 0; j < metrics.length; j++) {
                metrics[i][j] = -1.;
            }
        }
    }
    
    
    /**
     * Lazy delete a node
     */
    public void removeNode(InetAddress addr) {
        // lazy delete
        if (nodeToIndex.containsKey(addr)) {
            knownNodes[nodeToIndex.get(addr)] = null;
            nodeToIndex.remove(addr);
        }
}
    
    /**
     * Update the model based on new information from the given src node
     */
    public void updateTopology(InetAddress src, Map<InetAddress, Double> newValues) {
        // Put a self loop of length -1 in for src
        newValues.put(src, -1.);
        
        // Update known nodes
        for (InetAddress addr : newValues.keySet()) {
            if (!nodeToIndex.containsKey(addr)) {
                nodesToAdd.add(addr);
            }
        }
        
        // If we need to, add nodes or remove nodes
        if (!nodesToAdd.isEmpty() || nodeToIndex.size() != knownNodes.length) {            
            rebuildMatrix();
        }
        
        // Update the values in the table
        updateMetrics(src, newValues);
        
        // Rebuild the MST and the forwarding table
        updateModel(); // TODO this seems inefficient to rebuild all of this
                       // every time that we receive a topology update. idk.
    }
    
    /**
     * Call this when a low layer connection to a given dest is closed. This
     * will mark the connection as non-existent in the metric table.
     */
    public void closeConnection(InetAddress destAddr) {
        int src = nodeToIndex.get(rTbl.getSelfAddress());
        int dest = nodeToIndex.get(destAddr);
        
        // Do it manually since we want to trust ourselves, not necessarily the
        // node with the lower ip address
        metrics[src][dest] = -1.;
        metrics[dest][src] = -1.;
    }
    
    /**
     * Rebuild the matrix (i.e. do a batch update for the lazy adds and deletes)
     */
    private void rebuildMatrix() {
        // Figure out the new number of nodes
        int newSize = 0;
        for (int i = 0; i < knownNodes.length; i++)
            newSize += (knownNodes[i] != null) ? 1 : 0;
        newSize += nodesToAdd.size();
        
        // Create the new metrics matrix
        double[][] newMetrics = new double[newSize][newSize];
        
        // Populate the new matrix with the pertinent old metrics
        for (int i = 0; i < newSize; i++) {
            for (int j = 0; j < newSize; j++) {
                if (j >= knownNodes.length || i >= knownNodes.length || knownNodes[j] == null 
                        || knownNodes[i] == null) {
                    newMetrics[i][j] = -1.;
                } else {
                    newMetrics[i][j] = metrics[i][j];
                }   
            }
        }
        
        // Create the new known nodes array and map
        InetAddress[] newKnownNodes = new InetAddress[newSize];
        Map<InetAddress, Integer> newNodeToIndex = new HashMap<InetAddress, Integer>();
        
        // Populate the new known nodes array and map
        for (int i = 0; i < newSize; i++) {
            if (i >= knownNodes.length || knownNodes[i] == null)
                newKnownNodes[i] = nodesToAdd.remove();
            else 
                newKnownNodes[i] = knownNodes[i];
            
            newNodeToIndex.put(newKnownNodes[i], i);
        }
        
        metrics = newMetrics;
        knownNodes = newKnownNodes;
        nodeToIndex = newNodeToIndex;
    }
    
    /**
     * Update values in the table without creating a new table
     */
    private void updateMetrics(InetAddress src, Map<InetAddress, Double> values) {
        int row = nodeToIndex.get(src);
        
        // Update the metrics
        for (int i = 0; i < metrics.length; i++) {
            if (!values.containsKey(knownNodes[i]))
                insertMetricTable(row, i, -1.);
            else
                insertMetricTable(row, i, values.get(knownNodes[i]));
        }
    }
    
    /**
     * Insert a value into the metrics table at the given location
     */
    private void insertMetricTable(int row, int col, double value) {
        // Since these values are directed, but we want an undirected graph,
        // we need to be careful about how we update things. Whenever we do an
        // update, we need to update 2 entries (A->B and B->A). We pick which value
        // to use, by trusting the node with the smaller ip address.
        if (IPUtils.compareIPs(knownNodes[col], knownNodes[row]) < 0)
            value = metrics[col][row];
        
        metrics[row][col] = value;
        metrics[col][row] = value;
    }
    
    /**
     *  Update the model (i.e. the tree and the forwarding table) based on the current
     *  graph (metric matrix).
     */
    private void updateModel() {
        buildMst();
        constructForwardingTable();
    }
    
    /**
     * Build the tree with prim's algorithm
     */
    private void buildMst() {
        Map<InetAddress, TreeNode> nodesInTree = new HashMap<InetAddress, TreeNode>();
        Queue<Edge> edges = new PriorityQueue<Edge>();
        
        // Initialize with ourself
        InetAddress addr = rTbl.getSelfAddress();
        TreeNode node = new TreeNode(addr);
        edges.add(new Edge(node, node, 1.));
        
        // While there are more edges in the queue, examine them one at a time
        do { 
            Edge nextEdge = edges.remove();
            node = nextEdge.dest;
            addr = node.address;
            
            // We already have an edge to this node, continue
            if (nodesInTree.containsKey(addr))
                continue;
            
            // Add the edge to the tree (The if statement is needed for the case
            // of the first node, which has no parent in the nodesInTree).
            if (nodesInTree.containsKey(nextEdge.src.address)) {
                nodesInTree.get(nextEdge.src.address).children.add(node);
            }
            
            // Add the node to the set
            nodesInTree.put(addr, node);
            
            // Add all of this new node's edges to the queue
            int index = nodeToIndex.get(addr);
            for (int i = 0; i < metrics.length; i++) {
                // If this is a valid edge to a new node, add it to the queue
                if (!nodesInTree.containsKey(knownNodes[i]) && metrics[index][i] > 0)
                    edges.add(new Edge(node, new TreeNode(knownNodes[i]), metrics[index][i]));
            }
        
        } while (!edges.isEmpty());
        
        this.root = nodesInTree.get(rTbl.getSelfAddress());
    }
    
    /**
     * Use the MST to build a forwarding table
     */
    private void constructForwardingTable() {
        fTable = new HashMap<InetAddress, InetAddress>();
        
        for (int i = 0; i < knownNodes.length; i++) {
            for (TreeNode nodeInterface : root.children) {
                // If this interface has a path to reach the node, update the table
                if (hasPathToNode(nodeInterface, knownNodes[i])) {
                    fTable.put(knownNodes[i], nodeInterface.address);
                    break;
                }
            }
        }
    }
    
    /**
     * Helper method for constructForwardingTable()
     * 
     * Recursively search the MST to determine if the given src has a downstream 
     * path to dest
     */
    private boolean hasPathToNode(TreeNode src, InetAddress dest) {
        // Base case, is src the node we are looking for
        if (src.address.equals(dest))
            return true;
        
        // Recursively search all children
        for (TreeNode child : src.children) {
            if (hasPathToNode(child, dest))
                return true;
        }
        
        return false;
    }
    
    
    
    /* ------------------------ Static Inner Classes ----------------------- */
    
    
    
    /**
     * Used to represent a tree (a pointer to the root will allow access to
     * the entire tree).
     */
    public static class TreeNode implements Comparable<TreeNode>{
        public InetAddress address;
        public List<TreeNode> children;
        
        public TreeNode(InetAddress address) {
            this.address = address;
            children = new ArrayList<TreeNode>();
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof TreeNode) {
                if (this.address.equals(((TreeNode) o).address))
                    return true;
            }
            return false;
        }

        @Override
        public int compareTo(TreeNode t) {
            return IPUtils.compareIPs(this.address, t.address);
        }
    }
    
    /**
     * A class used to represent an edge between two nodes
     */
    private static class Edge implements Comparable<Edge> {
        public TreeNode src;
        public TreeNode dest;
        public double weight;
        
        public Edge(TreeNode src, TreeNode dest, double weight) {
            this.src = src;
            this.dest = dest;
            this.weight = weight;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof Edge) {
                Edge e = (Edge) o;
                return dest.equals(e.dest) && src.equals(e.src) && weight == e.weight;
            }
            return false;
        }
        
        @Override
        public int compareTo(Edge e) {
            double diff = weight - e.weight;
            
            if (diff > 0.)
                return 1;
            if (diff < 0.)
                return -1;
            
            if (src.compareTo(e.src) != 0)
                return src.compareTo(e.src);
            return dest.compareTo(e.dest);
        }
    }
}