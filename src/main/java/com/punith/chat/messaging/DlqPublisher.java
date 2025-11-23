package com.punith.chat.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter dlqMessagesCounter;

    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;

        this.dlqMessagesCounter = Counter.builder("chat_dlq_published_total")
                .description("Total events successfully sent to DLQ topics")
                .register(meterRegistry);
    }

    public void sendToDlq(String dlqTopic,
                          String originalTopic,
                          String originalKey,
                          String originalPayload,
                          String errorMessage) {

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("originalTopic", originalTopic);
        wrapper.put("originalKey", originalKey);
        wrapper.put("originalPayload", originalPayload);
        wrapper.put("errorMessage", errorMessage);
        wrapper.put("timestamp", Instant.now().toString());

        String value;
        try {
            value = objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DLQ payload for topic {}", dlqTopic, e);
            return;
        }

        // if no key, we can still send; Kafka allows null keys
        ProducerRecord<String, String> record = new ProducerRecord<>(dlqTopic, originalKey, value);

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish message to DLQ topic {}", dlqTopic, ex);
            } else {
                dlqMessagesCounter.increment();  // count only successful DLQ publishes
                log.warn("Sent message to DLQ topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
