package com.example.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic");
		config.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new DelayChannelInterceptor());
	}

	private static class DelayChannelInterceptor implements ChannelInterceptor {
		private static final long DELAY_MS = 15; // Adjust delay as needed (e.g., 50ms)

		@Override
		public Message<?> preSend(Message<?> message, MessageChannel channel) {
			try {
				// Introduce a delay for each incoming message
				Thread.sleep(DELAY_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.out.println("Delay interrupted: " + e.getMessage());
			}
			return message;
		}
	}
}