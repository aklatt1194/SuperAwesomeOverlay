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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.aklatt1194.SuperAwesomeOverlay.models.RoutingTable;
import com.github.aklatt1194.SuperAwesomeOverlay.utils.ExternalIP;

public class NetworkInterface implements Runnable {
    private static final int LINK_PORT = 3333;

    private static NetworkInterface instance = null;

    private RoutingTable routingTable;
    private Map<InetAddress, SocketChannel> tcpLinkTable;
    private Map<Integer, SimpleSocket> portMap;

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ByteBuffer readBuffer;
    private ByteBuffer pendingBuffer;
    private BlockingQueue<ServerDataEvent> readPackets;
    private BlockingQueue<ChangeRequest> pendingRequests;
    private Map<SocketChannel, BlockingQueue<ByteBuffer>> pendingWrites;

    public static NetworkInterface getInstance() {
        if (instance == null)
            instance = new NetworkInterface();

        return instance;
    }

    private NetworkInterface() {
    }

    public void initialize(RoutingTable routingTable) throws IOException {
        this.routingTable = routingTable;
        tcpLinkTable = new HashMap<>();
        portMap = new HashMap<>();

        readBuffer = ByteBuffer.allocateDirect(8192);
        pendingBuffer = ByteBuffer.allocate(8192);
        readPackets = new LinkedBlockingQueue<>();
        pendingRequests = new LinkedBlockingQueue<>();
        pendingWrites = new HashMap<>();

        // create the serverChannel and selector
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        selector = SelectorProvider.provider().openSelector();
        InetSocketAddress isa = new InetSocketAddress(LINK_PORT);

        serverChannel.socket().bind(isa);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // start the main thread
        Thread thread = new Thread(this);
        thread.start();

        // start up the packet router
        thread = new Thread(new PacketRouter());
        thread.start();

        // We need to do something clever here. All the nodes should be up
        // before we try to connect.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }

        // figure out the external facing ip address, it is not the same as
        // the internal AWS one
        InetAddress serverAddr = ExternalIP.getExternalAddress();

        for (InetAddress node : routingTable.getKnownNodes()) {
            if (node.getHostAddress().compareTo(serverAddr.getHostAddress()) < 0) {
                // for each node with hash < than this node, establish a
                // connection and stick it in the table
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                boolean res = socketChannel.connect(new InetSocketAddress(node,
                        LINK_PORT));

                if (res) {
                    socketChannel.register(selector, SelectionKey.OP_READ);
                    synchronized (tcpLinkTable) {
                        tcpLinkTable.put(node, socketChannel);
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (!pendingRequests.isEmpty()) {
                    ChangeRequest request = pendingRequests.take();
                    SelectionKey key = request.socket.keyFor(selector);
                    key.interestOps(request.ops);
                }

                // wait for an event
                this.selector.select();

                Iterator<SelectionKey> selectedKeys = this.selector
                        .selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid())
                        continue;

                    if (key.isAcceptable()) {
                        this.accept(key);
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
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
                .channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        // Socket socket = socketChannel.socket(); // add something to table?
        socketChannel.configureBlocking(false);

        // map the remote address to this socket
        InetAddress addr = socketChannel.socket().getInetAddress();
        synchronized (tcpLinkTable) {
            tcpLinkTable.put(addr, socketChannel);
        }

        // set selector to notify when data is to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // clear out the read buffer then do the read
        this.readBuffer.clear();
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            // remove the key and close the socket channel
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            // Remote closed socket cleanly
            key.cancel();
            socketChannel.close();
            return;
        }

        ByteBuffer readBytes = ByteBuffer.allocate(pendingBuffer.remaining()
                + readBuffer.remaining());
        if (pendingBuffer.remaining() > 0) {
            // we had read a partial packet during the last read, stick it on the front
            readBytes.put(pendingBuffer);
            pendingBuffer.clear();
        }
        readBytes.put(readBuffer);

        // stick the packet on the readPackets queue for the PacketHandler to
        // deal with
        while (true) {
            SimpleDatagramPacket packet = SimpleDatagramPacket
                    .createFromBuffer(readBytes, socketChannel.socket()
                            .getInetAddress(), socketChannel.socket()
                            .getLocalAddress());

            if (packet == null)
                // there wasn't a full packets worth of bytes left
                break;

            while (true) {
                try {
                    readPackets.put(new ServerDataEvent(socketChannel, packet));
                    break;
                } catch (InterruptedException e) {
                    continue;
                }
            }

            if (readBuffer.remaining() > 0)
                // if there are any bytes left, stick them on the pending
                // buffer. we will append them to the front of the read buffer
                // the next time this method is called
                pendingBuffer.put(readBytes);
        }
    }

    // Queues up a packet so that it can be sent by the selector
    private void send(SimpleDatagramPacket packet) {
        // place a pending request on the queue for this socketchannel to be
        // changed to write
        SocketChannel socket = tcpLinkTable.get(packet.getDestination());
        
        while (true) {
            try {
                pendingRequests.put(new ChangeRequest(socket,
                        SelectionKey.OP_WRITE));
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }

        // place the data onto the pending writes queue for the proper socket
        synchronized (pendingWrites) {
            BlockingQueue<ByteBuffer> queue = pendingWrites.get(socket);
            if (queue == null) {
                queue = new LinkedBlockingQueue<ByteBuffer>();
                pendingWrites.put(socket, queue);
            }

            while (true) {
                try {
                    queue.put(packet.getRawPacket());
                    break;
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }

        // Wakeup the selector thread
        selector.wakeup();
    }

    // pull any pending writes of a socket's queue and write them to the socket
    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (pendingWrites) {
            BlockingQueue<ByteBuffer> queue = pendingWrites.get(socketChannel);

            while (!queue.isEmpty()) {
                ByteBuffer buf = queue.peek();

                socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    // the socket buffer is full
                    break;
                }
                queue.remove();
            }

            // we are done, set the key back to read
            if (queue.isEmpty())
                key.interestOps(SelectionKey.OP_READ);
        }
    }

    // bind a SimpleSocket to a specific port
    protected void bindSocket(SimpleSocket simpleSocket, int port)
            throws SocketException {

        synchronized (portMap) {
            if (portMap.get(port) != null)
                throw new SocketException();

            portMap.put(port, simpleSocket);
        }
    }

    // close a SimpleSocket
    public void closeSocket(SimpleSocket simpleSocket) {
        synchronized (portMap) {
            portMap.remove(simpleSocket);
        }
    }

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

                System.out.println("src " + dataEvent.packet.getSource().toString());
                //send(dataEvent.socket, dataEvent.packet);
            }
        }
    }

    // helper class with the fields needed to queue up a key change request
    private class ChangeRequest {
        private SocketChannel socket;
        private int ops;

        private ChangeRequest(SocketChannel socket, int ops) {
            this.socket = socket;
            this.ops = ops;
        }
    }

    // helper class with the fields needed to queue up a server read event
    @SuppressWarnings("unused")
    private class ServerDataEvent {
        private SocketChannel socket;
        private SimpleDatagramPacket packet;

        private ServerDataEvent(SocketChannel socket,
                SimpleDatagramPacket packet) {
            this.socket = socket;
            this.packet = packet;
        }
    }

}
