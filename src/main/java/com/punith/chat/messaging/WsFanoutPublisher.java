package com.punith.chat.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.punith.chat.messaging.WsFanoutEvents.NewMessageFanoutEvent;
import com.punith.chat.messaging.WsFanoutEvents.ReadReceiptFanoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.punith.chat.messaging.WsFanoutEvents.MessageStatusFanoutEvent;

@Service
public class WsFanoutPublisher {

    private static final Logger log = LoggerFactory.getLogger(WsFanoutPublisher.class);

    private static final String CHANNEL_NEW_MESSAGE = "ws.fanout.messages";
    private static final String CHANNEL_READ_RECEIPT = "ws.fanout.read-receipts";
    private static final String CHANNEL_MESSAGE_STATUS = "ws.fanout.message-status";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public WsFanoutPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishNewMessage(NewMessageFanoutEvent event) {
        publish(CHANNEL_NEW_MESSAGE, event);
    }

    public void publishReadReceipt(ReadReceiptFanoutEvent event) {
        publish(CHANNEL_READ_RECEIPT, event);
    }
    public void publishMessageStatus(MessageStatusFanoutEvent event) {
        publish(CHANNEL_MESSAGE_STATUS, event);
    }


    private void publish(String channel, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize fanout event for channel {}", channel, e);
        }
    }
}
