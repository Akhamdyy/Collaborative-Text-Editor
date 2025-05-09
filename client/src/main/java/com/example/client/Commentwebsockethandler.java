package com.example.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
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

public class Commentwebsockethandler {
    private volatile StompSession stompSession;
    private Consumer<List<Comment>> commentsHandler;
    private Consumer<String> commentRemovalHandler;
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

    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
            stompSession = null;
            System.out.println("Disconnected from WebSocket server.");
        }
    }

    public void subscribeToRoom(String roomCode) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        // Use editor code as the room identifier
        String editorCode = roomCode.endsWith("-v") ? getEditorCodeFromViewer(roomCode) : roomCode;

        // Subscribe to comment additions
        String topic = "/topic/comments/" + editorCode;
        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return new TypeReference<List<Comment>>() {}.getType();
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<Comment> comments = mapper.convertValue(payload, new TypeReference<List<Comment>>() {});
                    if (commentsHandler != null) {
                        commentsHandler.accept(comments);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to deserialize comments: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        System.out.println("Subscribed to topic: " + topic);

        // Subscribe to comment removals
        String removalTopic = "/topic/comments/remove/" + editorCode;
        stompSession.subscribe(removalTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String commentId = (String) payload;
                if (commentRemovalHandler != null) {
                    commentRemovalHandler.accept(commentId);
                }
            }
        });
        System.out.println("Subscribed to removal topic: " + removalTopic);
    }

    public void addComment(String roomCode, Comment comment) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        String editorCode = roomCode.endsWith("-v") ? getEditorCodeFromViewer(roomCode) : roomCode;
        String destination = "/app/comments/" + editorCode;
        stompSession.send(destination, comment);
        System.out.println("Sent comment: " + comment.getText());
    }

    public void updateComment(String roomCode, Comment comment) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        String editorCode = roomCode.endsWith("-v") ? getEditorCodeFromViewer(roomCode) : roomCode;
        String destination = "/app/comments/update/" + editorCode;
        stompSession.send(destination, comment);
        System.out.println("Sent comment update: " + comment.getId());
    }

    public void removeComment(String roomCode, String commentId) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected. Please ensure connectToWebSocket() succeeds.");
            return;
        }

        String editorCode = roomCode.endsWith("-v") ? getEditorCodeFromViewer(roomCode) : roomCode;
        String destination = "/app/comments/remove/" + editorCode;
        stompSession.send(destination, commentId);
        System.out.println("Sent comment removal: " + commentId);
    }

    public void setCommentsHandler(Consumer<List<Comment>> handler) {
        this.commentsHandler = handler;
    }

    public void setCommentRemovalHandler(Consumer<String> handler) {
        this.commentRemovalHandler = handler;
    }

    private String getEditorCodeFromViewer(String viewerCode) {
        return viewerCode.replace("-v","-e");
    }
}
