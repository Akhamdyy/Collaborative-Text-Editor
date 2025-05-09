package com.example.client;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.util.concurrent.ListenableFuture;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
public class Userlistwebsockethandler {
    private volatile StompSession stompSession;
    private Consumer<List<String>> usershandler;
    public String IP;

    public boolean connectToWebSocket() {
        if (stompSession != null && stompSession.isConnected()) {
            System.out.println("Already connected.");
            return true;
        }

        List<Transport> transports = Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = "ws://" + IP + ":8080/ws";
        MyStompSessionHandler sessionHandler = new MyStompSessionHandler();

        CompletableFuture<Boolean> connectionResult = new CompletableFuture<>();

        ListenableFuture<StompSession> future = stompClient.connect(url, sessionHandler);
        future.addCallback(
                session -> {
                    stompSession = session;
                    System.out.println("Successfully connected to WebSocket server.");
                    connectionResult.complete(true);
                },
                ex -> {
                    System.err.println("WebSocket connection failed: " + ex.getMessage());
                    connectionResult.completeExceptionally(ex);
                }
        );

        try {
            return connectionResult.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Connection attempt failed: " + e.getMessage());
            return false;
        }
    }

    public void subscribeToRoom(String roomCode) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        String topic = "/topic/users/" + roomCode;
        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return List.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                List<String> users = (List<String>) payload;
                if (usershandler != null) {
                    usershandler.accept(users);
                }
            }
        });
        System.out.println("Subscribed to topic: " + topic);
    }
    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
            stompSession = null;
            System.out.println("Disconnected from WebSocket server.");
        }
    }
    public void join(String roomCode,String userid)
    {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        String destination = "/app/users/" + roomCode;
        stompSession.send(destination, userid);
        //System.out.println("Sent insert op: " + value);
    }
    public void leave(String roomCode,String username)
    {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        String destination = "/app/users/remove/" + roomCode;
        stompSession.send(destination, username);
    }

    public void setUsershandler(Consumer<List<String>> handler) {
        this.usershandler = handler;
}
}
