package com.example.client;

public class main {
    public static void main(String[] args) {
        DocumentWebsockethandler client = new DocumentWebsockethandler();
        String roomCode = "A1B2C3-e";

        client.connectToWebSocket();
        //client.subscribeToRoom(roomCode);

        // Simulate insert operation
        client.sendInsert(roomCode, "User1:123456789", 'H', "root");

        // Leave connection open to receive updates
    }
}
