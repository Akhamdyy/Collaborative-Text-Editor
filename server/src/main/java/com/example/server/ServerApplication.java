package com.example.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

@SpringBootApplication
public class ServerApplication {
	private static final int DISCOVERY_PORT = 8080;
	private static final String DISCOVERY_MSG = "WEATHER_SERVER_DISCOVERY";
	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
		startDiscoveryListener();
	}
	private static void startDiscoveryListener() {
		new Thread(() -> {
			try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
				System.out.println("Discovery service started on port " + DISCOVERY_PORT);
				byte[] buffer = new byte[1024];

				while (true) {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);

					String message = new String(packet.getData(), 0, packet.getLength());
					if (DISCOVERY_MSG.equals(message.trim())) {
						byte[] response = "SERVER_ACK".getBytes();
						socket.send(new DatagramPacket(
								response,
								response.length,
								packet.getAddress(),
								packet.getPort()
						));
					}
				}
			} catch (IOException e) {
				System.err.println("Discovery error: " + e.getMessage());
			}
		}).start();
	}
}
