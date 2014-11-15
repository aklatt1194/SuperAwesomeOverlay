package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

public class NetworkInterface implements Runnable {
    private static final int LINK_PORT = 3333;

    private static NetworkInterface instance = null;

    private RoutingTable routingTable;
    private Map<InetAddress, SocketChannel> tcpLinkTable;

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ByteBuffer readBuffer;
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

        readBuffer = ByteBuffer.allocateDirect(8192);
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

        // // We need to do something clever here. All the nodes should be up
        // // before we try to connect.
        // try {
        // Thread.sleep(10000);
        // } catch (InterruptedException e) {
        // }
        //
        // // figure out the external facing ip address, it is not the same as
        // the
        // // internal AWS one
        // InetAddress serverAddr = InetAddress.getByName(isa.getHostName());
        // for (InetAddress node : routingTable.getKnownNodes()) {
        // if (node.hashCode() < serverAddr.hashCode()) {
        // // connect to remote node
        // }
        // }
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
        tcpLinkTable.put(addr, socketChannel);

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

        // copy everything out of the read buffer into a new buffer
        int len = readBuffer.position();
        readBuffer.rewind();
        ByteBuffer buf = readBuffer.get(new byte[len], 0, len);
        buf.flip();

        // stick the packet on the readPackets queue for the PacketHandler to
        // deal with
        while (true) {
            try {
                readPackets.put(new ServerDataEvent(socketChannel, buf));
                break;
            } catch (InterruptedException e) {
                // TODO: is this correct?
                continue;
            }
        }
    }

    // Queues up a packet so that it can be sent by the selector
    private void send(SocketChannel socket, ByteBuffer data) {      
        // place a pending request on the queue for this socketchannel to be changed to write
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
                    queue.put(data);
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

                System.out.println(buf.remaining());

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

                send(dataEvent.socket, dataEvent.data);
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
    private class ServerDataEvent {
        private SocketChannel socket;
        private ByteBuffer data;

        private ServerDataEvent(SocketChannel socket, ByteBuffer data) {
            this.socket = socket;
            this.data = data;
        }
    }
}
