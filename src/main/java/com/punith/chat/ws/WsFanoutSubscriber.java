package com.punith.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.punith.chat.messaging.WsFanoutEvents.NewMessageFanoutEvent;
import com.punith.chat.messaging.WsFanoutEvents.ReadReceiptFanoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import com.punith.chat.messaging.WsFanoutEvents.MessageStatusFanoutEvent;

@Service
public class WsFanoutSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(WsFanoutSubscriber.class);

    private final ObjectMapper objectMapper;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public WsFanoutSubscriber(ObjectMapper objectMapper,
                              ChatWebSocketHandler chatWebSocketHandler) {
        this.objectMapper = objectMapper;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            switch (channel) {
                case "ws.fanout.messages" -> {
                    NewMessageFanoutEvent event =
                            objectMapper.readValue(body, NewMessageFanoutEvent.class);
                    chatWebSocketHandler.broadcastNewMessageFanout(event);
                }
                case "ws.fanout.read-receipts" -> {
                    ReadReceiptFanoutEvent event =
                            objectMapper.readValue(body, ReadReceiptFanoutEvent.class);
                    chatWebSocketHandler.broadcastReadReceiptFanout(event);
                }
                // 🔹 NEW
                case "ws.fanout.message-status" -> {
                    MessageStatusFanoutEvent event =
                            objectMapper.readValue(body, MessageStatusFanoutEvent.class);
                    chatWebSocketHandler.broadcastMessageStatusFanout(event);
                }
                default -> log.warn("Received message for unknown channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("Failed to handle fanout message from channel={}, body={}", channel, body, e);
        }
    }


}
