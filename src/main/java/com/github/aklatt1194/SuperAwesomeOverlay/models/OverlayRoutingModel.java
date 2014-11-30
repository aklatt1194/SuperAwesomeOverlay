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

import com.github.aklatt1194.SuperAwesomeOverlay.OverlayRoutingManager.TopologyUpdate;
import com.github.aklatt1194.SuperAwesomeOverlay.network.NetworkInterface;
import com.github.aklatt1194.SuperAwesomeOverlay.utils.IPUtils;

public class OverlayRoutingModel {
    public static final int DEFAULT_METRIC = 1000;

    public List<OverlayRoutingModelListener> listeners;

    // Used for managing the matrix of metrics
    private Queue<TopologyUpdate> pendingUpdates;
    private Queue<InetAddress> nodesToRemove;
    private Queue<InetAddress> nodesToAdd;
    private Map<InetAddress, Integer> nodeToIndex;
    private InetAddress[] indexToNode;
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

        listeners = new ArrayList<>();

        // Initially, we are the only known node
        nodeToIndex = new HashMap<InetAddress, Integer>();
        indexToNode = new InetAddress[] { selfAddress };
        nodeToIndex.put(selfAddress, 0);

        pendingUpdates = new LinkedList<TopologyUpdate>();
        nodesToAdd = new LinkedList<InetAddress>();
        nodesToRemove = new LinkedList<InetAddress>();

        metrics = new double[nodeToIndex.size()][nodeToIndex.size()];

        // Fill metrics with default values to start
        for (int i = 0; i < metrics.length; i++) {
            for (int j = 0; j < metrics.length; j++) {
                metrics[i][j] = DEFAULT_METRIC;
            }
        }
    }

    public synchronized void addListener(OverlayRoutingModelListener listener) {
        listeners.add(listener);
    }

    public synchronized void notifyNodeListeners() {
        for (OverlayRoutingModelListener listener : listeners) {
            listener.nodeChangeCallback();
        }
    }

    /**
     * The lazy update method used for each link state packet
     */
    public synchronized void recordLinkStateInformation(TopologyUpdate update) {
        Map<InetAddress, Double> newValues = update.metrics;
        // Update known nodes let addNode take care of preventing duplicates
        for (InetAddress addr : newValues.keySet()) {
            if (!nodeToIndex.containsKey(addr) && !nodesToAdd.contains(addr)) {
                NetworkInterface.getInstance().connectAndAdd(addr);
            }
        }
        // Add this update to the pending queue
        pendingUpdates.add(update);
    }

    /**
     * Trigger all of the batched link state updates to rebuild the model
     */
    public synchronized void triggerFullUpdate() {
        // Do a more explicit delete (still lazyish as it does not fully take
        // effect
        // until we rebuild the matrix). For the purposes of knownNeighborNodes
        // it does
        // do a complete remove however.
        while (!nodesToRemove.isEmpty()) {
            removeNodeHelper(nodesToRemove.remove());
        }

        // Rebuild the matrix
        rebuildMatrix();

        // Apply all of the new info from the link state packets
        while (!pendingUpdates.isEmpty()) {
            updateMetrics(pendingUpdates.remove());
        }

        // Build the tree
        buildMst();

        // Build the forwarding table
        constructForwardingTable();
    }

    /**
     * Lazy insert a node. (Only call this if you are going to update right
     * after)
     */
    public synchronized void addNode(InetAddress addr) {
        if (!nodeToIndex.containsKey(addr) && !nodesToAdd.contains(addr)) {
            nodesToAdd.add(addr);
            notifyNodeListeners();
        }
    }

    /**
     * Lazy remove a node. (Only call this if you are going to update right
     * after).
     */
    public synchronized void deleteNode(InetAddress addr) {
        if (!nodesToAdd.contains(addr) && nodeToIndex.containsKey(addr)
                && !nodesToRemove.contains(addr)) {
            nodesToRemove.add(addr);
            notifyNodeListeners();
        }
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
        List<InetAddress> result = new ArrayList<>(Arrays.asList(indexToNode));
        result.addAll(nodesToAdd);

        return result;
    }

    /**
     * Known Neighbors getter. Returns an array of all the nodes except self.
     */
    public synchronized List<InetAddress> getKnownNeighbors() {
        List<InetAddress> result = new ArrayList<>();

        for (InetAddress addr : indexToNode) {
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
     * Helper method called by triggerFullUpdate to delete nodes (still lazyish)
     */
    private void removeNodeHelper(InetAddress addr) {
        if (nodeToIndex.containsKey(addr)) {
            indexToNode[nodeToIndex.get(addr)] = null;
            nodeToIndex.remove(addr);
        }
    }

    /**
     * Update values in the table without creating a new table (all nodes must
     * be in the matrix already)
     */
    private void updateMetrics(TopologyUpdate update) {
        InetAddress src = update.src;
        Map<InetAddress, Double> values = update.metrics;

        int row = nodeToIndex.get(src);

        // Update the metrics
        for (int i = 0; i < metrics.length; i++) {
            if (!values.containsKey(indexToNode[i]))
                insertMetricTable(row, i, DEFAULT_METRIC);
            else
                insertMetricTable(row, i, values.get(indexToNode[i]));
        }
    }

    /**
     * If necessary, rebuild the matrix (i.e. do a batch update for the lazy
     * adds and deletes).
     */
    private void rebuildMatrix() {
        // If we don't need to rebuild it, then just return
        if (nodesToAdd.isEmpty() && nodeToIndex.size() == indexToNode.length)
            return;

        // Figure out the new number of nodes
        int newSize = 0;
        for (int i = 0; i < indexToNode.length; i++)
            newSize += (indexToNode[i] != null) ? 1 : 0;
        newSize += nodesToAdd.size();

        // Create the new metrics matrix
        double[][] newMetrics = new double[newSize][newSize];

        // Populate the new matrix with the pertinent old metrics
        for (int i = 0; i < newSize; i++) {
            for (int j = 0; j < newSize; j++) {
                if (j >= indexToNode.length || i >= indexToNode.length
                        || indexToNode[j] == null || indexToNode[i] == null) {
                    newMetrics[i][j] = DEFAULT_METRIC;
                } else {
                    newMetrics[i][j] = metrics[i][j];
                }
            }
        }

        // Create the new known nodes array and map
        InetAddress[] newIndexToNode = new InetAddress[newSize];
        Map<InetAddress, Integer> newNodeToIndex = new HashMap<InetAddress, Integer>();

        // Populate the new known nodes array and map
        for (int i = 0; i < newSize; i++) {
            if (i >= indexToNode.length || indexToNode[i] == null)
                newIndexToNode[i] = nodesToAdd.remove();
            else
                newIndexToNode[i] = indexToNode[i];

            newNodeToIndex.put(newIndexToNode[i], i);
        }

        metrics = newMetrics;
        indexToNode = newIndexToNode;
        nodeToIndex = newNodeToIndex;
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
                if (!nodesInTree.containsKey(indexToNode[i])
                        && metrics[index][i] > 0)
                    edges.add(new Edge(node, new TreeNode(indexToNode[i]),
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

        for (int i = 0; i < indexToNode.length; i++) {
            for (TreeNode nodeInterface : root.children) {
                // If this interface has a path to reach the node, update the
                // table
                if (hasPathToNode(nodeInterface, indexToNode[i])) {
                    fTable.put(indexToNode[i], nodeInterface.address);
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
        if (IPUtils.compareIPs(indexToNode[col], indexToNode[row]) < 0)
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
