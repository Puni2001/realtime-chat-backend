package com.punith.chat.config;

import com.punith.chat.ws.ChatWebSocketHandler;
import com.punith.chat.ws.UserHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final UserHandshakeInterceptor userHandshakeInterceptor;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           UserHandshakeInterceptor userHandshakeInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.userHandshakeInterceptor = userHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(userHandshakeInterceptor)   // <----- IMPORTANT
                .setAllowedOrigins("*");
    }
}
