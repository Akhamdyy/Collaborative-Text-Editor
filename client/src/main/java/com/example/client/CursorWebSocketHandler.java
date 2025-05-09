package com.example.client;

import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CursorWebSocketHandler {
    private StompSession stompSession;
    private TriConsumer<String, Integer, String> cursorHandler;
    private String sessionID;
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
                    this.sessionID = session.getSessionId();
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

    public void subscribeToCursorUpdates(String roomId) {
        stompSession.subscribe("/topic/cursors/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CursorPositionMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                CursorPositionMessage msg = (CursorPositionMessage) payload;
                if (cursorHandler != null) {
                    if (msg.getPosition() == -1) { // Disconnect signal
                        cursorHandler.accept(msg.getUserId(), null, null); // Notify handler to remove cursor
                    } else {
                        cursorHandler.accept(msg.getUserId(), msg.getPosition(), msg.getColor());
                    }
               }
}
        });
    }

    public void sendCursorPosition(String roomId, String userId, int position, String color) {
        stompSession.send(
                "/app/cursors/" + roomId,
                new CursorPositionMessage(userId, position, color)
        );
    }

    public void disconnect(String roomId, String userId) {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send(
                    "/app/cursors/" + roomId + "/disconnect",
                    new CursorPositionMessage(userId, -1, null) // Use -1 position to signal disconnect
            );
            stompSession.disconnect();
            stompSession = null;
            System.out.println("Disconnected from WebSocket server and sent disconnect signal for " + userId);
        }
    }

    public void setCursorHandler(TriConsumer<String, Integer, String> handler) {
        this.cursorHandler =handler;
}
}
