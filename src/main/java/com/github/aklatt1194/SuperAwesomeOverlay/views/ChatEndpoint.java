package com.github.aklatt1194.SuperAwesomeOverlay.views;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;
import com.github.aklatt1194.SuperAwesomeOverlay.network.OverlayDatagramSocket;
import com.github.aklatt1194.SuperAwesomeOverlay.network.SimpleDatagramPacket;

@ServerEndpoint(value = "/chat")
public class ChatEndpoint {
    private static final int PORT = 54321;
    private static Charset charset = Charset.forName("UTF-8");
    
    private static List<Session> sessions;
    private static OverlayDatagramSocket socket;
    private static ReceiverThread receiver;
    
    public static void init(OverlayRoutingModel model) {
        sessions = new ArrayList<Session>();
        socket = new OverlayDatagramSocket(model);
        socket.bind(PORT);
        receiver = new ReceiverThread(socket);
        receiver.start();
    }
    
    @OnOpen
    public void open(Session session) {
         sessions.add(session);
    }
    

    @OnClose
    public void closedConnection(Session session) {
        sessions.remove(session);
    }
    
    @OnMessage
    public void onMessage(String message, Session session) {
        SimpleDatagramPacket packet = new SimpleDatagramPacket(null, null, PORT, 
                PORT, message.getBytes(charset));
        socket.send(packet);
    }
    
    /**
     * Write the message to the websocket
     */
    private static void send(String msg) {
        if (sessions.isEmpty()) {
            return;
        }
        
        for (Session session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
                
            try {
                session.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                System.err.println("Failed to deliver message to chat frontend");
                e.printStackTrace();
            }
        }
    }
    
    /**
     * A helper thread that will receive packets and write their contents to the
     * websocket
     */
    private static class ReceiverThread extends Thread {
        private OverlayDatagramSocket socket;
        
        public ReceiverThread(OverlayDatagramSocket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            while (true) {
                SimpleDatagramPacket packet = socket.receive();
                String message =  new String(packet.getPayload(), charset);
                send(message);
            }
        }
    }
}
