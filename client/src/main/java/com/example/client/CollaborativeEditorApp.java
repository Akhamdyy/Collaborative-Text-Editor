package com.example.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class CollaborativeEditorApp extends Application {
    private static final int SERVER_PORT = 8080;
    private static final String DISCOVERY_MESSAGE = "WEATHER_SERVER_DISCOVERY";
    private static final int DISCOVERY_TIMEOUT_MS = 3000;
    private String SERVER_URL;
    private String SERVER_IP;
    @Override
    public void start(Stage primaryStage) throws IOException {
        try {
            String serverIp = discoverServer();
            SERVER_URL = "http://" + serverIp + ":" + SERVER_PORT;
            SERVER_IP = serverIp;
            if (serverIp == null) {
                System.out.println("No server found on LAN. Please check:");
                System.out.println("1. Server is running");
                System.out.println("2. Both devices are on same network");
                System.out.print("Enter server IP manually or 'exit': ");
                serverIp = new Scanner(System.in).nextLine().trim();
                if (serverIp.equalsIgnoreCase("exit")) return;
            }
            System.out.println("Connected to server at: " + serverIp);
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/client/welcome.fxml"));
        Parent root = loader.load();
        WelcomeController controller = loader.getController();
        controller.SERVER_URL = SERVER_URL;
        controller.SERVER_IP = SERVER_IP;
        primaryStage.setTitle("Collaborative Text Editor");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
    private static String discoverServer() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] sendData = DISCOVERY_MESSAGE.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData,
                    sendData.length,
                    InetAddress.getByName("255.255.255.255"),
                    SERVER_PORT
            );
            socket.send(sendPacket);
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                socket.receive(receivePacket);
                return receivePacket.getAddress().getHostAddress();
            } catch (SocketTimeoutException e) {
                return null;
            }
        } catch (SocketException e) {
            System.err.println("Network error: " + e.getMessage());
            return null;
        } catch (UnknownHostException e) {
            System.err.println("Invalid host: " + e.getMessage());
            return null;
        } catch (IOException e) {
            System.err.println("Communication error: " + e.getMessage());
            return null;
 }
}
}
