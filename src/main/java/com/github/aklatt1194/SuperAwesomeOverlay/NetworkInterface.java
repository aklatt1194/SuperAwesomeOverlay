package com.github.aklatt1194.SuperAwesomeOverlay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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
    private BlockingQueue<ServerDataEvent> pendingData;

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

        readBuffer = ByteBuffer.allocate(8192);
        readPackets = new LinkedBlockingQueue<>();
        pendingData = new LinkedBlockingQueue<>();

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

//        // We need to do something clever here. All the nodes should be up
//        // before we try to connect.
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//        }
//
//        // figure out the external facing ip address, it is not the same as the
//        // internal AWS one
//        InetAddress serverAddr = InetAddress.getByName(isa.getHostName());
//        for (InetAddress node : routingTable.getKnownNodes()) {
//            if (node.hashCode() < serverAddr.hashCode()) {
//                // connect to remote node
//            }
//        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // if there is pending data to write, change the key interest
                ByteBuffer pendingWrite = null;
                if (!pendingData.isEmpty()) {
                    ServerDataEvent event = pendingData.take();
                    SelectionKey key = event.socket.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE);
                    pendingWrite = event.data;
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
                        this.write(key, pendingWrite);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        Socket socket = socketChannel.socket(); // add something to table?
        socketChannel.configureBlocking(false);

        // set selector to notify when data is to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

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
            // Remote closed socket cleanly. I don't think this should happen
            // ever with our app.
            key.cancel();
            socketChannel.close();
            return;
        }

        while (true) {
            try {
                readPackets.put(new ServerDataEvent(socketChannel, readBuffer
                        .duplicate()));
                break;
            } catch (InterruptedException e) {
                // TODO: is this correct?
                continue;
            }
        }
    }

    private void send(SocketChannel socket, ByteBuffer data) {
        while (true) {
            try {
                pendingData.put(new ServerDataEvent(socket, data));
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }
        selector.wakeup();
    }
    
    private boolean write(SelectionKey key, ByteBuffer data) throws IOException {
        SocketChannel socketChannel = (SocketChannel)key.channel();
        
        socketChannel.write(data);
        if (data.remaining() > 0)
            return false;
        
        key.interestOps(SelectionKey.OP_READ);
        return true;
    }

    private class PacketRouter implements Runnable {
        @Override
        public void run() {
            ServerDataEvent dataEvent = null;

            while (true) {
                try {
                    readPackets.take();
                } catch (InterruptedException e) {
                    continue;
                }

                try {
                    dataEvent = readPackets.take();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println(dataEvent.data.toString());
                send(dataEvent.socket, dataEvent.data);
            }
        }
    }

    private class ServerDataEvent {
        SocketChannel socket;
        ByteBuffer data;

        public ServerDataEvent(SocketChannel socket, ByteBuffer data) {
            this.socket = socket;
            this.data = data;
        }
    }
}
