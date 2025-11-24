package com.punith.chat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.punith.chat.domain.message.Message;
import com.punith.chat.service.MessageService;
import com.punith.chat.messaging.WsFanoutEvents.NewMessageFanoutEvent;
import com.punith.chat.messaging.WsFanoutEvents.MessageStatusFanoutEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageConsumer.class);

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final WsFanoutPublisher wsFanoutPublisher;
    private final DlqPublisher dlqPublisher;
    private final Counter messagesProcessedCounter;
    private final Counter messagesFailedCounter;

    public ChatMessageConsumer(ObjectMapper objectMapper,
                               MessageService messageService,
                               WsFanoutPublisher wsFanoutPublisher,
                               DlqPublisher dlqPublisher,
                               MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.wsFanoutPublisher = wsFanoutPublisher;
        this.dlqPublisher = dlqPublisher;
        this.messagesProcessedCounter = Counter.builder("chat_messages_processed_total")
                .description("Total chat messages successfully processed from Kafka")
                .register(meterRegistry);

        this.messagesFailedCounter = Counter.builder("chat_messages_failed_total")
                .description("Total chat message events that failed processing")
                .register(meterRegistry);

    }

    @KafkaListener(topics = "chat.messages", groupId = "chat-message-processor")
    public void consume(String value) {
        final String topic = "chat.messages";

        try {
            ChatMessageEvent event = objectMapper.readValue(value, ChatMessageEvent.class);

            Message msg = messageService.sendMessage(
                    event.senderId(),
                    event.chatId(),
                    event.body(),
                    event.clientMessageId()
            );

            MessageStatusFanoutEvent statusEvent = new MessageStatusFanoutEvent(
                    msg.getId(),
                    msg.getChat().getId(),
                    msg.getSender().getId(),
                    "DELIVERED"
            );
            wsFanoutPublisher.publishMessageStatus(statusEvent);

            NewMessageFanoutEvent fanoutEvent = new NewMessageFanoutEvent(
                    msg.getId(),
                    msg.getChat().getId(),
                    msg.getSender().getId(),
                    msg.getBody(),
                    msg.getCreatedAt().toString()
            );
            wsFanoutPublisher.publishNewMessage(fanoutEvent);


            messagesProcessedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to process chat message event, payload={}", value, e);
            messagesFailedCounter.increment();

            dlqPublisher.sendToDlq(
                    "chat.messages.dlq",
                    topic,
                    null,
                    value,
                    e.getMessage()
            );
        }
    }

}
