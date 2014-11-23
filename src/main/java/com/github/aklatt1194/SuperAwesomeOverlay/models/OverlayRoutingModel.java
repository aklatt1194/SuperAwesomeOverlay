package com.github.aklatt1194.SuperAwesomeOverlay.models;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import com.github.aklatt1194.SuperAwesomeOverlay.utils.IPUtils;

// TODO we want want to try to batch our rebuilding of the tree and forwarding table
// perhaps after receiving an update from each other node? Not sure if this is really
// important, but we are doing a lot of rebuilding now.

public class OverlayRoutingModel {
    // Used for managing the matrix of metrics
    private Queue<InetAddress> nodesToAdd;
    private Map<InetAddress, Integer> nodeToIndex;
    private InetAddress[] knownNodes;
    private InetAddress selfAddress;
    private double[][] metrics;

    // Models
    private TreeNode root;
    private Map<InetAddress, InetAddress> fTable;

    public OverlayRoutingModel() {
        // figure out external ip
        try {
            selfAddress = IPUtils.getExternalAddress(); // Put ourselves in
        } catch (IOException e) {
            System.err
                    .println("Error: Unable to determine external IP address");
            System.exit(1);
        }

        // Initially, we are the only known node
        nodeToIndex = new HashMap<InetAddress, Integer>();
        knownNodes = new InetAddress[] { selfAddress };
        nodeToIndex.put(selfAddress, 0);

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
     * Lazy insert a node. (Only call this if you are going to update right
     * after)
     */
    public synchronized void addNode(InetAddress addr) {
        if (!nodeToIndex.containsKey(addr) && !nodesToAdd.contains(addr)) {
            nodesToAdd.add(addr);
        }
        // TODO tell the network interface to connect to this node (add to
        // newSocketChannels)
        // This will probably need to be synchronized.
    }

    /**
     * Lazy delete a node. (Only call this if you are going to update right
     * after)
     */
    public synchronized void removeNodeLazy(InetAddress addr) {
        // lazy delete
        if (nodeToIndex.containsKey(addr)) {
            knownNodes[nodeToIndex.get(addr)] = null;
            nodeToIndex.remove(addr);
        }
    }

    /**
     * Update the model based on new information from the given src node
     */
    public synchronized void updateTopology(InetAddress src,
            Map<InetAddress, Double> newValues) {
        // Update known nodes
        for (InetAddress addr : newValues.keySet()) {
            if (!nodeToIndex.containsKey(addr)) {
                addNode(addr);
            }
        }

        // If we need to, add nodes or remove nodes
        rebuildMatrix();

        // Update the values in the table
        updateMetrics(src, newValues);

        // Rebuild the MST and the forwarding table
        updateModel();
    }

    /**
     * Call this when a low layer connection to a given dest is closed. This
     * will mark the connection as non-existent in the metric table.
     */
    // TODO if our network will always be fully connected, we may just want to
    // send out a remove node update if we cannot connect to a node...?
    public void closeConnection(InetAddress destAddr) {
        int src = nodeToIndex.get(selfAddress);
        int dest = nodeToIndex.get(destAddr);

        // Do it manually since we want to trust ourselves, not necessarily the
        // node with the lower ip address
        metrics[src][dest] = -1.;
        metrics[dest][src] = -1.;
    }

    /**
     * Update values in the table without creating a new table (all nodes must
     * be in the matrix already)
     */
    public void updateMetrics(InetAddress src, Map<InetAddress, Double> values) {
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
     * If necessary, rebuild the matrix (i.e. do a batch update for the lazy
     * adds and deletes).
     */
    public synchronized void rebuildMatrix() {
        // If we don't need to rebuild it, then just return
        if (nodesToAdd.isEmpty() && nodeToIndex.size() == knownNodes.length)
            return;

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
                if (j >= knownNodes.length || i >= knownNodes.length
                        || knownNodes[j] == null || knownNodes[i] == null) {
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
     * Update the model (i.e. the tree and the forwarding table) based on the
     * current graph (metric matrix).
     */
    public void updateModel() {
        buildMst();
        constructForwardingTable();
    }

    /**
     * MST getter. Normally a deep copy would be a good idea.
     */
    public synchronized TreeNode getMST() {
        return root;
    }

    /**
     * Known Node getter. Returns an array of all the nodes including any that
     * are pending.
     */
    public synchronized List<InetAddress> getKnownNodes() {
        List<InetAddress> result = new ArrayList<>(Arrays.asList(knownNodes));
        result.addAll(nodesToAdd);

        return result;
    }

    /**
     * Known Neighbors getter. Returns an array of all the nodes except self.
     */
    public synchronized List<InetAddress> getKnownNeighbors() {
        List<InetAddress> result = new ArrayList<>();

        for (InetAddress addr : knownNodes) {
            if (addr != selfAddress) {
                result.add(addr);
            }
        }
        result.addAll(nodesToAdd);

        return result;
    }

    public synchronized InetAddress getSelfAddress() {
        return selfAddress;
    }

    /**
     * Build the tree with prim's algorithm
     */
    private void buildMst() {
        Map<InetAddress, TreeNode> nodesInTree = new HashMap<InetAddress, TreeNode>();
        Queue<Edge> edges = new PriorityQueue<Edge>();

        // Initialize with ourself
        InetAddress addr;
        TreeNode node = new TreeNode(selfAddress);
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
                if (!nodesInTree.containsKey(knownNodes[i])
                        && metrics[index][i] > 0)
                    edges.add(new Edge(node, new TreeNode(knownNodes[i]),
                            metrics[index][i]));
            }

        } while (!edges.isEmpty());

        this.root = nodesInTree.get(selfAddress);
    }

    /**
     * Use the MST to build a forwarding table
     */
    private void constructForwardingTable() {
        fTable = new HashMap<InetAddress, InetAddress>();

        for (int i = 0; i < knownNodes.length; i++) {
            for (TreeNode nodeInterface : root.children) {
                // If this interface has a path to reach the node, update the
                // table
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

    /**
     * Insert a value into the metrics table at the given location
     */
    private void insertMetricTable(int row, int col, double value) {
        // Since these values are directed, but we want an undirected graph,
        // we need to be careful about how we update things. Whenever we do an
        // update, we need to update 2 entries (A->B and B->A). We pick which
        // value
        // to use, by trusting the node with the smaller ip address.
        if (IPUtils.compareIPs(knownNodes[col], knownNodes[row]) < 0)
            value = metrics[col][row];

        metrics[row][col] = value;
        metrics[col][row] = value;
    }

    /* ------------------------ Static Inner Classes ----------------------- */

    /**
     * Used to represent a tree (a pointer to the root will allow access to the
     * entire tree).
     */
    public static class TreeNode implements Comparable<TreeNode> {
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
                return dest.equals(e.dest) && src.equals(e.src)
                        && weight == e.weight;
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
