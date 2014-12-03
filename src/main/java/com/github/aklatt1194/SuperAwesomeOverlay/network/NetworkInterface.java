package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;

public class NetworkInterface implements Runnable {
    public static final String[] NODES_BOOTSTRAP = {
            // "c-174-61-223-52.hsd1.wa.comcast.net", // for testing purposes
            "ec2-54-72-49-50.eu-west-1.compute.amazonaws.com",
            "ec2-54-64-177-145.ap-northeast-1.compute.amazonaws.com",
            "ec2-54-172-69-181.compute-1.amazonaws.com" };
    private static final int LINK_PORT = 3333;

    private static NetworkInterface instance = null;

    private OverlayRoutingModel model;
    private Map<InetAddress, SocketChannel> tcpLinkTable;
    private Map<Integer, SimpleSocket> portMap;

    private Selector selector;
    private ByteBuffer readBuffer;
    private BlockingQueue<ServerDataEvent> readPackets;
    private BlockingQueue<ChangeRequest> pendingRequests;
    private Map<InetAddress, BlockingQueue<ByteBuffer>> pendingWrites;
    private Map<SocketChannel, ByteBuffer> pendingReads;

    private BlockingQueue<InetAddress> potentialNodes;
    private BlockingQueue<InetAddress> nodesToRemove;

    public static NetworkInterface getInstance() {
        if (instance == null)
            instance = new NetworkInterface();
        return instance;
    }

    private NetworkInterface() {
    }

    public void initialize(OverlayRoutingModel model) throws IOException {
        this.model = model;
        tcpLinkTable = new ConcurrentHashMap<>();
        portMap = new ConcurrentHashMap<>();

        // various buffers to keep track of read/writes
        readBuffer = ByteBuffer.allocateDirect(8192);
        readPackets = new LinkedBlockingQueue<>();
        pendingRequests = new LinkedBlockingQueue<>();
        pendingWrites = new ConcurrentHashMap<>();
        pendingReads = new ConcurrentHashMap<>();

        // the main selector that we will use
        selector = SelectorProvider.provider().openSelector();

        // any potentially new nodes that the interface should connect to
        potentialNodes = new LinkedBlockingQueue<>();
        nodesToRemove = new LinkedBlockingQueue<>();

        // create the serverChannel and register it with the selector
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(LINK_PORT));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // start the main thread
        Thread thread = new Thread(this);
        thread.start();

        // start up the packet router
        thread = new Thread(new PacketRouter());
        thread.start();

        // try to connect to any of the bootstrap nodes (hopefully at least one)
        for (String node : NODES_BOOTSTRAP) {
            connectAndAdd(InetAddress.getByName(node));
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (!pendingRequests.isEmpty()) {
                    ChangeRequest request = pendingRequests.take();
                    SelectionKey key = tcpLinkTable.get(request.addr).keyFor(selector);
                    if (key != null && key.isValid()) {
                        key.interestOps(request.ops);
                    }
                }

                // wait for an event
                this.selector.select();

                while (!potentialNodes.isEmpty()) {
                    // we must add any keys to the selector in the same thread
                    // that we call select()
                    InetAddress addr = potentialNodes.poll();
                    SocketChannel socketChannel = SocketChannel.open();
                    socketChannel.configureBlocking(false);

                    boolean result = socketChannel.connect(new InetSocketAddress(addr, LINK_PORT));
                    if (result) {
                        System.err.println("The node is attempting to connect to itself!");
                        socketChannel.close();
                    } else {
                        socketChannel.register(selector, SelectionKey.OP_CONNECT);
                    }
                }

                while (!nodesToRemove.isEmpty()) {
                    InetAddress addr = nodesToRemove.poll();
                    SocketChannel socketChannel = tcpLinkTable.remove(addr);

                    if (socketChannel != null) {
                        // get rid of any buffers that this channel may have had
                        pendingReads.remove(socketChannel);
                        pendingWrites.remove(addr);

                        socketChannel.keyFor(selector).cancel();
                        try {
                            socketChannel.close();
                        } catch (IOException e) {
                        }

                        /** Debug stuff **/
                        if (addr.equals(model.getSelfAddress())) {
                            System.out.println("\n\n\nWhy are we deleting ourselves: run\n\n\n");
                        }
                        model.deleteNode(addr);
                    }
                }

                Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid())
                        continue;

                    if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isConnectable()) {
                        this.connect(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Accept a new connection. Save this socket in the tcpLinkTable and then
    // add it to the selector.
    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        // Socket socket = socketChannel.socket(); // add something to table?
        socketChannel.configureBlocking(false);

        // figure out the remote address
        InetAddress addr = socketChannel.socket().getInetAddress();

        // if a node that we are already connected to is connecting to us, we
        // should remove the old socketchannel and use the new one
        SocketChannel prevChannel = tcpLinkTable.get(addr);
        if (prevChannel != null) {
            prevChannel.keyFor(selector).cancel();
            prevChannel.close();
        }

        // add the new socketChannel to the table
        tcpLinkTable.put(addr, socketChannel);

        // add this newly connected node to model
        model.addNode(addr);

        // set selector to notify when data is to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    // Finish connecting to a remote node
    private void connect(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        try {
            socketChannel.finishConnect();

            InetAddress addr = socketChannel.socket().getInetAddress();

            // Close up our old connection if we had one
            SocketChannel prevChannel = tcpLinkTable.get(addr);
            if (prevChannel != null) {
                prevChannel.keyFor(selector).cancel();
                prevChannel.close();
            }

            tcpLinkTable.put(addr, socketChannel);
            socketChannel.register(selector, SelectionKey.OP_READ);
            model.addNode(addr);
        } catch (IOException e) {
            // connecting to this node didn't work out so well
            socketChannel.keyFor(selector).cancel();
        }
    }

    private void read(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // clear out the read buffer then do the read
        this.readBuffer.clear();
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            return;
        }

        if (numRead == -1) {
            // Remote closed socket cleanly
            pendingReads.remove(socketChannel);
            pendingWrites.remove(socketChannel.socket().getInetAddress());
            try {
                socketChannel.close();
            } catch (IOException e) {
            }
            key.cancel();

            /*
             * TODO I temporarily removed this. I think it may be causing
             * problems, and I am not sure when we need it.
             * 
             * We still remove cancel the key in the line preceding this
             * comment. By commenting out the code below, we keep a reference to
             * the socket channel in our table. Later on, if we try to get the
             * key for that socketchannel and do something with it, we'll have
             * an exception.
             */

            /*
             * We shouldn't connect to ourselves in the first place.
             * 
             * // Debug stuff if (addr.equals(model.getSelfAddress())) {
             * System.out
             * .println("\n\n\nWhy are we deleting ourselves: read\n\n\n"); }
             */
            
            InetAddress addr = socketChannel.socket().getInetAddress();
            model.deleteNode(addr);
            tcpLinkTable.remove(addr);

            return;
        }

        readBuffer.flip();
        ByteBuffer pendingBytes = pendingReads.get(socketChannel);
        ByteBuffer totalBytes;

        if (pendingBytes != null) {
            pendingReads.put(socketChannel, null);
            totalBytes = ByteBuffer.allocate(pendingBytes.remaining() + readBuffer.remaining());
            totalBytes.put(pendingBytes);
            totalBytes.put(readBuffer);
        } else {
            totalBytes = readBuffer;
        }

        totalBytes.rewind();

        // stick the packet on the readPackets queue for the PacketHandler to
        // deal with
        while (true) {
            SimpleDatagramPacket packet = SimpleDatagramPacket.createFromBuffer(totalBytes,
                    socketChannel.socket().getInetAddress(), model.getSelfAddress());

            if (packet == null) {
                if (totalBytes.remaining() > 0) {
                    // if there are any bytes left, stick them on the pending
                    // buffer. we will append them to the front of the read
                    // buffer
                    // the next time this method is called
                    ByteBuffer remaining = ByteBuffer.allocate(totalBytes.remaining());
                    remaining.put(totalBytes);
                    pendingReads.put(socketChannel, remaining);
                }
                break;
            }

            while (true) {
                try {
                    readPackets.put(new ServerDataEvent(socketChannel, packet));
                    break;
                } catch (InterruptedException e) {
                    continue;
                }
            }

        }
    }

    // pull any pending writes of a socket's queue and write them to the socket
    private void write(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        InetAddress dst = socketChannel.socket().getInetAddress();
        BlockingQueue<ByteBuffer> queue = pendingWrites.get(dst);

        while (!queue.isEmpty()) {
            ByteBuffer buf = queue.peek();

            try {
                socketChannel.write(buf);
            } catch (IOException e) {
                // If an exception occurs during a write, the node is probably
                // gone
                return;
            }

            if (buf.remaining() > 0) {
                // the socket buffer is full
                
                // TODO: WTF - If we only write a partial packet to the socket, we need to keep the remaining bytes around to write once there is room.
                break;
            }
            queue.remove();
        }

        // we are done, set the key back to read
        if (queue.isEmpty())
            key.interestOps(SelectionKey.OP_READ);
    }

    // Queues up a packet so that it can be sent by the selector
    protected void send(SimpleDatagramPacket packet) throws IOException {
        // place a pending request on the queue for this destination address

        if (tcpLinkTable.get(packet.getDestination()) == null) {
            // we aren't actually connected to the destination
            // TODO:
            throw new IOException();
        }

        while (true) {
            try {
                pendingRequests.put(new ChangeRequest(packet.getDestination(),
                        SelectionKey.OP_WRITE));
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }

        // place the data onto the pending writes queue for the proper socket
        BlockingQueue<ByteBuffer> queue = pendingWrites.get(packet.getDestination());
        if (queue == null) {
            queue = new LinkedBlockingQueue<ByteBuffer>();
            pendingWrites.put(packet.getDestination(), queue);
        }

        while (true) {
            try {
                queue.put(packet.getRawPacket());
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }

        // Wakeup the selector thread
        selector.wakeup();
    }

    // bind a SimpleSocket to a specific port
    protected void bindSocket(SimpleSocket simpleSocket, int port) throws SocketException {

        if (portMap.get(port) != null)
            throw new SocketException();

        portMap.put(port, simpleSocket);
    }

    // close a SimpleSocket
    protected void closeSocket(SimpleSocket simpleSocket) {
        portMap.remove(simpleSocket);
    }

    /**
     * Try to open a TCP connection to the given address and add the node to the
     * model if we are successful
     * 
     * @param addr The node to attempt to connect to and add
     */
    public void connectAndAdd(InetAddress addr) {
        // The test and add are not necessarily atomic, but I think it should
        // be fine since we are the only thread adding to the queue. (We may
        // still
        // get duplicate connection attempts, but the connect method should take
        // care of this. Also, lets not connect to ourself :/.
        if (!potentialNodes.contains(addr) && !addr.equals(model.getSelfAddress())) {
            potentialNodes.add(addr);
            selector.wakeup();
        }
    }

    /**
     * Try to disconnect (close the TCP connection) to the given node and if
     * successful remove it from the known nodes
     * 
     * @param addr The node to attempt to disconnect from and remove
     */
    public void disconnectFromNode(InetAddress addr) {
        if (tcpLinkTable.containsKey(addr)) {
            // let's check that it is actually in the table before we bother
            // waking the selector up
            nodesToRemove.add(addr);
            selector.wakeup();
        }
    }

    /**
     * A thread that multiplexes incoming packets to the correct ports
     */
    private class PacketRouter implements Runnable {
        @Override
        public void run() {
            ServerDataEvent dataEvent = null;

            while (true) {
                try {
                    dataEvent = readPackets.take();
                } catch (InterruptedException e) {
                    continue;
                }

                if (portMap.get(dataEvent.packet.getDestinationPort()) == null) {
                    // this port isn't open, drop packet
                    continue;
                }

                BlockingQueue<SimpleDatagramPacket> readQueue = portMap.get(dataEvent.packet
                        .getDestinationPort()).readQueue;

                try {
                    readQueue.put(dataEvent.packet);
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }

    // helper class with the fields needed to queue up a key change request
    private class ChangeRequest {
        private InetAddress addr;
        private int ops;

        private ChangeRequest(InetAddress addr, int ops) {
            this.addr = addr;
            this.ops = ops;
        }
    }

    // helper class with the fields needed to queue up a server read event
    @SuppressWarnings("unused")
    private class ServerDataEvent {
        private SocketChannel socket;
        private SimpleDatagramPacket packet;

        private ServerDataEvent(SocketChannel socket, SimpleDatagramPacket packet) {
            this.socket = socket;
            this.packet = packet;
        }
    }

}
