package com.github.aklatt1194.SuperAwesomeOverlay.views;

import java.io.IOException;

import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/chat")
public class ChatEndpoint {
    @OnOpen
    public void onOpen(Session session) {
        try {
            session.getBasicRemote().sendText("start");            
        } catch (IOException e) {
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
        }
    }
}
