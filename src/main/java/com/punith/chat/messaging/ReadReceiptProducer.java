package com.punith.chat.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReadReceiptProducer {

    private static final Logger log = LoggerFactory.getLogger(ReadReceiptProducer.class);
    private static final String TOPIC = "chat.read-receipts";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ReadReceiptProducer(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(ReadReceiptEvent event) {
        String key = event.chatId().toString(); // partition by chatId again
        String value;
        try {
            value = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ReadReceiptEvent", e);
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, value);

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish read receipt event to Kafka", ex);
            } else {
                log.debug("Published read receipt event to Kafka, topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
