package com.punith.chat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.punith.chat.messaging.WsFanoutEvents.ReadReceiptFanoutEvent;
import com.punith.chat.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ReadReceiptConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReadReceiptConsumer.class);

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final WsFanoutPublisher wsFanoutPublisher;
    private final DlqPublisher dlqPublisher;
    private final Counter readProcessedCounter;
    private final Counter readFailedCounter;

    public ReadReceiptConsumer(ObjectMapper objectMapper,
                               MessageService messageService,
                               WsFanoutPublisher wsFanoutPublisher,
                               DlqPublisher dlqPublisher,
                               MeterRegistry meterRegistry)  {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.wsFanoutPublisher = wsFanoutPublisher;
        this.dlqPublisher = dlqPublisher;
        this.readProcessedCounter = Counter.builder("chat_read_receipts_processed_total")
                .description("Total read receipt events successfully processed from Kafka")
                .register(meterRegistry);

        this.readFailedCounter = Counter.builder("chat_read_receipts_failed_total")
                .description("Total read receipt events that failed processing")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "chat.read-receipts", groupId = "chat-read-processor")
    public void consume(String value) {
        final String topic = "chat.read-receipts";

        try {
            ReadReceiptEvent event = objectMapper.readValue(value, ReadReceiptEvent.class);

            messageService.markMessagesAsRead(
                    event.userId(),
                    event.chatId(),
                    event.messageIds()
            );

            OffsetDateTime readAt = OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(event.timestampMillis()),
                    ZoneOffset.UTC
            );

            ReadReceiptFanoutEvent fanoutEvent = new ReadReceiptFanoutEvent(
                    event.chatId(),
                    event.userId(),
                    event.messageIds(),
                    readAt.toString()
            );

            wsFanoutPublisher.publishReadReceipt(fanoutEvent);


            readProcessedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to process read receipt event, payload={}", value, e);
            readFailedCounter.increment();

            dlqPublisher.sendToDlq(
                    "chat.read-receipts.dlq",
                    topic,
                    null,
                    value,
                    e.getMessage()
            );
        }
    }

}
